package io.github.jcondedata.aliveworkplace.test;

import io.github.jcondedata.aliveworkplace.build.BuildSite;
import io.github.jcondedata.aliveworkplace.build.Builders;
import io.github.jcondedata.aliveworkplace.craft.CrafterJob;
import io.github.jcondedata.aliveworkplace.registry.ModAttachments;
import io.github.jcondedata.aliveworkplace.registry.ModBlocks;
import io.github.jcondedata.aliveworkplace.registry.ModGameRules;
import io.github.jcondedata.aliveworkplace.registry.ModVillagers;
import io.github.jcondedata.aliveworkplace.work.Jobs;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Vec3i;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Container;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;

/**
 * QA (lane-e, 2026-11-01) for B773, from its spec: when the Storehouse fills while the Carpenter carries a chestless
 * builder's planks to it and stays full, he never drops them by his bench while the builder still waits. After the
 * 2400-tick wait he gives the job up (takes it home) only when the chests by his own bench have room for ALL of it; with
 * none, full ones, or room for only part, he keeps the planks in his bag, keeps the claim and retries the Storehouse
 * every 40 ticks. Each test also checks the wood adds up at every tick (nothing lost or made twice) and that no spruce
 * plank ever lies on the ground. Each test has its own batch: the crafters' claims and the village are shared state.
 */
public class QaCarpenterFullStore1101cGameTests implements net.fabricmc.fabric.api.gametest.v1.FabricGameTest {
	private static final String BIG = "aliveworkplace_test:big_area";
	private static final BlockPos STATION = QaCarpenterStorehouse1101GameTests.STATION;
	private static final BlockPos HOME = QaCarpenterStorehouse1101GameTests.CARPENTER_CHEST;
	private static final BlockPos STORE_CHEST = QaCarpenterStorehouse1101GameTests.STORE_CHEST;
	private static final BlockPos ROW = QaCarpenterStorehouse1101GameTests.ROW_A;
	/** The B773 wait before he may give up, and how long past it these tests watch. */
	private static final int WAIT = 2400;

	enum Home { NONE, FULL, ONE_SLOT, ROOM }

	/**
	 * The Storehouse stays full 2800 ticks after he set off and no chest stands by his bench: he still carries all 4
	 * planks, still on the job, the builder still waiting; then one Storehouse slot frees and the builder gets them.
	 */
	//$ gametest_ticks_batch BIG '6000' '"qaFull1101cKeep"'
	@GameTest(template = BIG, timeoutTicks = 6000, batch = "qaFull1101cKeep")
	public void heKeepsCarryingPastTheWaitAndDeliversWhenASlotFrees(GameTestHelper helper) {
		Run run = setUp(helper, "keep", 1, ROW, 3, 1, Home.NONE);
		Container store = helper.getBlockEntity(STORE_CHEST);
		AtomicInteger filledAt = new AtomicInteger(-1);
		AtomicInteger freedAt = new AtomicInteger(-1);
		helper.onEachTick(() -> {
			int tick = (int) run.tick();
			everyTick(helper, run, 4);
			if (filledAt.get() < 0 && carrying(run.carpenter()) > 0) {
				QaVillageEconomySaves1101GameTests.fillEmpty(store);
				filledAt.set(tick);
			}
			if (filledAt.get() >= 0 && freedAt.get() < 0 && tick == filledAt.get() + WAIT + 400) {
				stillCarrying(helper, run, run.carpenter(), 4, "2800 ticks after the Storehouse filled (no chest by his bench)");
				store.setItem(store.getContainerSize() - 1, ItemStack.EMPTY);
				freedAt.set(tick);
			}
			if (freedAt.get() >= 0 && tick > freedAt.get() + 900 && !QaCarpenterStorehouse1101GameTests.done(helper, run.site())) {
				throw new GameTestAssertException("900 ticks after a Storehouse slot freed the builder still waits for " + run.site().missing()
					+ " (Carpenter carries " + carrying(run.carpenter()) + ", store " + store.countItem(Items.SPRUCE_PLANKS) + ")");
			}
		});
		helper.succeedWhen(() -> {
			helper.assertTrue(freedAt.get() >= 0, "the Storehouse slot was never freed (filled at " + filledAt.get() + ")");
			helper.assertTrue(QaCarpenterStorehouse1101GameTests.done(helper, run.site()), "the row isn't finished: missing " + run.site().missing());
			helper.assertTrue(QaCarpenterStorehouse1101GameTests.placed(helper, ROW) == 3, "the row has " + QaCarpenterStorehouse1101GameTests.placed(helper, ROW) + " of 3 planks");
			helper.assertTrue(carrying(run.carpenter()) == 0 && ModAttachments.CRAFTER_JOB.get(run.carpenter()) == null,
				"after delivering he still carries " + carrying(run.carpenter()) + " planks / job " + ModAttachments.CRAFTER_JOB.get(run.carpenter()));
			helper.getLevel().getServer().sendSystemMessage(net.minecraft.network.chat.Component.literal(
				"[qaFull1101cKeep] kept 4 planks 2800 ticks past a full Storehouse, delivered after a slot freed, none on the ground"));
		});
	}

