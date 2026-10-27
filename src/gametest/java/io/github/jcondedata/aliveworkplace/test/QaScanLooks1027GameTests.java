package io.github.jcondedata.aliveworkplace.test;

import io.github.jcondedata.aliveworkplace.blueprint.BlueprintData;
import io.github.jcondedata.aliveworkplace.blueprint.BlueprintItem;
import io.github.jcondedata.aliveworkplace.build.BuildPlan;
import io.github.jcondedata.aliveworkplace.build.BuildSite;
import io.github.jcondedata.aliveworkplace.build.BuildSiteManager;
import io.github.jcondedata.aliveworkplace.build.BuilderLevels;
import io.github.jcondedata.aliveworkplace.build.Builders;
import io.github.jcondedata.aliveworkplace.registry.ModBlocks;
import io.github.jcondedata.aliveworkplace.registry.ModGameRules;
import io.github.jcondedata.aliveworkplace.registry.ModItems;
import io.github.jcondedata.aliveworkplace.work.Pace;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.animal.Bee;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.BeehiveBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CampfireBlock;
import net.minecraft.world.level.block.CocoaBlock;
import net.minecraft.world.level.block.ComposterBlock;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.entity.BeehiveBlockEntity;
import net.minecraft.world.level.block.entity.CampfireBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.level.block.state.properties.SlabType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * QA (lane-b-1027-0038), from builder.md "Builders never copy what is inside containers, nor what a block holds in its
 * look" (B429, B434, B449, B493-B496, B536) and "Speed" (B515), on blocks and levels the fixes' own tests don't build
 * through a real scan: a player builds something, scans it with real Scan Tool clicks, and a real Builder builds the
 * blueprint from a chest holding exactly the material list. What the player can take out of the new build must have
 * been paid for, and what each block shows must be what it holds. The scan-and-build helpers are the ones of
 * {@link QaScanFreeGoodsGameTests} (private there), copied.
 */
public class QaScanLooks1027GameTests implements net.fabricmc.fabric.api.gametest.v1.FabricGameTest {
	private static final String HUGE = "aliveworkplace_test:huge_area";
	private static final Logger LOGGER = LoggerFactory.getLogger("aliveworkplace-qa");
	private static final ResourceLocation TEST_HUT = ResourceLocation.fromNamespaceAndPath("aliveworkplace_test", "test_hut");
	private static final BlockPos BENCH = new BlockPos(2, 2, 2);
	private static final BlockPos CHEST = new BlockPos(2, 2, 4);
	/** The scanned row runs east from here (the floor under it, y = 1, is the scan's lower corner). */
	private static final BlockPos SRC = new BlockPos(14, 2, 24);

	private record Job(ServerLevel level, BuildSite site, Map<Item, Integer> asked, String list, BlockPos origin, Villager[] builder) {
		int asks(Item item) {
			return asked.getOrDefault(item, 0);
		}
	}

	/** Wiki: "a furnace, smoker or blast furnace is built cold". A smoker and a blast furnace scanned burning, with food and ore in them. */
	//$ gametest_ticks_batch HUGE '2400' '"qaScanLooksSmoker"'
	@GameTest(template = HUGE, timeoutTicks = 2400, batch = "qaScanLooksSmoker")
	public void aScannedBurningSmokerAndBlastFurnaceAreBuiltColdAndEmpty(GameTestHelper helper) {
		Leftovers.clear(helper);
		Job job = scanAndBuild(helper, 2, 1, Rotation.NONE, () -> {
			helper.setBlock(SRC, Blocks.SMOKER.defaultBlockState().setValue(BlockStateProperties.LIT, true));
			Container smoker = helper.getBlockEntity(SRC);
			smoker.setItem(0, new ItemStack(Items.BEEF, 8));
			smoker.setItem(1, new ItemStack(Items.COAL, 8));
			helper.setBlock(SRC.east(), Blocks.BLAST_FURNACE.defaultBlockState().setValue(BlockStateProperties.LIT, true));
			Container blast = helper.getBlockEntity(SRC.east());
			blast.setItem(0, new ItemStack(Items.RAW_IRON, 8));
			blast.setItem(2, new ItemStack(Items.IRON_INGOT, 5));
		});
		helper.succeedWhen(() -> {
			done(helper, job);
			List<String> wrong = new ArrayList<>();
			for (Block block : List.of(Blocks.SMOKER, Blocks.BLAST_FURNACE)) {
				BlockPos at = find(job, block);
				helper.assertTrue(at != null, "no " + name(block) + " built");
				if (job.level().getBlockState(at).getValue(BlockStateProperties.LIT)) {
					wrong.add("the " + name(block) + " is built burning with no fuel");
				}
				Container inside = (Container) job.level().getBlockEntity(at);
				for (int i = 0; i < inside.getContainerSize(); i++) {
					if (!inside.getItem(i).isEmpty()) {
						wrong.add("the " + name(block) + " holds " + inside.getItem(i));
					}
				}
			}
			helper.assertTrue(wrong.isEmpty(), "for the material list " + job.list() + ": " + wrong);
			LOGGER.info("qa scan looks: smoker and blast furnace built cold and empty ({})", job.list());
		});
	}

