package io.github.jcondedata.aliveworkplace.test;

import io.github.jcondedata.aliveworkplace.bee.BeekeeperWork;
import io.github.jcondedata.aliveworkplace.registry.ModBlocks;
import io.github.jcondedata.aliveworkplace.registry.ModVillagers;
import io.github.jcondedata.aliveworkplace.work.Jobs;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.Container;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.animal.Bee;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.BeehiveBlock;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CampfireBlock;
import net.minecraft.world.level.block.entity.BeehiveBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;

/**
 * QA (lane c, 2026-11-23): the Beekeeper's calm bees and breeding, from README "Beekeepers", where the mod breaks the
 * spec today. Not registered on main: these fail until the fixes land (the builder registers them with the fix).
 */
public class QaBeekeeperFlowers1123cBugTests implements FabricGameTest {
	private static final String AREA = "aliveworkplace_test:big_area";
	private static final BlockPos APIARY = new BlockPos(2, 2, 2);
	private static final BlockPos CHEST = new BlockPos(2, 2, 4);
	private static final BlockPos HIVE = new BlockPos(10, 3, 10);

	/**
	 * README: "Put a lit campfire under a hive and the bees stay calm; otherwise they fly out when it's harvested". A full
	 * hive over a lit campfire with two bees settled in it is harvested with a glass bottle: both bees must still be in
	 * the hive on the tick it's harvested (as when a player bottles a smoked hive).
	 */
	//$ gametest_ticks_batch AREA '1800' '"qaBeeBug1123cSmoked"'
	@GameTest(template = AREA, timeoutTicks = 1800, batch = "qaBeeBug1123cSmoked")
	public void aLitCampfireKeepsTheBeesInTheHiveWhenHarvested(GameTestHelper helper) {
		Leftovers.clear(helper);
		helper.setBlock(HIVE.below(), Blocks.CAMPFIRE.defaultBlockState().setValue(CampfireBlock.LIT, true));
		helper.setBlock(HIVE, Blocks.BEEHIVE.defaultBlockState().setValue(BeehiveBlock.HONEY_LEVEL, 5));
		BeehiveBlockEntity be = helper.getBlockEntity(HIVE);
		for (int i = 0; i < 2; i++) {
			Bee bee = helper.spawn(EntityType.BEE, HIVE.offset(i, 2, 1));
			bee.setHivePos(helper.absolutePos(HIVE));
			BeehiveBlockEntity.Occupant o = BeehiveBlockEntity.Occupant.of(bee);
			// settled in far longer than the test, so only something that sends them out brings them out
			be.storeBee(new BeehiveBlockEntity.Occupant(o.entityData(), 0, 100000));
			bee.discard();
		}
		helper.assertTrue(be.getOccupantCount() == 2, "setup: " + be.getOccupantCount() + " bees in the hive");
		beekeeper(helper, new ItemStack(Items.GLASS_BOTTLE, 2));
		AtomicLong harvested = new AtomicLong(-1);
		AtomicInteger inside = new AtomicInteger(-1);
		helper.onEachTick(() -> {
			BlockState s = helper.getBlockState(HIVE);
			if (harvested.get() < 0 && s.is(Blocks.BEEHIVE) && s.getValue(BeehiveBlock.HONEY_LEVEL) == 0) {
				harvested.set(helper.getTick());
				inside.set(((BeehiveBlockEntity) helper.getBlockEntity(HIVE)).getOccupantCount());
			}
		});
		helper.succeedWhen(() -> {
			helper.assertTrue(harvested.get() >= 0, "the hive was never harvested");
			helper.assertTrue(inside.get() == 2, "harvested over a lit campfire, " + inside.get() + " of 2 bees still in the hive ("
				+ (2 - inside.get()) + " flew out)");
			clearBees(helper);
		});
	}

