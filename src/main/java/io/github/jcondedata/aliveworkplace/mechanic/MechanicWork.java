package io.github.jcondedata.aliveworkplace.mechanic;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.mojang.datafixers.util.Pair;
import io.github.jcondedata.aliveworkplace.blueprint.Blueprint;
import io.github.jcondedata.aliveworkplace.blueprint.BlueprintData;
import io.github.jcondedata.aliveworkplace.blueprint.BlueprintLibrary;
import io.github.jcondedata.aliveworkplace.blueprint.Blueprints;
import io.github.jcondedata.aliveworkplace.build.BuildSiteManager;
import io.github.jcondedata.aliveworkplace.build.BuilderLevels;
import io.github.jcondedata.aliveworkplace.build.MaterialRules;
import io.github.jcondedata.aliveworkplace.build.SupplyContainers;
import io.github.jcondedata.aliveworkplace.mc.Nbt;
import io.github.jcondedata.aliveworkplace.platform.ItemStores;
import io.github.jcondedata.aliveworkplace.platform.Platform;
import io.github.jcondedata.aliveworkplace.registry.ModAttachments;
import io.github.jcondedata.aliveworkplace.registry.ModVillagers;
import io.github.jcondedata.aliveworkplace.work.Walker;
import io.github.jcondedata.aliveworkplace.work.WorkerStatus;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.WeakHashMap;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.SectionPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.IntArrayTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundSource;
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
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import net.minecraft.world.phys.AABB;
import org.jetbrains.annotations.Nullable;

/**
 * The Mechanic's work (ROADMAP 39.2, design note M39), in their WORK activity, only with Create installed. From their
 * Toolbox they look after the village's Create machinery:
 * <ul>
 * <li><b>Mending</b> comes first: a shaft, gearbox, cogwheel or belt gone from a finished build near the toolbox (within
 * {@link #REACH}) is put back from the build's blueprint, the way builders mend a build ({@code build/Upkeep}): the
 * part's own item is fetched from the store by the toolbox (a belt costs one belt connector, on its controller segment),
 * carried there and placed with the blueprint's axis and facing. Parts go back in their order ({@link Kinetics#group}):
 * shafts before the cogwheels that sit on them, both before belts. A build a builder is repairing is left to them; a
 * part whose item isn't in the store waits, and the Mechanic says what's needed.</li>
 * <li>Otherwise they walk their <b>round</b> of the village's machines ({@link MechanicRound}, saved): each machine a
 * machine-work file names (39.3, {@link MachineWork#tend}) has what it made emptied into the store, and one that's
 * turning and holds nothing to work on gets its next batch from the store (a millstone wheat). A
 * machine that has stopped turning is reported on their status, never fed.</li>
 * </ul>
 * Restartable at any tick: only the round is saved; a part in hand when the work stops goes back to the store, and the
 * next part is looked for again. Config {@code mechanics} off: no new Mechanics (the toolbox is hidden from jobless
 * villagers, {@code WorkerLimits}) and those hired stand idle. {@link #crewLead} is 39.5's hook: the Mechanic who leads
 * the builders on a contraption.
 */
public class MechanicWork extends Behavior<Villager> {
	/** Config {@code mechanics}: off, no villager becomes a Mechanic, and Mechanics do nothing. */
	public static boolean ENABLED = true;
	/** How far from the toolbox a Mechanic looks after builds and machines. */
	public static int REACH = 32;
	/** Ticks between looks for work when there's none. */
	public static int LOOK_EVERY = 100;
	/** Ticks between two machines on the round. */
	public static int MACHINE_EVERY = 200;
	/** Ticks spent putting a part back or seeing to a machine. */
	static final int WORK_TICKS = 20;
	/** A machine counts as turning at this many RPM or more (design note M39, {@code min_speed}). */
	static final float MIN_SPEED = 1f;
	public static final int XP_PER_PART = 3;
	public static final int XP_PER_MACHINE = 1;
	private static final float SPEED = 0.6f;
	private static final ItemStores STORES = Platform.get().items();

	/** A drive-train part gone from a finished build: where, what goes there (with its data), what it costs, whose build. */
	public record Part(BlockPos pos, BlockState state, @Nullable CompoundTag nbt, Item cost, ResourceLocation structure) {
	}

	private enum Task {
		FETCH, PLACE, MACHINE
	}

