package io.github.jcondedata.aliveworkplace.test;

import static io.github.jcondedata.aliveworkplace.test.QaBuilderTerrain1102GameTests.EXTRA;
import static io.github.jcondedata.aliveworkplace.test.QaBuilderTerrain1102GameTests.assertFinished;
import static io.github.jcondedata.aliveworkplace.test.QaBuilderTerrain1102GameTests.assertNoFluid;
import static io.github.jcondedata.aliveworkplace.test.QaBuilderTerrain1102GameTests.loose;
import static io.github.jcondedata.aliveworkplace.test.QaBuilderTerrain1102GameTests.prepare;
import static io.github.jcondedata.aliveworkplace.test.QaBuilderTerrain1102GameTests.start;
import static io.github.jcondedata.aliveworkplace.test.QaBuilderTerrain1102GameTests.watchDeath;
import static io.github.jcondedata.aliveworkplace.test.QaBuilderWater1114bGameTests.fill;

import io.github.jcondedata.aliveworkplace.blueprint.BlueprintData;
import io.github.jcondedata.aliveworkplace.build.BuildPlan;
import io.github.jcondedata.aliveworkplace.build.BuildSite;
import io.github.jcondedata.aliveworkplace.build.BuildSiteManager;
import io.github.jcondedata.aliveworkplace.build.Builders;
import io.github.jcondedata.aliveworkplace.registry.ModBlocks;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Vec3i;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Container;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.DoublePlantBlock;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.level.levelgen.structure.BoundingBox;

/**
 * QA (2026-11-15, lane-b-1115-0337's tester) of B1243's drain at the end of CLEAR, written from the spec, not the code:
 * docs/wiki/builder.md "Stages": "A build clicked onto the bed of a pond or a lake (its room under the water line) ends
 * dry: at the end of CLEAR the water or lava standing in its rooms ... is taken away at once ... and the water just
 * beside or above that would run back in is plugged with dirt, a little dam ... Water a build stands in or over on
 * purpose, open to the outside, and a pool the drawing has stay"; ROADMAP 23.1 "no item is duplicated or lost".
 * QaBuilderWater1114b*, QaBuilderWaterVerify1114f and QaBuilderWadeIn1115b cover a hut on a pond's bed, sunk in a lake,
 * waterlogged slabs, lava in the footprint, reloads round CLEAR and the lake beyond the border. These add:
 * <ul>
 * <li>a shut room with no door (a scanned cobblestone basin) sunk in a pond ends dry (the same basin with a pool in its
 * floor is QaBuilderDrainEdges1115bBugTests on tests/builder-drain-1115b);</li>
 * <li>a waterfall pouring into the box from a source above it: the room ends dry and stays dry 300 ticks after;</li>
 * <li>a canal of water, and one of lava, running past one side: plugged only beside the box, flowing on elsewhere, the
 * room dry for 200 ticks after the build, every material adding up;</li>
 * <li>a player's waterlogged slab right beside the box is kept, never buried under a plug;</li>
 * <li>seagrass, tall seagrass, kelp and a bubble column over soul sand in a sunk room: no water, no plants, no
 * duplicated items;</li>
 * <li>a hut wholly under a lake, water above its roof.</li>
 * </ul>
 * The setup helpers (bench, chest and barrels with the hut's list plus 2 of each, free materials off) are
 * QaBuilderTerrain1102GameTests'.
 */
public class QaBuilderDrainEdges1115bGameTests implements FabricGameTest {
	private static final String HUGE = "aliveworkplace_test:huge_area";
	static final org.slf4j.Logger LOGGER = org.slf4j.LoggerFactory.getLogger("aliveworkplace_test");

	/** Ticks of the game since the site was done, or -1 while it is building. */
	private static int[] watchDone(GameTestHelper helper, QaBuilderTerrain1102GameTests.Run r) {
		int[] doneAt = {-1};
		helper.onEachTick(() -> {
			if (doneAt[0] < 0 && r.site() == null) {
				doneAt[0] = (int) helper.getTick();
			}
		});
		return doneAt;
	}

	private static void waitAfterDone(GameTestHelper helper, int[] doneAt, int ticks) {
		helper.assertTrue(doneAt[0] >= 0 && helper.getTick() - doneAt[0] >= ticks,
			"waiting " + ticks + " ticks after the build was done (done at " + doneAt[0] + ", now " + helper.getTick() + ")");
	}

