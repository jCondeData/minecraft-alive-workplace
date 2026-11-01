package io.github.jcondedata.aliveworkplace.test;

import io.github.jcondedata.aliveworkplace.blueprint.BlueprintData;
import io.github.jcondedata.aliveworkplace.build.BuildSite;
import io.github.jcondedata.aliveworkplace.build.BuildSiteManager;
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
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Container;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;

/**
 * QA (lane-b-1101-0638) for B721, from its spec: a builder with no chest by his Blueprint Table, in a village with a
 * Storehouse, waits for spruce planks. "What the Carpenter makes reaches the builder who asked (the Storehouse he fetches
 * from, or by his bench), or he doesn't craft for a builder he can't deliver to." The fix's own test covers the plain
 * case; these cover exact counts (no plank lost or made twice), a builder who does have a chest, two chestless builders,
 * the Storehouse chest broken while the planks are on their way, no Storehouse at all, and a save
 * and reload on the way (a full Storehouse is B760). Each test has its own batch: the crafters'
 * claims and the village are shared state.
 */
public class QaCarpenterStorehouse1101GameTests implements net.fabricmc.fabric.api.gametest.v1.FabricGameTest {
	private static final String BIG = "aliveworkplace_test:big_area";
	static final BlockPos BENCH_A = new BlockPos(2, 2, 2);
	static final BlockPos BENCH_B = new BlockPos(5, 2, 2);
	static final BlockPos STATION = new BlockPos(13, 2, 2);
	/** The Carpenter's own chest, by his bench (only in the no-Storehouse test). */
	static final BlockPos CARPENTER_CHEST = new BlockPos(14, 2, 3);
	static final BlockPos STOREHOUSE = new BlockPos(18, 2, 19);
	static final BlockPos STORE_CHEST = new BlockPos(18, 2, 17);
	static final BlockPos A_CHEST = new BlockPos(0, 2, 2);
	static final BlockPos ROW_A = new BlockPos(6, 2, 8);
	static final BlockPos ROW_B = new BlockPos(6, 2, 11);

	/**
	 * Exactly one spare log, one chestless builder: at every tick the spruce wood adds up to exactly one log's worth (4
	 * planks, counting each log as 4) in the Storehouse, the bags, on the ground and in A's row; never more (made twice),
	 * never less (lost). The planks pass through the Storehouse, A's row is built, and the one plank left over is not on
	 * the ground.
	 */
	//$ gametest_ticks_batch BIG '4000' '"qaStore1101Count"'
	@GameTest(template = BIG, timeoutTicks = 4000, batch = "qaStore1101Count")
	public void aChestlessBuildersPlanksAddUpExactlyThroughTheStorehouse(GameTestHelper helper) {
		Run run = setUp(helper, "count", 1, false, false, false);
		AtomicBoolean seenInStore = new AtomicBoolean();
		helper.onEachTick(() -> {
			conserve(helper, run, 4);
			if (inChest(helper, STORE_CHEST, Items.SPRUCE_PLANKS) > 0) {
				seenInStore.set(true);
			}
			noPlanksLyingWhileWaiting(helper, run, run.a());
		});
		helper.succeedWhen(() -> {
			helper.assertTrue(done(helper, run.a()), "A's planks row isn't finished: " + run.a().status() + " missing " + run.a().missing());
			helper.assertTrue(placed(helper, ROW_A) == 3, "A's row has " + placed(helper, ROW_A) + " of its 3 planks");
			helper.assertTrue(seenInStore.get(), "the planks never went into the Storehouse (the chestless builder's only store)");
			helper.assertTrue(onGround(helper, Items.SPRUCE_PLANKS) == 0, "a plank lies on the ground");
			helper.assertTrue(onGround(helper, Items.SPRUCE_LOG) == 0 && inChest(helper, STORE_CHEST, Items.SPRUCE_LOG) == 0, "the log wasn't used");
		});
	}

