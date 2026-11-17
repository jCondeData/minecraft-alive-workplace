package io.github.jcondedata.aliveworkplace.test;

import io.github.jcondedata.aliveworkplace.registry.ModBlocks;
import io.github.jcondedata.aliveworkplace.registry.ModItems;
import io.github.jcondedata.aliveworkplace.registry.ModVillagers;
import io.github.jcondedata.aliveworkplace.shop.ShopCounterBlockEntity;
import io.github.jcondedata.aliveworkplace.travel.TravelNetwork;
import io.github.jcondedata.aliveworkplace.ui.CopperTradeMenu;
import io.github.jcondedata.aliveworkplace.work.Jobs;
import java.util.ArrayList;
import java.util.List;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.protocol.game.ClientboundMerchantOffersPacket;
import net.minecraft.network.protocol.game.ServerboundSelectTradePacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.npc.VillagerProfession;
import net.minecraft.world.entity.npc.WanderingTrader;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.trading.MerchantOffer;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;

/**
 * QA (lane c, 2026-11-17) for ROADMAP 44.2, from the spec: the mod's own jobs trade on the Copperline screen and nothing
 * the trade screen did is lost (Milestone 47: every button, price, refusal and key). Every test opens the trades the
 * player's way (a click or sneak-click packet, the select-trade packet and slot clicks a client sends) and checks the
 * ways a trade is refused: too few emeralds, a sold-out offer, a shop whose stock went while the screen was open, a
 * second player, a villager killed mid-trade; the Ferryman without CobbleDollars; Hero of the Village prices; the
 * shift-click on the goods repeating the trade (the whisper line's promise); and vanilla's wandering trader keeping its
 * screen.
 */
public class QaCopperTrades1117cGameTests implements FabricGameTest {
	private static final String BIG = "aliveworkplace_test:big_area";
	private static final String HUGE = "aliveworkplace_test:huge_area";
	private static final BlockPos AT = new BlockPos(3, 2, 3);
	private static final int PAY_A = 0;
	private static final int RESULT = 2;

	private static QaStageFiveGameTests.Client client(GameTestHelper helper) {
		QaStageFiveGameTests.Client client = QaStageSixGameTests.client(helper, GameType.SURVIVAL, QaStageSixGameTests.name("qaCuT"));
		client.player().getInventory().clearContent();
		return client;
	}

	private static void sneakClick(ServerPlayer player, Villager villager) {
		player.setShiftKeyDown(true);
		QaStageFourGameTests.pressOn(player, villager, new ArrayList<>());
		player.setShiftKeyDown(false);
	}

	private static ClientboundMerchantOffersPacket offersPacket(QaStageFiveGameTests.Client client) {
		ClientboundMerchantOffersPacket last = null;
		client.channel().flushOutbound();
		Object msg;
		while ((msg = client.channel().readOutbound()) != null) {
			if (msg instanceof ClientboundMerchantOffersPacket packet) {
				last = packet;
			}
		}
		return last;
	}

	private static Villager tailor(GameTestHelper helper, int level) {
		Villager v = helper.spawn(EntityType.VILLAGER, AT);
		v.setNoAi(true);
		v.setVillagerData(v.getVillagerData().setProfession(ModVillagers.TAILOR).setLevel(level));
		v.setVillagerXp(level == 1 ? 0 : 10);
		return v;
	}

	private static int offerFor(List<MerchantOffer> offers, net.minecraft.world.item.Item result) {
		for (int i = 0; i < offers.size(); i++) {
			if (offers.get(i).getResult().is(result)) {
				return i;
			}
		}
		return -1;
	}

	private static String menuId(ServerPlayer player) {
		if (player.containerMenu == player.inventoryMenu) {
			return "no screen";
		}
		return String.valueOf(BuiltInRegistries.MENU.getKey(player.containerMenu.getType()));
	}

