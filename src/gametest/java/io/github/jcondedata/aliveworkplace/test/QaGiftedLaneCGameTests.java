package io.github.jcondedata.aliveworkplace.test;

import com.google.gson.JsonObject;
import io.github.jcondedata.aliveworkplace.hall.Chronicle;
import io.github.jcondedata.aliveworkplace.hall.VillageHallBlockEntity;
import io.github.jcondedata.aliveworkplace.legend.Gifted;
import io.github.jcondedata.aliveworkplace.legend.GiftedAuras;
import io.github.jcondedata.aliveworkplace.legend.LegendPowers;
import io.github.jcondedata.aliveworkplace.people.Families;
import io.github.jcondedata.aliveworkplace.people.Moods;
import io.github.jcondedata.aliveworkplace.registry.ModAttachments;
import io.github.jcondedata.aliveworkplace.registry.ModBlocks;
import io.github.jcondedata.aliveworkplace.registry.ModVillagers;
import io.github.jcondedata.aliveworkplace.work.Pace;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.npc.VillagerProfession;
import net.minecraft.world.entity.schedule.Activity;
import net.minecraft.world.entity.schedule.Schedule;
import net.minecraft.world.inventory.MerchantMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.trading.ItemCost;
import net.minecraft.world.item.trading.MerchantOffer;
import net.minecraft.world.item.trading.MerchantOffers;

/**
 * QA (2026-10-27), written from ROADMAP 29.6 and 29.7 rather than the code, for what GiftedGameTests and
 * GiftedBornGameTests leave out: Silver Tongue through the trade screen a player opens (closed, restocked, opened again;
 * with Hero of the Village), the Night Owl's hours at dusk, dawn, mid-morning and mid-afternoon and who gets them (and
 * after a reload), the {@code GIFTED} attachment overriding the UUID roll over a save, {@code giftedChance} as "one in
 * N", Hardy's health not stacking, Beloved and Born Leader at exactly 16 blocks and at 17, the Born Leader under the
 * shared pace cap, the born rolls' "two schooled Masters" rule, and the sentences players read rendered from the real
 * en_us.json.
 */
public class QaGiftedLaneCGameTests implements FabricGameTest {
	private static final String AREA = "aliveworkplace_test:build_area";
	private static final String HUGE = "aliveworkplace_test:huge_area";
	private static final ResourceLocation PRODIGY = id("prodigy");
	private static final ResourceLocation SILVER_TONGUE = id("silver_tongue");
	private static final ResourceLocation NIGHT_OWL = id("night_owl");
	private static final ResourceLocation HARDY = id("hardy");
	private static final ResourceLocation BELOVED = id("beloved");
	private static final ResourceLocation BORN_LEADER = id("born_leader");

	private static ResourceLocation id(String path) {
		return ResourceLocation.fromNamespaceAndPath("aliveworkplace", path);
	}

	private static Villager worker(GameTestHelper helper, BlockPos at, VillagerProfession job) {
		Villager v = helper.spawn(EntityType.VILLAGER, at);
		v.setNoAi(true);
		v.setVillagerData(v.getVillagerData().setProfession(job).setLevel(2));
		return v;
	}

	// ---- Rendering with the real en_us.json ----

	/** {@code c} as an English-speaking player reads it, from the mod's own en_us.json (vanilla keys: their fallback). */
	private static String render(JsonObject lang, Component c) {
		StringBuilder out = new StringBuilder();
		if (c.getContents() instanceof TranslatableContents t) {
			String format = lang.has(t.getKey()) ? lang.get(t.getKey()).getAsString() : t.getFallback() != null ? t.getFallback() : MutableComponent.create(t).getString();
			Object[] args = t.getArgs();
			int next = 0;
			for (int i = 0; i < format.length(); i++) {
				char ch = format.charAt(i);
				if (ch != '%' || i + 1 >= format.length()) {
					out.append(ch);
					continue;
				}
				char n = format.charAt(i + 1);
				if (n == '%') {
					out.append('%');
					i++;
				} else if (n == 's') {
					out.append(arg(lang, args, next++));
					i++;
				} else if (Character.isDigit(n) && format.indexOf("$s", i) == i + 2) {
					out.append(arg(lang, args, n - '1'));
					i += 3;
				} else {
					out.append(ch);
				}
			}
		} else {
			out.append(MutableComponent.create(c.getContents()).getString());
		}
		for (Component sibling : c.getSiblings()) {
			out.append(render(lang, sibling));
		}
		return out.toString();
	}

