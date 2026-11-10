package io.github.jcondedata.aliveworkplace.test;

import static io.github.jcondedata.aliveworkplace.test.QaMoverResidents1110cGameTests.AREA;
import static io.github.jcondedata.aliveworkplace.test.QaMoverResidents1110cGameTests.BED_A;
import static io.github.jcondedata.aliveworkplace.test.QaMoverResidents1110cGameTests.LECTERN;

import io.github.jcondedata.aliveworkplace.hall.HallBeds;
import io.github.jcondedata.aliveworkplace.move.MoveResidents;
import io.github.jcondedata.aliveworkplace.move.MoveSite;
import io.github.jcondedata.aliveworkplace.move.MoveSites;
import io.github.jcondedata.aliveworkplace.test.QaMoverResidents1110cGameTests.Setup;
import io.github.jcondedata.aliveworkplace.work.Jobs;
import java.util.function.Consumer;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.village.poi.PoiTypes;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.npc.VillagerProfession;
import net.minecraft.world.level.ChunkPos;

/**
 * B1154: a resident too far off to be noted when the Mover starts (over 128 blocks, or not loaded) still gets the moved
 * bed and workstation. The ways it breaks beyond the QA test: a save and reload between the move and their return, their
 * moved bed broken before they're back, coming back while the house is still away, and never coming back (the hold is
 * let go, so the list of waiting claims doesn't grow for ever).
 */
public class MoverResidentsAwayGameTests implements FabricGameTest {
	private static final BlockPos HOME_SPOT = new BlockPos(10, 2, 3);

	/** A villager 200 blocks off the house, living in its bed A (and working its lectern if {@code worker}); cleaned up after. */
	private static void away(GameTestHelper helper, boolean worker, Consumer<Villager> body) {
		ServerLevel level = helper.getLevel();
		BlockPos far = helper.absolutePos(HOME_SPOT).offset(200, 0, 0);
		ChunkPos chunk = new ChunkPos(far);
		level.setChunkForced(chunk.x, chunk.z, true);
		Villager v = EntityType.VILLAGER.spawn(level, far, MobSpawnType.COMMAND);
		try {
			helper.assertTrue(v != null, "setup: couldn't spawn the traveler");
			v.setNoAi(true);
			helper.assertTrue(HallBeds.moveIn(level, v, helper.absolutePos(BED_A)), "setup: the traveler didn't get her bed");
			if (worker) {
				Jobs.employ(level, v, helper.absolutePos(LECTERN), PoiTypes.LIBRARIAN, VillagerProfession.LIBRARIAN);
			}
			body.accept(v);
		} finally {
			if (v != null) {
				v.discard();
			}
			level.setChunkForced(chunk.x, chunk.z, false);
		}
	}

	/** She walks back in and her once-a-second villager check runs. */
	private static void comeBack(GameTestHelper helper, Villager v) {
		BlockPos at = helper.absolutePos(HOME_SPOT);
		v.teleportTo(at.getX() + 0.5, at.getY(), at.getZ() + 0.5);
		v.tickCount = 13;
		MoveResidents.tick(v);
	}

	private static boolean waitingAt(ServerLevel level, BlockPos at) {
		return MoveSites.get(level).forwards().stream().anyMatch(c -> at.equals(c.at()));
	}

