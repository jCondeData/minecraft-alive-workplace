package io.github.jcondedata.aliveworkplace.test;

import io.github.jcondedata.aliveworkplace.blueprint.BlueprintData;
import io.github.jcondedata.aliveworkplace.build.BuildPlan;
import io.github.jcondedata.aliveworkplace.build.BuildSite;
import io.github.jcondedata.aliveworkplace.build.BuildSiteManager;
import io.github.jcondedata.aliveworkplace.build.Builders;
import io.github.jcondedata.aliveworkplace.registry.ModAttachments;
import io.github.jcondedata.aliveworkplace.registry.ModBlocks;
import io.github.jcondedata.aliveworkplace.registry.ModGameRules;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Container;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;

/**
 * QA (lane E, 2026-10-27), from the builder soak (23.1) on a real pack server: 35 of 37 builds, one builder stuck for
 * good "waiting for 2 lanterns" with none in his chests, while 5 lay in two other builders' chests 26 and 55 blocks
 * away (nothing lost: the ledger added up). The wiki (builder.md): a helper's bag "empties at the chests when it
 * finishes or stops helping"; {@code Builders.stopHelping}: "hands back anything fetched for the site". So what a helper
 * took from the lead's chests and didn't use goes back to the lead's chests, never into the helper's own, where the
 * lead's next build can't reach it.
 *
 * <p>Two helpers with their own chest by their tables (outside the lead's supply radius), the lead's chest holding twice
 * the hut's materials: once the hut is done and every bag is empty, the spare half is all in the lead's chest and none
 * of it in the helpers'.
 */
public class QaHelperLeftoversGameTests implements net.fabricmc.fabric.api.gametest.v1.FabricGameTest {
	private static final String AREA = "aliveworkplace_test:build_area";
	private static final org.slf4j.Logger LOGGER = org.slf4j.LoggerFactory.getLogger("aliveworkplace_test");
	private static final ResourceLocation TEST_HUT = ResourceLocation.fromNamespaceAndPath("aliveworkplace_test", "test_hut");
	private static final BlockPos TABLE = new BlockPos(2, 2, 2);
	private static final BlockPos CHEST = new BlockPos(2, 2, 4);
	private static final BlockPos LEAD = new BlockPos(3, 2, 3);
	private static final BlockPos HUT = new BlockPos(6, 2, 6);
	private static final BlockPos[] MATE_TABLES = {new BlockPos(13, 2, 2), new BlockPos(15, 2, 2)};
	/** The helpers' own chest: within the supply radius (8) of both their tables, 12 from the lead's. */
	private static final BlockPos MATES_CHEST = new BlockPos(14, 2, 4);

	static void prepare(GameTestHelper helper) {
		Leftovers.clear(helper);
		Leftovers.finished(helper);
		ServerLevel level = helper.getLevel();
		helper.setDayTime(2000);
		boolean protection = io.github.jcondedata.aliveworkplace.hall.VillageProtection.ENABLED;
		io.github.jcondedata.aliveworkplace.hall.VillageProtection.ENABLED = false;
		var rules = level.getGameRules();
		boolean help = rules.getRule(ModGameRules.BUILDERS_HELP).get();
		int delay = rules.getRule(ModGameRules.BUILD_DELAY).get();
		rules.getRule(ModGameRules.BUILDERS_HELP).set(true, level.getServer());
		rules.getRule(ModGameRules.BUILD_DELAY).set(2, level.getServer());
		Leftovers.after(helper, () -> {
			io.github.jcondedata.aliveworkplace.hall.VillageProtection.ENABLED = protection;
			rules.getRule(ModGameRules.BUILDERS_HELP).set(help, level.getServer());
			rules.getRule(ModGameRules.BUILD_DELAY).set(delay, level.getServer());
		});
		BuildSiteManager sites = BuildSiteManager.get(level);
		for (BuildSite other : new ArrayList<>(sites.all())) {
			if (other.bench() != null && other.bench().closerThan(helper.absolutePos(TABLE), 3 * Builders.MAX_SITE_DISTANCE)) {
				sites.remove(other.id());
			}
		}
	}

