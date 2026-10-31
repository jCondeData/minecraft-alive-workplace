package io.github.jcondedata.aliveworkplace.test;

import io.github.jcondedata.aliveworkplace.AliveWorkplace;
import io.github.jcondedata.aliveworkplace.Expansions;
import io.github.jcondedata.aliveworkplace.hall.Chronicle;
import io.github.jcondedata.aliveworkplace.hall.QuestJournal;
import io.github.jcondedata.aliveworkplace.hall.VillageHallBlockEntity;
import io.github.jcondedata.aliveworkplace.hall.VillageHallScreen;
import io.github.jcondedata.aliveworkplace.hall.VillageHalls;
import io.github.jcondedata.aliveworkplace.hall.VillageLedgerItem;
import io.github.jcondedata.aliveworkplace.hall.VillageNeeds;
import io.github.jcondedata.aliveworkplace.hall.VillageQuests;
import io.github.jcondedata.aliveworkplace.registry.ModBlocks;
import io.github.jcondedata.aliveworkplace.registry.ModItems;
import io.github.jcondedata.aliveworkplace.story.Objectives;
import io.github.jcondedata.aliveworkplace.story.Places;
import io.github.jcondedata.aliveworkplace.story.Quest;
import io.github.jcondedata.aliveworkplace.story.QuestFiles;
import io.github.jcondedata.aliveworkplace.story.QuestTracker;
import io.github.jcondedata.aliveworkplace.story.Rewards;
import io.github.jcondedata.aliveworkplace.story.Stories;
import io.github.jcondedata.aliveworkplace.work.ChoiceMenu;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.regex.Pattern;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerBossEvent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.ItemLore;
import net.minecraft.world.item.component.MapDecorations;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.level.saveddata.maps.MapDecorationTypes;

/**
 * QA (independent, from the spec): the quest engine (ROADMAP 31.2) and the quest journal, tracking and quest maps (31.3)
 * through the player's own ways in: the hall block and its Quests button, the Village Ledger, {@code /workplace quests},
 * the real server tick for the bar and {@code reach}. Boundaries from both sides, save and reload mid-quest, two players,
 * the hall broken, the switch off, broken quest files, and the sentences a player reads.
 */
public class QaQuestJournalGameTests implements FabricGameTest {
	private static final String AREA = "aliveworkplace_test:big_area";
	private static final BlockPos HALL = new BlockPos(9, 2, 9);
	private static final long DAY = VillageNeeds.DAY;
	private static final Pattern BRITISH = Pattern.compile("(?i)\\b(colour|favour|honour|grey|centre|metre|travell|cancell|defence|armour|labour|neighbour|"
		+ "behaviour|organis|realis|recognis)|\\bany more\\b");

	private static BlockPos hall(GameTestHelper helper) {
		int radius = VillageHalls.RADIUS;
		VillageHalls.RADIUS = 16;
		Leftovers.after(helper, () -> VillageHalls.RADIUS = radius);
		helper.setBlock(HALL, ModBlocks.VILLAGE_HALL);
		VillageHallBlockEntity entity = (VillageHallBlockEntity) helper.getLevel().getBlockEntity(helper.absolutePos(HALL));
		entity.setLastQuestDay(Long.MAX_VALUE / 2); // the hall's own rounds post nothing
		return helper.absolutePos(HALL);
	}

	private static VillageHallBlockEntity entity(GameTestHelper helper) {
		return (VillageHallBlockEntity) helper.getLevel().getBlockEntity(helper.absolutePos(HALL));
	}

	private static Quest quest(String file, String poster, long posted, long due, List<Objectives.Objective> objectives, List<Rewards.Reward> rewards) {
		return new Quest(UUID.randomUUID(), AliveWorkplace.id("qa/" + file), "hall", Optional.empty(), poster, posted, due, objectives,
			new int[objectives.size()], rewards);
	}

	private static Quest quest(ServerLevel level, String file, List<Objectives.Objective> objectives, List<Rewards.Reward> rewards) {
		long now = level.getGameTime();
		return quest(file, "Ana", now, now + 3 * DAY, objectives, rewards);
	}

