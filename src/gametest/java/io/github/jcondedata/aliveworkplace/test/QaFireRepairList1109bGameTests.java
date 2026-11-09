package io.github.jcondedata.aliveworkplace.test;

import io.github.jcondedata.aliveworkplace.blueprint.BlueprintData;
import io.github.jcondedata.aliveworkplace.build.BuildPlan;
import io.github.jcondedata.aliveworkplace.build.BuildSite;
import io.github.jcondedata.aliveworkplace.build.BuildSiteManager;
import io.github.jcondedata.aliveworkplace.build.Builders;
import io.github.jcondedata.aliveworkplace.build.Upkeep;
import io.github.jcondedata.aliveworkplace.registry.ModBlocks;
import io.github.jcondedata.aliveworkplace.threat.ThreatData;
import io.github.jcondedata.aliveworkplace.threat.VillageFire;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Vec3i;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.phys.Vec3;

/**
 * QA lane B, 2026-11-09, B1095's fix, from its words (wiki village-fire.md; the fix: "a burned block that is back stays on
 * the threats' list while a repair of ITS building is under way, and comes off at the first look after that repair
 * ends"). The bug tests cover one hut, one restart, a repair that runs to its end. These cover the edges: a repair that
 * ends without finishing (its site gone), a second building whose block is back while the first one's repair goes on,
 * and two restarts with looks between them.
 */
public class QaFireRepairList1109bGameTests implements FabricGameTest {
	/** A second finished building of the same builder: a 3x3 plank platform on log feet, away from the hut. */
	private static final BlockPos SHED = new BlockPos(13, 2, 6);

	private static boolean hasFrameAt(BuildPlan plan, Vec3 at) {
		return plan.entities().stream().anyMatch(e -> e.pos().distanceTo(at) < 1.0);
	}

	private static Villager otherBuilder(GameTestHelper helper, ServerLevel level) {
		BlockPos bench = new BlockPos(5, 2, 17);
		helper.setBlock(bench, ModBlocks.BUILDERS_BENCH);
		Villager other = helper.spawn(EntityType.VILLAGER, new BlockPos(6, 2, 18));
		Builders.employ(level, other, helper.absolutePos(bench));
		return other;
	}

	private static boolean listed(GameTestHelper helper, BlockPos pos) {
		return ThreatData.get(helper.getLevel()).damaged().contains(helper.absolutePos(pos));
	}

	/**
	 * The repair ends without finishing: its site is gone (the builder lost his job, the player canceled it). The plank
	 * that is back must not stay on the threats' list for good; the next look takes it off.
	 */
	//$ gametest_ticks_batch 'QaFireFrames1109dGameTests.AREA' '200' '"qaFireRepairList1109bGone"'
	@GameTest(template = QaFireFrames1109dGameTests.AREA, timeoutTicks = 200, batch = "qaFireRepairList1109bGone")
	public void aRepairWhoseSiteIsGoneLetsTheNextLookTakeThePlankOff(GameTestHelper helper) {
		QaFireFrames1109dGameTests.Scene scene = QaFireFrames1109dGameTests.scene(helper, 2, 0);
		ServerLevel level = scene.level();
		BlockPos roof = QaFireFrames1109dGameTests.ROOF_A;
		QaFireFrames1109dGameTests.burn(helper, roof, roof.below());
		BuildSite site = Upkeep.look(level, scene.builder());
		helper.assertTrue(site != null && site.isRepair(), "setup: the builder didn't start the repair of the burned plank");
		Leftovers.after(helper, () -> BuildSiteManager.get(level).remove(site.id()));
		helper.setBlock(roof, Blocks.OAK_PLANKS);
		Villager other = otherBuilder(helper, level);
		Upkeep.look(level, other);
		helper.assertTrue(listed(helper, roof), "setup: the plank came off the threats' list while its repair was still going");
		BuildSiteManager.get(level).remove(site.id()); // the repair is over, unfinished
		Upkeep.look(level, other);
		helper.assertTrue(!listed(helper, roof),
			"a plank that is back stays on the threats' list after its repair site is gone (it would be kept there for good)");
		helper.succeed();
	}

