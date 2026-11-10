package io.github.jcondedata.aliveworkplace.test;

import io.github.jcondedata.aliveworkplace.blueprint.BlueprintData;
import io.github.jcondedata.aliveworkplace.build.BuildPlan;
import io.github.jcondedata.aliveworkplace.build.BuildSite;
import io.github.jcondedata.aliveworkplace.build.BuildSiteManager;
import io.github.jcondedata.aliveworkplace.hall.HallPages;
import io.github.jcondedata.aliveworkplace.hall.MovePage;
import io.github.jcondedata.aliveworkplace.hall.VillageHallBlockEntity;
import io.github.jcondedata.aliveworkplace.hall.VillageHallScreen;
import io.github.jcondedata.aliveworkplace.hall.VillageHalls;
import io.github.jcondedata.aliveworkplace.move.MoveRules;
import io.github.jcondedata.aliveworkplace.move.Movers;
import io.github.jcondedata.aliveworkplace.move.PendingMoves;
import io.github.jcondedata.aliveworkplace.registry.ModBlocks;
import io.github.jcondedata.aliveworkplace.work.ChoiceMenu;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Predicate;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings;

/**
 * QA (lane C, 2026-11-10): roadmap 45.6 "Safe to use on the live server", written from the spec, not the code. Only the
 * village's own buildings can be moved (never another player's claim or a building outside the village); the Hall, a
 * Wonder and a building with a live build site are refused with a reason; config {@code moveBuildings} (on). Everything
 * goes through the Hall's Move page: the building's Move button (the pick) and Confirm. MovePageGameTests covers each
 * refusal at the pick once; these change the building's state between the pick and Confirm (a site started on it, a
 * claim put on it, the switch thrown, the building gone), walk each boundary from both sides (the village's edge, the
 * claim's edge, a footprint that holds the Hall), and check that a refusal leaves the building and its chest untouched.
 */
public class QaMoveRefusals1110cGameTests implements FabricGameTest {
	private static final String AREA = "aliveworkplace_test:big_area";
	private static final ResourceLocation HUT = ResourceLocation.fromNamespaceAndPath("aliveworkplace_test", "test_hut");
	private static final ResourceLocation WONDER = ResourceLocation.fromNamespaceAndPath("aliveworkplace", "wonder/test_wonder");
	private static final BlockPos HALL = new BlockPos(15, 2, 11);
	/** The finished Test Hut: x 3-7, y 2-5, z 3-7. */
	private static final BlockPos HUT_ORIGIN = new BlockPos(3, 2, 3);
	/** A chest put inside the hut, with 7 diamonds, to see a refusal leave its contents alone. */
	private static final BlockPos CHEST = new BlockPos(5, 3, 5);
	/** The map cell two to the east of the middle: the spot 8 blocks east (x 11-15, z 3-7), which fits. */
	private static final int EAST = MovePage.CENTRE + 2;

	private record Village(ServerLevel level, BlockPos hall, BlockPos hut, ServerPlayer player) {
	}

	private static Village stage(GameTestHelper helper, boolean mover) {
		Leftovers.clear(helper);
		ServerLevel level = helper.getLevel();
		int radius = VillageHalls.RADIUS;
		VillageHalls.RADIUS = 16;
		Predicate<Villager> movers = Movers.isMover;
		boolean enabled = MoveRules.ENABLED;
		BuildSiteManager sites = BuildSiteManager.get(level);
		BlueprintData.Placement hut = placement(helper, HUT_ORIGIN);
		ServerPlayer player = helper.makeMockServerPlayerInLevel();
		Leftovers.after(helper, () -> {
			VillageHalls.RADIUS = radius;
			Movers.isMover = movers;
			MoveRules.ENABLED = enabled;
			sites.forgetFinished(hut);
			PendingMoves.get(level).remove(hut.origin());
			MovePage.stop(player);
		});
		MoveRules.ENABLED = true;
		helper.setBlock(HALL, ModBlocks.VILLAGE_HALL);
		BlockPos origin = helper.absolutePos(HUT_ORIGIN);
		level.getStructureManager().get(HUT).orElseThrow().placeInWorld(level, origin, origin, new StructurePlaceSettings(), RandomSource.create(1L), 2);
		helper.setBlock(CHEST, Blocks.CHEST);
		ChestBlockEntity chest = helper.getBlockEntity(CHEST);
		chest.setItem(0, new ItemStack(Items.DIAMOND, 7));
		sites.recordFinished(HUT, hut, UUID.randomUUID());
		if (mover) {
			Villager v = helper.spawn(EntityType.VILLAGER, new BlockPos(10, 2, 15));
			Movers.isMover = villager -> villager.getUUID().equals(v.getUUID());
		}
		return new Village(level, helper.absolutePos(HALL), origin, player);
	}