	/**
	 * No regression: a builder who has his own chest still gets the planks in it, never in the Storehouse, and builds.
	 */
	//$ gametest_ticks_batch BIG '4000' '"qaStore1101OwnChest"'
	@GameTest(template = BIG, timeoutTicks = 4000, batch = "qaStore1101OwnChest")
	public void aBuilderWithHisOwnChestStillGetsThePlanksInIt(GameTestHelper helper) {
		Run run = setUp(helper, "own", 1, true, false, false);
		AtomicBoolean seenInOwn = new AtomicBoolean();
		helper.onEachTick(() -> {
			conserve(helper, run, 4);
			int inStore = inChest(helper, STORE_CHEST, Items.SPRUCE_PLANKS);
			if (inStore > 0) {
				throw new GameTestAssertException("at tick " + run.tick() + " " + inStore + " spruce planks went into the Storehouse though builder A has his own chest");
			}
			if (inChest(helper, A_CHEST, Items.SPRUCE_PLANKS) > 0) {
				seenInOwn.set(true);
			}
		});
		helper.succeedWhen(() -> {
			helper.assertTrue(done(helper, run.a()), "A's row isn't finished: " + run.a().status() + " missing " + run.a().missing());
			helper.assertTrue(placed(helper, ROW_A) == 3, "A's row has " + placed(helper, ROW_A) + " of its 3 planks");
			helper.assertTrue(seenInOwn.get(), "the planks never went into A's own chest");
		});
	}

	/**
	 * Two chestless builders each waiting for 3 spruce planks, two spare logs in the Storehouse: both rows are built, and
	 * the wood adds up to exactly two logs' worth at every tick.
	 */
	//$ gametest_ticks_batch BIG '5000' '"qaStore1101Two"'
	@GameTest(template = BIG, timeoutTicks = 5000, batch = "qaStore1101Two")
	public void twoChestlessBuildersBothGetTheirPlanks(GameTestHelper helper) {
		Run run = setUp(helper, "two", 2, false, true, false);
		helper.onEachTick(() -> {
			conserve(helper, run, 8);
			noPlanksLyingWhileWaiting(helper, run, run.a());
			noPlanksLyingWhileWaiting(helper, run, run.b());
		});
		helper.succeedWhen(() -> {
			helper.assertTrue(done(helper, run.a()), "A's row isn't finished: " + run.a().status() + " missing " + run.a().missing());
			helper.assertTrue(done(helper, run.b()), "B's row isn't finished: " + run.b().status() + " missing " + run.b().missing());
			helper.assertTrue(placed(helper, ROW_A) == 3 && placed(helper, ROW_B) == 3, "rows: A " + placed(helper, ROW_A) + "/3, B " + placed(helper, ROW_B) + "/3");
			helper.assertTrue(onGround(helper, Items.SPRUCE_PLANKS) == 0, "planks lie on the ground");
		});
	}

	/**
	 * The Storehouse chest is broken while the Carpenter carries the planks to it. Nothing is lost or doubled (the chest's
	 * contents spill, the planks in hand go somewhere), counted at every tick for 600 ticks after the break.
	 */
	//$ gametest_ticks_batch BIG '4000' '"qaStore1101Broken"'
	@GameTest(template = BIG, timeoutTicks = 4000, batch = "qaStore1101Broken")
	public void theStorehouseChestBrokenMidDeliveryLosesNoPlanks(GameTestHelper helper) {
		Run run = setUp(helper, "broken", 1, false, false, false);
		AtomicInteger brokeAt = new AtomicInteger(-1);
		helper.onEachTick(() -> {
			conserve(helper, run, 4);
			if (brokeAt.get() < 0 && inBags(helper, Items.SPRUCE_PLANKS) > 0) {
				helper.destroyBlock(STORE_CHEST);
				brokeAt.set((int) run.tick());
			}
		});
		helper.succeedWhen(() -> {
			helper.assertTrue(brokeAt.get() >= 0, "the Carpenter never set off with the planks");
			helper.assertTrue(run.tick() >= brokeAt.get() + 600, "waiting after the break");
			helper.assertTrue(planksAnywhere(helper) + placed(helper, ROW_A) == 4, "planks after the break: " + planksAnywhere(helper) + " loose + "
				+ placed(helper, ROW_A) + " placed");
		});
	}

