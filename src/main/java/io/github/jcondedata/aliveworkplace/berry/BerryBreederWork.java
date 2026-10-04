package io.github.jcondedata.aliveworkplace.berry;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.mojang.datafixers.util.Pair;
import io.github.jcondedata.aliveworkplace.build.BuilderBag;
import io.github.jcondedata.aliveworkplace.build.BuilderLevels;
import io.github.jcondedata.aliveworkplace.build.Builders;
import io.github.jcondedata.aliveworkplace.build.SupplyContainers;
import io.github.jcondedata.aliveworkplace.mc.Chat;
import io.github.jcondedata.aliveworkplace.registry.ModAttachments;
import io.github.jcondedata.aliveworkplace.work.PartnerShows;
import io.github.jcondedata.aliveworkplace.work.Partners;
import io.github.jcondedata.aliveworkplace.work.Walker;
import io.github.jcondedata.aliveworkplace.work.WorkerStatus;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.function.Predicate;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.behavior.Behavior;
import net.minecraft.world.entity.ai.behavior.BehaviorControl;
import net.minecraft.world.entity.ai.behavior.BlockPosTracker;
import net.minecraft.world.entity.ai.behavior.SetLookAndInteract;
import net.minecraft.world.entity.ai.behavior.UpdateActivityFromSchedule;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.memory.MemoryStatus;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

/**
 * The Berry Breeder's shift (ROADMAP 28.9). In her plot she plants the next step's two parent berries in alternating
 * rows, so each plant touches one of the other kind (Cobblemon then may grow the mutation on it), puts Growth and
 * Surprise Mulch from the chests on them, picks the fruit (the plant stays), notes any new berry in the village's hall,
 * and takes the harvest to the chests, where it is a parent for the next step. Plants of other kinds in the way of the
 * step are dug up (the berry comes back). Restartable at any tick: everything she knows is read from the world.
 */
public class BerryBreederWork extends Behavior<Villager> {
	private static final int LOOK_EVERY = 40;
	private static final float SPEED = 0.55f;
	private static final double REACH = 3.0;
	private static final int FETCH = 4;

	enum Task { NONE, PICK, DEPOSIT, PLANT, MULCH, CLEAR }

	private final Walker walker = new Walker(SPEED);
	private Task task = Task.NONE;
	@Nullable
	private BlockPos target;
	/** The berry (PLANT) or mulch (MULCH) the task needs. */
	private Predicate<ItemStack> needs = s -> false;
	private int lookTimer;
	private int timer = -1;
	private int stuck;
	private String idle = "growing";
	private Component[] idleArgs = {};

	public BerryBreederWork() {
		super(ImmutableMap.of(
			MemoryModuleType.JOB_SITE, MemoryStatus.VALUE_PRESENT,
			MemoryModuleType.WALK_TARGET, MemoryStatus.REGISTERED,
			MemoryModuleType.LOOK_TARGET, MemoryStatus.REGISTERED
		), 1200);
	}

	/** The Berry Breeder's WORK activity. */
	public static ImmutableList<Pair<Integer, ? extends BehaviorControl<? super Villager>>> packages(float speed) {
		return ImmutableList.<Pair<Integer, ? extends BehaviorControl<? super Villager>>>of(
			Pair.of(0, new BerryBreederWork()),
			Pair.of(10, SetLookAndInteract.create(EntityType.PLAYER, 4)),
			Pair.of(99, UpdateActivityFromSchedule.create())
		);
	}

	@Override
	protected boolean checkExtraStartConditions(ServerLevel level, Villager villager) {
		return !villager.isSleeping() && BerryBreeders.isBreeder(villager) && Builders.benchPos(villager).isPresent();
	}

	@Override
	protected boolean canStillUse(ServerLevel level, Villager villager, long gameTime) {
		return checkExtraStartConditions(level, villager);
	}

	@Override
	protected void start(ServerLevel level, Villager villager, long gameTime) {
		walker.reset();
		lookTimer = 0;
	}

