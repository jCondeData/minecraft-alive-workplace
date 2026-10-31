package io.github.jcondedata.aliveworkplace.test;

import io.github.jcondedata.aliveworkplace.legend.GolemSmith;
import io.github.jcondedata.aliveworkplace.registry.ModBlocks;
import io.github.jcondedata.aliveworkplace.registry.ModVillagers;
import io.github.jcondedata.aliveworkplace.store.HaulerGolems;
import io.github.jcondedata.aliveworkplace.work.Jobs;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Container;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.animal.IronGolem;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * QA (B593's fix, HaulerGolems.putBack/workerChest): with no Drop Box and the only Storehouse walled in, a hauler's load
 * goes back into the worker's chest it came from, or, when that has no room, the nearest village worker's chest with
 * room, never a Storehouse's. These are the edges: the source chest filled up after the fetch, room only in the
 * Storehouse's chest, and room in the source for part of the load. Every tick the logs are counted everywhere (worker
 * chests, the Storehouse chest, the golem's load and items on the ground): nothing may be lost or doubled.
 *
 * <p>Three lumberjacks, each chest more than {@code SupplyContainers.RADIUS} (8) from the other workstations, so every
 * chest is one worker's own (two chests 4 apart are both in each worker's stash, and "the chest it came from" blurs).
 * Every chest starts with logs, so whichever lumberjack the golem looks from (its own chest is not in its list), it
 * fetches from one of the others; the test finds the source by which chest lost logs. Filler is bone meal, which a
 * lumberjack keeps whole (so the golem never hauls it).
 */
public class QaHaulerPutBackEdgesGameTests implements net.fabricmc.fabric.api.gametest.v1.FabricGameTest {
	private static final String HUGE = "aliveworkplace_test:huge_area";
	private static final Logger LOGGER = LoggerFactory.getLogger("aliveworkplace-qa");
	private static final BlockPos STORE_CHEST = new BlockPos(4, 2, 4);
	private static final BlockPos[] CHESTS = {new BlockPos(12, 2, 27), new BlockPos(22, 2, 27), new BlockPos(26, 2, 15)};
	private static final Item FILLER = Items.BONE_MEAL;
	/** Ticks after the fetch by which the golem has given up on the walled-in Storehouse and tried a put-back (observed ~600). */
	private static final int SETTLE = 1200;

	/**
	 * The source chest is filled to the brim right after the golem fetches its 64 logs: they go into another lumberjack's
	 * chest with room, and the golem is free. 192 logs in all, before and after; the full chest is left as it was.
	 */
	//$ gametest_ticks_batch HUGE '2400' '"qaHaulerPutBackSourceFull"'
	@GameTest(template = HUGE, timeoutTicks = 2400, batch = "qaHaulerPutBackSourceFull")
	public void aHaulerWhoseSourceChestFilledUpPutsTheLoadInAnotherWorkersChest(GameTestHelper helper) {
		village(helper);
		for (BlockPos c : CHESTS) {
			fill(helper, c, Items.OAK_LOG, 1);
		}
		IronGolem golem = hauler(helper);
		int[] source = {-1};
		helper.onEachTick(() -> {
			assertConserved(helper, golem, 192);
			assertNoneInStorehouse(helper);
			if (source[0] < 0 && carried(golem) > 0) {
				helper.assertTrue(carried(golem) == 64, "the golem took " + carried(golem) + " logs, not one chest's 64");
				source[0] = sourceOf(helper, 64, 64);
				fillEmpty(helper, CHESTS[source[0]], 0);
			}
		});
		helper.succeedWhen(() -> {
			helper.assertTrue(source[0] >= 0, "the golem never fetched the lumberjacks' logs");
			helper.assertTrue(carried(golem) == 0, "the source chest full, two lumberjacks' chests with room: the golem still holds "
				+ carried(golem) + " logs at " + golem.blockPosition() + " (chests " + spread(helper) + ")");
			helper.assertTrue(logs(helper, CHESTS[source[0]]) == 0 && count(helper, CHESTS[source[0]], FILLER) == 27 * 64,
				"the full source chest changed: " + logs(helper, CHESTS[source[0]]) + " logs, " + count(helper, CHESTS[source[0]], FILLER) + " bone meal");
			LOGGER.info("qa hauler put back: source chest {} full, logs now {}, the golem free", source[0], spread(helper));
		});
	}