	/** A player in the server's list (so the tick loop and the bars see them) who keeps every message they're sent. */
	private static ServerPlayer player(GameTestHelper helper, List<Component> seen) {
		ServerLevel level = helper.getLevel();
		var cookie = net.minecraft.server.network.CommonListenerCookie.createInitial(
			new com.mojang.authlib.GameProfile(UUID.randomUUID(), "qa-quests"), false);
		ServerPlayer player = new ServerPlayer(level.getServer(), level, cookie.gameProfile(), cookie.clientInformation()) {
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
		BlockPos at = helper.absolutePos(new BlockPos(11, 2, 9));
		player.teleportTo(at.getX() + 0.5, at.getY(), at.getZ() + 0.5);
		Leftovers.after(helper, () -> {
			QuestTracker.untrack(player);
			level.getServer().getPlayerList().remove(player);
		});
		return player;
	}

	private static List<String> texts(List<Component> seen) {
		return seen.stream().map(Component::getString).toList();
	}

	private static List<String> lore(ItemStack stack) {
		ItemLore lore = stack.get(DataComponents.LORE);
		return lore == null ? List.of() : lore.lines().stream().map(Component::getString).toList();
	}

	private static String key(Component c) {
		return c.getContents() instanceof TranslatableContents t ? t.getKey() : "";
	}

	/** Opens the hall as a player does (right-click with an empty hand) and clicks its Quests button. */
	private static ChoiceMenu openJournal(GameTestHelper helper, ServerPlayer player) {
		BlockPos hall = helper.absolutePos(HALL);
		player.teleportTo(hall.getX() + 1.5, hall.getY(), hall.getZ() + 0.5);
		helper.useBlock(HALL, player);
		helper.assertTrue(player.containerMenu instanceof ChoiceMenu, "the hall didn't open: " + player.containerMenu);
		ChoiceMenu menu = (ChoiceMenu) player.containerMenu;
		menu.clicked(VillageHallScreen.QUESTS, 0, ClickType.PICKUP, player);
		return menu;
	}

	private static void click(ChoiceMenu menu, int slot, ServerPlayer player) {
		menu.clicked(slot, 0, ClickType.PICKUP, player);
	}

	private static void kill(GameTestHelper helper, EntityType<? extends net.minecraft.world.entity.LivingEntity> type, BlockPos at, ServerPlayer by) {
		var mob = helper.spawn(type, at);
		mob.hurt(helper.getLevel().damageSources().playerAttack(by), 1000f);
	}

	private static boolean only(ServerBossEvent bar, ServerPlayer player) {
		return bar != null && bar.getPlayers().size() == 1 && bar.getPlayers().contains(player);
	}

	private static int count(ServerPlayer player, net.minecraft.world.item.Item item) {
		return player.getInventory().countItem(item);
	}

	/** The hall block's Quests button opens the journal: four tabs named as the spec says, the quest with who asked, its line, reward and days; a click tracks and a second untracks, each said above the hotbar. */
	//$ gametest_ticks_batch AREA '60' '"qaJournalFromTheHall"'
	@GameTest(template = AREA, timeoutTicks = 60, batch = "qaJournalFromTheHall")
	public void theHallsQuestsButtonOpensTheJournalAndTracks(GameTestHelper helper) {
		Leftovers.clear(helper);
		ServerLevel level = helper.getLevel();
		BlockPos hall = hall(helper);
		Quest quest = quest(level, "kill", List.of(new Objectives.Kill("minecraft:zombie", 2, false)), List.of(new Rewards.Money_(3, 0, 0, false)));
		Stories.post(level, hall, quest);
		List<Component> seen = new CopyOnWriteArrayList<>();
		ServerPlayer me = player(helper, seen);
		ChoiceMenu menu = openJournal(helper, me);

		String[] names = {"Village", "Personal", "Story", "Bounties"};
		for (QuestJournal.Tab tab : QuestJournal.Tab.values()) {
			ItemStack icon = menu.icon(tab.slot);
			helper.assertTrue(icon.getHoverName().getString().equals(names[tab.ordinal()]), "tab " + tab + ": " + icon.getHoverName().getString());
			helper.assertTrue(lore(icon).stream().noneMatch(l -> l.contains("later update")), "a landed tab says it isn't there yet: " + lore(icon));
		}
		ItemStack icon = menu.icon(VillageHallScreen.QUEST_SLOTS[0]);
		List<String> lines = lore(icon);
		helper.assertTrue(lines.contains("Asked by Ana"), "who asked: " + lines);
		helper.assertTrue(lines.stream().anyMatch(l -> l.endsWith("(0/2)")), "the objective's line with progress: " + lines);
		helper.assertTrue(lines.contains("Reward: 3 emeralds"), "the reward: " + lines);
		helper.assertTrue(lines.contains("Up for 3 more days"), "the days left: " + lines);
		helper.assertTrue(lines.contains("Click to track it") && lines.contains("Shift-click to track it"), "the clicks: " + lines);

		click(menu, VillageHallScreen.QUEST_SLOTS[0], me);
		String title = quest.title().getString();
		helper.assertTrue(QuestTracker.isTracked(me, quest.id) && QuestTracker.bar(me) != null, "the click didn't track it");
		helper.assertTrue(texts(seen).contains("Tracking: " + title), "not told: " + texts(seen));
		ItemStack tracked = menu.icon(VillageHallScreen.QUEST_SLOTS[0]);
		helper.assertTrue(lore(tracked).contains("Tracked: shown at the top of your screen") && lore(tracked).contains("Click to stop tracking it"),
			"the tracked icon: " + lore(tracked));
		helper.assertTrue(Boolean.TRUE.equals(tracked.get(DataComponents.ENCHANTMENT_GLINT_OVERRIDE)), "a tracked quest doesn't shine");

		click(menu, VillageHallScreen.QUEST_SLOTS[0], me);
		helper.assertTrue(!QuestTracker.isTracked(me, quest.id) && QuestTracker.bar(me) == null, "a second click didn't untrack it");
		helper.assertTrue(texts(seen).contains("No longer tracking: " + title), "not told: " + texts(seen));
		me.closeContainer();
		helper.succeed();
	}

	/** A hand-in one under the goal leaves the quest open; the rest up to exactly the goal finishes it (the extra stays with the player), pays once into the store and the player, and clicking or handing in again pays nothing more. */
	//$ gametest_ticks_batch AREA '60' '"qaHandInExactlyOnce"'
	@GameTest(template = AREA, timeoutTicks = 60, batch = "qaHandInExactlyOnce")
	public void aHandInToTheGoalPaysExactlyOnce(GameTestHelper helper) {
		Leftovers.clear(helper);
		ServerLevel level = helper.getLevel();
		BlockPos hall = hall(helper);
		helper.setBlock(new BlockPos(11, 2, 13), ModBlocks.STOREHOUSE);
		helper.setBlock(new BlockPos(11, 2, 14), Blocks.CHEST);
		VillageHallBlockEntity entity = entity(helper);
		Quest quest = quest(level, "apples", List.of(new Objectives.Bring("minecraft:apple", 5, "hall", Optional.empty())),
			List.of(new Rewards.Money_(3, 0, 0, false), new Rewards.ItemReward(ResourceLocation.withDefaultNamespace("cake"), 1)));
		Stories.post(level, hall, quest);
		List<Component> seen = new CopyOnWriteArrayList<>();
		ServerPlayer me = player(helper, seen);
		me.getInventory().add(new ItemStack(Items.APPLE, 4));
		int done = entity.questsDone();
		ChoiceMenu menu = openJournal(helper, me);
		helper.assertTrue(lore(menu.icon(VillageHallScreen.QUEST_SLOTS[0])).contains("Click: hand in 4"), "the hint: " + lore(menu.icon(VillageHallScreen.QUEST_SLOTS[0])));

		// One under the goal.
		click(menu, VillageHallScreen.QUEST_SLOTS[0], me);
		helper.assertTrue(quest.progress[0] == 4 && !quest.done() && Stories.open(level, hall).contains(quest), "4 of 5: " + quest);
		helper.assertTrue(count(me, Items.APPLE) == 0 && count(me, Items.EMERALD) == 0 && entity.questsDone() == done, "paid one under the goal");
		helper.assertTrue(lore(menu.icon(VillageHallScreen.QUEST_SLOTS[0])).stream().anyMatch(l -> l.endsWith("(4/5)")),
			"the page didn't follow: " + lore(menu.icon(VillageHallScreen.QUEST_SLOTS[0])));

		// Exactly the goal: only one of three apples goes.
		me.getInventory().add(new ItemStack(Items.APPLE, 3));
		click(menu, VillageHallScreen.QUEST_SLOTS[0], me);
		helper.assertTrue(Stories.open(level, hall).stream().noneMatch(q -> q.id.equals(quest.id)), "still open at 5 of 5");
		helper.assertTrue(count(me, Items.APPLE) == 2, "took more than the goal: " + count(me, Items.APPLE));
		ChestBlockEntity chest = (ChestBlockEntity) level.getBlockEntity(helper.absolutePos(new BlockPos(11, 2, 14)));
		int stored = 0;
		for (int i = 0; i < chest.getContainerSize(); i++) {
			stored += chest.getItem(i).is(Items.APPLE) ? chest.getItem(i).getCount() : 0;
		}
		helper.assertTrue(stored == 5, "the store got " + stored + " apples");
		helper.assertTrue(count(me, Items.EMERALD) == 3 && count(me, Items.CAKE) == 1 && entity.questsDone() == done + 1,
			"paid: " + count(me, Items.EMERALD) + " emeralds, " + count(me, Items.CAKE) + " cake, done " + entity.questsDone());
		helper.assertTrue(texts(seen).stream().filter(s -> s.startsWith("Quest done: ")).count() == 1, "told: " + texts(seen));
		String title = quest.title().getString();
		long lines = entity.chronicle().stream().filter(c -> c.kind() == Chronicle.Kind.QUEST && key(c.text()).equals("chronicle.aliveworkplace.quest")
			&& c.text().getString().endsWith(title)).count();
		helper.assertTrue(lines == 1, "chronicle lines: " + lines);

		// Again: through the page and straight in. Nothing more is taken or paid.
		click(menu, VillageHallScreen.QUEST_SLOTS[0], me);
		helper.assertTrue(VillageQuests.handIn(me, hall, quest.id) <= 0, "handed in to a finished quest");
		helper.assertTrue(count(me, Items.APPLE) == 2 && count(me, Items.EMERALD) == 3 && count(me, Items.CAKE) == 1 && entity.questsDone() == done + 1,
			"paid twice: " + count(me, Items.EMERALD));
		me.closeContainer();
		helper.succeed();
	}

	/** In the hall's round a {@code wait} of a day counts at exactly a day and not a tick before; a quest comes down at its due tick and not one before (the bar of whoever tracked it goes, and an expired quest isn't a done one); the days left read right. */
	//$ gametest_ticks_batch AREA '60' '"qaRoundBoundaries"'
	@GameTest(template = AREA, timeoutTicks = 60, batch = "qaRoundBoundaries")
	public void theRoundsBoundariesFromBothSides(GameTestHelper helper) {
		Leftovers.clear(helper);
		ServerLevel level = helper.getLevel();
		BlockPos hall = hall(helper);
		VillageHallBlockEntity entity = entity(helper);
		List<Component> seen = new CopyOnWriteArrayList<>();
		ServerPlayer me = player(helper, seen);
		long now = level.getGameTime();

		// What the page says about the days left.
		Quest three = quest("days3", "Ana", now, now + 3 * DAY, List.of(new Objectives.Wait(5)), List.of());
		Quest justOver = quest("days2", "Ana", now, now + DAY + 1, List.of(new Objectives.Wait(5)), List.of());
		Quest lastTick = quest("days1", "Ana", now, now + 1, List.of(new Objectives.Wait(5)), List.of());
		helper.assertTrue(lore(QuestJournal.questIcon(level, three, me)).contains("Up for 3 more days"), "3: " + lore(QuestJournal.questIcon(level, three, me)));
		helper.assertTrue(lore(QuestJournal.questIcon(level, justOver, me)).contains("Up for 2 more days"), "a day and a tick: " + lore(QuestJournal.questIcon(level, justOver, me)));
		helper.assertTrue(lore(QuestJournal.questIcon(level, lastTick, me)).contains("Up for 1 more day"), "one tick: " + lore(QuestJournal.questIcon(level, lastTick, me)));

		Quest waitShort = quest("wait_short", "Ana", now - DAY + 1, now + 5 * DAY, List.of(new Objectives.Wait(1)), List.of());
		Quest waitDay = quest("wait_day", "Ana", now - DAY, now + 5 * DAY, List.of(new Objectives.Wait(1)), List.of());
		Quest dueNext = quest("due_next", "Ana", now, now + 1, List.of(new Objectives.Kill("minecraft:zombie", 1, false)), List.of());
		Quest dueNow = quest("due_now", "Ana", now - 3 * DAY, now, List.of(new Objectives.Kill("minecraft:zombie", 1, false)), List.of());
		for (Quest q : List.of(waitShort, waitDay, dueNext, dueNow)) {
			Stories.post(level, hall, q);
		}
		QuestTracker.track(me, level, hall, dueNow.id);
		int done = entity.questsDone();
		Stories.round(level, hall, entity);
		List<UUID> open = Stories.open(level, hall).stream().map(q -> q.id).toList();
		helper.assertTrue(open.contains(waitShort.id) && waitShort.progress[0] == 0, "a day less a tick counted: " + waitShort);
		helper.assertTrue(!open.contains(waitDay.id) && Stories.finished(level, hall, waitDay.file), "a full day didn't finish the wait: " + open);
		helper.assertTrue(open.contains(dueNext.id), "taken down a tick early");
		helper.assertTrue(!open.contains(dueNow.id), "still up at its due tick");
		helper.assertTrue(!Stories.finished(level, hall, dueNow.file), "an expired quest counts as done");
		helper.assertTrue(entity.questsDone() == done + 1, "done count: " + (entity.questsDone() - done));
		QuestTracker.refresh(me);
		helper.assertTrue(QuestTracker.bar(me) == null && !QuestTracker.isTracked(me, dueNow.id), "the expired quest's bar stayed");
		helper.succeed();
	}

	/** {@code reach}: exactly on the radius counts, a block past it or the diagonal just outside doesn't, a spectator never does; the server's own tick moves it on. */
	//$ gametest_ticks_batch AREA '120' '"qaReachEdge"'
	@GameTest(template = AREA, timeoutTicks = 120, batch = "qaReachEdge")
	public void reachCountsOnItsEdgeAndNotPast(GameTestHelper helper) {
		Leftovers.clear(helper);
		ServerLevel level = helper.getLevel();
		BlockPos hall = hall(helper);
		BlockPos spot = helper.absolutePos(new BlockPos(4, 2, 4));
		Quest quest = quest(level, "reach", List.of(new Objectives.Reach(Places.point(spot), 4), new Objectives.Wait(1)), List.of());
		Stories.post(level, hall, quest);
		ServerPlayer me = player(helper, new CopyOnWriteArrayList<>());
		me.teleportTo(spot.getX() + 5.5, spot.getY(), spot.getZ() + 0.5);
		QuestTracker.checkReach(me);
		helper.assertTrue(quest.progress[0] == 0, "counted a block past the radius");
		me.teleportTo(spot.getX() + 3.5, spot.getY(), spot.getZ() + 3.5);
		QuestTracker.checkReach(me);
		helper.assertTrue(quest.progress[0] == 0, "counted the diagonal (3, 3), 4.24 blocks out");
		me.setGameMode(GameType.SPECTATOR);
		me.teleportTo(spot.getX() + 0.5, spot.getY(), spot.getZ() + 0.5);
		QuestTracker.checkReach(me);
		helper.assertTrue(quest.progress[0] == 0, "a spectator counted");
		me.setGameMode(GameType.SURVIVAL);
		me.teleportTo(spot.getX() + 4.5, spot.getY(), spot.getZ() + 0.5);
		// Not called here: the server's tick does it.
		helper.succeedWhen(() -> helper.assertTrue(quest.progress[0] == 1 && quest.current() == 1, "not counted on the radius: " + quest));
	}

	/** Saved and loaded mid-quest (the dimension's data read back from disk): progress, who helped, tracking and a found place all stay; the bar comes back and follows the next kill; the place isn't looked up again. */
	//$ gametest_ticks_batch AREA '60' '"qaSaveReloadMidQuest"'
	@GameTest(template = AREA, timeoutTicks = 60, batch = "qaSaveReloadMidQuest")
	public void aSaveAndReloadMidQuestKeepsEverything(GameTestHelper helper) {
		Leftovers.clear(helper);
		ServerLevel level = helper.getLevel();
		BlockPos hall = hall(helper);
		BlockPos spot = helper.absolutePos(new BlockPos(2, 2, 16));
		Quest quest = quest(level, "reload", List.of(new Objectives.Kill("minecraft:zombie", 3, false)), List.of());
		Quest going = quest(level, "reload_place", List.of(new Objectives.Reach(Places.read(com.google.gson.JsonParser.parseString(
			"{\"point\": [" + spot.getX() + ", " + spot.getY() + ", " + spot.getZ() + "]}")), 2)), List.of());
		Quest located = Stories.locate(level, hall, going);
		Stories.post(level, hall, quest);
		Stories.post(level, hall, located);
		ServerPlayer me = player(helper, new CopyOnWriteArrayList<>());
		kill(helper, EntityType.ZOMBIE, new BlockPos(5, 2, 5), me);
		helper.assertTrue(quest.progress[0] == 1, "the first kill: " + quest);
		QuestTracker.track(me, level, hall, quest.id);

		var registries = level.registryAccess();
		Stories.Data before = Stories.Data.get(level);
		level.getDataStorage().set("aliveworkplace_stories", Stories.Data.load(before.save(new CompoundTag(), registries), registries));
		helper.assertTrue(Stories.Data.get(level) != before, "the data wasn't replaced");

		Quest reloaded = Stories.open(level, hall).stream().filter(q -> q.id.equals(quest.id)).findFirst().orElse(null);
		helper.assertTrue(reloaded != null && reloaded != quest && reloaded.progress[0] == 1, "progress lost: " + reloaded);
		helper.assertTrue(Integer.valueOf(1).equals(reloaded.helpers.get(me.getUUID())), "who helped lost: " + reloaded.helpers);
		helper.assertTrue(reloaded.due == quest.due && reloaded.posted == quest.posted && reloaded.poster.equals("Ana"), "dates lost");
		helper.assertTrue(QuestTracker.isTracked(me, quest.id), "tracking lost");
		QuestTracker.refresh(me);
		ServerBossEvent bar = QuestTracker.bar(me);
		helper.assertTrue(bar != null && bar.getName().getString().endsWith("(1/3)") && Math.abs(bar.getProgress() - 1 / 3f) < 0.01f,
			"the bar after reload: " + (bar == null ? null : bar.getName().getString() + " " + bar.getProgress()));

		Quest place = Stories.open(level, hall).stream().filter(q -> q.id.equals(located.id)).findFirst().orElseThrow();
		int lookups = Places.LOOKUPS.get();
		helper.assertTrue(Stories.locate(level, hall, place) == place && Places.LOOKUPS.get() == lookups, "looked up again after reload");
		helper.assertTrue(((Objectives.Reach) place.objectives.get(0)).place().found().equals(Optional.of(spot)), "the place moved: " + place);

		kill(helper, EntityType.ZOMBIE, new BlockPos(5, 2, 6), me);
		helper.assertTrue(reloaded.progress[0] == 2 && QuestTracker.bar(me).getName().getString().endsWith("(2/3)"),
			"after reload the kill didn't count or the bar didn't follow: " + reloaded);
		helper.succeed();
	}

	/** Two players: each tracks their own quest and sees only their own bar; tracking another replaces the first; the shared quest pays only whoever finished it, and the other's bar goes with it. */
	//$ gametest_ticks_batch AREA '60' '"qaTwoPlayers"'
	@GameTest(template = AREA, timeoutTicks = 60, batch = "qaTwoPlayers")
	public void twoPlayersTrackTheirOwnAndOnlyTheFinisherIsPaid(GameTestHelper helper) {
		Leftovers.clear(helper);
		ServerLevel level = helper.getLevel();
		BlockPos hall = hall(helper);
		Quest zombies = quest(level, "zombie", List.of(new Objectives.Kill("minecraft:zombie", 1, false)), List.of(new Rewards.Money_(2, 0, 0, false)));
		Quest skeletons = quest(level, "skeleton", List.of(new Objectives.Kill("minecraft:skeleton", 2, false)), List.of());
		Stories.post(level, hall, zombies);
		Stories.post(level, hall, skeletons);
		ServerPlayer me = player(helper, new CopyOnWriteArrayList<>());
		ServerPlayer other = player(helper, new CopyOnWriteArrayList<>());
		ChoiceMenu mine = ChoiceMenu.detached(me, m -> VillageHallScreen.renderQuests(m, level, hall, me));
		ChoiceMenu theirs = ChoiceMenu.detached(other, m -> VillageHallScreen.renderQuests(m, level, hall, other));
		click(mine, VillageHallScreen.QUEST_SLOTS[0], me);
		click(theirs, VillageHallScreen.QUEST_SLOTS[1], other);
		ServerBossEvent myBar = QuestTracker.bar(me);
		ServerBossEvent theirBar = QuestTracker.bar(other);
		helper.assertTrue(QuestTracker.isTracked(me, zombies.id) && QuestTracker.isTracked(other, skeletons.id), "tracking");
		helper.assertTrue(myBar != theirBar && only(myBar, me) && only(theirBar, other),
			"bars: " + myBar.getPlayers() + " / " + theirBar.getPlayers());
		helper.assertTrue(!myBar.getName().getString().equals(theirBar.getName().getString()), "both bars read " + myBar.getName().getString());

		// One tracked quest a player: tracking another replaces it.
		QuestTracker.track(me, level, hall, skeletons.id);
		helper.assertTrue(QuestTracker.isTracked(me, skeletons.id) && !QuestTracker.isTracked(me, zombies.id), "two tracked at once");
		QuestTracker.track(me, level, hall, zombies.id);

		// The other player finishes mine.
		kill(helper, EntityType.ZOMBIE, new BlockPos(5, 2, 5), other);
		helper.assertTrue(count(other, Items.EMERALD) == 2 && count(me, Items.EMERALD) == 0,
			"paid: finisher " + count(other, Items.EMERALD) + ", the other " + count(me, Items.EMERALD));
		helper.assertTrue(QuestTracker.bar(me) == null && !QuestTracker.isTracked(me, zombies.id), "my bar outlived the quest");
		helper.assertTrue(QuestTracker.bar(other) != null && QuestTracker.isTracked(other, skeletons.id), "their tracking went with my quest");

		// Both on the same quest: each has their own bar, and both follow.
		QuestTracker.track(me, level, hall, skeletons.id);
		kill(helper, EntityType.SKELETON, new BlockPos(5, 2, 6), me);
		helper.assertTrue(QuestTracker.bar(me) != QuestTracker.bar(other) && only(QuestTracker.bar(me), me)
			&& only(QuestTracker.bar(other), other), "one bar shared");
		helper.assertTrue(Math.abs(QuestTracker.bar(me).getProgress() - 0.5f) < 0.01f && Math.abs(QuestTracker.bar(other).getProgress() - 0.5f) < 0.01f,
			"bars: " + QuestTracker.bar(me).getProgress() + " / " + QuestTracker.bar(other).getProgress());
		helper.succeed();
	}

	/** The hall broken mid-quest: its quests go, nothing can be handed in or killed for them, and the server's tick takes the tracker's bar down. */
	//$ gametest_ticks_batch AREA '100' '"qaQuestHallBroken"'
	@GameTest(template = AREA, timeoutTicks = 100, batch = "qaQuestHallBroken")
	public void breakingTheHallEndsItsQuestsAndBars(GameTestHelper helper) {
		Leftovers.clear(helper);
		ServerLevel level = helper.getLevel();
		BlockPos hall = hall(helper);
		Quest quest = quest(level, "broken", List.of(new Objectives.Kill("minecraft:zombie", 2, false)), List.of(new Rewards.Money_(2, 0, 0, false)));
		Quest bring = quest(level, "broken_bring", List.of(new Objectives.Bring("minecraft:apple", 2, "hall", Optional.empty())),
			List.of(new Rewards.Money_(2, 0, 0, false)));
		Stories.post(level, hall, quest);
		Stories.post(level, hall, bring);
		ServerPlayer me = player(helper, new CopyOnWriteArrayList<>());
		me.getInventory().add(new ItemStack(Items.APPLE, 2));
		QuestTracker.track(me, level, hall, quest.id);
		helper.assertTrue(QuestTracker.bar(me) != null, "no bar");
		kill(helper, EntityType.ZOMBIE, new BlockPos(5, 2, 5), me);
		helper.assertTrue(quest.progress[0] == 1, "the first kill");

		helper.setBlock(HALL, Blocks.AIR);
		helper.assertTrue(Stories.open(level, hall).isEmpty(), "quests outlived their hall: " + Stories.open(level, hall));
		helper.assertTrue(VillageQuests.handIn(me, hall, bring.id) <= 0 && count(me, Items.APPLE) == 2, "handed in to a broken hall");
		kill(helper, EntityType.ZOMBIE, new BlockPos(5, 2, 6), me);
		helper.assertTrue(count(me, Items.EMERALD) == 0, "paid for a quest whose hall is gone");
		helper.succeedWhen(() -> helper.assertTrue(QuestTracker.bar(me) == null && !QuestTracker.isTracked(me, quest.id), "the bar outlived the hall"));
	}

	/** {@code /workplace quests} typed as a player: nothing open, then the list with a working [Track] link, [Untrack] once tracked, untrack, a quest no longer open, and the gate shut. */
	//$ gametest_ticks_batch AREA '60' '"qaQuestsCommand"'
	@GameTest(template = AREA, timeoutTicks = 60, batch = "qaQuestsCommand")
	public void theQuestsCommandListsTracksAndUntracks(GameTestHelper helper) {
		Leftovers.clear(helper);
		ServerLevel level = helper.getLevel();
		BlockPos hall = hall(helper);
		List<Component> seen = new CopyOnWriteArrayList<>();
		ServerPlayer me = player(helper, seen);
		var commands = level.getServer().getCommands();

		commands.performPrefixedCommand(me.createCommandSourceStack(), "workplace quests");
		helper.assertTrue(texts(seen).contains("No open quests in the village nearest you."), "with none open: " + texts(seen));

		Quest quest = quest(level, "command", List.of(new Objectives.Kill("minecraft:zombie", 2, false)), List.of());
		Stories.post(level, hall, quest);
		seen.clear();
		commands.performPrefixedCommand(me.createCommandSourceStack(), "workplace quests");
		Component line = seen.stream().filter(c -> key(c).equals("command.aliveworkplace.quests.line")).findFirst().orElse(null);
		helper.assertTrue(texts(seen).contains("Your quests:") && line != null, "the list: " + texts(seen));
		helper.assertTrue(line.getString().contains(VillageHalls.name(level, hall).getString()) && line.getString().contains("(0/2)")
			&& line.getString().endsWith("[Track]"), "the line: " + line.getString());
		Object[] args = ((TranslatableContents) line.getContents()).getArgs();
		ClickEvent click = ((Component) args[args.length - 1]).getStyle().getClickEvent();
		helper.assertTrue(click != null && click.getAction() == ClickEvent.Action.RUN_COMMAND, "no link: " + click);

		// Clicking [Track] runs its command.
		seen.clear();
		commands.performPrefixedCommand(me.createCommandSourceStack(), click.getValue().substring(1));
		helper.assertTrue(QuestTracker.isTracked(me, quest.id) && QuestTracker.bar(me) != null, "[Track] didn't track: " + click.getValue());
		helper.assertTrue(texts(seen).contains("Tracking: " + quest.title().getString()), "told: " + texts(seen));
		seen.clear();
		commands.performPrefixedCommand(me.createCommandSourceStack(), "workplace quests");
		helper.assertTrue(texts(seen).stream().anyMatch(s -> s.endsWith("[Untrack]")), "not offered [Untrack]: " + texts(seen));

		seen.clear();
		commands.performPrefixedCommand(me.createCommandSourceStack(), "workplace quests untrack");
		helper.assertTrue(!QuestTracker.isTracked(me, quest.id) && QuestTracker.bar(me) == null, "untrack");
		helper.assertTrue(texts(seen).contains("You're no longer tracking a quest."), "told: " + texts(seen));

		seen.clear();
		commands.performPrefixedCommand(me.createCommandSourceStack(), "workplace quests track " + UUID.randomUUID());
		helper.assertTrue(texts(seen).stream().anyMatch(s -> s.startsWith("That quest isn't open")) && QuestTracker.bar(me) == null,
			"a quest not open: " + texts(seen));

		boolean open = Expansions.openForTests;
		Expansions.openForTests = false;
		try {
			seen.clear();
			commands.performPrefixedCommand(me.createCommandSourceStack(), "workplace quests track " + quest.id);
			helper.assertTrue(texts(seen).contains("The quest journal isn't available yet.") && !QuestTracker.isTracked(me, quest.id),
				"gate shut: " + texts(seen));
		} finally {
			Expansions.openForTests = open;
		}
		helper.succeed();
	}

	/** The Village Ledger opens the journal from afar: the Quests button shows the tabs and the open quest. */
	//$ gametest_ticks_batch AREA '60' '"qaLedgerJournal"'
	@GameTest(template = AREA, timeoutTicks = 60, batch = "qaLedgerJournal")
	public void theLedgerOpensTheJournalFromAfar(GameTestHelper helper) {
		Leftovers.clear(helper);
		ServerLevel level = helper.getLevel();
		BlockPos hall = hall(helper);
		Quest quest = quest(level, "ledger", List.of(new Objectives.Kill("minecraft:zombie", 2, false)), List.of());
		Stories.post(level, hall, quest);
		ServerPlayer me = player(helper, new CopyOnWriteArrayList<>());
		ItemStack ledger = new ItemStack(ModItems.VILLAGE_LEDGER);
		me.setItemInHand(InteractionHand.MAIN_HAND, ledger);
		VillageLedgerItem.bind(level, me, ledger, hall);
		BlockPos far = helper.absolutePos(new BlockPos(1, 2, 20));
		me.teleportTo(far.getX() + 0.5, far.getY(), far.getZ() + 0.5);
		helper.assertTrue(ledger.use(level, me, InteractionHand.MAIN_HAND).getResult().consumesAction(), "the ledger didn't open");
		helper.assertTrue(me.containerMenu instanceof ChoiceMenu, "no screen: " + me.containerMenu);
		ChoiceMenu menu = (ChoiceMenu) me.containerMenu;
		menu.clicked(VillageHallScreen.QUESTS, 0, ClickType.PICKUP, me);
		helper.assertTrue(menu.icon(QuestJournal.Tab.VILLAGE.slot).getHoverName().getString().equals("Village"), "no journal tabs");
		helper.assertTrue(menu.icon(VillageHallScreen.QUEST_SLOTS[0]).getHoverName().getString().equals(quest.title().getString()), "the quest isn't there");
		click(menu, VillageHallScreen.QUEST_SLOTS[0], me);
		helper.assertTrue(QuestTracker.isTracked(me, quest.id), "tracking from afar");
		me.closeContainer();
		helper.succeed();
	}

	/** {@code villageQuests} off: nothing new goes up in the morning, but an open quest still shows in the journal, can be tracked, moves on a kill and pays. */
	//$ gametest_ticks_batch AREA '60' '"qaSwitchedOff"'
	@GameTest(template = AREA, timeoutTicks = 60, batch = "qaSwitchedOff")
	public void switchedOffOpenQuestsStillWork(GameTestHelper helper) {
		Leftovers.clear(helper);
		ServerLevel level = helper.getLevel();
		BlockPos hall = hall(helper);
		VillageHallBlockEntity entity = entity(helper);
		long dayTime = level.getDayTime();
		Leftovers.after(helper, () -> {
			Stories.ENABLED = true;
			level.setDayTime(dayTime);
		});
		Stories.ENABLED = false;
		Quest quest = quest(level, "off", List.of(new Objectives.Kill("minecraft:zombie", 1, false)), List.of(new Rewards.Money_(2, 0, 0, false)));
		Stories.post(level, hall, quest);
		ServerPlayer me = player(helper, new CopyOnWriteArrayList<>());

		level.setDayTime((dayTime / 24000) * 24000 + 1000);
		entity.setLastQuestDay(-1);
		VillageQuests.tick(level, hall, entity);
		helper.assertTrue(Stories.open(level, hall).stream().filter(q -> q.giver.equals("hall")).count() == 1, "posted while off: " + Stories.open(level, hall));

		ChoiceMenu menu = ChoiceMenu.detached(me, m -> VillageHallScreen.renderQuests(m, level, hall, me));
		helper.assertTrue(menu.icon(VillageHallScreen.QUEST_SLOTS[0]).getHoverName().getString().equals(quest.title().getString()), "not in the journal");
		click(menu, VillageHallScreen.QUEST_SLOTS[0], me);
		helper.assertTrue(QuestTracker.bar(me) != null, "can't track while off");
		kill(helper, EntityType.ZOMBIE, new BlockPos(5, 2, 5), me);
		helper.assertTrue(count(me, Items.EMERALD) == 2 && QuestTracker.bar(me) == null, "not finished while off: " + count(me, Items.EMERALD));
		helper.succeed();
	}

	/** Broken quest files of every kind are skipped with one log line each naming the file; the good ones, a one-day file among them, still load; {@code days} 0 is refused. */
	//$ gametest_batch 'FabricGameTest.EMPTY_STRUCTURE' '"qaBrokenQuestFiles"'
	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE, batch = "qaBrokenQuestFiles")
	public void brokenQuestFilesAreLoggedAndSkipped(GameTestHelper helper) {
		var manager = helper.getLevel().getServer().getResourceManager();
		Map<ResourceLocation, Resource> real = manager.listResources(QuestFiles.FOLDER, p -> p.getPath().endsWith(".json"));
		Resource any = real.values().iterator().next();
		int count = QuestFiles.all().size();
		Map<String, String> bad = Map.of(
			"syntax", "{\"giver\": \"hall\", \"objectives\": [",
			"array", "[1, 2, 3]",
			"empty", "",
			"days_zero", "{\"giver\": \"hall\", \"days\": 0, \"objectives\": [{\"type\": \"wait\", \"days\": 1}]}",
			"no_objectives", "{\"giver\": \"hall\", \"objectives\": []}",
			"bad_place", "{\"giver\": \"hall\", \"objectives\": [{\"type\": \"reach\", \"place\": {\"point\": [1, 2]}}]}");
		Map<ResourceLocation, Resource> withPack = new HashMap<>(real);
		bad.forEach((name, text) -> withPack.put(ResourceLocation.fromNamespaceAndPath("aliveworkplace_test", "quests/qa/" + name + ".json"),
			new Resource(any.source(), () -> new ByteArrayInputStream(text.getBytes(StandardCharsets.UTF_8)))));
		withPack.put(ResourceLocation.fromNamespaceAndPath("aliveworkplace_test", "quests/qa/one_day.json"), new Resource(any.source(),
			() -> new ByteArrayInputStream(("{\"giver\": \"hall\", \"weight\": 0, \"days\": 1, \"objectives\": [{\"type\": \"reach\", \"place\":"
				+ " [{\"structure\": \"#aliveworkplace:no_such_places\"}, {\"biome\": \"minecraft:plains\"}], \"radius\": 1}]}").getBytes(StandardCharsets.UTF_8))));

		List<String> logged = new CopyOnWriteArrayList<>();
		var appender = new org.apache.logging.log4j.core.appender.AbstractAppender("qaBrokenQuestFiles", null, null, true,
			org.apache.logging.log4j.core.config.Property.EMPTY_ARRAY) {
			@Override
			public void append(org.apache.logging.log4j.core.LogEvent event) {
				String message = event.getMessage().getFormattedMessage();
				if (message.contains("quest file") && message.contains("aliveworkplace_test:quests/qa/")) {
					logged.add(message);
				}
			}
		};
		var root = ((org.apache.logging.log4j.core.LoggerContext) org.apache.logging.log4j.LogManager.getContext(false)).getConfiguration().getRootLogger();
		appender.start();
		root.addAppender(appender, null, null);
		try {
			QuestFiles.load(withPack);
		} finally {
			root.removeAppender("qaBrokenQuestFiles");
			appender.stop();
			// what's checked below was read under the test pack; put the real files back first
		}
		boolean oneDay = QuestFiles.get(ResourceLocation.fromNamespaceAndPath("aliveworkplace_test", "qa/one_day")).map(f -> f.days() == 1).orElse(false);
		int loaded = QuestFiles.all().size();
		List<String> skipped = new ArrayList<>();
		for (String name : bad.keySet()) {
			if (QuestFiles.get(ResourceLocation.fromNamespaceAndPath("aliveworkplace_test", "qa/" + name)).isPresent()) {
				skipped.add("LOADED " + name);
			}
		}
		QuestFiles.load(real);
		helper.assertTrue(skipped.isEmpty(), "broken files loaded: " + skipped);
		helper.assertTrue(oneDay, "a one-day file didn't load");
		helper.assertTrue(loaded == count + 1, "the good files: " + loaded + ", expected " + (count + 1));
		for (String name : bad.keySet()) {
			long lines = logged.stream().filter(m -> m.contains("aliveworkplace_test:quests/qa/" + name + ".json")).count();
			helper.assertTrue(lines == 1, name + ": " + lines + " log lines in " + logged);
		}
		helper.assertTrue(logged.size() == bad.size(), "log lines: " + logged);
		helper.assertTrue(QuestFiles.all().size() == count, "not restored: " + QuestFiles.all().size());
		helper.succeed();
	}

