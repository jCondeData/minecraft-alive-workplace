package io.github.jcondedata.aliveworkplace.test;

import static io.github.jcondedata.aliveworkplace.test.QaInnRebuild1114cGameTests.AREA;
import static io.github.jcondedata.aliveworkplace.test.QaInnRebuild1114cGameTests.AWAY;
import static io.github.jcondedata.aliveworkplace.test.QaInnRebuild1114cGameTests.HALL;
import static io.github.jcondedata.aliveworkplace.test.QaInnRebuild1114cGameTests.INN;

import io.github.jcondedata.aliveworkplace.inn.InnCounters;
import io.github.jcondedata.aliveworkplace.registry.ModAttachments;
import io.github.jcondedata.aliveworkplace.registry.ModBlocks;
import io.github.jcondedata.aliveworkplace.registry.ModVillagers;
import io.github.jcondedata.aliveworkplace.work.JobSiteTickets;
import io.github.jcondedata.aliveworkplace.work.Jobs;
import java.lang.reflect.Method;
import java.util.Optional;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.level.saveddata.SavedData;

/**
 * QA (lane D, 2026-11-14), B1228's fix verified from B1213's spec ("a counter worked by someone other than the
 * remembered Innkeeper is no inn; a job-site memory whose ticket another villager holds is stale"): the fix's own tests
 * cover Ina the Innkeeper loading again (past the edge, or by the Hall) after her counter was rebuilt for Sam the
 * Shopkeeper. These cover the rest: the fix must not overcorrect (her counter never broken stays the inn when she loads
 * again, also for an Innkeeper whose ticket the mod never saw her get, as from an older save); the rebuilt counter left
 * free by Sam and taken again by Ina is the inn again; the break count and the inns' list through a save and reload; and
 * the move's residents, who stay by the Hall rather than at Sam's shop.
 */
public class QaInnHeldSite1114dGameTests implements FabricGameTest {
	private static final String TICKETS = "aliveworkplace_job_site_breaks";

	/** Ina loaded again at {@code at} (relative), from the save {@code tag} her chunk wrote. */
	private static Villager inaBack(GameTestHelper helper, CompoundTag tag, BlockPos at) {
		Villager back = QaMoveInnVerify1111cGameTests.back(helper.getLevel(), tag);
		BlockPos abs = helper.absolutePos(at);
		back.teleportTo(abs.getX() + 0.5, abs.getY(), abs.getZ() + 0.5);
		return back;
	}

	/** As a server restart does: the level's break counts and the inns' list written out and read back in. */
	private static void saveAndReload(ServerLevel level) {
		HolderLookup.Provider registries = level.registryAccess();
		try {
			Method get = JobSiteTickets.class.getDeclaredMethod("get", ServerLevel.class);
			Method load = JobSiteTickets.class.getDeclaredMethod("load", CompoundTag.class, HolderLookup.Provider.class);
			get.setAccessible(true);
			load.setAccessible(true);
			CompoundTag tickets = ((JobSiteTickets) get.invoke(null, level)).save(new CompoundTag(), registries);
			level.getDataStorage().set(TICKETS, (SavedData) load.invoke(null, tickets, registries));
		} catch (ReflectiveOperationException e) {
			throw new GameTestAssertException("setup: can't save and load the job sites' break counts: " + e);
		}
		CompoundTag inns = InnCounters.get(level).save(new CompoundTag(), registries);
		level.getDataStorage().set(InnCounters.NAME, InnCounters.load(inns, registries));
	}

	// --- the fix mustn't overcorrect

