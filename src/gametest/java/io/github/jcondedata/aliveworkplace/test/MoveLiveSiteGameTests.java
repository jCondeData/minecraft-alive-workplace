package io.github.jcondedata.aliveworkplace.test;

import io.github.jcondedata.aliveworkplace.blueprint.BlueprintData;
import io.github.jcondedata.aliveworkplace.build.BuildSite;
import io.github.jcondedata.aliveworkplace.build.BuildSiteManager;
import io.github.jcondedata.aliveworkplace.hall.HallPages;
import io.github.jcondedata.aliveworkplace.hall.MovePage;
import io.github.jcondedata.aliveworkplace.hall.VillageHallScreen;
import io.github.jcondedata.aliveworkplace.move.MoveSite;
import io.github.jcondedata.aliveworkplace.move.MoveSites;
import io.github.jcondedata.aliveworkplace.move.PendingMoves;
import io.github.jcondedata.aliveworkplace.work.ChoiceMenu;
import java.util.Optional;
import java.util.UUID;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.core.component.DataComponents;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.component.ItemLore;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;

/**
 * B1182: a move confirmed before the builders start work on the building (45.6's live build site) waits, kept and saved,
 * until their site is done, and the Hall's Move page says why; then the Mover moves it whole. Village and helpers from
 * {@link QaMoveSpotSaves1111eGameTests}.
 */
public class MoveLiveSiteGameTests implements FabricGameTest {
	private static final String AREA = "aliveworkplace_test:big_area";
	private static final ResourceLocation HUT = ResourceLocation.fromNamespaceAndPath("aliveworkplace_test", "test_hut");
	private static final String WAITS = "screen.aliveworkplace.move.waits_builders";

	/** A live build site on A, after A's move was confirmed; removed when the test ends. */
	private static BuildSite repairA(GameTestHelper helper, QaMoveSpotSaves1111eGameTests.Village v) {
		BuildSiteManager sites = BuildSiteManager.get(v.level());
		BuildSite repair = sites.create(UUID.randomUUID(), "Builder", HUT,
			new BlueprintData.Placement(v.level().dimension().location(), v.a(), Rotation.NONE, Mirror.NONE));
		UUID id = repair.id();
		Leftovers.after(helper, () -> BuildSiteManager.get(v.level()).remove(id));
		helper.assertTrue(!repair.isDone(), "the build site on A isn't live (setup)");
		return repair;
	}

	/** A few of the Mover's swings, as {@code MoverWork} gives them: the site he'd work on, if any, and a step on it. */
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

	/** Whether A's row on the Hall's Move page says the move waits for the builders. */
	private static boolean pageSaysWaiting(QaMoveSpotSaves1111eGameTests.Village v) {
		ChoiceMenu menu = VillageHallScreen.forTest(v.player(), v.hall());
		menu.press(HallPages.slot(MovePage.PAGE), v.player());
		String line = Component.translatable(WAITS).getString();
		boolean found = false;
		for (int slot = VillageHallScreen.FIRST_ROW; slot < ChoiceMenu.SIZE; slot++) {
			if (menu.icon(slot).getOrDefault(DataComponents.LORE, ItemLore.EMPTY).lines().stream().anyMatch(c -> c.getString().equals(line))) {
				found = true;
			}
		}
		menu.removed(v.player());
		return found;
	}

	/** Builders on A hold its confirmed move; once their site is done (still listed, finished) the Mover moves A whole. */
	//$ gametest_ticks_batch AREA '200' '"moveLiveSiteResumes"'
	@GameTest(template = AREA, timeoutTicks = 200, batch = "moveLiveSiteResumes")
	public void aMoveHeldByBuildersGoesAheadOnceTheirSiteIsDone(GameTestHelper helper) {
		QaMoveSpotSaves1111eGameTests.Village v = QaMoveSpotSaves1111eGameTests.stage(helper);
		helper.runAfterDelay(2, () -> {
			int[] before = QaMoveSpotSaves1111eGameTests.count(helper);
			helper.assertTrue(before[0] > 0 && before[1] == 0, "A isn't standing alone (setup): " + before[0] + "/" + before[1]);
			QaMoveSpotSaves1111eGameTests.planA(helper, v);
			helper.assertTrue(!pageSaysWaiting(v), "the Move page says A's move waits for builders before any work on it");
			BuildSite repair = repairA(helper, v);
			Optional<MoveSite> held = swings(v, 30);
			int[] now = QaMoveSpotSaves1111eGameTests.count(helper);
			helper.assertTrue(held.isEmpty() && now[0] == before[0] && now[1] == 0, "with builders on A the Mover took " + (before[0] - now[0])
				+ " of its " + before[0] + " blocks down (" + held.map(MoveSite::stage) + ")");
			helper.assertTrue(PendingMoves.get(v.level()).forBuilding(v.a()).isPresent(), "A's move was dropped while it waited");
			helper.assertTrue(pageSaysWaiting(v), "the Move page doesn't say A's move waits for the builders");
			for (int i = 0; i < 40 && !repair.isDone(); i++) {
				repair.finishList(); // the builders finish, stage by stage
			}
			helper.assertTrue(repair.isDone() && BuildSiteManager.get(v.level()).get(repair.id()) != null, "the builders' site isn't done and listed (setup)");
			helper.assertTrue(!pageSaysWaiting(v), "the Move page still says A's move waits once the builders are done");
			Optional<MoveSite> going = swings(v, 1);
			helper.assertTrue(going.isPresent() && going.get().from().equals(v.a()), "once the builders are done A's move doesn't start: " + going);
			swings(v, 5000);
			int[] after = QaMoveSpotSaves1111eGameTests.count(helper);
			helper.assertTrue(after[0] == 0 && after[1] == before[0], "after the move A's old spot has " + after[0] + " blocks and S " + after[1]
				+ " (of " + before[0] + ")");
			helper.assertTrue(PendingMoves.get(v.level()).forBuilding(v.a()).isEmpty() && MoveSites.get(v.level()).forBuilding(v.a()).isEmpty(),
				"A's move is still listed once it's over");
			helper.succeed();
		});
	}

