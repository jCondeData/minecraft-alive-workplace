package io.github.jcondedata.aliveworkplace.compat;

import io.github.jcondedata.aliveworkplace.blueprint.BlueprintData;
import io.github.jcondedata.aliveworkplace.build.BuildPlan;
import io.github.jcondedata.aliveworkplace.build.BuildSite;
import io.github.jcondedata.aliveworkplace.build.BuildSiteManager;
import io.github.jcondedata.aliveworkplace.build.Builders;
import io.github.jcondedata.aliveworkplace.registry.ModBlocks;
import io.github.jcondedata.aliveworkplace.registry.ModGameRules;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Vec3i;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.StairBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;

/**
 * QA (2026-11-21) of B1410's fix, written from its spec: "water drawn as running runs, as in the player's original; only
 * water that would actually flood a dry neighbor stays still". Vanilla's rule behind it: running water never waterlogs
 * anything; a spot becomes a new source (and so waterlogs a slab, stair or pipe there) only with two sources beside it
 * and a solid block or a source under it. Both sides of that boundary, through a builder building a blueprint:
 * <ul>
 * <li>still side: two drawn sources either side of a dry stair over a floor; a Create fluid pipe in a fountain's L of
 * water, built turned a quarter;</li>
 * <li>running side: a player's water wheel (ContraptionCompatTests' contraption) with a dry stair one level below or
 * above the tank water, or behind the tank's wall; the pipe case with a save and load mid-build; and two sources either
 * side of a dry stair with nothing under them (a waterfall: two sources beside, but no floor).</li>
 * </ul>
 */
public class QaPouredWater1121eCompatTests implements FabricGameTest {
	static final String HUGE_AREA = "aliveworkplace_compat:huge_area";
	/** Where the player's own build stands before it's saved (out of the build's way). */
	static final BlockPos ORIGINAL = new BlockPos(18, 2, 18);
	private static final Vec3i CONTRAPTION_SIZE = new Vec3i(8, 6, 9);
	static final BlockPos BUILD_AT = new BlockPos(9, 2, 3);
	static final org.slf4j.Logger LOGGER = org.slf4j.LoggerFactory.getLogger("aliveworkplace_compat");

	// ---- the still side: water that would flood a dry block stays still --------------------------------------------

	/**
	 * A blueprint of two water basins with a dry stone brick stair between them (its sides open to both), all over a stone
	 * floor and walled in. In vanilla the stair's spot has two sources beside it and a floor under it, so water let run
	 * would waterlog it: the builder keeps it dry, and the finished build matches the drawing and stays so.
	 */
	//$ gametest_ticks_batch HUGE_AREA '6000' '"qa1121e_poured_stair_between"'
	@GameTest(template = HUGE_AREA, timeoutTicks = 6000, batch = "qa1121e_poured_stair_between")
	public void aDryStairBetweenTwoDrawnSourcesStaysDry(GameTestHelper helper) {
		Map<BlockPos, BlockState> drawing = new LinkedHashMap<>();
		BlockState stone = Blocks.STONE.defaultBlockState();
		for (int x = 0; x < 5; x++) {
			for (int z = 0; z < 3; z++) {
				drawing.put(new BlockPos(x, 0, z), stone);
				drawing.put(new BlockPos(x, 1, z), stone);
			}
		}
		drawing.put(new BlockPos(1, 1, 1), Blocks.WATER.defaultBlockState());
		drawing.put(new BlockPos(3, 1, 1), Blocks.WATER.defaultBlockState());
		BlockPos stair = new BlockPos(2, 1, 1);
		drawing.put(stair, Blocks.STONE_BRICK_STAIRS.defaultBlockState().setValue(StairBlock.FACING, Direction.NORTH));
		ContraptionCompatTests.Run run = drawn(helper, "stair_between", drawing, new Vec3i(5, 2, 3), BUILD_AT, Rotation.NONE);
		assertStillAsDrawn(helper, run, drawing, stair);
	}

