package io.github.jcondedata.aliveworkplace.work;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.mojang.datafixers.util.Pair;
import io.github.jcondedata.aliveworkplace.build.BuilderLevels;
import io.github.jcondedata.aliveworkplace.registry.ModAttachments;
import io.github.jcondedata.aliveworkplace.registry.ModVillagers;
import io.github.jcondedata.aliveworkplace.threat.Disasters;
import io.github.jcondedata.aliveworkplace.threat.Drought;
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
import net.minecraft.tags.ItemTags;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ai.behavior.Behavior;
import net.minecraft.world.entity.ai.behavior.BehaviorControl;
import net.minecraft.world.entity.ai.behavior.BlockPosTracker;
import net.minecraft.world.entity.ai.behavior.SetLookAndInteract;
import net.minecraft.world.entity.ai.behavior.UpdateActivityFromSchedule;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.memory.MemoryStatus;
import net.minecraft.world.entity.ai.village.poi.PoiManager;
import net.minecraft.world.entity.ai.village.poi.PoiTypes;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.FarmBlock;
import net.minecraft.world.level.block.LayeredCauldronBlock;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

/**
 * The Well Keeper's work (ROADMAP 32.17), in their WORK activity: they carry one bucket at a time, filled at the nearest
 * well, river or pond (a water source block within {@link #WATER_REACH} of their cauldron).
 * <ul>
 * <li>In a drought ({@link Drought#in}) they carry it to the village's fields: the nearest farmland within
 * {@link #FIELD_REACH} of the cauldron that isn't wet through or watered today, where a bucket wets the 9 farmland round
 * it ({@link Drought#wet}), whose crops then grow at the full pace that day.</li>
 * <li>Between droughts they keep the cauldrons of the village round their own (leatherworkers' cauldrons as POIs) full,
 * and wet the farmland that has dried out.</li>
 * </ul>
 * Each bucket gains them experience; water partners (Squirtle, Wooper...) shorten the time to fill and to pour
 * ({@link Partners#factor}). They foretell the next drought a day ahead through the disaster file's {@code foretold_by}.
 * Nothing at all with config {@code wellKeepers} off (Well Keepers already hired stand idle). Restartable at any tick:
 * only the bucket's water is saved ({@link ModAttachments#WELL_KEEPER_WATER}); the targets are looked for again.
 */
public class WellKeeperWork extends Behavior<Villager> {
	/** Config {@code wellKeepers}: off, no villager can be made a Well Keeper, and Well Keepers do nothing. */
	public static boolean ENABLED = true;
	/** How far from their cauldron a Well Keeper looks after farmland and cauldrons. */
	public static final int FIELD_REACH = 24;
	/** How far from their cauldron a Well Keeper fetches water. */
	public static final int WATER_REACH = 24;
	/** Experience for each bucket carried. */
	public static final int XP_PER_BUCKET = 2;
	/** Ticks spent filling or pouring a bucket, times {@link Partners#factor}. */
	public static final int POUR_TICKS = 30;
	/** Ticks between looks for work when there is none. */
	static final int LOOK_EVERY = 100;
	private static final float SPEED = 0.6f;

	private enum Task {
		FIELD, CAULDRON
	}

	private static final class State {
		final Walker walker = new Walker(SPEED);
		@Nullable
		BlockPos target;
		Task task = Task.FIELD;
		@Nullable
		BlockPos water;
		long nextLook;
		long busyUntil;
	}

	private static final Map<Villager, State> STATES = new WeakHashMap<>();

	public WellKeeperWork() {
		super(ImmutableMap.of(
			MemoryModuleType.WALK_TARGET, MemoryStatus.REGISTERED,
			MemoryModuleType.LOOK_TARGET, MemoryStatus.REGISTERED
		), 2400);
	}

	public static ImmutableList<Pair<Integer, ? extends BehaviorControl<? super Villager>>> packages(float speed) {
		return ImmutableList.<Pair<Integer, ? extends BehaviorControl<? super Villager>>>of(
			Pair.of(0, new WellKeeperWork()),
			Pair.of(10, SetLookAndInteract.create(EntityType.PLAYER, 4)),
			Pair.of(99, UpdateActivityFromSchedule.create())
		);
	}

	public static boolean isWellKeeper(Villager villager) {
		return !villager.isBaby() && villager.getVillagerData().getProfession() == ModVillagers.WELL_KEEPER;
	}

	/** A shovel: what picks the job at a cauldron (with config {@code wellKeepers} on). */
	public static boolean isShovel(ItemStack stack) {
		return ENABLED && Disasters.live() && stack.is(ItemTags.SHOVELS);
	}

	/** Whether their bucket is full. */
	public static boolean hasWater(Villager villager) {
		return ModAttachments.WELL_KEEPER_WATER.getOrElse(villager, 0) > 0;
	}

