package io.github.jcondedata.aliveworkplace.test;

import static io.github.jcondedata.aliveworkplace.test.StationsSpecGameTests.listeningPlayer;

import io.github.jcondedata.aliveworkplace.inn.InnCounters;
import io.github.jcondedata.aliveworkplace.move.MoveSite;
import io.github.jcondedata.aliveworkplace.move.MoveSites;
import io.github.jcondedata.aliveworkplace.move.PendingMoves;
import io.github.jcondedata.aliveworkplace.work.Jobs;
import io.github.jcondedata.aliveworkplace.work.Retraining;
import java.util.ArrayList;
import java.util.List;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.village.poi.PoiTypes;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.npc.VillagerProfession;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Rotation;

/**
 * QA (lane C, 2026-11-11): tests for the mutants that survived a mutation check of the B1194, B1201, B1203 and B1205 fixes,
 * written from the bugs' text. A resident in an unloaded chunk while the move ends (the moved bed or lectern held for him)
 * who is back and given another bed, or let go, at the Hall before his first look at home leaves the moved one free
 * (B1194); a resident's death and an Innkeeper's counter forgotten are saved (marked for the next save, B1201, B1205).
 */
public class QaMoveInnMutants1111cGameTests implements FabricGameTest {
	static final String AREA = "aliveworkplace_test:huge_area";

	private static int free(ServerLevel level, BlockPos pos) {
		return level.getPoiManager().getFreeTickets(pos);
	}

	/**
	 * Juniper, the house's Librarian, is in an unloaded chunk when the Mover puts his lectern up at the new spot, so it's
	 * held for him. He comes back and the player lets him go at the Hall (Job ▾, No job) before he has looked at his new
	 * home: he stays jobless, and the moved lectern is free for whoever takes the job, now and after his first look.
	 */
	//$ gametest_batch AREA '"qaMutLetGoBack"'
	@GameTest(template = AREA, batch = "qaMutLetGoBack")
	public void aWorkerLetGoAtTheHallAsHeComesBackLeavesTheHeldLecternFree(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		QaMoveHallBed1111cGameTests.Setup s = QaMoveHallBed1111cGameTests.setup(helper, "mutletgo");
		helper.runAfterDelay(2, () -> {
			PendingMoves.get(level).put(new PendingMoves.Move(s.hall(), s.from(), s.id(), s.to(), Rotation.CLOCKWISE_90, null));
			Villager juniper = helper.spawn(EntityType.VILLAGER, new BlockPos(10, 2, 6));
			juniper.setNoAi(true);
			juniper.setCustomName(Component.literal("Juniper"));
			Jobs.employ(level, juniper, helper.absolutePos(QaMoveHallBed1111cGameTests.LECTERN), PoiTypes.LIBRARIAN, VillagerProfession.LIBRARIAN);
			juniper.setVillagerXp(5);
			MoveSite site = QaMoveHallBed1111cGameTests.work(level, s, x -> x.stage() == MoveSite.Stage.FOUNDATION);
			helper.assertTrue(site.stage() == MoveSite.Stage.FOUNDATION, "the house wasn't taken down: " + site.stage());
			CompoundTag saved = QaMoveInnVerify1111cGameTests.unload(juniper);
			QaMoveHallBed1111cGameTests.work(level, s, x -> false);
			helper.assertTrue(site.stage() == MoveSite.Stage.DONE, "the move ended " + site.stage());
			BlockPos lectern = s.before().at(QaMoveHallBed1111cGameTests.LECTERN.subtract(QaMoveHallBed1111cGameTests.MIN), s.to(), Rotation.CLOCKWISE_90);
			helper.assertTrue(level.getBlockState(lectern).is(Blocks.LECTERN), "the lectern isn't standing at " + lectern.toShortString());
			helper.assertTrue(free(level, lectern) == 0, "setup: the moved lectern isn't held for Juniper, away in an unloaded chunk");
			Villager back = QaMoveInnVerify1111cGameTests.back(level, saved);
			back.setNoAi(true);
			List<String> seen = new ArrayList<>();
			QaMoveHallBed1111cGameTests.letGoAtTheHall(helper, listeningPlayer(helper, seen), back);
			helper.assertTrue(back.getVillagerData().getProfession() == VillagerProfession.NONE, "the Hall's No job didn't let Juniper go: " + back.getVillagerData().getProfession() + "; told " + seen);
			helper.assertTrue(free(level, lectern) == 1, "Juniper was let go at the Hall, yet the moved lectern is still held for him: " + free(level, lectern) + " places free");
			back.tickCount = 13; // his once-a-second look (VillagerMixin -> MoveResidents.tick)
			io.github.jcondedata.aliveworkplace.move.MoveResidents.tick(back);
			String job = back.getBrain().getMemory(MemoryModuleType.JOB_SITE).map(g -> g.pos().toShortString()).orElse("none");
			helper.assertTrue(back.getVillagerData().getProfession() == VillagerProfession.NONE && job.equals("none"),
				"the Mover gave the let-go Juniper his lectern back: he's a " + back.getVillagerData().getProfession() + " with his job site at " + job);
			helper.assertTrue(free(level, lectern) == 1, "after his first look the moved lectern is held for the let-go Juniper: " + free(level, lectern) + " places free");
			helper.succeed();
		});
	}

