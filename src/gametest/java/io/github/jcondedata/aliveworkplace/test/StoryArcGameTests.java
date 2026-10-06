package io.github.jcondedata.aliveworkplace.test;

import io.github.jcondedata.aliveworkplace.hall.Chronicle;
import io.github.jcondedata.aliveworkplace.hall.QuestJournal;
import io.github.jcondedata.aliveworkplace.hall.VillageHallBlockEntity;
import io.github.jcondedata.aliveworkplace.hall.VillageHallScreen;
import io.github.jcondedata.aliveworkplace.hall.VillageHalls;
import io.github.jcondedata.aliveworkplace.hall.VillageNeeds;
import io.github.jcondedata.aliveworkplace.hall.VillageQuests;
import io.github.jcondedata.aliveworkplace.people.Chatter;
import io.github.jcondedata.aliveworkplace.registry.ModBlocks;
import io.github.jcondedata.aliveworkplace.story.ArcEffects;
import io.github.jcondedata.aliveworkplace.story.ArcState;
import io.github.jcondedata.aliveworkplace.story.Arcs;
import io.github.jcondedata.aliveworkplace.story.Objectives;
import io.github.jcondedata.aliveworkplace.story.Quest;
import io.github.jcondedata.aliveworkplace.story.Stories;
import io.github.jcondedata.aliveworkplace.work.ChoiceMenu;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.commands.CommandSource;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec2;
import net.minecraft.world.phys.Vec3;

/**
 * The story arc engine (ROADMAP 31.4): the test pack's three-chapter arc run from start to end with time skips, its
 * missed time limit, a save and reload mid-chapter, a far {@code place} waiting for a player, the {@code talk}
 * objective, the {@code arcsAtOnce} cap, {@code storyArcs} off, and the operators' commands.
 */
public class StoryArcGameTests implements FabricGameTest {
	private static final String AREA = "aliveworkplace_test:huge_area";
	private static final BlockPos HALL = new BlockPos(5, 2, 5);
	private static final BlockPos OTHER_HALL = new BlockPos(24, 2, 24);
	private static final ResourceLocation THREE = ResourceLocation.fromNamespaceAndPath("aliveworkplace_test", "test/three_chapters");
	private static final ResourceLocation FAR = ResourceLocation.fromNamespaceAndPath("aliveworkplace_test", "test/far_hut");
	private static final ResourceLocation TALK = ResourceLocation.fromNamespaceAndPath("aliveworkplace_test", "test/talk");
	private static final long DAY = VillageNeeds.DAY;

	/** The settings the tests change (a small village radius, the arc limits), put back when the test ends. Once per test. */
	private static void settings(GameTestHelper helper) {
		int radius = VillageHalls.RADIUS;
		int cooldown = Arcs.COOLDOWN_DAYS;
		int atOnce = Arcs.AT_ONCE;
		int near = Arcs.NEAR;
		boolean enabled = Arcs.ENABLED;
		VillageHalls.RADIUS = 8;
		Leftovers.after(helper, () -> {
			VillageHalls.RADIUS = radius;
			Arcs.COOLDOWN_DAYS = cooldown;
			Arcs.AT_ONCE = atOnce;
			Arcs.NEAR = near;
			Arcs.ENABLED = enabled;
		});
	}

	/** A hall at {@code at} whose own rounds post no quests; its arcs end with the test. */
	private static VillageHallBlockEntity hall(GameTestHelper helper, BlockPos at) {
		ServerLevel level = helper.getLevel();
		BlockPos abs = helper.absolutePos(at);
		Leftovers.after(helper, () -> Arcs.stopAll(level, abs));
		helper.setBlock(at, ModBlocks.VILLAGE_HALL);
		VillageHallBlockEntity entity = (VillageHallBlockEntity) level.getBlockEntity(abs);
		entity.setLastQuestDay(Long.MAX_VALUE / 2);
		Arcs.stopAll(level, abs); // (an earlier batch's arc at this spot)
		return entity;
	}

