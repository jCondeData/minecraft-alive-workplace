package io.github.jcondedata.aliveworkplace.test;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import io.github.jcondedata.aliveworkplace.WorkplaceConfig;
import io.github.jcondedata.aliveworkplace.build.BuilderLevels;
import io.github.jcondedata.aliveworkplace.guard.BanditCamps;
import io.github.jcondedata.aliveworkplace.guard.VillageRaids;
import io.github.jcondedata.aliveworkplace.hall.Chronicle;
import io.github.jcondedata.aliveworkplace.hall.VillageHallBlockEntity;
import io.github.jcondedata.aliveworkplace.hall.VillageHallScreen;
import io.github.jcondedata.aliveworkplace.hall.VillageHalls;
import io.github.jcondedata.aliveworkplace.hall.VillageQuests;
import io.github.jcondedata.aliveworkplace.hall.VillageRanks;
import io.github.jcondedata.aliveworkplace.registry.ModBlocks;
import io.github.jcondedata.aliveworkplace.registry.ModVillagers;
import io.github.jcondedata.aliveworkplace.story.Arcs;
import io.github.jcondedata.aliveworkplace.story.Gifts;
import io.github.jcondedata.aliveworkplace.story.Objectives;
import io.github.jcondedata.aliveworkplace.story.Quest;
import io.github.jcondedata.aliveworkplace.story.Reputation;
import io.github.jcondedata.aliveworkplace.story.Reputation.Title;
import io.github.jcondedata.aliveworkplace.story.Rewards;
import io.github.jcondedata.aliveworkplace.story.Stories;
import io.github.jcondedata.aliveworkplace.threat.Lairs;
import io.github.jcondedata.aliveworkplace.threat.ThreatData;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.animal.IronGolem;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/**
 * Reputation and titles (ROADMAP 31.11): every way standing is earned and lost, through the game's own entry points;
 * the title thresholds and the Lord's Town rule; a new title told to the whole server once and a lost one only to the
 * player; the {@code reputation} and {@code honour} rewards on a quest and in an arc's ending; the title before a chat
 * line; the hall's tooltip and {@code /workplace standing}; two players kept apart throughout; a save and reload, and
 * a save from before standings; both switches off; and the sentences a player reads.
 */
public class ReputationGameTests implements FabricGameTest {
	static final String AREA = HeartEventGameTests.AREA;
	static final BlockPos HALL = HeartEventGameTests.HALL;
	static final String ME = HeartEventGameTests.ME;
	static final ResourceLocation ARC = ResourceLocation.fromNamespaceAndPath("aliveworkplace_test", "test/standing");

	// --- Setting up -----------------------------------------------------------------------------------------------

	/** A hall called Thornholm alone in its batch, with reputation on and nobody known there; everything is put back afterwards. */
	static void village(GameTestHelper helper, Runnable then) {
		HeartEventGameTests.village(helper, () -> {
			reputation(helper, HALL, "Thornholm");
			then.run();
		});
	}

	/** Reputation on for the hall at {@code at} (named {@code name}, posting no quests of its own), cleaned up when the test ends. */
	static VillageHallBlockEntity reputation(GameTestHelper helper, BlockPos at, String name) {
		boolean enabled = Reputation.ENABLED;
		boolean chat = Reputation.CHAT;
		ServerLevel level = helper.getLevel();
		BlockPos hall = helper.absolutePos(at);
		Reputation.ENABLED = true;
		Reputation.CHAT = true;
		Reputation.TOLD.clear();
		Stories.Data.get(level).remove(hall);
		VillageHallBlockEntity entity = (VillageHallBlockEntity) level.getBlockEntity(hall);
		entity.setCustomName(Component.literal(name));
		entity.setLastQuestDay(Long.MAX_VALUE / 2);
		Leftovers.after(helper, () -> {
			Reputation.ENABLED = enabled;
			Reputation.CHAT = chat;
			Reputation.TOLD.clear();
			Stories.Data.get(level).remove(hall);
		});
		return entity;
	}

	static ServerPlayer player(GameTestHelper helper, BlockPos at) {
		ServerPlayer player = HeartEventGameTests.player(helper, at);
		Leftovers.after(helper, () -> helper.getLevel().getServer().getPlayerList().remove(player));
		return player;
	}

	static VillageHallBlockEntity hallEntity(GameTestHelper helper) {
		return (VillageHallBlockEntity) helper.getLevel().getBlockEntity(helper.absolutePos(HALL));
	}

	static int points(GameTestHelper helper, ServerPlayer player) {
		return Reputation.points(helper.getLevel(), helper.absolutePos(HALL), player.getUUID());
	}

	static Title title(GameTestHelper helper, ServerPlayer player) {
		return Reputation.title(helper.getLevel(), helper.absolutePos(HALL), player.getUUID());
	}

	/** The last thing told about titles: "all: ..." for the whole server, "one: ..." for one player. */
	static String lastTold() {
		Reputation.Told told = Reputation.TOLD.peekLast();
		return told == null ? "" : (told.to() == null ? "all: " : "one: ") + told.text().getString();
	}

	static List<String> titleLines(VillageHallBlockEntity entity) {
		return entity.chronicle().stream().filter(e -> e.kind() == Chronicle.Kind.TITLE).map(e -> e.text().getString()).toList();
	}

	static List<String> strings(List<Component> lines) {
		return lines.stream().map(Component::getString).toList();
	}

	static JsonObject json(String text) {
		return JsonParser.parseString(text).getAsJsonObject();
	}

	/** A quest on the hall's board asking for {@code apples} apples, from {@code giver}, paying {@code rewards} (JSON). */
	static Quest appleQuest(GameTestHelper helper, String giver, int apples, String... rewards) {
		ServerLevel level = helper.getLevel();
		List<Rewards.Reward> paid = new ArrayList<>();
		for (String reward : rewards) {
			paid.add(Rewards.parse(json(reward)));
		}
		Quest quest = new Quest(UUID.randomUUID(), ResourceLocation.fromNamespaceAndPath("aliveworkplace_test", "standing/" + giver + apples), giver,
			Optional.of(Component.literal("Apples")), "", level.getGameTime(), -1,
			List.of(Objectives.parse(json("{\"type\": \"bring\", \"item\": \"minecraft:apple\", \"count\": " + apples + "}"))), new int[1], paid);
		Stories.post(level, helper.absolutePos(HALL), quest);
		return quest;
	}

