package io.github.jcondedata.aliveworkplace.test;

import io.github.jcondedata.aliveworkplace.hall.Chronicle;
import io.github.jcondedata.aliveworkplace.hall.VillageHallBlockEntity;
import io.github.jcondedata.aliveworkplace.hall.VillageNeeds;
import io.github.jcondedata.aliveworkplace.hall.VillageRanks;
import io.github.jcondedata.aliveworkplace.herb.HerbBlock;
import io.github.jcondedata.aliveworkplace.people.Families;
import io.github.jcondedata.aliveworkplace.people.Sickness;
import io.github.jcondedata.aliveworkplace.registry.ModAttachments;
import io.github.jcondedata.aliveworkplace.story.ArcState;
import io.github.jcondedata.aliveworkplace.story.Arcs;
import io.github.jcondedata.aliveworkplace.story.Friendship;
import io.github.jcondedata.aliveworkplace.story.HeartEvents;
import io.github.jcondedata.aliveworkplace.story.Reputation;
import io.github.jcondedata.aliveworkplace.story.SicknessStory;
import io.github.jcondedata.aliveworkplace.threat.Threats;
import io.github.jcondedata.aliveworkplace.work.WorkerStatus;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.TextColor;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;

/**
 * QA (Check tier, 2026-10-31) for heart events (ROADMAP 31.7) and the Grey Cough (31.18), written from the spec, for
 * what the earlier tests leave out. 31.7: each line reaches only the listener's chat, in gray, as "Name: line"; a friend
 * who stays exactly 8 blocks off is still told, a step past 8 stops it and next time it starts over from the first line.
 * 31.18: a threat arc waits while the village is At peace (or the threat level is 0); a remedy eases the cough for
 * exactly one day; the brew takes half a day to the tick and reads "Brewing the cure" at 40% over the healer's head;
 * the pass-on in an even village reaches exactly half and never more; while the cough runs its course nobody else
 * catches it, the healer takes no herbs, nobody dies and a player who handed in some herbs gets no ending reward; and
 * the ending's +100 friendship goes to the villagers who were ill, exactly, and to nobody else.
 */
public class QaHeartsSickness1031GameTests implements FabricGameTest {
	static final String TALL = QaSicknessGameTests.TALL;
	static final String AREA = HeartEventGameTests.AREA;
	static final BlockPos HALL = QaSicknessGameTests.HALL;
	static final String SAID = "message.aliveworkplace.heart_event.said";

	// ---- helpers ---------------------------------------------------------------------------------------------------

	/** The distinct chat messages with {@code key} ({@code displayClientMessage} records each line twice). */
	static List<Component> distinct(List<Component> seen, String key) {
		Set<Component> once = Collections.newSetFromMap(new IdentityHashMap<>());
		List<Component> out = new ArrayList<>();
		for (Component c : seen) {
			if (key.equals(QaStageFourGameTests.key(c)) && once.add(c)) {
				out.add(c);
			}
		}
		return out;
	}

	static String overHead(Villager villager) {
		WorkerStatus.Entry entry = WorkerStatus.get(villager, villager.level().getGameTime());
		return entry == null || entry.line() == null ? "" : entry.line().getString();
	}

	static void stand(GameTestHelper helper, ServerPlayer player, double x, double z) {
		Vec3 pos = helper.absoluteVec(new Vec3(x, 2, z));
		player.moveTo(pos.x, pos.y, pos.z, 0, 0);
	}

	// ---- 31.7: heart events ----------------------------------------------------------------------------------------