	private static void gameRule(GameTestHelper helper, GameRules.Key<GameRules.BooleanValue> key, boolean value) {
		ServerLevel level = helper.getLevel();
		boolean was = level.getGameRules().getBoolean(key);
		level.getGameRules().getRule(key).set(value, level.getServer());
		Leftovers.after(helper, () -> level.getGameRules().getRule(key).set(was, level.getServer()));
	}

	static List<String> wet(GameTestHelper helper, QaBuilderTerrain1102GameTests.Run r, BlockPos except) {
		List<String> out = new ArrayList<>();
		BoundingBox box = r.plan().bounds();
		for (BlockPos p : BlockPos.betweenClosed(box.minX(), box.minY(), box.minZ(), box.maxX(), box.maxY(), box.maxZ())) {
			if (!r.level().getFluidState(p).isEmpty() && (except == null || !p.equals(except))) {
				out.add(helper.relativePos(p).toShortString() + "=" + r.level().getBlockState(p));
			}
		}
		return out;
	}

	/**
	 * Every one of the build's materials left loose is exactly the EXTRA stocked over its list, but cobblestone: the
	 * stone plateau dug round a sunk build drops cobblestone, so only none of it may be lost; and the buckets, whose
	 * count for a pool the pond already filled isn't the drain's promise.
	 */
	static void materialsAddUp(GameTestHelper helper, QaBuilderTerrain1102GameTests.Run r) {
		Map<Item, Integer> off = new HashMap<>();
		for (Map.Entry<Item, Integer> e : r.plan().materials().entrySet()) {
			Item item = e.getKey();
			if (item == Items.WATER_BUCKET || item == Items.BUCKET) {
				continue;
			}
			int got = loose(helper, r, item);
			if (item == Items.COBBLESTONE ? got < EXTRA : got != EXTRA) {
				off.put(item, got);
			}
		}
		helper.assertTrue(off.isEmpty(), "materials made or lost: left loose " + off + ", wanted " + EXTRA + " of each over " + r.plan().materials()
			+ " (cobblestone at least " + EXTRA + ")");
	}

	// ---------------------------------------------------------------------------------------------- the drawing's pool

	/**
	 * Scans a 5x4x5 basin into a fresh blueprint: a cobblestone floor with a water source in its middle (a one-block
	 * pool sunk in the floor), cobblestone walls two high and a cobblestone roof: one room, shut on every side.
	 */
	static ResourceLocation basin(GameTestHelper helper, boolean pool) {
		ServerLevel level = helper.getLevel();
		BlockPos at = new BlockPos(20, 12, 20);
		fill(helper, 20, 24, 11, 11, 20, 24, Blocks.STONE); // under the pool while it is scanned
		for (int x = 0; x < 5; x++) {
			for (int z = 0; z < 5; z++) {
				boolean edge = x == 0 || z == 0 || x == 4 || z == 4;
				helper.setBlock(at.offset(x, 0, z), pool && x == 2 && z == 2 ? Blocks.WATER : Blocks.COBBLESTONE);
				helper.setBlock(at.offset(x, 1, z), edge ? Blocks.COBBLESTONE : Blocks.AIR);
				helper.setBlock(at.offset(x, 2, z), edge ? Blocks.COBBLESTONE : Blocks.AIR);
				helper.setBlock(at.offset(x, 3, z), Blocks.COBBLESTONE);
			}
		}
		ResourceLocation id = ResourceLocation.fromNamespaceAndPath("aliveworkplace_test", "qa1115b_basin_" + Long.toHexString(level.getGameTime()));
		level.getStructureManager().getOrCreate(id).fillFromWorld(level, helper.absolutePos(at), new Vec3i(5, 4, 5), false, Blocks.STRUCTURE_VOID);
		fill(helper, 20, 24, 11, 15, 20, 24, Blocks.AIR);
		return id;
	}

