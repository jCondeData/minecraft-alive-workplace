package io.github.jcondedata.aliveworkplace.test;

import io.github.jcondedata.aliveworkplace.blueprint.BlueprintData;
import io.github.jcondedata.aliveworkplace.blueprint.Blueprints;
import io.github.jcondedata.aliveworkplace.build.BuildSiteManager;
import io.github.jcondedata.aliveworkplace.hall.HallPages;
import io.github.jcondedata.aliveworkplace.hall.MovePage;
import io.github.jcondedata.aliveworkplace.hall.VillageHallScreen;
import io.github.jcondedata.aliveworkplace.hall.VillageHalls;
import io.github.jcondedata.aliveworkplace.move.MoveSite;
import io.github.jcondedata.aliveworkplace.move.MoveSites;
import io.github.jcondedata.aliveworkplace.move.Movers;
import io.github.jcondedata.aliveworkplace.move.PendingMoves;
import io.github.jcondedata.aliveworkplace.registry.ModBlocks;
import io.github.jcondedata.aliveworkplace.work.ChoiceMenu;
import java.util.List;
import java.util.UUID;
import java.util.function.Predicate;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.core.BlockPos;
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
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings;

/**
 * QA (lane E, 2026-11-11): B1174's fix (MoveFit#moving: a spot over another building's planned or under-way new spot is
 * refused) across a save and reload, and once that other move is called off or finished. 45.2: a spot is "refused:
 * overlaps a building"; a called-off move leaves its building where it is; 45.3: a finished move's building stands at its
 * new spot. Two finished Test Huts, A (x 3-7, z 3-7) and B (x 3-7, z 12-16); A's move to S (x 11-15, z 3-7) is
 * confirmed on its own picker. Also the planned spot's edge from both sides, and A's own planned spot never refusing A;
 * a quarter-turned move of a building that isn't square is not covered here (the test huts are square). Helpers adapted from QaMovePage1111cBugTests (lane C).
 */
public class QaMoveSpotSaves1111eGameTests implements FabricGameTest {
	private static final String AREA = "aliveworkplace_test:big_area";
	private static final ResourceLocation HUT = ResourceLocation.fromNamespaceAndPath("aliveworkplace_test", "test_hut");
	private static final BlockPos HALL = new BlockPos(15, 2, 11);
	/** Building A, the Test Hut: x 3-7, y 2-5, z 3-7; its middle (5, 5). */
	private static final BlockPos A_ORIGIN = new BlockPos(3, 2, 3);
	/** Building B, a second Test Hut: x 3-7, y 2-5, z 12-16. */
	private static final BlockPos B_ORIGIN = new BlockPos(3, 2, 12);
	/** S, A's new spot: its middle 8 east of A's, x 11-15, z 3-7. */
	private static final BlockPos S = new BlockPos(13, 2, 5);
	/** A spot well clear of S (and of the Hall): x 11-15, z 15-19. */
	private static final BlockPos CLEAR = new BlockPos(13, 2, 17);

	record Village(ServerLevel level, BlockPos hall, BlockPos a, BlockPos b, ServerPlayer player) {
	}

