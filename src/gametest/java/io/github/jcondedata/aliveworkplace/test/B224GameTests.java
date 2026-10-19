package io.github.jcondedata.aliveworkplace.test;

import io.github.jcondedata.aliveworkplace.build.BuilderBag;
import io.github.jcondedata.aliveworkplace.mine.Miners;
import io.github.jcondedata.aliveworkplace.mine.QuarrySite;
import io.github.jcondedata.aliveworkplace.mine.QuarrySiteManager;
import io.github.jcondedata.aliveworkplace.registry.ModAttachments;
import io.github.jcondedata.aliveworkplace.registry.ModGameRules;
import java.util.LinkedHashMap;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Container;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.levelgen.structure.BoundingBox;

/**
 * B224 (owner, live server): a Miner stores everything they dig, the stone, cobblestone, deepslate, andesite, diorite,
 * granite, tuff, gravel and dirt as well as the ores, in their chests by the blast furnace; while digging (a few stacks
 * at a time), at the end, after a save and reload, and with a Storehouse porter about. With the chests full the ores
 * still go in first and the miner keeps digging.
 */
public class B224GameTests implements net.fabricmc.fabric.api.gametest.v1.FabricGameTest {
	private static final String AREA = "aliveworkplace_test:build_area";
	private static final BlockPos BENCH = new BlockPos(2, 2, 2);
	private static final BlockPos CHEST = new BlockPos(2, 2, 4);
	private static final BlockPos CHEST2 = new BlockPos(1, 2, 4);
	private static final BlockPos VILLAGER = new BlockPos(3, 2, 3);
	private static final BlockPos MIN = new BlockPos(6, 2, 6);
	private static final BlockPos MAX = new BlockPos(9, 4, 9);

	private record Setup(ServerLevel level, Villager miner, QuarrySite site) {
	}

	/** A Miner at a blast furnace (today's job block) with two chests, a small bag so they drop off as they go. */
	private static Setup setup(GameTestHelper helper, int bagSlots, boolean twoChests, ItemStack... chest) {
		Leftovers.clear(helper);
		ServerLevel level = helper.getLevel();
		helper.setDayTime(2000);
		level.getGameRules().getRule(ModGameRules.BUILD_DELAY).set(2, level.getServer());
		helper.setBlock(BENCH, Blocks.BLAST_FURNACE);
		helper.setBlock(CHEST, Blocks.CHEST);
		if (twoChests) {
			helper.setBlock(CHEST2, Blocks.BARREL);
		}
		Container c = helper.getBlockEntity(CHEST);
		for (int i = 0; i < chest.length; i++) {
			c.setItem(i, chest[i]);
		}
		Villager villager = helper.spawn(EntityType.VILLAGER, VILLAGER);
		Miners.employ(level, villager, helper.absolutePos(BENCH));
		ModAttachments.BUILDER_BAG.getOrCreate(villager).setSlots(bagSlots);
		BoundingBox box = BoundingBox.fromCorners(helper.absolutePos(MIN), helper.absolutePos(MAX));
		QuarrySite site = Miners.start(level, villager, null, box, box.getYSpan());
		site.setStairs(false);
		return new Setup(level, villager, site);
	}

	/**
	 * A 4×3×4 pit of every common rock (gravel on the bottom so it can't fall), with a coal and a diamond ore in it: what
	 * each block drops, by item.
	 */
	private static Map<Item, Integer> fillMixed(GameTestHelper helper) {
		Block[] rocks = {Blocks.STONE, Blocks.DEEPSLATE, Blocks.ANDESITE, Blocks.DIORITE, Blocks.GRANITE, Blocks.TUFF, Blocks.DIRT, Blocks.COBBLESTONE};
		Map<Item, Integer> expect = new LinkedHashMap<>();
		int i = 0;
		for (BlockPos p : BlockPos.betweenClosed(MIN, MAX)) {
			Block block;
			if (p.getY() == MIN.getY() && p.getX() == MIN.getX()) {
				block = Blocks.GRAVEL;
			} else if (p.equals(new BlockPos(7, 3, 7))) {
				block = Blocks.DEEPSLATE_COAL_ORE;
			} else if (p.equals(new BlockPos(8, 2, 8))) {
				block = Blocks.DIAMOND_ORE;
			} else {
				block = rocks[i++ % rocks.length];
			}
			helper.setBlock(p, block);
			Item drop = block == Blocks.STONE ? Items.COBBLESTONE : block == Blocks.DEEPSLATE ? Items.COBBLED_DEEPSLATE
				: block == Blocks.DEEPSLATE_COAL_ORE ? Items.COAL : block == Blocks.DIAMOND_ORE ? Items.DIAMOND : block.asItem();
			expect.merge(drop, 1, Integer::sum);
		}
		return expect;
	}