	private static final class State {
		final Walker walker = new Walker(SPEED);
		Task task = Task.MACHINE;
		@Nullable
		Part part;
		@Nullable
		BlockPos store;
		@Nullable
		BlockPos machine;
		@Nullable
		Component machineName;
		long nextLook;
		long nextMachine;
		long busyUntil;
	}

	private static final Map<Villager, State> STATES = new WeakHashMap<>();

	public MechanicWork() {
		super(ImmutableMap.of(
			MemoryModuleType.WALK_TARGET, MemoryStatus.REGISTERED,
			MemoryModuleType.LOOK_TARGET, MemoryStatus.REGISTERED
		), 2400);
	}

	public static ImmutableList<Pair<Integer, ? extends BehaviorControl<? super Villager>>> packages(float speed) {
		return ImmutableList.<Pair<Integer, ? extends BehaviorControl<? super Villager>>>of(
			Pair.of(0, new MechanicWork()),
			Pair.of(10, SetLookAndInteract.create(EntityType.PLAYER, 4)),
			Pair.of(99, UpdateActivityFromSchedule.create())
		);
	}

	public static boolean isMechanic(Villager villager) {
		return !villager.isBaby() && ModVillagers.isMechanic(villager.getVillagerData().getProfession());
	}

	private static State state(Villager villager) {
		synchronized (STATES) {
			return STATES.computeIfAbsent(villager, v -> new State());
		}
	}

	/** The Mechanic's toolbox. */
	public static Optional<BlockPos> post(ServerLevel level, Villager villager) {
		return villager.getBrain().getMemory(MemoryModuleType.JOB_SITE).filter(g -> g.dimension() == level.dimension()).map(GlobalPos::pos);
	}

	// ---- 39.5's hook ----------------------------------------------------------------------------------------------

	/**
	 * The Mechanic who leads the crew when builders build a contraption at {@code site} (ROADMAP 39.5): the nearest one at
	 * work whose toolbox is within {@link #REACH} of it; empty without Create, with config {@code mechanics} off, or when
	 * the village has none. 39.5 decides what leading means (the build order, the turning check); nothing calls this yet.
	 */
	public static Optional<Villager> crewLead(ServerLevel level, BlockPos site) {
		if (!ENABLED || ModVillagers.MECHANIC == null) {
			return Optional.empty();
		}
		return level.getEntitiesOfClass(Villager.class, new AABB(site).inflate(REACH * 2), v -> isMechanic(v) && v.isAlive()
				&& post(level, v).filter(p -> p.closerThan(site, REACH)).isPresent())
			.stream().min(Comparator.comparingDouble(v -> post(level, v).get().distSqr(site)));
	}

	// ---- mending ----------------------------------------------------------------------------------------------------

	/**
	 * The drive-train parts gone from the finished builds reaching within {@link #REACH} of {@code post} (to the build's
	 * nearest block, not its corner: B598), in the order they go back: shafts and
	 * gearboxes, then cogwheels, then belts (a belt's controller segment first), lowest first. A build that has a build
	 * site open (a builder's repair) is left out.
	 */
	public static List<Part> missingParts(ServerLevel level, BlockPos post) {
		BuildSiteManager manager = BuildSiteManager.get(level);
		List<Part> out = new ArrayList<>();
		for (BuildSiteManager.Finished f : manager.finishedReaching(level, post, REACH)) {
			if (manager.all().stream().anyMatch(s -> s.placement().equals(f.placement()))) {
				continue; // a builder is on it
			}
			Optional<Blueprint> blueprint = BlueprintLibrary.get(level, f.structure());
			if (blueprint.isEmpty()) {
				continue;
			}
			BlockPos sourceOrigin = beltOrigin(blueprint.get());
			for (Blueprint.Entry entry : blueprint.get().blocks()) {
				if (!Kinetics.isDrivePart(entry.state())) {
					continue;
				}
				BlueprintData.Placement placement = f.placement();
				BlockPos world = toWorld(placement, entry.pos());
				if (!level.isLoaded(world)) {
					continue;
				}
				BlockState state = MaterialRules.forPlacement(MaterialRules.transformed(entry.state(), placement.mirror(), placement.rotation()));
				BlockState there = level.getBlockState(world);
				if (!there.canBeReplaced() || MaterialRules.matches(there, state)) {
					continue; // in place, or something else stands there now (a player's): not a hole to fill
				}
				boolean belt = Kinetics.isBelt(state);
				boolean controller = belt && entry.nbt() != null && Nbt.getBoolean(entry.nbt(), "IsController");
				CompoundTag nbt = belt ? beltData(entry.nbt(), sourceOrigin, placement) : null;
				out.add(new Part(world, state, nbt, Kinetics.cost(state, controller), f.structure()));
			}
		}
		out.sort(Comparator.<Part>comparingInt(p -> Kinetics.group(p.state()))
			.thenComparingInt(p -> p.nbt() != null && Nbt.getBoolean(p.nbt(), "IsController") ? 0 : 1)
			.thenComparingInt(p -> p.pos().getY())
			.thenComparingDouble(p -> p.pos().distSqr(post)));
		return out;
	}

