package io.github.jcondedata.aliveworkplace.test;

import io.github.jcondedata.aliveworkplace.AliveWorkplace;
import io.github.jcondedata.aliveworkplace.hall.VillageHallBlockEntity;
import io.github.jcondedata.aliveworkplace.registry.ModBlocks;
import io.github.jcondedata.aliveworkplace.story.Objectives;
import io.github.jcondedata.aliveworkplace.story.Places;
import io.github.jcondedata.aliveworkplace.story.Quest;
import io.github.jcondedata.aliveworkplace.story.QuestFiles;
import io.github.jcondedata.aliveworkplace.story.QuestTracker;
import io.github.jcondedata.aliveworkplace.story.Rewards;
import io.github.jcondedata.aliveworkplace.story.Stories;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.locale.Language;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;

/**
 * Lane E QA (1114e, second pass), B1220 and B1223 checked from their decisions, not their code: a kill or a place reached
 * for a far village whose hall isn't loaded never loads it, still counts, and the finished quest is paid once, to its last
 * helper, at the hall's next round, also across a save; a hall broken meanwhile pays nothing; a player who may not help
 * (a one-time quest they already did) counts nothing and loads nothing.
 */
public class QaFarQuestsPaid1114e2GameTests implements FabricGameTest {
	private static final String AREA = "aliveworkplace_test:big_area";
	private static final String STORIES = "aliveworkplace_stories";
	private static final ResourceLocation DIAMOND = ResourceLocation.parse("minecraft:diamond");

	private static BlockPos far(GameTestHelper helper, int dx, int dz) {
		BlockPos origin = helper.absolutePos(BlockPos.ZERO);
		return new BlockPos(origin.getX() + dx, 70, origin.getZ() + dz);
	}

	private static boolean loaded(ServerLevel level, BlockPos pos) {
		return level.getChunkSource().getChunkNow(pos.getX() >> 4, pos.getZ() >> 4) != null;
	}

	/** After the test: the far hall goes (loading its chunk if a hall was put there) and so do its stories. */
	private static void cleanUp(GameTestHelper helper, ServerLevel level, BlockPos hall, boolean[] placed) {
		Leftovers.after(helper, () -> {
			if (placed[0] && level.getBlockState(hall).is(ModBlocks.VILLAGE_HALL)) {
				level.setBlock(hall, Blocks.AIR.defaultBlockState(), 3);
			}
			Stories.Data.get(level).remove(hall);
		});
	}

	/** A player at {@code at} (relative) who keeps every chat line shown to them. */
	private static ServerPlayer listener(GameTestHelper helper, String name, BlockPos at, List<Component> seen) {
		ServerLevel level = helper.getLevel();
		var cookie = net.minecraft.server.network.CommonListenerCookie.createInitial(new com.mojang.authlib.GameProfile(UUID.randomUUID(), name), false);
		ServerPlayer player = new ServerPlayer(level.getServer(), level, cookie.gameProfile(), cookie.clientInformation()) {
			@Override
			public void displayClientMessage(Component message, boolean overlay) {
				seen.add(message);
				super.displayClientMessage(message, overlay);
			}
		};
		net.minecraft.network.Connection connection = new net.minecraft.network.Connection(net.minecraft.network.protocol.PacketFlow.SERVERBOUND);
		new io.netty.channel.embedded.EmbeddedChannel(connection);
		level.getServer().getPlayerList().placeNewPlayer(connection, player, cookie);
		player.setGameMode(GameType.SURVIVAL);
		BlockPos abs = helper.absolutePos(at);
		player.teleportTo(level, abs.getX() + 0.5, abs.getY(), abs.getZ() + 0.5, 0f, 0f);
		Leftovers.after(helper, () -> level.getServer().getPlayerList().remove(player));
		return player;
	}

	private static Quest killQuest(ServerLevel level, int count, long due) {
		long now = level.getGameTime();
		return new Quest(UUID.randomUUID(), AliveWorkplace.id("personal/qa1114e2"), "hall", Optional.empty(), "Ana", now, due,
			List.of(new Objectives.Kill("minecraft:zombie", count, true)), new int[1], List.of(new Rewards.ItemReward(DIAMOND, 3)));
	}

