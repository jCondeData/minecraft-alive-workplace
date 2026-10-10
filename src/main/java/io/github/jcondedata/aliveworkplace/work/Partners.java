package io.github.jcondedata.aliveworkplace.work;

import io.github.jcondedata.aliveworkplace.platform.Platform;
import io.github.jcondedata.aliveworkplace.registry.ModVillagers;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;
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
	public static int RADIUS = 16;
	public static final int MAX = 3;
	public static final float PER_PARTNER = 0.15f;
	private static final int RECHECK_TICKS = 100;
	private static final boolean COBBLEMON = Platform.get().isModLoaded("cobblemon");

	private record Cached(long until, List<Component> names) {
	}

	private static final Map<Villager, Cached> CACHE = new WeakHashMap<>();
	/** The day each worker last had its partners counted (the Workers' Cup's counter, ROADMAP 28.22). */
	private static final Map<Villager, Long> COUNTED = new WeakHashMap<>();

	/** Every type some job's partners may be (the Workers' Cup's villager trainers field these). */
	public static Set<String> allTypes() {
		Set<String> out = new java.util.TreeSet<>();
		for (VillagerProfession profession : net.minecraft.core.registries.BuiltInRegistries.VILLAGER_PROFESSION) {
			out.addAll(types(profession));
		}
		return out;
	}

	/**
	 * Counts today for each Pokémon helping this villager at work (the Workers' Cup, ROADMAP 28.22): each pastured partner
	 * gets one more day on a counter in its own saved data, never twice the same day, however many workers it helps.
	 * Only while the villager is at work (its brain's work activity) and has partners helping.
	 */
	public static void countDay(Villager villager) {
		if (!COBBLEMON || !(villager.level() instanceof ServerLevel level)
			|| !villager.getBrain().isActive(net.minecraft.world.entity.schedule.Activity.WORK)) {
			return;
		}
		long day = io.github.jcondedata.aliveworkplace.hall.Chronicle.day(level);
		synchronized (COUNTED) {
			Long last = COUNTED.get(villager);
			if (last != null && last == day) {
				return;
			}
		}
		Set<String> types = types(villager.getVillagerData().getProfession());
		BlockPos site = villager.getBrain().getMemory(MemoryModuleType.JOB_SITE)
			.filter(p -> p.dimension() == level.dimension()).map(GlobalPos::pos).orElse(null);
		if (types.isEmpty() || site == null) {
			return;
		}
		int max = max(villager);
		int counted = PokemonPartners.EXTENSION.call(p -> p.countPartnerDay(level, site, RADIUS, types, max, day), 0);
		if (counted > 0) {
			synchronized (COUNTED) {
				COUNTED.put(villager, day);
			}
		}
	}

	/** Forget which workers were counted today (tests). */
	public static void forgetCounted() {
		synchronized (COUNTED) {
			COUNTED.clear();
		}
	}

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
		if (profession == ModVillagers.BALL_SMITH) {
			return Set.of("steel", "fire");
		}
		if (profession == ModVillagers.NURSE) {
			return Set.of("fairy", "normal", "psychic");
		}
		if (profession == ModVillagers.CARPENTER || profession == VillagerProfession.MASON) {
			return Set.of("fighting", "rock", "steel");
		}
		if (profession == ModVillagers.FOSSIL_SCIENTIST) {
			return Set.of("rock", "psychic");
		}
		if (profession == VillagerProfession.FLETCHER) {
			return Set.of("flying", "bug"); // feathers and silk
		}
		if (profession == VillagerProfession.WEAPONSMITH) {
			return Set.of("steel", "fighting");
		}
		if (profession == VillagerProfession.TOOLSMITH) {
			return Set.of("steel", "fire");
		}
		if (profession == VillagerProfession.ARMORER) {
			return Set.of("fire", "steel"); // more ore each trip (and Fire types smelt some on the spot)
		}
		if (profession == ModVillagers.CHEF) {
			return Set.of("fire", "normal");
		}
		if (profession == ModVillagers.PORTER) {
			return Set.of("fighting", "normal"); // strong arms: more carried each trip
		}
		if (profession == ModVillagers.COMPOSTER) {
			return Set.of("poison", "grass"); // Trubbish, Grimer, Gloom...: the compost rots quicker
		}
		if (profession == ModVillagers.NETHERWORKER) {
			return Set.of("fire", "dark"); // Houndour, Magmar, Sneasel...: expeditions go quicker
		}
		if (profession == ModVillagers.TINKERER) {
			return Set.of("steel", "electric"); // Magnemite, Klink, Rotom...: parts made quicker
		}
		if (profession == ModVillagers.SIFTER) {
			return Set.of("ground", "rock"); // Diglett, Sandshrew, Geodude...: sifting goes quicker
		}
		if (profession == ModVillagers.SCHOLAR) {
			return Set.of("psychic"); // Abra, Espeon, Metagross...: research goes quicker
		}
		if (profession == ModVillagers.TEACHER) {
			return Set.of("psychic", "normal"); // Alakazam, Chansey, Blissey...: lessons go quicker
		}
		if (profession == ModVillagers.RANCHER) {
			return Set.of("normal", "ground"); // Tauros, Mudbray, Ponyta's cousins: wild horses calm down quicker
		}
		if (profession == ModVillagers.GEM_GROWER) {
			return Set.of("rock", "steel"); // Roggenrola, Carbink, Bronzor...: the clusters come loose quicker
		}
		if (profession == ModVillagers.VINTNER) {
			return Set.of("grass", "bug", "fairy"); // Bellsprout, Combee, Cutiefly...: the fruit is picked over and pressed quicker
		}
		if (profession == ModVillagers.TAILOR) {
			return Set.of("bug", "normal"); // Spinarak, Sewaddle, Leavanny, Cinccino...: thread spun and cloth held taut
		}
		if (profession == ModVillagers.PRINTER) {
			return Set.of("psychic", "normal"); // Abra, Smeargle, Porygon...: the type set by thought, the ink daubed by tail
		}
		if (profession == ModVillagers.DAYCARE_KEEPER) {
			return Set.of("normal", "fairy"); // Chansey, Blissey, Togekiss...: they keep the pairs company
		}
		if (profession == ModVillagers.HABITAT_KEEPER) {
			return Set.of("flying", "grass"); // Pidgey, Skiploom, Hoothoot...: they scout the wild ones and make the snacks smell sweeter
		}
		if (profession == ModVillagers.CAMP_COOK) {
			return Set.of("fire", "normal"); // Charmander, Snorlax...: the pot cooks quicker
		}
		if (profession == ModVillagers.BERRY_BREEDER) {
			return Set.of("grass", "bug"); // Cherrim, Ribombee, Vivillon...: the berries grow quicker
		}
		if (profession == ModVillagers.FLORIST) {
			return Set.of("grass", "fairy"); // Bellossom, Comfey, Flabébé...
		}
		if (profession == ModVillagers.BEEKEEPER) {
			return Set.of("bug", "grass"); // Combee and friends: quicker harvests
		}
		if (profession == VillagerProfession.CARTOGRAPHER) {
			return Set.of("flying", "ground"); // scouting ahead, digging up finds: shorter searches and rests
		}
		if (profession == ModVillagers.GUARD) {
			return Set.of("fighting", "dragon"); // they join the fight (guard/GuardPartners)
		}
		if (profession == ModVillagers.POSTMAN) {
			return Set.of("flying"); // air mail: parcels for far away go at once instead of at dawn
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
		List<Component> names = site == null ? List.of() : PokemonPartners.EXTENSION.call(p -> p.helpers(level, site, RADIUS, types, max(villager)), List.<Component>of());
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

	/** How much of the usual time the job takes with its helpers (1 without any; never below {@link #FLOOR}). */
	public static float factor(Villager villager) {
		int helping = Math.min(max(villager), helpers(villager).size());
		if (helping == 0) {
			return 1f;
		}
		countDay(villager);
		// Field Notes (the Pokédex tree, 29.21): each partner helps 5% more a level
		return Math.max(FLOOR, 1f - PER_PARTNER * helping * (1f + io.github.jcondedata.aliveworkplace.legend.PokemonProfessor.partnerBonus(villager)));
	}

	/** The least time a job can take with helpers, as a share of the usual. */
	public static final float FLOOR = 0.4f;

	/**
	 * Partners a worker can have: {@link #MAX}, one more for each level of the village's Kinship research, and one more
	 * with the Pokédex tree's Kinship Studies (29.21).
	 */
	public static int max(Villager villager) {
		return MAX + io.github.jcondedata.aliveworkplace.research.Research.level(villager, io.github.jcondedata.aliveworkplace.research.Research.Topic.KINSHIP)
			+ io.github.jcondedata.aliveworkplace.legend.PokemonProfessor.extraPartners(villager);
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
