package io.github.jcondedata.aliveworkplace.test;

import io.github.jcondedata.aliveworkplace.build.BuildSite;
import io.github.jcondedata.aliveworkplace.build.BuildSiteManager;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Container;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CandleCakeBlock;
import net.minecraft.world.level.block.LeverBlock;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.PointedDripstoneBlock;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.piston.PistonBaseBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.AttachFace;
import net.minecraft.world.level.block.state.properties.DripstoneThickness;

/**
 * QA (lane-b-1102-0638) for B793, B794 and B795 after their fix: Scan Tool rebuilds of blocks with no item of their own
 * and of pushed-out pistons, written from the bugs' Expected (each scanned block is built again, or the builder waits;
 * never a broken piston, never a hanging piece with nothing to hang from), in the corners the fix's own tests leave out: a
 * lit candle cake paid from a chest, kelp in a tank, a stalactite, a pushed-out piston turned a
 * quarter, a lever-powered piston, and the builder's site saved and loaded again while hanging vines go up.
 */
public class QaScanRebuild1102cGameTests implements net.fabricmc.fabric.api.gametest.v1.FabricGameTest {
	private static final String HUGE = "aliveworkplace_test:huge_area";
	private static final BlockPos SRC = new BlockPos(18, 2, 18);
	private static final BlockPos SUPPLY = new BlockPos(2, 2, 4);

	/** B793: a lit cake with a red candle, paid from a chest holding exactly its list, stands there and is paid once. */
	//$ gametest_ticks_batch HUGE '2400' '"qa1102cLitCake"'
	@GameTest(template = HUGE, timeoutTicks = 2400, batch = "qa1102cLitCake")
	public void aScannedLitCandleCakeIsBuiltAndPaidOnce(GameTestHelper helper) {
		Leftovers.clear(helper);
		ServerLevel level = helper.getLevel();
		helper.setBlock(SRC, Blocks.STONE);
		helper.setBlock(SRC.above(), Blocks.RED_CANDLE_CAKE.defaultBlockState().setValue(CandleCakeBlock.LIT, true));
		ResourceLocation id = QaBuilders1102bGameTests.scan(helper, SRC, SRC.above());
		helper.setBlock(SRC.above(), Blocks.AIR);
		helper.setBlock(SRC, Blocks.AIR);
		QaBuilders1102bGameTests.Built b = QaBuilders1102bGameTests.buildBack(helper, id, Rotation.NONE, Mirror.NONE, false);
		Map<Item, Integer> asked = b.site().plan(level).materials();
		helper.assertTrue(asked.getOrDefault(Items.CAKE, 0) == 1 && asked.getOrDefault(Items.RED_CANDLE, 0) == 1,
			"a lit cake with a red candle asks " + asked + ", not a cake and a red candle");
		fill(helper, asked);
		helper.succeedWhen(() -> {
			helper.assertTrue(BuildSiteManager.get(level).get(b.site().id()) == null, "still building: " + b.site().stage() + " missing " + b.site().missing());
			BlockState top = level.getBlockState(b.origin().above());
			helper.assertTrue(top.is(Blocks.RED_CANDLE_CAKE), "the lit cake with a candle was built as " + top + " (" + b.site().skipped() + " skipped)");
			Container chest = helper.getBlockEntity(SUPPLY);
			helper.assertTrue(chest.countItem(Items.CAKE) == 0 && chest.countItem(Items.RED_CANDLE) == 0,
				"the chest still holds " + chest.countItem(Items.CAKE) + " cake and " + chest.countItem(Items.RED_CANDLE) + " red candle");
		});
	}