	/** The terrain tests' {@code start}, for blueprint {@code id}. */
	static QaBuilderTerrain1102GameTests.Run startWith(GameTestHelper helper, int g, BlockPos origin, ResourceLocation id) {
		ServerLevel level = helper.getLevel();
		BlockPos table = new BlockPos(2, g, 2);
		BlockPos chest = new BlockPos(2, g, 4);
		List<BlockPos> supplies = List.of(chest, new BlockPos(1, g, 4), new BlockPos(3, g, 4), new BlockPos(4, g, 4), new BlockPos(5, g, 4));
		helper.setBlock(table, ModBlocks.BUILDERS_BENCH);
		QaBuilderDisruptionGameTests.ensurePoi(level, helper.absolutePos(table));
		for (BlockPos p : supplies) {
			helper.setBlock(p, p.equals(chest) ? Blocks.CHEST : Blocks.BARREL);
		}
		Villager builder = helper.spawn(EntityType.VILLAGER, new BlockPos(3, g, 3));
		Builders.employ(level, builder, helper.absolutePos(table));
		BlueprintData.Placement placement = new BlueprintData.Placement(level.dimension().location(), helper.absolutePos(origin), Rotation.NONE, Mirror.NONE);
		BuildSite site = Builders.start(level, builder, null, id, placement);
		if (site == null) {
			throw new GameTestAssertException(id + " didn't start");
		}
		BuildPlan plan = site.plan(level);
		if (plan == null) {
			throw new GameTestAssertException("no blueprint " + id);
		}
		UUID siteId = site.id();
		Leftovers.after(helper, () -> {
			if (BuildSiteManager.get(level).get(siteId) != null) {
				BuildSiteManager.get(level).remove(siteId);
			}
		});
		List<ItemStack> stock = new ArrayList<>();
		plan.materials().forEach((item, n) -> {
			int left = n + EXTRA;
			while (left > 0) {
				int k = Math.min(left, item.getDefaultMaxStackSize());
				stock.add(new ItemStack(item, k));
				left -= k;
			}
		});
		for (int i = 0; i < stock.size(); i++) {
			Container c = helper.getBlockEntity(supplies.get(supplies.size() - 1 - i / 27));
			c.setItem(i % 27, stock.get(i));
		}
		LOGGER.info("qa 1115b drain: {} materials {}, bounds {}", id, plan.materials(), plan.bounds());
		return new QaBuilderTerrain1102GameTests.Run(level, new Villager[] {builder}, siteId, plan, g, supplies, new String[1]);
	}

	/**
	 * The same shut basin room drawn without its pool (a whole cobblestone floor), on the bed of the same pond: the
	 * room ends dry and the basin as drawn (the control for the pool test: a shut room with no door is drained too).
	 */
	//$ gametest_ticks_batch HUGE '12000' '"qa1115bDrainShut"'
	@GameTest(template = HUGE, timeoutTicks = 12000, batch = "qa1115bDrainShut")
	public void aShutRoomWithNoDoorSunkInAPondEndsDry(GameTestHelper helper) {
		prepare(helper);
		ResourceLocation id = basin(helper, false);
		fill(helper, 0, 29, 2, 4, 0, 29, Blocks.STONE);
		fill(helper, 6, 10, 2, 4, 6, 10, Blocks.WATER);
		QaBuilderTerrain1102GameTests.Run r = startWith(helper, 5, new BlockPos(6, 2, 6), id);
		watchDeath(helper, r);
		helper.succeedWhen(() -> {
			assertFinished(helper, r);
			assertNoFluid(helper, r);
			materialsAddUp(helper, r);
			LOGGER.info("qa 1115b drain shut: dry at tick {}", helper.getTick());
		});
	}

	// ---------------------------------------------------------------------------------------------- a waterfall

