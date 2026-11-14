package io.github.jcondedata.aliveworkplace.test;

import io.github.jcondedata.aliveworkplace.hall.VillageHalls;
import io.github.jcondedata.aliveworkplace.inn.InnCounters;
import io.github.jcondedata.aliveworkplace.inn.Innkeepers;
import io.github.jcondedata.aliveworkplace.legend.LegendGuests;
import io.github.jcondedata.aliveworkplace.registry.ModBlocks;
import io.github.jcondedata.aliveworkplace.registry.ModVillagers;
import io.github.jcondedata.aliveworkplace.shop.ShopCounterBlock;
import io.github.jcondedata.aliveworkplace.work.Jobs;
import io.github.jcondedata.aliveworkplace.work.Retraining;
import java.util.List;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

/**
 * QA (lane C, 2026-11-14), B1213/B1214/B1215 verified from their spec: a Shop Counter inside the village that an
 * Innkeeper works is its inn wherever he stands (B1214), and a counter that was broken, or that someone other than the
 * remembered Innkeeper works, is no inn (B1213, B1215). The fix's own tests cover a player breaking the counter (or
 * /setblock to air) and Sam taking the new one while out past the edge, and an Innkeeper never seen in the village;
 * these cover the rest: other ways the block goes (an explosion, replaced by another block), a block that only turns,
 * the never-seen Innkeeper across a save and retrained, two counters worked from past the edge, a baby, the edge, and
 * the old Innkeeper coming back after the counter was rebuilt for someone else.
 */
public class QaInnRebuild1114cGameTests implements FabricGameTest {
	static final String AREA = QaInnCounters1111dGameTests.AREA;
	static final BlockPos HALL = QaInnCounters1111dGameTests.HALL; // (15, 2, 2)
	static final BlockPos INN = QaInnCounters1111dGameTests.INN; // (25, 2, 3): 10.05 blocks from the Hall
	static final BlockPos SHOP = new BlockPos(5, 2, 3); // 10.05 blocks from the Hall, the other side
	static final BlockPos AWAY = new BlockPos(15, 2, 27); // 25 blocks from the Hall: past the edge (12)

	// --- helpers

	/** A village of radius 12 with a Shop Counter at {@code counters} (their POIs a few ticks later), remembered counters forgotten after. */
	static void village(GameTestHelper helper, BlockPos... counters) {
		Leftovers.clear(helper);
		QaInnCounters1111dGameTests.radius(helper, 12);
		for (BlockPos c : counters) {
			helper.setBlock(c, ModBlocks.SHOP_COUNTER);
			BlockPos abs = helper.absolutePos(c);
			Leftovers.after(helper, () -> InnCounters.get(helper.getLevel()).forget(abs));
		}
	}

	static BlockPos hall(GameTestHelper helper) {
		return helper.absolutePos(HALL);
	}

	static List<BlockPos> counters(GameTestHelper helper) {
		return Innkeepers.counters(helper.getLevel(), hall(helper), VillageHalls.RADIUS);
	}

	static boolean innRolls(GameTestHelper helper) {
		return LegendGuests.rollsOn(helper.getLevel(), hall(helper), null, "inn", 1);
	}

	/** A villager of {@code job} whose job site is the Shop Counter at {@code counter}, standing at {@code at}. */
	static Villager worker(GameTestHelper helper, BlockPos counter, BlockPos at, String name, net.minecraft.world.entity.npc.VillagerProfession job) {
		Villager v = QaMoverResidents1110cGameTests.villager(helper, at, name);
		Jobs.employ(helper.getLevel(), v, helper.absolutePos(counter), ModVillagers.SHOP_COUNTER_POI, job);
		return v;
	}

	/** Ina the Innkeeper seen working the inn (standing by it), then far off in a chunk that unloads (her save returned). */
	static CompoundTag inaSeenThenUnloaded(GameTestHelper helper) {
		InnShopCounterB1177GameTests.noOtherInns(helper.getLevel(), hall(helper));
		Villager ina = QaInnCounters1111dGameTests.innkeeper(helper, INN, INN.south());
		helper.assertTrue(counters(helper).contains(helper.absolutePos(INN)), "setup: Ina's Shop Counter isn't the inn with her by it");
		helper.assertTrue(ina.getUUID().equals(InnCounters.get(helper.getLevel()).keeper(helper.absolutePos(INN))), "setup: Ina isn't remembered at her counter");
		BlockPos away = helper.absolutePos(AWAY);
		ina.teleportTo(away.getX() + 0.5, away.getY(), away.getZ() + 0.5);
		return QaMoveInnVerify1111cGameTests.unload(ina);
	}

