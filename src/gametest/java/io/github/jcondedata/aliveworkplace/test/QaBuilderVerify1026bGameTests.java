package io.github.jcondedata.aliveworkplace.test;

import io.github.jcondedata.aliveworkplace.blueprint.BlueprintData;
import io.github.jcondedata.aliveworkplace.build.BuildPlan;
import io.github.jcondedata.aliveworkplace.build.BuildSite;
import io.github.jcondedata.aliveworkplace.build.BuildSiteManager;
import io.github.jcondedata.aliveworkplace.build.BuilderLevels;
import io.github.jcondedata.aliveworkplace.build.Builders;
import io.github.jcondedata.aliveworkplace.build.MaterialRules;
import io.github.jcondedata.aliveworkplace.build.Upkeep;
import io.github.jcondedata.aliveworkplace.registry.ModAttachments;
import io.github.jcondedata.aliveworkplace.registry.ModBlocks;
import io.github.jcondedata.aliveworkplace.registry.ModGameRules;
import io.github.jcondedata.aliveworkplace.work.Pace;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Container;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.WallTorchBlock;

/**
 * QA (lane-b-1026-1248), from the spec, of builder fixes that had landed but were not yet checked by a second agent:
 * <ul>
 * <li>B508 (wiki builder.md "Unfinished blocks are never claimed done"): "Blocks already given up (deferred, then
 * skipped) are not gone back for", and the last look before DONE survives a restart while the build is going back.
 * The first test also kills a mutant of BuildSite#defer that survived the nightly mutation run (decoration blocks
 * given up no longer remembered as left out).</li>
 * <li>B515 (wiki "Speed": exactly {@code workplaceBuildDelay} ticks per block times 100/100/85/70/55/40% by level):
 * the shares of the levels the existing tests don't measure (Apprentice, Journeyman, Expert) and the smallest delays,
 * where the old extra tick doubled the time per block.</li>
 * <li>B519 (wiki "Materials": the bag holds "never more of a thing than the stretch of work ahead still needs"; "a
 * helper at the chests puts back what its stretch no longer needs, such as the walls' last planks once the crew is on
 * the lanterns"): checked item by item, for a full crew of three helpers.</li>
 * </ul>
 * Upkeep (the later repair round) is off in the B508 tests, so only the build itself can put a block back.
 */
public class QaBuilderVerify1026bGameTests implements net.fabricmc.fabric.api.gametest.v1.FabricGameTest {
	private static final String AREA = "aliveworkplace_test:build_area";
	private static final String HUGE = "aliveworkplace_test:huge_area";
	private static final org.slf4j.Logger LOGGER = org.slf4j.LoggerFactory.getLogger("aliveworkplace_test");
	private static final ResourceLocation TEST_HUT = ResourceLocation.fromNamespaceAndPath("aliveworkplace_test", "test_hut");

	private static void upkeepOff(GameTestHelper helper) {
		boolean upkeep = Upkeep.ENABLED;
		Upkeep.ENABLED = false;
		Leftovers.after(helper, () -> Upkeep.ENABLED = upkeep);
	}

	private static boolean late(BuildSite site) {
		return site.stage() == BuildPlan.Stage.DECORATION || site.stage() == BuildPlan.Stage.LANDSCAPE;
	}

	/** A wall plank standing as drawn with no decoration drawn on or beside it (breaking it pops nothing), or null. */
	private static BlockPos aBarePlank(QaBuilderDisruptionGameTests.Run r) {
		for (BuildPlan.Step s : QaBuilderDisruptionGameTests.placed(r)) {
			if (s.state().is(Blocks.OAK_PLANKS) && r.plan().steps(BuildPlan.Stage.DECORATION).stream()
				.noneMatch(d -> !d.state().isAir() && d.pos().distManhattan(s.pos()) <= 1)) {
				return s.pos().immutable();
			}
		}
		return null;
	}

	/** As a server restart would: every build site from its save, and {@code old} saved and loaded as a fresh entity. */
	private static Villager restart(GameTestHelper helper, ServerLevel level, Villager old) {
		var registries = level.registryAccess();
		level.getDataStorage().set(BuildSiteManager.NAME, BuildSiteManager.load(BuildSiteManager.get(level).save(new CompoundTag(), registries), registries));
		CompoundTag tag = new CompoundTag();
		helper.assertTrue(old.save(tag), "the builder wasn't saved");
		old.discard();
		Entity back = EntityType.loadEntityRecursive(tag, level, e -> e);
		helper.assertTrue(back instanceof Villager, "the builder didn't load as a villager: " + back);
		level.addFreshEntity(back);
		return (Villager) back;
	}

