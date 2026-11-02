package io.github.jcondedata.aliveworkplace.compat;

import io.github.jcondedata.aliveworkplace.AliveWorkplace;
import io.github.jcondedata.aliveworkplace.compat.MachineWorkCompatTests.Kind;
import io.github.jcondedata.aliveworkplace.mechanic.MachineWork;
import io.github.jcondedata.aliveworkplace.mechanic.MachineWorkFiles;
import io.github.jcondedata.aliveworkplace.mechanic.MechanicWork;
import io.github.jcondedata.aliveworkplace.platform.ItemStores;
import io.github.jcondedata.aliveworkplace.platform.Platform;
import java.util.List;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Container;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;

/**
 * QA (lane-e-1102-1323), B820 from its spec, with the real Create: the Mechanic asks whether a millstone's flour fits the
 * store, so a store with a part stack in every slot takes what it has room for, and only that. The edges
 * MachineWorkCompatTests skips: room only for other items, room for part of the output, room for exactly the output split
 * over two stacks, and a Create Item Vault as the store (part stacks of many kinds in every slot, with and without a
 * flour stack among them). Every flour is counted: store, millstone, ground.
 */
public class QaStoreRoom1102eCompatTests implements FabricGameTest {
	private static final String AREA = CompatGameTests.AREA;
	private static final BlockPos CHEST = new BlockPos(2, 2, 4);

	/** Wrong-kind room: one cobblestone in each slot (lots of room, none for flour): FULL, and the flour stays put. */
	//$ gametest_ticks_batch AREA '200' '"qa_store_room_mech_wrong_kind"'
	@GameTest(template = AREA, timeoutTicks = 200, batch = "qa_store_room_mech_wrong_kind")
	public void theMechanicKeepsTheFlourForAStoreWithRoomOnlyForCobblestone(GameTestHelper helper) {
		Container chest = setup(helper);
		for (int i = 0; i < chest.getContainerSize(); i++) {
			chest.setItem(i, new ItemStack(Items.COBBLESTONE, 1));
		}
		BlockPos mill = mill(helper, 32);
		MechanicWork.Tended done = tend(helper, helper.absolutePos(CHEST), mill);
		helper.assertTrue(done == MechanicWork.Tended.FULL, "seeing to the millstone by a store with room only for cobblestone: " + done + ", not FULL");
		helper.assertTrue(count(chest, flour()) == 0, "the store took " + count(chest, flour()) + " flour without a slot for it");
		helper.assertTrue(count(chest, Items.COBBLESTONE) == 27, "the store's cobblestone changed to " + count(chest, Items.COBBLESTONE));
		helper.assertTrue(inMill(helper, mill) == 32, "the millstone holds " + inMill(helper, mill) + " of its 32 flour");
		helper.assertTrue(dropped(helper) == 0, dropped(helper) + " items were dropped on the ground");
		MachineWorkCompatTests.clearMachines(helper);
		helper.succeed();
	}

	/** Partial fit: 40 flour in one slot (room 24), the rest full of cobblestone; 32 flour made: 24 go in, 8 stay in the millstone. */
	//$ gametest_ticks_batch AREA '200' '"qa_store_room_mech_partial"'
	@GameTest(template = AREA, timeoutTicks = 200, batch = "qa_store_room_mech_partial")
	public void theMechanicMovesOnlyTheFlourThatFits(GameTestHelper helper) {
		Container chest = setup(helper);
		chest.setItem(0, new ItemStack(flour(), 40));
		for (int i = 1; i < chest.getContainerSize(); i++) {
			chest.setItem(i, new ItemStack(Items.COBBLESTONE, 64));
		}
		BlockPos mill = mill(helper, 32);
		MechanicWork.Tended done = tend(helper, helper.absolutePos(CHEST), mill);
		int stored = count(chest, flour());
		int left = inMill(helper, mill);
		int ground = dropped(helper);
		helper.assertTrue(stored + left + ground == 72, "flour lost or made: store " + stored + " + millstone " + left + " + ground " + ground + " != 72");
		helper.assertTrue(done == MechanicWork.Tended.WORKED, "emptying 24 of 32 flour into the store: " + done + ", not WORKED");
		helper.assertTrue(stored == 64, "the store holds " + stored + " flour, not 64");
		helper.assertTrue(left == 8, "the millstone holds " + left + " flour, not the 8 that didn't fit");
		helper.assertTrue(ground == 0, ground + " items were dropped on the ground");
		MachineWorkCompatTests.clearMachines(helper);
		helper.succeed();
	}