	private static String arg(JsonObject lang, Object[] args, int i) {
		if (i < 0 || i >= args.length) {
			return "<missing argument " + (i + 1) + ">";
		}
		return args[i] instanceof Component c ? render(lang, c) : String.valueOf(args[i]);
	}

	private static boolean key(Component c, String key) {
		return c.getContents() instanceof TranslatableContents t && t.getKey().equals(key);
	}

	// ---- Silver Tongue ----

	private static Villager trader(GameTestHelper helper, BlockPos at) {
		Villager v = helper.spawnWithNoFreeWill(EntityType.VILLAGER, at);
		v.setVillagerData(v.getVillagerData().setProfession(VillagerProfession.FARMER).setLevel(1));
		MerchantOffers offers = new MerchantOffers();
		offers.add(new MerchantOffer(new ItemCost(Items.EMERALD, 10), new ItemStack(Items.BREAD), 12, 1, 0.05f));
		offers.add(new MerchantOffer(new ItemCost(Items.EMERALD, 25), new ItemStack(Items.CAKE), 12, 1, 0.05f));
		offers.add(new MerchantOffer(new ItemCost(Items.EMERALD, 1), new ItemStack(Items.APPLE), 12, 1, 0.05f));
		v.setOffers(offers);
		return v;
	}

	/** The player right-clicks the villager, as a player opens its trades; returns the three prices on the open screen. */
	private static int[] open(GameTestHelper helper, Villager villager, ServerPlayer player) {
		player.moveTo(villager.getX() + 1, villager.getY(), villager.getZ(), 90f, 0f);
		player.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
		villager.mobInteract(player, InteractionHand.MAIN_HAND);
		helper.assertTrue(player.containerMenu instanceof MerchantMenu, "no trade screen: " + player.containerMenu);
		return prices(villager);
	}

	private static int[] prices(Villager villager) {
		MerchantOffers o = villager.getOffers();
		return new int[] {o.get(0).getCostA().getCount(), o.get(1).getCostA().getCount(), o.get(2).getCostA().getCount()};
	}

	/**
	 * Silver Tongue through the trade screen: 10 emeralds cost 8 and 25 cost 20, 1 stays 1; an ordinary villager's don't
	 * change. Closing the screen and restocking, then opening it again, gives the same prices (the discount neither
	 * stacks nor gets lost).
	 */
	//$ gametest_batch AREA '"qaSilverTongueScreen"'
	@GameTest(template = AREA, batch = "qaSilverTongueScreen")
	public void qaSilverTongueThroughTheTradeScreenAndARestock(GameTestHelper helper) {
		ServerPlayer player = helper.makeMockServerPlayerInLevel();
		Leftovers.after(helper, () -> helper.getLevel().getServer().getPlayerList().remove(player));
		Villager silver = trader(helper, new BlockPos(3, 2, 3));
		Villager plain = trader(helper, new BlockPos(8, 2, 3));
		Gifted.set(silver, SILVER_TONGUE);
		Gifted.set(plain, null);
		int[] first = open(helper, silver, player);
		helper.assertTrue(first[0] == 8 && first[1] == 20 && first[2] == 1, "a Silver Tongue's 10/25/1-emerald offers cost " + java.util.Arrays.toString(first));
		player.closeContainer();
		int[] closed = prices(silver);
		helper.assertTrue(closed[0] == 10 && closed[1] == 25, "with the screen closed the base prices read " + java.util.Arrays.toString(closed));
		int[] ordinary = open(helper, plain, player);
		helper.assertTrue(ordinary[0] == 10 && ordinary[1] == 25 && ordinary[2] == 1, "an ordinary villager's offers cost " + java.util.Arrays.toString(ordinary));
		player.closeContainer();
		silver.restock();
		int[] again = open(helper, silver, player);
		helper.assertTrue(again[0] == 8 && again[1] == 20 && again[2] == 1, "after closing and a restock, opened again: " + java.util.Arrays.toString(again));
		player.closeContainer();
		int[] third = open(helper, silver, player);
		helper.assertTrue(third[0] == 8 && third[1] == 20, "opened a third time: " + java.util.Arrays.toString(third));
		player.closeContainer();
		helper.succeed();
	}

