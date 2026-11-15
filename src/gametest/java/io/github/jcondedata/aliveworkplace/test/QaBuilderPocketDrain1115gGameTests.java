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
import net.minecraft.world.level.block.SlabBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.SlabType;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;

/**
 * QA (2026-11-15, qa-lane-b-1115-1537) past lane-a's fixes B1272 (b366798e0) and B1280 (0c7c4f1c8), written from
 * docs/wiki/builder.md "Stages": a build clicked onto the bed of a pond ends dry; a pool the drawing has in a shut
 * room's floor keeps its water; the floor of the box counts as a way in only where the spot under it holds water or
 * lets it through and that water reaches past the box's footprint or up to its height (a jetty's lake); a water-filled
 * pocket in the bed shut in by stone does not, so the room ends dry with its pool kept. QaBuilderMoverVerify1115bBugTests
 * covers a one-block pocket under the pool; these go further:
 * <ul>
 * <li>a pocket four deep straight down, an air pocket and a lava pocket under the drawn pool (a pocket that also runs
 * under the solid floor beside the pool floods the room: QaBuilderPocketDrain1115gBugTests);</li>
 * <li>a water pocket under a drawn hole in the floor and under a drawn waterlogged slab;</li>
 * <li>the pocket case saved and loaded mid-CLEAR, and turned a quarter, and mirrored and turned;</li>
 * <li>the jetty side: water under the pool that runs past the footprint to an open lake is never drained or dammed
 * there.</li>
 * </ul>
 * The ground sits higher than QaBuilderMoverVerify1115bGameTests' pond (stone y 2-8, the pond y 6-8 over a bed at y 5)
 * so pockets can go several blocks down into the bed. The basin is that class's 5x4x5 room.
 */
public class QaBuilderPocketDrain1115gGameTests implements FabricGameTest {
	private static final String HUGE = "aliveworkplace_test:huge_area";
	static final org.slf4j.Logger LOGGER = org.slf4j.LoggerFactory.getLogger("aliveworkplace_test");

	/** The top of the stone ground; the pond is three deep under it (y 6-8), its bed at y 5. */
	private static final int TOP = 8;
	/** The bench, chest and builder stand on the ground. */
	private static final int G = TOP + 1;
	/** The basin's corner, on the pond's bed: its box is x/z 6-10, y 6-9, its floor at y 6 and its room y 7-8. */
	private static final BlockPos ORIGIN = new BlockPos(6, 6, 6);
	/** The drawn pool's spot (the basin's floor middle) and the bed right under it. */
	private static final BlockPos POOL = new BlockPos(8, 6, 8);

	// ---------------------------------------------------------------------------------------------- setup

	/** Stone x/z 0-29 y 2-8, and the pond x/z 6-10 three deep (y 6-8) exactly the basin's footprint. */
	private static void ground(GameTestHelper helper) {
		fill(helper, 0, 29, 2, TOP, 0, 29, Blocks.STONE);
		fill(helper, 6, 10, TOP - 2, TOP, 6, 10, Blocks.WATER);
	}

	/** The basin drawn with {@code floor} at its floor's (x, z), on the pond, {@code pocket} carved into the bed, the builder hired. */
	private static QaBuilderTerrain1102GameTests.Run sunk(GameTestHelper helper, QaBuilderMoverVerify1115bGameTests.Floor floor, String name,
			Runnable pocket) {
		prepare(helper);
		ResourceLocation id = QaBuilderMoverVerify1115bGameTests.basin(helper, floor, "p1115g" + name);
		ground(helper);
		pocket.run();
		QaBuilderTerrain1102GameTests.Run r = QaBuilderDrainEdges1115bGameTests.startWith(helper, G, ORIGIN, id);
		watchDeath(helper, r);
		return r;
	}

	/** Each of {@code spots} (test coordinates) set to {@code state}. */
	static Runnable put(GameTestHelper helper, BlockState state, BlockPos... spots) {
		return () -> {
			for (BlockPos p : spots) {
				helper.setBlock(p, state);
			}
		};
	}

	/** The drawing has a water source at {@code at} (world coordinates). */
	private static void assertDrawnPool(GameTestHelper helper, QaBuilderTerrain1102GameTests.Run r, BlockPos at) {
		BlockState drawn = r.plan().targets().get(at);
		helper.assertTrue(drawn != null && !drawn.getFluidState().isEmpty(), "setup: the drawing has no pool at " + at + ": " + drawn);
	}