	/** Two emeralds for the Tailor's three-emerald Work Clothes: no goods, the offer unused, the emeralds back on closing. */
	//$ gametest_batch BIG '"qaCuTPoor"'
	@GameTest(template = BIG, batch = "qaCuTPoor")
	public void tooFewEmeraldsTradeNothingAndComeBack(GameTestHelper helper) {
		Leftovers.clear(helper);
		ServerPlayer player = client(helper).player();
		Villager tailor = tailor(helper, 1);
		sneakClick(player, tailor);
		helper.assertTrue(player.containerMenu instanceof CopperTradeMenu, "the Tailor's trades should open in Copperline: " + menuId(player));
		int i = offerFor(tailor.getOffers(), ModItems.WORK_CLOTHES);
		helper.assertTrue(i >= 0, "the Novice Tailor sells no Work Clothes");
		MerchantOffer offer = tailor.getOffers().get(i);
		int price = offer.getCostA().getCount();
		helper.assertTrue(offer.getCostA().is(Items.EMERALD) && price >= 2, "Work Clothes should cost 2+ emeralds: " + offer.getCostA());
		player.getInventory().add(new ItemStack(Items.EMERALD, price - 1));
		player.connection.handleSelectTrade(new ServerboundSelectTradePacket(i));
		helper.assertTrue(player.containerMenu.getSlot(PAY_A).getItem().getCount() == price - 1,
			"choosing the offer should move the " + (price - 1) + " emeralds into the price slot: " + player.containerMenu.getSlot(PAY_A).getItem());
		helper.assertTrue(player.containerMenu.getSlot(RESULT).getItem().isEmpty(),
			"the goods showed with too few emeralds: " + player.containerMenu.getSlot(RESULT).getItem());
		QaStageSixGameTests.click(player, RESULT, 0, ClickType.QUICK_MOVE);
		QaStageSixGameTests.click(player, RESULT, 0, ClickType.PICKUP);
		helper.assertTrue(player.getInventory().countItem(ModItems.WORK_CLOTHES) == 0 && player.containerMenu.getCarried().isEmpty(),
			"Work Clothes were handed over for " + (price - 1) + " emeralds");
		helper.assertTrue(offer.getUses() == 0, "a refused trade was counted: " + offer.getUses());
		player.closeContainer();
		helper.assertTrue(player.getInventory().countItem(Items.EMERALD) == price - 1,
			"closing the screen should give the " + (price - 1) + " emeralds back: " + player.getInventory().countItem(Items.EMERALD));
		helper.assertTrue(tailor.getTradingPlayer() == null, "the Tailor is still trading after the screen closed");
		helper.succeed();
	}

	/** A sold-out offer: the client is told it's out of stock, and the full price buys nothing. */
	//$ gametest_batch BIG '"qaCuTSoldOut"'
	@GameTest(template = BIG, batch = "qaCuTSoldOut")
	public void aSoldOutOfferIsShownAndRefused(GameTestHelper helper) {
		Leftovers.clear(helper);
		QaStageFiveGameTests.Client client = client(helper);
		ServerPlayer player = client.player();
		Villager tailor = tailor(helper, 1);
		int i = offerFor(tailor.getOffers(), ModItems.WORK_CLOTHES);
		helper.assertTrue(i >= 0, "the Novice Tailor sells no Work Clothes");
		MerchantOffer offer = tailor.getOffers().get(i);
		offer.setToOutOfStock();
		offersPacket(client);
		sneakClick(player, tailor);
		helper.assertTrue(player.containerMenu instanceof CopperTradeMenu, "the Tailor's trades should open in Copperline: " + menuId(player));
		ClientboundMerchantOffersPacket packet = offersPacket(client);
		helper.assertTrue(packet != null && packet.getOffers().get(i).isOutOfStock(), "the client wasn't told the Work Clothes are sold out");
		player.getInventory().add(offer.getCostA().copyWithCount(64));
		player.connection.handleSelectTrade(new ServerboundSelectTradePacket(i));
		QaStageSixGameTests.click(player, RESULT, 0, ClickType.QUICK_MOVE);
		helper.assertTrue(player.getInventory().countItem(ModItems.WORK_CLOTHES) == 0, "a sold-out offer handed over Work Clothes");
		player.closeContainer();
		helper.assertTrue(player.getInventory().countItem(Items.EMERALD) == 64, "emeralds lost on a sold-out offer: " + player.getInventory().countItem(Items.EMERALD));
		helper.succeed();
	}

