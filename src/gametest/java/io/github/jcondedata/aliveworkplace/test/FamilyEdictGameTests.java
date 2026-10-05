package io.github.jcondedata.aliveworkplace.test;

import io.github.jcondedata.aliveworkplace.AliveWorkplace;
import io.github.jcondedata.aliveworkplace.hall.CivicEffects;
import io.github.jcondedata.aliveworkplace.hall.EdictBook;
import io.github.jcondedata.aliveworkplace.hall.Edicts;
import io.github.jcondedata.aliveworkplace.hall.Reforms;
import io.github.jcondedata.aliveworkplace.hall.VillageGrowth;
import io.github.jcondedata.aliveworkplace.hall.VillageHallBlockEntity;
import io.github.jcondedata.aliveworkplace.hall.VillageHallScreen;
import io.github.jcondedata.aliveworkplace.hall.VillageHalls;
import io.github.jcondedata.aliveworkplace.hall.VillageNeeds;
import io.github.jcondedata.aliveworkplace.hall.VillageQuests;
import io.github.jcondedata.aliveworkplace.hall.VillageRanks;
import io.github.jcondedata.aliveworkplace.people.Moods;
import io.github.jcondedata.aliveworkplace.people.Sickness;
import io.github.jcondedata.aliveworkplace.registry.ModAttachments;
import io.github.jcondedata.aliveworkplace.registry.ModBlocks;
import io.github.jcondedata.aliveworkplace.work.ChoiceMenu;
import java.util.ArrayList;
import java.util.List;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponents;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.ItemLore;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.properties.BedPart;

/**
 * Free Bread and Large Families with their reforms (ROADMAP 30.6): the effects {@code food_use}, {@code births} and
 * {@code sickness}, each number with and without the reform: meals taken for 10 eaten (13, then 10), the "free bread"
 * mood, the gap between births (a day, then half), the meals a baby needs and the family eats (16 and 8, then 24 and 12,
 * reformed 16 and 8 again), and the daily chance of falling ill (half again, reformed as usual). Nothing here is random.
 */
public class FamilyEdictGameTests implements FabricGameTest {
	private static final String AREA = "aliveworkplace_test:big_area";
	private static final BlockPos HALL = new BlockPos(11, 2, 11);
	private static final BlockPos CHEST = new BlockPos(17, 2, 19);
	private static final ResourceLocation FREE_BREAD = AliveWorkplace.id("free_bread");
	private static final ResourceLocation LARGE_FAMILIES = AliveWorkplace.id("large_families");

