package io.github.jcondedata.aliveworkplace.hall;

import io.github.jcondedata.aliveworkplace.build.SupplyContainers;
import io.github.jcondedata.aliveworkplace.mc.Lookup;
import io.github.jcondedata.aliveworkplace.mc.Nbt;
import io.github.jcondedata.aliveworkplace.registry.ModVillagers;
import io.github.jcondedata.aliveworkplace.work.Requests;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.ai.village.poi.PoiManager;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.saveddata.SavedData;
import org.jetbrains.annotations.Nullable;

/**
 * Caravans: villages with a Village Hall send each other what they need. Every hall in a dimension is on a list (with
 * its name and what its workers are waiting for, kept up to date by its own round, so it's known even where the world
 * isn't loaded). A trade route (set up on the hall's Trade Routes page) sends, once a day, whatever the other village is
 * waiting for that this one has plenty of in its Storehouses' chests — up to {@link #CARGO_STACKS} stacks — and the
 * caravan arrives after a trip as long as the road ({@link #travelTicks}), into the other village's Storehouse chests.
 * Both chronicles note it. 27.17: each village builds its half of a road to the other ({@code CaravanRoads}); the list
 * keeps where each half ends and whether it's built ({@link Half}), and on a finished road caravans take three quarters
 * of the time.
 */
public final class Caravans {
	/** How far apart villages can trade. */
	public static int RANGE = 2048;
	/** Most stacks one caravan carries. */
	public static final int CARGO_STACKS = 4;
	/** A village keeps this many of anything back for itself. */
	public static final int KEEP = 16;
	/** Most trade routes out of one village. */
	public static final int MAX_ROUTES = 3;

	/** What a village is waiting for: {@code count} of {@code item}. */
	public record Want(Item item, int count) {
	}

	/** A village on the list: its hall, name and wants, and when it last sent a caravan. */
	public record Village(BlockPos hall, Component name, List<Want> wants, long lastCaravanDay) {
	}

	/**
	 * A village's half of the road to another village (27.17): where it ends (feet), whether it's all built, whether it
	 * stops short at a milestone, and whether the village's chronicle has noted it.
	 */
	public record Half(BlockPos end, boolean finished, boolean milestone, boolean noted) {
	}

	/** Two halves within this many blocks of each other's ends have met: the road is one. */
	public static final int HALVES_MEET = 4;

	/** Goods on the road. */
	public record Shipment(BlockPos from, BlockPos to, List<ItemStack> goods, long arrives) {
	}

	/** The list of villages, routes and caravans on the road in one dimension. */
	public static final class Data extends SavedData {
		private static final String NAME = "aliveworkplace_caravans";
		final Map<BlockPos, Village> villages = new LinkedHashMap<>();
		final Map<BlockPos, Set<BlockPos>> routes = new LinkedHashMap<>();
		final List<Shipment> onTheRoad = new ArrayList<>();
		/** The halves of roads between villages (27.17), from one hall towards another. */
		final Map<BlockPos, Map<BlockPos, Half>> halves = new LinkedHashMap<>();

		public static Data get(ServerLevel level) {
			return level.getDataStorage().computeIfAbsent(new SavedData.Factory<>(Data::new, Data::load, null), NAME);
		}

		public List<Village> villages() {
			return List.copyOf(villages.values());
		}

		@Nullable
		public Village village(BlockPos hall) {
			return villages.get(hall);
		}

		public Set<BlockPos> routesFrom(BlockPos hall) {
			return Set.copyOf(routes.getOrDefault(hall, Set.of()));
		}

		public List<Shipment> onTheRoad() {
			return List.copyOf(onTheRoad);
		}

		void update(BlockPos hall, Component name, List<Want> wants) {
			Village old = villages.get(hall);
			villages.put(hall.immutable(), new Village(hall.immutable(), name, List.copyOf(wants), old == null ? -1 : old.lastCaravanDay()));
			setDirty();
		}

		/** Notes what a village wants (tests, and the hall's round). */
		public void setWants(BlockPos hall, Component name, List<Want> wants) {
			update(hall, name, wants);
		}

		void sent(BlockPos hall, long day) {
			Village v = villages.get(hall);
			if (v != null) {
				villages.put(hall, new Village(v.hall(), v.name(), v.wants(), day));
				setDirty();
			}
		}

		public void remove(BlockPos hall) {
			villages.remove(hall);
			routes.remove(hall);
			routes.values().forEach(to -> to.remove(hall));
			halves.remove(hall);
			halves.values().forEach(to -> to.remove(hall));
			setDirty();
		}

		/** The villages {@code hall} has a trade route with, either way (27.17: each builds its half of the road). */
		public Set<BlockPos> partners(BlockPos hall) {
			Set<BlockPos> out = new LinkedHashSet<>(routes.getOrDefault(hall, Set.of()));
			routes.forEach((from, to) -> {
				if (to.contains(hall)) {
					out.add(from);
				}
			});
			out.remove(hall);
			return out;
		}

