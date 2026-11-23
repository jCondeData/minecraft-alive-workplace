package io.github.jcondedata.aliveworkplace.test;

import io.github.jcondedata.aliveworkplace.AliveWorkplace;
import io.github.jcondedata.aliveworkplace.WorkplaceConfig;
import io.github.jcondedata.aliveworkplace.blueprint.BlueprintData;
import io.github.jcondedata.aliveworkplace.blueprint.StarterBlueprints;
import io.github.jcondedata.aliveworkplace.build.BuildSiteManager;
import io.github.jcondedata.aliveworkplace.hall.Chronicle;
import io.github.jcondedata.aliveworkplace.hall.CivicEffects;
import io.github.jcondedata.aliveworkplace.hall.CradleSeat;
import io.github.jcondedata.aliveworkplace.hall.Cradles;
import io.github.jcondedata.aliveworkplace.hall.Curfew;
import io.github.jcondedata.aliveworkplace.hall.Edicts;
import io.github.jcondedata.aliveworkplace.hall.HarvestIdols;
import io.github.jcondedata.aliveworkplace.hall.MarketDays;
import io.github.jcondedata.aliveworkplace.hall.Seasons;
import io.github.jcondedata.aliveworkplace.hall.VillageBannerItem;
import io.github.jcondedata.aliveworkplace.hall.VillageBanners;
import io.github.jcondedata.aliveworkplace.hall.VillageGrowth;
import io.github.jcondedata.aliveworkplace.hall.VillageHallBlockEntity;
import io.github.jcondedata.aliveworkplace.hall.VillageHalls;
import io.github.jcondedata.aliveworkplace.hall.VillageNeeds;
import io.github.jcondedata.aliveworkplace.hall.VillageProtection;
import io.github.jcondedata.aliveworkplace.hall.VillageRanks;
import io.github.jcondedata.aliveworkplace.people.Moods;
import io.github.jcondedata.aliveworkplace.registry.ModBlocks;
import io.github.jcondedata.aliveworkplace.registry.ModItems;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.npc.WanderingTrader;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CropBlock;
import net.minecraft.world.level.block.FarmBlock;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.SugarCaneBlock;
import net.minecraft.world.level.block.entity.BannerPatternLayers;
import net.minecraft.world.level.block.entity.BannerPatterns;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BedPart;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

/**
 * QA (lane-c-1123-0953) from the civic items' spec (ROADMAP 30.12 the Cradle, 30.13 the Village Banner, 30.14 seasons
 * and the Harvest Idol; docs/wiki/civic-items.md) and market days (docs/wiki/market-days.md, B1361, B1362), filling what
 * the earlier tests left: the owner of a protected village raising colors by hand and what he reads, with the switch off
 * and over a save; a stranger to a protected village changing nothing; the recipe's edges in the real recipe list and
 * the tooltip; two cradles adding nothing more and the hall's line; which children the cradles seat (one each, the nearest, none from 9 blocks); the calendar at
 * {@code seasonDays} 1 and 120 and the config's clamp; only the idol crops growing faster; Open Gates' extra trader with
 * the news and the chronicle's sentence; a square outside the village holding no market. Each test has its own batch.
 */
public class QaCivicMarket1123cGameTests implements FabricGameTest {
	private static final String AREA = "aliveworkplace_test:big_area";
	private static final BlockPos HALL = new BlockPos(11, 2, 11);
	/** Two beds (heads at z = 16) with a cradle 2 blocks south of each head. */
	private static final BlockPos CRADLE_A = new BlockPos(5, 2, 18);
	private static final BlockPos CRADLE_B = new BlockPos(14, 2, 18);
	private static final BlockPos MARKET_HALL = new BlockPos(20, 2, 20);

	// --- The Village Banner ------------------------------------------------------------------------------------------

