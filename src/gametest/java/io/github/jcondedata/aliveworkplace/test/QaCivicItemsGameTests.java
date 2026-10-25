package io.github.jcondedata.aliveworkplace.test;

import io.github.jcondedata.aliveworkplace.WorkplaceConfig;
import io.github.jcondedata.aliveworkplace.hall.Chronicle;
import io.github.jcondedata.aliveworkplace.hall.CivicEffects;
import io.github.jcondedata.aliveworkplace.hall.CradleSeat;
import io.github.jcondedata.aliveworkplace.hall.Cradles;
import io.github.jcondedata.aliveworkplace.hall.HarvestIdols;
import io.github.jcondedata.aliveworkplace.hall.Seasons;
import io.github.jcondedata.aliveworkplace.hall.VillageBannerItem;
import io.github.jcondedata.aliveworkplace.hall.VillageBanners;
import io.github.jcondedata.aliveworkplace.hall.VillageHallBlockEntity;
import io.github.jcondedata.aliveworkplace.hall.VillageHallScreen;
import io.github.jcondedata.aliveworkplace.hall.VillageHalls;
import io.github.jcondedata.aliveworkplace.hall.VillageNeeds;
import io.github.jcondedata.aliveworkplace.hall.VillageRanks;
import io.github.jcondedata.aliveworkplace.people.Moods;
import io.github.jcondedata.aliveworkplace.registry.ModBlocks;
import io.github.jcondedata.aliveworkplace.registry.ModItems;
import java.util.List;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.ItemLore;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.CraftingRecipe;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.FarmBlock;
import net.minecraft.world.level.block.entity.BannerBlockEntity;
import net.minecraft.world.level.block.entity.BannerPatternLayers;
import net.minecraft.world.level.block.entity.BannerPatterns;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BedPart;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.level.levelgen.XoroshiroRandomSource;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

/**
 * QA for the civic items (ROADMAP 30.12 the Cradle, 30.13 the Village Banner, 30.14 Seasons and the Harvest Idol), on
 * top of {@link CradleGameTests}, {@link VillageBannerGameTests} and {@link HarvestIdolGameTests}: written from the
 * spec's words. A player places and breaks cradles and idols with the real item use and block breaking; the reach
 * boundaries (4 blocks from a bed, the village's radius) are tried from both sides; the harvest season is checked on the
 * world clock's tick before and after each edge; idols are kept per dimension; crops other than wheat grow faster too;
 * the Village Banner's tooltip, real crafting grid and wall placement. Each test has its own batch and puts back the
 * clock, the switches and every block it set outside the area.
 */
public class QaCivicItemsGameTests implements FabricGameTest {
	private static final String AREA = "aliveworkplace_test:big_area";
	private static final BlockPos HALL = new BlockPos(11, 2, 11);
	/** The bed's head (its home point) is at (5, 2, 16), its foot at (5, 2, 15). */
	private static final BlockPos BED_HEAD = new BlockPos(5, 2, 16);
	private static final BlockPos NEAR_CRADLE = new BlockPos(7, 2, 16);
	private static final BlockPos FOUR_OFF = new BlockPos(9, 2, 16);
	private static final BlockPos FIVE_OFF = new BlockPos(10, 2, 16);
	private static final BlockPos CROP = new BlockPos(4, 3, 4);
	private static final BlockPos IDOL = new BlockPos(8, 2, 4);
	private static final int TICKS = 2000;
	private static final long SEED = 3014L;
	/** Fixed seeds in a row for the other crops, which grow fewer stages than wheat in 2000 ticks. */
	private static final int SEEDS = 10;
	private static final org.slf4j.Logger LOG = org.slf4j.LoggerFactory.getLogger("aliveworkplace_test");

	// --- 30.12 The Cradle --------------------------------------------------------------------------------------------

