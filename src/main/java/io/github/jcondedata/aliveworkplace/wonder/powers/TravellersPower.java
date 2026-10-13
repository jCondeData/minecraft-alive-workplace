package io.github.jcondedata.aliveworkplace.wonder.powers;

import com.google.gson.JsonObject;
import io.github.jcondedata.aliveworkplace.hall.VillageHalls;
import io.github.jcondedata.aliveworkplace.wonder.Wonders;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;

/**
 * {@code travellers} (35.22, the Colossus's): while the Wonder stands, travellers come to its village's inns
 * {@code factor} times as often (an inn takes that many more a morning, and has room for that many more at once:
 * {@code inn/Innkeepers.arrivalsPerMorning} and {@code maxGuests}), and each is {@code expert_factor} times as likely to
 * be an Expert ({@code Innkeepers.newcomerLevel}). Both default to 1 and run 1 to {@link #MAX}.
 */
public record TravellersPower(double factor, double expertFactor) implements WonderPower {
	public static final String TYPE = "travellers";
	/** The most either number may be. */
	public static final double MAX = 8;

	static TravellersPower read(JsonObject json) {
		double factor = number(json, "factor");
		double expert = number(json, "expert_factor");
		return new TravellersPower(factor, expert);
	}

	private static double number(JsonObject json, String name) {
		if (!json.has(name)) {
			return 1;
		}
		double n = json.get(name).getAsDouble();
		if (!(n >= 1 && n <= MAX)) {
			throw new IllegalArgumentException("'" + name + "' must be 1 to " + (int) MAX + ", not " + n);
		}
		return n;
	}

	@Override
	public String type() {
		return TYPE;
	}

	/** "Travellers come to its inns twice as often, twice as likely to be Experts". */
	@Override
	public Component describe() {
		return Component.translatable("wonder.aliveworkplace.power.travellers_times", times(factor), times(expertFactor));
	}

	/** "twice", "three times", "1.5 times". */
	static Component times(double f) {
		if (f == 2) {
			return Component.translatable("wonder.aliveworkplace.power.twice");
		}
		String n = f == Math.rint(f) ? String.valueOf((long) f) : String.valueOf(Math.round(f * 10) / 10.0);
		return Component.translatable("wonder.aliveworkplace.power.times", n);
	}

	/** The power reaching the village round {@code pos} (an inn's counter or its innkeeper), while a finished, standing Wonder grants it. */
	public static Optional<TravellersPower> at(ServerLevel level, BlockPos pos) {
		if (!Wonders.ENABLED) {
			return Optional.empty();
		}
		Optional<BlockPos> hall = VillageHalls.nearest(level, pos);
		if (hall.isEmpty()) {
			return Optional.empty();
		}
		return Wonders.power(level, hall.get(), TYPE).filter(p -> p instanceof TravellersPower).map(p -> (TravellersPower) p);
	}

	/** {@code usual} (an inn's guests or a morning's arrivals) times the power's {@code factor} round {@code pos}, rounded; {@code usual} without it. */
	public static int more(ServerLevel level, BlockPos pos, int usual) {
		return at(level, pos).map(p -> (int) Math.round(usual * p.factor())).orElse(usual);
	}

	/** The power's {@code expert_factor} round {@code pos}, or 1 without it. */
	public static double expertFactor(ServerLevel level, BlockPos pos) {
		return at(level, pos).map(TravellersPower::expertFactor).orElse(1.0);
	}
}