	/**
	 * A water source hangs four blocks above the test hut's roof (over the middle of its room), on flat ground, so a
	 * waterfall pours through the box from before the build starts: the finished hut is as drawn with no water in its
	 * box, and still none 300 ticks after it is done.
	 */
	//$ gametest_ticks_batch HUGE '12000' '"qa1115bDrainFall"'
	@GameTest(template = HUGE, timeoutTicks = 12000, batch = "qa1115bDrainFall")
	public void aWaterfallPouringIntoTheBoxEndsDryAndStaysDry(GameTestHelper helper) {
		prepare(helper);
		// the test hut's box is x 10-14, y 2-5, z 10-14: the source hangs at y 9 over its middle, poured 60 ticks before
		// the hut is handed over, so the fall stands in the box when CLEAR starts
		helper.setBlock(new BlockPos(12, 9, 12), Blocks.WATER);
		QaBuilderTerrain1102GameTests.Run[] run = {null};
		boolean[] poured = {false};
		int[] doneAt = {-1};
		helper.runAfterDelay(60, () -> {
			poured[0] = !helper.getBlockState(new BlockPos(12, 3, 12)).getFluidState().isEmpty();
			run[0] = start(helper, 2, new BlockPos(10, 2, 10));
			helper.assertTrue(run[0].plan().bounds().equals(BoundingBox.fromCorners(helper.absolutePos(new BlockPos(10, 2, 10)),
				helper.absolutePos(new BlockPos(14, 5, 14)))), "setup: the hut's box isn't x 10-14, y 2-5, z 10-14: " + run[0].plan().bounds());
		});
		helper.onEachTick(() -> {
			QaBuilderTerrain1102GameTests.Run r = run[0];
			if (r == null) {
				return;
			}
			if (doneAt[0] < 0 && r.site() == null) {
				doneAt[0] = (int) helper.getTick();
			}
		});
		helper.succeedWhen(() -> {
			QaBuilderTerrain1102GameTests.Run r = run[0];
			helper.assertTrue(r != null, "not started yet");
			helper.assertTrue(poured[0], "setup: the waterfall wasn't pouring through the room when the hut was handed over");
			helper.assertTrue(r.builder()[0].isAlive(), "the builder died");
			assertFinished(helper, r);
			waitAfterDone(helper, doneAt, 300);
			assertFinished(helper, r);
			assertNoFluid(helper, r);
			LOGGER.info("qa 1115b drain fall: dry 300 ticks after done, tick {}", helper.getTick());
		});
	}

	// ---------------------------------------------------------------------------------------------- a canal past one side

	/** A stone plateau two high, a pond two deep exactly the hut's footprint and a canal one wide along its east side. */
	private static QaBuilderTerrain1102GameTests.Run pondAndCanal(GameTestHelper helper, Block fluid) {
		prepare(helper);
		fill(helper, 0, 29, 2, 3, 0, 29, Blocks.STONE);
		fill(helper, 6, 10, 2, 3, 6, 10, fluid);
		fill(helper, 11, 11, 2, 3, 0, 29, fluid);
		return start(helper, 4, new BlockPos(6, 2, 6));
	}

	/**
	 * The canal past the hut keeps flowing: every spot of it not beside the hut's box (z outside 6-10) is still a source
	 * of {@code fluid}, and the spots beside the box are that fluid or a dirt plug, nothing else.
	 */
	private static void assertCanal(GameTestHelper helper, Block fluid) {
		List<String> wrong = new ArrayList<>();
		for (int z = 0; z <= 29; z++) {
			for (int y = 2; y <= 3; y++) {
				BlockState s = helper.getBlockState(new BlockPos(11, y, z));
				boolean beside = z >= 6 && z <= 10;
				boolean ok = s.is(fluid) && s.getFluidState().isSource() || beside && (s.is(fluid) || s.is(Blocks.DIRT));
				if (!ok) {
					wrong.add("(11," + y + "," + z + ")=" + s.getBlock().getDescriptionId());
				}
			}
		}
		helper.assertTrue(wrong.isEmpty(), wrong.size() + " spot(s) of the canal changed (only those beside the box may be plugged): "
			+ wrong.stream().limit(12).toList());
	}

	/**
	 * A water canal one wide runs past the east side of the test hut sunk in a pond its footprint: the hut ends dry and
	 * as drawn, stays dry 200 ticks after, the canal still flows everywhere but beside the box (where only dirt plugs may
	 * stand), and every material adds up (no dirt plug is taken from the hut's stock).
	 */
	//$ gametest_ticks_batch HUGE '12000' '"qa1115bDrainCanal"'
	@GameTest(template = HUGE, timeoutTicks = 12000, batch = "qa1115bDrainCanal")
	public void aCanalPastOneSideIsPluggedOnlyBesideTheBox(GameTestHelper helper) {
		QaBuilderTerrain1102GameTests.Run r = pondAndCanal(helper, Blocks.WATER);
		watchDeath(helper, r);
		int[] doneAt = watchDone(helper, r);
		helper.succeedWhen(() -> {
			assertFinished(helper, r);
			waitAfterDone(helper, doneAt, 200);
			assertFinished(helper, r);
			assertNoFluid(helper, r);
			assertCanal(helper, Blocks.WATER);
			materialsAddUp(helper, r);
			LOGGER.info("qa 1115b drain canal: dry, canal flowing at tick {}", helper.getTick());
		});
	}

