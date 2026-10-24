package io.github.jcondedata.aliveworkplace.test;

import io.github.jcondedata.aliveworkplace.Expansions;
import io.github.jcondedata.aliveworkplace.blueprint.BlueprintData;
import io.github.jcondedata.aliveworkplace.build.BuildPlan;
import io.github.jcondedata.aliveworkplace.build.BuildSite;
import io.github.jcondedata.aliveworkplace.build.BuildSiteManager;
import io.github.jcondedata.aliveworkplace.build.Builders;
import io.github.jcondedata.aliveworkplace.craft.CrafterJob;
import io.github.jcondedata.aliveworkplace.craft.JobShortages;
import io.github.jcondedata.aliveworkplace.registry.ModAttachments;
import io.github.jcondedata.aliveworkplace.registry.ModBlocks;
import io.github.jcondedata.aliveworkplace.registry.ModGameRules;
import io.github.jcondedata.aliveworkplace.registry.ModVillagers;
import io.github.jcondedata.aliveworkplace.work.Jobs;
import io.github.jcondedata.aliveworkplace.work.WorkerStatus;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Vec3i;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.village.poi.PoiTypes;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.npc.VillagerProfession;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

/**
 * QA (lane-c-1024-0353) for ROADMAP 37.14/37.14a (each job crafts the whole chain a builder needs; the Crafter retired),
 * 37.16a (the Crafter's Workbench taken by nobody) and the player-read lines of both, written from the Done when: a
 * player taking the makings away mid-fetch, the job's workstation moved mid-chain, two carpenters on one builder,
 * a reload while the made things are carried to the builder, every line a carpenter shows through a chain, the shortage line's "and N more", Milestone 37 closed, and a Crafter's Workbench placed by a
 * player from the off hand. Each world test has its own batch: the jobs' claims are shared.
 */
public class QaCrafterFurnitureGameTests {
	static final String AREA = "aliveworkplace_test:build_area";
	static final BlockPos BENCH = new BlockPos(2, 2, 2);
	static final BlockPos CHEST = new BlockPos(2, 2, 4);
	static final BlockPos STATION = new BlockPos(14, 2, 2);

	record Asker(Villager builder, BuildSite site, BuildPlan plan, BlockPos chest) {
	}

	/**
	 * A player takes four of the six logs while the Carpenter is on the way to fetch them: nothing is half made (no planks
	 * sawn for barrels that can't be finished), the two logs it got come back, the job is let go, and the builder's line
	 * then says what's short ("short of 4 Oak Log to make 3 Barrel").
	 */
	//$ gametest_ticks_batch AREA '2400' '"qaChainLogsTaken"'
	@GameTest(template = AREA, timeoutTicks = 2400, batch = "qaChainLogsTaken")
	public void logsTakenMidFetchLeaveNothingHalfMade(GameTestHelper helper) {
		prepare(helper);
		Asker a = asker(helper, BENCH, CHEST, new BlockPos(6, 2, 9), Blocks.BARREL, new ItemStack(Items.OAK_LOG, 6));
		Villager carpenter = carpenter(helper, STATION);
		AtomicBoolean taken = new AtomicBoolean();
		AtomicInteger since = new AtomicInteger();
		helper.onEachTick(() -> {
			CrafterJob job = ModAttachments.CRAFTER_JOB.get(carpenter);
			if (!taken.get() && job != null && job.phase() == CrafterJob.Phase.FETCHING) {
				Container chest = helper.getBlockEntity(a.chest());
				helper.assertTrue(chest.countItem(Items.OAK_LOG) == 6, "the carpenter fetched before the player got there");
				chest.removeItem(0, 4);
				taken.set(true);
			}
			if (taken.get()) {
				since.incrementAndGet();
			}
		});
		helper.succeedWhen(() -> {
			helper.assertTrue(taken.get(), "the carpenter never set off for the logs");
			helper.assertTrue(since.get() > 700, "waiting for the carpenter to give up and the jobs to look again");
			ServerLevel level = helper.getLevel();
			Container chest = helper.getBlockEntity(a.chest());
			helper.assertTrue(chest.countItem(Items.OAK_LOG) == 2, "the two logs left should be back in the chest, " + chest.countItem(Items.OAK_LOG) + " there");
			helper.assertTrue(chest.countItem(Items.OAK_PLANKS) == 0 && chest.countItem(Items.OAK_SLAB) == 0 && loose(helper, Items.OAK_PLANKS) == 0,
				"planks or slabs were made for barrels that can't be finished");
			helper.assertTrue(ModAttachments.BUILDER_BAG.getOrCreate(carpenter).isEmpty(), "the carpenter still carries " + ModAttachments.BUILDER_BAG.getOrCreate(carpenter).stacks());
			helper.assertTrue(ModAttachments.CRAFTER_JOB.get(carpenter) == null, "the job it can't finish is still saved on the carpenter");
			Component line = JobShortages.line(level, a.site().id(), a.site().missing());
			helper.assertTrue(line != null && line.getString().equals("Can't be made yet: short of 4 Oak Log to make 3 Barrel"),
				"the builder's line after the logs were taken: " + (line == null ? null : line.getString()));
		});
	}