	/**
	 * Control: Ina's counter is never broken; she goes off into an unloaded chunk and loads again past the village's edge.
	 * Her ticket is still hers, so her counter stays the village's inn and a legend can come to it.
	 */
	//$ gametest_ticks_batch AREA '60' '"qaInnHeldSite1114dKept"'
	@GameTest(template = AREA, timeoutTicks = 60, batch = "qaInnHeldSite1114dKept")
	public void anUnbrokenCounterStaysTheInnWhenItsInnkeeperLoadsPastTheEdge(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		BlockPos inn = helper.absolutePos(INN);
		QaInnRebuild1114cGameTests.village(helper, INN);
		helper.runAfterDelay(2, () -> {
			CompoundTag tag = QaInnRebuild1114cGameTests.inaSeenThenUnloaded(helper);
			helper.runAfterDelay(2, () -> {
				Villager ina = inaBack(helper, tag, AWAY.west());
				helper.assertTrue(ModAttachments.JOB_SITE_HELD.has(ina), "setup: Ina came back without her job-site ticket record");
				helper.assertTrue(QaInnRebuild1114cGameTests.counters(helper).contains(inn),
					"Ina's counter (never broken) stopped being the inn once she loaded again past the village's edge: counters "
						+ QaInnRebuild1114cGameTests.counters(helper));
				helper.assertTrue(QaInnRebuild1114cGameTests.innRolls(helper), "no legend can come to the inn of an Innkeeper who loaded again past the edge");
				helper.assertTrue(ina.getUUID().equals(InnCounters.get(level).keeper(inn)), "Ina isn't remembered at her own counter after she loaded again");
				ina.remove(Entity.RemovalReason.DISCARDED);
				helper.succeed();
			});
		});
	}

	/**
	 * Control: an Innkeeper the mod never saw get her counter (no ticket record, as a villager from a save older than the
	 * break count) loads past the edge, her counter unbroken: nothing says her memory is stale, so it is still the inn.
	 */
	//$ gametest_ticks_batch AREA '60' '"qaInnHeldSite1114dNoRecord"'
	@GameTest(template = AREA, timeoutTicks = 60, batch = "qaInnHeldSite1114dNoRecord")
	public void anInnkeeperWithNoTicketRecordStillMakesHerCounterTheInn(GameTestHelper helper) {
		BlockPos inn = helper.absolutePos(INN);
		QaInnRebuild1114cGameTests.village(helper, INN);
		helper.runAfterDelay(2, () -> {
			CompoundTag tag = QaInnRebuild1114cGameTests.inaSeenThenUnloaded(helper);
			helper.runAfterDelay(2, () -> {
				Villager ina = inaBack(helper, tag, AWAY.west());
				ModAttachments.JOB_SITE_HELD.remove(ina); // (past the edge: too far from the counter for the mod to note it as hers)
				helper.assertTrue(QaInnRebuild1114cGameTests.counters(helper).contains(inn),
					"the counter of an Innkeeper with no ticket record (an older save), never broken, isn't the inn: counters "
						+ QaInnRebuild1114cGameTests.counters(helper));
				helper.assertTrue(QaInnRebuild1114cGameTests.innRolls(helper), "no legend can come to the inn of an Innkeeper from an older save");
				ina.remove(Entity.RemovalReason.DISCARDED);
				helper.succeed();
			});
		});
	}

	// --- the rebuilt counter, its new worker and its old one

