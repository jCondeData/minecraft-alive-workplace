package io.github.jcondedata.aliveworkplace.hall;

import io.github.jcondedata.aliveworkplace.AliveWorkplace;
import io.github.jcondedata.aliveworkplace.build.SupplyContainers;
import io.github.jcondedata.aliveworkplace.registry.ModAttachments;
import io.github.jcondedata.aliveworkplace.registry.ModVillagers;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.particles.ItemParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.village.poi.PoiManager;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.food.FoodProperties;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.LightLayer;
import org.jetbrains.annotations.Nullable;

/**
 * What a village with a Village Hall needs: every grown villager eats once a day from the store (the chests by the
 * Kitchen Stoves first — the chef's cooking — then the Storehouses), everyone sleeps in a bed of their own, and the
 * village is safe (guards) and lit (a light by the beds); decorations make it prettier. How well the village is kept, 0–100%, sets the pace of all the
 * work there: from 20% slower (nobody fed, housed or safe) through the usual pace at 50% to 25% faster when everything's
 * right. Villages without a hall work at the usual pace.
 */
public final class VillageNeeds {
	/** A villager eats once a day. */
	public static final long DAY = 24000;
	/** How often a hall feeds the hungry and recounts. */
	public static int CHECK_EVERY = 600;
	/** Work delay when everything's right (25% faster) and when nothing is (20% slower). */
	static final float BEST = 0.8f;
	static final float WORST = 1.25f;
	/** Block light by a villager's bed that counts as lit (a torch within a few blocks). */
	static final int LIT = 8;
	/** One guard is enough for this many villagers. */
	static final int VILLAGERS_PER_GUARD = 10;
	/** Food that isn't a meal for a villager (golden food, food that makes you ill, berries kept for Pokémon...). */
	public static final TagKey<Item> NOT_A_MEAL = TagKey.create(Registries.ITEM, AliveWorkplace.id("not_villager_food"));
	/** How long a villager's pace is remembered before the hall is asked again. */
	private static final long PACE_TICKS = 200;

	/**
	 * How the village is doing: grown-ups fed in the last day, villagers with a bed, lit beds, guards, beauty (from
	 * decorations, see {@link Decorations}); and the overall 0–1.
	 */
	public record Needs(int adults, int fed, int villagers, int housed, int lit, int guards, int beauty, float wellbeing) {
		/** The work delay multiplier this makes. */
		public float factor() {
			return VillageNeeds.factor(wellbeing);
		}

		/** How much faster (positive) or slower (negative) the village works, in percent. */
		public int pacePercent() {
			return Math.round((1f / factor() - 1f) * 100f);
		}
	}

	private record Pace(float factor, long until) {
	}

	private static final Map<Villager, Pace> PACE = new WeakHashMap<>();

	/** The work delay multiplier for a village kept this well (0–1). */
	public static float factor(float wellbeing) {
		float w = Math.max(0f, Math.min(1f, wellbeing));
		return w >= 0.5f ? 1f - (1f - BEST) * (w - 0.5f) / 0.5f : 1f + (WORST - 1f) * (0.5f - w) / 0.5f;
	}

	/** How well kept a village is, from its fed, housed and safe shares (each 0–1). */
	public static float wellbeing(float fed, float housed, float safe) {
		return 0.4f * fed + 0.3f * housed + 0.3f * safe;
	}

	/** The work delay multiplier for {@code villager}: their village's, or 1 without a Village Hall nearby. */
	public static float factor(Villager villager) {
		if (!(villager.level() instanceof ServerLevel level)) {
			return 1f;
		}
		long now = level.getGameTime();
		Pace pace = PACE.get(villager);
		if (pace != null && now < pace.until()) {
			return pace.factor();
		}
		float factor = VillageHalls.nearest(level, villager.blockPosition())
			.map(pos -> level.getBlockEntity(pos) instanceof VillageHallBlockEntity hall
				? (hall.needs() != null ? hall.needs().factor() : 1f) * swiftHands(hall.research().level(io.github.jcondedata.aliveworkplace.research.Research.Topic.SWIFT_HANDS))
				: 1f)
			.orElse(1f);
		PACE.put(villager, new Pace(factor, now + PACE_TICKS));
		return factor;
	}

	/** The work delay multiplier from the Swift Hands research: 5% faster a level. */
	static float swiftHands(int level) {
		return 1f / (1f + 0.05f * level);
	}

	/** Forget the villagers' paces (tests). */
	public static void forget() {
		PACE.clear();
	}

	/** The hall's round: whoever hasn't eaten for a day eats from the store; then the village is counted. */
	public static Needs check(ServerLevel level, BlockPos hall) {
		long now = level.getGameTime();
		List<BlockPos> store = null;
		List<Villager> everyone = level.getEntitiesOfClass(Villager.class, VillageHalls.area(hall), Villager::isAlive);
		io.github.jcondedata.aliveworkplace.people.Names.nameEveryone(everyone);
		for (Villager villager : everyone) {
			if (villager.isBaby()) {
				continue;
			}
			Long meal = villager.getAttached(ModAttachments.LAST_MEAL);
			if (meal == null) {
				villager.setAttached(ModAttachments.LAST_MEAL, now); // new to the village: they ate before they came
			} else if (now - meal >= io.github.jcondedata.aliveworkplace.people.Traits.mealEvery(villager, DAY)) {
				if (store == null) {
					store = store(level, hall);
				}
				eat(level, villager, store);
			}
		}
		return count(level, hall);
	}

