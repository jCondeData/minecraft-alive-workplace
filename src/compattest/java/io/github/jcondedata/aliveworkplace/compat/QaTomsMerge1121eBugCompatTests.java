package io.github.jcondedata.aliveworkplace.compat;

import io.github.jcondedata.aliveworkplace.build.SupplyContainers;
import io.github.jcondedata.aliveworkplace.registry.ModBlocks;
import java.lang.reflect.Field;
import java.util.List;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.TagParser;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;

/**
 * Failing QA tests (B1025's "not covered" case, lane-e-1121-1013): with Tom's Simple Storage 2.3.0, a chest that touches
 * the inventory connector and is also faced by a cable connector that the Inventory Configurator has configured (a
 * block filter on the cable connector) is read twice: the Storehouse and Tom's connector count its items double. Tom's
 * 2.3.0 has no separate filtered cable connector block; a filter is the Inventory Configurator's configuration of the
 * plain cable connector (an Item Filter in its slot, or only a priority / keep-last setting). Not registered.
 */
public class QaTomsMerge1121eBugCompatTests implements FabricGameTest {
	private static final String AREA = CompatGameTests.AREA;
	private static final BlockPos STOREHOUSE = QaTomsNetworks1108cCompatTests.STOREHOUSE;
	private static final BlockPos CONNECTOR = new BlockPos(6, 2, 12);
	private static final BlockPos CHEST = new BlockPos(7, 2, 12);
	private static final BlockPos CABLE = new BlockPos(6, 2, 13);
	private static final BlockPos CABLE_CONNECTOR = new BlockPos(7, 2, 13); // facing north, at the chest

	/** The chest touching the connector, faced by the cable connector, with {@code filterItem} in its configuration. */
	private static void setUp(GameTestHelper helper, ItemStack filterItem) {
		PartnerShowsCompatTests.clearLeftovers(helper);
		helper.setBlock(STOREHOUSE, ModBlocks.STOREHOUSE);
		helper.setBlock(CONNECTOR, TomsStorageCompatTests.block("inventory_connector"));
		helper.setBlock(CHEST, Blocks.CHEST);
		helper.setBlock(CABLE, TomsStorageCompatTests.block("inventory_cable"));
		helper.setBlock(CABLE_CONNECTOR, QaTomsNetworks1108cCompatTests.cableConnector(Direction.NORTH));
		QaTomsNetworks1108cCompatTests.joinUp(helper, CONNECTOR, CABLE, CABLE_CONNECTOR);
		QaTomsNetworks1108cCompatTests.put(helper, CHEST, new ItemStack(Items.COBBLESTONE, 10));
		// What the Inventory Configurator does on the cable connector: a block filter there, its slot holding filterItem.
		try {
			Class<?> blockFilter = Class.forName("com.tom.storagemod.inventory.BlockFilter");
			Object filter = blockFilter.getMethod("getOrCreateFilterAt", Level.class, BlockPos.class)
				.invoke(null, helper.getLevel(), helper.absolutePos(CABLE_CONNECTOR));
			Field slot = blockFilter.getField("filter");
			((SimpleContainer) slot.get(filter)).setItem(0, filterItem);
		} catch (ReflectiveOperationException e) {
			throw new IllegalStateException("can't configure Tom's cable connector: " + e, e);
		}
	}

	/** An Item Filter letting cobblestone through (allow list), as its screen saves it. */
	@SuppressWarnings({"unchecked", "rawtypes"})
	private static ItemStack cobblestoneFilter() {
		ItemStack stack = new ItemStack(BuiltInRegistries.ITEM.get(ResourceLocation.fromNamespaceAndPath("toms_storage", "item_filter")));
		DataComponentType type = BuiltInRegistries.DATA_COMPONENT_TYPE.get(ResourceLocation.fromNamespaceAndPath("toms_storage", "simple_item_filter"));
		if (type == null || stack.isEmpty()) {
			throw new IllegalStateException("no toms_storage:item_filter item or simple_item_filter component");
		}
		try {
			Object value = type.codecOrThrow().parse(NbtOps.INSTANCE,
				TagParser.parseTag("{stacks:[{id:\"minecraft:cobblestone\",count:1}],match_component:0b,allow_list:1b}")).getOrThrow();
			stack.set(type, value);
		} catch (Exception e) {
			throw new IllegalStateException("can't write the Item Filter: " + e, e);
		}
		return stack;
	}

	private static void check(GameTestHelper helper) {
		helper.runAfterDelay(60, () -> {
			Object self = selfOf(helper);
			helper.assertTrue(self != null && self.getClass().getSimpleName().equals("PlatformFilteredInventoryAccess"),
				"the cable connector isn't filtered (it reads through " + (self == null ? "nothing" : self.getClass().getSimpleName()) + ")");
			List<BlockPos> store = SupplyContainers.find(helper.getLevel(), helper.absolutePos(STOREHOUSE), null);
			helper.assertTrue(store.equals(List.of(helper.absolutePos(CONNECTOR))),
				"the store: " + QaTomsNetworks1108cCompatTests.rel(helper, store) + ", wanted the connector alone");
			long counted = SupplyContainers.count(helper.getLevel(), store, Items.COBBLESTONE);
			long shown = QaTomsNetworks1108cCompatTests.toms(helper, CONNECTOR, Items.COBBLESTONE);
			helper.assertTrue(counted == 10 && shown == 10, "the chest holds 10 cobblestone; the Storehouse counts " + counted
				+ " and Tom's connector shows " + shown);
			int free = SupplyContainers.freeSlots(helper.getLevel(), store);
			helper.assertTrue(free == 26, "the store has " + free + " free slots, wanted the one chest's 26");
			helper.succeed();
		});
	}

	private static Object selfOf(GameTestHelper helper) {
		Object be = helper.getLevel().getBlockEntity(helper.absolutePos(CABLE_CONNECTOR));
		try {
			Field f = be.getClass().getDeclaredField("self");
			f.setAccessible(true);
			return f.get(be);
		} catch (ReflectiveOperationException | NullPointerException e) {
			return null;
		}
	}

	/** The cable connector's configuration holds an Item Filter letting cobblestone through. */
	//$ gametest_ticks_batch AREA '200' '"qa_merge_filtered_cobble"'
	@GameTest(template = AREA, timeoutTicks = 200, batch = "qa_merge_filtered_cobble")
	public void aChestReachedThroughAFilteredCableConnectorCountsOnce(GameTestHelper helper) {
		setUp(helper, cobblestoneFilter());
		check(helper);
	}

	/** The cable connector configured with an empty filter slot (only its priority or keep-last set): lets all through. */
	//$ gametest_ticks_batch AREA '200' '"qa_merge_filtered_empty"'
	@GameTest(template = AREA, timeoutTicks = 200, batch = "qa_merge_filtered_empty")
	public void aChestReachedThroughAConfiguredUnfilteredCableConnectorCountsOnce(GameTestHelper helper) {
		setUp(helper, ItemStack.EMPTY);
		check(helper);
	}
}
