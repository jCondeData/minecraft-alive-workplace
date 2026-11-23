package io.github.jcondedata.aliveworkplace.test;

import io.github.jcondedata.aliveworkplace.hall.Chronicle;
import io.github.jcondedata.aliveworkplace.hall.QuestJournal;
import io.github.jcondedata.aliveworkplace.hall.VillageHallBlockEntity;
import io.github.jcondedata.aliveworkplace.hall.VillageHalls;
import io.github.jcondedata.aliveworkplace.hall.VillageNeeds;
import io.github.jcondedata.aliveworkplace.registry.ModBlocks;
import io.github.jcondedata.aliveworkplace.story.ArcEffects;
import io.github.jcondedata.aliveworkplace.story.ArcState;
import io.github.jcondedata.aliveworkplace.story.Arcs;
import io.github.jcondedata.aliveworkplace.story.Quest;
import io.github.jcondedata.aliveworkplace.story.Stories;
import io.github.jcondedata.aliveworkplace.work.ChoiceMenu;
import io.netty.channel.embedded.EmbeddedChannel;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.AABB;

/**
 * QA (lane-c-1123-0953) of the story arc engine (ROADMAP 31.4, B724), written from docs/wiki/story-arcs.md: a chapter's
 * time limit on its last day, a save and reload while the next chapter waits, what the players near and far hear at a
 * tale's good and bad ends, the same tale in two villages at once, and the cast villager dying mid-arc.
 */
public class QaStoryArcs1123cGameTests implements FabricGameTest {
	private static final String AREA = "aliveworkplace_test:huge_area";
	private static final BlockPos HALL = new BlockPos(5, 2, 5);
	private static final BlockPos OTHER_HALL = new BlockPos(24, 2, 24);
	private static final ResourceLocation THREE = ResourceLocation.fromNamespaceAndPath("aliveworkplace_test", "test/three_chapters");
	private static final ResourceLocation TALK = ResourceLocation.fromNamespaceAndPath("aliveworkplace_test", "test/talk");
	private static final long DAY = VillageNeeds.DAY;

	// ---- helpers ---------------------------------------------------------------------------------------------------

	private static void settings(GameTestHelper helper) {
		int radius = VillageHalls.RADIUS;
		int cooldown = Arcs.COOLDOWN_DAYS;
		int atOnce = Arcs.AT_ONCE;
		int near = Arcs.NEAR;
		boolean enabled = Arcs.ENABLED;
		boolean auto = Arcs.AUTO;
		VillageHalls.RADIUS = 8;
		Arcs.AUTO = false; // (the rounds here move arcs on; no roll starts another tale in the test's villages)
		Arcs.ENABLED = true;
		Arcs.AT_ONCE = 50;
		Leftovers.after(helper, () -> {
			VillageHalls.RADIUS = radius;
			Arcs.COOLDOWN_DAYS = cooldown;
			Arcs.AT_ONCE = atOnce;
			Arcs.NEAR = near;
			Arcs.ENABLED = enabled;
			Arcs.AUTO = auto;
		});
	}

	private static VillageHallBlockEntity hall(GameTestHelper helper, BlockPos at) {
		ServerLevel level = helper.getLevel();
		BlockPos abs = helper.absolutePos(at);
		Leftovers.after(helper, () -> Arcs.stopAll(level, abs));
		helper.setBlock(at, ModBlocks.VILLAGE_HALL);
		VillageHallBlockEntity entity = (VillageHallBlockEntity) level.getBlockEntity(abs);
		entity.setLastQuestDay(Long.MAX_VALUE / 2);
		Arcs.stopAll(level, abs);
		return entity;
	}

	private static Arcs.Arc arc(GameTestHelper helper, ResourceLocation id) {
		Arcs.Arc arc = Arcs.get(id).orElse(null);
		helper.assertTrue(arc != null, "the test pack's arc " + id + " didn't load");
		return arc;
	}

	private static ServerPlayer player(GameTestHelper helper, BlockPos at) {
		ServerPlayer player = helper.makeMockServerPlayerInLevel();
		player.setGameMode(GameType.SURVIVAL);
		BlockPos abs = helper.absolutePos(at);
		player.teleportTo(abs.getX() + 0.5, abs.getY(), abs.getZ() + 0.5);
		return player;
	}

