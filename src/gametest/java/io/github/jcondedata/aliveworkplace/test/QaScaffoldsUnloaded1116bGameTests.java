package io.github.jcondedata.aliveworkplace.test;

import io.github.jcondedata.aliveworkplace.blueprint.BlueprintData;
import io.github.jcondedata.aliveworkplace.build.BuildSite;
import io.github.jcondedata.aliveworkplace.build.BuildSiteManager;
import io.github.jcondedata.aliveworkplace.build.Builders;
import io.github.jcondedata.aliveworkplace.registry.ModBlocks;
import io.github.jcondedata.aliveworkplace.registry.ModGameRules;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;

/**
 * QA (2026-11-16, lane B): scaffolding (ROADMAP 35.6, docs/wiki/scaffolding.md) when the build ends while one of its
 * columns stands in a chunk nobody has loaded. The wiki: "A site removed with scaffolds still recorded (canceled, its
 * builder died, a Wonder's part called off) has them all taken down at once". A player who cancels a big build from the
 * far side of it (or whose builder dies there) walks over afterwards: none of the builder's scaffolding may still be
 * standing once that chunk loads again. The column here is put far off (4096 blocks east of the test) so its chunk
 * really unloads; the site records it as its builder would.
 */
public class QaScaffoldsUnloaded1116bGameTests implements net.fabricmc.fabric.api.gametest.v1.FabricGameTest {
	private static final String BIG = "aliveworkplace_test:big_area";
	private static final ResourceLocation PILLAR = ResourceLocation.fromNamespaceAndPath("aliveworkplace_test", "test_pillar");
	private static final BlockPos BENCH = new BlockPos(3, 2, 3);
	private static final BlockPos CHEST = new BlockPos(3, 2, 5);
	private static final BlockPos VILLAGER = new BlockPos(4, 2, 4);
	private static final BlockPos PILLAR_AT = new BlockPos(11, 2, 11);
	private static final int FAR = 4096;
	private static final int COLUMN = 3;
	/** Ticks to wait for the far chunk to unload, and after loading it again. */
	private static final int UNLOAD_WAIT = 600;
	private static final int SETTLE = 40;

	private static void log(GameTestHelper helper, String line) {
		helper.getLevel().getServer().sendSystemMessage(Component.literal("[qa-scaffolds-unloaded-1116b] " + line));
	}

	/** Canceled while a recorded column stands in an unloaded chunk: once that chunk loads again, no scaffolding of it stands. */
	//$ gametest_ticks_batch BIG '2000' '"qaScaffoldsUnloadedCancel"'
	@GameTest(template = BIG, timeoutTicks = 2000, batch = "qaScaffoldsUnloadedCancel")
	public void canceledWithAColumnInAnUnloadedChunkLeavesNoneStanding(GameTestHelper helper) {
		run(helper, (level, site) -> Builders.cancel(level, site), "canceled");
	}

	/** The site removed any other way (its builder died, a Wonder's part called off): the same, through the manager. */
	//$ gametest_ticks_batch BIG '2000' '"qaScaffoldsUnloadedRemoved"'
	@GameTest(template = BIG, timeoutTicks = 2000, batch = "qaScaffoldsUnloadedRemoved")
	public void siteRemovedWithAColumnInAnUnloadedChunkLeavesNoneStanding(GameTestHelper helper) {
		run(helper, (level, site) -> BuildSiteManager.get(level).remove(site.id()), "removed");
	}

	private interface End {
		void end(ServerLevel level, BuildSite site);
	}

	private static void run(GameTestHelper helper, End end, String how) {
		Leftovers.clear(helper);
		ServerLevel level = helper.getLevel();
		helper.setDayTime(2000);
		level.getGameRules().getRule(ModGameRules.BUILDERS_HELP).set(false, level.getServer());
		helper.setBlock(BENCH, ModBlocks.BUILDERS_BENCH);
		helper.setBlock(CHEST, Blocks.CHEST);
		Villager villager = helper.spawn(EntityType.VILLAGER, VILLAGER);
		Builders.employ(level, villager, helper.absolutePos(BENCH));
		BuildSite site = Builders.start(level, villager, null, PILLAR,
			new BlueprintData.Placement(level.dimension().location(), helper.absolutePos(PILLAR_AT), Rotation.NONE, Mirror.NONE));
		if (site == null) {
			throw new GameTestAssertException("the build of " + PILLAR + " didn't start");
		}
		UUID id = site.id();
		// The far column: stone under it, three scaffolds on top, recorded on the site.
		BlockPos foot = helper.absolutePos(new BlockPos(FAR, 2, 2));
		level.setBlockAndUpdate(foot.below(), Blocks.STONE.defaultBlockState());
		List<BlockPos> column = new ArrayList<>();
		for (int i = 0; i < COLUMN; i++) {
			BlockPos p = foot.above(i);
			level.setBlockAndUpdate(p, Blocks.SCAFFOLDING.defaultBlockState());
			site.addScaffold(p);
			column.add(p);
		}
		// And one by the bench, in the loaded test area, which the end must take down as always.
		BlockPos near = helper.absolutePos(new BlockPos(6, 2, 2));
		level.setBlockAndUpdate(near.below(), Blocks.STONE.defaultBlockState());
		level.setBlockAndUpdate(near, Blocks.SCAFFOLDING.defaultBlockState());
		site.addScaffold(near);
		for (BlockPos p : column) {
			helper.assertTrue(level.getBlockState(p).is(Blocks.SCAFFOLDING), "the far scaffold at " + p + " wasn't placed");
		}
		int[] phase = {0}; // 0 waiting for the unload, 1 ended and reloaded, settling
		int[] ticks = {0};
		helper.onEachTick(() -> {
			ticks[0]++;
			if (phase[0] == 0 && !level.isLoaded(foot)) {
				log(helper, "far chunk unloaded after " + ticks[0] + " ticks; site " + how + " with " + site.scaffolds().size() + " scaffolds recorded");
				end.end(level, site);
				helper.assertTrue(BuildSiteManager.get(level).get(id) == null, "the site wasn't " + how);
				helper.assertFalse(level.getBlockState(near).is(Blocks.SCAFFOLDING), "the scaffold by the bench is still standing after the site was " + how);
				level.getChunk(foot); // a player walks over: the chunk loads again
				phase[0] = 1;
				ticks[0] = 0;
			}
		});
		helper.succeedWhen(() -> {
			helper.assertTrue(phase[0] == 1, "the far chunk didn't unload yet (" + ticks[0] + " ticks)");
			helper.assertTrue(ticks[0] >= SETTLE, "settling: " + ticks[0]);
			level.getChunk(foot);
			for (BlockPos p : column) {
				if (level.getBlockState(p).is(Blocks.SCAFFOLDING)) {
					throw new GameTestAssertException("the builder's scaffolding is still standing at " + p + ", " + SETTLE
						+ " ticks after its chunk loaded again (the site was " + how + " while that chunk was unloaded)");
				}
			}
			log(helper, "site " + how + " with a column in an unloaded chunk: none standing once it loaded again");
		});
	}
}
