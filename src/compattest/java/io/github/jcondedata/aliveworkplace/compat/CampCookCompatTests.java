package io.github.jcondedata.aliveworkplace.compat;

import com.cobblemon.mod.common.block.entity.CampfireBlockEntity;
import io.github.jcondedata.aliveworkplace.camp.CampCooks;
import io.github.jcondedata.aliveworkplace.farm.Fields;
import io.github.jcondedata.aliveworkplace.hall.VillageNeeds;
import io.github.jcondedata.aliveworkplace.people.Diet;
import io.github.jcondedata.aliveworkplace.registry.ModAttachments;
import io.github.jcondedata.aliveworkplace.registry.ModBlocks;
import io.github.jcondedata.aliveworkplace.registry.ModVillagers;
import io.github.jcondedata.aliveworkplace.store.Porters;
import io.github.jcondedata.aliveworkplace.store.StockOrders;
import io.github.jcondedata.aliveworkplace.work.Jobs;
import io.github.jcondedata.aliveworkplace.work.Partners;
import io.github.jcondedata.aliveworkplace.work.Stations;
import java.util.List;
import java.util.Set;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.village.poi.PoiTypes;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.npc.VillagerProfession;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CropBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.structure.BoundingBox;

/** ROADMAP 28.8 with the real Cobblemon: the Camp Cook cooks in a real Campfire Pot, fills stock orders and feeds the village. */
public class CampCookCompatTests implements FabricGameTest {
	private static final String AREA = CompatGameTests.AREA;
	private static final BlockPos POT = new BlockPos(2, 2, 2);
	private static final BlockPos CHEST = new BlockPos(2, 2, 4);
	private static final BlockPos STOREHOUSE = new BlockPos(7, 2, 2);
	private static final BlockPos STORE_CHEST = new BlockPos(7, 2, 4);

	private static ResourceLocation c(String path) {
		return ResourceLocation.fromNamespaceAndPath("cobblemon", path);
	}

	private static Item item(String path) {
		return BuiltInRegistries.ITEM.get(c(path));
	}

	private static ItemStack stack(String path, int count) {
		return new ItemStack(item(path), count);
	}

	/** Cobblemon's campfire with a red pot on it, as a player makes it. */
	private static void pot(GameTestHelper helper) {
		Block campfire = BuiltInRegistries.BLOCK.get(c("campfire"));
		helper.setBlock(POT, campfire.defaultBlockState());
		helper.assertTrue(helper.getBlockEntity(POT) instanceof CampfireBlockEntity, "no campfire pot block entity");
		((CampfireBlockEntity) helper.getBlockEntity(POT)).setPotItem(stack("campfire_pot_red", 1));
	}

	private static Villager cook(GameTestHelper helper, ItemStack... chest) {
		helper.setDayTime(2000);
		pot(helper);
		helper.setBlock(CHEST, Blocks.CHEST);
		Container c = helper.getBlockEntity(CHEST);
		for (int i = 0; i < chest.length; i++) {
			c.setItem(i, chest[i]);
		}
		Villager villager = helper.spawn(EntityType.VILLAGER, new BlockPos(3, 2, 3));
		Jobs.employ(helper.getLevel(), villager, helper.absolutePos(POT), ModVillagers.CAMPFIRE_POT_POI, ModVillagers.CAMP_COOK);
		helper.assertTrue(CampCooks.isCook(villager), "not a camp cook");
		return villager;
	}

	/** What she says she's doing, and where she is (for failure messages). */
	private static String said(GameTestHelper helper, Villager cook) {
		io.github.jcondedata.aliveworkplace.work.WorkerStatus.Entry e = io.github.jcondedata.aliveworkplace.work.WorkerStatus.get(cook,
			helper.getLevel().getGameTime());
		return (e == null ? "no status" : e.line().getString()) + " at " + helper.relativePos(cook.blockPosition()) + ", job "
			+ cook.getVillagerData().getProfession() + ", activity " + cook.getBrain().getActiveNonCoreActivity().orElse(null);
	}

	private static Container potContainer(GameTestHelper helper) {
		return (Container) helper.getBlockEntity(POT);
	}

