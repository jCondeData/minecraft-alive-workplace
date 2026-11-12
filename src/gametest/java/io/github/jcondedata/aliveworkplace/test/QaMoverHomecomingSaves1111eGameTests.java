package io.github.jcondedata.aliveworkplace.test;

import static io.github.jcondedata.aliveworkplace.test.QaMoveHallBed1111cGameTests.AREA;
import static io.github.jcondedata.aliveworkplace.test.QaMoveHallBed1111cGameTests.BED;
import static io.github.jcondedata.aliveworkplace.test.QaMoveHallBed1111cGameTests.INN_BED;
import static io.github.jcondedata.aliveworkplace.test.QaMoveHallBed1111cGameTests.MIN;
import static io.github.jcondedata.aliveworkplace.test.QaMoveHallBed1111cGameTests.at;
import static io.github.jcondedata.aliveworkplace.test.QaMoveHallBed1111cGameTests.home;
import static io.github.jcondedata.aliveworkplace.test.QaMoveHallBed1111cGameTests.setup;
import static io.github.jcondedata.aliveworkplace.test.QaMoveHallBed1111cGameTests.tomas;
import static io.github.jcondedata.aliveworkplace.test.QaMoveHallBed1111cGameTests.work;

import io.github.jcondedata.aliveworkplace.move.MoveResidents;
import io.github.jcondedata.aliveworkplace.move.MoveSite;
import io.github.jcondedata.aliveworkplace.move.MoveSites;
import java.util.UUID;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.level.block.Rotation;

/**
 * QA (lane E, 2026-11-11): 45.4's homecomings and B1201's five-day wait across a server restart, from the spec (the wiki's
 * moving-buildings page: "a resident who isn't loaded when their bed goes up gets it when they're back; one not back
 * within five days has it let go for someone else"). Tomas sleeps in the house; while it's packed his chunk unloads (he's
 * saved and taken out of the world, as an unload does), so when the Mover puts his bed up he isn't there to take it.
 * The move records are saved and loaded back as a restart does, and Tomas is loaded back from his own save.
 */
public class QaMoverHomecomingSaves1111eGameTests implements FabricGameTest {

	/** Saves the moves' records and loads them back, as a server restart does; the loaded ones replace the live ones. */
	private static void reload(ServerLevel level) {
		CompoundTag sites = MoveSites.get(level).save(new CompoundTag(), level.registryAccess());
		level.getDataStorage().set(MoveSites.NAME, MoveSites.load(sites, level.registryAccess()));
	}

	/** Tomas's chunk unloads: he's saved as the chunk saves him and taken out of the world. */
	private static CompoundTag unload(Villager tomas) {
		CompoundTag saved = new CompoundTag();
		tomas.save(saved);
		tomas.remove(Entity.RemovalReason.UNLOADED_TO_CHUNK);
		return saved;
	}

	/** Tomas's chunk loads again: he's read back from his save and his once-a-second villager tick runs. */
	private static Villager loadBack(GameTestHelper helper, CompoundTag saved) {
		ServerLevel level = helper.getLevel();
		Entity e = EntityType.loadEntityRecursive(saved, level, x -> x);
		helper.assertTrue(e instanceof Villager, "setup: Tomas didn't load back from his save: " + e);
		Villager tomas = (Villager) e;
		BlockPos spot = helper.absolutePos(new BlockPos(10, 2, 4));
		tomas.moveTo(spot.getX() + 0.5, spot.getY(), spot.getZ() + 0.5);
		helper.assertTrue(level.addFreshEntity(tomas), "setup: Tomas couldn't be put back in the world");
		tomas.tickCount = 13;
		MoveResidents.tick(tomas);
		return tomas;
	}

	private static boolean waitedFor(ServerLevel level, UUID id) {
		return MoveSites.get(level).homecomings().stream().anyMatch(r -> r.id().equals(id));
	}

	private static int free(ServerLevel level, BlockPos pos) {
		return level.getPoiManager().getFreeTickets(pos);
	}

	/** The moved bed, standing: where the house's bed is now. */
	private static BlockPos movedBed(GameTestHelper helper, QaMoveHallBed1111cGameTests.Setup s) {
		BlockPos moved = s.before().at(BED.subtract(MIN), s.to(), Rotation.CLOCKWISE_90);
		helper.assertTrue(helper.getLevel().getBlockState(moved).getBlock() instanceof BedBlock, "the moved bed isn't standing at " + at(moved));
		return moved;
	}

