package io.github.jcondedata.aliveworkplace.test;

import com.mojang.serialization.JsonOps;
import io.github.jcondedata.aliveworkplace.AliveWorkplace;
import io.github.jcondedata.aliveworkplace.WorkplaceConfig;
import io.github.jcondedata.aliveworkplace.hall.Chronicle;
import io.github.jcondedata.aliveworkplace.hall.Festivals;
import io.github.jcondedata.aliveworkplace.hall.MarketDays;
import io.github.jcondedata.aliveworkplace.hall.NobleBalls;
import io.github.jcondedata.aliveworkplace.hall.Treasury;
import io.github.jcondedata.aliveworkplace.hall.VillageHallBlockEntity;
import io.github.jcondedata.aliveworkplace.hall.VillageHalls;
import io.github.jcondedata.aliveworkplace.hall.VillageRanks;
import io.github.jcondedata.aliveworkplace.people.ClassPerks;
import io.github.jcondedata.aliveworkplace.people.SocialClasses;
import io.github.jcondedata.aliveworkplace.registry.ModAttachments;
import io.github.jcondedata.aliveworkplace.registry.ModBlocks;
import io.github.jcondedata.aliveworkplace.registry.ModVillagers;
import io.github.jcondedata.aliveworkplace.work.Pace;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Container;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.npc.VillagerProfession;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;

/**
 * What each class gives (ROADMAP 34.7), from the class files we ship: the takings by class and wants (never below the old
 * formula for Peasants), the crafters' and scholars' pace under the shared cap, the Nobles' wellbeing (3% a household, 9%
 * at most), the Noble's Ball in place of every other festival (its best food, its mood, saved on the hall), the Burghers'
 * extra route and trader, and nothing at all with {@code villageClasses} off.
 */
public class ClassPerkGameTests implements net.fabricmc.fabric.api.gametest.v1.FabricGameTest {
	private static final String AREA = "aliveworkplace_test:big_area";
	private static final BlockPos HALL = new BlockPos(14, 2, 14);

	/** Our shipped class files, classes on; a hall at {@link #HALL} when {@code hall}; everything put back afterwards. */
	private static VillageHallBlockEntity setUp(GameTestHelper helper, boolean hall) {
		ServerLevel level = helper.getLevel();
		Leftovers.clear(helper);
		Leftovers.halls(helper);
		Leftovers.finished(helper);
		int radius = VillageHalls.RADIUS;
		int pace = Pace.MAX_PERCENT;
		VillageHalls.RADIUS = 16;
		SocialClasses.ENABLED = true;
		SocialClasses.load(SocialClasses.files(level.getServer().getResourceManager()));
		ClassPerks.forget();
		Leftovers.after(helper, () -> {
			VillageHalls.RADIUS = radius;
			Pace.MAX_PERCENT = pace;
			new WorkplaceConfig().apply();
			ClassPerks.forget();
			Festivals.forget();
		});
		if (!hall) {
			return null;
		}
		helper.setBlock(HALL, ModBlocks.VILLAGE_HALL);
		VillageHallBlockEntity entity = (VillageHallBlockEntity) level.getBlockEntity(helper.absolutePos(HALL));
		entity.setRank(VillageRanks.Rank.HAMLET);
		return entity;
	}

	private static ResourceLocation ours(String path) {
		return AliveWorkplace.id(path);
	}

	private static Villager villager(GameTestHelper helper, int x, int z, String cls, VillagerProfession job) {
		Villager v = helper.spawn(EntityType.VILLAGER, new BlockPos(x, 2, z));
		v.setNoAi(true);
		v.setVillagerData(v.getVillagerData().setProfession(job));
		SocialClasses.seed(v, SocialClasses.get(ours(cls)));
		return v;
	}

	private static void wants(Villager v, int wants) {
		ModAttachments.CLASS_STANDING.set(v, new SocialClasses.Standing(0, 0, 0, 0, wants));
	}

	private static void near(GameTestHelper helper, float a, float b, String what) {
		helper.assertTrue(Math.abs(a - b) < 1e-4f, what + ": " + a + " (expected " + b + ")");
	}

