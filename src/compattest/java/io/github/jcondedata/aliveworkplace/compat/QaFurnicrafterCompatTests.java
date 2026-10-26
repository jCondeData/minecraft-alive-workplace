package io.github.jcondedata.aliveworkplace.compat;

import io.github.jcondedata.aliveworkplace.craft.FurnicrafterWork;
import io.github.jcondedata.aliveworkplace.registry.ModAttachments;
import io.github.jcondedata.aliveworkplace.registry.ModBlocks;
import io.github.jcondedata.aliveworkplace.registry.ModVillagers;
import io.github.jcondedata.aliveworkplace.store.StockOrders;
import io.github.jcondedata.aliveworkplace.work.Stations;
import java.util.concurrent.atomic.AtomicBoolean;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.Container;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;

/**
 * QA (lane d, 2026-10-26) for ROADMAP 38.12, the Furnicrafter, with the real CobbleFurnies 1.2 (its Oak Chair is 1 oak
 * plank and 2 sticks, {@code data/cobblefurnies/recipe/furni_crafting/oak_chair.json}): the piece is made "from the
 * store's materials", so
 * <ul>
 * <li>a store one plank short of a chair (2 planks: the sticks would take both) makes nothing and loses nothing: no
 * planks cut into sticks for a chair that can't be finished; one more plank later and the chair is made;</li>
 * <li>sticks already in the store are used as they are: 1 plank and 2 sticks make the chair with nothing cut.</li>
 * </ul>
 */
public class QaFurnicrafterCompatTests implements FabricGameTest {
	private static final String AREA = CompatGameTests.AREA;
	private static final BlockPos CRAFTER = new BlockPos(8, 2, 2);
	private static final BlockPos STOREHOUSE = new BlockPos(2, 2, 13);
	private static final BlockPos STORE_CHEST = new BlockPos(2, 2, 15);

	private static Item furniItem(String path) {
		return BuiltInRegistries.ITEM.get(ResourceLocation.fromNamespaceAndPath("cobblefurnies", path));
	}

	private static Block furni(String path) {
		return BuiltInRegistries.BLOCK.get(ResourceLocation.fromNamespaceAndPath("cobblefurnies", path));
	}

	/** A storehouse with one chest, ordering Oak Chairs, and a Furnicrafter at a Furni Crafter by it. */
	private static Villager setUp(GameTestHelper helper, ItemStack... inStore) {
		PartnerShowsCompatTests.clearLeftovers(helper);
		FurnicrafterCompatTests.ownStoreOnly(helper);
		helper.setDayTime(2000);
		helper.setBlock(STOREHOUSE, ModBlocks.STOREHOUSE);
		helper.setBlock(STORE_CHEST, Blocks.CHEST);
		Container store = helper.getBlockEntity(STORE_CHEST);
		for (int i = 0; i < inStore.length; i++) {
			store.setItem(i, inStore[i]);
		}
		helper.assertTrue(furniItem("oak_chair") != Items.AIR, "setup: CobbleFurnies has no oak_chair");
		StockOrders.cycle(helper.getLevel(), helper.absolutePos(STOREHOUSE), furniItem("oak_chair"));
		helper.setBlock(CRAFTER, furni("furnicrafter"));
		Villager villager = helper.spawn(EntityType.VILLAGER, CRAFTER.south());
		Stations.assign(helper.getLevel(), villager, helper.absolutePos(CRAFTER), ModVillagers.FURNICRAFTER);
		helper.assertTrue(FurnicrafterWork.isFurnicrafter(villager), "setup: no Furnicrafter at the Furni Crafter");
		return villager;
	}

	private static String stock(Container store) {
		return store.countItem(Items.OAK_PLANKS) + " planks, " + store.countItem(Items.STICK) + " sticks, " + store.countItem(furniItem("oak_chair")) + " chairs";
	}