	/**
	 * The owner of a protected village right-clicks the hall with a Village Banner: the colors are his design, the banner
	 * is kept, he reads "These are now the colors of <village>" and the chronicle "<owner> raised new colors over
	 * <village>". With {@code villageBanners} off he reads that they're off and nothing changes; the colors already set
	 * survive a save while off. Every sentence in American English.
	 */
	//$ gametest_ticks_batch AREA '100' '"qaCivic1123cOwnerColors"'
	@GameTest(template = AREA, timeoutTicks = 100, batch = "qaCivic1123cOwnerColors")
	public void qaTheOwnerRaisesColorsByHandAndReadsWhatHappened(GameTestHelper helper) {
		Leftovers.clear(helper);
		Leftovers.halls(helper);
		helper.setBlock(HALL, ModBlocks.VILLAGE_HALL);
		List<Component> seen = new ArrayList<>();
		ServerPlayer owner = QaStageFourGameTests.player(helper, seen, GameType.SURVIVAL);
		boolean protection = VillageProtection.ENABLED;
		boolean banners = VillageBanners.ENABLED;
		helper.runAfterDelay(2, () -> {
			ServerLevel level = helper.getLevel();
			BlockPos hall = helper.absolutePos(HALL);
			VillageHallBlockEntity entity = (VillageHallBlockEntity) level.getBlockEntity(hall);
			VillageProtection.ENABLED = true;
			entity.setOwner(owner.getUUID(), owner.getGameProfile().getName());
			entity.setProtected(true);
			Leftovers.after(helper, () -> {
				entity.setProtected(false);
				entity.setColours(null);
				VillageProtection.ENABLED = protection;
				VillageBanners.ENABLED = banners;
			});
			VillageBanners.ENABLED = true;
			BannerPatternLayers design = stripe(helper, DyeColor.WHITE);
			String village = VillageHalls.name(level, hall).getString();

			use(owner, level, hall, VillageBannerItem.of(DyeColor.GREEN, design));
			VillageBanners.Colours set = entity.colours();
			helper.assertTrue(set != null && set.base() == DyeColor.GREEN && set.patterns().equals(design), "the owner's colors weren't raised: " + set);
			helper.assertTrue(owner.getMainHandItem().is(ModItems.VILLAGE_BANNER) && owner.getMainHandItem().getCount() == 1,
				"the banner was used up: " + owner.getMainHandItem());
			String told = "These are now the colors of " + village;
			helper.assertTrue(seen.stream().anyMatch(c -> c.getString().equals(told)), "the owner wasn't told '" + told + "': " + texts(seen));
			String line = owner.getGameProfile().getName() + " raised new colors over " + village;
			helper.assertTrue(entity.chronicle().stream().anyMatch(e -> e.kind() == Chronicle.Kind.BANNER && e.text().getString().equals(line)),
				"no chronicle line '" + line + "': " + entity.chronicle());

			// Off: told so, nothing changes, the banner stays in hand; the colors are kept over a save.
			seen.clear();
			VillageBanners.ENABLED = false;
			use(owner, level, hall, VillageBannerItem.of(DyeColor.BLACK, BannerPatternLayers.EMPTY));
			helper.assertTrue(set.equals(entity.colours()), "the colors changed with the switch off: " + entity.colours());
			helper.assertTrue(owner.getMainHandItem().is(ModItems.VILLAGE_BANNER), "the banner went with the switch off");
			helper.assertTrue(seen.stream().anyMatch(c -> c.getString().equals("Village Banners are turned off in the config")),
				"not told the banners are off: " + texts(seen));
			CompoundTag tag = entity.saveWithFullMetadata(level.registryAccess());
			VillageHallBlockEntity copy = (VillageHallBlockEntity) BlockEntity.loadStatic(hall, level.getBlockState(hall), tag, level.registryAccess());
			helper.assertTrue(copy != null && set.equals(copy.colours()), "the colors weren't saved while off: " + (copy == null ? null : copy.colours()));

			// The banner's sentences, in American English.
			for (String key : List.of("message.aliveworkplace.village_banner.set", "message.aliveworkplace.village_banner.off",
				"tooltip.aliveworkplace.village_banner.hint", "tooltip.aliveworkplace.village_banner.base", "chronicle.aliveworkplace.banner",
				"aliveworkplace.config.villageBanners.tooltip", "item.aliveworkplace.village_banner")) {
				String text = Component.translatable(key, "X", "Y").getString();
				helper.assertFalse(text.equals(key), "untranslated: " + key);
				helper.assertFalse(text.toLowerCase(java.util.Locale.ROOT).contains("colour"), key + " isn't American English: " + text);
			}
			helper.succeed();
		});
	}

	/**
	 * The owner set green colors and protected his village; a stranger right-clicks the hall with a black Village Banner.
	 * Refused: the colors stay green and the chronicle has only the owner's line.
	 */
	//$ gametest_ticks_batch AREA '100' '"qaCivic1123cStrangerColors"'
	@GameTest(template = AREA, timeoutTicks = 100, batch = "qaCivic1123cStrangerColors")
	public void qaAStrangerCantChangeAProtectedVillagesColors(GameTestHelper helper) {
		Leftovers.clear(helper);
		Leftovers.halls(helper);
		helper.setBlock(HALL, ModBlocks.VILLAGE_HALL);
		List<Component> ownerSeen = new ArrayList<>();
		List<Component> strangerSeen = new ArrayList<>();
		ServerPlayer owner = QaStageFourGameTests.player(helper, ownerSeen, GameType.SURVIVAL);
		ServerPlayer stranger = QaStageFourGameTests.player(helper, strangerSeen, GameType.SURVIVAL);
		boolean protection = VillageProtection.ENABLED;
		boolean banners = VillageBanners.ENABLED;
		helper.runAfterDelay(2, () -> {
			ServerLevel level = helper.getLevel();
			BlockPos hall = helper.absolutePos(HALL);
			VillageHallBlockEntity entity = (VillageHallBlockEntity) level.getBlockEntity(hall);
			VillageProtection.ENABLED = true;
			VillageBanners.ENABLED = true;
			entity.setOwner(owner.getUUID(), owner.getGameProfile().getName());
			entity.setProtected(true);
			Leftovers.after(helper, () -> {
				entity.setProtected(false);
				entity.setColours(null);
				VillageProtection.ENABLED = protection;
				VillageBanners.ENABLED = banners;
			});
			var patterns = level.registryAccess().lookupOrThrow(Registries.BANNER_PATTERN);
			BannerPatternLayers design = new BannerPatternLayers.Builder().add(patterns.getOrThrow(BannerPatterns.STRIPE_TOP), DyeColor.WHITE).build();
			use(owner, level, hall, VillageBannerItem.of(DyeColor.GREEN, design));
			VillageBanners.Colours ours = entity.colours();
			helper.assertTrue(ours != null && ours.base() == DyeColor.GREEN, "setup: the owner's colors weren't raised: " + ours);
			helper.assertFalse(stranger.hasPermissions(2), "setup: the stranger is an operator");
			helper.assertTrue(VillageProtection.keeper(level, stranger, hall).isPresent(), "setup: the village doesn't keep the stranger out");

			use(stranger, level, hall, VillageBannerItem.of(DyeColor.BLACK, BannerPatternLayers.EMPTY));
			helper.assertTrue(ours.equals(entity.colours()),
				"a stranger changed a protected village's colors from green to " + entity.colours().base()
					+ "; chronicle: " + entity.chronicle().stream().filter(e -> e.kind() == Chronicle.Kind.BANNER).map(e -> e.text().getString()).toList());
			helper.assertTrue(entity.chronicle().stream().filter(e -> e.kind() == Chronicle.Kind.BANNER).count() == 1, "the stranger's colors are in the chronicle");
			helper.succeed();
		});
	}

