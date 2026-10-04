package io.github.jcondedata.aliveworkplace.test;

import io.github.jcondedata.aliveworkplace.blueprint.BlueprintData;
import io.github.jcondedata.aliveworkplace.blueprint.BlueprintLibrary;
import io.github.jcondedata.aliveworkplace.blueprint.StarterBlueprints;
import io.github.jcondedata.aliveworkplace.build.BuildPlan;
import io.github.jcondedata.aliveworkplace.build.BuildSite;
import io.github.jcondedata.aliveworkplace.build.Builders;
import io.github.jcondedata.aliveworkplace.registry.ModAttachments;
import io.github.jcondedata.aliveworkplace.registry.ModBlocks;
import io.github.jcondedata.aliveworkplace.registry.ModGameRules;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Container;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.entity.BaseContainerBlockEntity;
import net.minecraft.world.level.levelgen.structure.BoundingBox;

/**
 * 23.1a (owner, 2026-10-04): a build should take about half the time with two builders on it, and so on. The stone
 * house (416 blocks) from exactly its material list, at the normal build speed: first by its builder alone, then
 * taken down and built again with one helper, then again with three (the most a site takes). The work day is held at
 * mid-morning, so only working ticks count.
 *
 * <p>Measured when this was written: alone 4200 ticks; two 2110-2117 (50%); four 1224-1468 (29-35%). Before 23.1a the
 * helpers fetched a handful per block, waited out the lead's end of each stage and all chased the block right after
 * the lead's: two took 2650-2997 (63-71%), four 1920-1958 (46%).
 */
public class BuilderCrewGameTests implements net.fabricmc.fabric.api.gametest.v1.FabricGameTest {
	private static final String HUGE = "aliveworkplace_test:huge_area";
	private static final BlockPos BENCH = new BlockPos(2, 2, 2);
	private static final BlockPos CHEST = new BlockPos(2, 2, 4);
	private static final BlockPos ORIGIN = new BlockPos(12, 2, 12);
	private static final BlockPos LEAD_START = new BlockPos(3, 2, 3);
	private static final BlockPos[] HELPER_BENCHES = {new BlockPos(7, 2, 2), new BlockPos(10, 2, 2), new BlockPos(13, 2, 2)};
	private static final BlockPos[] BARRELS = {new BlockPos(1, 2, 4), new BlockPos(3, 2, 4), new BlockPos(4, 2, 4),
		new BlockPos(5, 2, 4), new BlockPos(1, 2, 5), new BlockPos(3, 2, 5), new BlockPos(4, 2, 5), new BlockPos(5, 2, 5)};
	/** The crews built with, in turn. */
	private static final int[] CREWS = {1, 2, 4};
	/** The most a crew of {@code CREWS[i]} may take, as a share of the time alone: a half and a quarter, with slack. */
	private static final double[] MOST = {1, 0.62, 0.42};

