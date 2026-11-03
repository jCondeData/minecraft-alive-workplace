package io.github.jcondedata.aliveworkplace.test;

import io.github.jcondedata.aliveworkplace.build.BuilderBag;
import io.github.jcondedata.aliveworkplace.fish.Fishers;
import io.github.jcondedata.aliveworkplace.mine.Miners;
import io.github.jcondedata.aliveworkplace.mine.QuarrySite;
import io.github.jcondedata.aliveworkplace.mine.QuarrySiteManager;
import io.github.jcondedata.aliveworkplace.registry.ModAttachments;
import io.github.jcondedata.aliveworkplace.registry.ModBlocks;
import io.github.jcondedata.aliveworkplace.registry.ModGameRules;
import io.github.jcondedata.aliveworkplace.work.Jobs;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Container;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.village.poi.PoiTypes;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.npc.VillagerProfession;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.levelgen.structure.BoundingBox;

/**
 * QA (lane-b-1102-2137), verifying B832 and B834 from the bugs' text.
 * <p>
 * B832 (docs/wiki/builder.md: "a bigger delay is never faster, up to the rule's largest value"; a bite is the delay
 * times 15 to 60 steps): real fishermen at the rule's largest value, at 35,791,395 (the first value whose 60-step bite
 * passed an int), and Master fishermen (40% of the delay) at the largest value never catch in 1500 ticks; at the
 * smallest value, 1, they still fish (the fix doesn't stop them). The other jobs that call BuilderLevels.delay pass their
 * own fixed base ticks, not the rule, so the rule reaches only the Builder (QaBuilderDelay1102GameTests), his scaffolds
 * and the Fisherman.
 * <p>
 * B834 ("every dug block reaches this hall's Storehouse"): a kitchen nearer the Miner than the Storehouse, two kitchens,
 * saves and reloads with finds in the bag; and the other side, a full Storehouse, where the finds go to the kitchen's
 * chest rather than the ground (the fix: "into a kitchen's only when those are full").
 */
public class QaBuildDelay1102gGameTests implements net.fabricmc.fabric.api.gametest.v1.FabricGameTest {
	private static final String HUGE = "aliveworkplace_test:huge_area";
	private static final String BUILD = "aliveworkplace_test:build_area";

	// ---- B832: the Fisherman and workplaceBuildDelay ----

	/** Six fishermen at {@code levels} on a pond with the rule at {@code rule}; their total catch at tick 1500. */
	private static void fish(GameTestHelper helper, int rule, int[] levels, java.util.function.IntConsumer check) {
		Leftovers.clear(helper);
		ServerLevel level = helper.getLevel();
		helper.setDayTime(2000);
		int was = level.getGameRules().getInt(ModGameRules.BUILD_DELAY);
		Leftovers.after(helper, () -> level.getGameRules().getRule(ModGameRules.BUILD_DELAY).set(was, level.getServer()));
		level.getGameRules().getRule(ModGameRules.BUILD_DELAY).set(rule, level.getServer());
		helper.assertTrue(level.getGameRules().getInt(ModGameRules.BUILD_DELAY) == rule, "the rule didn't take " + rule);
		for (BlockPos p : BlockPos.betweenClosed(new BlockPos(8, 1, 4), new BlockPos(20, 1, 24))) {
			helper.setBlock(p, Blocks.WATER);
		}
		List<Villager> fishers = new ArrayList<>();
		for (int i = 0; i < levels.length; i++) {
			BlockPos barrel = new BlockPos(2, 2, 3 + i * 4);
			helper.setBlock(barrel, Blocks.BARREL);
			Villager v = helper.spawn(EntityType.VILLAGER, barrel.east(2));
			Jobs.employ(level, v, helper.absolutePos(barrel), PoiTypes.FISHERMAN, VillagerProfession.FISHERMAN);
			v.setVillagerData(v.getVillagerData().setLevel(levels[i]));
			Fishers.start(level, v, new ItemStack(Items.FISHING_ROD));
			helper.assertTrue(v.getVillagerData().getLevel() == levels[i], "setup: fisherman " + i + " is level "
				+ v.getVillagerData().getLevel() + ", not " + levels[i]);
			fishers.add(v);
		}
		helper.runAtTickTime(1500, () -> {
			int caught = 0;
			for (int i = 0; i < fishers.size(); i++) {
				caught += ModAttachments.FISH_CAUGHT.getOrElse(fishers.get(i), 0);
			}
			check.accept(caught);
			helper.succeed();
		});
	}