	/**
	 * Each line also goes to the listener's chat, in gray, as "Cora: line", so it can be read again; a second player
	 * standing just as near (with nothing to be told) gets none of it.
	 */
	//$ gametest_ticks_batch AREA '80' '"qaHS1031Chat"'
	@GameTest(template = AREA, timeoutTicks = 80, batch = "qaHS1031Chat")
	public void qaTheChatLinesReachOnlyTheListenerInGray(GameTestHelper helper) {
		HeartEventGameTests.village(helper, () -> {
			ServerLevel level = helper.getLevel();
			Villager cora = HeartEventGameTests.villager(helper, new BlockPos(5, 2, 4), "Cora");
			List<Component> heardA = new ArrayList<>();
			List<Component> heardB = new ArrayList<>();
			ServerPlayer a = QaStageThreeGameTests.listener(helper, heardA);
			ServerPlayer b = QaStageThreeGameTests.listener(helper, heardB);
			stand(helper, a, 5.5, 6.5);
			stand(helper, b, 6.5, 6.5);
			Friendship.add(cora, a, 200);
			helper.assertTrue(HeartEvents.pending(level, cora, b.getUUID()) == null, "setup: Cora has something for the second player");
			List<String> lines = HeartEventGameTests.listen(helper, cora, a, HeartEventGameTests.later(level), 4);
			List<Component> chat = distinct(heardA, SAID);
			helper.assertTrue(chat.size() == 4, "the listener's chat has " + chat.size() + " heart-event lines, not 4: " + chat.stream().map(Component::getString).toList());
			for (int i = 0; i < 4; i++) {
				Component c = chat.get(i);
				helper.assertTrue(c.getString().equals("Cora: " + lines.get(i)), "chat line " + (i + 1) + " reads \"" + c.getString() + "\", over her head: \"" + lines.get(i) + "\"");
				helper.assertTrue(TextColor.fromLegacyFormat(ChatFormatting.GRAY).equals(c.getStyle().getColor()), "chat line " + (i + 1) + " isn't gray: " + c.getStyle().getColor());
			}
			helper.assertTrue(distinct(heardB, SAID).isEmpty(), "the bystander's chat got the story: " + distinct(heardB, SAID).stream().map(Component::getString).toList());
			helper.assertTrue(HeartEventGameTests.told(cora, a).size() == 1 && HeartEventGameTests.told(cora, b).isEmpty(), "told: " + HeartEventGameTests.told(cora, a) + " / " + HeartEventGameTests.told(cora, b));
			helper.assertTrue(Friendship.points(cora, b.getUUID()) == 0, "the bystander got friendship: " + Friendship.points(cora, b.getUUID()));
			helper.succeed();
		});
	}

	/**
	 * The range from the listening side: a player who steps back to exactly 8 blocks is still told the next line; a step
	 * past 8 (8.5) and it stops, nothing kept (no +20, no chronicle line); coming back starts over from the first line,
	 * which reaches the chat a second time.
	 */
	//$ gametest_ticks_batch AREA '80' '"qaHS1031Edge"'
	@GameTest(template = AREA, timeoutTicks = 80, batch = "qaHS1031Edge")
	public void qaEightBlocksOffGoesOnAndAStepPastStartsItOver(GameTestHelper helper) {
		HeartEventGameTests.village(helper, () -> {
			ServerLevel level = helper.getLevel();
			BlockPos hall = helper.absolutePos(HeartEventGameTests.HALL);
			Villager dara = HeartEventGameTests.villager(helper, new BlockPos(5, 2, 4), "Dara");
			ModAttachments.PARENTS.set(dara, new Families.Parents(Component.literal("Mira"), Component.literal("Tomas"), "", "", true));
			List<Component> heard = new ArrayList<>();
			ServerPlayer me = QaStageThreeGameTests.listener(helper, heard);
			stand(helper, me, 5.5, 6.5);
			Friendship.add(dara, me, 200);
			long start = HeartEventGameTests.later(level);
			HeartEvents.tick(level, start);
			helper.assertTrue(HeartEvents.telling(dara) != null && HeartEvents.telling(dara).said == 1, "she didn't begin two blocks off");
			String first = overHead(dara);
			helper.assertTrue(first.startsWith("You've been good to me"), "the born-here story's first line: " + first);
			// Exactly 8 blocks: still within range, the next line comes.
			stand(helper, me, 5.5, 12.5);
			helper.assertTrue(Math.abs(dara.distanceTo(me) - 8.0) < 1e-6, "setup: not 8 blocks apart: " + dara.distanceTo(me));
			HeartEvents.tick(level, start + HeartEvents.LINE_EVERY);
			helper.assertTrue(HeartEvents.telling(dara) != null && HeartEvents.telling(dara).said == 2, "at exactly 8 blocks the telling stopped: " + HeartEvents.telling(dara));
			// 8.5 blocks: it stops, nothing kept.
			stand(helper, me, 5.5, 13.0);
			HeartEvents.tick(level, start + 2L * HeartEvents.LINE_EVERY);
			helper.assertTrue(HeartEvents.telling(dara) == null, "8.5 blocks off and she's still telling: said " + (HeartEvents.telling(dara) == null ? -1 : HeartEvents.telling(dara).said));
			helper.assertTrue(HeartEventGameTests.told(dara, me).isEmpty() && Friendship.points(dara, me.getUUID()) == 200,
				"half told and kept: told " + HeartEventGameTests.told(dara, me) + ", " + Friendship.points(dara, me.getUUID()) + " points");
			helper.assertTrue(HeartEventGameTests.friendLines(level, hall).isEmpty(), "a half-told story in the chronicle: " + HeartEventGameTests.friendLines(level, hall));
			// Back again: from the first line.
			stand(helper, me, 5.5, 6.5);
			HeartEvents.tick(level, HeartEventGameTests.later(level));
			helper.assertTrue(HeartEvents.telling(dara) != null && HeartEvents.telling(dara).said == 1, "coming back didn't start over: " + HeartEvents.telling(dara));
			helper.assertTrue(overHead(dara).equals(first), "coming back, the first line is: " + overHead(dara));
			long firstInChat = distinct(heard, SAID).stream().filter(c -> c.getString().equals("Dara: " + first)).count();
			helper.assertTrue(firstInChat == 2, "the first line reached the chat " + firstInChat + " times, not twice");
			helper.succeed();
		});
	}