	/** B794: kelp three tall in a glass tank of water is built three tall again from kelp. */
	//$ gametest_ticks_batch HUGE '3000' '"qa1102cKelp"'
	@GameTest(template = HUGE, timeoutTicks = 3000, batch = "qa1102cKelp")
	public void scannedKelpThreeTallInATankIsBuiltThreeTall(GameTestHelper helper) {
		Leftovers.clear(helper);
		ServerLevel level = helper.getLevel();
		BlockPos c = SRC.offset(1, 0, 1);
		for (int dx = -1; dx <= 1; dx++) {
			for (int dz = -1; dz <= 1; dz++) {
				helper.setBlock(c.offset(dx, 0, dz), Blocks.STONE);
				for (int dy = 1; dy <= 4; dy++) {
					if (dx != 0 || dz != 0) {
						helper.setBlock(c.offset(dx, dy, dz), Blocks.GLASS);
					}
				}
			}
		}
		helper.setBlock(c.above(4), Blocks.WATER);
		helper.setBlock(c.above(1), Blocks.KELP_PLANT);
		helper.setBlock(c.above(2), Blocks.KELP_PLANT);
		helper.setBlock(c.above(3), Blocks.KELP);
		ResourceLocation id = QaBuilders1102bGameTests.scan(helper, SRC, SRC.offset(2, 4, 2));
		QaBuilders1102bGameTests.Built b = QaBuilders1102bGameTests.buildBack(helper, id, Rotation.NONE, Mirror.NONE, true);
		Map<Item, Integer> asked = b.site().plan(level).materials();
		helper.assertTrue(asked.getOrDefault(Items.KELP, 0) == 3, "kelp three tall asks " + asked + ", not 3 kelp");
		helper.succeedWhen(() -> {
			helper.assertTrue(BuildSiteManager.get(level).get(b.site().id()) == null, "still building: " + b.site().stage() + " " + b.site().status());
			BlockPos at = b.origin().offset(1, 0, 1);
			int kelp = 0;
			for (int dy = 1; dy <= 3; dy++) {
				BlockState s = level.getBlockState(at.above(dy));
				kelp += s.is(Blocks.KELP) || s.is(Blocks.KELP_PLANT) ? 1 : 0;
			}
			helper.assertTrue(kelp == 3, "the kelp three tall was built " + kelp + " tall (" + b.site().skipped() + " skipped): "
				+ level.getBlockState(at.above(1)) + ", " + level.getBlockState(at.above(2)) + ", " + level.getBlockState(at.above(3)));
		});
	}

	/** B794 (the fix names stalactites): a stalactite three long under a ceiling is built three long, nothing skipped. */
	//$ gametest_ticks_batch HUGE '2400' '"qa1102cStalactite"'
	@GameTest(template = HUGE, timeoutTicks = 2400, batch = "qa1102cStalactite")
	public void aScannedStalactiteThreeLongIsBuiltThreeLong(GameTestHelper helper) {
		Leftovers.clear(helper);
		ServerLevel level = helper.getLevel();
		for (int dy = 0; dy <= 4; dy++) {
			helper.setBlock(SRC.offset(0, dy, 1), Blocks.STONE);
		}
		helper.setBlock(SRC.offset(0, 4, 0), Blocks.DRIPSTONE_BLOCK);
		DripstoneThickness[] t = {DripstoneThickness.TIP, DripstoneThickness.FRUSTUM, DripstoneThickness.BASE};
		for (int dy = 1; dy <= 3; dy++) {
			helper.setBlock(SRC.offset(0, dy, 0), Blocks.POINTED_DRIPSTONE.defaultBlockState()
				.setValue(PointedDripstoneBlock.TIP_DIRECTION, Direction.DOWN).setValue(PointedDripstoneBlock.THICKNESS, t[dy - 1]));
		}
		ResourceLocation id = QaBuilders1102bGameTests.scan(helper, SRC, SRC.offset(0, 4, 1));
		QaBuilders1102bGameTests.Built b = QaBuilders1102bGameTests.buildBack(helper, id, Rotation.NONE, Mirror.NONE, true);
		Map<Item, Integer> asked = b.site().plan(level).materials();
		helper.assertTrue(asked.getOrDefault(Items.POINTED_DRIPSTONE, 0) == 3, "a stalactite three long asks " + asked + ", not 3 pointed dripstone");
		helper.succeedWhen(() -> {
			helper.assertTrue(BuildSiteManager.get(level).get(b.site().id()) == null, "still building: " + b.site().stage() + " " + b.site().status());
			for (int dy = 1; dy <= 3; dy++) {
				BlockState s = level.getBlockState(b.origin().offset(0, dy, 0));
				helper.assertTrue(s.is(Blocks.POINTED_DRIPSTONE) && s.getValue(PointedDripstoneBlock.TIP_DIRECTION) == Direction.DOWN,
					"stalactite piece " + dy + " was built as " + s + " (" + b.site().skipped() + " skipped)");
			}
			helper.assertTrue(b.site().skipped() == 0, b.site().skipped() + " skipped");
		});
	}

