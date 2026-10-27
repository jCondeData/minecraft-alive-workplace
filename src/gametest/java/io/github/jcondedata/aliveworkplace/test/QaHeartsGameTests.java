package io.github.jcondedata.aliveworkplace.test;

import io.github.jcondedata.aliveworkplace.hall.CivicEffects;
import io.github.jcondedata.aliveworkplace.hall.VillageNeeds;
import io.github.jcondedata.aliveworkplace.people.Traits;
import io.github.jcondedata.aliveworkplace.registry.ModItems;
import io.github.jcondedata.aliveworkplace.registry.ModVillagers;
import io.github.jcondedata.aliveworkplace.story.Friendship;
import io.github.jcondedata.aliveworkplace.story.Gifts;
import io.github.jcondedata.aliveworkplace.story.HeartEvents;
import io.github.jcondedata.aliveworkplace.story.JobFamilies;
import io.github.jcondedata.aliveworkplace.story.Keepsakes;
import io.github.jcondedata.aliveworkplace.story.Tastes;
import io.github.jcondedata.aliveworkplace.work.WorkerStatus;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.locale.Language;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.npc.VillagerProfession;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.Vec3;

/**
 * QA (lane-c-1024-0353): friendship, gifts and heart events (ROADMAP 31.5-31.8), written from the spec's "Done when".
 * Gifts through the player's own click from either hand and on a sneak (on villagers whose jobs answer clicks of their
 * own), the heart thresholds from both sides (exactly N hearts and one below, the 0 and 1000 ends), the day and week
 * limits at the very tick a day or week turns, a gift that reaches two hearts starting her story in real time and
 * another player's gift not cutting it, a story cut by a chunk unload (and a job change) counting once, a player hearing
 * one story at a time, the switches off from the off hand, and every sentence a player reads.
 */
public class QaHeartsGameTests implements FabricGameTest {
	private static final String AREA = "aliveworkplace_test:big_area";

	// --- Setting up -----------------------------------------------------------------------------------------------

	/** The world's clock is put back after the test. */
	private static void keepClock(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		long dayTime = level.getDayTime();
		Leftovers.after(helper, () -> level.setDayTime(dayTime));
	}

	/** The day time of {@code tick} ticks into the (1-based) {@code day}. */
	private static long at(long day, long tick) {
		return (day - 1) * VillageNeeds.DAY + tick;
	}

	/** The first day from {@code from} (in steps of {@code step}) from which, for {@code span} days, it is none of the villagers' name day. */
	private static long quiet(long from, int span, int step, Villager... villagers) {
		for (long day = from; ; day += step) {
			boolean ok = true;
			for (Villager v : villagers) {
				for (int i = 0; i < span; i++) {
					ok &= Gifts.daysToNameDay(v.getUUID(), day + i) != 0;
				}
			}
			if (ok) {
				return day;
			}
		}
	}

	private static int seeds;

	/** A named villager of {@code job} standing still at {@code at}, Nimble only (whose tastes, sugar and rabbit's feet, no test here gives), from a fixed seed. */
	private static Villager named(GameTestHelper helper, BlockPos at, String name, VillagerProfession job) {
		ServerLevel level = helper.getLevel();
		Villager v = EntityType.VILLAGER.create(level);
		java.util.Random random = new java.util.Random(0x4ea27L + 7919L * seeds++);
		UUID id;
		do {
			id = new UUID(random.nextLong(), random.nextLong());
		} while (!Traits.of(id).equals(List.of(Traits.Trait.NIMBLE)) || level.getEntity(id) != null);
		v.setUUID(id);
		v.setVillagerData(v.getVillagerData().setProfession(job).setLevel(job == VillagerProfession.NONE ? 1 : 2));
		Vec3 pos = helper.absoluteVec(Vec3.atBottomCenterOf(at));
		v.moveTo(pos.x, pos.y, pos.z, 0, 0);
		v.setNoAi(true);
		v.setCustomName(Component.literal(name));
		level.addFreshEntity(v);
		return v;
	}

	private static QaStageFiveGameTests.Client client(GameTestHelper helper, String prefix) {
		return QaStageSixGameTests.client(helper, GameType.SURVIVAL, QaStageSixGameTests.name(prefix));
	}

	/**
	 * What the client was shown since message {@code from}. The mock client sees each chat line twice (displayClientMessage
	 * hands it on to sendSystemMessage, and both are recorded): one of each run of the same message is kept.
	 */
	private static List<String> heard(QaStageFiveGameTests.Client c, int from) {
		List<Component> seen = c.seen().subList(from, c.seen().size());
		List<String> out = new ArrayList<>();
		for (int i = 0; i < seen.size(); i++) {
			if (i == 0 || seen.get(i) != seen.get(i - 1)) {
				out.add(seen.get(i).getString());
			}
		}
		return out;
	}

	/** {@code player} holds a Gift of {@code item} in the main hand and gives it to {@code villager}. */
	private static Gifts.Result give(ServerPlayer player, Villager villager, Item item) {
		player.setItemInHand(InteractionHand.MAIN_HAND, Gifts.wrap(new ItemStack(item), player.getGameProfile().getName()));
		return Gifts.give(player, villager, player.getMainHandItem());
	}

	private static String overHead(Villager villager) {
		WorkerStatus.Entry entry = WorkerStatus.get(villager, villager.level().getGameTime());
		return entry == null || entry.line() == null ? "" : entry.line().getString();
	}

