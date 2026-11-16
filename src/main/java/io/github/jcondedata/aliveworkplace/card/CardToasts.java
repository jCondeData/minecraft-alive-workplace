package io.github.jcondedata.aliveworkplace.card;

import io.github.jcondedata.aliveworkplace.AliveWorkplace;
import io.github.jcondedata.aliveworkplace.platform.Platform;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentSerialization;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;

/**
 * Copperline's toast (ROADMAP 48.3, the VillagerCard board's "Toast": "Storehouse: level 3 / Mira finished today."): a
 * short note in the screen's top-right corner with a 16-pixel icon, a title and a line, sent when a builder finishes (or
 * takes down) a build for the player. The chat line still goes too (its words and Stop links stay as they were); a client
 * without the mod gets only that.
 */
public final class CardToasts {
	/** The section icon for a finished build (CopperStyle's {@code icon/buildings}). */
	public static final String BUILT_ICON = "buildings";

	/** A toast: the icon's name (a Copperline section icon), its title and its line. */
	public record Show(String icon, Component title, Component line) implements CustomPacketPayload {
		public static final Type<Show> TYPE = new Type<>(AliveWorkplace.id("card_toast"));
		public static final StreamCodec<RegistryFriendlyByteBuf, Show> CODEC = StreamCodec.of((buf, o) -> {
			buf.writeUtf(o.icon());
			ComponentSerialization.STREAM_CODEC.encode(buf, o.title());
			ComponentSerialization.STREAM_CODEC.encode(buf, o.line());
		}, buf -> new Show(buf.readUtf(), ComponentSerialization.STREAM_CODEC.decode(buf), ComponentSerialization.STREAM_CODEC.decode(buf)));

		@Override
		public Type<? extends CustomPacketPayload> type() {
			return TYPE;
		}
	}

	public static void init() {
		Platform.get().clientbound(Show.TYPE, Show.CODEC);
	}

	/** The toast for a finished build: the build's name, and "{builder} finished it." (or "took it down."). */
	public static Show built(Component builder, Component build, boolean takenDown) {
		return new Show(BUILT_ICON, build, Component.translatable(takenDown ? "toast.aliveworkplace.taken_down" : "toast.aliveworkplace.built", builder));
	}

	/** Sends {@code toast} to {@code player} when their client has the mod; returns whether it went. */
	public static boolean send(ServerPlayer player, Show toast) {
		try {
			if (!Platform.get().canSend(player, Show.TYPE)) {
				return false;
			}
			Platform.get().send(player, toast);
			return true;
		} catch (RuntimeException e) {
			return false; // a player without a real connection (a GameTest's mock player): the chat line is enough
		}
	}

	private CardToasts() {
	}
}
