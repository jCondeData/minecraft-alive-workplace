package io.github.jcondedata.aliveworkplace.test;

import io.github.jcondedata.aliveworkplace.blueprint.Blueprint;
import io.github.jcondedata.aliveworkplace.blueprint.BlueprintLibrary;
import io.github.jcondedata.aliveworkplace.blueprint.Blueprints;
import io.github.jcondedata.aliveworkplace.blueprint.StarterBlueprints;
import io.github.jcondedata.aliveworkplace.hall.Decorations;
import io.github.jcondedata.aliveworkplace.table.BlueprintUnlocks;
import java.util.List;
import java.util.stream.Collectors;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.ai.village.poi.PoiTypes;
import net.minecraft.world.level.block.state.BlockState;

/**
 * The decorations of ROADMAP 26.14 (tools/blueprints/decor.py, MORE_DECORATIONS): each one a builder builds and finishes
 * from exactly the materials its plan asks for, through the same path as the Fountain's test; none holds a block a
 * villager takes a job or a meeting from; each is a decoration (in the Table's list, worth beauty, named in the lang
 * file); the Flower Planters start unlocked and the rest have a way to get them.
 */
public class MoreDecorationsGameTests implements FabricGameTest {
	private static final String BIG_AREA = "aliveworkplace_test:big_area";
	private static final BlockPos ORIGIN = new BlockPos(9, 2, 9);

	/** No POI block (a job block, a bell, a bed, a beehive) in any of them: a decoration never hands out a job. */
	//$ gametest 'FabricGameTest.EMPTY_STRUCTURE'
	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
	public void moreDecorationsHoldNoJobBlock(GameTestHelper helper) {
		helper.assertTrue(StarterBlueprints.MORE_DECORATIONS.size() >= 12, "only " + StarterBlueprints.MORE_DECORATIONS.size() + " new decorations");
		for (StarterBlueprints.Entry entry : StarterBlueprints.MORE_DECORATIONS) {
			Blueprint blueprint = BlueprintLibrary.get(helper.getLevel(), entry.id()).orElseThrow();
			List<String> pois = blueprint.blocks().stream().map(Blueprint.Entry::state).filter(s -> PoiTypes.forState(s).isPresent())
				.map(BlockState::toString).distinct().collect(Collectors.toList());
			helper.assertTrue(pois.isEmpty(), entry.id() + " holds villager POI blocks: " + pois);
		}
		helper.succeed();
	}

	/** Each is in the decorations list (the Table, the creative tab, market day), worth beauty and has a name. */
	//$ gametest 'FabricGameTest.EMPTY_STRUCTURE'
	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
	public void moreDecorationsAreDecorationsWithBeautyAndAName(GameTestHelper helper) {
		for (StarterBlueprints.Entry entry : StarterBlueprints.MORE_DECORATIONS) {
			helper.assertTrue(StarterBlueprints.DECORATIONS.contains(entry), entry.id() + " isn't in DECORATIONS");
			helper.assertTrue(Decorations.points(entry.id()) >= 1, entry.id() + " adds no beauty");
			String name = Blueprints.displayName(entry.id()).getString();
			helper.assertTrue(!name.startsWith("blueprint."), entry.id() + " has no name in the lang file: " + name);
			helper.assertTrue(entry.size().getX() <= 9 && entry.size().getZ() <= 9 && entry.size().getY() <= 8,
				entry.id() + " is too big for a decoration: " + entry.size());
		}
		helper.assertTrue(Decorations.points(StarterBlueprints.TOPIARY_GARDEN.id()) == 3 && Decorations.points(StarterBlueprints.SIGNPOST.id()) == 1,
			"beauty: topiary " + Decorations.points(StarterBlueprints.TOPIARY_GARDEN.id()) + ", signpost " + Decorations.points(StarterBlueprints.SIGNPOST.id()));
		helper.succeed();
	}

	/** The Flower Planters start unlocked; every other new one starts locked with a way to get it (market day). */
	//$ gametest 'FabricGameTest.EMPTY_STRUCTURE'
	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
	public void flowerPlantersStartUnlockedTheRestComeFromMarketDay(GameTestHelper helper) {
		helper.assertTrue(BlueprintUnlocks.starting().contains(StarterBlueprints.FLOWER_PLANTERS.id()), "the Flower Planters don't start unlocked");
		for (StarterBlueprints.Entry entry : StarterBlueprints.MORE_DECORATIONS) {
			if (entry == StarterBlueprints.FLOWER_PLANTERS) {
				continue;
			}
			helper.assertTrue(!BlueprintUnlocks.starting().contains(entry.id()), entry.id() + " starts unlocked");
			helper.assertTrue(BlueprintUnlocks.hint(entry.id()).equals("screen.aliveworkplace.table.hint.market"),
				entry.id() + " says " + BlueprintUnlocks.hint(entry.id()));
		}
		helper.succeed();
	}

	// Each built by a builder from its plan's materials and finished (the Fountain's test, BuilderGameTests).
	//$ gametest_ticks_batch BIG_AREA '9000' '"decorations_2614a"'
	@GameTest(template = BIG_AREA, timeoutTicks = 9000, batch = "decorations_2614a")
	public void buildsMarketCross(GameTestHelper helper) {
		BuilderGameTests.buildStarter(helper, StarterBlueprints.MARKET_CROSS, ORIGIN);
	}

