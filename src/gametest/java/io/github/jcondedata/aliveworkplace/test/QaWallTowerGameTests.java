package io.github.jcondedata.aliveworkplace.test;

import io.github.jcondedata.aliveworkplace.blueprint.BlueprintLibrary;
import io.github.jcondedata.aliveworkplace.blueprint.BlueprintOutline;
import io.github.jcondedata.aliveworkplace.build.BuildPlan;
import io.github.jcondedata.aliveworkplace.build.BuildSite;
import io.github.jcondedata.aliveworkplace.build.BuildSiteManager;
import io.github.jcondedata.aliveworkplace.build.Builders;
import io.github.jcondedata.aliveworkplace.city.CityPlan;
import io.github.jcondedata.aliveworkplace.city.CityPlanItem;
import io.github.jcondedata.aliveworkplace.city.Stewards;
import io.github.jcondedata.aliveworkplace.city.Walls;
import io.github.jcondedata.aliveworkplace.hall.VillageHallBlockEntity;
import io.github.jcondedata.aliveworkplace.registry.ModAttachments;
import io.github.jcondedata.aliveworkplace.registry.ModBlocks;
import io.github.jcondedata.aliveworkplace.registry.ModItems;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.phys.AABB;

/**
 * QA (lane-b-1025-1838), B283 from its spec: the hall's wall round laid a second Palisade Tower on top of one still being
 * built. Every build started finishes; no piece opens on a site's spot at any height; a builder never loses materials
 * (ladders handed out = placed + returned). Adversarial beyond WallGameTests.noSecondTowerOpensOnATowerStillBeingBuilt:
 * many rounds and many free builders, a tower half way up its walls, two towers decorating at once, a tower finished
 * the real way then a round, the site and its builder saved and loaded mid-decoration, and the ladders' ledger when the
 * builder dies mid-job.
 */
public class QaWallTowerGameTests {
	private static final String AREA = "aliveworkplace_test:huge_area";
	private static final BlockPos HALL = new BlockPos(14, 2, 14);
	private static final int LADDERS = 16;

	// ---- the round never opens a piece on a tower being built ---------------------------------------------------------

	/** A tower half way up its walls (no roof): five rounds with three free builders, nothing opens over its footprint. */
	//$ gametest_ticks_batch AREA '60' '"qaWallMidStructure"'
	@GameTest(template = AREA, timeoutTicks = 60, batch = "qaWallMidStructure")
	public void roundsWhileATowerIsHalfWayUpNeverOpenAnotherOnIt(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		Village v = village(helper);
		boolean was = Walls.ENABLED;
		try {
			Walls.ENABLED = true;
			Walls.Piece tower = firstTower(helper, v);
			BuildSite site = open(helper, v, v.builders().get(0), tower);
			BuildPlan plan = site.plan(level);
			int top = plan.steps(BuildPlan.Stage.STRUCTURE).stream().mapToInt(s -> s.pos().getY()).max().orElseThrow();
			int half = tower.placement().origin().getY() + (top - tower.placement().origin().getY()) / 2;
			int placed = place(level, plan, s -> s.pos().getY() <= half, BuildPlan.Stage.FOUNDATION, BuildPlan.Stage.STRUCTURE);
			helper.assertTrue(placed > 20, "setup: only " + placed + " blocks of the half tower placed");
			helper.assertTrue(!plan.steps(BuildPlan.Stage.STRUCTURE).stream().allMatch(s -> !level.getBlockState(s.pos()).isAir() || s.state().isAir()),
				"setup: the whole structure is already up");
			freeBuilders(helper, 3);
			BlockPos again = Walls.pieces(level, v.hall()).get(0).placement().origin();
			for (int round = 0; round < 5; round++) {
				Walls.round(level, v.hall(), v.entity());
				assertNothingOn(helper, v, site, tower, "round " + round);
			}
			int open = Walls.openSites(level, v.hall()).size();
			helper.assertTrue(BuildSiteManager.get(level).get(site.id()) != null, "the half-built tower's own site is gone");
			helper.assertTrue(open == Walls.MAX_OPEN, "the free builders got no other pieces: " + open + " wall sites open, not " + Walls.MAX_OPEN);
			log("mid-structure: tower at " + tower.placement().origin().toShortString() + " laid out again at " + again.toShortString()
				+ ", " + placed + " blocks up to y " + half + ", 5 rounds, " + open + " sites open, none on it");
		} finally {
			Walls.ENABLED = was;
		}
		helper.succeed();
	}

