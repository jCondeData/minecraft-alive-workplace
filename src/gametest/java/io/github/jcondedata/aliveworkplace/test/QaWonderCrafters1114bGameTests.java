package io.github.jcondedata.aliveworkplace.test;

import io.github.jcondedata.aliveworkplace.blueprint.Blueprint;
import io.github.jcondedata.aliveworkplace.blueprint.BlueprintData;
import io.github.jcondedata.aliveworkplace.blueprint.BlueprintItem;
import io.github.jcondedata.aliveworkplace.blueprint.BlueprintLibrary;
import io.github.jcondedata.aliveworkplace.build.BuildPlan;
import io.github.jcondedata.aliveworkplace.build.BuildSite;
import io.github.jcondedata.aliveworkplace.build.BuildSiteManager;
import io.github.jcondedata.aliveworkplace.build.Builders;
import io.github.jcondedata.aliveworkplace.craft.CrafterJob;
import io.github.jcondedata.aliveworkplace.hall.VillageHallBlockEntity;
import io.github.jcondedata.aliveworkplace.hall.VillageRanks;
import io.github.jcondedata.aliveworkplace.registry.ModAttachments;
import io.github.jcondedata.aliveworkplace.registry.ModBlocks;
import io.github.jcondedata.aliveworkplace.registry.ModComponents;
import io.github.jcondedata.aliveworkplace.registry.ModGameRules;
import io.github.jcondedata.aliveworkplace.registry.ModVillagers;
import io.github.jcondedata.aliveworkplace.wonder.Wonder;
import io.github.jcondedata.aliveworkplace.wonder.WonderManager;
import io.github.jcondedata.aliveworkplace.wonder.WonderPlanItem;
import io.github.jcondedata.aliveworkplace.wonder.WonderSite;
import io.github.jcondedata.aliveworkplace.wonder.WonderStages;
import io.github.jcondedata.aliveworkplace.wonder.Wonders;
import io.github.jcondedata.aliveworkplace.work.Jobs;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.village.poi.PoiTypes;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.npc.VillagerProfession;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;

/**
 * QA (qa-lane-1114b), B1171's corner no test had reached: a Wonder's crew fed by crafters. The owner's words: what a
 * crafter makes for a builder reaches the place the builder takes it from, and a request already filled is never made
 * again. For a Wonder that place is its Foundation Stone's chests (35.4), not the lead's own bench.
 *
 * <p>The village: a Town's hall with its owner, one builder at a Builder's Bench who is handed the test folly's plan,
 * a Mason at a stonecutter and a Carpenter at his bench. Stage I's goods (bread) are in, and the stone's chests hold all
 * of stage I's blocks but its 33 stone brick stairs and its spruce door, plus 48 stone and 12 spruce planks that no block
 * of the stage needs: the Mason has to cut the stairs and the Carpenter make the door. Stage II's goods are not in, so
 * once stage I is up nothing more is asked for. Every 20 ticks the stairs and doors in the whole area (built, in any
 * container, bag, inventory or on the ground) are counted: never more than one round of making, and not one more made
 * in the 1200 ticks after stage I is up.
 */
public class QaWonderCrafters1114bGameTests implements net.fabricmc.fabric.api.gametest.v1.FabricGameTest {
	static final String TALL = "aliveworkplace_test:tall_area";
	static final ResourceLocation FOLLY = ResourceLocation.fromNamespaceAndPath("aliveworkplace_test", "test_folly");
	static final ResourceLocation STAGE_ONE = ResourceLocation.fromNamespaceAndPath("aliveworkplace_test", "wonder/test_folly/stage_1_0");
	static final BlockPos HALL = new BlockPos(2, 2, 27);
	static final BlockPos BENCH = new BlockPos(5, 2, 28);
	/** An empty chest by each builder's own bench: where the makings must NOT go (the Wonder is fed at its stone). */
	static final BlockPos BENCH_CHEST = new BlockPos(5, 2, 29);
	static final BlockPos BENCH_2 = new BlockPos(9, 2, 28);
	static final BlockPos BENCH_2_CHEST = new BlockPos(9, 2, 29);
	static final BlockPos CUTTER = new BlockPos(20, 2, 28);
	static final BlockPos CARPENTER_BENCH = new BlockPos(25, 2, 28);
	static final BlockPos MIDDLE = new BlockPos(14, 2, 14);
	/** Stone brick stairs: what the stage takes; a stonecutter cuts one from one stone, a crafting table four from six bricks. */
	static final int STAIRS = 33;
	static final int STAIRS_MOST = STAIRS + 3;
	/** One spruce door; a craft makes three. */
	static final int DOORS_MOST = 3;
	static final int SPARE_STONE = 48;
	static final int SPARE_PLANKS = 12;
	/** How long nothing more may be made once stage I is up. */
	static final int QUIET = 1200;