	/**
	 * The recipe in the game's own recipe list: one banner and one gold ingot anywhere in a 3 x 3 grid make a Village
	 * Banner of that banner's base and design; two banners, two ingots, a stray stick, a lone banner, or a Village Banner
	 * and gold make nothing. A plain banner makes a plain Village Banner whose tooltip names its base and the hall hint.
	 */
	//$ gametest_ticks_batch AREA '100' '"qaCivic1123cRecipe"'
	@GameTest(template = AREA, timeoutTicks = 100, batch = "qaCivic1123cRecipe")
	public void qaTheRecipeTakesOneBannerAndOneGoldIngotOnly(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		boolean banners = VillageBanners.ENABLED;
		VillageBanners.ENABLED = true;
		Leftovers.after(helper, () -> VillageBanners.ENABLED = banners);
		BannerPatternLayers design = stripe(helper, DyeColor.RED);
		ItemStack striped = new ItemStack(Items.PURPLE_BANNER);
		striped.set(DataComponents.BANNER_PATTERNS, design);

		ItemStack made = craft(level, grid(striped, 0, new ItemStack(Items.GOLD_INGOT), 8));
		helper.assertTrue(made.is(ModItems.VILLAGE_BANNER) && VillageBannerItem.base(made) == DyeColor.PURPLE
			&& VillageBannerItem.patterns(made).equals(design), "corner to corner made " + made + " " + VillageBannerItem.patterns(made));

		Map<String, List<ItemStack>> nothing = Map.of(
			"two banners and gold", grid(new ItemStack(Items.WHITE_BANNER), 0, new ItemStack(Items.WHITE_BANNER), 1, new ItemStack(Items.GOLD_INGOT), 4),
			"a banner and two gold", grid(new ItemStack(Items.WHITE_BANNER), 0, new ItemStack(Items.GOLD_INGOT), 1, new ItemStack(Items.GOLD_INGOT), 2),
			"a banner, gold and a stick", grid(new ItemStack(Items.WHITE_BANNER), 0, new ItemStack(Items.GOLD_INGOT), 1, new ItemStack(Items.STICK), 2),
			"a lone banner", grid(new ItemStack(Items.WHITE_BANNER), 4),
			"a Village Banner and gold", grid(VillageBannerItem.of(DyeColor.RED, design), 0, new ItemStack(Items.GOLD_INGOT), 1),
			"a gold nugget", grid(new ItemStack(Items.WHITE_BANNER), 0, new ItemStack(Items.GOLD_NUGGET), 1));
		nothing.forEach((what, items) -> {
			ItemStack out = craft(level, items);
			helper.assertTrue(!out.is(ModItems.VILLAGE_BANNER), what + " made a Village Banner");
		});

		ItemStack plain = craft(level, grid(new ItemStack(Items.WHITE_BANNER), 3, new ItemStack(Items.GOLD_INGOT), 5));
		helper.assertTrue(plain.is(ModItems.VILLAGE_BANNER) && VillageBannerItem.base(plain) == DyeColor.WHITE
			&& VillageBannerItem.patterns(plain).layers().isEmpty(), "a plain banner made " + plain);
		List<String> tooltip = plain.getTooltipLines(Item.TooltipContext.of(level), null, TooltipFlag.NORMAL).stream().map(Component::getString).toList();
		helper.assertTrue(tooltip.contains("Base: White") && tooltip.contains("Right-click a Village Hall to make this the village's colors"),
			"the tooltip: " + tooltip);
		helper.succeed();
	}

	// --- The Cradle --------------------------------------------------------------------------------------------------

