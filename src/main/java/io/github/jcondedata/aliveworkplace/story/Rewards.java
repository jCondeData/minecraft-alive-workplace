package io.github.jcondedata.aliveworkplace.story;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.mojang.serialization.JsonOps;
import io.github.jcondedata.aliveworkplace.hall.Chronicle;
import io.github.jcondedata.aliveworkplace.hall.VillageHallBlockEntity;
import io.github.jcondedata.aliveworkplace.mc.Lookup;
import io.github.jcondedata.aliveworkplace.work.Money;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Function;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentSerialization;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.GsonHelper;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.LootTable;
import net.minecraft.world.level.storage.loot.parameters.LootContextParamSets;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

/**
 * The rewards a quest file may pay (ROADMAP 31.2), by type: each a small record read from its JSON and written back the
 * same way. Money and items go to whoever finished the quest; the rest go to the village.
 */
public final class Rewards {
	/** One reward. {@link #resolve} fixes it when the quest goes up (money times the rank factor). */
	public interface Reward {
		String type();

		JsonObject json();

		default Reward resolve(List<Objectives.Objective> objectives, float rankFactor) {
			return this;
		}

		void give(ServerLevel level, BlockPos hall, @Nullable ServerPlayer finisher);
	}

	private static final Map<String, Function<JsonObject, Reward>> KINDS = new LinkedHashMap<>();

	static {
		register("money", Money_::read);
		register("item", ItemReward::read);
		register("loot_table", j -> new Loot(id(j, "table")));
		register("chronicle", j -> new ChronicleLine(GsonHelper.getAsString(j, "kind", "quest").toLowerCase(Locale.ROOT), text(j.get("line"))));
		register("village_mood", j -> new VillageMood(GsonHelper.getAsInt(j, "points"), Objectives.positive(j, "days"),
			j.has("reason") ? text(j.get("reason")) : Component.translatable("mood.aliveworkplace.reason.quest")));
		register("treasury", j -> new Treasury(Objectives.positive(j, "emeralds")));
		register("map", j -> new MapReward(Places.read(j.get("place"))));
	}

	public static void register(String type, Function<JsonObject, Reward> reader) {
		KINDS.put(type, reader);
	}

	/** One reward; throws {@link IllegalArgumentException} for an unknown type or a bad field. */
	public static Reward parse(JsonObject json) {
		String type = GsonHelper.getAsString(json, "type", "");
		Function<JsonObject, Reward> reader = KINDS.get(type);
		if (reader == null) {
			throw new IllegalArgumentException("unknown reward type '" + type + "'");
		}
		try {
			return reader.apply(json);
		} catch (RuntimeException e) {
			throw new IllegalArgumentException("reward '" + type + "': " + e.getMessage(), e);
		}
	}

	/** The emeralds a quest's resolved rewards pay. */
	public static int emeralds(List<Reward> rewards) {
		return rewards.stream().filter(r -> r instanceof Money_).mapToInt(r -> ((Money_) r).emeralds()).sum();
	}

	static ResourceLocation id(JsonObject json, String field) {
		ResourceLocation id = json.has(field) ? ResourceLocation.tryParse(json.get(field).getAsString()) : null;
		if (id == null) {
			throw new IllegalArgumentException("missing or bad '" + field + "'");
		}
		return id;
	}

	/** A text component: a plain string or a component object ({@code {"translate": ...}}). */
	public static Component text(@Nullable JsonElement json) {
		if (json == null) {
			throw new IllegalArgumentException("missing text");
		}
		return ComponentSerialization.CODEC.parse(JsonOps.INSTANCE, json).getOrThrow(IllegalArgumentException::new);
	}

	public static JsonElement json(Component text) {
		return ComponentSerialization.CODEC.encodeStart(JsonOps.INSTANCE, text).getOrThrow(IllegalStateException::new);
	}

	/**
	 * {@code money}: emeralds (or CobbleDollars when that's the currency), plus one per {@code per_count} of the first
	 * objective's count up to {@code max}, times the village's rank factor unless {@code rank_factor} is false.
	 */
	public record Money_(int emeralds, int perCount, int max, boolean rankFactor) implements Reward {
		static Money_ read(JsonObject json) {
			int emeralds = GsonHelper.getAsInt(json, "emeralds", 0);
			if (emeralds < 0) {
				throw new IllegalArgumentException("'emeralds' below 0");
			}
			return new Money_(emeralds, GsonHelper.getAsInt(json, "per_count", 0), GsonHelper.getAsInt(json, "max", 0),
				GsonHelper.getAsBoolean(json, "rank_factor", true));
		}

		@Override
		public String type() {
			return "money";
		}

		@Override
		public Reward resolve(List<Objectives.Objective> objectives, float rankFactor) {
			int base = emeralds;
			if (perCount > 0 && !objectives.isEmpty()) {
				base += objectives.get(0).need() / perCount;
			}
			if (max > 0) {
				base = Math.min(max, base);
			}
			return new Money_(this.rankFactor ? Math.round(base * rankFactor) : base, 0, 0, false);
		}

		@Override
		public void give(ServerLevel level, BlockPos hall, @Nullable ServerPlayer finisher) {
			if (finisher != null && emeralds > 0) {
				Money.pay(finisher, (long) emeralds * Money.DOLLARS_PER_EMERALD, emeralds);
			}
		}

		@Override
		public JsonObject json() {
			JsonObject o = new JsonObject();
			o.addProperty("type", type());
			o.addProperty("emeralds", emeralds);
			if (perCount > 0) {
				o.addProperty("per_count", perCount);
			}
			if (max > 0) {
				o.addProperty("max", max);
			}
			o.addProperty("rank_factor", rankFactor);
			return o;
		}
	}

