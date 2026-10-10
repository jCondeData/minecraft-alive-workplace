package io.github.jcondedata.aliveworkplace.realm;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.github.jcondedata.aliveworkplace.AliveWorkplace;
import io.github.jcondedata.aliveworkplace.mc.Nbt;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentSerialization;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;
import org.jetbrains.annotations.Nullable;

/**
 * The server-wide saved file {@code aliveworkplace_realms} (design note M33, "Save data"), kept in the overworld's data
 * storage so it is one file for the server; villages are named by dimension and hall position. ROADMAP 33.8 starts it
 * with the colonies' part: the orders on the road, the colonies founded, and each mother village's count and last
 * colony day. Everything is empty by default, so an older world loads unchanged; a part that can't be read is left
 * empty and logged, never a crash.
 */
public final class RealmData extends SavedData {
	private static final String NAME = "aliveworkplace_realms";

	/** A colony order's states, in the order they pass (33.9). */
	public static final String GATHERING = "gathering";
	public static final String ON_ROAD = "on_road";

	/**
	 * A colony order: the mother village, the spot, the colony's name (none: one is made up on arrival), where it has got
	 * to, when the settlers leave and arrive (game time; 0 until known) and what was paid, in hundredths of an emerald,
	 * for a refund. 33.9 adds the settlers and supplies, each with an empty default.
	 */
	public record Order(GlobalPos mother, BlockPos spot, Optional<Component> name, String state, long leaves, long arrives, int cost) {
		public static final Codec<Order> CODEC = RecordCodecBuilder.create(i -> i.group(
			GlobalPos.CODEC.fieldOf("mother").forGetter(Order::mother),
			BlockPos.CODEC.fieldOf("spot").forGetter(Order::spot),
			ComponentSerialization.CODEC.optionalFieldOf("name").forGetter(Order::name),
			Codec.STRING.optionalFieldOf("state", GATHERING).forGetter(Order::state),
			Codec.LONG.optionalFieldOf("leaves", 0L).forGetter(Order::leaves),
			Codec.LONG.optionalFieldOf("arrives", 0L).forGetter(Order::arrives),
			Codec.INT.optionalFieldOf("cost", 0).forGetter(Order::cost)
		).apply(i, Order::new));
	}

	/** A colony founded: its mother village, its own hall and the day. */
	public record Founded(GlobalPos mother, GlobalPos hall, long day) {
		public static final Codec<Founded> CODEC = RecordCodecBuilder.create(i -> i.group(
			GlobalPos.CODEC.fieldOf("mother").forGetter(Founded::mother),
			GlobalPos.CODEC.fieldOf("hall").forGetter(Founded::hall),
			Codec.LONG.optionalFieldOf("day", 0L).forGetter(Founded::day)
		).apply(i, Founded::new));
	}

	/** A mother village's count: the day of its last colony and how many it has founded (kept when a colony's hall is lost). */
	public record Cooldown(GlobalPos mother, long lastDay, int founded) {
		public static final Codec<Cooldown> CODEC = RecordCodecBuilder.create(i -> i.group(
			GlobalPos.CODEC.fieldOf("mother").forGetter(Cooldown::mother),
			Codec.LONG.optionalFieldOf("lastDay", -1L).forGetter(Cooldown::lastDay),
			Codec.INT.optionalFieldOf("founded", 0).forGetter(Cooldown::founded)
		).apply(i, Cooldown::new));
	}

	private final List<Order> orders = new ArrayList<>();
	private final List<Founded> founded = new ArrayList<>();
	private final List<Cooldown> cooldowns = new ArrayList<>();

	public static RealmData get(MinecraftServer server) {
		return server.overworld().getDataStorage().computeIfAbsent(new SavedData.Factory<>(RealmData::new, RealmData::load, null), NAME);
	}

	/** The colony orders not yet arrived, oldest first. */
	public List<Order> orders() {
		return List.copyOf(orders);
	}

	/** {@code mother}'s order on the road (or getting ready), or null. */
	@Nullable
	public Order orderOf(GlobalPos mother) {
		for (Order o : orders) {
			if (o.mother().equals(mother)) {
				return o;
			}
		}
		return null;
	}

	/** Adds {@code order}, in place of any other of the same mother village (a village has one at a time). */
	public void putOrder(Order order) {
		orders.removeIf(o -> o.mother().equals(order.mother()));
		orders.add(order);
		setDirty();
	}

	/** Takes {@code mother}'s order off the list (arrived or called off); the order, or null if it had none. */
	@Nullable
	public Order removeOrder(GlobalPos mother) {
		Order order = orderOf(mother);
		if (order != null) {
			orders.remove(order);
			setDirty();
		}
		return order;
	}

	/** Every colony founded, oldest first. */
	public List<Founded> founded() {
		return List.copyOf(founded);
	}

	/** The colonies {@code mother} founded, oldest first. */
	public List<Founded> foundedBy(GlobalPos mother) {
		return founded.stream().filter(f -> f.mother().equals(mother)).toList();
	}

	/** Notes a colony of {@code mother} founded at {@code hall} on {@code day}: on the list, and on the mother's count. */
	public void recordFounded(GlobalPos mother, GlobalPos hall, long day) {
		founded.add(new Founded(mother, hall, day));
		Cooldown was = cooldown(mother);
		cooldowns.removeIf(c -> c.mother().equals(mother));
		cooldowns.add(new Cooldown(mother, was.founded() == 0 ? day : Math.max(day, was.lastDay()), was.founded() + 1));
		setDirty();
	}

	/** {@code mother}'s count (last day -1 and none founded for a village that never sent a colony). */
	public Cooldown cooldown(GlobalPos mother) {
		for (Cooldown c : cooldowns) {
			if (c.mother().equals(mother)) {
				return c;
			}
		}
		return new Cooldown(mother, -1, 0);
	}

	/** Forgets everything about colonies (tests). */
	public void clearColonies() {
		orders.clear();
		founded.clear();
		cooldowns.clear();
		setDirty();
	}

	@Override
	public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
		var ops = registries.createSerializationContext(NbtOps.INSTANCE);
		CompoundTag colonies = new CompoundTag();
		Order.CODEC.listOf().encodeStart(ops, orders).result().ifPresent(t -> colonies.put("orders", t));
		Founded.CODEC.listOf().encodeStart(ops, founded).result().ifPresent(t -> colonies.put("founded", t));
		tag.put("colonies", colonies);
		Cooldown.CODEC.listOf().encodeStart(ops, cooldowns).result().ifPresent(t -> tag.put("cooldowns", t));
		return tag;
	}

	public static RealmData load(CompoundTag tag, HolderLookup.Provider registries) {
		var ops = registries.createSerializationContext(NbtOps.INSTANCE);
		RealmData data = new RealmData();
		CompoundTag colonies = Nbt.getCompound(tag, "colonies");
		read(Order.CODEC, colonies.get("orders"), ops, data.orders, "colony orders");
		read(Founded.CODEC, colonies.get("founded"), ops, data.founded, "founded colonies");
		read(Cooldown.CODEC, tag.get("cooldowns"), ops, data.cooldowns, "colony cooldowns");
		return data;
	}

	private static <T> void read(Codec<T> codec, @Nullable Tag tag, com.mojang.serialization.DynamicOps<Tag> ops, List<T> into, String what) {
		if (tag == null) {
			return;
		}
		codec.listOf().parse(ops, tag).resultOrPartial(e -> AliveWorkplace.LOG.warn("Couldn't read the {} in {}: {}", what, NAME, e))
			.ifPresent(into::addAll);
	}
}