	/** Two Carpenters by one builder short of three barrels: they're made once (one takes the job, the other leaves it). */
	//$ gametest_ticks_batch AREA '3600' '"qaChainTwoCarpenters"'
	@GameTest(template = AREA, timeoutTicks = 3600, batch = "qaChainTwoCarpenters")
	public void twoCarpentersMakeOneBuildersBarrelsOnce(GameTestHelper helper) {
		prepare(helper);
		Asker a = asker(helper, BENCH, CHEST, new BlockPos(6, 2, 9), Blocks.BARREL, new ItemStack(Items.OAK_LOG, 6));
		Villager one = carpenter(helper, STATION);
		Villager two = carpenter(helper, new BlockPos(14, 2, 12));
		AtomicBoolean both = new AtomicBoolean();
		helper.onEachTick(() -> {
			if (ModAttachments.CRAFTER_JOB.get(one) != null && ModAttachments.CRAFTER_JOB.get(two) != null) {
				both.set(true);
			}
		});
		helper.succeedWhen(() -> {
			assertBuilt(helper, a);
			int made = ModAttachments.ITEMS_CRAFTED.getOrElse(one, 0) + ModAttachments.ITEMS_CRAFTED.getOrElse(two, 0);
			helper.assertTrue(made == 3, "the two carpenters made " + made + " barrels between them, not 3");
			helper.assertTrue(!both.get(), "both carpenters took a job at once for one builder's three barrels");
			Container chest = helper.getBlockEntity(a.chest());
			helper.assertTrue(chest.countItem(Items.OAK_LOG) == 0 && chest.countItem(Items.BARREL) == 0, "logs " + chest.countItem(Items.OAK_LOG)
				+ ", barrels " + chest.countItem(Items.BARREL) + " left in the chest");
		});
	}

	/**
	 * Saved and loaded while the made barrels are on their way to the builder: they're delivered once (the build gets its
	 * three, none spare, none lost), the made count isn't doubled, and no job stays saved afterwards.
	 */
	//$ gametest_ticks_batch AREA '4000' '"qaChainReloadDelivering"'
	@GameTest(template = AREA, timeoutTicks = 4000, batch = "qaChainReloadDelivering")
	public void aChainReloadedOnItsWayToTheBuilderDeliversOnce(GameTestHelper helper) {
		prepare(helper);
		Asker a = asker(helper, BENCH, CHEST, new BlockPos(6, 2, 9), Blocks.BARREL, new ItemStack(Items.OAK_LOG, 6));
		AtomicReference<Villager> carpenter = new AtomicReference<>(carpenter(helper, STATION));
		AtomicBoolean reloaded = new AtomicBoolean();
		helper.onEachTick(() -> {
			if (reloaded.get()) {
				return;
			}
			CrafterJob job = ModAttachments.CRAFTER_JOB.get(carpenter.get());
			if (job != null && job.phase() == CrafterJob.Phase.DELIVERING) {
				Villager copy = reload(helper.getLevel(), carpenter.get());
				CrafterJob after = ModAttachments.CRAFTER_JOB.get(copy);
				helper.assertTrue(after != null && after.phase() == CrafterJob.Phase.DELIVERING, "the job on its way to the builder after a reload: " + after);
				helper.assertTrue(ModAttachments.BUILDER_BAG.getOrCreate(copy).count(Items.BARREL) == 3,
					"the three barrels should be in the bag across the reload, " + ModAttachments.BUILDER_BAG.getOrCreate(copy).count(Items.BARREL));
				carpenter.set(copy);
				reloaded.set(true);
			}
		});
		helper.succeedWhen(() -> {
			helper.assertTrue(reloaded.get(), "the carpenter never set off with the barrels");
			assertBuilt(helper, a);
			Container chest = helper.getBlockEntity(a.chest());
			helper.assertTrue(chest.countItem(Items.BARREL) == 0 && loose(helper, Items.BARREL) == 0, "spare barrels: " + chest.countItem(Items.BARREL)
				+ " in the chest, " + loose(helper, Items.BARREL) + " on the ground");
			helper.assertTrue(ModAttachments.ITEMS_CRAFTED.getOrElse(carpenter.get(), 0) == 3, "the carpenter's count says "
				+ ModAttachments.ITEMS_CRAFTED.getOrElse(carpenter.get(), 0) + " made");
			helper.assertTrue(ModAttachments.CRAFTER_JOB.get(carpenter.get()) == null, "the delivered job is still saved");
			helper.assertTrue(ModAttachments.BUILDER_BAG.getOrCreate(carpenter.get()).isEmpty(), "left in the bag: "
				+ ModAttachments.BUILDER_BAG.getOrCreate(carpenter.get()).stacks());
		});
	}

