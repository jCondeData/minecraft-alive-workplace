package io.github.jcondedata.aliveworkplace.test;

import io.github.jcondedata.aliveworkplace.blueprint.BlueprintData;
import io.github.jcondedata.aliveworkplace.build.BuildSite;
import io.github.jcondedata.aliveworkplace.build.BuildSiteManager;
import io.github.jcondedata.aliveworkplace.build.Builders;
import io.github.jcondedata.aliveworkplace.build.Scaffolds;
import io.github.jcondedata.aliveworkplace.build.Upkeep;
import io.github.jcondedata.aliveworkplace.registry.ModAttachments;
import io.github.jcondedata.aliveworkplace.registry.ModBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Vec3i;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Container;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.LadderBlock;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;

/**
 * QA (tester, 2026-10-24), from B261's spec: the case its tests leave out, the chest inside the building under repair
 * broken mid-job. The builder must not finish from goods that are gone (nor hang): it waits, and a barrel put back in
 * the shed with the missing blocks lets it finish from that barrel.
 */
public class QaStoreSupplyGameTests {
	private static final String BIG = "aliveworkplace_test:big_area";
	/** The test shed (as in B261GameTests): walls x 6-10, z 6-10, y 2-3, a roof at y 4, its door at x 8 on the front. */
	private static final BlockPos SHED = new BlockPos(6, 2, 6);
	private static final BlockPos BENCH = new BlockPos(8, 2, 4);
	private static final BlockPos HAY = new BlockPos(9, 2, 9);
	private static final BlockPos LADDER = new BlockPos(9, 2, 7);
	private static final BlockPos STORE = new BlockPos(7, 2, 8);

	//$ gametest_ticks_batch BIG '3000' '"qaStoreChestBroken"'
	@GameTest(template = BIG, timeoutTicks = 3000, batch = "qaStoreChestBroken")
	public void aRepairsChestBrokenMidJobHoldsItUntilABarrelIsPutBack(GameTestHelper helper) {
		Leftovers.clear(helper);
		Leftovers.finished(helper);
		liveServer(helper);
		ServerLevel level = helper.getLevel();
		for (int x = 6; x <= 10; x++) {
			for (int z = 6; z <= 10; z++) {
				boolean wall = x == 6 || x == 10 || z == 6 || z == 10;
				for (int y = 2; y <= 3; y++) {
					helper.setBlock(new BlockPos(x, y, z), wall ? Blocks.COBBLESTONE : Blocks.AIR);
				}
				helper.setBlock(new BlockPos(x, 4, z), Blocks.OAK_PLANKS);
			}
		}
		helper.setBlock(new BlockPos(8, 2, 6), Blocks.AIR);
		helper.setBlock(new BlockPos(8, 3, 6), Blocks.AIR);
		helper.setBlock(HAY, Blocks.HAY_BLOCK);
		helper.setBlock(LADDER, Blocks.LADDER.defaultBlockState().setValue(LadderBlock.FACING, Direction.WEST));
		helper.setBlock(STORE, Blocks.CHEST.defaultBlockState().setValue(ChestBlock.FACING, Direction.EAST));
		ResourceLocation id = ResourceLocation.fromNamespaceAndPath("aliveworkplace_test", "qa_store_broken_" + Long.toHexString(level.getGameTime()));
		level.getStructureManager().getOrCreate(id).fillFromWorld(level, helper.absolutePos(SHED), new Vec3i(5, 3, 5), false, Blocks.STRUCTURE_VOID);
		helper.setBlock(HAY, Blocks.AIR);
		helper.setBlock(LADDER, Blocks.AIR);
		Container chest = helper.getBlockEntity(STORE);
		chest.setItem(3, new ItemStack(Items.HAY_BLOCK));
		chest.setItem(4, new ItemStack(Items.LADDER));
		helper.setBlock(BENCH, ModBlocks.BUILDERS_BENCH);
		BlueprintData.Placement placement = new BlueprintData.Placement(level.dimension().location(), helper.absolutePos(SHED), Rotation.NONE, Mirror.NONE);
		Villager builder = helper.spawn(EntityType.VILLAGER, new BlockPos(8, 2, 2));
		Builders.employ(level, builder, helper.absolutePos(BENCH));
		BuildSiteManager.get(level).recordFinished(id, placement, builder.getUUID());
		helper.runAfterDelay(2, () -> {
			BuildSite site = ModAttachments.BUILDER_JOB.has(builder) ? Builders.activeSite(level, builder) : Upkeep.look(level, builder);
			helper.assertTrue(site != null && site.isRepair(), "no repair started");
		});
		// Mid-job: the repair has started, the builder hasn't fetched yet; the chest (and what it holds) is broken away.
		helper.runAfterDelay(3, () -> {
			helper.assertTrue(chest.countItem(Items.HAY_BLOCK) == 1 && chest.countItem(Items.LADDER) == 1, "the builder took from the chest before it broke");
			level.destroyBlock(helper.absolutePos(STORE), false);
		});
		helper.runAfterDelay(400, () -> {
			helper.assertBlockNotPresent(Blocks.HAY_BLOCK, HAY);
			helper.assertBlockNotPresent(Blocks.LADDER, LADDER);
			helper.assertTrue(ModAttachments.BUILDER_JOB.has(builder), "the builder dropped the repair with its materials gone");
			helper.assertTrue(builder.isAlive(), "the builder died");
			helper.setBlock(STORE, Blocks.BARREL);
			Container barrel = helper.getBlockEntity(STORE);
			barrel.setItem(0, new ItemStack(Items.HAY_BLOCK));
			barrel.setItem(1, new ItemStack(Items.LADDER));
		});
		helper.succeedWhen(() -> {
			helper.assertTrue(helper.getTick() > 400, "the barrel isn't back yet");
			helper.assertBlockPresent(Blocks.HAY_BLOCK, HAY);
			helper.assertBlockPresent(Blocks.LADDER, LADDER);
			helper.assertFalse(ModAttachments.BUILDER_JOB.has(builder), "still repairing");
			Container barrel = helper.getBlockEntity(STORE);
			helper.assertTrue(barrel.countItem(Items.HAY_BLOCK) == 0 && barrel.countItem(Items.LADDER) == 0, "the barrel still holds them");
		});
	}

	private static void liveServer(GameTestHelper helper) {
		boolean upkeep = Upkeep.ENABLED;
		boolean asks = Upkeep.ASK;
		boolean scaffolds = Scaffolds.ENABLED;
		Upkeep.ENABLED = true;
		Upkeep.ASK = false;
		Scaffolds.ENABLED = false;
		Leftovers.after(helper, () -> {
			Upkeep.ENABLED = upkeep;
			Upkeep.ASK = asks;
			Scaffolds.ENABLED = scaffolds;
		});
	}
}
