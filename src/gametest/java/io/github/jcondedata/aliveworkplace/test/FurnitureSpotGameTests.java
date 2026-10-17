package io.github.jcondedata.aliveworkplace.test;

import com.google.gson.JsonParser;
import io.github.jcondedata.aliveworkplace.AliveWorkplace;
import io.github.jcondedata.aliveworkplace.blueprint.Blueprint;
import io.github.jcondedata.aliveworkplace.blueprint.BlueprintLibrary;
import io.github.jcondedata.aliveworkplace.blueprint.BlueprintStyles;
import io.github.jcondedata.aliveworkplace.furnish.BlueprintFurniture;
import io.github.jcondedata.aliveworkplace.furnish.FurnitureSpot;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.FenceBlock;
import net.minecraft.world.level.block.StairBlock;
import net.minecraft.world.level.block.state.BlockState;

/** ROADMAP 37.2: the furniture spots written beside our blueprints, read back and checked against the templates. */
public class FurnitureSpotGameTests implements FabricGameTest {
	private static Map<BlockPos, BlockState> blocks(Blueprint blueprint) {
		Map<BlockPos, BlockState> map = new HashMap<>();
		blueprint.blocks().forEach(e -> map.put(e.pos(), e.state()));
		return map;
	}

	/** The Starter Cottage's spots: a chair at its stair seat facing the table, the table at its post, the bed at the bed. */
	//$ gametest 'FabricGameTest.EMPTY_STRUCTURE'
	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
	public void starterCottageSpotsAreWhereItsChairsAndTablesAre(GameTestHelper helper) {
		ResourceLocation id = AliveWorkplace.id("starter_cottage");
		List<FurnitureSpot> spots = BlueprintFurniture.of(id);
		Blueprint blueprint = BlueprintLibrary.get(helper.getLevel(), id).orElseThrow();
		Map<BlockPos, BlockState> template = blocks(blueprint);
		List<String> problems = new ArrayList<>();
		int chairs = 0, tables = 0, beds = 0;
		for (FurnitureSpot spot : spots) {
			BlockState first = template.get(spot.pos());
			switch (spot.kind()) {
				case CHAIR -> {
					chairs++;
					if (first == null || !(first.getBlock() instanceof StairBlock) || first.getValue(StairBlock.FACING) != spot.facing().getOpposite()) {
						problems.add("chair at " + spot.pos() + " is " + first + ", not a stair whose sitter faces " + spot.facing());
					}
					BlockState front = template.get(spot.pos().relative(spot.facing()));
					if (front == null || !(front.getBlock() instanceof FenceBlock)) {
						problems.add("chair at " + spot.pos() + " doesn't face its table: " + front);
					}
				}
				case TABLE -> {
					tables++;
					if (first == null || !(first.getBlock() instanceof FenceBlock)) {
						problems.add("table at " + spot.pos() + " is " + first);
					}
				}
				case BED -> {
					beds++;
					if (first == null || !(first.getBlock() instanceof BedBlock) || spot.blocks().size() != 2) {
						problems.add("bed at " + spot.pos() + " is " + first);
					}
				}
				default -> {
				}
			}
			if (!"spruce".equals(spot.wood())) {
				problems.add(spot.kind().id() + " at " + spot.pos() + " has wood " + spot.wood() + ", the cottage is spruce");
			}
		}
		helper.assertTrue(chairs >= 1 && tables >= 1 && beds >= 1, "the cottage's chair, table and bed: " + spots);
		helper.assertTrue(problems.isEmpty(), String.join("; ", problems));
		helper.succeed();
	}