	/** "Within 4 blocks of a bed": a cradle 4 blocks from the bed's head counts, one 5 blocks off doesn't. */
	//$ gametest_ticks_batch AREA '100' '"qaCradleReach"'
	@GameTest(template = AREA, timeoutTicks = 100, batch = "qaCradleReach")
	public void qaACradleCountsAtFourBlocksFromABedNotFive(GameTestHelper helper) {
		village(helper);
		helper.setBlock(FIVE_OFF, ModBlocks.CRADLE);
		helper.runAfterDelay(5, () -> {
			ServerLevel level = helper.getLevel();
			BlockPos hall = helper.absolutePos(HALL);
			ready(helper);
			helper.assertTrue(helper.absolutePos(FIVE_OFF).distSqr(helper.absolutePos(BED_HEAD)) == 25, "the far cradle isn't 5 blocks off");
			helper.assertFalse(Cradles.nursery(level, hall), "a cradle 5 blocks from the bed counted");
			helper.setBlock(FIVE_OFF, Blocks.AIR);
			helper.setBlock(FOUR_OFF, ModBlocks.CRADLE);
			helper.assertTrue(Cradles.nursery(level, hall), "a cradle 4 blocks from the bed doesn't count");
			helper.assertTrue(Cradles.extraBirths(level, hall) == 1, "extra births: " + Cradles.extraBirths(level, hall));
			// The bed broken: the cradle no longer counts.
			helper.setBlock(BED_HEAD, Blocks.AIR);
			helper.setBlock(BED_HEAD.north(), Blocks.AIR);
			helper.assertFalse(Cradles.nursery(level, hall), "a cradle counted after its bed was broken");
			helper.succeed();
		});
	}

	/** "In a village with a hall": a cradle by a bed just outside the hall's village doesn't count, just inside it does. */
	//$ gametest_ticks_batch AREA '100' '"qaCradleVillage"'
	@GameTest(template = AREA, timeoutTicks = 100, batch = "qaCradleVillage")
	public void qaACradleOutsideTheVillageDoesntCount(GameTestHelper helper) {
		village(helper);
		helper.setBlock(NEAR_CRADLE, ModBlocks.CRADLE);
		helper.runAfterDelay(5, () -> {
			ServerLevel level = helper.getLevel();
			BlockPos hall = helper.absolutePos(HALL);
			ready(helper);
			double d2 = helper.absolutePos(NEAR_CRADLE).distSqr(hall); // 41: between 6 and 7 blocks
			helper.assertTrue(d2 == 41, "the cradle is " + d2 + " from the hall squared");
			VillageHalls.RADIUS = 6;
			helper.assertFalse(Cradles.nursery(level, hall), "a cradle outside a village of radius 6 counted");
			VillageHalls.RADIUS = 7;
			helper.assertTrue(Cradles.nursery(level, hall), "a cradle inside a village of radius 7 doesn't count");
			VillageHalls.RADIUS = 16;
			helper.succeed();
		});
	}

	/**
	 * A survival player places a Cradle beside a bed with the item and the hall's beds icon says so; the player breaks it
	 * and the icon says there's none, the village no longer a nursery. Default config: cradles on.
	 */
	//$ gametest_ticks_batch AREA '100' '"qaCradlePlayer"'
	@GameTest(template = AREA, timeoutTicks = 100, batch = "qaCradlePlayer")
	public void qaAPlayerPlacesAndBreaksACradleAndTheHallSaysSo(GameTestHelper helper) {
		Leftovers.halls(helper);
		village(helper);
		ServerPlayer player = survivalPlayer(helper, new BlockPos(9, 2, 9));
		helper.runAfterDelay(5, () -> {
			ServerLevel level = helper.getLevel();
			BlockPos hall = helper.absolutePos(HALL);
			ready(helper);
			helper.assertTrue(WorkplaceConfig.parse("{}").cradles, "cradles default off");
			helper.assertTrue(beds(player, hall).stream().anyMatch(l -> l.startsWith("No Cradle near a bed")), "beds icon with no cradle: " + beds(player, hall));

			use(player, level, helper.absolutePos(NEAR_CRADLE.below()), Direction.UP, new ItemStack(ModBlocks.CRADLE.asItem()));
			helper.assertTrue(level.getBlockState(helper.absolutePos(NEAR_CRADLE)).is(ModBlocks.CRADLE), "the player's cradle wasn't placed: "
				+ level.getBlockState(helper.absolutePos(NEAR_CRADLE)));
			helper.assertTrue(Cradles.nursery(level, hall), "a placed cradle isn't found by the hall");
			helper.assertTrue(beds(player, hall).contains("A Cradle near a bed: children grow up twice as fast, one more baby a day"),
				"beds icon with a cradle: " + beds(player, hall));

			helper.assertTrue(player.gameMode.destroyBlock(helper.absolutePos(NEAR_CRADLE)), "the player couldn't break the cradle");
			helper.assertFalse(Cradles.nursery(level, hall), "a broken cradle still counts");
			helper.assertTrue(beds(player, hall).stream().anyMatch(l -> l.startsWith("No Cradle near a bed")), "beds icon after breaking: " + beds(player, hall));
			helper.succeed();
		});
	}

