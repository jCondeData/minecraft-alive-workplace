package io.github.jcondedata.aliveworkplace.guard;

import io.github.jcondedata.aliveworkplace.Expansions;
import io.github.jcondedata.aliveworkplace.hall.Chronicle;
import io.github.jcondedata.aliveworkplace.mc.Nbt;
import io.github.jcondedata.aliveworkplace.platform.Platform;
import io.github.jcondedata.aliveworkplace.registry.ModVillagers;
import it.unimi.dsi.fastutil.longs.LongLinkedOpenHashSet;
import it.unimi.dsi.fastutil.longs.LongSet;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.ai.village.poi.PoiManager;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.Property;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.saveddata.SavedData;

/**
 * The raid dens a village knows (ROADMAP 38.2, design note M38): Cobblemon Raid Dens' crystals within {@link #REACH}
 * blocks of a Village Hall. A den is recorded when its chunk loads (the chunk's block entities are looked at, never an
 * area), with its tier, type and whether it's active, read from its block state by property name, so nothing here
 * needs a Raid Dens class. Every {@link #CHECK_EVERY} ticks the dens whose chunks are loaded are read again: a den that
 * changed is updated, one broken or gone is dropped. Without Raid Dens (no den block) or with M38's gate shut, nothing
 * is recorded. The list shows on the village map and the hall's Defence page.
 */
public final class RaidDens {
	/** The den block, by id. */
	public static final ResourceLocation DEN = ResourceLocation.fromNamespaceAndPath("cobblemonraiddens", "raid_crystal_block");
	/** A den this close to a hall is the village's. */
	public static final int REACH = 160;
	/** Known dens are read again this often (10 s). */
	static final int CHECK_EVERY = 200;
	static final List<String> TIERS = List.of("tier_one", "tier_two", "tier_three", "tier_four", "tier_five", "tier_six", "tier_seven");

	/** A known den: where, its tier (1 to 7), its type ("fire"), whether it's active, and the day it was first seen. */
	public record Den(BlockPos pos, int tier, String type, boolean active, long day) {
	}

	/** Dens seen in chunks that loaded, to look at on the level's next tick (a chunk load is no place for POI searches). */
	private static final Map<ResourceKey<Level>, LongSet> PENDING = new HashMap<>();

	private RaidDens() {
	}

	public static void init() {
		Platform.get().onChunkLoad(RaidDens::chunkLoaded);
		Platform.get().onLevelTick(RaidDens::tick);
	}

	/** The den block, when Raid Dens is installed. */
	public static Optional<Block> block() {
		return BuiltInRegistries.BLOCK.getOptional(DEN);
	}

	/** Whether dens are looked for: Raid Dens is installed and M38's gate is open. */
	public static boolean live() {
		return Expansions.isOpen("M38") && block().isPresent();
	}

	/** A chunk loaded: its den crystals wait for the next tick. */
	public static void chunkLoaded(ServerLevel level, LevelChunk chunk) {
		Optional<Block> den = block();
		if (den.isEmpty() || !Expansions.isOpen("M38")) {
			return;
		}
		for (BlockPos pos : List.copyOf(chunk.getBlockEntities().keySet())) {
			if (chunk.getBlockState(pos).is(den.get())) {
				PENDING.computeIfAbsent(level.dimension(), k -> new LongLinkedOpenHashSet()).add(pos.asLong());
			}
		}
	}

	/** The level's tick: the dens seen in loaded chunks are looked at, and every {@link #CHECK_EVERY} ticks the known ones read again. */
	public static void tick(ServerLevel level) {
		LongSet pending = PENDING.remove(level.dimension());
		if (pending != null && live()) {
			pending.forEach((long p) -> seen(level, BlockPos.of(p)));
		}
		if (level.getGameTime() % CHECK_EVERY == 0 && live()) {
			Sites sites = Sites.get(level);
			for (Den den : List.copyOf(sites.dens.values())) {
				check(level, den.pos());
			}
		}
	}

	/** A den crystal at {@code pos} was seen: recorded (once) if a hall is within {@link #REACH}, updated if known. */
	public static void seen(ServerLevel level, BlockPos pos) {
		Sites sites = Sites.get(level);
		if (sites.dens.containsKey(pos.asLong())) {
			check(level, pos);
			return;
		}
		if (!level.isLoaded(pos)) {
			return;
		}
		BlockState state = level.getBlockState(pos);
		if (block().filter(state::is).isEmpty() || hallNear(level, pos).isEmpty()) {
			return;
		}
		sites.dens.put(pos.asLong(), read(state, pos.immutable(), Chronicle.day(level)));
		sites.setDirty();
	}