	/**
	 * Two planks in the store and Oak Chairs ordered: after 1,500 ticks the store still holds its 2 planks, no sticks and
	 * no chair (the Furnicrafter's hands empty too); a third plank put in, the chair is made (so she was at work all along).
	 */
	//$ gametest_ticks_batch AREA '4200' '"qaFurnicrafterShort"'
	@GameTest(template = AREA, timeoutTicks = 4200, batch = "qaFurnicrafterShort")
	public void aStoreOnePlankShortMakesNothingAndCutsNoPlanks(GameTestHelper helper) {
		Villager crafter = setUp(helper, new ItemStack(Items.OAK_PLANKS, 2));
		Container store = helper.getBlockEntity(STORE_CHEST);
		AtomicBoolean topped = new AtomicBoolean();
		helper.runAfterDelay(1500, () -> {
			helper.assertTrue(store.countItem(Items.OAK_PLANKS) == 2 && store.countItem(Items.STICK) == 0 && store.countItem(furniItem("oak_chair")) == 0,
				"one plank short of an Oak Chair, the store has " + stock(store) + " (expected 2 planks, nothing cut, no chair)");
			helper.assertTrue(crafter.getInventory().countItem(Items.OAK_PLANKS) == 0 && crafter.getInventory().countItem(Items.STICK) == 0,
				"one plank short, the Furnicrafter holds " + crafter.getInventory().countItem(Items.OAK_PLANKS) + " planks and "
					+ crafter.getInventory().countItem(Items.STICK) + " sticks");
			helper.assertTrue(ModAttachments.ITEMS_CRAFTED.getOrElse(crafter, 0) == 0, "one plank short, something was crafted: "
				+ ModAttachments.ITEMS_CRAFTED.getOrElse(crafter, 0));
			int free = -1;
			for (int i = 0; i < store.getContainerSize() && free < 0; i++) {
				if (store.getItem(i).isEmpty()) {
					free = i;
				}
			}
			store.setItem(free, new ItemStack(Items.OAK_PLANKS, 1));
			topped.set(true);
		});
		helper.succeedWhen(() -> {
			helper.assertTrue(topped.get(), "waiting for the plank to be put in");
			helper.assertTrue(store.countItem(furniItem("oak_chair")) == 1, "with the third plank no chair yet: " + stock(store));
		});
	}

	/**
	 * The storehouse also orders oak planks (keep 16) and its store holds 3: those planks are kept for that order, so no
	 * chair is made from them in 1,500 ticks and the store keeps its 3 planks (the wiki: "never what the storehouse's other
	 * orders keep"). The planks' order dropped, the chair is made.
	 */
	//$ gametest_ticks_batch AREA '4200' '"qaFurnicrafterKept"'
	@GameTest(template = AREA, timeoutTicks = 4200, batch = "qaFurnicrafterKept")
	public void planksAnotherOrderKeepsAreNotMadeIntoAChair(GameTestHelper helper) {
		BlockPos storehouse = helper.absolutePos(STOREHOUSE);
		Villager crafter = setUp(helper, new ItemStack(Items.OAK_PLANKS, 3));
		StockOrders.cycle(helper.getLevel(), storehouse, Items.OAK_PLANKS);
		helper.assertTrue(StockOrders.of(helper.getLevel(), storehouse).getOrDefault(Items.OAK_PLANKS, 0) == 16
			&& StockOrders.of(helper.getLevel(), storehouse).containsKey(furniItem("oak_chair")), "setup: the orders are " + StockOrders.of(helper.getLevel(), storehouse));
		Container store = helper.getBlockEntity(STORE_CHEST);
		AtomicBoolean dropped = new AtomicBoolean();
		helper.runAfterDelay(1500, () -> {
			helper.assertTrue(store.countItem(Items.OAK_PLANKS) == 3 && store.countItem(furniItem("oak_chair")) == 0,
				"with the planks kept for the store's own order (16, 3 there), the store has " + stock(store) + " (expected the 3 planks kept, no chair)");
			helper.assertTrue(ModAttachments.ITEMS_CRAFTED.getOrElse(crafter, 0) == 0, "something was made from the kept planks");
			for (int i = 0; i < 5; i++) {
				StockOrders.cycle(helper.getLevel(), storehouse, Items.OAK_PLANKS); // 32, 64, 128, 256, dropped
			}
			helper.assertTrue(!StockOrders.of(helper.getLevel(), storehouse).containsKey(Items.OAK_PLANKS), "setup: the planks' order wasn't dropped");
			dropped.set(true);
		});
		helper.succeedWhen(() -> {
			helper.assertTrue(dropped.get(), "waiting for the planks' order to be dropped");
			helper.assertTrue(store.countItem(furniItem("oak_chair")) == 1, "the planks' order dropped, still no chair: " + stock(store));
		});
	}

