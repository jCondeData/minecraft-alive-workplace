package io.github.jcondedata.aliveworkplace.test;

import io.github.jcondedata.aliveworkplace.blueprint.BlueprintData;
import io.github.jcondedata.aliveworkplace.build.BuildSite;
import io.github.jcondedata.aliveworkplace.build.BuildSiteManager;
import io.github.jcondedata.aliveworkplace.build.Builders;
import io.github.jcondedata.aliveworkplace.registry.ModBlocks;
import io.github.jcondedata.aliveworkplace.registry.ModGameRules;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.function.Function;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Container;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.SlabType;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;

/**
 * QA lane B (2026-11-23, lane-b-1123-1239), B1377 and B1410 at their edges. The wiki (builder.md, section 1): "Water drawn
 * beside a dry slab or stair that two sources would flood (a slab in the corner of a fountain's bowl) is poured still, so
 * the slab stays dry as drawn (B1377); any other poured water runs as in the original". BuilderWaterSlabGameTests and
 * QaWaterSlab1122bGameTests cover a bottom slab or stair touching two sources; QaWaterRuns1122eGameTests a stair two cells
 * off. These take the rest: a slab touching only ONE source (nothing can flood it, so the water is poured as usual and
 * runs into the open spot beside it), a slab touching THREE sources, the other waterloggables (a top slab, a trapdoor, a
 * fence), the drawing built turned a quarter (the neighbours are the turned ones), and the water already standing as
 * drawn when the Builder puts the dry slab in (a repair: the slab goes in beside two sources and must stay dry). Every
 * drawing is QaWaterSlab1122bGameTests's 5 x 2 x 5 (a stone floor, a stone rim, the water, the dry block, drawn air).
 */
public class QaBuilderWaterEdges1123bGameTests implements FabricGameTest {
	static final String HUGE = QaWaterSlab1122bGameTests.HUGE;
	static final BlockPos ORIGIN = QaWaterSlab1122bGameTests.ORIGIN;
	static final BlockPos DRY = QaWaterSlab1122bGameTests.DRY;
	static final BlockState SLAB = QaWaterSlab1122bGameTests.SLAB;

	/** One source west of the dry slab, and the drawn-air spot south of the source (not of the slab), where it runs. */
	static final BlockPos ONE_SOURCE = new BlockPos(1, 1, 2);
	static final BlockPos RUNS_TO = new BlockPos(1, 1, 3);

	/** Sources west, north and east of the slab: three touching it. */
	static final Set<BlockPos> THREE_WATER = Set.of(new BlockPos(1, 1, 2), new BlockPos(2, 1, 1), new BlockPos(3, 1, 2));

	/** The single-source drawing: stone floor and rim, the source, the slab beside it, drawn air beside the source. */
	static BlockState oneSource(BlockPos p) {
		if (p.getY() == 0) {
			return Blocks.STONE.defaultBlockState();
		}
		if (p.equals(ONE_SOURCE)) {
			return Blocks.WATER.defaultBlockState();
		}
		if (p.equals(DRY)) {
			return SLAB;
		}
		return p.equals(RUNS_TO) ? Blocks.AIR.defaultBlockState() : Blocks.STONE.defaultBlockState();
	}

	/**
	 * A slab touching one drawn source only: no spot there can get two sources beside it, so the pour is an ordinary one
	 * ("any other poured water runs as in the original"). The build ends with the source where drawn, the slab dry, and
	 * the water run into the open spot beside it (flowing water, never a new source), and stays so.
	 */
	//$ gametest_ticks_batch HUGE '4000' '"qaBuilderWaterEdges1123bOne"'
	@GameTest(template = HUGE, timeoutTicks = 4000, batch = "qaBuilderWaterEdges1123bOne")
	public void aSlabTouchingOneSourceStaysDryAndThePouredWaterRuns(GameTestHelper helper) {
		Leftovers.clear(helper);
		Function<BlockPos, BlockState> drawn = QaBuilderWaterEdges1123bGameTests::oneSource;
		QaWaterSlab1122bGameTests.Job job = QaWaterSlab1122bGameTests.start(helper,
			QaWaterSlab1122bGameTests.draw(helper, "edge_one", drawn), new ItemStack(Items.SMOOTH_STONE_SLAB));
		finishesRunningInto(helper, job, drawn, RUNS_TO);
	}

