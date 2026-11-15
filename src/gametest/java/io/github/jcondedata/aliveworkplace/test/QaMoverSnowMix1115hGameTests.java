package io.github.jcondedata.aliveworkplace.test;

import io.github.jcondedata.aliveworkplace.move.MoveSite;
import java.util.ArrayList;
import java.util.List;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CandleBlock;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.DoublePlantBlock;
import net.minecraft.world.level.block.MultifaceBlock;
import net.minecraft.world.level.block.PitcherCropBlock;
import net.minecraft.world.level.block.SeaPickleBlock;
import net.minecraft.world.level.block.SlabBlock;
import net.minecraft.world.level.block.SmallDripleafBlock;
import net.minecraft.world.level.block.SnowLayerBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.level.block.state.properties.SlabType;

/**
 * QA (lane b, 2026-11-15, h), past B1274, B1281, B1282 and B1283's fixes, written from their text and moving-buildings.md
 * ("nothing packed is ever deleted"; "a block that is several of its item drops all of them", never doubled; "1 to 7 snow
 * layers are that many snow ... 8 layers are 8 snow"; "a two-block crop (a pitcher crop) is what its lower half's loot
 * gives ... a grown one a pitcher plant, an ungrown one a pitcher pod"). What earlier QA classes left out: the snow counts
 * 2, 3, 5, 6 and 7 in a full move onto his crate (1, 4 and 8 were done), 3 and 8 layers through a save and reload with the
 * snow packed, the other blocks the "whole loot holds fewer than one piece's" rule now reaches (a sea pickle's,
 * candle's and slab's counts asked many times, a double slab), each age of a pitcher crop and either half, a grown pitcher
 * crop whose lower half lands on his crate in a full move, and two-block things no class asked yet (small dripleaf, every
 * kind of door). Same house, Mover and spot as MoverCrateNoRoomB1234GameTests (the chest's spot lands on his crate).
 */
public class QaMoverSnowMix1115hGameTests implements FabricGameTest {
	private static final String HUGE = "aliveworkplace_test:huge_area";
	/** The chest's spot: lands on his crate. */
	private static final BlockPos SPOT = new BlockPos(4, 3, 4);

	private static int count(List<ItemStack> stacks, Item item) {
		int n = 0;
		for (ItemStack s : stacks) {
			n += s.is(item) ? s.getCount() : 0;
		}
		return n;
	}

	private static int total(List<ItemStack> stacks) {
		int n = 0;
		for (ItemStack s : stacks) {
			n += s.getCount();
		}
		return n;
	}

	/**
	 * {@code layers} snow layers on his crate in a full move, the site saved and loaded back after it's all packed when
	 * {@code reload}: exactly that many snow, no snowball, no snow block, none standing, the move done with that one block skipped.
	 */
	private static void snowMove(GameTestHelper helper, int layers, boolean reload, String name) {
		ServerLevel level = helper.getLevel();
		MoverGameTests.Setup s = MoverCrateNoRoomB1234GameTests.crateInside(helper, name);
		QaMoverCrateVerify1114fGameTests.place(helper, Blocks.SNOW.defaultBlockState().setValue(SnowLayerBlock.LAYERS, layers));
		helper.runAfterDelay(2, () -> {
			BlockState snow = level.getBlockState(helper.absolutePos(SPOT));
			helper.assertTrue(snow.is(Blocks.SNOW) && snow.getValue(SnowLayerBlock.LAYERS) == layers, "setup: the snow is now " + snow);
			String what = layers + " snow layer(s) on his crate" + (reload ? ", saved and loaded back once packed" : "");
			MoveSite[] reloaded = new MoveSite[1];
			QaMoverCrateQa1114c4GameTests.toTheEnd(helper, s, () -> {
				if (reload) {
					helper.assertTrue(QaMoverCrateHolds1114cGameTests.standing(helper, Blocks.SNOW) == 0, what + ": setup, the snow isn't packed yet");
					reloaded[0] = MoverGameTests.reload(level, s);
				}
			}, what);
			helper.assertTrue(!reload || reloaded[0] != null, what + ": setup, never reloaded");
			MoverCrateNoRoomB1234GameTests.assertExactly(helper, Items.SNOWBALL, 0, what + ": snowballs");
			MoverCrateNoRoomB1234GameTests.assertExactly(helper, Items.SNOW_BLOCK, 0, what + ": snow blocks");
			MoverCrateNoRoomB1234GameTests.assertExactly(helper, Items.SNOW, layers, what);
			helper.assertTrue(QaMoverCrateHolds1114cGameTests.standing(helper, Blocks.SNOW) == 0, what + ": snow left standing");
			helper.succeed();
		});
	}

