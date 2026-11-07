package io.github.jcondedata.aliveworkplace.test;

import io.github.jcondedata.aliveworkplace.build.BuilderBag;
import io.github.jcondedata.aliveworkplace.fish.Fishers;
import io.github.jcondedata.aliveworkplace.mine.Miners;
import io.github.jcondedata.aliveworkplace.mine.QuarrySite;
import io.github.jcondedata.aliveworkplace.mine.QuarrySiteManager;
import io.github.jcondedata.aliveworkplace.registry.ModAttachments;
import io.github.jcondedata.aliveworkplace.registry.ModBlocks;
import io.github.jcondedata.aliveworkplace.registry.ModGameRules;
import io.github.jcondedata.aliveworkplace.store.HaulerGolems;
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
import net.minecraft.world.entity.animal.IronGolem;
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
 * QA lane c (1102e, qa-lane-c-1102-2153): adversarial corners of four landed fixes, from the bug texts.
 * <ul>
 * <li>B830: a Hauler Golem passes over goods that don't fit the Storehouse: two stashes in front that don't fit (a Drop Box
 * and a nearer worker), room that opens later, and a save and reload of the golem while it passes them over.</li>
 * <li>B832: real fishermen at the overflow edge (35,791,395 x 60 steps passes the int range) and at the rule's largest
 * value catch nothing in 1500 ticks, while the same pond at the default 8 still catches.</li>
 * <li>B833: the plural forms beside the new singulars through the hall's real buttons (two mercenaries left, three kinds of
 * meal).</li>
 * <li>B834: the Miner's finds go into a kitchen's chest only when the Storehouse is full or missing, and a Storehouse with
 * part-stack room still takes what fits first.</li>
 * </ul>
 */
public class QaLaneC1102eGameTests implements net.fabricmc.fabric.api.gametest.v1.FabricGameTest {
	private static final String HUGE = "aliveworkplace_test:huge_area";
	private static final String AREA = "aliveworkplace_test:big_area";
	private static final String BUILD = "aliveworkplace_test:build_area";
	private static final org.slf4j.Logger LOG = org.slf4j.LoggerFactory.getLogger("aliveworkplace-qa");

	// ---- B830: the Hauler Golem ----

	/** A Drop Box farther from the Storehouse than the usual one (QaStackRoom1102cGameTests.HAUL_BOX). */
	static final BlockPos FAR_BOX = new BlockPos(24, 2, 20);

	private static int count(GameTestHelper helper, BlockPos pos, Item item) {
		return QaStackRoom1102cGameTests.count(helper, pos, item);
	}

