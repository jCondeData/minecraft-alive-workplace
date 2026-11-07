package io.github.jcondedata.aliveworkplace.test;

import io.github.jcondedata.aliveworkplace.registry.ModAttachments;
import io.github.jcondedata.aliveworkplace.registry.ModBlocks;
import io.github.jcondedata.aliveworkplace.store.HaulerGolems;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Container;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.animal.IronGolem;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/**
 * B847, B848: Hauler Golems and porters look at the Drop Boxes nearest first and empty the first whose goods fit the
 * Storehouse, so a nearer box of goods with no room no longer hides a farther one. These are the likeliest ways that
 * breaks: when both boxes fit the nearer still goes first; when none fits nothing is taken and nothing dropped; and a
 * golem saved and loaded with the farther box's logs still brings them in.
 */
public class DropBoxFitOrderGameTests implements net.fabricmc.fabric.api.gametest.v1.FabricGameTest {
	private static final String HUGE = "aliveworkplace_test:huge_area";
	private static final String AREA = "aliveworkplace_test:big_area";
	private static final BlockPos NEAR_BOX = QaStackRoom1102cGameTests.HAUL_BOX;
	private static final BlockPos FAR_BOX = QaLaneC1102eGameTests.FAR_BOX;

	private static int count(GameTestHelper helper, BlockPos pos, Item item) {
		return QaStackRoom1102cGameTests.count(helper, pos, item);
	}

	/** A hauler's Storehouse and two Drop Boxes (13 and 26 blocks off) holding {@code near} and {@code far}; no worker has goods. */
	private static Container haulerTwoBoxes(GameTestHelper helper, ItemStack near, ItemStack far) {
		Container store = QaStackRoom1102cGameTests.storehouse(helper);
		helper.setBlock(NEAR_BOX, ModBlocks.DROP_BOX);
		((Container) helper.getBlockEntity(NEAR_BOX)).setItem(0, near);
		helper.setBlock(FAR_BOX, ModBlocks.DROP_BOX);
		((Container) helper.getBlockEntity(FAR_BOX)).setItem(0, far);
		QaStackRoom1102cGameTests.lumberjack(helper, QaStackRoom1102cGameTests.FAR_JACK_CHEST, 0);
		return store;
	}

	/** Items lying on the ground in and around the test area. */
	private static int onGround(GameTestHelper helper) {
		int n = 0;
		for (ItemEntity drop : helper.getLevel().getEntitiesOfClass(ItemEntity.class, helper.getBounds().inflate(16))) {
			n += drop.getItem().getCount();
		}
		return n;
	}

	/** Both boxes' goods fit an empty Storehouse: the golem's first load is the nearer box's cobblestone, and both come in. */
	//$ gametest_ticks_batch HUGE '4800' '"dropBoxFitOrderHaulerBothFit"'
	@GameTest(template = HUGE, timeoutTicks = 4800, batch = "dropBoxFitOrderHaulerBothFit")
	public void theHaulerEmptiesTheNearerDropBoxFirstWhenBothFit(GameTestHelper helper) {
		haulerTwoBoxes(helper, new ItemStack(Items.COBBLESTONE, 32), new ItemStack(Items.OAK_LOG, 32));
		IronGolem golem = QaStackRoom1102cGameTests.hauler(helper);
		Item[] first = {null};
		helper.onEachTick(() -> {
			if (first[0] == null && !HaulerGolems.load(golem).isEmpty()) {
				first[0] = HaulerGolems.load(golem).keySet().iterator().next();
			}
		});
		helper.succeedWhen(() -> {
			BlockPos chest = QaStackRoom1102cGameTests.STORE_CHEST;
			helper.assertTrue(count(helper, chest, Items.COBBLESTONE) == 32 && count(helper, chest, Items.OAK_LOG) == 32
				&& HaulerGolems.load(golem).isEmpty(), "the Storehouse has " + count(helper, chest, Items.COBBLESTONE) + " of 32 cobblestone and "
				+ count(helper, chest, Items.OAK_LOG) + " of 32 logs; the golem carries " + HaulerGolems.load(golem));
			helper.assertTrue(first[0] == Items.COBBLESTONE, "the golem's first load was " + first[0] + ", not the nearer Drop Box's cobblestone");
		});
	}

