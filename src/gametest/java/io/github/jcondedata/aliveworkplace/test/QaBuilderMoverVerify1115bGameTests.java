package io.github.jcondedata.aliveworkplace.test;

import static io.github.jcondedata.aliveworkplace.test.QaBuilderTerrain1102GameTests.assertFinished;
import static io.github.jcondedata.aliveworkplace.test.QaBuilderTerrain1102GameTests.prepare;
import static io.github.jcondedata.aliveworkplace.test.QaBuilderTerrain1102GameTests.reload;
import static io.github.jcondedata.aliveworkplace.test.QaBuilderTerrain1102GameTests.watchDeath;
import static io.github.jcondedata.aliveworkplace.test.QaBuilderWater1114bGameTests.fill;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import io.github.jcondedata.aliveworkplace.build.BuildPlan;
import io.github.jcondedata.aliveworkplace.build.BuildSite;
import io.github.jcondedata.aliveworkplace.move.MoveSite;
import io.github.jcondedata.aliveworkplace.move.MoveSites;
import io.github.jcondedata.aliveworkplace.move.MoverWork;
import io.github.jcondedata.aliveworkplace.work.Jobs;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.village.poi.PoiTypes;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.npc.VillagerProfession;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.DoublePlantBlock;
import net.minecraft.world.level.block.SlabBlock;
import net.minecraft.world.level.block.SnowLayerBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.level.block.state.properties.SlabType;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import net.minecraft.core.Vec3i;

/**
 * QA (2026-11-15, qa-lane-b-1115-0938) of lane-a's fixes B1272, B1264, B1259, B1274 and B1275, written from their ROADMAP
 * text and the wiki (builder.md "Stages", moving-buildings.md), not the code; the cases the earlier QA classes
 * (QaBuilderDrainEdges1115b*, QaMoveSpotCancel1114g*, QaMoverCrateDrops1114g*, QaMoverCrateFixes1115c*) leave out:
 * <ul>
 * <li>B1272: a shut basin room sunk in a pond with its drawn pool beside a wall, in the wall line on the box's edge, or
 * over a gravel bed; a drawn hole (air) in its floor over a sand bed; a drawn waterlogged slab in its floor; and the
 * pool basin saved and loaded mid-CLEAR. Each ends dry but its pool. (Over a water pocket in the bed:
 * QaBuilderMoverVerify1115bBugTests, fails.)</li>
 * <li>B1264: called off with exactly one block up at the new spot S (kept, with the put-back line, then free once that
 * block is down), and called off before the Mover's first swing (S free at once).</li>
 * <li>B1274: every count of snow layers 1-7 and a snow block, as the crate's drop works them out, and a full move with 1
 * layer on his crate. (8 layers: QaBuilderMoverVerify1115bBugTests, fails.)</li>
 * <li>B1275: large fern, lilac, peony, rose bush, pitcher plant, tall grass and small dripleaf, each half, 64 drops each;
 * and a full move with a large fern's upper half on his crate.</li>
 * <li>B1259: a lectern or a composter where his crate would have moved to doesn't take his job site, and a librarian
 * living in the same house still gets his moved lectern.</li>
 * </ul>
 */
public class QaBuilderMoverVerify1115bGameTests implements FabricGameTest {
	private static final String HUGE = "aliveworkplace_test:huge_area";
	private static final String BIG = "aliveworkplace_test:big_area";
	static final org.slf4j.Logger LOGGER = org.slf4j.LoggerFactory.getLogger("aliveworkplace_test");

	/** A player sentence from en_us.json, its {@code %s} filled in order. */
	static String lang(String key, Object... args) {
		try (InputStream in = QaBuilderMoverVerify1115bGameTests.class.getResourceAsStream("/assets/aliveworkplace/lang/en_us.json")) {
			if (in == null) {
				throw new GameTestAssertException("en_us.json not on the classpath");
			}
			JsonObject o = JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonObject();
			if (!o.has(key)) {
				throw new GameTestAssertException("no " + key + " in en_us.json");
			}
			return String.format(o.get(key).getAsString(), args);
		} catch (java.io.IOException e) {
			throw new GameTestAssertException("en_us.json: " + e);
		}
	}

	// =========================================================================================== B1272: the drawn pool

	/** What the basin's floor holds at one spot instead of cobblestone. */
	record Floor(int x, int z, BlockState state) { }