		/** {@code hall}'s half of the road towards {@code other}, or null if it has none with a way found yet. */
		@Nullable
		public Half half(BlockPos hall, BlockPos other) {
			return halves.getOrDefault(hall, Map.of()).get(other);
		}

		/** Notes {@code hall}'s half of the road towards {@code other}. */
		public void setHalf(BlockPos hall, BlockPos other, Half half) {
			Map<BlockPos, Half> to = halves.computeIfAbsent(hall.immutable(), k -> new LinkedHashMap<>());
			if (!half.equals(to.get(other))) {
				to.put(other.immutable(), half);
				setDirty();
			}
		}

		/** Whether the road between {@code a} and {@code b} is finished: both halves built, and they meet. */
		public boolean roadFinished(BlockPos a, BlockPos b) {
			Half ab = half(a, b);
			Half ba = half(b, a);
			return ab != null && ba != null && ab.finished() && ba.finished()
				&& Math.abs(ab.end().getX() - ba.end().getX()) + Math.abs(ab.end().getZ() - ba.end().getZ()) <= HALVES_MEET;
		}

		/** Starts or stops the route from {@code from} to {@code to} (at most {@link #MAX_ROUTES}); true if it's on now. */
		public boolean toggleRoute(BlockPos from, BlockPos to) {
			return toggleRoute(from, to, MAX_ROUTES);
		}

		/** Starts or stops the route from {@code from} to {@code to}, with at most {@code max} routes out; true if it's on now. */
		public boolean toggleRoute(BlockPos from, BlockPos to, int max) {
			Set<BlockPos> out = routes.computeIfAbsent(from.immutable(), k -> new LinkedHashSet<>());
			boolean on;
			if (out.remove(to)) {
				on = false;
			} else if (out.size() < max) {
				out.add(to.immutable());
				on = true;
			} else {
				return false;
			}
			setDirty();
			return on;
		}

		void ship(Shipment shipment) {
			onTheRoad.add(shipment);
			setDirty();
		}

		/** The caravans for {@code hall} that have arrived by {@code now}, taken off the road. */
		List<Shipment> arrived(BlockPos hall, long now) {
			List<Shipment> out = new ArrayList<>();
			onTheRoad.removeIf(s -> {
				if (s.to().equals(hall) && s.arrives() <= now) {
					out.add(s);
					return true;
				}
				return false;
			});
			if (!out.isEmpty()) {
				setDirty();
			}
			return out;
		}

		@Override
		public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
			ListTag list = new ListTag();
			for (Village v : villages.values()) {
				CompoundTag t = new CompoundTag();
				t.putLong("hall", v.hall().asLong());
				t.putString("name", Component.Serializer.toJson(v.name(), registries));
				t.putLong("lastCaravanDay", v.lastCaravanDay());
				ListTag wants = new ListTag();
				for (Want w : v.wants()) {
					CompoundTag wt = new CompoundTag();
					wt.putString("item", BuiltInRegistries.ITEM.getKey(w.item()).toString());
					wt.putInt("count", w.count());
					wants.add(wt);
				}
				t.put("wants", wants);
				ListTag to = new ListTag();
				for (BlockPos p : routes.getOrDefault(v.hall(), Set.of())) {
					CompoundTag pt = new CompoundTag();
					pt.putLong("to", p.asLong());
					to.add(pt);
				}
				t.put("routes", to);
				ListTag halfList = new ListTag();
				halves.getOrDefault(v.hall(), Map.of()).forEach((other, h) -> {
					CompoundTag ht = new CompoundTag();
					ht.putLong("to", other.asLong());
					ht.putLong("end", h.end().asLong());
					ht.putBoolean("finished", h.finished());
					ht.putBoolean("milestone", h.milestone());
					ht.putBoolean("noted", h.noted());
					halfList.add(ht);
				});
				t.put("halves", halfList);
				list.add(t);
			}
			tag.put("villages", list);
			ListTag road = new ListTag();
			for (Shipment s : onTheRoad) {
				CompoundTag t = new CompoundTag();
				t.putLong("from", s.from().asLong());
				t.putLong("to", s.to().asLong());
				t.putLong("arrives", s.arrives());
				ListTag goods = new ListTag();
				for (ItemStack stack : s.goods()) {
					if (!stack.isEmpty()) {
						goods.add(stack.save(registries));
					}
				}
				t.put("goods", goods);
				road.add(t);
			}
			tag.put("road", road);
			return tag;
		}

