package io.github.jcondedata.aliveworkplace.test;

import io.github.jcondedata.aliveworkplace.AliveWorkplace;
import io.github.jcondedata.aliveworkplace.Expansions;
import io.github.jcondedata.aliveworkplace.hall.QuestJournal;
import io.github.jcondedata.aliveworkplace.hall.VillageHallBlockEntity;
import io.github.jcondedata.aliveworkplace.hall.VillageHallScreen;
import io.github.jcondedata.aliveworkplace.hall.VillageHalls;
import io.github.jcondedata.aliveworkplace.hall.VillageNeeds;
import io.github.jcondedata.aliveworkplace.registry.ModBlocks;
import io.github.jcondedata.aliveworkplace.story.Objectives;
import io.github.jcondedata.aliveworkplace.story.Places;
import io.github.jcondedata.aliveworkplace.story.Quest;
import io.github.jcondedata.aliveworkplace.story.QuestTracker;
import io.github.jcondedata.aliveworkplace.story.Rewards;
import io.github.jcondedata.aliveworkplace.story.Stories;
import io.github.jcondedata.aliveworkplace.work.ChoiceMenu;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.server.level.ServerBossEvent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.item.component.MapDecorations;

/** The quest journal, tracking and quest maps (ROADMAP 31.3). */
public class QuestJournalGameTests implements FabricGameTest {
	private static final String AREA = "aliveworkplace_test:big_area";
	private static final BlockPos HALL = new BlockPos(9, 2, 9);

	private static BlockPos hall(GameTestHelper helper) {
		int radius = VillageHalls.RADIUS;
		VillageHalls.RADIUS = 16;
		Leftovers.after(helper, () -> VillageHalls.RADIUS = radius);
		helper.setBlock(HALL, ModBlocks.VILLAGE_HALL);
		VillageHallBlockEntity entity = (VillageHallBlockEntity) helper.getLevel().getBlockEntity(helper.absolutePos(HALL));
		entity.setLastQuestDay(Long.MAX_VALUE / 2); // the hall's own rounds post nothing
		return helper.absolutePos(HALL);
	}

	private static Quest quest(ServerLevel level, List<Objectives.Objective> objectives, List<Rewards.Reward> rewards) {
		long now = level.getGameTime();
		return new Quest(UUID.randomUUID(), AliveWorkplace.id("daily/test_journal"), "hall", Optional.empty(), "Ana", now, now + 3 * VillageNeeds.DAY,
			objectives, new int[objectives.size()], rewards);
	}

	private static ServerPlayer player(GameTestHelper helper) {
		ServerPlayer player = helper.makeMockServerPlayerInLevel();
		player.setGameMode(GameType.SURVIVAL);
		Leftovers.after(helper, () -> QuestTracker.untrack(player));
		return player;
	}

	private static String key(Component c) {
		return c.getContents() instanceof TranslatableContents t ? t.getKey() : c.getString();
	}

	/** A tracked quest is a bar for that player only; it follows the kills, survives a save and goes when the quest ends. */
	//$ gametest_ticks_batch AREA '80' '"trackingShowsABarForThatPlayerOnly"'
	@GameTest(template = AREA, timeoutTicks = 80, batch = "trackingShowsABarForThatPlayerOnly")
	public void trackingShowsABarForThatPlayerOnly(GameTestHelper helper) {
		Leftovers.clear(helper);
		ServerLevel level = helper.getLevel();
		BlockPos hall = hall(helper);
		Quest quest = quest(level, List.of(new Objectives.Kill("minecraft:zombie", 2, false)), List.of());
		Stories.post(level, hall, quest);
		ServerPlayer me = player(helper);
		ServerPlayer other = player(helper);

		// Tracked from the journal: a plain click on a quest that isn't a hand-in tracks it.
		ChoiceMenu menu = ChoiceMenu.detached(me, m -> VillageHallScreen.renderQuests(m, level, hall, me));
		for (QuestJournal.Tab tab : QuestJournal.Tab.values()) {
			helper.assertTrue(!menu.icon(tab.slot).isEmpty(), "no tab " + tab);
		}
		helper.assertTrue(menu.icon(VillageHallScreen.QUEST_SLOTS[0]).is(Items.IRON_SWORD), "the quest: " + menu.icon(VillageHallScreen.QUEST_SLOTS[0]));
		menu.press(VillageHallScreen.QUEST_SLOTS[0], me);
		helper.assertTrue(QuestTracker.isTracked(me, quest.id), "the click didn't track it");
		ServerBossEvent bar = QuestTracker.bar(me);
		helper.assertTrue(bar != null && bar.getPlayers().contains(me) && !bar.getPlayers().contains(other) && bar.getPlayers().size() == 1,
			"the bar: " + (bar == null ? null : bar.getPlayers()));
		helper.assertTrue(QuestTracker.bar(other) == null, "the other player has a bar");
		helper.assertTrue(bar.getProgress() == 0f && key(bar.getName()).equals("bossbar.aliveworkplace.quest_one"), "fresh bar: " + bar.getName());

		// Saved and loaded with the overworld's stories.
		CompoundTag saved = Stories.Data.get(level).save(new CompoundTag(), level.registryAccess());
		CompoundTag again = Stories.Data.load(saved, level.registryAccess()).save(new CompoundTag(), level.registryAccess());
		helper.assertTrue(saved.equals(again) && saved.contains("tracked"), "tracking didn't reload: " + saved.get("tracked"));

		var zombie = helper.spawn(EntityType.ZOMBIE, new BlockPos(5, 2, 5));
		zombie.hurt(level.damageSources().playerAttack(me), 1000f);
		helper.assertTrue(quest.progress[0] == 1 && Math.abs(bar.getProgress() - 0.5f) < 0.01f, "after a kill: " + bar.getProgress());
		var second = helper.spawn(EntityType.ZOMBIE, new BlockPos(5, 2, 6));
		second.hurt(level.damageSources().playerAttack(me), 1000f);
		helper.assertTrue(QuestTracker.bar(me) == null && bar.getPlayers().isEmpty(), "the bar stayed after the quest ended");
		helper.assertTrue(!QuestTracker.isTracked(me, quest.id), "still tracked after it ended");
		helper.succeed();
	}

