package io.github.jcondedata.aliveworkplace.test;

import io.github.jcondedata.aliveworkplace.mine.Miners;
import io.github.jcondedata.aliveworkplace.mine.QuarryData;
import io.github.jcondedata.aliveworkplace.mine.QuarrySite;
import io.github.jcondedata.aliveworkplace.registry.ModBlocks;
import io.github.jcondedata.aliveworkplace.registry.ModComponents;
import io.github.jcondedata.aliveworkplace.registry.ModItems;
import io.github.jcondedata.aliveworkplace.registry.ModVillagers;
import io.github.jcondedata.aliveworkplace.work.Jobs;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameType;

/**
 * QA (2026-10-31), B718's quarry side: village protection against a stranger's miner. A Quarry Marker's two corners are
 * clicked outside a protected village (each click is checked, and both are outside), but the rectangle between them runs
 * through the village's edge; the stranger hands it to his own miner, whose bench is outside too. Guide
 * village-protection.md: inside a protected village "only the owner, their friends and operators may break or place
 * blocks". Expected: the miner doesn't take a quarry reaching into someone else's protected village.
 */
public class QaProtectedQuarryBug1031Tests implements net.fabricmc.fabric.api.gametest.v1.FabricGameTest {
	private static final String AREA = "aliveworkplace_test:huge_area";

	/** Corners (25, 3) and (29, 19), both just outside a village of reach 16 round (11, 11); its column x = 25, z = 11 is 14 from the hall. */
	//$ gametest_ticks_batch AREA '60' '"qaProtectedQuarry1031"'
	@GameTest(template = AREA, timeoutTicks = 60, batch = "qaProtectedQuarry1031")
	public void qaAStrangersMinerDoesntTakeAQuarryReachingIntoAProtectedVillage(GameTestHelper helper) {
		var owner = ProtectionSpecGameTests.player(helper);
		ProtectionSpecGameTests.protectedHall(helper, owner); // hall at (11, 2, 11), reach 16
		ServerLevel level = helper.getLevel();
		BlockPos benchAt = new BlockPos(28, 2, 26);
		helper.setBlock(benchAt, ModBlocks.MINERS_BENCH);
		Villager miner = helper.spawn(EntityType.VILLAGER, benchAt.west());
		miner.setNoAi(true);
		Jobs.employ(level, miner, helper.absolutePos(benchAt), ModVillagers.MINERS_BENCH_POI, ModVillagers.MINER);

		ItemStack marker = new ItemStack(ModItems.QUARRY_MARKER);
		marker.set(ModComponents.QUARRY, new QuarryData(Optional.of(level.dimension().location()), Optional.of(helper.absolutePos(new BlockPos(25, 1, 3))),
			Optional.of(helper.absolutePos(new BlockPos(29, 1, 19))), 4));
		QaStageFiveGameTests.Client c = QaStageSixGameTests.client(helper, GameType.SURVIVAL, QaStageSixGameTests.name("qaPQ"));
		c.player().setItemInHand(InteractionHand.MAIN_HAND, marker);
		QaStageSixGameTests.clickVillager(c.player(), miner, c.seen());
		QuarrySite site = Miners.activeSite(level, miner);
		if (site != null) {
			Miners.cancel(level, site);
		}
		helper.assertTrue(site == null, "a stranger's miner took a 5 x 17 quarry whose middle lies inside a protected village (14 blocks from the hall); he said "
			+ c.seen().stream().map(Component::getString).toList());
		helper.succeed();
	}
}