	/**
	 * No worker's chest has room (the source fills up after the fetch, the others are full from the start) and the only
	 * empty chest is the walled-in Storehouse's: the put-back never uses it, and the golem keeps its 64 logs whole (none
	 * dropped, none lost, none put anywhere else).
	 */
	//$ gametest_ticks_batch HUGE '2400' '"qaHaulerPutBackOnlyStorehouse"'
	@GameTest(template = HUGE, timeoutTicks = 2400, batch = "qaHaulerPutBackOnlyStorehouse")
	public void aHaulerWithNoWorkerChestWithRoomKeepsItsLoadAndNeverUsesTheStorehouseChest(GameTestHelper helper) {
		village(helper);
		for (BlockPos c : CHESTS) {
			fill(helper, c, Items.OAK_LOG, 1);
			fillEmpty(helper, c, 0); // full from the start: 64 logs to fetch and 26 stacks of bone meal
		}
		IronGolem golem = hauler(helper);
		int[] source = {-1};
		int[] fetchedAt = {-1};
		int[] tick = {0};
		helper.onEachTick(() -> {
			tick[0]++;
			assertConserved(helper, golem, 192);
			assertNoneInStorehouse(helper);
			if (fetchedAt[0] < 0 && carried(golem) > 0) {
				fetchedAt[0] = tick[0];
				source[0] = sourceOf(helper, 64, 64);
				fillEmpty(helper, CHESTS[source[0]], 0);
			}
			if (source[0] >= 0) {
				for (BlockPos c : CHESTS) {
					helper.assertTrue(count(helper, c, FILLER) + logs(helper, c) == 27 * 64,
						"a full worker's chest changed: " + spread(helper));
				}
			}
		});
		helper.succeedWhen(() -> {
			helper.assertTrue(fetchedAt[0] >= 0, "the golem never fetched the lumberjacks' logs");
			helper.assertTrue(tick[0] >= fetchedAt[0] + SETTLE, "waiting for the put-back to be tried");
			helper.assertTrue(carried(golem) == 64, "no worker's chest with room: the golem should keep all 64 logs, but carries "
				+ carried(golem) + " (chests " + spread(helper) + ")");
			LOGGER.info("qa hauler put back: no worker chest with room, the golem keeps 64 logs ({} ticks after the fetch at {})",
				tick[0] - fetchedAt[0], fetchedAt[0]);
		});
	}

	/**
	 * The golem takes two stacks (128 logs) from one lumberjack, whose chest then fills up but for one slot: 64 go back
	 * there, the other 64 into another lumberjack's chest with room, and the golem is free. 384 logs in all.
	 */
	//$ gametest_ticks_batch HUGE '2400' '"qaHaulerPutBackPartRoom"'
	@GameTest(template = HUGE, timeoutTicks = 2400, batch = "qaHaulerPutBackPartRoom")
	public void aHaulerWhoseSourceChestHasRoomForPartSplitsTheLoad(GameTestHelper helper) {
		village(helper);
		for (BlockPos c : CHESTS) {
			fill(helper, c, Items.OAK_LOG, 2);
		}
		IronGolem golem = hauler(helper);
		int[] source = {-1};
		helper.onEachTick(() -> {
			assertConserved(helper, golem, 384);
			assertNoneInStorehouse(helper);
			if (source[0] < 0 && carried(golem) > 0) {
				helper.assertTrue(carried(golem) == 128, "the golem took " + carried(golem) + " logs (the test needs both stacks of one chest)");
				source[0] = sourceOf(helper, 128, 128);
				fillEmpty(helper, CHESTS[source[0]], 1);
			}
		});
		helper.succeedWhen(() -> {
			helper.assertTrue(source[0] >= 0, "the golem never fetched the lumberjacks' logs");
			helper.assertTrue(carried(golem) == 0, "the source with one free slot, two lumberjacks' chests with room: the golem still holds "
				+ carried(golem) + " logs at " + golem.blockPosition() + " (chests " + spread(helper) + ")");
			helper.assertTrue(logs(helper, CHESTS[source[0]]) == 64, "the golem is free but its source chest (one free slot) got "
				+ logs(helper, CHESTS[source[0]]) + " of the logs back, not 64 (chests " + spread(helper) + ")");
			helper.assertTrue(count(helper, CHESTS[source[0]], FILLER) == 26 * 64, "the source chest's bone meal changed");
			LOGGER.info("qa hauler put back: 64 logs back in source chest {}'s free slot, logs now {}, the golem free", source[0], spread(helper));
		});
	}

