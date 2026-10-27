package io.github.jcondedata.aliveworkplace.test;

import io.github.jcondedata.aliveworkplace.hall.Chronicle;
import io.github.jcondedata.aliveworkplace.hall.VillageNeeds;
import io.github.jcondedata.aliveworkplace.registry.ModItems;
import io.github.jcondedata.aliveworkplace.story.Friendship;
import io.github.jcondedata.aliveworkplace.story.Gifts;
import io.github.jcondedata.aliveworkplace.story.HeartEvents;
import io.github.jcondedata.aliveworkplace.story.Keepsakes;
import io.github.jcondedata.aliveworkplace.story.Tastes;
import java.util.ArrayList;
import java.util.List;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.npc.VillagerProfession;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;

/**
 * QA (lane-c-1027-2153): gifts and heart events (ROADMAP 31.6-31.8), written from the spec's "Done when" for the corners
 * the builders' tests and the earlier QA class leave: the name day tripling every band (the bad ones too) and only on
 * that day; the day and week limits kept per player and per villager; a nitwit's and a child's gifts (their tastes, and
 * no XP for someone without a trade); a story cut by the villager dying, leaving, or the listener turning spectator;
 * and a villager who is already at ten hearts telling every level's event in turn, then her keepsake, once.
 */
public class QaHeartsGiftsGameTests implements FabricGameTest {
	private static final String AREA = "aliveworkplace_test:big_area";

	// --- Setting up -----------------------------------------------------------------------------------------------

	private static void keepClock(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		long dayTime = level.getDayTime();
		Leftovers.after(helper, () -> level.setDayTime(dayTime));
	}

	private static void setDay(GameTestHelper helper, long day) {
		helper.getLevel().setDayTime((day - 1) * VillageNeeds.DAY + 1000);
		helper.assertTrue(Chronicle.day(helper.getLevel()) == day, "setup: the clock reads day " + Chronicle.day(helper.getLevel()) + ", not " + day);
	}

	/** The first day of a week (from day 8) from which, for ten days, it is none of the villagers' name day. */
	private static long quietWeek(Villager... villagers) {
		for (long day = 8; ; day += Gifts.WEEK) {
			boolean quiet = true;
			for (Villager v : villagers) {
				for (int i = 0; i < 10; i++) {
					quiet &= Gifts.daysToNameDay(v.getUUID(), day + i) != 0;
				}
			}
			if (quiet) {
				return day;
			}
		}
	}

	private static Villager villager(GameTestHelper helper, BlockPos at, String name, VillagerProfession job) {
		Villager v = HeartEventGameTests.villager(helper, at, name);
		v.setVillagerData(v.getVillagerData().setProfession(job).setLevel(job == VillagerProfession.NONE || job == VillagerProfession.NITWIT ? 1 : 2));
		return v;
	}

	private static Gifts.Result give(ServerPlayer player, Villager villager, Item item) {
		player.setItemInHand(InteractionHand.MAIN_HAND, Gifts.wrap(new ItemStack(item), player.getGameProfile().getName()));
		return Gifts.give(player, villager, player.getMainHandItem());
	}

	/** A sentence a player reads: written out, arguments filled in, no raw key, no placeholder. */
	private static void clean(GameTestHelper helper, String what, String s) {
		helper.assertTrue(!s.isBlank() && !s.contains("%") && !s.contains("aliveworkplace.") && !s.contains("minecraft.") && !s.contains("null")
			&& !s.contains("{}"), what + " reads \"" + s + "\"");
	}

	// --- Name days -------------------------------------------------------------------------------------------------