	/** Both load with their texts, icons, boost, cost and a three-step reform that takes the cost away. */
	//$ gametest 'FabricGameTest.EMPTY_STRUCTURE'
	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
	public void freeBreadAndLargeFamiliesLoadWithTheirReforms(GameTestHelper helper) {
		Edicts.Edict bread = Edicts.get(FREE_BREAD).orElse(null);
		helper.assertTrue(bread != null, "Free Bread didn't load: " + Edicts.all());
		helper.assertTrue(bread.name().getString().equals("Free Bread") && bread.icon().equals(ResourceLocation.withDefaultNamespace("bread")),
			"Free Bread: " + bread.name().getString() + ", " + bread.icon());
		helper.assertTrue(bread.description().getString().equals("Everyone fed in the last day is 10 happier; the village eats 30% more."),
			"description: " + bread.description().getString());
		helper.assertTrue(bread.boost().size() == 1 && bread.boost().get(0) instanceof CivicEffects.Mood mood && mood.points() == 10
			&& mood.when() == CivicEffects.When.FED_TODAY && mood.reason().getString().equals("free bread"), "boost: " + bread.boost());
		helper.assertTrue(bread.cost().size() == 1 && bread.cost().get(0) instanceof CivicEffects.FoodUse food && food.percent() == 30, "cost: " + bread.cost());
		Reforms.Reform granary = bread.reform().orElse(null);
		helper.assertTrue(granary != null && granary.name().getString().equals("The Common Granary") && granary.effects().isEmpty(), "reform: " + granary);
		steps(helper, granary, "minecraft:wheat", 512, 4, "minecraft:hay_block", 128, 5, "minecraft:barrel", 48, 3);

		Edicts.Edict families = Edicts.get(LARGE_FAMILIES).orElse(null);
		helper.assertTrue(families != null, "Large Families didn't load: " + Edicts.all());
		helper.assertTrue(families.name().getString().equals("Large Families") && families.icon().equals(ResourceLocation.withDefaultNamespace("white_bed")),
			"Large Families: " + families.name().getString() + ", " + families.icon());
		helper.assertTrue(families.boost().size() == 1 && families.boost().get(0) instanceof CivicEffects.Births b && b.perDay() == 2
			&& b.foodNeeded().isEmpty() && b.familyMeals().isEmpty(), "boost: " + families.boost());
		helper.assertTrue(families.cost().size() == 2 && families.cost().get(0) instanceof CivicEffects.Births c && c.perDay() == 1
			&& c.foodNeeded().orElse(0) == 24 && c.familyMeals().orElse(0) == 12
			&& families.cost().get(1) instanceof CivicEffects.Illness ill && ill.percent() == 50, "cost: " + families.cost());
		Reforms.Reform midwives = families.reform().orElse(null);
		helper.assertTrue(midwives != null && midwives.name().getString().equals("The Midwives") && midwives.effects().isEmpty(), "reform: " + midwives);
		steps(helper, midwives, "minecraft:honey_bottle", 48, 4, "minecraft:white_wool", 192, 3, "minecraft:golden_carrot", 64, 5);

		// Summed: babies a day add what each has over 1, the food named is the highest, percents add.
		CivicEffects.Sum sum = new CivicEffects.Sum(List.of(
			new CivicEffects.Active(new CivicEffects.Births(2, java.util.Optional.empty(), java.util.Optional.empty(), List.of()), Component.empty()),
			new CivicEffects.Active(new CivicEffects.Births(2, java.util.Optional.of(20), java.util.Optional.empty(), List.of()), Component.empty()),
			new CivicEffects.Active(new CivicEffects.Births(1, java.util.Optional.of(24), java.util.Optional.of(12), List.of()), Component.empty())));
		helper.assertTrue(sum.birthsPerDay() == 3 && sum.foodNeeded(16) == 24 && sum.familyMeals(8) == 12, "summed births: " + sum.birthsPerDay()
			+ ", " + sum.foodNeeded(16) + ", " + sum.familyMeals(8));
		helper.assertTrue(CivicEffects.Sum.EMPTY.birthsPerDay() == 1 && CivicEffects.Sum.EMPTY.foodNeeded(16) == 16
			&& CivicEffects.Sum.EMPTY.familyMeals(8) == 8, "no edicts: the usual");
		helper.succeed();
	}

	private static void steps(GameTestHelper helper, Reforms.Reform reform, Object... want) {
		helper.assertTrue(reform.steps().size() == 3, "steps: " + reform.steps());
		for (int i = 0; i < 3; i++) {
			Reforms.Step s = reform.steps().get(i);
			helper.assertTrue(s.kind() == VillageQuests.Kind.BRING && s.item().equals(want[3 * i]) && s.count() == (int) want[3 * i + 1]
				&& s.reward() == (int) want[3 * i + 2], "step " + (i + 1) + ": " + s);
		}
	}