	private static Quest reachQuest(ServerLevel level, ResourceLocation file, BlockPos place) {
		long now = level.getGameTime();
		return new Quest(UUID.randomUUID(), file, "hall", Optional.empty(), "Ana", now, now + 24000L * 3,
			List.of(new Objectives.Reach(Places.point(place), 3)), new int[1], List.of(new Rewards.ItemReward(DIAMOND, 3)));
	}

	/** {@code player} kills a zombie in the test area (far from every village). */
	private static void kill(GameTestHelper helper, ServerPlayer player) {
		Zombie zombie = helper.spawn(EntityType.ZOMBIE, new BlockPos(4, 2, 4));
		zombie.setNoAi(true);
		Stories.onKill(helper.getLevel(), zombie, player);
		zombie.discard();
	}

	/** The far hall's chunk loads with its hall, and the hall's round runs (no new quest this morning). */
	private static VillageHallBlockEntity loadAndRound(ServerLevel level, BlockPos hall, boolean[] placed) {
		level.getChunk(hall);
		placed[0] = true;
		if (!level.getBlockState(hall).is(ModBlocks.VILLAGE_HALL)) {
			level.setBlock(hall, ModBlocks.VILLAGE_HALL.defaultBlockState(), 3);
		}
		VillageHallBlockEntity entity = (VillageHallBlockEntity) level.getBlockEntity(hall);
		entity.setLastQuestDay(Long.MAX_VALUE / 2);
		Stories.round(level, hall, entity);
		return entity;
	}

	private static String key(Component c) {
		return c.getContents() instanceof TranslatableContents t ? t.getKey() : "";
	}

	/** The "Quest done" lines {@code seen} got. */
	private static List<Component> doneLines(List<Component> seen) {
		return seen.stream().filter(c -> key(c).startsWith("message.aliveworkplace.quest.done")).toList();
	}

	/** The chronicle's "did a quest" lines. */
	private static List<Component> chronicleLines(VillageHallBlockEntity entity) {
		return entity.chronicle().stream().map(e -> e.text()).filter(t -> key(t).equals("chronicle.aliveworkplace.quest")).toList();
	}

	private static Quest open(ServerLevel level, BlockPos hall, UUID id) {
		return Stories.open(level, hall).stream().filter(q -> q.id.equals(id)).findFirst().orElse(null);
	}

	/**
	 * A far quest needing two kills: both kills while the hall is unloaded count (a third doesn't go past 2), nothing is
	 * paid yet, and at the hall's round the killer is paid once (3 diamonds, one "Quest done" line in plain English, one
	 * chronicle line naming them); a second round pays nothing more.
	 */
	//$ gametest_ticks_batch AREA '60' '"qa1114e2TwoKillsPaidOnce"'
	@GameTest(template = AREA, timeoutTicks = 60, batch = "qa1114e2TwoKillsPaidOnce")
	public void twoKillsWhileTheHallIsUnloadedCountAndArePaidOnce(GameTestHelper helper) {
		Leftovers.clear(helper);
		ServerLevel level = helper.getLevel();
		BlockPos hall = far(helper, 7700, 7700);
		boolean[] placed = {false};
		cleanUp(helper, level, hall, placed);
		helper.assertTrue(!loaded(level, hall), "setup: the far hall is loaded");
		List<Component> seen = new ArrayList<>();
		ServerPlayer ana = listener(helper, "far-ana", new BlockPos(2, 2, 2), seen);
		Quest q = killQuest(level, 2, level.getGameTime() + 24000L * 3);
		Stories.post(level, hall, q);

		kill(helper, ana);
		helper.assertTrue(q.progress[0] == 1 && !q.done(), "the first kill: " + q.progress[0]);
		kill(helper, ana);
		helper.assertTrue(q.progress[0] == 2 && q.done(), "the second kill didn't count: " + q.progress[0]);
		kill(helper, ana);
		helper.assertTrue(q.progress[0] == 2, "a third kill went past the quest's count: " + q.progress[0]);
		helper.assertTrue(!loaded(level, hall), "a kill loaded the unloaded hall's chunk");
		helper.assertTrue(ana.getInventory().countItem(Items.DIAMOND) == 0 && doneLines(seen).isEmpty(), "paid before the hall loaded");

		VillageHallBlockEntity entity = loadAndRound(level, hall, placed);
		helper.assertTrue(open(level, hall, q.id) == null, "the finished quest is still up after the round");
		helper.assertTrue(ana.getInventory().countItem(Items.DIAMOND) == 3, "the killer's reward: " + ana.getInventory().countItem(Items.DIAMOND) + " diamonds");
		List<Component> lines = doneLines(seen);
		helper.assertTrue(lines.size() == 1, "\"Quest done\" lines: " + lines.stream().map(Component::getString).toList());
		helper.assertTrue(Language.getInstance().has(key(lines.get(0))), "no English for " + key(lines.get(0)));
		helper.assertTrue(lines.get(0).getString().equals("Quest done: " + q.title().getString()), "the player read: " + lines.get(0).getString());
		List<Component> chronicle = chronicleLines(entity);
		helper.assertTrue(chronicle.size() == 1 && chronicle.get(0).getString().startsWith("far-ana did a quest: "),
			"the chronicle: " + chronicle.stream().map(Component::getString).toList());

		Stories.round(level, hall, entity);
		helper.assertTrue(ana.getInventory().countItem(Items.DIAMOND) == 3 && doneLines(seen).size() == 1 && chronicleLines(entity).size() == 1,
			"a second round paid again: " + ana.getInventory().countItem(Items.DIAMOND) + " diamonds");
		helper.succeed();
	}

