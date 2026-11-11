package io.github.jcondedata.aliveworkplace.test;

import io.github.jcondedata.aliveworkplace.build.BuildSiteManager;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Container;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.BeehiveBlock;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CandleBlock;
import net.minecraft.world.level.block.ComposterBlock;
import net.minecraft.world.level.block.LadderBlock;
import net.minecraft.world.level.block.PointedDripstoneBlock;
import net.minecraft.world.level.block.SmallDripleafBlock;
import net.minecraft.world.level.block.WallTorchBlock;
import net.minecraft.world.level.block.entity.CampfireBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.level.block.state.properties.DripstoneThickness;

/**
 * QA (lane-b-1102): Scan Tool round trips of blocks no other test scans and builds back, from the Scan Tool's promise
 * (docs/wiki/scan-tool.md: the builders can build a scan again) and B434/B536/B792/B794's Expected: each block stands
 * as scanned with nothing skipped, the list asks pieces as a player uses them, and a build never hands out free goods
 * (fill levels, contents, items a player would have to supply). Scenes are scanned with real clicks and built back with
 * free materials ({@link QaScanRebuild1102dGameTests#roundTrip}).
 */
public class QaScanRebuild1102eGameTests implements net.fabricmc.fabric.api.gametest.v1.FabricGameTest {
	private static final String HUGE = "aliveworkplace_test:huge_area";
	private static final BlockPos SRC = QaScanRebuild1102dGameTests.SRC;

	/** Four candles in one block are asked as four candles and stand as four. */
	//$ gametest_ticks_batch HUGE '2400' '"qa1102eCandles"'
	@GameTest(template = HUGE, timeoutTicks = 2400, batch = "qa1102eCandles")
	public void fourScannedCandlesInOneBlockAreFourAgain(GameTestHelper helper) {
		QaScanRebuild1102dGameTests.Scene s = new QaScanRebuild1102dGameTests.Scene();
		s.put(0, 0, 0, Blocks.STONE);
		s.put(0, 1, 0, Blocks.CANDLE.defaultBlockState().setValue(CandleBlock.CANDLES, 4));
		QaScanRebuild1102dGameTests.Built b = QaScanRebuild1102dGameTests.roundTrip(helper, s, Map.of(Items.CANDLE, 4), true);
		helper.succeedWhen(() -> {
			b.done(helper);
			BlockState p = helper.getLevel().getBlockState(b.at(0, 1, 0));
			helper.assertTrue(p.is(Blocks.CANDLE) && p.getValue(CandleBlock.CANDLES) == 4, "the four candles were built as " + p);
		});
	}

	/** A composter full of ready bone meal is built back empty: the build hands out no bone meal. */
	//$ gametest_ticks_batch HUGE '2400' '"qa1102eComposter"'
	@GameTest(template = HUGE, timeoutTicks = 2400, batch = "qa1102eComposter")
	public void aScannedReadyComposterIsBuiltBackEmpty(GameTestHelper helper) {
		QaScanRebuild1102dGameTests.Scene s = new QaScanRebuild1102dGameTests.Scene();
		s.put(0, 0, 0, Blocks.STONE);
		s.put(0, 1, 0, Blocks.COMPOSTER.defaultBlockState().setValue(ComposterBlock.LEVEL, ComposterBlock.READY));
		QaScanRebuild1102dGameTests.Built b = QaScanRebuild1102dGameTests.roundTrip(helper, s, Map.of(Items.COMPOSTER, 1), true);
		helper.succeedWhen(() -> {
			b.done(helper);
			BlockState p = helper.getLevel().getBlockState(b.at(0, 1, 0));
			helper.assertTrue(p.getValue(ComposterBlock.LEVEL) == 0, "the composter was built with free compost: " + p);
		});
	}

	/** A beehive full of honey (level 5) is built back with no honey in it. */
	//$ gametest_ticks_batch HUGE '2400' '"qa1102eHive"'
	@GameTest(template = HUGE, timeoutTicks = 2400, batch = "qa1102eHive")
	public void aScannedHoneyFullBeehiveIsBuiltWithoutHoney(GameTestHelper helper) {
		QaScanRebuild1102dGameTests.Scene s = new QaScanRebuild1102dGameTests.Scene();
		s.put(0, 0, 0, Blocks.STONE);
		s.put(0, 1, 0, Blocks.BEEHIVE.defaultBlockState().setValue(BeehiveBlock.HONEY_LEVEL, 5));
		QaScanRebuild1102dGameTests.Built b = QaScanRebuild1102dGameTests.roundTrip(helper, s, Map.of(Items.BEEHIVE, 1), true);
		helper.succeedWhen(() -> {
			b.done(helper);
			BlockState p = helper.getLevel().getBlockState(b.at(0, 1, 0));
			helper.assertTrue(p.getValue(BeehiveBlock.HONEY_LEVEL) == 0, "the beehive was built with free honey: " + p);
		});
	}