	/**
	 * Tomas is in an unloaded chunk when the Mover puts his bed up at the new spot, so it's held for him. He comes back and
	 * the player gives him the bed in the open at the Hall before he has looked at his new home: he keeps the player's bed,
	 * and the moved bed is free for anyone.
	 */
	//$ gametest_batch AREA '"qaMutPickBack"'
	@GameTest(template = AREA, batch = "qaMutPickBack")
	public void aBedGivenAtTheHallAsHeComesBackLeavesTheHeldBedFree(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		QaMoveHallBed1111cGameTests.Setup s = QaMoveHallBed1111cGameTests.setup(helper, "mutpick");
		helper.runAfterDelay(2, () -> {
			Villager tomas = QaMoveHallBed1111cGameTests.tomas(helper, s);
			MoveSite site = QaMoveHallBed1111cGameTests.work(level, s, x -> x.stage() == MoveSite.Stage.FOUNDATION);
			helper.assertTrue(site.stage() == MoveSite.Stage.FOUNDATION, "the house wasn't taken down: " + site.stage());
			CompoundTag saved = QaMoveInnVerify1111cGameTests.unload(tomas);
			QaMoveHallBed1111cGameTests.work(level, s, x -> false);
			helper.assertTrue(site.stage() == MoveSite.Stage.DONE, "the move ended " + site.stage());
			BlockPos moved = s.before().at(QaMoveHallBed1111cGameTests.BED.subtract(QaMoveHallBed1111cGameTests.MIN), s.to(), Rotation.CLOCKWISE_90);
			helper.assertTrue(level.getBlockState(moved).getBlock() instanceof BedBlock, "the moved bed isn't standing");
			helper.assertTrue(free(level, moved) == 0, "setup: the moved bed isn't held for Tomas, away in an unloaded chunk");
			Villager back = QaMoveInnVerify1111cGameTests.back(level, saved);
			back.setNoAi(true);
			MoveSites.get(level).setDirty(false); // as just after an autosave
			QaMoveHallBed1111cGameTests.pickTheFreeBed(helper, listeningPlayer(helper, new ArrayList<>()), back);
			BlockPos chosen = helper.absolutePos(QaMoveHallBed1111cGameTests.OTHER_BED);
			helper.assertTrue(chosen.equals(QaMoveHallBed1111cGameTests.home(back)), "the Hall's pick didn't give Tomas the bed in the open");
			helper.assertTrue(free(level, moved) == 1, "Tomas was given another bed at the Hall, yet his moved bed is still held: " + free(level, moved) + " places free");
			helper.assertTrue(MoveSites.get(level).isDirty(), "the Hall's pick changed what's held for the homecoming Tomas but isn't marked to be saved");
			back.tickCount = 13;
			io.github.jcondedata.aliveworkplace.move.MoveResidents.tick(back);
			helper.assertTrue(chosen.equals(QaMoveHallBed1111cGameTests.home(back)),
				"the Mover undid the player's Hall pick on Tomas's first look: he sleeps at " + QaMoveHallBed1111cGameTests.at(QaMoveHallBed1111cGameTests.home(back)));
			helper.assertTrue(free(level, moved) == 1 && free(level, chosen) == 0,
				"after Tomas's first look: moved bed " + free(level, moved) + " places free (want 1), the player's bed " + free(level, chosen) + " (want 0)");
			helper.succeed();
		});
	}