	/** With Hero of the Village as well, a Silver Tongue is still cheaper than an ordinary villager, and 1 emerald stays 1. */
	//$ gametest_batch AREA '"qaSilverTongueHero"'
	@GameTest(template = AREA, batch = "qaSilverTongueHero")
	public void qaSilverTongueOnTopOfHeroOfTheVillage(GameTestHelper helper) {
		ServerPlayer player = helper.makeMockServerPlayerInLevel();
		Leftovers.after(helper, () -> helper.getLevel().getServer().getPlayerList().remove(player));
		player.addEffect(new MobEffectInstance(MobEffects.HERO_OF_THE_VILLAGE, 2000, 0));
		Villager silver = trader(helper, new BlockPos(3, 2, 3));
		Villager plain = trader(helper, new BlockPos(8, 2, 3));
		Gifted.set(silver, SILVER_TONGUE);
		Gifted.set(plain, null);
		int[] hero = open(helper, plain, player);
		player.closeContainer();
		int[] both = open(helper, silver, player);
		player.closeContainer();
		helper.assertTrue(hero[0] < 10, "staging: Hero of the Village gave no discount (" + hero[0] + ")");
		helper.assertTrue(both[0] < hero[0] && both[1] < hero[1], "Silver Tongue with Hero " + java.util.Arrays.toString(both) + ", Hero alone " + java.util.Arrays.toString(hero));
		helper.assertTrue(both[0] >= 1 && both[1] >= 1 && both[2] == 1, "a price fell under 1: " + java.util.Arrays.toString(both));
		helper.succeed();
	}

	// ---- Night Owl ----

	/**
	 * The Night Owl's hours: works from dusk to dawn (13000, 18000, 23000), sleeps from mid-morning to mid-afternoon (4000,
	 * noon, 8500); doesn't work in the day (1000, 10000) and isn't in bed after mid-afternoon.
	 */
	//$ gametest 'FabricGameTest.EMPTY_STRUCTURE'
	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
	public void qaNightOwlHoursAtDuskDawnAndMidday(GameTestHelper helper) {
		Schedule s = ModVillagers.NIGHT_OWL_SCHEDULE;
		for (int t : new int[] {13000, 15000, 18000, 21000, 23000}) {
			helper.assertTrue(s.getActivityAt(t) == Activity.WORK, "a Night Owl at " + t + " is " + s.getActivityAt(t).getName());
		}
		for (int t : new int[] {4000, 6000, 8500}) {
			helper.assertTrue(s.getActivityAt(t) == Activity.REST, "a Night Owl at " + t + " isn't asleep: " + s.getActivityAt(t).getName());
		}
		for (int t : new int[] {1000, 6000, 10000}) {
			helper.assertTrue(s.getActivityAt(t) != Activity.WORK, "a Night Owl works in the day at " + t);
		}
		helper.assertTrue(s.getActivityAt(10500) != Activity.REST, "a Night Owl still asleep after mid-afternoon");
		helper.succeed();
	}

	/**
	 * Who goes on the night schedule: a Night Owl with a trade (a vanilla farmer, a miner); not a Night Owl without one,
	 * a nitwit or a child; not an ordinary miner; and a Night Owl whose gift is taken goes back off it.
	 */
	//$ gametest_batch AREA '"qaNightOwlWho"'
	@GameTest(template = AREA, batch = "qaNightOwlWho")
	public void qaNightOwlScheduleGoesToGrownWorkersOnly(GameTestHelper helper) {
		Villager farmer = worker(helper, new BlockPos(2, 2, 2), VillagerProfession.FARMER);
		Villager miner = worker(helper, new BlockPos(4, 2, 2), ModVillagers.MINER);
		Villager plainMiner = worker(helper, new BlockPos(6, 2, 2), ModVillagers.MINER);
		Villager jobless = worker(helper, new BlockPos(8, 2, 2), VillagerProfession.NONE);
		Villager nitwit = worker(helper, new BlockPos(10, 2, 2), VillagerProfession.NITWIT);
		Villager child = helper.spawn(EntityType.VILLAGER, new BlockPos(2, 2, 6));
		child.setNoAi(true);
		child.setAge(-24000);
		for (Villager v : List.of(farmer, miner, jobless, nitwit, child)) {
			Gifted.set(v, NIGHT_OWL);
		}
		Gifted.set(plainMiner, null);
		helper.assertTrue(farmer.getBrain().getSchedule() == ModVillagers.NIGHT_OWL_SCHEDULE, "a Night Owl farmer isn't on the night schedule");
		helper.assertTrue(miner.getBrain().getSchedule() == ModVillagers.NIGHT_OWL_SCHEDULE, "a Night Owl miner isn't on the night schedule");
		helper.assertTrue(plainMiner.getBrain().getSchedule() != ModVillagers.NIGHT_OWL_SCHEDULE, "an ordinary miner is on the night schedule");
		helper.assertTrue(jobless.getBrain().getSchedule() != ModVillagers.NIGHT_OWL_SCHEDULE, "a jobless Night Owl works nights");
		helper.assertTrue(nitwit.getBrain().getSchedule() != ModVillagers.NIGHT_OWL_SCHEDULE, "a nitwit Night Owl works nights");
		helper.assertTrue(child.getBrain().getSchedule() != ModVillagers.NIGHT_OWL_SCHEDULE, "a child Night Owl works nights");
		Gifted.set(miner, null);
		helper.assertTrue(miner.getBrain().getSchedule() != ModVillagers.NIGHT_OWL_SCHEDULE, "the gift taken, still on the night schedule");
		helper.succeed();
	}

