package io.github.jcondedata.aliveworkplace.mechanic;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.mojang.datafixers.util.Pair;
import io.github.jcondedata.aliveworkplace.build.SupplyContainers;
import io.github.jcondedata.aliveworkplace.mc.Nbt;
import io.github.jcondedata.aliveworkplace.mc.Recipes;
import io.github.jcondedata.aliveworkplace.platform.ItemStores;
import io.github.jcondedata.aliveworkplace.platform.Platform;
import io.github.jcondedata.aliveworkplace.registry.ModAttachments;
import io.github.jcondedata.aliveworkplace.work.Gated;
import io.github.jcondedata.aliveworkplace.work.Walker;
import io.github.jcondedata.aliveworkplace.work.WorkerStatus;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.WeakHashMap;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.SectionPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.ai.behavior.Behavior;
import net.minecraft.world.entity.ai.behavior.BehaviorControl;
import net.minecraft.world.entity.ai.behavior.BlockPosTracker;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.memory.MemoryStatus;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.phys.AABB;
import org.jetbrains.annotations.Nullable;

/**
 * Villagers work Create's machines (ROADMAP 39.3, design note M39), as the {@code machine_work} files say
 * ({@link MachineWorkFiles}): the farmer mills, the armorer presses, the chef mixes and the miner crushes. In their WORK
 * activity, a worker whose job has a file looks for that machine within {@link #REACH} of their workstation (the
 * village's own only, as is the store: inside a building the builders finished, {@link VillageMachines}, B243); while it
 * turns (its {@code Speed}, read by name, at least the file's {@code min_speed}) and the store by the workstation has
 * room, they take the input from the store, carry it to the machine's feed spot (the machine, its depot or basin, or
 * the gap they drop it into) and bring what the machine made back to the store. What a machine makes is read from
 * Create's own recipes of the file's type through the recipe manager: a recipe whose result the store wants (fewer than
 * the file's {@code keep}) and whose ingredients the store holds. A machine that doesn't turn is fed nothing; a full
 * store pauses the work. A look for machine work comes every {@link #LOOK_EVERY} ticks; while a trip is on, the job's
 * own work and vanilla's routine wait (and pick up again after it, as they do after any pause).
 *
 * <p>Restartable at any tick: the trip is saved on the worker ({@link MachineJob}) with what they carry, and goes on
 * from its stage after a reload. The Mechanic's round (39.2) uses {@link #tend}, the same work done on the
 * spot. Config {@code machineWork} off: nothing is fed by anyone.
 */
public class MachineWork extends Behavior<Villager> {
	/** Config {@code machineWork}. */
	public static boolean ENABLED = true;
	/** How far from the workstation a worker looks for their machine. */
	public static int REACH = 32;
	/** Ticks between looks for machine work when there's none. */
	public static int LOOK_EVERY = 100;
	private static final float SPEED = 0.6f;
	private static final ItemStores STORES = Platform.get().items();
	private static final Map<Villager, State> STATES = new WeakHashMap<>();

	private static final class State {
		final Walker walker = new Walker(SPEED);
		long nextLook;
	}

	MachineWork() {
		super(ImmutableMap.of(
			MemoryModuleType.WALK_TARGET, MemoryStatus.REGISTERED,
			MemoryModuleType.LOOK_TARGET, MemoryStatus.REGISTERED
		), 2400);
	}

	/** A worker killed mid-trip drops what they carried where they fell (it's never lost with them). */
	public static void init() {
		Platform.get().afterDeath((entity, source) -> {
			if (entity instanceof Villager villager && villager.level() instanceof ServerLevel level) {
				MachineJob job = ModAttachments.MACHINE_JOB.get(villager);
				if (job == null) {
					return;
				}
				ModAttachments.MACHINE_JOB.remove(villager);
				for (ItemStack stack : job.carried()) {
					villager.spawnAtLocation(stack.copy(), 0.5f);
				}
			}
		});
	}

