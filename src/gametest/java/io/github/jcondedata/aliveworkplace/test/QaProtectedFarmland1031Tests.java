package io.github.jcondedata.aliveworkplace.test;

import io.github.jcondedata.aliveworkplace.hall.VillageProtection;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.animal.horse.Horse;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;

/**
 * QA (2026-10-31, lane-c-1031-2153), village protection and the village's fields. Guide village-protection.md: inside a
 * protected village only the owner, friends and operators may "break or place blocks". A stranger who jumps on the
 * fields turns the farmland to dirt and the crops on it pop off (FarmBlock.fallOn, the game's own entry point for a
 * landing; a jump from 1.5 blocks always tramples), so one visitor can wipe out every field in the village.
 */
public class QaProtectedFarmland1031Tests implements net.fabricmc.fabric.api.gametest.v1.FabricGameTest {
	private static final String AREA = "aliveworkplace_test:huge_area";

	/** Farmland with wheat 5 blocks from the hall: a stranger's landing leaves it farmland (control: protection off, dirt). */
	//$ gametest_ticks_batch AREA '40' '"qaProtectedFarmland1031"'
	@GameTest(template = AREA, timeoutTicks = 40, batch = "qaProtectedFarmland1031")
	public void qaAStrangerJumpingOnTheVillagesFieldDoesntTrampleIt(GameTestHelper helper) {
		ServerPlayer owner = ProtectionSpecGameTests.player(helper);
		ProtectionSpecGameTests.protectedHall(helper, owner); // hall at (11, 2, 11), reach 16
		BlockPos control = new BlockPos(16, 1, 11);
		BlockPos field = new BlockPos(16, 1, 14);
		for (BlockPos f : new BlockPos[] {control, field}) {
			helper.setBlock(f, Blocks.FARMLAND);
			helper.setBlock(f.above(), Blocks.WHEAT);
		}
		ServerPlayer stranger = ProtectionSpecGameTests.player(helper);

		VillageProtection.ENABLED = false;
		land(helper, stranger, control);
		VillageProtection.ENABLED = true;
		helper.assertTrue(helper.getBlockState(control).is(Blocks.DIRT), "setup: the landing didn't trample the control field: " + helper.getBlockState(control));

		land(helper, stranger, field);
		helper.assertTrue(helper.getBlockState(field).is(Blocks.FARMLAND) && helper.getBlockState(field.above()).is(Blocks.WHEAT),
			"a stranger jumping on a field inside the protected village trampled it: " + helper.getBlockState(field) + " under " + helper.getBlockState(field.above()));
		helper.succeed();
	}

	/** B742 neighbours: the owner and a friend trample inside, as in vanilla; a stranger outside the village too. */
	//$ gametest_ticks_batch AREA '40' '"qaProtectedFarmland1031Owner"'
	@GameTest(template = AREA, timeoutTicks = 40, batch = "qaProtectedFarmland1031Owner")
	public void qaTheOwnerAFriendAndAStrangerOutsideStillTrample(GameTestHelper helper) {
		ServerPlayer owner = ProtectionSpecGameTests.player(helper);
		ProtectionSpecGameTests.protectedHall(helper, owner);
		ServerPlayer friend = ProtectionSpecGameTests.player(helper);
		var friends = io.github.jcondedata.aliveworkplace.build.Friends.get(helper.getLevel().getServer());
		friends.add(owner.getUUID(), friend.getUUID(), friend.getGameProfile().getName());
		Leftovers.after(helper, () -> friends.remove(owner.getUUID(), friend.getUUID()));
		ServerPlayer stranger = ProtectionSpecGameTests.player(helper);
		BlockPos byOwner = new BlockPos(16, 1, 14);
		BlockPos byFriend = new BlockPos(14, 1, 16);
		BlockPos outside = new BlockPos(28, 1, 28); // 24 blocks from the hall, reach 16
		for (BlockPos f : new BlockPos[] {byOwner, byFriend, outside}) {
			helper.setBlock(f, Blocks.FARMLAND);
			helper.setBlock(f.above(), Blocks.WHEAT);
		}
		land(helper, owner, byOwner);
		land(helper, friend, byFriend);
		land(helper, stranger, outside);
		helper.assertTrue(helper.getBlockState(byOwner).is(Blocks.DIRT), "the owner's landing should trample their own field: " + helper.getBlockState(byOwner));
		helper.assertTrue(helper.getBlockState(byFriend).is(Blocks.DIRT), "a friend's landing should trample the field: " + helper.getBlockState(byFriend));
		helper.assertTrue(helper.getBlockState(outside).is(Blocks.DIRT), "a stranger's landing outside the village should trample: " + helper.getBlockState(outside));
		helper.succeed();
	}

	/** B742: a horse a stranger rides and steers doesn't trample the village's fields either (control: protection off). */
	//$ gametest_ticks_batch AREA '40' '"qaProtectedFarmland1031Horse"'
	@GameTest(template = AREA, timeoutTicks = 40, batch = "qaProtectedFarmland1031Horse")
	public void qaAStrangersHorseDoesntTrampleTheVillagesField(GameTestHelper helper) {
		ServerPlayer owner = ProtectionSpecGameTests.player(helper);
		ProtectionSpecGameTests.protectedHall(helper, owner);
		ServerPlayer stranger = ProtectionSpecGameTests.player(helper);
		BlockPos control = new BlockPos(16, 1, 11);
		BlockPos field = new BlockPos(16, 1, 14);
		for (BlockPos f : new BlockPos[] {control, field}) {
			helper.setBlock(f, Blocks.FARMLAND);
			helper.setBlock(f.above(), Blocks.WHEAT);
		}
		Horse horse = helper.spawn(EntityType.HORSE, control.above());
		horse.setTamed(true);
		horse.equipSaddle(new ItemStack(Items.SADDLE), null);
		stranger.startRiding(horse, true);
		helper.assertTrue(horse.getControllingPassenger() == stranger, "setup: the stranger should steer the horse");

		VillageProtection.ENABLED = false;
		land(helper, horse, control);
		VillageProtection.ENABLED = true;
		helper.assertTrue(helper.getBlockState(control).is(Blocks.DIRT), "setup: the horse's landing didn't trample the control field: " + helper.getBlockState(control));

		land(helper, horse, field);
		helper.assertTrue(helper.getBlockState(field).is(Blocks.FARMLAND) && helper.getBlockState(field.above()).is(Blocks.WHEAT),
			"a stranger's horse trampled a field inside the protected village: " + helper.getBlockState(field));
		stranger.stopRiding();
		horse.discard();
		helper.succeed();
	}

	private static void land(GameTestHelper helper, Entity player, BlockPos farmland) {
		BlockPos at = helper.absolutePos(farmland);
		Vec3 feet = Vec3.atBottomCenterOf(at.above());
		player.moveTo(feet.x, feet.y, feet.z, 0f, 0f);
		helper.getLevel().getBlockState(at).getBlock().fallOn(helper.getLevel(), helper.getLevel().getBlockState(at), at, player, 1.5f);
	}
}
