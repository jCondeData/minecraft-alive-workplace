package io.github.jcondedata.aliveworkplace.city;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.mojang.datafixers.util.Pair;
import io.github.jcondedata.aliveworkplace.hall.VillageHallBlockEntity;
import io.github.jcondedata.aliveworkplace.hall.VillageHalls;
import io.github.jcondedata.aliveworkplace.registry.ModAttachments;
import io.github.jcondedata.aliveworkplace.registry.ModItems;
import io.github.jcondedata.aliveworkplace.registry.ModVillagers;
import io.github.jcondedata.aliveworkplace.work.Walker;
import io.github.jcondedata.aliveworkplace.work.WorkerStatus;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.function.BiFunction;
import java.util.function.Function;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ai.behavior.Behavior;
import net.minecraft.world.entity.ai.behavior.BehaviorControl;
import net.minecraft.world.entity.ai.behavior.SetLookAndInteract;
import net.minecraft.world.entity.ai.behavior.UpdateActivityFromSchedule;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.memory.MemoryStatus;
import net.minecraft.world.entity.ai.village.poi.PoiManager;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.levelgen.Heightmap;

/**
 * The Steward's WORK activity (27.5). Each morning he walks his rounds holding the City Plan (vanilla shows a villager's
 * held item in his crossed arms): up to {@link #MAX_STOPS} stops — his open sites, the plan's zones, the storehouse —
 * {@link #STOP_TICKS} at each, then back to the hall, where he plans and stays until evening. The planning itself is
 * 27.6–27.9: they fill {@link #PLANNER} (what he's planning, shown over his head, and the work it does) and
 * {@link #OPEN_SITES} (his builds still going up). Restartable at any tick: the day of his last finished round is saved
 * on him; a round broken off starts again.
 */
public class StewardWork extends Behavior<Villager> {
	public static final int MAX_STOPS = 6;
	/** Three seconds at each stop. */
	public static final int STOP_TICKS = 60;
	/** How close to a stop counts as there. */
	static final double STOP_REACH = 3.0;

	/** A stop on his rounds: where, and what to call it over his head. */
	public record Stop(BlockPos pos, Component what) {
	}

	/** His builds still open (27.6 fills this in): none until he plans any. */
	public static BiFunction<ServerLevel, Villager, List<BlockPos>> OPEN_SITES = (level, steward) -> List.of();

	/**
	 * At the hall after his rounds, every second: plans (27.6–27.9 replace this) and returns the line shown over his head,
	 * such as "Planning a Stone House: 3 villagers have no bed".
	 */
	public static Planner PLANNER = (level, steward, hall) -> Component.translatable("message.aliveworkplace.steward.state.reading");

	/** How often he plans at the hall: every second. */
	public static final int PLAN_EVERY = 20;
	/** B85: the tick of the second that carries the second part of his planning (the first part goes on tick 0). */
	public static final int SECOND_PART = PLAN_EVERY / 2;

	@FunctionalInterface
	public interface Planner {
		/** All his planning at once (tests and commands; at the hall he plans {@linkplain #plan(ServerLevel, Villager, BlockPos, int) spread over the second}). */
		Component plan(ServerLevel level, Villager steward, BlockPos hall);

		/**
		 * B85: the same planning spread over the second, so no single tick carries all of it: {@code part} 0 (the tick
		 * that is a multiple of {@link #PLAN_EVERY}) and {@link #SECOND_PART}, half a second later. Returns his line, or
		 * null to keep the last one. A planner with no parts does it all at part 0.
		 */
		@org.jetbrains.annotations.Nullable
		default Component plan(ServerLevel level, Villager steward, BlockPos hall, int part) {
			return part == 0 ? plan(level, steward, hall) : null;
		}
	}

	public static ImmutableList<Pair<Integer, ? extends BehaviorControl<? super Villager>>> packages(float speed) {
		return ImmutableList.<Pair<Integer, ? extends BehaviorControl<? super Villager>>>of(
			Pair.of(0, new StewardWork()),
			Pair.of(10, SetLookAndInteract.create(EntityType.PLAYER, 4)),
			Pair.of(99, UpdateActivityFromSchedule.create())
		);
	}