	/**
	 * No Storehouse, no chest by the builder's table: the only log is in the Carpenter's own chest, where the builder never
	 * looks. The Carpenter either gets planks to him (his row is built) or leaves the log alone; planks never lie on the
	 * ground while he waits.
	 */
	//$ gametest_ticks_batch BIG '4000' '"qaStore1101None"'
	@GameTest(template = BIG, timeoutTicks = 4000, batch = "qaStore1101None")
	public void noStorehouseAndNoChestMeansNoPlanksLeftLying(GameTestHelper helper) {
		Run run = setUp(helper, "none", 0, false, false, true);
		helper.onEachTick(() -> {
			int wood = 4 * (logsAnywhere(helper) + inChest(helper, CARPENTER_CHEST, Items.SPRUCE_LOG))
				+ planksAnywhere(helper) + inChest(helper, CARPENTER_CHEST, Items.SPRUCE_PLANKS) + placed(helper, ROW_A);
			if (wood != 4) {
				throw new GameTestAssertException("at tick " + run.tick() + " the spruce wood adds up to " + wood + " planks' worth, not 4");
			}
			noPlanksLyingWhileWaiting(helper, run, run.a());
		});
		helper.succeedWhen(() -> {
			boolean built = done(helper, run.a());
			boolean leftAlone = run.tick() >= 3000 && inChest(helper, CARPENTER_CHEST, Items.SPRUCE_LOG) == 1;
			helper.assertTrue(built || leftAlone, "A neither built nor the log left alone: " + run.a().status() + ", carpenter's chest "
				+ inChest(helper, CARPENTER_CHEST, Items.SPRUCE_LOG) + " logs " + inChest(helper, CARPENTER_CHEST, Items.SPRUCE_PLANKS) + " planks");
		});
	}

	/**
	 * Saved and loaded while the planks for the chestless builder are on their way: after the reload the job still goes to
	 * the Storehouse, the planks arrive once (exact count at every tick), A's row is built and no job stays saved.
	 */
	//$ gametest_ticks_batch BIG '4000' '"qaStore1101Reload"'
	@GameTest(template = BIG, timeoutTicks = 4000, batch = "qaStore1101Reload")
	public void reloadedOnTheWayToTheStorehouseDeliversOnce(GameTestHelper helper) {
		Run run = setUp(helper, "reload", 1, false, false, false);
		AtomicReference<Villager> carpenter = new AtomicReference<>(run.carpenter());
		AtomicBoolean reloaded = new AtomicBoolean();
		helper.onEachTick(() -> {
			conserve(helper, run, 4);
			noPlanksLyingWhileWaiting(helper, run, run.a());
			if (reloaded.get()) {
				return;
			}
			CrafterJob job = ModAttachments.CRAFTER_JOB.get(carpenter.get());
			if (job != null && job.phase() == CrafterJob.Phase.DELIVERING) {
				helper.assertTrue(job.deliverTo().equals(helper.absolutePos(STOREHOUSE)), "the job goes to " + job.deliverTo() + ", not the Storehouse "
					+ helper.absolutePos(STOREHOUSE));
				Villager copy = reload(helper.getLevel(), carpenter.get());
				CrafterJob after = ModAttachments.CRAFTER_JOB.get(copy);
				helper.assertTrue(after != null && after.phase() == CrafterJob.Phase.DELIVERING && after.deliverTo().equals(job.deliverTo()),
					"after the reload the job is " + after);
				carpenter.set(copy);
				reloaded.set(true);
			}
		});
		helper.succeedWhen(() -> {
			helper.assertTrue(reloaded.get(), "the Carpenter never set off with the planks");
			helper.assertTrue(done(helper, run.a()), "A's row isn't finished: " + run.a().status() + " missing " + run.a().missing());
			helper.assertTrue(placed(helper, ROW_A) == 3, "A's row has " + placed(helper, ROW_A) + " of its 3 planks");
			helper.assertTrue(ModAttachments.CRAFTER_JOB.get(carpenter.get()) == null, "the delivered job is still saved");
		});
	}

