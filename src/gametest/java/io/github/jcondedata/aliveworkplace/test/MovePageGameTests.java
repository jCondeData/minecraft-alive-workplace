package io.github.jcondedata.aliveworkplace.test;

import io.github.jcondedata.aliveworkplace.blueprint.BlueprintData;
import io.github.jcondedata.aliveworkplace.blueprint.BlueprintOutline;
import io.github.jcondedata.aliveworkplace.blueprint.Blueprints;
import io.github.jcondedata.aliveworkplace.build.BuildSiteManager;
import io.github.jcondedata.aliveworkplace.hall.HallPages;
import io.github.jcondedata.aliveworkplace.hall.MovePage;
import io.github.jcondedata.aliveworkplace.hall.VillageHallScreen;
import io.github.jcondedata.aliveworkplace.hall.VillageHalls;
import io.github.jcondedata.aliveworkplace.move.MoveFit;
import io.github.jcondedata.aliveworkplace.move.Movers;
import io.github.jcondedata.aliveworkplace.move.PendingMoves;
import io.github.jcondedata.aliveworkplace.registry.ModBlocks;
import io.github.jcondedata.aliveworkplace.work.ChoiceMenu;
import java.util.List;
import java.util.UUID;
import java.util.function.Predicate;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponents;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.ItemLore;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings;

/**
 * The Village Hall's Move page (ROADMAP 45.2), through the hall's own clicks: the tab lists the finished Test Hut with a
 * Move button; its picker opens on the spot it stands on; a click on the map moves the spot (the ghost outline follows,
 * green); each refusal says why in plain words (another building, a road, outside the village, too steep, under water);
 * Rotate turns it a quarter at a time; Cancel forgets the pick and the ghost; Confirm without a Mover is refused and saves
 * nothing; with a Mover it saves the pending move, which survives a save and reload and opens the picker on its spot.
 */
public class MovePageGameTests implements FabricGameTest {
	private static final String AREA = "aliveworkplace_test:big_area";
	private static final ResourceLocation HUT = ResourceLocation.fromNamespaceAndPath("aliveworkplace_test", "test_hut");
	private static final BlockPos HALL = new BlockPos(15, 2, 11);
	/** The finished Test Hut: x 3-7, y 2-5, z 3-7; its middle (5, 5). */
	private static final BlockPos HUT_ORIGIN = new BlockPos(3, 2, 3);
	/** The map cell two to the east of the middle: the spot's middle goes 8 blocks east, to (13, 5): x 11-15, z 3-7. */
	private static final int EAST = MovePage.CENTRE + 2;
	/** Two rows north: the spot's middle at (5, -3), outside the village. */
	private static final int NORTH = MovePage.CENTRE - 2 * MovePage.COLUMNS;

	private record Village(ServerLevel level, BlockPos hall, BlockPos hut, ServerPlayer player) {
	}

	private static Village stage(GameTestHelper helper) {
		Leftovers.clear(helper);
		ServerLevel level = helper.getLevel();
		int radius = VillageHalls.RADIUS;
		VillageHalls.RADIUS = 16;
		Predicate<Villager> mover = Movers.isMover;
		BuildSiteManager sites = BuildSiteManager.get(level);
		BlueprintData.Placement hut = placement(helper, HUT_ORIGIN);
		ServerPlayer player = helper.makeMockServerPlayerInLevel();
		Leftovers.after(helper, () -> {
			VillageHalls.RADIUS = radius;
			Movers.isMover = mover;
			sites.forgetFinished(hut);
			PendingMoves.get(level).remove(hut.origin());
			MovePage.stop(player);
		});
		helper.setBlock(HALL, ModBlocks.VILLAGE_HALL);
		BlockPos origin = helper.absolutePos(HUT_ORIGIN);
		level.getStructureManager().get(HUT).orElseThrow().placeInWorld(level, origin, origin, new StructurePlaceSettings(), RandomSource.create(1L), 2);
		sites.recordFinished(HUT, hut, UUID.randomUUID());
		return new Village(level, helper.absolutePos(HALL), origin, player);
	}

	private static BlueprintData.Placement placement(GameTestHelper helper, BlockPos rel) {
		return new BlueprintData.Placement(helper.getLevel().dimension().location(), helper.absolutePos(rel), Rotation.NONE, Mirror.NONE);
	}