	// ---- snow: every count in a full move ---------------------------------------------------------------------------

	//$ gametest_batch HUGE '"qaH1115hSnow2"'
	@GameTest(template = HUGE, batch = "qaH1115hSnow2")
	public void twoSnowLayersOnHisCrateAreTwoSnow(GameTestHelper helper) {
		snowMove(helper, 2, false, "qah1115hsnow2");
	}

	//$ gametest_batch HUGE '"qaH1115hSnow3"'
	@GameTest(template = HUGE, batch = "qaH1115hSnow3")
	public void threeSnowLayersOnHisCrateAreThreeSnow(GameTestHelper helper) {
		snowMove(helper, 3, false, "qah1115hsnow3");
	}

	//$ gametest_batch HUGE '"qaH1115hSnow5"'
	@GameTest(template = HUGE, batch = "qaH1115hSnow5")
	public void fiveSnowLayersOnHisCrateAreFiveSnow(GameTestHelper helper) {
		snowMove(helper, 5, false, "qah1115hsnow5");
	}

	//$ gametest_batch HUGE '"qaH1115hSnow6"'
	@GameTest(template = HUGE, batch = "qaH1115hSnow6")
	public void sixSnowLayersOnHisCrateAreSixSnow(GameTestHelper helper) {
		snowMove(helper, 6, false, "qah1115hsnow6");
	}

	/** Seven, the last count under a full block's height: 7 snow, never a snow block. */
	//$ gametest_batch HUGE '"qaH1115hSnow7"'
	@GameTest(template = HUGE, batch = "qaH1115hSnow7")
	public void sevenSnowLayersOnHisCrateAreSevenSnow(GameTestHelper helper) {
		snowMove(helper, 7, false, "qah1115hsnow7");
	}

	// ---- snow through a save and reload -----------------------------------------------------------------------------

	/** Eight layers packed, the site saved and read back, then put up: 8 snow, not a snow block and 7, not 1. */
	//$ gametest_batch HUGE '"qaH1115hSnow8Reload"'
	@GameTest(template = HUGE, batch = "qaH1115hSnow8Reload")
	public void eightSnowLayersPackedThenReloadedAreEightSnow(GameTestHelper helper) {
		snowMove(helper, 8, true, "qah1115hsnow8r");
	}

	/** Three layers packed, the site saved and read back, then put up: 3 snow. */
	//$ gametest_batch HUGE '"qaH1115hSnow3Reload"'
	@GameTest(template = HUGE, batch = "qaH1115hSnow3Reload")
	public void threeSnowLayersPackedThenReloadedAreThreeSnow(GameTestHelper helper) {
		snowMove(helper, 3, true, "qah1115hsnow3r");
	}

	// ---- snow whose new spot gets a crate set down after the move is confirmed ----------------------------------------

	/** The barrel's spot in the house; with the move (2 south-east, not turned) it lands here. */
	private static final BlockPos BARREL = new BlockPos(6, 3, 4);
	private static final BlockPos BARREL_LANDS = new BlockPos(8, 3, 6);

