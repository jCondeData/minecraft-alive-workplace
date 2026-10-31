package io.github.jcondedata.aliveworkplace.mail;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.core.UUIDUtil;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

/**
 * A courier route on a Delivery Note: carry items from one container to another. With a filter, only
 * those items; without one, everything except tools, weapons and armor. {@code by} is the player who marked the
 * source (B717: a protected village's chests aren't theirs to empty unless they may open them); notes and routes from
 * before it have none.
 */
public record RouteData(Optional<BlockPos> from, Optional<BlockPos> to, List<Item> filter, Optional<UUID> by) {
	public static final int MAX_FILTER = 9;
	public static final RouteData EMPTY = new RouteData(Optional.empty(), Optional.empty(), List.of());

	/** A route nobody is named on. */
	public RouteData(Optional<BlockPos> from, Optional<BlockPos> to, List<Item> filter) {
		this(from, to, filter, Optional.empty());
	}

	public static final Codec<RouteData> CODEC = RecordCodecBuilder.create(i -> i.group(
		BlockPos.CODEC.optionalFieldOf("from").forGetter(RouteData::from),
		BlockPos.CODEC.optionalFieldOf("to").forGetter(RouteData::to),
		BuiltInRegistries.ITEM.byNameCodec().listOf().optionalFieldOf("filter", List.of()).forGetter(RouteData::filter),
		UUIDUtil.CODEC.optionalFieldOf("by").forGetter(RouteData::by)
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
		return new RouteData(from, to, List.copyOf(next), by);
	}
}