	/**
	 * The counter is broken while Ina is unloaded, put back and taken by Sam the Shopkeeper (out past the edge); Ina loads
	 * again by the Hall: a shop, no inn. Then Sam lets it go (his ticket freed) and the counter stands free: still no inn,
	 * though Ina still remembers the spot. Then Ina takes it again (a new ticket): it is the inn again.
	 */
	//$ gametest_ticks_batch AREA '80' '"qaInnHeldSite1114dRetaken"'
	@GameTest(template = AREA, timeoutTicks = 80, batch = "qaInnHeldSite1114dRetaken")
	public void theOldInnkeeperTakingTheRebuiltCounterAgainMakesItTheInnAgain(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		BlockPos inn = helper.absolutePos(INN);
		QaInnRebuild1114cGameTests.village(helper, INN);
		helper.runAfterDelay(2, () -> {
			CompoundTag tag = QaInnRebuild1114cGameTests.inaSeenThenUnloaded(helper);
			helper.destroyBlock(INN);
			QaInnRebuild1114cGameTests.dropItems(helper);
			helper.runAfterDelay(2, () -> {
				helper.setBlock(INN, ModBlocks.SHOP_COUNTER);
				helper.runAfterDelay(2, () -> {
					Villager sam = QaInnRebuild1114cGameTests.worker(helper, INN, AWAY.east(), "Sam", ModVillagers.SHOPKEEPER);
					Villager ina = inaBack(helper, tag, HALL.south());
					helper.assertTrue(level.getPoiManager().getFreeTickets(inn) == 0, "setup: Sam doesn't hold the rebuilt counter");
					helper.assertFalse(QaInnRebuild1114cGameTests.counters(helper).contains(inn),
						"Sam the Shopkeeper's rebuilt counter is the inn with Ina, its old Innkeeper, back by the Hall: counters "
							+ QaInnRebuild1114cGameTests.counters(helper));
					// Sam gives the counter up (dies, or takes another trade): its ticket is free.
					level.getPoiManager().release(inn);
					sam.remove(Entity.RemovalReason.DISCARDED);
					helper.assertTrue(level.getPoiManager().getFreeTickets(inn) == 1, "setup: the counter's ticket wasn't freed");
					helper.assertTrue(ina.getBrain().getMemory(net.minecraft.world.entity.ai.memory.MemoryModuleType.JOB_SITE).isPresent(),
						"setup: Ina forgot the spot (the case needs her stale memory)");
					helper.assertFalse(QaInnRebuild1114cGameTests.counters(helper).contains(inn),
						"a rebuilt counter nobody works is the inn because its old Innkeeper still remembers the spot: counters "
							+ QaInnRebuild1114cGameTests.counters(helper));
					helper.assertFalse(QaInnRebuild1114cGameTests.innRolls(helper), "a legend can come to a rebuilt counter nobody works");
					Jobs.employ(level, ina, inn, ModVillagers.SHOP_COUNTER_POI, ModVillagers.INNKEEPER);
					helper.assertTrue(level.getPoiManager().getFreeTickets(inn) == 0, "setup: Ina didn't take the counter's ticket");
					helper.assertTrue(QaInnRebuild1114cGameTests.counters(helper).contains(inn),
						"Ina took the rebuilt counter again (a new ticket) and it isn't the inn: counters " + QaInnRebuild1114cGameTests.counters(helper));
					helper.assertTrue(QaInnRebuild1114cGameTests.innRolls(helper), "no legend can come to the inn Ina works again");
					helper.assertTrue(ina.getUUID().equals(InnCounters.get(level).keeper(inn)), "Ina isn't remembered at the counter she took again");
					ina.remove(Entity.RemovalReason.DISCARDED);
					helper.succeed();
				});
			});
		});
	}

	/**
	 * The counter rebuilt for Sam the Shopkeeper, who goes off into an unloaded chunk; then the server restarts (the break
	 * counts and the inns' list saved and read back) and Ina loads past the edge: still Sam's shop, no inn, no legend.
	 */
	//$ gametest_ticks_batch AREA '80' '"qaInnHeldSite1114dReload"'
	@GameTest(template = AREA, timeoutTicks = 80, batch = "qaInnHeldSite1114dReload")
	public void theRebuiltShopStaysAShopThroughASaveAndReload(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		BlockPos inn = helper.absolutePos(INN);
		QaInnRebuild1114cGameTests.village(helper, INN);
		helper.runAfterDelay(2, () -> {
			CompoundTag tag = QaInnRebuild1114cGameTests.inaSeenThenUnloaded(helper);
			helper.destroyBlock(INN);
			QaInnRebuild1114cGameTests.dropItems(helper);
			helper.runAfterDelay(2, () -> {
				helper.setBlock(INN, ModBlocks.SHOP_COUNTER);
				helper.runAfterDelay(2, () -> {
					Villager sam = QaInnRebuild1114cGameTests.worker(helper, INN, AWAY.east(), "Sam", ModVillagers.SHOPKEEPER);
					QaMoveInnVerify1111cGameTests.unload(sam);
					saveAndReload(level);
					Villager ina = inaBack(helper, tag, AWAY.west());
					helper.assertTrue(level.getPoiManager().getFreeTickets(inn) == 0, "setup: Sam's ticket was let go");
					helper.assertFalse(QaInnRebuild1114cGameTests.counters(helper).contains(inn),
						"after a save and reload, Sam the Shopkeeper's rebuilt counter is the inn again once Ina (its old Innkeeper) loads: counters "
							+ QaInnRebuild1114cGameTests.counters(helper) + ", remembered keeper " + InnCounters.get(level).keeper(inn));
					helper.assertFalse(QaInnRebuild1114cGameTests.innRolls(helper), "after a save and reload a legend can come to Sam's rebuilt shop as an inn");
					ina.remove(Entity.RemovalReason.DISCARDED);
					helper.succeed();
				});
			});
		});
	}

