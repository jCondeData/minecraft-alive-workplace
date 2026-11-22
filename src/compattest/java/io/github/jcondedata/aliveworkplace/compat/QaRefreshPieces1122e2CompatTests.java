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
import io.github.jcondedata.aliveworkplace.platform.Platform;
import io.github.jcondedata.aliveworkplace.registry.ModAttachments;
import io.github.jcondedata.aliveworkplace.registry.ModBlocks;
import io.github.jcondedata.aliveworkplace.work.ChoiceMenu;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.structure.BoundingBox;

/**
 * QA lane E (2026-11-22, second tester), B1383 from its spec, with Handcrafted: a Stone House furnished with Handcrafted
 * before the rebuild is refreshed through the Village Hall's Repairs page. The refresh takes away the old Handcrafted
 * pieces the build's own furniture put there and holds nothing (the shelf, nightstand and crockery that stayed before the
 * fix), but "never ... one holding items": a piece with something in it stays with its items, and so does a piece the
 * player set down themselves. The started refresh goes through a save and load (its site and the village's build records)
 * and plans the same. RefreshFurnishedCompatTests builds the house turned and mirrored with empty pieces; these build it
 * straight, and turned half round.
 */
public class QaRefreshPieces1122e2CompatTests implements FabricGameTest {
	private static final String HUGE_AREA = "aliveworkplace_compat:huge_area";
	private static final BlockPos HOUSE = new BlockPos(9, 2, 9);
	private static final ResourceLocation STONE_HOUSE = AliveWorkplace.id("stone_house");
	/** RefreshFurnishedCompatTests' drawing from before B260, whose furniture spots its OLD_SPOTS are. */
	private static final String BEFORE_B260 = "aec09cb26";

	/** The old house standing, its refresh started from the hall and reloaded; {@code plan} is the reloaded site's. */
	private record Refresh(Map<BlockPos, BlockState> asBuilt, Map<BlockPos, BlockState> now, BuildPlan plan, BuildPlan planBeforeReload) {
	}

