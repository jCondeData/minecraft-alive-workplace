package io.github.jcondedata.aliveworkplace.work;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.mojang.datafixers.util.Pair;
import io.github.jcondedata.aliveworkplace.build.BuilderLevels;
import io.github.jcondedata.aliveworkplace.build.SupplyContainers;
import io.github.jcondedata.aliveworkplace.people.RemedyItem;
import io.github.jcondedata.aliveworkplace.people.Sickness;
import io.github.jcondedata.aliveworkplace.registry.ModAttachments;
import io.github.jcondedata.aliveworkplace.registry.ModItems;
import io.github.jcondedata.aliveworkplace.registry.ModVillagers;
import io.github.jcondedata.aliveworkplace.threat.Disasters;
import io.github.jcondedata.aliveworkplace.threat.Plague;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.WeakHashMap;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ai.behavior.Behavior;
import net.minecraft.world.entity.ai.behavior.BehaviorControl;
import net.minecraft.world.entity.ai.behavior.EntityTracker;
import net.minecraft.world.entity.ai.behavior.SetLookAndInteract;
import net.minecraft.world.entity.ai.behavior.UpdateActivityFromSchedule;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.memory.MemoryStatus;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.jetbrains.annotations.Nullable;

/**
 * The Physician's work (ROADMAP 32.18), in their WORK activity: they go house to house to the village's ill (the
 * plague's first, then the hexed and the rest) within {@link #REACH} of their brewing stand, with a Remedy in hand when
 * the chest by the stand has one (or its makings: a glass bottle, a glistering melon slice, a honey bottle and a
 * fermented spider eye, which they brew into one). At the bedside ({@link #visit}) they've seen the patient: one with
 * the plague is sent home and passes it on only to those sharing their house; with a Remedy they cure them (any illness,
 * hexes included) and keep them well ten days. Without one they ask on the requests board. They foretell an outbreak at
 * its first case through plague.json's {@code foretold_by}. Fairy and Psychic partners shorten their rounds. Nothing at
 * all with config {@code physicians} off. Restartable at any tick: the Remedy in hand is the villager's own (saved by
 * vanilla); the patient is looked for again.
 */
public class PhysicianWork extends Behavior<Villager> {
	/** Config {@code physicians}: off, no villager can be made a Physician, and Physicians do nothing. */
	public static boolean ENABLED = true;
	/** How far from their brewing stand a Physician goes to the ill. */
	public static final int REACH = 32;
	/** How close they come to a patient. */
	static final double BEDSIDE = 2.5;
	/** Ticks between looks for the ill when there are none (times {@link Partners#factor}). */
	static final int LOOK_EVERY = 100;
	/** Experience for each patient seen. */
	public static final int XP_PER_VISIT = 2;
	private static final float SPEED = 0.6f;

	private static final class State {
		final Walker walker = new Walker(SPEED);
		@Nullable
		Villager patient;
		long nextLook;
	}

	private static final Map<Villager, State> STATES = new WeakHashMap<>();

	public PhysicianWork() {
		super(ImmutableMap.of(
			MemoryModuleType.WALK_TARGET, MemoryStatus.REGISTERED,
			MemoryModuleType.LOOK_TARGET, MemoryStatus.REGISTERED
		), 2400);
	}

	public static ImmutableList<Pair<Integer, ? extends BehaviorControl<? super Villager>>> packages(float speed) {
		return ImmutableList.<Pair<Integer, ? extends BehaviorControl<? super Villager>>>of(
			Pair.of(0, new PhysicianWork()),
			Pair.of(10, SetLookAndInteract.create(EntityType.PLAYER, 4)),
			Pair.of(99, UpdateActivityFromSchedule.create())
		);
	}

	public static boolean isPhysician(Villager villager) {
		return !villager.isBaby() && villager.getVillagerData().getProfession() == ModVillagers.PHYSICIAN;
	}

	/** A glistering melon slice: what picks the job at a brewing stand (with config {@code physicians} on). */
	public static boolean isMelon(ItemStack stack) {
		return ENABLED && Disasters.live() && stack.is(Items.GLISTERING_MELON_SLICE);
	}

	private static State state(Villager villager) {
		synchronized (STATES) {
			return STATES.computeIfAbsent(villager, v -> new State());
		}
	}

	static Optional<BlockPos> post(ServerLevel level, Villager villager) {
		return villager.getBrain().getMemory(MemoryModuleType.JOB_SITE).filter(g -> g.dimension() == level.dimension()).map(GlobalPos::pos);
	}

	/** The ill within {@link #REACH} of {@code post}, the plague's first, then the nearest. */
	public static List<Villager> patients(ServerLevel level, Villager physician, BlockPos post) {
		return level.getEntitiesOfClass(Villager.class, new net.minecraft.world.phys.AABB(post).inflate(REACH, 8, REACH),
				v -> v != physician && v.isAlive() && Sickness.isIll(v)).stream()
			.sorted(Comparator.<Villager, Boolean>comparing(v -> !Sickness.isPlagueIll(v)).thenComparingDouble(v -> v.distanceToSqr(physician)))
			.toList();
	}

	@Override
	protected boolean checkExtraStartConditions(ServerLevel level, Villager villager) {
		if (!ENABLED || !isPhysician(villager) || !Disasters.live() || villager.isSleeping()) {
			return false;
		}
		Optional<BlockPos> post = post(level, villager);
		State state = state(villager);
		if (post.isEmpty() || level.getGameTime() < state.nextLook) {
			return false;
		}
		List<Villager> ill = patients(level, villager, post.get());
		if (ill.isEmpty()) {
			state.nextLook = level.getGameTime() + LOOK_EVERY;
			WorkerStatus.set(villager, Component.translatable("entity.minecraft.villager.physician"), -1f,
				Component.translatable("message.aliveworkplace.physician.state.open").withStyle(ChatFormatting.GRAY));
			return false;
		}
		state.patient = ill.get(0);
		ready(level, villager, post.get());
		return true;
	}

