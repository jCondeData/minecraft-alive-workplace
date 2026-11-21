package io.github.jcondedata.aliveworkplace.test;

import static io.github.jcondedata.aliveworkplace.test.RebuildDrawings1117e.*;
import static io.github.jcondedata.aliveworkplace.test.RebuildMoves1117e2.*;

import io.github.jcondedata.aliveworkplace.blueprint.Blueprint;
import io.github.jcondedata.aliveworkplace.blueprint.BlueprintData;
import io.github.jcondedata.aliveworkplace.blueprint.BlueprintItem;
import io.github.jcondedata.aliveworkplace.blueprint.BlueprintUpgrades;
import io.github.jcondedata.aliveworkplace.build.BuildPlan;
import io.github.jcondedata.aliveworkplace.build.BuildSite;
import io.github.jcondedata.aliveworkplace.build.BuildSiteManager;
import io.github.jcondedata.aliveworkplace.build.Builders;
import io.github.jcondedata.aliveworkplace.build.RepairAsks;
import io.github.jcondedata.aliveworkplace.build.Upkeep;
import io.github.jcondedata.aliveworkplace.hall.MovePage;
import io.github.jcondedata.aliveworkplace.hall.UpgradesPage;
import io.github.jcondedata.aliveworkplace.registry.ModAttachments;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Vec3i;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

/**
 * QA lane C (2026-11-21, lane-c-1121-1255): houses of an OLDER drawing of a family rebuilt under the same id (B1369-B1374,
 * fixed by lane-a-1117-1223), tested from their specs and REBUILD.md ("standing buildings and sites mid-build keep their
 * old drawing until a refresh") with the gaps the finders' tests left: restarts mid-way (twice), the repair question with
 * repairAsk on, a take-down that has nothing to take down, a player's block in the new drawing's box but outside the old
 * house, and controls with a house of today's drawing beside an older one in the history. Built on
 * {@link RebuildDrawings1117e}'s drawings (old: 5x5 cobblestone and oak; new: 7x3 stone bricks and spruce).
 */
public class QaOldDrawings1121cGameTests implements FabricGameTest {

	private static List<BlockPos> newBlocks(GameTestHelper helper) {
		List<BlockPos> out = new ArrayList<>(where(helper, Blocks.STONE_BRICKS));
		out.addAll(where(helper, Blocks.SPRUCE_PLANKS));
		return out;
	}

	static List<BlockPos> oldBlocks(GameTestHelper helper) {
		List<BlockPos> out = new ArrayList<>(where(helper, Blocks.COBBLESTONE));
		out.addAll(where(helper, Blocks.OAK_PLANKS));
		return out;
	}

	private static String first(List<BlockPos> spots) {
		return spots.stream().limit(6).map(p -> p.getX() + "," + p.getY() + "," + p.getZ()).toList() + (spots.size() > 6 ? "..." : "");
	}

	private static boolean is(GameTestHelper helper, BlockPos local, net.minecraft.world.level.block.Block block) {
		return helper.getLevel().getBlockState(helper.absolutePos(at(local))).is(block);
	}

	private static String blockAt(GameTestHelper helper, BlockPos local) {
		return helper.getLevel().getBlockState(helper.absolutePos(at(local))).getBlock().getName().getString();
	}

	static boolean listed(Stage s) {
		return BuildSiteManager.get(s.level()).finishedIn(s.level()).stream().anyMatch(f -> f.placement().equals(s.placement()));
	}

	/** Upkeep switched as the test wants, put back after. */
	private static void upkeep(GameTestHelper helper, boolean enabled, boolean ask) {
		boolean wasEnabled = Upkeep.ENABLED;
		boolean wasAsk = Upkeep.ASK;
		Upkeep.ENABLED = enabled;
		Upkeep.ASK = ask;
		Leftovers.after(helper, () -> {
			Upkeep.ENABLED = wasEnabled;
			Upkeep.ASK = wasAsk;
		});
	}

	private static final BlockPos BACK_WALL = new BlockPos(0, 1, 4);
	private static final BlockPos BACK_FLOOR = new BlockPos(2, 0, 4);
	private static final BlockPos FRONT_WALL = new BlockPos(1, 1, 0);

