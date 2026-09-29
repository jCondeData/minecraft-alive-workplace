package io.github.jcondedata.aliveworkplace.people;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.world.entity.npc.Villager;

/**
 * Names: a villager who lives in a village with a Village Hall gets a first name (from
 * {@code villager_name.aliveworkplace.N} in the language file), shown when you look at them, in the hall's list and on
 * their grave. Names already given (a Name Tag, a traveller's) are kept. {@code villagerNames} in the config turns it
 * off.
 */
public final class Names {
	/** Whether villagers in a village with a hall get names. */
	public static boolean ENABLED = true;
	/** How many names there are in the language file. */
	public static final int COUNT = 80;

	/** Names the villagers in {@code village} who have none, each a name nobody else there has (while there are names left). */
	public static void nameEveryone(List<Villager> village) {
		if (!ENABLED) {
			return;
		}
		Set<Integer> taken = new HashSet<>();
		for (Villager v : village) {
			int index = index(v.getCustomName());
			if (index >= 0) {
				taken.add(index);
			}
		}
		for (Villager v : village) {
			if (v.hasCustomName()) {
				continue;
			}
			int start = Math.floorMod(v.getUUID().hashCode(), COUNT);
			int pick = start;
			for (int i = 0; i < COUNT; i++) {
				int candidate = (start + i) % COUNT;
				if (!taken.contains(candidate)) {
					pick = candidate;
					break;
				}
			}
			taken.add(pick);
			v.setCustomName(name(pick));
		}
	}

	public static Component name(int index) {
		return Component.translatable("villager_name.aliveworkplace." + index);
	}

	/** Which of our names {@code name} is, or -1 for any other name (or none). */
	static int index(Component name) {
		if (name != null && name.getContents() instanceof TranslatableContents t && t.getKey().startsWith("villager_name.aliveworkplace.")) {
			try {
				return Integer.parseInt(t.getKey().substring("villager_name.aliveworkplace.".length()));
			} catch (NumberFormatException e) {
				return -1;
			}
		}
		return -1;
	}

	private Names() {
	}
}
