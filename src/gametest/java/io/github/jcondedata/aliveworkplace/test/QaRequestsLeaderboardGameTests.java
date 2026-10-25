package io.github.jcondedata.aliveworkplace.test;

import io.github.jcondedata.aliveworkplace.build.BuilderLevels;
import io.github.jcondedata.aliveworkplace.hall.Chronicle;
import io.github.jcondedata.aliveworkplace.hall.QuestJournal;
import io.github.jcondedata.aliveworkplace.hall.VillageHallBlockEntity;
import io.github.jcondedata.aliveworkplace.hall.VillageQuests;
import io.github.jcondedata.aliveworkplace.hall.VillageRanks;
import io.github.jcondedata.aliveworkplace.realm.Leaderboard;
import io.github.jcondedata.aliveworkplace.realm.RealmData;
import io.github.jcondedata.aliveworkplace.realm.Realms;
import io.github.jcondedata.aliveworkplace.story.Friendship;
import io.github.jcondedata.aliveworkplace.story.PersonalRequests;
import io.github.jcondedata.aliveworkplace.story.Quest;
import io.github.jcondedata.aliveworkplace.story.Stories;
import io.github.jcondedata.aliveworkplace.test.RealmGameTests.Land;
import io.github.jcondedata.aliveworkplace.work.ChoiceMenu;
import java.time.DayOfWeek;
import java.time.Instant;
import java.time.Clock;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.animal.Cat;
import net.minecraft.world.entity.animal.Wolf;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.npc.VillagerType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * QA (lane-c-1025-0053), from the spec of ROADMAP 31.9, 31.10 and 33.19, for what {@code PersonalRequestGameTests},
 * {@code PersonalErrandGameTests} and {@code LeaderboardGameTests} leave out: joining a request at exactly 3 hearts and
 * just under, with the words each player reads; a helper who is offline when it's done still gets the friendship; a full
 * inventory doesn't lose the emeralds; the giver dying mid-request; a half-done request through save and reload; the
 * cat's 10 blocks from both sides, and a tamed wolf; and on the leaderboard: only the top three read, an empty week, a
 * realm tie and a left-off capital, several weeks missed at once, two players online, and a winner of two boards whose
 * ribbons last exactly a week from 19:00 sharp.
 */
public class QaRequestsLeaderboardGameTests implements FabricGameTest {
	private static final String AREA = PersonalRequestGameTests.AREA;
	private static final String LAND = "aliveworkplace_test:huge_area";
	private static final BlockPos HALL = PersonalRequestGameTests.HALL;

	// --- Helpers --------------------------------------------------------------------------------------------------

	/** A player who keeps what they're told, standing at {@code at} in the test. */
	private static ServerPlayer listener(GameTestHelper helper, List<Component> heard, String name, BlockPos at) {
		ServerPlayer player = RealmGameTests.listener(helper, heard, name);
		Vec3 pos = helper.absoluteVec(Vec3.atBottomCenterOf(at));
		player.moveTo(pos.x, pos.y, pos.z, 0, 0);
		return player;
	}

	private static List<String> words(List<Component> heard) {
		return heard.stream().map(Component::getString).toList();
	}

	private static VillageHallBlockEntity hallEntity(GameTestHelper helper) {
		return (VillageHallBlockEntity) helper.getLevel().getBlockEntity(helper.absolutePos(HALL));
	}

	/** Dara reaches her next level and the hall's round looks: her level_up request is done. */
	private static void levelUp(GameTestHelper helper, Villager dara) {
		ServerLevel level = helper.getLevel();
		int before = dara.getVillagerData().getLevel();
		BuilderLevels.addXp(level, dara, io.github.jcondedata.aliveworkplace.story.Gifts.BOTTLE_XP, null);
		helper.assertTrue(dara.getVillagerData().getLevel() == before + 1, "no level gained: " + dara.getVillagerData().getLevel());
		VillageQuests.tick(level, helper.absolutePos(HALL), hallEntity(helper));
	}

	// --- 31.9: who may help -----------------------------------------------------------------------------------------

