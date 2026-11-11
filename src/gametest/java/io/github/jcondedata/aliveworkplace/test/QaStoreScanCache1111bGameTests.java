package io.github.jcondedata.aliveworkplace.test;

import io.github.jcondedata.aliveworkplace.blueprint.BlueprintData;
import io.github.jcondedata.aliveworkplace.build.BuildPlan;
import io.github.jcondedata.aliveworkplace.build.BuildSite;
import io.github.jcondedata.aliveworkplace.build.BuildSiteManager;
import io.github.jcondedata.aliveworkplace.build.Builders;
import io.github.jcondedata.aliveworkplace.build.SupplyContainers;
import io.github.jcondedata.aliveworkplace.registry.ModBlocks;
import io.github.jcondedata.aliveworkplace.registry.ModGameRules;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.core.Vec3i;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.Container;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;

/**
 * QA (qa-lane-b-1111-1237) for 43b2f71a8, the kept storage scan (B455, B412). The promise to a player: a builder,
 * crafter or Storehouse never acts on a stale list; a chest, barrel or furnace placed, broken or swapped anywhere in
 * reach is seen by the very next ask, and a store that is gone is never listed. Every check compares the kept answer
 * with a fresh scan in the same tick (no 40-tick wait), from the edges the feature's own tests don't reach: the far
 * corners of the reach in neighbouring chunks (at the default radius and the config's largest), another dimension at the
 * same coordinates, placements that skip neighbour updates, structure placement, explosions, a player breaking a chest,
 * every kind of block entity, a chunk dropping out and coming back, and a real builder.
 */
public class QaStoreScanCache1111bGameTests implements net.fabricmc.fabric.api.gametest.v1.FabricGameTest {
	private static final String AREA = "aliveworkplace_test:build_area";
	private static final String HUGE = "aliveworkplace_test:huge_area";
	private static final ResourceLocation TEST_HUT = ResourceLocation.fromNamespaceAndPath("aliveworkplace_test", "test_hut");

	// ------------------------------------------------------------------------------------------------ helpers

	/** The kept answer and a fresh scan's, which must be the same list in the same order (as StoreScanCacheGameTests). */
	private static List<BlockPos> both(GameTestHelper helper, ServerLevel level, BlockPos bench, String when) {
		List<BlockPos> kept = SupplyContainers.find(level, bench, null);
		List<BlockPos> fresh;
		SupplyContainers.KEEP_SCANS = false;
		try {
			fresh = SupplyContainers.find(level, bench, null);
		} finally {
			SupplyContainers.KEEP_SCANS = true;
		}
		helper.assertTrue(kept.equals(fresh), when + ": the kept list " + kept + " isn't what a scan finds, " + fresh);
		return kept;
	}

	/** {@link #both} for the furnaces. */
	private static List<BlockPos> bothFurnaces(GameTestHelper helper, ServerLevel level, BlockPos bench, String when) {
		List<BlockPos> kept = SupplyContainers.furnaces(level, bench);
		List<BlockPos> fresh;
		SupplyContainers.KEEP_SCANS = false;
		try {
			fresh = SupplyContainers.furnaces(level, bench);
		} finally {
			SupplyContainers.KEEP_SCANS = true;
		}
		helper.assertTrue(kept.equals(fresh), when + ": the kept furnaces " + kept + " aren't what a scan finds, " + fresh);
		return kept;
	}

	/** Remembers what stood at each spot this test changes outside its own area, and puts it back when the test ends. */
	private static final class Restore {
		private final List<Object[]> spots = new ArrayList<>();

		Restore(GameTestHelper helper) {
			Leftovers.after(helper, this::run);
		}

		/** Records the spot once; false (and nothing recorded) when another block entity stands there. */
		boolean mark(ServerLevel level, BlockPos pos) {
			for (Object[] s : spots) {
				if (s[0] == level && s[1].equals(pos)) {
					return true;
				}
			}
			BlockState old = level.getBlockState(pos);
			if (old.hasBlockEntity()) {
				return false;
			}
			spots.add(new Object[] {level, pos.immutable(), old});
			return true;
		}

		void run() {
			for (int i = spots.size() - 1; i >= 0; i--) {
				Object[] s = spots.get(i);
				ServerLevel level = (ServerLevel) s[0];
				BlockPos pos = (BlockPos) s[1];
				if (level.getBlockEntity(pos) instanceof Container c) {
					c.clearContent();
				}
				level.setBlock(pos, (BlockState) s[2], Block.UPDATE_CLIENTS);
			}
			spots.clear();
		}
	}