	/**
	 * Scans a 5x4x5 basin (QaBuilderDrainEdges1115bGameTests' room: cobblestone floor, walls two high, roof; one shut room)
	 * into a fresh blueprint, its floor holding {@code floor}'s block at that one spot.
	 */
	static ResourceLocation basin(GameTestHelper helper, Floor floor, String name) {
		ServerLevel level = helper.getLevel();
		BlockPos at = new BlockPos(20, 12, 20);
		fill(helper, 20, 24, 11, 11, 20, 24, Blocks.STONE);
		for (int x = 0; x < 5; x++) {
			for (int z = 0; z < 5; z++) {
				boolean edge = x == 0 || z == 0 || x == 4 || z == 4;
				boolean here = x == floor.x() && z == floor.z();
				helper.setBlock(at.offset(x, 0, z), here ? floor.state() : Blocks.COBBLESTONE.defaultBlockState());
				helper.setBlock(at.offset(x, 1, z), edge ? Blocks.COBBLESTONE : Blocks.AIR);
				helper.setBlock(at.offset(x, 2, z), edge ? Blocks.COBBLESTONE : Blocks.AIR);
				helper.setBlock(at.offset(x, 3, z), Blocks.COBBLESTONE);
			}
		}
		ResourceLocation id = ResourceLocation.fromNamespaceAndPath("aliveworkplace_test", "qav1115b_" + name + "_" + Long.toHexString(level.getGameTime()));
		StructureTemplate t = level.getStructureManager().getOrCreate(id);
		t.fillFromWorld(level, helper.absolutePos(at), new Vec3i(5, 4, 5), false, Blocks.STRUCTURE_VOID);
		fill(helper, 20, 24, 11, 15, 20, 24, Blocks.AIR);
		return id;
	}

	/**
	 * The ground: stone x/z 0-29 up to y 5, the pond x/z 6-10 three deep (y 3-5) over a bed at y 2 of {@code bed}, so the
	 * basin clicked with its corner at (6, 3, 6) has its box at y 3-6 and its whole room under the water line.
	 */
	private static void pond(GameTestHelper helper, Block bed) {
		fill(helper, 0, 29, 2, 5, 0, 29, Blocks.STONE);
		fill(helper, 6, 10, 2, 2, 6, 10, bed);
		fill(helper, 6, 10, 3, 5, 6, 10, Blocks.WATER);
	}

	private static final BlockPos ORIGIN = new BlockPos(6, 3, 6);

	/** Logs the box's wet spots on the first tick after CLEAR (for a failure's story). */
	private static void logFirstTickAfterClear(GameTestHelper helper, QaBuilderTerrain1102GameTests.Run r, BlockPos keep, String what) {
		boolean[] logged = {false};
		helper.onEachTick(() -> {
			BuildSite now = r.site();
			if (!logged[0] && now != null && now.stage() != BuildPlan.Stage.CLEAR) {
				logged[0] = true;
				List<String> wet = QaBuilderDrainEdges1115bGameTests.wet(helper, r, keep);
				LOGGER.info("qa v1115b {}: first tick of {}: {} wet spot(s) besides the pool: {}", what, now.stage(), wet.size(), wet);
			}
		});
	}

	/** Finished as drawn, the pool at {@code pool} (null: none) a water source, every other spot of the box dry, materials adding up. */
	private static void endsDryButThePool(GameTestHelper helper, QaBuilderTerrain1102GameTests.Run r, BlockPos pool, String what) {
		assertFinished(helper, r);
		if (pool != null) {
			BlockState there = r.level().getBlockState(pool);
			helper.assertTrue(there.getFluidState().isSource(), what + ": the drawing's pool lost its water: " + there);
		}
		List<String> wet = QaBuilderDrainEdges1115bGameTests.wet(helper, r, pool);
		helper.assertTrue(wet.isEmpty(), what + ": water left in the finished basin besides its pool: " + wet);
		QaBuilderDrainEdges1115bGameTests.materialsAddUp(helper, r);
	}

	/** One drawn-pool case: the basin with {@code floor}, on a pond over {@code bed}, the pool kept (if {@code pool}) and the rest dry. */
	static void poolCase(GameTestHelper helper, Floor floor, Block bed, boolean pool, String name, Runnable more) {
		prepare(helper);
		ResourceLocation id = basin(helper, floor, name);
		pond(helper, bed);
		if (more != null) {
			more.run();
		}
		QaBuilderTerrain1102GameTests.Run r = QaBuilderDrainEdges1115bGameTests.startWith(helper, 6, ORIGIN, id);
		BlockPos at = helper.absolutePos(ORIGIN.offset(floor.x(), 0, floor.z()));
		if (pool) {
			helper.assertTrue(r.plan().targets().containsKey(at) && !r.plan().targets().get(at).getFluidState().isEmpty(),
				"setup: the drawing has no pool at " + at + ": " + r.plan().targets().get(at));
		}
		watchDeath(helper, r);
		logFirstTickAfterClear(helper, r, pool ? at : null, name);
		helper.succeedWhen(() -> endsDryButThePool(helper, r, pool ? at : null, name));
	}