	/** A sentence a player reads: written out, with its arguments filled in, no raw key, no placeholder, no "null". */
	private static void clean(GameTestHelper helper, String what, String s) {
		helper.assertTrue(!s.isBlank() && !s.contains("%") && !s.contains("aliveworkplace.") && !s.contains("minecraft.") && !s.contains("null")
			&& !s.contains("{}"), what + " reads \"" + s + "\"");
	}

	private static int count(String s, char c) {
		return (int) s.chars().filter(ch -> ch == c).count();
	}

	// --- Gifts through the player's click ------------------------------------------------------------------------

	/**
	 * 31.6: "Right-click a named villager with a Gift". Through the real interact packets, a Gift is given from the main
	 * hand, from the main hand while sneaking (on a Builder, whose sneak-click is his status page), from the off hand with
	 * the main one empty (on a Guard) and from the off hand while sneaking (on a Farmer, whose sneak-click is her field's
	 * status): each time the Gift leaves that hand, the neutral gift's 20 points are counted, the day is noted, no screen
	 * opens and the villager's line is in the player's chat. A second gift the same day, from the off hand while sneaking,
	 * is kept, with the day's line.
	 */
	//$ gametest_ticks_batch AREA '100' '"qaHeartsHands"'
	@GameTest(template = AREA, timeoutTicks = 100, batch = "qaHeartsHands")
	public void aGiftIsTakenFromEitherHandAndOnASneak(GameTestHelper helper) {
		HeartEventGameTests.village(helper, () -> {
			ServerLevel level = helper.getLevel();
			keepClock(helper);
			Villager dara = named(helper, new BlockPos(3, 2, 4), "Dara", VillagerProfession.NONE);
			Villager bram = named(helper, new BlockPos(6, 2, 4), "Bram", ModVillagers.BUILDER);
			Villager cora = named(helper, new BlockPos(3, 2, 12), "Cora", ModVillagers.GUARD);
			Villager edda = named(helper, new BlockPos(6, 2, 12), "Edda", VillagerProfession.FARMER);
			long day = quiet(8, 2, 1, dara, bram, cora, edda);
			level.setDayTime(at(day, 1000));
			QaStageFiveGameTests.Client c = client(helper, "qahh");
			ServerPlayer p = c.player();
			String me = p.getGameProfile().getName();
			record Way(Villager to, InteractionHand hand, boolean sneak, String what) {
			}
			for (Way w : List.of(new Way(dara, InteractionHand.MAIN_HAND, false, "main hand"),
					new Way(bram, InteractionHand.MAIN_HAND, true, "main hand, sneaking, on a Builder"),
					new Way(cora, InteractionHand.OFF_HAND, false, "off hand on a Guard"),
					new Way(edda, InteractionHand.OFF_HAND, true, "off hand, sneaking, on a Farmer"))) {
				p.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
				p.setItemInHand(InteractionHand.OFF_HAND, ItemStack.EMPTY);
				p.setItemInHand(w.hand(), Gifts.wrap(new ItemStack(Items.STICK), me));
				p.setShiftKeyDown(w.sneak());
				int before = c.seen().size();
				QaStageSixGameTests.clickVillager(p, w.to(), c.seen());
				List<String> heard = heard(c, before);
				String name = w.to().getCustomName().getString();
				helper.assertTrue(p.getItemInHand(w.hand()).isEmpty(), w.what() + ": the Gift is still in hand (" + p.getItemInHand(w.hand()) + "), heard " + heard);
				helper.assertTrue(Friendship.points(w.to(), p.getUUID()) == 20, w.what() + ": " + Friendship.points(w.to(), p.getUUID())
					+ " points, not a neutral gift's 20; heard " + heard);
				helper.assertTrue(Friendship.of(w.to()).bond(p.getUUID()).giftDay() == day, w.what() + ": the gift's day isn't noted: " + Friendship.of(w.to()).bond(p.getUUID()));
				helper.assertTrue(p.containerMenu == p.inventoryMenu, w.what() + ": a screen opened: " + p.containerMenu);
				helper.assertTrue(heard.stream().anyMatch(s -> s.startsWith(name + ": ") && s.contains("Stick")), w.what() + ": " + name + "'s line isn't in the chat: " + heard);
				heard.forEach(s -> clean(helper, w.what(), s));
			}
			// A second gift to Dara the same day, from the off hand while sneaking: kept, unopened, with her line.
			p.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
			p.setItemInHand(InteractionHand.OFF_HAND, Gifts.wrap(new ItemStack(Items.CAKE), me));
			p.setShiftKeyDown(true);
			int before = c.seen().size();
			QaStageSixGameTests.clickVillager(p, dara, c.seen());
			List<String> heard = heard(c, before);
			helper.assertTrue(p.getOffhandItem().is(ModItems.GIFT) && Gifts.contents(p.getOffhandItem()).is(Items.CAKE),
				"the second gift of the day left the off hand: " + p.getOffhandItem());
			helper.assertTrue(Friendship.points(dara, p.getUUID()) == 20, "the second gift of the day counted: " + Friendship.points(dara, p.getUUID()));
			helper.assertTrue(heard.contains("Dara: You already gave me something today, " + me + ". Keep it for tomorrow."), "the day's line: " + heard);
			p.setShiftKeyDown(false);
			helper.succeed();
		});
	}

	// --- Thresholds --------------------------------------------------------------------------------------------------