	@Override
	protected void stop(ServerLevel level, Villager villager, long gameTime) {
		task = Task.NONE;
		villager.getBrain().eraseMemory(MemoryModuleType.WALK_TARGET);
	}

	@Override
	protected void tick(ServerLevel level, Villager villager, long gameTime) {
		BlockPos station = Builders.benchPos(villager).orElse(null);
		BerryChains.BerryData data = BerryBreeders.data();
		if (station == null) {
			return;
		}
		if (data == null || !BerryBreeders.ENABLED) {
			status(villager, "off");
			return;
		}
		List<BlockPos> own = SupplyContainers.find(level, station, null);
		if (own.isEmpty()) {
			status(villager, "no_chest");
			return;
		}
		BoundingBox plot = BerryBreeders.plot(villager);
		if (plot == null) {
			return;
		}
		BuilderBag bag = ModAttachments.BUILDER_BAG.getOrCreate(villager);
		if (task == Task.NONE) {
			if (--lookTimer > 0) {
				status(villager, idle, idleArgs);
				return;
			}
			lookTimer = LOOK_EVERY;
			choose(level, villager, data, own, plot, bag);
			walker.reset();
			timer = -1;
			stuck = 0;
			if (task == Task.NONE) {
				status(villager, idle, idleArgs);
				return;
			}
		}
		status(villager, task.name().toLowerCase());
		switch (task) {
			case PICK -> pick(level, villager, data, bag);
			case DEPOSIT -> deposit(level, villager, own, bag);
			case PLANT -> plant(level, villager, data, own, bag);
			case MULCH -> mulch(level, villager, data, own, bag);
			case CLEAR -> clear(level, villager, data, bag);
			default -> done();
		}
	}

	private void done() {
		task = Task.NONE;
		target = null;
		lookTimer = 5;
		walker.reset();
	}

	private void idle(String state, Component... args) {
		idle = state;
		idleArgs = args;
	}

	private void choose(ServerLevel level, Villager villager, BerryChains.BerryData data, List<BlockPos> own, BoundingBox plot, BuilderBag bag) {
		idle("growing");
		List<BlockPos> beds = BerryBreeders.beds(level, plot);
		BlockPos here = villager.blockPosition();
		// 1. Ripe fruit is picked.
		Optional<BlockPos> ripe = beds.stream().filter(p -> data.ripe(level.getBlockState(p)))
			.min(Comparator.comparingDouble(p -> p.distSqr(here)));
		if (ripe.isPresent()) {
			task = Task.PICK;
			target = ripe.get();
			return;
		}
		// 2. What she carries goes to the chests (the harvest, berries left from planting).
		if (!bag.isEmpty()) {
			task = Task.DEPOSIT;
			return;
		}
		ResourceLocation goal = BerryBreeders.goal(villager);
		if (goal == null) {
			idle("no_goal");
			return;
		}
		if (BerryBreeders.have(level, villager).contains(goal)) {
			idle("goal_reached", BerryBreeders.name(goal));
			return;
		}
		Optional<BerryChains.Mutation> step = BerryBreeders.step(level, villager);
		if (step.isEmpty()) {
			idle("out_of_reach", BerryBreeders.name(goal));
			return;
		}
		BerryChains.Mutation m = step.get();
		if (beds.isEmpty()) {
			idle("no_farmland");
			return;
		}
		// 3. The parents in alternating rows: an empty bed beside another bed, the kind its row takes.
		BlockPos free = null;
		ResourceLocation kind = null;
		boolean missing = false;
		for (BlockPos bed : beds.stream().sorted(Comparator.comparingDouble(p -> p.distSqr(here))).toList()) {
			if (!level.getBlockState(bed).isAir() || !pairs(beds, bed)) {
				continue;
			}
			ResourceLocation want = rowKind(bed, m);
			if (available(level, data, own, bag, want)) {
				free = bed;
				kind = want;
				break;
			}
			missing = true;
		}
		if (free != null) {
			ResourceLocation berry = kind;
			task = Task.PLANT;
			target = free;
			needs = s -> berry.equals(data.berryOf(s));
			return;
		}
		// 4. Mulch on the step's plants: Surprise first (mutations come four times as often), then Growth.
		Optional<BlockPos> bare = beds.stream().filter(p -> isParent(data.plantOf(level.getBlockState(p)), m) && !data.mulched(level, p))
			.min(Comparator.comparingDouble(p -> p.distSqr(here)));
		if (bare.isPresent() && (bag.stacks().stream().anyMatch(data::isMulch) || SupplyContainers.firstMatching(level, own, data::isMulch) != null)) {
			task = Task.MULCH;
			target = bare.get();
			needs = data::isMulch;
			return;
		}
		// 5. No room for the step: a plant of another kind is dug up (its berry goes back to the chests).
		boolean planted = beds.stream().anyMatch(p -> isParent(data.plantOf(level.getBlockState(p)), m));
		if (!missing && !planted) {
			Optional<BlockPos> other = beds.stream().filter(p -> {
				ResourceLocation id = data.plantOf(level.getBlockState(p));
				return id != null && !isParent(id, m);
			}).min(Comparator.comparingDouble(p -> p.distSqr(here)));
			if (other.isPresent()) {
				task = Task.CLEAR;
				target = other.get();
				return;
			}
		}
		if (missing && !planted) {
			ResourceLocation lacking = available(level, data, own, bag, m.a()) ? m.b() : m.a();
			idle("needs", BerryBreeders.name(lacking));
			return;
		}
		if (!planted) {
			idle("no_room", BerryBreeders.name(m.a()), BerryBreeders.name(m.b()));
			return;
		}
		idle("breeding", BerryBreeders.name(m.a()), BerryBreeders.name(m.b()), BerryBreeders.name(m.result()));
	}

