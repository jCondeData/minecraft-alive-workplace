package io.github.jcondedata.aliveworkplace.wonder;

import io.github.jcondedata.aliveworkplace.AliveWorkplace;
import io.github.jcondedata.aliveworkplace.explore.Explorers;
import io.github.jcondedata.aliveworkplace.mc.Ids;
import io.github.jcondedata.aliveworkplace.mc.Lookup;
import io.github.jcondedata.aliveworkplace.mc.Nbt;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.function.BiConsumer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderSet;
import net.minecraft.core.QuartPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BiomeTags;
import net.minecraft.tags.TagKey;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.ChunkGeneratorStructureState;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructureCheckResult;
import net.minecraft.world.level.levelgen.structure.StructureSet;
import net.minecraft.world.level.levelgen.structure.placement.ConcentricRingsStructurePlacement;
import net.minecraft.world.level.levelgen.structure.placement.RandomSpreadStructurePlacement;
import net.minecraft.world.level.levelgen.structure.placement.StructurePlacement;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.level.saveddata.maps.MapId;
import net.minecraft.world.level.saveddata.maps.MapItemSavedData;
import org.jetbrains.annotations.Nullable;

/**
 * The Observatory's Sky Chart (35.18; its size the owner's change 35.18a): a chart about 6,144 blocks across (384
 * chunks, 12 regions of 32 chunks a side) centred on the hall, marking every structure the Observatory has found.
 *
 * <p>A vanilla map shows at most 2,048 blocks (scale 4, {@link MapItemSavedData#MAX_SCALE}), so the chart is one
 * locked scale-4 map whose 128 pixels each stand for 48 blocks: the mod sketches the land itself (one biome sample a
 * pixel, as vanilla's explorer maps take) and places each marker where a scale-4 map would draw it at a third of its
 * distance. A marker in the last 48 blocks before the chart's edge sits on the edge pixel (vanilla drops markers
 * further out than 63 pixels).
 *
 * <p>The area is 144 regions where the 1,024-block chart had 4, so the search is spread: {@link Search#tick} searches
 * one region of one structure kind (a structure set: villages, shipwrecks...) every {@link #STEP_TICKS} ticks, only
 * while the server's tick has room ({@link #tickHasRoom}), and starts at most one kind an in-game hour. A kind's 144
 * regions take 720 ticks, inside its hour. All its progress (the cursor and what it found) is saved, so a restart
 * picks up at the next region.
 */
public final class SkyChart {
	/** The structures the chart marks (villages, outposts, temples, huts, igloos, monuments, mansions...). */
	public static final TagKey<Structure> MARKED = TagKey.create(Registries.STRUCTURE, AliveWorkplace.id("sky_chart"));
	/** The chart's size when the Observatory's file names none: 384 chunks, 12 regions (owner, 35.18a). */
	public static final int DEFAULT_SIZE = 6_144;
	/** The most a server owner's file may ask for: 24 regions a side (a kind then takes longer than its hour). */
	public static final int MAX_SIZE = 12_288;
	/** A region's side, in chunks (a region file's). */
	public static final int REGION_CHUNKS = 32;
	/** A map's side, in pixels. */
	public static final int PIXELS = 128;
	/** Vanilla keeps a marker only within this many pixels of the map's centre. */
	public static final int EDGE_PIXEL = 63;
	/** One region is searched every this many ticks. */
	public static final int STEP_TICKS = 5;
	/** An in-game hour, in ticks: at most one kind starts in each. */
	public static final int HOUR_TICKS = 1_000;
	/** The tick has room while the server's average tick is under this (four fifths of a tick's 50 ms). */
	public static final long ROOM_NANOS = 40_000_000L;

	private SkyChart() {
	}

	/** The chart size an Observatory's {@code sky_chart} power names ({@link #DEFAULT_SIZE} without one). */
	public static int size(Wonder wonder) {
		for (Wonder.Power p : wonder.powers()) {
			if (p.type().equals("sky_chart") && p.numbers().has("size")) {
				return p.numbers().get("size").getAsInt();
			}
		}
		return DEFAULT_SIZE;
	}

