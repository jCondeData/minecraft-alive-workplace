package io.github.jcondedata.aliveworkplace.test;

import io.github.jcondedata.aliveworkplace.hall.VillageProtection;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.decoration.ItemFrame;
import net.minecraft.world.entity.decoration.Painting;
import net.minecraft.world.entity.decoration.PaintingVariants;
import net.minecraft.world.entity.projectile.Arrow;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;

/**
 * QA (2026-10-31, lane-c-1031-2153), village protection and item frames. Guide village-protection.md: inside a protected
 * village only the owner, friends and operators may "hurt the villagers, golems, animals, armor stands and item frames, by
 * hand or with arrows, tridents and potions". A stranger's arrow is checked through the living-damage event, which an item
 * frame (not a living thing) never goes through. The hit is ItemFrame.hurt with the arrow's damage source, as
 * Arrow.onHitEntity calls it.
 */
public class QaProtectedFrames1031Tests implements net.fabricmc.fabric.api.gametest.v1.FabricGameTest {
	private static final String AREA = "aliveworkplace_test:huge_area";

	private static ItemFrame frame(GameTestHelper helper, BlockPos wall) {
		helper.setBlock(wall, Blocks.OAK_PLANKS);
		BlockPos at = helper.absolutePos(wall.east());
		ItemFrame frame = new ItemFrame(helper.getLevel(), at, Direction.EAST);
		frame.setItem(new ItemStack(Items.DIAMOND), false);
		helper.getLevel().addFreshEntity(frame);
		return frame;
	}

	private static void shoot(GameTestHelper helper, ServerPlayer shooter, net.minecraft.world.entity.decoration.HangingEntity frame) {
		Arrow arrow = new Arrow(EntityType.ARROW, helper.getLevel());
		arrow.setOwner(shooter);
		arrow.setPos(frame.position().add(1, 0, 0));
		frame.hurt(helper.getLevel().damageSources().arrow(arrow, shooter), 6f);
	}

	/** A framed diamond 5 blocks from the hall: a stranger's arrow leaves it framed (control: protection off, knocked out). */
	//$ gametest_ticks_batch AREA '40' '"qaProtectedFrames1031"'
	@GameTest(template = AREA, timeoutTicks = 40, batch = "qaProtectedFrames1031")
	public void qaAStrangersArrowDoesntBreakAnItemFrameInside(GameTestHelper helper) {
		ServerPlayer owner = ProtectionSpecGameTests.player(helper);
		ProtectionSpecGameTests.protectedHall(helper, owner); // hall at (11, 2, 11), reach 16
		ServerPlayer stranger = ProtectionSpecGameTests.player(helper);
		ItemFrame control = frame(helper, new BlockPos(15, 3, 8));
		ItemFrame kept = frame(helper, new BlockPos(15, 3, 14));
		helper.assertTrue(VillageProtection.keeper(helper.getLevel(), stranger, kept.blockPosition()).isPresent(), "setup: the frame should be in the protected village");

		VillageProtection.ENABLED = false;
		shoot(helper, stranger, control);
		VillageProtection.ENABLED = true;
		helper.assertTrue(control.getItem().isEmpty(), "setup: the arrow didn't knock the control frame's diamond out: " + control.getItem());

		shoot(helper, stranger, kept);
		helper.assertTrue(kept.isAlive() && kept.getItem().is(Items.DIAMOND),
			"a stranger's arrow knocked the diamond out of an item frame inside the protected village: frame alive " + kept.isAlive() + ", holds " + kept.getItem());
		helper.succeed();
	}

	/** B743: a stranger's arrow doesn't break a painting inside either (control: protection off, the painting breaks). */
	//$ gametest_ticks_batch AREA '40' '"qaProtectedFrames1031Painting"'
	@GameTest(template = AREA, timeoutTicks = 40, batch = "qaProtectedFrames1031Painting")
	public void qaAStrangersArrowDoesntBreakAPaintingInside(GameTestHelper helper) {
		ServerPlayer owner = ProtectionSpecGameTests.player(helper);
		ProtectionSpecGameTests.protectedHall(helper, owner);
		ServerPlayer stranger = ProtectionSpecGameTests.player(helper);
		Painting control = painting(helper, new BlockPos(15, 3, 8));
		Painting kept = painting(helper, new BlockPos(15, 3, 14));

		VillageProtection.ENABLED = false;
		shoot(helper, stranger, control);
		VillageProtection.ENABLED = true;
		helper.assertTrue(!control.isAlive(), "setup: the arrow didn't break the control painting");

		shoot(helper, stranger, kept);
		helper.assertTrue(kept.isAlive(), "a stranger's arrow broke a painting inside the protected village");
		kept.discard();
		helper.succeed();
	}

	/** B743 neighbours: the owner's and a friend's arrows knock a framed item out inside, a stranger's outside the village too. */
	//$ gametest_ticks_batch AREA '40' '"qaProtectedFrames1031Owner"'
	@GameTest(template = AREA, timeoutTicks = 40, batch = "qaProtectedFrames1031Owner")
	public void qaTheOwnerAFriendAndAStrangerOutsideStillShootFrames(GameTestHelper helper) {
		ServerPlayer owner = ProtectionSpecGameTests.player(helper);
		ProtectionSpecGameTests.protectedHall(helper, owner);
		ServerPlayer friend = ProtectionSpecGameTests.player(helper);
		var friends = io.github.jcondedata.aliveworkplace.build.Friends.get(helper.getLevel().getServer());
		friends.add(owner.getUUID(), friend.getUUID(), friend.getGameProfile().getName());
		Leftovers.after(helper, () -> friends.remove(owner.getUUID(), friend.getUUID()));
		ServerPlayer stranger = ProtectionSpecGameTests.player(helper);
		ItemFrame byOwner = frame(helper, new BlockPos(15, 3, 8));
		ItemFrame byFriend = frame(helper, new BlockPos(15, 3, 14));
		ItemFrame outside = frame(helper, new BlockPos(27, 3, 27)); // 22 blocks from the hall, reach 16
		shoot(helper, owner, byOwner);
		shoot(helper, friend, byFriend);
		shoot(helper, stranger, outside);
		helper.assertTrue(byOwner.getItem().isEmpty(), "the owner's arrow should knock the item out of their frame: " + byOwner.getItem());
		helper.assertTrue(byFriend.getItem().isEmpty(), "a friend's arrow should knock the item out: " + byFriend.getItem());
		helper.assertTrue(outside.getItem().isEmpty(), "a stranger's arrow outside the village should knock the item out: " + outside.getItem());
		for (ItemFrame f : new ItemFrame[] {byOwner, byFriend, outside}) {
			f.discard();
		}
		helper.succeed();
	}

	private static Painting painting(GameTestHelper helper, BlockPos wall) {
		helper.setBlock(wall, Blocks.OAK_PLANKS);
		Painting painting = new Painting(helper.getLevel(), helper.absolutePos(wall.east()), Direction.EAST,
			helper.getLevel().registryAccess().registryOrThrow(Registries.PAINTING_VARIANT).getHolderOrThrow(PaintingVariants.KEBAB));
		helper.getLevel().addFreshEntity(painting);
		return painting;
	}
}
