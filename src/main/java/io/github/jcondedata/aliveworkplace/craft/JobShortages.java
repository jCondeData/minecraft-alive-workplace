package io.github.jcondedata.aliveworkplace.craft;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.Item;
import org.jetbrains.annotations.Nullable;

/**
 * What the village's jobs can't make for a waiting builder (ROADMAP 37.14a, which retired the Crafter). Each job that
 * crafts for builders (Carpenter, Mason, Librarian, Tinkerer, Toolsmith, Dyer, Furnicrafter) looks at a builder's
 * missing things of its own kind; when one of them could make a thing but a raw material is short, it notes the whole
 * chain's lack here ("short of 2 Oak Log to make 3 Barrel"); when one is on it, it notes that too. A crafted part that no
 * job here has looked at in a while is one nobody in the village makes. The builder's shortage line says both
 * ({@link #line}). Kept in memory only: the jobs look again every few seconds, so nothing needs saving.
 */
public final class JobShortages {
	/** How long a job's note about a thing stands without being renewed (the jobs look every 100 ticks). */
	static final long FRESH_TICKS = 600;
	/** At most this many things are named on the line. */
	static final int SHOWN = 2;

	/** A job's note about one thing: when it was made and what's short (null: a job can make it, or is making it). */
	private record Note(long at, @Nullable Component lack) {
	}

	private static final Map<UUID, Map<Item, Note>> NOTES = new HashMap<>();
	/** When each site's line was first asked for (its builder's been waiting since about then). */
	private static final Map<UUID, Long> ASKED = new HashMap<>();

	private JobShortages() {
	}

	/** A job can make {@code item} for the build {@code site}, or is on it. */
	public static void covered(UUID site, Item item, long now) {
		synchronized (NOTES) {
			NOTES.computeIfAbsent(site, k -> new HashMap<>()).put(item, new Note(now, null));
		}
	}

	/** A job could make {@code item} for {@code site} but is short of a raw material: {@code lack} says what. */
	public static void lacks(UUID site, Item item, Component lack, long now) {
		synchronized (NOTES) {
			Map<Item, Note> notes = NOTES.computeIfAbsent(site, k -> new HashMap<>());
			Note old = notes.get(item);
			if (old == null || old.lack() != null || now - old.at() > FRESH_TICKS) {
				notes.put(item, new Note(now, lack)); // a job that can make it this round wins over one short of something
			}
		}
	}

	/** Forgets everything about {@code site} (the build is over). */
	public static void forget(UUID site) {
		synchronized (NOTES) {
			NOTES.remove(site);
			ASKED.remove(site);
		}
	}

	/** What's short of {@code item} for {@code site} now, or null (made, being made, or not looked at). */
	@Nullable
	public static Component lackOf(UUID site, Item item, long now) {
		synchronized (NOTES) {
			Map<Item, Note> notes = NOTES.get(site);
			Note note = notes == null ? null : notes.get(item);
			return note == null || now - note.at() > FRESH_TICKS ? null : note.lack();
		}
	}

	/** Whether a job has looked at {@code item} for {@code site} lately (made or short). */
	public static boolean lookedAt(UUID site, Item item, long now) {
		synchronized (NOTES) {
			Map<Item, Note> notes = NOTES.get(site);
			Note note = notes == null ? null : notes.get(item);
			return note != null && now - note.at() <= FRESH_TICKS;
		}
	}

	/**
	 * The line under a waiting builder's "Short of" (37.14a), or null when there's nothing to say: first what the jobs
	 * can't make for lack of a raw material ("Can't be made yet: short of 2 Oak Log to make 3 Barrel"), else the
	 * crafted parts no job here makes ("Nobody here makes: Lantern, Chain"). Raw things (logs, stone) are left to the
	 * line above; a crafted part only once the site has waited {@link #FRESH_TICKS} for it, so the jobs have had a look.
	 */
	@Nullable
	public static Component line(ServerLevel level, UUID site, Map<Item, Integer> missing) {
		long now = level.getGameTime();
		long waitingSince;
		synchronized (NOTES) {
			waitingSince = ASKED.computeIfAbsent(site, k -> now);
		}
		List<Component> lacks = new ArrayList<>();
		List<Item> nobody = new ArrayList<>();
		List<Map.Entry<Item, Integer>> sorted = new ArrayList<>(missing.entrySet());
		sorted.sort(Map.Entry.<Item, Integer>comparingByValue().reversed());
		for (Map.Entry<Item, Integer> e : sorted) {
			Component lack = lackOf(site, e.getKey(), now);
			if (lack != null) {
				lacks.add(lack);
			} else if (!lookedAt(site, e.getKey(), now) && now - waitingSince >= FRESH_TICKS && crafted(level, e.getKey())) {
				nobody.add(e.getKey());
			}
		}
		if (!lacks.isEmpty()) {
			MutableComponent list = Component.empty();
			for (int i = 0; i < Math.min(SHOWN, lacks.size()); i++) {
				if (i > 0) {
					list.append("; ");
				}
				list.append(lacks.get(i));
			}
			return Component.translatable("message.aliveworkplace.job_chain.cant_make", list);
		}
		if (!nobody.isEmpty()) {
			MutableComponent list = Component.empty();
			for (int i = 0; i < Math.min(SHOWN, nobody.size()); i++) {
				if (i > 0) {
					list.append(", ");
				}
				list.append(nobody.get(i).getDescription());
			}
			if (nobody.size() > SHOWN) {
				list.append(Component.translatable("message.aliveworkplace.status.and_more", nobody.size() - SHOWN));
			}
			return Component.translatable("message.aliveworkplace.job_chain.nobody_makes", list);
		}
		return null;
	}

	/** Whether {@code item} is a crafted part (some crafting-table, stonecutter or furnace recipe makes it). */
	static boolean crafted(ServerLevel level, Item item) {
		return !Crafting.recipesFor(level, Crafting.Kind.CRAFTING, item).isEmpty() || !Crafting.recipesFor(level, Crafting.Kind.STONECUTTING, item).isEmpty()
			|| !Crafting.recipesFor(level, Crafting.Kind.KILN, item).isEmpty() || FurnitureRecipes.makes(level.getServer(), item);
	}

	/** "short of 2 Oak Log, 1 Stick to make 4 Barrel": a whole chain's raw materials short, each with its count. */
	public static Component lackLine(Map<Item, Integer> lack, int count, Item target) {
		MutableComponent list = Component.empty();
		boolean first = true;
		for (Map.Entry<Item, Integer> e : lack.entrySet()) {
			if (!first) {
				list.append(", ");
			}
			first = false;
			list.append(Component.translatable("message.aliveworkplace.job_chain.lack_item", e.getValue(), e.getKey().getDescription()));
		}
		return Component.translatable("message.aliveworkplace.job_chain.lacks", list, count, target.getDescription());
	}

	/** {@code lack} without anything zero or less, in order. */
	static Map<Item, Integer> positive(Map<Item, Integer> lack) {
		Map<Item, Integer> out = new LinkedHashMap<>();
		lack.forEach((item, n) -> {
			if (n > 0) {
				out.put(item, n);
			}
		});
		return out;
	}
}