	/**
	 * The Done when: the makings of a Poké Snack in the chest by a real pot; she puts them in the pot through its
	 * container, shuts the lid (the pot's own state), the pot cooks it, and the snack comes out into the chest with the
	 * makings gone.
	 */
	//$ gametest_ticks_batch AREA '2400' '"camp_cook"'
	@GameTest(template = AREA, timeoutTicks = 2400, batch = "camp_cook")
	public void sheCooksAPokeSnackInARealPot(GameTestHelper helper) {
		Villager cook = cook(helper, stack("moomoo_milk", 3), new ItemStack(Items.HONEY_BOTTLE, 2), stack("vivichoke", 1),
			stack("hearty_grains", 3));
		CampCooks.Pot pot = CampCooks.pot();
		helper.assertTrue(pot != null, "no pot extension");
		boolean[] shut = {false};
		helper.onEachTick(() -> shut[0] |= pot.lidShut(helper.getBlockState(POT)));
		helper.succeedWhen(() -> {
			Container chest = helper.getBlockEntity(CHEST);
			helper.assertTrue(shut[0], "the lid never shut");
			helper.assertTrue(chest.countItem(item("poke_snack")) >= 1, "no Poké Snack in the chest; status "
				+ ModAttachments.DISHES_COOKED.getOrElse(cook, 0) + " cooked, pot " + potContainer(helper).getItem(0) + "; she says " + said(helper, cook));
			helper.assertTrue(chest.countItem(item("hearty_grains")) == 0 && chest.countItem(item("vivichoke")) == 0,
				"the makings are still in the chest");
			for (int slot = 1; slot <= 9; slot++) {
				helper.assertTrue(potContainer(helper).getItem(slot).isEmpty(), "makings left in the pot, slot " + slot);
			}
			helper.assertTrue(ModAttachments.DISHES_COOKED.getOrElse(cook, 0) >= 1, "no dish counted");
		});
	}

	/** A Storehouse's stock order for Lava Cookies: she cooks them (an order-only dish) and they end up in its chest. */
	//$ gametest_ticks_batch AREA '2400' '"camp_cook_order"'
	@GameTest(template = AREA, timeoutTicks = 2400, batch = "camp_cook_order")
	public void aStockOrderForLavaCookiesIsFilled(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		helper.setBlock(STOREHOUSE, ModBlocks.STOREHOUSE);
		helper.setBlock(STORE_CHEST, Blocks.CHEST);
		StockOrders.cycle(level, helper.absolutePos(STOREHOUSE), item("lava_cookie"));
		helper.assertTrue(StockOrders.of(level, helper.absolutePos(STOREHOUSE)).containsKey(item("lava_cookie")), "no order");
		Villager cook = cook(helper, stack("hearty_grains", 2), new ItemStack(Items.DRIED_KELP), new ItemStack(Items.MAGMA_CREAM));
		helper.succeedWhen(() -> {
			Container store = helper.getBlockEntity(STORE_CHEST);
			helper.assertTrue(store.countItem(item("lava_cookie")) >= 4, "the order isn't filled: " + store.countItem(item("lava_cookie"))
				+ " in the store, " + ModAttachments.DISHES_COOKED.getOrElse(cook, 0) + " cooked, bag "
				+ ModAttachments.BUILDER_BAG.getOrCreate(cook).stacks() + "; she says " + said(helper, cook));
		});
	}

	/** Lava Cookies are on the menu only for orders: with no order she leaves the makings alone. */
	//$ gametest_ticks_batch AREA '400' '"camp_cook_no_order"'
	@GameTest(template = AREA, timeoutTicks = 400, batch = "camp_cook_no_order")
	public void noOrderNoLavaCookies(GameTestHelper helper) {
		cook(helper, stack("hearty_grains", 2), new ItemStack(Items.DRIED_KELP), new ItemStack(Items.MAGMA_CREAM));
		helper.runAfterDelay(300, () -> {
			Container chest = helper.getBlockEntity(CHEST);
			helper.assertTrue(chest.countItem(item("hearty_grains")) == 2 && chest.countItem(Items.MAGMA_CREAM) == 1,
				"she cooked an order-only dish nobody ordered");
			helper.succeed();
		});
	}

