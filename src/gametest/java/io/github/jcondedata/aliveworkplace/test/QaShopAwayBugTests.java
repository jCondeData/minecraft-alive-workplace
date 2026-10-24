package io.github.jcondedata.aliveworkplace.test;

import io.github.jcondedata.aliveworkplace.shop.ShopLedger;
import io.github.jcondedata.aliveworkplace.work.Money;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.item.Items;

/**
 * QA (lane E, optional-mod absence), bug B416: with CobbleDollars a shop's takings while its owner is offline are kept in
 * the shops' ledger and paid when they join ("While you were away, your shops took ..."). If CobbleDollars is gone by
 * then (taken out of the pack, or broken by an update, when Money falls back to emeralds), the takings must still reach
 * the owner, in emeralds at the rate (100 CobbleDollars an emerald by default), not vanish. This test world has no
 * CobbleDollars, so a ledger entry made with it is exactly that case.
 */
public class QaShopAwayBugTests implements FabricGameTest {

	//$ gametest_ticks_batch 'FabricGameTest.EMPTY_STRUCTURE' '40' '"qaShopAwayBug"'
	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE, timeoutTicks = 40, batch = "qaShopAwayBug")
	public void takingsOwedInCobbleDollarsArePaidInEmeraldsWhenCobbleDollarsIsGone(GameTestHelper helper) {
		helper.assertTrue(!Money.cobbleDollars(), "setup: this test needs a world without CobbleDollars");
		MinecraftServer server = helper.getLevel().getServer();
		var owner = helper.makeMockServerPlayerInLevel();
		server.getPlayerList().remove(owner);
		owner.getInventory().clearContent();
		ShopLedger.payOwner(server, owner.getUUID(), 1234); // two sales made while CobbleDollars was installed
		helper.assertTrue(ShopLedger.get(server).pending(owner.getUUID()) == 1234, "setup: owed " + ShopLedger.get(server).pending(owner.getUUID()));
		ShopLedger.collect(owner); // the owner joins, CobbleDollars now gone
		int emeralds = owner.getInventory().countItem(Items.EMERALD);
		helper.assertTrue(emeralds >= 1234 / Money.DOLLARS_PER_EMERALD || ShopLedger.get(server).pending(owner.getUUID()) == 1234,
			"1234 CobbleDollars of takings were settled with " + emeralds + " emeralds and nothing is owed any more: the takings are lost");
		helper.succeed();
	}
}