	/** The first x (or z) at or after {@code from} that is a chunk's last block. */
	private static int lastOfChunk(int from) {
		int x = from;
		while ((x & 15) != 15) {
			x++;
		}
		return x;
	}

	// ------------------------------------------------------------------------------------------------ 1. every chunk in reach

	/**
	 * A bench on a chunk's corner: the reach spans four chunks at radius 8 and nine at the config's largest, 32. A chest at
	 * each far corner of the reach (just inside, at the top or bottom of the vertical reach) is seen by the very next ask
	 * and gone from it the moment it's broken; a barrel one block past the corner is never listed.
	 */
	//$ gametest_ticks_batch HUGE '100' '"qaStoreScan1111bChunkCorners"'
	@GameTest(template = HUGE, timeoutTicks = 100, batch = "qaStoreScan1111bChunkCorners")
	public void storesAtTheFarCornersOfTheReachInOtherChunksAreSeenAtOnce(GameTestHelper helper) {
		Leftovers.clear(helper);
		ServerLevel level = helper.getLevel();
		Restore restore = new Restore(helper);
		BlockPos origin = helper.absolutePos(new BlockPos(0, 2, 0));
		BlockPos bench = new BlockPos(lastOfChunk(origin.getX() + 8), origin.getY() + 4, lastOfChunk(origin.getZ() + 8));
		int radius = SupplyContainers.RADIUS;
		try {
			for (int r : new int[] {8, 32}) {
				SupplyContainers.RADIUS = r;
				int checked = 0;
				for (int sx : new int[] {-1, 1}) {
					for (int sz : new int[] {-1, 1}) {
						int dy = sx > 0 ? 4 : -4;
						BlockPos inside = bench.offset(sx * r, dy, sz * r);
						BlockPos pastX = bench.offset(sx * (r + 1), 0, sz * r);
						BlockPos pastY = bench.offset(sx * r, 5, sz * (r - 1));
						if (!restore.mark(level, inside) || !restore.mark(level, pastX) || !restore.mark(level, pastY)) {
							continue; // another test's container stands there: not this test's to replace
						}
						String where = "radius " + r + ", corner " + sx + "/" + sz;
						// the bench is a chunk's last block, so the far +x/+z corner is always in the diagonal neighbour
						helper.assertTrue(sx < 0 || sz < 0 || SectionPos.blockToSectionCoord(inside.getX()) != SectionPos.blockToSectionCoord(bench.getX())
							&& SectionPos.blockToSectionCoord(inside.getZ()) != SectionPos.blockToSectionCoord(bench.getZ()),
							where + ": the corner isn't in another chunk, the test no longer covers neighbouring chunks");
						List<BlockPos> before = both(helper, level, bench, where + ", before");
						helper.assertFalse(before.contains(inside), where + ": a chest nobody placed yet is listed at " + inside);

						level.setBlockAndUpdate(inside, Blocks.CHEST.defaultBlockState());
						List<BlockPos> placed = both(helper, level, bench, where + ", a chest placed at the corner " + inside);
						helper.assertTrue(placed.contains(inside), where + ": the chest just placed at the far corner " + inside + " isn't listed: " + placed);

						level.setBlockAndUpdate(pastX, Blocks.BARREL.defaultBlockState());
						level.setBlockAndUpdate(pastY, Blocks.BARREL.defaultBlockState());
						List<BlockPos> past = both(helper, level, bench, where + ", barrels just past the reach");
						helper.assertFalse(past.contains(pastX) || past.contains(pastY), where + ": a barrel one block past the reach is listed: " + past);
						helper.assertTrue(past.contains(inside), where + ": the corner chest dropped off the list: " + past);

						level.destroyBlock(inside, false);
						List<BlockPos> broken = both(helper, level, bench, where + ", the corner chest broken");
						helper.assertFalse(broken.contains(inside), where + ": the chest just broken at " + inside + " is still listed");
						level.setBlockAndUpdate(pastX, Blocks.AIR.defaultBlockState());
						level.setBlockAndUpdate(pastY, Blocks.AIR.defaultBlockState());
						checked++;
					}
				}
				helper.assertTrue(checked >= 3, "radius " + r + ": only " + checked + " of the 4 corners were free to test");
			}
		} finally {
			SupplyContainers.RADIUS = radius;
		}
		restore.run();
		helper.succeed();
	}

	// ------------------------------------------------------------------------------------------------ 2. another dimension