	/** A pot holding a poppy: the list asks the pot and the poppy, or the pot is built empty. Never a free poppy. */
	//$ gametest_ticks_batch HUGE '2400' '"qa1102ePottedPoppy"'
	@GameTest(template = HUGE, timeoutTicks = 2400, batch = "qa1102ePottedPoppy")
	public void aScannedPottedPoppyAsksThePoppyOrIsBuiltEmpty(GameTestHelper helper) {
		QaScanRebuild1102dGameTests.Scene s = new QaScanRebuild1102dGameTests.Scene();
		s.put(0, 0, 0, Blocks.STONE);
		s.put(0, 1, 0, Blocks.POTTED_POPPY);
		QaScanRebuild1102dGameTests.Built b = QaScanRebuild1102dGameTests.roundTrip(helper, s, Map.of(Items.FLOWER_POT, 1), true);
		Map<Item, Integer> asked = b.b().site().plan(helper.getLevel()).materials();
		helper.succeedWhen(() -> {
			doneExcept(helper, b, Set.of(new BlockPos(0, 1, 0)));
			BlockState p = helper.getLevel().getBlockState(b.at(0, 1, 0));
			helper.assertTrue(p.is(Blocks.POTTED_POPPY) || p.is(Blocks.FLOWER_POT), "the potted poppy was built as " + p);
			helper.assertTrue(!p.is(Blocks.POTTED_POPPY) || asked.getOrDefault(Items.POPPY, 0) == 1,
				"the pot was built with a poppy the list never asked for: the list asks " + asked);
		});
	}

	/** A chiseled bookshelf holding books is built without them: contents are never copied. */
	//$ gametest_ticks_batch HUGE '2400' '"qa1102eShelf"'
	@GameTest(template = HUGE, timeoutTicks = 2400, batch = "qa1102eShelf")
	public void aScannedChiseledBookshelfIsBuiltWithoutItsBooks(GameTestHelper helper) {
		Leftovers.clear(helper);
		helper.setBlock(SRC, Blocks.STONE);
		helper.setBlock(SRC.above(), Blocks.CHISELED_BOOKSHELF.defaultBlockState().setValue(BlockStateProperties.HORIZONTAL_FACING, Direction.SOUTH));
		Container shelf = helper.getBlockEntity(SRC.above());
		shelf.setItem(0, new ItemStack(Items.BOOK));
		shelf.setItem(2, new ItemStack(Items.ENCHANTED_BOOK));
		shelf.setItem(4, new ItemStack(Items.WRITABLE_BOOK));
		helper.assertTrue(!shelf.isEmpty(), "setup: the shelf took no books");
		QaScanRebuild1102dGameTests.Scene s = new QaScanRebuild1102dGameTests.Scene();
		s.put(0, 0, 0, Blocks.STONE);
		s.put(0, 1, 0, helper.getBlockState(SRC.above()));
		QaScanRebuild1102dGameTests.Built b = roundTripAsSet(helper, s, Map.of(Items.CHISELED_BOOKSHELF, 1, Items.BOOK, 0, Items.ENCHANTED_BOOK, 0));
		helper.succeedWhen(() -> {
			b.done(helper);
			helper.assertTrue(helper.getLevel().getBlockEntity(b.at(0, 1, 0)) instanceof Container c && c.isEmpty(),
				"the built shelf holds copied books: " + helper.getLevel().getBlockEntity(b.at(0, 1, 0)));
			BlockState p = helper.getLevel().getBlockState(b.at(0, 1, 0));
			helper.assertTrue(!p.getValue(BlockStateProperties.CHISELED_BOOKSHELF_SLOT_0_OCCUPIED) && !p.getValue(BlockStateProperties.CHISELED_BOOKSHELF_SLOT_2_OCCUPIED)
				&& !p.getValue(BlockStateProperties.CHISELED_BOOKSHELF_SLOT_4_OCCUPIED), "the built shelf shows books it doesn't hold: " + p);
		});
	}