	/** At night the cradle is for a child: an adult near it is never seated, a child that comes along is. */
	//$ gametest_ticks_batch AREA '100' '"qaCradleAdult"'
	@GameTest(template = AREA, timeoutTicks = 100, batch = "qaCradleAdult")
	public void qaAnAdultNeverSleepsInTheCradle(GameTestHelper helper) {
		village(helper);
		helper.setBlock(NEAR_CRADLE, ModBlocks.CRADLE);
		long time = helper.getLevel().getDayTime();
		Leftovers.after(helper, () -> helper.getLevel().setDayTime(time));
		helper.runAfterDelay(5, () -> {
			ServerLevel level = helper.getLevel();
			BlockPos hall = helper.absolutePos(HALL);
			ready(helper);
			Villager adult = helper.spawn(EntityType.VILLAGER, new BlockPos(7, 2, 14));
			adult.setNoAi(true);
			level.setDayTime(18000);
			Cradles.round(level, hall, 0);
			helper.assertFalse(adult.isPassenger(), "an adult was seated in the cradle: " + adult.getVehicle());
			helper.assertTrue(level.getEntitiesOfClass(CradleSeat.class, new AABB(helper.absolutePos(NEAR_CRADLE)).inflate(2)).isEmpty(), "a seat with nobody for it");
			helper.assertTrue(adult.getAge() >= 0, "the adult was aged backwards: " + adult.getAge());

			Villager child = helper.spawn(EntityType.VILLAGER, new BlockPos(5, 2, 13));
			child.setAge(-24000);
			child.setNoAi(true);
			Cradles.round(level, hall, 0);
			helper.assertTrue(child.getVehicle() instanceof CradleSeat, "the child wasn't seated: " + child.getVehicle());
			helper.assertFalse(adult.isPassenger(), "the adult was seated once a child came");
			helper.succeed();
		});
	}

	// --- 30.13 The Village Banner -------------------------------------------------------------------------------------

	/**
	 * Any banner (here a patterned white one, anywhere in the 3 x 3 grid) and a gold ingot, through the server's real
	 * recipe book, make a Village Banner of that design; the banner is used up. Two banners make nothing. Its tooltip
	 * lists the design: the base colour, each pattern by name, and the hint.
	 */
	//$ gametest_ticks_batch AREA '100' '"qaVillageBannerCraft"'
	@GameTest(template = AREA, timeoutTicks = 100, batch = "qaVillageBannerCraft")
	public void qaTheRealGridCraftsAVillageBannerAndItsTooltipListsTheDesign(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		var patterns = level.registryAccess().lookupOrThrow(Registries.BANNER_PATTERN);
		BannerPatternLayers design = new BannerPatternLayers.Builder()
			.add(patterns.getOrThrow(BannerPatterns.STRIPE_TOP), DyeColor.RED)
			.add(patterns.getOrThrow(BannerPatterns.BORDER), DyeColor.BLACK).build();
		ItemStack banner = new ItemStack(Items.WHITE_BANNER);
		banner.set(DataComponents.BANNER_PATTERNS, design);
		CraftingInput input = CraftingInput.of(3, 3, List.of(
			ItemStack.EMPTY, ItemStack.EMPTY, ItemStack.EMPTY,
			ItemStack.EMPTY, ItemStack.EMPTY, new ItemStack(Items.GOLD_INGOT),
			banner, ItemStack.EMPTY, ItemStack.EMPTY));
		RecipeHolder<CraftingRecipe> recipe = level.getRecipeManager().getRecipeFor(RecipeType.CRAFTING, input, level).orElse(null);
		helper.assertTrue(recipe != null, "no recipe for a banner and a gold ingot");
		ItemStack made = recipe.value().assemble(input, level.registryAccess());
		helper.assertTrue(made.is(ModItems.VILLAGE_BANNER) && made.getCount() == 1, "made " + made);
		helper.assertTrue(VillageBannerItem.base(made) == DyeColor.WHITE && design.equals(VillageBannerItem.patterns(made)), "the design wasn't kept: "
			+ VillageBannerItem.base(made) + " " + VillageBannerItem.patterns(made));
		helper.assertTrue(recipe.value().getRemainingItems(input).stream().allMatch(ItemStack::isEmpty), "something is left in the grid: " + recipe.value().getRemainingItems(input));

		CraftingInput two = CraftingInput.of(3, 1, List.of(banner.copy(), new ItemStack(Items.GOLD_INGOT), new ItemStack(Items.RED_BANNER)));
		helper.assertTrue(level.getRecipeManager().getRecipeFor(RecipeType.CRAFTING, two, level).isEmpty(), "two banners and a gold ingot made something");

		List<String> tooltip = made.getTooltipLines(Item.TooltipContext.of(level), null, TooltipFlag.NORMAL).stream().map(Component::getString).toList();
		helper.assertTrue(tooltip.get(0).equals("Village Banner"), "name: " + tooltip);
		helper.assertTrue(tooltip.contains("Base: White"), "no base line: " + tooltip);
		helper.assertTrue(tooltip.contains("Red Chief") && tooltip.contains("Black Bordure"), "the patterns aren't listed: " + tooltip);
		helper.assertTrue(tooltip.indexOf("Red Chief") < tooltip.indexOf("Black Bordure"), "patterns out of order: " + tooltip);
		helper.assertTrue(tooltip.contains("Right-click a Village Hall to make this the village's colors"), "no hint: " + tooltip);
		helper.assertTrue(tooltip.stream().noneMatch(l -> l.contains("aliveworkplace.") || l.contains("color.minecraft")), "an untranslated line: " + tooltip);
		helper.succeed();
	}