	/**
	 * The same with lava in the pond and the canal (fire spread off, so vanilla lava lighting planks near it is left
	 * out): no lava in the finished hut's box 200 ticks after, the lava canal still a source everywhere but beside the
	 * box, the builder alive and every material adding up.
	 */
	//$ gametest_ticks_batch HUGE '12000' '"qa1115bDrainLavaCanal"'
	@GameTest(template = HUGE, timeoutTicks = 12000, batch = "qa1115bDrainLavaCanal")
	public void aLavaCanalPastOneSideIsPluggedAndNeverRunsBack(GameTestHelper helper) {
		gameRule(helper, GameRules.RULE_DOFIRETICK, false);
		QaBuilderTerrain1102GameTests.Run r = pondAndCanal(helper, Blocks.LAVA);
		watchDeath(helper, r);
		int[] doneAt = watchDone(helper, r);
		helper.succeedWhen(() -> {
			assertFinished(helper, r);
			waitAfterDone(helper, doneAt, 200);
			assertFinished(helper, r);
			assertNoFluid(helper, r);
			assertCanal(helper, Blocks.LAVA);
			materialsAddUp(helper, r);
			LOGGER.info("qa 1115b drain lava canal: dry, canal kept at tick {}", helper.getTick());
		});
	}

	/**
	 * A player's waterlogged oak slab sits in a notch of the bank right beside the east wall of the test hut sunk in a
	 * pond its footprint: the hut ends dry and as drawn 200 ticks after, and the slab is still there (drained or not,
	 * "blocks holding water are drained and kept"), never buried under a dirt plug or broken.
	 */
	//$ gametest_ticks_batch HUGE '12000' '"qa1115bDrainSlab"'
	@GameTest(template = HUGE, timeoutTicks = 12000, batch = "qa1115bDrainSlab")
	public void aWaterloggedSlabBesideTheBoxIsKeptNotBuried(GameTestHelper helper) {
		prepare(helper);
		fill(helper, 0, 29, 2, 3, 0, 29, Blocks.STONE);
		fill(helper, 6, 10, 2, 3, 6, 10, Blocks.WATER);
		BlockPos slab = new BlockPos(11, 3, 8);
		helper.setBlock(slab, Blocks.OAK_SLAB.defaultBlockState().setValue(net.minecraft.world.level.block.SlabBlock.WATERLOGGED, true));
		QaBuilderTerrain1102GameTests.Run r = start(helper, 4, new BlockPos(6, 2, 6));
		watchDeath(helper, r);
		int[] doneAt = watchDone(helper, r);
		helper.succeedWhen(() -> {
			assertFinished(helper, r);
			waitAfterDone(helper, doneAt, 200);
			assertNoFluid(helper, r);
			BlockState s = helper.getBlockState(slab);
			helper.assertTrue(s.is(Blocks.OAK_SLAB), "the player's slab beside the hut is gone: " + s);
			materialsAddUp(helper, r);
			LOGGER.info("qa 1115b drain slab: dry, slab kept as {} at tick {}", s, helper.getTick());
		});
	}

	// ---------------------------------------------------------------------------------------------- water plants