	/** The pool in the floor's corner inside the walls (beside two walls), not in its middle: the room ends dry but the pool. */
	//$ gametest_ticks_batch HUGE '12000' '"qaV1115bPoolByWall"'
	@GameTest(template = HUGE, timeoutTicks = 12000, batch = "qaV1115bPoolByWall")
	public void aDrawnPoolBesideTheWallsOfASunkRoomKeepsItsWaterAndTheRestEndsDry(GameTestHelper helper) {
		poolCase(helper, new Floor(1, 1, Blocks.WATER.defaultBlockState()), Blocks.STONE, true, "poolbywall", null);
	}

	/**
	 * The pool in the floor's outer ring, under a wall (x 0, z 2), so it sits on the box's side face as well as its
	 * bottom, against the pond outside: the room above it is still shut, so it ends dry but the pool.
	 */
	//$ gametest_ticks_batch HUGE '12000' '"qaV1115bPoolOnEdge"'
	@GameTest(template = HUGE, timeoutTicks = 12000, batch = "qaV1115bPoolOnEdge")
	public void aDrawnPoolUnderTheWallOnTheBoxsEdgeKeepsItsWaterAndTheRoomEndsDry(GameTestHelper helper) {
		poolCase(helper, new Floor(0, 2, Blocks.WATER.defaultBlockState()), Blocks.STONE, true, "poolonedge", null);
	}

	/** The pool in the middle of the floor, the pond's bed gravel (solid, lets no water through): dry but the pool. */
	//$ gametest_ticks_batch HUGE '12000' '"qaV1115bPoolGravel"'
	@GameTest(template = HUGE, timeoutTicks = 12000, batch = "qaV1115bPoolGravel")
	public void aDrawnPoolOverAGravelBedKeepsItsWaterAndTheRestEndsDry(GameTestHelper helper) {
		poolCase(helper, new Floor(2, 2, Blocks.WATER.defaultBlockState()), Blocks.GRAVEL, true, "poolgravel", null);
	}

	/**
	 * A one-block hole drawn in the middle of the floor (air), the pond's bed sand: the hole is part of the shut room, so
	 * the room and the hole end dry, and no sand falls in.
	 */
	//$ gametest_ticks_batch HUGE '12000' '"qaV1115bHoleSand"'
	@GameTest(template = HUGE, timeoutTicks = 12000, batch = "qaV1115bHoleSand")
	public void aDrawnHoleInTheFloorOverASandBedEndsDry(GameTestHelper helper) {
		poolCase(helper, new Floor(2, 2, Blocks.AIR.defaultBlockState()), Blocks.SAND, false, "holesand", null);
	}

	/** A waterlogged bottom slab drawn in the middle of the floor: the room ends dry (the slab's own water aside). */
	//$ gametest_ticks_batch HUGE '12000' '"qaV1115bWetSlab"'
	@GameTest(template = HUGE, timeoutTicks = 12000, batch = "qaV1115bWetSlab")
	public void aDrawnWaterloggedSlabInTheFloorOfASunkRoomLeavesTheRoomDry(GameTestHelper helper) {
		BlockState slab = Blocks.COBBLESTONE_SLAB.defaultBlockState().setValue(SlabBlock.TYPE, SlabType.BOTTOM).setValue(BlockStateProperties.WATERLOGGED, true);
		prepare(helper);
		ResourceLocation id = basin(helper, new Floor(2, 2, slab), "wetslab");
		pond(helper, Blocks.STONE);
		QaBuilderTerrain1102GameTests.Run r = QaBuilderDrainEdges1115bGameTests.startWith(helper, 6, ORIGIN, id);
		BlockPos at = helper.absolutePos(ORIGIN.offset(2, 0, 2));
		watchDeath(helper, r);
		logFirstTickAfterClear(helper, r, at, "wetslab");
		helper.succeedWhen(() -> {
			assertFinished(helper, r);
			List<String> wet = QaBuilderDrainEdges1115bGameTests.wet(helper, r, at);
			helper.assertTrue(wet.isEmpty(), "water left in the finished basin besides its waterlogged slab: " + wet);
		});
	}

