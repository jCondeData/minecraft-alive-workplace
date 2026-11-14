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
import io.github.jcondedata.aliveworkplace.move.MoveSites;
import io.github.jcondedata.aliveworkplace.move.Movers;
import io.github.jcondedata.aliveworkplace.move.PendingMoves;
import io.github.jcondedata.aliveworkplace.registry.ModBlocks;
import io.github.jcondedata.aliveworkplace.work.ChoiceMenu;
import java.util.ArrayList;
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
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings;

/**
 * QA (lane C, 2026-11-11): the Village Hall's reworked Move page (B1163, 45.2a, on 45.2), written from the spec, not the
 * code. The owner: a first-time player understands without the wiki which building is being moved, where it is now and
 * where it would go, what the Mover will do and how long, with a clear Confirm and Cancel; 45.2: a fit check that says
 * plainly why a spot is refused (overlaps a building or the Hall, a road, outside the village, the ground rising more than 3
 * blocks, under water) and a rotate button. MovePageGameTests and MovePageGuideGameTests cover each refusal once; these
 * walk what they leave out: the Hall itself in the way, a building still going up in the way (named on the map), the
 * boundaries from both sides (the ground rising 3 then 4 blocks, the village's edge, "next to the Hall" against "4 blocks
 * west"), four turns back to the start, Cancel / Back / closing leaving the building exactly as it was, Confirm then a
 * fresh Hall showing the planned move, and every sentence on the page in each state read as plain American English.
 */
public class QaMovePage1111cGameTests implements FabricGameTest {
	private static final String AREA = "aliveworkplace_test:big_area";
	private static final ResourceLocation HUT = ResourceLocation.fromNamespaceAndPath("aliveworkplace_test", "test_hut");
	private static final BlockPos HALL = new BlockPos(15, 2, 11);
	/** The finished Test Hut: x 3-7, y 2-5, z 3-7; its middle (5, 5). */
	private static final BlockPos HUT_ORIGIN = new BlockPos(3, 2, 3);
	/** The block 8 east of the hut's middle: a spot there is x 11-15, z 3-7, and fits on flat ground. */
	private static final BlockPos EAST = new BlockPos(13, 2, 5);

	private record Village(ServerLevel level, BlockPos hall, BlockPos hut, ServerPlayer player) {
	}

