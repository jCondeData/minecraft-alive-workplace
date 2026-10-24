package io.github.jcondedata.aliveworkplace.test;

import com.google.gson.JsonParser;
import io.github.jcondedata.aliveworkplace.hall.CivicEffects;
import io.github.jcondedata.aliveworkplace.hall.VillageHallBlockEntity;
import io.github.jcondedata.aliveworkplace.hall.VillageHalls;
import io.github.jcondedata.aliveworkplace.hall.VillageQuests;
import io.github.jcondedata.aliveworkplace.hall.VillageRanks;
import io.github.jcondedata.aliveworkplace.mail.PostOffice;
import io.github.jcondedata.aliveworkplace.mixin.VillagerAccessor;
import io.github.jcondedata.aliveworkplace.people.Families;
import io.github.jcondedata.aliveworkplace.registry.ModBlocks;
import io.github.jcondedata.aliveworkplace.registry.ModVillagers;
import io.github.jcondedata.aliveworkplace.rules.Condition;
import io.github.jcondedata.aliveworkplace.rules.Conditions;
import io.github.jcondedata.aliveworkplace.story.FriendPerks;
import io.github.jcondedata.aliveworkplace.story.Friendship;
import io.github.jcondedata.aliveworkplace.story.Gifts;
import io.github.jcondedata.aliveworkplace.story.Reputation;
import io.github.jcondedata.aliveworkplace.story.Reputation.Title;
import io.github.jcondedata.aliveworkplace.story.Stories;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.locale.Language;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.animal.IronGolem;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.npc.VillagerProfession;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.alchemy.PotionContents;
import net.minecraft.world.item.alchemy.Potions;
import net.minecraft.world.item.trading.ItemCost;
import net.minecraft.world.item.trading.MerchantOffer;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * QA (adversarial, written from the spec) for ROADMAP 31.11 "Reputation and titles" and 31.12 "What friendship and
 * titles are worth": every title threshold crossed from both sides through {@code Reputation.add}; who hears what
 * (real connections whose chat is read back); the gift's day cap per village, the next day and a second village; a
 * Lord brought down by real harm; the chat decoration for a Lord and its switches; the price at every title step with
 * few hearts, the cap, vanilla's Hero of the Village under it, and a title elsewhere not counting; festival gifts per
 * friend, per player, per festival and per job family; the named baby once per player per village (and saved); the
 * weekly parcel at the week's edge; {@code title_at_least} and {@code hearts_at_least} at their edges; the say in
 * edicts as titles come and go; the perks with {@code reputation} off; and the sentences a player reads.
 */
public class QaReputationPerksGameTests implements FabricGameTest {
	static final String AREA = HeartEventGameTests.AREA;
	static final BlockPos HALL = HeartEventGameTests.HALL;
	/** A second village (Brackwater), with the radius at 4 so the two don't overlap. */
	static final BlockPos SECOND = new BlockPos(1, 2, 1);

	// --- Setting up -----------------------------------------------------------------------------------------------

	/** Thornholm at {@link #HALL}, reputation on, nobody known there. */
	private static void one(GameTestHelper helper, Runnable then) {
		ReputationGameTests.village(helper, then);
	}

	/** Thornholm at {@link #HALL} and Brackwater at {@link #SECOND}, radius 4, reputation on in both. */
	private static void two(GameTestHelper helper, Runnable then) {
		HeartEventGameTests.village(helper, () -> {
			VillageHalls.RADIUS = 4; // (HeartEventGameTests.village puts it back)
			helper.setBlock(SECOND, ModBlocks.VILLAGE_HALL);
			helper.runAfterDelay(2, () -> {
				CivicEffects.forget();
				ReputationGameTests.reputation(helper, HALL, "Thornholm");
				ReputationGameTests.reputation(helper, SECOND, "Brackwater");
				then.run();
			});
		});
	}

	private static ServerPlayer mock(GameTestHelper helper, BlockPos at) {
		return ReputationGameTests.player(helper, at);
	}

	/** A player on a real connection whose chat lines are read back, standing at {@code at}. */
	private static QaStageFiveGameTests.Client client(GameTestHelper helper, BlockPos at) {
		QaStageFiveGameTests.Client c = QaStageSixGameTests.client(helper, GameType.SURVIVAL, QaStageSixGameTests.name("rp"));
		Vec3 pos = helper.absoluteVec(Vec3.atBottomCenterOf(at));
		c.player().moveTo(pos.x, pos.y, pos.z, 0, 0);
		return c;
	}

	private static String name(QaStageFiveGameTests.Client c) {
		return c.player().getGameProfile().getName();
	}

	private static long count(QaStageFiveGameTests.Client c, String line) {
		return c.seen().stream().filter(m -> m.getString().equals(line)).count();
	}

	private static boolean saw(QaStageFiveGameTests.Client c, String line) {
		return count(c, line) > 0;
	}

	private static List<String> seen(QaStageFiveGameTests.Client c) {
		return c.seen().stream().map(Component::getString).toList();
	}

	private static Villager worker(GameTestHelper helper, BlockPos at, String name, VillagerProfession job) {
		Villager v = HeartEventGameTests.villager(helper, at, name);
		v.setVillagerData(v.getVillagerData().setProfession(job).setLevel(2));
		return v;
	}

	/** Exactly {@code points} friendship points between {@code v} and {@code player} (100 a heart). */
	private static void friendship(Villager v, ServerPlayer player, int points) {
		Friendship.add(v, player, points - Friendship.points(v, player.getUUID()));
	}

	private static int points(GameTestHelper helper, BlockPos at, ServerPlayer player) {
		return Reputation.points(helper.getLevel(), helper.absolutePos(at), player.getUUID());
	}

	private static Title title(GameTestHelper helper, BlockPos at, ServerPlayer player) {
		return Reputation.title(helper.getLevel(), helper.absolutePos(at), player.getUUID());
	}

	private static VillageHallBlockEntity hall(GameTestHelper helper, BlockPos at) {
		return (VillageHallBlockEntity) helper.getLevel().getBlockEntity(helper.absolutePos(at));
	}

	/** Runs {@code r} with the level's clock {@code days} later, and puts the clock back in the same tick (other batches share it). */
	private static void later(ServerLevel level, long days, Runnable r) {
		long time = level.getDayTime();
		level.setDayTime(time + days * 24000L);
		try {
			r.run();
		} finally {
			level.setDayTime(time);
		}
	}

	/** The price of {@code v}'s offer {@code i} for {@code player} as the trade screen sets it. */
	private static int price(Villager v, ServerPlayer player, int i) {
		v.getOffers().forEach(MerchantOffer::resetSpecialPriceDiff);
		((VillagerAccessor) v).aliveworkplace$updateSpecialPrices(player);
		int price = v.getOffers().get(i).getCostA().getCount();
		v.getOffers().forEach(MerchantOffer::resetSpecialPriceDiff);
		return price;
	}

	private static Gifts.Result give(ServerPlayer player, Villager villager) {
		List<Item> good = List.of(Items.CAKE, Items.BREAD, Items.EMERALD, Items.COOKIE, Items.APPLE, Items.DIAMOND, Items.BOOK, Items.GOLD_INGOT);
		Item liked = good.stream().filter(item -> Gifts.band(villager, new ItemStack(item)).points > 0).findFirst().orElseThrow();
		player.setItemInHand(InteractionHand.MAIN_HAND, Gifts.wrap(new ItemStack(liked), player.getGameProfile().getName()));
		return Gifts.give(player, villager, player.getMainHandItem());
	}