	/** The chest by his bench is full: past the wait he still keeps the 4 planks; nothing in or by his chest. */
	//$ gametest_ticks_batch BIG '5000' '"qaFull1101cFullHome"'
	@GameTest(template = BIG, timeoutTicks = 5000, batch = "qaFull1101cFullHome")
	public void aFullChestByHisBenchStillMeansHeKeepsCarrying(GameTestHelper helper) {
		Run run = setUp(helper, "fullhome", 1, ROW, 3, 1, Home.FULL);
		Container store = helper.getBlockEntity(STORE_CHEST);
		AtomicInteger filledAt = new AtomicInteger(-1);
		AtomicBoolean checked = new AtomicBoolean();
		helper.onEachTick(() -> {
			int tick = (int) run.tick();
			everyTick(helper, run, 4);
			if (filledAt.get() < 0 && carrying(run.carpenter()) > 0) {
				QaVillageEconomySaves1101GameTests.fillEmpty(store);
				filledAt.set(tick);
			}
			if (filledAt.get() >= 0 && !checked.get() && tick == filledAt.get() + WAIT + 600) {
				stillCarrying(helper, run, run.carpenter(), 4, "3000 ticks after the Storehouse filled (his own chest full)");
				helper.assertTrue(QaCarpenterStorehouse1101GameTests.inChest(helper, HOME, Items.SPRUCE_PLANKS) == 0, "planks went into his full chest");
				checked.set(true);
			}
		});
		helper.succeedWhen(() -> {
			helper.assertTrue(checked.get(), "still watching (filled at " + filledAt.get() + ")");
			helper.getLevel().getServer().sendSystemMessage(net.minecraft.network.chat.Component.literal(
				"[qaFull1101cFullHome] full chest by his bench: still carried 4 planks 3000 ticks after the Storehouse filled, none on the ground"));
		});
	}

