package io.github.jcondedata.aliveworkplace.table;

import io.github.jcondedata.aliveworkplace.AliveWorkplace;
import java.util.List;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentSerialization;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * Packets for the Blueprint Table screen. The server owns the blueprint library; the client screen
 * only shows what it is sent and asks for things.
 */
public final class TablePayloads {
	/** Bytes per upload packet (client-to-server custom payloads are limited to 32 KiB). */
	public static final int UPLOAD_CHUNK = 30_000;

	/** One blueprint in the library listing. */
	public record Entry(ResourceLocation id, int sizeX, int sizeY, int sizeZ, int blocks) {
		public static final StreamCodec<FriendlyByteBuf, Entry> CODEC = StreamCodec.composite(
			ResourceLocation.STREAM_CODEC, Entry::id,
			ByteBufCodecs.VAR_INT, Entry::sizeX,
			ByteBufCodecs.VAR_INT, Entry::sizeY,
			ByteBufCodecs.VAR_INT, Entry::sizeZ,
			ByteBufCodecs.VAR_INT, Entry::blocks,
			Entry::new);
	}

	/** An item and how many of it a blueprint needs. */
	public record Material(ResourceLocation item, int count) {
		public static final StreamCodec<FriendlyByteBuf, Material> CODEC = StreamCodec.composite(
			ResourceLocation.STREAM_CODEC, Material::item,
			ByteBufCodecs.VAR_INT, Material::count,
			Material::new);
	}

	/** S2C: open (or refresh) the table screen with the server's library. */
	public record Open(BlockPos table, List<Entry> entries, boolean canUpload) implements CustomPacketPayload {
		public static final Type<Open> TYPE = new Type<>(AliveWorkplace.id("table_open"));
		public static final StreamCodec<RegistryFriendlyByteBuf, Open> CODEC = StreamCodec.composite(
			BlockPos.STREAM_CODEC, Open::table,
			Entry.CODEC.apply(ByteBufCodecs.list()), Open::entries,
			ByteBufCodecs.BOOL, Open::canUpload,
			Open::new);

		@Override
		public Type<? extends CustomPacketPayload> type() {
			return TYPE;
		}
	}

	/** C2S: what does this blueprint need? */
	public record RequestDetails(ResourceLocation id) implements CustomPacketPayload {
		public static final Type<RequestDetails> TYPE = new Type<>(AliveWorkplace.id("table_request_details"));
		public static final StreamCodec<RegistryFriendlyByteBuf, RequestDetails> CODEC = StreamCodec.composite(
			ResourceLocation.STREAM_CODEC, RequestDetails::id, RequestDetails::new);

		@Override
		public Type<? extends CustomPacketPayload> type() {
			return TYPE;
		}
	}

	/** S2C: the materials list for one blueprint. */
	public record Details(ResourceLocation id, List<Material> materials) implements CustomPacketPayload {
		public static final Type<Details> TYPE = new Type<>(AliveWorkplace.id("table_details"));
		public static final StreamCodec<RegistryFriendlyByteBuf, Details> CODEC = StreamCodec.composite(
			ResourceLocation.STREAM_CODEC, Details::id,
			Material.CODEC.apply(ByteBufCodecs.list()), Details::materials,
			Details::new);

		@Override
		public Type<? extends CustomPacketPayload> type() {
			return TYPE;
		}
	}

	/** C2S: give me this blueprint (costs a Blank Blueprint in survival). */
	public record Take(BlockPos table, ResourceLocation id) implements CustomPacketPayload {
		public static final Type<Take> TYPE = new Type<>(AliveWorkplace.id("table_take"));
		public static final StreamCodec<RegistryFriendlyByteBuf, Take> CODEC = StreamCodec.composite(
			BlockPos.STREAM_CODEC, Take::table,
			ResourceLocation.STREAM_CODEC, Take::id,
			Take::new);

		@Override
		public Type<? extends CustomPacketPayload> type() {
			return TYPE;
		}
	}

	/** C2S: one piece of a file being uploaded. Pieces must arrive in order. */
	public record UploadChunk(BlockPos table, String fileName, int totalBytes, int offset, byte[] data) implements CustomPacketPayload {
		public static final Type<UploadChunk> TYPE = new Type<>(AliveWorkplace.id("table_upload"));
		public static final StreamCodec<RegistryFriendlyByteBuf, UploadChunk> CODEC = StreamCodec.composite(
			BlockPos.STREAM_CODEC, UploadChunk::table,
			ByteBufCodecs.stringUtf8(256), UploadChunk::fileName,
			ByteBufCodecs.VAR_INT, UploadChunk::totalBytes,
			ByteBufCodecs.VAR_INT, UploadChunk::offset,
			ByteBufCodecs.byteArray(UPLOAD_CHUNK), UploadChunk::data,
			UploadChunk::new);

		@Override
		public Type<? extends CustomPacketPayload> type() {
			return TYPE;
		}
	}

	/** S2C: how an upload went. */
	public record UploadResult(boolean ok, Component message, Optional<ResourceLocation> id) implements CustomPacketPayload {
		public static final Type<UploadResult> TYPE = new Type<>(AliveWorkplace.id("table_upload_result"));
		public static final StreamCodec<RegistryFriendlyByteBuf, UploadResult> CODEC = StreamCodec.composite(
			ByteBufCodecs.BOOL, UploadResult::ok,
			ComponentSerialization.STREAM_CODEC, UploadResult::message,
			ByteBufCodecs.optional(ResourceLocation.STREAM_CODEC), UploadResult::id,
			UploadResult::new);

		@Override
		public Type<? extends CustomPacketPayload> type() {
			return TYPE;
		}
	}

	private TablePayloads() {
	}
}