	/**
	 * Two cradles, each by a bed: still one nursery village. A newborn grows up in 12000 ticks as with one (a baby one
	 * round before), one extra baby a day, half a day between births, and the hall's line says so word for word; with the
	 * cradles gone the line says there's none.
	 */
	//$ gametest_ticks_batch AREA '100' '"qaCivic1123cTwoCradles"'
	@GameTest(template = AREA, timeoutTicks = 100, batch = "qaCivic1123cTwoCradles")
	public void qaTwoCradlesAddNothingMoreThanOne(GameTestHelper helper) {
		nursery(helper);
		helper.runAfterDelay(5, () -> {
			ServerLevel level = helper.getLevel();
			BlockPos hall = helper.absolutePos(HALL);
			ready(helper);
			helper.assertTrue(Cradles.cradles(level, hall).size() == 2, "both cradles should count as cradles: " + Cradles.cradles(level, hall));
			helper.assertTrue(Cradles.extraBirths(level, hall) == 1, "two cradles, extra births " + Cradles.extraBirths(level, hall));
			helper.assertTrue(VillageGrowth.every(level, hall) == 12000, "two cradles, a birth every " + VillageGrowth.every(level, hall));
			helper.assertValueEqual(Cradles.status(level, hall).getString(),
				"A Cradle near a bed: children grow up twice as fast, one more baby a day", "the hall's line");

			Villager child = helper.spawn(EntityType.VILLAGER, new BlockPos(9, 2, 13));
			child.setAge(-24000);
			child.setNoAi(true);
			int round = VillageNeeds.CHECK_EVERY;
			int rounds = 12000 / round;
			for (int i = 0; i < rounds - 1; i++) {
				child.setAge(Math.min(0, child.getAge() + round));
				Cradles.round(level, hall, round);
			}
			helper.assertTrue(child.isBaby() && child.getAge() == -2 * round, "two cradles aged the child faster: " + child.getAge());
			child.setAge(Math.min(0, child.getAge() + round));
			Cradles.round(level, hall, round);
			helper.assertTrue(!child.isBaby(), "not grown up after 12000 ticks with two cradles: " + child.getAge());

			helper.setBlock(CRADLE_A, Blocks.AIR);
			helper.setBlock(CRADLE_B, Blocks.AIR);
			VillageNeeds.forget();
			CivicEffects.forget();
			helper.assertValueEqual(Cradles.status(level, hall).getString(),
				"No Cradle near a bed (a Cradle: children grow up twice as fast, one more baby a day)", "the hall's line with none");
			helper.assertTrue(VillageGrowth.every(level, hall) == 24000, "no cradles, a birth every " + VillageGrowth.every(level, hall));
			helper.succeed();
		});
	}

	/**
	 * At night each empty cradle takes the nearest child within 8 blocks: a child 9 blocks from the only cradles stays up;
	 * with three children by two cradles, one child sits in each and the third stays up; another round seats nobody more.
	 */
	//$ gametest_ticks_batch AREA '100' '"qaCivic1123cCradleSeats"'
	@GameTest(template = AREA, timeoutTicks = 100, batch = "qaCivic1123cCradleSeats")
	public void qaEachCradleSeatsTheNearestChildAndNoneFromNineBlocks(GameTestHelper helper) {
		nursery(helper);
		helper.runAfterDelay(5, () -> {
			ServerLevel level = helper.getLevel();
			BlockPos hall = helper.absolutePos(HALL);
			ready(helper);
			level.setDayTime(18000);
			Villager far = child(helper, new BlockPos(5, 2, 9)); // 9 blocks north of cradle A, 9 west and 9 north of B
			Cradles.round(level, hall, 0);
			helper.assertTrue(!far.isPassenger(), "a child 9 blocks from the cradles was seated: " + far.getVehicle());

			Villager byA = child(helper, new BlockPos(5, 2, 20));
			Villager between = child(helper, new BlockPos(6, 2, 21));
			Villager byB = child(helper, new BlockPos(14, 2, 20));
			Cradles.round(level, hall, 0);
			Cradles.round(level, hall, 0);
			helper.assertTrue(byA.getVehicle() instanceof CradleSeat a && a.blockPosition().equals(helper.absolutePos(CRADLE_A)),
				"the child by cradle A isn't in it: " + byA.getVehicle());
			helper.assertTrue(byB.getVehicle() instanceof CradleSeat b && b.blockPosition().equals(helper.absolutePos(CRADLE_B)),
				"the child by cradle B isn't in it: " + byB.getVehicle());
			helper.assertTrue(!between.isPassenger() && !far.isPassenger(), "a third child was seated: " + between.getVehicle() + " / " + far.getVehicle());
			List<CradleSeat> seats = level.getEntitiesOfClass(CradleSeat.class, new AABB(hall).inflate(20), CradleSeat::isAlive);
			helper.assertTrue(seats.size() == 2, "seats: " + seats.size());
			for (CradleSeat seat : seats) {
				seat.ejectPassengers();
				seat.discard();
			}
			helper.succeed();
		});
	}

	// --- Seasons and the Harvest Idol --------------------------------------------------------------------------------