	static int bring(GameTestHelper helper, ServerPlayer player, Quest quest, int apples) {
		player.getInventory().add(new ItemStack(Items.APPLE, apples));
		return VillageQuests.handIn(player, helper.absolutePos(HALL), quest.id);
	}

	// --- Titles ---------------------------------------------------------------------------------------------------

	/**
	 * Stranger under 50, Friend from 50, Hero from 300, Lord from 1000 only in a Town: a Hero with 1000 in a Hamlet
	 * becomes Lord at the hall's round once the village is a Town (told to the server), is a Hero again if it shrinks
	 * (told to them alone) and Lord once more when it grows back (not told to the server twice).
	 */
	//$ gametest_ticks_batch AREA '60' '"reputationThresholds"'
	@GameTest(template = AREA, timeoutTicks = 60, batch = "reputationThresholds")
	public void theThresholdsAndTheLordsTownRule(GameTestHelper helper) {
		village(helper, () -> {
			ServerLevel level = helper.getLevel();
			BlockPos hall = helper.absolutePos(HALL);
			VillageHallBlockEntity entity = hallEntity(helper);
			helper.assertTrue(Reputation.title(-200, true) == Title.STRANGER && Reputation.title(0, true) == Title.STRANGER
				&& Reputation.title(49, true) == Title.STRANGER, "a Stranger under 50");
			helper.assertTrue(Reputation.title(50, false) == Title.FRIEND && Reputation.title(299, true) == Title.FRIEND, "a Friend from 50 to 299");
			helper.assertTrue(Reputation.title(300, false) == Title.HERO && Reputation.title(999, true) == Title.HERO, "a Hero from 300 to 999");
			helper.assertTrue(Reputation.title(1000, false) == Title.HERO && Reputation.title(50000, false) == Title.HERO, "a Lord outside a Town");
			helper.assertTrue(Reputation.title(1000, true) == Title.LORD, "no Lord at 1000 in a Town");

			ServerPlayer me = player(helper, HALL.south(2));
			entity.setRank(VillageRanks.Rank.HAMLET);
			int told = Reputation.ANNOUNCED.get();
			Reputation.Change change = Reputation.add(level, hall, me, 1000);
			helper.assertTrue(change.now() == Title.HERO && change.announced() && title(helper, me) == Title.HERO && points(helper, me) == 1000,
				"1000 in a Hamlet: " + change);
			helper.assertTrue(lastTold().equals("all: " + ME + " is now a Hero of Thornholm!"), "told: " + lastTold());
			entity.setRank(VillageRanks.Rank.VILLAGE);
			VillageQuests.tick(level, hall, entity);
			helper.assertTrue(title(helper, me) == Title.HERO, "a Lord of a Village");
			entity.setRank(VillageRanks.Rank.TOWN);
			VillageQuests.tick(level, hall, entity);
			helper.assertTrue(title(helper, me) == Title.LORD, "not a Lord of a Town: " + title(helper, me));
			helper.assertTrue(lastTold().equals("all: " + ME + " is now a Lord of Thornholm!"), "told: " + lastTold());
			helper.assertTrue(Reputation.ANNOUNCED.get() == told + 2, "announcements: " + (Reputation.ANNOUNCED.get() - told));
			helper.assertTrue(titleLines(entity).equals(List.of(ME + " became a Hero of the village", ME + " became a Lord of the village")),
				"the chronicle: " + titleLines(entity));
			// The Town shrinks: a Hero again, told only to them. It grows back: Lord once more, and the server isn't told twice.
			entity.setRank(VillageRanks.Rank.VILLAGE);
			VillageQuests.tick(level, hall, entity);
			helper.assertTrue(title(helper, me) == Title.HERO && lastTold().equals("one: You are no longer a Lord of Thornholm. They know you as a Hero now."),
				"after it shrank: " + title(helper, me) + ", " + lastTold());
			entity.setRank(VillageRanks.Rank.CITY);
			VillageQuests.tick(level, hall, entity);
			helper.assertTrue(title(helper, me) == Title.LORD && lastTold().equals("one: You are a Lord of Thornholm once more."),
				"after it grew back: " + title(helper, me) + ", " + lastTold());
			helper.assertTrue(Reputation.ANNOUNCED.get() == told + 2 && titleLines(entity).size() == 2, "told the server again");
			helper.succeed();
		});
	}

	/**
	 * A title reached for the first time is told to the whole server, with a TITLE line in the chronicle; losing it is
	 * told only to that player, and so is winning it back. Another player's title is their own news.
	 */
	//$ gametest_ticks_batch AREA '60' '"reputationAnnounced"'
	@GameTest(template = AREA, timeoutTicks = 60, batch = "reputationAnnounced")
	public void aNewTitleIsToldOnceAndALostOneOnlyToThePlayer(GameTestHelper helper) {
		village(helper, () -> {
			ServerLevel level = helper.getLevel();
			BlockPos hall = helper.absolutePos(HALL);
			VillageHallBlockEntity entity = hallEntity(helper);
			ServerPlayer me = player(helper, HALL.south(2));
			ServerPlayer other = player(helper, HALL.south(3));
			int told = Reputation.ANNOUNCED.get();
			helper.assertTrue(!Reputation.add(level, hall, me, 49).announced() && title(helper, me) == Title.STRANGER && Reputation.TOLD.isEmpty(),
				"told at 49: " + lastTold());
			Reputation.Change friend = Reputation.add(level, hall, me, 1);
			helper.assertTrue(friend.announced() && friend.was() == Title.STRANGER && friend.now() == Title.FRIEND && friend.by() == 1, "at 50: " + friend);
			helper.assertTrue(lastTold().equals("all: " + ME + " is now a Friend of Thornholm!"), "told: " + lastTold());
			helper.assertTrue(titleLines(entity).equals(List.of(ME + " became a Friend of the village")), "the chronicle: " + titleLines(entity));
			// Lost: only they hear of it.
			Reputation.Change lost = Reputation.add(level, hall, me, -10);
			helper.assertTrue(!lost.announced() && lost.now() == Title.STRANGER && points(helper, me) == 40, "after losing 10: " + lost);
			helper.assertTrue(lastTold().equals("one: You are no longer a Friend of Thornholm. You are a stranger there again.")
				&& Reputation.TOLD.peekLast().to().equals(me.getUUID()), "told: " + lastTold());
			// Won back: not news for the server a second time.
			Reputation.Change again = Reputation.add(level, hall, me, 10);
			helper.assertTrue(!again.announced() && again.now() == Title.FRIEND, "won back: " + again);
			helper.assertTrue(lastTold().equals("one: You are a Friend of Thornholm once more."), "told: " + lastTold());
			helper.assertTrue(Reputation.ANNOUNCED.get() == told + 1 && titleLines(entity).size() == 1, "announced again");
			// The next title is news again; and so is someone else's first.
			helper.assertTrue(Reputation.add(level, hall, me, 250).announced() && lastTold().equals("all: " + ME + " is now a Hero of Thornholm!"),
				"a Hero: " + lastTold());
			Reputation.add(level, hall, me, -10);
			helper.assertTrue(lastTold().equals("one: You are no longer a Hero of Thornholm. They know you as a Friend now."), "told: " + lastTold());
			helper.assertTrue(title(helper, other) == Title.STRANGER && points(helper, other) == 0, "the other player moved too");
			helper.assertTrue(Reputation.add(level, hall, other, 60).announced() && Reputation.ANNOUNCED.get() == told + 3, "the other's first title");
			helper.assertTrue(title(helper, me) == Title.FRIEND && points(helper, me) == 290 && title(helper, other) == Title.FRIEND && points(helper, other) == 60,
				"the two: " + points(helper, me) + ", " + points(helper, other));
			helper.succeed();
		});
	}

