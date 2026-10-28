package io.github.jcondedata.aliveworkplace.compat;

import io.github.jcondedata.aliveworkplace.AliveWorkplace;
import io.github.jcondedata.aliveworkplace.blueprint.Blueprint;
import io.github.jcondedata.aliveworkplace.blueprint.BlueprintData;
import io.github.jcondedata.aliveworkplace.blueprint.BlueprintLibrary;
import io.github.jcondedata.aliveworkplace.blueprint.ModdedLibrary;
import io.github.jcondedata.aliveworkplace.build.BuildPlan;
import io.github.jcondedata.aliveworkplace.build.BuildSite;
import io.github.jcondedata.aliveworkplace.build.BuildSiteManager;
import io.github.jcondedata.aliveworkplace.build.Builders;
import io.github.jcondedata.aliveworkplace.build.Employer;
import io.github.jcondedata.aliveworkplace.build.Refreshes;
import io.github.jcondedata.aliveworkplace.furnish.BlueprintFurniture;
import io.github.jcondedata.aliveworkplace.furnish.FurnitureChoice;
import io.github.jcondedata.aliveworkplace.furnish.FurnitureSpot;
import io.github.jcondedata.aliveworkplace.furnish.Resting;
import io.github.jcondedata.aliveworkplace.hall.HallPages;
import io.github.jcondedata.aliveworkplace.hall.RepairsPage;
import io.github.jcondedata.aliveworkplace.hall.VillageHallScreen;
import io.github.jcondedata.aliveworkplace.hall.VillageHalls;
import io.github.jcondedata.aliveworkplace.registry.ModAttachments;
import io.github.jcondedata.aliveworkplace.registry.ModBlocks;
import io.github.jcondedata.aliveworkplace.work.ChoiceMenu;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
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
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;

/** B281: the Terrace's homes are furnished alike in kind: every one gets the installed furniture mods' pieces. */
public class TerraceFurnitureCompatTests implements FabricGameTest {
	/**
	 * B281 (the owner, 2026-10-21: one Terrace home had a Handcrafted table and chair downstairs, the other spruce stand-ins):
	 * with Handcrafted and Beautify (and with CobbleFurnies first), every furniture spot of the Terrace and Terrace II is a
	 * mod's piece, none left vanilla, and each home's kitchen has a modded table and chair.
	 */
	//$ gametest 'FabricGameTest.EMPTY_STRUCTURE'
	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
	public void everyTerraceHomeGetsTheModsFurniture(GameTestHelper helper) {
		Map<String, Integer> homes = Map.of("terrace", 2, "terrace_2", 3);
		for (List<String> order : List.of(List.of("handcrafted", "beautify"), List.of("cobblefurnies", "handcrafted", "beautify"))) {
			homes.forEach((name, count) -> {
				List<FurnitureSpot> spots = BlueprintFurniture.of(AliveWorkplace.id(name));
				FurnitureChoice choice = FurnitureChoice.choose(spots, order, true);
				for (int i = 0; i < spots.size(); i++) {
					helper.assertTrue(choice.spots().containsKey(i), name + " with " + order + ": its " + spots.get(i).kind().id() + " at "
						+ spots.get(i).pos().toShortString() + " stays vanilla");
				}
				for (int home = 0; home < count; home++) {
					int x0 = 1 + 5 * home;
					for (FurnitureSpot.Kind kind : List.of(FurnitureSpot.Kind.TABLE, FurnitureSpot.Kind.CHAIR)) {
						long modded = choice.spots().values().stream().filter(p -> p.kind() == kind && p.pos().getY() == 1
							&& p.pos().getX() > x0 && p.pos().getX() < x0 + 5 && !p.mod().equals("minecraft")).count();
						helper.assertTrue(modded == 1, name + " home " + (home + 1) + " with " + order + " has " + modded + " modded " + kind.id() + "s");
					}
				}
			});
		}
		helper.succeed();
	}

	private static final String HUGE_AREA = "aliveworkplace_compat:huge_area";
	private static final List<String> MODS = List.of("handcrafted", "beautify");
	/** The placements a player can give a build: as drawn, turned, and turned and mirrored both ways. */
	private static final List<BlueprintData.Placement> TURNS = List.of(
		new BlueprintData.Placement(net.minecraft.world.level.Level.OVERWORLD.location(), BlockPos.ZERO, Rotation.NONE, Mirror.NONE),
		new BlueprintData.Placement(net.minecraft.world.level.Level.OVERWORLD.location(), BlockPos.ZERO, Rotation.CLOCKWISE_90, Mirror.LEFT_RIGHT),
		new BlueprintData.Placement(net.minecraft.world.level.Level.OVERWORLD.location(), BlockPos.ZERO, Rotation.CLOCKWISE_180, Mirror.FRONT_BACK),
		new BlueprintData.Placement(net.minecraft.world.level.Level.OVERWORLD.location(), BlockPos.ZERO, Rotation.COUNTERCLOCKWISE_90, Mirror.NONE));