	/** A file whose place can't be found anywhere is withdrawn with a chronicle line naming it, and the morning takes the next file instead; each place is looked up once. */
	//$ gametest_ticks_batch AREA '60' '"qaWithdrawnPlace"'
	@GameTest(template = AREA, timeoutTicks = 60, batch = "qaWithdrawnPlace")
	public void aQuestWhosePlaceIsNowhereIsWithdrawn(GameTestHelper helper) {
		Leftovers.clear(helper);
		ServerLevel level = helper.getLevel();
		BlockPos hall = hall(helper);
		BlockPos spot = helper.absolutePos(new BlockPos(3, 2, 15));
		var manager = level.getServer().getResourceManager();
		Map<ResourceLocation, Resource> real = manager.listResources(QuestFiles.FOLDER, p -> p.getPath().endsWith(".json"));
		Resource any = real.values().iterator().next();
		Map<ResourceLocation, Resource> withPack = new HashMap<>(real);
		withPack.put(ResourceLocation.fromNamespaceAndPath("aliveworkplace_test", "quests/qa/nowhere.json"), new Resource(any.source(),
			() -> new ByteArrayInputStream(("{\"giver\": \"hall\", \"priority\": 100001, \"name\": \"Find the Lost Tower\", \"objectives\": [{\"type\": \"reach\","
				+ " \"place\": {\"structure\": \"#aliveworkplace:no_such_places\"}, \"radius\": 8}], \"rewards\": [{\"type\": \"map\","
				+ " \"place\": {\"structure\": \"#aliveworkplace:no_such_places\"}}]}").getBytes(StandardCharsets.UTF_8))));
		withPack.put(ResourceLocation.fromNamespaceAndPath("aliveworkplace_test", "quests/qa/somewhere.json"), new Resource(any.source(),
			() -> new ByteArrayInputStream(("{\"giver\": \"hall\", \"priority\": 100000, \"objectives\": [{\"type\": \"reach\", \"place\": {\"point\": ["
				+ spot.getX() + ", " + spot.getY() + ", " + spot.getZ() + "]}, \"radius\": 8}], \"rewards\": [{\"type\": \"map\", \"place\": {\"point\": ["
				+ spot.getX() + ", " + spot.getY() + ", " + spot.getZ() + "]}}]}").getBytes(StandardCharsets.UTF_8))));
		Quest made;
		int lookups = Places.LOOKUPS.get();
		try {
			QuestFiles.load(withPack);
			made = Stories.make(level, hall, 1f, RandomSource.create(31));
		} finally {
			QuestFiles.load(real);
		}
		int looked = Places.LOOKUPS.get() - lookups;
		helper.assertTrue(made != null && made.file.equals(ResourceLocation.fromNamespaceAndPath("aliveworkplace_test", "qa/somewhere")), "made: " + made);
		helper.assertTrue(looked == 2, "lookups: " + looked + " (one per quest, the same place twice counts once)");
		helper.assertTrue(((Objectives.Reach) made.objectives.get(0)).place().found().equals(Optional.of(spot)), "found: " + made.objectives.get(0));
		helper.assertTrue(((Rewards.MapReward) made.rewards.get(0)).place().found().equals(Optional.of(spot)),
			"the map's place: " + made.rewards);
		long withdrawn = entity(helper).chronicle().stream().filter(c -> key(c.text()).equals("chronicle.aliveworkplace.quest_withdrawn")
			&& c.text().getString().contains("Find the Lost Tower")).count();
		helper.assertTrue(withdrawn == 1, "chronicle lines for the withdrawn quest: " + withdrawn);
		String said = entity(helper).chronicle().stream().filter(c -> key(c.text()).equals("chronicle.aliveworkplace.quest_withdrawn"))
			.map(c -> c.text().getString()).findFirst().orElse("");
		helper.assertTrue(said.equals("The board took down “Find the Lost Tower”: nobody knew the way"), "the line: " + said);
		helper.succeed();
	}