	/**
	 * The chest by his bench has one free slot and his load needs two: 4 planks and a bucket (a recipe's leftover; put in
	 * his bag as the Storehouse fills). Room for part is not room for all, so past the wait he keeps the whole load:
	 * nothing split into his chest, no plank or bucket on the ground, and the builder still waits for his planks.
	 */
	//$ gametest_ticks_batch BIG '6000' '"qaFull1101cPart"'
	@GameTest(template = BIG, timeoutTicks = 6000, batch = "qaFull1101cPart")
	public void roomForOnlyPartByHisBenchKeepsTheWholeLoadCarried(GameTestHelper helper) {
		Run run = setUp(helper, "part", 1, ROW, 3, 1, Home.ONE_SLOT);
		Container store = helper.getBlockEntity(STORE_CHEST);
		AtomicInteger filledAt = new AtomicInteger(-1);
		AtomicBoolean checked = new AtomicBoolean();
		helper.onEachTick(() -> {
			int tick = (int) run.tick();
			everyTick(helper, run, 4);
			if (filledAt.get() >= 0) {
				int buckets = QaCarpenterStorehouse1101GameTests.inBags(helper, Items.BUCKET) + QaCarpenterStorehouse1101GameTests.inChest(helper, HOME, Items.BUCKET)
					+ QaCarpenterStorehouse1101GameTests.inChest(helper, STORE_CHEST, Items.BUCKET);
				int lying = QaCarpenterStorehouse1101GameTests.onGround(helper, Items.BUCKET);
				if (buckets != 1 || lying > 0) {
					throw new GameTestAssertException("at tick " + tick + " the bucket is lost or dropped: bags/chests " + buckets + ", ground " + lying);
				}
			}
			if (filledAt.get() < 0 && carrying(run.carpenter()) > 0) {
				ModAttachments.BUILDER_BAG.getOrCreate(run.carpenter()).add(new ItemStack(Items.BUCKET));
				QaVillageEconomySaves1101GameTests.fillEmpty(store);
				filledAt.set(tick);
			}
			if (filledAt.get() >= 0 && !checked.get() && tick == filledAt.get() + WAIT + 600) {
				stillCarrying(helper, run, run.carpenter(), 4, "3000 ticks after the Storehouse filled (his chest has one free slot, his load needs two)");
				helper.assertTrue(ModAttachments.BUILDER_BAG.getOrCreate(run.carpenter()).count(Items.BUCKET) == 1, "he no longer carries the bucket");
				int home = QaCarpenterStorehouse1101GameTests.inChest(helper, HOME, Items.SPRUCE_PLANKS) + QaCarpenterStorehouse1101GameTests.inChest(helper, HOME, Items.BUCKET);
				helper.assertTrue(home == 0, home + " items of his load were split into his chest that had room for only part of it");
				checked.set(true);
			}
		});
		helper.succeedWhen(() -> {
			helper.assertTrue(checked.get(), "still watching (filled at " + filledAt.get() + ")");
			helper.getLevel().getServer().sendSystemMessage(net.minecraft.network.chat.Component.literal(
				"[qaFull1101cPart] one free slot by his bench for a two-slot load: kept planks and bucket, nothing split or dropped"));
		});
	}

	/**
	 * The chest by his bench has room for all 4: only after the 2400-tick wait (never before) he takes all of them home
	 * into it, none on the ground, none lost, and the job is let go. (Whether the builder then ever gets them is
	 * {@code QaCarpenterFullStore1101cBugTests}.)
	 */
	//$ gametest_ticks_batch BIG '6000' '"qaFull1101cHome"'
	@GameTest(template = BIG, timeoutTicks = 6000, batch = "qaFull1101cHome")
	public void roomForAllByHisBenchTakesItHomeOnlyAfterTheWait(GameTestHelper helper) {
		Run run = setUp(helper, "home", 1, ROW, 3, 1, Home.ROOM);
		Container store = helper.getBlockEntity(STORE_CHEST);
		AtomicInteger filledAt = new AtomicInteger(-1);
		AtomicInteger homeAt = new AtomicInteger(-1);
		AtomicInteger homeCount = new AtomicInteger();
		helper.onEachTick(() -> {
			int tick = (int) run.tick();
			everyTick(helper, run, 4);
			if (filledAt.get() < 0 && carrying(run.carpenter()) > 0) {
				QaVillageEconomySaves1101GameTests.fillEmpty(store);
				filledAt.set(tick);
			}
			int home = QaCarpenterStorehouse1101GameTests.inChest(helper, HOME, Items.SPRUCE_PLANKS);
			if (home > 0 && homeAt.get() < 0) {
				homeAt.set(tick);
				homeCount.set(home);
				helper.assertTrue(filledAt.get() >= 0 && tick - filledAt.get() >= WAIT, "the planks went home at tick " + tick + ", only "
					+ (tick - filledAt.get()) + " ticks after the Storehouse filled (before the " + WAIT + "-tick wait)");
			}
		});
		helper.succeedWhen(() -> {
			helper.assertTrue(homeAt.get() >= 0, "the planks never went into the chest by his bench (filled at " + filledAt.get() + ", he carries "
				+ carrying(run.carpenter()) + ")");
			helper.assertTrue(homeCount.get() == 4, "only " + homeCount.get() + " of 4 planks went home");
			helper.assertTrue(ModAttachments.CRAFTER_JOB.get(run.carpenter()) == null, "after taking it home the job is still saved");
			helper.getLevel().getServer().sendSystemMessage(net.minecraft.network.chat.Component.literal(
				"[qaFull1101cHome] room by his bench: took all 4 planks home " + (homeAt.get() - filledAt.get()) + " ticks after the Storehouse filled, none on the ground"));
		});
	}

