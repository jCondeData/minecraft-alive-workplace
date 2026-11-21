package io.github.jcondedata.aliveworkplace.test;

import io.github.jcondedata.aliveworkplace.AliveWorkplace;
import io.github.jcondedata.aliveworkplace.blueprint.BlueprintData;
import io.github.jcondedata.aliveworkplace.build.BuildSite;
import io.github.jcondedata.aliveworkplace.build.BuildSiteManager;
import io.github.jcondedata.aliveworkplace.build.Builders;
import io.github.jcondedata.aliveworkplace.registry.ModBlocks;
import io.github.jcondedata.aliveworkplace.registry.ModGameRules;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
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
 * B1377: a slab drawn dry between two drawn water sources. In vanilla a spot with two sources beside it and a floor
 * under it becomes a source, and water running into a slab there waterlogs it; the slab then pours out over its open
 * side. The rebuilt Fountain's first draft had such slabs at its bowl's inner corners and came out 24 blocks wrong.
 * The drawing (5 x 2 x 5): a stone floor; above it a stone rim round three water sources in an L, the bottom slab in
 * the L's corner touching two of them, and one drawn-air spot past the slab where a spill would run.
 */
public class BuilderWaterSlabGameTests implements FabricGameTest {
	private static final String HUGE = "aliveworkplace_test:huge_area";
	private static final BlockPos BENCH = new BlockPos(2, 2, 2);
	private static final BlockPos CHEST = new BlockPos(2, 2, 4);
	private static final BlockPos VILLAGER = new BlockPos(3, 2, 3);
	/** Where the drawing is laid out to be saved as a template (then taken away again). */
	private static final BlockPos DRAWN_AT = new BlockPos(20, 2, 20);
	private static final BlockPos ORIGIN = new BlockPos(8, 2, 8);
	private static final Set<BlockPos> WATER = Set.of(new BlockPos(1, 1, 1), new BlockPos(2, 1, 1), new BlockPos(1, 1, 2));
	private static final BlockPos SLAB = new BlockPos(2, 1, 2);
	private static final BlockPos OPEN = new BlockPos(2, 1, 3);

	private static BlockState drawn(BlockPos p) {
		if (p.getY() == 0) {
			return Blocks.STONE.defaultBlockState();
		}
		if (WATER.contains(p)) {
			return Blocks.WATER.defaultBlockState();
		}
		if (p.equals(SLAB)) {
			return Blocks.SMOOTH_STONE_SLAB.defaultBlockState(); // a bottom slab, dry
		}
		return p.equals(OPEN) ? Blocks.AIR.defaultBlockState() : Blocks.STONE.defaultBlockState();
	}

	/** Lays the drawing out quietly (no water runs), saves it as template {@code id}, and takes it away again. */
	private static void draw(GameTestHelper helper, ResourceLocation id) {
		ServerLevel level = helper.getLevel();
		int quiet = Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE;
		BlockPos corner = helper.absolutePos(DRAWN_AT);
		for (BlockPos p : BlockPos.betweenClosed(0, 0, 0, 4, 1, 4)) {
			level.setBlock(corner.offset(p), drawn(p), quiet);
		}
		StructureTemplate template = level.getStructureManager().getOrCreate(id);
		template.fillFromWorld(level, corner, new Vec3i(5, 2, 5), false, null);
		for (BlockPos p : BlockPos.betweenClosed(0, 0, 0, 4, 1, 4)) {
			level.setBlock(corner.offset(p), Blocks.AIR.defaultBlockState(), quiet);
		}
		level.getFluidTicks().clearArea(BoundingBox.fromCorners(corner, corner.offset(4, 1, 4)));
	}

	/** What is wrong with the build against the drawing, the spots round it included (no water may have run out). */
	private static List<String> wrong(GameTestHelper helper) {
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
					BlockState want = drawn(p);
					boolean ok = want.is(Blocks.WATER) ? s.is(Blocks.WATER) && s.getFluidState().isSource()
						: want.isAir() ? s.isAir()
						: s.getBlock() == want.getBlock() && (!s.hasProperty(BlockStateProperties.WATERLOGGED) || !s.getValue(BlockStateProperties.WATERLOGGED))
							&& (!s.hasProperty(BlockStateProperties.SLAB_TYPE) || s.getValue(BlockStateProperties.SLAB_TYPE) == want.getValue(BlockStateProperties.SLAB_TYPE));
					if (!ok) {
						out.add(p + " is " + s + ", drawn " + want);
					}
				}
			}
		}
		return out;
	}

	/**
	 * A builder builds the drawing from the chest: it ends exactly as drawn, the slab dry and no water anywhere but the
	 * three sources, and stays so a while after the builder has gone.
	 */
	//$ gametest_ticks_batch HUGE '4000' '"builderWaterSlab"'
	@GameTest(template = HUGE, timeoutTicks = 4000, batch = "builderWaterSlab")
	public void aSlabBetweenTwoDrawnWaterSourcesIsBuiltDry(GameTestHelper helper) {
		Leftovers.clear(helper);
		ServerLevel level = helper.getLevel();
		ResourceLocation id = AliveWorkplace.id("test/water_slab_" + Long.toHexString(level.getGameTime()));
		draw(helper, id);
		helper.setDayTime(2000);
		level.getGameRules().getRule(ModGameRules.BUILD_DELAY).set(2, level.getServer());
		level.getGameRules().getRule(ModGameRules.BUILDERS_HELP).set(false, level.getServer());
		helper.setBlock(BENCH, ModBlocks.BUILDERS_BENCH);
		helper.setBlock(CHEST, Blocks.CHEST);
		Container chest = helper.getBlockEntity(CHEST);
		chest.setItem(0, new ItemStack(Items.STONE, 64));
		chest.setItem(1, new ItemStack(Items.SMOOTH_STONE_SLAB, 1));
		for (int i = 0; i < 3; i++) {
			chest.setItem(2 + i, new ItemStack(Items.WATER_BUCKET));
		}
		Villager builder = helper.spawn(EntityType.VILLAGER, VILLAGER);
		Builders.employ(level, builder, helper.absolutePos(BENCH));
		BuildSite site = Builders.start(level, builder, null, id,
			new BlueprintData.Placement(level.dimension().location(), helper.absolutePos(ORIGIN), Rotation.NONE, Mirror.NONE));
		helper.assertTrue(site != null && site.plan(level) != null, "the drawing wasn't found as a blueprint");
		long[] doneAt = {-1};
		helper.succeedWhen(() -> {
			helper.assertTrue(BuildSiteManager.get(level).get(site.id()) == null,
				"still building: stage=" + site.stage() + " status=" + site.status() + " missing=" + site.missing());
			if (doneAt[0] < 0) {
				doneAt[0] = helper.getTick();
			}
			List<String> wrong = wrong(helper);
			helper.assertTrue(wrong.isEmpty(), "built not as drawn: " + wrong);
			helper.assertTrue(helper.getTick() >= doneAt[0] + 60, "watching the finished build a while");
		});
	}
}