	/**
	 * 31.6: "on a villager's name day a gift counts three times", whichever band: loved 240, liked 135, neutral 60,
	 * disliked −60, hated −120 (each from a different player, so the day's limit doesn't stand in the way), each line ending
	 * "And on my name day, too!"; a Bottle o' Enchanting to a worker on hers is liked three times over. The day before and
	 * the day after count once.
	 */
	//$ gametest_ticks_batch AREA '80' '"qaHgNameDay"'
	@GameTest(template = AREA, timeoutTicks = 80, batch = "qaHgNameDay")
	public void aNameDayTriplesEveryBandAndOnlyThatDay(GameTestHelper helper) {
		HeartEventGameTests.village(helper, () -> {
			keepClock(helper);
			Villager nell = villager(helper, new BlockPos(5, 2, 5), "Nell", VillagerProfession.NONE);
			Villager mia = villager(helper, new BlockPos(12, 2, 5), "Mia", VillagerProfession.MASON);
			long day = Gifts.nameDay(nell.getUUID()) + 1 + 28L;
			helper.assertTrue(Gifts.isNameDay(nell, day) && !Gifts.isNameDay(nell, day - 1) && !Gifts.isNameDay(nell, day + 1), "setup: day " + day);
			setDay(helper, day);
			record Case(Item item, Tastes.Band band, int change) {
			}
			List<Case> cases = List.of(new Case(Items.EMERALD, Tastes.Band.LOVED, 240), new Case(Items.BREAD, Tastes.Band.LIKED, 135),
				new Case(Items.STICK, Tastes.Band.NEUTRAL, 60), new Case(Items.DIRT, Tastes.Band.DISLIKED, -60),
				new Case(Items.ROTTEN_FLESH, Tastes.Band.HATED, -120));
			List<String> wrong = new ArrayList<>();
			int i = 0;
			for (Case c : cases) {
				ServerPlayer p = HeartEventGameTests.player(helper, new BlockPos(5, 2, 7 + (i++ % 3)));
				Friendship.add(nell, p, 500);
				Gifts.Result r = give(p, nell, c.item());
				String line = r.line().getString();
				clean(helper, c.item() + " on her name day", line);
				if (r.outcome() != Gifts.Outcome.GIVEN || r.band() != c.band() || r.change() != c.change() || !r.nameDay()
					|| Friendship.points(nell, p.getUUID()) != 500 + c.change() || !line.endsWith(" And on my name day, too!")) {
					wrong.add(c.item() + ": " + r.outcome() + " " + r.band() + " " + r.change() + " nameDay=" + r.nameDay() + " points="
						+ Friendship.points(nell, p.getUUID()) + " \"" + line + "\"");
				}
				helper.assertTrue(p.getMainHandItem().isEmpty(), c.item() + ": the gift stayed in hand");
			}
			helper.assertTrue(wrong.isEmpty(), "on her name day: " + wrong);

			// A worker's name day: the bottle is liked three times over, and still taught her (used up).
			long hers = Gifts.nameDay(mia.getUUID()) + 1 + 56L;
			setDay(helper, hers);
			ServerPlayer alex = HeartEventGameTests.player(helper, new BlockPos(12, 2, 7));
			int xp = mia.getVillagerXp();
			Gifts.Result bottle = give(alex, mia, Items.EXPERIENCE_BOTTLE);
			helper.assertTrue(bottle.band() == Tastes.Band.LIKED && bottle.change() == 135 && bottle.nameDay() && bottle.stored() == Gifts.Stored.USED
				&& mia.getVillagerXp() > xp, "a bottle on a worker's name day: " + bottle + ", XP " + xp + " -> " + mia.getVillagerXp());

			// The day before and the day after hers: once.
			for (long d : new long[] {day - 1, day + 1}) {
				setDay(helper, d);
				ServerPlayer p = HeartEventGameTests.player(helper, new BlockPos(4, 2, 8));
				Gifts.Result r = give(p, nell, Items.EMERALD);
				helper.assertTrue(r.outcome() == Gifts.Outcome.GIVEN && r.change() == 80 && !r.nameDay() && !r.line().getString().contains("name day"),
					"day " + d + " (her name day is " + day + "): " + r + " \"" + r.line().getString() + "\"");
			}
			helper.succeed();
		});
	}

	// --- Limits per player and per villager --------------------------------------------------------------------------