	// ---- 31.18: the trigger ----------------------------------------------------------------------------------------

	/**
	 * The Sickness is a threat arc: in a Village of 12 with sickness on, it never starts while the owner has set the
	 * village At peace, nor at threat level 0; with both back to normal it starts through the same roll.
	 */
	//$ gametest_ticks_batch TALL '200' '"qaHS1031Peace"'
	@GameTest(template = TALL, timeoutTicks = 200, batch = "qaHS1031Peace")
	public void qaTheGreyCoughWaitsWhileTheVillageIsAtPeace(GameTestHelper helper) {
		Leftovers.clear(helper);
		QaSicknessGameTests.settings(helper);
		ServerLevel level = helper.getLevel();
		VillageHallBlockEntity entity = QaSicknessGameTests.hall(helper);
		BlockPos hall = helper.absolutePos(HALL);
		Set<String> disabled = Arcs.DISABLED;
		int cooldown = Arcs.COOLDOWN_DAYS;
		int atOnce = Arcs.AT_ONCE;
		boolean enabled = Arcs.ENABLED;
		int threatLevel = Threats.LEVEL;
		Set<String> others = new HashSet<>();
		for (Arcs.Arc a : Arcs.all()) {
			if (!a.id().equals(SicknessStory.ARC)) {
				others.add(a.id().toString());
			}
		}
		Arcs.DISABLED = others;
		Arcs.COOLDOWN_DAYS = 0;
		Arcs.AT_ONCE = 1000;
		Arcs.ENABLED = true;
		Threats.LEVEL = Math.max(1, threatLevel);
		Leftovers.after(helper, () -> {
			Arcs.DISABLED = disabled;
			Arcs.COOLDOWN_DAYS = cooldown;
			Arcs.AT_ONCE = atOnce;
			Arcs.ENABLED = enabled;
			Threats.LEVEL = threatLevel;
		});
		helper.assertTrue(QaSicknessGameTests.arc(helper).threat(), "the Sickness isn't marked a threat");
		List<Villager> people = QaSicknessGameTests.village(helper, 12);
		RandomSource random = RandomSource.create(31181);
		entity.setRank(VillageRanks.Rank.VILLAGE);
		entity.setAtPeace(true, Chronicle.day(level));
		for (int i = 0; i < 300; i++) {
			helper.assertTrue(Arcs.tryStart(level, hall, random, 1) == null, "it started in a village At peace");
		}
		entity.setAtPeace(false, Chronicle.day(level));
		Threats.LEVEL = 0;
		for (int i = 0; i < 300; i++) {
			helper.assertTrue(Arcs.tryStart(level, hall, random, 1) == null, "it started at threat level 0");
		}
		helper.assertTrue(QaSicknessGameTests.grey(people) == 0, "someone coughs before it began");
		Threats.LEVEL = Math.max(1, threatLevel);
		ArcState s = null;
		for (int i = 0; i < 300 && s == null; i++) {
			s = Arcs.tryStart(level, hall, random, 1);
		}
		helper.assertTrue(s != null && s.id.equals(SicknessStory.ARC.toString()), "with peace over it never started in 300 rolls");
		helper.assertTrue(QaSicknessGameTests.grey(people) == 3, "coughing at the start: " + QaSicknessGameTests.grey(people));
		helper.succeed();
	}

	// ---- 31.18: the remedy and the brew ----------------------------------------------------------------------------