	/**
	 * Tomas is unloaded when his bed goes up. The server restarts; half the wait later his bed is still held for him, and
	 * when his chunk loads he sleeps in the moved bed, the inn's bed is free again, and after another restart nobody waits
	 * for him any more.
	 */
	//$ gametest_batch AREA '"qaMoverHomecomingSaved"'
	@GameTest(template = AREA, batch = "qaMoverHomecomingSaved")
	public void aHomecomingSurvivesARestartAndHeGetsTheMovedBed(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		QaMoveHallBed1111cGameTests.Setup s = setup(helper, "homesave");
		helper.runAfterDelay(2, () -> {
			Villager tomas = tomas(helper, s);
			UUID id = tomas.getUUID();
			MoveSite site = work(level, s, x -> x.stage() == MoveSite.Stage.FOUNDATION);
			helper.assertTrue(helper.absolutePos(INN_BED).equals(home(tomas)), "with his house packed Tomas sleeps at " + at(home(tomas)) + ", not the inn");
			CompoundTag saved = unload(tomas);
			work(level, s, x -> false);
			helper.assertTrue(site.stage() == MoveSite.Stage.DONE, "the move ended " + site.stage());
			BlockPos moved = movedBed(helper, s);
			helper.assertTrue(waitedFor(level, id), "Tomas, unloaded when his bed went up, isn't waited for");
			helper.assertTrue(free(level, moved) == 0, "the moved bed isn't held for the unloaded Tomas: " + free(level, moved) + " places free");
			long now = level.getGameTime();
			reload(level);
			helper.assertTrue(waitedFor(level, id), "after a restart nobody waits for Tomas");
			MoveSites.get(level).sweep(level, now + MoveResidents.KEEP_TICKS / 2);
			helper.assertTrue(waitedFor(level, id) && free(level, moved) == 0,
				"after a restart, two and a half days on, Tomas's moved bed was let go: waited for " + waitedFor(level, id) + ", " + free(level, moved) + " places free");
			Villager back = loadBack(helper, saved);
			helper.assertTrue(moved.equals(home(back)), "back after a restart Tomas sleeps at " + at(home(back)) + ", not the moved bed " + at(moved));
			helper.assertTrue(free(level, moved) == 0, "the moved bed Tomas sleeps in is free to anyone");
			helper.assertTrue(free(level, helper.absolutePos(INN_BED)) == 1, "the inn's bed wasn't given back when Tomas went home");
			helper.assertTrue(!waitedFor(level, id), "Tomas is home but still waited for");
			reload(level);
			helper.assertTrue(!waitedFor(level, id), "after another restart Tomas, home already, is waited for again");
			helper.succeed();
		});
	}

	/**
	 * Tomas never comes back: after a restart, five days past the move, his moved bed is let go for someone else (B1201),
	 * and another restart doesn't hold it again.
	 */
	//$ gametest_batch AREA '"qaMoverHomecomingGivenUp"'
	@GameTest(template = AREA, batch = "qaMoverHomecomingGivenUp")
	public void aHomecomingNobodyComesBackForIsLetGoAfterARestart(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		QaMoveHallBed1111cGameTests.Setup s = setup(helper, "homegiveup");
		helper.runAfterDelay(2, () -> {
			Villager tomas = tomas(helper, s);
			UUID id = tomas.getUUID();
			MoveSite site = work(level, s, x -> x.stage() == MoveSite.Stage.FOUNDATION);
			unload(tomas);
			work(level, s, x -> false);
			helper.assertTrue(site.stage() == MoveSite.Stage.DONE, "the move ended " + site.stage());
			BlockPos moved = movedBed(helper, s);
			helper.assertTrue(free(level, moved) == 0, "the moved bed isn't held for the unloaded Tomas: " + free(level, moved) + " places free");
			long now = level.getGameTime();
			reload(level);
			MoveSites.get(level).sweep(level, now + MoveResidents.KEEP_TICKS + 1200);
			helper.assertTrue(free(level, moved) == 1,
				"after a restart, five days past the move, the bed is still held for the absent Tomas: " + free(level, moved) + " places free; waited for " + waitedFor(level, id));
			helper.assertTrue(!waitedFor(level, id), "five days on Tomas is still waited for");
			reload(level);
			MoveSites.get(level).sweep(level, now + MoveResidents.KEEP_TICKS + 2400);
			helper.assertTrue(free(level, moved) == 1 && !waitedFor(level, id),
				"another restart held the let-go bed again: " + free(level, moved) + " places free; waited for " + waitedFor(level, id));
			helper.succeed();
		});
	}

