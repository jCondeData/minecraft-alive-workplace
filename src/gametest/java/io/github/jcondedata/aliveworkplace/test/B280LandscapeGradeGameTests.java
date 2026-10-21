package io.github.jcondedata.aliveworkplace.test;

import io.github.jcondedata.aliveworkplace.blueprint.BlueprintData;
import io.github.jcondedata.aliveworkplace.blueprint.BlueprintItem;
import io.github.jcondedata.aliveworkplace.blueprint.BlueprintOutline;
import io.github.jcondedata.aliveworkplace.blueprint.StarterBlueprints;
import io.github.jcondedata.aliveworkplace.build.BuildPlan;
import io.github.jcondedata.aliveworkplace.build.BuildSite;
import io.github.jcondedata.aliveworkplace.build.BuildSiteManager;
import io.github.jcondedata.aliveworkplace.build.Builders;
import io.github.jcondedata.aliveworkplace.registry.ModBlocks;
import io.github.jcondedata.aliveworkplace.registry.ModGameRules;
import java.util.Optional;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.FallingBlock;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.entity.BaseContainerBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

/**
 * B280 (owner, 2026-10-21): a finished Terrace stood one block above the lawn round it, and its landscaping filled the
 * ring round it up to the house's floor: a raised, jagged band of dirt two blocks wide on the grass, with holes where
 * flowers stood. Levelling now fills only holes below the ground beyond the ring, never above it; and a blueprint
 * clicked onto a flower or tall grass stands on the ground under the plant, as a block placed there would, not on top
 * of the plant.
 */
public class B280LandscapeGradeGameTests implements FabricGameTest {
	private static final String HUGE_AREA = "aliveworkplace_test:huge_area";
	private static final int LAWN = 3;
	/** The Terrace (13 x 10) from here; the lawn under it is one higher, as under the owner's. */
	private static final BlockPos ORIGIN = new BlockPos(8, LAWN + 2, 10);
	private static final BlockPos PIT = new BlockPos(12, LAWN, 8);

