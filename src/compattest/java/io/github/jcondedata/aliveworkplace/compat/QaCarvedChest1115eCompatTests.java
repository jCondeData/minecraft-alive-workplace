package io.github.jcondedata.aliveworkplace.compat;

import io.github.jcondedata.aliveworkplace.blueprint.BlueprintData;
import io.github.jcondedata.aliveworkplace.build.BuildSite;
import io.github.jcondedata.aliveworkplace.build.BuildSiteManager;
import io.github.jcondedata.aliveworkplace.build.Builders;
import io.github.jcondedata.aliveworkplace.craft.JobShortages;
import io.github.jcondedata.aliveworkplace.registry.ModAttachments;
import io.github.jcondedata.aliveworkplace.registry.ModBlocks;
import io.github.jcondedata.aliveworkplace.registry.ModGameRules;
import io.github.jcondedata.aliveworkplace.registry.ModVillagers;
import io.github.jcondedata.aliveworkplace.work.Jobs;
import io.github.jcondedata.aliveworkplace.work.Village;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Vec3i;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
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
import net.minecraft.world.level.block.state.BlockState;

/**
 * QA (lane E, 2026-11-15): in the owner's pack (Cobbleverse 1.7.42) Carved Wood turns the base game's #planks Barrel
 * recipe off: a plain minecraft:barrel (which it names "Spruce Barrel") is made from spruce planks and slabs only, and
 * oak makes a carved_wood:oak_barrel. So crafter.md's example (only oak logs, the Carpenter makes the barrels) can't hold
 * in the pack; what a player expects instead: the line names the makings a player in the pack would need (spruce logs,
 * never "Nobody here makes"), and with spruce logs the Carpenter makes the barrel and it's built. The plain chest, which
 * no shaped recipe in the pack makes, is QaCarvedChest1115eBugCompatTests.
 */
public class QaCarvedChest1115eCompatTests implements FabricGameTest {
	static final String AREA = CompatGameTests.AREA;
	static final BlockPos BENCH = new BlockPos(2, 2, 2);
	private static final BlockPos CHEST = new BlockPos(2, 2, 4);
	private static final BlockPos WORKBENCH = new BlockPos(14, 2, 2);
	private static final BlockPos ROW = new BlockPos(6, 2, 9);

	static Block block(String id) {
		return BuiltInRegistries.BLOCK.get(ResourceLocation.parse(id));
	}

	/**
	 * crafter.md's example in the pack: a plain barrel (named "Spruce Barrel" by Carved Wood, which makes it from spruce
	 * planks and slabs only) asked with only oak logs and a Carpenter hired: nothing is built from the oak, and the
	 * builder's line names what a player in the pack would need, 3 spruce logs, never "Nobody here makes".
	 */
	//$ gametest_ticks_batch AREA '1400' '"qaCarvedChest1115eBarrelOak"'
	@GameTest(template = AREA, timeoutTicks = 1400, batch = "qaCarvedChest1115eBarrelOak")
	public void aPlainBarrelWithOnlyOakLogsAsksForSpruceLogsTheBarrelsMakings(GameTestHelper helper) {
		ServerLevel level = prepare(helper);
		Container chest = builderChest(helper, new ItemStack(Items.OAK_LOG, 4));
		BuildSite site = startBuilder(helper, spaced(helper, Blocks.BARREL, "barreloak"));
		Villager carpenter = hire(helper);
		helper.runAfterDelay(1200, () -> {
			String report = report(helper, site, chest, carpenter);
			System.out.println("QaCarvedChest1115e barrel from oak: " + report);
			Component line = JobShortages.line(level, site.id(), site.missing(), helper.absolutePos(BENCH));
			String text = line == null ? null : line.getString();
			helper.assertTrue("Can't be made yet: short of 3 Spruce Logs to make 1 Spruce Barrel".equals(text), "the builder's line: " + report);
			helper.assertTrue(chest.countItem(Items.OAK_LOG) == 4 && ModAttachments.ITEMS_CRAFTED.getOrElse(carpenter, 0) == 0, "the oak was used: " + report);
			helper.succeed();
		});
	}

	/** The same barrel with 3 spruce logs in the builder's chest: the Carpenter makes it (planks, slabs, barrel) and it's built. */
	//$ gametest_ticks_batch AREA '3000' '"qaCarvedChest1115eBarrelSpruce"'
	@GameTest(template = AREA, timeoutTicks = 3000, batch = "qaCarvedChest1115eBarrelSpruce")
	public void aPlainBarrelWithSpruceLogsIsMadeByTheCarpenterAndBuilt(GameTestHelper helper) {
		run(helper, Blocks.BARREL, Blocks.BARREL, "barrelspruce", new ItemStack(Items.SPRUCE_LOG, 3));
	}

