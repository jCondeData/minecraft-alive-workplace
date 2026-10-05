package io.github.jcondedata.aliveworkplace.habitat;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.mojang.datafixers.util.Pair;
import io.github.jcondedata.aliveworkplace.build.BuilderBag;
import io.github.jcondedata.aliveworkplace.build.BuilderLevels;
import io.github.jcondedata.aliveworkplace.build.Builders;
import io.github.jcondedata.aliveworkplace.build.SupplyContainers;
import io.github.jcondedata.aliveworkplace.registry.ModAttachments;
import io.github.jcondedata.aliveworkplace.registry.ModVillagers;
import io.github.jcondedata.aliveworkplace.work.PartnerShows;
import io.github.jcondedata.aliveworkplace.work.Walker;
import io.github.jcondedata.aliveworkplace.work.WorkerStatus;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.function.Predicate;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.behavior.Behavior;
import net.minecraft.world.entity.ai.behavior.BehaviorControl;
import net.minecraft.world.entity.ai.behavior.SetLookAndInteract;
import net.minecraft.world.entity.ai.behavior.UpdateActivityFromSchedule;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.memory.MemoryStatus;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

/**
 * The Habitat Keeper's shift (ROADMAP 28.10). Round her Pasture Block she:
 * <ol>
 * <li>scans for Saccharine logs, {@link HabitatKeepers#SCAN_PER_TICK} blocks a tick at most, and remembers them;</li>
 * <li>looks round for wild Pokémon every minute ({@link HabitatKeepers#sight});</li>
 * <li>keeps a Poké Snack (seasoned for her lure if she can get one) set out on each lure spot, from her chests or the
 * Camp Cook's, and sets out another when one is eaten up;</li>
 * <li>slathers each bare Saccharine log with a honey bottle from her chests (the glass bottle goes back);</li>
 * <li>plants a Saccharine sapling from her chests round each lure spot that has none.</li>
 * </ol>
 * What she carries is in her bag (saved), and what to do with it is worked out again each time, so she can restart at
 * any tick.
 */
public class HabitatKeeperWork extends Behavior<Villager> {
	private static final int LOOK_EVERY = 40;
	private static final float SPEED = 0.55f;
	private static final double REACH = 3.0;

	private final Walker walker = new Walker(SPEED);
	private int lookTimer;
	private int stuck;
	private int scanCursor;
	private long nextSighting = -1;

	public HabitatKeeperWork() {
		super(ImmutableMap.of(
			MemoryModuleType.JOB_SITE, MemoryStatus.VALUE_PRESENT,
			MemoryModuleType.WALK_TARGET, MemoryStatus.REGISTERED,
			MemoryModuleType.LOOK_TARGET, MemoryStatus.REGISTERED
		), 1200);
	}

	/** The Habitat Keeper's WORK activity. */
	public static ImmutableList<Pair<Integer, ? extends BehaviorControl<? super Villager>>> packages(float speed) {
		return ImmutableList.<Pair<Integer, ? extends BehaviorControl<? super Villager>>>of(
			Pair.of(0, new HabitatKeeperWork()),
			Pair.of(10, SetLookAndInteract.create(EntityType.PLAYER, 4)),
			Pair.of(99, UpdateActivityFromSchedule.create())
		);
	}

	@Override
	protected boolean checkExtraStartConditions(ServerLevel level, Villager villager) {
		return !villager.isSleeping() && HabitatKeepers.isKeeper(villager) && Builders.benchPos(villager).isPresent();
	}

	@Override
	protected boolean canStillUse(ServerLevel level, Villager villager, long gameTime) {
		return checkExtraStartConditions(level, villager);
	}

	@Override
	protected void start(ServerLevel level, Villager villager, long gameTime) {
		walker.reset();
		lookTimer = 0;
		stuck = 0;
	}

	@Override
	protected void stop(ServerLevel level, Villager villager, long gameTime) {
		villager.getBrain().eraseMemory(MemoryModuleType.WALK_TARGET);
	}

