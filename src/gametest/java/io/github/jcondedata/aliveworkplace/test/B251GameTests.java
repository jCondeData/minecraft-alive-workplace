package io.github.jcondedata.aliveworkplace.test;

import io.github.jcondedata.aliveworkplace.registry.ModAttachments;
import io.github.jcondedata.aliveworkplace.registry.ModBlocks;
import io.github.jcondedata.aliveworkplace.registry.ModVillagers;
import io.github.jcondedata.aliveworkplace.store.Porters;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.Container;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;

/**
 * B251: the showcase's Drop Box scene. A jobless villager walking to a new Storehouse they picked (vanilla holds its one
 * ticket for them on the way) is made its Porter. The porter keeps that ticket (it was freed when the hire stopped the walk,
 * for the next jobless villager to take from him) and empties the full Drop Box into the store.
 */
public class B251GameTests implements FabricGameTest {
	private static final String AREA = "aliveworkplace_test:big_area";
	private static final BlockPos STOREHOUSE = new BlockPos(6, 2, 8);
	private static final BlockPos STORE_CHEST = STOREHOUSE.west(2);
	private static final BlockPos BOX = STOREHOUSE.offset(3, 0, -3);

	//$ gametest_ticks_batch AREA '900' '"b251_porter_hired_walking"'
	@GameTest(template = AREA, timeoutTicks = 900, batch = "b251_porter_hired_walking")
	public void aPorterHiredWhileWalkingToTheStorehouseEmptiesTheDropBox(GameTestHelper helper) {
		hired(helper, STOREHOUSE.offset(0, 0, 6), 3);
	}

	/** Spawns a jobless villager at {@code from} and makes them the porter {@code walked} ticks after they pick the Storehouse. */
	static void hired(GameTestHelper helper, BlockPos from, int walked) {
		Leftovers.clear(helper);
		helper.setDayTime(2500); // the scene's hour: work time
		helper.setBlock(STOREHOUSE, ModBlocks.STOREHOUSE);
		helper.setBlock(STORE_CHEST, Blocks.CHEST);
		helper.setBlock(BOX, ModBlocks.DROP_BOX);
		Container box = helper.getBlockEntity(BOX);
		box.setItem(0, new ItemStack(Items.IRON_SWORD));
		box.setItem(1, new ItemStack(Items.BREAD, 16));
		box.setItem(2, new ItemStack(Items.COBBLESTONE, 64));
		Villager villager = helper.spawn(EntityType.VILLAGER, from);
		BlockPos storehouse = helper.absolutePos(STOREHOUSE);
		GlobalPos site = GlobalPos.of(helper.getLevel().dimension(), storehouse);
		int[] picked = {-1};
		String[] hire = {null};
		helper.onEachTick(() -> {
			if (picked[0] < 0 && helper.getTick() >= 5) {
				// they pick it as vanilla's AcquirePoi does (its ticket and the memory), but now rather than at a random moment
				helper.getLevel().getPoiManager().take(h -> h.is(ModVillagers.STOREHOUSE_POI), (h, p) -> p.equals(storehouse), storehouse, 1);
				villager.getBrain().setMemory(MemoryModuleType.POTENTIAL_JOB_SITE, site);
			}
			if (picked[0] < 0 && villager.getBrain().getMemory(MemoryModuleType.POTENTIAL_JOB_SITE).filter(site::equals).isPresent()) {
				picked[0] = 0;
			}
			if (picked[0] >= 0 && hire[0] == null && picked[0]++ >= walked) {
				hire[0] = "picked, then hired " + walked + " ticks on: " + villager.getVillagerData().getProfession() + ", walking to it "
					+ villager.getBrain().getMemory(MemoryModuleType.POTENTIAL_JOB_SITE).isPresent();
				Porters.employ(helper.getLevel(), villager, storehouse);
			}
		});
		helper.succeedWhen(() -> {
			helper.assertTrue(hire[0] != null, "the villager never picked the Storehouse");
			helper.assertTrue(villager.getVillagerData().getProfession() == ModVillagers.PORTER, "not a porter: " + villager.getVillagerData().getProfession());
			helper.assertTrue(villager.getBrain().getMemory(MemoryModuleType.JOB_SITE).filter(site::equals).isPresent(),
				"the porter's job site: " + villager.getBrain().getMemory(MemoryModuleType.JOB_SITE) + " (" + hire[0] + ")");
			Container store = helper.getBlockEntity(STORE_CHEST);
			helper.assertTrue(box.isEmpty(), "the Drop Box still holds things (" + hire[0] + "; doing "
				+ villager.getBrain().getActiveNonCoreActivity().map(Object::toString).orElse("nothing") + ", at " + helper.relativePos(villager.blockPosition()) + ")");
			helper.assertTrue(store.countItem(Items.BREAD) == 16 && store.countItem(Items.IRON_SWORD) == 1 && store.countItem(Items.COBBLESTONE) == 64,
				"the store has " + store.countItem(Items.BREAD) + " bread");
			helper.assertTrue(ModAttachments.ITEMS_CARRIED.getOrElse(villager, 0) == 81, "carried " + ModAttachments.ITEMS_CARRIED.getOrElse(villager, 0));
			// the Storehouse's one ticket stays the porter's: freed, the next jobless villager walking by takes it from him
			helper.assertTrue(helper.getLevel().getPoiManager().getFreeTickets(storehouse) == 0,
				"the Storehouse has " + helper.getLevel().getPoiManager().getFreeTickets(storehouse) + " free tickets after the hire (" + hire[0] + ")");
		});
	}
}