	/** The bar goes when the quest is removed under it and while the expansion gate is shut; a shift-click untracks. */
	//$ gametest_ticks_batch AREA '60' '"trackingEndsWhenTheQuestGoes"'
	@GameTest(template = AREA, timeoutTicks = 60, batch = "trackingEndsWhenTheQuestGoes")
	public void trackingEndsWhenTheQuestGoes(GameTestHelper helper) {
		Leftovers.clear(helper);
		ServerLevel level = helper.getLevel();
		BlockPos hall = hall(helper);
		Quest quest = quest(level, List.of(new Objectives.Wait(2)), List.of());
		Stories.post(level, hall, quest);
		ServerPlayer me = player(helper);
		QuestTracker.track(me, level, hall, quest.id);
		helper.assertTrue(QuestTracker.bar(me) != null, "no bar");

		// The gate shut: no bar, no tabs (today's page), and /workplace quests says it isn't available.
		boolean open = Expansions.openForTests;
		Expansions.openForTests = false;
		try {
			QuestTracker.refresh(me);
			helper.assertTrue(QuestTracker.bar(me) == null, "a bar with the gate shut");
			ChoiceMenu menu = ChoiceMenu.detached(me, m -> VillageHallScreen.renderQuests(m, level, hall, me));
			helper.assertTrue(menu.icon(QuestJournal.Tab.PERSONAL.slot).isEmpty(), "tabs with the gate shut");
		} finally {
			Expansions.openForTests = open;
		}
		QuestTracker.refresh(me);
		helper.assertTrue(QuestTracker.bar(me) != null && QuestTracker.isTracked(me, quest.id), "the bar didn't come back");

		// A shift-click always toggles Track; here it untracks, then tracks again.
		ChoiceMenu menu = ChoiceMenu.detached(me, m -> VillageHallScreen.renderQuests(m, level, hall, me));
		menu.clicked(VillageHallScreen.QUEST_SLOTS[0], 0, net.minecraft.world.inventory.ClickType.QUICK_MOVE, me);
		helper.assertTrue(!QuestTracker.isTracked(me, quest.id) && QuestTracker.bar(me) == null, "shift-click didn't untrack");
		QuestTracker.track(me, level, hall, quest.id);

		// /workplace quests lists it with its button.
		me.teleportTo(hall.getX() + 0.5, hall.getY(), hall.getZ() + 2.5);
		List<Component> lines = QuestJournal.chatList(me);
		helper.assertTrue(lines.size() == 2 && key(lines.get(1)).equals("command.aliveworkplace.quests.line"), "the list: " + lines);

		// The hall broken: its quests go, and so do the tracking and the bar.
		Stories.Data.get(level).remove(hall);
		QuestTracker.refresh(me);
		helper.assertTrue(QuestTracker.bar(me) == null && !QuestTracker.isTracked(me, quest.id), "the bar outlived its quest");
		helper.succeed();
	}