	/** Wiki: "wheat and other crops, stems, ... and cocoa go in at age 0". Ripe cocoa, beetroots, carrots, a full melon stem and a torchflower crop. */
	//$ gametest_ticks_batch HUGE '2400' '"qaScanLooksCrops"'
	@GameTest(template = HUGE, timeoutTicks = 2400, batch = "qaScanLooksCrops")
	public void aScannedRipeCocoaBeetsCarrotsStemAndTorchflowerArePlantedYoung(GameTestHelper helper) {
		Leftovers.clear(helper);
		Job job = scanAndBuild(helper, 6, 2, Rotation.NONE, () -> {
			helper.setBlock(SRC, Blocks.JUNGLE_LOG);
			helper.setBlock(SRC.east(1), Blocks.COCOA.defaultBlockState().setValue(CocoaBlock.FACING, Direction.WEST).setValue(CocoaBlock.AGE, CocoaBlock.MAX_AGE));
			for (int dx = 2; dx < 6; dx++) {
				helper.setBlock(SRC.east(dx), Blocks.FARMLAND);
			}
			helper.setBlock(SRC.east(2).above(), Blocks.BEETROOTS.defaultBlockState().setValue(BlockStateProperties.AGE_3, 3));
			helper.setBlock(SRC.east(3).above(), Blocks.CARROTS.defaultBlockState().setValue(BlockStateProperties.AGE_7, 7));
			helper.setBlock(SRC.east(4).above(), Blocks.MELON_STEM.defaultBlockState().setValue(BlockStateProperties.AGE_7, 7));
			helper.setBlock(SRC.east(5).above(), Blocks.TORCHFLOWER_CROP.defaultBlockState().setValue(BlockStateProperties.AGE_1, 1));
		});
		helper.succeedWhen(() -> {
			done(helper, job);
			ServerLevel level = job.level();
			List<String> ripe = new ArrayList<>();
			for (Block block : List.of(Blocks.COCOA, Blocks.BEETROOTS, Blocks.CARROTS, Blocks.MELON_STEM, Blocks.TORCHFLOWER_CROP)) {
				BlockPos at = find(job, block);
				helper.assertTrue(at != null, "no " + name(block) + " planted");
				BlockState state = level.getBlockState(at);
				for (var property : state.getProperties()) {
					if (property.getName().equals("age") && !state.getValue(property).equals(0)) {
						ripe.add(name(block) + " at age " + state.getValue(property));
					}
				}
			}
			helper.assertTrue(ripe.isEmpty(), "planted grown, a free harvest for the material list " + job.list() + ": " + ripe);
			LOGGER.info("qa scan looks: cocoa, beetroots, carrots, melon stem and torchflower planted at age 0 ({})", job.list());
		});
	}

	/**
	 * "Crops are planted, never built ripe" for the two-tall pitcher crop: a ripe one (age 4, which breaks into a pitcher
	 * plant). Run by {@link QaScanLooks1027BugTests} until the mod plants it young.
	 */
	static void pitcher(GameTestHelper helper) {
		Leftovers.clear(helper);
		Job job = scanAndBuild(helper, 1, 3, Rotation.NONE, () -> {
			helper.setBlock(SRC, Blocks.FARMLAND);
			helper.setBlock(SRC.above(), Blocks.PITCHER_CROP.defaultBlockState().setValue(BlockStateProperties.AGE_4, 4)
				.setValue(BlockStateProperties.DOUBLE_BLOCK_HALF, DoubleBlockHalf.LOWER));
			helper.setBlock(SRC.above(2), Blocks.PITCHER_CROP.defaultBlockState().setValue(BlockStateProperties.AGE_4, 4)
				.setValue(BlockStateProperties.DOUBLE_BLOCK_HALF, DoubleBlockHalf.UPPER));
			helper.assertTrue(helper.getBlockState(SRC.above(2)).is(Blocks.PITCHER_CROP), "setup: the ripe pitcher crop didn't stand");
		});
		// the verdict is taken once, the first tick the build is done: breaking the crop to see its drops changes the build
		String[] verdict = {null};
		helper.succeedWhen(() -> {
			if (verdict[0] != null) {
				helper.assertTrue(verdict[0].isEmpty(), verdict[0]);
			}
			done(helper, job);
			List<String> wrong = new ArrayList<>();
			for (BlockPos p : BlockPos.betweenClosed(job.origin().offset(-10, 0, -10), job.origin().offset(10, 4, 10))) {
				BlockState state = job.level().getBlockState(p);
				if (state.is(Blocks.PITCHER_CROP) && state.getValue(BlockStateProperties.AGE_4) == 4) {
					wrong.add("a ripe pitcher crop (" + state.getValue(BlockStateProperties.DOUBLE_BLOCK_HALF) + ")");
				}
				if (state.is(Blocks.PITCHER_PLANT)) {
					wrong.add("a pitcher plant");
				}
			}
			if (!wrong.isEmpty()) {
				// what a player gets for breaking it (by hand, as anyone would)
				BlockPos lower = null;
				for (BlockPos p : BlockPos.betweenClosed(job.origin().offset(-10, 0, -10), job.origin().offset(10, 4, 10))) {
					BlockState state = job.level().getBlockState(p);
					if (state.is(Blocks.PITCHER_CROP) && state.getValue(BlockStateProperties.DOUBLE_BLOCK_HALF) == DoubleBlockHalf.LOWER) {
						lower = p.immutable();
					}
				}
				if (lower != null) {
					job.level().destroyBlock(lower, true);
					List<String> drops = new ArrayList<>();
					for (var item : job.level().getEntitiesOfClass(net.minecraft.world.entity.item.ItemEntity.class, new net.minecraft.world.phys.AABB(lower).inflate(3))) {
						drops.add(item.getItem().toString());
					}
					wrong.add("broken, it drops " + drops);
				}
			}
			verdict[0] = wrong.isEmpty() ? "" : "built from " + job.asks(Items.PITCHER_POD) + " pitcher pod (list " + job.list() + "): " + wrong;
			helper.assertTrue(verdict[0].isEmpty(), verdict[0]);
			LOGGER.info("qa scan looks: the pitcher crop is planted, not ripe ({}; built {}; states {})", job.list(), column(job), pitcherStates(job));
		});
	}