	/**
	 * The calendar at the config's edges: {@code seasonDays} 0 and 500 are clamped to 1 and 120. At 1 day a season, day 3
	 * is autumn (harvest season) and day 4 winter, day 7 autumn again; at 120, harvest season runs from day 241 to day 360.
	 * The hall's line reads "Autumn: harvest season, day 1 of 1" and "Summer, day 120 of 120", and the clock agrees.
	 */
	//$ gametest_ticks_batch AREA '100' '"qaCivic1123cSeasonEdges"'
	@GameTest(template = AREA, timeoutTicks = 100, batch = "qaCivic1123cSeasonEdges")
	public void qaTheCalendarAtOneAndAHundredTwentyDaysASeason(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		int days = Seasons.DAYS;
		long time = level.getDayTime();
		Leftovers.after(helper, () -> {
			Seasons.DAYS = days;
			level.setDayTime(time);
		});
		helper.assertTrue(WorkplaceConfig.parse("{\"seasonDays\": 0}").seasonDays == 1, "seasonDays 0 not clamped to 1");
		helper.assertTrue(WorkplaceConfig.parse("{\"seasonDays\": 500}").seasonDays == 120, "seasonDays 500 not clamped to 120");
		helper.assertTrue(WorkplaceConfig.parse("{}").seasonDays == 16, "seasonDays doesn't default to 16");

		Seasons.DAYS = 1;
		long[][] one = {{1, 0}, {2, 1}, {3, 2}, {4, 3}, {5, 0}, {7, 2}, {8, 3}};
		for (long[] c : one) {
			level.setDayTime((c[0] - 1) * 24000L + 12000);
			Seasons.Date date = Seasons.today(level);
			helper.assertTrue(date.season().ordinal() == c[1] && date.dayOfSeason() == 1,
				"1-day seasons, day " + c[0] + ": " + date);
			helper.assertTrue(HarvestIdols.harvestSeason(level) == (c[1] == 2), "1-day seasons, day " + c[0] + ": harvest season " + HarvestIdols.harvestSeason(level));
		}
		helper.assertValueEqual(Seasons.seasonLine(Seasons.date(3, 1)).getString(), "Autumn: harvest season, day 1 of 1", "the line on day 3");

		Seasons.DAYS = 120;
		long[][] long_ = {{240, 1}, {241, 2}, {360, 2}, {361, 3}, {480 + 241, 2}};
		for (long[] c : long_) {
			level.setDayTime((c[0] - 1) * 24000L);
			helper.assertTrue(Seasons.today(level).season().ordinal() == c[1], "120-day seasons, first tick of day " + c[0] + ": " + Seasons.today(level));
			level.setDayTime(c[0] * 24000L - 1);
			helper.assertTrue(Seasons.today(level).season().ordinal() == c[1], "120-day seasons, last tick of day " + c[0] + ": " + Seasons.today(level));
		}
		helper.assertValueEqual(Seasons.seasonLine(Seasons.date(240, 120)).getString(), "Summer, day 120 of 120", "the line on day 240");
		helper.assertValueEqual(Seasons.seasonLine(Seasons.date(360, 120)).getString(), "Autumn: harvest season, day 120 of 120", "the line on day 360");
		helper.succeed();
	}

	/**
	 * Only the idol crops grow faster: by an idol in harvest season, sugar cane (not in the list) grows exactly as without,
	 * from the same fixed random, while potatoes (in the list) grow about a quarter more.
	 */
	//$ gametest_ticks_batch AREA '100' '"qaCivic1123cIdolOnlyCrops"'
	@GameTest(template = AREA, timeoutTicks = 100, batch = "qaCivic1123cIdolOnlyCrops")
	public void qaOnlyTheIdolCropsGrowFaster(GameTestHelper helper) {
		Leftovers.clear(helper);
		ServerLevel level = helper.getLevel();
		long time = level.getDayTime();
		boolean idols = HarvestIdols.ENABLED;
		BlockPos idol = new BlockPos(8, 2, 10);
		BlockPos cane = new BlockPos(4, 2, 4);
		BlockPos potato = new BlockPos(12, 2, 4);
		helper.setBlock(cane.below(), Blocks.SAND);
		helper.setBlock(cane.below().east(), Blocks.WATER);
		helper.setBlock(cane, Blocks.SUGAR_CANE);
		helper.setBlock(potato.below(), Blocks.FARMLAND.defaultBlockState().setValue(FarmBlock.MOISTURE, FarmBlock.MAX_MOISTURE));
		helper.setBlock(potato.below().east(), Blocks.WATER);
		helper.setBlock(potato, Blocks.POTATOES);
		Leftovers.after(helper, () -> {
			level.setDayTime(time);
			HarvestIdols.ENABLED = idols;
			level.setBlockAndUpdate(helper.absolutePos(idol), Blocks.AIR.defaultBlockState());
		});
		helper.runAfterDelay(5, () -> {
			HarvestIdols.ENABLED = true;
			long autumn = 2L * Seasons.DAYS + 1;
			level.setDayTime((autumn - 1) * 24000L + 6000);
			helper.assertTrue(HarvestIdols.harvestSeason(level), "not harvest season: " + Seasons.today(level));
			helper.assertFalse(Blocks.SUGAR_CANE.defaultBlockState().is(HarvestIdols.CROPS), "sugar cane is an idol crop");
			int caneWithout = cane(helper, cane);
			int potatoWithout = crop(helper, potato);
			helper.setBlock(idol, ModBlocks.HARVEST_IDOL);
			helper.assertTrue(HarvestIdols.near(level, helper.absolutePos(cane)) && HarvestIdols.near(level, helper.absolutePos(potato)), "the idol isn't near");
			int caneWith = cane(helper, cane);
			int potatoWith = crop(helper, potato);
			helper.assertTrue(caneWith == caneWithout, "sugar cane grew " + caneWith + " by an idol, " + caneWithout + " without");
			double ratio = (double) potatoWith / potatoWithout;
			helper.assertTrue(ratio >= 1.15 && ratio <= 1.35, "potatoes " + potatoWith + " by an idol, " + potatoWithout + " without: x" + ratio);
			helper.succeed();
		});
	}

