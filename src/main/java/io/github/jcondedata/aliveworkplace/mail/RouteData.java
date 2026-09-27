package io.github.jcondedata.aliveworkplace.mail;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

/**
 * A courier route on a Delivery Note: carry items from one container to another. With a filter, only
 * those items; without one, everything except tools, weapons and armor.
 */
public record RouteData(Optional<BlockPos> from, Optional<BlockPos> to, List<Item> filter) {
	public static final int MAX_FILTER = 9;
	public static final RouteData EMPTY = new RouteData(Optional.empty(), Optional.empty(), List.of());

	public static final Codec<RouteData> CODEC = RecordCodecBuilder.create(i -> i.group(
		BlockPos.CODEC.optionalFieldOf("from").forGetter(RouteData::from),
		BlockPos.CODEC.optionalFieldOf("to").forGetter(RouteData::to),
		BuiltInRegistries.ITEM.byNameCodec().listOf().optionalFieldOf("filter", List.of()).forGetter(RouteData::filter)
	).apply(i, RouteData::new));

	public static final StreamCodec<RegistryFriendlyByteBuf, RouteData> STREAM_CODEC = ByteBufCodecs.fromCodecWithRegistries(CODEC);

	public boolean isComplete() {
		return from.isPresent() && to.isPresent();
	}

	/** Whether the courier should carry {@code stack} on this route. */
	public boolean carries(ItemStack stack) {
		if (stack.isEmpty()) {
			return false;
		}
		return filter.isEmpty() ? !stack.isDamageableItem() : filter.contains(stack.getItem());
	}

	public RouteData toggle(Item item) {
		List<Item> next = new ArrayList<>(filter);
		if (!next.remove(item) && next.size() < MAX_FILTER) {
			next.add(item);
		}
		return new RouteData(from, to, List.copyOf(next));
	}
}
