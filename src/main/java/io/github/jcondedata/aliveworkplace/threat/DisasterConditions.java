package io.github.jcondedata.aliveworkplace.threat;

import com.google.gson.JsonObject;
import io.github.jcondedata.aliveworkplace.hall.VillageHalls;
import java.util.Locale;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;

/**
 * A disaster's conditions (ROADMAP 32.15): the raider cultures' toolbox ({@link Conditions}) and the keys only
 * disasters use:
 * <ul>
 * <li>{@code weather}: {@code thunder} or {@code rain} now;</li>
 * <li>{@code dry_days}: at least this many days without rain ({@link Disasters#dryDays});</li>
 * <li>{@code wood_share}: at least this share (0 to 1) of the blocks of the village's finished builds is wood
 * ({@link Disasters#woodShare}, sampled once a day);</li>
 * <li>{@code crowded}: {@code true}: more villagers than beds;</li>
 * <li>{@code lair_standing}: a lair of this culture stands near the village.</li>
 * </ul>
 */
public record DisasterConditions(Conditions base, Optional<String> weather, int dryDays, double woodShare, boolean crowded,
	Optional<ResourceLocation> lairStanding) {
	public static final DisasterConditions ANY = new DisasterConditions(Conditions.ANY, Optional.empty(), 0, 0, false, Optional.empty());

	public static DisasterConditions read(JsonObject json, String file) {
		JsonObject rest = json.deepCopy();
		Optional<String> weather = Optional.empty();
		if (rest.has("weather")) {
			String w = rest.remove("weather").getAsString().toLowerCase(Locale.ROOT);
			if (!w.equals("thunder") && !w.equals("rain")) {
				throw new IllegalArgumentException("weather must be thunder or rain, not " + w);
			}
			weather = Optional.of(w);
		}
		int dry = rest.has("dry_days") ? rest.remove("dry_days").getAsInt() : 0;
		double wood = rest.has("wood_share") ? rest.remove("wood_share").getAsDouble() : 0;
		if (dry < 0 || wood < 0 || wood > 1) {
			throw new IllegalArgumentException("dry_days must be 0 or more and wood_share 0 to 1");
		}
		boolean crowded = rest.has("crowded") && rest.remove("crowded").getAsBoolean();
		Optional<ResourceLocation> lair = rest.has("lair_standing") ? Optional.of(ResourceLocation.parse(rest.remove("lair_standing").getAsString()))
			: Optional.empty();
		return new DisasterConditions(Conditions.read(rest, file), weather, dry, wood, crowded, lair);
	}

	/** Whether every condition holds for the village round {@code hall} of {@code census}. The cheap checks come first. */
	public boolean test(ServerLevel level, BlockPos hall, VillageHalls.Census census) {
		if (weather.isPresent() && !(weather.get().equals("thunder") ? Disasters.thundering(level) : Disasters.raining(level))) {
			return false;
		}
		if (dryDays > 0 && Disasters.dryDays(level) < dryDays) {
			return false;
		}
		if (crowded && census.villagers() <= census.beds()) {
			return false;
		}
		if (lairStanding.isPresent() && Lairs.of(level, hall, lairStanding.get()).isEmpty()) {
			return false;
		}
		if (!base.test(level, hall, census::villagers)) {
			return false;
		}
		return woodShare <= 0 || Disasters.woodShare(level, hall) >= woodShare;
	}
}