	/**
	 * 31.6: "Each player can give each villager one gift a day and two a week". One player's gift doesn't use up another's
	 * day or week, nor the same player's day with another villager; a refused gift stays in hand, unopened, with the
	 * villager's line, and counts nothing.
	 */
	//$ gametest_ticks_batch AREA '80' '"qaHgLimits"'
	@GameTest(template = AREA, timeoutTicks = 80, batch = "qaHgLimits")
	public void theLimitsArePerPlayerAndPerVillager(GameTestHelper helper) {
		HeartEventGameTests.village(helper, () -> {
			keepClock(helper);
			Villager dara = villager(helper, new BlockPos(5, 2, 5), "Dara", VillagerProfession.NONE);
			Villager bram = villager(helper, new BlockPos(12, 2, 5), "Bram", VillagerProfession.NONE);
			long start = quietWeek(dara, bram);
			helper.assertTrue(Gifts.week(start) == Gifts.week(start + 6) && Gifts.week(start) != Gifts.week(start - 1), "setup: day " + start + " doesn't start a week");
			ServerPlayer alex = HeartEventGameTests.player(helper, new BlockPos(5, 2, 7));
			ServerPlayer sam = HeartEventGameTests.player(helper, new BlockPos(6, 2, 7));
			String alexName = alex.getGameProfile().getName();

			setDay(helper, start);
			helper.assertTrue(give(alex, dara, Items.STICK).outcome() == Gifts.Outcome.GIVEN, "Alex's first gift to Dara");
			helper.assertTrue(give(alex, bram, Items.STICK).outcome() == Gifts.Outcome.GIVEN, "Alex's gift to Dara used up his day with Bram");
			helper.assertTrue(give(sam, dara, Items.STICK).outcome() == Gifts.Outcome.GIVEN, "Alex's gift to Dara used up Sam's day with her");
			Gifts.Result again = give(alex, dara, Items.CAKE);
			helper.assertTrue(again.outcome() == Gifts.Outcome.DAY_LIMIT && again.line().getString().equals("You already gave me something today, " + alexName + ". Keep it for tomorrow."),
				"a second gift the same day: " + again.outcome() + " \"" + again.line().getString() + "\"");
			helper.assertTrue(alex.getMainHandItem().is(ModItems.GIFT) && Gifts.contents(alex.getMainHandItem()).is(Items.CAKE) && alex.getMainHandItem().getCount() == 1,
				"the refused gift: " + alex.getMainHandItem());
			helper.assertTrue(Friendship.points(dara, alex.getUUID()) == 20, "the refused cake counted: " + Friendship.points(dara, alex.getUUID()));

			setDay(helper, start + 1);
			helper.assertTrue(give(alex, dara, Items.STICK).outcome() == Gifts.Outcome.GIVEN, "Alex's second gift of the week to Dara");
			setDay(helper, start + 3);
			Gifts.Result third = give(alex, dara, Items.CAKE);
			helper.assertTrue(third.outcome() == Gifts.Outcome.WEEK_LIMIT && third.line().getString().equals("Two gifts in one week is plenty, " + alexName + ". Keep it for next week."),
				"a third gift in the week: " + third.outcome() + " \"" + third.line().getString() + "\"");
			helper.assertTrue(alex.getMainHandItem().is(ModItems.GIFT) && Friendship.points(dara, alex.getUUID()) == 40, "the week's refused gift: " + alex.getMainHandItem()
				+ ", " + Friendship.points(dara, alex.getUUID()));
			// His week with Dara is full; with Bram it isn't, and Sam's with Dara isn't either.
			helper.assertTrue(give(alex, bram, Items.STICK).outcome() == Gifts.Outcome.GIVEN, "Alex's full week with Dara refused his gift to Bram");
			helper.assertTrue(give(sam, dara, Items.STICK).outcome() == Gifts.Outcome.GIVEN, "Alex's full week with Dara refused Sam's gift");
			helper.assertTrue(Friendship.points(dara, sam.getUUID()) == 40 && Friendship.points(bram, alex.getUUID()) == 40, "points: Sam with Dara "
				+ Friendship.points(dara, sam.getUUID()) + ", Alex with Bram " + Friendship.points(bram, alex.getUUID()));
			helper.succeed();
		});
	}

	// --- Nitwits and children ----------------------------------------------------------------------------------------