	/** Juniper dies while his lectern is packed: the move's record no longer waiting for him is marked to be saved. */
	//$ gametest_batch AREA '"qaMutDiesSaved"'
	@GameTest(template = AREA, batch = "qaMutDiesSaved")
	public void aResidentWhoDiesMidMoveIsSavedAsGone(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		QaMoveHallBed1111cGameTests.Setup s = QaMoveHallBed1111cGameTests.setup(helper, "mutdies");
		helper.runAfterDelay(2, () -> {
			PendingMoves.get(level).put(new PendingMoves.Move(s.hall(), s.from(), s.id(), s.to(), Rotation.CLOCKWISE_90, null));
			Villager juniper = helper.spawn(EntityType.VILLAGER, new BlockPos(10, 2, 6));
			juniper.setNoAi(true);
			Jobs.employ(level, juniper, helper.absolutePos(QaMoveHallBed1111cGameTests.LECTERN), PoiTypes.LIBRARIAN, VillagerProfession.LIBRARIAN);
			juniper.setVillagerXp(5);
			MoveSite site = QaMoveHallBed1111cGameTests.work(level, s, x -> x.stage() == MoveSite.Stage.FOUNDATION);
			helper.assertTrue(site.residents().stream().anyMatch(r -> r.id().equals(juniper.getUUID())), "setup: Juniper wasn't noted as a resident");
			MoveSites moves = MoveSites.get(level);
			moves.setDirty(false); // as just after an autosave
			juniper.kill();
			juniper.remove(Entity.RemovalReason.KILLED);
			helper.assertTrue(site.residents().stream().noneMatch(r -> r.id().equals(juniper.getUUID())), "the dead Juniper is still a resident of the move");
			helper.assertTrue(moves.isDirty(), "Juniper's death changed the move's residents but isn't marked to be saved: after a restart the move waits for him again");
			helper.succeed();
		});
	}

	/**
	 * The Innkeeper, away past the village's edge, is let go and a Shopkeeper takes his Shop Counter: the counter is
	 * forgotten as the inn's, and that is marked to be saved.
	 */
	//$ gametest_batch AREA '"qaMutForgetSaved"'
	@GameTest(template = AREA, batch = "qaMutForgetSaved")
	public void aCounterForgottenIsSaved(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		var s = QaMoveInnVerify1111cGameTests.innVillage(helper, "mutforget");
		helper.runAfterDelay(2, () -> {
			Villager keeper = QaMoveInnVerify1111cGameTests.seenInnkeeper(helper, s);
			QaMoveInnVerify1111cGameTests.sendAway(helper, keeper);
			BlockPos counter = helper.absolutePos(QaInnShopCounter1111cGameTests.INN);
			InnCounters known = InnCounters.get(level);
			helper.assertTrue(keeper.getUUID().equals(known.keeper(counter)), "setup: the Innkeeper's counter isn't remembered");
			Retraining.unemploy(level, listeningPlayer(helper, new ArrayList<>()), keeper);
			Villager sal = InnShopCounterB1177GameTests.counter(helper, io.github.jcondedata.aliveworkplace.registry.ModVillagers.SHOPKEEPER, "Sal");
			known.setDirty(false); // as just after an autosave
			helper.assertFalse(QaInnShopCounter1111cGameTests.innRolls(helper), "a Shopkeeper works the old inn's Shop Counter now, yet a legend still rolls there");
			helper.assertTrue(known.keeper(counter) == null, "the let-go Innkeeper is still remembered at the counter a Shopkeeper works now");
			helper.assertTrue(known.isDirty(), "the counter was forgotten but that isn't marked to be saved: after a restart it's the inn again");
			keeper.remove(Entity.RemovalReason.DISCARDED);
			sal.remove(Entity.RemovalReason.DISCARDED);
			helper.succeed();
		});
	}