	// ------------------------------------------------------------------------------------------------------- B508

	/**
	 * In the decoration, before the hut's wall torch goes up, a player breaks the wall plank it hangs on and keeps it.
	 * The torch has nothing to hang on: it is deferred, then given up (skipped). At the end the build's last look goes
	 * back for the broken plank and lays it (B508). Wiki: "Blocks already given up (deferred, then skipped) are not gone
	 * back for": the torch stays given up (its spot empty, still counted in skipped, left to the repair round) and the
	 * build went back exactly once, for the plank. Kills the BuildSite#defer mutant where a decoration block given up is
	 * not remembered as left out (the build then went back for the torch too and hung it).
	 */
	//$ gametest_ticks_batch AREA '6000' '"qaV1026bTorchLeft"'
	@GameTest(template = AREA, timeoutTicks = 6000, batch = "qaV1026bTorchLeft")
	public void aTorchGivenUpWhileItsWallWasGoneIsNotGoneBackFor(GameTestHelper helper) {
		QaBuilderDisruptionGameTests.Run r = QaBuilderDisruptionGameTests.start(helper, ModBlocks.BUILDERS_BENCH);
		upkeepOff(helper);
		BuildPlan.Step torch = r.plan().steps(BuildPlan.Stage.DECORATION).stream()
			.filter(s -> s.state().getBlock() instanceof WallTorchBlock).findFirst().orElse(null);
		if (torch == null) {
			throw new GameTestAssertException("setup: the test hut has no wall torch in its decoration");
		}
		BlockPos wall = torch.pos().relative(torch.state().getValue(WallTorchBlock.FACING).getOpposite()).immutable();
		helper.assertTrue(r.plan().steps(BuildPlan.Stage.STRUCTURE).stream().anyMatch(s -> s.pos().equals(wall) && s.state().is(Blocks.OAK_PLANKS)),
			"setup: the torch doesn't hang on a wall plank of the structure");
		boolean[] broke = {false};
		helper.onEachTick(() -> {
			if (broke[0] || r.site().stage() != BuildPlan.Stage.DECORATION) {
				return;
			}
			helper.assertTrue(r.level().getBlockState(torch.pos()).isAir(), "setup: the torch was up before the test saw the decoration start");
			helper.assertTrue(r.level().getBlockState(wall).is(Blocks.OAK_PLANKS), "setup: the torch's wall plank isn't up at the decoration");
			r.level().destroyBlock(wall, false); // the player mined it and keeps the plank
			broke[0] = true;
			LOGGER.info("qa v1026b torch: wall plank at {} broken at the start of the decoration, {}", helper.relativePos(wall),
				QaBuilderDisruptionGameTests.state(helper, r));
		});
		helper.succeedWhen(() -> {
			helper.assertTrue(broke[0], "setup: the build never reached its decoration: " + QaBuilderDisruptionGameTests.state(helper, r));
			helper.assertFalse(QaBuilderDisruptionGameTests.building(r), "still building: " + QaBuilderDisruptionGameTests.state(helper, r));
			helper.assertTrue(r.level().getBlockState(wall).is(Blocks.OAK_PLANKS), "the broken wall plank at " + helper.relativePos(wall)
				+ " wasn't put back before DONE (B508): " + r.level().getBlockState(wall));
			helper.assertTrue(r.level().getBlockState(torch.pos()).isAir(), "the torch given up while its wall was gone was gone back for and hung "
				+ "(wiki: blocks already given up are not gone back for): " + r.level().getBlockState(torch.pos()) + ", rechecks " + r.site().rechecks());
			helper.assertTrue(r.site().rechecks() == 1, "the build went back " + r.site().rechecks() + " times; once, for the plank, was wanted");
			helper.assertTrue(r.site().skipped() >= 1, "the given-up torch isn't counted in skipped: " + r.site().skipped());
			LOGGER.info("qa v1026b torch: done, plank back, torch left, skipped {}, rechecks {}", r.site().skipped(), r.site().rechecks());
		});
	}

