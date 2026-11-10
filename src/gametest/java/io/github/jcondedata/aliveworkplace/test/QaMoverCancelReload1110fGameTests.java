package io.github.jcondedata.aliveworkplace.test;

import io.github.jcondedata.aliveworkplace.build.BuildSiteManager;
import io.github.jcondedata.aliveworkplace.build.MaterialRules;
import io.github.jcondedata.aliveworkplace.move.MovePlan;
import io.github.jcondedata.aliveworkplace.move.MoveSite;
import io.github.jcondedata.aliveworkplace.move.MoveSites;
import io.github.jcondedata.aliveworkplace.move.MoverWork;
import io.github.jcondedata.aliveworkplace.move.PendingMoves;
import io.github.jcondedata.aliveworkplace.test.MoverGameTests.Setup;
import io.github.jcondedata.aliveworkplace.work.WorkerStatus;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Container;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Rotation;

/**
 * QA (lane E, 2026-11-10, run 1012): a move called off, then the server saved and restarted while the Mover is putting
 * the house back (45.3: "save and reload in each stage", "the job canceled half way: everything taken down is put back up
 * where it was"). The canceled move must stay canceled through every reload (never turn round and finish the move) and
 * end with the house whole at its old spot, the chest's contents back once, the new spot empty, nothing dropped, the hall
 * still knowing the building at its old spot, and the Mover saying why. MoverGameTests's house and helpers.
 */
public class QaMoverCancelReload1110fGameTests implements FabricGameTest {
	private static final String AREA = "aliveworkplace_test:huge_area";
	private static final BlockPos CHEST = new BlockPos(4, 3, 4);
	private static final String CHEST_HOLDS = "0=12 minecraft:bread;26=64 minecraft:emerald;";

	/** Both saved records written and read back in place of the live ones (a server restart); the site picked up again. */
	private static MoveSite restart(ServerLevel level, Setup s) {
		CompoundTag pending = PendingMoves.get(level).save(new CompoundTag(), level.registryAccess());
		level.getDataStorage().set(PendingMoves.NAME, PendingMoves.load(pending, level.registryAccess()));
		return MoverGameTests.reload(level, s);
	}

	private static String chest(ServerLevel level, BlockPos abs) {
		if (!(level.getBlockEntity(abs) instanceof Container c)) {
			return "no container (" + level.getBlockState(abs) + ")";
		}
		StringBuilder out = new StringBuilder();
		for (int i = 0; i < c.getContainerSize(); i++) {
			ItemStack stack = c.getItem(i);
			if (!stack.isEmpty()) {
				out.append(i).append('=').append(stack.getCount()).append(' ').append(BuiltInRegistries.ITEM.getKey(stack.getItem())).append(';');
			}
		}
		return out.toString();
	}

	/** Everything a canceled move must leave: the house back, nothing at the new spot, nothing dropped, the hall unchanged. */
	private static void back(GameTestHelper helper, ServerLevel level, Setup s, MoveSite site, String when) {
		helper.assertTrue(site.canceled() && site.stage() == MoveSite.Stage.DONE, when + ": the move ended " + site.stage()
			+ (site.canceled() ? " canceled" : " NOT canceled (it went on with the move)"));
		int wrong = 0;
		String first = null;
		for (MovePlan.Placed p : s.before().placed(s.from(), Rotation.NONE)) {
			if (!MaterialRules.matches(level.getBlockState(p.pos()), p.state())) {
				wrong++;
				first = first != null ? first : p.pos().toShortString() + " is " + level.getBlockState(p.pos()) + ", not " + p.state();
			}
		}
		helper.assertTrue(wrong == 0, when + ": the house at its old spot has " + wrong + " blocks wrong, first " + first);
		int atNew = 0;
		for (MovePlan.Placed p : s.before().placed(s.to(), Rotation.CLOCKWISE_90)) {
			atNew += level.getBlockState(p.pos()).isAir() ? 0 : 1;
		}
		helper.assertTrue(atNew == 0, when + ": " + atNew + " blocks still stand at the new spot");
		String holds = chest(level, helper.absolutePos(CHEST));
		helper.assertTrue(holds.equals(CHEST_HOLDS), when + ": the chest is back with " + holds + ", not " + CHEST_HOLDS);
		int loose = MoverGameTests.loose(level, helper);
		helper.assertTrue(loose == 0, when + ": " + loose + " items lie loose");
		helper.assertTrue(BuildSiteManager.get(level).finishedAt(level, s.before().structure(), s.from()).isPresent(),
			when + ": the hall forgot the building at its old spot");
		helper.assertTrue(MoveSites.get(level).forBuilding(s.from()).isEmpty(), when + ": the site wasn't put away");
	}