	/**
	 * A nurse's remedy takes the slowness away for exactly a day: eased the tick before the day is up, not at it; while
	 * eased the hall's round leaves them quick (and still coughing); once the day is up the round makes them slow again.
	 */
	//$ gametest_ticks_batch TALL '100' '"qaHS1031Ease"'
	@GameTest(template = TALL, timeoutTicks = 100, batch = "qaHS1031Ease")
	public void qaARemedyEasesTheCoughForExactlyOneDay(GameTestHelper helper) {
		Leftovers.clear(helper);
		QaSicknessGameTests.settings(helper);
		ServerLevel level = helper.getLevel();
		QaSicknessGameTests.hall(helper);
		BlockPos hall = helper.absolutePos(HALL);
		List<Villager> people = QaSicknessGameTests.village(helper, 10);
		QaSicknessGameTests.nurse(helper, new BlockPos(20, 2, 20));
		QaSicknessGameTests.setDay(level, Chronicle.day(level) + 1, 1000);
		ArcState s = Arcs.start(level, hall, QaSicknessGameTests.arc(helper));
		helper.assertTrue(s != null, "the arc didn't start");
		Villager c = people.stream().filter(Sickness::isGrey).findFirst().orElse(null);
		helper.assertTrue(c != null && c.hasEffect(MobEffects.MOVEMENT_SLOWDOWN), "nobody coughing, or not slow");
		long now = level.getGameTime();
		Sickness.ease(level, c);
		helper.assertTrue(!c.hasEffect(MobEffects.MOVEMENT_SLOWDOWN) && Sickness.isGrey(c), "eased: slow " + c.hasEffect(MobEffects.MOVEMENT_SLOWDOWN) + ", grey " + Sickness.isGrey(c));
		helper.assertTrue(Sickness.eased(c, now + VillageNeeds.DAY - 1), "not eased the tick before a day is up");
		helper.assertFalse(Sickness.eased(c, now + VillageNeeds.DAY), "still eased a whole day on");
		Sickness.round(level, c, 1);
		helper.assertTrue(!c.hasEffect(MobEffects.MOVEMENT_SLOWDOWN) && Sickness.isGrey(c), "the round while eased: slow " + c.hasEffect(MobEffects.MOVEMENT_SLOWDOWN) + ", grey " + Sickness.isGrey(c));
		ModAttachments.ILL_EASED_UNTIL.set(c, level.getGameTime()); // the day is up
		Sickness.round(level, c, 1);
		helper.assertTrue(c.hasEffect(MobEffects.MOVEMENT_SLOWDOWN) && Sickness.isGrey(c), "a day on: slow " + c.hasEffect(MobEffects.MOVEMENT_SLOWDOWN) + ", grey " + Sickness.isGrey(c));
		helper.succeed();
	}

	/**
	 * The brew takes half a day to the tick: at 40% the Nurse's head reads "Brewing the cure" with the bar at 0.4 and the
	 * tonic named under it; a tick before half a day it isn't brewed, at half a day it is.
	 */
	//$ gametest_ticks_batch TALL '100' '"qaHS1031Brew"'
	@GameTest(template = TALL, timeoutTicks = 100, batch = "qaHS1031Brew")
	public void qaTheBrewTakesHalfADayToTheTick(GameTestHelper helper) {
		Leftovers.clear(helper);
		QaSicknessGameTests.settings(helper);
		Leftovers.players(helper);
		ServerLevel level = helper.getLevel();
		QaSicknessGameTests.hall(helper);
		BlockPos hall = helper.absolutePos(HALL);
		QaSicknessGameTests.village(helper, 10);
		Villager nurse = QaSicknessGameTests.nurse(helper, new BlockPos(20, 2, 20));
		ServerPlayer player = QaSicknessGameTests.player(helper);
		QaSicknessGameTests.setDay(level, Chronicle.day(level) + 1, 1000);
		ArcState s = Arcs.start(level, hall, QaSicknessGameTests.arc(helper));
		helper.assertTrue(s != null, "the arc didn't start");
		QaSicknessGameTests.gather(helper, level, s, player, nurse);
		long from = SicknessStory.stamp(s, "brew_from");
		helper.assertTrue(from >= 0, "no brew began");
		level.setDayTime(from + SicknessStory.BREW * 2 / 5);
		SicknessStory.tick(level);
		WorkerStatus.Entry status = WorkerStatus.get(nurse, level.getGameTime());
		helper.assertTrue(status != null && status.title().getString().equals("Brewing the cure"), "over her head at 40%: " + (status == null ? null : status.title().getString()));
		helper.assertTrue(Math.abs(status.progress() - 0.4f) < 0.01f, "the bar at 40%: " + status.progress());
		helper.assertTrue(status.line() != null && status.line().getString().equals("the Three-Herb Tonic"), "under it: " + (status.line() == null ? null : status.line().getString()));
		level.setDayTime(from + SicknessStory.BREW - 1);
		SicknessStory.tick(level);
		helper.assertFalse(s.flags.containsKey(SicknessStory.BREWED), "brewed a tick before half a day");
		level.setDayTime(from + SicknessStory.BREW);
		SicknessStory.tick(level);
		helper.assertTrue(s.flags.containsKey(SicknessStory.BREWED), "not brewed at half a day");
		helper.succeed();
	}

