package io.github.jcondedata.aliveworkplace.test;

import io.github.jcondedata.aliveworkplace.blueprint.BlueprintData;
import io.github.jcondedata.aliveworkplace.blueprint.BlueprintLibrary;
import io.github.jcondedata.aliveworkplace.blueprint.BlueprintOutline;
import io.github.jcondedata.aliveworkplace.build.BuildSite;
import io.github.jcondedata.aliveworkplace.build.Builders;
import io.github.jcondedata.aliveworkplace.mine.Miners;
import io.github.jcondedata.aliveworkplace.mine.QuarrySite;
import io.github.jcondedata.aliveworkplace.registry.ModBlocks;
import io.github.jcondedata.aliveworkplace.registry.ModGameRules;
import io.github.jcondedata.aliveworkplace.work.SiteChunks;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Vec3i;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Container;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.levelgen.structure.BoundingBox;

/**
 * B666, B667 (36.20: "A worker whose far site sits in unloaded chunks waits there as today (no chunk loading)"): the
 * site is kept exactly as it was while its chunks are away (a quarry's cursor, a build's lists), survives a save and
 * load like that, and the worker goes on from there once the chunks are loaded again (the player came back). The
 * "nothing is loaded" halves are QaFarReach1028BugTests and QaFarReach1028bBugTests.
 */
public class FarSiteUnloadedGameTests {
	private static final String AREA = "aliveworkplace_test:build_area";
	private static final ResourceLocation TEST_HUT = ResourceLocation.fromNamespaceAndPath("aliveworkplace_test", "test_hut");
	private static final BlockPos WORK = new BlockPos(3, 2, 3);
	private static final int AWAY = 200;

	/** Loads every chunk from the work block out to {@code site} (as a player walking out there would), until the test ends. */
	private static void bringBack(GameTestHelper helper, BlockPos work, BoundingBox site) {
		ServerLevel level = helper.getLevel();
		int m = SiteChunks.MARGIN + 16;
		List<ChunkPos> forced = new ArrayList<>();
		for (int cx = (Math.min(work.getX(), site.minX()) - m) >> 4; cx <= (Math.max(work.getX(), site.maxX()) + m) >> 4; cx++) {
			for (int cz = (Math.min(work.getZ(), site.minZ()) - m) >> 4; cz <= (Math.max(work.getZ(), site.maxZ()) + m) >> 4; cz++) {
				if (!level.getForcedChunks().contains(ChunkPos.asLong(cx, cz))) {
					level.setChunkForced(cx, cz, true);
					forced.add(new ChunkPos(cx, cz));
				}
			}
		}
		Leftovers.after(helper, () -> forced.forEach(c -> level.setChunkForced(c.x, c.z, false)));
	}

