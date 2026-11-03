package io.github.jcondedata.aliveworkplace.compat;

import io.github.jcondedata.aliveworkplace.mechanic.MachineWork;
import io.github.jcondedata.aliveworkplace.platform.Platform;
import io.github.jcondedata.aliveworkplace.registry.ModAttachments;
import io.github.jcondedata.aliveworkplace.work.Stations;
import io.github.jcondedata.aliveworkplace.work.WorkerStatus;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Container;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;

/**
 * QA lane E, 2026-11-03 (a mutant of MachineWork.outputFits, `return false` to `return true`, survived the whole compat
 * suite): ROADMAP 39.3 and B820 on the farmer's own trip, where only the Mechanic's round was tested. A turning millstone
 * already holding 32 flour, the village store beside it:
 * <ul>
 *   <li>brim full (every slot a full stack, the flour's too): the farmer never sets off for the flour, says "Machine work:
 *   the store is full: the Millstone waits", and the flour stays in the millstone, none in the store or on the ground;</li>
 *   <li>the other side: one flour in the first slot and a cobblestone in every other (no slot empty, but the flour stack has
 *   room): the farmer fetches all 32 flour into the store, none dropped.</li>
 * </ul>
 */
public class QaMachineFullOutput1103eCompatTests implements FabricGameTest {
	private static final String AREA = CompatGameTests.AREA;
	private static final BlockPos STATION = new BlockPos(2, 2, 2);
	private static final BlockPos CHEST = new BlockPos(2, 2, 4);
	private static final BlockPos MILL = new BlockPos(6, 3, 2);

	private static Item flour() {
		return MechanicCompatTests.createItem("wheat_flour");
	}

	/** The village store: {@code flour} flour in the first slot, a stack of {@code cobblestone} in each of the other 26. */
	private static Container store(GameTestHelper helper, int flour, int cobblestone) {
		helper.setBlock(CHEST, Blocks.CHEST.defaultBlockState());
		Container chest = (Container) helper.getBlockEntity(CHEST);
		chest.setItem(0, new ItemStack(flour(), flour));
		for (int i = 1; i < chest.getContainerSize(); i++) {
			chest.setItem(i, new ItemStack(Items.COBBLESTONE, cobblestone));
		}
		return chest;
	}

	/** A millstone turned by a creative motor, holding {@code flour} flour it made. */
	private static void millHolding(GameTestHelper helper, int flour) {
		ServerLevel level = helper.getLevel();
		helper.setBlock(MILL.below(), MechanicCompatTests.with(MechanicCompatTests.create("creative_motor").defaultBlockState(), "facing", "up"));
		helper.setBlock(MILL, MechanicCompatTests.create("millstone").defaultBlockState());
		var be = helper.getBlockEntity(MILL);
		CompoundTag tag = be.saveWithoutMetadata(level.registryAccess());
		CompoundTag out = tag.getCompound("OutputInventory");
		net.minecraft.nbt.ListTag items = new net.minecraft.nbt.ListTag();
		CompoundTag item = (CompoundTag) new ItemStack(flour(), flour).save(level.registryAccess(), new CompoundTag());
		item.putInt("Slot", 0);
		items.add(item);
		out.put("Items", items);
		tag.put("OutputInventory", out);
		be.loadWithComponents(tag, level.registryAccess());
		helper.assertTrue(inMill(helper) == flour, "setup: the millstone holds " + inMill(helper) + " flour, not " + flour);
	}

	private static long inMill(GameTestHelper helper) {
		return Platform.get().items().count(helper.getLevel(), List.of(helper.absolutePos(MILL)), flour());
	}