	// --- Earned ---------------------------------------------------------------------------------------------------

	/**
	 * A daily quest is worth 10 to whoever finishes it and a bounty 40; a {@code reputation} reward pays the finisher or
	 * every helper, and an {@code honour} names them. A player who did nothing has nothing.
	 */
	//$ gametest_ticks_batch AREA '60' '"reputationQuests"'
	@GameTest(template = AREA, timeoutTicks = 60, batch = "reputationQuests")
	public void questsBountiesAndTheirRewards(GameTestHelper helper) {
		village(helper, () -> {
			ServerLevel level = helper.getLevel();
			BlockPos hall = helper.absolutePos(HALL);
			ServerPlayer me = player(helper, HALL.south(2));
			ServerPlayer other = player(helper, HALL.south(3));
			ServerPlayer third = player(helper, HALL.south(4));
			// Two bring an apple each; the second finishes it. Both helpers get the reward's 30 and the honour.
			Quest shared = appleQuest(helper, "hall", 2, "{\"type\": \"reputation\", \"points\": 30, \"who\": \"helpers\"}",
				"{\"type\": \"honour\", \"id\": \"wayfinder\", \"who\": \"helpers\"}");
			helper.assertTrue(bring(helper, me, shared, 1) == 1 && points(helper, me) == 0, "paid before it was done: " + points(helper, me));
			helper.assertTrue(bring(helper, other, shared, 1) == 1 && Stories.open(level, hall).isEmpty(), "the shared quest is still open");
			helper.assertTrue(points(helper, me) == 30 && points(helper, other) == 40, "a helper 30, the finisher 30 + 10: " + points(helper, me) + ", " + points(helper, other));
			helper.assertTrue(Reputation.honours(level, hall, me.getUUID()).equals(List.of("wayfinder"))
				&& Reputation.honours(level, hall, other.getUUID()).equals(List.of("wayfinder")), "the honour: " + Reputation.honours(level, hall, me.getUUID()));
			helper.assertTrue(lastTold().equals("one: Thornholm names you Wayfinder."), "told: " + lastTold());
			helper.assertTrue(titleLines(hallEntity(helper)).contains(ME + " was named Wayfinder"), "the chronicle: " + titleLines(hallEntity(helper)));
			helper.assertTrue(points(helper, third) == 0 && Reputation.honours(level, hall, third.getUUID()).isEmpty(), "the third player got some");
			// A bounty: 40. A plain daily quest with a reward for the finisher: 10 + 5.
			Quest bounty = appleQuest(helper, "bounty", 1);
			helper.assertTrue(bring(helper, third, bounty, 1) == 1 && points(helper, third) == 40, "a bounty: " + points(helper, third));
			Quest daily = appleQuest(helper, "hall", 1, "{\"type\": \"reputation\", \"points\": 5}");
			helper.assertTrue(bring(helper, third, daily, 1) == 1 && points(helper, third) == 55 && title(helper, third) == Title.FRIEND,
				"a daily quest with 5 more: " + points(helper, third));
			// The honour is given once.
			helper.assertTrue(!Reputation.honour(level, hall, me.getUUID(), ME, "wayfinder") && Reputation.honours(level, hall, me.getUUID()).size() == 1, "twice");
			// A reward file with a bad 'who' or no points doesn't read.
			for (String bad : List.of("{\"type\": \"reputation\", \"points\": 5, \"who\": \"everyone\"}", "{\"type\": \"reputation\"}",
				"{\"type\": \"honour\", \"id\": \"Not An Id\"}")) {
				try {
					Rewards.parse(json(bad));
					helper.fail("read " + bad);
				} catch (IllegalArgumentException expected) {
					// as it should
				}
			}
			helper.assertTrue(Rewards.parse(Rewards.parse(json("{\"type\": \"honour\", \"id\": \"healer\", \"who\": \"helpers\"}")).json())
				.equals(new Rewards.Honour("healer", "helpers")), "an honour reward written and read back");
			helper.succeed();
		});
	}

	/** A villager's own request done: 25 to every helper, none to a friend who didn't help. */
	//$ gametest_ticks_batch AREA '80' '"reputationRequest"'
	@GameTest(template = AREA, timeoutTicks = 80, batch = "reputationRequest")
	public void aPersonalRequestPaysEveryHelper(GameTestHelper helper) {
		PersonalRequestGameTests.village(helper, () -> {
			VillageHallBlockEntity entity = reputation(helper, HALL, "Thornholm");
			ServerLevel level = helper.getLevel();
			BlockPos hall = helper.absolutePos(HALL);
			Villager dara = PersonalRequestGameTests.worker(helper, new BlockPos(5, 2, 4), "Dara", "minecraft:farmer", 1);
			ServerPlayer me = PersonalRequestGameTests.friend(helper, new BlockPos(5, 2, 6), dara, 300);
			ServerPlayer other = PersonalRequestGameTests.friend(helper, new BlockPos(6, 2, 6), dara, 420);
			ServerPlayer bystander = PersonalRequestGameTests.friend(helper, new BlockPos(7, 2, 6), dara, 420);
			Quest quest = PersonalRequestGameTests.accepted(helper, "level_up", dara, me);
			PersonalRequestGameTests.command(other, "workplace quest accept " + quest.id);
			helper.assertTrue(quest.helpers.size() == 2, "helpers: " + quest.helpers.keySet());
			BuilderLevels.addXp(level, dara, Gifts.BOTTLE_XP, null);
			VillageQuests.tick(level, hall, entity);
			helper.assertTrue(Stories.open(level, hall).stream().noneMatch(q -> q.id.equals(quest.id)), "the request is still open");
			helper.assertTrue(points(helper, me) == 25 && points(helper, other) == 25 && points(helper, bystander) == 0,
				"standing: " + points(helper, me) + ", " + points(helper, other) + ", " + points(helper, bystander));
			helper.succeed();
		});
	}