	/**
	 * What the player sees from a move (B1213's "a move's residents can stay there"): the inn's counter rebuilt for Sam
	 * the Shopkeeper (unloaded), Ina back in the village: a move's residents stay in the bed by the Hall, not in a bed
	 * round Sam's shop.
	 */
	//$ gametest_batch AREA '"qaInnHeldSite1114dMove"'
	@GameTest(template = AREA, batch = "qaInnHeldSite1114dMove")
	public void aMovesResidentsDontStayAtTheRebuiltShop(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		QaMoverResidents1110cGameTests.Setup s = QaInnShopCounter1111cGameTests.shopInn(helper, "qa1114dheld");
		BlockPos inn = helper.absolutePos(QaMoverResidents1110cGameTests.INN);
		Leftovers.after(helper, () -> InnCounters.get(level).forget(inn));
		CompoundTag[] tag = new CompoundTag[1];
		helper.runAfterDelay(2, () -> {
			InnShopCounterB1177GameTests.noOtherInns(level, s.hall());
			Villager ina = InnShopCounterB1177GameTests.counter(helper, ModVillagers.INNKEEPER, "Ina");
			helper.assertTrue(QaInnShopCounter1111cGameTests.innRolls(helper), "setup: Ina's Shop Counter isn't the inn");
			helper.assertTrue(ina.getUUID().equals(InnCounters.get(level).keeper(inn)), "setup: Ina isn't remembered at her counter");
			BlockPos far = helper.absolutePos(new BlockPos(15, 2, 27));
			ina.teleportTo(far.getX() + 0.5, far.getY(), far.getZ() + 0.5);
			tag[0] = QaMoveInnVerify1111cGameTests.unload(ina);
			helper.destroyBlock(QaMoverResidents1110cGameTests.INN);
			QaInnRebuild1114cGameTests.dropItems(helper);
		});
		helper.runAfterDelay(5, () -> helper.setBlock(QaMoverResidents1110cGameTests.INN, ModBlocks.SHOP_COUNTER));
		helper.runAfterDelay(9, () -> {
			Villager sam = InnShopCounterB1177GameTests.counter(helper, ModVillagers.SHOPKEEPER, "Sam");
			QaMoveInnVerify1111cGameTests.unload(sam);
			helper.assertTrue(level.getPoiManager().getFreeTickets(inn) == 0, "setup: Sam's ticket was let go");
			Villager ina = inaBack(helper, tag[0], new BlockPos(20, 2, 12));
			Optional<java.util.UUID> keeper = Optional.ofNullable(InnCounters.get(level).keeper(inn));
			Optional<BlockPos> bed = QaInnShopCounter1111cGameTests.stayBed(helper, s);
			helper.assertFalse(QaInnShopCounter1111cGameTests.atTheInn(helper, bed),
				"a move's residents stay at Sam the Shopkeeper's rebuilt shop (" + bed + ") because Ina, its old Innkeeper, is back in the village; remembered keeper " + keeper);
			helper.assertTrue(QaInnShopCounter1111cGameTests.byTheHall(helper, bed), "with no inn, the move's residents should stay by the Hall, got " + bed);
			helper.assertFalse(QaInnShopCounter1111cGameTests.innRolls(helper), "a legend can come to Sam's rebuilt shop with Ina back in the village");
			ina.remove(Entity.RemovalReason.DISCARDED);
			helper.succeed();
		});
	}
}
