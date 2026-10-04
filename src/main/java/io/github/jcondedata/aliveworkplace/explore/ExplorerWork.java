package io.github.jcondedata.aliveworkplace.explore;

import com.google.common.collect.ImmutableMap;
import io.github.jcondedata.aliveworkplace.build.BuilderBag;
import io.github.jcondedata.aliveworkplace.build.BuilderLevels;
import io.github.jcondedata.aliveworkplace.build.Builders;
import io.github.jcondedata.aliveworkplace.build.SupplyContainers;
import io.github.jcondedata.aliveworkplace.registry.ModAttachments;
import io.github.jcondedata.aliveworkplace.work.Partners;
import io.github.jcondedata.aliveworkplace.work.Village;
import io.github.jcondedata.aliveworkplace.work.Walker;
import io.github.jcondedata.aliveworkplace.work.WorkerStatus;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.WeakHashMap;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.particles.ItemParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.ai.behavior.Behavior;
import net.minecraft.world.entity.ai.behavior.BlockPosTracker;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.memory.MemoryStatus;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

/**
 * A Cartographer's shift as the village's explorer: with food from the chests by the cartography table (one ration a
 * stop) and, if there is one, a sword or axe, they set out on an expedition — a string of stops out in the land around
 * the village (within {@link #RANGE} blocks of the table, only where the world is running), searching each for what
 * the land has ({@link Explorers#finds}) — then come home and put what they found in the chests. Every
 * {@link #MAP_EVERY}th expedition, with an empty map in the chests, they draw a map to a place nearby. Between
 * expeditions they rest (and do the vanilla Cartographer's day). Adapted in spirit from MineColonies' expeditions; the
 * code is ours.
 */
public class ExplorerWork extends Behavior<Villager> {
	/** How far from the cartography table an expedition goes. */
	public static int RANGE = 48;
	/** How far apart the stops are. */
	public static int MIN_HOP = 10;
	public static int MAX_HOP = 24;
	/** How long a stop's search takes (before levels and partners). */
	public static int SEARCH_TICKS = 100;
	/** The rest between expeditions. */
	public static int REST_TICKS = 1200;
	/** Rations needed to set out. */
	static final int MIN_FOOD = 2;
	/** Every so many expeditions, a map to somewhere (with an empty map in the chests). */
	static final int MAP_EVERY = 3;
	private static final int GIVE_UP_TICKS = 300;
	private static final int LOOK_EVERY = 100;
	private static final float SPEED = 0.6f;
	private static final double REACH = 3.0;

	private enum Phase { IDLE, PACKING, OUT, SEARCHING, HOME }

	private static final Set<Villager> BUSY = Collections.newSetFromMap(new WeakHashMap<>());

	private final Walker walker = new Walker(SPEED);
	private Phase phase = Phase.IDLE;
	private int stops;
	private int stopsDone;
	private int failures;
	@Nullable
	private BlockPos target;
	private final List<BlockPos> visited = new ArrayList<>();
	private int timer;
	private int lookTimer;
	private int stuckTicks;
	private double bestDistance;
	private int retryWait;
	private long restUntil;

	public ExplorerWork() {
		super(ImmutableMap.of(
			MemoryModuleType.JOB_SITE, MemoryStatus.VALUE_PRESENT,
			MemoryModuleType.WALK_TARGET, MemoryStatus.REGISTERED,
			MemoryModuleType.LOOK_TARGET, MemoryStatus.REGISTERED
		), 1200);
	}

	/** Whether this explorer is out on (or packing for, or back from) an expedition: vanilla's routine waits. */
	public static boolean isBusy(Villager villager) {
		synchronized (BUSY) {
			return BUSY.contains(villager);
		}
	}

	private static void busy(Villager villager, boolean busy) {
		synchronized (BUSY) {
			if (busy) {
				BUSY.add(villager);
			} else {
				BUSY.remove(villager);
			}
		}
	}

	@Override
	protected boolean checkExtraStartConditions(ServerLevel level, Villager villager) {
		return !villager.isSleeping() && Explorers.isExplorer(villager) && Builders.benchPos(villager).isPresent();
	}

	@Override
	protected boolean canStillUse(ServerLevel level, Villager villager, long gameTime) {
		return checkExtraStartConditions(level, villager);
	}

	@Override
	protected void start(ServerLevel level, Villager villager, long gameTime) {
		walker.reset();
		lookTimer = 0;
		stuckTicks = 0;
	}

	@Override
	protected void stop(ServerLevel level, Villager villager, long gameTime) {
		// The expedition goes on next shift (what was found is in the bag, saved with the villager).
		busy(villager, false);
		villager.getBrain().eraseMemory(MemoryModuleType.WALK_TARGET);
	}