	//$ gametest_ticks_batch BIG_AREA '9000' '"decorations_2614a"'
	@GameTest(template = BIG_AREA, timeoutTicks = 9000, batch = "decorations_2614a")
	public void buildsStagStatue(GameTestHelper helper) {
		BuilderGameTests.buildStarter(helper, StarterBlueprints.STAG_STATUE, ORIGIN);
	}

	//$ gametest_ticks_batch BIG_AREA '6000' '"decorations_2614a"'
	@GameTest(template = BIG_AREA, timeoutTicks = 6000, batch = "decorations_2614a")
	public void buildsFlowerPlanters(GameTestHelper helper) {
		BuilderGameTests.buildStarter(helper, StarterBlueprints.FLOWER_PLANTERS, ORIGIN);
	}

	//$ gametest_ticks_batch BIG_AREA '6000' '"decorations_2614a"'
	@GameTest(template = BIG_AREA, timeoutTicks = 6000, batch = "decorations_2614a")
	public void buildsSignpost(GameTestHelper helper) {
		BuilderGameTests.buildStarter(helper, StarterBlueprints.SIGNPOST, ORIGIN);
	}

	//$ gametest_ticks_batch BIG_AREA '9000' '"decorations_2614a"'
	@GameTest(template = BIG_AREA, timeoutTicks = 9000, batch = "decorations_2614a")
	public void buildsStoneArchway(GameTestHelper helper) {
		BuilderGameTests.buildStarter(helper, StarterBlueprints.STONE_ARCHWAY, ORIGIN);
	}

	//$ gametest_ticks_batch BIG_AREA '12000' '"decorations_2614a"'
	@GameTest(template = BIG_AREA, timeoutTicks = 12000, batch = "decorations_2614a")
	public void buildsLilyPond(GameTestHelper helper) {
		BuilderGameTests.buildStarter(helper, StarterBlueprints.LILY_POND, ORIGIN);
	}

	//$ gametest_ticks_batch BIG_AREA '6000' '"decorations_2614a"'
	@GameTest(template = BIG_AREA, timeoutTicks = 6000, batch = "decorations_2614a")
	public void buildsHayCart(GameTestHelper helper) {
		BuilderGameTests.buildStarter(helper, StarterBlueprints.HAY_CART, ORIGIN);
	}

	//$ gametest_ticks_batch BIG_AREA '6000' '"decorations_2614b"'
	@GameTest(template = BIG_AREA, timeoutTicks = 6000, batch = "decorations_2614b")
	public void buildsNoticeBoard(GameTestHelper helper) {
		BuilderGameTests.buildStarter(helper, StarterBlueprints.NOTICE_BOARD, ORIGIN);
	}

	//$ gametest_ticks_batch BIG_AREA '12000' '"decorations_2614b"'
	@GameTest(template = BIG_AREA, timeoutTicks = 12000, batch = "decorations_2614b")
	public void buildsLanternPergola(GameTestHelper helper) {
		BuilderGameTests.buildStarter(helper, StarterBlueprints.LANTERN_PERGOLA, ORIGIN);
	}

	//$ gametest_ticks_batch BIG_AREA '9000' '"decorations_2614b"'
	@GameTest(template = BIG_AREA, timeoutTicks = 9000, batch = "decorations_2614b")
	public void buildsFirePit(GameTestHelper helper) {
		BuilderGameTests.buildStarter(helper, StarterBlueprints.FIRE_PIT, ORIGIN);
	}

	//$ gametest_ticks_batch BIG_AREA '9000' '"decorations_2614b"'
	@GameTest(template = BIG_AREA, timeoutTicks = 9000, batch = "decorations_2614b")
	public void buildsSundial(GameTestHelper helper) {
		BuilderGameTests.buildStarter(helper, StarterBlueprints.SUNDIAL, ORIGIN);
	}

	//$ gametest_ticks_batch BIG_AREA '12000' '"decorations_2614b"'
	@GameTest(template = BIG_AREA, timeoutTicks = 12000, batch = "decorations_2614b")
	public void buildsTopiaryGarden(GameTestHelper helper) {
		BuilderGameTests.buildStarter(helper, StarterBlueprints.TOPIARY_GARDEN, ORIGIN);
	}

	//$ gametest_ticks_batch BIG_AREA '9000' '"decorations_2614b"'
	@GameTest(template = BIG_AREA, timeoutTicks = 9000, batch = "decorations_2614b")
	public void buildsMarketCorner(GameTestHelper helper) {
		BuilderGameTests.buildStarter(helper, StarterBlueprints.MARKET_CORNER, ORIGIN);
	}

	//$ gametest_ticks_batch BIG_AREA '9000' '"decorations_2614b"'
	@GameTest(template = BIG_AREA, timeoutTicks = 9000, batch = "decorations_2614b")
	public void buildsFootbridge(GameTestHelper helper) {
		BuilderGameTests.buildStarter(helper, StarterBlueprints.FOOTBRIDGE, ORIGIN);
	}
}