	private static Arcs.Arc arc(GameTestHelper helper, ResourceLocation id) {
		Arcs.Arc arc = Arcs.get(id).orElse(null);
		helper.assertTrue(arc != null, "the test pack's arc " + id + " didn't load: " + Arcs.all().stream().map(Arcs.Arc::id).toList());
		return arc;
	}

	private static ServerPlayer player(GameTestHelper helper, BlockPos at) {
		ServerPlayer player = helper.makeMockServerPlayerInLevel();
		player.setGameMode(GameType.SURVIVAL);
		BlockPos abs = helper.absolutePos(at);
		player.teleportTo(abs.getX() + 0.5, abs.getY(), abs.getZ() + 0.5);
		return player;
	}

	/** The morning of {@code day} (the tests only go forward from today, a few days at most, as days go by in play). */
	private static void setDay(ServerLevel level, long day) {
		level.setDayTime((day - 1) * DAY + 1000);
	}

	private static List<String> chronicle(VillageHallBlockEntity entity) {
		return entity.chronicle().stream().filter(e -> e.kind() == Chronicle.Kind.STORY).map(e -> e.text().getString()).toList();
	}

	private static List<Quest> arcQuests(ServerLevel level, BlockPos hall) {
		return Stories.open(level, hall).stream().filter(q -> q.arc != null).toList();
	}

	private static List<Zombie> grubbs(ServerLevel level, BlockPos hall) {
		return level.getEntitiesOfClass(Zombie.class, new AABB(hall).inflate(24), z -> z.isAlive() && z.getTags().contains(ArcEffects.roleTag("grubb")));
	}

	/** Moves the three-chapter arc on to its third chapter (Grubb) at once, as {@code /workplace story next} does. */
	private static void toGrubb(ServerLevel level, BlockPos hall) {
		while (Arcs.running(level, hall) != null && Arcs.running(level, hall).chapter < 2) {
			Arcs.next(level, hall);
		}
	}