	private static BlueprintData.Placement placement(GameTestHelper helper, BlockPos rel) {
		return new BlueprintData.Placement(helper.getLevel().dimension().location(), helper.absolutePos(rel), Rotation.NONE, Mirror.NONE);
	}

	/** Every block of the hut (and a little round it), with each block entity's data: what a refusal must leave alone. */
	private static Map<BlockPos, String> snapshot(Village v) {
		Map<BlockPos, String> out = new LinkedHashMap<>();
		for (BlockPos pos : BlockPos.betweenClosed(v.hut().offset(-1, 0, -1), v.hut().offset(5, 4, 5))) {
			BlockEntity be = v.level().getBlockEntity(pos);
			out.put(pos.immutable(), v.level().getBlockState(pos) + (be == null ? "" : be.saveWithoutMetadata(v.level().registryAccess()).toString()));
		}
		return out;
	}

	private static void assertUntouched(GameTestHelper helper, Village v, Map<BlockPos, String> before, String when) {
		Map<BlockPos, String> now = snapshot(v);
		for (Map.Entry<BlockPos, String> e : before.entrySet()) {
			helper.assertTrue(e.getValue().equals(now.get(e.getKey())), when + ": the hut changed at " + e.getKey() + ": " + e.getValue() + " -> " + now.get(e.getKey()));
		}
		ChestBlockEntity chest = helper.getBlockEntity(CHEST);
		helper.assertTrue(chest.getItem(0).is(Items.DIAMOND) && chest.getItem(0).getCount() == 7, when + ": the chest holds " + chest.getItem(0));
		helper.assertTrue(PendingMoves.get(v.level()).forBuilding(v.hut()).isEmpty(), when + ": a move was saved");
	}

	/** The hall opened on its Move page. */
	private static ChoiceMenu movePage(GameTestHelper helper, Village v) {
		ChoiceMenu menu = VillageHallScreen.forTest(v.player(), v.hall());
		int tab = HallPages.slot(MovePage.PAGE);
		helper.assertTrue(tab >= VillageHallScreen.PAGE_ROW, "the Move page has no tab: " + tab);
		menu.press(tab, v.player());
		return menu;
	}

	/** The row on the Move page of the building at {@code from}, or -1 when it isn't listed. */
	private static int row(Village v, BlockPos from) {
		List<BuildSiteManager.Finished> listed = BuildSiteManager.get(v.level()).finishedInVillage(v.level(), v.hall());
		for (int i = 0; i < listed.size(); i++) {
			if (listed.get(i).placement().origin().equals(from)) {
				return i < MovePage.SHOWN ? VillageHallScreen.FIRST_ROW + i : -1;
			}
		}
		return -1;
	}

	/** Clicks the Move button of the building at {@code from}; true when the picker opened on it. */
	private static boolean pick(GameTestHelper helper, Village v, ChoiceMenu menu, BlockPos from) {
		int slot = row(v, from);
		helper.assertTrue(slot >= 0, "the building at " + from + " isn't on the Move page");
		helper.assertTrue(menu.icon(slot).is(Items.BRICKS) || menu.icon(slot).is(Items.STICKY_PISTON), "its row: " + menu.icon(slot));
		menu.press(slot, v.player());
		return MovePage.picking(v.player()).map(p -> p.from().equals(from)).orElse(false);
	}

	/** The reason the building at {@code from} is refused now, as the player reads it (null when it isn't). */
	private static String why(Village v, BlockPos from) {
		Component c = MoveRules.refusal(v.level(), v.hall(), from, v.player());
		return c == null ? null : c.getString();
	}