	/**
	 * A quarry 120 out in unloaded chunks: for 200 ticks on shift its chunks stay away and its cursor, counts and status
	 * don't move; saved and loaded it is the same quarry at the same spot; then the chunks come back and the miner
	 * goes on from that spot (the cursor moves, or the pit is finished).
	 */
	//$ gametest_ticks_batch AREA '1400' '"farSiteQuarryResumes"'
	@GameTest(template = AREA, timeoutTicks = 1400, batch = "farSiteQuarryResumes")
	public void aFarQuarryIsKeptAsItIsAndGoesOnWhenItsChunksAreBack(GameTestHelper helper) {
		QaFarReach1028bGameTests.setUp(helper);
		ServerLevel level = helper.getLevel();
		helper.setBlock(WORK, ModBlocks.MINERS_BENCH);
		helper.setBlock(new BlockPos(3, 2, 5), Blocks.CHEST);
		Container chest = helper.getBlockEntity(new BlockPos(3, 2, 5));
		chest.setItem(0, new ItemStack(Items.IRON_PICKAXE));
		BlockPos bench = helper.absolutePos(WORK);
		BlockPos at = QaFarReach1028bGameTests.unloadedSpot(helper, bench, 120, 2);
		BoundingBox far = new BoundingBox(at.getX() - 2, at.getY() - 3, at.getZ() - 2, at.getX() + 2, at.getY(), at.getZ() + 2);
		List<ChunkPos> farChunks = QaFarReach1028bGameTests.chunks(far);
		Villager miner = helper.spawn(EntityType.VILLAGER, new BlockPos(4, 2, 4));
		Miners.employ(level, miner, bench);
		QuarrySite site = Miners.start(level, miner, null, far, far.getYSpan());
		helper.assertTrue(site != null, "setup: the far quarry didn't start");
		UUID id = site.id();
		BoundingBox started = site.box(); // (far, its bottom kept 5 above the world's floor)
		Leftovers.after(helper, () -> {
			QuarrySite left = Miners.activeSite(level, miner);
			if (left != null) {
				Miners.cancel(level, left);
			}
		});
		long cursor = site.cursor();
		BlockPos current = site.current();
		int[] ticks = {0};
		boolean[] back = {false};
		List<String> problems = new ArrayList<>();
		helper.onEachTick(() -> {
			ticks[0]++;
			if (back[0] || !problems.isEmpty()) {
				return;
			}
			QuarrySite now = Miners.activeSite(level, miner);
			List<ChunkPos> loaded = QaFarReach1028bGameTests.loaded(level, farChunks);
			if (now == null || !now.id().equals(id) || !now.box().equals(started) || now.isDone() || now.cursor() != cursor || now.mined() != 0 || now.skipped() != 0 || !loaded.isEmpty()) {
				problems.add("tick " + ticks[0] + ": far chunks loaded " + loaded + ", the quarry " + (now == null ? "gone"
					: "cursor " + now.cursor() + " (was " + cursor + "), mined " + now.mined() + ", skipped " + now.skipped() + ", done " + now.isDone()));
			}
		});
		helper.runAfterDelay(AWAY, () -> {
			helper.assertTrue(problems.isEmpty(), "the far quarry while its chunks were away: " + problems);
			helper.assertTrue(miner.getBrain().isActive(net.minecraft.world.entity.schedule.Activity.WORK), "setup: the miner wasn't on shift: "
				+ miner.getBrain().getActiveNonCoreActivity());
			QuarrySite saved = QuarrySite.load(Miners.activeSite(level, miner).save());
			helper.assertTrue(saved.id().equals(id) && saved.box().equals(started) && saved.cursor() == cursor && saved.current().equals(current) && !saved.isDone(),
				"saved and loaded, the waiting quarry isn't where it was: cursor " + saved.cursor() + " (was " + cursor + "), at " + saved.current() + " (was " + current + ")");
			back[0] = true;
			bringBack(helper, bench, far);
		});
		helper.succeedWhen(() -> {
			helper.assertTrue(back[0], "the far quarry's chunks are still away");
			helper.assertTrue(problems.isEmpty(), "the far quarry while its chunks were away: " + problems);
			QuarrySite now = Miners.activeSite(level, miner);
			helper.assertTrue(now == null || now.cursor() != cursor || now.mined() > 0, "the chunks are back (" + QaFarReach1028bGameTests.loaded(level, farChunks).size() + " of "
				+ farChunks.size() + " loaded) and the miner hasn't gone on: cursor " + (now == null ? -1 : now.cursor()) + ", status " + (now == null ? null : now.status())
				+ ", miner at " + miner.blockPosition().toShortString());
		});
	}

