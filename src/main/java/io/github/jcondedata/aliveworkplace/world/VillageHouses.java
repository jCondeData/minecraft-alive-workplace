package io.github.jcondedata.aliveworkplace.world;

import io.github.jcondedata.aliveworkplace.mc.Lookup;
import io.github.jcondedata.aliveworkplace.platform.Platform;
import com.mojang.datafixers.util.Pair;
import io.github.jcondedata.aliveworkplace.AliveWorkplace;
import io.github.jcondedata.aliveworkplace.mixin.StructureTemplatePoolAccessor;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.levelgen.structure.pools.StructurePoolElement;
import net.minecraft.world.level.levelgen.structure.pools.StructureTemplatePool;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureProcessorList;

/**
 * Villages grow our kinds of houses now and then: a builder's workshop (a Builder's Bench, a chest of
 * building supplies), a guard house, a clinic, a post office and — with Cobblemon — a trainer's house, a
 * Trainer Leader's hall, a school (Move Tutor) and a trade hall (Pokémon Trader), each with a bed and a
 * villager who takes the job block, so these workers turn up without players having to make them. Added
 * to the vanilla house pools (and to any data pack's replacement of them) and to Repurposed Structures'
 * villages when the server starts.
 */
public final class VillageHouses {
	public static final List<String> STYLES = List.of("plains", "desert", "savanna", "snowy", "taiga");
	/**
	 * Vanilla job houses have weight 2 in house pools that add up to 70–90, so 3 gives roughly every
	 * other village a workshop. (-Daliveworkplace.workshopWeight overrides it for screenshots.)
	 */
	private static final int WEIGHT = Integer.getInteger("aliveworkplace.workshopWeight", 3);

	/**
	 * The other staffed houses and their weights. Trainers' houses are the most common, so most villages have
	 * someone to battle (-Daliveworkplace.houseWeight overrides all of them for screenshots).
	 */
	private static final java.util.Map<String, Integer> HOUSES = houses();

	/** Houses whose job only works with Cobblemon: villages without it don't grow them. */
	public static final java.util.Set<String> COBBLEMON_HOUSES = java.util.Set.of("trainers_house", "leaders_hall", "school", "trade_hall", "ball_workshop", "fossil_lab",
		"pokemon_center", "camp_kitchen", "berry_nursery", "daycare", "gem_grotto");

	/**
	 * The Pokémon jobs' houses (ROADMAP 28.15): a Pokémon Center (a nurse at a Healing Machine), a Camp Kitchen (a Camp
	 * Cook), a Berry Nursery (a Berry Breeder), a Daycare (a Daycare Keeper) and a Gem Grotto (a Gem Grower), each with
	 * its villager already in the job. Only with Cobblemon, like the other Pokémon houses, and config
	 * {@code pokemonVillageHouses}; read when the server starts, which is when the pools are filled.
	 */
	public static final java.util.Set<String> POKEMON_JOB_HOUSES = java.util.Set.of("pokemon_center", "camp_kitchen", "berry_nursery", "daycare", "gem_grotto");

	/** Config switch {@code pokemonVillageHouses}: off, villages don't grow the five Pokémon jobs' houses (houses already grown stay). */
	public static boolean POKEMON_JOBS = true;

	/**
	 * The luxury jobs' houses (ROADMAP 34.13) and the switch each needs: a Winery (a Vintner at a cauldron, config
	 * {@code vintners}) and a Tailor's Shop (a Tailor at a loom, config {@code tailors}). With its switch off (or Classes
	 * and luxuries not yet released) villages don't grow that house: its villager would have a job that does nothing.
	 * Read when the server starts, which is when the pools are filled; houses already grown stay.
	 */
	public static final java.util.Map<String, java.util.function.BooleanSupplier> LUXURY_HOUSES = java.util.Map.of(
		"winery", () -> io.github.jcondedata.aliveworkplace.vintner.Vintners.ENABLED,
		"tailors_shop", () -> io.github.jcondedata.aliveworkplace.tailor.Tailors.ENABLED,
		// ROADMAP 34.14: a Print Shop (a Printer at a cartography table, config printers) and a Jeweller's Workshop (a
		// Jeweller at a stonecutter, config jewellers)
		"print_shop", () -> io.github.jcondedata.aliveworkplace.printer.Printers.ENABLED,
		"jewellers_workshop", () -> io.github.jcondedata.aliveworkplace.jeweller.Jewellers.ENABLED);

