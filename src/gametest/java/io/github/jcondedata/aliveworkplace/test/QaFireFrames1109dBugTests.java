package io.github.jcondedata.aliveworkplace.test;

import io.github.jcondedata.aliveworkplace.build.BuildPlan;
import io.github.jcondedata.aliveworkplace.build.BuildSite;
import io.github.jcondedata.aliveworkplace.build.BuildSiteManager;
import io.github.jcondedata.aliveworkplace.build.Builders;
import io.github.jcondedata.aliveworkplace.build.Upkeep;
import io.github.jcondedata.aliveworkplace.registry.ModBlocks;
import io.github.jcondedata.aliveworkplace.threat.ThreatData;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.decoration.ItemFrame;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;

/**
 * QA lane D, 2026-11-09, B1083's fix (docs/wiki/village-fire.md: "a frame that fell when its wall burned is hung again
 * with the wall"; BuildPlan.lostItsWall: a wall "still on the threats' damaged list (so a reload mid-repair, with the wall
 * already back, still hangs it)"). The frame goes up only when the repair finishes. Mid-repair, once the burned plank is
 * back, any other idle builder's look (Upkeep.threatDamage, run at the start of every builder's look) takes the plank off
 * the list. A restart then rebuilds the repair's plan without the frame: the wall is back and the frame could hang, so
 * it reads as one a player took down, and it is never hung again.
 *
 * <p>Fixed (B1095, lane-a-1109-0923): a burned block that is back stays on the threats' list while a repair of its
 * building is under way, and comes off at the first look after that repair ends.
 */
public class QaFireFrames1109dBugTests implements FabricGameTest {
	private static boolean hasFrameAt(BuildPlan plan, Vec3 at) {
		return plan.entities().stream().anyMatch(e -> e.pos().distanceTo(at) < 1.0);
	}

	//$ gametest_ticks_batch 'QaFireFrames1109dGameTests.AREA' '200' '"qaFireFrames1109dReload"'
	@GameTest(template = QaFireFrames1109dGameTests.AREA, timeoutTicks = 200, batch = "qaFireFrames1109dReload")
	public void aRestartMidRepairAfterAnotherBuildersLookStillHangsTheFrame(GameTestHelper helper) {
		QaFireFrames1109dGameTests.Scene scene = QaFireFrames1109dGameTests.scene(helper, 2, 0);
		ServerLevel level = scene.level();
		BlockPos roof = QaFireFrames1109dGameTests.ROOF_A;
		Vec3 frameAt = Vec3.atCenterOf(helper.absolutePos(roof.below()));
		QaFireFrames1109dGameTests.burn(helper, roof, roof.below());
		BuildSite site = Upkeep.look(level, scene.builder());
		helper.assertTrue(site != null && site.isRepair(), "setup: the builder didn't start the repair of the burned plank");
		Leftovers.after(helper, () -> BuildSiteManager.get(level).remove(site.id()));
		helper.setBlock(roof, Blocks.OAK_PLANKS); // the builder's repair has put the plank back; the frame goes up at its end
		helper.assertTrue(hasFrameAt(BuildSite.load(site.save()).plan(level), frameAt),
			"setup: a restart right after the plank went back already leaves the frame out of the repair");
		// a second builder of the village, idle, looks over his own builds
		BlockPos bench = new BlockPos(5, 2, 17);
		helper.setBlock(bench, ModBlocks.BUILDERS_BENCH);
		Villager other = helper.spawn(EntityType.VILLAGER, new BlockPos(6, 2, 18));
		Builders.employ(level, other, helper.absolutePos(bench));
		Upkeep.look(level, other);
		// the server restarts: the threats' list and the repair site come back from the save
		ThreatData.get(level).roundTrip(level.registryAccess());
		BuildSite back = BuildSite.load(site.save());
		helper.assertTrue(back != null, "setup: the repair site didn't load");
		helper.assertTrue(hasFrameAt(back.plan(level), frameAt),
			"after another builder's look and a restart mid-repair, the repair no longer hangs the frame that fell with the burned plank (on the threats' list: "
				+ ThreatData.get(level).damaged().contains(helper.absolutePos(roof)) + ")");
		helper.succeed();
	}

	/** A second, idle builder of the village, who looks over his own builds now. */
	private static Villager otherBuilder(GameTestHelper helper, ServerLevel level) {
		BlockPos bench = new BlockPos(5, 2, 17);
		helper.setBlock(bench, ModBlocks.BUILDERS_BENCH);
		Villager other = helper.spawn(EntityType.VILLAGER, new BlockPos(6, 2, 18));
		Builders.employ(level, other, helper.absolutePos(bench));
		return other;
	}