	/**
	 * Called off with the house half up at the new spot; then the server restarts every 5 of the Mover's steps while he
	 * takes it down again and puts it back: it stays canceled through each restart, and everything ends back as it was.
	 */
	//$ gametest_batch AREA '"qa_e1110f_mover_cancel_reload"'
	@GameTest(template = AREA, batch = "qa_e1110f_mover_cancel_reload")
	public void aMoveCalledOffStaysCalledOffThroughRestartsWhileItGoesBack(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		Setup s = MoverGameTests.setup(helper, "qa_cancel_reload");
		helper.runAfterDelay(2, () -> {
			MoveSite site = MoverGameTests.work(level, s, x -> x.stage() == MoveSite.Stage.STRUCTURE);
			for (int i = 0; i < 10; i++) {
				MoverWork.work(level, s.mover(), site); // some walls up at the new spot
			}
			helper.assertTrue(site.stage() == MoveSite.Stage.STRUCTURE, "it didn't get half way up: " + site.stage());
			PendingMoves.get(level).remove(s.from()); // the Move page's Call off
			int steps = 0;
			int restarts = 0;
			boolean turned = false;
			while (site.stage() != MoveSite.Stage.DONE && steps++ < MoverGameTests.MAX_STEPS) {
				MoverWork.work(level, s.mover(), site);
				if (steps % 5 == 0 && site.stage() != MoveSite.Stage.DONE) {
					boolean was = site.canceled();
					site = restart(level, s);
					restarts++;
					turned |= was && !site.canceled();
				}
			}
			helper.assertTrue(restarts >= 3, "only " + restarts + " restarts happened on the way back");
			helper.assertTrue(!turned, "a restart forgot the move was called off");
			back(helper, level, s, site, "called off, restarted " + restarts + " times on the way back");
			var status = WorkerStatus.get(s.mover(), level.getGameTime());
			helper.assertTrue(status != null && status.line().getString().equals("Called off: putting it back where it was"),
				"the Mover's line: " + (status == null ? "none" : status.line().getString()));
			helper.succeed();
		});
	}

	/**
	 * Called off while the house is all down (packed in his crates) and the server restarts at once, before the Mover's
	 * next step: after the restart he puts it back at its old spot, never up at the new one.
	 */
	//$ gametest_batch AREA '"qa_e1110f_mover_cancel_reload_down"'
	@GameTest(template = AREA, batch = "qa_e1110f_mover_cancel_reload_down")
	public void aMoveCalledOffWithTheHouseDownAndRestartedAtOnceGoesBack(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		Setup s = MoverGameTests.setup(helper, "qa_cancel_reload_down");
		helper.runAfterDelay(2, () -> {
			MoveSite site = MoverGameTests.work(level, s, x -> x.stage() == MoveSite.Stage.FOUNDATION);
			helper.assertTrue(site.stage() == MoveSite.Stage.FOUNDATION, "the house didn't come all down: " + site.stage());
			PendingMoves.get(level).remove(s.from());
			site = restart(level, s);
			int upAtNew = 0;
			for (int i = 0; i < MoverGameTests.MAX_STEPS && site.stage() != MoveSite.Stage.DONE; i++) {
				MoverWork.work(level, s.mover(), site);
				if (upAtNew == 0) {
					for (MovePlan.Placed p : s.before().placed(s.to(), Rotation.CLOCKWISE_90)) {
						upAtNew += level.getBlockState(p.pos()).isAir() ? 0 : 1;
					}
				}
			}
			helper.assertTrue(upAtNew == 0, "after the restart the Mover put " + upAtNew + " blocks up at the new spot of a called-off move");
			back(helper, level, s, site, "called off with the house down and restarted at once");
			helper.succeed();
		});
	}
}