	/** The hall, the finished hut and a player, with the village {@code radius} blocks round the hall; no Mover. */
	private static Village stage(GameTestHelper helper, int radius) {
		Leftovers.clear(helper);
		ServerLevel level = helper.getLevel();
		int was = VillageHalls.RADIUS;
		VillageHalls.RADIUS = radius;
		Predicate<Villager> mover = Movers.isMover;
		Movers.isMover = villager -> false;
		BuildSiteManager sites = BuildSiteManager.get(level);
		BlueprintData.Placement hut = placement(helper, HUT_ORIGIN);
		ServerPlayer player = helper.makeMockServerPlayerInLevel();
		Leftovers.after(helper, () -> {
			VillageHalls.RADIUS = was;
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

	private static String name() {
		return Blueprints.displayName(HUT).getString();
	}

	private static CompoundTag view(ChoiceMenu menu) {
		CustomData data = menu.icon(MovePage.VIEW).get(DataComponents.CUSTOM_DATA);
		return data == null ? new CompoundTag() : data.copyTag().getCompound(MovePage.VIEW_MARK);
	}

	private static int step(ChoiceMenu menu) {
		CustomData data = menu.icon(MovePage.GUIDE).get(DataComponents.CUSTOM_DATA);
		return data == null ? -1 : data.copyTag().getInt(MovePage.STEP_MARK);
	}

	private static String instruction(ChoiceMenu menu) {
		return menu.icon(MovePage.GUIDE).getHoverName().getString();
	}

	private static String verdict(ChoiceMenu menu) {
		return menu.icon(MovePage.VERDICT).getHoverName().getString();
	}

	/** The guide's second line: where the building would go ("New spot: ..."). */
	private static String newSpot(ChoiceMenu menu) {
		List<String> lines = lore(menu.icon(MovePage.GUIDE));
		return lines.size() > 1 ? lines.get(1) : "(no second line: " + lines + ")";
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

	/** The refused state as the player reads it: step 2 with "won't work", the plain reason, a red map box, Confirm gray and saying why. */
	private static void assertRefused(GameTestHelper helper, ChoiceMenu menu, String reason) {
		helper.assertTrue(verdict(menu).equals(reason), "the reason says \"" + verdict(menu) + "\", not \"" + reason + "\"");
		helper.assertTrue(step(menu) == 2, "a refused spot isn't step 2: " + step(menu));
		helper.assertTrue(instruction(menu).equals("Step 2 of 3: that spot won't work for the " + name() + ". Click another spot on the map"),
			"the instruction: " + instruction(menu));
		helper.assertTrue(view(menu).getInt("fit") == 2, "the map's new spot isn't red: " + view(menu).getInt("fit"));
		helper.assertTrue(menu.icon(MovePage.CONFIRM).is(Items.GRAY_DYE), "Confirm looks ready on a refused spot: " + menu.icon(MovePage.CONFIRM));
		helper.assertTrue(lore(menu.icon(MovePage.CONFIRM)).contains(reason), "Confirm doesn't say why it can't: " + lore(menu.icon(MovePage.CONFIRM)));
	}

	/** The fitting state, no Mover hired: step 3, "This spot fits", a green map box. */
	private static void assertFits(GameTestHelper helper, ChoiceMenu menu) {
		helper.assertTrue(verdict(menu).equals("This spot fits"), "the fit line: " + verdict(menu));
		helper.assertTrue(step(menu) == 3, "a spot that fits isn't step 3: " + step(menu));
		helper.assertTrue(view(menu).getInt("fit") == 0, "the map's new spot isn't green: " + view(menu).getInt("fit"));
	}

	/**
	 * 45.2 / guide: "overlap another building or the Village Hall". A spot over the Hall is refused with the Hall named; a
	 * spot 4 blocks west of it fits and reads "4 blocks west of the Hall"; 3 blocks west reads "next to the Hall".
	 */
	//$ gametest_ticks_batch AREA '100' '"qaMove1111cHall"'
	@GameTest(template = AREA, timeoutTicks = 100, batch = "qaMove1111cHall")
	public void aSpotOverTheHallIsRefusedAndNearItReadsPlainly(GameTestHelper helper) {
		Village v = stage(helper, 16);
		helper.runAfterDelay(2, () -> {
			ChoiceMenu menu = openPicker(helper, v);
			clickMap(helper, v, menu, 15, 9); // x 13-17, z 7-11: the Hall's block at (15, 11)
			assertRefused(helper, menu, "Refused: it would overlap the Village Hall");
			BlueprintOutline.MoveGhost ghost = BlueprintOutline.moveGhost(v.player());
			helper.assertTrue(ghost != null && !ghost.fits(), "the ghost over the Hall isn't red: " + ghost);
			helper.assertTrue(newSpot(menu).startsWith("New spot: "), "where it would go: " + newSpot(menu));
			clickMap(helper, v, menu, 11, 11); // x 9-13: 4 blocks west of the Hall, clear of it
			assertFits(helper, menu);
			helper.assertTrue(newSpot(menu).equals("New spot: 4 blocks west of the Hall"), "4 west: " + newSpot(menu));
			clickMap(helper, v, menu, 12, 11); // x 10-14: 3 blocks west, still clear of it
			assertFits(helper, menu);
			helper.assertTrue(newSpot(menu).equals("New spot: next to the Hall"), "3 west: " + newSpot(menu));
			helper.succeed();
		});
	}

	/**
	 * 45.2: "overlaps a building"; wiki: "every other building as a named brick box". A building still going up (a live
	 * build site) is on the map with its name and refuses a spot over it, named; once it's gone the same spot fits.
	 */
	//$ gametest_ticks_batch AREA '100' '"qaMove1111cGoingUp"'
	@GameTest(template = AREA, timeoutTicks = 100, batch = "qaMove1111cGoingUp")
	public void aBuildingGoingUpIsNamedOnTheMapAndInTheWay(GameTestHelper helper) {
		Village v = stage(helper, 16);
		BuildSiteManager sites = BuildSiteManager.get(v.level());
		UUID id = sites.create(UUID.randomUUID(), "Builder", HUT, placement(helper, new BlockPos(12, 2, 3))).id();
		Leftovers.after(helper, () -> sites.remove(id));
		helper.runAfterDelay(2, () -> {
			ChoiceMenu menu = openPicker(helper, v);
			BlockPos corner = helper.absolutePos(new BlockPos(12, 2, 3));
			int[] others = view(menu).getIntArray("others");
			boolean drawn = false;
			for (int i = 0; i + 3 < others.length; i += 4) {
				drawn |= others[i] == corner.getX() && others[i + 1] == corner.getZ() && others[i + 2] == corner.getX() + 4 && others[i + 3] == corner.getZ() + 4;
			}
			helper.assertTrue(drawn, "the building going up isn't on the map: " + java.util.Arrays.toString(others));
			helper.assertTrue(lore(menu.icon(MovePage.VIEW)).contains(name()), "the map doesn't name it: " + lore(menu.icon(MovePage.VIEW)));
			clickMap(helper, v, menu, EAST.getX(), EAST.getZ());
			assertRefused(helper, menu, "Refused: it would overlap " + name());
			sites.remove(id);
			clickMap(helper, v, menu, EAST.getX(), EAST.getZ());
			assertFits(helper, menu);
			helper.succeed();
		});
	}

	/** 45.2 guide: refused when "the ground rises more than 3 blocks across it". Exactly 3 fits; 4 is refused, plainly. */
	//$ gametest_ticks_batch AREA '100' '"qaMove1111cSteep"'
	@GameTest(template = AREA, timeoutTicks = 100, batch = "qaMove1111cSteep")
	public void theGroundMayRiseThreeBlocksButNotFour(GameTestHelper helper) {
		Village v = stage(helper, 16);
		for (int x = 14; x <= 15; x++) {
			for (int z = 3; z <= 7; z++) {
				for (int y = 2; y <= 4; y++) {
					helper.setBlock(new BlockPos(x, y, z), Blocks.STONE); // the floor's top is y 1: these columns rise 3
				}
			}
		}
		helper.runAfterDelay(2, () -> {
			helper.assertTrue(MoveFit.MAX_STEP == 3, "the spec's 3 blocks: " + MoveFit.MAX_STEP);
			ChoiceMenu menu = openPicker(helper, v);
			clickMap(helper, v, menu, EAST.getX(), EAST.getZ());
			assertFits(helper, menu);
			for (int x = 14; x <= 15; x++) {
				for (int z = 3; z <= 7; z++) {
					helper.setBlock(new BlockPos(x, 5, z), Blocks.STONE); // now they rise 4
				}
			}
			clickMap(helper, v, menu, EAST.getX(), EAST.getZ());
			assertRefused(helper, menu, "Refused: the ground there is too steep");
			helper.succeed();
		});
	}

	/**
	 * 45.2: refused "outside the village"; guide: "stick out of the village". The village's edge from both sides (radius 12
	 * round the Hall): a spot whose far corners are 11 rows north of the Hall fits, one row further it is refused.
	 */
	//$ gametest_ticks_batch AREA '100' '"qaMove1111cEdge"'
	@GameTest(template = AREA, timeoutTicks = 100, batch = "qaMove1111cEdge")
	public void theVillagesEdgeFromBothSides(GameTestHelper helper) {
		Village v = stage(helper, 12);
		helper.runAfterDelay(2, () -> {
			ChoiceMenu menu = openPicker(helper, v);
			helper.assertTrue(VillageHalls.contains(v.hall(), helper.absolutePos(new BlockPos(13, 2, 0)))
				&& !VillageHalls.contains(v.hall(), helper.absolutePos(new BlockPos(13, 2, -1))), "the edge isn't where this test expects it (setup)");
			clickMap(helper, v, menu, 15, 2); // x 13-17, z 0-4: its corners (13, 0) and (17, 0) just inside
			assertFits(helper, menu);
			clickMap(helper, v, menu, 15, 1); // z -1 to 3: its corners a block past the edge
			assertRefused(helper, menu, "Refused: part of it would be outside the village");
			helper.succeed();
		});
	}

	/**
	 * 45.2: "a rotate button". Four turns on a picked spot face east, south, west and north, the button saying where the
	 * next turn faces, and come back to exactly where they started: the spot, the turn, the guide and the fit line. A click
	 * back on its own spot is "not picked yet" again.
	 */
	//$ gametest_ticks_batch AREA '100' '"qaMove1111cRotate"'
	@GameTest(template = AREA, timeoutTicks = 100, batch = "qaMove1111cRotate")
	public void fourTurnsComeBackToTheStart(GameTestHelper helper) {
		Village v = stage(helper, 16);
		helper.runAfterDelay(2, () -> {
			ChoiceMenu menu = openPicker(helper, v);
			clickMap(helper, v, menu, EAST.getX(), EAST.getZ());
			MovePage.Picking before = MovePage.picking(v.player()).orElseThrow();
			List<String> guideBefore = lore(menu.icon(MovePage.GUIDE));
			String instructionBefore = instruction(menu);
			String verdictBefore = verdict(menu);
			int[] spotBefore = view(menu).getIntArray("spot");
			helper.assertTrue(Direction.from2DDataValue(view(menu).getInt("front")) == Direction.NORTH, "the front before turning: " + view(menu).getInt("front"));
			Direction[] faces = {Direction.EAST, Direction.SOUTH, Direction.WEST, Direction.NORTH};
			for (int i = 0; i < 4; i++) {
				String hint = "A quarter turn: its front will face " + faces[i].getName();
				helper.assertTrue(lore(menu.icon(MovePage.ROTATE)).contains(hint), "turn " + i + ", the button (" + hint + "): " + lore(menu.icon(MovePage.ROTATE)));
				menu.press(MovePage.ROTATE, v.player());
				Direction front = Direction.from2DDataValue(view(menu).getInt("front"));
				BlueprintOutline.MoveGhost ghost = BlueprintOutline.moveGhost(v.player());
				helper.assertTrue(front == faces[i] && ghost != null && ghost.front() == faces[i], "turn " + i + ": the map's front " + front + ", the ghost " + ghost);
				helper.assertTrue(step(menu) == 3, "turn " + i + " on a square spot that fits isn't step 3: " + step(menu));
			}
			MovePage.Picking after = MovePage.picking(v.player()).orElseThrow();
			helper.assertTrue(after.rotation() == Rotation.NONE && after.x() == before.x() && after.z() == before.z(), "four turns: " + before + " became " + after);
			helper.assertTrue(lore(menu.icon(MovePage.GUIDE)).equals(guideBefore) && instruction(menu).equals(instructionBefore) && verdict(menu).equals(verdictBefore),
				"the words changed after four turns: " + instruction(menu) + " / " + lore(menu.icon(MovePage.GUIDE)) + " / " + verdict(menu));
			helper.assertTrue(java.util.Arrays.equals(view(menu).getIntArray("spot"), spotBefore), "the map's spot moved: " + java.util.Arrays.toString(view(menu).getIntArray("spot")));
			clickMap(helper, v, menu, 5, 5); // back on its own spot, unturned
			helper.assertTrue(step(menu) == 2 && instruction(menu).equals("Step 2 of 3: click the map where the " + name() + " should go"), "on its own spot: " + instruction(menu));
			helper.assertTrue(newSpot(menu).equals("New spot: not picked yet"), "on its own spot: " + newSpot(menu));
			helper.assertTrue(verdict(menu).equals("Pick a new spot on the map, or rotate it") && view(menu).getInt("fit") == 1, "on its own spot: " + verdict(menu));
			helper.assertTrue(menu.icon(MovePage.CONFIRM).is(Items.GRAY_DYE), "Confirm is ready with no new spot: " + menu.icon(MovePage.CONFIRM));
			helper.succeed();
		});
	}

	/** Every block of the hut and a block round it. */
	private static List<BlockState> snapshot(GameTestHelper helper) {
		List<BlockState> out = new ArrayList<>();
		for (int x = 2; x <= 8; x++) {
			for (int y = 1; y <= 6; y++) {
				for (int z = 2; z <= 8; z++) {
					out.add(helper.getBlockState(new BlockPos(x, y, z)));
				}
			}
		}
		return out;
	}

	/** Nothing changed: no pick, no ghost, no map, no planned move, no move going on, the building listed and every block as it was. */
	private static void assertUntouched(GameTestHelper helper, Village v, List<BlockState> before, String how) {
		helper.assertTrue(MovePage.picking(v.player()).isEmpty(), how + ": still picking");
		helper.assertTrue(BlueprintOutline.moveGhost(v.player()) == null, how + ": the ghost stayed");
		helper.assertTrue(MovePage.mapClick(v.player(), v.hall().getX(), v.hall().getZ()) == -1, how + ": the map was kept");
		helper.assertTrue(PendingMoves.get(v.level()).forBuilding(v.hut()).isEmpty(), how + ": a move was planned");
		helper.assertTrue(MoveSites.get(v.level()).forBuilding(v.hut()).isEmpty(), how + ": a move started");
		helper.assertTrue(BuildSiteManager.get(v.level()).finishedInVillage(v.level(), v.hall()).stream().anyMatch(f -> f.placement().origin().equals(v.hut())),
			how + ": the hut is no longer one of the village's buildings");
		helper.assertTrue(snapshot(helper).equals(before), how + ": the hut's blocks changed");
	}

	/**
	 * Owner: "a clear Confirm/Cancel"; guide: "Cancel leaves the building where it is". Cancel (after a pick and a turn),
	 * Back, and closing the screen each leave everything as it was.
	 */
	//$ gametest_ticks_batch AREA '100' '"qaMove1111cCancel"'
	@GameTest(template = AREA, timeoutTicks = 100, batch = "qaMove1111cCancel")
	public void cancelBackAndClosingLeaveTheBuildingAsItWas(GameTestHelper helper) {
		Village v = stage(helper, 16);
		helper.runAfterDelay(2, () -> {
			List<BlockState> before = snapshot(helper);
			ChoiceMenu menu = openPicker(helper, v);
			helper.assertTrue(menu.icon(MovePage.CANCEL).getHoverName().getString().equals("Cancel")
				&& lore(menu.icon(MovePage.CANCEL)).contains("Leave the building where it is"), "the Cancel button: " + lore(menu.icon(MovePage.CANCEL)));
			clickMap(helper, v, menu, EAST.getX(), EAST.getZ());
			menu.press(MovePage.ROTATE, v.player());
			menu.press(MovePage.CANCEL, v.player());
			assertUntouched(helper, v, before, "Cancel");
			CustomData row = menu.icon(VillageHallScreen.FIRST_ROW).get(DataComponents.CUSTOM_DATA);
			helper.assertTrue(row != null && row.contains(MovePage.LIST_MARK) && menu.icon(VillageHallScreen.FIRST_ROW).is(Items.BRICKS),
				"Cancel didn't go back to the list unplanned: " + menu.icon(VillageHallScreen.FIRST_ROW));
			ChoiceMenu back = openPicker(helper, v);
			clickMap(helper, v, back, EAST.getX(), EAST.getZ());
			back.press(MovePage.BACK, v.player());
			assertUntouched(helper, v, before, "Back");
			ChoiceMenu closed = openPicker(helper, v);
			clickMap(helper, v, closed, EAST.getX(), EAST.getZ());
			closed.removed(v.player());
			assertUntouched(helper, v, before, "closing the screen");
			helper.succeed();
		});
	}

	/**
	 * Owner: Confirm; wiki: the list shows each building "and, once planned, where it's going", the tab counts the moves
	 * planned, "Call off the planned move once one is planned". Confirm with a Mover, then a fresh Hall: the tab says 1 move
	 * planned, the row where it's going, and its picker opens on the planned spot with Call off.
	 */
	//$ gametest_ticks_batch AREA '100' '"qaMove1111cReopen"'
	@GameTest(template = AREA, timeoutTicks = 100, batch = "qaMove1111cReopen")
	public void confirmThenAFreshHallShowsThePlannedMove(GameTestHelper helper) {
		Village v = stage(helper, 16);
		Villager mover = helper.spawn(EntityType.VILLAGER, new BlockPos(10, 2, 15));
		Movers.isMover = villager -> villager.getUUID().equals(mover.getUUID());
		helper.runAfterDelay(2, () -> {
			ChoiceMenu menu = openPicker(helper, v);
			clickMap(helper, v, menu, EAST.getX(), EAST.getZ());
			BlockPos to = MovePage.fit(v.player()).orElseThrow().min();
			helper.assertTrue(menu.icon(MovePage.CONFIRM).is(Items.PISTON), "Confirm isn't ready: " + menu.icon(MovePage.CONFIRM));
			menu.press(MovePage.CONFIRM, v.player());
			PendingMoves.Move move = PendingMoves.get(v.level()).forBuilding(v.hut()).orElseThrow(() -> new AssertionError("no move saved"));
			helper.assertTrue(move.to().equals(to) && to.equals(v.hut().offset(8, 0, 0)), "the move: " + move);
			// the screen closed and opened again
			menu.removed(v.player());
			ChoiceMenu again = VillageHallScreen.forTest(v.player(), v.hall());
			int tab = HallPages.slot(MovePage.PAGE);
			helper.assertTrue(lore(again.icon(tab)).contains("1 move planned"), "the tab: " + lore(again.icon(tab)));
			again.press(tab, v.player());
			ItemStack row = again.icon(VillageHallScreen.FIRST_ROW);
			String planned = "Move planned: to " + to.getX() + ", " + to.getY() + ", " + to.getZ();
			helper.assertTrue(row.is(Items.STICKY_PISTON) && lore(row).contains(planned), "the row (" + planned + "): " + row + " " + lore(row));
			helper.assertTrue(PendingMoves.get(v.level()).forBuilding(v.hut()).isPresent(), "opening the Hall dropped the move");
			again.press(VillageHallScreen.FIRST_ROW, v.player());
			MovePage.Picking p = MovePage.picking(v.player()).orElseThrow(() -> new AssertionError("the planned building's picker didn't open"));
			helper.assertTrue(p.rotation() == Rotation.NONE && MovePage.fit(v.player()).orElseThrow().min().equals(to), "not on the planned spot: " + p);
			helper.assertTrue(step(again) == 3 && view(again).getInt("fit") == 0, "the planned spot isn't shown as fitting: " + step(again) + ", " + view(again).getInt("fit"));
			helper.assertTrue(newSpot(again).startsWith("New spot: ") && newSpot(again).endsWith("of the Hall"), "where it's going: " + newSpot(again));
			int[] spot = view(again).getIntArray("spot");
			helper.assertTrue(spot.length == 4 && spot[0] == to.getX() && spot[1] == to.getZ(), "the map's new spot: " + java.util.Arrays.toString(spot));
			ItemStack callOff = again.icon(MovePage.CALL_OFF);
			helper.assertTrue(callOff.is(Items.COBWEB) && callOff.getHoverName().getString().equals("Call off the planned move") && lore(callOff).contains(planned),
				"Call off: " + callOff + " " + lore(callOff));
			helper.succeed();
		});
	}

	private static final String[] BRITISH = {"colour", "centre", "grey", "metre", "cancelled", "defence", "armour", "neighbour", "favour", "travelling", "labour"};

	/** Every name and line of slots {@code from} to the end, each tagged with {@code state} and its slot. */
	private static List<String> words(ChoiceMenu menu, int from, String state) {
		List<String> out = new ArrayList<>();
		for (int slot = from; slot < ChoiceMenu.SIZE; slot++) {
			ItemStack icon = menu.icon(slot);
			if (icon.isEmpty() || icon.has(DataComponents.HIDE_TOOLTIP)) {
				continue; // a divider pane: nothing to read
			}
			out.add(state + " #" + slot + ": " + icon.getHoverName().getString());
			for (String line : lore(icon)) {
				out.add(state + " #" + slot + ": " + line);
			}
		}
		return out;
	}

	/** No raw key, no unfilled argument, no "null", no British spelling (the village's own name left out of the last). */
	private static void assertPlain(GameTestHelper helper, List<String> all, String village) {
		for (String line : all) {
			String said = line.substring(line.indexOf(": ") + 2);
			helper.assertTrue(!said.isBlank(), "an empty sentence: " + line);
			helper.assertTrue(!said.contains("aliveworkplace") && !said.contains("screen.") && !said.contains("message."), "a raw key: " + line);
			helper.assertTrue(!said.contains("%s") && !said.contains("%1$") && !said.contains("%d"), "an unfilled argument: " + line);
			String lower = said.replace(village, "").toLowerCase();
			helper.assertTrue(!lower.contains("null"), "null: " + line);
			for (String word : BRITISH) {
				helper.assertTrue(!lower.contains(word), "British spelling \"" + word + "\": " + line);
			}
		}
	}

	/**
	 * Every sentence a player reads on the page, rendered with its real arguments in each state: the list (step 1), the
	 * picker with no spot (step 2), a spot that fits (step 3), a refused spot with a place in it (a road), a turned spot.
	 * None is a raw key, an unfilled %s or "null", and each is American English; the building's own name is there.
	 */
	//$ gametest_ticks_batch AREA '100' '"qaMove1111cWords"'
	@GameTest(template = AREA, timeoutTicks = 100, batch = "qaMove1111cWords")
	public void everySentenceOnThePageIsPlainAmericanEnglish(GameTestHelper helper) {
		Village v = stage(helper, 16);
		helper.setBlock(new BlockPos(9, 1, 15), Blocks.DIRT_PATH);
		helper.runAfterDelay(2, () -> {
			String village = VillageHalls.name(v.level(), v.hall()).getString();
			List<String> all = new ArrayList<>();
			ChoiceMenu menu = VillageHallScreen.forTest(v.player(), v.hall());
			int tab = HallPages.slot(MovePage.PAGE);
			all.add("tab #" + tab + ": " + menu.icon(tab).getHoverName().getString());
			for (String line : lore(menu.icon(tab))) {
				all.add("tab #" + tab + ": " + line);
			}
			menu.press(tab, v.player());
			all.addAll(words(menu, 0, "list"));
			menu.press(VillageHallScreen.FIRST_ROW, v.player());
			List<String> step2 = words(menu, 0, "step 2");
			helper.assertTrue(step2.stream().anyMatch(l -> l.contains(name())), "the building isn't named on the picker: " + step2);
			all.addAll(step2);
			clickMap(helper, v, menu, EAST.getX(), EAST.getZ());
			all.addAll(words(menu, 0, "step 3"));
			menu.press(MovePage.ROTATE, v.player());
			all.addAll(words(menu, 0, "turned"));
			clickMap(helper, v, menu, 9, 15); // x 7-11, z 13-17, over the path at (9, 15)
			BlockPos road = helper.absolutePos(new BlockPos(9, 1, 15));
			String why = "Refused: it would sit on a road (at " + road.getX() + ", " + road.getY() + ", " + road.getZ() + ")";
			helper.assertTrue(verdict(menu).equals(why), "the road line: " + verdict(menu));
			all.addAll(words(menu, 0, "refused"));
			helper.assertTrue(all.size() > 40, "too few sentences read to judge: " + all.size());
			assertPlain(helper, all, village);
			helper.succeed();
		});
	}
}