	/** The goods shift-clicked with three prices in the pack trade three times (the whisper: "Shift-click repeats"). */
	//$ gametest_batch BIG '"qaCuTRepeat"'
	@GameTest(template = BIG, batch = "qaCuTRepeat")
	public void shiftClickOnTheGoodsRepeatsTheTrade(GameTestHelper helper) {
		Leftovers.clear(helper);
		ServerPlayer player = client(helper).player();
		Villager tailor = tailor(helper, 1);
		sneakClick(player, tailor);
		helper.assertTrue(player.containerMenu instanceof CopperTradeMenu, "the Tailor's trades should open in Copperline: " + menuId(player));
		int i = offerFor(tailor.getOffers(), ModItems.WORK_CLOTHES);
		MerchantOffer offer = tailor.getOffers().get(i);
		int price = offer.getCostA().getCount();
		player.getInventory().add(new ItemStack(Items.EMERALD, price * 3));
		player.connection.handleSelectTrade(new ServerboundSelectTradePacket(i));
		QaStageSixGameTests.click(player, RESULT, 0, ClickType.QUICK_MOVE);
		helper.assertTrue(player.getInventory().countItem(ModItems.WORK_CLOTHES) == 3 && offer.getUses() == 3,
			"three prices should buy three Work Clothes in one shift-click: " + player.getInventory().countItem(ModItems.WORK_CLOTHES) + ", uses " + offer.getUses());
		helper.assertTrue(player.getInventory().countItem(Items.EMERALD) == 0 && player.containerMenu.getSlot(PAY_A).getItem().isEmpty(),
			"every emerald should be spent");
		player.closeContainer();
		helper.succeed();
	}

	/** Hero of the Village: the cheaper price reaches the Copperline screen and buys at that price. */
	//$ gametest_batch BIG '"qaCuTHero"'
	@GameTest(template = BIG, batch = "qaCuTHero")
	public void heroOfTheVillagePricesReachTheCopperlineScreen(GameTestHelper helper) {
		Leftovers.clear(helper);
		QaStageFiveGameTests.Client client = client(helper);
		ServerPlayer player = client.player();
		player.addEffect(new MobEffectInstance(MobEffects.HERO_OF_THE_VILLAGE, 2400, 0));
		Villager tailor = tailor(helper, 1);
		int i = offerFor(tailor.getOffers(), Items.EMERALD); // 18 white wool for an emerald
		helper.assertTrue(i >= 0 && tailor.getOffers().get(i).getBaseCostA().getCount() >= 10, "the Novice Tailor buys no wool");
		offersPacket(client);
		sneakClick(player, tailor);
		helper.assertTrue(player.containerMenu instanceof CopperTradeMenu, "the Tailor's trades should open in Copperline: " + menuId(player));
		ClientboundMerchantOffersPacket packet = offersPacket(client);
		MerchantOffer offer = tailor.getOffers().get(i);
		int base = offer.getBaseCostA().getCount();
		int cheap = offer.getCostA().getCount();
		helper.assertTrue(cheap < base, "a Hero should pay less than " + base + " wool, pays " + cheap);
		helper.assertTrue(packet != null && packet.getOffers().get(i).getCostA().getCount() == cheap,
			"the client should be shown " + cheap + " wool: " + (packet == null ? "no packet" : packet.getOffers().get(i).getCostA()));
		player.getInventory().add(new ItemStack(Items.WHITE_WOOL, cheap));
		player.connection.handleSelectTrade(new ServerboundSelectTradePacket(i));
		QaStageSixGameTests.click(player, RESULT, 0, ClickType.QUICK_MOVE);
		helper.assertTrue(player.getInventory().countItem(Items.EMERALD) == 1 && player.getInventory().countItem(Items.WHITE_WOOL) == 0,
			"the Hero's " + cheap + " wool should buy an emerald: emeralds " + player.getInventory().countItem(Items.EMERALD));
		player.closeContainer();
		helper.succeed();
	}

