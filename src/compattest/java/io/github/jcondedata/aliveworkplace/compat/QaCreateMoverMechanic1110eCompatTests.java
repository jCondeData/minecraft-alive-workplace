package io.github.jcondedata.aliveworkplace.compat;

import static io.github.jcondedata.aliveworkplace.compat.QaCreateMoverContents1110eCompatTests.SPOT;
import static io.github.jcondedata.aliveworkplace.compat.QaCreateMoverContents1110eCompatTests.block;
import static io.github.jcondedata.aliveworkplace.compat.QaCreateMoverContents1110eCompatTests.fill;
import static io.github.jcondedata.aliveworkplace.compat.QaCreateMoverContents1110eCompatTests.held;
import static io.github.jcondedata.aliveworkplace.compat.QaCreateMoverContents1110eCompatTests.loose;
import static io.github.jcondedata.aliveworkplace.compat.QaCreateMoverContents1110eCompatTests.set;
import static io.github.jcondedata.aliveworkplace.compat.QaCreateMoverContents1110eCompatTests.setupAround;
import static io.github.jcondedata.aliveworkplace.compat.QaCreateMoverContents1110eCompatTests.tidy;
import static io.github.jcondedata.aliveworkplace.compat.QaCreateMoverContents1110eCompatTests.work;
import static io.github.jcondedata.aliveworkplace.compat.QaCreateMoverMultiblock1110e2CompatTests.moved;

import io.github.jcondedata.aliveworkplace.compat.QaCreateMoverContents1110eCompatTests.Setup;
import io.github.jcondedata.aliveworkplace.move.MoveSite;
import io.github.jcondedata.aliveworkplace.registry.ModVillagers;
import io.github.jcondedata.aliveworkplace.work.Jobs;
import java.util.Map;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.item.Items;

/**
 * QA (lane E, 2026-11-10): the Mover (45.3, 45.4) moving the house that holds a Mechanic's workstation, a Create
 * Toolbox with his spare parts in it. moving-buildings.md: a worker keeps his workstation through the move and it is his
 * the moment it is put up; containers come across with their contents, nothing dropped. The Toolbox is the Mechanic's
 * workstation only with Create (ModVillagers.TOOLBOX_POI), so the lectern tests of MoverResidentsGameTests don't reach it.
 */
public class QaCreateMoverMechanic1110eCompatTests implements FabricGameTest {
	static final String AREA = "aliveworkplace_compat:huge_area";
	static final int PARTS = 8;

	/** The Mechanic's Toolbox, holding 8 iron ingots, in a moved house: his at the new spot, the 8 in it once. */
	//$ gametest_ticks_batch AREA '400' '"qa_e1110b_mover_mechanic"'
	@GameTest(template = AREA, timeoutTicks = 400, batch = "qa_e1110b_mover_mechanic")
	public void aMechanicKeepsHisToolboxAndItsPartsThroughTheMove(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		PartnerShowsCompatTests.clearLeftovers(helper);
		helper.assertTrue(ModVillagers.MECHANIC != null, "no Mechanic with Create");
		set(level, helper.absolutePos(SPOT), block("create:brown_toolbox").defaultBlockState());
		helper.runAfterDelay(5, () -> {
			helper.assertTrue(fill(level, helper.absolutePos(SPOT), Items.IRON_INGOT, PARTS) != null && held(level, helper.absolutePos(SPOT), Items.IRON_INGOT) == PARTS,
				"couldn't put " + PARTS + " iron ingots in the toolbox");
			Villager mechanic = helper.spawn(EntityType.VILLAGER, new BlockPos(10, 2, 7));
			mechanic.setNoAi(true);
			Jobs.employ(level, mechanic, helper.absolutePos(SPOT), ModVillagers.TOOLBOX_POI, ModVillagers.MECHANIC);
			helper.assertTrue(mechanic.getVillagerData().getProfession() == ModVillagers.MECHANIC, "the villager isn't a Mechanic");
			Setup s = setupAround(helper, "mechanic");
			helper.runAfterDelay(2, () -> {
				BlockPos box = moved(s, SPOT);
				// put up but the move not over yet: the toolbox is his at once (45.4), not only when the move ends
				MoveSite site = work(level, s, x -> level.getBlockState(box).is(block("create:brown_toolbox")));
				MoveSite.Stage up = site.stage();
				var jobAtOnce = mechanic.getBrain().getMemory(MemoryModuleType.JOB_SITE);
				long freeAtOnce = level.getPoiManager().getFreeTickets(box);
				work(level, s, x -> false);
				MoveSite.Stage end = site.stage();
				int skipped = site.skipped();
				var state = level.getBlockState(box);
				long parts = held(level, box, Items.IRON_INGOT);
				Map<String, Integer> looseNow = loose(helper);
				var job = mechanic.getBrain().getMemory(MemoryModuleType.JOB_SITE);
				var profession = mechanic.getVillagerData().getProfession();
				long free = level.getPoiManager().getFreeTickets(box);
				tidy(helper, s);
				mechanic.discard();
				helper.assertTrue(up != MoveSite.Stage.DONE && jobAtOnce.filter(g -> g.equals(GlobalPos.of(level.dimension(), box))).isPresent() && freeAtOnce == 0,
					"the toolbox put up (the move at " + up + ") isn't the Mechanic's at once: his workstation is " + jobAtOnce + ", " + freeAtOnce + " places free");
				helper.assertTrue(end == MoveSite.Stage.DONE && skipped == 0, "the move ended " + end + ", skipped " + skipped);
				helper.assertTrue(state.is(block("create:brown_toolbox")), "the new spot " + box.toShortString() + " has " + state);
				helper.assertTrue(parts == PARTS && looseNow.isEmpty(), "the moved toolbox holds " + parts + " of " + PARTS + " iron ingots; loose: " + looseNow);
				helper.assertTrue(profession == ModVillagers.MECHANIC, "the Mechanic lost his job: " + profession);
				helper.assertTrue(job.filter(g -> g.equals(GlobalPos.of(level.dimension(), box))).isPresent(),
					"the Mechanic's workstation is " + job + ", not the moved toolbox at " + box.toShortString());
				helper.assertTrue(free == 0, "the moved toolbox is free to anyone (" + free + " places)");
				helper.succeed();
			});
		});
	}
}