	static void dropItems(GameTestHelper helper) {
		helper.getLevel().getEntitiesOfClass(ItemEntity.class, helper.getBounds().inflate(8)).forEach(Entity::discard);
	}

	/**
	 * The counter was put back after {@code removal} and Sam the Shopkeeper took it, then Sam too went off into an unloaded
	 * chunk (his ticket held): with nobody loaded to ask, the counter must not be taken as the old Innkeeper's inn.
	 */
	private static void rebuiltForSamWhoUnloads(GameTestHelper helper, Runnable removal, String how) {
		ServerLevel level = helper.getLevel();
		BlockPos inn = helper.absolutePos(INN);
		village(helper, INN);
		helper.runAfterDelay(2, () -> {
			inaSeenThenUnloaded(helper);
			removal.run();
			helper.assertFalse(level.getBlockState(inn).is(ModBlocks.SHOP_COUNTER), "setup: the counter is still there after " + how);
			dropItems(helper);
			helper.runAfterDelay(2, () -> {
				helper.setBlock(INN, ModBlocks.SHOP_COUNTER);
				helper.runAfterDelay(2, () -> {
					Villager sam = worker(helper, INN, AWAY.east(), "Sam", ModVillagers.SHOPKEEPER);
					// (no village asks about its inn while Sam is loaded: a look then would learn the counter is his shop)
					QaMoveInnVerify1111cGameTests.unload(sam);
					helper.assertTrue(level.getPoiManager().getFreeTickets(inn) == 0, "setup: Sam's ticket was let go as he unloaded");
					helper.assertFalse(counters(helper).contains(inn),
						"a Shop Counter " + how + " and rebuilt for Sam the Shopkeeper is the inn again once Sam and the old Innkeeper are both unloaded: counters "
							+ counters(helper) + ", remembered keeper " + InnCounters.get(level).keeper(inn));
					helper.assertFalse(innRolls(helper), "a legend can come to a shop " + how + " and rebuilt, as if it were the old Innkeeper's inn");
					helper.succeed();
				});
			});
		});
	}

	// --- B1213, B1215: other ways the block goes

	/** The inn's counter is blown up by an explosion while Ina is unloaded, then rebuilt for Sam. */
	//$ gametest_ticks_batch AREA '80' '"qaInnRebuild1114cBlast"'
	@GameTest(template = AREA, timeoutTicks = 80, batch = "qaInnRebuild1114cBlast")
	public void aCounterBlownUpAndRebuiltForAShopkeeperIsNoInn(GameTestHelper helper) {
		rebuiltForSamWhoUnloads(helper, () -> {
			BlockPos at = helper.absolutePos(INN);
			helper.getLevel().explode(null, at.getX() + 0.5, at.getY() + 0.5, at.getZ() + 0.5, 4.0f, Level.ExplosionInteraction.TNT);
		}, "blown up");
	}

	/** The inn's counter is replaced by another block (a builder or /setblock stone) while Ina is unloaded, then rebuilt for Sam. */
	//$ gametest_ticks_batch AREA '80' '"qaInnRebuild1114cReplaced"'
	@GameTest(template = AREA, timeoutTicks = 80, batch = "qaInnRebuild1114cReplaced")
	public void aCounterReplacedByAnotherBlockAndRebuiltForAShopkeeperIsNoInn(GameTestHelper helper) {
		rebuiltForSamWhoUnloads(helper, () -> helper.setBlock(INN, Blocks.STONE), "replaced by stone");
	}

