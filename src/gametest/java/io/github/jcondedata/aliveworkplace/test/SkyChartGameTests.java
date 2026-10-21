package io.github.jcondedata.aliveworkplace.test;

import io.github.jcondedata.aliveworkplace.AliveWorkplace;
import io.github.jcondedata.aliveworkplace.wonder.SkyChart;
import io.github.jcondedata.aliveworkplace.wonder.Wonder;
import io.github.jcondedata.aliveworkplace.wonder.Wonders;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.core.QuartPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.BiomeTags;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.MapItem;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.saveddata.maps.MapDecoration;
import net.minecraft.world.level.saveddata.maps.MapDecorationTypes;
import net.minecraft.world.level.saveddata.maps.MapItemSavedData;

/**
 * The Sky Chart at the owner's size (35.18a): about 6,144 blocks across (384 chunks, 12 regions), its edge, its search
 * spread over ticks and hours, saved and reloaded mid-search, and what a step and the chart cost.
 */
public class SkyChartGameTests implements FabricGameTest {
	private static final ResourceLocation VILLAGES = ResourceLocation.withDefaultNamespace("villages");
	private static final ResourceLocation SHIPWRECKS = ResourceLocation.withDefaultNamespace("shipwrecks");
	private static final ResourceLocation PORTALS = ResourceLocation.withDefaultNamespace("ruined_portals");
	private static final ResourceLocation PLAINS = ResourceLocation.withDefaultNamespace("village_plains");
	private static final ResourceLocation WRECK = ResourceLocation.withDefaultNamespace("shipwreck");
	private static final ResourceLocation PORTAL = ResourceLocation.withDefaultNamespace("ruined_portal");
	private static final List<ResourceLocation> KINDS = List.of(VILLAGES, SHIPWRECKS, PORTALS);
	/** The hall, far from the test area so nothing else is near (the chart reads no blocks). */
	private static final BlockPos HALL = new BlockPos(100_008, 70, -40_008);

	/** A stand-in for the world: these starts, each of its kind, reported only for the square asked about. */
	record Stub(List<SkyChart.Found> starts) implements SkyChart.Finder {
		@Override
		public void find(ServerLevel level, ResourceLocation kind, ChunkPos min, int chunks, java.util.function.BiConsumer<ResourceLocation, BlockPos> found) {
			for (SkyChart.Found f : starts) {
				int cx = f.pos().getX() >> 4;
				int cz = f.pos().getZ() >> 4;
				if (f.kind().equals(kind) && cx >= min.x && cz >= min.z && cx < min.x + chunks && cz < min.z + chunks) {
					found.accept(f.structure(), f.pos());
				}
			}
		}
	}

	private static SkyChart.Area area() {
		return SkyChart.Area.around(HALL, SkyChart.DEFAULT_SIZE);
	}

	/** Runs the search tick by tick from {@code from} to {@code to} (exclusive), checking no tick searches two regions. */
	private static void run(GameTestHelper helper, SkyChart.Search search, SkyChart.Finder finder, long from, long to) {
		for (long t = from; t < to; t++) {
			int before = search.steps();
			search.tick(helper.getLevel(), area(), KINDS, finder, t, true);
			helper.assertTrue(search.steps() - before <= 1, "tick " + t + " searched " + (search.steps() - before) + " regions");
		}
	}

	//$ gametest 'FabricGameTest.EMPTY_STRUCTURE'
	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
	public void theObservatoryChartsSixThousandBlocks(GameTestHelper helper) {
		Wonder observatory = Wonders.read(AliveWorkplace.id("observatory"), WonderBlueprintGameTests.file(helper.getLevel().getServer(), "observatory"));
		helper.assertTrue(observatory != null, "the Observatory's file is skipped");
		int size = SkyChart.size(observatory);
		helper.assertTrue(size == 6_144, "the Observatory's Sky Chart is " + size + " blocks across, not 6,144");
		SkyChart.Area area = SkyChart.Area.around(HALL, size);
		helper.assertTrue(area.chunksAcross() == 384, area.chunksAcross() + " chunks across, not 384");
		helper.assertTrue(area.regionsAcross() == 12, area.regionsAcross() + " regions across, not 12");
		helper.assertTrue(area.regions() == 144, area.regions() + " regions searched a kind, not 144");
		helper.assertTrue(area.blocksAcross() == 6_144 && area.blocksPerPixel() == 48, area.blocksPerPixel() + " blocks a pixel, not 48");
		helper.assertTrue(Math.abs(area.centreX() - HALL.getX()) < 16 && Math.abs(area.centreZ() - HALL.getZ()) < 16,
			"the chart isn't centred on the hall");
		// A size that isn't whole regions rounds up, and a file can't ask for more than the cap.
		helper.assertTrue(SkyChart.Area.around(HALL, 1_000).regionsAcross() == 2, "1,000 blocks doesn't round up to 2 regions");
		helper.assertTrue(SkyChart.Area.around(HALL, 1_000_000).blocksAcross() == SkyChart.MAX_SIZE, "the size isn't capped");
		helper.succeed();
	}