	/** The Innkeeper is killed away past the village's edge: his counter forgotten is marked to be saved. */
	//$ gametest_batch AREA '"qaMutKeeperDiesSaved"'
	@GameTest(template = AREA, batch = "qaMutKeeperDiesSaved")
	public void anInnkeeperKilledIsSavedAsGone(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		var s = QaMoveInnVerify1111cGameTests.innVillage(helper, "mutkdies");
		helper.runAfterDelay(2, () -> {
			Villager keeper = QaMoveInnVerify1111cGameTests.seenInnkeeper(helper, s);
			QaMoveInnVerify1111cGameTests.sendAway(helper, keeper);
			BlockPos counter = helper.absolutePos(QaInnShopCounter1111cGameTests.INN);
			InnCounters known = InnCounters.get(level);
			known.setDirty(false);
			keeper.kill();
			keeper.remove(Entity.RemovalReason.KILLED);
			helper.assertTrue(known.keeper(counter) == null, "the dead Innkeeper is still remembered at his counter");
			helper.assertTrue(known.isDirty(), "the dead Innkeeper's counter was forgotten but that isn't marked to be saved");
			helper.succeed();
		});
	}

	/**
	 * Tomas sleeps in the house and works its lectern. While it's packed the player lets him go at the Hall (Job ▾, No job)
	 * but gives him no other bed: when the house goes up he sleeps in his moved bed (45.4), and only the lectern is free.
	 */
	//$ gametest_batch AREA '"qaMutLetGoKeepsBed"'
	@GameTest(template = AREA, batch = "qaMutLetGoKeepsBed")
	public void aWorkerLetGoAtTheHallMidMoveStillGetsHisMovedBed(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		QaMoveHallBed1111cGameTests.Setup s = QaMoveHallBed1111cGameTests.setup(helper, "mutkeepbed");
		helper.runAfterDelay(2, () -> {
			Villager tomas = QaMoveHallBed1111cGameTests.tomas(helper, s);
			Jobs.employ(level, tomas, helper.absolutePos(QaMoveHallBed1111cGameTests.LECTERN), PoiTypes.LIBRARIAN, VillagerProfession.LIBRARIAN);
			tomas.setVillagerXp(5);
			MoveSite site = QaMoveHallBed1111cGameTests.work(level, s, x -> x.stage() == MoveSite.Stage.FOUNDATION);
			helper.assertTrue(site.stage() == MoveSite.Stage.FOUNDATION, "the house wasn't taken down: " + site.stage());
			QaMoveHallBed1111cGameTests.letGoAtTheHall(helper, listeningPlayer(helper, new ArrayList<>()), tomas);
			helper.assertTrue(tomas.getVillagerData().getProfession() == VillagerProfession.NONE, "the Hall's No job didn't let Tomas go mid-move");
			QaMoveHallBed1111cGameTests.work(level, s, x -> false);
			helper.assertTrue(site.stage() == MoveSite.Stage.DONE, "the move ended " + site.stage());
			BlockPos moved = s.before().at(QaMoveHallBed1111cGameTests.BED.subtract(QaMoveHallBed1111cGameTests.MIN), s.to(), Rotation.CLOCKWISE_90);
			BlockPos lectern = s.before().at(QaMoveHallBed1111cGameTests.LECTERN.subtract(QaMoveHallBed1111cGameTests.MIN), s.to(), Rotation.CLOCKWISE_90);
			helper.assertTrue(moved.equals(QaMoveHallBed1111cGameTests.home(tomas)) && free(level, moved) == 0,
				"letting Tomas go at the Hall cost him his moved bed: he sleeps at " + QaMoveHallBed1111cGameTests.at(QaMoveHallBed1111cGameTests.home(tomas))
					+ ", the moved bed " + moved.toShortString() + " has " + free(level, moved) + " places free");
			helper.assertTrue(free(level, lectern) == 1, "the moved lectern is held for the let-go Tomas: " + free(level, lectern) + " places free");
			helper.assertTrue(free(level, helper.absolutePos(QaMoveHallBed1111cGameTests.INN_BED)) == 1, "the inn's bed is still held after Tomas went home");
			helper.succeed();
		});
	}