	/** B795: the pushed-out sticky piston, built turned a quarter, still stands pushed out with its head in front of it. */
	//$ gametest_ticks_batch HUGE '2400' '"qa1102cPistonTurned"'
	@GameTest(template = HUGE, timeoutTicks = 2400, batch = "qa1102cPistonTurned")
	public void aPushedOutPistonBuiltTurnedAQuarterHasItsHead(GameTestHelper helper) {
		Leftovers.clear(helper);
		ServerLevel level = helper.getLevel();
		for (int dx = 0; dx < 3; dx++) {
			helper.setBlock(SRC.east(dx), Blocks.STONE);
		}
		helper.setBlock(SRC.above(), Blocks.REDSTONE_BLOCK);
		helper.setBlock(SRC.east(1).above(), Blocks.STICKY_PISTON.defaultBlockState().setValue(PistonBaseBlock.FACING, Direction.EAST));
		QaBuilders1102bGameTests.Built[] b = new QaBuilders1102bGameTests.Built[1];
		helper.startSequence().thenWaitUntil(() -> {
			if (!level.getBlockState(helper.absolutePos(SRC.east(2).above())).is(Blocks.PISTON_HEAD)) {
				throw new GameTestAssertException("setup: the piston hasn't pushed out yet");
			}
		}).thenExecute(() -> {
			ResourceLocation id = QaBuilders1102bGameTests.scan(helper, SRC, SRC.east(2).above());
			helper.setBlock(SRC.east(2).above(), Blocks.AIR);
			helper.setBlock(SRC.east(1).above(), Blocks.AIR);
			helper.setBlock(SRC.above(), Blocks.AIR);
			b[0] = QaBuilders1102bGameTests.buildBack(helper, id, Rotation.CLOCKWISE_90, Mirror.NONE, true);
		}).thenWaitUntil(() -> {
			if (BuildSiteManager.get(level).get(b[0].site().id()) != null) {
				throw new GameTestAssertException("still building: " + b[0].site().stage() + " " + b[0].site().status());
			}
		}).thenIdle(10).thenExecute(() -> {
			BlockPos piston = find(level, b[0].origin(), Blocks.STICKY_PISTON.defaultBlockState());
			helper.assertTrue(piston != null, "no sticky piston was built near " + b[0].origin().toShortString());
			BlockState base = level.getBlockState(piston);
			Direction facing = base.getValue(PistonBaseBlock.FACING);
			helper.assertTrue(facing == Direction.SOUTH, "the piston turned a quarter faces " + facing + ", not south");
			BlockState front = level.getBlockState(piston.relative(facing));
			helper.assertTrue(base.getValue(PistonBaseBlock.EXTENDED) && front.is(Blocks.PISTON_HEAD),
				"the turned pushed-out piston was built as " + base + " with " + front + " in front");
		}).thenSucceed();
	}

