package io.github.jcondedata.aliveworkplace.test;

import io.github.jcondedata.aliveworkplace.build.SupplyContainers;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.state.properties.ChestType;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import org.jetbrains.annotations.Nullable;

/**
 * B455, B412: the list of storage near a workstation ({@link SupplyContainers#find}, asked by every job, most of them
 * every tick) is kept until a block entity changes, and still says at once what a fresh scan says: a chest placed,
 * broken, joined to its other half or swapped for a furnace, another search radius, another building left out.
 * Measured by {@link SupplyContainers#scans}, a count, so it can't be flaky; each check runs inside one tick, each test
 * alone in its batch, so no other test's blocks change in between.
 */
public class StoreScanCacheGameTests implements net.fabricmc.fabric.api.gametest.v1.FabricGameTest {
	private static final String BUILD_AREA = "aliveworkplace_test:build_area";
	private static final String BIG_AREA = "aliveworkplace_test:big_area";
	private static final BlockPos BENCH = new BlockPos(3, 2, 3);

	/** The kept answer and a fresh scan's, which must be the same list in the same order. */
	private static List<BlockPos> both(GameTestHelper helper, BlockPos bench, @Nullable BoundingBox exclude, String when) {
		ServerLevel level = helper.getLevel();
		List<BlockPos> kept = SupplyContainers.find(level, bench, exclude);
		List<BlockPos> fresh;
		SupplyContainers.KEEP_SCANS = false;
		try {
			fresh = SupplyContainers.find(level, bench, exclude);
		} finally {
			SupplyContainers.KEEP_SCANS = true;
		}
		helper.assertTrue(kept.equals(fresh), when + ": the kept list " + kept + " isn't what a scan finds, " + fresh);
		return kept;
	}

	/** A village's workers asking again and again go through the chunks' block entities once. */
	//$ gametest_batch BUILD_AREA '"store_scan_repeat"'
	@GameTest(template = BUILD_AREA, batch = "store_scan_repeat")
	public void askingAgainDoesNotScanAgain(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		BlockPos bench = helper.absolutePos(BENCH);
		helper.setBlock(new BlockPos(2, 2, 2), Blocks.CHEST);
		helper.setBlock(new BlockPos(5, 2, 4), Blocks.BARREL);
		helper.setBlock(new BlockPos(4, 2, 2), Blocks.FURNACE);
		List<BlockPos> first = both(helper, bench, null, "two stores");
		helper.assertTrue(first.contains(helper.absolutePos(new BlockPos(2, 2, 2))) && first.contains(helper.absolutePos(new BlockPos(5, 2, 4))),
			"the chest and the barrel aren't both listed: " + first);
		helper.assertFalse(first.contains(helper.absolutePos(new BlockPos(4, 2, 2))), "a furnace is listed as storage");
		List<BlockPos> furnaces = SupplyContainers.furnaces(level, bench);
		long before = SupplyContainers.scans;
		for (int i = 0; i < 200; i++) {
			helper.assertTrue(SupplyContainers.find(level, bench, null).equals(first), "the list changed with nothing placed");
			helper.assertTrue(SupplyContainers.furnaces(level, bench).equals(furnaces), "the furnaces changed with nothing placed");
		}
		helper.assertTrue(SupplyContainers.scans == before, "400 asks scanned " + (SupplyContainers.scans - before) + " times (expected 0)");
		// the caller's list is its own: emptying it doesn't empty the next answer
		SupplyContainers.find(level, bench, null).clear();
		helper.assertTrue(SupplyContainers.find(level, bench, null).equals(first), "a caller clearing its list emptied the kept one");
		// with the switch off every ask scans, as before
		SupplyContainers.KEEP_SCANS = false;
		try {
			SupplyContainers.find(level, bench, null);
			SupplyContainers.find(level, bench, null);
		} finally {
			SupplyContainers.KEEP_SCANS = true;
		}
		helper.assertTrue(SupplyContainers.scans == before + 2, "with keeping off, 2 asks scanned " + (SupplyContainers.scans - before) + " times");
		helper.succeed();
	}

