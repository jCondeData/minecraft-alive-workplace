package io.github.jcondedata.aliveworkplace.test;

import io.github.jcondedata.aliveworkplace.blueprint.BlueprintData;
import io.github.jcondedata.aliveworkplace.blueprint.StarterBlueprints;
import io.github.jcondedata.aliveworkplace.build.BuildSiteManager;
import io.github.jcondedata.aliveworkplace.build.Refreshes;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockState;

/**
 * B648 (after B640, the Terrace's broken bay windows): 56 panes and bars in 22 other drawings had their glass joined on
 * one side only, with air on the other, so the window stopped half way across its last block, a thin strip with a gap
 * beside it. Each was redrawn so its glass meets a block on both ends (jambs by doorways, a corner pane in the glass
 * cases, posts at the railings' ends, windows moved back into their bays). Here every structure the mod ships (the
 * vanilla and modded libraries, the village houses, the Wonders' parts) is read as stored, and no pane or bars may be
 * joined on one side only with nothing on the other, except the ends listed in {@link #ALLOWED} with their reason
 * (tools/blueprints/check.py's open_panes say the same). And a Townhouse built from the drawing before is offered
 * Refresh to the latest look.
 */
public class B648GameTests implements net.fabricmc.fabric.api.gametest.v1.FabricGameTest {
	private static final String AREA = "aliveworkplace_test:huge_area";
	private static final BlockPos ORIGIN = new BlockPos(8, 2, 8);
	/** The drawings players' builds were built from before B648, as the mod ships them for Refresh to the latest look. */
	private static final String BEFORE = "cd1557d8c";

	private record Allowed(String pathEnd, BlockPos pos, String why) {
	}

	/** Pane or bar ends meant to stand free, by the end of the structure's path and the position in it. */
	static final List<Allowed> ALLOWED = List.of(
		new Allowed("_print_shop", new BlockPos(6, 5, 0), "the print shop's lamp arm ends over the lantern it holds"));

	/** No shipped structure has a glass pane or iron bars joined on one side and open to the air on the other. */
	//$ gametest_ticks EMPTY_STRUCTURE '200'
	@GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 200)
	public void b648NoShippedGlassEndsInTheAir(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		Map<ResourceLocation, Resource> files = level.getServer().getResourceManager()
			.listResources("structure", id -> id.getNamespace().equals("aliveworkplace") && id.getPath().endsWith(".nbt"));
		List<String> problems = new ArrayList<>();
		int structures = 0;
		int panes = 0;
		for (Map.Entry<ResourceLocation, Resource> e : files.entrySet()) {
			String path = e.getKey().getPath().substring("structure/".length(), e.getKey().getPath().length() - ".nbt".length());
			CompoundTag tag;
			try (InputStream in = e.getValue().open()) {
				tag = NbtIo.readCompressed(in, NbtAccounter.unlimitedHeap());
			} catch (Exception ex) {
				throw new IllegalStateException("couldn't read " + e.getKey() + ": " + ex);
			}
			structures++;
			ListTag palette = tag.getList("palette", Tag.TAG_COMPOUND);
			Map<BlockPos, CompoundTag> blocks = new HashMap<>();
			for (Tag t : tag.getList("blocks", Tag.TAG_COMPOUND)) {
				CompoundTag block = (CompoundTag) t;
				ListTag pos = block.getList("pos", Tag.TAG_INT);
				blocks.put(new BlockPos(pos.getInt(0), pos.getInt(1), pos.getInt(2)), palette.getCompound(block.getInt("state")));
			}
			for (Map.Entry<BlockPos, CompoundTag> b : blocks.entrySet()) {
				String name = b.getValue().getString("Name");
				if (!name.endsWith("glass_pane") && !name.equals("minecraft:iron_bars")) {
					continue;
				}
				panes++;
				CompoundTag props = b.getValue().getCompound("Properties");
				List<Direction> joined = new ArrayList<>();
				for (Direction d : Direction.Plane.HORIZONTAL) {
					if ("true".equals(props.getString(d.getName()))) {
						joined.add(d);
					}
				}
				if (joined.size() != 1) {
					continue;
				}
				Direction open = joined.get(0).getOpposite();
				CompoundTag other = blocks.get(b.getKey().relative(open));
				boolean air = other == null || other.getString("Name").endsWith(":air");
				if (!air || allowed(path, b.getKey())) {
					continue;
				}
				problems.add(path + ": " + name.substring(name.indexOf(':') + 1) + " at " + b.getKey().toShortString()
					+ " joins only to the " + joined.get(0).getName() + ", air to the " + open.getName());
			}
		}
		helper.assertTrue(structures > 300, "only " + structures + " structures read");
		helper.assertTrue(panes > 1000, "only " + panes + " panes and bars read");
		helper.assertTrue(problems.isEmpty(), "B648: " + problems.size() + " panes or bars end in the air, e.g. "
			+ String.join("; ", problems.subList(0, Math.min(6, problems.size()))));
		helper.succeed();
	}

	private static boolean allowed(String path, BlockPos pos) {
		return ALLOWED.stream().anyMatch(a -> path.endsWith(a.pathEnd()) && a.pos().equals(pos));
	}

	/**
	 * A Townhouse already standing as the drawing before B648 drew it (one iron bar at each front balcony, joined only
	 * to the window behind) is offered Refresh to the latest look from that drawing, which puts the new railings in.
	 */
	//$ gametest_ticks_batch AREA '100' '"b648OldTownhouseRefresh"'
	@GameTest(template = AREA, timeoutTicks = 100, batch = "b648OldTownhouseRefresh")
	public void b648AnOldTownhouseIsOfferedTheNewRailings(GameTestHelper helper) {
		Leftovers.clear(helper);
		ServerLevel level = helper.getLevel();
		BlueprintData.Placement placement = new BlueprintData.Placement(level.dimension().location(), helper.absolutePos(ORIGIN), Rotation.NONE, Mirror.NONE);
		io.github.jcondedata.aliveworkplace.blueprint.Blueprint old = Refreshes.olderDrawing(level.getServer(), StarterBlueprints.TOWNHOUSE.id(), BEFORE)
			.orElseThrow(() -> new IllegalStateException("the Townhouse's drawing before B648 isn't shipped"));
		for (Map.Entry<BlockPos, BlockState> e : io.github.jcondedata.aliveworkplace.build.BuildPlan.create(old, placement).targets().entrySet()) {
			level.setBlock(e.getKey(), e.getValue(), Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE);
		}
		BuildSiteManager.get(level).recordFinished(StarterBlueprints.TOWNHOUSE.id(), placement, UUID.randomUUID());
		Leftovers.after(helper, () -> BuildSiteManager.get(level).forgetFinished(placement));
		BuildSiteManager.Finished townhouse = BuildSiteManager.get(level).finishedIn(level).stream().filter(f -> f.placement().equals(placement))
			.findFirst().orElseThrow();
		var change = Refreshes.change(level, townhouse);
		helper.assertTrue(change.isPresent(), "an old Townhouse isn't offered Refresh to the latest look");
		helper.assertTrue(BEFORE.equals(change.get().from()), "refreshed from " + change.get().from() + ", not the drawing before B648");
		helper.assertTrue(change.get().total() >= 3, "the refresh changes " + change.get().total() + " pieces, fewer than the three railings");
		helper.succeed();
	}
}
