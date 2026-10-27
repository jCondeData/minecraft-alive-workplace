package io.github.jcondedata.aliveworkplace.compat;

import io.github.jcondedata.aliveworkplace.registry.ModAttachments;
import io.github.jcondedata.aliveworkplace.registry.ModBlocks;
import io.github.jcondedata.aliveworkplace.registry.ModVillagers;
import io.github.jcondedata.aliveworkplace.work.Jobs;
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
import net.minecraft.world.level.block.Blocks;

/**
 * QA bug tests (lane-d-1025-0408), from ROADMAP 38.11 ("cooks ... the candies ... and stocks them for trainers and
 * players") and the wiki ("keeps 16 of each in the chests by the stove"). Both FAIL on the current code: a Novice Chef
 * with the makings for 20 EXP Candy XS crafts 350+ items in 3000 ticks and leaves no XS candy in the chest (12 XS and
 * an S in his bag), and 15 XS candies already in the chest are cooked up into one EXP Candy M.
 */
public class QaCuisineCandyBugTests implements FabricGameTest {
	private static final String AREA = CompatGameTests.AREA;
	private static final BlockPos STATION = new BlockPos(2, 2, 2);
	private static final BlockPos CHEST = new BlockPos(2, 2, 4);

	private static Item cm(String path) {
		Item item = BuiltInRegistries.ITEM.get(ResourceLocation.fromNamespaceAndPath("cobblemon", path));
		if (item == Items.AIR) {
			throw new AssertionError("no cobblemon:" + path);
		}
		return item;
	}

	private static Container chest(GameTestHelper helper, ItemStack... items) {
		helper.setBlock(CHEST, Blocks.CHEST);
		Container chest = helper.getBlockEntity(CHEST);
		for (int i = 0; i < items.length; i++) {
			chest.setItem(i, items[i]);
		}
		return chest;
	}

	private static Villager chef(GameTestHelper helper) {
		helper.setDayTime(2000);
		helper.setBlock(STATION, ModBlocks.KITCHEN_STOVE);
		Villager chef = helper.spawn(EntityType.VILLAGER, new BlockPos(3, 2, 3));
		Jobs.employ(helper.getLevel(), chef, helper.absolutePos(STATION), ModVillagers.KITCHEN_STOVE_POI, ModVillagers.CHEF);
		return chef;
	}

	/** What a chest holds, for failure messages. */
	private static String dump(Container c) {
		StringBuilder b = new StringBuilder("[");
		for (int i = 0; i < c.getContainerSize(); i++) {
			if (!c.getItem(i).isEmpty()) {
				b.append(c.getItem(i).getCount()).append(' ').append(BuiltInRegistries.ITEM.getKey(c.getItem(i).getItem()).getPath()).append(", ");
			}
		}
		return b.append(']').toString();
	}

	private static String state(Container chest, Villager chef) {
		return "chest " + dump(chest) + ", crafted " + ModAttachments.ITEMS_CRAFTED.getOrElse(chef, 0) + ", bag "
			+ ModAttachments.BUILDER_BAG.getOrCreate(chef).stacks();
	}

	/** The wiki: the Chef "keeps 16 of each" pot dish. Twenty sets of makings from none: 16 EXP Candies XS, 4 sets left. */
	//$ gametest_ticks_batch AREA '3200' '"qa_pot_keep_sixteen"'
	@GameTest(template = AREA, timeoutTicks = 3200, batch = "qa_pot_keep_sixteen")
	public void theChefStopsAtSixteenCandies(GameTestHelper helper) {
		PartnerShowsCompatTests.clearLeftovers(helper);
		Container chest = chest(helper, new ItemStack(Items.SCULK, 20), new ItemStack(Items.HONEYCOMB, 20));
		Villager chef = chef(helper);
		Item candy = cm("exp_candy_xs");
		long[] reached = {-1};
		helper.onEachTick(() -> {
			if (reached[0] < 0 && chest.countItem(candy) >= 16) {
				reached[0] = helper.getTick();
			}
		});
		helper.succeedWhen(() -> {
			helper.assertTrue(reached[0] >= 0, chest.countItem(candy) + " EXP Candies XS in the chest so far; " + state(chest, chef));
			helper.assertTrue(chest.countItem(candy) == 16, chest.countItem(candy) + " candies: 16 are kept; " + state(chest, chef));
			helper.assertTrue(chest.countItem(Items.SCULK) == 4 && chest.countItem(Items.HONEYCOMB) == 4, "4 sets of makings should be left; " + state(chest, chef));
			helper.assertTrue(helper.getTick() - reached[0] >= 400, "waiting to see he cooks no more");
		});
	}

	/** Both sides of 16: 15 EXP Candies XS in the chest, and he cooks exactly one more and keeps them. */
	//$ gametest_ticks_batch AREA '3000' '"qa_pot_fifteen"'
	@GameTest(template = AREA, timeoutTicks = 3000, batch = "qa_pot_fifteen")
	public void fifteenCandiesGetOneMore(GameTestHelper helper) {
		PartnerShowsCompatTests.clearLeftovers(helper);
		Item candy = cm("exp_candy_xs");
		Container chest = chest(helper, new ItemStack(candy, 15), new ItemStack(Items.SCULK, 4), new ItemStack(Items.HONEYCOMB, 4));
		Villager chef = chef(helper);
		long[] reached = {-1};
		helper.onEachTick(() -> {
			if (reached[0] < 0 && chest.countItem(candy) >= 16) {
				reached[0] = helper.getTick();
			}
		});
		helper.succeedWhen(() -> {
			helper.assertTrue(reached[0] >= 0, "the store's 15 EXP Candies XS became " + chest.countItem(candy) + "; " + state(chest, chef));
			helper.assertTrue(chest.countItem(candy) == 16, chest.countItem(candy) + " candies: one more than 15 was wanted; " + state(chest, chef));
			helper.assertTrue(chest.countItem(Items.SCULK) == 3 && chest.countItem(Items.HONEYCOMB) == 3, "3 sets of makings should be left; " + state(chest, chef));
			helper.assertTrue(helper.getTick() - reached[0] >= 400, "waiting to see he cooks no more");
		});
	}
}