	/**
	 * Free Bread: ten villagers eat ten meals and the hall takes three more (0.3 a meal, a whole one each time it reaches
	 * 1); seven eat and nine go, 0.1 kept over a save; everyone fed is 10 happier ("free bread"), the hungry aren't.
	 * Reformed: ten eaten, ten taken, the mood stays. Edicts off: no mood, no extra.
	 */
	//$ gametest_ticks_batch AREA '100' '"freeBreadMeals"'
	@GameTest(template = AREA, timeoutTicks = 100, batch = "freeBreadMeals")
	public void freeBreadTakesThirteenMealsForTenUntilReformed(GameTestHelper helper) {
		Leftovers.clear(helper);
		village(helper);
		moodsOn(helper);
		boolean sickness = Sickness.ENABLED;
		Sickness.ENABLED = false; // nobody falls ill between two looks at a mood
		Leftovers.after(helper, () -> Sickness.ENABLED = sickness);
		helper.setBlock(new BlockPos(17, 2, 17), ModBlocks.STOREHOUSE);
		helper.setBlock(CHEST, Blocks.CHEST);
		List<Villager> villagers = new ArrayList<>();
		for (int i = 0; i < 10; i++) {
			Villager v = helper.spawn(EntityType.VILLAGER, new BlockPos(2 + i, 2, 4));
			v.setNoAi(true);
			villagers.add(v);
		}
		helper.setBlock(HALL, ModBlocks.VILLAGE_HALL);
		helper.runAfterDelay(5, () -> {
			ServerLevel level = helper.getLevel();
			BlockPos hall = helper.absolutePos(HALL);
			VillageHallBlockEntity entity = ready(helper);
			Container chest = helper.getBlockEntity(CHEST);

			helper.assertTrue(taken(level, hall, chest, villagers) == 10, "no edict: " + taken(level, hall, chest, villagers));
			int fed = Moods.work(level, villagers.get(0)).score();
			helper.assertTrue(Moods.work(level, villagers.get(0)).good().stream().noneMatch(c -> c.getString().equals("free bread")), "free bread before the edict");

			helper.assertTrue(Edicts.proclaim(level, hall, null, Edicts.get(FREE_BREAD).orElseThrow()).done(), "proclaimed");
			Moods.forget();
			Moods.Mood mood = Moods.work(level, villagers.get(0));
			helper.assertTrue(mood.score() == fed + 10 && mood.good().stream().anyMatch(c -> c.getString().equals("free bread")),
				"fed: " + fed + " -> " + mood.score() + " " + mood.good());
			ModAttachments.LAST_MEAL.set(villagers.get(1), level.getGameTime() - 3 * VillageNeeds.DAY);
			helper.assertTrue(Moods.work(level, villagers.get(1)).good().stream().noneMatch(c -> c.getString().equals("free bread")), "the hungry get free bread");

			int all = taken(level, hall, chest, villagers);
			helper.assertTrue(all == 13, "10 eaten under Free Bread: " + all + " taken");
			helper.assertTrue(entity.extraMeals() == 0, "3.0 extra, nothing left over: " + entity.extraMeals());
			int seven = taken(level, hall, chest, villagers.subList(0, 7));
			helper.assertTrue(seven == 9 && entity.extraMeals() == 10, "7 eaten: " + seven + " taken, " + entity.extraMeals() + " hundredths kept");

			// Kept over a save; a hall saved before has none.
			CompoundTag tag = entity.saveWithFullMetadata(level.registryAccess());
			VillageHallBlockEntity copy = (VillageHallBlockEntity) BlockEntity.loadStatic(hall, level.getBlockState(hall), tag, level.registryAccess());
			helper.assertTrue(copy != null && copy.extraMeals() == 10, "reloaded: " + (copy == null ? null : copy.extraMeals()));
			tag.remove("extraMeals");
			VillageHallBlockEntity old = (VillageHallBlockEntity) BlockEntity.loadStatic(hall, level.getBlockState(hall), tag, level.registryAccess());
			helper.assertTrue(old != null && old.extraMeals() == 0, "an old hall: " + (old == null ? null : old.extraMeals()));

			// Reformed: The Common Granary keeps the bread line, the mood stays.
			entity.setReforms(List.of(new Reforms.Progress(FREE_BREAD.toString(), 3, true, 0)));
			int reformed = taken(level, hall, chest, villagers);
			helper.assertTrue(reformed == 10 && entity.extraMeals() == 10, "reformed: " + reformed + " taken, " + entity.extraMeals());
			Moods.forget();
			helper.assertTrue(Moods.work(level, villagers.get(0)).good().stream().anyMatch(c -> c.getString().equals("free bread")), "reformed: the mood went");

			// Edicts off: neither.
			entity.setReforms(List.of());
			try {
				Edicts.setEnabled(false);
				Moods.forget();
				helper.assertTrue(Moods.work(level, villagers.get(0)).good().stream().noneMatch(c -> c.getString().equals("free bread")), "off: free bread");
				helper.assertTrue(taken(level, hall, chest, villagers) == 10, "off: extra meals taken");
			} finally {
				Edicts.setEnabled(true);
			}
			helper.succeed();
		});
	}

