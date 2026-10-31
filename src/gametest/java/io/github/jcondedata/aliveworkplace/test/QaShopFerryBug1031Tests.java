package io.github.jcondedata.aliveworkplace.test;

import io.github.jcondedata.aliveworkplace.registry.ModBlocks;
import io.github.jcondedata.aliveworkplace.registry.ModItems;
import io.github.jcondedata.aliveworkplace.registry.ModVillagers;
import io.github.jcondedata.aliveworkplace.shop.PriceTagItem;
import io.github.jcondedata.aliveworkplace.shop.ShopCounterBlockEntity;
import io.github.jcondedata.aliveworkplace.shop.Shops;
import io.github.jcondedata.aliveworkplace.travel.TravelNetwork;
import io.github.jcondedata.aliveworkplace.work.Jobs;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.GlobalPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

/**
 * QA 2026-10-31: mismatches between the Shopkeeper and Ferryman guide pages and the mod, found by
 * {@link QaShopFerry1031GameTests}' pass. These fail until the mod is fixed; the builder commits each with its fix.
 */
public class QaShopFerryBug1031Tests implements net.fabricmc.fabric.api.gametest.v1.FabricGameTest {
	private static final String AREA = "aliveworkplace_test:big_area";
	private static final String HUGE = "aliveworkplace_test:huge_area";

	/**
	 * travel-post.md: "Right-click a Travel Post ... you're told how many others you know." A player who knows one other
	 * post reads "You know 1 other posts." (message.aliveworkplace.travel.found and .known have no singular form).
	 */
	//$ gametest_ticks_batch HUGE '100' '"qaBugPostOneOther"'
	@GameTest(template = HUGE, timeoutTicks = 100, batch = "qaBugPostOneOther")
	public void rightClickingASecondPostSaysOneOtherPostSingular(GameTestHelper helper) {
		Leftovers.clear(helper);
		BlockPos a = new BlockPos(5, 2, 5);
		BlockPos b = new BlockPos(20, 2, 5);
		helper.setBlock(a, ModBlocks.TRAVEL_POST);
		helper.setBlock(b, ModBlocks.TRAVEL_POST);
		TravelNetwork network = TravelNetwork.get(helper.getLevel().getServer());
		network.add(GlobalPos.of(helper.getLevel().dimension(), helper.absolutePos(a)), "Alder");
		network.add(GlobalPos.of(helper.getLevel().dimension(), helper.absolutePos(b)), "Brook");
		QaStageFiveGameTests.Client client = QaStageSixGameTests.client(helper, GameType.SURVIVAL, QaStageSixGameTests.name("qaBugPost"));
		ServerPlayer p = client.player();
		click(helper, p, a);
		client.seen().clear();
		click(helper, p, b);
		String found = client.seen().stream().map(Component::getString).filter(s -> s.startsWith("Found the Travel Post")).findFirst().orElse("<nothing>");
		client.seen().clear();
		click(helper, p, b);
		String known = client.seen().stream().map(Component::getString).filter(s -> s.startsWith("Travel Post \"Brook\"")).findFirst().orElse("<nothing>");
		helper.assertTrue(found.endsWith("You know 1 other post."), "the second post found says: " + found);
		helper.assertTrue(known.endsWith("you know 1 other post"), "clicking it again says: " + known);
		helper.succeed();
	}

	private static void click(GameTestHelper helper, ServerPlayer p, BlockPos post) {
		BlockPos at = helper.absolutePos(post);
		p.moveTo(Vec3.atCenterOf(at.south()));
		p.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
		p.gameMode.useItemOn(p, helper.getLevel(), ItemStack.EMPTY, InteractionHand.MAIN_HAND,
			new BlockHitResult(Vec3.atCenterOf(at).relative(Direction.SOUTH, 0.5), Direction.SOUTH, at, false));
	}

	/**
	 * shopkeeper.md: "Without CobbleDollars a tag's price is paid in emeralds at the server's rate", the rate being "100
	 * per emerald" (shop-counter.md; 1,234 CobbleDollars: 12 emeralds). A Price Tag of 10,000 should then cost 100
	 * emeralds, but the shopkeeper's trade asks 64 (PriceTagItem.emeralds caps at 64): every tag over 6,400 sells for 64.
	 */
	//$ gametest AREA
	@GameTest(template = AREA)
	public void aPriceTagOver6400IsPaidAtTheRateNotCappedAt64(GameTestHelper helper) {
		ItemStack tag = new ItemStack(ModItems.PRICE_TAG);
		PriceTagItem.setPrice(tag, 10_000);
		BlockPos counterPos = new BlockPos(2, 2, 2);
		helper.setBlock(counterPos, ModBlocks.SHOP_COUNTER);
		ShopCounterBlockEntity counter = helper.getBlockEntity(counterPos);
		counter.setOwner(UUID.randomUUID(), "Frank");
		counter.setItem(0, new ItemStack(Items.DIAMOND_BLOCK, 1));
		counter.setItem(ShopCounterBlockEntity.COLUMNS, tag);
		helper.setBlock(new BlockPos(2, 2, 4), Blocks.CHEST);
		((Container) helper.getBlockEntity(new BlockPos(2, 2, 4))).setItem(0, new ItemStack(Items.DIAMOND_BLOCK, 8));
		Villager keeper = helper.spawn(EntityType.VILLAGER, new BlockPos(4, 2, 2));
		keeper.setNoAi(true);
		Jobs.employ(helper.getLevel(), keeper, helper.absolutePos(counterPos), ModVillagers.SHOP_COUNTER_POI, ModVillagers.SHOPKEEPER);
		Shops.refreshOffers(helper.getLevel(), keeper);
		int asked = keeper.getOffers().isEmpty() ? -1 : keeper.getOffers().get(0).getCostA().getCount() + keeper.getOffers().get(0).getCostB().getCount();
		helper.assertTrue(asked == -1 || asked >= 100,
			"a 10,000 CobbleDollar tag costs " + Shops.emeraldPrice(tag) + " emeralds (the trade sells a diamond block for " + asked + "), expected 100 at 100 a piece");
		helper.succeed();
	}
}