	/** A full Storehouse and two Drop Boxes whose goods don't fit: for 1200 ticks nothing is taken and nothing dropped. */
	//$ gametest_ticks_batch HUGE '1400' '"dropBoxFitOrderHaulerNoneFits"'
	@GameTest(template = HUGE, timeoutTicks = 1400, batch = "dropBoxFitOrderHaulerNoneFits")
	public void theHaulerTakesNothingWhenNoDropBoxFits(GameTestHelper helper) {
		Container store = haulerTwoBoxes(helper, new ItemStack(Items.OAK_LOG, 32), new ItemStack(Items.DIRT, 32));
		QaStackRoom1102cGameTests.fillEach(store, Items.COBBLESTONE, 64);
		IronGolem golem = QaStackRoom1102cGameTests.hauler(helper);
		helper.runAtTickTime(1200, () -> {
			helper.assertTrue(count(helper, NEAR_BOX, Items.OAK_LOG) == 32 && count(helper, FAR_BOX, Items.DIRT) == 32,
				"goods left a Drop Box though none fits: logs " + count(helper, NEAR_BOX, Items.OAK_LOG) + ", dirt " + count(helper, FAR_BOX, Items.DIRT));
			helper.assertTrue(HaulerGolems.load(golem).isEmpty() && onGround(helper) == 0, "the golem carries " + HaulerGolems.load(golem)
				+ " and " + onGround(helper) + " items lie on the ground");
			helper.assertTrue(count(helper, QaStackRoom1102cGameTests.STORE_CHEST, Items.COBBLESTONE) == 27 * 64, "the full Storehouse changed");
			helper.succeed();
		});
	}

	/**
	 * B847's shape (the nearer box's cobblestone has no room, the farther box's logs do), and the golem is saved and loaded
	 * as soon as it carries the logs: the loaded golem brings them in, and the cobblestone stays.
	 */
	//$ gametest_ticks_batch HUGE '4800' '"dropBoxFitOrderHaulerReload"'
	@GameTest(template = HUGE, timeoutTicks = 4800, batch = "dropBoxFitOrderHaulerReload")
	public void aHaulerReloadedWithTheFartherBoxsLogsBringsThemIn(GameTestHelper helper) {
		Container store = haulerTwoBoxes(helper, new ItemStack(Items.COBBLESTONE, 32), new ItemStack(Items.OAK_LOG, 32));
		QaStackRoom1102cGameTests.fillEach(store, Items.COBBLESTONE, 64);
		store.setItem(26, new ItemStack(Items.OAK_LOG, 1));
		IronGolem first = QaStackRoom1102cGameTests.hauler(helper);
		IronGolem[] golem = {first};
		ServerLevel level = helper.getLevel();
		helper.onEachTick(() -> {
			if (golem[0] == first && HaulerGolems.load(first).getOrDefault(Items.OAK_LOG, 0) > 0) {
				CompoundTag tag = new CompoundTag();
				helper.assertTrue(first.save(tag), "the golem wasn't saved");
				first.discard();
				Entity loaded = EntityType.loadEntityRecursive(tag, level, e -> e);
				helper.assertTrue(loaded instanceof IronGolem, "the save didn't load as a golem");
				level.addFreshEntity(loaded);
				golem[0] = (IronGolem) loaded;
				Leftovers.after(helper, loaded::discard);
			}
		});
		helper.succeedWhen(() -> {
			helper.assertTrue(golem[0] != first, "waiting for the golem to carry the logs and be reloaded");
			int stored = count(helper, QaStackRoom1102cGameTests.STORE_CHEST, Items.OAK_LOG);
			helper.assertTrue(stored == 33 && count(helper, FAR_BOX, Items.OAK_LOG) == 0 && HaulerGolems.load(golem[0]).isEmpty(),
				"after the reload the Storehouse has " + stored + " of 33 logs; the farther Drop Box keeps " + count(helper, FAR_BOX, Items.OAK_LOG)
					+ "; the golem carries " + HaulerGolems.load(golem[0]));
			helper.assertTrue(count(helper, NEAR_BOX, Items.COBBLESTONE) == 32 && onGround(helper) == 0, "the nearer box has "
				+ count(helper, NEAR_BOX, Items.COBBLESTONE) + " of 32 cobblestone and " + onGround(helper) + " items lie on the ground");
		});
	}