	/**
	 * 16 Red Lamps ordered (a red wool, a stick and a redstone each) with red wool for only 2 and plenty of the rest: she
	 * makes the 2 there are materials for (the wiki: "as many as asked for if the materials are there, or half, down to
	 * one ... 16 Red Lamps ordered and wool for 2: she makes 2"), and both wools are used.
	 */
	//$ gametest_ticks_batch AREA '3000' '"qaFurnicrafterHalf"'
	@GameTest(template = AREA, timeoutTicks = 3000, batch = "qaFurnicrafterHalf")
	public void sixteenLampsOrderedWithWoolForTwoMakesTwo(GameTestHelper helper) {
		BlockPos storehouse = helper.absolutePos(STOREHOUSE);
		Villager crafter = setUp(helper, new ItemStack(Items.RED_WOOL, 2), new ItemStack(Items.STICK, 16), new ItemStack(Items.REDSTONE, 16));
		StockOrders.cycle(helper.getLevel(), storehouse, furniItem("red_lamp"));
		helper.assertTrue(StockOrders.of(helper.getLevel(), storehouse).getOrDefault(furniItem("red_lamp"), 0) == 16, "setup: the orders are "
			+ StockOrders.of(helper.getLevel(), storehouse));
		Container store = helper.getBlockEntity(STORE_CHEST);
		helper.succeedWhen(() -> {
			int lamps = store.countItem(furniItem("red_lamp"));
			helper.assertTrue(lamps == 2, lamps + " Red Lamps in the store, with wool for 2 (made " + ModAttachments.ITEMS_CRAFTED.getOrElse(crafter, 0) + "); wool left "
				+ store.countItem(Items.RED_WOOL) + ", sticks " + store.countItem(Items.STICK) + ", redstone " + store.countItem(Items.REDSTONE));
			helper.assertTrue(store.countItem(Items.RED_WOOL) == 0 && store.countItem(Items.STICK) == 14 && store.countItem(Items.REDSTONE) == 14,
				"two lamps made, but the store has " + store.countItem(Items.RED_WOOL) + " wool, " + store.countItem(Items.STICK) + " sticks and "
					+ store.countItem(Items.REDSTONE) + " redstone (expected 0, 14, 14)");
		});
	}

	/** One plank and two sticks in the store: the chair is made from them as they are, and nothing is left or cut. */
	//$ gametest_ticks_batch AREA '2400' '"qaFurnicrafterSticks"'
	@GameTest(template = AREA, timeoutTicks = 2400, batch = "qaFurnicrafterSticks")
	public void sticksInTheStoreAreUsedAsTheyAre(GameTestHelper helper) {
		Villager crafter = setUp(helper, new ItemStack(Items.OAK_PLANKS, 1), new ItemStack(Items.STICK, 2));
		Container store = helper.getBlockEntity(STORE_CHEST);
		helper.succeedWhen(() -> {
			helper.assertTrue(store.countItem(furniItem("oak_chair")) == 1, "no chair from 1 plank and 2 sticks yet: " + stock(store));
			helper.assertTrue(store.countItem(Items.OAK_PLANKS) == 0 && store.countItem(Items.STICK) == 0,
				"the chair is made but the store has " + stock(store) + " (expected the plank and both sticks used)");
			helper.assertTrue(ModAttachments.ITEMS_CRAFTED.getOrElse(crafter, 0) >= 1, "nothing counted as made");
		});
	}
}