	/** The counter only turns (same block, another facing) while Ina is unloaded: it is still her inn. */
	//$ gametest_ticks_batch AREA '60' '"qaInnRebuild1114cTurned"'
	@GameTest(template = AREA, timeoutTicks = 60, batch = "qaInnRebuild1114cTurned")
	public void aCounterThatOnlyTurnsStaysTheInnOfItsUnloadedInnkeeper(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		BlockPos inn = helper.absolutePos(INN);
		village(helper, INN);
		helper.runAfterDelay(2, () -> {
			CompoundTag ina = inaSeenThenUnloaded(helper);
			BlockState was = level.getBlockState(inn);
			Direction to = was.getValue(ShopCounterBlock.FACING) == Direction.EAST ? Direction.WEST : Direction.EAST;
			level.setBlock(inn, was.setValue(ShopCounterBlock.FACING, to), 3);
			helper.assertTrue(level.getBlockState(inn).getValue(ShopCounterBlock.FACING) == to, "setup: the counter didn't turn");
			helper.assertTrue(ina.getUUID("UUID").equals(InnCounters.get(level).keeper(inn)), "a Shop Counter that only turned forgot its Innkeeper");
			helper.assertTrue(counters(helper).contains(inn), "a Shop Counter that only turned is no inn while its Innkeeper is unloaded: counters " + counters(helper));
			helper.assertTrue(innRolls(helper), "no legend can come to an inn whose counter only turned");
			helper.succeed();
		});
	}

	// --- B1214: an Innkeeper past the edge since he was hired

	/**
	 * Ina, hired past the village's edge and never seen in it, makes her counter the inn; it stays the inn across a save
	 * while she is unloaded and when she loads again; retrained as its Shopkeeper (the Hall's Change job) while still out
	 * there, it is a shop and no inn.
	 */
	//$ gametest_ticks_batch AREA '60' '"qaInnRebuild1114cNeverSeen"'
	@GameTest(template = AREA, timeoutTicks = 60, batch = "qaInnRebuild1114cNeverSeen")
	public void anInnkeeperNeverSeenInTheVillageKeepsTheInnAcrossASaveUntilRetrained(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		BlockPos inn = helper.absolutePos(INN);
		village(helper, INN);
		helper.runAfterDelay(2, () -> {
			InnShopCounterB1177GameTests.noOtherInns(level, hall(helper));
			Villager ina = QaInnCounters1111dGameTests.innkeeper(helper, INN, AWAY);
			helper.assertTrue(counters(helper).contains(inn), "an Innkeeper never seen in the village doesn't make her counter the inn: counters " + counters(helper));
			CompoundTag tag = QaMoveInnVerify1111cGameTests.unload(ina);
			CompoundTag saved = InnCounters.get(level).save(new CompoundTag(), level.registryAccess());
			level.getDataStorage().set(InnCounters.NAME, InnCounters.load(saved, level.registryAccess()));
			helper.assertTrue(counters(helper).contains(inn),
				"after a save, the inn of an Innkeeper never seen in the village (now in an unloaded chunk) is no inn: counters " + counters(helper));
			helper.assertTrue(innRolls(helper), "no legend can come to the inn of an Innkeeper never seen in the village, after a save");
			Villager back = QaMoveInnVerify1111cGameTests.back(level, tag);
			helper.assertTrue(counters(helper).contains(inn), "the inn isn't the inn once its Innkeeper (past the edge) loads again: counters " + counters(helper));
			Retraining.retrain(level, helper.makeMockServerPlayerInLevel(), back, inn, ModVillagers.SHOPKEEPER);
			helper.assertTrue(back.getVillagerData().getProfession() == ModVillagers.SHOPKEEPER, "setup: not retrained: " + back.getVillagerData());
			helper.assertFalse(counters(helper).contains(inn), "the Innkeeper retrained as the counter's Shopkeeper (out past the edge): the shop is still the inn");
			helper.assertFalse(innRolls(helper), "a legend can come to the shop of a retrained Innkeeper out past the edge");
			helper.assertTrue(InnCounters.get(level).keeper(inn) == null, "the retrained Innkeeper is still remembered at the counter");
			back.discard();
			helper.succeed();
		});
	}

