package io.github.jcondedata.aliveworkplace.test;

import io.github.jcondedata.aliveworkplace.story.Friendship;
import io.github.jcondedata.aliveworkplace.story.HeartEvents;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.schedule.Activity;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;

/**
 * QA (lane-c-1116-0052) for heart events (ROADMAP 31.7, 31.8, B309), from docs/wiki/heart-events.md "How it works":
 * two friends at 2 hearts standing together, the listener dying or logging out halfway, the villager falling asleep
 * or panicking halfway, and a jump from 0 to 6 hearts told level by level. The engine is ticked by hand as in
 * {@link HeartEventGameTests} (it doesn't tick itself in gametests).
 */
public class QaHeartEvents1116cGameTests implements FabricGameTest {
	static final String AREA = HeartEventGameTests.AREA;
	private static final String SAID = QaHeartsSickness1031GameTests.SAID;

	/** A player on a real connection with a name of its own, whose chat is recorded in {@code seen}. */
	private static ServerPlayer named(GameTestHelper helper, String name, List<Component> seen, double x, double z) {
		ServerLevel level = helper.getLevel();
		var cookie = net.minecraft.server.network.CommonListenerCookie.createInitial(new com.mojang.authlib.GameProfile(UUID.randomUUID(), name), false);
		ServerPlayer player = new ServerPlayer(level.getServer(), level, cookie.gameProfile(), cookie.clientInformation()) {
			@Override
			public void displayClientMessage(Component message, boolean overlay) {
				seen.add(message);
				super.displayClientMessage(message, overlay);
			}

			@Override
			public void sendSystemMessage(Component message, boolean overlay) {
				seen.add(message);
				super.sendSystemMessage(message, overlay);
			}
		};
		net.minecraft.network.Connection connection = new net.minecraft.network.Connection(net.minecraft.network.protocol.PacketFlow.SERVERBOUND);
		new io.netty.channel.embedded.EmbeddedChannel(connection);
		level.getServer().getPlayerList().placeNewPlayer(connection, player, cookie);
		player.setGameMode(GameType.SURVIVAL);
		Vec3 pos = helper.absoluteVec(new Vec3(x, 2, z));
		player.moveTo(pos.x, pos.y, pos.z, 0, 0);
		Leftovers.after(helper, () -> {
			if (level.getServer().getPlayerList().getPlayer(player.getUUID()) != null) {
				level.getServer().getPlayerList().remove(player);
			}
		});
		return player;
	}

	private static String tellingOf(Villager villager) {
		HeartEvents.Telling t = HeartEvents.telling(villager);
		return t == null ? "none" : t.event.id() + " to " + t.player + ", " + t.said + " said";
	}

	// --- Two friends at once -------------------------------------------------------------------------------------

