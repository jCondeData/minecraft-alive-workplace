package io.github.jcondedata.aliveworkplace.card;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentSerialization;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.world.item.ItemStack;

/**
 * A small card in the Village Hall's look (ROADMAP 43.3; 42.5's job-status cards reuse it), worked out on the server and
 * sent with its {@link StatusCardMenu}: the villager's name in gold with a tag on the right (their job and level), a
 * grey-blue line under it, a gold heading over a few labeled lines (an icon, a label, a value in its color, and a bar
 * when the line has one), a line for when there are none, a row of buttons and a footer hint. A button with a reason is
 * grayed out and the reason shows under the row; the first usable one is gold.
 */
public record StatusCard(int entityId, Component title, Component tag, Component subtitle, Component heading, List<Line> lines,
						 Component empty, List<Button> buttons, Component hint) {
	/** Colors for a line's value and bar (the Hall's gold and white, vanilla's green, red, orange and gray). */
	public static final int GOLD = 0xFFFFD66B;
	public static final int WHITE = 0xFFFFFFFF;
	public static final int GREEN = 0xFF55FF55;
	public static final int RED = 0xFFFF5555;
	public static final int ORANGE = 0xFFFFAA00;
	public static final int GRAY = 0xFFAAAAAA;
	/** A line without a bar. */
	public static final float NO_BAR = -1f;

	/** One line: its icon (empty: none), label, value in {@code color}, and a bar {@code bar} full (0 to 1; {@link #NO_BAR}: none). */
	public record Line(ItemStack icon, Component label, Component value, int color, float bar) {
		public boolean hasBar() {
			return bar >= 0;
		}
	}

	/** A button: the id its click sends ({@link StatusCardMenu#CLOSE} closes), its label, and why it's grayed out (empty: it isn't). */
	public record Button(int id, Component label, Component refusal) {
		public boolean usable() {
			return refusal.getString().isEmpty();
		}

		public static Button of(int id, Component label, @org.jetbrains.annotations.Nullable Component refusal) {
			return new Button(id, label, refusal == null ? Component.empty() : refusal);
		}
	}

	/** The button with {@code id}, or null. */
	@org.jetbrains.annotations.Nullable
	public Button button(int id) {
		return buttons.stream().filter(b -> b.id() == id).findFirst().orElse(null);
	}

	public static final StreamCodec<RegistryFriendlyByteBuf, StatusCard> STREAM_CODEC = StreamCodec.of((buf, card) -> {
		buf.writeVarInt(card.entityId());
		for (Component c : List.of(card.title(), card.tag(), card.subtitle(), card.heading())) {
			ComponentSerialization.STREAM_CODEC.encode(buf, c);
		}
		buf.writeVarInt(card.lines().size());
		for (Line line : card.lines()) {
			ItemStack.OPTIONAL_STREAM_CODEC.encode(buf, line.icon());
			ComponentSerialization.STREAM_CODEC.encode(buf, line.label());
			ComponentSerialization.STREAM_CODEC.encode(buf, line.value());
			buf.writeInt(line.color());
			buf.writeFloat(line.bar());
		}
		ComponentSerialization.STREAM_CODEC.encode(buf, card.empty());
		buf.writeVarInt(card.buttons().size());
		for (Button button : card.buttons()) {
			buf.writeVarInt(button.id());
			ComponentSerialization.STREAM_CODEC.encode(buf, button.label());
			ComponentSerialization.STREAM_CODEC.encode(buf, button.refusal());
		}
		ComponentSerialization.STREAM_CODEC.encode(buf, card.hint());
	}, buf -> {
		int id = buf.readVarInt();
		Component title = ComponentSerialization.STREAM_CODEC.decode(buf);
		Component tag = ComponentSerialization.STREAM_CODEC.decode(buf);
		Component subtitle = ComponentSerialization.STREAM_CODEC.decode(buf);
		Component heading = ComponentSerialization.STREAM_CODEC.decode(buf);
		int n = buf.readVarInt();
		List<Line> lines = new ArrayList<>();
		for (int i = 0; i < n; i++) {
			ItemStack icon = ItemStack.OPTIONAL_STREAM_CODEC.decode(buf);
			Component label = ComponentSerialization.STREAM_CODEC.decode(buf);
			Component value = ComponentSerialization.STREAM_CODEC.decode(buf);
			int color = buf.readInt();
			lines.add(new Line(icon, label, value, color, buf.readFloat()));
		}
		Component empty = ComponentSerialization.STREAM_CODEC.decode(buf);
		int m = buf.readVarInt();
		List<Button> buttons = new ArrayList<>();
		for (int i = 0; i < m; i++) {
			int bid = buf.readVarInt();
			Component label = ComponentSerialization.STREAM_CODEC.decode(buf);
			buttons.add(new Button(bid, label, ComponentSerialization.STREAM_CODEC.decode(buf)));
		}
		Component hint = ComponentSerialization.STREAM_CODEC.decode(buf);
		return new StatusCard(id, title, tag, subtitle, heading, List.copyOf(lines), empty, List.copyOf(buttons), hint);
	});
}
