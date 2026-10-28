package io.github.jcondedata.aliveworkplace.test;

import io.github.jcondedata.aliveworkplace.blueprint.BlueprintData;
import io.github.jcondedata.aliveworkplace.blueprint.BlueprintLibrary;
import io.github.jcondedata.aliveworkplace.blueprint.BlueprintOutline;
import io.github.jcondedata.aliveworkplace.blueprint.StarterBlueprints;
import io.github.jcondedata.aliveworkplace.build.BuildPlan;
import io.github.jcondedata.aliveworkplace.build.BuildSite;
import io.github.jcondedata.aliveworkplace.build.BuildSiteManager;
import io.github.jcondedata.aliveworkplace.build.Builders;
import io.github.jcondedata.aliveworkplace.registry.ModBlocks;
import io.github.jcondedata.aliveworkplace.registry.ModGameRules;
import static io.github.jcondedata.aliveworkplace.test.QaWindowsAndWards1028GameTests.*;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CarpetBlock;
import net.minecraft.world.level.block.CrossCollisionBlock;
import net.minecraft.world.level.block.IronBarsBlock;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.SlabBlock;
import net.minecraft.world.level.block.StairBlock;
import net.minecraft.world.level.block.WallBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BedPart;
import net.minecraft.world.level.block.state.properties.Half;
import net.minecraft.world.level.block.state.properties.SlabType;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.material.FluidState;

/**
 * QA (qa-lane-b-1028-0037), B648 (every window's glass meets a jamb at both ends, in every drawing): every structure the
 * mod ships, its panes and bars joined as the game itself joins them from the blocks really beside them (not the
 * connections the file stores, which B648GameTests reads), has no glass ending in open air and no lone glass post. Wonder
 * parts are judged in their whole Wonder, as they stand together. Fails on main 2026-10-28: the Pokemon Center plaza
 * looks' upper window panes stand between two stairs they can't join (the Terrace's B640 look), and the Lighthouse's
 * lower window panes stand one block out from the tower, joined to nothing.
 */
public class QaWindowsAndWards1028BugTests implements net.fabricmc.fabric.api.gametest.v1.FabricGameTest {
	private static final org.slf4j.Logger LOGGER = org.slf4j.LoggerFactory.getLogger("aliveworkplace_test");

