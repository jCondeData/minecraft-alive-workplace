package io.github.jcondedata.aliveworkplace.test;

import static io.github.jcondedata.aliveworkplace.test.QaBuilderTerrain1102GameTests.EXTRA;
import static io.github.jcondedata.aliveworkplace.test.QaBuilderTerrain1102GameTests.assertFinished;
import static io.github.jcondedata.aliveworkplace.test.QaBuilderTerrain1102GameTests.prepare;
import static io.github.jcondedata.aliveworkplace.test.QaBuilderTerrain1102GameTests.reload;
import static io.github.jcondedata.aliveworkplace.test.QaBuilderTerrain1102GameTests.watchDeath;
import static io.github.jcondedata.aliveworkplace.test.QaBuilderWater1114bGameTests.fill;

import io.github.jcondedata.aliveworkplace.blueprint.BlueprintData;
import io.github.jcondedata.aliveworkplace.blueprint.BlueprintItem;
import io.github.jcondedata.aliveworkplace.build.BuildPlan;
import io.github.jcondedata.aliveworkplace.build.BuildSite;
import io.github.jcondedata.aliveworkplace.build.BuildSiteManager;
import io.github.jcondedata.aliveworkplace.build.Builders;
import io.github.jcondedata.aliveworkplace.registry.ModBlocks;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Vec3i;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.Container;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;

/**
 * QA (2026-11-22, qa-lane-b-1122-1538) past B1294's fix, written from docs/wiki/builder.md "Stages": a shut room
 * with a pool drawn in its floor, sunk in a pond, ends dry but its pool over a water pocket in the bed shut in by
 * stone, "however wide the pocket is under the floor"; the water under the floor counts as a way in only when it
 * reaches past the box's footprint or up to its height. QaBuilderPocketDrain1115gGameTests/BugTests cover a pocket
 * one wide (four deep), two wide and L-shaped; these go further:
 * <ul>
 * <li>three wide, and the pocket filling the whole footprint exactly to its edge (the boundary from inside);</li>
 * <li>the same pocket one block past the footprint (the boundary from outside): it counts as a way in, so the build
 * is finished and the water past the box is never drained or dammed;</li>
 * <li>a pocket three wide saved and loaded mid-CLEAR, and turned a quarter, and mirrored and turned.</li>
 * </ul>
 * The basin, pond and helpers are QaBuilderPocketDrain1115gGameTests' (stone y 2-8, the pond x/z 6-10 y 6-8 over a
 * bed at y 5, the basin's box x/z 6-10 y 6-9, the drawn pool at (8, 6, 8)).
 */
public class QaPocketDrainWide1122bGameTests implements FabricGameTest {
	private static final String HUGE = "aliveworkplace_test:huge_area";
	static final org.slf4j.Logger LOGGER = org.slf4j.LoggerFactory.getLogger("aliveworkplace_test");

	private static final int TOP = 8;
	private static final int G = TOP + 1;
	private static final BlockPos ORIGIN = new BlockPos(6, 6, 6);
	private static final BlockPos POOL = new BlockPos(8, 6, 8);

	/** Water at each spot of the bed (y 5) x {@code x0..x1}, z {@code z0..z1}. */
	private static BlockPos[] bed(int x0, int x1, int z0, int z1) {
		List<BlockPos> out = new ArrayList<>();
		for (int x = x0; x <= x1; x++) {
			for (int z = z0; z <= z1; z++) {
				out.add(new BlockPos(x, 5, z));
			}
		}
		return out.toArray(BlockPos[]::new);
	}

	// ---------------------------------------------------------------------------------------------- wider pockets

	/** A pocket three wide (x 7-9) one deep under the pool and the solid floor either side: dry but the pool. */
	//$ gametest_ticks_batch HUGE '12000' '"qa1122bThreeWidePocket"'
	@GameTest(template = HUGE, timeoutTicks = 12000, batch = "qa1122bThreeWidePocket")
	public void aPoolOverAPocketThreeWideUnderTheFloorEndsDryButThePool(GameTestHelper helper) {
		QaBuilderPocketDrain1115gGameTests.poolOver(helper, "threewide",
			QaBuilderPocketDrain1115gGameTests.put(helper, Blocks.WATER.defaultBlockState(), bed(7, 9, 8, 8)));
	}

