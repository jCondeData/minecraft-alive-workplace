package io.github.jcondedata.aliveworkplace.test;

import com.google.gson.JsonParser;
import io.github.jcondedata.aliveworkplace.legend.Gifted;
import io.github.jcondedata.aliveworkplace.mixin.VillagerAccessor;
import io.github.jcondedata.aliveworkplace.registry.ModAttachments;
import io.github.jcondedata.aliveworkplace.registry.ModVillagers;
import java.util.List;
import java.util.UUID;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.npc.VillagerProfession;
import net.minecraft.world.entity.schedule.Activity;
import net.minecraft.world.entity.schedule.Schedule;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.trading.ItemCost;
import net.minecraft.world.item.trading.MerchantOffer;
import net.minecraft.world.level.GameType;

/**
 * QA (qa-lane-d-1027-0110) for 29.6, Gifted villagers, written from the spec: the chance at other settings (1, 10, 30)
 * and that raising it only adds people, malformed gift files, an attachment naming a removed gift or "none", Silver
 * Tongue on several prices and off with {@code giftedChance} 0, the Night Owl schedule's hours at each edge, a Night
 * Owl miner, the schedule following the gift on and off and surviving a save, and the hall's gold line in exact English.
 */
public class QaGiftedGameTests implements FabricGameTest {
	private static final String AREA = "aliveworkplace_test:build_area";
	private static final ResourceLocation PRODIGY = ResourceLocation.fromNamespaceAndPath("aliveworkplace", "prodigy");
	private static final ResourceLocation SILVER_TONGUE = ResourceLocation.fromNamespaceAndPath("aliveworkplace", "silver_tongue");
	private static final ResourceLocation NIGHT_OWL = ResourceLocation.fromNamespaceAndPath("aliveworkplace", "night_owl");

	/** giftedChance 1 gifts everyone, 10 about one in ten; anyone Gifted at 30 is still Gifted at 10 (no reshuffle). */
	//$ gametest 'FabricGameTest.EMPTY_STRUCTURE'
	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
	public void giftedChanceOtherSettingsOnlyAddPeople(GameTestHelper helper) {
		RandomSource random = RandomSource.create(2906L);
		int at10 = 0;
		for (int i = 0; i < 3000; i++) {
			UUID id = new UUID(random.nextLong(), random.nextLong());
			helper.assertTrue(Gifted.isGifted(id, 1), "giftedChance 1 left " + id + " ungifted");
			boolean g30 = Gifted.isGifted(id, 30);
			boolean g10 = Gifted.isGifted(id, 10);
			helper.assertTrue(!g30 || g10, id + " is Gifted at 1 in 30 but not at 1 in 10");
			if (g10) {
				at10++;
			}
		}
		// expected 300, spread about 16: 250-350 is three either way
		helper.assertTrue(at10 >= 250 && at10 <= 350, "giftedChance 10: " + at10 + " of 3000");
		helper.assertTrue(!Gifted.isGifted(new UUID(1, 2), 0) && !Gifted.isGifted(new UUID(1, 2), -5), "a chance of 0 or below gifted someone");
		helper.succeed();
	}

	/** A gift file with no weight, a weight of 0 or an unknown effect fails; the four built-in gifts load with their lang keys. */
	//$ gametest 'FabricGameTest.EMPTY_STRUCTURE'
	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
	public void malformedGiftFilesFail(GameTestHelper helper) {
		ResourceLocation bad = ResourceLocation.fromNamespaceAndPath("aliveworkplace_test", "qa_bad_gift");
		for (String json : List.of("{\"effects\": [{\"type\": \"xp\", \"factor\": 3}]}", "{\"weight\": 0, \"effects\": []}",
			"{\"weight\": -2, \"effects\": []}", "{\"weight\": 5, \"effects\": [{\"type\": \"fly\"}]}")) {
			try {
				Gifted.Gift g = Gifted.read(bad, JsonParser.parseString(json).getAsJsonObject());
				helper.fail("a malformed gift loaded: " + json + " -> " + g);
			} catch (RuntimeException expected) {
				// skipped by the loader, with a warning
			}
		}
		for (String id : List.of("prodigy", "iron_will", "silver_tongue", "night_owl")) {
			Gifted.Gift g = Gifted.get(ResourceLocation.fromNamespaceAndPath("aliveworkplace", id));
			helper.assertTrue(g != null && g.weight() > 0, id + " not loaded");
		}
		helper.succeed();
	}