	static final class Run {
		ServerLevel level;
		UUID wonder;
		UUID part;
		BlockPos stone;
		List<BlockPos> chests = new ArrayList<>();
		/** The stage goods' chest by the stone: part of the Wonder's stockpile too. */
		BlockPos goodsChest;
		final AtomicReference<Villager> mason = new AtomicReference<>();
		final AtomicReference<Villager> carpenter = new AtomicReference<>();
		Villager builder;
		final List<Villager> crew = new ArrayList<>();
		int mostCrew;
		long start;
		/** Highest counts seen of stairs and doors anywhere; the tick stage I's part was seen finished (-1: not yet). */
		int mostStairs;
		int mostDoors;
		final AtomicLong partDoneAt = new AtomicLong(-1);
		int stairsAtDone = -1;
		int doorsAtDone = -1;
		int madeAtDone = -1;
		boolean stairsReachedStone;
		/** Every container (relative to the test) stairs or a door were seen in, at the 20-tick counts. */
		final java.util.Set<String> seenIn = new java.util.TreeSet<>();
		boolean doorReachedStone;

		long tick() {
			return level.getGameTime() - start;
		}

		int made() {
			return ModAttachments.ITEMS_CRAFTED.getOrElse(mason.get(), 0) + ModAttachments.ITEMS_CRAFTED.getOrElse(carpenter.get(), 0);
		}
	}

	/**
	 * Stage I of the folly with its stairs cut by the Mason and its door made by the Carpenter: each is made once, lands
	 * in the Foundation Stone's chests (never any other chest), is built, and nothing more is made for 1200 ticks after.
	 */
	//$ gametest_ticks_batch TALL '16000' '"qaWonderCrafters1114b"'
	@GameTest(template = TALL, timeoutTicks = 16000, batch = "qaWonderCrafters1114b")
	public void aWondersStairsAndDoorAreMadeOnceIntoTheStonesChestsAndBuilt(GameTestHelper helper) {
		Run run = setUp(helper, 1);
		watch(helper, run, null);
	}

	/**
	 * A crew of two on the Wonder (35.5): the lead and a member with his own bench and chest. What the crafters make still
	 * goes to the stone's chests, never into the member's or the lead's own chest, is made once and built.
	 */
	//$ gametest_ticks_batch TALL '16000' '"qaWonderCraftersCrew1114b"'
	@GameTest(template = TALL, timeoutTicks = 16000, batch = "qaWonderCraftersCrew1114b")
	public void aWonderCrewOfTwoGetsItsMakingsOnceAtTheStone(GameTestHelper helper) {
		Run run = setUp(helper, 2);
		watch(helper, run, () -> {
			if (run.mostCrew < 2) {
				throw new GameTestAssertException("the second builder never joined the Wonder's crew (at most " + run.mostCrew + ")");
			}
		});
	}

	/**
	 * The same, the Mason and the Carpenter each saved and loaded the first time they are caught carrying what they made
	 * to the Wonder: it still arrives once, nothing is made twice or lost, and stage I goes up.
	 */
	//$ gametest_ticks_batch TALL '16000' '"qaWonderCraftersReload1114b"'
	@GameTest(template = TALL, timeoutTicks = 16000, batch = "qaWonderCraftersReload1114b")
	public void reloadedMidDeliveryToAWonderTheMakingsStillArriveOnce(GameTestHelper helper) {
		Run run = setUp(helper, 1);
		AtomicBoolean masonReloaded = new AtomicBoolean();
		AtomicBoolean carpenterReloaded = new AtomicBoolean();
		helper.onEachTick(() -> {
			reloadIfDelivering(run, run.mason, masonReloaded);
			reloadIfDelivering(run, run.carpenter, carpenterReloaded);
		});
		watch(helper, run, () -> {
			if (!masonReloaded.get() && !carpenterReloaded.get()) {
				throw new GameTestAssertException("neither crafter was ever caught carrying to the Wonder, so the reload showed nothing");
			}
		});
	}

