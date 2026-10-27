package io.github.jcondedata.aliveworkplace.compat;

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
import io.github.jcondedata.aliveworkplace.hall.HallPages;
import io.github.jcondedata.aliveworkplace.hall.RepairsPage;
import io.github.jcondedata.aliveworkplace.hall.VillageHallScreen;
import io.github.jcondedata.aliveworkplace.hall.VillageHalls;
import io.github.jcondedata.aliveworkplace.registry.ModAttachments;
import io.github.jcondedata.aliveworkplace.registry.ModBlocks;
import io.github.jcondedata.aliveworkplace.work.ChoiceMenu;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockState;

/**
 * B642 with the pack's furniture mods: a Stone House furnished before B260 with Handcrafted's pieces (the spots file of
 * its day, so its table, chairs and clock stand where today's file no longer puts them; Lucky's Cozy Home can't load in
 * the dev run, so the clock is the vanilla stand-in or whatever mod furnishes it here) is refreshed through the hall's
 * Repairs page, turned and mirrored: afterwards it equals a fresh build of the current drawing, its Handcrafted pieces
 * of the old spots gone and today's spots furnished once.
 */
public class RefreshFurnishedCompatTests implements FabricGameTest {
	private static final String HUGE_AREA = "aliveworkplace_compat:huge_area";
	private static final BlockPos HOUSE = new BlockPos(9, 2, 9);
	private static final ResourceLocation STONE_HOUSE = AliveWorkplace.id("stone_house");
	private static final String BEFORE_B260 = "aec09cb26";