	// --- Market days -------------------------------------------------------------------------------------------------

	/**
	 * Under the Open Gates edict one more trader comes (3 for a Hamlet); a player in the village reads, in gold, "It's
	 * market day in <village>: traders have come to the Market Square"; the chronicle says "Market day: 3 traders came to
	 * the Market Square"; every trader is gone by dusk. The edict lifted, two again.
	 */
	//$ gametest_ticks_batch AREA '200' '"qaCivic1123cOpenGates"'
	@GameTest(template = AREA, timeoutTicks = 200, batch = "qaCivic1123cOpenGates")
	public void qaOpenGatesBringsOneMoreTraderAndTheNewsSaysSo(GameTestHelper helper) {
		BlockPos hall = market(helper);
		ServerLevel level = helper.getLevel();
		VillageHallBlockEntity entity = (VillageHallBlockEntity) level.getBlockEntity(hall);
		square(helper, new BlockPos(2, 2, 2));
		List<Component> seen = new ArrayList<>();
		ServerPlayer player = QaStageFourGameTests.player(helper, seen, GameType.SURVIVAL);
		player.moveTo(Vec3.atCenterOf(hall.east(3)));
		entity.setRank(VillageRanks.Rank.HAMLET);
		long day = marketDay(hall, 120);
		at(helper, day, 3000);
		entity.setEdicts(List.of(new Edicts.InForce(AliveWorkplace.id("open_gates").toString(), Chronicle.day(level) - Edicts.MIN_DAYS)));
		Leftovers.after(helper, () -> {
			entity.setEdicts(List.of());
			CivicEffects.forget();
		});
		CivicEffects.forget();
		VillageNeeds.forget();
		helper.assertTrue(MarketDays.traders(level, hall) == 3, "a Hamlet under Open Gates: " + MarketDays.traders(level, hall) + " traders");
		helper.assertTrue(MarketDays.tick(level, hall, entity), "no market on market morning");
		List<WanderingTrader> came = traders(helper, hall);
		helper.assertTrue(came.size() == 3, came.size() + " traders came under Open Gates");
		for (WanderingTrader t : came) {
			helper.assertTrue(t.getDespawnDelay() > 0 && 3000 + t.getDespawnDelay() <= Curfew.DUSK, "a trader stays until " + (3000 + t.getDespawnDelay()));
		}
		String village = VillageHalls.name(level, hall).getString();
		String news = "It's market day in " + village + ": traders have come to the Market Square";
		helper.assertTrue(seen.stream().anyMatch(c -> c.getString().equals(news) && c.getStyle().getColor() != null
			&& c.getStyle().getColor().equals(net.minecraft.network.chat.TextColor.fromLegacyFormat(net.minecraft.ChatFormatting.GOLD))),
			"the player in the village wasn't told '" + news + "' in gold: " + texts(seen));
		helper.assertTrue(entity.chronicle().stream().anyMatch(e -> e.kind() == Chronicle.Kind.MARKET
			&& e.text().getString().equals("Market day: 3 traders came to the Market Square")), "the chronicle: " + entity.chronicle());
		came.forEach(WanderingTrader::discard);

		entity.setEdicts(List.of());
		CivicEffects.forget();
		helper.assertTrue(MarketDays.traders(level, hall) == 2, "the edict lifted: " + MarketDays.traders(level, hall) + " traders");
		helper.succeed();
	}

