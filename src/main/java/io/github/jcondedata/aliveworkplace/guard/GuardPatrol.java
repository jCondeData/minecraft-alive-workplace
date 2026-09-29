package io.github.jcondedata.aliveworkplace.guard;

import com.google.common.collect.ImmutableMap;
import io.github.jcondedata.aliveworkplace.build.Builders;
import io.github.jcondedata.aliveworkplace.build.SupplyContainers;
import io.github.jcondedata.aliveworkplace.work.Walker;
import io.github.jcondedata.aliveworkplace.work.WorkerStatus;
import java.util.List;
import java.util.function.Predicate;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ai.behavior.Behavior;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.memory.MemoryStatus;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.levelgen.Heightmap;
import org.jetbrains.annotations.Nullable;

/**
 * A guard's shift (WORK): take the best weapon and armor from the chests near the Guard Post, then walk
 * the area around the post, staying close to it at night. By day, a guard below {@link #TRAIN_UP_TO} spars with a
 * Training Dummy near the post now and then ({@link #HITS} hits, 1 XP every {@link #HITS_PER_XP}). Fighting is
 * {@link GuardCombat}'s job.
 */
public class GuardPatrol extends Behavior<Villager> {
	private static final float SPEED = 0.5f;
	private static final int GEAR_CHECK_EVERY = 400;
	private static final EquipmentSlot[] ARMOR = {EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET};

	/** Guards earn experience at a Training Dummy up to this level (Expert); Masters are made in real fights. */
	public static final int TRAIN_UP_TO = 4;
	/** How far from the post a dummy is used. */
	static final int DUMMY_RADIUS = 12;
	/** Hits in one session at the dummy, and hits per point of experience. */
	public static final int HITS = 12;
	public static final int HITS_PER_XP = 4;
	private static final int HIT_EVERY = 20;
	/** Ticks between two sessions at the dummy. */
	private static final int TRAIN_EVERY = 2400;
	private static final int DUMMY_SEARCH_EVERY = 400;

	private final Walker walker = new Walker(SPEED);
	@Nullable
	private BlockPos dummy;
	private int hits;
	private int hitTimer;
	private long lastTrained = -TRAIN_EVERY;
	/** No dummy was found: when to look again. */
	private long nextDummySearch;
	@Nullable
	private BlockPos waypoint;
	private int wait;
	/** The next point of the guard's patrol route (see {@link PatrolMapItem}). */
	private int routeIndex;
	private int gearTimer;
	@Nullable
	private BlockPos gearChest;
	/** A free horse near the post the guard is going to mount (see {@link Cavalry}), and when to look for one again. */
	@Nullable
	private net.minecraft.world.entity.animal.horse.AbstractHorse horse;
	private long nextHorseSearch;
	private static final int HORSE_SEARCH_EVERY = 200;

	public GuardPatrol() {
		super(ImmutableMap.of(
			MemoryModuleType.JOB_SITE, MemoryStatus.VALUE_PRESENT,
			MemoryModuleType.WALK_TARGET, MemoryStatus.REGISTERED,
			MemoryModuleType.LOOK_TARGET, MemoryStatus.REGISTERED
		), 1200);
	}

	@Override
	protected boolean checkExtraStartConditions(ServerLevel level, Villager villager) {
		return !villager.isSleeping() && Builders.benchPos(villager).isPresent();
	}

	@Override
	protected boolean canStillUse(ServerLevel level, Villager villager, long gameTime) {
		return !villager.isSleeping() && Builders.benchPos(villager).isPresent();
	}

	@Override
	protected void start(ServerLevel level, Villager villager, long gameTime) {
		walker.reset();
		waypoint = null;
		villager.setDropChance(EquipmentSlot.MAINHAND, 0f);
		villager.setDropChance(EquipmentSlot.OFFHAND, 0f);
		for (EquipmentSlot slot : ARMOR) {
			villager.setDropChance(slot, 0f);
		}
	}

	@Override
	protected void stop(ServerLevel level, Villager villager, long gameTime) {
		Cavalry.dismount(villager); // the shift's over: the horse is left where it stands
		horse = null;
	}