	/**
	 * The lead saved and loaded while his part waits for the stairs and the door: the request survives, the makings
	 * arrive at the stone once, and stage I goes up with nothing made again.
	 */
	//$ gametest_ticks_batch TALL '16000' '"qaWonderCraftersLead1114b"'
	@GameTest(template = TALL, timeoutTicks = 16000, batch = "qaWonderCraftersLead1114b")
	public void theLeadReloadedWhileHisPartWaitsGetsItsMakingsOnce(GameTestHelper helper) {
		Run run = setUp(helper, 1);
		AtomicBoolean reloaded = new AtomicBoolean();
		helper.onEachTick(() -> {
			BuildSite part = run.part == null ? null : BuildSiteManager.get(run.level).get(run.part);
			if (!reloaded.get() && part != null && part.missing().containsKey(Items.STONE_BRICK_STAIRS)) {
				reloaded.set(true);
				run.builder = QaCarpenterStorehouse1101GameTests.reload(run.level, run.builder);
			}
		});
		watch(helper, run, () -> helper.assertTrue(reloaded.get(), "the lead's part never waited for the stairs, so the reload showed nothing"));
	}

	// ------------------------------------------------------------------------------------------------------ helpers

	static void reloadIfDelivering(Run run, AtomicReference<Villager> who, AtomicBoolean done) {
		if (done.get()) {
			return;
		}
		CrafterJob job = ModAttachments.CRAFTER_JOB.get(who.get());
		if (job != null && job.phase() == CrafterJob.Phase.DELIVERING) {
			done.set(true);
			who.set(QaCarpenterStorehouse1101GameTests.reload(run.level, who.get()));
		}
	}

