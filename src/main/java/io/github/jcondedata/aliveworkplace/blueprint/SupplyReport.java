package io.github.jcondedata.aliveworkplace.blueprint;

import java.util.List;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.world.item.Item;

/**
 * What a placed blueprint still needs from the chests by the nearest Builder's Bench (the server keeps it
 * up to date while the blueprint is in a player's inventory; never saved). {@code bench} is empty when no
 * bench is close enough to build there.
 */
public record SupplyReport(Optional<BlockPos> bench, int chests, List<Missing> missing) {
	public record Missing(Item item, int count) {
		public static final StreamCodec<RegistryFriendlyByteBuf, Missing> STREAM_CODEC = StreamCodec.composite(
			ByteBufCodecs.registry(Registries.ITEM), Missing::item,
			ByteBufCodecs.VAR_INT, Missing::count,
			Missing::new);
	}

	public static final StreamCodec<RegistryFriendlyByteBuf, SupplyReport> STREAM_CODEC = StreamCodec.composite(
		ByteBufCodecs.optional(BlockPos.STREAM_CODEC), SupplyReport::bench,
		ByteBufCodecs.VAR_INT, SupplyReport::chests,
		Missing.STREAM_CODEC.apply(ByteBufCodecs.list()), SupplyReport::missing,
		SupplyReport::new);

	public int totalMissing() {
		return missing.stream().mapToInt(Missing::count).sum();
	}
}