	/**
	 * The Storehouse has room only for logs (26 full cobblestone stacks and one log). In front of the lumberjack's logs stand
	 * two stashes whose goods don't fit: a Drop Box with 32 cobblestone and a miner with 64. The golem passes over both and
	 * brings in 63 logs; the cobblestone stays where it was.
	 */
	//$ gametest_ticks_batch HUGE '4800' '"qaLaneC1102eTwoInFront"'
	@GameTest(template = HUGE, timeoutTicks = 4800, batch = "qaLaneC1102eTwoInFront")
	public void qaB830TheHaulerPassesOverADropBoxAndAWorkerThatDontFit(GameTestHelper helper) {
		Container store = QaStackRoom1102cGameTests.storehouse(helper);
		QaStackRoom1102cGameTests.fillEach(store, Items.COBBLESTONE, 64);
		store.setItem(26, new ItemStack(Items.OAK_LOG, 1));
		helper.setBlock(QaStackRoom1102cGameTests.HAUL_BOX, ModBlocks.DROP_BOX);
		((Container) helper.getBlockEntity(QaStackRoom1102cGameTests.HAUL_BOX)).setItem(0, new ItemStack(Items.COBBLESTONE, 32));
		QaStackRoom1102cGameTests.miner(helper, 64);
		QaStackRoom1102cGameTests.lumberjack(helper, QaStackRoom1102cGameTests.FAR_JACK_CHEST, 64);
		IronGolem golem = QaStackRoom1102cGameTests.hauler(helper);
		helper.succeedWhen(() -> {
			int stored = count(helper, QaStackRoom1102cGameTests.STORE_CHEST, Items.OAK_LOG);
			int left = count(helper, QaStackRoom1102cGameTests.FAR_JACK_CHEST, Items.OAK_LOG);
			helper.assertTrue(stored == 64 && left == 1 && HaulerGolems.load(golem).isEmpty(), "the Storehouse has " + stored
				+ " of 64 logs, the lumberjack keeps " + left + ", the golem carries " + HaulerGolems.load(golem));
			helper.assertTrue(count(helper, QaStackRoom1102cGameTests.HAUL_BOX, Items.COBBLESTONE) == 32
				&& count(helper, QaStackRoom1102cGameTests.MINER_CHEST, Items.COBBLESTONE) == 64
				&& count(helper, QaStackRoom1102cGameTests.STORE_CHEST, Items.COBBLESTONE) == 26 * 64,
				"cobblestone moved though none fits: box " + count(helper, QaStackRoom1102cGameTests.HAUL_BOX, Items.COBBLESTONE)
					+ ", miner " + count(helper, QaStackRoom1102cGameTests.MINER_CHEST, Items.COBBLESTONE));
			LOG.info("[QA 1102e] B830: past a Drop Box and a miner whose cobblestone doesn't fit, 63 logs came in");
		});
	}

	/**
	 * Passing over is not for good: a Storehouse chest full of cobblestone and a lumberjack with 64 logs; for 600 ticks
	 * nothing moves. Then a player takes a stack out of the chest: the golem brings the logs in.
	 */
	//$ gametest_ticks_batch HUGE '4800' '"qaLaneC1102eRoomLater"'
	@GameTest(template = HUGE, timeoutTicks = 4800, batch = "qaLaneC1102eRoomLater")
	public void qaB830RoomThatOpensLaterIsUsed(GameTestHelper helper) {
		Container store = QaStackRoom1102cGameTests.storehouse(helper);
		QaStackRoom1102cGameTests.fillEach(store, Items.COBBLESTONE, 64);
		QaStackRoom1102cGameTests.lumberjack(helper, QaStackRoom1102cGameTests.FAR_JACK_CHEST, 64);
		IronGolem golem = QaStackRoom1102cGameTests.hauler(helper);
		boolean[] opened = {false};
		helper.runAtTickTime(600, () -> {
			helper.assertTrue(HaulerGolems.load(golem).isEmpty() && count(helper, QaStackRoom1102cGameTests.FAR_JACK_CHEST, Items.OAK_LOG) == 64,
				"before there was room the golem carries " + HaulerGolems.load(golem) + " and the lumberjack has "
					+ count(helper, QaStackRoom1102cGameTests.FAR_JACK_CHEST, Items.OAK_LOG));
			store.setItem(13, ItemStack.EMPTY); // a player takes a stack of cobblestone out
			opened[0] = true;
		});
		helper.succeedWhen(() -> {
			helper.assertTrue(opened[0], "waiting for the room to open");
			int stored = count(helper, QaStackRoom1102cGameTests.STORE_CHEST, Items.OAK_LOG);
			helper.assertTrue(stored == 64 && count(helper, QaStackRoom1102cGameTests.FAR_JACK_CHEST, Items.OAK_LOG) == 0 && HaulerGolems.load(golem).isEmpty(),
				"after a stack was taken out the Storehouse has " + stored + " of 64 logs; the golem carries " + HaulerGolems.load(golem));
			LOG.info("[QA 1102e] B830: the golem used room that opened after 600 ticks");
		});
	}

