package io.github.jcondedata.aliveworkplace.test;

import io.github.jcondedata.aliveworkplace.blueprint.Blueprint;
import io.github.jcondedata.aliveworkplace.blueprint.BlueprintData;
import io.github.jcondedata.aliveworkplace.blueprint.BlueprintLibrary;
import io.github.jcondedata.aliveworkplace.build.BuildPlan;
import io.github.jcondedata.aliveworkplace.build.BuildSite;
import io.github.jcondedata.aliveworkplace.build.BuildSiteManager;
import io.github.jcondedata.aliveworkplace.build.Builders;
import io.github.jcondedata.aliveworkplace.build.MaterialRules;
import io.github.jcondedata.aliveworkplace.registry.ModBlocks;
import io.github.jcondedata.aliveworkplace.registry.ModGameRules;
import java.util.List;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
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
import net.minecraft.world.level.block.StairBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.StairsShape;

/**
 * Where two runs of stairs meet at a corner (a roof's hip or valley, a flight that turns), the builder's stair takes
 * the corner shape the game gives a stair a player places there, whatever shape the blueprint stored: a blueprint
 * saved with every stair straight still gets its inner and outer corners, so no quarter-block gap is left.
 */
public class StairCornerGameTests implements FabricGameTest {
	private static final String AREA = "aliveworkplace_test:build_area";
	private static final BlockPos BENCH = new BlockPos(2, 2, 2);
	private static final BlockPos CHEST = new BlockPos(2, 2, 4);
	private static final BlockPos VILLAGER = new BlockPos(3, 2, 3);
	private static final BlockPos ORIGIN = new BlockPos(6, 2, 6);
	/** In the blueprint: the corner of a run facing north and one facing west (inner), and of another such pair (outer). */
	private static final BlockPos INNER = new BlockPos(0, 0, 0);
	private static final BlockPos OUTER = new BlockPos(4, 0, 4);