	/**
	 * Every structure the mod ships (the vanilla and modded libraries, village looks, furnished and Wonder parts), its
	 * glass panes and iron bars joined as the game joins them from the blocks really around them: none has a side
	 * against open air (one side joined and air on the other, or a lone post beside air). Unlike B648GameTests this
	 * ignores the connections stored in the file, which a drawing can get wrong.
	 */
	//$ gametest_ticks EMPTY_STRUCTURE '400'
	@GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 400)
	public void qaNoShippedGlassEndsInTheAirAsTheGameJoinsIt(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		var lookup = level.holderLookup(Registries.BLOCK);
		Map<ResourceLocation, Resource> files = level.getServer().getResourceManager()
			.listResources("structure", id -> id.getNamespace().equals("aliveworkplace") && id.getPath().endsWith(".nbt"));
		List<String> problems = new ArrayList<>();
		int structures = 0;
		int panes = 0;
		int unknown = 0;
		int stored = 0;
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
			List<BlockState> states = new ArrayList<>();
			List<Boolean> known = new ArrayList<>();
			for (int i = 0; i < palette.size(); i++) {
				CompoundTag p = palette.getCompound(i);
				ResourceLocation name = ResourceLocation.tryParse(p.getString("Name"));
				known.add(name != null && BuiltInRegistries.BLOCK.containsKey(name));
				states.add(NbtUtils.readBlockState(lookup, p));
			}
			Map<BlockPos, BlockState> blocks = new HashMap<>();
			Map<BlockPos, Boolean> modded = new HashMap<>();
			for (Tag t : tag.getList("blocks", Tag.TAG_COMPOUND)) {
				CompoundTag block = (CompoundTag) t;
				ListTag pos = block.getList("pos", Tag.TAG_INT);
				BlockPos at = new BlockPos(pos.getInt(0), pos.getInt(1), pos.getInt(2));
				int s = block.getInt("state");
				blocks.put(at, states.get(s));
				if (!known.get(s)) {
					modded.put(at, true);
				}
			}
			if (path.startsWith("wonder/")) {
				// A Wonder's part: its glass joins the parts and stages beside it, so it is judged in the whole Wonder below
				continue;
			}
			int[] counts = check(path, blocks, modded, problems);
			panes += counts[0];
			unknown += counts[1];
			stored += counts[2];
		}
		int wonders = 0;
		for (ResourceLocation id : io.github.jcondedata.aliveworkplace.wonder.Wonders.loaded().keySet()) {
			ResourceLocation whole = io.github.jcondedata.aliveworkplace.wonder.WonderBlueprints.idOf(id);
			io.github.jcondedata.aliveworkplace.blueprint.Blueprint wonder = io.github.jcondedata.aliveworkplace.wonder.WonderBlueprints
				.whole(level.getServer(), whole).orElseThrow(() -> new IllegalStateException("the Wonder " + whole + " has no whole blueprint"));
			Map<BlockPos, BlockState> blocks = new HashMap<>();
			wonder.blocks().forEach(x -> blocks.put(x.pos(), x.state()));
			int[] counts = check(whole.getPath(), blocks, Map.of(), problems);
			panes += counts[0];
			wonders++;
		}
		helper.assertTrue(wonders >= 3, "only " + wonders + " Wonders put together");
		helper.assertTrue(structures > 300, "only " + structures + " structures read");
		helper.assertTrue(panes > 1000, "only " + panes + " panes and bars read");
		LOGGER.info("QA B648: " + panes + " panes and bars in " + structures + " structures joined as the game joins them; " + unknown
			+ " beside another mod's block left to the vanilla copy; " + stored + " stored with other connections than the game gives");
		problems.forEach(p -> LOGGER.info("QA B648 open end: {}", p));
		helper.assertTrue(problems.isEmpty(), "B648: " + problems.size() + " panes or bars, joined as the game joins them, end in open air: "
			+ String.join("; ", problems.subList(0, Math.min(2, problems.size()))));
		helper.succeed();
	}


	/** The glass in one drawing: counts {panes, beside another mod's block, stored otherwise}; offenders into problems. */
	private static int[] check(String path, Map<BlockPos, BlockState> blocks, Map<BlockPos, Boolean> modded, List<String> problems) {
		int panes = 0;
		int unknown = 0;
		int stored = 0;
		BlockGetter getter = over(blocks);
		for (Map.Entry<BlockPos, BlockState> b : blocks.entrySet()) {
			if (!glass(b.getValue())) {
				continue;
			}
			panes++;
			BlockPos at = b.getKey();
			boolean blind = false;
			for (Direction d : Direction.Plane.HORIZONTAL) {
				blind |= modded.containsKey(at.relative(d));
			}
			if (blind) {
				// Another mod's block beside it (air on a test server without that mod): judged in the vanilla copy
				unknown++;
				continue;
			}
			boolean[] j = joins(getter, at);
			BlockState s = b.getValue();
			if (s.getValue(CrossCollisionBlock.NORTH) != j[Direction.NORTH.get2DDataValue()] || s.getValue(CrossCollisionBlock.SOUTH) != j[Direction.SOUTH.get2DDataValue()]
				|| s.getValue(CrossCollisionBlock.EAST) != j[Direction.EAST.get2DDataValue()] || s.getValue(CrossCollisionBlock.WEST) != j[Direction.WEST.get2DDataValue()]) {
				stored++;
			}
			String why = endsInAir(getter, at);
			if (why != null && !allowed(path, at)) {
				String name = BuiltInRegistries.BLOCK.getKey(s.getBlock()).getPath();
				problems.add(path + ": " + name + " at " + at.toShortString() + " " + why + " (beside: " + around(getter, at) + ")");
			}
		}
		return new int[] {panes, unknown, stored};
	}

	private static String around(BlockGetter level, BlockPos pos) {
		StringBuilder out = new StringBuilder();
		for (Direction d : Direction.values()) {
			out.append(d.getName().charAt(0)).append('=').append(BuiltInRegistries.BLOCK.getKey(level.getBlockState(pos.relative(d)).getBlock()).getPath()).append(' ');
		}
		return out.toString().trim();
	}
}
