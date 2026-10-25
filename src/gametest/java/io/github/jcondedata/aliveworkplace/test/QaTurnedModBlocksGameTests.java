package io.github.jcondedata.aliveworkplace.test;

import io.github.jcondedata.aliveworkplace.blueprint.Blueprint;
import io.github.jcondedata.aliveworkplace.blueprint.BlueprintData;
import io.github.jcondedata.aliveworkplace.blueprint.BlueprintLibrary;
import io.github.jcondedata.aliveworkplace.blueprint.StarterBlueprints;
import io.github.jcondedata.aliveworkplace.build.BuildPlan;
import io.github.jcondedata.aliveworkplace.build.BuildSite;
import io.github.jcondedata.aliveworkplace.build.BuildSiteManager;
import io.github.jcondedata.aliveworkplace.build.Builders;
import io.github.jcondedata.aliveworkplace.registry.ModBlocks;
import io.github.jcondedata.aliveworkplace.registry.ModGameRules;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * QA (lane B), from ROADMAP 23.8 (a blueprint is built turned and mirrored as the player placed it; "the front is the
 * template's z=0 side") and every build's own blocks: whatever faces a way in a blueprint faces the same way relative to
 * the building once it's turned or mirrored. Every block with a horizontal facing in every starter build, in all four
 * turns, plain and mirrored, is planned facing its blueprint facing turned with the build; and a builder builds the
 * Storehouse turned a quarter with its Storehouse block facing the way the building faces.
 */
public class QaTurnedModBlocksGameTests implements FabricGameTest {
	private static final String HUGE = "aliveworkplace_test:huge_area";
	private static final Logger LOGGER = LoggerFactory.getLogger("aliveworkplace-qa");

	//$ gametest 'FabricGameTest.EMPTY_STRUCTURE'
	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
	public void everyFacingBlockTurnsWithItsBuild(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		BlockPos origin = helper.absolutePos(new BlockPos(1, 2, 1)).offset(0, 100, 0);
		Set<String> stuck = new TreeSet<>();
		List<String> examples = new ArrayList<>();
		int checked = 0;
		for (StarterBlueprints.Entry entry : StarterBlueprints.ALL) {
			Blueprint blueprint = BlueprintLibrary.get(level, entry.id()).orElseThrow();
			Map<BlockPos, BlockState> drawn = new HashMap<>();
			blueprint.blocks().forEach(e -> drawn.put(e.pos(), e.state()));
			for (Mirror mirror : List.of(Mirror.NONE, Mirror.FRONT_BACK)) {
				for (Rotation rotation : Rotation.values()) {
					BlueprintData.Placement placement = new BlueprintData.Placement(level.dimension().location(), origin, rotation, mirror);
					Map<BlockPos, BlockState> planned = new HashMap<>();
					BuildPlan.create(blueprint, placement).steps(BuildPlan.Stage.CLEAR).forEach(s -> planned.put(s.pos(), s.state()));
					for (Map.Entry<BlockPos, BlockState> e : drawn.entrySet()) {
						BlockState was = e.getValue();
						// vanilla's anvil turns but doesn't mirror its facing: it looks the same either way round, so it's left out
						if (!was.hasProperty(HorizontalDirectionalBlock.FACING) || was.is(net.minecraft.world.level.block.Blocks.ANVIL)) {
							continue;
						}
						BlockPos at = origin.offset(StructureTemplate.transform(e.getKey(), mirror, rotation, BlockPos.ZERO));
						BlockState now = planned.get(at);
						if (now == null || !now.hasProperty(HorizontalDirectionalBlock.FACING)) {
							continue;
						}
						Direction expected = rotation.rotate(mirror.mirror(was.getValue(HorizontalDirectionalBlock.FACING)));
						checked++;
						if (now.getValue(HorizontalDirectionalBlock.FACING) != expected) {
							String block = BuiltInRegistries.BLOCK.getKey(was.getBlock()).toString();
							stuck.add(block);
							if (examples.size() < 6) {
								examples.add(entry.id().getPath() + " " + rotation + (mirror == Mirror.NONE ? "" : " mirrored") + ": " + block + " at "
									+ e.getKey().toShortString() + " drawn facing " + was.getValue(HorizontalDirectionalBlock.FACING) + " faces "
									+ now.getValue(HorizontalDirectionalBlock.FACING) + ", expected " + expected);
							}
						}
					}
				}
			}
		}
		LOGGER.info("qa turned mod blocks: {} facings checked in 8 placements of every starter build; blocks that don't turn: {}", checked, stuck);
		helper.assertTrue(stuck.isEmpty(), "blocks that don't turn with their build: " + stuck + "; e.g. " + examples);
		helper.succeed();
	}

	/** The player's view: the Storehouse turned a quarter, built by a builder, has its Storehouse block facing as it should. */
	//$ gametest_ticks_batch HUGE '12000' '"qaTurnedStorehouse"'
	@GameTest(template = HUGE, timeoutTicks = 12000, batch = "qaTurnedStorehouse")
	public void aTurnedStorehouseIsBuiltWithItsStorehouseBlockTurned(GameTestHelper helper) {
		Leftovers.clear(helper);
		ServerLevel level = helper.getLevel();
		helper.setDayTime(2000);
		boolean free = level.getGameRules().getBoolean(ModGameRules.FREE_MATERIALS);
		int delay = level.getGameRules().getInt(ModGameRules.BUILD_DELAY);
		boolean help = level.getGameRules().getBoolean(ModGameRules.BUILDERS_HELP);
		Leftovers.after(helper, () -> {
			level.getGameRules().getRule(ModGameRules.FREE_MATERIALS).set(free, level.getServer());
			level.getGameRules().getRule(ModGameRules.BUILD_DELAY).set(delay, level.getServer());
			level.getGameRules().getRule(ModGameRules.BUILDERS_HELP).set(help, level.getServer());
		});
		level.getGameRules().getRule(ModGameRules.FREE_MATERIALS).set(true, level.getServer());
		level.getGameRules().getRule(ModGameRules.BUILD_DELAY).set(1, level.getServer());
		level.getGameRules().getRule(ModGameRules.BUILDERS_HELP).set(false, level.getServer());
		BlockPos bench = new BlockPos(2, 2, 2);
		helper.setBlock(bench, ModBlocks.BUILDERS_BENCH);
		Villager builder = helper.spawn(EntityType.VILLAGER, new BlockPos(3, 2, 3));
		Builders.employ(level, builder, helper.absolutePos(bench));
		Blueprint blueprint = BlueprintLibrary.get(level, StarterBlueprints.STOREHOUSE.id()).orElseThrow();
		Rotation turn = Rotation.CLOCKWISE_90;
		BlockPos corner = helper.absolutePos(new BlockPos(8, 2, 8));
		BoundingBox box = BuildPlan.create(blueprint, new BlueprintData.Placement(level.dimension().location(), corner, turn, Mirror.NONE)).bounds();
		BlockPos origin = corner.offset(corner.getX() - box.minX(), 0, corner.getZ() - box.minZ());
		BlueprintData.Placement placement = new BlueprintData.Placement(level.dimension().location(), origin, turn, Mirror.NONE);
		List<Blueprint.Entry> stores = blueprint.blocks().stream().filter(e -> e.state().is(ModBlocks.STOREHOUSE)).toList();
		helper.assertTrue(!stores.isEmpty(), "the Storehouse blueprint has no Storehouse block to check");
		BuildSite site = Builders.start(level, builder, null, StarterBlueprints.STOREHOUSE.id(), placement);
		helper.assertTrue(site != null, "the builder didn't take the Storehouse");
		helper.succeedWhen(() -> {
			helper.assertTrue(BuildSiteManager.get(level).get(site.id()) == null, "still building: " + site.stage() + " " + site.status());
			for (Blueprint.Entry e : stores) {
				BlockPos at = origin.offset(StructureTemplate.transform(e.pos(), Mirror.NONE, turn, BlockPos.ZERO));
				BlockState there = level.getBlockState(at);
				Direction expected = turn.rotate(e.state().getValue(HorizontalDirectionalBlock.FACING));
				helper.assertTrue(there.is(ModBlocks.STOREHOUSE) && there.getValue(HorizontalDirectionalBlock.FACING) == expected,
					"the turned Storehouse's Storehouse block is " + there + ", expected facing " + expected + " (drawn facing "
						+ e.state().getValue(HorizontalDirectionalBlock.FACING) + ")");
			}
			LOGGER.info("qa turned storehouse: its {} Storehouse block(s) face the way the building was turned", stores.size());
		});
	}
}
