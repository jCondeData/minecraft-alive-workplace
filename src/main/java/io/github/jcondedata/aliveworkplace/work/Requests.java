package io.github.jcondedata.aliveworkplace.work;

import io.github.jcondedata.aliveworkplace.build.BuildSite;
import io.github.jcondedata.aliveworkplace.build.Builders;
import io.github.jcondedata.aliveworkplace.build.Employer;
import io.github.jcondedata.aliveworkplace.mc.Lookup;
import io.github.jcondedata.aliveworkplace.mine.Miners;
import io.github.jcondedata.aliveworkplace.mine.QuarrySite;
import io.github.jcondedata.aliveworkplace.registry.ModAttachments;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;
import java.util.function.Predicate;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.jetbrains.annotations.Nullable;

/**
 * What the workers are waiting for: a builder's missing materials, a miner's pickaxe or ladders, a lumberjack's axe, a
 * farmer's seeds, a fisherman's rod. The Storehouse shows them on its board (players can hand things over there), and
 * the jobs that can make something see what's wanted: lumberjacks fell the kinds of tree whose wood is asked for first.
 * Builders and miners keep what they're missing in their saved site; the others post it here while they wait (not
 * saved: they post again as soon as they check after a restart).
 */
public final class Requests {
	/** A post is forgotten if its worker hasn't repeated it for this long (they stopped waiting, or aren't loaded). */
	private static final long FRESH_TICKS = 600;

	/** One thing a worker is waiting for: {@code count} of what {@code accepts}, shown as {@code icon}. */
	public record Request(Villager worker, BlockPos station, ItemStack icon, int count, Component what, Predicate<ItemStack> accepts) {
		/** The exact item asked for, or null for "any pickaxe" and the like. */
		@Nullable
		public Item item() {
			return accepts instanceof ExactItem exact ? exact.item() : null;
		}
	}

	/** Accepts one plain item (so it can be counted and moved by item). */
	record ExactItem(Item item) implements Predicate<ItemStack> {
		@Override
		public boolean test(ItemStack stack) {
			return stack.is(item);
		}
	}

	private record Post(ItemStack icon, int count, Component what, Predicate<ItemStack> accepts, long time) {
	}

	private static final Map<Villager, Post> POSTS = new WeakHashMap<>();

	/** Told when a player hands something over for a request (the Storehouse board). */
	@FunctionalInterface
	public interface Given {
		void given(net.minecraft.server.level.ServerPlayer player, Request request, int count);
	}

	private static final List<Given> GIVEN = new java.util.concurrent.CopyOnWriteArrayList<>();

	/** Adds a listener for hand-overs (a strange mood credits the player, 29.10). */
	public static void onGiven(Given listener) {
		GIVEN.add(listener);
	}

	/** {@code player} handed over {@code count} for {@code request}. */
	public static void given(net.minecraft.server.level.ServerPlayer player, Request request, int count) {
		for (Given listener : GIVEN) {
			listener.given(player, request, count);
		}
	}

	/** {@code villager} is waiting for {@code count} of what {@code accepts} (call again every so often while waiting). */
	public static void post(Villager villager, ItemStack icon, int count, Component what, Predicate<ItemStack> accepts) {
		synchronized (POSTS) {
			POSTS.put(villager, new Post(icon, count, what, accepts, villager.level().getGameTime()));
		}
	}

	/** A tool: one of whatever {@code accepts}, shown as {@code icon} and called {@code key} ("a pickaxe"). */
	public static void postTool(Villager villager, Item icon, String key, Predicate<ItemStack> accepts) {
		post(villager, new ItemStack(icon), 1, Component.translatable("request.aliveworkplace." + key), accepts);
	}

	/** {@code villager} isn't waiting for anything (any more). */
	public static void clear(Villager villager) {
		synchronized (POSTS) {
			POSTS.remove(villager);
		}
	}

