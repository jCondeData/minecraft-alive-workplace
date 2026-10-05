package io.github.jcondedata.aliveworkplace.test;

import io.github.jcondedata.aliveworkplace.AliveWorkplace;
import io.github.jcondedata.aliveworkplace.hall.Chronicle;
import io.github.jcondedata.aliveworkplace.hall.VillageHallBlockEntity;
import io.github.jcondedata.aliveworkplace.hall.VillageHallScreen;
import io.github.jcondedata.aliveworkplace.hall.VillageHalls;
import io.github.jcondedata.aliveworkplace.hall.VillageQuests;
import io.github.jcondedata.aliveworkplace.registry.ModBlocks;
import io.github.jcondedata.aliveworkplace.story.Objectives;
import io.github.jcondedata.aliveworkplace.story.Quest;
import io.github.jcondedata.aliveworkplace.story.QuestFiles;
import io.github.jcondedata.aliveworkplace.story.Stories;
import io.github.jcondedata.aliveworkplace.work.ChoiceMenu;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;

/** The quest engine (ROADMAP 31.2): quest files, the saved open quests, old hall quests moving in, the switch. */
public class QuestEngineGameTests implements FabricGameTest {
	private static final String AREA = "aliveworkplace_test:big_area";
	private static final BlockPos HALL = new BlockPos(9, 2, 9);
	private static final ResourceLocation APPLES = ResourceLocation.fromNamespaceAndPath("aliveworkplace_test", "test/apples");
	private static final ResourceLocation KEY = ResourceLocation.fromNamespaceAndPath("aliveworkplace_test", "test/key");

	private static VillageHallBlockEntity hall(GameTestHelper helper) {
		int radius = VillageHalls.RADIUS;
		VillageHalls.RADIUS = 16;
		Leftovers.after(helper, () -> VillageHalls.RADIUS = radius);
		helper.setBlock(HALL, ModBlocks.VILLAGE_HALL);
		VillageHallBlockEntity entity = (VillageHallBlockEntity) helper.getLevel().getBlockEntity(helper.absolutePos(HALL));
		entity.setLastQuestDay(Long.MAX_VALUE / 2); // the hall's own rounds post nothing; the tests open the morning themselves
		return entity;
	}

	private static String oldQuest(String kind, String item, int count, int progress, int reward, long posted, String extra) {
		return "{id:" + uuidNbt(UUID.randomUUID()) + ",kind:\"" + kind + "\",item:\"" + item + "\",count:" + count + ",progress:" + progress
			+ ",reward:" + reward + ",posted:" + posted + "L,poster:\"Ana\"" + extra + "}";
	}

	private static String uuidNbt(UUID id) {
		long m = id.getMostSignificantBits();
		long l = id.getLeastSignificantBits();
		return "[I;" + (int) (m >> 32) + "," + (int) m + "," + (int) (l >> 32) + "," + (int) l + "]";
	}