	/**
	 * The test arc from start to end: a quiet day (the next chapter waits for the next day), apples brought to the hall,
	 * then Grubb the Rotten spawned with his name, helmet, extra health and boss bar, and killed. Each chapter is told and
	 * written in the chronicle; the Story tab ticks the chapters done; the villagers talk of the running one; the ending
	 * pays the treasury and sets its flag.
	 */
	//$ gametest_ticks_batch AREA '120' '"storyArcRunsFromStartToEnd"'
	@GameTest(template = AREA, timeoutTicks = 120, batch = "storyArcRunsFromStartToEnd")
	public void storyArcRunsFromStartToEnd(GameTestHelper helper) {
		Leftovers.clear(helper);
		settings(helper);
		Leftovers.players(helper);
		ServerLevel level = helper.getLevel();
		VillageHallBlockEntity entity = hall(helper, HALL);
		BlockPos hall = helper.absolutePos(HALL);
		Arcs.Arc arc = arc(helper, THREE);
		helper.assertTrue(Arcs.get(ResourceLocation.fromNamespaceAndPath("aliveworkplace_test", "test/broken")).isEmpty(), "the broken arc loaded");
		ServerPlayer player = player(helper, HALL.south(2));
		player.getInventory().add(new ItemStack(Items.APPLE, 2));
		long day = Chronicle.day(level) + 1; // (tomorrow morning: the tests only go forward, so other batches' saved days stay in the past)
		setDay(level, day);
		int treasury = entity.treasury();

		ChoiceMenu none = ChoiceMenu.detached(player, m -> QuestJournal.render(m, level, hall, player, QuestJournal.Tab.STORY));
		helper.assertTrue(none.icon(22).getHoverName().getString().equals("No story is unfolding here"), "the empty tab: " + none.icon(22).getHoverName().getString());
		ArcState s = Arcs.start(level, hall, arc);
		helper.assertTrue(s != null && Arcs.running(level, hall) == s, "didn't start");
		ChoiceMenu waiting = ChoiceMenu.detached(player, m -> QuestJournal.render(m, level, hall, player, QuestJournal.Tab.STORY));
		List<String> lore = waiting.icon(QuestJournal.CHAPTER_ROW).getOrDefault(net.minecraft.core.component.DataComponents.LORE,
			net.minecraft.world.item.component.ItemLore.EMPTY).lines().stream().map(Component::getString).toList();
		helper.assertTrue(lore.contains("Day 1 of the tale"), "the tale's day: " + lore);
		List<String> next = waiting.icon(QuestJournal.CHAPTER_ROW + 2).getOrDefault(net.minecraft.core.component.DataComponents.LORE,
			net.minecraft.world.item.component.ItemLore.EMPTY).lines().stream().map(Component::getString).toList();
		helper.assertTrue(waiting.icon(QuestJournal.CHAPTER_ROW + 2).is(Items.CLOCK) && next.contains("Begins on day " + (day + 1)), "the waiting chapter: " + next);
		// The quiet day began and was done at once; the next chapter waits for tomorrow.
		helper.assertTrue(s.chapter == 1 && !s.started && s.nextChapterDay == day + 1, "after the quiet day: chapter " + s.chapter + " started " + s.started
			+ " next " + s.nextChapterDay);
		List<String> told = chronicle(entity);
		helper.assertTrue(told.contains("The Test of Three, chapter 1: A quiet day") && told.contains("Nothing happens today, and that is the point.")
			&& told.contains("The test of three began."), "the chronicle: " + told);
		Arcs.round(level, hall, entity);
		helper.assertTrue(s.chapter == 1 && !s.started && arcQuests(level, hall).isEmpty(), "chapter 2 began the same day");

		// The next day: the apples are asked for, the villagers talk of it, the Story tab shows chapter 1 ticked.
		setDay(level, day + 1);
		Arcs.round(level, hall, entity);
		helper.assertTrue(s.chapter == 1 && s.started, "chapter 2 didn't begin");
		List<Quest> quests = arcQuests(level, hall);
		helper.assertTrue(quests.size() == 1 && quests.get(0).objectives.get(0) instanceof Objectives.Bring && quests.get(0).due < 0
			&& quests.get(0).giver.equals("arc"), "chapter 2's quest: " + quests);
		helper.assertTrue(VillageQuests.open(level, hall).isEmpty(), "an arc quest on the daily board");
		Villager villager = helper.spawn(EntityType.VILLAGER, HALL.east(2));
		helper.assertTrue(Chatter.topics(level, villager, hall).contains("arc"), "no talk of the story: " + Chatter.topics(level, villager, hall));
		ChoiceMenu menu = ChoiceMenu.detached(player, m -> QuestJournal.render(m, level, hall, player, QuestJournal.Tab.STORY));
		helper.assertTrue(menu.icon(QuestJournal.CHAPTER_ROW).getHoverName().getString().equals("The Test of Three"), "the tab's title: "
			+ menu.icon(QuestJournal.CHAPTER_ROW).getHoverName().getString());
		helper.assertTrue(menu.icon(QuestJournal.CHAPTER_ROW + 1).is(Items.ENCHANTED_BOOK)
			&& menu.icon(QuestJournal.CHAPTER_ROW + 1).getHoverName().getString().equals("Chapter 1: A quiet day"), "chapter 1 not ticked: "
			+ menu.icon(QuestJournal.CHAPTER_ROW + 1));
		helper.assertTrue(menu.icon(QuestJournal.CHAPTER_ROW + 2).is(Items.WRITABLE_BOOK)
			&& menu.icon(QuestJournal.CHAPTER_ROW + 2).getHoverName().getString().equals("Chapter 2: Apples wanted"), "chapter 2 not current: "
			+ menu.icon(QuestJournal.CHAPTER_ROW + 2));
		helper.assertTrue(menu.icon(QuestJournal.STORY_QUEST_SLOTS[0]).is(Items.APPLE), "the apples quest on the tab: " + menu.icon(QuestJournal.STORY_QUEST_SLOTS[0]));

		// The apples, handed in from the tab: chapter 3 begins at once and Grubb comes once the check runs.
		menu.press(QuestJournal.STORY_QUEST_SLOTS[0], player);
		helper.assertTrue(s.chapter == 2 && s.started, "chapter 3 didn't begin: chapter " + s.chapter);
		ArcEffects.check(level);
		List<Zombie> grubb = grubbs(level, hall);
		helper.assertTrue(grubb.size() == 1, "Grubb: " + grubb);
		Zombie z = grubb.get(0);
		helper.assertTrue(z.getCustomName() != null && z.getCustomName().getString().equals("Grubb the Rotten") && z.getItemBySlot(net.minecraft.world.entity.EquipmentSlot.HEAD).is(Items.IRON_HELMET)
			&& z.getMaxHealth() >= 30f && z.isPersistenceRequired() && z.hasRestriction() && !z.isBaby(), "Grubb as spawned: " + z);
		ArcEffects.check(level); // (the bar comes with the check after the spawn)
		helper.assertTrue(ArcEffects.bar(z.getUUID()) != null && ArcEffects.bar(z.getUUID()).getPlayers().contains(player), "no boss bar");
		Quest kill = arcQuests(level, hall).get(0);
		helper.assertTrue(kill.objectives.get(0).line().getString().equals("Defeat Grubb the Rotten"), "the kill line: " + kill.objectives.get(0).line().getString());

		z.hurt(level.damageSources().playerAttack(player), 1000f);
		helper.assertTrue(Arcs.running(level, hall) == null, "the arc didn't end");
		helper.assertTrue(entity.treasury() == treasury + 500, "the ending's treasury: " + entity.treasury());
		helper.assertTrue(Stories.villageFlag(level, hall, "test_done"), "the ending's flag");
		helper.assertTrue(Arcs.lastArcDay(level, hall) == day + 1, "the cooldown didn't start: " + Arcs.lastArcDay(level, hall));
		told = chronicle(entity);
		helper.assertTrue(told.contains("The Test of Three, chapter 3: Grubb") && told.contains("The tale of The Test of Three came to its end."),
			"the chronicle: " + told);
		helper.assertTrue(ArcEffects.bar(z.getUUID()) == null, "the boss bar stayed");
		helper.assertTrue(Component.translatable("message.aliveworkplace.arc.ended", arc.name()).getString().equals("The tale of The Test of Three has come to its end."),
			"the ending message");
		helper.succeed();
	}