	/** A Night Owl miner saved and loaded back into the world is a Night Owl again and on the night schedule. */
	//$ gametest_ticks_batch AREA '400' '"qaNightOwlReload"'
	@GameTest(template = AREA, timeoutTicks = 400, batch = "qaNightOwlReload")
	public void qaNightOwlScheduleSurvivesASaveAndReload(GameTestHelper helper) {
		Leftovers.clear(helper);
		ServerLevel level = helper.getLevel();
		Villager owl = worker(helper, new BlockPos(3, 2, 3), ModVillagers.MINER);
		Gifted.set(owl, NIGHT_OWL);
		CompoundTag tag = new CompoundTag();
		helper.assertTrue(owl.save(tag), "not saved");
		owl.discard();
		Entity loaded = EntityType.loadEntityRecursive(tag, level, e -> e);
		helper.assertTrue(loaded instanceof Villager, "didn't load a villager: " + loaded);
		Villager back = (Villager) loaded;
		back.setNoAi(false); // a living villager, whose brain and ticks run as in a world
		helper.assertTrue(level.addFreshEntity(back), "couldn't add the loaded villager");
		helper.assertTrue(Gifted.of(back) != null && NIGHT_OWL.equals(Gifted.of(back).id()), "the gift after a reload: " + Gifted.of(back));
		helper.succeedWhen(() -> helper.assertTrue(back.getBrain().getSchedule() == ModVillagers.NIGHT_OWL_SCHEDULE,
			"the reloaded Night Owl isn't on the night schedule"));
	}

	// ---- The roll and the attachment ----

	/**
	 * The {@code GIFTED} attachment beats the UUID roll both ways (a rolled-Gifted villager set to none, an ungifted one
	 * set to Prodigy), and both survive a save and reload; a gift id no file has any more gives no gift and no crash.
	 */
	//$ gametest_batch AREA '"qaGiftedOverride"'
	@GameTest(template = AREA, batch = "qaGiftedOverride")
	public void qaGiftedAttachmentOverridesTheRollOverASave(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		UUID rolledGifted = null;
		UUID rolledPlain = null;
		RandomSource random = RandomSource.create(2967L);
		while (rolledGifted == null || rolledPlain == null) {
			UUID u = new UUID(random.nextLong(), random.nextLong());
			if (Gifted.roll(u) != null) {
				rolledGifted = rolledGifted == null ? u : rolledGifted;
			} else {
				rolledPlain = rolledPlain == null ? u : rolledPlain;
			}
		}
		boolean roll = Gifted.ROLL;
		try {
			Gifted.ROLL = true;
			Villager lucky = EntityType.VILLAGER.create(level);
			lucky.setUUID(rolledGifted);
			helper.assertTrue(Gifted.of(lucky) == Gifted.roll(rolledGifted), "with no attachment the roll should decide");
			ModAttachments.GIFTED.set(lucky, Gifted.NONE);
			helper.assertTrue(Gifted.of(lucky) == null, "set to none, still Gifted by the roll");
			Villager ordinary = EntityType.VILLAGER.create(level);
			ordinary.setUUID(rolledPlain);
			helper.assertTrue(Gifted.of(ordinary) == null, "staging: the plain UUID rolled a gift");
			ModAttachments.GIFTED.set(ordinary, PRODIGY.toString());
			for (Villager v : List.of(lucky, ordinary)) {
				CompoundTag tag = new CompoundTag();
				helper.assertTrue(v.save(tag), "not saved");
				Entity back = EntityType.loadEntityRecursive(tag, level, e -> e);
				helper.assertTrue(back instanceof Villager, "didn't load");
				Gifted.Gift was = Gifted.of(v);
				Gifted.Gift now = Gifted.of((Villager) back);
				helper.assertTrue(was == now, "after a reload the gift went from " + (was == null ? "none" : was.id()) + " to " + (now == null ? "none" : now.id()));
				back.discard();
			}
			helper.assertTrue(Gifted.of(ordinary) != null && PRODIGY.equals(Gifted.of(ordinary).id()), "the set Prodigy was lost");
			Villager gone = EntityType.VILLAGER.create(level);
			ModAttachments.GIFTED.set(gone, "aliveworkplace:a_gift_since_removed");
			helper.assertTrue(Gifted.of(gone) == null && Gifted.xpFactor(gone) == 1f && Gifted.tradeDiscount(gone) == 0, "a removed gift still does something");
			lucky.discard();
			ordinary.discard();
			gone.discard();
		} finally {
			Gifted.ROLL = roll;
		}
		helper.succeed();
	}

