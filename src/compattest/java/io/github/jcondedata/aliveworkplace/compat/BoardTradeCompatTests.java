package io.github.jcondedata.aliveworkplace.compat;

import io.github.jcondedata.aliveworkplace.compat.cobbledollars.CobbleDollarsBank;
import io.github.jcondedata.aliveworkplace.hall.Caravans;
import io.github.jcondedata.aliveworkplace.hall.Chronicle;
import io.github.jcondedata.aliveworkplace.hall.VillageHallBlockEntity;
import io.github.jcondedata.aliveworkplace.hall.VillageHallScreen;
import io.github.jcondedata.aliveworkplace.hall.VillageHalls;
import io.github.jcondedata.aliveworkplace.registry.ModBlocks;
import io.github.jcondedata.aliveworkplace.trade.Market;
import io.github.jcondedata.aliveworkplace.trade.TradeGoods;
import io.github.jcondedata.aliveworkplace.trade.TradePage;
import io.github.jcondedata.aliveworkplace.work.ChoiceMenu;
import io.github.jcondedata.aliveworkplace.work.Money;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;

/**
 * ROADMAP 33.5 with the pack: trading at the price board goes in CobbleDollars, to the cent. A sale pays the player's
 * account exactly the board's price and takes exactly that from the treasury (no rounding to whole emeralds, and no
 * emeralds change hands); a purchase charges the account 10% over and the treasury keeps it; a player whose account
 * can't pay buys nothing. (The emerald side, the limits and the messages: {@code BoardTradeGameTests}.)
 */
public class BoardTradeCompatTests implements FabricGameTest {
	private static final String AREA = CompatGameTests.AREA;
	private static final ResourceLocation TIMBER = ResourceLocation.fromNamespaceAndPath("aliveworkplace", "timber");

	private static List<String> lore(ItemStack icon) {
		List<String> out = new ArrayList<>();
		var lore = icon.get(DataComponents.LORE);
		if (lore != null) {
			lore.lines().forEach(l -> out.add(l.getString()));
		}
		return out;
	}

	private static int logs(Container chest) {
		int n = 0;
		for (int i = 0; i < chest.getContainerSize(); i++) {
			n += chest.getItem(i).is(Items.OAK_LOG) ? chest.getItem(i).getCount() : 0;
		}
		return n;
	}

