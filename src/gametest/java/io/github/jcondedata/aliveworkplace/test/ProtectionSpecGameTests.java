package io.github.jcondedata.aliveworkplace.test;

import io.github.jcondedata.aliveworkplace.hall.VillageHallBlockEntity;
import io.github.jcondedata.aliveworkplace.hall.VillageHalls;
import io.github.jcondedata.aliveworkplace.hall.VillageLedgerItem;
import io.github.jcondedata.aliveworkplace.hall.VillageProtection;
import io.github.jcondedata.aliveworkplace.registry.ModBlocks;
import io.github.jcondedata.aliveworkplace.registry.ModItems;
import io.github.jcondedata.aliveworkplace.work.ChoiceMenu;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.players.ServerOpListEntry;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.ButtonBlock;
import net.minecraft.world.level.block.entity.BellBlockEntity;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

/**
 * The tester's spec tests for village protection (0.135.0) and the treasury (0.134.0), from CHANGELOG.md: "only you,
 * your friends and operators can break, place or open things in the village, or hurt its villagers and animals;
 * everyone can still come in, open doors, trade and ring the bell ... villageProtection in the config switches it off".
 */
public class ProtectionSpecGameTests implements net.fabricmc.fabric.api.gametest.v1.FabricGameTest {
	private static final String AREA = "aliveworkplace_test:big_area";
	static final BlockPos HALL = new BlockPos(11, 2, 11);

	static ServerPlayer player(GameTestHelper helper) {
		ServerPlayer player = helper.makeMockServerPlayerInLevel();
		player.setGameMode(GameType.SURVIVAL);
		Leftovers.after(helper, () -> helper.getLevel().getServer().getPlayerList().remove(player));
		return player;
	}

	/** A protected hall owned by {@code owner}, the village area shrunk to this test's (put back after). */
	static VillageHallBlockEntity protectedHall(GameTestHelper helper, ServerPlayer owner) {
		Leftovers.clear(helper);
		int radius = VillageHalls.RADIUS;
		boolean enabled = VillageProtection.ENABLED;
		VillageHalls.RADIUS = 16;
		helper.setBlock(HALL, ModBlocks.VILLAGE_HALL);
		VillageHallBlockEntity hall = helper.getBlockEntity(HALL);
		hall.setOwner(owner.getUUID(), "Owner");
		hall.setProtected(true);
		Leftovers.after(helper, () -> {
			VillageHalls.RADIUS = radius;
			VillageProtection.ENABLED = enabled;
			hall.setProtected(false);
		});
		return hall;
	}

	private static void use(GameTestHelper helper, ServerPlayer player, BlockPos pos, Direction face, ItemStack held) {
		BlockPos at = helper.absolutePos(pos);
		player.setItemInHand(InteractionHand.MAIN_HAND, held);
		player.gameMode.useItemOn(player, helper.getLevel(), held, InteractionHand.MAIN_HAND,
			new BlockHitResult(Vec3.atCenterOf(at).relative(face, 0.5), face, at, false));
	}

	/**
	 * A stranger can't place a block or empty a bucket in a protected village, but can still ring the bell, press a
	 * button and trade with a villager.
	 */
	//$ gametest_ticks_batch AREA '100' '"strangersMayRingTradeButNotBuild"'
	@GameTest(template = AREA, timeoutTicks = 100, batch = "strangersMayRingTradeButNotBuild")
	public void strangersMayRingTradeButNotBuild(GameTestHelper helper) {
		ServerPlayer owner = player(helper);
		ServerPlayer stranger = player(helper);
		protectedHall(helper, owner);
		ServerLevel level = helper.getLevel();
		// Placing a block on the floor
		use(helper, stranger, new BlockPos(5, 1, 5), Direction.UP, new ItemStack(Items.COBBLESTONE, 4));
		helper.assertBlockPresent(Blocks.AIR, new BlockPos(5, 2, 5));
		// Emptying a water bucket, looking down at the floor
		BlockPos stand = helper.absolutePos(new BlockPos(8, 2, 8));
		stranger.moveTo(stand.getX() + 0.5, stand.getY(), stand.getZ() + 0.5, 0f, 90f);
		ItemStack bucket = new ItemStack(Items.WATER_BUCKET);
		stranger.setItemInHand(InteractionHand.MAIN_HAND, bucket);
		stranger.gameMode.useItem(stranger, level, bucket, InteractionHand.MAIN_HAND);
		helper.assertTrue(level.getFluidState(stand).isEmpty(), "a stranger poured water in a protected village");
		// The bell rings, the button presses
		helper.setBlock(new BlockPos(3, 2, 8), Blocks.BELL);
		use(helper, stranger, new BlockPos(3, 2, 8), Direction.NORTH, ItemStack.EMPTY);
		BellBlockEntity bell = helper.getBlockEntity(new BlockPos(3, 2, 8));
		helper.assertTrue(bell.shaking, "a stranger couldn't ring the bell");
		helper.setBlock(new BlockPos(3, 2, 12), Blocks.STONE);
		helper.setBlock(new BlockPos(3, 2, 13), Blocks.STONE_BUTTON.defaultBlockState().setValue(ButtonBlock.FACING, Direction.SOUTH));
		use(helper, stranger, new BlockPos(3, 2, 13), Direction.SOUTH, ItemStack.EMPTY);
		helper.assertBlockProperty(new BlockPos(3, 2, 13), ButtonBlock.POWERED, true);
		// Trading, as the stranger's client asks for it
		Villager farmer = helper.spawn(EntityType.VILLAGER, new BlockPos(14, 2, 5));
		farmer.setNoAi(true);
		farmer.setVillagerData(farmer.getVillagerData().setProfession(net.minecraft.world.entity.npc.VillagerProfession.FARMER).setLevel(2));
		stranger.moveTo(farmer.getX() + 1, farmer.getY(), farmer.getZ(), 90f, 0f);
		stranger.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
		stranger.connection.handleInteract(net.minecraft.network.protocol.game.ServerboundInteractPacket.createInteractionPacket(farmer, false, InteractionHand.MAIN_HAND));
		helper.assertTrue(stranger.containerMenu instanceof net.minecraft.world.inventory.MerchantMenu,
			"a stranger couldn't trade in a protected village: " + stranger.containerMenu);
		stranger.closeContainer();
		helper.succeed();
	}

