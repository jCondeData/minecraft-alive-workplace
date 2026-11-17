package io.github.jcondedata.aliveworkplace.test;

import static io.github.jcondedata.aliveworkplace.test.RebuildDrawings1117e.*;

import io.github.jcondedata.aliveworkplace.build.BuildPlan;
import io.github.jcondedata.aliveworkplace.build.BuildSite;
import io.github.jcondedata.aliveworkplace.build.BuildSiteManager;
import io.github.jcondedata.aliveworkplace.build.Builders;
import io.github.jcondedata.aliveworkplace.build.Refreshes;
import io.github.jcondedata.aliveworkplace.hall.HallPages;
import io.github.jcondedata.aliveworkplace.hall.RepairsPage;
import io.github.jcondedata.aliveworkplace.hall.UpgradesPage;
import io.github.jcondedata.aliveworkplace.hall.VillageHallScreen;
import io.github.jcondedata.aliveworkplace.registry.ModAttachments;
import io.github.jcondedata.aliveworkplace.work.ChoiceMenu;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.Container;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;

/**
 * QA lane E (2026-11-17, lane-e-1117-1013): saves across a blueprint REBUILT from scratch under the same id (Milestones
 * 49-52; tools/blueprints/REBUILD.md "Saves stay safe"; ROADMAP 36.21). The drawings are {@link RebuildDrawings1117e}'s:
 * the new one has another footprint, another door, no bed and no job block. What this class holds is what passes today;
 * QaRebuildSaves1117eBugTests (not registered) holds the cases that fail because of the mod.
 */
public class QaRebuildSaves1117eGameTests implements FabricGameTest {

	private static Optional<Refreshes.Entry> entryOf(GameTestHelper helper, Stage s) {
		return Refreshes.entries(s.level(), helper.absolutePos(HALL)).stream()
			.filter(e -> e.building().placement().equals(s.placement())).findFirst();
	}

	private static int sum(int[] counts) {
		return Arrays.stream(counts).sum();
	}

	private static ChoiceMenu openPage(GameTestHelper helper, Stage s) {
		ChoiceMenu menu = VillageHallScreen.forTest(s.owner(), helper.absolutePos(HALL));
		menu.press(HallPages.slot(RepairsPage.PAGE), s.owner());
		helper.assertTrue(menu.icon(4).is(Items.ANVIL), "not on the Repairs page: " + menu.icon(4));
		return menu;
	}

	private static int refreshSlot(ChoiceMenu menu) {
		for (int slot = VillageHallScreen.FIRST_ROW; slot < ChoiceMenu.SIZE; slot++) {
			if (menu.icon(slot).is(Items.PAINTING) || menu.icon(slot).is(Items.SCAFFOLDING)) {
				return slot;
			}
		}
		return -1;
	}

	private static void assertChestKept(GameTestHelper helper) {
		helper.assertBlockPresent(Blocks.CHEST, at(CHEST));
		Container chest = helper.getBlockEntity(at(CHEST));
		helper.assertTrue(chest.countItem(Items.EMERALD) == 7, "the house's chest lost its emeralds: " + chest.countItem(Items.EMERALD));
	}

	// ---- 2. a finished house of the old drawing, refreshed to a totally new one -------------------------------------------