	/**
	 * The pool basin in the middle of the pond, six stone blocks on the bed inside its room (a fixed random) so CLEAR has
	 * digging to do, saved and loaded (sites and builder) once CLEAR dug some but not all: ends dry but the pool.
	 */
	//$ gametest_ticks_batch HUGE '16000' '"qaV1115bPoolReload"'
	@GameTest(template = HUGE, timeoutTicks = 16000, batch = "qaV1115bPoolReload")
	public void aDrawnPoolBasinSavedAndLoadedMidClearKeepsItsPoolAndEndsDry(GameTestHelper helper) {
		prepare(helper);
		ResourceLocation id = basin(helper, new Floor(2, 2, Blocks.WATER.defaultBlockState()), "poolreload");
		pond(helper, Blocks.STONE);
		RandomSource random = RandomSource.create(11152L);
		Set<BlockPos> stones = new HashSet<>();
		while (stones.size() < 6) {
			// inside the room (x/z 7-9, y 4-5), never the pool's own spot (8, 3, 8)
			stones.add(helper.absolutePos(new BlockPos(7 + random.nextInt(3), 4 + random.nextInt(2), 7 + random.nextInt(3))));
		}
		for (BlockPos p : stones) {
			helper.getLevel().setBlockAndUpdate(p, Blocks.STONE.defaultBlockState());
		}
		QaBuilderTerrain1102GameTests.Run r = QaBuilderDrainEdges1115bGameTests.startWith(helper, 6, ORIGIN, id);
		BlockPos pool = helper.absolutePos(new BlockPos(8, 3, 8));
		watchDeath(helper, r);
		int[] reloadedAt = {-1};
		helper.onEachTick(() -> {
			BuildSite s = r.site();
			if (reloadedAt[0] >= 0 || s == null || s.stage() != BuildPlan.Stage.CLEAR) {
				return;
			}
			long left = stones.stream().filter(p -> helper.getLevel().getBlockState(p).is(Blocks.STONE)).count();
			if (left > 0 && left < stones.size()) {
				r.builder()[0] = reload(helper, r.level(), r.builder()[0]);
				reloadedAt[0] = (int) helper.getTick();
				LOGGER.info("qa v1115b pool reload: saved and loaded mid-CLEAR at tick {}, {} of {} stones left", helper.getTick(), left, stones.size());
			}
		});
		logFirstTickAfterClear(helper, r, pool, "poolreload");
		helper.succeedWhen(() -> {
			helper.assertTrue(reloadedAt[0] >= 0, "setup: never saved and loaded mid-CLEAR (stage " + (r.site() == null ? "done" : r.site().stage()) + ")");
			endsDryButThePool(helper, r, pool, "pool basin reloaded mid-CLEAR");
		});
	}

	// =========================================================================================== B1264: called off early

	/**
	 * Called off with exactly one block of A up at S: the Mover is still to take that block down, so S is kept (the
	 * put-back line from en_us.json, nothing taken); once that one block is down again, S is taken by a builder.
	 */
	//$ gametest_ticks_batch BIG '300' '"qaV1115bOneAtS"'
	@GameTest(template = BIG, timeoutTicks = 300, batch = "qaV1115bOneAtS")
	public void calledOffWithOneBlockUpAtSKeepsSUntilItIsDown(GameTestHelper helper) {
		Leftovers.clear(helper);
		QaMoveSpotBuild1114bGameTests.Setup s = QaMoveSpotBuild1114bGameTests.setup(helper);
		helper.runAfterDelay(2, () -> {
			ServerLevel level = s.v().level();
			int[] before = QaMoveSpotSaves1111eGameTests.count(helper);
			MoveSite site = QaMoveSpotBuild1114bGameTests.begin(s.v(), QaMoveSpotSaves1111eGameTests.planA(helper, s.v()));
			QaMoveSpotBuild1114bGameTests.stepUntil(level, site, m -> QaMoveSpotSaves1111eGameTests.count(helper)[1] >= 1);
			int atS = QaMoveSpotSaves1111eGameTests.count(helper)[1];
			helper.assertTrue(atS == 1, "setup: " + atS + " of A's blocks at S after the first one went up (stage " + site.stage() + "), not 1");
			helper.assertTrue(MoveSites.get(level).cancel(level, s.v().a()), "setup: A's move couldn't be called off");
			String back = lang("message.aliveworkplace.assign.move_spot_back", QaMoveSpotBuild1114bGameTests.hut());
			QaMoveSpotBuild1114bGameTests.Hand h = QaMoveSpotBuild1114bGameTests.hand(helper, s, 11, 3);
			helper.assertTrue(!h.taken() && h.said().equals(List.of(back)),
				"called off with one block at S: expected refused with exactly \"" + back + "\"; taken " + h.taken() + ", said " + h.said());
			for (int i = 0; i < 2000 && QaMoveSpotSaves1111eGameTests.count(helper)[1] > 0 && site.stage() != MoveSite.Stage.DONE; i++) {
				MoveSites.get(level).step(level, site);
			}
			helper.assertTrue(QaMoveSpotSaves1111eGameTests.count(helper)[1] == 0, "the one block at S was never taken down (stage " + site.stage() + ")");
			QaMoveSpotBuild1114bGameTests.assertTaken(helper, QaMoveSpotBuild1114bGameTests.hand(helper, s, 11, 3), "its one block at S down again, a hut on S");
			QaMoveSpotBuild1114bGameTests.stepUntil(level, site, m -> false);
			String wrong = QaMoveSpotSaves1111eGameTests.backWhole(helper, s.v(), before);
			helper.assertTrue(wrong == null, wrong);
			helper.succeed();
		});
	}