	/**
	 * The Terrace on a bump one above the lawn, a pit and flowers in the ring round it: once landscaped, nothing outside
	 * the footprint stands above the lawn it had, no sand or gravel is there, and the pit is filled to the lawn.
	 */
	//$ gametest_ticks_batch HUGE_AREA '4000' '"b280_terrace_grade"'
	@GameTest(template = HUGE_AREA, timeoutTicks = 4000, batch = "b280_terrace_grade")
	public void b280TerraceLeavesNoRaisedBandRoundIt(GameTestHelper helper) {
		Leftovers.clear(helper);
		ServerLevel level = helper.getLevel();
		RandomSource random = RandomSource.create(280L);
		BoundingBox footprint = new BoundingBox(ORIGIN.getX(), 0, ORIGIN.getZ(), ORIGIN.getX() + StarterBlueprints.TERRACE.size().getX() - 1, 0,
			ORIGIN.getZ() + StarterBlueprints.TERRACE.size().getZ() - 1);
		int[][] oldTop = new int[30][30];
		for (int x = 0; x < 30; x++) {
			for (int z = 0; z < 30; z++) {
				boolean under = footprint.isInside(x, 0, z);
				helper.setBlock(new BlockPos(x, 2, z), Blocks.DIRT);
				helper.setBlock(new BlockPos(x, LAWN, z), under ? Blocks.DIRT : Blocks.GRASS_BLOCK);
				if (under) {
					helper.setBlock(new BlockPos(x, LAWN + 1, z), Blocks.GRASS_BLOCK); // the bump the house stands on
				} else if (random.nextInt(5) == 0) {
					helper.setBlock(new BlockPos(x, LAWN + 1, z), random.nextBoolean() ? Blocks.POPPY : Blocks.DANDELION);
				}
				oldTop[x][z] = LAWN + (helper.getBlockState(new BlockPos(x, LAWN + 1, z)).isAir() ? 0 : 1);
			}
		}
		helper.setBlock(PIT, Blocks.AIR); // a hole in the lawn beside the house, two deep
		helper.setBlock(PIT.below(), Blocks.AIR);
		helper.setBlock(PIT.above(), Blocks.AIR);
		oldTop[PIT.getX()][PIT.getZ()] = LAWN;

		helper.setDayTime(2000);
		int delay = level.getGameRules().getInt(ModGameRules.BUILD_DELAY);
		boolean help = level.getGameRules().getBoolean(ModGameRules.BUILDERS_HELP);
		Leftovers.after(helper, () -> {
			level.getGameRules().getRule(ModGameRules.BUILD_DELAY).set(delay, level.getServer());
			level.getGameRules().getRule(ModGameRules.BUILDERS_HELP).set(help, level.getServer());
		});
		level.getGameRules().getRule(ModGameRules.BUILD_DELAY).set(2, level.getServer());
		level.getGameRules().getRule(ModGameRules.BUILDERS_HELP).set(false, level.getServer());
		BlockPos bench = new BlockPos(2, LAWN + 1, 2);
		BlockPos barrelPos = new BlockPos(2, LAWN + 1, 4);
		helper.setBlock(bench, ModBlocks.BUILDERS_BENCH);
		helper.setBlock(barrelPos, Blocks.BARREL);
		BaseContainerBlockEntity barrel = helper.getBlockEntity(barrelPos);
		barrel.setItem(0, new ItemStack(Items.DIRT, 64));
		Villager villager = helper.spawn(EntityType.VILLAGER, new BlockPos(3, LAWN + 1, 3));
		Builders.employ(level, villager, helper.absolutePos(bench));
		BlueprintData.Placement placement = new BlueprintData.Placement(level.dimension().location(), helper.absolutePos(ORIGIN), Rotation.NONE, Mirror.NONE);
		BuildSite site = Builders.start(level, villager, null, StarterBlueprints.TERRACE.id(), placement);
		BuildPlan plan = site.plan(level);
		helper.assertTrue(plan != null, "no Terrace blueprint");
		BoundingBox bounds = BlueprintOutline.bounds(placement, StarterBlueprints.TERRACE.size());
		helper.assertTrue(bounds.minX() == helper.absolutePos(ORIGIN).getX() && bounds.minZ() == helper.absolutePos(ORIGIN).getZ()
			&& bounds.getXSpan() == footprint.getXSpan() && bounds.getZSpan() == footprint.getZSpan(), "the footprint isn't where the test thinks: " + bounds);

		// Planned: no dirt above the lawn of its column
		for (BuildPlan.Step step : plan.steps(BuildPlan.Stage.LANDSCAPE)) {
			BlockPos rel = step.pos().subtract(helper.absolutePos(BlockPos.ZERO));
			helper.assertTrue(step.state().isAir() || rel.getY() <= oldTop[rel.getX()][rel.getZ()],
				"levelling plans " + step.state().getBlock() + " at " + rel.toShortString() + ", above the lawn there (y " + oldTop[rel.getX()][rel.getZ()] + ")");
		}
		helper.assertTrue(plan.steps(BuildPlan.Stage.LANDSCAPE).stream().anyMatch(s -> s.pos().equals(helper.absolutePos(PIT))),
			"the pit beside the house isn't filled");

		// The house itself, put up at once; the builder does the landscaping
		for (BuildPlan.Stage stage : new BuildPlan.Stage[] {BuildPlan.Stage.FOUNDATION, BuildPlan.Stage.STRUCTURE, BuildPlan.Stage.DECORATION}) {
			for (BuildPlan.Step step : plan.steps(stage)) {
				level.setBlock(step.pos(), step.state(), Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE);
				if (step.secondaryPos() != null && step.secondaryState() != null) {
					level.setBlock(step.secondaryPos(), step.secondaryState(), Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE);
				}
			}
		}
		for (int i = 0; i < 20 && site.stage() != BuildPlan.Stage.LANDSCAPE; i++) {
			site.finishList();
		}
		helper.assertTrue(site.stage() == BuildPlan.Stage.LANDSCAPE, "not landscaping: " + site.stage());

		helper.succeedWhen(() -> {
			helper.assertTrue(BuildSiteManager.get(level).get(site.id()) == null, "still landscaping: " + site.stage() + ", " + site.status());
			for (int x = 0; x < 30; x++) {
				for (int z = 0; z < 30; z++) {
					if (footprint.isInside(x, 0, z) || x <= 4 && z <= 6) { // the house; the bench, barrel and builder
						continue;
					}
					for (int y = 2; y < 12; y++) {
						BlockPos p = new BlockPos(x, y, z);
						BlockState state = helper.getBlockState(p);
						helper.assertTrue(!(state.getBlock() instanceof FallingBlock), state.getBlock() + " left at " + p.toShortString());
						helper.assertTrue(y <= oldTop[x][z] || state.isAir(), state.getBlock() + " at " + p.toShortString()
							+ ", above the lawn there (y " + oldTop[x][z] + ")");
					}
				}
			}
			helper.assertTrue(!helper.getBlockState(PIT).isAir() && !helper.getBlockState(PIT.below()).isAir(), "the pit wasn't filled");
		});
	}

