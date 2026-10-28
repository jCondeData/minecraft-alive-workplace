package io.github.jcondedata.aliveworkplace.test;

import io.github.jcondedata.aliveworkplace.hall.VillageHalls;
import io.github.jcondedata.aliveworkplace.hall.VillageProtection;
import io.github.jcondedata.aliveworkplace.mine.Miners;
import io.github.jcondedata.aliveworkplace.mine.QuarryData;
import io.github.jcondedata.aliveworkplace.mine.QuarrySite;
import io.github.jcondedata.aliveworkplace.registry.ModBlocks;
import io.github.jcondedata.aliveworkplace.registry.ModComponents;
import io.github.jcondedata.aliveworkplace.registry.ModGameRules;
import io.github.jcondedata.aliveworkplace.registry.ModItems;
import io.github.jcondedata.aliveworkplace.registry.ModVillagers;
import io.github.jcondedata.aliveworkplace.work.Jobs;
import io.github.jcondedata.aliveworkplace.work.KeepLoaded;
import io.github.jcondedata.aliveworkplace.work.Reach;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
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
 * QA (2026-10-28) bug tests for 36.20: a quarry marked 125 blocks out (inside the miner's long reach of 128) with the
 * Quarry Marker's standard depth 64 is refused ("... 129 blocks from the Miner's Bench ... keep it within 128."),
 * while the same spot marked 96 deep is taken: a pit of 64 or less is measured to the middle of its depth, a deeper one
 * to the middle of its top. The spec: "a spot is in reach when ... within a long reach of the worker's block outside it:
 * mines ... 128", and Reach's own rule "Up and down never count".
 *
 * <p>And "A worker whose far site sits in unloaded chunks waits there as today (no chunk loading)": a quarry 120 blocks
 * out in chunks nobody has loaded, nobody online to keep it loaded; on the miner's first work tick its chunks are
 * loaded (MinerWork reads the box's blocks with no loaded check, which loads the chunk on the server thread).
 */
public class QaFarReach1028BugTests {
	private static final String AREA = "aliveworkplace_test:build_area";

	/** True if a 5 × 5 quarry, its top at the bench's height {@code east} blocks east, {@code depth} deep, is taken. */
	private static boolean offer(GameTestHelper helper, ServerPlayer p, Villager miner, BlockPos bench, int east, int depth, List<Component> seen) {
		ServerLevel level = helper.getLevel();
		BlockPos at = bench.east(east);
		ItemStack marker = new ItemStack(ModItems.QUARRY_MARKER);
		marker.set(ModComponents.QUARRY, new QuarryData(Optional.of(level.dimension().location()), Optional.of(at.offset(-2, 0, -2)), Optional.of(at.offset(2, 0, 2)), depth));
		p.setItemInHand(InteractionHand.MAIN_HAND, marker);
		seen.clear();
		QaStageSixGameTests.clickVillager(p, miner, seen);
		QuarrySite site = Miners.activeSite(level, miner);
		if (site != null) {
			Miners.cancel(level, site);
		}
		return site != null;
	}

	//$ gametest_ticks_batch AREA '200' '"qa1028QuarryDepthReach"'
	@GameTest(template = AREA, timeoutTicks = 200, batch = "qa1028QuarryDepthReach")
	public void aQuarry125OutIsInReachWhateverItsDepth(GameTestHelper helper) {
		Leftovers.clear(helper);
		ServerLevel level = helper.getLevel();
		boolean protection = VillageProtection.ENABLED;
		VillageProtection.ENABLED = false;
		int radius = VillageHalls.RADIUS;
		// the bench high in the air (above the test area), so a 96-deep pit under it stays above the world's floor
		BlockPos bench = helper.absolutePos(new BlockPos(3, 2, 3)).above(100);
		Leftovers.after(helper, () -> {
			VillageProtection.ENABLED = protection;
			VillageHalls.RADIUS = radius;
			VillageHalls.forgetNearest();
			level.setBlockAndUpdate(bench, Blocks.AIR.defaultBlockState());
			level.setBlockAndUpdate(bench.below(), Blocks.AIR.defaultBlockState());
		});
		helper.setBlock(new BlockPos(1, 2, 1), ModBlocks.VILLAGE_HALL);
		VillageHalls.RADIUS = 16;
		VillageHalls.forgetNearest();
		level.setBlockAndUpdate(bench, ModBlocks.MINERS_BENCH.defaultBlockState());
		level.setBlockAndUpdate(bench.below(), Blocks.STONE.defaultBlockState());
		Villager miner = EntityType.VILLAGER.create(level);
		miner.moveTo(bench.getX() + 1.5, bench.getY(), bench.getZ() + 0.5);
		miner.setNoAi(true);
		level.addFreshEntity(miner);
		Leftovers.after(helper, miner::discard);
		Jobs.employ(level, miner, bench, ModVillagers.MINERS_BENCH_POI, ModVillagers.MINER);
		List<Component> seen = new ArrayList<>();
		ServerPlayer p = QaStageFourGameTests.player(helper, seen, GameType.SURVIVAL);

		helper.assertTrue(offer(helper, p, miner, bench, 125, 96, seen), "control: the 96-deep quarry 125 blocks out was refused: "
			+ seen.stream().map(Component::getString).toList());
		helper.assertTrue(offer(helper, p, miner, bench, 125, 4, seen), "control: the 4-deep quarry 125 blocks out was refused: "
			+ seen.stream().map(Component::getString).toList());
		boolean taken = offer(helper, p, miner, bench, 125, 64, seen);
		helper.assertTrue(taken, "the 64-deep quarry 125 blocks out (the 96-deep one there was taken) was refused: "
			+ seen.stream().map(Component::getString).toList());
		helper.succeed();
	}

	// --- a far quarry in unloaded chunks: the miner waits, nothing is loaded ---------------------------------------------

	/** The chunks a box covers. */
	private static List<ChunkPos> chunks(BoundingBox box) {
		List<ChunkPos> out = new ArrayList<>();
		for (int cx = box.minX() >> 4; cx <= box.maxX() >> 4; cx++) {
			for (int cz = box.minZ() >> 4; cz <= box.maxZ() >> 4; cz++) {
				out.add(new ChunkPos(cx, cz));
			}
		}
		return out;
	}

	private static List<ChunkPos> loaded(ServerLevel level, List<ChunkPos> chunks) {
		return chunks.stream().filter(c -> level.getChunkSource().getChunkNow(c.x, c.z) != null).toList();
	}

	/**
	 * A quarry 120 blocks out (outside a village of radius 16, inside the long reach), in chunks nobody has loaded; the
	 * miner on shift (day 2000) with a pickaxe in the chest by the bench; the order's player offline, so nothing keeps
	 * the site loaded. For 300 ticks the miner works: the quarry's chunks are never loaded, the quarry stays his.
	 */
	//$ gametest_ticks_batch AREA '500' '"qa1028FarUnloaded"'
	@GameTest(template = AREA, timeoutTicks = 500, batch = "qa1028FarUnloaded")
	public void aFarQuarryInUnloadedChunksWaitsWithoutLoadingThem(GameTestHelper helper) {
		Leftovers.clear(helper);
		ServerLevel level = helper.getLevel();
		boolean protection = VillageProtection.ENABLED;
		VillageProtection.ENABLED = false;
		int radius = VillageHalls.RADIUS;
		boolean keep = level.getGameRules().getBoolean(ModGameRules.KEEP_WORK_LOADED);
		boolean villages = KeepLoaded.VILLAGES;
		Leftovers.after(helper, () -> {
			VillageProtection.ENABLED = protection;
			VillageHalls.RADIUS = radius;
			VillageHalls.forgetNearest();
			level.getGameRules().getRule(ModGameRules.KEEP_WORK_LOADED).set(keep, level.getServer());
			KeepLoaded.VILLAGES = villages;
		});
		level.getGameRules().getRule(ModGameRules.KEEP_WORK_LOADED).set(false, level.getServer());
		KeepLoaded.VILLAGES = false;
		helper.setBlock(new BlockPos(1, 2, 1), ModBlocks.VILLAGE_HALL);
		VillageHalls.RADIUS = 16;
		VillageHalls.forgetNearest();
		helper.setDayTime(2000);
		BlockPos benchRel = new BlockPos(3, 2, 3);
		helper.setBlock(benchRel, ModBlocks.MINERS_BENCH);
		helper.setBlock(new BlockPos(3, 2, 5), Blocks.CHEST);
		Container chest = helper.getBlockEntity(new BlockPos(3, 2, 5));
		chest.setItem(0, new ItemStack(Items.IRON_PICKAXE));
		BlockPos bench = helper.absolutePos(benchRel);

		// a spot 120 out whose chunks nobody has loaded
		BoundingBox box = null;
		for (int angle = 0; angle < 360 && box == null; angle += 15) {
			double a = Math.toRadians(angle);
			BlockPos at = bench.offset((int) Math.round(120 * Math.cos(a)), 0, (int) Math.round(120 * Math.sin(a)));
			BoundingBox candidate = new BoundingBox(at.getX() - 2, at.getY() - 3, at.getZ() - 2, at.getX() + 2, at.getY(), at.getZ() + 2);
			if (loaded(level, chunks(candidate)).isEmpty()) {
				box = candidate;
			}
		}
		helper.assertTrue(box != null, "setup: every spot 120 blocks round the bench is in a loaded chunk");
		BoundingBox far = box;
		List<ChunkPos> farChunks = chunks(far);
		helper.assertTrue(!Reach.inSameVillage(level, bench, far.getCenter()) && Reach.inReach(level, bench, far.getCenter(), Miners.MAX_DISTANCE),
			"setup: the far spot isn't outside the village and inside the long reach");

		Villager miner = helper.spawn(EntityType.VILLAGER, new BlockPos(4, 2, 4));
		miner.setNoAi(true);
		Miners.employ(level, miner, bench);
		QuarrySite site = Miners.start(level, miner, null, far, far.getYSpan());
		helper.assertTrue(site != null, "setup: the far quarry didn't start");
		java.util.UUID id = site.id();
		helper.assertTrue(loaded(level, farChunks).isEmpty(), "starting the far quarry loaded its chunks " + loaded(level, farChunks));
		Leftovers.after(helper, () -> {
			QuarrySite left = Miners.activeSite(level, miner);
			if (left != null) {
				Miners.cancel(level, left);
			}
		});
		// first 100 ticks with the miner standing still (nothing else may load the far chunks), then 300 ticks at work
		List<String> problems = new ArrayList<>();
		int[] ticks = {0};
		helper.onEachTick(() -> {
			ticks[0]++;
			if (ticks[0] == 100) {
				miner.setNoAi(false);
			}
			List<ChunkPos> now = loaded(level, farChunks);
			QuarrySite active = Miners.activeSite(level, miner);
			if (problems.isEmpty() && (!now.isEmpty() || active == null)) {
				problems.add("tick " + ticks[0] + " (" + (ticks[0] < 100 ? "miner still" : "miner at work") + "): far chunks loaded " + now
					+ ", the quarry " + (active == null ? "gone (finished or dropped)" : "status " + active.status() + ", done " + active.isDone())
					+ ", miner at " + miner.blockPosition().toShortString() + " " + miner.getBrain().getActiveNonCoreActivity());
			}
		});
		helper.runAfterDelay(400, () -> {
			helper.assertTrue(problems.isEmpty(), "the far quarry in unloaded chunks: " + problems);
			helper.assertTrue(miner.isAlive(), "the miner died");
			QuarrySite after = Miners.activeSite(level, miner);
			helper.assertTrue(after != null && after.id().equals(id) && after.box().equals(far), "the far quarry isn't his any more: " + after);
			helper.assertTrue(miner.getBrain().isActive(net.minecraft.world.entity.schedule.Activity.WORK), "setup: the miner wasn't on shift: "
				+ miner.getBrain().getActiveNonCoreActivity());
			helper.succeed();
		});
	}
}