	/** A logged-in player at {@code at} (absolute) whose chat lines are kept in {@code heard}. */
	private static ServerPlayer listener(GameTestHelper helper, String name, BlockPos at, List<String> heard) {
		ServerLevel level = helper.getLevel();
		MinecraftServer server = level.getServer();
		var cookie = net.minecraft.server.network.CommonListenerCookie.createInitial(new com.mojang.authlib.GameProfile(UUID.randomUUID(), name), false);
		ServerPlayer p = new ServerPlayer(server, level, cookie.gameProfile(), cookie.clientInformation()) {
			@Override
			public void displayClientMessage(Component message, boolean overlay) {
				if (!overlay) {
					heard.add(message.getString());
				}
				super.displayClientMessage(message, overlay);
			}
		};
		net.minecraft.network.Connection connection = new net.minecraft.network.Connection(net.minecraft.network.protocol.PacketFlow.SERVERBOUND);
		new EmbeddedChannel(connection);
		server.getPlayerList().placeNewPlayer(connection, p, cookie);
		Leftovers.after(helper, () -> server.getPlayerList().remove(p));
		p.setGameMode(GameType.SURVIVAL);
		p.teleportTo(at.getX() + 0.5, at.getY(), at.getZ() + 0.5);
		heard.clear();
		return p;
	}

	private static void setDay(ServerLevel level, long day) {
		level.setDayTime((day - 1) * DAY + 1000);
	}

	private static List<Quest> arcQuests(ServerLevel level, BlockPos hall) {
		return Stories.open(level, hall).stream().filter(q -> q.arc != null).toList();
	}

	private static List<String> chronicle(VillageHallBlockEntity entity) {
		return entity.chronicle().stream().filter(e -> e.kind() == Chronicle.Kind.STORY).map(e -> e.text().getString()).toList();
	}

	private static List<Zombie> grubbs(ServerLevel level, BlockPos hall) {
		return level.getEntitiesOfClass(Zombie.class, new AABB(hall).inflate(24), z -> z.isAlive() && z.getTags().contains(ArcEffects.roleTag("grubb")));
	}

	/** Hands in the apples from village {@code hall}'s Story tab, as the player's click does. */
	private static void handInApples(GameTestHelper helper, ServerLevel level, BlockPos hall, ServerPlayer player) {
		ChoiceMenu menu = ChoiceMenu.detached(player, m -> QuestJournal.render(m, level, hall, player, QuestJournal.Tab.STORY));
		for (int slot : QuestJournal.STORY_QUEST_SLOTS) {
			if (menu.icon(slot).is(Items.APPLE)) {
				menu.press(slot, player);
				return;
			}
		}
		helper.fail("no apples quest on the Story tab");
	}

	private static String storyTabTitle(ServerLevel level, BlockPos hall, ServerPlayer player) {
		ChoiceMenu menu = ChoiceMenu.detached(player, m -> QuestJournal.render(m, level, hall, player, QuestJournal.Tab.STORY));
		return menu.icon(22).getHoverName().getString();
	}

	// ---- the tests -------------------------------------------------------------------------------------------------

	/**
	 * Chapter 2 ("Apples wanted", a two-day limit) began on day D: the apples brought on day D+1, its last day, beat the
	 * limit; the failure never runs on the days after (no "ended badly", no mood drop) and the tale goes on to Grubb.
	 */
	//$ gametest_ticks_batch AREA '60' '"qaArcLastDay1123c"'
	@GameTest(template = AREA, timeoutTicks = 60, batch = "qaArcLastDay1123c")
	public void qaApplesBroughtOnTheLastDayBeatTheTimeLimit(GameTestHelper helper) {
		Leftovers.clear(helper);
		settings(helper);
		Leftovers.players(helper);
		ServerLevel level = helper.getLevel();
		VillageHallBlockEntity entity = hall(helper, HALL);
		BlockPos hall = helper.absolutePos(HALL);
		Villager villager = helper.spawn(EntityType.VILLAGER, HALL.east(2));
		ServerPlayer player = player(helper, HALL.south(2));
		player.getInventory().add(new ItemStack(Items.APPLE, 2));
		long day = Chronicle.day(level) + 1;
		setDay(level, day);
		int mood = Stories.mood(level, villager);
		ArcState s = Arcs.start(level, hall, arc(helper, THREE));
		helper.assertTrue(s != null, "didn't start");
		setDay(level, day + 1);
		Arcs.round(level, hall, entity);
		helper.assertTrue(s.chapter == 1 && s.started, "chapter 2 didn't begin on day D");
		setDay(level, day + 2); // (chapter 2 began on day + 1 with two days: day + 2 is its last)
		Arcs.round(level, hall, entity);
		helper.assertTrue(Arcs.running(level, hall) == s && s.chapter == 1, "the limit ran out on its last day");
		handInApples(helper, level, hall, player);
		helper.assertTrue(s.chapter == 2 && s.started, "the apples on the last day didn't move the tale on: chapter " + s.chapter);
		for (long d = day + 3; d <= day + 5; d++) {
			setDay(level, d);
			Arcs.round(level, hall, entity);
		}
		helper.assertTrue(Arcs.running(level, hall) == s && s.chapter == 2, "the tale didn't carry on with Grubb");
		helper.assertTrue(chronicle(entity).stream().noneMatch(l -> l.contains("ended badly")), "the failure ran anyway: " + chronicle(entity));
		helper.assertTrue(Stories.mood(level, villager) == mood, "the failure's mood came anyway: " + Stories.mood(level, villager) + " vs " + mood);
		helper.succeed();
	}

