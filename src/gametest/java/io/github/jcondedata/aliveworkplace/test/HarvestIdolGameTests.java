package io.github.jcondedata.aliveworkplace.test;

import io.github.jcondedata.aliveworkplace.hall.HarvestIdols;
import io.github.jcondedata.aliveworkplace.hall.Seasons;
import io.github.jcondedata.aliveworkplace.hall.VillageHallScreen;
import io.github.jcondedata.aliveworkplace.hall.VillageHalls;
import io.github.jcondedata.aliveworkplace.hall.VillageNeeds;
import io.github.jcondedata.aliveworkplace.registry.ModBlocks;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.ItemLore;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CropBlock;
import net.minecraft.world.level.block.FarmBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;

/**
 * Seasons and the Harvest Idol (ROADMAP 30.14). Wheat on wet farmland takes {@link #TICKS} random ticks in one go
 * (so the world's own random ticks can't add any) from a fixed {@link RandomSource}, and the stages it grows are
 * counted (a ripe crop is set back to seed and goes on). Autumn and summer are set on the clock and the clock put back
 * afterwards. Each test has its own batch and takes away the idols it put up (some stand outside the area).
 */
public class HarvestIdolGameTests implements FabricGameTest {
	private static final String AREA = "aliveworkplace_test:big_area";
	private static final int TICKS = 2000;
	private static final long SEED = 3014L;
	/** The wheat, on a 3 x 3 of wet farmland. */
	private static final BlockPos CROP = new BlockPos(4, 3, 4);
	/** An idol beside the field. */
	private static final BlockPos IDOL = new BlockPos(8, 2, 4);
	private static final BlockPos SECOND_IDOL = new BlockPos(8, 2, 8);

	/** In autumn, wheat by an idol grows 25% (within 5%) more over 2000 random ticks than the same wheat without. */
	//$ gametest_ticks_batch AREA '100' '"harvestIdolAutumn"'
	@GameTest(template = AREA, timeoutTicks = 100, batch = "harvestIdolAutumn")
	public void wheatByAnIdolGrowsAQuarterFasterInAutumn(GameTestHelper helper) {
		field(helper);
		helper.runAfterDelay(5, () -> {
			ServerLevel level = helper.getLevel();
			season(helper, Seasons.Season.AUTUMN);
			helper.assertTrue(HarvestIdols.harvestSeason(level), "not harvest season on day " + Seasons.today(level));
			helper.assertTrue(level.getRawBrightness(helper.absolutePos(CROP), 0) >= 9, "too dark for wheat to grow");
			int without = grow(helper);
			idol(helper, IDOL);
			int with = grow(helper);
			double ratio = (double) with / without;
			helper.assertTrue(ratio >= 1.20 && ratio <= 1.30, "with an idol " + with + " stages, without " + without + ": x" + ratio);
			helper.succeed();
		});
	}

	/** Nothing extra in summer, from an idol 33 blocks off, or from a second idol; an idol 32 blocks off still counts. */
	//$ gametest_ticks_batch AREA '100' '"harvestIdolNothingExtra"'
	@GameTest(template = AREA, timeoutTicks = 100, batch = "harvestIdolNothingExtra")
	public void nothingExtraInSummerAt33BlocksOrFromASecondIdol(GameTestHelper helper) {
		field(helper);
		helper.runAfterDelay(5, () -> {
			ServerLevel level = helper.getLevel();
			BlockPos crop = helper.absolutePos(CROP);
			season(helper, Seasons.Season.AUTUMN);
			int without = grow(helper);

			// 33 blocks away (outside the test area): too far. 32: in reach.
			BlockPos far = crop.east(33);
			place(helper, far);
			helper.assertTrue(!HarvestIdols.near(level, crop), "an idol 33 blocks off counts");
			helper.assertTrue(grow(helper) == without, "an idol 33 blocks off made the wheat grow more");
			level.setBlockAndUpdate(far, Blocks.AIR.defaultBlockState());
			place(helper, crop.east(32));
			helper.assertTrue(HarvestIdols.near(level, crop), "an idol 32 blocks off doesn't count");
			level.setBlockAndUpdate(crop.east(32), Blocks.AIR.defaultBlockState());
			helper.assertTrue(!HarvestIdols.near(level, crop), "the broken idol still counts");

			// One idol, then two: the same growth (idols don't stack).
			idol(helper, IDOL);
			int one = grow(helper);
			idol(helper, SECOND_IDOL);
			int two = grow(helper);
			helper.assertTrue(one > without && two == one, "one idol " + one + " stages, two " + two + ", none " + without);

			// Summer: idols or not, the same.
			season(helper, Seasons.Season.SUMMER);
			helper.assertTrue(!HarvestIdols.harvestSeason(level), "harvest season in summer");
			helper.assertTrue(grow(helper) == without, "the idols made the wheat grow more in summer");
			helper.succeed();
		});
	}