	/**
	 * A blueprint clicked onto a flower (or the top of tall grass) stands on the ground the plant grows on, where a block
	 * placed there would go; clicked onto the grass block itself, on top of it, as before.
	 */
	//$ gametest '"aliveworkplace_test:build_area"'
	@GameTest(template = "aliveworkplace_test:build_area")
	public void b280BlueprintClickedOnAFlowerStandsOnTheGround(GameTestHelper helper) {
		for (int x = 0; x < 6; x++) {
			helper.setBlock(new BlockPos(x, 2, 2), Blocks.GRASS_BLOCK);
		}
		helper.setBlock(new BlockPos(1, 3, 2), Blocks.POPPY);
		helper.setBlock(new BlockPos(3, 3, 2), Blocks.TALL_GRASS.defaultBlockState().setValue(net.minecraft.world.level.block.DoublePlantBlock.HALF,
			net.minecraft.world.level.block.state.properties.DoubleBlockHalf.LOWER));
		helper.setBlock(new BlockPos(3, 4, 2), Blocks.TALL_GRASS.defaultBlockState().setValue(net.minecraft.world.level.block.DoublePlantBlock.HALF,
			net.minecraft.world.level.block.state.properties.DoubleBlockHalf.UPPER));
		@SuppressWarnings("removal")
		ServerPlayer player = helper.makeMockServerPlayerInLevel();
		int ground = helper.absolutePos(new BlockPos(0, 2, 0)).getY();
		for (BlockPos clicked : new BlockPos[] {new BlockPos(1, 3, 2), new BlockPos(3, 4, 2), new BlockPos(5, 2, 2)}) {
			ItemStack stack = BlueprintItem.create(StarterBlueprints.TERRACE.id(), StarterBlueprints.TERRACE.size());
			player.setItemInHand(InteractionHand.MAIN_HAND, stack);
			BlockPos at = helper.absolutePos(clicked);
			stack.useOn(new UseOnContext(player, InteractionHand.MAIN_HAND, new BlockHitResult(Vec3.atCenterOf(at), Direction.UP, at, false)));
			Optional<BlueprintData.Placement> placed = BlueprintItem.data(player.getMainHandItem()).flatMap(BlueprintData::placement);
			helper.assertTrue(placed.isPresent(), "clicking " + helper.getBlockState(clicked).getBlock() + " placed nothing");
			helper.assertValueEqual(placed.get().origin().getY(), ground + 1, "the build's floor when clicking " + helper.getBlockState(clicked).getBlock());
		}
		helper.succeed();
	}
}