	/**
	 * A story chapter is worth 50 to everyone who helped in it, and the ending pays what its file says: here 150 and the
	 * honours Kingslayer and Healer, listed with the standing. The arc's quests don't count as daily quests on top.
	 */
	//$ gametest_ticks_batch AREA '80' '"reputationArc"'
	@GameTest(template = AREA, timeoutTicks = 80, batch = "reputationArc")
	public void anArcsChapterAndEndingPayItsHelpers(GameTestHelper helper) {
		village(helper, () -> {
			ServerLevel level = helper.getLevel();
			BlockPos hall = helper.absolutePos(HALL);
			VillageHallBlockEntity entity = hallEntity(helper);
			boolean arcs = Arcs.ENABLED;
			Arcs.ENABLED = true;
			Arcs.stopAll(level, hall);
			Leftovers.after(helper, () -> {
				Arcs.stopAll(level, hall);
				Arcs.ENABLED = arcs;
			});
			Arcs.Arc arc = Arcs.get(ARC).orElse(null);
			helper.assertTrue(arc != null, "the test pack's arc didn't load (its reputation and honour effects must read): " + ARC);
			ServerPlayer me = player(helper, HALL.south(2));
			ServerPlayer other = player(helper, HALL.south(3));
			ServerPlayer third = player(helper, HALL.south(4));
			helper.assertTrue(Arcs.start(level, hall, arc) != null, "the arc didn't start");
			if (Stories.open(level, hall).stream().noneMatch(q -> q.arc != null)) {
				VillageQuests.tick(level, hall, entity);
			}
			Quest quest = Stories.open(level, hall).stream().filter(q -> q.arc != null).findFirst().orElse(null);
			helper.assertTrue(quest != null, "the chapter's quest isn't up: " + Stories.open(level, hall));
			bring(helper, me, quest, 1);
			helper.assertTrue(points(helper, me) == 0, "paid before the chapter was done");
			bring(helper, other, quest, 1);
			helper.assertTrue(Arcs.running(level, hall) == null, "the arc didn't end");
			helper.assertTrue(points(helper, me) == 200 && points(helper, other) == 200 && points(helper, third) == 0,
				"50 a chapter and 150 at the end: " + points(helper, me) + ", " + points(helper, other) + ", " + points(helper, third));
			helper.assertTrue(title(helper, me) == Title.FRIEND && title(helper, other) == Title.FRIEND, "titles: " + title(helper, me));
			helper.assertTrue(Reputation.honours(level, hall, me.getUUID()).equals(List.of("kingslayer", "healer"))
				&& Reputation.honours(level, hall, other.getUUID()).equals(List.of("kingslayer", "healer"))
				&& Reputation.honours(level, hall, third.getUUID()).isEmpty(), "honours: " + Reputation.honours(level, hall, me.getUUID()));
			// Listed with the standing: the command and the hall's tooltip.
			helper.assertTrue(strings(Reputation.standingLines(me)).equals(List.of("Your standing:", "Thornholm: Friend, 200 standing",
				"  Honours: Kingslayer, Healer of Thornholm")), "the command's lines: " + strings(Reputation.standingLines(me)));
			List<String> lore = PersonalRequestGameTests.lore(VillageHallScreen.forTest(me, hall).icon(VillageHallScreen.NAME));
			helper.assertTrue(lore.contains("Your honours: Kingslayer, Healer of Thornholm"), "the tooltip: " + lore);
			helper.assertTrue(titleLines(entity).contains(ME + " was named Kingslayer") && titleLines(entity).contains(ME + " was named Healer of Thornholm"),
				"the chronicle: " + titleLines(entity));
			// An honour a pack names itself reads as its id until a lang file names it.
			helper.assertTrue(Reputation.honourName("dragon_friend", Component.literal("Thornholm")).getString().equals("Dragon friend"), "a pack's honour");
			helper.succeed();
		});
	}

	/** The player who brings a bandit camp's chief down earns 40 in the village it preyed on. */
	//$ gametest_ticks_batch AREA '80' '"reputationLair"'
	@GameTest(template = AREA, timeoutTicks = 80, batch = "reputationLair")
	public void breakingUpACampEarnsFortyForWhoKilledTheChief(GameTestHelper helper) {
		Leftovers.clear(helper);
		Leftovers.players(helper);
		Leftovers.halls(helper);
		ServerLevel level = helper.getLevel();
		BlockPos at = new BlockPos(1, 2, 1);
		BlockPos hall = helper.absolutePos(at);
		int radius = VillageHalls.RADIUS;
		VillageHalls.RADIUS = 16;
		helper.setBlock(at, ModBlocks.VILLAGE_HALL);
		ThreatData.get(level).forgetPast(hall);
		Leftovers.after(helper, () -> {
			level.getEntitiesOfClass(Mob.class, helper.getBounds().inflate(48),
				m -> m.getTags().contains(Lairs.TAG) || m.getTags().contains(BanditCamps.TAG) || m.getTags().contains(VillageRaids.TAG)).forEach(Mob::discard);
			VillageRaids.forget();
			BanditCamps.forget(level);
			ThreatData.get(level).forgetPast(hall);
			ThreatData.get(level).forgetClock(hall);
			VillageHalls.RADIUS = radius;
		});
		helper.runAfterDelay(2, () -> {
			reputation(helper, at, "Thornholm");
			ServerPlayer me = player(helper, new BlockPos(3, 2, 3));
			ServerPlayer other = player(helper, new BlockPos(4, 2, 3));
			helper.assertTrue(BanditCamps.found(level, hall, helper.absolutePos(new BlockPos(12, 1, 12))) != null, "no camp");
			Lairs.Lair lair = Lairs.near(level, hall).orElseThrow();
			List<Mob> band = Lairs.band(level, lair);
			Mob man = band.stream().filter(m -> !m.getUUID().equals(lair.captain())).findFirst().orElseThrow();
			man.hurt(level.damageSources().playerAttack(other), 1000f);
			helper.assertTrue(Reputation.points(level, hall, other.getUUID()) == 0, "one of the band earned standing: " + Reputation.points(level, hall, other.getUUID()));
			Mob chief = (Mob) level.getEntity(lair.captain());
			helper.assertTrue(chief != null, "no chief");
			chief.hurt(level.damageSources().playerAttack(me), 1000f);
			helper.assertTrue(Lairs.near(level, hall).isEmpty(), "the camp still stands");
			helper.assertTrue(Reputation.points(level, hall, me.getUUID()) == 40 && Reputation.points(level, hall, other.getUUID()) == 0,
				"the chief's killer 40: " + Reputation.points(level, hall, me.getUUID()) + ", " + Reputation.points(level, hall, other.getUUID()));
			helper.succeed();
		});
	}