	/**
	 * A pocket under the whole footprint (x/z 6-10, y 5) and two deep under its middle: every spot under the box,
	 * reaching exactly to the footprint's edge and no further, shut in by stone. The boundary from inside: dry but the pool.
	 */
	//$ gametest_ticks_batch HUGE '12000' '"qa1122bFootprintPocket"'
	@GameTest(template = HUGE, timeoutTicks = 12000, batch = "qa1122bFootprintPocket")
	public void aPoolOverAPocketExactlyAsWideAsTheFootprintEndsDryButThePool(GameTestHelper helper) {
		BlockPos[] under = bed(6, 10, 6, 10);
		QaBuilderPocketDrain1115gGameTests.poolOver(helper, "footprint", () -> {
			QaBuilderPocketDrain1115gGameTests.put(helper, Blocks.WATER.defaultBlockState(), under).run();
			helper.setBlock(new BlockPos(8, 4, 8), Blocks.WATER);
		});
	}

	/**
	 * The boundary from outside: a pocket along z 8 from under the pool (x 8) to one block past the footprint (x 11),
	 * shut in by stone everywhere else. It reaches past the footprint, so it counts as a way in (the jetty's rule): the
	 * build is finished with its builder alive, and the spot past the box is still a water source, never drained or
	 * dammed (no dirt put there).
	 */
	//$ gametest_ticks_batch HUGE '12000' '"qa1122bPastPocket"'
	@GameTest(template = HUGE, timeoutTicks = 12000, batch = "qa1122bPastPocket")
	public void aPocketOneBlockPastTheFootprintCountsAsAWayInAndIsKept(GameTestHelper helper) {
		QaBuilderTerrain1102GameTests.Run r = sunk(helper, "past", QaBuilderPocketDrain1115gGameTests.put(helper, Blocks.WATER.defaultBlockState(),
			bed(8, 11, 8, 8)));
		BlockPos pool = helper.absolutePos(POOL);
		helper.succeedWhen(() -> {
			helper.assertTrue(r.died()[0] == null, "the builder died on the site: " + r.died()[0]);
			helper.assertTrue(r.site() == null, "still building: " + QaBuilderTerrain1102GameTests.state(helper, r));
			BlockState past = helper.getBlockState(new BlockPos(11, 5, 8));
			helper.assertTrue(past.is(Blocks.WATER) && past.getFluidState().isSource(), "the pocket's water past the footprint at (11, 5, 8) was drained or dammed: " + past);
			LOGGER.info("qa 1122b past: the water past the box kept; box wet spots besides the pool: {}",
				QaBuilderDrainEdges1115bGameTests.wet(helper, r, pool).size());
		});
	}

	// ---------------------------------------------------------------------------------------------- saved and loaded

	/** The 1115g basin with {@code pocket} carved in the bed and the builder hired (QaBuilderPocketDrain1115gGameTests' sunk). */
	private static QaBuilderTerrain1102GameTests.Run sunk(GameTestHelper helper, String name, Runnable pocket) {
		prepare(helper);
		ResourceLocation id = QaBuilderMoverVerify1115bGameTests.basin(helper,
			new QaBuilderMoverVerify1115bGameTests.Floor(2, 2, Blocks.WATER.defaultBlockState()), "p1122b" + name);
		fill(helper, 0, 29, 2, TOP, 0, 29, Blocks.STONE);
		fill(helper, 6, 10, TOP - 2, TOP, 6, 10, Blocks.WATER);
		pocket.run();
		QaBuilderTerrain1102GameTests.Run r = QaBuilderDrainEdges1115bGameTests.startWith(helper, G, ORIGIN, id);
		watchDeath(helper, r);
		return r;
	}

	private static void endsDryButThePool(GameTestHelper helper, QaBuilderTerrain1102GameTests.Run r, BlockPos pool, String what) {
		assertFinished(helper, r);
		BlockState there = r.level().getBlockState(pool);
		helper.assertTrue(there.is(Blocks.WATER) && there.getFluidState().isSource(), what + ": the drawing's pool lost its water: " + there);
		List<String> wet = QaBuilderDrainEdges1115bGameTests.wet(helper, r, pool);
		helper.assertTrue(wet.isEmpty(), what + ": water left in the finished box besides its pool: " + wet);
		QaBuilderDrainEdges1115bGameTests.materialsAddUp(helper, r);
		LOGGER.info("qa 1122b {}: finished dry but the pool, materials add up", what);
	}

