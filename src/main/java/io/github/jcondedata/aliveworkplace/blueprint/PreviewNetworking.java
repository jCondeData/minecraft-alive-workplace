package io.github.jcondedata.aliveworkplace.blueprint;

import io.github.jcondedata.aliveworkplace.platform.Platform;
import io.github.jcondedata.aliveworkplace.AliveWorkplace;
import io.github.jcondedata.aliveworkplace.build.MaterialRules;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Sends a blueprint's blocks to a client so it can draw the see-through preview. Only blocks a
 * builder would place are sent (in template space; the client applies the placement), capped so the
 * packet stays small — a bigger build previews as an outline only.
 */
public final class PreviewNetworking {
	public static final int MAX_BLOCKS = 60_000;
	private static final Map<java.util.UUID, Map<ResourceLocation, Integer>> LAST_REQUEST = new HashMap<>();

	/** C2S: please send me the preview for this blueprint. */
	public record Request(ResourceLocation id) implements CustomPacketPayload {
		public static final Type<Request> TYPE = new Type<>(AliveWorkplace.id("preview_request"));
		public static final StreamCodec<RegistryFriendlyByteBuf, Request> CODEC = StreamCodec.composite(
			ResourceLocation.STREAM_CODEC, Request::id, Request::new);

		@Override
		public Type<? extends CustomPacketPayload> type() {
			return TYPE;
		}
	}

	/**
	 * S2C: the blocks. {@code palette} holds block-state ids; {@code blocks} is x, y, z, paletteIndex
	 * repeated. {@code complete} is false if the build was too big to send.
	 */
	public record Data(ResourceLocation id, List<Integer> palette, int[] blocks, boolean complete) implements CustomPacketPayload {
		public static final Type<Data> TYPE = new Type<>(AliveWorkplace.id("preview_data"));
		public static final StreamCodec<RegistryFriendlyByteBuf, Data> CODEC = StreamCodec.composite(
			ResourceLocation.STREAM_CODEC, Data::id,
			ByteBufCodecs.VAR_INT.apply(ByteBufCodecs.list()), Data::palette,
			StreamCodec.of((buf, arr) -> buf.writeVarIntArray(arr), buf -> buf.readVarIntArray()), Data::blocks,
			ByteBufCodecs.BOOL, Data::complete,
			Data::new);

		@Override
		public Type<? extends CustomPacketPayload> type() {
			return TYPE;
		}
	}

	public static void init() {
		Platform.get().clientbound(Data.TYPE, Data.CODEC);
		Platform.get().serverbound(Request.TYPE, Request.CODEC, (payload, player) -> send(player, payload.id()));
		Platform.get().onPlayerLeave(player -> LAST_REQUEST.remove(player.getUUID()));
	}

	private static void send(ServerPlayer player, ResourceLocation id) {
		// Clients ask once per blueprint; ignore repeats so a bad client can't spam big packets.
		int now = player.level().getServer().getTickCount();
		Integer last = LAST_REQUEST.computeIfAbsent(player.getUUID(), u -> new HashMap<>()).put(id, now);
		if (last != null && now - last < 100) {
			return;
		}
		BlueprintLibrary.get(player.level().getServer(), id).ifPresent(bp -> Platform.get().send(player, build(bp)));
	}

	public static Data build(Blueprint blueprint) {
		Map<BlockState, Integer> index = new HashMap<>();
		List<Integer> palette = new ArrayList<>();
		List<Blueprint.Entry> shown = new ArrayList<>();
		for (Blueprint.Entry e : blueprint.blocks()) {
			BlockState state = e.state();
			if (state.isAir() || MaterialRules.classify(state, e.nbt()) == MaterialRules.Kind.SKIP && !MaterialRules.isSecondaryHalf(state)) {
				continue;
			}
			shown.add(e);
		}
		boolean complete = shown.size() <= MAX_BLOCKS;
		int count = Math.min(shown.size(), MAX_BLOCKS);
		int[] blocks = new int[count * 4];
		for (int i = 0; i < count; i++) {
			Blueprint.Entry e = shown.get(i);
			Integer idx = index.get(e.state());
			if (idx == null) {
				idx = palette.size();
				index.put(e.state(), idx);
				palette.add(Block.getId(e.state()));
			}
			blocks[i * 4] = e.pos().getX();
			blocks[i * 4 + 1] = e.pos().getY();
			blocks[i * 4 + 2] = e.pos().getZ();
			blocks[i * 4 + 3] = idx;
		}
		return new Data(blueprint.id(), palette, blocks, complete);
	}

	private PreviewNetworking() {
	}
}
