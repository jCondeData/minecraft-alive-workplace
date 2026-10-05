package io.github.jcondedata.aliveworkplace.gem;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.mojang.datafixers.util.Pair;
import io.github.jcondedata.aliveworkplace.build.BuilderBag;
import io.github.jcondedata.aliveworkplace.build.BuilderLevels;
import io.github.jcondedata.aliveworkplace.build.Builders;
import io.github.jcondedata.aliveworkplace.build.SupplyContainers;
import io.github.jcondedata.aliveworkplace.registry.ModAttachments;
import io.github.jcondedata.aliveworkplace.work.PartnerShows;
import io.github.jcondedata.aliveworkplace.work.Walker;
import io.github.jcondedata.aliveworkplace.work.WorkerStatus;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
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
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

/**
 * The Gem Grower's shift (ROADMAP 28.11). Round her stonecutter she:
 * <ol>
 * <li>scans for gem beds ({@link GemGrowers#SCAN_PER_TICK} blocks a tick at most) and remembers them;</li>
 * <li>picks each ripe cluster (its own loot), never the block it grew on, and carries the harvest to her chests;</li>
 * <li>plants each bed that's planted with something (a tumblestone, a Type Gem Block) from her chests, up to its count;</li>
 * <li>with Cobblemon 1.8 makes Blank TMs at the stonecutter from the shards and glass in her chests, up to 8.</li>
 * </ol>
 * What she carries is in her bag (saved), and what to do with it is worked out again each time, so she can restart at
 * any tick.
 */
public class GemGrowerWork extends Behavior<Villager> {
	private static final int LOOK_EVERY = 40;
	private static final float SPEED = 0.55f;
	private static final double REACH = 3.0;
	/** How long a Blank TM takes her at the stonecutter. */
	private static final int TM_EVERY = 200;

	private final Walker walker = new Walker(SPEED);
	private int lookTimer;
	private int stuck;
	private int scanCursor;
	private long nextTm;

	public GemGrowerWork() {
		super(ImmutableMap.of(
			MemoryModuleType.JOB_SITE, MemoryStatus.VALUE_PRESENT,
			MemoryModuleType.WALK_TARGET, MemoryStatus.REGISTERED,
			MemoryModuleType.LOOK_TARGET, MemoryStatus.REGISTERED
		), 1200);
	}

	/** The Gem Grower's WORK activity. */
	public static ImmutableList<Pair<Integer, ? extends BehaviorControl<? super Villager>>> packages(float speed) {
		return ImmutableList.<Pair<Integer, ? extends BehaviorControl<? super Villager>>>of(
			Pair.of(0, new GemGrowerWork()),
			Pair.of(10, SetLookAndInteract.create(EntityType.PLAYER, 4)),
			Pair.of(99, UpdateActivityFromSchedule.create())
		);
	}

	@Override
	protected boolean checkExtraStartConditions(ServerLevel level, Villager villager) {
		return !villager.isSleeping() && GemGrowers.isGrower(villager) && Builders.benchPos(villager).isPresent();
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
		BlockPos cutter = Builders.benchPos(villager).orElse(null);
		if (cutter == null) {
			return;
		}
		if (!GemGrowers.ENABLED) {
			status(villager, "off");
			return;
		}
		if (!GemGrowers.isStonecutter(level.getBlockState(cutter))) {
			status(villager, "no_stonecutter");
			return;
		}
		scanBeds(level, villager, cutter);
		List<BlockPos> chests = SupplyContainers.find(level, cutter, null);
		BuilderBag bag = ModAttachments.BUILDER_BAG.getOrCreate(villager);
		// 1. What she carries: a planting to a bed that wants one, the rest (her picking) back to her chests.
		ItemStack carried = bag.stacks().stream().filter(s -> !s.isEmpty()).findFirst().orElse(ItemStack.EMPTY);
		if (!carried.isEmpty()) {
			carry(level, villager, chests, bag, carried);
			return;
		}
		if (--lookTimer > 0) {
			return;
		}
		lookTimer = LOOK_EVERY;
		List<BlockPos> beds = liveBeds(level, villager);
		// 2. A ripe cluster to pick.
		for (BlockPos touch : beds) {
			for (GemBeds.Bed bed : GemGrowers.bedsAt(level.getBlockState(touch), villager)) {
				BlockPos ripe = GemGrowers.ripe(level, touch, bed);
				if (ripe == null) {
					continue;
				}
				status(villager, "picking", Component.translatable(bed.nameKey()));
				if (!reach(level, villager, ripe)) {
					lookTimer = 0;
					return;
				}
				PartnerShows.cue(villager, "pick_cluster", ripe);
				List<ItemStack> drops = GemGrowers.pick(level, ripe, bed, villager);
				for (ItemStack drop : drops) {
					ItemStack rest = bag.add(drop);
					if (!rest.isEmpty()) {
						net.minecraft.world.level.block.Block.popResource(level, ripe, rest);
					}
				}
				if (!drops.isEmpty()) {
					ModAttachments.GEMS_PICKED.set(villager, ModAttachments.GEMS_PICKED.getOrElse(villager, 0) + 1);
					BuilderLevels.addXp(level, villager, 1, null);
				}
				walker.reset();
				lookTimer = 0;
				return;
			}
		}
		// 3. A bed to plant, with what it's planted with in her chests (with no orders, any she has the makings for).
		boolean wanting = false;
		for (BlockPos touch : beds) {
			List<GemBeds.Bed> here = GemGrowers.bedsAt(level.getBlockState(touch), villager);
			for (GemBeds.Bed bed : here) {
				if (!GemGrowers.wantsPlanting(level, touch, bed, here)) {
					continue;
				}
				wanting = true;
				if (fetch(level, villager, chests, s -> bed.plantItem().map(s::is).orElse(false))) {
					return;
				}
			}
		}
		// 4. Blank TMs at the stonecutter (Cobblemon 1.8).
		if (gameTime >= nextTm && GemGrowers.blankTmRecipe(level).isPresent()) {
			nextTm = gameTime + TM_EVERY;
			if (GemGrowers.makeBlankTms(level, chests)) {
				status(villager, "blank_tm");
				level.playSound(null, cutter, net.minecraft.sounds.SoundEvents.UI_STONECUTTER_TAKE_RESULT, net.minecraft.sounds.SoundSource.NEUTRAL, 1f, 1f);
				BuilderLevels.addXp(level, villager, 1, null);
				return;
			}
		}
		if (beds.isEmpty()) {
			status(villager, "no_beds");
		} else if (wanting) {
			status(villager, "needs_planting");
		} else {
			status(villager, "tending", Component.literal(String.valueOf(beds.size())));
		}
	}