	/** Three raiders killed in one raid earn 20, once; a raider killed with no raid on earns nothing, and each player counts their own. */
	//$ gametest_ticks_batch AREA '60' '"reputationRaid"'
	@GameTest(template = AREA, timeoutTicks = 60, batch = "reputationRaid")
	public void fightingInARaidEarnsTwentyAtTheThirdRaider(GameTestHelper helper) {
		village(helper, () -> {
			ServerLevel level = helper.getLevel();
			BlockPos hall = helper.absolutePos(HALL);
			VillageRaids.forget();
			Leftovers.after(helper, VillageRaids::forget);
			ServerPlayer me = player(helper, HALL.south(2));
			ServerPlayer other = player(helper, HALL.south(3));
			raider(helper, 0).hurt(level.damageSources().playerAttack(me), 1000f);
			helper.assertTrue(points(helper, me) == 0, "a raider with no raid on: " + points(helper, me));
			helper.assertTrue(VillageRaids.track(level, hall, 6) != null && VillageRaids.active(hall).isPresent(), "no raid");
			raider(helper, 1).hurt(level.damageSources().playerAttack(me), 1000f);
			raider(helper, 2).hurt(level.damageSources().playerAttack(me), 1000f);
			raider(helper, 3).hurt(level.damageSources().playerAttack(other), 1000f);
			helper.assertTrue(points(helper, me) == 0 && points(helper, other) == 0, "paid before the third: " + points(helper, me));
			raider(helper, 4).hurt(level.damageSources().playerAttack(me), 1000f);
			helper.assertTrue(points(helper, me) == 20 && points(helper, other) == 0, "at the third: " + points(helper, me) + ", " + points(helper, other));
			raider(helper, 5).hurt(level.damageSources().playerAttack(me), 1000f);
			raider(helper, 6).hurt(level.damageSources().playerAttack(me), 1000f);
			raider(helper, 7).hurt(level.damageSources().playerAttack(me), 1000f);
			helper.assertTrue(points(helper, me) == 20, "paid twice in one raid: " + points(helper, me));
			// A zombie that is no raider counts for nothing, raid or not.
			Zombie stray = helper.spawn(EntityType.ZOMBIE, new BlockPos(4, 2, 4));
			stray.hurt(level.damageSources().playerAttack(other), 1000f);
			helper.assertTrue(points(helper, other) == 0, "a stray zombie: " + points(helper, other));
			helper.succeed();
		});
	}

	private static Zombie raider(GameTestHelper helper, int n) {
		Zombie zombie = helper.spawn(EntityType.ZOMBIE, new BlockPos(3 + n, 2, 3));
		zombie.addTag(VillageRaids.TAG);
		return zombie;
	}

	/**
	 * Coming to a festival is worth 5, once a festival day, to the players in the village. A gift a villager doesn't
	 * dislike is worth 2, at most 10 a day in one village; a gift they dislike is worth nothing.
	 */
	//$ gametest_ticks_batch AREA '80' '"reputationFestivalGifts"'
	@GameTest(template = AREA, timeoutTicks = 80, batch = "reputationFestivalGifts")
	public void aFestivalAndGifts(GameTestHelper helper) {
		village(helper, () -> {
			ServerLevel level = helper.getLevel();
			BlockPos hall = helper.absolutePos(HALL);
			ServerPlayer me = player(helper, HALL.south(2));
			ServerPlayer away = player(helper, HALL.south(2));
			away.moveTo(hall.getX() + 60.5, hall.getY(), hall.getZ() + 0.5, 0, 0);
			Reputation.onFestival(level, hall);
			Reputation.onFestival(level, hall); // (the festival's every tick calls it)
			helper.assertTrue(points(helper, me) == 5 && points(helper, away) == 0, "the festival: " + points(helper, me) + ", " + points(helper, away));

			List<Item> good = List.of(Items.CAKE, Items.BREAD, Items.EMERALD, Items.COOKIE, Items.APPLE, Items.DIAMOND, Items.BOOK);
			List<Item> bad = List.of(Items.ROTTEN_FLESH, Items.POISONOUS_POTATO, Items.SPIDER_EYE, Items.DIRT, Items.COBBLESTONE);
			// A disliked gift first (before the day's cap could hide it).
			Villager grump = HeartEventGameTests.villager(helper, new BlockPos(4, 2, 12), "Grump");
			Item hated = bad.stream().filter(i -> Gifts.band(grump, new ItemStack(i)).points < 0).findFirst().orElse(null);
			helper.assertTrue(hated != null, "nothing here that a villager dislikes");
			helper.assertTrue(give(me, grump, hated).outcome() == Gifts.Outcome.GIVEN && points(helper, me) == 5, "a disliked gift earned standing: " + points(helper, me));
			// Six villagers each get something they don't dislike: 2 each, and the sixth is over the day's 10.
			for (int i = 0; i < 6; i++) {
				Villager villager = HeartEventGameTests.villager(helper, new BlockPos(4 + i, 2, 5), "Villager" + i);
				Item liked = good.stream().filter(item -> Gifts.band(villager, new ItemStack(item)).points > 0).findFirst().orElse(null);
				helper.assertTrue(liked != null, "nothing here that " + villager.getName().getString() + " doesn't dislike");
				Gifts.Result result = give(me, villager, liked);
				helper.assertTrue(result.outcome() == Gifts.Outcome.GIVEN, "gift " + i + " wasn't taken: " + result.outcome());
				int expected = 5 + Math.min(10, 2 * (i + 1));
				helper.assertTrue(points(helper, me) == expected, "after gift " + (i + 1) + ": " + points(helper, me) + ", expected " + expected);
			}
			helper.assertTrue(points(helper, me) == 15 && points(helper, away) == 0, "the day's cap of 10: " + points(helper, me));
			helper.succeed();
		});
	}

