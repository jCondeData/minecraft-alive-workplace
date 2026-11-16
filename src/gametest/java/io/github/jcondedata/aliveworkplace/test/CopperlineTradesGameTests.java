package io.github.jcondedata.aliveworkplace.test;

import io.github.jcondedata.aliveworkplace.AliveWorkplace;
import io.github.jcondedata.aliveworkplace.registry.ModBlocks;
import io.github.jcondedata.aliveworkplace.registry.ModVillagers;
import io.github.jcondedata.aliveworkplace.shop.ShopCounterBlockEntity;
import io.github.jcondedata.aliveworkplace.ui.CopperLayout;
import io.github.jcondedata.aliveworkplace.ui.CopperSlots;
import io.github.jcondedata.aliveworkplace.ui.CopperTradeMenu;
import io.github.jcondedata.aliveworkplace.ui.CopperTrades;
import io.github.jcondedata.aliveworkplace.work.Jobs;
import java.util.ArrayList;
import java.util.List;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.protocol.game.ClientboundMerchantOffersPacket;
import net.minecraft.network.protocol.game.ServerboundSelectTradePacket;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.npc.VillagerProfession;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.trading.MerchantOffer;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;

/**
 * ROADMAP 44.2: the trades of the mod's own jobs open in Copperline, on vanilla's merchant menu with its own menu type
 * ({@code aliveworkplace:trades}); vanilla's professions keep {@code minecraft:merchant}. Each test opens the trades the
 * player's way (a sneak-click, the packets a real client sends) and checks that every trade still goes through, that a
 * shopkeeper's offers are still rebuilt from the stock when the screen opens ({@code VillagerMixin}), that the level and
 * experience still reach the screen and a trade still levels the villager up, and that the screen's parts sit clear of
 * the real slots.
 */
public class CopperlineTradesGameTests implements FabricGameTest {
	private static final String BIG = "aliveworkplace_test:big_area";
	private static final BlockPos VILLAGER = new BlockPos(3, 2, 3);
	private static final int RESULT = 2;

	private static QaStageFiveGameTests.Client client(GameTestHelper helper) {
		QaStageFiveGameTests.Client client = QaStageSixGameTests.client(helper, GameType.SURVIVAL, QaStageSixGameTests.name("cuTrade"));
		client.player().getInventory().clearContent();
		return client;
	}

	/** A sneak-click with an empty hand, as a client sends it. */
	private static void sneakClick(ServerPlayer player, Villager villager) {
		player.setShiftKeyDown(true);
		QaStageFourGameTests.pressOn(player, villager, new ArrayList<>());
		player.setShiftKeyDown(false);
	}

	/** The last merchant offers packet sent to {@code client} since the last read (or null). */
	private static ClientboundMerchantOffersPacket offersPacket(QaStageFiveGameTests.Client client) {
		ClientboundMerchantOffersPacket last = null;
		client.channel().flushOutbound(); // packets sent during the server's tick wait for its end to be flushed
		Object msg;
		while ((msg = client.channel().readOutbound()) != null) {
			if (msg instanceof ClientboundMerchantOffersPacket packet) {
				last = packet;
			}
		}
		return last;
	}

	private static Villager villager(GameTestHelper helper, VillagerProfession job, int level, int xp) {
		Villager v = helper.spawn(EntityType.VILLAGER, VILLAGER);
		v.setNoAi(true);
		v.setVillagerData(v.getVillagerData().setProfession(job).setLevel(level));
		v.setVillagerXp(xp);
		return v;
	}

	/** Puts the price of offer {@code i} in the pack, chooses the offer as the screen's click does, shift-clicks the goods. */
	private static ItemStack trade(ServerPlayer player, int i) {
		MerchantOffer offer = ((CopperTradeMenu) player.containerMenu).getOffers().get(i);
		player.getInventory().clearContent();
		player.getInventory().add(offer.getCostA().copy());
		if (!offer.getCostB().isEmpty()) {
			player.getInventory().add(offer.getCostB().copy());
		}
		player.connection.handleSelectTrade(new ServerboundSelectTradePacket(i));
		ItemStack goods = offer.getResult().copy();
		QaStageSixGameTests.click(player, RESULT, 0, ClickType.QUICK_MOVE);
		return goods;
	}