	/** Moved while she was away, the world saved and reloaded, then she's back: the moved bed and lectern are hers. */
	//$ gametest_batch AREA '"moverAwayReload"'
	@GameTest(template = AREA, batch = "moverAwayReload")
	public void aFarResidentBackAfterAReloadGetsTheMovedBedAndLectern(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		Setup s = QaMoverResidents1110cGameTests.setup(helper, "away_reload", true);
		helper.runAfterDelay(2, () -> {
			QaMoverResidents1110cGameTests.confirm(helper, s);
			away(helper, true, v -> {
				MoveSite site = QaMoverResidents1110cGameTests.start(level, s);
				helper.assertTrue(site.residents().isEmpty(), "she was noted though 200 blocks off");
				helper.assertTrue(site.claims().size() == 2, "the bed and lectern she has weren't held for her: " + site.claims().size() + " claims");
				QaMoverResidents1110cGameTests.work(level, s, site, x -> false);
				helper.assertTrue(site.stage() == MoveSite.Stage.DONE, "the move ended " + site.stage());
				BlockPos bed = QaMoverResidents1110cGameTests.moved(s, BED_A);
				BlockPos lectern = QaMoverResidents1110cGameTests.moved(s, LECTERN);
				helper.assertTrue(level.getPoiManager().getFreeTickets(bed) == 0, "the moved bed isn't held for her");
				helper.assertTrue(level.getPoiManager().getFreeTickets(lectern) == 0, "the moved lectern isn't held for her");
				CompoundTag saved = MoveSites.get(level).save(new CompoundTag(), level.registryAccess());
				level.getDataStorage().set(MoveSites.NAME, MoveSites.load(saved, level.registryAccess()));
				helper.assertTrue(waitingAt(level, bed) && waitingAt(level, lectern), "after the reload her bed and lectern aren't waiting for her");
				comeBack(helper, v);
				helper.assertTrue(QaMoverResidents1110cGameTests.remembers(level, v, MemoryModuleType.HOME, bed),
					"her bed is " + QaMoverResidents1110cGameTests.memory(v, MemoryModuleType.HOME) + ", not the moved bed " + bed.toShortString());
				helper.assertTrue(QaMoverResidents1110cGameTests.remembers(level, v, MemoryModuleType.JOB_SITE, lectern),
					"her lectern is " + QaMoverResidents1110cGameTests.memory(v, MemoryModuleType.JOB_SITE) + ", not the moved one " + lectern.toShortString());
				helper.assertTrue(v.getVillagerData().getProfession() == VillagerProfession.LIBRARIAN, "she lost her job");
				helper.assertTrue(!waitingAt(level, bed) && !waitingAt(level, lectern), "her claims still wait after she took them");
			});
			helper.succeed();
		});
	}

	/** Her moved bed is broken before she's back: she isn't sent to the empty spot, and nothing is held any more. */
	//$ gametest_batch AREA '"moverAwayBroken"'
	@GameTest(template = AREA, batch = "moverAwayBroken")
	public void aFarResidentWhoseMovedBedWasBrokenIsNotSentThere(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		Setup s = QaMoverResidents1110cGameTests.setup(helper, "away_broken", true);
		helper.runAfterDelay(2, () -> {
			QaMoverResidents1110cGameTests.confirm(helper, s);
			away(helper, false, v -> {
				MoveSite site = QaMoverResidents1110cGameTests.start(level, s);
				QaMoverResidents1110cGameTests.work(level, s, site, x -> false);
				helper.assertTrue(site.stage() == MoveSite.Stage.DONE, "the move ended " + site.stage());
				BlockPos bed = QaMoverResidents1110cGameTests.moved(s, BED_A);
				helper.assertTrue(level.getBlockState(bed).is(net.minecraft.tags.BlockTags.BEDS), "setup: no moved bed at " + bed.toShortString());
				level.destroyBlock(bed, false);
				comeBack(helper, v);
				helper.assertFalse(QaMoverResidents1110cGameTests.remembers(level, v, MemoryModuleType.HOME, bed),
					"she was given the bed that was broken at " + bed.toShortString());
				helper.assertTrue(!waitingAt(level, bed), "the broken bed is still held");
			});
			helper.succeed();
		});
	}