	@Override
	protected void tick(ServerLevel level, Villager villager, long gameTime) {
		BlockPos station = Builders.benchPos(villager).orElse(null);
		if (station == null) {
			return;
		}
		BuilderBag bag = ModAttachments.BUILDER_BAG.getOrCreate(villager);
		switch (phase) {
			case IDLE -> idle(level, villager, station, bag, gameTime);
			case PACKING -> pack(level, villager, station, bag);
			case OUT -> travel(level, villager, station, bag);
			case SEARCHING -> search(level, villager, bag);
			case HOME -> home(level, villager, station, bag, gameTime);
		}
	}

	/** Between expeditions: unpack anything still in the bag, rest, then set out if there's food. */
	private void idle(ServerLevel level, Villager villager, BlockPos station, BuilderBag bag, long now) {
		if (!bag.isEmpty()) {
			phase = Phase.HOME; // back from an expedition cut short (a restart, the night)
			busy(villager, true);
			return;
		}
		busy(villager, false);
		if (now < restUntil) {
			status(villager, "resting");
			return;
		}
		if (--lookTimer > 0) {
			return;
		}
		lookTimer = LOOK_EVERY;
		List<BlockPos> own = SupplyContainers.find(level, station, null);
		if (own.isEmpty()) {
			status(villager, "no_chest");
			return;
		}
		if (foodCount(level, villager, station, own) < MIN_FOOD) {
			status(villager, "needs_food");
			return;
		}
		phase = Phase.PACKING;
		busy(villager, true);
		walker.reset();
	}

	/** Food in the chests by the table, and at the village-mates' (the chef's kitchen). */
	private static int foodCount(ServerLevel level, Villager villager, BlockPos station, List<BlockPos> own) {
		int count = 0;
		for (BlockPos chest : Village.allChests(level, villager, station, null)) {
			for (ItemStack stack : SupplyContainers.peekMatching(level, chest, Explorers::isFood)) {
				count += stack.getCount();
				if (count >= MIN_FOOD) {
					return count;
				}
			}
		}
		return count;
	}

	/** At the chests: a ration for every stop and the best weapon there is. */
	private void pack(ServerLevel level, Villager villager, BlockPos station, BuilderBag bag) {
		status(villager, "packing");
		List<BlockPos> chests = Village.allChests(level, villager, station, null);
		BlockPos chest = SupplyContainers.firstMatching(level, chests, Explorers::isFood);
		if (chest == null) {
			phase = Phase.IDLE;
			busy(villager, false);
			return;
		}
		if (!walker.walkTo(level, villager, chest, REACH)) {
			return;
		}
		int wanted = 3 + BuilderLevels.level(villager);
		int rations = 0;
		while (rations < wanted) {
			ItemStack food = SupplyContainers.takeOne(level, List.of(chest), Explorers::isFood);
			if (food.isEmpty()) {
				food = SupplyContainers.takeOne(level, chests, Explorers::isFood);
			}
			if (food.isEmpty()) {
				break;
			}
			bag.add(food);
			rations++;
		}
		ItemStack weapon = SupplyContainers.takeOne(level, SupplyContainers.find(level, station, null), Explorers::isWeapon);
		if (!weapon.isEmpty()) {
			bag.add(weapon);
		}
		villager.swing(InteractionHand.MAIN_HAND);
		level.playSound(null, chest, SoundEvents.CHEST_OPEN, SoundSource.BLOCKS, 0.4f, 1.1f);
		stops = rations;
		stopsDone = 0;
		failures = 0;
		visited.clear();
		target = null;
		phase = rations >= MIN_FOOD ? Phase.OUT : Phase.HOME;
		if (phase == Phase.OUT) {
			// A Flying or Ground partner scouts ahead as they set out (ROADMAP 28.6).
			io.github.jcondedata.aliveworkplace.work.PartnerShows.cue(villager, "set_out", villager.blockPosition().relative(villager.getDirection(), 6));
		}
		walker.reset();
	}

	/** On the way to the next stop. */
	private void travel(ServerLevel level, Villager villager, BlockPos station, BuilderBag bag) {
		status(villager, "out", stopsDone + 1, stops);
		if (target == null) {
			target = pickStop(level, villager, station);
			stuckTicks = 0;
			bestDistance = Double.MAX_VALUE;
			if (target == null) {
				if (++failures > 5) {
					phase = Phase.HOME; // nowhere left to go round here
				}
				return;
			}
		}
		double distance = villager.position().distanceTo(Vec3.atBottomCenterOf(target));
		if (distance <= 2.5) {
			villager.getBrain().eraseMemory(MemoryModuleType.WALK_TARGET);
			phase = Phase.SEARCHING;
			timer = -1;
			return;
		}
		retryWait = Walker.requestWalk(villager, target, SPEED, 1, retryWait);
		if (distance < bestDistance - 0.5) {
			bestDistance = distance;
			stuckTicks = 0;
		} else if (++stuckTicks > GIVE_UP_TICKS) {
			// Can't get there (water, a cliff): somewhere else instead.
			visited.add(target);
			target = null;
			failures++;
			if (failures > 5) {
				phase = Phase.HOME;
			}
		}
	}