	/**
	 * 31.6: "no trade yet (jobless, nitwits): love emeralds; like bread and beds", and a Bottle o' Enchanting gives XP to
	 * a worker only: a named nitwit loves an emerald and likes a bed; a named child likes the bottle but learns nothing from
	 * it, and it is kept rather than used up.
	 */
	//$ gametest_ticks_batch AREA '80' '"qaHgNitwit"'
	@GameTest(template = AREA, timeoutTicks = 80, batch = "qaHgNitwit")
	public void aNitwitAndAChildTakeTheirGifts(GameTestHelper helper) {
		HeartEventGameTests.village(helper, () -> {
			keepClock(helper);
			Villager nitwit = villager(helper, new BlockPos(5, 2, 5), "Nils", VillagerProfession.NITWIT);
			Villager pip = villager(helper, new BlockPos(12, 2, 5), "Pip", VillagerProfession.NONE);
			pip.setAge(-24000);
			helper.assertTrue(pip.isBaby(), "setup: Pip isn't a child");
			setDay(helper, quietWeek(nitwit, pip));
			ServerPlayer alex = HeartEventGameTests.player(helper, new BlockPos(5, 2, 7));
			ServerPlayer sam = HeartEventGameTests.player(helper, new BlockPos(6, 2, 7));

			Gifts.Result emerald = give(alex, nitwit, Items.EMERALD);
			helper.assertTrue(emerald.outcome() == Gifts.Outcome.GIVEN && emerald.band() == Tastes.Band.LOVED && emerald.change() == 80, "an emerald to a nitwit: " + emerald);
			Gifts.Result bed = give(sam, nitwit, Items.RED_BED);
			helper.assertTrue(bed.band() == Tastes.Band.LIKED && bed.change() == 45, "a bed to a nitwit: " + bed);
			clean(helper, "the nitwit's line", emerald.line().getString());

			int xp = pip.getVillagerXp();
			Gifts.Result bottle = give(alex, pip, Items.EXPERIENCE_BOTTLE);
			helper.assertTrue(bottle.outcome() == Gifts.Outcome.GIVEN && bottle.band() == Tastes.Band.LIKED && bottle.change() == 45, "a bottle to a child: " + bottle);
			helper.assertTrue(bottle.stored() != Gifts.Stored.USED && pip.getVillagerXp() == xp, "a child learned from the bottle: " + bottle.stored() + ", XP " + pip.getVillagerXp());
			Gifts.Result bread = give(sam, pip, Items.BREAD);
			helper.assertTrue(bread.band() == Tastes.Band.LIKED && bread.change() == 45, "bread to a child: " + bread);
			helper.succeed();
		});
	}

	// --- A story cut short -------------------------------------------------------------------------------------------

	/**
	 * 31.7: a story told halfway counts for nothing. Dara dies after her first line and Bram leaves (unloaded) after
	 * his: both tellings end, neither is noted as told, no +20, and nothing goes in the chronicle.
	 */
	//$ gametest_ticks_batch AREA '80' '"qaHgDeath"'
	@GameTest(template = AREA, timeoutTicks = 80, batch = "qaHgDeath")
	public void aStoryEndsWhenTheTellerDiesOrLeaves(GameTestHelper helper) {
		HeartEventGameTests.village(helper, () -> {
			ServerLevel level = helper.getLevel();
			BlockPos hall = helper.absolutePos(HeartEventGameTests.HALL);
			Villager dara = villager(helper, new BlockPos(2, 2, 2), "Dara", VillagerProfession.NONE);
			Villager bram = villager(helper, new BlockPos(16, 2, 16), "Bram", VillagerProfession.NONE);
			ServerPlayer alex = HeartEventGameTests.player(helper, new BlockPos(2, 2, 4));
			ServerPlayer sam = HeartEventGameTests.player(helper, new BlockPos(16, 2, 14));
			Friendship.add(dara, alex, 200);
			Friendship.add(bram, sam, 200);
			long t = HeartEventGameTests.later(level);
			HeartEvents.tick(level, t);
			HeartEvents.Telling d = HeartEvents.telling(dara);
			HeartEvents.Telling b = HeartEvents.telling(bram);
			helper.assertTrue(d != null && d.said == 1 && d.player.equals(alex.getUUID()), "Dara didn't start her story to Alex: " + (d == null ? "nothing" : d.said));
			helper.assertTrue(b != null && b.said == 1 && b.player.equals(sam.getUUID()), "Bram didn't start his story to Sam: " + (b == null ? "nothing" : b.said));

			dara.kill();
			bram.discard();
			HeartEvents.tick(level, t + HeartEvents.LINE_EVERY);
			HeartEvents.tick(level, t + 2L * HeartEvents.LINE_EVERY);
			helper.assertTrue(HeartEvents.telling(dara) == null && HeartEvents.telling(bram) == null, "a telling went on without its teller");
			helper.assertTrue(HeartEventGameTests.told(dara, alex).isEmpty() && HeartEventGameTests.told(bram, sam).isEmpty(),
				"half a story was noted as told: " + HeartEventGameTests.told(dara, alex) + " " + HeartEventGameTests.told(bram, sam));
			helper.assertTrue(Friendship.points(dara, alex.getUUID()) == 200 && Friendship.points(bram, sam.getUUID()) == 200,
				"half a story gave points: " + Friendship.points(dara, alex.getUUID()) + ", " + Friendship.points(bram, sam.getUUID()));
			helper.assertTrue(HeartEventGameTests.friendLines(level, hall).isEmpty(), "the chronicle: " + HeartEventGameTests.friendLines(level, hall));
			helper.succeed();
		});
	}