	/** While one player trades with a mod job, a second is refused; killing the villager closes the first one's screen. */
	//$ gametest_ticks_batch BIG '100' '"qaCuTBusy"'
	@GameTest(template = BIG, timeoutTicks = 100, batch = "qaCuTBusy")
	public void aSecondPlayerIsRefusedAndADeadVillagerClosesTheScreen(GameTestHelper helper) {
		Leftovers.clear(helper);
		ServerPlayer first = client(helper).player();
		ServerPlayer second = client(helper).player();
		Villager tailor = tailor(helper, 1);
		sneakClick(first, tailor);
		helper.assertTrue(first.containerMenu instanceof CopperTradeMenu && tailor.getTradingPlayer() == first, "the first player's trades didn't open");
		sneakClick(second, tailor);
		helper.assertFalse(second.containerMenu instanceof net.minecraft.world.inventory.MerchantMenu,
			"a second player opened the trades of a villager already trading: " + menuId(second));
		helper.assertTrue(tailor.getTradingPlayer() == first, "the second click took the villager from the first player");
		first.getInventory().add(new ItemStack(Items.EMERALD, 3));
		int i = offerFor(tailor.getOffers(), ModItems.WORK_CLOTHES);
		first.connection.handleSelectTrade(new ServerboundSelectTradePacket(i));
		tailor.kill();
		helper.succeedWhen(() -> {
			helper.assertTrue(first.containerMenu == first.inventoryMenu, "the trades stay open with a dead Tailor: " + menuId(first));
			helper.assertTrue(first.getInventory().countItem(Items.EMERALD) == 3, "the emeralds in the price slot were lost: "
				+ first.getInventory().countItem(Items.EMERALD));
		});
	}

	/** A Novice mod job with no offers left, a baby and the wandering trader: no Copperline screen. */
	//$ gametest_batch BIG '"qaCuTNone"'
	@GameTest(template = BIG, batch = "qaCuTNone")
	public void noOffersABabyAndTheWanderingTraderOpenNoCopperlineScreen(GameTestHelper helper) {
		Leftovers.clear(helper);
		ServerPlayer player = client(helper).player();
		Villager empty = tailor(helper, 1);
		empty.getOffers().clear();
		sneakClick(player, empty);
		helper.assertFalse(player.containerMenu instanceof net.minecraft.world.inventory.MerchantMenu,
			"a Tailor with no offers opened a trade screen: " + menuId(player));
		if (player.containerMenu != player.inventoryMenu) {
			player.closeContainer();
		}
		empty.discard();

		Villager baby = helper.spawn(EntityType.VILLAGER, AT);
		baby.setNoAi(true);
		baby.setAge(-24000);
		baby.setVillagerData(baby.getVillagerData().setProfession(ModVillagers.TAILOR).setLevel(1));
		sneakClick(player, baby);
		helper.assertFalse(player.containerMenu instanceof net.minecraft.world.inventory.MerchantMenu, "a baby opened a trade screen: " + menuId(player));
		if (player.containerMenu != player.inventoryMenu) {
			player.closeContainer();
		}
		baby.discard();

		WanderingTrader trader = helper.spawn(EntityType.WANDERING_TRADER, AT);
		trader.setNoAi(true);
		helper.assertTrue(!trader.getOffers().isEmpty(), "the wandering trader has no offers");
		player.moveTo(trader.getX() + 1.5, trader.getY(), trader.getZ(), 90f, 0f);
		player.connection.handleInteract(net.minecraft.network.protocol.game.ServerboundInteractPacket.createInteractionPacket(trader, false,
			net.minecraft.world.InteractionHand.MAIN_HAND));
		helper.assertTrue(player.containerMenu != player.inventoryMenu && player.containerMenu.getType() == MenuType.MERCHANT && !(player.containerMenu instanceof CopperTradeMenu),
			"the wandering trader should keep vanilla's screen: " + menuId(player));
		player.closeContainer();
		trader.discard();

		Villager librarian = helper.spawn(EntityType.VILLAGER, AT);
		librarian.setNoAi(true);
		librarian.setVillagerData(librarian.getVillagerData().setProfession(VillagerProfession.LIBRARIAN).setLevel(3));
		librarian.setVillagerXp(70);
		sneakClick(player, librarian);
		helper.assertTrue(player.containerMenu != player.inventoryMenu && player.containerMenu.getType() == MenuType.MERCHANT && !(player.containerMenu instanceof CopperTradeMenu),
			"a Librarian should keep vanilla's screen: " + menuId(player));
		player.closeContainer();
		helper.succeed();
	}

