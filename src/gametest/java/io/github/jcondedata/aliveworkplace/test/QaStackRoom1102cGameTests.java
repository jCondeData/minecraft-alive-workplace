package io.github.jcondedata.aliveworkplace.test;

import io.github.jcondedata.aliveworkplace.legend.GolemSmith;
import io.github.jcondedata.aliveworkplace.registry.ModAttachments;
import io.github.jcondedata.aliveworkplace.registry.ModBlocks;
import io.github.jcondedata.aliveworkplace.registry.ModVillagers;
import io.github.jcondedata.aliveworkplace.store.HaulerGolems;
import io.github.jcondedata.aliveworkplace.store.Porters;
import io.github.jcondedata.aliveworkplace.work.Furnaces;
import io.github.jcondedata.aliveworkplace.work.Jobs;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.Container;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.animal.IronGolem;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.AbstractFurnaceBlockEntity;

/**
 * QA lane c, 2026-11-02, from the specs of B802, B806 and B820 ("a container whose every slot holds a part stack has
 * room, so it is not full"; "cooked fish that fit in the barrel call him home"; "a Storehouse with room for the load is
 * not full"; "the goods would fit"). The fix's own tests cover a stack in every slot with plenty of room and the
 * brim-full chest. These check the boundaries from both sides (room for exactly part of the load, a full stack of the
 * same item, part stacks of other kinds only, 16-stack items), that nothing is lost or made (totals before and after),
 * the hauler's Drop Box and put-back paths, a golem saved and reloaded mid-haul, and a busy Storehouse where only some
 * workers' goods fit.
 */
public class QaStackRoom1102cGameTests implements net.fabricmc.fabric.api.gametest.v1.FabricGameTest {
	private static final String AREA = "aliveworkplace_test:build_area";
	private static final String BIG = "aliveworkplace_test:big_area";
	static final String HUGE = "aliveworkplace_test:huge_area";

	// ---- the fisherman's furnace (B802): Furnaces.needsTending is what calls him home ----

	private static final BlockPos BARREL = new BlockPos(2, 2, 2);
	private static final BlockPos FURNACE = new BlockPos(4, 2, 2);

	private static Container barrel(GameTestHelper helper, int cookedCod) {
		Leftovers.clear(helper);
		helper.setBlock(BARREL, Blocks.BARREL);
		helper.setBlock(FURNACE, Blocks.FURNACE);
		((AbstractFurnaceBlockEntity) helper.getBlockEntity(FURNACE)).setItem(2, new ItemStack(Items.COOKED_COD, cookedCod));
		return (Container) helper.getBlockEntity(BARREL);
	}

	private static boolean calls(GameTestHelper helper) {
		BlockPos at = helper.absolutePos(BARREL);
		return Furnaces.needsTending(helper.getLevel(), at, List.of(at), Furnaces::isFish);
	}

	/**
	 * Both sides of a full cooked cod stack: 26 full stacks of cobblestone and 64 cooked cod (no room for one more) don't
	 * call him; the same barrel with 63 cooked cod (room for exactly one) does.
	 */
	//$ gametest_ticks AREA '20'
	@GameTest(template = AREA, timeoutTicks = 20)
	public void qaACookedStackWithRoomForOneCallsHimAFullOneDoesnt(GameTestHelper helper) {
		Container barrel = barrel(helper, 1);
		for (int i = 0; i < barrel.getContainerSize() - 1; i++) {
			barrel.setItem(i, new ItemStack(Items.COBBLESTONE, 64));
		}
		barrel.setItem(barrel.getContainerSize() - 1, new ItemStack(Items.COOKED_COD, 64));
		helper.assertTrue(!calls(helper), "every stack in the barrel is full (64 cooked cod among them), but he's called for 1 cooked cod");
		barrel.setItem(barrel.getContainerSize() - 1, new ItemStack(Items.COOKED_COD, 63));
		helper.assertTrue(calls(helper), "the barrel's cooked cod stack has room for exactly the 1 cooked cod waiting, but he isn't called");
		helper.succeed();
	}

