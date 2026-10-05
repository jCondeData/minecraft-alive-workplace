package io.github.jcondedata.aliveworkplace.registry;

import io.github.jcondedata.aliveworkplace.platform.Platform;
import io.github.jcondedata.aliveworkplace.blueprint.BlueprintItem;
import io.github.jcondedata.aliveworkplace.blueprint.StarterBlueprints;
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
		beekeeperTrades();
		rancherTrades();
		teacherTrades();
		innkeeperTrades();
		undertakerTrades();
		scholarTrades();
		sifterTrades();
		tinkererTrades();
		netherworkerTrades();
		composterTrades();
		ballSmithTrades();
		porterTrades();
		carpenterTrades();
		chefTrades();
		fossilScientistTrades();
		berryBreederTrades();
		stewardTrades();
		campCookTrades();
		habitatKeeperTrades();
		gemGrowerTrades();
		daycareKeeperTrades();
		trainerLeaderTrades();
		Platform.get().addTrades(ModVillagers.BARD, 1, offers -> {
			offers.add((entity, random) -> new MerchantOffer(new ItemCost(Items.EMERALD, 2), new ItemStack(Items.NOTE_BLOCK, 2), 12, 2, 0.05f));
			offers.add((entity, random) -> new MerchantOffer(new ItemCost(Items.STRING, 16), new ItemStack(Items.EMERALD), 16, 2, 0.05f));
		});
		Platform.get().addTrades(ModVillagers.BARD, 2, offers ->
			offers.add((entity, random) -> new MerchantOffer(new ItemCost(Items.EMERALD, 1), new ItemStack(Items.GOAT_HORN), 4, 10, 0.05f)));
		Platform.get().addTrades(ModVillagers.BARD, 3, offers ->
			offers.add((entity, random) -> new MerchantOffer(new ItemCost(Items.EMERALD, 12), new ItemStack(Items.MUSIC_DISC_CAT), 2, 20, 0.05f)));
		Platform.get().addTrades(ModVillagers.BARD, 4, offers ->
			offers.add((entity, random) -> new MerchantOffer(new ItemCost(Items.EMERALD, 14), new ItemStack(Items.MUSIC_DISC_BLOCKS), 2, 20, 0.05f)));
		Platform.get().addTrades(ModVillagers.BARD, 5, offers ->
			offers.add((entity, random) -> new MerchantOffer(new ItemCost(Items.EMERALD, 16), new ItemStack(Items.MUSIC_DISC_OTHERSIDE), 1, 30, 0.05f)));
		Platform.get().addTrades(net.minecraft.world.entity.npc.VillagerProfession.FARMER, 1, offers ->
			offers.add((entity, random) -> new MerchantOffer(new ItemCost(Items.EMERALD, 1), new ItemStack(ModItems.FIELD_MARKER), 12, 1, 0.05f)));
		Platform.get().addTrades(ModVillagers.BUILDER, 1, offers -> {
			offers.add((entity, random) -> new MerchantOffer(new ItemCost(Items.COBBLESTONE, 20), new ItemStack(Items.EMERALD), 16, 2, 0.05f));
			offers.add((entity, random) -> blueprint(StarterBlueprints.STARTER_COTTAGE, 6));
		});
		Platform.get().addTrades(ModVillagers.BUILDER, 2, offers -> {
			offers.add((entity, random) -> new MerchantOffer(new ItemCost(Items.OAK_LOG, 8), new ItemStack(Items.EMERALD), 16, 10, 0.05f));
			offers.add((entity, random) -> blueprint(StarterBlueprints.MARKET_STALL, 8));
		});
		Platform.get().addTrades(ModVillagers.BUILDER, 3, offers -> {
			offers.add((entity, random) -> new MerchantOffer(new ItemCost(Items.GLASS, 12), new ItemStack(Items.EMERALD), 12, 20, 0.05f));
			offers.add((entity, random) -> blueprint(StarterBlueprints.LOOKOUT_TOWER, 12));
		});
		Platform.get().addTrades(ModVillagers.BUILDER, 4, offers -> {
			offers.add((entity, random) -> new MerchantOffer(new ItemCost(Items.EMERALD, 1), new ItemStack(Items.SCAFFOLDING, 12), 12, 15, 0.05f));
			offers.add((entity, random) -> blueprint(StarterBlueprints.HEALING_CENTER, 16));
		});
		Platform.get().addTrades(ModVillagers.BUILDER, 5, offers -> {
			offers.add((entity, random) -> new MerchantOffer(new ItemCost(Items.EMERALD, 1), new ItemStack(Items.BRICKS, 16), 12, 30, 0.05f));
			offers.add((entity, random) -> blueprint(StarterBlueprints.SUPPLY_SHOP, 20));
		});
	}

	/** Porters deal in the everyday things a storehouse is full of. */
	private static void porterTrades() {
		Platform.get().addTrades(ModVillagers.PORTER, 1, offers -> {
			offers.add((entity, random) -> new MerchantOffer(new ItemCost(Items.EMERALD, 1), new ItemStack(Items.CHEST, 2), 12, 2, 0.05f));
			offers.add((entity, random) -> blueprint(StarterBlueprints.STOREHOUSE, 6));
		});
		Platform.get().addTrades(ModVillagers.PORTER, 2, offers -> {
			offers.add((entity, random) -> new MerchantOffer(new ItemCost(Items.EMERALD, 1), new ItemStack(Items.BARREL, 2), 12, 5, 0.05f));
			offers.add((entity, random) -> new MerchantOffer(new ItemCost(Items.LEATHER, 6), new ItemStack(Items.EMERALD), 12, 10, 0.05f));
		});
		Platform.get().addTrades(ModVillagers.PORTER, 3, offers ->
			offers.add((entity, random) -> new MerchantOffer(new ItemCost(Items.EMERALD, 2), new ItemStack(Items.LEAD, 2), 6, 15, 0.05f)));
		Platform.get().addTrades(ModVillagers.PORTER, 4, offers ->
			offers.add((entity, random) -> new MerchantOffer(new ItemCost(Items.EMERALD, 4), new ItemStack(Items.CHEST_MINECART), 4, 20, 0.05f)));
		Platform.get().addTrades(ModVillagers.PORTER, 5, offers ->
			offers.add((entity, random) -> new MerchantOffer(new ItemCost(Items.EMERALD, 12), new ItemStack(Items.SHULKER_SHELL), 2, 30, 0.05f)));
	}

	/** Carpenters sell what they make and buy wood. */
	private static void carpenterTrades() {
		Platform.get().addTrades(ModVillagers.CARPENTER, 1, offers -> {
			offers.add((entity, random) -> new MerchantOffer(new ItemCost(Items.OAK_LOG, 16), new ItemStack(Items.EMERALD), 16, 2, 0.05f));
			offers.add((entity, random) -> new MerchantOffer(new ItemCost(Items.EMERALD, 1), new ItemStack(Items.OAK_STAIRS, 8), 12, 1, 0.05f));
		});
		Platform.get().addTrades(ModVillagers.CARPENTER, 2, offers -> {
			offers.add((entity, random) -> new MerchantOffer(new ItemCost(Items.SPRUCE_LOG, 16), new ItemStack(Items.EMERALD), 16, 10, 0.05f));
			offers.add((entity, random) -> new MerchantOffer(new ItemCost(Items.EMERALD, 1), new ItemStack(Items.SPRUCE_DOOR, 2), 12, 5, 0.05f));
		});
		Platform.get().addTrades(ModVillagers.CARPENTER, 3, offers ->
			offers.add((entity, random) -> new MerchantOffer(new ItemCost(Items.EMERALD, 2), new ItemStack(Items.BOOKSHELF, 1), 12, 15, 0.05f)));
		Platform.get().addTrades(ModVillagers.CARPENTER, 4, offers ->
			offers.add((entity, random) -> new MerchantOffer(new ItemCost(Items.EMERALD, 3), new ItemStack(Items.SCAFFOLDING, 16), 12, 20, 0.05f)));
		Platform.get().addTrades(ModVillagers.CARPENTER, 5, offers ->
			offers.add((entity, random) -> new MerchantOffer(new ItemCost(Items.EMERALD, 6), new ItemStack(Items.CHISELED_BOOKSHELF, 1), 6, 30, 0.05f)));
	}

	/** Chefs buy the village's produce and sell what they cook. */
	private static void chefTrades() {
		Platform.get().addTrades(ModVillagers.CHEF, 1, offers -> {
			offers.add((entity, random) -> new MerchantOffer(new ItemCost(Items.POTATO, 26), new ItemStack(Items.EMERALD), 16, 2, 0.05f));
			offers.add((entity, random) -> new MerchantOffer(new ItemCost(Items.EMERALD, 1), new ItemStack(Items.BREAD, 6), 16, 1, 0.05f));
		});
		Platform.get().addTrades(ModVillagers.CHEF, 2, offers -> {
			offers.add((entity, random) -> new MerchantOffer(new ItemCost(Items.SUGAR, 16), new ItemStack(Items.EMERALD), 16, 10, 0.05f));
			offers.add((entity, random) -> new MerchantOffer(new ItemCost(Items.EMERALD, 1), new ItemStack(Items.PUMPKIN_PIE, 4), 12, 5, 0.05f));
		});
		Platform.get().addTrades(ModVillagers.CHEF, 3, offers ->
			offers.add((entity, random) -> new MerchantOffer(new ItemCost(Items.EMERALD, 1), new ItemStack(Items.COOKED_BEEF, 5), 12, 15, 0.05f)));
		Platform.get().addTrades(ModVillagers.CHEF, 4, offers ->
			offers.add((entity, random) -> new MerchantOffer(new ItemCost(Items.EMERALD, 2), new ItemStack(Items.CAKE), 6, 20, 0.05f)));
		Platform.get().addTrades(ModVillagers.CHEF, 5, offers ->
			offers.add((entity, random) -> new MerchantOffer(new ItemCost(Items.EMERALD, 3), new ItemStack(Items.GOLDEN_CARROT, 3), 12, 30, 0.05f)));
	}

	/** Fossil Scientists deal in what digging for fossils takes. */
	private static void fossilScientistTrades() {
		Platform.get().addTrades(ModVillagers.FOSSIL_SCIENTIST, 1, offers -> {
			offers.add((entity, random) -> new MerchantOffer(new ItemCost(Items.BONE, 12), new ItemStack(Items.EMERALD), 16, 2, 0.05f));
			offers.add((entity, random) -> new MerchantOffer(new ItemCost(Items.EMERALD, 3), new ItemStack(Items.BRUSH), 6, 1, 0.05f));
		});
		Platform.get().addTrades(ModVillagers.FOSSIL_SCIENTIST, 2, offers ->
			offers.add((entity, random) -> new MerchantOffer(new ItemCost(Items.EMERALD, 2), new ItemStack(Items.SUSPICIOUS_SAND), 6, 10, 0.05f)));
		Platform.get().addTrades(ModVillagers.FOSSIL_SCIENTIST, 3, offers -> {
			offers.add((entity, random) -> new MerchantOffer(new ItemCost(Items.EMERALD, 2), new ItemStack(Items.SUSPICIOUS_GRAVEL), 6, 15, 0.05f));
			offers.add((entity, random) -> blueprint(StarterBlueprints.RESEARCH_LAB, 12));
		});
		Platform.get().addTrades(ModVillagers.FOSSIL_SCIENTIST, 4, offers ->
			offers.add((entity, random) -> new MerchantOffer(new ItemCost(Items.AMETHYST_SHARD, 8), new ItemStack(Items.EMERALD), 12, 20, 0.05f)));
		Platform.get().addTrades(ModVillagers.FOSSIL_SCIENTIST, 5, offers ->
			offers.add((entity, random) -> new MerchantOffer(new ItemCost(Items.EMERALD, 10), new ItemStack(Items.SNIFFER_EGG), 2, 30, 0.05f)));
	}

	private static void minerTrades() {
		Platform.get().addTrades(ModVillagers.MINER, 1, offers -> {
			offers.add((entity, random) -> new MerchantOffer(new ItemCost(Items.COAL, 15), new ItemStack(Items.EMERALD), 16, 2, 0.05f));
			offers.add((entity, random) -> new MerchantOffer(new ItemCost(Items.EMERALD, 1), new ItemStack(ModItems.QUARRY_MARKER), 12, 1, 0.05f));
		});
		Platform.get().addTrades(ModVillagers.MINER, 2, offers -> {
			offers.add((entity, random) -> new MerchantOffer(new ItemCost(Items.RAW_COPPER, 10), new ItemStack(Items.EMERALD), 16, 10, 0.05f));
			offers.add((entity, random) -> new MerchantOffer(new ItemCost(Items.EMERALD, 1), new ItemStack(Items.TORCH, 16), 12, 5, 0.05f));
		});
		Platform.get().addTrades(ModVillagers.MINER, 3, offers ->
			offers.add((entity, random) -> new MerchantOffer(new ItemCost(Items.RAW_IRON, 6), new ItemStack(Items.EMERALD), 12, 20, 0.05f)));
		Platform.get().addTrades(ModVillagers.MINER, 4, offers ->
			offers.add((entity, random) -> new MerchantOffer(new ItemCost(Items.EMERALD, 6), new ItemStack(Items.IRON_PICKAXE), 3, 15, 0.05f)));
		Platform.get().addTrades(ModVillagers.MINER, 5, offers ->
			offers.add((entity, random) -> new MerchantOffer(new ItemCost(Items.EMERALD, 18), new ItemStack(Items.DIAMOND_PICKAXE), 3, 30, 0.05f)));
	}

	private static void lumberjackTrades() {
		Platform.get().addTrades(ModVillagers.LUMBERJACK, 1, offers -> {
			offers.add((entity, random) -> new MerchantOffer(new ItemCost(Items.STICK, 32), new ItemStack(Items.EMERALD), 16, 2, 0.05f));
			offers.add((entity, random) -> new MerchantOffer(new ItemCost(Items.EMERALD, 1), new ItemStack(Items.OAK_LOG, 8), 12, 1, 0.05f));
		});
		Platform.get().addTrades(ModVillagers.LUMBERJACK, 2, offers -> {
			offers.add((entity, random) -> new MerchantOffer(new ItemCost(Items.APPLE, 6), new ItemStack(Items.EMERALD), 12, 10, 0.05f));
			offers.add((entity, random) -> new MerchantOffer(new ItemCost(Items.EMERALD, 1), new ItemStack(Items.SPRUCE_LOG, 8), 12, 5, 0.05f));
		});
		Platform.get().addTrades(ModVillagers.LUMBERJACK, 3, offers ->
			offers.add((entity, random) -> new MerchantOffer(new ItemCost(Items.EMERALD, 2), new ItemStack(Items.DARK_OAK_SAPLING, 4), 12, 20, 0.05f)));
		Platform.get().addTrades(ModVillagers.LUMBERJACK, 4, offers ->
			offers.add((entity, random) -> new MerchantOffer(new ItemCost(Items.EMERALD, 4), new ItemStack(Items.IRON_AXE), 3, 15, 0.05f)));
		Platform.get().addTrades(ModVillagers.LUMBERJACK, 5, offers ->
			offers.add((entity, random) -> new MerchantOffer(new ItemCost(Items.EMERALD, 3), new ItemStack(Items.CHERRY_SAPLING, 2), 6, 30, 0.05f)));
	}

	private static void guardTrades() {
		Platform.get().addTrades(ModVillagers.GUARD, 1, offers -> {
			offers.add((entity, random) -> new MerchantOffer(new ItemCost(Items.ROTTEN_FLESH, 32), new ItemStack(Items.EMERALD), 16, 2, 0.05f));
			offers.add((entity, random) -> new MerchantOffer(new ItemCost(Items.EMERALD, 3), new ItemStack(Items.IRON_SWORD), 3, 5, 0.05f));
		});
		Platform.get().addTrades(ModVillagers.GUARD, 2, offers -> {
			offers.add((entity, random) -> new MerchantOffer(new ItemCost(Items.BONE, 16), new ItemStack(Items.EMERALD), 16, 10, 0.05f));
			offers.add((entity, random) -> new MerchantOffer(new ItemCost(Items.EMERALD, 4), new ItemStack(Items.SHIELD), 3, 10, 0.05f));
		});
		Platform.get().addTrades(ModVillagers.GUARD, 3, offers ->
			offers.add((entity, random) -> new MerchantOffer(new ItemCost(Items.EMERALD, 6), new ItemStack(Items.IRON_CHESTPLATE), 3, 20, 0.05f)));
		Platform.get().addTrades(ModVillagers.GUARD, 4, offers ->
			offers.add((entity, random) -> new MerchantOffer(new ItemCost(Items.EMERALD, 8), new ItemStack(Items.CROSSBOW), 3, 15, 0.05f)));
		Platform.get().addTrades(ModVillagers.GUARD, 5, offers ->
			offers.add((entity, random) -> new MerchantOffer(new ItemCost(Items.EMERALD, 20), new ItemStack(Items.DIAMOND_SWORD), 2, 30, 0.05f)));
	}

	/** Tutors buy paper and books (emeralds for lessons) and sell a few things for the study. */
	private static void tutorTrades() {
		Platform.get().addTrades(ModVillagers.TUTOR, 1, offers -> {
			offers.add((entity, random) -> new MerchantOffer(new ItemCost(Items.PAPER, 24), new ItemStack(Items.EMERALD), 16, 2, 0.05f));
			offers.add((entity, random) -> new MerchantOffer(new ItemCost(Items.EMERALD, 1), new ItemStack(Items.BOOK), 12, 1, 0.05f));
		});
		Platform.get().addTrades(ModVillagers.TUTOR, 2, offers ->
			offers.add((entity, random) -> new MerchantOffer(new ItemCost(Items.BOOK, 4), new ItemStack(Items.EMERALD), 12, 10, 0.05f)));
		Platform.get().addTrades(ModVillagers.TUTOR, 3, offers ->
			offers.add((entity, random) -> new MerchantOffer(new ItemCost(Items.EMERALD, 3), new ItemStack(Items.EXPERIENCE_BOTTLE), 12, 20, 0.05f)));
		Platform.get().addTrades(ModVillagers.TUTOR, 4, offers ->
			offers.add((entity, random) -> new MerchantOffer(new ItemCost(Items.WRITABLE_BOOK, 2), new ItemStack(Items.EMERALD), 12, 30, 0.05f)));
		Platform.get().addTrades(ModVillagers.TUTOR, 5, offers ->
			offers.add((entity, random) -> new MerchantOffer(new ItemCost(Items.EMERALD, 20), new ItemStack(Items.NAME_TAG), 4, 30, 0.05f)));
	}

	/** Pokémon Traders sell Cobblemon's balls and candies (nothing when Cobblemon isn't installed). */
	private static void pokemonTraderTrades() {
		Platform.get().addTrades(ModVillagers.POKEMON_TRADER, 1, offers -> {
			offers.add((entity, random) -> cobblemon("poke_ball", 1, 4, 16, 1));
			offers.add((entity, random) -> net.minecraft.core.registries.BuiltInRegistries.ITEM
				.getOptional(net.minecraft.resources.ResourceLocation.fromNamespaceAndPath("cobblemon", "red_apricorn"))
				.map(item -> new MerchantOffer(new ItemCost(item, 8), new ItemStack(Items.EMERALD), 16, 2, 0.05f)).orElse(null));
		});
		Platform.get().addTrades(ModVillagers.POKEMON_TRADER, 2, offers -> {
			offers.add((entity, random) -> cobblemon("great_ball", 1, 2, 12, 5));
			offers.add((entity, random) -> cobblemon("exp_candy_m", 2, 1, 12, 10));
		});
		Platform.get().addTrades(ModVillagers.POKEMON_TRADER, 3, offers ->
			offers.add((entity, random) -> cobblemon("ultra_ball", 2, 1, 12, 15)));
		Platform.get().addTrades(ModVillagers.POKEMON_TRADER, 4, offers ->
			offers.add((entity, random) -> cobblemon("exp_candy_l", 6, 1, 8, 20)));
		Platform.get().addTrades(ModVillagers.POKEMON_TRADER, 5, offers -> {
			offers.add((entity, random) -> cobblemon("rare_candy", 12, 1, 4, 30));
			offers.add((entity, random) -> cobblemon("ability_capsule", 24, 1, 2, 30));
		});
	}

	/** Tinkerers buy raw iron and redstone and sell the parts they make. */
	private static void tinkererTrades() {
		Platform.get().addTrades(ModVillagers.TINKERER, 1, offers -> {
			offers.add((entity, random) -> new MerchantOffer(new ItemCost(Items.RAW_IRON, 6), new ItemStack(Items.EMERALD), 16, 2, 0.05f));
			offers.add((entity, random) -> new MerchantOffer(new ItemCost(Items.EMERALD, 1), new ItemStack(Items.RAIL, 12), 12, 1, 0.05f));
		});
		Platform.get().addTrades(ModVillagers.TINKERER, 2, offers -> {
			offers.add((entity, random) -> new MerchantOffer(new ItemCost(Items.REDSTONE, 16), new ItemStack(Items.EMERALD), 16, 10, 0.05f));
			offers.add((entity, random) -> new MerchantOffer(new ItemCost(Items.EMERALD, 2), new ItemStack(Items.LANTERN, 3), 12, 5, 0.05f));
		});
		Platform.get().addTrades(ModVillagers.TINKERER, 3, offers -> {
			offers.add((entity, random) -> new MerchantOffer(new ItemCost(Items.EMERALD, 3), new ItemStack(Items.PISTON, 2), 8, 15, 0.05f));
			offers.add((entity, random) -> new MerchantOffer(new ItemCost(Items.EMERALD, 4), new ItemStack(Items.POWERED_RAIL, 6), 8, 15, 0.05f));
		});
		Platform.get().addTrades(ModVillagers.TINKERER, 4, offers ->
			offers.add((entity, random) -> new MerchantOffer(new ItemCost(Items.EMERALD, 5), new ItemStack(Items.HOPPER), 8, 20, 0.05f)));
		Platform.get().addTrades(ModVillagers.TINKERER, 5, offers ->
			offers.add((entity, random) -> new MerchantOffer(new ItemCost(Items.EMERALD, 8), new ItemStack(Items.CRAFTER), 4, 30, 0.05f)));
	}

	/** Netherworkers buy food for the road and sell what they bring back from the Nether. */
	private static void netherworkerTrades() {
		Platform.get().addTrades(ModVillagers.NETHERWORKER, 1, offers -> {
			offers.add((entity, random) -> new MerchantOffer(new ItemCost(Items.BREAD, 8), new ItemStack(Items.EMERALD), 16, 2, 0.05f));
			offers.add((entity, random) -> new MerchantOffer(new ItemCost(Items.EMERALD, 1), new ItemStack(Items.NETHERRACK, 32), 12, 1, 0.05f));
		});
		Platform.get().addTrades(ModVillagers.NETHERWORKER, 2, offers -> {
			offers.add((entity, random) -> new MerchantOffer(new ItemCost(Items.COOKED_PORKCHOP, 6), new ItemStack(Items.EMERALD), 16, 10, 0.05f));
			offers.add((entity, random) -> new MerchantOffer(new ItemCost(Items.EMERALD, 2), new ItemStack(Items.QUARTZ, 12), 12, 5, 0.05f));
		});
		Platform.get().addTrades(ModVillagers.NETHERWORKER, 3, offers -> {
			offers.add((entity, random) -> new MerchantOffer(new ItemCost(Items.EMERALD, 3), new ItemStack(Items.NETHER_WART, 8), 8, 15, 0.05f));
			offers.add((entity, random) -> new MerchantOffer(new ItemCost(Items.EMERALD, 3), new ItemStack(Items.GLOWSTONE, 4), 8, 15, 0.05f));
		});
		Platform.get().addTrades(ModVillagers.NETHERWORKER, 4, offers ->
			offers.add((entity, random) -> new MerchantOffer(new ItemCost(Items.EMERALD, 8), new ItemStack(Items.BLAZE_ROD, 2), 6, 20, 0.05f)));
		Platform.get().addTrades(ModVillagers.NETHERWORKER, 5, offers ->
			offers.add((entity, random) -> new MerchantOffer(new ItemCost(Items.EMERALD, 24), new ItemStack(Items.NETHERITE_SCRAP), 2, 30, 0.05f)));
	}

	/** Composters buy scraps and sell bone meal and rich earth. */
	private static void composterTrades() {
		Platform.get().addTrades(ModVillagers.COMPOSTER, 1, offers -> {
			offers.add((entity, random) -> new MerchantOffer(new ItemCost(Items.ROTTEN_FLESH, 24), new ItemStack(Items.EMERALD), 16, 2, 0.05f));
			offers.add((entity, random) -> new MerchantOffer(new ItemCost(Items.EMERALD, 1), new ItemStack(Items.BONE_MEAL, 12), 12, 1, 0.05f));
		});
		Platform.get().addTrades(ModVillagers.COMPOSTER, 2, offers -> {
			offers.add((entity, random) -> new MerchantOffer(new ItemCost(Items.WHEAT_SEEDS, 32), new ItemStack(Items.EMERALD), 16, 10, 0.05f));
			offers.add((entity, random) -> new MerchantOffer(new ItemCost(Items.EMERALD, 1), new ItemStack(Items.PODZOL, 8), 12, 5, 0.05f));
		});
		Platform.get().addTrades(ModVillagers.COMPOSTER, 3, offers ->
			offers.add((entity, random) -> new MerchantOffer(new ItemCost(Items.EMERALD, 2), new ItemStack(Items.MOSS_BLOCK, 8), 12, 15, 0.05f)));
		Platform.get().addTrades(ModVillagers.COMPOSTER, 4, offers ->
			offers.add((entity, random) -> new MerchantOffer(new ItemCost(Items.EMERALD, 3), new ItemStack(Items.MYCELIUM, 4), 8, 20, 0.05f)));
		Platform.get().addTrades(ModVillagers.COMPOSTER, 5, offers ->
			offers.add((entity, random) -> new MerchantOffer(new ItemCost(Items.EMERALD, 4), new ItemStack(Items.ROOTED_DIRT, 8), 8, 30, 0.05f)));
	}

	/** Stewards (27.5) buy paper and books and sell the village's paperwork: Blank Blueprints, City Plans, Village Ledgers. */
	private static void stewardTrades() {
		Platform.get().addTrades(ModVillagers.STEWARD, 1, offers -> {
			offers.add((entity, random) -> new MerchantOffer(new ItemCost(Items.PAPER, 24), new ItemStack(Items.EMERALD), 16, 2, 0.05f));
			offers.add((entity, random) -> new MerchantOffer(new ItemCost(Items.EMERALD, 1), new ItemStack(ModItems.BLANK_BLUEPRINT, 2), 12, 1, 0.05f));
		});
		Platform.get().addTrades(ModVillagers.STEWARD, 2, offers -> {
			offers.add((entity, random) -> new MerchantOffer(new ItemCost(Items.BOOK, 4), new ItemStack(Items.EMERALD), 12, 10, 0.05f));
			offers.add((entity, random) -> new MerchantOffer(new ItemCost(Items.EMERALD, 4), new ItemStack(ModItems.VILLAGE_LEDGER), 6, 5, 0.05f));
		});
		Platform.get().addTrades(ModVillagers.STEWARD, 3, offers ->
			offers.add((entity, random) -> new MerchantOffer(new ItemCost(Items.EMERALD, 6), new ItemStack(ModItems.CITY_PLAN), 4, 15, 0.05f)));
		Platform.get().addTrades(ModVillagers.STEWARD, 4, offers ->
			offers.add((entity, random) -> new MerchantOffer(new ItemCost(Items.WRITABLE_BOOK, 1), new ItemStack(Items.EMERALD, 2), 12, 20, 0.05f)));
		Platform.get().addTrades(ModVillagers.STEWARD, 5, offers ->
			offers.add((entity, random) -> new MerchantOffer(new ItemCost(Items.EMERALD, 3), new ItemStack(ModItems.BLANK_BLUEPRINT, 8), 8, 30, 0.05f)));
	}

	/** Sifters buy gravel and sand and sell what they find in them. */
	private static void sifterTrades() {
		Platform.get().addTrades(ModVillagers.SIFTER, 1, offers -> {
			offers.add((entity, random) -> new MerchantOffer(new ItemCost(Items.GRAVEL, 32), new ItemStack(Items.EMERALD), 16, 2, 0.05f));
			offers.add((entity, random) -> new MerchantOffer(new ItemCost(Items.EMERALD, 1), new ItemStack(Items.FLINT, 8), 12, 1, 0.05f));
		});
		Platform.get().addTrades(ModVillagers.SIFTER, 2, offers -> {
			offers.add((entity, random) -> new MerchantOffer(new ItemCost(Items.SAND, 32), new ItemStack(Items.EMERALD), 16, 10, 0.05f));
			offers.add((entity, random) -> new MerchantOffer(new ItemCost(Items.EMERALD, 2), new ItemStack(Items.IRON_NUGGET, 12), 8, 5, 0.05f));
		});
		Platform.get().addTrades(ModVillagers.SIFTER, 3, offers -> {
			offers.add((entity, random) -> new MerchantOffer(new ItemCost(Items.EMERALD, 3), new ItemStack(Items.GOLD_NUGGET, 12), 8, 15, 0.05f));
			offers.add((entity, random) -> new MerchantOffer(new ItemCost(Items.EMERALD, 2), new ItemStack(Items.AMETHYST_SHARD, 4), 8, 15, 0.05f));
		});
		Platform.get().addTrades(ModVillagers.SIFTER, 4, offers ->
			offers.add((entity, random) -> new MerchantOffer(new ItemCost(Items.EMERALD, 6), new ItemStack(Items.LAPIS_LAZULI, 8), 8, 20, 0.05f)));
		Platform.get().addTrades(ModVillagers.SIFTER, 5, offers ->
			offers.add((entity, random) -> new MerchantOffer(new ItemCost(Items.EMERALD, 12), new ItemStack(Items.DIAMOND), 3, 30, 0.05f)));
	}

	/** Scholars buy paper and books and sell what learning's made of. */
	private static void scholarTrades() {
		Platform.get().addTrades(ModVillagers.SCHOLAR, 1, offers -> {
			offers.add((entity, random) -> new MerchantOffer(new ItemCost(Items.PAPER, 24), new ItemStack(Items.EMERALD), 16, 2, 0.05f));
			offers.add((entity, random) -> new MerchantOffer(new ItemCost(Items.EMERALD, 1), new ItemStack(Items.BOOK, 2), 12, 1, 0.05f));
		});
		Platform.get().addTrades(ModVillagers.SCHOLAR, 2, offers -> {
			offers.add((entity, random) -> new MerchantOffer(new ItemCost(Items.BOOK, 4), new ItemStack(Items.EMERALD), 16, 10, 0.05f));
			offers.add((entity, random) -> new MerchantOffer(new ItemCost(Items.EMERALD, 3), new ItemStack(Items.COMPASS), 8, 5, 0.05f));
		});
		Platform.get().addTrades(ModVillagers.SCHOLAR, 3, offers -> {
			offers.add((entity, random) -> new MerchantOffer(new ItemCost(Items.EMERALD, 2), new ItemStack(Items.SPYGLASS), 6, 15, 0.05f));
			offers.add((entity, random) -> new MerchantOffer(new ItemCost(Items.EMERALD, 4), new ItemStack(Items.BOOKSHELF, 2), 8, 15, 0.05f));
		});
		Platform.get().addTrades(ModVillagers.SCHOLAR, 4, offers ->
			offers.add((entity, random) -> new MerchantOffer(new ItemCost(Items.EMERALD, 6), new ItemStack(Items.EXPERIENCE_BOTTLE, 3), 8, 20, 0.05f)));
		Platform.get().addTrades(ModVillagers.SCHOLAR, 5, offers ->
			offers.add((entity, random) -> new MerchantOffer(new ItemCost(Items.EMERALD, 10), new ItemStack(Items.ENCHANTING_TABLE), 2, 30, 0.05f)));
	}

	/** Undertakers deal in flowers, candles and headstones. */
	private static void undertakerTrades() {
		Platform.get().addTrades(ModVillagers.UNDERTAKER, 1, offers -> {
			offers.add((entity, random) -> new MerchantOffer(new ItemCost(Items.BONE, 16), new ItemStack(Items.EMERALD), 16, 2, 0.05f));
			offers.add((entity, random) -> new MerchantOffer(new ItemCost(Items.EMERALD, 1), new ItemStack(Items.CANDLE, 3), 12, 1, 0.05f));
		});
		Platform.get().addTrades(ModVillagers.UNDERTAKER, 2, offers -> {
			offers.add((entity, random) -> new MerchantOffer(new ItemCost(Items.ROTTEN_FLESH, 32), new ItemStack(Items.EMERALD), 16, 10, 0.05f));
			offers.add((entity, random) -> new MerchantOffer(new ItemCost(Items.EMERALD, 1), new ItemStack(Items.LILY_OF_THE_VALLEY, 4), 12, 5, 0.05f));
		});
		Platform.get().addTrades(ModVillagers.UNDERTAKER, 3, offers -> {
			offers.add((entity, random) -> new MerchantOffer(new ItemCost(Items.EMERALD, 2), new ItemStack(Items.SOUL_LANTERN, 2), 12, 15, 0.05f));
			offers.add((entity, random) -> new MerchantOffer(new ItemCost(Items.EMERALD, 3), new ItemStack(Items.MOSSY_STONE_BRICKS, 16), 8, 15, 0.05f));
		});
		Platform.get().addTrades(ModVillagers.UNDERTAKER, 4, offers ->
			offers.add((entity, random) -> new MerchantOffer(new ItemCost(Items.EMERALD, 8), new ItemStack(Items.GOLDEN_APPLE), 4, 20, 0.05f)));
		Platform.get().addTrades(ModVillagers.UNDERTAKER, 5, offers ->
			offers.add((entity, random) -> new MerchantOffer(new ItemCost(Items.EMERALD, 24), new ItemStack(Items.TOTEM_OF_UNDYING), 2, 30, 0.05f)));
	}

	/** Innkeepers buy what the kitchen needs and sell a traveller's comforts. */
	private static void innkeeperTrades() {
		Platform.get().addTrades(ModVillagers.INNKEEPER, 1, offers -> {
			offers.add((entity, random) -> new MerchantOffer(new ItemCost(Items.WHEAT, 20), new ItemStack(Items.EMERALD), 16, 2, 0.05f));
			offers.add((entity, random) -> new MerchantOffer(new ItemCost(Items.EMERALD, 1), new ItemStack(Items.BREAD, 6), 16, 1, 0.05f));
		});
		Platform.get().addTrades(ModVillagers.INNKEEPER, 2, offers -> {
			offers.add((entity, random) -> new MerchantOffer(new ItemCost(Items.SUGAR, 16), new ItemStack(Items.EMERALD), 16, 10, 0.05f));
			offers.add((entity, random) -> new MerchantOffer(new ItemCost(Items.EMERALD, 1), new ItemStack(Items.MUSHROOM_STEW), 12, 5, 0.05f));
		});
		Platform.get().addTrades(ModVillagers.INNKEEPER, 3, offers -> {
			offers.add((entity, random) -> new MerchantOffer(new ItemCost(Items.EMERALD, 2), new ItemStack(Items.RED_BED), 8, 15, 0.05f));
			offers.add((entity, random) -> new MerchantOffer(new ItemCost(Items.EMERALD, 1), new ItemStack(Items.PUMPKIN_PIE, 2), 12, 15, 0.05f));
		});
		Platform.get().addTrades(ModVillagers.INNKEEPER, 4, offers -> {
			offers.add((entity, random) -> new MerchantOffer(new ItemCost(Items.EMERALD, 3), new ItemStack(Items.CAKE), 6, 20, 0.05f));
			offers.add((entity, random) -> new MerchantOffer(new ItemCost(Items.EMERALD, 2), new ItemStack(Items.LANTERN, 2), 12, 20, 0.05f));
		});
		Platform.get().addTrades(ModVillagers.INNKEEPER, 5, offers ->
			offers.add((entity, random) -> new MerchantOffer(new ItemCost(Items.EMERALD, 6), new ItemStack(Items.JUKEBOX), 3, 30, 0.05f)));
	}

	/** Teachers buy paper and sell what lessons are made of. */
	private static void teacherTrades() {
		Platform.get().addTrades(ModVillagers.TEACHER, 1, offers -> {
			offers.add((entity, random) -> new MerchantOffer(new ItemCost(Items.PAPER, 24), new ItemStack(Items.EMERALD), 16, 2, 0.05f));
			offers.add((entity, random) -> new MerchantOffer(new ItemCost(Items.EMERALD, 1), new ItemStack(Items.BOOK), 12, 1, 0.05f));
		});
		Platform.get().addTrades(ModVillagers.TEACHER, 2, offers -> {
			offers.add((entity, random) -> new MerchantOffer(new ItemCost(Items.INK_SAC, 5), new ItemStack(Items.EMERALD), 16, 10, 0.05f));
			offers.add((entity, random) -> new MerchantOffer(new ItemCost(Items.EMERALD, 2), new ItemStack(Items.WRITABLE_BOOK), 12, 5, 0.05f));
		});
		Platform.get().addTrades(ModVillagers.TEACHER, 3, offers -> {
			offers.add((entity, random) -> new MerchantOffer(new ItemCost(Items.FEATHER, 16), new ItemStack(Items.EMERALD), 16, 15, 0.05f));
			offers.add((entity, random) -> new MerchantOffer(new ItemCost(Items.EMERALD, 3), new ItemStack(Items.MAP), 8, 15, 0.05f));
		});
		Platform.get().addTrades(ModVillagers.TEACHER, 4, offers -> {
			offers.add((entity, random) -> new MerchantOffer(new ItemCost(Items.EMERALD, 5), new ItemStack(Items.BOOKSHELF), 6, 20, 0.05f));
			offers.add((entity, random) -> new MerchantOffer(new ItemCost(Items.EMERALD, 4), new ItemStack(Items.CLOCK), 4, 20, 0.05f));
		});
		Platform.get().addTrades(ModVillagers.TEACHER, 5, offers ->
			offers.add((entity, random) -> new MerchantOffer(new ItemCost(Items.EMERALD, 8), new ItemStack(Items.EXPERIENCE_BOTTLE, 4), 6, 30, 0.05f)));
	}

	/** Ranchers buy hay and apples for the herd, and sell what riders need. */
	private static void rancherTrades() {
		Platform.get().addTrades(ModVillagers.RANCHER, 1, offers -> {
			offers.add((entity, random) -> new MerchantOffer(new ItemCost(Items.WHEAT, 20), new ItemStack(Items.EMERALD), 16, 2, 0.05f));
			offers.add((entity, random) -> new MerchantOffer(new ItemCost(Items.EMERALD, 2), new ItemStack(Items.LEAD), 12, 1, 0.05f));
		});
		Platform.get().addTrades(ModVillagers.RANCHER, 2, offers -> {
			offers.add((entity, random) -> new MerchantOffer(new ItemCost(Items.APPLE, 10), new ItemStack(Items.EMERALD), 16, 10, 0.05f));
			offers.add((entity, random) -> new MerchantOffer(new ItemCost(Items.EMERALD, 6), new ItemStack(Items.SADDLE), 6, 5, 0.05f));
		});
		Platform.get().addTrades(ModVillagers.RANCHER, 3, offers -> {
			offers.add((entity, random) -> new MerchantOffer(new ItemCost(Items.HAY_BLOCK, 4), new ItemStack(Items.EMERALD), 16, 15, 0.05f));
			offers.add((entity, random) -> new MerchantOffer(new ItemCost(Items.EMERALD, 4), new ItemStack(Items.LEATHER_HORSE_ARMOR), 6, 15, 0.05f));
		});
		Platform.get().addTrades(ModVillagers.RANCHER, 4, offers -> {
			offers.add((entity, random) -> new MerchantOffer(new ItemCost(Items.EMERALD, 3), new ItemStack(Items.GOLDEN_CARROT, 3), 12, 20, 0.05f));
			offers.add((entity, random) -> new MerchantOffer(new ItemCost(Items.EMERALD, 8), new ItemStack(Items.IRON_HORSE_ARMOR), 4, 20, 0.05f));
		});
		Platform.get().addTrades(ModVillagers.RANCHER, 5, offers -> {
			offers.add((entity, random) -> new MerchantOffer(new ItemCost(Items.EMERALD, 12), new ItemStack(Items.GOLDEN_HORSE_ARMOR), 3, 30, 0.05f));
			offers.add((entity, random) -> new MerchantOffer(new ItemCost(Items.EMERALD, 16), new ItemStack(Items.NAME_TAG), 3, 30, 0.05f));
		});
	}

	/** Beekeepers buy flowers and bottles, and sell what the hives make. */
	private static void beekeeperTrades() {
		Platform.get().addTrades(ModVillagers.BEEKEEPER, 1, offers -> {
			offers.add((entity, random) -> new MerchantOffer(new ItemCost(Items.DANDELION, 12), new ItemStack(Items.EMERALD), 16, 2, 0.05f));
			offers.add((entity, random) -> new MerchantOffer(new ItemCost(Items.EMERALD, 1), new ItemStack(Items.HONEY_BOTTLE, 2), 12, 1, 0.05f));
		});
		Platform.get().addTrades(ModVillagers.BEEKEEPER, 2, offers -> {
			offers.add((entity, random) -> new MerchantOffer(new ItemCost(Items.GLASS_BOTTLE, 12), new ItemStack(Items.EMERALD), 16, 10, 0.05f));
			offers.add((entity, random) -> new MerchantOffer(new ItemCost(Items.EMERALD, 1), new ItemStack(Items.HONEYCOMB, 4), 12, 5, 0.05f));
		});
		Platform.get().addTrades(ModVillagers.BEEKEEPER, 3, offers -> {
			offers.add((entity, random) -> new MerchantOffer(new ItemCost(Items.POPPY, 12), new ItemStack(Items.EMERALD), 16, 15, 0.05f));
			offers.add((entity, random) -> new MerchantOffer(new ItemCost(Items.EMERALD, 2), new ItemStack(Items.CANDLE, 4), 12, 15, 0.05f));
		});
		Platform.get().addTrades(ModVillagers.BEEKEEPER, 4, offers -> {
			offers.add((entity, random) -> new MerchantOffer(new ItemCost(Items.EMERALD, 3), new ItemStack(Items.BEEHIVE), 6, 20, 0.05f));
			offers.add((entity, random) -> new MerchantOffer(new ItemCost(Items.EMERALD, 2), new ItemStack(Items.HONEY_BLOCK), 8, 20, 0.05f));
		});
		Platform.get().addTrades(ModVillagers.BEEKEEPER, 5, offers ->
			offers.add((entity, random) -> new MerchantOffer(new ItemCost(Items.EMERALD, 4), new ItemStack(Items.HONEYCOMB_BLOCK, 2), 8, 30, 0.05f)));
	}

	/** Orchard Keepers buy and sell fruit; with Cobblemon, apricorns and berries too. */
	private static void orchardKeeperTrades() {
		Platform.get().addTrades(ModVillagers.ORCHARD_KEEPER, 1, offers -> {
			offers.add((entity, random) -> new MerchantOffer(new ItemCost(Items.SWEET_BERRIES, 22), new ItemStack(Items.EMERALD), 16, 2, 0.05f));
			offers.add((entity, random) -> new MerchantOffer(new ItemCost(Items.EMERALD, 1), new ItemStack(Items.APPLE, 5), 12, 1, 0.05f));
		});
		Platform.get().addTrades(ModVillagers.ORCHARD_KEEPER, 2, offers -> {
			offers.add((entity, random) -> new MerchantOffer(new ItemCost(Items.COCOA_BEANS, 12), new ItemStack(Items.EMERALD), 16, 10, 0.05f));
			offers.add((entity, random) -> new MerchantOffer(new ItemCost(Items.EMERALD, 1), new ItemStack(Items.GLOW_BERRIES, 6), 12, 5, 0.05f));
		});
		Platform.get().addTrades(ModVillagers.ORCHARD_KEEPER, 3, offers -> {
			offers.add((entity, random) -> cobblemon(APRICORNS[random.nextInt(APRICORNS.length)] + "_apricorn", 1, 4, 12, 15));
			offers.add((entity, random) -> new MerchantOffer(new ItemCost(Items.GLOW_BERRIES, 16), new ItemStack(Items.EMERALD), 12, 15, 0.05f));
		});
		Platform.get().addTrades(ModVillagers.ORCHARD_KEEPER, 4, offers -> {
			offers.add((entity, random) -> cobblemon(BERRIES[random.nextInt(BERRIES.length)] + "_berry", 3, 2, 8, 20));
			offers.add((entity, random) -> blueprint(StarterBlueprints.BERRY_FARM, 10));
		});
		Platform.get().addTrades(ModVillagers.ORCHARD_KEEPER, 5, offers -> {
			offers.add((entity, random) -> new MerchantOffer(new ItemCost(Items.EMERALD, 8), new ItemStack(Items.GOLDEN_APPLE), 4, 30, 0.05f));
			offers.add((entity, random) -> cobblemon(APRICORNS[random.nextInt(APRICORNS.length)] + "_apricorn_seed", 6, 1, 4, 30));
		});
	}

	/**
	 * Berry Breeders (28.9; Cobblemon only): common berries (Novice), Growth and Surprise Mulch (Apprentice), the Berry
	 * Nursery's blueprint (Journeyman, 28.13), and from Journeyman the berries her village has found (bred ones too, from
	 * the hall's book).
	 */
	private static void berryBreederTrades() {
		Platform.get().addTrades(ModVillagers.BERRY_BREEDER, 1, offers -> {
			offers.add((entity, random) -> cobblemon(COMMON_BERRIES[random.nextInt(COMMON_BERRIES.length)] + "_berry", 1, 3, 12, 1));
			offers.add((entity, random) -> cobblemon(COMMON_BERRIES[random.nextInt(COMMON_BERRIES.length)] + "_berry", 1, 3, 12, 1));
		});
		Platform.get().addTrades(ModVillagers.BERRY_BREEDER, 2, offers -> {
			offers.add((entity, random) -> cobblemon("growth_mulch", 2, 4, 12, 5));
			offers.add((entity, random) -> cobblemon("surprise_mulch", 3, 4, 12, 10));
		});
		Platform.get().addTrades(ModVillagers.BERRY_BREEDER, 3, offers ->
			offers.add((entity, random) -> blueprint(StarterBlueprints.BERRY_NURSERY, 10))); // 28.13
		for (int level = 3; level <= 5; level++) {
			int emeralds = level * 2;
			int xp = level == 3 ? 15 : level == 4 ? 20 : 30;
			Platform.get().addTrades(ModVillagers.BERRY_BREEDER, level, offers ->
				offers.add((entity, random) -> foundBerry(entity, random, emeralds, xp)));
		}
	}

	/** One of the berries the breeder's village has found (a common one when it has found none yet), for emeralds. */
	private static MerchantOffer foundBerry(net.minecraft.world.entity.Entity entity, net.minecraft.util.RandomSource random, int emeralds, int xp) {
		java.util.List<net.minecraft.resources.ResourceLocation> found = new java.util.ArrayList<>();
		io.github.jcondedata.aliveworkplace.berry.BerryChains.BerryData data = io.github.jcondedata.aliveworkplace.berry.BerryBreeders.data();
		if (data == null) {
			return null;
		}
		if (entity instanceof net.minecraft.world.entity.npc.Villager villager && villager.level() instanceof net.minecraft.server.level.ServerLevel level) {
			found.addAll(io.github.jcondedata.aliveworkplace.berry.BerryBreeders.found(level, villager));
		}
		if (found.isEmpty()) {
			return cobblemon(COMMON_BERRIES[random.nextInt(COMMON_BERRIES.length)] + "_berry", emeralds, 2, 8, xp);
		}
		found.sort(java.util.Comparator.comparing(Object::toString));
		ItemStack berry = data.item(found.get(random.nextInt(found.size())));
		return berry.isEmpty() ? null : new MerchantOffer(new ItemCost(Items.EMERALD, emeralds), berry.copyWithCount(2), 8, xp, 0.05f);
	}

	/** Ball Smiths buy apricorns and copper, and sell balls (nothing but the copper without Cobblemon). */
	private static void ballSmithTrades() {
		Platform.get().addTrades(ModVillagers.BALL_SMITH, 1, offers -> {
			offers.add((entity, random) -> new MerchantOffer(new ItemCost(Items.COPPER_INGOT, 8), new ItemStack(Items.EMERALD), 16, 2, 0.05f));
			offers.add((entity, random) -> cobblemon("poke_ball", 1, 4, 16, 1));
		});
		Platform.get().addTrades(ModVillagers.BALL_SMITH, 2, offers -> {
			offers.add((entity, random) -> net.minecraft.core.registries.BuiltInRegistries.ITEM
				.getOptional(net.minecraft.resources.ResourceLocation.fromNamespaceAndPath("cobblemon", APRICORNS[random.nextInt(APRICORNS.length)] + "_apricorn"))
				.map(item -> new MerchantOffer(new ItemCost(item, 12), new ItemStack(Items.EMERALD), 16, 10, 0.05f)).orElse(null));
			offers.add((entity, random) -> cobblemon("great_ball", 2, 4, 12, 5));
		});
		Platform.get().addTrades(ModVillagers.BALL_SMITH, 3, offers ->
			offers.add((entity, random) -> cobblemon("ultra_ball", 4, 4, 12, 15)));
		Platform.get().addTrades(ModVillagers.BALL_SMITH, 4, offers -> {
			String[] special = {"quick_ball", "dusk_ball", "timer_ball", "net_ball", "dive_ball", "heavy_ball", "level_ball", "lure_ball", "moon_ball"};
			offers.add((entity, random) -> cobblemon(special[random.nextInt(special.length)], 5, 2, 8, 20));
		});
		Platform.get().addTrades(ModVillagers.BALL_SMITH, 5, offers ->
			offers.add((entity, random) -> cobblemon("premier_ball", 1, 8, 8, 30)));
	}

	private static final String[] APRICORNS = {"red", "yellow", "green", "blue", "pink", "black", "white"};
	/** The berries that grow wild (no pair makes them): what a Berry Breeder sells from the start. */
	private static final String[] COMMON_BERRIES = {"oran", "cheri", "chesto", "pecha", "rawst", "aspear", "persim"};
	private static final String[] BERRIES = {"oran", "sitrus", "lum", "leppa", "pecha", "cheri", "chesto", "rawst", "aspear", "persim"};

	/** Emeralds for a Cobblemon item, or no offer when Cobblemon (or that item) isn't there. */
	private static MerchantOffer cobblemon(String item, int emeralds, int count, int uses, int xp) {
		return net.minecraft.core.registries.BuiltInRegistries.ITEM
			.getOptional(net.minecraft.resources.ResourceLocation.fromNamespaceAndPath("cobblemon", item))
			.map(i -> new MerchantOffer(new ItemCost(Items.EMERALD, emeralds), new ItemStack(i, count), uses, xp, 0.05f))
			.orElse(null);
	}

	/** The Aprijuice colours (Cobblemon's {@code aprijuice_<colour>}). */
	private static final String[] APRIJUICE = {"black", "blue", "green", "pink", "red", "white", "yellow"};

	/**
	 * Camp Cooks (28.8; Cobblemon only): Poké Bait and Poké Snacks (Novice), Aprijuice (Apprentice), Exp. Candy S or M and
	 * the Camp Kitchen's blueprint (Journeyman, 28.13), Lumiose Galette and Big Malasada (Expert), Exp. Candy L (Master).
	 */
	private static void campCookTrades() {
		Platform.get().addTrades(ModVillagers.CAMP_COOK, 1, offers -> {
			offers.add((entity, random) -> cobblemon("poke_bait", 2, 2, 12, 1));
			offers.add((entity, random) -> cobblemon("poke_snack", 3, 1, 12, 2));
		});
		Platform.get().addTrades(ModVillagers.CAMP_COOK, 2, offers -> {
			offers.add((entity, random) -> cobblemon("aprijuice_" + APRIJUICE[random.nextInt(APRIJUICE.length)], 3, 1, 12, 5));
			offers.add((entity, random) -> cobblemon("aprijuice_" + APRIJUICE[random.nextInt(APRIJUICE.length)], 3, 1, 12, 5));
		});
		Platform.get().addTrades(ModVillagers.CAMP_COOK, 3, offers -> {
			offers.add((entity, random) -> random.nextBoolean() ? cobblemon("exp_candy_s", 4, 1, 12, 10) : cobblemon("exp_candy_m", 8, 1, 8, 15));
			offers.add((entity, random) -> blueprint(StarterBlueprints.CAMP_KITCHEN, 12)); // 28.13
		});
		Platform.get().addTrades(ModVillagers.CAMP_COOK, 4, offers -> {
			offers.add((entity, random) -> cobblemon("lumiose_galette", 6, 1, 8, 20));
			offers.add((entity, random) -> cobblemon("big_malasada", 6, 1, 8, 20));
		});
		Platform.get().addTrades(ModVillagers.CAMP_COOK, 5, offers ->
			offers.add((entity, random) -> cobblemon("exp_candy_l", 16, 1, 4, 30)));
	}

	/**
	 * Habitat Keepers (28.10; Cobblemon only): Saccharine saplings and honey (Novice), Poké Snacks and the Habitat Garden's
	 * blueprint (Journeyman, 28.13), and on the way honeycomb and Saccharine logs.
	 */
	private static void habitatKeeperTrades() {
		Platform.get().addTrades(ModVillagers.HABITAT_KEEPER, 1, offers -> {
			offers.add((entity, random) -> cobblemon("saccharine_sapling", 2, 1, 12, 1));
			offers.add((entity, random) -> new MerchantOffer(new ItemCost(Items.EMERALD, 1), new ItemStack(Items.HONEY_BOTTLE, 2), 16, 2, 0.05f));
		});
		Platform.get().addTrades(ModVillagers.HABITAT_KEEPER, 2, offers -> {
			offers.add((entity, random) -> new MerchantOffer(new ItemCost(Items.GLASS_BOTTLE, 8), new ItemStack(Items.EMERALD), 16, 5, 0.05f));
			offers.add((entity, random) -> new MerchantOffer(new ItemCost(Items.EMERALD, 3), new ItemStack(Items.HONEYCOMB, 3), 12, 5, 0.05f));
		});
		Platform.get().addTrades(ModVillagers.HABITAT_KEEPER, 3, offers -> {
			offers.add((entity, random) -> cobblemon("poke_snack", 4, 1, 12, 10));
			offers.add((entity, random) -> blueprint(StarterBlueprints.HABITAT_GARDEN, 12)); // 28.13
		});
		Platform.get().addTrades(ModVillagers.HABITAT_KEEPER, 4, offers ->
			offers.add((entity, random) -> cobblemon("saccharine_log", 3, 4, 12, 15)));
		Platform.get().addTrades(ModVillagers.HABITAT_KEEPER, 5, offers ->
			offers.add((entity, random) -> new MerchantOffer(new ItemCost(Items.EMERALD, 8), new ItemStack(Items.SPYGLASS), 4, 30, 0.05f)));
	}

	/** The 18 types whose Type Gems Cobblemon 1.8 grows ({@code cobblemon:<type>_gem}). */
	private static final String[] GEM_TYPES = {"normal", "fire", "water", "grass", "electric", "ice", "fighting", "poison", "ground",
		"flying", "psychic", "bug", "rock", "ghost", "dragon", "dark", "steel", "fairy"};

	/**
	 * Gem Growers (28.11; work without Cobblemon): amethyst shards (Novice), tumblestones (Apprentice, with Cobblemon), the
	 * Gem Grotto's blueprint (Journeyman, 28.13), a Type Gem (Expert, Cobblemon 1.8), and on the way glass, spyglasses and tinted glass.
	 */
	private static void gemGrowerTrades() {
		Platform.get().addTrades(ModVillagers.GEM_GROWER, 1, offers -> {
			offers.add((entity, random) -> new MerchantOffer(new ItemCost(Items.EMERALD, 1), new ItemStack(Items.AMETHYST_SHARD, 4), 16, 1, 0.05f));
			offers.add((entity, random) -> new MerchantOffer(new ItemCost(Items.GLASS, 8), new ItemStack(Items.EMERALD), 16, 2, 0.05f));
		});
		Platform.get().addTrades(ModVillagers.GEM_GROWER, 2, offers -> {
			offers.add((entity, random) -> cobblemon("tumblestone", 2, 4, 12, 5));
			offers.add((entity, random) -> cobblemon("sky_tumblestone", 3, 4, 12, 5));
			offers.add((entity, random) -> cobblemon("black_tumblestone", 3, 4, 12, 5));
			offers.add((entity, random) -> new MerchantOffer(new ItemCost(Items.EMERALD, 2), new ItemStack(Items.TINTED_GLASS, 2), 12, 5, 0.05f));
		});
		Platform.get().addTrades(ModVillagers.GEM_GROWER, 3, offers -> {
			offers.add((entity, random) -> random.nextBoolean()
				? new MerchantOffer(new ItemCost(Items.EMERALD, 4), new ItemStack(Items.SPYGLASS), 8, 10, 0.05f)
				: new MerchantOffer(new ItemCost(Items.AMETHYST_SHARD, 12), new ItemStack(Items.EMERALD), 16, 10, 0.05f));
			offers.add((entity, random) -> blueprint(StarterBlueprints.GEM_GROTTO, 12)); // 28.13
		});
		Platform.get().addTrades(ModVillagers.GEM_GROWER, 4, offers -> {
			offers.add((entity, random) -> cobblemon(GEM_TYPES[random.nextInt(GEM_TYPES.length)] + "_gem", 6, 1, 8, 15));
			offers.add((entity, random) -> cobblemon(GEM_TYPES[random.nextInt(GEM_TYPES.length)] + "_gem", 6, 1, 8, 15));
		});
		Platform.get().addTrades(ModVillagers.GEM_GROWER, 5, offers ->
			offers.add((entity, random) -> new MerchantOffer(new ItemCost(Items.EMERALD, 12), new ItemStack(Items.AMETHYST_BLOCK, 4), 4, 30, 0.05f)));
	}

	/**
	 * Daycare Keepers (28.12; Cobblemon only): Exp. Candy XS (Novice), an Everstone and the Daycare's
	 * blueprint (Journeyman, 28.13), a Destiny Knot (Master),
	 * and on the way eggs, wheat and hay.
	 */
	private static void daycareKeeperTrades() {
		Platform.get().addTrades(ModVillagers.DAYCARE_KEEPER, 1, offers -> {
			offers.add((entity, random) -> cobblemon("exp_candy_xs", 2, 3, 16, 1));
			offers.add((entity, random) -> new MerchantOffer(new ItemCost(Items.WHEAT, 20), new ItemStack(Items.EMERALD), 16, 2, 0.05f));
		});
		Platform.get().addTrades(ModVillagers.DAYCARE_KEEPER, 2, offers -> {
			offers.add((entity, random) -> new MerchantOffer(new ItemCost(Items.EGG, 16), new ItemStack(Items.EMERALD), 16, 5, 0.05f));
			offers.add((entity, random) -> new MerchantOffer(new ItemCost(Items.EMERALD, 2), new ItemStack(Items.HAY_BLOCK, 2), 12, 5, 0.05f));
		});
		Platform.get().addTrades(ModVillagers.DAYCARE_KEEPER, 3, offers -> {
			offers.add((entity, random) -> cobblemon("everstone", 10, 1, 4, 10));
			offers.add((entity, random) -> blueprint(StarterBlueprints.DAYCARE, 14)); // 28.13
		});
		Platform.get().addTrades(ModVillagers.DAYCARE_KEEPER, 4, offers ->
			offers.add((entity, random) -> cobblemon("exp_candy_s", 4, 2, 12, 15)));
		Platform.get().addTrades(ModVillagers.DAYCARE_KEEPER, 5, offers ->
			offers.add((entity, random) -> cobblemon("destiny_knot", 24, 1, 2, 30)));
	}

	/**
	 * Trainer Leaders (with Cobblemon; ROADMAP 28.16): an Expert Leader sells the Arena's blueprint, where the village's
	 * Festival Cup is fought. Leaders trade nothing else (sneak and right-click to see).
	 */
	private static void trainerLeaderTrades() {
		Platform.get().addTrades(ModVillagers.TRAINER_LEADER, 4, offers -> {
			if (Platform.get().isModLoaded("cobblemon")) {
				offers.add((entity, random) -> blueprint(StarterBlueprints.ARENA, 20));
			}
		});
	}

	private static void nurseTrades() {
		Platform.get().addTrades(ModVillagers.NURSE, 1, offers -> {
			offers.add((entity, random) -> new MerchantOffer(new ItemCost(Items.GLISTERING_MELON_SLICE, 4), new ItemStack(Items.EMERALD), 12, 2, 0.05f));
			offers.add((entity, random) -> new MerchantOffer(new ItemCost(Items.EMERALD, 3),
				net.minecraft.world.item.alchemy.PotionContents.createItemStack(Items.POTION, net.minecraft.world.item.alchemy.Potions.HEALING), 6, 5, 0.05f));
		});
		Platform.get().addTrades(ModVillagers.NURSE, 2, offers ->
			offers.add((entity, random) -> new MerchantOffer(new ItemCost(Items.EMERALD, 4),
				net.minecraft.world.item.alchemy.PotionContents.createItemStack(Items.POTION, net.minecraft.world.item.alchemy.Potions.REGENERATION), 6, 10, 0.05f)));
		Platform.get().addTrades(ModVillagers.NURSE, 3, offers -> {
			offers.add((entity, random) -> new MerchantOffer(new ItemCost(Items.EMERALD, 2), new ItemStack(Items.HONEY_BOTTLE, 3), 12, 15, 0.05f));
			if (Platform.get().isModLoaded("cobblemon")) { // ROADMAP 28.7: Journeyman Nurses sell the Pokémon Center
				offers.add((entity, random) -> blueprint(StarterBlueprints.POKEMON_CENTER, 14));
			}
		});
		Platform.get().addTrades(ModVillagers.NURSE, 4, offers ->
			offers.add((entity, random) -> new MerchantOffer(new ItemCost(Items.EMERALD, 8), new ItemStack(Items.GOLDEN_APPLE), 4, 20, 0.05f)));
		Platform.get().addTrades(ModVillagers.NURSE, 5, offers ->
			offers.add((entity, random) -> new MerchantOffer(new ItemCost(Items.EMERALD, 12), new ItemStack(Items.GOLDEN_CARROT, 8), 8, 30, 0.05f)));
	}

	private static MerchantOffer blueprint(StarterBlueprints.Entry entry, int emeralds) {
		return new MerchantOffer(new ItemCost(Items.EMERALD, emeralds), BlueprintItem.create(entry.id(), entry.size()), 3, 10, 0.05f);
	}

	private ModTrades() {
	}
}