	/**
	 * {@code layers} snow layers where the barrel stood; once the house is down a player sets a Mover's Crate (not his)
	 * where they'd go up (moving-buildings.md: a crate set down after the move is confirmed is never broken, and the
	 * block is dropped, nothing lost). Exactly that many snow, no snowball or snow block, none standing, the crate standing.
	 */
	private static void snowOnAPlayersCrate(GameTestHelper helper, int layers, String name) {
		ServerLevel level = helper.getLevel();
		MoverGameTests.Setup s = MoverCrateNoRoomB1234GameTests.crateInside(helper, name);
		BlockPos at = helper.absolutePos(BARREL);
		net.minecraft.world.Clearable.tryClear(level.getBlockEntity(at));
		level.setBlock(at, Blocks.AIR.defaultBlockState(), Block.UPDATE_CLIENTS);
		level.setBlock(at, Blocks.SNOW.defaultBlockState().setValue(SnowLayerBlock.LAYERS, layers), Block.UPDATE_CLIENTS);
		helper.runAfterDelay(2, () -> {
			helper.assertTrue(level.getBlockState(at).is(Blocks.SNOW), "setup: no snow where the barrel stood: " + level.getBlockState(at));
			String what = layers + " snow layer(s) whose new spot got a player's Mover's Crate mid-move";
			MoveSite site = QaMoverCrateVerify1114fGameTests.run(helper, s, () -> {
				helper.assertTrue(level.getBlockState(helper.absolutePos(BARREL_LANDS)).isAir(), what + ": setup, the spot isn't clear: "
					+ level.getBlockState(helper.absolutePos(BARREL_LANDS)));
				helper.setBlock(BARREL_LANDS, io.github.jcondedata.aliveworkplace.registry.ModBlocks.MOVERS_CRATE);
			});
			helper.assertTrue(site.stage() == MoveSite.Stage.DONE && !site.canceled(), what + ": the move ended " + site.stage());
			helper.assertTrue(level.getBlockState(helper.absolutePos(BARREL_LANDS)).is(io.github.jcondedata.aliveworkplace.registry.ModBlocks.MOVERS_CRATE),
				what + ": the player's crate is gone: " + level.getBlockState(helper.absolutePos(BARREL_LANDS)));
			MoverCrateNoRoomB1234GameTests.assertExactly(helper, Items.SNOWBALL, 0, what + ": snowballs");
			MoverCrateNoRoomB1234GameTests.assertExactly(helper, Items.SNOW_BLOCK, 0, what + ": snow blocks");
			MoverCrateNoRoomB1234GameTests.assertExactly(helper, Items.SNOW, layers, what);
			helper.assertTrue(QaMoverCrateHolds1114cGameTests.standing(helper, Blocks.SNOW) == 0, what + ": snow left standing");
			helper.succeed();
		});
	}

	/** Eight layers whose new spot got a player's crate: 8 snow. */
	//$ gametest_batch HUGE '"qaH1115hSnow8Players"'
	@GameTest(template = HUGE, batch = "qaH1115hSnow8Players")
	public void eightSnowLayersOnAPlayersCrateSetDownMidMoveAreEightSnow(GameTestHelper helper) {
		snowOnAPlayersCrate(helper, 8, "qah1115hsnow8p");
	}

	/** One layer whose new spot got a player's crate: 1 snow, not a snowball. */
	//$ gametest_batch HUGE '"qaH1115hSnow1Players"'
	@GameTest(template = HUGE, batch = "qaH1115hSnow1Players")
	public void oneSnowLayerOnAPlayersCrateSetDownMidMoveIsOneSnow(GameTestHelper helper) {
		snowOnAPlayersCrate(helper, 1, "qah1115hsnow1p");
	}

	/** Four layers whose new spot got a player's crate: 4 snow. */
	//$ gametest_batch HUGE '"qaH1115hSnow4Players"'
	@GameTest(template = HUGE, batch = "qaH1115hSnow4Players")
	public void fourSnowLayersOnAPlayersCrateSetDownMidMoveAreFourSnow(GameTestHelper helper) {
		snowOnAPlayersCrate(helper, 4, "qah1115hsnow4p");
	}

	// ---- the "fewer than one piece's" rule: other blocks, asked many times ----------------------------------------------

