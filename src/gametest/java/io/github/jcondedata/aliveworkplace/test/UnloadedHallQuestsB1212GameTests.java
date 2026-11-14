package io.github.jcondedata.aliveworkplace.test;

import io.github.jcondedata.aliveworkplace.AliveWorkplace;
import io.github.jcondedata.aliveworkplace.hall.VillageHallBlockEntity;
import io.github.jcondedata.aliveworkplace.hall.VillageQuests;
import io.github.jcondedata.aliveworkplace.registry.ModBlocks;
import io.github.jcondedata.aliveworkplace.story.Objectives;
import io.github.jcondedata.aliveworkplace.story.PersonalRequests;
import io.github.jcondedata.aliveworkplace.story.Quest;
import io.github.jcondedata.aliveworkplace.story.RequestErrands;
import io.github.jcondedata.aliveworkplace.story.Stories;
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
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Blocks;

/**
 * Bug B1212 (bug lane, lane-a-1111-2122): a village hall whose chunk isn't loaded was loaded on the server thread every
 * 2 seconds, because the personal requests' level tick (RequestErrands.placeHeirlooms) asks Stories.open for every hall
 * with stories, and Stories.open looked the hall's block entity up to move pre-1.5 quests in. A quest check never loads
 * a chunk now: an unloaded hall's saved quests are listed as they are and its old ones move in once it loads.
 */
public class UnloadedHallQuestsB1212GameTests implements FabricGameTest {
	private static final String AREA = "aliveworkplace_test:big_area";

	/** A spot far from every test area and player, so its chunk is never loaded unless something loads it. */
	private static BlockPos far(GameTestHelper helper, int dx) {
		BlockPos origin = helper.absolutePos(BlockPos.ZERO);
		return new BlockPos(origin.getX() + dx, 70, origin.getZ() + 6000);
	}

	private static boolean loaded(ServerLevel level, BlockPos pos) {
		return level.getChunkSource().getChunkNow(pos.getX() >> 4, pos.getZ() >> 4) != null;
	}

	/** A villager's own request (31.9) to bring 3 bread, posted at {@code hall}. */
	private static Quest request(ServerLevel level, BlockPos hall) {
		long now = level.getGameTime();
		Quest q = new Quest(UUID.randomUUID(), AliveWorkplace.id("personal/b1212"), "villager", Optional.empty(), "Ana", now, now + 24000L * 3,
			List.of(new Objectives.Bring("minecraft:bread", 3, "hall", Optional.empty())), new int[1], List.of());
		q.villager = UUID.randomUUID();
		Stories.post(level, hall, q);
		return q;
	}

	private static String uuidNbt(UUID id) {
		long m = id.getMostSignificantBits();
		long l = id.getLeastSignificantBits();
		return "[I;" + (int) (m >> 32) + "," + (int) m + "," + (int) (l >> 32) + "," + (int) l + "]";
	}

	/**
	 * A hall with a posted request in a chunk that isn't loaded: the errand tick (placeHeirlooms), Stories.open and
	 * PersonalRequests.open list the request without loading the chunk, and two seconds of the level's own ticks later
	 * the chunk is still not loaded. Its stories are saved and read back unchanged.
	 */
	//$ gametest_ticks_batch AREA '100' '"b1212UnloadedHallStaysUnloaded"'
	@GameTest(template = AREA, timeoutTicks = 100, batch = "b1212UnloadedHallStaysUnloaded")
	public void anUnloadedHallIsNeverLoadedByAQuestCheck(GameTestHelper helper) {
		Leftovers.clear(helper);
		ServerLevel level = helper.getLevel();
		BlockPos hall = far(helper, 6000);
		Leftovers.after(helper, () -> Stories.Data.get(level).remove(hall));
		helper.assertTrue(!loaded(level, hall), "setup: the far hall's chunk is already loaded");
		Quest quest = request(level, hall);

		RequestErrands.placeHeirlooms(level);
		helper.assertTrue(!loaded(level, hall), "the errand tick loaded the hall's chunk");
		List<Quest> open = Stories.open(level, hall);
		helper.assertTrue(!loaded(level, hall), "Stories.open loaded the hall's chunk");
		helper.assertTrue(open.size() == 1 && open.get(0) == quest, "the unloaded hall's quests: " + open);
		helper.assertTrue(PersonalRequests.open(level, hall).equals(List.of(quest)), "the request isn't listed: " + PersonalRequests.open(level, hall));
		helper.assertTrue(Stories.halls(level).contains(hall), "the hall's stories were dropped");
		helper.assertTrue(PersonalRequests.accept(helper.makeMockServerPlayerInLevel(), UUID.randomUUID()) == false && !loaded(level, hall),
			"looking a request up by id loaded the hall's chunk");

		// Saved and loaded with the dimension: the unloaded hall's request is kept as it is.
		CompoundTag saved = Stories.Data.get(level).save(new CompoundTag(), level.registryAccess());
		CompoundTag again = Stories.Data.load(saved, level.registryAccess()).save(new CompoundTag(), level.registryAccess());
		helper.assertTrue(saved.equals(again), "reloaded differs:\n" + saved + "\n" + again);
		ListTag halls = saved.getList("halls", Tag.TAG_COMPOUND);
		boolean kept = false;
		for (int i = 0; i < halls.size(); i++) {
			CompoundTag h = halls.getCompound(i);
			kept |= h.getLong("hall") == hall.asLong() && h.getList("quests", Tag.TAG_COMPOUND).size() == 1;
		}
		helper.assertTrue(kept, "the far hall's request wasn't saved: " + halls);

		// The level's own ticks (the personal requests run every 40) don't load it either.
		helper.runAfterDelay(50, () -> {
			helper.assertTrue(!loaded(level, hall), "the level's ticks loaded the hall's chunk");
			helper.assertTrue(Stories.open(level, hall).equals(List.of(quest)), "the quest went: " + Stories.open(level, hall));
			helper.succeed();
		});
	}