	/** A campfire with raw beef cooking on it is built back with nothing on it. */
	//$ gametest_ticks_batch HUGE '2400' '"qa1102eCampfire"'
	@GameTest(template = HUGE, timeoutTicks = 2400, batch = "qa1102eCampfire")
	public void aScannedCampfireIsBuiltWithoutTheFoodOnIt(GameTestHelper helper) {
		Leftovers.clear(helper);
		helper.setBlock(SRC, Blocks.STONE);
		helper.setBlock(SRC.above(), Blocks.CAMPFIRE);
		CampfireBlockEntity fire = (CampfireBlockEntity) helper.getBlockEntity(SRC.above());
		fire.placeFood(null, new ItemStack(Items.BEEF), 600);
		fire.placeFood(null, new ItemStack(Items.CHICKEN), 600);
		helper.assertTrue(fire.getItems().stream().filter(st -> !st.isEmpty()).count() == 2, "setup: the campfire took " + fire.getItems());
		QaScanRebuild1102dGameTests.Scene s = new QaScanRebuild1102dGameTests.Scene();
		s.put(0, 0, 0, Blocks.STONE);
		s.put(0, 1, 0, helper.getBlockState(SRC.above()));
		QaScanRebuild1102dGameTests.Built b = roundTripAsSet(helper, s, Map.of(Items.BEEF, 0, Items.CHICKEN, 0, Items.COOKED_BEEF, 0));
		helper.succeedWhen(() -> {
			b.done(helper);
			helper.assertTrue(helper.getLevel().getBlockEntity(b.at(0, 1, 0)) instanceof CampfireBlockEntity c && c.getItems().stream().allMatch(ItemStack::isEmpty),
				"the built campfire cooks copied food: " + helper.getLevel().getBlockEntity(b.at(0, 1, 0)));
		});
	}

	/** A lava cauldron: the list asks a lava bucket, or the cauldron is built empty. Never free lava. */
	//$ gametest_ticks_batch HUGE '2400' '"qa1102eLavaCauldron"'
	@GameTest(template = HUGE, timeoutTicks = 2400, batch = "qa1102eLavaCauldron")
	public void aScannedLavaCauldronAsksLavaOrIsBuiltEmpty(GameTestHelper helper) {
		QaScanRebuild1102dGameTests.Scene s = new QaScanRebuild1102dGameTests.Scene();
		s.put(0, 0, 0, Blocks.STONE);
		s.put(0, 1, 0, Blocks.LAVA_CAULDRON);
		QaScanRebuild1102dGameTests.Built b = QaScanRebuild1102dGameTests.roundTrip(helper, s, Map.of(Items.CAULDRON, 1), true);
		Map<Item, Integer> asked = b.b().site().plan(helper.getLevel()).materials();
		helper.succeedWhen(() -> {
			doneExcept(helper, b, Set.of(new BlockPos(0, 1, 0)));
			BlockState p = helper.getLevel().getBlockState(b.at(0, 1, 0));
			helper.assertTrue(p.is(Blocks.LAVA_CAULDRON) || p.is(Blocks.CAULDRON), "the lava cauldron was built as " + p);
			helper.assertTrue(!p.is(Blocks.LAVA_CAULDRON) || asked.getOrDefault(Items.LAVA_BUCKET, 0) == 1,
				"the cauldron was built full of lava the list never asked for: the list asks " + asked);
		});
	}

	/** A cactus 3 tall on sand and bamboo 3 tall on dirt stand 3 tall again, from 3 cactus and 3 bamboo. */
	//$ gametest_ticks_batch HUGE '3000' '"qa1102eCactusBamboo"'
	@GameTest(template = HUGE, timeoutTicks = 3000, batch = "qa1102eCactusBamboo")
	public void scannedCactusAndBambooColumnsAreThreeTallAgain(GameTestHelper helper) {
		QaScanRebuild1102dGameTests.Scene s = new QaScanRebuild1102dGameTests.Scene();
		s.put(0, 0, 0, Blocks.SAND);
		s.put(2, 0, 0, Blocks.DIRT);
		for (int dy = 1; dy <= 3; dy++) {
			s.put(0, dy, 0, Blocks.CACTUS);
			s.put(2, dy, 0, Blocks.BAMBOO);
		}
		QaScanRebuild1102dGameTests.roundTrip(helper, s, Map.of(Items.CACTUS, 3, Items.BAMBOO, 3), false);
	}