	/**
	 * The likeliest way the fix breaks: the same story (fire, plank back, the other builder's look, restart), but the
	 * player had taken down the frame under the other roof plank, which never burned. The restarted repair still leaves
	 * that one down.
	 */
	//$ gametest_ticks_batch 'QaFireFrames1109dGameTests.AREA' '200' '"qaFireFrames1109dReloadTaken"'
	@GameTest(template = QaFireFrames1109dGameTests.AREA, timeoutTicks = 200, batch = "qaFireFrames1109dReloadTaken")
	public void aRestartMidRepairStillLeavesAFrameAPlayerTookDown(GameTestHelper helper) {
		QaFireFrames1109dGameTests.Scene scene = QaFireFrames1109dGameTests.scene(helper, 2, 0);
		ServerLevel level = scene.level();
		BlockPos roof = QaFireFrames1109dGameTests.ROOF_A;
		BlockPos standing = roof.offset(2, 0, 2); // the other roof plank, with its frame under it
		for (ItemFrame f : QaFireFrames1109dGameTests.hanging(helper, standing.below(), ItemFrame.class)) {
			f.discard(); // the player takes it down
		}
		QaFireFrames1109dGameTests.burn(helper, roof, roof.below());
		BuildSite site = Upkeep.look(level, scene.builder());
		helper.assertTrue(site != null && site.isRepair(), "setup: the builder didn't start the repair of the burned plank");
		Leftovers.after(helper, () -> BuildSiteManager.get(level).remove(site.id()));
		helper.setBlock(roof, Blocks.OAK_PLANKS);
		Upkeep.look(level, otherBuilder(helper, level));
		ThreatData.get(level).roundTrip(level.registryAccess());
		BuildPlan plan = BuildSite.load(site.save()).plan(level);
		helper.assertTrue(hasFrameAt(plan, Vec3.atCenterOf(helper.absolutePos(roof.below()))),
			"after a restart mid-repair, the repair no longer hangs the frame that fell with the burned plank");
		helper.assertTrue(!hasFrameAt(plan, Vec3.atCenterOf(helper.absolutePos(standing.below()))),
			"after a restart mid-repair, the repair hangs again the frame a player took down from a roof plank that never burned");
		helper.succeed();
	}

	/**
	 * No restart: with a second builder looking all along, the repair runs to its end and hangs the frame, and the plank
	 * comes off the threats' list once the repair is over (it isn't kept there for good).
	 */
	//$ gametest_ticks_batch 'QaFireFrames1109dGameTests.AREA' '3000' '"qaFireFrames1109dTwoBuilders"'
	@GameTest(template = QaFireFrames1109dGameTests.AREA, timeoutTicks = 3000, batch = "qaFireFrames1109dTwoBuilders")
	public void withTwoBuildersLookingTheFrameGoesUpAndThePlankComesOffTheList(GameTestHelper helper) {
		QaFireFrames1109dGameTests.Scene scene = QaFireFrames1109dGameTests.scene(helper, 2, 0);
		ServerLevel level = scene.level();
		BlockPos roof = QaFireFrames1109dGameTests.ROOF_A;
		QaFireFrames1109dGameTests.burn(helper, roof, roof.below());
		Villager other = otherBuilder(helper, level);
		Upkeep.look(level, scene.builder());
		helper.onEachTick(() -> {
			if (helper.getTick() % 40 == 20) {
				Upkeep.look(level, other); // the other builder's looks while the repair goes on
			}
			if (helper.getTick() % 60 == 5) {
				Upkeep.look(level, scene.builder());
			}
		});
		helper.succeedWhen(() -> {
			helper.assertTrue(helper.getBlockState(roof).is(Blocks.OAK_PLANKS), "(the builder hasn't put the burned plank back yet)");
			helper.assertTrue(!QaFireFrames1109dGameTests.hanging(helper, roof.below(), ItemFrame.class).isEmpty(),
				"(the frame under the burned plank isn't back yet)");
			helper.assertTrue(BuildSiteManager.get(level).all().stream()
				.noneMatch(s -> s.isRepair() && helper.getBounds().contains(s.placement().origin().getCenter())), "(the repair isn't over yet)");
			helper.assertTrue(!ThreatData.get(level).damaged().contains(helper.absolutePos(roof)), "(no look since the repair ended yet)");
			helper.assertTrue(QaFireFrames1109dGameTests.hanging(helper, roof.below(), ItemFrame.class).size() == 1,
				"two frames hang under the repaired plank");
		});
	}
}
