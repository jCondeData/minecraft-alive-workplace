package io.github.jcondedata.aliveworkplace.work;

import com.google.common.collect.ImmutableMap;
import io.github.jcondedata.aliveworkplace.build.BuilderLevels;
import io.github.jcondedata.aliveworkplace.registry.ModAttachments;
import io.github.jcondedata.aliveworkplace.registry.ModVillagers;
import io.github.jcondedata.aliveworkplace.threat.VillageFire;
import java.util.Map;
import java.util.Optional;
import java.util.WeakHashMap;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ai.behavior.Behavior;
import net.minecraft.world.entity.ai.behavior.BlockPosTracker;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.memory.MemoryStatus;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LayeredCauldronBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

/**
 * The Firewarden's work (ROADMAP 32.16), in their CORE activity so they answer a fire day or night: they keep
 * {@link #BUCKETS} buckets of water, filled at water or a water cauldron within {@link #WATER_REACH} blocks of their
 * grindstone, run to the nearest fire the village has noticed ({@link VillageFire}) and throw a bucket over it: every
 * fire within {@link VillageFire#SPLASH} blocks goes out in a hiss of steam, and they gain experience. Out of water,
 * they refill and go back. From Journeyman they ring the bell first, and two villagers off work join a bucket brigade
 * ({@link FireBrigade}). Nothing at all with config {@code firewardens} off (Firewardens already hired stand idle).
 */
public class FirewardenWork extends Behavior<Villager> {
	/** Config {@code firewardens}: off, no villager can be made a Firewarden, and Firewardens put out nothing. */
	public static boolean ENABLED = true;
	public static final int BUCKETS = 2;
	/** How far from their grindstone a firewarden fills their buckets. */
	public static final int WATER_REACH = 16;
	/** Experience for each bucket thrown on a fire (and one more per fire block it put out). */
	public static final int XP_PER_SPLASH = 2;
	/** How close (eyes to fire) a firewarden throws from. */
	static final double THROW = 3.5;
	/**
	 * How far (eyes to fire) a firewarden standing in the open throws a bucket up onto a roof from, and how far out from
	 * under the fire they may stand (B218: the ridge of a Starter Cottage has nowhere within {@link #THROW} to stand, and
	 * a firewarden stood watching it burn until the fire crept down the roof).
	 */
	static final double THROW_UP = 12;
	static final double THROW_UP_SIDE = 7;
	private static final float SPEED = 0.75f;

	private static final class State {
		final Walker walker = new Walker(SPEED);
		@Nullable
		BlockPos fire;
		@Nullable
		BlockPos water;
		long nextWaterLook;
		long nextThrow;
		/** The open ground they throw up onto {@code belowFor} from: a fire with nowhere near it to stand (B218). */
		@Nullable
		BlockPos below;
		@Nullable
		BlockPos belowFor;
	}

	private static final Map<Villager, State> STATES = new WeakHashMap<>();

	public FirewardenWork() {
		super(ImmutableMap.of(
			MemoryModuleType.WALK_TARGET, MemoryStatus.REGISTERED,
			MemoryModuleType.LOOK_TARGET, MemoryStatus.REGISTERED
		), 2400);
	}

	public static boolean isFirewarden(Villager villager) {
		return !villager.isBaby() && villager.getVillagerData().getProfession() == ModVillagers.FIREWARDEN;
	}

	/** A water bucket: what picks the job at a grindstone (with config {@code firewardens} on). */
	public static boolean isWaterBucket(ItemStack stack) {
		return ENABLED && stack.is(Items.WATER_BUCKET);
	}

	/** How many of their buckets are full. */
	public static int water(Villager villager) {
		return Math.max(0, Math.min(BUCKETS, ModAttachments.FIREWARDEN_WATER.getOrElse(villager, BUCKETS)));
	}

	public static void setWater(Villager villager, int water) {
		ModAttachments.FIREWARDEN_WATER.set(villager, Math.max(0, Math.min(BUCKETS, water)));
	}

	private static State state(Villager villager) {
		synchronized (STATES) {
			return STATES.computeIfAbsent(villager, v -> new State());
		}
	}

	static Optional<BlockPos> post(ServerLevel level, Villager villager) {
		return villager.getBrain().getMemory(MemoryModuleType.JOB_SITE).filter(g -> g.dimension() == level.dimension()).map(GlobalPos::pos);
	}

	@Override
	protected boolean checkExtraStartConditions(ServerLevel level, Villager villager) {
		if (!ENABLED || !isFirewarden(villager) || !VillageFire.live()) {
			return false;
		}
		Optional<BlockPos> post = post(level, villager);
		if (post.isEmpty()) {
			return false;
		}
		VillageFire.onDuty(villager, level.getGameTime());
		return plan(level, villager, post.get(), state(villager));
	}