	private static BlockPos toWorld(BlueprintData.Placement placement, BlockPos local) {
		return placement.origin().offset(StructureTemplate.transform(local, placement.mirror(), placement.rotation(), BlockPos.ZERO)).immutable();
	}

	/**
	 * Where the blueprint's own corner was in the world it was saved from, read off a belt's controller segment (its
	 * {@code Controller} is its own position there); null without a belt.
	 */
	@Nullable
	static BlockPos beltOrigin(Blueprint blueprint) {
		for (Blueprint.Entry entry : blueprint.blocks()) {
			if (Kinetics.isBelt(entry.state()) && entry.nbt() != null && Nbt.getBoolean(entry.nbt(), "IsController")) {
				BlockPos controller = readPos(entry.nbt().get("Controller"));
				if (controller != null) {
					return controller.subtract(entry.pos());
				}
			}
		}
		return null;
	}

	/**
	 * A belt segment's data for its place in this build: its {@code Index}, {@code Length}, {@code IsController} and
	 * {@code Casing} as saved, with {@code Controller} moved from where the blueprint was saved to where the build stands
	 * (by the build's offset and rotation), so the segment finds its controller. Never the items on it.
	 */
	@Nullable
	static CompoundTag beltData(@Nullable CompoundTag saved, @Nullable BlockPos sourceOrigin, BlueprintData.Placement placement) {
		if (saved == null) {
			return null;
		}
		CompoundTag out = new CompoundTag();
		for (String key : List.of("Index", "Length", "IsController", "Casing", "Covered")) {
			Tag value = saved.get(key);
			if (value != null) {
				out.put(key, value.copy());
			}
		}
		Tag controller = saved.get("Controller");
		BlockPos at = readPos(controller);
		if (at != null && sourceOrigin != null) {
			BlockPos world = toWorld(placement, at.subtract(sourceOrigin));
			out.put("Controller", controller instanceof CompoundTag ? xyz(world) : new IntArrayTag(new int[] {world.getX(), world.getY(), world.getZ()}));
		}
		return out;
	}

	@Nullable
	static BlockPos readPos(@Nullable Tag tag) {
		if (tag instanceof IntArrayTag array && array.getAsIntArray().length == 3) {
			int[] p = array.getAsIntArray();
			return new BlockPos(p[0], p[1], p[2]);
		}
		if (tag instanceof CompoundTag compound && compound.contains("X") && compound.contains("Y") && compound.contains("Z")) {
			return new BlockPos(Nbt.getInt(compound, "X"), Nbt.getInt(compound, "Y"), Nbt.getInt(compound, "Z"));
		}
		return null;
	}

	private static CompoundTag xyz(BlockPos pos) {
		CompoundTag tag = new CompoundTag();
		tag.putInt("X", pos.getX());
		tag.putInt("Y", pos.getY());
		tag.putInt("Z", pos.getZ());
		return tag;
	}