	/**
	 * B1377's fountain bowl (three sources in an L over a floor, the dry block in the L's corner touching two of them, a
	 * drawn air spot past it), with a Create fluid pipe as the dry block and the whole blueprint built turned a quarter:
	 * the pipe stays dry and the build matches the drawing, with no water spilled round it.
	 */
	//$ gametest_ticks_batch HUGE_AREA '6000' '"qa1121e_poured_fountain_pipe"'
	@GameTest(template = HUGE_AREA, timeoutTicks = 6000, batch = "qa1121e_poured_fountain_pipe")
	public void aFountainPipeBetweenTwoSourcesStaysDryTurnedAQuarter(GameTestHelper helper) {
		Map<BlockPos, BlockState> drawing = new LinkedHashMap<>();
		BlockState stone = Blocks.STONE.defaultBlockState();
		BlockPos pipe = new BlockPos(2, 1, 2);
		BlockPos open = new BlockPos(2, 1, 3);
		for (int x = 0; x < 5; x++) {
			for (int z = 0; z < 5; z++) {
				drawing.put(new BlockPos(x, 0, z), stone);
				BlockPos p = new BlockPos(x, 1, z);
				if (!p.equals(open)) {
					drawing.put(p, stone);
				}
			}
		}
		for (BlockPos w : List.of(new BlockPos(1, 1, 1), new BlockPos(2, 1, 1), new BlockPos(1, 1, 2))) {
			drawing.put(w, Blocks.WATER.defaultBlockState());
		}
		BlockState pipeState = ContraptionCompatTests.state("fluid_pipe");
		helper.assertTrue(pipeState.hasProperty(BlockStateProperties.WATERLOGGED), "setup: Create's fluid pipe isn't waterloggable");
		drawing.put(pipe, pipeState.setValue(BlockStateProperties.WATERLOGGED, false));
		drawing.put(open, Blocks.AIR.defaultBlockState());
		ContraptionCompatTests.Run run = drawn(helper, "fountain_pipe", drawing, new Vec3i(5, 2, 5),
			ContraptionCompatTests.origin(Rotation.CLOCKWISE_90), Rotation.CLOCKWISE_90);
		assertStillAsDrawn(helper, run, drawing, pipe);
	}

	// ---- the running side: water drawn as running runs ----------------------------------------------------------------

	/**
	 * A player's water wheel whose tank leg beside the wheel is a stone brick stair: one level below the tank water and two
	 * cells off. Nothing there can turn into a source, so the poured water falls through the wheel and everything turns.
	 */
	//$ gametest_ticks_batch HUGE_AREA '12000' '"qa1121e_poured_stair_below"'
	@GameTest(template = HUGE_AREA, timeoutTicks = 12000, batch = "qa1121e_poured_stair_below")
	public void aWaterWheelWithADryStairOneLevelBelowTurns(GameTestHelper helper) {
		BlockPos stair = new BlockPos(2, 2, 3);
		ContraptionCompatTests.Run run = contraption(helper, "stair_below", stair, blocks -> {
			helper.assertTrue(blocks.containsKey(stair), "setup: the contraption has no tank leg at " + stair.toShortString());
			blocks.put(stair, Blocks.STONE_BRICK_STAIRS.defaultBlockState().setValue(StairBlock.FACING, Direction.SOUTH));
		});
		helper.succeedWhen(() -> assertRunsAndTurns(helper, run, stair));
	}

	/**
	 * A player's water wheel with a stone brick stair set on the tank's rim, one level above the tank water and two cells
	 * off (a player's trim): the poured water falls through the wheel and everything turns.
	 */
	//$ gametest_ticks_batch HUGE_AREA '12000' '"qa1121e_poured_stair_above"'
	@GameTest(template = HUGE_AREA, timeoutTicks = 12000, batch = "qa1121e_poured_stair_above")
	public void aWaterWheelWithADryStairOneLevelAboveTurns(GameTestHelper helper) {
		BlockPos stair = new BlockPos(2, 4, 5);
		ContraptionCompatTests.Run run = contraption(helper, "stair_above", stair, blocks -> {
			helper.assertTrue(blocks.containsKey(stair.below()) && !blocks.containsKey(stair), "setup: the tank's rim isn't under "
				+ stair.toShortString());
			blocks.put(stair, Blocks.STONE_BRICK_STAIRS.defaultBlockState().setValue(StairBlock.FACING, Direction.EAST));
		});
		helper.succeedWhen(() -> assertRunsAndTurns(helper, run, stair));
	}