	/**
	 * The Nether's blocks at the same x/y/z are never in the Overworld's answer, the Nether bench's answer is never the
	 * Overworld's, and asking in turn in the two never hands one the other's list.
	 */
	//$ gametest_ticks_batch AREA '100' '"qaStoreScan1111bNether"'
	@GameTest(template = AREA, timeoutTicks = 100, batch = "qaStoreScan1111bNether")
	public void theNetherAtTheSameCoordinatesHasItsOwnAnswer(GameTestHelper helper) {
		Leftovers.clear(helper);
		ServerLevel over = helper.getLevel();
		ServerLevel nether = over.getServer().getLevel(Level.NETHER);
		helper.assertTrue(nether != null, "the test server has no Nether");
		Restore restore = new Restore(helper);
		BlockPos bench = helper.absolutePos(new BlockPos(3, 2, 3));
		boolean sameY = bench.getY() - 4 > nether.getMinBuildHeight() && bench.getY() + 4 < nether.getMaxBuildHeight();
		BlockPos netherBench = sameY ? bench : new BlockPos(bench.getX(), 64, bench.getZ());
		BlockPos chestOver = bench.offset(2, 0, 0);
		BlockPos chestNether = netherBench.offset(2, 0, 0);
		BlockPos barrelNether = netherBench.offset(-2, 0, 1);
		helper.assertTrue(restore.mark(nether, chestNether) && restore.mark(nether, barrelNether), "a container already stands in the Nether here");

		List<BlockPos> overNone = both(helper, over, bench, "the Overworld, nothing placed");
		List<BlockPos> netherNone = both(helper, nether, netherBench, "the Nether, nothing placed");

		nether.setBlockAndUpdate(chestNether, Blocks.CHEST.defaultBlockState());
		List<BlockPos> overAfterNether = both(helper, over, bench, "the Overworld after a Nether chest");
		helper.assertTrue(overAfterNether.equals(overNone), "a chest placed in the Nether shows in the Overworld's answer: " + overAfterNether
			+ (sameY ? " (same coordinates)" : " (Nether bench at y 64)"));
		List<BlockPos> netherOne = both(helper, nether, netherBench, "the Nether, a chest placed");
		helper.assertTrue(netherOne.contains(chestNether) && netherOne.size() == netherNone.size() + 1, "the Nether chest isn't in the Nether's answer: " + netherOne);

		helper.setBlock(new BlockPos(5, 2, 3), Blocks.CHEST);
		List<BlockPos> overOne = both(helper, over, bench, "the Overworld, a chest placed");
		helper.assertTrue(overOne.contains(chestOver) && overOne.size() == overNone.size() + 1, "the Overworld chest isn't in its answer: " + overOne);
		for (int i = 0; i < 3; i++) { // asked in turn, each from its kept list
			helper.assertTrue(SupplyContainers.find(nether, netherBench, null).equals(netherOne), "round " + i + ": the Nether's answer became "
				+ SupplyContainers.find(nether, netherBench, null) + " after the Overworld's chest, not " + netherOne);
			helper.assertTrue(SupplyContainers.find(over, bench, null).equals(overOne), "round " + i + ": the Overworld's answer became "
				+ SupplyContainers.find(over, bench, null) + ", not " + overOne);
		}

		nether.setBlockAndUpdate(barrelNether, Blocks.BARREL.defaultBlockState());
		helper.assertTrue(both(helper, over, bench, "the Overworld after a Nether barrel").equals(overOne), "a Nether barrel changed the Overworld's answer");
		List<BlockPos> netherTwo = both(helper, nether, netherBench, "the Nether, a barrel placed");
		helper.assertTrue(netherTwo.contains(barrelNether) && netherTwo.contains(chestNether), "the Nether barrel isn't listed: " + netherTwo);

		nether.destroyBlock(chestNether, false);
		nether.destroyBlock(barrelNether, false);
		helper.assertTrue(both(helper, nether, netherBench, "the Nether chests broken").equals(netherNone), "a broken Nether store is still listed");
		helper.assertTrue(both(helper, over, bench, "the Overworld after the Nether's were broken").contains(chestOver),
			"breaking the Nether chest at the same spot took the Overworld's chest off its list");
		helper.setBlock(new BlockPos(5, 2, 3), Blocks.AIR);
		helper.assertTrue(both(helper, over, bench, "the Overworld chest broken").equals(overNone), "the broken Overworld chest is still listed");
		restore.run();
		helper.succeed();
	}

	// ------------------------------------------------------------------------------------------------ 3. silent placements