	@Override
	protected void tick(ServerLevel level, Villager villager, long gameTime) {
		if (GuardCombat.isFighting(villager) || GuardRally.isRallying(villager)) {
			waypoint = null;
			return;
		}
		var leader = Escorts.leader(villager);
		if (leader.isPresent()) {
			waypoint = null;
			GuardEscort.status(villager, leader.get());
			return;
		}
		BlockPos post = Builders.benchPos(villager).orElse(null);
		if (post == null) {
			return;
		}
		// Better gear in the chests? Go and get it.
		if (gearChest == null && --gearTimer <= 0) {
			gearTimer = GEAR_CHECK_EVERY;
			gearChest = SupplyContainers.firstMatching(level, SupplyContainers.find(level, post, null), stack -> isUpgrade(villager, stack)
				|| io.github.jcondedata.aliveworkplace.brew.AlchemistWork.isGuardPotion(stack) && Guards.potions(villager) < Guards.potionsFor(villager)
				|| Guards.isSpecialArrow(stack) && (Guards.hasBow(villager) || SupplyContainers.firstMatching(level,
					SupplyContainers.find(level, post, null), Guards::isBow) != null) && Guards.quiver(villager) < Guards.QUIVER / 2);
		}
		if (gearChest != null) {
			status(villager, "gearing_up");
			if (walker.walkTo(level, villager, gearChest, 3.0)) {
				equipBest(level, villager, SupplyContainers.find(level, post, null));
				level.playSound(null, villager.blockPosition(), SoundEvents.ARMOR_EQUIP_IRON.value(), SoundSource.NEUTRAL, 0.8f, 1f);
				gearChest = null;
			}
			return;
		}
		// Sparring at the dummy.
		if (dummy != null) {
			if (level.isNight() || !level.getBlockState(dummy).is(io.github.jcondedata.aliveworkplace.registry.ModBlocks.TRAINING_DUMMY)) {
				dummy = null;
			} else {
				Cavalry.dismount(villager); // (on foot: the horse waits)
				train(level, villager, gameTime);
				return;
			}
		}
		// Cavalry: into the saddle of a free horse near the post.
		if (Cavalry.mount(villager) == null && mountUp(level, villager, post, gameTime)) {
			return;
		}
		// Walk the area; stay near the post at night.
		status(villager, "patrolling");
		if (wait > 0) {
			wait--;
			return;
		}
		if (waypoint == null) {
			boolean night = level.isNight();
			if (!night && gameTime - lastTrained >= TRAIN_EVERY && gameTime >= nextDummySearch && canTrain(villager)) {
				dummy = findDummy(level, post);
				nextDummySearch = gameTime + DUMMY_SEARCH_EVERY;
				if (dummy != null) {
					lastTrained = gameTime;
					hits = 0;
					hitTimer = 0;
					walker.reset();
					return;
				}
			}
			BlockPos routed = night ? null : nextOnRoute(villager, post);
			if (routed != null) {
				waypoint = routed;
				walker.reset();
				return;
			}
			int r = night ? 6 : 14;
			int x = post.getX() + level.random.nextInt(r * 2 + 1) - r;
			int z = post.getZ() + level.random.nextInt(r * 2 + 1) - r;
			int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
			BlockPos p = new BlockPos(x, y, z);
			waypoint = Math.abs(y - post.getY()) <= 6 && Walker.canStand(level, p) ? p : null;
			walker.reset();
			return;
		}
		if (walker.walkTo(level, villager, waypoint, 2.0) || walker.noSpot()) {
			boolean routed = io.github.jcondedata.aliveworkplace.registry.ModAttachments.PATROL_ROUTE.has(villager);
			waypoint = null;
			wait = routed ? 20 + level.random.nextInt(40) : 60 + level.random.nextInt(100);
		}
	}

	/** Walks up to a free horse near the post and mounts it; false when there's none (the patrol goes on on foot). */
	private boolean mountUp(ServerLevel level, Villager villager, BlockPos post, long gameTime) {
		if (horse != null && !Cavalry.usable(horse)) {
			horse = null;
		}
		if (horse == null) {
			if (gameTime < nextHorseSearch) {
				return false;
			}
			nextHorseSearch = gameTime + HORSE_SEARCH_EVERY;
			horse = Cavalry.freeHorse(level, villager, post);
			if (horse == null) {
				return false;
			}
			walker.reset();
		}
		status(villager, "mounting");
		if (villager.distanceTo(horse) <= Cavalry.MOUNT_REACH) {
			if (Cavalry.ride(villager, horse)) {
				waypoint = null;
				walker.reset();
			}
			horse = null;
			return true;
		}
		if (!walker.walkTo(level, villager, horse.blockPosition(), 1.5) && walker.noSpot()) {
			horse = null; // can't get to it
		}
		return true;
	}