	/** Two towers with their roofs on, both decorating: rounds with free builders open nothing over either. */
	//$ gametest_ticks_batch AREA '60' '"qaWallTwoDecorating"'
	@GameTest(template = AREA, timeoutTicks = 60, batch = "qaWallTwoDecorating")
	public void roundsWhileTwoTowersDecorateNeverOpenOnEither(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		Village v = village(helper);
		boolean was = Walls.ENABLED;
		try {
			Walls.ENABLED = true;
			List<Walls.Piece> towers = Walls.pieces(level, v.hall()).stream().filter(p -> p.kind() == Walls.Kind.TOWER).limit(2).toList();
			helper.assertTrue(towers.size() == 2, "setup: the wall has " + towers.size() + " towers");
			List<Villager> extra = freeBuilders(helper, 3);
			List<BuildSite> sites = new ArrayList<>();
			sites.add(open(helper, v, v.builders().get(0), towers.get(0)));
			sites.add(open(helper, v, extra.get(0), towers.get(1)));
			for (BuildSite site : sites) {
				place(level, site.plan(level), s -> !s.state().is(Blocks.LADDER), BuildPlan.Stage.FOUNDATION, BuildPlan.Stage.STRUCTURE, BuildPlan.Stage.DECORATION);
			}
			for (int round = 0; round < 5; round++) {
				Walls.round(level, v.hall(), v.entity());
				for (int i = 0; i < 2; i++) {
					assertNothingOn(helper, v, sites.get(i), towers.get(i), "round " + round + ", tower " + i);
				}
			}
			assertNoTwoOverlap(helper, v);
			int open = Walls.openSites(level, v.hall()).size();
			helper.assertTrue(open == Walls.MAX_OPEN, open + " wall sites open with two towers and two free builders, not " + Walls.MAX_OPEN);
			log("two decorating: towers at " + towers.get(0).placement().origin().toShortString() + " and "
				+ towers.get(1).placement().origin().toShortString() + ", 5 rounds, " + open + " sites open, none on either");
		} finally {
			Walls.ENABLED = was;
		}
		helper.succeed();
	}

	/** A tower finished the way a builder finishes it (Builders.finish): rounds never open it again on its roof (B94). */
	//$ gametest_ticks_batch AREA '60' '"qaWallFinishedRound"'
	@GameTest(template = AREA, timeoutTicks = 60, batch = "qaWallFinishedRound")
	public void aTowerFinishedThenRoundsNeverGetsATwinOnItsRoof(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		Village v = village(helper);
		boolean was = Walls.ENABLED;
		try {
			Walls.ENABLED = true;
			Walls.Piece tower = firstTower(helper, v);
			BuildSite site = open(helper, v, v.builders().get(0), tower);
			BuildPlan plan = site.plan(level);
			place(level, plan, s -> true, BuildPlan.Stage.FOUNDATION, BuildPlan.Stage.STRUCTURE, BuildPlan.Stage.DECORATION);
			Builders.finish(level, v.builders().get(0), site);
			helper.assertTrue(BuildSiteManager.get(level).get(site.id()) == null, "setup: the finished tower's site is still open");
			freeBuilders(helper, 2);
			for (int round = 0; round < 4; round++) {
				Walls.round(level, v.hall(), v.entity());
				assertNothingOn(helper, v, null, tower, "round " + round + " after it was finished");
			}
			Walls.Piece again = Walls.pieces(level, v.hall()).get(0);
			helper.assertTrue(Walls.stands(level, again), "the finished tower doesn't stand, laid out again at " + again.placement().origin().toShortString());
			helper.assertTrue(Walls.unbuilt(level, v.hall()).stream().noneMatch(p -> p.index() == tower.index()), "the finished tower is still to build");
			log("finished: tower at " + tower.placement().origin().toShortString() + ", laid out again at "
				+ again.placement().origin().toShortString() + ", stands; 4 rounds, " + Walls.openSites(level, v.hall()).size() + " others open, none on it");
		} finally {
			Walls.ENABLED = was;
		}
		helper.succeed();
	}