	/** Takes a Remedy into hand from the chest by the stand, brewing one from its makings when there's none. */
	public static boolean ready(ServerLevel level, Villager physician, BlockPos post) {
		if (RemedyItem.isRemedy(physician.getMainHandItem())) {
			return true;
		}
		List<BlockPos> chests = SupplyContainers.find(level, post, null);
		ItemStack remedy = SupplyContainers.takeOne(level, chests, RemedyItem::isRemedy);
		if (remedy.isEmpty() && brew(level, chests)) {
			remedy = new ItemStack(ModItems.REMEDY);
		}
		if (remedy.isEmpty()) {
			return false;
		}
		physician.setItemSlot(EquipmentSlot.MAINHAND, remedy);
		return true;
	}

	/** Brews a Remedy from the makings in {@code chests}, all four or none; the honey's bottle goes back. */
	static boolean brew(ServerLevel level, List<BlockPos> chests) {
		List<net.minecraft.world.item.Item> makings = List.of(Items.GLASS_BOTTLE, Items.GLISTERING_MELON_SLICE, Items.HONEY_BOTTLE, Items.FERMENTED_SPIDER_EYE);
		for (net.minecraft.world.item.Item item : makings) {
			if (SupplyContainers.count(level, chests, item) < 1) {
				return false;
			}
		}
		for (net.minecraft.world.item.Item item : makings) {
			SupplyContainers.takeOne(level, chests, s -> s.is(item));
		}
		SupplyContainers.insert(level, chests, new ItemStack(Items.GLASS_BOTTLE));
		return true;
	}

	@Override
	protected boolean canStillUse(ServerLevel level, Villager villager, long gameTime) {
		State state = state(villager);
		return ENABLED && isPhysician(villager) && Disasters.live() && !villager.isSleeping() && state.patient != null && state.patient.isAlive()
			&& Sickness.isIll(state.patient);
	}

	@Override
	protected void tick(ServerLevel level, Villager villager, long gameTime) {
		State state = state(villager);
		Villager patient = state.patient;
		if (patient == null) {
			return;
		}
		villager.getBrain().setMemory(MemoryModuleType.LOOK_TARGET, new EntityTracker(patient, true));
		WorkerStatus.set(villager, Component.translatable("entity.minecraft.villager.physician"), -1f,
			Component.translatable("message.aliveworkplace.physician.state.visiting", patient.getDisplayName()).withStyle(ChatFormatting.YELLOW));
		if (!state.walker.walkTo(level, villager, patient.blockPosition(), BEDSIDE)) {
			return;
		}
		Component done = visit(level, villager, patient);
		WorkerStatus.set(villager, Component.translatable("entity.minecraft.villager.physician"), -1f, done);
		state.patient = null;
		state.nextLook = gameTime + (long) (20 * Partners.factor(villager));
	}

	@Override
	protected void stop(ServerLevel level, Villager villager, long gameTime) {
		State state = state(villager);
		state.patient = null;
		state.walker.reset();
		villager.getBrain().eraseMemory(MemoryModuleType.WALK_TARGET);
	}

	/**
	 * The Physician at {@code patient}'s bedside: seen (with the plague: sent home, passing it on only in their house);
	 * cured with the Remedy in hand, if any (taken or brewed from the chest by the stand), else a Remedy asked for on the
	 * requests board. Returns what they did, for their status.
	 */
	public static Component visit(ServerLevel level, Villager physician, Villager patient) {
		Optional<BlockPos> post = post(level, physician);
		if (post.isPresent()) {
			ready(level, physician, post.get());
		}
		BuilderLevels.addXp(level, physician, XP_PER_VISIT, null);
		ItemStack hand = physician.getMainHandItem();
		if (RemedyItem.isRemedy(hand)) {
			hand.shrink(1);
			physician.setItemSlot(EquipmentSlot.MAINHAND, hand.isEmpty() ? ItemStack.EMPTY : hand);
			Plague.cure(level, patient);
			ItemStack bottle = new ItemStack(Items.GLASS_BOTTLE);
			ItemStack rest = post.isPresent() ? SupplyContainers.insert(level, SupplyContainers.find(level, post.get(), null), bottle) : bottle;
			if (!rest.isEmpty()) {
				physician.spawnAtLocation(rest);
			}
			Requests.clear(physician);
			ModAttachments.VILLAGERS_CURED.set(physician, ModAttachments.VILLAGERS_CURED.getOrElse(physician, 0) + 1);
			PartnerShows.cue(physician, "cure", patient.blockPosition());
			return Component.translatable("message.aliveworkplace.physician.state.cured", patient.getDisplayName()).withStyle(ChatFormatting.GREEN);
		}
		if (Sickness.isPlagueIll(patient)) {
			ModAttachments.PLAGUE_SEEN.set(patient, true);
			Plague.sendHome(level, patient);
		}
		Requests.post(physician, new ItemStack(ModItems.REMEDY), 1, Component.translatable("request.aliveworkplace.remedy_physician"), RemedyItem::isRemedy);
		return Component.translatable("message.aliveworkplace.physician.state.sent_home", patient.getDisplayName()).withStyle(ChatFormatting.YELLOW);
	}
}