	/** The next point on the guard's route (within reach of the post), or null without a route. */
	@Nullable
	private BlockPos nextOnRoute(Villager villager, BlockPos post) {
		java.util.List<BlockPos> route = io.github.jcondedata.aliveworkplace.registry.ModAttachments.PATROL_ROUTE.get(villager);
		if (route == null || route.isEmpty()) {
			return null;
		}
		for (int tries = 0; tries < route.size(); tries++) {
			BlockPos p = route.get(routeIndex++ % route.size());
			if (p.closerThan(post, PatrolMapItem.MAX_DISTANCE)) {
				return p;
			}
		}
		return null;
	}

	/** Where the guard is heading now (tests). */
	@Nullable
	public BlockPos waypoint() {
		return waypoint;
	}

	/** Whether sparring still teaches this guard anything. */
	public static boolean canTrain(Villager villager) {
		return villager.getVillagerData().getLevel() < TRAIN_UP_TO;
	}

	/** The Training Dummy nearest the post, within {@link #DUMMY_RADIUS}. */
	@Nullable
	static BlockPos findDummy(ServerLevel level, BlockPos post) {
		BlockPos best = null;
		double bestDistance = Double.MAX_VALUE;
		for (BlockPos p : BlockPos.betweenClosed(post.offset(-DUMMY_RADIUS, -4, -DUMMY_RADIUS), post.offset(DUMMY_RADIUS, 4, DUMMY_RADIUS))) {
			if (level.getBlockState(p).is(io.github.jcondedata.aliveworkplace.registry.ModBlocks.TRAINING_DUMMY)) {
				double d = p.distSqr(post);
				if (d < bestDistance) {
					bestDistance = d;
					best = p.immutable();
				}
			}
		}
		return best;
	}

	/** Walks up to the dummy and hits it every second; a point of experience every few hits. */
	private void train(ServerLevel level, Villager villager, long gameTime) {
		status(villager, "training");
		if (!walker.reach(level, villager, dummy, 2.5)) {
			if (walker.noSpot()) {
				dummy = null;
			}
			return;
		}
		villager.getBrain().setMemory(MemoryModuleType.LOOK_TARGET, new net.minecraft.world.entity.ai.behavior.BlockPosTracker(dummy.above()));
		if (++hitTimer < HIT_EVERY) {
			return;
		}
		hitTimer = 0;
		villager.swing(net.minecraft.world.InteractionHand.MAIN_HAND);
		TrainingDummyBlock.hit(level, dummy);
		hits++;
		io.github.jcondedata.aliveworkplace.registry.ModAttachments.DUMMY_HITS.set(villager, io.github.jcondedata.aliveworkplace.registry.ModAttachments.DUMMY_HITS.getOrElse(villager, 0) + 1);
		if (hits % HITS_PER_XP == 0 && canTrain(villager)) {
			io.github.jcondedata.aliveworkplace.build.BuilderLevels.addXp(level, villager, 1, null);
		}
		if (hits >= HITS) {
			dummy = null;
			lastTrained = gameTime;
			wait = 60;
		}
	}

	private static void status(Villager villager, String state) {
		WorkerStatus.set(villager, GuardCombat.title(villager), -1f,
			Component.translatable("message.aliveworkplace.guard.state." + state).withStyle(ChatFormatting.GRAY));
	}

	/** True if {@code stack} beats what the guard has in that slot. */
	static boolean isUpgrade(Villager villager, ItemStack stack) {
		if (Guards.isBow(stack)) {
			Guards.Kind kind = Guards.kind(villager);
			return (kind == Guards.Kind.GUARD || kind == Guards.Kind.ARCHER)
				&& Guards.rangedRank(stack) > Guards.rangedRank(villager.getItemBySlot(EquipmentSlot.OFFHAND));
		}
		if (Guards.isShield(stack)) {
			return villager.getItemBySlot(EquipmentSlot.OFFHAND).isEmpty();
		}
		if (Guards.isWeapon(stack)) {
			return Guards.baseDamage(stack) > Guards.baseDamage(villager.getItemBySlot(EquipmentSlot.MAINHAND));
		}
		if (stack.getItem() instanceof ArmorItem armor) {
			EquipmentSlot slot = armor.getEquipmentSlot();
			return Guards.armorValue(stack) > Guards.armorValue(villager.getItemBySlot(slot));
		}
		return false;
	}