	/**
	 * The one asked takes it, nobody else can take the offer; a friend at exactly 3 hearts (300) joins, one at 299 can't;
	 * joining twice adds nobody. Every answer is read in the player's chat in words.
	 */
	//$ gametest_ticks_batch AREA '80' '"qaRequestJoinAt3"'
	@GameTest(template = AREA, timeoutTicks = 80, batch = "qaRequestJoinAt3")
	public void joiningAtExactlyThreeHeartsAndJustUnder(GameTestHelper helper) {
		PersonalRequestGameTests.village(helper, () -> {
			ServerLevel level = helper.getLevel();
			Villager dara = PersonalRequestGameTests.worker(helper, new BlockPos(5, 2, 4), "Dara", "minecraft:farmer", 1);
			List<Component> mine = new ArrayList<>();
			List<Component> others = new ArrayList<>();
			List<Component> lows = new ArrayList<>();
			ServerPlayer me = listener(helper, mine, "qa-me", new BlockPos(5, 2, 6));
			ServerPlayer other = listener(helper, others, "qa-other", new BlockPos(6, 2, 6));
			ServerPlayer low = listener(helper, lows, "qa-low", new BlockPos(7, 2, 6));
			Friendship.add(dara, me, 300);
			Friendship.add(dara, other, 300);
			Friendship.add(dara, low, 299);
			Quest quest = PersonalRequestGameTests.resolve(helper, "level_up", dara, me);
			helper.assertTrue(quest != null, "Dara has nothing to ask " + (quest == null ? PersonalRequestGameTests.why(helper, dara, me) : ""));
			PersonalRequests.propose(level, helper.absolutePos(HALL), dara, me, quest, Chronicle.day(level));

			// The offer is mine: someone else's [I'll help] is refused, in words.
			PersonalRequestGameTests.command(other, "workplace quest accept " + quest.id);
			helper.assertTrue(PersonalRequests.of(dara) == null && PersonalRequests.offer(dara) != null, "someone else took the offer");
			helper.assertTrue(words(others).contains("That request was asked of someone else."), "the other player read: " + words(others));
			PersonalRequestGameTests.command(me, "workplace quest accept " + quest.id);
			helper.assertTrue(PersonalRequests.of(dara) == quest, "I couldn't take my own offer");
			helper.assertTrue(words(mine).stream().anyMatch(s -> s.startsWith("You're helping with \"Next level before the festival\". It's in the Personal tab of the quest journal at the hall of ")
				&& !s.contains("%s")), "I read: " + words(mine));

			// Exactly 3 hearts joins; 299 points doesn't.
			others.clear();
			PersonalRequestGameTests.command(other, "workplace quest accept " + quest.id);
			helper.assertTrue(quest.helpers.containsKey(other.getUUID()), "a friend at exactly 3 hearts couldn't join");
			helper.assertTrue(words(others).contains("You're helping Dara too: Next level before the festival."), "the friend read: " + words(others));
			PersonalRequestGameTests.command(low, "workplace quest accept " + quest.id);
			helper.assertTrue(!quest.helpers.containsKey(low.getUUID()), "joined at 299 points");
			helper.assertTrue(words(lows).contains("You need 3 hearts with Dara to help with this."), "the 299-point player read: " + words(lows));
			// Joining again adds nobody and says so.
			others.clear();
			PersonalRequestGameTests.command(other, "workplace quest accept " + quest.id);
			helper.assertTrue(quest.helpers.keySet().equals(Set.of(me.getUUID(), other.getUUID())), "helpers: " + quest.helpers.keySet());
			helper.assertTrue(words(others).contains("You're already helping Dara."), "joining twice read: " + words(others));
			// One point more and the 299 player may join after all.
			Friendship.add(dara, low, 1);
			PersonalRequestGameTests.command(low, "workplace quest accept " + quest.id);
			helper.assertTrue(quest.helpers.containsKey(low.getUUID()), "couldn't join at 300 after one more point");

			// It's done: each of the three reads the thanks and what they earned.
			levelUp(helper, dara);
			helper.assertTrue(PersonalRequests.of(dara) == null, "not done at the next level");
			for (List<Component> heard : List.of(mine, others, lows)) {
				helper.assertTrue(words(heard).stream().anyMatch(s -> s.startsWith("You helped Dara: Next level before the festival. You earned ") && s.contains("6")
					&& !s.contains("%")), "a helper read: " + words(heard));
			}
			helper.succeed();
		});
	}

	/** "Everyone who helped gets the friendship": a helper who has left the game when it's done gets their +150 too. */
	//$ gametest_ticks_batch AREA '80' '"qaRequestOffline"'
	@GameTest(template = AREA, timeoutTicks = 80, batch = "qaRequestOffline")
	public void aHelperWhoLeftStillGetsTheFriendship(GameTestHelper helper) {
		PersonalRequestGameTests.village(helper, () -> {
			ServerLevel level = helper.getLevel();
			Villager dara = PersonalRequestGameTests.worker(helper, new BlockPos(5, 2, 4), "Dara", "minecraft:farmer", 1);
			ServerPlayer me = PersonalRequestGameTests.friend(helper, new BlockPos(5, 2, 6), dara, 300);
			ServerPlayer away = PersonalRequestGameTests.friend(helper, new BlockPos(6, 2, 6), dara, 420);
			UUID awayId = away.getUUID();
			Quest quest = PersonalRequestGameTests.accepted(helper, "level_up", dara, me);
			helper.assertTrue(PersonalRequests.join(away, level, quest), "a 4-heart friend couldn't join");
			level.getServer().getPlayerList().remove(away);
			helper.assertTrue(level.getServer().getPlayerList().getPlayer(awayId) == null, "still online");
			levelUp(helper, dara);
			helper.assertTrue(PersonalRequests.of(dara) == null, "not done at the next level");
			helper.assertTrue(Friendship.points(dara, me.getUUID()) == 450, "the helper here: " + Friendship.points(dara, me.getUUID()));
			helper.assertTrue(Friendship.points(dara, awayId) == 570, "the helper who left: " + Friendship.points(dara, awayId) + " (wanted 420 + 150)");
			helper.assertTrue(me.getInventory().countItem(Items.EMERALD) == 6, "emeralds: " + me.getInventory().countItem(Items.EMERALD));
			helper.succeed();
		});
	}