	static int count(Container chest, Map<Item, Integer> materials) {
		int n = 0;
		for (Item item : materials.keySet()) {
			n += chest.countItem(item);
		}
		return n;
	}

	static Map<Item, Integer> in(Container chest, Map<Item, Integer> materials) {
		Map<Item, Integer> out = new LinkedHashMap<>();
		for (Item item : materials.keySet()) {
			if (chest.countItem(item) > 0) {
				out.put(item, chest.countItem(item));
			}
		}
		return out;
	}

	static boolean bagEmpty(Villager v) {
		return ModAttachments.BUILDER_BAG.getOrCreate(v).stacks().stream().allMatch(ItemStack::isEmpty);
	}

	//$ gametest_ticks_batch AREA '4000' '"qaHelperLeftovers"'
	@GameTest(template = AREA, timeoutTicks = 4000, batch = "qaHelperLeftovers")
	public void whatAHelperTookAndDidntUseGoesBackToTheLeadsChest(GameTestHelper helper) {
		prepare(helper);
		ServerLevel level = helper.getLevel();
		helper.setBlock(TABLE, ModBlocks.BUILDERS_BENCH);
		helper.setBlock(CHEST, Blocks.CHEST);
		helper.setBlock(MATES_CHEST, Blocks.CHEST);
		Villager lead = helper.spawn(EntityType.VILLAGER, LEAD);
		Builders.employ(level, lead, helper.absolutePos(TABLE));
		Villager[] mates = new Villager[MATE_TABLES.length];
		for (int i = 0; i < mates.length; i++) {
			helper.setBlock(MATE_TABLES[i], ModBlocks.BUILDERS_BENCH);
			mates[i] = helper.spawn(EntityType.VILLAGER, MATE_TABLES[i].south().east());
			Builders.employ(level, mates[i], helper.absolutePos(MATE_TABLES[i]));
		}
		BlueprintData.Placement placement = new BlueprintData.Placement(level.dimension().location(), helper.absolutePos(HUT), Rotation.NONE, Mirror.NONE);
		BuildSite site = Builders.start(level, lead, null, TEST_HUT, placement);
		helper.assertTrue(site != null, "setup: the hut didn't start");
		BuildPlan plan = site.plan(level);
		helper.assertTrue(plan != null, "setup: no plan for the test hut");
		Map<Item, Integer> materials = plan.materials();
		Container chest = helper.getBlockEntity(CHEST);
		Container matesChest = helper.getBlockEntity(MATES_CHEST);
		int slot = 0;
		for (Map.Entry<Item, Integer> e : materials.entrySet()) {
			int left = e.getValue() * 2;
			while (left > 0) {
				int n = Math.min(left, e.getKey().getDefaultMaxStackSize());
				chest.setItem(slot++, new ItemStack(e.getKey(), n));
				left -= n;
			}
		}
		Leftovers.after(helper, () -> BuildSiteManager.get(level).remove(site.id()));
		boolean[] helped = {false};
		int[] most = {0};
		helper.onEachTick(() -> {
			helper.setDayTime(2000);
			for (Villager mate : mates) {
				helped[0] |= Builders.isHelping(mate);
			}
			most[0] = Math.max(most[0], count(matesChest, materials));
		});
		helper.succeedWhen(() -> {
			helper.assertTrue(BuildSiteManager.get(level).get(site.id()) == null, "the hut isn't finished: stage " + site.stage() + ", status " + site.status());
			helper.assertTrue(helped[0], "no idle builder ever helped");
			for (Villager v : mates) {
				helper.assertTrue(!Builders.isHelping(v) && bagEmpty(v), "a helper is still helping or carrying");
			}
			helper.assertTrue(bagEmpty(lead), "the lead's bag isn't empty");
			helper.assertTrue(most[0] == 0, "the helpers' own chest got " + most[0] + " of the lead's materials (" + in(matesChest, materials)
				+ "), out of the lead's reach");
			helper.assertTrue(in(chest, materials).equals(materials), "the lead's chest should hold exactly the spare " + materials
				+ " but holds " + in(chest, materials));
			LOGGER.info("qa helper leftovers: the spare is all back in the lead's chest, none in the helpers'");
		});
	}