	/** Somewhere new to search: a hop away, in reach of the table, on solid ground where the world is running. */
	@Nullable
	private BlockPos pickStop(ServerLevel level, Villager villager, BlockPos station) {
		BlockPos from = villager.blockPosition();
		double rangeSqr = (double) RANGE * RANGE;
		for (int i = 0; i < 16; i++) {
			double angle = level.random.nextDouble() * Math.PI * 2;
			int hop = MIN_HOP + level.random.nextInt(Math.max(1, MAX_HOP - MIN_HOP + 1));
			int x = from.getX() + Mth.floor(Math.cos(angle) * hop);
			int z = from.getZ() + Mth.floor(Math.sin(angle) * hop);
			BlockPos column = new BlockPos(x, from.getY(), z);
			if (column.distSqr(new BlockPos(station.getX(), from.getY(), station.getZ())) > rangeSqr || !level.isLoaded(column)
				|| !level.isPositionEntityTicking(column)) {
				continue;
			}
			BlockPos spot = groundNear(level, column);
			if (spot == null) {
				continue;
			}
			boolean seen = false;
			for (BlockPos v : visited) {
				seen |= v.distSqr(spot) < (double) MIN_HOP * MIN_HOP / 4;
			}
			if (!seen) {
				visited.add(spot);
				return spot;
			}
		}
		return null;
	}

	/** Somewhere to stand in this column, as near the height of {@code column} as there is (up or down a hill). */
	@Nullable
	private static BlockPos groundNear(ServerLevel level, BlockPos column) {
		for (int i = 0; i <= 16; i++) {
			int dy = (i % 2 == 0 ? 1 : -1) * ((i + 1) / 2);
			BlockPos p = column.above(dy);
			if (Walker.canStand(level, p)) {
				return p;
			}
		}
		return null;
	}

	/** At a stop: a ration eaten, a look around, and what turns up goes in the bag. */
	private void search(ServerLevel level, Villager villager, BuilderBag bag) {
		status(villager, "searching", stopsDone + 1, stops);
		if (timer < 0) {
			eat(level, villager, bag);
			timer = Math.max(20, (int) (BuilderLevels.delay(SEARCH_TICKS, villager) * Partners.factor(villager)));
		}
		if (timer % 20 == 0) {
			// Looking about, crouching over something, poking the ground
			float angle = level.random.nextFloat() * Mth.TWO_PI;
			villager.getBrain().setMemory(MemoryModuleType.LOOK_TARGET, new BlockPosTracker(
				villager.blockPosition().offset(Mth.floor(Mth.cos(angle) * 4), -1, Mth.floor(Mth.sin(angle) * 4))));
			villager.swing(InteractionHand.MAIN_HAND);
			level.sendParticles(ParticleTypes.COMPOSTER, villager.getX(), villager.getY() + 0.3, villager.getZ(), 4, 0.6, 0.1, 0.6, 0.0);
			level.playSound(null, villager.blockPosition(), SoundEvents.GRASS_BREAK, SoundSource.NEUTRAL, 0.5f, 0.9f + level.random.nextFloat() * 0.2f);
		}
		if (--timer > 0) {
			return;
		}
		ItemStack weapon = bag.takeFirst(Explorers::isWeapon);
		boolean armed = !weapon.isEmpty();
		int found = 0;
		for (ItemStack find : Explorers.finds(level, villager, villager.blockPosition(), armed)) {
			found += find.getCount();
			ItemStack rest = bag.add(find);
			if (!rest.isEmpty()) {
				Block.popResource(level, villager.blockPosition(), rest); // the bag is full: it's left behind
			}
		}
		if (armed) {
			// The hunt wears the blade; a broken one isn't brought back.
			weapon.setDamageValue(weapon.getDamageValue() + 1 + level.random.nextInt(3));
			if (weapon.getDamageValue() < weapon.getMaxDamage()) {
				bag.add(weapon);
			} else {
				level.playSound(null, villager.blockPosition(), SoundEvents.ITEM_BREAK, SoundSource.NEUTRAL, 0.8f, 1f);
			}
		}
		if (found > 0) {
			level.playSound(null, villager.blockPosition(), SoundEvents.ITEM_PICKUP, SoundSource.NEUTRAL, 0.5f, 1.2f);
			level.sendParticles(ParticleTypes.HAPPY_VILLAGER, villager.getX(), villager.getY() + 1.8, villager.getZ(), 3, 0.3, 0.2, 0.3, 0.0);
		}
		BuilderLevels.addXp(level, villager, 1, null);
		stopsDone++;
		target = null;
		boolean food = bag.stacks().stream().anyMatch(Explorers::isFood);
		phase = stopsDone >= stops || !food || bag.freeSlots() < 3 ? Phase.HOME : Phase.OUT;
		walker.reset();
	}