	// ---- 31.18: the night's pass-on --------------------------------------------------------------------------------

	/**
	 * Never past half the village, an even one: 12 villagers close together, one coughing; over 200 nights the cough
	 * reaches exactly 6 and never a 7th.
	 */
	//$ gametest_ticks_batch TALL '100' '"qaHS1031Cap"'
	@GameTest(template = TALL, timeoutTicks = 100, batch = "qaHS1031Cap")
	public void qaTheCoughReachesHalfAnEvenVillageAndNoMore(GameTestHelper helper) {
		Leftovers.clear(helper);
		QaSicknessGameTests.settings(helper);
		ServerLevel level = helper.getLevel();
		QaSicknessGameTests.hall(helper);
		BlockPos hall = helper.absolutePos(HALL);
		List<Villager> people = QaSicknessGameTests.village(helper, 12);
		helper.assertTrue(SicknessStory.cap(level, hall) == 6, "cap: " + SicknessStory.cap(level, hall));
		helper.assertTrue(Sickness.greyCough(level, people.get(5)), "the first didn't catch it");
		ArcState s = new ArcState(SicknessStory.ARC.toString());
		RandomSource random = RandomSource.create(1031);
		for (int night = 0; night < 200; night++) {
			SicknessStory.spread(level, hall, s, random);
			long ill = QaSicknessGameTests.grey(people);
			helper.assertTrue(ill <= 6, "night " + (night + 1) + ": " + ill + " of 12 coughing");
		}
		helper.assertTrue(QaSicknessGameTests.grey(people) == 6, "after 200 nights only " + QaSicknessGameTests.grey(people) + " of 12 coughing");
		helper.succeed();
	}

	// ---- 31.18: the endings ----------------------------------------------------------------------------------------