	private static void assertWhy(GameTestHelper helper, Village v, BlockPos from, String key, Object... args) {
		String expected = Component.translatable(key, args).getString();
		helper.assertTrue(!expected.equals(key) && !expected.isBlank(), "no words for " + key);
		String got = why(v, from);
		helper.assertTrue(expected.equals(got), "refused with \"" + got + "\", not \"" + expected + "\"");
	}

	/** Picks the hut and its spot 8 blocks east, which fits; Confirm is ready. */
	private static ChoiceMenu pickEast(GameTestHelper helper, Village v) {
		ChoiceMenu menu = movePage(helper, v);
		helper.assertTrue(pick(helper, v, menu, v.hut()), "the picker didn't open on the hut");
		menu.press(EAST, v.player());
		helper.assertTrue(MovePage.fit(v.player()).orElseThrow().fits(), "the east spot doesn't fit: " + MovePage.fit(v.player()).orElseThrow().reason());
		helper.assertTrue(menu.icon(MovePage.CONFIRM).is(Items.PISTON), "Confirm isn't ready: " + menu.icon(MovePage.CONFIRM));
		return menu;
	}

	/** A builder starts a live site on the hut after it was picked: Confirm is refused, nothing moves; once it's gone, Confirm saves. */
	//$ gametest_ticks_batch AREA '100' '"qaC1110MoveSiteAfterPick"'
	@GameTest(template = AREA, timeoutTicks = 100, batch = "qaC1110MoveSiteAfterPick")
	public void aSiteStartedOnItAfterThePickIsRefusedAtConfirm(GameTestHelper helper) {
		Village v = stage(helper, true);
		BuildSiteManager sites = BuildSiteManager.get(v.level());
		UUID[] id = new UUID[1];
		Leftovers.after(helper, () -> {
			if (id[0] != null) {
				sites.remove(id[0]);
			}
		});
		helper.runAfterDelay(2, () -> {
			ChoiceMenu menu = pickEast(helper, v);
			Map<BlockPos, String> before = snapshot(v);
			id[0] = sites.create(UUID.randomUUID(), "Builder", HUT, placement(helper, HUT_ORIGIN)).id();
			menu.press(MovePage.CONFIRM, v.player());
			assertUntouched(helper, v, before, "Confirm with a site started");
			assertWhy(helper, v, v.hut(), "message.aliveworkplace.move.refused.building");
			sites.remove(id[0]);
			id[0] = null;
			menu.press(MovePage.CONFIRM, v.player());
			helper.assertTrue(PendingMoves.get(v.level()).forBuilding(v.hut()).isPresent(), "Confirm didn't save the move once the site was gone");
			helper.succeed();
		});
	}

	/** The hut's village is protected by another player after the pick: Confirm is refused with whose village it is. */
	//$ gametest_ticks_batch AREA '100' '"qaC1110MoveClaimAfterPick"'
	@GameTest(template = AREA, timeoutTicks = 100, batch = "qaC1110MoveClaimAfterPick")
	public void aClaimPutOnItAfterThePickIsRefusedAtConfirm(GameTestHelper helper) {
		Village v = stage(helper, true);
		VillageHallBlockEntity hall = helper.getBlockEntity(HALL);
		Leftovers.after(helper, () -> hall.setProtected(false));
		helper.runAfterDelay(2, () -> {
			helper.assertTrue(!v.player().hasPermissions(2), "the test player is an operator (setup)");
			ChoiceMenu menu = pickEast(helper, v);
			Map<BlockPos, String> before = snapshot(v);
			hall.setOwner(UUID.randomUUID(), "Owner");
			hall.setProtected(true);
			menu.press(MovePage.CONFIRM, v.player());
			assertUntouched(helper, v, before, "Confirm in a claim");
			assertWhy(helper, v, v.hut(), "message.aliveworkplace.protection.denied", VillageHalls.name(v.level(), v.hall()), "Owner");
			helper.succeed();
		});
	}