	//$ gametest_batch AREA '"classPerkTax"'
	@GameTest(template = AREA, batch = "classPerkTax")
	public void takingsFollowTheClassesAndTheirWants(GameTestHelper helper) {
		VillageHallBlockEntity hall = setUp(helper, true);
		ServerLevel level = helper.getLevel();
		Villager peasantA = villager(helper, 3, 3, "peasant", VillagerProfession.FARMER);
		Villager peasantB = villager(helper, 6, 3, "peasant", VillagerProfession.FISHERMAN);
		Villager artisan = villager(helper, 9, 3, "artisan", ModVillagers.CARPENTER);
		Villager burgher = villager(helper, 12, 3, "burgher", ModVillagers.SCHOLAR);
		Villager noble = villager(helper, 15, 3, "noble", VillagerProfession.NONE);
		Villager idlePeasant = villager(helper, 18, 3, "peasant", VillagerProfession.NONE);
		wants(artisan, 1); // a tavern near home: 10% more
		wants(burgher, 2);
		wants(peasantA, 3); // a Peasant has no wants to have: stays at 1
		List<Villager> workers = List.of(peasantA, peasantB, artisan, burgher);
		List<Villager> jobless = List.of(noble, idlePeasant);

		ClassPerks.Tax tax = ClassPerks.tax(workers, jobless);
		near(helper, tax.shares(), 1 + 1 + 1.5f * 1.1f + 2.5f * 1.2f + 4f, "shares of 2 Peasants, an Artisan with a want, a Burgher with two, a jobless Noble");
		near(helper, tax.byClass().get(ours("peasant")), 2f, "the Peasants' share");
		helper.assertTrue(tax.payers().get(ours("noble")) == 1 && tax.payers().get(ours("peasant")) == 2, "payers: " + tax.payers());

		// Peasants only: exactly the old formula.
		ClassPerks.Tax peasants = ClassPerks.tax(List.of(peasantA, peasantB), List.of(idlePeasant));
		near(helper, peasants.shares(), 2f, "two Peasant workers are two shares");
		for (float w : new float[]{0f, 0.5f, 1f}) {
			for (VillageRanks.Rank r : VillageRanks.Rank.values()) {
				helper.assertTrue(Treasury.takings(peasants.shares(), w, r) == Treasury.takings(2, w, r), "Peasants pay today's rate at " + w + " " + r);
				helper.assertTrue(Treasury.takings(tax.shares(), w, r) >= Treasury.takings(workers.size(), w, r), "never below the old formula at " + w + " " + r);
			}
		}

		// The hall's round puts the class takings in.
		BlockPos pos = helper.absolutePos(HALL);
		long today = Chronicle.day(level);
		hall.setLastTaxDay(today - 1);
		hall.setTreasury(0);
		Treasury.round(level, pos, hall, workers, jobless);
		float wellbeing = hall.needs() == null ? 0.5f : hall.needs().wellbeing();
		helper.assertTrue(hall.treasury() == Treasury.takings(tax.shares(), wellbeing, hall.rank()), "treasury " + hall.treasury());

		// The name tag's tooltip: a heading and one line per class with payers.
		List<Component> lines = Treasury.taxLines(level, pos, workers, jobless);
		helper.assertTrue(lines.size() == 1 + 4, "tooltip lines: " + lines);
		helper.assertTrue(lines.get(1).getString().startsWith("Peasant: 2 pay"), "first line: " + lines.get(1).getString());

		// Classes off: everyone is a plain worker again, and no split.
		SocialClasses.ENABLED = false;
		near(helper, ClassPerks.tax(workers, jobless).shares(), 4f, "classes off: one share a worker, the Noble pays nothing");
		helper.assertTrue(Treasury.taxLines(level, pos, workers, jobless).isEmpty(), "no split with classes off");
		helper.succeed();
	}