	/**
	 * Without the cure in 15 days it runs its course: over the next nights nobody new catches it, the healer takes no
	 * more herbs, everyone is well and alive within 3 days, and a player who handed in one herb before gets none of the
	 * ending's thanks (no +150, no honor, no +100 friendship).
	 */
	//$ gametest_ticks_batch TALL '200' '"qaHS1031Course"'
	@GameTest(template = TALL, timeoutTicks = 200, batch = "qaHS1031Course")
	public void qaWhileItRunsItsCourseNobodyCatchesItAndNobodyIsThanked(GameTestHelper helper) {
		Leftovers.clear(helper);
		QaSicknessGameTests.settings(helper);
		Leftovers.players(helper);
		ServerLevel level = helper.getLevel();
		QaSicknessGameTests.hall(helper);
		BlockPos hall = helper.absolutePos(HALL);
		List<Villager> people = QaSicknessGameTests.village(helper, 10);
		Villager nurse = QaSicknessGameTests.nurse(helper, new BlockPos(20, 2, 20));
		ServerPlayer player = QaSicknessGameTests.player(helper);
		long day = Chronicle.day(level) + 1;
		QaSicknessGameTests.setDay(level, day, 1000);
		ArcState s = Arcs.start(level, hall, QaSicknessGameTests.arc(helper));
		helper.assertTrue(s != null, "the arc didn't start");
		SicknessStory.onUse(player, level, InteractionHand.MAIN_HAND, nurse);
		List<HerbBlock> herbs = SicknessStory.herbs(s);
		helper.assertTrue(herbs.size() == 3, "herbs: " + herbs);
		player.getInventory().add(new ItemStack(herbs.get(0), 3));
		SicknessStory.onUse(player, level, InteractionHand.MAIN_HAND, nurse);
		helper.assertTrue(QaSicknessGameTests.count(player, herbs.get(0)) == 0, "the first herb wasn't taken");

		QaSicknessGameTests.setDay(level, day + 15, 1000);
		SicknessStory.tick(level);
		helper.assertTrue(SicknessStory.stamp(s, "course") == day + 15, "it isn't running its course on day 15");
		Set<UUID> coughing = new HashSet<>();
		people.stream().filter(Sickness::isGrey).forEach(v -> coughing.add(v.getUUID()));
		helper.assertTrue(!coughing.isEmpty(), "nobody coughing when it began to run its course");
		player.getInventory().add(new ItemStack(herbs.get(1), 3));
		player.getInventory().add(new ItemStack(herbs.get(2), 3));
		InteractionResult talk = SicknessStory.onUse(player, level, InteractionHand.MAIN_HAND, nurse);
		helper.assertTrue(talk == InteractionResult.PASS && QaSicknessGameTests.count(player, herbs.get(1)) == 3 && s.chapter == 1,
			"the healer took herbs while it ran its course: " + talk + ", chapter " + s.chapter);
		for (int d = 0; d <= 3 && Arcs.running(level, hall) == s; d++) {
			QaSicknessGameTests.setDay(level, day + 15 + d, 14000); // night
			SicknessStory.tick(level);
			for (Villager v : people) {
				helper.assertFalse(Sickness.isGrey(v) && !coughing.contains(v.getUUID()), v.getCustomName().getString() + " caught it while it ran its course (night " + d + ")");
			}
		}
		helper.assertTrue(Arcs.running(level, hall) == null, "the arc didn't end within 3 days");
		helper.assertTrue(people.stream().noneMatch(Sickness::isIll) && people.stream().allMatch(Villager::isAlive), "not all well and alive");
		helper.assertTrue(Reputation.points(level, hall, player.getUUID()) < 150, "standing after a failed cure: " + Reputation.points(level, hall, player.getUUID()));
		helper.assertFalse(Reputation.honours(level, hall, player.getUUID()).contains("healer"), "the Healer honor after a failed cure");
		for (Villager v : people) {
			helper.assertTrue(Friendship.points(v, player.getUUID()) < SicknessStory.FRIENDSHIP,
				v.getCustomName().getString() + " thanked the player for a cure that never came: " + Friendship.points(v, player.getUUID()));
		}
		helper.succeed();
	}

	/**
	 * The ending's friendship, exactly: +100 with the gatherer from each villager who was ill, and nothing from those who
	 * never coughed (the Nurse among them).
	 */
	//$ gametest_ticks_batch TALL '200' '"qaHS1031Thanks"'
	@GameTest(template = TALL, timeoutTicks = 200, batch = "qaHS1031Thanks")
	public void qaOnlyTheVillagersWhoWereIllThankTheGatherer(GameTestHelper helper) {
		Leftovers.clear(helper);
		QaSicknessGameTests.settings(helper);
		Leftovers.players(helper);
		ServerLevel level = helper.getLevel();
		QaSicknessGameTests.hall(helper);
		BlockPos hall = helper.absolutePos(HALL);
		List<Villager> people = QaSicknessGameTests.village(helper, 10);
		Villager nurse = QaSicknessGameTests.nurse(helper, new BlockPos(20, 2, 20));
		ServerPlayer player = QaSicknessGameTests.player(helper);
		QaSicknessGameTests.setDay(level, Chronicle.day(level) + 1, 1000);
		ArcState s = Arcs.start(level, hall, QaSicknessGameTests.arc(helper));
		helper.assertTrue(s != null, "the arc didn't start");
		QaSicknessGameTests.gather(helper, level, s, player, nurse);
		level.setDayTime(SicknessStory.stamp(s, "brew_from") + SicknessStory.BREW);
		QaSicknessGameTests.runRound(level, hall, s, 40);
		helper.assertTrue(Arcs.running(level, hall) == null, "the arc isn't over");
		List<UUID> ill = SicknessStory.caught(s);
		int never = 0;
		for (Villager v : people) {
			int points = Friendship.points(v, player.getUUID());
			if (ill.contains(v.getUUID())) {
				helper.assertTrue(points == SicknessStory.FRIENDSHIP, v.getCustomName().getString() + " was ill and gave " + points + ", not 100");
			} else {
				never++;
				helper.assertTrue(points == 0, v.getCustomName().getString() + " never coughed and gave " + points);
			}
		}
		helper.assertTrue(never > 0, "setup: everyone was ill");
		helper.assertTrue(Friendship.points(nurse, player.getUUID()) == 0, "the Nurse gave " + Friendship.points(nurse, player.getUUID()));
		helper.succeed();
	}
}