	/**
	 * Called off at once, before the Mover's first swing (nothing of A down, nothing at S): S is taken by a builder
	 * straight away, never the put-back line, and A still stands whole when the move is over.
	 */
	//$ gametest_ticks_batch BIG '300' '"qaV1115bNoSwing"'
	@GameTest(template = BIG, timeoutTicks = 300, batch = "qaV1115bNoSwing")
	public void calledOffBeforeTheFirstSwingFreesSAtOnce(GameTestHelper helper) {
		Leftovers.clear(helper);
		QaMoveSpotBuild1114bGameTests.Setup s = QaMoveSpotBuild1114bGameTests.setup(helper);
		helper.runAfterDelay(2, () -> {
			ServerLevel level = s.v().level();
			int[] before = QaMoveSpotSaves1111eGameTests.count(helper);
			MoveSite site = QaMoveSpotBuild1114bGameTests.begin(s.v(), QaMoveSpotSaves1111eGameTests.planA(helper, s.v()));
			helper.assertTrue(MoveSites.get(level).cancel(level, s.v().a()), "setup: A's move couldn't be called off");
			String back = lang("message.aliveworkplace.assign.move_spot_back", QaMoveSpotBuild1114bGameTests.hut());
			QaMoveSpotBuild1114bGameTests.Hand h = QaMoveSpotBuild1114bGameTests.hand(helper, s, 11, 3);
			helper.assertTrue(!h.said().contains(back), "called off before any swing, yet told the Mover is still taking it back down at S: " + h.said());
			QaMoveSpotBuild1114bGameTests.assertTaken(helper, h, "called off before any swing, a hut on S");
			QaMoveSpotBuild1114bGameTests.stepUntil(level, site, m -> false);
			String wrong = QaMoveSpotSaves1111eGameTests.backWhole(helper, s.v(), before);
			helper.assertTrue(wrong == null, wrong);
			helper.succeed();
		});
	}

	// =========================================================================================== B1274: snow

	static int count(List<ItemStack> stacks, Item item) {
		int n = 0;
		for (ItemStack st : stacks) {
			n += st.is(item) ? st.getCount() : 0;
		}
		return n;
	}

	static int total(List<ItemStack> stacks) {
		int n = 0;
		for (ItemStack st : stacks) {
			n += st.getCount();
		}
		return n;
	}