	/** A helper whose inventory is full when the request is done doesn't lose the 6 emeralds: they're in the bag or at their feet. */
	//$ gametest_ticks_batch AREA '80' '"qaRequestFull"'
	@GameTest(template = AREA, timeoutTicks = 80, batch = "qaRequestFull")
	public void aFullInventoryKeepsTheReward(GameTestHelper helper) {
		PersonalRequestGameTests.village(helper, () -> {
			ServerLevel level = helper.getLevel();
			Villager dara = PersonalRequestGameTests.worker(helper, new BlockPos(5, 2, 4), "Dara", "minecraft:farmer", 1);
			ServerPlayer me = PersonalRequestGameTests.friend(helper, new BlockPos(5, 2, 6), dara, 300);
			PersonalRequestGameTests.accepted(helper, "level_up", dara, me);
			for (int i = 0; i < me.getInventory().items.size(); i++) {
				me.getInventory().items.set(i, new ItemStack(Items.COBBLESTONE, 64));
			}
			me.getInventory().offhand.set(0, new ItemStack(Items.COBBLESTONE, 64));
			levelUp(helper, dara);
			helper.assertTrue(PersonalRequests.of(dara) == null, "not done at the next level");
			int held = me.getInventory().countItem(Items.EMERALD);
			int dropped = 0;
			for (ItemEntity item : level.getEntitiesOfClass(ItemEntity.class, new AABB(me.blockPosition()).inflate(4), e -> e.getItem().is(Items.EMERALD))) {
				dropped += item.getItem().getCount();
			}
			helper.assertTrue(held + dropped == 6, "with a full inventory: " + held + " held, " + dropped + " on the ground (wanted 6)");
			helper.assertTrue(Friendship.points(dara, me.getUUID()) == 450, "friendship: " + Friendship.points(dara, me.getUUID()));
			helper.succeed();
		});
	}

	/** The giver dies with a request open: nobody is paid for it, it comes down by its deadline, and an offer from the dead can't be taken. */
	//$ gametest_ticks_batch AREA '80' '"qaRequestGiverDead"'
	@GameTest(template = AREA, timeoutTicks = 80, batch = "qaRequestGiverDead")
	public void theGiverDiesMidRequest(GameTestHelper helper) {
		PersonalRequestGameTests.village(helper, () -> {
			ServerLevel level = helper.getLevel();
			BlockPos hall = helper.absolutePos(HALL);
			Villager dara = PersonalRequestGameTests.worker(helper, new BlockPos(5, 2, 4), "Dara", "minecraft:farmer", 1);
			Villager odo = PersonalRequestGameTests.worker(helper, new BlockPos(7, 2, 4), "Odo", "minecraft:farmer", 1);
			List<Component> mine = new ArrayList<>();
			ServerPlayer me = listener(helper, mine, "qa-mourner", new BlockPos(5, 2, 6));
			Friendship.add(dara, me, 300);
			Friendship.add(odo, me, 300);
			Quest quest = PersonalRequestGameTests.accepted(helper, "level_up", dara, me);
			Quest odos = PersonalRequestGameTests.resolve(helper, "level_up", odo, me);
			PersonalRequests.propose(level, hall, odo, me, odos, Chronicle.day(level));

			dara.kill();
			odo.kill();
			helper.assertTrue(!dara.isAlive() && !odo.isAlive(), "they didn't die");
			VillageQuests.tick(level, hall, hallEntity(helper));
			helper.assertTrue(me.getInventory().countItem(Items.EMERALD) == 0, "paid for a dead villager's request");
			// An offer from someone who died can't be taken.
			mine.clear();
			PersonalRequestGameTests.command(me, "workplace quest accept " + odos.id);
			helper.assertTrue(PersonalRequests.open(level, hall).stream().noneMatch(q -> q.id.equals(odos.id)), "accepted a dead villager's offer");
			helper.assertTrue(words(mine).contains("That request is no longer open."), "I read: " + words(mine));
			// By the deadline at the latest it has come down, without paying.
			PersonalRequests.overdue(level, hall, quest.due);
			helper.assertTrue(PersonalRequests.open(level, hall).stream().noneMatch(q -> q.id.equals(quest.id)), "the dead villager's request is still open after its deadline");
			helper.assertTrue(me.getInventory().countItem(Items.EMERALD) == 0, "paid after all");
			helper.succeed();
		});
	}