	/**
	 * Saved and loaded (a restart) past the wait, still by the full Storehouse with no chest at home: the 4 planks come
	 * back in his bag on the same job, are never dropped, and reach the builder once a slot frees.
	 */
	//$ gametest_ticks_batch BIG '7000' '"qaFull1101cReload"'
	@GameTest(template = BIG, timeoutTicks = 7000, batch = "qaFull1101cReload")
	public void reloadedPastTheWaitKeepsThePlanksAndStillDelivers(GameTestHelper helper) {
		Run run = setUp(helper, "reload", 1, ROW, 3, 1, Home.NONE);
		Container store = helper.getBlockEntity(STORE_CHEST);
		AtomicReference<Villager> carpenter = new AtomicReference<>(run.carpenter());
		AtomicInteger filledAt = new AtomicInteger(-1);
		AtomicInteger reloadedAt = new AtomicInteger(-1);
		AtomicInteger freedAt = new AtomicInteger(-1);
		helper.onEachTick(() -> {
			int tick = (int) run.tick();
			everyTick(helper, run, 4);
			if (filledAt.get() < 0 && carrying(carpenter.get()) > 0) {
				QaVillageEconomySaves1101GameTests.fillEmpty(store);
				filledAt.set(tick);
			}
			if (filledAt.get() >= 0 && reloadedAt.get() < 0 && tick >= filledAt.get() + WAIT + 200) {
				Villager c = carpenter.get();
				stillCarrying(helper, run, c, 4, "just before the reload, " + (tick - filledAt.get()) + " ticks after the Storehouse filled");
				Villager copy = QaCarpenterStorehouse1101GameTests.reload(helper.getLevel(), c);
				carpenter.set(copy);
				reloadedAt.set(tick);
				CrafterJob job = ModAttachments.CRAFTER_JOB.get(copy);
				helper.assertTrue(job != null && job.phase() == CrafterJob.Phase.DELIVERING, "after the reload his job is " + job);
				helper.assertTrue(carrying(copy) == 4, "after the reload he carries " + carrying(copy) + " planks, not 4");
			}
			if (reloadedAt.get() >= 0 && freedAt.get() < 0 && tick == reloadedAt.get() + WAIT + 200) {
				stillCarrying(helper, run, carpenter.get(), 4, (tick - reloadedAt.get()) + " ticks after the reload");
				store.setItem(store.getContainerSize() - 1, ItemStack.EMPTY);
				freedAt.set(tick);
			}
			if (freedAt.get() >= 0 && tick > freedAt.get() + 900 && !QaCarpenterStorehouse1101GameTests.done(helper, run.site())) {
				throw new GameTestAssertException("900 ticks after a Storehouse slot freed (reloaded) the builder still waits for " + run.site().missing());
			}
		});
		helper.succeedWhen(() -> {
			helper.assertTrue(freedAt.get() >= 0, "still watching (filled " + filledAt.get() + ", reloaded " + reloadedAt.get() + ")");
			helper.assertTrue(QaCarpenterStorehouse1101GameTests.done(helper, run.site()), "the row isn't finished: missing " + run.site().missing());
			helper.assertTrue(QaCarpenterStorehouse1101GameTests.placed(helper, ROW) == 3, "the row isn't 3 planks");
			helper.assertTrue(ModAttachments.CRAFTER_JOB.get(carpenter.get()) == null, "the delivered job is still saved");
			helper.getLevel().getServer().sendSystemMessage(net.minecraft.network.chat.Component.literal(
				"[qaFull1101cReload] reloaded past the wait: kept 4 planks on the job, delivered once after a slot freed"));
		});
	}