	private static boolean finished(Setup s) {
		return QuarrySiteManager.get(s.level()).get(s.site().id()) == null;
	}

	private static long count(GameTestHelper helper, Item item, BlockPos... chests) {
		long n = 0;
		for (BlockPos c : chests) {
			if (helper.getLevel().getBlockState(helper.absolutePos(c)).isAir()) {
				continue;
			}
			n += ((Container) helper.getBlockEntity(c)).countItem(item);
		}
		return n;
	}

	/** Every kind dug is in the chests, as many as there were blocks (gravel may come out as flint). */
	private static void assertAllStored(GameTestHelper helper, Map<Item, Integer> expect) {
		for (Map.Entry<Item, Integer> e : expect.entrySet()) {
			long have = count(helper, e.getKey(), CHEST, CHEST2);
			if (e.getKey() == Items.GRAVEL) {
				have += count(helper, Items.FLINT, CHEST, CHEST2);
			}
			helper.assertTrue(have == e.getValue(), "the chests hold " + have + " " + e.getKey() + ", dug " + e.getValue());
		}
	}

	/** Stone, deepslate, andesite, diorite, granite, tuff, dirt, gravel and the ores: all of it in the chests. */
	//$ gametest_ticks_batch AREA '4000' '"b224_mixed"'
	@GameTest(template = AREA, timeoutTicks = 4000, batch = "b224_mixed")
	public void minerStoresEveryRockAndOre(GameTestHelper helper) {
		Map<Item, Integer> expect = fillMixed(helper);
		Setup s = setup(helper, 4, true, new ItemStack(Items.IRON_PICKAXE));
		helper.succeedWhen(() -> {
			helper.assertTrue(finished(s), "still digging: " + Math.round(s.site().progress() * 100) + "% (" + s.site().status() + ")");
			assertAllStored(helper, expect);
		});
	}

	/** While digging, before the quarry ends: the stone goes into the chests as the bag fills, not only at the end. */
	//$ gametest_ticks_batch AREA '4000' '"b224_midway"'
	@GameTest(template = AREA, timeoutTicks = 4000, batch = "b224_midway")
	public void minerDropsOffStoneWhileDigging(GameTestHelper helper) {
		fillMixed(helper);
		Setup s = setup(helper, 4, true, new ItemStack(Items.IRON_PICKAXE));
		helper.succeedWhen(() -> {
			helper.assertFalse(finished(s), "the quarry ended before anything was checked");
			long rock = 0;
			for (Item item : new Item[] {Items.COBBLESTONE, Items.COBBLED_DEEPSLATE, Items.ANDESITE, Items.DIORITE, Items.GRANITE, Items.TUFF, Items.DIRT}) {
				rock += count(helper, item, CHEST, CHEST2);
			}
			helper.assertTrue(rock >= 8, "only " + rock + " rock in the chests while digging");
		});
	}

