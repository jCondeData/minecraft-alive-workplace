package io.github.jcondedata.aliveworkplace.test;

import io.github.jcondedata.aliveworkplace.blueprint.BlueprintData;
import io.github.jcondedata.aliveworkplace.build.BuildSite;
import io.github.jcondedata.aliveworkplace.build.BuildSiteManager;
import io.github.jcondedata.aliveworkplace.build.Builders;
import io.github.jcondedata.aliveworkplace.registry.ModBlocks;
import io.github.jcondedata.aliveworkplace.work.KeepLoaded;
import java.util.Set;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;

/**
 * QA (lane-b-1103-0338, 2026-11-03) for workplaceKeepWorkLoaded (builder wiki: "Builds keep running while the ordering
 * player is online but far away"; KeepLoaded: "Nothing is kept loaded for players who are offline"), from both sides:
 * the build of an online owner keeps its chunk; a build queued behind it, one whose owner is offline, and the first one
 * once its owner leaves, keep nothing. The village rule (keepVillagesWorking) is off here so only builds count.
 */
public class QaKeepBuildsLoaded1103GameTests {
	private static final String AREA = "aliveworkplace_test:big_area";
	private static final ResourceLocation TEST_HUT = ResourceLocation.fromNamespaceAndPath("aliveworkplace_test", "test_hut");
	private static final BlockPos BENCH = new BlockPos(2, 2, 2);
	private static final BlockPos BENCH2 = new BlockPos(2, 2, 12);

	private static BlueprintData.Placement at(ServerLevel level, BlockPos abs) {
		return new BlueprintData.Placement(level.dimension().location(), abs, Rotation.NONE, Mirror.NONE);
	}

	//$ gametest_ticks_batch AREA '100' '"qa1103KeepBuildsLoaded"'
	@GameTest(template = AREA, timeoutTicks = 100, batch = "qa1103KeepBuildsLoaded")
	public void onlyTheRunningBuildOfAnOnlineOwnerIsKeptLoaded(GameTestHelper helper) {
		Leftovers.clear(helper);
		ServerLevel level = helper.getLevel();
		boolean villages = KeepLoaded.VILLAGES;
		KeepLoaded.VILLAGES = false;
		ServerPlayer owner = helper.makeMockServerPlayerInLevel();
		Leftovers.after(helper, () -> {
			KeepLoaded.VILLAGES = villages;
			if (level.getServer().getPlayerList().getPlayer(owner.getUUID()) != null) {
				owner.connection.disconnect(Component.literal("test over"));
			}
		});
		helper.setBlock(BENCH, ModBlocks.BUILDERS_BENCH);
		Villager builder = helper.spawn(EntityType.VILLAGER, BENCH.east());
		Builders.employ(level, builder, helper.absolutePos(BENCH));
		helper.setBlock(BENCH2, ModBlocks.BUILDERS_BENCH);
		Villager other = helper.spawn(EntityType.VILLAGER, BENCH2.east());
		Builders.employ(level, other, helper.absolutePos(BENCH2));

		BlockPos runningAt = helper.absolutePos(new BlockPos(8, 2, 8));
		BuildSite running = Builders.start(level, builder, owner, TEST_HUT, at(level, runningAt));
		helper.assertTrue(running != null && !running.isQueued(), "the first build didn't start");
		// queued behind it, 96 blocks east: a chunk of its own
		BlockPos queuedAt = runningAt.offset(96, 0, 0);
		BuildSite queued = Builders.enqueue(level, builder, owner, TEST_HUT, at(level, queuedAt));
		helper.assertTrue(queued != null && queued.isQueued(), "the second build isn't queued");
		// another builder's, ordered by a player who isn't online, 96 blocks south
		BlockPos offlineAt = runningAt.offset(0, 0, 96);
		BuildSite offline = Builders.start(level, other, UUID.randomUUID(), "Gone", TEST_HUT, at(level, offlineAt));
		helper.assertTrue(offline != null, "the offline player's build didn't start");
		Leftovers.after(helper, () -> {
			for (BuildSite s : new BuildSite[]{running, queued, offline}) {
				if (BuildSiteManager.get(level).get(s.id()) != null) {
					Builders.cancel(level, s);
				}
			}
		});

		Set<ChunkPos> kept = KeepLoaded.chunksToKeep(level);
		helper.assertTrue(kept.contains(new ChunkPos(runningAt)), "the running build of an online owner isn't kept loaded: " + kept);
		helper.assertTrue(kept.contains(new ChunkPos(helper.absolutePos(BENCH))), "the running build's bench isn't kept loaded: " + kept);
		helper.assertFalse(kept.contains(new ChunkPos(queuedAt)), "a build still queued is kept loaded (chunk " + new ChunkPos(queuedAt) + ")");
		helper.assertFalse(kept.contains(new ChunkPos(offlineAt)), "the build of an offline player is kept loaded (chunk " + new ChunkPos(offlineAt) + ")");

		owner.connection.disconnect(Component.literal("gone home"));
		helper.runAfterDelay(2, () -> {
			helper.assertTrue(level.getServer().getPlayerList().getPlayer(owner.getUUID()) == null, "setup: the owner is still online");
			Set<ChunkPos> after = KeepLoaded.chunksToKeep(level);
			helper.assertFalse(after.contains(new ChunkPos(runningAt)), "the build is still kept loaded after its owner left: " + after);
			helper.assertFalse(after.contains(new ChunkPos(helper.absolutePos(BENCH))), "the bench is still kept loaded after the owner left: " + after);
			helper.succeed();
		});
	}
}
