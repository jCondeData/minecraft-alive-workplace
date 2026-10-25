package io.github.jcondedata.aliveworkplace.test;

import io.github.jcondedata.aliveworkplace.blueprint.Blueprint;
import io.github.jcondedata.aliveworkplace.blueprint.BlueprintLibrary;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.ai.village.poi.PoiTypes;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BedPart;

/**
 * QA (lane-b-1025-0038), from B149 ("the Storehouse has a second floor with no ladder or stairs, so it can't be
 * reached") and 23.10 ("every shipped build reviewed"), not from the code: in every build the Blueprint Table lists, every
 * chest, barrel, bed and job block (a workstation a villager can take a job at) can be walked to from the ground
 * outside, within a player's reach (two blocks across, one up or down) of a spot reached on foot. The walk is
 * {@link QaBasementGameTests#walk}: a block at a time, level, one up, up to three down, ladders; doors and trapdoors open.
 */
public class QaBuildReachGameTests implements FabricGameTest {

	private static boolean target(BlockState state) {
		if (state.is(Blocks.CHEST) || state.is(Blocks.BARREL) || state.is(Blocks.TRAPPED_CHEST)) {
			return true;
		}
		if (state.getBlock() instanceof BedBlock) {
			return state.getValue(BedBlock.PART) == BedPart.HEAD;
		}
		return PoiTypes.forState(state).filter(type -> type.is(net.minecraft.tags.PoiTypeTags.ACQUIRABLE_JOB_SITE)).isPresent();
	}

	//$ gametest 'FabricGameTest.EMPTY_STRUCTURE'
	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
	public void everyListedBuildsChestsBedsAndJobBlocksCanBeWalkedTo(GameTestHelper helper) {
		List<String> problems = new ArrayList<>();
		int builds = 0;
		int targets = 0;
		for (ResourceLocation id : BlueprintLibrary.list(helper.getLevel().getServer(), true)) {
			Blueprint blueprint = BlueprintLibrary.get(helper.getLevel(), id).orElse(null);
			// the modded/ looks need their mods' blocks (missing here) and a Wonder's stages are parts of a whole
			if (blueprint == null || !id.getNamespace().equals("aliveworkplace") || id.getPath().startsWith("modded/") || id.getPath().startsWith("wonder/")) {
				continue;
			}
			// B424 (open): the camps' tents hide their chest and barrels (QaCampReachGameTests on tests/qa-camp-chest-1025).
			// Take these two out of this list with B424's fix.
			if (id.getPath().equals("camp/settlers_camp") || id.getPath().equals("camp/colony_camp")) {
				continue;
			}
			builds++;
			Map<BlockPos, BlockState> blocks = QaBasementGameTests.blocks(blueprint);
			int sx = blueprint.size().getX();
			int sz = blueprint.size().getZ();
			int top = blueprint.size().getY();
			int bottom = -blueprint.basement();
			List<BlockPos> outside = new ArrayList<>();
			for (int x = -2; x <= sx + 1; x++) {
				for (int z = -2; z <= sz + 1; z++) {
					if (x < -1 || x > sx || z < -1 || z > sz) {
						outside.add(new BlockPos(x, 0, z));
					}
				}
			}
			Set<BlockPos> reached = QaBasementGameTests.walk(blocks, outside, sx, sz, top, bottom);
			List<String> cutOff = new ArrayList<>();
			for (Map.Entry<BlockPos, BlockState> e : blocks.entrySet()) {
				if (!target(e.getValue()) || QaBasementGameTests.at(blocks, e.getKey().above()).is(Blocks.CHAIN)) {
					// (a barrel hung on a chain is a shop's sign, like the Tinker's over its door)
					continue;
				}
				targets++;
				BlockPos c = e.getKey();
				boolean near = false;
				for (BlockPos p : BlockPos.betweenClosed(c.offset(-2, -1, -2), c.offset(2, 1, 2))) {
					if (reached.contains(p)) {
						near = true;
						break;
					}
				}
				if (!near) {
					cutOff.add(e.getValue().getBlock().getName().getString() + " " + c.toShortString());
				}
			}
			if (!cutOff.isEmpty()) {
				problems.add(id.getPath() + ": " + cutOff);
			}
		}
		helper.assertTrue(builds > 100 && targets > 1000, "only " + builds + " builds and " + targets + " targets checked");
		helper.assertTrue(problems.isEmpty(), problems.size() + " builds with things nobody can walk to: " + String.join("; ", problems));
		helper.succeed();
	}
}