	/** A ration from the bag (a bowl back if it was stew). */
	private static void eat(ServerLevel level, Villager villager, BuilderBag bag) {
		ItemStack food = bag.takeFirst(Explorers::isFood);
		if (food.isEmpty()) {
			return;
		}
		ItemStack ration = food.split(1);
		if (!food.isEmpty()) {
			bag.add(food);
		}
		var properties = ration.get(DataComponents.FOOD);
		if (properties != null) {
			properties.usingConvertsTo().ifPresent(left -> bag.add(left.copy()));
		}
		level.sendParticles(new ItemParticleOption(ParticleTypes.ITEM, ration), villager.getX(), villager.getEyeY() - 0.2, villager.getZ(),
			6, 0.15, 0.1, 0.15, 0.05);
		level.playSound(null, villager.blockPosition(), SoundEvents.GENERIC_EAT, SoundSource.NEUTRAL, 0.6f, 1f);
	}

	/** Home: everything into the chests by the table (the weapon too), and now and then a map to somewhere. */
	private void home(ServerLevel level, Villager villager, BlockPos station, BuilderBag bag, long now) {
		status(villager, "home", bag.stacks().stream().mapToInt(ItemStack::getCount).sum());
		List<BlockPos> own = SupplyContainers.find(level, station, null);
		BlockPos target = own.isEmpty() ? station : own.get(0);
		if (!walker.walkTo(level, villager, target, REACH)) {
			return;
		}
		for (ItemStack stack : bag.takeAll()) {
			ItemStack rest = own.isEmpty() ? stack : SupplyContainers.insert(level, own, stack);
			if (!rest.isEmpty()) {
				Block.popResource(level, station.above(), rest);
			}
		}
		villager.swing(InteractionHand.MAIN_HAND);
		level.playSound(null, target, SoundEvents.CHEST_CLOSE, SoundSource.BLOCKS, 0.4f, 1.0f);
		if (stopsDone > 0) {
			int trips = ModAttachments.EXPEDITIONS.getOrElse(villager, 0) + 1;
			ModAttachments.EXPEDITIONS.set(villager, trips);
			BuilderLevels.addXp(level, villager, 3, null);
			if (trips % MAP_EVERY == 0) {
				chart(level, villager, station, own);
			}
		}
		stopsDone = 0;
		phase = Phase.IDLE;
		restUntil = now + (long) (REST_TICKS * Partners.factor(villager) * io.github.jcondedata.aliveworkplace.nether.Netherworkers.expeditionFactor(villager));
		busy(villager, false);
		walker.reset();
	}

	/** An empty map from the chests becomes a map to the nearest place nobody has a map to yet. */
	private static void chart(ServerLevel level, Villager villager, BlockPos station, List<BlockPos> own) {
		if (SupplyContainers.count(level, own, Items.MAP) <= 0) {
			return;
		}
		Explorers.Place place = Explorers.findPlace(level, station);
		if (place == null || SupplyContainers.extract(level, own, Items.MAP, 1) != 1) {
			return;
		}
		ItemStack map = Explorers.mapTo(level, place);
		ItemStack rest = SupplyContainers.insert(level, own, map);
		if (!rest.isEmpty()) {
			Block.popResource(level, station.above(), rest);
		}
		ModAttachments.MAPS_CHARTED.set(villager, ModAttachments.MAPS_CHARTED.getOrElse(villager, 0) + 1);
		level.playSound(null, station, SoundEvents.VILLAGER_WORK_CARTOGRAPHER, SoundSource.NEUTRAL, 1f, 1f);
	}

	private static void status(Villager villager, String state, Object... args) {
		Component title = Component.translatable("message.aliveworkplace.explorer.title", ModAttachments.EXPEDITIONS.getOrElse(villager, 0));
		boolean warn = state.equals("needs_food") || state.equals("no_chest");
		WorkerStatus.set(villager, title, -1f, Component.translatable("message.aliveworkplace.explorer.state." + state, args)
			.withStyle(warn ? ChatFormatting.YELLOW : ChatFormatting.GRAY));
	}
}
