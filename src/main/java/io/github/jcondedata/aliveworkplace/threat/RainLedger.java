package io.github.jcondedata.aliveworkplace.threat;

import io.github.jcondedata.aliveworkplace.mc.Nbt;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;

/**
 * The last day it rained, one per dimension (ROADMAP 32.17, B948): the drought's "days without rain" survive a server
 * restart. Older worlds have none saved and count from the first day this version sees, as before.
 */
public final class RainLedger extends SavedData {
	public static final String NAME = "aliveworkplace_rain";
	private static final String LAST_RAIN = "last_rain_day";
	/** No rain day known yet (an old save, a new world). */
	private static final long UNKNOWN = Long.MIN_VALUE;
	private long lastRain = UNKNOWN;

	public static RainLedger get(ServerLevel level) {
		return level.getDataStorage().computeIfAbsent(new SavedData.Factory<>(RainLedger::new, RainLedger::load, null), NAME);
	}

	/**
	 * Days since it last rained, on {@code today}; the first call (an old save) or a day later than today (the time set
	 * back) starts the count from today.
	 */
	public long dryDays(long today) {
		if (lastRain == UNKNOWN || lastRain > today) {
			rainedOn(today);
		}
		return today - lastRain;
	}

	public void rainedOn(long day) {
		if (lastRain != day) {
			lastRain = day;
			setDirty();
		}
	}

	@Override
	public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
		if (lastRain != UNKNOWN) {
			tag.putLong(LAST_RAIN, lastRain);
		}
		return tag;
	}

	public static RainLedger load(CompoundTag tag, HolderLookup.Provider registries) {
		RainLedger ledger = new RainLedger();
		if (Nbt.has(tag, LAST_RAIN, Tag.TAG_ANY_NUMERIC)) {
			ledger.lastRain = Nbt.getLong(tag, LAST_RAIN);
		}
		return ledger;
	}
}