	/** An operator may build in anyone's protected village. */
	//$ gametest_ticks_batch AREA '100' '"anOperatorMayBuildInAProtectedVillage"'
	@GameTest(template = AREA, timeoutTicks = 100, batch = "anOperatorMayBuildInAProtectedVillage")
	public void anOperatorMayBuildInAProtectedVillage(GameTestHelper helper) {
		ServerPlayer owner = player(helper);
		ServerPlayer op = player(helper);
		protectedHall(helper, owner);
		var ops = helper.getLevel().getServer().getPlayerList().getOps();
		ops.add(new ServerOpListEntry(op.getGameProfile(), 4, false));
		Leftovers.after(helper, () -> ops.remove(op.getGameProfile()));
		helper.setBlock(new BlockPos(5, 2, 5), Blocks.STONE);
		op.gameMode.destroyBlock(helper.absolutePos(new BlockPos(5, 2, 5)));
		helper.assertBlockPresent(Blocks.AIR, new BlockPos(5, 2, 5));
		helper.succeed();
	}

	/** {@code villageProtection: false} in the config switches the setting off: a protected hall keeps nobody out. */
	//$ gametest_ticks_batch AREA '100' '"theConfigSwitchesProtectionOff"'
	@GameTest(template = AREA, timeoutTicks = 100, batch = "theConfigSwitchesProtectionOff")
	public void theConfigSwitchesProtectionOff(GameTestHelper helper) {
		ServerPlayer owner = player(helper);
		ServerPlayer stranger = player(helper);
		VillageHallBlockEntity hall = protectedHall(helper, owner);
		VillageProtection.ENABLED = false;
		helper.setBlock(new BlockPos(5, 2, 5), Blocks.STONE);
		stranger.gameMode.destroyBlock(helper.absolutePos(new BlockPos(5, 2, 5)));
		helper.assertBlockPresent(Blocks.AIR, new BlockPos(5, 2, 5));
		boolean was = hall.isProtected();
		String said = VillageProtection.toggle(helper.getLevel(), helper.absolutePos(HALL), owner).getString();
		helper.assertTrue(hall.isProtected() == was, "the setting flipped with protection switched off: " + said);
		helper.succeed();
	}

	/** The hall's owner and its protection survive a save and reload of the hall. */
	//$ gametest_ticks_batch AREA '100' '"protectionSurvivesAReload"'
	@GameTest(template = AREA, timeoutTicks = 100, batch = "protectionSurvivesAReload")
	public void protectionSurvivesAReload(GameTestHelper helper) {
		ServerPlayer owner = player(helper);
		VillageHallBlockEntity hall = protectedHall(helper, owner);
		ServerLevel level = helper.getLevel();
		BlockPos at = helper.absolutePos(HALL);
		CompoundTag tag = hall.saveWithFullMetadata(level.registryAccess());
		BlockEntity loaded = BlockEntity.loadStatic(at, level.getBlockState(at), tag, level.registryAccess());
		helper.assertTrue(loaded instanceof VillageHallBlockEntity copy && copy.isProtected() && owner.getUUID().equals(copy.owner())
			&& "Owner".equals(copy.ownerName()), "the hall came back as " + tag);
		helper.succeed();
	}

	/** The owner collects their own treasury through the same ledger (the control for the test above). */
	//$ gametest_ticks_batch AREA '100' '"theOwnersLedgerCollectsTheTreasury"'
	@GameTest(template = AREA, timeoutTicks = 100, batch = "theOwnersLedgerCollectsTheTreasury")
	public void theOwnersLedgerCollectsTheTreasury(GameTestHelper helper) {
		ServerPlayer owner = player(helper);
		VillageHallBlockEntity hall = protectedHall(helper, owner);
		ServerLevel level = helper.getLevel();
		hall.setTreasury(300);
		ItemStack ledger = new ItemStack(ModItems.VILLAGE_LEDGER);
		VillageLedgerItem.bind(level, owner, ledger, helper.absolutePos(HALL));
		owner.setItemInHand(InteractionHand.MAIN_HAND, ledger);
		helper.runAfterDelay(2, () -> {
			ledger.use(level, owner, InteractionHand.MAIN_HAND);
			helper.assertTrue(owner.containerMenu instanceof ChoiceMenu, "the owner's ledger didn't open the hall");
			((ChoiceMenu) owner.containerMenu).press(0, owner);
			owner.closeContainer();
			helper.assertTrue(owner.getInventory().countItem(Items.EMERALD) == 3 && hall.treasury() == 0,
				"collected " + owner.getInventory().countItem(Items.EMERALD) + ", left " + hall.treasury());
			helper.succeed();
		});
	}
}
