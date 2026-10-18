package io.github.jcondedata.aliveworkplace.jeweller;

import io.github.jcondedata.aliveworkplace.AliveWorkplace;
import io.github.jcondedata.aliveworkplace.craft.LuxuryRecipes;
import io.github.jcondedata.aliveworkplace.cup.CupDays;
import io.github.jcondedata.aliveworkplace.hall.VillageHalls;
import io.github.jcondedata.aliveworkplace.trainer.MegaBracelets;
import io.github.jcondedata.aliveworkplace.trainer.Trainers;
import io.github.jcondedata.aliveworkplace.wonder.StadiumDays;
import io.github.jcondedata.aliveworkplace.work.Extension;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.AABB;

/**
 * Mega Stones and Key Stones from the village (ROADMAP 38.6, Mega Showdown). The Jeweller cuts them through the luxury
 * recipes ({@code data/aliveworkplace/luxury_recipes/mega_*.json}, loaded only with Mega Showdown): a Key Stone from Key
 * Stone ore, and from a Mega Stone Crystal (its plain Mega Stone, or four Mega meteorite ores) the Mega Stone of a Pokémon
 * the village's trainers use that the store doesn't hold yet ({@link #CHOOSER}). The Miner carries what they dig of those
 * ({@link #FINDS}) to the village store instead of their own chests. All behind {@code villageMegas} ({@link #SWITCH})
 * and the Jeweller ({@code jewellers}, M34): with either off the recipes simply don't run and the Miner keeps everything.
 */
public final class MegaStones {
	/** The recipes' switch ({@code "when": "village_megas"}). */
	public static final String SWITCH = "village_megas";
	/** The Mega Stone recipes' chooser ({@code "for": "village_trainers"}). */
	public static final String CHOOSER = "village_trainers";
	/** What a Miner takes to the village store for the Jeweller: Key Stone ore, Mega Stone Crystals, Mega meteorite ores. */
	public static final TagKey<Item> FINDS = TagKey.create(Registries.ITEM, AliveWorkplace.id("jeweller_finds"));
	/** How far round the Jeweller's station trainers count as the village's when there's no Village Hall. */
	static final int REACH = 48;

	/** The Mega Stones (item ids) for the Pokémon on a trainer's team: filled by Cobblemon with Mega Showdown. */
	public interface TeamStones {
		List<ResourceLocation> of(Villager trainer);

		Extension<TeamStones> EXTENSION = new Extension<>("Mega Stones of a trainer's team");
	}

	/** A trainer's stones, worked out for this tier and Mega (a team is built from its seed, so it's the same until those change). */
	private record Known(int tier, boolean mega, List<Item> stones) {
	}

	private static final Map<UUID, Known> KNOWN = new ConcurrentHashMap<>();

	public static void init() {
		LuxuryRecipes.when(SWITCH, MegaStones::on);
		LuxuryRecipes.chooser(CHOOSER, MegaStones::forVillageOf);
	}

	/** Whether the village's Mega Stones are on: Mega Showdown installed, {@code villageMegas} on and Jewellers on. */
	public static boolean on() {
		return MegaBracelets.extraMegas() && Jewellers.ENABLED;
	}

	/** Whether a Miner takes {@code stack} to the village store for the Jeweller. */
	public static boolean forTheJeweller(ItemStack stack) {
		return on() && stack.is(FINDS);
	}

	/** The Mega Stones of the Pokémon the trainers of {@code maker}'s village use, in the trainers' order (nearest first). */
	public static List<Item> forVillageOf(Villager maker) {
		if (!on() || !(maker.level() instanceof ServerLevel level)) {
			return List.of();
		}
		List<Item> out = new ArrayList<>();
		for (Villager trainer : trainers(level, maker.blockPosition())) {
			for (Item stone : stonesOf(trainer)) {
				if (!out.contains(stone)) {
					out.add(stone);
				}
			}
		}
		return out;
	}

	/** The village's trainers: those round the nearest Village Hall, or without one those near {@code pos}; nearest first. */
	public static List<Villager> trainers(ServerLevel level, BlockPos pos) {
		Optional<BlockPos> hall = VillageHalls.nearest(level, pos);
		AABB area = hall.map(VillageHalls::area).orElseGet(() -> new AABB(pos).inflate(REACH));
		List<Villager> out = new ArrayList<>(level.getEntitiesOfClass(Villager.class, area,
			v -> v.isAlive() && Trainers.isTrainer(v) && !StadiumDays.isVisitor(v) && !CupDays.isDelegate(v)));
		out.sort(Comparator.comparingDouble(v -> v.blockPosition().distSqr(pos)));
		return out;
	}

	/** The Mega Stones of the Pokémon on {@code trainer}'s team (none without Cobblemon and Mega Showdown). */
	public static List<Item> stonesOf(Villager trainer) {
		int tier = Trainers.tier(trainer);
		boolean mega = MegaBracelets.canMegaEvolve(trainer);
		Known known = KNOWN.get(trainer.getUUID());
		if (known != null && known.tier() == tier && known.mega() == mega) {
			return known.stones();
		}
		List<Item> stones = new ArrayList<>();
		for (ResourceLocation id : TeamStones.EXTENSION.call(t -> t.of(trainer), List.<ResourceLocation>of())) {
			BuiltInRegistries.ITEM.getOptional(id).filter(i -> !stones.contains(i)).ifPresent(stones::add);
		}
		List<Item> result = List.copyOf(stones);
		KNOWN.put(trainer.getUUID(), new Known(tier, mega, result));
		return result;
	}

	/** Forgets the stones worked out (tests). */
	public static void forget() {
		KNOWN.clear();
	}

	private MegaStones() {
	}
}