	/** Logs the box's wet spots on the first tick after CLEAR (a failure's story). */
	private static void logAfterClear(GameTestHelper helper, QaBuilderTerrain1102GameTests.Run r, BlockPos keep, String what) {
		boolean[] logged = {false};
		helper.onEachTick(() -> {
			BuildSite now = r.site();
			if (!logged[0] && now != null && now.stage() != BuildPlan.Stage.CLEAR) {
				logged[0] = true;
				List<String> wet = QaBuilderDrainEdges1115bGameTests.wet(helper, r, keep);
				LOGGER.info("qa 1115g {}: first tick of {}: {} wet spot(s) besides the kept one: {}", what, now.stage(), wet.size(), wet);
			}
		});
	}

	/**
	 * The build is finished as drawn with its builder alive, {@code pool} (world coordinates; null: none) still a water
	 * source, every other spot of the box dry, and every material adds up.
	 */
	private static void endsDryButThePool(GameTestHelper helper, QaBuilderTerrain1102GameTests.Run r, BlockPos pool, String what) {
		assertFinished(helper, r);
		if (pool != null) {
			BlockState there = r.level().getBlockState(pool);
			helper.assertTrue(there.is(Blocks.WATER) && there.getFluidState().isSource(), what + ": the drawing's pool lost its water: " + there);
		}
		List<String> wet = QaBuilderDrainEdges1115bGameTests.wet(helper, r, pool);
		helper.assertTrue(wet.isEmpty(), what + ": water left in the finished box besides its pool: " + wet);
		QaBuilderDrainEdges1115bGameTests.materialsAddUp(helper, r);
		LOGGER.info("qa 1115g {}: finished dry but the pool, materials add up", what);
	}

	/** The pool basin over {@code pocket}: dry but the pool. */
	static void poolOver(GameTestHelper helper, String name, Runnable pocket) {
		QaBuilderTerrain1102GameTests.Run r = sunk(helper, new QaBuilderMoverVerify1115bGameTests.Floor(2, 2, Blocks.WATER.defaultBlockState()),
			name, pocket);
		BlockPos pool = helper.absolutePos(POOL);
		assertDrawnPool(helper, r, pool);
		logAfterClear(helper, r, pool, name);
		helper.succeedWhen(() -> endsDryButThePool(helper, r, pool, name));
	}

	// ---------------------------------------------------------------------------------------------- deeper and wider pockets

	/**
	 * A water column four deep (y 2-5) under the pool, shut in by stone on every side but its top: water that reaches
	 * neither past the footprint nor up to the box's height, so the room ends dry and the pool keeps its water.
	 */
	//$ gametest_ticks_batch HUGE '12000' '"qa1115gDeepPocket"'
	@GameTest(template = HUGE, timeoutTicks = 12000, batch = "qa1115gDeepPocket")
	public void aPoolOverAWaterColumnFourDeepShutInTheBedEndsDryButThePool(GameTestHelper helper) {
		poolOver(helper, "deep", put(helper, Blocks.WATER.defaultBlockState(),
			new BlockPos(8, 5, 8), new BlockPos(8, 4, 8), new BlockPos(8, 3, 8), new BlockPos(8, 2, 8)));
	}

	/**
	 * An air pocket two deep shut in by stone under the pool (the pool's water runs down into it): nothing outside the
	 * box reaches it, so the room still ends dry and the pool's spot is still a water source.
	 */
	//$ gametest_ticks_batch HUGE '12000' '"qa1115gAirPocket"'
	@GameTest(template = HUGE, timeoutTicks = 12000, batch = "qa1115gAirPocket")
	public void aPoolOverAnAirPocketShutInTheBedEndsDryButThePool(GameTestHelper helper) {
		poolOver(helper, "air", put(helper, Blocks.AIR.defaultBlockState(), new BlockPos(8, 5, 8), new BlockPos(8, 4, 8)));
	}