	/**
	 * The builder's build is canceled while the Carpenter waits at the full Storehouse (no chest at home): the spec
	 * doesn't say where the planks go then, only that they are never lost nor left on the ground; checked 3000 ticks
	 * on and again after a Storehouse slot frees.
	 */
	//$ gametest_ticks_batch BIG '6000' '"qaFull1101cCancel"'
	@GameTest(template = BIG, timeoutTicks = 6000, batch = "qaFull1101cCancel")
	public void aCanceledBuildLosesNoPlanksAndDropsNone(GameTestHelper helper) {
		Run run = setUp(helper, "cancel", 1, ROW, 3, 1, Home.NONE);
		Container store = helper.getBlockEntity(STORE_CHEST);
		AtomicInteger filledAt = new AtomicInteger(-1);
		AtomicInteger canceledAt = new AtomicInteger(-1);
		AtomicInteger freedAt = new AtomicInteger(-1);
		AtomicReference<String> where = new AtomicReference<>("");
		helper.onEachTick(() -> {
			int tick = (int) run.tick();
			everyTick(helper, run, 4);
			if (filledAt.get() < 0 && carrying(run.carpenter()) > 0) {
				QaVillageEconomySaves1101GameTests.fillEmpty(store);
				filledAt.set(tick);
			}
			if (filledAt.get() >= 0 && canceledAt.get() < 0 && tick == filledAt.get() + 200) {
				Builders.cancel(helper.getLevel(), run.site());
				canceledAt.set(tick);
			}
			if (canceledAt.get() >= 0 && freedAt.get() < 0 && tick == canceledAt.get() + WAIT + 600) {
				where.set("bag " + carrying(run.carpenter()) + ", store " + store.countItem(Items.SPRUCE_PLANKS));
				store.setItem(store.getContainerSize() - 1, ItemStack.EMPTY);
				freedAt.set(tick);
			}
		});
		helper.succeedWhen(() -> {
			helper.assertTrue(freedAt.get() >= 0 && run.tick() >= freedAt.get() + 600, "still watching");
			int bags = QaCarpenterStorehouse1101GameTests.inBags(helper, Items.SPRUCE_PLANKS);
			int stored = store.countItem(Items.SPRUCE_PLANKS);
			helper.assertTrue(bags + stored == 4, "after the cancel the 4 planks aren't in a bag or the Storehouse: bags " + bags + ", store " + stored);
			helper.getLevel().getServer().sendSystemMessage(net.minecraft.network.chat.Component.literal(
				"[qaFull1101cCancel] build canceled while he waited: 3000 ticks on " + where.get() + "; after a freed slot bags " + bags + ", store " + stored
				+ "; none on the ground"));
		});
	}

	// ------------------------------------------------------------------------------------------------------ helpers

	record Run(ServerLevel level, long start, BuildSite site, Villager carpenter, BlockPos origin, int width, int depth) {
		long tick() {
			return level.getGameTime() - start;
		}
	}

	static Run setUp(GameTestHelper helper, String name, int logs, BlockPos origin, int width, int depth, Home home) {
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
		ResourceLocation id = floor(helper, name, origin, width, depth);
		helper.setBlock(QaCarpenterStorehouse1101GameTests.STOREHOUSE, ModBlocks.STOREHOUSE);
		QaBuilderDisruptionGameTests.ensurePoi(level, helper.absolutePos(QaCarpenterStorehouse1101GameTests.STOREHOUSE));
		helper.setBlock(STORE_CHEST, Blocks.CHEST);
		Container chest = helper.getBlockEntity(STORE_CHEST);
		chest.setItem(0, new ItemStack(Items.SPRUCE_LOG, logs));
		if (home != Home.NONE) {
			helper.setBlock(HOME, Blocks.CHEST);
			Container own = helper.getBlockEntity(HOME);
			int filled = home == Home.FULL ? own.getContainerSize() : home == Home.ONE_SLOT ? own.getContainerSize() - 1 : 0;
			for (int i = 0; i < filled; i++) {
				own.setItem(i, new ItemStack(Items.COBBLESTONE, 64));
			}
		}
		BuildSite site = QaCarpenterStorehouse1101GameTests.start(helper, QaCarpenterStorehouse1101GameTests.builder(helper, QaCarpenterStorehouse1101GameTests.BENCH_A),
			id, origin);
		helper.setBlock(STATION, ModBlocks.CARPENTERS_BENCH);
		Villager carpenter = helper.spawn(EntityType.VILLAGER, STATION.south());
		Jobs.employ(level, carpenter, helper.absolutePos(STATION), ModVillagers.CARPENTERS_BENCH_POI, ModVillagers.CARPENTER);
		return new Run(level, level.getGameTime(), site, carpenter, origin, width, depth);
	}