	//$ gametest_ticks_batch HUGE '20000' '"crewScaling"'
	@GameTest(template = HUGE, timeoutTicks = 20000, batch = "crewScaling")
	public void aCrewBuildsInAboutTheTimeOfOneBuilderDividedByItsSize(GameTestHelper helper) {
		Leftovers.clear(helper);
		ServerLevel level = helper.getLevel();
		helper.setDayTime(3000);
		level.getGameRules().getRule(ModGameRules.BUILD_DELAY).set(8, level.getServer());
		level.getGameRules().getRule(ModGameRules.BUILDERS_HELP).set(true, level.getServer());
		helper.setBlock(BENCH, ModBlocks.BUILDERS_BENCH);
		helper.setBlock(CHEST, Blocks.CHEST);
		for (BlockPos barrel : BARRELS) {
			helper.setBlock(barrel, Blocks.BARREL);
		}
		Villager lead = helper.spawn(EntityType.VILLAGER, LEAD_START);
		Builders.employ(level, lead, helper.absolutePos(BENCH));
		List<Villager> crew = new ArrayList<>(List.of(lead));

		int[] phase = {0};
		int[] startedAt = {0};
		int[] took = new int[CREWS.length];
		BuildSite[] site = {start(helper, crew, CREWS[0])};
		helper.onEachTick(() -> {
			helper.setDayTime(3000); // a working day that doesn't end
			if (phase[0] >= CREWS.length || !site[0].isDone()) {
				return;
			}
			int p = phase[0];
			took[p] = (int) helper.getTick() - startedAt[0];
			BuildPlan plan = site[0].plan(level);
			if (plan != null && !plan.unfinished(level).isEmpty()) {
				throw new GameTestAssertException("crew of " + CREWS[p] + ": the stone house isn't finished: "
					+ plan.unfinished(level).size() + " blocks left");
			}
			StringBuilder shares = new StringBuilder();
			int least = Integer.MAX_VALUE;
			int total = 0;
			for (Villager v : crew) {
				int n = site[0].placedBy(v.getUUID(), false);
				shares.append(' ').append(n);
				least = Math.min(least, n);
				total += n;
			}
			System.out.println("[23.1a] a crew of " + CREWS[p] + " built the stone house in " + took[p] + " ticks"
				+ (p > 0 ? String.format(" (%.0f%% of the time alone)", 100.0 * took[p] / took[0]) : "") + "; blocks each:" + shares);
			// Everyone pulls their weight: nobody places less than half an even share.
			if (least * CREWS[p] * 2 < total) {
				throw new GameTestAssertException("a builder of the crew of " + CREWS[p] + " placed only " + least + " of " + total
					+ " blocks (each:" + shares + ")");
			}
			if (took[p] > took[0] * MOST[p]) {
				throw new GameTestAssertException(String.format("a crew of %d took %d ticks, %.0f%% of the %d ticks alone (at most %.0f%%)",
					CREWS[p], took[p], 100.0 * took[p] / took[0], took[0], 100 * MOST[p]));
			}
			phase[0]++;
			if (phase[0] < CREWS.length) {
				startedAt[0] = (int) helper.getTick();
				site[0] = start(helper, crew, CREWS[phase[0]]);
			}
		});
		helper.succeedWhen(() -> helper.assertTrue(phase[0] >= CREWS.length, "still building with a crew of "
			+ CREWS[Math.min(phase[0], CREWS.length - 1)] + ": stage=" + site[0].stage() + " placed=" + site[0].placed()));
	}

	/**
	 * One of two helpers leaves mid-build (gone, as if it wandered off) while it has a block claimed: the lead and the
	 * other helper finish the build, including the blocks it had and anything the lead had left to it.
	 */
	//$ gametest_ticks_batch HUGE '12000' '"crewHelperLeaves"'
	@GameTest(template = HUGE, timeoutTicks = 12000, batch = "crewHelperLeaves")
	public void theLeadFinishesWhenAHelperLeavesMidBuild(GameTestHelper helper) {
		Leftovers.clear(helper);
		ServerLevel level = helper.getLevel();
		helper.setDayTime(3000);
		level.getGameRules().getRule(ModGameRules.BUILD_DELAY).set(2, level.getServer());
		level.getGameRules().getRule(ModGameRules.BUILDERS_HELP).set(true, level.getServer());
		helper.setBlock(BENCH, ModBlocks.BUILDERS_BENCH);
		helper.setBlock(CHEST, Blocks.CHEST);
		for (BlockPos barrel : BARRELS) {
			helper.setBlock(barrel, Blocks.BARREL);
		}
		Villager lead = helper.spawn(EntityType.VILLAGER, LEAD_START);
		Builders.employ(level, lead, helper.absolutePos(BENCH));
		List<Villager> crew = new ArrayList<>(List.of(lead));
		BuildSite site = start(helper, crew, 3);
		boolean[] left = {false};
		helper.onEachTick(() -> {
			helper.setDayTime(3000);
			if (!left[0] && site.stage() == io.github.jcondedata.aliveworkplace.build.BuildPlan.Stage.STRUCTURE && site.placed() > 80
				&& site.claim(crew.get(1).getUUID()) != null) {
				left[0] = true;
				// What it carried is back in the chest (this is about the blocks it had claimed, not lost materials).
				Container chest = helper.getBlockEntity(CHEST);
				for (ItemStack stack : ModAttachments.BUILDER_BAG.getOrCreate(crew.get(1)).takeAll()) {
					for (int slot = 0; slot < chest.getContainerSize() && !stack.isEmpty(); slot++) {
						if (chest.getItem(slot).isEmpty()) {
							chest.setItem(slot, stack.copy());
							stack.setCount(0);
						}
					}
				}
				crew.get(1).discard();
			}
		});
		helper.succeedWhen(() -> {
			helper.assertTrue(left[0], "setup: the helper never had a block claimed in the structure");
			BuildPlan plan = site.plan(level);
			helper.assertTrue(site.isDone(), "still building after a helper left: stage=" + site.stage() + " status=" + site.status()
				+ " placed=" + site.placed());
			helper.assertTrue(plan == null || plan.unfinished(level).isEmpty(), "the stone house isn't finished after a helper left: "
				+ (plan == null ? "?" : plan.unfinished(level).size()) + " blocks left");
		});
	}