	/** Every shipped spots file: each spot's blocks are the blocks in its template there (the check generate.py makes, in game). */
	//$ gametest 'FabricGameTest.EMPTY_STRUCTURE'
	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
	public void everyShippedSpotMatchesItsTemplate(GameTestHelper helper) {
		List<String> problems = new ArrayList<>();
		int files = 0;
		for (Map.Entry<ResourceLocation, List<FurnitureSpot>> e : BlueprintFurniture.all().entrySet()) {
			if (!e.getKey().getNamespace().equals(AliveWorkplace.MOD_ID)) {
				continue;
			}
			files++;
			Blueprint blueprint = BlueprintLibrary.get(helper.getLevel(), e.getKey()).orElse(null);
			if (blueprint == null) {
				problems.add(e.getKey() + ": no such blueprint");
				continue;
			}
			Map<BlockPos, BlockState> template = blocks(blueprint);
			for (FurnitureSpot spot : e.getValue()) {
				for (FurnitureSpot.Cell cell : spot.blocks()) {
					BlockState there = template.get(spot.pos().offset(cell.at()));
					if (there == null || there.getBlock() != cell.state().getBlock()) {
						problems.add(e.getKey() + " " + spot.kind().id() + "@" + spot.pos() + ": " + there + " is not " + cell.state());
					}
				}
			}
		}
		helper.assertTrue(files > 50, "only " + files + " of our blueprints have spots files");
		helper.assertTrue(problems.isEmpty(), problems.size() + " mismatches, e.g. " + problems.subList(0, Math.min(5, problems.size())));
		helper.succeed();
	}

	/** A file with a mistake is skipped (and logged with its path); the reader names the mistake. */
	//$ gametest 'FabricGameTest.EMPTY_STRUCTURE'
	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
	public void aBrokenSpotsFileIsSkipped(GameTestHelper helper) {
		helper.assertTrue(!BlueprintFurniture.all().containsKey(ResourceLocation.fromNamespaceAndPath("aliveworkplace_test", "broken")),
			"the test pack's broken spots file was loaded");
		String message = "";
		try {
			BlueprintFurniture.read(JsonParser.parseString("{\"spots\": [{\"kind\": \"throne\", \"pos\": [0, 1, 0], \"facing\": \"north\", \"blocks\": []}]}"));
		} catch (IllegalArgumentException ex) {
			message = ex.getMessage();
		}
		helper.assertTrue(message.contains("unknown kind throne"), "the mistake isn't named: " + message);
		List<FurnitureSpot> good = BlueprintFurniture.read(JsonParser.parseString(
			"{\"spots\": [{\"kind\": \"side_table\", \"pos\": [2, 1, 3], \"facing\": \"east\", \"wood\": \"birch\", \"blocks\": [{\"at\": [0, 0, 0], \"state\": \"minecraft:birch_slab[type=top]\"}]}]}"));
		helper.assertTrue(good.size() == 1 && good.get(0).kind() == FurnitureSpot.Kind.SIDE_TABLE && good.get(0).blocks().get(0).state().is(Blocks.BIRCH_SLAB),
			"a good spot read wrong: " + good);
		helper.succeed();
	}

	/** Styles carry the spots over: the cottage's spruce chair in the Dark Oak style is a dark oak chair. */
	//$ gametest 'FabricGameTest.EMPTY_STRUCTURE'
	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
	public void aStyleCarriesTheSpotsOver(GameTestHelper helper) {
		ResourceLocation styled = BlueprintStyles.styled(AliveWorkplace.id("starter_cottage"), "dark_oak");
		List<FurnitureSpot> spots = BlueprintFurniture.of(styled);
		FurnitureSpot chair = spots.stream().filter(s -> s.kind() == FurnitureSpot.Kind.CHAIR).findFirst().orElse(null);
		helper.assertTrue(chair != null, "no chair in the styled cottage's spots");
		helper.assertTrue("dark_oak".equals(chair.wood()), "the chair's wood is " + chair.wood());
		helper.assertTrue(chair.blocks().get(0).state().is(Blocks.DARK_OAK_STAIRS), "the chair is drawn with " + chair.blocks().get(0).state());
		Blueprint blueprint = BlueprintLibrary.get(helper.getLevel(), styled).orElseThrow();
		BlockState there = blocks(blueprint).get(chair.pos());
		helper.assertTrue(there != null && there.is(Blocks.DARK_OAK_STAIRS), "the styled template has " + there + " at the chair");
		helper.succeed();
	}
}