	/**
	 * The tower's blocks up to its roof, only the ladders to go (put in by hand: the site's own stage is still the one it
	 * opened with; aTowerReloadedMidDecorationIsFinishedWhereItStands has a real DECORATION stage), the sites are saved
	 * and loaded (the manager's copy keeps the tower's stage and spot) and the builder
	 * and hall are reloaded from their saves: the rounds after still open nothing on the tower and its own site goes on.
	 */
	//$ gametest_ticks_batch AREA '60' '"qaWallReload"'
	@GameTest(template = AREA, timeoutTicks = 60, batch = "qaWallReload")
	public void aTowerSavedAndLoadedMidDecorationIsStillOneTower(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		Village v = village(helper);
		boolean was = Walls.ENABLED;
		try {
			Walls.ENABLED = true;
			Walls.Piece tower = firstTower(helper, v);
			BuildSite site = open(helper, v, v.builders().get(0), tower);
			BuildPlan plan = site.plan(level);
			place(level, plan, s -> !s.state().is(Blocks.LADDER), BuildPlan.Stage.FOUNDATION, BuildPlan.Stage.STRUCTURE, BuildPlan.Stage.DECORATION);
			BuildSiteManager manager = BuildSiteManager.get(level);
			CompoundTag tag = manager.save(new CompoundTag(), level.registryAccess());
			BuildSiteManager copy = BuildSiteManager.load(tag, level.registryAccess());
			BuildSite loaded = copy.get(site.id());
			helper.assertTrue(loaded != null, "the tower's site wasn't saved");
			helper.assertTrue(loaded.placement().equals(site.placement()) && loaded.structure().equals(site.structure()),
				"the site came back elsewhere: " + loaded.placement() + " " + loaded.structure());
			helper.assertTrue(loaded.stage() == site.stage(), "the site's stage changed over a save: " + site.stage() + " -> " + loaded.stage());
			BoundingBox t = tower.box();
			long towersSaved = copy.all().stream().filter(s -> s.structure().equals(tower.blueprint())
				&& s.placement().origin().getX() >= t.minX() - 8 && s.placement().origin().getX() <= t.maxX() + 8
				&& s.placement().origin().getZ() >= t.minZ() - 8 && s.placement().origin().getZ() <= t.maxZ() + 8).count();
			helper.assertTrue(towersSaved == 1, towersSaved + " tower sites on this tower's spot in the saved copy, not 1");
			int ladderSteps = (int) loaded.plan(level).steps(BuildPlan.Stage.DECORATION).stream().filter(s -> s.state().is(Blocks.LADDER)).count();
			long ladderOpen = loaded.plan(level).steps(BuildPlan.Stage.DECORATION).stream().filter(s -> s.state().is(Blocks.LADDER)
				&& !level.getBlockState(s.pos()).is(Blocks.LADDER)).count();
			helper.assertTrue(ladderSteps > 0 && ladderOpen == ladderSteps, "the loaded plan's ladders: " + ladderOpen + " open of " + ladderSteps);
			// the builder and the hall reloaded from their saves (as after a restart)
			Villager builder = reload(level, v.builders().get(0));
			helper.assertTrue(site.id().equals(ModAttachments.BUILDER_JOB.get(builder) == null ? null : ModAttachments.BUILDER_JOB.get(builder).siteId()),
				"the reloaded builder lost its tower job");
			CompoundTag hallTag = v.entity().saveWithFullMetadata(level.registryAccess());
			v.entity().loadWithComponents(hallTag, level.registryAccess());
			freeBuilders(helper, 2);
			for (int round = 0; round < 4; round++) {
				Walls.round(level, v.hall(), v.entity());
				assertNothingOn(helper, v, site, tower, "round " + round + " after the reload");
			}
			helper.assertTrue(BuildSiteManager.get(level).get(site.id()) != null, "the tower's site is gone after the reload");
			log("reload: site " + site.stage() + " saved and loaded at " + loaded.placement().origin().toShortString() + ", " + ladderSteps
				+ " ladders open; builder reloaded on its job; 4 rounds, one tower");
		} finally {
			Walls.ENABLED = was;
		}
		helper.succeed();
	}