	//$ gametest_batch AREA '"classPerkPace"'
	@GameTest(template = AREA, batch = "classPerkPace")
	public void artisanCraftersAndBurgherScholarsWorkFasterUnderTheCap(GameTestHelper helper) {
		setUp(helper, false);
		Villager artisanCarpenter = villager(helper, 3, 3, "artisan", ModVillagers.CARPENTER);
		Villager nobleMason = villager(helper, 6, 3, "noble", VillagerProfession.MASON);
		Villager peasantCarpenter = villager(helper, 9, 3, "peasant", ModVillagers.CARPENTER);
		Villager artisanFarmer = villager(helper, 12, 3, "artisan", VillagerProfession.FARMER);
		Villager burgherScholar = villager(helper, 15, 3, "burgher", ModVillagers.SCHOLAR);
		Villager artisanScholar = villager(helper, 18, 3, "artisan", ModVillagers.SCHOLAR);

		near(helper, ClassPerks.pace(artisanCarpenter), 1f / 1.1f, "an Artisan carpenter");
		near(helper, ClassPerks.pace(nobleMason), 1f / 1.1f, "a Noble mason (Artisan or better)");
		near(helper, ClassPerks.pace(peasantCarpenter), 1f, "a Peasant carpenter");
		near(helper, ClassPerks.pace(artisanFarmer), 1f, "an Artisan farmer (not a crafter)");
		near(helper, ClassPerks.pace(burgherScholar), 1f / 1.15f, "a Burgher scholar");
		near(helper, ClassPerks.pace(artisanScholar), 1f, "an Artisan scholar");
		boolean listed = Pace.of(artisanCarpenter).faster().stream().anyMatch(p -> p.source() == Pace.CLASS);
		helper.assertTrue(listed, "the pace line names the class: " + Pace.of(artisanCarpenter).faster());
		String label = Pace.CLASS.label().of(artisanCarpenter).getString();
		helper.assertTrue(label.equals("their class (Artisan)"), "label: " + label);

		// Through the shared cap: with the cap at 105% the 10% stops at it.
		Pace.MAX_PERCENT = 105;
		Pace.Breakdown capped = Pace.of(artisanCarpenter);
		helper.assertTrue(capped.factor() >= Pace.cap() - 1e-4f && capped.capped(), "held to the cap: " + capped);
		Pace.MAX_PERCENT = 200;
		helper.assertTrue(Pace.factor(artisanCarpenter) >= Pace.cap() - 1e-4f, "never past the 2× cap");

		SocialClasses.ENABLED = false;
		near(helper, ClassPerks.pace(artisanCarpenter), 1f, "classes off");
		near(helper, ClassPerks.pace(burgherScholar), 1f, "classes off");
		helper.succeed();
	}

	//$ gametest_batch AREA '"classPerkWellbeing"'
	@GameTest(template = AREA, batch = "classPerkWellbeing")
	public void noblesLiftWellbeingThreePercentEachUpToNine(GameTestHelper helper) {
		setUp(helper, true);
		ServerLevel level = helper.getLevel();
		BlockPos pos = helper.absolutePos(HALL);
		near(helper, ClassPerks.wellbeing(ClassPerks.Sums.NONE), 0f, "no Nobles");
		near(helper, ClassPerks.wellbeing(new ClassPerks.Sums(Map.of(ours("noble"), 1))), 0.03f, "one Noble household");
		near(helper, ClassPerks.wellbeing(new ClassPerks.Sums(Map.of(ours("noble"), 2, ours("burgher"), 4))), 0.06f, "two Noble households, Burghers don't count");
		near(helper, ClassPerks.wellbeing(new ClassPerks.Sums(Map.of(ours("noble"), 3))), 0.09f, "three");
		near(helper, ClassPerks.wellbeing(new ClassPerks.Sums(Map.of(ours("noble"), 7))), 0.09f, "seven: capped at 9%");

		// Counted from the village's people: 4 single Nobles are 4 households, a child isn't one.
		List<Villager> people = new ArrayList<>();
		for (int i = 0; i < 4; i++) {
			people.add(villager(helper, 3 + 3 * i, 3, "noble", VillagerProfession.NONE));
		}
		Villager child = villager(helper, 3, 8, "noble", VillagerProfession.NONE);
		child.setAge(-24000);
		people.add(child);
		people.add(villager(helper, 6, 8, "peasant", VillagerProfession.FARMER));
		ClassPerks.Sums sums = ClassPerks.tally(level, pos, people);
		helper.assertTrue(sums.atLeast(SocialClasses.get(ours("noble"))) == 4, "noble households: " + sums);
		helper.assertTrue(sums.atLeast(SocialClasses.get(ours("peasant"))) == 5, "all households: " + sums);
		near(helper, ClassPerks.wellbeing(ClassPerks.sums(level, pos)), 0.09f, "the hall's sums");
		// Burghers or better: 4 Nobles count for the Burghers' route and trader.
		helper.assertTrue(ClassPerks.caravanRoutes(level, pos) == 1 && ClassPerks.marketTraders(level, pos) == 1, "one more route and trader");
		helper.assertTrue(ClassPerks.marketOffers(level, pos).size() == 2 && ClassPerks.marketOffers(level, pos).get(0).price() == 12,
			"the grand-house blueprints at 12: " + ClassPerks.marketOffers(level, pos));

		SocialClasses.ENABLED = false;
		near(helper, ClassPerks.wellbeing(ClassPerks.sums(level, pos)), 0f, "classes off");
		helper.assertTrue(ClassPerks.caravanRoutes(level, pos) == 0 && ClassPerks.marketTraders(level, pos) == 0, "classes off: no extra route or trader");
		helper.succeed();
	}