	/** A Tailor (one of the crafters) sneak-clicked opens on the Copperline menu, and every one of her offers trades. */
	//$ gametest_batch BIG '"cuTradeCrafter"'
	@GameTest(template = BIG, batch = "cuTradeCrafter")
	public void crafterTradesOpenInCopperlineAndEveryTradeGoesThrough(GameTestHelper helper) {
		Leftovers.clear(helper);
		QaStageFiveGameTests.Client client = client(helper);
		ServerPlayer player = client.player();
		Villager tailor = villager(helper, ModVillagers.TAILOR, 2, 20);
		helper.assertTrue(tailor.getVillagerData().getProfession() == ModVillagers.TAILOR, "the villager isn't a Tailor");
		offersPacket(client);
		sneakClick(player, tailor);
		helper.assertTrue(player.containerMenu instanceof CopperTradeMenu && player.containerMenu.getType() == ModBlocks.TRADES_MENU,
			"a Tailor's sneak-click should open the Copperline trades, not " + player.containerMenu + " / "
				+ BuiltInRegistries.MENU.getKey(player.containerMenu.getType()));
		CopperTradeMenu menu = (CopperTradeMenu) player.containerMenu;
		ClientboundMerchantOffersPacket packet = offersPacket(client);
		helper.assertTrue(packet != null && packet.getContainerId() == menu.containerId, "the offers never reached the client");
		int offers = tailor.getOffers().size();
		helper.assertTrue(offers >= 2 && packet.getOffers().size() == offers, "the Tailor's " + offers + " offers, sent " + packet.getOffers().size());
		for (int i = 0; i < offers; i++) {
			MerchantOffer offer = tailor.getOffers().get(i);
			int uses = offer.getUses();
			ItemStack goods = trade(player, i);
			helper.assertTrue(player.getInventory().countItem(goods.getItem()) >= goods.getCount(),
				"offer " + i + " (" + offer.getCostA() + " for " + goods + ") handed over nothing: " + player.getInventory().items);
			helper.assertTrue(offer.getUses() == uses + 1, "offer " + i + " wasn't counted as used: " + offer.getUses());
			helper.assertTrue(player.getInventory().countItem(offer.getCostA().getItem()) == 0 || offer.getResult().is(offer.getCostA().getItem()),
				"offer " + i + "'s price wasn't taken");
		}
		player.closeContainer();
		helper.assertTrue(tailor.getTradingPlayer() == null, "closing the screen left the Tailor trading");
		helper.succeed();
	}

	/** A Farmer keeps vanilla's trade screen: the plain merchant menu type. */
	//$ gametest_batch BIG '"cuTradeVanilla"'
	@GameTest(template = BIG, batch = "cuTradeVanilla")
	public void vanillaProfessionsKeepVanillasTradeScreen(GameTestHelper helper) {
		Leftovers.clear(helper);
		ServerPlayer player = client(helper).player();
		Villager farmer = villager(helper, VillagerProfession.FARMER, 1, 0);
		helper.assertTrue(!farmer.getOffers().isEmpty(), "the Farmer has no offers");
		sneakClick(player, farmer);
		helper.assertTrue(player.containerMenu.getType() == MenuType.MERCHANT && !(player.containerMenu instanceof CopperTradeMenu),
			"a Farmer should keep vanilla's trade screen, got " + BuiltInRegistries.MENU.getKey(player.containerMenu.getType()));
		player.closeContainer();
		helper.succeed();
	}