	/** The Ferryman without CobbleDollars: his tickets open in Copperline and one buys for its emerald fare. */
	//$ gametest_ticks_batch HUGE '100' '"qaCuTFerry"'
	@GameTest(template = HUGE, timeoutTicks = 100, batch = "qaCuTFerry")
	public void theFerrymanSellsATicketOnTheCopperlineScreen(GameTestHelper helper) {
		Leftovers.clear(helper);
		helper.setDayTime(2000);
		BlockPos home = new BlockPos(5, 2, 5);
		BlockPos away = new BlockPos(24, 2, 24);
		helper.setBlock(home, ModBlocks.TRAVEL_POST);
		helper.setBlock(away, ModBlocks.TRAVEL_POST);
		TravelNetwork network = TravelNetwork.get(helper.getLevel().getServer());
		network.add(GlobalPos.of(helper.getLevel().dimension(), helper.absolutePos(home)), "Home");
		TravelNetwork.Post river = network.add(GlobalPos.of(helper.getLevel().dimension(), helper.absolutePos(away)), "Riverside");
		Villager ferryman = helper.spawn(EntityType.VILLAGER, new BlockPos(7, 2, 5));
		ferryman.setNoAi(true);
		Jobs.employ(helper.getLevel(), ferryman, helper.absolutePos(home), ModVillagers.TRAVEL_POST_POI, ModVillagers.FERRYMAN);
		QaStageFiveGameTests.Client client = client(helper);
		ServerPlayer player = client.player();
		network.visit(player.getUUID(), river);
		QaStageFourGameTests.pressOn(player, ferryman, client.seen());
		helper.assertTrue(player.containerMenu instanceof CopperTradeMenu && player.containerMenu.getType() == ModBlocks.TRADES_MENU,
			"the Ferryman's tickets should open in Copperline without CobbleDollars: " + menuId(player));
		CopperTradeMenu menu = (CopperTradeMenu) player.containerMenu;
		helper.assertTrue(menu.getOffers().size() == 1, "one ticket expected, offers: " + menu.getOffers().size());
		MerchantOffer offer = menu.getOffers().get(0);
		player.getInventory().add(offer.getCostA().copy());
		player.connection.handleSelectTrade(new ServerboundSelectTradePacket(0));
		QaStageSixGameTests.click(player, RESULT, 0, ClickType.QUICK_MOVE);
		ItemStack ticket = ItemStack.EMPTY;
		for (ItemStack s : player.getInventory().items) {
			if (s.is(offer.getResult().getItem())) {
				ticket = s;
			}
		}
		helper.assertTrue(!ticket.isEmpty() && ticket.getHoverName().getString().equals("Ticket to Riverside"),
			"the fare should buy a Ticket to Riverside: " + player.getInventory().items);
		helper.assertTrue(player.getInventory().countItem(Items.EMERALD) == 0, "the fare wasn't taken");
		player.closeContainer();
		helper.succeed();
	}

