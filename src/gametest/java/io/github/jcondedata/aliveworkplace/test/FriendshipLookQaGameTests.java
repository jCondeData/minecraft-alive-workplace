package io.github.jcondedata.aliveworkplace.test;

import io.github.jcondedata.aliveworkplace.story.Friendship;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.ChunkPos;

/**
 * QA (qa-1009-2234), bug test for B93: the friendship look check (31.5) must never load a chunk. It runs every 10
 * ticks for every player on the server and casts a ray with {@code level.clip}, which reads blocks through
 * {@code Level.getChunk}: for a player standing where no chunk is loaded that is a blocking chunk load on the server
 * thread, each time. The test suite's left-over mock players stand in exactly such chunks (nothing moves their chunk
 * tickets), so every tick of a full run got slower the more tests had run: 64% of the server thread sat in
 * {@code Friendship.lookedAt} ten minutes into a local run, and the full build went from 13 to 110 minutes on CI.
 */
public class FriendshipLookQaGameTests {
	private static final String AREA = "aliveworkplace_test:big_area";

	//$ gametest_ticks_batch AREA '60' '"qaFriendshipLookFarOff"'
	@GameTest(template = AREA, timeoutTicks = 60, batch = "qaFriendshipLookFarOff")
	public void lookingFromAnUnloadedChunkLoadsNothing(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		ServerPlayer player = helper.makeMockServerPlayerInLevel();
		BlockPos far = helper.absolutePos(new BlockPos(1, 2, 1)).offset(4000, 0, 4000);
		ChunkPos chunk = new ChunkPos(far);
		player.moveTo(far.getX() + 0.5, far.getY(), far.getZ() + 0.5, 0f, 0f);
		try {
			helper.assertTrue(!level.getChunkSource().hasChunk(chunk.x, chunk.z),
				"the test's own far-off chunk " + chunk + " was already loaded, so this test proves nothing");
			Component line = Friendship.lookLine(player);
			helper.assertTrue(line == null, "hearts shown to a player with nobody in sight: " + line);
			helper.assertTrue(!level.getChunkSource().hasChunk(chunk.x, chunk.z),
				"the friendship look check loaded chunk " + chunk + " for a player standing where nothing is loaded");
		} finally {
			level.getServer().getPlayerList().remove(player);
		}
		helper.succeed();
	}
}
