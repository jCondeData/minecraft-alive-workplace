package io.github.jcondedata.aliveworkplace.test;

import io.github.jcondedata.aliveworkplace.blueprint.BlueprintData;
import io.github.jcondedata.aliveworkplace.blueprint.BlueprintItem;
import io.github.jcondedata.aliveworkplace.blueprint.BlueprintLibrary;
import io.github.jcondedata.aliveworkplace.blueprint.Blueprints;
import io.github.jcondedata.aliveworkplace.build.BuildSite;
import io.github.jcondedata.aliveworkplace.build.BuildSiteManager;
import io.github.jcondedata.aliveworkplace.build.Builders;
import io.github.jcondedata.aliveworkplace.hall.VillageProtection;
import io.github.jcondedata.aliveworkplace.move.MoveSite;
import io.github.jcondedata.aliveworkplace.move.MoveSites;
import io.github.jcondedata.aliveworkplace.move.PendingMoves;
import io.github.jcondedata.aliveworkplace.registry.ModBlocks;
import io.github.jcondedata.aliveworkplace.registry.ModComponents;
import java.util.List;
import java.util.Optional;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;

/**
 * QA (lane B, 2026-11-14) of B1185's fix: with building A's move to spot S confirmed or under way, a player handing a
 * builder a blueprint whose footprint reaches S is refused with "That spot is kept for <building>, which is moving
 * there."; a spot clear of S is still taken. Written from ROADMAP B1185, moving-buildings.md (wiki and guide) and
 * builder.md. The village from QaMoveSpotSaves1111eGameTests: A (Test Hut, x 3-7, z 3-7) moving to S (x 11-15, z 3-7).
 * The Test Hut is 5 by 5; placed at origin o, unturned, it covers o..o+4; turned 180 it covers o-4..o; mirrored
 * left-right its z runs o-4..o. Each hand-over goes through the real click on the builder (the player's own entry point;
 * sneaking for a take-down); a site he takes is dropped straight away so it never refuses the next one.
 */
public class QaMoveSpotBuild1114bGameTests implements FabricGameTest {
	private static final String AREA = "aliveworkplace_test:big_area";
	private static final ResourceLocation HUT = ResourceLocation.fromNamespaceAndPath("aliveworkplace_test", "test_hut");
	/** The builder's Blueprint Table, away from every footprint tried here, within the village and in reach. */
	private static final BlockPos BENCH = new BlockPos(9, 2, 19);

	record Hand(boolean taken, boolean takeDown, List<String> said) {
		boolean refusedFor(String building) {
			return !taken && said.contains(line(building));
		}
	}

	record Setup(QaMoveSpotSaves1111eGameTests.Village v, Villager builder, QaStageFiveGameTests.Client client) {
	}

	static String line(String building) {
		return "That spot is kept for " + building + ", which is moving there.";
	}

	static String hut() {
		return Blueprints.displayName(HUT).getString();
	}

	static Setup setup(GameTestHelper helper) {
		QaMoveSpotSaves1111eGameTests.Village v = QaMoveSpotSaves1111eGameTests.stage(helper);
		boolean protection = VillageProtection.ENABLED;
		VillageProtection.ENABLED = false;
		Leftovers.after(helper, () -> VillageProtection.ENABLED = protection);
		helper.setBlock(BENCH, ModBlocks.BLUEPRINT_TABLE);
		Villager builder = helper.spawn(EntityType.VILLAGER, BENCH.south());
		builder.setNoAi(true);
		Builders.employ(v.level(), builder, helper.absolutePos(BENCH));
		QaStageFiveGameTests.Client client = QaStageSixGameTests.client(helper, GameType.SURVIVAL, QaStageSixGameTests.name("qaMB4b"));
		return new Setup(v, builder, client);
	}

