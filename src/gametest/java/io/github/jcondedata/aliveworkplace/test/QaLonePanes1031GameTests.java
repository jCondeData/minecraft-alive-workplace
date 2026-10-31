package io.github.jcondedata.aliveworkplace.test;

import io.github.jcondedata.aliveworkplace.AliveWorkplace;
import io.github.jcondedata.aliveworkplace.blueprint.Blueprint;
import io.github.jcondedata.aliveworkplace.blueprint.BlueprintData;
import io.github.jcondedata.aliveworkplace.blueprint.BlueprintLibrary;
import io.github.jcondedata.aliveworkplace.blueprint.BlueprintOutline;
import io.github.jcondedata.aliveworkplace.blueprint.StarterBlueprints;
import io.github.jcondedata.aliveworkplace.build.BuildPlan;
import io.github.jcondedata.aliveworkplace.build.BuildSite;
import io.github.jcondedata.aliveworkplace.build.BuildSiteManager;
import io.github.jcondedata.aliveworkplace.build.Builders;
import io.github.jcondedata.aliveworkplace.build.Refreshes;
import io.github.jcondedata.aliveworkplace.registry.ModBlocks;
import io.github.jcondedata.aliveworkplace.registry.ModGameRules;
import io.github.jcondedata.aliveworkplace.wonder.Wonder;
import io.github.jcondedata.aliveworkplace.wonder.WonderBlueprints;
import io.github.jcondedata.aliveworkplace.wonder.Wonders;
import static io.github.jcondedata.aliveworkplace.test.QaWindowsAndWards1028GameTests.endsInAir;
import static io.github.jcondedata.aliveworkplace.test.QaWindowsAndWards1028GameTests.glass;
import static io.github.jcondedata.aliveworkplace.test.QaWindowsAndWards1028GameTests.joins;
import static io.github.jcondedata.aliveworkplace.test.QaWindowsAndWards1028GameTests.over;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Vec3i;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CrossCollisionBlock;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.WallBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;

/**
 * QA (qa-lane-b-1031-0338), B656: no window's glass stands as a lone thin post. The Pokemon Center's Sunny Plaza looks
 * I and II close each arched front window on a chiseled sandstone keystone (3,3,2 and 9,3,2) with glass three wide and
 * two high, pilaster to pilaster; the Lighthouse Wonder's two-tall windows have both panes in one column in the tower
 * wall, the nub in front cleared, and the tower's stage part keeps its size and offset so a stage half built on a
 * server still lines up. Unlike QaWindowsAndWards1028BugTests (the shipped files, joined as the game joins them) these:
 * <ul>
 * <li>have a builder really build Sunny Plaza I, and Sunny Plaza II turned and mirrored, and read the glass in the
 * world after the game's neighbour updates;</li>
 * <li>judge both plaza looks in every turn and mirror a player can place them in, through the builder's own plan;</li>
 * <li>check every Lighthouse window cell by cell (one column, nothing out in front, a ledge under it), and that the
 * same check finds the lone panes in the drawing before the fix (v0.151.0);</li>
 * <li>compare the redrawn tower part with v0.151.0: the same size and offset, changed only at the two window rows.</li>
 * </ul>
 */