	// ---- the ladders' ledger ----------------------------------------------------------------------------------------

	/**
	 * The builder dies mid-decoration with ladders in his bag: every ladder is still somewhere (on the tower, in the
	 * chest, or dropped where he fell); none vanish.
	 */
	//$ gametest_ticks_batch AREA '80' '"qaWallLedgerDeath"'
	@GameTest(template = AREA, timeoutTicks = 80, batch = "qaWallLedgerDeath")
	public void aBuilderKilledMidDecorationLosesNoLadders(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		Village v = village(helper);
		boolean was = Walls.ENABLED;
		try {
			Walls.ENABLED = true;
			Walls.Piece tower = firstTower(helper, v);
			Villager builder = v.builders().get(0);
			BuildSite site = open(helper, v, builder, tower);
			BuildPlan plan = site.plan(level);
			List<BuildPlan.Step> ladders = plan.steps(BuildPlan.Stage.DECORATION).stream().filter(s -> s.state().is(Blocks.LADDER)).toList();
			helper.assertTrue(ladders.size() >= 2, "setup: the tower has " + ladders.size() + " ladders");
			place(level, plan, s -> !s.state().is(Blocks.LADDER), BuildPlan.Stage.FOUNDATION, BuildPlan.Stage.STRUCTURE, BuildPlan.Stage.DECORATION);
			// half the ladders put up, the rest handed out to his bag from the chest
			int up = ladders.size() / 2;
			for (int i = 0; i < up; i++) {
				level.setBlock(ladders.get(i).pos(), ladders.get(i).state(), Block.UPDATE_CLIENTS);
			}
			Container chest = helper.getBlockEntity(HALL.offset(-5, 0, 0));
			int carried = ladders.size() - up;
			takeFromChest(chest, carried + up);
			ModAttachments.BUILDER_BAG.getOrCreate(builder).add(new ItemStack(Items.LADDER, carried));
			int before = ledger(helper, v, tower);
			helper.assertValueEqual(before, LADDERS, "setup: ladders before the death");
			builder.kill();
			helper.runAfterDelay(5, () -> {
				int after = ledger(helper, v, tower);
				int dropped = droppedLadders(helper);
				helper.assertTrue(!builder.isAlive(), "setup: the builder didn't die");
				helper.assertValueEqual(after, LADDERS, "ladders after the builder died (" + placedLadders(level, tower) + " on the tower, "
					+ chest.countItem(Items.LADDER) + " in the chest, " + dropped + " dropped)");
				helper.assertTrue(dropped == carried, "the " + carried + " ladders he carried should lie where he fell, " + dropped + " there");
				log("ledger at death: " + up + " placed + " + chest.countItem(Items.LADDER) + " in chest + " + dropped + " dropped = " + after);
				helper.succeed();
			});
		} finally {
			Walls.ENABLED = was;
		}
	}

