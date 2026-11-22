package io.github.jcondedata.aliveworkplace.test;

import io.github.jcondedata.aliveworkplace.AliveWorkplace;
import io.github.jcondedata.aliveworkplace.blueprint.BlueprintData;
import io.github.jcondedata.aliveworkplace.build.BuildPlan;
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
import net.minecraft.core.Vec3i;
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
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;

/**
 * QA lane B (2026-11-22, lane-b-1122-0338), B1377: "the builder places waterloggable blocks next to drawn water before the
 * water (or places them dry as drawn), so the build finishes as drawn ... any blueprint with a slab, stair or other
 * waterloggable block that touches two water cells can hit it". BuilderWaterSlabGameTests covers a bottom slab in the
 * corner of an L of sources; these take the bug's other words: a stair in that corner, a slab between two sources on
 * opposite sides (a straight channel), and the world saved and loaded once the dry block is in and the water still to pour,
 *. Each drawing is 5 x 2 x 5:
 * a stone floor, a stone rim round the water, the dry block, and a drawn-air spot past it where a spill would run.
 */
public class QaWaterSlab1122bGameTests implements FabricGameTest {
	static final String HUGE = "aliveworkplace_test:huge_area";
	static final BlockPos BENCH = new BlockPos(2, 2, 2);
	static final BlockPos CHEST = new BlockPos(2, 2, 4);
	static final BlockPos VILLAGER = new BlockPos(3, 2, 3);
	static final BlockPos DRAWN_AT = new BlockPos(20, 2, 20);
	static final BlockPos ORIGIN = new BlockPos(8, 2, 8);
	static final Set<BlockPos> L_WATER = Set.of(new BlockPos(1, 1, 1), new BlockPos(2, 1, 1), new BlockPos(1, 1, 2));
	static final Set<BlockPos> LINE_WATER = Set.of(new BlockPos(1, 1, 2), new BlockPos(3, 1, 2));
	static final BlockPos DRY = new BlockPos(2, 1, 2);
	static final BlockPos OPEN = new BlockPos(2, 1, 3);
	static final BlockState SLAB = Blocks.SMOOTH_STONE_SLAB.defaultBlockState();
	static final BlockState STAIR = Blocks.STONE_BRICK_STAIRS.defaultBlockState();

	/** A drawing: {@code water} sources, {@code dry} at DRY, OPEN drawn air, stone everywhere else. */
	static Function<BlockPos, BlockState> drawing(Set<BlockPos> water, BlockState dry) {
		return p -> {
			if (p.getY() == 0) {
				return Blocks.STONE.defaultBlockState();
			}
			if (water.contains(p)) {
				return Blocks.WATER.defaultBlockState();
			}
			if (p.equals(DRY)) {
				return dry;
			}
			return p.equals(OPEN) ? Blocks.AIR.defaultBlockState() : Blocks.STONE.defaultBlockState();
		};
	}

	/** Lays the drawing out quietly, saves it as a template and takes it away again. */
	static ResourceLocation draw(GameTestHelper helper, String what, Function<BlockPos, BlockState> drawn) {
		ServerLevel level = helper.getLevel();
		ResourceLocation id = AliveWorkplace.id("test/qa_water_" + what + "_" + Long.toHexString(level.getGameTime()));
		int quiet = Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE;
		BlockPos corner = helper.absolutePos(DRAWN_AT);
		for (BlockPos p : BlockPos.betweenClosed(0, 0, 0, 4, 1, 4)) {
			level.setBlock(corner.offset(p), drawn.apply(p), quiet);
		}
		StructureTemplate template = level.getStructureManager().getOrCreate(id);
		template.fillFromWorld(level, corner, new Vec3i(5, 2, 5), false, null);
		for (BlockPos p : BlockPos.betweenClosed(0, 0, 0, 4, 1, 4)) {
			level.setBlock(corner.offset(p), Blocks.AIR.defaultBlockState(), quiet);
		}
		level.getFluidTicks().clearArea(BoundingBox.fromCorners(corner, corner.offset(4, 1, 4)));
		Leftovers.after(helper, () -> level.getStructureManager().remove(id));
		return id;
	}

