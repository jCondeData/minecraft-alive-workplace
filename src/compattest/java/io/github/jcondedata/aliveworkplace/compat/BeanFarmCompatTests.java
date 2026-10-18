package io.github.jcondedata.aliveworkplace.compat;

import io.github.jcondedata.aliveworkplace.craft.CuisineDishes;
import io.github.jcondedata.aliveworkplace.farm.BeanFarms;
import io.github.jcondedata.aliveworkplace.farm.Fields;
import io.github.jcondedata.aliveworkplace.registry.ModAttachments;
import io.github.jcondedata.aliveworkplace.work.Jobs;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.TagKey;
import net.minecraft.world.Container;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.village.poi.PoiTypes;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.npc.VillagerProfession;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.level.levelgen.structure.BoundingBox;

/**
 * ROADMAP 38.9 with the real Cobblecuisine 2.0.1: a farmer sows beans while the store is short of a colour a menu dish
 * needs, cuts {@code cobblecuisine:bean_crop} only when fully grown and puts the beans in the store; nothing wants
 * beans, or the switch is off, and none are sown.
 */
public class BeanFarmCompatTests implements FabricGameTest {
	private static final String AREA = CompatGameTests.AREA;
	private static final BlockPos COMPOSTER = new BlockPos(2, 2, 2);
	private static final BlockPos CHEST = new BlockPos(2, 2, 4);
	/** A 3x3 field of farmland, watered from its middle. */
	private static final BlockPos FIELD_A = new BlockPos(5, 1, 5);
	private static final BlockPos FIELD_B = new BlockPos(7, 1, 7);
	private static final BlockPos WATER = new BlockPos(6, 1, 6);
	private static final TagKey<Item> BEANS = TagKey.create(Registries.ITEM, ResourceLocation.fromNamespaceAndPath("cobblecuisine", "beans"));

	static Item cc(String path) {
		Item item = BuiltInRegistries.ITEM.get(ResourceLocation.fromNamespaceAndPath("cobblecuisine", path));
		if (item == Items.AIR) {
			throw new AssertionError("no cobblecuisine:" + path);
		}
		return item;
	}

	static Block crop() {
		return BuiltInRegistries.BLOCK.get(ResourceLocation.fromNamespaceAndPath("cobblecuisine", "bean_crop"));
	}

	static IntegerProperty age() {
		return (IntegerProperty) crop().getStateDefinition().getProperty("age");
	}

	static int maxAge() {
		return age().getPossibleValues().stream().mapToInt(Integer::intValue).max().orElseThrow();
	}

	static BlockState cropAt(int age) {
		return crop().defaultBlockState().setValue(age(), age);
	}

	private static List<BlockPos> spots() {
		List<BlockPos> out = new ArrayList<>();
		for (BlockPos p : BlockPos.betweenClosed(FIELD_A, FIELD_B)) {
			if (!p.equals(WATER)) {
				out.add(p.immutable());
			}
		}
		return out;
	}

	/** The field (bare farmland, or {@code planted} on it) and a farmer on it, with {@code chest} by the composter. */
	static Villager farmer(GameTestHelper helper, BlockState planted, ItemStack... chest) {
		PartnerShowsCompatTests.clearLeftovers(helper);
		ServerLevel level = helper.getLevel();
		helper.setDayTime(2000);
		helper.setBlock(WATER, Blocks.WATER);
		for (BlockPos p : spots()) {
			helper.setBlock(p, Blocks.FARMLAND);
			helper.setBlock(p.above(), planted);
		}
		helper.setBlock(COMPOSTER, Blocks.COMPOSTER);
		helper.setBlock(CHEST, Blocks.CHEST);
		Container c = helper.getBlockEntity(CHEST);
		for (int i = 0; i < chest.length; i++) {
			c.setItem(i, chest[i]);
		}
		Villager villager = helper.spawn(EntityType.VILLAGER, new BlockPos(3, 2, 3));
		Jobs.employ(level, villager, helper.absolutePos(COMPOSTER), PoiTypes.FARMER, VillagerProfession.FARMER);
		Fields.start(level, villager, BoundingBox.fromCorners(helper.absolutePos(FIELD_A), helper.absolutePos(FIELD_B)));
		return villager;
	}

	private static int beansIn(Container chest) {
		int n = 0;
		for (int i = 0; i < chest.getContainerSize(); i++) {
			if (chest.getItem(i).is(BEANS)) {
				n += chest.getItem(i).getCount();
			}
		}
		return n;
	}