	/** An idol's chunk unloading takes it out of the set, and loading again (the block entity from its save) puts it back. */
	//$ gametest_ticks_batch AREA '100' '"harvestIdolReload"'
	@GameTest(template = AREA, timeoutTicks = 100, batch = "harvestIdolReload")
	public void theIdolsAreFoundAgainAfterAReload(GameTestHelper helper) {
		field(helper);
		helper.runAfterDelay(5, () -> {
			ServerLevel level = helper.getLevel();
			BlockPos crop = helper.absolutePos(CROP);
			BlockPos idol = helper.absolutePos(IDOL);
			season(helper, Seasons.Season.AUTUMN);
			int without = grow(helper);
			idol(helper, IDOL);
			helper.assertTrue(HarvestIdols.idols(level).contains(idol), "a placed idol isn't in the set");
			int with = grow(helper);

			// Saved and unloaded: the chunk saves the block entity and removes it, and the server forgets everything.
			BlockEntity entity = level.getBlockEntity(idol);
			helper.assertTrue(entity != null && entity.getType() == ModBlocks.HARVEST_IDOL_ENTITY, "no idol block entity: " + entity);
			CompoundTag saved = entity.saveWithFullMetadata(level.registryAccess());
			entity.setRemoved();
			helper.assertTrue(!HarvestIdols.idols(level).contains(idol) && !HarvestIdols.near(level, crop), "an unloaded idol still counts");
			HarvestIdols.forget();
			helper.assertTrue(grow(helper) == without, "the wheat grew more with the idol unloaded");

			// Loaded again: the chunk makes the block entity from its save and sets it in place.
			BlockState state = level.getBlockState(idol);
			BlockEntity loaded = BlockEntity.loadStatic(idol, state, saved, level.registryAccess());
			helper.assertTrue(loaded != null, "the idol's block entity didn't load");
			level.getChunkAt(idol).setBlockEntity(loaded);
			helper.assertTrue(HarvestIdols.idols(level).contains(idol), "the reloaded idol isn't found again: " + HarvestIdols.idols(level));
			helper.assertTrue(grow(helper) == with, "after the reload the wheat grows differently");

			// Broken: gone from the set.
			level.setBlockAndUpdate(idol, Blocks.AIR.defaultBlockState());
			helper.assertTrue(!HarvestIdols.idols(level).contains(idol), "a broken idol is still in the set");
			helper.succeed();
		});
	}

	/** The calendar's seasons on days 1, 17 and 32, at the default 16 days and at 8; the season line the hall shows. */
	//$ gametest_ticks_batch AREA '100' '"harvestIdolSeasons"'
	@GameTest(template = AREA, timeoutTicks = 100, batch = "harvestIdolSeasons")
	public void theSeasonIsRightOnDays1And17And32(GameTestHelper helper) {
		check(helper, Seasons.date(1, 16), Seasons.Season.SPRING, 1);
		check(helper, Seasons.date(17, 16), Seasons.Season.SUMMER, 1);
		check(helper, Seasons.date(32, 16), Seasons.Season.SUMMER, 16);
		check(helper, Seasons.date(1, 8), Seasons.Season.SPRING, 1);
		check(helper, Seasons.date(17, 8), Seasons.Season.AUTUMN, 1);
		check(helper, Seasons.date(32, 8), Seasons.Season.WINTER, 8);
		helper.assertTrue(Seasons.date(17, 8).season().harvest() && !Seasons.date(17, 16).season().harvest(), "autumn alone is harvest season");

		String autumn = Seasons.seasonLine(Seasons.date(19, 8)).getString();
		helper.assertTrue(autumn.equals("Autumn: harvest season, day 3 of 8"), "autumn line: " + autumn);
		String spring = Seasons.seasonLine(Seasons.date(3, 16)).getString();
		helper.assertTrue(spring.equals("Spring, day 3 of 16"), "spring line: " + spring);

		// The hall's festival icon names today's season and its day.
		Leftovers.clear(helper);
		int radius = VillageHalls.RADIUS;
		VillageHalls.RADIUS = 16;
		BlockPos hallAt = new BlockPos(11, 2, 11);
		helper.setBlock(hallAt, ModBlocks.VILLAGE_HALL);
		Leftovers.after(helper, () -> {
			helper.getLevel().setBlockAndUpdate(helper.absolutePos(hallAt), Blocks.AIR.defaultBlockState());
			VillageHalls.RADIUS = radius;
			VillageNeeds.forget();
			Leftovers.players(helper);
		});
		season(helper, Seasons.Season.AUTUMN);
		ServerPlayer player = helper.makeMockServerPlayerInLevel();
		player.setGameMode(GameType.SURVIVAL);
		BlockPos at = helper.absolutePos(new BlockPos(9, 2, 9));
		player.moveTo(at.getX() + 0.5, at.getY(), at.getZ() + 0.5);
		ItemLore lore = VillageHallScreen.forTest(player, helper.absolutePos(hallAt)).icon(VillageHallScreen.FESTIVAL).get(DataComponents.LORE);
		List<String> lines = lore == null ? List.of() : lore.lines().stream().map(Component::getString).toList();
		String expected = "Autumn: harvest season, day 1 of " + Seasons.DAYS;
		helper.assertTrue(lines.contains(expected), "the festival icon doesn't say \"" + expected + "\": " + lines);
		helper.succeed();
	}