	private static java.util.Map<String, Integer> houses() {
		Integer override = Integer.getInteger("aliveworkplace.houseWeight");
		java.util.Map<String, Integer> out = new java.util.LinkedHashMap<>();
		out.put("trainers_house", override != null ? override : 6);
		out.put("guard_house", override != null ? override : 3);
		out.put("clinic", override != null ? override : 2);
		out.put("post_office", override != null ? override : 2);
		// The village's Trainer Leader: common enough that most villages have one.
		out.put("leaders_hall", override != null ? override : 5);
		out.put("school", override != null ? override : 2);
		out.put("trade_hall", override != null ? override : 2);
		out.put("orchard_house", override != null ? override : 2);
		out.put("ball_workshop", override != null ? override : 2);
		// Travel posts: common, so most villages end up on the travel network.
		out.put("ferry_house", override != null ? override : 4);
		// The village's store and its porter.
		out.put("storehouse", override != null ? override : 3);
		// The carpenter who makes what the village's builders are waiting for.
		out.put("carpenters_workshop", override != null ? override : 2);
		// The chef who cooks for the village.
		out.put("kitchen", override != null ? override : 2);
		// The fossil scientist (Cobblemon only).
		out.put("fossil_lab", override != null ? override : 2);
		// The florist, the rancher, the teacher, the innkeeper and the undertaker.
		out.put("flower_shop", override != null ? override : 2);
		out.put("ranch_house", override != null ? override : 2);
		out.put("schoolhouse", override != null ? override : 2);
		out.put("inn_room", override != null ? override : 2);
		out.put("mortuary", override != null ? override : 1);
		// The tinkerer and the sifter.
		out.put("tinkers_shop", override != null ? override : 2);
		out.put("sifting_shed", override != null ? override : 1);
		out.put("compost_yard", override != null ? override : 1);
		// The vintner and the tailor (ROADMAP 34.13): only while their jobs are on (LUXURY_HOUSES).
		out.put("winery", override != null ? override : 2);
		out.put("tailors_shop", override != null ? override : 2);
		// The printer and the jeweller (ROADMAP 34.14): the same, by their jobs' switches.
		out.put("print_shop", override != null ? override : 2);
		out.put("jewellers_workshop", override != null ? override : 2);
		// The Pokémon jobs' houses (ROADMAP 28.15; Cobblemon and config pokemonVillageHouses): the Pokémon Center most often.
		// (-Daliveworkplace.pokemonHouseWeight overrides these five, for the village screenshots.)
		Integer pokemon = Integer.getInteger("aliveworkplace.pokemonHouseWeight", override);
		out.put("pokemon_center", pokemon != null ? pokemon : 2);
		out.put("camp_kitchen", pokemon != null ? pokemon : 1);
		out.put("berry_nursery", pokemon != null ? pokemon : 1);
		out.put("daycare", pokemon != null ? pokemon : 1);
		out.put("gem_grotto", pokemon != null ? pokemon : 1);
		return out;
	}

	/** The staffed houses (besides the workshop) villages grow here, by name. */
	public static java.util.List<String> houseNames() {
		boolean cobblemon = Platform.get().isModLoaded("cobblemon");
		return HOUSES.keySet().stream().filter(h -> cobblemon || !COBBLEMON_HOUSES.contains(h))
			.filter(h -> POKEMON_JOBS || !POKEMON_JOB_HOUSES.contains(h))
			.filter(h -> !LUXURY_HOUSES.containsKey(h) || LUXURY_HOUSES.get(h).getAsBoolean()).toList();
	}

	/**
	 * Other mods' villages: their house pool and the style of ours that fits it best. Pools that aren't
	 * there (the mod isn't installed) are skipped. Repurposed Structures uses vanilla's jigsaw names, so our
	 * houses join its streets like vanilla ones; nether and ocean villages are left alone.
	 */
	public static final java.util.Map<ResourceLocation, String> MODDED_HOUSE_POOLS = moddedPools();

	private static java.util.Map<ResourceLocation, String> moddedPools() {
		java.util.Map<ResourceLocation, String> out = new java.util.LinkedHashMap<>();
		String[][] rs = {
			{"badlands", "desert"}, {"bamboo", "savanna"}, {"birch", "plains"}, {"cherry", "plains"}, {"dark_forest", "taiga"},
			{"giant_taiga", "taiga"}, {"jungle", "savanna"}, {"mountains", "taiga"}, {"mushroom", "plains"}, {"oak", "plains"},
			{"swamp", "plains"}
		};
		for (String[] village : rs) {
			out.put(ResourceLocation.fromNamespaceAndPath("repurposed_structures", "villages/" + village[0] + "/houses"), village[1]);
		}
		return out;
	}

	public static void init() {
		Platform.get().onServerStarting(VillageHouses::addWorkshops);
	}

	public static ResourceLocation workshop(String style) {
		return AliveWorkplace.id("village/" + style + "_builders_workshop");
	}

	public static ResourceLocation housePool(String style) {
		return ResourceLocation.withDefaultNamespace("village/" + style + "/houses");
	}

	private static void addWorkshops(MinecraftServer server) {
		Registry<StructureTemplatePool> pools = Lookup.registry(server.registryAccess(), Registries.TEMPLATE_POOL);
		Registry<StructureProcessorList> processors = Lookup.registry(server.registryAccess(), Registries.PROCESSOR_LIST);
		Holder<StructureProcessorList> none = Lookup.holderOrThrow(processors, ResourceKey.create(Registries.PROCESSOR_LIST, ResourceLocation.withDefaultNamespace("empty")));
		java.util.Map<ResourceLocation, String> targets = new java.util.LinkedHashMap<>();
		for (String style : STYLES) {
			targets.put(housePool(style), style);
		}
		targets.putAll(MODDED_HOUSE_POOLS);
		for (var target : targets.entrySet()) {
			StructureTemplatePool pool = Lookup.value(pools, target.getKey());
			if (pool == null) {
				continue;
			}
			String style = target.getValue();
			add(pool, workshop(style), WEIGHT, none);
			for (String house : houseNames()) {
				add(pool, AliveWorkplace.id("village/" + style + "_" + house), HOUSES.get(house), none);
			}
		}
	}

	private static void add(StructureTemplatePool pool, ResourceLocation template, int weight, Holder<StructureProcessorList> none) {
		if (weight <= 0) {
			return;
		}
		StructurePoolElement element = StructurePoolElement.legacy(template.toString(), none).apply(StructureTemplatePool.Projection.RIGID);
		StructureTemplatePoolAccessor access = (StructureTemplatePoolAccessor) pool;
		for (int i = 0; i < weight; i++) {
			access.aliveworkplace$templates().add(element);
		}
		List<Pair<StructurePoolElement, Integer>> raw = new ArrayList<>(access.aliveworkplace$rawTemplates());
		raw.add(Pair.of(element, weight));
		access.aliveworkplace$setRawTemplates(raw);
	}

	private VillageHouses() {
	}
}
