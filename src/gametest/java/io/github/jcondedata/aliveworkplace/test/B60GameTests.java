package io.github.jcondedata.aliveworkplace.test;

import io.github.jcondedata.aliveworkplace.blueprint.BlueprintData;
import io.github.jcondedata.aliveworkplace.blueprint.StarterBlueprints;
import io.github.jcondedata.aliveworkplace.build.BuildPlan;
import io.github.jcondedata.aliveworkplace.build.BuildSite;
import io.github.jcondedata.aliveworkplace.build.Builders;
import io.github.jcondedata.aliveworkplace.registry.ModBlocks;
import io.github.jcondedata.aliveworkplace.registry.ModGameRules;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.levelgen.structure.BoundingBox;

/**
 * B60: in the soak a builder ended up inside the ground (dirt at its feet, air at its head) while landscaping. A builder
 * that can't walk to its block hops onto the standing spot it picked when it chose that block, up to 300 ticks earlier;
 * if the ground around the build was filled in meanwhile (a crewmate filling a landscaping hole, a player), it hopped
 * into the dirt. Here a builder walled in far from its site picks its spot, then the ground around the site is raised
 * two blocks: when it hops it must land on top of the new ground, never inside it.
 */
public class B60GameTests implements net.fabricmc.fabric.api.gametest.v1.FabricGameTest {
	private static final String HUGE = "aliveworkplace_test:huge_area";
	private static final BlockPos BENCH = new BlockPos(2, 2, 3);
	private static final BlockPos BUILDER = new BlockPos(3, 2, 3);
	private static final BlockPos ORIGIN = new BlockPos(14, 2, 14);
	private static final int MARGIN = 5;

	//$ gametest_ticks_batch HUGE '1200' '"b60HopIntoGround"'
	@GameTest(template = HUGE, timeoutTicks = 1200, batch = "b60HopIntoGround")
	public void b60ABuilderNeverHopsIntoGroundFilledSinceItPickedItsSpot(GameTestHelper helper) {
		Leftovers.clear(helper);
		ServerLevel level = helper.getLevel();
		helper.setDayTime(2000);
		level.getGameRules().getRule(ModGameRules.FREE_MATERIALS).set(true, level.getServer());
		level.getGameRules().getRule(ModGameRules.BUILD_DELAY).set(2, level.getServer());
		Leftovers.after(helper, () -> level.getGameRules().getRule(ModGameRules.FREE_MATERIALS).set(false, level.getServer()));
		// A glass cell the builder can't walk out of; the bench is part of its wall.
		for (int dx = -1; dx <= 1; dx++) {
			for (int dz = -1; dz <= 1; dz++) {
				for (int y = 2; y <= 4; y++) {
					BlockPos p = BUILDER.offset(dx, y - BUILDER.getY(), dz);
					if (!p.equals(BUILDER) && !p.equals(BUILDER.above())) {
						helper.setBlock(p, Blocks.GLASS);
					}
				}
			}
		}
		helper.setBlock(BENCH, ModBlocks.BUILDERS_BENCH);
		Villager villager = helper.spawn(EntityType.VILLAGER, BUILDER);
		Builders.employ(level, villager, helper.absolutePos(BENCH));
		BlueprintData.Placement placement = new BlueprintData.Placement(level.dimension().location(), helper.absolutePos(ORIGIN),
			Rotation.NONE, Mirror.NONE);
		BuildSite site = Builders.start(level, villager, null, StarterBlueprints.MARKET_STALL.id(), placement);
		helper.assertTrue(site != null, "the market stall didn't start");
		BuildPlan plan = site.plan(level);
		if (plan == null) {
			throw new GameTestAssertException("no plan for the market stall");
		}
		BoundingBox bounds = plan.bounds();
		BlockPos start = villager.blockPosition();
		int[] filledAt = {-1};
		String[] inside = {null};
		helper.onEachTick(() -> {
			if (filledAt[0] < 0 && site.stage() == BuildPlan.Stage.STRUCTURE) {
				// The builder has chosen its first block and where to stand for it: now the ground around the site rises.
				filledAt[0] = (int) helper.getTick() + 5;
			}
			if (filledAt[0] >= 0 && helper.getTick() == filledAt[0]) {
				for (int x = bounds.minX() - MARGIN; x <= bounds.maxX() + MARGIN; x++) {
					for (int z = bounds.minZ() - MARGIN; z <= bounds.maxZ() + MARGIN; z++) {
						if (bounds.isInside(new BlockPos(x, bounds.minY(), z))) {
							continue;
						}
						for (int y = bounds.minY(); y <= bounds.minY() + 1; y++) {
							BlockPos p = new BlockPos(x, y, z);
							if (level.getBlockState(p).isAir()) {
								level.setBlockAndUpdate(p, Blocks.DIRT.defaultBlockState());
							}
						}
					}
				}
			}
			if (inside[0] == null && villager.isAlive() && !level.noCollision(villager, villager.getBoundingBox().deflate(0.1))) {
				inside[0] = "B60: the builder is inside a block at " + villager.blockPosition().toShortString() + " (y="
					+ String.format("%.2f", villager.getY()) + ", " + level.getBlockState(villager.blockPosition()).getBlock().getName().getString()
					+ ") at tick " + helper.getTick();
			}
		});
		helper.succeedWhen(() -> {
			helper.assertTrue(inside[0] == null, inside[0] == null ? "" : inside[0]);
			helper.assertTrue(filledAt[0] >= 0 && helper.getTick() > filledAt[0], "the build hasn't reached its structure yet: stage " + site.stage());
			helper.assertTrue(villager.blockPosition().distManhattan(start) > 4, "the builder hasn't hopped out of its cell yet");
			helper.assertTrue(helper.getTick() > filledAt[0] + 100, "waiting a little after the hop");
		});
	}
}
