package io.github.jcondedata.aliveworkplace.people;

import io.github.jcondedata.aliveworkplace.guard.Mercenaries;
import io.github.jcondedata.aliveworkplace.inn.Innkeepers;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.minecraft.world.entity.npc.Villager;

/**
 * Households (ROADMAP 34.2): one grown villager on their own, or a married couple ({@link Couples}) when both are in the
 * village. Worked out from the villagers each time, never saved. Mercenaries and inn guests aren't anyone's household:
 * they don't live here.
 */
public final class Households {
	/** A household: one or two grown villagers, the one with the lower UUID first (the {@link #lead}). */
	public record Household(List<Villager> members) {
		/** The member whose saved progress speaks for the household. */
		public Villager lead() {
			return members.get(0);
		}
	}

	/** Whether {@code villager} lives in the village as a grown-up (not a child, a mercenary or a passing guest). */
	public static boolean grownResident(Villager villager) {
		return villager.isAlive() && !villager.isBaby() && !Mercenaries.isMercenary(villager) && !Innkeepers.isTraveller(villager);
	}

	/** The households among {@code villagers} (children and non-residents left out), in a fixed order (by the lead's UUID). */
	public static List<Household> of(List<Villager> villagers) {
		Map<UUID, Villager> byId = new HashMap<>();
		for (Villager v : villagers) {
			if (grownResident(v)) {
				byId.put(v.getUUID(), v);
			}
		}
		List<Household> out = new ArrayList<>();
		Set<UUID> placed = new HashSet<>();
		List<Villager> sorted = new ArrayList<>(byId.values());
		sorted.sort(Comparator.comparing(Villager::getUUID));
		for (Villager v : sorted) {
			if (!placed.add(v.getUUID())) {
				continue;
			}
			Couples.Partner partner = Couples.partner(v);
			Villager other = partner != null && partner.married() ? byId.get(partner.id()) : null;
			if (other != null && placed.add(other.getUUID())) {
				out.add(new Household(List.of(v, other))); // v's UUID is the lower: sorted, and other wasn't placed yet
			} else {
				out.add(new Household(List.of(v)));
			}
		}
		return out;
	}

	private Households() {
	}
}