	/** The dishes on the menu want beans (the green bean of the salad among them), from the recipes, all in the tag. */
	//$ gametest EMPTY_STRUCTURE
	@GameTest(template = EMPTY_STRUCTURE)
	public void menuWantsTheBeansOfItsRecipes(GameTestHelper helper) {
		Set<Item> wanted = BeanFarms.wanted(helper.getLevel());
		helper.assertTrue(wanted.contains(cc("green_bean")), "the salad's green bean isn't wanted: " + wanted);
		for (Item bean : wanted) {
			helper.assertTrue(new ItemStack(bean).is(BEANS), bean + " is wanted but isn't a bean");
		}
		helper.assertTrue(BeanFarms.isRipe(cropAt(maxAge())), "a full-grown bean crop isn't ripe");
		helper.assertTrue(!BeanFarms.isRipe(cropAt(maxAge() - 1)), "a bean crop a stage short is ripe");
		helper.assertTrue(!BeanFarms.isRipe(Blocks.WHEAT.defaultBlockState()), "wheat is a bean crop");
		CuisineDishes.ENABLED = false;
		try {
			helper.assertTrue(BeanFarms.wanted(helper.getLevel()).isEmpty(), "beans wanted with the dishes off the menu");
		} finally {
			CuisineDishes.ENABLED = true;
		}
		helper.succeed();
	}

	/** The Done when: the store holds no beans and the menu wants them, so the farmer sows the field from the chest. */
	//$ gametest_ticks_batch AREA '2400' '"bean_farm_plants"'
	@GameTest(template = AREA, timeoutTicks = 2400, batch = "bean_farm_plants")
	public void farmerPlantsBeansTheMenuWants(GameTestHelper helper) {
		farmer(helper, Blocks.AIR.defaultBlockState(), new ItemStack(cc("bean_seeds"), 16));
		helper.succeedWhen(() -> {
			for (BlockPos p : spots()) {
				helper.assertBlockPresent(crop(), p.above());
			}
		});
	}

	/** The Done when: the store holds 16 of every colour the menu needs, so the seeds stay in the chest. */
	//$ gametest_ticks_batch AREA '1200' '"bean_farm_stocked"'
	@GameTest(template = AREA, timeoutTicks = 1200, batch = "bean_farm_stocked")
	public void noBeansPlantedWhenTheStoreHasEnough(GameTestHelper helper) {
		List<ItemStack> chest = new ArrayList<>();
		chest.add(new ItemStack(cc("bean_seeds"), 16));
		for (Item bean : BeanFarms.wanted(helper.getLevel())) {
			chest.add(new ItemStack(bean, BeanFarms.LOW));
		}
		farmer(helper, Blocks.AIR.defaultBlockState(), chest.toArray(ItemStack[]::new));
		helper.runAtTickTime(900, () -> {
			for (BlockPos p : spots()) {
				helper.assertBlockNotPresent(crop(), p.above());
			}
			Container c = helper.getBlockEntity(CHEST);
			helper.assertTrue(c.countItem(cc("bean_seeds")) == 16, "seeds left the chest: " + c.countItem(cc("bean_seeds")));
			helper.succeed();
		});
	}

	/** Switch {@code beanFarms} off: no beans sown, though the store has none. */
	//$ gametest_ticks_batch AREA '1200' '"bean_farm_off"'
	@GameTest(template = AREA, timeoutTicks = 1200, batch = "bean_farm_off")
	public void noBeansWithTheSwitchOff(GameTestHelper helper) {
		BeanFarms.ENABLED = false;
		farmer(helper, Blocks.AIR.defaultBlockState(), new ItemStack(cc("bean_seeds"), 16));
		helper.runAtTickTime(900, () -> {
			try {
				for (BlockPos p : spots()) {
					helper.assertBlockNotPresent(crop(), p.above());
				}
			} finally {
				BeanFarms.ENABLED = true;
			}
			helper.succeed();
		});
	}

	/**
	 * The Done when: of a field half ripe, half a stage short, only the ripe beans are cut, their beans go in the chest,
	 * and (the menu still short, the seeds from the harvest) they're sown again. No seeds in the store to start with.
	 */
	//$ gametest_ticks_batch AREA '3000' '"bean_farm_harvest"'
	@GameTest(template = AREA, timeoutTicks = 3000, batch = "bean_farm_harvest")
	public void farmerHarvestsOnlyRipeBeansIntoTheStore(GameTestHelper helper) {
		Villager villager = farmer(helper, cropAt(maxAge()));
		List<BlockPos> green = new ArrayList<>();
		List<BlockPos> all = spots();
		for (int i = 0; i < all.size(); i += 2) {
			green.add(all.get(i).above());
			helper.setBlock(all.get(i).above(), cropAt(1));
		}
		int ripe = all.size() - green.size();
		helper.succeedWhen(() -> {
			int harvested = ModAttachments.FARM_HARVESTED.getOrElse(villager, 0);
			helper.assertTrue(harvested >= ripe, "only " + harvested + " harvested");
			Container c = helper.getBlockEntity(CHEST);
			helper.assertTrue(beansIn(c) >= ripe, "only " + beansIn(c) + " beans in the chest");
			for (BlockPos p : green) {
				helper.assertBlockPresent(crop(), p);
				int a = helper.getBlockState(p).getValue(age());
				helper.assertTrue(a < maxAge(), "an unripe bean at " + p + " is full-grown (age " + a + ")");
			}
			for (BlockPos p : all) {
				helper.assertBlockPresent(crop(), p.above()); // the cut ones sown again
			}
		});
	}
}
