package io.github.jcondedata.aliveworkplace.store;

import io.github.jcondedata.aliveworkplace.registry.ModVillagers;
import io.github.jcondedata.aliveworkplace.work.Requests;
import io.github.jcondedata.aliveworkplace.work.Words;
import java.util.List;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.world.entity.npc.VillagerProfession;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.component.ItemLore;
import org.jetbrains.annotations.Nullable;

/**
 * What the Storehouse board's Copperline table (ROADMAP 47.4, {@code docs/design/copperline/boards/Storehouse.png}) shows
 * of each request, carried on its button in {@link #MARK} beside the lore the board always had (the lore is unchanged, so
 * every older reader of it still works): how many are asked for, how many the store holds, how many the viewer carries,
 * which lore line says who brings it, and who asked (their group for the rail's filter, their name if they have one).
 *
 * <p>Everything here is plain logic on the tag and the lore lines, so the client draws from it and the GameTests check
 * it: the rail's filters ({@link #shown}), the Give / Stocked / Have 0 button ({@link #state}), the count's color
 * ({@link #tone}), and the two-line Needed by and Brought by cells ({@link #neededBy}, {@link #broughtBy}).
 */
public final class BoardRows {
	public static final String MARK = "aliveworkplace:store_row";

	/** Who asked, for the rail's "Needed by" filter. EVERYONE is the filter that shows them all. */
	public enum Group {
		EVERYONE("everyone"), BUILDERS("builders"), WORKERS("workers"), KITCHEN("kitchen"), PROJECTS("projects");

		public final String id;

		Group(String id) {
			this.id = id;
		}

		public Component title() {
			return Component.translatable("screen.aliveworkplace.storehouse.group." + id);
		}

		public static Group of(String id) {
			for (Group g : values()) {
				if (g.id.equals(id)) {
					return g;
				}
			}
			return WORKERS;
		}
	}

	/** A row's button: Give (the viewer carries some), Stocked (the store holds enough already), or Have 0. */
	public enum State { GIVE, STOCKED, NONE }

	/** How full the store is for a row: nothing, under a quarter, partway, or all of it (unknown: a tool, "any pickaxe"). */
	public enum Tone { EMPTY, LOW, PART, FULL, UNKNOWN }

	/** The group of a request: a block's goods are a project's, a builder's are the builders', cooks' the kitchen's. */
	public static Group group(Requests.Request request) {
		if (request.forBlock() || request.worker() == null) {
			return Group.PROJECTS;
		}
		VillagerProfession job = request.worker().getVillagerData().getProfession();
		if (job == ModVillagers.BUILDER) {
			return Group.BUILDERS;
		}
		if (job == ModVillagers.CHEF || job == ModVillagers.CAMP_COOK || job == ModVillagers.INNKEEPER || job == VillagerProfession.BUTCHER) {
			return Group.KITCHEN;
		}
		return Group.WORKERS;
	}

	/**
	 * Marks {@code icon}, a request's button: {@code need} asked for, {@code store} in the store (-1: not counted, a tool
	 * of any kind), {@code have} the viewer carries, {@code byLine} the lore line saying who brings it (-1: none),
	 * {@code away} blocks from the viewer, {@code who} the asker's name ("" without one).
	 */
	public static void tag(ItemStack icon, int need, long store, int have, int byLine, Group group, int away, String who) {
		CompoundTag row = new CompoundTag();
		row.putInt("need", need);
		row.putInt("store", (int) Math.max(-1, Math.min(Integer.MAX_VALUE, store)));
		row.putInt("have", have);
		row.putInt("by", byLine);
		row.putString("group", group.id);
		row.putInt("away", away);
		row.putString("who", who);
		CompoundTag data = icon.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag();
		data.put(MARK, row);
		icon.set(DataComponents.CUSTOM_DATA, CustomData.of(data));
	}

	/** A request's row on its button (null: not a request, as the store's info or the stock orders). */
	@Nullable
	public static CompoundTag row(ItemStack icon) {
		CustomData data = icon.get(DataComponents.CUSTOM_DATA);
		if (data == null || !data.contains(MARK)) {
			return null;
		}
		return data.copyTag().getCompound(MARK);
	}

	/** How many a click hands over: what the viewer carries, at most what's asked for. */
	public static int give(CompoundTag row) {
		return Math.max(0, Math.min(row.getInt("have"), row.getInt("need")));
	}

	public static State state(CompoundTag row) {
		if (give(row) > 0) {
			return State.GIVE;
		}
		int store = row.getInt("store");
		return store >= 0 && store >= row.getInt("need") ? State.STOCKED : State.NONE;
	}

	public static Tone tone(int store, int need) {
		if (store < 0) {
			return Tone.UNKNOWN;
		}
		if (store >= need) {
			return Tone.FULL;
		}
		if (store == 0) {
			return Tone.EMPTY;
		}
		return store * 4 < need ? Tone.LOW : Tone.PART;
	}