	/** An attachment naming a gift that no longer exists, or "none", makes nobody Gifted and never crashes the hall's line. */
	//$ gametest_batch AREA '"qaGiftedAttachment"'
	@GameTest(template = AREA, batch = "qaGiftedAttachment")
	public void attachmentOverridesTheRollEvenForARemovedGift(GameTestHelper helper) {
		boolean roll = Gifted.ROLL;
		try {
			Gifted.ROLL = true;
			// a UUID the roll gifts
			UUID lucky = null;
			for (long i = 1; lucky == null; i++) {
				UUID id = new UUID(i * 0x9E3779B97F4A7C15L, ~i);
				if (Gifted.roll(id) != null) {
					lucky = id;
				}
			}
			Villager v = EntityType.VILLAGER.create(helper.getLevel());
			v.setUUID(lucky);
			helper.assertTrue(Gifted.of(v) != null, "the roll should gift " + lucky);
			ModAttachments.GIFTED.set(v, Gifted.NONE);
			helper.assertTrue(Gifted.of(v) == null && Gifted.hallLine(v) == null, "\"none\" didn't override the roll");
			ModAttachments.GIFTED.set(v, "aliveworkplace:retired_gift");
			helper.assertTrue(Gifted.of(v) == null && Gifted.hallLine(v) == null && Gifted.tradeDiscount(v) == 0 && Gifted.xpFactor(v) == 1f,
				"a removed gift still does something");
			ModAttachments.GIFTED.set(v, "not a valid id!");
			helper.assertTrue(Gifted.of(v) == null, "a broken id gave a gift");
			v.discard();
		} finally {
			Gifted.ROLL = roll;
		}
		helper.succeed();
	}

	/** The hall's gold line reads "Gifted: Prodigy (learns three times as fast)", and so on for each of the four. */
	//$ gametest_batch AREA '"qaGiftedHallLine"'
	@GameTest(template = AREA, batch = "qaGiftedHallLine")
	public void hallLineInExactEnglish(GameTestHelper helper) {
		Villager v = helper.spawnWithNoFreeWill(EntityType.VILLAGER, new BlockPos(3, 2, 3));
		String[][] expect = {
			{"prodigy", "Gifted: Prodigy (learns three times as fast)"},
			{"iron_will", "Gifted: Iron Will (never panics, and keeps working through raids and the bell)"},
			{"silver_tongue", "Gifted: Silver Tongue (every trade with them is 20% cheaper)"},
			{"night_owl", "Gifted: Night Owl (works from dusk to dawn, sleeps from mid-morning to mid-afternoon)"}};
		for (String[] e : expect) {
			Gifted.set(v, ResourceLocation.fromNamespaceAndPath("aliveworkplace", e[0]));
			String line = Gifted.hallLine(v) == null ? "null" : Gifted.hallLine(v).getString();
			helper.assertTrue(line.equals(e[1]), e[0] + ": '" + line + "'");
		}
		Gifted.set(v, null);
		helper.assertTrue(Gifted.hallLine(v) == null, "no gift, but a hall line");
		helper.succeed();
	}

	/** Silver Tongue on 5, 20 and 64 emeralds (4, 16, 52), and no discount at all with giftedChance 0. */
	//$ gametest_batch AREA '"qaGiftedSilverPrices"'
	@GameTest(template = AREA, batch = "qaGiftedSilverPrices")
	public void silverTongueOnSeveralPricesAndOffWithChanceZero(GameTestHelper helper) {
		Villager silver = helper.spawnWithNoFreeWill(EntityType.VILLAGER, new BlockPos(3, 2, 3));
		Gifted.set(silver, SILVER_TONGUE);
		var player = helper.makeMockPlayer(GameType.SURVIVAL);
		int first = silver.getOffers().size();
		for (int price : new int[] {5, 20, 64}) {
			silver.getOffers().add(new MerchantOffer(new ItemCost(Items.EMERALD, price), new ItemStack(Items.BREAD), 12, 1, 0.05f));
		}
		((VillagerAccessor) silver).aliveworkplace$updateSpecialPrices(player);
		int[] want = {4, 16, 52};
		for (int i = 0; i < 3; i++) {
			int got = silver.getOffers().get(first + i).getCostA().getCount();
			helper.assertTrue(got == want[i], "a Silver Tongue's offer of " + new int[] {5, 20, 64}[i] + " costs " + got + ", expected " + want[i]);
		}
		for (MerchantOffer offer : silver.getOffers()) {
			offer.resetSpecialPriceDiff(); // as vanilla does when the trading screen closes
		}
		int before = Gifted.CHANCE;
		try {
			Gifted.CHANCE = 0;
			((VillagerAccessor) silver).aliveworkplace$updateSpecialPrices(player);
			int got = silver.getOffers().get(first + 1).getCostA().getCount();
			helper.assertTrue(got == 20, "with giftedChance 0 a Silver Tongue's 20-emerald offer costs " + got);
		} finally {
			Gifted.CHANCE = before;
		}
		helper.succeed();
	}