	/** Her remembered beds that are still beds (forgetting those whose touch block is gone). */
	private static List<BlockPos> liveBeds(ServerLevel level, Villager villager) {
		List<BlockPos> beds = GemGrowers.beds(villager);
		List<BlockPos> kept = new ArrayList<>();
		for (BlockPos pos : beds) {
			if (!level.isLoaded(pos) || GemGrowers.isTouch(level.getBlockState(pos))) {
				kept.add(pos);
			}
		}
		if (kept.size() != beds.size()) {
			ModAttachments.GEM_BEDS.set(villager, List.copyOf(kept));
		}
		return kept.stream().filter(level::isLoaded).toList();
	}

	/** Walks to the first of {@code containers} with something {@code wanted} and takes one: true while that's under way. */
	private boolean fetch(ServerLevel level, Villager villager, List<BlockPos> containers, java.util.function.Predicate<ItemStack> wanted) {
		BlockPos chest = SupplyContainers.firstMatching(level, containers, wanted);
		if (chest == null) {
			return false;
		}
		status(villager, "fetching");
		if (!reach(level, villager, chest)) {
			lookTimer = 0;
			return true;
		}
		ItemStack taken = SupplyContainers.takeOne(level, List.of(chest), wanted);
		if (!taken.isEmpty()) {
			ModAttachments.BUILDER_BAG.getOrCreate(villager).add(taken);
		}
		lookTimer = 0;
		return true;
	}

	private void carry(ServerLevel level, Villager villager, List<BlockPos> chests, BuilderBag bag, ItemStack carried) {
		BlockPos target = plantingFor(level, villager, carried);
		GemBeds.Bed bed = target == null ? null : bedFor(level.getBlockState(target), villager, carried);
		if (target != null && bed != null) {
			status(villager, "planting", Component.translatable(bed.nameKey()));
			if (!reach(level, villager, target)) {
				return;
			}
			if (GemGrowers.plant(level, target, bed)) {
				bag.remove(carried.getItem(), 1);
				BuilderLevels.addXp(level, villager, 1, null);
			}
			walker.reset();
			return;
		}
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

	/** The bed (touch block) that {@code carried} is planted in and that wants a planting, or null. */
	@Nullable
	private static BlockPos plantingFor(ServerLevel level, Villager villager, ItemStack carried) {
		for (BlockPos touch : liveBeds(level, villager)) {
			BlockState state = level.getBlockState(touch);
			List<GemBeds.Bed> here = GemGrowers.bedsAt(state, villager);
			GemBeds.Bed bed = bedFor(state, villager, carried);
			if (bed != null && GemGrowers.wantsPlanting(level, touch, bed, here)) {
				return touch;
			}
		}
		return null;
	}

	/** Which of the beds at a touch block {@code carried} is planted in, or null. */
	@Nullable
	private static GemBeds.Bed bedFor(BlockState touch, Villager villager, ItemStack carried) {
		return GemGrowers.bedsAt(touch, villager).stream().filter(b -> b.plantItem().map(carried::is).orElse(false)).findFirst().orElse(null);
	}

	/** One tick's worth of the bed scan, adding what it finds to her remembered beds. */
	private void scanBeds(ServerLevel level, Villager villager, BlockPos cutter) {
		List<BlockPos> beds = new ArrayList<>(GemGrowers.beds(villager));
		int before = beds.size();
		scanCursor = GemGrowers.scan(level, cutter, scanCursor, GemGrowers.SCAN_PER_TICK, beds);
		if (beds.size() != before) {
			ModAttachments.GEM_BEDS.set(villager, List.copyOf(beds));
		}
	}

	private boolean reach(ServerLevel level, Villager villager, BlockPos pos) {
		if (walker.reach(level, villager, pos, REACH)) {
			stuck = 0;
			return true;
		}
		if (walker.noSpot() || ++stuck > 600) {
			stuck = 0;
			walker.reset();
		}
		return false;
	}

	private static void status(Villager villager, String state, Component... args) {
		Component title = Component.translatable("message.aliveworkplace.gem_grower.title",
			ModAttachments.GEMS_PICKED.getOrElse(villager, 0));
		boolean warn = state.startsWith("needs") || state.equals("no_chest") || state.equals("no_stonecutter") || state.equals("off")
			|| state.equals("no_beds");
		WorkerStatus.set(villager, title, -1f, Component.translatable("message.aliveworkplace.gem_grower.state." + state, (Object[]) args)
			.withStyle(warn ? ChatFormatting.YELLOW : ChatFormatting.GRAY));
	}
}