	/** The hall opened, its Move tab clicked, then the hut's Move button: the picker. */
	private static ChoiceMenu openPicker(GameTestHelper helper, Village v) {
		ChoiceMenu menu = VillageHallScreen.forTest(v.player(), v.hall());
		int tab = HallPages.slot(MovePage.PAGE);
		helper.assertTrue(tab >= VillageHallScreen.PAGE_ROW, "the Move page has no tab: " + tab);
		helper.assertTrue(menu.icon(tab).is(Items.PISTON), "the tab: " + menu.icon(tab));
		menu.press(tab, v.player());
		ItemStack row = menu.icon(VillageHallScreen.FIRST_ROW);
		helper.assertTrue(row.is(Items.BRICKS) || row.is(Items.STICKY_PISTON), "the hut's row: " + row);
		helper.assertTrue(row.getHoverName().getString().equals(Blueprints.displayName(HUT).getString()), "its name: " + row.getHoverName().getString());
		helper.assertTrue(lore(row).contains(Component.translatable("screen.aliveworkplace.move.click").getString()), "its Move button: " + lore(row));
		menu.press(VillageHallScreen.FIRST_ROW, v.player());
		helper.assertTrue(MovePage.picking(v.player()).map(p -> p.from().equals(v.hut())).orElse(false), "the picker didn't open on the hut: " + MovePage.picking(v.player()));
		helper.assertTrue(menu.icon(MovePage.HEADER).getHoverName().getString().equals(Blueprints.displayName(HUT).getString()),
			"the picker's building: " + menu.icon(MovePage.HEADER));
		return menu;
	}

	private static List<String> lore(ItemStack stack) {
		return stack.getOrDefault(DataComponents.LORE, ItemLore.EMPTY).lines().stream().map(Component::getString).toList();
	}

	/** The fit line shows {@code key}'s words (its plain reason). */
	private static void assertVerdict(GameTestHelper helper, ChoiceMenu menu, Component expected) {
		String shown = menu.icon(MovePage.VERDICT).getHoverName().getString();
		helper.assertTrue(shown.equals(expected.getString()), "the fit line says \"" + shown + "\", not \"" + expected.getString() + "\"");
	}

	/** Picking: the picker opens on the spot it stands on (yellow, "pick a new spot"); a map click moves the spot and the ghost. */
	//$ gametest_ticks_batch AREA '100' '"movePagePick"'
	@GameTest(template = AREA, timeoutTicks = 100, batch = "movePagePick")
	public void aMapClickPicksTheSpotAndTheGhostFollows(GameTestHelper helper) {
		Village v = stage(helper);
		helper.runAfterDelay(2, () -> {
			ChoiceMenu menu = openPicker(helper, v);
			assertVerdict(helper, menu, Component.translatable("screen.aliveworkplace.move.fit.same_spot"));
			helper.assertTrue(menu.icon(MovePage.CENTRE).is(Items.YELLOW_STAINED_GLASS), "the spot before a pick: " + menu.icon(MovePage.CENTRE));
			helper.assertTrue(menu.icon(MovePage.CONFIRM).is(Items.GRAY_DYE), "Confirm before a pick: " + menu.icon(MovePage.CONFIRM));
			MovePage.Picking before = MovePage.picking(v.player()).orElseThrow();
			menu.press(EAST, v.player());
			MovePage.Picking after = MovePage.picking(v.player()).orElseThrow();
			helper.assertTrue(after.x() == before.x() + 2 * MovePage.STEP && after.z() == before.z(), "the spot moved to " + after.x() + ", " + after.z());
			MoveFit.Fit fit = MovePage.fit(v.player()).orElseThrow();
			helper.assertTrue(fit.fits() && fit.min().equals(v.hut().offset(8, 0, 0)), "the east spot: " + fit.reason() + " at " + fit.min() + " on " + v.level().getBlockState(fit.min().below()) + ", the floor " + v.level().getBlockState(v.hut().offset(8, -1, 0)));
			assertVerdict(helper, menu, Component.translatable("screen.aliveworkplace.move.fit.fits"));
			helper.assertTrue(menu.icon(MovePage.VERDICT).is(Items.LIME_DYE), "the fit line's icon: " + menu.icon(MovePage.VERDICT));
			helper.assertTrue(menu.icon(MovePage.CENTRE).is(Items.LIME_STAINED_GLASS), "the new spot on the map: " + menu.icon(MovePage.CENTRE));
			helper.assertTrue(menu.icon(MovePage.CENTRE - 2).is(Items.YELLOW_STAINED_GLASS), "where it stands now, on the map: " + menu.icon(MovePage.CENTRE - 2));
			CompoundTag mark = menu.icon(MovePage.CENTRE).get(DataComponents.CUSTOM_DATA).copyTag();
			helper.assertTrue(mark.contains(MovePage.MAP_MARK), "the map cell isn't marked for the client");
			BlueprintOutline.MoveGhost ghost = BlueprintOutline.moveGhost(v.player());
			helper.assertTrue(ghost != null && ghost.fits() && ghost.box().minX() == v.hut().getX() + 8 && ghost.box().minZ() == v.hut().getZ()
				&& ghost.front() == Direction.NORTH, "the ghost: " + ghost);
			helper.succeed();
		});
	}