	/** Wiki: "a full beehive ... with no honey" and "a bee nest or hive holds no bees": a plain Beehive (not a nest) with honey 5 and two bees. */
	//$ gametest_ticks_batch HUGE '2400' '"qaScanLooksHive"'
	@GameTest(template = HUGE, timeoutTicks = 2400, batch = "qaScanLooksHive")
	public void aScannedFullBeehiveIsBuiltWithNoHoneyAndNoBees(GameTestHelper helper) {
		Leftovers.clear(helper);
		Job job = scanAndBuild(helper, 1, 1, Rotation.NONE, () -> {
			helper.setBlock(SRC, Blocks.BEEHIVE.defaultBlockState().setValue(BeehiveBlock.FACING, Direction.SOUTH)
				.setValue(BeehiveBlock.HONEY_LEVEL, BeehiveBlock.MAX_HONEY_LEVELS));
			BeehiveBlockEntity hive = helper.getBlockEntity(SRC);
			for (int i = 0; i < 2; i++) {
				hive.addOccupant(helper.spawn(EntityType.BEE, SRC.south()));
			}
			helper.assertTrue(hive.getOccupantCount() == 2, "setup: the hive holds " + hive.getOccupantCount() + " bees");
		});
		helper.succeedWhen(() -> {
			done(helper, job);
			ServerLevel level = job.level();
			BlockPos at = find(job, Blocks.BEEHIVE);
			helper.assertTrue(at != null, "no beehive built");
			List<String> free = new ArrayList<>();
			int honey = level.getBlockState(at).getValue(BeehiveBlock.HONEY_LEVEL);
			if (honey > 0) {
				free.add("honey level " + honey + (honey == BeehiveBlock.MAX_HONEY_LEVELS ? " (a free bottle of honey or 3 honeycombs)" : ""));
			}
			int inside = ((BeehiveBlockEntity) level.getBlockEntity(at)).getOccupantCount();
			if (inside > 0) {
				free.add(inside + " bees inside");
			}
			int bees = level.getEntitiesOfClass(Bee.class, new net.minecraft.world.phys.AABB(job.origin()).inflate(12)).size();
			if (bees > 0) {
				free.add(bees + " bees came with the build");
			}
			helper.assertTrue(free.isEmpty(), "the built beehive has, for the material list " + job.list() + ": " + free);
			LOGGER.info("qa scan looks: the beehive is built with no honey and no bees");
		});
	}

