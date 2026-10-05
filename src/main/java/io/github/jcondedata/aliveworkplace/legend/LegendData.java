package io.github.jcondedata.aliveworkplace.legend;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.Map;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;

/**
 * What makes a villager a Legend (the {@code LEGEND} attachment): which Legend, the name they go by (empty: their own),
 * a guest or settled, their hall, the day they came, a guest's last day, how many days running each need has gone
 * unmet, the day a strike began (-1: none), the day of their last luxury, the way they came and the last day their needs
 * were checked (29.5; -1: never). Days are {@code Chronicle.day}s, but {@code since}, the world's day count when they
 * came (one less). Every field has a default, so a later field never breaks an older save.
 */
public record LegendData(ResourceLocation id, String name, boolean guest, Optional<BlockPos> hall, long since, long lastDay,
						 Map<String, Integer> unmet, long strikeSince, long lastLuxury, String way, long checked) {
	public static final Codec<LegendData> CODEC = RecordCodecBuilder.create(i -> i.group(
		ResourceLocation.CODEC.fieldOf("id").forGetter(LegendData::id),
		Codec.STRING.optionalFieldOf("name", "").forGetter(LegendData::name),
		Codec.BOOL.optionalFieldOf("guest", false).forGetter(LegendData::guest),
		BlockPos.CODEC.optionalFieldOf("hall").forGetter(LegendData::hall),
		Codec.LONG.optionalFieldOf("since", 0L).forGetter(LegendData::since),
		Codec.LONG.optionalFieldOf("last_day", -1L).forGetter(LegendData::lastDay),
		Codec.unboundedMap(Codec.STRING, Codec.INT).optionalFieldOf("unmet", Map.of()).forGetter(LegendData::unmet),
		Codec.LONG.optionalFieldOf("strike_since", -1L).forGetter(LegendData::strikeSince),
		Codec.LONG.optionalFieldOf("last_luxury", -1L).forGetter(LegendData::lastLuxury),
		Codec.STRING.optionalFieldOf("way", "").forGetter(LegendData::way),
		Codec.LONG.optionalFieldOf("checked", -1L).forGetter(LegendData::checked)
	).apply(i, LegendData::new));

	/** A Legend whose needs were never checked. */
	public LegendData(ResourceLocation id, String name, boolean guest, Optional<BlockPos> hall, long since, long lastDay,
					  Map<String, Integer> unmet, long strikeSince, long lastLuxury, String way) {
		this(id, name, guest, hall, since, lastDay, unmet, strikeSince, lastLuxury, way, -1);
	}

	/** A Legend settled in the village round {@code hall} from {@code day}. */
	public static LegendData settled(ResourceLocation id, String name, Optional<BlockPos> hall, long day, String way) {
		return new LegendData(id, name, false, hall, day, -1, Map.of(), -1, -1, way);
	}

	public boolean settled() {
		return !guest;
	}

	public boolean onStrike() {
		return strikeSince >= 0;
	}

	/** The Chronicle day they settled ({@link #since} counts the world's days from 0, the chronicle from 1). */
	public long settledDay() {
		return since + 1;
	}

	/** The same Legend after a needs check (29.5): the days each need has gone unmet, the strike, the last luxury, the day. */
	public LegendData checkedOn(long day, Map<String, Integer> unmetNow, long strike, long luxury) {
		return new LegendData(id, name, guest, hall, since, lastDay, Map.copyOf(unmetNow), strike, luxury, way, day);
	}
}