	/** A request half handed in (10 of 16 berries) keeps its progress, its helpers and its deadline through a save and reload. */
	//$ gametest_ticks_batch AREA '80' '"qaRequestHalfSaved"'
	@GameTest(template = AREA, timeoutTicks = 80, batch = "qaRequestHalfSaved")
	public void aHalfDoneRequestSurvivesSaveAndReload(GameTestHelper helper) {
		PersonalRequestGameTests.village(helper, () -> {
			ServerLevel level = helper.getLevel();
			BlockPos hall = helper.absolutePos(HALL);
			Villager odo = HeartEventGameTests.villager(helper, new BlockPos(5, 2, 4), "Odo");
			odo.setVillagerData(odo.getVillagerData().setType(VillagerType.TAIGA));
			ServerPlayer me = PersonalRequestGameTests.friend(helper, new BlockPos(5, 2, 6), odo, 300);
			ServerPlayer other = PersonalRequestGameTests.friend(helper, new BlockPos(6, 2, 6), odo, 300);
			Quest quest = PersonalRequestGameTests.accepted(helper, "taste_of_home", odo, me);
			helper.assertTrue(PersonalRequests.join(other, level, quest), "the second friend couldn't join");
			me.getInventory().add(new ItemStack(Items.SWEET_BERRIES, 10));
			ChoiceMenu menu = ChoiceMenu.detached(me, m -> QuestJournal.render(m, level, hall, me, QuestJournal.Tab.PERSONAL));
			menu.press(QuestJournal.PERSONAL_ROW, me);
			helper.assertTrue(quest.progress[0] == 10, "handed in: " + quest.progress[0]);

			CompoundTag saved = Stories.Data.get(level).save(new CompoundTag(), level.registryAccess());
			CompoundTag again = Stories.Data.load(saved, level.registryAccess()).save(new CompoundTag(), level.registryAccess());
			helper.assertTrue(saved.equals(again), "changed on reload");
			CompoundTag found = null;
			for (Tag h : again.getList("halls", Tag.TAG_COMPOUND)) {
				for (Tag q : ((CompoundTag) h).getList("quests", Tag.TAG_COMPOUND)) {
					if (io.github.jcondedata.aliveworkplace.mc.Nbt.getUuid((CompoundTag) q, "id").equals(quest.id)) {
						found = (CompoundTag) q;
					}
				}
			}
			helper.assertTrue(found != null, "the request isn't in the save");
			CompoundTag objective = found.getList("objectives", Tag.TAG_COMPOUND).getCompound(0);
			helper.assertTrue(objective.getInt("progress") == 10, "progress after reload: " + objective);
			helper.assertTrue(found.getCompound("helpers").contains(me.getUUID().toString()) && found.getCompound("helpers").contains(other.getUUID().toString()),
				"helpers after reload: " + found.getCompound("helpers"));
			helper.assertTrue(found.getLong("due") == quest.due, "the deadline after reload: " + found.getLong("due") + " vs " + quest.due);
			// The rest still finishes it, and pays both.
			me.getInventory().add(new ItemStack(Items.SWEET_BERRIES, 6));
			menu.press(QuestJournal.PERSONAL_ROW, me);
			helper.assertTrue(PersonalRequests.of(odo) == null && me.getInventory().countItem(Items.EMERALD) == 4 && other.getInventory().countItem(Items.EMERALD) == 4,
				"after the rest: " + me.getInventory().countItem(Items.EMERALD) + " and " + other.getInventory().countItem(Items.EMERALD));
			helper.succeed();
		});
	}

	/** A missed deadline costs every helper 30, the one who has left the game too; the other one reads why. */
	//$ gametest_ticks_batch AREA '80' '"qaRequestMissedAway"'
	@GameTest(template = AREA, timeoutTicks = 80, batch = "qaRequestMissedAway")
	public void aMissedDeadlineCostsAHelperWhoLeftToo(GameTestHelper helper) {
		PersonalRequestGameTests.village(helper, () -> {
			ServerLevel level = helper.getLevel();
			BlockPos hall = helper.absolutePos(HALL);
			Villager dara = PersonalRequestGameTests.worker(helper, new BlockPos(5, 2, 4), "Dara", "minecraft:farmer", 1);
			List<Component> mine = new ArrayList<>();
			ServerPlayer me = listener(helper, mine, "qa-late", new BlockPos(5, 2, 6));
			Friendship.add(dara, me, 300);
			ServerPlayer away = PersonalRequestGameTests.friend(helper, new BlockPos(6, 2, 6), dara, 500);
			UUID awayId = away.getUUID();
			Quest quest = PersonalRequestGameTests.accepted(helper, "taste_of_home", dara, me);
			helper.assertTrue(PersonalRequests.join(away, level, quest), "the second friend couldn't join");
			level.getServer().getPlayerList().remove(away);
			helper.assertTrue(PersonalRequests.overdue(level, hall, quest.due) == 1, "not missed at its deadline");
			helper.assertTrue(Friendship.points(dara, me.getUUID()) == 270 && Friendship.points(dara, awayId) == 470,
				"after the miss: " + Friendship.points(dara, me.getUUID()) + " and " + Friendship.points(dara, awayId) + " (wanted 270 and 470)");
			helper.assertTrue(words(mine).contains("You didn't help Dara in time (A taste of home). They feel let down: 30 friendship lost."), "I read: " + words(mine));
			helper.succeed();
		});
	}