	/** Chapter 2's apples aren't brought within its two days: its failure runs (the mood, the message) and the arc ends badly. */
	//$ gametest_ticks_batch AREA '60' '"storyArcMissedTimeLimit"'
	@GameTest(template = AREA, timeoutTicks = 60, batch = "storyArcMissedTimeLimit")
	public void storyArcMissedTimeLimit(GameTestHelper helper) {
		Leftovers.clear(helper);
		settings(helper);
		Leftovers.players(helper);
		ServerLevel level = helper.getLevel();
		VillageHallBlockEntity entity = hall(helper, HALL);
		BlockPos hall = helper.absolutePos(HALL);
		Villager villager = helper.spawn(EntityType.VILLAGER, HALL.east(2));
		long day = Chronicle.day(level) + 1; // (tomorrow morning: the tests only go forward, so other batches' saved days stay in the past)
		setDay(level, day);
		int mood = Stories.mood(level, villager);
		ArcState s = Arcs.start(level, hall, arc(helper, THREE));
		setDay(level, day + 1);
		Arcs.round(level, hall, entity);
		helper.assertTrue(s.chapter == 1 && s.started && arcQuests(level, hall).size() == 1, "chapter 2 didn't begin");
		setDay(level, day + 2);
		Arcs.round(level, hall, entity);
		helper.assertTrue(Arcs.running(level, hall) == s, "failed a day early");
		setDay(level, day + 3);
		Arcs.round(level, hall, entity);
		helper.assertTrue(Arcs.running(level, hall) == null, "the time limit didn't end it");
		helper.assertTrue(arcQuests(level, hall).isEmpty(), "its quest stayed up: " + arcQuests(level, hall));
		helper.assertTrue(Stories.mood(level, villager) == mood - 5, "the failure's mood: " + Stories.mood(level, villager) + " vs " + mood);
		List<String> told = chronicle(entity);
		helper.assertTrue(told.contains("The tale of The Test of Three ended badly."), "the chronicle: " + told);
		helper.succeed();
	}