	/**
	 * Placements that tell no neighbour (flags 0, 2, 16, 2|16, 4|16|32, 2|16|64), straight on the chunk, a block entity
	 * removed with its block left, a new block entity put on a chest, and the Leftovers way of clearing a store: each is
	 * seen by the very next ask, as storage and as furnaces.
	 */
	//$ gametest_ticks_batch AREA '100' '"qaStoreScan1111bSilent"'
	@GameTest(template = AREA, timeoutTicks = 100, batch = "qaStoreScan1111bSilent")
	public void placementsThatSkipNeighbourUpdatesAreSeenAtOnce(GameTestHelper helper) {
		Leftovers.clear(helper);
		ServerLevel level = helper.getLevel();
		BlockPos bench = helper.absolutePos(new BlockPos(3, 2, 3));
		BlockPos p = helper.absolutePos(new BlockPos(5, 2, 5));
		List<BlockPos> none = both(helper, level, bench, "nothing placed");
		List<BlockPos> noFurnace = bothFurnaces(helper, level, bench, "nothing placed");
		for (int flags : new int[] {0, 2, 16, 2 | 16, 4 | 16 | 32, 2 | 16 | 64}) {
			String f = "flags " + flags;
			level.setBlock(p, Blocks.CHEST.defaultBlockState(), flags);
			helper.assertTrue(both(helper, level, bench, f + ", a chest").contains(p), f + ": the chest isn't listed");
			level.setBlock(p, Blocks.BARREL.defaultBlockState(), flags);
			helper.assertTrue(both(helper, level, bench, f + ", the chest swapped for a barrel").contains(p), f + ": the barrel isn't listed");
			level.setBlock(p, Blocks.BLAST_FURNACE.defaultBlockState(), flags);
			helper.assertFalse(both(helper, level, bench, f + ", a blast furnace").contains(p), f + ": the blast furnace is listed as storage");
			helper.assertTrue(bothFurnaces(helper, level, bench, f + ", a blast furnace").contains(p), f + ": the blast furnace isn't among the furnaces");
			level.setBlock(p, Blocks.AIR.defaultBlockState(), flags);
			helper.assertTrue(both(helper, level, bench, f + ", taken away").equals(none), f + ": a store taken away is still listed");
			helper.assertTrue(bothFurnaces(helper, level, bench, f + ", taken away").equals(noFurnace), f + ": a furnace taken away is still listed");
		}
		// straight on the chunk, no Level.setBlock at all
		level.getChunkAt(p).setBlockState(p, Blocks.BARREL.defaultBlockState(), false);
		helper.assertTrue(both(helper, level, bench, "a barrel set on the chunk").contains(p), "a barrel set straight on the chunk isn't listed");
		level.getChunkAt(p).setBlockState(p, Blocks.AIR.defaultBlockState(), false);
		helper.assertTrue(both(helper, level, bench, "the barrel cleared on the chunk").equals(none), "a barrel cleared on the chunk is still listed");
		// a chest whose block entity is removed, the block left
		level.setBlockAndUpdate(p, Blocks.CHEST.defaultBlockState());
		both(helper, level, bench, "a chest again");
		level.removeBlockEntity(p);
		both(helper, level, bench, "the chest's block entity removed");
		// a fresh block entity put on it, the block unchanged
		level.setBlockEntity(new ChestBlockEntity(p, level.getBlockState(p)));
		helper.assertTrue(both(helper, level, bench, "a new block entity on the chest").contains(p), "the chest with a new block entity isn't listed");
		// the way Leftovers.stores clears a store: block entity first, then air without neighbour updates
		level.removeBlockEntity(p);
		level.setBlock(p, Blocks.AIR.defaultBlockState(), Block.UPDATE_CLIENTS);
		helper.assertTrue(both(helper, level, bench, "cleared like Leftovers.stores").equals(none), "a store cleared like Leftovers does is still listed");
		helper.succeed();
	}

	// ------------------------------------------------------------------------------------------------ 3b. structure placement