	/** Makes {@code eaters} hungry, fills the store with 64 bread and runs the hall's round; returns the meals it took. */
	private static int taken(ServerLevel level, BlockPos hall, Container chest, List<Villager> eaters) {
		chest.setItem(0, new ItemStack(Items.BREAD, 64));
		for (Villager v : eaters) {
			ModAttachments.LAST_MEAL.set(v, level.getGameTime() - 3 * VillageNeeds.DAY);
		}
		VillageNeeds.check(level, hall);
		return 64 - chest.countItem(Items.BREAD);
	}

	/**
	 * Large Families: a day between births becomes half a day, so two babies are born in one day; a baby needs 24 meals
	 * (23 aren't enough) and the family eats 12. Reformed (The Midwives): still two a day, with 16 needed and 8 eaten.
	 */
	//$ gametest_ticks_batch AREA '100' '"largeFamiliesBirths"'
	@GameTest(template = AREA, timeoutTicks = 100, batch = "largeFamiliesBirths")
	public void largeFamiliesHaveTwoBabiesADayOnMoreFood(GameTestHelper helper) {
		Leftovers.clear(helper);
		village(helper);
		helper.setBlock(new BlockPos(17, 2, 17), ModBlocks.STOREHOUSE);
		helper.setBlock(CHEST, Blocks.CHEST);
		helper.spawn(EntityType.VILLAGER, new BlockPos(8, 2, 8)).setNoAi(true);
		helper.spawn(EntityType.VILLAGER, new BlockPos(9, 2, 8)).setNoAi(true);
		for (int x : new int[] {3, 5, 7}) {
			helper.setBlock(new BlockPos(x, 2, 15), Blocks.RED_BED.defaultBlockState().setValue(BedBlock.FACING, Direction.SOUTH).setValue(BedBlock.PART, BedPart.FOOT));
			helper.setBlock(new BlockPos(x, 2, 16), Blocks.RED_BED.defaultBlockState().setValue(BedBlock.FACING, Direction.SOUTH).setValue(BedBlock.PART, BedPart.HEAD));
		}
		helper.setBlock(HALL, ModBlocks.VILLAGE_HALL);
		VillageNeeds.Needs happy = new VillageNeeds.Needs(2, 2, 2, 2, 2, 0, 0, 1f);
		helper.runAfterDelay(5, () -> {
			ServerLevel level = helper.getLevel();
			BlockPos hall = helper.absolutePos(HALL);
			VillageHallBlockEntity entity = ready(helper);
			Container chest = helper.getBlockEntity(CHEST);
			long now = level.getGameTime();

			// No edict: a day apart, 16 needed, 8 eaten.
			helper.assertTrue(VillageGrowth.every(level, hall) == 24000 && VillageGrowth.foodNeeded(level, hall) == 16
				&& VillageGrowth.familyMeals(level, hall) == 8, "usual: " + numbers(level, hall));
			chest.setItem(0, new ItemStack(Items.BREAD, 16));
			helper.assertTrue(VillageGrowth.blocker(level, hall, happy, now - 12000) == VillageGrowth.Blocker.TOO_SOON, "half a day after a birth: "
				+ VillageGrowth.blocker(level, hall, happy, now - 12000));
			helper.assertTrue(born(helper, level, hall, chest, happy, 16, 0) == 8, "the usual family ate");

			// Large Families: half a day apart, 24 needed, 12 eaten.
			helper.assertTrue(Edicts.proclaim(level, hall, null, Edicts.get(LARGE_FAMILIES).orElseThrow()).done(), "proclaimed");
			helper.assertTrue(VillageGrowth.every(level, hall) == 12000 && VillageGrowth.foodNeeded(level, hall) == 24
				&& VillageGrowth.familyMeals(level, hall) == 12, "Large Families: " + numbers(level, hall));
			chest.setItem(0, new ItemStack(Items.BREAD, 23));
			helper.assertTrue(VillageGrowth.blocker(level, hall, happy, 0) == VillageGrowth.Blocker.FOOD, "23 meals: " + VillageGrowth.blocker(level, hall, happy, 0));
			helper.assertTrue(VillageGrowth.blocker(level, hall, happy, now - 11999) == VillageGrowth.Blocker.FOOD, "food first");
			chest.setItem(0, new ItemStack(Items.BREAD, 24));
			helper.assertTrue(VillageGrowth.blocker(level, hall, happy, now - 11999) == VillageGrowth.Blocker.TOO_SOON, "11999 ticks after a birth");
			helper.assertTrue(VillageGrowth.blocker(level, hall, happy, now - 12000) == VillageGrowth.Blocker.NONE, "12000 ticks after a birth");
			int births = entity.chronicle().size();
			helper.assertTrue(born(helper, level, hall, chest, happy, 24, now - 12000) == 12, "the large family ate");
			helper.assertTrue(born(helper, level, hall, chest, happy, 24, now - 12000) == 12, "the second baby's family ate");
			helper.assertTrue(entity.chronicle().size() == births + 2, "two births chronicled: " + entity.chronicle());

			// Reformed: two a day, the usual food.
			entity.setReforms(List.of(new Reforms.Progress(LARGE_FAMILIES.toString(), 3, true, 0)));
			helper.assertTrue(VillageGrowth.every(level, hall) == 12000 && VillageGrowth.foodNeeded(level, hall) == 16
				&& VillageGrowth.familyMeals(level, hall) == 8, "reformed: " + numbers(level, hall));
			helper.assertTrue(born(helper, level, hall, chest, happy, 16, now - 12000) == 8, "the reformed family ate");

			// Edicts off: the usual.
			try {
				Edicts.setEnabled(false);
				helper.assertTrue(VillageGrowth.every(level, hall) == 24000 && VillageGrowth.foodNeeded(level, hall) == 16, "off: " + numbers(level, hall));
			} finally {
				Edicts.setEnabled(true);
			}
			helper.succeed();
		});
	}