	/**
	 * Saved and loaded mid-chapter (a round trip of {@code aliveworkplace_stories}): the arc carries on in chapter 3 with
	 * Grubb; Grubb lost (gone with an unloaded chunk) is put back at his spot by the next check, and killing him ends it.
	 */
	//$ gametest_ticks_batch AREA '80' '"storyArcSurvivesAReload"'
	@GameTest(template = AREA, timeoutTicks = 80, batch = "storyArcSurvivesAReload")
	public void storyArcSurvivesAReload(GameTestHelper helper) {
		Leftovers.clear(helper);
		settings(helper);
		Leftovers.players(helper);
		ServerLevel level = helper.getLevel();
		VillageHallBlockEntity entity = hall(helper, HALL);
		BlockPos hall = helper.absolutePos(HALL);
		ServerPlayer player = player(helper, HALL.south(2));
		setDay(level, Chronicle.day(level) + 1);
		Arcs.start(level, hall, arc(helper, THREE));
		toGrubb(level, hall);
		ArcEffects.check(level);
		helper.assertTrue(grubbs(level, hall).size() == 1, "no Grubb before the reload");
		UUID first = grubbs(level, hall).get(0).getUUID();

		CompoundTag saved = Stories.Data.get(level).save(new CompoundTag(), level.registryAccess());
		Stories.Data copy = Stories.Data.load(saved, level.registryAccess());
		CompoundTag again = copy.save(new CompoundTag(), level.registryAccess());
		helper.assertTrue(saved.equals(again), "reloaded differs:\n" + saved + "\n" + again);
		level.getDataStorage().set("aliveworkplace_stories", copy);
		ArcState s = Arcs.running(level, hall);
		helper.assertTrue(s != null && s.chapter == 2 && s.started && s.mobs.size() == 1 && first.equals(s.mobs.get(0).uuid),
			"after the reload: " + (s == null ? "no arc" : "chapter " + s.chapter + " mobs " + s.mobs.size()));
		Arcs.round(level, hall, entity);
		helper.assertTrue(Arcs.running(level, hall) == s && s.chapter == 2 && arcQuests(level, hall).size() == 1, "the round moved it");

		grubbs(level, hall).forEach(z -> z.discard());
		ArcEffects.check(level);
		List<Zombie> back = grubbs(level, hall);
		helper.assertTrue(back.size() == 1 && !back.get(0).getUUID().equals(first) && s.mobs.get(0).uuid.equals(back.get(0).getUUID())
			&& back.get(0).getCustomName().getString().equals("Grubb the Rotten"), "Grubb wasn't put back: " + back);
		ArcEffects.check(level);
		helper.assertTrue(grubbs(level, hall).size() == 1, "put back twice");
		back.get(0).hurt(level.damageSources().playerAttack(player), 1000f);
		helper.assertTrue(Arcs.running(level, hall) == null, "killing him didn't end it");
		helper.succeed();
	}

	/** An arc whose file was removed ends quietly at the next round (with a log line), its quest taken down. */
	//$ gametest_ticks_batch AREA '40' '"storyArcFileRemoved"'
	@GameTest(template = AREA, timeoutTicks = 40, batch = "storyArcFileRemoved")
	public void storyArcFileRemoved(GameTestHelper helper) {
		Leftovers.clear(helper);
		settings(helper);
		ServerLevel level = helper.getLevel();
		VillageHallBlockEntity entity = hall(helper, HALL);
		BlockPos hall = helper.absolutePos(HALL);
		setDay(level, Chronicle.day(level) + 1);
		Arcs.start(level, hall, arc(helper, THREE));
		Arcs.next(level, hall);
		helper.assertTrue(arcQuests(level, hall).size() == 1, "chapter 2's quest");
		CompoundTag saved = Stories.Data.get(level).save(new CompoundTag(), level.registryAccess());
		String text = saved.toString().replace("aliveworkplace_test:test/three_chapters", "aliveworkplace_test:test/gone");
		try {
			CompoundTag edited = net.minecraft.nbt.TagParser.parseTag(text);
			level.getDataStorage().set("aliveworkplace_stories", Stories.Data.load(edited, level.registryAccess()));
		} catch (com.mojang.brigadier.exceptions.CommandSyntaxException e) {
			throw new IllegalStateException(e);
		}
		helper.assertTrue(Arcs.running(level, hall) != null, "the gone arc didn't load");
		Arcs.round(level, hall, entity);
		helper.assertTrue(Arcs.running(level, hall) == null && arcQuests(level, hall).isEmpty(), "the gone arc carried on");
		helper.assertTrue(chronicle(entity).stream().noneMatch(l -> l.contains("ended badly")), "it didn't end quietly");
		helper.succeed();
	}