	/**
	 * The player hands the builder a Test Hut placed at {@code rel} (turned and mirrored as given; {@code sneak}: a
	 * take-down), as a click on him. Whether he took it (a site on that placement), and what the player was told.
	 */
	static Hand hand(GameTestHelper helper, Setup s, BlockPos rel, Rotation rotation, Mirror mirror, boolean sneak) {
		ServerLevel level = s.v().level();
		ItemStack blueprint = BlueprintItem.create(HUT, BlueprintLibrary.get(level, HUT).orElseThrow().size());
		BlueprintData.Placement at = new BlueprintData.Placement(level.dimension().location(), helper.absolutePos(rel), rotation, mirror);
		blueprint.set(ModComponents.BLUEPRINT, BlueprintItem.data(blueprint).orElseThrow().withPlacement(Optional.of(at)));
		s.client().seen().clear();
		s.client().player().setShiftKeyDown(sneak);
		s.client().player().setItemInHand(InteractionHand.MAIN_HAND, blueprint);
		QaStageSixGameTests.clickVillager(s.client().player(), s.builder(), s.client().seen());
		s.client().player().setShiftKeyDown(false);
		BuildSite taken = BuildSiteManager.get(level).all().stream().filter(site -> site.placement().equals(at)).findFirst().orElse(null);
		boolean takeDown = taken != null && taken.isDeconstruction();
		if (taken != null) {
			BuildSiteManager.get(level).remove(taken.id());
		}
		return new Hand(taken != null, takeDown, s.client().seen().stream().map(Component::getString).toList());
	}

	static Hand hand(GameTestHelper helper, Setup s, int x, int z) {
		return hand(helper, s, new BlockPos(x, 2, z), Rotation.NONE, Mirror.NONE, false);
	}

	static void assertRefused(GameTestHelper helper, Hand h, String what) {
		helper.assertTrue(h.refusedFor(hut()), what + ": expected refused with \"" + line(hut()) + "\"; taken " + h.taken() + ", he said " + h.said());
	}

	static void assertTaken(GameTestHelper helper, Hand h, String what) {
		helper.assertTrue(h.taken(), what + ": expected taken; he said " + h.said());
		helper.assertTrue(h.said().stream().noneMatch(l -> l.contains("which is moving there")), what + ": taken, yet told the spot is kept: " + h.said());
	}

	/** Saves the moves' records and loads them back, as a server restart does (QaMoveSpotSaves1111eGameTests' pattern). */
	static void reload(ServerLevel level) {
		CompoundTag pending = PendingMoves.get(level).save(new CompoundTag(), level.registryAccess());
		CompoundTag sites = MoveSites.get(level).save(new CompoundTag(), level.registryAccess());
		level.getDataStorage().set(PendingMoves.NAME, PendingMoves.load(pending, level.registryAccess()));
		level.getDataStorage().set(MoveSites.NAME, MoveSites.load(sites, level.registryAccess()));
	}

	static MoveSite begin(QaMoveSpotSaves1111eGameTests.Village v, PendingMoves.Move move) {
		return MoveSites.get(v.level()).begin(v.level(), move).orElseThrow(() -> new AssertionError("A's move didn't start (setup)"));
	}

	static void stepUntil(ServerLevel level, MoveSite site, java.util.function.Predicate<MoveSite> until) {
		for (int i = 0; i < 100_000 && !until.test(site) && site.stage() != MoveSite.Stage.DONE; i++) {
			MoveSites.get(level).step(level, site);
		}
	}