	/** Picks what to do next: a fire while there's water, else water while a bucket is empty. False: nothing to do. */
	private static boolean plan(ServerLevel level, Villager villager, BlockPos post, State state) {
		state.fire = water(villager) > 0 ? VillageFire.nearestFire(level, post, VillageFire.ANSWER).orElse(null) : null;
		if (state.fire != null) {
			state.water = null;
			return true;
		}
		if (water(villager) >= BUCKETS) {
			state.water = null;
			return false;
		}
		if (state.water != null && isWater(level, state.water)) {
			return true;
		}
		long now = level.getGameTime();
		if (now < state.nextWaterLook) {
			return false;
		}
		state.nextWaterLook = now + 100;
		state.water = findWater(level, post).orElse(null);
		return state.water != null;
	}

	/** Water a bucket can be filled at: a water source, or a water cauldron. */
	static boolean isWater(ServerLevel level, BlockPos pos) {
		BlockState state = level.getBlockState(pos);
		return state.is(Blocks.WATER_CAULDRON) || state.getFluidState().is(FluidTags.WATER) && state.getFluidState().isSource();
	}

	/** The nearest water within {@link #WATER_REACH} of the grindstone (looked for only when a bucket is empty). */
	public static Optional<BlockPos> findWater(ServerLevel level, BlockPos post) {
		return BlockPos.findClosestMatch(post, WATER_REACH, 6, p -> level.isLoaded(p) && isWater(level, p));
	}

	@Override
	protected void start(ServerLevel level, Villager villager, long gameTime) {
		if (villager.isSleeping()) {
			villager.stopSleeping();
		}
		State state = state(villager);
		if (state.fire != null && villager.getVillagerData().getLevel() >= 3) {
			VillageFire.ring(level, villager, state.fire);
		}
	}

	@Override
	protected boolean canStillUse(ServerLevel level, Villager villager, long gameTime) {
		if (!ENABLED || !isFirewarden(villager) || !VillageFire.live()) {
			return false;
		}
		State state = state(villager);
		return state.fire != null || state.water != null;
	}

	@Override
	protected void tick(ServerLevel level, Villager villager, long gameTime) {
		Optional<BlockPos> post = post(level, villager);
		State state = state(villager);
		if (post.isEmpty()) {
			state.fire = null;
			state.water = null;
			return;
		}
		VillageFire.onDuty(villager, gameTime);
		if (gameTime % 10 == 0 || state.fire != null && !level.getBlockState(state.fire).is(net.minecraft.tags.BlockTags.FIRE)
			|| state.water != null && water(villager) >= BUCKETS) {
			if (!plan(level, villager, post.get(), state)) {
				return;
			}
		}
		if (state.fire != null) {
			hold(villager, Items.WATER_BUCKET);
			villager.getBrain().setMemory(MemoryModuleType.LOOK_TARGET, new BlockPosTracker(state.fire));
			status(villager, "message.aliveworkplace.firewarden.state.fire");
			if (inReach(level, villager, state.fire, state) && gameTime >= state.nextThrow) {
				splash(level, villager, state.fire);
				state.nextThrow = gameTime + 10;
				if (water(villager) == 0) {
					state.fire = null;
				}
			}
		} else if (state.water != null) {
			hold(villager, Items.BUCKET);
			villager.getBrain().setMemory(MemoryModuleType.LOOK_TARGET, new BlockPosTracker(state.water));
			status(villager, "message.aliveworkplace.firewarden.state.water");
			if (state.walker.reach(level, villager, state.water, 3.0)) {
				fill(level, villager, state.water);
				state.water = null;
			}
		}
	}

	/**
	 * Gets within a throw of {@code fire}: within {@link #THROW} of it, or, when there is nowhere to stand that near (a roof's
	 * ridge), out in the open below it, close enough to throw the bucket up ({@link #throwsUp}). True once there.
	 */
	private static boolean inReach(ServerLevel level, Villager villager, BlockPos fire, State state) {
		if (throwsUp(level, villager.blockPosition(), villager.getEyePosition(), fire) && villager.onGround()) {
			villager.getBrain().eraseMemory(MemoryModuleType.WALK_TARGET);
			return true;
		}
		if (state.below != null && (!fire.equals(state.belowFor) || !Walker.canStand(level, state.below))) {
			state.below = null;
		}
		if (state.below == null) {
			boolean near = state.walker.reach(level, villager, fire, THROW);
			if (near || !state.walker.noSpot()) {
				return near;
			}
			state.below = spotBelow(level, fire, villager.blockPosition()).orElse(null);
			state.belowFor = fire;
			if (state.below == null) {
				return false;
			}
		}
		state.walker.walkTo(level, villager, state.below, 1.2);
		return false;
	}