	/**
	 * Late in the build a player breaks a bare wall plank; the build's last look goes back for it, and at that moment the
	 * server restarts (the build sites and the builder saved and loaded). After the restart the build still lays the
	 * plank and finishes: the whole hut as drawn, nothing skipped, it went back once (not again from the start).
	 */
	//$ gametest_ticks_batch AREA '6000' '"qaV1026bRecheckRestart"'
	@GameTest(template = AREA, timeoutTicks = 6000, batch = "qaV1026bRecheckRestart")
	public void aRestartWhileGoingBackForABrokenPlankStillPutsItBack(GameTestHelper helper) {
		QaBuilderDisruptionGameTests.Run r = QaBuilderDisruptionGameTests.start(helper, ModBlocks.BUILDERS_BENCH);
		upkeepOff(helper);
		UUID id = r.site().id();
		BlockPos[] broken = new BlockPos[1];
		Villager[] builder = {r.builder()};
		BuildSite[] site = {r.site()};
		boolean[] restarted = {false};
		helper.onEachTick(() -> {
			helper.setDayTime(2000);
			if (broken[0] == null) {
				if (late(site[0])) {
					broken[0] = aBarePlank(r);
					if (broken[0] != null) {
						r.level().destroyBlock(broken[0], false);
					}
				}
				return;
			}
			if (!restarted[0] && site[0].rechecks() > 0) {
				builder[0] = restart(helper, r.level(), builder[0]);
				site[0] = BuildSiteManager.get(r.level()).get(id);
				helper.assertTrue(site[0] != null, "the build site was lost in the restart");
				restarted[0] = true;
				LOGGER.info("qa v1026b restart: restarted going back ({}, rechecks {})", site[0].stage(), site[0].rechecks());
			}
		});
		helper.succeedWhen(() -> {
			helper.assertTrue(broken[0] != null, "setup: the build never reached decoration or landscaping with a plank standing");
			helper.assertTrue(restarted[0], "the build never went back for the plank broken late: stage " + site[0].stage());
			helper.assertTrue(BuildSiteManager.get(r.level()).get(id) == null, "still building after the restart: stage " + site[0].stage()
				+ " status " + site[0].status() + " missing " + site[0].missing());
			helper.assertTrue(builder[0].isAlive(), "the builder died");
			helper.assertTrue(r.level().getBlockState(broken[0]).is(Blocks.OAK_PLANKS), "after a restart while going back the plank at "
				+ helper.relativePos(broken[0]) + " is still gone: " + r.level().getBlockState(broken[0]));
			List<BlockPos> wrong = r.plan().unfinished(r.level());
			helper.assertTrue(wrong.isEmpty(), wrong.size() + " block(s) of the hut not as drawn, e.g. " + wrong.stream().limit(4).map(helper::relativePos).toList());
			helper.assertTrue(site[0].skipped() == 0, site[0].skipped() + " step(s) counted skipped");
			helper.assertTrue(site[0].rechecks() == 1, "the build went back " + site[0].rechecks() + " times; once was wanted");
		});
	}

	// ------------------------------------------------------------------------------------------------------- B515

