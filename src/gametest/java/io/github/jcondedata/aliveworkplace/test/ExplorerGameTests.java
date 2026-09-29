package io.github.jcondedata.aliveworkplace.test;

import io.github.jcondedata.aliveworkplace.explore.ExplorerWork;
import io.github.jcondedata.aliveworkplace.explore.Explorers;
import io.github.jcondedata.aliveworkplace.registry.ModAttachments;
import io.github.jcondedata.aliveworkplace.work.Jobs;
import java.util.Set;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.Container;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.village.poi.PoiTypes;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.npc.VillagerProfession;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.levelgen.structure.BuiltinStructures;
import net.minecraft.world.level.saveddata.maps.MapDecorationTypes;
import net.minecraft.world.level.storage.loot.LootTable;

/** Cartographers go exploring from their cartography table and bring back what they find. */
public class ExplorerGameTests implements FabricGameTest {
	private static final String AREA = "aliveworkplace_test:big_area";
	private static final BlockPos TABLE = new BlockPos(11, 2, 11);
	private static final BlockPos CHEST = new BlockPos(11, 2, 13);
	/** What only the hunt brings back (with a sword or an axe). */
	private static final Set<Item> HUNTED = Set.of(Items.ROTTEN_FLESH, Items.BEEF, Items.PORKCHOP, Items.MUTTON, Items.CHICKEN, Items.RABBIT,
		Items.GUNPOWDER, Items.SPIDER_EYE, Items.ENDER_PEARL, Items.PHANTOM_MEMBRANE);

	/** With bread in the chest, an explorer goes out, searches a few stops and brings the finds home. */
	@GameTest(template = AREA, timeoutTicks = 4000, batch = "explorer_finds")
	public void explorerBringsBackFinds(GameTestHelper helper) {
		Leftovers.clear(helper);
		shortTrips(helper);
		helper.setDayTime(2000);
		Villager explorer = explorer(helper);
		Container chest = helper.getBlockEntity(CHEST);
		chest.setItem(0, new ItemStack(Items.BREAD, 4));
		helper.succeedWhen(() -> {
			helper.assertTrue(ModAttachments.EXPEDITIONS.getOrElse(explorer, 0) >= 1, "no expedition finished yet");
			int bread = 0;
			boolean finds = false;
			for (int i = 0; i < chest.getContainerSize(); i++) {
				ItemStack stack = chest.getItem(i);
				bread += stack.is(Items.BREAD) ? stack.getCount() : 0;
				finds |= !stack.isEmpty() && !stack.is(Items.BREAD);
			}
			helper.assertTrue(bread < 4, "no bread was eaten: " + bread);
			helper.assertTrue(finds, "nothing was brought back");
		});
	}

	/** With a sword, the explorer hunts too: meat or monster drops come back, and the sword comes back worn. */
	@GameTest(template = AREA, timeoutTicks = 4000, batch = "explorer_hunts")
	public void armedExplorerHuntsToo(GameTestHelper helper) {
		Leftovers.clear(helper);
		shortTrips(helper);
		helper.setDayTime(2000);
		Villager explorer = explorer(helper);
		Container chest = helper.getBlockEntity(CHEST);
		chest.setItem(0, new ItemStack(Items.COOKED_BEEF, 3));
		chest.setItem(1, new ItemStack(Items.IRON_SWORD));
		helper.succeedWhen(() -> {
			helper.assertTrue(ModAttachments.EXPEDITIONS.getOrElse(explorer, 0) >= 1, "no expedition finished yet");
			boolean hunted = false;
			boolean worn = false;
			for (int i = 0; i < chest.getContainerSize(); i++) {
				ItemStack stack = chest.getItem(i);
				hunted |= HUNTED.contains(stack.getItem());
				worn |= stack.is(Items.IRON_SWORD) && stack.getDamageValue() > 0;
			}
			helper.assertTrue(hunted, "nothing hunted came back");
			helper.assertTrue(worn, "the sword didn't come back worn");
		});
	}

