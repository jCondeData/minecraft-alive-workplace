package io.github.jcondedata.aliveworkplace.test;

import io.github.jcondedata.aliveworkplace.blueprint.BlueprintData;
import io.github.jcondedata.aliveworkplace.blueprint.BlueprintItem;
import io.github.jcondedata.aliveworkplace.blueprint.BlueprintOutline;
import io.github.jcondedata.aliveworkplace.build.BuildSite;
import io.github.jcondedata.aliveworkplace.build.BuildSiteManager;
import io.github.jcondedata.aliveworkplace.build.Builders;
import io.github.jcondedata.aliveworkplace.build.Employer;
import io.github.jcondedata.aliveworkplace.build.Upkeep;
import io.github.jcondedata.aliveworkplace.hall.VillageProtection;
import io.github.jcondedata.aliveworkplace.registry.ModAttachments;
import io.github.jcondedata.aliveworkplace.registry.ModBlocks;
import io.github.jcondedata.aliveworkplace.registry.ModComponents;
import io.github.jcondedata.aliveworkplace.work.Reach;
import java.util.List;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Vec3i;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.levelgen.structure.BoundingBox;

/**
 * QA, 36.20 at the edge of the long reach: "upkeep, the Steward's proposals and Plots use the same rule" as placing a
 * build. Placing a build measures the reach from the blueprint outline's centre ({@code Builders}, the click), while
 * Upkeep (and Refreshes and RepairAsks) measure it from the placement's origin, the outline's corner. With no village
 * near (only the long reach counts), a 16-block wall whose centre is exactly at the reach (12, set low to fit the area)
 * but whose origin corner is 20 off is accepted by the builder through the player's click; once it stands finished
 * and a block is knocked out, its own builder should repair it. The control is the same wall, centre also 12 off, with
 * its origin 4 off (the wall pointing away from the Table): repaired.
 */
public class QaReachEdgeUpkeepBugTests implements net.fabricmc.fabric.api.gametest.v1.FabricGameTest {
	private static final String HUGE = "aliveworkplace_test:huge_area";
	private static final int REACH = 12;
	private static final int LENGTH = 16;
	private static final org.slf4j.Logger LOGGER = org.slf4j.LoggerFactory.getLogger("aliveworkplace_test");

	/** Origin past the reach, centre at it: the build is accepted, so its builder should keep it in repair. */
	//$ gametest_ticks_batch HUGE '600' '"qaReachEdgeUpkeep"'
	@GameTest(template = HUGE, timeoutTicks = 600, batch = "qaReachEdgeUpkeep")
	public void aBuildAcceptedAtTheReachEdgeWithItsCornerPastItIsRepaired(GameTestHelper helper) {
		// bench at x 28, the wall x 8..23 (towards the bench): centre x 16 (12 off), origin x 8 (20 off)
		run(helper, new BlockPos(28, 2, 15), new BlockPos(8, 2, 15), "qreA", true);
	}

	/** The control: the same wall, centre 12 off, origin 4 off (the wall pointing away from the bench): repaired. */
	//$ gametest_ticks_batch HUGE '600' '"qaReachEdgeUpkeepControl"'
	@GameTest(template = HUGE, timeoutTicks = 600, batch = "qaReachEdgeUpkeepControl")
	public void aBuildAcceptedAtTheReachEdgeWithItsCornerInsideIsRepaired(GameTestHelper helper) {
		// bench at x 2, the wall x 6..21 (away from the bench): centre x 14 (12 off), origin x 6 (4 off)
		run(helper, new BlockPos(2, 2, 15), new BlockPos(6, 2, 15), "qreB", false);
	}