	/** A torch on the side of a stone block and a ladder column 3 tall against a wall are built again, nothing skipped. */
	//$ gametest_ticks_batch HUGE '3000' '"qa1102eTorchLadder"'
	@GameTest(template = HUGE, timeoutTicks = 3000, batch = "qa1102eTorchLadder")
	public void aScannedWallTorchAndLadderColumnAreBuiltAgain(GameTestHelper helper) {
		QaScanRebuild1102dGameTests.Scene s = new QaScanRebuild1102dGameTests.Scene();
		for (int dx = 0; dx < 4; dx++) {
			s.put(dx, 0, 0, Blocks.STONE);
			s.put(dx, 0, 1, Blocks.STONE);
		}
		// the torch on the south side of a stone block
		s.put(0, 1, 0, Blocks.STONE);
		s.put(0, 1, 1, Blocks.WALL_TORCH.defaultBlockState().setValue(WallTorchBlock.FACING, Direction.SOUTH));
		// a wall 3 tall, the ladders on its south side
		for (int dy = 1; dy <= 3; dy++) {
			s.put(3, dy, 0, Blocks.STONE);
			s.put(3, dy, 1, Blocks.LADDER.defaultBlockState().setValue(LadderBlock.FACING, Direction.SOUTH));
		}
		QaScanRebuild1102dGameTests.Built b = QaScanRebuild1102dGameTests.roundTrip(helper, s, Map.of(Items.TORCH, 1, Items.LADDER, 3), true);
		helper.succeedWhen(() -> {
			b.done(helper);
			BlockState t = helper.getLevel().getBlockState(b.at(0, 1, 1));
			helper.assertTrue(t.getValue(WallTorchBlock.FACING) == Direction.SOUTH, "the wall torch faces " + t);
		});
	}

	/**
	 * A chorus plant with a flower on top, on end stone, is built again with nothing skipped. B872: each stalk piece costs
	 * the chorus flower that grows it (its own item is creative-only), so two pieces and the flower ask three flowers.
	 */
	//$ gametest_ticks_batch HUGE '3000' '"qa1102eChorus"'
	@GameTest(template = HUGE, timeoutTicks = 3000, batch = "qa1102eChorus")
	public void aScannedChorusPlantAndFlowerAreBuiltAgain(GameTestHelper helper) {
		QaScanRebuild1102dGameTests.Scene s = new QaScanRebuild1102dGameTests.Scene();
		s.put(0, 0, 0, Blocks.END_STONE);
		s.put(0, 1, 0, Blocks.CHORUS_PLANT.defaultBlockState().setValue(BlockStateProperties.DOWN, true).setValue(BlockStateProperties.UP, true));
		s.put(0, 2, 0, Blocks.CHORUS_PLANT.defaultBlockState().setValue(BlockStateProperties.DOWN, true).setValue(BlockStateProperties.UP, true));
		s.put(0, 3, 0, Blocks.CHORUS_FLOWER);
		QaScanRebuild1102dGameTests.roundTrip(helper, s, Map.of(Items.CHORUS_FLOWER, 3), false);
	}

	/** A small dripleaf on clay (two blocks) is built as both halves, from one small dripleaf. */
	//$ gametest_ticks_batch HUGE '2400' '"qa1102eSmallDripleaf"'
	@GameTest(template = HUGE, timeoutTicks = 2400, batch = "qa1102eSmallDripleaf")
	public void aScannedSmallDripleafIsBuiltAsBothHalves(GameTestHelper helper) {
		QaScanRebuild1102dGameTests.Scene s = new QaScanRebuild1102dGameTests.Scene();
		s.put(0, 0, 0, Blocks.CLAY);
		s.put(0, 1, 0, Blocks.SMALL_DRIPLEAF.defaultBlockState().setValue(SmallDripleafBlock.HALF, DoubleBlockHalf.LOWER));
		s.put(0, 2, 0, Blocks.SMALL_DRIPLEAF.defaultBlockState().setValue(SmallDripleafBlock.HALF, DoubleBlockHalf.UPPER));
		QaScanRebuild1102dGameTests.Built b = QaScanRebuild1102dGameTests.roundTrip(helper, s, Map.of(Items.SMALL_DRIPLEAF, 1), true);
		helper.succeedWhen(() -> {
			b.done(helper);
			BlockState lo = helper.getLevel().getBlockState(b.at(0, 1, 0));
			BlockState hi = helper.getLevel().getBlockState(b.at(0, 2, 0));
			helper.assertTrue(lo.getValue(SmallDripleafBlock.HALF) == DoubleBlockHalf.LOWER && hi.getValue(SmallDripleafBlock.HALF) == DoubleBlockHalf.UPPER,
				"the dripleaf halves were built as " + lo + " / " + hi);
		});
	}

