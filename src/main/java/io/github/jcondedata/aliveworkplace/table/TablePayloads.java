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

	/**
	 * One blueprint in the library listing. {@code state} is a {@link BlueprintUnlocks.State} ordinal (26.10); a locked
	 * build's {@code hint} is the lang key of how to get it. A hidden ("???") entry carries a stand-in id and no size.
	 */
	public record Entry(ResourceLocation id, int sizeX, int sizeY, int sizeZ, int blocks, int state, String hint) {
		public static final StreamCodec<FriendlyByteBuf, Entry> CODEC = StreamCodec.of((buf, e) -> {
			ResourceLocation.STREAM_CODEC.encode(buf, e.id());
			buf.writeVarInt(e.sizeX());
			buf.writeVarInt(e.sizeY());
			buf.writeVarInt(e.sizeZ());
			buf.writeVarInt(e.blocks());
			buf.writeVarInt(e.state());
			buf.writeUtf(e.hint(), 256);
		}, buf -> new Entry(ResourceLocation.STREAM_CODEC.decode(buf), buf.readVarInt(), buf.readVarInt(), buf.readVarInt(), buf.readVarInt(),
			buf.readVarInt(), buf.readUtf(256)));

		/** An unlocked entry. */
		public Entry(ResourceLocation id, int sizeX, int sizeY, int sizeZ, int blocks) {
			this(id, sizeX, sizeY, sizeZ, blocks, BlueprintUnlocks.State.UNLOCKED.ordinal(), "");
		}

		public boolean unlocked() {
			return state == BlueprintUnlocks.State.UNLOCKED.ordinal();
		}

		public boolean locked() {
			return state == BlueprintUnlocks.State.LOCKED.ordinal();
		}

		public boolean hidden() {
			return state == BlueprintUnlocks.State.HIDDEN.ordinal();
		}
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

	/**
	 * A style the selected build can be built in (26.11): its name (as in {@code blueprint_styles/}), title and icon item.
	 */
	public record StyleChoice(String name, Component title, ResourceLocation icon) {
		public static final StreamCodec<RegistryFriendlyByteBuf, StyleChoice> CODEC = StreamCodec.composite(
			ByteBufCodecs.stringUtf8(64), StyleChoice::name,
			ComponentSerialization.STREAM_CODEC, StyleChoice::title,
			ResourceLocation.STREAM_CODEC, StyleChoice::icon,
			StyleChoice::new);
	}

	/**
	 * C2S: what does this blueprint need? {@code id} is a listed build, or one in a style (26.11:
	 * {@link io.github.jcondedata.aliveworkplace.blueprint.BlueprintStyles#styled}); the answer comes back under that id.
	 */
	public record RequestDetails(ResourceLocation id) implements CustomPacketPayload {
		public static final Type<RequestDetails> TYPE = new Type<>(AliveWorkplace.id("table_request_details"));
		public static final StreamCodec<RegistryFriendlyByteBuf, RequestDetails> CODEC = StreamCodec.composite(
			ResourceLocation.STREAM_CODEC, RequestDetails::id, RequestDetails::new);

		@Override
		public Type<? extends CustomPacketPayload> type() {
			return TYPE;
		}
	}

	/**
	 * S2C: the materials list for one blueprint (as drawn or in a style), and the styles its build can be built in (26.11;
	 * none for a locked build or a Wonder).
	 */
	public record Details(ResourceLocation id, List<Material> materials, List<StyleChoice> styles) implements CustomPacketPayload {
		public static final Type<Details> TYPE = new Type<>(AliveWorkplace.id("table_details"));
		public static final StreamCodec<RegistryFriendlyByteBuf, Details> CODEC = StreamCodec.composite(
			ResourceLocation.STREAM_CODEC, Details::id,
			Material.CODEC.apply(ByteBufCodecs.list()), Details::materials,
			StyleChoice.CODEC.apply(ByteBufCodecs.list()), Details::styles,
			Details::new);

		public Details(ResourceLocation id, List<Material> materials) {
			this(id, materials, List.of());
		}

		@Override
		public Type<? extends CustomPacketPayload> type() {
			return TYPE;
		}
	}

	/**
	 * S2C (26.9): the blocks the table screen's 3D preview draws for one blueprint, sent with its {@link Details}.
	 * {@code palette} holds block-state ids; {@code blocks} is x, y, z, paletteIndex repeated, in cells of
	 * {@code scale}×{@code scale}×{@code scale} blocks (1 for most builds; bigger builds are drawn coarser so the
	 * preview stays within {@link TableServer#PREVIEW_CAP} cells). Blocks hidden inside the build are left out.
	 */
	/**
	 * A build's 3D preview: its size above the ground, how many blocks of it lie under the ground ({@code below}, a
	 * basement's depth, 36.1; 0 for most builds), the cell size and the cells ({@code x, y, z, palette index}, in cells;
	 * a basement's cells have y below 0).
	 */
	public record Preview(ResourceLocation id, int sizeX, int sizeY, int sizeZ, int below, int scale, List<Integer> palette, int[] blocks)
		implements CustomPacketPayload {
		public static final Type<Preview> TYPE = new Type<>(AliveWorkplace.id("table_preview"));
		private static final StreamCodec<? super RegistryFriendlyByteBuf, List<Integer>> PALETTE = ByteBufCodecs.VAR_INT.apply(ByteBufCodecs.list());
		public static final StreamCodec<RegistryFriendlyByteBuf, Preview> CODEC = StreamCodec.of((buf, p) -> {
			ResourceLocation.STREAM_CODEC.encode(buf, p.id());
			buf.writeVarInt(p.sizeX());
			buf.writeVarInt(p.sizeY());
			buf.writeVarInt(p.sizeZ());
			buf.writeVarInt(p.below());
			buf.writeVarInt(p.scale());
			PALETTE.encode(buf, p.palette());
			buf.writeVarIntArray(p.blocks());
		}, buf -> new Preview(ResourceLocation.STREAM_CODEC.decode(buf), buf.readVarInt(), buf.readVarInt(), buf.readVarInt(), buf.readVarInt(),
			buf.readVarInt(), PALETTE.decode(buf), buf.readVarIntArray()));

		/** Cells sent. */
		public int count() {
			return blocks.length / 4;
		}

		@Override
		public Type<? extends CustomPacketPayload> type() {
			return TYPE;
		}
	}

	/** C2S: give me this blueprint (costs a Blank Blueprint in survival); a styled id gives it set to that style (26.11). */
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
