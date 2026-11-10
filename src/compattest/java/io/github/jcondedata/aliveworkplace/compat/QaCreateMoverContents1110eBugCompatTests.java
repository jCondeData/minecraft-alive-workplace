package io.github.jcondedata.aliveworkplace.compat;

import static io.github.jcondedata.aliveworkplace.compat.QaCreateMoverContents1110eCompatTests.*;

import io.github.jcondedata.aliveworkplace.move.MoveSite;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockState;

/**
 * QA (lane E, 2026-11-10), failing on main: the Mover (45.3, "nothing dropped, nothing lost") moving a building with
 * a Create belt (B1128): a three-segment belt loses its other two segments and the item on them the moment he takes the
 * first. Helpers and the passing tests (vault, depot, crafter, ejector): QaCreateMoverContents1110eCompatTests.
 */
public class QaCreateMoverContents1110eBugCompatTests implements FabricGameTest {
	/**
	 * A three-segment belt running east along the house's floor (its controller the west end), with an iron ingot on
	 * each end (the middle left free): moved, every segment comes across, nothing is dropped, the ingots are on it at the new spot and it still takes
	 * an item put on its middle segment.
	 */
	//$ gametest_batch AREA '"qa_e1110_mover_belt"'
	@GameTest(template = AREA, batch = "qa_e1110_mover_belt")
	public void aMovedBeltComesAcrossWholeAndStillTakesItems(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		BlockState belt = with(with(block("create:belt").defaultBlockState(), "facing", "east"), "slope", "horizontal");
		List<BlockPos> segments = List.of(new BlockPos(4, 3, 5), new BlockPos(5, 3, 5), new BlockPos(6, 3, 5));
		String[] parts = {"start", "middle", "end"};
		// the house first (its floor and walls), then the belt in it, then the vault the setup fills (the belt's items go on after)
		PartnerShowsCompatTests.clearLeftovers(helper);
		for (int i = 0; i < 3; i++) {
			set(level, helper.absolutePos(segments.get(i)), with(belt, "part", parts[i]));
		}
		BlockPos controller = helper.absolutePos(segments.get(0));
		for (int i = 0; i < 3; i++) {
			net.minecraft.world.level.block.entity.BlockEntity be = level.getBlockEntity(helper.absolutePos(segments.get(i)));
			helper.assertTrue(be != null, "setup: no belt block entity at " + segments.get(i));
			net.minecraft.nbt.CompoundTag data = be.saveWithoutMetadata(level.registryAccess());
			data.putBoolean("IsController", i == 0);
			data.putInt("Index", i);
			data.putInt("Length", 3);
			data.putIntArray("Controller", new int[] {controller.getX(), controller.getY(), controller.getZ()});
			be.loadWithComponents(data, level.registryAccess());
			be.setChanged();
		}
		helper.runAfterDelay(1, () -> {
			int on = 0;
			on += onto(level, helper.absolutePos(segments.get(0)), Items.IRON_INGOT) ? 1 : 0;
			on += onto(level, helper.absolutePos(segments.get(2)), Items.IRON_INGOT) ? 1 : 0;
			helper.assertTrue(on == 2, "setup: the belt took " + on + " of 2 iron ingots before the move");
			Setup s = setupAround(helper, "belt");
			helper.runAfterDelay(2, () -> {
				MoveSite site = work(level, s, x -> x.stage() == MoveSite.Stage.FOUNDATION);
				Map<String, Integer> afterDown = loose(helper);
				int skippedDown = site.skipped();
				work(level, s, x -> false);
				MoveSite.Stage end = site.stage();
				int skipped = site.skipped();
				List<String> wrong = new ArrayList<>();
				long ingots = 0;
				for (int i = 0; i < 3; i++) {
					BlockPos at = s.before().at(segments.get(i).subtract(MIN), s.to(), Rotation.CLOCKWISE_90);
					BlockState there = level.getBlockState(at);
					if (!there.is(belt.getBlock())) {
						wrong.add(parts[i] + " at " + at.toShortString() + " is " + there);
					}
					ingots += held(level, at, Items.IRON_INGOT);
				}
				BlockPos middle = s.before().at(segments.get(1).subtract(MIN), s.to(), Rotation.CLOCKWISE_90);
				boolean takes = level.getBlockState(middle).is(belt.getBlock()) && onto(level, middle, Items.GOLD_INGOT);
				Map<String, Integer> afterAll = loose(helper);
				tidy(helper, s);
				helper.assertTrue(afterDown.isEmpty() && skippedDown == 0, "taking the belt down dropped " + afterDown + " and lost "
					+ skippedDown + " blocks (45.3: nothing dropped, nothing lost); in the end the move " + end + " skipped " + skipped
					+ ", the belt at the new spot: " + (wrong.isEmpty() ? "all three segments" : wrong) + ", " + ingots
					+ " of its 2 iron ingots on it, loose " + afterAll + ", takes an item on its middle: " + takes);
				helper.assertTrue(end == MoveSite.Stage.DONE && skipped == 0, "the move ended " + end + ", skipped " + skipped);
				helper.assertTrue(wrong.isEmpty(), "the belt at the new spot: " + wrong);
				helper.assertTrue(afterAll.isEmpty(), "loose after the move: " + afterAll);
				helper.assertTrue(ingots == 2, "the belt carried 2 iron ingots; at the new spot it has " + ingots);
				helper.assertTrue(takes, "the moved belt doesn't take an item put on its middle segment at " + middle.toShortString()
					+ " (the same belt took them before the move)");
				helper.succeed();
			});
		});
	}

}