	private static String id(BlockState state) {
		return BuiltInRegistries.BLOCK.getKey(state.getBlock()).toString();
	}

	/** Where the blueprint's {@code pos} lands in the world for {@code placement}. */
	private static BlockPos world(BlueprintData.Placement placement, BlockPos pos) {
		return placement.origin().offset(StructureTemplate.transform(pos, placement.mirror(), placement.rotation(), BlockPos.ZERO));
	}

	/**
	 * What's wrong with the kitchens of {@code name} standing at {@code placement}: a home whose table or chair spot holds
	 * no furniture mod's piece, a library crockery set not on its table, and anything small over a cell with nothing to stand on.
	 */
	private static List<String> kitchenProblems(ServerLevel level, ResourceLocation name, BlueprintData.Placement placement, Map<BlockPos, BlockState> targets) {
		List<String> out = new ArrayList<>();
		for (FurnitureSpot spot : BlueprintFurniture.of(name)) {
			if (spot.kind() != FurnitureSpot.Kind.TABLE && spot.kind() != FurnitureSpot.Kind.CHAIR) {
				continue;
			}
			BlockState there = level.getBlockState(world(placement, spot.pos()));
			if (id(there).startsWith("minecraft:")) {
				out.add(name.getPath() + " " + spot.kind().id() + " at " + spot.pos().toShortString() + " is " + id(there));
			}
		}
		for (BlockPos pos : targets.keySet()) {
			BlockState state = level.getBlockState(pos);
			if ((Resting.standsOnFloor(state) || Resting.libraryPiece(state)) && level.getBlockState(pos.below()).getCollisionShape(level, pos.below()).isEmpty()
				&& !(level.getBlockState(pos.above()).getBlock() instanceof net.minecraft.world.level.block.ChainBlock)) {
				out.add(name.getPath() + " " + id(state) + " floats at " + pos.toShortString() + " over " + id(level.getBlockState(pos.below())));
			}
		}
		return out;
	}

	/**
	 * B641 (the owner, 2026-10-27: in one Terrace the east kitchen had Handcrafted's pieces and the west one spruce
	 * stand-ins, with crockery hanging over the table): a new Terrace and Terrace II, from the vanilla and the modded
	 * library, put up as drawn, turned and mirrored, have a furniture mod's table and chair in every kitchen, and the
	 * library's crockery on each table, nothing in the air.
	 */
	//$ gametest_ticks_batch HUGE_AREA '400' '"b641_kitchens"'
	@GameTest(template = HUGE_AREA, timeoutTicks = 400, batch = "b641_kitchens")
	public void everyKitchenOfANewTerraceGetsTheModsPiecesTurnedAndMirrored(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		List<String> problems = new ArrayList<>();
		int built = 0;
		for (String name : List.of("terrace", "terrace_2")) {
			ResourceLocation id = AliveWorkplace.id(name);
			Blueprint base = BlueprintLibrary.get(level, id).orElseThrow();
			FurnitureChoice chosen = FurnitureChoice.choose(BlueprintFurniture.of(id), MODS, true);
			for (boolean modded : List.of(false, true)) {
				if (modded) {
					helper.assertTrue(ModdedLibrary.copy(id).isPresent(), name + " has no modded library copy here");
				}
				Blueprint furnished = chosen.withLibrary(modded).apply(base, id);
				for (BlueprintData.Placement turn : TURNS) {
					BlueprintData.Placement placement = new BlueprintData.Placement(level.dimension().location(), helper.absolutePos(new BlockPos(6, 1, 6)),
						turn.rotation(), turn.mirror());
					Map<BlockPos, BlockState> targets = BuildPlan.create(furnished, placement).targets();
					targets.forEach((pos, state) -> level.setBlock(pos, state, Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE));
					problems.addAll(kitchenProblems(level, id, placement, targets).stream()
						.map(p -> (modded ? "modded " : "") + turn.rotation() + "/" + turn.mirror() + ": " + p).toList());
					if (modded) {
						long crockery = targets.keySet().stream().filter(p -> id(level.getBlockState(p)).equals("handcrafted:wood_crockery_combo")).count();
						if (crockery < (name.equals("terrace") ? 2 : 3)) {
							problems.add(name + " " + turn.rotation() + "/" + turn.mirror() + ": " + crockery + " crockery sets on its tables");
						}
					}
					targets.keySet().forEach(pos -> level.setBlock(pos, Blocks.AIR.defaultBlockState(), Block.UPDATE_CLIENTS));
					built++;
				}
			}
		}
		helper.assertTrue(built == 16, "built " + built);
		helper.assertTrue(problems.isEmpty(), problems.size() + " problem(s): " + problems.subList(0, Math.min(8, problems.size())));
		helper.succeed();
	}