	/** A chest placed, filled, joined to a second half, broken or swapped for a furnace shows in the very next ask. */
	//$ gametest_batch BUILD_AREA '"store_scan_changes"'
	@GameTest(template = BUILD_AREA, batch = "store_scan_changes")
	public void aChangedChestIsNoticedAtOnce(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		BlockPos bench = helper.absolutePos(BENCH);
		BlockPos a = new BlockPos(2, 2, 5), b = new BlockPos(3, 2, 5), c = new BlockPos(5, 2, 2);
		List<BlockPos> none = both(helper, bench, null, "nothing placed");
		helper.assertFalse(none.contains(helper.absolutePos(a)), "a chest nobody placed is listed");

		helper.setBlock(a, Blocks.CHEST.defaultBlockState().setValue(ChestBlock.FACING, Direction.NORTH));
		List<BlockPos> one = both(helper, bench, null, "a chest placed");
		helper.assertTrue(one.contains(helper.absolutePos(a)) && one.size() == none.size() + 1, "the chest just placed isn't listed: " + one);

		// filled: what the chests hold is never kept, only where they are
		helper.assertTrue(SupplyContainers.count(level, one, Items.OAK_PLANKS) == 0, "an empty chest holds planks");
		helper.assertTrue(SupplyContainers.insert(level, one, new ItemStack(Items.OAK_PLANKS, 40)).isEmpty(), "the chest didn't take the planks");
		helper.assertTrue(SupplyContainers.count(level, SupplyContainers.find(level, bench, null), Items.OAK_PLANKS) == 40, "the planks put in aren't counted");

		// a second chest beside it, then both joined into a double chest: a change of state only, no new block entity
		helper.setBlock(b, Blocks.CHEST.defaultBlockState().setValue(ChestBlock.FACING, Direction.NORTH));
		List<BlockPos> two = both(helper, bench, null, "a second chest placed");
		helper.assertTrue(two.size() == none.size() + 2, "two single chests aren't two stores: " + two);
		long scansBefore = SupplyContainers.scans;
		helper.setBlock(b, Blocks.CHEST.defaultBlockState().setValue(ChestBlock.FACING, Direction.NORTH).setValue(ChestBlock.TYPE, ChestType.RIGHT));
		helper.assertTrue(level.getBlockState(helper.absolutePos(a)).getValue(ChestBlock.TYPE) == ChestType.LEFT, "vanilla didn't join the first chest to the second");
		List<BlockPos> joined = both(helper, bench, null, "the chests joined");
		helper.assertTrue(joined.size() == none.size() + 1, "a double chest isn't one store: " + joined);
		helper.assertTrue(SupplyContainers.scans == scansBefore + 2, "joining the halves should make the next ask scan once (and the comparison once): "
			+ (SupplyContainers.scans - scansBefore));
		// one half turned back into a single chest with nothing else told: a change of state only, no block entity placed
		scansBefore = SupplyContainers.scans;
		level.setBlock(helper.absolutePos(a), level.getBlockState(helper.absolutePos(a)).setValue(ChestBlock.TYPE, ChestType.SINGLE), 2 | 16);
		both(helper, bench, null, "one half single again");
		helper.assertTrue(SupplyContainers.scans == scansBefore + 2, "a chest changing state should make the next ask scan once (and the comparison once): "
			+ (SupplyContainers.scans - scansBefore));
		level.setBlock(helper.absolutePos(a), level.getBlockState(helper.absolutePos(a)).setValue(ChestBlock.TYPE, ChestType.LEFT), 2 | 16);
		joined = both(helper, bench, null, "the chests joined again");
		helper.assertTrue(SupplyContainers.count(level, joined, Items.OAK_PLANKS) == 40, "the double chest's planks are counted " + SupplyContainers.count(level, joined, Items.OAK_PLANKS) + " times over 40");

		// broken
		level.destroyBlock(helper.absolutePos(b), false);
		level.destroyBlock(helper.absolutePos(a), false);
		List<BlockPos> gone = both(helper, bench, null, "the chests broken");
		helper.assertTrue(gone.equals(none), "broken chests are still listed: " + gone);

		// a barrel, then a furnace in its place: storage, then not
		helper.setBlock(c, Blocks.BARREL);
		helper.assertTrue(both(helper, bench, null, "a barrel placed").contains(helper.absolutePos(c)), "the barrel isn't listed");
		helper.assertFalse(SupplyContainers.furnaces(level, bench).contains(helper.absolutePos(c)), "a barrel is listed as a furnace");
		helper.setBlock(c, Blocks.FURNACE);
		helper.assertFalse(both(helper, bench, null, "a furnace in the barrel's place").contains(helper.absolutePos(c)), "the furnace is listed as storage");
		helper.assertTrue(SupplyContainers.furnaces(level, bench).contains(helper.absolutePos(c)), "the furnace just placed isn't among the furnaces");
		helper.setBlock(c, Blocks.AIR);
		helper.assertFalse(SupplyContainers.furnaces(level, bench).contains(helper.absolutePos(c)), "the furnace taken away is still listed");
		helper.succeed();
	}