	/** Wiki: "a chiseled bookshelf or brewing stand showing no books or bottles": three potions, a blaze powder and a nether wart in the stand. */
	//$ gametest_ticks_batch HUGE '2400' '"qaScanLooksBrew"'
	@GameTest(template = HUGE, timeoutTicks = 2400, batch = "qaScanLooksBrew")
	public void aScannedBrewingStandWithPotionsHoldsAndShowsNone(GameTestHelper helper) {
		Leftovers.clear(helper);
		Job job = scanAndBuild(helper, 1, 1, Rotation.NONE, () -> {
			helper.setBlock(SRC, Blocks.BREWING_STAND.defaultBlockState().setValue(BlockStateProperties.HAS_BOTTLE_0, true)
				.setValue(BlockStateProperties.HAS_BOTTLE_1, true).setValue(BlockStateProperties.HAS_BOTTLE_2, true));
			Container stand = helper.getBlockEntity(SRC);
			for (int i = 0; i < 3; i++) {
				stand.setItem(i, new ItemStack(Items.POTION));
			}
			stand.setItem(3, new ItemStack(Items.NETHER_WART, 4));
			stand.setItem(4, new ItemStack(Items.BLAZE_POWDER, 6));
		});
		helper.succeedWhen(() -> {
			done(helper, job);
			BlockPos at = find(job, Blocks.BREWING_STAND);
			helper.assertTrue(at != null, "no brewing stand built");
			List<String> wrong = new ArrayList<>();
			BlockState state = job.level().getBlockState(at);
			for (var bottle : List.of(BlockStateProperties.HAS_BOTTLE_0, BlockStateProperties.HAS_BOTTLE_1, BlockStateProperties.HAS_BOTTLE_2)) {
				if (state.getValue(bottle)) {
					wrong.add("shows " + bottle.getName());
				}
			}
			Container stand = (Container) job.level().getBlockEntity(at);
			for (int i = 0; i < stand.getContainerSize(); i++) {
				if (!stand.getItem(i).isEmpty()) {
					wrong.add("holds " + stand.getItem(i));
				}
			}
			helper.assertTrue(wrong.isEmpty(), "the built brewing stand, for the material list " + job.list() + ": " + wrong);
			LOGGER.info("qa scan looks: the brewing stand is built empty and shows no bottle");
		});
	}

	/**
	 * Wiki: "a ready composter at level 0" and "a full beehive ... with no honey" — and the same one step short of full: a
	 * composter at level 7 (one more seed gives bone meal) and a bee nest at honey level 4.
	 */
	//$ gametest_ticks_batch HUGE '2400' '"qaScanLooksAlmost"'
	@GameTest(template = HUGE, timeoutTicks = 2400, batch = "qaScanLooksAlmost")
	public void aNearlyReadyComposterAndNearlyFullNestAreBuiltEmpty(GameTestHelper helper) {
		Leftovers.clear(helper);
		Job job = scanAndBuild(helper, 2, 1, Rotation.NONE, () -> {
			helper.setBlock(SRC, Blocks.COMPOSTER.defaultBlockState().setValue(ComposterBlock.LEVEL, 7));
			helper.setBlock(SRC.east(), Blocks.BEE_NEST.defaultBlockState().setValue(BeehiveBlock.HONEY_LEVEL, 4));
		});
		helper.succeedWhen(() -> {
			done(helper, job);
			ServerLevel level = job.level();
			List<String> free = new ArrayList<>();
			BlockPos composter = find(job, Blocks.COMPOSTER);
			BlockPos nest = find(job, Blocks.BEE_NEST);
			helper.assertTrue(composter != null && nest != null, "not built: " + column(job));
			if (level.getBlockState(composter).getValue(ComposterBlock.LEVEL) > 0) {
				free.add("a composter at level " + level.getBlockState(composter).getValue(ComposterBlock.LEVEL));
			}
			if (level.getBlockState(nest).getValue(BeehiveBlock.HONEY_LEVEL) > 0) {
				free.add("a nest at honey level " + level.getBlockState(nest).getValue(BeehiveBlock.HONEY_LEVEL));
			}
			helper.assertTrue(free.isEmpty(), "the builder gave away, for the material list " + job.list() + ": " + free);
			LOGGER.info("qa scan looks: composter and nest one step short of full built empty");
		});
	}

	/** Wiki: "a campfire stays lit as drawn" — and holds no food (nothing inside is copied): a lit soul campfire cooking two beef. */
	//$ gametest_ticks_batch HUGE '2400' '"qaScanLooksCampfire"'
	@GameTest(template = HUGE, timeoutTicks = 2400, batch = "qaScanLooksCampfire")
	public void aScannedLitSoulCampfireStaysLitAndHoldsNoFood(GameTestHelper helper) {
		Leftovers.clear(helper);
		Job job = scanAndBuild(helper, 1, 1, Rotation.NONE, () -> {
			helper.setBlock(SRC, Blocks.SOUL_CAMPFIRE.defaultBlockState().setValue(CampfireBlock.LIT, true));
			CampfireBlockEntity fire = helper.getBlockEntity(SRC);
			fire.placeFood(null, new ItemStack(Items.BEEF), 600);
			fire.placeFood(null, new ItemStack(Items.BEEF), 600);
			helper.assertTrue(fire.getItems().stream().filter(s -> !s.isEmpty()).count() == 2, "setup: the campfire holds " + fire.getItems());
		});
		helper.succeedWhen(() -> {
			done(helper, job);
			BlockPos at = find(job, Blocks.SOUL_CAMPFIRE);
			helper.assertTrue(at != null, "no soul campfire built");
			helper.assertTrue(job.level().getBlockState(at).getValue(CampfireBlock.LIT), "the scanned lit soul campfire is built out (wiki: a campfire stays lit as drawn)");
			CampfireBlockEntity fire = (CampfireBlockEntity) job.level().getBlockEntity(at);
			helper.assertTrue(fire.getItems().stream().allMatch(ItemStack::isEmpty), "the built campfire cooks free food: " + fire.getItems());
			LOGGER.info("qa scan looks: the soul campfire is lit and empty");
		});
	}

