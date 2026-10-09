package io.github.jcondedata.aliveworkplace.trade;

import io.github.jcondedata.aliveworkplace.mc.Nbt;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;

/**
 * A village's trade goods as its hall's last daily count left them (ROADMAP 33.2): up to 3 goods it's known for, up to 3
 * it's short of, each good's price, the day prices last moved, and demand raised by events. Kept on the village's
 * {@code Caravans.Data} entry (keys {@code knownFor}, {@code shortOf}, {@code prices}, {@code priceDay}, {@code demand}),
 * so a village that isn't loaded keeps its last prices. An entry saved before 1.7 has none of them: {@link #EMPTY}.
 */
public record Market(List<ResourceLocation> knownFor, List<ResourceLocation> shortOf, Map<ResourceLocation, Price> prices, long priceDay,
					 List<Demand> demand) {
	public static final Market EMPTY = new Market(List.of(), List.of(), Map.of(), -1, List.of());

	/** A good's price: {@code cents} a bundle today, {@code yesterday}'s, and today's 2% steps from trades (33.5; reset at dawn). */
	public record Price(int cents, int yesterday, int nudge) {
	}

	/** {@code amount} more demand for {@code good} from day {@code from} to day {@code until}, both counted. */
	public record Demand(ResourceLocation good, int amount, long from, long until) {
		public boolean on(long day) {
			return from <= day && day <= until;
		}
	}

	public Market {
		knownFor = List.copyOf(knownFor);
		shortOf = List.copyOf(shortOf);
		prices = java.util.Collections.unmodifiableMap(new LinkedHashMap<>(prices));
		demand = List.copyOf(demand);
	}

	public Market withDemand(List<Demand> demand) {
		return new Market(knownFor, shortOf, prices, priceDay, demand);
	}

	/** Whether nothing has been worked out or raised here yet. */
	public boolean isEmpty() {
		return knownFor.isEmpty() && shortOf.isEmpty() && prices.isEmpty() && priceDay < 0 && demand.isEmpty();
	}

	/** Writes the keys onto a village entry (nothing for an empty market, so 1.6's entries stay as they were). */
	public void save(CompoundTag t) {
		if (isEmpty()) {
			return;
		}
		t.put("knownFor", ids(knownFor));
		t.put("shortOf", ids(shortOf));
		ListTag list = new ListTag();
		prices.forEach((good, p) -> {
			CompoundTag pt = new CompoundTag();
			pt.putString("good", good.toString());
			pt.putInt("cents", p.cents());
			pt.putInt("yesterday", p.yesterday());
			pt.putInt("nudge", p.nudge());
			list.add(pt);
		});
		t.put("prices", list);
		t.putLong("priceDay", priceDay);
		ListTag dl = new ListTag();
		for (Demand d : demand) {
			CompoundTag dt = new CompoundTag();
			dt.putString("good", d.good().toString());
			dt.putInt("amount", d.amount());
			dt.putLong("from", d.from());
			dt.putLong("until", d.until());
			dl.add(dt);
		}
		t.put("demand", dl);
	}

	/** Reads the keys off a village entry; an entry without them gives {@link #EMPTY}. */
	public static Market load(CompoundTag t) {
		List<ResourceLocation> known = ids(Nbt.getList(t, "knownFor", Tag.TAG_STRING));
		List<ResourceLocation> shortOf = ids(Nbt.getList(t, "shortOf", Tag.TAG_STRING));
		Map<ResourceLocation, Price> prices = new LinkedHashMap<>();
		ListTag pl = Nbt.getList(t, "prices", Tag.TAG_COMPOUND);
		for (int i = 0; i < pl.size(); i++) {
			CompoundTag pt = Nbt.compoundAt(pl, i);
			ResourceLocation good = ResourceLocation.tryParse(Nbt.getString(pt, "good"));
			if (good != null) {
				prices.put(good, new Price(Nbt.getInt(pt, "cents"), Nbt.getInt(pt, "yesterday"), Nbt.getInt(pt, "nudge")));
			}
		}
		long priceDay = Nbt.has(t, "priceDay", Tag.TAG_LONG) ? Nbt.getLong(t, "priceDay") : -1;
		List<Demand> demand = new ArrayList<>();
		ListTag dl = Nbt.getList(t, "demand", Tag.TAG_COMPOUND);
		for (int i = 0; i < dl.size(); i++) {
			CompoundTag dt = Nbt.compoundAt(dl, i);
			ResourceLocation good = ResourceLocation.tryParse(Nbt.getString(dt, "good"));
			if (good != null) {
				demand.add(new Demand(good, Nbt.getInt(dt, "amount"), Nbt.getLong(dt, "from"), Nbt.getLong(dt, "until")));
			}
		}
		Market m = new Market(known, shortOf, prices, priceDay, demand);
		return m.isEmpty() ? EMPTY : m;
	}

	private static ListTag ids(List<ResourceLocation> ids) {
		ListTag list = new ListTag();
		ids.forEach(id -> list.add(StringTag.valueOf(id.toString())));
		return list;
	}

	private static List<ResourceLocation> ids(ListTag list) {
		List<ResourceLocation> out = new ArrayList<>();
		for (int i = 0; i < list.size(); i++) {
			ResourceLocation id = ResourceLocation.tryParse(Nbt.stringAt(list, i));
			if (id != null) {
				out.add(id);
			}
		}
		return out;
	}
}
