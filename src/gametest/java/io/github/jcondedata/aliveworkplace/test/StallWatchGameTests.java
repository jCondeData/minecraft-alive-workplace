package io.github.jcondedata.aliveworkplace.test;

import io.github.jcondedata.aliveworkplace.blueprint.BlueprintData;
import io.github.jcondedata.aliveworkplace.build.BuildSite;
import io.github.jcondedata.aliveworkplace.build.BuildSiteManager;
import io.github.jcondedata.aliveworkplace.build.Builders;
import io.github.jcondedata.aliveworkplace.build.StallWatch;
import io.github.jcondedata.aliveworkplace.registry.ModBlocks;
import io.github.jcondedata.aliveworkplace.registry.ModGameRules;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
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

/** The "builder stalled" log line (23.1): written after 30 s without progress, never for a builder at work or a queued site. */
public class StallWatchGameTests implements FabricGameTest {
	private static final String AREA = "aliveworkplace_test:build_area";
	private static final ResourceLocation TEST_HUT = ResourceLocation.fromNamespaceAndPath("aliveworkplace_test", "test_hut");
	private static final BlockPos BENCH = new BlockPos(2, 2, 2);
	private static final BlockPos CHEST = new BlockPos(2, 2, 4);
	private static final BlockPos VILLAGER = new BlockPos(3, 2, 3);
	private static final BlockPos HUT_ORIGIN = new BlockPos(6, 2, 6);

	private static Villager builder(GameTestHelper helper, ItemStack... chestItems) {
		ServerLevel level = helper.getLevel();
		helper.setDayTime(2000);
		level.getGameRules().getRule(ModGameRules.BUILD_DELAY).set(2, level.getServer());
		level.getGameRules().getRule(ModGameRules.BUILDERS_HELP).set(false, level.getServer());
		level.getGameRules().getRule(ModGameRules.FREE_MATERIALS).set(false, level.getServer());
		helper.setBlock(BENCH, ModBlocks.BUILDERS_BENCH);
		helper.setBlock(CHEST, Blocks.CHEST);
		Container chest = helper.getBlockEntity(CHEST);
		for (int i = 0; i < chestItems.length; i++) {
			chest.setItem(i, chestItems[i]);
		}
		Villager villager = helper.spawn(EntityType.VILLAGER, VILLAGER);
		Builders.employ(level, villager, helper.absolutePos(BENCH));
		return villager;
	}

	private static BlueprintData.Placement at(GameTestHelper helper, BlockPos rel) {
		return new BlueprintData.Placement(helper.getLevel().dimension().location(), helper.absolutePos(rel), Rotation.NONE, Mirror.NONE);
	}

	/** No materials anywhere: the builder waits, and after 30 s of that the stall is logged once. */
	//$ gametest_ticks AREA '1600'
	@GameTest(template = AREA, timeoutTicks = 1600)
	public void aBuilderWaitingForMaterialsIsLoggedAsStalled(GameTestHelper helper) {
		Villager villager = builder(helper);
		BuildSite site = Builders.start(helper.getLevel(), villager, null, TEST_HUT, at(helper, HUT_ORIGIN));
		long started = helper.getTick();
		helper.succeedWhen(() -> {
			helper.assertTrue(StallWatch.isStalled(site.id()), "no stall logged yet: status " + site.status() + ", stage " + site.stage());
			helper.assertTrue(helper.getTick() - started >= StallWatch.STALL_TICKS,
				"stall logged after only " + (helper.getTick() - started) + " ticks");
		});
	}

	/** With everything in the chest, the hut goes up and the site is never once logged as stalled. */
	//$ gametest_ticks AREA '2400'
	@GameTest(template = AREA, timeoutTicks = 2400)
	public void aBuilderAtWorkIsNeverLoggedAsStalled(GameTestHelper helper) {
		Villager villager = builder(helper, new ItemStack(Items.COBBLESTONE, 25), new ItemStack(Items.OAK_PLANKS, 55),
			new ItemStack(Items.OAK_DOOR), new ItemStack(Items.TORCH));
		BuildSite site = Builders.start(helper.getLevel(), villager, null, TEST_HUT, at(helper, HUT_ORIGIN));
		AtomicBoolean stalled = new AtomicBoolean();
		helper.onEachTick(() -> stalled.compareAndSet(false, StallWatch.isStalled(site.id())));
		helper.succeedWhen(() -> {
			helper.assertFalse(stalled.get(), "a builder at work was logged as stalled");
			helper.assertTrue(BuildSiteManager.get(helper.getLevel()).get(site.id()) == null,
				"still building: stage " + site.stage() + ", status " + site.status());
		});
	}

	/** A site waiting in a busy builder's queue is not a stall, even while the builder's own site is stuck. */
	//$ gametest_ticks AREA '1600'
	@GameTest(template = AREA, timeoutTicks = 1600)
	public void aQueuedSiteIsNeverLoggedAsStalled(GameTestHelper helper) {
		Villager villager = builder(helper);
		BuildSite first = Builders.start(helper.getLevel(), villager, null, TEST_HUT, at(helper, HUT_ORIGIN));
		BuildSite queued = Builders.enqueue(helper.getLevel(), villager, null, TEST_HUT, at(helper, new BlockPos(6, 2, 1)));
		AtomicLong firstStalledAt = new AtomicLong(-1);
		helper.onEachTick(() -> {
			helper.assertFalse(StallWatch.isStalled(queued.id()), "the queued site was logged as stalled");
			if (firstStalledAt.get() < 0 && StallWatch.isStalled(first.id())) {
				firstStalledAt.set(helper.getTick());
			}
		});
		// Keep watching for another 100 ticks after the builder's own site stalls.
		helper.succeedWhen(() -> helper.assertTrue(firstStalledAt.get() >= 0 && helper.getTick() - firstStalledAt.get() >= 100,
			"the builder's own site hasn't stalled yet (status " + first.status() + ")"));
	}
}