	private static List<ItemStack> atFeet(ServerLevel level, ServerPlayer player) {
		return level.getEntitiesOfClass(ItemEntity.class, new AABB(player.blockPosition()).inflate(1.5)).stream().map(ItemEntity::getItem).toList();
	}

	private static void clearFeet(ServerLevel level, ServerPlayer player) {
		level.getEntitiesOfClass(ItemEntity.class, new AABB(player.blockPosition()).inflate(1.5)).forEach(ItemEntity::discard);
	}

	private static Villager born(GameTestHelper helper, Villager mother, Villager father) {
		Villager baby = EntityType.VILLAGER.create(helper.getLevel());
		baby.moveTo(mother.getX(), mother.getY(), mother.getZ(), 0, 0);
		baby.setAge(-24000);
		baby.setNoAi(true);
		Families.born(baby, mother, father);
		helper.getLevel().addFreshEntity(baby);
		return baby;
	}

	private static String babyName(Villager baby) {
		return baby.hasCustomName() ? baby.getCustomName().getString() : "";
	}

	// --- 31.11: titles --------------------------------------------------------------------------------------------

	/**
	 * In a Town, every threshold crossed one point at a time, up and back down: 49 Stranger, 50 Friend, 299 Friend,
	 * 300 Hero, 999 Hero, 1000 Lord, and the same on the way down. Each new title is announced once, none of the losses.
	 * In a Village 1000 (or 10000) is a Hero, never a Lord, until it is a Town.
	 */
	//$ gametest_ticks_batch AREA '60' '"qaRepThresholds"'
	@GameTest(template = AREA, timeoutTicks = 60, batch = "qaRepThresholds")
	public void everyThresholdFromBothSides(GameTestHelper helper) {
		one(helper, () -> {
			ServerLevel level = helper.getLevel();
			BlockPos hall = helper.absolutePos(HALL);
			VillageHallBlockEntity entity = hall(helper, HALL);
			entity.setRank(VillageRanks.Rank.TOWN);
			ServerPlayer me = mock(helper, HALL.south(2));
			int told = Reputation.ANNOUNCED.get();
			Object[][] steps = {
				{49, Title.STRANGER, false}, {1, Title.FRIEND, true}, {249, Title.FRIEND, false}, {1, Title.HERO, true},
				{699, Title.HERO, false}, {1, Title.LORD, true},
				{-1, Title.HERO, false}, {-699, Title.HERO, false}, {-1, Title.FRIEND, false}, {-249, Title.FRIEND, false},
				{-1, Title.STRANGER, false}};
			int total = 0;
			for (Object[] step : steps) {
				int by = (Integer) step[0];
				total += by;
				Reputation.Change c = Reputation.add(level, hall, me, by);
				helper.assertTrue(c.after() == total && c.now() == step[1] && c.announced() == (Boolean) step[2] && title(helper, HALL, me) == step[1],
					"at " + total + " (by " + by + "): " + c + ", expected " + step[1] + (((Boolean) step[2]) ? " announced" : ""));
				if (by == -1) {
					String expected = c.now() == Title.STRANGER
						? "one: You are no longer a Friend of Thornholm. You are a stranger there again."
						: "one: You are no longer a " + (c.now() == Title.HERO ? "Lord" : "Hero") + " of Thornholm. They know you as a " + c.now().name().charAt(0)
							+ c.now().name().substring(1).toLowerCase() + " now.";
					helper.assertTrue(ReputationGameTests.lastTold().equals(expected), "the loss at " + total + ": " + ReputationGameTests.lastTold());
				}
			}
			helper.assertTrue(Reputation.ANNOUNCED.get() == told + 3, "announcements: " + (Reputation.ANNOUNCED.get() - told) + ", expected 3");
			// The Lord's Town rule: 1000 and even 10000 in a Village is a Hero.
			entity.setRank(VillageRanks.Rank.VILLAGE);
			ServerPlayer other = mock(helper, HALL.south(3));
			Reputation.Change big = Reputation.add(level, hall, other, 1000);
			helper.assertTrue(big.now() == Title.HERO && big.announced(), "1000 in a Village: " + big);
			helper.assertTrue(Reputation.add(level, hall, other, 9000).now() == Title.HERO && title(helper, HALL, other) == Title.HERO,
				"10000 in a Village: " + title(helper, HALL, other));
			helper.assertTrue(ReputationGameTests.lastTold().equals("all: " + HeartEventGameTests.ME + " is now a Hero of Thornholm!"),
				"told: " + ReputationGameTests.lastTold());
			// A jump from Stranger straight to Lord (a Town) is one announcement, of the Lord.
			entity.setRank(VillageRanks.Rank.TOWN);
			ServerPlayer third = mock(helper, HALL.south(4));
			int before = Reputation.ANNOUNCED.get();
			Reputation.Change jump = Reputation.add(level, hall, third, 1200);
			helper.assertTrue(jump.now() == Title.LORD && Reputation.ANNOUNCED.get() == before + 1
				&& ReputationGameTests.lastTold().equals("all: " + HeartEventGameTests.ME + " is now a Lord of Thornholm!"), "the jump: " + jump + ", " + ReputationGameTests.lastTold());
			helper.succeed();
		});
	}

	/**
	 * Through real connections: a new title is told to every player (once, however often it is lost and won back), the
	 * action bar shows the change with its numbers, and a lost or won-back title is told only to that player.
	 */
	//$ gametest_ticks_batch AREA '60' '"qaRepWhoHears"'
	@GameTest(template = AREA, timeoutTicks = 60, batch = "qaRepWhoHears")
	public void newsForEveryoneLossesForOne(GameTestHelper helper) {
		one(helper, () -> {
			ServerLevel level = helper.getLevel();
			BlockPos hall = helper.absolutePos(HALL);
			QaStageFiveGameTests.Client a = client(helper, HALL.south(2));
			QaStageFiveGameTests.Client b = client(helper, HALL.south(3));
			String news = name(a) + " is now a Friend of Thornholm!";
			Reputation.add(level, hall, a.player(), 50);
			helper.assertTrue(count(b, news) == 1 && count(a, news) == 1, "the news to both: a " + seen(a) + ", b " + seen(b));
			helper.assertTrue(saw(a, "+50 standing in Thornholm (now 50)"), "a's action bar: " + seen(a));
			helper.assertTrue(!saw(b, "+50 standing in Thornholm (now 50)"), "b saw a's action bar");
			for (int i = 0; i < 3; i++) {
				Reputation.add(level, hall, a.player(), -1);
				Reputation.add(level, hall, a.player(), 1);
			}
			String lost = "You are no longer a Friend of Thornholm. You are a stranger there again.";
			String again = "You are a Friend of Thornholm once more.";
			helper.assertTrue(saw(a, lost) && saw(a, again) && saw(a, "-1 standing in Thornholm (now 49)"), "a's own lines: " + seen(a));
			helper.assertTrue(!saw(b, lost) && !saw(b, again) && b.seen().stream().noneMatch(m -> m.getString().contains("standing in")),
				"b was told of a's loss: " + seen(b));
			helper.assertTrue(count(b, news) == 1 && count(a, news) == 1, "the news told again: " + count(b, news));
			// b's own first title is news to a.
			Reputation.add(level, hall, b.player(), 75);
			helper.assertTrue(saw(a, name(b) + " is now a Friend of Thornholm!"), "a didn't hear b's news: " + seen(a));
			for (String line : seen(a)) {
				helper.assertFalse(line.contains("aliveworkplace.") || line.contains("%"), "a raw line: " + line);
			}
			helper.succeed();
		});
	}

