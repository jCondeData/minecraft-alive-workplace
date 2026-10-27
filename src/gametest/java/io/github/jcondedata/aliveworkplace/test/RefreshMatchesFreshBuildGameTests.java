package io.github.jcondedata.aliveworkplace.test;

import io.github.jcondedata.aliveworkplace.AliveWorkplace;
import io.github.jcondedata.aliveworkplace.blueprint.Blueprint;
import io.github.jcondedata.aliveworkplace.blueprint.BlueprintData;
import io.github.jcondedata.aliveworkplace.blueprint.BlueprintLibrary;
import io.github.jcondedata.aliveworkplace.build.BuildPlan;
import io.github.jcondedata.aliveworkplace.build.BuildSite;
import io.github.jcondedata.aliveworkplace.build.BuildSiteManager;
import io.github.jcondedata.aliveworkplace.build.Builders;
import io.github.jcondedata.aliveworkplace.build.Employer;
import io.github.jcondedata.aliveworkplace.build.MaterialRules;
import io.github.jcondedata.aliveworkplace.build.Refreshes;
import io.github.jcondedata.aliveworkplace.furnish.BlueprintFurniture;
import io.github.jcondedata.aliveworkplace.furnish.FurnitureChoice;
import io.github.jcondedata.aliveworkplace.furnish.FurnitureSpot;
import io.github.jcondedata.aliveworkplace.hall.HallPages;
import io.github.jcondedata.aliveworkplace.hall.RepairsPage;
import io.github.jcondedata.aliveworkplace.hall.VillageHallScreen;
import io.github.jcondedata.aliveworkplace.hall.VillageHalls;
import io.github.jcondedata.aliveworkplace.registry.ModAttachments;
import io.github.jcondedata.aliveworkplace.registry.ModBlocks;
import io.github.jcondedata.aliveworkplace.work.ChoiceMenu;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.ListTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockState;

/**
 * B642: "Refresh to the latest look" (36.21) on a Stone House built from an older drawing must leave exactly what a
 * fresh build of the current drawing would (chests, beds slept in, job blocks and players' blocks aside): every old
 * piece the new drawing doesn't have gone, nothing doubled. Through the hall's Repairs page, as the owner presses it,
 * on a house built before B260 (the drawing on the live server), turned and mirrored too, and with the furniture the
 * build was furnished with when the spots file of its day put pieces where today's no longer does.
 */
public class RefreshMatchesFreshBuildGameTests implements FabricGameTest {
	private static final String HUGE = "aliveworkplace_test:huge_area";
	private static final BlockPos HOUSE = new BlockPos(9, 2, 9);
	private static final ResourceLocation STONE_HOUSE = AliveWorkplace.id("stone_house");
	/** The Stone House as drawn before B260 redrew its rooms (what the owner's house was built from). */
	static final String BEFORE_B260 = "aec09cb26";

	/** Every spot where the build differs from a fresh build of {@code current} (chests, beds and job blocks aside). */
	static List<String> differences(ServerLevel level, BlueprintData.Placement placement, Blueprint old, Blueprint current) {
		Map<BlockPos, BlockState> was = BuildPlan.create(old, placement).targets();
		Map<BlockPos, BlockState> now = BuildPlan.create(current, placement).targets();
		Set<BlockPos> spots = new HashSet<>(was.keySet());
		spots.addAll(now.keySet());
		List<String> out = new ArrayList<>();
		for (BlockPos pos : spots) {
			BlockState world = level.getBlockState(pos);
			BlockState wanted = now.getOrDefault(pos, Blocks.AIR.defaultBlockState());
			if (world.getBlock() instanceof net.minecraft.world.level.block.ChestBlock
				|| net.minecraft.world.entity.ai.village.poi.PoiTypes.forState(world).isPresent() && !(world.getBlock() instanceof net.minecraft.world.level.block.BedBlock)) {
				continue;
			}
			boolean ok = wanted.isAir() ? world.isAir() || world.canBeReplaced() && world.getFluidState().isEmpty() : MaterialRules.matches(world, wanted);
			if (!ok) {
				out.add(pos.toShortString() + " " + world.getBlock().getDescriptionId().replace("block.minecraft.", "") + " (wanted "
					+ wanted.getBlock().getDescriptionId().replace("block.minecraft.", "") + ")");
			}
		}
		return out;
	}