	/** {@code moveBuildings} switched off after the pick: Confirm is refused, nothing saved; back on, Confirm saves. */
	//$ gametest_ticks_batch AREA '100' '"qaC1110MoveOffAfterPick"'
	@GameTest(template = AREA, timeoutTicks = 100, batch = "qaC1110MoveOffAfterPick")
	public void theSwitchTurnedOffAfterThePickIsRefusedAtConfirm(GameTestHelper helper) {
		Village v = stage(helper, true);
		helper.runAfterDelay(2, () -> {
			ChoiceMenu menu = pickEast(helper, v);
			Map<BlockPos, String> before = snapshot(v);
			MoveRules.ENABLED = false;
			menu.press(MovePage.CONFIRM, v.player());
			assertUntouched(helper, v, before, "Confirm with the switch off");
			assertWhy(helper, v, v.hut(), "message.aliveworkplace.move.refused.off");
			MoveRules.ENABLED = true;
			menu.press(MovePage.CONFIRM, v.player());
			helper.assertTrue(PendingMoves.get(v.level()).forBuilding(v.hut()).isPresent(), "Confirm didn't save the move with the switch back on");
			helper.succeed();
		});
	}

	/** The hut is no longer one of the village's buildings after the pick (taken down): Confirm is refused, nothing saved. */
	//$ gametest_ticks_batch AREA '100' '"qaC1110MoveGoneAfterPick"'
	@GameTest(template = AREA, timeoutTicks = 100, batch = "qaC1110MoveGoneAfterPick")
	public void aBuildingNoLongerTheVillagesIsRefusedAtConfirm(GameTestHelper helper) {
		Village v = stage(helper, true);
		helper.runAfterDelay(2, () -> {
			ChoiceMenu menu = pickEast(helper, v);
			Map<BlockPos, String> before = snapshot(v);
			BuildSiteManager.get(v.level()).forgetFinished(placement(helper, HUT_ORIGIN));
			menu.press(MovePage.CONFIRM, v.player());
			assertUntouched(helper, v, before, "Confirm for a building the village no longer has");
			assertWhy(helper, v, v.hut(), "message.aliveworkplace.move.refused.not_ours");
			helper.succeed();
		});
	}

	/**
	 * A site that is DONE on the hut doesn't hold it; a live site of another build reaching into its footprint does; one
	 * standing a block clear of it doesn't.
	 */
	//$ gametest_ticks_batch AREA '100' '"qaC1110MoveSiteStates"'
	@GameTest(template = AREA, timeoutTicks = 100, batch = "qaC1110MoveSiteStates")
	public void aDoneSiteIsAllowedAndAnOverlappingLiveSiteIsRefused(GameTestHelper helper) {
		Village v = stage(helper, false);
		BuildSiteManager sites = BuildSiteManager.get(v.level());
		List<UUID> made = new java.util.ArrayList<>();
		Leftovers.after(helper, () -> made.forEach(sites::remove));
		helper.runAfterDelay(2, () -> {
			BuildSite done = sites.create(UUID.randomUUID(), "Builder", HUT, placement(helper, HUT_ORIGIN));
			made.add(done.id());
			done.backToBroken(BuildPlan.Stage.DONE, 0);
			helper.assertTrue(done.isDone(), "the site isn't done (setup)");
			helper.assertTrue(why(v, v.hut()) == null, "a done site holds the hut: " + why(v, v.hut()));
			ChoiceMenu menu = movePage(helper, v);
			helper.assertTrue(pick(helper, v, menu, v.hut()), "the picker didn't open with only a done site on the hut");
			MovePage.stop(v.player());
			// another build whose footprint (x 7-11) shares the hut's east wall: refused
			BuildSite over = sites.create(UUID.randomUUID(), "Builder", HUT, placement(helper, new BlockPos(7, 2, 3)));
			made.add(over.id());
			Map<BlockPos, String> before = snapshot(v);
			menu = movePage(helper, v);
			helper.assertTrue(!pick(helper, v, menu, v.hut()), "the picker opened with a live build reaching into the hut");
			assertUntouched(helper, v, before, "a live build next door");
			assertWhy(helper, v, v.hut(), "message.aliveworkplace.move.refused.building");
			sites.remove(over.id());
			// one a block clear of it (x 8-12): allowed
			BuildSite clear = sites.create(UUID.randomUUID(), "Builder", HUT, placement(helper, new BlockPos(8, 2, 3)));
			made.add(clear.id());
			helper.assertTrue(why(v, v.hut()) == null, "a live build a block clear of the hut holds it: " + why(v, v.hut()));
			helper.succeed();
		});
	}

