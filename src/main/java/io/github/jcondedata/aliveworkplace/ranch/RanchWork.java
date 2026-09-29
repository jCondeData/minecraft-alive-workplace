package io.github.jcondedata.aliveworkplace.ranch;

import com.google.common.collect.ImmutableMap;
import io.github.jcondedata.aliveworkplace.build.BuilderBag;
import io.github.jcondedata.aliveworkplace.build.BuilderLevels;
import io.github.jcondedata.aliveworkplace.build.Builders;
import io.github.jcondedata.aliveworkplace.build.SupplyContainers;
import io.github.jcondedata.aliveworkplace.registry.ModAttachments;
import io.github.jcondedata.aliveworkplace.work.Walker;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.WeakHashMap;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.behavior.Behavior;
import net.minecraft.world.entity.ai.behavior.EntityTracker;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.memory.MemoryStatus;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.phys.AABB;
import org.jetbrains.annotations.Nullable;

/**
 * Looking after the animals around a workstation (shepherds, herders): what's dropped is picked up and put in the chests
 * by the workstation, pairs are fed to breed while there are fewer than {@link #CAP} of a kind, and each job has its own
 * work on the animals (shearing, milking...). The pen is everything within {@link #RADIUS} blocks of the workstation.
 */
public abstract class RanchWork extends Behavior<Villager> {
	/** How far from the workstation the animals are looked after. */
	public static int RADIUS = 16;
	/** Animals of a kind kept: no breeding at this many. */
	public static final int CAP = 8;
	protected static final double REACH = 2.5;
	private static final float SPEED = 0.55f;
	private static final int LOOK_EVERY = 40;
	/** Picked-up items are taken to the chests once there's this many stacks (or nothing else to do). */
	private static final int DEPOSIT_STACKS = 4;

	protected enum Task { NONE, DEPOSIT, COLLECT, BREED, TEND }

	private static final Set<Villager> BUSY = Collections.newSetFromMap(new WeakHashMap<>());

	protected final Walker walker = new Walker(SPEED);
	protected Task task = Task.NONE;
	@Nullable
	protected Entity target;
	@Nullable
	private Animal mate;
	@Nullable
	private Item food;
	private int lookTimer;

	protected RanchWork() {
		super(ImmutableMap.of(
			MemoryModuleType.JOB_SITE, MemoryStatus.VALUE_PRESENT,
			MemoryModuleType.WALK_TARGET, MemoryStatus.REGISTERED,
			MemoryModuleType.LOOK_TARGET, MemoryStatus.REGISTERED
		), 1200);
	}

	public static boolean isBusy(Villager villager) {
		synchronized (BUSY) {
			return BUSY.contains(villager);
		}
	}

	protected static void busy(Villager villager, boolean busy) {
		synchronized (BUSY) {
			if (busy) {
				BUSY.add(villager);
			} else {
				BUSY.remove(villager);
			}
		}
	}

	/** Whether this villager does this job. */
	protected abstract boolean isOurs(Villager villager);

	/** The kinds of animal kept (and bred). */
	protected abstract Set<EntityType<?>> herd();

	/** Whether a dropped item is ours to pick up (wool, eggs...). */
	protected abstract boolean isDrop(ItemStack stack);

	/** The next animal to work on (shear, milk...), or null. */
	@Nullable
	protected abstract Entity tendTarget(ServerLevel level, Villager villager, BlockPos station, List<BlockPos> own);

	/**
	 * Gets ready to work on the animals (fetches the tool from the chests): true when ready, false while still at it, null
	 * to give up.
	 */
	@Nullable
	protected Boolean prepare(ServerLevel level, Villager villager, List<BlockPos> own, BuilderBag bag) {
		return true;
	}

	/** Works on {@code animal} (in reach now); returns false when done with it. */
	protected abstract boolean tend(ServerLevel level, Villager villager, Entity animal, List<BlockPos> own, BuilderBag bag);