	/**
	 * A player's shop: the stock taken out of the chest while the screen is open. The goods aren't handed over, the payment
	 * stays the buyer's, and the screen is told the lot is sold out (the red cross).
	 */
	//$ gametest_batch BIG '"qaCuTShopGone"'
	@GameTest(template = BIG, batch = "qaCuTShopGone")
	public void aShopLotEmptiedWhileTheScreenIsOpenIsRefused(GameTestHelper helper) {
		Leftovers.clear(helper);
		BlockPos counterAt = new BlockPos(2, 2, 2);
		BlockPos chestAt = new BlockPos(2, 2, 4);
		helper.setBlock(counterAt, ModBlocks.SHOP_COUNTER);
		ShopCounterBlockEntity counter = helper.getBlockEntity(counterAt);
		counter.setOwner(java.util.UUID.randomUUID(), "Frank");
		counter.setItem(0, new ItemStack(Items.COBBLESTONE, 16));
		counter.setItem(ShopCounterBlockEntity.COLUMNS, new ItemStack(Items.EMERALD, 2));
		helper.setBlock(chestAt, Blocks.CHEST);
		Container chest = helper.getBlockEntity(chestAt);
		chest.setItem(0, new ItemStack(Items.COBBLESTONE, 16));
		Villager keeper = helper.spawn(EntityType.VILLAGER, new BlockPos(4, 2, 2));
		keeper.setNoAi(true);
		Jobs.employ(helper.getLevel(), keeper, helper.absolutePos(counterAt), ModVillagers.SHOP_COUNTER_POI, ModVillagers.SHOPKEEPER);
		QaStageFiveGameTests.Client client = client(helper);
		ServerPlayer player = client.player();
		QaStageFourGameTests.pressOn(player, keeper, client.seen());
		helper.assertTrue(player.containerMenu instanceof CopperTradeMenu, "the shopkeeper's trades should open in Copperline: " + menuId(player));
		helper.assertTrue(keeper.getOffers().size() == 1 && keeper.getOffers().get(0).getCostA().getCount() == 2,
			"one lot at 2 emeralds expected: " + keeper.getOffers().size());
		chest.clearContent();
		offersPacket(client);
		player.getInventory().add(new ItemStack(Items.EMERALD, 2));
		player.connection.handleSelectTrade(new ServerboundSelectTradePacket(0));
		QaStageSixGameTests.click(player, RESULT, 0, ClickType.QUICK_MOVE);
		helper.assertTrue(player.getInventory().countItem(Items.COBBLESTONE) == 0, "cobblestone handed over from an empty chest: "
			+ player.getInventory().countItem(Items.COBBLESTONE));
		helper.assertTrue(keeper.getOffers().get(0).isOutOfStock(), "the emptied lot should show as sold out");
		ClientboundMerchantOffersPacket packet = offersPacket(client);
		helper.assertTrue(packet != null && packet.getContainerId() == player.containerMenu.containerId && packet.getOffers().get(0).isOutOfStock(),
			"the Copperline screen wasn't told the lot is sold out");
		player.closeContainer();
		helper.assertTrue(player.getInventory().countItem(Items.EMERALD) == 2 && chest.countItem(Items.EMERALD) == 0,
			"the buyer should keep both emeralds: pack " + player.getInventory().countItem(Items.EMERALD) + ", chest " + chest.countItem(Items.EMERALD));
		helper.succeed();
	}