	/**
	 * Blocks that hold several of one item in one block (four candles, four sea pickles, four pink petals, three turtle eggs,
	 * a double slab): the material list asks for each piece, so breaking the new build gives back no more than was paid.
	 */
	//$ gametest_ticks_batch HUGE '2400' '"qaScanLooksBunches"'
	@GameTest(template = HUGE, timeoutTicks = 2400, batch = "qaScanLooksBunches")
	public void scannedBunchesAskForEveryPiece(GameTestHelper helper) {
		Leftovers.clear(helper);
		Job job = scanAndBuild(helper, 5, 2, Rotation.NONE, () -> {
			helper.setBlock(SRC, Blocks.GRASS_BLOCK);
			helper.setBlock(SRC.above(), Blocks.PINK_PETALS.defaultBlockState().setValue(BlockStateProperties.FLOWER_AMOUNT, 4));
			helper.setBlock(SRC.east(1), Blocks.SAND);
			helper.setBlock(SRC.east(1).above(), Blocks.TURTLE_EGG.defaultBlockState().setValue(BlockStateProperties.EGGS, 3));
			helper.setBlock(SRC.east(2), Blocks.STONE);
			helper.setBlock(SRC.east(2).above(), Blocks.SEA_PICKLE.defaultBlockState().setValue(BlockStateProperties.PICKLES, 4)
				.setValue(BlockStateProperties.WATERLOGGED, false));
			helper.setBlock(SRC.east(3), Blocks.STONE);
			helper.setBlock(SRC.east(3).above(), Blocks.CANDLE.defaultBlockState().setValue(BlockStateProperties.CANDLES, 4));
			helper.setBlock(SRC.east(4), Blocks.STONE_SLAB.defaultBlockState().setValue(BlockStateProperties.SLAB_TYPE, SlabType.DOUBLE));
		});
		List<String> short_ = new ArrayList<>();
		if (job.asks(Items.PINK_PETALS) < 4) {
			short_.add(job.asks(Items.PINK_PETALS) + " pink petals for 4");
		}
		if (job.asks(Items.TURTLE_EGG) < 3) {
			short_.add(job.asks(Items.TURTLE_EGG) + " turtle eggs for 3");
		}
		if (job.asks(Items.SEA_PICKLE) < 4) {
			short_.add(job.asks(Items.SEA_PICKLE) + " sea pickles for 4");
		}
		if (job.asks(Items.CANDLE) < 4) {
			short_.add(job.asks(Items.CANDLE) + " candles for 4");
		}
		if (job.asks(Items.STONE_SLAB) < 2) {
			short_.add(job.asks(Items.STONE_SLAB) + " stone slabs for a double slab");
		}
		helper.assertTrue(short_.isEmpty(), "the material list " + job.list() + " asks only " + short_);
		helper.succeedWhen(() -> {
			done(helper, job);
			BlockState candles = job.level().getBlockState(find(job, Blocks.CANDLE));
			BlockState pickles = job.level().getBlockState(find(job, Blocks.SEA_PICKLE));
			helper.assertTrue(candles.getValue(BlockStateProperties.CANDLES) <= job.asks(Items.CANDLE)
				&& pickles.getValue(BlockStateProperties.PICKLES) <= job.asks(Items.SEA_PICKLE), "built more than paid: " + candles + ", " + pickles);
			LOGGER.info("qa scan looks: bunches asked piece by piece ({})", job.list());
		});
	}