	//$ gametest 'FabricGameTest.EMPTY_STRUCTURE'
	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
	public void theChartsEdgeHoldsAndNothingPastIt(GameTestHelper helper) {
		SkyChart.Area area = area();
		int west = area.minX();
		int north = area.minZ();
		int east = west + 6_143;
		int south = north + 6_143;
		BlockPos nw = new BlockPos(west, 64, north);
		BlockPos se = new BlockPos(east, 64, south);
		helper.assertTrue(area.contains(nw) && area.contains(se), "the chart's corners are off it");
		helper.assertTrue(area.regionOf(nw) == 0 && area.regionOf(se) == 143, "corner regions " + area.regionOf(nw) + ", " + area.regionOf(se));
		helper.assertTrue(!area.contains(nw.west()) && !area.contains(nw.north()) && !area.contains(se.east()) && !area.contains(se.south()),
			"a block past the edge is on the chart");
		BlockPos middle = new BlockPos(area.centreX() + 480, 64, area.centreZ() - 960); // 10 pixels east, 20 north
		Stub stub = new Stub(List.of(
			new SkyChart.Found(VILLAGES, PLAINS, nw), new SkyChart.Found(SHIPWRECKS, WRECK, se), new SkyChart.Found(PORTALS, PORTAL, middle),
			new SkyChart.Found(VILLAGES, PLAINS, nw.west()), new SkyChart.Found(SHIPWRECKS, WRECK, se.south()),
			new SkyChart.Found(PORTALS, PORTAL, new BlockPos(east + 2_000, 64, area.centreZ()))));
		SkyChart.Search search = new SkyChart.Search();
		run(helper, search, stub, 0, 3_000);
		helper.assertTrue(search.done(), "the search isn't done after three hours");
		Set<BlockPos> found = new HashSet<>();
		search.found().forEach(f -> found.add(f.pos()));
		helper.assertTrue(found.equals(Set.of(nw, se, middle)), "found " + found + ", not the corners and the middle");

		// On the chart: both corners on vanilla's edge pixel (63, not dropped), the middle where it is.
		ItemStack chart = SkyChart.chart(helper.getLevel(), area, search.found());
		MapItemSavedData data = MapItem.getSavedData(chart, helper.getLevel());
		helper.assertTrue(data != null && data.scale == MapItemSavedData.MAX_SCALE && data.locked, "the chart isn't a locked scale-4 map");
		ServerPlayer player = helper.makeMockServerPlayerInLevel();
		data.tickCarriedBy(player, chart);
		List<MapDecoration> marks = new ArrayList<>();
		data.getDecorations().forEach(marks::add);
		helper.assertTrue(marks.size() == 3, marks.size() + " markers on the chart, not 3");
		helper.assertTrue(marks.stream().anyMatch(m -> m.x() <= -125 && m.y() <= -125), "the north-west corner isn't on the edge: " + marks);
		helper.assertTrue(marks.stream().anyMatch(m -> m.x() >= 125 && m.y() >= 125), "the south-east corner isn't on the edge: " + marks);
		helper.assertTrue(marks.stream().anyMatch(m -> Math.abs(m.x() - 20) <= 1 && Math.abs(m.y() + 40) <= 1),
			"the portal 480 east and 960 north isn't 10 pixels east and 20 north: " + marks);
		helper.assertTrue(marks.stream().anyMatch(m -> m.type().is(MapDecorationTypes.PLAINS_VILLAGE.unwrapKey().orElseThrow())),
			"the village has no village marker");
		helper.assertTrue(chart.get(DataComponents.ITEM_NAME).getContents() instanceof TranslatableContents t
			&& t.getKey().equals("item.aliveworkplace.sky_chart"), "the chart isn't named Sky Chart");
		helper.succeed();
	}