	/**
	 * A second Village Banner of another design used on the same hall replaces the colours (still not used up), and the
	 * chronicle notes it again. Placed against a wall, it hangs as a wall banner of its design.
	 */
	//$ gametest_ticks_batch AREA '100' '"qaVillageBannerAgain"'
	@GameTest(template = AREA, timeoutTicks = 100, batch = "qaVillageBannerAgain")
	public void qaNewColoursReplaceTheOldAndTheBannerHangsOnAWall(GameTestHelper helper) {
		Leftovers.clear(helper);
		Leftovers.halls(helper);
		BlockPos hallAt = new BlockPos(2, 2, 2);
		helper.setBlock(hallAt, ModBlocks.VILLAGE_HALL);
		BlockPos wall = new BlockPos(8, 2, 8);
		helper.setBlock(wall, Blocks.STONE);
		ServerPlayer player = survivalPlayer(helper, new BlockPos(5, 2, 5));
		helper.runAfterDelay(3, () -> {
			ServerLevel level = helper.getLevel();
			BlockPos hall = helper.absolutePos(hallAt);
			VillageHallBlockEntity entity = (VillageHallBlockEntity) level.getBlockEntity(hall);
			Leftovers.after(helper, () -> {
				entity.setColours(null);
				level.setBlockAndUpdate(hall, Blocks.AIR.defaultBlockState());
			});
			var patterns = level.registryAccess().lookupOrThrow(Registries.BANNER_PATTERN);
			BannerPatternLayers cross = new BannerPatternLayers.Builder().add(patterns.getOrThrow(BannerPatterns.CROSS), DyeColor.YELLOW).build();
			use(player, level, hall, Direction.UP, VillageBannerItem.of(DyeColor.GREEN, cross));
			helper.assertTrue(new VillageBanners.Colours(DyeColor.GREEN, cross).equals(entity.colours()), "first colours: " + entity.colours());
			long noted = banners(entity);
			helper.assertTrue(noted >= 1, "the first colours aren't in the chronicle");

			use(player, level, hall, Direction.UP, VillageBannerItem.of(DyeColor.PURPLE, BannerPatternLayers.EMPTY));
			helper.assertTrue(new VillageBanners.Colours(DyeColor.PURPLE, BannerPatternLayers.EMPTY).equals(entity.colours()), "the new colours didn't replace the old: " + entity.colours());
			helper.assertTrue(player.getMainHandItem().is(ModItems.VILLAGE_BANNER) && player.getMainHandItem().getCount() == 1, "the second banner was used up");
			helper.assertTrue(banners(entity) == noted + 1, "the new colours aren't in the chronicle: " + entity.chronicle());

			// Against a wall's west face: a green wall banner with the cross.
			BlockPos at = helper.absolutePos(wall);
			use(player, level, at, Direction.WEST, VillageBannerItem.of(DyeColor.GREEN, cross));
			BlockState hung = level.getBlockState(at.west());
			helper.assertTrue(hung.is(Blocks.GREEN_WALL_BANNER), "hung on the wall: " + hung);
			helper.assertTrue(level.getBlockEntity(at.west()) instanceof BannerBlockEntity b && cross.equals(b.getPatterns()), "the wall banner lost its design");
			helper.succeed();
		});
	}