	/**
	 * S's edges from both sides, unturned: overlapping S by one column on the east, north and south side and by one
	 * block at each of the four corners is refused with the move's line; one block clear on the east, north and south
	 * and diagonally off the north-east and south-east corners is taken. (The west side lies against A itself, x 3-7:
	 * one column over S there is refused too; a hut a block clear of S on the west would stand on A, so it isn't tried.)
	 */
	//$ gametest_ticks_batch AREA '100' '"qaMoveBuild1114bEdges"'
	@GameTest(template = AREA, timeoutTicks = 100, batch = "qaMoveBuild1114bEdges")
	public void everyEdgeAndCornerOfTheKeptSpotFromBothSides(GameTestHelper helper) {
		Setup s = setup(helper);
		helper.runAfterDelay(2, () -> {
			QaMoveSpotSaves1111eGameTests.planA(helper, s.v());
			assertRefused(helper, hand(helper, s, 15, 3), "east, sharing S's column x 15 (x 15-19, z 3-7)");
			assertTaken(helper, hand(helper, s, 16, 3), "east, a block clear of S (x 16-20, z 3-7)");
			assertRefused(helper, hand(helper, s, 11, -1), "north, sharing S's row z 3 (x 11-15, z -1..3)");
			assertTaken(helper, hand(helper, s, 11, -2), "north, a block clear of S (x 11-15, z -2..2)");
			assertRefused(helper, hand(helper, s, 10, 7), "south, sharing S's row z 7 (x 10-14, z 7-11)");
			assertTaken(helper, hand(helper, s, 10, 8), "south, a block clear of S (x 10-14, z 8-12)");
			assertRefused(helper, hand(helper, s, 7, 4), "west, sharing S's column x 11 (x 7-11, z 4-8)");
			assertRefused(helper, hand(helper, s, 15, -1), "north-east corner, one block (15, 3) shared");
			assertRefused(helper, hand(helper, s, 15, 7), "south-east corner, one block (15, 7) shared");
			assertRefused(helper, hand(helper, s, 7, 7), "south-west corner, one block (11, 7) shared");
			assertRefused(helper, hand(helper, s, 7, -1), "north-west corner, one block (11, 3) shared");
			assertTaken(helper, hand(helper, s, 16, -2), "diagonally off the north-east corner (x 16-20, z -2..2)");
			assertTaken(helper, hand(helper, s, 16, 8), "diagonally off the south-east corner (x 16-20, z 8-12)");
			helper.succeed();
		});
	}

	/**
	 * Turned and mirrored blueprints are judged by where they really stand: origin (16, 5) turned 180 covers x 12-16 (over
	 * S: refused) though unturned it would be clear; origin (14, 8) mirrored left-right covers z 4-8 (over S: refused);
	 * origin (14, 2) turned 180 covers x 10-14, z -2..2 (clear: taken) though unturned it would reach S.
	 */
	//$ gametest_ticks_batch AREA '100' '"qaMoveBuild1114bTurned"'
	@GameTest(template = AREA, timeoutTicks = 100, batch = "qaMoveBuild1114bTurned")
	public void turnedAndMirroredFootprintsAreJudgedWhereTheyStand(GameTestHelper helper) {
		Setup s = setup(helper);
		helper.runAfterDelay(2, () -> {
			QaMoveSpotSaves1111eGameTests.planA(helper, s.v());
			assertTaken(helper, hand(helper, s, new BlockPos(16, 2, 5), Rotation.NONE, Mirror.NONE, false), "control, origin (16, 5) unturned (x 16-20, z 5-9)");
			assertRefused(helper, hand(helper, s, new BlockPos(16, 2, 5), Rotation.CLOCKWISE_180, Mirror.NONE, false), "origin (16, 5) turned 180 (x 12-16, z 1-5)");
			assertTaken(helper, hand(helper, s, new BlockPos(14, 2, 8), Rotation.NONE, Mirror.NONE, false), "control, origin (14, 8) unturned (x 14-18, z 8-12)");
			assertRefused(helper, hand(helper, s, new BlockPos(14, 2, 8), Rotation.NONE, Mirror.LEFT_RIGHT, false), "origin (14, 8) mirrored left-right (x 14-18, z 4-8)");
			assertRefused(helper, hand(helper, s, new BlockPos(14, 2, 2), Rotation.NONE, Mirror.NONE, false), "origin (14, 2) unturned (x 14-18, z 2-6)");
			assertTaken(helper, hand(helper, s, new BlockPos(14, 2, 2), Rotation.CLOCKWISE_180, Mirror.NONE, false), "origin (14, 2) turned 180 (x 10-14, z -2..2), clear of S");
			assertRefused(helper, hand(helper, s, new BlockPos(16, 2, 7), Rotation.CLOCKWISE_90, Mirror.NONE, false), "origin (16, 7) turned 90 (x 12-16, z 7-11), sharing S's row z 7");
			helper.succeed();
		});
	}