	/** Rows run along z; the kind alternates from row to row (by x), so side by side the plants are of both kinds. */
	static ResourceLocation rowKind(BlockPos bed, BerryChains.Mutation m) {
		return (bed.getX() & 1) == 0 ? m.a() : m.b();
	}

	/** Whether {@code bed} has a bed of the other row beside it (or the plant there would touch only its own kind). */
	private static boolean pairs(List<BlockPos> beds, BlockPos bed) {
		return beds.contains(bed.east()) || beds.contains(bed.west());
	}

	private static boolean isParent(@Nullable ResourceLocation berry, BerryChains.Mutation m) {
		return berry != null && (berry.equals(m.a()) || berry.equals(m.b()));
	}

	private static boolean available(ServerLevel level, BerryChains.BerryData data, List<BlockPos> own, BuilderBag bag, ResourceLocation berry) {
		Predicate<ItemStack> test = s -> berry.equals(data.berryOf(s));
		return bag.stacks().stream().anyMatch(test) || SupplyContainers.firstMatching(level, own, test) != null;
	}

	/** Fetches what the task needs from the chests; true once it's in the bag. */
	private boolean fetch(ServerLevel level, Villager villager, List<BlockPos> own, BuilderBag bag) {
		if (bag.stacks().stream().anyMatch(needs)) {
			return true;
		}
		BlockPos chest = SupplyContainers.firstMatching(level, own, needs);
		if (chest == null) {
			done();
			return false;
		}
		if (walker.walkTo(level, villager, chest, REACH)) {
			ItemStack first = SupplyContainers.takeOne(level, List.of(chest), needs);
			if (!first.isEmpty()) {
				bag.add(first);
				bag.addAll(first.getItem(), SupplyContainers.extract(level, own, first.getItem(), FETCH - 1));
			}
			walker.reset();
		}
		return false;
	}

