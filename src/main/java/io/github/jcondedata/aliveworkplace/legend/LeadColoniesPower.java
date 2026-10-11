package io.github.jcondedata.aliveworkplace.legend;

import com.google.gson.JsonObject;
import io.github.jcondedata.aliveworkplace.hall.VillageRanks;
import java.util.Locale;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;

/**
 * {@code lead_colonies} (the Founder, ROADMAP 33.23): the village he lives in may found colonies from {@code rank}
 * ({@code colony.Colonies#rankNeeded}), sends {@code settlers} settlers instead of two ({@code colony.Settlers#volunteers}),
 * waits {@code cooldown} percent as long between colonies ({@code colony.Colonies#cooldownDays}), and its colonies are
 * founded with his blessing: both chronicles say so and his statue goes up beside the colony's camp
 * ({@code colony.ColonyArrival#found}).
 */
public record LeadColoniesPower(VillageRanks.Rank rank, int settlers, int cooldown) implements Power {
	static LeadColoniesPower read(JsonObject json) {
		String name = json.has("rank") ? json.get("rank").getAsString() : "town";
		VillageRanks.Rank rank;
		try {
			rank = VillageRanks.Rank.valueOf(name.toUpperCase(Locale.ROOT));
		} catch (IllegalArgumentException e) {
			throw new IllegalArgumentException("unknown rank '" + name + "'");
		}
		int settlers = json.has("settlers") ? json.get("settlers").getAsInt() : 4;
		int cooldown = json.has("cooldown") ? json.get("cooldown").getAsInt() : 50;
		if (settlers < 2) {
			throw new IllegalArgumentException("'settlers' below 2");
		}
		if (cooldown < 0 || cooldown > 100) {
			throw new IllegalArgumentException("'cooldown' outside 0 to 100");
		}
		return new LeadColoniesPower(rank, settlers, cooldown);
	}

	@Override
	public String type() {
		return "lead_colonies";
	}

	/** "Colonies from Town rank, 4 settlers each, half the wait between them, with his blessing". */
	@Override
	public Component describe() {
		return Component.translatable("legend.aliveworkplace.power.lead_colonies", rank.title(), settlers, cooldown);
	}

	/** The power working for the village round {@code hall} (its Founder settled there), if any. */
	public static Optional<LeadColoniesPower> of(ServerLevel level, BlockPos hall) {
		return LegendPowers.ofVillage(level, hall, LeadColoniesPower.class);
	}
}