	static Run setUp(GameTestHelper helper, int builders) {
		Leftovers.clear(helper);
		Leftovers.buildSites(helper);
		Leftovers.village(helper, 48);
		ServerLevel level = helper.getLevel();
		helper.setDayTime(2000);
		int delay = level.getGameRules().getInt(ModGameRules.BUILD_DELAY);
		boolean help = level.getGameRules().getBoolean(ModGameRules.BUILDERS_HELP);
		boolean free = level.getGameRules().getBoolean(ModGameRules.FREE_MATERIALS);
		level.getGameRules().getRule(ModGameRules.BUILD_DELAY).set(2, level.getServer());
		level.getGameRules().getRule(ModGameRules.BUILDERS_HELP).set(false, level.getServer());
		level.getGameRules().getRule(ModGameRules.FREE_MATERIALS).set(false, level.getServer());
		Leftovers.after(helper, () -> {
			level.getGameRules().getRule(ModGameRules.BUILD_DELAY).set(delay, level.getServer());
			level.getGameRules().getRule(ModGameRules.BUILDERS_HELP).set(help, level.getServer());
			level.getGameRules().getRule(ModGameRules.FREE_MATERIALS).set(free, level.getServer());
		});
		Run run = new Run();
		run.level = level;
		helper.setBlock(HALL, ModBlocks.VILLAGE_HALL);
		ServerPlayer player = ProtectionSpecGameTests.player(helper);
		VillageHallBlockEntity hall = helper.getBlockEntity(HALL);
		hall.setOwner(player.getUUID(), "Owner");
		hall.setRank(VillageRanks.Rank.TOWN);
		helper.setBlock(BENCH, ModBlocks.BUILDERS_BENCH);
		run.builder = helper.spawn(EntityType.VILLAGER, BENCH.north(2));
		Builders.employ(level, run.builder, helper.absolutePos(BENCH));
		helper.setBlock(BENCH_CHEST, Blocks.CHEST);
		if (builders > 1) {
			helper.setBlock(BENCH_2, ModBlocks.BUILDERS_BENCH);
			helper.setBlock(BENCH_2_CHEST, Blocks.CHEST);
			Villager mate = helper.spawn(EntityType.VILLAGER, BENCH_2.north(2));
			Builders.employ(level, mate, helper.absolutePos(BENCH_2));
			run.crew.add(mate);
		}
		helper.setBlock(CUTTER, Blocks.STONECUTTER);
		Villager mason = helper.spawn(EntityType.VILLAGER, CUTTER.north(2));
		Jobs.employ(level, mason, helper.absolutePos(CUTTER), PoiTypes.MASON, VillagerProfession.MASON);
		run.mason.set(mason);
		helper.setBlock(CARPENTER_BENCH, ModBlocks.CARPENTERS_BENCH);
		Villager carpenter = helper.spawn(EntityType.VILLAGER, CARPENTER_BENCH.north(2));
		Jobs.employ(level, carpenter, helper.absolutePos(CARPENTER_BENCH), ModVillagers.CARPENTERS_BENCH_POI, ModVillagers.CARPENTER);
		run.carpenter.set(carpenter);

		// the plan, placed with its middle at MIDDLE
		Wonder folly = Wonders.loaded().get(FOLLY);
		helper.assertTrue(folly != null, "the test folly isn't loaded: " + Wonders.problems());
		BlockPos hallPos = helper.absolutePos(HALL);
		ItemStack plan = WonderPlanItem.of(level, hallPos, folly, Component.literal("Testholm"));
		BlueprintData data = BlueprintItem.data(plan).orElseThrow();
		BlockPos origin = helper.absolutePos(MIDDLE).subtract(StructureTemplate.transform(new BlockPos(4, 0, 4), Mirror.NONE, Rotation.NONE, BlockPos.ZERO));
		BlueprintData.Placement where = new BlueprintData.Placement(level.dimension().location(), origin, Rotation.NONE, Mirror.NONE);
		plan.set(ModComponents.BLUEPRINT, data.withPlacement(Optional.of(where)));
		run.stone = WonderStages.stoneSpot(level, where).orElseThrow(() -> new GameTestAssertException("no spot for the Foundation Stone"));

		// stage I's goods only: stage II waits, so nothing more is asked for once stage I is up
		BlockPos goods = WonderGoodsKit.goodsChest(where, run.stone);
		level.setBlockAndUpdate(goods, Blocks.CHEST.defaultBlockState());
		run.goodsChest = goods;
		int slot = 0;
		for (Wonder.Good good : folly.stages().get(0).goods()) {
			slot = WonderGoodsKit.put((Container) level.getBlockEntity(goods), slot, good, good.count());
		}

		// stage I's blocks in the stone's chests, but its stairs and door; the makings for those, which it doesn't need
		Blueprint part = BlueprintLibrary.get(level, STAGE_ONE).orElseThrow(() -> new GameTestAssertException("no stage I part"));
		Map<Item, Integer> materials = BuildPlan.create(part, where).materials();
		helper.assertTrue(materials.getOrDefault(Items.STONE_BRICK_STAIRS, 0) == STAIRS, "stage I takes " + materials.get(Items.STONE_BRICK_STAIRS)
			+ " stone brick stairs, not " + STAIRS + ": " + materials);
		helper.assertTrue(materials.getOrDefault(Items.SPRUCE_DOOR, 0) == 1, "stage I takes " + materials.get(Items.SPRUCE_DOOR) + " spruce doors: " + materials);
		helper.assertTrue(!materials.containsKey(Items.STONE) && !materials.containsKey(Items.SPRUCE_PLANKS), "stage I takes stone or spruce planks: " + materials);
		List<ItemStack> stock = new ArrayList<>();
		materials.forEach((item, n) -> {
			if (item == Items.STONE_BRICK_STAIRS || item == Items.SPRUCE_DOOR) {
				return;
			}
			for (int left = n; left > 0; left -= item.getDefaultMaxStackSize()) {
				stock.add(new ItemStack(item, Math.min(left, item.getDefaultMaxStackSize())));
			}
		});
		stock.add(new ItemStack(Items.STONE, SPARE_STONE));
		stock.add(new ItemStack(Items.SPRUCE_PLANKS, SPARE_PLANKS));
		int dx = Integer.signum(run.stone.getX() - helper.absolutePos(MIDDLE).getX());
		int dz = Integer.signum(run.stone.getZ() - helper.absolutePos(MIDDLE).getZ());
		run.chests.add(run.stone.offset(2 * dx, 0, 0));
		run.chests.add(run.stone.offset(0, 0, 2 * dz));
		for (BlockPos p : run.chests) {
			level.setBlockAndUpdate(p, Blocks.CHEST.defaultBlockState());
		}
		Container first = (Container) level.getBlockEntity(run.chests.get(0));
		helper.assertTrue(stock.size() <= first.getContainerSize(), "stage I's stock takes " + stock.size() + " slots");
		for (int i = 0; i < stock.size(); i++) {
			first.setItem(i, stock.get(i));
		}

		if (WonderStages.handOver(player, run.builder, plan) != InteractionResult.SUCCESS) {
			throw new GameTestAssertException("the folly's plan wasn't taken: cap " + Wonders.capRefusal(level, hallPos, FOLLY).map(Component::getString)
				+ ", bench " + Builders.benchPos(run.builder));
		}
		WonderSite site = WonderManager.get(level).in(hallPos).get(0);
		run.wonder = site.id();
		UUID id = site.id();
		Leftovers.after(helper, () -> {
			BuildSiteManager sites = BuildSiteManager.get(level);
			for (BuildSite s : List.copyOf(sites.all())) {
				if (id.equals(s.wonder())) {
					sites.remove(s.id());
				}
			}
			WonderManager.get(level).remove(id);
		});
		helper.assertTrue(site.stone().map(run.stone::equals).orElse(true), "the stone went to " + site.stone() + ", not " + run.stone);
		run.start = level.getGameTime();
		return run;
	}