	/** Puts {@code meals} bread in the store and has a baby (the last one {@code lastBirth}); returns the meals eaten. */
	private static int born(GameTestHelper helper, ServerLevel level, BlockPos hall, Container chest, VillageNeeds.Needs needs, int meals, long lastBirth) {
		chest.setItem(0, new ItemStack(Items.BREAD, meals));
		Villager baby = VillageGrowth.grow(level, hall, needs, lastBirth);
		helper.assertTrue(baby != null && baby.isBaby(), "no baby with " + meals + " meals: " + VillageGrowth.blocker(level, hall, needs, lastBirth));
		baby.setNoAi(true);
		return meals - chest.countItem(Items.BREAD);
	}

	private static String numbers(ServerLevel level, BlockPos hall) {
		return VillageGrowth.every(level, hall) + " ticks, " + VillageGrowth.foodNeeded(level, hall) + " needed, " + VillageGrowth.familyMeals(level, hall) + " eaten";
	}

	/**
	 * The daily chance of falling ill: 6% for a fed villager without a bed, 9% under Large Families, 6% again once
	 * reformed; Free Bread leaves it alone.
	 */
	//$ gametest_ticks_batch AREA '100' '"largeFamiliesSickness"'
	@GameTest(template = AREA, timeoutTicks = 100, batch = "largeFamiliesSickness")
	public void largeFamiliesFallIllHalfAgainAsOften(GameTestHelper helper) {
		Leftovers.clear(helper);
		village(helper);
		Villager villager = helper.spawn(EntityType.VILLAGER, new BlockPos(6, 2, 6));
		villager.setNoAi(true);
		helper.setBlock(HALL, ModBlocks.VILLAGE_HALL);
		helper.runAfterDelay(5, () -> {
			ServerLevel level = helper.getLevel();
			BlockPos hall = helper.absolutePos(HALL);
			VillageHallBlockEntity entity = ready(helper);
			entity.setRank(VillageRanks.Rank.VILLAGE);
			ModAttachments.LAST_MEAL.set(villager, level.getGameTime());
			float usual = Sickness.dailyChance(level, villager);
			helper.assertTrue(Math.abs(usual - 0.06f) < 1e-6, "fed, no bed: " + usual);
			helper.assertTrue(Edicts.proclaim(level, hall, null, Edicts.get(FREE_BREAD).orElseThrow()).done(), "Free Bread proclaimed");
			helper.assertTrue(Math.abs(Sickness.dailyChance(level, villager) - 0.06f) < 1e-6, "Free Bread: " + Sickness.dailyChance(level, villager));
			helper.assertTrue(Edicts.proclaim(level, hall, null, Edicts.get(LARGE_FAMILIES).orElseThrow()).done(), "Large Families proclaimed");
			float more = Sickness.dailyChance(level, villager);
			helper.assertTrue(Math.abs(more - 0.09f) < 1e-6, "Large Families: " + more);
			entity.setReforms(List.of(new Reforms.Progress(LARGE_FAMILIES.toString(), 3, true, 0)));
			float reformed = Sickness.dailyChance(level, villager);
			helper.assertTrue(Math.abs(reformed - 0.06f) < 1e-6, "reformed: " + reformed);
			helper.succeed();
		});
	}

