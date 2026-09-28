package io.github.jcondedata.aliveworkplace.work;

import io.github.jcondedata.aliveworkplace.compat.cobblemon.CobblemonPartners;
import io.github.jcondedata.aliveworkplace.registry.ModVillagers;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.npc.VillagerProfession;

/**
 * Pokémon partners (with Cobblemon): Pokémon kept in a Pasture Block within {@link #RADIUS} blocks of a
 * villager's workstation help with the work when their type suits the job — Fighting, Rock and Steel
 * types build, Ground/Rock/Steel dig, Grass and Bug types tend trees, and so on (see {@link #types}).
 * Each helper takes {@link #PER_PARTNER} off the time the job takes, up to {@link #MAX} helpers.
 * Any pasture works, including ones whose Pokémon also work for Cobbleworkers.
 */
public final class Partners {
	public static final int RADIUS = 16;
	public static final int MAX = 3;
	public static final float PER_PARTNER = 0.15f;
	private static final int RECHECK_TICKS = 100;
	private static final boolean COBBLEMON = FabricLoader.getInstance().isModLoaded("cobblemon");

	private record Cached(long until, List<Component> names) {
	}

	private static final Map<Villager, Cached> CACHE = new WeakHashMap<>();

	/** Lower-case Pokémon types that help with this job (empty: no job Pokémon can help with). */
	public static Set<String> types(VillagerProfession profession) {
		if (profession == ModVillagers.BUILDER) {
			return Set.of("fighting", "rock", "steel");
		}
		if (profession == ModVillagers.MINER) {
			return Set.of("ground", "rock", "steel");
		}
		if (profession == ModVillagers.LUMBERJACK) {
			return Set.of("grass", "bug", "fighting");
		}
		if (profession == ModVillagers.ORCHARD_KEEPER) {
			return Set.of("grass", "bug", "flying");
		}
		if (profession == VillagerProfession.FARMER) {
			return Set.of("grass", "ground", "water");
		}
		if (profession == VillagerProfession.FISHERMAN) {
			return Set.of("water", "ice");
		}
		if (profession == ModVillagers.NURSE) {
			return Set.of("fairy", "normal", "psychic");
		}
		return Set.of();
	}

	/** The Pokémon helping this villager right now (their names), nearest first. */
	public static List<Component> helpers(Villager villager) {
		if (!COBBLEMON || !(villager.level() instanceof ServerLevel level)) {
			return List.of();
		}
		Set<String> types = types(villager.getVillagerData().getProfession());
		if (types.isEmpty()) {
			return List.of();
		}
		long now = level.getGameTime();
		synchronized (CACHE) {
			Cached cached = CACHE.get(villager);
			if (cached != null && cached.until() > now) {
				return cached.names();
			}
		}
		BlockPos site = villager.getBrain().getMemory(MemoryModuleType.JOB_SITE)
			.filter(p -> p.dimension() == level.dimension()).map(GlobalPos::pos).orElse(null);
		List<Component> names = site == null ? List.of() : CobblemonPartners.helpers(level, site, RADIUS, types, MAX);
		synchronized (CACHE) {
			CACHE.put(villager, new Cached(now + RECHECK_TICKS, names));
		}
		return names;
	}

	/** Forget what was found (tests; a pasture was just filled). */
	public static void forget(Villager villager) {
		synchronized (CACHE) {
			CACHE.remove(villager);
		}
	}

	/** How much of the usual time the job takes with its helpers (1 without any). */
	public static float factor(Villager villager) {
		return 1f - PER_PARTNER * Math.min(MAX, helpers(villager).size());
	}

	/** "Machop", "Machop and Geodude", "Machop, Geodude and Onix". */
	public static Component names(List<Component> names) {
		if (names.size() == 1) {
			return names.get(0);
		}
		net.minecraft.network.chat.MutableComponent out = Component.empty();
		for (int i = 0; i < names.size(); i++) {
			if (i > 0) {
				out.append(i == names.size() - 1 ? Component.translatable("message.aliveworkplace.partners.and") : Component.literal(", "));
			}
			out.append(names.get(i));
		}
		return out;
	}

	private Partners() {
	}
}