	/**
	 * 31.5 and 31.7 at their edges: ten hearts of 100 (199 is one heart, 200 two, 999 nine, 1000 ten) as the action bar
	 * shows them; at 199 nothing to tell and at 200 a 2-heart event; with that told, at 399 nothing and at 400 a 4-heart
	 * event; points stop at 1000 and at 0 whatever a gift is worth.
	 */
	//$ gametest_ticks_batch AREA '80' '"qaHeartsEdges"'
	@GameTest(template = AREA, timeoutTicks = 80, batch = "qaHeartsEdges")
	public void heartThresholdsFromBothSides(GameTestHelper helper) {
		HeartEventGameTests.village(helper, () -> {
			ServerLevel level = helper.getLevel();
			keepClock(helper);
			int[][] steps = {{0, 0}, {99, 0}, {100, 1}, {199, 1}, {200, 2}, {201, 2}, {999, 9}, {1000, 10}};
			for (int[] s : steps) {
				helper.assertTrue(Friendship.hearts(s[0]) == s[1], s[0] + " points are " + Friendship.hearts(s[0]) + " hearts, not " + s[1]);
			}
			Villager edda = named(helper, new BlockPos(5, 2, 5), "Edda", VillagerProfession.FARMER);
			for (int[] s : steps) {
				String line = Friendship.heartsLine(edda, s[0]).getString();
				helper.assertTrue(line.startsWith("Edda ") && count(line, '♥') == s[1] && count(line, '♡') == 10 - s[1], "at " + s[0] + " points the action bar reads \"" + line + "\"");
			}
			ServerPlayer me = HeartEventGameTests.player(helper, new BlockPos(5, 2, 7));
			// One below two hearts: nothing to tell, and nobody starts.
			Friendship.add(edda, me, 199);
			helper.assertTrue(HeartEvents.pending(level, edda, me.getUUID()) == null, "at 199 points: " + HeartEvents.pending(level, edda, me.getUUID()));
			HeartEvents.tick(level, HeartEventGameTests.later(level));
			helper.assertTrue(HeartEvents.telling(edda) == null, "at 199 points she started telling");
			// Exactly two hearts.
			Friendship.add(edda, me, 1);
			HeartEvents.Event two = HeartEvents.pending(level, edda, me.getUUID());
			helper.assertTrue(two != null && two.hearts() == 2, "at exactly 200 points: " + (two == null ? "nothing" : two.id() + " at " + two.hearts()));
			// Told; then one below four hearts: nothing; exactly four: a 4-heart event.
			Friendship.told(edda, me, two.id().toString());
			Friendship.add(edda, me, 399 - Friendship.points(edda, me.getUUID()));
			helper.assertTrue(Friendship.points(edda, me.getUUID()) == 399, "setup: " + Friendship.points(edda, me.getUUID()));
			helper.assertTrue(HeartEvents.pending(level, edda, me.getUUID()) == null, "at 399 points, the 2-heart event told: " + HeartEvents.pending(level, edda, me.getUUID()));
			helper.assertTrue(HeartEvents.nextHearts(level, edda, me.getUUID()) == 4, "the next heart level: " + HeartEvents.nextHearts(level, edda, me.getUUID()));
			Friendship.add(edda, me, 1);
			HeartEvents.Event four = HeartEvents.pending(level, edda, me.getUUID());
			helper.assertTrue(four != null && four.hearts() == 4, "at exactly 400 points: " + (four == null ? "nothing" : four.id() + " at " + four.hearts()));
			// The ends: a loved gift at 990 stops at 1000; a hated one at 10 stops at 0.
			Villager bram = named(helper, new BlockPos(3, 2, 5), "Bram", VillagerProfession.NONE);
			level.setDayTime(at(quiet(8, 1, 1, edda, bram), 1000));
			Friendship.add(edda, me, 990 - Friendship.points(edda, me.getUUID()));
			Gifts.Result cake = give(me, edda, Items.CAKE);
			helper.assertTrue(cake.outcome() == Gifts.Outcome.GIVEN && cake.change() == 10 && Friendship.points(edda, me.getUUID()) == Friendship.MAX,
				"a cake at 990: " + cake + ", " + Friendship.points(edda, me.getUUID()));
			Friendship.add(bram, me, 10);
			Gifts.Result flesh = give(me, bram, Items.ROTTEN_FLESH);
			helper.assertTrue(flesh.outcome() == Gifts.Outcome.GIVEN && flesh.change() == -10 && Friendship.points(bram, me.getUUID()) == 0,
				"rotten flesh at 10: " + flesh + ", " + Friendship.points(bram, me.getUUID()));
			helper.assertTrue(Friendship.heartsLine(edda, Friendship.points(edda, me.getUUID())).getString().equals("Edda ♥♥♥♥♥♥♥♥♥♥"), "at the top");
			helper.succeed();
		});
	}

	// --- The day and the week turning --------------------------------------------------------------------------------