	/**
	 * Two buildings of the village: the hut's repair is going on; the shed's burned plank was put back by a player. Only
	 * the hut's repair keeps its own blocks listed: the shed's plank comes off at the next look.
	 */
	//$ gametest_ticks_batch 'QaFireFrames1109dGameTests.AREA' '200' '"qaFireRepairList1109bTwoBuilds"'
	@GameTest(template = QaFireFrames1109dGameTests.AREA, timeoutTicks = 200, batch = "qaFireRepairList1109bTwoBuilds")
	public void aRepairOfTheHutKeepsNoBlockOfAnotherBuildingListed(GameTestHelper helper) {
		QaFireFrames1109dGameTests.Scene scene = QaFireFrames1109dGameTests.scene(helper, 2, 0);
		ServerLevel level = scene.level();
		for (int x = 0; x < 3; x++) {
			for (int z = 0; z < 3; z++) {
				helper.setBlock(SHED.offset(x, -1, z), Blocks.OAK_LOG);
				helper.setBlock(SHED.offset(x, 0, z), Blocks.OAK_PLANKS);
			}
		}
		ResourceLocation id = ResourceLocation.fromNamespaceAndPath("aliveworkplace_test", "qa_firerepairlist_1109b_" + Long.toHexString(level.getGameTime()));
		level.getStructureManager().getOrCreate(id).fillFromWorld(level, helper.absolutePos(SHED.below()), new Vec3i(3, 2, 3), true, Blocks.STRUCTURE_VOID);
		BlueprintData.Placement shed = new BlueprintData.Placement(level.dimension().location(), helper.absolutePos(SHED.below()), Rotation.NONE, Mirror.NONE);
		BuildSiteManager.get(level).recordFinished(id, shed, scene.builder().getUUID());
		VillageFire.forgetBuilds();
		Leftovers.after(helper, () -> BuildSiteManager.get(level).forgetFinished(shed));
		helper.assertTrue(VillageFire.buildAt(level, helper.absolutePos(SHED.offset(1, 0, 1))).isPresent(), "setup: the shed isn't a finished building");

		BlockPos roof = QaFireFrames1109dGameTests.ROOF_A;
		QaFireFrames1109dGameTests.burn(helper, roof, roof.below());
		BuildSite site = Upkeep.look(level, scene.builder());
		helper.assertTrue(site != null && site.isRepair() && !site.placement().equals(shed), "setup: the builder didn't start the hut's repair");
		Leftovers.after(helper, () -> BuildSiteManager.get(level).remove(site.id()));
		helper.setBlock(roof, Blocks.OAK_PLANKS); // the hut's plank is back, its repair still going

		BlockPos shedPlank = SHED.offset(1, 0, 1);
		helper.setBlock(shedPlank, Blocks.AIR);
		ThreatData.get(level).damaged(helper.absolutePos(shedPlank)); // the shed's plank burned too
		helper.setBlock(shedPlank, Blocks.OAK_PLANKS); // and a player put it back before any builder came

		Upkeep.look(level, otherBuilder(helper, level));
		helper.assertTrue(listed(helper, roof), "the hut's plank came off the threats' list while the hut's repair is still going");
		helper.assertTrue(!listed(helper, shedPlank),
			"the shed's plank, back with no repair of the shed going, stays on the threats' list because the hut is being repaired");
		helper.succeed();
	}

	/** Restart, another look, restart again: the repair still knows the frame fell with the burned plank. */
	//$ gametest_ticks_batch 'QaFireFrames1109dGameTests.AREA' '200' '"qaFireRepairList1109bTwoRestarts"'
	@GameTest(template = QaFireFrames1109dGameTests.AREA, timeoutTicks = 200, batch = "qaFireRepairList1109bTwoRestarts")
	public void twoRestartsWithLooksBetweenStillHangTheFrame(GameTestHelper helper) {
		QaFireFrames1109dGameTests.Scene scene = QaFireFrames1109dGameTests.scene(helper, 2, 0);
		ServerLevel level = scene.level();
		BlockPos roof = QaFireFrames1109dGameTests.ROOF_A;
		Vec3 frameAt = Vec3.atCenterOf(helper.absolutePos(roof.below()));
		QaFireFrames1109dGameTests.burn(helper, roof, roof.below());
		BuildSite site = Upkeep.look(level, scene.builder());
		helper.assertTrue(site != null && site.isRepair(), "setup: the builder didn't start the repair of the burned plank");
		Leftovers.after(helper, () -> BuildSiteManager.get(level).remove(site.id()));
		helper.setBlock(roof, Blocks.OAK_PLANKS);
		Villager other = otherBuilder(helper, level);
		Upkeep.look(level, other);
		ThreatData.get(level).roundTrip(level.registryAccess());
		BuildSite once = BuildSite.load(site.save());
		Upkeep.look(level, other);
		ThreatData.get(level).roundTrip(level.registryAccess());
		BuildSite twice = BuildSite.load(once.save());
		helper.assertTrue(twice != null, "setup: the repair site didn't load the second time");
		helper.assertTrue(listed(helper, roof), "after two restarts and two looks, the burned plank is off the threats' list mid-repair");
		helper.assertTrue(hasFrameAt(twice.plan(level), frameAt),
			"after two restarts with another builder's look between them, the repair no longer hangs the frame that fell with the burned plank");
		helper.succeed();
	}
}