	/**
	 * Two builders side by side, each on its own hut, levels {@code levelA} and {@code levelB}, at a block delay of
	 * {@code delay}: the typical (median) wait between two of each one's blocks is exactly {@code wantA} and {@code wantB}.
	 */
	private static void pace(GameTestHelper helper, int delay, int levelA, int levelB, int wantA, int wantB, String tag) {
		ServerLevel level = QaBenchMovePathsPaceGameTests.prepare(helper, delay);
		QaBenchMovePathsPaceGameTests.traitsOff(helper);
		Villager a = QaBenchMovePathsPaceGameTests.bench(helper, new BlockPos(2, 2, 2), QaBenchMovePathsPaceGameTests.hutMaterials());
		Villager b = QaBenchMovePathsPaceGameTests.bench(helper, new BlockPos(26, 2, 2), QaBenchMovePathsPaceGameTests.hutMaterials());
		a.setVillagerData(a.getVillagerData().setLevel(levelA));
		b.setVillagerData(b.getVillagerData().setLevel(levelB));
		helper.assertTrue(Pace.factor(a) == 1f && Pace.factor(b) == 1f, "setup: pace " + Pace.of(a) + " / " + Pace.of(b));
		helper.assertTrue(BuilderLevels.delay(level, a) == wantA && BuilderLevels.delay(level, b) == wantB, "setup: delays "
			+ BuilderLevels.delay(level, a) + " / " + BuilderLevels.delay(level, b) + ", wanted " + wantA + " / " + wantB);
		BuildSite siteA = Builders.start(level, a, null, TEST_HUT, QaBenchMovePathsPaceGameTests.placement(helper, new BlockPos(4, 2, 12)));
		BuildSite siteB = Builders.start(level, b, null, TEST_HUT, QaBenchMovePathsPaceGameTests.placement(helper, new BlockPos(20, 2, 12)));
		BuildPlan planA = siteA.plan(level);
		BuildPlan planB = siteB.plan(level);
		if (planA == null || planB == null) {
			throw new GameTestAssertException("no test hut blueprint");
		}
		List<Long> timesA = new ArrayList<>();
		List<Long> timesB = new ArrayList<>();
		int[] left = {planA.unfinished(level).size(), planB.unfinished(level).size()};
		helper.onEachTick(() -> {
			long now = level.getGameTime();
			int n = planA.unfinished(level).size();
			int m = planB.unfinished(level).size();
			for (int i = n; i < left[0]; i++) {
				timesA.add(now);
			}
			for (int i = m; i < left[1]; i++) {
				timesB.add(now);
			}
			left[0] = n;
			left[1] = m;
		});
		helper.succeedWhen(() -> {
			helper.assertTrue(timesA.size() >= 20 && timesB.size() >= 20, "placed " + timesA.size() + " / " + timesB.size());
			int gapA = QaBenchMovePathsPaceGameTests.median(timesA);
			int gapB = QaBenchMovePathsPaceGameTests.median(timesB);
			LOGGER.info("[{}] delay {}: level {} waits {} ticks a block (wanted {}), level {} waits {} (wanted {})", tag, delay, levelA, gapA, wantA,
				levelB, gapB, wantB);
			helper.assertTrue(gapA == wantA, "at a delay of " + delay + " a level " + levelA + " builder waits " + gapA + " ticks between blocks, not "
				+ wantA + " (wiki: the delay times " + levelA + "'s share)");
			helper.assertTrue(gapB == wantB, "at a delay of " + delay + " a level " + levelB + " builder waits " + gapB + " ticks between blocks, not "
				+ wantB + " (wiki: the delay times " + levelB + "'s share)");
		});
	}

	/**
	 * Wiki "Speed" (shares 100% for the unused index 0, then 100, 85, 70, 55, 40 for levels 1 to 5): at a delay of 20 an
	 * Apprentice (85%) takes 17 ticks a block and an Expert (55%) 11, not 18 and 12.
	 */
	//$ gametest_ticks_batch HUGE '3000' '"qaV1026bPaceMid"'
	@GameTest(template = HUGE, timeoutTicks = 3000, batch = "qaV1026bPaceMid")
	public void anApprenticeAndAnExpertTakeTheirShareOfTheDelay(GameTestHelper helper) {
		pace(helper, 20, 2, 4, 17, 11, "qa-v1026b-pace-mid");
	}

	/** At a delay of 2 an Apprentice (85%, 1.7 rounded) takes 2 ticks a block and a Master (40%, 0.8 rounded) 1: not 3 and 2. */
	//$ gametest_ticks_batch HUGE '2000' '"qaV1026bPaceTwo"'
	@GameTest(template = HUGE, timeoutTicks = 2000, batch = "qaV1026bPaceTwo")
	public void atADelayOfTwoAnApprenticeTakesTwoTicksAndAMasterOne(GameTestHelper helper) {
		pace(helper, 2, 2, 5, 2, 1, "qa-v1026b-pace-two");
	}

	/** The rule's smallest delay, 1: a Novice and a Journeyman (70%, 0.7 rounded) each lay a block every tick, not every other one. */
	//$ gametest_ticks_batch HUGE '2000' '"qaV1026bPaceOne"'
	@GameTest(template = HUGE, timeoutTicks = 2000, batch = "qaV1026bPaceOne")
	public void atTheSmallestDelayABlockGoesInEveryTick(GameTestHelper helper) {
		pace(helper, 1, 1, 3, 1, 1, "qa-v1026b-pace-one");
	}

	// ------------------------------------------------------------------------------------------------------- B519

