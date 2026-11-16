package io.github.jcondedata.aliveworkplace.hall;

import io.github.jcondedata.aliveworkplace.hall.HallSections.Section;
import io.github.jcondedata.aliveworkplace.work.ChoiceMenu;
import io.github.jcondedata.aliveworkplace.work.Words;
import java.util.ArrayList;
import java.util.List;
import java.util.function.IntFunction;
import java.util.function.IntUnaryOperator;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.component.ItemLore;

/**
 * The Village Hall's Overview in Copperline (ROADMAP 47.2, {@code docs/design/copperline/boards/Main.png}): what its four
 * stat cards, "Needs you" and "At work now" say, read from the hall's main page as the server lays it out (the buttons
 * and the menu's numbers, {@link HallSections}), and where each goes when clicked; and where they sit on the stage, at
 * GUI scale 2 and on a small screen (320x180, where "At work now" gives way). Pure, so the server's menu and the
 * client's screen read the same thing and the GameTests check what the screen draws.
 */
public final class HallBoard {
	/** The mark on a person's button: which lore line is the first of what they're doing. */
	public static final String DOING = "aliveworkplace:hall_doing";

	/** How a line reads: a glyph, a word and a color each (Copperline's statuses), or plain. */
	public enum Tone { GOOD, BAD, INFO, WARNING, PLAIN }

	/**
	 * A stat card: its id (people, mood, food, defense), the glyph by its title, the big number and the word after it,
	 * the status line under it with its tone and glyph, the main-page button it stands for (its tooltip) and the section
	 * a click opens.
	 */
	public record Card(String id, String glyph, String big, Component unit, Component line, Tone tone, String lineGlyph, int slot, Section opens) {
		public Component title() {
			return Component.translatable("screen.aliveworkplace.hall.card." + id);
		}
	}

	/** A line under "Needs you": the sentence, its tone, and what a click does (a section opened, or a button pressed). */
	public record Need(Component text, Tone tone, Section opens, int slot) {
	}

	/** A line under "At work now": the villager's button, their name and the first of what they're doing. */
	public record Worker(int slot, Component name, Component doing) {
	}

	/** The four stat cards, from the menu's numbers. */
	public static List<Card> cards(IntUnaryOperator state) {
		List<Card> out = new ArrayList<>();
		int people = state.applyAsInt(HallSections.PEOPLE_TOTAL);
		int cap = state.applyAsInt(HallSections.CAP);
		int noBed = state.applyAsInt(HallSections.NO_BED);
		int jobless = state.applyAsInt(HallSections.PEOPLE_JOBLESS);
		Component peopleLine;
		Tone peopleTone;
		String peopleGlyph;
		if (noBed > 0) {
			peopleLine = Component.translatable("screen.aliveworkplace.hall.card.no_bed", noBed);
			peopleTone = Tone.WARNING;
			peopleGlyph = "bed";
		} else if (jobless > 0) {
			peopleLine = Component.translatable("screen.aliveworkplace.hall.card.no_job", jobless);
			peopleTone = Tone.WARNING;
			peopleGlyph = "warning";
		} else {
			peopleLine = Component.translatable(people == 0 ? "screen.aliveworkplace.hall.card.nobody" : "screen.aliveworkplace.hall.card.housed");
			peopleTone = people == 0 ? Tone.PLAIN : Tone.GOOD;
			peopleGlyph = people == 0 ? "dot" : "good";
		}
		out.add(new Card("people", "person", String.valueOf(people), cap > 0 ? Component.translatable("screen.aliveworkplace.hall.card.of", cap) : Component.empty(),
			peopleLine, peopleTone, peopleGlyph, VillageHallScreen.PEOPLE, Section.PEOPLE));

		int wellbeing = state.applyAsInt(HallSections.WELLBEING);
		String mood = wellbeing >= 70 ? "happy" : wellbeing >= 40 ? "getting_by" : "unhappy";
		out.add(new Card("mood", wellbeing >= 70 ? "happy" : wellbeing >= 40 ? "content" : "unhappy", wellbeing + "%", Component.empty(),
			Component.translatable("screen.aliveworkplace.hall.card." + mood), wellbeing >= 70 ? Tone.GOOD : wellbeing >= 40 ? Tone.WARNING : Tone.BAD,
			wellbeing >= 70 ? "up" : wellbeing >= 40 ? "steady" : "down", VillageHallScreen.WELLBEING, Section.PEOPLE));

		int meals = state.applyAsInt(HallSections.MEALS);
		int kinds = state.applyAsInt(HallSections.MEAL_KINDS);
		boolean varied = kinds >= io.github.jcondedata.aliveworkplace.people.Diet.VARIED_KINDS;
		out.add(new Card("food", "bread", String.valueOf(meals), Words.counted("screen.aliveworkplace.hall.card.meals", meals),
			meals == 0 ? Component.translatable("screen.aliveworkplace.hall.card.no_food") : Words.counted("screen.aliveworkplace.hall.card.kinds", kinds, kinds),
			meals == 0 ? Tone.BAD : varied ? Tone.GOOD : Tone.WARNING, meals == 0 ? "bad" : varied ? "good" : "warning", VillageHallScreen.FOOD, Section.HOMES));

		int guards = state.applyAsInt(HallSections.GUARDS);
		out.add(new Card("defense", "shield", String.valueOf(guards), Words.counted("screen.aliveworkplace.hall.card.guards", guards),
			Component.translatable(guards == 0 ? "screen.aliveworkplace.hall.card.unguarded" : "screen.aliveworkplace.hall.card.on_watch"),
			guards == 0 ? Tone.WARNING : Tone.INFO, guards == 0 ? "warning" : "shield", VillageHallScreen.GUARDS, Section.DEFENSE));
		return out;
	}