	private final Walker walker = new Walker(0.5f);
	/** Today's stops, worked out when the round starts (null: not on his rounds). */
	private List<Stop> stops;
	private int stop;
	private int wait;
	/** Ticks on the way to the current stop: a stop he can't reach is given up after {@link #GIVE_UP}. */
	private int travel;
	static final int GIVE_UP = 600;
	/** His line over his head while planning, as shown (null until he first plans at the hall). */
	@org.jetbrains.annotations.Nullable
	private Component line;
	/** The title over his head ("Steward of Oakvale"), worked out once a second rather than every tick (B85). */
	@org.jetbrains.annotations.Nullable
	private Component title;
	@org.jetbrains.annotations.Nullable
	private BlockPos titleHall;

	public StewardWork() {
		super(ImmutableMap.of(
			MemoryModuleType.JOB_SITE, MemoryStatus.VALUE_PRESENT,
			MemoryModuleType.WALK_TARGET, MemoryStatus.REGISTERED,
			MemoryModuleType.LOOK_TARGET, MemoryStatus.REGISTERED
		), 1200);
	}

	/** Whether {@code villager} holds the City Plan for his rounds. */
	public static boolean holdsPlan(Villager villager) {
		return villager.getItemBySlot(EquipmentSlot.MAINHAND).is(ModItems.CITY_PLAN);
	}

	@Override
	protected boolean checkExtraStartConditions(ServerLevel level, Villager villager) {
		return Stewards.ENABLED && !villager.isSleeping() && Stewards.isSteward(villager) && Stewards.hallOf(level, villager).isPresent();
	}

	@Override
	protected boolean canStillUse(ServerLevel level, Villager villager, long gameTime) {
		return checkExtraStartConditions(level, villager);
	}

	@Override
	protected void start(ServerLevel level, Villager villager, long gameTime) {
		walker.reset();
		stops = null;
	}

	@Override
	protected void stop(ServerLevel level, Villager villager, long gameTime) {
		hold(villager, false);
		stops = null;
	}

	/** Whether today's round is done. */
	public static boolean roundDone(ServerLevel level, Villager villager) {
		Long day = ModAttachments.STEWARD_ROUND_DAY.get(villager);
		return day != null && day == level.getDayTime() / 24000L;
	}