	/** The old Handcrafted Stone House at {@code placement}, with {@code before} run on it before the refresh is started. */
	private static void refresh(GameTestHelper helper, BlockPos house, Rotation rotation, Mirror mirror,
								java.util.function.BiConsumer<Map<BlockPos, BlockState>, Map<BlockPos, BlockState>> before,
								java.util.function.Consumer<Refresh> check) {
		ServerLevel level = helper.getLevel();
		helper.assertTrue(BuiltInRegistries.BLOCK.containsKey(ResourceLocation.parse("handcrafted:oak_drawer")), "Handcrafted isn't in the compat run");
		FurnitureChoice furniture = FurnitureChoice.load(FurnitureChoice.choose(BlueprintFurniture.read(com.google.gson.JsonParser.parseString(OLD_SPOTS)),
			List.of("handcrafted"), true).save());
		ServerPlayer owner = helper.makeMockServerPlayerInLevel();
		BlueprintData.Placement placement = new BlueprintData.Placement(level.dimension().location(), helper.absolutePos(house), rotation, mirror);
		Blueprint old = Refreshes.olderDrawing(level.getServer(), STONE_HOUSE, BEFORE_B260).orElseThrow();
		Map<BlockPos, BlockState> asBuilt = BuildPlan.create(furniture.applyAsBuilt(old), placement).targets();
		for (Map.Entry<BlockPos, BlockState> e : asBuilt.entrySet()) {
			level.setBlock(e.getKey(), e.getValue(), Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE);
		}
		Blueprint current = Refreshes.refreshedFurniture(level, STONE_HOUSE, furniture).apply(BlueprintLibrary.get(level, STONE_HOUSE).orElseThrow(), STONE_HOUSE);
		Map<BlockPos, BlockState> now = BuildPlan.create(current, placement).targets();
		before.accept(asBuilt, now);
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
			try {
				helper.assertTrue(site != null && BEFORE_B260.equals(site.refreshFrom()),
					"not refreshed from the drawing it was built from: " + (site == null ? null : site.refreshFrom()));
				BuildPlan beforeReload = site.plan(level);
				// the world saved and opened again: the village's build records and the refresh's site, as the save holds them
				CompoundTag records = manager.save(new CompoundTag(), level.registryAccess());
				BuildSiteManager reloadedRecords = BuildSiteManager.load(records, level.registryAccess());
				helper.assertTrue(furniture.equals(reloadedRecords.furniture(placement)),
					"the old house's Handcrafted furniture didn't come back with the save: " + reloadedRecords.furniture(placement));
				BuildSite reloaded = BuildSite.load(site.save());
				helper.assertTrue(BEFORE_B260.equals(reloaded.refreshFrom()), "the reloaded refresh forgot the drawing it was built from: " + reloaded.refreshFrom());
				BuildPlan plan = reloaded.plan(level);
				helper.assertTrue(plan != null, "the reloaded refresh can't be planned");
				check.accept(new Refresh(asBuilt, now, plan, beforeReload));
			} finally {
				manager.forgetFinished(placement);
				level.getServer().getPlayerList().remove(owner);
			}
			helper.succeed();
		});
	}

	/** The positions each stage of {@code plan} changes. */
	private static Set<BlockPos> steps(BuildPlan plan, BuildPlan.Stage stage) {
		Set<BlockPos> out = new HashSet<>();
		for (BuildPlan.Step step : plan.steps(stage)) {
			out.add(step.pos());
		}
		return out;
	}

	/** Lays the plan's clearing, structure and decoration as a builder would, leaving {@code untouched} alone. */
	private static void lay(ServerLevel level, Refresh r, Set<BlockPos> untouched) {
		for (BuildPlan.Stage stage : List.of(BuildPlan.Stage.CLEAR, BuildPlan.Stage.STRUCTURE, BuildPlan.Stage.DECORATION)) {
			for (BuildPlan.Step step : r.plan().steps(stage)) {
				if (untouched.contains(step.pos())) {
					continue; // a builder never breaks a piece holding items (it is skipped like a container)
				}
				level.setBlock(step.pos(), step.state(), Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE);
				if (step.state().getBlock() instanceof BedBlock) {
					BlockPos other = step.pos().relative(BedBlock.getConnectedDirection(step.state()));
					if (r.now().containsKey(other)) {
						level.setBlock(other, r.now().get(other), Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE);
					}
				}
			}
		}
	}

	private static String id(BlockState state) {
		return BuiltInRegistries.BLOCK.getKey(state.getBlock()).toString();
	}

	/**
	 * Built straight: of the old Handcrafted pieces today's drawing doesn't want, every other one that can hold items is
	 * given a stack. Those stay where they are with their items (no clearing step reaches them); the empty ones are cleared
	 * and the spot ends as a fresh build has it; a Handcrafted drawer the player set down in an empty corner of the house
	 * stays too. The plan is the reloaded site's, and it clears the same blocks the plan before the save did.
	 */
	//$ gametest_ticks_batch HUGE_AREA '400' '"qa1122e2_pieces_items"'
	@GameTest(template = HUGE_AREA, timeoutTicks = 400, batch = "qa1122e2_pieces_items")
	public void anOldHandcraftedPieceHoldingItemsStaysThroughARefresh(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		List<BlockPos> holding = new ArrayList<>();
		List<BlockPos> empty = new ArrayList<>();
		BlockPos[] drawer = new BlockPos[1];
		refresh(helper, HOUSE, Rotation.NONE, Mirror.NONE, (asBuilt, now) -> {
			List<BlockPos> leftovers = new ArrayList<>();
			for (Map.Entry<BlockPos, BlockState> e : asBuilt.entrySet()) {
				BlockState piece = e.getValue();
				if (id(piece).startsWith("handcrafted:") && !(piece.getBlock() instanceof BedBlock)
					&& now.getOrDefault(e.getKey(), Blocks.AIR.defaultBlockState()).getBlock() != piece.getBlock()) {
					leftovers.add(e.getKey());
				}
			}
			leftovers.sort(Comparator.comparing(BlockPos::asLong));
			boolean give = true;
			for (BlockPos pos : leftovers) {
				if (give && Platform.get().items().insert(level, List.of(pos), new ItemStack(Items.BREAD, 5)).isEmpty()) {
					holding.add(pos);
					give = false;
				} else {
					empty.add(pos);
					give = true;
				}
			}
			// the player's own drawer: an empty cell over a floor, inside the house, empty in both drawings
			BoundingBox box = BoundingBox.encapsulatingPositions(now.keySet()).orElseThrow();
			for (BlockPos cell : BlockPos.betweenClosed(box.minX(), box.minY(), box.minZ(), box.maxX(), box.maxY(), box.maxZ())) {
				BlockPos pos = cell.immutable();
				if (drawer[0] == null && now.getOrDefault(pos, Blocks.AIR.defaultBlockState()).isAir() && pos.getX() > box.minX() + 1 && pos.getX() < box.maxX() - 1
					&& pos.getZ() > box.minZ() + 1 && pos.getZ() < box.maxZ() - 1 && pos.getY() > box.minY()
					&& asBuilt.getOrDefault(pos, Blocks.AIR.defaultBlockState()).isAir()
					&& now.getOrDefault(pos.below(), Blocks.AIR.defaultBlockState()).isCollisionShapeFullBlock(level, pos.below())
					&& asBuilt.getOrDefault(pos.below(), Blocks.AIR.defaultBlockState()).isCollisionShapeFullBlock(level, pos.below())
					&& now.getOrDefault(pos.above(), Blocks.AIR.defaultBlockState()).isAir()) {
					drawer[0] = pos;
				}
			}
			helper.assertTrue(drawer[0] != null, "setup: no empty floor cell in the house for the player's drawer");
			level.setBlock(drawer[0], BuiltInRegistries.BLOCK.get(ResourceLocation.parse("handcrafted:oak_drawer")).defaultBlockState(), Block.UPDATE_ALL);
		}, r -> {
			helper.assertTrue(!holding.isEmpty(), "setup: none of the old Handcrafted pieces takes items (" + empty.size() + " empty)");
			helper.assertTrue(!empty.isEmpty(), "setup: no empty old Handcrafted piece left to clear");
			Set<BlockPos> clear = steps(r.plan(), BuildPlan.Stage.CLEAR);
			helper.assertTrue(clear.equals(steps(r.planBeforeReload(), BuildPlan.Stage.CLEAR)),
				"after a save and load the refresh clears other blocks than before it");
			List<String> wrong = new ArrayList<>();
			for (BlockPos pos : holding) {
				if (clear.contains(pos)) {
					wrong.add("the refresh clears the " + id(r.asBuilt().get(pos)) + " holding bread at " + pos.toShortString());
				}
			}
			if (clear.contains(drawer[0]) || steps(r.plan(), BuildPlan.Stage.STRUCTURE).contains(drawer[0])
				|| steps(r.plan(), BuildPlan.Stage.DECORATION).contains(drawer[0])) {
				wrong.add("the refresh takes away the player's own drawer at " + drawer[0].toShortString());
			}
			Set<BlockPos> untouched = new HashSet<>(holding);
			untouched.add(drawer[0]);
			lay(level, r, untouched);
			for (BlockPos pos : holding) {
				BlockState world = level.getBlockState(pos);
				if (world.getBlock() != r.asBuilt().get(pos).getBlock()) {
					wrong.add("the " + id(r.asBuilt().get(pos)) + " holding bread at " + pos.toShortString() + " became " + id(world));
				} else if (Platform.get().items().count(level, List.of(pos), Items.BREAD) != 5) {
					wrong.add("the " + id(world) + " at " + pos.toShortString() + " holds " + Platform.get().items().count(level, List.of(pos), Items.BREAD) + " bread, not 5");
				}
			}
			for (BlockPos pos : empty) {
				BlockState world = level.getBlockState(pos);
				BlockState wanted = r.now().getOrDefault(pos, Blocks.AIR.defaultBlockState());
				boolean ok = wanted.isAir() ? world.isAir() || world.canBeReplaced() && world.getFluidState().isEmpty() : MaterialRules.matches(world, wanted);
				if (!ok) {
					wrong.add("the empty old " + id(r.asBuilt().get(pos)) + " at " + pos.toShortString() + " is " + id(world) + ", a fresh build has " + id(wanted));
				}
			}
			if (!id(level.getBlockState(drawer[0])).equals("handcrafted:oak_drawer")) {
				wrong.add("the player's drawer at " + drawer[0].toShortString() + " is now " + id(level.getBlockState(drawer[0])));
			}
			AliveWorkplace.LOG.info("[test] B1383 refresh: kept {} old Handcrafted pieces holding bread ({}), cleared {} empty ones ({}), kept the player's drawer at {}",
				holding.size(), holding.stream().map(p -> id(r.asBuilt().get(p))).toList(), empty.size(), empty.stream().map(p -> id(r.asBuilt().get(p))).toList(), drawer[0].toShortString());
			helper.assertTrue(wrong.isEmpty(), wrong.size() + " wrong (" + holding.size() + " holding, " + empty.size() + " empty): "
				+ wrong.subList(0, Math.min(6, wrong.size())));
		});
	}

	/**
	 * Turned half round: the reloaded refresh leaves the old house exactly as a fresh build of today's drawing (chests and
	 * job blocks kept, B642), no old Handcrafted piece left standing in it.
	 */
	//$ gametest_ticks_batch HUGE_AREA '400' '"qa1122e2_pieces_turned"'
	@GameTest(template = HUGE_AREA, timeoutTicks = 400, batch = "qa1122e2_pieces_turned")
	public void aReloadedRefreshOfAnOldHandcraftedHouseTurnedHalfRoundEndsAsAFreshBuild(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		refresh(helper, new BlockPos(19, 2, 19), Rotation.CLOCKWISE_180, Mirror.NONE, (asBuilt, now) -> {
		}, r -> {
			lay(level, r, Set.of());
			Set<BlockPos> spots = new HashSet<>(r.asBuilt().keySet());
			spots.addAll(r.now().keySet());
			List<String> left = new ArrayList<>();
			for (BlockPos pos : spots) {
				BlockState world = level.getBlockState(pos);
				BlockState wanted = r.now().getOrDefault(pos, Blocks.AIR.defaultBlockState());
				if (world.getBlock() instanceof net.minecraft.world.level.block.ChestBlock
					|| net.minecraft.world.entity.ai.village.poi.PoiTypes.forState(world).map(h -> h.is(net.minecraft.tags.PoiTypeTags.ACQUIRABLE_JOB_SITE)).orElse(false)) {
					continue; // B642: chests and job blocks stay
				}
				boolean ok = wanted.isAir() ? world.isAir() || world.canBeReplaced() && world.getFluidState().isEmpty() : MaterialRules.matches(world, wanted);
				if (!ok) {
					left.add(pos.toShortString() + " " + id(world) + " (wanted " + id(wanted) + ")");
				}
			}
			helper.assertTrue(left.isEmpty(), left.size() + " spots differ from a fresh build: " + left.subList(0, Math.min(8, left.size())));
		});
	}

	/** RefreshFurnishedCompatTests' spots file of the Stone House before B260 (its day's furniture). */
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