	/**
	 * Every line above a Carpenter's head through a three-step chain reads as English with its numbers in: the title with
	 * the count made, "fetching what 3 Barrel take", each step ("making 3 Barrel (step 1 of 3)" to step 3), and
	 * "taking 3 Barrel to the builder"; no raw key or empty %s anywhere.
	 */
	//$ gametest_ticks_batch AREA '3600' '"qaChainLines"'
	@GameTest(template = AREA, timeoutTicks = 3600, batch = "qaChainLines")
	public void aCarpentersLinesReadRightThroughAChain(GameTestHelper helper) {
		prepare(helper);
		Asker a = asker(helper, BENCH, CHEST, new BlockPos(6, 2, 9), Blocks.BARREL, new ItemStack(Items.OAK_LOG, 6));
		Villager carpenter = carpenter(helper, STATION);
		Set<String> lines = new LinkedHashSet<>();
		Set<String> titles = new LinkedHashSet<>();
		helper.onEachTick(() -> {
			var entry = WorkerStatus.get(carpenter, helper.getLevel().getGameTime());
			if (entry != null) {
				lines.add(entry.line().getString());
				titles.add(entry.title().getString());
			}
		});
		helper.succeedWhen(() -> {
			assertBuilt(helper, a);
			for (String want : List.of("fetching what 3 Barrel take", "making 3 Barrel (step 1 of 3)", "making 3 Barrel (step 2 of 3)",
				"making 3 Barrel (step 3 of 3)", "taking 3 Barrel to the builder")) {
				helper.assertTrue(lines.contains(want), "the carpenter never said \"" + want + "\"; it said " + lines);
			}
			helper.assertTrue(titles.contains("Carpenter · 0 made") && titles.contains("Carpenter · 3 made"), "the carpenter's titles: " + titles);
			for (String s : lines) {
				helper.assertFalse(s.contains("aliveworkplace.") || s.contains("%"), "a line with a raw key or placeholder: " + s);
			}
		});
	}

