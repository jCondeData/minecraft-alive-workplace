package io.github.jcondedata.aliveworkplace.test;

import com.google.gson.JsonParser;
import io.github.jcondedata.aliveworkplace.WorkplaceConfig;
import io.github.jcondedata.aliveworkplace.blueprint.BlueprintData;
import io.github.jcondedata.aliveworkplace.blueprint.BlueprintItem;
import io.github.jcondedata.aliveworkplace.build.BlueprintSupplies;
import io.github.jcondedata.aliveworkplace.build.BuildPlan;
import io.github.jcondedata.aliveworkplace.build.BuildSite;
import io.github.jcondedata.aliveworkplace.build.BuilderBag;
import io.github.jcondedata.aliveworkplace.build.FurnitureStock;
import io.github.jcondedata.aliveworkplace.build.MaterialList;
import io.github.jcondedata.aliveworkplace.build.SupplyContainers;
import io.github.jcondedata.aliveworkplace.furnish.FurnitureChoice;
import io.github.jcondedata.aliveworkplace.furnish.FurnitureEquivalents;
import io.github.jcondedata.aliveworkplace.furnish.FurnitureSpot;
import io.github.jcondedata.aliveworkplace.furnish.Furnishings;
import io.github.jcondedata.aliveworkplace.registry.ModBlocks;
import io.github.jcondedata.aliveworkplace.registry.ModGameRules;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Vec3i;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Container;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockState;

/**
 * ROADMAP 37.13 without any furniture mod: two test "mods" ({@code aliveworkplace_test_pieces}, whose chair is an
 * upturned stair, and {@code aliveworkplace_test_alt}, whose chair is a block of wood) and a test equivalents file that
 * makes their spruce chairs the same piece prove the rules: the material list shows the piece once with both on hover
 * and counts either; the Builder's furniture switches to whichever piece the chests hold, first in the order when both are
 * there, keeps its piece when neither is or the spot is already built, and stays vanilla with no mod in the order or the
 * switch off. With the real mods: {@code FurnitureAnyModCompatTests}.
 */
public class FurnitureAnyModGameTests implements FabricGameTest {
	private static final String AREA = "aliveworkplace_test:build_area";
	private static final ResourceLocation NOOK = ResourceLocation.fromNamespaceAndPath("aliveworkplace_test", "chair_nook");
	private static final Vec3i NOOK_SIZE = new Vec3i(3, 2, 3);
	private static final String PIECES = "aliveworkplace_test_pieces";
	private static final String ALT = "aliveworkplace_test_alt";
	/** A test mod whose chair is two blocks tall (a pressure plate on each): not the same footprint as the others. */
	private static final String WIDE = "aliveworkplace_test_wide";
	/** A test mod whose spruce chair is the very block {@link #PIECES} uses. */
	private static final String SAME = "aliveworkplace_test_same";
	private static final BlockPos BENCH = new BlockPos(2, 2, 2);
	private static final BlockPos CHEST = new BlockPos(2, 2, 4);
	private static final BlockPos NOOK_AT = new BlockPos(5, 2, 5);
	/** The nook's chair spot, relative to the nook. */
	private static final BlockPos CHAIR = new BlockPos(1, 1, 1);

	private static BuildSite site(GameTestHelper helper) {
		BlueprintData.Placement placement = new BlueprintData.Placement(helper.getLevel().dimension().location(), helper.absolutePos(NOOK_AT),
			Rotation.NONE, Mirror.NONE);
		return new BuildSite(UUID.randomUUID(), UUID.randomUUID(), "test", NOOK, placement);
	}

	/** What the plan builds at the chair. */
	private static BlockState chairStep(BuildSite site, BuildPlan plan) {
		BlockPos at = site.placement().origin().offset(CHAIR);
		for (BuildPlan.Stage stage : List.of(BuildPlan.Stage.STRUCTURE, BuildPlan.Stage.DECORATION)) {
			for (BuildPlan.Step step : plan.steps(stage)) {
				if (step.pos().equals(at)) {
					return step.state();
				}
			}
		}
		return Blocks.AIR.defaultBlockState();
	}

	private static Container chest(GameTestHelper helper, ItemStack... items) {
		helper.setBlock(BENCH, ModBlocks.BUILDERS_BENCH);
		helper.setBlock(CHEST, Blocks.CHEST);
		Container chest = helper.getBlockEntity(CHEST);
		chest.clearContent();
		for (int i = 0; i < items.length; i++) {
			chest.setItem(i, items[i].copy());
		}
		return chest;
	}