	/** Each bench, each building left out and each search radius has its own answer. */
	//$ gametest_batch BUILD_AREA '"store_scan_keys"'
	@GameTest(template = BUILD_AREA, batch = "store_scan_keys")
	public void everyQuestionHasItsOwnAnswer(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		BlockPos bench = helper.absolutePos(BENCH);
		BlockPos near = new BlockPos(3, 2, 4), far = new BlockPos(3, 2, 9);
		helper.setBlock(near, Blocks.CHEST);
		helper.setBlock(far, Blocks.BARREL);
		BoundingBox aroundFar = BoundingBox.fromCorners(helper.absolutePos(far).offset(-1, -1, -1), helper.absolutePos(far).offset(1, 1, 1));
		BoundingBox aroundNear = BoundingBox.fromCorners(helper.absolutePos(near).offset(-1, -1, -1), helper.absolutePos(near).offset(1, 1, 1));
		for (int round = 0; round < 3; round++) { // the second and third rounds are answered from the kept lists
			List<BlockPos> all = both(helper, bench, null, "nothing left out");
			helper.assertTrue(all.contains(helper.absolutePos(near)) && all.contains(helper.absolutePos(far)), "both stores should be listed: " + all);
			helper.assertTrue(all.indexOf(helper.absolutePos(near)) < all.indexOf(helper.absolutePos(far)), "the nearest store isn't first: " + all);
			List<BlockPos> withoutFar = both(helper, bench, aroundFar, "the far building left out");
			helper.assertTrue(withoutFar.contains(helper.absolutePos(near)) && !withoutFar.contains(helper.absolutePos(far)), "the far barrel wasn't left out: " + withoutFar);
			List<BlockPos> withoutNear = both(helper, bench, aroundNear, "the near building left out");
			helper.assertTrue(!withoutNear.contains(helper.absolutePos(near)) && withoutNear.contains(helper.absolutePos(far)), "the near chest wasn't left out: " + withoutNear);
			// another bench, 12 blocks on: the near chest is out of its reach
			List<BlockPos> other = both(helper, bench.offset(0, 0, 12), null, "another bench");
			helper.assertTrue(!other.contains(helper.absolutePos(near)) && other.contains(helper.absolutePos(far)), "the other bench's list is the first one's: " + other);
		}
		// the config's supplyRadius changed: the very next ask uses it
		int radius = SupplyContainers.RADIUS;
		try {
			SupplyContainers.RADIUS = 3;
			List<BlockPos> small = both(helper, bench, null, "radius 3");
			helper.assertTrue(small.contains(helper.absolutePos(near)) && !small.contains(helper.absolutePos(far)), "radius 3 still reaches 6 blocks: " + small);
		} finally {
			SupplyContainers.RADIUS = radius;
		}
		helper.assertTrue(both(helper, bench, null, "the radius back").contains(helper.absolutePos(far)), "the radius put back doesn't reach the barrel again");
		helper.succeed();
	}