	/**
	 * The golem is saved and loaded (a chunk unloaded and back) while it passes over a miner whose cobblestone doesn't fit:
	 * the loaded golem still passes him over and brings the farther lumberjack's logs in.
	 */
	//$ gametest_ticks_batch HUGE '4800' '"qaLaneC1102eReload"'
	@GameTest(template = HUGE, timeoutTicks = 4800, batch = "qaLaneC1102eReload")
	public void qaB830AReloadedHaulerStillPassesOverGoodsThatDontFit(GameTestHelper helper) {
		Container store = QaStackRoom1102cGameTests.storehouse(helper);
		QaStackRoom1102cGameTests.fillEach(store, Items.COBBLESTONE, 64);
		store.setItem(26, new ItemStack(Items.OAK_LOG, 1));
		QaStackRoom1102cGameTests.miner(helper, 64);
		QaStackRoom1102cGameTests.lumberjack(helper, QaStackRoom1102cGameTests.FAR_JACK_CHEST, 64);
		IronGolem first = QaStackRoom1102cGameTests.hauler(helper);
		IronGolem[] golem = {first};
		ServerLevel level = helper.getLevel();
		helper.runAtTickTime(60, () -> {
			helper.assertTrue(HaulerGolems.load(golem[0]).isEmpty(), "setup: the golem took something before the reload: " + HaulerGolems.load(golem[0]));
			CompoundTag tag = new CompoundTag();
			helper.assertTrue(golem[0].save(tag), "the golem wasn't saved");
			golem[0].discard();
			Entity loaded = EntityType.loadEntityRecursive(tag, level, e -> e);
			helper.assertTrue(loaded instanceof IronGolem, "the save didn't load as a golem");
			level.addFreshEntity(loaded);
			golem[0] = (IronGolem) loaded;
			Leftovers.after(helper, loaded::discard);
		});
		helper.succeedWhen(() -> {
			helper.assertTrue(golem[0] != first, "waiting for the reload");
			int stored = count(helper, QaStackRoom1102cGameTests.STORE_CHEST, Items.OAK_LOG);
			int left = count(helper, QaStackRoom1102cGameTests.FAR_JACK_CHEST, Items.OAK_LOG);
			helper.assertTrue(stored == 64 && left == 1 && HaulerGolems.load(golem[0]).isEmpty(), "after the reload the Storehouse has "
				+ stored + " of 64 logs, the lumberjack keeps " + left + ", the golem carries " + HaulerGolems.load(golem[0]));
			helper.assertTrue(count(helper, QaStackRoom1102cGameTests.MINER_CHEST, Items.COBBLESTONE) == 64, "the miner's cobblestone moved: "
				+ count(helper, QaStackRoom1102cGameTests.MINER_CHEST, Items.COBBLESTONE));
			LOG.info("[QA 1102e] B830: a reloaded golem passed over the miner and brought 63 logs");
		});
	}

	/**
	 * The control for QaLaneC1102eBugTests' two Drop Boxes: the same Storehouse (room for logs only) and the farther Drop Box
	 * with 32 logs, without the nearer box of cobblestone. The golem empties it: the box is in reach and its logs fit.
	 */
	//$ gametest_ticks_batch HUGE '4800' '"qaLaneC1102eFarBoxAlone"'
	@GameTest(template = HUGE, timeoutTicks = 4800, batch = "qaLaneC1102eFarBoxAlone")
	public void qaB830AFartherDropBoxAloneIsEmptied(GameTestHelper helper) {
		Container store = QaStackRoom1102cGameTests.storehouse(helper);
		QaStackRoom1102cGameTests.fillEach(store, Items.COBBLESTONE, 64);
		store.setItem(26, new ItemStack(Items.OAK_LOG, 1));
		helper.setBlock(FAR_BOX, ModBlocks.DROP_BOX);
		((Container) helper.getBlockEntity(FAR_BOX)).setItem(0, new ItemStack(Items.OAK_LOG, 32));
		QaStackRoom1102cGameTests.lumberjack(helper, QaStackRoom1102cGameTests.FAR_JACK_CHEST, 0);
		IronGolem golem = QaStackRoom1102cGameTests.hauler(helper);
		helper.succeedWhen(() -> {
			int stored = count(helper, QaStackRoom1102cGameTests.STORE_CHEST, Items.OAK_LOG);
			helper.assertTrue(stored == 33 && count(helper, FAR_BOX, Items.OAK_LOG) == 0 && HaulerGolems.load(golem).isEmpty(),
				"the Storehouse has " + stored + " of 33 logs; the Drop Box keeps " + count(helper, FAR_BOX, Items.OAK_LOG));
			LOG.info("[QA 1102e] B830: a lone Drop Box's 32 logs came in");
		});
	}