	/**
	 * Every job of the mod that has offers (the crafters, the retired ones registered for old saves, the rest) opens its
	 * trades in Copperline, as the click reaches vanilla's {@code mobInteract}.
	 */
	//$ gametest_batch BIG '"cuTradeEveryJob"'
	@GameTest(template = BIG, batch = "cuTradeEveryJob")
	public void everyModJobWithOffersTradesInCopperline(GameTestHelper helper) {
		Leftovers.clear(helper);
		ServerPlayer player = client(helper).player();
		List<String> opened = new ArrayList<>();
		List<String> wrong = new ArrayList<>();
		for (ResourceLocation id : BuiltInRegistries.VILLAGER_PROFESSION.keySet()) {
			if (!id.getNamespace().equals(AliveWorkplace.MOD_ID)) {
				continue;
			}
			VillagerProfession job = BuiltInRegistries.VILLAGER_PROFESSION.get(id);
			Villager v = villager(helper, job, 1, 1);
			if (v.getVillagerData().getProfession() != job || v.getOffers().isEmpty()) {
				v.discard();
				continue;
			}
			player.setPos(v.getX() + 1, v.getY(), v.getZ());
			v.mobInteract(player, InteractionHand.MAIN_HAND);
			if (player.containerMenu instanceof CopperTradeMenu && player.containerMenu.getType() == ModBlocks.TRADES_MENU) {
				opened.add(id.getPath());
			} else if (player.containerMenu != player.inventoryMenu) {
				wrong.add(id.getPath() + " opened " + BuiltInRegistries.MENU.getKey(player.containerMenu.getType()));
			}
			if (player.containerMenu != player.inventoryMenu) {
				player.closeContainer();
			}
			v.discard();
		}
		helper.assertTrue(wrong.isEmpty(), "jobs whose trades didn't open in Copperline: " + wrong);
		helper.assertTrue(opened.contains("tailor") && opened.contains("vintner") && opened.size() >= 5,
			"too few of the mod's jobs traded in Copperline: " + opened);
		helper.succeed();
	}

	/**
	 * A shopkeeper without CobbleDollars: the trades open in Copperline with the offers rebuilt from the stock each time
	 * (VillagerMixin's refresh), a sale goes through and is paid into the chest, and a lot added to the counter shows the
	 * next time the trades open.
	 */
	//$ gametest_batch BIG '"cuTradeShop"'
	@GameTest(template = BIG, batch = "cuTradeShop")
	public void shopkeeperOffersRefreshAndSellOnTheCopperlineScreen(GameTestHelper helper) {
		Leftovers.clear(helper);
		BlockPos counterAt = new BlockPos(2, 2, 2);
		BlockPos chestAt = new BlockPos(2, 2, 4);
		helper.setBlock(counterAt, ModBlocks.SHOP_COUNTER);
		ShopCounterBlockEntity counter = helper.getBlockEntity(counterAt);
		counter.setOwner(java.util.UUID.randomUUID(), "Frank");
		counter.setItem(0, new ItemStack(Items.COBBLESTONE, 16));
		counter.setItem(ShopCounterBlockEntity.COLUMNS, new ItemStack(Items.EMERALD, 1));
		helper.setBlock(chestAt, Blocks.CHEST);
		Container chest = helper.getBlockEntity(chestAt);
		chest.setItem(0, new ItemStack(Items.COBBLESTONE, 32));
		chest.setItem(1, new ItemStack(Items.TORCH, 16));
		Villager keeper = helper.spawn(EntityType.VILLAGER, new BlockPos(4, 2, 2));
		keeper.setNoAi(true);
		Jobs.employ(helper.getLevel(), keeper, helper.absolutePos(counterAt), ModVillagers.SHOP_COUNTER_POI, ModVillagers.SHOPKEEPER);
		ServerPlayer player = client(helper).player();

		QaStageFourGameTests.pressOn(player, keeper, new ArrayList<>());
		helper.assertTrue(player.containerMenu instanceof CopperTradeMenu, "the shopkeeper's trades should open in Copperline: " + player.containerMenu);
		helper.assertTrue(keeper.getOffers().size() == 1, "one lot on the counter, offers: " + keeper.getOffers().size());
		ItemStack goods = trade(player, 0);
		helper.assertTrue(goods.is(Items.COBBLESTONE) && player.getInventory().countItem(Items.COBBLESTONE) == 16,
			"the buyer should have 16 cobblestone: " + player.getInventory().countItem(Items.COBBLESTONE));
		helper.assertTrue(chest.countItem(Items.COBBLESTONE) == 16 && chest.countItem(Items.EMERALD) == 1,
			"the chest should hold 16 cobblestone and the emerald: " + chest.countItem(Items.COBBLESTONE) + ", " + chest.countItem(Items.EMERALD));
		player.closeContainer();

		counter.setItem(1, new ItemStack(Items.TORCH, 8));
		counter.setItem(ShopCounterBlockEntity.COLUMNS + 1, new ItemStack(Items.EMERALD, 1));
		QaStageFourGameTests.pressOn(player, keeper, new ArrayList<>());
		helper.assertTrue(player.containerMenu instanceof CopperTradeMenu, "the trades didn't open again: " + player.containerMenu);
		List<MerchantOffer> offers = ((CopperTradeMenu) player.containerMenu).getOffers();
		helper.assertTrue(offers.size() == 2 && offers.stream().anyMatch(o -> o.getResult().is(Items.TORCH)),
			"the torches added to the counter should be on sale now: " + offers.size() + " offers");
		player.closeContainer();
		helper.succeed();
	}