	/**
	 * The server restarts while the house is packed and Tomas is unloaded: the move goes on from the save, his bed goes
	 * up held for him, and when he's back he sleeps in it.
	 */
	//$ gametest_batch AREA '"qaMoverHomecomingMidMove"'
	@GameTest(template = AREA, batch = "qaMoverHomecomingMidMove")
	public void aRestartMidMoveWithTheResidentUnloadedStillGivesHimTheMovedBed(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		QaMoveHallBed1111cGameTests.Setup s = setup(helper, "homemid");
		helper.runAfterDelay(2, () -> {
			Villager tomas = tomas(helper, s);
			UUID id = tomas.getUUID();
			work(level, s, x -> x.stage() == MoveSite.Stage.FOUNDATION);
			CompoundTag saved = unload(tomas);
			reload(level);
			MoveSite site = MoveSites.get(level).forBuilding(s.from()).orElse(null);
			helper.assertTrue(site != null, "the move didn't survive the restart");
			helper.assertTrue(site.residents().stream().anyMatch(r -> r.id().equals(id)), "after the restart Tomas isn't one of the move's residents");
			site = work(level, s, x -> false);
			helper.assertTrue(site.stage() == MoveSite.Stage.DONE, "the move ended " + site.stage());
			BlockPos moved = movedBed(helper, s);
			helper.assertTrue(free(level, moved) == 0 && waitedFor(level, id),
				"the bed put up after the restart isn't held for the unloaded Tomas: " + free(level, moved) + " places free; waited for " + waitedFor(level, id));
			Villager back = loadBack(helper, saved);
			helper.assertTrue(moved.equals(home(back)), "back home Tomas sleeps at " + at(home(back)) + ", not the moved bed " + at(moved));
			helper.assertTrue(free(level, helper.absolutePos(INN_BED)) == 1, "the inn's bed wasn't given back");
			helper.succeed();
		});
	}

	/**
	 * B1194 across a restart: the server restarts while the house is packed; then the player gives Tomas the bed in the
	 * open from the Hall. When the Mover puts the house up, Tomas keeps the bed the player chose and the moved bed is free
	 * for someone else.
	 */
	//$ gametest_batch AREA '"qaMoverHomecomingHallPick"'
	@GameTest(template = AREA, batch = "qaMoverHomecomingHallPick")
	public void aBedGivenAtTheHallAfterARestartMidMoveIsKept(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		QaMoveHallBed1111cGameTests.Setup s = setup(helper, "homepick");
		helper.runAfterDelay(2, () -> {
			Villager tomas = tomas(helper, s);
			work(level, s, x -> x.stage() == MoveSite.Stage.FOUNDATION);
			helper.assertTrue(helper.absolutePos(INN_BED).equals(home(tomas)), "with his house packed Tomas sleeps at " + at(home(tomas)) + ", not the inn");
			reload(level);
			QaMoveHallBed1111cGameTests.pickTheFreeBed(helper, StationsSpecGameTests.listeningPlayer(helper, new java.util.ArrayList<>()), tomas);
			BlockPos chosen = helper.absolutePos(QaMoveHallBed1111cGameTests.OTHER_BED);
			helper.assertTrue(chosen.equals(home(tomas)), "after a restart the Hall's pick didn't give Tomas the bed in the open: he sleeps at " + at(home(tomas)));
			MoveSite site = work(level, s, x -> false);
			helper.assertTrue(site.stage() == MoveSite.Stage.DONE, "the move ended " + site.stage());
			BlockPos moved = movedBed(helper, s);
			helper.assertTrue(chosen.equals(home(tomas)),
				"after a restart the Mover undid the player's Hall pick: Tomas now sleeps at " + at(home(tomas)) + ", not the bed in the open at " + at(chosen));
			helper.assertTrue(free(level, chosen) == 0, "the bed the player gave Tomas is free for anyone");
			helper.assertTrue(free(level, moved) == 1, "the moved bed nobody sleeps in is held: " + free(level, moved) + " places free");
			helper.assertTrue(free(level, helper.absolutePos(INN_BED)) == 1, "the inn's bed wasn't let go");
			helper.assertTrue(!waitedFor(level, tomas.getUUID()), "Tomas, in the bed the player chose, is waited for");
			helper.succeed();
		});
	}
}