	/** A villager eats a Ponigiri from the store, and Diet records it. */
	//$ gametest 'FabricGameTest.EMPTY_STRUCTURE'
	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
	public void aVillagerEatsAPonigiriFromTheStore(GameTestHelper helper) {
		helper.setBlock(new BlockPos(0, 1, 0), Blocks.CHEST);
		Container chest = helper.getBlockEntity(new BlockPos(0, 1, 0));
		chest.setItem(0, stack("ponigiri", 1));
		for (String meal : List.of("ponigiri", "leek_and_potato_stew", "smoked_tail_curry", "open_faced_sandwich", "vivichoke_dip", "sinister_tea")) {
			helper.assertTrue(VillageNeeds.isMeal(stack(meal, 1)), meal + " isn't a meal");
		}
		Villager villager = helper.spawn(EntityType.VILLAGER, new BlockPos(1, 1, 1));
		helper.assertTrue(VillageNeeds.eat(helper.getLevel(), villager, List.of(helper.absolutePos(new BlockPos(0, 1, 0)))), "nothing eaten");
		helper.assertTrue(chest.countItem(item("ponigiri")) == 0, "the Ponigiri is still in the store");
		helper.assertTrue(Diet.hadLately(villager, stack("ponigiri", 1)), "Diet didn't record the Ponigiri");
		helper.succeed();
	}

	/** A farmer harvests ripe Hearty Grains and plants them right back, and sows Vivichoke seeds from the chest. */
	//$ gametest_ticks_batch AREA '3000' '"camp_cook_farm"'
	@GameTest(template = AREA, timeoutTicks = 3000, batch = "camp_cook_farm")
	public void aFarmerHarvestsAndReplantsHeartyGrains(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		helper.setDayTime(2000);
		Block grains = BuiltInRegistries.BLOCK.get(c("hearty_grains"));
		BlockPos composter = new BlockPos(2, 2, 2);
		BlockPos chestPos = new BlockPos(2, 2, 4);
		List<BlockPos> rows = List.of(new BlockPos(8, 1, 8), new BlockPos(9, 1, 8), new BlockPos(10, 1, 8));
		BlockPos bare = new BlockPos(11, 1, 8);
		for (BlockPos p : rows) {
			helper.setBlock(p, Blocks.FARMLAND);
			CropBlock crop = (CropBlock) grains;
			BlockState lower = crop.getStateForAge(crop.getMaxAge());
			helper.setBlock(p.above(), lower);
			helper.setBlock(p.above(2), lower.setValue(net.minecraft.world.level.block.state.properties.BlockStateProperties.DOUBLE_BLOCK_HALF,
				net.minecraft.world.level.block.state.properties.DoubleBlockHalf.UPPER));
		}
		helper.setBlock(bare, Blocks.FARMLAND);
		helper.setBlock(new BlockPos(12, 1, 8), Blocks.WATER);
		helper.setBlock(composter, Blocks.COMPOSTER);
		helper.setBlock(chestPos, Blocks.CHEST);
		Container chest = helper.getBlockEntity(chestPos);
		chest.setItem(0, stack("vivichoke_seeds", 1));
		Villager farmer = helper.spawn(EntityType.VILLAGER, new BlockPos(3, 2, 3));
		Jobs.employ(level, farmer, helper.absolutePos(composter), PoiTypes.FARMER, VillagerProfession.FARMER);
		Fields.start(level, farmer, BoundingBox.fromCorners(helper.absolutePos(new BlockPos(8, 1, 8)), helper.absolutePos(new BlockPos(12, 1, 8))));
		helper.succeedWhen(() -> {
			int harvested = ModAttachments.FARM_HARVESTED.getOrElse(farmer, 0);
			helper.assertTrue(harvested >= 3, "only " + harvested + " harvested");
			for (BlockPos p : rows) {
				BlockState now = helper.getBlockState(p.above());
				helper.assertTrue(now.is(grains) && !((CropBlock) grains).isMaxAge(now), "Hearty Grains not replanted at " + p + ": " + now);
			}
			helper.assertTrue(helper.getBlockState(bare.above()).is(BuiltInRegistries.BLOCK.get(c("vivichoke_seeds"))), "Vivichoke not sown");
		});
	}