	/**
	 * 31.5/31.6: one gift a day, two a week, favours once a day, at the very tick the day turns: the last tick of a day is
	 * still that day (refused), the first tick of the next is a new day; the third gift is refused to the week's last tick,
	 * and the first tick of the next week takes one. A save and reload between keeps the favour's day.
	 */
	//$ gametest_ticks_batch AREA '60' '"qaHeartsRollover"'
	@GameTest(template = AREA, timeoutTicks = 60, batch = "qaHeartsRollover")
	public void theDayAndTheWeekTurnAtTheirFirstTick(GameTestHelper helper) {
		HeartEventGameTests.village(helper, () -> {
			ServerLevel level = helper.getLevel();
			keepClock(helper);
			Villager dara = named(helper, new BlockPos(5, 2, 5), "Dara", VillagerProfession.NONE);
			ServerPlayer me = HeartEventGameTests.player(helper, new BlockPos(5, 2, 7));
			UUID id = me.getUUID();
			// A week's last two days and the next week's first two, none her name day (weeks of 7 days from day 1).
			long last = quiet(12, 4, Gifts.WEEK, dara) + 2; // days last-2 .. last+1, last a multiple of 7
			helper.assertTrue(last % Gifts.WEEK == 0, "setup: day " + last + " isn't a week's last day");

			level.setDayTime(at(last - 2, 0));
			helper.assertTrue(give(me, dara, Items.STICK).outcome() == Gifts.Outcome.GIVEN, "the first gift of the week");
			level.setDayTime(at(last - 2, VillageNeeds.DAY - 1));
			Gifts.Result late = give(me, dara, Items.STICK);
			helper.assertTrue(late.outcome() == Gifts.Outcome.DAY_LIMIT, "the same day's last tick took a second gift: " + late);
			level.setDayTime(at(last - 1, 0));
			Gifts.Result morning = give(me, dara, Items.STICK);
			helper.assertTrue(morning.outcome() == Gifts.Outcome.GIVEN, "the next day's first tick refused: " + morning);
			level.setDayTime(at(last, 0));
			Gifts.Result third = give(me, dara, Items.STICK);
			helper.assertTrue(third.outcome() == Gifts.Outcome.WEEK_LIMIT, "a third gift in the week: " + third);
			level.setDayTime(at(last, VillageNeeds.DAY - 1));
			helper.assertTrue(give(me, dara, Items.STICK).outcome() == Gifts.Outcome.WEEK_LIMIT, "the week's last tick took a third gift");
			level.setDayTime(at(last + 1, 0));
			Gifts.Result next = give(me, dara, Items.STICK);
			helper.assertTrue(next.outcome() == Gifts.Outcome.GIVEN, "the next week's first tick refused: " + next);
			helper.assertTrue(Friendship.points(dara, id) == 60, "three neutral gifts: " + Friendship.points(dara, id));

			// A trade is a favour once a day: twice on the day's first tick, once; its last tick, nothing; next day, again.
			int a = Friendship.favour(dara, me, Friendship.Favour.TRADE);
			int b = Friendship.favour(dara, me, Friendship.Favour.TRADE);
			level.setDayTime(at(last + 1, VillageNeeds.DAY - 1));
			int c = Friendship.favour(dara, me, Friendship.Favour.TRADE);
			helper.assertTrue(a == 5 && b == 0 && c == 0, "trades on one day: " + a + ", " + b + ", " + c);
			// Saved and loaded overnight, she still knows today's trade was counted, and tomorrow's counts.
			CompoundTag saved = dara.saveWithoutId(new CompoundTag());
			dara.discard();
			Villager back = EntityType.VILLAGER.create(level);
			back.load(saved);
			level.addFreshEntity(back);
			CivicEffects.forget();
			helper.assertTrue(Friendship.favour(back, me, Friendship.Favour.TRADE) == 0, "after a reload the day's trade counted again");
			level.setDayTime(at(last + 2, 0));
			helper.assertTrue(Friendship.favour(back, me, Friendship.Favour.TRADE) == 5, "the next day's trade didn't count");
			// Not a daily favour: defending her counts every time.
			helper.assertTrue(Friendship.favour(back, me, Friendship.Favour.DEFENDED) == 15 && Friendship.favour(back, me, Friendship.Favour.DEFENDED) == 15,
				"defending her twice in a day");
			helper.assertTrue(Friendship.points(back, id) == 60 + 5 + 5 + 30, "points: " + Friendship.points(back, id));
			helper.succeed();
		});
	}

	// --- Heart events in real time -----------------------------------------------------------------------------------