	//$ gametest 'FabricGameTest.EMPTY_STRUCTURE'
	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
	public void oneKindAnHourOneRegionAStepOnlyWithRoom(GameTestHelper helper) {
		Stub stub = new Stub(List.of());
		SkyChart.Search search = new SkyChart.Search();
		for (long t = 0; t < 2_000; t++) {
			search.tick(helper.getLevel(), area(), KINDS, stub, t, false);
		}
		helper.assertTrue(search.steps() == 0, "it searched " + search.steps() + " regions while the tick had no room");
		run(helper, search, stub, 2_000, 3_000);
		helper.assertTrue(search.steps() == 144 && SHIPWRECKS.equals(search.kind()) && search.region() == 0,
			"after the first hour: " + search.steps() + " regions, at " + search.kind() + " region " + search.region());
		helper.assertTrue(144 * SkyChart.STEP_TICKS <= SkyChart.HOUR_TICKS, "a kind's regions don't fit in its hour");
		run(helper, search, stub, 3_000, 3_999);
		helper.assertTrue(search.steps() == 288 && PORTALS.equals(search.kind()) && search.region() == 0,
			"the third kind started in the second hour: " + search.steps() + " regions, at " + search.kind());
		run(helper, search, stub, 3_999, 6_000);
		helper.assertTrue(search.done() && search.steps() == 432, "not done after three hours: " + search.steps());
		run(helper, search, stub, 6_000, 8_000);
		helper.assertTrue(search.steps() == 432, "a finished week searched again: " + search.steps());
		search.restart(KINDS);
		run(helper, search, stub, 8_000, 9_000);
		helper.assertTrue(search.steps() == 576, "the new week's search didn't start: " + search.steps());
		helper.succeed();
	}

	//$ gametest 'FabricGameTest.EMPTY_STRUCTURE'
	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
	public void aSearchSavedMidwayCarriesOnWhereItWas(GameTestHelper helper) {
		SkyChart.Area area = area();
		List<SkyChart.Found> starts = new ArrayList<>();
		java.util.Random random = new java.util.Random(35_18L);
		for (int i = 0; i < 300; i++) {
			ResourceLocation kind = KINDS.get(i % 3);
			ResourceLocation structure = kind == VILLAGES ? PLAINS : kind == SHIPWRECKS ? WRECK : PORTAL;
			starts.add(new SkyChart.Found(kind, structure, new BlockPos(area.minX() + random.nextInt(6_144), 64, area.minZ() + random.nextInt(6_144))));
		}
		Stub stub = new Stub(starts);
		SkyChart.Search whole = new SkyChart.Search();
		run(helper, whole, stub, 0, 3_000);

		SkyChart.Search before = new SkyChart.Search();
		run(helper, before, stub, 0, 1_250); // the second kind, 50 regions in
		helper.assertTrue(SHIPWRECKS.equals(before.kind()) && before.region() == 50, "at " + before.kind() + " region " + before.region());
		CompoundTag saved = before.save().copy();
		SkyChart.Search after = SkyChart.Search.load(saved);
		helper.assertTrue(SHIPWRECKS.equals(after.kind()) && after.region() == 50 && after.found().equals(before.found()),
			"the reloaded search is at " + after.kind() + " region " + after.region() + " with " + after.found().size() + " found");
		run(helper, after, stub, 1_250, 3_000);
		helper.assertTrue(after.steps() == 432 - 194, "after reloading it searched " + after.steps() + " regions, not the 238 left");
		helper.assertTrue(after.done() && new HashSet<>(after.found()).equals(new HashSet<>(whole.found())) && whole.found().size() == 300,
			"reloaded: " + after.found().size() + " found, uninterrupted: " + whole.found().size());
		// The same hour isn't started twice by a reload: the saved hour comes back with it.
		SkyChart.Search mid = SkyChart.Search.load(before.save());
		mid.restart(KINDS);
		mid.tick(helper.getLevel(), area, KINDS, stub, 1_250, true);
		helper.assertTrue(mid.steps() == 0, "a restart in the hour a kind began started another kind");
		// An old hall (no saved search) and a kind the data no longer has read as a search not yet begun.
		SkyChart.Search empty = SkyChart.Search.load(new CompoundTag());
		helper.assertTrue(empty.found().isEmpty() && empty.kind() == null && !empty.done(), "an empty tag isn't a fresh search");
		CompoundTag stale = before.save();
		stale.putString("kind", "minecraft:gone");
		SkyChart.Search fromStale = SkyChart.Search.load(stale);
		fromStale.tick(helper.getLevel(), area, KINDS, stub, 5_000, true);
		helper.assertTrue(fromStale.steps() == 1 && VILLAGES.equals(fromStale.kind()), "a stale kind didn't start over at the first");
		helper.succeed();
	}

	/** On an idle box {@link #load} times its 16,384 biome samples at about 8 ms (7 to 9 in a dozen runs, 2026-10-18). */
	private static final long IDLE_SAMPLES_NANOS = 10_000_000L;