	/**
	 * The builder's shortage line names at most two things: three lacks show the two biggest with "; " between; three
	 * crafted parts nobody makes show two and " and 1 more", only once the builder has waited 600 ticks.
	 */
	//$ gametest_ticks 'FabricGameTest.EMPTY_STRUCTURE' '800'
	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE, timeoutTicks = 800)
	public void theShortageLineShowsTwoAndCountsTheRest(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		UUID lacking = UUID.randomUUID();
		Map<Item, Integer> missing = new LinkedHashMap<>();
		missing.put(Items.BARREL, 5);
		missing.put(Items.OAK_STAIRS, 4);
		missing.put(Items.LANTERN, 3);
		long now = level.getGameTime();
		JobShortages.lacks(lacking, Items.LANTERN, JobShortages.lackLine(Map.of(Items.IRON_NUGGET, 8), 3, Items.LANTERN), now);
		JobShortages.lacks(lacking, Items.BARREL, JobShortages.lackLine(Map.of(Items.OAK_LOG, 2), 5, Items.BARREL), now);
		JobShortages.lacks(lacking, Items.OAK_STAIRS, JobShortages.lackLine(Map.of(Items.OAK_LOG, 1), 4, Items.OAK_STAIRS), now);
		Component three = JobShortages.line(level, lacking, missing);
		helper.assertTrue(three != null && three.getString().equals("Can't be made yet: short of 2 Oak Log to make 5 Barrel; short of 1 Oak Log to make 4 Oak Stairs"),
			"three lacks on the line: " + (three == null ? null : three.getString()));
		UUID nobody = UUID.randomUUID();
		helper.assertTrue(JobShortages.line(level, nobody, missing) == null, "\"nobody makes\" before the jobs had a look");
		helper.runAfterDelay(610, () -> {
			Component line = JobShortages.line(level, nobody, missing);
			helper.assertTrue(line != null && line.getString().equals("Nobody here makes: Barrel, Oak Stairs and 1 more"),
				"three things nobody makes: " + (line == null ? null : line.getString()));
			JobShortages.forget(lacking);
			JobShortages.forget(nobody);
			helper.succeed();
		});
	}

	/**
	 * With Milestone 37 closed (the owner's live server today) the jobs work as before 37.14a: the Carpenter still cuts the
	 * builder's slabs, but saves no chain on itself.
	 */
	//$ gametest_ticks_batch AREA '2400' '"qaChainM37Closed"'
	@GameTest(template = AREA, timeoutTicks = 2400, batch = "qaChainM37Closed")
	public void withMilestone37ClosedNoChainIsSaved(GameTestHelper helper) {
		prepare(helper);
		boolean was = Expansions.openForTests;
		Expansions.openForTests = false;
		try {
			helper.assertFalse(Expansions.isOpen("M37"), "Milestone 37 is still open with the test switch off");
		} catch (RuntimeException e) {
			Expansions.openForTests = was;
			throw e;
		}
		Asker a = asker(helper, BENCH, CHEST, new BlockPos(6, 2, 9), Blocks.OAK_SLAB, new ItemStack(Items.OAK_PLANKS, 3));
		Villager carpenter = carpenter(helper, STATION);
		AtomicBoolean saved = new AtomicBoolean();
		helper.onEachTick(() -> {
			if (ModAttachments.CRAFTER_JOB.get(carpenter) != null) {
				saved.set(true);
			}
		});
		// The gate stays closed for the whole job, and is opened again on this one tick whatever the outcome.
		helper.runAfterDelay(2000, () -> {
			try {
				assertBuilt(helper, a);
				helper.assertTrue(!saved.get(), "a chain was saved on the carpenter with Milestone 37 closed");
				helper.assertTrue(ModAttachments.ITEMS_CRAFTED.getOrElse(carpenter, 0) == 6, "the carpenter made " + ModAttachments.ITEMS_CRAFTED.getOrElse(carpenter, 0));
			} finally {
				Expansions.openForTests = was;
			}
			helper.succeed();
		});
	}

	/**
	 * The player moves the Carpenter's bench mid-chain (broken and put down again a few blocks away): the carpenter takes
	 * the bench again, carries on with the planks it had made, and the build gets its three barrels with no logs left over.
	 */
	//$ gametest_ticks_batch AREA '4000' '"qaChainBenchMoved"'
	@GameTest(template = AREA, timeoutTicks = 4000, batch = "qaChainBenchMoved")
	public void theCarpentersBenchMovedMidChainCarriesOn(GameTestHelper helper) {
		prepare(helper);
		Asker a = asker(helper, BENCH, CHEST, new BlockPos(6, 2, 9), Blocks.BARREL, new ItemStack(Items.OAK_LOG, 6));
		Villager carpenter = carpenter(helper, STATION);
		AtomicInteger since = new AtomicInteger(-1);
		helper.onEachTick(() -> {
			CrafterJob job = ModAttachments.CRAFTER_JOB.get(carpenter);
			if (since.get() < 0 && job != null && job.phase() == CrafterJob.Phase.CRAFTING && job.step() >= 1) {
				helper.setBlock(STATION, Blocks.AIR);
				since.set(0);
			} else if (since.get() >= 0 && since.incrementAndGet() == 100) {
				helper.setBlock(STATION.east(2), ModBlocks.CARPENTERS_BENCH);
			}
		});
		helper.succeedWhen(() -> {
			helper.assertTrue(since.get() >= 100, "the bench wasn't moved yet");
			assertBuilt(helper, a);
			Container chest = helper.getBlockEntity(a.chest());
			helper.assertTrue(chest.countItem(Items.OAK_LOG) == 0 && chest.countItem(Items.BARREL) == 0, "logs " + chest.countItem(Items.OAK_LOG)
				+ ", barrels " + chest.countItem(Items.BARREL) + " in the chest");
			helper.assertTrue(carpenter.getVillagerData().getProfession() == ModVillagers.CARPENTER, "the carpenter lost the job: "
				+ carpenter.getVillagerData().getProfession());
		});
	}

	/**
	 * A player places a Crafter's Workbench from the off hand and a Carpenter's Bench from the main hand by a jobless
	 * villager: the villager becomes the Carpenter (so job taking works here), never a Crafter, and the workbench stays
	 * free; the player's item is used up from the hand it was in.
	 */
	//$ gametest_ticks_batch AREA '1200' '"qaCraftersWorkbenchPlaced"'
	@GameTest(template = AREA, timeoutTicks = 1200, batch = "qaCraftersWorkbenchPlaced")
	public void aPlacedCraftersWorkbenchIsTakenByNobody(GameTestHelper helper) {
		prepare(helper);
		ServerLevel level = helper.getLevel();
		ServerPlayer player = QaStageFiveGameTests.client(helper, GameType.SURVIVAL).player();
		BlockPos workbench = new BlockPos(6, 2, 6);
		BlockPos carpenterBench = new BlockPos(10, 2, 6);
		player.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
		player.setItemInHand(InteractionHand.OFF_HAND, new ItemStack(ModBlocks.CRAFTERS_WORKBENCH.asItem(), 2));
		place(helper, player, InteractionHand.OFF_HAND, workbench);
		helper.assertBlockPresent(ModBlocks.CRAFTERS_WORKBENCH, workbench);
		helper.assertTrue(player.getOffhandItem().getCount() == 1, "the off hand holds " + player.getOffhandItem());
		player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(ModBlocks.CARPENTERS_BENCH.asItem()));
		place(helper, player, InteractionHand.MAIN_HAND, carpenterBench);
		helper.assertBlockPresent(ModBlocks.CARPENTERS_BENCH, carpenterBench);
		Villager jobless = helper.spawn(EntityType.VILLAGER, new BlockPos(8, 2, 8));
		Villager second = helper.spawn(EntityType.VILLAGER, new BlockPos(6, 2, 8));
		AtomicBoolean crafter = new AtomicBoolean();
		helper.onEachTick(() -> {
			if (jobless.getVillagerData().getProfession() == ModVillagers.CRAFTER || second.getVillagerData().getProfession() == ModVillagers.CRAFTER) {
				crafter.set(true);
			}
		});
		helper.succeedWhen(() -> {
			helper.assertFalse(crafter.get(), "a villager took the Crafter's Workbench");
			boolean hired = jobless.getVillagerData().getProfession() == ModVillagers.CARPENTER || second.getVillagerData().getProfession() == ModVillagers.CARPENTER;
			helper.assertTrue(hired, "nobody took the Carpenter's Bench either (the test proves nothing): " + jobless.getVillagerData().getProfession()
				+ ", " + second.getVillagerData().getProfession());
			Villager other = jobless.getVillagerData().getProfession() == ModVillagers.CARPENTER ? second : jobless;
			helper.assertTrue(other.getVillagerData().getProfession() != ModVillagers.CRAFTER, "the other villager is a Crafter");
			helper.assertBlockPresent(ModBlocks.CRAFTERS_WORKBENCH, workbench);
			BlockPos abs = helper.absolutePos(workbench);
			helper.assertTrue(level.getPoiManager().getType(abs).map(h -> h.is(ModVillagers.CRAFTERS_WORKBENCH_POI)).orElse(false),
				"the placed workbench isn't a point of interest at all");
		});
	}

	// --- helpers (as JobChainGameTests's) ------------------------------------------------------------------------

	static void place(GameTestHelper helper, ServerPlayer player, InteractionHand hand, BlockPos at) {
		BlockPos below = helper.absolutePos(at.below());
		player.moveTo(Vec3.atCenterOf(below.above().south(2)));
		player.gameMode.useItemOn(player, helper.getLevel(), player.getItemInHand(hand), hand,
			new BlockHitResult(Vec3.atCenterOf(below).add(0, 0.5, 0), Direction.UP, below, false));
	}

	static int loose(GameTestHelper helper, Item item) {
		AABB box = new AABB(helper.absolutePos(BlockPos.ZERO)).inflate(20);
		return helper.getLevel().getEntitiesOfClass(ItemEntity.class, box, e -> e.getItem().is(item)).stream().mapToInt(e -> e.getItem().getCount()).sum();
	}

	static void prepare(GameTestHelper helper) {
		Leftovers.clear(helper);
		Leftovers.village(helper, 48);
		ServerLevel level = helper.getLevel();
		helper.setDayTime(2000);
		level.getGameRules().getRule(ModGameRules.BUILD_DELAY).set(2, level.getServer());
		level.getGameRules().getRule(ModGameRules.BUILDERS_HELP).set(false, level.getServer());
	}

	static Asker asker(GameTestHelper helper, BlockPos bench, BlockPos chest, BlockPos origin, Block block, ItemStack... items) {
		ServerLevel level = helper.getLevel();
		ResourceLocation id = row(helper, origin, block);
		helper.setBlock(bench, ModBlocks.BUILDERS_BENCH);
		helper.setBlock(chest, Blocks.CHEST);
		Container container = helper.getBlockEntity(chest);
		for (int i = 0; i < items.length; i++) {
			container.setItem(i, items[i].copy());
		}
		Villager builder = helper.spawn(EntityType.VILLAGER, bench.south());
		Builders.employ(level, builder, helper.absolutePos(bench));
		BuildSite site = Builders.start(level, builder, null, id, new BlueprintData.Placement(level.dimension().location(), helper.absolutePos(origin),
			Rotation.NONE, Mirror.NONE));
		BuildPlan plan = site.plan(level);
		if (plan == null) {
			throw new GameTestAssertException("blueprint not found: " + id);
		}
		return new Asker(builder, site, plan, chest);
	}

	static ResourceLocation row(GameTestHelper helper, BlockPos origin, Block block) {
		ServerLevel level = helper.getLevel();
		for (int x = 0; x < 3; x++) {
			helper.setBlock(origin.offset(x, 0, 0), block);
		}
		ResourceLocation id = ResourceLocation.fromNamespaceAndPath("aliveworkplace_test",
			"qachain_" + BuiltInRegistries.BLOCK.getKey(block).getPath() + "_" + Long.toHexString(level.getGameTime()) + "_" + origin.getX());
		level.getStructureManager().getOrCreate(id).fillFromWorld(level, helper.absolutePos(origin), new Vec3i(3, 1, 1), false, Blocks.STRUCTURE_VOID);
		for (int x = 0; x < 3; x++) {
			helper.setBlock(origin.offset(x, 0, 0), Blocks.AIR);
		}
		return id;
	}

	static Villager carpenter(GameTestHelper helper, BlockPos at) {
		helper.setBlock(at, ModBlocks.CARPENTERS_BENCH);
		Villager carpenter = helper.spawn(EntityType.VILLAGER, at.south());
		Jobs.employ(helper.getLevel(), carpenter, helper.absolutePos(at), ModVillagers.CARPENTERS_BENCH_POI, ModVillagers.CARPENTER);
		return carpenter;
	}

	static void assertBuilt(GameTestHelper helper, Asker a) {
		ServerLevel level = helper.getLevel();
		helper.assertTrue(BuildSiteManager.get(level).get(a.site().id()) == null,
			"still building: status=" + a.site().status() + " missing=" + a.site().missing());
		List<BlockPos> unfinished = a.plan().unfinished(level);
		helper.assertTrue(unfinished.isEmpty(), unfinished.size() + " block(s) wrong after the build");
	}

	static Villager reload(ServerLevel level, Villager villager) {
		CompoundTag tag = new CompoundTag();
		villager.saveWithoutId(tag);
		villager.discard();
		Villager copy = EntityType.VILLAGER.create(level);
		if (copy == null) {
			throw new GameTestAssertException("no villager made");
		}
		copy.load(tag);
		level.addFreshEntity(copy);
		io.github.jcondedata.aliveworkplace.work.Village.forget(copy);
		return copy;
	}
}