	public static void setWater(Villager villager, boolean full) {
		ModAttachments.WELL_KEEPER_WATER.set(villager, full ? 1 : 0);
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
		if (!ENABLED || !isWellKeeper(villager) || !Disasters.live()) {
			return false;
		}
		Optional<BlockPos> post = post(level, villager);
		State state = state(villager);
		if (post.isEmpty() || level.getGameTime() < state.nextLook) {
			return false;
		}
		return plan(level, villager, post.get(), state);
	}

	/** Picks the next farmland or cauldron, and the water to fill the bucket at if it's empty. False: nothing to do. */
	private static boolean plan(ServerLevel level, Villager villager, BlockPos post, State state) {
		long now = level.getGameTime();
		if (state.target == null || !needsWater(level, state.target, state.task)) {
			state.target = null;
			if (Drought.in(level, post)) {
				state.task = Task.FIELD;
				state.target = findFarmland(level, post, true).orElse(null);
			} else {
				Optional<BlockPos> cauldron = findCauldron(level, post);
				state.task = cauldron.isPresent() ? Task.CAULDRON : Task.FIELD;
				state.target = cauldron.orElseGet(() -> findFarmland(level, post, false).orElse(null));
			}
		}
		if (state.target == null) {
			state.nextLook = now + LOOK_EVERY;
			return false;
		}
		if (!hasWater(villager) && (state.water == null || !isWater(level, state.water))) {
			state.water = findWater(level, post).orElse(null);
			if (state.water == null) {
				state.target = null;
				state.nextLook = now + LOOK_EVERY;
				return false;
			}
		}
		return true;
	}

	/** Whether {@code pos} still wants the keeper's water. */
	private static boolean needsWater(ServerLevel level, BlockPos pos, Task task) {
		BlockState state = level.getBlockState(pos);
		if (task == Task.CAULDRON) {
			return state.is(Blocks.CAULDRON) || state.is(Blocks.WATER_CAULDRON) && state.getValue(LayeredCauldronBlock.LEVEL) < LayeredCauldronBlock.MAX_FILL_LEVEL;
		}
		return isDryFarmland(level, pos, Drought.in(level, pos));
	}

	/**
	 * Farmland the keeper waters: in a drought, any not wet through or not yet watered today; between droughts, only
	 * farmland dried right out (moisture 0).
	 */
	static boolean isDryFarmland(ServerLevel level, BlockPos pos, boolean drought) {
		BlockState state = level.getBlockState(pos);
		if (!state.is(Blocks.FARMLAND)) {
			return false;
		}
		int moisture = state.getValue(FarmBlock.MOISTURE);
		return drought ? moisture < FarmBlock.MAX_MOISTURE || !Drought.watered(level, pos) : moisture == 0;
	}

	/** The nearest farmland to water within {@link #FIELD_REACH} of the cauldron. */
	static Optional<BlockPos> findFarmland(ServerLevel level, BlockPos post, boolean drought) {
		return BlockPos.findClosestMatch(post, FIELD_REACH, 4, p -> level.isLoaded(p) && isDryFarmland(level, p, drought)).map(BlockPos::immutable);
	}

	/** The nearest cauldron of the village (found as a POI: nothing scans) that isn't full of water. */
	static Optional<BlockPos> findCauldron(ServerLevel level, BlockPos post) {
		return level.getPoiManager().findClosest(h -> h.is(PoiTypes.LEATHERWORKER), p -> needsWater(level, p, Task.CAULDRON), post, FIELD_REACH,
			PoiManager.Occupancy.ANY);
	}

	/** A well, river or pond: a water source block. */
	static boolean isWater(ServerLevel level, BlockPos pos) {
		return level.getFluidState(pos).is(FluidTags.WATER) && level.getFluidState(pos).isSource();
	}

	/** The nearest water source within {@link #WATER_REACH} of the cauldron, with air above it to dip a bucket in. */
	public static Optional<BlockPos> findWater(ServerLevel level, BlockPos post) {
		return BlockPos.findClosestMatch(post, WATER_REACH, 6, p -> level.isLoaded(p) && isWater(level, p) && !isWater(level, p.above()))
			.map(BlockPos::immutable);
	}

	@Override
	protected boolean canStillUse(ServerLevel level, Villager villager, long gameTime) {
		if (!ENABLED || !isWellKeeper(villager) || !Disasters.live()) {
			return false;
		}
		return state(villager).target != null;
	}

