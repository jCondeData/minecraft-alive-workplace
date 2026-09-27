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
 * Villages grow a builder's workshop now and then: a small house with a Builder's Bench, a chest of
 * building supplies and a villager who takes the bench, so builders turn up without players having
 * to make one. Added to the vanilla house pools (and to any data pack's replacement of them) when
 * the server starts.
 */
public final class VillageHouses {
	public static final List<String> STYLES = List.of("plains", "desert", "savanna", "snowy", "taiga");
	/**
	 * Vanilla job houses have weight 2 in house pools that add up to 70–90, so 3 gives roughly every
	 * other village a workshop. (-Daliveworkplace.workshopWeight overrides it for screenshots.)
	 */
	private static final int WEIGHT = Integer.getInteger("aliveworkplace.workshopWeight", 3);

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
			StructurePoolElement workshop = StructurePoolElement.legacy(workshop(style).toString(), none)
				.apply(StructureTemplatePool.Projection.RIGID);
			StructureTemplatePoolAccessor access = (StructureTemplatePoolAccessor) pool;
			for (int i = 0; i < WEIGHT; i++) {
				access.aliveworkplace$templates().add(workshop);
			}
			List<Pair<StructurePoolElement, Integer>> raw = new ArrayList<>(access.aliveworkplace$rawTemplates());
			raw.add(Pair.of(workshop, WEIGHT));
			access.aliveworkplace$setRawTemplates(raw);
		}
	}

	private VillageHouses() {
	}
}
