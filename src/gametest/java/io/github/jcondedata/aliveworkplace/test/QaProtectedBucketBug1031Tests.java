package io.github.jcondedata.aliveworkplace.test;

import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;

/**
 * QA (2026-10-31), village protection and buckets. Guide village-protection.md: inside a protected village only the
 * owner, friends and operators may "break or place blocks, or empty buckets". VillageProtection's allowUseItem asks
 * mayChange at the PLAYER's feet, not where the bucket empties: a stranger standing one step outside the village pours
 * lava (here water) onto the ground inside it. The use goes through ServerPlayerGameMode.useItem, as the client's
 * use-item packet does after its own block click passes.
 */
public class QaProtectedBucketBug1031Tests implements net.fabricmc.fabric.api.gametest.v1.FabricGameTest {
	private static final String AREA = "aliveworkplace_test:huge_area";

	/** A stranger outside the edge empties a bucket onto a block 15 blocks from the hall, inside: nothing may be poured there. */
	//$ gametest_ticks_batch AREA '40' '"qaProtectedBucket1031"'
	@GameTest(template = AREA, timeoutTicks = 40, batch = "qaProtectedBucket1031")
	public void qaAStrangerOutsideCantPourABucketIntoAProtectedVillage(GameTestHelper helper) {
		ServerPlayer owner = ProtectionSpecGameTests.player(helper);
		ProtectionSpecGameTests.protectedHall(helper, owner); // hall at (11, 2, 11), reach 16
		BlockPos ground = new BlockPos(26, 1, 11); // the floor 15 blocks east of the hall: inside
		BlockPos poured = ground.above();
		helper.assertTrue(helper.getBlockState(ground).isSolid() && helper.getBlockState(poured).isAir(), "setup: floor and air at the target");

		ServerPlayer stranger = ProtectionSpecGameTests.player(helper);
		Vec3 feet = Vec3.atBottomCenterOf(helper.absolutePos(new BlockPos(28, 2, 11))); // 17 blocks out: outside
		Vec3 target = Vec3.atCenterOf(helper.absolutePos(ground)).add(0, 0.5, 0);
		stranger.moveTo(feet.x, feet.y, feet.z, 90f, 0f);
		stranger.lookAt(net.minecraft.commands.arguments.EntityAnchorArgument.Anchor.EYES, target);
		// control: with protection switched off the same pour lands there (the aim is right)
		io.github.jcondedata.aliveworkplace.hall.VillageProtection.ENABLED = false;
		ItemStack trial = new ItemStack(Items.WATER_BUCKET);
		stranger.setItemInHand(InteractionHand.MAIN_HAND, trial);
		stranger.gameMode.useItem(stranger, helper.getLevel(), trial, InteractionHand.MAIN_HAND);
		io.github.jcondedata.aliveworkplace.hall.VillageProtection.ENABLED = true;
		helper.assertTrue(helper.getBlockState(poured).is(Blocks.WATER), "setup: the aimed pour didn't land at the target: " + helper.getBlockState(poured));
		helper.setBlock(poured, Blocks.AIR);

		ItemStack bucket = new ItemStack(Items.WATER_BUCKET);
		stranger.setItemInHand(InteractionHand.MAIN_HAND, bucket);
		stranger.gameMode.useItem(stranger, helper.getLevel(), bucket, InteractionHand.MAIN_HAND);
		helper.assertTrue(!helper.getBlockState(poured).is(Blocks.WATER),
			"a stranger standing outside poured water onto the ground inside the protected village (15 blocks from the hall); his hand now holds "
				+ stranger.getMainHandItem());
		helper.succeed();
	}
}