	/**
	 * Alex and Sam both reach 2 hearts with Cora at the same moment and stand by her: she tells one of them at a time,
	 * the one waiting reads none of it in chat, then the other hears it from the first line. Each is told once (+20
	 * each, a chronicle line naming each), and her life story has one line for the event with both names.
	 */
	//$ gametest_ticks_batch AREA '100' '"qaHe1116cTwo"'
	@GameTest(template = AREA, timeoutTicks = 100, batch = "qaHe1116cTwo")
	public void qaTwoFriendsAtTwoHeartsTogetherAreEachToldOnceInTurn(GameTestHelper helper) {
		HeartEventGameTests.village(helper, () -> {
			ServerLevel level = helper.getLevel();
			BlockPos hall = helper.absolutePos(HeartEventGameTests.HALL);
			Villager cora = HeartEventGameTests.villager(helper, new BlockPos(5, 2, 4), "Cora");
			List<Component> heardA = new ArrayList<>();
			List<Component> heardS = new ArrayList<>();
			ServerPlayer alex = named(helper, "QaAlex1116c", heardA, 4.5, 6.5);
			ServerPlayer sam = named(helper, "QaSam1116c", heardS, 6.5, 6.5);
			Friendship.add(cora, alex, 200);
			Friendship.add(cora, sam, 200);
			HeartEvents.Event event = HeartEvents.pending(level, cora, alex.getUUID());
			helper.assertTrue(event != null && event.hearts() == 2 && event.equals(HeartEvents.pending(level, cora, sam.getUUID())),
				"setup: not the same 2-heart story for both: " + event + " / " + HeartEvents.pending(level, cora, sam.getUUID()));
			int n = event.lines().size();
			long start = HeartEventGameTests.later(level);
			List<UUID> order = new ArrayList<>();
			for (int i = 0; i < 80; i++) {
				HeartEvents.tick(level, start + (long) i * HeartEvents.CHECK_EVERY);
				HeartEvents.Telling t = HeartEvents.telling(cora);
				if (t != null && (order.isEmpty() || !order.get(order.size() - 1).equals(t.player))) {
					order.add(t.player);
				}
				if (t != null && t.player.equals(alex.getUUID())) {
					helper.assertTrue(QaHeartsSickness1031GameTests.distinct(heardS, SAID).isEmpty() || order.get(0).equals(sam.getUUID()),
						"look " + i + ": Sam read Alex's story while waiting: " + QaHeartsSickness1031GameTests.distinct(heardS, SAID).size() + " lines");
				}
				if (i == 0) {
					helper.assertTrue(t != null && t.said == 1, "nobody was told anything at the first look: " + tellingOf(cora));
				}
			}
			helper.assertTrue(order.size() == 2 && !order.get(0).equals(order.get(1)), "the tellings went " + order + ", not one to each in turn");
			ServerPlayer first = order.get(0).equals(alex.getUUID()) ? alex : sam;
			List<Component> firstChat = first == alex ? heardA : heardS;
			List<Component> secondChat = first == alex ? heardS : heardA;
			List<Component> a = QaHeartsSickness1031GameTests.distinct(firstChat, SAID);
			List<Component> b = QaHeartsSickness1031GameTests.distinct(secondChat, SAID);
			helper.assertTrue(a.size() == n && b.size() == n, "chat lines: " + a.size() + " and " + b.size() + ", not " + n + " each");
			List<String> expectedSecond = HeartEvents.lines(level, cora, first == alex ? sam : alex, event).stream().map(c -> "Cora: " + c.getString()).toList();
			helper.assertTrue(b.stream().map(Component::getString).toList().equals(expectedSecond),
				"the second listener read " + b.stream().map(Component::getString).toList() + ", not the story from line one " + expectedSecond);
			helper.assertTrue(HeartEventGameTests.told(cora, alex).equals(List.of(event.id().toString()))
				&& HeartEventGameTests.told(cora, sam).equals(List.of(event.id().toString())),
				"told: " + HeartEventGameTests.told(cora, alex) + " / " + HeartEventGameTests.told(cora, sam));
			helper.assertTrue(Friendship.points(cora, alex.getUUID()) == 220 && Friendship.points(cora, sam.getUUID()) == 220,
				"points: " + Friendship.points(cora, alex.getUUID()) + " / " + Friendship.points(cora, sam.getUUID()));
			List<String> chronicle = HeartEventGameTests.friendLines(level, hall);
			helper.assertTrue(chronicle.size() == 2 && chronicle.stream().anyMatch(s -> s.contains("QaAlex1116c")) && chronicle.stream().anyMatch(s -> s.contains("QaSam1116c")),
				"the chronicle: " + chronicle);
			Map<HeartEvents.Event, List<String>> story = HeartEvents.toldBy(cora);
			helper.assertTrue(story.size() == 1 && List.of("QaAlex1116c", "QaSam1116c").equals(story.get(event)), "her life story: " + story);
			helper.succeed();
		});
	}

	// --- The listener goes -----------------------------------------------------------------------------------------