	/**
	 * A far {@code place}: nothing is built while no player is near; once one comes within reach the hut is built (once),
	 * its flag set; the map handed out when the chapter began marks its spot.
	 */
	//$ gametest_ticks_batch AREA '200' '"storyArcFarPlace"'
	@GameTest(template = AREA, timeoutTicks = 200, batch = "storyArcFarPlace")
	public void storyArcFarPlace(GameTestHelper helper) {
		Leftovers.clear(helper);
		settings(helper);
		Leftovers.players(helper);
		ServerLevel level = helper.getLevel();
		hall(helper, HALL);
		BlockPos hall = helper.absolutePos(HALL);
		Arcs.NEAR = 6; // (the land next to the test area stands in for the land 96 blocks out: inside it, the test's barrier roof is the ground)
		BlockPos[] built = new BlockPos[1];
		Leftovers.after(helper, () -> {
			// The hut stands on the open ground outside the test area: it goes, so later batches find the land as it was.
			if (built[0] != null) {
				for (BlockPos q : BlockPos.betweenClosed(built[0], built[0].offset(4, 4, 4))) {
					level.setBlock(q, q.getY() == built[0].getY() ? Blocks.GRASS_BLOCK.defaultBlockState() : Blocks.AIR.defaultBlockState(), 2);
				}
			}
		});
		ServerPlayer player = player(helper, HALL.south(1));
		setDay(level, Chronicle.day(level) + 1);
		int placed = ArcEffects.PLACED.get();
		ArcState s = Arcs.start(level, hall, arc(helper, FAR));
		helper.assertTrue(s != null && s.places.size() == 1 && s.places.get(0).waiting(), "the hut isn't waiting: " + (s == null ? null : s.places));
		helper.assertTrue(player.getInventory().contains(st -> st.is(Items.FILLED_MAP)), "no map to the hut");
		helper.runAfterDelay(90, () -> {
			helper.assertTrue(ArcEffects.PLACED.get() == placed && s.places.get(0).waiting(), "built with nobody near");
			BlockPos near = hall.offset(38, -2, 0);
			player.teleportTo(near.getX() + 0.5, near.getY(), near.getZ() + 0.5);
		});
		helper.runAfterDelay(180, () -> {
			helper.assertTrue(ArcEffects.PLACED.get() == placed + 1, "built " + (ArcEffects.PLACED.get() - placed) + " times");
			ArcState.ArcPlace hut = s.places.get(0);
			built[0] = hut.origin == null ? null : new BlockPos(hut.spot.getX() - 2, hut.spot.getY() - 1, hut.spot.getZ() - 2);
			helper.assertTrue(!hut.waiting() && hut.origin != null && s.flag("hut_done", Chronicle.day(level)), "the hut: " + hut.origin);
			helper.assertTrue(!level.getBlockState(hut.origin).isAir() || !level.getBlockState(hut.origin.above()).isAir(), "nothing at the hut's corner");
			for (int i = 0; i < 3; i++) {
				ArcEffects.check(level); // (and never again)
			}
			helper.assertTrue(ArcEffects.PLACED.get() == placed + 1, "built again");
			helper.succeed();
		});
	}