	//$ gametest_ticks_batch HUGE_AREA '400' '"b642_handcrafted"'
	@GameTest(template = HUGE_AREA, timeoutTicks = 400, batch = "b642_handcrafted")
	public void aHandcraftedOldStoneHouseIsRefreshedWithoutDoubles(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		helper.assertTrue(BuiltInRegistries.BLOCK.containsKey(ResourceLocation.parse("handcrafted:oak_table")), "Handcrafted isn't in the compat run");
		FurnitureChoice furniture = FurnitureChoice.load(FurnitureChoice.choose(BlueprintFurniture.read(com.google.gson.JsonParser.parseString(OLD_SPOTS)),
			List.of("handcrafted"), true).save());
		helper.assertTrue(furniture.spots().values().stream().anyMatch(p -> p.mod().equals("handcrafted") && p.kind().id().equals("table")),
			"no Handcrafted table in the old furniture: " + furniture);
		ServerPlayer owner = helper.makeMockServerPlayerInLevel();
		BlueprintData.Placement placement = new BlueprintData.Placement(level.dimension().location(), helper.absolutePos(HOUSE), Rotation.CLOCKWISE_90, Mirror.LEFT_RIGHT);
		Blueprint old = Refreshes.olderDrawing(level.getServer(), STONE_HOUSE, BEFORE_B260).orElseThrow();
		Blueprint asBuilt = furniture.applyAsBuilt(old);
		for (Map.Entry<BlockPos, BlockState> e : BuildPlan.create(asBuilt, placement).targets().entrySet()) {
			level.setBlock(e.getKey(), e.getValue(), Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE);
		}
		BlockPos hall = new BlockPos(1, 2, 1);
		helper.setBlock(hall, ModBlocks.VILLAGE_HALL);
		helper.setBlock(new BlockPos(1, 2, 26), Blocks.CHEST);
		helper.setBlock(new BlockPos(3, 2, 26), ModBlocks.BUILDERS_BENCH);
		Villager builder = helper.spawn(EntityType.VILLAGER, new BlockPos(3, 2, 24));
		Builders.employ(level, builder, helper.absolutePos(new BlockPos(3, 2, 26)));
		ModAttachments.BUILDER_EMPLOYER.set(builder, new Employer(owner.getUUID(), owner.getGameProfile().getName()));
		BuildSiteManager manager = BuildSiteManager.get(level);
		manager.recordFinished(STONE_HOUSE, placement, owner.getUUID());
		manager.setFurniture(placement, furniture);
		helper.runAfterDelay(2, () -> {
			int radius = VillageHalls.RADIUS;
			BuildSite site;
			try {
				VillageHalls.RADIUS = 32;
				ChoiceMenu menu = VillageHallScreen.forTest(owner, helper.absolutePos(hall));
				menu.press(HallPages.slot(RepairsPage.PAGE), owner);
				int slot = -1;
				for (int i = VillageHallScreen.FIRST_ROW; i < ChoiceMenu.SIZE && slot < 0; i++) {
					if (menu.icon(i).is(Items.PAINTING) || menu.icon(i).is(Items.SCAFFOLDING)) {
						slot = i;
					}
				}
				helper.assertTrue(slot >= 0, "the old Stone House has no Refresh on the Repairs page");
				menu.press(slot, owner);
				menu.press(RepairsPage.REFRESH, owner);
				site = Refreshes.siteOf(level, placement);
			} finally {
				VillageHalls.RADIUS = radius;
			}
			helper.assertTrue(site != null && BEFORE_B260.equals(site.refreshFrom()), "not refreshed from the drawing it was built from: " + (site == null ? null : site.refreshFrom()));
			Blueprint current = Refreshes.refreshedFurniture(level, STONE_HOUSE, furniture).apply(BlueprintLibrary.get(level, STONE_HOUSE).orElseThrow(), STONE_HOUSE);
			Map<BlockPos, BlockState> now = BuildPlan.create(current, placement).targets();
			BuildPlan plan = site.plan(level);
			for (BuildPlan.Stage stage : List.of(BuildPlan.Stage.CLEAR, BuildPlan.Stage.STRUCTURE, BuildPlan.Stage.DECORATION)) {
				for (BuildPlan.Step step : plan.steps(stage)) {
					level.setBlock(step.pos(), step.state(), Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE);
					if (step.state().getBlock() instanceof BedBlock) {
						BlockPos other = step.pos().relative(BedBlock.getConnectedDirection(step.state()));
						if (now.containsKey(other)) {
							level.setBlock(other, now.get(other), Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE);
						}
					}
				}
			}
			Set<BlockPos> spots = new HashSet<>(BuildPlan.create(asBuilt, placement).targets().keySet());
			spots.addAll(now.keySet());
			List<String> left = new ArrayList<>();
			int tables = 0;
			int freshTables = 0;
			for (BlockPos pos : spots) {
				BlockState world = level.getBlockState(pos);
				BlockState wanted = now.getOrDefault(pos, Blocks.AIR.defaultBlockState());
				if (isTable(world, level.getBlockState(pos.above()))) {
					tables++;
				}
				if (isTable(wanted, now.getOrDefault(pos.above(), Blocks.AIR.defaultBlockState()))) {
					freshTables++;
				}
				if (world.getBlock() instanceof net.minecraft.world.level.block.ChestBlock) {
					continue;
				}
				boolean ok = wanted.isAir() ? world.isAir() || world.canBeReplaced() && world.getFluidState().isEmpty() : MaterialRules.matches(world, wanted);
				if (!ok) {
					left.add(pos.toShortString() + " " + BuiltInRegistries.BLOCK.getKey(world.getBlock()) + " (wanted " + BuiltInRegistries.BLOCK.getKey(wanted.getBlock()) + ")");
				}
			}
			manager.forgetFinished(placement);
			level.getServer().getPlayerList().remove(owner);
			helper.assertTrue(left.isEmpty(), left.size() + " spots differ from a fresh build: " + left.subList(0, Math.min(10, left.size())));
			helper.assertTrue(tables == freshTables, "tables in the refreshed house: " + tables + ", in a fresh build: " + freshTables);
			helper.succeed();
		});
	}

	/** A table: a furniture mod's table block, or the vanilla stand-in (a fence under a pressure plate). */
	private static boolean isTable(BlockState state, BlockState above) {
		ResourceLocation id = BuiltInRegistries.BLOCK.getKey(state.getBlock());
		return !id.getNamespace().equals("minecraft") && id.getPath().endsWith("_table") && !id.getPath().contains("side")
			|| state.getBlock() instanceof net.minecraft.world.level.block.FenceBlock && above.getBlock() instanceof net.minecraft.world.level.block.PressurePlateBlock;
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