	/**
	 * The Book of Edicts tells both in words, with their reforms; a proclaimed edict puts its reform's first step on the
	 * quest page (64 wheat); reformed, the cost lines go and "Reformed: The Midwives" shows.
	 */
	//$ gametest_ticks_batch AREA '100' '"familyEdictsBook"'
	@GameTest(template = AREA, timeoutTicks = 100, batch = "familyEdictsBook")
	public void theBookTellsFreeBreadAndLargeFamilies(GameTestHelper helper) {
		Leftovers.clear(helper);
		village(helper);
		helper.setBlock(HALL, ModBlocks.VILLAGE_HALL);
		ServerPlayer owner = helper.makeMockServerPlayerInLevel();
		Leftovers.after(helper, () -> helper.getLevel().getServer().getPlayerList().remove(owner));
		helper.runAfterDelay(5, () -> {
			ServerLevel level = helper.getLevel();
			BlockPos hall = helper.absolutePos(HALL);
			VillageHallBlockEntity entity = ready(helper);
			entity.setRank(VillageRanks.Rank.VILLAGE);
			owner.teleportTo(hall.getX() + 1.5, hall.getY(), hall.getZ() + 0.5);

			List<String> bread = bookLore(owner, hall, "Free Bread");
			for (String want : List.of("Everyone fed in the last day is 10 happier; the village eats 30% more.",
				"+ everyone fed in the last day is 10 happier (free bread)", "- the village eats 30% more",
				"Reform: The Common Granary, step 1 of 3", "A granary keeps the bread line, so the free bread no longer eats into the store.")) {
				helper.assertTrue(bread.contains(want), "Free Bread lacks \"" + want + "\": " + bread);
			}
			List<String> families = bookLore(owner, hall, "Large Families");
			for (String want : List.of("Up to two babies a day; a baby needs 24 meals in the store and the family eats 12; villagers fall ill 50% more often.",
				"+ up to 2 babies a day", "- a baby needs 24 meals in the store (not 16), the family eats 12 meals for it (not 8)",
				"- villagers fall ill 50% more often", "Reform: The Midwives, step 1 of 3")) {
				helper.assertTrue(families.contains(want), "Large Families lacks \"" + want + "\": " + families);
			}

			helper.assertTrue(Edicts.proclaim(level, hall, null, Edicts.get(FREE_BREAD).orElseThrow()).done(), "Free Bread proclaimed");
			List<VillageQuests.Quest> shown = Reforms.shown(entity);
			helper.assertTrue(shown.size() == 1 && shown.get(0).item().equals("minecraft:wheat") && shown.get(0).count() == 512
				&& shown.get(0).reward() == Math.round(4 * VillageRanks.questRewardFactor(entity.rank())), "the Granary's first step: " + shown);

			helper.assertTrue(Edicts.proclaim(level, hall, null, Edicts.get(LARGE_FAMILIES).orElseThrow()).done(), "Large Families proclaimed");
			entity.setReforms(List.of(new Reforms.Progress(LARGE_FAMILIES.toString(), 3, true, 0)));
			List<String> reformed = bookLore(owner, hall, "Large Families");
			helper.assertTrue(reformed.contains("+ up to 2 babies a day") && reformed.contains("Reformed: The Midwives")
				&& reformed.stream().noneMatch(l -> l.startsWith("- ")), "reformed: " + reformed);
			helper.succeed();
		});
	}

