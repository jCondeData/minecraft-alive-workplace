package io.github.jcondedata.aliveworkplace.hall;

import io.github.jcondedata.aliveworkplace.guard.Guards;
import io.github.jcondedata.aliveworkplace.guard.Mercenaries;
import io.github.jcondedata.aliveworkplace.guard.VillageRaids;
import io.github.jcondedata.aliveworkplace.mixin.MobAccessor;
import io.github.jcondedata.aliveworkplace.people.Sickness;
import io.github.jcondedata.aliveworkplace.platform.Platform;
import java.util.Map;
import java.util.WeakHashMap;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.CustomData;
import org.jetbrains.annotations.Nullable;

/**
 * The Conscription edict's rules (ROADMAP 30.10), read from the {@code militia} and {@code work_stops_in_raids} effects
 * ({@link CivicEffects.Militia}, {@link CivicEffects.WorkStops}) where the villager lives.
 *
 * <p><b>The militia:</b> while the village is raided (one of our monster or bandit raids, or a vanilla pillager raid
 * there: {@link VillageRaids#raided}) every grown villager who isn't ill, a guard or a mercenary is a conscript
 * ({@link #militia}): they never panic ({@code mixin/VillagerPanicTriggerMixin}), hold a stone sword made for the raid
 * (never taken from a chest; tagged {@link #SWORD_TAG}, gone when the raid ends and again whenever the villager loads, so
 * a save mid-raid can't leave a free sword) and go for the nearest raider in range ({@code guard/MilitiaCombat}).
 * Whatever they held moves to their off hand meanwhile and comes back after.
 *
 * <p><b>The work gate:</b> {@link #workStopped} is the one check every job's work passes ({@code mixin/BrainMixin}: a
 * villager whose work is stopped leaves the WORK activity, whose behaviours stop, and the schedule can't send them back).
 * With {@code until_noon} the village's work stays stopped after the raid until noon the next day (kept on the hall as
 * {@code raidWorkUntil}, in the level's day time, so a night slept through still ends at noon); with {@code near} work
 * stops only for villagers with a raider that close. Guards and mercenaries keep their watch.
 */
public final class Conscription {
	/** The custom-data key that marks a conscript's sword. */
	public static final String SWORD_TAG = "aliveworkplace_militia";
	/** Noon: when the morning after a raid is over. */
	public static final long NOON = 6000;
	/** How long the work gate's answer is kept for a villager. */
	private static final long CHECK_TICKS = 20;

	private record Kept(boolean stopped, long until) {
	}

	private static final Map<Villager, Kept> STOPPED = new WeakHashMap<>();

	public static void init() {
		Platform.get().onEntityLoad((entity, level) -> {
			if (entity instanceof Villager villager) {
				disarm(villager);
			}
		});
	}

	/** The militia {@code villager} fights in now, or null: not raided, no {@code militia}, a child, ill, a guard or a mercenary. */
	@Nullable
	public static CivicEffects.Militia militia(Villager villager) {
		if (villager.isBaby() || !villager.isAlive() || !(villager.level() instanceof ServerLevel level) || Guards.isGuard(villager)
			|| Mercenaries.isMercenary(villager) || Sickness.isIll(villager)) {
			return null;
		}
		VillageHallBlockEntity hall = CivicEffects.hallOf(villager);
		if (hall == null) {
			return null;
		}
		CivicEffects.Militia militia = CivicEffects.of(hall).militia();
		return militia != null && VillageRaids.raided(level, hall.getBlockPos(), villager.blockPosition()) ? militia : null;
	}

	/** Whether {@code villager}'s work is stopped now by a raid (asked again every {@link #CHECK_TICKS} ticks). */
	public static boolean workStopped(Villager villager) {
		if (!Edicts.ENABLED || villager.isBaby() || !(villager.level() instanceof ServerLevel level)) {
			return false;
		}
		long now = level.getGameTime();
		Kept kept;
		synchronized (STOPPED) {
			kept = STOPPED.get(villager);
		}
		if (kept != null && now < kept.until() && now >= kept.until() - CHECK_TICKS) {
			return kept.stopped();
		}
		boolean stopped = check(level, villager);
		synchronized (STOPPED) {
			STOPPED.put(villager, new Kept(stopped, now + CHECK_TICKS));
		}
		return stopped;
	}

	private static boolean check(ServerLevel level, Villager villager) {
		if (Guards.isGuard(villager) || Mercenaries.isMercenary(villager)) {
			return false;
		}
		VillageHallBlockEntity hall = CivicEffects.hallOf(villager);
		if (hall == null) {
			return false;
		}
		CivicEffects.WorkStops rule = CivicEffects.of(hall).workStops();
		if (rule == null) {
			return false;
		}
		if (VillageRaids.raided(level, hall.getBlockPos(), villager.blockPosition())) {
			if (rule.untilNoon()) {
				hall.keepWorkStoppedUntil(nextNoon(level));
			}
			return rule.near().map(r -> raiderNear(level, villager, r)).orElse(true);
		}
		return rule.untilNoon() && stoppedAfterRaid(level, hall);
	}