	/** Whether the server's tick has room for a search step. */
	public static boolean tickHasRoom(MinecraftServer server) {
		return server.getAverageTickTimeNanos() < ROOM_NANOS;
	}

	/**
	 * The square a chart covers, in whole chunks around the hall's chunk: its centre is that chunk's corner, so the hall
	 * is within 16 blocks of the chart's middle.
	 */
	public record Area(int centreChunkX, int centreChunkZ, int chunksAcross) {
		/** The chart about {@code size} blocks across around {@code hall}, rounded up to whole regions (one at least). */
		public static Area around(BlockPos hall, int size) {
			int blocks = Mth.clamp(size, REGION_CHUNKS * 16, MAX_SIZE);
			int regions = Mth.positiveCeilDiv(blocks, REGION_CHUNKS * 16);
			return new Area(hall.getX() >> 4, hall.getZ() >> 4, regions * REGION_CHUNKS);
		}

		public int minChunkX() {
			return centreChunkX - chunksAcross / 2;
		}

		public int minChunkZ() {
			return centreChunkZ - chunksAcross / 2;
		}

		public int blocksAcross() {
			return chunksAcross * 16;
		}

		/** The chart's middle (the hall chunk's corner). */
		public int centreX() {
			return centreChunkX * 16;
		}

		public int centreZ() {
			return centreChunkZ * 16;
		}

		public int minX() {
			return minChunkX() * 16;
		}

		public int minZ() {
			return minChunkZ() * 16;
		}

		/** Blocks a pixel stands for: 48 on the 6,144-block chart. */
		public int blocksPerPixel() {
			return blocksAcross() / PIXELS;
		}

		public int regionsAcross() {
			return chunksAcross / REGION_CHUNKS;
		}

		/** Regions the search walks for each kind: 144 on the 6,144-block chart. */
		public int regions() {
			return regionsAcross() * regionsAcross();
		}

		/** The north-west chunk of region {@code index} (row by row from the north-west corner). */
		public ChunkPos regionMin(int index) {
			return new ChunkPos(minChunkX() + (index % regionsAcross()) * REGION_CHUNKS, minChunkZ() + (index / regionsAcross()) * REGION_CHUNKS);
		}

		public boolean contains(BlockPos pos) {
			int cx = (pos.getX() >> 4) - minChunkX();
			int cz = (pos.getZ() >> 4) - minChunkZ();
			return cx >= 0 && cz >= 0 && cx < chunksAcross && cz < chunksAcross;
		}

		/** The region {@code pos} is in, or -1 off the chart. */
		public int regionOf(BlockPos pos) {
			if (!contains(pos)) {
				return -1;
			}
			int rx = ((pos.getX() >> 4) - minChunkX()) / REGION_CHUNKS;
			int rz = ((pos.getZ() >> 4) - minChunkZ()) / REGION_CHUNKS;
			return rz * regionsAcross() + rx;
		}

		/** How far from the chart's middle {@code pos} is drawn, in pixels, kept within vanilla's 63. */
		public float pixelX(BlockPos pos) {
			return Mth.clamp((pos.getX() - centreX()) / (float) blocksPerPixel(), -EDGE_PIXEL, EDGE_PIXEL);
		}

		public float pixelZ(BlockPos pos) {
			return Mth.clamp((pos.getZ() - centreZ()) / (float) blocksPerPixel(), -EDGE_PIXEL, EDGE_PIXEL);
		}

		/**
		 * Where to put {@code pos}'s marker on {@code map} so a vanilla map at its scale draws it at {@code pos}'s pixel on
		 * this chart (vanilla places markers by world position at the map's own scale).
		 */
		public BlockPos markerPos(MapItemSavedData map, BlockPos pos) {
			int scale = 1 << map.scale;
			return new BlockPos(map.centerX + Math.round(pixelX(pos) * scale), pos.getY(), map.centerZ + Math.round(pixelZ(pos) * scale));
		}
	}

	/** A structure the Observatory found: its set (the kind searched), the structure and where its start is. */
	public record Found(ResourceLocation kind, ResourceLocation structure, BlockPos pos) {
		/** The marker's id on the chart, one per structure. */
		public String key() {
			return "sky_" + structure + "_" + pos.getX() + "_" + pos.getZ();
		}
	}