	@Override
	protected void tick(ServerLevel level, Villager villager, long gameTime) {
		BlockPos pasture = Builders.benchPos(villager).orElse(null);
		if (pasture == null) {
			return;
		}
		HabitatKeepers.Snacks snacks = HabitatKeepers.snacks();
		if (snacks == null || !HabitatKeepers.ENABLED) {
			status(villager, "off");
			return;
		}
		if (!BuiltInRegistries.BLOCK.getKey(level.getBlockState(pasture).getBlock()).equals(ModVillagers.PASTURE_BLOCK)) {
			status(villager, "no_pasture");
			return;
		}
		scanLogs(level, villager, pasture);
		if (gameTime >= nextSighting) {
			nextSighting = gameTime + HabitatKeepers.SIGHT_EVERY;
			HabitatKeepers.sight(level, villager, pasture);
		}
		List<BlockPos> chests = SupplyContainers.find(level, pasture, null);
		BuilderBag bag = ModAttachments.BUILDER_BAG.getOrCreate(villager);
		List<BlockPos> spots = HabitatKeepers.lureSpots(level, villager, pasture);
		// 1. What she carries: a snack to a spot, honey to a log, a sapling into the ground, the rest back to her chests.
		ItemStack carried = bag.stacks().stream().filter(s -> !s.isEmpty()).findFirst().orElse(ItemStack.EMPTY);
		if (!carried.isEmpty()) {
			carry(level, villager, pasture, chests, spots, bag, carried, snacks);
			return;
		}
		if (--lookTimer > 0) {
			return;
		}
		lookTimer = LOOK_EVERY;
		// 2. The next thing to fetch: a snack for an empty spot, honey for a bare log, a sapling for a spot without one.
		String lure = HabitatKeepers.lure(villager);
		BlockPos emptySpot = spots.stream().filter(p -> !snacks.isSnackBlock(level.getBlockState(p)) && HabitatKeepers.free(level, p))
			.findFirst().orElse(null);
		if (emptySpot != null) {
			List<BlockPos> sources = HabitatKeepers.snackChests(level, pasture);
			if (fetch(level, villager, sources, s -> HabitatKeepers.seasonedFor(s, lure))
				|| lure != null && fetch(level, villager, sources, snacks::isSnack)) {
				return;
			}
		}
		BlockPos bareLog = bareLog(level, villager, pasture);
		if (bareLog != null && fetch(level, villager, chests, s -> s.is(Items.HONEY_BOTTLE))) {
			return;
		}
		boolean saplingWanted = spots.stream().anyMatch(p -> HabitatKeepers.saplingSpot(level, p) != null);
		if (saplingWanted && fetch(level, villager, chests, HabitatKeepers::isSapling)) {
			return;
		}
		if (emptySpot != null) {
			status(villager, lure == null ? "needs_snack" : "needs_seasoned", HabitatKeepers.lureName(lure));
		} else if (bareLog != null) {
			status(villager, "needs_honey");
		} else if (spots.isEmpty()) {
			status(villager, "no_spot");
		} else {
			status(villager, "watching", Component.literal(String.valueOf(spots.size())),
				Component.literal(String.valueOf(ModAttachments.HONEY_LOGS.getOrElse(villager, List.of()).size())));
		}
	}

	/** Walks to the first of {@code containers} with something {@code wanted} and takes one: true while that's under way. */
	private boolean fetch(ServerLevel level, Villager villager, List<BlockPos> containers, Predicate<ItemStack> wanted) {
		BlockPos chest = SupplyContainers.firstMatching(level, containers, wanted);
		if (chest == null) {
			return false;
		}
		status(villager, "fetching");
		if (!reach(level, villager, chest)) {
			lookTimer = 0; // keep walking
			return true;
		}
		ItemStack taken = SupplyContainers.takeOne(level, List.of(chest), wanted);
		if (!taken.isEmpty()) {
			ModAttachments.BUILDER_BAG.getOrCreate(villager).add(taken);
		}
		lookTimer = 0;
		return true;
	}