	/**
	 * A gift is worth 2, at most 10 a day per village: the sixth gift of the day gives nothing, a gift in a second
	 * village counts there (its own cap), and the next day counts again. Coming to a festival: 5, once a festival day,
	 * and again the next festival day.
	 */
	//$ gametest_ticks_batch AREA '100' '"qaRepGiftCap"'
	@GameTest(template = AREA, timeoutTicks = 100, batch = "qaRepGiftCap")
	public void giftCapPerDayPerVillage(GameTestHelper helper) {
		two(helper, () -> {
			ServerLevel level = helper.getLevel();
			BlockPos hall = helper.absolutePos(HALL);
			BlockPos second = helper.absolutePos(SECOND);
			ServerPlayer me = mock(helper, HALL.south(2));
			BlockPos[] at = {new BlockPos(7, 2, 7), new BlockPos(8, 2, 7), new BlockPos(10, 2, 7), new BlockPos(11, 2, 7), new BlockPos(7, 2, 9), new BlockPos(11, 2, 9)};
			for (int i = 0; i < at.length; i++) {
				Villager v = HeartEventGameTests.villager(helper, at[i], "Thorn" + i);
				helper.assertTrue(CivicEffects.hallOf(v) != null && CivicEffects.hallOf(v).getBlockPos().equals(hall), "Thorn" + i + " isn't of Thornholm");
				Gifts.Result r = give(me, v);
				helper.assertTrue(r.outcome() == Gifts.Outcome.GIVEN, "gift " + (i + 1) + ": " + r.outcome());
				int want = Math.min(10, 2 * (i + 1));
				helper.assertTrue(points(helper, HALL, me) == want, "after gift " + (i + 1) + ": " + points(helper, HALL, me) + ", expected " + want);
			}
			// A second village: its own cap.
			Villager brack = HeartEventGameTests.villager(helper, new BlockPos(3, 2, 3), "Brack");
			helper.assertTrue(CivicEffects.hallOf(brack) != null && CivicEffects.hallOf(brack).getBlockPos().equals(second), "Brack isn't of Brackwater");
			helper.assertTrue(give(me, brack).outcome() == Gifts.Outcome.GIVEN, "Brackwater's gift wasn't taken");
			helper.assertTrue(points(helper, SECOND, me) == 2 && points(helper, HALL, me) == 10,
				"a gift in a second village: Brackwater " + points(helper, SECOND, me) + ", Thornholm " + points(helper, HALL, me));
			// The festival: 5 once, nothing the second time the same day.
			Reputation.onFestival(level, hall);
			Reputation.onFestival(level, hall);
			helper.assertTrue(points(helper, HALL, me) == 15, "the festival: " + points(helper, HALL, me));
			// The next day: gifts count again, and the next festival day does too.
			Villager next = HeartEventGameTests.villager(helper, new BlockPos(7, 2, 11), "Thorn6");
			Gifts.Result[] r = new Gifts.Result[1];
			later(level, 1, () -> {
				r[0] = give(me, next);
				Reputation.onFestival(level, hall);
			});
			helper.assertTrue(r[0].outcome() == Gifts.Outcome.GIVEN, "the next day's gift: " + r[0].outcome());
			helper.assertTrue(points(helper, HALL, me) == 22, "the next day (15 + 2 + 5): " + points(helper, HALL, me));
			helper.succeed();
		});
	}

	/**
	 * A Lord brought down by real harm: a hit (−10) takes 1000 to 990 and the title to Hero, told only to that player;
	 * a kill costs 150 (not 160), a guard 200, a golem 100. The other player hears none of it and keeps their own.
	 */
	//$ gametest_ticks_batch AREA '60' '"qaRepLordFalls"'
	@GameTest(template = AREA, timeoutTicks = 60, batch = "qaRepLordFalls")
	public void aLordBroughtDownByHarm(GameTestHelper helper) {
		one(helper, () -> {
			ServerLevel level = helper.getLevel();
			BlockPos hall = helper.absolutePos(HALL);
			hall(helper, HALL).setRank(VillageRanks.Rank.TOWN);
			QaStageFiveGameTests.Client a = client(helper, HALL.south(2));
			QaStageFiveGameTests.Client b = client(helper, HALL.south(3));
			Reputation.add(level, hall, a.player(), 1000);
			Reputation.add(level, hall, b.player(), 60);
			helper.assertTrue(title(helper, HALL, a.player()) == Title.LORD, "not a Lord: " + title(helper, HALL, a.player()));
			int told = Reputation.ANNOUNCED.get();
			Villager dara = HeartEventGameTests.villager(helper, new BlockPos(5, 2, 5), "Dara");
			dara.hurt(level.damageSources().playerAttack(a.player()), 1f);
			helper.assertTrue(dara.isAlive() && points(helper, HALL, a.player()) == 990 && title(helper, HALL, a.player()) == Title.HERO,
				"after a hit: " + points(helper, HALL, a.player()) + " " + title(helper, HALL, a.player()));
			String lost = "You are no longer a Lord of Thornholm. They know you as a Hero now.";
			helper.assertTrue(saw(a, lost), "a wasn't told: " + seen(a));
			helper.assertTrue(!saw(b, lost) && Reputation.TOLD.peekLast().to() != null && Reputation.TOLD.peekLast().to().equals(a.player().getUUID()),
				"the loss went beyond a: " + seen(b));
			Villager olan = HeartEventGameTests.villager(helper, new BlockPos(6, 2, 5), "Olan");
			olan.hurt(level.damageSources().playerAttack(a.player()), 1000f);
			helper.assertTrue(!olan.isAlive() && points(helper, HALL, a.player()) == 840, "a villager killed: " + points(helper, HALL, a.player()));
			Villager guard = HeartEventGameTests.villager(helper, new BlockPos(7, 2, 5), "Bram");
			guard.setVillagerData(guard.getVillagerData().setProfession(ModVillagers.GUARD));
			guard.hurt(level.damageSources().playerAttack(a.player()), 1000f);
			helper.assertTrue(!guard.isAlive() && points(helper, HALL, a.player()) == 640, "a guard killed: " + points(helper, HALL, a.player()));
			IronGolem golem = helper.spawn(EntityType.IRON_GOLEM, new BlockPos(12, 2, 5));
			golem.hurt(level.damageSources().playerAttack(a.player()), 1000f);
			helper.assertTrue(!golem.isAlive() && points(helper, HALL, a.player()) == 540 && title(helper, HALL, a.player()) == Title.HERO,
				"a golem killed: " + points(helper, HALL, a.player()));
			helper.assertTrue(points(helper, HALL, b.player()) == 60 && title(helper, HALL, b.player()) == Title.FRIEND, "b paid: " + points(helper, HALL, b.player()));
			helper.assertTrue(b.seen().stream().noneMatch(m -> m.getString().contains("no longer") || m.getString().startsWith("-")), "b heard a's losses: " + seen(b));
			helper.assertTrue(Reputation.ANNOUNCED.get() == told, "a loss was announced");
			helper.succeed();
		});
	}