	/** What's shown above the head for {@code task} (the job's own lines). */
	protected abstract void status(Villager villager, Task task, boolean noChest);

	@Override
	protected boolean checkExtraStartConditions(ServerLevel level, Villager villager) {
		return !villager.isSleeping() && isOurs(villager) && Builders.benchPos(villager).isPresent();
	}

	@Override
	protected boolean canStillUse(ServerLevel level, Villager villager, long gameTime) {
		return checkExtraStartConditions(level, villager);
	}

	@Override
	protected void start(ServerLevel level, Villager villager, long gameTime) {
		walker.reset();
		task = Task.NONE;
		lookTimer = 0;
	}

	@Override
	protected void stop(ServerLevel level, Villager villager, long gameTime) {
		busy(villager, false);
		task = Task.NONE;
		villager.getBrain().eraseMemory(MemoryModuleType.WALK_TARGET);
	}

	@Override
	protected void tick(ServerLevel level, Villager villager, long gameTime) {
		BlockPos station = Builders.benchPos(villager).orElse(null);
		if (station == null) {
			return;
		}
		List<BlockPos> own = SupplyContainers.find(level, station, null);
		if (own.isEmpty()) {
			busy(villager, false);
			status(villager, Task.NONE, true);
			return;
		}
		BuilderBag bag = villager.getAttachedOrCreate(ModAttachments.BUILDER_BAG);
		if (task == Task.NONE) {
			if (--lookTimer > 0) {
				status(villager, Task.NONE, false);
				return;
			}
			lookTimer = LOOK_EVERY;
			choose(level, villager, station, own, bag);
			if (task == Task.NONE) {
				busy(villager, false);
				status(villager, Task.NONE, false);
				return;
			}
			walker.reset();
		}
		busy(villager, true);
		status(villager, task, false);
		switch (task) {
			case DEPOSIT -> deposit(level, villager, own, bag);
			case COLLECT -> collect(level, villager, bag);
			case BREED -> breed(level, villager, own, bag);
			default -> {
				if (target == null || !target.isAlive()) {
					done();
					return;
				}
				Boolean ready = prepare(level, villager, own, bag);
				if (ready == null) {
					done();
				} else if (ready && reach(level, villager, target) && !tend(level, villager, target, own, bag)) {
					done();
				}
			}
		}
	}

	protected void done() {
		task = Task.NONE;
		target = null;
		mate = null;
		lookTimer = 10;
		walker.reset();
	}

	/** What to do next: take what's picked up home, pick up what's dropped, the job's own work, breeding. */
	private void choose(ServerLevel level, Villager villager, BlockPos station, List<BlockPos> own, BuilderBag bag) {
		int carrying = BuilderBag.SLOTS - bag.freeSlots();
		ItemEntity drop = nearestDrop(level, station);
		if (carrying >= DEPOSIT_STACKS || carrying > 0 && drop == null) {
			task = Task.DEPOSIT;
			return;
		}
		if (drop != null) {
			task = Task.COLLECT;
			target = drop;
			return;
		}
		Entity animal = tendTarget(level, villager, station, own);
		if (animal != null) {
			task = Task.TEND;
			target = animal;
			return;
		}
		if (pickPair(level, station, own)) {
			task = Task.BREED;
		}
	}

	/** The animals of the herd around the workstation. */
	protected List<Animal> animals(ServerLevel level, BlockPos station) {
		return level.getEntitiesOfClass(Animal.class, new AABB(station).inflate(RADIUS, 6, RADIUS), a -> a.isAlive() && herd().contains(a.getType()));
	}

	@Nullable
	private ItemEntity nearestDrop(ServerLevel level, BlockPos station) {
		return level.getEntitiesOfClass(ItemEntity.class, new AABB(station).inflate(RADIUS, 6, RADIUS), e -> e.isAlive() && isDrop(e.getItem()))
			.stream().min(Comparator.comparingDouble(e -> e.distanceToSqr(station.getCenter()))).orElse(null);
	}