	private static List<String> bookLore(ServerPlayer player, BlockPos hall, String name) {
		ChoiceMenu menu = VillageHallScreen.forTest(player, hall);
		menu.clicked(VillageHallScreen.BOOK, 0, ClickType.PICKUP, player);
		for (int s = EdictBook.FIRST_EDICT; s < EdictBook.END_EDICTS; s++) {
			ItemStack icon = menu.icon(s);
			if (icon.getHoverName().getString().equals(name)) {
				ItemLore lore = icon.get(DataComponents.LORE);
				return lore == null ? List.of() : lore.lines().stream().map(Component::getString).toList();
			}
		}
		return List.of("(" + name + " isn't in the Book)");
	}

	// --- Helpers ---------------------------------------------------------------------------------------------------

	/** A village of radius 16; every cache and the edicts reset after the test. */
	private static void village(GameTestHelper helper) {
		int radius = VillageHalls.RADIUS;
		VillageHalls.RADIUS = 16;
		Leftovers.after(helper, () -> {
			VillageHalls.RADIUS = radius;
			VillageNeeds.forget();
			CivicEffects.forget();
			Moods.forget();
		});
	}

	/** The hall after its first round: a Hamlet with nothing in force and no reform, every cache asked again. */
	private static VillageHallBlockEntity ready(GameTestHelper helper) {
		VillageHallBlockEntity hall = (VillageHallBlockEntity) helper.getLevel().getBlockEntity(helper.absolutePos(HALL));
		hall.setRank(VillageRanks.Rank.HAMLET);
		hall.setEdicts(List.of());
		hall.setReforms(List.of());
		hall.setExtraMeals(0);
		VillageNeeds.forget();
		CivicEffects.forget();
		Moods.forget();
		return hall;
	}

	private static void moodsOn(GameTestHelper helper) {
		boolean moods = Moods.ENABLED;
		Moods.ENABLED = true;
		Leftovers.after(helper, () -> Moods.ENABLED = moods);
	}
}
