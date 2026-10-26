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
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BedPart;
import net.minecraft.world.level.block.state.properties.ChestType;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * QA (lane B), from ROADMAP 23.8 ("rotation and mirroring before placing": a placed blueprint is built turned and
 * flipped as the player chose) and the Steward's rule that a street mirrors its houses (27.x): a build turned any way
 * and mirrored keeps its two-block pieces whole. Every door keeps both halves with one hinge and facing, every bed its
 * head and foot, and every double chest stays a double chest, in all four turns, plain and mirrored.
 */
public class QaMirroredBuildsGameTests implements FabricGameTest {
	private static final String HUGE_AREA = "aliveworkplace_test:huge_area";
	private static final Logger LOGGER = LoggerFactory.getLogger("aliveworkplace-qa");

	//$ gametest 'FabricGameTest.EMPTY_STRUCTURE'
	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
	public void everyBuildsDoorsBedsAndDoubleChestsStayWholeTurnedAndMirrored(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		BlockPos origin = helper.absolutePos(new BlockPos(1, 2, 1)).offset(0, 100, 0);
		List<String> broken = new ArrayList<>();
		java.util.Set<String> spoiled = new java.util.TreeSet<>();
		int builds = 0;
		int pieces = 0;
		for (StarterBlueprints.Entry entry : StarterBlueprints.ALL) {
			Blueprint blueprint = BlueprintLibrary.get(level, entry.id()).orElse(null);
			helper.assertTrue(blueprint != null, "no blueprint " + entry.id());
			builds++;
			for (Mirror mirror : List.of(Mirror.NONE, Mirror.FRONT_BACK)) {
				for (Rotation rotation : Rotation.values()) {
					BlueprintData.Placement placement = new BlueprintData.Placement(level.dimension().location(), origin, rotation, mirror);
					BuildPlan plan = BuildPlan.create(blueprint, placement);
					Map<BlockPos, BlockState> at = new HashMap<>();
					plan.steps(BuildPlan.Stage.CLEAR).forEach(s -> at.put(s.pos(), s.state()));
					for (Map.Entry<BlockPos, BlockState> e : at.entrySet()) {
						String problem = problem(e.getKey(), e.getValue(), at);
						if (problem == null) {
							continue;
						}
						pieces++;
						if (problem.isEmpty()) {
							continue;
						}
						String where = entry.id().getPath() + " " + rotation + (mirror == Mirror.NONE ? "" : " mirrored");
						if (broken.size() < 12) {
							broken.add(where + ": " + e.getKey().subtract(origin).toShortString() + " " + problem);
						}
						spoiled.add(entry.id().getPath() + (mirror == Mirror.NONE ? "" : " (mirrored)"));
					}
				}
			}
		}
		LOGGER.info("qa mirrored builds: {} builds, {} two-block pieces checked in 8 placements each; builds with pieces that come apart: {}",
			builds, pieces, spoiled);
		helper.assertTrue(spoiled.isEmpty(), "builds whose two-block pieces come apart: " + spoiled + "; e.g. " + broken);
		helper.succeed();
	}