	/** A hall saved before 1.5 with three old quests: its round moves them into the engine with their progress, once. */
	//$ gametest_ticks_batch AREA '60' '"oldQuestsMoveIntoTheEngine"'
	@GameTest(template = AREA, timeoutTicks = 60, batch = "oldQuestsMoveIntoTheEngine")
	public void oldQuestsMoveIntoTheEngine(GameTestHelper helper) throws Exception {
		Leftovers.clear(helper);
		ServerLevel level = helper.getLevel();
		VillageHallBlockEntity entity = hall(helper);
		BlockPos hall = helper.absolutePos(HALL);
		BlockPos station = hall.east(3);
		long now = level.getGameTime();
		CompoundTag tag = entity.saveWithFullMetadata(level.registryAccess());
		ListTag quests = (ListTag) net.minecraft.nbt.TagParser.parseTag("{q:[" + String.join(",",
			oldQuest("BRING", "minecraft:iron_ingot", 8, 3, 6, now - 100, ""),
			oldQuest("SLAY", "minecraft:air", 8, 5, 7, now - 200, ""),
			oldQuest("BRING", "minecraft:wheat", 20, 4, 4, now - 300, ",deliver_to:[I;" + station.getX() + "," + station.getY() + "," + station.getZ() + "]")) + "]}").get("q");
		tag.put("quests", quests);
		tag.putLong("lastQuestDay", Long.MAX_VALUE / 2); // no new quest this morning
		entity.loadWithComponents(tag, level.registryAccess());
		helper.assertTrue(entity.quests().size() == 3, "the old field reads: " + entity.quests());
		List<UUID> ids = entity.quests().stream().map(VillageQuests.Quest::id).toList();
		List<VillageQuests.Quest> before = List.copyOf(entity.quests());

		VillageQuests.tick(level, hall, entity);
		helper.assertTrue(entity.quests().isEmpty(), "still on the hall: " + entity.quests());
		List<Quest> open = Stories.open(level, hall);
		helper.assertTrue(open.size() == 3 && open.stream().map(q -> q.id).toList().equals(ids), "moved: " + open);
		Quest iron = open.get(0);
		Quest slay = open.get(1);
		Quest wheat = open.get(2);
		helper.assertTrue(iron.file.equals(AliveWorkplace.id("daily/want_iron_ingot")) && iron.progress[0] == 3 && iron.emeralds() == 6
			&& iron.due == now - 100 + VillageQuests.LASTS && iron.poster.equals("Ana"), "iron: " + iron);
		helper.assertTrue(slay.file.equals(AliveWorkplace.id("daily/slay")) && slay.progress[0] == 5 && slay.emeralds() == 7
			&& slay.objectives.get(0) instanceof Objectives.Kill k && k.entity().equals("monster") && !k.anywhere(), "slay: " + slay);
		helper.assertTrue(wheat.file.equals(AliveWorkplace.id("daily/worker_request")) && wheat.progress[0] == 4
			&& wheat.objectives.get(0) instanceof Objectives.Bring b && b.station().equals(Optional.of(station)), "wheat: " + wheat);
		// The page shows them as before.
		helper.assertTrue(VillageQuests.open(level, hall).equals(before), "the page: " + VillageQuests.open(level, hall) + " vs " + before);

		// The server stopped before the hall's chunk was written: the old list comes back, nothing moves twice.
		entity.setQuests(before);
		Stories.migrate(level, hall, entity);
		helper.assertTrue(Stories.open(level, hall).size() == 3 && entity.quests().isEmpty(), "moved twice: " + Stories.open(level, hall));

		// Saved and loaded with the dimension.
		CompoundTag saved = Stories.Data.get(level).save(new CompoundTag(), level.registryAccess());
		Stories.Data copy = Stories.Data.load(saved, level.registryAccess());
		CompoundTag again = copy.save(new CompoundTag(), level.registryAccess());
		helper.assertTrue(saved.equals(again), "reloaded differs:\n" + saved + "\n" + again);

		// A kill in the village counts on the moved clearing-out quest.
		ServerPlayer player = helper.makeMockServerPlayerInLevel();
		player.setGameMode(net.minecraft.world.level.GameType.SURVIVAL);
		var zombie = helper.spawn(EntityType.ZOMBIE, new BlockPos(5, 2, 5));
		zombie.hurt(level.damageSources().playerAttack(player), 1000f);
		helper.assertTrue(slay.progress[0] == 6, "the kill: " + slay);
		helper.succeed();
	}