	/**
	 * The same hall once its chunk loads: its pre-1.5 daily quests move into the engine on the first quest check, next to
	 * the request kept while it was unloaded, and only once.
	 */
	//$ gametest_ticks_batch AREA '60' '"b1212HallMigratesWhenLoaded"'
	@GameTest(template = AREA, timeoutTicks = 60, batch = "b1212HallMigratesWhenLoaded")
	public void oldQuestsMoveInOnceTheHallLoads(GameTestHelper helper) throws Exception {
		Leftovers.clear(helper);
		ServerLevel level = helper.getLevel();
		BlockPos hall = far(helper, -6000);
		Leftovers.after(helper, () -> {
			if (loaded(level, hall) && level.getBlockState(hall).is(ModBlocks.VILLAGE_HALL)) {
				level.setBlock(hall, Blocks.AIR.defaultBlockState(), 3);
			}
			Stories.Data.get(level).remove(hall);
		});
		helper.assertTrue(!loaded(level, hall), "setup: the far hall's chunk is already loaded");
		Quest quest = request(level, hall);
		helper.assertTrue(Stories.open(level, hall).equals(List.of(quest)) && !loaded(level, hall), "unloaded: " + Stories.open(level, hall));

		// The chunk loads, with the hall and its two old quests saved on it before 1.5.
		level.getChunk(hall);
		level.setBlock(hall, ModBlocks.VILLAGE_HALL.defaultBlockState(), 3);
		VillageHallBlockEntity entity = (VillageHallBlockEntity) level.getBlockEntity(hall);
		long now = level.getGameTime();
		UUID a = UUID.randomUUID();
		UUID b = UUID.randomUUID();
		CompoundTag tag = entity.saveWithFullMetadata(level.registryAccess());
		tag.put("quests", net.minecraft.nbt.TagParser.parseTag("{q:[{id:" + uuidNbt(a) + ",kind:\"BRING\",item:\"minecraft:iron_ingot\",count:8,progress:3,"
			+ "reward:6,posted:" + (now - 100) + "L,poster:\"Ana\"},{id:" + uuidNbt(b) + ",kind:\"BRING\",item:\"minecraft:wheat\",count:20,progress:4,"
			+ "reward:4,posted:" + (now - 200) + "L,poster:\"Ben\"}]}").get("q"));
		tag.putLong("lastQuestDay", Long.MAX_VALUE / 2); // no new quest this morning
		entity.loadWithComponents(tag, level.registryAccess());
		helper.assertTrue(entity.quests().size() == 2 && entity.quests().stream().allMatch(VillageQuests.Quest::daily), "setup: old quests: " + entity.quests());

		List<Quest> open = Stories.open(level, hall);
		helper.assertTrue(open.size() == 3 && open.get(0) == quest && open.get(1).id.equals(a) && open.get(1).progress[0] == 3
			&& open.get(2).id.equals(b) && open.get(2).progress[0] == 4, "moved in: " + open);
		helper.assertTrue(entity.quests().isEmpty(), "still on the hall: " + entity.quests());
		helper.assertTrue(Stories.open(level, hall).size() == 3, "moved twice: " + Stories.open(level, hall));
		helper.assertTrue(PersonalRequests.open(level, hall).equals(List.of(quest)), "the request: " + PersonalRequests.open(level, hall));

		// Saved and loaded with the dimension.
		CompoundTag saved = Stories.Data.get(level).save(new CompoundTag(), level.registryAccess());
		CompoundTag again = Stories.Data.load(saved, level.registryAccess()).save(new CompoundTag(), level.registryAccess());
		helper.assertTrue(saved.equals(again), "reloaded differs:\n" + saved + "\n" + again);
		level.setBlock(hall, Blocks.AIR.defaultBlockState(), 3); // (the far hall goes, with its stories)
		helper.succeed();
	}
}