	/**
	 * 31.6 + 31.7 in the level's own time: Alex's gift (a cake, through the click) takes Dara from 150 to 230 points, two
	 * hearts, so she starts her story to him; two lines in, Sam gives her a gift too. Sam's gift is taken, and Alex hears
	 * the story to its end exactly once, each line in his chat in turn: +20 for him, nothing told to Sam.
	 */
	//$ gametest_ticks_batch AREA '600' '"qaHeartsGiftStory"'
	@GameTest(template = AREA, timeoutTicks = 600, batch = "qaHeartsGiftStory")
	public void aGiftReachingTwoHeartsStartsHerStoryAndAnotherGiftDoesNotCutIt(GameTestHelper helper) {
		HeartEventGameTests.village(helper, () -> {
			ServerLevel level = helper.getLevel();
			keepClock(helper);
			Villager dara = named(helper, new BlockPos(5, 2, 5), "Dara", VillagerProfession.NONE);
			level.setDayTime(at(quiet(8, 1, 1, dara), 1000));
			QaStageFiveGameTests.Client alex = client(helper, "qaha");
			QaStageFiveGameTests.Client sam = client(helper, "qahs");
			// B544: the cake once counted 20 (neutral), not 80: the taste files as shipped (cake loved by everyone), whatever
			// an earlier test loaded, as GiftGameTests does; and a failure names the band she had for it then
			Tastes.load(Tastes.files(level.getServer().getResourceManager()));
			Tastes.Band cake = Tastes.of(dara, new ItemStack(Items.CAKE));
			helper.assertTrue(cake == Tastes.Band.LOVED, "setup: Dara's band for cake is " + cake + " with the shipped taste files " + Tastes.all().keySet());
			Friendship.add(dara, alex.player(), 150);
			int before = Friendship.points(dara, alex.player().getUUID());
			alex.player().setItemInHand(InteractionHand.MAIN_HAND, Gifts.wrap(new ItemStack(Items.CAKE), alex.player().getGameProfile().getName()));
			QaStageSixGameTests.clickVillager(alex.player(), dara, alex.seen());
			helper.assertTrue(alex.player().getMainHandItem().isEmpty() && before == 150 && Friendship.points(dara, alex.player().getUUID()) == 230,
				"Alex's cake: " + before + " -> " + Friendship.points(dara, alex.player().getUUID()) + " points, holding " + alex.player().getMainHandItem()
					+ ", band now " + Tastes.of(dara, new ItemStack(Items.CAKE)) + ", told " + heard(alex, 0));
			HeartEvents.Event event = HeartEvents.pending(level, dara, alex.player().getUUID());
			helper.assertTrue(event != null && event.hearts() == 2, "at 230 points: " + (event == null ? "nothing to tell" : event.id()));
			int from = alex.seen().size();
			long[] samGave = {-1};
			// The engine's look every 10 ticks, as the server runs it (HeartEvents.AUTO is off in GameTests); then the checks.
			helper.succeedWhen(() -> {
				long now = level.getGameTime();
				if (now % HeartEvents.CHECK_EVERY == 0) {
					HeartEvents.tick(level, now);
				}
				HeartEvents.Telling t = HeartEvents.telling(dara);
				if (samGave[0] < 0 && t != null && t.said == 2) {
					sam.player().setItemInHand(InteractionHand.MAIN_HAND, Gifts.wrap(new ItemStack(Items.STICK), sam.player().getGameProfile().getName()));
					QaStageSixGameTests.clickVillager(sam.player(), dara, sam.seen());
					samGave[0] = now;
				}
				helper.assertTrue(samGave[0] >= 0, "she never got two lines into her story: " + HeartEvents.telling(dara));
				helper.assertTrue(sam.player().getMainHandItem().isEmpty() && Friendship.points(dara, sam.player().getUUID()) == 20,
					"Sam's gift mid-story wasn't taken: holding " + sam.player().getMainHandItem() + ", " + Friendship.points(dara, sam.player().getUUID()));
				List<String> told = HeartEventGameTests.told(dara, alex.player());
				t = HeartEvents.telling(dara);
				helper.assertTrue(told.size() == 1 && t == null, "not told to the end yet: told " + told + ", telling "
					+ (t == null ? "none" : t.said + " lines to " + (t.player.equals(alex.player().getUUID()) ? "Alex" : "someone else")) + ", over her head \"" + overHead(dara) + "\"");
				List<String> story = HeartEvents.lines(level, dara, alex.player(), event).stream()
					.map(l -> HeartEvents.said(dara.getDisplayName(), l).getString()).toList();
				List<String> lines = heard(alex, from).stream().filter(story::contains).toList();
				helper.assertTrue(lines.equals(story), "Alex heard " + lines.size() + " lines of a " + story.size() + "-line story, in this order: " + lines);
				lines.forEach(s -> clean(helper, "a line of her story", s));
				helper.assertTrue(Friendship.points(dara, alex.player().getUUID()) == 250, "Alex's points after the story: " + Friendship.points(dara, alex.player().getUUID()));
				helper.assertTrue(HeartEventGameTests.told(dara, sam.player()).isEmpty(), "Sam was told it too");
			});
		});
	}