	/**
	 * The crop tag holds every vanilla crop the spec names; golden sparkles rise from an idol now and then in harvest
	 * season and never in summer; the recipe; the names a player reads.
	 */
	//$ gametest_ticks_batch AREA '100' '"harvestIdolBits"'
	@GameTest(template = AREA, timeoutTicks = 100, batch = "harvestIdolBits")
	public void cropsSparklesRecipeAndNames(GameTestHelper helper) {
		for (Block crop : List.of(Blocks.WHEAT, Blocks.CARROTS, Blocks.POTATOES, Blocks.BEETROOTS, Blocks.MELON_STEM, Blocks.PUMPKIN_STEM,
			Blocks.SWEET_BERRY_BUSH, Blocks.COCOA, Blocks.NETHER_WART, Blocks.TORCHFLOWER_CROP, Blocks.PITCHER_CROP)) {
			helper.assertTrue(crop.defaultBlockState().is(HarvestIdols.CROPS), crop + " isn't an idol crop");
		}
		helper.assertTrue(!Blocks.OAK_SAPLING.defaultBlockState().is(HarvestIdols.CROPS) && !Blocks.GRASS_BLOCK.defaultBlockState().is(HarvestIdols.CROPS),
			"saplings or grass are idol crops");

		ServerLevel level = helper.getLevel();
		BlockPos idol = helper.absolutePos(IDOL);
		season(helper, Seasons.Season.AUTUMN);
		RandomSource random = RandomSource.create(SEED);
		int sparkled = 0;
		for (int i = 0; i < 30; i++) {
			sparkled += HarvestIdols.sparkle(level, idol, random) ? 1 : 0;
		}
		helper.assertTrue(sparkled > 3 && sparkled < 20, "sparkled " + sparkled + " times in 30 looks in autumn (about one in three)");
		season(helper, Seasons.Season.SUMMER);
		for (int i = 0; i < 30; i++) {
			helper.assertTrue(!HarvestIdols.sparkle(level, idol, random), "sparkles in summer");
		}

		// A hay bale, three wheat, a stick and a gold ingot make one.
		CraftingInput input = CraftingInput.of(3, 3, List.of(
			new ItemStack(Items.WHEAT), new ItemStack(Items.WHEAT), new ItemStack(Items.WHEAT),
			ItemStack.EMPTY, new ItemStack(Items.HAY_BLOCK), new ItemStack(Items.GOLD_INGOT),
			ItemStack.EMPTY, new ItemStack(Items.STICK), ItemStack.EMPTY));
		ItemStack made = level.getRecipeManager().getRecipeFor(RecipeType.CRAFTING, input, level)
			.map(r -> r.value().assemble(input, level.registryAccess())).orElse(ItemStack.EMPTY);
		helper.assertTrue(made.is(ModBlocks.HARVEST_IDOL.asItem()), "the recipe made " + made);

		helper.assertTrue(ModBlocks.HARVEST_IDOL.getName().getString().equals("Harvest Idol"), "name: " + ModBlocks.HARVEST_IDOL.getName().getString());
		helper.assertTrue(Component.translatable("aliveworkplace.config.harvestIdols").getString().equals("Harvest Idols"), "config name");
		String tooltip = Component.translatable("aliveworkplace.config.harvestIdols.tooltip").getString();
		helper.assertTrue(tooltip.startsWith("In harvest season (autumn on the village calendar) the crops within 32 blocks of a Harvest Idol grow 25% faster"),
			"config tooltip: " + tooltip);
		helper.succeed();
	}