	/** A quest file from the test pack goes up in the morning, is handed in from the hall's page, then finished by a kill. */
	//$ gametest_ticks_batch AREA '80' '"aQuestFileIsPostedAndDone"'
	@GameTest(template = AREA, timeoutTicks = 80, batch = "aQuestFileIsPostedAndDone")
	public void aQuestFileIsPostedAndDone(GameTestHelper helper) {
		Leftovers.clear(helper);
		ServerLevel level = helper.getLevel();
		VillageHallBlockEntity entity = hall(helper);
		BlockPos hall = helper.absolutePos(HALL);
		helper.setBlock(new BlockPos(11, 2, 13), ModBlocks.STOREHOUSE);
		helper.setBlock(new BlockPos(11, 2, 14), Blocks.CHEST);
		helper.assertTrue(QuestFiles.get(APPLES).isPresent(), "the test file didn't load: " + QuestFiles.all().stream().map(QuestFiles.QuestFile::id).toList());
		ServerPlayer player = helper.makeMockServerPlayerInLevel();
		player.setGameMode(net.minecraft.world.level.GameType.SURVIVAL);
		player.getInventory().add(new ItemStack(Items.APPLE, 5));
		helper.runAfterDelay(3, () -> {
			// Only once the key quest is done in this village (quest_done) does the file's priority win the morning.
			Stories.Data.get(level).noteFinished(hall, KEY);
			Quest made = Stories.make(level, hall, 1f, RandomSource.create(1));
			helper.assertTrue(made != null && made.file.equals(APPLES), "chosen: " + made);
			long dayTime = level.getDayTime();
			Leftovers.after(helper, () -> level.setDayTime(dayTime));
			level.setDayTime((dayTime / 24000) * 24000 + 1000);
			entity.setLastQuestDay(-1);
			VillageQuests.tick(level, hall, entity);
			List<Quest> open = Stories.open(level, hall);
			helper.assertTrue(open.size() == 1 && open.get(0).file.equals(APPLES) && open.get(0).due == open.get(0).posted + 2 * 24000, "posted: " + open);
			Quest quest = open.get(0);

			// Hand in at the hall's quest page.
			ChoiceMenu menu = ChoiceMenu.detached(player, m -> VillageHallScreen.renderQuests(m, level, hall, player));
			helper.assertTrue(menu.icon(VillageHallScreen.QUEST_SLOTS[0]).is(Items.APPLE), "not on the page: " + menu.icon(VillageHallScreen.QUEST_SLOTS[0]));
			menu.press(VillageHallScreen.QUEST_SLOTS[0], player);
			helper.assertTrue(player.getInventory().countItem(Items.APPLE) == 2 && quest.progress[0] == 3 && !quest.done(), "handed in: " + quest);
			int treasury = entity.treasury();

			// A zombie anywhere finishes it.
			var zombie = helper.spawn(EntityType.ZOMBIE, new BlockPos(3, 2, 3));
			zombie.hurt(level.damageSources().playerAttack(player), 1000f);
			helper.assertTrue(Stories.open(level, hall).isEmpty(), "still open: " + Stories.open(level, hall));
			helper.assertTrue(player.getInventory().countItem(Items.EMERALD) == 2, "emeralds: " + player.getInventory().countItem(Items.EMERALD));
			helper.assertTrue(player.getInventory().countItem(Items.CAKE) == 1, "cake");
			helper.assertTrue(entity.treasury() == treasury + 500, "treasury: " + entity.treasury());
			helper.assertTrue(entity.questsDone() == 1, "done count");
			helper.assertTrue(entity.chronicle().stream().anyMatch(c -> c.kind() == Chronicle.Kind.FESTIVAL), "chronicle: " + entity.chronicle());
			helper.assertTrue(Stories.finished(level, hall, APPLES), "finished not noted");

			// Not repeatable: the same player can't do it again here.
			level.setDayTime((dayTime / 24000 + 1) * 24000 + 1000);
			VillageQuests.tick(level, hall, entity);
			Quest second = Stories.open(level, hall).stream().filter(q -> q.file.equals(APPLES)).findFirst().orElse(null);
			helper.assertTrue(second != null, "not posted again: " + Stories.open(level, hall));
			helper.assertTrue(VillageQuests.handIn(player, hall, second.id) == 0 && player.getInventory().countItem(Items.APPLE) == 2,
				"took apples for a one-time quest twice");
			helper.succeed();
		});
	}