	/**
	 * README: "feed pairs of bees flowers to breed until there are three bees a hive". Three adult bees live in a
	 * flowered hive with dandelions in the chest: for 1200 ticks no bee may be born and no flower taken. Bees fly about
	 * as they please (into the hive, out foraging), as on a player's farm.
	 */
	//$ gametest_ticks_batch AREA '1400' '"qaBeeBug1123cThreeBees"'
	@GameTest(template = AREA, timeoutTicks = 1400, batch = "qaBeeBug1123cThreeBees")
	public void threeBeesAHiveAreNeverBredToFour(GameTestHelper helper) {
		Leftovers.clear(helper);
		for (BlockPos p : BlockPos.betweenClosed(new BlockPos(4, 1, 4), new BlockPos(20, 1, 20))) {
			helper.setBlock(p, Blocks.GRASS_BLOCK);
		}
		helper.setBlock(HIVE.below(), Blocks.CAMPFIRE.defaultBlockState().setValue(CampfireBlock.LIT, true));
		helper.setBlock(HIVE, Blocks.BEEHIVE);
		for (BlockPos p : List.of(HIVE.offset(-2, -1, 0), HIVE.offset(2, -1, 0), HIVE.offset(0, -1, 2), HIVE.offset(0, -1, -2))) {
			helper.setBlock(p, Blocks.POPPY);
		}
		List<Bee> mine = new java.util.ArrayList<>();
		for (int i = 0; i < 3; i++) {
			Bee bee = helper.spawn(EntityType.BEE, HIVE.offset(i - 1, 2, 1));
			bee.setHivePos(helper.absolutePos(HIVE));
			bee.setPersistenceRequired();
			mine.add(bee);
		}
		beekeeper(helper, new ItemStack(Items.DANDELION, 8));
		Container chest = helper.getBlockEntity(CHEST);
		AtomicInteger most = new AtomicInteger();
		AtomicReference<String> before = new AtomicReference<>("");
		AtomicReference<String> seen = new AtomicReference<>("");
		AtomicInteger loved = new AtomicInteger();
		AtomicInteger counted = new AtomicInteger(99);
		AtomicReference<String> fewest = new AtomicReference<>("");
		helper.onEachTick(() -> {
			int n = beeCount(helper);
			if ((n > 3 && most.get() <= 3) || (chest.countItem(Items.DANDELION) < 8 && seen.get().isEmpty())) {
				seen.set(before.get());
			}
			most.set(Math.max(most.get(), n));
			before.set(where(helper));
			for (Bee b : mine) {
				loved.addAndGet(b.isInLove() ? 1 : 0);
			}
			if (nearCount(helper) < counted.get()) {
				counted.set(nearCount(helper));
				fewest.set("tick " + helper.getTick() + ": " + where(helper));
			}
		});
		helper.runAfterDelay(1200, () -> {
			// Fed flowers and in love is the Beekeeper breeding them; the head count also sees bees that wander in.
			helper.assertTrue(chest.countItem(Items.DANDELION) == 8 && loved.get() == 0, "three bees were fed to breed: "
				+ (8 - chest.countItem(Items.DANDELION)) + " dandelions taken, in love " + loved.get() + " bee-ticks, most bees seen " + most.get()
				+ "; the tick before the first feed or birth: " + seen.get() + "; fewest bees in the hive or within 16 blocks of the Apiary: " + fewest.get());
			clearBees(helper);
			helper.succeed();
		});
	}

	/** Where the bees are: in the hive, and how many fly within 16 blocks of the Apiary (or farther out). */
	private static String where(GameTestHelper helper) {
		AABB near = new AABB(helper.absolutePos(APIARY)).inflate(16);
		int in = 0;
		int far = 0;
		for (Bee b : bees(helper)) {
			if (near.contains(b.position())) {
				in++;
			} else {
				far++;
			}
		}
		return ((BeehiveBlockEntity) helper.getBlockEntity(HIVE)).getOccupantCount() + " in the hive, " + in
			+ " flying within 16 blocks of the Apiary, " + far + " farther out";
	}

	/** Bees in the hive plus bees flying within 16 blocks of the Apiary. */
	private static int nearCount(GameTestHelper helper) {
		AABB near = new AABB(helper.absolutePos(APIARY)).inflate(16);
		int n = ((BeehiveBlockEntity) helper.getBlockEntity(HIVE)).getOccupantCount();
		for (Bee b : bees(helper)) {
			n += near.contains(b.position()) ? 1 : 0;
		}
		return n;
	}

	/** Bees that leave the test would fly into later ones. */
	private static void clearBees(GameTestHelper helper) {
		for (Bee b : helper.getLevel().getEntitiesOfClass(Bee.class, helper.getBounds().inflate(48))) {
			b.discard();
		}
	}

	private static List<Bee> bees(GameTestHelper helper) {
		return helper.getLevel().getEntitiesOfClass(Bee.class, helper.getBounds().inflate(40), Bee::isAlive);
	}

	private static int beeCount(GameTestHelper helper) {
		return bees(helper).size() + ((BeehiveBlockEntity) helper.getBlockEntity(HIVE)).getOccupantCount();
	}

	private static Villager beekeeper(GameTestHelper helper, ItemStack... chest) {
		helper.setDayTime(2000);
		helper.setBlock(APIARY, ModBlocks.APIARY);
		helper.setBlock(CHEST, Blocks.CHEST);
		Container c = helper.getBlockEntity(CHEST);
		for (int i = 0; i < chest.length; i++) {
			c.setItem(i, chest[i]);
		}
		Villager villager = helper.spawn(EntityType.VILLAGER, new BlockPos(3, 2, 3));
		Jobs.employ(helper.getLevel(), villager, helper.absolutePos(APIARY), ModVillagers.APIARY_POI, ModVillagers.BEEKEEPER);
		helper.assertTrue(BeekeeperWork.isBeekeeper(villager), "setup: not a beekeeper");
		return villager;
	}

}