	/** Refused: overlaps another building (a second hut east of it), with the building's name. */
	//$ gametest_ticks_batch AREA '100' '"movePageBuilding"'
	@GameTest(template = AREA, timeoutTicks = 100, batch = "movePageBuilding")
	public void aSpotOverAnotherBuildingIsRefused(GameTestHelper helper) {
		Village v = stage(helper);
		BuildSiteManager sites = BuildSiteManager.get(v.level());
		BlueprintData.Placement other = placement(helper, new BlockPos(12, 2, 3));
		sites.recordFinished(HUT, other, UUID.randomUUID());
		Leftovers.after(helper, () -> sites.forgetFinished(other));
		helper.runAfterDelay(2, () -> {
			ChoiceMenu menu = openPicker(helper, v);
			menu.press(EAST, v.player());
			assertVerdict(helper, menu, Component.translatable("screen.aliveworkplace.move.fit.building", Blueprints.displayName(HUT)));
			helper.assertTrue(menu.icon(MovePage.VERDICT).is(Items.RED_DYE), "the fit line's icon: " + menu.icon(MovePage.VERDICT));
			helper.assertTrue(menu.icon(MovePage.CENTRE).is(Items.RED_STAINED_GLASS), "the refused spot on the map: " + menu.icon(MovePage.CENTRE));
			BlueprintOutline.MoveGhost ghost = BlueprintOutline.moveGhost(v.player());
			helper.assertTrue(ghost != null && !ghost.fits(), "the ghost isn't red: " + ghost);
			helper.succeed();
		});
	}

	/** Refused: a road (a dirt path) under the spot, with where. */
	//$ gametest_ticks_batch AREA '100' '"movePageRoad"'
	@GameTest(template = AREA, timeoutTicks = 100, batch = "movePageRoad")
	public void aSpotOnARoadIsRefused(GameTestHelper helper) {
		Village v = stage(helper);
		helper.setBlock(new BlockPos(13, 1, 5), Blocks.DIRT_PATH);
		helper.runAfterDelay(2, () -> {
			ChoiceMenu menu = openPicker(helper, v);
			menu.press(EAST, v.player());
			BlockPos at = helper.absolutePos(new BlockPos(13, 1, 5));
			assertVerdict(helper, menu, Component.translatable("screen.aliveworkplace.move.fit.road.at", at.getX(), at.getY(), at.getZ()));
			helper.succeed();
		});
	}

	/** Refused: part of the spot outside the village. */
	//$ gametest_ticks_batch AREA '100' '"movePageOutside"'
	@GameTest(template = AREA, timeoutTicks = 100, batch = "movePageOutside")
	public void aSpotOutsideTheVillageIsRefused(GameTestHelper helper) {
		Village v = stage(helper);
		helper.runAfterDelay(2, () -> {
			ChoiceMenu menu = openPicker(helper, v);
			menu.press(NORTH, v.player());
			assertVerdict(helper, menu, Component.translatable("screen.aliveworkplace.move.fit.outside"));
			helper.succeed();
		});
	}

	/** Refused: ground rising five blocks across the spot. */
	//$ gametest_ticks_batch AREA '100' '"movePageSteep"'
	@GameTest(template = AREA, timeoutTicks = 100, batch = "movePageSteep")
	public void aSteepSpotIsRefused(GameTestHelper helper) {
		Village v = stage(helper);
		for (int x = 14; x <= 15; x++) {
			for (int z = 3; z <= 7; z++) {
				for (int y = 2; y <= 6; y++) {
					helper.setBlock(new BlockPos(x, y, z), Blocks.STONE);
				}
			}
		}
		helper.runAfterDelay(2, () -> {
			ChoiceMenu menu = openPicker(helper, v);
			menu.press(EAST, v.player());
			assertVerdict(helper, menu, Component.translatable("screen.aliveworkplace.move.fit.steep"));
			helper.succeed();
		});
	}