	// --- 30.14 Seasons and the Harvest Idol --------------------------------------------------------------------------

	/**
	 * On the world clock, harvest season (autumn) starts on the first tick of day 2 x seasonDays + 1 and ends on the
	 * last tick of day 3 x seasonDays, every year, at the default length and at the spec's 8; the season line on autumn's
	 * last day; every dimension shares the overworld's calendar.
	 */
	//$ gametest_ticks_batch AREA '100' '"qaSeasonEdges"'
	@GameTest(template = AREA, timeoutTicks = 100, batch = "qaSeasonEdges")
	public void qaHarvestSeasonStartsAndEndsOnTheRightTick(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		long time = level.getDayTime();
		int days = Seasons.DAYS;
		Leftovers.after(helper, () -> {
			Seasons.DAYS = days;
			level.setDayTime(time);
		});
		helper.assertTrue(WorkplaceConfig.parse("{}").seasonDays == 16 && WorkplaceConfig.parse("{}").harvestIdols, "config defaults");
		ServerLevel nether = level.getServer().getLevel(Level.NETHER);
		for (int length : new int[] {16, 8}) {
			Seasons.DAYS = length;
			long day = VillageNeeds.DAY;
			for (int year = 0; year < 2; year++) {
				long start = (long) year * 4 * length * day;
				harvest(helper, start + 2L * length * day - 1, false, length + "-day seasons, year " + (year + 1) + ", summer's last tick");
				harvest(helper, start + 2L * length * day, true, length + "-day seasons, year " + (year + 1) + ", autumn's first tick");
				harvest(helper, start + 3L * length * day - 1, true, length + "-day seasons, year " + (year + 1) + ", autumn's last tick");
				harvest(helper, start + 3L * length * day, false, length + "-day seasons, year " + (year + 1) + ", winter's first tick");
			}
			level.setDayTime(3L * length * day - 1);
			String line = Seasons.seasonLine(Seasons.today(level)).getString();
			helper.assertTrue(line.equals("Autumn: harvest season, day " + length + " of " + length), "autumn's last day reads: " + line);
			helper.assertTrue(nether != null && HarvestIdols.harvestSeason(nether), "the Nether isn't in harvest season with the overworld");
			level.setDayTime(3L * length * day);
			helper.assertTrue(Seasons.seasonLine(Seasons.today(level)).getString().equals("Winter, day 1 of " + length), "winter's first day reads: "
				+ Seasons.seasonLine(Seasons.today(level)).getString());
			helper.assertFalse(HarvestIdols.harvestSeason(nether), "the Nether is in harvest season in winter");
		}
		helper.succeed();
	}

	/**
	 * A survival player places a Harvest Idol by the wheat with the item: in autumn the wheat grows more; the player
	 * breaks it and the wheat grows exactly as without one.
	 */
	//$ gametest_ticks_batch AREA '100' '"qaIdolPlayer"'
	@GameTest(template = AREA, timeoutTicks = 100, batch = "qaIdolPlayer")
	public void qaAPlayerPlacesAndBreaksAHarvestIdol(GameTestHelper helper) {
		field(helper, Blocks.FARMLAND.defaultBlockState().setValue(FarmBlock.MOISTURE, FarmBlock.MAX_MOISTURE));
		ServerPlayer player = survivalPlayer(helper, new BlockPos(6, 2, 6));
		helper.runAfterDelay(5, () -> {
			ServerLevel level = helper.getLevel();
			autumn(helper);
			BlockPos crop = helper.absolutePos(CROP);
			int without = grow(level, crop, Blocks.WHEAT.defaultBlockState(), BlockStateProperties.AGE_7, 7);
			use(player, level, helper.absolutePos(IDOL.below()), Direction.UP, new ItemStack(ModBlocks.HARVEST_IDOL.asItem()));
			helper.assertTrue(level.getBlockState(helper.absolutePos(IDOL)).is(ModBlocks.HARVEST_IDOL), "the player's idol wasn't placed: " + level.getBlockState(helper.absolutePos(IDOL)));
			helper.assertTrue(HarvestIdols.idols(level).contains(helper.absolutePos(IDOL)) && HarvestIdols.near(level, crop), "a placed idol isn't found");
			int with = grow(level, crop, Blocks.WHEAT.defaultBlockState(), BlockStateProperties.AGE_7, 7);
			double ratio = (double) with / without;
			helper.assertTrue(ratio >= 1.20 && ratio <= 1.30, "with the player's idol " + with + ", without " + without + ": x" + ratio);

			helper.assertTrue(player.gameMode.destroyBlock(helper.absolutePos(IDOL)), "the player couldn't break the idol");
			helper.assertFalse(HarvestIdols.idols(level).contains(helper.absolutePos(IDOL)), "a broken idol is still in the set");
			int after = grow(level, crop, Blocks.WHEAT.defaultBlockState(), BlockStateProperties.AGE_7, 7);
			helper.assertTrue(after == without, "after the idol was broken " + after + " stages, without one " + without);
			helper.succeed();
		});
	}