	/**
	 * A real builder builds the tower from his chest; the ledger (on the tower + in the chest + in bags + dropped) is the
	 * chest's 16 every second all the way; at the end every ladder step is a ladder and the spare ladders are back.
	 */
	//$ gametest_ticks_batch AREA '6000' '"qaWallLedgerRun"'
	@GameTest(template = AREA, timeoutTicks = 6000, batch = "qaWallLedgerRun")
	public void aTowerBuiltForRealHandsOutEveryLadderItPlacesOrReturns(GameTestHelper helper) {
		helper.setDayTime(2000);
		ServerLevel level = helper.getLevel();
		Village v = village(helper);
		boolean was = Walls.ENABLED;
		Walls.ENABLED = true;
		Walls.Piece tower;
		BuildSite site;
		List<BuildPlan.Step> ladders;
		try {
			tower = firstTower(helper, v);
			site = open(helper, v, v.builders().get(0), tower);
			ladders = site.plan(level).steps(BuildPlan.Stage.DECORATION).stream().filter(s -> s.state().is(Blocks.LADDER)).toList();
		} finally {
			Walls.ENABLED = was;
		}
		Container chest = helper.getBlockEntity(HALL.offset(-5, 0, 0));
		helper.onEachTick(() -> {
			if (level.getGameTime() % 20 == 0) {
				int now = ledger(helper, v, tower);
				helper.assertTrue(now == LADDERS, "ladders went missing mid-build (stage " + site.stage() + "): " + placedLadders(level, tower)
					+ " on the tower, " + chest.countItem(Items.LADDER) + " in the chest, " + bagLadders(helper) + " in bags, " + droppedLadders(helper)
					+ " dropped = " + now + " of " + LADDERS);
			}
		});
		helper.succeedWhen(() -> {
			helper.assertTrue(BuildSiteManager.get(level).get(site.id()) == null, "the tower isn't finished yet (stage " + site.stage() + ")");
			long up = ladders.stream().filter(s -> level.getBlockState(s.pos()).is(Blocks.LADDER)).count();
			helper.assertTrue(up == ladders.size(), up + " of the tower's " + ladders.size() + " ladders are up");
			helper.assertValueEqual(chest.countItem(Items.LADDER), LADDERS - ladders.size(), "spare ladders back in the chest");
			helper.assertValueEqual(bagLadders(helper), 0, "ladders left in bags");
			log("ledger run: " + up + " ladders up, " + chest.countItem(Items.LADDER) + " back in the chest");
		});
	}

	/**
	 * A real builder reaches DECORATION (roof on, ladders to go); there the sites are saved and loaded, the builder is
	 * reloaded from his save and three rounds run with a free builder: nothing opens on the tower, the reloaded builder
	 * finishes it where it stands, every ladder goes up and it is the only tower there.
	 */
	//$ gametest_ticks_batch AREA '6000' '"qaWallReloadReal"'
	@GameTest(template = AREA, timeoutTicks = 6000, batch = "qaWallReloadReal")
	public void aTowerReloadedMidDecorationIsFinishedWhereItStands(GameTestHelper helper) {
		helper.setDayTime(2000);
		ServerLevel level = helper.getLevel();
		Village v = village(helper);
		boolean was = Walls.ENABLED;
		Walls.ENABLED = true;
		Walls.Piece tower;
		BuildSite site;
		List<BuildPlan.Step> ladders;
		try {
			tower = firstTower(helper, v);
			site = open(helper, v, v.builders().get(0), tower);
			ladders = site.plan(level).steps(BuildPlan.Stage.DECORATION).stream().filter(s -> s.state().is(Blocks.LADDER)).toList();
		} finally {
			Walls.ENABLED = was;
		}
		boolean[] reloaded = {false};
		String[] seen = {""};
		helper.onEachTick(() -> {
			if (reloaded[0] || site.stage() != BuildPlan.Stage.DECORATION) {
				return;
			}
			reloaded[0] = true;
			BuildSiteManager copy = BuildSiteManager.load(BuildSiteManager.get(level).save(new CompoundTag(), level.registryAccess()), level.registryAccess());
			BuildSite loaded = copy.get(site.id());
			helper.assertTrue(loaded != null && loaded.stage() == BuildPlan.Stage.DECORATION && loaded.placement().equals(site.placement()),
				"the decorating tower's site after a save: " + (loaded == null ? null : loaded.stage() + " at " + loaded.placement()));
			long up = ladders.stream().filter(s -> level.getBlockState(s.pos()).is(Blocks.LADDER)).count();
			Villager builder = reload(level, v.builders().get(0));
			helper.assertTrue(ModAttachments.BUILDER_JOB.get(builder) != null && site.id().equals(ModAttachments.BUILDER_JOB.get(builder).siteId()),
				"the reloaded builder lost the tower");
			freeBuilders(helper, 1);
			boolean on = Walls.ENABLED;
			Walls.ENABLED = true;
			try {
				for (int round = 0; round < 3; round++) {
					Walls.round(level, v.hall(), v.entity());
					assertNothingOn(helper, v, site, tower, "round " + round + " after the reload mid-decoration");
				}
			} finally {
				Walls.ENABLED = on;
			}
			seen[0] = "reloaded in DECORATION with " + up + " of " + ladders.size() + " ladders up, " + BuildSiteManager.get(level).all().size() + " sites";
		});
		helper.succeedWhen(() -> {
			helper.assertTrue(reloaded[0], "the tower never reached DECORATION (stage " + site.stage() + ")");
			helper.assertTrue(BuildSiteManager.get(level).get(site.id()) == null, "the tower isn't finished yet (stage " + site.stage() + ")");
			long up = ladders.stream().filter(s -> level.getBlockState(s.pos()).is(Blocks.LADDER)).count();
			helper.assertTrue(up == ladders.size(), up + " of the tower's " + ladders.size() + " ladders are up after the reload");
			assertNothingOn(helper, v, null, tower, "after the reloaded tower was finished");
			log("reload real: " + seen[0] + "; finished where it stands, " + up + " ladders up");
		});
	}