	/**
	 * Every snow count 1 to 8 asked 16 times each at the same spot, with a different neighbour each round (air, stone under
	 * it, snow next to it): always exactly that many snow, nothing else; a snow block is one snow block. The B1281 rule
	 * must not change its answer by what stands round the spot.
	 */
	//$ gametest_batch HUGE '"qaH1115hSnowRounds"'
	@GameTest(template = HUGE, batch = "qaH1115hSnowRounds")
	public void everySnowCountIsThatManySnowWhateverStandsNextToIt(GameTestHelper helper) {
		Leftovers.clear(helper);
		ServerLevel level = helper.getLevel();
		BlockPos pos = helper.absolutePos(new BlockPos(5, 3, 5));
		BlockState[] next = {Blocks.AIR.defaultBlockState(), Blocks.STONE.defaultBlockState(), Blocks.SNOW.defaultBlockState().setValue(SnowLayerBlock.LAYERS, 8)};
		StringBuilder bad = new StringBuilder();
		for (BlockState n : next) {
			level.setBlock(pos.below(), n, Block.UPDATE_CLIENTS);
			level.setBlock(pos.east(), n, Block.UPDATE_CLIENTS);
			for (int layers = 1; layers <= 8; layers++) {
				for (int i = 0; i < 16; i++) {
					List<ItemStack> drops = QaMoverCrateFixes1115cGameTests.itself(level, pos, Blocks.SNOW.defaultBlockState().setValue(SnowLayerBlock.LAYERS, layers));
					if (count(drops, Items.SNOW) != layers || total(drops) != layers) {
						bad.append(layers).append(" layer(s) by ").append(n.getBlock()).append(": ").append(drops).append("; ");
						break;
					}
				}
			}
			List<ItemStack> block = QaMoverCrateFixes1115cGameTests.itself(level, pos, Blocks.SNOW_BLOCK.defaultBlockState());
			if (count(block, Items.SNOW_BLOCK) != 1 || total(block) != 1) {
				bad.append("a snow block by ").append(n.getBlock()).append(": ").append(block).append("; ");
			}
		}
		level.setBlock(pos.below(), Blocks.AIR.defaultBlockState(), Block.UPDATE_CLIENTS);
		level.setBlock(pos.east(), Blocks.AIR.defaultBlockState(), Block.UPDATE_CLIENTS);
		helper.assertTrue(bad.isEmpty(), "snow on a crate wasn't that many snow (B1274, B1281): " + bad);
		helper.succeed();
	}

	/**
	 * The blocks whose loot already counts their pieces (sea pickles dry and waterlogged, candles of a color lit and unlit)
	 * asked 16 times each count: exactly that many, never split into pieces of something else or doubled; a slab is 1
	 * slab, a double slab 2 (oak, stone, waterlogged), never 1, 3 or 4; a full block is 1.
	 */
	//$ gametest_batch HUGE '"qaH1115hManyRounds"'
	@GameTest(template = HUGE, batch = "qaH1115hManyRounds")
	public void picklesCandlesAndSlabsAreExactlyTheirPiecesEveryTime(GameTestHelper helper) {
		Leftovers.clear(helper);
		ServerLevel level = helper.getLevel();
		BlockPos pos = helper.absolutePos(new BlockPos(5, 3, 5));
		List<String> bad = new ArrayList<>();
		for (int i = 0; i < 16; i++) {
			for (int n = 1; n <= 4; n++) {
				for (boolean wet : new boolean[] {false, true}) {
					expect(level, pos, Blocks.SEA_PICKLE.defaultBlockState().setValue(SeaPickleBlock.PICKLES, n).setValue(SeaPickleBlock.WATERLOGGED, wet),
						Items.SEA_PICKLE, n, bad);
					expect(level, pos, Blocks.BLUE_CANDLE.defaultBlockState().setValue(CandleBlock.CANDLES, n).setValue(CandleBlock.LIT, wet), Items.BLUE_CANDLE, n, bad);
				}
			}
			for (Block slab : new Block[] {Blocks.OAK_SLAB, Blocks.STONE_SLAB, Blocks.CUT_COPPER_SLAB}) {
				expect(level, pos, slab.defaultBlockState().setValue(SlabBlock.TYPE, SlabType.BOTTOM), slab.asItem(), 1, bad);
				expect(level, pos, slab.defaultBlockState().setValue(SlabBlock.TYPE, SlabType.TOP).setValue(SlabBlock.WATERLOGGED, true), slab.asItem(), 1, bad);
				expect(level, pos, slab.defaultBlockState().setValue(SlabBlock.TYPE, SlabType.DOUBLE), slab.asItem(), 2, bad);
			}
			expect(level, pos, Blocks.OAK_PLANKS.defaultBlockState(), Items.OAK_PLANKS, 1, bad);
		}
		helper.assertTrue(bad.isEmpty(), "dropped the wrong count on a crate (nothing lost, nothing doubled): " + bad.stream().distinct().toList());
		helper.succeed();
	}