	/** How the village round {@code hall} is doing right now (nothing eaten). */
	public static Needs count(ServerLevel level, BlockPos hall) {
		long now = level.getGameTime();
		int adults = 0;
		int fed = 0;
		int villagers = 0;
		int housed = 0;
		int lit = 0;
		int guards = 0;
		int cheerful = 0;
		for (Villager villager : level.getEntitiesOfClass(Villager.class, VillageHalls.area(hall), Villager::isAlive)) {
			villagers++;
			if (!villager.isBaby()) {
				adults++;
				if (io.github.jcondedata.aliveworkplace.people.Traits.has(villager, io.github.jcondedata.aliveworkplace.people.Traits.Trait.CHEERFUL)) {
					cheerful++;
				}
				if (!isHungry(villager, now)) {
					fed++;
				}
				if (villager.getVillagerData().getProfession() == ModVillagers.GUARD) {
					guards++;
				}
			}
			BlockPos bed = bed(level, villager);
			if (bed != null) {
				housed++;
			}
			if (level.getBrightness(LightLayer.BLOCK, bed != null ? bed : villager.blockPosition()) >= LIT) {
				lit++;
			}
		}
		float fedShare = adults == 0 ? 1f : fed / (float) adults;
		float housedShare = villagers == 0 ? 1f : housed / (float) villagers;
		float guarded = Math.min(1f, guards / (float) Math.max(1, (villagers + VILLAGERS_PER_GUARD - 1) / VILLAGERS_PER_GUARD));
		float litShare = villagers == 0 ? 1f : lit / (float) villagers;
		int hearth = level.getBlockEntity(hall) instanceof VillageHallBlockEntity entity
			? entity.research().level(io.github.jcondedata.aliveworkplace.research.Research.Topic.HEARTH) : 0;
		int beauty = Decorations.beauty(level, hall);
		float cheer = Math.min(io.github.jcondedata.aliveworkplace.people.Traits.MAX_CHEER, cheerful * io.github.jcondedata.aliveworkplace.people.Traits.CHEER);
		float wellbeing = Math.min(1f, wellbeing(fedShare, housedShare, 0.5f * guarded + 0.5f * litShare) + 0.1f * hearth + Decorations.bonus(beauty) + cheer);
		return new Needs(adults, fed, villagers, housed, lit, guards, beauty, wellbeing);
	}

	/** A grown villager who hasn't eaten in the last day. */
	public static boolean isHungry(Villager villager, long now) {
		Long meal = villager.getAttached(ModAttachments.LAST_MEAL);
		return !villager.isBaby() && meal != null && now - meal >= io.github.jcondedata.aliveworkplace.people.Traits.mealEvery(villager, DAY);
	}

	/** The bed a villager sleeps in (in this dimension), or null. */
	@Nullable
	public static BlockPos bed(ServerLevel level, Villager villager) {
		GlobalPos home = villager.getBrain().getMemory(MemoryModuleType.HOME).orElse(null);
		return home != null && home.dimension().equals(level.dimension()) ? home.pos() : null;
	}

	/** Food a villager eats: something to eat with no effects (not golden, not rotten), and not in {@link #NOT_A_MEAL}. */
	public static boolean isMeal(ItemStack stack) {
		FoodProperties food = stack.get(DataComponents.FOOD);
		return food != null && food.effects().isEmpty() && !stack.is(NOT_A_MEAL);
	}

	/** The store: the chests by the village's Kitchen Stoves, then by its Storehouses. */
	static List<BlockPos> store(ServerLevel level, BlockPos hall) {
		Set<BlockPos> chests = new LinkedHashSet<>();
		PoiManager poi = level.getPoiManager();
		poi.findAll(h -> h.is(ModVillagers.KITCHEN_STOVE_POI), p -> true, hall, VillageHalls.RADIUS, PoiManager.Occupancy.ANY)
			.forEach(stove -> chests.addAll(SupplyContainers.find(level, stove.immutable(), null)));
		poi.findAll(h -> h.is(ModVillagers.STOREHOUSE_POI), p -> true, hall, VillageHalls.RADIUS, PoiManager.Occupancy.ANY)
			.forEach(storehouse -> chests.addAll(SupplyContainers.find(level, storehouse.immutable(), null)));
		return new ArrayList<>(chests);
	}

	/** {@code villager} eats one meal from the store; false if there's nothing to eat. */
	static boolean eat(ServerLevel level, Villager villager, List<BlockPos> store) {
		ItemStack meal = SupplyContainers.takeOne(level, store, VillageNeeds::isMeal);
		if (meal.isEmpty()) {
			return false;
		}
		villager.setAttached(ModAttachments.LAST_MEAL, level.getGameTime());
		villager.heal(4f);
		level.sendParticles(new ItemParticleOption(ParticleTypes.ITEM, meal), villager.getX(), villager.getEyeY() - 0.2, villager.getZ(),
			8, 0.15, 0.1, 0.15, 0.05);
		level.playSound(null, villager.blockPosition(), SoundEvents.GENERIC_EAT, SoundSource.NEUTRAL, 0.7f, 1f);
		return true;
	}

	private VillageNeeds() {
	}
}