	/**
	 * Whether the rail's filters show {@code row}: its group (or Everyone), "Missing" (only what the store is short of)
	 * and "I carry" (only what the viewer can give).
	 */
	public static boolean shown(CompoundTag row, Group filter, boolean missingOnly, boolean carryOnly) {
		if (filter != Group.EVERYONE && Group.of(row.getString("group")) != filter) {
			return false;
		}
		if (missingOnly && tone(row.getInt("store"), row.getInt("need")) == Tone.FULL) {
			return false;
		}
		return !carryOnly || give(row) > 0;
	}

	/** What is asked for, without the count before it: "Oak Log" of "16 × Oak Log"; a named request ("A pickaxe") as it is. */
	public static Component what(Component name) {
		if (name.getContents() instanceof TranslatableContents t && t.getKey().equals("screen.aliveworkplace.storehouse.wants")
			&& t.getArgs().length == 2) {
			return asComponent(t.getArgs()[1]);
		}
		return name;
	}

	/** The asker's job (or the block's name, 35.7), from the row's first line "For the %s, %s blocks away". */
	public static Component job(List<Component> lore) {
		if (!lore.isEmpty() && lore.get(0).getContents() instanceof TranslatableContents t && t.getKey().startsWith("screen.aliveworkplace.storehouse.for")
			&& t.getArgs().length >= 1) {
			return asComponent(t.getArgs()[0]);
		}
		return Component.empty();
	}

	/** The Needed by cell: the asker's name and job, or their job and how far ("Builder", "12 blocks off"). */
	public static Component[] neededBy(CompoundTag row, List<Component> lore) {
		Component job = job(lore);
		String who = row.getString("who");
		if (!who.isEmpty()) {
			return new Component[]{Component.literal(who), job};
		}
		int away = row.getInt("away");
		return new Component[]{job, Words.counted("screen.aliveworkplace.storehouse.away", away, away)};
	}

	/** The Brought by cell: who, what they do (or the job missing), and whether nobody does (the warning). */
	public record BroughtBy(Component who, Component how, boolean nobody) {
	}

	/**
	 * The Brought by cell, read from the row's line that says who brings it (46.1's "Gathered by: the Lumberjack (Bo)",
	 * "Hire a Cleric to make it", 35.7's "Made by the Mason"): a name and the job, or the job and "gathers it" / "makes
	 * it", or "Nobody" and the job missing ("no Cleric"). A row without such a line (a tool of any kind) brings itself: "You".
	 */
	public static BroughtBy broughtBy(CompoundTag row, List<Component> lore) {
		int by = row.getInt("by");
		if (by < 0 || by >= lore.size() || !(lore.get(by).getContents() instanceof TranslatableContents t)) {
			return new BroughtBy(Component.translatable("screen.aliveworkplace.storehouse.by.you"),
				Component.translatable("screen.aliveworkplace.storehouse.by.bring"), false);
		}
		String key = t.getKey();
		Object[] args = t.getArgs();
		String base = "screen.aliveworkplace.storehouse.";
		if ((key.equals(base + "gathered_by") || key.equals(base + "made_by_worker")) && args.length >= 2) {
			return new BroughtBy(asComponent(args[1]), asComponent(args[0]), false);
		}
		if (key.equals(base + "gathered_by_unnamed") && args.length >= 1) {
			return new BroughtBy(asComponent(args[0]), Component.translatable(base + "by.gathers"), false);
		}
		if ((key.equals(base + "made_by_worker_unnamed") || key.equals(base + "made_by")) && args.length >= 1) {
			return new BroughtBy(asComponent(args[0]), Component.translatable(base + "by.makes"), false);
		}
		if (key.equals(base + "found") && args.length >= 1) {
			return new BroughtBy(Component.translatable(base + "by.you"), asComponent(args[0]), false);
		}
		Component nobody = Component.translatable(base + "by.nobody");
		if (key.equals(base + "hire_maker") && args.length >= 1) {
			return new BroughtBy(nobody, Component.translatable(base + "by.none_here", args[0]), true);
		}
		if (key.startsWith(base + "hire_gatherer.")) {
			return new BroughtBy(nobody, Component.translatable(base + "by.none_here." + key.substring((base + "hire_gatherer.").length())), true);
		}
		if (key.equals(base + "no_job")) {
			return new BroughtBy(nobody, Component.translatable(base + "by.no_job"), true);
		}
		return new BroughtBy(lore.get(by), Component.empty(), false);
	}

	/** Every line of {@code icon}'s lore (none: an empty list). */
	public static List<Component> lore(ItemStack icon) {
		ItemLore lore = icon.get(DataComponents.LORE);
		return lore == null ? List.of() : lore.lines();
	}

	private static Component asComponent(Object arg) {
		return arg instanceof Component c ? c : Component.literal(String.valueOf(arg));
	}

	private BoardRows() {
	}
}