	/**
	 * Every slot holds a part stack, but none is cooked cod (cobblestone and cooked salmon with room): the cooked cod can't
	 * go anywhere, so he isn't called; room in a stack of another kind isn't room for it.
	 */
	//$ gametest_ticks AREA '20'
	@GameTest(template = AREA, timeoutTicks = 20)
	public void qaPartStacksOfOtherKindsDontCallHim(GameTestHelper helper) {
		Container barrel = barrel(helper, 3);
		for (int i = 0; i < barrel.getContainerSize(); i++) {
			barrel.setItem(i, new ItemStack(i % 2 == 0 ? Items.COBBLESTONE : Items.COOKED_SALMON, 1));
		}
		helper.assertTrue(!calls(helper), "no slot is empty and no stack is cooked cod, but 3 cooked cod call him home");
		barrel.setItem(5, ItemStack.EMPTY);
		helper.assertTrue(calls(helper), "one slot emptied, but the 3 cooked cod don't call him");
		helper.succeed();
	}

	// ---- the Hauler Golem (B806) ----

	static final BlockPos STOREHOUSE = new BlockPos(3, 2, 4);
	static final BlockPos STORE_CHEST = new BlockPos(4, 2, 4);
	private static final BlockPos[] JACK_CHESTS = {new BlockPos(12, 2, 27), new BlockPos(22, 2, 27), new BlockPos(26, 2, 15)};
	private static final BlockPos MINER_BENCH = new BlockPos(14, 2, 16);
	static final BlockPos MINER_CHEST = new BlockPos(14, 2, 18);
	static final BlockPos FAR_JACK_CHEST = new BlockPos(24, 2, 27);
	static final BlockPos HAUL_BOX = new BlockPos(16, 2, 4);

	static int count(GameTestHelper helper, BlockPos pos, Item item) {
		return ((Container) helper.getBlockEntity(pos)).countItem(item);
	}

	static int carried(IronGolem golem, Item item) {
		return HaulerGolems.load(golem).getOrDefault(item, 0);
	}

	static void fillEach(Container c, Item item, int count) {
		for (int i = 0; i < c.getContainerSize(); i++) {
			c.setItem(i, new ItemStack(item, count));
		}
	}

	/**
	 * Removes every Drop Box outside the test area that a porter or hauler here could pick (an earlier batch's, never
	 * cleared: a stray Drop Box with goods is taken first, as a player's would be), and the test's own after it ends.
	 */
	static void strayDropBoxes(GameTestHelper helper) {
		net.minecraft.world.phys.AABB area = helper.getBounds();
		int reach = Math.max(io.github.jcondedata.aliveworkplace.store.PorterWork.DROP_BOX_RANGE, HaulerGolems.STOREHOUSE_RANGE)
			+ (int) Math.ceil(Math.hypot(area.getXsize(), area.getZsize())) + 1;
		BlockPos center = BlockPos.containing(area.getCenter());
		for (BlockPos stray : helper.getLevel().getPoiManager().findAll(h -> h.is(ModVillagers.DROP_BOX_POI),
			p -> !area.contains(net.minecraft.world.phys.Vec3.atCenterOf(p)), center, reach,
			net.minecraft.world.entity.ai.village.poi.PoiManager.Occupancy.ANY).toList()) {
			helper.getLevel().removeBlock(stray, false);
		}
		Leftovers.after(helper, () -> {
			for (BlockPos own : helper.getLevel().getPoiManager().findAll(h -> h.is(ModVillagers.DROP_BOX_POI),
				p -> area.contains(net.minecraft.world.phys.Vec3.atCenterOf(p)), center, reach,
				net.minecraft.world.entity.ai.village.poi.PoiManager.Occupancy.ANY).toList()) {
				if (helper.getLevel().getBlockEntity(own) instanceof Container c) {
					c.clearContent();
				}
				helper.getLevel().removeBlock(own, false);
			}
		});
	}

