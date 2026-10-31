package io.github.jcondedata.aliveworkplace.test;

import io.github.jcondedata.aliveworkplace.farm.FieldData;
import io.github.jcondedata.aliveworkplace.mc.Ids;
import io.github.jcondedata.aliveworkplace.orchard.Orchards;
import io.github.jcondedata.aliveworkplace.registry.ModBlocks;
import io.github.jcondedata.aliveworkplace.registry.ModComponents;
import io.github.jcondedata.aliveworkplace.registry.ModItems;
import io.github.jcondedata.aliveworkplace.registry.ModVillagers;
import io.github.jcondedata.aliveworkplace.wood.TreeFarms;
import io.github.jcondedata.aliveworkplace.work.Jobs;
import java.util.List;
import java.util.Optional;
import java.util.function.Predicate;
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
 * B737: handing a Field Marker to a lumberjack or an orchard keeper names the chest's place by the block the worker
 * really works at (the mod's Chopping Block or Fruit Basket, or the vanilla fletching table or composter), not always
 * the vanilla one.
 */
public class AreaJobWorkBlockGameTests implements net.fabricmc.fabric.api.gametest.v1.FabricGameTest {
	private static final String BIG = "aliveworkplace_test:big_area";

	/** A worker employed at {@code block} (2, 2, 2) handed a 3 x 3 area beside it by a player; what the player read. */
	private static List<String> hand(GameTestHelper helper, String name, Block block, ResourceKey<PoiType> poi, VillagerProfession job,
									 Predicate<Villager> taken) {
		ServerLevel level = helper.getLevel();
		BlockPos at = new BlockPos(2, 2, 2);
		helper.setBlock(at, block);
		Villager worker = helper.spawn(EntityType.VILLAGER, at.east());
		worker.setNoAi(true);
		Jobs.employ(level, worker, helper.absolutePos(at), poi, job);
		QaStageFiveGameTests.Client c = QaStageSixGameTests.client(helper, GameType.SURVIVAL, QaStageSixGameTests.name(name));
		ItemStack marker = new ItemStack(ModItems.FIELD_MARKER);
		marker.set(ModComponents.FIELD, new FieldData(Optional.of(Ids.of(level.dimension())), Optional.of(helper.absolutePos(new BlockPos(5, 1, 5))),
			Optional.of(helper.absolutePos(new BlockPos(7, 1, 7)))));
		c.player().setItemInHand(InteractionHand.MAIN_HAND, marker);
		QaStageSixGameTests.clickVillager(c.player(), worker, c.seen());
		List<String> said = QaVillageGuards1031GameTests.said(c);
		helper.assertTrue(taken.test(worker), "the worker didn't take the area: " + said);
		return said;
	}

	/** A lumberjack at a Chopping Block: the chest goes "within 8 blocks of their Chopping Block", no fletching table. */
	//$ gametest_ticks_batch BIG '40' '"areaWorkBlockChopping"'
	@GameTest(template = BIG, timeoutTicks = 40, batch = "areaWorkBlockChopping")
	public void aLumberjackAtAChoppingBlockIsToldOfHisChoppingBlock(GameTestHelper helper) {
		List<String> said = hand(helper, "awChop", ModBlocks.CHOPPING_BLOCK, ModVillagers.CHOPPING_BLOCK_POI, ModVillagers.LUMBERJACK,
			v -> TreeFarms.farm(v) != null);
		helper.assertTrue(said.stream().anyMatch(s -> s.contains("tree farm") && s.contains("of their Chopping Block.")),
			"the lumberjack's line doesn't name his Chopping Block: " + said);
		helper.assertTrue(said.stream().noneMatch(s -> s.toLowerCase(java.util.Locale.ROOT).contains("fletching table")),
			"the player is sent to a fletching table that isn't there: " + said);
		helper.succeed();
	}

	/** An orchard keeper at a Fruit Basket: "of their Fruit Basket", no composter. */
	//$ gametest_ticks_batch BIG '40' '"areaWorkBlockBasket"'
	@GameTest(template = BIG, timeoutTicks = 40, batch = "areaWorkBlockBasket")
	public void anOrchardKeeperAtAFruitBasketIsToldOfHerFruitBasket(GameTestHelper helper) {
		List<String> said = hand(helper, "awBask", ModBlocks.FRUIT_BASKET, ModVillagers.FRUIT_BASKET_POI, ModVillagers.ORCHARD_KEEPER,
			v -> Orchards.orchard(v) != null);
		helper.assertTrue(said.stream().anyMatch(s -> s.contains("orchard") && s.contains("of their Fruit Basket.")),
			"the orchard keeper's line doesn't name the Fruit Basket: " + said);
		helper.assertTrue(said.stream().noneMatch(s -> s.toLowerCase(java.util.Locale.ROOT).contains("composter")),
			"the player is sent to a composter that isn't there: " + said);
		helper.succeed();
	}

	/** A lumberjack at a vanilla fletching table is still told of the fletching table. */
	//$ gametest_ticks_batch BIG '40' '"areaWorkBlockFletching"'
	@GameTest(template = BIG, timeoutTicks = 40, batch = "areaWorkBlockFletching")
	public void aLumberjackAtAFletchingTableIsToldOfHisFletchingTable(GameTestHelper helper) {
		List<String> said = hand(helper, "awFlet", Blocks.FLETCHING_TABLE, PoiTypes.FLETCHER, ModVillagers.LUMBERJACK,
			v -> TreeFarms.farm(v) != null);
		helper.assertTrue(said.stream().anyMatch(s -> s.contains("tree farm") && s.contains("of their Fletching Table.")),
			"the lumberjack's line doesn't name his fletching table: " + said);
		helper.succeed();
	}
}