	/** Finds the starts of one kind (a structure set) in a square of chunks; the tests' stub stands in for the world. */
	@FunctionalInterface
	public interface Finder {
		void find(ServerLevel level, ResourceLocation kind, ChunkPos min, int chunks, BiConsumer<ResourceLocation, BlockPos> found);
	}

	/** The structure sets this world places that hold a structure the chart marks, in the world's order. */
	public static List<ResourceLocation> kinds(ServerLevel level) {
		Optional<HolderSet.Named<Structure>> marked = marked(level);
		if (marked.isEmpty() || !level.getServer().getWorldData().worldGenOptions().generateStructures()) {
			return List.of();
		}
		List<ResourceLocation> out = new ArrayList<>();
		for (Holder<StructureSet> set : level.getChunkSource().getGeneratorState().possibleStructureSets()) {
			if (set.value().structures().stream().anyMatch(e -> marked.get().contains(e.structure()))) {
				set.unwrapKey().map(Ids::of).filter(id -> !out.contains(id)).ifPresent(out::add);
			}
		}
		return out;
	}

	private static Optional<HolderSet.Named<Structure>> marked(ServerLevel level) {
		return Lookup.tag(Lookup.registry(level.registryAccess(), Registries.STRUCTURE), MARKED);
	}

	/**
	 * The world's own answer, as {@code /locate} gets it: the set's placement grid gives the chunks a start could be in
	 * (one a cell), and the structure check says whether it is there (from what's saved, or predicted from the biomes
	 * for land nobody has been to). No chunk is loaded or generated.
	 */
	public static final Finder VANILLA = (level, kind, min, chunks, found) -> {
		Optional<HolderSet.Named<Structure>> marked = marked(level);
		if (marked.isEmpty()) {
			return;
		}
		ChunkGeneratorStructureState state = level.getChunkSource().getGeneratorState();
		for (Holder<StructureSet> set : state.possibleStructureSets()) {
			if (!set.unwrapKey().map(k -> Ids.of(k).equals(kind)).orElse(false)) {
				continue;
			}
			StructurePlacement placement = set.value().placement();
			List<Holder<Structure>> wanted = set.value().structures().stream().map(StructureSet.StructureSelectionEntry::structure)
				.filter(marked.get()::contains).toList();
			for (ChunkPos chunk : candidates(state, placement, min, chunks)) {
				for (Holder<Structure> structure : wanted) {
					if (level.structureManager().checkStructurePresence(chunk, structure.value(), placement, false) == StructureCheckResult.START_PRESENT) {
						found.accept(structure.unwrapKey().map(Ids::of).orElse(kind), placement.getLocatePos(chunk));
						break;
					}
				}
			}
		}
	};

	private static List<ChunkPos> candidates(ChunkGeneratorStructureState state, StructurePlacement placement, ChunkPos min, int chunks) {
		List<ChunkPos> out = new ArrayList<>();
		if (placement instanceof RandomSpreadStructurePlacement spread) {
			int s = spread.spacing();
			for (int cz = Math.floorDiv(min.z, s); cz <= Math.floorDiv(min.z + chunks - 1, s); cz++) {
				for (int cx = Math.floorDiv(min.x, s); cx <= Math.floorDiv(min.x + chunks - 1, s); cx++) {
					ChunkPos chunk = spread.getPotentialStructureChunk(state.getLevelSeed(), cx * s, cz * s);
					if (inside(chunk, min, chunks) && spread.isStructureChunk(state, chunk.x, chunk.z)) {
						out.add(chunk);
					}
				}
			}
		} else if (placement instanceof ConcentricRingsStructurePlacement rings) {
			List<ChunkPos> ring = state.getRingPositionsFor(rings);
			if (ring != null) {
				ring.stream().filter(c -> inside(c, min, chunks)).forEach(out::add);
			}
		}
		// Other mods' placement types: not charted (the chart never guesses where they might be).
		return out;
	}

	private static boolean inside(ChunkPos chunk, ChunkPos min, int chunks) {
		return chunk.x >= min.x && chunk.z >= min.z && chunk.x < min.x + chunks && chunk.z < min.z + chunks;
	}