	/**
	 * Done, everything as drawn (the drawn sources sources, the dry blocks dry, nothing outside wet) except {@code runsTo},
	 * a drawn-air spot beside a source, which must hold the water run into it (flowing water, not a source): the pour
	 * was an ordinary one. Still so 60 ticks later.
	 */
	static void finishesRunningInto(GameTestHelper helper, QaWaterSlab1122bGameTests.Job job, Function<BlockPos, BlockState> drawn, BlockPos runsTo) {
		long[] doneAt = {-1};
		helper.succeedWhen(() -> {
			BuildSite live = BuildSiteManager.get(job.level()).get(job.site().id());
			helper.assertTrue(live == null, "still building: " + (live == null ? "-" : live.stage() + " " + live.status() + " missing=" + live.missing()));
			if (doneAt[0] < 0) {
				doneAt[0] = helper.getTick();
			}
			BlockState ran = helper.getBlockState(ORIGIN.offset(runsTo));
			helper.assertTrue(ran.getFluidState().is(net.minecraft.tags.FluidTags.WATER) && !ran.getFluidState().isSource(),
				"the poured water was kept still instead of running into the open spot " + runsTo.toShortString() + " beside it: " + ran);
			List<String> wrong = new ArrayList<>();
			for (String w : QaWaterSlab1122bGameTests.wrong(helper, drawn)) {
				if (!w.startsWith(runsTo.toString())) { // the spot the water runs into is checked above
					wrong.add(w);
				}
			}
			helper.assertTrue(wrong.isEmpty(), "built not as drawn: " + wrong);
			helper.assertTrue(helper.getTick() >= doneAt[0] + 60, "watching the finished build a while");
		});
	}

	/**
	 * B1410's side with no dry block at all: three sources in an L round a stone corner (a solid block, never filled), and
	 * a drawn-air spot east of the L's end with only that one source beside it. No waterloggable is drawn anywhere, so
	 * every pour is an ordinary one and the water runs into the open spot; the three sources stay sources.
	 */
	//$ gametest_ticks_batch HUGE '4000' '"qaBuilderWaterEdges1123bNoDry"'
	@GameTest(template = HUGE, timeoutTicks = 4000, batch = "qaBuilderWaterEdges1123bNoDry")
	public void sourcesWithNoDryBlockNearStillRun(GameTestHelper helper) {
		Leftovers.clear(helper);
		BlockPos runsTo = new BlockPos(3, 1, 1);
		Function<BlockPos, BlockState> drawn = p -> p.getY() == 0 ? Blocks.STONE.defaultBlockState()
			: QaWaterSlab1122bGameTests.L_WATER.contains(p) ? Blocks.WATER.defaultBlockState()
			: p.equals(runsTo) ? Blocks.AIR.defaultBlockState() : Blocks.STONE.defaultBlockState();
		QaWaterSlab1122bGameTests.Job job = QaWaterSlab1122bGameTests.start(helper,
			QaWaterSlab1122bGameTests.draw(helper, "edge_nodry", drawn), new ItemStack(Items.STONE));
		finishesRunningInto(helper, job, drawn, runsTo);
	}

	/**
	 * The chained case B1410 describes ("the open spots that would turn to sources are added until none more do"), with a
	 * bottom slab, whose sides water passes: three sources on a diagonal, two drawn-air spots each with two of them beside
	 * it over the stone floor (in vanilla each turns to a source), and the slab past them, touching both spots. Poured as
	 * usual, the spots would fill and then flood the slab; the Builder must keep it dry as drawn, nothing spilled.
	 */
	//$ gametest_ticks_batch HUGE '4000' '"qaBuilderWaterEdges1123bChain"'
	@GameTest(template = HUGE, timeoutTicks = 4000, batch = "qaBuilderWaterEdges1123bChain")
	public void aSlabThatGrownSourcesWouldFloodIsBuiltDry(GameTestHelper helper) {
		Leftovers.clear(helper);
		Set<BlockPos> diagonal = Set.of(new BlockPos(1, 1, 3), new BlockPos(2, 1, 2), new BlockPos(3, 1, 1));
		Set<BlockPos> grows = Set.of(new BlockPos(2, 1, 3), new BlockPos(3, 1, 2));
		BlockPos slabAt = new BlockPos(3, 1, 3);
		Function<BlockPos, BlockState> drawn = p -> p.getY() == 0 ? Blocks.STONE.defaultBlockState()
			: diagonal.contains(p) ? Blocks.WATER.defaultBlockState()
			: grows.contains(p) ? Blocks.AIR.defaultBlockState()
			: p.equals(slabAt) ? SLAB : Blocks.STONE.defaultBlockState();
		QaWaterSlab1122bGameTests.Job job = QaWaterSlab1122bGameTests.start(helper,
			QaWaterSlab1122bGameTests.draw(helper, "edge_chain", drawn), new ItemStack(Items.SMOOTH_STONE_SLAB));
		QaWaterSlab1122bGameTests.finishesAsDrawn(helper, job, drawn);
	}