	// ---- helpers ----------------------------------------------------------------------------------------------------

	private static void log(String line) {
		org.slf4j.LoggerFactory.getLogger("aliveworkplace-qa").info("[QA B283] {}", line);
	}

	private static Walls.Piece firstTower(GameTestHelper helper, Village v) {
		Walls.Piece tower = Walls.pieces(helper.getLevel(), v.hall()).get(0);
		helper.assertTrue(tower.kind() == Walls.Kind.TOWER, "setup: the first piece is a " + tower.kind());
		return tower;
	}

	/** The piece opened for {@code builder}, as the round opens it. */
	private static BuildSite open(GameTestHelper helper, Village v, Villager builder, Walls.Piece piece) {
		BuildSite site = Builders.start(helper.getLevel(), builder, v.owner().getUUID(), "", piece.blueprint(), piece.placement());
		site.setStewardHall(v.hall());
		site.setLevelGround(false);
		return site;
	}

	/** Puts the steps of {@code stages} that pass {@code which} into the world; how many. */
	private static int place(ServerLevel level, BuildPlan plan, java.util.function.Predicate<BuildPlan.Step> which, BuildPlan.Stage... stages) {
		int n = 0;
		for (BuildPlan.Stage stage : stages) {
			for (BuildPlan.Step step : plan.steps(stage)) {
				if (which.test(step)) {
					level.setBlock(step.pos(), step.state(), Block.UPDATE_CLIENTS);
					n++;
				}
			}
		}
		return n;
	}

	/** {@code n} more builders, each with an empty bench of his own inside the wall line. */
	private static List<Villager> freeBuilders(GameTestHelper helper, int n) {
		List<BlockPos> tables = List.of(new BlockPos(10, 2, 18), new BlockPos(18, 2, 10), new BlockPos(18, 2, 18));
		List<Villager> out = new ArrayList<>();
		for (int i = 0; i < n; i++) {
			helper.setBlock(tables.get(i), ModBlocks.BLUEPRINT_TABLE);
			Villager b = helper.spawn(EntityType.VILLAGER, tables.get(i).east());
			Builders.employ(helper.getLevel(), b, helper.absolutePos(tables.get(i)));
			out.add(b);
		}
		return out;
	}

	/** No site but {@code own} (null: none) overlaps the tower's footprint, at any height. */
	private static void assertNothingOn(GameTestHelper helper, Village v, BuildSite own, Walls.Piece tower, String when) {
		ServerLevel level = helper.getLevel();
		BoundingBox t = tower.box();
		for (BuildSite other : BuildSiteManager.get(level).all()) {
			if (own != null && other.id().equals(own.id())) {
				continue;
			}
			BoundingBox box = BlueprintLibrary.get(level, other.structure()).map(b -> BlueprintOutline.bounds(other.placement(), b.size())).orElse(null);
			if (box == null) {
				continue;
			}
			boolean over = box.minX() <= t.maxX() && box.maxX() >= t.minX() && box.minZ() <= t.maxZ() && box.maxZ() >= t.minZ();
			helper.assertTrue(!over, when + ": a " + other.structure().getPath() + " opened at " + other.placement().origin().toShortString() + " (box "
				+ box + ") on the tower at " + tower.placement().origin().toShortString() + " (box " + t + ")");
		}
	}