	static void run(GameTestHelper helper, Block wanted, Block carved, String what, ItemStack... supply) {
		ServerLevel level = prepare(helper);
		helper.assertTrue(carved != Blocks.AIR, "Carved Wood isn't loaded");
		Container chest = builderChest(helper, supply);
		BuildSite site = startBuilder(helper, spaced(helper, wanted, what));
		Villager carpenter = hire(helper);
		for (int t = 300; t < 3000; t += 600) {
			int at = t;
			helper.runAfterDelay(t, () -> System.out.println("QaCarvedChest1115e " + what + " @" + at + ": " + report(helper, site, chest, carpenter)));
		}
		helper.succeedWhen(() -> {
			BlockState built = helper.getBlockState(ROW);
			helper.assertTrue(built.is(wanted) || built.is(carved), "no " + what + " built: " + report(helper, site, chest, carpenter));
			helper.assertTrue(BuildSiteManager.get(level).get(site.id()) == null, "the " + what + " is there but the build isn't done: "
				+ report(helper, site, chest, carpenter));
		});
	}

	static String report(GameTestHelper helper, BuildSite site, Container chest, Villager carpenter) {
		Component line = JobShortages.line(helper.getLevel(), site.id(), site.missing(), helper.absolutePos(BENCH));
		StringBuilder s = new StringBuilder();
		for (int i = 0; i < chest.getContainerSize(); i++) {
			if (!chest.getItem(i).isEmpty()) {
				s.append(chest.getItem(i)).append(' ');
			}
		}
		return "status " + site.status() + ", missing " + site.missing() + ", skipped " + site.skipped() + ", line '" + (line == null ? null : line.getString())
			+ "', at the spot " + BuiltInRegistries.BLOCK.getKey(helper.getBlockState(ROW).getBlock()) + ", chest [" + s.toString().trim() + "], carpenter made "
			+ ModAttachments.ITEMS_CRAFTED.getOrElse(carpenter, 0) + ", job " + ModAttachments.CRAFTER_JOB.get(carpenter) + ", bag "
			+ ModAttachments.BUILDER_BAG.getOrCreate(carpenter).stacks();
	}

	static ServerLevel prepare(GameTestHelper helper) {
		PartnerShowsCompatTests.clearLeftovers(helper);
		FurnicrafterCompatTests.ownStoreOnly(helper);
		ServerLevel level = helper.getLevel();
		int radius = Village.RADIUS;
		Village.RADIUS = 48;
		PartnerShowsCompatTests.after(helper, () -> Village.RADIUS = radius);
		helper.setDayTime(2000);
		level.getGameRules().getRule(ModGameRules.BUILD_DELAY).set(2, level.getServer());
		level.getGameRules().getRule(ModGameRules.BUILDERS_HELP).set(false, level.getServer());
		return level;
	}

	static Container builderChest(GameTestHelper helper, ItemStack... items) {
		helper.setBlock(BENCH, ModBlocks.BUILDERS_BENCH);
		helper.setBlock(CHEST, Blocks.CHEST);
		Container chest = helper.getBlockEntity(CHEST);
		for (int i = 0; i < items.length; i++) {
			chest.setItem(i, items[i].copy());
		}
		return chest;
	}

	static BuildSite startBuilder(GameTestHelper helper, ResourceLocation id) {
		ServerLevel level = helper.getLevel();
		Villager builder = helper.spawn(EntityType.VILLAGER, BENCH.south());
		Builders.employ(level, builder, helper.absolutePos(BENCH));
		BuildSite site = Builders.start(level, builder, null, id, new BlueprintData.Placement(level.dimension().location(), helper.absolutePos(ROW),
			Rotation.NONE, Mirror.NONE));
		helper.assertTrue(site != null && site.plan(level) != null, "the build didn't start");
		return site;
	}

	static Villager hire(GameTestHelper helper) {
		helper.setBlock(WORKBENCH, ModBlocks.CARPENTERS_BENCH);
		Villager worker = helper.spawn(EntityType.VILLAGER, WORKBENCH.south());
		Jobs.employ(helper.getLevel(), worker, helper.absolutePos(WORKBENCH), ModVillagers.CARPENTERS_BENCH_POI, ModVillagers.CARPENTER);
		return worker;
	}

	/** A one-block blueprint of {@code block}, drawn where it's to be built and taken away. */
	static ResourceLocation spaced(GameTestHelper helper, Block block, String what) {
		ServerLevel level = helper.getLevel();
		helper.setBlock(ROW, block);
		ResourceLocation id = ResourceLocation.fromNamespaceAndPath("aliveworkplace_compat",
			"qa_carved_" + what + "_" + Long.toHexString(level.getGameTime()) + "_" + helper.absolutePos(ROW).getX());
		level.getStructureManager().getOrCreate(id).fillFromWorld(level, helper.absolutePos(ROW), new Vec3i(1, 1, 1), false, Blocks.STRUCTURE_VOID);
		helper.setBlock(ROW, Blocks.AIR);
		return id;
	}
}