	/** {@code giftedChance} is "one villager in this many": 1 makes everyone Gifted, 2 about half, 30 fewer than 2. */
	//$ gametest 'FabricGameTest.EMPTY_STRUCTURE'
	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
	public void qaGiftedChanceIsOneInN(GameTestHelper helper) {
		int before = Gifted.CHANCE;
		try {
			List<UUID> ids = new ArrayList<>();
			RandomSource random = RandomSource.create(1234L);
			for (int i = 0; i < 2000; i++) {
				ids.add(new UUID(random.nextLong(), random.nextLong()));
			}
			int[] counts = new int[3];
			int[] chances = {1, 2, 30};
			for (int c = 0; c < 3; c++) {
				Gifted.CHANCE = chances[c];
				for (UUID u : ids) {
					counts[c] += Gifted.roll(u) != null ? 1 : 0;
				}
			}
			helper.assertTrue(counts[0] == 2000, "giftedChance 1: " + counts[0] + " of 2000 Gifted");
			// Half of 2000, spread about 22: 900-1100 is more than four of them.
			helper.assertTrue(counts[1] >= 900 && counts[1] <= 1100, "giftedChance 2: " + counts[1] + " of 2000 Gifted");
			helper.assertTrue(counts[2] < counts[1], "giftedChance 30 (" + counts[2] + ") isn't rarer than 2 (" + counts[1] + ")");
		} finally {
			Gifted.CHANCE = before;
		}
		helper.succeed();
	}

	// ---- Hardy ----

	/** Hardy's twice the health doesn't stack: the gift set twice and refreshed again is still twice, and another gift is once. */
	//$ gametest_batch AREA '"qaHardyStack"'
	@GameTest(template = AREA, batch = "qaHardyStack")
	public void qaHardyHealthIsTwiceNotMore(GameTestHelper helper) {
		Villager hardy = helper.spawn(EntityType.VILLAGER, new BlockPos(3, 2, 3));
		Villager plain = helper.spawn(EntityType.VILLAGER, new BlockPos(6, 2, 3));
		Gifted.set(plain, null);
		float base = plain.getMaxHealth();
		Gifted.set(hardy, HARDY);
		Gifted.set(hardy, HARDY);
		Gifted.refresh(hardy);
		Gifted.refresh(hardy);
		helper.assertTrue(hardy.getMaxHealth() == base * 2, "a Hardy villager given the gift twice has " + hardy.getMaxHealth() + ", a villager " + base);
		helper.assertTrue(hardy.getHealth() == base * 2, "a healthy Hardy villager's health is " + hardy.getHealth());
		Gifted.set(hardy, PRODIGY);
		helper.assertTrue(hardy.getMaxHealth() == base && hardy.getHealth() <= base, "a Prodigy now, health " + hardy.getHealth() + " of " + hardy.getMaxHealth());
		helper.succeed();
	}

	// ---- Beloved ----

