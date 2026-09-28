package io.github.jcondedata.aliveworkplace.test;

import io.github.jcondedata.aliveworkplace.compat.rct.RctLevelCaps;
import io.github.jcondedata.aliveworkplace.work.Money;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;

/** Without the pack's optional mods (CobbleDollars, Radical Cobblemon Trainers), everything falls back quietly. */
public class OptionalModsGameTests implements FabricGameTest {
	/** No CobbleDollars: prices and prizes are in emeralds. */
	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
	public void moneyIsEmeraldsWithoutCobbleDollars(GameTestHelper helper) {
		ServerPlayer player = helper.makeMockServerPlayerInLevel();
		player.setGameMode(GameType.SURVIVAL);
		helper.assertFalse(Money.cobbleDollars(), "CobbleDollars isn't installed here");
		player.getInventory().add(new ItemStack(Items.EMERALD, 5));
		helper.assertTrue(Money.canAfford(player, 500, 5) && !Money.canAfford(player, 100, 6), "affording in emeralds");
		helper.assertFalse(Money.charge(player, 100, 6), "charged more emeralds than the player has");
		helper.assertTrue(Money.charge(player, 999_999, 3) && player.getInventory().countItem(Items.EMERALD) == 2, "charging in emeralds");
		Money.pay(player, 999_999, 4);
		helper.assertTrue(player.getInventory().countItem(Items.EMERALD) == 6, "paying in emeralds");
		helper.succeed();
	}

	/** No Radical Cobblemon Trainers: no level cap to match. */
	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
	public void noLevelCapWithoutRct(GameTestHelper helper) {
		helper.assertTrue(RctLevelCaps.levelCap(helper.makeMockServerPlayerInLevel()).isEmpty(), "a level cap without RCT");
		helper.succeed();
	}
}