	/**
	 * A story cut by her chunk unloading two lines in: nothing is told or written; loaded again (a fresh entity from her
	 * save) she starts it again from the first line, and a job change halfway doesn't stop it counting once: told, +20
	 * once, one chronicle line, and nothing more to tell at 220 points.
	 */
	//$ gametest_ticks_batch AREA '80' '"qaHeartsUnload"'
	@GameTest(template = AREA, timeoutTicks = 80, batch = "qaHeartsUnload")
	public void aStoryCutByAnUnloadStartsAgainAndCountsOnce(GameTestHelper helper) {
		HeartEventGameTests.village(helper, () -> {
			ServerLevel level = helper.getLevel();
			BlockPos hall = helper.absolutePos(HeartEventGameTests.HALL);
			Villager cora = named(helper, new BlockPos(5, 2, 5), "Cora", VillagerProfession.NONE);
			ServerPlayer me = HeartEventGameTests.player(helper, new BlockPos(5, 2, 7));
			Friendship.add(cora, me, 200);
			long start = HeartEventGameTests.later(level);
			HeartEvents.tick(level, start);
			String first = overHead(cora);
			HeartEvents.tick(level, start + HeartEvents.LINE_EVERY);
			helper.assertTrue(HeartEvents.telling(cora) != null && HeartEvents.telling(cora).said == 2 && !first.isEmpty(), "two lines in: " + HeartEvents.telling(cora));
			CompoundTag saved = cora.saveWithoutId(new CompoundTag());
			cora.remove(Entity.RemovalReason.UNLOADED_TO_CHUNK);
			HeartEvents.tick(level, start + 2 * HeartEvents.LINE_EVERY);
			helper.assertTrue(HeartEvents.telling(cora) == null, "a telling outlived her chunk");
			helper.assertTrue(HeartEventGameTests.friendLines(level, hall).isEmpty(), "a half-told story is in the chronicle");
			// Loaded again.
			Villager back = EntityType.VILLAGER.create(level);
			back.load(saved);
			helper.assertTrue(back.getUUID().equals(cora.getUUID()), "setup: a different villager came back");
			level.addFreshEntity(back);
			CivicEffects.forget();
			helper.assertTrue(HeartEventGameTests.told(back, me).isEmpty() && Friendship.points(back, me.getUUID()) == 200,
				"saved halfway: told " + HeartEventGameTests.told(back, me) + ", " + Friendship.points(back, me.getUUID()));
			long again = HeartEventGameTests.later(level);
			HeartEvents.tick(level, again);
			helper.assertTrue(HeartEvents.telling(back) != null && HeartEvents.telling(back).said == 1 && overHead(back).equals(first),
				"back from the unload she began with \"" + overHead(back) + "\", not \"" + first + "\"");
			HeartEvents.tick(level, again + HeartEvents.LINE_EVERY);
			// Given a trade halfway (a farmer now): the story goes on or starts over, but counts once.
			back.setVillagerData(back.getVillagerData().setProfession(VillagerProfession.FARMER).setLevel(1));
			for (int i = 2; i < 20 && HeartEvents.telling(back) != null; i++) {
				HeartEvents.tick(level, again + (long) i * HeartEvents.LINE_EVERY);
			}
			helper.assertTrue(HeartEvents.telling(back) == null, "still telling after 20 lines' time");
			helper.assertTrue(HeartEventGameTests.told(back, me).size() == 1 && Friendship.points(back, me.getUUID()) == 220,
				"told " + HeartEventGameTests.told(back, me) + ", " + Friendship.points(back, me.getUUID()) + " points");
			helper.assertTrue(HeartEventGameTests.friendLines(level, hall).size() == 1, "the chronicle: " + HeartEventGameTests.friendLines(level, hall));
			HeartEvents.tick(level, HeartEventGameTests.later(level));
			helper.assertTrue(HeartEvents.telling(back) == null && HeartEvents.pending(level, back, me.getUUID()) == null, "more to tell at 220 points");
			helper.succeed();
		});
	}

	/**
	 * Two friends with a story each for one player, and a second player who is a friend of one of them, all close: a player
	 * hears one story at a time and a villager tells one player at a time; in the end each pair heard theirs once (+20
	 * each), the one who isn't a friend nothing, and the chronicle has a line per telling.
	 */
	//$ gametest_ticks_batch AREA '80' '"qaHeartsOneAtATime"'
	@GameTest(template = AREA, timeoutTicks = 80, batch = "qaHeartsOneAtATime")
	public void aPlayerHearsOneStoryAtATime(GameTestHelper helper) {
		HeartEventGameTests.village(helper, () -> {
			ServerLevel level = helper.getLevel();
			BlockPos hall = helper.absolutePos(HeartEventGameTests.HALL);
			Villager cora = named(helper, new BlockPos(5, 2, 6), "Cora", VillagerProfession.NONE);
			Villager dara = named(helper, new BlockPos(5, 2, 10), "Dara", VillagerProfession.NONE);
			ServerPlayer alex = HeartEventGameTests.player(helper, new BlockPos(5, 2, 7));
			ServerPlayer sam = HeartEventGameTests.player(helper, new BlockPos(7, 2, 8));
			Friendship.add(cora, alex, 200);
			Friendship.add(dara, alex, 200);
			Friendship.add(cora, sam, 200);
			long start = HeartEventGameTests.later(level);
			for (int i = 0; i < 120; i++) {
				HeartEvents.tick(level, start + (long) i * HeartEvents.CHECK_EVERY);
				HeartEvents.Telling c = HeartEvents.telling(cora);
				HeartEvents.Telling d = HeartEvents.telling(dara);
				helper.assertTrue(c == null || d == null || !c.player.equals(d.player), "tick " + i + ": one player hears two stories at once");
				helper.assertTrue(d == null || d.player.equals(alex.getUUID()), "tick " + i + ": Dara tells someone who isn't her friend");
				if (i == 0) {
					helper.assertTrue(c != null, "nobody started at the first look");
				}
			}
			helper.assertTrue(HeartEvents.telling(cora) == null && HeartEvents.telling(dara) == null, "still telling after 120 looks");
			helper.assertTrue(HeartEventGameTests.told(cora, alex).size() == 1 && HeartEventGameTests.told(dara, alex).size() == 1
				&& HeartEventGameTests.told(cora, sam).size() == 1 && HeartEventGameTests.told(dara, sam).isEmpty(),
				"told: Cora to Alex " + HeartEventGameTests.told(cora, alex) + ", Dara to Alex " + HeartEventGameTests.told(dara, alex)
					+ ", Cora to Sam " + HeartEventGameTests.told(cora, sam) + ", Dara to Sam " + HeartEventGameTests.told(dara, sam));
			helper.assertTrue(Friendship.points(cora, alex.getUUID()) == 220 && Friendship.points(dara, alex.getUUID()) == 220
				&& Friendship.points(cora, sam.getUUID()) == 220 && Friendship.points(dara, sam.getUUID()) == 0,
				"points: " + Friendship.points(cora, alex.getUUID()) + ", " + Friendship.points(dara, alex.getUUID()) + ", "
					+ Friendship.points(cora, sam.getUUID()) + ", " + Friendship.points(dara, sam.getUUID()));
			List<String> lines = HeartEventGameTests.friendLines(level, hall);
			helper.assertTrue(lines.size() == 3, "the chronicle: " + lines);
			lines.forEach(s -> clean(helper, "a chronicle line", s));
			helper.succeed();
		});
	}

