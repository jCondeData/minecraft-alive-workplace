package io.github.jcondedata.aliveworkplace.test;

import io.github.jcondedata.aliveworkplace.AliveWorkplace;
import io.github.jcondedata.aliveworkplace.blueprint.Blueprint;
import io.github.jcondedata.aliveworkplace.blueprint.BlueprintLibrary;
import io.github.jcondedata.aliveworkplace.blueprint.StarterBlueprints;
import io.github.jcondedata.aliveworkplace.blueprint.io.BlueprintImporter;
import io.github.jcondedata.aliveworkplace.table.TablePayloads;
import io.github.jcondedata.aliveworkplace.table.TableServer;
import io.netty.buffer.Unpooled;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Stream;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Vec3i;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

/**
 * ROADMAP 26.9: the Blueprint Table's 3D preview. The server sends the selected entry's blocks (palette plus
 * positions, hollowed, capped, cached); these check that data against the blueprints themselves.
 */
public class TablePreviewGameTests implements FabricGameTest {
	/** Bytes a preview packet may take: well under a custom payload's 1 MiB, small enough to send on every click. */
	private static final int MAX_PACKET_BYTES = 64 * 1024;

	/** The cells of a preview as position (in cells) -> block state. */
	private static Map<BlockPos, BlockState> cells(TablePayloads.Preview preview) {
		Map<BlockPos, BlockState> out = new HashMap<>();
		int[] b = preview.blocks();
		for (int i = 0; i < b.length; i += 4) {
			out.put(new BlockPos(b[i], b[i + 1], b[i + 2]), Block.stateById(preview.palette().get(b[i + 3])));
		}
		return out;
	}

	private static int packetBytes(MinecraftServer server, TablePayloads.Preview preview) {
		RegistryFriendlyByteBuf buf = new RegistryFriendlyByteBuf(Unpooled.buffer(), server.registryAccess());
		try {
			TablePayloads.Preview.CODEC.encode(buf, preview);
			int bytes = buf.readableBytes();
			TablePayloads.Preview back = TablePayloads.Preview.CODEC.decode(buf);
			if (!back.id().equals(preview.id()) || back.scale() != preview.scale() || !java.util.Arrays.equals(back.blocks(), preview.blocks())
				|| !back.palette().equals(preview.palette())) {
				return -1;
			}
			return bytes;
		} finally {
			buf.release();
		}
	}

	/**
	 * A full-scale preview against its blueprint: every cell is the blueprint's own block at that spot, the top block of
	 * every column is there (the roof is seen), nothing hidden inside the walls is sent, and it fits the cap.
	 */
	private static List<String> checkFullScale(MinecraftServer server, Blueprint bp, TablePayloads.Preview preview) {
		List<String> problems = new ArrayList<>();
		String name = bp.id().toString();
		if (preview.scale() != 1) {
			problems.add(name + ": drawn at scale " + preview.scale() + ", expected 1");
			return problems;
		}
		if (preview.sizeX() != bp.size().getX() || preview.sizeY() != bp.size().getY() || preview.sizeZ() != bp.size().getZ()) {
			problems.add(name + ": size " + preview.sizeX() + "x" + preview.sizeY() + "x" + preview.sizeZ() + " isn't the blueprint's " + bp.size());
		}
		Map<BlockPos, BlockState> blocks = new HashMap<>();
		for (Blueprint.Entry e : bp.blocks()) {
			if (TableServer.previewShows(e.state())) {
				blocks.put(e.pos(), e.state());
			}
		}
		Map<BlockPos, BlockState> cells = cells(preview);
		if (cells.isEmpty()) {
			problems.add(name + ": the preview is empty");
		}
		if (cells.size() > TableServer.PREVIEW_CAP) {
			problems.add(name + ": " + cells.size() + " cells, over the cap of " + TableServer.PREVIEW_CAP);
		}
		for (Map.Entry<BlockPos, BlockState> c : cells.entrySet()) {
			BlockState want = blocks.get(c.getKey());
			if (!c.getValue().equals(want)) {
				problems.add(name + ": " + c.getKey().toShortString() + " is " + c.getValue() + " in the preview but " + want + " in the blueprint");
				break;
			}
		}
		Map<Long, BlockPos> tops = new HashMap<>();
		for (BlockPos p : blocks.keySet()) {
			tops.merge(BlockPos.asLong(p.getX(), 0, p.getZ()), p, (a, b) -> a.getY() >= b.getY() ? a : b);
		}
		long missingTops = tops.values().stream().filter(p -> !cells.containsKey(p)).count();
		if (missingTops > 0) {
			problems.add(name + ": " + missingTops + " of " + tops.size() + " roof-top blocks are missing from the preview");
		}
		for (BlockPos p : cells.keySet()) {
			boolean hidden = true;
			for (Direction d : Direction.values()) {
				BlockState n = blocks.get(p.relative(d));
				hidden &= n != null && n.canOcclude();
			}
			if (hidden) {
				problems.add(name + ": " + p.toShortString() + " is hidden inside the build but was sent");
				break;
			}
		}
		int bytes = packetBytes(server, preview);
		if (bytes < 0 || bytes > MAX_PACKET_BYTES) {
			problems.add(name + ": the packet " + (bytes < 0 ? "didn't survive encoding" : "is " + bytes + " bytes"));
		}
		return problems;
	}