	/** A Storehouse with its chest and nothing else (no workers), stray Storehouses removed, workers shared village-wide. */
	static Container storehouse(GameTestHelper helper) {
		Leftovers.clear(helper);
		GolemHaulerFightGameTests.strayStorehouses(helper);
		strayDropBoxes(helper);
		Leftovers.village(helper, 48);
		helper.setBlock(STOREHOUSE, ModBlocks.STOREHOUSE);
		helper.setBlock(STORE_CHEST, Blocks.CHEST);
		return (Container) helper.getBlockEntity(STORE_CHEST);
	}

	static Villager lumberjack(GameTestHelper helper, BlockPos chest, int logs) {
		BlockPos block = chest.north();
		helper.setBlock(block, ModBlocks.CHOPPING_BLOCK);
		helper.setBlock(chest, Blocks.CHEST);
		if (logs > 0) {
			((Container) helper.getBlockEntity(chest)).setItem(0, new ItemStack(Items.OAK_LOG, logs));
		}
		Villager jack = helper.spawn(EntityType.VILLAGER, block.east());
		jack.setNoAi(true);
		Jobs.employ(helper.getLevel(), jack, helper.absolutePos(block), ModVillagers.CHOPPING_BLOCK_POI, ModVillagers.LUMBERJACK);
		return jack;
	}

	static Villager miner(GameTestHelper helper, int cobblestone) {
		helper.setBlock(MINER_BENCH, ModBlocks.MINERS_BENCH);
		helper.setBlock(MINER_CHEST, Blocks.CHEST);
		((Container) helper.getBlockEntity(MINER_CHEST)).setItem(0, new ItemStack(Items.COBBLESTONE, cobblestone));
		Villager miner = helper.spawn(EntityType.VILLAGER, MINER_BENCH.east());
		io.github.jcondedata.aliveworkplace.mine.Miners.employ(helper.getLevel(), miner, helper.absolutePos(MINER_BENCH));
		miner.setNoAi(true);
		return miner;
	}

	static IronGolem hauler(GameTestHelper helper) {
		IronGolem golem = GolemSmith.build(helper.getLevel(), helper.absolutePos(new BlockPos(14, 2, 10)), GolemSmith.Role.HAULER);
		helper.assertTrue(golem != null, "a hauler");
		Leftovers.after(helper, golem::discard);
		return golem;
	}

	/**
	 * Room for exactly part of the logs: every slot of the Storehouse chest holds 60 logs (room for 108), three lumberjacks
	 * hold 64 each. The chest fills to the brim, 84 logs stay with the lumberjacks, the golem ends empty-handed: no log lost
	 * and none made.
	 */
	//$ gametest_ticks_batch HUGE '4800' '"qaRoom1102cHaulerExact"'
	@GameTest(template = HUGE, timeoutTicks = 4800, batch = "qaRoom1102cHaulerExact")
	public void qaTheHaulerFillsTheRoomInEveryStackAndLeavesTheRest(GameTestHelper helper) {
		Container store = storehouse(helper);
		fillEach(store, Items.OAK_LOG, 60);
		for (BlockPos c : JACK_CHESTS) {
			lumberjack(helper, c, 64);
		}
		int total = 27 * 60 + 3 * 64;
		IronGolem golem = hauler(helper);
		helper.succeedWhen(() -> {
			int left = 0;
			for (BlockPos c : JACK_CHESTS) {
				left += count(helper, c, Items.OAK_LOG);
			}
			int stored = count(helper, STORE_CHEST, Items.OAK_LOG);
			int onGolem = carried(golem, Items.OAK_LOG);
			helper.assertTrue(stored + left + onGolem == total, "logs lost or made: " + stored + " stored + " + left + " with the lumberjacks + "
				+ onGolem + " on the golem, not " + total);
			helper.assertTrue(stored == 27 * 64 && left == 192 - 108 && onGolem == 0, "the Storehouse chest (room for 108) has " + stored + " of "
				+ 27 * 64 + " logs; the lumberjacks keep " + left + " (expected 84); the golem carries " + HaulerGolems.load(golem));
		});
	}