	/** The every-tick checks, and the pass: stage I up, every stair and the door built, nothing more made in {@link #QUIET} ticks. */
	static void watch(GameTestHelper helper, Run run, Runnable extra) {
		helper.onEachTick(() -> {
			ServerLevel level = run.level;
			if (level.getGameTime() % 200 == 0) {
				helper.setDayTime(2000);
			}
			WonderSite now = WonderManager.get(level).site(run.wonder).orElse(null);
			if (now == null) {
				throw new GameTestAssertException("the Wonder is gone at tick " + run.tick());
			}
			if (run.part == null && now.stage() <= 1 && !now.parts().isEmpty()) {
				run.part = now.parts().get(0);
			}
			run.mostCrew = Math.max(run.mostCrew, io.github.jcondedata.aliveworkplace.wonder.WonderCrews.crew(level, now).size());
			for (BlockPos own : List.of(BENCH_CHEST, BENCH_2_CHEST)) {
				if (level.getBlockEntity(helper.absolutePos(own)) instanceof Container c
					&& (c.countItem(Items.STONE_BRICK_STAIRS) > 0 || c.countItem(Items.SPRUCE_DOOR) > 0)) {
					throw new GameTestAssertException("at tick " + run.tick() + " the chest by a builder's own bench (" + own.toShortString() + ") holds "
						+ c.countItem(Items.STONE_BRICK_STAIRS) + " stairs and " + c.countItem(Items.SPRUCE_DOOR) + " doors: the Wonder is fed at its stone" + state(helper, run));
				}
			}
			for (BlockPos p : run.chests) {
				if (level.getBlockEntity(p) instanceof Container c) {
					run.stairsReachedStone |= c.countItem(Items.STONE_BRICK_STAIRS) > 0;
					run.doorReachedStone |= c.countItem(Items.SPRUCE_DOOR) > 0;
				}
			}
			if (run.tick() % 20 != 0) {
				return;
			}
			Map<Item, Integer> t = tally(helper, run);
			int stairs = t.getOrDefault(Items.STONE_BRICK_STAIRS, 0);
			int doors = t.getOrDefault(Items.SPRUCE_DOOR, 0);
			run.mostStairs = Math.max(run.mostStairs, stairs);
			run.mostDoors = Math.max(run.mostDoors, doors);
			if (stairs > STAIRS_MOST) {
				throw new GameTestAssertException("at tick " + run.tick() + " there are " + stairs + " stone brick stairs in the village; stage I takes " + STAIRS
					+ ": made again after it was delivered" + state(helper, run));
			}
			if (doors > DOORS_MOST) {
				throw new GameTestAssertException("at tick " + run.tick() + " there are " + doors + " spruce doors in the village; stage I takes 1: made again" + state(helper, run));
			}
			// stone and spruce wood are only turned into stairs and doors, never lost or doubled
			int stone = t.getOrDefault(Items.STONE, 0) + t.getOrDefault(Items.STONE_BRICK_STAIRS, 0);
			if (stone > SPARE_STONE) {
				throw new GameTestAssertException("at tick " + run.tick() + " stone and stairs add up to " + stone + ", more than the " + SPARE_STONE + " stone there was" + state(helper, run));
			}
			int wood = t.getOrDefault(Items.SPRUCE_PLANKS, 0) + 2 * t.getOrDefault(Items.SPRUCE_DOOR, 0);
			if (wood != SPARE_PLANKS) {
				throw new GameTestAssertException("at tick " + run.tick() + " spruce planks and doors add up to " + wood + " planks' worth, not " + SPARE_PLANKS + state(helper, run));
			}
			boolean partDone = run.part != null && (BuildSiteManager.get(level).get(run.part) == null || BuildSiteManager.get(level).get(run.part).isDone());
			if (partDone && run.partDoneAt.get() < 0) {
				run.partDoneAt.set(run.tick());
				run.stairsAtDone = stairs;
				run.doorsAtDone = doors;
				run.madeAtDone = run.made();
				io.github.jcondedata.aliveworkplace.AliveWorkplace.LOG.info("QaWonderCrafters1114b: stage I up at tick {}, crafters made {}, {} stairs, {} doors", run.tick(),
					run.madeAtDone, stairs, doors);
			} else if (run.partDoneAt.get() >= 0) {
				if (run.made() != run.madeAtDone || stairs != run.stairsAtDone || doors != run.doorsAtDone) {
					throw new GameTestAssertException((run.tick() - run.partDoneAt.get()) + " ticks after stage I was up, the crafters' count went from " + run.madeAtDone
						+ " to " + run.made() + ", stairs from " + run.stairsAtDone + " to " + stairs + ", doors from " + run.doorsAtDone + " to " + doors
						+ ": a filled request made again" + state(helper, run));
				}
			}
		});
		helper.succeedWhen(() -> {
			ServerLevel level = run.level;
			helper.assertTrue(run.part != null, "stage I's part never began" + state(helper, run));
			long doneAt = run.partDoneAt.get();
			if (doneAt < 0) {
				throw new GameTestAssertException("stage I isn't up at tick " + run.tick() + state(helper, run));
			}
			if (run.tick() - doneAt < QUIET) {
				throw new GameTestAssertException("stage I up at " + doneAt + ", still watching");
			}
			BlueprintData.Placement at = WonderManager.get(level).site(run.wonder).orElseThrow().placement().orElseThrow();
			Blueprint part = BlueprintLibrary.get(level, STAGE_ONE).orElseThrow();
			int stairs = 0;
			int doors = 0;
			BuildPlan plan = BuildPlan.create(part, at);
			java.util.Set<BlockPos> seen = new java.util.HashSet<>(); // a spot can be on more than one stage's list
			for (BuildPlan.Stage stage : BuildPlan.Stage.values()) {
				for (BuildPlan.Step s : plan.steps(stage)) {
					if (!seen.add(s.pos().immutable())) {
						continue;
					}
					BlockState state = level.getBlockState(s.pos());
					stairs += s.state().is(Blocks.STONE_BRICK_STAIRS) && state.is(Blocks.STONE_BRICK_STAIRS) ? 1 : 0;
					doors += s.state().is(Blocks.SPRUCE_DOOR) && state.is(Blocks.SPRUCE_DOOR) && state.getValue(DoorBlock.HALF) == DoubleBlockHalf.LOWER ? 1 : 0;
				}
			}
			helper.assertTrue(stairs == STAIRS, "stage I is up with " + stairs + " of its " + STAIRS + " stone brick stairs built" + state(helper, run));
			helper.assertTrue(doors == 1, "stage I is up without its spruce door (" + doors + ")" + state(helper, run));
			// (the lead, waiting by the stone, may take what was just put in within the same tick, so they needn't be seen there;
			// but no container other than the stone's may ever have held any)
			helper.assertTrue(run.stairsReachedStone || run.doorReachedStone || !run.seenIn.isEmpty(), "no making was ever seen in any chest" + state(helper, run));
			for (String where : run.seenIn) {
				helper.assertTrue(where.endsWith("(stone)"), "makings were seen in a chest that isn't the Wonder's stockpile: " + where + state(helper, run));
			}
			helper.assertTrue(ModAttachments.ITEMS_CRAFTED.getOrElse(run.mason.get(), 0) >= STAIRS, "the Mason made only "
				+ ModAttachments.ITEMS_CRAFTED.getOrElse(run.mason.get(), 0) + " of the " + STAIRS + " stairs" + state(helper, run));
			helper.assertTrue(ModAttachments.ITEMS_CRAFTED.getOrElse(run.carpenter.get(), 0) >= 1, "the Carpenter made no door" + state(helper, run));
			if (extra != null) {
				extra.run();
			}
		});
	}