	/**
	 * The pool basin over a pocket three wide (x 7-9) and two deep under the pool, six stone blocks in its room (a fixed
	 * random) so CLEAR has digging to do, saved and loaded (sites and builder) once CLEAR took some but not all: ends dry
	 * but the pool.
	 */
	//$ gametest_ticks_batch HUGE '16000' '"qa1122bWidePocketReload"'
	@GameTest(template = HUGE, timeoutTicks = 16000, batch = "qa1122bWidePocketReload")
	public void aPoolOverAPocketThreeWideSavedAndLoadedMidClearEndsDryButThePool(GameTestHelper helper) {
		RandomSource random = RandomSource.create(112215L);
		Set<BlockPos> stones = new HashSet<>();
		while (stones.size() < 6) {
			stones.add(helper.absolutePos(new BlockPos(7 + random.nextInt(3), 7 + random.nextInt(2), 7 + random.nextInt(3))));
		}
		QaBuilderTerrain1102GameTests.Run r = sunk(helper, "reload", () -> {
			for (BlockPos p : bed(7, 9, 8, 8)) {
				helper.setBlock(p, Blocks.WATER);
			}
			helper.setBlock(new BlockPos(8, 4, 8), Blocks.WATER);
			for (BlockPos p : stones) {
				helper.getLevel().setBlockAndUpdate(p, Blocks.STONE.defaultBlockState());
			}
		});
		BlockPos pool = helper.absolutePos(POOL);
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
				LOGGER.info("qa 1122b wide pocket reload: saved and loaded mid-CLEAR at tick {}, {} of {} stones left", helper.getTick(), left, stones.size());
			}
		});
		helper.succeedWhen(() -> {
			helper.assertTrue(reloadedAt[0] >= 0, "setup: never saved and loaded mid-CLEAR (stage " + (r.site() == null ? "done" : r.site().stage()) + ")");
			endsDryButThePool(helper, r, pool, "wide pocket reloaded mid-CLEAR");
		});
	}

	// ---------------------------------------------------------------------------------------------- turned

	/** A 7 x 4 x 4 shut room, its 5 x 2 x 2 inside at x 1-5, z 1-2, a pool in its floor at (2, 0, 1). */
	private static final Vec3i ROOM = new Vec3i(7, 4, 4);
	private static final BlockPos ROOM_POOL = new BlockPos(2, 0, 1);
	/** The pocket under the drawing's floor: under the pool and the solid floor either side of it along the drawing's x. */
	private static final List<BlockPos> ROOM_POCKET = List.of(new BlockPos(1, 0, 1), new BlockPos(2, 0, 1), new BlockPos(3, 0, 1));
	private static final BlockPos ANCHOR = new BlockPos(15, TOP - 2, 15);

	private static ResourceLocation roomWithPool(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		BlockPos at = new BlockPos(20, 16, 20);
		fill(helper, 20, 26, 15, 15, 20, 23, Blocks.STONE);
		for (int x = 0; x < ROOM.getX(); x++) {
			for (int z = 0; z < ROOM.getZ(); z++) {
				boolean edge = x == 0 || z == 0 || x == ROOM.getX() - 1 || z == ROOM.getZ() - 1;
				boolean pool = x == ROOM_POOL.getX() && z == ROOM_POOL.getZ();
				helper.setBlock(at.offset(x, 0, z), pool ? Blocks.WATER : Blocks.COBBLESTONE);
				helper.setBlock(at.offset(x, 1, z), edge ? Blocks.COBBLESTONE : Blocks.AIR);
				helper.setBlock(at.offset(x, 2, z), edge ? Blocks.COBBLESTONE : Blocks.AIR);
				helper.setBlock(at.offset(x, 3, z), Blocks.COBBLESTONE);
			}
		}
		ResourceLocation id = ResourceLocation.fromNamespaceAndPath("aliveworkplace_test", "qa1122b_room_" + Long.toHexString(level.getGameTime()));
		level.getStructureManager().getOrCreate(id).fillFromWorld(level, helper.absolutePos(at), ROOM, false, Blocks.STRUCTURE_VOID);
		fill(helper, 20, 26, 15, 19, 20, 23, Blocks.AIR);
		return id;
	}

	/**
	 * The room placed turned as a player places it (front middle at {@link #ANCHOR}) in a lake three deep (x/z 4-26,
	 * y 6-8), a water pocket three wide one deep (two deep under the pool) carved in the stone bed under its turned
	 * floor, the room's list plus EXTRA stocked: ends dry but its pool, wherever the turn put it.
	 */
	private static void turned(GameTestHelper helper, Rotation rotation, Mirror mirror, String what) {
		prepare(helper);
		ResourceLocation id = roomWithPool(helper);
		ServerLevel level = helper.getLevel();
		fill(helper, 0, 29, 2, TOP, 0, 29, Blocks.STONE);
		fill(helper, 4, 26, TOP - 2, TOP, 4, 26, Blocks.WATER);
		BlueprintData.Placement placement = BlueprintItem.placementAt(level.dimension().location(), ROOM, helper.absolutePos(ANCHOR), rotation, mirror);
		BlockPos pool = placement.origin().offset(StructureTemplate.transform(ROOM_POOL, mirror, rotation, BlockPos.ZERO));
		List<BlockPos> pocket = new ArrayList<>();
		for (BlockPos d : ROOM_POCKET) {
			pocket.add(placement.origin().offset(StructureTemplate.transform(d, mirror, rotation, BlockPos.ZERO)).below());
		}
		pocket.add(pool.below(2));
		for (BlockPos p : pocket) {
			helper.assertTrue(level.getBlockState(p).is(Blocks.STONE), "setup: the bed under the turned floor at " + p + " is " + level.getBlockState(p));
			level.setBlockAndUpdate(p, Blocks.WATER.defaultBlockState());
		}
		BlockPos table = new BlockPos(2, G, 2);
		BlockPos chest = new BlockPos(2, G, 0);
		List<BlockPos> supplies = List.of(chest, new BlockPos(1, G, 0), new BlockPos(3, G, 0), new BlockPos(0, G, 0), new BlockPos(0, G, 2));
		helper.setBlock(table, ModBlocks.BUILDERS_BENCH);
		QaBuilderDisruptionGameTests.ensurePoi(level, helper.absolutePos(table));
		for (BlockPos p : supplies) {
			helper.setBlock(p, p.equals(chest) ? Blocks.CHEST : Blocks.BARREL);
		}
		Villager builder = helper.spawn(EntityType.VILLAGER, new BlockPos(3, G, 3));
		Builders.employ(level, builder, helper.absolutePos(table));
		BuildSite site = Builders.start(level, builder, null, id, placement);
		if (site == null) {
			throw new GameTestAssertException(id + " didn't start turned " + rotation + " mirrored " + mirror);
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
		LOGGER.info("qa 1122b {}: {} {} origin {}, pool {}, pocket {}, bounds {}", what, rotation, mirror, placement.origin(), pool, pocket, plan.bounds());
		QaBuilderTerrain1102GameTests.Run r = new QaBuilderTerrain1102GameTests.Run(level, new Villager[] {builder}, siteId, plan, G, supplies, new String[1]);
		BlockState drawn = plan.targets().get(pool);
		helper.assertTrue(drawn != null && !drawn.getFluidState().isEmpty(), "setup: the turned drawing has no pool at " + pool + ": " + drawn);
		watchDeath(helper, r);
		helper.succeedWhen(() -> endsDryButThePool(helper, r, pool, what));
	}

	/** The pool room turned a quarter over a pocket three wide under its turned floor: dry but the pool. */
	//$ gametest_ticks_batch HUGE '16000' '"qa1122bWidePocketTurn90"'
	@GameTest(template = HUGE, timeoutTicks = 16000, batch = "qa1122bWidePocketTurn90")
	public void aPoolRoomTurnedAQuarterOverAWidePocketEndsDryButThePool(GameTestHelper helper) {
		turned(helper, Rotation.CLOCKWISE_90, Mirror.NONE, "wide pocket turned a quarter");
	}

	/** The pool room mirrored left to right and turned half round over a pocket three wide: dry but the pool. */
	//$ gametest_ticks_batch HUGE '16000' '"qa1122bWidePocketMirror"'
	@GameTest(template = HUGE, timeoutTicks = 16000, batch = "qa1122bWidePocketMirror")
	public void aPoolRoomMirroredAndTurnedOverAWidePocketEndsDryButThePool(GameTestHelper helper) {
		turned(helper, Rotation.CLOCKWISE_180, Mirror.LEFT_RIGHT, "wide pocket mirrored and turned half round");
	}
}