	@Override
	protected void tick(ServerLevel level, Villager villager, long gameTime) {
		BlockPos hall = Stewards.hallOf(level, villager).orElse(null);
		if (hall == null) {
			return;
		}
		if (title == null || gameTime % PLAN_EVERY == 0 || !hall.equals(titleHall)) {
			title = Component.translatable("message.aliveworkplace.steward.title", VillageHalls.name(level, hall));
			titleHall = hall.immutable();
		}
		if (!roundDone(level, villager)) {
			if (stops == null) {
				long cost = StewardCost.start(); // 27.22
				try {
					stops = stops(level, villager, hall);
				} finally {
					StewardCost.stop(cost, "rounds");
				}
				stop = 0;
				wait = 0;
				travel = 0;
				walker.reset();
			}
			if (stop < stops.size()) {
				hold(villager, true);
				Stop at = stops.get(stop);
				WorkerStatus.set(villager, title, (float) stop / stops.size(), Component.translatable("message.aliveworkplace.steward.state.rounds",
					stop + 1, stops.size(), at.what()).withStyle(ChatFormatting.GRAY));
				if (!walker.walkTo(level, villager, at.pos(), STOP_REACH) && ++travel < GIVE_UP) {
					return;
				}
				villager.getLookControl().setLookAt(at.pos().getX() + 0.5, at.pos().getY() + 0.5, at.pos().getZ() + 0.5);
				if (++wait >= STOP_TICKS || travel >= GIVE_UP) {
					stop++;
					wait = 0;
					travel = 0;
					walker.reset();
				}
				return;
			}
			// back to the hall
			hold(villager, true);
			WorkerStatus.set(villager, title, 1f, Component.translatable("message.aliveworkplace.steward.state.back").withStyle(ChatFormatting.GRAY));
			if (!walker.walkTo(level, villager, hall, HALL_REACH) && ++travel < GIVE_UP) {
				return;
			}
			travel = 0;
			ModAttachments.STEWARD_ROUND_DAY.set(villager, level.getDayTime() / 24000L);
			stops = null;
			hold(villager, false);
		}
		// At the hall till evening, planning.
		if (!walker.walkTo(level, villager, hall, HALL_REACH)) {
			WorkerStatus.set(villager, title, -1f, Component.translatable("message.aliveworkplace.steward.state.back").withStyle(ChatFormatting.GRAY));
			return;
		}
		int part = (int) (gameTime % PLAN_EVERY);
		boolean first = line == null;
		if (first || part == 0 || part == SECOND_PART) {
			long cost = StewardCost.start(); // 27.22
			try {
				// B85: spread over the second (the first time, its first part straight away)
				Component next = PLANNER.plan(level, villager, hall, first ? 0 : part);
				if (next != null) {
					line = next.copy().withStyle(ChatFormatting.GRAY);
				}
			} finally {
				StewardCost.stop(cost, first || part == 0 ? "planning" : "planning (desk)");
			}
		}
		if (line == null) {
			return;
		}
		villager.getLookControl().setLookAt(hall.getX() + 0.5, hall.getY() + 0.5, hall.getZ() + 0.5);
		WorkerStatus.set(villager, title, -1f, line);
	}

	private static final double HALL_REACH = 2.5;

	/** Puts the City Plan in his hands for his rounds, or away again. */
	static void hold(Villager villager, boolean on) {
		if (on && villager.getItemBySlot(EquipmentSlot.MAINHAND).isEmpty()) {
			villager.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(ModItems.CITY_PLAN));
			villager.setDropChance(EquipmentSlot.MAINHAND, 0f);
		} else if (!on && holdsPlan(villager)) {
			villager.setItemSlot(EquipmentSlot.MAINHAND, ItemStack.EMPTY);
		}
	}

	/** Today's stops, nearest the hall first: his open sites, one spot in each zone of the plan, the storehouse; at most {@link #MAX_STOPS}. */
	public static List<Stop> stops(ServerLevel level, Villager villager, BlockPos hall) {
		List<Stop> out = new ArrayList<>();
		for (BlockPos site : OPEN_SITES.apply(level, villager)) {
			out.add(new Stop(ground(level, site), Component.translatable("message.aliveworkplace.steward.stop.site")));
		}
		if (level.getBlockEntity(hall) instanceof VillageHallBlockEntity entity) {
			for (CityPlan.Zone zone : entity.plan().zones()) {
				zone.cells().stream()
					.mapToObj(cell -> CityPlan.cellCentre(hall, cell))
					.min(Comparator.comparingDouble(p -> p.distSqr(hall)))
					.ifPresent(p -> out.add(new Stop(ground(level, p), Component.translatable("message.aliveworkplace.steward.stop.zone", zone.name()))));
			}
		}
		level.getPoiManager().findClosest(h -> h.is(ModVillagers.STOREHOUSE_POI), hall, VillageHalls.RADIUS, PoiManager.Occupancy.ANY)
			.ifPresent(p -> out.add(new Stop(p, Component.translatable("message.aliveworkplace.steward.stop.storehouse"))));
		Function<Stop, Double> far = s -> s.pos().distSqr(hall);
		out.sort(Comparator.comparing(far));
		return out.size() > MAX_STOPS ? List.copyOf(out.subList(0, MAX_STOPS)) : out;
	}

	private static BlockPos ground(ServerLevel level, BlockPos pos) {
		return level.isLoaded(pos) ? level.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, pos) : pos;
	}
}