	/** Two counters in the village, both worked from past the edge: only the Innkeeper's is the inn, not the Shopkeeper's. */
	//$ gametest_ticks_batch AREA '60' '"qaInnRebuild1114cTwo"'
	@GameTest(template = AREA, timeoutTicks = 60, batch = "qaInnRebuild1114cTwo")
	public void ofTwoCountersWorkedFromPastTheEdgeOnlyTheInnkeepersIsTheInn(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		BlockPos inn = helper.absolutePos(INN);
		BlockPos shop = helper.absolutePos(SHOP);
		village(helper, INN, SHOP);
		helper.runAfterDelay(2, () -> {
			InnShopCounterB1177GameTests.noOtherInns(level, hall(helper));
			Villager sam = worker(helper, SHOP, AWAY.west(), "Sam", ModVillagers.SHOPKEEPER);
			Villager ina = worker(helper, INN, AWAY.east(), "Ina", ModVillagers.INNKEEPER);
			List<BlockPos> found = counters(helper);
			helper.assertTrue(found.equals(List.of(inn)), "two counters worked from past the edge (Ina the Innkeeper's at " + inn.toShortString()
				+ ", Sam the Shopkeeper's at " + shop.toShortString() + "): counters " + found);
			helper.assertTrue(InnCounters.get(level).keeper(shop) == null, "Sam the Shopkeeper's counter is remembered as an Innkeeper's");
			// a stale memory of an Innkeeper at the shop (one unloaded long ago): the loaded Shopkeeper working it says it's a shop
			InnCounters.get(level).remember(shop, java.util.UUID.randomUUID());
			helper.assertFalse(counters(helper).contains(shop), "Sam the Shopkeeper's counter (he is out past the edge) counts as an inn because an unloaded Innkeeper was once remembered there");
			sam.discard();
			ina.discard();
			helper.succeed();
		});
	}

	/** A baby whose job site memory is the counter (holding its ticket) is no Innkeeper working it: no inn. */
	//$ gametest_ticks_batch AREA '60' '"qaInnRebuild1114cBaby"'
	@GameTest(template = AREA, timeoutTicks = 60, batch = "qaInnRebuild1114cBaby")
	public void aBabyWhoseJobSiteIsTheCounterMakesNoInn(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		BlockPos inn = helper.absolutePos(INN);
		village(helper, INN);
		helper.runAfterDelay(2, () -> {
			InnShopCounterB1177GameTests.noOtherInns(level, hall(helper));
			Villager kid = worker(helper, INN, AWAY, "Kit", ModVillagers.INNKEEPER);
			kid.setAge(-24000);
			helper.assertTrue(kid.isBaby(), "setup: not a baby");
			helper.assertTrue(level.getPoiManager().getFreeTickets(inn) == 0, "setup: the counter's ticket isn't held");
			helper.assertTrue(counters(helper).isEmpty(), "a baby past the edge whose job site is the counter makes it the inn: counters " + counters(helper));
			BlockPos by = helper.absolutePos(INN.south());
			kid.teleportTo(by.getX() + 0.5, by.getY(), by.getZ() + 0.5);
			helper.assertTrue(counters(helper).isEmpty(), "a baby by the counter whose job site it is makes it the inn: counters " + counters(helper));
			helper.assertTrue(InnCounters.get(level).keeper(inn) == null, "a baby is remembered as the counter's Innkeeper");
			kid.discard();
			helper.succeed();
		});
	}

	/** The village's edge for an Innkeeper never seen in the village: the counter at 10.05 blocks is no inn of radius 10, the inn of radius 11. */
	//$ gametest_ticks_batch AREA '60' '"qaInnRebuild1114cEdge"'
	@GameTest(template = AREA, timeoutTicks = 60, batch = "qaInnRebuild1114cEdge")
	public void aNeverSeenInnkeepersCounterIsTheInnOnlyInsideTheVillagesEdge(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		BlockPos inn = helper.absolutePos(INN);
		village(helper, INN);
		helper.runAfterDelay(2, () -> {
			InnShopCounterB1177GameTests.noOtherInns(level, hall(helper));
			VillageHalls.RADIUS = 10;
			Villager ina = QaInnCounters1111dGameTests.innkeeper(helper, INN, AWAY);
			helper.assertTrue(counters(helper).isEmpty(), "a counter 10.05 blocks from the Hall, its Innkeeper past the edge, is the inn of a village of radius 10: " + counters(helper));
			helper.assertFalse(innRolls(helper), "a legend rolls at an inn just outside the village of radius 10");
			VillageHalls.RADIUS = 11;
			helper.assertTrue(counters(helper).equals(List.of(inn)), "a counter 10.05 blocks from the Hall, its Innkeeper never seen in the village, isn't the inn of a village of radius 11: " + counters(helper));
			helper.assertTrue(innRolls(helper), "no legend rolls at the inn just inside the village of radius 11");
			ina.discard();
			helper.succeed();
		});
	}
}
