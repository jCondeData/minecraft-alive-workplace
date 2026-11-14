package io.github.jcondedata.aliveworkplace.test;

import io.github.jcondedata.aliveworkplace.hall.VillageHalls;
import io.github.jcondedata.aliveworkplace.inn.Innkeepers;
import io.github.jcondedata.aliveworkplace.legend.LegendGuests;
import io.github.jcondedata.aliveworkplace.move.MoveResidents;
import io.github.jcondedata.aliveworkplace.move.MoveSite;
import io.github.jcondedata.aliveworkplace.registry.ModBlocks;
import io.github.jcondedata.aliveworkplace.registry.ModVillagers;
import io.github.jcondedata.aliveworkplace.work.Jobs;
import java.util.Optional;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.ai.village.poi.PoiManager;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.npc.VillagerProfession;
import net.minecraft.world.phys.AABB;

/**
 * B1177: inns are built with the Shop Counter since the Inn Counter was retired. A legend visiting the inn rolls, and a
 * move's residents stay at the inn, when the village's inn is a Shop Counter an Innkeeper works; a plain shop (a
 * Shopkeeper's Shop Counter) is no inn.
 */
public class InnShopCounterB1177GameTests implements FabricGameTest {
	static final String AREA = QaMoverResidents1110cGameTests.AREA;

	/** No inn left round {@code center} by other tests: Inn Counters taken down, stray Innkeepers gone. */
	static void noOtherInns(ServerLevel level, BlockPos center) {
		level.getPoiManager().findAll(h -> h.is(ModVillagers.INN_COUNTER_POI), p -> true, center, 80, PoiManager.Occupancy.ANY)
			.map(BlockPos::immutable).toList().forEach(p -> level.removeBlock(p, false));
		level.getEntitiesOfClass(Villager.class, new AABB(center).inflate(80), Innkeepers::isInnkeeper)
			.forEach(v -> v.remove(Entity.RemovalReason.DISCARDED));
	}

	/** A villager of {@code job} working the Shop Counter at the setup's inn spot (placed a few ticks before, for its POI). */
	static Villager counter(GameTestHelper helper, VillagerProfession job, String name) {
		ServerLevel level = helper.getLevel();
		BlockPos at = QaMoverResidents1110cGameTests.INN;
		Villager keeper = QaMoverResidents1110cGameTests.villager(helper, at.south(), name);
		Jobs.employ(level, keeper, helper.absolutePos(at), ModVillagers.SHOP_COUNTER_POI, job);
		return keeper;
	}

	/** A move's residents stay in a bed at the inn (not by the Hall) when the inn is an Innkeeper's Shop Counter. */
	//$ gametest_batch AREA '"b1177MoveShopInn"'
	@GameTest(template = AREA, batch = "b1177MoveShopInn")
	public void moveResidentsStayAtAShopCounterInn(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		QaMoverResidents1110cGameTests.Setup s = QaMoverResidents1110cGameTests.setup(helper, "b1177inn", false);
		QaMoverResidents1110cGameTests.bed(helper, QaMoverResidents1110cGameTests.INN_BED_1);
		QaMoverResidents1110cGameTests.bed(helper, QaMoverResidents1110cGameTests.INN_BED_2);
		helper.setBlock(QaMoverResidents1110cGameTests.INN, ModBlocks.SHOP_COUNTER);
		helper.runAfterDelay(2, () -> {
			noOtherInns(level, s.hall());
			counter(helper, ModVillagers.INNKEEPER, "Ina");
			QaMoverResidents1110cGameTests.confirm(helper, s);
			MoveSite site = QaMoverResidents1110cGameTests.start(level, s);
			Optional<BlockPos> bed = MoveResidents.stayBed(level, site);
			BlockPos one = helper.absolutePos(QaMoverResidents1110cGameTests.INN_BED_1);
			BlockPos two = helper.absolutePos(QaMoverResidents1110cGameTests.INN_BED_2);
			helper.assertTrue(bed.isPresent() && (bed.get().equals(one) || bed.get().equals(two)),
				"residents should stay at the Innkeeper's Shop Counter inn (" + one.toShortString() + " or " + two.toShortString() + "), got " + bed);
			helper.succeed();
		});
	}