	/** Not just wheat: carrots, sweet berries and nether wart by an idol in autumn grow about a quarter more too. */
	//$ gametest_ticks_batch AREA '200' '"qaIdolCrops"'
	@GameTest(template = AREA, timeoutTicks = 200, batch = "qaIdolCrops")
	public void qaOtherCropsGrowAQuarterFasterByAnIdol(GameTestHelper helper) {
		field(helper, Blocks.FARMLAND.defaultBlockState().setValue(FarmBlock.MOISTURE, FarmBlock.MAX_MOISTURE));
		helper.runAfterDelay(5, () -> {
			ServerLevel level = helper.getLevel();
			autumn(helper);
			BlockPos crop = helper.absolutePos(CROP);
			record Kind(String name, Block soil, BlockState seed, IntegerProperty age, int max) {
			}
			List<Kind> kinds = List.of(
				new Kind("carrots", Blocks.FARMLAND, Blocks.CARROTS.defaultBlockState(), BlockStateProperties.AGE_7, 7),
				new Kind("sweet berries", Blocks.GRASS_BLOCK, Blocks.SWEET_BERRY_BUSH.defaultBlockState(), BlockStateProperties.AGE_3, 3),
				new Kind("nether wart", Blocks.SOUL_SAND, Blocks.NETHER_WART.defaultBlockState(), BlockStateProperties.AGE_3, 3));
			for (Kind kind : kinds) {
				helper.setBlock(IDOL, Blocks.AIR);
				BlockState soil = kind.soil() == Blocks.FARMLAND
					? Blocks.FARMLAND.defaultBlockState().setValue(FarmBlock.MOISTURE, FarmBlock.MAX_MOISTURE) : kind.soil().defaultBlockState();
				level.setBlock(crop.below(), soil, Block.UPDATE_CLIENTS);
				StringBuilder per = new StringBuilder();
				int without = 0;
				int with = 0;
				for (long s = SEED; s < SEED + SEEDS; s++) {
					helper.setBlock(IDOL, Blocks.AIR);
					int w0 = grow(level, crop, kind.seed(), kind.age(), kind.max(), new XoroshiroRandomSource(s));
					helper.setBlock(IDOL, ModBlocks.HARVEST_IDOL);
					int w1 = grow(level, crop, kind.seed(), kind.age(), kind.max(), new XoroshiroRandomSource(s));
					without += w0;
					with += w1;
					per.append(String.format(" %.2f", (double) w1 / w0));
				}
				double ratio = (double) with / without;
				LOG.info("QA idol crops: {} x{} over {} seeds (each:{})", kind.name(), ratio, SEEDS, per);
				helper.assertTrue(ratio >= 1.20 && ratio <= 1.30, kind.name() + ": with an idol " + with + " stages, without " + without + ": x" + ratio + " (per seed:" + per + ")");
			}
			helper.succeed();
		});
	}