	/** Saved and loaded halfway: what's in the miner's bag still ends up in the chests. */
	//$ gametest_ticks_batch AREA '5000' '"b224_reload"'
	@GameTest(template = AREA, timeoutTicks = 5000, batch = "b224_reload")
	public void minerStoresEverythingAfterAReload(GameTestHelper helper) {
		Map<Item, Integer> expect = fillMixed(helper);
		Setup s = setup(helper, BuilderBag.SLOTS, true, new ItemStack(Items.IRON_PICKAXE));
		Villager[] miner = {s.miner()};
		helper.runAfterDelay(400, () -> {
			BuilderBag bag = ModAttachments.BUILDER_BAG.getOrCreate(miner[0]);
			helper.assertFalse(bag.isEmpty(), "nothing dug yet to carry over the reload");
			CompoundTag tag = new CompoundTag();
			helper.assertTrue(miner[0].save(tag), "the miner wasn't saved");
			miner[0].discard();
			Entity loaded = EntityType.loadEntityRecursive(tag, helper.getLevel(), e -> e);
			helper.assertTrue(loaded instanceof Villager, "the save didn't load as a villager");
			helper.getLevel().addFreshEntity(loaded);
			miner[0] = (Villager) loaded;
		});
		helper.succeedWhen(() -> {
			helper.assertTrue(finished(s), "still digging: " + Math.round(s.site().progress() * 100) + "% (" + s.site().status() + ")");
			assertAllStored(helper, expect);
		});
	}

	/**
	 * No chests by the blast furnace, a village Storehouse across the area: everything dug, stone and ores, goes into
	 * the Storehouse's chests (it was all dropped on the ground by the bench).
	 */
	//$ gametest_ticks_batch AREA '4000' '"b224_store"'
	@GameTest(template = AREA, timeoutTicks = 4000, batch = "b224_store")
	public void minerWithNoChestsStoresInTheStorehouse(GameTestHelper helper) {
		Leftovers.storehouses(helper);
		Map<Item, Integer> expect = fillMixed(helper);
		BlockPos storehouse = new BlockPos(14, 2, 14);
		BlockPos storeChest = new BlockPos(14, 2, 12);
		BlockPos storeBarrel = new BlockPos(12, 2, 14);
		helper.setBlock(storehouse, io.github.jcondedata.aliveworkplace.registry.ModBlocks.STOREHOUSE);
		helper.setBlock(storeChest, Blocks.CHEST);
		helper.setBlock(storeBarrel, Blocks.BARREL);
		Setup s = setup(helper, 4, false);
		helper.setBlock(CHEST, Blocks.AIR);
		s.miner().setItemSlot(net.minecraft.world.entity.EquipmentSlot.MAINHAND, new ItemStack(Items.IRON_PICKAXE));
		helper.succeedWhen(() -> {
			helper.assertTrue(finished(s), "still digging: " + Math.round(s.site().progress() * 100) + "% (" + s.site().status() + ")");
			for (Map.Entry<Item, Integer> e : expect.entrySet()) {
				long have = count(helper, e.getKey(), storeChest, storeBarrel);
				if (e.getKey() == Items.GRAVEL) {
					have += count(helper, Items.FLINT, storeChest, storeBarrel);
				}
				helper.assertTrue(have == e.getValue(), "the Storehouse holds " + have + " " + e.getKey() + ", dug " + e.getValue());
			}
		});
	}

	/**
	 * Chests full of cobblestone but for one slot: the miner doesn't stall. The ores dug take the room first, the quarry
	 * is finished, and nothing is left stuck in the bag.
	 */
	//$ gametest_ticks_batch AREA '4000' '"b224_full"'
	@GameTest(template = AREA, timeoutTicks = 4000, batch = "b224_full")
	public void fullChestsTakeTheOresFirst(GameTestHelper helper) {
		fillMixed(helper);
		ItemStack[] full = new ItemStack[27];
		full[0] = new ItemStack(Items.IRON_PICKAXE);
		for (int i = 1; i < 27; i++) {
			full[i] = new ItemStack(Items.COBBLESTONE, 64);
		}
		Setup s = setup(helper, 4, false, full);
		helper.succeedWhen(() -> {
			helper.assertTrue(finished(s), "stalled: " + Math.round(s.site().progress() * 100) + "% (" + s.site().status() + ")");
			helper.assertTrue(count(helper, Items.COAL, CHEST) == 1, "the coal didn't make it into the full chest");
			helper.assertTrue(count(helper, Items.DIAMOND, CHEST) == 1, "the diamond didn't make it into the full chest");
			helper.assertTrue(ModAttachments.BUILDER_BAG.getOrCreate(s.miner()).isEmpty(), "things left in the miner's bag");
		});
	}
}