	/**
	 * The player's view: the Tinker's Workshop, placed mirrored (the style screen's mirror button, or a Steward's street
	 * mirroring its houses) and built by a real builder, has its double chest as a double chest, like the plain one.
	 */
	//$ gametest_ticks_batch HUGE_AREA '12000' '"qaMirroredChest"'
	@GameTest(template = HUGE_AREA, timeoutTicks = 12000, batch = "qaMirroredChest")
	public void aMirroredTinkersWorkshopIsBuiltWithItsDoubleChestWhole(GameTestHelper helper) {
		Leftovers.clear(helper);
		ServerLevel level = helper.getLevel();
		helper.setDayTime(2000);
		boolean free = level.getGameRules().getBoolean(ModGameRules.FREE_MATERIALS);
		int delay = level.getGameRules().getInt(ModGameRules.BUILD_DELAY);
		Leftovers.after(helper, () -> {
			level.getGameRules().getRule(ModGameRules.FREE_MATERIALS).set(free, level.getServer());
			level.getGameRules().getRule(ModGameRules.BUILD_DELAY).set(delay, level.getServer());
		});
		level.getGameRules().getRule(ModGameRules.FREE_MATERIALS).set(true, level.getServer());
		level.getGameRules().getRule(ModGameRules.BUILD_DELAY).set(1, level.getServer());
		BlockPos bench = new BlockPos(2, 2, 2);
		helper.setBlock(bench, ModBlocks.BUILDERS_BENCH);
		Villager builder = helper.spawn(EntityType.VILLAGER, new BlockPos(3, 2, 3));
		Builders.employ(level, builder, helper.absolutePos(bench));
		BlueprintData.Placement placement = new BlueprintData.Placement(level.dimension().location(), helper.absolutePos(new BlockPos(20, 2, 8)),
			Rotation.NONE, Mirror.FRONT_BACK);
		BuildSite site = Builders.start(level, builder, null, StarterBlueprints.TINKERS_WORKSHOP.id(), placement);
		helper.assertTrue(site != null, "the builder didn't take the mirrored Tinker's Workshop");
		Blueprint blueprint = BlueprintLibrary.get(level, StarterBlueprints.TINKERS_WORKSHOP.id()).orElseThrow();
		List<BlockPos> doubles = blueprint.blocks().stream()
			.filter(e -> e.state().getBlock() instanceof ChestBlock && e.state().getValue(ChestBlock.TYPE) != ChestType.SINGLE)
			.map(e -> placement.origin().offset(StructureTemplate.transform(e.pos(), placement.mirror(), placement.rotation(), BlockPos.ZERO)))
			.toList();
		helper.assertTrue(!doubles.isEmpty(), "the Tinker's Workshop has no double chest to check");
		helper.succeedWhen(() -> {
			helper.assertTrue(BuildSiteManager.get(level).get(site.id()) == null, "still building: " + site.stage() + " " + site.status());
			List<String> split = new ArrayList<>();
			for (BlockPos half : doubles) {
				BlockState state = level.getBlockState(half);
				BlockState partner = state.getBlock() instanceof ChestBlock && state.getValue(ChestBlock.TYPE) != ChestType.SINGLE
					? level.getBlockState(half.relative(ChestBlock.getConnectedDirection(state))) : null;
				boolean whole = partner != null && partner.is(state.getBlock()) && partner.getValue(ChestBlock.TYPE) == state.getValue(ChestBlock.TYPE).getOpposite();
				if (!whole) {
					split.add(half.subtract(placement.origin()).toShortString() + "=" + state + " partner " + partner);
				}
			}
			helper.assertTrue(split.isEmpty(), "the mirrored Tinker's Workshop's double chest came out as two single chests: " + split);
			LOGGER.info("qa mirrored chest: the mirrored Tinker's Workshop is done ({} skipped), its {} double chest halves joined", site.skipped(), doubles.size());
		});
	}

	/** {@code null}: not a two-block piece's first half; empty: whole; otherwise what is wrong. */
	private static String problem(BlockPos pos, BlockState state, Map<BlockPos, BlockState> at) {
		if (state.getBlock() instanceof DoorBlock && state.getValue(DoorBlock.HALF) == DoubleBlockHalf.LOWER) {
			BlockState top = at.get(pos.above());
			if (top == null || !top.is(state.getBlock()) || top.getValue(DoorBlock.HALF) != DoubleBlockHalf.UPPER) {
				return "door without its top: " + top;
			}
			if (top.getValue(DoorBlock.HINGE) != state.getValue(DoorBlock.HINGE) || top.getValue(DoorBlock.FACING) != state.getValue(DoorBlock.FACING)) {
				return "door halves disagree: " + state + " / " + top;
			}
			return "";
		}
		if (state.getBlock() instanceof BedBlock && state.getValue(BedBlock.PART) == BedPart.HEAD) {
			Direction facing = state.getValue(BedBlock.FACING);
			BlockState foot = at.get(pos.relative(facing.getOpposite()));
			if (foot == null || !foot.is(state.getBlock()) || foot.getValue(BedBlock.PART) != BedPart.FOOT || foot.getValue(BedBlock.FACING) != facing) {
				return "bed head facing " + facing + " without its foot behind it: " + foot;
			}
			return "";
		}
		if (state.getBlock() instanceof ChestBlock && state.hasProperty(ChestBlock.TYPE) && state.getValue(ChestBlock.TYPE) == ChestType.LEFT) {
			BlockPos other = pos.relative(ChestBlock.getConnectedDirection(state));
			BlockState partner = at.get(other);
			if (partner == null || !partner.is(state.getBlock()) || partner.getValue(ChestBlock.TYPE) != ChestType.RIGHT
				|| partner.getValue(ChestBlock.FACING) != state.getValue(ChestBlock.FACING)) {
				return "double chest half " + state + " has no right half at " + ChestBlock.getConnectedDirection(state) + ": " + partner;
			}
			return "";
		}
		return null;
	}
}