	// --- 31.10: the cat by the door, at the edge of its 10 blocks ---------------------------------------------------

	/** A cat 11 blocks from the bed at dawn doesn't count; a tamed wolf 9 blocks away does. */
	//$ gametest_ticks_batch AREA '80' '"qaRequestCatEdge"'
	@GameTest(template = AREA, timeoutTicks = 80, batch = "qaRequestCatEdge")
	public void aPetJustOutsideTenBlocksDoesntCountAndOneInsideDoes(GameTestHelper helper) {
		PersonalRequestGameTests.village(helper, () -> {
			ServerLevel level = helper.getLevel();
			BlockPos hall = helper.absolutePos(HALL);
			Villager dara = PersonalRequestGameTests.worker(helper, new BlockPos(3, 2, 3), "Dara", "minecraft:farmer", 2);
			ServerPlayer me = PersonalRequestGameTests.friend(helper, new BlockPos(5, 2, 6), dara, 300);
			BlockPos bed = new BlockPos(2, 2, 2);
			dara.getBrain().setMemory(MemoryModuleType.HOME, GlobalPos.of(level.dimension(), helper.absolutePos(bed)));
			Quest quest = PersonalRequestGameTests.accepted(helper, "cat_by_the_door", dara, me);
			Cat cat = helper.spawn(EntityType.CAT, bed.offset(11, 0, 0));
			cat.setNoAi(true);
			PersonalRequests.look(level, hall, hallEntity(helper), true);
			helper.assertTrue(PersonalRequests.of(dara) == quest, "a cat 11 blocks from the bed counted");
			Wolf wolf = helper.spawn(EntityType.WOLF, bed.offset(9, 0, 0));
			wolf.setNoAi(true);
			wolf.tame(me);
			helper.assertTrue(wolf.isTame(), "the wolf isn't tame");
			PersonalRequests.look(level, hall, hallEntity(helper), true);
			helper.assertTrue(PersonalRequests.of(dara) == null && me.getInventory().countItem(Items.EMERALD) > 0, "a tamed wolf 9 blocks from the bed at dawn didn't count");
			helper.succeed();
		});
	}

	// --- 33.19: the weekly leaderboard ------------------------------------------------------------------------------

	private static long count(Land land, String line) {
		return land.heard().stream().filter(c -> c.getString().equals(line)).count();
	}

	private static boolean chronicled(Land land, int hall, String line) {
		return land.entity(hall).chronicle().stream().anyMatch(e -> e.kind() == Chronicle.Kind.CUP && e.text().getString().equals(line));
	}

	/** Four villages: only the top three are read; the fourth is still in the standings. */
	//$ gametest_ticks_batch LAND '100' '"qaLeaderboardFour"'
	@GameTest(template = LAND, timeoutTicks = 100, batch = "qaLeaderboardFour")
	public void onlyTheTopThreeAreReadOut(GameTestHelper helper) {
		Land land = RealmGameTests.land(helper, VillageRanks.Rank.VILLAGE, VillageRanks.Rank.VILLAGE, VillageRanks.Rank.VILLAGE, VillageRanks.Rank.VILLAGE);
		MinecraftServer server = land.level().getServer();
		boolean enabled = Leaderboard.ENABLED;
		Clock clock = Leaderboard.CLOCK;
		DayOfWeek day = Leaderboard.DAY;
		int hour = Leaderboard.HOUR;
		Leaderboard.ENABLED = true;
		Leaderboard.DAY = DayOfWeek.SUNDAY;
		Leaderboard.HOUR = 19;
		Leaderboard.CLOCK = LeaderboardGameTests.at("2026-10-04T12:00:00Z");
		land.data().clearLeaderboard();
		Leftovers.after(helper, () -> {
			Leaderboard.ENABLED = enabled;
			Leaderboard.CLOCK = clock;
			Leaderboard.DAY = day;
			Leaderboard.HOUR = hour;
			RealmData.get(server).clearLeaderboard();
		});
		String[] names = {"Ashford", "Brook", "Corth", "Dunmore"};
		String[] owners = {"Alex", "Bea", "Cal", "Dee"};
		int[] villagers = {12, 20, 8, 30};
		for (int i = 0; i < 4; i++) {
			land.entity(i).setCustomName(Component.literal(names[i]));
			land.entity(i).setOwner(land.owner().getUUID(), owners[i]);
			Realms.round(land.level(), land.hall(i), land.entity(i), villagers[i]);
		}
		Leaderboard.settle(server, false);
		helper.assertTrue(Leaderboard.standings(server, Leaderboard.Board.BIGGEST).size() == 4, "standings: " + Leaderboard.standings(server, Leaderboard.Board.BIGGEST));
		Leaderboard.CLOCK = LeaderboardGameTests.at("2026-10-04T19:00:10Z");
		Leaderboard.settle(server, false);
		String biggest = "Biggest: 1. Dunmore (Dee) 30 villagers, 2. Brook (Bea) 20 villagers, 3. Ashford (Alex) 12 villagers";
		helper.assertTrue(count(land, biggest) == 1, "the Biggest line: " + words(land.heard()));
		helper.assertTrue(land.heard().stream().noneMatch(c -> c.getString().contains("Corth") || c.getString().contains("4.")), "a fourth place was read: " + words(land.heard()));
		helper.succeed();
	}

