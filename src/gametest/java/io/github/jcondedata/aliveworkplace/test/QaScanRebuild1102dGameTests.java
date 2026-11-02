package io.github.jcondedata.aliveworkplace.test;

import io.github.jcondedata.aliveworkplace.build.BuildSiteManager;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.PinkPetalsBlock;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.SeaPickleBlock;
import net.minecraft.world.level.block.TurtleEggBlock;
import net.minecraft.world.level.block.state.BlockState;

/**
 * QA (lane-b-1102-0638): more Scan Tool round trips of blocks whose place depends on what's next to them or that have
 * no item of their own, from the Scan Tool's promise (docs/wiki/scan-tool.md: the builders can build a scan again) and
 * B793/B794's Expected: each block scanned stands again after the build, nothing skipped, and pieces are asked for
 * as a player would use them. Each scene is scanned with real clicks and built back with free materials.
 */
public class QaScanRebuild1102dGameTests implements net.fabricmc.fabric.api.gametest.v1.FabricGameTest {
	private static final String HUGE = "aliveworkplace_test:huge_area";
	static final BlockPos SRC = new BlockPos(18, 2, 18);

	/** A lily pad on a one-block pool. */
	//$ gametest_ticks_batch HUGE '3000' '"qa1102dLilyPad"'
	@GameTest(template = HUGE, timeoutTicks = 3000, batch = "qa1102dLilyPad")
	public void aScannedLilyPadOnAPoolIsBuiltAgain(GameTestHelper helper) {
		Scene s = new Scene();
		for (int dx = 0; dx < 3; dx++) {
			for (int dz = 0; dz < 3; dz++) {
				s.put(dx, 0, dz, Blocks.STONE);
				if (dx != 1 || dz != 1) {
					s.put(dx, 1, dz, Blocks.STONE);
				}
			}
		}
		s.put(1, 1, 1, Blocks.WATER);
		s.put(1, 2, 1, Blocks.LILY_PAD);
		roundTrip(helper, s, Map.of(Items.LILY_PAD, 1), false);
	}

	/** A scaffolding column three tall that is part of the scan stays standing when the builder finishes. */
	//$ gametest_ticks_batch HUGE '3000' '"qa1102dScaffolding"'
	@GameTest(template = HUGE, timeoutTicks = 3000, batch = "qa1102dScaffolding")
	public void aScannedScaffoldingTowerStillStandsAfterTheBuild(GameTestHelper helper) {
		Scene s = new Scene();
		s.put(0, 0, 0, Blocks.STONE);
		for (int dy = 1; dy <= 3; dy++) {
			s.put(0, dy, 0, Blocks.SCAFFOLDING);
		}
		roundTrip(helper, s, Map.of(Items.SCAFFOLDING, 3), false);
	}

	/** Four pink petals in one block are asked as four petals and stand as four. */
	//$ gametest_ticks_batch HUGE '2400' '"qa1102dPetals"'
	@GameTest(template = HUGE, timeoutTicks = 2400, batch = "qa1102dPetals")
	public void fourScannedPinkPetalsAreFourAgain(GameTestHelper helper) {
		Scene s = new Scene();
		s.put(0, 0, 0, Blocks.GRASS_BLOCK);
		s.put(0, 1, 0, Blocks.PINK_PETALS.defaultBlockState().setValue(PinkPetalsBlock.AMOUNT, 4));
		Built b = roundTrip(helper, s, Map.of(Items.PINK_PETALS, 4), true);
		helper.succeedWhen(() -> {
			b.done(helper);
			BlockState p = helper.getLevel().getBlockState(b.at(0, 1, 0));
			helper.assertTrue(p.is(Blocks.PINK_PETALS) && p.getValue(PinkPetalsBlock.AMOUNT) == 4, "the four petals were built as " + p);
		});
	}

	/** Three turtle eggs on sand are asked as three eggs and stand as three. */
	//$ gametest_ticks_batch HUGE '2400' '"qa1102dEggs"'
	@GameTest(template = HUGE, timeoutTicks = 2400, batch = "qa1102dEggs")
	public void threeScannedTurtleEggsAreThreeAgain(GameTestHelper helper) {
		Scene s = new Scene();
		s.put(0, 0, 0, Blocks.SAND);
		s.put(0, 1, 0, Blocks.TURTLE_EGG.defaultBlockState().setValue(TurtleEggBlock.EGGS, 3));
		Built b = roundTrip(helper, s, Map.of(Items.TURTLE_EGG, 3), true);
		helper.succeedWhen(() -> {
			b.done(helper);
			BlockState p = helper.getLevel().getBlockState(b.at(0, 1, 0));
			helper.assertTrue(p.is(Blocks.TURTLE_EGG) && p.getValue(TurtleEggBlock.EGGS) == 3, "the three eggs were built as " + p);
		});
	}