	/** {@code talk}: the villager cast as the elder is right-clicked; it counts, they say their line, and the arc ends. */
	//$ gametest_ticks_batch AREA '40' '"storyArcTalk"'
	@GameTest(template = AREA, timeoutTicks = 40, batch = "storyArcTalk")
	public void storyArcTalk(GameTestHelper helper) {
		Leftovers.clear(helper);
		settings(helper);
		ServerLevel level = helper.getLevel();
		hall(helper, HALL);
		BlockPos hall = helper.absolutePos(HALL);
		Villager elder = helper.spawn(EntityType.VILLAGER, HALL.east(2));
		elder.setCustomName(Component.literal("Ilse"));
		ServerPlayer player = player(helper, HALL.east(3));
		setDay(level, Chronicle.day(level) + 1);
		ArcState s = Arcs.start(level, hall, arc(helper, TALK));
		helper.assertTrue(s != null && elder.getTags().contains(ArcEffects.roleTag("elder")), "Ilse wasn't cast");
		Quest q = arcQuests(level, hall).get(0);
		helper.assertTrue(q.objectives.get(0).line().getString().equals("Talk to Ilse"), "the line: " + q.objectives.get(0).line().getString());
		Villager stranger = helper.spawn(EntityType.VILLAGER, HALL.west(2));
		helper.assertTrue(ArcEffects.onTalk(player, level, InteractionHand.MAIN_HAND, stranger) == InteractionResult.PASS, "a stranger counted");
		helper.assertTrue(ArcEffects.onTalk(player, level, InteractionHand.MAIN_HAND, elder) == InteractionResult.SUCCESS, "Ilse didn't count");
		helper.assertTrue(Arcs.running(level, hall) == null, "the arc didn't end");
		helper.assertTrue(!elder.getTags().contains(ArcEffects.roleTag("elder")), "Ilse kept her part");
		helper.assertTrue(Component.translatable("message.aliveworkplace.arc.says", "Ilse", "Thank you for coming.").getString()
			.equals("Ilse: \"Thank you for coming.\""), "the line");
		helper.succeed();
	}

	/** No more than {@code arcsAtOnce} arcs run on the server; the cooldown keeps a village from a second at once. */
	//$ gametest_ticks_batch AREA '40' '"storyArcsAtOnce"'
	@GameTest(template = AREA, timeoutTicks = 40, batch = "storyArcsAtOnce")
	public void storyArcsAtOnce(GameTestHelper helper) {
		Leftovers.clear(helper);
		settings(helper);
		ServerLevel level = helper.getLevel();
		hall(helper, HALL);
		hall(helper, OTHER_HALL);
		BlockPos a = helper.absolutePos(HALL);
		BlockPos b = helper.absolutePos(OTHER_HALL);
		setDay(level, Chronicle.day(level) + 1);
		Arcs.COOLDOWN_DAYS = 0;
		Arcs.AT_ONCE = Arcs.runningCount(level.getServer()) + 1;
		RandomSource random = RandomSource.create(7);
		helper.assertTrue(Arcs.tryStart(level, a, random, 1) != null, "the first didn't start");
		helper.assertTrue(Arcs.tryStart(level, b, random, 1) == null, "a second started over the cap of " + Arcs.AT_ONCE);
		helper.assertTrue(Arcs.tryStart(level, a, random, 1) == null && Arcs.running(level, a) != null, "a village got two");
		Arcs.stop(level, a);
		helper.assertTrue(Arcs.tryStart(level, b, random, 1) != null, "the freed place wasn't taken");
		Arcs.COOLDOWN_DAYS = 8;
		helper.assertTrue(Arcs.tryStart(level, a, random, 1) == null, "the cooldown didn't hold");
		Arcs.AT_ONCE = 0;
		Arcs.stop(level, b);
		Arcs.COOLDOWN_DAYS = 0;
		helper.assertTrue(Arcs.tryStart(level, a, random, 1) == null, "arcsAtOnce 0 let one start");
		helper.succeed();
	}