	/**
	 * B795: a plain piston pushed out by a lever beside it is never left a stuck broken piston: either the lever is built on
	 * and the piston pushed out with its head, or the lever is built off and the piston stands retracted with air in front.
	 */
	//$ gametest_ticks_batch HUGE '2400' '"qa1102cPistonLever"'
	@GameTest(template = HUGE, timeoutTicks = 2400, batch = "qa1102cPistonLever")
	public void aLeverPushedOutPistonIsNeverBuiltStuck(GameTestHelper helper) {
		Leftovers.clear(helper);
		ServerLevel level = helper.getLevel();
		for (int dx = 0; dx < 3; dx++) {
			helper.setBlock(SRC.east(dx), Blocks.STONE);
		}
		helper.setBlock(SRC.east(1).above(), Blocks.PISTON.defaultBlockState().setValue(PistonBaseBlock.FACING, Direction.EAST));
		helper.setBlock(SRC.above(), Blocks.LEVER.defaultBlockState().setValue(LeverBlock.FACE, AttachFace.FLOOR)
			.setValue(LeverBlock.FACING, Direction.EAST).setValue(LeverBlock.POWERED, true));
		helper.getLevel().updateNeighborsAt(helper.absolutePos(SRC.above()), Blocks.LEVER);
		QaBuilders1102bGameTests.Built[] b = new QaBuilders1102bGameTests.Built[1];
		helper.startSequence().thenWaitUntil(() -> {
			if (!level.getBlockState(helper.absolutePos(SRC.east(2).above())).is(Blocks.PISTON_HEAD)) {
				throw new GameTestAssertException("setup: the lever hasn't pushed the piston out yet");
			}
		}).thenExecute(() -> {
			ResourceLocation id = QaBuilders1102bGameTests.scan(helper, SRC, SRC.east(2).above());
			helper.setBlock(SRC.above(), Blocks.AIR);
			helper.setBlock(SRC.east(2).above(), Blocks.AIR);
			helper.setBlock(SRC.east(1).above(), Blocks.AIR);
			b[0] = QaBuilders1102bGameTests.buildBack(helper, id, Rotation.NONE, Mirror.NONE, true);
		}).thenWaitUntil(() -> {
			if (BuildSiteManager.get(level).get(b[0].site().id()) != null) {
				throw new GameTestAssertException("still building: " + b[0].site().stage() + " " + b[0].site().status());
			}
		}).thenIdle(10).thenExecute(() -> {
			BlockState lever = level.getBlockState(b[0].origin().above());
			BlockState base = level.getBlockState(b[0].origin().offset(1, 1, 0));
			BlockState front = level.getBlockState(b[0].origin().offset(2, 1, 0));
			helper.assertTrue(lever.is(Blocks.LEVER), "the lever was built as " + lever);
			helper.assertTrue(base.is(Blocks.PISTON) && base.getValue(PistonBaseBlock.FACING) == Direction.EAST, "the piston was built as " + base);
			boolean on = lever.getValue(LeverBlock.POWERED);
			boolean out = base.getValue(PistonBaseBlock.EXTENDED);
			helper.assertTrue(on == out, "the lever is " + (on ? "on" : "off") + " but the piston is " + (out ? "pushed out" : "retracted"));
			helper.assertTrue(out ? front.is(Blocks.PISTON_HEAD) : front.isAir(), "the piston (" + base + ") has " + front + " in front");
		}).thenSucceed();
	}

	/** B794 with a restart: the site saved and loaded every 7 ticks while vines go up still hangs them three long. */
	//$ gametest_ticks_batch HUGE '3000' '"qa1102cVinesRestart"'
	@GameTest(template = HUGE, timeoutTicks = 3000, batch = "qa1102cVinesRestart")
	public void hangingVinesSavedAndLoadedMidBuildAreStillBuiltThreeLong(GameTestHelper helper) {
		Leftovers.clear(helper);
		ServerLevel level = helper.getLevel();
		for (int dy = 0; dy <= 4; dy++) {
			helper.setBlock(SRC.offset(0, dy, 1), Blocks.STONE);
		}
		helper.setBlock(SRC.offset(0, 4, 0), Blocks.STONE);
		helper.setBlock(SRC.offset(0, 3, 0), Blocks.CAVE_VINES_PLANT);
		helper.setBlock(SRC.offset(0, 2, 0), Blocks.CAVE_VINES_PLANT);
		helper.setBlock(SRC.offset(0, 1, 0), Blocks.CAVE_VINES);
		ResourceLocation id = QaBuilders1102bGameTests.scan(helper, SRC, SRC.offset(0, 4, 1));
		QaBuilders1102bGameTests.Built b = QaBuilders1102bGameTests.buildBack(helper, id, Rotation.NONE, Mirror.NONE, true);
		java.util.UUID siteId = b.site().id();
		int[] ticks = {0};
		int[] restarts = {0};
		helper.onEachTick(() -> {
			if (BuildSiteManager.get(level).get(siteId) == null || ++ticks[0] % 7 != 0) {
				return;
			}
			var registries = level.registryAccess();
			level.getDataStorage().set(BuildSiteManager.NAME,
				BuildSiteManager.load(BuildSiteManager.get(level).save(new CompoundTag(), registries), registries));
			restarts[0]++;
		});
		helper.succeedWhen(() -> {
			BuildSite live = BuildSiteManager.get(level).get(siteId);
			helper.assertTrue(live == null, "still building: " + (live == null ? "" : live.stage() + " " + live.status()));
			int cave = 0;
			for (int dy = 1; dy <= 3; dy++) {
				BlockState s = level.getBlockState(b.origin().offset(0, dy, 0));
				cave += s.is(Blocks.CAVE_VINES) || s.is(Blocks.CAVE_VINES_PLANT) ? 1 : 0;
			}
			helper.assertTrue(restarts[0] >= 2, "only " + restarts[0] + " restarts happened");
			helper.assertTrue(cave == 3, "after " + restarts[0] + " restarts the cave vines were built " + cave + " long");
		});
	}