	private static void breakThree(GameTestHelper helper) {
		for (BlockPos p : List.of(BACK_WALL, BACK_FLOOR, FRONT_WALL)) {
			put(helper, at(p), Blocks.AIR.defaultBlockState());
		}
	}

	private static void assertThreeBack(GameTestHelper helper) {
		boolean back = is(helper, BACK_WALL, Blocks.OAK_PLANKS) && is(helper, BACK_FLOOR, Blocks.COBBLESTONE) && is(helper, FRONT_WALL, Blocks.OAK_PLANKS);
		List<BlockPos> mixed = newBlocks(helper);
		helper.assertTrue(back && mixed.isEmpty(), "the old house's 3 broken blocks are back wall " + blockAt(helper, BACK_WALL) + ", back floor "
			+ blockAt(helper, BACK_FLOOR) + ", front wall " + blockAt(helper, FRONT_WALL) + " (wanted oak planks, cobblestone, oak planks), and "
			+ mixed.size() + " blocks of the new drawing were put in at " + first(mixed));
	}

	// ---- B1373: the Mover leaves a player's own block alone --------------------------------------------------------------

	/**
	 * B1373 (by reading in the bug, untested there): the player's glowstone stands inside the NEW drawing's 7x3 box but
	 * outside the old 5x5 house (local 5,1,0). The house is moved through the Move page: the whole old house arrives, and the
	 * glowstone stays where the player put it, not carried off or copied to the new spot.
	 */
	//$ gametest_ticks_batch AREA '400' '"qaOld1121cMoveOwn"'
	@GameTest(template = AREA, timeoutTicks = 400, batch = "qaOld1121cMoveOwn")
	public void aMoveOfAnOldDrawingHouseLeavesThePlayersBlockBesideIt(GameTestHelper helper) {
		ResourceLocation id = id(helper, "mvown");
		Stage s = stage(helper, id, true);
		cleanUpMoves(helper, s);
		standOld(helper, s, true, false);
		put(helper, at(OWN_OUTSIDE), Blocks.GLOWSTONE.defaultBlockState());
		Villager mover = mover(helper);
		helper.runAfterDelay(2, () -> {
			Villager sleeper = sleeper(helper);
			rebuild(s.level(), id, true);
			reload(helper, s.level(), null);
			BlockPos to = confirmMove(helper, s);
			moveWithReloads(helper, s, mover);
			assertMovedWhole(helper, s, to, sleeper);
			helper.assertTrue(is(helper, OWN_OUTSIDE, Blocks.GLOWSTONE), "the player's glowstone beside the old house was taken: "
				+ blockAt(helper, OWN_OUTSIDE));
			helper.assertFalse(helper.getLevel().getBlockState(to.offset(OWN_OUTSIDE)).is(Blocks.GLOWSTONE),
				"the player's glowstone was copied to the new spot at " + to.offset(OWN_OUTSIDE));
			helper.assertTrue(anywhere(helper, Blocks.GLOWSTONE).size() == 1, "glowstone in the area: " + anywhere(helper, Blocks.GLOWSTONE));
			helper.succeed();
		});
	}

	// ---- B1372: take-downs ---------------------------------------------------------------------------------------------

	/**
	 * B1372: a sneak take-down of an old-drawing house, the world restarted while it is half down. The hall still lists the
	 * house while any of it stands; at the end the whole house is down, the chest (7 emeralds) and lectern stand, the hall
	 * has forgotten it and the builder is free.
	 */
	//$ gametest_ticks_batch AREA '3000' '"qaOld1121cTdReload"'
	@GameTest(template = AREA, timeoutTicks = 3000, batch = "qaOld1121cTdReload")
	public void aTakeDownOfAnOldDrawingHouseSurvivesARestartHalfWay(GameTestHelper helper) {
		ResourceLocation id = id(helper, "tdrl");
		Stage s = stage(helper, id, true);
		standOld(helper, s, true, false);
		Villager[] builder = {null};
		boolean[] reloaded = {false};
		helper.runAfterDelay(2, () -> {
			rebuild(s.level(), id, true);
			reload(helper, s.level(), null);
			builder[0] = builder(helper, s);
			takeDown(helper, s, builder[0]);
		});
		helper.onEachTick(() -> {
			if (builder[0] == null || sites(s) == 0) {
				return;
			}
			int left = oldBlocks(helper).size() + where(helper, Blocks.RED_BED).size();
			helper.assertTrue(listed(s), "the hall forgot the house while " + left + " of its blocks still stand");
			if (!reloaded[0] && left < 34 && left > 8) {
				reloaded[0] = true;
				builder[0] = reload(helper, s.level(), builder[0]);
			}
		});
		helper.succeedWhen(() -> {
			helper.assertTrue(builder[0] != null && reloaded[0], "not restarted half-way yet");
			assertTakenDown(helper, s, builder[0]);
		});
	}