	/** A stalagmite (pointed dripstone standing up) 2 tall on stone is built 2 tall again, from 2 pointed dripstone. */
	//$ gametest_ticks_batch HUGE '2400' '"qa1102eStalagmite"'
	@GameTest(template = HUGE, timeoutTicks = 2400, batch = "qa1102eStalagmite")
	public void aScannedStalagmiteIsBuiltTwoTallAgain(GameTestHelper helper) {
		QaScanRebuild1102dGameTests.Scene s = new QaScanRebuild1102dGameTests.Scene();
		s.put(0, 0, 0, Blocks.STONE);
		s.put(0, 1, 0, Blocks.POINTED_DRIPSTONE.defaultBlockState().setValue(PointedDripstoneBlock.TIP_DIRECTION, Direction.UP)
			.setValue(PointedDripstoneBlock.THICKNESS, DripstoneThickness.FRUSTUM));
		s.put(0, 2, 0, Blocks.POINTED_DRIPSTONE.defaultBlockState().setValue(PointedDripstoneBlock.TIP_DIRECTION, Direction.UP)
			.setValue(PointedDripstoneBlock.THICKNESS, DripstoneThickness.TIP));
		QaScanRebuild1102dGameTests.Built b = QaScanRebuild1102dGameTests.roundTrip(helper, s, Map.of(Items.POINTED_DRIPSTONE, 2), true);
		helper.succeedWhen(() -> {
			b.done(helper);
			for (int dy = 1; dy <= 2; dy++) {
				BlockState p = helper.getLevel().getBlockState(b.at(0, dy, 0));
				helper.assertTrue(p.getValue(PointedDripstoneBlock.TIP_DIRECTION) == Direction.UP, "the stalagmite's block at +" + dy + " was built as " + p);
			}
		});
	}

	// --- helpers ------------------------------------------------------------------------------------------------------

	/**
	 * For a scene whose block entities the caller filled at SRC before: {@link QaScanRebuild1102dGameTests#roundTrip}
	 * would set the scene's blocks again (wiping the contents), so the blocks are left as they are and only scanned.
	 */
	private static QaScanRebuild1102dGameTests.Built roundTripAsSet(GameTestHelper helper, QaScanRebuild1102dGameTests.Scene scene, Map<Item, Integer> asks) {
		ServerLevel level = helper.getLevel();
		net.minecraft.resources.ResourceLocation id = QaBuilders1102bGameTests.scan(helper, SRC, SRC.offset(scene.max()));
		QaBuilders1102bGameTests.Built b = QaBuilders1102bGameTests.buildBack(helper, id, net.minecraft.world.level.block.Rotation.NONE,
			net.minecraft.world.level.block.Mirror.NONE, true);
		Map<Item, Integer> asked = b.site().plan(level).materials();
		for (Map.Entry<Item, Integer> e : asks.entrySet()) {
			helper.assertTrue(asked.getOrDefault(e.getKey(), 0) == e.getValue(), "the list asks " + asked + ", not " + e.getValue() + " " + e.getKey());
		}
		return new QaScanRebuild1102dGameTests.Built(b, scene);
	}

	/** {@link QaScanRebuild1102dGameTests.Built#done}, with the blocks at {@code except} left for the caller to check. */
	private static void doneExcept(GameTestHelper helper, QaScanRebuild1102dGameTests.Built built, Set<BlockPos> except) {
		ServerLevel level = helper.getLevel();
		QaBuilders1102bGameTests.Built b = built.b();
		helper.assertTrue(BuildSiteManager.get(level).get(b.site().id()) == null, "still building: " + b.site().stage() + " " + b.site().status()
			+ " missing " + b.site().missing());
		List<String> wrong = new ArrayList<>();
		for (Map.Entry<BlockPos, BlockState> e : built.scene().blocks.entrySet()) {
			if (except.contains(e.getKey())) {
				continue;
			}
			BlockState now = level.getBlockState(b.origin().offset(e.getKey()));
			if (!now.is(e.getValue().getBlock())) {
				wrong.add(e.getKey().toShortString() + " " + e.getValue().getBlock() + " built as " + now);
			}
		}
		helper.assertTrue(wrong.isEmpty(), "not built again (" + b.site().skipped() + " skipped): " + String.join("; ", wrong));
		helper.assertTrue(b.site().skipped() == 0, b.site().skipped() + " skipped");
	}
}