	/**
	 * A player's water wheel with a stone brick stair against the outside of the tank's wall, level with the tank water and
	 * two cells off in a straight line, the wall between them: the water can't reach the stair, so it runs through the
	 * wheel and everything turns.
	 */
	//$ gametest_ticks_batch HUGE_AREA '12000' '"qa1121e_poured_stair_walled"'
	@GameTest(template = HUGE_AREA, timeoutTicks = 12000, batch = "qa1121e_poured_stair_walled")
	public void aWaterWheelWithADryStairBehindTheTankWallTurns(GameTestHelper helper) {
		BlockPos stair = new BlockPos(1, 3, 6);
		ContraptionCompatTests.Run run = contraption(helper, "stair_walled", stair, blocks -> {
			helper.assertTrue(blocks.get(new BlockPos(1, 3, 5)) != null && blocks.get(new BlockPos(1, 3, 5)).isSolid()
				&& !blocks.containsKey(stair), "setup: the tank wall isn't between the water and " + stair.toShortString());
			blocks.put(stair, Blocks.STONE_BRICK_STAIRS.defaultBlockState().setValue(StairBlock.FACING, Direction.NORTH));
		});
		helper.succeedWhen(() -> assertRunsAndTurns(helper, run, stair));
	}

	/**
	 * B1410's own pipe case (a Create fluid pipe in the tank's corner, two cells from the water) with the build sites and
	 * the builder saved and loaded among the kinetic parts, the pipe already standing and the water not poured yet: after
	 * the load the water is poured, falls through the wheel, and everything turns.
	 */
	//$ gametest_ticks_batch HUGE_AREA '12000' '"qa1121e_poured_pipe_reload"'
	@GameTest(template = HUGE_AREA, timeoutTicks = 12000, batch = "qa1121e_poured_pipe_reload")
	public void aWaterWheelWithAPipeReloadedBeforeItsWaterTurns(GameTestHelper helper) {
		BlockPos pipe = new BlockPos(2, 3, 5);
		ContraptionCompatTests.Run run = contraption(helper, "pipe_reload", pipe, blocks -> {
			helper.assertTrue(blocks.containsKey(pipe), "setup: the contraption has no block at the tank's corner");
			blocks.put(pipe, ContraptionCompatTests.state("fluid_pipe"));
		});
		boolean[] reloaded = {false};
		helper.onEachTick(() -> {
			if (reloaded[0] || !run.started()) {
				return;
			}
			BuildSite now = BuildSiteManager.get(run.level).get(run.site.id());
			// among the kinetic parts, the tank's corner pipe already standing and its water not yet poured
			if (now == null || now.stage() != BuildPlan.Stage.STRUCTURE || now.cursor() <= run.plan.kineticStart(0)
				|| run.level.getBlockState(run.world(pipe)).getBlock() != MechanicCompatTests.create("fluid_pipe")
				|| !run.level.getFluidState(run.world(ContraptionCompatTests.WATER)).isEmpty()) {
				return;
			}
			reloaded[0] = true;
			run.builder = QaRebuiltCreate1121eCompatTests.reload(helper, run.level, run.builder);
			BuildSite back = BuildSiteManager.get(run.level).get(run.site.id());
			helper.assertTrue(back != null, "the build site didn't survive a save and load");
			run.site = back;
		});
		helper.succeedWhen(() -> {
			helper.assertTrue(reloaded[0], "never saved and loaded between the structure and the tank's water");
			assertRunsAndTurns(helper, run, pipe);
		});
	}