	// ---- B1369: upkeep ---------------------------------------------------------------------------------------------------

	/**
	 * B1369 with repairAsk on (the default): an undamaged house of the old drawing after the rebuild raises no repair
	 * question on two builder rounds, and nothing of the new drawing goes into it.
	 */
	//$ gametest_ticks_batch AREA '1000' '"qaOld1121cAskNone"'
	@GameTest(template = AREA, timeoutTicks = 1000, batch = "qaOld1121cAskNone")
	public void anUndamagedOldDrawingHouseRaisesNoRepairQuestion(GameTestHelper helper) {
		ResourceLocation id = id(helper, "asknone");
		Stage s = stage(helper, id, true);
		standOld(helper, s, true, false);
		upkeep(helper, true, true);
		Villager[] builder = {null};
		helper.runAfterDelay(2, () -> {
			rebuild(s.level(), id, true);
			reload(helper, s.level(), null);
			builder[0] = builder(helper, s);
			helper.assertTrue(Upkeep.look(s.level(), builder[0]) == null, "a repair started on an undamaged house");
		});
		helper.runAfterDelay(300, () -> helper.assertTrue(Upkeep.look(s.level(), builder[0]) == null, "a repair started on an undamaged house (2nd round)"));
		helper.runAfterDelay(600, () -> {
			RepairAsks.Ask ask = BuildSiteManager.get(s.level()).repairAsk(s.placement());
			helper.assertTrue(ask == null, "the owner was asked about repairing an undamaged house: " + ask);
			List<BlockPos> mixed = newBlocks(helper);
			helper.assertTrue(mixed.isEmpty() && sites(s) == 0, "the undamaged house got " + mixed.size() + " new-drawing blocks at " + first(mixed));
			helper.succeed();
		});
	}

	/**
	 * B1369 with repairAsk on: a house of the old drawing with 3 blocks broken (2 outside the new footprint) is asked about
	 * with 3 missing (not the new drawing's count); the owner's Yes repairs it to its own drawing: oak and cobblestone back,
	 * nothing of the new drawing.
	 */
	//$ gametest_ticks_batch AREA '2400' '"qaOld1121cAskYes"'
	@GameTest(template = AREA, timeoutTicks = 2400, batch = "qaOld1121cAskYes")
	public void aDamagedOldDrawingHouseIsAskedAboutAndYesRepairsItToItsOwnDrawing(GameTestHelper helper) {
		ResourceLocation id = id(helper, "askyes");
		Stage s = stage(helper, id, true);
		standOld(helper, s, true, false);
		breakThree(helper);
		upkeep(helper, true, true);
		Villager[] builder = {null};
		RepairAsks.Outcome[] said = {null};
		helper.runAfterDelay(2, () -> {
			rebuild(s.level(), id, true);
			reload(helper, s.level(), null);
			builder[0] = builder(helper, s);
			helper.assertTrue(Upkeep.look(s.level(), builder[0]) == null, "repaired without asking (repairAsk on)");
			RepairAsks.Ask ask = BuildSiteManager.get(s.level()).repairAsk(s.placement());
			helper.assertTrue(ask != null && ask.state() == RepairAsks.State.ASKED && ask.missing() == 3,
				"the owner's question about the damaged old house: " + ask + " (wanted ASKED with 3 missing)");
			said[0] = RepairAsks.yes(s.owner(), s.placement().origin());
			helper.assertTrue(said[0] == RepairAsks.Outcome.YES, "the owner's Yes: " + said[0]);
		});
		helper.succeedWhen(() -> {
			helper.assertTrue(said[0] != null && sites(s) == 0, "still repairing");
			assertThreeBack(helper);
		});
	}