	/** In chat: "[Lord of Thornholm]" for a Lord, "[Hero of …]" once the Town shrinks; nothing with either switch off. */
	//$ gametest_ticks_batch AREA '60' '"qaRepChatLord"'
	@GameTest(template = AREA, timeoutTicks = 60, batch = "qaRepChatLord")
	public void chatShowsTheLordAndHonoursTheSwitches(GameTestHelper helper) {
		one(helper, () -> {
			ServerLevel level = helper.getLevel();
			BlockPos hall = helper.absolutePos(HALL);
			VillageHallBlockEntity entity = hall(helper, HALL);
			entity.setRank(VillageRanks.Rank.TOWN);
			ServerPlayer me = mock(helper, HALL.south(2));
			Component hi = Component.literal("hi there");
			Reputation.add(level, hall, me, 1000);
			String lord = level.getServer().getChatDecorator().decorate(me, hi).getString();
			helper.assertTrue(lord.equals("[Lord of Thornholm] hi there"), "a Lord's line: " + lord);
			entity.setRank(VillageRanks.Rank.VILLAGE);
			VillageQuests.tick(level, hall, entity);
			String hero = level.getServer().getChatDecorator().decorate(me, hi).getString();
			helper.assertTrue(hero.equals("[Hero of Thornholm] hi there"), "after the Town shrank: " + hero);
			Reputation.CHAT = false;
			helper.assertTrue(level.getServer().getChatDecorator().decorate(me, hi).getString().equals("hi there"), "titlesInChat off");
			Reputation.CHAT = true;
			Reputation.ENABLED = false;
			helper.assertTrue(level.getServer().getChatDecorator().decorate(me, hi).getString().equals("hi there"), "reputation off");
			Reputation.ENABLED = true;
			helper.assertTrue(level.getServer().getChatDecorator().decorate(me, hi).getString().equals("[Hero of Thornholm] hi there"), "back on");
			helper.succeed();
		});
	}

	// --- 31.12: prices --------------------------------------------------------------------------------------------

	/**
	 * Through the trade screen's own price update, a 20-emerald trade: each title alone (5, 10, 15%), a Lord with 4 to 9
	 * hearts (20% to the 40% cap), a Hero and a Friend with 3 and 4 hearts; a 2-emerald trade never under 1; vanilla's
	 * Hero of the Village under ours, never under 1 and still cheaper; a Lord of another village and another player get
	 * nothing here.
	 */
	//$ gametest_ticks_batch AREA '80' '"qaRepPrices"'
	@GameTest(template = AREA, timeoutTicks = 80, batch = "qaRepPrices")
	public void priceAtEveryTitleStep(GameTestHelper helper) {
		two(helper, () -> {
			ServerLevel level = helper.getLevel();
			BlockPos hall = helper.absolutePos(HALL);
			BlockPos second = helper.absolutePos(SECOND);
			VillageHallBlockEntity entity = hall(helper, HALL);
			ServerPlayer me = mock(helper, HALL.south(2));
			ServerPlayer other = mock(helper, HALL.south(3).east());
			Villager dara = worker(helper, new BlockPos(11, 2, 9), "Dara", VillagerProfession.FARMER);
			helper.assertTrue(CivicEffects.hallOf(dara) != null && CivicEffects.hallOf(dara).getBlockPos().equals(hall), "Dara isn't of Thornholm");
			dara.getOffers().clear();
			dara.getOffers().add(new MerchantOffer(new ItemCost(Items.EMERALD, 20), new ItemStack(Items.BREAD), 12, 1, 0.05f));
			dara.getOffers().add(new MerchantOffer(new ItemCost(Items.EMERALD, 2), new ItemStack(Items.APPLE), 12, 1, 0.05f));
			helper.assertTrue(price(dara, me, 0) == 20, "a Stranger with no hearts: " + price(dara, me, 0));
			Reputation.add(level, hall, me, 50);
			helper.assertTrue(price(dara, me, 0) == 19, "a Friend alone (5%): " + price(dara, me, 0));
			Reputation.add(level, hall, me, 250);
			helper.assertTrue(price(dara, me, 0) == 18, "a Hero alone (10%): " + price(dara, me, 0));
			entity.setRank(VillageRanks.Rank.TOWN);
			Reputation.add(level, hall, me, 700);
			helper.assertTrue(title(helper, HALL, me) == Title.LORD && price(dara, me, 0) == 17, "a Lord alone (15%): " + price(dara, me, 0));
			int[] lordWith = {16, 15, 14, 13, 12, 12}; // 4..9 hearts: 20, 25, 30, 35, 40, 40 (cap)
			for (int h = 4; h <= 9; h++) {
				friendship(dara, me, h * 100);
				helper.assertTrue(price(dara, me, 0) == lordWith[h - 4], "a Lord with " + h + " hearts: " + price(dara, me, 0) + ", expected " + lordWith[h - 4]);
			}
			helper.assertTrue(price(dara, me, 1) == 1, "a 2-emerald trade at 40%: " + price(dara, me, 1));
			// Vanilla's Hero of the Village (V) first, ours on top: never under 1, and cheaper than vanilla's alone.
			me.addEffect(new MobEffectInstance(MobEffects.HERO_OF_THE_VILLAGE, 600, 4));
			int both = price(dara, me, 0);
			int cheap = price(dara, me, 1);
			me.removeEffect(MobEffects.HERO_OF_THE_VILLAGE);
			other.addEffect(new MobEffectInstance(MobEffects.HERO_OF_THE_VILLAGE, 600, 4));
			int vanilla = price(dara, other, 0);
			other.removeEffect(MobEffects.HERO_OF_THE_VILLAGE);
			helper.assertTrue(both >= 1 && cheap >= 1 && both < vanilla, "with Hero of the Village: ours " + both + " (vanilla alone " + vanilla + "), cheap " + cheap);
			// The title falls with the Town: a Hero with 4 hearts 15% (17), with 3 hearts 10% (18).
			entity.setRank(VillageRanks.Rank.VILLAGE);
			VillageQuests.tick(level, hall, entity);
			friendship(dara, me, 400);
			helper.assertTrue(title(helper, HALL, me) == Title.HERO && price(dara, me, 0) == 17, "a Hero with 4 hearts: " + price(dara, me, 0));
			friendship(dara, me, 399);
			helper.assertTrue(price(dara, me, 0) == 18, "a Hero with 3 hearts: " + price(dara, me, 0));
			// A Friend with 4 hearts: 10% (18); with 3: 5% (19).
			Reputation.add(level, hall, me, 100 - Reputation.points(level, hall, me.getUUID()));
			friendship(dara, me, 400);
			helper.assertTrue(title(helper, HALL, me) == Title.FRIEND && price(dara, me, 0) == 18, "a Friend with 4 hearts: " + price(dara, me, 0));
			friendship(dara, me, 300);
			helper.assertTrue(price(dara, me, 0) == 19, "a Friend with 3 hearts: " + price(dara, me, 0));
			// A Lord of Brackwater has no title in Thornholm, and the other player has no friendship with Dara.
			hall(helper, SECOND).setRank(VillageRanks.Rank.TOWN);
			Reputation.add(level, second, other, 1000);
			helper.assertTrue(Reputation.title(level, second, other.getUUID()) == Title.LORD && price(dara, other, 0) == 20,
				"a Lord of another village: " + price(dara, other, 0));
			helper.succeed();
		});
	}