	/**
	 * A structure template with a chest and a barrel (the Mover's and blueprint pasting's way of placing blocks) placed
	 * into reach, then a template of air placed over them: the very next ask lists them, then doesn't.
	 */
	//$ gametest_ticks_batch AREA '100' '"qaStoreScan1111bTemplate"'
	@GameTest(template = AREA, timeoutTicks = 100, batch = "qaStoreScan1111bTemplate")
	public void storesPlacedByAStructureTemplateAreSeenAtOnce(GameTestHelper helper) {
		Leftovers.clear(helper);
		ServerLevel level = helper.getLevel();
		BlockPos bench = helper.absolutePos(new BlockPos(3, 2, 3));
		// the template is taken from blocks outside the bench's vertical reach (5 up), so the source isn't in the answer
		BlockPos src = helper.absolutePos(new BlockPos(1, 7, 6));
		helper.setBlock(new BlockPos(1, 7, 6), Blocks.CHEST);
		helper.setBlock(new BlockPos(2, 7, 6), Blocks.BARREL);
		((Container) helper.getBlockEntity(new BlockPos(1, 7, 6))).setItem(0, new ItemStack(Items.OAK_PLANKS, 12));
		StructureTemplate stores = new StructureTemplate();
		stores.fillFromWorld(level, src, new Vec3i(2, 1, 1), false, Blocks.STRUCTURE_VOID);
		helper.setBlock(new BlockPos(1, 7, 6), Blocks.AIR);
		helper.setBlock(new BlockPos(2, 7, 6), Blocks.AIR);
		StructureTemplate air = new StructureTemplate();
		air.fillFromWorld(level, src, new Vec3i(2, 1, 1), false, Blocks.STRUCTURE_VOID);

		List<BlockPos> none = both(helper, level, bench, "nothing placed");
		BlockPos at = helper.absolutePos(new BlockPos(5, 2, 5));
		for (int flags : new int[] {2, 2 | 16, Block.UPDATE_ALL}) {
			String f = "template flags " + flags;
			stores.placeInWorld(level, at, at, new StructurePlaceSettings(), RandomSource.create(1), flags);
			helper.assertTrue(level.getBlockState(at).is(Blocks.CHEST), f + ": the template didn't place its chest");
			List<BlockPos> placed = both(helper, level, bench, f + ", placed");
			helper.assertTrue(placed.contains(at) && placed.contains(at.east()), f + ": the chest and barrel the template placed aren't listed: " + placed);
			helper.assertTrue(SupplyContainers.count(level, placed, Items.OAK_PLANKS) == 12, f + ": the template's chest holds "
				+ SupplyContainers.count(level, placed, Items.OAK_PLANKS) + " planks, not 12");
			air.placeInWorld(level, at, at, new StructurePlaceSettings(), RandomSource.create(1), flags);
			helper.assertTrue(level.getBlockState(at).isAir(), f + ": the air template didn't clear the chest");
			helper.assertTrue(both(helper, level, bench, f + ", air placed over").equals(none), f + ": stores a template replaced with air are still listed");
		}
		helper.succeed();
	}

	// ------------------------------------------------------------------------------------------------ 4. explosions, players, kinds

