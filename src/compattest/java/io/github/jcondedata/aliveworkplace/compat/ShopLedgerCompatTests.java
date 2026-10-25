package io.github.jcondedata.aliveworkplace.compat;

import io.github.jcondedata.aliveworkplace.compat.cobbledollars.CobbleDollarsBank;
import io.github.jcondedata.aliveworkplace.shop.ShopLedger;
import io.github.jcondedata.aliveworkplace.work.Money;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.item.Items;

/**
 * B416's other half: with CobbleDollars installed, a shop's takings while its owner was away are still paid whole in
 * CobbleDollars when they join (no emeralds, nothing kept owed). Without it: {@code QaShopAwayBugTests}.
 */
public class ShopLedgerCompatTests implements FabricGameTest {

	//$ gametest_ticks_batch 'FabricGameTest.EMPTY_STRUCTURE' '40' '"shopLedgerCompat"'
	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE, timeoutTicks = 40, batch = "shopLedgerCompat")
	public void takingsWhileAwayArePaidWholeInCobbleDollars(GameTestHelper helper) {
		helper.assertTrue(Money.cobbleDollars(), "setup: this test needs CobbleDollars");
		MinecraftServer server = helper.getLevel().getServer();
		var owner = helper.makeMockServerPlayerInLevel();
		server.getPlayerList().remove(owner);
		owner.getInventory().clearContent();
		CobbleDollarsBank.take(owner, CobbleDollarsBank.balance(owner));
		ShopLedger.payOwner(server, owner.getUUID(), 700); // the owner is offline: owed
		ShopLedger.payOwner(server, owner.getUUID(), 534);
		helper.assertTrue(ShopLedger.get(server).pending(owner.getUUID()) == 1234, "setup: owed " + ShopLedger.get(server).pending(owner.getUUID()));
		ShopLedger.collect(owner);
		helper.assertTrue(CobbleDollarsBank.balance(owner) == 1234, "the owner got " + CobbleDollarsBank.balance(owner) + " CobbleDollars, not 1234");
		helper.assertTrue(ShopLedger.get(server).pending(owner.getUUID()) == 0, "still owed: " + ShopLedger.get(server).pending(owner.getUUID()));
		helper.assertTrue(owner.getInventory().countItem(Items.EMERALD) == 0, "emeralds paid as well: " + owner.getInventory().countItem(Items.EMERALD));
		helper.succeed();
	}
}