	/** The rule's largest value, 2147483647, three novices and three Masters: no catch in 1500 ticks. */
	//$ gametest_ticks_batch HUGE '1600' '"qa1102gFisherLargest"'
	@GameTest(template = HUGE, timeoutTicks = 1600, batch = "qa1102gFisherLargest")
	public void qaFishermenAtTheLargestDelayNeverCatchInFifteenHundredTicks(GameTestHelper helper) {
		fish(helper, Integer.MAX_VALUE, new int[] {1, 1, 1, 5, 5, 5}, caught -> helper.assertTrue(caught == 0,
			"at workplaceBuildDelay " + Integer.MAX_VALUE + " three novice and three Master fishermen caught " + caught
				+ " fish in 1500 ticks; a Master's bite is still 40% of that times 15 to 60 steps"));
	}

	/** Just past the old edge, 35,791,395 (x 60 steps passed an int): six novices, no catch in 1500 ticks. */
	//$ gametest_ticks_batch HUGE '1600' '"qa1102gFisherEdge"'
	@GameTest(template = HUGE, timeoutTicks = 1600, batch = "qa1102gFisherEdge")
	public void qaFishermenJustPastTheOldOverflowEdgeNeverCatch(GameTestHelper helper) {
		fish(helper, 35_791_395, new int[] {1, 1, 1, 1, 1, 1}, caught -> helper.assertTrue(caught == 0,
			"at workplaceBuildDelay 35791395 six fishermen caught " + caught + " fish in 1500 ticks; a bite takes at least 15 x 35791395 ticks"));
	}

	/** The other side: at the smallest value, 1, the fishermen still catch (a bite is 15 to 60 ticks, at least 20). */
	//$ gametest_ticks_batch HUGE '1600' '"qa1102gFisherSmallest"'
	@GameTest(template = HUGE, timeoutTicks = 1600, batch = "qa1102gFisherSmallest")
	public void qaFishermenAtTheSmallestDelayStillCatch(GameTestHelper helper) {
		fish(helper, 1, new int[] {1, 1, 1, 1, 1, 1}, caught -> helper.assertTrue(caught >= 6,
			"at workplaceBuildDelay 1 six fishermen caught only " + caught + " fish in 1500 ticks; a bite comes after 20 to 60 ticks"));
	}

	// ---- B834: the Miner's finds go to the Storehouse ----

	private static final BlockPos BENCH = new BlockPos(2, 2, 2);
	private static final BlockPos MINER = new BlockPos(3, 2, 3);
	private static final BlockPos MIN = new BlockPos(6, 2, 6);
	private static final BlockPos MAX = new BlockPos(9, 4, 9);
	private static final BlockPos STOREHOUSE = new BlockPos(14, 2, 14);
	private static final BlockPos STORE_CHEST = new BlockPos(14, 2, 12);
	private static final BlockPos STORE_BARREL = new BlockPos(12, 2, 14);
	/** Kitchens about 10 blocks from the bench (the Storehouse is 17 off): out of the Miner's and the Storehouse's reach (8). */
	private static final BlockPos[][] KITCHENS = {{new BlockPos(2, 2, 12), new BlockPos(3, 2, 12)}, {new BlockPos(12, 2, 2), new BlockPos(12, 2, 3)}};