	/** At a porter's Storehouse both boxes' goods fit: the porter's first bagful is the nearer box's cobblestone, and both come in. */
	//$ gametest_ticks_batch AREA '2400' '"dropBoxFitOrderPorterBothFit"'
	@GameTest(template = AREA, timeoutTicks = 2400, batch = "dropBoxFitOrderPorterBothFit")
	public void thePorterEmptiesTheNearerDropBoxFirstWhenBothFit(GameTestHelper helper) {
		Villager porter = QaLaneC1102eGameTests.porterTwoBoxes(helper, true);
		((Container) helper.getBlockEntity(QaLaneC1102eGameTests.P_STORE_CHEST)).setItem(0, ItemStack.EMPTY); // room for cobblestone too
		Item[] first = {null};
		helper.onEachTick(() -> {
			var bag = ModAttachments.BUILDER_BAG.getOrCreate(porter);
			if (first[0] == null && (bag.count(Items.COBBLESTONE) > 0 || bag.count(Items.OAK_LOG) > 0)) {
				first[0] = bag.count(Items.COBBLESTONE) > 0 ? Items.COBBLESTONE : Items.OAK_LOG;
			}
		});
		helper.succeedWhen(() -> {
			BlockPos chest = QaLaneC1102eGameTests.P_STORE_CHEST;
			helper.assertTrue(count(helper, QaLaneC1102eGameTests.P_NEAR_BOX, Items.COBBLESTONE) == 0 && count(helper, QaLaneC1102eGameTests.P_FAR_BOX, Items.OAK_LOG) == 0
				&& count(helper, chest, Items.COBBLESTONE) == 25 * 64 + 32 && count(helper, chest, Items.OAK_LOG) == 33,
				"the Storehouse has " + count(helper, chest, Items.COBBLESTONE) + " of " + (25 * 64 + 32) + " cobblestone and "
					+ count(helper, chest, Items.OAK_LOG) + " of 33 logs");
			helper.assertTrue(first[0] == Items.COBBLESTONE, "the porter's first bagful was " + first[0] + ", not the nearer Drop Box's cobblestone");
		});
	}

	/** At a porter's full Storehouse neither box's goods fit: for 1200 ticks nothing is taken and nothing dropped. */
	//$ gametest_ticks_batch AREA '1400' '"dropBoxFitOrderPorterNoneFits"'
	@GameTest(template = AREA, timeoutTicks = 1400, batch = "dropBoxFitOrderPorterNoneFits")
	public void thePorterTakesNothingWhenNoDropBoxFits(GameTestHelper helper) {
		Villager porter = QaLaneC1102eGameTests.porterTwoBoxes(helper, true);
		((Container) helper.getBlockEntity(QaLaneC1102eGameTests.P_STORE_CHEST)).setItem(26, new ItemStack(Items.COBBLESTONE, 64)); // no room for logs
		helper.runAtTickTime(1200, () -> {
			var bag = ModAttachments.BUILDER_BAG.getOrCreate(porter);
			helper.assertTrue(count(helper, QaLaneC1102eGameTests.P_NEAR_BOX, Items.COBBLESTONE) == 32 && count(helper, QaLaneC1102eGameTests.P_FAR_BOX, Items.OAK_LOG) == 32,
				"goods left a Drop Box though none fits: cobblestone " + count(helper, QaLaneC1102eGameTests.P_NEAR_BOX, Items.COBBLESTONE) + ", logs "
					+ count(helper, QaLaneC1102eGameTests.P_FAR_BOX, Items.OAK_LOG));
			helper.assertTrue(bag.count(Items.COBBLESTONE) == 0 && bag.count(Items.OAK_LOG) == 0 && onGround(helper) == 0,
				"the porter's bag holds " + bag.count(Items.COBBLESTONE) + " cobblestone and " + bag.count(Items.OAK_LOG) + " logs; "
					+ onGround(helper) + " items lie on the ground");
			helper.succeed();
		});
	}
}
