package io.github.jcondedata.aliveworkplace.hall;

import io.github.jcondedata.aliveworkplace.work.Money;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;

/**
 * The village's treasury: every morning a village with a Village Hall puts by its takings — a fifth of an emerald a
 * worker (the config's {@code treasuryPerWorker}, in hundredths), half as much in a village that's badly kept and half
 * again in one that's well kept, a quarter more a rank — and whoever opens the hall collects them (in CobbleDollars
 * when the pack has them). It keeps up to a stack of emeralds a rank; days the hall wasn't loaded count up to three.
 * A Merchant Prince's bank (29.17) adds interest each day on what it holds and lets it hold more ({@link #cap(VillageHallBlockEntity)}).
 */
public final class Treasury {
	public static boolean ENABLED = true;
	/** Hundredths of an emerald a worker brings in a day. */
	public static int CENTS_PER_WORKER = 20;
	/** Days missed (the hall's chunk not loaded) that still count. */
	static final int MAX_DAYS = 3;

	/** Most emeralds the treasury holds at {@code rank}. */
	public static int cap(VillageRanks.Rank rank) {
		return 64 * (1 + rank.ordinal());
	}

	/**
	 * Most emeralds the treasury of {@code entity} holds: {@link #cap(VillageRanks.Rank)}, times a Merchant Prince's
	 * bank's (29.17).
	 */
	public static int cap(VillageHallBlockEntity entity) {
		int base = cap(entity.rank());
		if (entity.getLevel() instanceof ServerLevel level) {
			return base * io.github.jcondedata.aliveworkplace.legend.BankPower.of(level, entity.getBlockPos()).map(b -> b.cap()).orElse(1);
		}
		return base;
	}

	/** A day's interest on {@code cents} at {@code percent}, in hundredths of an emerald (rounded down). */
	public static long interest(long cents, int percent) {
		return Math.max(0, cents) * percent / 100;
	}

	/** A day's takings, in hundredths of an emerald. */
	public static int takings(int workers, float wellbeing, VillageRanks.Rank rank) {
		double w = Math.max(0, Math.min(1, wellbeing));
		return (int) Math.round(workers * CENTS_PER_WORKER * (0.5 + w) * (1 + 0.25 * rank.ordinal()));
	}

	/** The hall's round: a new day's takings go in (once a day). */
	public static void round(ServerLevel level, BlockPos hall, VillageHallBlockEntity entity, int workers) {
		long day = Chronicle.day(level);
		long last = entity.lastTaxDay();
		if (last >= day) {
			return;
		}
		entity.setLastTaxDay(day);
		if (last < 0) {
			return; // a new hall: its first takings come tomorrow
		}
		VillageRanks.Rank rank = entity.rank();
		float wellbeing = entity.needs() == null ? 0.5f : entity.needs().wellbeing();
		long days = Math.min(MAX_DAYS, day - last);
		long add = (long) takings(workers, wellbeing, rank) * days;
		int before = entity.treasury();
		long cap = cap(entity) * 100L;
		long held = before;
		int percent = io.github.jcondedata.aliveworkplace.legend.BankPower.of(level, hall).map(b -> b.interest()).orElse(0);
		for (long d = 0; d < days && percent > 0; d++) { // a bank's interest on what it held each day, then the takings
			held = Math.min(cap, held + interest(held, percent));
		}
		entity.setTreasury((int) Math.max(before, Math.min(cap, held + add)));
		entity.addTreasuryTotal(entity.treasury() - before);
	}

	/** Whole emeralds waiting at the hall at {@code hall}. */
	public static int emeralds(ServerLevel level, BlockPos hall) {
		return level.getBlockEntity(hall) instanceof VillageHallBlockEntity entity ? entity.treasury() / 100 : 0;
	}

	/** {@code player} takes the treasury's whole emeralds (or their worth in CobbleDollars); what to tell them. */
	public static Component collect(ServerLevel level, BlockPos hall, ServerPlayer player) {
		if (!(level.getBlockEntity(hall) instanceof VillageHallBlockEntity entity)) {
			return Component.empty();
		}
		int emeralds = entity.treasury() / 100;
		if (emeralds <= 0) {
			return Component.translatable("message.aliveworkplace.treasury.empty").withStyle(ChatFormatting.GRAY);
		}
		entity.setTreasury(entity.treasury() - emeralds * 100);
		Money.pay(player, (long) emeralds * Money.DOLLARS_PER_EMERALD, emeralds);
		level.playSound(null, player.blockPosition(), SoundEvents.EXPERIENCE_ORB_PICKUP, SoundSource.PLAYERS, 0.8f, 1.2f);
		return Component.translatable("message.aliveworkplace.treasury.collected",
			Money.describe((long) emeralds * Money.DOLLARS_PER_EMERALD, emeralds), VillageHalls.name(level, hall)).withStyle(ChatFormatting.GREEN);
	}

	private Treasury() {
	}
}
