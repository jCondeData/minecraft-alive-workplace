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
 * Bug test (QA lane C, 2026-11-10), from QaMoveRefusals1110cGameTests: roadmap 45.6 "Safe to use on the live server", written from the spec, not the code. Only the
 * village's own buildings can be moved (never another player's claim or a building outside the village); the Hall, a
 * Wonder and a building with a live build site are refused with a reason; config {@code moveBuildings} (on). Everything
 * goes through the Hall's Move page: the building's Move button (the pick) and Confirm. MovePageGameTests covers each
 * refusal at the pick once; these change the building's state between the pick and Confirm (a site started on it, a
 * claim put on it, the switch thrown, the building gone), walk each boundary from both sides (the village's edge, the
 * claim's edge, a footprint that holds the Hall), and check that a refusal leaves the building and its chest untouched.
 */
public class QaMoveRefusals1110cBugTests implements FabricGameTest {
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

	/**
	 * Another Village Hall standing inside one of this village's buildings (two villages side by side): "the Hall stays
	 * where it stands" holds for it too, so the building is refused from this hall's Move page.
	 */
	//$ gametest_ticks_batch AREA '100' '"qaC1110MoveOtherHall"'
	@GameTest(template = AREA, timeoutTicks = 100, batch = "qaC1110MoveOtherHall")
	public void aBuildingHoldingAnotherVillageHallIsRefused(GameTestHelper helper) {
		Village v = stage(helper, true);
		helper.setBlock(new BlockPos(5, 2, 5), ModBlocks.VILLAGE_HALL);
		helper.runAfterDelay(2, () -> {
			helper.assertTrue(v.level().getBlockEntity(helper.absolutePos(new BlockPos(5, 2, 5))) instanceof VillageHallBlockEntity, "no second hall (setup)");
			Map<BlockPos, String> before = snapshot(v);
			ChoiceMenu menu = movePage(helper, v);
			boolean opened = pick(helper, v, menu, v.hut());
			boolean saved = false;
			if (opened) {
				menu.press(EAST, v.player());
				menu.press(MovePage.CONFIRM, v.player());
				saved = PendingMoves.get(v.level()).forBuilding(v.hut()).isPresent();
			}
			helper.assertTrue(!opened, "the picker opened on a building holding another Village Hall (refusal: " + why(v, v.hut())
				+ "), and Confirm " + (saved ? "saved the move: its Mover would carry the hall away" : "saved nothing"));
			assertUntouched(helper, v, before, "a building holding another Village Hall");
			assertWhy(helper, v, v.hut(), "message.aliveworkplace.move.refused.hall");
			helper.succeed();
		});
	}
}