	/** Two animals of a kind that's short, ready to breed, and food for them in the chests. */
	private boolean pickPair(ServerLevel level, BlockPos station, List<BlockPos> own) {
		List<Animal> all = animals(level, station);
		for (EntityType<?> kind : herd()) {
			List<Animal> ofKind = all.stream().filter(a -> a.getType() == kind).toList();
			if (ofKind.size() >= CAP) {
				continue;
			}
			List<Animal> ready = ofKind.stream().filter(a -> a.getAge() == 0 && a.canFallInLove() && !a.isInLove()).toList();
			if (ready.size() < 2) {
				continue;
			}
			for (var e : SupplyContainers.contents(level, own).entrySet()) {
				if (e.getValue() >= 2 && ready.get(0).isFood(new ItemStack(e.getKey()))) {
					target = ready.get(0);
					mate = ready.get(1);
					food = e.getKey();
					return true;
				}
			}
		}
		return false;
	}

	/** Takes the food, then feeds the two. */
	private void breed(ServerLevel level, Villager villager, List<BlockPos> own, BuilderBag bag) {
		if (bag.count(food) < 2) {
			if (walker.walkTo(level, villager, own.get(0), 3.0)) {
				int got = SupplyContainers.extract(level, own, food, 2 - bag.count(food));
				bag.addAll(food, got);
				if (bag.count(food) < 2) {
					done();
				}
				walker.reset();
			}
			return;
		}
		Animal next = target instanceof Animal a && a.isAlive() && !a.isInLove() ? a : mate != null && mate.isAlive() && !mate.isInLove() ? mate : null;
		if (next == null) {
			BuilderLevels.addXp(level, villager, 1, null);
			done();
			return;
		}
		if (reach(level, villager, next)) {
			if (next.getAge() == 0 && next.canFallInLove() && bag.remove(food, 1) == 1) {
				next.setInLove(null);
				villager.swing(InteractionHand.MAIN_HAND);
			} else {
				done();
			}
			walker.reset();
		}
	}

	/** Picks up a dropped item. */
	private void collect(ServerLevel level, Villager villager, BuilderBag bag) {
		if (!(target instanceof ItemEntity item) || !item.isAlive()) {
			done();
			return;
		}
		if (reach(level, villager, item)) {
			ItemStack rest = bag.add(item.getItem());
			if (rest.isEmpty()) {
				item.discard();
			} else {
				item.setItem(rest);
			}
			level.playSound(null, item.blockPosition(), SoundEvents.ITEM_PICKUP, SoundSource.NEUTRAL, 0.3f, 1.4f);
			done();
		}
	}

	/** Takes what's been picked up to the chests (tools and food stay in the bag's... no: everything goes in). */
	private void deposit(ServerLevel level, Villager villager, List<BlockPos> own, BuilderBag bag) {
		if (!walker.walkTo(level, villager, own.get(0), 3.0)) {
			return;
		}
		for (ItemStack stack : bag.takeAll()) {
			ItemStack rest = SupplyContainers.insert(level, own, stack);
			if (!rest.isEmpty()) {
				Block.popResource(level, own.get(0).above(), rest);
			}
		}
		villager.swing(InteractionHand.MAIN_HAND);
		level.playSound(null, own.get(0), SoundEvents.CHEST_CLOSE, SoundSource.BLOCKS, 0.4f, 1.0f);
		done();
	}

	/** Walks up to an entity; true once it's in reach. */
	protected boolean reach(ServerLevel level, Villager villager, Entity entity) {
		villager.getBrain().setMemory(MemoryModuleType.LOOK_TARGET, new EntityTracker(entity, true));
		if (villager.distanceToSqr(entity) <= REACH * REACH) {
			villager.getBrain().eraseMemory(MemoryModuleType.WALK_TARGET);
			return true;
		}
		walker.walkTo(level, villager, entity.blockPosition(), REACH - 0.5);
		return false;
	}
}