	/**
	 * A player's waterfall: two water sources either side of a stone brick stair at the top, with nothing under them (an
	 * open channel below, then a stone floor). The stair has two sources beside it but no floor under it, so in vanilla it
	 * never waterlogs and the water falls. Saved and built by a builder: the water falls into the channel as in the
	 * player's original, the stair stays dry, and the build matches the drawing.
	 */
	//$ gametest_ticks_batch HUGE_AREA '6000' '"qa1121e_poured_waterfall"'
	@GameTest(template = HUGE_AREA, timeoutTicks = 6000, batch = "qa1121e_poured_waterfall")
	public void aWaterfallWithADryStairBetweenItsSourcesFalls(GameTestHelper helper) {
		Map<BlockPos, BlockState> blocks = new LinkedHashMap<>();
		BlockState stone = Blocks.STONE.defaultBlockState();
		for (int x = 0; x < 5; x++) {
			for (int z = 0; z < 3; z++) {
				blocks.put(new BlockPos(x, 0, z), stone);
				boolean channel = x >= 1 && x <= 3 && z == 1;
				if (!channel) {
					blocks.put(new BlockPos(x, 1, z), stone);
				}
				blocks.put(new BlockPos(x, 2, z), stone);
			}
		}
		BlockPos stair = new BlockPos(2, 2, 1);
		List<BlockPos> sources = List.of(new BlockPos(1, 2, 1), new BlockPos(3, 2, 1));
		blocks.remove(sources.get(0));
		blocks.remove(sources.get(1));
		blocks.put(stair, Blocks.STONE_BRICK_STAIRS.defaultBlockState().setValue(StairBlock.FACING, Direction.NORTH));
		ContraptionCompatTests.Run run = playersBuild(helper, "waterfall", blocks, sources, new Vec3i(5, 3, 3), BUILD_AT, Rotation.NONE,
			() -> {
				for (BlockPos s : sources) {
					helper.assertTrue(!helper.getBlockState(ORIGINAL.offset(s).below()).getFluidState().isEmpty(),
						"setup: the player's own waterfall doesn't fall at " + s.toShortString());
				}
				helper.assertFalse(helper.getBlockState(ORIGINAL.offset(stair)).getValue(BlockStateProperties.WATERLOGGED),
					"setup: the player's own stair is waterlogged");
			});
		helper.succeedWhen(() -> {
			assertFinished(helper, run);
			for (BlockPos s : sources) {
				BlockPos at = run.world(s);
				helper.assertTrue(run.level.getFluidState(at).isSource(), "no water poured at the waterfall's top " + s.toShortString()
					+ ": " + run.level.getBlockState(at));
				helper.assertTrue(!run.level.getFluidState(at.below()).isEmpty(), "the waterfall hangs at its top: the water poured at "
					+ s.toShortString() + " never fell into the channel (under it is " + run.level.getBlockState(at.below()) + ")");
			}
			assertDry(helper, run, stair);
			List<BlockPos> unfinished = run.plan.unfinished(run.level);
			helper.assertTrue(unfinished.isEmpty(), "the waterfall isn't as drawn: " + unfinished.size() + " wrong, e.g. "
				+ unfinished.stream().limit(8).map(p -> p.toShortString() + "=" + run.level.getBlockState(p)).toList());
			LOGGER.info("qa 1121e poured water: waterfall falls, stair dry, at tick {}", helper.getTick());
		});
	}

	// ---- helpers ----------------------------------------------------------------------------------------------------

	/**
	 * The player's water wheel (ContraptionCompatTests' contraption) changed by {@code change}, run as the player stood it
	 * (the water falls through the wheel, the wheel turns, the block at {@code dry} is dry), saved with a Structure Block's
	 * own call, taken away, and built by a builder at (9, 2, 3) from barrels of exactly its materials.
	 */
	private static ContraptionCompatTests.Run contraption(GameTestHelper helper, String name, BlockPos dry, Consumer<Map<BlockPos, BlockState>> change) {
		Map<BlockPos, BlockState> blocks = ContraptionCompatTests.contraption(false);
		change.accept(blocks);
		ServerLevel level = helper.getLevel();
		return playersBuild(helper, "wheel_" + name, blocks, List.of(ContraptionCompatTests.WATER), CONTRAPTION_SIZE, BUILD_AT, Rotation.NONE, () -> {
			helper.assertTrue(!level.getFluidState(helper.absolutePos(ORIGINAL.offset(ContraptionCompatTests.WATER).below())).isEmpty(),
				"setup: the player's own tank water doesn't run");
			helper.assertTrue(Math.abs(ContraptionCompatTests.speed(level, helper.absolutePos(ORIGINAL.offset(ContraptionCompatTests.WHEEL)))) > 0,
				"setup: the player's own wheel doesn't turn");
			BlockState there = helper.getBlockState(ORIGINAL.offset(dry));
			helper.assertTrue(there.hasProperty(BlockStateProperties.WATERLOGGED) && !there.getValue(BlockStateProperties.WATERLOGGED),
				"setup: the player's own " + there + " isn't a dry waterloggable block");
		});
	}

