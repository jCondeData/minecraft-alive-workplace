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
	private static final float SPEED = 0.75f;

	private static final class State {
		final Walker walker = new Walker(SPEED);
		@Nullable
		BlockPos fire;
		@Nullable
		BlockPos water;
		long nextWaterLook;
		long nextThrow;
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
			if (state.walker.reach(level, villager, state.fire, THROW) && gameTime >= state.nextThrow) {
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
		state.fire = null;
		state.water = null;
	}
}
