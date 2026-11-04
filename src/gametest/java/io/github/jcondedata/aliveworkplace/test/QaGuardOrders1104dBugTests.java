package io.github.jcondedata.aliveworkplace.test;

import io.github.jcondedata.aliveworkplace.guard.Cavalry;
import io.github.jcondedata.aliveworkplace.registry.ModBlocks;
import io.github.jcondedata.aliveworkplace.registry.ModVillagers;
import io.github.jcondedata.aliveworkplace.work.Jobs;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.animal.horse.AbstractHorse;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/**
 * QA (lane D, 2026-11-04): guard behaviour that disagrees with the player guide (docs/guide/guard.md). Not registered:
 * these fail until the mod is fixed (QaGuardOrders1104dGameTests has the passing ones).
 */
public class QaGuardOrders1104dBugTests implements FabricGameTest {
	private static final String AREA = "aliveworkplace_test:huge_area";

	/**
	 * guard.md: a guard rides a tame, saddled horse "within 24 blocks of the post". Cavalry.freeHorse looks in the post
	 * block's box grown by 24, which any horse whose body touches it passes: a horse standing 25 blocks from the post
	 * (on either side) is taken too.
	 */
	//$ gametest_ticks_batch AREA '60' '"qaGo4dBugHorse25"'
	@GameTest(template = AREA, timeoutTicks = 60, batch = "qaGo4dBugHorse25")
	public void aHorseTwentyFiveBlocksFromThePostIsNotTheGuards(GameTestHelper helper) {
		Leftovers.clear(helper);
		BlockPos post = new BlockPos(2, 2, 14);
		helper.setBlock(post, ModBlocks.GUARD_POST);
		Villager guard = helper.spawn(EntityType.VILLAGER, post.north());
		guard.setNoAi(true);
		Jobs.employ(helper.getLevel(), guard, helper.absolutePos(post), ModVillagers.GUARD_POST_POI, ModVillagers.GUARD);
		AbstractHorse horse = helper.spawn(EntityType.HORSE, new BlockPos(27, 2, 14));
		horse.setTamed(true);
		horse.equipSaddle(new ItemStack(Items.SADDLE), null);
		horse.setNoAi(true);
		double distance = horse.position().distanceTo(net.minecraft.world.phys.Vec3.atBottomCenterOf(helper.absolutePos(post)));
		helper.assertTrue(Cavalry.freeHorse(helper.getLevel(), guard, helper.absolutePos(post)) == null,
			"a horse " + distance + " blocks from the post (the guide: within 24) is the guard's");
		helper.succeed();
	}
}