	private static void expect(ServerLevel level, BlockPos pos, BlockState state, Item item, int n, List<String> bad) {
		List<ItemStack> drops = QaMoverCrateFixes1115cGameTests.itself(level, pos, state);
		if (count(drops, item) != n || total(drops) != n) {
			bad.add(state + " -> " + drops + " (expected " + n + " " + item + ")");
		}
	}

	// ---- B1283: pitcher crops and other two-block things -------------------------------------------------------------

	/**
	 * Each age of a pitcher crop, its lower half (and, from age 2 where it has one, its upper half too), asked 16 times as
	 * the crate's drop works it out: age 4 is exactly one Pitcher Plant, ages 0 to 3 exactly one Pitcher Pod; never both,
	 * never two, never nothing.
	 */
	//$ gametest_batch HUGE '"qaH1115hPitcherAges"'
	@GameTest(template = HUGE, batch = "qaH1115hPitcherAges")
	public void everyAgeOfAPitcherCropIsOnePlantWhenGrownElseOnePod(GameTestHelper helper) {
		Leftovers.clear(helper);
		ServerLevel level = helper.getLevel();
		BlockPos lower = helper.absolutePos(new BlockPos(5, 3, 5));
		level.setBlock(lower.below(), Blocks.FARMLAND.defaultBlockState(), Block.UPDATE_CLIENTS);
		List<String> bad = new ArrayList<>();
		for (int age = 0; age <= PitcherCropBlock.MAX_AGE; age++) {
			BlockState lo = Blocks.PITCHER_CROP.defaultBlockState().setValue(PitcherCropBlock.AGE, age).setValue(DoublePlantBlock.HALF, DoubleBlockHalf.LOWER);
			BlockState up = lo.setValue(DoublePlantBlock.HALF, DoubleBlockHalf.UPPER);
			boolean tall = age >= 2;
			level.setBlock(lower, lo, Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE);
			level.setBlock(lower.above(), tall ? up : Blocks.AIR.defaultBlockState(), Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE);
			Item want = age == PitcherCropBlock.MAX_AGE ? Items.PITCHER_PLANT : Items.PITCHER_POD;
			for (int i = 0; i < 16; i++) {
				List<ItemStack> l = QaMoverCrateFixes1115cGameTests.itself(level, lower, lo);
				if (total(l) != 1 || count(l, want) != 1) {
					bad.add("age " + age + " lower: " + l);
				}
				if (tall) {
					List<ItemStack> u = QaMoverCrateFixes1115cGameTests.itself(level, lower.above(), up);
					if (total(u) != 1 || count(u, want) != 1) {
						bad.add("age " + age + " upper: " + u);
					}
				}
			}
		}
		level.setBlock(lower.above(), Blocks.AIR.defaultBlockState(), Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE);
		level.setBlock(lower, Blocks.AIR.defaultBlockState(), Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE);
		level.setBlock(lower.below(), Blocks.AIR.defaultBlockState(), Block.UPDATE_CLIENTS);
		helper.assertTrue(bad.isEmpty(), "pitcher crops on a crate not one plant (grown) or one pod (ungrown): " + bad.stream().distinct().toList());
		helper.succeed();
	}