	/**
	 * Hearty Grains make a villager by a Campfire Pot a Camp Cook (not with the switch off), a jobless villager never takes
	 * the pot by itself, and her trades, partner types and porter rules.
	 */
	//$ gametest_ticks AREA '200'
	@GameTest(template = AREA, timeoutTicks = 200)
	public void heartyGrainsMakeACampCook(GameTestHelper helper) {
		pot(helper);
		helper.assertTrue(Stations.at(BuiltInRegistries.BLOCK.get(c("campfire"))).map(s -> !s.byItself() && s.has(ModVillagers.CAMP_COOK))
			.orElse(false), "a jobless villager could take the pot by itself");
		Villager villager = helper.spawn(EntityType.VILLAGER, new BlockPos(3, 2, 2));
		ServerPlayer player = helper.makeMockServerPlayerInLevel();
		player.setPos(helper.absoluteVec(new net.minecraft.world.phys.Vec3(3.5, 2, 3.5)));
		try {
			CampCooks.ENABLED = false;
			helper.assertTrue(!CampCooks.isGrains(stack("hearty_grains", 1)), "Hearty Grains still pick a job with the switch off");
			helper.assertTrue(Stations.choose(player, villager, stack("hearty_grains", 1)) == InteractionResult.PASS
				&& villager.getVillagerData().getProfession() == VillagerProfession.NONE, "switched off, Hearty Grains still gave a job");
		} finally {
			CampCooks.ENABLED = true;
		}
		Stations.choose(player, villager, stack("hearty_grains", 1));
		helper.assertTrue(villager.getVillagerData().getProfession() == ModVillagers.CAMP_COOK,
			"Hearty Grains made a " + villager.getVillagerData().getProfession());
		helper.assertTrue(Partners.types(ModVillagers.CAMP_COOK).equals(Set.of("fire", "normal")), "partner types");
		helper.assertTrue(Porters.keeps(ModVillagers.CAMP_COOK, stack("ponigiri", 1), false) == 16, "porters leave 16 Ponigiri");
		helper.assertTrue(Porters.keeps(ModVillagers.CAMP_COOK, stack("lava_cookie", 1), false) == 0, "order dishes go to the store");
		helper.assertTrue(Porters.keeps(ModVillagers.CAMP_COOK, stack("hearty_grains", 1), false) == Porters.ALL, "makings stay");
		// The shipped menu: every dish the spec lists is an item Cobblemon has, and the pot has a recipe for it.
		helper.assertTrue(CampCooks.menu().size() >= 60, "menu has " + CampCooks.menu().size() + " dishes");
		for (CampCooks.Dish dish : CampCooks.menu()) {
			Item it = BuiltInRegistries.ITEM.getOptional(dish.item()).orElse(null);
			helper.assertTrue(it != null && it != Items.AIR, dish.name() + ": no item " + dish.item());
			helper.assertTrue(!CampCooks.pot().recipes(helper.getLevel(), it).isEmpty(), dish.name() + ": the pot has no recipe for it");
		}
		helper.succeed();
	}

	/** The pot taken off mid-job: she stops cooking with a warning, nothing is lost, and with a pot back she goes on. */
	//$ gametest_ticks_batch AREA '2400' '"camp_cook_broken"'
	@GameTest(template = AREA, timeoutTicks = 2400, batch = "camp_cook_broken")
	public void thePotBrokenMidJob(GameTestHelper helper) {
		Villager cook = cook(helper, stack("hearty_grains", 3), new ItemStack(Items.DRIED_KELP));
		CampCooks.Pot pot = CampCooks.pot();
		boolean[] broken = {false};
		helper.onEachTick(() -> {
			if (!broken[0] && pot.lidShut(helper.getBlockState(POT))) {
				broken[0] = true;
				helper.setBlock(POT, Blocks.CAMPFIRE); // the pot (with what's in it) is gone: a plain campfire is left
			}
		});
		helper.runAfterDelay(2000, () -> {
			helper.assertTrue(broken[0], "she never shut the lid");
			helper.assertTrue(cook.isAlive() && ModAttachments.DISHES_COOKED.getOrElse(cook, 0) == 0,
				"counted a dish from a pot that was gone: " + ModAttachments.DISHES_COOKED.getOrElse(cook, 0));
			helper.assertTrue(ModAttachments.BUILDER_BAG.getOrCreate(cook).isEmpty(), "she carries something out of nowhere");
			helper.succeed();
		});
	}

	/** Her dish count survives a save and reload of the villager. */
	//$ gametest 'FabricGameTest.EMPTY_STRUCTURE'
	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
	public void herDishCountIsSaved(GameTestHelper helper) {
		Villager cook = helper.spawn(EntityType.VILLAGER, new BlockPos(1, 2, 1));
		ModAttachments.DISHES_COOKED.set(cook, 7);
		net.minecraft.nbt.CompoundTag tag = new net.minecraft.nbt.CompoundTag();
		cook.saveWithoutId(tag);
		Villager copy = EntityType.VILLAGER.create(helper.getLevel());
		copy.load(tag);
		helper.assertTrue(ModAttachments.DISHES_COOKED.getOrElse(copy, 0) == 7, "dishes cooked not saved");
		helper.succeed();
	}
}