	/**
	 * Saved and loaded while chapter 2 waits for tomorrow (a round trip of {@code aliveworkplace_stories}): the round the
	 * same day still waits, the next day's round begins chapter 2 with its quest, and a player near the village hears the
	 * chapter's heading and intro in chat.
	 */
	//$ gametest_ticks_batch AREA '60' '"qaArcWaitReload1123c"'
	@GameTest(template = AREA, timeoutTicks = 60, batch = "qaArcWaitReload1123c")
	public void qaAReloadWhileTheNextChapterWaitsBeginsItOnItsDay(GameTestHelper helper) {
		Leftovers.clear(helper);
		settings(helper);
		Leftovers.players(helper);
		ServerLevel level = helper.getLevel();
		VillageHallBlockEntity entity = hall(helper, HALL);
		BlockPos hall = helper.absolutePos(HALL);
		List<String> heard = new ArrayList<>();
		listener(helper, "qaNear1123c", hall.south(3), heard);
		long day = Chronicle.day(level) + 1;
		setDay(level, day);
		ArcState first = Arcs.start(level, hall, arc(helper, THREE));
		helper.assertTrue(first != null && heard.contains("The Test of Three, chapter 1: A quiet day")
			&& heard.contains("Nothing happens today, and that is the point."), "chapter 1 as heard: " + heard);
		heard.clear();
		CompoundTag saved = Stories.Data.get(level).save(new CompoundTag(), level.registryAccess());
		level.getDataStorage().set("aliveworkplace_stories", Stories.Data.load(saved, level.registryAccess()));
		ArcState s = Arcs.running(level, hall);
		helper.assertTrue(s != null && s != first && s.chapter == 1 && !s.started && s.nextChapterDay == day + 1,
			"after the reload: " + (s == null ? "no arc" : "chapter " + s.chapter + " started " + s.started + " next " + s.nextChapterDay));
		Arcs.round(level, hall, entity);
		helper.assertTrue(!s.started && arcQuests(level, hall).isEmpty() && heard.isEmpty(), "chapter 2 began a day early: " + heard);
		setDay(level, day + 1);
		Arcs.round(level, hall, entity);
		helper.assertTrue(s.started && s.chapter == 1 && arcQuests(level, hall).size() == 1, "chapter 2 didn't begin on its day");
		helper.assertTrue(heard.contains("The Test of Three, chapter 2: Apples wanted") && heard.contains("The cook needs apples for the test."),
			"chapter 2 as heard: " + heard);
		helper.assertTrue(chronicle(entity).contains("The Test of Three, chapter 2: Apples wanted"), "the chronicle: " + chronicle(entity));
		helper.succeed();
	}