	/** Whether the morning after a raid is still on in {@code hall}'s village (its {@code raidWorkUntil} not reached). */
	public static boolean stoppedAfterRaid(ServerLevel level, VillageHallBlockEntity hall) {
		long until = hall.raidWorkUntil();
		long now = level.getDayTime();
		// More than a day ahead: the clock was set back (/time set), so the raid's morning is long over.
		return until > now && until - now <= VillageNeeds.DAY;
	}

	/** The next noon after now, in the level's day time (a raid in the night: noon the next day). */
	public static long nextNoon(ServerLevel level) {
		long now = level.getDayTime();
		long time = Math.floorMod(now, VillageNeeds.DAY);
		return now - time + NOON + (time >= NOON ? VillageNeeds.DAY : 0);
	}

	/** A raider (a foe by the guards' rules) within {@code range} blocks of {@code villager}. */
	public static boolean raiderNear(ServerLevel level, Villager villager, int range) {
		double r = (double) range * range;
		return !level.getEntitiesOfClass(LivingEntity.class, villager.getBoundingBox().inflate(range),
			e -> Guards.isFoe(e) && e.distanceToSqr(villager) <= r).isEmpty();
	}

	/** The hall's round: a raid under way under {@code until_noon} keeps the village's work stopped till the next noon. */
	public static void round(ServerLevel level, BlockPos pos, VillageHallBlockEntity hall) {
		CivicEffects.WorkStops rule = CivicEffects.of(hall).workStops();
		if (rule != null && rule.untilNoon() && VillageRaids.raided(level, pos, pos)) {
			hall.keepWorkStoppedUntil(nextNoon(level));
		}
	}

	/** Whether {@code stack} is a conscript's sword (made for a raid, never a real one). */
	public static boolean isMilitiaSword(ItemStack stack) {
		return stack.is(Items.STONE_SWORD) && stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).contains(SWORD_TAG);
	}

	/** A conscript's stone sword, made for the raid. */
	public static ItemStack sword() {
		ItemStack sword = new ItemStack(Items.STONE_SWORD);
		CompoundTag tag = new CompoundTag();
		tag.putBoolean(SWORD_TAG, true);
		sword.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));
		return sword;
	}

	/**
	 * Puts a conscript's sword in {@code villager}'s hand (whatever they held goes to their empty off hand, with its drop
	 * chance); with both hands full they fight without one showing. Never drops on death.
	 */
	public static void arm(Villager villager) {
		ItemStack held = villager.getMainHandItem();
		if (isMilitiaSword(held)) {
			return;
		}
		if (!held.isEmpty()) {
			if (!villager.getOffhandItem().isEmpty()) {
				return;
			}
			float chance = ((MobAccessor) villager).aliveworkplace$dropChance(EquipmentSlot.MAINHAND);
			villager.setItemSlot(EquipmentSlot.OFFHAND, held);
			villager.setDropChance(EquipmentSlot.OFFHAND, chance);
		}
		villager.setItemSlot(EquipmentSlot.MAINHAND, sword());
		villager.setDropChance(EquipmentSlot.MAINHAND, 0f);
	}

	/** Takes a conscript's sword away and gives back what they held before; nothing for anyone else. */
	public static void disarm(Villager villager) {
		boolean armed = false;
		if (isMilitiaSword(villager.getMainHandItem())) {
			villager.setItemSlot(EquipmentSlot.MAINHAND, ItemStack.EMPTY);
			armed = true;
		}
		if (isMilitiaSword(villager.getOffhandItem())) {
			villager.setItemSlot(EquipmentSlot.OFFHAND, ItemStack.EMPTY);
		}
		if (armed && !Guards.isGuard(villager) && !villager.getOffhandItem().isEmpty()) {
			float chance = ((MobAccessor) villager).aliveworkplace$dropChance(EquipmentSlot.OFFHAND);
			villager.setItemSlot(EquipmentSlot.MAINHAND, villager.getOffhandItem());
			villager.setDropChance(EquipmentSlot.MAINHAND, chance);
			villager.setItemSlot(EquipmentSlot.OFFHAND, ItemStack.EMPTY);
			villager.setDropChance(EquipmentSlot.OFFHAND, 0.085f);
		}
	}

	/** Forget every villager's work-gate answer (tests, and when a raid or an edict changes in one). */
	public static void forget() {
		synchronized (STOPPED) {
			STOPPED.clear();
		}
	}

	private Conscription() {
	}
}
