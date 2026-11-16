package io.github.jcondedata.aliveworkplace.test;

import io.github.jcondedata.aliveworkplace.blueprint.BlueprintData;
import io.github.jcondedata.aliveworkplace.build.BuildSite;
import io.github.jcondedata.aliveworkplace.build.BuildSiteManager;
import io.github.jcondedata.aliveworkplace.build.Builders;
import io.github.jcondedata.aliveworkplace.registry.ModBlocks;
import io.github.jcondedata.aliveworkplace.registry.ModGameRules;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Container;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.phys.AABB;

/**
 * QA (2026-11-16, lane B): a build canceled with scaffolding up while its supply chest is full (ROADMAP 35.6,
 * docs/wiki/scaffolding.md: "has them all taken down at once, into the supply chests by its bench (else dropped
 * there)"). None may stay standing and none may be lost: whatever doesn't fit lies on the ground by the bench. Also
 * with a server restart between the scaffolds going up and the cancel (the site's scaffold list is saved).
 */
public class QaScaffoldsFullChest1116bGameTests implements net.fabricmc.fabric.api.gametest.v1.FabricGameTest {
	private static final String BIG = "aliveworkplace_test:big_area";
	private static final ResourceLocation PILLAR = ResourceLocation.fromNamespaceAndPath("aliveworkplace_test", "test_pillar");
	private static final BlockPos BENCH = new BlockPos(3, 2, 3);
	private static final BlockPos CHEST = new BlockPos(3, 2, 5);
	private static final BlockPos VILLAGER = new BlockPos(4, 2, 4);
	private static final BlockPos PILLAR_AT = new BlockPos(11, 2, 11);
	/** Two columns of scaffolding, 4 and 3 high, clear of the pillar's footprint (the builder clears that first). */
	private static final BlockPos[] FEET = {new BlockPos(1, 2, 13), new BlockPos(1, 2, 17)};
	private static final int[] HEIGHTS = {4, 3};
	private static final int TOTAL = 7;

	private static void log(GameTestHelper helper, String line) {
		helper.getLevel().getServer().sendSystemMessage(Component.literal("[qa-scaffolds-full-chest-1116b] " + line));
	}

	/** Canceled with 7 scaffolds up and the only supply chest full of dirt: none standing, all 7 on the ground by the bench. */
	//$ gametest_ticks_batch BIG '400' '"qaScaffoldsFullChest"'
	@GameTest(template = BIG, timeoutTicks = 400, batch = "qaScaffoldsFullChest")
	public void canceledWithTheChestFullDropsEveryScaffoldByTheBench(GameTestHelper helper) {
		run(helper, false);
	}

	/** The same after a server restart between the scaffolds going up and the cancel. */
	//$ gametest_ticks_batch BIG '400' '"qaScaffoldsFullChestRestart"'
	@GameTest(template = BIG, timeoutTicks = 400, batch = "qaScaffoldsFullChestRestart")
	public void canceledAfterARestartWithTheChestFullDropsEveryScaffold(GameTestHelper helper) {
		run(helper, true);
	}

	private static void run(GameTestHelper helper, boolean restart) {
		Leftovers.clear(helper);
		ServerLevel level = helper.getLevel();
		helper.setDayTime(2000);
		level.getGameRules().getRule(ModGameRules.BUILDERS_HELP).set(false, level.getServer());
		level.getGameRules().getRule(ModGameRules.FREE_MATERIALS).set(false, level.getServer());
		helper.setBlock(BENCH, ModBlocks.BUILDERS_BENCH);
		helper.setBlock(CHEST, Blocks.CHEST);
		Container chest = helper.getBlockEntity(CHEST);
		for (int i = 0; i < chest.getContainerSize(); i++) {
			chest.setItem(i, new ItemStack(Items.DIRT, 64));
		}
		Villager villager = helper.spawn(EntityType.VILLAGER, VILLAGER);
		Builders.employ(level, villager, helper.absolutePos(BENCH));
		BuildSite site = Builders.start(level, villager, null, PILLAR,
			new BlueprintData.Placement(level.dimension().location(), helper.absolutePos(PILLAR_AT), Rotation.NONE, Mirror.NONE));
		if (site == null) {
			throw new GameTestAssertException("the build of " + PILLAR + " didn't start");
		}
		UUID id = site.id();
		List<BlockPos> scaffolds = new ArrayList<>();
		for (int c = 0; c < FEET.length; c++) {
			for (int i = 0; i < HEIGHTS[c]; i++) {
				BlockPos p = helper.absolutePos(FEET[c].above(i));
				level.setBlockAndUpdate(p, Blocks.SCAFFOLDING.defaultBlockState());
				site.addScaffold(p);
				scaffolds.add(p);
			}
		}
		helper.assertTrue(scaffolds.size() == TOTAL, "placed " + scaffolds.size());
		boolean[] done = {false};
		helper.runAfterDelay(5, () -> {
			BuildSite live = site;
			if (restart) {
				var registries = level.registryAccess();
				level.getDataStorage().set(BuildSiteManager.NAME,
					BuildSiteManager.load(BuildSiteManager.get(level).save(new CompoundTag(), registries), registries));
				live = BuildSiteManager.get(level).get(id);
				helper.assertTrue(live != null && live != site, "the site didn't come back from the restart");
				helper.assertTrue(live.scaffolds().size() == TOTAL, "the restarted site lists " + live.scaffolds().size() + " scaffolds, not " + TOTAL);
			}
			for (BlockPos p : scaffolds) {
				helper.assertTrue(level.getBlockState(p).is(Blocks.SCAFFOLDING), "a scaffold fell before the cancel: " + helper.relativePos(p));
			}
			Builders.cancel(level, live);
			helper.assertTrue(BuildSiteManager.get(level).get(id) == null, "the build wasn't canceled");
			done[0] = true;
		});
		helper.succeedWhen(() -> {
			helper.assertTrue(done[0], "not canceled yet");
			for (BlockPos p : scaffolds) {
				if (level.getBlockState(p).is(Blocks.SCAFFOLDING)) {
					throw new GameTestAssertException("scaffolding still standing at " + helper.relativePos(p) + " after the cancel");
				}
			}
			int inChest = 0;
			for (int i = 0; i < chest.getContainerSize(); i++) {
				ItemStack s = chest.getItem(i);
				inChest += s.is(Items.SCAFFOLDING) ? s.getCount() : 0;
			}
			int loose = 0;
			AABB area = new AABB(helper.absolutePos(BlockPos.ZERO)).expandTowards(22, 30, 22);
			for (ItemEntity item : level.getEntitiesOfClass(ItemEntity.class, area)) {
				if (item.getItem().is(Items.SCAFFOLDING)) {
					loose += item.getItem().getCount();
					helper.assertTrue(item.blockPosition().closerThan(helper.absolutePos(BENCH), 4),
						"scaffolding dropped at " + helper.relativePos(item.blockPosition()) + ", not by the bench");
				}
			}
			helper.assertTrue(inChest == 0, "the full chest took " + inChest + " scaffolding");
			helper.assertTrue(loose == TOTAL, loose + " scaffolding on the ground by the bench, not " + TOTAL);
			log(helper, (restart ? "after a restart, " : "") + "canceled with " + TOTAL + " up and the chest full: none standing, " + loose + " by the bench");
		});
	}
}