	/** Exact boundary split over two stacks: two slots of 48 flour (room 16 + 16), the rest full; 32 made: all go in. */
	//$ gametest_ticks_batch AREA '200' '"qa_store_room_mech_exact"'
	@GameTest(template = AREA, timeoutTicks = 200, batch = "qa_store_room_mech_exact")
	public void theMechanicFillsRoomSplitOverTwoFlourStacks(GameTestHelper helper) {
		Container chest = setup(helper);
		chest.setItem(0, new ItemStack(flour(), 48));
		chest.setItem(1, new ItemStack(flour(), 48));
		for (int i = 2; i < chest.getContainerSize(); i++) {
			chest.setItem(i, new ItemStack(Items.COBBLESTONE, 64));
		}
		BlockPos mill = mill(helper, 32);
		MechanicWork.Tended done = tend(helper, helper.absolutePos(CHEST), mill);
		helper.assertTrue(done == MechanicWork.Tended.WORKED, "emptying 32 flour into exactly 32 room: " + done + ", not WORKED");
		helper.assertTrue(count(chest, flour()) == 128, "the store holds " + count(chest, flour()) + " flour, not 128");
		helper.assertTrue(inMill(helper, mill) == 0, "the millstone kept " + inMill(helper, mill) + " flour");
		helper.assertTrue(dropped(helper) == 0, dropped(helper) + " items were dropped on the ground");
		MachineWorkCompatTests.clearMachines(helper);
		helper.succeed();
	}

	/** A Create Item Vault with one item of a different kind in every slot, a flour among them: it still takes the 32 flour. */
	//$ gametest_ticks_batch AREA '200' '"qa_store_room_mech_vault"'
	@GameTest(template = AREA, timeoutTicks = 200, batch = "qa_store_room_mech_vault")
	public void aVaultWithAPartStackInEverySlotTakesTheMechanicsFlour(GameTestHelper helper) {
		vault(helper, true);
	}

	/** The same vault without a flour among its part stacks: no room for flour, FULL, the millstone keeps it. */
	//$ gametest_ticks_batch AREA '200' '"qa_store_room_mech_vault_wrong_kind"'
	@GameTest(template = AREA, timeoutTicks = 200, batch = "qa_store_room_mech_vault_wrong_kind")
	public void aVaultWithRoomOnlyForOtherItemsKeepsTheFlourOut(GameTestHelper helper) {
		vault(helper, false);
	}