	/**
	 * A footprint that holds the Hall is refused however it is listed: a building whose corner is elsewhere but whose box
	 * reaches the hall block at its very edge is refused with the Hall's line; the same building a block short is not.
	 */
	//$ gametest_ticks_batch AREA '100' '"qaC1110MoveHallEdge"'
	@GameTest(template = AREA, timeoutTicks = 100, batch = "qaC1110MoveHallEdge")
	public void aFootprintReachingTheHallIsRefusedAndABlockShortIsNot(GameTestHelper helper) {
		Village v = stage(helper, false);
		BuildSiteManager sites = BuildSiteManager.get(v.level());
		// x 11-15, z 7-11: the hall (15, 11) is its far corner
		BlueprintData.Placement annex = placement(helper, new BlockPos(11, 2, 7));
		// x 10-14, z 7-11: a block short
		BlueprintData.Placement shy = placement(helper, new BlockPos(10, 2, 7));
		Leftovers.after(helper, () -> {
			sites.forgetFinished(annex);
			sites.forgetFinished(shy);
		});
		sites.recordFinished(HUT, annex, UUID.randomUUID());
		helper.runAfterDelay(2, () -> {
			ChoiceMenu menu = movePage(helper, v);
			helper.assertTrue(!pick(helper, v, menu, annex.origin()), "the picker opened on a building holding the Hall");
			helper.assertTrue(PendingMoves.get(v.level()).forBuilding(annex.origin()).isEmpty(), "a move was saved");
			assertWhy(helper, v, annex.origin(), "message.aliveworkplace.move.refused.hall");
			sites.forgetFinished(annex);
			sites.recordFinished(HUT, shy, UUID.randomUUID());
			helper.assertTrue(why(v, shy.origin()) == null, "a building a block short of the Hall is refused: " + why(v, shy.origin()));
			helper.succeed();
		});
	}

	/**
	 * Claims, from both sides: the player's own protected village lets them move; a neighbor's protected village refuses a
	 * building whose footprint reaches its edge, and not one a block clear of it.
	 */
	//$ gametest_ticks_batch AREA '100' '"qaC1110MoveClaimEdge"'
	@GameTest(template = AREA, timeoutTicks = 100, batch = "qaC1110MoveClaimEdge")
	public void ownClaimAllowedAndANeighborsClaimRefusedToItsEdge(GameTestHelper helper) {
		Village v = stage(helper, false);
		VillageHallBlockEntity hall = helper.getBlockEntity(HALL);
		BuildSiteManager sites = BuildSiteManager.get(v.level());
		BlockPos otherHall = new BlockPos(21, 2, 5);
		// x 1-5: 16 from the other hall (in its reach); x 0-4: 17 (clear)
		BlueprintData.Placement reaching = placement(helper, new BlockPos(1, 2, 3));
		BlueprintData.Placement clear = placement(helper, new BlockPos(0, 2, 3));
		Leftovers.after(helper, () -> {
			hall.setProtected(false);
			if (v.level().getBlockEntity(helper.absolutePos(otherHall)) instanceof VillageHallBlockEntity b) {
				b.setProtected(false);
			}
			sites.forgetFinished(reaching);
			sites.forgetFinished(clear);
		});
		hall.setOwner(v.player().getUUID(), v.player().getGameProfile().getName());
		hall.setProtected(true);
		helper.runAfterDelay(2, () -> {
			helper.assertTrue(!v.player().hasPermissions(2), "the test player is an operator (setup)");
			helper.assertTrue(why(v, v.hut()) == null, "the player's own protected village refuses them: " + why(v, v.hut()));
			ChoiceMenu menu = movePage(helper, v);
			helper.assertTrue(pick(helper, v, menu, v.hut()), "the picker didn't open in the player's own protected village");
			MovePage.stop(v.player());
			// the neighbor's protected village, its hall 6 blocks past this one
			helper.setBlock(otherHall, ModBlocks.VILLAGE_HALL);
			VillageHallBlockEntity other = helper.getBlockEntity(otherHall);
			other.setOwner(UUID.randomUUID(), "Neighbor");
			other.setProtected(true);
			sites.recordFinished(HUT, reaching, UUID.randomUUID());
			sites.recordFinished(HUT, clear, UUID.randomUUID());
			assertWhy(helper, v, reaching.origin(), "message.aliveworkplace.protection.denied", VillageHalls.name(v.level(), helper.absolutePos(otherHall)), "Neighbor");
			menu = movePage(helper, v);
			helper.assertTrue(!pick(helper, v, menu, reaching.origin()), "the picker opened on a building reaching into a neighbor's claim");
			helper.assertTrue(why(v, clear.origin()) == null, "a building a block clear of the neighbor's claim is refused: " + why(v, clear.origin()));
			helper.succeed();
		});
	}