	/** A grown pitcher crop (age 4) whose lower half lands on his crate, upper half over it, a full move: one Pitcher Plant, no pod, no half left. */
	//$ gametest_batch HUGE '"qaH1115hPitcherMove"'
	@GameTest(template = HUGE, batch = "qaH1115hPitcherMove")
	public void aGrownPitcherCropWhoseLowerHalfLandsOnHisCrateIsOnePitcherPlant(GameTestHelper helper) {
		pitcherMove(helper, PitcherCropBlock.MAX_AGE, false, Items.PITCHER_PLANT, "qah1115hpitcher4");
	}

	/** A grown pitcher crop whose UPPER half lands on his crate (its lower half under it): one Pitcher Plant, no pod, no half left. */
	//$ gametest_batch HUGE '"qaH1115hPitcherUpper"'
	@GameTest(template = HUGE, batch = "qaH1115hPitcherUpper")
	public void aGrownPitcherCropWhoseUpperHalfLandsOnHisCrateIsOnePitcherPlant(GameTestHelper helper) {
		pitcherMove(helper, PitcherCropBlock.MAX_AGE, true, Items.PITCHER_PLANT, "qah1115hpitcher4u");
	}

	/** A half-grown pitcher crop (age 3, two blocks tall) whose lower half lands on his crate: one Pitcher Pod, no plant. */
	//$ gametest_batch HUGE '"qaH1115hPitcherYoung"'
	@GameTest(template = HUGE, batch = "qaH1115hPitcherYoung")
	public void anUngrownTallPitcherCropOnHisCrateIsOnePitcherPod(GameTestHelper helper) {
		pitcherMove(helper, 3, false, Items.PITCHER_POD, "qah1115hpitcher3");
	}

	private static void pitcherMove(GameTestHelper helper, int age, boolean upperOnCrate, Item want, String name) {
		ServerLevel level = helper.getLevel();
		MoverGameTests.Setup s = MoverCrateNoRoomB1234GameTests.crateInside(helper, name);
		BlockPos at = helper.absolutePos(SPOT);
		QaMoverCrateVerify1114fGameTests.place(helper, Blocks.AIR.defaultBlockState());
		BlockPos lo = upperOnCrate ? at.below() : at;
		BlockState crop = Blocks.PITCHER_CROP.defaultBlockState().setValue(PitcherCropBlock.AGE, age);
		level.setBlock(lo, crop.setValue(DoublePlantBlock.HALF, DoubleBlockHalf.LOWER), Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE);
		level.setBlock(lo.above(), crop.setValue(DoublePlantBlock.HALF, DoubleBlockHalf.UPPER), Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE);
		String what = "an age-" + age + " pitcher crop's " + (upperOnCrate ? "upper" : "lower") + " half on his crate";
		Item other = want == Items.PITCHER_PLANT ? Items.PITCHER_POD : Items.PITCHER_PLANT;
		helper.runAfterDelay(2, () -> {
			helper.assertTrue(QaMoverCrateHolds1114cGameTests.standing(helper, Blocks.PITCHER_CROP) == 2, "setup: no two-block pitcher crop");
			QaMoverCrateQa1114c4GameTests.toTheEnd(helper, s, () -> { }, what);
			int halves = QaMoverCrateHolds1114cGameTests.standing(helper, Blocks.PITCHER_CROP);
			helper.assertTrue(halves == 0, what + ": " + halves + " half(s) left standing alone");
			MoverCrateNoRoomB1234GameTests.assertExactly(helper, want, 1, what);
			MoverCrateNoRoomB1234GameTests.assertExactly(helper, other, 0, what + ", the wrong item");
			helper.succeed();
		});
	}