	// --- 31.12: festival gifts ------------------------------------------------------------------------------------

	/**
	 * At a festival every villager with 6 hearts brings one gift to each player they're that fond of: two friends, two
	 * gifts; one shared friend gives the other player theirs too; 5 hearts gives nothing; a second call the same day
	 * nothing; the next festival day again. The chat line names the giver and the gift.
	 */
	//$ gametest_ticks_batch AREA '80' '"qaRepFestivalGifts"'
	@GameTest(template = AREA, timeoutTicks = 80, batch = "qaRepFestivalGifts")
	public void oneGiftPerFriendPerFestival(GameTestHelper helper) {
		one(helper, () -> {
			ServerLevel level = helper.getLevel();
			BlockPos hall = helper.absolutePos(HALL);
			QaStageFiveGameTests.Client a = client(helper, new BlockPos(3, 2, 3));
			QaStageFiveGameTests.Client b = client(helper, new BlockPos(14, 2, 14));
			Villager dara = worker(helper, new BlockPos(7, 2, 6), "Dara", VillagerProfession.MASON);
			Villager bo = worker(helper, new BlockPos(9, 2, 6), "Bo", VillagerProfession.FARMER);
			Villager cy = worker(helper, new BlockPos(11, 2, 6), "Cy", VillagerProfession.LIBRARIAN);
			friendship(dara, a.player(), 600);
			friendship(bo, a.player(), 790);
			friendship(bo, b.player(), 600);
			friendship(cy, a.player(), 599);
			friendship(cy, b.player(), 599);
			clearFeet(level, a.player());
			clearFeet(level, b.player());
			int toA = FriendPerks.festivalGifts(level, hall, a.player());
			int toB = FriendPerks.festivalGifts(level, hall, b.player());
			helper.assertTrue(toA == 2 && toB == 1, "gifts: a " + toA + ", b " + toB);
			List<ItemStack> aGot = atFeet(level, a.player());
			List<ItemStack> bGot = atFeet(level, b.player());
			helper.assertTrue(aGot.size() == 2 && aGot.stream().anyMatch(s -> s.is(Items.STONE_BRICKS) && s.getCount() == 16)
				&& aGot.stream().anyMatch(s -> s.is(Items.APPLE) && s.getCount() == 6), "a's gifts: " + aGot);
			helper.assertTrue(bGot.size() == 1 && bGot.get(0).is(Items.APPLE) && bGot.get(0).getCount() == 6, "b's gift: " + bGot);
			helper.assertTrue(saw(a, "Dara brings you a festival gift: Stone Bricks (16).") && saw(a, "Bo brings you a festival gift: Apple (6)."), "a's lines: " + seen(a));
			helper.assertTrue(saw(b, "Bo brings you a festival gift: Apple (6).") && !saw(b, "Dara brings you a festival gift: Stone Bricks (16)."), "b's lines: " + seen(b));
			clearFeet(level, a.player());
			clearFeet(level, b.player());
			helper.assertTrue(FriendPerks.festivalGifts(level, hall, a.player()) == 0 && FriendPerks.festivalGifts(level, hall, b.player()) == 0, "a second gift the same festival");
			helper.assertTrue(atFeet(level, a.player()).isEmpty(), "items the second time: " + atFeet(level, a.player()));
			int[] next = new int[2];
			later(level, 1, () -> {
				next[0] = FriendPerks.festivalGifts(level, hall, a.player());
				next[1] = FriendPerks.festivalGifts(level, hall, b.player());
			});
			helper.assertTrue(next[0] == 2 && next[1] == 1, "the next festival: a " + next[0] + ", b " + next[1]);
			clearFeet(level, a.player());
			clearFeet(level, b.player());
			helper.succeed();
		});
	}

	/**
	 * Each villager's gift follows their job's family, as the spec lists them, through the festival itself: a toolsmith
	 * 4 iron ingots, a fisherman 4 cooked salmon, a cleric a potion of regeneration, a guard 16 arrows, a cartographer 2
	 * books, a nitwit 3 flowers; no gift's line shows a raw key.
	 */
	//$ gametest_ticks_batch AREA '80' '"qaRepGiftFamilies"'
	@GameTest(template = AREA, timeoutTicks = 80, batch = "qaRepGiftFamilies")
	public void theGiftFollowsTheJobFamily(GameTestHelper helper) {
		one(helper, () -> {
			ServerLevel level = helper.getLevel();
			BlockPos hall = helper.absolutePos(HALL);
			QaStageFiveGameTests.Client a = client(helper, new BlockPos(3, 2, 14));
			ServerPlayer p = a.player();
			List<VillagerProfession> jobs = List.of(VillagerProfession.TOOLSMITH, VillagerProfession.FISHERMAN, VillagerProfession.CLERIC, ModVillagers.GUARD,
				VillagerProfession.CARTOGRAPHER, VillagerProfession.NITWIT);
			for (int i = 0; i < jobs.size(); i++) {
				Villager v = worker(helper, new BlockPos(5 + i, 2, 6), "Job" + i, jobs.get(i));
				if (jobs.get(i) == VillagerProfession.NITWIT) {
					v.setVillagerData(v.getVillagerData().setLevel(1));
				}
				friendship(v, p, 650);
			}
			clearFeet(level, p);
			int n = FriendPerks.festivalGifts(level, hall, p);
			List<ItemStack> got = atFeet(level, p);
			helper.assertTrue(n == 6 && got.size() == 6, "gifts: " + n + ", " + got);
			helper.assertTrue(got.stream().anyMatch(s -> s.is(Items.IRON_INGOT) && s.getCount() == 4), "the toolsmith's iron: " + got);
			helper.assertTrue(got.stream().anyMatch(s -> s.is(Items.COOKED_SALMON) && s.getCount() == 4), "the fisherman's salmon: " + got);
			helper.assertTrue(got.stream().anyMatch(s -> s.is(Items.POTION)
				&& s.getOrDefault(DataComponents.POTION_CONTENTS, PotionContents.EMPTY).is(Potions.REGENERATION)), "the cleric's potion: " + got);
			helper.assertTrue(got.stream().anyMatch(s -> s.is(Items.ARROW) && s.getCount() == 16), "the guard's arrows: " + got);
			helper.assertTrue(got.stream().anyMatch(s -> s.is(Items.BOOK) && s.getCount() == 2), "the cartographer's books: " + got);
			helper.assertTrue(got.stream().anyMatch(s -> s.is(ItemTags.SMALL_FLOWERS) && s.getCount() == 3), "the nitwit's flowers: " + got);
			helper.assertTrue(saw(a, "Job0 brings you a festival gift: Iron Ingot (4)."), "the toolsmith's line: " + seen(a));
			List<String> lines = seen(a).stream().filter(l -> l.contains("festival gift")).toList();
			helper.assertTrue(lines.size() >= 6, "the gift lines: " + lines);
			for (String line : lines) {
				helper.assertFalse(line.contains("aliveworkplace.") || line.contains("%") || line.contains("item.minecraft"), "a raw line: " + line);
			}
			clearFeet(level, p);
			helper.succeed();
		});
	}

