package io.github.jcondedata.aliveworkplace.compat;

import io.github.jcondedata.aliveworkplace.AliveWorkplace;
import io.github.jcondedata.aliveworkplace.mechanic.MachineWork;
import io.github.jcondedata.aliveworkplace.mechanic.MachineWorkFiles;
import io.github.jcondedata.aliveworkplace.mechanic.MechanicWork;
import java.util.List;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.core.BlockPos;
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
 * QA lane c, 2026-11-02, B820 from its spec ("the Mechanic takes a chest whose every slot holds a part stack as full ...
 * Expected: ask whether the load fits") with the real Create millstone. The fix's tests cover a chest with room for all
 * of the flour and a brim-full one; these take the boundaries between: room for exactly the 32 flour, room for only 14
 * (the rest stays in the millstone, nothing dropped, nothing lost or made), and part stacks of other kinds only (no room
 * for flour at all, though no stack is full).
 */
public class QaMechanicRoom1102cCompatTests implements FabricGameTest {
	private static final String AREA = CompatGameTests.AREA;
	private static final BlockPos CHEST = new BlockPos(2, 2, 4);

	/** The store chest: {@code flour} flour in the first slot (none when 0), {@code other} cobblestone in each other slot. */
	private static Container store(GameTestHelper helper, int flour, int other) {
		helper.setBlock(CHEST, Blocks.CHEST.defaultBlockState());
		Container chest = (Container) helper.getBlockEntity(CHEST);
		chest.setItem(0, flour > 0 ? new ItemStack(MachineWorkCompatTests.Kind.MILL.output(), flour) : new ItemStack(Items.COBBLESTONE, other));
		for (int i = 1; i < chest.getContainerSize(); i++) {
			chest.setItem(i, new ItemStack(Items.COBBLESTONE, other));
		}
		return chest;
	}

	private static int count(Container container, Item item) {
		int n = 0;
		for (int i = 0; i < container.getContainerSize(); i++) {
			if (container.getItem(i).is(item)) {
				n += container.getItem(i).getCount();
			}
		}
		return n;
	}

	private static MachineWorkFiles.Machine millFile() {
		return MachineWorkFiles.all().stream().filter(m -> m.id().equals(AliveWorkplace.id("millstone"))).findFirst()
			.orElseThrow(() -> new AssertionError("no machine_work file millstone: " + MachineWorkFiles.all()));
	}

	/** Puts {@code made} into the millstone's output, as a player's automation leaves it there. */
	private static void millOutput(GameTestHelper helper, BlockPos mill, ItemStack made) {
		ServerLevel level = helper.getLevel();
		var be = helper.getBlockEntity(mill);
		CompoundTag tag = be.saveWithoutMetadata(level.registryAccess());
		CompoundTag out = tag.getCompound("OutputInventory");
		net.minecraft.nbt.ListTag items = new net.minecraft.nbt.ListTag();
		CompoundTag item = (CompoundTag) made.save(level.registryAccess(), new CompoundTag());
		item.putInt("Slot", 0);
		items.add(item);
		out.put("Items", items);
		tag.put("OutputInventory", out);
		be.loadWithComponents(tag, level.registryAccess());
	}

	/**
	 * The Mechanic sees to a millstone holding 32 flour by a store whose every slot holds something ({@code flour} flour in
	 * the first, {@code other} cobblestone in each other slot); checks what went where and returns what the visit read.
	 */
	private static MechanicWork.Tended visit(GameTestHelper helper, int flour, int other, int stored, int kept) {
		PartnerShowsCompatTests.clearLeftovers(helper);
		MachineWorkCompatTests.clearMachines(helper);
		ServerLevel level = helper.getLevel();
		MachineWorkCompatTests.villageWorkshop(helper);
		Container chest = store(helper, flour, other);
		Item flourItem = MachineWorkCompatTests.Kind.MILL.output();
		BlockPos mill = MachineWorkCompatTests.rig(helper, MachineWorkCompatTests.Kind.MILL, false);
		millOutput(helper, mill, new ItemStack(flourItem, 32));
		BlockPos at = helper.absolutePos(mill);
		var stores = io.github.jcondedata.aliveworkplace.platform.Platform.get().items();
		helper.assertTrue(stores.count(level, List.of(at), flourItem) == 32, "setup: the millstone doesn't hold 32 flour");
		helper.assertTrue(io.github.jcondedata.aliveworkplace.build.SupplyContainers.freeSlots(level, List.of(helper.absolutePos(CHEST))) == 0,
			"setup: the chest has an empty slot");
		MechanicWork.Tended done = MachineWork.tend(level, List.of(helper.absolutePos(CHEST)), at, millFile(), helper.absolutePos(CHEST));
		int dropped = level.getEntitiesOfClass(ItemEntity.class, helper.getBounds().inflate(4)).stream().mapToInt(e -> e.getItem().getCount()).sum();
		long inMill = stores.count(level, List.of(at), flourItem);
		int inChest = count(chest, flourItem);
		helper.assertTrue(dropped == 0, dropped + " items were dropped on the ground");
		helper.assertTrue(inChest + inMill == flour + 32, "flour lost or made: " + inChest + " in the store + " + inMill + " in the millstone, not " + (flour + 32));
		helper.assertTrue(inChest == stored && inMill == kept, "the store holds " + inChest + " flour (expected " + stored + "), the millstone " + inMill
			+ " (expected " + kept + "); the visit read " + done);
		helper.assertTrue(count(chest, Items.COBBLESTONE) == (flour > 0 ? 26 : 27) * other, "the store's cobblestone changed: " + count(chest, Items.COBBLESTONE));
		MachineWorkCompatTests.clearMachines(helper);
		return done;
	}

	/** Room for exactly the 32 flour (32 in the store's flour stack): all of it goes in. */
	//$ gametest_ticks_batch AREA '200' '"qaMechRoom1102cExact"'
	@GameTest(template = AREA, timeoutTicks = 200, batch = "qaMechRoom1102cExact")
	public void qaAStoreWithRoomForExactlyTheFlourTakesItAll(GameTestHelper helper) {
		MechanicWork.Tended done = visit(helper, 32, 1, 64, 0);
		helper.assertTrue(done == MechanicWork.Tended.WORKED, "emptying the millstone into a store with room for exactly its flour read " + done);
		helper.succeed();
	}

	/** Room for 14 of the 32 flour (50 in the flour stack): 14 go in, 18 stay in the millstone, nothing dropped. */
	//$ gametest_ticks_batch AREA '200' '"qaMechRoom1102cPart"'
	@GameTest(template = AREA, timeoutTicks = 200, batch = "qaMechRoom1102cPart")
	public void qaAStoreWithRoomForPartOfTheFlourTakesThatMuch(GameTestHelper helper) {
		MechanicWork.Tended done = visit(helper, 50, 1, 64, 18);
		helper.assertTrue(done != MechanicWork.Tended.FULL, "14 of the flour fit, but the visit read FULL");
		helper.succeed();
	}

	/** Part stacks of cobblestone only, no flour stack and no empty slot: no room for flour, so FULL and the flour stays. */
	//$ gametest_ticks_batch AREA '200' '"qaMechRoom1102cOtherKinds"'
	@GameTest(template = AREA, timeoutTicks = 200, batch = "qaMechRoom1102cOtherKinds")
	public void qaPartStacksOfOtherKindsAreNoRoomForFlour(GameTestHelper helper) {
		MechanicWork.Tended done = visit(helper, 0, 1, 0, 32);
		helper.assertTrue(done == MechanicWork.Tended.FULL, "no room for flour (cobblestone part stacks only), but the visit read " + done);
		helper.succeed();
	}
}