	/** The starter house the table opens on: its preview is its own blocks, full size, hollowed. */
	//$ gametest 'FabricGameTest.EMPTY_STRUCTURE'
	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
	public void starterHousePreviewIsItsOwnBlocks(GameTestHelper helper) {
		MinecraftServer server = helper.getLevel().getServer();
		ResourceLocation id = StarterBlueprints.STARTER_COTTAGE.id();
		Blueprint bp = BlueprintLibrary.get(server, id).orElseThrow();
		TablePayloads.Preview preview = TableServer.preview(server, id).orElse(null);
		helper.assertTrue(preview != null, "no preview for the starter cottage");
		List<String> problems = checkFullScale(server, bp, preview);
		helper.assertTrue(preview.count() < bp.solidBlockCount(), "nothing was hollowed: " + preview.count() + " of " + bp.solidBlockCount() + " blocks sent");
		helper.assertTrue(TableServer.preview(server, id).orElseThrow() == preview, "the preview was worked out again instead of kept");
		helper.assertTrue(problems.isEmpty(), String.join("; ", problems));
		helper.succeed();
	}

	/** Every build the Table lists has a preview within the cap and the packet budget; the largest one too. */
	//$ gametest 'FabricGameTest.EMPTY_STRUCTURE'
	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
	public void everyListedBuildHasAPreviewUnderTheCap(GameTestHelper helper) {
		MinecraftServer server = helper.getLevel().getServer();
		List<String> problems = new ArrayList<>();
		for (TablePayloads.Entry e : TableServer.listing(server)) {
			Optional<TablePayloads.Preview> preview = TableServer.preview(server, e.id());
			if (preview.isEmpty()) {
				problems.add(e.id() + " is listed but has no preview");
				continue;
			}
			int bytes = packetBytes(server, preview.get());
			if (preview.get().count() > TableServer.PREVIEW_CAP || bytes < 0 || bytes > MAX_PACKET_BYTES || preview.get().count() == 0 && e.blocks() > 0) {
				problems.add(e.id() + ": " + preview.get().count() + " cells, " + bytes + " bytes");
			}
		}
		TablePayloads.Entry largest = TableServer.listing(server).stream()
			.max(Comparator.comparingInt(TablePayloads.Entry::blocks)).orElseThrow();
		Blueprint bp = BlueprintLibrary.get(server, largest.id()).orElseThrow();
		TablePayloads.Preview preview = TableServer.preview(server, largest.id()).orElseThrow();
		if (preview.scale() == 1) {
			problems.addAll(checkFullScale(server, bp, preview));
		}
		helper.assertTrue(problems.isEmpty(), String.join("; ", problems));
		helper.succeed();
	}