	/**
	 * B1369 across restarts: the house is damaged, the new jar ships with a restart, the builder starts the repair, and the
	 * world restarts again while it is under way. The repair still ends as the old drawing.
	 */
	//$ gametest_ticks_batch AREA '2400' '"qaOld1121cRepairReload"'
	@GameTest(template = AREA, timeoutTicks = 2400, batch = "qaOld1121cRepairReload")
	public void aRepairOfAnOldDrawingHouseRestartedMidWayStaysItsOwnDrawing(GameTestHelper helper) {
		ResourceLocation id = id(helper, "rprl");
		Stage s = stage(helper, id, true);
		standOld(helper, s, true, false);
		breakThree(helper);
		upkeep(helper, true, false);
		Villager[] builder = {null};
		boolean[] reloaded = {false};
		helper.runAfterDelay(2, () -> {
			rebuild(s.level(), id, true);
			reload(helper, s.level(), null);
			builder[0] = builder(helper, s);
			helper.assertTrue(Upkeep.look(s.level(), builder[0]) != null, "the damaged house wasn't set for repair");
		});
		helper.runAfterDelay(5, () -> {
			helper.assertTrue(sites(s) == 1, "(setup) the repair already ended before the restart");
			reloaded[0] = true;
			builder[0] = reload(helper, s.level(), builder[0]);
		});
		helper.succeedWhen(() -> {
			helper.assertTrue(reloaded[0] && sites(s) == 0, "still repairing");
			assertThreeBack(helper);
		});
	}

	/**
	 * Control (the boundary of B1369's fix): after the rebuild, a house built from TODAY's drawing (with the old drawing in
	 * the history) with 2 blocks broken is repaired to today's drawing: spruce and stone bricks back, no old block invented.
	 */
	//$ gametest_ticks_batch AREA '2400' '"qaOld1121cRepairToday"'
	@GameTest(template = AREA, timeoutTicks = 2400, batch = "qaOld1121cRepairToday")
	public void aHouseOfTodaysDrawingIsStillRepairedToTodaysDrawing(GameTestHelper helper) {
		ResourceLocation id = id(helper, "rptoday");
		Stage s = stage(helper, id, false);
		upkeep(helper, true, false);
		BlockPos wall = new BlockPos(0, 1, 1);
		BlockPos floor = new BlockPos(3, 0, 1);
		Villager[] builder = {null};
		helper.runAfterDelay(2, () -> {
			rebuild(s.level(), id, false);
			reload(helper, s.level(), null);
			for (Blueprint.Entry e : newDrawing(false)) {
				put(helper, at(e.pos()), e.state());
			}
			put(helper, at(wall), Blocks.AIR.defaultBlockState());
			put(helper, at(floor), Blocks.AIR.defaultBlockState());
			BuildSiteManager.get(s.level()).recordFinished(id, s.placement(), s.owner().getUUID());
			builder[0] = builder(helper, s);
			helper.assertTrue(Upkeep.look(s.level(), builder[0]) != null, "the damaged house of today's drawing wasn't set for repair");
		});
		helper.succeedWhen(() -> {
			helper.assertTrue(builder[0] != null && sites(s) == 0, "still repairing");
			helper.assertTrue(is(helper, wall, Blocks.SPRUCE_PLANKS) && is(helper, floor, Blocks.STONE_BRICKS), "today's house got back "
				+ blockAt(helper, wall) + " and " + blockAt(helper, floor) + " (wanted spruce planks, stone bricks)");
			List<BlockPos> old = oldBlocks(helper);
			helper.assertTrue(old.isEmpty(), "the old drawing's blocks were put into today's house at " + first(old));
			helper.assertTrue(where(helper, Blocks.STONE_BRICKS).size() == 21 && where(helper, Blocks.SPRUCE_PLANKS).size() == 15,
				"today's house: " + where(helper, Blocks.STONE_BRICKS).size() + " of 21 stone bricks, " + where(helper, Blocks.SPRUCE_PLANKS).size() + " of 15 spruce");
		});
	}