public class QaLonePanes1031GameTests implements net.fabricmc.fabric.api.gametest.v1.FabricGameTest {
	private static final org.slf4j.Logger LOGGER = org.slf4j.LoggerFactory.getLogger("aliveworkplace_test");
	private static final String AREA = "aliveworkplace_test:huge_area";
	private static final BlockPos BENCH = new BlockPos(2, 2, 2);
	/** The arch windows' keystones in both plaza looks, and the glass under each (x0..x0+2, y 1..2, z 2). */
	private static final List<BlockPos> KEYSTONES = List.of(new BlockPos(3, 3, 2), new BlockPos(9, 3, 2));
	private static final ResourceLocation LIGHTHOUSE = AliveWorkplace.id("lighthouse");
	private static final ResourceLocation TOWER = AliveWorkplace.id("wonder/lighthouse/stage_2_0");
	/** The tower's middle in the whole Lighthouse (wonders.py LC), and each window's lower row (L_WINDOWS). */
	private static final int CX = 12, CZ = 21;
	private static final int[] WINDOWS = {9, 16, 26};
	/** The rows B656 redrew: where the tower narrows past a whole block between a window's two rows. */
	private static final Set<Integer> REDRAWN = Set.of(9, 26);
	private static final int[][] SIDES = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};

	/**
	 * A builder puts up Sunny Plaza I from start to DONE: in the world, after the game has joined every pane to what
	 * really stands beside it, each arch closes on a chiseled sandstone keystone, the six panes under each run from
	 * pilaster to pilaster joined at both ends, and no glass in the building is a lone post or ends in the air.
	 */
	//$ gametest_ticks_batch AREA '40000' '"qaSunnyPlazaBuilt"'
	@GameTest(template = AREA, timeoutTicks = 40000, batch = "qaSunnyPlazaBuilt")
	public void qaABuiltSunnyPlazaClosesItsArchesOnKeystonesWithNoLonePane(GameTestHelper helper) {
		Build b = start(helper, StarterBlueprints.POKEMON_CENTER_PLAZA, Rotation.NONE, Mirror.NONE, new BlockPos(8, 2, 8));
		helper.succeedWhen(() -> assertBuiltGlass(helper, b, "Sunny Plaza I"));
	}

	/**
	 * A builder puts up Sunny Plaza II turned a quarter clockwise and mirrored front to back, as a player can place it:
	 * the keystones and arch glass land turned with the rest, and in the world no pane is a lone post or ends in air.
	 */
	//$ gametest_ticks_batch AREA '60000' '"qaSunnyPlaza2Turned"'
	@GameTest(template = AREA, timeoutTicks = 60000, batch = "qaSunnyPlaza2Turned")
	public void qaABuiltSunnyPlazaIITurnedAndMirroredHasNoLonePane(GameTestHelper helper) {
		Build b = start(helper, StarterBlueprints.POKEMON_CENTER_PLAZA_2, Rotation.CLOCKWISE_90, Mirror.FRONT_BACK, new BlockPos(4, 2, 4));
		helper.succeedWhen(() -> assertBuiltGlass(helper, b, "Sunny Plaza II (turned, mirrored)"));
	}

	/**
	 * Both Sunny Plaza looks in all four turns, plain and mirrored both ways (12 placements each), as the builder's
	 * own plan lays them out: the keystones are chiseled sandstone, the arch glass joins at both ends, and no pane in
	 * the look, joined as the game joins it, is a lone post or ends in the air.
	 */
	//$ gametest_ticks EMPTY_STRUCTURE '200'
	@GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 200)
	public void qaEveryTurnOfBothSunnyPlazasJoinsItsGlassAtBothEnds(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		List<String> problems = new ArrayList<>();
		int placements = 0;
		int panes = 0;
		for (StarterBlueprints.Entry entry : List.of(StarterBlueprints.POKEMON_CENTER_PLAZA, StarterBlueprints.POKEMON_CENTER_PLAZA_2)) {
			Blueprint blueprint = BlueprintLibrary.get(level, entry.id()).orElseThrow(() -> new IllegalStateException("no blueprint " + entry.id()));
			for (Mirror mirror : Mirror.values()) {
				for (Rotation rotation : Rotation.values()) {
					BlockPos origin = new BlockPos(1000, 100, 1000);
					BlueprintData.Placement placement = new BlueprintData.Placement(level.dimension().location(), origin, rotation, mirror);
					Map<BlockPos, BlockState> blocks = new HashMap<>(BuildPlan.create(blueprint, placement).targets());
					BlockGetter getter = over(blocks);
					String where = entry.id().getPath() + " " + rotation + " " + mirror;
					placements++;
					for (Map.Entry<BlockPos, BlockState> e : blocks.entrySet()) {
						if (glass(e.getValue())) {
							panes++;
							String why = endsInAir(getter, e.getKey());
							if (why != null) {
								problems.add(where + ": pane at " + e.getKey().subtract(origin).toShortString() + " " + why);
							}
						}
					}
					problems.addAll(arches(getter, origin, rotation, mirror, where));
				}
			}
		}
		LOGGER.info("QA B656: {} plaza placements, {} panes judged, {} problems", placements, panes, problems.size());
		helper.assertTrue(placements == 24 && panes > 100, "only " + placements + " placements and " + panes + " panes judged");
		helper.assertTrue(problems.isEmpty(), "B656: the Sunny Plaza's glass turned and mirrored: " + problems.size() + " wrong, e.g. "
			+ String.join("; ", problems.subList(0, Math.min(4, problems.size()))));
		helper.succeed();
	}

	/**
	 * Every two-tall window of the whole Lighthouse (rows 9, 16 and 26, all four sides): its two panes stand in one
	 * column in the tower wall, joined by the game to the wall at both sides, with no glass and no block out in front
	 * at the lower row and a ledge under the cell in front. The same check finds the 8 lone panes in the tower part as
	 * drawn before B656 (v0.151.0), so it would catch them coming back.
	 */
	//$ gametest_ticks EMPTY_STRUCTURE '200'
	@GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 200)
	public void qaEveryLighthouseWindowStandsInTheWallInOneColumn(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		Blueprint whole = WonderBlueprints.whole(level.getServer(), WonderBlueprints.idOf(LIGHTHOUSE))
			.orElseThrow(() -> new IllegalStateException("no whole Lighthouse"));
		Map<BlockPos, BlockState> now = new HashMap<>();
		whole.blocks().forEach(e -> now.put(e.pos(), e.state()));
		List<String> problems = new ArrayList<>();
		for (int y : WINDOWS) {
			for (int[] d : SIDES) {
				String why = window(now, y, d);
				if (why != null) {
					problems.add(why);
				}
			}
		}
		// The drawing before the fix, laid in the whole at the tower part's offset: the check must find its lone panes
		Wonder.Part tower = towerPart();
		Blueprint old = Refreshes.olderDrawing(level.getServer(), TOWER, "v0.151.0")
			.orElseThrow(() -> new IllegalStateException("no v0.151.0 drawing of " + TOWER + " in blueprint_history"));
		Map<BlockPos, BlockState> before = new HashMap<>(now);
		old.blocks().forEach(e -> before.put(e.pos().offset(tower.offset()), e.state()));
		int caught = 0;
		for (int y : REDRAWN) {
			for (int[] d : SIDES) {
				caught += window(before, y, d) != null ? 1 : 0;
			}
		}
		LOGGER.info("QA B656: the Lighthouse's 12 windows, {} wrong now; the check finds {} of 8 in the v0.151.0 tower", problems.size(), caught);
		helper.assertTrue(caught == 8, "the window check finds only " + caught + " of the 8 lone panes in the tower drawn before B656");
		helper.assertTrue(problems.isEmpty(), "B656: the Lighthouse's windows: " + problems.size() + " wrong, e.g. "
			+ String.join("; ", problems.subList(0, Math.min(4, problems.size()))));
		helper.succeed();
	}

	/**
	 * The redrawn tower part (Lighthouse stage II) keeps the size and the offset of the drawing before B656 (v0.151.0),
	 * the size it shares with no gap or overlap against the stage parts on either side, and every block that changed
	 * lies at the two redrawn window rows on the four window rays: a stage half built on a server lines up with it.
	 */
	//$ gametest_ticks EMPTY_STRUCTURE '200'
	@GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 200)
	public void qaTheRedrawnLighthouseTowerKeepsItsSizeAndPlace(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		Wonder.Part tower = towerPart();
		helper.assertTrue(tower.offset().equals(new BlockPos(5, 3, 14)), "the tower part's offset moved: " + tower.offset().toShortString());
		Blueprint now = BlueprintLibrary.get(level.getServer(), TOWER).orElseThrow(() -> new IllegalStateException("no " + TOWER));
		Blueprint old = Refreshes.olderDrawing(level.getServer(), TOWER, "v0.151.0")
			.orElseThrow(() -> new IllegalStateException("no v0.151.0 drawing of " + TOWER));
		helper.assertTrue(now.size().equals(old.size()), "the tower part's size changed: " + old.size().toShortString() + " -> " + now.size().toShortString());
		Map<BlockPos, BlockState> a = new HashMap<>();
		Map<BlockPos, BlockState> b = new HashMap<>();
		old.blocks().forEach(e -> a.put(e.pos(), e.state()));
		now.blocks().forEach(e -> b.put(e.pos(), e.state()));
		Set<BlockPos> all = new HashSet<>(a.keySet());
		all.addAll(b.keySet());
		BlockState air = Blocks.AIR.defaultBlockState();
		List<String> stray = new ArrayList<>();
		int changed = 0;
		int cx = CX - tower.offset().getX();
		int cz = CZ - tower.offset().getZ();
		for (BlockPos p : all) {
			BlockState was = a.getOrDefault(p, air);
			BlockState is = b.getOrDefault(p, air);
			if (was.equals(is) || (was.isAir() && is.isAir())) {
				continue;
			}
			changed++;
			int wy = p.getY() + tower.offset().getY();
			boolean onRay = p.getX() == cx || p.getZ() == cz;
			boolean atRow = REDRAWN.stream().anyMatch(y -> wy >= y - 1 && wy <= y + 1);
			if (!onRay || !atRow) {
				stray.add(p.toShortString() + " " + was.getBlock() + " -> " + is.getBlock());
			}
		}
		// Its neighbours in the Wonder stand where they stood: every part still fits inside the whole Wonder
		Blueprint whole = WonderBlueprints.whole(level.getServer(), WonderBlueprints.idOf(LIGHTHOUSE)).orElseThrow();
		Vec3i far = new Vec3i(tower.offset().getX() + now.size().getX(), tower.offset().getY() + now.size().getY(), tower.offset().getZ() + now.size().getZ());
		helper.assertTrue(far.getX() <= whole.size().getX() && far.getY() <= whole.size().getY() && far.getZ() <= whole.size().getZ(),
			"the tower part reaches " + far.toShortString() + " past the whole Lighthouse's " + whole.size().toShortString());
		LOGGER.info("QA B656: the tower part {} at {}, {} blocks changed from v0.151.0, {} away from the redrawn windows", now.size().toShortString(),
			tower.offset().toShortString(), changed, stray.size());
		helper.assertTrue(changed >= 8, "only " + changed + " blocks of the tower changed from v0.151.0; the 8 lone panes are still there?");
		helper.assertTrue(stray.isEmpty(), "B656: the tower part changed away from its windows (a half-built stage wouldn't line up): " + stray.size()
			+ ", e.g. " + String.join("; ", stray.subList(0, Math.min(4, stray.size()))));
		helper.succeed();
	}

	/** The Lighthouse's tower (stage II) part. */
	private static Wonder.Part towerPart() {
		Wonder wonder = Wonders.get(LIGHTHOUSE).orElseThrow(() -> new IllegalStateException("no Lighthouse Wonder loaded"));
		return wonder.stages().stream().flatMap(s -> s.parts().stream()).filter(p -> p.part().equals(TOWER)).findFirst()
			.orElseThrow(() -> new IllegalStateException("the Lighthouse has no part " + TOWER));
	}

	/**
	 * One window of the whole Lighthouse (lower row y, side d): null when its two panes stand in one column in the wall,
	 * each joined at both sides by the game to a block, nothing out in front at the lower row and a ledge under that.
	 */
	private static String window(Map<BlockPos, BlockState> blocks, int y, int[] d) {
		String where = "the window at y " + y + " facing " + d[0] + "," + d[1];
		BlockGetter getter = over(blocks);
		BlockPos upper = null;
		for (int k = 12; k >= 1; k--) {
			BlockPos p = new BlockPos(CX + d[0] * k, y + 1, CZ + d[1] * k);
			if (glass(blocks.getOrDefault(p, Blocks.AIR.defaultBlockState()))) {
				upper = p;
				break;
			}
		}
		if (upper == null) {
			return where + ": no upper pane";
		}
		BlockPos lower = upper.below();
		if (!glass(getter.getBlockState(lower))) {
			return where + ": the lower pane isn't under the upper one at " + upper.toShortString() + " (" + getter.getBlockState(lower).getBlock() + " there)";
		}
		Direction out = Direction.fromDelta(d[0], 0, d[1]);
		for (int k = 1; k <= 4; k++) {
			BlockState front = getter.getBlockState(lower.relative(out, k));
			if (glass(front)) {
				return where + ": a pane " + k + " out in front of the wall at " + lower.relative(out, k).toShortString();
			}
		}
		if (!getter.getBlockState(lower.relative(out)).isAir()) {
			return where + ": the cell in front of the lower pane isn't open: " + getter.getBlockState(lower.relative(out)).getBlock();
		}
		if (getter.getBlockState(lower.relative(out).below()).isAir()) {
			return where + ": no ledge under the cell in front of the glass";
		}
		Direction side = out.getClockWise();
		for (BlockPos pane : List.of(lower, upper)) {
			boolean[] j = joins(getter, pane);
			if (!j[side.get2DDataValue()] || !j[side.getOpposite().get2DDataValue()]) {
				return where + ": the pane at " + pane.toShortString() + " isn't joined at both sides (" + getter.getBlockState(pane.relative(side)).getBlock()
					+ ", " + getter.getBlockState(pane.relative(side.getOpposite())).getBlock() + ")";
			}
			String why = endsInAir(getter, pane);
			if (why != null) {
				return where + ": the pane at " + pane.toShortString() + " " + why;
			}
		}
		return null;
	}

	/**
	 * The two arch windows of a plaza look placed at origin: each keystone chiseled sandstone, the six panes under it
	 * glass, and the run's two end panes joined by the game outward, to the pilasters (the run along the front's x).
	 */
	private static List<String> arches(BlockGetter level, BlockPos origin, Rotation rotation, Mirror mirror, String where) {
		List<String> out = new ArrayList<>();
		for (BlockPos key : KEYSTONES) {
			BlockPos at = origin.offset(StructureTemplate.transform(key, mirror, rotation, BlockPos.ZERO));
			if (!level.getBlockState(at).is(Blocks.CHISELED_SANDSTONE)) {
				out.add(where + ": the keystone " + key.toShortString() + " is " + level.getBlockState(at).getBlock());
			}
			for (int dx = -1; dx <= 1; dx++) {
				for (int y = 1; y <= 2; y++) {
					BlockPos local = new BlockPos(key.getX() + dx, y, key.getZ());
					BlockPos p = origin.offset(StructureTemplate.transform(local, mirror, rotation, BlockPos.ZERO));
					if (!glass(level.getBlockState(p))) {
						out.add(where + ": no glass at " + local.toShortString() + " under the keystone: " + level.getBlockState(p).getBlock());
						continue;
					}
					// Joined both ways along the run: to its neighbour pane or, at the run's ends, the pilaster
					BlockPos east = origin.offset(StructureTemplate.transform(local.east(), mirror, rotation, BlockPos.ZERO));
					BlockPos west = origin.offset(StructureTemplate.transform(local.west(), mirror, rotation, BlockPos.ZERO));
					boolean[] j = joins(level, p);
					Direction toEast = Direction.fromDelta(east.getX() - p.getX(), 0, east.getZ() - p.getZ());
					Direction toWest = Direction.fromDelta(west.getX() - p.getX(), 0, west.getZ() - p.getZ());
					if (!j[toEast.get2DDataValue()] || !j[toWest.get2DDataValue()]) {
						out.add(where + ": the arch pane at " + local.toShortString() + " isn't joined at both ends (" + level.getBlockState(west).getBlock() + ", "
							+ level.getBlockState(east).getBlock() + ")");
					}
				}
			}
		}
		return out;
	}

	private static void assertBuiltGlass(GameTestHelper helper, Build b, String what) {
		ServerLevel level = helper.getLevel();
		BuildSite site = BuildSiteManager.get(level).get(b.id());
		helper.assertTrue(site == null, site == null ? "" : "the " + what + " is still building: " + site.stage() + " " + site.status() + " step "
			+ site.cursor() + ", " + site.placed() + " placed, " + site.detail() + (b.builder().isAlive() ? "" : " (builder dead)"));
		int panes = 0;
		List<String> problems = new ArrayList<>();
		for (BlockPos p : BlockPos.betweenClosed(b.box().minX(), b.box().minY(), b.box().minZ(), b.box().maxX(), b.box().maxY(), b.box().maxZ())) {
			BlockState state = level.getBlockState(p);
			String rel = p.subtract(b.origin()).toShortString();
			if (state.getBlock() instanceof CrossCollisionBlock || state.getBlock() instanceof WallBlock) {
				BlockState vanilla = Block.updateFromNeighbourShapes(state, level, p);
				if (vanilla != state) {
					problems.add(state.getBlock() + " at " + rel + " stands joined otherwise than the game joins it");
				}
			}
			if (glass(state)) {
				panes++;
				String why = endsInAir(level, p.immutable());
				if (why != null) {
					problems.add(state.getBlock() + " at " + rel + " " + why);
				}
			}
		}
		problems.addAll(arches(level, b.origin(), b.rotation(), b.mirror(), what));
		LOGGER.info("QA B656: the built {} has {} panes and bars, {} problems", what, panes, problems.size());
		helper.assertTrue(panes >= 12, "only " + panes + " panes in the built " + what);
		helper.assertTrue(problems.isEmpty(), "B656: the built " + what + "'s glass: " + problems.size() + " wrong, e.g. "
			+ String.join("; ", problems.subList(0, Math.min(5, problems.size()))));
	}

	private record Build(UUID id, BlockPos origin, BoundingBox box, Rotation rotation, Mirror mirror, Villager builder) {
	}

	/** A builder at its bench given the look in that turn, its box's low corner at corner (free materials, no help). */
	private static Build start(GameTestHelper helper, StarterBlueprints.Entry entry, Rotation rotation, Mirror mirror, BlockPos corner) {
		Leftovers.clear(helper);
		ServerLevel level = helper.getLevel();
		helper.setDayTime(2000);
		int delay = level.getGameRules().getInt(ModGameRules.BUILD_DELAY);
		boolean help = level.getGameRules().getBoolean(ModGameRules.BUILDERS_HELP);
		boolean free = level.getGameRules().getBoolean(ModGameRules.FREE_MATERIALS);
		Leftovers.after(helper, () -> {
			level.getGameRules().getRule(ModGameRules.BUILD_DELAY).set(delay, level.getServer());
			level.getGameRules().getRule(ModGameRules.BUILDERS_HELP).set(help, level.getServer());
			level.getGameRules().getRule(ModGameRules.FREE_MATERIALS).set(free, level.getServer());
		});
		level.getGameRules().getRule(ModGameRules.BUILD_DELAY).set(1, level.getServer());
		level.getGameRules().getRule(ModGameRules.BUILDERS_HELP).set(false, level.getServer());
		level.getGameRules().getRule(ModGameRules.FREE_MATERIALS).set(true, level.getServer());
		helper.setBlock(BENCH, ModBlocks.BUILDERS_BENCH);
		Villager villager = helper.spawn(EntityType.VILLAGER, new BlockPos(3, 2, 3));
		Builders.employ(level, villager, helper.absolutePos(BENCH));
		BlockPos want = helper.absolutePos(corner);
		BoundingBox trial = BlueprintOutline.bounds(new BlueprintData.Placement(level.dimension().location(), want, rotation, mirror), entry.size());
		BlockPos origin = want.offset(want.getX() - trial.minX(), 0, want.getZ() - trial.minZ());
		BlueprintData.Placement placement = new BlueprintData.Placement(level.dimension().location(), origin, rotation, mirror);
		BoundingBox box = BlueprintOutline.bounds(placement, entry.size());
		BlockPos far = helper.relativePos(new BlockPos(box.maxX(), box.maxY(), box.maxZ()));
		helper.assertTrue(far.getX() < 30 && far.getZ() < 30 && far.getY() < 30, entry.id() + " doesn't fit the test area: " + box);
		BuildSite site = Builders.start(level, villager, null, entry.id(), placement);
		helper.assertTrue(site != null && site.plan(level) != null, entry.id() + " didn't start");
		return new Build(site.id(), origin, box, rotation, mirror, villager);
	}
}