	// ---- B847's porter kin: two Drop Boxes by a porter's Storehouse (PorterGameTests' village) ----

	static final BlockPos P_STOREHOUSE = new BlockPos(19, 2, 19);
	static final BlockPos P_STORE_CHEST = new BlockPos(19, 2, 17);
	static final BlockPos P_NEAR_BOX = new BlockPos(8, 2, 17);
	static final BlockPos P_FAR_BOX = new BlockPos(2, 2, 8);

	/**
	 * A porter at a Storehouse whose chest has room for logs only (26 full cobblestone stacks and a log), a farther Drop
	 * Box with 32 logs and, when {@code nearBox}, a nearer one with 32 cobblestone. Returns the porter.
	 */
	static Villager porterTwoBoxes(GameTestHelper helper, boolean nearBox) {
		Leftovers.clear(helper);
		helper.setDayTime(2000); // work hours
		Leftovers.village(helper, 48);
		QaStackRoom1102cGameTests.strayDropBoxes(helper);
		helper.setBlock(P_STOREHOUSE, ModBlocks.STOREHOUSE);
		helper.setBlock(P_STORE_CHEST, Blocks.CHEST);
		Container store = (Container) helper.getBlockEntity(P_STORE_CHEST);
		QaStackRoom1102cGameTests.fillEach(store, Items.COBBLESTONE, 64);
		store.setItem(26, new ItemStack(Items.OAK_LOG, 1));
		if (nearBox) {
			helper.setBlock(P_NEAR_BOX, ModBlocks.DROP_BOX);
			((Container) helper.getBlockEntity(P_NEAR_BOX)).setItem(0, new ItemStack(Items.COBBLESTONE, 32));
		}
		helper.setBlock(P_FAR_BOX, ModBlocks.DROP_BOX);
		((Container) helper.getBlockEntity(P_FAR_BOX)).setItem(0, new ItemStack(Items.OAK_LOG, 32));
		Villager porter = helper.spawn(EntityType.VILLAGER, new BlockPos(18, 2, 18));
		io.github.jcondedata.aliveworkplace.store.Porters.employ(helper.getLevel(), porter, helper.absolutePos(P_STOREHOUSE));
		return porter;
	}

	/** The control for the porter's two Drop Boxes: the farther box alone, its 32 logs fit, and the porter brings them in. */
	//$ gametest_ticks_batch AREA '2400' '"qaLaneC1102ePorterFarBoxAlone"'
	@GameTest(template = AREA, timeoutTicks = 2400, batch = "qaLaneC1102ePorterFarBoxAlone")
	public void qaB847APortersFartherDropBoxAloneIsEmptied(GameTestHelper helper) {
		Villager porter = porterTwoBoxes(helper, false);
		helper.succeedWhen(() -> {
			int stored = count(helper, P_STORE_CHEST, Items.OAK_LOG);
			helper.assertTrue(stored == 33 && count(helper, P_FAR_BOX, Items.OAK_LOG) == 0
				&& ModAttachments.BUILDER_BAG.getOrCreate(porter).count(Items.OAK_LOG) == 0,
				"the Storehouse has " + stored + " of 33 logs; the Drop Box keeps " + count(helper, P_FAR_BOX, Items.OAK_LOG));
			LOG.info("[QA 1102e] B847 porter control: a lone Drop Box's 32 logs came in");
		});
	}

	// ---- B832: real fishermen at the edges of workplaceBuildDelay ----