	/** Stairs and doors (a door counted by its lower half), stone and spruce planks: built, in containers, bags and inventories, and on the ground. */
	static Map<Item, Integer> tally(GameTestHelper helper) {
		return tally(helper, null);
	}

	static Map<Item, Integer> tally(GameTestHelper helper, Run run) {
		List<Item> watched = List.of(Items.STONE_BRICK_STAIRS, Items.SPRUCE_DOOR, Items.STONE, Items.SPRUCE_PLANKS);
		Map<Item, Integer> out = new HashMap<>();
		ServerLevel level = helper.getLevel();
		AABB box = helper.getBounds().inflate(4);
		int floor = helper.absolutePos(new BlockPos(0, 2, 0)).getY();
		for (BlockPos p : BlockPos.betweenClosed((int) box.minX, (int) box.minY, (int) box.minZ, (int) box.maxX, (int) box.maxY, (int) box.maxZ)) {
			BlockState state = level.getBlockState(p);
			if (p.getY() >= floor && !state.isAir()) {
				Item item = state.getBlock().asItem();
				if (item == Items.STONE_BRICK_STAIRS || (item == Items.SPRUCE_DOOR && state.getValue(DoorBlock.HALF) == DoubleBlockHalf.LOWER)) {
					out.merge(item, 1, Integer::sum);
				}
			}
			if (state.hasBlockEntity()) {
				BlockEntity be = level.getBlockEntity(p);
				if (be instanceof Container c) {
					for (int i = 0; i < c.getContainerSize(); i++) {
						ItemStack s = c.getItem(i);
						if (watched.contains(s.getItem())) {
							out.merge(s.getItem(), s.getCount(), Integer::sum);
							if (run != null && (s.is(Items.STONE_BRICK_STAIRS) || s.is(Items.SPRUCE_DOOR))) {
								run.seenIn.add(helper.relativePos(p).toShortString() + (run.chests.contains(p.immutable()) || p.equals(run.goodsChest) ? " (stone)" : ""));
							}
						}
					}
				}
			}
		}
		for (Villager v : level.getEntitiesOfClass(Villager.class, box.inflate(16))) {
			for (Item item : watched) {
				int n = ModAttachments.BUILDER_BAG.getOrCreate(v).count(item) + v.getInventory().countItem(item);
				if (n > 0) {
					out.merge(item, n, Integer::sum);
				}
			}
		}
		for (ItemEntity e : level.getEntitiesOfClass(ItemEntity.class, box.inflate(16))) {
			if (watched.contains(e.getItem().getItem())) {
				out.merge(e.getItem().getItem(), e.getItem().getCount(), Integer::sum);
			}
		}
		return out;
	}

