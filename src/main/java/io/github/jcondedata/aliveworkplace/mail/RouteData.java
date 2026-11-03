package io.github.jcondedata.aliveworkplace.mail;

import com.mojang.serialization.Codec;
import com.mojang.serialization.JavaOps;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.core.UUIDUtil;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

/**
 * A courier route on a Delivery Note: carry items from one container to another. With a filter, only
 * those items; without one, everything except tools, weapons and armor. {@code by} is the player who marked the
 * source (B717: a protected village's chests aren't theirs to empty unless they may open them); notes and routes from
 * before it have none.
 *
 * <p>{@code gone} (B819) holds the filter's ids that name no item in this game (a mod taken out of the pack). They
 * still count as a filter: a route whose filter named only a gone mod's items carries nothing, never "everything", and
 * they are saved back as they were, so the items come back on the route if the mod returns.
 */
public record RouteData(Optional<BlockPos> from, Optional<BlockPos> to, List<Item> filter, Optional<UUID> by, List<String> gone) {
	public static final int MAX_FILTER = 9;
	public static final RouteData EMPTY = new RouteData(Optional.empty(), Optional.empty(), List.of());

	public RouteData {
		filter = List.copyOf(filter);
		gone = List.copyOf(gone);
	}

	/** A route nobody is named on. */
	public RouteData(Optional<BlockPos> from, Optional<BlockPos> to, List<Item> filter) {
		this(from, to, filter, Optional.empty());
	}

	public RouteData(Optional<BlockPos> from, Optional<BlockPos> to, List<Item> filter, Optional<UUID> by) {
		this(from, to, filter, by, List.of());
	}

	/** The saved filter: the items' ids, then the ids no item answers to any more, in one list as before B819. */
	private record Filter(List<Item> items, List<String> gone) {
		static final Filter NONE = new Filter(List.of(), List.of());
		static final Codec<Filter> CODEC = io.github.jcondedata.aliveworkplace.mc.Lenient.list(Codec.STRING, "route filter").xmap(Filter::of, Filter::ids);

		static Filter of(List<String> ids) {
			List<Item> items = new ArrayList<>();
			List<String> gone = new ArrayList<>();
			for (String id : ids) {
				Optional<Item> item = BuiltInRegistries.ITEM.byNameCodec().parse(JavaOps.INSTANCE, id).result();
				if (item.isPresent()) {
					items.add(item.get());
				} else {
					gone.add(id);
				}
			}
			return new Filter(List.copyOf(items), List.copyOf(gone));
		}

		List<String> ids() {
			List<String> ids = new ArrayList<>();
			for (Item item : items) {
				ids.add(BuiltInRegistries.ITEM.getKey(item).toString());
			}
			ids.addAll(gone);
			return ids;
		}
	}

	public static final Codec<RouteData> CODEC = RecordCodecBuilder.create(i -> i.group(
		BlockPos.CODEC.optionalFieldOf("from").forGetter(RouteData::from),
		BlockPos.CODEC.optionalFieldOf("to").forGetter(RouteData::to),
		Filter.CODEC.optionalFieldOf("filter", Filter.NONE).forGetter(r -> new Filter(r.filter, r.gone)),
		UUIDUtil.CODEC.optionalFieldOf("by").forGetter(RouteData::by)
	).apply(i, (from, to, filter, by) -> new RouteData(from, to, filter.items(), by, filter.gone())));

	public static final StreamCodec<RegistryFriendlyByteBuf, RouteData> STREAM_CODEC = ByteBufCodecs.fromCodecWithRegistries(CODEC);

	public boolean isComplete() {
		return from.isPresent() && to.isPresent();
	}

	/** Whether the courier should carry {@code stack} on this route. */
	public boolean carries(ItemStack stack) {
		if (stack.isEmpty()) {
			return false;
		}
		return carriesEverything() ? !stack.isDamageableItem() : filter.contains(stack.getItem());
	}

	/** Whether no filter was ever set: a filter whose items all left the game still filters (B819). */
	public boolean carriesEverything() {
		return filter.isEmpty() && gone.isEmpty();
	}

	/**
	 * What the route carries, for the note's tooltip and the postman's list: "everything but tools…", or the items' names,
	 * with "1 item no longer in the game" for the gone ids (B819).
	 */
	public Component filterText() {
		return carriesEverything() ? filterNames() : Component.translatable("tooltip.aliveworkplace.route.only", filterNames());
	}

	/** {@link #filterText} without "Carries only:", as the postman's route list writes it. */
	public Component filterNames() {
		if (carriesEverything()) {
			return Component.translatable("tooltip.aliveworkplace.route.everything");
		}
		MutableComponent names = Component.empty();
		for (int k = 0; k < filter.size(); k++) {
			if (k > 0) {
				names.append(", ");
			}
			names.append(filter.get(k).getDescription());
		}
		if (!gone.isEmpty()) {
			if (!filter.isEmpty()) {
				names.append(", ");
			}
			names.append(io.github.jcondedata.aliveworkplace.work.Words.counted("tooltip.aliveworkplace.route.gone", gone.size(), gone.size()));
		}
		return names;
	}

	/** The route with the same filter between other ends: the filter's gone ids stay with it. */
	public RouteData withEnds(Optional<BlockPos> from, Optional<BlockPos> to, Optional<UUID> by) {
		return new RouteData(from, to, filter, by, gone);
	}

	public RouteData toggle(Item item) {
		List<Item> next = new ArrayList<>(filter);
		if (!next.remove(item) && next.size() < MAX_FILTER) {
			next.add(item);
		}
		return new RouteData(from, to, List.copyOf(next), by, gone);
	}
}