	/** No two open wall sites overlap, unless both are segments (a run's last segment may sit flush on its neighbour). */
	private static void assertNoTwoOverlap(GameTestHelper helper, Village v) {
		ServerLevel level = helper.getLevel();
		List<BuildSite> open = Walls.openSites(level, v.hall());
		for (int i = 0; i < open.size(); i++) {
			for (int j = i + 1; j < open.size(); j++) {
				BuildSite a = open.get(i);
				BuildSite b = open.get(j);
				BoundingBox ba = BlueprintLibrary.get(level, a.structure()).map(bp -> BlueprintOutline.bounds(a.placement(), bp.size())).orElseThrow();
				BoundingBox bb = BlueprintLibrary.get(level, b.structure()).map(bp -> BlueprintOutline.bounds(b.placement(), bp.size())).orElseThrow();
				boolean xz = ba.minX() <= bb.maxX() && ba.maxX() >= bb.minX() && ba.minZ() <= bb.maxZ() && ba.maxZ() >= bb.minZ();
				boolean segments = a.structure().getPath().equals("palisade") && b.structure().getPath().equals("palisade");
				helper.assertTrue(!xz || segments, "two open wall sites overlap: " + a.structure().getPath() + " " + ba + " and " + b.structure().getPath() + " " + bb);
			}
		}
	}

	private static void takeFromChest(Container chest, int n) {
		for (int i = 0; i < chest.getContainerSize() && n > 0; i++) {
			ItemStack s = chest.getItem(i);
			if (s.is(Items.LADDER)) {
				int take = Math.min(n, s.getCount());
				s.shrink(take);
				n -= take;
			}
		}
	}

	private static int placedLadders(ServerLevel level, Walls.Piece tower) {
		BoundingBox t = tower.box();
		int[] n = {0};
		BlockPos.betweenClosed(t.minX(), t.minY() - 2, t.minZ(), t.maxX(), t.maxY() + 30, t.maxZ()).forEach(p -> {
			if (level.getBlockState(p).is(Blocks.LADDER)) {
				n[0]++;
			}
		});
		return n[0];
	}

	private static AABB around(GameTestHelper helper) {
		return helper.getBounds().inflate(48);
	}

	private static int bagLadders(GameTestHelper helper) {
		int n = 0;
		for (Villager villager : helper.getLevel().getEntitiesOfClass(Villager.class, around(helper))) {
			n += ModAttachments.BUILDER_BAG.getOrCreate(villager).count(Items.LADDER);
		}
		return n;
	}

	private static int droppedLadders(GameTestHelper helper) {
		int n = 0;
		for (ItemEntity item : helper.getLevel().getEntitiesOfClass(ItemEntity.class, around(helper))) {
			if (item.getItem().is(Items.LADDER)) {
				n += item.getItem().getCount();
			}
		}
		return n;
	}

	/** Every ladder in the test: on the tower, in the bench's chest, in bags, on the ground. */
	private static int ledger(GameTestHelper helper, Village v, Walls.Piece tower) {
		Container chest = helper.getBlockEntity(HALL.offset(-5, 0, 0));
		return placedLadders(helper.getLevel(), tower) + chest.countItem(Items.LADDER) + bagLadders(helper) + droppedLadders(helper);
	}

	private static Villager reload(ServerLevel level, Villager villager) {
		CompoundTag tag = new CompoundTag();
		villager.saveWithoutId(tag);
		villager.discard();
		Villager copy = EntityType.VILLAGER.create(level);
		copy.load(tag);
		level.addFreshEntity(copy);
		io.github.jcondedata.aliveworkplace.work.Village.forget(copy);
		return copy;
	}

	// ---- the village (as WallGameTests builds it) --------------------------------------------------------------------

	record Village(BlockPos hall, VillageHallBlockEntity entity, ServerPlayer owner, List<Villager> builders, Villager steward) {
	}