	/** A biome place: looked up once for the objective and the map together, near the hall; the map's red X sits on it and it's named "Map to the ..."; a list of places falls back to the next option. */
	//$ gametest_ticks_batch AREA '200' '"qaBiomeMap"'
	@GameTest(template = AREA, timeoutTicks = 200, batch = "qaBiomeMap")
	public void aBiomeMapMarksTheFoundPlace(GameTestHelper helper) {
		Leftovers.clear(helper);
		ServerLevel level = helper.getLevel();
		BlockPos hall = hall(helper);
		ResourceLocation biome = level.getBiome(hall).unwrapKey().orElseThrow().location();
		Places.Place place = Places.read(com.google.gson.JsonParser.parseString("{\"biome\": \"" + biome + "\"}"));
		Quest quest = quest(level, "biome", List.of(new Objectives.Reach(place, 8)), List.of(new Rewards.MapReward(place)));
		int before = Places.LOOKUPS.get();
		Quest located = Stories.locate(level, hall, quest);
		helper.assertTrue(located != null && Places.LOOKUPS.get() - before == 1, "lookups: " + (Places.LOOKUPS.get() - before));
		BlockPos found = ((Objectives.Reach) located.objectives.get(0)).place().found().orElseThrow();
		Places.Place paid = ((Rewards.MapReward) located.rewards.get(0)).place();
		helper.assertTrue(paid.found().equals(Optional.of(found)), "the map and the objective disagree: " + paid + " vs " + found);
		double dx = found.getX() - hall.getX();
		double dz = found.getZ() - hall.getZ();
		helper.assertTrue(dx * dx + dz * dz <= 64 * 64 && level.getBiome(found).is(net.minecraft.resources.ResourceKey.create(net.minecraft.core.registries.Registries.BIOME, biome)),
			"found " + found + " for a biome at the hall " + hall);
		ItemStack map = Places.map(level, paid);
		MapDecorations marks = map.get(DataComponents.MAP_DECORATIONS);
		helper.assertTrue(marks != null && marks.decorations().size() == 1, "marks: " + marks);
		MapDecorations.Entry mark = marks.decorations().values().iterator().next();
		helper.assertTrue(mark.x() == found.getX() && mark.z() == found.getZ() && mark.type().equals(MapDecorationTypes.RED_X), "mark: " + mark);
		String name = map.getHoverName().getString();
		helper.assertTrue(name.startsWith("Map to the ") && !name.contains("biome.") && !name.contains("%"), "the map's name: " + name);
		helper.assertTrue(lore(QuestJournal.questIcon(level, located, player(helper, new CopyOnWriteArrayList<>()))).stream()
			.anyMatch(l -> l.startsWith("Reward: a map to the ")), "the journal doesn't show the map reward");

		// Several options: the first found is taken.
		BlockPos spot = helper.absolutePos(new BlockPos(2, 2, 2));
		Places.Place options = Places.read(com.google.gson.JsonParser.parseString("[{\"structure\": \"#aliveworkplace:no_such_places\"}, {\"point\": ["
			+ spot.getX() + ", " + spot.getY() + ", " + spot.getZ() + "]}]"));
		Places.Place second = Places.locate(level, hall, options);
		helper.assertTrue(second != null && second.found().equals(Optional.of(spot)) && second.option() == 1, "the next option: " + second);
		helper.assertTrue(Places.map(level, second).getHoverName().getString().equals("Map to the marked place"),
			"the point map's name: " + Places.map(level, second).getHoverName().getString());
		helper.succeed();
	}

