package io.github.jcondedata.aliveworkplace.test;

import io.github.jcondedata.aliveworkplace.blueprint.BlueprintData;
import io.github.jcondedata.aliveworkplace.build.BuildPlan;
import io.github.jcondedata.aliveworkplace.build.BuildSite;
import io.github.jcondedata.aliveworkplace.build.Builders;
import io.github.jcondedata.aliveworkplace.registry.ModBlocks;
import io.github.jcondedata.aliveworkplace.registry.ModGameRules;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.phys.Vec3;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * QA 2026-11-07 (b): B60's fix, seen by a player. A builder that can't walk to its block hops onto the standing spot it
 * picked when it chose the block; if that spot was filled since (a crewmate, a player, a neighbouring site), it looks
 * again, and the new spot must still be one it can work from: the block gets placed, from within the builder's reach,
 * and is never skipped. Kills the mutant that let the second look pick spots half a block beyond reach (2026-11-06
 * mutation run, BuilderWork's re-pick after B60).
 */
public class QaApproachSpot1107bGameTests implements net.fabricmc.fabric.api.gametest.v1.FabricGameTest {
	private static final String HUGE = "aliveworkplace_test:huge_area";
	private static final Logger LOGGER = LoggerFactory.getLogger("aliveworkplace-qa");
	/** A builder places blocks from up to this far, eye to block centre (builder-s-bench.md; BuilderWork.REACH). */
	private static final double REACH = 5.5;
	/** Where the one-block blueprint is scanned from (on the area's floor). */
	private static final BlockPos SRC = new BlockPos(25, 2, 25);
	/** The builder, walled in a glass cell on a ledge at the same height as the work, so it can only hop. */
	private static final BlockPos BUILDER = new BlockPos(3, 8, 3);
	private static final BlockPos BENCH = new BlockPos(2, 8, 3);
	/** The block to build, on top of a stone pillar, well above the floor (no ground to stand on near it). */
	private static final BlockPos TARGET = new BlockPos(14, 9, 14);
	/** The spot nearest the builder that's in reach when it picks one: filled with dirt while it waits. */
	private static final BlockPos FIRST = new BlockPos(12, 8, 14);
	/** Nearer the builder than any spot in reach, but its eye would be about 5.66 from the block: out of reach. */
	private static final BlockPos TOO_FAR = new BlockPos(10, 8, 10);
	/** The far side of the pillar: eye about 4.0 from the block, comfortably in reach. */
	private static final BlockPos FAR_SIDE = new BlockPos(18, 8, 14);

	/**
	 * The builder picks the ledge beside the pillar to stand on, a player fills that ledge with dirt while the walled-in
	 * builder waits, and the builder has to look again. Nearest to it is a ledge just out of its reach; in reach are the
	 * far side of the pillar and the top of the new dirt. It must hop to one it can work from and place the block from
	 * there: placed, from within 5.5 blocks, nothing skipped, never inside a block.
	 */
	//$ gametest_ticks_batch HUGE '900' '"qa1107bRepickInReach"'
	@GameTest(template = HUGE, timeoutTicks = 900, batch = "qa1107bRepickInReach")
	public void aBuilderWhoseStandingSpotWasFilledPlacesTheBlockFromASpotInReach(GameTestHelper helper) {
		Leftovers.clear(helper);
		ServerLevel level = helper.getLevel();
		// The blueprint: one stone block, scanned with real clicks
		helper.setBlock(SRC, Blocks.STONE);
		ResourceLocation id = QaBuilders1102bGameTests.scan(helper, SRC, SRC);
		helper.setBlock(SRC, Blocks.AIR);

		boolean free = level.getGameRules().getBoolean(ModGameRules.FREE_MATERIALS);
		int delay = level.getGameRules().getInt(ModGameRules.BUILD_DELAY);
		boolean help = level.getGameRules().getBoolean(ModGameRules.BUILDERS_HELP);
		Leftovers.after(helper, () -> {
			level.getGameRules().getRule(ModGameRules.FREE_MATERIALS).set(free, level.getServer());
			level.getGameRules().getRule(ModGameRules.BUILD_DELAY).set(delay, level.getServer());
			level.getGameRules().getRule(ModGameRules.BUILDERS_HELP).set(help, level.getServer());
		});
		level.getGameRules().getRule(ModGameRules.FREE_MATERIALS).set(true, level.getServer());
		level.getGameRules().getRule(ModGameRules.BUILD_DELAY).set(2, level.getServer());
		level.getGameRules().getRule(ModGameRules.BUILDERS_HELP).set(false, level.getServer());
		helper.setDayTime(2000);

		// The pillar the block goes on, and three one-block ledges to stand on (nothing else near it holds a villager)
		for (int y = 2; y < TARGET.getY(); y++) {
			helper.setBlock(new BlockPos(TARGET.getX(), y, TARGET.getZ()), Blocks.STONE);
		}
		for (BlockPos spot : new BlockPos[] {FIRST, TOO_FAR, FAR_SIDE}) {
			helper.setBlock(spot.below(), Blocks.STONE);
		}
		// The builder's cell: a stone ledge, glass all round and over it; the bench is part of its wall
		for (int dx = -1; dx <= 1; dx++) {
			for (int dz = -1; dz <= 1; dz++) {
				helper.setBlock(BUILDER.offset(dx, -1, dz), Blocks.STONE);
				for (int dy = 0; dy <= 2; dy++) {
					BlockPos p = BUILDER.offset(dx, dy, dz);
					if (!p.equals(BUILDER) && !p.equals(BUILDER.above())) {
						helper.setBlock(p, Blocks.GLASS);
					}
				}
			}
		}
		helper.setBlock(BENCH, ModBlocks.BUILDERS_BENCH);
		Villager villager = helper.spawn(EntityType.VILLAGER, BUILDER);
		Builders.employ(level, villager, helper.absolutePos(BENCH));
		BlueprintData.Placement placement = new BlueprintData.Placement(level.dimension().location(), helper.absolutePos(TARGET),
			Rotation.NONE, Mirror.NONE);
		BuildSite site = Builders.start(level, villager, null, id, placement);
		helper.assertTrue(site != null, "the one-block build didn't start");

		BlockPos target = helper.absolutePos(TARGET);
		BlockPos first = helper.absolutePos(FIRST);
		int[] fillAt = {-1};
		boolean[] filledWhileWalledIn = {false};
		double[] placedFrom = {-1};
		BlockPos[] stoodAt = {null};
		String[] inside = {null};
		helper.onEachTick(() -> {
			if (fillAt[0] < 0 && site.stage() == BuildPlan.Stage.STRUCTURE) {
				// it has chosen the block and the ledge beside the pillar to stand on: a few ticks later a player fills that ledge
				fillAt[0] = (int) helper.getTick() + 5;
			}
			if (fillAt[0] >= 0 && helper.getTick() == fillAt[0]) {
				filledWhileWalledIn[0] = villager.blockPosition().distManhattan(helper.absolutePos(BUILDER)) <= 1;
				level.setBlockAndUpdate(first, Blocks.DIRT.defaultBlockState());
			}
			if (placedFrom[0] < 0 && level.getBlockState(target).is(Blocks.STONE)) {
				placedFrom[0] = villager.getEyePosition().distanceTo(Vec3.atCenterOf(target));
				stoodAt[0] = villager.blockPosition().subtract(helper.absolutePos(BlockPos.ZERO));
			}
			if (inside[0] == null && villager.isAlive() && !level.noCollision(villager, villager.getBoundingBox().deflate(0.1))) {
				inside[0] = "the builder is inside a block at " + villager.blockPosition().subtract(helper.absolutePos(BlockPos.ZERO)).toShortString() + " ("
					+ level.getBlockState(villager.blockPosition()).getBlock().getName().getString() + ") at tick " + helper.getTick();
			}
		});
		helper.succeedWhen(() -> {
			helper.assertTrue(inside[0] == null, inside[0] == null ? "" : inside[0]);
			helper.assertTrue(site.skipped() == 0, "the builder skipped " + site.skipped() + " block(s) instead of placing the stone on the pillar");
			helper.assertTrue(fillAt[0] >= 0 && helper.getTick() > fillAt[0], "the build hasn't reached its structure yet: stage " + site.stage()
				+ ", builder at " + villager.blockPosition().subtract(helper.absolutePos(BlockPos.ZERO)).toShortString());
			helper.assertTrue(placedFrom[0] >= 0, "the stone isn't on the pillar yet (stage " + site.stage() + ", status " + site.status()
				+ ", builder at " + villager.blockPosition().subtract(helper.absolutePos(BlockPos.ZERO)).toShortString() + ", eye "
				+ String.format("%.2f", villager.getEyePosition().distanceTo(Vec3.atCenterOf(target))) + " from the block)");
			helper.assertTrue(filledWhileWalledIn[0], "setup: the builder had left its cell before its first ledge was filled, so it never had to look again");
			helper.assertTrue(placedFrom[0] <= REACH + 1e-6, "the stone was placed from " + String.format("%.2f", placedFrom[0])
				+ " blocks away (at " + stoodAt[0].toShortString() + "), beyond the builder's reach of " + REACH);
			helper.assertTrue(!villager.blockPosition().equals(helper.absolutePos(TOO_FAR)) && !stoodAt[0].equals(TOO_FAR), "the builder hopped onto the ledge out of reach at " + TOO_FAR.toShortString());
			LOGGER.info("qa 1107b re-pick: first ledge {} filled while walled in; stone placed from {} ({} blocks, reach {}), skipped {}",
				FIRST.toShortString(), stoodAt[0].toShortString(), String.format("%.2f", placedFrom[0]), REACH, site.skipped());
		});
	}
}