	/**
	 * B244, every server tick of a villager (from the villager's own tick): a trip to a machine that can't go on any more
	 * ends here, with what was carried put back. A trip goes on while machine work is on (config {@code machineWork} and
	 * the Create expansion), Create is installed and the worker still has the job of the trip's machine-work file. One whose
	 * worker lost that job (their workstation broken at Novice, or another job taken) or whose work was switched off would
	 * otherwise keep its load in the saved record for good, since nobody runs the trip any more: the load goes into the
	 * village's store by the worker's workstation (or by where they stand, when the workstation is gone) and what doesn't
	 * fit, or has no store, drops at the worker's feet. A trip whose machine was broken, or whose file is gone, is still
	 * run, and brings its load back on foot.
	 */
	public static void tickTrip(Villager villager) {
		if (!(villager.level() instanceof ServerLevel level)) {
			return;
		}
		MachineJob job = ModAttachments.MACHINE_JOB.get(villager);
		if (job == null || goesOn(villager, job)) {
			return;
		}
		endTrip(level, villager, job);
	}

	/** Whether {@code job} can go on: machine work on, Create installed and the worker still in the job of its file. */
	static boolean goesOn(Villager villager, MachineJob job) {
		if (!ENABLED || !Platform.get().isModLoaded(Kinetics.CREATE)) {
			return false;
		}
		List<MachineWorkFiles.Machine> files = files(villager);
		if (files.isEmpty()) {
			return false;
		}
		MachineWorkFiles.Machine file = file(job.file());
		return file == null || files.contains(file); // a file gone since: the trip itself brings the load back
	}

	/** Ends {@code job} where the worker is: its load into the store by their workstation (else by them), the rest dropped. */
	static void endTrip(ServerLevel level, Villager villager, MachineJob job) {
		ModAttachments.MACHINE_JOB.remove(villager);
		state(villager).walker.reset();
		if (job.carried().isEmpty()) {
			return;
		}
		BlockPos here = villager.blockPosition();
		List<BlockPos> chests = post(level, villager).map(p -> VillageMachines.stores(level, p)).orElse(List.of());
		if (chests.isEmpty()) {
			chests = VillageMachines.stores(level, here);
		}
		toStore(level, chests, job.carried(), here);
	}

	/**
	 * {@code work} with machine work added (only with Create installed, for a job a machine-work file names): ours first, and the rest of the
	 * activity waiting while a trip to a machine is on (the schedule update always runs).
	 */
	public static ImmutableList<Pair<Integer, ? extends BehaviorControl<? super Villager>>> packages(net.minecraft.world.entity.npc.VillagerProfession profession,
			ImmutableList<Pair<Integer, ? extends BehaviorControl<? super Villager>>> work) {
		if (work == null || !Platform.get().isModLoaded(Kinetics.CREATE)
				|| MachineWorkFiles.forJob(BuiltInRegistries.VILLAGER_PROFESSION.getKey(profession)).isEmpty()) {
			return work;
		}
		ImmutableList.Builder<Pair<Integer, ? extends BehaviorControl<? super Villager>>> rest = ImmutableList.builder();
		for (Pair<Integer, ? extends BehaviorControl<? super Villager>> entry : work) {
			if (entry.getFirst() >= 99) {
				rest.add(entry);
				continue;
			}
			rest.add(Pair.of(entry.getFirst(), new Gated<Villager>(v -> !busy(v), entry.getSecond())));
		}
		return ImmutableList.<Pair<Integer, ? extends BehaviorControl<? super Villager>>>builder()
			.add(Pair.of(0, new MachineWork()))
			.addAll(rest.build())
			.build();
	}

	/** Whether {@code villager} is on a trip to a machine. */
	public static boolean busy(Villager villager) {
		return ModAttachments.MACHINE_JOB.has(villager);
	}

	private static State state(Villager villager) {
		synchronized (STATES) {
			return STATES.computeIfAbsent(villager, v -> new State());
		}
	}

	// ---- the machine ------------------------------------------------------------------------------------------------

	/** Whether the machine at {@code pos} turns at {@code minSpeed} RPM or more ({@code Speed} in its saved data). */
	public static boolean turning(ServerLevel level, BlockPos pos, float minSpeed) {
		BlockEntity be = level.getBlockEntity(pos);
		if (be == null) {
			return false;
		}
		CompoundTag tag = be.saveWithoutMetadata(level.registryAccess());
		return Math.abs(Nbt.getFloat(tag, "Speed")) >= minSpeed;
	}