	private record Stage(ServerLevel level, ServerPlayer owner, BlueprintData.Placement placement, Blueprint old, BlockPos hall, List<BlockPos> stores) {
	}

	/** A Stone House standing as {@code oldKey} drew it (furnished as {@code furniture}), a hall, a Builder and their stores. */
	private static Stage stage(GameTestHelper helper, String oldKey, Rotation rotation, Mirror mirror, FurnitureChoice furniture) {
		Leftovers.clear(helper);
		Leftovers.finished(helper);
		ServerLevel level = helper.getLevel();
		int radius = VillageHalls.RADIUS;
		VillageHalls.RADIUS = 32;
		helper.setDayTime(2000);
		helper.onEachTick(() -> {
			if (level.getDayTime() % 24000 > 9000) {
				helper.setDayTime(2000);
			}
		});
		ServerPlayer owner = helper.makeMockServerPlayerInLevel();
		Leftovers.after(helper, () -> {
			VillageHalls.RADIUS = radius;
			level.getServer().getPlayerList().remove(owner);
		});
		BlueprintData.Placement placement = new BlueprintData.Placement(level.dimension().location(), helper.absolutePos(HOUSE), rotation, mirror);
		Blueprint old = Refreshes.olderDrawing(level.getServer(), STONE_HOUSE, oldKey)
			.orElseThrow(() -> new IllegalStateException("the Stone House's drawing " + oldKey + " isn't shipped"));
		Blueprint built = furniture.applyAsBuilt(old);
		for (Map.Entry<BlockPos, BlockState> e : BuildPlan.create(built, placement).targets().entrySet()) {
			level.setBlock(e.getKey(), e.getValue(), Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE);
		}
		BlockPos hall = new BlockPos(1, 2, 1);
		helper.setBlock(hall, ModBlocks.VILLAGE_HALL);
		List<BlockPos> stores = List.of(new BlockPos(1, 2, 26), new BlockPos(1, 2, 28), new BlockPos(5, 2, 26), new BlockPos(5, 2, 28));
		stores.forEach(c -> helper.setBlock(c, Blocks.CHEST));
		helper.setBlock(new BlockPos(3, 2, 26), ModBlocks.BUILDERS_BENCH);
		Villager builder = helper.spawn(EntityType.VILLAGER, new BlockPos(3, 2, 24));
		Builders.employ(level, builder, helper.absolutePos(new BlockPos(3, 2, 26)));
		ModAttachments.BUILDER_EMPLOYER.set(builder, new Employer(owner.getUUID(), owner.getGameProfile().getName()));
		BuildSiteManager manager = BuildSiteManager.get(level);
		manager.recordFinished(STONE_HOUSE, placement, owner.getUUID());
		if (!furniture.isVanilla()) {
			manager.setFurniture(placement, furniture);
		}
		Leftovers.after(helper, () -> BuildSiteManager.get(level).forgetFinished(placement));
		return new Stage(level, owner, placement, old, helper.absolutePos(hall), stores);
	}

	/** The owner presses Refresh on the hall's Repairs page (twice: what changes, then the button); returns the started refresh. */
	private static BuildSite pressRefresh(GameTestHelper helper, Stage s) {
		ChoiceMenu menu = VillageHallScreen.forTest(s.owner(), s.hall());
		menu.press(HallPages.slot(RepairsPage.PAGE), s.owner());
		int slot = -1;
		for (int i = VillageHallScreen.FIRST_ROW; i < ChoiceMenu.SIZE && slot < 0; i++) {
			if (menu.icon(i).is(Items.PAINTING) || menu.icon(i).is(Items.SCAFFOLDING)) {
				slot = i;
			}
		}
		helper.assertTrue(slot >= 0, "the old Stone House has no Refresh on the Repairs page");
		menu.press(slot, s.owner());
		menu.press(RepairsPage.REFRESH, s.owner());
		BuildSite site = Refreshes.siteOf(s.level(), s.placement());
		helper.assertTrue(site != null, "no Builder took the refresh up");
		return site;
	}