	/**
	 * Every count of snow layers 1 to 7 drops as that many snow (layers) and nothing else; a snow block as one snow block.
	 * (8 layers: QaBuilderMoverVerify1115bBugTests, fails.)
	 */
	//$ gametest_batch HUGE '"qaV1115bSnowCounts"'
	@GameTest(template = HUGE, batch = "qaV1115bSnowCounts")
	public void snowLayersOneToSevenAndASnowBlockDropAsThemselves(GameTestHelper helper) {
		Leftovers.clear(helper);
		ServerLevel level = helper.getLevel();
		BlockPos pos = helper.absolutePos(new BlockPos(3, 2, 3));
		StringBuilder bad = new StringBuilder();
		for (int n = 1; n <= 7; n++) {
			List<ItemStack> drops = QaMoverCrateFixes1115cGameTests.itself(level, pos, Blocks.SNOW.defaultBlockState().setValue(SnowLayerBlock.LAYERS, n));
			if (count(drops, Items.SNOW) != n || total(drops) != n) {
				bad.append(n).append(" layer(s): ").append(drops).append("; ");
			}
		}
		List<ItemStack> block = QaMoverCrateFixes1115cGameTests.itself(level, pos, Blocks.SNOW_BLOCK.defaultBlockState());
		if (count(block, Items.SNOW_BLOCK) != 1 || total(block) != 1) {
			bad.append("a snow block: ").append(block).append("; ");
		}
		helper.assertTrue(bad.isEmpty(), "snow not dropped as itself: " + bad);
		helper.succeed();
	}

	static void snowMove(GameTestHelper helper, int layers, String name) {
		ServerLevel level = helper.getLevel();
		MoverGameTests.Setup s = MoverCrateNoRoomB1234GameTests.crateInside(helper, name);
		QaMoverCrateVerify1114fGameTests.place(helper, Blocks.SNOW.defaultBlockState().setValue(SnowLayerBlock.LAYERS, layers));
		helper.runAfterDelay(2, () -> {
			BlockState snow = level.getBlockState(helper.absolutePos(new BlockPos(4, 3, 4)));
			helper.assertTrue(snow.is(Blocks.SNOW) && snow.getValue(SnowLayerBlock.LAYERS) == layers, "setup: the snow is now " + snow);
			String what = layers + " snow layer(s) on his crate";
			QaMoverCrateQa1114c4GameTests.toTheEnd(helper, s, () -> { }, what);
			MoverCrateNoRoomB1234GameTests.assertExactly(helper, Items.SNOWBALL, 0, what + ": snowballs");
			MoverCrateNoRoomB1234GameTests.assertExactly(helper, Items.SNOW, layers, what);
			helper.assertTrue(QaMoverCrateHolds1114cGameTests.standing(helper, Blocks.SNOW) == 0, what + ": snow left standing");
			helper.succeed();
		});
	}

	/** One snow layer on his crate: one snow, no snowball (the boundary under B1274's four). */
	//$ gametest_batch HUGE '"qaV1115bSnowOne"'
	@GameTest(template = HUGE, batch = "qaV1115bSnowOne")
	public void oneSnowLayerOnHisCrateIsOneSnow(GameTestHelper helper) {
		snowMove(helper, 1, "qav1115bsnow1");
	}

	// =========================================================================================== B1275: two-block plants

	/**
	 * Each two-block plant, each half asked with its other half standing next to it, 64 times as the crate's drop does:
	 * always exactly the plant's one item, never seeds, two ferns or nothing by its loot's roll.
	 */
	//$ gametest_batch HUGE '"qaV1115bTallPlants"'
	@GameTest(template = HUGE, batch = "qaV1115bTallPlants")
	public void everyTwoBlockPlantEitherHalfIsAlwaysItsOneItem(GameTestHelper helper) {
		Leftovers.clear(helper);
		ServerLevel level = helper.getLevel();
		Block[] plants = {Blocks.LARGE_FERN, Blocks.LILAC, Blocks.PEONY, Blocks.ROSE_BUSH, Blocks.PITCHER_PLANT, Blocks.TALL_GRASS, Blocks.SUNFLOWER};
		List<String> wrong = new ArrayList<>();
		int col = 0;
		for (Block plant : plants) {
			for (DoubleBlockHalf half : DoubleBlockHalf.values()) {
				BlockPos lower = helper.absolutePos(new BlockPos(1 + col % 12 * 2, 2, 1 + col / 12 * 2));
				col++;
				BlockState lo = plant.defaultBlockState().setValue(DoublePlantBlock.HALF, DoubleBlockHalf.LOWER);
				BlockState up = plant.defaultBlockState().setValue(DoublePlantBlock.HALF, DoubleBlockHalf.UPPER);
				// the other half stands; the asked half is where it would be
				if (half == DoubleBlockHalf.UPPER) {
					level.setBlock(lower, lo, Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE);
				} else {
					level.setBlock(lower.above(), up, Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE);
				}
				int bad = 0;
				String seen = "";
				for (int i = 0; i < 64; i++) {
					List<ItemStack> drops = QaMoverCrateFixes1115cGameTests.itself(level, half == DoubleBlockHalf.UPPER ? lower.above() : lower,
						half == DoubleBlockHalf.UPPER ? up : lo);
					if (total(drops) != 1 || count(drops, plant.asItem()) != 1) {
						bad++;
						seen = drops.toString();
					}
				}
				if (bad > 0) {
					wrong.add(plant + " " + half + ": " + bad + " of 64 wrong, e.g. " + seen);
				}
			}
		}
		helper.assertTrue(wrong.isEmpty(), "two-block plants not dropped as their one item: " + wrong);
		helper.succeed();
	}

