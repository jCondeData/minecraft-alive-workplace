package io.github.jcondedata.aliveworkplace.compat;

import io.github.jcondedata.aliveworkplace.blueprint.BlueprintData;
import io.github.jcondedata.aliveworkplace.blueprint.StarterBlueprints;
import io.github.jcondedata.aliveworkplace.build.BuildPlan;
import io.github.jcondedata.aliveworkplace.build.BuildSite;
import io.github.jcondedata.aliveworkplace.build.BuildSiteManager;
import io.github.jcondedata.aliveworkplace.build.Builders;
import io.github.jcondedata.aliveworkplace.build.SupplyContainers;
import io.github.jcondedata.aliveworkplace.city.CaravanTrains;
import io.github.jcondedata.aliveworkplace.mc.Nbt;
import io.github.jcondedata.aliveworkplace.registry.ModBlocks;
import io.github.jcondedata.aliveworkplace.registry.ModGameRules;
import io.github.jcondedata.aliveworkplace.store.StorageNetworks;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.StairBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

import static io.github.jcondedata.aliveworkplace.compat.QaRebuiltCreate1121eCompatTests.*;

/**
 * QA (2026-11-21) failures from {@link QaRebuiltCreate1121eCompatTests}: a player's own water wheel with a dry stair or
 * pipe two cells from its poured water never runs (B1377's {@code keepsDrawnDry} pours it "quietly"), and the rebuilt
 * Pump House's tank never fills (its intake pipe is reshaped away from the pool).
 */
public class QaRebuiltCreate1121eBugCompatTests implements FabricGameTest {
	private static final BlockPos ORIGINAL = new BlockPos(18, 2, 18);
	private static final net.minecraft.core.Vec3i SIZE = new net.minecraft.core.Vec3i(8, 6, 9);
	private static final BlockPos TWO_FROM_WATER = new BlockPos(2, 3, 5);

	// ---- 1. A player's own water wheel with a stair or a pipe two cells from its poured water -------------------------

	/**
	 * A player's own contraption (ContraptionCompatTests' water wheel fed from a header tank, belt and cogwheels) with one
	 * change a player would make without thinking: the tank's corner brick drawn as a stone brick stair, two cells from the
	 * tank's water. Saved with a Structure Block's own call while it runs, taken away, and built by a builder from barrels
	 * of its materials: when it's done the water falls through the wheel and everything turns.
	 */
	//$ gametest_ticks_batch HUGE_AREA '12000' '"qa1121e_wheel_stair"'
	@GameTest(template = HUGE_AREA, timeoutTicks = 12000, batch = "qa1121e_wheel_stair")
	public void aPlayersWaterWheelWithAStairTwoFromItsWaterTurns(GameTestHelper helper) {
		ContraptionCompatTests.Run run = playersContraption(helper, "qa1121e_stair",
			Blocks.STONE_BRICK_STAIRS.defaultBlockState().setValue(StairBlock.FACING, Direction.WEST));
		helper.succeedWhen(() -> assertWaterRunsAndTurns(helper, run));
	}

	/** As above with a Create fluid pipe in the tank's corner (a player piping the tank out): the water still runs. */
	//$ gametest_ticks_batch HUGE_AREA '12000' '"qa1121e_wheel_pipe"'
	@GameTest(template = HUGE_AREA, timeoutTicks = 12000, batch = "qa1121e_wheel_pipe")
	public void aPlayersWaterWheelWithAPipeTwoFromItsWaterTurns(GameTestHelper helper) {
		ContraptionCompatTests.Run run = playersContraption(helper, "qa1121e_pipe", ContraptionCompatTests.state("fluid_pipe"));
		helper.succeedWhen(() -> assertWaterRunsAndTurns(helper, run));
	}

	private static void assertWaterRunsAndTurns(GameTestHelper helper, ContraptionCompatTests.Run run) {
		helper.assertTrue(run.started(), "the build hasn't started");
		helper.assertTrue(BuildSiteManager.get(run.level).get(run.site.id()) == null,
			"still building: " + run.site.stage() + " " + run.site.status() + " missing " + run.site.missing());
		BlockPos source = run.world(ContraptionCompatTests.WATER);
		BlockPos below = source.below();
		helper.assertTrue(run.level.getFluidState(source).isSource(), "no water poured in the tank: " + run.level.getBlockState(source));
		helper.assertTrue(!run.level.getFluidState(below).isEmpty(), "the tank's poured water never ran: under it is "
			+ run.level.getBlockState(below) + " (pending fluid ticks there: " + run.level.getFluidTicks().hasScheduledTick(source,
			run.level.getFluidState(source).getType()) + ")");
		ContraptionCompatTests.assertBuiltAndTurning(helper, run);
	}