	/** {@code reach} counts inside its radius and not outside. */
	//$ gametest_ticks_batch AREA '60' '"reachCountsInsideItsRadius"'
	@GameTest(template = AREA, timeoutTicks = 60, batch = "reachCountsInsideItsRadius")
	public void reachCountsInsideItsRadius(GameTestHelper helper) {
		Leftovers.clear(helper);
		ServerLevel level = helper.getLevel();
		BlockPos hall = hall(helper);
		BlockPos spot = helper.absolutePos(new BlockPos(3, 2, 3));
		Quest quest = quest(level, List.of(new Objectives.Reach(Places.point(spot), 4), new Objectives.Wait(1)), List.of());
		Stories.post(level, hall, quest);
		ServerPlayer me = player(helper);
		me.teleportTo(spot.getX() + 6.5, spot.getY(), spot.getZ() + 0.5);
		QuestTracker.checkReach(me);
		helper.assertTrue(quest.progress[0] == 0, "counted 6 blocks out of a radius of 4");
		me.teleportTo(spot.getX() + 3.5, spot.getY(), spot.getZ() + 0.5);
		QuestTracker.checkReach(me);
		helper.assertTrue(quest.progress[0] == 1 && quest.current() == 1, "not counted 3 blocks in: " + quest);
		helper.succeed();
	}

	/** The place is looked up once when the quest opens; the map reward marks it; a place nowhere withdraws the quest. */
	//$ gametest_ticks_batch AREA '60' '"questMapsMarkTheFoundPlace"'
	@GameTest(template = AREA, timeoutTicks = 60, batch = "questMapsMarkTheFoundPlace")
	public void questMapsMarkTheFoundPlace(GameTestHelper helper) {
		Leftovers.clear(helper);
		ServerLevel level = helper.getLevel();
		BlockPos hall = hall(helper);
		BlockPos spot = helper.absolutePos(new BlockPos(12, 2, 4));
		com.google.gson.JsonObject point = com.google.gson.JsonParser.parseString(
			"{\"point\": [" + spot.getX() + ", " + spot.getY() + ", " + spot.getZ() + "]}").getAsJsonObject();
		Places.Place unfound = Places.read(point);
		Quest quest = quest(level, List.of(new Objectives.Reach(unfound, 8)), List.of(new Rewards.MapReward(unfound)));
		int before = Places.LOOKUPS.get();
		Quest located = Stories.locate(level, hall, quest);
		helper.assertTrue(located != null && Places.LOOKUPS.get() - before == 1, "lookups: " + (Places.LOOKUPS.get() - before));
		Quest again = Stories.locate(level, hall, located);
		helper.assertTrue(again == located && Places.LOOKUPS.get() - before == 1, "looked up again: " + (Places.LOOKUPS.get() - before));
		Objectives.Reach reach = (Objectives.Reach) located.objectives.get(0);
		helper.assertTrue(reach.place().found().equals(Optional.of(spot)), "found: " + reach.place());

		// Saved with the quest: the reloaded objective keeps its place without a lookup.
		Objectives.Objective reread = Objectives.parse(reach.json());
		helper.assertTrue(reread.equals(reach), "reloaded: " + reread + " vs " + reach);

		// The map's red X sits on the place.
		ItemStack map = Places.map(level, ((Rewards.MapReward) located.rewards.get(0)).place());
		MapDecorations marks = map.get(DataComponents.MAP_DECORATIONS);
		helper.assertTrue(marks != null && marks.decorations().size() == 1, "marks: " + marks);
		MapDecorations.Entry mark = marks.decorations().values().iterator().next();
		helper.assertTrue(mark.x() == spot.getX() && mark.z() == spot.getZ()
			&& mark.type().equals(net.minecraft.world.level.saveddata.maps.MapDecorationTypes.RED_X), "mark: " + mark);

		// Paid to the finisher when the quest is done.
		ServerPlayer me = player(helper);
		Stories.post(level, hall, located);
		me.teleportTo(spot.getX() + 0.5, spot.getY(), spot.getZ() + 0.5);
		QuestTracker.checkReach(me);
		helper.assertTrue(io.github.jcondedata.aliveworkplace.mc.Players.mainItems(me).stream().anyMatch(s -> s.is(Items.FILLED_MAP)), "no map paid");

		// A structure tag with nothing in it: no place, so the quest can't open.
		Places.Place nowhere = Places.read(com.google.gson.JsonParser.parseString("{\"structure\": \"#aliveworkplace:no_such_places\"}"));
		helper.assertTrue(Stories.locate(level, hall, quest(level, List.of(new Objectives.Reach(nowhere, 8)), List.of())) == null, "found nowhere");
		helper.succeed();
	}
}
