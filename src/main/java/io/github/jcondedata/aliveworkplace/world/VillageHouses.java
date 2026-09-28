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
 * building supplies), a trainer's house, a guard house, a clinic and a post office, each with a bed and a
 * villager who takes the job block, so these workers turn up without players having to make them. Added
 * to the vanilla house pools (and to any data pack's replacement of them) when the server starts.
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

	private static java.util.Map<String, Integer> houses() {
		Integer override = Integer.getInteger("aliveworkplace.houseWeight");
		java.util.Map<String, Integer> out = new java.util.LinkedHashMap<>();
		out.put("trainers_house", override != null ? override : 6);
		out.put("guard_house", override != null ? override : 3);
		out.put("clinic", override != null ? override : 2);
		out.put("post_office", override != null ? override : 2);
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
		for (String style : STYLES) {
			StructureTemplatePool pool = pools.get(housePool(style));
			if (pool == null) {
				continue;
			}
			add(pool, workshop(style), WEIGHT, none);
			for (var house : HOUSES.entrySet()) {
				add(pool, AliveWorkplace.id("village/" + style + "_" + house.getKey()), house.getValue(), none);
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
