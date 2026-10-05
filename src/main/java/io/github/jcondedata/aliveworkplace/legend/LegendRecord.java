package io.github.jcondedata.aliveworkplace.legend;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.github.jcondedata.aliveworkplace.AliveWorkplace;
import io.github.jcondedata.aliveworkplace.grave.GraveBlockEntity;
import io.github.jcondedata.aliveworkplace.hall.Chronicle;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Predicate;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.UUIDUtil;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.saveddata.SavedData;
import org.jetbrains.annotations.Nullable;

/**
 * The server's record of Legends (29.3), saved with the overworld as {@code aliveworkplace_legends}: every Legend that
 * has settled anywhere, with their villager, dimension, hall, rarity, the day they settled and the day they fell. A
 * Legend holds their slot (for {@link Legends#canCome}) until they fall: alive, as a zombie villager, or dead in a grave;
 * dead with no grave, or with their grave broken, they fall {@link #FALL_DAYS} days later.
 */
public final class LegendRecord extends SavedData {
	public static final String NAME = "aliveworkplace_legends";
	/** Days a Legend dead with no grave keeps their slot. */
	public static final int FALL_DAYS = 7;

	/**
	 * One Legend: which, their villager (a zombie villager while {@code zombie}), dimension, hall, rarity, the name they
	 * went by, the day they settled, the day they fell (-1: they haven't), the day the slot's clock began (-1: alive or in
	 * a grave) and their grave. Every field but the first two has a default.
	 */
	public record Entry(ResourceLocation id, UUID villager, String dimension, Optional<BlockPos> hall, Rarity rarity, String name,
						long settled, long fell, long gone, Optional<BlockPos> grave, boolean zombie) {
		static final Codec<Rarity> RARITY = Codec.STRING.xmap(s -> {
			try {
				return Rarity.parse(s);
			} catch (IllegalArgumentException e) {
				return Rarity.RARE;
			}
		}, Rarity::key);
		public static final Codec<Entry> CODEC = RecordCodecBuilder.create(i -> i.group(
			ResourceLocation.CODEC.fieldOf("id").forGetter(Entry::id),
			UUIDUtil.CODEC.fieldOf("villager").forGetter(Entry::villager),
			Codec.STRING.optionalFieldOf("dimension", "minecraft:overworld").forGetter(Entry::dimension),
			BlockPos.CODEC.optionalFieldOf("hall").forGetter(Entry::hall),
			RARITY.optionalFieldOf("rarity", Rarity.RARE).forGetter(Entry::rarity),
			Codec.STRING.optionalFieldOf("name", "").forGetter(Entry::name),
			Codec.LONG.optionalFieldOf("settled", 0L).forGetter(Entry::settled),
			Codec.LONG.optionalFieldOf("fell", -1L).forGetter(Entry::fell),
			Codec.LONG.optionalFieldOf("gone", -1L).forGetter(Entry::gone),
			BlockPos.CODEC.optionalFieldOf("grave").forGetter(Entry::grave),
			Codec.BOOL.optionalFieldOf("zombie", false).forGetter(Entry::zombie)
		).apply(i, Entry::new));

		/** Whether they still hold their slot. */
		public boolean holds() {
			return fell < 0;
		}

		public boolean in(String dim, @Nullable BlockPos village) {
			return dimension.equals(dim) && hall.equals(Optional.ofNullable(village));
		}

		Entry withVillager(UUID uuid, boolean asZombie) {
			return new Entry(id, uuid, dimension, hall, rarity, name, settled, fell, gone, grave, asZombie);
		}

		Entry withHall(Optional<BlockPos> to) {
			return new Entry(id, villager, dimension, to, rarity, name, settled, fell, gone, grave, zombie);
		}

		Entry dead(long day, Optional<BlockPos> in) {
			return new Entry(id, villager, dimension, hall, rarity, name, settled, fell, in.isPresent() ? -1 : day, in, zombie);
		}

		Entry back(UUID uuid) {
			return new Entry(id, uuid, dimension, hall, rarity, name, settled, -1, -1, Optional.empty(), false);
		}

		Entry fallen(long day) {
			return new Entry(id, villager, dimension, hall, rarity, name, settled, day, gone, Optional.empty(), zombie);
		}
	}

	private final Map<UUID, Entry> entries = new LinkedHashMap<>();
	/** The structure starts a found Legend's camp was placed at (29.9), each used once: see {@link LegendSites#key}. */
	private final java.util.Set<String> usedSites = new java.util.LinkedHashSet<>();
	/** Found Legends waiting at their camp, or freed and on their way to the hall (29.9), by villager. */
	private final Map<UUID, Captive> captives = new LinkedHashMap<>();