	/**
	 * The listener dies after the first line: the telling stops, nothing is kept (no told, no +20, no chronicle line),
	 * and while dead she starts nothing. Respawned and standing by her, it starts again from line one and counts once.
	 */
	//$ gametest_ticks_batch AREA '100' '"qaHe1116cDead"'
	@GameTest(template = AREA, timeoutTicks = 100, batch = "qaHe1116cDead")
	public void qaTheListenerDyingHalfwayStopsItAndItStartsOverAfter(GameTestHelper helper) {
		HeartEventGameTests.village(helper, () -> {
			ServerLevel level = helper.getLevel();
			BlockPos hall = helper.absolutePos(HeartEventGameTests.HALL);
			Villager cora = HeartEventGameTests.villager(helper, new BlockPos(5, 2, 4), "Cora");
			ServerPlayer me = HeartEventGameTests.player(helper, new BlockPos(5, 2, 6));
			Friendship.add(cora, me, 200);
			HeartEvents.Event event = HeartEvents.pending(level, cora, me.getUUID());
			helper.assertTrue(event != null, "setup: nothing to tell at 2 hearts");
			long t = HeartEventGameTests.later(level);
			HeartEvents.tick(level, t);
			helper.assertTrue(HeartEvents.telling(cora) != null && HeartEvents.telling(cora).said == 1, "she didn't start: " + tellingOf(cora));
			me.setHealth(0);
			helper.assertTrue(!me.isAlive(), "setup: the player is still alive");
			HeartEvents.tick(level, t + HeartEvents.LINE_EVERY);
			helper.assertTrue(HeartEvents.telling(cora) == null, "she went on telling a dead player: " + tellingOf(cora));
			HeartEvents.tick(level, HeartEventGameTests.later(level));
			helper.assertTrue(HeartEvents.telling(cora) == null, "she started telling a dead player: " + tellingOf(cora));
			helper.assertTrue(HeartEventGameTests.told(cora, me).isEmpty() && Friendship.points(cora, me.getUUID()) == 200
				&& HeartEventGameTests.friendLines(level, hall).isEmpty(), "half a story counted: " + HeartEventGameTests.told(cora, me) + ", "
				+ Friendship.points(cora, me.getUUID()) + ", " + HeartEventGameTests.friendLines(level, hall));
			me.setHealth(20);
			List<String> lines = HeartEventGameTests.listen(helper, cora, me, HeartEventGameTests.later(level), event.lines().size());
			List<String> expected = HeartEvents.lines(level, cora, me, event).stream().map(Component::getString).toList();
			helper.assertTrue(lines.equals(expected), "after respawning she said " + lines + ", not " + expected);
			helper.assertTrue(HeartEventGameTests.told(cora, me).size() == 1 && Friendship.points(cora, me.getUUID()) == 220
				&& HeartEventGameTests.friendLines(level, hall).size() == 1, "told " + HeartEventGameTests.told(cora, me) + ", "
				+ Friendship.points(cora, me.getUUID()) + ", " + HeartEventGameTests.friendLines(level, hall));
			helper.succeed();
		});
	}

	/**
	 * The listener logs out after two lines: the telling stops at once and nothing is kept for him; Sam, a friend at 2
	 * hearts who was waiting next to him, is told the story from line one at the next look, and it counts for Sam alone.
	 */
	//$ gametest_ticks_batch AREA '100' '"qaHe1116cLogout"'
	@GameTest(template = AREA, timeoutTicks = 100, batch = "qaHe1116cLogout")
	public void qaTheListenerLoggingOutHalfwayFreesHerForTheNextFriend(GameTestHelper helper) {
		HeartEventGameTests.village(helper, () -> {
			ServerLevel level = helper.getLevel();
			BlockPos hall = helper.absolutePos(HeartEventGameTests.HALL);
			Villager cora = HeartEventGameTests.villager(helper, new BlockPos(5, 2, 4), "Cora");
			List<Component> heardA = new ArrayList<>();
			List<Component> heardS = new ArrayList<>();
			ServerPlayer alex = named(helper, "QaAlexOut1116c", heardA, 5.5, 5.5);
			ServerPlayer sam = named(helper, "QaSamOut1116c", heardS, 5.5, 8.5);
			Friendship.add(cora, alex, 200);
			Friendship.add(cora, sam, 200);
			HeartEvents.Event event = HeartEvents.pending(level, cora, sam.getUUID());
			long t = HeartEventGameTests.later(level);
			HeartEvents.tick(level, t);
			HeartEvents.tick(level, t + HeartEvents.LINE_EVERY);
			HeartEvents.Telling telling = HeartEvents.telling(cora);
			helper.assertTrue(telling != null && telling.player.equals(alex.getUUID()) && telling.said == 2,
				"setup: she isn't two lines into Alex's story (the nearer): " + tellingOf(cora));
			level.getServer().getPlayerList().remove(alex);
			long next = t + HeartEvents.LINE_EVERY + HeartEvents.CHECK_EVERY;
			HeartEvents.tick(level, next);
			telling = HeartEvents.telling(cora);
			helper.assertTrue(telling == null || telling.player.equals(sam.getUUID()), "she went on telling a player who logged out: " + tellingOf(cora));
			helper.assertTrue(HeartEventGameTests.told(cora, alex).isEmpty() && Friendship.points(cora, alex.getUUID()) == 200,
				"half a story counted for Alex: " + HeartEventGameTests.told(cora, alex) + ", " + Friendship.points(cora, alex.getUUID()));
			helper.assertTrue(QaHeartsSickness1031GameTests.distinct(heardS, SAID).size() <= 1, "Sam read Alex's lines: "
				+ QaHeartsSickness1031GameTests.distinct(heardS, SAID).stream().map(Component::getString).toList());
			for (int i = 1; i < 60 && HeartEvents.telling(cora) != null || i == 1; i++) {
				HeartEvents.tick(level, next + (long) i * HeartEvents.CHECK_EVERY);
			}
			List<String> samRead = QaHeartsSickness1031GameTests.distinct(heardS, SAID).stream().map(Component::getString).toList();
			List<String> expected = HeartEvents.lines(level, cora, sam, event).stream().map(c -> "Cora: " + c.getString()).toList();
			helper.assertTrue(samRead.equals(expected), "Sam read " + samRead + ", not the story from line one " + expected);
			helper.assertTrue(HeartEventGameTests.told(cora, sam).size() == 1 && Friendship.points(cora, sam.getUUID()) == 220,
				"Sam: told " + HeartEventGameTests.told(cora, sam) + ", " + Friendship.points(cora, sam.getUUID()));
			List<String> chronicle = HeartEventGameTests.friendLines(level, hall);
			helper.assertTrue(chronicle.size() == 1 && chronicle.get(0).contains("QaSamOut1116c"), "the chronicle: " + chronicle);
			helper.succeed();
		});
	}