	/**
	 * A player's own build: {@code blocks} set by (18, 2, 18) as a player would (with block updates), then water poured at
	 * {@code water}; after 80 ticks (the water has run where it runs) {@code check} looks at it, it is saved as a
	 * blueprint with a Structure Block's own call, taken away (nothing dropped), and a builder starts it at
	 * {@code origin} turned by {@code rotation}, from barrels of exactly its materials.
	 */
	static ContraptionCompatTests.Run playersBuild(GameTestHelper helper, String name, Map<BlockPos, BlockState> blocks, List<BlockPos> water,
														   Vec3i size, BlockPos origin, Rotation rotation, Runnable check) {
		ContraptionCompatTests.Run run = prepare(helper);
		blocks.forEach((p, s) -> helper.setBlock(ORIGINAL.offset(p), s));
		for (BlockPos w : water) {
			helper.setBlock(ORIGINAL.offset(w), Blocks.WATER);
		}
		helper.runAfterDelay(80, () -> {
			check.run();
			saveTakeAwayAndStart(helper, run, "poured_" + name, size, origin, rotation);
		});
		return run;
	}

	/**
	 * A drawing laid out quietly (no water runs, nothing told of its neighbors, as a blueprint file holds it), saved as a
	 * blueprint, taken away again, and started by a builder at {@code origin} turned by {@code rotation}.
	 */
	private static ContraptionCompatTests.Run drawn(GameTestHelper helper, String name, Map<BlockPos, BlockState> drawing, Vec3i size,
													BlockPos origin, Rotation rotation) {
		ContraptionCompatTests.Run run = prepare(helper);
		ServerLevel level = helper.getLevel();
		BlockPos corner = helper.absolutePos(ORIGINAL);
		drawing.forEach((p, s) -> level.setBlock(corner.offset(p), s, Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE));
		helper.runAfterDelay(1, () -> saveTakeAwayAndStart(helper, run, "poured_" + name, size, origin, rotation));
		return run;
	}

	private static ContraptionCompatTests.Run prepare(GameTestHelper helper) {
		PartnerShowsCompatTests.clearLeftovers(helper);
		ContraptionCompatTests.Run run = new ContraptionCompatTests.Run();
		ServerLevel level = helper.getLevel();
		run.level = level;
		helper.setDayTime(2000);
		level.getGameRules().getRule(ModGameRules.BUILD_DELAY).set(1, level.getServer());
		level.getGameRules().getRule(ModGameRules.BUILDERS_HELP).set(false, level.getServer());
		return run;
	}

	private static void saveTakeAwayAndStart(GameTestHelper helper, ContraptionCompatTests.Run run, String name, Vec3i size, BlockPos origin,
											 Rotation rotation) {
		ServerLevel level = run.level;
		ResourceLocation id = ResourceLocation.fromNamespaceAndPath("aliveworkplace_compat", name);
		StructureTemplate template = level.getStructureManager().getOrCreate(id);
		BlockPos corner = helper.absolutePos(ORIGINAL);
		template.fillFromWorld(level, corner, size, false, Blocks.STRUCTURE_VOID);
		for (int y = size.getY() - 1; y >= 0; y--) {
			for (int x = 0; x < size.getX(); x++) {
				for (int z = 0; z < size.getZ(); z++) {
					level.setBlock(corner.offset(x, y, z), Blocks.AIR.defaultBlockState(), Block.UPDATE_CLIENTS | Block.UPDATE_SUPPRESS_DROPS);
				}
			}
		}
		level.getFluidTicks().clearArea(BoundingBox.fromCorners(corner, corner.offset(size.getX() - 1, size.getY() - 1, size.getZ() - 1)));
		PartnerShowsCompatTests.clearLeftovers(helper);
		BlockPos bench = new BlockPos(2, 2, 2);
		helper.setBlock(bench, ModBlocks.BUILDERS_BENCH);
		Villager builder = helper.spawn(EntityType.VILLAGER, new BlockPos(3, 2, 3));
		Builders.employ(level, builder, helper.absolutePos(bench));
		run.builder = builder;
		run.placement = new BlueprintData.Placement(level.dimension().location(), helper.absolutePos(origin), rotation, Mirror.NONE);
		BuildSite site = Builders.start(level, builder, null, id, run.placement);
		if (site == null || site.plan(level) == null) {
			throw new GameTestAssertException("no blueprint " + id);
		}
		BuildPlan plan = site.plan(level);
		QaRebuiltCreate1121eCompatTests.barrels(helper, plan, 6);
		run.plan = plan;
		run.site = site;
	}