	/**
	 * The happy end, as the players hear it: Grubb killed by the near player, who reads "The tale of The Test of Three has
	 * come to its end."; a player far away hears nothing of it; the Story tab is back to "No story is unfolding here" and
	 * the villagers' chatter of the tale is gone.
	 */
	//$ gametest_ticks_batch AREA '60' '"qaArcHappyEnd1123c"'
	@GameTest(template = AREA, timeoutTicks = 60, batch = "qaArcHappyEnd1123c")
	public void qaTheHappyEndIsToldToThePlayersNearOnly(GameTestHelper helper) {
		Leftovers.clear(helper);
		settings(helper);
		Leftovers.players(helper);
		ServerLevel level = helper.getLevel();
		VillageHallBlockEntity entity = hall(helper, HALL);
		BlockPos hall = helper.absolutePos(HALL);
		List<String> nearHeard = new ArrayList<>();
		List<String> farHeard = new ArrayList<>();
		ServerPlayer near = listener(helper, "qaHero1123c", hall.south(3), nearHeard);
		listener(helper, "qaAway1123c", hall.offset(400, 0, 0), farHeard);
		setDay(level, Chronicle.day(level) + 1);
		Arcs.start(level, hall, arc(helper, THREE));
		Arcs.next(level, hall);
		helper.assertTrue(!Arcs.chatter(level, hall).isEmpty(), "no chatter in chapter 2: " + Arcs.chatter(level, hall));
		Arcs.next(level, hall);
		helper.assertTrue(Arcs.running(level, hall) != null && Arcs.running(level, hall).chapter == 2, "not at Grubb's chapter");
		ArcEffects.check(level);
		List<Zombie> grubb = grubbs(level, hall);
		helper.assertTrue(grubb.size() == 1, "Grubb: " + grubb);
		nearHeard.clear();
		farHeard.clear();
		grubb.get(0).hurt(level.damageSources().playerAttack(near), 1000f);
		helper.assertTrue(Arcs.running(level, hall) == null, "killing Grubb didn't end the tale");
		helper.assertTrue(nearHeard.contains("The tale of The Test of Three has come to its end."), "the near player heard: " + nearHeard);
		helper.assertTrue(farHeard.stream().noneMatch(l -> l.contains("Test of Three")), "a player 400 blocks off heard: " + farHeard);
		helper.assertTrue(storyTabTitle(level, hall, near).equals("No story is unfolding here"), "the Story tab after the end: " + storyTabTitle(level, hall, near));
		helper.assertTrue(Arcs.chatter(level, hall).isEmpty(), "the villagers still talk of the tale: " + Arcs.chatter(level, hall));
		helper.assertTrue(chronicle(entity).contains("The tale of The Test of Three came to its end."), "the chronicle: " + chronicle(entity));
		helper.succeed();
	}

	/**
	 * The bad end: chapter 2's limit runs out; the near player reads "Nobody brought the apples." and "The tale of The Test
	 * of Three has ended badly."; the ending's rewards (5 emeralds to the treasury, the test_done flag) are never paid,
	 * and the village's cooldown starts on the day it failed.
	 */
	//$ gametest_ticks_batch AREA '60' '"qaArcBadEnd1123c"'
	@GameTest(template = AREA, timeoutTicks = 60, batch = "qaArcBadEnd1123c")
	public void qaTheBadEndIsToldAndPaysNoReward(GameTestHelper helper) {
		Leftovers.clear(helper);
		settings(helper);
		Leftovers.players(helper);
		ServerLevel level = helper.getLevel();
		VillageHallBlockEntity entity = hall(helper, HALL);
		BlockPos hall = helper.absolutePos(HALL);
		List<String> heard = new ArrayList<>();
		listener(helper, "qaLate1123c", hall.south(3), heard);
		long day = Chronicle.day(level) + 1;
		setDay(level, day);
		int treasury = entity.treasury();
		Arcs.start(level, hall, arc(helper, THREE));
		setDay(level, day + 1);
		Arcs.round(level, hall, entity);
		heard.clear();
		setDay(level, day + 3);
		Arcs.round(level, hall, entity);
		helper.assertTrue(Arcs.running(level, hall) == null, "the missed limit didn't end the tale");
		helper.assertTrue(heard.contains("Nobody brought the apples.") && heard.contains("The tale of The Test of Three has ended badly."),
			"the near player heard: " + heard);
		helper.assertTrue(heard.stream().noneMatch(l -> l.contains("come to its end")), "a failed tale was told as a happy end: " + heard);
		helper.assertTrue(entity.treasury() == treasury, "the ending's emeralds were paid for a failure: " + entity.treasury() + " vs " + treasury);
		helper.assertTrue(!Stories.villageFlag(level, hall, "test_done"), "the ending's flag was set for a failure");
		helper.assertTrue(Arcs.lastArcDay(level, hall) == day + 3, "the cooldown's day: " + Arcs.lastArcDay(level, hall) + " vs " + (day + 3));
		helper.succeed();
	}