	private void carry(ServerLevel level, Villager villager, BlockPos pasture, List<BlockPos> chests, List<BlockPos> spots, BuilderBag bag,
			ItemStack carried, HabitatKeepers.Snacks snacks) {
		if (snacks.isSnack(carried)) {
			BlockPos spot = spots.stream().filter(p -> !snacks.isSnackBlock(level.getBlockState(p)) && HabitatKeepers.free(level, p))
				.min(Comparator.comparingDouble(p -> p.distSqr(villager.blockPosition()))).orElse(null);
			if (spot != null) {
				status(villager, "setting_out");
				if (!reach(level, villager, spot)) {
					return;
				}
				ItemStack one = carried.copyWithCount(1);
				if (snacks.setOut(level, spot, one, HabitatKeepers.faceToward(spot, pasture), villager)) {
					bag.remove(carried.getItem(), 1);
					BuilderLevels.addXp(level, villager, 1, null);
					PartnerShows.cue(villager, "set_out_snack", spot);
				}
				walker.reset();
				return;
			}
		} else if (carried.is(Items.HONEY_BOTTLE)) {
			BlockPos log = bareLog(level, villager, pasture);
			if (log != null) {
				status(villager, "slathering");
				if (!walker.reachingUp(4).reach(level, villager, log, REACH + 1) && !nearEnough(villager, log)) {
					stuckCheck();
					return;
				}
				if (HabitatKeepers.slather(level, log, HabitatKeepers.faceToward(log, villager.blockPosition()))) {
					bag.remove(Items.HONEY_BOTTLE, 1);
					bag.add(new ItemStack(Items.GLASS_BOTTLE));
					BuilderLevels.addXp(level, villager, 1, null);
					PartnerShows.cue(villager, "slather", log);
				}
				walker.reset();
				return;
			}
		} else if (HabitatKeepers.isSapling(carried)) {
			BlockPos plant = spots.stream().map(p -> HabitatKeepers.saplingSpot(level, p)).filter(java.util.Objects::nonNull).findFirst().orElse(null);
			if (plant != null) {
				status(villager, "planting");
				if (!reach(level, villager, plant)) {
					return;
				}
				if (HabitatKeepers.plantSapling(level, plant)) {
					bag.remove(carried.getItem(), 1);
					BuilderLevels.addXp(level, villager, 1, null);
				}
				walker.reset();
				return;
			}
		}
		// Nothing to do with it (or a glass bottle): back to her chests.
		if (chests.isEmpty()) {
			status(villager, "no_chest");
			return;
		}
		status(villager, "putting_away");
		if (!reach(level, villager, chests.get(0))) {
			return;
		}
		for (ItemStack stack : new ArrayList<>(bag.stacks())) {
			if (!stack.isEmpty()) {
				ItemStack rest = SupplyContainers.insert(level, chests, stack.copy());
				bag.remove(stack.getItem(), stack.getCount() - rest.getCount());
			}
		}
		walker.reset();
	}

	private static boolean nearEnough(Villager villager, BlockPos pos) {
		return villager.blockPosition().distSqr(pos) <= 2 * 2 + 4 * 4;
	}

	/** The first remembered log that is bare now (forgetting those that aren't Saccharine any more). */
	@Nullable
	private static BlockPos bareLog(ServerLevel level, Villager villager, BlockPos pasture) {
		List<BlockPos> logs = ModAttachments.HONEY_LOGS.getOrElse(villager, List.of());
		BlockPos bare = null;
		List<BlockPos> kept = new ArrayList<>();
		for (BlockPos log : logs) {
			if (!level.isLoaded(log)) {
				kept.add(log);
				continue;
			}
			BlockState state = level.getBlockState(log);
			if (HabitatKeepers.isBareLog(state)) {
				kept.add(log);
				if (bare == null) {
					bare = log;
				}
			} else if (HabitatKeepers.isSlathered(state)) {
				kept.add(log);
			}
		}
		if (kept.size() != logs.size()) {
			ModAttachments.HONEY_LOGS.set(villager, List.copyOf(kept));
		}
		return bare;
	}

	/** One tick's worth of the log scan, adding what it finds to her remembered logs. */
	private void scanLogs(ServerLevel level, Villager villager, BlockPos pasture) {
		List<BlockPos> logs = new ArrayList<>(ModAttachments.HONEY_LOGS.getOrElse(villager, List.of()));
		int before = logs.size();
		scanCursor = HabitatKeepers.scan(level, pasture, scanCursor, HabitatKeepers.SCAN_PER_TICK, logs);
		if (logs.size() != before) {
			ModAttachments.HONEY_LOGS.set(villager, List.copyOf(logs));
		}
	}

	private boolean reach(ServerLevel level, Villager villager, BlockPos pos) {
		if (walker.reach(level, villager, pos, REACH)) {
			stuck = 0;
			return true;
		}
		stuckCheck();
		return false;
	}

	private void stuckCheck() {
		if (walker.noSpot() || ++stuck > 600) {
			stuck = 0;
			walker.reset();
		}
	}

	private static void status(Villager villager, String state, Component... args) {
		Component title = Component.translatable("message.aliveworkplace.habitat_keeper.title", HabitatKeepers.lureName(HabitatKeepers.lure(villager)));
		boolean warn = state.startsWith("needs") || state.equals("no_chest") || state.equals("no_pasture") || state.equals("off") || state.equals("no_spot");
		WorkerStatus.set(villager, title, -1f, Component.translatable("message.aliveworkplace.habitat_keeper.state." + state, (Object[]) args)
			.withStyle(warn ? ChatFormatting.YELLOW : ChatFormatting.GRAY));
	}
}