	/** What differs from the drawing, the spots round it included (no water may have run out). */
	static List<String> wrong(GameTestHelper helper, Function<BlockPos, BlockState> drawn) {
		List<String> out = new ArrayList<>();
		for (int x = -2; x <= 6; x++) {
			for (int z = -2; z <= 6; z++) {
				for (int y = 0; y <= 2; y++) {
					BlockPos p = new BlockPos(x, y, z);
					BlockState s = helper.getBlockState(ORIGIN.offset(p));
					boolean inside = x >= 0 && x <= 4 && z >= 0 && z <= 4 && y <= 1;
					if (!inside) {
						if (!s.getFluidState().isEmpty()) {
							out.add(p + " outside has " + s);
						}
						continue;
					}
					BlockState want = drawn.apply(p);
					boolean ok = want.is(Blocks.WATER) ? s.is(Blocks.WATER) && s.getFluidState().isSource()
						: want.isAir() ? s.isAir()
						: s.getBlock() == want.getBlock() && (!s.hasProperty(BlockStateProperties.WATERLOGGED) || !s.getValue(BlockStateProperties.WATERLOGGED))
							&& (!s.hasProperty(BlockStateProperties.SLAB_TYPE) || s.getValue(BlockStateProperties.SLAB_TYPE) == want.getValue(BlockStateProperties.SLAB_TYPE))
							&& (!s.hasProperty(BlockStateProperties.HALF) || s.getValue(BlockStateProperties.HALF) == want.getValue(BlockStateProperties.HALF));
					if (!ok) {
						out.add(p + " is " + s + ", drawn " + want);
					}
				}
			}
		}
		return out;
	}

	record Job(ServerLevel level, BuildSite site, Villager builder) {
	}

	/** A builder at his bench with {@code dry}'s item, 64 stone and 3 water buckets in his chest, set on the drawing. */
	static Job start(GameTestHelper helper, ResourceLocation id, ItemStack dry) {
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
		helper.setBlock(BENCH, ModBlocks.BUILDERS_BENCH);
		helper.setBlock(CHEST, Blocks.CHEST);
		Container chest = helper.getBlockEntity(CHEST);
		chest.setItem(0, new ItemStack(Items.STONE, 64));
		chest.setItem(1, dry);
		for (int i = 0; i < 3; i++) {
			chest.setItem(2 + i, new ItemStack(Items.WATER_BUCKET));
		}
		Villager builder = helper.spawn(EntityType.VILLAGER, VILLAGER);
		Builders.employ(level, builder, helper.absolutePos(BENCH));
		BuildSite site = Builders.start(level, builder, null, id,
			new BlueprintData.Placement(level.dimension().location(), helper.absolutePos(ORIGIN), Rotation.NONE, Mirror.NONE));
		helper.assertTrue(site != null && site.plan(level) != null, "the drawing wasn't found as a blueprint");
		Leftovers.after(helper, () -> {
			if (BuildSiteManager.get(level).get(site.id()) != null) {
				BuildSiteManager.get(level).remove(site.id());
			}
		});
		return new Job(level, site, builder);
	}

	/** Done, exactly as drawn, and still so 60 ticks later. */
	static void finishesAsDrawn(GameTestHelper helper, Job job, Function<BlockPos, BlockState> drawn) {
		long[] doneAt = {-1};
		helper.succeedWhen(() -> {
			BuildSite live = BuildSiteManager.get(job.level()).get(job.site().id());
			helper.assertTrue(live == null, "still building: stage=" + (live == null ? "-" : live.stage() + " status=" + live.status() + " missing=" + live.missing()));
			if (doneAt[0] < 0) {
				doneAt[0] = helper.getTick();
			}
			List<String> wrong = wrong(helper, drawn);
			helper.assertTrue(wrong.isEmpty(), "built not as drawn: " + wrong);
			helper.assertTrue(helper.getTick() >= doneAt[0] + 60, "watching the finished build a while");
		});
	}