	/**
	 * A large fern whose UPPER half lands on his crate (its lower half put up first, right under the crate's spot), a full
	 * move: one large fern, no half standing, no seeds or ferns (large fern's loot is seeds by chance, or two ferns).
	 */
	//$ gametest_batch HUGE '"qaV1115bFernUpper"'
	@GameTest(template = HUGE, batch = "qaV1115bFernUpper")
	public void aLargeFernWhoseUpperHalfLandsOnHisCrateIsOneLargeFern(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		MoverGameTests.Setup s = MoverCrateNoRoomB1234GameTests.crateInside(helper, "qav1115bfern");
		BlockPos upper = helper.absolutePos(new BlockPos(4, 3, 4));
		QaMoverCrateVerify1114fGameTests.place(helper, Blocks.AIR.defaultBlockState());
		level.setBlock(upper.below(), Blocks.LARGE_FERN.defaultBlockState().setValue(DoublePlantBlock.HALF, DoubleBlockHalf.LOWER), Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE);
		level.setBlock(upper, Blocks.LARGE_FERN.defaultBlockState().setValue(DoublePlantBlock.HALF, DoubleBlockHalf.UPPER), Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE);
		helper.runAfterDelay(2, () -> {
			helper.assertTrue(QaMoverCrateHolds1114cGameTests.standing(helper, Blocks.LARGE_FERN) == 2, "setup: no large fern");
			QaMoverCrateQa1114c4GameTests.toTheEnd(helper, s, () -> { }, "a large fern's upper half on his crate");
			helper.assertTrue(QaMoverCrateHolds1114cGameTests.standing(helper, Blocks.LARGE_FERN) == 0, "a half of the large fern left standing");
			MoverCrateNoRoomB1234GameTests.assertExactly(helper, Items.LARGE_FERN, 1, "the large fern");
			MoverCrateNoRoomB1234GameTests.assertExactly(helper, Items.WHEAT_SEEDS, 0, "seeds from the large fern");
			MoverCrateNoRoomB1234GameTests.assertExactly(helper, Items.FERN, 0, "ferns from the large fern");
			helper.succeed();
		});
	}

	// =========================================================================================== B1259: his job site

	/** His crate stays while the house moves 2 south-east; {@code block} is put where the crate would have gone once the house is down. */
	private static void blockAtCrateSpot(GameTestHelper helper, Block block, String name) {
		ServerLevel level = helper.getLevel();
		MoverGameTests.Setup s = MoverCrateNoRoomB1234GameTests.crateInside(helper, name);
		BlockPos crate = helper.absolutePos(QaMoverCrateDrop1114eGameTests.OWN_CRATE);
		BlockPos moved = crate.offset(2, 0, 2);
		helper.runAfterDelay(2, () -> {
			MoveSite site = MoverCrateNoRoomB1234GameTests.work(level, s, x -> x.stage() != MoveSite.Stage.DISMANTLE);
			helper.assertTrue(level.getBlockState(moved).isAir(), "setup: the spot 2 south-east of his crate holds " + level.getBlockState(moved));
			level.setBlock(moved, block.defaultBlockState(), Block.UPDATE_ALL);
			site = MoverCrateNoRoomB1234GameTests.work(level, s, x -> false);
			helper.assertTrue(site.stage() == MoveSite.Stage.DONE, "the move ended " + site.stage());
			String job = s.mover().getBrain().getMemory(MemoryModuleType.JOB_SITE)
				.map(g -> level.getBlockState(g.pos()).getBlock() + " at " + g.pos().subtract(crate) + " from his crate").orElse("none");
			helper.assertTrue(MoverWork.hasCrate(level, s.mover()) && level.getBlockState(crate).is(io.github.jcondedata.aliveworkplace.registry.ModBlocks.MOVERS_CRATE),
				"with a " + block + " where his crate would have gone, the Mover's job site after the move is " + job + ", not his crate");
			helper.assertTrue(level.getBlockState(moved).is(block), "the " + block + " at the crate's would-be spot is now " + level.getBlockState(moved));
			helper.succeed();
		});
	}