	/** {@code item}: {@code count} of an item for whoever finished it. */
	public record ItemReward(ResourceLocation item, int count) implements Reward {
		static ItemReward read(JsonObject json) {
			ResourceLocation item = id(json, "item");
			if (!BuiltInRegistries.ITEM.containsKey(item)) {
				throw new IllegalArgumentException("unknown item '" + item + "'");
			}
			return new ItemReward(item, GsonHelper.getAsInt(json, "count", 1));
		}

		@Override
		public String type() {
			return "item";
		}

		@Override
		public void give(ServerLevel level, BlockPos hall, @Nullable ServerPlayer finisher) {
			Item type = Lookup.value(BuiltInRegistries.ITEM, item);
			int left = count;
			while (left > 0) {
				ItemStack stack = new ItemStack(type, Math.min(left, type.getDefaultMaxStackSize()));
				left -= stack.getCount();
				hand(level, hall, finisher, stack);
			}
		}

		@Override
		public JsonObject json() {
			JsonObject o = new JsonObject();
			o.addProperty("type", type());
			o.addProperty("item", item.toString());
			o.addProperty("count", count);
			return o;
		}
	}

	/** {@code loot_table}: a roll of a loot table for whoever finished it (dropped at the hall if nobody). */
	public record Loot(ResourceLocation table) implements Reward {
		@Override
		public String type() {
			return "loot_table";
		}

		@Override
		public void give(ServerLevel level, BlockPos hall, @Nullable ServerPlayer finisher) {
			LootTable loot = level.getServer().reloadableRegistries().getLootTable(ResourceKey.create(Registries.LOOT_TABLE, table));
			LootParams.Builder params = new LootParams.Builder(level).withParameter(LootContextParams.ORIGIN, Vec3.atCenterOf(hall));
			if (finisher != null) {
				params.withParameter(LootContextParams.THIS_ENTITY, finisher).withLuck(finisher.getLuck());
			}
			for (ItemStack stack : loot.getRandomItems(params.create(LootContextParamSets.GIFT))) {
				hand(level, hall, finisher, stack);
			}
		}

		@Override
		public JsonObject json() {
			JsonObject o = new JsonObject();
			o.addProperty("type", type());
			o.addProperty("table", table.toString());
			return o;
		}
	}

	/** {@code chronicle}: a line in the village's chronicle, of a {@code kind} (its icon). */
	public record ChronicleLine(String kind, Component line) implements Reward {
		@Override
		public String type() {
			return "chronicle";
		}

		@Override
		public void give(ServerLevel level, BlockPos hall, @Nullable ServerPlayer finisher) {
			Chronicle.Kind k;
			try {
				k = Chronicle.Kind.valueOf(kind.toUpperCase(Locale.ROOT));
			} catch (IllegalArgumentException e) {
				k = Chronicle.Kind.QUEST;
			}
			Chronicle.atHall(level, hall, k, line);
		}

		@Override
		public JsonObject json() {
			JsonObject o = new JsonObject();
			o.addProperty("type", type());
			o.addProperty("kind", kind);
			o.add("line", Rewards.json(line));
			return o;
		}
	}

	/** {@code village_mood}: the village's villagers are {@code points} happier for {@code days} days, like after a festival. */
	public record VillageMood(int points, int days, Component reason) implements Reward {
		@Override
		public String type() {
			return "village_mood";
		}

		@Override
		public void give(ServerLevel level, BlockPos hall, @Nullable ServerPlayer finisher) {
			Stories.Data.get(level).addMood(hall, points, Chronicle.day(level) + days, reason);
		}

		@Override
		public JsonObject json() {
			JsonObject o = new JsonObject();
			o.addProperty("type", type());
			o.addProperty("points", points);
			o.addProperty("days", days);
			o.add("reason", Rewards.json(reason));
			return o;
		}
	}

	/** {@code treasury}: emeralds into the village's treasury. */
	public record Treasury(int emeralds) implements Reward {
		@Override
		public String type() {
			return "treasury";
		}

		@Override
		public void give(ServerLevel level, BlockPos hall, @Nullable ServerPlayer finisher) {
			if (level.getBlockEntity(hall) instanceof VillageHallBlockEntity entity) {
				entity.setTreasury(entity.treasury() + emeralds * 100);
				entity.addTreasuryTotal(emeralds * 100L);
			}
		}

		@Override
		public JsonObject json() {
			JsonObject o = new JsonObject();
			o.addProperty("type", type());
			o.addProperty("emeralds", emeralds);
			return o;
		}
	}

	/**
	 * {@code map} (31.3): a map to a place (a structure, a biome or a point, {@link Places}), drawn like an explorer map
	 * with the place marked, for whoever finished it. The place is looked up once, when the quest opens.
	 */
	public record MapReward(Places.Place place) implements Reward {
		@Override
		public String type() {
			return "map";
		}

		@Override
		public void give(ServerLevel level, BlockPos hall, @Nullable ServerPlayer finisher) {
			if (place.located()) {
				hand(level, hall, finisher, Places.map(level, place));
			}
		}

		@Override
		public JsonObject json() {
			JsonObject o = new JsonObject();
			o.addProperty("type", type());
			o.add("place", place.json());
			return o;
		}
	}

	private static void hand(ServerLevel level, BlockPos hall, @Nullable ServerPlayer finisher, ItemStack stack) {
		if (finisher != null && finisher.getInventory().add(stack) && stack.isEmpty()) {
			return;
		}
		if (!stack.isEmpty()) {
			Block.popResource(level, finisher != null ? finisher.blockPosition() : hall.above(), stack);
		}
	}

	private Rewards() {
	}
}
