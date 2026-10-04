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
 * unmet, the day a strike began (-1: none), the day of their last luxury and the way they came. Every field has a
 * default, so a later field never breaks an older save.
 */
public record LegendData(ResourceLocation id, String name, boolean guest, Optional<BlockPos> hall, long since, long lastDay,
						 Map<String, Integer> unmet, long strikeSince, long lastLuxury, String way) {
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
		Codec.STRING.optionalFieldOf("way", "").forGetter(LegendData::way)
	).apply(i, LegendData::new));

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
}