	/** A lectern (a librarian's workstation) where his crate would have moved to: his crate stays his job site. */
	//$ gametest_batch HUGE '"qaV1115bLecternAtCrate"'
	@GameTest(template = HUGE, batch = "qaV1115bLecternAtCrate")
	public void aLecternWhereHisCrateWouldHaveMovedDoesNotTakeHisJobSite(GameTestHelper helper) {
		blockAtCrateSpot(helper, Blocks.LECTERN, "qav1115blectern");
	}

	/** A composter (a farmer's workstation) where his crate would have moved to: his crate stays his job site. */
	//$ gametest_batch HUGE '"qaV1115bComposterAtCrate"'
	@GameTest(template = HUGE, batch = "qaV1115bComposterAtCrate")
	public void aComposterWhereHisCrateWouldHaveMovedDoesNotTakeHisJobSite(GameTestHelper helper) {
		blockAtCrateSpot(helper, Blocks.COMPOSTER, "qav1115bcomposter");
	}

	/**
	 * A librarian whose lectern stands in the same house as the Mover's crate: the move ends with the librarian's job
	 * site the moved lectern (held for him, a librarian still), and the Mover's still his crate where it stood (B1259's
	 * fix leaves the Mover out of the residents, not everyone).
	 */
	//$ gametest_batch HUGE '"qaV1115bLibrarianToo"'
	@GameTest(template = HUGE, batch = "qaV1115bLibrarianToo")
	public void aLibrarianInTheHouseStillGetsHisMovedLecternWhileTheMoverKeepsHisCrate(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		MoverGameTests.Setup s = MoverCrateNoRoomB1234GameTests.crateInside(helper, "qav1115blibrarian");
		BlockPos lectern = helper.absolutePos(new BlockPos(5, 3, 6));
		level.setBlock(lectern, Blocks.LECTERN.defaultBlockState(), Block.UPDATE_ALL);
		Villager librarian = helper.spawn(EntityType.VILLAGER, new BlockPos(14, 2, 2));
		librarian.setNoAi(true);
		Jobs.employ(level, librarian, lectern, PoiTypes.LIBRARIAN, VillagerProfession.LIBRARIAN);
		librarian.setVillagerXp(1);
		BlockPos crate = helper.absolutePos(QaMoverCrateDrop1114eGameTests.OWN_CRATE);
		BlockPos movedLectern = lectern.offset(s.to().subtract(s.from()));
		helper.runAfterDelay(2, () -> {
			helper.assertTrue(librarian.getBrain().getMemory(MemoryModuleType.JOB_SITE).map(GlobalPos::pos).filter(lectern::equals).isPresent(),
				"setup: the librarian's job site isn't the lectern: " + librarian.getBrain().getMemory(MemoryModuleType.JOB_SITE));
			MoveSite site = MoverCrateNoRoomB1234GameTests.work(level, s, x -> false);
			helper.assertTrue(site.stage() == MoveSite.Stage.DONE, "the move ended " + site.stage());
			helper.assertTrue(level.getBlockState(movedLectern).is(Blocks.LECTERN), "no lectern at its moved spot " + helper.relativePos(movedLectern).toShortString()
				+ ": " + level.getBlockState(movedLectern));
			String job = librarian.getBrain().getMemory(MemoryModuleType.JOB_SITE).map(g -> helper.relativePos(g.pos()).toShortString()).orElse("none");
			helper.assertTrue(librarian.getBrain().getMemory(MemoryModuleType.JOB_SITE).map(GlobalPos::pos).filter(movedLectern::equals).isPresent(),
				"the librarian's job site after the move is " + job + ", not the moved lectern " + helper.relativePos(movedLectern).toShortString());
			helper.assertTrue(librarian.getVillagerData().getProfession() == VillagerProfession.LIBRARIAN, "the librarian lost his job");
			helper.assertTrue(level.getPoiManager().getFreeTickets(movedLectern) == 0, "the moved lectern is free to anyone, not held for the librarian");
			helper.assertTrue(MoverWork.hasCrate(level, s.mover()) && level.getBlockState(crate).is(io.github.jcondedata.aliveworkplace.registry.ModBlocks.MOVERS_CRATE),
				"the Mover lost his crate as his job site: " + s.mover().getBrain().getMemory(MemoryModuleType.JOB_SITE));
			helper.succeed();
		});
	}
}