	/** A's move held by builders, the world saved and reloaded: still kept, still held, and it goes ahead once they're done. */
	//$ gametest_ticks_batch AREA '200' '"moveLiveSiteReload"'
	@GameTest(template = AREA, timeoutTicks = 200, batch = "moveLiveSiteReload")
	public void aMoveHeldByBuildersSurvivesASaveAndReload(GameTestHelper helper) {
		QaMoveSpotSaves1111eGameTests.Village v = QaMoveSpotSaves1111eGameTests.stage(helper);
		helper.runAfterDelay(2, () -> {
			int[] before = QaMoveSpotSaves1111eGameTests.count(helper);
			PendingMoves.Move planned = QaMoveSpotSaves1111eGameTests.planA(helper, v);
			BuildSite repair = repairA(helper, v);
			helper.assertTrue(swings(v, 10).isEmpty(), "the Mover started on A with builders on it");
			ServerLevel level = v.level();
			CompoundTag pending = PendingMoves.get(level).save(new CompoundTag(), level.registryAccess());
			CompoundTag moving = MoveSites.get(level).save(new CompoundTag(), level.registryAccess());
			level.getDataStorage().set(PendingMoves.NAME, PendingMoves.load(pending, level.registryAccess()));
			level.getDataStorage().set(MoveSites.NAME, MoveSites.load(moving, level.registryAccess()));
			Optional<PendingMoves.Move> kept = PendingMoves.get(level).forBuilding(v.a());
			helper.assertTrue(kept.isPresent() && kept.get().to().equals(planned.to()), "A's held move after the reload: " + kept);
			helper.assertTrue(swings(v, 10).isEmpty(), "after the reload the Mover started on A with builders still on it");
			int[] now = QaMoveSpotSaves1111eGameTests.count(helper);
			helper.assertTrue(now[0] == before[0] && now[1] == 0, "after the reload " + (before[0] - now[0]) + " of A's blocks came down");
			BuildSiteManager.get(level).remove(repair.id()); // the builders are done
			Optional<MoveSite> going = swings(v, 1);
			helper.assertTrue(going.isPresent() && going.get().from().equals(v.a()), "after the reload and the builders done, A's move doesn't start: " + going);
			helper.succeed();
		});
	}

	/** A's move held by builders can still be called off: its row opens the picker with Call off, and Call off drops it. */
	//$ gametest_ticks_batch AREA '200' '"moveLiveSiteCallOff"'
	@GameTest(template = AREA, timeoutTicks = 200, batch = "moveLiveSiteCallOff")
	public void aMoveHeldByBuildersCanStillBeCalledOff(GameTestHelper helper) {
		QaMoveSpotSaves1111eGameTests.Village v = QaMoveSpotSaves1111eGameTests.stage(helper);
		helper.runAfterDelay(2, () -> {
			int[] before = QaMoveSpotSaves1111eGameTests.count(helper);
			QaMoveSpotSaves1111eGameTests.planA(helper, v);
			repairA(helper, v);
			ChoiceMenu opened = null;
			for (int slot = VillageHallScreen.FIRST_ROW; slot < ChoiceMenu.SIZE && opened == null; slot++) {
				ChoiceMenu menu = VillageHallScreen.forTest(v.player(), v.hall());
				menu.press(HallPages.slot(MovePage.PAGE), v.player());
				if (menu.icon(slot).isEmpty()) {
					break;
				}
				menu.press(slot, v.player());
				if (MovePage.picking(v.player()).map(p -> p.from().equals(v.a())).orElse(false)) {
					opened = menu;
				} else {
					MovePage.stop(v.player());
				}
			}
			helper.assertTrue(opened != null, "A's row doesn't open while its move waits on the builders: it can't be called off");
			helper.assertTrue(!opened.icon(MovePage.CALL_OFF).isEmpty(), "A's picker has no Call off while its move waits on the builders");
			opened.press(MovePage.CALL_OFF, v.player());
			opened.removed(v.player());
			helper.assertTrue(PendingMoves.get(v.level()).forBuilding(v.a()).isEmpty(), "Call off didn't drop A's held move");
			helper.assertTrue(swings(v, 10).isEmpty(), "the Mover has work after A's held move was called off");
			int[] now = QaMoveSpotSaves1111eGameTests.count(helper);
			helper.assertTrue(now[0] == before[0] && now[1] == 0, (before[0] - now[0]) + " of A's blocks came down");
			helper.succeed();
		});
	}
}