	/**
	 * A chest blown up, a chest a player breaks, a chest swapped for a barrel in one tick, and every vanilla kind of block
	 * entity placed then broken (hopper, shulker box, trapped chest, dispenser, dropper, ender chest, chiseled bookshelf,
	 * decorated pot, crafter, brewing stand, jukebox, lectern, smoker, campfire, beehive, sign): the kept answer is a
	 * fresh scan's after each, as storage and as furnaces.
	 */
	//$ gametest_ticks_batch AREA '100' '"qaStoreScan1111bBreaks"'
	@GameTest(template = AREA, timeoutTicks = 100, batch = "qaStoreScan1111bBreaks")
	public void explosionsPlayersSwapsAndEveryKindAreSeenAtOnce(GameTestHelper helper) {
		Leftovers.clear(helper);
		ServerLevel level = helper.getLevel();
		BlockPos bench = helper.absolutePos(new BlockPos(3, 2, 3));
		List<BlockPos> none = both(helper, level, bench, "nothing placed");

		// blown up
		BlockPos boom = helper.absolutePos(new BlockPos(6, 2, 6));
		level.setBlockAndUpdate(boom, Blocks.CHEST.defaultBlockState());
		helper.assertTrue(both(helper, level, bench, "a chest to blow up").contains(boom), "the chest to blow up isn't listed");
		level.explode(null, boom.getX() + 0.5, boom.getY() + 0.5, boom.getZ() + 0.5, 4f, Level.ExplosionInteraction.TNT);
		helper.assertTrue(level.getBlockState(boom).isAir(), "the explosion left the chest: " + level.getBlockState(boom));
		helper.assertFalse(both(helper, level, bench, "the chest blown up").contains(boom), "a chest blown up is still listed");
		Leftovers.clear(helper); // its contents and the floor it blew out, as items
		for (int x = 2; x <= 7; x++) { // the floor back, so later blocks stand on something
			for (int z = 2; z <= 7; z++) {
				if (helper.getBlockState(new BlockPos(x, 1, z)).isAir()) {
					helper.setBlock(new BlockPos(x, 1, z), Blocks.STONE);
				}
			}
		}
		for (int x = 2; x <= 7; x++) {
			for (int y = 2; y <= 4; y++) {
				for (int z = 2; z <= 7; z++) {
					helper.setBlock(new BlockPos(x, y, z), Blocks.AIR);
				}
			}
		}
		none = both(helper, level, bench, "after the explosion");

		// broken by a player
		ServerPlayer player = helper.makeMockServerPlayerInLevel();
		BlockPos hit = helper.absolutePos(new BlockPos(2, 2, 5));
		level.setBlockAndUpdate(hit, Blocks.CHEST.defaultBlockState());
		helper.assertTrue(both(helper, level, bench, "a chest for the player").contains(hit), "the player's chest isn't listed");
		player.gameMode.destroyBlock(hit);
		helper.assertTrue(level.getBlockState(hit).isAir(), "the player didn't break the chest");
		helper.assertTrue(both(helper, level, bench, "the chest a player broke").equals(none), "a chest a player broke is still listed");

		// a chest swapped for a barrel in the same tick, at the same spot, with a kept ask between
		BlockPos swap = helper.absolutePos(new BlockPos(5, 2, 2));
		level.setBlockAndUpdate(swap, Blocks.CHEST.defaultBlockState());
		both(helper, level, bench, "a chest to swap");
		level.setBlockAndUpdate(swap, Blocks.BARREL.defaultBlockState());
		List<BlockPos> swapped = both(helper, level, bench, "the chest swapped for a barrel");
		helper.assertTrue(swapped.contains(swap) && level.getBlockEntity(swap) instanceof net.minecraft.world.level.block.entity.BarrelBlockEntity,
			"the barrel in the chest's place isn't listed: " + swapped);
		level.setBlockAndUpdate(swap, Blocks.SMOKER.defaultBlockState());
		helper.assertFalse(both(helper, level, bench, "the barrel swapped for a smoker").contains(swap), "a smoker is listed as storage");
		helper.assertTrue(bothFurnaces(helper, level, bench, "the barrel swapped for a smoker").contains(swap), "the smoker isn't among the furnaces");
		level.setBlockAndUpdate(swap, Blocks.AIR.defaultBlockState());
		both(helper, level, bench, "the smoker taken");

		// every kind, placed one by one, then broken one by one
		Block[] kinds = {Blocks.HOPPER, Blocks.SHULKER_BOX, Blocks.TRAPPED_CHEST, Blocks.DISPENSER, Blocks.DROPPER, Blocks.ENDER_CHEST,
			Blocks.CHISELED_BOOKSHELF, Blocks.DECORATED_POT, Blocks.CRAFTER, Blocks.BREWING_STAND, Blocks.JUKEBOX, Blocks.LECTERN,
			Blocks.SMOKER, Blocks.CAMPFIRE, Blocks.BEEHIVE, Blocks.OAK_SIGN};
		List<BlockPos> spots = new ArrayList<>();
		for (int i = 0; i < kinds.length; i++) {
			BlockPos at = helper.absolutePos(new BlockPos(1 + (i % 6), 2 + (i / 6), 6));
			spots.add(at);
			level.setBlockAndUpdate(at, kinds[i].defaultBlockState());
			String kind = kinds[i].getDescriptionId();
			both(helper, level, bench, kind + " placed");
			bothFurnaces(helper, level, bench, kind + " placed");
		}
		List<BlockPos> all = both(helper, level, bench, "every kind placed");
		helper.assertTrue(all.contains(spots.get(0)) && all.contains(spots.get(1)) && all.contains(spots.get(2)),
			"a hopper, shulker box or trapped chest isn't listed as storage: " + all);
		for (int i = kinds.length - 1; i >= 0; i--) {
			level.destroyBlock(spots.get(i), false);
			String kind = kinds[i].getDescriptionId();
			helper.assertFalse(both(helper, level, bench, kind + " broken").contains(spots.get(i)), "the " + kind + " just broken is still listed");
			bothFurnaces(helper, level, bench, kind + " broken");
		}
		helper.assertTrue(both(helper, level, bench, "every kind broken").equals(none), "stores broken are still listed");
		helper.succeed();
	}

	// ------------------------------------------------------------------------------------------------ 5. a chunk out and back