	/** Fills the stores with what {@code plan} takes. */
	private static void stock(GameTestHelper helper, Stage s, BuildPlan plan) {
		int at = 0;
		for (Map.Entry<net.minecraft.world.item.Item, Integer> need : plan.materials().entrySet()) {
			for (int left = need.getValue() + 2; left > 0 && at < 27 * s.stores().size(); at++) {
				int n = Math.min(left, need.getKey().getDefaultMaxStackSize());
				Container store = helper.getBlockEntity(s.stores().get(at / 27));
				store.setItem(at % 27, new ItemStack(need.getKey(), n));
				left -= n;
			}
		}
	}

	/** The current Stone House as this build would be built now (its furniture brought to today's spots). */
	private static Blueprint currentFor(Stage s) {
		Blueprint base = BlueprintLibrary.get(s.level(), STONE_HOUSE).orElseThrow();
		return Refreshes.refreshedFurniture(s.level(), STONE_HOUSE, BuildSiteManager.get(s.level()).furniture(s.placement())).apply(base, STONE_HOUSE);
	}

	/**
	 * The refresh's plan, carried out at once block by block (the Builder's own work is {@link #aBuilderRefreshesTheOldStoneHouseToAFreshBuild}):
	 * afterwards the house equals a fresh build of the current drawing.
	 */
	private static void plannedRefreshEqualsAFreshBuild(GameTestHelper helper, String oldKey, Rotation rotation, Mirror mirror, FurnitureChoice furniture) {
		Stage s = stage(helper, oldKey, rotation, mirror, furniture);
		Blueprint asBuilt = furniture.applyAsBuilt(s.old());
		helper.runAfterDelay(2, () -> {
			helper.assertTrue(!differences(s.level(), s.placement(), asBuilt, currentFor(s)).isEmpty(), "the old house already equals the current drawing");
			BuildSite site = pressRefresh(helper, s);
			helper.assertTrue(oldKey.equals(site.refreshFrom()), "refreshed from the wrong drawing: " + site.refreshFrom() + ", built from " + oldKey);
			BuildPlan plan = site.plan(s.level());
			Map<BlockPos, BlockState> now = BuildPlan.create(currentFor(s), s.placement()).targets();
			for (BuildPlan.Stage stage : List.of(BuildPlan.Stage.CLEAR, BuildPlan.Stage.STRUCTURE, BuildPlan.Stage.DECORATION)) {
				for (BuildPlan.Step step : plan.steps(stage)) {
					s.level().setBlock(step.pos(), step.state(), Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE);
					if (step.state().getBlock() instanceof net.minecraft.world.level.block.BedBlock) {
						// (as the Builder puts a bed: both halves at once)
						BlockPos other = step.pos().relative(net.minecraft.world.level.block.BedBlock.getConnectedDirection(step.state()));
						if (now.containsKey(other)) {
							s.level().setBlock(other, now.get(other), Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE);
						}
					}
				}
			}
			List<String> left = differences(s.level(), s.placement(), asBuilt, currentFor(s));
			helper.assertTrue(left.isEmpty(), left.size() + " spots differ from a fresh build: " + left.subList(0, Math.min(10, left.size())));
			helper.succeed();
		});
	}

	//$ gametest_ticks_batch HUGE '400' '"b642_plain"'
	@GameTest(template = HUGE, timeoutTicks = 400, batch = "b642_plain")
	public void aRefreshedOldStoneHouseEqualsAFreshBuild(GameTestHelper helper) {
		plannedRefreshEqualsAFreshBuild(helper, BEFORE_B260, Rotation.NONE, Mirror.NONE, FurnitureChoice.VANILLA);
	}