	/** Six fishermen in one pond with the rule at {@code rule}; the fish they caught after 1500 ticks. */
	private static void fishers(GameTestHelper helper, int rule, java.util.function.IntConsumer caughtCheck) {
		Leftovers.clear(helper);
		ServerLevel level = helper.getLevel();
		helper.setDayTime(2000);
		int was = level.getGameRules().getInt(ModGameRules.BUILD_DELAY);
		Leftovers.after(helper, () -> level.getGameRules().getRule(ModGameRules.BUILD_DELAY).set(was, level.getServer()));
		level.getGameRules().getRule(ModGameRules.BUILD_DELAY).set(rule, level.getServer());
		for (BlockPos p : BlockPos.betweenClosed(new BlockPos(8, 1, 4), new BlockPos(20, 1, 24))) {
			helper.setBlock(p, Blocks.WATER);
		}
		List<Villager> fishers = new ArrayList<>();
		for (int i = 0; i < 6; i++) {
			BlockPos barrel = new BlockPos(2, 2, 3 + i * 4);
			helper.setBlock(barrel, Blocks.BARREL);
			Villager v = helper.spawn(EntityType.VILLAGER, barrel.east(2));
			Jobs.employ(level, v, helper.absolutePos(barrel), PoiTypes.FISHERMAN, VillagerProfession.FISHERMAN);
			Fishers.start(level, v, new ItemStack(Items.FISHING_ROD));
			fishers.add(v);
		}
		helper.runAtTickTime(1500, () -> {
			int caught = 0;
			for (Villager v : fishers) {
				caught += ModAttachments.FISH_CAUGHT.getOrElse(v, 0);
			}
			LOG.info("[QA 1102e] B832: workplaceBuildDelay {}: six fishermen caught {} fish in 1500 ticks", rule, caught);
			caughtCheck.accept(caught);
			helper.succeed();
		});
	}

	/** 35,791,395 is the first delay whose 60-step bite passes the int range: still no fish in 1500 ticks. */
	//$ gametest_ticks_batch HUGE '1600' '"qaLaneC1102eFishEdge"'
	@GameTest(template = HUGE, timeoutTicks = 1600, batch = "qaLaneC1102eFishEdge")
	public void qaB832AtTheOverflowEdgeFishermenCatchNothing(GameTestHelper helper) {
		fishers(helper, 35_791_395, caught -> helper.assertTrue(caught == 0, "at workplaceBuildDelay 35791395 six fishermen caught "
			+ caught + " fish in 1500 ticks; a bite takes the delay x 15 to 60 ticks"));
	}

	/** The rule's largest value: no fish in 1500 ticks (B832's own test is at 2^30). */
	//$ gametest_ticks_batch HUGE '1600' '"qaLaneC1102eFishMax"'
	@GameTest(template = HUGE, timeoutTicks = 1600, batch = "qaLaneC1102eFishMax")
	public void qaB832AtTheLargestDelayFishermenCatchNothing(GameTestHelper helper) {
		fishers(helper, Integer.MAX_VALUE, caught -> helper.assertTrue(caught == 0, "at workplaceBuildDelay " + Integer.MAX_VALUE
			+ " six fishermen caught " + caught + " fish in 1500 ticks"));
	}

	/** The control: the same pond at the default delay of 8 still catches (a bite takes 120 to 480 ticks). */
	//$ gametest_ticks_batch HUGE '1600' '"qaLaneC1102eFishDefault"'
	@GameTest(template = HUGE, timeoutTicks = 1600, batch = "qaLaneC1102eFishDefault")
	public void qaB832AtTheDefaultDelayFishermenStillCatch(GameTestHelper helper) {
		fishers(helper, 8, caught -> helper.assertTrue(caught > 0, "at the default workplaceBuildDelay 8 six fishermen caught nothing in 1500 ticks"));
	}

	// ---- B833: the plurals beside the new singulars, through the hall's buttons ----