	/**
	 * What needs the player, most pressing first, at most {@code max}: villagers without a bed or a job, no guards, no food
	 * (each opens its section), then the hall's advice (each opens the "What next?" page).
	 */
	public static List<Need> needs(IntFunction<ItemStack> icons, IntUnaryOperator state, int max) {
		List<Need> out = new ArrayList<>();
		int people = state.applyAsInt(HallSections.PEOPLE_TOTAL);
		int noBed = state.applyAsInt(HallSections.NO_BED);
		if (noBed > 0) {
			out.add(new Need(Words.counted("screen.aliveworkplace.hall.needs.beds", noBed, noBed), Tone.WARNING, Section.PEOPLE, -1));
		}
		int jobless = state.applyAsInt(HallSections.PEOPLE_JOBLESS);
		if (jobless > 0) {
			out.add(new Need(Words.counted("screen.aliveworkplace.hall.needs.jobs", jobless, jobless), Tone.WARNING, Section.PEOPLE, -1));
		}
		if (people > 0 && state.applyAsInt(HallSections.GUARDS) == 0) {
			out.add(new Need(Component.translatable("screen.aliveworkplace.hall.needs.guards"), Tone.BAD, Section.DEFENSE, -1));
		}
		if (people > 0 && state.applyAsInt(HallSections.MEALS) == 0) {
			out.add(new Need(Component.translatable("screen.aliveworkplace.hall.needs.food"), Tone.BAD, Section.HOMES, -1));
		}
		// the advice button's lines: the count, then each tip's title and how; a tip said above already (beds, jobs, guards,
		// food) isn't said twice
		java.util.Set<String> said = new java.util.HashSet<>();
		if (noBed > 0) {
			said.add("beds");
		}
		if (jobless > 0) {
			said.add("jobless");
		}
		if (people > 0 && state.applyAsInt(HallSections.GUARDS) == 0) {
			said.add("guards");
		}
		if (people > 0 && state.applyAsInt(HallSections.MEALS) == 0) {
			said.add("food");
		}
		ItemLore lore = icons.apply(VillageHallScreen.ADVICE).get(DataComponents.LORE);
		if (lore != null) {
			List<Component> lines = lore.lines();
			for (int i = 1; i < lines.size(); i += 2) {
				String tip = tipOf(lines.get(i));
				if (tip == null || !said.contains(tip)) {
					out.add(new Need(Component.literal(lines.get(i).getString()), Tone.INFO, null, VillageHallScreen.ADVICE));
				}
			}
		}
		return out.size() > max ? out.subList(0, max) : out;
	}