	/**
	 * Sets up the player's contraption with {@code corner} at the tank's corner, runs it, saves it as a blueprint the way a
	 * Structure Block does, takes it away and has a builder build it by (9, 2, 3) from barrels of exactly its materials.
	 */
	private static ContraptionCompatTests.Run playersContraption(GameTestHelper helper, String name, BlockState corner) {
		PartnerShowsCompatTests.clearLeftovers(helper);
		ContraptionCompatTests.Run run = new ContraptionCompatTests.Run();
		ServerLevel level = helper.getLevel();
		run.level = level;
		helper.setDayTime(2000);
		level.getGameRules().getRule(ModGameRules.BUILD_DELAY).set(1, level.getServer());
		level.getGameRules().getRule(ModGameRules.BUILDERS_HELP).set(false, level.getServer());
		Map<BlockPos, BlockState> blocks = ContraptionCompatTests.contraption(false);
		helper.assertTrue(blocks.containsKey(TWO_FROM_WATER), "setup: the contraption has no block at the tank's corner");
		blocks.put(TWO_FROM_WATER, corner);
		blocks.forEach((p, s) -> helper.setBlock(ORIGINAL.offset(p), s));
		helper.setBlock(ORIGINAL.offset(ContraptionCompatTests.WATER), Blocks.WATER);
		helper.runAfterDelay(80, () -> {
			// setup check: as the player stood it, the water fell through the wheel and the wheel turned
			helper.assertTrue(!level.getFluidState(helper.absolutePos(ORIGINAL.offset(ContraptionCompatTests.WATER).below())).isEmpty(),
				"setup: the player's own tank water doesn't run");
			helper.assertTrue(Math.abs(ContraptionCompatTests.speed(level, helper.absolutePos(ORIGINAL.offset(ContraptionCompatTests.WHEEL)))) > 0,
				"setup: the player's own wheel doesn't turn");
			helper.assertTrue(!level.getBlockState(helper.absolutePos(ORIGINAL.offset(TWO_FROM_WATER))).getValue(
				net.minecraft.world.level.block.state.properties.BlockStateProperties.WATERLOGGED), "setup: the corner block is waterlogged");
			ResourceLocation id = ResourceLocation.fromNamespaceAndPath("aliveworkplace_compat", "contraption_" + name);
			StructureTemplate template = level.getStructureManager().getOrCreate(id);
			template.fillFromWorld(level, helper.absolutePos(ORIGINAL), SIZE, false, Blocks.STRUCTURE_VOID);
			BlockPos at = helper.absolutePos(ORIGINAL);
			for (int y = SIZE.getY() - 1; y >= 0; y--) {
				for (int x = 0; x < SIZE.getX(); x++) {
					for (int z = 0; z < SIZE.getZ(); z++) {
						level.setBlock(at.offset(x, y, z), Blocks.AIR.defaultBlockState(), Block.UPDATE_CLIENTS | Block.UPDATE_SUPPRESS_DROPS);
					}
				}
			}
			PartnerShowsCompatTests.clearLeftovers(helper);
			BlockPos bench = new BlockPos(2, 2, 2);
			helper.setBlock(bench, ModBlocks.BUILDERS_BENCH);
			Villager builder = helper.spawn(EntityType.VILLAGER, new BlockPos(3, 2, 3));
			Builders.employ(level, builder, helper.absolutePos(bench));
			run.builder = builder;
			run.placement = new BlueprintData.Placement(level.dimension().location(), helper.absolutePos(new BlockPos(9, 2, 3)), Rotation.NONE, Mirror.NONE);
			BuildSite site = Builders.start(level, builder, null, id, run.placement);
			if (site == null || site.plan(level) == null) {
				throw new GameTestAssertException("no blueprint " + id);
			}
			BuildPlan plan = site.plan(level);
			barrels(helper, plan, 6);
			run.plan = plan;
			run.site = site;
		});
		return run;
	}

	// ---- The Pump House's tank ----

	/**
	 * The Pump House's brief: the pump draws water from the pool and pumps it up the riser into the two-high tank. A while
	 * after the builder is done, the tank (13, 8..9, 3) holds water.
	 */
	//$ gametest_ticks_batch HUGE_AREA '30000' '"qa1121e_pump_house_tank"'
	@GameTest(template = HUGE_AREA, timeoutTicks = 30000, batch = "qa1121e_pump_house_tank")
	public void aBuiltPumpHouseFillsItsTank(GameTestHelper helper) {
		Build b = start(helper, StarterBlueprints.PUMP_HOUSE, List.of());
		long[] doneAt = {-1};
		helper.succeedWhen(() -> {
			assertDone(helper, b);
			if (doneAt[0] < 0) {
				doneAt[0] = helper.getTick();
			}
			String lower = tag(b.level, b.at(new BlockPos(13, 8, 3)));
			String upper = tag(b.level, b.at(new BlockPos(13, 9, 3)));
			boolean water = lower.contains("minecraft:water") || upper.contains("minecraft:water");
			if (!water && helper.getTick() < doneAt[0] + 1200) {
				throw new GameTestAssertException("waiting for the tank to fill");
			}
			StringBuilder seen = new StringBuilder();
			for (int y = 1; y <= 9; y++) {
				for (int x = 11; x <= 15; x++) {
					for (int z = 2; z <= 4; z++) {
						BlockState s = b.level.getBlockState(b.at(new BlockPos(x, y, z)));
						if (!s.isAir() && (BuiltInRegistries.BLOCK.getKey(s.getBlock()).getNamespace().equals("create") || !s.getFluidState().isEmpty())) {
							seen.append(' ').append(x).append(',').append(y).append(',').append(z).append('=').append(s);
						}
					}
				}
			}
			helper.assertTrue(water, "a minute after the build, the tank holds no water: " + lower + " / " + upper + "; pump Speed "
				+ speed(b.level, b.at(new BlockPos(13, 2, 3))) + "; round the pump:" + seen);
		});
	}
}
