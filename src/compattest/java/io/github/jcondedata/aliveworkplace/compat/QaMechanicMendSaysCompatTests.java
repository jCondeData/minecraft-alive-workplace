package io.github.jcondedata.aliveworkplace.compat;

import io.github.jcondedata.aliveworkplace.AliveWorkplace;
import io.github.jcondedata.aliveworkplace.blueprint.BlueprintData;
import io.github.jcondedata.aliveworkplace.blueprint.BlueprintLibrary;
import io.github.jcondedata.aliveworkplace.build.BuildSiteManager;
import io.github.jcondedata.aliveworkplace.mechanic.MechanicWork;
import io.github.jcondedata.aliveworkplace.registry.ModVillagers;
import io.github.jcondedata.aliveworkplace.work.Stations;
import io.github.jcondedata.aliveworkplace.work.WorkerStatus;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Vec3i;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Container;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;

/**
 * QA (lane E, 2026-10-24), ROADMAP 39.2 from the player's side: the line over the Mechanic's head while a part goes back
 * into a finished build, rendered as a player reads it: "Mechanic: putting back a Shaft in the ...", after "fetching Shaft
 * for the ..." from the store; no line reads "a" before a vowel.
 */
public class QaMechanicMendSaysCompatTests implements FabricGameTest {
	private static final String AREA = CompatGameTests.AREA;
	private static final BlockPos TOOLBOX = new BlockPos(2, 2, 2);
	private static final BlockPos CHEST = new BlockPos(2, 2, 4);
	private static final BlockPos LINE = new BlockPos(6, 2, 6);
	/** "a" before a word starting with a vowel. */
	private static final Pattern A_BEFORE_VOWEL = Pattern.compile("\\ba [AEIOUaeiou]");

	/** A shaft gone from a finished drive line: the Mechanic fetches one, puts it back and says so. */
	//$ gametest_ticks_batch AREA '1800' '"qa_mechanic_mend_says"'
	@GameTest(template = AREA, timeoutTicks = 1800, batch = "qa_mechanic_mend_says")
	public void theMechanicSaysWhatItPutsBack(GameTestHelper helper) {
		PartnerShowsCompatTests.clearLeftovers(helper);
		MachineWorkCompatTests.clearMachines(helper);
		ServerLevel level = helper.getLevel();
		BuildSiteManager sites = BuildSiteManager.get(level);
		for (BuildSiteManager.Finished f : sites.finishedNear(level, helper.absolutePos(TOOLBOX), 64)) {
			sites.forgetFinished(f.placement());
		}
		MachineWorkCompatTests.noVillage(helper);
		// A finished line of three shafts on a stone footing, recorded as a build of its own blueprint
		BlockState shaft = MechanicCompatTests.with(MechanicCompatTests.create("shaft").defaultBlockState(), "axis", "x");
		List<BlockPos> parts = List.of(LINE.offset(0, 1, 0), LINE.offset(1, 1, 0), LINE.offset(2, 1, 0));
		for (BlockPos part : parts) {
			helper.setBlock(part.below(), Blocks.STONE_BRICKS);
			helper.setBlock(part, shaft);
		}
		ResourceLocation id = AliveWorkplace.id("compat_test/qa_shaft_line_" + UUID.randomUUID().toString().substring(0, 8));
		StructureTemplate template = level.getStructureManager().getOrCreate(id);
		template.fillFromWorld(level, helper.absolutePos(LINE), new Vec3i(3, 2, 1), false, Blocks.STRUCTURE_VOID);
		helper.assertTrue(BlueprintLibrary.get(level, id).isPresent(), "setup: could not register the test blueprint");
		sites.recordFinished(id, new BlueprintData.Placement(level.dimension().location(), helper.absolutePos(LINE), Rotation.NONE, Mirror.NONE),
			UUID.randomUUID());
		// The store holds the part
		helper.setBlock(CHEST, Blocks.CHEST.defaultBlockState());
		Container chest = helper.getBlockEntity(CHEST);
		chest.setItem(0, new ItemStack(MechanicCompatTests.createItem("shaft"), 2));
		// The Mechanic
		helper.setBlock(TOOLBOX, MechanicCompatTests.create("brown_toolbox").defaultBlockState());
		Villager mechanic = helper.spawn(EntityType.VILLAGER, new BlockPos(3, 2, 3));
		Stations.assign(level, mechanic, helper.absolutePos(TOOLBOX), ModVillagers.MECHANIC);
		helper.assertTrue(MechanicWork.isMechanic(mechanic), "setup: no Mechanic at the toolbox");
		helper.setDayTime(2000);
		helper.setBlock(parts.get(1), Blocks.AIR); // the middle shaft broken
		List<MechanicWork.Part> missing = MechanicWork.missingParts(level, helper.absolutePos(TOOLBOX));
		helper.assertTrue(missing.size() == 1 && missing.get(0).pos().equals(helper.absolutePos(parts.get(1))),
			"the broken shaft isn't a part to put back: " + missing.stream().map(p -> helper.relativePos(p.pos()) + " " + p.state()).toList());
		Set<String> said = new LinkedHashSet<>();
		helper.onEachTick(() -> {
			WorkerStatus.Entry e = WorkerStatus.get(mechanic, level.getGameTime());
			if (e != null) {
				said.add(e.title().getString() + ": " + e.line().getString());
			}
		});
		helper.succeedWhen(() -> {
			helper.assertTrue(helper.getBlockState(parts.get(1)).is(MechanicCompatTests.create("shaft")),
				"the shaft isn't back: " + helper.getBlockState(parts.get(1)) + "; the Mechanic said " + said);
			List<String> mending = said.stream().filter(s -> s.startsWith("Mechanic: putting back")).toList();
			helper.assertTrue(!mending.isEmpty() && mending.stream().allMatch(s -> s.startsWith("Mechanic: putting back a Shaft in the ")),
				"the Mechanic didn't say it was putting back a Shaft: " + said);
			helper.assertTrue(said.stream().anyMatch(s -> s.startsWith("Mechanic: fetching Shaft for the ")), "the Mechanic didn't say it fetched the Shaft: " + said);
			List<String> wrong = said.stream().filter(s -> A_BEFORE_VOWEL.matcher(s).find()).toList();
			helper.assertTrue(wrong.isEmpty(), "\"a\" before a vowel over the Mechanic's head: " + wrong);
			sites.forgetFinished(new BlueprintData.Placement(level.dimension().location(), helper.absolutePos(LINE), Rotation.NONE, Mirror.NONE));
		});
	}
}