	/**
	 * Two players each kill one of a far quest's two zombies while its hall is unloaded: the round pays the last helper
	 * (who finished it), not the first, and only once.
	 */
	//$ gametest_ticks_batch AREA '60' '"qa1114e2LastHelperPaid"'
	@GameTest(template = AREA, timeoutTicks = 60, batch = "qa1114e2LastHelperPaid")
	public void theLastHelperIsPaidNotTheFirst(GameTestHelper helper) {
		Leftovers.clear(helper);
		ServerLevel level = helper.getLevel();
		BlockPos hall = far(helper, -7700, 7700);
		boolean[] placed = {false};
		cleanUp(helper, level, hall, placed);
		helper.assertTrue(!loaded(level, hall), "setup: the far hall is loaded");
		List<Component> seenAna = new ArrayList<>();
		List<Component> seenBo = new ArrayList<>();
		ServerPlayer ana = listener(helper, "far-ana", new BlockPos(2, 2, 2), seenAna);
		ServerPlayer bo = listener(helper, "far-bo", new BlockPos(8, 2, 8), seenBo);
		Quest q = killQuest(level, 2, level.getGameTime() + 24000L * 3);
		Stories.post(level, hall, q);

		kill(helper, ana);
		kill(helper, bo);
		helper.assertTrue(q.done() && bo.getUUID().equals(q.last), "setup: the quest isn't finished by Bo: " + q.progress[0]);
		helper.assertTrue(!loaded(level, hall), "a kill loaded the unloaded hall's chunk");

		VillageHallBlockEntity entity = loadAndRound(level, hall, placed);
		helper.assertTrue(bo.getInventory().countItem(Items.DIAMOND) == 3 && doneLines(seenBo).size() == 1,
			"the last helper wasn't paid: " + bo.getInventory().countItem(Items.DIAMOND) + " diamonds");
		helper.assertTrue(ana.getInventory().countItem(Items.DIAMOND) == 0 && doneLines(seenAna).isEmpty(),
			"the first helper was paid a hall quest the last one finished");
		List<Component> chronicle = chronicleLines(entity);
		helper.assertTrue(chronicle.size() == 1 && chronicle.get(0).getString().startsWith("far-bo did a quest"),
			"the chronicle: " + chronicle.stream().map(Component::getString).toList());
		helper.succeed();
	}

