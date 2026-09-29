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
		tutorTrades();
		pokemonTraderTrades();
		orchardKeeperTrades();
		ballSmithTrades();
		porterTrades();
		carpenterTrades();
		chefTrades();
		fossilScientistTrades();
		TradeOfferHelper.registerVillagerOffers(ModVillagers.BARD, 1, offers -> {
			offers.add((entity, random) -> new MerchantOffer(new ItemCost(Items.EMERALD, 2), new ItemStack(Items.NOTE_BLOCK, 2), 12, 2, 0.05f));
			offers.add((entity, random) -> new MerchantOffer(new ItemCost(Items.STRING, 16), new ItemStack(Items.EMERALD), 16, 2, 0.05f));
		});
		TradeOfferHelper.registerVillagerOffers(ModVillagers.BARD, 2, offers ->
			offers.add((entity, random) -> new MerchantOffer(new ItemCost(Items.EMERALD, 1), new ItemStack(Items.GOAT_HORN), 4, 10, 0.05f)));
		TradeOfferHelper.registerVillagerOffers(ModVillagers.BARD, 3, offers ->
			offers.add((entity, random) -> new MerchantOffer(new ItemCost(Items.EMERALD, 12), new ItemStack(Items.MUSIC_DISC_CAT), 2, 20, 0.05f)));
		TradeOfferHelper.registerVillagerOffers(ModVillagers.BARD, 4, offers ->
			offers.add((entity, random) -> new MerchantOffer(new ItemCost(Items.EMERALD, 14), new ItemStack(Items.MUSIC_DISC_BLOCKS), 2, 20, 0.05f)));
		TradeOfferHelper.registerVillagerOffers(ModVillagers.BARD, 5, offers ->
			offers.add((entity, random) -> new MerchantOffer(new ItemCost(Items.EMERALD, 16), new ItemStack(Items.MUSIC_DISC_OTHERSIDE), 1, 30, 0.05f)));
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

	/** Porters deal in the everyday things a storehouse is full of. */
	private static void porterTrades() {
		TradeOfferHelper.registerVillagerOffers(ModVillagers.PORTER, 1, offers -> {
			offers.add((entity, random) -> new MerchantOffer(new ItemCost(Items.EMERALD, 1), new ItemStack(Items.CHEST, 2), 12, 2, 0.05f));
			offers.add((entity, random) -> blueprint(StarterBlueprints.STOREHOUSE, 6));
		});
		TradeOfferHelper.registerVillagerOffers(ModVillagers.PORTER, 2, offers -> {
			offers.add((entity, random) -> new MerchantOffer(new ItemCost(Items.EMERALD, 1), new ItemStack(Items.BARREL, 2), 12, 5, 0.05f));
			offers.add((entity, random) -> new MerchantOffer(new ItemCost(Items.LEATHER, 6), new ItemStack(Items.EMERALD), 12, 10, 0.05f));
		});
		TradeOfferHelper.registerVillagerOffers(ModVillagers.PORTER, 3, offers ->
			offers.add((entity, random) -> new MerchantOffer(new ItemCost(Items.EMERALD, 2), new ItemStack(Items.LEAD, 2), 6, 15, 0.05f)));
		TradeOfferHelper.registerVillagerOffers(ModVillagers.PORTER, 4, offers ->
			offers.add((entity, random) -> new MerchantOffer(new ItemCost(Items.EMERALD, 4), new ItemStack(Items.CHEST_MINECART), 4, 20, 0.05f)));
		TradeOfferHelper.registerVillagerOffers(ModVillagers.PORTER, 5, offers ->
			offers.add((entity, random) -> new MerchantOffer(new ItemCost(Items.EMERALD, 12), new ItemStack(Items.SHULKER_SHELL), 2, 30, 0.05f)));
	}

	/** Carpenters sell what they make and buy wood. */
	private static void carpenterTrades() {
		TradeOfferHelper.registerVillagerOffers(ModVillagers.CARPENTER, 1, offers -> {
			offers.add((entity, random) -> new MerchantOffer(new ItemCost(Items.OAK_LOG, 16), new ItemStack(Items.EMERALD), 16, 2, 0.05f));
			offers.add((entity, random) -> new MerchantOffer(new ItemCost(Items.EMERALD, 1), new ItemStack(Items.OAK_STAIRS, 8), 12, 1, 0.05f));
		});
		TradeOfferHelper.registerVillagerOffers(ModVillagers.CARPENTER, 2, offers -> {
			offers.add((entity, random) -> new MerchantOffer(new ItemCost(Items.SPRUCE_LOG, 16), new ItemStack(Items.EMERALD), 16, 10, 0.05f));
			offers.add((entity, random) -> new MerchantOffer(new ItemCost(Items.EMERALD, 1), new ItemStack(Items.SPRUCE_DOOR, 2), 12, 5, 0.05f));
		});
		TradeOfferHelper.registerVillagerOffers(ModVillagers.CARPENTER, 3, offers ->
			offers.add((entity, random) -> new MerchantOffer(new ItemCost(Items.EMERALD, 2), new ItemStack(Items.BOOKSHELF, 1), 12, 15, 0.05f)));
		TradeOfferHelper.registerVillagerOffers(ModVillagers.CARPENTER, 4, offers ->
			offers.add((entity, random) -> new MerchantOffer(new ItemCost(Items.EMERALD, 3), new ItemStack(Items.SCAFFOLDING, 16), 12, 20, 0.05f)));
		TradeOfferHelper.registerVillagerOffers(ModVillagers.CARPENTER, 5, offers ->
			offers.add((entity, random) -> new MerchantOffer(new ItemCost(Items.EMERALD, 6), new ItemStack(Items.CHISELED_BOOKSHELF, 1), 6, 30, 0.05f)));
	}

	/** Chefs buy the village's produce and sell what they cook. */
	private static void chefTrades() {
		TradeOfferHelper.registerVillagerOffers(ModVillagers.CHEF, 1, offers -> {
			offers.add((entity, random) -> new MerchantOffer(new ItemCost(Items.POTATO, 26), new ItemStack(Items.EMERALD), 16, 2, 0.05f));
			offers.add((entity, random) -> new MerchantOffer(new ItemCost(Items.EMERALD, 1), new ItemStack(Items.BREAD, 6), 16, 1, 0.05f));
		});
		TradeOfferHelper.registerVillagerOffers(ModVillagers.CHEF, 2, offers -> {
			offers.add((entity, random) -> new MerchantOffer(new ItemCost(Items.SUGAR, 16), new ItemStack(Items.EMERALD), 16, 10, 0.05f));
			offers.add((entity, random) -> new MerchantOffer(new ItemCost(Items.EMERALD, 1), new ItemStack(Items.PUMPKIN_PIE, 4), 12, 5, 0.05f));
		});
		TradeOfferHelper.registerVillagerOffers(ModVillagers.CHEF, 3, offers ->
			offers.add((entity, random) -> new MerchantOffer(new ItemCost(Items.EMERALD, 1), new ItemStack(Items.COOKED_BEEF, 5), 12, 15, 0.05f)));
		TradeOfferHelper.registerVillagerOffers(ModVillagers.CHEF, 4, offers ->
			offers.add((entity, random) -> new MerchantOffer(new ItemCost(Items.EMERALD, 2), new ItemStack(Items.CAKE), 6, 20, 0.05f)));
		TradeOfferHelper.registerVillagerOffers(ModVillagers.CHEF, 5, offers ->
			offers.add((entity, random) -> new MerchantOffer(new ItemCost(Items.EMERALD, 3), new ItemStack(Items.GOLDEN_CARROT, 3), 12, 30, 0.05f)));
	}

	/** Fossil Scientists deal in what digging for fossils takes. */
	private static void fossilScientistTrades() {
		TradeOfferHelper.registerVillagerOffers(ModVillagers.FOSSIL_SCIENTIST, 1, offers -> {
			offers.add((entity, random) -> new MerchantOffer(new ItemCost(Items.BONE, 12), new ItemStack(Items.EMERALD), 16, 2, 0.05f));
			offers.add((entity, random) -> new MerchantOffer(new ItemCost(Items.EMERALD, 3), new ItemStack(Items.BRUSH), 6, 1, 0.05f));
		});
		TradeOfferHelper.registerVillagerOffers(ModVillagers.FOSSIL_SCIENTIST, 2, offers ->
			offers.add((entity, random) -> new MerchantOffer(new ItemCost(Items.EMERALD, 2), new ItemStack(Items.SUSPICIOUS_SAND), 6, 10, 0.05f)));
		TradeOfferHelper.registerVillagerOffers(ModVillagers.FOSSIL_SCIENTIST, 3, offers -> {
			offers.add((entity, random) -> new MerchantOffer(new ItemCost(Items.EMERALD, 2), new ItemStack(Items.SUSPICIOUS_GRAVEL), 6, 15, 0.05f));
			offers.add((entity, random) -> blueprint(StarterBlueprints.RESEARCH_LAB, 12));
		});
		TradeOfferHelper.registerVillagerOffers(ModVillagers.FOSSIL_SCIENTIST, 4, offers ->
			offers.add((entity, random) -> new MerchantOffer(new ItemCost(Items.AMETHYST_SHARD, 8), new ItemStack(Items.EMERALD), 12, 20, 0.05f)));
		TradeOfferHelper.registerVillagerOffers(ModVillagers.FOSSIL_SCIENTIST, 5, offers ->
			offers.add((entity, random) -> new MerchantOffer(new ItemCost(Items.EMERALD, 10), new ItemStack(Items.SNIFFER_EGG), 2, 30, 0.05f)));
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

	/** Tutors buy paper and books (emeralds for lessons) and sell a few things for the study. */
	private static void tutorTrades() {
		TradeOfferHelper.registerVillagerOffers(ModVillagers.TUTOR, 1, offers -> {
			offers.add((entity, random) -> new MerchantOffer(new ItemCost(Items.PAPER, 24), new ItemStack(Items.EMERALD), 16, 2, 0.05f));
			offers.add((entity, random) -> new MerchantOffer(new ItemCost(Items.EMERALD, 1), new ItemStack(Items.BOOK), 12, 1, 0.05f));
		});
		TradeOfferHelper.registerVillagerOffers(ModVillagers.TUTOR, 2, offers ->
			offers.add((entity, random) -> new MerchantOffer(new ItemCost(Items.BOOK, 4), new ItemStack(Items.EMERALD), 12, 10, 0.05f)));
		TradeOfferHelper.registerVillagerOffers(ModVillagers.TUTOR, 3, offers ->
			offers.add((entity, random) -> new MerchantOffer(new ItemCost(Items.EMERALD, 3), new ItemStack(Items.EXPERIENCE_BOTTLE), 12, 20, 0.05f)));
		TradeOfferHelper.registerVillagerOffers(ModVillagers.TUTOR, 4, offers ->
			offers.add((entity, random) -> new MerchantOffer(new ItemCost(Items.WRITABLE_BOOK, 2), new ItemStack(Items.EMERALD), 12, 30, 0.05f)));
		TradeOfferHelper.registerVillagerOffers(ModVillagers.TUTOR, 5, offers ->
			offers.add((entity, random) -> new MerchantOffer(new ItemCost(Items.EMERALD, 20), new ItemStack(Items.NAME_TAG), 4, 30, 0.05f)));
	}

	/** Pokémon Traders sell Cobblemon's balls and candies (nothing when Cobblemon isn't installed). */
	private static void pokemonTraderTrades() {
		TradeOfferHelper.registerVillagerOffers(ModVillagers.POKEMON_TRADER, 1, offers -> {
			offers.add((entity, random) -> cobblemon("poke_ball", 1, 4, 16, 1));
			offers.add((entity, random) -> net.minecraft.core.registries.BuiltInRegistries.ITEM
				.getOptional(net.minecraft.resources.ResourceLocation.fromNamespaceAndPath("cobblemon", "red_apricorn"))
				.map(item -> new MerchantOffer(new ItemCost(item, 8), new ItemStack(Items.EMERALD), 16, 2, 0.05f)).orElse(null));
		});
		TradeOfferHelper.registerVillagerOffers(ModVillagers.POKEMON_TRADER, 2, offers -> {
			offers.add((entity, random) -> cobblemon("great_ball", 1, 2, 12, 5));
			offers.add((entity, random) -> cobblemon("exp_candy_m", 2, 1, 12, 10));
		});
		TradeOfferHelper.registerVillagerOffers(ModVillagers.POKEMON_TRADER, 3, offers ->
			offers.add((entity, random) -> cobblemon("ultra_ball", 2, 1, 12, 15)));
		TradeOfferHelper.registerVillagerOffers(ModVillagers.POKEMON_TRADER, 4, offers ->
			offers.add((entity, random) -> cobblemon("exp_candy_l", 6, 1, 8, 20)));
		TradeOfferHelper.registerVillagerOffers(ModVillagers.POKEMON_TRADER, 5, offers -> {
			offers.add((entity, random) -> cobblemon("rare_candy", 12, 1, 4, 30));
			offers.add((entity, random) -> cobblemon("ability_capsule", 24, 1, 2, 30));
		});
	}

	/** Orchard Keepers buy and sell fruit; with Cobblemon, apricorns and berries too. */
	private static void orchardKeeperTrades() {
		TradeOfferHelper.registerVillagerOffers(ModVillagers.ORCHARD_KEEPER, 1, offers -> {
			offers.add((entity, random) -> new MerchantOffer(new ItemCost(Items.SWEET_BERRIES, 22), new ItemStack(Items.EMERALD), 16, 2, 0.05f));
			offers.add((entity, random) -> new MerchantOffer(new ItemCost(Items.EMERALD, 1), new ItemStack(Items.APPLE, 5), 12, 1, 0.05f));
		});
		TradeOfferHelper.registerVillagerOffers(ModVillagers.ORCHARD_KEEPER, 2, offers -> {
			offers.add((entity, random) -> new MerchantOffer(new ItemCost(Items.COCOA_BEANS, 12), new ItemStack(Items.EMERALD), 16, 10, 0.05f));
			offers.add((entity, random) -> new MerchantOffer(new ItemCost(Items.EMERALD, 1), new ItemStack(Items.GLOW_BERRIES, 6), 12, 5, 0.05f));
		});
		TradeOfferHelper.registerVillagerOffers(ModVillagers.ORCHARD_KEEPER, 3, offers -> {
			offers.add((entity, random) -> cobblemon(APRICORNS[random.nextInt(APRICORNS.length)] + "_apricorn", 1, 4, 12, 15));
			offers.add((entity, random) -> new MerchantOffer(new ItemCost(Items.GLOW_BERRIES, 16), new ItemStack(Items.EMERALD), 12, 15, 0.05f));
		});
		TradeOfferHelper.registerVillagerOffers(ModVillagers.ORCHARD_KEEPER, 4, offers -> {
			offers.add((entity, random) -> cobblemon(BERRIES[random.nextInt(BERRIES.length)] + "_berry", 3, 2, 8, 20));
			offers.add((entity, random) -> blueprint(StarterBlueprints.BERRY_FARM, 10));
		});
		TradeOfferHelper.registerVillagerOffers(ModVillagers.ORCHARD_KEEPER, 5, offers -> {
			offers.add((entity, random) -> new MerchantOffer(new ItemCost(Items.EMERALD, 8), new ItemStack(Items.GOLDEN_APPLE), 4, 30, 0.05f));
			offers.add((entity, random) -> cobblemon(APRICORNS[random.nextInt(APRICORNS.length)] + "_apricorn_seed", 6, 1, 4, 30));
		});
	}

	/** Ball Smiths buy apricorns and copper, and sell balls (nothing but the copper without Cobblemon). */
	private static void ballSmithTrades() {
		TradeOfferHelper.registerVillagerOffers(ModVillagers.BALL_SMITH, 1, offers -> {
			offers.add((entity, random) -> new MerchantOffer(new ItemCost(Items.COPPER_INGOT, 8), new ItemStack(Items.EMERALD), 16, 2, 0.05f));
			offers.add((entity, random) -> cobblemon("poke_ball", 1, 4, 16, 1));
		});
		TradeOfferHelper.registerVillagerOffers(ModVillagers.BALL_SMITH, 2, offers -> {
			offers.add((entity, random) -> net.minecraft.core.registries.BuiltInRegistries.ITEM
				.getOptional(net.minecraft.resources.ResourceLocation.fromNamespaceAndPath("cobblemon", APRICORNS[random.nextInt(APRICORNS.length)] + "_apricorn"))
				.map(item -> new MerchantOffer(new ItemCost(item, 12), new ItemStack(Items.EMERALD), 16, 10, 0.05f)).orElse(null));
			offers.add((entity, random) -> cobblemon("great_ball", 2, 4, 12, 5));
		});
		TradeOfferHelper.registerVillagerOffers(ModVillagers.BALL_SMITH, 3, offers ->
			offers.add((entity, random) -> cobblemon("ultra_ball", 4, 4, 12, 15)));
		TradeOfferHelper.registerVillagerOffers(ModVillagers.BALL_SMITH, 4, offers -> {
			String[] special = {"quick_ball", "dusk_ball", "timer_ball", "net_ball", "dive_ball", "heavy_ball", "level_ball", "lure_ball", "moon_ball"};
			offers.add((entity, random) -> cobblemon(special[random.nextInt(special.length)], 5, 2, 8, 20));
		});
		TradeOfferHelper.registerVillagerOffers(ModVillagers.BALL_SMITH, 5, offers ->
			offers.add((entity, random) -> cobblemon("premier_ball", 1, 8, 8, 30)));
	}

	private static final String[] APRICORNS = {"red", "yellow", "green", "blue", "pink", "black", "white"};
	private static final String[] BERRIES = {"oran", "sitrus", "lum", "leppa", "pecha", "cheri", "chesto", "rawst", "aspear", "persim"};

	/** Emeralds for a Cobblemon item, or no offer when Cobblemon (or that item) isn't there. */
	private static MerchantOffer cobblemon(String item, int emeralds, int count, int uses, int xp) {
		return net.minecraft.core.registries.BuiltInRegistries.ITEM
			.getOptional(net.minecraft.resources.ResourceLocation.fromNamespaceAndPath("cobblemon", item))
			.map(i -> new MerchantOffer(new ItemCost(Items.EMERALD, emeralds), new ItemStack(i, count), uses, xp, 0.05f))
			.orElse(null);
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