	private static void vault(GameTestHelper helper, boolean withFlour) {
		setup(helper);
		helper.setBlock(CHEST, MechanicCompatTests.create("item_vault").defaultBlockState());
		BlockPos mill = mill(helper, 32);
		helper.runAfterDelay(20, () -> {
			ServerLevel level = helper.getLevel();
			ItemStores stores = Platform.get().items();
			List<BlockPos> vault = List.of(helper.absolutePos(CHEST));
			int slots = 0;
			if (withFlour) {
				helper.assertTrue(stores.insert(level, vault, new ItemStack(flour(), 1)).isEmpty(), "setup: the vault takes no flour");
				slots++;
			}
			for (Item item : BuiltInRegistries.ITEM) {
				if (item == Items.AIR || item == flour() || item.getDefaultMaxStackSize() != 64) {
					continue;
				}
				if (!stores.insert(level, vault, new ItemStack(item, 1)).isEmpty()) {
					break; // every slot holds something
				}
				slots++;
				if (slots > 500) {
					throw new net.minecraft.gametest.framework.GameTestAssertException("setup: the vault took 500 kinds of items and isn't full");
				}
			}
			helper.assertTrue(slots >= 2, "setup: the vault has " + slots + " slots");
			MechanicWork.Tended done = tend(helper, vault.get(0), mill);
			long stored = stores.count(level, vault, flour());
			int left = inMill(helper, mill);
			int ground = dropped(helper);
			if (withFlour) {
				helper.assertTrue(done == MechanicWork.Tended.WORKED, "seeing to the millstone by a vault (" + slots + " part stacks, one of flour): " + done);
				helper.assertTrue(stored == 33, "the vault holds " + stored + " flour, not 1 + 32");
				helper.assertTrue(left == 0, "the millstone kept " + left + " flour");
			} else {
				helper.assertTrue(done == MechanicWork.Tended.FULL, "seeing to the millstone by a vault (" + slots + " part stacks, none of flour): " + done + ", not FULL");
				helper.assertTrue(stored == 0, "the vault took " + stored + " flour without a slot for it");
				helper.assertTrue(left == 32, "the millstone holds " + left + " of its 32 flour");
			}
			helper.assertTrue(ground == 0, ground + " items were dropped on the ground");
			MachineWorkCompatTests.clearMachines(helper); // the vault too, without spilling
			helper.succeed();
		});
	}

	/** A clean village workshop with a chest at {@link #CHEST}. */
	private static Container setup(GameTestHelper helper) {
		PartnerShowsCompatTests.clearLeftovers(helper);
		MachineWorkCompatTests.clearMachines(helper);
		MachineWorkCompatTests.villageWorkshop(helper);
		helper.setBlock(CHEST, Blocks.CHEST.defaultBlockState());
		return (Container) helper.getBlockEntity(CHEST);
	}

	/** A still millstone holding {@code flour} made flour in its output; returns its absolute position. */
	private static BlockPos mill(GameTestHelper helper, int flour) {
		BlockPos mill = MachineWorkCompatTests.rig(helper, Kind.MILL, false);
		ServerLevel level = helper.getLevel();
		var be = helper.getBlockEntity(mill);
		CompoundTag tag = be.saveWithoutMetadata(level.registryAccess());
		CompoundTag out = tag.getCompound("OutputInventory");
		net.minecraft.nbt.ListTag items = new net.minecraft.nbt.ListTag();
		CompoundTag item = (CompoundTag) new ItemStack(flour(), flour).save(level.registryAccess(), new CompoundTag());
		item.putInt("Slot", 0);
		items.add(item);
		out.put("Items", items);
		tag.put("OutputInventory", out);
		be.loadWithComponents(tag, level.registryAccess());
		BlockPos at = helper.absolutePos(mill);
		helper.assertTrue(inMill(helper, at) == flour, "setup: the millstone holds " + inMill(helper, at) + " flour, not " + flour);
		return at;
	}

	private static MechanicWork.Tended tend(GameTestHelper helper, BlockPos store, BlockPos mill) {
		MachineWorkFiles.Machine file = MachineWorkFiles.all().stream().filter(m -> m.id().equals(AliveWorkplace.id(Kind.MILL.file))).findFirst()
			.orElseThrow(() -> new AssertionError("no machine_work file millstone"));
		return MachineWork.tend(helper.getLevel(), List.of(store), mill, file, store);
	}

	private static Item flour() {
		return Kind.MILL.output();
	}

	private static int inMill(GameTestHelper helper, BlockPos at) {
		return (int) Platform.get().items().count(helper.getLevel(), List.of(at), flour());
	}

	private static int count(Container container, Item item) {
		return container.countItem(item);
	}

	private static int dropped(GameTestHelper helper) {
		return helper.getLevel().getEntitiesOfClass(ItemEntity.class, helper.getBounds().inflate(4)).stream().mapToInt(e -> e.getItem().getCount()).sum();
	}
}
