package io.github.jcondedata.aliveworkplace.test;

import io.github.jcondedata.aliveworkplace.build.Employer;
import io.github.jcondedata.aliveworkplace.build.Friends;
import io.github.jcondedata.aliveworkplace.hall.VillageProtection;
import io.github.jcondedata.aliveworkplace.mail.DeliveryNoteItem;
import io.github.jcondedata.aliveworkplace.mail.PostmanWork;
import io.github.jcondedata.aliveworkplace.mail.RouteData;
import io.github.jcondedata.aliveworkplace.registry.ModAttachments;
import io.github.jcondedata.aliveworkplace.registry.ModBlocks;
import io.github.jcondedata.aliveworkplace.registry.ModItems;
import io.github.jcondedata.aliveworkplace.registry.ModVillagers;
import io.github.jcondedata.aliveworkplace.work.Jobs;
import java.util.List;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.NbtOps;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

/**
 * B717: village protection against the Delivery Note. In a protected village only the owner, their friends and operators
 * may name a container on a note (as good as opening it), and a postman's courier route doesn't carry out of the village
 * for anyone else, routes saved before the fix included.
 */
public class DeliveryNoteProtectionGameTests implements net.fabricmc.fabric.api.gametest.v1.FabricGameTest {
	private static final String AREA = "aliveworkplace_test:big_area";
	private static final BlockPos CHEST = ProtectionSpecGameTests.HALL.offset(3, 0, 3);

	private static void use(GameTestHelper helper, ServerPlayer player, BlockPos pos, ItemStack held) {
		BlockPos at = helper.absolutePos(pos);
		player.setItemInHand(InteractionHand.MAIN_HAND, held);
		player.gameMode.useItemOn(player, helper.getLevel(), held, InteractionHand.MAIN_HAND,
			new BlockHitResult(Vec3.atCenterOf(at).relative(Direction.UP, 0.5), Direction.UP, at, false));
	}

	private static Container chest(GameTestHelper helper, BlockPos pos) {
		helper.setBlock(pos, Blocks.CHEST);
		Container chest = helper.getBlockEntity(pos);
		chest.setItem(0, new ItemStack(Items.DIAMOND, 10));
		return chest;
	}

	/** A stranger's note marks nothing and the chest stays shut; the owner's note names the chest and him as its maker. */
	//$ gametest_ticks_batch AREA '60' '"deliveryNoteProtection"'
	@GameTest(template = AREA, timeoutTicks = 60, batch = "deliveryNoteProtection")
	public void strangersNoteMarksNothingOwnersDoes(GameTestHelper helper) {
		ServerPlayer owner = ProtectionSpecGameTests.player(helper);
		ProtectionSpecGameTests.protectedHall(helper, owner);
		chest(helper, CHEST);
		ServerPlayer stranger = ProtectionSpecGameTests.player(helper);
		use(helper, stranger, CHEST, new ItemStack(ModItems.DELIVERY_NOTE));
		helper.assertTrue(DeliveryNoteItem.data(stranger.getMainHandItem()).from().isEmpty(), "the stranger's note marked the chest");
		helper.assertTrue(stranger.containerMenu == stranger.inventoryMenu, "the stranger's note opened the chest: " + stranger.containerMenu);

		use(helper, owner, CHEST, new ItemStack(ModItems.DELIVERY_NOTE));
		RouteData own = DeliveryNoteItem.data(owner.getMainHandItem());
		helper.assertTrue(own.from().equals(Optional.of(helper.absolutePos(CHEST))), "the owner's note didn't take his chest: " + own);
		helper.assertTrue(own.by().equals(Optional.of(owner.getUUID())), "the owner's note doesn't name him: " + own.by());
		helper.assertTrue(owner.containerMenu == owner.inventoryMenu, "the owner's note opened the chest");
		helper.succeed();
	}

	/** The owner's friend may mark a chest in his village. */
	//$ gametest_ticks_batch AREA '60' '"deliveryNoteProtectionFriend"'
	@GameTest(template = AREA, timeoutTicks = 60, batch = "deliveryNoteProtectionFriend")
	public void friendsNoteTakesTheChest(GameTestHelper helper) {
		ServerPlayer owner = ProtectionSpecGameTests.player(helper);
		ProtectionSpecGameTests.protectedHall(helper, owner);
		chest(helper, CHEST);
		ServerPlayer friend = ProtectionSpecGameTests.player(helper);
		Friends friends = Friends.get(helper.getLevel().getServer());
		friends.add(owner.getUUID(), friend.getUUID(), "Friend");
		try {
			use(helper, friend, CHEST, new ItemStack(ModItems.DELIVERY_NOTE));
			helper.assertTrue(DeliveryNoteItem.data(friend.getMainHandItem()).from().isPresent(), "the owner's friend's note didn't take the chest");
			helper.assertTrue(VillageProtection.keeper(helper.getLevel(), friend.getUUID(), helper.absolutePos(CHEST)).isEmpty(),
				"a friend's route is kept out of the village");
		} finally {
			friends.remove(owner.getUUID(), friend.getUUID());
		}
		helper.succeed();
	}

