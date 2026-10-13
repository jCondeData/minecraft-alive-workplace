package io.github.jcondedata.aliveworkplace.wonder.powers;

import com.google.gson.JsonObject;
import io.github.jcondedata.aliveworkplace.hall.VillageHalls;
import io.github.jcondedata.aliveworkplace.legend.LegendPowers;
import io.github.jcondedata.aliveworkplace.wonder.Wonders;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.npc.Villager;

/**
 * {@code mood} (35.8, generic: for server owners' own Wonders, and the Colossus's): every grown villager of the Wonder's
 * village is {@code home_amount} happier (or {@code amount} when the file gives no {@code home_amount}); with
 * {@code "scope": "realm"} every villager of the realm's other villages is {@code amount} happier too.
 * {@code people/Moods} lists it among the reasons.
 */
public record MoodPower(boolean realm, int amount, int homeAmount) implements WonderPower {
	public static final String TYPE = "mood";
	/** Most a Wonder's mood may add or take away (a mood runs 0 to 100). */
	public static final int MAX = 50;

	static MoodPower read(JsonObject json) {
		String scope = json.has("scope") ? json.get("scope").getAsString() : "village";
		if (!scope.equals("village") && !scope.equals("realm")) {
			throw new IllegalArgumentException("'scope' must be \"village\" or \"realm\", not \"" + scope + "\"");
		}
		if (!json.has("amount")) {
			throw new IllegalArgumentException("missing 'amount'");
		}
		int amount = json.get("amount").getAsInt();
		int home = json.has("home_amount") ? json.get("home_amount").getAsInt() : amount;
		if (Math.abs(amount) > MAX || Math.abs(home) > MAX) {
			throw new IllegalArgumentException("'amount' and 'home_amount' must be -" + MAX + " to " + MAX);
		}
		return new MoodPower(scope.equals("realm"), amount, home);
	}

	@Override
	public String type() {
		return TYPE;
	}

	/** How much happier a villager of the village the power reaches is: {@link #homeAmount} at home, {@link #amount} in the realm. */
	public int points(boolean home) {
		return home ? homeAmount : amount;
	}

	/** "Everyone in the village is 4 happier", "Everyone in the realm is 8 happier, 12 at home". */
	@Override
	public Component describe() {
		if (!realm) {
			return Component.translatable("wonder.aliveworkplace.power.mood", homeAmount);
		}
		return homeAmount == amount ? Component.translatable("wonder.aliveworkplace.power.mood_realm", amount)
			: Component.translatable("wonder.aliveworkplace.power.mood_realm_home", amount, homeAmount);
	}

	/**
	 * The moods {@code villager}'s village gets from finished, standing Wonders: its own ("Proud of the Test Folly") and
	 * its realm's ("The realm's Colossus"), one reason each.
	 */
	public static List<LegendPowers.MoodReason> moods(ServerLevel level, Villager villager) {
		Optional<BlockPos> hall = VillageHalls.nearest(level, villager.blockPosition());
		if (hall.isEmpty()) {
			return List.of();
		}
		List<LegendPowers.MoodReason> out = new ArrayList<>();
		for (Wonders.Granted g : Wonders.powers(level, hall.get(), TYPE)) {
			if (g.power() instanceof MoodPower mood && mood.points(g.home()) != 0) {
				out.add(new LegendPowers.MoodReason(Component.translatable(g.home() ? "mood.aliveworkplace.reason.wonder" : "mood.aliveworkplace.reason.wonder_realm",
					g.wonder().nameText()), mood.points(g.home())));
			}
		}
		return out;
	}
}