	/**
	 * The far corner of the spots the Builder looks at round a pour (three cells off on both axes): a source at (3, 1, 3)
	 * and a drawn-air pocket at (0, 1, 0) on the stone floor, everything else stone. Nothing can run or flood; the source
	 * is poured and the build ends as drawn (the pocket open, the source a source).
	 */
	//$ gametest_ticks_batch HUGE '4000' '"qaBuilderWaterEdges1123bFarCorner"'
	@GameTest(template = HUGE, timeoutTicks = 4000, batch = "qaBuilderWaterEdges1123bFarCorner")
	public void anOpenSpotAtTheFarCornerOfThePourIsBuiltAsDrawn(GameTestHelper helper) {
		Leftovers.clear(helper);
		BlockPos source = new BlockPos(3, 1, 3);
		BlockPos pocket = new BlockPos(0, 1, 0);
		Function<BlockPos, BlockState> drawn = p -> p.getY() == 0 ? Blocks.STONE.defaultBlockState()
			: p.equals(source) ? Blocks.WATER.defaultBlockState()
			: p.equals(pocket) ? Blocks.AIR.defaultBlockState() : Blocks.STONE.defaultBlockState();
		QaWaterSlab1122bGameTests.Job job = QaWaterSlab1122bGameTests.start(helper,
			QaWaterSlab1122bGameTests.draw(helper, "edge_corner", drawn), new ItemStack(Items.STONE));
		QaWaterSlab1122bGameTests.finishesAsDrawn(helper, job, drawn);
	}

	/** A slab with sources on three sides (west, north, east): poured still, so it is built dry and nothing spills. */
	//$ gametest_ticks_batch HUGE '4000' '"qaBuilderWaterEdges1123bThree"'
	@GameTest(template = HUGE, timeoutTicks = 4000, batch = "qaBuilderWaterEdges1123bThree")
	public void aSlabTouchingThreeSourcesIsBuiltDry(GameTestHelper helper) {
		Leftovers.clear(helper);
		Function<BlockPos, BlockState> drawn = QaWaterSlab1122bGameTests.drawing(THREE_WATER, SLAB);
		QaWaterSlab1122bGameTests.Job job = QaWaterSlab1122bGameTests.start(helper,
			QaWaterSlab1122bGameTests.draw(helper, "edge_three", drawn), new ItemStack(Items.SMOOTH_STONE_SLAB));
		QaWaterSlab1122bGameTests.finishesAsDrawn(helper, job, drawn);
	}

	/** A top slab in the corner of the L of sources (a top slab waterlogs as a bottom one does): built dry. */
	//$ gametest_ticks_batch HUGE '4000' '"qaBuilderWaterEdges1123bTopSlab"'
	@GameTest(template = HUGE, timeoutTicks = 4000, batch = "qaBuilderWaterEdges1123bTopSlab")
	public void aTopSlabBetweenTwoSourcesIsBuiltDry(GameTestHelper helper) {
		Leftovers.clear(helper);
		Function<BlockPos, BlockState> drawn = QaWaterSlab1122bGameTests.drawing(QaWaterSlab1122bGameTests.L_WATER,
			SLAB.setValue(BlockStateProperties.SLAB_TYPE, SlabType.TOP));
		QaWaterSlab1122bGameTests.Job job = QaWaterSlab1122bGameTests.start(helper,
			QaWaterSlab1122bGameTests.draw(helper, "edge_top", drawn), new ItemStack(Items.SMOOTH_STONE_SLAB));
		QaWaterSlab1122bGameTests.finishesAsDrawn(helper, job, drawn);
	}

	/** A closed oak trapdoor (another waterloggable the wiki's "or stair" stands for) in the L's corner: built dry. */
	//$ gametest_ticks_batch HUGE '4000' '"qaBuilderWaterEdges1123bTrapdoor"'
	@GameTest(template = HUGE, timeoutTicks = 4000, batch = "qaBuilderWaterEdges1123bTrapdoor")
	public void aTrapdoorBetweenTwoSourcesIsBuiltDry(GameTestHelper helper) {
		Leftovers.clear(helper);
		Function<BlockPos, BlockState> drawn = QaWaterSlab1122bGameTests.drawing(QaWaterSlab1122bGameTests.L_WATER,
			Blocks.OAK_TRAPDOOR.defaultBlockState());
		QaWaterSlab1122bGameTests.Job job = QaWaterSlab1122bGameTests.start(helper,
			QaWaterSlab1122bGameTests.draw(helper, "edge_trapdoor", drawn), new ItemStack(Items.OAK_TRAPDOOR));
		QaWaterSlab1122bGameTests.finishesAsDrawn(helper, job, drawn);
	}