	static Village stage(GameTestHelper helper) {
		Leftovers.clear(helper);
		ServerLevel level = helper.getLevel();
		int radius = VillageHalls.RADIUS;
		VillageHalls.RADIUS = 16;
		Predicate<Villager> mover = Movers.isMover;
		BuildSiteManager sites = BuildSiteManager.get(level);
		BlueprintData.Placement a = placement(helper, A_ORIGIN);
		BlueprintData.Placement b = placement(helper, B_ORIGIN);
		ServerPlayer player = helper.makeMockServerPlayerInLevel();
		Leftovers.after(helper, () -> {
			for (BlockPos from : List.of(a.origin(), b.origin())) {
				MoveSites.get(level).cancel(level, from);
				for (MoveSite s : MoveSites.get(level).all().stream().filter(m -> m.from().equals(from)).toList()) {
					for (int i = 0; i < 100_000 && s.stage() != MoveSite.Stage.DONE; i++) {
						MoveSites.get(level).step(level, s);
					}
				}
				PendingMoves.get(level).remove(from);
			}
			VillageHalls.RADIUS = radius;
			Movers.isMover = mover;
			sites.forgetFinished(a);
			sites.forgetFinished(b);
			MovePage.stop(player);
		});
		helper.setBlock(HALL, ModBlocks.VILLAGE_HALL);
		for (BlueprintData.Placement p : List.of(a, b)) {
			level.getStructureManager().get(HUT).orElseThrow().placeInWorld(level, p.origin(), p.origin(), new StructurePlaceSettings(), RandomSource.create(1L), 2);
			sites.recordFinished(HUT, p, UUID.randomUUID());
		}
		Villager hired = helper.spawn(EntityType.VILLAGER, new BlockPos(18, 2, 19));
		Movers.isMover = villager -> villager.getUUID().equals(hired.getUUID());
		return new Village(level, helper.absolutePos(HALL), a.origin(), b.origin(), player);
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

	/** A fresh Hall, its Move tab, then the row of the building at {@code from}: its picker. */
	private static ChoiceMenu openPickerFor(GameTestHelper helper, Village v, BlockPos from) {
		for (int slot = VillageHallScreen.FIRST_ROW; slot < ChoiceMenu.SIZE; slot++) {
			ChoiceMenu menu = VillageHallScreen.forTest(v.player(), v.hall());
			menu.press(HallPages.slot(MovePage.PAGE), v.player());
			if (menu.icon(slot).isEmpty()) {
				break;
			}
			menu.press(slot, v.player());
			if (MovePage.picking(v.player()).map(p -> p.from().equals(from)).orElse(false)) {
				return menu;
			}
			MovePage.stop(v.player());
		}
		throw new AssertionError("no row on the Move page opens the picker of the building at " + from);
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

	/** A's move to S planned through its own picker and Confirm (a Mover is hired). */
	static PendingMoves.Move planA(GameTestHelper helper, Village v) {
		ChoiceMenu menu = openPickerFor(helper, v, v.a());
		clickMap(helper, v, menu, S.getX(), S.getZ());
		helper.assertTrue(menu.icon(MovePage.CONFIRM).is(Items.PISTON), "A's Confirm isn't ready (setup): " + menu.icon(MovePage.CONFIRM) + " " + lore(menu.icon(MovePage.CONFIRM)));
		menu.press(MovePage.CONFIRM, v.player());
		PendingMoves.Move move = PendingMoves.get(v.level()).forBuilding(v.a()).orElseThrow(() -> new AssertionError("A's move wasn't saved (setup)"));
		helper.assertTrue(move.to().equals(v.a().offset(8, 0, 0)), "A's move (setup): " + move);
		menu.removed(v.player());
		return move;
	}


	/** The verdict line on B's picker with its spot's middle on ({@code x}, {@code z}). */
	private static String verdictForB(GameTestHelper helper, Village v, int x, int z) {
		ChoiceMenu menu = openPickerFor(helper, v, v.b());
		clickMap(helper, v, menu, x, z);
		String line = menu.icon(MovePage.VERDICT).getHoverName().getString();
		boolean gray = menu.icon(MovePage.CONFIRM).is(Items.GRAY_DYE);
		menu.removed(v.player());
		MovePage.stop(v.player());
		return line + (gray ? " [Confirm gray]" : " [Confirm ready]");
	}

	private static void assertRefusedOverS(GameTestHelper helper, Village v, String when) {
		String over = verdictForB(helper, v, S.getX(), S.getZ());
		helper.assertTrue(over.equals("Refused: it would overlap " + name() + " [Confirm gray]"), when + ": B over the spot A is moving to: \"" + over
			+ "\", not \"Refused: it would overlap " + name() + "\" with Confirm gray (two moves onto the same ground)");
		String clear = verdictForB(helper, v, CLEAR.getX(), CLEAR.getZ());
		helper.assertTrue(clear.equals("This spot fits [Confirm ready]"), when + ": control, B well clear of S: " + clear);
	}

	/** Saves the two moves' records and loads them back, as a server restart does; the loaded ones replace the live ones. */
	private static void reload(ServerLevel level) {
		CompoundTag pending = PendingMoves.get(level).save(new CompoundTag(), level.registryAccess());
		CompoundTag sites = MoveSites.get(level).save(new CompoundTag(), level.registryAccess());
		level.getDataStorage().set(PendingMoves.NAME, PendingMoves.load(pending, level.registryAccess()));
		level.getDataStorage().set(MoveSites.NAME, MoveSites.load(sites, level.registryAccess()));
	}

	/** A's move to S planned, then the world saved and reloaded: B over S is still refused, naming A. */
	//$ gametest_ticks_batch AREA '100' '"qaMoveSaves1111ePlanned"'
	@GameTest(template = AREA, timeoutTicks = 100, batch = "qaMoveSaves1111ePlanned")
	public void aPlannedMovesSpotIsStillRefusedAfterAReload(GameTestHelper helper) {
		Village v = stage(helper);
		helper.runAfterDelay(2, () -> {
			planA(helper, v);
			reload(v.level());
			helper.assertTrue(PendingMoves.get(v.level()).forBuilding(v.a()).isPresent(), "A's planned move didn't survive the reload");
			assertRefusedOverS(helper, v, "after a reload");
			helper.succeed();
		});
	}

	/** A's move to S under way (its first blocks packed), then the world saved and reloaded: B over S is still refused. */
	//$ gametest_ticks_batch AREA '100' '"qaMoveSaves1111eStarted"'
	@GameTest(template = AREA, timeoutTicks = 100, batch = "qaMoveSaves1111eStarted")
	public void anUnderWayMovesSpotIsStillRefusedAfterAReload(GameTestHelper helper) {
		Village v = stage(helper);
		helper.runAfterDelay(2, () -> {
			PendingMoves.Move move = planA(helper, v);
			MoveSite site = MoveSites.get(v.level()).begin(v.level(), move).orElseThrow(() -> new AssertionError("A's move didn't start (setup)"));
			for (int i = 0; i < 5; i++) {
				MoveSites.get(v.level()).step(v.level(), site);
			}
			reload(v.level());
			MoveSite again = MoveSites.get(v.level()).forBuilding(v.a()).orElseThrow(() -> new AssertionError("A's move didn't survive the reload"));
			helper.assertTrue(again != site && again.stage() != MoveSite.Stage.DONE, "A's move after the reload: " + again.stage());
			assertRefusedOverS(helper, v, "after a reload with A's move under way");
			helper.succeed();
		});
	}

	/** A's planned move called off on its own picker (the Call off button): S is free again, B over S fits. */
	//$ gametest_ticks_batch AREA '100' '"qaMoveSaves1111eCalledOff"'
	@GameTest(template = AREA, timeoutTicks = 100, batch = "qaMoveSaves1111eCalledOff")
	public void aCalledOffMovesSpotFitsAgain(GameTestHelper helper) {
		Village v = stage(helper);
		helper.runAfterDelay(2, () -> {
			planA(helper, v);
			ChoiceMenu menu = openPickerFor(helper, v, v.a());
			helper.assertTrue(menu.icon(MovePage.CALL_OFF).is(Items.COBWEB), "A's picker has no Call off button: " + menu.icon(MovePage.CALL_OFF));
			menu.press(MovePage.CALL_OFF, v.player());
			menu.removed(v.player());
			MovePage.stop(v.player());
			helper.assertTrue(PendingMoves.get(v.level()).forBuilding(v.a()).isEmpty(), "A's move wasn't called off");
			String over = verdictForB(helper, v, S.getX(), S.getZ());
			helper.assertTrue(over.equals("This spot fits [Confirm ready]"), "A's move to S was called off, yet B over S: " + over);
			helper.succeed();
		});
	}

	/**
	 * A's move run to its end: A stands at S, so B over S is refused naming A (as a building standing there now), and A's
	 * old spot, now empty, fits B.
	 */
	//$ gametest_ticks_batch AREA '200' '"qaMoveSaves1111eDone"'
	@GameTest(template = AREA, timeoutTicks = 200, batch = "qaMoveSaves1111eDone")
	public void aFinishedMoveStandsAtItsNewSpotAndFreesItsOldOne(GameTestHelper helper) {
		Village v = stage(helper);
		helper.runAfterDelay(2, () -> {
			PendingMoves.Move move = planA(helper, v);
			MoveSite site = MoveSites.get(v.level()).begin(v.level(), move).orElseThrow(() -> new AssertionError("A's move didn't start (setup)"));
			for (int i = 0; i < 100_000 && site.stage() != MoveSite.Stage.DONE; i++) {
				MoveSites.get(v.level()).step(v.level(), site);
			}
			helper.assertTrue(site.stage() == MoveSite.Stage.DONE && site.skipped() == 0, "A's move ended " + site.stage() + ", skipped " + site.skipped());
			helper.assertTrue(MoveSites.get(v.level()).forBuilding(v.a()).isEmpty() && PendingMoves.get(v.level()).forBuilding(v.a()).isEmpty(),
				"A's finished move is still on the books");
			String over = verdictForB(helper, v, S.getX(), S.getZ());
			helper.assertTrue(over.equals("Refused: it would overlap " + name() + " [Confirm gray]"), "A now stands at S, yet B over S: " + over);
			String old = verdictForB(helper, v, 5, 5);
			helper.assertTrue(old.equals("This spot fits [Confirm ready]"), "A has left its old spot (x 3-7, z 3-7), yet B there: " + old);
			helper.succeed();
		});
	}

	/**
	 * Lets A's move run until {@code until} holds (or it ends), presses A's row on the Move page, then the picker's Call
	 * off; then lets the Mover finish. "Called off on the Move page, even half way, what went up at the new spot comes
	 * down again and everything goes back up where it was" (moving-buildings.md).
	 */
	static String callOffHalfWay(GameTestHelper helper, Village v, MoveSite site, Predicate<MoveSite> until) {
		for (int i = 0; i < 100_000 && !until.test(site) && site.stage() != MoveSite.Stage.DONE; i++) {
			MoveSites.get(v.level()).step(v.level(), site);
		}
		MoveSite.Stage at = site.stage();
		ChoiceMenu menu = null;
		for (int slot = VillageHallScreen.FIRST_ROW; slot < ChoiceMenu.SIZE && menu == null; slot++) {
			ChoiceMenu m = VillageHallScreen.forTest(v.player(), v.hall());
			m.press(HallPages.slot(MovePage.PAGE), v.player());
			if (m.icon(slot).isEmpty()) {
				break;
			}
			m.press(slot, v.player());
			if (MovePage.picking(v.player()).map(p -> p.from().equals(v.a())).orElse(false)) {
				menu = m;
			} else {
				MovePage.stop(v.player());
			}
		}
		if (menu == null) {
			return "with A's move at " + at + " no row on the Move page opens A's picker (nothing there can call the move off)";
		}
		if (!menu.icon(MovePage.CALL_OFF).is(Items.COBWEB)) {
			return "with A's move at " + at + " A's picker has no Call off button: " + menu.icon(MovePage.CALL_OFF);
		}
		menu.press(MovePage.CALL_OFF, v.player());
		menu.removed(v.player());
		MovePage.stop(v.player());
		for (int i = 0; i < 100_000 && site.stage() != MoveSite.Stage.DONE; i++) {
			MoveSites.get(v.level()).step(v.level(), site);
		}
		if (!site.canceled() || site.stage() != MoveSite.Stage.DONE || site.skipped() != 0) {
			return "A's move called off at " + at + " ended " + site.stage() + ", canceled " + site.canceled() + ", skipped " + site.skipped();
		}
		return null;
	}

	/** Whether every block of the Test Hut at A's old spot stands again and S is empty. */
	static String backWhole(GameTestHelper helper, Village v, int[] before) {
		int[] now = count(helper);
		return now[0] == before[0] && now[1] == 0 ? null : "after the call-off A's old spot has " + now[0] + " blocks (of " + before[0]
			+ ") and S still " + now[1];
	}

	/** Non-air blocks at A's old spot (x 3-7, z 3-7) and at S (x 11-15, z 3-7), y 2-6. */
	static int[] count(GameTestHelper helper) {
		int[] n = new int[2];
		for (int x = 3; x <= 15; x++) {
			for (int z = 3; z <= 7; z++) {
				for (int y = 2; y <= 6; y++) {
					if (x >= 8 && x <= 10 || helper.getLevel().getBlockState(helper.absolutePos(new BlockPos(x, y, z))).isAir()) {
						continue;
					}
					n[x <= 7 ? 0 : 1]++;
				}
			}
		}
		return n;
	}

	/**
	 * A's move half taken down (some blocks packed, some still standing): Call off on A's picker puts every block back at
	 * the old spot, and S, no longer anyone's new spot, fits B.
	 */
	//$ gametest_ticks_batch AREA '200' '"qaMoveSaves1111eCallOffDown"'
	@GameTest(template = AREA, timeoutTicks = 200, batch = "qaMoveSaves1111eCallOffDown")
	public void aMoveCalledOffHalfTakenDownGoesBackWhole(GameTestHelper helper) {
		Village v = stage(helper);
		helper.runAfterDelay(2, () -> {
			int[] before = count(helper);
			PendingMoves.Move move = planA(helper, v);
			MoveSite site = MoveSites.get(v.level()).begin(v.level(), move).orElseThrow(() -> new AssertionError("A's move didn't start (setup)"));
			int[] steps = {0};
			String wrong = callOffHalfWay(helper, v, site, x -> ++steps[0] > 12);
			helper.assertTrue(wrong == null, wrong);
			wrong = backWhole(helper, v, before);
			helper.assertTrue(wrong == null, wrong);
			String over = verdictForB(helper, v, S.getX(), S.getZ());
			helper.assertTrue(over.equals("This spot fits [Confirm ready]"), "A's move to S was called off, yet B over S: " + over);
			helper.succeed();
		});
	}

	/**
	 * The planned spot's edge from both sides: with A's move to S (x 11-15, z 3-7) planned, B on x 15-19 (sharing S's east
	 * column) is refused naming A, and B on x 16-20 (a block clear of it) fits.
	 */
	//$ gametest_ticks_batch AREA '100' '"qaMoveSaves1111eEdge"'
	@GameTest(template = AREA, timeoutTicks = 100, batch = "qaMoveSaves1111eEdge")
	public void aPlannedSpotsEdgeIsRefusedAndOneBlockOffFits(GameTestHelper helper) {
		Village v = stage(helper);
		helper.runAfterDelay(2, () -> {
			planA(helper, v);
			String touching = verdictForB(helper, v, 17, 5);
			helper.assertTrue(touching.equals("Refused: it would overlap " + name() + " [Confirm gray]"),
				"B on x 15-19 shares S's east column (x 15), yet: " + touching);
			String clear = verdictForB(helper, v, 18, 5);
			helper.assertTrue(clear.equals("This spot fits [Confirm ready]"), "B on x 16-20 is a block clear of S, yet: " + clear);
			helper.succeed();
		});
	}

	/** A's own planned spot never refuses A: reopened on its planned spot, A's picker says it fits, and a block east of it too. */
	//$ gametest_ticks_batch AREA '100' '"qaMoveSaves1111eOwn"'
	@GameTest(template = AREA, timeoutTicks = 100, batch = "qaMoveSaves1111eOwn")
	public void aBuildingsOwnPlannedSpotDoesntRefuseIt(GameTestHelper helper) {
		Village v = stage(helper);
		helper.runAfterDelay(2, () -> {
			planA(helper, v);
			ChoiceMenu menu = openPickerFor(helper, v, v.a());
			String reopened = menu.icon(MovePage.VERDICT).getHoverName().getString();
			helper.assertTrue(reopened.equals("This spot fits"), "A's picker reopened on its own planned spot S says: " + reopened);
			clickMap(helper, v, menu, S.getX() + 1, S.getZ());
			String shifted = menu.icon(MovePage.VERDICT).getHoverName().getString();
			helper.assertTrue(shifted.equals("This spot fits") && menu.icon(MovePage.CONFIRM).is(Items.PISTON),
				"A moved a block east of its own planned spot (overlapping it) says: " + shifted + ", " + menu.icon(MovePage.CONFIRM));
			menu.removed(v.player());
			MovePage.stop(v.player());
			helper.succeed();
		});
	}
}
