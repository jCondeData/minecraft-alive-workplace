package io.github.jcondedata.aliveworkplace.ui;

import io.github.jcondedata.aliveworkplace.registry.ModBlocks;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.trading.Merchant;

/**
 * The trades of the mod's own jobs (ROADMAP 44.2): vanilla's {@link net.minecraft.world.inventory.MerchantMenu} itself,
 * its slots, offers, selection and level packets unchanged, on its own menu type ({@code aliveworkplace:trades}) so the
 * client draws it in Copperline. Vanilla's professions keep {@code minecraft:merchant} and vanilla's screen.
 */
public class CopperTradeMenu extends net.minecraft.world.inventory.MerchantMenu {
	/** The client's copy (vanilla's client-side merchant fills in from the offers packet). */
	public CopperTradeMenu(int id, Inventory inventory) {
		super(id, inventory);
	}

	public CopperTradeMenu(int id, Inventory inventory, Merchant trader) {
		super(id, inventory, trader);
	}

	@Override
	public MenuType<?> getType() {
		return ModBlocks.TRADES_MENU;
	}
}