	/**
	 * The soak's case in small: the lead has a second hut queued and his chest holds exactly what both need. Two helpers
	 * help with the first; the second still finishes from the lead's own chest, and nothing ends up in the helpers'.
	 */
	//$ gametest_ticks_batch AREA '6000' '"qaHelperLeftoversQueue"'
	@GameTest(template = AREA, timeoutTicks = 6000, batch = "qaHelperLeftoversQueue")
	public void theLeadsQueuedBuildStillFindsItsMaterialsAfterTheHelpersLeave(GameTestHelper helper) {
		prepare(helper);
		ServerLevel level = helper.getLevel();
		helper.setBlock(TABLE, ModBlocks.BUILDERS_BENCH);
		helper.setBlock(CHEST, Blocks.CHEST);
		helper.setBlock(MATES_CHEST, Blocks.CHEST);
		Villager lead = helper.spawn(EntityType.VILLAGER, LEAD);
		Builders.employ(level, lead, helper.absolutePos(TABLE));
		Villager[] mates = new Villager[MATE_TABLES.length];
		for (int i = 0; i < mates.length; i++) {
			helper.setBlock(MATE_TABLES[i], ModBlocks.BUILDERS_BENCH);
			mates[i] = helper.spawn(EntityType.VILLAGER, MATE_TABLES[i].south().east());
			Builders.employ(level, mates[i], helper.absolutePos(MATE_TABLES[i]));
		}
		BlueprintData.Placement first = new BlueprintData.Placement(level.dimension().location(), helper.absolutePos(HUT), Rotation.NONE, Mirror.NONE);
		BuildSite one = Builders.start(level, lead, null, TEST_HUT, first);
		helper.assertTrue(one != null, "setup: the first hut didn't start");
		BuildPlan plan = one.plan(level);
		helper.assertTrue(plan != null, "setup: no plan for the test hut");
		Map<Item, Integer> materials = plan.materials();
		// The second hut north of the first, inside the area (the first stands at x 6-10, z 6-10).
		BlueprintData.Placement second = new BlueprintData.Placement(level.dimension().location(), helper.absolutePos(new BlockPos(6, 2, 0)),
			Rotation.NONE, Mirror.NONE);
		BuildSite two = Builders.enqueue(level, lead, null, TEST_HUT, second);
		helper.assertTrue(two != null, "setup: the second hut wasn't queued");
		Container chest = helper.getBlockEntity(CHEST);
		Container matesChest = helper.getBlockEntity(MATES_CHEST);
		int slot = 0;
		for (Map.Entry<Item, Integer> e : materials.entrySet()) {
			int left = e.getValue() * 2;
			while (left > 0) {
				int n = Math.min(left, e.getKey().getDefaultMaxStackSize());
				chest.setItem(slot++, new ItemStack(e.getKey(), n));
				left -= n;
			}
		}
		Leftovers.after(helper, () -> {
			BuildSiteManager.get(level).remove(one.id());
			BuildSiteManager.get(level).remove(two.id());
		});
		boolean[] helped = {false};
		int[] most = {0};
		helper.onEachTick(() -> {
			helper.setDayTime(2000);
			for (Villager mate : mates) {
				helped[0] |= Builders.isHelping(mate);
			}
			most[0] = Math.max(most[0], count(matesChest, materials));
		});
		helper.succeedWhen(() -> {
			helper.assertTrue(helped[0], "no idle builder ever helped");
			helper.assertTrue(most[0] == 0, "the helpers' own chest got " + most[0] + " of the lead's materials (" + in(matesChest, materials)
				+ "), out of the lead's reach; the second hut: stage " + two.stage() + ", status " + two.status() + ", missing " + two.missing());
			helper.assertTrue(BuildSiteManager.get(level).get(one.id()) == null, "the first hut isn't finished: " + one.stage() + " " + one.status());
			helper.assertTrue(BuildSiteManager.get(level).get(two.id()) == null, "the second hut isn't finished: stage " + two.stage() + ", status "
				+ two.status() + ", missing " + two.missing());
			LOGGER.info("qa helper leftovers: both huts built from the lead's chest, none of it in the helpers'");
		});
	}
}
