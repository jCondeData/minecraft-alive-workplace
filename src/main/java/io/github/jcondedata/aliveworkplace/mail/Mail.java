package io.github.jcondedata.aliveworkplace.mail;

import com.mojang.authlib.GameProfile;
import io.github.jcondedata.aliveworkplace.AliveWorkplace;
import java.util.List;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.ChatFormatting;
import net.minecraft.core.GlobalPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.item.ItemStack;

/** Posting parcels from a mailbox, and the dawn delivery of long-distance mail. */
public final class Mail {
	/** Longest letter that can be written on the mailbox screen. */
	public static final int MAX_MESSAGE = 256;

	/** C2S: send what's in the outgoing row of the open mailbox to this player, with a letter if {@code message} isn't blank. */
	public record Send(String to, String message) implements CustomPacketPayload {
		public static final Type<Send> TYPE = new Type<>(AliveWorkplace.id("mail_send"));
		public static final StreamCodec<RegistryFriendlyByteBuf, Send> CODEC = StreamCodec.composite(
			ByteBufCodecs.stringUtf8(32), Send::to, ByteBufCodecs.stringUtf8(MAX_MESSAGE * 4), Send::message, Send::new);

		@Override
		public Type<? extends CustomPacketPayload> type() {
			return TYPE;
		}
	}

	public static void init() {
		PayloadTypeRegistry.playC2S().register(Send.TYPE, Send.CODEC);
		ServerPlayNetworking.registerGlobalReceiver(Send.TYPE, (payload, context) -> send(context.player(), payload.to(), payload.message()));
		ServerTickEvents.END_SERVER_TICK.register(server -> PostOffice.get(server).tick(server));
	}

	static void send(ServerPlayer player, String toName, String message) {
		if (!(player.containerMenu instanceof MailboxMenu menu) || menu.mailbox() == null) {
			return;
		}
		String name = toName.trim();
		if (name.isEmpty()) {
			tell(player, Component.translatable("message.aliveworkplace.mail.no_name"), ChatFormatting.YELLOW);
			return;
		}
		String letter = message.strip();
		if (letter.length() > MAX_MESSAGE) {
			letter = letter.substring(0, MAX_MESSAGE);
		}
		if (!menu.hasOutgoing() && letter.isEmpty()) {
			tell(player, Component.translatable("message.aliveworkplace.mail.nothing"), ChatFormatting.YELLOW);
			return;
		}
		ServerPlayer online = player.server.getPlayerList().getPlayerByName(name);
		GameProfile profile = online != null ? online.getGameProfile()
			: player.server.getProfileCache() != null ? player.server.getProfileCache().get(name).orElse(null) : null;
		if (profile == null) {
			tell(player, Component.translatable("message.aliveworkplace.mail.unknown", name), ChatFormatting.RED);
			return;
		}
		PostOffice office = PostOffice.get(player.server);
		if (office.mailboxOf(profile.getId()) == null) {
			tell(player, Component.translatable("message.aliveworkplace.mail.no_mailbox", profile.getName()), ChatFormatting.RED);
			return;
		}
		List<ItemStack> items = new java.util.ArrayList<>(menu.takeOutgoing());
		if (!letter.isEmpty()) {
			items.add(0, letter(player.getGameProfile().getName(), letter));
		}
		GlobalPos origin = GlobalPos.of(player.level().dimension(), menu.pos());
		long now = player.level().getGameTime();
		office.post(player.getUUID(), player.getGameProfile().getName(), profile.getId(), profile.getName(), origin, now, items);
		int count = items.stream().mapToInt(ItemStack::getCount).sum();
		player.level().playSound(null, menu.pos(), SoundEvents.BOOK_PAGE_TURN, SoundSource.BLOCKS, 1f, 1f);
		player.sendSystemMessage(Component.translatable("message.aliveworkplace.mail.posted", count, profile.getName()).withStyle(ChatFormatting.GREEN)
			.append(Component.literal(" "))
			.append(Component.translatable("message.aliveworkplace.mail.track").withStyle(style -> style.withColor(ChatFormatting.AQUA).withUnderlined(true)
				.withClickEvent(new net.minecraft.network.chat.ClickEvent(net.minecraft.network.chat.ClickEvent.Action.RUN_COMMAND, "/workplace mail")))));
		if (!office.isServed(origin, now)) {
			tell(player, Component.translatable("message.aliveworkplace.mail.no_postman", PostOffice.ROUND), ChatFormatting.YELLOW);
		}
	}

	/** A letter: a written book from {@code author} with the message on its page, readable like any book. */
	public static ItemStack letter(String author, String message) {
		ItemStack book = new ItemStack(net.minecraft.world.item.Items.WRITTEN_BOOK);
		String title = Component.translatable("item.aliveworkplace.letter.title", author).getString();
		book.set(net.minecraft.core.component.DataComponents.WRITTEN_BOOK_CONTENT, new net.minecraft.world.item.component.WrittenBookContent(
			net.minecraft.server.network.Filterable.passThrough(title.length() > 32 ? title.substring(0, 32) : title), author, 0,
			List.of(net.minecraft.server.network.Filterable.passThrough(Component.literal(message))), true));
		return book;
	}

	/** What's on its way to and from {@code player}, for {@code /workplace mail}. */
	public static List<Component> tracking(net.minecraft.server.MinecraftServer server, java.util.UUID player) {
		List<Component> out = new java.util.ArrayList<>();
		for (Parcel parcel : PostOffice.get(server).parcels()) {
			boolean sent = parcel.from().equals(player);
			boolean coming = parcel.to().equals(player);
			if (!sent && !coming) {
				continue;
			}
			Component where = switch (parcel.status()) {
				case AWAITING_PICKUP -> Component.translatable("message.aliveworkplace.mail.status.awaiting_pickup");
				case CARRIED -> Component.translatable("message.aliveworkplace.mail.status.carried");
				case IN_TRANSIT -> Component.translatable("message.aliveworkplace.mail.status.in_transit");
			};
			out.add(Component.translatable(sent ? "message.aliveworkplace.mail.track_to" : "message.aliveworkplace.mail.track_from",
				sent ? parcel.toName() : parcel.fromName(), parcel.count(), where).withStyle(sent ? ChatFormatting.GRAY : ChatFormatting.GOLD));
		}
		return out;
	}

	private static void tell(ServerPlayer player, Component message, ChatFormatting color) {
		player.sendSystemMessage(message.copy().withStyle(color));
	}

	private Mail() {
	}
}