	/** A save and load of the world mid-build, with a burning smoker, ripe cocoa, a full beehive and a stand of potions still to come. */
	//$ gametest_ticks_batch HUGE '3600' '"qaScanLooksReload"'
	@GameTest(template = HUGE, timeoutTicks = 3600, batch = "qaScanLooksReload")
	public void aScanBuiltAcrossASaveAndLoadStillGivesNothingAway(GameTestHelper helper) {
		Leftovers.clear(helper);
		Job job = scanAndBuild(helper, 8, 1, Rotation.NONE, () -> {
			for (int dx = 0; dx < 3; dx++) {
				helper.setBlock(SRC.east(dx), Blocks.COBBLESTONE);
			}
			helper.setBlock(SRC.east(3), Blocks.SMOKER.defaultBlockState().setValue(BlockStateProperties.LIT, true));
			((Container) helper.getBlockEntity(SRC.east(3))).setItem(1, new ItemStack(Items.COAL, 4));
			helper.setBlock(SRC.east(4), Blocks.JUNGLE_LOG);
			helper.setBlock(SRC.east(5), Blocks.COCOA.defaultBlockState().setValue(CocoaBlock.FACING, Direction.WEST).setValue(CocoaBlock.AGE, CocoaBlock.MAX_AGE));
			helper.setBlock(SRC.east(6), Blocks.BEEHIVE.defaultBlockState().setValue(BeehiveBlock.HONEY_LEVEL, BeehiveBlock.MAX_HONEY_LEVELS));
			helper.setBlock(SRC.east(7), Blocks.BREWING_STAND.defaultBlockState().setValue(BlockStateProperties.HAS_BOTTLE_0, true));
			((Container) helper.getBlockEntity(SRC.east(7))).setItem(0, new ItemStack(Items.POTION));
		});
		UUID id = job.site().id();
		BlockPos firstStone = job.origin().offset(0, 1, 0);
		boolean[] reloaded = {false};
		helper.onEachTick(() -> {
			BuildSite now = BuildSiteManager.get(job.level()).get(id);
			if (reloaded[0] || now == null || !job.level().getBlockState(firstStone).is(Blocks.COBBLESTONE)) {
				return;
			}
			reloaded[0] = true;
			helper.assertTrue(find(job, Blocks.BREWING_STAND) == null, "the whole row went up before the reload");
			job.builder()[0] = reload(helper, job.level(), job.builder()[0]);
			LOGGER.info("qa scan looks: saved and loaded mid-build at {}", now.stage());
		});
		helper.succeedWhen(() -> {
			helper.assertTrue(reloaded[0], "never reloaded mid-build");
			done(helper, job);
			ServerLevel level = job.level();
			List<String> free = new ArrayList<>();
			BlockPos smoker = find(job, Blocks.SMOKER);
			BlockPos cocoa = find(job, Blocks.COCOA);
			BlockPos hive = find(job, Blocks.BEEHIVE);
			BlockPos stand = find(job, Blocks.BREWING_STAND);
			helper.assertTrue(smoker != null && cocoa != null && hive != null && stand != null, "not all built: " + smoker + cocoa + hive + stand);
			if (level.getBlockState(smoker).getValue(BlockStateProperties.LIT) || !((Container) level.getBlockEntity(smoker)).getItem(1).isEmpty()) {
				free.add("the smoker is lit or fueled: " + level.getBlockState(smoker) + " " + ((Container) level.getBlockEntity(smoker)).getItem(1));
			}
			if (level.getBlockState(cocoa).getValue(CocoaBlock.AGE) > 0) {
				free.add("cocoa at age " + level.getBlockState(cocoa).getValue(CocoaBlock.AGE));
			}
			if (level.getBlockState(hive).getValue(BeehiveBlock.HONEY_LEVEL) > 0) {
				free.add("the hive at honey level " + level.getBlockState(hive).getValue(BeehiveBlock.HONEY_LEVEL));
			}
			if (level.getBlockState(stand).getValue(BlockStateProperties.HAS_BOTTLE_0) || !((Container) level.getBlockEntity(stand)).getItem(0).isEmpty()) {
				free.add("the brewing stand shows or holds a potion");
			}
			helper.assertTrue(free.isEmpty(), "after a save and load mid-build, for the material list " + job.list() + ": " + free);
			LOGGER.info("qa scan looks: reloaded build gave nothing away");
		});
	}



	// ---- B515, the default delay's other levels and a non-default delay ----

	/** Wiki "Speed": at the default delay 8 (no village, no traits, pace 1) a Novice takes 8 ticks a block; at 5 a Master (40%) takes exactly 2. */
	//$ gametest_ticks_batch HUGE '3000' '"qaScanLooksPaceFive"'
	@GameTest(template = HUGE, timeoutTicks = 3000, batch = "qaScanLooksPaceFive")
	public void atADelayOfFiveANoviceTakesFiveTicksAndAMasterTwo(GameTestHelper helper) {
		pace(helper, 5, 1, 5, 5, 2, "qa-scan-looks-pace-five");
	}

	/** Wiki "Speed": at a delay of 10 a Journeyman (70%) takes exactly 7 ticks a block and a Master (40%) 4. */
	//$ gametest_ticks_batch HUGE '3000' '"qaScanLooksPaceTen"'
	@GameTest(template = HUGE, timeoutTicks = 3000, batch = "qaScanLooksPaceTen")
	public void atADelayOfTenAJourneymanTakesSevenTicksAndAMasterFour(GameTestHelper helper) {
		pace(helper, 10, 3, 5, 7, 4, "qa-scan-looks-pace-ten");
	}