	private static Villager farmer(GameTestHelper helper) {
		helper.setBlock(STATION, Blocks.COMPOSTER);
		Villager villager = helper.spawn(EntityType.VILLAGER, new BlockPos(3, 2, 3));
		Stations.assign(helper.getLevel(), villager, helper.absolutePos(STATION), net.minecraft.world.entity.npc.VillagerProfession.FARMER);
		helper.assertTrue(villager.getVillagerData().getProfession() == net.minecraft.world.entity.npc.VillagerProfession.FARMER, "setup: no farmer at the composter");
		helper.setDayTime(2000);
		return villager;
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

	private static int dropped(GameTestHelper helper) {
		return helper.getLevel().getEntitiesOfClass(ItemEntity.class, helper.getBounds().inflate(4)).stream()
			.mapToInt(e -> e.getItem().getCount()).sum();
	}

	private static void setUp(GameTestHelper helper) {
		PartnerShowsCompatTests.clearLeftovers(helper);
		MachineWorkCompatTests.clearMachines(helper);
		MachineWorkCompatTests.villageWorkshop(helper);
	}

	/** Brim full: the farmer stays, says the store is full, and the millstone keeps its 32 flour. */
	//$ gametest_ticks_batch AREA '800' '"qa_e1103_mill_brim_full_trip"'
	@GameTest(template = AREA, timeoutTicks = 800, batch = "qa_e1103_mill_brim_full_trip")
	public void aFarmerLeavesTheMillstonesFlourWhenTheStoreIsBrimFull(GameTestHelper helper) {
		setUp(helper);
		Container chest = store(helper, 64, 64);
		millHolding(helper, 32);
		Villager farmer = farmer(helper);
		AtomicBoolean setOff = new AtomicBoolean();
		Set<String> said = new LinkedHashSet<>();
		helper.onEachTick(() -> {
			if (MachineWork.busy(farmer)) {
				setOff.set(true);
			}
			WorkerStatus.Entry e = WorkerStatus.get(farmer, helper.getLevel().getGameTime());
			if (e != null) {
				said.add(e.title().getString() + ": " + e.line().getString());
			}
		});
		helper.runAfterDelay(600, () -> {
			helper.assertFalse(setOff.get(), "the store is brim full, but the farmer set off for the millstone's flour: "
				+ ModAttachments.MACHINE_JOB.get(farmer));
			helper.assertTrue(said.contains("Machine work: the store is full: the Millstone waits"), "the farmer said " + said);
			helper.assertTrue(inMill(helper) == 32, "the millstone holds " + inMill(helper) + " of its 32 flour");
			helper.assertTrue(count(chest, flour()) == 64, "the brim-full store holds " + count(chest, flour()) + " flour, not 64");
			helper.assertTrue(dropped(helper) == 0, dropped(helper) + " items lie on the ground");
			MachineWorkCompatTests.clearMachines(helper);
			helper.succeed();
		});
	}

	/** Room in the flour's stack only: the farmer fetches all 32 flour into the store. */
	//$ gametest_ticks_batch AREA '2400' '"qa_e1103_mill_stack_room_trip"'
	@GameTest(template = AREA, timeoutTicks = 2400, batch = "qa_e1103_mill_stack_room_trip")
	public void aFarmerFetchesTheMillstonesFlourIntoTheRoomLeftInItsStack(GameTestHelper helper) {
		setUp(helper);
		Container chest = store(helper, 1, 1);
		millHolding(helper, 32);
		Villager farmer = farmer(helper);
		helper.succeedWhen(() -> {
			helper.assertTrue(count(chest, flour()) == 33, "the store holds " + count(chest, flour()) + " flour, not 1 + 32 (the millstone "
				+ inMill(helper) + ", trip " + ModAttachments.MACHINE_JOB.get(farmer) + ")");
			helper.assertTrue(inMill(helper) == 0, "the millstone still holds " + inMill(helper) + " flour");
			helper.assertFalse(MachineWork.busy(farmer), "the farmer is still on his trip");
			helper.assertTrue(dropped(helper) == 0, dropped(helper) + " items lie on the ground");
			MachineWorkCompatTests.clearMachines(helper);
		});
	}
}