	/** A week with nothing earned and nothing counted: each empty board says so in words, and nobody wins its ribbon; one villager reads "1 villager". */
	//$ gametest_ticks_batch LAND '100' '"qaLeaderboardEmpty"'
	@GameTest(template = LAND, timeoutTicks = 100, batch = "qaLeaderboardEmpty")
	public void anEmptyWeekReadsNobody(GameTestHelper helper) {
		Land land = LeaderboardGameTests.three(helper);
		MinecraftServer server = land.level().getServer();
		Realms.round(land.level(), land.hall(2), land.entity(2), 1);
		land.heard().clear();
		server.getCommands().performPrefixedCommand(land.owner().createCommandSourceStack(), "workplace leaderboard");
		String legends = Leaderboard.Board.LEGENDS.id().equals("legends") ? "Most Legends" : "Most Masters";
		List<String> soFar = words(land.heard());
		helper.assertTrue(soFar.size() == 6 && soFar.get(0).equals("The leaderboard so far this week (read out on Sunday at 19:00):"), "the command: " + soFar);
		land.heard().clear();
		Leaderboard.CLOCK = LeaderboardGameTests.at("2026-10-04T19:00:10Z");
		Leaderboard.settle(server, false);
		List<String> heard = words(land.heard());
		helper.assertTrue(heard.contains("Biggest: 1. Brook (Bea) 20 villagers, 2. Ashford (Alex) 12 villagers, 3. Corth (Cal) 1 villager"), "the Biggest line: " + heard);
		helper.assertTrue(heard.contains("Richest: no village yet"), "the Richest line: " + heard);
		helper.assertTrue(heard.contains(legends + ": no village yet"), "the " + legends + " line: " + heard);
		helper.assertTrue(heard.contains("Greatest realm: no realm yet"), "the realm line: " + heard);
		helper.assertTrue(heard.stream().noneMatch(s -> s.contains("%") || s.contains("leaderboard.aliveworkplace")), "untranslated: " + heard);
		helper.assertTrue(land.data().ribbons().stream().noneMatch(r -> r.board().equals("richest") || r.board().equals("realm") || r.board().equals(Leaderboard.Board.LEGENDS.id())),
			"a ribbon for an empty board: " + land.data().ribbons());
		for (int i = 0; i < 3; i++) {
			int hall = i;
			helper.assertTrue(land.entity(hall).chronicle().stream().noneMatch(e -> e.text().getString().contains("Richest village")), "won an empty Richest");
		}
		helper.succeed();
	}

	/** Two one-village realms with the same members: the bigger one is first; a realm whose capital its owner left off isn't listed. */
	//$ gametest_ticks_batch LAND '100' '"qaLeaderboardRealmTie"'
	@GameTest(template = LAND, timeoutTicks = 100, batch = "qaLeaderboardRealmTie")
	public void aRealmTieGoesToTheBiggerRealm(GameTestHelper helper) {
		Land land = LeaderboardGameTests.three(helper);
		MinecraftServer server = land.level().getServer();
		RealmData data = land.data();
		// Ashford (12 villagers) founded first, then Brook (20).
		data.putRealm(new RealmData.Realm(UUID.randomUUID(), Optional.empty(), land.at(0), List.of(new RealmData.Member(land.at(0), 0)), Optional.empty(), "barony", 0));
		data.putRealm(new RealmData.Realm(UUID.randomUUID(), Optional.empty(), land.at(1), List.of(new RealmData.Member(land.at(1), 0)), Optional.empty(), "barony", 0));
		List<Leaderboard.Standing> realms = Leaderboard.standings(server, Leaderboard.Board.REALM);
		helper.assertTrue(realms.size() == 2 && realms.get(0).hall().equals(land.at(1)) && realms.get(1).hall().equals(land.at(0)), "the realms: " + realms);
		String line = Leaderboard.line(Leaderboard.Board.REALM, realms).getString();
		helper.assertTrue(line.startsWith("Greatest realm: 1. ") && line.contains("(Bea) 1 village, 20 villagers, 2. ") && line.endsWith("(Alex) 1 village, 12 villagers"),
			"the realm line: " + line);
		data.setLeftOff(land.at(1), true);
		realms = Leaderboard.standings(server, Leaderboard.Board.REALM);
		helper.assertTrue(realms.size() == 1 && realms.get(0).hall().equals(land.at(0)), "a left-off capital's realm is listed: " + realms);
		helper.succeed();
	}