	/**
	 * The chart in hand: a locked scale-4 map centred on the area, the land sketched at 48 blocks a pixel and a marker for
	 * each structure found on it. Locked, so carrying it never redraws it at vanilla's own scale.
	 */
	public static ItemStack chart(ServerLevel level, Area area, Collection<Found> found) {
		MapItemSavedData data = MapItemSavedData.createFresh(area.centreX(), area.centreZ(), (byte) MapItemSavedData.MAX_SCALE,
			false, false, level.dimension()).locked();
		sketch(level, area, data);
		MapId id = level.getFreeMapId();
		level.setMapData(id, data);
		ItemStack map = new ItemStack(Items.FILLED_MAP);
		map.set(DataComponents.MAP_ID, id);
		map.set(DataComponents.ITEM_NAME, Component.translatable("item.aliveworkplace.sky_chart"));
		for (Found f : found) {
			if (area.contains(f.pos())) {
				MapItemSavedData.addTargetDecoration(map, area.markerPos(data, f.pos()), f.key(), Explorers.marker(f.structure().getPath()));
			}
		}
		return map;
	}

	/** The land like a vanilla explorer map's: water blank, a brown shore line, the land in shades of orange. */
	static void sketch(ServerLevel level, Area area, MapItemSavedData data) {
		int bpp = area.blocksPerPixel();
		int y = QuartPos.fromBlock(level.getSeaLevel());
		boolean[] water = new boolean[PIXELS * PIXELS];
		for (int pz = 0; pz < PIXELS; pz++) {
			int z = QuartPos.fromBlock(area.minZ() + pz * bpp + bpp / 2);
			for (int px = 0; px < PIXELS; px++) {
				int x = QuartPos.fromBlock(area.minX() + px * bpp + bpp / 2);
				water[pz * PIXELS + px] = level.getNoiseBiome(x, y, z).is(BiomeTags.WATER_ON_MAP_OUTLINES);
			}
		}
		for (int pz = 0; pz < PIXELS; pz++) {
			for (int px = 0; px < PIXELS; px++) {
				byte colour;
				if (!water[pz * PIXELS + px]) {
					int shade = Math.floorMod((int) (Mth.sin(px * 0.35F + pz * 0.21F) * 2.0F + Mth.sin(pz * 0.29F - px * 0.17F) * 2.0F), 3);
					colour = MapColor.COLOR_ORANGE.getPackedId(shade == 0 ? MapColor.Brightness.LOW : shade == 1 ? MapColor.Brightness.NORMAL : MapColor.Brightness.HIGH);
				} else if (land(water, px - 1, pz) || land(water, px + 1, pz) || land(water, px, pz - 1) || land(water, px, pz + 1)) {
					colour = MapColor.COLOR_BROWN.getPackedId(MapColor.Brightness.LOW);
				} else {
					colour = 0; // blank, as vanilla leaves open water
				}
				data.setColor(px, pz, colour);
			}
		}
	}

	private static boolean land(boolean[] water, int px, int pz) {
		return px >= 0 && pz >= 0 && px < PIXELS && pz < PIXELS && !water[pz * PIXELS + px];
	}

	/**
	 * The week's search and what it has found, saved in the hall ({@code skyChart}). Each kind's results replace the
	 * last week's region by region, so the chart is never empty while a new search runs.
	 */
	public static final class Search {
		private final List<Found> found = new ArrayList<>();
		/** The kind being searched; null: start from the first. */
		@Nullable
		private ResourceLocation kind;
		/** The next region of {@link #kind} to search. */
		private int region;
		/** The in-game hour the last kind was started in. */
		private long kindHour = -1;
		/** Every kind searched this week. */
		private boolean done;
		/** The week ({@code (day - 1) / 7}) this search began in; -1: none yet (and in halls saved before 35.18). */
		private long week = -1;
		/** Regions searched since loading (tests and the cost check). */
		private int steps;

		public List<Found> found() {
			return List.copyOf(found);
		}

		public boolean done() {
			return done;
		}

		@Nullable
		public ResourceLocation kind() {
			return kind;
		}

		public int region() {
			return region;
		}