	//$ gametest_batch AREA '"classPerkBurghers"'
	@GameTest(template = AREA, batch = "classPerkBurghers")
	public void threeBurgherHouseholdsBringARouteAndATrader(GameTestHelper helper) {
		setUp(helper, true);
		ServerLevel level = helper.getLevel();
		BlockPos pos = helper.absolutePos(HALL);
		List<Villager> people = new ArrayList<>();
		people.add(villager(helper, 3, 3, "burgher", ModVillagers.SCHOLAR));
		people.add(villager(helper, 6, 3, "burgher", VillagerProfession.LIBRARIAN));
		ClassPerks.tally(level, pos, people);
		int before = MarketDays.traders(level, pos);
		helper.assertTrue(ClassPerks.caravanRoutes(level, pos) == 0 && ClassPerks.marketOffers(level, pos).isEmpty(), "two Burgher households: nothing yet");
		people.add(villager(helper, 9, 3, "burgher", VillagerProfession.CLERIC));
		ClassPerks.tally(level, pos, people);
		helper.assertTrue(ClassPerks.caravanRoutes(level, pos) == 1, "three: one more caravan route");
		helper.assertTrue(MarketDays.traders(level, pos) == before + 1, "three: a fourth trader (" + MarketDays.traders(level, pos) + ")");
		helper.succeed();
	}

	//$ gametest_batch AREA '"classPerkBall"'
	@GameTest(template = AREA, batch = "classPerkBall")
	public void aNobleHouseholdMakesEveryOtherFestivalABall(GameTestHelper helper) {
		VillageHallBlockEntity hall = setUp(helper, true);
		ServerLevel level = helper.getLevel();
		BlockPos pos = helper.absolutePos(HALL);
		long today = Chronicle.day(level);

		// No Noble: festivals stay festivals.
		List<Villager> people = new ArrayList<>(List.of(villager(helper, 3, 3, "burgher", VillagerProfession.FARMER)));
		ClassPerks.tally(level, pos, people);
		for (int i = 0; i < 4; i++) {
			Festivals.plan(level, pos, hall, today + 10 + i, 0);
			helper.assertTrue(!NobleBalls.isBall(hall, today + 10 + i), "no Noble, no ball");
		}

		// A Noble household: every other festival is a ball.
		people.add(villager(helper, 6, 3, "noble", VillagerProfession.NONE));
		ClassPerks.tally(level, pos, people);
		List<Boolean> balls = new ArrayList<>();
		for (int i = 0; i < 6; i++) {
			long day = today + 20 + i;
			Festivals.plan(level, pos, hall, day, 0);
			balls.add(NobleBalls.isBall(hall, day));
		}
		helper.assertTrue(balls.equals(List.of(false, true, false, true, false, true)), "one festival in two: " + balls);

		// Saved on the hall: the turn and the ball's day come back after a reload.
		CompoundTag tag = hall.saveWithFullMetadata(level.registryAccess());
		long ballDay = hall.ballDay();
		boolean turn = hall.ballTurn();
		hall.setBallDay(-100);
		hall.setBallTurn(!turn);
		hall.loadWithComponents(tag, level.registryAccess());
		helper.assertTrue(hall.ballDay() == ballDay && hall.ballTurn() == turn, "reloaded: " + hall.ballDay() + " " + hall.ballTurn());
		CompoundTag old = tag.copy();
		old.remove("ballDay");
		old.remove("ballTurn");
		hall.loadWithComponents(old, level.registryAccess());
		helper.assertTrue(hall.ballDay() == -100 && !hall.ballTurn(), "a hall saved before 34.7: no ball yet, the next festival plain");

		// Classes off: no ball even with the Noble.
		SocialClasses.ENABLED = false;
		Festivals.plan(level, pos, hall, today + 40, 0);
		Festivals.plan(level, pos, hall, today + 41, 0);
		helper.assertTrue(!NobleBalls.isBall(hall, today + 40) && !NobleBalls.isBall(hall, today + 41), "classes off: no ball");
		helper.succeed();
	}