	private static Gifts.Result give(ServerPlayer player, Villager villager, Item item) {
		player.setItemInHand(InteractionHand.MAIN_HAND, Gifts.wrap(new ItemStack(item), player.getGameProfile().getName()));
		return Gifts.give(player, villager, player.getMainHandItem());
	}

	// --- Lost -----------------------------------------------------------------------------------------------------

	/** Hitting a villager costs 10, killing one 150 (and not the hit's 10 on top), a guard 200, an iron golem 100; only whoever did it pays. */
	//$ gametest_ticks_batch AREA '60' '"reputationHarm"'
	@GameTest(template = AREA, timeoutTicks = 60, batch = "reputationHarm")
	public void harmToTheVillageCostsStanding(GameTestHelper helper) {
		village(helper, () -> {
			ServerLevel level = helper.getLevel();
			BlockPos hall = helper.absolutePos(HALL);
			ServerPlayer me = player(helper, HALL.south(2));
			ServerPlayer other = player(helper, HALL.south(3));
			Reputation.add(level, hall, me, 60);
			Reputation.add(level, hall, other, 60);
			Villager dara = HeartEventGameTests.villager(helper, new BlockPos(5, 2, 5), "Dara");
			dara.hurt(level.damageSources().playerAttack(me), 1f);
			helper.assertTrue(dara.isAlive() && points(helper, me) == 50, "a hit: " + points(helper, me));
			// A zombie's blow is nobody's fault.
			Villager olan = HeartEventGameTests.villager(helper, new BlockPos(7, 2, 5), "Olan");
			olan.hurt(level.damageSources().mobAttack(helper.spawn(EntityType.ZOMBIE, new BlockPos(7, 2, 7))), 1f);
			helper.assertTrue(points(helper, me) == 50 && points(helper, other) == 60, "a zombie's blow: " + points(helper, me));
			olan.hurt(level.damageSources().playerAttack(me), 1000f);
			helper.assertTrue(!olan.isAlive() && points(helper, me) == -100, "a villager killed (150, not 160): " + points(helper, me));
			helper.assertTrue(title(helper, me) == Title.STRANGER
				&& lastTold().equals("one: You are no longer a Friend of Thornholm. You are a stranger there again."), "told: " + lastTold());
			Villager guard = HeartEventGameTests.villager(helper, new BlockPos(9, 2, 5), "Bram");
			guard.setVillagerData(guard.getVillagerData().setProfession(ModVillagers.GUARD));
			guard.hurt(level.damageSources().playerAttack(me), 1000f);
			helper.assertTrue(points(helper, me) == -300, "a guard killed: " + points(helper, me));
			IronGolem golem = helper.spawn(EntityType.IRON_GOLEM, new BlockPos(11, 2, 5));
			golem.hurt(level.damageSources().playerAttack(me), 1000f);
			helper.assertTrue(!golem.isAlive() && points(helper, me) == -400, "an iron golem killed: " + points(helper, me));
			helper.assertTrue(points(helper, other) == 60 && title(helper, other) == Title.FRIEND, "the other player paid: " + points(helper, other));
			helper.assertTrue(strings(Reputation.standingLines(me)).equals(List.of("Your standing:", "Thornholm: Stranger, -400 standing")),
				"the command's lines: " + strings(Reputation.standingLines(me)));
			helper.succeed();
		});
	}

	// --- Shown ----------------------------------------------------------------------------------------------------

	/**
	 * In chat a player's title in the village they stand in goes before what they say, else their best anywhere; a
	 * Stranger's line is left alone. The server's own chat decorator is ours ({@code Platform.onChatDecorate}).
	 * {@code titlesInChat} off: no line is decorated, and the titles are still held.
	 */
	//$ gametest_ticks_batch AREA '60' '"reputationChat"'
	@GameTest(template = AREA, timeoutTicks = 60, batch = "reputationChat")
	public void theTitleGoesBeforeAChatLine(GameTestHelper helper) {
		village(helper, () -> {
			ServerLevel level = helper.getLevel();
			BlockPos hall = helper.absolutePos(HALL);
			BlockPos second = new BlockPos(1, 2, 1);
			BlockPos far = helper.absolutePos(second);
			VillageHalls.RADIUS = 4; // (two villages in one test area; HeartEventGameTests.village puts the radius back)
			helper.setBlock(second, ModBlocks.VILLAGE_HALL);
			helper.runAfterDelay(2, () -> {
				reputation(helper, second, "Brackwater");
				ServerPlayer me = player(helper, HALL.south(2));
				ServerPlayer stranger = player(helper, HALL.south(3));
				Component hello = Component.literal("hello");
				helper.assertTrue(Reputation.decorate(me, hello) == hello && Reputation.shown(me).isEmpty(), "a Stranger's line was decorated");
				Reputation.add(level, hall, me, 60);
				Reputation.add(level, far, me, 300);
				// In Thornholm: the title held there, though Brackwater's is the better one.
				helper.assertTrue(Reputation.decorate(me, hello).getString().equals("[Friend of Thornholm] hello"), "in Thornholm: " + Reputation.decorate(me, hello).getString());
				helper.assertTrue(level.getServer().getChatDecorator().decorate(me, hello).getString().equals("[Friend of Thornholm] hello"),
					"the server's decorator: " + level.getServer().getChatDecorator().decorate(me, hello).getString());
				// In Brackwater.
				me.moveTo(far.getX() + 0.5, far.getY(), far.getZ() + 2.5, 0, 0);
				helper.assertTrue(Reputation.decorate(me, hello).getString().equals("[Hero of Brackwater] hello"), "in Brackwater: " + Reputation.decorate(me, hello).getString());
				// In no village: the best anywhere.
				me.moveTo(hall.getX() + 40.5, hall.getY(), hall.getZ() + 40.5, 0, 0);
				helper.assertTrue(VillageHalls.nearest(level, me.blockPosition()).isEmpty(), "still in a village");
				helper.assertTrue(Reputation.decorate(me, hello).getString().equals("[Hero of Brackwater] hello"), "away: " + Reputation.decorate(me, hello).getString());
				// In a village where they are a Stranger: the best anywhere too.
				Reputation.add(level, hall, me, -60);
				me.moveTo(hall.getX() + 0.5, hall.getY(), hall.getZ() + 2.5, 0, 0);
				helper.assertTrue(Reputation.decorate(me, hello).getString().equals("[Hero of Brackwater] hello"), "a Stranger here: " + Reputation.decorate(me, hello).getString());
				// Someone else, and a message nobody sent, are left alone.
				helper.assertTrue(Reputation.decorate(stranger, hello) == hello && Reputation.decorate(null, hello) == hello, "someone else's line was decorated");
				helper.assertTrue(level.getServer().getChatDecorator().decorate(stranger, hello).getString().equals("hello"), "the server's decorator on a Stranger");
				// titlesInChat off.
				Reputation.CHAT = false;
				helper.assertTrue(Reputation.decorate(me, hello) == hello && level.getServer().getChatDecorator().decorate(me, hello).getString().equals("hello"),
					"decorated with titlesInChat off");
				helper.assertTrue(Reputation.title(level, far, me.getUUID()) == Title.HERO && Reputation.shown(me).isPresent(), "the title went with the chat switch");
				helper.succeed();
			});
		});
	}