	// --- 31.12: named after you -----------------------------------------------------------------------------------

	/**
	 * The first baby whose parent has 8 hearts with a player takes that player's name, once per player per village: the
	 * next baby takes the next friend's name, the third none; in another village the first player's name comes again.
	 * The parents' friend is told; the marks are saved.
	 */
	//$ gametest_ticks_batch AREA '80' '"qaRepNamedBaby"'
	@GameTest(template = AREA, timeoutTicks = 80, batch = "qaRepNamedBaby")
	public void namedOncePerPlayerPerVillage(GameTestHelper helper) {
		two(helper, () -> {
			ServerLevel level = helper.getLevel();
			BlockPos hall = helper.absolutePos(HALL);
			BlockPos second = helper.absolutePos(SECOND);
			QaStageFiveGameTests.Client a = client(helper, new BlockPos(9, 2, 12));
			QaStageFiveGameTests.Client b = client(helper, new BlockPos(10, 2, 12));
			Villager ana = HeartEventGameTests.villager(helper, new BlockPos(8, 2, 7), "Ana");
			Villager bo = HeartEventGameTests.villager(helper, new BlockPos(10, 2, 7), "Bo");
			friendship(ana, a.player(), 850);
			friendship(bo, b.player(), 820);
			Villager first = born(helper, ana, bo);
			Villager secondBaby = born(helper, ana, bo);
			Villager third = born(helper, ana, bo);
			helper.assertTrue(babyName(first).equals(name(a)), "the first baby: '" + babyName(first) + "', expected " + name(a));
			helper.assertTrue(babyName(secondBaby).equals(name(b)), "the second baby: '" + babyName(secondBaby) + "', expected " + name(b));
			helper.assertTrue(!third.hasCustomName(), "the third baby: " + babyName(third));
			helper.assertTrue(saw(a, "Ana and Bo of Thornholm named their baby after you!") && saw(b, "Ana and Bo of Thornholm named their baby after you!"),
				"told: a " + seen(a) + ", b " + seen(b));
			List<String> chronicle = hall(helper, HALL).chronicle().stream().map(e -> e.text().getString()).toList();
			helper.assertTrue(chronicle.contains("Ana and Bo named their baby " + name(a) + ", after a dear friend"), "the chronicle: " + chronicle);
			// In Brackwater a's name comes again.
			Villager cy = HeartEventGameTests.villager(helper, new BlockPos(2, 2, 3), "Cy");
			Villager di = HeartEventGameTests.villager(helper, new BlockPos(3, 2, 3), "Di");
			helper.assertTrue(CivicEffects.hallOf(cy) != null && CivicEffects.hallOf(cy).getBlockPos().equals(second), "Cy isn't of Brackwater");
			friendship(cy, a.player(), 900);
			Villager fourth = born(helper, cy, di);
			helper.assertTrue(babyName(fourth).equals(name(a)), "Brackwater's baby: '" + babyName(fourth) + "'");
			helper.assertTrue(Reputation.namedBaby(level, hall, a.player().getUUID()) && Reputation.namedBaby(level, second, a.player().getUUID())
				&& Reputation.namedBaby(level, hall, b.player().getUUID()) && !Reputation.namedBaby(level, second, b.player().getUUID()), "the marks");
			// Saved and read back.
			CompoundTag saved = Stories.Data.get(level).save(new CompoundTag(), level.registryAccess());
			CompoundTag again = Stories.Data.load(saved, level.registryAccess()).save(new CompoundTag(), level.registryAccess());
			helper.assertTrue(named(again, hall, a.player().getUUID()) && named(again, second, a.player().getUUID()) && named(again, hall, b.player().getUUID())
				&& !named(again, second, b.player().getUUID()), "the marks after a reload");
			List.of(first, secondBaby, third, fourth).forEach(Villager::discard);
			helper.succeed();
		});
	}

	private static boolean named(CompoundTag saved, BlockPos hall, UUID player) {
		ListTag halls = saved.getList("halls", Tag.TAG_COMPOUND);
		for (int i = 0; i < halls.size(); i++) {
			if (halls.getCompound(i).getLong("hall") != hall.asLong()) {
				continue;
			}
			ListTag standings = halls.getCompound(i).getList("standings", Tag.TAG_COMPOUND);
			for (int j = 0; j < standings.size(); j++) {
				CompoundTag s = standings.getCompound(j);
				if (io.github.jcondedata.aliveworkplace.mc.Nbt.getUuid(s, "player").equals(player)) {
					return s.getBoolean("named_baby");
				}
			}
		}
		return false;
	}

	// --- 31.12: letters from friends ------------------------------------------------------------------------------

	/**
	 * At 10 hearts a parcel once a week, to each such player: two players at 10 hearts, two parcels; 9 hearts none; not
	 * again on the week's last day; again on the first day of the next week. The player is told it is on its way.
	 */
	//$ gametest_ticks_batch AREA '80' '"qaRepParcel"'
	@GameTest(template = AREA, timeoutTicks = 80, batch = "qaRepParcel")
	public void aParcelAWeekAtTheWeeksEdge(GameTestHelper helper) {
		one(helper, () -> {
			ServerLevel level = helper.getLevel();
			QaStageFiveGameTests.Client a = client(helper, new BlockPos(4, 2, 12));
			QaStageFiveGameTests.Client b = client(helper, new BlockPos(5, 2, 12));
			ServerPlayer c = mock(helper, new BlockPos(6, 2, 12));
			PostOffice office = PostOffice.get(level.getServer());
			List<UUID> ours = List.of(a.player().getUUID(), b.player().getUUID(), c.getUUID());
			Leftovers.after(helper, () -> office.parcels().removeIf(p -> ours.contains(p.to())));
			Villager dara = worker(helper, new BlockPos(7, 2, 6), "Dara", VillagerProfession.LIBRARIAN);
			friendship(dara, a.player(), 1000);
			friendship(dara, b.player(), 1000);
			friendship(dara, c, 999);
			boolean was = Friendship.ENABLED;
			Friendship.ENABLED = false;
			int off;
			try {
				off = FriendPerks.parcels(level, dara);
			} finally {
				Friendship.ENABLED = was;
			}
			helper.assertTrue(off == 0, "a parcel with friendship off: " + off);
			int first = FriendPerks.parcels(level, dara);
			helper.assertTrue(first == 2, "parcels to the two at 10 hearts: " + first);
			helper.assertTrue(saw(a, "Dara sent you a parcel through the post. It arrives at dawn."), "a's line: " + seen(a));
			helper.assertTrue(office.parcels().stream().noneMatch(p -> p.to().equals(c.getUUID())), "a parcel at 9 hearts");
			// The rest of this week: nothing more. The next week: one each again.
			long day = io.github.jcondedata.aliveworkplace.hall.Chronicle.day(level);
			long toWeekEnd = Gifts.WEEK - 1 - Math.floorMod(day - 1, (long) Gifts.WEEK);
			int[] sent = new int[3];
			sent[0] = FriendPerks.parcels(level, dara);
			later(level, toWeekEnd, () -> sent[1] = FriendPerks.parcels(level, dara));
			later(level, toWeekEnd + 1, () -> sent[2] = FriendPerks.parcels(level, dara));
			helper.assertTrue(sent[0] == 0 && sent[1] == 0, "a second parcel the same week: " + sent[0] + " now, " + sent[1] + " on its last day (+" + toWeekEnd + ")");
			helper.assertTrue(sent[2] == 2, "the next week's parcels: " + sent[2]);
			long toA = office.parcels().stream().filter(p -> p.to().equals(a.player().getUUID())).count();
			helper.assertTrue(toA == 2, "parcels to a: " + toA);
			helper.succeed();
		});
	}