	/** {@link #equipBest} (tests). */
	public static void equipBestForTest(ServerLevel level, Villager villager, List<BlockPos> chests) {
		equipBest(level, villager, chests);
	}

	/** Swaps in the best weapon and armor from the chests; what they had goes back in. */
	static void equipBest(ServerLevel level, Villager villager, List<BlockPos> chests) {
		// Each swap raises the bar, so a few rounds end with the best piece of each kind.
		for (int i = 0; i < 4 && take(level, villager, chests, EquipmentSlot.MAINHAND, stack -> Guards.isWeapon(stack)
			&& Guards.baseDamage(stack) > Guards.baseDamage(villager.getItemBySlot(EquipmentSlot.MAINHAND))); i++) {
		}
		// The off hand makes the kind of guard: a bow for an archer (or a better one), else a shield for a knight. A knight or
		// a medic (a potion handed to them) keeps what they hold.
		Guards.Kind kind = Guards.kind(villager);
		if (kind == Guards.Kind.GUARD || kind == Guards.Kind.ARCHER) {
			for (int i = 0; i < 2 && take(level, villager, chests, EquipmentSlot.OFFHAND, stack -> Guards.isBow(stack)
				&& Guards.rangedRank(stack) > Guards.rangedRank(villager.getItemBySlot(EquipmentSlot.OFFHAND))); i++) {
			}
		}
		if (villager.getItemBySlot(EquipmentSlot.OFFHAND).isEmpty()) {
			take(level, villager, chests, EquipmentSlot.OFFHAND, Guards::isShield);
		}
		for (EquipmentSlot slot : ARMOR) {
			for (int i = 0; i < 4 && take(level, villager, chests, slot, stack -> stack.getItem() instanceof ArmorItem armor
				&& armor.getEquipmentSlot() == slot && Guards.armorValue(stack) > Guards.armorValue(villager.getItemBySlot(slot))); i++) {
			}
		}
		// Healing, regeneration and strength potions, a few.
		var potionBag = io.github.jcondedata.aliveworkplace.registry.ModAttachments.BUILDER_BAG.getOrCreate(villager);
		for (int i = Guards.potions(villager); i < Guards.potionsFor(villager); i++) {
			ItemStack potion = SupplyContainers.takeOne(level, chests, io.github.jcondedata.aliveworkplace.brew.AlchemistWork::isGuardPotion);
			if (potion.isEmpty()) {
				break;
			}
			ItemStack rest = potionBag.add(potion);
			if (!rest.isEmpty()) {
				SupplyContainers.insert(level, chests, rest);
				break;
			}
		}
		// Spectral and tipped arrows fill the quiver.
		if (Guards.hasBow(villager)) {
			var bag = io.github.jcondedata.aliveworkplace.registry.ModAttachments.BUILDER_BAG.getOrCreate(villager);
			int room = Guards.QUIVER - Guards.quiver(villager);
			while (room > 0) {
				BlockPos chest = SupplyContainers.firstMatching(level, chests, Guards::isSpecialArrow);
				if (chest == null) {
					break;
				}
				List<ItemStack> taken = SupplyContainers.takeMatching(level, chest, Guards::isSpecialArrow, 1);
				if (taken.isEmpty()) {
					break;
				}
				ItemStack arrows = taken.get(0);
				ItemStack mine = arrows.split(Math.min(room, arrows.getCount()));
				int count = mine.getCount();
				ItemStack rest = bag.add(mine);
				room -= count - rest.getCount();
				for (ItemStack back : List.of(arrows, rest)) {
					if (!back.isEmpty()) {
						SupplyContainers.insert(level, chests, back);
					}
				}
				if (!rest.isEmpty()) {
					break; // the bag is full
				}
			}
		}
	}

	private static boolean take(ServerLevel level, Villager villager, List<BlockPos> chests, EquipmentSlot slot, Predicate<ItemStack> better) {
		ItemStack got = SupplyContainers.takeOne(level, chests, better);
		if (got.isEmpty()) {
			return false;
		}
		ItemStack old = villager.getItemBySlot(slot);
		villager.setItemSlot(slot, got);
		villager.setDropChance(slot, 0f);
		if (!old.isEmpty()) {
			ItemStack rest = SupplyContainers.insert(level, chests, old);
			if (!rest.isEmpty()) {
				villager.spawnAtLocation(rest);
			}
		}
		return true;
	}
}