	/**
	 * The test hut on the bed of a pond three deep its footprint, so its room is wholly under water, with seagrass and
	 * tall seagrass on the bed, a kelp three tall and a bubble column over soul sand: the finished hut has no water, no
	 * water plant and no bubble column in its box, at most one kelp item per kelp block and one seagrass per seagrass
	 * block lies loose (none made from nothing), and the hut's materials add up.
	 */
	//$ gametest_ticks_batch HUGE '12000' '"qa1115bDrainPlants"'
	@GameTest(template = HUGE, timeoutTicks = 12000, batch = "qa1115bDrainPlants")
	public void waterPlantsAndABubbleColumnInASunkRoomGoWithoutWaterOrDuplicates(GameTestHelper helper) {
		prepare(helper);
		fill(helper, 0, 29, 2, 4, 0, 29, Blocks.STONE);
		fill(helper, 6, 10, 2, 4, 6, 10, Blocks.WATER);
		helper.setBlock(new BlockPos(7, 2, 7), Blocks.SEAGRASS);
		helper.setBlock(new BlockPos(9, 2, 7), Blocks.SEAGRASS);
		helper.setBlock(new BlockPos(7, 2, 9), Blocks.TALL_SEAGRASS.defaultBlockState().setValue(DoublePlantBlock.HALF, DoubleBlockHalf.LOWER));
		helper.setBlock(new BlockPos(7, 3, 9), Blocks.TALL_SEAGRASS.defaultBlockState().setValue(DoublePlantBlock.HALF, DoubleBlockHalf.UPPER));
		helper.setBlock(new BlockPos(8, 2, 8), Blocks.KELP_PLANT);
		helper.setBlock(new BlockPos(8, 3, 8), Blocks.KELP_PLANT);
		helper.setBlock(new BlockPos(8, 4, 8), Blocks.KELP);
		helper.setBlock(new BlockPos(9, 1, 9), Blocks.SOUL_SAND);
		for (int y = 2; y <= 4; y++) {
			helper.setBlock(new BlockPos(9, y, 9), Blocks.BUBBLE_COLUMN);
		}
		QaBuilderTerrain1102GameTests.Run r = start(helper, 5, new BlockPos(6, 2, 6));
		watchDeath(helper, r);
		helper.succeedWhen(() -> {
			assertFinished(helper, r);
			assertNoFluid(helper, r);
			List<String> left = new ArrayList<>();
			BoundingBox box = r.plan().bounds();
			for (BlockPos p : BlockPos.betweenClosed(box.minX(), box.minY(), box.minZ(), box.maxX(), box.maxY(), box.maxZ())) {
				BlockState s = r.level().getBlockState(p);
				if (s.is(Blocks.SEAGRASS) || s.is(Blocks.TALL_SEAGRASS) || s.is(Blocks.KELP) || s.is(Blocks.KELP_PLANT) || s.is(Blocks.BUBBLE_COLUMN)) {
					left.add(helper.relativePos(p).toShortString() + "=" + s.getBlock().getDescriptionId());
				}
			}
			helper.assertTrue(left.isEmpty(), "water plants or bubbles left in the finished hut: " + left);
			int kelp = loose(helper, r, Items.KELP);
			int seagrass = loose(helper, r, Items.SEAGRASS);
			helper.assertTrue(kelp <= 3, kelp + " kelp loose from 3 kelp blocks: made from nothing");
			helper.assertTrue(seagrass <= 3, seagrass + " seagrass loose from 2 seagrass and 1 tall seagrass: made from nothing");
			materialsAddUp(helper, r);
			LOGGER.info("qa 1115b drain plants: dry at tick {}, kelp {}, seagrass {}", helper.getTick(), kelp, seagrass);
		});
	}

	// ---------------------------------------------------------------------------------------------- wholly under water

	/**
	 * A lake five deep (x 6-18, z 6-18, y 2-6) cut into a stone plateau five high, the test hut clicked onto its bed
	 * (x 10-14, z 10-14, y 2-5), so water stands over its roof as well as round it: the finished hut is dry and as drawn,
	 * the builder alive, and the lake three or more blocks from the box is still all water sources.
	 */
	//$ gametest_ticks_batch HUGE '16000' '"qa1115bDrainUnder"'
	@GameTest(template = HUGE, timeoutTicks = 16000, batch = "qa1115bDrainUnder")
	public void aHutWhollyUnderALakeEndsDry(GameTestHelper helper) {
		prepare(helper);
		fill(helper, 0, 29, 2, 6, 0, 29, Blocks.STONE);
		fill(helper, 6, 18, 2, 6, 6, 18, Blocks.WATER);
		QaBuilderTerrain1102GameTests.Run r = start(helper, 7, new BlockPos(10, 2, 10));
		watchDeath(helper, r);
		helper.succeedWhen(() -> {
			assertFinished(helper, r);
			assertNoFluid(helper, r);
			List<String> touched = new ArrayList<>();
			for (int x = 6; x <= 18; x++) {
				for (int z = 6; z <= 18; z++) {
					if (x >= 7 && x <= 17 && z >= 7 && z <= 17) {
						continue; // the box, its plugs and the ring round it
					}
					for (int y = 2; y <= 6; y++) {
						BlockState s = helper.getBlockState(new BlockPos(x, y, z));
						if (!s.is(Blocks.WATER) || !s.getFluidState().isSource()) {
							touched.add("(" + x + "," + y + "," + z + ")=" + s.getBlock().getDescriptionId());
						}
					}
				}
			}
			helper.assertTrue(touched.isEmpty(), touched.size() + " spot(s) of the lake 3+ blocks from the hut changed: " + touched.stream().limit(12).toList());
			LOGGER.info("qa 1115b drain under: dry under the lake at tick {}", helper.getTick());
		});
	}
}