	/**
	 * A kill finishes a far quest while its hall is unloaded, and the world is saved and read back (the quests' saved
	 * data, as on a restart) before anyone goes there: the quest is still full, still knows who finished it, and the
	 * hall's round pays that player exactly once.
	 */
	//$ gametest_ticks_batch AREA '60' '"qa1114e2KillSavedThenPaid"'
	@GameTest(template = AREA, timeoutTicks = 60, batch = "qa1114e2KillSavedThenPaid")
	public void aKillCountedWhileUnloadedSurvivesASaveAndIsPaidOnce(GameTestHelper helper) {
		Leftovers.clear(helper);
		ServerLevel level = helper.getLevel();
		BlockPos hall = far(helper, 7700, -7700);
		boolean[] placed = {false};
		cleanUp(helper, level, hall, placed);
		helper.assertTrue(!loaded(level, hall), "setup: the far hall is loaded");
		List<Component> seen = new ArrayList<>();
		ServerPlayer ana = listener(helper, "far-ana", new BlockPos(2, 2, 2), seen);
		Quest q = killQuest(level, 1, level.getGameTime() + 24000L * 3);
		Stories.post(level, hall, q);
		kill(helper, ana);
		helper.assertTrue(q.done(), "setup: the kill didn't count");

		var registries = level.registryAccess();
		CompoundTag saved = Stories.Data.get(level).save(new CompoundTag(), registries);
		level.getDataStorage().set(STORIES, Stories.Data.load(saved, registries));
		Quest back = open(level, hall, q.id);
		helper.assertTrue(back != null && back != q, "the saved quest didn't come back: " + Stories.open(level, hall));
		helper.assertTrue(back.done() && back.progress[0] == 1, "the kill was lost on the save: " + back.progress[0]);
		helper.assertTrue(ana.getUUID().equals(back.last), "who finished it was lost on the save: " + back.last);
		helper.assertTrue(!loaded(level, hall), "the save loaded the far hall's chunk");

		VillageHallBlockEntity entity = loadAndRound(level, hall, placed);
		helper.assertTrue(open(level, hall, q.id) == null, "the saved finished quest is still up after the round");
		helper.assertTrue(ana.getInventory().countItem(Items.DIAMOND) == 3 && doneLines(seen).size() == 1,
			"after the save the killer got " + ana.getInventory().countItem(Items.DIAMOND) + " diamonds, " + doneLines(seen).size() + " lines");
		Stories.round(level, hall, entity);
		helper.assertTrue(ana.getInventory().countItem(Items.DIAMOND) == 3, "paid twice: " + ana.getInventory().countItem(Items.DIAMOND));
		helper.assertTrue(Stories.finished(level, hall, q.file), "the quest isn't finished in the village");
		helper.succeed();
	}

	/**
	 * A kill finishes a far quest, and the quest's due time passes before anyone goes to the village: the round pays it
	 * (a finished quest waits to be paid), it doesn't take it down unpaid as overdue.
	 */
	//$ gametest_ticks_batch AREA '60' '"qa1114e2FinishedThenDue"'
	@GameTest(template = AREA, timeoutTicks = 60, batch = "qa1114e2FinishedThenDue")
	public void aFinishedQuestIsPaidThoughItFellDueBeforeTheHallLoaded(GameTestHelper helper) {
		Leftovers.clear(helper);
		ServerLevel level = helper.getLevel();
		BlockPos hall = far(helper, -7700, -7700);
		boolean[] placed = {false};
		cleanUp(helper, level, hall, placed);
		helper.assertTrue(!loaded(level, hall), "setup: the far hall is loaded");
		List<Component> seen = new ArrayList<>();
		ServerPlayer ana = listener(helper, "far-ana", new BlockPos(2, 2, 2), seen);
		Quest q = killQuest(level, 1, level.getGameTime() + 5);
		Stories.post(level, hall, q);
		kill(helper, ana);
		helper.assertTrue(q.done(), "setup: the kill didn't count");
		helper.runAfterDelay(10, () -> {
			helper.assertTrue(level.getGameTime() >= q.due, "setup: not due yet");
			loadAndRound(level, hall, placed);
			helper.assertTrue(open(level, hall, q.id) == null, "the quest is still up");
			helper.assertTrue(ana.getInventory().countItem(Items.DIAMOND) == 3 && doneLines(seen).size() == 1,
				"a quest finished in time but due before the hall loaded wasn't paid: " + ana.getInventory().countItem(Items.DIAMOND) + " diamonds");
			helper.succeed();
		});
	}