	/** An oak fence post in a straight channel, a source on each side: built dry, nothing spills past it. */
	//$ gametest_ticks_batch HUGE '4000' '"qaBuilderWaterEdges1123bFence"'
	@GameTest(template = HUGE, timeoutTicks = 4000, batch = "qaBuilderWaterEdges1123bFence")
	public void aFenceBetweenTwoSourcesIsBuiltDry(GameTestHelper helper) {
		Leftovers.clear(helper);
		Function<BlockPos, BlockState> drawn = QaWaterSlab1122bGameTests.drawing(QaWaterSlab1122bGameTests.LINE_WATER,
			Blocks.OAK_FENCE.defaultBlockState());
		QaWaterSlab1122bGameTests.Job job = QaWaterSlab1122bGameTests.start(helper,
			QaWaterSlab1122bGameTests.draw(helper, "edge_fence", drawn), new ItemStack(Items.OAK_FENCE));
		QaWaterSlab1122bGameTests.finishesAsDrawn(helper, job, drawn);
	}

	/**
	 * The L drawing (slab in the corner of three sources) built turned a quarter clockwise at {@code TURNED_AT}: the slab's
	 * neighbours are the turned spots, and it still ends as drawn (turned), the slab dry, the three sources sources, and
	 * no water anywhere else.
	 */
	//$ gametest_ticks_batch HUGE '4000' '"qaBuilderWaterEdges1123bTurned"'
	@GameTest(template = HUGE, timeoutTicks = 4000, batch = "qaBuilderWaterEdges1123bTurned")
	public void theCornerSlabBuiltTurnedAQuarterIsBuiltDry(GameTestHelper helper) {
		Leftovers.clear(helper);
		Function<BlockPos, BlockState> drawn = QaWaterSlab1122bGameTests.drawing(QaWaterSlab1122bGameTests.L_WATER, SLAB);
		ResourceLocation id = QaWaterSlab1122bGameTests.draw(helper, "edge_turned", drawn);
		BuildSite site = startTurned(helper, id, Rotation.CLOCKWISE_90);
		long[] doneAt = {-1};
		helper.succeedWhen(() -> {
			BuildSite live = BuildSiteManager.get(helper.getLevel()).get(site.id());
			helper.assertTrue(live == null, "still building: " + (live == null ? "-" : live.stage() + " " + live.status() + " missing=" + live.missing()));
			if (doneAt[0] < 0) {
				doneAt[0] = helper.getTick();
			}
			List<String> wrong = wrongTurned(helper, drawn, Rotation.CLOCKWISE_90);
			helper.assertTrue(wrong.isEmpty(), "built turned not as drawn: " + wrong);
			helper.assertTrue(helper.getTick() >= doneAt[0] + 60, "watching the finished build a while");
		});
	}

	static final BlockPos TURNED_AT = new BlockPos(14, 2, 8);

	/** QaWaterSlab1122bGameTests.start's builder and chest, the site placed at TURNED_AT turned by {@code rotation}. */
	static BuildSite startTurned(GameTestHelper helper, ResourceLocation id, Rotation rotation) {
		ServerLevel level = helper.getLevel();
		helper.setDayTime(2000);
		int delay = level.getGameRules().getInt(ModGameRules.BUILD_DELAY);
		boolean help = level.getGameRules().getBoolean(ModGameRules.BUILDERS_HELP);
		level.getGameRules().getRule(ModGameRules.BUILD_DELAY).set(2, level.getServer());
		level.getGameRules().getRule(ModGameRules.BUILDERS_HELP).set(false, level.getServer());
		Leftovers.after(helper, () -> {
			level.getGameRules().getRule(ModGameRules.BUILD_DELAY).set(delay, level.getServer());
			level.getGameRules().getRule(ModGameRules.BUILDERS_HELP).set(help, level.getServer());
		});
		helper.setBlock(QaWaterSlab1122bGameTests.BENCH, ModBlocks.BUILDERS_BENCH);
		helper.setBlock(QaWaterSlab1122bGameTests.CHEST, Blocks.CHEST);
		Container chest = helper.getBlockEntity(QaWaterSlab1122bGameTests.CHEST);
		chest.setItem(0, new ItemStack(Items.STONE, 64));
		chest.setItem(1, new ItemStack(Items.SMOOTH_STONE_SLAB));
		for (int i = 0; i < 3; i++) {
			chest.setItem(2 + i, new ItemStack(Items.WATER_BUCKET));
		}
		Villager builder = helper.spawn(EntityType.VILLAGER, QaWaterSlab1122bGameTests.VILLAGER);
		Builders.employ(level, builder, helper.absolutePos(QaWaterSlab1122bGameTests.BENCH));
		BuildSite site = Builders.start(level, builder, null, id,
			new BlueprintData.Placement(level.dimension().location(), helper.absolutePos(TURNED_AT), rotation, Mirror.NONE));
		helper.assertTrue(site != null && site.plan(level) != null, "the drawing wasn't found as a blueprint");
		Leftovers.after(helper, () -> {
			if (BuildSiteManager.get(level).get(site.id()) != null) {
				BuildSiteManager.get(level).remove(site.id());
			}
		});
		return site;
	}