	// ------------------------------------------------------------------------------------------------------ helpers

	record Run(ServerLevel level, long start, BuildSite a, BuildSite b, Villager carpenter) {
		long tick() {
			return level.getGameTime() - start;
		}
	}

	static Run setUp(GameTestHelper helper, String name, int logs, boolean aChest, boolean twoBuilders, boolean noStorehouse) {
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
		ResourceLocation rowA = blueprint(helper, "a_" + name, ROW_A);
		ResourceLocation rowB = twoBuilders ? blueprint(helper, "b_" + name, ROW_B) : null;
		if (noStorehouse) {
			helper.setBlock(CARPENTER_CHEST, Blocks.CHEST);
			Container chest = helper.getBlockEntity(CARPENTER_CHEST);
			chest.setItem(0, new ItemStack(Items.SPRUCE_LOG, 1));
		} else {
			helper.setBlock(STOREHOUSE, ModBlocks.STOREHOUSE);
			QaBuilderDisruptionGameTests.ensurePoi(level, helper.absolutePos(STOREHOUSE));
			helper.setBlock(STORE_CHEST, Blocks.CHEST);
			Container chest = helper.getBlockEntity(STORE_CHEST);
			chest.setItem(0, new ItemStack(Items.SPRUCE_LOG, logs));
		}
		if (aChest) {
			helper.setBlock(A_CHEST, Blocks.CHEST);
		}
		BuildSite siteA = start(helper, builder(helper, BENCH_A), rowA, ROW_A);
		BuildSite siteB = twoBuilders ? start(helper, builder(helper, BENCH_B), rowB, ROW_B) : null;
		helper.setBlock(STATION, ModBlocks.CARPENTERS_BENCH);
		Villager carpenter = helper.spawn(EntityType.VILLAGER, STATION.south());
		Jobs.employ(level, carpenter, helper.absolutePos(STATION), ModVillagers.CARPENTERS_BENCH_POI, ModVillagers.CARPENTER);
		return new Run(level, level.getGameTime(), siteA, siteB, carpenter);
	}

	static Villager builder(GameTestHelper helper, BlockPos bench) {
		helper.setBlock(bench, ModBlocks.BLUEPRINT_TABLE);
		QaBuilderDisruptionGameTests.ensurePoi(helper.getLevel(), helper.absolutePos(bench));
		Villager v = helper.spawn(EntityType.VILLAGER, bench.south());
		Builders.employ(helper.getLevel(), v, helper.absolutePos(bench));
		return v;
	}

	static BuildSite start(GameTestHelper helper, Villager builder, ResourceLocation id, BlockPos origin) {
		ServerLevel level = helper.getLevel();
		BuildSite site = Builders.start(level, builder, null, id, new BlueprintData.Placement(level.dimension().location(), helper.absolutePos(origin),
			Rotation.NONE, Mirror.NONE));
		if (site.plan(level) == null) {
			throw new GameTestAssertException("no blueprint " + id);
		}
		Leftovers.after(helper, () -> {
			if (BuildSiteManager.get(level).get(site.id()) != null) {
				BuildSiteManager.get(level).remove(site.id());
			}
		});
		return site;
	}

