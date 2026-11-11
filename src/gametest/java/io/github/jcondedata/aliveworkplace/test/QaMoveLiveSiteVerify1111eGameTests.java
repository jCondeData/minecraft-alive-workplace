package io.github.jcondedata.aliveworkplace.test;

import io.github.jcondedata.aliveworkplace.blueprint.BlueprintData;
import io.github.jcondedata.aliveworkplace.build.BuildSite;
import io.github.jcondedata.aliveworkplace.build.BuildSiteManager;
import io.github.jcondedata.aliveworkplace.move.MoveSite;
import io.github.jcondedata.aliveworkplace.move.MoveSites;
import io.github.jcondedata.aliveworkplace.move.PendingMoves;
import java.util.Optional;
import java.util.UUID;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;

/**
 * QA (lane E, 2026-11-11), verifying B1182 from its spec: "a confirmed move waits (nothing taken down, the move kept) while
 * a live build site holds the building, and goes ahead once the site is done". 45.6: "a building with a live build site"
 * (MoveRules: "one at its own corner or one overlapping it"). Edges: a site overlapping A by one row waits, one just
 * touching A's side doesn't; a site in another dimension at A's coordinates doesn't; another building's move isn't held
 * up behind A's; and a whole save (build sites too) keeps A held, then lets it go once the reloaded site is done.
 * Village and helpers from {@link QaMoveSpotSaves1111eGameTests} (the Test Hut: 5 wide, A at (3, 2, 3) to (7, 6, 7)).
 */
public class QaMoveLiveSiteVerify1111eGameTests implements FabricGameTest {
	private static final String AREA = "aliveworkplace_test:big_area";
	private static final ResourceLocation HUT = ResourceLocation.fromNamespaceAndPath("aliveworkplace_test", "test_hut");

	/** A live build site of the Test Hut at {@code origin} in {@code dimension}; removed when the test ends. */
	private static BuildSite site(GameTestHelper helper, ServerLevel level, ResourceLocation dimension, BlockPos origin) {
		BuildSite s = BuildSiteManager.get(level).create(UUID.randomUUID(), "Builder", HUT,
			new BlueprintData.Placement(dimension, origin, Rotation.NONE, Mirror.NONE));
		UUID id = s.id();
		Leftovers.after(helper, () -> BuildSiteManager.get(level).remove(id));
		helper.assertTrue(!s.isDone(), "the build site isn't live (setup)");
		return s;
	}

	/** Up to {@code n} Mover swings; the last site he worked on. */
	private static Optional<MoveSite> swings(QaMoveSpotSaves1111eGameTests.Village v, int n) {
		Optional<MoveSite> work = Optional.empty();
		for (int i = 0; i < n; i++) {
			work = MoveSites.get(v.level()).work(v.level(), v.hall());
			if (work.isEmpty() || work.get().stage() == MoveSite.Stage.DONE) {
				break;
			}
			MoveSites.get(v.level()).step(v.level(), work.get());
		}
		return work;
	}

	private static String held(GameTestHelper helper, QaMoveSpotSaves1111eGameTests.Village v, int[] before) {
		Optional<MoveSite> work = swings(v, 20);
		int[] now = QaMoveSpotSaves1111eGameTests.count(helper);
		if (work.isPresent() || now[0] != before[0] || now[1] != 0) {
			return "the Mover " + work.map(w -> "started (" + w.stage() + ")").orElse("didn't start") + " and took " + (before[0] - now[0])
				+ " of A's " + before[0] + " blocks down; " + now[1] + " stand at S";
		}
		return PendingMoves.get(v.level()).forBuilding(v.a()).isPresent() ? null : "A's move was dropped, not kept";
	}

	private static String goes(QaMoveSpotSaves1111eGameTests.Village v) {
		Optional<MoveSite> work = swings(v, 1);
		return work.isPresent() && work.get().from().equals(v.a()) ? null : "A's move didn't start: " + work;
	}