	/** A width x depth floor of spruce planks drawn at {@code origin}, saved as a blueprint, and the spot cleared again. */
	static ResourceLocation floor(GameTestHelper helper, String name, BlockPos origin, int width, int depth) {
		ServerLevel level = helper.getLevel();
		for (int x = 0; x < width; x++) {
			for (int z = 0; z < depth; z++) {
				helper.setBlock(origin.offset(x, 0, z), Blocks.SPRUCE_PLANKS);
			}
		}
		ResourceLocation id = ResourceLocation.fromNamespaceAndPath("aliveworkplace_test", "qa1101c_" + name + "_" + Long.toHexString(level.getGameTime()));
		level.getStructureManager().getOrCreate(id).fillFromWorld(level, helper.absolutePos(origin), new Vec3i(width, 1, depth), false, Blocks.STRUCTURE_VOID);
		for (int x = 0; x < width; x++) {
			for (int z = 0; z < depth; z++) {
				helper.setBlock(origin.offset(x, 0, z), Blocks.AIR);
			}
		}
		return id;
	}

	static int carrying(Villager carpenter) {
		return ModAttachments.BUILDER_BAG.getOrCreate(carpenter).count(Items.SPRUCE_PLANKS);
	}

	static int placed(GameTestHelper helper, Run run) {
		int n = 0;
		for (int x = 0; x < run.width(); x++) {
			for (int z = 0; z < run.depth(); z++) {
				if (helper.getBlockState(run.origin().offset(x, 0, z)).is(Blocks.SPRUCE_PLANKS)) {
					n++;
				}
			}
		}
		return n;
	}

	/** No spruce plank on the ground, ever; and the spruce wood adds up to {@code expected} planks' worth (a log is 4). */
	static void everyTick(GameTestHelper helper, Run run, int expected) {
		int lying = QaCarpenterStorehouse1101GameTests.onGround(helper, Items.SPRUCE_PLANKS);
		if (lying > 0) {
			throw new GameTestAssertException("at tick " + run.tick() + " " + lying + " spruce planks lie on the ground (builder done: "
				+ QaCarpenterStorehouse1101GameTests.done(helper, run.site()) + ", missing " + run.site().missing() + ")");
		}
		int logs = QaCarpenterStorehouse1101GameTests.inChest(helper, STORE_CHEST, Items.SPRUCE_LOG) + QaCarpenterStorehouse1101GameTests.inChest(helper, HOME, Items.SPRUCE_LOG)
			+ QaCarpenterStorehouse1101GameTests.inBags(helper, Items.SPRUCE_LOG) + QaCarpenterStorehouse1101GameTests.onGround(helper, Items.SPRUCE_LOG);
		int store = QaCarpenterStorehouse1101GameTests.inChest(helper, STORE_CHEST, Items.SPRUCE_PLANKS);
		int home = QaCarpenterStorehouse1101GameTests.inChest(helper, HOME, Items.SPRUCE_PLANKS);
		int bags = QaCarpenterStorehouse1101GameTests.inBags(helper, Items.SPRUCE_PLANKS);
		int placed = placed(helper, run);
		int wood = 4 * logs + store + home + bags + placed;
		if (wood != expected) {
			throw new GameTestAssertException("at tick " + run.tick() + " the spruce wood adds up to " + wood + " planks' worth, not " + expected + " (logs " + logs
				+ "; planks: store " + store + ", his chest " + home + ", bags " + bags + ", placed " + placed + ")");
		}
	}

	/** The B773 decision: he still carries {@code n} planks, on the job (delivering), and the builder still waits. */
	static void stillCarrying(GameTestHelper helper, Run run, Villager carpenter, int n, String when) {
		CrafterJob job = ModAttachments.CRAFTER_JOB.get(carpenter);
		helper.assertTrue(carrying(carpenter) == n, when + ": the Carpenter carries " + carrying(carpenter) + " planks, not " + n
			+ " (store " + QaCarpenterStorehouse1101GameTests.inChest(helper, STORE_CHEST, Items.SPRUCE_PLANKS) + ", his chest "
			+ QaCarpenterStorehouse1101GameTests.inChest(helper, HOME, Items.SPRUCE_PLANKS) + ")");
		helper.assertTrue(job != null && job.phase() == CrafterJob.Phase.DELIVERING, when + ": his job is " + job + ", not still delivering");
		helper.assertTrue(!QaCarpenterStorehouse1101GameTests.done(helper, run.site()) && !run.site().missing().isEmpty(),
			when + ": the builder isn't waiting any more (done " + QaCarpenterStorehouse1101GameTests.done(helper, run.site()) + ")");
	}
}