	/**
	 * 31.7: a listener who turns spectator halfway is no longer listening; back in the game, she starts again from the
	 * first line next time, and the whole story counts once.
	 */
	//$ gametest_ticks_batch AREA '80' '"qaHgSpectator"'
	@GameTest(template = AREA, timeoutTicks = 80, batch = "qaHgSpectator")
	public void aSpectatorIsNotListeningAndTheStoryStartsAgain(GameTestHelper helper) {
		HeartEventGameTests.village(helper, () -> {
			ServerLevel level = helper.getLevel();
			BlockPos hall = helper.absolutePos(HeartEventGameTests.HALL);
			Villager cora = villager(helper, new BlockPos(5, 2, 4), "Cora", VillagerProfession.NONE);
			// A player on a real connection: the GameTest mock player always says it isn't a spectator.
			ServerPlayer alex = QaStageSixGameTests.client(helper, GameType.SURVIVAL, QaStageSixGameTests.name("qahgs")).player();
			net.minecraft.world.phys.Vec3 spot = helper.absoluteVec(net.minecraft.world.phys.Vec3.atBottomCenterOf(new BlockPos(5, 2, 6)));
			alex.moveTo(spot.x, spot.y, spot.z, 0, 0);
			Friendship.add(cora, alex, 200);
			HeartEvents.Event event = HeartEvents.pending(level, cora, alex.getUUID());
			helper.assertTrue(event != null && event.hearts() == 2, "nothing to tell at 2 hearts");
			long t = HeartEventGameTests.later(level);
			HeartEvents.tick(level, t);
			helper.assertTrue(HeartEvents.telling(cora) != null && HeartEvents.telling(cora).said == 1, "she didn't start");
			alex.setGameMode(GameType.SPECTATOR);
			helper.assertTrue(alex.isSpectator(), "setup: the player isn't a spectator");
			HeartEvents.tick(level, t + HeartEvents.LINE_EVERY);
			helper.assertTrue(HeartEvents.telling(cora) == null, "she went on telling a spectator: said " + (HeartEvents.telling(cora) == null ? 0 : HeartEvents.telling(cora).said));
			HeartEvents.tick(level, HeartEventGameTests.later(level));
			helper.assertTrue(HeartEvents.telling(cora) == null, "she started telling a spectator");
			helper.assertTrue(HeartEventGameTests.told(cora, alex).isEmpty() && Friendship.points(cora, alex.getUUID()) == 200, "half a story counted");

			alex.setGameMode(GameType.SURVIVAL);
			List<String> lines = HeartEventGameTests.listen(helper, cora, alex, HeartEventGameTests.later(level), event.lines().size());
			List<String> expected = HeartEvents.lines(level, cora, alex, event).stream().map(Component::getString).toList();
			helper.assertTrue(lines.equals(expected), "back in the game she said " + lines + ", not from the first line " + expected);
			helper.assertTrue(HeartEventGameTests.told(cora, alex).equals(List.of(event.id().toString())) && Friendship.points(cora, alex.getUUID()) == 220,
				"told " + HeartEventGameTests.told(cora, alex) + ", " + Friendship.points(cora, alex.getUUID()) + " points");
			helper.assertTrue(HeartEventGameTests.friendLines(level, hall).size() == 1, "the chronicle: " + HeartEventGameTests.friendLines(level, hall));
			helper.succeed();
		});
	}