	/** Everything {@code worker} is waiting for right now. */
	public static List<Request> of(ServerLevel level, Villager worker) {
		List<Request> out = new ArrayList<>();
		BlockPos station = Builders.benchPos(worker).orElse(null);
		if (station == null) {
			return out;
		}
		out.addAll(io.github.jcondedata.aliveworkplace.legend.StrangeMoods.requests(level, worker)); // a strange mood's materials (29.10)
		BuildSite site = Builders.activeSite(level, worker);
		if (site != null && site.status() == BuildSite.Status.WAITING_FOR_MATERIALS) {
			site.missing().entrySet().stream()
				.sorted(Map.Entry.<Item, Integer>comparingByValue().reversed())
				.forEach(e -> out.add(forItem(worker, station, e.getKey(), e.getValue())));
		}
		QuarrySite quarry = Miners.activeSite(level, worker);
		if (quarry != null && quarry.status() == QuarrySite.Status.NEEDS_PICKAXE) {
			out.add(new Request(worker, station, new ItemStack(Items.IRON_PICKAXE), 1, Component.translatable("request.aliveworkplace.pickaxe"),
				stack -> stack.is(ItemTags.PICKAXES)));
		} else if (quarry != null && quarry.status() == QuarrySite.Status.NEEDS_LADDERS) {
			out.add(forItem(worker, station, Items.LADDER, 64));
		}
		synchronized (POSTS) {
			Post post = POSTS.get(worker);
			if (post != null && level.getGameTime() - post.time() <= FRESH_TICKS) {
				out.add(new Request(worker, station, post.icon(), post.count(), post.what(), post.accepts()));
			}
		}
		return out;
	}

	/** {@code count} of one plain item for {@code worker}. */
	public static Request forItem(Villager worker, BlockPos station, Item item, int count) {
		return new Request(worker, station, new ItemStack(item), count, item.getDescription(), new ExactItem(item));
	}

	/** What the workers near {@code pos} who answer to the same people as {@code boss} are waiting for, nearest first. */
	public static List<Request> near(ServerLevel level, BlockPos pos, @Nullable Employer boss) {
		List<Request> out = new ArrayList<>();
		for (Villager worker : Village.workersNear(level, pos, boss)) {
			out.addAll(of(level, worker));
		}
		return out;
	}

	/** What {@code villager}'s village (the workers who share with them) is waiting for. */
	public static List<Request> forVillage(ServerLevel level, Villager villager, BlockPos station) {
		return near(level, station, ModAttachments.BUILDER_EMPLOYER.get(villager));
	}

	/**
	 * The logs the requests are made of: logs asked for as they are, and the log of the wood that planks, stairs, doors
	 * and the like are made from ({@code oak_planks} → {@code oak_log}, by the usual naming, so modded woods count too).
	 */
	public static Set<Item> wantedLogs(List<Request> requests) {
		Set<Item> out = new HashSet<>();
		for (Request r : requests) {
			Item item = r.item();
			if (item == null) {
				continue;
			}
			if (new ItemStack(item).is(ItemTags.LOGS)) {
				out.add(item);
				net.minecraft.world.item.Item unstripped = io.github.jcondedata.aliveworkplace.wood.Trees.unstripped(item);
				if (unstripped != null) {
					out.add(unstripped); // stripped logs come from the tree's own
				}
				continue;
			}
			Item log = logFor(item);
			if (log != null) {
				out.add(log);
			}
		}
		return out;
	}

	private static final String[] WOOD_SUFFIXES = {"_planks", "_stairs", "_slab", "_fence_gate", "_fence", "_door", "_trapdoor",
		"_pressure_plate", "_button", "_hanging_sign", "_sign", "_wood"};

	/** The log a wooden block's wood comes from, by name ({@code spruce_stairs} → {@code spruce_log}); null if none. */
	@Nullable
	static Item logFor(Item item) {
		ResourceLocation id = BuiltInRegistries.ITEM.getKey(item);
		String path = id.getPath();
		if (path.startsWith("stripped_")) {
			path = path.substring("stripped_".length());
		}
		for (String suffix : WOOD_SUFFIXES) {
			if (path.endsWith(suffix)) {
				String wood = path.substring(0, path.length() - suffix.length());
				for (String kind : new String[] {"_log", "_stem"}) {
					Item log = Lookup.value(BuiltInRegistries.ITEM, ResourceLocation.fromNamespaceAndPath(id.getNamespace(), wood + kind));
					if (log != Items.AIR && new ItemStack(log).is(ItemTags.LOGS)) {
						return log;
					}
				}
				return null;
			}
		}
		return null;
	}

	private Requests() {
	}
}