	/**
	 * How many times slower than idle the machine is right now (1 to 20): the 16,384 biome samples a chart takes, taken
	 * on another 6,144-block square (so the chart's own aren't warmed) and timed against {@link #IDLE_SAMPLES_NANOS}.
	 * Only the game's own code runs in it, so the cost limits below stretch with a loaded machine (B167: the chart took
	 * 564 ms against a fixed 250 ms on a busy shared box) and never with a slower chart or search.
	 */
	private static double load(ServerLevel level, SkyChart.Area area) {
		int bpp = area.blocksPerPixel();
		int y = QuartPos.fromBlock(level.getSeaLevel());
		int west = area.minX() + 2 * area.blocksAcross();
		long start = System.nanoTime();
		for (int pz = 0; pz < 128; pz++) {
			for (int px = 0; px < 128; px++) {
				level.getNoiseBiome(QuartPos.fromBlock(west + px * bpp + bpp / 2), y, QuartPos.fromBlock(area.minZ() + pz * bpp + bpp / 2))
					.is(BiomeTags.WATER_ON_MAP_OUTLINES);
			}
		}
		long took = System.nanoTime() - start;
		return Math.round(Math.min(20.0, Math.max(1.0, (double) took / IDLE_SAMPLES_NANOS)) * 10) / 10.0;
	}

	//$ gametest 'FabricGameTest.EMPTY_STRUCTURE'
	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
	public void aStepAndTheChartAreCheap(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		SkyChart.Area area = area();
		// The world's own finder over every region of every kind this world places (or the villages where it places none).
		List<ResourceLocation> kinds = SkyChart.kinds(level).isEmpty() ? List.of(VILLAGES) : SkyChart.kinds(level);
		load(level, area); // the first, cold, warms the samples up
		// Each limit scales with the machine's load, measured before and after what it times (the slower of the two), so
		// a runner that turns busy mid-loop is seen. One pause (a GC, a busy CI runner: 165 ms once, B267) mustn't decide
		// the verdict either: the steps are judged by their mean without the slowest tenth, and the two single timings
		// below by the best of three, while a search that is slow every time still fails.
		double loadBefore = load(level, area);
		List<Long> took = new ArrayList<>();
		for (ResourceLocation kind : kinds) {
			for (int r = 0; r < area.regions(); r++) {
				long start = System.nanoTime();
				SkyChart.VANILLA.find(level, kind, area.regionMin(r), SkyChart.REGION_CHUNKS, (s, p) -> {
				});
				took.add(System.nanoTime() - start);
			}
		}
		double stepLoad = Math.max(loadBefore, load(level, area));
		took.sort(null);
		int kept = took.size() - took.size() / 10;
		long total = 0;
		for (int i = 0; i < kept; i++) {
			total += took.get(i);
		}
		long typical = total / kept;
		helper.assertTrue(typical < 5_000_000L * stepLoad, "a search step takes " + typical / 1_000 + " µs on average without the slowest tenth (median "
			+ took.get(took.size() / 2) / 1_000 + " µs, worst " + took.get(took.size() - 1) / 1_000 + " µs; the machine " + stepLoad
			+ " times slower than idle)");
		// A thousand structures already found: the per-step bookkeeping stays small.
		List<SkyChart.Found> starts = new ArrayList<>();
		for (int i = 0; i < 1_000; i++) {
			starts.add(new SkyChart.Found(VILLAGES, PLAINS, new BlockPos(area.minX() + (i * 37) % 6_144, 64, area.minZ() + (i * 91) % 6_144)));
		}
		SkyChart.Search search = null;
		long kindNanos = Long.MAX_VALUE;
		loadBefore = load(level, area);
		for (int attempt = 0; attempt < 3; attempt++) {
			search = new SkyChart.Search();
			long started = System.nanoTime();
			run(helper, search, new Stub(starts), 0, 1_000);
			kindNanos = Math.min(kindNanos, System.nanoTime() - started);
			helper.assertTrue(search.found().size() == 1_000, search.found().size() + " of 1,000 found");
		}
		double kindLoad = Math.max(loadBefore, load(level, area));
		helper.assertTrue(kindNanos / 144 < 2_000_000L * kindLoad, "a step with 1,000 found takes " + kindNanos / 144 / 1_000
			+ " µs at best of three (the machine " + kindLoad + " times slower than idle)");
		// The chart: 16,384 biome samples, like a vanilla explorer map, whatever its size (about 40 ms idle; sampled per
		// block, 37 million, it would cost thousands of times that).
		long draw = Long.MAX_VALUE;
		loadBefore = load(level, area);
		for (int attempt = 0; attempt < 3; attempt++) {
			long drawStart = System.nanoTime();
			SkyChart.chart(level, area, search.found());
			draw = Math.min(draw, System.nanoTime() - drawStart);
		}
		double chartLoad = Math.max(loadBefore, load(level, area));
		helper.assertTrue(draw < 250_000_000L * chartLoad, "drawing the chart takes " + draw / 1_000_000 + " ms at best of three (the machine "
			+ chartLoad + " times slower than idle)");
		helper.succeed();
	}
}