	/** Refused: water on the spot, with where. */
	//$ gametest_ticks_batch AREA '100' '"movePageWater"'
	@GameTest(template = AREA, timeoutTicks = 100, batch = "movePageWater")
	public void aSpotUnderWaterIsRefused(GameTestHelper helper) {
		Village v = stage(helper);
		helper.setBlock(new BlockPos(13, 0, 5), Blocks.STONE);
		helper.setBlock(new BlockPos(13, 1, 5), Blocks.WATER);
		helper.runAfterDelay(2, () -> {
			ChoiceMenu menu = openPicker(helper, v);
			menu.press(EAST, v.player());
			BlockPos at = helper.absolutePos(new BlockPos(13, 1, 5));
			assertVerdict(helper, menu, Component.translatable("screen.aliveworkplace.move.fit.water.at", at.getX(), at.getY(), at.getZ()));
			helper.succeed();
		});
	}

	/** Rotate: a quarter turn a click (the header and ghost face east, then south, west, north); turning where it stands fits. */
	//$ gametest_ticks_batch AREA '100' '"movePageRotate"'
	@GameTest(template = AREA, timeoutTicks = 100, batch = "movePageRotate")
	public void rotateTurnsItAQuarterAClick(GameTestHelper helper) {
		Village v = stage(helper);
		helper.runAfterDelay(2, () -> {
			ChoiceMenu menu = openPicker(helper, v);
			Direction[] faces = {Direction.EAST, Direction.SOUTH, Direction.WEST, Direction.NORTH};
			Rotation[] turns = {Rotation.CLOCKWISE_90, Rotation.CLOCKWISE_180, Rotation.COUNTERCLOCKWISE_90, Rotation.NONE};
			for (int i = 0; i < 4; i++) {
				menu.press(MovePage.ROTATE, v.player());
				helper.assertTrue(MovePage.picking(v.player()).orElseThrow().rotation() == turns[i], "turn " + i + ": " + MovePage.picking(v.player()).get().rotation());
				String faces_ = Component.translatable("screen.aliveworkplace.move.faces",
					Component.translatable("screen.aliveworkplace.move.direction." + faces[i].getName())).getString();
				helper.assertTrue(lore(menu.icon(MovePage.HEADER)).contains(faces_), "the header (" + faces_ + "): " + lore(menu.icon(MovePage.HEADER)));
				BlueprintOutline.MoveGhost ghost = BlueprintOutline.moveGhost(v.player());
				helper.assertTrue(ghost != null && ghost.front() == faces[i], "the ghost's front: " + ghost);
				if (i < 3) {
					assertVerdict(helper, menu, Component.translatable("screen.aliveworkplace.move.fit.fits")); // turned where it stands
				}
			}
			assertVerdict(helper, menu, Component.translatable("screen.aliveworkplace.move.fit.same_spot")); // all the way round
			helper.succeed();
		});
	}

	/** Cancel: the pick and the ghost are forgotten, the list shows again, and nothing is saved. */
	//$ gametest_ticks_batch AREA '100' '"movePageCancel"'
	@GameTest(template = AREA, timeoutTicks = 100, batch = "movePageCancel")
	public void cancelForgetsThePickAndTheGhost(GameTestHelper helper) {
		Village v = stage(helper);
		helper.runAfterDelay(2, () -> {
			ChoiceMenu menu = openPicker(helper, v);
			menu.press(EAST, v.player());
			helper.assertTrue(BlueprintOutline.moveGhost(v.player()) != null, "no ghost while picking");
			menu.press(MovePage.CANCEL, v.player());
			helper.assertTrue(MovePage.picking(v.player()).isEmpty(), "still picking after Cancel");
			helper.assertTrue(BlueprintOutline.moveGhost(v.player()) == null, "the ghost stayed after Cancel");
			helper.assertTrue(menu.icon(VillageHallScreen.FIRST_ROW).is(Items.BRICKS), "the list isn't back: " + menu.icon(VillageHallScreen.FIRST_ROW));
			helper.assertTrue(PendingMoves.get(v.level()).forBuilding(v.hut()).isEmpty(), "Cancel saved a move");
			helper.succeed();
		});
	}

	/** Confirm with no Mover in the village: refused with the plain line, nothing saved, the pick kept. */
	//$ gametest_ticks_batch AREA '100' '"movePageNoMover"'
	@GameTest(template = AREA, timeoutTicks = 100, batch = "movePageNoMover")
	public void confirmWithoutAMoverIsRefused(GameTestHelper helper) {
		Village v = stage(helper);
		helper.runAfterDelay(2, () -> {
			ChoiceMenu menu = openPicker(helper, v);
			menu.press(EAST, v.player());
			String needs = Component.translatable("screen.aliveworkplace.move.needs_mover").getString();
			helper.assertTrue(lore(menu.icon(MovePage.CONFIRM)).contains(needs), "Confirm doesn't say it needs a Mover: " + lore(menu.icon(MovePage.CONFIRM)));
			helper.assertTrue(menu.icon(MovePage.CONFIRM).is(Items.GRAY_DYE), "Confirm looks ready: " + menu.icon(MovePage.CONFIRM));
			menu.press(MovePage.CONFIRM, v.player());
			helper.assertTrue(PendingMoves.get(v.level()).forBuilding(v.hut()).isEmpty(), "a move was saved with no Mover");
			helper.assertTrue(MovePage.picking(v.player()).isPresent(), "the pick was dropped");
			helper.succeed();
		});
	}