	/**
	 * A build 120 out in unloaded chunks, handed over with the Blueprint: for 200 ticks on shift its chunks stay away and
	 * the site doesn't move on (stage, list position, blocks placed, skipped); saved and loaded it is the same; then the
	 * chunks come back and the builder goes on with it.
	 */
	//$ gametest_ticks_batch AREA '1800' '"farSiteBuildResumes"'
	@GameTest(template = AREA, timeoutTicks = 1800, batch = "farSiteBuildResumes")
	public void aFarBuildIsKeptAsItIsAndGoesOnWhenItsChunksAreBack(GameTestHelper helper) {
		QaFarReach1028bGameTests.setUp(helper);
		ServerLevel level = helper.getLevel();
		boolean free = level.getGameRules().getBoolean(ModGameRules.FREE_MATERIALS);
		Leftovers.after(helper, () -> level.getGameRules().getRule(ModGameRules.FREE_MATERIALS).set(free, level.getServer()));
		level.getGameRules().getRule(ModGameRules.FREE_MATERIALS).set(true, level.getServer());
		helper.setBlock(WORK, ModBlocks.BLUEPRINT_TABLE);
		BlockPos bench = helper.absolutePos(WORK);
		Vec3i size = BlueprintLibrary.get(level, TEST_HUT).orElseThrow().size();
		BlockPos at = QaFarReach1028bGameTests.unloadedSpot(helper, bench, 120, Math.max(size.getX(), size.getZ()) / 2 + 2);
		Villager builder = helper.spawn(EntityType.VILLAGER, new BlockPos(4, 2, 4));
		builder.setNoAi(true);
		Builders.employ(level, builder, bench);
		QaFarReach1028bGameTests.forgetSitesOf(helper, builder);
		QaStageFiveGameTests.Client c = QaStageSixGameTests.client(helper, GameType.SURVIVAL, QaStageSixGameTests.name("farR"));
		BlueprintData.Placement placement = QaFarReach1028bGameTests.standing(level, TEST_HUT, bench, at);
		c.player().setItemInHand(InteractionHand.MAIN_HAND, QaFarReach1028bGameTests.blueprint(level, TEST_HUT, placement));
		QaStageSixGameTests.clickVillager(c.player(), builder, c.seen());
		BuildSite site = Builders.activeSite(level, builder);
		helper.assertTrue(site != null, "setup: the build 120 out was refused: " + c.seen().stream().map(Component::getString).toList());
		UUID id = site.id();
		c.player().discard();
		BoundingBox far = BlueprintOutline.bounds(placement, size);
		List<ChunkPos> farChunks = QaFarReach1028bGameTests.chunks(far);
		long[] mark = {0};
		int[] ticks = {0};
		boolean[] back = {false};
		List<String> problems = new ArrayList<>();
		helper.onEachTick(() -> {
			ticks[0]++;
			if (ticks[0] < 100 || back[0] || !problems.isEmpty()) {
				return; // (the first 100 ticks: whatever handing the site over loaded unloads, the builder still)
			}
			BuildSite now = Builders.activeSite(level, builder);
			List<ChunkPos> loaded = QaFarReach1028bGameTests.loaded(level, farChunks);
			if (ticks[0] == 100) {
				if (now == null || !loaded.isEmpty()) {
					problems.add("setup: 100 ticks after the hand-over the far chunks were still loaded " + loaded + " or the site gone");
					return;
				}
				mark[0] = now.progressMark();
				builder.setNoAi(false);
				return;
			}
			if (now == null || !now.id().equals(id) || now.isDone() || now.progressMark() != mark[0] || now.placed() != 0 || !loaded.isEmpty()) {
				problems.add("tick " + ticks[0] + ": far chunks loaded " + loaded + ", the build " + (now == null ? "gone"
					: "stage " + now.stage() + ", at " + now.cursor() + ", placed " + now.placed() + ", skipped " + now.skipped()));
			}
		});
		helper.runAfterDelay(100 + AWAY, () -> {
			helper.assertTrue(problems.isEmpty(), "the far build while its chunks were away: " + problems);
			helper.assertTrue(builder.getBrain().isActive(net.minecraft.world.entity.schedule.Activity.WORK), "setup: the builder wasn't on shift: "
				+ builder.getBrain().getActiveNonCoreActivity());
			BuildSite live = Builders.activeSite(level, builder);
			BuildSite saved = BuildSite.load(live.save());
			helper.assertTrue(saved.id().equals(id) && saved.stage() == live.stage() && saved.cursor() == live.cursor() && saved.placed() == 0 && saved.skipped() == live.skipped()
				&& saved.placement().equals(placement), "saved and loaded, the waiting build isn't as it was: stage " + saved.stage() + ", at " + saved.cursor()
				+ ", placed " + saved.placed() + ", skipped " + saved.skipped());
			back[0] = true;
			bringBack(helper, bench, far);
		});
		helper.succeedWhen(() -> {
			helper.assertTrue(back[0], "the far build's chunks are still away");
			helper.assertTrue(problems.isEmpty(), "the far build while its chunks were away: " + problems);
			BuildSite now = Builders.activeSite(level, builder);
			helper.assertTrue(now != null && now.id().equals(id), "the far build isn't the builder's any more");
			helper.assertTrue(now.placed() > 0, "the chunks are back (" + QaFarReach1028bGameTests.loaded(level, farChunks).size() + " of " + farChunks.size()
				+ " loaded) and the builder hasn't placed a block: stage " + now.stage() + ", at " + now.cursor() + ", status " + now.status()
				+ ", builder at " + builder.blockPosition().toShortString());
		});
	}
}