	/** The hall's name tag tooltip shows the viewer's standing and the three best regarded; {@code /workplace standing} lists every village. */
	//$ gametest_ticks_batch AREA '60' '"reputationTooltip"'
	@GameTest(template = AREA, timeoutTicks = 60, batch = "reputationTooltip")
	public void theHallTooltipAndTheCommand(GameTestHelper helper) {
		village(helper, () -> {
			ServerLevel level = helper.getLevel();
			BlockPos hall = helper.absolutePos(HALL);
			ServerPlayer me = player(helper, HALL.south(2));
			ServerPlayer second = player(helper, HALL.south(3));
			ServerPlayer third = player(helper, HALL.south(4));
			ServerPlayer fourth = player(helper, HALL.south(5));
			ServerPlayer newcomer = player(helper, HALL.south(6));
			List<String> empty = PersonalRequestGameTests.lore(VillageHallScreen.forTest(newcomer, hall).icon(VillageHallScreen.NAME));
			helper.assertTrue(empty.contains("Your standing: 0 (Stranger)") && empty.contains("Nobody has earned a standing here yet"), "an unknown village: " + empty);
			helper.assertTrue(strings(Reputation.standingLines(newcomer)).equals(List.of(
				"No village knows you yet. Finish a village's quests, come to its festivals and fight for it to earn a standing there.")),
				"a newcomer's lines: " + strings(Reputation.standingLines(newcomer)));
			Reputation.add(level, hall, me, 320);
			Reputation.honour(level, hall, me.getUUID(), ME, "wayfinder");
			Reputation.add(level, hall, second, 60);
			Reputation.add(level, hall, third, 10);
			Reputation.add(level, hall, fourth, 5);
			List<String> lore = PersonalRequestGameTests.lore(VillageHallScreen.forTest(me, hall).icon(VillageHallScreen.NAME));
			int at = lore.indexOf("Your standing: 320 (Hero)");
			helper.assertTrue(at >= 0 && lore.subList(at, at + 6).equals(List.of("Your standing: 320 (Hero)", "Your honours: Wayfinder", "Best regarded:",
				"1. " + ME + ", Hero (320)", "2. " + ME + ", Friend (60)", "3. " + ME + ", Stranger (10)")), "the tooltip: " + lore);
			helper.assertTrue(lore.stream().noneMatch(l -> l.startsWith("4. ")), "a fourth on the list: " + lore);
			for (String line : lore.subList(at, at + 6)) {
				helper.assertFalse(line.contains("aliveworkplace.") || line.contains("%"), "a raw line: " + line);
			}
			List<String> theirs = PersonalRequestGameTests.lore(VillageHallScreen.forTest(fourth, hall).icon(VillageHallScreen.NAME));
			helper.assertTrue(theirs.contains("Your standing: 5 (Stranger)") && theirs.contains("1. " + ME + ", Hero (320)"), "the fourth's tooltip: " + theirs);
			// The command: registered, and it lists what the player holds.
			helper.assertTrue(level.getServer().getCommands().getDispatcher().findNode(List.of("workplace", "standing")) != null, "/workplace standing isn't registered");
			PersonalRequestGameTests.command(me, "workplace standing");
			helper.assertTrue(strings(Reputation.standingLines(me)).equals(List.of("Your standing:", "Thornholm: Hero, 320 standing", "  Honours: Wayfinder")),
				"the command's lines: " + strings(Reputation.standingLines(me)));
			helper.assertTrue(strings(Reputation.standingLines(second)).equals(List.of("Your standing:", "Thornholm: Friend, 60 standing")),
				"the second's lines: " + strings(Reputation.standingLines(second)));
			helper.succeed();
		});
	}

	// --- Saved ----------------------------------------------------------------------------------------------------