	/** A's move under way: while the Mover takes A down, and again while he puts it up at S, S is refused naming A. */
	//$ gametest_ticks_batch AREA '200' '"qaMoveBuild1114bUnderWay"'
	@GameTest(template = AREA, timeoutTicks = 200, batch = "qaMoveBuild1114bUnderWay")
	public void theKeptSpotIsRefusedWhileTheMoverTakesItDownAndPutsItUp(GameTestHelper helper) {
		Setup s = setup(helper);
		helper.runAfterDelay(2, () -> {
			MoveSite site = begin(s.v(), QaMoveSpotSaves1111eGameTests.planA(helper, s.v()));
			int whole = QaMoveSpotSaves1111eGameTests.count(helper)[0];
			stepUntil(s.v().level(), site, m -> QaMoveSpotSaves1111eGameTests.count(helper)[0] < whole / 2);
			helper.assertTrue(site.stage() == MoveSite.Stage.DISMANTLE, "setup: A half taken down, yet the move is at " + site.stage());
			assertRefused(helper, hand(helper, s, 11, 3), "A half taken down, a hut right on S");
			assertTaken(helper, hand(helper, s, 16, 3), "A half taken down, control a block east of S");
			stepUntil(s.v().level(), site, m -> m.stage() == MoveSite.Stage.STRUCTURE);
			helper.assertTrue(site.stage() == MoveSite.Stage.STRUCTURE && QaMoveSpotSaves1111eGameTests.count(helper)[1] > 0,
				"setup: A going up at S, yet " + site.stage() + " with " + QaMoveSpotSaves1111eGameTests.count(helper)[1] + " blocks at S");
			assertRefused(helper, hand(helper, s, 13, 5), "A going up at S, a hut over S's middle");
			assertRefused(helper, hand(helper, s, 15, 7), "A going up at S, a hut sharing S's south-east corner");
			helper.succeed();
		});
	}

	/** A builder already busy (a build of his own): a hut on S is refused all the same, and his queue gains nothing. */
	//$ gametest_ticks_batch AREA '100' '"qaMoveBuild1114bBusy"'
	@GameTest(template = AREA, timeoutTicks = 100, batch = "qaMoveBuild1114bBusy")
	public void aBusyBuilderIsRefusedTheKeptSpotToo(GameTestHelper helper) {
		Setup s = setup(helper);
		helper.runAfterDelay(2, () -> {
			QaMoveSpotSaves1111eGameTests.planA(helper, s.v());
			// his own job first: a hut south of the Hall (kept, so the next one is queued behind it)
			ItemStack first = BlueprintItem.create(HUT, BlueprintLibrary.get(s.v().level(), HUT).orElseThrow().size());
			first.set(ModComponents.BLUEPRINT, BlueprintItem.data(first).orElseThrow().withPlacement(Optional.of(new BlueprintData.Placement(
				s.v().level().dimension().location(), helper.absolutePos(new BlockPos(16, 2, 13)), Rotation.NONE, Mirror.NONE))));
			s.client().player().setItemInHand(InteractionHand.MAIN_HAND, first);
			QaStageSixGameTests.clickVillager(s.client().player(), s.builder(), s.client().seen());
			BuildSite own = Builders.activeSite(s.v().level(), s.builder());
			helper.assertTrue(own != null, "setup: the builder didn't take his first job (x 16-20, z 13-17): " + s.client().seen().stream().map(Component::getString).toList());
			Leftovers.after(helper, () -> BuildSiteManager.get(s.v().level()).remove(own.id()));
			int queued = Builders.queue(s.v().level(), s.builder()).size();
			assertRefused(helper, hand(helper, s, 11, 3), "busy builder, a hut on S");
			helper.assertTrue(Builders.queue(s.v().level(), s.builder()).size() == queued && Builders.activeSite(s.v().level(), s.builder()) == own,
				"a refused hut on S still changed the busy builder's work: queue " + Builders.queue(s.v().level(), s.builder()).size() + " (was " + queued + ")");
			assertTaken(helper, hand(helper, s, 16, 3), "busy builder, control a block east of S (queued)");
			helper.succeed();
		});
	}