	// ---- B1370: upgrades -------------------------------------------------------------------------------------------------

	/**
	 * B1370 with two restarts: Level 1 of the old drawing upgraded through the Upgrades page, the world restarted while the
	 * old level is cleared and again once the roof goes on. At the end: nothing of the old Level 1 left, the next level
	 * whole (21 stone bricks, 15 spruce, 21 smooth stone), the chest with its 7 emeralds kept, the builder free.
	 */
	//$ gametest_ticks_batch AREA '3000' '"qaOld1121cUpgrade"'
	@GameTest(template = AREA, timeoutTicks = 3000, batch = "qaOld1121cUpgrade")
	public void anUpgradeOfAnOldDrawingLevelRestartedTwiceLeavesNothingOld(GameTestHelper helper) {
		ResourceLocation id = id(helper, "upg");
		Stage s = stage(helper, id, true);
		standOld(helper, s, true, false);
		Villager[] builder = {null};
		UpgradesPage.Outcome[] outcome = {null};
		int[] reloads = {0};
		helper.runAfterDelay(2, () -> {
			rebuild(s.level(), id, true);
			ResourceLocation next = BlueprintUpgrades.upgradeOf(id);
			install(s.level(), next, newBlueprint(next, true));
			reload(helper, s.level(), null);
			builder[0] = builder(helper, s);
			outcome[0] = UpgradesPage.order(s.level(), helper.absolutePos(HALL), s.owner(), s.placement());
			helper.assertTrue(outcome[0].ok(), "the upgrade was refused: " + outcome[0]);
		});
		helper.onEachTick(() -> {
			if (outcome[0] == null || sites(s) == 0) {
				return;
			}
			boolean early = reloads[0] == 0 && oldBlocks(helper).size() < 38;
			boolean late = reloads[0] == 1 && !where(helper, Blocks.SMOOTH_STONE).isEmpty();
			if (early || late) {
				reloads[0]++;
				builder[0] = reload(helper, s.level(), builder[0]);
			}
		});
		helper.succeedWhen(() -> {
			helper.assertTrue(outcome[0] != null && sites(s) == 0 && !ModAttachments.BUILDER_JOB.has(builder[0]), "still upgrading");
			helper.assertTrue(reloads[0] == 2, "(setup) restarted " + reloads[0] + " times, not 2");
			List<BlockPos> old = oldBlocks(helper);
			helper.assertTrue(old.isEmpty(), "the upgraded house keeps " + old.size() + " blocks of the old Level 1 at " + first(old)
				+ "; the bed " + (is(helper, BED_FOOT, Blocks.RED_BED) ? "still stands" : "is gone"));
			helper.assertTrue(where(helper, Blocks.STONE_BRICKS).size() == 21 && where(helper, Blocks.SPRUCE_PLANKS).size() == 15
				&& where(helper, Blocks.SMOOTH_STONE).size() == 21, "the next level: " + where(helper, Blocks.STONE_BRICKS).size() + "/21 stone bricks, "
				+ where(helper, Blocks.SPRUCE_PLANKS).size() + "/15 spruce, " + where(helper, Blocks.SMOOTH_STONE).size() + "/21 smooth stone");
			helper.assertBlockPresent(Blocks.CHEST, at(CHEST));
			Container chest = helper.getBlockEntity(at(CHEST));
			helper.assertTrue(chest.countItem(Items.EMERALD) == 7, "the chest lost its emeralds: " + chest.countItem(Items.EMERALD));
		});
	}

	// ---- B1371: sites mid-build ------------------------------------------------------------------------------------------

