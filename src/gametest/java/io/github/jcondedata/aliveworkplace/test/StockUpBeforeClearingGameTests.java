package io.github.jcondedata.aliveworkplace.test;

import io.github.jcondedata.aliveworkplace.blueprint.BlueprintData;
import io.github.jcondedata.aliveworkplace.build.BuildPlan;
import io.github.jcondedata.aliveworkplace.build.BuildSite;
import io.github.jcondedata.aliveworkplace.build.BuilderBag;
import io.github.jcondedata.aliveworkplace.build.Builders;
import io.github.jcondedata.aliveworkplace.registry.ModAttachments;
import io.github.jcondedata.aliveworkplace.registry.ModBlocks;
import io.github.jcondedata.aliveworkplace.registry.ModGameRules;
import net.minecraft.core.BlockPos;
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
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;

/**
 * B39: a builder whose new site is far from its chests takes what the build starts with before clearing the site, so
 * it starts building as soon as the site is clear instead of walking all the way back to its chests first (the soak's
 * graveyard, 41 blocks from its chests, stood still for over 30 seconds every run). With nothing in stock it doesn't
 * wait: it clears the site as before.
 */
public class StockUpBeforeClearingGameTests {
	private static final String AREA = "aliveworkplace_test:huge_area";
	private static final ResourceLocation TEST_HUT = ResourceLocation.fromNamespaceAndPath("aliveworkplace_test", "test_hut");
	private static final BlockPos BENCH = new BlockPos(2, 2, 2);
	private static final BlockPos CHEST = new BlockPos(2, 2, 4);
	/** About 25 blocks from the bench. */
	private static final BlockPos HUT = new BlockPos(20, 2, 20);

	/** At the moment the site is clear, the builder already carries the build's first materials. */
	//$ gametest_ticks_batch AREA '1200' '"aFarSiteIsStockedBeforeClearing"'
	@GameTest(template = AREA, timeoutTicks = 1200, batch = "aFarSiteIsStockedBeforeClearing")
	public void aFarSiteIsStockedBeforeClearing(GameTestHelper helper) {
		Leftovers.clear(helper);
		Run r = start(helper, true);
		boolean[] seen = {false};
		helper.onEachTick(() -> {
			if (!seen[0] && r.site().stage() != BuildPlan.Stage.CLEAR) {
				seen[0] = true;
				BuilderBag bag = ModAttachments.BUILDER_BAG.getOrCreate(r.builder());
				helper.assertTrue(bag.count(Items.COBBLESTONE) > 0 || bag.count(Items.OAK_PLANKS) > 0,
					"the site was cleared at tick " + helper.getTick() + " with nothing to build with in the bag: " + bag.stacks());
			}
		});
		helper.succeedWhen(() -> helper.assertTrue(seen[0], "still clearing"));
	}

	/** Nothing in the chests: it clears the site anyway (it never stands waiting before there's anything to build). */
	//$ gametest_ticks_batch AREA '1200' '"aFarSiteIsClearedWithEmptyChests"'
	@GameTest(template = AREA, timeoutTicks = 1200, batch = "aFarSiteIsClearedWithEmptyChests")
	public void aFarSiteIsClearedWithEmptyChests(GameTestHelper helper) {
		Leftovers.clear(helper);
		Run r = start(helper, false);
		helper.succeedWhen(() -> helper.assertTrue(r.site().stage() != BuildPlan.Stage.CLEAR,
			"still clearing with empty chests: status " + r.site().status() + ", at " + helper.relativePos(r.builder().blockPosition())));
	}

	private record Run(Villager builder, BuildSite site) {
	}

	private static Run start(GameTestHelper helper, boolean stocked) {
		ServerLevel level = helper.getLevel();
		helper.setDayTime(2000);
		level.getGameRules().getRule(ModGameRules.BUILD_DELAY).set(2, level.getServer());
		helper.setBlock(BENCH, ModBlocks.BUILDERS_BENCH);
		helper.setBlock(CHEST, Blocks.CHEST);
		if (stocked) {
			Container chest = (Container) helper.getBlockEntity(CHEST);
			chest.setItem(0, new ItemStack(Items.COBBLESTONE, 25));
			chest.setItem(1, new ItemStack(Items.OAK_PLANKS, 55));
			chest.setItem(2, new ItemStack(Items.OAK_DOOR));
			chest.setItem(3, new ItemStack(Items.TORCH));
		}
		// Something to clear: a few dirt blocks where the hut's walls go.
		for (int i = 0; i < 4; i++) {
			helper.setBlock(HUT.offset(i, 1, 0), Blocks.DIRT);
		}
		Villager builder = helper.spawn(EntityType.VILLAGER, HUT.offset(-2, 0, -2));
		Builders.employ(level, builder, helper.absolutePos(BENCH));
		BuildSite site = Builders.start(level, builder, null, TEST_HUT,
			new BlueprintData.Placement(level.dimension().location(), helper.absolutePos(HUT), Rotation.NONE, Mirror.NONE));
		return new Run(builder, site);
	}
}