	private static long in(GameTestHelper helper, BlockPos[] chests, Item item) {
		long n = 0;
		for (BlockPos c : chests) {
			n += ((Container) helper.getBlockEntity(c)).countItem(item);
		}
		return n;
	}

	/**
	 * A Miner digs a 4x3x4 box with the village's hall, Storehouse (chest and barrel) and {@code kitchens} kitchens (a
	 * smoker and its chest each), saved and loaded {@code reloads} times with finds in the bag. With {@code full} the
	 * Storehouse is brim-full of diamond blocks first. Every dug block must end in the Storehouse (or, when it's full, the
	 * kitchens' chests), none in the bag, none on the ground.
	 */
	private static void dig(GameTestHelper helper, int kitchens, int wanted, boolean full) {
		Leftovers.clear(helper);
		Leftovers.halls(helper);
		Leftovers.storehouses(helper);
		ServerLevel level = helper.getLevel();
		helper.setDayTime(2000);
		int delay = level.getGameRules().getInt(ModGameRules.BUILD_DELAY);
		Leftovers.after(helper, () -> level.getGameRules().getRule(ModGameRules.BUILD_DELAY).set(delay, level.getServer()));
		level.getGameRules().getRule(ModGameRules.BUILD_DELAY).set(2, level.getServer());
		Map<Item, Integer> expect = new LinkedHashMap<>();
		Block[] rocks = {Blocks.STONE, Blocks.ANDESITE, Blocks.DIRT, Blocks.COBBLESTONE, Blocks.GRANITE};
		int i = 0;
		for (BlockPos p : BlockPos.betweenClosed(MIN, MAX)) {
			Block block = p.equals(new BlockPos(7, 3, 7)) ? Blocks.IRON_ORE : rocks[i++ % rocks.length];
			helper.setBlock(p, block);
			Item drop = block == Blocks.STONE ? Items.COBBLESTONE : block == Blocks.IRON_ORE ? Items.RAW_IRON : block.asItem();
			expect.merge(drop, 1, Integer::sum);
		}
		helper.setBlock(new BlockPos(2, 2, 14), ModBlocks.VILLAGE_HALL);
		helper.setBlock(STOREHOUSE, ModBlocks.STOREHOUSE);
		BlockPos[] kitchenChests = new BlockPos[kitchens];
		for (int k = 0; k < kitchens; k++) {
			helper.setBlock(KITCHENS[k][0], Blocks.SMOKER);
			helper.setBlock(KITCHENS[k][1], Blocks.CHEST);
			kitchenChests[k] = KITCHENS[k][1];
		}
		helper.setBlock(STORE_CHEST, Blocks.CHEST);
		helper.setBlock(STORE_BARREL, Blocks.BARREL);
		BlockPos[] store = {STORE_CHEST, STORE_BARREL};
		if (full) {
			for (BlockPos c : store) {
				QaStackRoom1102cGameTests.fillEach((Container) helper.getBlockEntity(c), Items.DIAMOND_BLOCK, 64);
			}
		}
		helper.setBlock(BENCH, Blocks.BLAST_FURNACE);
		Villager first = helper.spawn(EntityType.VILLAGER, MINER);
		Miners.employ(level, first, helper.absolutePos(BENCH));
		ModAttachments.BUILDER_BAG.getOrCreate(first).setSlots(4);
		first.setItemSlot(net.minecraft.world.entity.EquipmentSlot.MAINHAND, new ItemStack(Items.IRON_PICKAXE));
		BoundingBox box = BoundingBox.fromCorners(helper.absolutePos(MIN), helper.absolutePos(MAX));
		QuarrySite site = Miners.start(level, first, null, box, box.getYSpan());
		site.setStairs(false);
		Villager[] miner = {first};
		int[] reloads = {0};
		long[] last = {0};
		helper.onEachTick(() -> {
			long now = helper.getTick();
			if (reloads[0] < wanted && now - last[0] >= 40 && !ModAttachments.BUILDER_BAG.getOrCreate(miner[0]).isEmpty()
				&& QuarrySiteManager.get(level).get(site.id()) != null) {
				last[0] = now;
				CompoundTag tag = new CompoundTag();
				helper.assertTrue(miner[0].save(tag), "the miner wasn't saved");
				miner[0].discard();
				Entity loaded = EntityType.loadEntityRecursive(tag, level, e -> e);
				helper.assertTrue(loaded instanceof Villager, "the save didn't load as a villager");
				level.addFreshEntity(loaded);
				miner[0] = (Villager) loaded;
				reloads[0]++;
			}
		});
		String setup = kitchens + " kitchen(s) nearer than the Storehouse, " + wanted + " reloads, Storehouse " + (full ? "full" : "empty");
		helper.succeedWhen(() -> {
			helper.assertTrue(QuarrySiteManager.get(level).get(site.id()) == null,
				"still digging: " + Math.round(site.progress() * 100) + "% (" + site.status() + ")");
			helper.assertTrue(reloads[0] == wanted, "reloaded " + reloads[0] + " times with finds in the bag, not " + wanted);
			BuilderBag bag = ModAttachments.BUILDER_BAG.getOrCreate(miner[0]);
			for (Map.Entry<Item, Integer> e : expect.entrySet()) {
				long have = in(helper, store, e.getKey());
				long kitchen = in(helper, kitchenChests, e.getKey());
				int ground = 0;
				for (ItemEntity drop : level.getEntitiesOfClass(ItemEntity.class, helper.getBounds().inflate(32))) {
					if (drop.getItem().is(e.getKey())) {
						ground += drop.getItem().getCount();
					}
				}
				String where = setup + ": dug " + e.getValue() + " " + e.getKey() + "; the Storehouse holds " + have + ", the kitchens " + kitchen
					+ ", the bag " + bag.count(e.getKey()) + ", the ground " + ground;
				if (full) {
					helper.assertTrue(have == 0 && kitchen == e.getValue() && ground == 0, where + " (the full Storehouse leaves the kitchen's chest next)");
				} else {
					helper.assertTrue(have == e.getValue() && kitchen == 0 && ground == 0, where);
				}
			}
			if (full) {
				helper.assertTrue(in(helper, store, Items.DIAMOND_BLOCK) == 2 * 27 * 64, "the full Storehouse's diamond blocks changed");
			}
		});
	}

