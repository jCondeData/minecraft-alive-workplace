package io.github.jcondedata.aliveworkplace.hall;

import io.github.jcondedata.aliveworkplace.AliveWorkplace;
import io.github.jcondedata.aliveworkplace.build.BuildSiteManager;
import io.github.jcondedata.aliveworkplace.build.SupplyContainers;
import io.github.jcondedata.aliveworkplace.registry.ModAttachments;
import java.util.List;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ItemParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.food.FoodProperties;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

/**
 * The Noble's Ball (ROADMAP 34.7): in a village with a Noble household (the noble class file's {@code festival} effect),
 * every other festival is a ball in its place. The guests gather at the Manor (34.16) or else the hall; each grown guest
 * is served a glass of wine from the store ({@code #aliveworkplace:ball_wine}) and the store's best food; the fireworks at
 * dusk are gold; everyone who came is {@link #MOOD} happier for {@link #MOOD_DAYS} days; and players there are Heroes of the
 * Village for the night. Saved on the hall: {@code ballTurn} (whether the next festival is a ball) and {@code ballDay}.
 */
public final class NobleBalls {
	public static final int MOOD = 15;
	public static final int MOOD_DAYS = 3;
	public static final TagKey<Item> WINE = TagKey.create(Registries.ITEM, AliveWorkplace.id("ball_wine"));
	/** The Manor (34.16) holds the ball when the village has one finished. */
	public static final ResourceLocation MANOR = AliveWorkplace.id("manor");
	/** Gold, pale gold and white: the ball's fireworks. */
	public static final int[] GOLD = {0xF5C542, 0xFFD966, 0xE8B923, 0xFFFFFF};

	/**
	 * A festival is planned for {@code day}: in a village with a Noble household it is a ball every other time (the hall's
	 * {@code ballTurn} flips); returns whether this one is.
	 */
	public static boolean decide(ServerLevel level, BlockPos hall, VillageHallBlockEntity entity, long day) {
		if (io.github.jcondedata.aliveworkplace.people.ClassPerks.ballEvery(level, hall) <= 0) {
			return false;
		}
		boolean ball = entity.ballTurn();
		entity.setBallTurn(!ball);
		if (ball) {
			entity.setBallDay(day);
		}
		return ball;
	}

	/** True if the festival of {@code entity}'s village on {@code day} is a Noble's Ball. */
	public static boolean isBall(VillageHallBlockEntity entity, long day) {
		return entity.ballDay() == day && entity.festivalDay() == day;
	}

	/** True if today's festival in the village round {@code hall} is a ball. */
	public static boolean today(ServerLevel level, BlockPos hall) {
		return level.getBlockEntity(hall) instanceof VillageHallBlockEntity entity && isBall(entity, Chronicle.day(level));
	}

	/** Where the guests gather: the middle of a finished Manor in the village (in any style), else the hall. */
	public static BlockPos venue(ServerLevel level, BlockPos hall) {
		for (BuildSiteManager.Finished f : BuildSiteManager.get(level).finishedNear(level, hall, VillageHalls.RADIUS)) {
			if (io.github.jcondedata.aliveworkplace.blueprint.BlueprintStyles.base(f.structure()).equals(MANOR)) {
				net.minecraft.core.Vec3i size = io.github.jcondedata.aliveworkplace.blueprint.BlueprintLibrary.get(level, f.structure())
					.map(b -> b.size()).orElse(net.minecraft.core.Vec3i.ZERO);
				return f.placement().origin().offset(new BlockPos(size.getX() / 2, 1, size.getZ() / 2).rotate(f.placement().rotation()));
			}
		}
		return hall;
	}

	/** True if {@code villager}'s last festival was a ball (the day they came is the hall's ball day). */
	public static boolean cameToBall(Villager villager) {
		Long day = ModAttachments.FESTIVAL_DAY.get(villager);
		VillageHallBlockEntity entity = day == null ? null : CivicEffects.hallOf(villager);
		return entity != null && entity.ballDay() == day;
	}

	/** Each grown guest's glass of wine and the store's best food; returns how many were served wine. */
	public static int serve(ServerLevel level, List<Villager> guests, List<BlockPos> store) {
		int wine = 0;
		for (Villager v : guests) {
			if (v.isBaby()) {
				continue;
			}
			ItemStack glass = SupplyContainers.takeOne(level, store, s -> s.is(WINE));
			if (!glass.isEmpty()) {
				wine++;
				level.sendParticles(new ItemParticleOption(ParticleTypes.ITEM, glass), v.getX(), v.getEyeY() - 0.2, v.getZ(), 6, 0.15, 0.1, 0.15, 0.05);
				level.playSound(null, v.blockPosition(), SoundEvents.GENERIC_DRINK, SoundSource.NEUTRAL, 0.6f, 1.1f);
			}
		}
		return wine;
	}

	/** The meal in {@code store} that fills most (the ball's fare), or null if it holds none. */
	@Nullable
	public static Item bestMeal(ServerLevel level, List<BlockPos> store) {
		Item best = null;
		int most = -1;
		for (Map.Entry<Item, Long> e : SupplyContainers.contents(level, store).entrySet()) {
			ItemStack stack = new ItemStack(e.getKey());
			FoodProperties food = stack.get(DataComponents.FOOD);
			if (e.getValue() > 0 && food != null && VillageNeeds.isMeal(stack) && food.nutrition() > most) {
				most = food.nutrition();
				best = e.getKey();
			}
		}
		return best;
	}

	/** {@code villager} eats the store's best food at the ball; false if the store has none. */
	public static boolean feast(ServerLevel level, Villager villager, List<BlockPos> store) {
		Item best = bestMeal(level, store);
		if (best == null) {
			return false;
		}
		ItemStack meal = SupplyContainers.takeOne(level, store, s -> s.is(best));
		if (meal.isEmpty()) {
			return false;
		}
		io.github.jcondedata.aliveworkplace.people.Diet.ate(villager, meal);
		ModAttachments.LAST_MEAL.set(villager, level.getGameTime());
		villager.heal(4f);
		level.sendParticles(new ItemParticleOption(ParticleTypes.ITEM, meal), villager.getX(), villager.getEyeY() - 0.2, villager.getZ(), 8, 0.15, 0.1, 0.15, 0.05);
		level.playSound(null, villager.blockPosition(), SoundEvents.GENERIC_EAT, SoundSource.NEUTRAL, 0.7f, 1f);
		return true;
	}

	private NobleBalls() {
	}
}