	/** Beloved: a neighbour whose bed is exactly 16 blocks from hers is +5; one 17 away gets nothing. */
	//$ gametest_batch HUGE '"qaBelovedEdge"'
	@GameTest(template = HUGE, batch = "qaBelovedEdge")
	public void qaBelovedReachesSixteenBlocksNotSeventeen(GameTestHelper helper) {
		Leftovers.clear(helper);
		Leftovers.after(helper, () -> {
			GiftedAuras.forget();
			Moods.forget();
		});
		ServerLevel level = helper.getLevel();
		Villager beloved = worker(helper, new BlockPos(3, 2, 3), VillagerProfession.FARMER);
		Villager at16 = worker(helper, new BlockPos(6, 2, 3), VillagerProfession.FARMER);
		Villager at17 = worker(helper, new BlockPos(9, 2, 3), VillagerProfession.FARMER);
		Beds.home(beloved, helper.absolutePos(new BlockPos(3, 2, 20)));
		Beds.home(at16, helper.absolutePos(new BlockPos(19, 2, 20)));
		Beds.home(at17, helper.absolutePos(new BlockPos(20, 2, 20)));
		Gifted.set(at16, null);
		Gifted.set(at17, null);
		Moods.forget();
		int before16 = Moods.work(level, at16).score();
		int before17 = Moods.work(level, at17).score();
		Gifted.set(beloved, BELOVED);
		Moods.forget();
		Moods.Mood after16 = Moods.work(level, at16);
		Moods.Mood after17 = Moods.work(level, at17);
		helper.assertTrue(after16.good().stream().anyMatch(c -> key(c, "mood.aliveworkplace.reason.beloved")), "a bed 16 blocks off isn't a beloved neighbour: " + after16.good());
		helper.assertTrue(after16.score() - before16 == 5, "the neighbour 16 blocks off went from " + before16 + " to " + after16.score());
		helper.assertTrue(after17.good().stream().noneMatch(c -> key(c, "mood.aliveworkplace.reason.beloved")), "a bed 17 blocks off counted");
		helper.assertTrue(after17.score() == before17, "the neighbour 17 blocks off went from " + before17 + " to " + after17.score());
		helper.succeed();
	}

	// ---- Born Leader ----

	/**
	 * Born Leader: a farmer exactly 16 blocks off works faster, one 17 off doesn't; with the shared cap (maxWorkPace) at
	 * 105% the 10% is held to 5%; and eight Born Leaders together never pass the Legends' own cap.
	 */
	//$ gametest_batch HUGE '"qaBornLeaderEdge"'
	@GameTest(template = HUGE, batch = "qaBornLeaderEdge")
	public void qaBornLeaderSixteenNotSeventeenAndUnderTheSharedCap(GameTestHelper helper) {
		Leftovers.clear(helper);
		Leftovers.halls(helper);
		int maxPercent = Pace.MAX_PERCENT;
		Leftovers.after(helper, () -> {
			GiftedAuras.forget();
			Pace.MAX_PERCENT = maxPercent;
		});
		Villager leader = worker(helper, new BlockPos(3, 2, 3), VillagerProfession.FARMER);
		Villager at16 = worker(helper, new BlockPos(19, 2, 3), VillagerProfession.FARMER);
		Villager at17 = worker(helper, new BlockPos(20, 2, 3), VillagerProfession.FARMER);
		Gifted.set(at16, null);
		Gifted.set(at17, null);
		Pace.Breakdown before = Pace.of(at16);
		helper.assertTrue(before.faster().isEmpty(), "staging: the farmer already works faster: " + before.faster());
		Gifted.set(leader, BORN_LEADER);
		helper.assertTrue(Math.abs(GiftedAuras.pace(at16) - 1.1f) < 1e-5, "a farmer 16 blocks off: " + GiftedAuras.pace(at16));
		helper.assertTrue(GiftedAuras.pace(at17) == 1f, "a farmer 17 blocks off: " + GiftedAuras.pace(at17));
		try {
			Pace.MAX_PERCENT = 105;
			Pace.Breakdown capped = Pace.of(at16);
			float penalties = 1f;
			for (Pace.Part p : capped.slower()) {
				penalties *= p.factor();
			}
			float bonusPart = capped.factor() / penalties;
			helper.assertTrue(Math.abs(bonusPart - 100f / 105f) < 1e-4, "with maxWorkPace 105 the Born Leader's farmer works at time factor " + bonusPart);
			helper.assertTrue(capped.capped(), "not shown as capped");
		} finally {
			Pace.MAX_PERCENT = maxPercent;
		}
		// Eight Born Leaders round one farmer: 1.1^8 would be 2.14, held to the cap.
		Villager crowd = worker(helper, new BlockPos(8, 2, 12), VillagerProfession.FARMER);
		Gifted.set(crowd, null);
		for (int i = 0; i < 8; i++) {
			Villager l = worker(helper, new BlockPos(4 + i, 2, 9), VillagerProfession.FARMER);
			Gifted.set(l, BORN_LEADER);
		}
		float many = GiftedAuras.pace(crowd);
		helper.assertTrue(many <= LegendPowers.PACE_CAP + 1e-5 && many > 1.1f, "eight Born Leaders: " + many);
		float factor = Pace.of(crowd).factor();
		helper.assertTrue(factor >= Pace.cap() - 1e-5 || !Pace.of(crowd).slower().isEmpty(), "past the shared cap: time factor " + factor + ", cap " + Pace.cap());
		helper.succeed();
	}