	/**
	 * Two L-shaped stair runs, saved all straight: built by a builder from the chest, the one corner comes out inner
	 * and the other outer, as the game works them out; the build counts as done, and still does once its site is
	 * saved and read back.
	 */
	//$ gametest_ticks_batch AREA '2400' '"stair_corners"'
	@GameTest(template = AREA, timeoutTicks = 2400, batch = "stair_corners")
	public void builderGivesStairCornersTheirShape(GameTestHelper helper) {
		Leftovers.clear(helper);
		ServerLevel level = helper.getLevel();
		BlockState north = Blocks.OAK_STAIRS.defaultBlockState().setValue(StairBlock.FACING, Direction.NORTH);
		BlockState west = Blocks.OAK_STAIRS.defaultBlockState().setValue(StairBlock.FACING, Direction.WEST);
		List<BlockPos> norths = List.of(new BlockPos(0, 0, 0), new BlockPos(1, 0, 0), new BlockPos(2, 0, 0),
			new BlockPos(2, 0, 4), new BlockPos(3, 0, 4), new BlockPos(4, 0, 4));
		List<BlockPos> wests = List.of(new BlockPos(0, 0, 1), new BlockPos(0, 0, 2), new BlockPos(4, 0, 2), new BlockPos(4, 0, 3));
		// Set without shape updates, so every stair is saved straight, as a blueprint file that didn't work them out
		for (BlockPos p : norths) {
			level.setBlock(helper.absolutePos(ORIGIN.offset(p)), north, Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE);
		}
		for (BlockPos p : wests) {
			level.setBlock(helper.absolutePos(ORIGIN.offset(p)), west, Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE);
		}
		ResourceLocation id = ResourceLocation.fromNamespaceAndPath("aliveworkplace_test", "stair_corners_" + Long.toHexString(level.getGameTime()));
		level.getStructureManager().getOrCreate(id).fillFromWorld(level, helper.absolutePos(ORIGIN), new Vec3i(5, 1, 5), false, Blocks.STRUCTURE_VOID);
		for (int x = 0; x < 5; x++) {
			for (int z = 0; z < 5; z++) {
				level.setBlock(helper.absolutePos(ORIGIN.offset(x, 0, z)), Blocks.AIR.defaultBlockState(), Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE);
			}
		}
		Blueprint blueprint = BlueprintLibrary.get(level, id).orElseThrow();
		for (Blueprint.Entry e : blueprint.blocks()) {
			if (e.state().getBlock() instanceof StairBlock) {
				helper.assertTrue(e.state().getValue(StairBlock.SHAPE) == StairsShape.STRAIGHT, "the blueprint should store every stair straight, "
					+ e.pos().toShortString() + " is " + e.state().getValue(StairBlock.SHAPE));
			}
		}

		helper.setDayTime(2000);
		level.getGameRules().getRule(ModGameRules.BUILD_DELAY).set(2, level.getServer());
		level.getGameRules().getRule(ModGameRules.BUILDERS_HELP).set(false, level.getServer());
		helper.setBlock(BENCH, ModBlocks.BUILDERS_BENCH);
		helper.setBlock(CHEST, Blocks.CHEST);
		Container chest = helper.getBlockEntity(CHEST);
		chest.setItem(0, new ItemStack(Items.OAK_STAIRS, norths.size() + wests.size()));
		Villager villager = helper.spawn(EntityType.VILLAGER, VILLAGER);
		Builders.employ(level, villager, helper.absolutePos(BENCH));
		BuildSite site = Builders.start(level, villager, null, id,
			new BlueprintData.Placement(level.dimension().location(), helper.absolutePos(ORIGIN), Rotation.NONE, Mirror.NONE));
		BuildPlan plan = site.plan(level);
		helper.assertTrue(plan != null, "no plan for " + id);

		helper.succeedWhen(() -> {
			helper.assertTrue(BuildSiteManager.get(level).get(site.id()) == null, "still building: stage=" + site.stage() + " status=" + site.status()
				+ " missing=" + site.missing());
			assertShape(helper, INNER, StairsShape.INNER_LEFT);
			assertShape(helper, OUTER, StairsShape.OUTER_LEFT);
			for (BlockPos p : norths) {
				assertAsTheGameWould(helper, p);
			}
			for (BlockPos p : wests) {
				assertAsTheGameWould(helper, p);
			}
			// Done: the corner stairs count as the straight ones the blueprint asked for, now and after a save
			helper.assertTrue(plan.unfinished(level).isEmpty(), "blocks wrong after the build: " + plan.unfinished(level));
			BuildSite back = BuildSite.load(site.save());
			helper.assertTrue(back != null, "the finished site didn't load back");
			BuildPlan again = back.plan(level);
			helper.assertTrue(again != null && again.unfinished(level).isEmpty(), "after a save and reload the build isn't done: "
				+ (again == null ? "no plan" : again.unfinished(level)));
			BlockState corner = helper.getBlockState(ORIGIN.offset(INNER));
			helper.assertTrue(MaterialRules.matches(corner, north), "an inner corner should count as the straight stair planned there");
		});
	}

	private static void assertShape(GameTestHelper helper, BlockPos local, StairsShape wanted) {
		BlockState state = helper.getBlockState(ORIGIN.offset(local));
		helper.assertTrue(state.is(Blocks.OAK_STAIRS), "no stair at " + local.toShortString() + ": " + state);
		helper.assertTrue(state.getValue(StairBlock.SHAPE) == wanted, "the corner at " + local.toShortString() + " is "
			+ state.getValue(StairBlock.SHAPE) + ", not " + wanted);
	}

	/** The shape the game itself gives the stair from its neighbours (what a player placing it would get). */
	private static void assertAsTheGameWould(GameTestHelper helper, BlockPos local) {
		BlockPos abs = helper.absolutePos(ORIGIN.offset(local));
		BlockState state = helper.getLevel().getBlockState(abs);
		helper.assertTrue(state.is(Blocks.OAK_STAIRS), "no stair at " + local.toShortString() + ": " + state);
		BlockState vanilla = Block.updateFromNeighbourShapes(state, helper.getLevel(), abs);
		helper.assertTrue(vanilla.getValue(StairBlock.SHAPE) == state.getValue(StairBlock.SHAPE), "the stair at " + local.toShortString() + " is "
			+ state.getValue(StairBlock.SHAPE) + "; the game makes it " + vanilla.getValue(StairBlock.SHAPE));
	}
}