	/** A build too big for the cap even hollowed is drawn in coarser cells: a 40-block stone cube becomes 2-block cells. */
	//$ gametest 'FabricGameTest.EMPTY_STRUCTURE'
	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
	public void aHugeBuildIsDrawnCoarserToFitTheCap(GameTestHelper helper) {
		List<Blueprint.Entry> blocks = new ArrayList<>();
		int n = 40;
		for (int x = 0; x < n; x++) {
			for (int y = 0; y < n; y++) {
				for (int z = 0; z < n; z++) {
					blocks.add(new Blueprint.Entry(new BlockPos(x, y, z), y == n - 1 ? Blocks.OAK_PLANKS.defaultBlockState() : Blocks.STONE.defaultBlockState(), null));
				}
			}
		}
		Blueprint cube = new Blueprint(AliveWorkplace.id("test/preview_cube"), new Vec3i(n, n, n), blocks);
		TablePayloads.Preview preview = TableServer.buildPreview(cube.id(), cube);
		Map<BlockPos, BlockState> cells = cells(preview);
		helper.assertTrue(preview.scale() == 2, "a 40-cube's shell (" + (n * n * n - 38 * 38 * 38) + " blocks) should be drawn at scale 2, was " + preview.scale());
		helper.assertTrue(cells.size() <= TableServer.PREVIEW_CAP, cells.size() + " cells, over the cap");
		int c = n / 2;
		helper.assertTrue(cells.size() == c * c * c - (c - 2) * (c - 2) * (c - 2), "the cube's shell at scale 2 is " + (c * c * c - (c - 2) * (c - 2) * (c - 2))
			+ " cells, got " + cells.size());
		helper.assertTrue(Blocks.OAK_PLANKS.defaultBlockState().equals(cells.get(new BlockPos(5, c - 1, 5))), "a top cell should show the planks on top, got "
			+ cells.get(new BlockPos(5, c - 1, 5)));
		helper.assertTrue(packetBytes(helper.getLevel().getServer(), preview) <= MAX_PACKET_BYTES, "the packet is too big");
		helper.succeed();
	}

	/** An imported build (a .litematic, as uploaded) previews from its own blocks like a starter build. */
	//$ gametest 'FabricGameTest.EMPTY_STRUCTURE'
	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
	public void anImportedBuildPreviewsFromItsBlocks(GameTestHelper helper) throws Exception {
		MinecraftServer server = helper.getLevel().getServer();
		ImportGameTests.clearGenerated(helper, "uploads/test_preview");
		BlueprintImporter.Imported imported = BlueprintImporter.importBytes(server, "uploads/test_preview", "Preview Hut.litematic", ImportGameTests.bytes("hut.litematic"));
		Blueprint bp = BlueprintLibrary.get(server, imported.id()).orElseThrow();
		TablePayloads.Preview preview = TableServer.preview(server, imported.id()).orElse(null);
		helper.assertTrue(preview != null, "no preview for the imported " + imported.id());
		List<String> problems = checkFullScale(server, bp, preview);
		helper.assertTrue(problems.isEmpty(), String.join("; ", problems));
		helper.succeed();
	}

	/** Nothing is sent for an id the Table doesn't list: unknown, sold by villagers only, or of a closed expansion. */
	//$ gametest 'FabricGameTest.EMPTY_STRUCTURE'
	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
	public void unlistedIdsSendNoPreview(GameTestHelper helper) {
		MinecraftServer server = helper.getLevel().getServer();
		List<String> problems = new ArrayList<>();
		if (TableServer.preview(server, AliveWorkplace.id("no_such_build")).isPresent()) {
			problems.add("an unknown id got a preview");
		}
		if (TableServer.preview(server, StarterBlueprints.MANOR.id()).isPresent()) {
			problems.add("the Manor (sold by villagers only) got a preview");
		}
		if (TableServer.preview(server, AliveWorkplace.id("village/plains_house")).isPresent()) {
			problems.add("a world-generation piece got a preview");
		}
		List<ResourceLocation> gated = Stream.of(StarterBlueprints.WINERY, StarterBlueprints.GUILDHALL, StarterBlueprints.FIRE_STATION, StarterBlueprints.ARENA)
			.map(StarterBlueprints.Entry::id).toList();
		int[] closed = {0};
		ExpansionGateGameTests.asPlayer(() -> {
			for (ResourceLocation id : gated) {
				if (StarterBlueprints.offered(id)) {
					continue;
				}
				closed[0]++;
				if (TableServer.preview(server, id).isPresent()) {
					problems.add(id + " got a preview while its expansion is closed");
				}
			}
		});
		helper.assertTrue(closed[0] > 0, "none of " + gated + " belongs to a closed expansion any more: pick another for this test");
		helper.assertTrue(TableServer.preview(server, StarterBlueprints.WINERY.id()).isPresent(), "the Winery has no preview with its expansion open");
		helper.assertTrue(problems.isEmpty(), String.join("; ", problems));
		helper.succeed();
	}