	/**
	 * A kill finishes a far quest, then the hall is broken before its round: the quest goes with it, nothing is paid, a
	 * later kill crashes nothing, and a new hall at the same spot doesn't pay the old quest.
	 */
	//$ gametest_ticks_batch AREA '60' '"qa1114e2HallBrokenUnpaid"'
	@GameTest(template = AREA, timeoutTicks = 60, batch = "qa1114e2HallBrokenUnpaid")
	public void aWaitingQuestWhoseHallIsBrokenIsNeverPaid(GameTestHelper helper) {
		Leftovers.clear(helper);
		ServerLevel level = helper.getLevel();
		BlockPos hall = far(helper, 7900, 7900);
		boolean[] placed = {false};
		cleanUp(helper, level, hall, placed);
		helper.assertTrue(!loaded(level, hall), "setup: the far hall is loaded");
		List<Component> seen = new ArrayList<>();
		ServerPlayer ana = listener(helper, "far-ana", new BlockPos(2, 2, 2), seen);
		Quest q = killQuest(level, 1, level.getGameTime() + 24000L * 3);
		Stories.post(level, hall, q);
		kill(helper, ana);
		helper.assertTrue(q.done(), "setup: the kill didn't count");

		level.getChunk(hall);
		placed[0] = true;
		level.setBlock(hall, ModBlocks.VILLAGE_HALL.defaultBlockState(), 3);
		level.setBlock(hall, Blocks.AIR.defaultBlockState(), 3); // broken before its round ran
		helper.assertTrue(Stories.open(level, hall).isEmpty(), "the broken hall's quest is still kept: " + Stories.open(level, hall));
		kill(helper, ana);
		loadAndRound(level, hall, placed); // a new hall at the same spot
		helper.assertTrue(ana.getInventory().countItem(Items.DIAMOND) == 0 && doneLines(seen).isEmpty(),
			"a quest of a broken hall was paid: " + ana.getInventory().countItem(Items.DIAMOND) + " diamonds");
		helper.assertTrue(!Stories.finished(level, hall, q.file), "the new hall counts the broken hall's quest as finished");
		helper.succeed();
	}

	/**
	 * B1223: a far quest's place to reach, with its hall unloaded. A player elsewhere counts nothing; the player standing
	 * there counts it (once, however many checks) without loading the hall, and the round pays them, not the other one.
	 */
	//$ gametest_ticks_batch AREA '60' '"qa1114e2ReachPaidLater"'
	@GameTest(template = AREA, timeoutTicks = 60, batch = "qa1114e2ReachPaidLater")
	public void aPlaceReachedWhileTheHallIsUnloadedIsPaidToWhoStoodThere(GameTestHelper helper) {
		Leftovers.clear(helper);
		ServerLevel level = helper.getLevel();
		BlockPos hall = far(helper, -7900, 7900);
		boolean[] placed = {false};
		cleanUp(helper, level, hall, placed);
		helper.assertTrue(!loaded(level, hall), "setup: the far hall is loaded");
		List<Component> seenAna = new ArrayList<>();
		List<Component> seenBo = new ArrayList<>();
		ServerPlayer ana = listener(helper, "far-ana", new BlockPos(2, 2, 2), seenAna);
		ServerPlayer bo = listener(helper, "far-bo", new BlockPos(14, 2, 14), seenBo);
		Quest q = reachQuest(level, AliveWorkplace.id("personal/qa1114e2"), ana.blockPosition());
		Stories.post(level, hall, q);
		Objectives.Reach reach = (Objectives.Reach) q.objectives.get(0);
		helper.assertTrue(reach.inside(ana.blockPosition()) && !reach.inside(bo.blockPosition()), "setup: who stands at the place");

		QuestTracker.checkReach(bo);
		helper.assertTrue(q.progress[0] == 0, "a player away from the place counted it");
		QuestTracker.checkReach(ana);
		QuestTracker.checkReach(ana);
		helper.assertTrue(q.progress[0] == 1 && q.done() && ana.getUUID().equals(q.last), "the place didn't count: " + q.progress[0]);
		helper.assertTrue(!loaded(level, hall), "the reach check loaded the unloaded hall's chunk");
		helper.assertTrue(ana.getInventory().countItem(Items.DIAMOND) == 0, "paid before the hall loaded");

		loadAndRound(level, hall, placed);
		helper.assertTrue(open(level, hall, q.id) == null, "the reached quest is still up after the round");
		helper.assertTrue(ana.getInventory().countItem(Items.DIAMOND) == 3 && doneLines(seenAna).size() == 1,
			"who stood there got " + ana.getInventory().countItem(Items.DIAMOND) + " diamonds");
		helper.assertTrue(bo.getInventory().countItem(Items.DIAMOND) == 0 && doneLines(seenBo).isEmpty(), "the other player was paid");
		helper.succeed();
	}