	//$ gametest_ticks_batch HUGE '400' '"b642_turned"'
	@GameTest(template = HUGE, timeoutTicks = 400, batch = "b642_turned")
	public void aTurnedOldStoneHouseIsRefreshedToAFreshBuild(GameTestHelper helper) {
		plannedRefreshEqualsAFreshBuild(helper, BEFORE_B260, Rotation.CLOCKWISE_90, Mirror.NONE, FurnitureChoice.VANILLA);
	}

	//$ gametest_ticks_batch HUGE '400' '"b642_mirrored"'
	@GameTest(template = HUGE, timeoutTicks = 400, batch = "b642_mirrored")
	public void aMirroredTurnedOldStoneHouseIsRefreshedToAFreshBuild(GameTestHelper helper) {
		plannedRefreshEqualsAFreshBuild(helper, BEFORE_B260, Rotation.COUNTERCLOCKWISE_90, Mirror.LEFT_RIGHT, FurnitureChoice.VANILLA);
	}

	/** Built from each older drawing the mod ships (the first one of 2026-09-29 on): refreshed to a fresh build. */
	//$ gametest_ticks_batch HUGE '400' '"b642_first"'
	@GameTest(template = HUGE, timeoutTicks = 400, batch = "b642_first")
	public void theFirstStoneHouseIsRefreshedToAFreshBuild(GameTestHelper helper) {
		plannedRefreshEqualsAFreshBuild(helper, "25c521dcd", Rotation.NONE, Mirror.NONE, FurnitureChoice.VANILLA);
	}

	//$ gametest_ticks_batch HUGE '400' '"b642_redrawn"'
	@GameTest(template = HUGE, timeoutTicks = 400, batch = "b642_redrawn")
	public void theRedrawnStoneHouseIsRefreshedToAFreshBuild(GameTestHelper helper) {
		plannedRefreshEqualsAFreshBuild(helper, "11ef1ff39", Rotation.CLOCKWISE_90, Mirror.NONE, FurnitureChoice.VANILLA);
	}

	//$ gametest_ticks_batch HUGE '400' '"b642_exterior"'
	@GameTest(template = HUGE, timeoutTicks = 400, batch = "b642_exterior")
	public void theOwnExteriorStoneHouseIsRefreshedToAFreshBuild(GameTestHelper helper) {
		plannedRefreshEqualsAFreshBuild(helper, "42864073a", Rotation.NONE, Mirror.FRONT_BACK, FurnitureChoice.VANILLA);
	}

	/**
	 * Furnished before B260 and turned and mirrored: the pieces of its day come away from where they stand, and
	 * afterwards the saved choice is today's (a second refresh has nothing to change).
	 */
	//$ gametest_ticks_batch HUGE '400' '"b642_furnished_mirrored"'
	@GameTest(template = HUGE, timeoutTicks = 400, batch = "b642_furnished_mirrored")
	public void aFurnishedMirroredOldStoneHouseIsRefreshedWithoutDoubles(GameTestHelper helper) {
		plannedRefreshEqualsAFreshBuild(helper, BEFORE_B260, Rotation.CLOCKWISE_90, Mirror.LEFT_RIGHT, oldFurniture());
	}

	/**
	 * Furnished when the spots file of its day put the table, clock and beds where today's doesn't: the pieces it was
	 * furnished with (saved by index, against the old spots) are its own and come away, and today's spots are furnished
	 * anew, so nothing is doubled. The save is the old one: written and read back as the live server keeps it.
	 */
	//$ gametest_ticks_batch HUGE '400' '"b642_furnished"'
	@GameTest(template = HUGE, timeoutTicks = 400, batch = "b642_furnished")
	public void aFurnishedOldStoneHouseIsRefreshedWithoutDoubles(GameTestHelper helper) {
		plannedRefreshEqualsAFreshBuild(helper, BEFORE_B260, Rotation.CLOCKWISE_180, Mirror.NONE, oldFurniture());
	}

