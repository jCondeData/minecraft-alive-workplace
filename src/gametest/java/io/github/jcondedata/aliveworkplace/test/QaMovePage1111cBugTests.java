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
 * QA bug tests (lane C, 2026-11-11) for B1174, fixed in MoveFit#moving. 45.2: a spot is
 * "refused: overlaps a building". Two finished Test Huts, A (x 3-7, z 3-7) and B (x 3-7, z 12-16); A's move to S (x 11-15,
 * z 3-7) is confirmed. B's picker on a spot over S should be refused with a plain line naming A, Confirm gray: A will stand
 * there. Before the fix MoveFit only knew A at its old spot, so B could be planned onto S too and two moves targeted the same ground.
 * The second case is the same with A's move already started (a MoveSite, as when its Mover begins). A control in each:
 * B on a spot well clear of S fits.
 */
public class QaMovePage1111cBugTests implements FabricGameTest {
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

	private record Village(ServerLevel level, BlockPos hall, BlockPos a, BlockPos b, ServerPlayer player) {
	}

	private static Village stage(GameTestHelper helper) {
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
	private static PendingMoves.Move planA(GameTestHelper helper, Village v) {
		ChoiceMenu menu = openPickerFor(helper, v, v.a());
		clickMap(helper, v, menu, S.getX(), S.getZ());
		helper.assertTrue(menu.icon(MovePage.CONFIRM).is(Items.PISTON), "A's Confirm isn't ready (setup): " + menu.icon(MovePage.CONFIRM) + " " + lore(menu.icon(MovePage.CONFIRM)));
		menu.press(MovePage.CONFIRM, v.player());
		PendingMoves.Move move = PendingMoves.get(v.level()).forBuilding(v.a()).orElseThrow(() -> new AssertionError("A's move wasn't saved (setup)"));
		helper.assertTrue(move.to().equals(v.a().offset(8, 0, 0)), "A's move (setup): " + move);
		menu.removed(v.player());
		return move;
	}

	/** B's picker: clear of S fits (the control); over S it is refused, naming A, Confirm gray, and Confirm saves nothing. */
	private static void assertBRefusedOverS(GameTestHelper helper, Village v) {
		ChoiceMenu menu = openPickerFor(helper, v, v.b());
		clickMap(helper, v, menu, CLEAR.getX(), CLEAR.getZ());
		String clear = menu.icon(MovePage.VERDICT).getHoverName().getString();
		helper.assertTrue(clear.equals("This spot fits") && step(menu) == 3, "control: B well clear of S doesn't fit: " + clear);
		clickMap(helper, v, menu, S.getX(), S.getZ());
		String why = menu.icon(MovePage.VERDICT).getHoverName().getString();
		helper.assertTrue(why.equals("Refused: it would overlap " + name()),
			"B over the spot A is moving to: the page says \"" + why + "\", not \"Refused: it would overlap " + name() + "\" (two moves onto the same ground)");
		helper.assertTrue(step(menu) == 2 && view(menu).getInt("fit") == 2, "B over S isn't a refused step 2: " + step(menu) + ", " + view(menu).getInt("fit"));
		helper.assertTrue(menu.icon(MovePage.CONFIRM).is(Items.GRAY_DYE), "B's Confirm is ready over S: " + menu.icon(MovePage.CONFIRM));
		menu.press(MovePage.CONFIRM, v.player());
		helper.assertTrue(PendingMoves.get(v.level()).forBuilding(v.b()).isEmpty(), "B's move onto S was saved");
	}

	/** A's move to S planned (not started): B's spot over S is refused, naming A; clear of S it fits. */
	//$ gametest_ticks_batch AREA '100' '"qaMove1111cBugPlanned"'
	@GameTest(template = AREA, timeoutTicks = 100, batch = "qaMove1111cBugPlanned")
	public void aSpotOverAnotherBuildingsPlannedSpotIsRefused(GameTestHelper helper) {
		Village v = stage(helper);
		helper.runAfterDelay(2, () -> {
			planA(helper, v);
			assertBRefusedOverS(helper, v);
			helper.succeed();
		});
	}

	/** A's move to S started (its MoveSite begun, as when its Mover takes it): B's spot over S is refused, naming A; clear of S it fits. */
	//$ gametest_ticks_batch AREA '100' '"qaMove1111cBugStarted"'
	@GameTest(template = AREA, timeoutTicks = 100, batch = "qaMove1111cBugStarted")
	public void aSpotOverAnotherBuildingsMoveUnderWayIsRefused(GameTestHelper helper) {
		Village v = stage(helper);
		helper.runAfterDelay(2, () -> {
			PendingMoves.Move move = planA(helper, v);
			MoveSite site = MoveSites.get(v.level()).begin(v.level(), move).orElseThrow(() -> new AssertionError("A's move didn't start (setup)"));
			helper.assertTrue(MoveSites.get(v.level()).forBuilding(v.a()).isPresent() && site.from().equals(v.a()), "A's move isn't under way (setup)");
			assertBRefusedOverS(helper, v);
			helper.succeed();
		});
	}
}