	// --- The villager stops ----------------------------------------------------------------------------------------

	/**
	 * She falls asleep after the first line: the telling stops, nothing is kept, and asleep she starts nothing. Awake,
	 * she starts again from line one. Then panic halfway (a raid's hide, a zombie's flee) stops it the same way.
	 */
	//$ gametest_ticks_batch AREA '100' '"qaHe1116cSleep"'
	@GameTest(template = AREA, timeoutTicks = 100, batch = "qaHe1116cSleep")
	public void qaFallingAsleepOrPanickingHalfwayStopsItAndItStartsOver(GameTestHelper helper) {
		HeartEventGameTests.village(helper, () -> {
			ServerLevel level = helper.getLevel();
			BlockPos hall = helper.absolutePos(HeartEventGameTests.HALL);
			BlockPos bed = new BlockPos(5, 2, 4);
			helper.setBlock(bed, Blocks.RED_BED);
			Villager cora = HeartEventGameTests.villager(helper, new BlockPos(5, 2, 5), "Cora");
			ServerPlayer me = HeartEventGameTests.player(helper, new BlockPos(5, 2, 7));
			Friendship.add(cora, me, 200);
			HeartEvents.Event event = HeartEvents.pending(level, cora, me.getUUID());
			long t = HeartEventGameTests.later(level);
			HeartEvents.tick(level, t);
			helper.assertTrue(HeartEvents.telling(cora) != null && HeartEvents.telling(cora).said == 1, "she didn't start: " + tellingOf(cora));
			cora.startSleeping(helper.absolutePos(bed));
			helper.assertTrue(cora.isSleeping(), "setup: she isn't asleep");
			HeartEvents.tick(level, t + HeartEvents.LINE_EVERY);
			helper.assertTrue(HeartEvents.telling(cora) == null, "she went on telling in her sleep: " + tellingOf(cora));
			HeartEvents.tick(level, HeartEventGameTests.later(level));
			helper.assertTrue(HeartEvents.telling(cora) == null, "she started a story asleep: " + tellingOf(cora));
			helper.assertTrue(HeartEventGameTests.told(cora, me).isEmpty() && Friendship.points(cora, me.getUUID()) == 200
				&& HeartEventGameTests.friendLines(level, hall).isEmpty(), "half a story counted while asleep");
			cora.stopSleeping();
			Vec3 spot = helper.absoluteVec(Vec3.atBottomCenterOf(new BlockPos(5, 2, 5)));
			cora.moveTo(spot.x, spot.y, spot.z, 0, 0);
			// Awake: from line one. Two lines in, she panics.
			long again = HeartEventGameTests.later(level);
			HeartEvents.tick(level, again);
			HeartEvents.tick(level, again + HeartEvents.LINE_EVERY);
			helper.assertTrue(HeartEvents.telling(cora) != null && HeartEvents.telling(cora).said == 2, "awake, she didn't start over: " + tellingOf(cora));
			cora.getBrain().setActiveActivityIfPossible(Activity.PANIC);
			helper.assertTrue(cora.getBrain().isActive(Activity.PANIC), "setup: she isn't panicking");
			HeartEvents.tick(level, again + 2L * HeartEvents.LINE_EVERY);
			helper.assertTrue(HeartEvents.telling(cora) == null, "she went on telling while panicking: " + tellingOf(cora));
			HeartEvents.tick(level, HeartEventGameTests.later(level));
			helper.assertTrue(HeartEvents.telling(cora) == null && HeartEventGameTests.told(cora, me).isEmpty(), "she started or finished a story while panicking");
			cora.getBrain().setActiveActivityIfPossible(Activity.IDLE);
			List<String> lines = HeartEventGameTests.listen(helper, cora, me, HeartEventGameTests.later(level), event.lines().size());
			List<String> expected = HeartEvents.lines(level, cora, me, event).stream().map(Component::getString).toList();
			helper.assertTrue(lines.equals(expected), "calm again she said " + lines + ", not " + expected);
			helper.assertTrue(HeartEventGameTests.told(cora, me).size() == 1 && Friendship.points(cora, me.getUUID()) == 220
				&& HeartEventGameTests.friendLines(level, hall).size() == 1, "told " + HeartEventGameTests.told(cora, me) + ", "
				+ Friendship.points(cora, me.getUUID()) + ", " + HeartEventGameTests.friendLines(level, hall));
			helper.succeed();
		});
	}

