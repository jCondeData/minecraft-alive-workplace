package io.github.jcondedata.aliveworkplace.test;

import io.github.jcondedata.aliveworkplace.blueprint.Blueprint;
import io.github.jcondedata.aliveworkplace.blueprint.BlueprintData;
import io.github.jcondedata.aliveworkplace.blueprint.BlueprintLibrary;
import io.github.jcondedata.aliveworkplace.blueprint.StarterBlueprints;
import io.github.jcondedata.aliveworkplace.build.BuildPlan;
import io.github.jcondedata.aliveworkplace.build.BuildSite;
import io.github.jcondedata.aliveworkplace.build.BuildSiteManager;
import io.github.jcondedata.aliveworkplace.build.Builders;
import io.github.jcondedata.aliveworkplace.registry.ModBlocks;
import io.github.jcondedata.aliveworkplace.registry.ModGameRules;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.schedule.Activity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.entity.BaseContainerBlockEntity;

/**
 * B23: the terrain stages (FOUNDATION and LANDSCAPE) on a hillside. In the 23.1 soak the graveyard's builder stood 30 s
 * at the start of its foundation with nothing placed, and a LANDSCAPE crawled (13 blocks in 30 s, then 30 s more).
 * A builder on shift should never go 30 s (600 ticks) without moving on: placing, digging, or skipping a step.
 */
public class TerrainStallGameTests implements FabricGameTest {
	private static final String HUGE_AREA = "aliveworkplace_test:huge_area";
	private static final BlockPos BENCH = new BlockPos(2, 2, 2);
	private static final BlockPos CHEST = new BlockPos(2, 2, 4);
	private static final BlockPos VILLAGER = new BlockPos(3, 2, 3);
	/** The soak's stall line: 30 s on shift without progress. */
	private static final int STALL_TICKS = 600;

	/** Ground height of the hillside at {@code x}: flat by the bench, then one block up every two blocks east. */
	private static int top(int x) {
		return 1 + Math.max(0, (x - 6) / 2);
	}

	private static void hillside(GameTestHelper helper) {
		for (int x = 0; x < 30; x++) {
			int top = top(x);
			for (int z = 0; z < 30; z++) {
				for (int y = 2; y <= top; y++) {
					helper.setBlock(new BlockPos(x, y, z), y == top ? Blocks.GRASS_BLOCK : Blocks.DIRT);
				}
			}
		}
	}

	/**
	 * The graveyard on a hillside: its floor (y=6) is under the ground on the uphill (east) side, which is cleared,
	 * and up to four blocks above it downhill, where it gets a foundation; levelling the ground cuts the hill
	 * around it and fills the hollow below. The builder finishes, and never stalls 30 s on shift.
	 */
	//$ gametest_ticks_batch HUGE_AREA '40000' '"b23_graveyard_hillside"'
	@GameTest(template = HUGE_AREA, timeoutTicks = 40000, batch = "b23_graveyard_hillside")
	public void b23GraveyardOnAHillsideNeverStalls(GameTestHelper helper) {
		Leftovers.clear(helper);
		buildOnHillside(helper, StarterBlueprints.GRAVEYARD, new BlockPos(9, 6, 8));
	}

	/** The same for the tinker's workshop, whose LANDSCAPE crawled in the soak. */
	//$ gametest_ticks_batch HUGE_AREA '40000' '"b23_tinkers_hillside"'
	@GameTest(template = HUGE_AREA, timeoutTicks = 40000, batch = "b23_tinkers_hillside")
	public void b23TinkersWorkshopOnAHillsideNeverStalls(GameTestHelper helper) {
		Leftovers.clear(helper);
		buildOnHillside(helper, StarterBlueprints.TINKERS_WORKSHOP, new BlockPos(9, 6, 8));
	}