	/** A sneak hand-over (take down) of a blueprint placed on S is refused the same way: nothing stands there to take down, and the spot is kept. */
	//$ gametest_ticks_batch AREA '100' '"qaMoveBuild1114bSneak"'
	@GameTest(template = AREA, timeoutTicks = 100, batch = "qaMoveBuild1114bSneak")
	public void aTakeDownOnTheKeptSpotIsRefusedToo(GameTestHelper helper) {
		Setup s = setup(helper);
		helper.runAfterDelay(2, () -> {
			QaMoveSpotSaves1111eGameTests.planA(helper, s.v());
			Hand h = hand(helper, s, new BlockPos(11, 2, 3), Rotation.NONE, Mirror.NONE, true);
			assertRefused(helper, h, "a sneak (take-down) hand-over of a hut on S");
			helper.succeed();
		});
	}

	/**
	 * A's move called off half way (some of A up at S) and run until it stands back where it was: S is nobody's spot any
	 * more and the builder takes a hut there; so does a move called off before anything came down.
	 */
	//$ gametest_ticks_batch AREA '300' '"qaMoveBuild1114bCalledOff"'
	@GameTest(template = AREA, timeoutTicks = 300, batch = "qaMoveBuild1114bCalledOff")
	public void aCalledOffMoveFreesTheSpotOnceItsBack(GameTestHelper helper) {
		Setup s = setup(helper);
		helper.runAfterDelay(2, () -> {
			int[] before = QaMoveSpotSaves1111eGameTests.count(helper);
			MoveSite site = begin(s.v(), QaMoveSpotSaves1111eGameTests.planA(helper, s.v()));
			String wrong = QaMoveSpotSaves1111eGameTests.callOffHalfWay(helper, s.v(), site,
				m -> m.stage() == MoveSite.Stage.STRUCTURE && QaMoveSpotSaves1111eGameTests.count(helper)[1] > 5);
			helper.assertTrue(wrong == null, wrong);
			wrong = QaMoveSpotSaves1111eGameTests.backWhole(helper, s.v(), before);
			helper.assertTrue(wrong == null, wrong);
			assertTaken(helper, hand(helper, s, 11, 3), "A's move called off at STRUCTURE and A back whole, a hut on S");
			// and planned again, then called off before the Mover took anything down
			MoveSite again = begin(s.v(), QaMoveSpotSaves1111eGameTests.planA(helper, s.v()));
			assertRefused(helper, hand(helper, s, 11, 3), "A's move planned again, a hut on S");
			wrong = QaMoveSpotSaves1111eGameTests.callOffHalfWay(helper, s.v(), again, m -> true);
			helper.assertTrue(wrong == null, wrong);
			assertTaken(helper, hand(helper, s, 13, 5), "A's second move called off before anything came down, a hut on S");
			helper.succeed();
		});
	}

	/**
	 * A's move finished: A stands on S. The spot is no longer "kept for A, which is moving there" (nothing is moving), so
	 * that line is never said; a sneak take-down of A where it now stands is taken as a take-down (builder.md: "A placed
	 * Blueprint sneak-handed over its finished building: take it down").
	 */
	//$ gametest_ticks_batch AREA '300' '"qaMoveBuild1114bDone"'
	@GameTest(template = AREA, timeoutTicks = 300, batch = "qaMoveBuild1114bDone")
	public void aFinishedMoveNoLongerKeepsTheSpot(GameTestHelper helper) {
		Setup s = setup(helper);
		helper.runAfterDelay(2, () -> {
			MoveSite site = begin(s.v(), QaMoveSpotSaves1111eGameTests.planA(helper, s.v()));
			stepUntil(s.v().level(), site, m -> false);
			helper.assertTrue(site.stage() == MoveSite.Stage.DONE && site.skipped() == 0, "setup: A's move ended " + site.stage() + ", skipped " + site.skipped());
			Hand plain = hand(helper, s, 11, 3);
			helper.assertTrue(plain.said().stream().noneMatch(l -> l.contains("which is moving there")),
				"A's move is over (A stands on S), yet the builder says the spot is kept for a move: " + plain.said());
			Hand down = hand(helper, s, new BlockPos(11, 2, 3), Rotation.NONE, Mirror.NONE, true);
			helper.assertTrue(down.taken() && down.takeDown(), "A stands on S after its move; a sneak take-down of it there: taken " + down.taken()
				+ ", take-down " + down.takeDown() + "; he said " + down.said());
			helper.succeed();
		});
	}