	/**
	 * A Market Square finished far outside the village is not its square: no market on market morning, the day isn't
	 * spent, and nobody foretells one; the same square inside the village holds it that morning.
	 */
	//$ gametest_ticks_batch AREA '100' '"qaCivic1123cFarSquare"'
	@GameTest(template = AREA, timeoutTicks = 100, batch = "qaCivic1123cFarSquare")
	public void qaASquareOutsideTheVillageHoldsNoMarket(GameTestHelper helper) {
		BlockPos hall = market(helper);
		ServerLevel level = helper.getLevel();
		VillageHallBlockEntity entity = (VillageHallBlockEntity) level.getBlockEntity(hall);
		square(helper, new BlockPos(2 + 200, 2, 2 + 200));
		long day = marketDay(hall, 150);
		at(helper, day, 1500);
		helper.assertTrue(MarketDays.square(level, hall).isEmpty(), "a square 200 blocks off is the village's: " + MarketDays.square(level, hall));
		helper.assertFalse(MarketDays.tick(level, hall, entity), "a market at a square outside the village");
		helper.assertTrue(entity.lastMarketDay() != day && traders(helper, hall).isEmpty(), "the day was spent, or traders came");
		helper.assertTrue(MarketDays.foretold(level, hall, entity.lastMarketDay()) == -1, "a market foretold with no square in the village");
		square(helper, new BlockPos(2, 2, 2));
		helper.assertTrue(MarketDays.tick(level, hall, entity), "the square in the village held no market that morning");
		traders(helper, hall).forEach(WanderingTrader::discard);
		helper.succeed();
	}

	// --- Helpers -----------------------------------------------------------------------------------------------------

	private static BannerPatternLayers stripe(GameTestHelper helper, DyeColor color) {
		var patterns = helper.getLevel().registryAccess().lookupOrThrow(Registries.BANNER_PATTERN);
		return new BannerPatternLayers.Builder().add(patterns.getOrThrow(BannerPatterns.STRIPE_TOP), color)
			.add(patterns.getOrThrow(BannerPatterns.BORDER), DyeColor.BLACK).build();
	}

	/** A right-click on the top of {@code at} with {@code held} in the main hand, as the client sends it. */
	private static void use(ServerPlayer player, ServerLevel level, BlockPos at, ItemStack held) {
		player.setItemInHand(InteractionHand.MAIN_HAND, held);
		player.moveTo(Vec3.atCenterOf(at).add(1.5, 0, 0));
		player.gameMode.useItemOn(player, level, held, InteractionHand.MAIN_HAND,
			new BlockHitResult(Vec3.atCenterOf(at).relative(Direction.UP, 0.5), Direction.UP, at, false));
	}

	private static List<String> texts(List<Component> seen) {
		return seen.stream().map(Component::getString).toList();
	}

	/** A 3 x 3 grid with the stacks at the given slots: (stack, slot, stack, slot, ...). */
	private static List<ItemStack> grid(Object... at) {
		List<ItemStack> items = new ArrayList<>();
		for (int i = 0; i < 9; i++) {
			items.add(ItemStack.EMPTY);
		}
		for (int i = 0; i < at.length; i += 2) {
			items.set((Integer) at[i + 1], ((ItemStack) at[i]).copy());
		}
		return items;
	}

	private static ItemStack craft(ServerLevel level, List<ItemStack> items) {
		CraftingInput input = CraftingInput.of(3, 3, items);
		return level.getRecipeManager().getRecipeFor(RecipeType.CRAFTING, input, level)
			.map(r -> r.value().assemble(input, level.registryAccess())).orElse(ItemStack.EMPTY);
	}

	/** A bed with its head at (x, 2, 16), foot at z = 15. */
	private static void bed(GameTestHelper helper, int x) {
		helper.setBlock(new BlockPos(x, 2, 15), Blocks.RED_BED.defaultBlockState().setValue(BedBlock.FACING, Direction.SOUTH).setValue(BedBlock.PART, BedPart.FOOT));
		helper.setBlock(new BlockPos(x, 2, 16), Blocks.RED_BED.defaultBlockState().setValue(BedBlock.FACING, Direction.SOUTH).setValue(BedBlock.PART, BedPart.HEAD));
	}

	/** A village of radius 16: its hall, two beds and a cradle by each; nothing of it left afterwards. */
	private static void nursery(GameTestHelper helper) {
		Leftovers.clear(helper);
		Leftovers.halls(helper);
		int radius = VillageHalls.RADIUS;
		boolean cradles = Cradles.ENABLED;
		long time = helper.getLevel().getDayTime();
		VillageHalls.RADIUS = 16;
		Cradles.ENABLED = true;
		bed(helper, 5);
		bed(helper, 14);
		helper.setBlock(CRADLE_A, ModBlocks.CRADLE);
		helper.setBlock(CRADLE_B, ModBlocks.CRADLE);
		helper.setBlock(HALL, ModBlocks.VILLAGE_HALL);
		Leftovers.after(helper, () -> {
			ServerLevel level = helper.getLevel();
			if (level.getBlockEntity(helper.absolutePos(HALL)) instanceof VillageHallBlockEntity hall) {
				hall.setEdicts(List.of());
				hall.setReforms(List.of());
			}
			for (BlockPos p : List.of(HALL, CRADLE_A, CRADLE_B)) {
				level.setBlockAndUpdate(helper.absolutePos(p), Blocks.AIR.defaultBlockState());
			}
			level.setDayTime(time);
			VillageHalls.RADIUS = radius;
			Cradles.ENABLED = cradles;
			VillageHalls.forgetNearest();
			VillageNeeds.forget();
			CivicEffects.forget();
			Moods.forget();
		});
	}