	/**
	 * Two-block things no other class asks: a small dripleaf (its loot is shears-only, so by the loot it would be nothing)
	 * and every wood's door, iron and copper doors (waxed and weathered too), each half, 16 times: always exactly its one item.
	 */
	//$ gametest_batch HUGE '"qaH1115hTwoBlockThings"'
	@GameTest(template = HUGE, batch = "qaH1115hTwoBlockThings")
	public void dripleafAndEveryDoorEitherHalfIsExactlyItsOneItem(GameTestHelper helper) {
		Leftovers.clear(helper);
		ServerLevel level = helper.getLevel();
		BlockPos lower = helper.absolutePos(new BlockPos(5, 3, 5));
		List<String> bad = new ArrayList<>();
		List<BlockState[]> things = new ArrayList<>();
		BlockState leaf = Blocks.SMALL_DRIPLEAF.defaultBlockState();
		things.add(new BlockState[] {leaf.setValue(SmallDripleafBlock.HALF, DoubleBlockHalf.LOWER), leaf.setValue(SmallDripleafBlock.HALF, DoubleBlockHalf.UPPER)});
		Block[] doors = {Blocks.OAK_DOOR, Blocks.SPRUCE_DOOR, Blocks.BIRCH_DOOR, Blocks.JUNGLE_DOOR, Blocks.ACACIA_DOOR, Blocks.DARK_OAK_DOOR, Blocks.MANGROVE_DOOR,
			Blocks.CHERRY_DOOR, Blocks.BAMBOO_DOOR, Blocks.CRIMSON_DOOR, Blocks.WARPED_DOOR, Blocks.IRON_DOOR, Blocks.COPPER_DOOR, Blocks.WEATHERED_COPPER_DOOR,
			Blocks.WAXED_OXIDIZED_COPPER_DOOR};
		for (Block d : doors) {
			BlockState st = d.defaultBlockState().setValue(DoorBlock.OPEN, true);
			things.add(new BlockState[] {st.setValue(DoorBlock.HALF, DoubleBlockHalf.LOWER), st.setValue(DoorBlock.HALF, DoubleBlockHalf.UPPER)});
		}
		for (BlockState[] t : things) {
			level.setBlock(lower, t[0], Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE);
			level.setBlock(lower.above(), t[1], Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE);
			Item item = t[0].getBlock().asItem();
			for (int i = 0; i < 16; i++) {
				for (int h = 0; h < 2; h++) {
					List<ItemStack> drops = QaMoverCrateFixes1115cGameTests.itself(level, h == 0 ? lower : lower.above(), t[h]);
					if (total(drops) != 1 || count(drops, item) != 1) {
						bad.add(t[h].getBlock() + (h == 0 ? " lower: " : " upper: ") + drops);
					}
				}
			}
		}
		level.setBlock(lower.above(), Blocks.AIR.defaultBlockState(), Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE);
		level.setBlock(lower, Blocks.AIR.defaultBlockState(), Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE);
		helper.assertTrue(bad.isEmpty(), "two-block things on a crate not their one item: " + bad.stream().distinct().toList());
		helper.succeed();
	}

	/** A sculk vein on 3 faces, worked out as dropped on a crate: 3 sculk veins. */
	//$ gametest_batch HUGE '"qaH1115hSculkVein"'
	@GameTest(template = HUGE, batch = "qaH1115hSculkVein")
	public void aSculkVeinOnThreeFacesOnACrateIsThreeSculkVeins(GameTestHelper helper) {
		Leftovers.clear(helper);
		BlockState vein = Blocks.SCULK_VEIN.defaultBlockState().setValue(MultifaceBlock.getFaceProperty(Direction.DOWN), true)
			.setValue(MultifaceBlock.getFaceProperty(Direction.NORTH), true).setValue(MultifaceBlock.getFaceProperty(Direction.EAST), true);
		List<ItemStack> drops = QaMoverCrateFixes1115cGameTests.itself(helper.getLevel(), helper.absolutePos(new BlockPos(5, 3, 5)), vein);
		int n = drops.stream().filter(s -> s.is(Items.SCULK_VEIN)).mapToInt(ItemStack::getCount).sum();
		helper.assertTrue(n == 3, "a sculk vein on 3 faces on a crate dropped as " + drops + ": " + n + " sculk veins, not 3");
		helper.succeed();
	}
}