	/** The recipes of the file's type whose result the file says the village wants. */
	public static List<Recipe<?>> recipes(ServerLevel level, MachineWorkFiles.Machine file) {
		RecipeType<?> type = BuiltInRegistries.RECIPE_TYPE.get(file.recipeType());
		List<Recipe<?>> out = new ArrayList<>();
		for (RecipeHolder<?> holder : Recipes.all(Recipes.manager(level))) {
			Recipe<?> recipe = holder.value();
			if (recipe.getType() == type && file.makes(Recipes.result(recipe, level.registryAccess()).getItem())) {
				out.add(recipe);
			}
		}
		return out;
	}

	/** Every recipe of the file's type: what the machine can work on, whoever put it in. */
	static List<Recipe<?>> allRecipes(ServerLevel level, MachineWorkFiles.Machine file) {
		RecipeType<?> type = BuiltInRegistries.RECIPE_TYPE.get(file.recipeType());
		List<Recipe<?>> out = new ArrayList<>();
		for (RecipeHolder<?> holder : Recipes.all(Recipes.manager(level))) {
			if (holder.value().getType() == type) {
				out.add(holder.value());
			}
		}
		return out;
	}

	/**
	 * Whether {@code stack} in the machine is still to be worked on: an ingredient of any recipe of the machine's type
	 * (a player's gold ingots on the press's depot too, not only the village's iron), unless it's something the file
	 * wants made. Anything else is what the machine made.
	 */
	static boolean inWork(MachineWorkFiles.Machine file, List<Recipe<?>> all, ItemStack stack) {
		return !file.makes(stack.getItem()) && isInput(all, stack);
	}

	/** Whether {@code stack} is something the machine works on (an ingredient of one of its recipes). */
	static boolean isInput(List<Recipe<?>> recipes, ItemStack stack) {
		for (Recipe<?> recipe : recipes) {
			for (Ingredient ingredient : Recipes.ingredients(recipe)) {
				if (!ingredient.isEmpty() && ingredient.test(stack)) {
					return true;
				}
			}
		}
		return false;
	}

	static BlockPos feedPos(MachineWorkFiles.Machine file, BlockPos machine) {
		return machine.offset(file.feed().at());
	}

	static BlockPos collectPos(MachineWorkFiles.Machine file, BlockPos machine) {
		return machine.offset(file.collect().at());
	}

	/** Whether the machine is whole: the block it is, and the feed and collect spots' blocks where the file names them. */
	public static boolean whole(ServerLevel level, MachineWorkFiles.Machine file, BlockPos machine) {
		return level.getBlockState(machine).is(file.machine())
			&& (file.feed().block() == null || level.getBlockState(feedPos(file, machine)).is(file.feed().block()))
			&& (file.collect().block() == null || level.getBlockState(collectPos(file, machine)).is(file.collect().block()));
	}

	private static List<ItemEntity> dropped(ServerLevel level, BlockPos pos) {
		return level.getEntitiesOfClass(ItemEntity.class, new AABB(pos).inflate(0.25), ItemEntity::isAlive);
	}

	/** Whether there's something made to take from the machine. */
	public static boolean hasOutput(ServerLevel level, MachineWorkFiles.Machine file, BlockPos machine, List<Recipe<?>> recipes) {
		BlockPos at = collectPos(file, machine);
		List<Recipe<?>> all = allRecipes(level, file);
		if (file.collect().drop()) {
			return dropped(level, at).stream().anyMatch(e -> !inWork(file, all, e.getItem()));
		}
		return STORES.hasMatching(level, at, s -> !inWork(file, all, s));
	}

	/**
	 * Takes what the machine made (never what it's still working on, nor a player's input waiting in it), at most
	 * {@code maxStacks} stacks: what the store has room for stays in the machine, never dropped by the store to despawn.
	 */
	static List<ItemStack> takeOutput(ServerLevel level, MachineWorkFiles.Machine file, BlockPos machine, List<Recipe<?>> recipes, int maxStacks) {
		if (maxStacks <= 0) {
			return new ArrayList<>();
		}
		BlockPos at = collectPos(file, machine);
		List<Recipe<?>> all = allRecipes(level, file);
		if (!file.collect().drop()) {
			return STORES.takeMatching(level, at, s -> !inWork(file, all, s), Math.min(9, maxStacks));
		}
		List<ItemStack> out = new ArrayList<>();
		for (ItemEntity e : dropped(level, at)) {
			if (out.size() >= maxStacks) {
				break;
			}
			if (!inWork(file, all, e.getItem())) {
				out.add(e.getItem().copy());
				e.discard();
			}
		}
		return out;
	}