	/**
	 * B641's cause: a Terrace from the modded library furnished before B281 (its spots file of the day had no east kitchen
	 * table and chair, so those stayed spruce, and every bed's spot index has moved since), turned and mirrored, gets a
	 * Refresh on the hall's Repairs page; carried out, both kitchens have Handcrafted's table and chair, the beds stay
	 * Handcrafted's, and the crockery that hung over the tables sits on them.
	 */
	//$ gametest_ticks_batch HUGE_AREA '400' '"b641_refresh"'
	@GameTest(template = HUGE_AREA, timeoutTicks = 400, batch = "b641_refresh")
	public void aTerraceFurnishedBeforeTheFixIsRefreshedWithBothKitchensModded(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		ResourceLocation terrace = AliveWorkplace.id("terrace");
		List<FurnitureSpot> today = BlueprintFurniture.of(terrace);
		List<FurnitureSpot> before = today.stream().filter(s -> !(s.pos().getY() == 1 && s.pos().getX() >= 7)).toList(); // the file before B281
		helper.assertTrue(before.size() == today.size() - 2, "the east kitchen's spots: " + (today.size() - before.size()));
		FurnitureChoice furniture = FurnitureChoice.load(FurnitureChoice.choose(before, MODS, true).withLibrary(true).save());
		helper.assertTrue(furniture.modded() && furniture.staleFor(today), "not the stale modded choice: " + furniture);
		Blueprint copy = ModdedLibrary.copy(terrace).orElseThrow();
		Blueprint asBuilt = furniture.applyAsBuilt(copy);
		// (the beds keep their Handcrafted pieces against today's file: their indices moved by two)
		Blueprint upkept = furniture.apply(BlueprintLibrary.get(level, terrace).orElseThrow(), terrace);
		long fancyBeds = upkept.blocks().stream().filter(e -> id(e.state()).endsWith("_fancy_bed")).count();
		helper.assertTrue(fancyBeds == 8, "the stale choice applied to today's spots has " + fancyBeds + " fancy bed halves, not 8");
		ServerPlayer owner = helper.makeMockServerPlayerInLevel();
		BlueprintData.Placement placement = new BlueprintData.Placement(level.dimension().location(), helper.absolutePos(new BlockPos(9, 2, 9)),
			Rotation.CLOCKWISE_90, Mirror.LEFT_RIGHT);
		for (Map.Entry<BlockPos, BlockState> e : BuildPlan.create(asBuilt, placement).targets().entrySet()) {
			level.setBlock(e.getKey(), e.getValue(), Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE);
		}
		helper.assertTrue(!kitchenProblems(level, terrace, placement, BuildPlan.create(asBuilt, placement).targets()).isEmpty(),
			"the old Terrace already has both kitchens modded and nothing floating");
		BlockPos hall = new BlockPos(1, 2, 1);
		helper.setBlock(hall, ModBlocks.VILLAGE_HALL);
		helper.setBlock(new BlockPos(1, 2, 26), Blocks.CHEST);
		helper.setBlock(new BlockPos(3, 2, 26), ModBlocks.BUILDERS_BENCH);
		Villager builder = helper.spawn(EntityType.VILLAGER, new BlockPos(3, 2, 24));
		Builders.employ(level, builder, helper.absolutePos(new BlockPos(3, 2, 26)));
		ModAttachments.BUILDER_EMPLOYER.set(builder, new Employer(owner.getUUID(), owner.getGameProfile().getName()));
		BuildSiteManager manager = BuildSiteManager.get(level);
		manager.recordFinished(terrace, placement, owner.getUUID());
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
				helper.assertTrue(slot >= 0, "the old Terrace has no Refresh on the Repairs page");
				menu.press(slot, owner);
				menu.press(RepairsPage.REFRESH, owner);
				site = Refreshes.siteOf(level, placement);
			} finally {
				VillageHalls.RADIUS = radius;
			}
			helper.assertTrue(site != null, "no Builder took the refresh up");
			Blueprint current = Refreshes.refreshedFurniture(level, terrace, furniture).apply(BlueprintLibrary.get(level, terrace).orElseThrow(), terrace);
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
			List<String> left = kitchenProblems(level, terrace, placement, now);
			long beds = now.keySet().stream().filter(p -> id(level.getBlockState(p)).endsWith("_fancy_bed")).count();
			long crockery = now.keySet().stream().filter(p -> id(level.getBlockState(p)).equals("handcrafted:wood_crockery_combo")).count();
			manager.forgetFinished(placement);
			level.getServer().getPlayerList().remove(owner);
			helper.assertTrue(left.isEmpty(), "after the refresh: " + left);
			helper.assertTrue(beds == 8, beds + " fancy bed halves after the refresh, not 8");
			helper.assertTrue(crockery == 2, crockery + " crockery sets after the refresh, not one on each kitchen table");
			helper.succeed();
		});
	}
}