	static String state(GameTestHelper helper, Run run) {
		ServerLevel level = run.level;
		StringBuilder out = new StringBuilder(" [tick ").append(run.tick());
		WonderSite now = WonderManager.get(level).site(run.wonder).orElse(null);
		out.append("; wonder stage ").append(now == null ? "-" : now.stage()).append(" parts ").append(now == null ? "-" : now.parts().size());
		BuildSite part = run.part == null ? null : BuildSiteManager.get(level).get(run.part);
		out.append("; part ").append(part == null ? (run.part == null ? "none" : "done") : part.stage() + "/" + part.status() + " missing " + part.missing()
			+ " placed " + part.placed() + " skipped " + part.skipped());
		for (AtomicReference<Villager> who : List.of(run.mason, run.carpenter)) {
			Villager v = who.get();
			out.append("; ").append(v.getVillagerData().getProfession()).append(" made ").append(ModAttachments.ITEMS_CRAFTED.getOrElse(v, 0))
				.append(" job ").append(ModAttachments.CRAFTER_JOB.get(v)).append(" bag ").append(ModAttachments.BUILDER_BAG.getOrCreate(v).stacks());
		}
		out.append("; makings seen in ").append(run.seenIn);
		out.append("; builder bag ").append(ModAttachments.BUILDER_BAG.getOrCreate(run.builder).stacks());
		out.append("; tally ").append(tally(helper));
		out.append("; stone chests ");
		for (BlockPos p : run.chests) {
			if (level.getBlockEntity(p) instanceof Container c) {
				out.append(c.countItem(Items.STONE_BRICK_STAIRS)).append(" stairs/").append(c.countItem(Items.SPRUCE_DOOR)).append(" doors/")
					.append(c.countItem(Items.STONE)).append(" stone ");
			}
		}
		return out.append("]").toString();
	}
}
