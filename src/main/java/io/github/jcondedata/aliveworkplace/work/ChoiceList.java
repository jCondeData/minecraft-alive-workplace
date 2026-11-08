package io.github.jcondedata.aliveworkplace.work;

import io.github.jcondedata.aliveworkplace.mc.Nbt;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentSerialization;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import org.jetbrains.annotations.Nullable;

/**
 * What a {@link ChoiceMenu}'s buttons are on the list screen (ROADMAP 42.1, {@link ChoiceMenu#LIST}): a small convention
 * every menu that moves to the list uses, so the client draws any of them without knowing whose menu it is.
 *
 * <p>Each button stays an icon in its chest slot, with its name and its tooltip (lore) as before; clicks stay the chest
 * clicks the server's menu takes. On top of that an icon may carry a mark (custom data {@link #KEY}) saying what it is:
 * <ul>
 *   <li>{@link Kind#ROW}: a row of the list: its name (the icon's), a second line, a price and a button label (each a
 *   text, so they keep their colors and are translated on the player's side), grayed out ({@code off}: it can't be
 *   picked now) or chosen ({@code on}: waiting for the second click, drawn gold).</li>
 *   <li>{@link Kind#TAB}: a tab over the list (the Tutor's party), {@code on} for the open one.</li>
 *   <li>{@link Kind#PAGE}: a page arrow in the footer ({@code on}: the next page; off: the previous).</li>
 *   <li>{@link Kind#INFO}: the menu's own line: the money at the top right, the footer hint, the column headings. Its
 *   tooltip (lore) shows when the money line is pointed at.</li>
 * </ul>
 * A shown button without a mark is a plain row: its name, its first lore line as the second line, no price, the whole
 * row a button. So a menu moved to the list screen works before it marks anything, and marks only add the columns.
 */
public final class ChoiceList {
	/** The custom data key of an icon's mark. */
	public static final String KEY = "aliveworkplace_list";

	public enum Kind {
		ROW, TAB, PAGE, INFO
	}

	/** One button's mark, as the client reads it. Texts are null where the menu gave none. */
	public record Mark(Kind kind, @Nullable Component second, @Nullable Component price, @Nullable Component button, boolean off, boolean on) {
	}

	/** The menu's own line (from its {@link Kind#INFO} icon). */
	public record Info(@Nullable Component money, @Nullable Component hint, List<Component> columns) {
	}

	/**
	 * Marks {@code icon} as a list row: {@code second} beside or under its name, {@code price} in the price column,
	 * {@code button} on the button at the right (null: no button, the row itself is clicked), {@code off} gray,
	 * {@code on} chosen. Returns the icon.
	 */
	public static ItemStack row(ItemStack icon, @Nullable Component second, @Nullable Component price, @Nullable Component button,
		boolean off, boolean on) {
		CompoundTag mark = new CompoundTag();
		mark.putString("kind", Kind.ROW.name());
		put(mark, "second", second);
		put(mark, "price", price);
		put(mark, "button", button);
		mark.putBoolean("off", off);
		mark.putBoolean("on", on);
		return mark(icon, mark);
	}

	/** Marks {@code icon} as a tab over the list; {@code on} for the open one. */
	public static ItemStack tab(ItemStack icon, boolean on) {
		CompoundTag mark = new CompoundTag();
		mark.putString("kind", Kind.TAB.name());
		mark.putBoolean("on", on);
		return mark(icon, mark);
	}

	/** Marks {@code icon} as the footer's page arrow: the next page when {@code next}, else the previous. */
	public static ItemStack page(ItemStack icon, boolean next) {
		CompoundTag mark = new CompoundTag();
		mark.putString("kind", Kind.PAGE.name());
		mark.putBoolean("on", next);
		return mark(icon, mark);
	}

	/** Marks {@code icon} as the menu's own line: the money at the top right, the footer hint and the column headings. */
	public static ItemStack info(ItemStack icon, @Nullable Component money, @Nullable Component hint, List<Component> columns) {
		CompoundTag mark = new CompoundTag();
		mark.putString("kind", Kind.INFO.name());
		put(mark, "money", money);
		put(mark, "hint", hint);
		ListTag headings = new ListTag();
		for (Component column : columns) {
			CompoundTag heading = new CompoundTag(); // wrapped: a list holds one kind of tag, a text may be a string or a compound
			put(heading, "text", column);
			headings.add(heading);
		}
		mark.put("columns", headings);
		return mark(icon, mark);
	}

	/** {@code icon}'s mark, or null when it has none. */
	@Nullable
	public static Mark mark(ItemStack icon) {
		CompoundTag mark = tag(icon);
		if (mark == null) {
			return null;
		}
		Kind kind;
		try {
			kind = Kind.valueOf(Nbt.getString(mark, "kind"));
		} catch (IllegalArgumentException e) {
			return null;
		}
		return new Mark(kind, get(mark, "second"), get(mark, "price"), get(mark, "button"), Nbt.getBoolean(mark, "off"), Nbt.getBoolean(mark, "on"));
	}

	/** The menu's own line from its {@link Kind#INFO} icon, or null for any other icon. */
	@Nullable
	public static Info info(ItemStack icon) {
		CompoundTag mark = tag(icon);
		if (mark == null || !Kind.INFO.name().equals(Nbt.getString(mark, "kind"))) {
			return null;
		}
		List<Component> columns = new ArrayList<>();
		ListTag headings = Nbt.getList(mark, "columns", Tag.TAG_COMPOUND);
		for (int i = 0; i < headings.size(); i++) {
			Component c = get(Nbt.compoundAt(headings, i), "text");
			columns.add(c == null ? Component.empty() : c);
		}
		return new Info(get(mark, "money"), get(mark, "hint"), columns);
	}

	@Nullable
	private static CompoundTag tag(ItemStack icon) {
		CustomData data = icon.get(DataComponents.CUSTOM_DATA);
		if (data == null) {
			return null;
		}
		CompoundTag all = data.copyTag();
		return Nbt.has(all, KEY, Tag.TAG_COMPOUND) ? Nbt.getCompound(all, KEY) : null;
	}

	private static ItemStack mark(ItemStack icon, CompoundTag mark) {
		CompoundTag all = icon.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag();
		all.put(KEY, mark);
		icon.set(DataComponents.CUSTOM_DATA, CustomData.of(all));
		return icon;
	}

	private static void put(CompoundTag mark, String key, @Nullable Component text) {
		Tag tag = text == null ? null : encode(text);
		if (tag != null) {
			mark.put(key, tag);
		}
	}

	@Nullable
	private static Component get(CompoundTag mark, String key) {
		return mark.contains(key) ? decode(mark.get(key)) : null;
	}

	@Nullable
	private static Tag encode(Component text) {
		return ComponentSerialization.CODEC.encodeStart(NbtOps.INSTANCE, text).result().orElse(null);
	}

	@Nullable
	private static Component decode(@Nullable Tag tag) {
		return tag == null ? null : ComponentSerialization.CODEC.parse(NbtOps.INSTANCE, tag).result().orElse(null);
	}

	private ChoiceList() {
	}
}