		public int steps() {
			return steps;
		}

		public long week() {
			return week;
		}

		public void setWeek(long week) {
			this.week = week;
		}

		/** A new week: search every kind again (what was found stays until its region is searched again). */
		public void restart(List<ResourceLocation> kinds) {
			done = false;
			kind = null;
			region = 0;
			found.removeIf(f -> !kinds.contains(f.kind()));
		}

		/** One step when it's due ({@link #STEP_TICKS}), the tick has room and, for a kind's first region, a new hour has begun. */
		public boolean tick(ServerLevel level, Area area, List<ResourceLocation> kinds, Finder finder, long gameTime, boolean room) {
			if (done || kinds.isEmpty() || !room || gameTime % STEP_TICKS != 0) {
				return false;
			}
			int k = kind == null ? -1 : kinds.indexOf(kind);
			if (k < 0 || region >= area.regions()) {
				// Nothing begun, or a kind (or a size) the data no longer has: start that kind again from its first region.
				k = Math.max(k, 0);
				kind = kinds.get(k);
				region = 0;
			}
			if (region == 0) {
				long hour = Math.floorDiv(gameTime, HOUR_TICKS);
				if (hour <= kindHour) {
					return false;
				}
				kindHour = hour;
			}
			step(level, area, finder);
			if (++region >= area.regions()) {
				region = 0;
				if (k + 1 < kinds.size()) {
					kind = kinds.get(k + 1);
				} else {
					kind = null;
					done = true;
				}
			}
			return true;
		}

		private void step(ServerLevel level, Area area, Finder finder) {
			ResourceLocation searching = kind;
			int searched = region;
			found.removeIf(f -> f.kind().equals(searching) && area.regionOf(f.pos()) == searched);
			finder.find(level, searching, area.regionMin(searched), REGION_CHUNKS, (structure, pos) -> {
				Found f = new Found(searching, structure, pos.immutable());
				if (area.regionOf(f.pos()) == searched && !found.contains(f)) {
					found.add(f);
				}
			});
			steps++;
		}

		public CompoundTag save() {
			CompoundTag tag = new CompoundTag();
			ListTag list = new ListTag();
			for (Found f : found) {
				CompoundTag t = new CompoundTag();
				t.putString("kind", f.kind().toString());
				t.putString("structure", f.structure().toString());
				t.putInt("x", f.pos().getX());
				t.putInt("y", f.pos().getY());
				t.putInt("z", f.pos().getZ());
				list.add(t);
			}
			tag.put("found", list);
			if (kind != null) {
				tag.putString("kind", kind.toString());
			}
			tag.putInt("region", region);
			tag.putLong("kindHour", kindHour);
			tag.putBoolean("done", done);
			tag.putLong("week", week);
			return tag;
		}

		/** A saved search; every field has a default, so an empty or old tag reads as a search not yet begun. */
		public static Search load(CompoundTag tag) {
			Search s = new Search();
			ListTag list = Nbt.getList(tag, "found", Tag.TAG_COMPOUND);
			for (int i = 0; i < list.size(); i++) {
				CompoundTag t = Nbt.compoundAt(list, i);
				ResourceLocation kind = ResourceLocation.tryParse(Nbt.getString(t, "kind"));
				ResourceLocation structure = ResourceLocation.tryParse(Nbt.getString(t, "structure"));
				if (kind != null && structure != null) {
					s.found.add(new Found(kind, structure, new BlockPos(Nbt.getInt(t, "x"), Nbt.getInt(t, "y"), Nbt.getInt(t, "z"))));
				}
			}
			String kind = Nbt.getString(tag, "kind");
			s.kind = kind.isEmpty() ? null : ResourceLocation.tryParse(kind);
			s.region = Math.max(0, Nbt.getInt(tag, "region"));
			s.kindHour = Nbt.has(tag, "kindHour", Tag.TAG_LONG) ? Nbt.getLong(tag, "kindHour") : -1;
			s.done = Nbt.getBoolean(tag, "done");
			s.week = Nbt.has(tag, "week", Tag.TAG_LONG) ? Nbt.getLong(tag, "week") : -1;
			return s;
		}
	}
}
