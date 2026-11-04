package io.github.jcondedata.aliveworkplace.test;

import io.github.jcondedata.aliveworkplace.build.BuildPlan;
import io.github.jcondedata.aliveworkplace.build.BuildSite;
import io.github.jcondedata.aliveworkplace.build.Builders;
import io.github.jcondedata.aliveworkplace.build.MaterialRules;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.phys.AABB;

/**
 * B937: a blueprint whose top pokes over the world's build limit (or whose bottom reaches under the world's bottom). The
 * blocks that can't stand there are skipped (counted in skipped) without taking any material, and the build ends; the
 * hand-over says so. The likeliest ways it breaks: a save and a load mid-build near the limit, and a build that fits
 * just under the limit, which must be built whole as ever.
 */
public class BuildHeightLimitGameTests implements FabricGameTest {
	private static final String HUGE = "aliveworkplace_test:huge_area";

	/** The test hut (4 layers) on a platform whose top layer is {@code under} blocks under the world's top. */
	private static QaBuilderTerrain1102GameTests.Run hutUnderTheTop(GameTestHelper helper, int under) {
		QaBuilderTerrain1102GameTests.prepare(helper);
		ServerLevel level = helper.getLevel();
		int max = level.getMaxBuildHeight();
		int base = helper.absolutePos(BlockPos.ZERO).getY();
		int g = max - under - base;
		for (int x = -1; x <= 16; x++) {
			for (int z = -1; z <= 16; z++) {
				for (int y = g - 1; y < max - base; y++) {
					helper.setBlock(new BlockPos(x, y, z), y == g - 1 ? Blocks.STONE : Blocks.AIR);
				}
			}
		}
		Leftovers.after(helper, () -> {
			AABB top = new AABB(helper.absolutePos(new BlockPos(-4, g - 2, -4))).expandTowards(24, 10, 24);
			for (Entity e : level.getEntitiesOfClass(Entity.class, top, e -> e instanceof Villager || e instanceof ItemEntity)) {
				e.discard();
			}
			for (int x = -1; x <= 16; x++) {
				for (int z = -1; z <= 16; z++) {
					for (int y = g - 1; y < max - base; y++) {
						level.setBlock(helper.absolutePos(new BlockPos(x, y, z)), Blocks.AIR.defaultBlockState(), 2);
					}
				}
			}
		});
		return QaBuilderTerrain1102GameTests.start(helper, g, new BlockPos(6, g, 6));
	}

	/** What's loose (chests, bags, ground) is the stock less what stands of the hut under the top: nothing made or lost. */
	private static void assertNothingLost(GameTestHelper helper, QaBuilderTerrain1102GameTests.Run r) {
		ServerLevel level = r.level();
		int max = level.getMaxBuildHeight();
		Map<Item, Integer> built = new HashMap<>();
		for (Map.Entry<BlockPos, BlockState> e : r.plan().targets().entrySet()) {
			BlockState there = level.getBlockState(e.getKey());
			if (e.getValue().isAir() || e.getKey().getY() >= max || !MaterialRules.matches(there, e.getValue())
				|| there.hasProperty(BlockStateProperties.DOUBLE_BLOCK_HALF) && there.getValue(BlockStateProperties.DOUBLE_BLOCK_HALF) == DoubleBlockHalf.UPPER) {
				continue;
			}
			built.merge(there.getBlock().asItem(), 1, Integer::sum);
		}
		Map<Item, String> off = new HashMap<>();
		for (Map.Entry<Item, Integer> e : r.plan().materials().entrySet()) {
			int want = e.getValue() + QaBuilderTerrain1102GameTests.EXTRA - built.getOrDefault(e.getKey(), 0);
			int got = QaBuilderTerrain1102GameTests.loose(helper, r, e.getKey());
			if (got != want) {
				off.put(e.getKey(), "loose " + got + ", expected " + want);
			}
		}
		helper.assertTrue(off.isEmpty(), "materials made or lost by the hut at the world's top: " + off);
	}

	/** Every block of the hut under the top stands (a door whose top half would be over it can't). */
	private static void assertBuiltUnderTheTop(GameTestHelper helper, QaBuilderTerrain1102GameTests.Run r) {
		int max = r.level().getMaxBuildHeight();
		List<BlockPos> unbuilt = r.plan().unfinished(r.level()).stream()
			.filter(p -> p.getY() < max && !(r.plan().targets().get(p).getBlock() instanceof DoorBlock)).map(helper::relativePos).toList();
		helper.assertTrue(unbuilt.isEmpty(), "blocks under the world's top left unbuilt: " + unbuilt);
	}

	/** Saved and loaded while building its lower layers: the blocks over the top are still skipped, nothing lost, it ends. */
	//$ gametest_ticks_batch HUGE '9000' '"buildHeightLimitReload"'
	@GameTest(template = HUGE, timeoutTicks = 9000, batch = "buildHeightLimitReload")
	public void aHutAcrossTheTopSavedAndLoadedMidBuildStillEndsWithNothingLost(GameTestHelper helper) {
		QaBuilderTerrain1102GameTests.Run r = hutUnderTheTop(helper, 2);
		BuildSite[] last = {null};
		boolean[] reloaded = {false};
		helper.onEachTick(() -> {
			BuildSite s = r.site();
			if (s == null) {
				return;
			}
			last[0] = s; // (the skips and the end come in one tick: its count is read off the site once it's gone)
			if (!reloaded[0] && s.stage() == BuildPlan.Stage.STRUCTURE && s.cursor() > 3) {
				reloaded[0] = true;
				r.builder()[0] = QaBuilderTerrain1102GameTests.reload(helper, helper.getLevel(), r.builder()[0]);
			}
		});
		helper.succeedWhen(() -> {
			helper.assertTrue(r.site() == null, "the hut across the world's top never ends after a reload: " + QaBuilderTerrain1102GameTests.state(helper, r));
			helper.assertTrue(reloaded[0], "the build was never saved and loaded mid-structure");
			helper.assertTrue(last[0].skipped() > 0, "the blocks over the world's top weren't counted as skipped");
			assertBuiltUnderTheTop(helper, r);
			assertNothingLost(helper, r);
		});
	}

