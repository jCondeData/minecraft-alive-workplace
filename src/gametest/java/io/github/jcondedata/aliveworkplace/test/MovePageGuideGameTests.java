package io.github.jcondedata.aliveworkplace.test;

import io.github.jcondedata.aliveworkplace.blueprint.BlueprintData;
import io.github.jcondedata.aliveworkplace.blueprint.BlueprintOutline;
import io.github.jcondedata.aliveworkplace.blueprint.Blueprints;
import io.github.jcondedata.aliveworkplace.build.BuildSiteManager;
import io.github.jcondedata.aliveworkplace.hall.HallPages;
import io.github.jcondedata.aliveworkplace.hall.MovePage;
import io.github.jcondedata.aliveworkplace.hall.VillageHallScreen;
import io.github.jcondedata.aliveworkplace.hall.VillageHalls;
import io.github.jcondedata.aliveworkplace.move.Movers;
import io.github.jcondedata.aliveworkplace.move.PendingMoves;
import io.github.jcondedata.aliveworkplace.registry.ModBlocks;
import io.github.jcondedata.aliveworkplace.work.ChoiceMenu;
import io.github.jcondedata.aliveworkplace.work.Words;
import java.util.List;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.function.Predicate;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponents;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.component.ItemLore;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings;

/**
 * The Move page reworked so a first-time player understands it (B1163, 45.2a), through the hall's own clicks: the list is
 * step 1 and says where each building stands from the Hall; the picker's guide names the step and what to do ("Step 2 of
 * 3: click the map where the Test Hut should go"), where it is now and where it would go, what the Mover will do, about
 * how long, and whether a Mover works here; the village map (the land, the roads, the other buildings, now and the new
 * spot) is sent for the screen to draw, and a click on it puts the spot on the block clicked; a refused spot keeps step 2
 * with the plain reason (another building, a road, outside the village, too steep, under water); a spot that fits is step
 * 3; Rotate turns the front drawn on the map; Cancel forgets the map; Confirm without a Mover is refused, with one saves
 * the move.
 */
public class MovePageGuideGameTests implements FabricGameTest {
	private static final String AREA = "aliveworkplace_test:big_area";
	private static final ResourceLocation HUT = ResourceLocation.fromNamespaceAndPath("aliveworkplace_test", "test_hut");
	private static final BlockPos HALL = new BlockPos(15, 2, 11);
	/** The finished Test Hut: x 3-7, y 2-5, z 3-7; its middle (5, 5). */
	private static final BlockPos HUT_ORIGIN = new BlockPos(3, 2, 3);
	/** The block 8 east of the hut's middle: a spot there is x 11-15, z 3-7, and fits. */
	private static final BlockPos EAST = new BlockPos(13, 2, 5);

	private record Village(ServerLevel level, BlockPos hall, BlockPos hut, ServerPlayer player) {
	}