	private static void run(GameTestHelper helper, BlockPos benchRel, BlockPos wallRel, String playerName, boolean originPast) {
		Leftovers.clear(helper);
		Leftovers.players(helper);
		Leftovers.halls(helper);
		Leftovers.finished(helper);
		ServerLevel level = helper.getLevel();
		int reachWas = Builders.MAX_SITE_DISTANCE;
		boolean upkeepWas = Upkeep.ENABLED;
		boolean askWas = Upkeep.ASK;
		boolean protectionWas = VillageProtection.ENABLED;
		Builders.MAX_SITE_DISTANCE = REACH;
		Upkeep.ENABLED = true;
		Upkeep.ASK = false;
		VillageProtection.ENABLED = false;
		Leftovers.after(helper, () -> {
			Builders.MAX_SITE_DISTANCE = reachWas;
			Upkeep.ENABLED = upkeepWas;
			Upkeep.ASK = askWas;
			VillageProtection.ENABLED = protectionWas;
		});
		helper.setDayTime(2000);
		QaStageFiveGameTests.Client c = QaStageSixGameTests.client(helper, GameType.SURVIVAL, QaStageSixGameTests.name(playerName));
		ServerPlayer owner = c.player();

		// the wall, standing, captured as the blueprint's structure
		for (int x = 0; x < LENGTH; x++) {
			helper.setBlock(wallRel.offset(x, 0, 0), Blocks.OAK_PLANKS);
		}
		ResourceLocation wall = ResourceLocation.fromNamespaceAndPath("aliveworkplace_test", "qa_reach_edge_" + playerName.toLowerCase() + "_" + Long.toHexString(level.getGameTime()));
		Vec3i size = new Vec3i(LENGTH, 1, 1);
		level.getStructureManager().getOrCreate(wall).fillFromWorld(level, helper.absolutePos(wallRel), size, false, Blocks.STRUCTURE_VOID);

		helper.setBlock(benchRel, ModBlocks.BLUEPRINT_TABLE);
		BlockPos chestRel = benchRel.offset(0, 0, 2);
		helper.setBlock(chestRel, Blocks.CHEST);
		Container chest = helper.getBlockEntity(chestRel);
		chest.setItem(0, new ItemStack(Items.OAK_PLANKS, 8));
		BlockPos bench = helper.absolutePos(benchRel);
		Villager builder = helper.spawn(EntityType.VILLAGER, benchRel.offset(-1, 0, 1));
		Builders.employ(level, builder, bench);
		helper.assertTrue(Reach.village(level, bench).isEmpty(), "a village claims the Table: only the long reach should count");

		// the distances the two rules measure
		BlueprintData.Placement placement = new BlueprintData.Placement(level.dimension().location(), helper.absolutePos(wallRel), Rotation.NONE, Mirror.NONE);
		BoundingBox box = BlueprintOutline.bounds(placement, size);
		double centre = Math.sqrt(box.getCenter().distSqr(bench));
		double origin = Math.sqrt(placement.origin().distSqr(bench));
		helper.assertTrue(centre <= REACH, "the wall's centre is " + centre + " blocks off (want within " + REACH + ")");
		helper.assertTrue(originPast ? origin > REACH : origin <= REACH,
			"the wall's origin is " + origin + " blocks off (want " + (originPast ? "past " : "within ") + REACH + ")");

		// placed through the player's own click on the builder
		ItemStack blueprint = BlueprintItem.create(wall, size);
		blueprint.set(ModComponents.BLUEPRINT, BlueprintItem.data(blueprint).orElseThrow().withPlacement(Optional.of(placement)));
		owner.setItemInHand(InteractionHand.MAIN_HAND, blueprint);
		QaStageSixGameTests.clickVillager(owner, builder, c.seen());
		BuildSite taken = Builders.activeSite(level, builder);
		List<String> said = c.seen().stream().map(Component::getString).toList();
		helper.assertTrue(taken != null, String.format("the wall (centre %.1f, origin %.1f blocks off, reach %d) wasn't taken: %s", centre, origin, REACH, said));
		LOGGER.info("qa reach edge {}: centre {} origin {} blocks off, taken; said {}", playerName, centre, origin, said);

		// finished, the way the upkeep tests record it: the wall stands whole; then one plank is knocked out
		Builders.cancel(level, taken);
		ModAttachments.BUILDER_EMPLOYER.set(builder, new Employer(owner.getUUID(), owner.getGameProfile().getName()));
		BuildSiteManager manager = BuildSiteManager.get(level);
		manager.recordFinished(wall, placement, owner.getUUID());
		BlockPos hole = wallRel.offset(2, 0, 0);
		helper.setBlock(hole, Blocks.AIR);

		helper.runAfterDelay(2, () -> {
			if (!ModAttachments.BUILDER_JOB.has(builder)) {
				Upkeep.look(level, builder);
			}
			boolean repairing = manager.all().stream().anyMatch(s -> s.isRepair() && s.placement().equals(placement));
			helper.assertTrue(repairing, String.format(
				"its own builder never started repairing the wall it was allowed to build (centre %.1f blocks from the Table, within the reach %d; origin corner %.1f blocks off)",
				centre, REACH, origin));
		});
		helper.succeedWhen(() -> helper.assertBlockPresent(Blocks.OAK_PLANKS, hole));
	}
}