	/**
	 * B1371 with two restarts: a site of the old drawing caught in its STRUCTURE when the rebuilt drawing ships (restart 1),
	 * and restarted again further on. It still finishes as the drawing it was begun from: 25 cobblestone, 15 oak planks,
	 * nothing of the new drawing.
	 */
	//$ gametest_ticks_batch AREA '3000' '"qaOld1121cMidBuild"'
	@GameTest(template = AREA, timeoutTicks = 3000, batch = "qaOld1121cMidBuild")
	public void aSiteMidBuildRestartedTwiceAfterTheRebuildKeepsItsOldDrawing(GameTestHelper helper) {
		ResourceLocation id = id(helper, "mid2");
		Stage s = stage(helper, id, false);
		Villager[] builder = {builder(helper, s)};
		BuildSite site = Builders.start(s.level(), builder[0], s.owner(), id, s.placement());
		helper.assertTrue(site != null, "(setup) the old drawing didn't start");
		UUID siteId = site.id();
		int[] reloads = {0};
		helper.onEachTick(() -> {
			BuildSite now = BuildSiteManager.get(s.level()).get(siteId);
			if (now == null || now.stage() != BuildPlan.Stage.STRUCTURE) {
				return;
			}
			int planks = where(helper, Blocks.OAK_PLANKS).size();
			if (reloads[0] == 0 && where(helper, Blocks.COBBLESTONE).size() == 25 && planks >= 1 && planks <= 6) {
				reloads[0]++;
				rebuild(s.level(), id, false);
				builder[0] = reload(helper, s.level(), builder[0]);
			} else if (reloads[0] == 1 && planks >= 9 && planks <= 13) {
				reloads[0]++;
				builder[0] = reload(helper, s.level(), builder[0]);
			}
		});
		helper.succeedWhen(() -> {
			helper.assertTrue(reloads[0] == 2, "(setup) restarted " + reloads[0] + " times, not 2");
			helper.assertTrue(BuildSiteManager.get(s.level()).get(siteId) == null, "the site hasn't ended");
			int cobble = where(helper, Blocks.COBBLESTONE).size();
			int planks = where(helper, Blocks.OAK_PLANKS).size();
			List<BlockPos> mixed = newBlocks(helper);
			helper.assertTrue(cobble == 25 && planks == 15 && mixed.isEmpty(), "a house begun from the old drawing ended with " + cobble
				+ "/25 cobblestone, " + planks + "/15 oak planks and " + mixed.size() + " new-drawing blocks at " + first(mixed));
		});
	}

	// ---- B1374: the Move page, and controls --------------------------------------------------------------------------------

	/**
	 * Control (the boundary of B1374's fix): after the rebuild, a house built from TODAY's drawing (old one in the history)
	 * is planned on the Move page as today's 7x2x3 box, not the old 5x5.
	 */
	//$ gametest_ticks_batch AREA '200' '"qaOld1121cFpToday"'
	@GameTest(template = AREA, timeoutTicks = 200, batch = "qaOld1121cFpToday")
	public void theMovePagePlansAHouseOfTodaysDrawingAtTodaysSize(GameTestHelper helper) {
		ResourceLocation id = id(helper, "fptoday");
		Stage s = stage(helper, id, false);
		cleanUpMoves(helper, s);
		helper.runAfterDelay(2, () -> {
			rebuild(s.level(), id, false);
			reload(helper, s.level(), null);
			for (Blueprint.Entry e : newDrawing(false)) {
				put(helper, at(e.pos()), e.state());
			}
			BuildSiteManager.get(s.level()).recordFinished(id, s.placement(), s.owner().getUUID());
			pick(helper, s, s.placement().origin());
			MovePage.Picking p = MovePage.picking(s.owner()).orElseThrow();
			BoundingBox want = BoundingBox.fromCorners(helper.absolutePos(HOUSE), helper.absolutePos(at(new BlockPos(6, 1, 2))));
			helper.assertTrue(p.old().equals(want), "the Move page's 'now' box is " + p.old() + ", not today's house's " + want);
			helper.assertTrue(p.plan().size().equals(new Vec3i(7, 2, 3)), "the picker plans a " + p.plan().size() + " building, not the 7x2x3 house");
			helper.succeed();
		});
	}

	/** The player clicks {@code stack} onto the top of the block at {@code clicked} (local to the house); the placement it got. */
	static Optional<BlueprintData.Placement> clickOnto(GameTestHelper helper, ServerPlayer player, ItemStack stack, BlockPos clicked) {
		player.setYRot(0);
		player.setItemInHand(InteractionHand.MAIN_HAND, stack);
		BlockPos abs = helper.absolutePos(at(clicked));
		player.getMainHandItem().useOn(new UseOnContext(player, InteractionHand.MAIN_HAND, new BlockHitResult(Vec3.atCenterOf(abs), Direction.UP, abs, false)));
		return BlueprintItem.data(player.getMainHandItem()).flatMap(BlueprintData::placement);
	}