	/** A site whose Test Hut overlaps A by one column (origin 4 west of A's) holds A's move like one at A's corner. */
	//$ gametest_ticks_batch AREA '100' '"qaMoveLiveV1111eOverlap"'
	@GameTest(template = AREA, timeoutTicks = 100, batch = "qaMoveLiveV1111eOverlap")
	public void aSiteOverlappingOneColumnHoldsTheMove(GameTestHelper helper) {
		QaMoveSpotSaves1111eGameTests.Village v = QaMoveSpotSaves1111eGameTests.stage(helper);
		helper.runAfterDelay(2, () -> {
			int[] before = QaMoveSpotSaves1111eGameTests.count(helper);
			QaMoveSpotSaves1111eGameTests.planA(helper, v);
			BuildSite s = site(helper, v.level(), v.level().dimension().location(), v.a().offset(-4, 0, 0));
			String why = held(helper, v, before);
			helper.assertTrue(why == null, "with a build site overlapping A by one column: " + why);
			BuildSiteManager.get(v.level()).remove(s.id());
			why = goes(v);
			helper.assertTrue(why == null, "once the overlapping site is gone: " + why);
			helper.succeed();
		});
	}

	/** A site just beside A (its Test Hut ends one block short of A's west wall) doesn't hold A's move. */
	//$ gametest_ticks_batch AREA '100' '"qaMoveLiveV1111eBeside"'
	@GameTest(template = AREA, timeoutTicks = 100, batch = "qaMoveLiveV1111eBeside")
	public void aSiteJustBesideDoesNotHoldTheMove(GameTestHelper helper) {
		QaMoveSpotSaves1111eGameTests.Village v = QaMoveSpotSaves1111eGameTests.stage(helper);
		helper.runAfterDelay(2, () -> {
			QaMoveSpotSaves1111eGameTests.planA(helper, v);
			site(helper, v.level(), v.level().dimension().location(), v.a().offset(-5, 0, 0));
			String why = goes(v);
			helper.assertTrue(why == null, "with a build site beside A, not on it: " + why);
			helper.succeed();
		});
	}

	/** A site at A's very coordinates in the Nether isn't on A: the move goes ahead. */
	//$ gametest_ticks_batch AREA '100' '"qaMoveLiveV1111eNether"'
	@GameTest(template = AREA, timeoutTicks = 100, batch = "qaMoveLiveV1111eNether")
	public void aSiteInAnotherDimensionDoesNotHoldTheMove(GameTestHelper helper) {
		QaMoveSpotSaves1111eGameTests.Village v = QaMoveSpotSaves1111eGameTests.stage(helper);
		helper.runAfterDelay(2, () -> {
			QaMoveSpotSaves1111eGameTests.planA(helper, v);
			site(helper, v.level(), Level.NETHER.location(), v.a());
			String why = goes(v);
			helper.assertTrue(why == null, "with a build site at A's coordinates in the Nether: " + why);
			helper.succeed();
		});
	}