	/**
	 * A job change switches the screen both ways: a Librarian who becomes a Tailor trades in Copperline with the Tailor's
	 * wares, and back to Librarian gets vanilla's screen again.
	 */
	//$ gametest_batch BIG '"qaCuTJobSwap"'
	@GameTest(template = BIG, batch = "qaCuTJobSwap")
	public void aJobChangeSwitchesTheScreenBothWays(GameTestHelper helper) {
		Leftovers.clear(helper);
		ServerPlayer player = client(helper).player();
		Villager v = helper.spawn(EntityType.VILLAGER, AT);
		v.setNoAi(true);
		v.setVillagerData(v.getVillagerData().setProfession(VillagerProfession.LIBRARIAN).setLevel(1));
		sneakClick(player, v);
		helper.assertTrue(player.containerMenu != player.inventoryMenu && player.containerMenu.getType() == MenuType.MERCHANT
			&& !(player.containerMenu instanceof CopperTradeMenu), "a Librarian should get vanilla's screen: " + menuId(player));
		player.closeContainer();
		v.setVillagerData(v.getVillagerData().setProfession(ModVillagers.TAILOR).setLevel(1));
		sneakClick(player, v);
		helper.assertTrue(player.containerMenu instanceof CopperTradeMenu, "the new Tailor should trade in Copperline: " + menuId(player));
		helper.assertTrue(offerFor(((CopperTradeMenu) player.containerMenu).getOffers(), ModItems.WORK_CLOTHES) >= 0,
			"the new Tailor's screen should show the Tailor's wares, not the Librarian's");
		player.closeContainer();
		v.setVillagerData(v.getVillagerData().setProfession(VillagerProfession.LIBRARIAN).setLevel(1));
		sneakClick(player, v);
		helper.assertTrue(player.containerMenu != player.inventoryMenu && player.containerMenu.getType() == MenuType.MERCHANT
			&& !(player.containerMenu instanceof CopperTradeMenu), "a Librarian again should get vanilla's screen: " + menuId(player));
		player.closeContainer();
		helper.succeed();
	}

	/** A Tailor saved and loaded keeps his offers, their uses, and the Copperline screen, and still trades. */
	//$ gametest_batch BIG '"qaCuTReload"'
	@GameTest(template = BIG, batch = "qaCuTReload")
	public void aReloadedTailorStillTradesInCopperline(GameTestHelper helper) {
		Leftovers.clear(helper);
		ServerPlayer player = client(helper).player();
		Villager tailor = tailor(helper, 1);
		int i = offerFor(tailor.getOffers(), ModItems.WORK_CLOTHES);
		helper.assertTrue(i >= 0, "the Novice Tailor sells no Work Clothes");
		int offers = tailor.getOffers().size();
		tailor.getOffers().get(i).increaseUses();
		Villager copy = EntityType.VILLAGER.create(helper.getLevel());
		copy.load(tailor.saveWithoutId(new net.minecraft.nbt.CompoundTag()));
		copy.setUUID(java.util.UUID.randomUUID());
		tailor.discard();
		copy.setNoAi(true);
		copy.moveTo(helper.absoluteVec(net.minecraft.world.phys.Vec3.atBottomCenterOf(AT)));
		helper.getLevel().addFreshEntity(copy);
		helper.assertTrue(copy.getVillagerData().getProfession() == ModVillagers.TAILOR && copy.getOffers().size() == offers
			&& copy.getOffers().get(i).getUses() == 1, "the reload lost the Tailor's job or offers: " + copy.getOffers().size() + " of " + offers);
		sneakClick(player, copy);
		helper.assertTrue(player.containerMenu instanceof CopperTradeMenu, "the reloaded Tailor should trade in Copperline: " + menuId(player));
		MerchantOffer offer = copy.getOffers().get(i);
		player.getInventory().add(offer.getCostA().copy());
		player.connection.handleSelectTrade(new ServerboundSelectTradePacket(i));
		QaStageSixGameTests.click(player, RESULT, 0, ClickType.QUICK_MOVE);
		helper.assertTrue(player.getInventory().countItem(ModItems.WORK_CLOTHES) == 1 && offer.getUses() == 2,
			"the reloaded Tailor should sell Work Clothes: " + player.getInventory().countItem(ModItems.WORK_CLOTHES) + ", uses " + offer.getUses());
		player.closeContainer();
		helper.succeed();
	}