	static void upgradeClick(GameTestHelper helper, String what, BlockPos clicked) {
		ResourceLocation id = id(helper, what);
		Stage s = stage(helper, id, true);
		standOld(helper, s, true, false);
		helper.runAfterDelay(2, () -> {
			rebuild(s.level(), id, true);
			ResourceLocation next = BlueprintUpgrades.upgradeOf(id);
			install(s.level(), next, newBlueprint(next, true));
			reload(helper, s.level(), null);
			Optional<BlueprintData.Placement> placed = clickOnto(helper, s.owner(), BlueprintItem.create(next, null), clicked);
			if (placed.isEmpty()) {
				throw new GameTestAssertException("clicking the Level 2 blueprint onto the house's " + blockAt(helper, clicked) + " at " + clicked + " placed nothing");
			}
			helper.assertTrue(placed.get().equals(s.placement()), "the Level 2 blueprint clicked onto the old house's " + blockAt(helper, clicked) + " at "
				+ clicked + " didn't line up with the house: placed at " + placed.get().origin() + " " + placed.get().rotation() + ", the house is at "
				+ s.placement().origin() + " " + s.placement().rotation());
			helper.succeed();
		});
	}

	/**
	 * Control (builder.md: an upgrade blueprint clicked onto the finished building it upgrades lines up with it): the next
	 * level's blueprint clicked onto the old house's front wall (inside both drawings' boxes) lines up with the house.
	 */
	//$ gametest_ticks_batch AREA '200' '"qaOld1121cClickFront"'
	@GameTest(template = AREA, timeoutTicks = 200, batch = "qaOld1121cClickFront")
	public void anUpgradeBlueprintClickedOnTheOldHousesFrontLinesUpWithIt(GameTestHelper helper) {
		upgradeClick(helper, "clkfront", new BlockPos(1, 1, 0));
	}

	/**
	 * B1433 (builder.md: an upgrade blueprint clicked onto the finished building it upgrades lines up with it): clicked onto
	 * the old house's back wall (outside the rebuilt drawing's 7x3 box, inside the 5x5 house that stands), it lines up with
	 * the house as it does on the front wall (the control above).
	 */
	//$ gametest_ticks_batch AREA '200' '"qaOld1121cClickBack"'
	@GameTest(template = AREA, timeoutTicks = 200, batch = "qaOld1121cClickBack")
	public void anUpgradeBlueprintClickedOnTheOldHousesBackLinesUpWithIt(GameTestHelper helper) {
		upgradeClick(helper, "clkback", new BlockPos(1, 1, 4));
	}

	/**
	 * B1432, B1372 ("at the least a take-down that removed nothing doesn't forget the record"): the rebuilt drawing ships WITHOUT
	 * the old one in the history, so nothing can tell what the house was built from. The sneak take-down then removes
	 * nothing; the house must stay listed in the hall (it can still be moved, repaired or refreshed).
	 */
	//$ gametest_ticks_batch AREA '1500' '"qaOld1121cTdNothing"'
	@GameTest(template = AREA, timeoutTicks = 1500, batch = "qaOld1121cTdNothing")
	public void aTakeDownThatRemovesNothingKeepsTheHousesRecord(GameTestHelper helper) {
		ResourceLocation id = id(helper, "tdnone");
		Stage s = stage(helper, id, true);
		standOld(helper, s, true, false);
		Villager[] builder = {null};
		helper.runAfterDelay(2, () -> {
			install(s.level(), id, newBlueprint(id, false)); // no history: the old drawing is unknown
			reload(helper, s.level(), null);
			builder[0] = builder(helper, s);
			takeDown(helper, s, builder[0]);
		});
		helper.succeedWhen(() -> {
			helper.assertTrue(builder[0] != null && sites(s) == 0 && !ModAttachments.BUILDER_JOB.has(builder[0]), "still taking it down");
			int left = oldBlocks(helper).size() + where(helper, Blocks.RED_BED).size();
			helper.assertTrue(left == 0 || listed(s), "the take-down left " + left + " of the house's 42 blocks standing and the hall forgot the house");
		});
	}
}