	/**
	 * A found Legend (29.9): their villager at the camp, which Legend, the site ({@code ruined_portal}, {@code outpost},
	 * {@code shipwreck}), the hall they'll go to, the dimension, the camp's corners, and the day and game time they were
	 * freed (-1: not yet). Every field but the first two has a default.
	 */
	public record Captive(UUID villager, ResourceLocation id, String site, Optional<BlockPos> hall, String dimension, BlockPos min, BlockPos max,
						  long freedDay, long freedAt) {
		public static final Codec<Captive> CODEC = RecordCodecBuilder.create(i -> i.group(
			UUIDUtil.CODEC.fieldOf("villager").forGetter(Captive::villager),
			ResourceLocation.CODEC.fieldOf("id").forGetter(Captive::id),
			Codec.STRING.optionalFieldOf("site", "").forGetter(Captive::site),
			BlockPos.CODEC.optionalFieldOf("hall").forGetter(Captive::hall),
			Codec.STRING.optionalFieldOf("dimension", "minecraft:overworld").forGetter(Captive::dimension),
			BlockPos.CODEC.optionalFieldOf("min", BlockPos.ZERO).forGetter(Captive::min),
			BlockPos.CODEC.optionalFieldOf("max", BlockPos.ZERO).forGetter(Captive::max),
			Codec.LONG.optionalFieldOf("freed_day", -1L).forGetter(Captive::freedDay),
			Codec.LONG.optionalFieldOf("freed_at", -1L).forGetter(Captive::freedAt)
		).apply(i, Captive::new));

		public boolean freed() {
			return freedDay >= 0;
		}

		/** Whether {@code pos} is inside the camp (the cage's bars, for the prisoner). */
		public boolean holds(BlockPos pos) {
			return pos.getX() >= min.getX() && pos.getX() <= max.getX() && pos.getY() >= min.getY() && pos.getY() <= max.getY()
				&& pos.getZ() >= min.getZ() && pos.getZ() <= max.getZ();
		}
	}

	/** Whether the structure start {@code key} has had its found Legend. */
	public boolean siteUsed(String key) {
		return usedSites.contains(key);
	}

	/** Marks the structure start {@code key} as used (its camp is placed). */
	public void useSite(String key) {
		if (usedSites.add(key)) {
			setDirty();
		}
	}

	/** Forgets a used structure start (tests). */
	public void forgetSite(String key) {
		if (usedSites.remove(key)) {
			setDirty();
		}
	}

	public List<Captive> captives() {
		return List.copyOf(captives.values());
	}

	public Optional<Captive> captive(UUID villager) {
		return Optional.ofNullable(captives.get(villager));
	}

	public void putCaptive(Captive c) {
		captives.put(c.villager(), c);
		setDirty();
	}

	public void forgetCaptive(UUID villager) {
		if (captives.remove(villager) != null) {
			setDirty();
		}
	}

	public static LegendRecord get(MinecraftServer server) {
		return server.overworld().getDataStorage().computeIfAbsent(new SavedData.Factory<>(LegendRecord::new, LegendRecord::load, null), NAME);
	}

	public static LegendRecord get(ServerLevel level) {
		return get(level.getServer());
	}

	/** Every Legend on record, fallen ones too, in the order they settled. */
	public List<Entry> entries() {
		return List.copyOf(entries.values());
	}

	public Optional<Entry> entry(UUID villager) {
		return Optional.ofNullable(entries.get(villager));
	}

	/** The Legends holding a slot that match {@code test}. */
	public long holding(Predicate<Entry> test) {
		return entries.values().stream().filter(Entry::holds).filter(test).count();
	}

	/** A Legend has settled (again, for one already on record: their entry starts over). */
	public Entry settled(ResourceLocation id, UUID villager, ResourceKey<Level> dimension, Optional<BlockPos> hall, Rarity rarity, String name, long day) {
		Entry e = new Entry(id, villager, dimension.location().toString(), hall, rarity, name, day, -1, -1, Optional.empty(), false);
		entries.put(villager, e);
		setDirty();
		return e;
	}

	/** Takes a villager off the record (no longer a Legend, or a test's leftover). */
	public void forget(UUID villager) {
		if (entries.remove(villager) != null) {
			setDirty();
		}
	}

	/** A Legend died on {@code day}: in a grave at {@code grave}, or with none (their slot's clock starts). */
	public void died(UUID villager, long day, Optional<BlockPos> grave) {
		Entry e = entries.get(villager);
		if (e != null && e.holds()) {
			entries.put(villager, e.dead(day, grave));
			setDirty();
		}
	}