	/** Whether the machine still holds input to work on, the village's or a player's (then it isn't fed more). */
	static boolean holdsInput(ServerLevel level, MachineWorkFiles.Machine file, BlockPos machine, List<Recipe<?>> recipes) {
		BlockPos at = feedPos(file, machine);
		List<Recipe<?>> all = allRecipes(level, file);
		if (file.feed().drop()) {
			return dropped(level, at).stream().anyMatch(e -> inWork(file, all, e.getItem()));
		}
		return STORES.hasMatching(level, at, s -> inWork(file, all, s));
	}

	/**
	 * What to take from the store for the machine's next batch: a recipe whose result the store wants and whose
	 * ingredients it holds, as many times over as the file's {@code batch} allows (and the store holds). Empty: nothing.
	 */
	public static List<ItemStack> plan(ServerLevel level, MachineWorkFiles.Machine file, List<BlockPos> chests, List<Recipe<?>> recipes) {
		for (Recipe<?> recipe : recipes) {
			Item made = Recipes.result(recipe, level.registryAccess()).getItem();
			int keep = file.makes().stream().filter(m -> m.item() == made).mapToInt(MachineWorkFiles.Make::keep).findFirst().orElse(0);
			if (SupplyContainers.count(level, chests, made) >= keep) {
				continue;
			}
			Map<Item, Integer> needs = new LinkedHashMap<>();
			boolean ok = true;
			for (Ingredient ingredient : Recipes.ingredients(recipe)) {
				if (ingredient.isEmpty()) {
					continue;
				}
				Item pick = null;
				for (ItemStack option : Recipes.options(ingredient)) {
					if (SupplyContainers.count(level, chests, option.getItem()) >= needs.getOrDefault(option.getItem(), 0) + 1) {
						pick = option.getItem();
						break;
					}
				}
				if (pick == null) {
					ok = false;
					break;
				}
				needs.merge(pick, 1, Integer::sum);
			}
			if (!ok || needs.isEmpty()) {
				continue;
			}
			int sets = file.batch();
			for (Map.Entry<Item, Integer> e : needs.entrySet()) {
				sets = (int) Math.min(sets, SupplyContainers.count(level, chests, e.getKey()) / e.getValue());
			}
			if (sets <= 0) {
				continue;
			}
			List<ItemStack> out = new ArrayList<>();
			for (Map.Entry<Item, Integer> e : needs.entrySet()) {
				out.add(new ItemStack(e.getKey(), Math.min(e.getValue() * sets, e.getKey().getDefaultMaxStackSize())));
			}
			return out;
		}
		return List.of();
	}

	/** Takes the planned items from the store; returns what was taken. */
	static List<ItemStack> take(ServerLevel level, List<BlockPos> chests, List<ItemStack> planned) {
		List<ItemStack> out = new ArrayList<>();
		for (ItemStack want : planned) {
			int got = SupplyContainers.extract(level, chests, want.getItem(), want.getCount());
			if (got > 0) {
				out.add(new ItemStack(want.getItem(), got));
			}
		}
		return out;
	}

	/** Puts {@code carried} into the machine's feed spot (and the file's fluid into its tank); returns what didn't go in. */
	static List<ItemStack> feed(ServerLevel level, MachineWorkFiles.Machine file, BlockPos machine, List<ItemStack> carried) {
		BlockPos at = feedPos(file, machine);
		MachineWorkFiles.Spot spot = file.feed();
		if (spot.fluid() != null) {
			long missing = spot.fluidMb() - STORES.fluidMb(level, at, spot.fluid());
			if (missing > 0) {
				STORES.pourFluid(level, at, spot.fluid(), missing);
			}
		}
		List<ItemStack> rest = new ArrayList<>();
		for (ItemStack stack : carried) {
			if (spot.drop()) {
				ItemEntity e = new ItemEntity(level, at.getX() + 0.5, at.getY() + 0.5, at.getZ() + 0.5, stack.copy(), 0, 0, 0);
				level.addFreshEntity(e);
			} else {
				ItemStack left = STORES.insert(level, List.of(at), stack.copy());
				if (!left.isEmpty()) {
					rest.add(left);
				}
			}
		}
		return rest;
	}