	// ---- Born rolls and the sentences ----

	private static Villager child(GameTestHelper helper, BlockPos at, String name, boolean motherMaster, boolean fatherMaster) {
		Villager v = helper.spawn(EntityType.VILLAGER, at);
		v.setNoAi(true);
		v.setCustomName(Component.literal(name));
		ModAttachments.PARENTS.set(v, new Families.Parents(Component.literal("Dara"), Component.literal("Tom"), "minecraft:farmer", "minecraft:fisherman",
			false, motherMaster, fatherMaster));
		return v;
	}

	private static String line(JsonObject lang, VillageHallBlockEntity hall, String name) {
		// Her growing-up line: a grown child may also take up the family trade in the same round ("Wren took up the family
		// trade: Fisherman", when a free workstation of a parent's trade is near, as other tests' leftovers can be).
		return grownUp(lang, hall, name).reduce((a, b) -> b).orElse(null);
	}

	private static java.util.stream.Stream<String> grownUp(JsonObject lang, VillageHallBlockEntity hall, String name) {
		return hall.chronicle().stream().map(e -> render(lang, e.text())).filter(s -> s.startsWith(name + ", child of ") && s.contains(" has grown up"));
	}

	/**
	 * The born rolls need two schooled Masters: with only one, the gift die that makes a child of two Masters Gifted
	 * (seed 1) does nothing; with {@code giftedChance} 0 nobody grows up Gifted; a child rolls once, when they grow up,
	 * not again in the next round. And the chronicle reads, from en_us.json, "Wren, child of Dara and Tom, has grown up
	 * Gifted: Prodigy" (with her gift's name), and "…has grown up" for the others.
	 */
	//$ gametest_ticks_batch HUGE '100' '"qaBornRules"'
	@GameTest(template = HUGE, timeoutTicks = 100, batch = "qaBornRules")
	public void qaBornRollsNeedTwoSchooledMastersAndRollOnce(GameTestHelper helper) {
		Leftovers.clear(helper);
		BlockPos hallAt = new BlockPos(15, 2, 15);
		helper.setBlock(hallAt, ModBlocks.VILLAGE_HALL);
		JsonObject lang = QaAmericanSpellingGameTests.english();
		helper.runAfterDelay(2, () -> {
			ServerLevel level = helper.getLevel();
			BlockPos hall = helper.absolutePos(hallAt);
			VillageHallBlockEntity entity = (VillageHallBlockEntity) level.getBlockEntity(hall);

			// The control: two Masters, seed 1, Gifted.
			Villager wren = child(helper, new BlockPos(3, 2, 3), "Wren", true, true);
			Families.round(level, hall, wren, RandomSource.create(1));
			Gifted.Gift gift = Gifted.of(wren);
			helper.assertTrue(gift != null, "staging: seed 1 didn't make a child of two schooled Masters Gifted");
			String expected = "Wren, child of Dara and Tom, has grown up Gifted: " + render(lang, gift.title());
			String wrenLine = line(lang, entity, "Wren");
			helper.assertTrue(expected.equals(wrenLine), "the chronicle reads \"" + wrenLine + "\", expected \"" + expected + "\"");
			helper.assertTrue(!render(lang, gift.title()).startsWith("gifted."), "the gift's name isn't translated: " + render(lang, gift.title()));

			// One schooled Master and one not: nothing, with the same dice.
			for (boolean[] p : new boolean[][] {{true, false}, {false, true}}) {
				Villager one = child(helper, new BlockPos(6, 2, 3), "Ola", p[0], p[1]);
				Families.round(level, hall, one, RandomSource.create(1));
				helper.assertTrue(!ModAttachments.LEGEND.has(one) && Gifted.of(one) == null, "a child of one schooled Master grew up Gifted (" + p[0] + ", " + p[1] + ")");
				one.discard();
			}

			// giftedChance 0: nobody grows up Gifted.
			int chance = Gifted.CHANCE;
			try {
				Gifted.CHANCE = 0;
				Villager kit = child(helper, new BlockPos(9, 2, 3), "Kit", true, true);
				Families.round(level, hall, kit, RandomSource.create(1));
				Gifted.CHANCE = chance;
				helper.assertTrue(Gifted.of(kit) == null, "a child grew up Gifted with giftedChance 0 (and kept it after): " + Gifted.of(kit));
			} finally {
				Gifted.CHANCE = chance;
			}

			// Neither, then the next round with the dice that would have made them Gifted: still nothing.
			Villager cal = child(helper, new BlockPos(12, 2, 3), "Cal", true, true);
			Families.round(level, hall, cal, RandomSource.create(2));
			helper.assertTrue(Gifted.of(cal) == null, "staging: seed 2 made Cal Gifted");
			helper.assertTrue("Cal, child of Dara and Tom, has grown up".equals(line(lang, entity, "Cal")), "Cal's line: " + line(lang, entity, "Cal"));
			Families.round(level, hall, cal, RandomSource.create(1));
			helper.assertTrue(Gifted.of(cal) == null, "a grown child was rolled for again in a later round");
			helper.assertTrue(grownUp(lang, entity, "Cal").count() == 1, "a later round wrote another growing-up line: " + grownUp(lang, entity, "Cal").toList());
			helper.succeed();
		});
	}