	/** With {@code harvestIdols} off an idol is an ornament: no extra growth in autumn and no sparkles. */
	//$ gametest_ticks_batch AREA '100' '"harvestIdolOff"'
	@GameTest(template = AREA, timeoutTicks = 100, batch = "harvestIdolOff")
	public void harvestIdolsOffMakesThemOrnaments(GameTestHelper helper) {
		field(helper);
		Leftovers.after(helper, () -> HarvestIdols.ENABLED = true);
		helper.runAfterDelay(5, () -> {
			ServerLevel level = helper.getLevel();
			season(helper, Seasons.Season.AUTUMN);
			int without = grow(helper);
			idol(helper, IDOL);
			HarvestIdols.ENABLED = false;
			helper.assertTrue(grow(helper) == without, "off: the idol made the wheat grow more");
			RandomSource random = RandomSource.create(SEED);
			for (int i = 0; i < 30; i++) {
				helper.assertTrue(!HarvestIdols.sparkle(level, helper.absolutePos(IDOL), random), "off: sparkles");
			}
			HarvestIdols.ENABLED = true;
			helper.assertTrue(grow(helper) > without, "on again: no extra growth");
			helper.succeed();
		});
	}

	// --- Helpers ---------------------------------------------------------------------------------------------------

	/** Wheat on a 3 x 3 of wet farmland; afterwards the clock is put back and the idols taken away. */
	private static void field(GameTestHelper helper) {
		Leftovers.clear(helper);
		long time = helper.getLevel().getDayTime();
		for (int dx = -1; dx <= 1; dx++) {
			for (int dz = -1; dz <= 1; dz++) {
				helper.setBlock(CROP.offset(dx, -1, dz), Blocks.FARMLAND.defaultBlockState().setValue(FarmBlock.MOISTURE, FarmBlock.MAX_MOISTURE));
			}
		}
		helper.setBlock(CROP, Blocks.WHEAT);
		Leftovers.after(helper, () -> {
			ServerLevel level = helper.getLevel();
			level.setDayTime(time);
			for (BlockPos p : List.of(IDOL, SECOND_IDOL)) {
				level.setBlockAndUpdate(helper.absolutePos(p), Blocks.AIR.defaultBlockState());
			}
			BlockPos crop = helper.absolutePos(CROP);
			for (BlockPos p : List.of(crop.east(32), crop.east(33))) {
				if (level.getBlockState(p).is(ModBlocks.HARVEST_IDOL)) {
					level.setBlockAndUpdate(p, Blocks.AIR.defaultBlockState());
				}
			}
		});
	}

	/** An idol at {@code at} in the test area. */
	private static void idol(GameTestHelper helper, BlockPos at) {
		helper.setBlock(at, ModBlocks.HARVEST_IDOL);
	}

	/** An idol at the absolute place {@code at} (maybe outside the area). */
	private static void place(GameTestHelper helper, BlockPos at) {
		helper.getLevel().setBlockAndUpdate(at, ModBlocks.HARVEST_IDOL.defaultBlockState());
	}

	/** The first day of {@code season} on the overworld's clock (at noon). */
	private static void season(GameTestHelper helper, Seasons.Season season) {
		long day = (long) season.ordinal() * Seasons.DAYS + 1;
		helper.getLevel().setDayTime((day - 1) * VillageNeeds.DAY + 6000);
	}

	/**
	 * {@link #TICKS} random ticks to the wheat, from the same fixed random each time; the stages it grew (ripe wheat is set
	 * back to seed and goes on). It starts from seed.
	 */
	private static int grow(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		BlockPos pos = helper.absolutePos(CROP);
		CropBlock wheat = (CropBlock) Blocks.WHEAT;
		level.setBlock(pos, wheat.getStateForAge(0), Block.UPDATE_CLIENTS);
		RandomSource random = RandomSource.create(SEED);
		int grown = 0;
		for (int i = 0; i < TICKS; i++) {
			BlockState before = level.getBlockState(pos);
			helper.assertTrue(before.is(Blocks.WHEAT), "the wheat is gone: " + before);
			int age = wheat.getAge(before);
			before.randomTick(level, pos, random);
			int now = wheat.getAge(level.getBlockState(pos));
			grown += now - age;
			if (now >= wheat.getMaxAge()) {
				level.setBlock(pos, wheat.getStateForAge(0), Block.UPDATE_CLIENTS);
			}
		}
		return grown;
	}

	private static void check(GameTestHelper helper, Seasons.Date date, Seasons.Season season, int dayOfSeason) {
		helper.assertTrue(date.season() == season && date.dayOfSeason() == dayOfSeason,
			"day " + date.day() + " (" + date.length() + "-day seasons): " + date.season() + " day " + date.dayOfSeason() + ", expected " + season + " day " + dayOfSeason);
	}
}