	/** Down for three Sundays: one late announcement at the start, never repeated; the new week starts at the last Sunday 19:00 and its ribbons last until the next. */
	//$ gametest_ticks_batch LAND '100' '"qaLeaderboardWeeksMissed"'
	@GameTest(template = LAND, timeoutTicks = 100, batch = "qaLeaderboardWeeksMissed")
	public void severalMissedWeeksAreAnnouncedOnce(GameTestHelper helper) {
		Land land = LeaderboardGameTests.three(helper);
		MinecraftServer server = land.level().getServer();
		Leaderboard.CLOCK = LeaderboardGameTests.at("2026-10-25T09:00:00Z");
		Leaderboard.settle(server, true);
		Leaderboard.settle(server, true);
		Leaderboard.settle(server, false);
		String late = "The week's leaderboard, missed while the server was down! How the villages did:";
		helper.assertTrue(count(land, late) == 1, "late announcements: " + count(land, late) + " in " + words(land.heard()));
		helper.assertTrue(land.heard().stream().noneMatch(c -> c.getString().startsWith("The week's leaderboard!")), "also read as on time: " + words(land.heard()));
		helper.assertTrue(count(land, "Biggest: 1. Brook (Bea) 20 villagers, 2. Ashford (Alex) 12 villagers, 3. Corth (Cal) 8 villagers") == 1, "the Biggest line: " + words(land.heard()));
		helper.assertTrue(land.data().weekStarted() == Instant.parse("2026-10-18T19:00:00Z").toEpochMilli(), "the week started at " + Instant.ofEpochMilli(land.data().weekStarted()));
		helper.assertTrue(!Leaderboard.ribbons(land.level(), land.hall(1)).isEmpty(), "Brook wears no ribbon after the late reading");
		// That evening it's due again: read on time, once.
		Leaderboard.CLOCK = LeaderboardGameTests.at("2026-10-25T19:00:00Z");
		Leaderboard.settle(server, false);
		Leaderboard.settle(server, false);
		helper.assertTrue(count(land, "The week's leaderboard! How the villages did this week:") == 1 && count(land, late) == 1, "that evening: " + words(land.heard()));
		helper.succeed();
	}

	/** Everyone online is told: two players each read it once. */
	//$ gametest_ticks_batch LAND '100' '"qaLeaderboardTwoPlayers"'
	@GameTest(template = LAND, timeoutTicks = 100, batch = "qaLeaderboardTwoPlayers")
	public void everyoneOnlineHearsItOnce(GameTestHelper helper) {
		Land land = LeaderboardGameTests.three(helper);
		MinecraftServer server = land.level().getServer();
		List<Component> seconds = new ArrayList<>();
		RealmGameTests.listener(helper, seconds, "qa-second");
		Leaderboard.CLOCK = LeaderboardGameTests.at("2026-10-04T19:00:10Z");
		Leaderboard.settle(server, false);
		Leaderboard.settle(server, false);
		String title = "The week's leaderboard! How the villages did this week:";
		String biggest = "Biggest: 1. Brook (Bea) 20 villagers, 2. Ashford (Alex) 12 villagers, 3. Corth (Cal) 8 villagers";
		helper.assertTrue(count(land, title) == 1 && count(land, biggest) == 1, "the owner heard: " + words(land.heard()));
		helper.assertTrue(seconds.stream().filter(c -> c.getString().equals(title)).count() == 1 && seconds.stream().filter(c -> c.getString().equals(biggest)).count() == 1,
			"the second player heard: " + words(seconds));
		helper.succeed();
	}

	/** Due at 19:00 sharp, not a second before; a winner of two boards wears both ribbons and gets both lines; they last exactly a week. */
	//$ gametest_ticks_batch LAND '100' '"qaLeaderboardTwoRibbons"'
	@GameTest(template = LAND, timeoutTicks = 100, batch = "qaLeaderboardTwoRibbons")
	public void aWinnerOfTwoBoardsWearsBothRibbonsForAWeek(GameTestHelper helper) {
		Land land = LeaderboardGameTests.three(helper);
		MinecraftServer server = land.level().getServer();
		Leaderboard.earned(land.level(), land.hall(1), 5000);
		Leaderboard.earned(land.level(), land.hall(0), 100);
		Leaderboard.CLOCK = LeaderboardGameTests.at("2026-10-04T18:59:59Z");
		Leaderboard.settle(server, false);
		helper.assertTrue(land.heard().stream().noneMatch(c -> c.getString().startsWith("The week's leaderboard")), "read a second early: " + words(land.heard()));
		Leaderboard.CLOCK = LeaderboardGameTests.at("2026-10-04T19:00:00Z");
		Leaderboard.settle(server, false);
		helper.assertTrue(count(land, "The week's leaderboard! How the villages did this week:") == 1, "not read at 19:00 sharp: " + words(land.heard()));
		helper.assertTrue(count(land, "Richest: 1. Brook (Bea) 50 emeralds, 2. Ashford (Alex) 1 emerald") == 1, "the Richest line: " + words(land.heard()));
		List<String> boards = Leaderboard.ribbons(land.level(), land.hall(1)).stream().map(RealmData.Ribbon::board).toList();
		helper.assertTrue(boards.contains("biggest") && boards.contains("richest"), "Brook's ribbons: " + boards);
		helper.assertTrue(chronicled(land, 1, "Won the week's leaderboard: Biggest village, with 20 villagers")
			&& chronicled(land, 1, "Won the week's leaderboard: Richest village, with 50 emeralds"), "Brook's chronicle: " + land.entity(1).chronicle());
		helper.assertTrue(land.data().tally(land.at(1)).earned() == 0, "the earnings weren't reset for the new week");
		Leaderboard.CLOCK = LeaderboardGameTests.at("2026-10-11T18:59:59Z");
		helper.assertTrue(Leaderboard.ribbons(land.level(), land.hall(1)).size() == boards.size(), "a ribbon came off before its week was up");
		Leaderboard.CLOCK = LeaderboardGameTests.at("2026-10-11T19:00:00Z");
		helper.assertTrue(Leaderboard.ribbons(land.level(), land.hall(1)).isEmpty(), "a ribbon outlasted its week");
		helper.succeed();
	}