	/**
	 * Whether a firewarden standing at {@code feet} (eyes at {@code eyes}) can throw a bucket up onto {@code fire}: in the
	 * open (nothing over their head up to the fire's height), the fire above their eyes, within {@link #THROW_UP_SIDE} blocks out from under it and
	 * {@link #THROW_UP} of their eyes.
	 */
	static boolean throwsUp(ServerLevel level, BlockPos feet, Vec3 eyes, BlockPos fire) {
		Vec3 at = Vec3.atCenterOf(fire);
		double dx = at.x - eyes.x;
		double dz = at.z - eyes.z;
		if (at.y <= eyes.y || dx * dx + dz * dz > THROW_UP_SIDE * THROW_UP_SIDE || eyes.distanceTo(at) > THROW_UP) {
			return false;
		}
		// (in the open: nothing over their head up to the fire's height, so not from inside under the roof)
		BlockPos.MutableBlockPos p = feet.mutable();
		for (int y = feet.getY() + 2; y < fire.getY(); y++) {
			p.setY(y);
			if (!level.getBlockState(p).getCollisionShape(level, p).isEmpty()) {
				return false;
			}
		}
		return true;
	}

	/** The open ground nearest {@code from} that a bucket can be thrown up onto {@code fire} from ({@link #throwsUp}). */
	static Optional<BlockPos> spotBelow(ServerLevel level, BlockPos fire, BlockPos from) {
		int side = (int) THROW_UP_SIDE;
		BlockPos best = null;
		double bestScore = Double.MAX_VALUE;
		for (BlockPos p : BlockPos.betweenClosed(fire.offset(-side, -(int) THROW_UP, -side), fire.offset(side, -2, side))) {
			if (!level.isLoaded(p) || !Walker.canStand(level, p) || !throwsUp(level, p, Walker.eyeFrom(p), fire)) {
				continue;
			}
			double score = p.distSqr(from);
			if (score < bestScore) {
				bestScore = score;
				best = p.immutable();
			}
		}
		return Optional.ofNullable(best);
	}

	/** Throws a bucket of water over {@code fire}: the fire within the splash goes out, with steam. */
	public static int splash(ServerLevel level, Villager villager, BlockPos fire) {
		villager.swing(InteractionHand.MAIN_HAND, true);
		level.playSound(null, villager, SoundEvents.BUCKET_EMPTY, SoundSource.NEUTRAL, 1f, 1f);
		Vec3 from = villager.getEyePosition();
		Vec3 to = Vec3.atCenterOf(fire);
		int steps = Math.max(4, (int) (from.distanceTo(to) * 2));
		for (int i = 1; i <= steps; i++) {
			Vec3 p = from.lerp(to, i / (double) steps).add(0, Math.sin(Math.PI * i / steps) * 0.8, 0);
			level.sendParticles(ParticleTypes.FALLING_WATER, p.x, p.y, p.z, 3, 0.1, 0.1, 0.1, 0);
		}
		int out = VillageFire.douse(level, fire, VillageFire.SPLASH, villager);
		setWater(villager, water(villager) - 1);
		BuilderLevels.addXp(level, villager, XP_PER_SPLASH + out, null);
		return out;
	}

	/** Fills the buckets at {@code water}: both at a water source; a water cauldron fills one and is emptied. */
	public static void fill(ServerLevel level, Villager villager, BlockPos water) {
		BlockState state = level.getBlockState(water);
		if (state.is(Blocks.WATER_CAULDRON)) {
			level.setBlockAndUpdate(water, Blocks.CAULDRON.defaultBlockState());
			setWater(villager, water(villager) + 1);
		} else if (isWater(level, water)) {
			setWater(villager, BUCKETS);
		} else {
			return;
		}
		villager.swing(InteractionHand.MAIN_HAND, true);
		level.playSound(null, water, SoundEvents.BUCKET_FILL, SoundSource.NEUTRAL, 1f, 1f);
		level.sendParticles(ParticleTypes.SPLASH, water.getX() + 0.5, water.getY() + 1, water.getZ() + 0.5, 12, 0.3, 0.1, 0.3, 0.1);
		hold(villager, Items.WATER_BUCKET);
	}

	private static void hold(Villager villager, net.minecraft.world.item.Item item) {
		if (!villager.getMainHandItem().is(item)) {
			villager.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(item));
		}
	}

	private static void status(Villager villager, String key) {
		WorkerStatus.set(villager, title(villager), -1f, Component.translatable(key).withStyle(ChatFormatting.GOLD));
	}

	/** "Firewarden · 2 of 2 buckets full". */
	public static Component title(Villager villager) {
		return Component.translatable("message.aliveworkplace.firewarden.title", water(villager), BUCKETS);
	}

	@Override
	protected void stop(ServerLevel level, Villager villager, long gameTime) {
		villager.getBrain().eraseMemory(MemoryModuleType.WALK_TARGET);
		if (villager.getMainHandItem().is(Items.WATER_BUCKET) || villager.getMainHandItem().is(Items.BUCKET)) {
			villager.setItemSlot(EquipmentSlot.MAINHAND, ItemStack.EMPTY);
		}
		State state = state(villager);
		state.walker.reset();
		state.below = null;
		state.fire = null;
		state.water = null;
	}
}