	/**
	 * A finished house of the old drawing (chest with 7 emeralds, a lectern, a bed someone sleeps in, the owner's glowstone
	 * on its back wall and outside its plot where the new drawing has a wall); the drawing is rebuilt (another footprint,
	 * door at the back) and the world restarted. The Repairs page offers Refresh with sane counts (35 new blocks: every one
	 * of the new drawing's 36 but the owner's glowstone's spot; 17 taken away: what the old drawing put that the new one
	 * hasn't, less the kept chest, lectern and bed and the owner's block). Refreshed through the button, with a restart
	 * mid-way: the new drawing stands whole, nothing of the old drawing is left anywhere, the chest keeps its emeralds,
	 * the lectern and the bed stay with its claim, the owner's two blocks stay, and the page has nothing more to offer.
	 */
	//$ gametest_ticks_batch AREA '3000' '"qaRebuild1117eRefresh"'
	@GameTest(template = AREA, timeoutTicks = 3000, batch = "qaRebuild1117eRefresh")
	public void aHouseOfTheOldDrawingIsRefreshedToANewFootprintKeepingWhatStays(GameTestHelper helper) {
		ResourceLocation id = id(helper, "refresh");
		Stage s = stage(helper, id, true);
		standOld(helper, s, true, true);
		Villager[] builder = {null};
		boolean[] reloaded = {false};
		boolean[] pressed = {false};
		helper.runAfterDelay(2, () -> {
			claimBed(helper);
			rebuild(s.level(), id, true);
			reload(helper, s.level(), null); // the restart with the new jar
			Optional<Refreshes.Entry> e = entryOf(helper, s);
			helper.assertTrue(e.isPresent() && e.get().change() != null, "the rebuilt house isn't offered a refresh");
			Refreshes.Change c = e.get().change();
			helper.assertTrue(sum(c.added()) == 35 && sum(c.removed()) == 17, "the preview counts new " + Arrays.toString(c.added())
				+ " and taken away " + Arrays.toString(c.removed()) + ", wanted 35 new blocks and 17 taken away");
			builder[0] = builder(helper, s);
			ChoiceMenu menu = openPage(helper, s);
			int slot = refreshSlot(menu);
			helper.assertTrue(slot >= 0, "no Refresh entry on the Repairs page");
			menu.press(slot, s.owner());
			helper.assertTrue(menu.icon(RepairsPage.REFRESH).is(Items.BRUSH), "no Refresh button: " + menu.icon(RepairsPage.REFRESH));
			menu.press(RepairsPage.REFRESH, s.owner());
			pressed[0] = Refreshes.siteOf(s.level(), s.placement()) != null;
			helper.assertTrue(pressed[0], "the button started no refresh with a free builder");
		});
		helper.onEachTick(() -> {
			if (!pressed[0] || reloaded[0] || Refreshes.siteOf(s.level(), s.placement()) == null
				|| !s.level().getBlockState(helper.absolutePos(at(new BlockPos(0, 1, 4)))).isAir()) {
				return;
			}
			reloaded[0] = true;
			builder[0] = reload(helper, s.level(), builder[0]);
		});
		helper.succeedWhen(() -> {
			helper.assertTrue(reloaded[0], "never restarted mid-refresh");
			helper.assertTrue(sites(s) == 0, "still refreshing: " + BuildSiteManager.get(s.level()).all().stream()
				.filter(x -> x.placement().equals(s.placement())).map(x -> x.stage() + " " + x.status()).toList());
			helper.assertFalse(ModAttachments.BUILDER_JOB.has(builder[0]), "the builder is still at it");
			List<BlockPos> oldLeft = new java.util.ArrayList<>(where(helper, Blocks.COBBLESTONE));
			oldLeft.addAll(where(helper, Blocks.OAK_PLANKS));
			helper.assertTrue(oldLeft.isEmpty(), "the old drawing's blocks are left at " + oldLeft.size() + " spots, "
				+ oldLeft.stream().limit(6).toList());
			helper.assertTrue(where(helper, Blocks.STONE_BRICKS).size() == 21, "stone brick floor: " + where(helper, Blocks.STONE_BRICKS).size() + " of 21");
			helper.assertTrue(where(helper, Blocks.SPRUCE_PLANKS).size() == 14, "spruce walls: " + where(helper, Blocks.SPRUCE_PLANKS).size()
				+ " of 14 (15 less the owner's glowstone's spot)");
			helper.assertBlockPresent(Blocks.GLOWSTONE, at(OWN_ON_OLD));
			helper.assertBlockPresent(Blocks.GLOWSTONE, at(OWN_OUTSIDE));
			assertChestKept(helper);
			helper.assertBlockPresent(Blocks.LECTERN, at(LECTERN));
			helper.assertBlockPresent(Blocks.RED_BED, at(BED_FOOT));
			helper.assertBlockPresent(Blocks.RED_BED, at(BED_HEAD));
			helper.assertTrue(bedClaimed(helper), "the bed lost its sleeper's claim");
			helper.assertTrue(entryOf(helper, s).isEmpty(), "the refreshed house is still offered a refresh");
			helper.assertTrue(refreshSlot(openPage(helper, s)) < 0, "the Repairs page still shows Refresh");
		});
	}

	// ---- 1. a site mid-build when the drawing is replaced --------------------------------------------------------------

	/**
	 * A house of the old drawing half-way through its STRUCTURE (some of the floor down) when the jar with the rebuilt
	 * drawing comes and the world restarts: whatever it builds, it never crashes or sticks: the site ends, the builder
	 * comes free and the house is recorded finished (whether it finishes the old drawing, as REBUILD.md promises, is
	 * QaRebuildSaves1117eBugTests' case).
	 */
	//$ gametest_ticks_batch AREA '3000' '"qaRebuild1117eMidBuild"'
	@GameTest(template = AREA, timeoutTicks = 3000, batch = "qaRebuild1117eMidBuild")
	public void aSiteMidBuildWhenItsDrawingIsRebuiltEndsAndFreesItsBuilder(GameTestHelper helper) {
		midBuild(helper, false);
	}

	/** The same, caught later: the old floor whole (its back rows outside the new footprint) and a few old walls up. */
	//$ gametest_ticks_batch AREA '3000' '"qaRebuild1117eMidBuildLate"'
	@GameTest(template = AREA, timeoutTicks = 3000, batch = "qaRebuild1117eMidBuildLate")
	public void aSiteLateInItsStructureWhenItsDrawingIsRebuiltEndsAndFreesItsBuilder(GameTestHelper helper) {
		midBuild(helper, true);
	}

