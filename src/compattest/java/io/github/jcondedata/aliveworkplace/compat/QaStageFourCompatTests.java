package io.github.jcondedata.aliveworkplace.compat;

import io.github.jcondedata.aliveworkplace.mail.DeliveryNoteItem;
import io.github.jcondedata.aliveworkplace.registry.ModItems;
import java.util.Optional;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

/**
 * QA stage 4 (2026-10-23), compat entry points clicked the way a client clicks them: block clicks through
 * {@code ServerPlayerGameMode.useItemOn}, villager clicks through the real {@code ServerboundInteractPacket}.
 */
public class QaStageFourCompatTests implements FabricGameTest {
	private static final String AREA = CompatGameTests.AREA;

	/**
	 * README, Courier routes: "With Cobblemon, a route can start at a Pasture Block"; "right-click the container to take
	 * from". A Delivery Note right-clicked on a Pasture Block (either half) marks its base as the route's start (B333's
	 * fix covers pastures too: this was written before it and failed then).
	 */
	//$ gametest_batch AREA '"qa4NotePasture"'
	@GameTest(template = AREA, batch = "qa4NotePasture")
	public void deliveryNoteMarksAPastureARealPlayerRightClicks(GameTestHelper helper) {
		BlockPos base = PastureCompatTests.pasture(helper, new BlockPos(4, 2, 4));
		ServerPlayer player = helper.makeMockServerPlayerInLevel();
		PartnerShowsCompatTests.after(helper, () -> helper.getLevel().getServer().getPlayerList().remove(player));
		player.setGameMode(GameType.SURVIVAL);
		for (BlockPos clicked : new BlockPos[] {base, base.above()}) {
			player.closeContainer();
			player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(ModItems.DELIVERY_NOTE));
			player.moveTo(Vec3.atBottomCenterOf(clicked).add(1.5, 0, 0), 90f, 0f);
			BlockHitResult hit = new BlockHitResult(Vec3.atCenterOf(clicked).relative(Direction.EAST, 0.5), Direction.EAST, clicked, false);
			InteractionResult main = player.gameMode.useItemOn(player, helper.getLevel(), player.getMainHandItem(), InteractionHand.MAIN_HAND, hit);
			if (!main.consumesAction() && main != InteractionResult.FAIL) {
				player.gameMode.useItemOn(player, helper.getLevel(), player.getOffhandItem(), InteractionHand.OFF_HAND, hit);
			}
			Optional<BlockPos> from = DeliveryNoteItem.data(player.getMainHandItem()).from();
			helper.assertTrue(from.equals(Optional.of(base)), "clicking the pasture's " + (clicked.equals(base) ? "base" : "top")
				+ " with a Delivery Note: the note's start is " + from + " (result " + main + ", screen "
				+ player.containerMenu.getClass().getSimpleName() + ")");
			helper.assertTrue(player.containerMenu == player.inventoryMenu, "the click opened " + player.containerMenu.getClass().getSimpleName());
		}
		helper.succeed();
	}

	/**
	 * README, Habitat Keeper: "Field Markers for her lure spots"; BuilderEvents' own words for her: "Sneak-right-click with
	 * an empty hand: the lure picker". A Field Marker's two corners right-clicked on the ground, then the marker
	 * right-clicked on her through the real packet: one lure spot, the marker kept. Then a sneak-right-click with an
	 * empty hand opens the lure picker, not her trades.
	 */
	//$ gametest_batch AREA '"qa4HabitatClicks"'
	@GameTest(template = AREA, batch = "qa4HabitatClicks")
	public void habitatKeeperTakesALureSpotAndOpensThePicker(GameTestHelper helper) {
		Villager keeper = HabitatKeeperCompatTests.keeper(helper);
		keeper.setNoAi(true);
		ServerPlayer player = helper.makeMockServerPlayerInLevel();
		PartnerShowsCompatTests.after(helper, () -> helper.getLevel().getServer().getPlayerList().remove(player));
		player.setGameMode(GameType.SURVIVAL);
		player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(ModItems.FIELD_MARKER));
		for (BlockPos rel : new BlockPos[] {new BlockPos(6, 1, 6), new BlockPos(8, 1, 8)}) {
			helper.setBlock(rel, net.minecraft.world.level.block.Blocks.GRASS_BLOCK);
			BlockPos abs = helper.absolutePos(rel);
			player.moveTo(Vec3.atBottomCenterOf(abs).add(1.5, 1, 0), 90f, 0f);
			player.gameMode.useItemOn(player, helper.getLevel(), player.getMainHandItem(), InteractionHand.MAIN_HAND,
				new BlockHitResult(Vec3.atCenterOf(abs).relative(Direction.UP, 0.5), Direction.UP, abs, false));
		}
		helper.assertTrue(io.github.jcondedata.aliveworkplace.farm.FieldMarkerItem.data(player.getMainHandItem()).isComplete(),
			"the two clicks didn't mark the field: " + io.github.jcondedata.aliveworkplace.farm.FieldMarkerItem.data(player.getMainHandItem()));
		click(player, keeper, false);
		helper.assertTrue(io.github.jcondedata.aliveworkplace.registry.ModAttachments.LURE_SPOTS.getOrElse(keeper, java.util.List.of()).size() == 1,
			"no lure spot after the right-click: " + io.github.jcondedata.aliveworkplace.registry.ModAttachments.LURE_SPOTS.getOrElse(keeper, java.util.List.of()));
		helper.assertTrue(player.getMainHandItem().is(ModItems.FIELD_MARKER), "the marker went: " + player.getMainHandItem());
		helper.assertTrue(!(player.containerMenu instanceof net.minecraft.world.inventory.MerchantMenu), "her trades opened for the marker");
		player.closeContainer();
		player.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
		click(player, keeper, true);
		helper.assertTrue(player.containerMenu != player.inventoryMenu && !(player.containerMenu instanceof net.minecraft.world.inventory.MerchantMenu),
			"a sneak-right-click with an empty hand opened " + player.containerMenu.getClass().getSimpleName());
		helper.succeed();
	}

	/** The client's right-click on {@code target}: the click at a spot on them, then (if no screen opened) the plain one. */
	private static void click(ServerPlayer player, Villager target, boolean sneak) {
		player.setShiftKeyDown(sneak);
		player.moveTo(target.getX() + 1.5, target.getY(), target.getZ(), 90f, 0f);
		player.connection.handleInteract(net.minecraft.network.protocol.game.ServerboundInteractPacket.createInteractionPacket(target, sneak,
			InteractionHand.MAIN_HAND, new Vec3(0, target.getBbHeight() / 2, 0)));
		player.setShiftKeyDown(false);
	}
}
