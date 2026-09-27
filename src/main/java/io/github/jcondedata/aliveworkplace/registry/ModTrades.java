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
		lumberjackTrades();
		guardTrades();
		nurseTrades();
		TradeOfferHelper.registerVillagerOffers(net.minecraft.world.entity.npc.VillagerProfession.FARMER, 1, offers ->
			offers.add((entity, random) -> new MerchantOffer(new ItemCost(Items.EMERALD, 1), new ItemStack(ModItems.FIELD_MARKER), 12, 1, 0.05f)));
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

	private static void lumberjackTrades() {
		TradeOfferHelper.registerVillagerOffers(ModVillagers.LUMBERJACK, 1, offers -> {
			offers.add((entity, random) -> new MerchantOffer(new ItemCost(Items.STICK, 32), new ItemStack(Items.EMERALD), 16, 2, 0.05f));
			offers.add((entity, random) -> new MerchantOffer(new ItemCost(Items.EMERALD, 1), new ItemStack(Items.OAK_LOG, 8), 12, 1, 0.05f));
		});
		TradeOfferHelper.registerVillagerOffers(ModVillagers.LUMBERJACK, 2, offers -> {
			offers.add((entity, random) -> new MerchantOffer(new ItemCost(Items.APPLE, 6), new ItemStack(Items.EMERALD), 12, 10, 0.05f));
			offers.add((entity, random) -> new MerchantOffer(new ItemCost(Items.EMERALD, 1), new ItemStack(Items.SPRUCE_LOG, 8), 12, 5, 0.05f));
		});
		TradeOfferHelper.registerVillagerOffers(ModVillagers.LUMBERJACK, 3, offers ->
			offers.add((entity, random) -> new MerchantOffer(new ItemCost(Items.EMERALD, 2), new ItemStack(Items.DARK_OAK_SAPLING, 4), 12, 20, 0.05f)));
		TradeOfferHelper.registerVillagerOffers(ModVillagers.LUMBERJACK, 4, offers ->
			offers.add((entity, random) -> new MerchantOffer(new ItemCost(Items.EMERALD, 4), new ItemStack(Items.IRON_AXE), 3, 15, 0.05f)));
		TradeOfferHelper.registerVillagerOffers(ModVillagers.LUMBERJACK, 5, offers ->
			offers.add((entity, random) -> new MerchantOffer(new ItemCost(Items.EMERALD, 3), new ItemStack(Items.CHERRY_SAPLING, 2), 6, 30, 0.05f)));
	}

	private static void guardTrades() {
		TradeOfferHelper.registerVillagerOffers(ModVillagers.GUARD, 1, offers -> {
			offers.add((entity, random) -> new MerchantOffer(new ItemCost(Items.ROTTEN_FLESH, 32), new ItemStack(Items.EMERALD), 16, 2, 0.05f));
			offers.add((entity, random) -> new MerchantOffer(new ItemCost(Items.EMERALD, 3), new ItemStack(Items.IRON_SWORD), 3, 5, 0.05f));
		});
		TradeOfferHelper.registerVillagerOffers(ModVillagers.GUARD, 2, offers -> {
			offers.add((entity, random) -> new MerchantOffer(new ItemCost(Items.BONE, 16), new ItemStack(Items.EMERALD), 16, 10, 0.05f));
			offers.add((entity, random) -> new MerchantOffer(new ItemCost(Items.EMERALD, 4), new ItemStack(Items.SHIELD), 3, 10, 0.05f));
		});
		TradeOfferHelper.registerVillagerOffers(ModVillagers.GUARD, 3, offers ->
			offers.add((entity, random) -> new MerchantOffer(new ItemCost(Items.EMERALD, 6), new ItemStack(Items.IRON_CHESTPLATE), 3, 20, 0.05f)));
		TradeOfferHelper.registerVillagerOffers(ModVillagers.GUARD, 4, offers ->
			offers.add((entity, random) -> new MerchantOffer(new ItemCost(Items.EMERALD, 8), new ItemStack(Items.CROSSBOW), 3, 15, 0.05f)));
		TradeOfferHelper.registerVillagerOffers(ModVillagers.GUARD, 5, offers ->
			offers.add((entity, random) -> new MerchantOffer(new ItemCost(Items.EMERALD, 20), new ItemStack(Items.DIAMOND_SWORD), 2, 30, 0.05f)));
	}

	private static void nurseTrades() {
		TradeOfferHelper.registerVillagerOffers(ModVillagers.NURSE, 1, offers -> {
			offers.add((entity, random) -> new MerchantOffer(new ItemCost(Items.GLISTERING_MELON_SLICE, 4), new ItemStack(Items.EMERALD), 12, 2, 0.05f));
			offers.add((entity, random) -> new MerchantOffer(new ItemCost(Items.EMERALD, 3),
				net.minecraft.world.item.alchemy.PotionContents.createItemStack(Items.POTION, net.minecraft.world.item.alchemy.Potions.HEALING), 6, 5, 0.05f));
		});
		TradeOfferHelper.registerVillagerOffers(ModVillagers.NURSE, 2, offers ->
			offers.add((entity, random) -> new MerchantOffer(new ItemCost(Items.EMERALD, 4),
				net.minecraft.world.item.alchemy.PotionContents.createItemStack(Items.POTION, net.minecraft.world.item.alchemy.Potions.REGENERATION), 6, 10, 0.05f)));
		TradeOfferHelper.registerVillagerOffers(ModVillagers.NURSE, 3, offers ->
			offers.add((entity, random) -> new MerchantOffer(new ItemCost(Items.EMERALD, 2), new ItemStack(Items.HONEY_BOTTLE, 3), 12, 15, 0.05f)));
		TradeOfferHelper.registerVillagerOffers(ModVillagers.NURSE, 4, offers ->
			offers.add((entity, random) -> new MerchantOffer(new ItemCost(Items.EMERALD, 8), new ItemStack(Items.GOLDEN_APPLE), 4, 20, 0.05f)));
		TradeOfferHelper.registerVillagerOffers(ModVillagers.NURSE, 5, offers ->
			offers.add((entity, random) -> new MerchantOffer(new ItemCost(Items.EMERALD, 12), new ItemStack(Items.GOLDEN_CARROT, 8), 8, 30, 0.05f)));
	}

	private static MerchantOffer blueprint(StarterBlueprints.Entry entry, int emeralds) {
		return new MerchantOffer(new ItemCost(Items.EMERALD, emeralds), BlueprintItem.create(entry.id(), entry.size()), 3, 10, 0.05f);
	}

	private ModTrades() {
	}
}