	/**
	 * The put-back with no Storehouse (B463): a Drop Box whose every slot holds one oak log (no empty slot, room for 1,701)
	 * takes the golem's 64 logs; the golem ends empty-handed.
	 */
	//$ gametest_ticks_batch HUGE '2400' '"qaRoom1102cPutBackRoom"'
	@GameTest(template = HUGE, timeoutTicks = 2400, batch = "qaRoom1102cPutBackRoom")
	public void qaAPutBackGoesIntoADropBoxWithAStackInEverySlot(GameTestHelper helper) {
		Leftovers.clear(helper);
		GolemHaulerFightGameTests.strayStorehouses(helper);
		strayDropBoxes(helper);
		helper.setBlock(HAUL_BOX, ModBlocks.DROP_BOX);
		Container box = (Container) helper.getBlockEntity(HAUL_BOX);
		fillEach(box, Items.OAK_LOG, 1);
		IronGolem golem = hauler(helper);
		ModAttachments.HAULER_LOAD.set(golem, Map.of(Items.OAK_LOG, 64));
		helper.succeedWhen(() -> helper.assertTrue(box.countItem(Items.OAK_LOG) == 27 + 64 && HaulerGolems.load(golem).isEmpty(),
			"the Drop Box (a log in every slot) has " + box.countItem(Items.OAK_LOG) + " of " + (27 + 64) + " logs; the golem carries " + HaulerGolems.load(golem)));
	}

	/**
	 * The put-back's other side: the Drop Box's stacks have room for only 27 of the 64 logs. 27 go in, the rest stay on
	 * the golem (nowhere else to go), none is lost or made, and the Drop Box isn't overfilled.
	 */
	//$ gametest_ticks_batch HUGE '1200' '"qaRoom1102cPutBackShort"'
	@GameTest(template = HUGE, timeoutTicks = 1200, batch = "qaRoom1102cPutBackShort")
	public void qaAPutBackIntoADropBoxWithRoomForPartKeepsTheRest(GameTestHelper helper) {
		Leftovers.clear(helper);
		GolemHaulerFightGameTests.strayStorehouses(helper);
		strayDropBoxes(helper);
		helper.setBlock(HAUL_BOX, ModBlocks.DROP_BOX);
		Container box = (Container) helper.getBlockEntity(HAUL_BOX);
		fillEach(box, Items.OAK_LOG, 63);
		IronGolem golem = hauler(helper);
		ModAttachments.HAULER_LOAD.set(golem, Map.of(Items.OAK_LOG, 64));
		helper.runAfterDelay(1000, () -> {
			int inBox = box.countItem(Items.OAK_LOG);
			int onGolem = carried(golem, Items.OAK_LOG);
			helper.assertTrue(inBox + onGolem == 27 * 63 + 64, "logs lost or made: " + inBox + " in the box + " + onGolem + " on the golem, not " + (27 * 63 + 64));
			helper.assertTrue(inBox == 27 * 64 && onGolem == 37, "the Drop Box (room for 27) has " + inBox + " of " + 27 * 64 + " logs; the golem carries "
				+ onGolem + " (expected 37)");
			helper.succeed();
		});
	}