	/** Puts {@code stacks} into the store; what doesn't fit is dropped at {@code at}. */
	static void toStore(ServerLevel level, List<BlockPos> chests, List<ItemStack> stacks, BlockPos at) {
		for (ItemStack stack : stacks) {
			ItemStack rest = chests.isEmpty() ? stack.copy() : SupplyContainers.insert(level, chests, stack.copy());
			if (!rest.isEmpty()) {
				ItemEntity item = new ItemEntity(level, at.getX() + 0.5, at.getY() + 1.1, at.getZ() + 0.5, rest);
				item.setDefaultPickUpDelay();
				level.addFreshEntity(item);
			}
		}
	}

	/**
	 * Sees to the machine at {@code machine} on the spot (the Mechanic's round): what it made goes to the store, and while
	 * it turns and holds nothing to work on it gets its next batch from the store. A full store: nothing either way.
	 */
	public static MechanicWork.Tended tend(ServerLevel level, List<BlockPos> chests, BlockPos machine, MachineWorkFiles.Machine file, BlockPos dropAt) {
		if (!ENABLED) {
			return MechanicWork.Tended.FINE;
		}
		if (!whole(level, file, machine)) {
			return level.getBlockState(machine).is(file.machine()) ? MechanicWork.Tended.FINE : MechanicWork.Tended.GONE;
		}
		if (!VillageMachines.village(level, machine)) {
			return MechanicWork.Tended.FINE; // a player's own machine (B243): never fed, never emptied
		}
		if (chests.isEmpty() || SupplyContainers.freeSlots(level, chests) <= 0) {
			return MechanicWork.Tended.FULL;
		}
		List<Recipe<?>> recipes = recipes(level, file);
		boolean worked = false;
		List<ItemStack> made = takeOutput(level, file, machine, recipes, SupplyContainers.freeSlots(level, chests));
		if (!made.isEmpty()) {
			toStore(level, chests, made, dropAt);
			worked = true;
		}
		boolean turning = turning(level, machine, file.minSpeed());
		if (turning && !holdsInput(level, file, machine, recipes)) {
			List<ItemStack> taken = take(level, chests, plan(level, file, chests, recipes));
			if (!taken.isEmpty()) {
				List<ItemStack> rest = feed(level, file, machine, taken);
				toStore(level, chests, rest, dropAt);
				worked |= rest.size() < taken.size() || rest.stream().mapToInt(ItemStack::getCount).sum() < taken.stream().mapToInt(ItemStack::getCount).sum();
			}
		}
		return worked ? MechanicWork.Tended.WORKED : turning ? MechanicWork.Tended.FINE : MechanicWork.Tended.STOPPED;
	}

	/**
	 * The village's machines of {@code file} within {@link #REACH} of {@code post}, in loaded chunks, nearest first: only
	 * those inside a building the builders finished ({@link VillageMachines}, B243), never a player's own.
	 */
	public static List<BlockPos> findMachines(ServerLevel level, BlockPos post, MachineWorkFiles.Machine file) {
		List<BlockPos> found = new ArrayList<>();
		for (int cx = SectionPos.blockToSectionCoord(post.getX() - REACH); cx <= SectionPos.blockToSectionCoord(post.getX() + REACH); cx++) {
			for (int cz = SectionPos.blockToSectionCoord(post.getZ() - REACH); cz <= SectionPos.blockToSectionCoord(post.getZ() + REACH); cz++) {
				LevelChunk chunk = level.getChunkSource().getChunkNow(cx, cz);
				if (chunk == null) {
					continue;
				}
				for (BlockEntity be : chunk.getBlockEntities().values()) {
					if (be.getBlockPos().closerThan(post, REACH) && be.getBlockState().is(file.machine()) && VillageMachines.village(level, be.getBlockPos())) {
						found.add(be.getBlockPos().immutable());
					}
				}
			}
		}
		found.sort(Comparator.comparingDouble(p -> p.distSqr(post)));
		return found;
	}

	// ---- the worker -------------------------------------------------------------------------------------------------

	private static Optional<BlockPos> post(ServerLevel level, Villager villager) {
		return villager.getBrain().getMemory(MemoryModuleType.JOB_SITE).filter(g -> g.dimension() == level.dimension()).map(GlobalPos::pos);
	}

