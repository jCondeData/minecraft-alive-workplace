package io.github.jcondedata.aliveworkplace.compat;

import io.github.jcondedata.aliveworkplace.registry.ModBlocks;
import io.github.jcondedata.aliveworkplace.registry.ModVillagers;
import io.github.jcondedata.aliveworkplace.work.Jobs;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.Container;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;

/**
 * QA for ROADMAP 38.11 (the Chef cooks the Campfire Pot recipes "from the store's ingredients, and stocks them"; the wiki:
 * 16 of each in the chests by his stove), written from the item, not the code, with a pot dish that is no making of
 * another pot recipe (Strawberry Sweet: red dye, a honey bottle and sugar), so B435's candy churn stays out of it: makings
 * for exactly one cook make one and leave the rest; 16 already in the chests means none cooked and nothing taken, 15
 * means exactly one more; a Chef saved and loaded again (a restart) still cooks them.
 */
public class QaCampfirePotStockCompatTests implements FabricGameTest {
	private static final String AREA = CompatGameTests.AREA;
	private static final BlockPos STATION = new BlockPos(2, 2, 2);
	private static final BlockPos CHEST = new BlockPos(2, 2, 4);
	private static final BlockPos CHEF_AT = new BlockPos(3, 2, 3);

	private static Item sweet() {
		Item item = BuiltInRegistries.ITEM.get(ResourceLocation.fromNamespaceAndPath("cobblemon", "strawberry_sweet"));
		if (item == Items.AIR) {
			throw new AssertionError("no cobblemon:strawberry_sweet");
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
		Villager chef = helper.spawn(EntityType.VILLAGER, CHEF_AT);
		Jobs.employ(helper.getLevel(), chef, helper.absolutePos(STATION), ModVillagers.KITCHEN_STOVE_POI, ModVillagers.CHEF);
		return chef;
	}

	/** Every item the Chef holds of {@code item} (what he carries counts as not yet stocked). */
	private static int carried(Villager chef, Item item) {
		return chef.getInventory().countItem(item);
	}

	/** Makings for exactly one cook (3 red dye and 3 sugar, 1 honey bottle): one sweet, and 2 dye and 2 sugar left, for good. */
	//$ gametest_ticks_batch AREA '3000' '"qa_pot_stock_one"'
	@GameTest(template = AREA, timeoutTicks = 3000, batch = "qa_pot_stock_one")
	public void makingsForOneCookMakeOneSweet(GameTestHelper helper) {
		PartnerShowsCompatTests.clearLeftovers(helper);
		Item sweet = sweet();
		Container chest = chest(helper, new ItemStack(Items.RED_DYE, 3), new ItemStack(Items.HONEY_BOTTLE, 1), new ItemStack(Items.SUGAR, 3));
		Villager chef = chef(helper);
		helper.startSequence()
			.thenWaitUntil(() -> helper.assertTrue(chest.countItem(sweet) == 1, chest.countItem(sweet) + " sweets in the chest"))
			.thenIdle(600)
			.thenExecute(() -> {
				helper.assertTrue(chest.countItem(sweet) + carried(chef, sweet) == 1, (chest.countItem(sweet) + carried(chef, sweet)) + " sweets from one honey bottle");
				helper.assertTrue(chest.countItem(Items.RED_DYE) + carried(chef, Items.RED_DYE) == 2 && chest.countItem(Items.SUGAR) + carried(chef, Items.SUGAR) == 2,
					chest.countItem(Items.RED_DYE) + " red dye and " + chest.countItem(Items.SUGAR) + " sugar left, expected 2 and 2");
				helper.assertTrue(chest.countItem(Items.HONEY_BOTTLE) == 0, "the honey bottle is still there");
			})
			.thenSucceed();
	}

	/** 16 sweets already in the chest by the stove: he cooks none, and the makings for two stay where they are. */
	//$ gametest_ticks_batch AREA '1600' '"qa_pot_stock_full"'
	@GameTest(template = AREA, timeoutTicks = 1600, batch = "qa_pot_stock_full")
	public void sixteenInTheChestMeansNoneCooked(GameTestHelper helper) {
		PartnerShowsCompatTests.clearLeftovers(helper);
		Item sweet = sweet();
		Container chest = chest(helper, new ItemStack(sweet, 16), new ItemStack(Items.RED_DYE, 2), new ItemStack(Items.HONEY_BOTTLE, 2),
			new ItemStack(Items.SUGAR, 2));
		Villager chef = chef(helper);
		helper.runAfterDelay(1400, () -> {
			helper.assertTrue(chest.countItem(sweet) == 16 && carried(chef, sweet) == 0, chest.countItem(sweet) + " sweets in the chest, "
				+ carried(chef, sweet) + " carried, with 16 kept");
			helper.assertTrue(chest.countItem(Items.HONEY_BOTTLE) == 2 && chest.countItem(Items.RED_DYE) == 2,
				"makings taken with 16 in stock: " + chest.countItem(Items.HONEY_BOTTLE) + " honey bottles left");
			helper.succeed();
		});
	}

	/** 15 sweets in the chest and the makings for three: he tops it up to exactly 16, and two cooks' makings stay. */
	//$ gametest_ticks_batch AREA '3000' '"qa_pot_stock_fifteen"'
	@GameTest(template = AREA, timeoutTicks = 3000, batch = "qa_pot_stock_fifteen")
	public void fifteenInTheChestGetsOneMore(GameTestHelper helper) {
		PartnerShowsCompatTests.clearLeftovers(helper);
		Item sweet = sweet();
		Container chest = chest(helper, new ItemStack(sweet, 15), new ItemStack(Items.RED_DYE, 3), new ItemStack(Items.HONEY_BOTTLE, 3),
			new ItemStack(Items.SUGAR, 3));
		Villager chef = chef(helper);
		helper.startSequence()
			.thenWaitUntil(() -> helper.assertTrue(chest.countItem(sweet) >= 16, chest.countItem(sweet) + " sweets in the chest"))
			.thenIdle(600)
			.thenExecute(() -> {
				int all = chest.countItem(sweet) + carried(chef, sweet);
				helper.assertTrue(all == 16, all + " sweets with 16 kept (15 to start)");
				helper.assertTrue(chest.countItem(Items.HONEY_BOTTLE) == 2, chest.countItem(Items.HONEY_BOTTLE) + " honey bottles left, expected 2");
			})
			.thenSucceed();
	}

	/** The Chef saved and read back (a restart, his chunk reloaded) still cooks the pot dish from the makings. */
	//$ gametest_ticks_batch AREA '3000' '"qa_pot_stock_reload"'
	@GameTest(template = AREA, timeoutTicks = 3000, batch = "qa_pot_stock_reload")
	public void aChefLoadedAgainStillCooksThePotDish(GameTestHelper helper) {
		PartnerShowsCompatTests.clearLeftovers(helper);
		Item sweet = sweet();
		Villager first = chef(helper);
		CompoundTag saved = first.saveWithoutId(new CompoundTag());
		first.discard();
		Villager[] chef = new Villager[1];
		helper.runAfterDelay(2, () -> {
			Villager back = EntityType.VILLAGER.create(helper.getLevel());
			helper.assertTrue(back != null, "no villager made");
			back.load(saved);
			helper.getLevel().addFreshEntity(back);
			chef[0] = back;
			helper.assertTrue(back.getVillagerData().getProfession() == ModVillagers.CHEF, "loaded as " + back.getVillagerData().getProfession());
		});
		Container[] chest = new Container[1];
		helper.runAfterDelay(4, () -> chest[0] = chest(helper, new ItemStack(Items.RED_DYE, 2), new ItemStack(Items.HONEY_BOTTLE, 2), new ItemStack(Items.SUGAR, 2)));
		helper.succeedWhen(() -> {
			helper.assertTrue(chest[0] != null && chef[0] != null, "not set up yet");
			helper.assertTrue(chest[0].countItem(sweet) == 2, chest[0].countItem(sweet) + " sweets from the reloaded Chef");
			helper.assertTrue(chest[0].countItem(Items.HONEY_BOTTLE) == 0, "the honey bottles are left");
		});
	}
}