	/**
	 * Puts {@code part} back (the Mechanic is beside it, its item already taken from the store): the blueprint's block,
	 * shaped to its neighbours, with a belt's data. False if it can't go there now (something stands in the way, or
	 * nothing holds it up).
	 */
	public static boolean putBack(ServerLevel level, Villager villager, Part part) {
		BlockState there = level.getBlockState(part.pos());
		if (!there.canBeReplaced() || !part.state().canSurvive(level, part.pos())) {
			return false;
		}
		BlockState shaped = Block.updateFromNeighbourShapes(part.state(), level, part.pos());
		level.setBlock(part.pos(), shaped.isAir() ? part.state() : shaped, Block.UPDATE_ALL);
		if (part.nbt() != null && !part.nbt().isEmpty()) {
			BlockEntity be = level.getBlockEntity(part.pos());
			if (be != null) {
				CompoundTag tag = be.saveWithoutMetadata(level.registryAccess());
				tag.merge(part.nbt());
				try {
					be.loadWithComponents(tag, level.registryAccess());
					be.setChanged();
				} catch (RuntimeException ignored) {
					// Odd data never stops the mending: the block itself is back.
				}
			}
		}
		SoundType sound = part.state().getSoundType();
		level.playSound(null, part.pos(), sound.getPlaceSound(), SoundSource.BLOCKS, (sound.getVolume() + 1f) / 2f, sound.getPitch() * 0.8f);
		level.gameEvent(villager, GameEvent.BLOCK_PLACE, part.pos());
		villager.swing(InteractionHand.MAIN_HAND, true);
		BuilderLevels.addXp(level, villager, XP_PER_PART, null);
		return true;
	}

	// ---- the round --------------------------------------------------------------------------------------------------

	/** The machines on a round from {@code post}, nearest first: the village's loaded ones within {@link #REACH} (B243, {@link VillageMachines}). */
	public static List<BlockPos> findMachines(ServerLevel level, BlockPos post) {
		List<BlockPos> found = new ArrayList<>();
		for (int cx = SectionPos.blockToSectionCoord(post.getX() - REACH); cx <= SectionPos.blockToSectionCoord(post.getX() + REACH); cx++) {
			for (int cz = SectionPos.blockToSectionCoord(post.getZ() - REACH); cz <= SectionPos.blockToSectionCoord(post.getZ() + REACH); cz++) {
				LevelChunk chunk = level.getChunkSource().getChunkNow(cx, cz); // loaded only, without waiting
				if (chunk == null) {
					continue;
				}
				for (BlockEntity be : chunk.getBlockEntities().values()) {
					if (be.getBlockPos().closerThan(post, REACH) && Kinetics.isMachine(be.getBlockState()) && VillageMachines.village(level, be.getBlockPos())) {
						found.add(be.getBlockPos().immutable());
					}
				}
			}
		}
		found.sort(Comparator.comparingDouble(p -> p.distSqr(post)));
		return found;
	}

	/** The Mechanic's round, found again when it's new, has come back to its start or lost a machine. */
	public static MechanicRound round(ServerLevel level, Villager villager, BlockPos post) {
		MechanicRound round = ModAttachments.MECHANIC_ROUND.getOrElse(villager, MechanicRound.EMPTY);
		BlockPos current = round.current();
		if (current == null || round.atStart() || level.isLoaded(current) && (!Kinetics.isMachine(level.getBlockState(current)) || !VillageMachines.village(level, current))) {
			List<BlockPos> machines = findMachines(level, post);
			int next = current == null ? 0 : Math.max(0, machines.indexOf(current));
			round = new MechanicRound(machines, next);
			ModAttachments.MECHANIC_ROUND.set(villager, round);
		}
		return round;
	}

	/** Whether the machine at {@code pos} turns: its {@code Speed}, read by name from its saved data, is at least 1 RPM. */
	public static boolean turning(ServerLevel level, BlockPos pos) {
		BlockEntity be = level.getBlockEntity(pos);
		if (be == null) {
			return false;
		}
		CompoundTag tag = be.saveWithoutMetadata(level.registryAccess());
		return Math.abs(Nbt.getFloat(tag, "Speed")) >= MIN_SPEED;
	}

	/** What seeing to a machine did. */
	public enum Tended {
		/** Emptied into the store, refilled, or both. */
		WORKED,
		/** Nothing to take out and nothing to put in. */
		FINE,
		/** Not turning: nothing fed. */
		STOPPED,
		/** It isn't a machine any more. */
		GONE,
		/** The store is full: nothing taken out, nothing put in. */
		FULL
	}

	/**
	 * Sees to the machine at {@code machine} with the store by {@code post}, as its machine-work file says (39.3,
	 * {@link MachineWork#tend}): what it made goes to the store (as much as fits), and if it turns and holds nothing to
	 * work on it gets its next batch from the store.
	 */
	public static Tended tend(ServerLevel level, BlockPos post, BlockPos machine) {
		MachineWorkFiles.Machine file = MachineWorkFiles.forMachine(level.getBlockState(machine));
		if (file == null) {
			return Tended.GONE;
		}
		return MachineWork.tend(level, VillageMachines.stores(level, post), machine, file, post);
	}