	@Nullable
	private static MachineWorkFiles.Machine file(ResourceLocation id) {
		for (MachineWorkFiles.Machine m : MachineWorkFiles.all()) {
			if (m.id().equals(id)) {
				return m;
			}
		}
		return null;
	}

	private static List<MachineWorkFiles.Machine> files(Villager villager) {
		if (villager.isBaby()) {
			return List.of();
		}
		return MachineWorkFiles.forJob(BuiltInRegistries.VILLAGER_PROFESSION.getKey(villager.getVillagerData().getProfession()));
	}

	@Override
	protected boolean checkExtraStartConditions(ServerLevel level, Villager villager) {
		if (!ENABLED) {
			return false;
		}
		if (busy(villager)) {
			return true; // a trip saved mid-way goes on
		}
		List<MachineWorkFiles.Machine> files = files(villager);
		State state = state(villager);
		if (files.isEmpty() || level.getGameTime() < state.nextLook) {
			return false;
		}
		state.nextLook = level.getGameTime() + LOOK_EVERY;
		Optional<BlockPos> post = post(level, villager);
		if (post.isEmpty()) {
			return false;
		}
		List<BlockPos> chests = VillageMachines.stores(level, post.get());
		if (chests.isEmpty()) {
			return false;
		}
		boolean full = SupplyContainers.freeSlots(level, chests) <= 0;
		for (MachineWorkFiles.Machine file : files) {
			for (BlockPos machine : findMachines(level, post.get(), file)) {
				if (!whole(level, file, machine)) {
					continue;
				}
				if (full) {
					status(villager, Component.translatable("message.aliveworkplace.machine_work.full", file.machine().getName()));
					return false;
				}
				List<Recipe<?>> recipes = recipes(level, file);
				if (hasOutput(level, file, machine, recipes)) {
					ModAttachments.MACHINE_JOB.set(villager, new MachineJob(machine, file.id(), MachineJob.Stage.FEED, List.of()));
					return true;
				}
				if (!turning(level, machine, file.minSpeed())) {
					status(villager, Component.translatable("message.aliveworkplace.machine_work.stopped", file.machine().getName()));
					continue;
				}
				if (!holdsInput(level, file, machine, recipes) && !plan(level, file, chests, recipes).isEmpty()) {
					ModAttachments.MACHINE_JOB.set(villager, new MachineJob(machine, file.id(), MachineJob.Stage.FETCH, List.of()));
					return true;
				}
			}
		}
		return false;
	}

	@Override
	protected boolean canStillUse(ServerLevel level, Villager villager, long gameTime) {
		return ENABLED && busy(villager);
	}