	/**
	 * Standings, titles, honours and the day's gift count come back from a save as they went in; a save from before
	 * 31.11 (no standings) loads with nobody known, and is written back without them.
	 */
	//$ gametest_ticks_batch AREA '60' '"reputationSaved"'
	@GameTest(template = AREA, timeoutTicks = 60, batch = "reputationSaved")
	public void aSaveAndReloadKeepsStandings(GameTestHelper helper) {
		village(helper, () -> {
			ServerLevel level = helper.getLevel();
			BlockPos hall = helper.absolutePos(HALL);
			ServerPlayer me = player(helper, HALL.south(2));
			ServerPlayer other = player(helper, HALL.south(3));
			Reputation.add(level, hall, me, 320);
			Reputation.honour(level, hall, me.getUUID(), ME, "kingslayer");
			Reputation.honour(level, hall, me.getUUID(), ME, "co_author");
			Reputation.add(level, hall, other, -30);
			Reputation.onFestival(level, hall);
			Villager dara = HeartEventGameTests.villager(helper, new BlockPos(5, 2, 5), "Dara");
			Item liked = List.of(Items.CAKE, Items.BREAD, Items.EMERALD, Items.COOKIE, Items.APPLE).stream()
				.filter(i -> Gifts.band(dara, new ItemStack(i)).points > 0).findFirst().orElseThrow();
			give(me, dara, liked);
			helper.assertTrue(points(helper, me) == 327 && points(helper, other) == -25, "before the save: " + points(helper, me) + ", " + points(helper, other));

			CompoundTag saved = Stories.Data.get(level).save(new CompoundTag(), level.registryAccess());
			Stories.Data loaded = Stories.Data.load(saved, level.registryAccess());
			helper.assertTrue(Reputation.points(loaded, hall, me.getUUID()) == 327 && Reputation.points(loaded, hall, other.getUUID()) == -25,
				"points after the load: " + Reputation.points(loaded, hall, me.getUUID()) + ", " + Reputation.points(loaded, hall, other.getUUID()));
			helper.assertTrue(Reputation.held(loaded, hall, me.getUUID()).equals("hero [kingslayer, co_author]")
				&& Reputation.held(loaded, hall, other.getUUID()).equals("stranger []"), "titles and honours after the load: " + Reputation.held(loaded, hall, me.getUUID()));
			CompoundTag again = loaded.save(new CompoundTag(), level.registryAccess());
			helper.assertTrue(saved.equals(again), "a second save differs:\n" + saved + "\n" + again);
			CompoundTag mine = standing(saved, hall, me.getUUID());
			helper.assertTrue(mine != null && mine.getString("name").equals(ME) && mine.getInt("gift_points") == 2 && mine.contains("gift_day")
				&& mine.contains("festival_day") && mine.getInt("announced") != 0, "what is saved of a standing: " + mine);

			// A save from before 31.11: the same halls with no standings.
			CompoundTag old = saved.copy();
			ListTag halls = old.getList("halls", Tag.TAG_COMPOUND);
			for (int i = 0; i < halls.size(); i++) {
				halls.getCompound(i).remove("standings");
				halls.getCompound(i).remove("village_name");
			}
			Stories.Data before = Stories.Data.load(old, level.registryAccess());
			helper.assertTrue(Reputation.points(before, hall, me.getUUID()) == 0 && Reputation.held(before, hall, me.getUUID()).isEmpty(), "standing out of an old save");
			helper.assertTrue(before.save(new CompoundTag(), level.registryAccess()).equals(old), "an old save isn't written back as it was");
			helper.succeed();
		});
	}

	private static CompoundTag standing(CompoundTag saved, BlockPos hall, UUID player) {
		ListTag halls = saved.getList("halls", Tag.TAG_COMPOUND);
		for (int i = 0; i < halls.size(); i++) {
			if (halls.getCompound(i).getLong("hall") != hall.asLong()) {
				continue;
			}
			ListTag standings = halls.getCompound(i).getList("standings", Tag.TAG_COMPOUND);
			for (int j = 0; j < standings.size(); j++) {
				if (io.github.jcondedata.aliveworkplace.mc.Nbt.getUuid(standings.getCompound(j), "player").equals(player)) {
					return standings.getCompound(j);
				}
			}
		}
		return null;
	}

	// --- Off ------------------------------------------------------------------------------------------------------

	/**
	 * Config {@code reputation} off: nothing is earned or lost, nobody holds a title, the chat and the hall's tooltip
	 * show nothing of it, and the command says so; the saved standing is there again when it's back on. The two
	 * settings switch the two flags.
	 */
	//$ gametest_ticks_batch AREA '60' '"reputationOff"'
	@GameTest(template = AREA, timeoutTicks = 60, batch = "reputationOff")
	public void theSwitchesOff(GameTestHelper helper) {
		village(helper, () -> {
			ServerLevel level = helper.getLevel();
			BlockPos hall = helper.absolutePos(HALL);
			ServerPlayer me = player(helper, HALL.south(2));
			Component hello = Component.literal("hello");
			Reputation.add(level, hall, me, 60);
			int told = Reputation.ANNOUNCED.get();

			Reputation.ENABLED = false;
			helper.assertTrue(Reputation.add(level, hall, me, 500).by() == 0 && points(helper, me) == 60, "earned with reputation off: " + points(helper, me));
			helper.assertTrue(!Reputation.honour(level, hall, me.getUUID(), ME, "wayfinder") && Reputation.honours(level, hall, me.getUUID()).isEmpty(), "an honour with it off");
			Villager dara = HeartEventGameTests.villager(helper, new BlockPos(5, 2, 5), "Dara");
			dara.hurt(level.damageSources().playerAttack(me), 1f);
			dara.hurt(level.damageSources().playerAttack(me), 1000f);
			Reputation.onFestival(level, hall);
			Quest daily = appleQuest(helper, "hall", 1, "{\"type\": \"reputation\", \"points\": 5}");
			helper.assertTrue(bring(helper, me, daily, 1) == 1 && Stories.open(level, hall).isEmpty(), "the quest wasn't finished");
			helper.assertTrue(points(helper, me) == 60, "standing moved with reputation off: " + points(helper, me));
			helper.assertTrue(title(helper, me) == Title.STRANGER && Reputation.shown(me).isEmpty() && Reputation.decorate(me, hello) == hello, "a title with it off");
			List<String> lore = PersonalRequestGameTests.lore(VillageHallScreen.forTest(me, hall).icon(VillageHallScreen.NAME));
			helper.assertTrue(lore.stream().noneMatch(l -> l.contains("standing") || l.contains("Best regarded")), "the tooltip with it off: " + lore);
			helper.assertTrue(strings(Reputation.standingLines(me)).equals(List.of("Standing and titles are switched off on this server.")),
				"the command with it off: " + strings(Reputation.standingLines(me)));
			helper.assertTrue(Reputation.ANNOUNCED.get() == told && Reputation.TOLD.size() == 1, "something was told with it off: " + lastTold());

			// Back on: what was saved is still there.
			Reputation.ENABLED = true;
			helper.assertTrue(points(helper, me) == 60 && title(helper, me) == Title.FRIEND
				&& Reputation.decorate(me, hello).getString().equals("[Friend of Thornholm] hello"), "back on: " + points(helper, me) + ", " + title(helper, me));

			// The settings (applied and put back within this tick, so no other test sees them).
			try {
				WorkplaceConfig config = new WorkplaceConfig();
				helper.assertTrue(config.reputation && config.titlesInChat, "the defaults in a test (the 1.5 gate open) should be on");
				config.reputation = false;
				config.apply();
				helper.assertTrue(!Reputation.ENABLED && Reputation.CHAT, "reputation off: " + Reputation.ENABLED + ", " + Reputation.CHAT);
				config.reputation = true;
				config.titlesInChat = false;
				config.apply();
				helper.assertTrue(Reputation.ENABLED && !Reputation.CHAT, "titlesInChat off: " + Reputation.ENABLED + ", " + Reputation.CHAT);
			} finally {
				new WorkplaceConfig().apply();
			}
			helper.assertTrue(Reputation.ENABLED && Reputation.CHAT, "the defaults didn't come back");
			helper.succeed();
		});
	}
}