	// --- Ten hearts at once ------------------------------------------------------------------------------------------

	/**
	 * 31.7 + 31.8: a villager the player already has ten hearts with (points given by other means) tells every level's
	 * event in turn, lowest first (2, 4, 6, 8, 10), each in three to five lines, each once, each a chronicle line and a
	 * life-story line; after the tenth, her keepsake ("Cora's Pressed Flower", a cornflower for someone with no trade yet),
	 * once; and nothing more after.
	 */
	//$ gametest_ticks_batch AREA '100' '"qaHgTen"'
	@GameTest(template = AREA, timeoutTicks = 100, batch = "qaHgTen")
	public void tenHeartsAtOnceTellsEachLevelInTurnThenTheKeepsakeOnce(GameTestHelper helper) {
		HeartEventGameTests.village(helper, () -> {
			ServerLevel level = helper.getLevel();
			BlockPos hall = helper.absolutePos(HeartEventGameTests.HALL);
			Villager cora = villager(helper, new BlockPos(5, 2, 4), "Cora", VillagerProfession.NONE);
			ServerPlayer alex = HeartEventGameTests.player(helper, new BlockPos(5, 2, 6));
			Friendship.add(cora, alex, Friendship.MAX);
			List<Integer> heard = new ArrayList<>();
			for (int hearts = 2; hearts <= Friendship.HEARTS; hearts += 2) {
				HeartEvents.Event event = HeartEvents.pending(level, cora, alex.getUUID());
				helper.assertTrue(event != null && event.hearts() == hearts, "after the " + heard + "-heart events she has " + (event == null ? "nothing" : event.id() + " at " + event.hearts())
					+ " to tell, not a " + hearts + "-heart event");
				helper.assertTrue(!Keepsakes.given(cora, alex), "a keepsake before the 10-heart event, after " + heard);
				int n = event.lines().size();
				helper.assertTrue(n >= HeartEvents.MIN_LINES && n <= HeartEvents.MAX_LINES, event.id() + " has " + n + " lines");
				List<String> lines = HeartEventGameTests.listen(helper, cora, alex, HeartEventGameTests.later(level), n);
				for (String line : lines) {
					clean(helper, event.id().toString(), line);
				}
				heard.add(hearts);
			}
			helper.assertTrue(HeartEventGameTests.told(cora, alex).size() == 5, "told: " + HeartEventGameTests.told(cora, alex));
			helper.assertTrue(HeartEvents.pending(level, cora, alex.getUUID()) == null, "a sixth story: " + HeartEvents.pending(level, cora, alex.getUUID()));
			helper.assertTrue(HeartEvents.toldBy(cora).size() == 5, "her life story has " + HeartEvents.toldBy(cora).size() + " events");
			helper.assertTrue(HeartEventGameTests.friendLines(level, hall).size() == 5, "the chronicle: " + HeartEventGameTests.friendLines(level, hall));
			helper.assertTrue(Keepsakes.given(cora, alex), "no keepsake after the 10-heart event");
			int flowers = alex.getInventory().countItem(Items.CORNFLOWER);
			ItemStack flower = ItemStack.EMPTY;
			for (int i = 0; i < alex.getInventory().getContainerSize(); i++) {
				if (alex.getInventory().getItem(i).is(Items.CORNFLOWER)) {
					flower = alex.getInventory().getItem(i);
				}
			}
			helper.assertTrue(flowers == 1 && flower.getHoverName().getString().equals("Cora's Pressed Flower"), "the keepsake: " + flowers + " cornflowers, \"" + flower.getHoverName().getString() + "\"");
			// Nothing more: no story and no second keepsake, however long he stays.
			HeartEvents.tick(level, HeartEventGameTests.later(level));
			HeartEvents.tick(level, HeartEventGameTests.later(level));
			helper.assertTrue(HeartEvents.telling(cora) == null && alex.getInventory().countItem(Items.CORNFLOWER) == 1, "more after the last story");
			helper.assertTrue(Keepsakes.give(level, cora, alex) == null && alex.getInventory().countItem(Items.CORNFLOWER) == 1, "a second keepsake");
			helper.succeed();
		});
	}
}