	/**
	 * The level bar: the level, the experience and the bar's switch reach the client with the offers, the bar fills as
	 * vanilla's does, a trade adds its experience, and with enough the villager levels up once the screen is closed (the
	 * next opening says the new level).
	 */
	//$ gametest_batch BIG '"cuTradeLevel"'
	@GameTest(template = BIG, batch = "cuTradeLevel")
	public void levelBarStillFillsAndATradeLevelsTheVillagerUp(GameTestHelper helper) {
		Leftovers.clear(helper);
		QaStageFiveGameTests.Client client = client(helper);
		ServerPlayer player = client.player();
		Villager tailor = villager(helper, ModVillagers.TAILOR, 1, 8);
		tailor.setNoAi(false);
		offersPacket(client);
		sneakClick(player, tailor);
		helper.assertTrue(player.containerMenu instanceof CopperTradeMenu, "the Tailor's trades didn't open in Copperline: " + player.containerMenu);
		ClientboundMerchantOffersPacket packet = offersPacket(client);
		helper.assertTrue(packet != null && packet.getVillagerLevel() == 1 && packet.getVillagerXp() == 8 && packet.showProgress(),
			"the client should get level 1, 8 experience and the bar: " + (packet == null ? "no packet"
				: packet.getVillagerLevel() + ", " + packet.getVillagerXp() + ", " + packet.showProgress()));
		// What the client's copy of the menu shows from that packet, as vanilla's packet handler fills it in.
		CopperTradeMenu shown = ModBlocks.TRADES_MENU.create(packet.getContainerId(), player.getInventory());
		shown.setOffers(packet.getOffers());
		shown.setXp(packet.getVillagerXp());
		shown.setMerchantLevel(packet.getVillagerLevel());
		shown.setShowProgressBar(packet.showProgress());
		float[] fill = CopperTrades.levelFill(shown.getTraderLevel(), shown.getTraderXp(), 0);
		helper.assertTrue(shown.showProgressBar() && fill != null && Math.abs(fill[0] - 0.8f) < 0.001f,
			"8 of a Novice's 10 experience should fill the bar to 0.8: " + (fill == null ? "no bar" : fill[0]));
		helper.assertTrue(CopperTrades.levelFill(5, 300, 0) == null, "a Master shows no bar");
		int[] numbers = CopperTrades.levelNumbers(2, 40);
		helper.assertTrue(numbers != null && numbers[0] == 30 && numbers[1] == 60, "an Apprentice with 40: 30 of 60, got "
			+ java.util.Arrays.toString(numbers));

		int wool = -1;
		for (int i = 0; i < tailor.getOffers().size(); i++) {
			if (tailor.getOffers().get(i).getCostA().is(Items.WHITE_WOOL)) {
				wool = i;
			}
		}
		helper.assertTrue(wool >= 0, "the Novice Tailor buys no wool");
		trade(player, wool);
		helper.assertTrue(tailor.getVillagerXp() >= 10, "the trade should add its experience: " + tailor.getVillagerXp());
		player.closeContainer();
		helper.succeedWhen(() -> {
			helper.assertTrue(tailor.getVillagerData().getLevel() == 2, "the Tailor should level up to Apprentice, is " + tailor.getVillagerData().getLevel());
			offersPacket(client);
			sneakClick(player, tailor);
			ClientboundMerchantOffersPacket again = offersPacket(client);
			player.closeContainer();
			helper.assertTrue(again != null && again.getVillagerLevel() == 2, "the next opening should say level 2: "
				+ (again == null ? "no packet, menu " + player.containerMenu + ", trading " + tailor.isTrading() + ", sleeping " + tailor.isSleeping()
				+ ", offers " + tailor.getOffers().size() + ", baby " + tailor.isBaby() + ", alive " + tailor.isAlive() : "level " + again.getVillagerLevel()));
		});
	}