	/** A hut whose roof is the last layer under the top is built whole, nothing skipped, as anywhere else. */
	//$ gametest_ticks_batch HUGE '9000' '"buildHeightLimitFits"'
	@GameTest(template = HUGE, timeoutTicks = 9000, batch = "buildHeightLimitFits")
	public void aHutJustUnderTheTopIsBuiltWholeWithNothingSkipped(GameTestHelper helper) {
		QaBuilderTerrain1102GameTests.Run r = hutUnderTheTop(helper, 4);
		helper.assertTrue(r.plan().bounds().maxY() == helper.getLevel().getMaxBuildHeight() - 1,
			"setup: the hut's roof isn't the layer under the top: " + r.plan().bounds());
		BuildSite[] last = {null};
		helper.onEachTick(() -> {
			BuildSite s = r.site();
			if (s != null) {
				last[0] = s; // (read once it's gone: the last steps and the end can come in one tick)
			}
		});
		helper.succeedWhen(() -> {
			QaBuilderTerrain1102GameTests.assertFinished(helper, r);
			helper.assertTrue(last[0].skipped() == 0, last[0].skipped() + " block(s) skipped by a hut that fits under the world's top");
			assertNothingLost(helper, r);
		});
	}

	/** Which steps lie outside the world: over its top, under its bottom, or a door whose top half is over the top. */
	//$ gametest EMPTY_STRUCTURE
	@GameTest(template = EMPTY_STRUCTURE)
	public void stepsOverTheTopOrUnderTheBottomAreOutsideTheWorld(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		int max = level.getMaxBuildHeight();
		int min = level.getMinBuildHeight();
		BlockState planks = Blocks.OAK_PLANKS.defaultBlockState();
		helper.assertTrue(BuildPlan.outsideWorld(level, new BuildPlan.Step(new BlockPos(0, max, 0), planks, null, null, null)), "y " + max + " is in the world");
		helper.assertTrue(BuildPlan.outsideWorld(level, new BuildPlan.Step(new BlockPos(0, min - 1, 0), planks, null, null, null)), "y " + (min - 1) + " is in the world");
		helper.assertFalse(BuildPlan.outsideWorld(level, new BuildPlan.Step(new BlockPos(0, max - 1, 0), planks, null, null, null)), "y " + (max - 1) + " is outside");
		helper.assertFalse(BuildPlan.outsideWorld(level, new BuildPlan.Step(new BlockPos(0, min, 0), planks, null, null, null)), "y " + min + " is outside");
		BlockState door = Blocks.OAK_DOOR.defaultBlockState().setValue(DoorBlock.FACING, Direction.NORTH);
		BlockPos lower = new BlockPos(0, max - 1, 0);
		helper.assertTrue(BuildPlan.outsideWorld(level, new BuildPlan.Step(lower, door, null, lower.above(),
			door.setValue(DoorBlock.HALF, DoubleBlockHalf.UPPER))), "a door whose top half is over the world's top is in the world");
		helper.succeed();
	}

	/** The hand-over's warnings, word for word, and none for a build that fits. */
	//$ gametest EMPTY_STRUCTURE
	@GameTest(template = EMPTY_STRUCTURE)
	public void theHandOverWarnsOfBlocksOverTheTopOrUnderTheBottom(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		int max = level.getMaxBuildHeight();
		int min = level.getMinBuildHeight();
		Component hut = Component.literal("Test Hut");
		List<Component> over = Builders.heightWarnings(level, new BoundingBox(0, max - 2, 0, 4, max + 1, 4), 0, hut);
		helper.assertTrue(over.size() == 1 && over.get(0).getString().equals("The top of Test Hut reaches over the world's build limit (y " + max
			+ "). Nothing can stand up there, so those blocks will be left out and need no materials."), "over the top: " + over.stream().map(Component::getString).toList());
		List<Component> under = Builders.heightWarnings(level, new BoundingBox(0, min + 1, 0, 4, min + 5, 4), 3, hut);
		helper.assertTrue(under.size() == 1 && under.get(0).getString().equals("The bottom of Test Hut reaches below the bottom of the world (y " + min
			+ "). Nothing can be built down there, so those blocks will be left out and need no materials."), "under the bottom: " + under.stream().map(Component::getString).toList());
		helper.assertTrue(Builders.heightWarnings(level, new BoundingBox(0, max - 4, 0, 4, max - 1, 4), 0, hut).isEmpty(), "a warning for a hut that fits under the top");
		helper.assertTrue(Builders.heightWarnings(level, new BoundingBox(0, min + 3, 0, 4, min + 6, 4), 3, hut).isEmpty(), "a warning for a basement that fits over the bottom");
		helper.succeed();
	}
}