	// --- 31.12: conditions and edicts -----------------------------------------------------------------------------

	/**
	 * {@code title_at_least lord} isn't met by 1000 in a Village and is once it is a Town; {@code friend} at 49 and 50
	 * for one player; {@code hearts_at_least 6} at 599 and 600 points, for that player only, never without a giver or
	 * a player; a heart count outside 1-10 doesn't read.
	 */
	//$ gametest_ticks_batch AREA '60' '"qaRepConditions"'
	@GameTest(template = AREA, timeoutTicks = 60, batch = "qaRepConditions")
	public void titleAndHeartsConditionsAtTheirEdges(GameTestHelper helper) {
		one(helper, () -> {
			ServerLevel level = helper.getLevel();
			BlockPos hall = helper.absolutePos(HALL);
			VillageHallBlockEntity entity = hall(helper, HALL);
			entity.setRank(VillageRanks.Rank.VILLAGE);
			ServerPlayer me = mock(helper, HALL.south(2));
			ServerPlayer other = mock(helper, HALL.south(3));
			Condition lord = Conditions.parse(JsonParser.parseString("{\"type\": \"title_at_least\", \"title\": \"lord\"}").getAsJsonObject());
			Condition hero = Conditions.parse(JsonParser.parseString("{\"type\": \"title_at_least\", \"title\": \"hero\"}").getAsJsonObject());
			Condition friend = Conditions.parse(JsonParser.parseString("{\"type\": \"title_at_least\", \"title\": \"friend\"}").getAsJsonObject());
			Reputation.add(level, hall, me, 1000);
			helper.assertTrue(!lord.met(level, hall) && !lord.met(level, hall, null, me) && hero.met(level, hall), "1000 in a Village: lord "
				+ lord.met(level, hall) + ", hero " + hero.met(level, hall));
			entity.setRank(VillageRanks.Rank.TOWN);
			VillageQuests.tick(level, hall, entity);
			helper.assertTrue(lord.met(level, hall) && lord.met(level, hall, null, me) && !lord.met(level, hall, null, other), "a Lord of a Town");
			Reputation.add(level, hall, other, 49);
			helper.assertTrue(!friend.met(level, hall, null, other), "a Friend at 49");
			Reputation.add(level, hall, other, 1);
			helper.assertTrue(friend.met(level, hall, null, other) && !hero.met(level, hall, null, other), "a Friend at 50");

			Condition six = Conditions.parse(JsonParser.parseString("{\"type\": \"hearts_at_least\", \"hearts\": 6}").getAsJsonObject());
			Villager dara = HeartEventGameTests.villager(helper, new BlockPos(5, 2, 5), "Dara");
			friendship(dara, me, 599);
			helper.assertTrue(!six.met(level, hall, dara, me), "6 hearts at 599 points");
			friendship(dara, me, 600);
			helper.assertTrue(six.met(level, hall, dara, me), "not 6 hearts at 600 points");
			helper.assertTrue(!six.met(level, hall, dara, other) && !six.met(level, hall, null, me) && !six.met(level, hall, dara, null) && !six.met(level, hall),
				"held for someone else, or with no giver or player");
			for (int bad : new int[] {0, 11}) {
				boolean threw = false;
				try {
					Conditions.parse(JsonParser.parseString("{\"type\": \"hearts_at_least\", \"hearts\": " + bad + "}").getAsJsonObject());
				} catch (IllegalArgumentException e) {
					threw = true;
				}
				helper.assertTrue(threw, "hearts_at_least " + bad + " read");
			}
			Condition ten = Conditions.parse(JsonParser.parseString("{\"type\": \"hearts_at_least\", \"hearts\": 10}").getAsJsonObject());
			friendship(dara, me, 1000);
			helper.assertTrue(ten.met(level, hall, dara, me), "10 hearts at the most");
			helper.succeed();
		});
	}

	/**
	 * The say in edicts follows the title as it comes and goes: 1000 in a Village (a Hero) proposes but can't set; in a
	 * Town (a Lord) sets; a hit that brings them to 990 takes it away again. A Friend and a Stranger (49) have no say,
	 * and a Lord of Brackwater has none in Thornholm.
	 */
	//$ gametest_ticks_batch AREA '60' '"qaRepEdicts"'
	@GameTest(template = AREA, timeoutTicks = 60, batch = "qaRepEdicts")
	public void theSayInEdictsFollowsTheTitle(GameTestHelper helper) {
		two(helper, () -> {
			ServerLevel level = helper.getLevel();
			BlockPos hall = helper.absolutePos(HALL);
			BlockPos second = helper.absolutePos(SECOND);
			VillageHallBlockEntity entity = hall(helper, HALL);
			VillageHallBlockEntity brackwater = hall(helper, SECOND);
			entity.setOwner(UUID.randomUUID(), "Jesse");
			brackwater.setOwner(UUID.randomUUID(), "Jesse");
			Leftovers.after(helper, () -> {
				entity.setOwner(null, "");
				brackwater.setOwner(null, "");
			});
			entity.setRank(VillageRanks.Rank.VILLAGE);
			ServerPlayer me = mock(helper, HALL.south(2));
			ServerPlayer friend = mock(helper, HALL.south(3));
			ServerPlayer stranger = mock(helper, HALL.east(2));
			ServerPlayer elsewhere = mock(helper, HALL.west(2));
			Reputation.add(level, hall, me, 1000);
			helper.assertTrue(!Reputation.maySetEdicts(level, hall, me) && Reputation.mayProposeEdicts(level, hall, me), "1000 in a Village (a Hero)");
			entity.setRank(VillageRanks.Rank.TOWN);
			VillageQuests.tick(level, hall, entity);
			helper.assertTrue(Reputation.maySetEdicts(level, hall, me) && !Reputation.mayProposeEdicts(level, hall, me), "a Lord of a Town");
			Villager dara = HeartEventGameTests.villager(helper, new BlockPos(10, 2, 7), "Dara");
			dara.hurt(level.damageSources().playerAttack(me), 1f);
			helper.assertTrue(points(helper, HALL, me) == 990 && !Reputation.maySetEdicts(level, hall, me) && Reputation.mayProposeEdicts(level, hall, me),
				"after the hit (990): may set " + Reputation.maySetEdicts(level, hall, me));
			Reputation.add(level, hall, friend, 299);
			Reputation.add(level, hall, stranger, 49);
			helper.assertTrue(!Reputation.maySetEdicts(level, hall, friend) && !Reputation.mayProposeEdicts(level, hall, friend), "a Friend (299)");
			helper.assertTrue(!Reputation.maySetEdicts(level, hall, stranger) && !Reputation.mayProposeEdicts(level, hall, stranger), "a Stranger (49)");
			brackwater.setRank(VillageRanks.Rank.TOWN);
			Reputation.add(level, second, elsewhere, 1000);
			helper.assertTrue(Reputation.maySetEdicts(level, second, elsewhere), "a Lord of Brackwater in Brackwater");
			helper.assertTrue(!Reputation.maySetEdicts(level, hall, elsewhere) && !Reputation.mayProposeEdicts(level, hall, elsewhere),
				"a Lord of Brackwater in Thornholm");
			helper.succeed();
		});
	}