	/** One of the three fell: "2 mercenaries are here until dawn" (the singular's neighbour). */
	//$ gametest_ticks_batch AREA '60' '"qaLaneC1102eTwoMercenaries"'
	@GameTest(template = AREA, timeoutTicks = 60, batch = "qaLaneC1102eTwoMercenaries")
	public void qaB833TwoMercenariesLeftReadPlural(GameTestHelper helper) {
		QaOneCount1102cGameTests.mercenaries(helper, 1, "2 mercenaries are here until dawn");
	}

	/** Bread, baked potatoes and cooked beef: "3 kinds of meal in the store (3 or more for a varied diet)". */
	//$ gametest_ticks_batch AREA '60' '"qaLaneC1102eThreeMealKinds"'
	@GameTest(template = AREA, timeoutTicks = 60, batch = "qaLaneC1102eThreeMealKinds")
	public void qaB833ThreeKindsOfMealReadPlural(GameTestHelper helper) {
		QaOneCount1102cGameTests.mealKinds(helper, List.of(new ItemStack(Items.BREAD, 5), new ItemStack(Items.BAKED_POTATO, 5),
			new ItemStack(Items.COOKED_BEEF, 5)), "3 kinds of meal in the store (3 or more for a varied diet)");
	}

	// ---- B834: the Miner's finds, the Storehouse and a kitchen ----

	private static final BlockPos BENCH = new BlockPos(2, 2, 2);
	private static final BlockPos MINER = new BlockPos(3, 2, 3);
	private static final BlockPos MIN = new BlockPos(6, 2, 6);
	private static final BlockPos MAX = new BlockPos(9, 4, 9);
	private static final BlockPos STOREHOUSE = new BlockPos(14, 2, 14);
	private static final BlockPos STORE_CHEST = new BlockPos(14, 2, 12);
	private static final BlockPos STORE_BARREL = new BlockPos(12, 2, 14);
	// The kitchen's chest is out of the Miner's reach (x, z within 8 of the bench) and the Storehouse's, 2 from the hall.
	private static final BlockPos KITCHEN = new BlockPos(1, 2, 12);
	private static final BlockPos KITCHEN_CHEST = new BlockPos(2, 2, 12);

	private enum Store { FULL, NONE, PART_COBBLESTONE }

