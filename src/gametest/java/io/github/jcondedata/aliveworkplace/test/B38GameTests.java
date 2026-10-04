package io.github.jcondedata.aliveworkplace.test;

import io.github.jcondedata.aliveworkplace.blueprint.BlueprintData;
import io.github.jcondedata.aliveworkplace.blueprint.StarterBlueprints;
import io.github.jcondedata.aliveworkplace.build.BuildPlan;
import io.github.jcondedata.aliveworkplace.build.BuildSite;
import io.github.jcondedata.aliveworkplace.build.BuildSiteManager;
import io.github.jcondedata.aliveworkplace.build.Builders;
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
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.schedule.Activity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.entity.BaseContainerBlockEntity;

/**
 * B38: the soak's tinker's workshop builder waited 30 s on shift in STRUCTURE with WAITING_FOR_MATERIALS and nothing
 * missing. Given exactly the workshop's material list in barrels, a builder (alone, and with a helper) never waits on
 * shift with an empty missing list for 600 ticks, and finishes.
 */
public class B38GameTests implements net.fabricmc.fabric.api.gametest.v1.FabricGameTest {
	private static final String HUGE = "aliveworkplace_test:huge_area";
	private static final BlockPos BENCH = new BlockPos(2, 2, 2);
	private static final BlockPos HELPER_BENCH = new BlockPos(7, 2, 2);
	private static final BlockPos ORIGIN = new BlockPos(12, 2, 12);
	/** How long a wait with nothing missing may last on shift (the bug's 30 s is 600 ticks). */
	private static final int LIMIT = 600;

	//$ gametest_ticks_batch HUGE '40000' '"b38TinkersAlone"'
	@GameTest(template = HUGE, timeoutTicks = 40000, batch = "b38TinkersAlone")
	public void b38TheTinkersWorkshopBuilderNeverWaitsWithNothingMissing(GameTestHelper helper) {
		Leftovers.clear(helper);
		build(helper, false);
	}

	//$ gametest_ticks_batch HUGE '40000' '"b38TinkersCrew"'
	@GameTest(template = HUGE, timeoutTicks = 40000, batch = "b38TinkersCrew")
	public void b38TheTinkersWorkshopCrewNeverWaitsWithNothingMissing(GameTestHelper helper) {
		Leftovers.clear(helper);
		build(helper, true);
	}

	/**
	 * The case found: the builder carries a free variant (one family, as Chipped's or Rechiseled's) of what the next
	 * block needs, and its chests hold none. The missing list counts the whole family in the bag, so nothing is missing;
	 * the builder must turn the variant into the block it needs, not wait.
	 */
	//$ gametest_ticks_batch HUGE '40000' '"b38Variant"'
	@GameTest(template = HUGE, timeoutTicks = 40000, batch = "b38Variant")
	public void b38ABuilderCarryingAVariantUsesIt(GameTestHelper helper) {
		Leftovers.clear(helper);
		List<List<Item>> before = io.github.jcondedata.aliveworkplace.build.MaterialFamilies.dataFamilies();
		Leftovers.after(helper, () -> io.github.jcondedata.aliveworkplace.build.MaterialFamilies.setDataFamilies(before));
		build(helper, false, true);
	}

	private void build(GameTestHelper helper, boolean withHelper) {
		build(helper, withHelper, false);
	}