	static void assertFinished(GameTestHelper helper, ContraptionCompatTests.Run run) {
		helper.assertTrue(run.started(), "the builder never started the build");
		BuildSite now = BuildSiteManager.get(run.level).get(run.site.id());
		helper.assertTrue(now == null, "still building: " + (now == null ? "" : now.stage() + " " + now.status() + " missing " + now.missing()));
		helper.assertTrue(run.site.skipped() == 0, "the builder skipped " + run.site.skipped() + " steps");
	}

	static void assertDry(GameTestHelper helper, ContraptionCompatTests.Run run, BlockPos local) {
		BlockState there = run.level.getBlockState(run.world(local));
		helper.assertTrue(there.hasProperty(BlockStateProperties.WATERLOGGED), "the dry block drawn at " + local.toShortString()
			+ " is missing: " + there);
		helper.assertFalse(there.getValue(BlockStateProperties.WATERLOGGED), "the " + there.getBlock().getName().getString() + " drawn dry at "
			+ local.toShortString() + " is full of water");
	}

	/** The wheel build is done, its water poured and falling through the wheel, the dry block dry, and it all turns. */
	private static void assertRunsAndTurns(GameTestHelper helper, ContraptionCompatTests.Run run, BlockPos dry) {
		assertFinished(helper, run);
		BlockPos source = run.world(ContraptionCompatTests.WATER);
		helper.assertTrue(run.level.getFluidState(source).isSource(), "no water poured in the tank: " + run.level.getBlockState(source));
		helper.assertTrue(!run.level.getFluidState(source.below()).isEmpty(), "the tank's water hangs over the drop and never runs: under it is "
			+ run.level.getBlockState(source.below()));
		assertDry(helper, run, dry);
		ContraptionCompatTests.assertBuiltAndTurning(helper, run);
		LOGGER.info("qa 1121e poured water: wheel built, water runs, {} dry, all turning at tick {}", dry.toShortString(), helper.getTick());
	}

	/**
	 * A drawing of still water built: done, every drawn block there (water as a source, the rest dry), no water in any spot
	 * round it or in its drawn air, the dry block dry, and still so 60 ticks after the build was called done.
	 */
	private static void assertStillAsDrawn(GameTestHelper helper, ContraptionCompatTests.Run run, Map<BlockPos, BlockState> drawing, BlockPos dry) {
		long[] doneAt = {-1};
		helper.succeedWhen(() -> {
			assertFinished(helper, run);
			if (doneAt[0] < 0) {
				doneAt[0] = helper.getTick();
			}
			assertDry(helper, run, dry);
			List<String> wrong = new ArrayList<>();
			for (Map.Entry<BlockPos, BlockState> e : drawing.entrySet()) {
				BlockPos p = e.getKey();
				BlockState want = e.getValue();
				BlockState s = run.level.getBlockState(run.world(p));
				boolean ok = want.is(Blocks.WATER) ? s.is(Blocks.WATER) && s.getFluidState().isSource()
					: want.isAir() ? s.isAir()
					: s.getBlock() == want.getBlock() && (!s.hasProperty(BlockStateProperties.WATERLOGGED) || !s.getValue(BlockStateProperties.WATERLOGGED));
				if (!ok) {
					wrong.add(p.toShortString() + " is " + s + ", drawn " + want);
				}
				for (Direction d : new Direction[] {Direction.NORTH, Direction.SOUTH, Direction.EAST, Direction.WEST, Direction.UP}) {
					BlockPos n = p.relative(d);
					if (!drawing.containsKey(n) && !run.level.getFluidState(run.world(n)).isEmpty()) {
						wrong.add("water spilled out at " + n.toShortString() + ": " + run.level.getBlockState(run.world(n)));
					}
				}
			}
			helper.assertTrue(wrong.isEmpty(), "the built pool isn't as drawn: " + wrong.stream().distinct().toList());
			helper.assertTrue(helper.getTick() >= doneAt[0] + 60, "watching the finished build a while");
			LOGGER.info("qa 1121e poured water: still pool as drawn, {} dry, at tick {}", dry.toShortString(), helper.getTick());
		});
	}
}