	/** A's move confirmed, then saved and loaded as a restart does: a hut on S is still refused naming A; one clear of S is taken. */
	//$ gametest_ticks_batch AREA '100' '"qaMoveBuild1114bReload"'
	@GameTest(template = AREA, timeoutTicks = 100, batch = "qaMoveBuild1114bReload")
	public void theKeptSpotSurvivesASaveAndReload(GameTestHelper helper) {
		Setup s = setup(helper);
		helper.runAfterDelay(2, () -> {
			QaMoveSpotSaves1111eGameTests.planA(helper, s.v());
			reload(s.v().level());
			helper.assertTrue(PendingMoves.get(s.v().level()).forBuilding(s.v().a()).isPresent(), "A's planned move didn't survive the reload");
			assertRefused(helper, hand(helper, s, 11, 3), "after a reload, a hut on S");
			assertTaken(helper, hand(helper, s, 16, 3), "after a reload, control a block east of S");
			helper.succeed();
		});
	}

	/** A's move under way (A going up at S), saved and loaded: S is still refused, naming A by the name the Mover saved. */
	//$ gametest_ticks_batch AREA '200' '"qaMoveBuild1114bReloadUp"'
	@GameTest(template = AREA, timeoutTicks = 200, batch = "qaMoveBuild1114bReloadUp")
	public void anUnderWayMovesSpotSurvivesASaveAndReload(GameTestHelper helper) {
		Setup s = setup(helper);
		helper.runAfterDelay(2, () -> {
			MoveSite site = begin(s.v(), QaMoveSpotSaves1111eGameTests.planA(helper, s.v()));
			stepUntil(s.v().level(), site, m -> m.stage() == MoveSite.Stage.STRUCTURE);
			reload(s.v().level());
			MoveSite loaded = MoveSites.get(s.v().level()).forBuilding(s.v().a()).orElseThrow(() -> new AssertionError("A's move didn't survive the reload"));
			helper.assertTrue(loaded != site && loaded.stage() == MoveSite.Stage.STRUCTURE, "A's move after the reload: " + loaded.stage());
			assertRefused(helper, hand(helper, s, 11, 3), "after a reload with A going up at S, a hut on S");
			helper.succeed();
		});
	}

	/** The refusal's sentence as a player reads it: the building's real name, no raw key, no placeholder, American English. */
	//$ gametest_ticks_batch AREA '100' '"qaMoveBuild1114bLine"'
	@GameTest(template = AREA, timeoutTicks = 100, batch = "qaMoveBuild1114bLine")
	public void theRefusalReadsWithTheBuildingsName(GameTestHelper helper) {
		Setup s = setup(helper);
		helper.runAfterDelay(2, () -> {
			QaMoveSpotSaves1111eGameTests.planA(helper, s.v());
			Hand h = hand(helper, s, 11, 3);
			helper.assertTrue(hut().equals("Test Hut"), "setup: the Test Hut's name renders as \"" + hut() + "\"");
			helper.assertTrue(h.said().contains("That spot is kept for Test Hut, which is moving there."), "the refusal read " + h.said());
			helper.assertTrue(h.said().size() == 1, "one line for one refusal, not " + h.said());
			for (String l : h.said()) {
				helper.assertTrue(!l.contains("aliveworkplace.") && !l.contains("%") && !l.contains("{"), "a raw key or placeholder in " + l);
			}
			helper.succeed();
		});
	}
}