	/** The walled-in Storehouse (its chest empty) and three lumberjacks with empty chests; no Drop Box (as QaHaulerNoDropBoxGameTests). */
	private static void village(GameTestHelper helper) {
		Leftovers.clear(helper);
		GolemHaulerFightGameTests.strayStorehouses(helper);
		ServerLevel level = helper.getLevel();
		// workers share their chests across the village (off by default in GameTests)
		int radius = io.github.jcondedata.aliveworkplace.work.Village.RADIUS;
		io.github.jcondedata.aliveworkplace.work.Village.RADIUS = 48;
		Leftovers.after(helper, () -> io.github.jcondedata.aliveworkplace.work.Village.RADIUS = radius);
		// the only Storehouse, walled in and roofed over at x 1..8, z 1..7
		for (int x = 1; x <= 8; x++) {
			for (int y = 2; y <= 4; y++) {
				for (int z = 1; z <= 7; z++) {
					boolean shell = x == 1 || x == 8 || z == 1 || z == 7 || y == 4;
					helper.setBlock(new BlockPos(x, y, z), shell ? Blocks.STONE_BRICKS : Blocks.AIR);
				}
			}
		}
		helper.setBlock(new BlockPos(3, 2, 4), ModBlocks.STOREHOUSE);
		helper.setBlock(STORE_CHEST, Blocks.CHEST);
		for (BlockPos chest : CHESTS) {
			BlockPos block = chest.north();
			helper.setBlock(block, ModBlocks.CHOPPING_BLOCK);
			helper.setBlock(chest, Blocks.CHEST);
			Villager jack = helper.spawn(EntityType.VILLAGER, block.east());
			jack.setNoAi(true);
			Jobs.employ(level, jack, helper.absolutePos(block), ModVillagers.CHOPPING_BLOCK_POI, ModVillagers.LUMBERJACK);
		}
	}

	private static IronGolem hauler(GameTestHelper helper) {
		IronGolem golem = GolemSmith.build(helper.getLevel(), helper.absolutePos(new BlockPos(14, 2, 14)), GolemSmith.Role.HAULER);
		helper.assertTrue(golem != null, "a hauler");
		Leftovers.after(helper, golem::discard);
		return golem;
	}

	/** Puts {@code stacks} full stacks of {@code item} into the chest's first slots. */
	private static void fill(GameTestHelper helper, BlockPos chest, Item item, int stacks) {
		Container c = (Container) helper.getBlockEntity(chest);
		for (int i = 0; i < stacks; i++) {
			c.setItem(i, new ItemStack(item, 64));
		}
	}

	/** Fills the chest's empty slots with bone meal, leaving {@code free} of them empty. */
	private static void fillEmpty(GameTestHelper helper, BlockPos chest, int free) {
		Container c = (Container) helper.getBlockEntity(chest);
		int left = free;
		for (int i = c.getContainerSize() - 1; i >= 0; i--) {
			if (c.getItem(i).isEmpty()) {
				if (left > 0) {
					left--;
				} else {
					c.setItem(i, new ItemStack(FILLER, 64));
				}
			}
		}
		c.setChanged();
	}

	/** The one lumberjack's chest the golem's {@code took} logs came from (the only one now holding that many fewer). */
	private static int sourceOf(GameTestHelper helper, int had, int took) {
		int found = -1;
		for (int i = 0; i < CHESTS.length; i++) {
			int now = logs(helper, CHESTS[i]);
			if (now != had) {
				helper.assertTrue(found < 0 && now == had - took, "the golem's " + took + " logs came from more than one chest: " + spread(helper));
				found = i;
			}
		}
		helper.assertTrue(found >= 0, "no chest lost the golem's " + took + " logs: " + spread(helper));
		return found;
	}

	/** Every log in the test, wherever it is, equals {@code expected}: the chests, the golem's load, items on the ground. */
	private static void assertConserved(GameTestHelper helper, IronGolem golem, int expected) {
		int chests = logs(helper, STORE_CHEST);
		for (BlockPos c : CHESTS) {
			chests += logs(helper, c);
		}
		int ground = 0;
		for (ItemEntity e : helper.getLevel().getEntitiesOfClass(ItemEntity.class, helper.getBounds().inflate(16),
			e -> e.isAlive() && e.getItem().is(Items.OAK_LOG))) {
			ground += e.getItem().getCount();
		}
		int load = carried(golem);
		helper.assertTrue(load == HaulerGolems.load(golem).getOrDefault(Items.OAK_LOG, 0), "the golem carries something besides logs: "
			+ HaulerGolems.load(golem));
		helper.assertTrue(chests + load + ground == expected, "logs lost or doubled: " + chests + " in chests + " + load + " carried + "
			+ ground + " on the ground = " + (chests + load + ground) + ", not " + expected);
	}

	private static void assertNoneInStorehouse(GameTestHelper helper) {
		helper.assertTrue(logs(helper, STORE_CHEST) == 0, logs(helper, STORE_CHEST) + " logs got into the walled-in Storehouse's chest");
	}

	private static String spread(GameTestHelper helper) {
		StringBuilder out = new StringBuilder();
		for (BlockPos c : CHESTS) {
			out.append(out.isEmpty() ? "" : "/").append(logs(helper, c));
		}
		return out.toString();
	}

	private static int logs(GameTestHelper helper, BlockPos chest) {
		return count(helper, chest, Items.OAK_LOG);
	}

	private static int count(GameTestHelper helper, BlockPos chest, Item item) {
		return ((Container) helper.getBlockEntity(chest)).countItem(item);
	}

	private static int carried(IronGolem golem) {
		return HaulerGolems.load(golem).values().stream().mapToInt(Integer::intValue).sum();
	}
}