	/** A bottom stone-brick stair (any stair, the bug says) in the L's corner, touching two drawn sources: built dry. */
	//$ gametest_ticks_batch HUGE '4000' '"qaWaterSlab1122bStair"'
	@GameTest(template = HUGE, timeoutTicks = 4000, batch = "qaWaterSlab1122bStair")
	public void aStairBetweenTwoDrawnWaterSourcesIsBuiltDry(GameTestHelper helper) {
		Leftovers.clear(helper);
		Function<BlockPos, BlockState> drawn = drawing(L_WATER, STAIR);
		Job job = start(helper, draw(helper, "stair", drawn), new ItemStack(Items.STONE_BRICK_STAIRS));
		finishesAsDrawn(helper, job, drawn);
	}

	/** A straight channel: the slab with a source on each side (west and east), drawn air in front of it: built dry. */
	//$ gametest_ticks_batch HUGE '4000' '"qaWaterSlab1122bLine"'
	@GameTest(template = HUGE, timeoutTicks = 4000, batch = "qaWaterSlab1122bLine")
	public void aSlabWithASourceOnEachSideIsBuiltDry(GameTestHelper helper) {
		Leftovers.clear(helper);
		Function<BlockPos, BlockState> drawn = drawing(LINE_WATER, SLAB);
		Job job = start(helper, draw(helper, "line", drawn), new ItemStack(Items.SMOOTH_STONE_SLAB));
		finishesAsDrawn(helper, job, drawn);
	}

	/**
	 * A restart once the slab is in and before the water is poured (the first tick the site is past STRUCTURE): the
	 * sites and the builder saved and loaded back. The water then poured still leaves the slab dry.
	 */
	//$ gametest_ticks_batch HUGE '4000' '"qaWaterSlab1122bReload"'
	@GameTest(template = HUGE, timeoutTicks = 4000, batch = "qaWaterSlab1122bReload")
	public void aRestartBeforeTheWaterIsPouredStillLeavesTheSlabDry(GameTestHelper helper) {
		Leftovers.clear(helper);
		Function<BlockPos, BlockState> drawn = drawing(L_WATER, SLAB);
		Job job = start(helper, draw(helper, "reload", drawn), new ItemStack(Items.SMOOTH_STONE_SLAB));
		boolean[] reloaded = {false};
		helper.onEachTick(() -> {
			BuildSite live = BuildSiteManager.get(job.level()).get(job.site().id());
			if (reloaded[0] || live == null) {
				return;
			}
			boolean slabIn = helper.getBlockState(ORIGIN.offset(DRY)).is(Blocks.SMOOTH_STONE_SLAB);
			boolean noWater = L_WATER.stream().noneMatch(p -> helper.getBlockState(ORIGIN.offset(p)).is(Blocks.WATER));
			if (slabIn && noWater && live.stage().ordinal() > BuildPlan.Stage.STRUCTURE.ordinal()) {
				reloaded[0] = true;
				RebuildDrawings1117e.reload(helper, job.level(), job.builder());
			}
		});
		long[] doneAt = {-1};
		helper.succeedWhen(() -> {
			helper.assertTrue(reloaded[0], "never saw the slab in with the water still to pour");
			helper.assertTrue(BuildSiteManager.get(job.level()).get(job.site().id()) == null, "still building after the restart");
			if (doneAt[0] < 0) {
				doneAt[0] = helper.getTick();
			}
			List<String> wrong = wrong(helper, drawn);
			helper.assertTrue(wrong.isEmpty(), "built not as drawn after a restart: " + wrong);
			helper.assertTrue(helper.getTick() >= doneAt[0] + 60, "watching the finished build a while");
		});
	}
}