	/**
	 * A bench's chunk far from every test, force-loaded with a chest by the bench: the chunk is let go, and on the first
	 * tick it can no longer be read the kept answer is a fresh scan's (a chest in a chunk nobody can read isn't offered);
	 * loaded again, the chest is back in the very next ask. The kept answer is asked every tick in between, the way a
	 * worker does.
	 */
	//$ gametest_ticks_batch AREA '1200' '"qaStoreScan1111bUnload"'
	@GameTest(template = AREA, timeoutTicks = 1200, batch = "qaStoreScan1111bUnload")
	public void aChunkThatDropsOutAndComesBackIsSeenAtOnce(GameTestHelper helper) {
		Leftovers.clear(helper);
		ServerLevel level = helper.getLevel();
		BlockPos base = helper.absolutePos(new BlockPos(0, 2, 0));
		int cx = SectionPos.blockToSectionCoord(base.getX() + 3000), cz = SectionPos.blockToSectionCoord(base.getZ() + 3000);
		BlockPos bench = new BlockPos(SectionPos.sectionToBlockCoord(cx) + 8, base.getY(), SectionPos.sectionToBlockCoord(cz) + 8);
		BlockPos chest = bench.offset(1, 0, 0);
		level.setChunkForced(cx, cz, true);
		boolean[] forced = {true};
		Leftovers.after(helper, () -> {
			if (forced[0]) {
				level.setChunkForced(cx, cz, false);
			}
		});
		level.getChunk(cx, cz); // loaded now, at full status
		helper.assertTrue(level.getBlockState(chest).getBlock() != Blocks.CHEST, "a chest already stands at the far spot");
		BlockState old = level.getBlockState(chest);
		helper.assertFalse(old.hasBlockEntity(), "a block entity already stands at the far spot");
		level.setBlockAndUpdate(chest, Blocks.CHEST.defaultBlockState());
		helper.assertTrue(level.getChunkSource().getChunkNow(cx, cz) != null, "the forced chunk can't be read");
		helper.assertTrue(both(helper, level, bench, "the far chest placed").contains(chest), "the far chest isn't listed");
		level.setChunkForced(cx, cz, false);
		forced[0] = false;
		int[] phase = {0};
		int[] ticks = {0};
		helper.onEachTick(() -> {
			ticks[0]++;
			if (phase[0] == 0) {
				if (level.getChunkSource().getChunkNow(cx, cz) != null) {
					SupplyContainers.find(level, bench, null); // a worker asking every tick
					return;
				}
				List<BlockPos> out = both(helper, level, bench, "tick " + ticks[0] + ", the chunk can no longer be read");
				helper.assertFalse(out.contains(chest), "the chest in a chunk nobody can read is still listed");
				phase[0] = 1;
				level.setChunkForced(cx, cz, true);
				forced[0] = true;
				level.getChunk(cx, cz);
				helper.assertTrue(level.getChunkSource().getChunkNow(cx, cz) != null, "the chunk loaded again can't be read");
				helper.assertTrue(level.getBlockState(chest).is(Blocks.CHEST), "the chest wasn't saved with its chunk");
				helper.assertTrue(both(helper, level, bench, "the chunk loaded again").contains(chest), "the chest in the chunk loaded again isn't listed");
				level.setBlockAndUpdate(chest, old);
				helper.assertFalse(both(helper, level, bench, "the far chest taken away").contains(chest), "the far chest taken away is still listed");
				level.setChunkForced(cx, cz, false);
				forced[0] = false;
				phase[0] = 2;
			}
		});
		helper.succeedWhen(() -> helper.assertTrue(phase[0] == 2, "the far chunk was never let go (tick " + ticks[0] + ")"));
	}

	// ------------------------------------------------------------------------------------------------ 6. a real builder

