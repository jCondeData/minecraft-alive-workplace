package io.github.jcondedata.aliveworkplace.test;

import io.github.jcondedata.aliveworkplace.hall.VillageProtection;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.decoration.ItemFrame;
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

	private static void shoot(GameTestHelper helper, ServerPlayer shooter, ItemFrame frame) {
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
}