	/**
	 * The village's edge from both sides (radius 16 round the hall, a building counted by its corner or its middle): the
	 * building whose middle is 16 from the hall is listed and may be moved; a block further it isn't listed and is refused.
	 */
	//$ gametest_ticks_batch AREA '100' '"qaC1110MoveVillageEdge"'
	@GameTest(template = AREA, timeoutTicks = 100, batch = "qaC1110MoveVillageEdge")
	public void theVillagesEdgeFromBothSides(GameTestHelper helper) {
		Village v = stage(helper, false);
		BuildSiteManager sites = BuildSiteManager.get(v.level());
		// the hall at x 15, z 11; a 5 x 5 hut's middle is its corner + 2: corner x -3 puts the middle 16 west, x -4 17
		BlueprintData.Placement inside = placement(helper, new BlockPos(-3, 2, 9));
		BlueprintData.Placement outside = placement(helper, new BlockPos(-4, 2, 9));
		Leftovers.after(helper, () -> {
			sites.forgetFinished(inside);
			sites.forgetFinished(outside);
		});
		sites.recordFinished(HUT, inside, UUID.randomUUID());
		sites.recordFinished(HUT, outside, UUID.randomUUID());
		helper.runAfterDelay(2, () -> {
			helper.assertTrue(row(v, inside.origin()) >= 0, "the building on the village's edge isn't on the Move page");
			helper.assertTrue(why(v, inside.origin()) == null, "the building on the village's edge is refused: " + why(v, inside.origin()));
			helper.assertTrue(row(v, outside.origin()) < 0, "the building a block outside the village is on the Move page");
			assertWhy(helper, v, outside.origin(), "message.aliveworkplace.move.refused.not_ours");
			helper.succeed();
		});
	}

	/** A Wonder's finished part, listed on the Move page, is refused at its Move button with the Wonder's line. */
	//$ gametest_ticks_batch AREA '100' '"qaC1110MoveWonder"'
	@GameTest(template = AREA, timeoutTicks = 100, batch = "qaC1110MoveWonder")
	public void aWondersPartIsRefusedAtItsMoveButton(GameTestHelper helper) {
		Village v = stage(helper, true);
		BuildSiteManager sites = BuildSiteManager.get(v.level());
		BlueprintData.Placement wonder = placement(helper, new BlockPos(10, 2, 15));
		Leftovers.after(helper, () -> sites.forgetFinished(wonder));
		sites.recordFinished(WONDER, wonder, UUID.randomUUID());
		helper.runAfterDelay(2, () -> {
			ChoiceMenu menu = movePage(helper, v);
			helper.assertTrue(!pick(helper, v, menu, wonder.origin()), "the picker opened on a Wonder");
			helper.assertTrue(PendingMoves.get(v.level()).forBuilding(wonder.origin()).isEmpty(), "a move was saved for a Wonder");
			assertWhy(helper, v, wonder.origin(), "message.aliveworkplace.move.refused.wonder");
			helper.assertTrue(menu.icon(row(v, v.hut())).is(Items.BRICKS), "the list isn't back after the refusal: " + menu.icon(row(v, v.hut())));
			helper.succeed();
		});
	}
}