	/** She comes back while the house is away: noted then, she sleeps at the inn, and gets the moved bed when it's up. */
	//$ gametest_batch AREA '"moverAwayMidway"'
	@GameTest(template = AREA, batch = "moverAwayMidway")
	public void aFarResidentBackDuringTheMoveIsNotedThen(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		Setup s = QaMoverResidents1110cGameTests.setup(helper, "away_midway", true);
		helper.runAfterDelay(2, () -> {
			QaMoverResidents1110cGameTests.confirm(helper, s);
			away(helper, false, v -> {
				MoveSite site = QaMoverResidents1110cGameTests.start(level, s);
				helper.assertTrue(site.residents().isEmpty(), "she was noted though 200 blocks off");
				QaMoverResidents1110cGameTests.work(level, s, site, x -> x.stage() == MoveSite.Stage.FOUNDATION);
				helper.assertTrue(site.stage() == MoveSite.Stage.FOUNDATION, "the house didn't come down: " + site.stage());
				comeBack(helper, v);
				helper.assertTrue(site.residents().stream().anyMatch(r -> r.id().equals(v.getUUID())), "back during the move, she wasn't noted");
				helper.assertTrue(site.claims().isEmpty(), "her claim is still waiting for someone: " + site.claims().size());
				BlockPos inn1 = helper.absolutePos(QaMoverResidents1110cGameTests.INN_BED_1);
				BlockPos inn2 = helper.absolutePos(QaMoverResidents1110cGameTests.INN_BED_2);
				helper.assertTrue(QaMoverResidents1110cGameTests.remembers(level, v, MemoryModuleType.HOME, inn1)
					|| QaMoverResidents1110cGameTests.remembers(level, v, MemoryModuleType.HOME, inn2),
					"meanwhile she sleeps at " + QaMoverResidents1110cGameTests.memory(v, MemoryModuleType.HOME) + ", not at the inn");
				QaMoverResidents1110cGameTests.work(level, s, site, x -> false);
				helper.assertTrue(site.stage() == MoveSite.Stage.DONE, "the move ended " + site.stage());
				BlockPos bed = QaMoverResidents1110cGameTests.moved(s, BED_A);
				helper.assertTrue(QaMoverResidents1110cGameTests.remembers(level, v, MemoryModuleType.HOME, bed),
					"her bed is " + QaMoverResidents1110cGameTests.memory(v, MemoryModuleType.HOME) + ", not the moved bed " + bed.toShortString());
			});
			helper.succeed();
		});
	}

	/** She never comes back: after five days the moved bed is let go for someone else and no claim is left waiting. */
	//$ gametest_batch AREA '"moverAwayGone"'
	@GameTest(template = AREA, batch = "moverAwayGone")
	public void aClaimNobodyComesBackForIsLetGo(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		Setup s = QaMoverResidents1110cGameTests.setup(helper, "away_gone", true);
		helper.runAfterDelay(2, () -> {
			QaMoverResidents1110cGameTests.confirm(helper, s);
			away(helper, false, v -> {
				MoveSite site = QaMoverResidents1110cGameTests.start(level, s);
				QaMoverResidents1110cGameTests.work(level, s, site, x -> false);
				helper.assertTrue(site.stage() == MoveSite.Stage.DONE, "the move ended " + site.stage());
				BlockPos bed = QaMoverResidents1110cGameTests.moved(s, BED_A);
				helper.assertTrue(waitingAt(level, bed) && level.getPoiManager().getFreeTickets(bed) == 0, "the moved bed isn't held for her");
				MoveSites.get(level).sweep(level, level.getGameTime() + MoveResidents.KEEP_TICKS / 2);
				helper.assertTrue(waitingAt(level, bed), "her bed was let go before five days were up");
				MoveSites.get(level).sweep(level, level.getGameTime() + MoveResidents.KEEP_TICKS + 1);
				helper.assertTrue(!waitingAt(level, bed), "after five days her bed is still waiting for her");
				helper.assertTrue(level.getPoiManager().getFreeTickets(bed) == 1, "after five days the moved bed is still held");
				helper.assertTrue(MoveSites.get(level).forwards().size() <= MoveSites.MAX_FORWARDS, "too many claims kept");
			});
			helper.succeed();
		});
	}
}