	private static void midBuild(GameTestHelper helper, boolean late) {
		ResourceLocation id = id(helper, late ? "midlate" : "midbuild");
		Stage s = stage(helper, id, false);
		Villager[] builder = {builder(helper, s)};
		BuildSite site = Builders.start(s.level(), builder[0], s.owner(), id, s.placement());
		helper.assertTrue(site != null, "(setup) the old drawing didn't start");
		UUID siteId = site.id();
		boolean[] swapped = {false};
		helper.onEachTick(() -> {
			if (swapped[0]) {
				return;
			}
			BuildSite now = BuildSiteManager.get(s.level()).get(siteId);
			if (now == null || now.stage() != BuildPlan.Stage.STRUCTURE) {
				return;
			}
			int placed = where(helper, Blocks.COBBLESTONE).size();
			int planks = where(helper, Blocks.OAK_PLANKS).size();
			if (late ? placed == 25 && planks >= 2 && planks <= 12 : placed >= 4 && placed <= 20) {
				swapped[0] = true;
				rebuild(s.level(), id, false);
				builder[0] = reload(helper, s.level(), builder[0]);
			}
		});
		helper.succeedWhen(() -> {
			helper.assertTrue(swapped[0], "(setup) never caught half-way through the old drawing's structure");
			helper.assertTrue(BuildSiteManager.get(s.level()).get(siteId) == null, "the site never ended: "
				+ Optional.ofNullable(BuildSiteManager.get(s.level()).get(siteId)).map(x -> x.stage() + " " + x.status()).orElse("-"));
			helper.assertFalse(ModAttachments.BUILDER_JOB.has(builder[0]), "the builder is still at it");
			helper.assertTrue(BuildSiteManager.get(s.level()).finishedIn(s.level()).stream().anyMatch(f -> f.placement().equals(s.placement())),
				"the house isn't recorded finished");
		});
	}

	// ---- 3. the next level over a lower level of the old drawing -------------------------------------------------------

	/**
	 * Level 1 stands as the old drawing drew it (chest with 7 emeralds, a lectern); the family is rebuilt (Level 1 and
	 * Level 2 both new; Level 2 is the new Level 1 with a roof). The Upgrades page's order is either a clean upgrade or a
	 * refusal the player reads (a blocker named); never a crash or a stuck site, and the chest and its emeralds are never
	 * taken. Restarted once mid-upgrade.
	 */
	//$ gametest_ticks_batch AREA '3000' '"qaRebuild1117eUpgrade"'
	@GameTest(template = AREA, timeoutTicks = 3000, batch = "qaRebuild1117eUpgrade")
	public void anUpgradeOverALevelOfTheOldDrawingEndsCleanlyOrIsRefused(GameTestHelper helper) {
		ResourceLocation id = id(helper, "upgrade");
		Stage s = stage(helper, id, true);
		standOld(helper, s, true, false);
		Villager[] builder = {null};
		UpgradesPage.Outcome[] outcome = {null};
		boolean[] reloaded = {false};
		helper.runAfterDelay(2, () -> {
			rebuild(s.level(), id, true);
			ResourceLocation next = io.github.jcondedata.aliveworkplace.blueprint.BlueprintUpgrades.upgradeOf(id);
			install(s.level(), next, newBlueprint(next, true));
			reload(helper, s.level(), null);
			builder[0] = builder(helper, s);
			Optional<UpgradesPage.Upgrade> up = UpgradesPage.at(s.level(), helper.absolutePos(HALL), s.placement());
			helper.assertTrue(up.isPresent(), "the old Level 1 has no next level on the Upgrades page");
			outcome[0] = UpgradesPage.order(s.level(), helper.absolutePos(HALL), s.owner(), s.placement());
			if (!outcome[0].ok()) {
				helper.assertTrue(outcome[0] == UpgradesPage.Outcome.BLOCKED && up.get().blocker() != null,
					"refused without a reason the player reads: " + outcome[0] + ", blocker " + up.get().blocker());
			}
		});
		helper.onEachTick(() -> {
			if (outcome[0] == null || !outcome[0].ok() || reloaded[0] || sites(s) == 0 || where(helper, Blocks.SMOOTH_STONE).isEmpty()) {
				return;
			}
			reloaded[0] = true;
			builder[0] = reload(helper, s.level(), builder[0]);
		});
		helper.succeedWhen(() -> {
			helper.assertTrue(outcome[0] != null, "not ordered yet");
			assertChestKept(helper);
			if (!outcome[0].ok()) {
				return; // refused, with its reason (checked at the order)
			}
			helper.assertTrue(reloaded[0], "never restarted mid-upgrade");
			helper.assertTrue(sites(s) == 0, "the upgrade never ended: " + BuildSiteManager.get(s.level()).all().stream()
				.filter(x -> x.placement().equals(s.placement())).map(x -> x.stage() + " " + x.status() + " " + x.structure()).toList());
			helper.assertFalse(ModAttachments.BUILDER_JOB.has(builder[0]), "the builder is still at it");
			helper.assertTrue(where(helper, Blocks.SMOOTH_STONE).size() == 21, "the next level's roof: " + where(helper, Blocks.SMOOTH_STONE).size() + " of 21");
		});
	}
}