	/** A Master Tailor: Copperline, the client told level 5 (no bar), and a Master trade still works. */
	//$ gametest_batch BIG '"qaCuTMaster"'
	@GameTest(template = BIG, batch = "qaCuTMaster")
	public void aMasterTailorTradesWithNoLevelBar(GameTestHelper helper) {
		Leftovers.clear(helper);
		QaStageFiveGameTests.Client client = client(helper);
		ServerPlayer player = client.player();
		Villager tailor = helper.spawn(EntityType.VILLAGER, AT);
		tailor.setNoAi(true);
		tailor.setVillagerData(tailor.getVillagerData().setProfession(ModVillagers.TAILOR).setLevel(5));
		tailor.setVillagerXp(250);
		helper.assertTrue(tailor.getOffers().size() >= 1, "a Master Tailor has " + tailor.getOffers().size() + " offers");
		offersPacket(client);
		sneakClick(player, tailor);
		helper.assertTrue(player.containerMenu instanceof CopperTradeMenu, "the Master Tailor should trade in Copperline: " + menuId(player));
		ClientboundMerchantOffersPacket packet = offersPacket(client);
		helper.assertTrue(packet != null && packet.getVillagerLevel() == 5 && packet.getOffers().size() == tailor.getOffers().size(),
			"the client should be told level 5 with every offer: " + (packet == null ? "no packet" : packet.getVillagerLevel() + "/" + packet.getOffers().size()));
		helper.assertTrue(io.github.jcondedata.aliveworkplace.ui.CopperTrades.levelFill(5, 250, 0) == null
			&& io.github.jcondedata.aliveworkplace.ui.CopperTrades.levelNumbers(5, 250) == null, "a Master should show no level bar or numbers");
		int last = tailor.getOffers().size() - 1;
		MerchantOffer offer = tailor.getOffers().get(last);
		player.getInventory().add(offer.getCostA().copy());
		if (!offer.getCostB().isEmpty()) {
			player.getInventory().add(offer.getCostB().copy());
		}
		player.connection.handleSelectTrade(new ServerboundSelectTradePacket(last));
		QaStageSixGameTests.click(player, RESULT, 0, ClickType.QUICK_MOVE);
		helper.assertTrue(offer.getUses() == 1 && player.getInventory().countItem(offer.getResult().getItem()) >= offer.getResult().getCount(),
			"the Master's last offer should trade once: uses " + offer.getUses() + ", " + offer.getResult());
		player.closeContainer();
		helper.succeed();
	}

	/** Every sentence 44.2 added is in the lang file, renders its numbers, and is American English. */
	//$ gametest EMPTY_STRUCTURE
	@GameTest(template = EMPTY_STRUCTURE)
	public void theTradeScreensSentencesExistAndRender(GameTestHelper helper) {
		String[] keys = {"offers", "trade", "count.none", "count.one", "count", "empty", "whisper.idle", "whisper.empty", "whisper.offer",
			"whisper.cheaper", "whisper.sold_out", "whisper.pay", "whisper.result", "whisper.pack", "whisper.level", "whisper.master"};
		List<String> problems = new ArrayList<>();
		for (String k : keys) {
			String key = "screen.aliveworkplace.trades." + k;
			if (!net.minecraft.locale.Language.getInstance().has(key)) {
				problems.add(key + " missing");
			}
			String s = net.minecraft.network.chat.Component.translatable(key, "7", "9").getString().toLowerCase(java.util.Locale.ROOT);
			for (String british : new String[]{"colour", "centre", "grey", "cancelled", "favour", "defence", "armour", "jewell", "licence"}) {
				if (s.contains(british)) {
					problems.add(key + " uses British '" + british + "'");
				}
			}
		}
		String cheaper = net.minecraft.network.chat.Component.translatable("screen.aliveworkplace.trades.whisper.cheaper", "2", "3").getString();
		String level = net.minecraft.network.chat.Component.translatable("screen.aliveworkplace.trades.whisper.level", "30", "60").getString();
		String one = net.minecraft.network.chat.Component.translatable("screen.aliveworkplace.trades.count.one", "1").getString();
		helper.assertTrue(problems.isEmpty(), String.join("; ", problems));
		helper.assertTrue(cheaper.equals("Cheaper for you: 2 instead of 3."), "cheaper reads: " + cheaper);
		helper.assertTrue(level.equals("30 of 60 XP to the next level."), "level reads: " + level);
		helper.assertTrue(one.equals("1 offer"), "one offer reads: " + one);
		helper.succeed();
	}
}
