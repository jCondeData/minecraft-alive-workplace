package io.github.jcondedata.aliveworkplace.test;

import io.github.jcondedata.aliveworkplace.build.BuildSite;
import io.github.jcondedata.aliveworkplace.build.BuildSiteManager;
import io.github.jcondedata.aliveworkplace.build.BuilderBag;
import io.github.jcondedata.aliveworkplace.build.Builders;
import io.github.jcondedata.aliveworkplace.build.Paths;
import io.github.jcondedata.aliveworkplace.registry.ModAttachments;
import io.github.jcondedata.aliveworkplace.registry.ModBlocks;
import io.github.jcondedata.aliveworkplace.registry.ModVillagers;
import io.github.jcondedata.aliveworkplace.work.Jobs;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Container;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.village.poi.PoiManager;
import net.minecraft.world.entity.ai.village.poi.PoiTypes;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.npc.VillagerProfession;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * QA (lane-b-1107-1537), written from the spec of two landed fixes, not their code.
 * <p>
 * B982 (config-switches.md: {@code builderPaths} "Switched off, a path a builder had already planned is dropped: no more
 * of it is laid"; builder.md: "switched off, a builder also drops a path he had planned and lays no more of it"). Gaps
 * left by {@link QaBuilderPathsOffGameTests} and {@link PathLedgerGameTests}: off then on again (the dropped plan never
 * comes back), off across a save and reload of the builder mid-path, the switch on (control: a planned path is laid),
 * and the builder free for a new build after the plan is dropped (whose own path is then laid, the old one not).
 * <p>
 * B1009 (B353: a job that outlives its bench is let go and what he carries goes back to the chests or the ground,
 * whatever job he takes next; the request goes back to the builder). Gaps left by {@link QaCrafterNewJob1107BugTests}:
 * the wood counted exactly, the request then made by another Carpenter, a save and reload between losing the bench and
 * taking the new job, the bench put back (control) and an experienced Carpenter (keeps his profession). Wood is counted
 * in half-planks: a log is 8, a plank 2, a slab 1, a barrel 14 (6 planks and 2 slabs). Every test has its own batch.
 */
public class QaPathsCrafterVerify1107bGameTests implements net.fabricmc.fabric.api.gametest.v1.FabricGameTest {
	private static final String HUGE = "aliveworkplace_test:huge_area";
	private static final ResourceLocation TEST_HUT = ResourceLocation.fromNamespaceAndPath("aliveworkplace_test", "test_hut");
	private static final String AREA = QaCrafterFurnitureGameTests.AREA;
	private static final BlockPos BENCH = QaCrafterFurnitureGameTests.BENCH;
	private static final BlockPos CHEST = QaCrafterFurnitureGameTests.CHEST;
	private static final BlockPos STATION = QaCrafterFurnitureGameTests.STATION;
	private static final BlockPos ORIGIN = new BlockPos(6, 2, 9);
	private static final BlockPos LECTERN = new BlockPos(11, 2, 4);
	private static final BlockPos SECOND_STATION = new BlockPos(14, 2, 12);
	/** 6 Oak Logs, in half-planks. */
	private static final int WOOD = 6 * 8;

	// --- B982 ------------------------------------------------------------------------------------------------------

	/**
	 * builderPaths off while 10 blocks of path are planned, and once the builder has dropped the plan the operator
	 * switches it back on: for 900 ticks after, none of the old plan is laid and it doesn't come back (no new building
	 * finished, so nothing new is planned either).
	 */
	//$ gametest_ticks_batch HUGE '2000' '"qa1107bPathsOffOn"'
	@GameTest(template = HUGE, timeoutTicks = 2000, batch = "qa1107bPathsOffOn")
	public void qaPathsOffThenOnAgainDoesNotResumeTheDroppedPlan(GameTestHelper helper) {
		Villager builder = pathBuilder(helper, false);
		List<BlockPos> spots = row(helper, 6, 15, 14);
		ModAttachments.PATH.set(builder, absolute(helper, spots));
		AtomicInteger onAt = new AtomicInteger(-1);
		helper.onEachTick(() -> {
			if (onAt.get() < 0) {
				helper.assertTrue(helper.getTick() < 600, "the builder still keeps his plan 600 ticks after builderPaths was switched off: "
					+ ModAttachments.PATH.getOrElse(builder, List.of()).size() + " blocks");
				if (!ModAttachments.PATH.has(builder)) {
					helper.assertTrue(laid(helper, spots) == 0, laid(helper, spots) + " blocks laid while the switch was off");
					Paths.ENABLED = true; // the operator changes his mind
					onAt.set((int) helper.getTick());
				}
			}
		});
		helper.succeedWhen(() -> {
			helper.assertTrue(onAt.get() >= 0 && helper.getTick() >= onAt.get() + 900, "waiting");
			helper.assertTrue(laid(helper, spots) == 0, laid(helper, spots) + " of " + spots.size()
				+ " blocks of the dropped path laid after builderPaths was switched back on");
			helper.assertTrue(!ModAttachments.PATH.has(builder), "the dropped plan came back: "
				+ ModAttachments.PATH.getOrElse(builder, List.of()).size() + " blocks");
		});
	}

	/**
	 * The builder is laying a 24-block path (switch on); once he has laid some, the switch goes off and the server
	 * restarts (builder and build sites saved and loaded). After the reload not one more block is laid in 900 ticks and
	 * the reloaded builder has no plan.
	 */
	//$ gametest_ticks_batch HUGE '3000' '"qa1107bPathsOffReload"'
	@GameTest(template = HUGE, timeoutTicks = 3000, batch = "qa1107bPathsOffReload")
	public void qaPathsOffAcrossAReloadMidPathLaysNoMore(GameTestHelper helper) {
		Villager[] builder = {pathBuilder(helper, true)};
		List<BlockPos> spots = row(helper, 4, 27, 20);
		ModAttachments.PATH.set(builder[0], absolute(helper, spots));
		AtomicInteger offAt = new AtomicInteger(-1);
		AtomicInteger laidAtOff = new AtomicInteger(-1);
		helper.onEachTick(() -> {
			if (offAt.get() < 0 && laid(helper, spots) >= 1) {
				helper.assertTrue(laid(helper, spots) < spots.size(), "setup: the whole path was laid in one tick");
				Paths.ENABLED = false;
				restart(helper, builder);
				laidAtOff.set(laid(helper, spots));
				offAt.set((int) helper.getTick());
			}
		});
		helper.succeedWhen(() -> {
			helper.assertTrue(offAt.get() >= 0, "the builder never started laying the path (switch on)");
			helper.assertTrue(helper.getTick() >= offAt.get() + 900, "waiting");
			helper.assertTrue(laid(helper, spots) == laidAtOff.get(), (laid(helper, spots) - laidAtOff.get())
				+ " more blocks of path laid after builderPaths was switched off and the builder reloaded (" + laidAtOff.get() + " before)");
			helper.assertTrue(!ModAttachments.PATH.has(builder[0]), "the reloaded builder still keeps the path: "
				+ ModAttachments.PATH.getOrElse(builder[0], List.of()).size() + " blocks");
		});
	}

	/** Control: with builderPaths on, a planned 10-block path is laid in full and the plan is then done. */
	//$ gametest_ticks_batch HUGE '2000' '"qa1107bPathsOn"'
	@GameTest(template = HUGE, timeoutTicks = 2000, batch = "qa1107bPathsOn")
	public void qaPathsOnAPlannedPathIsLaid(GameTestHelper helper) {
		Villager builder = pathBuilder(helper, true);
		List<BlockPos> spots = row(helper, 6, 15, 14);
		ModAttachments.PATH.set(builder, absolute(helper, spots));
		helper.succeedWhen(() -> {
			helper.assertTrue(laid(helper, spots) == spots.size(), laid(helper, spots) + " of " + spots.size()
				+ " blocks of a planned path laid with builderPaths on");
			helper.assertTrue(!ModAttachments.PATH.has(builder), "the path is laid but the builder still keeps the plan");
		});
	}

	/**
	 * Off drops a planned path (a row at the far side of the area); the switch then goes back on and the builder is given
	 * a hut to build: he builds it, then lays the hut's own path to the bell, and the dropped row stays grass.
	 */
	//$ gametest_ticks_batch HUGE '5000' '"qa1107bPathsFree"'
	@GameTest(template = HUGE, timeoutTicks = 5000, batch = "qa1107bPathsFree")
	public void qaAfterTheDroppedPlanTheBuilderBuildsAndPavesANewHut(GameTestHelper helper) {
		Villager builder = pathBuilder(helper, false);
		ServerLevel level = helper.getLevel();
		clearBells(helper);
		helper.setBlock(new BlockPos(27, 2, 8), Blocks.BELL);
		List<BlockPos> old = row(helper, 4, 14, 27);
		ModAttachments.PATH.set(builder, absolute(helper, old));
		AtomicReference<BuildSite> site = new AtomicReference<>();
		AtomicInteger planned = new AtomicInteger();
		helper.onEachTick(() -> {
			if (site.get() == null) {
				helper.assertTrue(helper.getTick() < 600, "the builder still keeps his plan 600 ticks after builderPaths was switched off");
				if (!ModAttachments.PATH.has(builder)) {
					Paths.ENABLED = true;
					site.set(Builders.start(level, builder, null, TEST_HUT, QaBenchMovePathsPaceGameTests.placement(helper, new BlockPos(8, 2, 8))));
					helper.assertTrue(site.get() != null, "the builder couldn't take a new build after dropping his plan");
				}
			} else {
				planned.set(Math.max(planned.get(), ModAttachments.PATH.getOrElse(builder, List.of()).size()));
			}
		});
		helper.succeedWhen(() -> {
			helper.assertTrue(site.get() != null, "no new build started");
			helper.assertTrue(BuildSiteManager.get(level).get(site.get().id()) == null, "still building the new hut: " + site.get().status());
			helper.assertTrue(planned.get() > 0, "no path planned for the new hut");
			helper.assertTrue(!ModAttachments.PATH.has(builder), "still laying the new hut's path");
			helper.assertTrue(laid(helper, old) == 0, laid(helper, old) + " blocks of the dropped path laid");
			int paved = 0;
			for (int x = 0; x < 30; x++) {
				for (int z = 0; z < 30; z++) {
					paved += helper.getBlockState(new BlockPos(x, 1, z)).is(Blocks.DIRT_PATH) ? 1 : 0;
				}
			}
			helper.assertTrue(paved >= 2, "only " + paved + " blocks of the new hut's path laid");
		});
	}

	// --- B1009 -----------------------------------------------------------------------------------------------------

	/**
	 * The 0-XP Carpenter carrying the builder's 6 logs loses his bench and (a lectern nearby) takes another job: within
	 * 1,500 ticks he has let the job go, and all the wood is back in a chest or on the ground, not one log more or less
	 * (counted every tick: nothing is ever duplicated).
	 */
	//$ gametest_ticks_batch AREA '6000' '"qa1107bCarpenterWood"'
	@GameTest(template = AREA, timeoutTicks = 6000, batch = "qa1107bCarpenterWood")
	public void qaANewCarpenterInAnotherJobGivesBackExactlyTheWood(GameTestHelper helper) {
		QaCrafterFurnitureGameTests.prepare(helper);
		QaCrafterFurnitureGameTests.asker(helper, BENCH, CHEST, ORIGIN, Blocks.BARREL, new ItemStack(Items.OAK_LOG, 6));
		Villager carpenter = novice(helper);
		helper.setBlock(LECTERN, Blocks.LECTERN);
		AtomicInteger brokenAt = breakWhenCarrying(helper, () -> carpenter, false);
		takesTheLectern(helper, () -> carpenter, brokenAt);
		helper.succeedWhen(() -> {
			helper.assertTrue(brokenAt.get() >= 0, "the carpenter never carried anything");
			helper.assertTrue(helper.getTick() >= brokenAt.get() + 1500, "letting him react");
			helper.assertTrue(carpenter.getVillagerData().getProfession() == ModVillagers.SCHOLAR, "setup: he didn't become a Scholar: "
				+ carpenter.getVillagerData().getProfession());
			assertLetGo(helper, carpenter);
			helper.assertTrue(wood(helper, true) == WOOD, "the wood doesn't add up after he let the job go: " + wood(helper, true)
				+ " half-planks, " + WOOD + " expected (" + describe(helper) + ")");
		});
	}

	/**
	 * As above, and a second (experienced) Carpenter is hired once the first one's bench is broken: the request goes back
	 * to the builder and the second Carpenter makes the barrels, so the builder finishes (he isn't stuck).
	 */
	//$ gametest_ticks_batch AREA '9000' '"qa1107bCarpenterSecond"'
	@GameTest(template = AREA, timeoutTicks = 9000, batch = "qa1107bCarpenterSecond")
	public void qaTheRequestIsThenMadeByAnotherCarpenter(GameTestHelper helper) {
		QaCrafterFurnitureGameTests.prepare(helper);
		QaCrafterFurnitureGameTests.Asker a = QaCrafterFurnitureGameTests.asker(helper, BENCH, CHEST, ORIGIN, Blocks.BARREL, new ItemStack(Items.OAK_LOG, 6));
		Villager carpenter = novice(helper);
		helper.setBlock(LECTERN, Blocks.LECTERN);
		AtomicReference<Villager> second = new AtomicReference<>();
		AtomicInteger brokenAt = breakWhenCarrying(helper, () -> carpenter, false);
		helper.onEachTick(() -> {
			if (brokenAt.get() >= 0 && second.get() == null) {
				Villager two = QaCrafterFurnitureGameTests.carpenter(helper, SECOND_STATION);
				two.setVillagerXp(10);
				second.set(two);
			}
		});
		helper.succeedWhen(() -> {
			helper.assertTrue(second.get() != null, "the first carpenter never carried anything");
			helper.assertTrue(ModAttachments.CRAFTER_JOB.get(carpenter) == null, "the first carpenter (now "
				+ carpenter.getVillagerData().getProfession() + ") still holds the crafting job");
			QaCrafterFurnitureGameTests.assertBuilt(helper, a);
		});
	}

	/**
	 * The bench is broken while the 0-XP Carpenter carries the logs and he is saved and reloaded before a lectern is put
	 * down for him to take: whatever job he takes, the job is let go and the wood adds up exactly.
	 */
	//$ gametest_ticks_batch AREA '6000' '"qa1107bCarpenterReload"'
	@GameTest(template = AREA, timeoutTicks = 6000, batch = "qa1107bCarpenterReload")
	public void qaAReloadBetweenLosingTheBenchAndTheNewJobStillLetsGo(GameTestHelper helper) {
		QaCrafterFurnitureGameTests.prepare(helper);
		QaCrafterFurnitureGameTests.asker(helper, BENCH, CHEST, ORIGIN, Blocks.BARREL, new ItemStack(Items.OAK_LOG, 6));
		Villager[] carpenter = {novice(helper)};
		AtomicInteger brokenAt = breakWhenCarrying(helper, () -> carpenter[0], false);
		AtomicInteger reloadedAt = new AtomicInteger(-1);
		takesTheLectern(helper, () -> carpenter[0], reloadedAt);
		helper.onEachTick(() -> {
			if (brokenAt.get() >= 0 && reloadedAt.get() < 0 && helper.getTick() >= brokenAt.get() + 5) {
				BuilderBag bag = ModAttachments.BUILDER_BAG.get(carpenter[0]);
				helper.assertTrue(bag != null && !bag.isEmpty(), "setup: the bag emptied before the reload");
				carpenter[0] = QaCrafterFurnitureGameTests.reload(helper.getLevel(), carpenter[0]);
				helper.setBlock(LECTERN, Blocks.LECTERN); // only now a new job to take
				reloadedAt.set((int) helper.getTick());
			}
		});
		helper.succeedWhen(() -> {
			helper.assertTrue(reloadedAt.get() >= 0, "the carpenter never carried anything");
			helper.assertTrue(helper.getTick() >= reloadedAt.get() + 1500, "letting him react");
			helper.assertTrue(carpenter[0].getVillagerData().getProfession() == ModVillagers.SCHOLAR, "setup: he didn't become a Scholar: "
				+ carpenter[0].getVillagerData().getProfession());
			assertLetGo(helper, carpenter[0]);
			helper.assertTrue(wood(helper, true) == WOOD, "the wood doesn't add up after the reload: " + wood(helper, true)
				+ " half-planks, " + WOOD + " expected (" + describe(helper) + ")");
		});
	}

	/**
	 * Control: the 0-XP Carpenter's bench is broken while he carries the logs and put back 60 ticks later (no lectern
	 * around): he is a Carpenter again and the builder gets his barrels.
	 */
	//$ gametest_ticks_batch AREA '6000' '"qa1107bCarpenterBenchBack"'
	@GameTest(template = AREA, timeoutTicks = 6000, batch = "qa1107bCarpenterBenchBack")
	public void qaABenchPutBackKeepsTheNoviceCarpenterOnTheRequest(GameTestHelper helper) {
		QaCrafterFurnitureGameTests.prepare(helper);
		QaCrafterFurnitureGameTests.Asker a = QaCrafterFurnitureGameTests.asker(helper, BENCH, CHEST, ORIGIN, Blocks.BARREL, new ItemStack(Items.OAK_LOG, 6));
		Villager carpenter = novice(helper);
		AtomicInteger brokenAt = breakWhenCarrying(helper, () -> carpenter, true);
		helper.succeedWhen(() -> {
			helper.assertTrue(brokenAt.get() >= 0, "the carpenter never carried anything");
			helper.assertTrue(carpenter.getVillagerData().getProfession() == ModVillagers.CARPENTER, "not a Carpenter again with his bench put back: "
				+ carpenter.getVillagerData().getProfession());
			QaCrafterFurnitureGameTests.assertBuilt(helper, a);
		});
	}

	/**
	 * An experienced Carpenter (10 XP: vanilla keeps his profession) loses his bench while carrying the logs, a free
	 * lectern nearby: he stays a Carpenter, keeps the job through the grace period's first 150 ticks, and once the bench
	 * is put back (at 200 ticks) finishes the request.
	 */
	//$ gametest_ticks_batch AREA '6000' '"qa1107bCarpenterExperienced"'
	@GameTest(template = AREA, timeoutTicks = 6000, batch = "qa1107bCarpenterExperienced")
	public void qaAnExperiencedCarpenterKeepsHisJobThroughALostBench(GameTestHelper helper) {
		QaCrafterFurnitureGameTests.prepare(helper);
		QaCrafterFurnitureGameTests.Asker a = QaCrafterFurnitureGameTests.asker(helper, BENCH, CHEST, ORIGIN, Blocks.BARREL, new ItemStack(Items.OAK_LOG, 6));
		Villager carpenter = QaCrafterFurnitureGameTests.carpenter(helper, STATION);
		carpenter.setVillagerXp(10);
		helper.setBlock(LECTERN, Blocks.LECTERN);
		AtomicInteger brokenAt = new AtomicInteger(-1);
		helper.onEachTick(() -> {
			BuilderBag bag = ModAttachments.BUILDER_BAG.get(carpenter);
			if (brokenAt.get() < 0 && bag != null && !bag.isEmpty()) {
				helper.setBlock(STATION, Blocks.AIR);
				brokenAt.set((int) helper.getTick());
			}
			if (brokenAt.get() >= 0) {
				helper.assertTrue(carpenter.getVillagerData().getProfession() == ModVillagers.CARPENTER, "the experienced carpenter became "
					+ carpenter.getVillagerData().getProfession());
				long since = helper.getTick() - brokenAt.get();
				if (since == 150) {
					helper.assertTrue(ModAttachments.CRAFTER_JOB.get(carpenter) != null, "the experienced carpenter let the job go 150 ticks"
						+ " after losing his bench, inside the 600-tick grace");
				}
				if (since == 200) {
					helper.setBlock(STATION, ModBlocks.CARPENTERS_BENCH);
				}
			}
		});
		helper.succeedWhen(() -> {
			helper.assertTrue(brokenAt.get() >= 0 && helper.getTick() >= brokenAt.get() + 200, "waiting");
			QaCrafterFurnitureGameTests.assertBuilt(helper, a);
		});
	}

	// --- helpers ---------------------------------------------------------------------------------------------------

	private static Villager pathBuilder(GameTestHelper helper, boolean paths) {
		QaBenchMovePathsPaceGameTests.prepare(helper, 2);
		boolean was = Paths.ENABLED;
		Leftovers.after(helper, () -> Paths.ENABLED = was);
		Paths.ENABLED = paths;
		for (int x = 0; x < 30; x++) {
			for (int z = 0; z < 30; z++) {
				helper.setBlock(new BlockPos(x, 1, z), Blocks.GRASS_BLOCK);
			}
		}
		return QaBenchMovePathsPaceGameTests.bench(helper, new BlockPos(2, 2, 14), QaBenchMovePathsPaceGameTests.hutMaterials());
	}

	private static List<BlockPos> row(GameTestHelper helper, int fromX, int toX, int z) {
		List<BlockPos> out = new ArrayList<>();
		for (int x = fromX; x <= toX; x++) {
			out.add(new BlockPos(x, 1, z));
		}
		return out;
	}

	private static List<BlockPos> absolute(GameTestHelper helper, List<BlockPos> rel) {
		return rel.stream().map(helper::absolutePos).map(BlockPos::immutable).toList();
	}

	private static int laid(GameTestHelper helper, List<BlockPos> rel) {
		int n = 0;
		for (BlockPos p : rel) {
			n += helper.getBlockState(p).is(Blocks.DIRT_PATH) ? 1 : 0;
		}
		return n;
	}

	/** A server restart for the builder: build sites and the builder saved and loaded (as a fresh entity). */
	private static void restart(GameTestHelper helper, Villager[] builder) {
		ServerLevel level = helper.getLevel();
		var registries = level.registryAccess();
		level.getDataStorage().set(BuildSiteManager.NAME, BuildSiteManager.load(BuildSiteManager.get(level).save(new CompoundTag(), registries), registries));
		CompoundTag tag = new CompoundTag();
		helper.assertTrue(builder[0].save(tag), "the builder wasn't saved");
		builder[0].discard();
		Entity loaded = EntityType.loadEntityRecursive(tag, level, e -> e);
		helper.assertTrue(loaded instanceof Villager, "the builder didn't load as a villager: " + loaded);
		level.addFreshEntity(loaded);
		builder[0] = (Villager) loaded;
	}

	/** Bells another batch left beside this area (never cleared) would be nearer than this test's own (PathLedgerGameTests's). */
	private static void clearBells(GameTestHelper helper) {
		AABB box = helper.getBounds();
		for (BlockPos bell : helper.getLevel().getPoiManager().findAll(h -> h.is(PoiTypes.MEETING), p -> !box.contains(Vec3.atCenterOf(p)),
			BlockPos.containing(box.getCenter()), 96, PoiManager.Occupancy.ANY).toList()) {
			helper.getLevel().removeBlock(bell, false);
		}
	}

	/** A brand-new Carpenter (never traded: vanilla takes his profession away with the bench). */
	private static Villager novice(GameTestHelper helper) {
		Villager carpenter = QaCrafterFurnitureGameTests.carpenter(helper, STATION);
		carpenter.setVillagerXp(0);
		return carpenter;
	}

	/**
	 * Breaks the bench the first tick {@code who} carries something (putting it back 60 ticks later when {@code putBack}),
	 * and from then on checks every tick that the wood never adds up to more than the 6 logs (nothing duplicated).
	 */
	private static AtomicInteger breakWhenCarrying(GameTestHelper helper, java.util.function.Supplier<Villager> who, boolean putBack) {
		AtomicInteger brokenAt = new AtomicInteger(-1);
		AtomicInteger lastShort = new AtomicInteger(-1);
		helper.onEachTick(() -> {
			Villager carpenter = who.get();
			BuilderBag bag = ModAttachments.BUILDER_BAG.get(carpenter);
			if (brokenAt.get() < 0 && bag != null && !bag.isEmpty()) {
				helper.setBlock(STATION, Blocks.AIR); // the player breaks the bench while he carries the wood
				brokenAt.set((int) helper.getTick());
			}
			if (brokenAt.get() >= 0) {
				int w = wood(helper, true);
				helper.assertTrue(w <= WOOD, "wood duplicated: " + w + " half-planks, " + WOOD + " to start with (" + describe(helper) + ")");
				if (w < WOOD && lastShort.get() != w) {
					lastShort.set(w);
					System.out.println("[qa1107b] tick " + (helper.getTick() - brokenAt.get()) + " after the break: " + w + " half-planks: " + describe(helper));
				}
				if (putBack && helper.getTick() == brokenAt.get() + 60) {
					helper.setBlock(STATION, ModBlocks.CARPENTERS_BENCH);
				}
			}
		});
		return brokenAt;
	}

	/**
	 * 20 ticks after {@code since} (well inside B353's 600-tick grace) the ex-Carpenter takes the free lectern as a
	 * Scholar (the job B1009's report saw), as a novice who lost his bench does in play (vanilla), only without waiting on his brain's timing: in
	 * play it can take long enough for the grace to run out first, which hides the case.
	 */
	private static void takesTheLectern(GameTestHelper helper, java.util.function.Supplier<Villager> who, AtomicInteger since) {
		helper.onEachTick(() -> {
			Villager v = who.get();
			if (since.get() >= 0 && helper.getTick() >= since.get() + 20 && v.getVillagerData().getProfession() != ModVillagers.SCHOLAR
				&& helper.getBlockState(LECTERN).is(Blocks.LECTERN)) {
				Jobs.employ(helper.getLevel(), v, helper.absolutePos(LECTERN), PoiTypes.LIBRARIAN, ModVillagers.SCHOLAR);
				v.setVillagerData(v.getVillagerData().setProfession(ModVillagers.SCHOLAR));
			}
		});
	}

	private static void assertLetGo(GameTestHelper helper, Villager carpenter) {
		BuilderBag bag = ModAttachments.BUILDER_BAG.get(carpenter);
		boolean job = ModAttachments.CRAFTER_JOB.get(carpenter) != null;
		VillagerProfession now = carpenter.getVillagerData().getProfession();
		helper.assertTrue(!job && (bag == null || bag.isEmpty()), "a 0-XP carpenter lost his bench while carrying wood and is now " + now
			+ ": crafting job " + (job ? "still held" : "let go") + ", bag " + (bag == null ? "none" : bag.stacks()));
	}

	private static int units(ItemStack s) {
		int each = s.is(Items.OAK_LOG) ? 8 : s.is(Items.OAK_PLANKS) ? 2 : s.is(Items.OAK_SLAB) ? 1 : s.is(Items.BARREL) ? 14 : 0;
		return each * s.getCount();
	}

	/**
	 * Every bit of oak wood in and around the area, in half-planks: containers, items on the ground, villagers' bags and
	 * inventories, and barrels placed ({@code placed}; the barrel blocks a builder put down).
	 */
	private static int wood(GameTestHelper helper, boolean placed) {
		ServerLevel level = helper.getLevel();
		AABB box = helper.getBounds().inflate(8);
		int n = 0;
		for (BlockPos p : BlockPos.betweenClosed(BlockPos.containing(box.minX, box.minY, box.minZ), BlockPos.containing(box.maxX - 1, box.maxY - 1, box.maxZ - 1))) {
			BlockEntity be = level.getBlockEntity(p);
			if (be instanceof Container c) {
				for (int i = 0; i < c.getContainerSize(); i++) {
					n += units(c.getItem(i));
				}
			}
			if (placed && level.getBlockState(p).is(Blocks.BARREL)) {
				n += 14;
			}
		}
		for (ItemEntity e : level.getEntitiesOfClass(ItemEntity.class, box)) {
			n += units(e.getItem());
		}
		for (Villager v : level.getEntitiesOfClass(Villager.class, box)) {
			BuilderBag bag = ModAttachments.BUILDER_BAG.get(v);
			if (bag != null) {
				for (ItemStack s : bag.stacks()) {
					n += units(s);
				}
			}
			for (int i = 0; i < v.getInventory().getContainerSize(); i++) {
				n += units(v.getInventory().getItem(i));
			}
		}
		return n;
	}

	/** Where the wood is: every container, loose item, bag and inventory holding some, and the barrels placed. */
	private static String describe(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		AABB box = helper.getBounds().inflate(8);
		StringBuilder out = new StringBuilder();
		for (BlockPos p : BlockPos.betweenClosed(BlockPos.containing(box.minX, box.minY, box.minZ), BlockPos.containing(box.maxX - 1, box.maxY - 1, box.maxZ - 1))) {
			BlockEntity be = level.getBlockEntity(p);
			if (be instanceof Container c) {
				for (int i = 0; i < c.getContainerSize(); i++) {
					if (units(c.getItem(i)) > 0) {
						out.append(c.getItem(i)).append(" in ").append(helper.relativePos(p)).append("; ");
					}
				}
			}
			if (level.getBlockState(p).is(Blocks.BARREL)) {
				out.append("barrel placed at ").append(helper.relativePos(p)).append("; ");
			}
		}
		for (ItemEntity e : level.getEntitiesOfClass(ItemEntity.class, box)) {
			if (units(e.getItem()) > 0) {
				out.append(e.getItem()).append(" on the ground at ").append(helper.relativePos(e.blockPosition())).append("; ");
			}
		}
		for (Villager v : level.getEntitiesOfClass(Villager.class, box)) {
			BuilderBag bag = ModAttachments.BUILDER_BAG.get(v);
			out.append(v.getVillagerData().getProfession()).append(" bag ").append(bag == null ? "none" : bag.stacks()).append(" inv ")
				.append(v.getInventory().getItems()).append("; ");
		}
		return out.toString();
	}
}