	/** A spore blossom under a ceiling, scanned and built again, hangs there again (it needs the ceiling above it). */
	//$ gametest_ticks_batch HUGE '2400' '"qa1102cSporeBlossom"'
	@GameTest(template = HUGE, timeoutTicks = 2400, batch = "qa1102cSporeBlossom")
	public void aScannedSporeBlossomUnderACeilingIsBuiltAgain(GameTestHelper helper) {
		underCeiling(helper, Blocks.SPORE_BLOSSOM.defaultBlockState());
	}

	/** Hanging roots under a ceiling, scanned and built again, hang there again. */
	//$ gametest_ticks_batch HUGE '2400' '"qa1102cHangingRoots"'
	@GameTest(template = HUGE, timeoutTicks = 2400, batch = "qa1102cHangingRoots")
	public void scannedHangingRootsUnderACeilingAreBuiltAgain(GameTestHelper helper) {
		underCeiling(helper, Blocks.HANGING_ROOTS.defaultBlockState());
	}

	/** A hanging sign under a ceiling, scanned and built again, hangs there again. */
	//$ gametest_ticks_batch HUGE '2400' '"qa1102cHangingSign"'
	@GameTest(template = HUGE, timeoutTicks = 2400, batch = "qa1102cHangingSign")
	public void aScannedHangingSignUnderACeilingIsBuiltAgain(GameTestHelper helper) {
		underCeiling(helper, Blocks.OAK_HANGING_SIGN.defaultBlockState());
	}

	/** {@code hanging} three blocks up under a stone ceiling held by a stone column, scanned, built back free, is there. */
	private static void underCeiling(GameTestHelper helper, BlockState hanging) {
		Leftovers.clear(helper);
		ServerLevel level = helper.getLevel();
		for (int dy = 0; dy <= 4; dy++) {
			helper.setBlock(SRC.offset(0, dy, 1), Blocks.STONE);
		}
		helper.setBlock(SRC.offset(0, 4, 0), Blocks.STONE);
		helper.setBlock(SRC, Blocks.STONE);
		helper.setBlock(SRC.offset(0, 3, 0), hanging);
		helper.assertTrue(helper.getBlockState(SRC.offset(0, 3, 0)).is(hanging.getBlock()), "setup: " + hanging + " didn't stay");
		ResourceLocation id = QaBuilders1102bGameTests.scan(helper, SRC, SRC.offset(0, 4, 1));
		QaBuilders1102bGameTests.Built b = QaBuilders1102bGameTests.buildBack(helper, id, Rotation.NONE, Mirror.NONE, true);
		Map<Item, Integer> asked = b.site().plan(level).materials();
		helper.assertTrue(asked.getOrDefault(hanging.getBlock().asItem(), 0) == 1, hanging.getBlock() + " asks " + asked);
		helper.succeedWhen(() -> {
			helper.assertTrue(BuildSiteManager.get(level).get(b.site().id()) == null, "still building: " + b.site().stage() + " " + b.site().status());
			BlockState s = level.getBlockState(b.origin().offset(0, 3, 0));
			helper.assertTrue(s.is(hanging.getBlock()), hanging.getBlock() + " under the ceiling was built as " + s + " (" + b.site().skipped() + " skipped)");
		});
	}

	/** The nearest block of {@code state}'s kind within 6 blocks of {@code origin}, or null. */
	private static BlockPos find(ServerLevel level, BlockPos origin, BlockState state) {
		for (BlockPos p : BlockPos.betweenClosed(origin.offset(-6, -1, -6), origin.offset(6, 4, 6))) {
			if (level.getBlockState(p).is(state.getBlock())) {
				return p.immutable();
			}
		}
		return null;
	}

	/** Fills the supply chest with exactly {@code asked} (and dirt for the foundation). */
	private static void fill(GameTestHelper helper, Map<Item, Integer> asked) {
		helper.setBlock(SUPPLY, Blocks.CHEST);
		Container chest = helper.getBlockEntity(SUPPLY);
		int slot = 0;
		for (Map.Entry<Item, Integer> e : asked.entrySet()) {
			chest.setItem(slot++, new ItemStack(e.getKey(), e.getValue()));
		}
		chest.setItem(slot, new ItemStack(Items.DIRT, 64));
	}
}