	/**
	 * The sentences players read, rendered from en_us.json: every gift's name and description exist; the hall's gold line
	 * "Gifted: Prodigy (learns three times as fast)"; a Beloved neighbour's mood reason; the inn's twice-the-price line;
	 * and the spec's numbers (20%, 16 blocks, 10%, three times) in the descriptions.
	 */
	//$ gametest_batch HUGE '"qaGiftedSentences"'
	@GameTest(template = HUGE, batch = "qaGiftedSentences")
	public void qaGiftedSentencesReadRight(GameTestHelper helper) {
		Leftovers.clear(helper);
		Leftovers.after(helper, () -> {
			GiftedAuras.forget();
			Moods.forget();
		});
		ServerLevel level = helper.getLevel();
		JsonObject lang = QaAmericanSpellingGameTests.english();
		List<String> missing = new ArrayList<>();
		for (Gifted.Gift g : Gifted.all().values()) {
			for (Component c : List.of(g.title(), g.describe())) {
				String k = ((TranslatableContents) c.getContents()).getKey();
				if (!lang.has(k) || lang.get(k).getAsString().isBlank()) {
					missing.add(k);
				}
			}
		}
		helper.assertTrue(missing.isEmpty(), "gift text missing from en_us.json: " + missing);
		helper.assertTrue("Gifted: twice the usual price".equals(lang.get("screen.aliveworkplace.inn.gifted_price").getAsString()), "the inn's line");
		String[][] numbers = {{"prodigy", "three times"}, {"silver_tongue", "20%"}, {"beloved", "16 blocks"}, {"born_leader", "10%"}, {"born_leader", "16 blocks"},
			{"hardy", "twice"}, {"lucky", "+3"}};
		for (String[] n : numbers) {
			String desc = render(lang, Gifted.get(id(n[0])).describe());
			helper.assertTrue(desc.contains(n[1]), n[0] + "'s description \"" + desc + "\" doesn't say " + n[1]);
		}

		Villager prodigy = worker(helper, new BlockPos(3, 2, 3), VillagerProfession.FARMER);
		Gifted.set(prodigy, PRODIGY);
		String hallLine = render(lang, Gifted.hallLine(prodigy));
		helper.assertTrue("Gifted: Prodigy (learns three times as fast)".equals(hallLine), "the hall's line: \"" + hallLine + "\"");

		Villager ada = worker(helper, new BlockPos(6, 2, 3), VillagerProfession.FARMER);
		ada.setCustomName(Component.literal("Ada"));
		Villager near = worker(helper, new BlockPos(9, 2, 3), VillagerProfession.FARMER);
		Beds.home(ada, helper.absolutePos(new BlockPos(6, 2, 20)));
		Beds.home(near, helper.absolutePos(new BlockPos(12, 2, 20)));
		Gifted.set(near, null);
		Gifted.set(ada, BELOVED);
		Moods.forget();
		String reason = Moods.work(level, near).good().stream().filter(c -> key(c, "mood.aliveworkplace.reason.beloved")).map(c -> render(lang, c)).findFirst().orElse(null);
		helper.assertTrue(reason != null && reason.startsWith("a beloved neighbor, Ada"), "the mood reason reads \"" + reason + "\"");
		helper.succeed();
	}
}
