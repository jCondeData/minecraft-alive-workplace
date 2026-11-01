package io.github.jcondedata.aliveworkplace.test;

import io.github.jcondedata.aliveworkplace.farm.FieldData;
import io.github.jcondedata.aliveworkplace.mc.Ids;
import io.github.jcondedata.aliveworkplace.orchard.Orchards;
import io.github.jcondedata.aliveworkplace.registry.ModAttachments;
import io.github.jcondedata.aliveworkplace.registry.ModBlocks;
import io.github.jcondedata.aliveworkplace.registry.ModComponents;
import io.github.jcondedata.aliveworkplace.registry.ModItems;
import io.github.jcondedata.aliveworkplace.registry.ModVillagers;
import io.github.jcondedata.aliveworkplace.wood.TreeFarms;
import io.github.jcondedata.aliveworkplace.work.Jobs;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.village.poi.PoiType;
import net.minecraft.world.entity.ai.village.poi.PoiTypes;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.npc.VillagerProfession;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;

/**
 * QA (2026-10-31) of B718: failures found past the fix (B731). The class's cabin-bed test (B715/B732) lives in
 * {@code QaCabinBedWorkshop1031GameTests}; a berry plot test was added with the fix.
 * <ul>
 * <li>B718 ("a build, quarry or field of a player who may not change a protected village is refused where it reaches
 * into it"; guide: inside a protected village "only the owner, their friends and operators may break or place
 * blocks"): the Field Marker handed to a lumberjack (tree farm) or an orchard keeper is still taken when it reaches into
 * someone's protected village; only the farmer's Fields.assign was guarded. The berry breeder's plot takes the same path.</li>
 * </ul>
 */
public class QaVillageGuardsBug1031Tests implements net.fabricmc.fabric.api.gametest.v1.FabricGameTest {
	private static final String HUGE = "aliveworkplace_test:huge_area";

	/**
	 * A worker at {@code block} (22, 2, 26), outside the village, handed a Field Marker area from (25, 2) to (28, 20) by a
	 * stranger: both corners outside the protected village (hall (11, 2, 11), reach 16), the area through its edge (column
	 * (25, 11) is 14 from the hall). True if taken.
	 */
	private static boolean handArea(GameTestHelper helper, QaStageFiveGameTests.Client c, Block block, ResourceKey<PoiType> poi, VillagerProfession job,
									java.util.function.Predicate<Villager> taken) {
		ServerLevel level = helper.getLevel();
		BlockPos at = new BlockPos(22, 2, 26);
		helper.setBlock(at, block);
		Villager worker = helper.spawn(EntityType.VILLAGER, at.west());
		worker.setNoAi(true);
		Jobs.employ(level, worker, helper.absolutePos(at), poi, job);
		ItemStack marker = new ItemStack(ModItems.FIELD_MARKER);
		marker.set(ModComponents.FIELD, new FieldData(Optional.of(Ids.of(level.dimension())), Optional.of(helper.absolutePos(new BlockPos(25, 1, 2))),
			Optional.of(helper.absolutePos(new BlockPos(28, 1, 20)))));
		c.player().setItemInHand(InteractionHand.MAIN_HAND, marker);
		QaStageSixGameTests.clickVillager(c.player(), worker, c.seen());
		boolean yes = taken.test(worker);
		worker.discard();
		return yes;
	}

	/** B718: a stranger's tree farm (Field Marker to his lumberjack) reaching into a protected village is refused, saying whose. */
	//$ gametest_ticks_batch HUGE '60' '"qaGuardsBugTreeFarm"'
	@GameTest(template = HUGE, timeoutTicks = 60, batch = "qaGuardsBugTreeFarm")
	public void qaAStrangersTreeFarmReachingIntoAProtectedVillageIsRefused(GameTestHelper helper) {
		ProtectionSpecGameTests.protectedHall(helper, ProtectionSpecGameTests.player(helper));
		QaStageFiveGameTests.Client c = QaStageSixGameTests.client(helper, GameType.SURVIVAL, QaStageSixGameTests.name("qgTree"));
		boolean taken = handArea(helper, c, ModBlocks.CHOPPING_BLOCK, ModVillagers.CHOPPING_BLOCK_POI, ModVillagers.LUMBERJACK, v -> TreeFarms.farm(v) != null);
		helper.assertTrue(!taken, "a stranger's lumberjack took a tree farm reaching into Owner's protected village (he'll plant and fell there): "
			+ QaVillageGuards1031GameTests.said(c));
		helper.assertTrue(QaVillageGuards1031GameTests.said(c).stream().anyMatch(s -> s.contains("Owner's village")),
			"the stranger wasn't told whose village it is: " + QaVillageGuards1031GameTests.said(c));
		helper.succeed();
	}

	/** B718: a stranger's orchard (Field Marker to his orchard keeper) reaching into a protected village is refused, saying whose. */
	//$ gametest_ticks_batch HUGE '60' '"qaGuardsBugOrchard"'
	@GameTest(template = HUGE, timeoutTicks = 60, batch = "qaGuardsBugOrchard")
	public void qaAStrangersOrchardReachingIntoAProtectedVillageIsRefused(GameTestHelper helper) {
		ProtectionSpecGameTests.protectedHall(helper, ProtectionSpecGameTests.player(helper));
		QaStageFiveGameTests.Client c = QaStageSixGameTests.client(helper, GameType.SURVIVAL, QaStageSixGameTests.name("qgOrch"));
		boolean taken = handArea(helper, c, ModBlocks.FRUIT_BASKET, ModVillagers.FRUIT_BASKET_POI, ModVillagers.ORCHARD_KEEPER, v -> Orchards.orchard(v) != null);
		helper.assertTrue(!taken, "a stranger's orchard keeper took an orchard reaching into Owner's protected village (he'll till and plant there): "
			+ QaVillageGuards1031GameTests.said(c));
		helper.assertTrue(QaVillageGuards1031GameTests.said(c).stream().anyMatch(s -> s.contains("Owner's village")),
			"the stranger wasn't told whose village it is: " + QaVillageGuards1031GameTests.said(c));
		helper.succeed();
	}

	/** B731: a stranger's berry plot (Field Marker to his berry breeder, same code path) reaching into a protected village is refused, saying whose. */
	//$ gametest_ticks_batch HUGE '60' '"qaGuardsBugBerryPlot"'
	@GameTest(template = HUGE, timeoutTicks = 60, batch = "qaGuardsBugBerryPlot")
	public void aStrangersBerryPlotReachingIntoAProtectedVillageIsRefused(GameTestHelper helper) {
		ProtectionSpecGameTests.protectedHall(helper, ProtectionSpecGameTests.player(helper));
		QaStageFiveGameTests.Client c = QaStageSixGameTests.client(helper, GameType.SURVIVAL, QaStageSixGameTests.name("qgBerry"));
		boolean taken = handArea(helper, c, Blocks.COMPOSTER, PoiTypes.FARMER, ModVillagers.BERRY_BREEDER,
			v -> ModAttachments.BERRY_PLOT.get(v) != null);
		helper.assertTrue(!taken, "a stranger's berry breeder took a plot reaching into Owner's protected village (she'll till and plant there): "
			+ QaVillageGuards1031GameTests.said(c));
		helper.assertTrue(QaVillageGuards1031GameTests.said(c).stream().anyMatch(s -> s.contains("Owner's village")),
			"the stranger wasn't told whose village it is: " + QaVillageGuards1031GameTests.said(c));
		helper.succeed();
	}
}