	private static void pace(GameTestHelper helper, int delay, int levelA, int levelB, int wantA, int wantB, String tag) {
		ServerLevel level = QaBenchMovePathsPaceGameTests.prepare(helper, delay);
		QaBenchMovePathsPaceGameTests.traitsOff(helper);
		Villager a = QaBenchMovePathsPaceGameTests.bench(helper, new BlockPos(2, 2, 2), QaBenchMovePathsPaceGameTests.hutMaterials());
		Villager b = QaBenchMovePathsPaceGameTests.bench(helper, new BlockPos(26, 2, 2), QaBenchMovePathsPaceGameTests.hutMaterials());
		a.setVillagerData(a.getVillagerData().setLevel(levelA));
		b.setVillagerData(b.getVillagerData().setLevel(levelB));
		helper.assertTrue(Pace.factor(a) == 1f && Pace.factor(b) == 1f, "setup: pace " + Pace.of(a) + " / " + Pace.of(b));
		BuildSite siteA = Builders.start(level, a, null, TEST_HUT, QaBenchMovePathsPaceGameTests.placement(helper, new BlockPos(4, 2, 12)));
		BuildSite siteB = Builders.start(level, b, null, TEST_HUT, QaBenchMovePathsPaceGameTests.placement(helper, new BlockPos(20, 2, 12)));
		BuildPlan planA = siteA.plan(level);
		BuildPlan planB = siteB.plan(level);
		if (planA == null || planB == null) {
			throw new GameTestAssertException("no test hut blueprint");
		}
		List<Long> timesA = new ArrayList<>();
		List<Long> timesB = new ArrayList<>();
		int[] left = {planA.unfinished(level).size(), planB.unfinished(level).size()};
		helper.onEachTick(() -> {
			long now = level.getGameTime();
			int n = planA.unfinished(level).size();
			int m = planB.unfinished(level).size();
			for (int i = n; i < left[0]; i++) {
				timesA.add(now);
			}
			for (int i = m; i < left[1]; i++) {
				timesB.add(now);
			}
			left[0] = n;
			left[1] = m;
		});
		helper.succeedWhen(() -> {
			helper.assertTrue(timesA.size() >= 25 && timesB.size() >= 25, "placed " + timesA.size() + " / " + timesB.size());
			int gapA = QaBenchMovePathsPaceGameTests.median(timesA);
			int gapB = QaBenchMovePathsPaceGameTests.median(timesB);
			LOGGER.info("[{}] delay {}: level {} waits {} ticks a block (wanted {}), level {} waits {} (wanted {}); BuilderLevels says {} / {}", tag, delay,
				levelA, gapA, wantA, levelB, gapB, wantB, BuilderLevels.delay(level, a), BuilderLevels.delay(level, b));
			helper.assertTrue(gapA == wantA, "at a delay of " + delay + " a level " + levelA + " builder waits " + gapA + " ticks between blocks, not " + wantA);
			helper.assertTrue(gapB == wantB, "at a delay of " + delay + " a level " + levelB + " builder waits " + gapB + " ticks between blocks, not " + wantB);
		});
	}

	// ---- helpers (as QaScanFreeGoodsGameTests') ----

	private static Job scanAndBuild(GameTestHelper helper, int width, int height, Rotation rotation, Runnable setup) {
		ServerLevel level = helper.getLevel();
		setup.run();
		List<Component> seen = new ArrayList<>();
		ServerPlayer player = QaStageThreeGameTests.listener(helper, seen);
		player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(ModItems.SCAN_TOOL));
		player.getInventory().add(new ItemStack(ModItems.BLANK_BLUEPRINT, 1));
		QaStageThreeGameTests.click(helper, player, SRC.below());
		QaStageThreeGameTests.click(helper, player, SRC.east(width).above(height - 1));
		player.setShiftKeyDown(true);
		QaStageThreeGameTests.clickAir(helper, player);
		player.setShiftKeyDown(false);
		helper.assertTrue(QaStageThreeGameTests.last(seen).equals("message.aliveworkplace.scan.saved"), "the scan said " + QaStageThreeGameTests.last(seen));
		ItemStack blueprint = ItemStack.EMPTY;
		for (ItemStack s : player.getInventory().items) {
			if (s.is(ModItems.BLUEPRINT)) {
				blueprint = s;
			}
		}
		helper.assertTrue(!blueprint.isEmpty(), "the scan gave no blueprint");
		ResourceLocation id = BlueprintItem.data(blueprint).orElseThrow().structure();
		for (int dy = height - 1; dy >= 0; dy--) {
			for (int dx = 0; dx < width; dx++) {
				helper.setBlock(SRC.east(dx).above(dy), Blocks.AIR);
			}
		}
		for (Entity e : level.getEntitiesOfClass(Entity.class, new net.minecraft.world.phys.AABB(helper.absolutePos(SRC)).inflate(10),
			e -> e instanceof Bee || e instanceof net.minecraft.world.entity.item.ItemEntity)) {
			e.discard();
		}