		public static Data load(CompoundTag tag, HolderLookup.Provider registries) {
			Data data = new Data();
			ListTag list = Nbt.getList(tag, "villages", Tag.TAG_COMPOUND);
			for (int i = 0; i < list.size(); i++) {
				CompoundTag t = Nbt.compoundAt(list, i);
				BlockPos hall = BlockPos.of(Nbt.getLong(t, "hall"));
				Component name = Component.Serializer.fromJson(Nbt.getString(t, "name"), registries);
				List<Want> wants = new ArrayList<>();
				ListTag wl = Nbt.getList(t, "wants", Tag.TAG_COMPOUND);
				for (int j = 0; j < wl.size(); j++) {
					ResourceLocation id = ResourceLocation.tryParse(Nbt.getString(Nbt.compoundAt(wl, j), "item"));
					if (id != null && BuiltInRegistries.ITEM.containsKey(id)) {
						wants.add(new Want(Lookup.value(BuiltInRegistries.ITEM, id), Nbt.getInt(Nbt.compoundAt(wl, j), "count")));
					}
				}
				data.villages.put(hall, new Village(hall, name == null ? Component.empty() : name, List.copyOf(wants), Nbt.getLong(t, "lastCaravanDay")));
				ListTag rl = Nbt.getList(t, "routes", Tag.TAG_COMPOUND);
				for (int j = 0; j < rl.size(); j++) {
					data.routes.computeIfAbsent(hall, k -> new LinkedHashSet<>()).add(BlockPos.of(Nbt.getLong(Nbt.compoundAt(rl, j), "to")));
				}
				ListTag hl = Nbt.getList(t, "halves", Tag.TAG_COMPOUND); // 27.17; older saves have none
				for (int j = 0; j < hl.size(); j++) {
					CompoundTag ht = Nbt.compoundAt(hl, j);
					data.halves.computeIfAbsent(hall, k -> new LinkedHashMap<>()).put(BlockPos.of(Nbt.getLong(ht, "to")),
						new Half(BlockPos.of(Nbt.getLong(ht, "end")), Nbt.getBoolean(ht, "finished"), Nbt.getBoolean(ht, "milestone"), Nbt.getBoolean(ht, "noted")));
				}
			}
			ListTag road = Nbt.getList(tag, "road", Tag.TAG_COMPOUND);
			for (int i = 0; i < road.size(); i++) {
				CompoundTag t = Nbt.compoundAt(road, i);
				List<ItemStack> goods = new ArrayList<>();
				ListTag gl = Nbt.getList(t, "goods", Tag.TAG_COMPOUND);
				for (int j = 0; j < gl.size(); j++) {
					ItemStack.parse(registries, Nbt.compoundAt(gl, j)).ifPresent(goods::add);
				}
				data.onTheRoad.add(new Shipment(BlockPos.of(Nbt.getLong(t, "from")), BlockPos.of(Nbt.getLong(t, "to")), goods, Nbt.getLong(t, "arrives")));
			}
			return data;
		}
	}

	/** The shortest trip, in ticks. */
	public static long MIN_TRAVEL = 1200;

	/** How long a caravan takes over {@code blocks} of road: a minute at least, a tick for every two blocks beyond. */
	public static long travelTicks(double blocks) {
		return Math.max(MIN_TRAVEL, (long) (blocks / 2));
	}

	/** How long a caravan takes over {@code blocks}, three quarters of it on a finished road between the villages (27.17). */
	public static long travelTicks(double blocks, boolean road) {
		long ticks = travelTicks(blocks);
		return road ? ticks * 3 / 4 : ticks;
	}

	/** The villages {@code hall} can trade with, nearest first. */
	public static List<Village> neighbours(ServerLevel level, BlockPos hall) {
		return Data.get(level).villages().stream()
			.filter(v -> !v.hall().equals(hall) && v.hall().distSqr(hall) <= (double) RANGE * RANGE)
			.sorted(Comparator.comparingDouble(v -> v.hall().distSqr(hall)))
			.toList();
	}

	/** What the village round {@code hall} is waiting for: its workers' requests, and food when the store runs low. */
	static List<Want> wants(ServerLevel level, BlockPos hall, VillageHalls.Census census) {
		Map<Item, Integer> out = new LinkedHashMap<>();
		for (Requests.Request r : census.requests()) {
			Item item = r.item();
			if (item != null && item != Items.AIR) {
				out.merge(item, r.count(), Math::max);
			}
		}
		// 27.19: what the Steward's waiting builds miss
		for (Map.Entry<Item, Integer> e : io.github.jcondedata.aliveworkplace.city.StewardSafety.shoppingList(level, hall)) {
			out.merge(e.getKey(), e.getValue(), Math::max);
		}
		int needed = VillageGrowth.foodNeeded(level, hall);
		if (census.food() < needed) {
			out.merge(Items.BREAD, needed, Math::max);
		}
		return out.entrySet().stream().limit(12).map(e -> new Want(e.getKey(), e.getValue())).toList();
	}

	/** The chests by the village's Storehouses (where caravans load and unload). */
	static List<BlockPos> storehouse(ServerLevel level, BlockPos hall) {
		Set<BlockPos> chests = new LinkedHashSet<>();
		level.getPoiManager().findAll(h -> h.is(ModVillagers.STOREHOUSE_POI), p -> true, hall, VillageHalls.RADIUS, PoiManager.Occupancy.ANY)
			.forEach(s -> chests.addAll(SupplyContainers.find(level, s.immutable(), null)));
		return new ArrayList<>(chests);
	}

	/** The hall's round: its entry on the list brought up to date, caravans that have come unloaded, today's sent. */
	public static void round(ServerLevel level, BlockPos hall, @Nullable VillageHalls.Census census) {
		Data data = Data.get(level);
		if (census != null) {
			data.update(hall, VillageHalls.name(level, hall), wants(level, hall, census));
		}
		unload(level, hall, data);
		long day = Chronicle.day(level);
		Village us = data.village(hall);
		if (us == null || us.lastCaravanDay() == day || data.routesFrom(hall).isEmpty()) {
			return;
		}
		data.sent(hall, day);
		for (BlockPos to : data.routesFrom(hall)) {
			send(level, hall, to, data);
		}
	}

	/** Loads a caravan for the village at {@code to} with what it wants and we have plenty of; null if there's nothing to send. */
	@Nullable
	static Shipment send(ServerLevel level, BlockPos hall, BlockPos to, Data data) {
		Village them = data.village(to);
		if (them == null) {
			return null;
		}
		List<BlockPos> ours = storehouse(level, hall);
		List<ItemStack> goods = new ArrayList<>();
		for (Want want : them.wants()) {
			if (goods.size() >= CARGO_STACKS) {
				break;
			}
			long have = SupplyContainers.count(level, ours, want.item());
			int spare = (int) Math.min(want.count(), Math.min(want.item().getDefaultMaxStackSize(), have - KEEP));
			if (spare <= 0) {
				continue;
			}
			int taken = SupplyContainers.extract(level, ours, want.item(), spare);
			if (taken > 0) {
				goods.add(new ItemStack(want.item(), taken));
			}
		}
		if (goods.isEmpty()) {
			return null;
		}
		Shipment shipment = new Shipment(hall.immutable(), to.immutable(), List.copyOf(goods), level.getGameTime() + travelTicks(Math.sqrt(hall.distSqr(to)), data.roadFinished(hall, to)));
		data.ship(shipment);
		level.playSound(null, hall, SoundEvents.LLAMA_CHEST, SoundSource.NEUTRAL, 1f, 1f);
		Chronicle.record(level, hall, Chronicle.Kind.CARAVAN, Component.translatable("chronicle.aliveworkplace.caravan_left", them.name(), describe(goods)), true);
		return shipment;
	}

	/** Unloads the caravans that have come to {@code hall} into its Storehouses' chests (what doesn't fit stays on the road). */
	static void unload(ServerLevel level, BlockPos hall, Data data) {
		List<Shipment> arrived = data.arrived(hall, level.getGameTime());
		if (arrived.isEmpty()) {
			return;
		}
		List<BlockPos> chests = storehouse(level, hall);
		for (Shipment s : arrived) {
			List<ItemStack> left = new ArrayList<>();
			for (ItemStack stack : s.goods()) {
				ItemStack rest = chests.isEmpty() ? stack.copy() : SupplyContainers.insert(level, chests, stack.copy());
				if (!rest.isEmpty()) {
					left.add(rest);
				}
			}
			if (!left.isEmpty()) {
				data.ship(new Shipment(s.from(), s.to(), left, level.getGameTime() + VillageNeeds.CHECK_EVERY));
			}
			if (left.size() < s.goods().size() || left.stream().mapToInt(ItemStack::getCount).sum() < s.goods().stream().mapToInt(ItemStack::getCount).sum()) {
				Village from = data.village(s.from());
				Chronicle.record(level, hall, Chronicle.Kind.CARAVAN, Component.translatable("chronicle.aliveworkplace.caravan_came",
					from == null ? Component.translatable("chronicle.aliveworkplace.someone") : from.name(), describe(s.goods())), true);
				level.playSound(null, hall, SoundEvents.LLAMA_CHEST, SoundSource.NEUTRAL, 1f, 1.2f);
			}
		}
	}

	/** "32 × Oak Log, 16 × Bread". */
	static Component describe(List<ItemStack> goods) {
		net.minecraft.network.chat.MutableComponent out = Component.empty();
		for (int i = 0; i < goods.size(); i++) {
			if (i > 0) {
				out.append(", ");
			}
			out.append(Component.translatable("chronicle.aliveworkplace.goods", goods.get(i).getCount(), goods.get(i).getHoverName()));
		}
		return out;
	}

	private Caravans() {
	}
}