	/**
	 * A lava pocket two deep shut in by stone under the pool's spot (a stone block over it at first, so it is still lava
	 * when the builder comes): the room ends dry but the pool, the builder lives, and nothing in the area is on fire.
	 */
	//$ gametest_ticks_batch HUGE '12000' '"qa1115gLavaPocket"'
	@GameTest(template = HUGE, timeoutTicks = 12000, batch = "qa1115gLavaPocket")
	public void aPoolOverALavaPocketShutInTheBedEndsDryWithNoFire(GameTestHelper helper) {
		QaBuilderTerrain1102GameTests.Run r = sunk(helper, new QaBuilderMoverVerify1115bGameTests.Floor(2, 2, Blocks.WATER.defaultBlockState()),
			"lava", () -> {
				helper.setBlock(POOL, Blocks.STONE);
				helper.setBlock(new BlockPos(8, 4, 8), Blocks.LAVA);
				helper.setBlock(new BlockPos(8, 5, 8), Blocks.LAVA);
			});
		BlockPos pool = helper.absolutePos(POOL);
		assertDrawnPool(helper, r, pool);
		logAfterClear(helper, r, pool, "lava");
		helper.succeedWhen(() -> {
			endsDryButThePool(helper, r, pool, "lava pocket");
			List<String> fire = new ArrayList<>();
			for (BlockPos p : BlockPos.betweenClosed(0, 2, 0, 29, 14, 29)) {
				if (helper.getBlockState(p).is(Blocks.FIRE)) {
					fire.add(p.toShortString());
				}
			}
			helper.assertTrue(fire.isEmpty(), "fire in the area: " + fire);
		});
	}

	// ---------------------------------------------------------------------------------------------- other open floors

	/** A drawn hole (air) in the floor's middle over a water pocket two deep: the hole and the room end dry. */
	//$ gametest_ticks_batch HUGE '12000' '"qa1115gHolePocket"'
	@GameTest(template = HUGE, timeoutTicks = 12000, batch = "qa1115gHolePocket")
	public void aDrawnHoleInTheFloorOverAWaterPocketEndsDry(GameTestHelper helper) {
		QaBuilderTerrain1102GameTests.Run r = sunk(helper, new QaBuilderMoverVerify1115bGameTests.Floor(2, 2, Blocks.AIR.defaultBlockState()),
			"hole", put(helper, Blocks.WATER.defaultBlockState(), new BlockPos(8, 5, 8), new BlockPos(8, 4, 8)));
		logAfterClear(helper, r, null, "hole");
		helper.succeedWhen(() -> endsDryButThePool(helper, r, null, "hole over a pocket"));
	}

	/** A drawn waterlogged bottom slab in the floor's middle over a water pocket two deep: the room ends dry (the slab's own water aside). */
	//$ gametest_ticks_batch HUGE '12000' '"qa1115gSlabPocket"'
	@GameTest(template = HUGE, timeoutTicks = 12000, batch = "qa1115gSlabPocket")
	public void aDrawnWaterloggedSlabOverAWaterPocketLeavesTheRoomDry(GameTestHelper helper) {
		BlockState slab = Blocks.COBBLESTONE_SLAB.defaultBlockState().setValue(SlabBlock.TYPE, SlabType.BOTTOM).setValue(BlockStateProperties.WATERLOGGED, true);
		QaBuilderTerrain1102GameTests.Run r = sunk(helper, new QaBuilderMoverVerify1115bGameTests.Floor(2, 2, slab), "slab",
			put(helper, Blocks.WATER.defaultBlockState(), new BlockPos(8, 5, 8), new BlockPos(8, 4, 8)));
		BlockPos at = helper.absolutePos(POOL);
		logAfterClear(helper, r, at, "slab");
		helper.succeedWhen(() -> {
			assertFinished(helper, r);
			List<String> wet = QaBuilderDrainEdges1115bGameTests.wet(helper, r, at);
			helper.assertTrue(wet.isEmpty(), "water left in the finished basin besides its waterlogged slab: " + wet);
			LOGGER.info("qa 1115g slab over a pocket: finished dry");
		});
	}

	// ---------------------------------------------------------------------------------------------- saved and loaded

