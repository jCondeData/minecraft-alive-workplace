package io.github.jcondedata.aliveworkplace.compat;

import io.github.jcondedata.aliveworkplace.blueprint.BlueprintData;
import io.github.jcondedata.aliveworkplace.build.BuildSite;
import io.github.jcondedata.aliveworkplace.build.BuildSiteManager;
import io.github.jcondedata.aliveworkplace.build.Builders;
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

/**
 * QA (lane E, 2026-11-15) for Carved Wood's one-wood recipes in the owner's pack (Carved Wood 1.9.7-B, its default
 * config): a builder whose one-block blueprint asks for a plain barrel or chest, with the makings in his chest and a
 * Carpenter hired, gets it built the way a player in the pack would make it. The barrel works (the pack's plain barrel is
 * spruce-only, and the Carpenter makes it from spruce logs); the chest is B1302's (QaCarvedChest1115eBugCompatTests).
 * {@link #run} is the shared setup: a builder at his bench, his supply chest holding {@code stock}, a Carpenter at his
 * bench, and the blueprint's one block at {@link #SPOT}.
 */
public class QaCarvedChest1115eCompatTests implements FabricGameTest {
	static final String AREA = CompatGameTests.AREA;
	private static final BlockPos BENCH = new BlockPos(2, 2, 2);
	private static final BlockPos CHEST = new BlockPos(2, 2, 4);
	private static final BlockPos WORKBENCH = new BlockPos(14, 2, 2);
	private static final BlockPos SPOT = new BlockPos(7, 2, 9);

	/** The block with {@code id}; fails the test when it isn't installed. */
	static Block block(String id) {
		Block block = BuiltInRegistries.BLOCK.get(ResourceLocation.parse(id));
		if (block == Blocks.AIR) {
			throw new net.minecraft.gametest.framework.GameTestAssertException("not installed: " + id);
		}
		return block;
	}

	/** A plain barrel asked, 3 spruce logs (6 planks and 2 slabs: 9 planks) in the builder's chest, a Carpenter hired: the barrel is built. */
	//$ gametest_ticks_batch AREA '3000' '"qaCarvedChest1115eBarrel"'
	@GameTest(template = AREA, timeoutTicks = 3000, batch = "qaCarvedChest1115eBarrel")
	public void aPlainBarrelAskedWithSpruceLogsAndACarpenterIsBuilt(GameTestHelper helper) {
		run(helper, Blocks.BARREL, Blocks.BARREL, "barrel", new ItemStack(Items.SPRUCE_LOG, 3));
	}

	/**
	 * A builder whose blueprint is one {@code asked} at {@link #SPOT}, his chest holding {@code stock}, and a Carpenter
	 * hired: passes once the build is finished with {@code asked} (or {@code alternative}, what a player in the pack might
	 * build for it) standing there and the stock used.
	 */
	static void run(GameTestHelper helper, Block asked, Block alternative, String name, ItemStack stock) {
		PartnerShowsCompatTests.clearLeftovers(helper);
		FurnicrafterCompatTests.ownStoreOnly(helper);
		ServerLevel level = helper.getLevel();
		Village.RADIUS = 48;
		PartnerShowsCompatTests.after(helper, () -> Village.RADIUS = 0);
		helper.setDayTime(2000);
		level.getGameRules().getRule(ModGameRules.BUILD_DELAY).set(2, level.getServer());
		level.getGameRules().getRule(ModGameRules.BUILDERS_HELP).set(false, level.getServer());
		ResourceLocation id = oneBlock(helper, asked, name);
		helper.setBlock(BENCH, ModBlocks.BUILDERS_BENCH);
		helper.setBlock(CHEST, Blocks.CHEST);
		Container chest = helper.getBlockEntity(CHEST);
		Block given = Block.byItem(stock.getItem());
		chest.setItem(0, stock.copy());
		Villager builder = helper.spawn(EntityType.VILLAGER, BENCH.south());
		Builders.employ(level, builder, helper.absolutePos(BENCH));
		BuildSite site = Builders.start(level, builder, null, id, new BlueprintData.Placement(level.dimension().location(), helper.absolutePos(SPOT),
			Rotation.NONE, Mirror.NONE));
		helper.assertTrue(site != null, "the " + name + " build didn't start");
		helper.setBlock(WORKBENCH, ModBlocks.CARPENTERS_BENCH);
		Villager carpenter = helper.spawn(EntityType.VILLAGER, WORKBENCH.south());
		Jobs.employ(level, carpenter, helper.absolutePos(WORKBENCH), ModVillagers.CARPENTERS_BENCH_POI, ModVillagers.CARPENTER);
		helper.succeedWhen(() -> {
			helper.assertTrue(BuildSiteManager.get(level).get(site.id()) == null, "still building: " + site.stage() + " " + site.status()
				+ " missing " + site.missing() + "; the Carpenter made " + ModAttachments.ITEMS_CRAFTED.getOrElse(carpenter, 0) + ", chest holds "
				+ chest.countItem(stock.getItem()) + " of " + stock.getCount() + " " + BuiltInRegistries.ITEM.getKey(stock.getItem()));
			Block built = level.getBlockState(helper.absolutePos(SPOT)).getBlock();
			helper.assertTrue(built == asked || built == alternative, "built " + BuiltInRegistries.BLOCK.getKey(built) + " for "
				+ BuiltInRegistries.BLOCK.getKey(asked));
			helper.assertTrue(chest.countItem(stock.getItem()) < stock.getCount() || given == built,
				"the " + BuiltInRegistries.ITEM.getKey(stock.getItem()) + " in the chest weren't used for it");
		});
	}

	/** A blueprint of one {@code block}, drawn at {@link #SPOT} and taken away again. */
	private static ResourceLocation oneBlock(GameTestHelper helper, Block block, String name) {
		ServerLevel level = helper.getLevel();
		helper.setBlock(SPOT, block);
		ResourceLocation id = ResourceLocation.fromNamespaceAndPath("aliveworkplace_compat", "qa_carved_" + name + "_" + Long.toHexString(level.getGameTime()));
		level.getStructureManager().getOrCreate(id).fillFromWorld(level, helper.absolutePos(SPOT), new Vec3i(1, 1, 1), false, Blocks.STRUCTURE_VOID);
		helper.setBlock(SPOT, Blocks.AIR);
		return id;
	}
}