	//$ gametest_batch AREA '"classPerkBallFeast"'
	@GameTest(template = AREA, batch = "classPerkBallFeast")
	public void theBallServesTheStoresBestAndCheersForThreeDays(GameTestHelper helper) {
		VillageHallBlockEntity hall = setUp(helper, true);
		ServerLevel level = helper.getLevel();
		BlockPos pos = helper.absolutePos(HALL);
		helper.setBlock(new BlockPos(3, 2, 20), Blocks.CHEST);
		BlockPos chest = helper.absolutePos(new BlockPos(3, 2, 20));
		Container box = (Container) level.getBlockEntity(chest);
		box.setItem(0, new ItemStack(Items.BREAD, 4));
		box.setItem(1, new ItemStack(Items.COOKED_BEEF, 2));
		box.setItem(2, new ItemStack(Items.COOKED_CHICKEN, 3));
		helper.assertTrue(NobleBalls.bestMeal(level, List.of(chest)) == Items.COOKED_BEEF, "the best food is the beef");
		Villager guest = villager(helper, 6, 6, "noble", VillagerProfession.NONE);
		helper.assertTrue(NobleBalls.feast(level, guest, List.of(chest)), "the guest dined");
		helper.assertTrue(box.getItem(1).getCount() == 1 && box.getItem(0).getCount() == 4, "the beef went first");
		NobleBalls.feast(level, guest, List.of(chest));
		NobleBalls.feast(level, guest, List.of(chest));
		helper.assertTrue(box.getItem(1).isEmpty() && box.getItem(0).getCount() == 4 && box.getItem(2).getCount() == 2,
			"then the next best (chicken beats bread)");

		// The ball's mood: 15, and it lasts 3 days.
		long today = Chronicle.day(level);
		hall.setFestivalDay(today);
		hall.setBallDay(today);
		helper.assertTrue(NobleBalls.isBall(hall, today) && Festivals.square(level, pos).equals(pos), "the ball is at the hall (no Manor)");
		ModAttachments.FESTIVAL_DAY.set(guest, today - 3);
		hall.setBallDay(today - 3);
		helper.assertTrue(NobleBalls.cameToBall(guest), "the guest came to the ball");
		helper.assertTrue(Festivals.mood(level, guest) == NobleBalls.MOOD, "+15 on the third day: " + Festivals.mood(level, guest));
		ModAttachments.FESTIVAL_DAY.set(guest, today - 4);
		hall.setBallDay(today - 4);
		helper.assertTrue(Festivals.mood(level, guest) == 0, "over after 3 days");

		// The new sentences read.
		for (String key : List.of("message.aliveworkplace.festival.ball_today", "message.aliveworkplace.festival.ball_tomorrow", "chronicle.aliveworkplace.ball",
			"screen.aliveworkplace.hall.tax_by_class", "screen.aliveworkplace.hall.tax_class", "pace.aliveworkplace.source.class")) {
			String text = Component.translatable(key, "Oakvale", 3, 2).getString();
			helper.assertTrue(!text.equals(key), "no text for " + key);
		}
		helper.succeed();
	}

	//$ gametest_batch AREA '"classPerkStanding"'
	@GameTest(template = AREA, batch = "classPerkStanding")
	public void wantsAreSavedAndOldStandingsReadAsNone(GameTestHelper helper) {
		setUp(helper, false);
		SocialClasses.Standing s = new SocialClasses.Standing(5, 1, 4, 1, 2);
		var json = SocialClasses.Standing.CODEC.encodeStart(JsonOps.INSTANCE, s).getOrThrow();
		helper.assertTrue(SocialClasses.Standing.CODEC.parse(JsonOps.INSTANCE, json).getOrThrow().equals(s), "round trip: " + json);
		var old = com.google.gson.JsonParser.parseString("{\"day\": 5, \"missing\": 1, \"turn_day\": 4, \"turn\": 1}");
		helper.assertTrue(SocialClasses.Standing.CODEC.parse(JsonOps.INSTANCE, old).getOrThrow().wants() == 0, "a 34.6 standing has no wants");
		helper.succeed();
	}
}