	/** The player gives Tomas another bed at the Hall while his house is packed: the move's record of it is marked to be saved. */
	//$ gametest_batch AREA '"qaMutPickSaved"'
	@GameTest(template = AREA, batch = "qaMutPickSaved")
	public void aHallPickMidMoveIsSaved(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		QaMoveHallBed1111cGameTests.Setup s = QaMoveHallBed1111cGameTests.setup(helper, "mutpicksaved");
		helper.runAfterDelay(2, () -> {
			Villager tomas = QaMoveHallBed1111cGameTests.tomas(helper, s);
			MoveSite site = QaMoveHallBed1111cGameTests.work(level, s, x -> x.stage() == MoveSite.Stage.FOUNDATION);
			helper.assertTrue(site.stage() == MoveSite.Stage.FOUNDATION, "the house wasn't taken down: " + site.stage());
			MoveSites moves = MoveSites.get(level);
			moves.setDirty(false); // as just after an autosave
			QaMoveHallBed1111cGameTests.pickTheFreeBed(helper, listeningPlayer(helper, new ArrayList<>()), tomas);
			helper.assertTrue(helper.absolutePos(QaMoveHallBed1111cGameTests.OTHER_BED).equals(QaMoveHallBed1111cGameTests.home(tomas)), "the Hall's pick didn't give Tomas the bed in the open");
			helper.assertTrue(moves.isDirty(), "the Hall's pick changed what the move holds for Tomas but isn't marked to be saved: after a restart the Mover pulls him back");
			helper.succeed();
		});
	}

	/**
	 * Tomas is in an unloaded chunk when his moved bed goes up (held for him); he comes back and is killed before he has
	 * looked at his new home: the moved bed is free at once, he is no longer waited for, and that is marked to be saved.
	 */
	//$ gametest_batch AREA '"qaMutHomecomingDies"'
	@GameTest(template = AREA, batch = "qaMutHomecomingDies")
	public void aHomecomingResidentKilledLetsGoOfHisMovedBed(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		QaMoveHallBed1111cGameTests.Setup s = QaMoveHallBed1111cGameTests.setup(helper, "muthomedies");
		helper.runAfterDelay(2, () -> {
			Villager tomas = QaMoveHallBed1111cGameTests.tomas(helper, s);
			MoveSite site = QaMoveHallBed1111cGameTests.work(level, s, x -> x.stage() == MoveSite.Stage.FOUNDATION);
			helper.assertTrue(site.stage() == MoveSite.Stage.FOUNDATION, "the house wasn't taken down: " + site.stage());
			CompoundTag saved = QaMoveInnVerify1111cGameTests.unload(tomas);
			QaMoveHallBed1111cGameTests.work(level, s, x -> false);
			helper.assertTrue(site.stage() == MoveSite.Stage.DONE, "the move ended " + site.stage());
			BlockPos moved = s.before().at(QaMoveHallBed1111cGameTests.BED.subtract(QaMoveHallBed1111cGameTests.MIN), s.to(), Rotation.CLOCKWISE_90);
			MoveSites moves = MoveSites.get(level);
			helper.assertTrue(free(level, moved) == 0 && moves.homecomings().stream().anyMatch(r -> r.id().equals(tomas.getUUID())),
				"setup: the moved bed isn't held for Tomas, away in an unloaded chunk");
			Villager back = QaMoveInnVerify1111cGameTests.back(level, saved);
			back.setNoAi(true);
			moves.setDirty(false); // as just after an autosave
			back.kill();
			back.remove(Entity.RemovalReason.KILLED);
			helper.assertTrue(free(level, moved) == 1, "the moved bed is held for the dead Tomas: " + free(level, moved) + " places free");
			helper.assertTrue(moves.homecomings().stream().noneMatch(r -> r.id().equals(tomas.getUUID())), "the dead Tomas is still waited for");
			helper.assertTrue(moves.isDirty(), "Tomas's death let go of his moved bed but isn't marked to be saved: after a restart he's waited for again");
			helper.succeed();
		});
	}