	private void pick(ServerLevel level, Villager villager, BerryChains.BerryData data, BuilderBag bag) {
		if (!data.ripe(level.getBlockState(target))) {
			done();
			return;
		}
		if (!reach(level, villager, target)) {
			return;
		}
		int picked = 0;
		for (ItemStack got : data.pick(level, target)) {
			ResourceLocation berry = data.berryOf(got);
			if (berry != null) {
				picked += got.getCount();
				if (BerryBreeders.note(level, villager, berry)) {
					found(level, villager, berry);
				}
			}
			ItemStack rest = bag.add(got);
			if (!rest.isEmpty()) {
				Block.popResource(level, target, rest);
			}
		}
		villager.swing(InteractionHand.MAIN_HAND);
		level.playSound(null, target, SoundEvents.SWEET_BERRY_BUSH_PICK_BERRIES, SoundSource.BLOCKS, 0.8f, 1f);
		if (picked > 0) {
			int before = ModAttachments.BERRIES_PICKED.getOrElse(villager, 0);
			ModAttachments.BERRIES_PICKED.set(villager, before + picked);
			if ((before + picked) / 16 > before / 16) {
				BuilderLevels.addXp(level, villager, 1, null);
			}
		}
		done();
	}

	/** A berry the village never had: she's pleased, and players nearby hear of it. */
	private static void found(ServerLevel level, Villager villager, ResourceLocation berry) {
		BuilderLevels.addXp(level, villager, 3, null);
		level.sendParticles(ParticleTypes.HAPPY_VILLAGER, villager.getX(), villager.getY() + 2.0, villager.getZ(), 10, 0.4, 0.3, 0.4, 0.0);
		level.playSound(null, villager, SoundEvents.VILLAGER_CELEBRATE, SoundSource.NEUTRAL, 1f, 1f);
		Component text = Component.translatable("message.aliveworkplace.berry_breeder.found", villager.getDisplayName(), BerryBreeders.name(berry))
			.withStyle(ChatFormatting.GREEN);
		for (ServerPlayer player : level.players()) {
			if (player.distanceToSqr(villager) <= 48 * 48) {
				Chat.system(player, text);
			}
		}
	}

	private void plant(ServerLevel level, Villager villager, BerryChains.BerryData data, List<BlockPos> own, BuilderBag bag) {
		if (!level.getBlockState(target).isAir()) {
			done();
			return;
		}
		if (!fetch(level, villager, own, bag)) {
			return;
		}
		if (!reach(level, villager, target)) {
			return;
		}
		ItemStack seed = bag.takeFirst(needs);
		if (seed.isEmpty() || !(seed.getItem() instanceof BlockItem item)) {
			done();
			return;
		}
		if (villager.getBoundingBox().intersects(new net.minecraft.world.phys.AABB(target))) {
			// Standing on the bed: step aside first.
			for (Direction d : Direction.Plane.HORIZONTAL) {
				if (Walker.canStand(level, target.relative(d))) {
					Walker.hop(level, villager, target.relative(d));
					break;
				}
			}
		}
		BlockPos ground = target.below();
		BlockPlaceContext context = new BlockPlaceContext(level, null, InteractionHand.MAIN_HAND, seed.copyWithCount(1),
			new BlockHitResult(Vec3.atCenterOf(ground).add(0, 0.5, 0), Direction.UP, ground, false));
		if (item.place(context).consumesAction()) {
			seed.shrink(1);
			villager.swing(InteractionHand.MAIN_HAND);
			level.playSound(null, target, SoundEvents.SWEET_BERRY_BUSH_PLACE, SoundSource.BLOCKS, 0.8f, 1f);
			// A Bug or Grass partner flits between the paired plants (28.9).
			PartnerShows.cue(villager, "pair", target);
		}
		if (!seed.isEmpty()) {
			bag.add(seed);
		}
		done();
	}