	private static void drop(ServerLevel level, BlockPos pos, ItemStack stack) {
		ItemEntity item = new ItemEntity(level, pos.getX() + 0.5, pos.getY() + 1.1, pos.getZ() + 0.5, stack);
		item.setDefaultPickUpDelay();
		level.addFreshEntity(item);
	}

	// ---- the behaviour ----------------------------------------------------------------------------------------------

	@Override
	protected boolean checkExtraStartConditions(ServerLevel level, Villager villager) {
		if (!ENABLED || !isMechanic(villager)) {
			return false;
		}
		Optional<BlockPos> post = post(level, villager);
		State state = state(villager);
		if (post.isEmpty() || level.getGameTime() < state.nextLook) {
			return false;
		}
		return plan(level, villager, post.get(), state);
	}

	/** Picks the next part to put back, or else the next machine on the round when it's time. False: nothing to do. */
	private static boolean plan(ServerLevel level, Villager villager, BlockPos post, State state) {
		long now = level.getGameTime();
		state.part = null;
		state.machine = null;
		Part waiting = null;
		List<BlockPos> chests = null;
		for (Part part : missingParts(level, post)) {
			if (part.cost() == Items.AIR || villager.getMainHandItem().is(part.cost())) {
				state.part = part;
				state.task = Task.PLACE;
				return true;
			}
			if (chests == null) {
				chests = SupplyContainers.find(level, post, null);
			}
			BlockPos store = SupplyContainers.firstWith(level, chests, part.cost());
			if (store != null) {
				state.part = part;
				state.store = store;
				state.task = Task.FETCH;
				return true;
			}
			if (waiting == null) {
				waiting = part;
			}
		}
		if (now >= state.nextMachine) {
			MechanicRound round = round(level, villager, post);
			if (round.current() != null) {
				state.machine = round.current();
				state.machineName = level.getBlockState(round.current()).getBlock().getName();
				state.task = Task.MACHINE;
				return true;
			}
		}
		status(villager, waiting != null
			? Component.translatable("message.aliveworkplace.mechanic.state.waiting", waiting.cost().getDescription(), Blueprints.displayName(waiting.structure()))
			: Component.translatable("message.aliveworkplace.mechanic.state.idle"));
		state.nextLook = now + LOOK_EVERY;
		return false;
	}

	@Override
	protected boolean canStillUse(ServerLevel level, Villager villager, long gameTime) {
		if (!ENABLED || !isMechanic(villager) || post(level, villager).isEmpty()) {
			return false;
		}
		State state = state(villager);
		return state.part != null || state.machine != null;
	}

	@Override
	protected void tick(ServerLevel level, Villager villager, long gameTime) {
		State state = state(villager);
		Optional<BlockPos> post = post(level, villager);
		if (post.isEmpty() || gameTime < state.busyUntil) {
			return;
		}
		switch (state.task) {
			case FETCH -> fetch(level, villager, post.get(), state, gameTime);
			case PLACE -> place(level, villager, post.get(), state, gameTime);
			case MACHINE -> machine(level, villager, post.get(), state, gameTime);
		}
	}