	private void buildOnHillside(GameTestHelper helper, StarterBlueprints.Entry entry, BlockPos origin) {
		ServerLevel level = helper.getLevel();
		hillside(helper);
		helper.setDayTime(2000);
		level.getGameRules().getRule(ModGameRules.BUILD_DELAY).set(2, level.getServer());
		level.getGameRules().getRule(ModGameRules.BUILDERS_HELP).set(false, level.getServer());
		Blueprint blueprint = BlueprintLibrary.get(level, entry.id())
			.orElseThrow(() -> new GameTestAssertException("missing starter blueprint " + entry.id()));
		BlueprintData.Placement placement = new BlueprintData.Placement(level.dimension().location(), helper.absolutePos(origin), Rotation.NONE, Mirror.NONE);
		// What it needs on this ground (foundation included), plus dirt to fill hollows with.
		BuildPlan onGround = BuildPlan.create(blueprint, placement, level,
			level.getGameRules().getInt(ModGameRules.FOUNDATION_DEPTH), 0);
		List<ItemStack> stock = new ArrayList<>();
		for (Map.Entry<Item, Integer> e : onGround.materials().entrySet()) {
			int left = e.getValue();
			while (left > 0) {
				int n = Math.min(left, e.getKey().getDefaultMaxStackSize());
				stock.add(new ItemStack(e.getKey(), n));
				left -= n;
			}
		}
		stock.add(new ItemStack(Items.DIRT, 64));
		BlockPos[] barrels = {new BlockPos(1, 2, 4), new BlockPos(3, 2, 4), new BlockPos(4, 2, 4), new BlockPos(5, 2, 4),
			new BlockPos(1, 2, 5), new BlockPos(3, 2, 5), new BlockPos(4, 2, 5), new BlockPos(5, 2, 5)};
		helper.assertTrue(stock.size() <= barrels.length * 27, "test needs more barrels for " + entry.id());
		for (int i = 0; i < barrels.length; i++) {
			helper.setBlock(barrels[i], Blocks.BARREL);
			BaseContainerBlockEntity barrel = helper.getBlockEntity(barrels[i]);
			for (int slot = 0; slot < barrel.getContainerSize() && i * 27 + slot < stock.size(); slot++) {
				barrel.setItem(slot, stock.get(i * 27 + slot));
			}
		}
		helper.setBlock(BENCH, ModBlocks.BUILDERS_BENCH);
		helper.setBlock(CHEST, Blocks.CHEST);
		Villager villager = helper.spawn(EntityType.VILLAGER, VILLAGER);
		Builders.employ(level, villager, helper.absolutePos(BENCH));
		BuildSite site = Builders.start(level, villager, null, entry.id(), placement);
		BuildPlan plan = site.plan(level);
		if (plan == null) {
			throw new GameTestAssertException("blueprint not found: " + entry.id());
		}

		long[] last = {Long.MIN_VALUE};
		int[] since = {0};
		int[] worst = {0};
		String[] worstAt = {""};
		helper.onEachTick(() -> {
			if (site.isDone()) {
				return;
			}
			long mark = mark(site);
			boolean onShift = !villager.isSleeping() && villager.getBrain().isActive(Activity.WORK);
			if (mark != last[0] || !onShift) {
				last[0] = mark;
				since[0] = 0;
				return;
			}
			if (++since[0] > worst[0]) {
				worst[0] = since[0];
				worstAt[0] = "stage " + site.stage() + ", status " + site.status() + ", " + site.placed() + " placed, " + site.skipped()
					+ " skipped, builder at " + helper.relativePos(villager.blockPosition()) + ", detail " + (site.detail() == null ? "-" : site.detail().getString());
			}
		});
		helper.succeedWhen(() -> {
			helper.assertTrue(worst[0] < STALL_TICKS, "stalled " + worst[0] + " ticks on shift: " + worstAt[0]);
			helper.assertTrue(BuildSiteManager.get(level).get(site.id()) == null,
				"still building: stage " + site.stage() + ", status " + site.status() + ", " + site.placed() + " placed (worst gap so far "
					+ worst[0] + " ticks: " + worstAt[0] + ")");
			List<BlockPos> unfinished = plan.unfinished(level);
			helper.assertTrue(unfinished.isEmpty(), unfinished.size() + " block(s) wrong after the build, e.g. "
				+ unfinished.stream().limit(3).map(p -> helper.relativePos(p) + "=" + level.getBlockState(p)).toList());
		});
	}

	/** Changes whenever the site moves on: stage, cursor, retrying, placed or skipped (as the soak's StallWatch). */
	private static long mark(BuildSite site) {
		try {
			Field cursor = BuildSite.class.getDeclaredField("cursor");
			Field retrying = BuildSite.class.getDeclaredField("retrying");
			cursor.setAccessible(true);
			retrying.setAccessible(true);
			return ((((long) site.stage().ordinal() * 2 + (retrying.getBoolean(site) ? 1 : 0)) * 1_000_003L + cursor.getInt(site)) * 1_000_003L
				+ site.placed()) * 1_000_003L + site.skipped();
		} catch (ReflectiveOperationException e) {
			throw new IllegalStateException(e);
		}
	}
}