	/** With village protection off on the server, anyone's note marks the chest. */
	//$ gametest_ticks_batch AREA '60' '"deliveryNoteProtectionOff"'
	@GameTest(template = AREA, timeoutTicks = 60, batch = "deliveryNoteProtectionOff")
	public void protectionOffLetsAnyNoteMark(GameTestHelper helper) {
		ServerPlayer owner = ProtectionSpecGameTests.player(helper);
		ProtectionSpecGameTests.protectedHall(helper, owner); // puts ENABLED back after
		VillageProtection.ENABLED = false;
		chest(helper, CHEST);
		ServerPlayer stranger = ProtectionSpecGameTests.player(helper);
		use(helper, stranger, CHEST, new ItemStack(ModItems.DELIVERY_NOTE));
		helper.assertTrue(DeliveryNoteItem.data(stranger.getMainHandItem()).from().isPresent(), "with protection off a stranger's note should mark the chest");
		helper.assertTrue(VillageProtection.keeper(helper.getLevel(), stranger.getUUID(), helper.absolutePos(CHEST)).isEmpty(), "protection off still keeps routes out");
		helper.succeed();
	}

	/**
	 * Routes already on a postman: one a stranger marked doesn't carry the owner's diamonds out (a route from before the
	 * fix names nobody and goes by the postman's employer); the owner's own route may.
	 */
	//$ gametest_ticks_batch AREA '700' '"deliveryNoteProtectionRoute"'
	@GameTest(template = AREA, timeoutTicks = 700, batch = "deliveryNoteProtectionRoute")
	public void strangersSavedRouteDoesntCarryOut(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		ServerPlayer owner = ProtectionSpecGameTests.player(helper);
		ProtectionSpecGameTests.protectedHall(helper, owner);
		ServerPlayer stranger = ProtectionSpecGameTests.player(helper);
		helper.setDayTime(2000);
		Container source = chest(helper, CHEST);
		BlockPos to = new BlockPos(4, 2, 12);
		helper.setBlock(to, Blocks.CHEST);
		Container target = helper.getBlockEntity(to);
		BlockPos desk = new BlockPos(2, 2, 2);
		helper.setBlock(desk, ModBlocks.POSTAL_DESK);
		Villager postman = helper.spawn(EntityType.VILLAGER, new BlockPos(3, 2, 3));
		Jobs.employ(level, postman, helper.absolutePos(desk), ModVillagers.POSTAL_DESK_POI, ModVillagers.POSTMAN);
		ModAttachments.BUILDER_EMPLOYER.set(postman, new Employer(stranger.getUUID(), "Stranger"));
		Optional<BlockPos> from = Optional.of(helper.absolutePos(CHEST));
		Optional<BlockPos> into = Optional.of(helper.absolutePos(to));
		RouteData strangers = new RouteData(from, into, List.of(), Optional.of(stranger.getUUID()));
		RouteData old = new RouteData(from, into, List.of());
		RouteData owners = new RouteData(from, into, List.of(), Optional.of(owner.getUUID()));
		helper.assertFalse(PostmanWork.mayTake(level, postman, strangers), "a stranger's route may take from the owner's chest");
		helper.assertFalse(PostmanWork.mayTake(level, postman, old), "an old route of a stranger's postman may take from the owner's chest");
		helper.assertTrue(PostmanWork.mayTake(level, postman, owners), "the owner's own route may not take from his chest");
		// "by" is saved with the route
		RouteData back = RouteData.CODEC.parse(NbtOps.INSTANCE, RouteData.CODEC.encodeStart(NbtOps.INSTANCE, strangers).getOrThrow()).getOrThrow();
		helper.assertTrue(back.equals(strangers), "the route's maker isn't saved: " + back);

		ModAttachments.COURIER_ROUTES.set(postman, List.of(strangers, old));
		helper.runAfterDelay(600, () -> {
			helper.assertTrue(source.countItem(Items.DIAMOND) == 10, "the postman carried " + (10 - source.countItem(Items.DIAMOND)) + " diamonds out");
			helper.assertTrue(target.isEmpty(), "the stranger's chest got the owner's things");
			helper.succeed();
		});
	}
}