	private static void fetch(ServerLevel level, Villager villager, BlockPos post, State state, long gameTime) {
		Part part = state.part;
		if (part == null || state.store == null) {
			state.part = null;
			return;
		}
		status(villager, Component.translatable("message.aliveworkplace.mechanic.state.fetch", part.cost().getDescription(),
			Blueprints.displayName(part.structure())));
		villager.getBrain().setMemory(MemoryModuleType.LOOK_TARGET, new BlockPosTracker(state.store));
		if (state.walker.reach(level, villager, state.store, 3.0)) {
			state.walker.reset();
			if (STORES.extract(level, List.of(state.store), part.cost(), 1) < 1) {
				state.part = null; // someone took it first: look again
				return;
			}
			villager.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(part.cost()));
			state.task = Task.PLACE;
		} else if (state.walker.noSpot()) {
			giveUp(state, gameTime);
		}
	}

	private static void place(ServerLevel level, Villager villager, BlockPos post, State state, long gameTime) {
		Part part = state.part;
		if (part == null) {
			return;
		}
		BlockState there = level.getBlockState(part.pos());
		if (!there.canBeReplaced() || MaterialRules.matches(there, part.state())) {
			state.part = null; // put back by someone else (a builder, a player): the part in hand goes back with stop()
			return;
		}
		status(villager, io.github.jcondedata.aliveworkplace.work.Words.articled("message.aliveworkplace.mechanic.state.mend", 0, part.state().getBlock().getName(),
			Blueprints.displayName(part.structure())));
		villager.getBrain().setMemory(MemoryModuleType.LOOK_TARGET, new BlockPosTracker(part.pos()));
		if (state.walker.reach(level, villager, part.pos(), 3.5)) {
			state.walker.reset();
			if (putBack(level, villager, part)) {
				if (part.cost() != Items.AIR && villager.getMainHandItem().is(part.cost())) {
					villager.setItemSlot(EquipmentSlot.MAINHAND, ItemStack.EMPTY);
				}
				state.busyUntil = gameTime + WORK_TICKS;
				state.part = null;
			} else {
				giveUp(state, gameTime);
			}
		} else if (state.walker.noSpot()) {
			giveUp(state, gameTime);
		}
	}

	private static void machine(ServerLevel level, Villager villager, BlockPos post, State state, long gameTime) {
		BlockPos machine = state.machine;
		if (machine == null) {
			return;
		}
		// the name it had when the round picked it: the spot may hold something else by now (B506: "seeing to the Air")
		Component name = state.machineName != null ? state.machineName : level.getBlockState(machine).getBlock().getName();
		if (level.isLoaded(machine) && !Kinetics.isMachine(level.getBlockState(machine))) {
			status(villager, Component.translatable("message.aliveworkplace.mechanic.state.gone", name));
			giveUp(state, gameTime); // the round finds the machines again next time, without this one
			return;
		}
		status(villager, Component.translatable("message.aliveworkplace.mechanic.state.machine", name));
		villager.getBrain().setMemory(MemoryModuleType.LOOK_TARGET, new BlockPosTracker(machine));
		if (state.walker.reach(level, villager, machine, 3.0) || state.walker.noSpot()) {
			boolean reached = !state.walker.noSpot();
			state.walker.reset();
			Tended done = reached ? tend(level, post, machine) : Tended.FINE;
			if (done == Tended.STOPPED) {
				status(villager, Component.translatable("message.aliveworkplace.mechanic.state.stopped", name));
			}
			if (done == Tended.WORKED) {
				villager.swing(InteractionHand.MAIN_HAND, true);
				BuilderLevels.addXp(level, villager, XP_PER_MACHINE, null);
			}
			ModAttachments.MECHANIC_ROUND.set(villager, round(level, villager, post).advance());
			state.machine = null;
			state.busyUntil = gameTime + WORK_TICKS;
			state.nextMachine = gameTime + MACHINE_EVERY;
		}
	}

	private static void giveUp(State state, long gameTime) {
		state.walker.reset();
		state.part = null;
		state.machine = null;
		state.nextLook = gameTime + LOOK_EVERY;
	}

	private static void status(Villager villager, Component line) {
		WorkerStatus.set(villager, Component.translatable("message.aliveworkplace.mechanic.title"), -1f, line.copy().withStyle(ChatFormatting.AQUA));
	}

	@Override
	protected void stop(ServerLevel level, Villager villager, long gameTime) {
		villager.getBrain().eraseMemory(MemoryModuleType.WALK_TARGET);
		State state = state(villager);
		state.walker.reset();
		state.part = null;
		state.machine = null;
		ItemStack held = villager.getMainHandItem();
		if (!held.isEmpty() && "create".equals(net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(held.getItem()).getNamespace())) {
			// a part fetched but not put back goes back to the store (or at the toolbox's foot)
			Optional<BlockPos> post = post(level, villager);
			ItemStack rest = post.isPresent() ? SupplyContainers.insert(level, SupplyContainers.find(level, post.get(), null), held.copy()) : held.copy();
			if (!rest.isEmpty()) {
				drop(level, post.orElse(villager.blockPosition()), rest);
			}
			villager.setItemSlot(EquipmentSlot.MAINHAND, ItemStack.EMPTY);
		}
	}
}