	/** A 3-long row of spruce planks drawn at {@code origin}, saved as a blueprint, and the spot cleared again. */
	static ResourceLocation blueprint(GameTestHelper helper, String name, BlockPos origin) {
		ServerLevel level = helper.getLevel();
		Vec3i size = new Vec3i(3, 1, 1);
		for (int x = 0; x < 3; x++) {
			helper.setBlock(origin.offset(x, 0, 0), Blocks.SPRUCE_PLANKS);
		}
		ResourceLocation id = ResourceLocation.fromNamespaceAndPath("aliveworkplace_test", "qa1101_" + name + "_" + Long.toHexString(level.getGameTime()));
		level.getStructureManager().getOrCreate(id).fillFromWorld(level, helper.absolutePos(origin), size, false, Blocks.STRUCTURE_VOID);
		for (int x = 0; x < 3; x++) {
			helper.setBlock(origin.offset(x, 0, 0), Blocks.AIR);
		}
		return id;
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

	static boolean done(GameTestHelper helper, BuildSite site) {
		return BuildSiteManager.get(helper.getLevel()).get(site.id()) == null;
	}

	/** Spruce wood in planks' worth (a log is 4) in the Storehouse, the bags, on the ground and placed in the rows. */
	static void conserve(GameTestHelper helper, Run run, int expected) {
		int wood = 4 * logsAnywhere(helper) + planksAnywhere(helper) + placed(helper, ROW_A) + (run.b() == null ? 0 : placed(helper, ROW_B));
		if (wood != expected) {
			throw new GameTestAssertException("at tick " + run.tick() + " the spruce wood adds up to " + wood + " planks' worth, not " + expected + " (logs: store "
				+ inChest(helper, STORE_CHEST, Items.SPRUCE_LOG) + ", bags " + inBags(helper, Items.SPRUCE_LOG) + ", ground " + onGround(helper, Items.SPRUCE_LOG)
				+ "; planks: store " + inChest(helper, STORE_CHEST, Items.SPRUCE_PLANKS) + ", A's chest " + inChest(helper, A_CHEST, Items.SPRUCE_PLANKS)
				+ ", bags " + inBags(helper, Items.SPRUCE_PLANKS) + ", ground " + onGround(helper, Items.SPRUCE_PLANKS) + ", placed " + placed(helper, ROW_A) + ")");
		}
	}

	/** The bug itself: after tick 600, spruce planks on the ground while {@code site} still waits for them. */
	static void noPlanksLyingWhileWaiting(GameTestHelper helper, Run run, BuildSite site) {
		int lying = onGround(helper, Items.SPRUCE_PLANKS);
		if (lying > 0 && run.tick() > 600 && !done(helper, site)) {
			throw new GameTestAssertException("at tick " + run.tick() + " " + lying + " spruce planks lie on the ground while a chestless builder still waits for "
				+ site.missing() + " (store " + inChest(helper, STORE_CHEST, Items.SPRUCE_PLANKS) + " planks)");
		}
	}

	static int logsAnywhere(GameTestHelper helper) {
		return inChest(helper, STORE_CHEST, Items.SPRUCE_LOG) + inBags(helper, Items.SPRUCE_LOG) + onGround(helper, Items.SPRUCE_LOG);
	}

	static int planksAnywhere(GameTestHelper helper) {
		return inChest(helper, STORE_CHEST, Items.SPRUCE_PLANKS) + inChest(helper, A_CHEST, Items.SPRUCE_PLANKS) + inBags(helper, Items.SPRUCE_PLANKS)
			+ onGround(helper, Items.SPRUCE_PLANKS);
	}

	static int inChest(GameTestHelper helper, BlockPos pos, Item item) {
		return helper.getLevel().getBlockEntity(helper.absolutePos(pos)) instanceof Container c ? c.countItem(item) : 0;
	}

	static int inBags(GameTestHelper helper, Item item) {
		int n = 0;
		for (Villager v : helper.getLevel().getEntitiesOfClass(Villager.class, helper.getBounds().inflate(8))) {
			n += ModAttachments.BUILDER_BAG.getOrCreate(v).count(item);
			n += v.getInventory().countItem(item);
		}
		return n;
	}

	static int onGround(GameTestHelper helper, Item item) {
		int n = 0;
		for (ItemEntity e : helper.getLevel().getEntitiesOfClass(ItemEntity.class, helper.getBounds().inflate(8), e -> e.getItem().is(item))) {
			n += e.getItem().getCount();
		}
		return n;
	}

	static int placed(GameTestHelper helper, BlockPos from) {
		int n = 0;
		for (int x = 0; x < 3; x++) {
			if (helper.getBlockState(from.offset(x, 0, 0)).is(Blocks.SPRUCE_PLANKS)) {
				n++;
			}
		}
		return n;
	}
}