	private static Village stage(GameTestHelper helper) {
		Leftovers.clear(helper);
		ServerLevel level = helper.getLevel();
		int radius = VillageHalls.RADIUS;
		VillageHalls.RADIUS = 16;
		Predicate<Villager> mover = Movers.isMover;
		Movers.isMover = villager -> false;
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

	private static List<String> lore(ItemStack stack) {
		return stack.getOrDefault(DataComponents.LORE, ItemLore.EMPTY).lines().stream().map(Component::getString).toList();
	}

	private static String text(String key, Object... args) {
		return Component.translatable(key, args).getString();
	}

	private static String name() {
		return Blueprints.displayName(HUT).getString();
	}

	/** "17 blocks north-west of the Hall" for {@code pos}, as the page words it. */
	private static String fromHall(BlockPos hall, BlockPos pos) {
		return text("screen.aliveworkplace.move.from_hall", VillageHallScreen.where(hall, pos));
	}

	private static CompoundTag view(ChoiceMenu menu) {
		CustomData data = menu.icon(MovePage.VIEW).get(DataComponents.CUSTOM_DATA);
		return data == null ? new CompoundTag() : data.copyTag().getCompound(MovePage.VIEW_MARK);
	}

	private static int step(ChoiceMenu menu) {
		CustomData data = menu.icon(MovePage.GUIDE).get(DataComponents.CUSTOM_DATA);
		return data == null ? -1 : data.copyTag().getInt(MovePage.STEP_MARK);
	}

	/** The hall opened, its Move tab clicked (step 1), then the hut's row: the picker. */
	private static ChoiceMenu openPicker(GameTestHelper helper, Village v) {
		ChoiceMenu menu = VillageHallScreen.forTest(v.player(), v.hall());
		menu.press(HallPages.slot(MovePage.PAGE), v.player());
		menu.press(VillageHallScreen.FIRST_ROW, v.player());
		helper.assertTrue(MovePage.picking(v.player()).isPresent(), "the picker didn't open");
		return menu;
	}

	/** A click on the village map on block ({@code x}, {@code z}) relative to the test, as the screen sends it. */
	private static void clickMap(GameTestHelper helper, Village v, ChoiceMenu menu, int x, int z) {
		BlockPos at = helper.absolutePos(new BlockPos(x, 2, z));
		int id = MovePage.mapClick(v.player(), at.getX(), at.getZ());
		helper.assertTrue(MovePage.handles(id), "the block " + at + " isn't on the map: " + id);
		helper.assertTrue(menu.clickMenuButton(v.player(), id), "the hall didn't take the map click " + id);
		MovePage.Picking p = MovePage.picking(v.player()).orElseThrow();
		helper.assertTrue(p.x() == at.getX() && p.z() == at.getZ(), "the spot's middle went to " + p.x() + ", " + p.z() + ", not " + at);
	}

	/** Step 1: the list is the first step; each row says where the building stands from the Hall and what a click does. */
	//$ gametest_ticks_batch AREA '100' '"moveGuideList"'
	@GameTest(template = AREA, timeoutTicks = 100, batch = "moveGuideList")
	public void theListIsStepOneAndSaysWhereEachBuildingStands(GameTestHelper helper) {
		Village v = stage(helper);
		helper.runAfterDelay(2, () -> {
			ChoiceMenu menu = VillageHallScreen.forTest(v.player(), v.hall());
			menu.press(HallPages.slot(MovePage.PAGE), v.player());
			ItemStack row = menu.icon(VillageHallScreen.FIRST_ROW);
			CustomData data = row.get(DataComponents.CUSTOM_DATA);
			helper.assertTrue(data != null && data.contains(MovePage.LIST_MARK), "the row isn't marked as step 1 for the screen: " + row);
			BlockPos middle = v.hut().offset(2, 0, 2);
			String now = text("screen.aliveworkplace.move.now", Component.translatable("screen.aliveworkplace.move.from_hall", VillageHallScreen.where(v.hall(), middle)));
			helper.assertTrue(lore(row).contains(now), "the row (" + now + "): " + lore(row));
			helper.assertTrue(now.endsWith("of the Hall"), "where, from the Hall: " + now);
			helper.assertTrue(lore(row).contains("Click to pick its new spot"), "what a click does: " + lore(row));
			helper.assertTrue(text("screen.aliveworkplace.move.step1").startsWith("Step 1 of 3: pick the building to move"), "the list's note: "
				+ text("screen.aliveworkplace.move.step1"));
			helper.succeed();
		});
	}

	/**
	 * Step 2: the picker's guide says what to do, where the building is now and that no new spot is picked, what the Mover
	 * does, about how long and that none works here; the map shows the land, the roads, the Hall and where it stands now.
	 */
	//$ gametest_ticks_batch AREA '100' '"moveGuideOpen"'
	@GameTest(template = AREA, timeoutTicks = 100, batch = "moveGuideOpen")
	public void thePickerSaysStepTwoAndShowsTheVillageMap(GameTestHelper helper) {
		Village v = stage(helper);
		helper.setBlock(new BlockPos(10, 1, 14), Blocks.DIRT_PATH);
		helper.runAfterDelay(2, () -> {
			ChoiceMenu menu = openPicker(helper, v);
			ItemStack guide = menu.icon(MovePage.GUIDE);
			helper.assertTrue(step(menu) == 2, "the step: " + step(menu));
			helper.assertTrue(guide.getHoverName().getString().equals("Step 2 of 3: click the map where the " + name() + " should go"),
				"the instruction: " + guide.getHoverName().getString());
			MovePage.Picking p = MovePage.picking(v.player()).orElseThrow();
			int minutes = MovePage.minutes(p.plan());
			List<String> lines = lore(guide);
			helper.assertTrue(lines.equals(List.of(
				text("screen.aliveworkplace.move.now", Component.literal(fromHall(v.hall(), p.old().getCenter()))),
				"New spot: not picked yet",
				"The Mover takes it down from the top and builds it again at the new spot, with its chests, furniture and people. The old spot is left clear.",
				Words.counted("screen.aliveworkplace.move.time", minutes, minutes).getString(),
				"No Mover yet: place a Mover's Crate for a jobless villager to claim")), "the guide's lines: " + lines);
			helper.assertTrue(lines.get(0).startsWith("Now: ") && lines.get(0).endsWith("of the Hall"), "where it is now: " + lines.get(0));
			helper.assertTrue(lines.get(3).startsWith("About " + minutes + " minute"), "how long: " + lines.get(3));
			CompoundTag view = view(menu);
			helper.assertTrue(view.getByteArray("colors").length == MovePage.MAP * MovePage.MAP && view.getByteArray("kinds").length == MovePage.MAP * MovePage.MAP,
				"the map's land isn't sent: " + view.getByteArray("colors").length);
			helper.assertTrue(view.getInt("hallX") == v.hall().getX() && view.getInt("hallZ") == v.hall().getZ() && view.getInt("radius") == 16, "the Hall on the map: " + view);
			int[] old = view.getIntArray("old");
			helper.assertTrue(old.length == 4 && old[0] == v.hut().getX() && old[1] == v.hut().getZ() && old[2] == v.hut().getX() + 4 && old[3] == v.hut().getZ() + 4,
				"where it stands now, on the map: " + java.util.Arrays.toString(old));
			helper.assertTrue(view.getInt("fit") == 1, "no spot picked yet, but the map says " + view.getInt("fit"));
			BlockPos road = helper.absolutePos(new BlockPos(10, 1, 14));
			int index = MovePage.mapClick(v.player(), road.getX(), road.getZ()) - MovePage.MAP_CLICK;
			helper.assertTrue(view.getByteArray("kinds")[index] == 1, "the road isn't on the map: " + view.getByteArray("kinds")[index]);
			helper.assertTrue(menu.icon(MovePage.VIEW).getHoverName().getString().equals(VillageHalls.name(v.level(), v.hall()).getString()), "the Hall's name: "
				+ menu.icon(MovePage.VIEW).getHoverName().getString());
			helper.succeed();
		});
	}

	/** A click on the map puts the spot on the block clicked; it fits: step 3, the new spot from the Hall, the map's green box. */
	//$ gametest_ticks_batch AREA '100' '"moveGuideClick"'
	@GameTest(template = AREA, timeoutTicks = 100, batch = "moveGuideClick")
	public void aClickOnTheVillageMapPicksTheSpotAndStepThree(GameTestHelper helper) {
		Village v = stage(helper);
		helper.runAfterDelay(2, () -> {
			ChoiceMenu menu = openPicker(helper, v);
			clickMap(helper, v, menu, EAST.getX(), EAST.getZ());
			helper.assertTrue(step(menu) == 3, "the step: " + step(menu));
			helper.assertTrue(menu.icon(MovePage.GUIDE).getHoverName().getString().equals("Step 3 of 3: the spot fits, but moving the " + name() + " needs a hired Mover first"),
				"the instruction: " + menu.icon(MovePage.GUIDE).getHoverName().getString());
			MovePage.Picking p = MovePage.picking(v.player()).orElseThrow();
			String spot = text("screen.aliveworkplace.move.spot", Component.literal(fromHall(v.hall(), MovePage.fit(v.player()).orElseThrow().box().getCenter())));
			helper.assertTrue(lore(menu.icon(MovePage.GUIDE)).get(1).equals(spot) && spot.startsWith("New spot: "), "the new spot: " + lore(menu.icon(MovePage.GUIDE)));
			int[] box = view(menu).getIntArray("spot");
			helper.assertTrue(view(menu).getInt("fit") == 0 && box.length == 4 && box[0] == v.hut().getX() + 8 && box[1] == v.hut().getZ(),
				"the new spot on the map: " + java.util.Arrays.toString(box));
			BlueprintOutline.MoveGhost ghost = BlueprintOutline.moveGhost(v.player());
			helper.assertTrue(ghost != null && ghost.fits() && ghost.box().minX() == v.hut().getX() + 8, "the ghost: " + ghost);
			helper.assertTrue(MovePage.mapClick(v.player(), v.hall().getX() + 10_000, v.hall().getZ()) == -1, "a block far off is on the map");
			helper.assertTrue(p.x() == helper.absolutePos(EAST).getX(), "the pick: " + p);
			helper.succeed();
		});
	}

	private void refused(GameTestHelper helper, Consumer<GameTestHelper> ground, int x, int z, java.util.function.Function<GameTestHelper, Component> why) {
		Village v = stage(helper);
		ground.accept(helper);
		helper.runAfterDelay(2, () -> {
			ChoiceMenu menu = openPicker(helper, v);
			clickMap(helper, v, menu, x, z);
			helper.assertTrue(step(menu) == 2, "a refused spot isn't step 2: " + step(menu));
			helper.assertTrue(menu.icon(MovePage.GUIDE).getHoverName().getString().equals("Step 2 of 3: that spot won't work for the " + name() + ". Click another spot on the map"),
				"the instruction: " + menu.icon(MovePage.GUIDE).getHoverName().getString());
			String expected = why.apply(helper).getString();
			helper.assertTrue(menu.icon(MovePage.VERDICT).getHoverName().getString().equals(expected) && expected.startsWith("Refused: "),
				"the reason says \"" + menu.icon(MovePage.VERDICT).getHoverName().getString() + "\", not \"" + expected + "\"");
			helper.assertTrue(view(menu).getInt("fit") == 2, "the map's spot isn't red: " + view(menu).getInt("fit"));
			helper.assertTrue(menu.icon(MovePage.CONFIRM).is(Items.GRAY_DYE), "Confirm looks ready on a refused spot");
			helper.succeed();
		});
	}

	/** Refused on the map: another building, named. */
	//$ gametest_ticks_batch AREA '100' '"moveGuideBuilding"'
	@GameTest(template = AREA, timeoutTicks = 100, batch = "moveGuideBuilding")
	public void aMapClickOnAnotherBuildingSaysWhy(GameTestHelper helper) {
		BlueprintData.Placement other = placement(helper, new BlockPos(12, 2, 3));
		refused(helper, h -> {
			BuildSiteManager.get(h.getLevel()).recordFinished(HUT, other, UUID.randomUUID());
			Leftovers.after(h, () -> BuildSiteManager.get(h.getLevel()).forgetFinished(other));
		}, EAST.getX(), EAST.getZ(), h -> Component.translatable("screen.aliveworkplace.move.fit.building", Blueprints.displayName(HUT)));
	}

	/** Refused on the map: a road, with where. */
	//$ gametest_ticks_batch AREA '100' '"moveGuideRoad"'
	@GameTest(template = AREA, timeoutTicks = 100, batch = "moveGuideRoad")
	public void aMapClickOnARoadSaysWhy(GameTestHelper helper) {
		refused(helper, h -> h.setBlock(new BlockPos(13, 1, 5), Blocks.DIRT_PATH), EAST.getX(), EAST.getZ(), h -> {
			BlockPos at = h.absolutePos(new BlockPos(13, 1, 5));
			return Component.translatable("screen.aliveworkplace.move.fit.road.at", at.getX(), at.getY(), at.getZ());
		});
	}

	/** Refused on the map: outside the village (8 north of the hut). */
	//$ gametest_ticks_batch AREA '100' '"moveGuideOutside"'
	@GameTest(template = AREA, timeoutTicks = 100, batch = "moveGuideOutside")
	public void aMapClickOutsideTheVillageSaysWhy(GameTestHelper helper) {
		refused(helper, h -> {
		}, 5, -3, h -> Component.translatable("screen.aliveworkplace.move.fit.outside"));
	}

	/** Refused on the map: ground too steep. */
	//$ gametest_ticks_batch AREA '100' '"moveGuideSteep"'
	@GameTest(template = AREA, timeoutTicks = 100, batch = "moveGuideSteep")
	public void aMapClickOnSteepGroundSaysWhy(GameTestHelper helper) {
		refused(helper, h -> {
			for (int x = 14; x <= 15; x++) {
				for (int z = 3; z <= 7; z++) {
					for (int y = 2; y <= 6; y++) {
						h.setBlock(new BlockPos(x, y, z), Blocks.STONE);
					}
				}
			}
		}, EAST.getX(), EAST.getZ(), h -> Component.translatable("screen.aliveworkplace.move.fit.steep"));
	}

	/** Refused on the map: under water, with where. */
	//$ gametest_ticks_batch AREA '100' '"moveGuideWater"'
	@GameTest(template = AREA, timeoutTicks = 100, batch = "moveGuideWater")
	public void aMapClickOnWaterSaysWhy(GameTestHelper helper) {
		refused(helper, h -> {
			h.setBlock(new BlockPos(13, 0, 5), Blocks.STONE);
			h.setBlock(new BlockPos(13, 1, 5), Blocks.WATER);
		}, EAST.getX(), EAST.getZ(), h -> {
			BlockPos at = h.absolutePos(new BlockPos(13, 1, 5));
			return Component.translatable("screen.aliveworkplace.move.fit.water.at", at.getX(), at.getY(), at.getZ());
		});
	}

	/** Rotate: the front drawn on the map turns with it, and the button's words say where it faces. */
	//$ gametest_ticks_batch AREA '100' '"moveGuideRotate"'
	@GameTest(template = AREA, timeoutTicks = 100, batch = "moveGuideRotate")
	public void rotateTurnsTheFrontOnTheMap(GameTestHelper helper) {
		Village v = stage(helper);
		helper.runAfterDelay(2, () -> {
			ChoiceMenu menu = openPicker(helper, v);
			clickMap(helper, v, menu, EAST.getX(), EAST.getZ());
			Direction before = Direction.from2DDataValue(view(menu).getInt("front"));
			menu.press(MovePage.ROTATE, v.player());
			Direction after = Direction.from2DDataValue(view(menu).getInt("front"));
			helper.assertTrue(before == Direction.NORTH && after == Direction.EAST, "the front on the map: " + before + " then " + after);
			helper.assertTrue(text("screen.aliveworkplace.move.rotate_button", Component.translatable("screen.aliveworkplace.move.direction.east"))
				.equals("Rotate (front faces east)"), "the Rotate button's words");
			helper.assertTrue(step(menu) == 3, "a turned spot that fits isn't step 3: " + step(menu));
			helper.succeed();
		});
	}

	/** Cancel: the pick, the map and the ghost are forgotten; a map click after it does nothing. */
	//$ gametest_ticks_batch AREA '100' '"moveGuideCancel"'
	@GameTest(template = AREA, timeoutTicks = 100, batch = "moveGuideCancel")
	public void cancelForgetsTheMap(GameTestHelper helper) {
		Village v = stage(helper);
		helper.runAfterDelay(2, () -> {
			ChoiceMenu menu = openPicker(helper, v);
			BlockPos at = helper.absolutePos(EAST);
			int id = MovePage.mapClick(v.player(), at.getX(), at.getZ());
			menu.press(MovePage.CANCEL, v.player());
			helper.assertTrue(MovePage.picking(v.player()).isEmpty() && BlueprintOutline.moveGhost(v.player()) == null, "still picking after Cancel");
			helper.assertTrue(MovePage.mapClick(v.player(), at.getX(), at.getZ()) == -1, "the map is kept after Cancel");
			helper.assertTrue(!menu.clickMenuButton(v.player(), id), "a map click after Cancel was taken");
			helper.assertTrue(MovePage.picking(v.player()).isEmpty() && PendingMoves.get(v.level()).forBuilding(v.hut()).isEmpty(), "a map click after Cancel picked or saved");
			CustomData row = menu.icon(VillageHallScreen.FIRST_ROW).get(DataComponents.CUSTOM_DATA);
			helper.assertTrue(row != null && row.contains(MovePage.LIST_MARK), "Cancel didn't go back to step 1: " + menu.icon(VillageHallScreen.FIRST_ROW));
			helper.succeed();
		});
	}

	/** Confirm without a Mover: refused, nothing saved; the guide says a Mover must be hired. */
	//$ gametest_ticks_batch AREA '100' '"moveGuideNoMover"'
	@GameTest(template = AREA, timeoutTicks = 100, batch = "moveGuideNoMover")
	public void confirmWithoutAMoverIsRefusedAndSaysHowToHireOne(GameTestHelper helper) {
		Village v = stage(helper);
		helper.runAfterDelay(2, () -> {
			ChoiceMenu menu = openPicker(helper, v);
			clickMap(helper, v, menu, EAST.getX(), EAST.getZ());
			helper.assertTrue(lore(menu.icon(MovePage.GUIDE)).get(4).equals("No Mover yet: place a Mover's Crate for a jobless villager to claim"),
				"the Mover line: " + lore(menu.icon(MovePage.GUIDE)));
			menu.press(MovePage.CONFIRM, v.player());
			helper.assertTrue(PendingMoves.get(v.level()).forBuilding(v.hut()).isEmpty(), "a move was saved with no Mover");
			helper.assertTrue(MovePage.picking(v.player()).isPresent() && step(menu) == 3, "the pick was dropped");
			helper.succeed();
		});
	}

	/** Confirm with a Mover: step 3 says to press Confirm, the Mover line is green, and the move is saved; back on step 1. */
	//$ gametest_ticks_batch AREA '100' '"moveGuideConfirm"'
	@GameTest(template = AREA, timeoutTicks = 100, batch = "moveGuideConfirm")
	public void confirmWithAMoverSavesTheMove(GameTestHelper helper) {
		Village v = stage(helper);
		Villager mover = helper.spawn(EntityType.VILLAGER, new BlockPos(10, 2, 15));
		Movers.isMover = villager -> villager.getUUID().equals(mover.getUUID());
		helper.runAfterDelay(2, () -> {
			ChoiceMenu menu = openPicker(helper, v);
			clickMap(helper, v, menu, EAST.getX(), EAST.getZ());
			helper.assertTrue(menu.icon(MovePage.GUIDE).getHoverName().getString().equals("Step 3 of 3: the spot fits. Press Confirm and a Mover moves the " + name()),
				"the instruction: " + menu.icon(MovePage.GUIDE).getHoverName().getString());
			helper.assertTrue(lore(menu.icon(MovePage.GUIDE)).get(4).equals(text("screen.aliveworkplace.move.mover_here")), "the Mover line: " + lore(menu.icon(MovePage.GUIDE)));
			helper.assertTrue(menu.icon(MovePage.CONFIRM).is(Items.PISTON), "Confirm isn't ready: " + menu.icon(MovePage.CONFIRM));
			menu.press(MovePage.CONFIRM, v.player());
			PendingMoves.Move move = PendingMoves.get(v.level()).forBuilding(v.hut()).orElseThrow(() -> new AssertionError("no move saved"));
			helper.assertTrue(move.to().equals(v.hut().offset(8, 0, 0)), "the move: " + move);
			ItemStack row = menu.icon(VillageHallScreen.FIRST_ROW);
			helper.assertTrue(row.is(Items.STICKY_PISTON) && row.get(DataComponents.CUSTOM_DATA) != null && row.get(DataComponents.CUSTOM_DATA).contains(MovePage.LIST_MARK),
				"not back on step 1 with the move marked: " + row);
			helper.succeed();
		});
	}

	/** How long: at least a minute, and a bigger building takes longer (each block taken down and put up again). */
	//$ gametest_ticks_batch AREA '100' '"moveGuideTime"'
	@GameTest(template = AREA, timeoutTicks = 100, batch = "moveGuideTime")
	public void theTimeGrowsWithTheBuilding(GameTestHelper helper) {
		Village v = stage(helper);
		helper.runAfterDelay(2, () -> {
			openPicker(helper, v);
			io.github.jcondedata.aliveworkplace.move.MovePlan hut = MovePage.picking(v.player()).orElseThrow().plan();
			int small = MovePage.minutes(hut);
			helper.assertTrue(small >= 1, "under a minute: " + small);
			// a plan of the whole test area round the hut: many more blocks
			net.minecraft.world.level.levelgen.structure.BoundingBox box = net.minecraft.world.level.levelgen.structure.BoundingBox.fromCorners(
				helper.absolutePos(new BlockPos(0, 0, 0)), helper.absolutePos(new BlockPos(20, 6, 20)));
			io.github.jcondedata.aliveworkplace.move.MovePlan bigger = io.github.jcondedata.aliveworkplace.move.MovePlan.capture(v.level(), box, Direction.NORTH, null).orElseThrow();
			helper.assertTrue(bigger.blocks().size() > hut.blocks().size() && MovePage.minutes(bigger) > small,
				"a bigger plan (" + bigger.blocks().size() + " blocks, " + MovePage.minutes(bigger) + " min) isn't longer than the hut (" + hut.blocks().size() + ", " + small + ")");
			helper.assertTrue(Words.counted("screen.aliveworkplace.move.time", 1, 1).getString().equals("About 1 minute of a Mover's work"),
				"one minute: " + Words.counted("screen.aliveworkplace.move.time", 1, 1).getString());
			helper.succeed();
		});
	}
}