	/** The advice tip a line of the advice button's names ("beds" for "advice.aliveworkplace.beds.one"), or null. */
	static String tipOf(Component line) {
		if (line.getContents() instanceof net.minecraft.network.chat.contents.TranslatableContents t && t.getKey().startsWith("advice.aliveworkplace.")) {
			String rest = t.getKey().substring("advice.aliveworkplace.".length());
			int dot = rest.indexOf('.');
			return dot < 0 ? rest : rest.substring(0, dot);
		}
		for (Component sibling : line.getSiblings()) {
			String tip = tipOf(sibling);
			if (tip != null) {
				return tip;
			}
		}
		return null;
	}

	/** Who is at work now, at most {@code max}: the people on the main page with something they're doing. */
	public static List<Worker> atWork(IntFunction<ItemStack> icons, int max) {
		List<Worker> out = new ArrayList<>();
		for (int slot = VillageHallScreen.FIRST_PERSON; slot < ChoiceMenu.SIZE && out.size() < max; slot++) {
			ItemStack icon = icons.apply(slot);
			Component doing = doing(icon);
			if (doing != null && HallPeople.row(icon) != null) {
				out.add(new Worker(slot, icon.getHoverName(), doing));
			}
		}
		return out;
	}

	/** Marks a person's button: lore line {@code index} is the first of what they're doing. */
	static void markDoing(ItemStack icon, int index) {
		CompoundTag tag = icon.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag();
		tag.putInt(DOING, index);
		icon.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));
	}

	/** The first of what the person on {@code icon} is doing (null: nothing, or not a person). */
	public static Component doing(ItemStack icon) {
		CustomData data = icon.get(DataComponents.CUSTOM_DATA);
		ItemLore lore = icon.get(DataComponents.LORE);
		if (data == null || lore == null || !data.contains(DOING)) {
			return null;
		}
		int i = data.copyTag().getInt(DOING);
		return i >= 0 && i < lore.lines().size() ? lore.lines().get(i) : null;
	}

	// --- where things sit -------------------------------------------------------------------------------------------

	/** The heading's height (the village's name, its rank and today's date), on a normal and a small screen. */
	public static final int HEAD_H = 24;
	public static final int HEAD_H_SMALL = 20;
	/** A card's height. */
	public static final int CARD_H = 44;
	public static final int CARD_H_SMALL = 34;
	public static final int GAP = 4;
	/** A line's height under "Needs you" and "At work now", and their headings'. */
	public static final int LINE_H = 14;
	public static final int WORK_H = 20;
	public static final int HEADING_H = 12;

	/**
	 * The Overview's boxes on {@code stage} [x, y, w, h]: the heading, the four cards, "Needs you" and "At work now" (a
	 * zero-width box on a small screen, where "Needs you" takes the whole width), each [x, y, w, h].
	 */
	public static int[][] overview(int[] stage, boolean small) {
		int x = stage[0];
		int y = stage[1];
		int w = stage[2];
		int h = stage[3];
		int headH = small ? HEAD_H_SMALL : HEAD_H;
		int cardH = small ? CARD_H_SMALL : CARD_H;
		int[][] out = new int[7][];
		out[0] = new int[]{x, y, w, headH};
		int cw = (w - 3 * GAP) / 4;
		int cy = y + headH + GAP;
		for (int i = 0; i < 4; i++) {
			out[1 + i] = new int[]{x + i * (cw + GAP), cy, cw, cardH};
		}
		int ly = cy + cardH + GAP + 2;
		int lh = Math.max(0, y + h - ly);
		if (small) {
			out[5] = new int[]{x, ly, w, lh};
			out[6] = new int[]{x + w, ly, 0, lh};
		} else {
			int needsW = (w - 2 * GAP) * 52 / 100;
			out[5] = new int[]{x, ly, needsW, lh};
			out[6] = new int[]{x + needsW + 2 * GAP, ly, w - needsW - 2 * GAP, lh};
		}
		return out;
	}

	/** How many lines fit in a list box of height {@code h} with lines {@code lineH} tall under its heading. */
	public static int fits(int h, int lineH) {
		return Math.max(0, (h - HEADING_H) / lineH);
	}

	/** Line {@code i}'s box in list {@code box}. */
	public static int[] line(int[] box, int i, int lineH) {
		return new int[]{box[0], box[1] + HEADING_H + i * lineH, box[2], lineH - 1};
	}

	private HallBoard() {
	}
}