	/**
	 * The same tale in two villages at once: apples brought to village A move only A on; village B's limit runs out and
	 * only B's tale ends badly, its chronicle alone tells it, and A carries on in chapter 3 with its cooldown untouched.
	 */
	//$ gametest_ticks_batch AREA '60' '"qaArcTwoVillages1123c"'
	@GameTest(template = AREA, timeoutTicks = 60, batch = "qaArcTwoVillages1123c")
	public void qaTheSameTaleInTwoVillagesRunsApart(GameTestHelper helper) {
		Leftovers.clear(helper);
		settings(helper);
		Leftovers.players(helper);
		ServerLevel level = helper.getLevel();
		VillageHallBlockEntity entityA = hall(helper, HALL);
		VillageHallBlockEntity entityB = hall(helper, OTHER_HALL);
		BlockPos a = helper.absolutePos(HALL);
		BlockPos b = helper.absolutePos(OTHER_HALL);
		ServerPlayer player = player(helper, HALL.south(2));
		player.getInventory().add(new ItemStack(Items.APPLE, 2));
		long day = Chronicle.day(level) + 1;
		setDay(level, day);
		ArcState inA = Arcs.start(level, a, arc(helper, THREE));
		ArcState inB = Arcs.start(level, b, arc(helper, THREE));
		helper.assertTrue(inA != null && inB != null && inA != inB, "the tale didn't start in both villages");
		setDay(level, day + 1);
		Arcs.round(level, a, entityA);
		Arcs.round(level, b, entityB);
		helper.assertTrue(inA.started && inA.chapter == 1 && inB.started && inB.chapter == 1, "chapter 2 didn't begin in both");
		handInApples(helper, level, a, player);
		helper.assertTrue(inA.chapter == 2, "A's apples didn't move A on");
		helper.assertTrue(inB.chapter == 1 && arcQuests(level, b).size() == 1 && player.getInventory().countItem(Items.APPLE) == 0,
			"A's apples moved B on too, or were taken twice: B chapter " + inB.chapter + " quests " + arcQuests(level, b).size());
		setDay(level, day + 3);
		Arcs.round(level, a, entityA);
		Arcs.round(level, b, entityB);
		helper.assertTrue(Arcs.running(level, b) == null && chronicle(entityB).contains("The tale of The Test of Three ended badly."),
			"B's tale didn't fail: " + chronicle(entityB));
		helper.assertTrue(Arcs.running(level, a) == inA && inA.chapter == 2, "B's failure ended A's tale");
		helper.assertTrue(chronicle(entityA).stream().noneMatch(l -> l.contains("ended badly")), "A's chronicle told B's failure: " + chronicle(entityA));
		helper.assertTrue(Arcs.lastArcDay(level, a) < day + 3 && Arcs.lastArcDay(level, b) == day + 3,
			"the cooldowns: A " + Arcs.lastArcDay(level, a) + ", B " + Arcs.lastArcDay(level, b) + " (B failed on day " + (day + 3) + ")");
		helper.succeed();
	}

	/**
	 * The villager cast as the elder dies before anyone talks to them: talking to another villager of the village never
	 * finishes the talk step, the rounds on the days after go by without error, and the tale is never told as finished
	 * (no "came to its end" in the chronicle).
	 */
	//$ gametest_ticks_batch AREA '60' '"qaArcElderDies1123c"'
	@GameTest(template = AREA, timeoutTicks = 60, batch = "qaArcElderDies1123c")
	public void qaTheCastElderDyingNeverFinishesTheTalk(GameTestHelper helper) {
		Leftovers.clear(helper);
		settings(helper);
		Leftovers.players(helper);
		ServerLevel level = helper.getLevel();
		VillageHallBlockEntity entity = hall(helper, HALL);
		BlockPos hall = helper.absolutePos(HALL);
		Villager ilse = helper.spawn(EntityType.VILLAGER, HALL.east(2));
		ilse.setCustomName(Component.literal("Ilse"));
		ServerPlayer player = player(helper, HALL.east(3));
		long day = Chronicle.day(level) + 1;
		setDay(level, day);
		ArcState s = Arcs.start(level, hall, arc(helper, TALK));
		helper.assertTrue(s != null && ilse.getTags().contains(ArcEffects.roleTag("elder")), "Ilse wasn't cast");
		ilse.hurt(level.damageSources().playerAttack(player), 1000f);
		helper.assertTrue(!ilse.isAlive(), "Ilse lived");
		Villager stranger = helper.spawn(EntityType.VILLAGER, HALL.west(2));
		stranger.setCustomName(Component.literal("Otto"));
		helper.assertTrue(ArcEffects.onTalk(player, level, InteractionHand.MAIN_HAND, stranger) == InteractionResult.PASS,
			"talking to Otto counted for the dead elder");
		for (long d = day + 1; d <= day + 3; d++) {
			setDay(level, d);
			Arcs.round(level, hall, entity);
		}
		helper.assertTrue(chronicle(entity).stream().noneMatch(l -> l.contains("came to its end")),
			"the talk step was done with the elder dead: " + chronicle(entity));
		helper.assertTrue(entity.treasury() >= 0, "the hall broke");
		helper.succeed();
	}
}