	/**
	 * The pool basin over a water pocket two deep, six stone blocks in its room (a fixed random) so CLEAR has digging to
	 * do, saved and loaded (sites and builder) once CLEAR took some but not all: ends dry but the pool.
	 */
	//$ gametest_ticks_batch HUGE '16000' '"qa1115gPocketReload"'
	@GameTest(template = HUGE, timeoutTicks = 16000, batch = "qa1115gPocketReload")
	public void aPoolOverAPocketSavedAndLoadedMidClearEndsDryButThePool(GameTestHelper helper) {
		RandomSource random = RandomSource.create(111507L);
		Set<BlockPos> stones = new HashSet<>();
		while (stones.size() < 6) {
			// inside the room (x/z 7-9, y 7-8)
			stones.add(helper.absolutePos(new BlockPos(7 + random.nextInt(3), 7 + random.nextInt(2), 7 + random.nextInt(3))));
		}
		QaBuilderTerrain1102GameTests.Run r = sunk(helper, new QaBuilderMoverVerify1115bGameTests.Floor(2, 2, Blocks.WATER.defaultBlockState()),
			"reload", () -> {
				helper.setBlock(new BlockPos(8, 5, 8), Blocks.WATER);
				helper.setBlock(new BlockPos(8, 4, 8), Blocks.WATER);
				for (BlockPos p : stones) {
					helper.getLevel().setBlockAndUpdate(p, Blocks.STONE.defaultBlockState());
				}
			});
		BlockPos pool = helper.absolutePos(POOL);
		assertDrawnPool(helper, r, pool);
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
				LOGGER.info("qa 1115g pocket reload: saved and loaded mid-CLEAR at tick {}, {} of {} stones left", helper.getTick(), left, stones.size());
			}
		});
		logAfterClear(helper, r, pool, "reload");
		helper.succeedWhen(() -> {
			helper.assertTrue(reloadedAt[0] >= 0, "setup: never saved and loaded mid-CLEAR (stage " + (r.site() == null ? "done" : r.site().stage()) + ")");
			endsDryButThePool(helper, r, pool, "pocket reloaded mid-CLEAR");
		});
	}

	// ---------------------------------------------------------------------------------------------- turned

	/** The turned drawing's size: a 7 x 4 x 4 shut room, its 5 x 2 x 2 inside at x 1-5, z 1-2. */
	private static final Vec3i ROOM = new Vec3i(7, 4, 4);
	/** The pool in the drawing's floor, off its middle so a wrong turn puts it elsewhere. */
	private static final BlockPos ROOM_POOL = new BlockPos(2, 0, 1);
	/** Where the turned room is clicked (its front's middle), in the lake. */
	private static final BlockPos ANCHOR = new BlockPos(15, TOP - 2, 15);

	/** Scans the 7 x 4 x 4 shut room, a water source at {@link #ROOM_POOL} in its floor. */
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
		ResourceLocation id = ResourceLocation.fromNamespaceAndPath("aliveworkplace_test", "qa1115g_room_" + Long.toHexString(level.getGameTime()));
		level.getStructureManager().getOrCreate(id).fillFromWorld(level, helper.absolutePos(at), ROOM, false, Blocks.STRUCTURE_VOID);
		fill(helper, 20, 26, 15, 19, 20, 23, Blocks.AIR);
		return id;
	}

	/**
	 * The room with its pool placed turned as a player places it (its front's middle at {@link #ANCHOR}) in a lake three
	 * deep (x/z 4-26, y 6-8) cut in the stone ground, a water pocket two deep in the bed under the turned pool's spot,
	 * the bench, chest and barrels on the ground, the room's list plus EXTRA of each stocked: the room ends dry but its
	 * pool, wherever the turn put it.
	 */
	private static void turned(GameTestHelper helper, Rotation rotation, Mirror mirror, String what) {
		prepare(helper);
		ResourceLocation id = roomWithPool(helper);
		ServerLevel level = helper.getLevel();
		fill(helper, 0, 29, 2, TOP, 0, 29, Blocks.STONE);
		fill(helper, 4, 26, TOP - 2, TOP, 4, 26, Blocks.WATER);
		BlueprintData.Placement placement = BlueprintItem.placementAt(level.dimension().location(), ROOM, helper.absolutePos(ANCHOR), rotation, mirror);
		BlockPos pool = placement.origin().offset(StructureTemplate.transform(ROOM_POOL, mirror, rotation, BlockPos.ZERO));
		for (int d = 1; d <= 2; d++) {
			BlockPos p = pool.below(d);
			helper.assertTrue(level.getBlockState(p).is(Blocks.STONE), "setup: the bed under the turned pool at " + p + " is " + level.getBlockState(p));
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
		LOGGER.info("qa 1115g {}: {} {} origin {}, pool {}, materials {}, bounds {}", what, rotation, mirror, placement.origin(), pool,
			plan.materials(), plan.bounds());
		QaBuilderTerrain1102GameTests.Run r = new QaBuilderTerrain1102GameTests.Run(level, new Villager[] {builder}, siteId, plan, G, supplies, new String[1]);
		assertDrawnPool(helper, r, pool);
		watchDeath(helper, r);
		logAfterClear(helper, r, pool, what);
		helper.succeedWhen(() -> endsDryButThePool(helper, r, pool, what));
	}

	/** The pool room turned a quarter over a water pocket under its turned pool: dry but the pool. */
	//$ gametest_ticks_batch HUGE '16000' '"qa1115gPocketTurn90"'
	@GameTest(template = HUGE, timeoutTicks = 16000, batch = "qa1115gPocketTurn90")
	public void aPoolRoomTurnedAQuarterOverAPocketEndsDryButThePool(GameTestHelper helper) {
		turned(helper, Rotation.CLOCKWISE_90, Mirror.NONE, "turned a quarter");
	}

	/** The pool room mirrored front to back and turned three quarters over a water pocket under its pool: dry but the pool. */
	//$ gametest_ticks_batch HUGE '16000' '"qa1115gPocketMirror"'
	@GameTest(template = HUGE, timeoutTicks = 16000, batch = "qa1115gPocketMirror")
	public void aPoolRoomMirroredAndTurnedOverAPocketEndsDryButThePool(GameTestHelper helper) {
		turned(helper, Rotation.COUNTERCLOCKWISE_90, Mirror.FRONT_BACK, "mirrored and turned");
	}

	// ---------------------------------------------------------------------------------------------- the jetty side

	/**
	 * The jetty's side of the rule: the pool basin over a channel in the bed (y 5) that runs from under the pool out past
	 * the footprint (x 11-12) into an open lake (x 13-18, z 4-12, y 5-8) beside the pond. Water a build stands over on
	 * purpose and open to the outside stays: once the build is over, the channel past the box and the whole lake are
	 * still water sources, and no dirt is put anywhere in them (the drain neither drains nor dams beyond the box).
	 */
	//$ gametest_ticks_batch HUGE '12000' '"qa1115gOpenChannel"'
	@GameTest(template = HUGE, timeoutTicks = 12000, batch = "qa1115gOpenChannel")
	public void waterUnderThePoolOpenPastTheFootprintToALakeIsNeverDrainedOrDammed(GameTestHelper helper) {
		QaBuilderTerrain1102GameTests.Run r = sunk(helper, new QaBuilderMoverVerify1115bGameTests.Floor(2, 2, Blocks.WATER.defaultBlockState()),
			"channel", () -> {
				fill(helper, 8, 12, 5, 5, 8, 8, Blocks.WATER);
				fill(helper, 13, 18, 5, TOP, 4, 12, Blocks.WATER);
			});
		BlockPos pool = helper.absolutePos(POOL);
		assertDrawnPool(helper, r, pool);
		logAfterClear(helper, r, pool, "channel");
		helper.succeedWhen(() -> {
			// (the room over open water counts as open, so it isn't drained: only the build being over is judged here)
			helper.assertTrue(r.died()[0] == null, "the builder died on the site: " + r.died()[0]);
			helper.assertTrue(r.site() == null, "still building: " + QaBuilderTerrain1102GameTests.state(helper, r));
			List<String> wrong = new ArrayList<>();
			for (int x = 11; x <= 12; x++) {
				BlockState s = helper.getBlockState(new BlockPos(x, 5, 8));
				if (!(s.is(Blocks.WATER) && s.getFluidState().isSource())) {
					wrong.add("channel (" + x + ",5,8)=" + s.getBlock().getDescriptionId());
				}
			}
			for (int x = 13; x <= 18; x++) {
				for (int z = 4; z <= 12; z++) {
					for (int y = 5; y <= TOP; y++) {
						BlockState s = helper.getBlockState(new BlockPos(x, y, z));
						if (!(s.is(Blocks.WATER) && s.getFluidState().isSource())) {
							wrong.add("lake (" + x + "," + y + "," + z + ")=" + s.getBlock().getDescriptionId());
						}
					}
				}
			}
			helper.assertTrue(wrong.isEmpty(), wrong.size() + " spot(s) of the open water beyond the box drained or dammed: "
				+ wrong.stream().limit(12).toList());
			LOGGER.info("qa 1115g channel: open water beyond the box kept; box wet spots besides the pool: {}",
				QaBuilderDrainEdges1115bGameTests.wet(helper, r, pool).size());
		});
	}

}