	/** The Night Owl's hours: works from dusk (12000) to dawn, rests from mid-morning (3000) to mid-afternoon (9000), each edge both sides. */
	//$ gametest 'FabricGameTest.EMPTY_STRUCTURE'
	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
	public void nightOwlScheduleEdges(GameTestHelper helper) {
		Schedule s = ModVillagers.NIGHT_OWL_SCHEDULE;
		Object[][] at = {{11999, Activity.IDLE}, {12000, Activity.WORK}, {18000, Activity.WORK}, {23999, Activity.WORK}, {0, Activity.WORK},
			{2999, Activity.IDLE}, {3000, Activity.REST}, {6000, Activity.REST}, {8999, Activity.REST}, {9000, Activity.IDLE}};
		for (Object[] a : at) {
			Activity got = s.getActivityAt((Integer) a[0]);
			helper.assertTrue(got == a[1], "at " + a[0] + ": " + got + ", expected " + a[1]);
		}
		helper.succeed();
	}

	/**
	 * The schedule follows the gift: a Night Owl miner is on it, a jobless or nitwit Night Owl and a baby aren't, the gift
	 * taken away puts the miner back on days, and a Night Owl saved and loaded is still one.
	 */
	//$ gametest_ticks_batch AREA '300' '"qaGiftedOwlSchedule"'
	@GameTest(template = AREA, timeoutTicks = 300, batch = "qaGiftedOwlSchedule")
	public void nightOwlScheduleFollowsTheGift(GameTestHelper helper) {
		Leftovers.clear(helper);
		helper.setDayTime(6000);
		Villager miner = helper.spawn(EntityType.VILLAGER, new BlockPos(2, 2, 2));
		miner.setVillagerData(miner.getVillagerData().setProfession(ModVillagers.MINER).setLevel(2));
		Villager jobless = helper.spawn(EntityType.VILLAGER, new BlockPos(5, 2, 2));
		Villager nitwit = helper.spawn(EntityType.VILLAGER, new BlockPos(2, 2, 5));
		nitwit.setVillagerData(nitwit.getVillagerData().setProfession(VillagerProfession.NITWIT));
		Villager baby = helper.spawn(EntityType.VILLAGER, new BlockPos(5, 2, 5));
		baby.setAge(-24000);
		for (Villager v : List.of(miner, jobless, nitwit, baby)) {
			Gifted.set(v, NIGHT_OWL);
		}
		helper.runAfterDelay(110, () -> {
			helper.assertTrue(miner.getBrain().getSchedule() == ModVillagers.NIGHT_OWL_SCHEDULE, "a Night Owl miner isn't on the night schedule");
			helper.assertTrue(Gifted.nightOwl(miner) && !Gifted.nightOwl(jobless) && !Gifted.nightOwl(nitwit) && !Gifted.nightOwl(baby),
				"nightOwl(): " + Gifted.nightOwl(miner) + Gifted.nightOwl(jobless) + Gifted.nightOwl(nitwit) + Gifted.nightOwl(baby));
			for (Villager v : List.of(jobless, nitwit, baby)) {
				helper.assertTrue(v.getBrain().getSchedule() != ModVillagers.NIGHT_OWL_SCHEDULE, "on the night schedule without a trade or grown: " + v.getVillagerData());
			}
			CompoundTag tag = new CompoundTag();
			miner.saveWithoutId(tag);
			Villager copy = EntityType.VILLAGER.create(helper.getLevel());
			copy.load(tag);
			helper.assertTrue(Gifted.of(copy) != null && Gifted.of(copy).id().equals(NIGHT_OWL) && Gifted.nightOwl(copy), "the Night Owl lost the gift in a save");
			copy.discard();
			Gifted.set(miner, null);
		});
		helper.runAfterDelay(230, () -> {
			helper.assertTrue(miner.getBrain().getSchedule() != ModVillagers.NIGHT_OWL_SCHEDULE, "the miner kept the night schedule after the gift went");
			helper.succeed();
		});
	}

	/** A Prodigy's XP is three times even on odd amounts (1 to 3, 5 to 15), and ordinary with the gift taken away. */
	//$ gametest_batch AREA '"qaGiftedProdigyAmounts"'
	@GameTest(template = AREA, batch = "qaGiftedProdigyAmounts")
	public void prodigyFactorAndRemoval(GameTestHelper helper) {
		Villager v = helper.spawnWithNoFreeWill(EntityType.VILLAGER, new BlockPos(3, 2, 3));
		Gifted.set(v, PRODIGY);
		helper.assertTrue(Gifted.xpFactor(v) == 3f, "Prodigy factor " + Gifted.xpFactor(v));
		for (int xp : new int[] {1, 5}) {
			int got = io.github.jcondedata.aliveworkplace.people.Traits.xp(v, xp, RandomSource.create(xp));
			helper.assertTrue(got == xp * 3, "a Prodigy got " + got + " XP for " + xp);
		}
		Gifted.set(v, null);
		helper.assertTrue(Gifted.xpFactor(v) == 1f && io.github.jcondedata.aliveworkplace.people.Traits.xp(v, 5, RandomSource.create(1)) == 5,
			"the gift taken away, still faster");
		helper.succeed();
	}
}