	private static final BlockPos TABLE = new BlockPos(2, 2, 2);
	private static final BlockPos CHEST = new BlockPos(2, 2, 4);
	private static final BlockPos LEAD = new BlockPos(3, 2, 3);
	private static final BlockPos HUT = new BlockPos(6, 2, 6);
	private static final BlockPos[] MATE_TABLES = {new BlockPos(13, 2, 2), new BlockPos(15, 2, 2), new BlockPos(2, 2, 13)};

	private static Map<Item, Integer> bag(Villager villager) {
		Map<Item, Integer> out = new LinkedHashMap<>();
		for (ItemStack stack : ModAttachments.BUILDER_BAG.getOrCreate(villager).stacks()) {
			if (!stack.isEmpty()) {
				out.merge(stack.getItem(), stack.getCount(), Integer::sum);
			}
		}
		return out;
	}

	/** What the build still needs, by item: the requirements of every foundation, structure and decoration step not yet as drawn. */
	private static Map<Item, Integer> stillNeeded(ServerLevel level, BuildPlan plan) {
		Map<Item, Integer> out = new LinkedHashMap<>();
		for (BuildPlan.Stage stage : List.of(BuildPlan.Stage.FOUNDATION, BuildPlan.Stage.STRUCTURE, BuildPlan.Stage.DECORATION)) {
			for (BuildPlan.Step s : plan.steps(stage)) {
				if (!MaterialRules.matches(level.getBlockState(s.pos()), s.state())) {
					for (MaterialRules.Requirement req : s.requirements()) {
						out.merge(req.item(), req.count(), Integer::sum);
					}
				}
			}
		}
		return out;
	}