	private void build(GameTestHelper helper, boolean withHelper, boolean variantInBag) {
		ServerLevel level = helper.getLevel();
		helper.setDayTime(2000);
		level.getGameRules().getRule(ModGameRules.BUILD_DELAY).set(2, level.getServer());
		level.getGameRules().getRule(ModGameRules.BUILDERS_HELP).set(withHelper, level.getServer());
		helper.setBlock(BENCH, ModBlocks.BUILDERS_BENCH);
		Villager villager = helper.spawn(EntityType.VILLAGER, new BlockPos(3, 2, 3));
		Builders.employ(level, villager, helper.absolutePos(BENCH));
		BlueprintData.Placement placement = new BlueprintData.Placement(level.dimension().location(), helper.absolutePos(ORIGIN),
			Rotation.NONE, Mirror.NONE);
		BuildSite site = Builders.start(level, villager, null, StarterBlueprints.TINKERS_WORKSHOP.id(), placement);
		helper.assertTrue(site != null, "the tinker's workshop didn't start");
		BuildPlan plan = site.plan(level);
		if (plan == null) {
			throw new GameTestAssertException("no plan for the tinker's workshop");
		}
		Map<Item, Integer> materials = new java.util.LinkedHashMap<>(plan.materials());
		if (variantInBag) {
			// The two least-used block materials become one family; the bag carries both counts as the second one.
			List<Map.Entry<Item, Integer>> blocks = materials.entrySet().stream()
				.filter(e -> e.getKey() instanceof net.minecraft.world.item.BlockItem)
				.sorted(Map.Entry.comparingByValue()).toList();
			helper.assertTrue(blocks.size() >= 2, "setup: the workshop uses fewer than two kinds of block");
			Item a = blocks.get(0).getKey();
			Item b = blocks.get(1).getKey();
			List<List<Item>> families = new ArrayList<>(io.github.jcondedata.aliveworkplace.build.MaterialFamilies.dataFamilies());
			families.add(List.of(a, b));
			io.github.jcondedata.aliveworkplace.build.MaterialFamilies.setDataFamilies(families);
			int both = materials.remove(a) + materials.remove(b);
			io.github.jcondedata.aliveworkplace.registry.ModAttachments.BUILDER_BAG.getOrCreate(villager).addAll(b, both);
		}
		List<ItemStack> stock = new ArrayList<>();
		for (Map.Entry<Item, Integer> e : materials.entrySet()) {
			for (int left = e.getValue(); left > 0; left -= e.getKey().getDefaultMaxStackSize()) {
				stock.add(new ItemStack(e.getKey(), Math.min(left, e.getKey().getDefaultMaxStackSize())));
			}
		}
		BlockPos[] barrels = {new BlockPos(1, 2, 4), new BlockPos(2, 2, 4), new BlockPos(3, 2, 4), new BlockPos(4, 2, 4),
			new BlockPos(5, 2, 4), new BlockPos(1, 2, 5), new BlockPos(2, 2, 5), new BlockPos(3, 2, 5), new BlockPos(4, 2, 5),
			new BlockPos(5, 2, 5)};
		helper.assertTrue(stock.size() <= barrels.length * 27, "setup: needs more barrels for " + stock.size() + " stacks");
		for (int i = 0; i < barrels.length; i++) {
			helper.setBlock(barrels[i], Blocks.BARREL);
			BaseContainerBlockEntity barrel = helper.getBlockEntity(barrels[i]);
			for (int slot = 0; slot < barrel.getContainerSize() && i * 27 + slot < stock.size(); slot++) {
				barrel.setItem(slot, stock.get(i * 27 + slot));
			}
		}
		if (withHelper) {
			helper.setBlock(HELPER_BENCH, ModBlocks.BUILDERS_BENCH);
			Villager mate = helper.spawn(EntityType.VILLAGER, new BlockPos(8, 2, 3));
			Builders.employ(level, mate, helper.absolutePos(HELPER_BENCH));
		}
		int[] emptyFor = {0};
		String[] stall = {null};
		helper.onEachTick(() -> {
			boolean onShift = villager.getBrain().isActive(Activity.WORK);
			if (!site.isDone() && onShift && site.status() == BuildSite.Status.WAITING_FOR_MATERIALS && site.missing().isEmpty()) {
				if (++emptyFor[0] >= LIMIT && stall[0] == null) {
					stall[0] = "stage " + site.stage() + " at tick " + helper.getTick() + ", builder at " + villager.blockPosition().toShortString()
						+ ", bag " + io.github.jcondedata.aliveworkplace.registry.ModAttachments.BUILDER_BAG.getOrCreate(villager);
				}
			} else {
				emptyFor[0] = 0;
			}
		});
		helper.succeedWhen(() -> {
			helper.assertTrue(stall[0] == null, "B38: waited " + LIMIT + " ticks on shift with nothing missing (" + stall[0] + ")");
			helper.assertTrue(BuildSiteManager.get(level).get(site.id()) == null || site.isDone(),
				"still building: stage=" + site.stage() + " status=" + site.status() + " missing=" + site.missing());
		});
	}
}