	/** Reads a known den again if its chunk is loaded: updated, or dropped when the crystal is gone. */
	static void check(ServerLevel level, BlockPos pos) {
		Sites sites = Sites.get(level);
		Den known = sites.dens.get(pos.asLong());
		if (known == null || !level.isLoaded(pos)) {
			return;
		}
		BlockState state = level.getBlockState(pos);
		if (block().filter(state::is).isEmpty()) {
			sites.dens.remove(pos.asLong());
			sites.setDirty();
			return;
		}
		Den now = read(state, known.pos(), known.day());
		if (!now.equals(known)) {
			sites.dens.put(pos.asLong(), now);
			sites.setDirty();
		}
	}

	/** The dens {@code hall}'s village knows, nearest first (those in loaded chunks read again first). */
	public static List<Den> near(ServerLevel level, BlockPos hall) {
		if (!live()) {
			return List.of();
		}
		Sites sites = Sites.get(level);
		List<Den> out = new ArrayList<>();
		for (Den den : List.copyOf(sites.dens.values())) {
			if (den.pos().distSqr(hall) <= (double) REACH * REACH) {
				check(level, den.pos());
				Den now = sites.dens.get(den.pos().asLong());
				if (now != null) {
					out.add(now);
				}
			}
		}
		out.sort(Comparator.comparingDouble(d -> d.pos().distSqr(hall)));
		return out;
	}

	/** "Raid den, tier 3, Fire, active". */
	public static Component describe(Den den) {
		return Component.translatable("screen.aliveworkplace.defence.raid_den", den.tier(), typeName(den.type()),
			Component.translatable(den.active() ? "screen.aliveworkplace.defence.raid_den_active" : "screen.aliveworkplace.defence.raid_den_resting"));
	}

	/** A Pokémon type's name: Cobblemon's own word, or the id written nicely. */
	public static Component typeName(String type) {
		String plain = type.isEmpty() ? "?" : Character.toUpperCase(type.charAt(0)) + type.substring(1);
		return Component.translatableWithFallback("cobblemon.type." + type, plain);
	}

	static Optional<BlockPos> hallNear(ServerLevel level, BlockPos pos) {
		return level.getPoiManager().findClosest(h -> h.is(ModVillagers.VILLAGE_HALL_POI), pos, REACH, PoiManager.Occupancy.ANY);
	}

	/** The den at {@code pos} as its block state says: properties read by name, missing ones as "not known". */
	static Den read(BlockState state, BlockPos pos, long day) {
		String tier = value(state, "raid_tier");
		int number = TIERS.indexOf(tier) + 1;
		String active = value(state, "is_active");
		return new Den(pos, number, value(state, "raid_type").toLowerCase(Locale.ROOT), !"false".equals(active), day);
	}

	/** A block state's property by name, as its saved word ("tier_three", "fire", "true"), or "" when it has none. */
	static String value(BlockState state, String name) {
		Property<?> property = state.getBlock().getStateDefinition().getProperty(name);
		return property == null ? "" : named(state, property);
	}

	private static <T extends Comparable<T>> String named(BlockState state, Property<T> property) {
		return property.getName(state.getValue(property));
	}

	/** The known dens of a level, saved with it ({@code aliveworkplace_raid_dens}). */
	public static final class Sites extends SavedData {
		private static final String NAME = "aliveworkplace_raid_dens";
		final Map<Long, Den> dens = new LinkedHashMap<>();

		/** The known dens, by place. */
		public java.util.Collection<Den> dens() {
			return java.util.Collections.unmodifiableCollection(dens.values());
		}

		public static Sites get(ServerLevel level) {
			return level.getDataStorage().computeIfAbsent(new SavedData.Factory<>(Sites::new, Sites::load, null), NAME);
		}

		@Override
		public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
			ListTag list = new ListTag();
			for (Den den : dens.values()) {
				CompoundTag d = new CompoundTag();
				d.putLong("pos", den.pos().asLong());
				d.putInt("tier", den.tier());
				d.putString("type", den.type());
				d.putBoolean("active", den.active());
				d.putLong("firstSeen", den.day());
				list.add(d);
			}
			tag.put("dens", list);
			return tag;
		}

		public static Sites load(CompoundTag tag, HolderLookup.Provider registries) {
			Sites sites = new Sites();
			ListTag list = Nbt.getList(tag, "dens", Tag.TAG_COMPOUND);
			for (int i = 0; i < list.size(); i++) {
				CompoundTag d = Nbt.compoundAt(list, i);
				BlockPos pos = BlockPos.of(Nbt.getLong(d, "pos"));
				boolean active = !Nbt.has(d, "active", Tag.TAG_BYTE) || Nbt.getBoolean(d, "active");
				sites.dens.put(pos.asLong(), new Den(pos, Nbt.getInt(d, "tier"), Nbt.getString(d, "type"), active, Nbt.getLong(d, "firstSeen")));
			}
			return sites;
		}
	}
}
