package io.github.jcondedata.aliveworkplace.registry;

import io.github.jcondedata.aliveworkplace.blueprint.BlueprintItem;
import io.github.jcondedata.aliveworkplace.blueprint.StarterBlueprints;
import net.fabricmc.fabric.api.object.builder.v1.trade.TradeOfferHelper;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.trading.ItemCost;
import net.minecraft.world.item.trading.MerchantOffer;

/** Builders sell blueprints, so survival players can get plans without commands. */
public final class ModTrades {
	public static void init() {
		minerTrades();
		TradeOfferHelper.registerVillagerOffers(ModVillagers.BUILDER, 1, offers -> {
			offers.add((entity, random) -> new MerchantOffer(new ItemCost(Items.COBBLESTONE, 20), new ItemStack(Items.EMERALD), 16, 2, 0.05f));
			offers.add((entity, random) -> blueprint(StarterBlueprints.STARTER_COTTAGE, 6));
		});
		TradeOfferHelper.registerVillagerOffers(ModVillagers.BUILDER, 2, offers -> {
			offers.add((entity, random) -> new MerchantOffer(new ItemCost(Items.OAK_LOG, 8), new ItemStack(Items.EMERALD), 16, 10, 0.05f));
			offers.add((entity, random) -> blueprint(StarterBlueprints.MARKET_STALL, 8));
		});
		TradeOfferHelper.registerVillagerOffers(ModVillagers.BUILDER, 3, offers -> {
			offers.add((entity, random) -> new MerchantOffer(new ItemCost(Items.GLASS, 12), new ItemStack(Items.EMERALD), 12, 20, 0.05f));
			offers.add((entity, random) -> blueprint(StarterBlueprints.LOOKOUT_TOWER, 12));
		});
		TradeOfferHelper.registerVillagerOffers(ModVillagers.BUILDER, 4, offers -> {
			offers.add((entity, random) -> new MerchantOffer(new ItemCost(Items.EMERALD, 1), new ItemStack(Items.SCAFFOLDING, 12), 12, 15, 0.05f));
			offers.add((entity, random) -> blueprint(StarterBlueprints.HEALING_CENTER, 16));
		});
		TradeOfferHelper.registerVillagerOffers(ModVillagers.BUILDER, 5, offers -> {
			offers.add((entity, random) -> new MerchantOffer(new ItemCost(Items.EMERALD, 1), new ItemStack(Items.BRICKS, 16), 12, 30, 0.05f));
			offers.add((entity, random) -> blueprint(StarterBlueprints.SUPPLY_SHOP, 20));
		});
	}

	private static void minerTrades() {
		TradeOfferHelper.registerVillagerOffers(ModVillagers.MINER, 1, offers -> {
			offers.add((entity, random) -> new MerchantOffer(new ItemCost(Items.COAL, 15), new ItemStack(Items.EMERALD), 16, 2, 0.05f));
			offers.add((entity, random) -> new MerchantOffer(new ItemCost(Items.EMERALD, 1), new ItemStack(ModItems.QUARRY_MARKER), 12, 1, 0.05f));
		});
		TradeOfferHelper.registerVillagerOffers(ModVillagers.MINER, 2, offers -> {
			offers.add((entity, random) -> new MerchantOffer(new ItemCost(Items.RAW_COPPER, 10), new ItemStack(Items.EMERALD), 16, 10, 0.05f));
			offers.add((entity, random) -> new MerchantOffer(new ItemCost(Items.EMERALD, 1), new ItemStack(Items.TORCH, 16), 12, 5, 0.05f));
		});
		TradeOfferHelper.registerVillagerOffers(ModVillagers.MINER, 3, offers ->
			offers.add((entity, random) -> new MerchantOffer(new ItemCost(Items.RAW_IRON, 6), new ItemStack(Items.EMERALD), 12, 20, 0.05f)));
		TradeOfferHelper.registerVillagerOffers(ModVillagers.MINER, 4, offers ->
			offers.add((entity, random) -> new MerchantOffer(new ItemCost(Items.EMERALD, 6), new ItemStack(Items.IRON_PICKAXE), 3, 15, 0.05f)));
		TradeOfferHelper.registerVillagerOffers(ModVillagers.MINER, 5, offers ->
			offers.add((entity, random) -> new MerchantOffer(new ItemCost(Items.EMERALD, 18), new ItemStack(Items.DIAMOND_PICKAXE), 3, 30, 0.05f)));
	}

	private static MerchantOffer blueprint(StarterBlueprints.Entry entry, int emeralds) {
		return new MerchantOffer(new ItemCost(Items.EMERALD, emeralds), BlueprintItem.create(entry.id(), entry.size()), 3, 10, 0.05f);
	}

	private ModTrades() {
	}
}