	/** The Legend once {@code before} is now {@code after}: brought back from their grave, or cured. */
	public void back(UUID before, UUID after) {
		Entry e = entries.remove(before);
		if (e != null) {
			entries.put(after, e.back(after));
			setDirty();
		}
	}

	/** The Legend {@code before} was turned into the zombie villager {@code zombie}: they keep their slot. */
	public void turned(UUID before, UUID zombie) {
		Entry e = entries.remove(before);
		if (e != null) {
			entries.put(zombie, e.withVillager(zombie, true));
			setDirty();
		}
	}

	/** The Legend joined another hall. */
	public void moved(UUID villager, BlockPos hall) {
		Entry e = entries.get(villager);
		if (e != null && !e.hall().equals(Optional.of(hall))) {
			entries.put(villager, e.withHall(Optional.of(hall)));
			setDirty();
		}
	}

	/**
	 * Once a day ({@code today} as {@link Chronicle#day} counts): a grave found broken starts its Legend's clock; a
	 * Legend whose clock has run {@link #FALL_DAYS} days falls, and their village's chronicle says so. Returns those who fell.
	 */
	public List<Entry> newDay(MinecraftServer server, long today) {
		List<Entry> fell = new ArrayList<>();
		for (Entry e : List.copyOf(entries.values())) {
			if (!e.holds()) {
				continue;
			}
			ServerLevel level = level(server, e);
			Entry now = e;
			if (e.grave().isPresent() && level != null && level.isLoaded(e.grave().get())
				&& !(level.getBlockEntity(e.grave().get()) instanceof GraveBlockEntity grave && !grave.villager().isEmpty())) {
				now = new Entry(e.id(), e.villager(), e.dimension(), e.hall(), e.rarity(), e.name(), e.settled(), -1, today, Optional.empty(), e.zombie());
			}
			if (now.grave().isEmpty() && now.gone() >= 0 && today - now.gone() >= FALL_DAYS) {
				now = now.fallen(today);
				fell.add(now);
				if (level != null && now.hall().isPresent()) {
					Component title = Legends.get(now.id()).map(Legend::titleText).orElse(Component.literal(now.id().toString()));
					Chronicle.record(level, now.hall().get(), Chronicle.Kind.LEGEND,
						Component.translatable("chronicle.aliveworkplace.legend.fallen", Component.literal(now.name()), title));
				}
				AliveWorkplace.LOG.info("Legend {} ({}) has fallen; their slot is free", now.id(), now.name());
			}
			if (now != e) {
				entries.put(e.villager(), now);
				setDirty();
			}
		}
		return fell;
	}

	@Nullable
	private static ServerLevel level(MinecraftServer server, Entry e) {
		ResourceLocation dim = ResourceLocation.tryParse(e.dimension());
		return dim == null ? null : server.getLevel(ResourceKey.create(Registries.DIMENSION, dim));
	}

	@Override
	public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
		Entry.CODEC.listOf().encodeStart(NbtOps.INSTANCE, List.copyOf(entries.values())).result().ifPresent(t -> tag.put("legends", t));
		if (!usedSites.isEmpty()) {
			Codec.STRING.listOf().encodeStart(NbtOps.INSTANCE, List.copyOf(usedSites)).result().ifPresent(t -> tag.put("found_sites", t));
		}
		if (!captives.isEmpty()) {
			Captive.CODEC.listOf().encodeStart(NbtOps.INSTANCE, List.copyOf(captives.values())).result().ifPresent(t -> tag.put("captives", t));
		}
		return tag;
	}

	public static LegendRecord load(CompoundTag tag, HolderLookup.Provider registries) {
		LegendRecord record = new LegendRecord();
		Tag list = tag.get("legends");
		if (list != null) {
			Entry.CODEC.listOf().parse(NbtOps.INSTANCE, list)
				.resultOrPartial(err -> AliveWorkplace.LOG.warn("Legend record: {}", err))
				.ifPresent(all -> all.forEach(e -> record.entries.put(e.villager(), e)));
		}
		// Both absent in saves from before 29.9.
		Tag sites = tag.get("found_sites");
		if (sites != null) {
			Codec.STRING.listOf().parse(NbtOps.INSTANCE, sites).result().ifPresent(record.usedSites::addAll);
		}
		Tag caught = tag.get("captives");
		if (caught != null) {
			Captive.CODEC.listOf().parse(NbtOps.INSTANCE, caught)
				.resultOrPartial(err -> AliveWorkplace.LOG.warn("Legend record, captives: {}", err))
				.ifPresent(all -> all.forEach(c -> record.captives.put(c.villager(), c)));
		}
		return record;
	}
}