	/** Flat grass to the area's top, a hall with an owner, a Steward, one builder with a stocked bench, a square wall. */
	static Village village(GameTestHelper helper) {
		Leftovers.clear(helper);
		ServerLevel level = helper.getLevel();
		BlockPos a = helper.absolutePos(BlockPos.ZERO);
		BoundingBox near = new BoundingBox(a.getX() - 80, level.getMinBuildHeight(), a.getZ() - 80, a.getX() + 110, level.getMaxBuildHeight(), a.getZ() + 110);
		BuildSiteManager manager = BuildSiteManager.get(level);
		for (BuildSite site : new ArrayList<>(manager.all())) {
			if (near.isInside(site.placement().origin())) {
				manager.remove(site.id());
			}
		}
		for (BuildSiteManager.Finished f : manager.finishedIn(level)) {
			if (near.isInside(f.placement().origin())) {
				manager.forgetFinished(f.placement());
			}
		}
		for (int x = 0; x < 30; x++) {
			for (int z = 0; z < 30; z++) {
				helper.setBlock(new BlockPos(x, 0, z), Blocks.DIRT);
				helper.setBlock(new BlockPos(x, 1, z), Blocks.GRASS_BLOCK);
				for (int y = 2; y < 30; y++) {
					helper.setBlock(new BlockPos(x, y, z), Blocks.AIR); // to the top, so nothing up there is ground
				}
			}
		}
		helper.setBlock(HALL, ModBlocks.VILLAGE_HALL);
		BlockPos hall = helper.absolutePos(HALL);
		ServerPlayer owner = helper.makeMockServerPlayerInLevel();
		VillageHallBlockEntity entity = (VillageHallBlockEntity) level.getBlockEntity(hall);
		entity.setOwner(owner.getUUID(), owner.getGameProfile().getName());
		BlockPos table = HALL.offset(-4, 0, 0);
		helper.setBlock(table, ModBlocks.BLUEPRINT_TABLE);
		helper.setBlock(table.west(), Blocks.CHEST);
		Container chest = helper.getBlockEntity(table.west());
		chest.setItem(0, new ItemStack(Items.SPRUCE_LOG, 64));
		chest.setItem(1, new ItemStack(Items.STRIPPED_SPRUCE_LOG, 64));
		chest.setItem(2, new ItemStack(Items.SPRUCE_FENCE, 64));
		chest.setItem(3, new ItemStack(Items.SPRUCE_PLANKS, 64));
		chest.setItem(4, new ItemStack(Items.DARK_OAK_STAIRS, 64));
		chest.setItem(5, new ItemStack(Items.DARK_OAK_SLAB, 16));
		chest.setItem(6, new ItemStack(Items.LADDER, LADDERS));
		chest.setItem(7, new ItemStack(Items.LANTERN, 8));
		chest.setItem(8, new ItemStack(Items.COBBLESTONE, 64));
		chest.setItem(9, new ItemStack(Items.STONE, 64));
		chest.setItem(10, new ItemStack(Items.ANDESITE, 64));
		chest.setItem(11, new ItemStack(Items.DIRT, 64));
		List<Villager> builders = new ArrayList<>();
		Villager builder = helper.spawn(EntityType.VILLAGER, table.east());
		Builders.employ(level, builder, helper.absolutePos(table));
		builders.add(builder);
		Villager steward = StewardGameTests.seasoned(helper.spawn(EntityType.VILLAGER, HALL.north(2)));
		ItemStack plan = new ItemStack(ModItems.CITY_PLAN);
		CityPlanItem.bind(level, owner, plan, hall);
		helper.assertTrue(Stewards.appoint(owner, steward, plan) == InteractionResult.SUCCESS, "setup: Steward not appointed");
		entity.setPlan(CityPlan.EMPTY.withWall(new CityPlan.Wall(List.of(new BlockPos(-8, 0, -8), new BlockPos(8, 0, -8), new BlockPos(8, 0, 8),
			new BlockPos(-8, 0, 8)), true).approvedWith("palisade")));
		return new Village(hall, entity, owner, builders, steward);
	}

}