	/** Without food nobody sets out: the chest is left alone. */
	@GameTest(template = AREA, timeoutTicks = 400, batch = "explorer_hungry")
	public void hungryExplorerStaysHome(GameTestHelper helper) {
		Leftovers.clear(helper);
		shortTrips(helper);
		helper.setDayTime(2000);
		Villager explorer = explorer(helper);
		Container chest = helper.getBlockEntity(CHEST);
		chest.setItem(0, new ItemStack(Items.ROTTEN_FLESH, 8)); // not fit to pack
		chest.setItem(1, new ItemStack(Items.IRON_SWORD));
		helper.runAtTickTime(300, () -> {
			helper.assertTrue(!ExplorerWork.isBusy(explorer), "set out without food");
			helper.assertTrue(chest.getItem(0).getCount() == 8 && chest.getItem(1).is(Items.IRON_SWORD), "took from the chest");
			helper.assertTrue(ModAttachments.EXPEDITIONS.getOrElse(explorer, 0) == 0, "went on an expedition");
			helper.succeed();
		});
	}

	/** The finds tables load (the Cobblemon one only with Cobblemon); a map to a place is named and marked like vanilla's. */
	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
	public void findsAndMaps(GameTestHelper helper) {
		var tables = helper.getLevel().getServer().reloadableRegistries();
		helper.assertTrue(tables.getLootTable(Explorers.FINDS) != LootTable.EMPTY, "no finds table");
		helper.assertTrue(tables.getLootTable(Explorers.HUNTING) != LootTable.EMPTY, "no hunting table");
		helper.assertTrue(tables.getLootTable(Explorers.COBBLEMON) == LootTable.EMPTY, "the Cobblemon finds loaded without Cobblemon");
		helper.assertTrue(Explorers.isFood(new ItemStack(Items.BREAD)) && !Explorers.isFood(new ItemStack(Items.ROTTEN_FLESH))
			&& !Explorers.isFood(new ItemStack(Items.GOLDEN_APPLE)) && !Explorers.isFood(new ItemStack(Items.CHICKEN)), "food rules");
		// No structures in the test world: no place to draw a map to, and no trouble looking.
		helper.assertTrue(Explorers.findPlace(helper.getLevel(), helper.absolutePos(BlockPos.ZERO)) == null, "found a place in a world without structures");
		var village = helper.getLevel().registryAccess().registryOrThrow(Registries.STRUCTURE).getHolderOrThrow(BuiltinStructures.VILLAGE_PLAINS);
		ItemStack map = Explorers.mapTo(helper.getLevel(), new Explorers.Place(helper.absolutePos(new BlockPos(0, 1, 0)).offset(300, 0, 200), village));
		helper.assertTrue(map.is(Items.FILLED_MAP) && map.get(DataComponents.MAP_ID) != null, "not a filled map");
		helper.assertTrue(map.getHoverName().getString().equals("Explorer's Map: Plains Village"), "named " + map.getHoverName().getString());
		var decorations = map.get(DataComponents.MAP_DECORATIONS);
		helper.assertTrue(decorations != null && decorations.decorations().values().stream().anyMatch(d -> d.type().equals(MapDecorationTypes.PLAINS_VILLAGE)),
			"no village marker: " + decorations);
		helper.succeed();
	}

	/** Short trips inside the test area. */
	private static void shortTrips(GameTestHelper helper) {
		int range = ExplorerWork.RANGE;
		int min = ExplorerWork.MIN_HOP;
		int max = ExplorerWork.MAX_HOP;
		int search = ExplorerWork.SEARCH_TICKS;
		int rest = ExplorerWork.REST_TICKS;
		ExplorerWork.RANGE = 8;
		ExplorerWork.MIN_HOP = 3;
		ExplorerWork.MAX_HOP = 6;
		ExplorerWork.SEARCH_TICKS = 30;
		ExplorerWork.REST_TICKS = 100;
		Leftovers.after(helper, () -> {
			ExplorerWork.RANGE = range;
			ExplorerWork.MIN_HOP = min;
			ExplorerWork.MAX_HOP = max;
			ExplorerWork.SEARCH_TICKS = search;
			ExplorerWork.REST_TICKS = rest;
		});
	}

	private static Villager explorer(GameTestHelper helper) {
		helper.setBlock(TABLE, Blocks.CARTOGRAPHY_TABLE);
		helper.setBlock(CHEST, Blocks.CHEST);
		Villager villager = helper.spawn(EntityType.VILLAGER, new BlockPos(12, 2, 12));
		Jobs.employ(helper.getLevel(), villager, helper.absolutePos(TABLE), PoiTypes.CARTOGRAPHER, VillagerProfession.CARTOGRAPHER);
		return villager;
	}
}