	/**
	 * The furniture of a Stone House furnished before B260: today's choice of pieces for the spots file as it was then
	 * (each spot's pieces where that file put them), saved and loaded back as an old save has it.
	 */
	static FurnitureChoice oldFurniture() {
		List<FurnitureSpot> spots = oldSpots();
		FurnitureChoice chosen = FurnitureChoice.choose(spots, List.of(FurnishingTableGameTests.TEST_TABLE), true);
		ListTag saved = chosen.save();
		return FurnitureChoice.load(saved);
	}

	/** The Stone House's spots file before B260 (git show aec09cb26^), the spots whose places moved since. */
	static List<FurnitureSpot> oldSpots() {
		return BlueprintFurniture.read(com.google.gson.JsonParser.parseString(OLD_SPOTS));
	}

	/**
	 * The Builder works the refresh of a house furnished before B260 through, turned and mirrored: the house ends as a
	 * fresh build, and its saved furniture is today's, so the page offers no second refresh.
	 */
	//$ gametest_ticks_batch HUGE '12000' '"b642_builder"'
	@GameTest(template = HUGE, timeoutTicks = 12000, batch = "b642_builder")
	public void aBuilderRefreshesTheOldStoneHouseToAFreshBuild(GameTestHelper helper) {
		FurnitureChoice furniture = oldFurniture();
		Stage s = stage(helper, BEFORE_B260, Rotation.CLOCKWISE_90, Mirror.FRONT_BACK, furniture);
		Blueprint asBuilt = furniture.applyAsBuilt(s.old());
		BuildPlan[] plan = {null};
		helper.runAfterDelay(2, () -> {
			BuildSite site = pressRefresh(helper, s);
			plan[0] = site.plan(s.level());
			stock(helper, s, plan[0]);
		});
		helper.succeedWhen(() -> {
			helper.assertTrue(plan[0] != null, "not started");
			helper.assertTrue(Refreshes.siteOf(s.level(), s.placement()) == null, "still refreshing");
			List<String> left = differences(s.level(), s.placement(), asBuilt, currentFor(s));
			helper.assertTrue(left.isEmpty(), left.size() + " spots differ from a fresh build: " + left.subList(0, Math.min(10, left.size())));
			BuildSiteManager.Finished f = BuildSiteManager.get(s.level()).finishedIn(s.level()).stream()
				.filter(b -> b.placement().equals(s.placement())).findFirst().orElseThrow();
			helper.assertTrue(Refreshes.change(s.level(), f).isEmpty(), "the refreshed house still offers a refresh: " + Refreshes.change(s.level(), f));
		});
	}

