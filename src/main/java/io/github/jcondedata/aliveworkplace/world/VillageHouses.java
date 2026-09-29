package io.github.jcondedata.aliveworkplace.world;

import com.mojang.datafixers.util.Pair;
import io.github.jcondedata.aliveworkplace.AliveWorkplace;
import io.github.jcondedata.aliveworkplace.mixin.StructureTemplatePoolAccessor;
import java.util.ArrayList;
import java.util.List;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
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
	public static final java.util.Set<String> COBBLEMON_HOUSES = java.util.Set.of("trainers_house", "leaders_hall", "school", "trade_hall", "ball_workshop", "fossil_lab");

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
		return out;
	}

	/** The staffed houses (besides the workshop) villages grow here, by name. */
	public static java.util.List<String> houseNames() {
		boolean cobblemon = net.fabricmc.loader.api.FabricLoader.getInstance().isModLoaded("cobblemon");
		return HOUSES.keySet().stream().filter(h -> cobblemon || !COBBLEMON_HOUSES.contains(h)).toList();
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
		ServerLifecycleEvents.SERVER_STARTING.register(VillageHouses::addWorkshops);
	}

	public static ResourceLocation workshop(String style) {
		return AliveWorkplace.id("village/" + style + "_builders_workshop");
	}

	public static ResourceLocation housePool(String style) {
		return ResourceLocation.withDefaultNamespace("village/" + style + "/houses");
	}

	private static void addWorkshops(MinecraftServer server) {
		Registry<StructureTemplatePool> pools = server.registryAccess().registryOrThrow(Registries.TEMPLATE_POOL);
		Registry<StructureProcessorList> processors = server.registryAccess().registryOrThrow(Registries.PROCESSOR_LIST);
		Holder<StructureProcessorList> none = processors.getHolderOrThrow(
			ResourceKey.create(Registries.PROCESSOR_LIST, ResourceLocation.withDefaultNamespace("empty")));
		java.util.Map<ResourceLocation, String> targets = new java.util.LinkedHashMap<>();
		for (String style : STYLES) {
			targets.put(housePool(style), style);
		}
		targets.putAll(MODDED_HOUSE_POOLS);
		for (var target : targets.entrySet()) {
			StructureTemplatePool pool = pools.get(target.getKey());
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