	/**
	 * Idols are kept per dimension: one in the Nether at the very coordinates of an overworld field does nothing for the
	 * overworld wheat, but nether wart beside it in the Nether grows faster in the overworld's autumn.
	 */
	//$ gametest_ticks_batch AREA '200' '"qaIdolNether"'
	@GameTest(template = AREA, timeoutTicks = 200, batch = "qaIdolNether")
	public void qaAnIdolInTheNetherDoesntReachTheOverworld(GameTestHelper helper) {
		field(helper, Blocks.FARMLAND.defaultBlockState().setValue(FarmBlock.MOISTURE, FarmBlock.MAX_MOISTURE));
		ServerLevel level = helper.getLevel();
		ServerLevel nether = level.getServer().getLevel(Level.NETHER);
		// Up at y 64 in both dimensions, above the test area: the same coordinates in each.
		BlockPos crop = helper.absolutePos(CROP).atY(64);
		BlockPos idol = helper.absolutePos(IDOL).atY(64);
		Leftovers.after(helper, () -> {
			for (BlockPos p : List.of(idol, crop, crop.below())) {
				nether.setBlockAndUpdate(p, Blocks.AIR.defaultBlockState());
				level.setBlockAndUpdate(p, Blocks.AIR.defaultBlockState());
			}
		});
		helper.runAfterDelay(5, () -> {
			autumn(helper);
			level.setBlock(crop.below(), Blocks.FARMLAND.defaultBlockState().setValue(FarmBlock.MOISTURE, FarmBlock.MAX_MOISTURE), Block.UPDATE_CLIENTS);
			int without = grow(level, crop, Blocks.WHEAT.defaultBlockState(), BlockStateProperties.AGE_7, 7);
			nether.setBlockAndUpdate(idol, ModBlocks.HARVEST_IDOL.defaultBlockState());
			helper.assertTrue(nether.getBlockState(idol).is(ModBlocks.HARVEST_IDOL), "no idol in the Nether");
			helper.assertTrue(HarvestIdols.idols(nether).contains(idol) && HarvestIdols.near(nether, crop), "the Nether idol isn't in the Nether's set");
			helper.assertFalse(HarvestIdols.idols(level).contains(idol) || HarvestIdols.near(level, crop), "the Nether idol counts in the overworld");
			helper.assertTrue(grow(level, crop, Blocks.WHEAT.defaultBlockState(), BlockStateProperties.AGE_7, 7) == without,
				"the overworld wheat grew more from an idol in the Nether");

			nether.setBlock(crop.below(), Blocks.SOUL_SAND.defaultBlockState(), Block.UPDATE_CLIENTS);
			BlockState wart = Blocks.NETHER_WART.defaultBlockState();
			int by = 0;
			int alone = 0;
			for (long s = SEED; s < SEED + SEEDS; s++) {
				nether.setBlockAndUpdate(idol, ModBlocks.HARVEST_IDOL.defaultBlockState());
				by += grow(nether, crop, wart, BlockStateProperties.AGE_3, 3, new XoroshiroRandomSource(s));
				nether.setBlockAndUpdate(idol, Blocks.AIR.defaultBlockState());
				alone += grow(nether, crop, wart, BlockStateProperties.AGE_3, 3, new XoroshiroRandomSource(s));
			}
			helper.assertFalse(HarvestIdols.idols(nether).contains(idol), "the broken Nether idol is still in the set");
			double ratio = (double) by / alone;
			helper.assertTrue(ratio >= 1.20 && ratio <= 1.30, "nether wart in the Nether: by the idol " + by + ", without " + alone + ": x" + ratio);
			helper.succeed();
		});
	}

	// --- Helpers -----------------------------------------------------------------------------------------------------

	private static void harvest(GameTestHelper helper, long dayTime, boolean expected, String what) {
		ServerLevel level = helper.getLevel();
		level.setDayTime(dayTime);
		helper.assertTrue(HarvestIdols.harvestSeason(level) == expected, what + " (day " + Seasons.today(level).day() + ", "
			+ Seasons.today(level).season() + " day " + Seasons.today(level).dayOfSeason() + "): harvest season " + !expected);
	}

	/** The first day of autumn at noon on the overworld's clock; the clock is put back afterwards by {@link #field}. */
	private static void autumn(GameTestHelper helper) {
		long day = 2L * Seasons.DAYS + 1;
		helper.getLevel().setDayTime((day - 1) * VillageNeeds.DAY + 6000);
	}

	/** {@link #TICKS} random ticks from a fixed random to the crop at {@code pos} from {@code seed}; the stages it grew (ripe: back to seed). */
	private static int grow(ServerLevel level, BlockPos pos, BlockState seed, IntegerProperty age, int max) {
		return grow(level, pos, seed, age, max, SEED);
	}

	private static int grow(ServerLevel level, BlockPos pos, BlockState seed, IntegerProperty age, int max, long randomSeed) {
		return grow(level, pos, seed, age, max, RandomSource.create(randomSeed));
	}

	private static int grow(ServerLevel level, BlockPos pos, BlockState seed, IntegerProperty age, int max, RandomSource random) {
		level.setBlock(pos, seed, Block.UPDATE_CLIENTS);
		int grown = 0;
		for (int i = 0; i < TICKS; i++) {
			BlockState before = level.getBlockState(pos);
			if (!before.is(seed.getBlock())) {
				throw new net.minecraft.gametest.framework.GameTestAssertException("the crop is gone: " + before);
			}
			int was = before.getValue(age);
			before.randomTick(level, pos, random);
			int now = level.getBlockState(pos).getValue(age);
			grown += now - was;
			if (now >= max) {
				level.setBlock(pos, seed, Block.UPDATE_CLIENTS);
			}
		}
		return grown;
	}

