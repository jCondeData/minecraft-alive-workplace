package io.github.jcondedata.aliveworkplace.ranch;

import io.github.jcondedata.aliveworkplace.build.BuilderBag;
import io.github.jcondedata.aliveworkplace.build.BuilderLevels;
import io.github.jcondedata.aliveworkplace.build.SupplyContainers;
import io.github.jcondedata.aliveworkplace.registry.ModAttachments;
import io.github.jcondedata.aliveworkplace.work.WorkerStatus;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.entity.animal.Cow;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.npc.VillagerProfession;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.jetbrains.annotations.Nullable;

/**
 * A Butcher's shift as the village's herder (at their smoker): the cows, pigs, chickens and rabbits around it are bred
 * up to {@link RanchWork#CAP} of each with food from the chests, eggs and whatever else is dropped are picked up, and cows
 * are milked into the buckets in the chests (while there are fewer than {@link #MILK} milk buckets). A herder someone
 * hired also keeps each kind at that number: from {@link #CULL_ABOVE} grown ones, the rest go for meat and leather —
 * never babies, named or leashed animals.
 */
public class HerderWork extends RanchWork {
	static final Set<EntityType<?>> HERD = Set.of(EntityType.COW, EntityType.MOOSHROOM, EntityType.PIG, EntityType.CHICKEN, EntityType.RABBIT);
	/** Milk buckets kept in the chests. */
	static final int MILK = 4;
	/** A hired herder takes grown animals of a kind for meat above this many. */
	static final int CULL_ABOVE = CAP + 2;

	private boolean culling;

	public static boolean isHerder(Villager villager) {
		return !villager.isBaby() && villager.getVillagerData().getProfession() == VillagerProfession.BUTCHER;
	}

	@Override
	protected boolean isOurs(Villager villager) {
		return isHerder(villager);
	}

	@Override
	protected Set<EntityType<?>> herd() {
		return HERD;
	}

	@Override
	protected boolean isDrop(ItemStack stack) {
		return stack.is(Items.EGG) || stack.is(Items.FEATHER) || stack.is(Items.LEATHER) || stack.is(Items.RABBIT_HIDE) || stack.is(Items.RABBIT_FOOT)
			|| stack.is(Items.BEEF) || stack.is(Items.PORKCHOP) || stack.is(Items.CHICKEN) || stack.is(Items.RABBIT) || stack.is(Items.COOKED_BEEF)
			|| stack.is(Items.COOKED_PORKCHOP) || stack.is(Items.COOKED_CHICKEN) || stack.is(Items.COOKED_RABBIT);
	}

	@Nullable
	@Override
	protected Entity tendTarget(ServerLevel level, Villager villager, BlockPos station, List<BlockPos> own) {
		List<Animal> animals = animals(level, station);
		// A hired herder keeps the herd from growing past the pen.
		if (villager.getAttached(ModAttachments.BUILDER_EMPLOYER) != null) {
			Map<EntityType<?>, List<Animal>> grown = animals.stream().filter(a -> !a.isBaby())
				.collect(Collectors.groupingBy(Animal::getType));
			for (List<Animal> kind : grown.values()) {
				if (kind.size() > CULL_ABOVE) {
					Animal one = kind.stream().filter(a -> !a.hasCustomName() && !a.isLeashed() && !a.isInLove())
						.min(Comparator.comparingDouble(villager::distanceToSqr)).orElse(null);
					if (one != null) {
						culling = true;
						return one;
					}
				}
			}
		}
		// Milk, while there are empty buckets and not much milk yet.
		if (SupplyContainers.count(level, own, Items.MILK_BUCKET) < MILK && SupplyContainers.firstWith(level, own, Items.BUCKET) != null) {
			Animal cow = animals.stream().filter(a -> a instanceof Cow && !a.isBaby()).min(Comparator.comparingDouble(villager::distanceToSqr)).orElse(null);
			if (cow != null) {
				culling = false;
				return cow;
			}
		}
		return null;
	}

	/** An empty bucket in the bag before going to milk. */
	@Nullable
	@Override
	protected Boolean prepare(ServerLevel level, Villager villager, List<BlockPos> own, BuilderBag bag) {
		if (culling || bag.count(Items.BUCKET) > 0) {
			return true;
		}
		BlockPos chest = SupplyContainers.firstWith(level, own, Items.BUCKET);
		if (chest == null) {
			return null;
		}
		if (!walker.walkTo(level, villager, chest, 3.0)) {
			return false;
		}
		int got = SupplyContainers.extract(level, own, Items.BUCKET, 1);
		bag.addAll(Items.BUCKET, got);
		walker.reset();
		return got > 0 ? true : null;
	}

	@Override
	protected boolean tend(ServerLevel level, Villager villager, Entity animal, List<BlockPos> own, BuilderBag bag) {
		if (culling) {
			if (animal instanceof Animal a && !a.isBaby() && !a.hasCustomName() && !a.isLeashed()) {
				villager.swing(InteractionHand.MAIN_HAND);
				a.hurt(level.damageSources().mobAttack(villager), a.getMaxHealth() * 4);
				BuilderLevels.addXp(level, villager, 1, null);
			}
			return false;
		}
		if (animal instanceof Cow cow && !cow.isBaby() && bag.remove(Items.BUCKET, 1) == 1) {
			bag.add(new ItemStack(Items.MILK_BUCKET));
			villager.swing(InteractionHand.MAIN_HAND);
			level.playSound(null, cow.blockPosition(), SoundEvents.COW_MILK, SoundSource.NEUTRAL, 1f, 1f);
			villager.setAttached(ModAttachments.MILK_COLLECTED, villager.getAttachedOrElse(ModAttachments.MILK_COLLECTED, 0) + 1);
			BuilderLevels.addXp(level, villager, 1, null);
		}
		return false;
	}

	@Override
	protected void status(Villager villager, Task task, boolean noChest) {
		Component title = Component.translatable("message.aliveworkplace.herder.title", villager.getAttachedOrElse(ModAttachments.MILK_COLLECTED, 0));
		String state = noChest ? "no_chest" : task == Task.TEND ? (culling ? "culling" : "milking") : task.name().toLowerCase();
		WorkerStatus.set(villager, title, -1f, Component.translatable("message.aliveworkplace.herder.state." + state)
			.withStyle(noChest ? ChatFormatting.YELLOW : ChatFormatting.GRAY));
	}

	/** Foods the herd is bred with (kept in the chests by the porter). */
	public static boolean isBreedingFood(Item item) {
		return item == Items.WHEAT || item == Items.CARROT || item == Items.POTATO || item == Items.BEETROOT || item == Items.WHEAT_SEEDS
			|| item == Items.BEETROOT_SEEDS || item == Items.MELON_SEEDS || item == Items.PUMPKIN_SEEDS || item == Items.DANDELION
			|| item == Items.GOLDEN_CARROT;
	}
}