	/** {@code leaderboardDay} Monday and {@code leaderboardHour} 8: the command says so, Sunday 19:00 passes quietly, Monday 08:00 reads it. */
	//$ gametest_ticks_batch LAND '100' '"qaLeaderboardMonday"'
	@GameTest(template = LAND, timeoutTicks = 100, batch = "qaLeaderboardMonday")
	public void anotherDayAndHourFromTheConfig(GameTestHelper helper) {
		Land land = LeaderboardGameTests.three(helper); // the week begun Sunday 2026-09-27 19:00
		MinecraftServer server = land.level().getServer();
		Leaderboard.DAY = DayOfWeek.MONDAY;
		Leaderboard.HOUR = 8;
		land.heard().clear();
		server.getCommands().performPrefixedCommand(land.owner().createCommandSourceStack(), "workplace leaderboard");
		helper.assertTrue(count(land, "The leaderboard so far this week (read out on Monday at 08:00):") == 1, "the command: " + words(land.heard()));
		land.heard().clear();
		// Monday 2026-09-28 08:00 is past already, by the new day: it's read once now, then Sunday evening is quiet.
		Leaderboard.CLOCK = LeaderboardGameTests.at("2026-10-04T19:00:10Z");
		Leaderboard.settle(server, false);
		long first = count(land, "The week's leaderboard! How the villages did this week:");
		land.heard().clear();
		Leaderboard.CLOCK = LeaderboardGameTests.at("2026-10-05T07:59:59Z");
		Leaderboard.settle(server, false);
		helper.assertTrue(land.heard().isEmpty(), "read before Monday 08:00: " + words(land.heard()));
		Leaderboard.CLOCK = LeaderboardGameTests.at("2026-10-05T08:00:00Z");
		Leaderboard.settle(server, false);
		helper.assertTrue(first <= 1 && count(land, "The week's leaderboard! How the villages did this week:") == 1, "Monday 08:00: " + words(land.heard()));
		helper.assertTrue(land.data().weekStarted() == Instant.parse("2026-10-05T08:00:00Z").toEpochMilli(), "the week started " + Instant.ofEpochMilli(land.data().weekStarted()));
		helper.succeed();
	}

	/** A round whose wellbeing isn't known yet doesn't drag the week's average down, and nothing earned (or a loss) counts as no earnings. */
	//$ gametest_ticks_batch LAND '100' '"qaLeaderboardUnknown"'
	@GameTest(template = LAND, timeoutTicks = 100, batch = "qaLeaderboardUnknown")
	public void anUnknownWellbeingAndALossDontCount(GameTestHelper helper) {
		Land land = LeaderboardGameTests.three(helper);
		MinecraftServer server = land.level().getServer();
		Leaderboard.round(land.level(), land.hall(0), 80, 0, 0);
		Leaderboard.round(land.level(), land.hall(0), -1, 0, 0);
		Leaderboard.round(land.level(), land.hall(0), 60, 0, 0);
		Leaderboard.earned(land.level(), land.hall(0), -400);
		Leaderboard.earned(land.level(), land.hall(0), 0);
		Leaderboard.earned(land.level(), land.hall(1), 250);
		List<Leaderboard.Standing> happiest = Leaderboard.standings(server, Leaderboard.Board.HAPPIEST);
		Leaderboard.Standing ashford = happiest.stream().filter(s -> s.hall().equals(land.at(0))).findFirst().orElse(null);
		helper.assertTrue(ashford != null && ashford.value() == 70, "Ashford's week: " + happiest);
		List<Leaderboard.Standing> richest = Leaderboard.standings(server, Leaderboard.Board.RICHEST);
		helper.assertTrue(richest.size() == 1 && richest.get(0).hall().equals(land.at(1)) && land.data().tally(land.at(0)).earned() == 0, "the Richest: " + richest);
		helper.assertTrue(Leaderboard.line(Leaderboard.Board.RICHEST, richest).getString().equals("Richest: 1. Brook (Bea) 2.5 emeralds"),
			Leaderboard.line(Leaderboard.Board.RICHEST, richest).getString());
		helper.succeed();
	}
}