	/** While builders hold A's move, B's confirmed move isn't stuck behind it: the Mover moves B whole, A stays put. */
	//$ gametest_ticks_batch AREA '200' '"qaMoveLiveV1111eOther"'
	@GameTest(template = AREA, timeoutTicks = 200, batch = "qaMoveLiveV1111eOther")
	public void anotherBuildingsMoveGoesAheadWhileAWaits(GameTestHelper helper) {
		QaMoveSpotSaves1111eGameTests.Village v = QaMoveSpotSaves1111eGameTests.stage(helper);
		helper.runAfterDelay(2, () -> {
			int[] before = QaMoveSpotSaves1111eGameTests.count(helper);
			QaMoveSpotSaves1111eGameTests.planA(helper, v);
			site(helper, v.level(), v.level().dimension().location(), v.a());
			BlockPos bTo = v.b().offset(8, 0, 0);
			PendingMoves.get(v.level()).put(new PendingMoves.Move(v.hall(), v.b(), HUT, bTo, Rotation.NONE, v.player().getUUID()));
			Optional<MoveSite> first = swings(v, 1);
			helper.assertTrue(first.isPresent() && first.get().from().equals(v.b()), "with A held, the Mover doesn't take B's move: " + first);
			swings(v, 5000);
			helper.assertTrue(MoveSites.get(v.level()).forBuilding(v.b()).isEmpty() && PendingMoves.get(v.level()).forBuilding(v.b()).isEmpty(),
				"B's move isn't over after 5000 swings");
			int[] now = QaMoveSpotSaves1111eGameTests.count(helper);
			helper.assertTrue(now[0] == before[0] && now[1] == 0, "moving B took " + (before[0] - now[0]) + " of A's blocks down");
			helper.assertTrue(PendingMoves.get(v.level()).forBuilding(v.a()).isPresent(), "A's held move was dropped while B moved");
			int bAt = 0;
			for (int x = 0; x < 5; x++) {
				for (int z = 0; z < 5; z++) {
					for (int y = 0; y < 5; y++) {
						if (!v.level().getBlockState(bTo.offset(x, y, z)).isAir()) {
							bAt++;
						}
					}
				}
			}
			helper.assertTrue(bAt == before[0], "B's new spot has " + bAt + " blocks, the Test Hut " + before[0]);
			helper.succeed();
		});
	}

	/** The whole save (build sites, moves and Mover sites) written and read back while A waits: still held; done after, it goes. */
	//$ gametest_ticks_batch AREA '100' '"qaMoveLiveV1111eSave"'
	@GameTest(template = AREA, timeoutTicks = 100, batch = "qaMoveLiveV1111eSave")
	public void aWholeSaveKeepsTheMoveHeldThenLetsItGo(GameTestHelper helper) {
		QaMoveSpotSaves1111eGameTests.Village v = QaMoveSpotSaves1111eGameTests.stage(helper);
		helper.runAfterDelay(2, () -> {
			int[] before = QaMoveSpotSaves1111eGameTests.count(helper);
			QaMoveSpotSaves1111eGameTests.planA(helper, v);
			UUID id = site(helper, v.level(), v.level().dimension().location(), v.a()).id();
			helper.assertTrue(held(helper, v, before) == null, "before the save: " + held(helper, v, before));
			ServerLevel level = v.level();
			Leftovers.after(helper, () -> { // stage()'s own clean-up holds the manager from before the reload
				for (BlockPos from : new BlockPos[] {v.a(), v.b()}) {
					BuildSiteManager.get(level).forgetFinished(new BlueprintData.Placement(level.dimension().location(), from, Rotation.NONE, Mirror.NONE));
				}
			});
			CompoundTag builds = BuildSiteManager.get(level).save(new CompoundTag(), level.registryAccess());
			CompoundTag pending = PendingMoves.get(level).save(new CompoundTag(), level.registryAccess());
			CompoundTag moving = MoveSites.get(level).save(new CompoundTag(), level.registryAccess());
			level.getDataStorage().set(BuildSiteManager.NAME, BuildSiteManager.load(builds, level.registryAccess()));
			level.getDataStorage().set(PendingMoves.NAME, PendingMoves.load(pending, level.registryAccess()));
			level.getDataStorage().set(MoveSites.NAME, MoveSites.load(moving, level.registryAccess()));
			BuildSite reloaded = BuildSiteManager.get(level).get(id);
			helper.assertTrue(reloaded != null && !reloaded.isDone(), "the live build site didn't come back from the save: " + reloaded);
			String why = held(helper, v, before);
			helper.assertTrue(why == null, "after the whole save and reload: " + why);
			for (int i = 0; i < 40 && !reloaded.isDone(); i++) {
				reloaded.finishList();
			}
			helper.assertTrue(reloaded.isDone(), "the reloaded site couldn't be finished (setup)");
			why = goes(v);
			helper.assertTrue(why == null, "once the reloaded site is done: " + why);
			helper.succeed();
		});
	}
}
