package io.github.jcondedata.aliveworkplace.test;

import io.github.jcondedata.aliveworkplace.camp.SettlersWagonItem;
import io.github.jcondedata.aliveworkplace.colony.ColonyArrival;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.phys.Vec3;

/**
 * QA (lane-b-1025-0038): the Settler's Wagon's camp ("a covered wagon with a chest of supplies: building materials,
 * food, two blueprints and a Village Hall", SettlersWagonItem) is there for the player to unpack, so its chest must be
 * one a player can open where it stands: some spot round it where a player fits standing, with the chest under the
 * crosshair within reach (the game's own pick: a ray from the eyes against block outlines, 4.5 blocks).
 */
public class QaCampReachGameTests implements net.fabricmc.fabric.api.gametest.v1.FabricGameTest {
	private static final String AREA = "aliveworkplace_test:big_area";
	private static final String HUGE = "aliveworkplace_test:huge_area";

	/** Whether a standing player at {@code feet} can put the crosshair on {@code target} within reach. */
	static boolean canClick(ServerLevel level, ServerPlayer player, BlockPos feet, BlockPos target) {
		Vec3 eye = new Vec3(feet.getX() + 0.5, feet.getY() + 1.62, feet.getZ() + 0.5);
		double reach = player.blockInteractionRange();
		for (double dx = 0.1; dx <= 0.9; dx += 0.4) {
			for (double dy = 0.1; dy <= 0.9; dy += 0.4) {
				for (double dz = 0.1; dz <= 0.9; dz += 0.4) {
					Vec3 aim = new Vec3(target.getX() + dx, target.getY() + dy, target.getZ() + dz);
					if (aim.distanceTo(eye) > reach) {
						continue;
					}
					BlockHitResult hit = level.clip(new ClipContext(eye, aim, ClipContext.Block.OUTLINE, ClipContext.Fluid.NONE, player));
					if (hit.getType() == HitResult.Type.BLOCK && hit.getBlockPos().equals(target)) {
						return true;
					}
				}
			}
		}
		return false;
	}

	/** A player fits standing at {@code feet}: nothing to bump into there or a block up, and something under them. */
	static boolean fits(ServerLevel level, BlockPos feet) {
		BlockState here = level.getBlockState(feet);
		BlockState head = level.getBlockState(feet.above());
		return here.getCollisionShape(level, feet).isEmpty() && head.getCollisionShape(level, feet.above()).isEmpty()
			&& !level.getBlockState(feet.below()).getCollisionShape(level, feet.below()).isEmpty();
	}

	/** The camp's chest of supplies can be opened by a player standing somewhere round it. */
	//$ gametest_ticks_batch AREA '40' '"qaCampChestReach"'
	@GameTest(template = AREA, timeoutTicks = 40, batch = "qaCampChestReach")
	public void theCampsChestOfSuppliesCanBeOpened(GameTestHelper helper) {
		Leftovers.clear(helper);
		ServerLevel level = helper.getLevel();
		ServerPlayer player = helper.makeMockServerPlayerInLevel();
		try {
			List<Villager> settlers = SettlersWagonItem.makeCamp(level, player, helper.absolutePos(new BlockPos(11, 2, 6)));
			helper.assertTrue(settlers.size() == 2, "no camp was made: " + settlers.size() + " settlers");
			BlockPos chest = null;
			for (BlockPos p : BlockPos.betweenClosed(helper.absolutePos(new BlockPos(0, 1, 0)), helper.absolutePos(new BlockPos(21, 8, 21)))) {
				if (level.getBlockState(p).is(Blocks.CHEST)) {
					chest = p.immutable();
				}
			}
			helper.assertTrue(chest != null, "the camp has no chest");
			int spots = 0;
			BlockPos from = null;
			for (BlockPos feet : BlockPos.betweenClosed(chest.offset(-5, -3, -5), chest.offset(5, 3, 5))) {
				if (fits(level, feet)) {
					spots++; // every spot counted, not only those before the first that works (B590 moved the chest)
					if (from == null && canClick(level, player, feet, chest)) {
						from = feet.immutable();
					}
				}
			}
			// the check itself: a lone chest on open ground is opened from the spot beside it
			helper.setBlock(new BlockPos(2, 2, 18), Blocks.CHEST);
			helper.assertTrue(canClick(level, player, helper.absolutePos(new BlockPos(4, 2, 18)), helper.absolutePos(new BlockPos(2, 2, 18))),
				"the check can't even open a lone chest two blocks away");
			helper.assertTrue(spots > 20, "only " + spots + " places to stand round the camp: the check sees nothing");
			helper.assertTrue(from != null, "no spot round the camp lets a player open the chest of supplies at "
				+ helper.relativePos(chest).toShortString() + " (" + spots + " spots tried)");
		} finally {
			level.getServer().getPlayerList().remove(player);
		}
		helper.succeed();
	}