	/**
	 * Confirm with a Mover: the pending move is saved (the building, its new corner, its turn) and survives a save and
	 * reload; the list marks it, its picker opens on the planned spot, and Call off takes it back. An old save (no list) and
	 * a broken entry load without losing the rest.
	 */
	//$ gametest_ticks_batch AREA '100' '"movePageConfirm"'
	@GameTest(template = AREA, timeoutTicks = 100, batch = "movePageConfirm")
	public void confirmWithAMoverSavesThePendingMove(GameTestHelper helper) {
		Village v = stage(helper);
		Villager mover = helper.spawn(EntityType.VILLAGER, new BlockPos(10, 2, 15));
		Movers.isMover = villager -> villager.getUUID().equals(mover.getUUID());
		helper.runAfterDelay(2, () -> {
			ChoiceMenu menu = openPicker(helper, v);
			menu.press(EAST, v.player());
			menu.press(MovePage.ROTATE, v.player());
			helper.assertTrue(menu.icon(MovePage.CONFIRM).is(Items.PISTON), "Confirm isn't ready: " + menu.icon(MovePage.CONFIRM));
			BlockPos to = MovePage.fit(v.player()).orElseThrow().min();
			menu.press(MovePage.CONFIRM, v.player());
			PendingMoves.Move move = PendingMoves.get(v.level()).forBuilding(v.hut()).orElseThrow(() -> new AssertionError("no move saved"));
			helper.assertTrue(move.to().equals(to) && move.to().equals(v.hut().offset(8, 0, 0)) && move.rotation() == Rotation.CLOCKWISE_90
				&& move.structure().equals(HUT) && move.hall().equals(v.hall()) && v.player().getUUID().equals(move.by()), "the move: " + move);
			helper.assertTrue(MovePage.picking(v.player()).isEmpty() && BlueprintOutline.moveGhost(v.player()) == null, "still picking after Confirm");
			// save and reload
			CompoundTag saved = PendingMoves.get(v.level()).save(new CompoundTag(), v.level().registryAccess());
			PendingMoves loaded = PendingMoves.load(saved, v.level().registryAccess());
			helper.assertTrue(loaded.forBuilding(v.hut()).equals(java.util.Optional.of(move)), "after reload: " + loaded.forBuilding(v.hut()));
			helper.assertTrue(PendingMoves.load(new CompoundTag(), v.level().registryAccess()).all().isEmpty(), "an old save has moves");
			ListTag list = saved.getList("moves", 10).copy();
			CompoundTag broken = new CompoundTag();
			broken.putString("structure", "aliveworkplace_test:test_hut");
			list.add(0, broken);
			CompoundTag withBroken = new CompoundTag();
			withBroken.put("moves", list);
			helper.assertTrue(PendingMoves.load(withBroken, v.level().registryAccess()).forBuilding(v.hut()).isPresent(), "a broken entry lost the rest");
			// the list marks it; its picker opens on the planned spot; Call off takes it back
			ItemStack row = menu.icon(VillageHallScreen.FIRST_ROW);
			helper.assertTrue(row.is(Items.STICKY_PISTON), "the list's row: " + row);
			String planned = Component.translatable("screen.aliveworkplace.move.planned_to", to.getX(), to.getY(), to.getZ()).getString();
			helper.assertTrue(lore(row).contains(planned), "the row (" + planned + "): " + lore(row));
			menu.press(VillageHallScreen.FIRST_ROW, v.player());
			MovePage.Picking reopened = MovePage.picking(v.player()).orElseThrow();
			helper.assertTrue(reopened.rotation() == Rotation.CLOCKWISE_90 && MovePage.fit(v.player()).orElseThrow().min().equals(to),
				"the picker didn't open on the planned spot: " + reopened);
			helper.assertTrue(menu.icon(MovePage.CALL_OFF).is(Items.COBWEB), "no Call off: " + menu.icon(MovePage.CALL_OFF));
			menu.press(MovePage.CALL_OFF, v.player());
			helper.assertTrue(PendingMoves.get(v.level()).forBuilding(v.hut()).isEmpty(), "Call off kept the move");
			helper.succeed();
		});
	}
}