	/**
	 * A Miner with no chests of their own digs 48 blocks by the village's own Village Hall; the village has a kitchen (a
	 * smoker with its chest) and a Storehouse in the state {@code mode}. When the dig is done, {@code check} gets what was
	 * dug and asserts where it went; nothing may be on the ground or left in the bag.
	 */
	private static void minerWithKitchen(GameTestHelper helper, Store mode, java.util.function.BiConsumer<Map<Item, Integer>, Long> check) {
		Leftovers.clear(helper);
		Leftovers.halls(helper);
		Leftovers.storehouses(helper);
		ServerLevel level = helper.getLevel();
		// a village of 16 blocks: an earlier batch's kitchen or Storehouse beside the area is not this village's
		int radius = io.github.jcondedata.aliveworkplace.hall.VillageHalls.RADIUS;
		io.github.jcondedata.aliveworkplace.hall.VillageHalls.RADIUS = 16;
		Leftovers.after(helper, () -> io.github.jcondedata.aliveworkplace.hall.VillageHalls.RADIUS = radius);
		helper.setDayTime(2000);
		int delay = level.getGameRules().getInt(ModGameRules.BUILD_DELAY);
		Leftovers.after(helper, () -> level.getGameRules().getRule(ModGameRules.BUILD_DELAY).set(delay, level.getServer()));
		level.getGameRules().getRule(ModGameRules.BUILD_DELAY).set(2, level.getServer());
		Map<Item, Integer> expect = new LinkedHashMap<>();
		Block[] rocks = {Blocks.STONE, Blocks.ANDESITE, Blocks.DIRT, Blocks.COBBLESTONE, Blocks.GRANITE};
		int i = 0;
		for (BlockPos p : BlockPos.betweenClosed(MIN, MAX)) {
			Block block = rocks[i++ % rocks.length];
			helper.setBlock(p, block);
			expect.merge(block == Blocks.STONE ? Items.COBBLESTONE : block.asItem(), 1, Integer::sum);
		}
		helper.setBlock(new BlockPos(2, 2, 14), ModBlocks.VILLAGE_HALL);
		helper.setBlock(KITCHEN, Blocks.SMOKER);
		helper.setBlock(KITCHEN_CHEST, Blocks.CHEST);
		long before = 0;
		if (mode != Store.NONE) {
			helper.setBlock(STOREHOUSE, ModBlocks.STOREHOUSE);
			helper.setBlock(STORE_CHEST, Blocks.CHEST);
			helper.setBlock(STORE_BARREL, Blocks.BARREL);
			for (BlockPos c : new BlockPos[] {STORE_CHEST, STORE_BARREL}) {
				Container box = (Container) helper.getBlockEntity(c);
				for (int s = 0; s < box.getContainerSize(); s++) {
					box.setItem(s, mode == Store.FULL ? new ItemStack(Items.STICK, 64) : new ItemStack(Items.COBBLESTONE, 63));
				}
				before += box.getContainerSize() * (mode == Store.FULL ? 64L : 63L);
			}
		}
		long held = before;
		helper.setBlock(BENCH, Blocks.BLAST_FURNACE);
		Villager miner = helper.spawn(EntityType.VILLAGER, MINER);
		Miners.employ(level, miner, helper.absolutePos(BENCH));
		ModAttachments.BUILDER_BAG.getOrCreate(miner).setSlots(4);
		miner.setItemSlot(net.minecraft.world.entity.EquipmentSlot.MAINHAND, new ItemStack(Items.IRON_PICKAXE));
		BoundingBox box = BoundingBox.fromCorners(helper.absolutePos(MIN), helper.absolutePos(MAX));
		QuarrySite site = Miners.start(level, miner, null, box, box.getYSpan());
		site.setStairs(false);
		helper.succeedWhen(() -> {
			helper.assertTrue(QuarrySiteManager.get(level).get(site.id()) == null,
				"still digging: " + Math.round(site.progress() * 100) + "% (" + site.status() + ")");
			BuilderBag bag = ModAttachments.BUILDER_BAG.getOrCreate(miner);
			for (Map.Entry<Item, Integer> e : expect.entrySet()) {
				int ground = 0;
				for (ItemEntity drop : level.getEntitiesOfClass(ItemEntity.class, helper.getBounds().inflate(32))) {
					if (drop.getItem().is(e.getKey())) {
						ground += drop.getItem().getCount();
					}
				}
				helper.assertTrue(ground == 0 && bag.count(e.getKey()) == 0, e.getKey() + ": " + ground + " on the ground, "
					+ bag.count(e.getKey()) + " in the bag");
			}
			check.accept(expect, held);
		});
	}

	/** The village store's chests (relative) and how many of {@code item} each holds: where finds went, for a failure. */
	private static String where(GameTestHelper helper, Item item) {
		StringBuilder out = new StringBuilder();
		for (BlockPos c : io.github.jcondedata.aliveworkplace.hall.VillageNeeds.store(helper.getLevel(), helper.absolutePos(new BlockPos(2, 2, 14)))) {
			if (helper.getLevel().getBlockEntity(c) instanceof Container box) {
				out.append(helper.relativePos(c)).append('=').append(box.countItem(item)).append(' ');
			}
		}
		return "; the village store: " + out;
	}

	private static long storehouse(GameTestHelper helper, Item item) {
		return count(helper, STORE_CHEST, item) + count(helper, STORE_BARREL, item);
	}