	/** A plain shop is no inn: the move's residents stay by the Hall. */
	//$ gametest_batch AREA '"b1177MovePlainShop"'
	@GameTest(template = AREA, batch = "b1177MovePlainShop")
	public void moveResidentsDontStayAtAPlainShop(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		QaMoverResidents1110cGameTests.Setup s = QaMoverResidents1110cGameTests.setup(helper, "b1177shop", false);
		QaMoverResidents1110cGameTests.bed(helper, QaMoverResidents1110cGameTests.INN_BED_1);
		QaMoverResidents1110cGameTests.bed(helper, QaMoverResidents1110cGameTests.INN_BED_2);
		helper.setBlock(QaMoverResidents1110cGameTests.INN, ModBlocks.SHOP_COUNTER);
		helper.runAfterDelay(2, () -> {
			noOtherInns(level, s.hall());
			counter(helper, ModVillagers.SHOPKEEPER, "Sol");
			QaMoverResidents1110cGameTests.confirm(helper, s);
			MoveSite site = QaMoverResidents1110cGameTests.start(level, s);
			Optional<BlockPos> bed = MoveResidents.stayBed(level, site);
			BlockPos hallBed = helper.absolutePos(QaMoverResidents1110cGameTests.HALL_BED);
			helper.assertTrue(bed.isPresent() && bed.get().equals(hallBed),
				"a Shopkeeper's counter is no inn: residents should stay by the Hall at " + hallBed.toShortString() + ", got " + bed);
			helper.succeed();
		});
	}

	/** The legend roll at the inn: none without an inn or at a plain shop, one at an Innkeeper's Shop Counter or an Inn Counter. */
	//$ gametest_batch AREA '"b1177LegendInn"'
	@GameTest(template = AREA, batch = "b1177LegendInn")
	public void legendRollsAtAShopCounterInnButNotAPlainShop(GameTestHelper helper) {
		Leftovers.clear(helper);
		ServerLevel level = helper.getLevel();
		BlockPos hall = helper.absolutePos(QaMoverResidents1110cGameTests.HALL);
		helper.setBlock(QaMoverResidents1110cGameTests.INN, ModBlocks.SHOP_COUNTER);
		helper.runAfterDelay(2, () -> {
			noOtherInns(level, hall);
			helper.assertFalse(LegendGuests.rollsOn(level, hall, null, "inn", 1), "a Shop Counter nobody works counted as an inn");
			Villager keeper = counter(helper, ModVillagers.SHOPKEEPER, "Sol");
			helper.assertFalse(LegendGuests.rollsOn(level, hall, null, "inn", 1), "a Shopkeeper's Shop Counter counted as an inn");
			helper.assertTrue(Innkeepers.counters(level, hall, VillageHalls.RADIUS).isEmpty(), "a plain shop found as an inn counter");
			keeper.remove(Entity.RemovalReason.DISCARDED);
			counter(helper, ModVillagers.INNKEEPER, "Ina");
			helper.assertTrue(LegendGuests.rollsOn(level, hall, null, "inn", 1), "an Innkeeper's Shop Counter inn doesn't roll for a legend");
			helper.assertTrue(Innkeepers.counters(level, hall, VillageHalls.RADIUS).contains(helper.absolutePos(QaMoverResidents1110cGameTests.INN)),
				"the Innkeeper's Shop Counter isn't among the inn counters");
			noOtherInns(level, hall);
			helper.setBlock(QaMoverResidents1110cGameTests.INN, ModBlocks.INN_COUNTER);
		});
		helper.runAfterDelay(5, () -> {
			helper.assertTrue(LegendGuests.rollsOn(level, hall, null, "inn", 1), "an old Inn Counter no longer rolls for a legend");
			helper.succeed();
		});
	}
}