	/** Every sentence of the journal, the bar, the chat list and the messages renders from en_us.json with its arguments in place, in American English. */
	//$ gametest_ticks_batch AREA '60' '"qaJournalWords"'
	@GameTest(template = AREA, timeoutTicks = 60, batch = "qaJournalWords")
	public void everyJournalSentenceRenders(GameTestHelper helper) {
		Leftovers.clear(helper);
		ServerLevel level = helper.getLevel();
		BlockPos hall = hall(helper);
		BlockPos spot = helper.absolutePos(new BlockPos(3, 2, 3));
		List<Component> seen = new CopyOnWriteArrayList<>();
		ServerPlayer me = player(helper, seen);
		long now = level.getGameTime();
		Quest named = new Quest(UUID.randomUUID(), AliveWorkplace.id("qa/words"), "hall", Optional.of(Component.literal("The Long Walk")), "Ana", now, now + DAY,
			List.of(new Objectives.Reach(Places.point(spot), 4), new Objectives.Wait(1), new Objectives.Wait(2)), new int[3],
			List.of(new Rewards.Money_(1, 0, 0, false), new Rewards.ItemReward(ResourceLocation.withDefaultNamespace("cake"), 2), new Rewards.MapReward(Places.point(spot))));
		named.progress[0] = 1;
		Quest bring = quest(level, "words_bring", List.of(new Objectives.Bring("minecraft:apple", 5, "hall", Optional.empty())), List.of(new Rewards.Money_(4, 0, 0, false)));
		Quest kill = quest(level, "words_kill", List.of(new Objectives.Kill("minecraft:zombie", 1, false)), List.of());
		Stories.post(level, hall, named);
		Stories.post(level, hall, bring);
		Stories.post(level, hall, kill);

		List<String> all = new ArrayList<>();
		ChoiceMenu menu = openJournal(helper, me);
		for (int slot = 0; slot < ChoiceMenu.SIZE; slot++) {
			ItemStack icon = menu.icon(slot);
			if (!icon.isEmpty()) {
				all.add(icon.getHoverName().getString());
				all.addAll(lore(icon));
			}
		}
		List<String> namedLore = lore(QuestJournal.questIcon(level, named, me));
		helper.assertTrue(namedLore.contains("✔ Go to the marked place") && namedLore.contains("Wait 1 day (0/1)") && namedLore.contains("Wait 2 days (0/2)"),
			"the objectives: " + namedLore);
		helper.assertTrue(namedLore.contains("Reward: 1 emerald") && namedLore.contains("Reward: 2 × Cake") && namedLore.contains("Reward: a map to the marked place")
			&& namedLore.contains("Up for 1 more day"), "rewards and days: " + namedLore);
		List<String> bringLore = lore(QuestJournal.questIcon(level, bring, me));
		helper.assertTrue(bringLore.contains("You have none on you"), "bring with none: " + bringLore);
		click(menu, VillageHallScreen.QUEST_SLOTS[1], me); // the bring with nothing on you
		helper.assertTrue(texts(seen).stream().anyMatch(s -> s.startsWith("You have no ") && s.contains("Apple")), "nothing to hand in: " + texts(seen));

		QuestTracker.track(me, level, hall, named.id);
		String bar = QuestTracker.bar(me).getName().getString();
		helper.assertTrue(bar.equals("The Long Walk: Wait 1 day (0/1)"), "the named bar: " + bar);
		QuestTracker.track(me, level, hall, kill.id);
		String one = QuestTracker.bar(me).getName().getString();
		helper.assertTrue(one.equals(kill.objectives.get(0).line().getString() + " (0/1)"), "the one-objective bar: " + one);
		all.add(bar);
		all.add(one);
		for (Component c : QuestJournal.chatList(me)) {
			all.add(c.getString());
		}
		all.addAll(texts(seen));
		for (String key : List.of("command.aliveworkplace.quests.track_hover", "command.aliveworkplace.quests.untrack_hover", "command.aliveworkplace.quests.off",
			"command.aliveworkplace.quests.none", "command.aliveworkplace.quests.untracked", "screen.aliveworkplace.journal.not_yet",
			"screen.aliveworkplace.journal.empty", "screen.aliveworkplace.journal.about", "message.aliveworkplace.quest.done_plain")) {
			all.add(Component.translatable(key, "X").getString());
		}
		all.add(Component.translatable("message.aliveworkplace.quest.posted", VillageHalls.name(level, hall), kill.title()).getString());
		all.add(Component.translatable("chronicle.aliveworkplace.quest_withdrawn", "X").getString());
		all.add(Component.translatable("item.aliveworkplace.quest_map", Places.name(Places.point(spot))).getString());
		for (String s : all) {
			helper.assertTrue(!s.contains("aliveworkplace.") && !s.contains("%s") && !s.contains("%1$"), "untranslated or argument left: " + s);
			helper.assertTrue(!BRITISH.matcher(s).find(), "not American English: " + s);
		}
		me.closeContainer();
		helper.succeed();
	}
}