	/**
	 * A full crew: a lead and three idle builders at their own tables help the test hut, with twice its materials in the
	 * chest. Item by item, each time a crew member's bag fills (a trip to the chest or a hand from a crewmate), it holds
	 * no more of any of the hut's materials than the hut still needs of that thing: no planks once the planks are all
	 * laid (B519: "the walls' last planks once the crew is on the lanterns"). At the end the hut is whole, every bag is
	 * empty and exactly the spare half is back in the chest.
	 */
	//$ gametest_ticks_batch AREA '4000' '"qaV1026bCrewItems"'
	@GameTest(template = AREA, timeoutTicks = 4000, batch = "qaV1026bCrewItems")
	public void noCrewBagHoldsMoreOfAThingThanTheHutStillNeeds(GameTestHelper helper) {
		Leftovers.clear(helper);
		Leftovers.finished(helper);
		ServerLevel level = helper.getLevel();
		helper.setDayTime(2000);
		boolean protection = io.github.jcondedata.aliveworkplace.hall.VillageProtection.ENABLED;
		io.github.jcondedata.aliveworkplace.hall.VillageProtection.ENABLED = false;
		var rules = level.getGameRules();
		boolean help = rules.getRule(ModGameRules.BUILDERS_HELP).get();
		int delay = rules.getRule(ModGameRules.BUILD_DELAY).get();
		boolean free = rules.getRule(ModGameRules.FREE_MATERIALS).get();
		rules.getRule(ModGameRules.BUILDERS_HELP).set(true, level.getServer());
		rules.getRule(ModGameRules.BUILD_DELAY).set(2, level.getServer());
		rules.getRule(ModGameRules.FREE_MATERIALS).set(false, level.getServer());
		Leftovers.after(helper, () -> {
			io.github.jcondedata.aliveworkplace.hall.VillageProtection.ENABLED = protection;
			rules.getRule(ModGameRules.BUILDERS_HELP).set(help, level.getServer());
			rules.getRule(ModGameRules.BUILD_DELAY).set(delay, level.getServer());
			rules.getRule(ModGameRules.FREE_MATERIALS).set(free, level.getServer());
		});
		BuildSiteManager sites = BuildSiteManager.get(level);
		for (BuildSite other : new ArrayList<>(sites.all())) {
			if (other.bench() != null && other.bench().closerThan(helper.absolutePos(TABLE), 3 * Builders.MAX_SITE_DISTANCE)) {
				sites.remove(other.id());
			}
		}
		helper.setBlock(TABLE, ModBlocks.BUILDERS_BENCH);
		helper.setBlock(CHEST, Blocks.CHEST);
		Villager[] all = new Villager[MATE_TABLES.length + 1];
		all[0] = helper.spawn(EntityType.VILLAGER, LEAD);
		Builders.employ(level, all[0], helper.absolutePos(TABLE));
		for (int i = 0; i < MATE_TABLES.length; i++) {
			helper.setBlock(MATE_TABLES[i], ModBlocks.BUILDERS_BENCH);
			all[i + 1] = helper.spawn(EntityType.VILLAGER, MATE_TABLES[i].south().east());
			Builders.employ(level, all[i + 1], helper.absolutePos(MATE_TABLES[i]));
		}
		BlueprintData.Placement placement = new BlueprintData.Placement(level.dimension().location(), helper.absolutePos(HUT), Rotation.NONE, Mirror.NONE);
		BuildSite site = Builders.start(level, all[0], null, TEST_HUT, placement);
		helper.assertTrue(site != null, "setup: the hut didn't start");
		BuildPlan plan = site.plan(level);
		helper.assertTrue(plan != null, "setup: no plan for the test hut");
		Leftovers.after(helper, () -> BuildSiteManager.get(level).remove(site.id()));
		Map<Item, Integer> materials = plan.materials();
		Container chest = helper.getBlockEntity(CHEST);
		int slot = 0;
		for (Map.Entry<Item, Integer> e : materials.entrySet()) {
			int left = e.getValue() * 2;
			while (left > 0) {
				int n = Math.min(left, e.getKey().getDefaultMaxStackSize());
				chest.setItem(slot++, new ItemStack(e.getKey(), n));
				left -= n;
			}
		}
		List<Map<Item, Integer>> last = new ArrayList<>();
		for (int i = 0; i < all.length; i++) {
			last.add(Map.of());
		}
		boolean[] helped = {false};
		int[] checks = {0};
		helper.onEachTick(() -> {
			helper.setDayTime(2000);
			if (BuildSiteManager.get(level).get(site.id()) == null) {
				return;
			}
			Map<Item, Integer> needed = stillNeeded(level, plan);
			for (int i = 0; i < all.length; i++) {
				Villager v = all[i];
				helped[0] |= i > 0 && Builders.isHelping(v);
				Map<Item, Integer> now = bag(v);
				Map<Item, Integer> before = last.get(i);
				boolean grew = now.entrySet().stream().anyMatch(e -> e.getValue() > before.getOrDefault(e.getKey(), 0));
				if (grew) {
					checks[0]++;
					for (Map.Entry<Item, Integer> e : now.entrySet()) {
						if (materials.containsKey(e.getKey())) {
							int need = needed.getOrDefault(e.getKey(), 0);
							helper.assertTrue(e.getValue() <= need, (i == 0 ? "the lead" : "helper " + i + (Builders.isHelping(v) ? " (helping)" : ""))
								+ " filled its bag to " + now + ": " + e.getValue() + " " + e.getKey() + " with only " + need + " still needed (stage "
								+ site.stage() + ", still needed " + needed + ")");
						}
					}
				}
				last.set(i, now);
			}
		});
		helper.succeedWhen(() -> {
			helper.assertTrue(helped[0], "setup: no idle builder ever helped");
			helper.assertTrue(BuildSiteManager.get(level).get(site.id()) == null, "the hut isn't finished: stage " + site.stage() + " status "
				+ site.status() + " missing " + site.missing());
			List<BlockPos> wrong = plan.unfinished(level);
			helper.assertTrue(wrong.isEmpty(), wrong.size() + " block(s) of the hut missing or wrong");
			for (int i = 0; i < all.length; i++) {
				helper.assertTrue(bag(all[i]).isEmpty(), "builder " + i + "'s bag isn't empty after the hut: " + bag(all[i]));
				helper.assertTrue(i == 0 || !Builders.isHelping(all[i]), "helper " + i + " is still on the finished hut");
			}
			Map<Item, Integer> held = new LinkedHashMap<>();
			for (Item item : materials.keySet()) {
				int n = chest.countItem(item);
				for (ItemEntity e : level.getEntitiesOfClass(ItemEntity.class, helper.getBounds().inflate(8), e -> e.getItem().is(item))) {
					n += e.getItem().getCount();
				}
				held.put(item, n);
			}
			helper.assertTrue(held.equals(materials), "after the hut the chest should hold exactly the spare " + materials + " but holds " + held);
			LOGGER.info("qa v1026b crew items: {} bag fills checked item by item", checks[0]);
		});
	}
}