	/** A kitchen nearer the Miner than the Storehouse, two saves and loads with finds in the bag: all to the Storehouse. */
	//$ gametest_ticks_batch BUILD '5000' '"qa1102gMinerNearKitchen"'
	@GameTest(template = BUILD, timeoutTicks = 5000, batch = "qa1102gMinerNearKitchen")
	public void qaAKitchenNearerTheMinerThanTheStorehouseGetsNoneOfHisFindsAcrossReloads(GameTestHelper helper) {
		dig(helper, 1, 2, false);
	}

	/** Two kitchens in the hall's reach, both nearer the Miner: every find still goes to the Storehouse. */
	//$ gametest_ticks_batch BUILD '5000' '"qa1102gMinerTwoKitchens"'
	@GameTest(template = BUILD, timeoutTicks = 5000, batch = "qa1102gMinerTwoKitchens")
	public void qaTwoKitchensInTheHallsReachGetNoneOfTheMinersFinds(GameTestHelper helper) {
		dig(helper, 2, 0, false);
	}

	/** The other side: the Storehouse is brim-full, so the finds go into the kitchen's chest, none on the ground. */
	//$ gametest_ticks_batch BUILD '5000' '"qa1102gMinerFullStore"'
	@GameTest(template = BUILD, timeoutTicks = 5000, batch = "qa1102gMinerFullStore")
	public void qaWithTheStorehouseFullTheMinersFindsGoToTheKitchenNotTheGround(GameTestHelper helper) {
		dig(helper, 1, 0, true);
	}
}