	/**
	 * Save and reload mid-haul: a golem carrying 64 logs is saved and loaded again (a new entity from its saved data); the
	 * Storehouse chest holds one log in every slot. The reloaded golem still stores all 64.
	 */
	//$ gametest_ticks_batch HUGE '2400' '"qaRoom1102cReload"'
	@GameTest(template = HUGE, timeoutTicks = 2400, batch = "qaRoom1102cReload")
	public void qaAReloadedLoadedHaulerStoresIntoAStackInEverySlot(GameTestHelper helper) {
		Container store = storehouse(helper);
		fillEach(store, Items.OAK_LOG, 1);
		lumberjack(helper, JACK_CHESTS[0], 0); // a worker of the village, with nothing to haul
		IronGolem golem = hauler(helper);
		ModAttachments.HAULER_LOAD.set(golem, Map.of(Items.OAK_LOG, 64));
		CompoundTag tag = new CompoundTag();
		golem.saveWithoutId(tag);
		golem.discard();
		IronGolem copy = EntityType.IRON_GOLEM.create(helper.getLevel());
		helper.assertTrue(copy != null, "no golem made");
		copy.load(tag);
		copy.setUUID(UUID.randomUUID());
		helper.getLevel().addFreshEntity(copy);
		Leftovers.after(helper, copy::discard);
		helper.assertTrue(carried(copy, Items.OAK_LOG) == 64, "the load wasn't saved: " + HaulerGolems.load(copy));
		helper.succeedWhen(() -> helper.assertTrue(store.countItem(Items.OAK_LOG) == 27 + 64 && HaulerGolems.load(copy).isEmpty(),
			"the Storehouse chest (a log in every slot) has " + store.countItem(Items.OAK_LOG) + " of " + (27 + 64) + " logs; the reloaded golem carries "
				+ HaulerGolems.load(copy)));
	}

	// ---- the porter (B820), in PorterGameTests' village ----

	private static final BlockPos P_BENCH = new BlockPos(2, 2, 2);
	private static final BlockPos P_MINER_CHEST = new BlockPos(2, 2, 4);
	private static final BlockPos P_STOREHOUSE = new BlockPos(19, 2, 19);
	private static final BlockPos P_STORE_CHEST = new BlockPos(19, 2, 17);
	private static final BlockPos P_BOX = new BlockPos(8, 2, 17);

	private static Villager porter(GameTestHelper helper) {
		helper.setBlock(P_STOREHOUSE, ModBlocks.STOREHOUSE);
		helper.setBlock(P_STORE_CHEST, Blocks.CHEST);
		Villager porter = helper.spawn(EntityType.VILLAGER, new BlockPos(18, 2, 18));
		Porters.employ(helper.getLevel(), porter, helper.absolutePos(P_STOREHOUSE));
		return porter;
	}

	private static void portVillage(GameTestHelper helper) {
		Leftovers.clear(helper);
		helper.setDayTime(2000); // work hours
		Leftovers.village(helper, 48);
		strayDropBoxes(helper);
	}

	/**
	 * Room for exactly part of the load: the store chest holds 63 cobblestone in each slot (room for 27), the miner has
	 * 128 (96 spare). The porter carries exactly 27: the store fills, the miner keeps 101, the porter's bag ends empty.
	 */
	//$ gametest_ticks_batch BIG '1600' '"qaRoom1102cPorterExact"'
	@GameTest(template = BIG, timeoutTicks = 1600, batch = "qaRoom1102cPorterExact")
	public void qaThePorterCarriesExactlyTheRoomInTheStacks(GameTestHelper helper) {
		portVillage(helper);
		helper.setBlock(P_BENCH, ModBlocks.MINERS_BENCH);
		helper.setBlock(P_MINER_CHEST, Blocks.CHEST);
		Villager miner = helper.spawn(EntityType.VILLAGER, new BlockPos(3, 2, 3));
		io.github.jcondedata.aliveworkplace.mine.Miners.employ(helper.getLevel(), miner, helper.absolutePos(P_BENCH));
		Container minersChest = (Container) helper.getBlockEntity(P_MINER_CHEST);
		minersChest.setItem(0, new ItemStack(Items.COBBLESTONE, 64));
		minersChest.setItem(1, new ItemStack(Items.COBBLESTONE, 64));
		Villager porter = porter(helper);
		Container store = (Container) helper.getBlockEntity(P_STORE_CHEST);
		fillEach(store, Items.COBBLESTONE, 63);
		helper.succeedWhen(() -> {
			int stored = store.countItem(Items.COBBLESTONE);
			int left = minersChest.countItem(Items.COBBLESTONE);
			int bag = ModAttachments.BUILDER_BAG.getOrCreate(porter).count(Items.COBBLESTONE);
			helper.assertTrue(stored + left + bag == 27 * 63 + 128, "cobblestone lost or made: " + stored + " + " + left + " + " + bag);
			helper.assertTrue(stored == 27 * 64 && left == 128 - 27 && ModAttachments.BUILDER_BAG.getOrCreate(porter).isEmpty(),
				"the store (room for 27) holds " + stored + " of " + 27 * 64 + "; the miner keeps " + left + " (expected 101); the porter carries " + bag);
		});
	}