	// --- A big jump --------------------------------------------------------------------------------------------------

	/**
	 * 0 to 600 points in one go (6 hearts, a big gift or a quest reward): no level is skipped. She tells her 2-, 4- and
	 * 6-heart events in that order, each once and each a chronicle line, ending at 660 points (6 hearts: nothing at 8
	 * yet), and then has nothing more to tell; the next level she would tell at is 8.
	 */
	//$ gametest_ticks_batch AREA '100' '"qaHe1116cJump"'
	@GameTest(template = AREA, timeoutTicks = 100, batch = "qaHe1116cJump")
	public void qaAJumpToSixHeartsTellsTwoFourAndSixInOrder(GameTestHelper helper) {
		HeartEventGameTests.village(helper, () -> {
			ServerLevel level = helper.getLevel();
			BlockPos hall = helper.absolutePos(HeartEventGameTests.HALL);
			Villager cora = HeartEventGameTests.villager(helper, new BlockPos(5, 2, 4), "Cora");
			ServerPlayer me = HeartEventGameTests.player(helper, new BlockPos(5, 2, 6));
			Friendship.add(cora, me, 600);
			helper.assertTrue(Friendship.hearts(Friendship.points(cora, me.getUUID())) == 6, "setup: not 6 hearts: " + Friendship.points(cora, me.getUUID()));
			long start = HeartEventGameTests.later(level);
			List<Integer> levels = new ArrayList<>();
			HeartEvents.Telling last = null;
			for (int i = 0; i < 300; i++) {
				HeartEvents.tick(level, start + (long) i * HeartEvents.CHECK_EVERY);
				HeartEvents.Telling t = HeartEvents.telling(cora);
				if (t != null && t != last) {
					levels.add(t.event.hearts());
				}
				last = t;
			}
			helper.assertTrue(levels.equals(List.of(2, 4, 6)), "the tellings went by hearts " + levels + ", not 2, 4, 6");
			List<String> told = HeartEventGameTests.told(cora, me);
			helper.assertTrue(told.size() == 3 && told.stream().distinct().count() == 3, "told: " + told);
			helper.assertTrue(Friendship.points(cora, me.getUUID()) == 660, "points: " + Friendship.points(cora, me.getUUID()) + ", not 600 + 3 x 20");
			helper.assertTrue(HeartEventGameTests.friendLines(level, hall).size() == 3, "the chronicle: " + HeartEventGameTests.friendLines(level, hall));
			helper.assertTrue(HeartEvents.pending(level, cora, me.getUUID()) == null && HeartEvents.telling(cora) == null,
				"something more to tell at 6 hearts: " + HeartEvents.pending(level, cora, me.getUUID()));
			helper.assertTrue(HeartEvents.nextHearts(level, cora, me.getUUID()) == 8, "next level: " + HeartEvents.nextHearts(level, cora, me.getUUID()));
			helper.assertTrue(me.getInventory().items.stream().allMatch(s -> s.isEmpty()), "a keepsake before 10 hearts: " + me.getInventory().items);
			helper.succeed();
		});
	}
}