	@Override
	protected void tick(ServerLevel level, Villager villager, long gameTime) {
		Optional<BlockPos> post = post(level, villager);
		State state = state(villager);
		if (post.isEmpty()) {
			state.target = null;
			return;
		}
		if (gameTime < state.busyUntil) {
			return;
		}
		if (state.target == null || !needsWater(level, state.target, state.task)) {
			state.target = null;
			if (!plan(level, villager, post.get(), state)) {
				return;
			}
		}
		if (!hasWater(villager)) {
			if (state.water == null || !isWater(level, state.water)) {
				if (!plan(level, villager, post.get(), state)) {
					return;
				}
			}
			hold(villager, Items.BUCKET);
			villager.getBrain().setMemory(MemoryModuleType.LOOK_TARGET, new BlockPosTracker(state.water));
			status(villager, "message.aliveworkplace.well_keeper.state.water");
			if (state.walker.reach(level, villager, state.water, 3.0)) {
				fill(level, villager, state.water);
				state.busyUntil = gameTime + wait(villager);
			} else if (state.walker.noSpot()) {
				state.water = null; // nowhere to stand by it: another well next look
				state.target = null;
				state.nextLook = gameTime + LOOK_EVERY;
			}
			return;
		}
		hold(villager, Items.WATER_BUCKET);
		villager.getBrain().setMemory(MemoryModuleType.LOOK_TARGET, new BlockPosTracker(state.target));
		status(villager, state.task == Task.CAULDRON ? "message.aliveworkplace.well_keeper.state.cauldron"
			: Drought.in(level, state.target) ? "message.aliveworkplace.well_keeper.state.drought" : "message.aliveworkplace.well_keeper.state.field");
		if (state.walker.reach(level, villager, state.target, 3.0)) {
			pour(level, villager, state.target, state.task == Task.CAULDRON);
			state.target = null;
			state.busyUntil = gameTime + wait(villager);
		} else if (state.walker.noSpot()) {
			state.target = null;
			state.nextLook = gameTime + LOOK_EVERY;
		}
	}

	/** Ticks to fill or pour a bucket: {@link #POUR_TICKS}, less with water partners. */
	static int wait(Villager villager) {
		return Math.max(5, Math.round(POUR_TICKS * Partners.factor(villager)));
	}

	/** Fills the bucket at {@code water} (the water source stays). */
	public static void fill(ServerLevel level, Villager villager, BlockPos water) {
		if (!isWater(level, water)) {
			return;
		}
		setWater(villager, true);
		villager.swing(InteractionHand.MAIN_HAND, true);
		level.playSound(null, water, SoundEvents.BUCKET_FILL, SoundSource.NEUTRAL, 1f, 1f);
		level.sendParticles(ParticleTypes.SPLASH, water.getX() + 0.5, water.getY() + 1, water.getZ() + 0.5, 12, 0.3, 0.1, 0.3, 0.1);
		hold(villager, Items.WATER_BUCKET);
	}

	/**
	 * Pours the bucket on {@code target}: a cauldron is filled to the brim, farmland wets the 9 round it. Returns how many
	 * blocks it watered (1 for a cauldron).
	 */
	public static int pour(ServerLevel level, Villager villager, BlockPos target, boolean cauldron) {
		int watered;
		if (cauldron) {
			level.setBlockAndUpdate(target, Blocks.WATER_CAULDRON.defaultBlockState().setValue(LayeredCauldronBlock.LEVEL, LayeredCauldronBlock.MAX_FILL_LEVEL));
			watered = 1;
		} else {
			watered = Drought.wet(level, target);
		}
		setWater(villager, false);
		villager.swing(InteractionHand.MAIN_HAND, true);
		level.playSound(null, target, SoundEvents.BUCKET_EMPTY, SoundSource.NEUTRAL, 1f, 1f);
		level.sendParticles(ParticleTypes.SPLASH, target.getX() + 0.5, target.getY() + 1.1, target.getZ() + 0.5, 30, 1.0, 0.1, 1.0, 0.2);
		level.sendParticles(ParticleTypes.FALLING_WATER, target.getX() + 0.5, target.getY() + 1.6, target.getZ() + 0.5, 12, 0.8, 0.2, 0.8, 0);
		hold(villager, Items.BUCKET);
		BuilderLevels.addXp(level, villager, XP_PER_BUCKET, null);
		return watered;
	}

	private static void hold(Villager villager, net.minecraft.world.item.Item item) {
		if (!villager.getMainHandItem().is(item)) {
			villager.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(item));
		}
	}

	private static void status(Villager villager, String key) {
		WorkerStatus.set(villager, title(villager), -1f, Component.translatable(key).withStyle(ChatFormatting.AQUA));
	}

	/** "Well Keeper · bucket full" / "Well Keeper · bucket empty". */
	public static Component title(Villager villager) {
		return Component.translatable(hasWater(villager) ? "message.aliveworkplace.well_keeper.title_full" : "message.aliveworkplace.well_keeper.title_empty");
	}

	@Override
	protected void stop(ServerLevel level, Villager villager, long gameTime) {
		villager.getBrain().eraseMemory(MemoryModuleType.WALK_TARGET);
		if (villager.getMainHandItem().is(Items.WATER_BUCKET) || villager.getMainHandItem().is(Items.BUCKET)) {
			villager.setItemSlot(EquipmentSlot.MAINHAND, ItemStack.EMPTY);
		}
		State state = state(villager);
		state.walker.reset();
		state.target = null;
	}
}