	/**
	 * 36.1: Starter Cottage IV's preview carries its cellar: cells below y = 0, the basement's depth in the payload, and
	 * a fitted height (above the ground and below it) that counts the cellar.
	 */
	//$ gametest 'FabricGameTest.EMPTY_STRUCTURE'
	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
	public void aBasementShowsInThePreviewBelowTheGround(GameTestHelper helper) {
		MinecraftServer server = helper.getLevel().getServer();
		ResourceLocation id = StarterBlueprints.STARTER_COTTAGE_4.id();
		Blueprint bp = BlueprintLibrary.get(server, id).orElseThrow();
		TablePayloads.Preview preview = TableServer.preview(server, id).orElseThrow();
		int depth = StarterBlueprints.basement(id);
		helper.assertTrue(depth > 0, "Starter Cottage IV has no basement listed");
		helper.assertTrue(cells(preview).keySet().stream().anyMatch(p -> p.getY() < 0), "no cell below y = 0");
		helper.assertTrue(preview.below() == depth, "the payload's depth is " + preview.below() + ", the basement is " + depth);
		helper.assertTrue(preview.sizeY() + preview.below() == bp.size().getY() + depth,
			"fitted height " + (preview.sizeY() + preview.below()) + " doesn't count the cellar (" + bp.size().getY() + " + " + depth + ")");
		int bytes = packetBytes(server, preview);
		helper.assertTrue(bytes > 0 && bytes <= MAX_PACKET_BYTES, "the packet: " + bytes + " bytes");
		helper.succeed();
	}

	/**
	 * 36.1: at scale 2 no cellar block shares a cell with a ground-floor block (cells use floor division): a block at
	 * y = -1 and one at y = 0 above it are two cells; and every cell of Starter Cottage IV at scale 2 shows the top block
	 * of its own 2-block cube.
	 */
	//$ gametest 'FabricGameTest.EMPTY_STRUCTURE'
	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
	public void atScaleTwoTheCellarNeverFoldsIntoTheGroundFloor(GameTestHelper helper) {
		List<Blueprint.Entry> blocks = List.of(
			new Blueprint.Entry(new BlockPos(0, -1, 0), Blocks.STONE.defaultBlockState(), null),
			new Blueprint.Entry(new BlockPos(0, 0, 0), Blocks.OAK_PLANKS.defaultBlockState(), null));
		Blueprint tiny = new Blueprint(AliveWorkplace.id("test/preview_cellar"), new Vec3i(1, 1, 1), blocks);
		TablePayloads.Preview small = TableServer.buildPreview(tiny.id(), tiny, 2);
		Map<BlockPos, BlockState> smallCells = cells(small);
		helper.assertTrue(small.scale() == 2 && small.below() == 1, "scale " + small.scale() + ", below " + small.below());
		helper.assertTrue(Blocks.STONE.defaultBlockState().equals(smallCells.get(new BlockPos(0, -1, 0)))
			&& Blocks.OAK_PLANKS.defaultBlockState().equals(smallCells.get(new BlockPos(0, 0, 0))), "the cellar folded into the ground floor: " + smallCells);

		MinecraftServer server = helper.getLevel().getServer();
		Blueprint bp = BlueprintLibrary.get(server, StarterBlueprints.STARTER_COTTAGE_4.id()).orElseThrow();
		TablePayloads.Preview coarse = TableServer.buildPreview(bp.id(), bp, 2);
		Map<BlockPos, Blueprint.Entry> tops = new HashMap<>();
		for (Blueprint.Entry e : bp.blocks()) {
			if (TableServer.previewShows(e.state())) {
				BlockPos cell = new BlockPos(Math.floorDiv(e.pos().getX(), 2), Math.floorDiv(e.pos().getY(), 2), Math.floorDiv(e.pos().getZ(), 2));
				tops.merge(cell, e, (a, b) -> b.pos().getY() > a.pos().getY() ? b : a);
			}
		}
		List<String> problems = new ArrayList<>();
		for (Map.Entry<BlockPos, BlockState> c : cells(coarse).entrySet()) {
			Blueprint.Entry top = tops.get(c.getKey());
			if (top == null || !top.state().equals(c.getValue())) {
				problems.add(c.getKey().toShortString() + " shows " + c.getValue() + ", its cube's top is " + (top == null ? "nothing" : top.state() + " at y " + top.pos().getY()));
			}
			if (c.getKey().getY() >= 0 && top != null && top.pos().getY() < 0) {
				problems.add(c.getKey().toShortString() + " is a ground-floor cell drawn from the cellar");
			}
		}
		helper.assertTrue(cells(coarse).keySet().stream().anyMatch(p -> p.getY() < 0), "no cellar cell at scale 2");
		helper.assertTrue(problems.isEmpty(), problems.size() + " wrong cells: " + String.join("; ", problems.subList(0, Math.min(4, problems.size()))));
		helper.succeed();
	}
}