	private static void ready(GameTestHelper helper) {
		VillageHallBlockEntity hall = (VillageHallBlockEntity) helper.getLevel().getBlockEntity(helper.absolutePos(HALL));
		hall.setRank(VillageRanks.Rank.HAMLET);
		hall.setEdicts(List.of());
		hall.setReforms(List.of());
		VillageNeeds.forget();
		CivicEffects.forget();
		Moods.forget();
	}

	/** A newborn standing still. */
	private static Villager child(GameTestHelper helper, BlockPos at) {
		Villager child = helper.spawn(EntityType.VILLAGER, at);
		child.setAge(-24000);
		child.setNoAi(true);
		return child;
	}

	/** 2000 random ticks to the sugar cane from a fixed random: how often it grew (its age went up or it grew a block). */
	private static int cane(GameTestHelper helper, BlockPos rel) {
		ServerLevel level = helper.getLevel();
		BlockPos pos = helper.absolutePos(rel);
		level.setBlock(pos, Blocks.SUGAR_CANE.defaultBlockState(), Block.UPDATE_CLIENTS);
		level.setBlock(pos.above(), Blocks.AIR.defaultBlockState(), Block.UPDATE_CLIENTS);
		RandomSource random = RandomSource.create(1123L);
		int grown = 0;
		for (int i = 0; i < 2000; i++) {
			BlockState before = level.getBlockState(pos);
			helper.assertTrue(before.is(Blocks.SUGAR_CANE), "the sugar cane is gone: " + before);
			int age = before.getValue(SugarCaneBlock.AGE);
			before.randomTick(level, pos, random);
			BlockState after = level.getBlockState(pos);
			if (after.getValue(SugarCaneBlock.AGE) > age) {
				grown += after.getValue(SugarCaneBlock.AGE) - age;
			}
			if (level.getBlockState(pos.above()).is(Blocks.SUGAR_CANE)) {
				grown++;
				level.setBlock(pos.above(), Blocks.AIR.defaultBlockState(), Block.UPDATE_CLIENTS);
			}
		}
		return grown;
	}

	/** 2000 random ticks to the crop from a fixed random: the stages it grew (ripe, it's set back to seed). */
	private static int crop(GameTestHelper helper, BlockPos rel) {
		ServerLevel level = helper.getLevel();
		BlockPos pos = helper.absolutePos(rel);
		CropBlock crop = (CropBlock) level.getBlockState(pos).getBlock();
		level.setBlock(pos, crop.getStateForAge(0), Block.UPDATE_CLIENTS);
		RandomSource random = RandomSource.create(1123L);
		int grown = 0;
		for (int i = 0; i < 2000; i++) {
			BlockState before = level.getBlockState(pos);
			helper.assertTrue(before.is(crop), "the crop is gone: " + before);
			int age = crop.getAge(before);
			before.randomTick(level, pos, random);
			int now = crop.getAge(level.getBlockState(pos));
			grown += now - age;
			if (now >= crop.getMaxAge()) {
				level.setBlock(pos, crop.getStateForAge(0), Block.UPDATE_CLIENTS);
			}
		}
		return grown;
	}

	/** A village with markets on, its hall at {@link #MARKET_HALL}, radius 32; everything put back after. */
	private static BlockPos market(GameTestHelper helper) {
		Leftovers.clear(helper);
		Leftovers.halls(helper);
		int radius = VillageHalls.RADIUS;
		boolean enabled = MarketDays.ENABLED;
		long dayTime = helper.getLevel().getDayTime();
		VillageHalls.RADIUS = 32;
		MarketDays.ENABLED = true;
		helper.setBlock(MARKET_HALL, ModBlocks.VILLAGE_HALL);
		BlockPos hall = helper.absolutePos(MARKET_HALL);
		Leftovers.after(helper, () -> {
			helper.getLevel().removeBlock(hall, false);
			VillageHalls.RADIUS = radius;
			MarketDays.ENABLED = enabled;
			helper.getLevel().setDayTime(dayTime);
			VillageHalls.forgetNearest();
			traders(helper, hall).forEach(WanderingTrader::discard);
		});
		return hall;
	}

	/** A finished Market Square placed at {@code at} (relative to the test), forgotten after the test. */
	private static void square(GameTestHelper helper, BlockPos at) {
		BuildSiteManager sites = BuildSiteManager.get(helper.getLevel());
		BlueprintData.Placement placement = new BlueprintData.Placement(helper.getLevel().dimension().location(),
			helper.absolutePos(at), Rotation.NONE, Mirror.NONE);
		sites.recordFinished(StarterBlueprints.MARKET_SQUARE.id(), placement, UUID.randomUUID());
		Leftovers.after(helper, () -> sites.forgetFinished(placement));
	}

	private static long marketDay(BlockPos hall, long from) {
		long day = from;
		while (!MarketDays.isMarketDay(hall, day)) {
			day++;
		}
		return day;
	}

	private static void at(GameTestHelper helper, long day, int time) {
		helper.getLevel().setDayTime((day - 1) * 24000L + time);
	}

	private static List<WanderingTrader> traders(GameTestHelper helper, BlockPos hall) {
		return helper.getLevel().getEntitiesOfClass(WanderingTrader.class, new AABB(hall).inflate(40), WanderingTrader::isAlive);
	}
}