	//$ gametest_ticks_batch AREA '100' '"boardTradeDollars"'
	@GameTest(template = AREA, timeoutTicks = 100, batch = "boardTradeDollars")
	public void theBoardPaysAndChargesInCobbleDollars(GameTestHelper helper) {
		PartnerShowsCompatTests.clearLeftovers(helper);
		PartnerShowsCompatTests.clearHalls(helper);
		ServerLevel level = helper.getLevel();
		helper.assertTrue(Money.cobbleDollars() && Money.DOLLARS_PER_EMERALD == 100, "CobbleDollars is installed here, 100 to the emerald");
		helper.setBlock(new BlockPos(8, 1, 8), ModBlocks.VILLAGE_HALL);
		helper.setBlock(new BlockPos(11, 1, 8), ModBlocks.STOREHOUSE);
		helper.setBlock(new BlockPos(11, 1, 10), Blocks.CHEST);
		BlockPos hall = helper.absolutePos(new BlockPos(8, 1, 8));
		ServerPlayer player = helper.makeMockServerPlayerInLevel();
		player.setGameMode(GameType.SURVIVAL);
		int radius = VillageHalls.RADIUS;
		helper.runAfterDelay(2, () -> {
			Caravans.Data data = Caravans.Data.get(level);
			try {
				VillageHalls.RADIUS = 8; // only this test's Storehouse
				TradeGoods.Good good = TradeGoods.get(TIMBER);
				helper.assertTrue(good != null && good.bundle() == 16, "no Timber: " + good);
				data.setWants(hall, VillageHalls.name(level, hall), List.of());
				data.setMarket(hall, new Market(List.of(), List.of(), Map.of(TIMBER, new Market.Price(150, 150, 0)), Chronicle.day(level), List.of()));
				VillageHallBlockEntity entity = (VillageHallBlockEntity) level.getBlockEntity(hall);
				entity.setTreasury(1000);
				String village = VillageHalls.name(level, hall).getString();
				Container chest = helper.getBlockEntity(new BlockPos(11, 1, 10));
				CobbleDollarsBank.take(player, CobbleDollarsBank.balance(player));
				CobbleDollarsBank.add(player, 500);
				player.getInventory().add(new ItemStack(Items.OAK_LOG, 16));

				ChoiceMenu menu = VillageHallScreen.forTest(player, hall);
				menu.press(VillageHallScreen.ROUTES, player);
				menu.press(TradePage.Tab.PRICES.slot(), player);
				int timber = -1;
				for (int s = VillageHallScreen.FIRST_ROW; s < ChoiceMenu.SIZE; s++) {
					if (menu.icon(s).getHoverName().getString().equals("Timber")) {
						timber = s;
					}
				}
				helper.assertTrue(timber >= 0, "no Timber on the Prices tab");
				helper.assertTrue(lore(menu.icon(timber)).contains("We pay: 150 CobbleDollars") && lore(menu.icon(timber)).contains("We sell: 165 CobbleDollars"),
					"the prices in CobbleDollars: " + lore(menu.icon(timber)));
				helper.assertTrue(menu.icon(TradePage.TREASURY).getHoverName().getString().equals("Treasury: 1000 CobbleDollars"), "the treasury: "
					+ menu.icon(TradePage.TREASURY).getHoverName().getString());

				// a sale: 1.5 emeralds is 150 CobbleDollars, to the cent, out of the treasury
				menu.press(timber, player);
				helper.assertTrue(CobbleDollarsBank.balance(player) == 650, "the sale paid " + (CobbleDollarsBank.balance(player) - 500) + " CobbleDollars");
				helper.assertTrue(entity.treasury() == 850 && logs(chest) == 16, "treasury " + entity.treasury() + ", " + logs(chest) + " logs in the chest");
				helper.assertTrue(player.getInventory().countItem(Items.EMERALD) == 0 && player.getInventory().countItem(Items.OAK_LOG) == 0, "emeralds changed hands");
				helper.assertTrue(lore(menu.icon(timber)).get(0).equals("You sell 16 × Timber to " + village + " for 150 CobbleDollars."), "the tooltip: " + lore(menu.icon(timber)));
				helper.assertTrue(data.market(hall).prices().get(TIMBER).cents() == 147, "the price after: " + data.market(hall).prices().get(TIMBER));

				// a purchase: 10% over 1.47 is 162 CobbleDollars, into the treasury
				chest.setItem(5, new ItemStack(Items.OAK_LOG, 32));
				menu.press(timber, player);
				helper.assertTrue(CobbleDollarsBank.balance(player) == 650 - 162, "the purchase cost " + (650 - CobbleDollarsBank.balance(player)) + " CobbleDollars");
				helper.assertTrue(entity.treasury() == 850 + 162 && logs(chest) == 32 && player.getInventory().countItem(Items.OAK_LOG) == 16,
					"treasury " + entity.treasury() + ", " + logs(chest) + " logs in the chest");
				helper.assertTrue(lore(menu.icon(timber)).get(0).equals("You buy 16 × Timber from " + village + " for 162 CobbleDollars."), "the tooltip: " + lore(menu.icon(timber)));

				// an account that can't pay buys nothing (a right click: the player carries a bundle now)
				CobbleDollarsBank.take(player, CobbleDollarsBank.balance(player) - 100);
				menu.clicked(timber, 1, net.minecraft.world.inventory.ClickType.PICKUP, player);
				helper.assertTrue(CobbleDollarsBank.balance(player) == 100 && logs(chest) == 32 && entity.treasury() == 850 + 162, "bought on credit: "
					+ CobbleDollarsBank.balance(player) + " CobbleDollars left, " + logs(chest) + " logs in the chest");
				helper.assertTrue(lore(menu.icon(timber)).get(0).equals("You can't pay 165 CobbleDollars for a bundle of Timber."), "the tooltip: " + lore(menu.icon(timber)));
				helper.succeed();
			} finally {
				VillageHalls.RADIUS = radius;
				data.remove(hall);
				level.getServer().getPlayerList().remove(player);
			}
		});
	}
}