	/**
	 * Config {@code reputation} off: the standing is kept but worth nothing: no title discount, Hero of the Village I at
	 * festivals, no say in edicts; gifts and festivals earn nothing. Back on, all of it returns.
	 */
	//$ gametest_ticks_batch AREA '60' '"qaRepOffPerks"'
	@GameTest(template = AREA, timeoutTicks = 60, batch = "qaRepOffPerks")
	public void titlesAreWorthNothingWithReputationOff(GameTestHelper helper) {
		one(helper, () -> {
			ServerLevel level = helper.getLevel();
			BlockPos hall = helper.absolutePos(HALL);
			VillageHallBlockEntity entity = hall(helper, HALL);
			entity.setRank(VillageRanks.Rank.TOWN);
			entity.setOwner(UUID.randomUUID(), "Jesse");
			Leftovers.after(helper, () -> entity.setOwner(null, ""));
			ServerPlayer me = mock(helper, HALL.south(2));
			Villager dara = worker(helper, new BlockPos(11, 2, 9), "Dara", VillagerProfession.FARMER);
			dara.getOffers().clear();
			dara.getOffers().add(new MerchantOffer(new ItemCost(Items.EMERALD, 20), new ItemStack(Items.BREAD), 12, 1, 0.05f));
			Reputation.add(level, hall, me, 1000);
			helper.assertTrue(price(dara, me, 0) == 17 && FriendPerks.heroLevel(level, hall, me) == 1 && Reputation.maySetEdicts(level, hall, me), "a Lord, on");
			Reputation.ENABLED = false;
			int price = price(dara, me, 0);
			int heroLevel = FriendPerks.heroLevel(level, hall, me);
			boolean edicts = Reputation.maySetEdicts(level, hall, me);
			Villager bo = HeartEventGameTests.villager(helper, new BlockPos(7, 2, 9), "Bo");
			give(me, bo);
			Reputation.onFestival(level, hall);
			int kept = points(helper, HALL, me);
			Reputation.ENABLED = true;
			helper.assertTrue(price == 20 && heroLevel == 0 && !edicts, "off: price " + price + ", hero level " + heroLevel + ", edicts " + edicts);
			helper.assertTrue(kept == 1000, "earned with it off: " + kept);
			helper.assertTrue(price(dara, me, 0) == 17 && Reputation.maySetEdicts(level, hall, me), "back on");
			helper.succeed();
		});
	}

	// --- The sentences --------------------------------------------------------------------------------------------

	/** Every sentence these features show is written out in en_us, with its arguments where the spec puts them. */
	//$ gametest 'FabricGameTest.EMPTY_STRUCTURE'
	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
	public void everySentenceIsWrittenOut(GameTestHelper helper) {
		List<String> keys = List.of("title.aliveworkplace.stranger", "title.aliveworkplace.friend", "title.aliveworkplace.hero", "title.aliveworkplace.lord",
			"title.aliveworkplace.of", "message.aliveworkplace.title.new", "message.aliveworkplace.title.again", "message.aliveworkplace.title.lost",
			"message.aliveworkplace.title.lost_all", "message.aliveworkplace.honour.new", "message.aliveworkplace.standing.gain",
			"message.aliveworkplace.standing.loss", "message.aliveworkplace.standing.header", "message.aliveworkplace.standing.line",
			"message.aliveworkplace.standing.honours", "message.aliveworkplace.standing.none", "message.aliveworkplace.standing.off",
			"chronicle.aliveworkplace.title", "chronicle.aliveworkplace.honour", "chat.aliveworkplace.title", "message.aliveworkplace.festival_gift",
			"chronicle.aliveworkplace.named_after", "message.aliveworkplace.named_after", "letter.aliveworkplace.friend",
			"message.aliveworkplace.friend_parcel", "rule.aliveworkplace.title_at_least", "rule.aliveworkplace.hearts_at_least",
			"screen.aliveworkplace.hall.standing", "screen.aliveworkplace.hall.honours", "screen.aliveworkplace.hall.standing_none",
			"screen.aliveworkplace.hall.standing_top", "screen.aliveworkplace.hall.standing_line");
		List<String> missing = new ArrayList<>();
		for (String key : keys) {
			if (!Language.getInstance().has(key)) {
				missing.add(key);
			}
		}
		helper.assertTrue(missing.isEmpty(), "not in en_us: " + missing);
		Component thornholm = Component.literal("Thornholm");
		check(helper, Component.translatable("message.aliveworkplace.title.new", "Jesse", Title.HERO.of(thornholm)), "Jesse is now a Hero of Thornholm!");
		check(helper, Component.translatable("message.aliveworkplace.title.new", "Jesse", Title.LORD.of(thornholm)), "Jesse is now a Lord of Thornholm!");
		check(helper, Component.translatable("message.aliveworkplace.title.lost", Title.LORD.of(thornholm), Title.HERO.label()),
			"You are no longer a Lord of Thornholm. They know you as a Hero now.");
		check(helper, Component.translatable("message.aliveworkplace.standing.gain", 1, thornholm, 51), "+1 standing in Thornholm (now 51)");
		check(helper, Component.translatable("message.aliveworkplace.standing.loss", 150, thornholm, -100), "-150 standing in Thornholm (now -100)");
		check(helper, Component.translatable("chat.aliveworkplace.title", Title.FRIEND.of(thornholm), Component.literal("hello")), "[Friend of Thornholm] hello");
		check(helper, Component.translatable("message.aliveworkplace.named_after", "Ana", "Bo", thornholm), "Ana and Bo of Thornholm named their baby after you!");
		check(helper, Component.translatable("letter.aliveworkplace.friend", "Dara"), "Thought you'd like these. Dara");
		check(helper, Component.translatable("message.aliveworkplace.friend_parcel", "Dara"), "Dara sent you a parcel through the post. It arrives at dawn.");
		check(helper, Component.translatable("message.aliveworkplace.festival_gift", "Dara", 1, new ItemStack(Items.NOTE_BLOCK).getHoverName()),
			"Dara brings you a festival gift: Note Block (1).");
		check(helper, Component.translatable("rule.aliveworkplace.title_at_least", 1, 3, Title.FRIEND.label(), Title.LORD.label()),
			"A player who is at least Lord of the village (the best now: Friend)");
		check(helper, Component.translatable("chronicle.aliveworkplace.title", "Jesse", Title.LORD.label()), "Jesse became a Lord of the village");
		helper.succeed();
	}

	private static void check(GameTestHelper helper, Component text, String expected) {
		String got = text.getString();
		helper.assertTrue(got.equals(expected), "'" + got + "', expected '" + expected + "'");
	}
}