	/** A kept list is scanned again after two seconds whatever happened, and not before. */
	//$ gametest_ticks_batch BUILD_AREA '200' '"store_scan_age"'
	@GameTest(template = BUILD_AREA, timeoutTicks = 200, batch = "store_scan_age")
	public void aKeptListIsReadAgainAfterTwoSeconds(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		BlockPos bench = helper.absolutePos(BENCH);
		helper.setBlock(new BlockPos(2, 2, 2), Blocks.CHEST);
		List<BlockPos> first = SupplyContainers.find(level, bench, null);
		long asked = level.getGameTime();
		helper.runAfterDelay(50, () -> {
			helper.assertTrue(level.getGameTime() - asked >= 40, "the test didn't wait two seconds");
			// other tests' blocks may have changed in between, so only this is certain: the first ask after two seconds scans
			long before = SupplyContainers.scans;
			List<BlockPos> again = SupplyContainers.find(level, bench, null);
			helper.assertTrue(SupplyContainers.scans == before + 1, "a list kept for over two seconds wasn't read again");
			helper.assertTrue(again.equals(first), "the list changed with nothing placed: " + again + " for " + first);
			SupplyContainers.find(level, bench, null);
			helper.assertTrue(SupplyContainers.scans == before + 1, "the ask right after scanned again");
			helper.succeed();
		});
	}

	/**
	 * A farmer with nothing to do counts its chests for spare seeds once a second, not every tick, and still composts
	 * them when they pile up later.
	 */
	//$ gametest_ticks_batch BIG_AREA '6000' '"store_scan_farmer"'
	@GameTest(template = BIG_AREA, timeoutTicks = 6000, batch = "store_scan_farmer")
	public void anIdleFarmerLooksForSpareSeedsOnceASecondAndStillComposts(GameTestHelper helper) {
		Leftovers.clear(helper);
		ServerLevel level = helper.getLevel();
		BlockPos composter = new BlockPos(2, 2, 2), chestAt = new BlockPos(2, 2, 4);
		helper.setDayTime(2000);
		helper.setBlock(composter, Blocks.COMPOSTER);
		helper.setBlock(chestAt, Blocks.CHEST);
		net.minecraft.world.Container chest = helper.getBlockEntity(chestAt);
		chest.setItem(0, new ItemStack(Items.WHEAT_SEEDS, 8));
		net.minecraft.world.entity.npc.Villager farmer = helper.spawn(net.minecraft.world.entity.EntityType.VILLAGER, new BlockPos(3, 2, 3));
		io.github.jcondedata.aliveworkplace.work.Jobs.employ(level, farmer, helper.absolutePos(composter), net.minecraft.world.entity.ai.village.poi.PoiTypes.FARMER,
			net.minecraft.world.entity.npc.VillagerProfession.FARMER);
		io.github.jcondedata.aliveworkplace.farm.Fields.start(level, farmer,
			BoundingBox.fromCorners(helper.absolutePos(new BlockPos(8, 1, 8)), helper.absolutePos(new BlockPos(9, 1, 9))));
		long[] looks = new long[1];
		helper.runAfterDelay(600, () -> looks[0] = io.github.jcondedata.aliveworkplace.farm.FieldWork.compostLooks);
		helper.runAfterDelay(800, () -> {
			helper.setDayTime(2000);
			long looked = io.github.jcondedata.aliveworkplace.farm.FieldWork.compostLooks - looks[0];
			helper.assertTrue(looked >= 1, "the farmer never looked for spare seeds in 200 ticks: the test doesn't reach the idle farmer");
			helper.assertTrue(looked <= 200 / io.github.jcondedata.aliveworkplace.farm.FieldWork.COMPOST_LOOK_TICKS + 2,
				"an idle farmer counted its chests " + looked + " times in 200 ticks (once a second is 10)");
			helper.assertTrue(chest.countItem(Items.BONE_MEAL) == 0, "bone meal with no spare seeds");
			// now the seeds pile up: three more stacks
			for (int slot = 1; slot <= 3; slot++) {
				chest.setItem(slot, new ItemStack(Items.WHEAT_SEEDS, 64));
			}
			helper.succeedWhen(() -> {
				var bag = io.github.jcondedata.aliveworkplace.registry.ModAttachments.BUILDER_BAG.getOrCreate(farmer);
				helper.assertTrue(chest.countItem(Items.BONE_MEAL) + bag.count(Items.BONE_MEAL) >= 1, "no bone meal from the seeds that piled up later");
			});
		});
	}
}