	/** The chests and barrels in {@code box} no standing player round them can open (none: every one can). */
	static List<String> unopenable(GameTestHelper helper, ServerPlayer player, BoundingBox box) {
		ServerLevel level = helper.getLevel();
		List<String> out = new ArrayList<>();
		int containers = 0;
		for (BlockPos c : BlockPos.betweenClosed(box.minX(), box.minY(), box.minZ(), box.maxX(), box.maxY(), box.maxZ())) {
			BlockState state = level.getBlockState(c);
			if (!state.is(Blocks.CHEST) && !state.is(Blocks.BARREL)) {
				continue;
			}
			containers++;
			boolean ok = false;
			for (BlockPos feet : BlockPos.betweenClosed(c.offset(-5, -3, -5), c.offset(5, 3, 5))) {
				if (fits(level, feet) && canClick(level, player, feet, c)) {
					ok = true;
					break;
				}
			}
			if (!ok) {
				out.add(state.getBlock().getName().getString() + " at " + helper.relativePos(c.immutable()).toShortString());
			}
		}
		if (containers == 0) {
			out.add("no chest or barrel at all");
		}
		return out;
	}

	/** B424's neighbour: the Colony Camp's wagons hid their barrels the same way; every chest and barrel there opens too. */
	//$ gametest_ticks_batch HUGE '40' '"qaColonyCampReach"'
	@GameTest(template = HUGE, timeoutTicks = 40, batch = "qaColonyCampReach")
	public void everyChestAndBarrelInTheColonyCampCanBeOpened(GameTestHelper helper) {
		Leftovers.clear(helper);
		ServerPlayer player = helper.makeMockServerPlayerInLevel();
		try {
			BoundingBox box = SettlersWagonItem.placeCamp(helper.getLevel(), ColonyArrival.COLONY_CAMP, helper.absolutePos(new BlockPos(15, 2, 8)), Rotation.NONE);
			helper.assertTrue(box != null, "the colony camp's template is missing");
			List<String> shut = unopenable(helper, player, box);
			helper.assertTrue(shut.isEmpty(), "in the colony camp no player can open: " + shut);
		} finally {
			helper.getLevel().getServer().getPlayerList().remove(player);
		}
		helper.succeed();
	}

	/** The settlers' camp turned round (a player facing another way): every chest and barrel still opens where it stands. */
	//$ gametest_ticks_batch HUGE '40' '"qaSettlersCampTurnedReach"'
	@GameTest(template = HUGE, timeoutTicks = 40, batch = "qaSettlersCampTurnedReach")
	public void theSettlersCampTurnedRoundStillOpens(GameTestHelper helper) {
		Leftovers.clear(helper);
		ServerPlayer player = helper.makeMockServerPlayerInLevel();
		try {
			for (Rotation rotation : new Rotation[] {Rotation.CLOCKWISE_90, Rotation.CLOCKWISE_180}) {
				BlockPos front = helper.absolutePos(new BlockPos(rotation == Rotation.CLOCKWISE_90 ? 8 : 22, 2, rotation == Rotation.CLOCKWISE_90 ? 8 : 22));
				BoundingBox box = SettlersWagonItem.placeCamp(helper.getLevel(), SettlersWagonItem.CAMP, front, rotation);
				helper.assertTrue(box != null, "the settlers' camp's template is missing");
				List<String> shut = unopenable(helper, player, box);
				helper.assertTrue(shut.isEmpty(), "in the settlers' camp turned " + rotation + " no player can open: " + shut);
			}
		} finally {
			helper.getLevel().getServer().getPlayerList().remove(player);
		}
		helper.succeed();
	}
}