	/** A Storehouse with every slot full of sticks: the finds go into the kitchen's chest, every one, and none is lost. */
	//$ gametest_ticks_batch BUILD '5000' '"qaLaneC1102eKitchenFull"'
	@GameTest(template = BUILD, timeoutTicks = 5000, batch = "qaLaneC1102eKitchenFull")
	public void qaB834AFullStorehouseSendsTheFindsToTheKitchensChest(GameTestHelper helper) {
		minerWithKitchen(helper, Store.FULL, (expect, held) -> {
			helper.assertTrue(storehouse(helper, Items.STICK) == held, "the full Storehouse changed: " + storehouse(helper, Items.STICK) + " sticks");
			for (Map.Entry<Item, Integer> e : expect.entrySet()) {
				int kitchen = count(helper, KITCHEN_CHEST, e.getKey());
				helper.assertTrue(kitchen == e.getValue() && storehouse(helper, e.getKey()) == 0, "dug " + e.getValue() + " " + e.getKey()
					+ ": the kitchen's chest has " + kitchen + ", the full Storehouse " + storehouse(helper, e.getKey()) + where(helper, e.getKey()));
			}
			LOG.info("[QA 1102e] B834: a full Storehouse, the finds in the kitchen's chest: {}", expect);
		});
	}

	/** No Storehouse at all, only the hall and a kitchen: the finds go into the kitchen's chest, none on the ground. */
	//$ gametest_ticks_batch BUILD '5000' '"qaLaneC1102eKitchenNoStore"'
	@GameTest(template = BUILD, timeoutTicks = 5000, batch = "qaLaneC1102eKitchenNoStore")
	public void qaB834WithNoStorehouseTheFindsGoToTheKitchensChest(GameTestHelper helper) {
		minerWithKitchen(helper, Store.NONE, (expect, held) -> {
			for (Map.Entry<Item, Integer> e : expect.entrySet()) {
				int kitchen = count(helper, KITCHEN_CHEST, e.getKey());
				helper.assertTrue(kitchen == e.getValue(), "dug " + e.getValue() + " " + e.getKey() + ", the kitchen's chest has " + kitchen + where(helper, e.getKey()));
			}
			LOG.info("[QA 1102e] B834: no Storehouse, the finds in the kitchen's chest: {}", expect);
		});
	}

	/**
	 * Every Storehouse slot holds 63 cobblestone: room for one more in each (54), none for anything else. Every cobblestone
	 * dug goes into the Storehouse, none into the kitchen; only the other finds, which can't fit, go to the kitchen.
	 */
	//$ gametest_ticks_batch BUILD '5000' '"qaLaneC1102eKitchenPart"'
	@GameTest(template = BUILD, timeoutTicks = 5000, batch = "qaLaneC1102eKitchenPart")
	public void qaB834PartStackRoomInTheStorehouseIsFilledBeforeTheKitchen(GameTestHelper helper) {
		minerWithKitchen(helper, Store.PART_COBBLESTONE, (expect, held) -> {
			int cobble = expect.get(Items.COBBLESTONE);
			helper.assertTrue(cobble <= 54, "setup: " + cobble + " cobblestone is more than the Storehouse's room");
			helper.assertTrue(storehouse(helper, Items.COBBLESTONE) == held + cobble && count(helper, KITCHEN_CHEST, Items.COBBLESTONE) == 0,
				"dug " + cobble + " cobblestone: the Storehouse gained " + (storehouse(helper, Items.COBBLESTONE) - held) + ", the kitchen's chest has "
					+ count(helper, KITCHEN_CHEST, Items.COBBLESTONE));
			for (Map.Entry<Item, Integer> e : expect.entrySet()) {
				if (e.getKey() != Items.COBBLESTONE) {
					helper.assertTrue(count(helper, KITCHEN_CHEST, e.getKey()) == e.getValue(), "dug " + e.getValue() + " " + e.getKey()
						+ " (no room in the Storehouse): the kitchen's chest has " + count(helper, KITCHEN_CHEST, e.getKey()));
				}
			}
			LOG.info("[QA 1102e] B834: part-stack room took {} cobblestone before the kitchen", cobble);
		});
	}
}