	/**
	 * End to end: a builder whose bench chest holds all of the test hut's materials but the planks; right after his first
	 * asks (his kept list has no planks chest), a chest with the planks is placed in the neighbouring chunk, inside his
	 * reach. He takes them and finishes the hut.
	 */
	//$ gametest_ticks_batch HUGE '9000' '"qaStoreScan1111bBuilder"'
	@GameTest(template = HUGE, timeoutTicks = 9000, batch = "qaStoreScan1111bBuilder")
	public void aBuilderUsesAChestPlacedInTheNextChunkAfterHeAsked(GameTestHelper helper) {
		Leftovers.clear(helper);
		Leftovers.players(helper);
		Leftovers.halls(helper);
		Leftovers.buildSites(helper);
		Leftovers.finished(helper);
		ServerLevel level = helper.getLevel();
		helper.setDayTime(2000);
		boolean free = level.getGameRules().getBoolean(ModGameRules.FREE_MATERIALS);
		int delay = level.getGameRules().getInt(ModGameRules.BUILD_DELAY);
		boolean help = level.getGameRules().getBoolean(ModGameRules.BUILDERS_HELP);
		level.getGameRules().getRule(ModGameRules.FREE_MATERIALS).set(false, level.getServer());
		level.getGameRules().getRule(ModGameRules.BUILD_DELAY).set(2, level.getServer());
		level.getGameRules().getRule(ModGameRules.BUILDERS_HELP).set(false, level.getServer());
		Leftovers.after(helper, () -> {
			level.getGameRules().getRule(ModGameRules.FREE_MATERIALS).set(free, level.getServer());
			level.getGameRules().getRule(ModGameRules.BUILD_DELAY).set(delay, level.getServer());
			level.getGameRules().getRule(ModGameRules.BUILDERS_HELP).set(help, level.getServer());
		});
		BlockPos benchRel = new BlockPos(14, 2, 3);
		BlockPos benchAbs = helper.absolutePos(benchRel);
		Leftovers.stores(helper, benchAbs);
		// the planks chest one block past the bench's chunk edge, on the side nearest the bench
		int inChunk = benchAbs.getX() & 15;
		int dx = inChunk >= 8 ? 16 - inChunk : -(inChunk + 1);
		BlockPos planksRel = benchRel.offset(dx, 0, 0);
		BlockPos planksAbs = helper.absolutePos(planksRel);
		helper.assertTrue(SectionPos.blockToSectionCoord(planksAbs.getX()) != SectionPos.blockToSectionCoord(benchAbs.getX())
			&& SupplyContainers.inReach(benchAbs, planksAbs), "the planks chest isn't in the next chunk inside the reach: " + planksRel);
		BlockPos nearRel = new BlockPos(14, 2, 1);
		helper.setBlock(nearRel, Blocks.CHEST);
		helper.setBlock(benchRel, ModBlocks.BUILDERS_BENCH);
		Villager builder = helper.spawn(EntityType.VILLAGER, benchRel.south());
		builder.setPersistenceRequired();
		Builders.employ(level, builder, benchAbs);
		BlockPos hut = new BlockPos(12, 2, 14);
		BuildSite site = Builders.start(level, builder, null, TEST_HUT,
			new BlueprintData.Placement(level.dimension().location(), helper.absolutePos(hut), Rotation.NONE, Mirror.NONE));
		BuildPlan plan = site.plan(level);
		helper.assertTrue(plan != null, "no test hut blueprint");
		Map<Item, Integer> materials = plan.materials();
		int planks = materials.getOrDefault(Items.OAK_PLANKS, 0);
		helper.assertTrue(planks > 0, "the test hut needs no oak planks: the test no longer covers fetching them");
		Container near = helper.getBlockEntity(nearRel);
		int slot = 0;
		for (Map.Entry<Item, Integer> e : materials.entrySet()) {
			if (e.getKey() == Items.OAK_PLANKS) {
				continue;
			}
			for (int left = e.getValue(); left > 0; ) {
				int n = Math.min(left, e.getKey().getDefaultMaxStackSize());
				near.setItem(slot++, new ItemStack(e.getKey(), n));
				left -= n;
			}
		}
		int[] ticks = {0};
		boolean[] placed = {false};
		helper.onEachTick(() -> {
			ticks[0]++;
			if (!placed[0] && ticks[0] >= 5) {
				List<BlockPos> asked = SupplyContainers.find(level, benchAbs, null);
				helper.assertFalse(asked.contains(planksAbs), "a planks chest is listed before it was placed");
				helper.setBlock(planksRel, Blocks.CHEST);
				Container chest = helper.getBlockEntity(planksRel);
				for (int i = 0, left = planks; left > 0; i++) {
					int n = Math.min(left, 64);
					chest.setItem(i, new ItemStack(Items.OAK_PLANKS, n));
					left -= n;
				}
				helper.assertTrue(SupplyContainers.find(level, benchAbs, null).contains(planksAbs),
					"the very next ask after the planks chest was placed in the next chunk doesn't list it");
				placed[0] = true;
			}
		});
		helper.succeedWhen(() -> {
			helper.assertTrue(placed[0], "the planks chest wasn't placed yet");
			BuildSite live = BuildSiteManager.get(level).get(site.id());
			helper.assertTrue(live == null, "the hut isn't finished at tick " + ticks[0] + ": "
				+ (live == null ? "" : live.status() + ", missing " + live.missing()));
			Container chest = helper.getBlockEntity(planksRel);
			helper.assertTrue(chest.countItem(Items.OAK_PLANKS) < planks, "the builder finished without taking any of the planks in the next chunk");
			int built = 0;
			for (BlockPos p : BlockPos.betweenClosed(helper.absolutePos(new BlockPos(0, 2, 10)), helper.absolutePos(new BlockPos(29, 12, 29)))) {
				built += level.getBlockState(p).is(Blocks.OAK_PLANKS) ? 1 : 0;
			}
			helper.assertTrue(built > 0, "the hut is said to be finished but no oak planks stand in it");
		});
	}
}