	// --- Switched off ------------------------------------------------------------------------------------------------

	/**
	 * {@code friendship} off: a Gift from the off hand (main hand empty), and from the main hand while sneaking, is handed
	 * back unopened with the off line, no points, no screen, no story and no hearts on a look. {@code heartEvents} off
	 * (friendship on): the gift is taken and counts, and she has nothing to tell; on again, she has.
	 */
	//$ gametest_ticks_batch AREA '80' '"qaHeartsOff"'
	@GameTest(template = AREA, timeoutTicks = 80, batch = "qaHeartsOff")
	public void theSwitchesOffFromTheOffHand(GameTestHelper helper) {
		HeartEventGameTests.village(helper, () -> {
			ServerLevel level = helper.getLevel();
			keepClock(helper);
			Villager dara = named(helper, new BlockPos(5, 2, 5), "Dara", VillagerProfession.NONE);
			level.setDayTime(at(quiet(8, 1, 1, dara), 1000));
			QaStageFiveGameTests.Client c = client(helper, "qaho");
			ServerPlayer p = c.player();
			String me = p.getGameProfile().getName();
			Friendship.add(dara, p, 200);
			Friendship.ENABLED = false;
			String off = Component.translatable("message.aliveworkplace.gift.off", dara.getDisplayName()).getString();
			clean(helper, "the off line", off);
			for (boolean sneak : new boolean[] {false, true}) {
				InteractionHand hand = sneak ? InteractionHand.MAIN_HAND : InteractionHand.OFF_HAND;
				p.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
				p.setItemInHand(InteractionHand.OFF_HAND, ItemStack.EMPTY);
				p.setItemInHand(hand, Gifts.wrap(new ItemStack(Items.CAKE), me));
				p.setShiftKeyDown(sneak);
				int before = c.seen().size();
				QaStageSixGameTests.clickVillager(p, dara, c.seen());
				String what = sneak ? "main hand, sneaking" : "off hand";
				helper.assertTrue(p.getItemInHand(hand).is(ModItems.GIFT) && Gifts.contents(p.getItemInHand(hand)).is(Items.CAKE),
					what + ": friendship off, and the Gift wasn't handed back unopened: " + p.getItemInHand(hand));
				helper.assertTrue(Friendship.points(dara, p.getUUID()) == 200 && Friendship.of(dara).bond(p.getUUID()).giftDay() < 0,
					what + ": friendship off, and the gift counted: " + Friendship.of(dara).bond(p.getUUID()));
				helper.assertTrue(heard(c, before).contains(off), what + ": the off line wasn't shown: " + heard(c, before));
				helper.assertTrue(p.containerMenu == p.inventoryMenu, what + ": a screen opened");
			}
			p.setShiftKeyDown(false);
			helper.assertTrue(Friendship.lookLine(p) == null, "friendship off, and hearts on a look");
			HeartEvents.tick(level, HeartEventGameTests.later(level));
			helper.assertTrue(HeartEvents.telling(dara) == null && HeartEvents.pending(level, dara, p.getUUID()) == null, "friendship off, and a story");
			// Heart events off, friendship on: the gift counts, the story waits.
			Friendship.ENABLED = true;
			HeartEvents.ENABLED = false;
			p.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
			p.setItemInHand(InteractionHand.OFF_HAND, Gifts.wrap(new ItemStack(Items.CAKE), me));
			QaStageSixGameTests.clickVillager(p, dara, c.seen());
			helper.assertTrue(p.getOffhandItem().isEmpty() && Friendship.points(dara, p.getUUID()) == 280, "heart events off, the gift: "
				+ Friendship.points(dara, p.getUUID()) + ", holding " + p.getOffhandItem());
			HeartEvents.tick(level, HeartEventGameTests.later(level));
			helper.assertTrue(HeartEvents.telling(dara) == null && HeartEvents.pending(level, dara, p.getUUID()) == null, "heart events off, and a story");
			HeartEvents.ENABLED = true;
			helper.assertTrue(HeartEvents.pending(level, dara, p.getUUID()) != null, "on again, nothing to tell at 280 points");
			helper.succeed();
		});
	}

	// --- Every sentence ----------------------------------------------------------------------------------------------

	private static final Pattern INDEXED = Pattern.compile("%(\\d+)\\$s");
	private static final Pattern PLAIN = Pattern.compile("%s");