	/** A water cauldron (no item of its own: a cauldron and a water bucket) stands again full. */
	//$ gametest_ticks_batch HUGE '2400' '"qa1102dCauldron"'
	@GameTest(template = HUGE, timeoutTicks = 2400, batch = "qa1102dCauldron")
	public void aScannedWaterCauldronIsBuiltAgainFull(GameTestHelper helper) {
		Scene s = new Scene();
		s.put(0, 0, 0, Blocks.STONE);
		s.put(0, 1, 0, Blocks.WATER_CAULDRON.defaultBlockState().setValue(net.minecraft.world.level.block.LayeredCauldronBlock.LEVEL, 3));
		Built b = roundTrip(helper, s, Map.of(Items.CAULDRON, 1), true);
		helper.succeedWhen(() -> {
			b.done(helper);
			BlockState p = helper.getLevel().getBlockState(b.at(0, 1, 0));
			helper.assertTrue(p.is(Blocks.WATER_CAULDRON), "the full water cauldron was built as " + p);
		});
	}

	/** Farmland and a dirt path (no item of their own: a hoe or a shovel on dirt) stand again, with wheat on the farmland. */
	//$ gametest_ticks_batch HUGE '2400' '"qa1102dFarmland"'
	@GameTest(template = HUGE, timeoutTicks = 2400, batch = "qa1102dFarmland")
	public void scannedFarmlandWithWheatAndADirtPathAreBuiltAgain(GameTestHelper helper) {
		Scene s = new Scene();
		s.put(0, 0, 0, Blocks.STONE);
		s.put(1, 0, 0, Blocks.STONE);
		s.put(0, 1, 0, Blocks.FARMLAND);
		s.put(0, 2, 0, Blocks.WHEAT);
		s.put(1, 1, 0, Blocks.DIRT_PATH);
		roundTrip(helper, s, Map.of(Items.WHEAT_SEEDS, 1), false);
	}

	// --- the round trip -----------------------------------------------------------------------------------------------

	/** Blocks by offset from the scan's lowest corner, set in order. */
	static final class Scene {
		final Map<BlockPos, BlockState> blocks = new LinkedHashMap<>();

		void put(int x, int y, int z, Block block) {
			put(x, y, z, block.defaultBlockState());
		}

		void put(int x, int y, int z, BlockState state) {
			blocks.put(new BlockPos(x, y, z), state);
		}

		BlockPos max() {
			int x = 0, y = 0, z = 0;
			for (BlockPos p : blocks.keySet()) {
				x = Math.max(x, p.getX());
				y = Math.max(y, p.getY());
				z = Math.max(z, p.getZ());
			}
			return new BlockPos(x, y, z);
		}
	}

	record Built(QaBuilders1102bGameTests.Built b, Scene scene) {
		BlockPos at(int x, int y, int z) {
			return b.origin().offset(x, y, z);
		}

		/** The site is gone (done), and every block of the scene stands again (same block), with nothing skipped. */
		void done(GameTestHelper helper) {
			ServerLevel level = helper.getLevel();
			helper.assertTrue(BuildSiteManager.get(level).get(b.site().id()) == null, "still building: " + b.site().stage() + " " + b.site().status()
				+ " missing " + b.site().missing());
			List<String> wrong = new ArrayList<>();
			for (Map.Entry<BlockPos, BlockState> e : scene.blocks.entrySet()) {
				BlockState now = level.getBlockState(b.origin().offset(e.getKey()));
				if (!now.is(e.getValue().getBlock())) {
					wrong.add(e.getKey().toShortString() + " " + e.getValue().getBlock() + " built as " + now);
				}
			}
			helper.assertTrue(wrong.isEmpty(), "not built again (" + b.site().skipped() + " skipped): " + String.join("; ", wrong));
			helper.assertTrue(b.site().skipped() == 0, b.site().skipped() + " skipped");
		}
	}

	/**
	 * Sets the scene at SRC, scans it with real clicks, builds it back free at the usual origin and checks the list asks
	 * exactly {@code asks}; unless {@code ownCheck} (the caller checks more in its own succeedWhen) it waits for {@link Built#done}.
	 */
	static Built roundTrip(GameTestHelper helper, Scene scene, Map<Item, Integer> asks, boolean ownCheck) {
		Leftovers.clear(helper);
		ServerLevel level = helper.getLevel();
		for (Map.Entry<BlockPos, BlockState> e : scene.blocks.entrySet()) {
			helper.setBlock(SRC.offset(e.getKey()), e.getValue());
		}
		List<String> setup = new ArrayList<>();
		for (Map.Entry<BlockPos, BlockState> e : scene.blocks.entrySet()) {
			if (!helper.getBlockState(SRC.offset(e.getKey())).is(e.getValue().getBlock())) {
				setup.add(e.getKey().toShortString() + " " + e.getValue().getBlock() + " is " + helper.getBlockState(SRC.offset(e.getKey())));
			}
		}
		helper.assertTrue(setup.isEmpty(), "setup: " + setup);
		ResourceLocation id = QaBuilders1102bGameTests.scan(helper, SRC, SRC.offset(scene.max()));
		QaBuilders1102bGameTests.Built b = QaBuilders1102bGameTests.buildBack(helper, id, Rotation.NONE, Mirror.NONE, true);
		Map<Item, Integer> asked = b.site().plan(level).materials();
		for (Map.Entry<Item, Integer> e : asks.entrySet()) {
			helper.assertTrue(asked.getOrDefault(e.getKey(), 0) == e.getValue(), "the list asks " + asked + ", not " + e.getValue() + " " + e.getKey());
		}
		Built built = new Built(b, scene);
		if (!ownCheck) {
			helper.succeedWhen(() -> built.done(helper));
		}
		return built;
	}
}