	/** A 3 x 3 of {@code soil} under the crop's place; afterwards the clock is put back and the idol taken away. */
	private static void field(GameTestHelper helper, BlockState soil) {
		Leftovers.clear(helper);
		long time = helper.getLevel().getDayTime();
		for (int dx = -1; dx <= 1; dx++) {
			for (int dz = -1; dz <= 1; dz++) {
				helper.setBlock(CROP.offset(dx, -1, dz), soil);
			}
		}
		Leftovers.after(helper, () -> {
			helper.getLevel().setDayTime(time);
			helper.getLevel().setBlockAndUpdate(helper.absolutePos(IDOL), Blocks.AIR.defaultBlockState());
			helper.getLevel().setBlockAndUpdate(helper.absolutePos(CROP), Blocks.AIR.defaultBlockState());
		});
	}

	private static ServerPlayer survivalPlayer(GameTestHelper helper, BlockPos at) {
		ServerPlayer player = helper.makeMockServerPlayerInLevel();
		player.setGameMode(GameType.SURVIVAL);
		BlockPos abs = helper.absolutePos(at);
		player.moveTo(abs.getX() + 0.5, abs.getY(), abs.getZ() + 0.5);
		Leftovers.after(helper, () -> helper.getLevel().getServer().getPlayerList().remove(player));
		return player;
	}

	private static void use(ServerPlayer player, ServerLevel level, BlockPos at, Direction face, ItemStack held) {
		player.setItemInHand(InteractionHand.MAIN_HAND, held);
		player.gameMode.useItemOn(player, level, held, InteractionHand.MAIN_HAND, new BlockHitResult(Vec3.atCenterOf(at).relative(face, 0.5), face, at, false));
	}

	/** The lines of the hall's beds icon. */
	private static List<String> beds(ServerPlayer player, BlockPos hall) {
		ItemLore lore = VillageHallScreen.forTest(player, hall).icon(VillageHallScreen.BEDS).get(DataComponents.LORE);
		return lore == null ? List.of() : lore.lines().stream().map(Component::getString).toList();
	}

	private static long banners(VillageHallBlockEntity entity) {
		return entity.chronicle().stream().filter(e -> e.kind() == Chronicle.Kind.BANNER).count();
	}

	/** A village of radius 16 with its hall and a bed (head at {@link #BED_HEAD}); afterwards no hall or cradle is left. */
	private static void village(GameTestHelper helper) {
		Leftovers.clear(helper);
		int radius = VillageHalls.RADIUS;
		VillageHalls.RADIUS = 16;
		helper.setBlock(BED_HEAD.north(), Blocks.RED_BED.defaultBlockState().setValue(BedBlock.FACING, Direction.SOUTH).setValue(BedBlock.PART, BedPart.FOOT));
		helper.setBlock(BED_HEAD, Blocks.RED_BED.defaultBlockState().setValue(BedBlock.FACING, Direction.SOUTH).setValue(BedBlock.PART, BedPart.HEAD));
		helper.setBlock(HALL, ModBlocks.VILLAGE_HALL);
		Leftovers.after(helper, () -> {
			if (helper.getLevel().getBlockEntity(helper.absolutePos(HALL)) instanceof VillageHallBlockEntity hall) {
				hall.setEdicts(List.of());
				hall.setReforms(List.of());
			}
			for (BlockPos p : List.of(HALL, NEAR_CRADLE, FOUR_OFF, FIVE_OFF)) {
				helper.getLevel().setBlockAndUpdate(helper.absolutePos(p), Blocks.AIR.defaultBlockState());
			}
			VillageHalls.RADIUS = radius;
			VillageNeeds.forget();
			CivicEffects.forget();
			Moods.forget();
		});
	}

	private static VillageHallBlockEntity ready(GameTestHelper helper) {
		VillageHallBlockEntity hall = (VillageHallBlockEntity) helper.getLevel().getBlockEntity(helper.absolutePos(HALL));
		hall.setRank(VillageRanks.Rank.HAMLET);
		hall.setEdicts(List.of());
		hall.setReforms(List.of());
		VillageNeeds.forget();
		CivicEffects.forget();
		Moods.forget();
		return hall;
	}
}