	/**
	 * Takes down what's there, sends the crew (grown to {@code size}) back to their benches with empty bags, puts exactly
	 * the stone house's materials in the barrels and starts the build.
	 */
	private static BuildSite start(GameTestHelper helper, List<Villager> crew, int size) {
		ServerLevel level = helper.getLevel();
		BlueprintData.Placement placement = new BlueprintData.Placement(level.dimension().location(), helper.absolutePos(ORIGIN),
			Rotation.NONE, Mirror.NONE);
		BuildPlan bare = BuildPlan.create(BlueprintLibrary.get(level.getServer(), StarterBlueprints.STONE_HOUSE.id()).orElseThrow(), placement);
		BoundingBox box = bare.bounds();
		for (BlockPos pos : BlockPos.betweenClosed(box.minX(), box.minY(), box.minZ(), box.maxX(), box.maxY(), box.maxZ())) {
			level.setBlock(pos, Blocks.AIR.defaultBlockState(), 2);
		}
		while (crew.size() < size) {
			BlockPos bench = HELPER_BENCHES[crew.size() - 1];
			helper.setBlock(bench, ModBlocks.BUILDERS_BENCH);
			Villager mate = helper.spawn(EntityType.VILLAGER, bench.offset(1, 0, 1));
			Builders.employ(level, mate, helper.absolutePos(bench));
			crew.add(mate);
		}
		for (int i = 0; i < crew.size(); i++) {
			Villager v = crew.get(i);
			ModAttachments.BUILDER_BAG.getOrCreate(v).takeAll();
			BlockPos at = helper.absolutePos(i == 0 ? LEAD_START : HELPER_BENCHES[i - 1].offset(1, 0, 1));
			v.teleportTo(at.getX() + 0.5, at.getY(), at.getZ() + 0.5);
		}
		BuildSite site = Builders.start(level, crew.get(0), null, StarterBlueprints.STONE_HOUSE.id(), placement);
		if (site == null) {
			throw new GameTestAssertException("the stone house didn't start for a crew of " + size);
		}
		BuildPlan plan = site.plan(level);
		if (plan == null) {
			throw new GameTestAssertException("no plan for the stone house");
		}
		Container chest = helper.getBlockEntity(CHEST);
		chest.clearContent();
		stock(helper, plan.materials());
		return site;
	}

	/** Exactly the build's materials, in the barrels next to the chest (emptied first). */
	private static void stock(GameTestHelper helper, Map<Item, Integer> materials) {
		List<ItemStack> stock = new ArrayList<>();
		for (Map.Entry<Item, Integer> e : materials.entrySet()) {
			int left = e.getValue();
			while (left > 0) {
				int n = Math.min(left, e.getKey().getDefaultMaxStackSize());
				stock.add(new ItemStack(e.getKey(), n));
				left -= n;
			}
		}
		helper.assertTrue(stock.size() <= BARRELS.length * 27, "test needs more barrels: " + stock.size() + " stacks");
		for (int i = 0; i < BARRELS.length; i++) {
			BaseContainerBlockEntity barrel = helper.getBlockEntity(BARRELS[i]);
			barrel.clearContent();
			for (int slot = 0; slot < barrel.getContainerSize() && i * 27 + slot < stock.size(); slot++) {
				barrel.setItem(slot, stock.get(i * 27 + slot));
			}
		}
	}
}