	@Override
	protected void tick(ServerLevel level, Villager villager, long gameTime) {
		MachineJob job = ModAttachments.MACHINE_JOB.get(villager);
		if (job == null) {
			return;
		}
		State state = state(villager);
		BlockPos post = post(level, villager).orElse(villager.blockPosition());
		List<BlockPos> chests = VillageMachines.stores(level, post);
		MachineWorkFiles.Machine file = file(job.file());
		if (file == null && job.stage() != MachineJob.Stage.RETURN) {
			job = save(villager, job.with(MachineJob.Stage.RETURN, job.carried())); // its file is gone: bring back what's carried
		}
		Component name = machineName(level, file, job.machine());
		boolean gone = gone(level, file, job.machine());
		if (gone && job.stage() == MachineJob.Stage.FEED) {
			job = save(villager, job.with(MachineJob.Stage.RETURN, job.carried())); // broken on the way: bring the load back (B506)
			state.walker.reset();
		}
		switch (job.stage()) {
			case FETCH -> {
				List<Recipe<?>> recipes = recipes(level, file);
				List<ItemStack> planned = plan(level, file, chests, recipes);
				if (gone || planned.isEmpty() || SupplyContainers.freeSlots(level, chests) <= 0 || !VillageMachines.village(level, job.machine())) {
					done(villager, state);
					return;
				}
				BlockPos store = SupplyContainers.firstWith(level, chests, planned.get(0).getItem());
				if (store == null) {
					done(villager, state);
					return;
				}
				status(villager, Component.translatable("message.aliveworkplace.machine_work.fetch", planned.get(0).getHoverName(), name));
				villager.getBrain().setMemory(MemoryModuleType.LOOK_TARGET, new BlockPosTracker(store));
				if (state.walker.reach(level, villager, store, 3.0) || state.walker.noSpot()) {
					state.walker.reset();
					List<ItemStack> taken = take(level, chests, planned);
					if (taken.isEmpty()) {
						done(villager, state);
						return;
					}
					save(villager, job.with(MachineJob.Stage.FEED, taken));
				}
			}
			case FEED -> {
				status(villager, Component.translatable("message.aliveworkplace.machine_work.feed", name));
				villager.getBrain().setMemory(MemoryModuleType.LOOK_TARGET, new BlockPosTracker(job.machine()));
				if (state.walker.reach(level, villager, job.machine(), 3.0) || state.walker.noSpot()) {
					state.walker.reset();
					List<ItemStack> carried = new ArrayList<>(job.carried());
					List<ItemStack> back = new ArrayList<>();
					if (file != null && whole(level, file, job.machine()) && VillageMachines.village(level, job.machine())) {
						List<Recipe<?>> recipes = recipes(level, file);
						if (!carried.isEmpty() && turning(level, job.machine(), file.minSpeed())) {
							back.addAll(feed(level, file, job.machine(), carried));
							villager.swing(InteractionHand.MAIN_HAND, true);
						} else {
							back.addAll(carried); // stopped since: the input goes back to the store
						}
						back.addAll(takeOutput(level, file, job.machine(), recipes, SupplyContainers.freeSlots(level, chests) - back.size()));
					} else {
						back.addAll(carried);
					}
					if (back.isEmpty()) {
						done(villager, state);
					} else {
						save(villager, job.with(MachineJob.Stage.RETURN, back));
					}
				}
			}
			case RETURN -> {
				BlockPos store = chests.isEmpty() ? post : chests.get(0);
				status(villager, Component.translatable(gone ? "message.aliveworkplace.machine_work.gone" : "message.aliveworkplace.machine_work.return", name));
				villager.getBrain().setMemory(MemoryModuleType.LOOK_TARGET, new BlockPosTracker(store));
				if (state.walker.reach(level, villager, store, 3.0) || state.walker.noSpot()) {
					state.walker.reset();
					toStore(level, chests, job.carried(), post);
					done(villager, state);
				}
			}
		}
	}

	/**
	 * The name of the machine a trip goes to: its file's machine block, read from the file and not from the spot, which
	 * may hold something else by now (B506: "working the Air" when the millstone was broken on the way). With its file
	 * gone, the block at the spot if it is still a machine, else just "machine".
	 */
	static Component machineName(ServerLevel level, @Nullable MachineWorkFiles.Machine file, BlockPos machine) {
		if (file != null) {
			return file.machine().getName();
		}
		BlockState there = level.getBlockState(machine);
		return MachineWorkFiles.forMachine(there) != null ? there.getBlock().getName() : Component.translatable("message.aliveworkplace.machine_work.machine");
	}

	/** Whether the machine of {@code file} at {@code machine} was broken (or replaced) since the trip began. */
	static boolean gone(ServerLevel level, @Nullable MachineWorkFiles.Machine file, BlockPos machine) {
		return file != null && level.isLoaded(machine) && !level.getBlockState(machine).is(file.machine());
	}

	private static MachineJob save(Villager villager, MachineJob job) {
		ModAttachments.MACHINE_JOB.set(villager, job);
		return job;
	}

	private static void done(Villager villager, State state) {
		ModAttachments.MACHINE_JOB.remove(villager);
		state.walker.reset();
	}

	private static void status(Villager villager, Component line) {
		WorkerStatus.set(villager, Component.translatable("message.aliveworkplace.machine_work.title"), -1f, line.copy().withStyle(ChatFormatting.AQUA));
	}

	@Override
	protected void stop(ServerLevel level, Villager villager, long gameTime) {
		villager.getBrain().eraseMemory(MemoryModuleType.WALK_TARGET);
		state(villager).walker.reset();
		// a trip cut short (the work day ended) stays saved with what's carried, and goes on next time; one that can't go
		// on (machine work switched off, the job lost) ends in tickTrip, which puts the load back (B244)
	}
}
