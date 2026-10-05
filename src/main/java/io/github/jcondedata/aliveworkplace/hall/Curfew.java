package io.github.jcondedata.aliveworkplace.hall;

import io.github.jcondedata.aliveworkplace.guard.Guards;
import io.github.jcondedata.aliveworkplace.guard.Mercenaries;
import io.github.jcondedata.aliveworkplace.mc.Chat;
import io.github.jcondedata.aliveworkplace.platform.Platform;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.npc.VillagerProfession;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.schedule.Activity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BedBlock;

/**
 * The Curfew edict's rules (ROADMAP 30.9), read from the {@code curfew} effect ({@link CivicEffects.CurfewRules}) where
 * the villager lives. With {@code stay_in}, from dusk ({@link #DUSK}) to dawn every grown villager but the guards and the
 * mercenaries rests (the brain's schedule is overruled: {@code mixin/BrainMixin}), and nobody trades with players
 * ({@code mixin/VillagerMixin}); festivals end at dusk without fireworks ({@link Festivals}), market traders leave at dusk
 * ({@link MarketDays}), and netherworkers and explorers don't set out after midday ({@link #MIDDAY}). With
 * {@code safe_nights}, the village's safety counts as full at night ({@link VillageNeeds}) and a monster can't hurt a
 * villager asleep in their bed. Raids and bandit camps take {@code raids} from {@code guard/VillageRaids} and
 * {@code guard/BanditCamps}.
 */
public final class Curfew {
	/** Dusk: when the curfew begins (and festivals end under it). It lasts to dawn, the end of the day. */
	public static final long DUSK = 12000;
	/** After midday nobody sets out on a trip that would keep them out past dusk. */
	public static final long MIDDAY = 6000;

	public static void init() {
		Platform.get().allowDamage((entity, source, amount) -> !(entity instanceof Villager villager) || !(source.getEntity() instanceof Enemy)
			|| !asleepInBed(villager) || !isNight(villager.level()) || !CivicEffects.of(villager).safeNights());
	}

	static long timeOfDay(Level level) {
		return level.getDayTime() % VillageNeeds.DAY;
	}

	/** From dusk to dawn. */
	public static boolean isNight(Level level) {
		return timeOfDay(level) >= DUSK;
	}

	/** Asleep in a bed (villagers only sleep in their own). */
	public static boolean asleepInBed(Villager villager) {
		return villager.isSleeping() && villager.getSleepingPos().map(p -> villager.level().getBlockState(p).getBlock() instanceof BedBlock).orElse(false);
	}

	/** Whether {@code villager} must be in bed now: Curfew's {@code stay_in} at night, grown, not a guard or a mercenary. */
	public static boolean keepsIn(Villager villager) {
		return !villager.isBaby() && isNight(villager.level()) && !Guards.isGuard(villager) && !Mercenaries.isMercenary(villager)
			&& CivicEffects.of(villager).stayIn();
	}

	/** Overrules the schedule of a villager kept in: rest, whatever the hour says (the brain's schedule check). */
	public static boolean rest(Villager villager) {
		if (!keepsIn(villager)) {
			return false;
		}
		if (!villager.getBrain().isActive(Activity.REST)) {
			villager.getBrain().setActiveActivityIfPossible(Activity.REST);
		}
		return true;
	}

	/**
	 * A player asks {@code villager} to trade at night under Curfew: "Curfew: come back in the morning." and a shake of
	 * the head; true if refused. Only where vanilla would trade (a grown villager with a job, awake, not trading).
	 */
	public static boolean refuseTrade(Villager villager, Player player) {
		if (!(villager.level() instanceof ServerLevel level) || villager.isBaby() || villager.isSleeping() || villager.isTrading()
			|| villager.getVillagerData().getProfession() == VillagerProfession.NONE || !isNight(level) || !CivicEffects.of(villager).stayIn()) {
			return false;
		}
		villager.setUnhappyCounter(40);
		level.playSound(null, villager.blockPosition(), SoundEvents.VILLAGER_NO, SoundSource.NEUTRAL, 1f, 1f);
		Chat.actionBar(player, Component.translatable("message.aliveworkplace.curfew.no_trade").withStyle(ChatFormatting.GOLD));
		return true;
	}

	/** Whether {@code villager} may not set out on a trip now (Curfew's {@code stay_in}, after midday). */
	public static boolean noTrips(Villager villager) {
		return timeOfDay(villager.level()) >= MIDDAY && CivicEffects.of(villager).stayIn();
	}

	/** Whether the village round {@code hall} counts as safe now: a night under {@code safe_nights}. */
	public static boolean safeNow(ServerLevel level, BlockPos hall) {
		return isNight(level) && CivicEffects.of(level, hall).safeNights();
	}

	/** When the festival of {@code entity}'s village ends: at dusk under {@code stay_in}, else {@link Festivals#END}. */
	public static long festivalEnd(VillageHallBlockEntity entity) {
		return CivicEffects.of(entity).stayIn() ? DUSK : Festivals.END;
	}

	/** Whether the festival of {@code entity}'s village has fireworks (none under {@code stay_in}). */
	public static boolean fireworks(VillageHallBlockEntity entity) {
		return !CivicEffects.of(entity).stayIn();
	}

	/** How long market traders arriving now stay in the village round {@code hall}: {@code usual}, or until dusk. */
	public static int marketStay(ServerLevel level, BlockPos hall, int usual) {
		if (!CivicEffects.of(level, hall).stayIn()) {
			return usual;
		}
		return (int) Math.max(20, Math.min(usual, DUSK - timeOfDay(level)));
	}

	private Curfew() {
	}
}