	/**
	 * Tomas is in an unloaded chunk when his moved bed goes up and never comes back: once five days are past, the village's
	 * own goings-on (another villager's once-a-second look) let the bed go, with nothing calling the sweep by hand (B1201).
	 * The game clock is moved ahead for one look and put back at once, within this test's own batch.
	 */
	//$ gametest_batch AREA '"qaMutSweepByTick"'
	@GameTest(template = AREA, batch = "qaMutSweepByTick")
	public void anUnloadedResidentsBedIsLetGoByTheVillagesOwnTicks(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		QaMoveHallBed1111cGameTests.Setup s = QaMoveHallBed1111cGameTests.setup(helper, "mutsweep");
		helper.runAfterDelay(2, () -> {
			Villager tomas = QaMoveHallBed1111cGameTests.tomas(helper, s);
			MoveSite site = QaMoveHallBed1111cGameTests.work(level, s, x -> x.stage() == MoveSite.Stage.FOUNDATION);
			helper.assertTrue(site.stage() == MoveSite.Stage.FOUNDATION, "the house wasn't taken down: " + site.stage());
			QaMoveInnVerify1111cGameTests.unload(tomas);
			QaMoveHallBed1111cGameTests.work(level, s, x -> false);
			helper.assertTrue(site.stage() == MoveSite.Stage.DONE, "the move ended " + site.stage());
			BlockPos moved = s.before().at(QaMoveHallBed1111cGameTests.BED.subtract(QaMoveHallBed1111cGameTests.MIN), s.to(), Rotation.CLOCKWISE_90);
			helper.assertTrue(free(level, moved) == 0, "setup: the moved bed isn't held for Tomas, away in an unloaded chunk");
			Villager rosa = helper.spawn(EntityType.VILLAGER, new BlockPos(20, 2, 20));
			rosa.setNoAi(true);
			net.minecraft.world.level.storage.ServerLevelData data = (net.minecraft.world.level.storage.ServerLevelData) level.getLevelData();
			long now = level.getGameTime();
			int freeAfter;
			boolean waited;
			boolean dirty;
			try {
				data.setGameTime(now + io.github.jcondedata.aliveworkplace.move.MoveResidents.KEEP_TICKS + 1201);
				MoveSites.get(level).setDirty(false);
				rosa.tickCount = 13; // Rosa's once-a-second look (VillagerMixin -> MoveResidents.tick)
				io.github.jcondedata.aliveworkplace.move.MoveResidents.tick(rosa);
				freeAfter = free(level, moved);
				dirty = MoveSites.get(level).isDirty();
				waited = MoveSites.get(level).homecomings().stream().anyMatch(r -> r.id().equals(tomas.getUUID()));
			} finally {
				data.setGameTime(now);
				MoveSites.get(level).sweep(level, now - 1200); // the next sweep due as it was, a minute from now
			}
			helper.assertTrue(freeAfter == 1 && !waited, "five days after the move, with villagers about, Tomas's moved bed is still held for him: "
				+ freeAfter + " places free, still waited for " + waited);
			helper.assertTrue(dirty, "the sweep let go of Tomas's moved bed but that isn't marked to be saved");
			rosa.remove(Entity.RemovalReason.DISCARDED);
			helper.succeed();
		});
	}

	/** An Innkeeper is seen working his Shop Counter: the counter remembered is marked to be saved (so it's the inn after a restart while he's away). */
	//$ gametest_batch AREA '"qaMutRememberSaved"'
	@GameTest(template = AREA, batch = "qaMutRememberSaved")
	public void aCounterLearnedIsSaved(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		var s = QaMoveInnVerify1111cGameTests.innVillage(helper, "mutlearn");
		helper.runAfterDelay(2, () -> {
			BlockPos counter = helper.absolutePos(QaInnShopCounter1111cGameTests.INN);
			InnCounters known = InnCounters.get(level);
			known.forget(counter);
			known.setDirty(false); // as just after an autosave
			Villager keeper = QaMoveInnVerify1111cGameTests.seenInnkeeper(helper, s);
			helper.assertTrue(keeper.getUUID().equals(known.keeper(counter)), "the Innkeeper working his counter isn't remembered there");
			helper.assertTrue(known.isDirty(), "the counter learned isn't marked to be saved: after a restart with the Innkeeper away it's no inn");
			keeper.remove(Entity.RemovalReason.DISCARDED);
			helper.succeed();
		});
	}
}