	/** What differs from the drawing turned by {@code rotation} at TURNED_AT; any water off the drawn sources counts. */
	static List<String> wrongTurned(GameTestHelper helper, Function<BlockPos, BlockState> drawn, Rotation rotation) {
		List<String> out = new ArrayList<>();
		java.util.Map<BlockPos, BlockPos> drawnAt = new java.util.HashMap<>();
		for (BlockPos p : BlockPos.betweenClosed(0, 0, 0, 4, 1, 4)) {
			drawnAt.put(TURNED_AT.offset(StructureTemplate.transform(p, Mirror.NONE, rotation, BlockPos.ZERO)), p.immutable());
		}
		for (BlockPos at : BlockPos.betweenClosed(TURNED_AT.offset(-7, 0, -7), TURNED_AT.offset(7, 2, 7))) {
			BlockState s = helper.getBlockState(at);
			BlockPos local = drawnAt.get(at);
			if (local == null) {
				if (!s.getFluidState().isEmpty()) {
					out.add(at + " outside has " + s);
				}
				continue;
			}
			BlockState want = drawn.apply(local);
			boolean ok = want.is(Blocks.WATER) ? s.is(Blocks.WATER) && s.getFluidState().isSource()
				: want.isAir() ? s.isAir()
				: s.getBlock() == want.getBlock() && (!s.hasProperty(BlockStateProperties.WATERLOGGED) || !s.getValue(BlockStateProperties.WATERLOGGED));
			if (!ok) {
				out.add(at + " (drawn " + local + ") is " + s + ", drawn " + want);
			}
		}
		return out;
	}

	/**
	 * A repair's case: everything of the L drawing already stands, its three sources too (placed without waking them),
	 * and only the slab in their corner is missing. The Builder puts the slab in beside two standing sources, and it must
	 * go in quietly: in vanilla the two sources over the stone floor would fill it and it would pour out. It ends dry, as
	 * drawn, and stays so.
	 */
	//$ gametest_ticks_batch HUGE '4000' '"qaBuilderWaterEdges1123bStanding"'
	@GameTest(template = HUGE, timeoutTicks = 4000, batch = "qaBuilderWaterEdges1123bStanding")
	public void aSlabPutInBesideStandingSourcesStaysDry(GameTestHelper helper) {
		Leftovers.clear(helper);
		Function<BlockPos, BlockState> drawn = QaWaterSlab1122bGameTests.drawing(QaWaterSlab1122bGameTests.L_WATER, SLAB);
		ResourceLocation id = QaWaterSlab1122bGameTests.draw(helper, "edge_standing", drawn);
		ServerLevel level = helper.getLevel();
		int quiet = Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE;
		BlockPos corner = helper.absolutePos(ORIGIN);
		for (BlockPos p : BlockPos.betweenClosed(0, 0, 0, 4, 1, 4)) {
			BlockState state = p.equals(DRY) ? Blocks.AIR.defaultBlockState() : drawn.apply(p);
			level.setBlock(corner.offset(p), state, quiet);
		}
		level.getFluidTicks().clearArea(BoundingBox.fromCorners(corner, corner.offset(4, 1, 4)));
		helper.assertTrue(helper.getBlockState(ORIGIN.offset(DRY)).isAir(), "the slab's spot wasn't left open");
		QaWaterSlab1122bGameTests.Job job = QaWaterSlab1122bGameTests.start(helper, id, new ItemStack(Items.SMOOTH_STONE_SLAB));
		QaWaterSlab1122bGameTests.finishesAsDrawn(helper, job, drawn);
	}
}
