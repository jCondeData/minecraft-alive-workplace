package io.github.jcondedata.aliveworkplace.legend;

import com.google.gson.JsonObject;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;

/**
 * {@code bank} (the Merchant Prince, 29.17): the treasury of the Legend's village earns {@code interest} percent a day
 * on what it holds ({@code io.github.jcondedata.aliveworkplace.hall.Treasury#round}) and holds {@code cap} times as
 * much.
 */
public record BankPower(int interest, int cap) implements Power {
	static BankPower read(JsonObject json) {
		int interest = json.has("interest") ? json.get("interest").getAsInt() : 2;
		int cap = json.has("cap") ? json.get("cap").getAsInt() : 2;
		if (interest < 0 || interest > 100) {
			throw new IllegalArgumentException("'interest' must be 0 to 100");
		}
		if (cap < 1) {
			throw new IllegalArgumentException("'cap' below 1");
		}
		return new BankPower(interest, cap);
	}

	@Override
	public String type() {
		return "bank";
	}

	/** "The treasury earns 2% a day on what it holds, and holds 2 times as much". */
	@Override
	public Component describe() {
		return Component.translatable("legend.aliveworkplace.power.bank", interest, cap);
	}

	/** The bank working for the village round {@code hall}: a settled Legend of that village holding the power. */
	public static Optional<BankPower> of(ServerLevel level, BlockPos hall) {
		return LegendPowers.ofVillage(level, hall, BankPower.class);
	}
}