		boolean free = level.getGameRules().getBoolean(ModGameRules.FREE_MATERIALS);
		int delay = level.getGameRules().getInt(ModGameRules.BUILD_DELAY);
		boolean help = level.getGameRules().getBoolean(ModGameRules.BUILDERS_HELP);
		Leftovers.after(helper, () -> {
			level.getGameRules().getRule(ModGameRules.FREE_MATERIALS).set(free, level.getServer());
			level.getGameRules().getRule(ModGameRules.BUILD_DELAY).set(delay, level.getServer());
			level.getGameRules().getRule(ModGameRules.BUILDERS_HELP).set(help, level.getServer());
		});
		level.getGameRules().getRule(ModGameRules.FREE_MATERIALS).set(false, level.getServer());
		level.getGameRules().getRule(ModGameRules.BUILD_DELAY).set(2, level.getServer());
		level.getGameRules().getRule(ModGameRules.BUILDERS_HELP).set(false, level.getServer());
		helper.setDayTime(2000);
		helper.setBlock(BENCH, ModBlocks.BUILDERS_BENCH);
		Villager builder = helper.spawn(EntityType.VILLAGER, BENCH.east());
		Builders.employ(level, builder, helper.absolutePos(BENCH));
		BlockPos origin = helper.absolutePos(new BlockPos(10, 1, 10));
		BlueprintData.Placement placement = new BlueprintData.Placement(level.dimension().location(), origin, rotation, Mirror.NONE);
		BuildSite site = Builders.start(level, builder, null, id, placement);
		if (site == null) {
			throw new GameTestAssertException("the builder didn't take the scanned blueprint " + id);
		}
		Map<Item, Integer> asked = site.plan(level).materials();
		helper.setBlock(CHEST, Blocks.CHEST);
		Container chest = helper.getBlockEntity(CHEST);
		int slot = 0;
		for (Map.Entry<Item, Integer> e : asked.entrySet()) {
			int left = e.getValue();
			while (left > 0) {
				int n = Math.min(left, e.getKey().getDefaultMaxStackSize());
				chest.setItem(slot++, new ItemStack(e.getKey(), n));
				left -= n;
			}
		}
		String list = asked.entrySet().stream().map(e -> e.getValue() + " " + BuiltInRegistries.ITEM.getKey(e.getKey()).getPath()).toList().toString();
		return new Job(level, site, asked, list, origin, new Villager[] {builder});
	}

	/** What stands in the build's box at each block of the scanned row, for messages. */
	private static String column(Job job) {
		List<String> out = new ArrayList<>();
		for (BlockPos p : BlockPos.betweenClosed(job.origin().offset(-10, 0, -10), job.origin().offset(10, 4, 10))) {
			BlockState state = job.level().getBlockState(p);
			if (!state.isAir() && p.getY() > job.origin().getY()) {
				out.add(p.subtract(job.origin()).toShortString() + " " + name(state.getBlock()));
			}
		}
		return out.toString();
	}

	private static String pitcherStates(Job job) {
		List<String> out = new ArrayList<>();
		for (BlockPos p : BlockPos.betweenClosed(job.origin().offset(-10, 0, -10), job.origin().offset(10, 4, 10))) {
			if (job.level().getBlockState(p).is(Blocks.PITCHER_CROP)) {
				out.add(job.level().getBlockState(p).toString());
			}
		}
		return out.toString();
	}

	private static String name(Block block) {
		return BuiltInRegistries.BLOCK.getKey(block).getPath();
	}

	private static void done(GameTestHelper helper, Job job) {
		BuildSite now = BuildSiteManager.get(job.level()).get(job.site().id());
		helper.assertTrue(now == null, "still building: " + (now == null ? "" : now.stage() + " " + now.status() + " missing " + now.missing()));
	}

	private static BlockPos find(Job job, Block block) {
		for (BlockPos p : BlockPos.betweenClosed(job.origin().offset(-10, 0, -10), job.origin().offset(10, 4, 10))) {
			if (job.level().getBlockState(p).is(block)) {
				return p.immutable();
			}
		}
		return null;
	}

	private static Villager reload(GameTestHelper helper, ServerLevel level, Villager old) {
		BuildSiteManager loaded = BuildSiteManager.load(BuildSiteManager.get(level).save(new CompoundTag(), level.registryAccess()), level.registryAccess());
		level.getDataStorage().set(BuildSiteManager.NAME, loaded);
		CompoundTag tag = new CompoundTag();
		helper.assertTrue(old.save(tag), "the builder wasn't saved");
		old.discard();
		Entity back = EntityType.loadEntityRecursive(tag, level, e -> e);
		helper.assertTrue(back instanceof Villager, "the builder didn't load as a villager: " + back);
		level.addFreshEntity(back);
		return (Villager) back;
	}
}
