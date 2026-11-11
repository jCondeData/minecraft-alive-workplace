package io.github.jcondedata.aliveworkplace.compat;

import static io.github.jcondedata.aliveworkplace.compat.QaCreateMoverContents1110eCompatTests.MIN;
import static io.github.jcondedata.aliveworkplace.compat.QaCreateMoverContents1110eCompatTests.SPOT;
import static io.github.jcondedata.aliveworkplace.compat.QaCreateMoverContents1110eCompatTests.block;
import static io.github.jcondedata.aliveworkplace.compat.QaCreateMoverContents1110eCompatTests.loose;
import static io.github.jcondedata.aliveworkplace.compat.QaCreateMoverContents1110eCompatTests.movesOnce;
import static io.github.jcondedata.aliveworkplace.compat.QaCreateMoverContents1110eCompatTests.set;
import static io.github.jcondedata.aliveworkplace.compat.QaCreateMoverContents1110eCompatTests.setupAround;
import static io.github.jcondedata.aliveworkplace.compat.QaCreateMoverContents1110eCompatTests.tidy;
import static io.github.jcondedata.aliveworkplace.compat.QaCreateMoverContents1110eCompatTests.with;
import static io.github.jcondedata.aliveworkplace.compat.QaCreateMoverContents1110eCompatTests.work;

import com.simibubi.create.content.decoration.placard.PlacardBlockEntity;
import com.simibubi.create.foundation.blockEntity.behaviour.BlockEntityBehaviour;
import com.simibubi.create.foundation.blockEntity.behaviour.filtering.FilteringBehaviour;
import io.github.jcondedata.aliveworkplace.compat.QaCreateMoverContents1110eCompatTests.Setup;
import io.github.jcondedata.aliveworkplace.move.MoveSite;
import java.util.Map;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

/**
 * QA (lane E, 2026-11-11, run 1012), from 45.3 ("nothing dropped, nothing lost") and 45.5 ("containers come back with
 * exactly their contents") with Create 6: the Create blocks that hold one thing for the player rather than a stock, left
 * untested by the earlier Mover classes (lane E's notes of 11-10: "Deployer filters ... not tested").
 * <ul>
 * <li>A Deployer holding 16 iron ingots in its hand: they arrive in its hand once, none loose.</li>
 * <li>A Deployer filtered to an iron pickaxe, with one in its hand: the filter and the pickaxe: both still set at the new spot, nothing loose.</li>
 * <li>A Placard showing a diamond (Create's item frame block), on the floor or hung on a wall: the diamond is on it at
 * the new spot, the wall one hangs on its turned wall, none dropped on the way or left loose.</li>
 * </ul>
 */
public class QaCreateMoverHeld1111eCompatTests implements FabricGameTest {
	static final String AREA = QaCreateMoverContents1110eCompatTests.AREA;

	/** A Deployer holding 16 iron ingots (put in as a funnel would). */
	//$ gametest_batch AREA '"qa_e1111_mover_deployer"'
	@GameTest(template = AREA, batch = "qa_e1111_mover_deployer")
	public void aMovedDeployerKeepsWhatItsHandHoldsOnce(GameTestHelper helper) {
		movesOnce(helper, "deployer", block("create:deployer").defaultBlockState(), Items.IRON_INGOT, 16);
	}

	/** A Deployer filtered to an iron pickaxe (it takes only that) and holding one: both still there after the move. */
	//$ gametest_batch AREA '"qa_e1111_mover_deployer_filter"'
	@GameTest(template = AREA, batch = "qa_e1111_mover_deployer_filter")
	public void aMovedDeployerKeepsItsFilterAndItsTool(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		PartnerShowsCompatTests.clearLeftovers(helper);
		BlockState deployer = block("create:deployer").defaultBlockState();
		set(level, helper.absolutePos(SPOT), deployer);
		helper.runAfterDelay(5, () -> {
			BlockEntity be = level.getBlockEntity(helper.absolutePos(SPOT));
			FilteringBehaviour filter = BlockEntityBehaviour.get(be, FilteringBehaviour.TYPE);
			helper.assertTrue(filter != null && filter.setFilter(new ItemStack(Items.IRON_PICKAXE)), "setup: the deployer took no filter");
			helper.assertTrue(QaCreateMoverContents1110eCompatTests.fill(level, helper.absolutePos(SPOT), Items.IRON_PICKAXE, 1) != null,
				"setup: the deployer took no pickaxe");
			Setup s = setupAround(helper, "deployer_filter");
			helper.runAfterDelay(2, () -> {
				MoveSite site = work(level, s, x -> x.stage() == MoveSite.Stage.FOUNDATION);
				Map<String, Integer> afterDown = loose(helper);
				work(level, s, x -> false);
				MoveSite.Stage end = site.stage();
				int skipped = site.skipped();
				BlockPos newSpot = s.before().at(SPOT.subtract(MIN), s.to(), Rotation.CLOCKWISE_90);
				helper.runAfterDelay(5, () -> {
					BlockState there = level.getBlockState(newSpot);
					BlockEntity moved = level.getBlockEntity(newSpot);
					FilteringBehaviour after = moved == null ? null : BlockEntityBehaviour.get(moved, FilteringBehaviour.TYPE);
					String filterNow = after == null ? "no filter behaviour" : after.getFilter().isEmpty() ? "none" : after.getFilter().getItem().toString();
					long tool = QaCreateMoverContents1110eCompatTests.held(level, newSpot, Items.IRON_PICKAXE);
					Map<String, Integer> looseNow = loose(helper);
					try {
						helper.assertTrue(end == MoveSite.Stage.DONE && skipped == 0, "the move ended " + end + ", skipped " + skipped);
						helper.assertTrue(there.is(deployer.getBlock()), "the new spot " + newSpot + " has " + there);
						helper.assertTrue(afterDown.isEmpty(), "taking the house down dropped " + afterDown);
						helper.assertTrue(after != null && after.getFilter().is(Items.IRON_PICKAXE), "the deployer's filter was an iron pickaxe; after the move: " + filterNow);
						helper.assertTrue(tool == 1 && looseNow.isEmpty(), "the deployer held an iron pickaxe; after the move it holds " + tool
							+ ", loose " + looseNow);
					} finally {
						tidy(helper, s);
					}
					helper.succeed();
				});
			});
		});
	}

