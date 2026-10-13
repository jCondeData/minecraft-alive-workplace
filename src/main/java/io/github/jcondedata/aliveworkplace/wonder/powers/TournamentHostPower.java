package io.github.jcondedata.aliveworkplace.wonder.powers;

import com.google.gson.JsonObject;
import io.github.jcondedata.aliveworkplace.wonder.Wonders;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;

/**
 * {@code tournament_host} (35.24, the Stadium's): the Festival Cup (M28, {@code cup/Cups}) of the villages taking part
 * is held at the nearest Stadium among them, and the Wonder serves as its village's Arena for it ({@code hall/Arenas.find}
 * gives the Stadium's field, boxes, seats and fair lane), so its village gets the Cup's fair and the crowds. It takes no
 * numbers.
 */
public record TournamentHostPower() implements WonderPower {
	public static final String TYPE = "tournament_host";

	static TournamentHostPower read(JsonObject json) {
		return new TournamentHostPower();
	}

	@Override
	public String type() {
		return TYPE;
	}

	/** The finished, standing Wonder of the village round {@code hall} (its own, not its realm's) that hosts its Festival Cup. */
	public static Optional<Wonders.Granted> at(ServerLevel level, BlockPos hall) {
		return Wonders.powers(level, hall, TYPE).stream().filter(Wonders.Granted::home).findFirst();
	}
}