	/**
	 * Every sentence a player reads here: every heart event's lines, chronicle line and life-story line, as told to a
	 * player by a villager of each job family (all with their real arguments: no raw key, placeholder or "null"), each
	 * event 3 to 5 lines with no argument past the ones it is given; the gift, keepsake and hearts messages; each family's
	 * keepsake name; and the job a jobless friend is told about, for every job there is.
	 */
	//$ gametest_ticks_batch AREA '80' '"qaHeartsText"'
	@GameTest(template = AREA, timeoutTicks = 80, batch = "qaHeartsText")
	public void everySentenceAPlayerReadsIsWritten(GameTestHelper helper) {
		HeartEventGameTests.village(helper, () -> {
			ServerLevel level = helper.getLevel();
			Villager dara = named(helper, new BlockPos(5, 2, 5), "Dara", VillagerProfession.NONE);
			ServerPlayer me = HeartEventGameTests.player(helper, new BlockPos(5, 2, 7));
			Language language = Language.getInstance();
			int nargs = HeartEvents.args(level, dara, Component.literal("Jesse")).length;
			List<String> problems = new ArrayList<>();
			helper.assertTrue(!HeartEvents.all().isEmpty(), "no heart events loaded");
			Set<Integer> levels = new HashSet<>();
			for (HeartEvents.Event event : HeartEvents.all().values()) {
				levels.add(event.hearts());
				if (event.lines().size() < HeartEvents.MIN_LINES || event.lines().size() > HeartEvents.MAX_LINES) {
					problems.add(event.id() + " has " + event.lines().size() + " lines");
				}
				List<String> keys = new ArrayList<>(event.lines());
				keys.add(event.chronicle());
				keys.add(event.story());
				for (String key : keys) {
					if (!language.has(key)) {
						problems.add(event.id() + ": no text for " + key);
						continue;
					}
					String raw = language.getOrDefault(key);
					Matcher m = INDEXED.matcher(raw);
					while (m.find()) {
						if (Integer.parseInt(m.group(1)) > nargs) {
							problems.add(key + " asks for argument " + m.group(1) + " of " + nargs);
						}
					}
					int plain = 0;
					Matcher pm = PLAIN.matcher(raw);
					while (pm.find()) {
						plain++;
					}
					if (plain > nargs) {
						problems.add(key + " has " + plain + " %s of " + nargs);
					}
				}
			}
			helper.assertTrue(levels.containsAll(List.of(2, 4, 6, 8, 10)), "heart levels with events: " + levels);
			// Rendered, as told by a villager of every job family.
			for (VillagerProfession job : List.of(VillagerProfession.NONE, VillagerProfession.FARMER, ModVillagers.BUILDER, ModVillagers.MINER,
					ModVillagers.GUARD, ModVillagers.NURSE, ModVillagers.BARD, ModVillagers.SCHOLAR, ModVillagers.SHOPKEEPER, ModVillagers.CHEF,
					VillagerProfession.FISHERMAN, ModVillagers.TRAINER)) {
				dara.setVillagerData(dara.getVillagerData().setProfession(job));
				Object[] args = HeartEvents.args(level, dara, Component.literal(me.getGameProfile().getName()));
				for (HeartEvents.Event event : HeartEvents.all().values()) {
					List<String> rendered = new ArrayList<>(HeartEvents.lines(level, dara, me, event).stream().map(Component::getString).toList());
					rendered.add(Component.translatable(event.chronicle(), args).getString());
					rendered.add(Component.translatable(event.story(), args).getString());
					rendered.add(HeartEvents.said(dara.getDisplayName(), HeartEvents.lines(level, dara, me, event).get(0)).getString());
					for (String s : rendered) {
						if (s.isBlank() || s.contains("%") || s.contains("aliveworkplace.") || s.contains("minecraft.") || s.contains("null")) {
							problems.add(job.name() + "/" + event.id() + ": \"" + s + "\"");
						}
					}
				}
			}
			// The fixed messages, with their arguments.
			Component who = Component.literal("Dara");
			Component player = Component.literal("Jesse");
			List<Component> fixed = List.of(
				Component.translatable("message.aliveworkplace.gift.off", who),
				Component.translatable("message.aliveworkplace.gift.unnamed"),
				Component.translatable("message.aliveworkplace.gift.no_hall", who),
				Component.translatable("message.aliveworkplace.gift.empty"),
				Component.translatable("message.aliveworkplace.gift.said", who, Component.literal("Thanks")),
				Component.translatable("gift.aliveworkplace.refuse.day", player),
				Component.translatable("gift.aliveworkplace.refuse.week", player),
				Component.translatable("gift.aliveworkplace.name_day", Component.literal("Cake!")),
				Component.translatable("message.aliveworkplace.keepsake.given", who, Component.literal("Dara's Trowel")),
				Component.translatable("message.aliveworkplace.heart_event.said", who, Component.literal("Once")),
				Friendship.heartsLine(dara, 300),
				Gifts.nameDayLine(dara, 40));
			for (Component line : fixed) {
				clean(helper, "a fixed message", line.getString());
			}
			helper.assertTrue(Component.translatable("message.aliveworkplace.keepsake.given", who, Component.literal("Dara's Trowel")).getString()
				.equals("Dara gave you a keepsake: Dara's Trowel"), "the keepsake line");
			for (JobFamilies.Family family : JobFamilies.Family.values()) {
				String name = Keepsakes.name(family, who).getString();
				if (!name.startsWith("Dara") || name.contains("%") || name.contains("aliveworkplace.")) {
					problems.add("keepsake of " + family + ": \"" + name + "\"");
				}
			}
			// A jobless friend is told of the nearest free workstation by its job's name: every job has one.
			for (VillagerProfession job : BuiltInRegistries.VILLAGER_PROFESSION) {
				if (job == VillagerProfession.NONE || job == VillagerProfession.NITWIT) {
					continue;
				}
				String key = "entity.minecraft.villager." + job.name();
				if (!language.has(key)) {
					problems.add("no job name for " + BuiltInRegistries.VILLAGER_PROFESSION.getKey(job) + " (" + key + ")");
				}
			}
			helper.assertTrue(problems.isEmpty(), problems.size() + " sentences: " + problems.subList(0, Math.min(8, problems.size())));
			helper.succeed();
		});
	}
}