	/** A broken quest file is skipped (the others load); a changed file is read again on reload. */
	//$ gametest 'FabricGameTest.EMPTY_STRUCTURE'
	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
	public void aReloadReadsTheQuestFilesAgain(GameTestHelper helper) {
		var manager = helper.getLevel().getServer().getResourceManager();
		Map<ResourceLocation, Resource> real = manager.listResources(QuestFiles.FOLDER, p -> p.getPath().endsWith(".json"));
		ResourceLocation path = AliveWorkplace.id("quests/daily/want_torch.json");
		Resource ours = real.get(path);
		helper.assertTrue(ours != null, "no " + path + " in " + real.keySet());
		int count = QuestFiles.all().size();
		Map<ResourceLocation, Resource> withPack = new HashMap<>(real);
		withPack.put(path, new Resource(ours.source(), () -> new ByteArrayInputStream(("{\"giver\": \"hall\", \"objectives\": [{\"type\": \"bring\","
			+ " \"item\": \"minecraft:torch\", \"count\": 64}], \"rewards\": [{\"type\": \"money\", \"emeralds\": 9}]}").getBytes(StandardCharsets.UTF_8))));
		withPack.put(ResourceLocation.fromNamespaceAndPath("aliveworkplace_test", "quests/test/broken.json"), new Resource(ours.source(),
			() -> new ByteArrayInputStream("{\"giver\": \"hall\", \"objectives\": [{\"type\": \"brng\"}]}".getBytes(StandardCharsets.UTF_8))));
		withPack.put(AliveWorkplace.id("quests/daily/want_coal.json"), new Resource(ours.source(),
			() -> new ByteArrayInputStream("{\"enabled\": false}".getBytes(StandardCharsets.UTF_8))));
		try {
			QuestFiles.load(withPack);
			QuestFiles.QuestFile torch = QuestFiles.get(AliveWorkplace.id("daily/want_torch")).orElseThrow();
			helper.assertTrue(torch.objectives().get(0).need() == 64, "not read again: " + torch);
			helper.assertTrue(QuestFiles.get(ResourceLocation.fromNamespaceAndPath("aliveworkplace_test", "test/broken")).isEmpty(), "a broken file loaded");
			helper.assertTrue(QuestFiles.get(AliveWorkplace.id("daily/want_coal")).isEmpty(), "switched off but loaded");
			helper.assertTrue(QuestFiles.all().size() == count - 1, "the others: " + QuestFiles.all().size() + " of " + count);
		} finally {
			QuestFiles.load(real);
		}
		helper.assertTrue(QuestFiles.get(AliveWorkplace.id("daily/want_torch")).orElseThrow().objectives().get(0).need() == 32, "back");
		// Bad fields name themselves.
		for (String[] bad : new String[][] {{"{\"giver\": \"hal\", \"objectives\": []}", "giver"},
			{"{\"giver\": \"hall\", \"objectives\": [{\"type\": \"bring\", \"item\": \"minecraft:nope\", \"count\": 1}]}", "nope"},
			{"{\"giver\": \"hall\", \"conditions\": [{\"type\": \"food_belo\"}], \"objectives\": [{\"type\": \"wait\", \"days\": 1}]}", "food_belo"},
			{"{\"giver\": \"hall\", \"objectives\": [{\"type\": \"kill\", \"entity\": \"minecraft:zombie\", \"count\": 0}]}", "count"},
			{"{\"giver\": \"hall\", \"objectives\": [{\"type\": \"wait\", \"days\": 1}], \"rewards\": [{\"type\": \"cash\"}]}", "cash"}}) {
			try {
				QuestFiles.read(AliveWorkplace.id("x"), com.google.gson.JsonParser.parseString(bad[0]).getAsJsonObject());
				helper.fail("read: " + bad[0]);
			} catch (IllegalArgumentException e) {
				helper.assertTrue(e.getMessage().contains(bad[1]), "the message for " + bad[0] + ": " + e.getMessage());
			}
		}
		helper.succeed();
	}

	/** {@code villageQuests} off: no quest goes up in the morning; an open one can still be handed in. */
	//$ gametest_ticks_batch AREA '60' '"questsSwitchedOff"'
	@GameTest(template = AREA, timeoutTicks = 60, batch = "questsSwitchedOff")
	public void questsSwitchedOff(GameTestHelper helper) {
		Leftovers.clear(helper);
		ServerLevel level = helper.getLevel();
		VillageHallBlockEntity entity = hall(helper);
		BlockPos hall = helper.absolutePos(HALL);
		ServerPlayer player = helper.makeMockServerPlayerInLevel();
		player.setGameMode(net.minecraft.world.level.GameType.SURVIVAL);
		player.getInventory().add(new ItemStack(Items.TORCH, 40));
		long dayTime = level.getDayTime();
		Leftovers.after(helper, () -> {
			Stories.ENABLED = true;
			level.setDayTime(dayTime);
		});
		helper.runAfterDelay(3, () -> {
			entity.setQuests(List.of(new VillageQuests.Quest(UUID.randomUUID(), VillageQuests.Kind.BRING, "minecraft:torch", 32, 0, 2,
				level.getGameTime(), "", Optional.empty())));
			Stories.ENABLED = false;
			level.setDayTime((dayTime / 24000) * 24000 + 1000);
			entity.setLastQuestDay(-1);
			VillageQuests.tick(level, hall, entity);
			helper.assertTrue(Stories.open(level, hall).size() == 1 && entity.lastQuestDay() == -1, "posted while off: " + Stories.open(level, hall));
			UUID id = Stories.open(level, hall).get(0).id;
			helper.assertTrue(VillageQuests.handIn(player, hall, id) == 32 && player.getInventory().countItem(Items.EMERALD) == 2,
				"hand-in while off: " + player.getInventory().countItem(Items.EMERALD));
			Stories.ENABLED = true;
			VillageQuests.tick(level, hall, entity);
			helper.assertTrue(Stories.open(level, hall).size() == 1, "on again, nothing posted: " + Stories.open(level, hall));
			helper.succeed();
		});
	}
}