	/** With {@code storyArcs} off no arc starts (by chance or command), and a running one ends quietly at its next round, its mob gone. */
	//$ gametest_ticks_batch AREA '40' '"storyArcsOff"'
	@GameTest(template = AREA, timeoutTicks = 40, batch = "storyArcsOff")
	public void storyArcsOff(GameTestHelper helper) {
		Leftovers.clear(helper);
		settings(helper);
		Leftovers.players(helper);
		ServerLevel level = helper.getLevel();
		VillageHallBlockEntity entity = hall(helper, HALL);
		hall(helper, OTHER_HALL);
		BlockPos hall = helper.absolutePos(HALL);
		player(helper, HALL.south(2));
		setDay(level, Chronicle.day(level) + 1);
		Arcs.COOLDOWN_DAYS = 0;
		Arcs.AT_ONCE = 20;
		Arcs.start(level, hall, arc(helper, THREE));
		toGrubb(level, hall);
		ArcEffects.check(level);
		helper.assertTrue(grubbs(level, hall).size() == 1, "no Grubb");
		Arcs.ENABLED = false;
		helper.assertTrue(Arcs.tryStart(level, helper.absolutePos(OTHER_HALL), RandomSource.create(3), 1) == null, "one started while off");
		List<String> said = new ArrayList<>();
		level.getServer().getCommands().performPrefixedCommand(new_op(level, helper.absolutePos(OTHER_HALL), said), "workplace story start aliveworkplace_test:test/three_chapters");
		helper.assertTrue(Arcs.running(level, helper.absolutePos(OTHER_HALL)) == null && said.contains("Story arcs are switched off (storyArcs in the config)."),
			"the command started one while off: " + said);
		Arcs.round(level, hall, entity);
		helper.assertTrue(Arcs.running(level, hall) == null, "the running arc carried on");
		helper.assertTrue(grubbs(level, hall).isEmpty(), "Grubb stayed");
		helper.assertTrue(chronicle(entity).stream().noneMatch(l -> l.contains("ended")), "it didn't end quietly: " + chronicle(entity));
		helper.succeed();
	}

	/** {@code /workplace story start <arc>}, {@code next} and {@code stop}, with what they say. */
	//$ gametest_ticks_batch AREA '40' '"storyCommands"'
	@GameTest(template = AREA, timeoutTicks = 40, batch = "storyCommands")
	public void storyCommands(GameTestHelper helper) {
		Leftovers.clear(helper);
		settings(helper);
		ServerLevel level = helper.getLevel();
		hall(helper, HALL);
		BlockPos hall = helper.absolutePos(HALL);
		setDay(level, Chronicle.day(level) + 1);
		String village = VillageHalls.name(level, hall).getString();
		List<String> said = new ArrayList<>();
		CommandSourceStack op = new_op(level, hall, said);
		var commands = level.getServer().getCommands();
		commands.performPrefixedCommand(op, "workplace story next");
		helper.assertTrue(said.contains("No story is running in " + village + "."), "next with none: " + said);
		commands.performPrefixedCommand(op, "workplace story start nonsense");
		helper.assertTrue(said.contains("No story arc called nonsense."), "unknown: " + said);
		commands.performPrefixedCommand(op, "workplace story start aliveworkplace_test:test/three_chapters");
		helper.assertTrue(Arcs.running(level, hall) != null && said.contains("The Test of Three begins in " + village + "."), "start: " + said);
		commands.performPrefixedCommand(op, "workplace story start aliveworkplace_test:test/three_chapters");
		helper.assertTrue(said.contains(village + " already has a story running: The Test of Three."), "twice: " + said);
		commands.performPrefixedCommand(op, "workplace story next");
		helper.assertTrue(Arcs.running(level, hall).chapter == 1 && Arcs.running(level, hall).started
			&& said.contains("The Test of Three moves on to chapter 2."), "next: " + said);
		commands.performPrefixedCommand(op, "workplace story stop");
		helper.assertTrue(Arcs.running(level, hall) == null && said.contains("Stopped The Test of Three in " + village + "."), "stop: " + said);
		helper.succeed();
	}

	private static CommandSourceStack new_op(ServerLevel level, BlockPos at, List<String> said) {
		CommandSource out = new CommandSource() {
			@Override
			public void sendSystemMessage(Component message) {
				said.add(message.getString());
			}

			@Override
			public boolean acceptsSuccess() {
				return true;
			}

			@Override
			public boolean acceptsFailure() {
				return true;
			}

			@Override
			public boolean shouldInformAdmins() {
				return false;
			}
		};
		return new CommandSourceStack(out, Vec3.atCenterOf(at.south(2)), Vec2.ZERO, level, 2, "test", Component.literal("test"), level.getServer(), null);
	}
}