	/** Plans a fresh nook site, then lets it restock from the chest; returns the site. */
	private static BuildSite restocked(GameTestHelper helper, Item wanted, boolean[] changed) {
		ServerLevel level = helper.getLevel();
		BuildSite site = site(helper);
		BuildPlan plan = site.plan(level);
		List<BlockPos> supplies = SupplyContainers.find(level, helper.absolutePos(BENCH), plan.bounds());
		changed[0] = FurnitureStock.restock(level, site, BuilderBag.of(List.of()), supplies, wanted);
		return site;
	}

	private static String chairMod(BuildSite site) {
		return site.furniture().spots().isEmpty() ? "vanilla" : site.furniture().spots().values().iterator().next().mod();
	}

	/** The material list names the piece once, "(any furniture mod)", each mod's piece on hover; only mods in the order count. */
	//$ gametest_batch 'FabricGameTest.EMPTY_STRUCTURE' '"furnitureAnyMod"'
	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE, batch = "furnitureAnyMod")
	public void anyModsPieceIsListedOnceWithTheOthersOnHover(GameTestHelper helper) {
		try {
			Furnishings.MODS = List.of(PIECES, ALT);
			helper.assertTrue(FurnitureEquivalents.options(Items.SPRUCE_STAIRS).equals(List.of(Items.SPRUCE_STAIRS, Items.SPRUCE_WOOD)),
				"the spruce chair's options: " + FurnitureEquivalents.options(Items.SPRUCE_STAIRS));
			helper.assertTrue(FurnitureEquivalents.key(Items.SPRUCE_WOOD) == Items.SPRUCE_STAIRS, "the alt chair isn't listed as the first mod's");
			helper.assertTrue(FurnitureEquivalents.options(Items.OAK_STAIRS).equals(List.of(Items.OAK_STAIRS, Items.OAK_WOOD)),
				"an oak chair is matched by its own wood: " + FurnitureEquivalents.options(Items.OAK_STAIRS));
			helper.assertTrue(FurnitureEquivalents.options(Items.OAK_PLANKS).equals(List.of(Items.OAK_PLANKS)), "planks became furniture");

			Component name = FurnitureEquivalents.name(Items.SPRUCE_STAIRS);
			helper.assertTrue(name.getString().equals("Spruce Stairs (any furniture mod)"), "the name: " + name.getString());
			HoverEvent hover = name.getStyle().getHoverEvent();
			Component shown = hover == null ? null : hover.getValue(HoverEvent.Action.SHOW_TEXT);
			helper.assertTrue(shown != null && shown.getString().equals("Any of these will do:\nSpruce Stairs (" + PIECES + ")\nSpruce Wood (" + ALT + ")"),
				"the hover: " + (shown == null ? null : shown.getString().replace("\n", " / ")));

			BlueprintSupplies.Checklist list = new BlueprintSupplies.Checklist(Optional.empty(), List.of(new BlueprintSupplies.Line(Items.SPRUCE_STAIRS, 2, 2)));
			List<String> pages = MaterialList.pages(Component.literal("Chair Nook"), list).stream().map(Component::getString).toList();
			helper.assertTrue(pages.get(1).equals("☐ 2 × Spruce Stairs (any furniture mod)"), "the list page: " + pages.get(1));

			// The mods' names as a player reads them
			Map<String, String> names = Map.of("handcrafted", "Handcrafted", "cozyhome", "Lucky's Cozy Home", "cobblefurnies", "CobbleFurnies", "beautify", "Beautify");
			names.forEach((mod, shownName) -> helper.assertTrue(FurnitureEquivalents.modName(mod).getString().equals(shownName),
				mod + " reads " + FurnitureEquivalents.modName(mod).getString()));

			// Only one of them in the order: no choice, so the piece's own name
			Furnishings.MODS = List.of(PIECES);
			helper.assertTrue(FurnitureEquivalents.options(Items.SPRUCE_STAIRS).equals(List.of(Items.SPRUCE_STAIRS)), "a mod outside the order is offered");
			helper.assertTrue(FurnitureEquivalents.name(Items.SPRUCE_STAIRS).getString().equals("Spruce Stairs"), "named for one mod: "
				+ FurnitureEquivalents.name(Items.SPRUCE_STAIRS).getString());
			// Furnished builds off: nothing is furniture
			WorkplaceConfig.parse("{\"furnishedBuilds\": false, \"furnitureMods\": [\"" + PIECES + "\", \"" + ALT + "\"]}").apply();
			helper.assertTrue(FurnitureEquivalents.options(Items.SPRUCE_STAIRS).equals(List.of(Items.SPRUCE_STAIRS)), "furniture with the switch off");
		} finally {
			new WorkplaceConfig().apply();
		}
		helper.succeed();
	}

	/**
	 * The Builder's furniture follows the chest: only the other mod's chair there, the spot switches to it (its own table's
	 * piece, the same footprint) and the plan builds it; both, the first in the order stays; neither, the planned piece
	 * stays (the builder waits for it); only the planned one, nothing changes.
	 */
	//$ gametest_batch AREA '"furnitureAnyMod"'
	@GameTest(template = AREA, batch = "furnitureAnyMod")
	public void theBuildersChairFollowsTheChest(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		boolean[] changed = new boolean[1];
		try {
			Furnishings.MODS = List.of(PIECES, ALT);
			BuildSite planned = site(helper);
			BlockState first = chairStep(planned, planned.plan(level));
			helper.assertTrue(first.is(Blocks.SPRUCE_STAIRS) && first.getValue(net.minecraft.world.level.block.StairBlock.HALF)
				== net.minecraft.world.level.block.state.properties.Half.TOP, "the first mod's chair isn't planned: " + first);

			chest(helper, new ItemStack(Items.SPRUCE_WOOD));
			BuildSite onlyAlt = restocked(helper, Items.SPRUCE_STAIRS, changed);
			helper.assertTrue(changed[0] && chairMod(onlyAlt).equals(ALT), "only the alt chair in the chest: " + chairMod(onlyAlt));
			BlockState built = chairStep(onlyAlt, onlyAlt.plan(level));
			helper.assertTrue(built.is(Blocks.SPRUCE_WOOD), "the plan builds " + built + " at the chair");
			// The switched piece is saved with the site and comes back after a reload
			BuildSite loaded = BuildSite.load(onlyAlt.save());
			helper.assertTrue(loaded != null && chairMod(loaded).equals(ALT) && chairStep(loaded, loaded.plan(level)).is(Blocks.SPRUCE_WOOD),
				"after a reload the chair is " + (loaded == null ? null : chairMod(loaded)));

			chest(helper, new ItemStack(Items.SPRUCE_WOOD), new ItemStack(Items.SPRUCE_STAIRS));
			BuildSite both = restocked(helper, Items.SPRUCE_STAIRS, changed);
			helper.assertTrue(!changed[0] && chairMod(both).equals(PIECES), "both in the chest: " + chairMod(both));

			chest(helper, new ItemStack(Items.OAK_PLANKS, 9));
			BuildSite neither = restocked(helper, Items.SPRUCE_STAIRS, changed);
			helper.assertTrue(!changed[0] && chairMod(neither).equals(PIECES), "neither in the chest: " + chairMod(neither));

			chest(helper, new ItemStack(Items.SPRUCE_STAIRS));
			BuildSite own = restocked(helper, Items.SPRUCE_STAIRS, changed);
			helper.assertTrue(!changed[0] && chairMod(own).equals(PIECES), "the planned chair in the chest: " + chairMod(own));

			// Planks aren't furniture: nothing to switch
			chest(helper, new ItemStack(Items.SPRUCE_WOOD));
			BuildSite planks = restocked(helper, Items.OAK_PLANKS, changed);
			helper.assertTrue(!changed[0] && chairMod(planks).equals(PIECES), "switched for planks: " + chairMod(planks));

			// The other order: the alt chair is planned, only the stair in the chest, so it becomes the stair
			Furnishings.MODS = List.of(ALT, PIECES);
			chest(helper, new ItemStack(Items.SPRUCE_STAIRS));
			BuildSite reversed = restocked(helper, Items.SPRUCE_WOOD, changed);
			helper.assertTrue(changed[0] && chairMod(reversed).equals(PIECES), "the other way round: " + chairMod(reversed));
		} finally {
			new WorkplaceConfig().apply();
		}
		helper.succeed();
	}

	/**
	 * A half-furnished build keeps its pieces: a chair already standing isn't switched. With no mod in the order the
	 * spot is the vanilla stand-in whatever the chest holds, and with furnished builds off nothing switches.
	 */
	//$ gametest_batch AREA '"furnitureAnyMod"'
	@GameTest(template = AREA, batch = "furnitureAnyMod")
	public void aBuiltChairAndTheStandInStayAsTheyAre(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		boolean[] changed = new boolean[1];
		try {
			Furnishings.MODS = List.of(PIECES, ALT);
			chest(helper, new ItemStack(Items.SPRUCE_WOOD));
			BuildSite site = site(helper);
			BuildPlan plan = site.plan(level);
			BlockState chair = chairStep(site, plan);
			level.setBlockAndUpdate(site.placement().origin().offset(CHAIR), chair); // the stair chair is up already
			List<BlockPos> supplies = SupplyContainers.find(level, helper.absolutePos(BENCH), plan.bounds());
			changed[0] = FurnitureStock.restock(level, site, BuilderBag.of(List.of()), supplies, Items.SPRUCE_STAIRS);
			helper.assertTrue(!changed[0] && chairMod(site).equals(PIECES), "a chair already built was switched to " + chairMod(site));
			level.setBlockAndUpdate(site.placement().origin().offset(CHAIR), Blocks.AIR.defaultBlockState());

			// The builder's bag counts like the chests: the alt chair in the bag, none in the chest
			chest(helper);
			BuildSite bagged = site(helper);
			BuildPlan baggedPlan = bagged.plan(level);
			BuilderBag bag = BuilderBag.of(List.of(new ItemStack(Items.SPRUCE_WOOD)));
			changed[0] = FurnitureStock.restock(level, bagged, bag, SupplyContainers.find(level, helper.absolutePos(BENCH), baggedPlan.bounds()), Items.SPRUCE_STAIRS);
			helper.assertTrue(changed[0] && chairMod(bagged).equals(ALT), "the alt chair in the bag: " + chairMod(bagged));

			Furnishings.MODS = List.of();
			chest(helper, new ItemStack(Items.SPRUCE_WOOD), new ItemStack(Items.SPRUCE_STAIRS));
			BuildSite vanilla = restocked(helper, Items.SPRUCE_STAIRS, changed);
			BlockState standIn = chairStep(vanilla, vanilla.plan(level));
			helper.assertTrue(!changed[0] && vanilla.furniture().isVanilla() && standIn.is(Blocks.SPRUCE_STAIRS)
				&& standIn.getValue(net.minecraft.world.level.block.StairBlock.HALF) == net.minecraft.world.level.block.state.properties.Half.BOTTOM,
				"no mod in the order: " + standIn);

			WorkplaceConfig.parse("{\"furnishedBuilds\": false, \"furnitureMods\": [\"" + PIECES + "\", \"" + ALT + "\"]}").apply();
			chest(helper, new ItemStack(Items.SPRUCE_WOOD));
			BuildSite off = restocked(helper, Items.SPRUCE_STAIRS, changed);
			helper.assertTrue(!changed[0] && off.furniture().isVanilla(), "switched off: " + chairMod(off));
		} finally {
			new WorkplaceConfig().apply();
		}
		helper.succeed();
	}

	/** The material list of a placed nook counts the alt chair in the chest as the chair: nothing to bring. */
	//$ gametest_ticks_batch AREA '40' '"furnitureAnyMod"'
	@GameTest(template = AREA, timeoutTicks = 40, batch = "furnitureAnyMod")
	public void theMaterialListCountsAnyModsPiece(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		level.getGameRules().getRule(ModGameRules.FREE_MATERIALS).set(false, level.getServer());
		helper.setBlock(BENCH, ModBlocks.BLUEPRINT_TABLE);
		helper.setBlock(CHEST, Blocks.CHEST);
		Container chest = helper.getBlockEntity(CHEST);
		chest.setItem(0, new ItemStack(Items.OAK_PLANKS, 9));
		chest.setItem(1, new ItemStack(Items.SPRUCE_WOOD));
		BlueprintData.Placement placement = new BlueprintData.Placement(level.dimension().location(), helper.absolutePos(NOOK_AT), Rotation.NONE, Mirror.NONE);
		BlueprintData data = BlueprintItem.data(BlueprintItem.create(NOOK, NOOK_SIZE)).orElseThrow().withPlacement(Optional.of(placement));
		helper.runAfterDelay(2, () -> { // the table becomes a point of interest a moment after it's placed
			try {
				Furnishings.MODS = List.of(PIECES, ALT);
				BlueprintSupplies.Checklist list = BlueprintSupplies.checklist(level, data).orElseThrow();
				helper.assertTrue(list.bench().isPresent(), "no table found");
				BlueprintSupplies.Line chair = list.lines().stream().filter(l -> l.item() == Items.SPRUCE_STAIRS).findFirst().orElse(null);
				helper.assertTrue(chair != null && chair.need() == 1 && chair.toBring() == 0, "the chair line: " + list.lines());
				helper.assertTrue(list.lines().stream().noneMatch(l -> l.item() == Items.SPRUCE_WOOD), "the alt chair listed apart: " + list.lines());
				helper.assertTrue(list.toBring() == 0, "still to bring: " + list.lines());
				List<String> pages = MaterialList.pages(Component.literal("Chair Nook"), list).stream().map(Component::getString).toList();
				helper.assertTrue(pages.get(1).contains("☑ Spruce Stairs (any furniture mod)"), "the list page: " + pages.get(1));
				// The alt mod out of the order: its chair doesn't count
				Furnishings.MODS = List.of(PIECES);
				BlueprintSupplies.Checklist without = BlueprintSupplies.checklist(level, data).orElseThrow();
				helper.assertTrue(without.toBring() == 1, "the alt chair counted without its mod in the order: " + without.lines());
			} finally {
				new WorkplaceConfig().apply();
			}
			helper.succeed();
		});
	}

	private static FurnitureSpot spruceChair(int x) {
		return new FurnitureSpot(FurnitureSpot.Kind.CHAIR, new BlockPos(x, 1, 1), Direction.SOUTH, "spruce", null,
			List.of(new FurnitureSpot.Cell(BlockPos.ZERO, Blocks.SPRUCE_STAIRS.defaultBlockState())));
	}

	private static String mods(FurnitureChoice choice) {
		return choice.spots().get(0).mod() + "," + choice.spots().get(1).mod();
	}

	/**
	 * Two chair spots share what the chests hold, one chair each: one alt chair goes to the first spot only (the second
	 * keeps its planned piece and waits); two go to both; one of each gets both spots built (the stair to the first, the
	 * alt chair to the second); a spot already going up passes the chair on to the next. A choice whose spots don't match
	 * the blueprint's (or that has more spots than it) is left alone rather than switched or crashing.
	 */
	//$ gametest_batch 'FabricGameTest.EMPTY_STRUCTURE' '"furnitureAnyMod"'
	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE, batch = "furnitureAnyMod")
	public void twoChairSpotsShareTheChestsChairsOneEach(GameTestHelper helper) {
		try {
			Furnishings.MODS = List.of(PIECES, ALT);
			List<FurnitureSpot> spots = List.of(spruceChair(1), spruceChair(3));
			FurnitureChoice planned = FurnitureChoice.choose(spots, Furnishings.MODS, true);
			helper.assertTrue(planned.spots().size() == 2 && mods(planned).equals(PIECES + "," + PIECES), "planned: " + planned.spots());
			java.util.function.IntPredicate none = i -> false;

			FurnitureChoice one = planned.restocked(spots, Furnishings.MODS, none, Map.of(Items.SPRUCE_WOOD, 1L));
			helper.assertTrue(mods(one).equals(ALT + "," + PIECES), "one alt chair in the chest: " + mods(one));
			helper.assertTrue(one.spots().get(0).cells().get(BlockPos.ZERO).is(Blocks.SPRUCE_WOOD), "spot 0 builds " + one.spots().get(0).cells());

			FurnitureChoice two = planned.restocked(spots, Furnishings.MODS, none, Map.of(Items.SPRUCE_WOOD, 2L));
			helper.assertTrue(mods(two).equals(ALT + "," + ALT), "two alt chairs in the chest: " + mods(two));

			FurnitureChoice eachOne = planned.restocked(spots, Furnishings.MODS, none, Map.of(Items.SPRUCE_WOOD, 1L, Items.SPRUCE_STAIRS, 1L));
			helper.assertTrue(mods(eachOne).equals(PIECES + "," + ALT), "one stair chair and one alt chair: " + mods(eachOne));

			FurnitureChoice firstStarted = planned.restocked(spots, Furnishings.MODS, i -> i == 0, Map.of(Items.SPRUCE_WOOD, 1L));
			helper.assertTrue(mods(firstStarted).equals(PIECES + "," + ALT), "the first spot going up already: " + mods(firstStarted));

			FurnitureChoice moved = planned.restocked(List.of(spruceChair(2), spruceChair(4)), Furnishings.MODS, none, Map.of(Items.SPRUCE_WOOD, 2L));
			helper.assertTrue(moved == planned, "spots that aren't the blueprint's were switched: " + mods(moved));
			FurnitureChoice shorter = planned.restocked(List.of(spruceChair(1)), Furnishings.MODS, none, Map.of(Items.SPRUCE_WOOD, 2L));
			helper.assertTrue(mods(shorter).equals(ALT + "," + PIECES), "a spot past the blueprint's: " + mods(shorter));
		} finally {
			new WorkplaceConfig().apply();
		}
		helper.succeed();
	}

	/**
	 * Only a set's own piece is ever switched, and only to one with the same footprint: a spot holding something that
	 * isn't its set's piece (as CobbleFurnies' Poké Ball chair isn't its spruce chair) keeps it, and a two-block chair
	 * doesn't take a one-block chair's place even when it's all the chest holds. Two mods with the very same block are
	 * one option, offered and listed once.
	 */
	//$ gametest_batch 'FabricGameTest.EMPTY_STRUCTURE' '"furnitureAnyMod"'
	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE, batch = "furnitureAnyMod")
	public void onlyTheSetsOwnPieceSwitchesAndOnlyToTheSameFootprint(GameTestHelper helper) {
		try {
			Furnishings.MODS = List.of(PIECES, ALT);
			java.util.function.IntPredicate none = i -> false;
			FurnitureChoice special = new FurnitureChoice(Map.of(0, new FurnitureChoice.Placed(new BlockPos(1, 1, 1), FurnitureSpot.Kind.CHAIR, PIECES,
				Map.of(BlockPos.ZERO, Blocks.OAK_LOG.defaultBlockState()))));
			FurnitureChoice after = special.restocked(List.of(spruceChair(1)), Furnishings.MODS, none, Map.of(Items.SPRUCE_WOOD, 1L));
			helper.assertTrue(after == special, "a piece outside its set was switched to " + after.spots());

			List<FurnitureSpot> tall = List.of(new FurnitureSpot(FurnitureSpot.Kind.CHAIR, new BlockPos(1, 1, 1), Direction.SOUTH, "spruce", null,
				List.of(new FurnitureSpot.Cell(BlockPos.ZERO, Blocks.SPRUCE_STAIRS.defaultBlockState()),
					new FurnitureSpot.Cell(new BlockPos(0, 1, 0), Blocks.SPRUCE_PLANKS.defaultBlockState()))));
			long wideCells = FurnitureChoice.choose(tall, List.of(WIDE), true).spots().get(0).cells().values().stream().filter(s -> !s.isAir()).count();
			helper.assertTrue(wideCells == 2, "the two-block chair fills " + wideCells + " cells");
			Furnishings.MODS = List.of(PIECES, WIDE);
			FurnitureChoice planned = FurnitureChoice.choose(tall, Furnishings.MODS, true);
			helper.assertTrue(planned.spots().get(0).mod().equals(PIECES), "planned: " + planned.spots());
			FurnitureChoice wide = planned.restocked(tall, Furnishings.MODS, none, Map.of(Items.SPRUCE_PRESSURE_PLATE, 2L));
			helper.assertTrue(wide == planned, "a two-block chair took a one-block chair's place: " + wide.spots());

			Furnishings.MODS = List.of(PIECES, SAME, ALT);
			helper.assertTrue(FurnitureEquivalents.options(Items.SPRUCE_STAIRS).equals(List.of(Items.SPRUCE_STAIRS, Items.SPRUCE_WOOD)),
				"two mods' same block offered twice: " + FurnitureEquivalents.options(Items.SPRUCE_STAIRS));
			HoverEvent hover = FurnitureEquivalents.name(Items.SPRUCE_STAIRS).getStyle().getHoverEvent();
			Component shown = hover == null ? null : hover.getValue(HoverEvent.Action.SHOW_TEXT);
			helper.assertTrue(shown != null && shown.getString().equals("Any of these will do:\nSpruce Stairs (" + PIECES + ")\nSpruce Wood (" + ALT + ")"),
				"the hover: " + (shown == null ? null : shown.getString().replace("\n", " / ")));
		} finally {
			new WorkplaceConfig().apply();
		}
		helper.succeed();
	}

	/**
	 * Only a new build follows the chest (37.13): a repair, a taking-down and a cellar dig keep the pieces their building
	 * has, and so does a build planned with furnished builds on once the switch is turned off (a config change never
	 * changes a build that is going up, 37.3), whatever chair the chest holds. The same site as a new build switches.
	 */
	//$ gametest_batch AREA '"furnitureAnyMod"'
	@GameTest(template = AREA, batch = "furnitureAnyMod")
	public void onlyANewBuildsChairFollowsTheChest(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		String switchOff = "{\"furnishedBuilds\": false, \"furnitureMods\": [\"" + PIECES + "\", \"" + ALT + "\"]}";
		Map<String, java.util.function.Consumer<BuildSite>> jobs = new java.util.LinkedHashMap<>();
		jobs.put("a new build", site -> {
		});
		jobs.put("a repair", BuildSite::setRepair);
		jobs.put("a taking-down", BuildSite::setDeconstruction);
		jobs.put("a cellar dig", BuildSite::setCellarOnly);
		jobs.put("a build with furnished builds since turned off", site -> WorkplaceConfig.parse(switchOff).apply());
		try {
			chest(helper, new ItemStack(Items.SPRUCE_WOOD));
			jobs.forEach((job, turn) -> {
				new WorkplaceConfig().apply();
				Furnishings.MODS = List.of(PIECES, ALT);
				BuildSite site = site(helper);
				BuildPlan plan = site.plan(level);
				helper.assertTrue(chairMod(site).equals(PIECES), job + " planned " + chairMod(site));
				List<BlockPos> supplies = SupplyContainers.find(level, helper.absolutePos(BENCH), plan.bounds());
				turn.accept(site);
				boolean changed = FurnitureStock.restock(level, site, BuilderBag.of(List.of()), supplies, Items.SPRUCE_STAIRS);
				boolean switches = job.equals("a new build");
				helper.assertTrue(changed == switches && chairMod(site).equals(switches ? ALT : PIECES),
					job + " with the alt chair in the chest: switched " + changed + ", the chair is " + chairMod(site));
			});
		} finally {
			new WorkplaceConfig().apply();
		}
		helper.succeed();
	}

	/**
	 * A bed spot only follows the chest to a bed villagers can sleep in (B166 with 37.13): the test pack's bed set makes
	 * {@code aliveworkplace_test_plank_bed}'s two blocks of red wool the same piece as a red bed, with the same footprint.
	 * Wool in the chest never takes the planned bed's place, first in the order or not, and a bed in the chest keeps it.
	 */
	//$ gametest_batch 'FabricGameTest.EMPTY_STRUCTURE' '"furnitureAnyMod"'
	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE, batch = "furnitureAnyMod")
	public void aBedSpotOnlyFollowsTheChestToABedVillagersSleepIn(GameTestHelper helper) {
		String beds = "aliveworkplace_test_beds";
		String plankBed = "aliveworkplace_test_plank_bed";
		try {
			List<FurnitureSpot.Cell> cells = new java.util.ArrayList<>();
			for (int dx = -1; dx <= 1; dx++) {
				for (int dz = -1; dz <= 1; dz++) {
					cells.add(new FurnitureSpot.Cell(new BlockPos(dx, 0, dz), Blocks.AIR.defaultBlockState()));
				}
			}
			List<FurnitureSpot> spots = List.of(new FurnitureSpot(FurnitureSpot.Kind.BED, new BlockPos(1, 1, 1), Direction.SOUTH, null, "red", cells));
			FurnitureChoice planned = FurnitureChoice.choose(spots, List.of(beds), true);
			FurnitureChoice.Placed bed = planned.spots().get(0);
			helper.assertTrue(bed != null && bed.mod().equals(beds) && FurnitureChoice.sleepable(bed.cells())
				&& bed.cells().values().stream().anyMatch(s -> s.is(Blocks.RED_BED)), "planned: " + planned.spots());
			FurnitureEquivalents.Group group = FurnitureEquivalents.of(spots.get(0)).orElse(null);
			helper.assertTrue(group != null && group.block(plankBed) == Blocks.RED_WOOL, "the test bed set: " + group);
			java.util.function.IntPredicate none = i -> false;

			Furnishings.MODS = List.of(plankBed, beds);
			FurnitureChoice wool = planned.restocked(spots, Furnishings.MODS, none, Map.of(Items.RED_WOOL, 4L));
			helper.assertTrue(wool == planned, "wool first in the order took the bed's place: " + wool.spots());
			FurnitureChoice both = planned.restocked(spots, Furnishings.MODS, none, Map.of(Items.RED_WOOL, 4L, Items.RED_BED, 1L));
			helper.assertTrue(both == planned, "with a bed and wool in the chest the bed went: " + both.spots());
			Furnishings.MODS = List.of(beds, plankBed);
			FurnitureChoice after = planned.restocked(spots, Furnishings.MODS, none, Map.of(Items.RED_WOOL, 4L));
			helper.assertTrue(after == planned, "wool second in the order took the bed's place: " + after.spots());
		} finally {
			new WorkplaceConfig().apply();
		}
		helper.succeed();
	}

	/** The equivalents file: every shipped kind is a real kind with two or more mods; a broken file is skipped and named. */
	//$ gametest_batch 'FabricGameTest.EMPTY_STRUCTURE' '"furnitureAnyMod"'
	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE, batch = "furnitureAnyMod")
	public void theEquivalentsFileIsReadAndCheckedWhenBroken(GameTestHelper helper) {
		Map<FurnitureSpot.Kind, FurnitureEquivalents.Piece> all = FurnitureEquivalents.all();
		for (FurnitureSpot.Kind kind : List.of(FurnitureSpot.Kind.CHAIR, FurnitureSpot.Kind.TABLE, FurnitureSpot.Kind.CUPBOARD, FurnitureSpot.Kind.LAMP)) {
			helper.assertTrue(all.containsKey(kind), "no " + kind.id() + " in the table: " + all.keySet());
		}
		helper.assertTrue(all.get(FurnitureSpot.Kind.CHAIR).mods().keySet().containsAll(List.of("handcrafted", "cozyhome", "cobblefurnies", PIECES, ALT)),
			"the chair's mods (the test pack's file adds two): " + all.get(FurnitureSpot.Kind.CHAIR).mods().keySet());
		helper.assertTrue(all.get(FurnitureSpot.Kind.LAMP).byColour() && !all.get(FurnitureSpot.Kind.CHAIR).byColour(), "lamps go by colour, chairs by wood");
		for (FurnitureEquivalents.Piece piece : all.values()) {
			helper.assertTrue(new HashSet<>(piece.mods().keySet()).size() >= 2, piece.kind().id() + " has one mod: nothing to swap");
		}
		Map<FurnitureSpot.Kind, FurnitureEquivalents.Piece> into = new EnumMap<>(FurnitureSpot.Kind.class);
		for (String[] bad : new String[][] {
			{"{\"pieces\": {\"throne\": {\"mods\": {\"a\": \"a:b\"}}}}", "unknown kind throne"},
			{"{\"pieces\": {\"chair\": {\"by\": \"size\", \"mods\": {\"a\": \"a:b\"}}}}", "chair: by size is not wood or colour"},
			{"{\"pieces\": {\"chair\": {\"by\": \"wood\"}}}", "chair: no \"mods\" object"},
			{"{}", "no \"pieces\" object"}}) {
			String message = null;
			try {
				FurnitureEquivalents.read(JsonParser.parseString(bad[0]), into, new HashSet<>(), new HashSet<>());
			} catch (IllegalArgumentException e) {
				message = e.getMessage();
			}
			helper.assertTrue(bad[1].equals(message), "for " + bad[0] + " the reader said " + message);
		}
		// Two files matching the same kind different ways: the second is refused
		FurnitureEquivalents.read(JsonParser.parseString("{\"pieces\": {\"lamp\": {\"by\": \"colour\", \"mods\": {\"a\": \"a:{colour}_lamp\"}}}}"), into, new HashSet<>(), new HashSet<>());
		String clash = null;
		try {
			FurnitureEquivalents.read(JsonParser.parseString("{\"pieces\": {\"lamp\": {\"by\": \"wood\", \"mods\": {\"b\": \"b:{wood}_lamp\"}}}}"), into, new HashSet<>(), new HashSet<>());
		} catch (IllegalArgumentException e) {
			clash = e.getMessage();
		}
		helper.assertTrue("lamp: matched by wood here and the other way in another file".equals(clash), "the clash: " + clash);
		helper.succeed();
	}
}