	private void mulch(ServerLevel level, Villager villager, BerryChains.BerryData data, List<BlockPos> own, BuilderBag bag) {
		if (data.plantOf(level.getBlockState(target)) == null || data.mulched(level, target)) {
			done();
			return;
		}
		// Surprise Mulch first, when there's some.
		if (!bag.stacks().stream().anyMatch(data::isMulch)) {
			ItemStack sample = SupplyContainers.firstMatching(level, own, data::isMulch) == null ? ItemStack.EMPTY : surprise(level, data, own);
			needs = sample.isEmpty() ? data::isMulch : s -> ItemStack.isSameItem(s, sample);
		}
		if (!fetch(level, villager, own, bag)) {
			return;
		}
		if (!reach(level, villager, target)) {
			return;
		}
		villager.getBrain().setMemory(MemoryModuleType.LOOK_TARGET, new BlockPosTracker(target));
		if (timer < 0) {
			timer = Math.max(8, (int) (BuilderLevels.delay(20, villager) * Partners.factor(villager)));
		}
		if (--timer > 0) {
			return;
		}
		ItemStack mulch = bag.takeFirst(data::isMulch);
		if (!mulch.isEmpty() && data.mulch(level, target, mulch)) {
			villager.swing(InteractionHand.MAIN_HAND);
			level.playSound(null, target, SoundEvents.ROOTED_DIRT_PLACE, SoundSource.BLOCKS, 0.8f, 1f);
			PartnerShows.cue(villager, "pair", target);
		}
		if (!mulch.isEmpty()) {
			bag.add(mulch);
		}
		done();
	}

	/** The Surprise Mulch in the chests, else any of hers. */
	private static ItemStack surprise(ServerLevel level, BerryChains.BerryData data, List<BlockPos> own) {
		for (var e : SupplyContainers.contents(level, own).keySet()) {
			ItemStack s = new ItemStack(e);
			if (data.isMulch(s) && net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(e).getPath().startsWith("surprise")) {
				return s;
			}
		}
		for (var e : SupplyContainers.contents(level, own).keySet()) {
			ItemStack s = new ItemStack(e);
			if (data.isMulch(s)) {
				return s;
			}
		}
		return ItemStack.EMPTY;
	}

	private void clear(ServerLevel level, Villager villager, BerryChains.BerryData data, BuilderBag bag) {
		if (data.plantOf(level.getBlockState(target)) == null) {
			done();
			return;
		}
		if (!reach(level, villager, target)) {
			return;
		}
		for (ItemStack got : Block.getDrops(level.getBlockState(target), level, target, level.getBlockEntity(target), villager, ItemStack.EMPTY)) {
			ItemStack rest = bag.add(got);
			if (!rest.isEmpty()) {
				Block.popResource(level, target, rest);
			}
		}
		level.destroyBlock(target, false, villager);
		villager.swing(InteractionHand.MAIN_HAND);
		done();
	}

	private void deposit(ServerLevel level, Villager villager, List<BlockPos> own, BuilderBag bag) {
		if (!walker.walkTo(level, villager, own.get(0), REACH)) {
			if (walker.noSpot() || ++stuck > 600) {
				done();
			}
			return;
		}
		for (ItemStack stack : bag.takeAll()) {
			ItemStack rest = SupplyContainers.insert(level, own, stack);
			if (!rest.isEmpty()) {
				Block.popResource(level, own.get(0).above(), rest);
			}
		}
		level.playSound(null, own.get(0), SoundEvents.CHEST_CLOSE, SoundSource.BLOCKS, 0.4f, 1f);
		done();
	}

	private boolean reach(ServerLevel level, Villager villager, BlockPos pos) {
		if (walker.reach(level, villager, pos, REACH)) {
			return true;
		}
		if (walker.noSpot() || ++stuck > 600) {
			done();
		}
		return false;
	}

	private static void status(Villager villager, String state, Component... args) {
		Component title = Component.translatable("message.aliveworkplace.berry_breeder.title", ModAttachments.BERRIES_PICKED.getOrElse(villager, 0));
		boolean warn = state.equals("no_chest") || state.equals("needs") || state.equals("out_of_reach") || state.equals("no_farmland")
			|| state.equals("off");
		WorkerStatus.set(villager, title, -1f, Component.translatable("message.aliveworkplace.berry_breeder.state." + state, (Object[]) args)
			.withStyle(warn ? ChatFormatting.YELLOW : ChatFormatting.GRAY));
	}
}