	/** The screen's parts round vanilla's merchant slots: every well inside the window, clear of the lintel, the whisper line, the offer list, the level row and the arrow. */
	//$ gametest_batch BIG '"cuTradeLayout"'
	@GameTest(template = BIG, batch = "cuTradeLayout")
	public void tradeScreenPartsSitClearOfTheSlots(GameTestHelper helper) {
		ServerPlayer player = helper.makeMockServerPlayerInLevel();
		CopperTradeMenu menu = ModBlocks.TRADES_MENU.create(1, player.getInventory());
		helper.assertTrue(menu.getType() == ModBlocks.TRADES_MENU && menu.slots.size() == 3 + 36, menu.slots.size() + " slots");
		int[] win = CopperTrades.window();
		int[] lintel = CopperSlots.lintel(CopperTrades.FRAME_H, CopperTrades.SHIFT, 0);
		int[] whisper = CopperSlots.whisper(CopperTrades.FRAME_H, CopperTrades.SHIFT, 0);
		List<int[]> parts = new ArrayList<>(List.of(lintel, whisper, CopperTrades.scrollbar(), CopperTrades.LEVEL, CopperTrades.ARROW));
		for (int i = 0; i < CopperTrades.ROWS; i++) {
			int[] row = CopperTrades.row(i);
			parts.add(row);
			helper.assertTrue(row[0] >= win[0] + 2 && row[1] + row[3] <= whisper[1] && row[1] >= lintel[1] + lintel[3],
				"offer row " + i + " " + java.util.Arrays.toString(row) + " sticks out");
		}
		for (Slot slot : menu.slots) {
			int[] well = CopperSlots.well(slot.x - CopperTrades.SHIFT, slot.y);
			String name = "slot " + slot.index + " at " + slot.x + "," + slot.y;
			helper.assertTrue(well[0] >= win[0] + 2 && well[1] >= win[1] && well[0] + well[2] <= win[0] + win[2] - 2
				&& well[1] + well[3] <= win[1] + win[3], name + " sticks out of the window " + java.util.Arrays.toString(win));
			for (int[] part : parts) {
				helper.assertFalse(CopperLayout.overlap(well, part), name + " is under " + java.util.Arrays.toString(part));
			}
		}
		int[] origin = CopperSlots.origin(427, 240, CopperTrades.FRAME_H, CopperTrades.SHIFT, 0);
		helper.assertTrue(origin[0] + win[0] >= 0 && origin[0] + win[0] + win[2] <= 427
			&& origin[1] + win[1] - CopperLayout.PLAQUE_RISE >= 0 && origin[1] + win[1] + win[3] <= 240, "the window does not fit 427x240");
		helper.succeed();
	}
}