	/**
	 * B1223: a one-time quest, done by Ana at a far village (counted while its hall was unloaded, paid at its round). Once
	 * the hall's chunk has unloaded again, the same quest goes up there with a place Ana stands at: her reach checks count
	 * nothing and never load the hall; Bo, who hasn't done it, standing at the same place, counts it, still loading nothing.
	 */
	//$ gametest_ticks_batch AREA '600' '"qa1114e2OneTimeReach"'
	@GameTest(template = AREA, timeoutTicks = 600, batch = "qa1114e2OneTimeReach")
	public void aPlayerWhoDidAOneTimeQuestNeverLoadsTheHallAndCountsNothing(GameTestHelper helper) {
		Leftovers.clear(helper);
		ServerLevel level = helper.getLevel();
		BlockPos hall = far(helper, 7900, -7900);
		boolean[] placed = {false};
		cleanUp(helper, level, hall, placed);
		helper.assertTrue(!loaded(level, hall), "setup: the far hall is loaded");

		// A one-time quest file (the file only says it's one-time; the quests below carry their own place to reach).
		var manager = level.getServer().getResourceManager();
		Map<ResourceLocation, Resource> real = manager.listResources(QuestFiles.FOLDER, p -> p.getPath().endsWith(".json"));
		Resource any = real.values().iterator().next();
		Map<ResourceLocation, Resource> withOurs = new HashMap<>(real);
		String json = "{\"giver\": \"hall\", \"weight\": 0, \"repeatable\": false, \"objectives\": [{\"type\": \"bring\", \"item\": \"minecraft:apple\", \"count\": 1}]}";
		withOurs.put(AliveWorkplace.id(QuestFiles.FOLDER + "/qa1114e2/once.json"), new Resource(any.source(),
			() -> new java.io.ByteArrayInputStream(json.getBytes(java.nio.charset.StandardCharsets.UTF_8))));
		QuestFiles.load(withOurs);
		Leftovers.after(helper, () -> QuestFiles.load(real));
		ResourceLocation once = AliveWorkplace.id("qa1114e2/once");
		helper.assertTrue(QuestFiles.get(once).isPresent() && !QuestFiles.get(once).get().repeatable(), "setup: the one-time file didn't load");

		List<Component> seen = new ArrayList<>();
		ServerPlayer ana = listener(helper, "far-ana", new BlockPos(2, 2, 2), seen);
		ServerPlayer bo = listener(helper, "far-bo", new BlockPos(14, 2, 14), new ArrayList<>());
		BlockPos place = ana.blockPosition();
		Quest first = reachQuest(level, once, place);
		Stories.post(level, hall, first);
		QuestTracker.checkReach(ana);
		helper.assertTrue(first.done() && !loaded(level, hall), "setup: Ana's first time didn't count, or loaded the hall");
		loadAndRound(level, hall, placed);
		helper.assertTrue(ana.getInventory().countItem(Items.DIAMOND) == 3, "setup: Ana's first time wasn't paid");

		boolean[] posted = {false};
		helper.onEachTick(() -> {
			if (posted[0] || loaded(level, hall)) {
				return; // waiting for the hall's chunk to unload again
			}
			posted[0] = true;
			Quest again = reachQuest(level, once, place);
			Stories.post(level, hall, again);
			QuestTracker.checkReach(ana);
			QuestTracker.checkReach(ana);
			helper.assertTrue(!loaded(level, hall), "the reach check loaded the far hall for a player who already did its one-time quest");
			helper.assertTrue(again.progress[0] == 0, "a player who already did a one-time quest moved it on again");
			bo.teleportTo(level, ana.getX(), ana.getY(), ana.getZ(), 0f, 0f);
			helper.assertTrue(((Objectives.Reach) again.objectives.get(0)).inside(bo.blockPosition()), "setup: Bo isn't at the place");
			QuestTracker.checkReach(bo);
			helper.assertTrue(again.progress[0] == 1 && bo.getUUID().equals(again.last), "a player who may help didn't count the place: " + again.progress[0]);
			helper.assertTrue(!loaded(level, hall), "Bo's reach check loaded the far hall");
			helper.succeed();
		});
	}
}