	/**
	 * 16-stack items: every slot of the store chest holds 16 ender pearls, a full stack though far from 64. A Drop Box with
	 * 20 ender pearls by the Storehouse: the porter takes none (no room), the pearls stay in the box.
	 */
	//$ gametest_ticks_batch BIG '700' '"qaRoom1102cPorterPearlsFull"'
	@GameTest(template = BIG, timeoutTicks = 700, batch = "qaRoom1102cPorterPearlsFull")
	public void qaThePorterLeavesPearlsForAStoreFullOfPearlStacks(GameTestHelper helper) {
		portVillage(helper);
		Villager porter = porter(helper);
		Container store = (Container) helper.getBlockEntity(P_STORE_CHEST);
		fillEach(store, Items.ENDER_PEARL, 16);
		helper.setBlock(P_BOX, ModBlocks.DROP_BOX);
		Container box = (Container) helper.getBlockEntity(P_BOX);
		box.setItem(0, new ItemStack(Items.ENDER_PEARL, 16));
		box.setItem(1, new ItemStack(Items.ENDER_PEARL, 4));
		helper.runAfterDelay(600, () -> {
			helper.assertTrue(box.countItem(Items.ENDER_PEARL) == 20, "the porter took " + (20 - box.countItem(Items.ENDER_PEARL))
				+ " ender pearls for a store whose every pearl stack is full");
			helper.assertTrue(ModAttachments.BUILDER_BAG.getOrCreate(porter).isEmpty(), "the porter carries "
				+ ModAttachments.BUILDER_BAG.getOrCreate(porter).stacks());
			helper.assertTrue(store.countItem(Items.ENDER_PEARL) == 27 * 16, "the store holds " + store.countItem(Items.ENDER_PEARL) + " pearls");
			helper.succeed();
		});
	}

	/** The other side: every slot holds 15 ender pearls (room for 27); the Drop Box's 20 pearls all go in. */
	//$ gametest_ticks_batch BIG '1600' '"qaRoom1102cPorterPearlsRoom"'
	@GameTest(template = BIG, timeoutTicks = 1600, batch = "qaRoom1102cPorterPearlsRoom")
	public void qaThePorterFillsPearlStacksWithRoom(GameTestHelper helper) {
		portVillage(helper);
		Villager porter = porter(helper);
		Container store = (Container) helper.getBlockEntity(P_STORE_CHEST);
		fillEach(store, Items.ENDER_PEARL, 15);
		helper.setBlock(P_BOX, ModBlocks.DROP_BOX);
		Container box = (Container) helper.getBlockEntity(P_BOX);
		box.setItem(0, new ItemStack(Items.ENDER_PEARL, 16));
		box.setItem(1, new ItemStack(Items.ENDER_PEARL, 4));
		helper.succeedWhen(() -> {
			int stored = store.countItem(Items.ENDER_PEARL);
			int bag = ModAttachments.BUILDER_BAG.getOrCreate(porter).count(Items.ENDER_PEARL);
			helper.assertTrue(stored + box.countItem(Items.ENDER_PEARL) + bag == 27 * 15 + 20, "pearls lost or made: " + stored + " + "
				+ box.countItem(Items.ENDER_PEARL) + " + " + bag);
			helper.assertTrue(stored == 27 * 15 + 20 && box.countItem(Items.ENDER_PEARL) == 0 && bag == 0, "the store (room for 27 pearls) holds " + stored
				+ " of " + (27 * 15 + 20) + "; the Drop Box keeps " + box.countItem(Items.ENDER_PEARL) + "; the porter carries " + bag);
		});
	}
}
