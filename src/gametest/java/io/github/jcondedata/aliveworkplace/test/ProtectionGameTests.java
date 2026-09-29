package io.github.jcondedata.aliveworkplace.test;

import io.github.jcondedata.aliveworkplace.build.Friends;
import io.github.jcondedata.aliveworkplace.hall.VillageHallBlockEntity;
import io.github.jcondedata.aliveworkplace.hall.VillageHalls;
import io.github.jcondedata.aliveworkplace.hall.VillageProtection;
import io.github.jcondedata.aliveworkplace.registry.ModBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

/** A Village Hall's owner can protect the village: others can't break, place or open things in it (Milestone 20). */
public class ProtectionGameTests implements net.fabricmc.fabric.api.gametest.v1.FabricGameTest {
	private static final String AREA = "aliveworkplace_test:big_area";
	private static final BlockPos HALL = new BlockPos(11, 2, 11);

	/** A hall owned by {@code owner}, protected or not, with the village area shrunk to this test's (put back after). */
	private static VillageHallBlockEntity hall(GameTestHelper helper, ServerPlayer owner, boolean on) {
		Leftovers.clear(helper);
		int radius = VillageHalls.RADIUS;
		VillageHalls.RADIUS = 16;
		helper.setBlock(HALL, ModBlocks.VILLAGE_HALL);
		VillageHallBlockEntity hall = helper.getBlockEntity(HALL);
		hall.setOwner(owner.getUUID(), "Owner");
		hall.setProtected(on);
		// Leave nothing protected behind for the tests that run here next.
		Leftovers.after(helper, () -> {
			VillageHalls.RADIUS = radius;
			hall.setProtected(false);
		});
		return hall;
	}

	private static boolean breaks(GameTestHelper helper, ServerPlayer player, BlockPos pos) {
		helper.setBlock(pos, Blocks.STONE);
		player.gameMode.destroyBlock(helper.absolutePos(pos));
		return helper.getLevel().getBlockState(helper.absolutePos(pos)).isAir();
	}

	/** Right-clicks the block at {@code pos} with an empty hand, as the player's client would ask the server to. */
	private static void use(GameTestHelper helper, ServerPlayer player, BlockPos pos) {
		BlockPos at = helper.absolutePos(pos);
		player.gameMode.useItemOn(player, helper.getLevel(), ItemStack.EMPTY, InteractionHand.MAIN_HAND,
			new BlockHitResult(Vec3.atCenterOf(at), Direction.UP, at, false));
	}

	/** Strangers can't break blocks in a protected village; the owner and their friends can; unprotected, anyone can. */
	//$ gametest_ticks_batch AREA '100' '"protectionKeepsStrangersFromBreaking"'
	@GameTest(template = AREA, timeoutTicks = 100, batch = "protectionKeepsStrangersFromBreaking")
	public void protectionKeepsStrangersFromBreaking(GameTestHelper helper) {
		ServerPlayer owner = helper.makeMockServerPlayerInLevel();
		ServerPlayer stranger = helper.makeMockServerPlayerInLevel();
		ServerPlayer friend = helper.makeMockServerPlayerInLevel();
		VillageHallBlockEntity hall = hall(helper, owner, true);
		Friends friends = Friends.get(helper.getLevel().getServer());
		friends.add(owner.getUUID(), friend.getUUID(), "Friend");
		Leftovers.after(helper, () -> friends.remove(owner.getUUID(), friend.getUUID()));

		helper.assertFalse(breaks(helper, stranger, new BlockPos(5, 2, 5)), "a stranger broke a block in a protected village");
		helper.assertTrue(breaks(helper, owner, new BlockPos(6, 2, 5)), "the owner couldn't break a block");
		helper.assertTrue(breaks(helper, friend, new BlockPos(7, 2, 5)), "the owner's friend couldn't break a block");
		// Outside the village's area the stranger can build as they like.
		helper.assertTrue(VillageProtection.keeper(helper.getLevel(), stranger, helper.absolutePos(HALL).offset(40, 0, 0)).isEmpty(),
			"the protection reaches past the village");
		hall.setProtected(false);
		helper.assertTrue(breaks(helper, stranger, new BlockPos(8, 2, 5)), "a stranger couldn't break a block once protection was off");
		helper.succeed();
	}

	/** Strangers can't open chests in a protected village, but can open doors; nor hurt the villagers. */
	//$ gametest_ticks_batch AREA '100' '"protectionKeepsChestsShut"'
	@GameTest(template = AREA, timeoutTicks = 100, batch = "protectionKeepsChestsShut")
	public void protectionKeepsChestsShut(GameTestHelper helper) {
		ServerPlayer owner = helper.makeMockServerPlayerInLevel();
		ServerPlayer stranger = helper.makeMockServerPlayerInLevel();
		hall(helper, owner, true);
		helper.setBlock(new BlockPos(5, 2, 5), Blocks.CHEST);
		helper.setBlock(new BlockPos(8, 2, 5), Blocks.OAK_DOOR.defaultBlockState());
		helper.setBlock(new BlockPos(8, 3, 5), Blocks.OAK_DOOR.defaultBlockState().setValue(DoorBlock.HALF, net.minecraft.world.level.block.state.properties.DoubleBlockHalf.UPPER));

		use(helper, stranger, new BlockPos(5, 2, 5));
		helper.assertTrue(stranger.containerMenu == stranger.inventoryMenu, "a stranger opened a chest in a protected village");
		use(helper, owner, new BlockPos(5, 2, 5));
		helper.assertTrue(owner.containerMenu != owner.inventoryMenu, "the owner couldn't open their chest");
		use(helper, stranger, new BlockPos(8, 2, 5));
		helper.assertBlockProperty(new BlockPos(8, 2, 5), DoorBlock.OPEN, true);

		Villager villager = helper.spawn(EntityType.VILLAGER, new BlockPos(6, 2, 8));
		helper.assertFalse(VillageProtection.mayChange(helper.getLevel(), stranger, villager.blockPosition()),
			"a stranger may hurt the village's villagers");
		helper.assertTrue(VillageProtection.mayChange(helper.getLevel(), owner, villager.blockPosition()), "the owner is kept out of their own village");
		helper.succeed();
	}

	/** Only the hall's owner turns protection on and off; a hall nobody owns goes to whoever protects it first. */
	//$ gametest_ticks_batch AREA '100' '"onlyTheOwnerProtectsTheVillage"'
	@GameTest(template = AREA, timeoutTicks = 100, batch = "onlyTheOwnerProtectsTheVillage")
	public void onlyTheOwnerProtectsTheVillage(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		ServerPlayer owner = helper.makeMockServerPlayerInLevel();
		ServerPlayer stranger = helper.makeMockServerPlayerInLevel();
		VillageHallBlockEntity hall = hall(helper, owner, false);
		BlockPos at = helper.absolutePos(HALL);

		VillageProtection.toggle(level, at, stranger);
		helper.assertFalse(hall.isProtected(), "a stranger protected someone else's village");
		VillageProtection.toggle(level, at, owner);
		helper.assertTrue(hall.isProtected(), "the owner couldn't protect their village");
		VillageProtection.toggle(level, at, owner);
		helper.assertFalse(hall.isProtected(), "the owner couldn't open their village again");

		hall.setOwner(null, "");
		VillageProtection.toggle(level, at, stranger);
		helper.assertTrue(hall.isProtected() && stranger.getUUID().equals(hall.owner()), "an unowned hall wasn't claimed by who protected it");
		helper.succeed();
	}
}