	/** A Placard on the floor showing a diamond: the diamond is on it at the new spot, none dropped or loose. */
	//$ gametest_batch AREA '"qa_e1111_mover_placard"'
	@GameTest(template = AREA, batch = "qa_e1111_mover_placard")
	public void aMovedPlacardStillShowsItsDiamond(GameTestHelper helper) {
		placardMoves(helper, "placard", SPOT, with(block("create:placard").defaultBlockState(), "face", "floor"));
	}

	/**
	 * A Placard hung on the inside of the house's west wall (facing east) showing a diamond: at the new spot it hangs on
	 * the same wall, turned with the house (facing south after a quarter turn clockwise), still showing the diamond; it
	 * never pops off while its wall is taken down or put up, so nothing is dropped.
	 */
	//$ gametest_batch AREA '"qa_e1111_mover_wall_placard"'
	@GameTest(template = AREA, batch = "qa_e1111_mover_wall_placard")
	public void aMovedWallPlacardHangsOnItsTurnedWallStillShowingItsDiamond(GameTestHelper helper) {
		BlockState placard = with(with(block("create:placard").defaultBlockState(), "face", "wall"), "facing", "east");
		placardMoves(helper, "wall_placard", new BlockPos(MIN.getX() + 1, SPOT.getY(), SPOT.getZ()), placard);
	}

	/** {@code placard} at {@code at} (its support placed first) showing a diamond, the house moved a quarter turn. */
	static void placardMoves(GameTestHelper helper, String name, BlockPos at, BlockState placard) {
		ServerLevel level = helper.getLevel();
		PartnerShowsCompatTests.clearLeftovers(helper);
		BlockState wanted = placard.rotate(Rotation.CLOCKWISE_90);
		helper.runAfterDelay(1, () -> {
			// the block it hangs on first (the floor under it, or the west wall beside it)
			BlockPos support = placard.getValue(net.minecraft.world.level.block.FaceAttachedHorizontalDirectionalBlock.FACE)
				== net.minecraft.world.level.block.state.properties.AttachFace.FLOOR ? at.below() : at.west();
			set(level, helper.absolutePos(support), support.getY() == MIN.getY()
				? net.minecraft.world.level.block.Blocks.COBBLESTONE.defaultBlockState() : net.minecraft.world.level.block.Blocks.OAK_PLANKS.defaultBlockState());
			set(level, helper.absolutePos(at), placard);
			helper.runAfterDelay(5, () -> {
				helper.assertTrue(level.getBlockEntity(helper.absolutePos(at)) instanceof PlacardBlockEntity, "setup: no placard at "
					+ helper.absolutePos(at) + ": " + level.getBlockState(helper.absolutePos(at)));
				((PlacardBlockEntity) level.getBlockEntity(helper.absolutePos(at))).setHeldItem(new ItemStack(Items.DIAMOND));
				Setup setup = setupAround(helper, name);
				helper.runAfterDelay(2, () -> {
					MoveSite site = work(level, setup, x -> x.stage() == MoveSite.Stage.FOUNDATION);
					Map<String, Integer> afterDown = loose(helper);
					work(level, setup, x -> false);
					MoveSite.Stage end = site.stage();
					int skipped = site.skipped();
					BlockPos newSpot = setup.before().at(at.subtract(MIN), setup.to(), Rotation.CLOCKWISE_90);
					helper.runAfterDelay(5, () -> {
						BlockState there = level.getBlockState(newSpot);
						String shows = level.getBlockEntity(newSpot) instanceof PlacardBlockEntity p
							? (p.getHeldItem().isEmpty() ? "nothing" : p.getHeldItem().getCount() + " " + p.getHeldItem().getItem()) : "no placard";
						Map<String, Integer> looseNow = loose(helper);
						try {
							helper.assertTrue(end == MoveSite.Stage.DONE && skipped == 0, "the move ended " + end + ", skipped " + skipped);
							helper.assertTrue(afterDown.isEmpty(), "taking the house down dropped " + afterDown);
							helper.assertTrue(there.equals(wanted) && shows.equals("1 " + Items.DIAMOND) && looseNow.isEmpty(),
								"the placard (" + placard + ") showed a diamond; after the move the new spot has " + there + " (wanted " + wanted
								+ ") showing " + shows + ", loose " + looseNow);
						} finally {
							tidy(helper, setup);
						}
						helper.succeed();
					});
				});
			});
		});
	}
}