	private static final String OLD_SPOTS = """
{"spots": [
  {"kind": "clock", "pos": [6, 1, 2], "facing": "south", "wood": "dark_oak", "blocks": [{"at": [0, 0, 0], "state": "minecraft:stripped_dark_oak_log[axis=y]"}, {"at": [0, 1, 0], "state": "minecraft:dark_oak_trapdoor[facing=south,half=bottom,open=false,powered=false,waterlogged=false]"}]},
  {"kind": "chair", "pos": [3, 1, 3], "facing": "east", "wood": "spruce", "blocks": [{"at": [0, 0, 0], "state": "minecraft:spruce_stairs[facing=west,half=bottom,shape=straight,waterlogged=false]"}]},
  {"kind": "table", "pos": [4, 1, 3], "facing": "north", "wood": "dark_oak", "blocks": [{"at": [0, 0, 0], "state": "minecraft:dark_oak_fence[east=false,north=false,south=false,waterlogged=false,west=false]"}, {"at": [0, 1, 0], "state": "minecraft:dark_oak_pressure_plate[powered=false]"}]},
  {"kind": "chair", "pos": [5, 1, 3], "facing": "west", "wood": "spruce", "blocks": [{"at": [0, 0, 0], "state": "minecraft:spruce_stairs[facing=east,half=bottom,shape=straight,waterlogged=false]"}]},
  {"kind": "bed", "pos": [7, 1, 5], "facing": "south", "wood": "spruce", "colour": "red", "blocks": [{"at": [0, 0, 0], "state": "minecraft:red_bed[facing=south,occupied=false,part=foot]"}, {"at": [0, 0, 1], "state": "minecraft:red_bed[facing=south,occupied=false,part=head]"}]},
  {"kind": "lamp", "pos": [6, 1, 6], "facing": "north", "wood": "dark_oak", "blocks": [{"at": [0, 0, 0], "state": "minecraft:dark_oak_fence[east=false,north=false,south=true,west=false]"}, {"at": [0, 1, 0], "state": "minecraft:lantern[hanging=false,waterlogged=false]"}]},
  {"kind": "nightstand", "pos": [8, 1, 6], "facing": "north", "wood": "dark_oak", "blocks": [{"at": [0, 0, 0], "state": "minecraft:dark_oak_slab[type=top,waterlogged=false]"}]},
  {"kind": "shelf", "pos": [4, 2, 6], "facing": "north", "wood": "dark_oak", "blocks": [{"at": [0, 0, 0], "state": "minecraft:dark_oak_slab[type=top,waterlogged=false]"}]},
  {"kind": "blinds", "pos": [2, 3, 2], "facing": "south", "wood": "dark_oak", "blocks": [{"at": [0, 0, 0], "state": "minecraft:dark_oak_trapdoor[facing=south,half=top,open=false,powered=false,waterlogged=false]"}]},
  {"kind": "blinds", "pos": [3, 3, 2], "facing": "south", "wood": "dark_oak", "blocks": [{"at": [0, 0, 0], "state": "minecraft:dark_oak_trapdoor[facing=south,half=top,open=false,powered=false,waterlogged=false]"}]},
  {"kind": "blinds", "pos": [7, 3, 2], "facing": "south", "wood": "dark_oak", "blocks": [{"at": [0, 0, 0], "state": "minecraft:dark_oak_trapdoor[facing=south,half=top,open=false,powered=false,waterlogged=false]"}]},
  {"kind": "blinds", "pos": [8, 3, 2], "facing": "south", "wood": "dark_oak", "blocks": [{"at": [0, 0, 0], "state": "minecraft:dark_oak_trapdoor[facing=south,half=top,open=false,powered=false,waterlogged=false]"}]},
  {"kind": "picture", "pos": [8, 3, 5], "facing": "west", "wood": "dark_oak", "colour": "red", "blocks": [{"at": [0, 0, 0], "state": "minecraft:red_wall_banner[facing=west]"}]},
  {"kind": "blinds", "pos": [3, 3, 6], "facing": "north", "wood": "dark_oak", "blocks": [{"at": [0, 0, 0], "state": "minecraft:dark_oak_trapdoor[facing=north,half=top,open=false,powered=false,waterlogged=false]"}]},
  {"kind": "crockery", "pos": [4, 3, 6], "facing": "north", "wood": "dark_oak", "colour": "white", "blocks": [{"at": [0, 0, 0], "state": "minecraft:flower_pot"}]},
  {"kind": "blinds", "pos": [7, 3, 6], "facing": "north", "wood": "dark_oak", "blocks": [{"at": [0, 0, 0], "state": "minecraft:dark_oak_trapdoor[facing=north,half=top,open=false,powered=false,waterlogged=false]"}]},
  {"kind": "bed", "pos": [3, 6, 4], "facing": "east", "wood": "spruce", "colour": "light_blue", "blocks": [{"at": [0, 0, 0], "state": "minecraft:light_blue_bed[facing=east,occupied=false,part=foot]"}, {"at": [1, 0, 0], "state": "minecraft:light_blue_bed[facing=east,occupied=false,part=head]"}]}
]}
""";
}
