package io.github.jcondedata.aliveworkplace.test;

import io.github.jcondedata.aliveworkplace.registry.ModBlocks;
import io.github.jcondedata.aliveworkplace.store.Porters;
import io.github.jcondedata.aliveworkplace.work.KeepLoaded;
import io.github.jcondedata.aliveworkplace.work.WorkSites;
import java.util.Set;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.level.ChunkPos;

/**
 * 23.6: villages keep working when no player is near. While anyone is online, each worker's workstation chunk stays
 * loaded (remembered across restarts); {@code keepVillagesWorking: false} in the config pauses them instead.
 */
public class KeepVillagesWorkingGameTests implements net.fabricmc.fabric.api.gametest.v1.FabricGameTest {
	private static final String AREA = "aliveworkplace_test:build_area";
	private static final BlockPos STOREHOUSE = new BlockPos(3, 2, 3);

	/** A porter at its storehouse is noted within one round, and its chunk is among those kept loaded. */
	//$ gametest_ticks_batch AREA '400' '"keepVillages"'
	@GameTest(template = AREA, timeoutTicks = 400, batch = "keepVillages")
	public void aWorkersChunkIsKeptLoadedWhileSomeoneIsOnline(GameTestHelper helper) {
		Leftovers.clear(helper);
		villagesOn(helper, true);
		ServerLevel level = helper.getLevel();
		ServerPlayer player = online(helper);
		Villager porter = porter(helper);
		ChunkPos station = new ChunkPos(helper.absolutePos(STOREHOUSE));
		helper.succeedWhen(() -> {
			WorkSites.Site site = WorkSites.get(level).all().get(porter.getUUID());
			helper.assertTrue(site != null, "the porter wasn't noted");
			helper.assertTrue(site.station() == station.toLong(), "the porter's station chunk: " + new ChunkPos(site.station()) + ", not " + station);
			Set<ChunkPos> kept = KeepLoaded.chunksToKeep(level);
			helper.assertTrue(kept.contains(station), "the storehouse's chunk isn't kept: " + kept);
			helper.assertTrue(level.getServer().getPlayerList().getPlayers().contains(player), "the player left");
		});
	}

	/** With {@code keepVillagesWorking: false} the worker is still noted, but no chunk is kept for villages. */
	//$ gametest_ticks_batch AREA '400' '"keepVillagesOff"'
	@GameTest(template = AREA, timeoutTicks = 400, batch = "keepVillagesOff")
	public void withTheConfigOffVillagesArentKeptLoaded(GameTestHelper helper) {
		Leftovers.clear(helper);
		villagesOn(helper, false);
		ServerLevel level = helper.getLevel();
		online(helper);
		Villager porter = porter(helper);
		helper.succeedWhen(() -> {
			helper.assertTrue(WorkSites.get(level).all().containsKey(porter.getUUID()), "the porter wasn't noted");
			helper.assertTrue(KeepLoaded.villageChunks(level).isEmpty(), "chunks kept with the config off: " + KeepLoaded.villageChunks(level));
		});
	}

	/** A village far from everyone, known only from the saved list (as after a restart), gets its chunk loaded. */
	//$ gametest_ticks_batch AREA '400' '"keepVillagesFar"'
	@GameTest(template = AREA, timeoutTicks = 400, batch = "keepVillagesFar")
	public void aFarVillageIsLoadedFromTheSavedList(GameTestHelper helper) {
		villagesOn(helper, true);
		ServerLevel level = helper.getLevel();
		online(helper);
		ChunkPos here = new ChunkPos(helper.absolutePos(STOREHOUSE));
		ChunkPos far = new ChunkPos(here.x + 64, here.z + 64);
		helper.assertFalse(level.hasChunk(far.x, far.z), "the far chunk is loaded already");
		UUID worker = UUID.randomUUID();
		WorkSites.get(level).see(worker, far.toLong(), far.toLong(), level.getGameTime());
		Leftovers.after(helper, () -> WorkSites.get(level).forget(worker));
		helper.succeedWhen(() -> helper.assertTrue(level.hasChunk(far.x, far.z), "the far village's chunk wasn't loaded"));
	}

	/** The list survives a save and load, and a worker not seen for 2 minutes is forgotten. */
	//$ gametest 'FabricGameTest.EMPTY_STRUCTURE'
	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
	public void theListIsSavedAndForgetsWhoIsGone(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		WorkSites sites = new WorkSites();
		UUID stays = UUID.randomUUID();
		UUID gone = UUID.randomUUID();
		sites.see(stays, ChunkPos.asLong(10, -3), ChunkPos.asLong(11, -3), 5000);
		sites.see(gone, ChunkPos.asLong(-40, 7), ChunkPos.asLong(-40, 7), 1000);
		WorkSites back = sites.reloaded(level.registryAccess());
		helper.assertTrue(back.all().equals(sites.all()), "after a save and load: " + back.all() + ", not " + sites.all());
		back.forgetBefore(5000 - WorkSites.FORGET);
		helper.assertTrue(back.all().keySet().equals(Set.of(stays)), "after forgetting: " + back.all().keySet());
		Set<ChunkPos> chunks = KeepLoaded.villageChunks(level, back.all().values());
		helper.assertTrue(chunks.equals(Set.of(new ChunkPos(10, -3), new ChunkPos(11, -3))), "chunks: " + chunks);
		helper.succeed();
	}

	/** The cap: never more than {@link KeepLoaded#MAX_VILLAGE_CHUNKS} chunks for villages, however many workers. */
	//$ gametest 'FabricGameTest.EMPTY_STRUCTURE'
	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
	public void villagesNeverKeepMoreThanTheCap(GameTestHelper helper) {
		WorkSites sites = new WorkSites();
		for (int i = 0; i < KeepLoaded.MAX_VILLAGE_CHUNKS; i++) {
			sites.see(UUID.randomUUID(), ChunkPos.asLong(i, 0), ChunkPos.asLong(i, 1), 0);
		}
		int kept = KeepLoaded.villageChunks(helper.getLevel(), sites.all().values()).size();
		helper.assertTrue(kept <= KeepLoaded.MAX_VILLAGE_CHUNKS + 1, kept + " chunks kept");
		helper.succeed();
	}

	private static void villagesOn(GameTestHelper helper, boolean on) {
		boolean before = KeepLoaded.VILLAGES;
		KeepLoaded.VILLAGES = on;
		Leftovers.after(helper, () -> KeepLoaded.VILLAGES = before);
	}

	private static ServerPlayer online(GameTestHelper helper) {
		ServerPlayer player = helper.makeMockServerPlayerInLevel();
		Leftovers.after(helper, () -> player.connection.disconnect(Component.literal("test over")));
		return player;
	}

	private static Villager porter(GameTestHelper helper) {
		helper.setBlock(STOREHOUSE, ModBlocks.STOREHOUSE);
		Villager porter = helper.spawn(EntityType.VILLAGER, STOREHOUSE.south());
		Porters.employ(helper.getLevel(), porter, helper.absolutePos(STOREHOUSE));
		return porter;
	}
}
