package io.github.jcondedata.aliveworkplace.test;

import io.github.jcondedata.aliveworkplace.blueprint.BlueprintLibrary;
import io.github.jcondedata.aliveworkplace.blueprint.StarterBlueprints;
import io.github.jcondedata.aliveworkplace.build.BuildPlan;
import io.github.jcondedata.aliveworkplace.build.BuildSite;
import io.github.jcondedata.aliveworkplace.build.BuildSiteManager;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Vec3i;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Container;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.FenceGateBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;

/**
 * QA (2026-11-22, lane b) of ROADMAP 52.2 (the rebuilt street pieces) and 52.3 (the rebuilt walls), written from the
 * items and their blind briefs ({@code tools/blueprints/briefs/}), from the player's side. Existing tests already build
 * most of 52.2 from the plan's own counts ({@code BuilderGameTests}) or from the player's list
 * ({@link QaDecorations1104fGameTests}), and the siege tests pin the walls' shapes; what this class adds:
 * <ul>
 * <li>the pieces nothing built yet from exactly what the held blueprint's Material List asks for (Park Bench, Gazebo,
 * Bandstand, Street Lamp, Palisade, Palisade Gate, Palisade Tower, Stone Wall, Wall Tower), each finished as drawn with
 * nothing skipped, plus what the brief pins on the built piece (the gate's three spruce gates, opening to a way through;
 * the tower's ladders and solid core; the lamp lighting the ground round it);</li>
 * <li>a server restart in the middle of a Palisade Tower, and a Park Bench short of one material: it waits, then
 * finishes once the player brings it;</li>
 * <li>every listed piece has a blueprint and a name in words, fits the plot its brief gives, and the Camp Kitchen
 * (a Cobblemon job) is not offered without Cobblemon.</li>
 * </ul>
 */
public class QaRebuiltStreetWalls1122bGameTests implements FabricGameTest {
	private static final String BIG = "aliveworkplace_test:big_area";
	private static final org.slf4j.Logger LOGGER = org.slf4j.LoggerFactory.getLogger("aliveworkplace_test");
	private static final BlockPos ORIGIN = new BlockPos(9, 2, 9);
	private static final List<BlockPos> CHESTS = List.of(new BlockPos(2, 2, 4), new BlockPos(4, 2, 4), new BlockPos(1, 2, 5), new BlockPos(4, 2, 1));

	/** Every piece 52.2 and 52.3 list, by id (and their second levels where the family has one). */
	private static final List<StarterBlueprints.Entry> LISTED = List.of(StarterBlueprints.STREET_LAMP, StarterBlueprints.SIGNPOST,
		StarterBlueprints.NOTICE_BOARD, StarterBlueprints.PARK_BENCH, StarterBlueprints.SUNDIAL, StarterBlueprints.HAY_CART,
		StarterBlueprints.FLOWER_PLANTERS, StarterBlueprints.LANTERN_PERGOLA, StarterBlueprints.GAZEBO, StarterBlueprints.BANDSTAND,
		StarterBlueprints.TOPIARY_GARDEN, StarterBlueprints.STAG_STATUE, StarterBlueprints.STONE_ARCHWAY, StarterBlueprints.FIRE_PIT,
		StarterBlueprints.CAMP_KITCHEN, StarterBlueprints.CAMP_KITCHEN_2, StarterBlueprints.STONE_WALL, StarterBlueprints.STONE_WALL_2,
		StarterBlueprints.WALL_TOWER, StarterBlueprints.WALL_TOWER_2, StarterBlueprints.PALISADE, StarterBlueprints.PALISADE_2,
		StarterBlueprints.PALISADE_GATE, StarterBlueprints.PALISADE_TOWER);

	// ------------------------------------------------------------------------------------------------------ helpers

	private static String where(GameTestHelper helper, BuildSite live) {
		return live == null ? "done" : live.stage() + " " + live.status() + ", skipped " + live.skipped() + ", missing " + live.missing();
	}

	/** Every cell of the built piece's box, relative to the test. */
	private static Iterable<BlockPos> box(StarterBlueprints.Entry entry) {
		Vec3i s = entry.size();
		return BlockPos.betweenClosed(ORIGIN, ORIGIN.offset(s.getX() - 1, s.getY() - 1, s.getZ() - 1));
	}

	private static Vec3i templateSize(GameTestHelper helper, StarterBlueprints.Entry entry) {
		StructureTemplate t = helper.getLevel().getStructureManager().get(entry.id())
			.orElseThrow(() -> new GameTestAssertException(entry.id() + " has no structure file"));
		return t.getSize();
	}

	/** Builds {@code entry} from exactly the player's list, then runs {@code after} on the finished piece. */
	private static void buildExactlyThen(GameTestHelper helper, StarterBlueprints.Entry entry, Runnable after) {
		Leftovers.clear(helper);
		QaDecorations1104fGameTests.Job job = QaDecorations1104fGameTests.exact(helper, entry, ORIGIN);
		helper.succeedWhen(() -> {
			QaDecorations1104fGameTests.assertExactlyBuilt(helper, job);
			after.run();
		});
	}

	// ------------------------------------------------------------------- 1. exactly what the held list asks for

	//$ gametest_ticks_batch BIG '12000' '"qaStreet1122b_parkBenchFromExactlyItsList"'
	@GameTest(template = BIG, timeoutTicks = 12000, batch = "qaStreet1122b_parkBenchFromExactlyItsList")
	public void parkBenchFromExactlyItsList(GameTestHelper helper) {
		buildExactlyThen(helper, StarterBlueprints.PARK_BENCH, () -> {
		});
	}

	//$ gametest_ticks_batch BIG '20000' '"qaStreet1122b_gazeboFromExactlyItsList"'
	@GameTest(template = BIG, timeoutTicks = 20000, batch = "qaStreet1122b_gazeboFromExactlyItsList")
	public void gazeboFromExactlyItsList(GameTestHelper helper) {
		buildExactlyThen(helper, StarterBlueprints.GAZEBO, () -> {
		});
	}

	//$ gametest_ticks_batch BIG '20000' '"qaStreet1122b_bandstandFromExactlyItsList"'
	@GameTest(template = BIG, timeoutTicks = 20000, batch = "qaStreet1122b_bandstandFromExactlyItsList")
	public void bandstandFromExactlyItsList(GameTestHelper helper) {
		buildExactlyThen(helper, StarterBlueprints.BANDSTAND, () -> {
		});
	}

	/**
	 * The Street Lamp from exactly its list, and it is a lamp: a light of at least 10 in it, and no cell of the ground
	 * right round its foot left at block light 0 (where mobs spawn at night).
	 */
	//$ gametest_ticks_batch BIG '12000' '"qaStreet1122b_streetLampFromExactlyItsListLightsItsFoot"'
	@GameTest(template = BIG, timeoutTicks = 12000, batch = "qaStreet1122b_streetLampFromExactlyItsListLightsItsFoot")
	public void streetLampFromExactlyItsListLightsItsFoot(GameTestHelper helper) {
		StarterBlueprints.Entry lamp = StarterBlueprints.STREET_LAMP;
		buildExactlyThen(helper, lamp, () -> {
			int brightest = 0;
			for (BlockPos p : box(lamp)) {
				brightest = Math.max(brightest, helper.getBlockState(p).getLightEmission());
			}
			helper.assertTrue(brightest >= 10, "the built street lamp's brightest block gives light " + brightest);
			Vec3i s = lamp.size();
			for (int x = -1; x <= s.getX(); x++) {
				for (int z = -1; z <= s.getZ(); z++) {
					if (x >= 0 && x < s.getX() && z >= 0 && z < s.getZ()) {
						continue;
					}
					BlockPos ground = ORIGIN.offset(x, 0, z);
					int light = helper.getLevel().getBrightness(LightLayer.BLOCK, helper.absolutePos(ground));
					helper.assertTrue(light > 0, "the ground beside the street lamp at " + ground + " is dark (block light 0)");
				}
			}
		});
	}

	//$ gametest_ticks_batch BIG '12000' '"qaStreet1122b_palisadeFromExactlyItsList"'
	@GameTest(template = BIG, timeoutTicks = 12000, batch = "qaStreet1122b_palisadeFromExactlyItsList")
	public void palisadeFromExactlyItsList(GameTestHelper helper) {
		buildExactlyThen(helper, StarterBlueprints.PALISADE, () -> {
		});
	}

	/**
	 * The Palisade Gate from exactly its list. Its brief pins exactly three spruce fence gates, at (2..4, 0, 1), each
	 * shut across the way (its panel along x), and no other gate block; opened, each gateway cell and the one above it
	 * are free to walk through.
	 */
	//$ gametest_ticks_batch BIG '12000' '"qaStreet1122b_palisadeGateFromExactlyItsListOpensAWayThrough"'
	@GameTest(template = BIG, timeoutTicks = 12000, batch = "qaStreet1122b_palisadeGateFromExactlyItsListOpensAWayThrough")
	public void palisadeGateFromExactlyItsListOpensAWayThrough(GameTestHelper helper) {
		StarterBlueprints.Entry gate = StarterBlueprints.PALISADE_GATE;
		buildExactlyThen(helper, gate, () -> {
			List<BlockPos> gates = new ArrayList<>();
			for (BlockPos p : box(gate)) {
				if (helper.getBlockState(p).getBlock() instanceof FenceGateBlock) {
					gates.add(p.subtract(ORIGIN).immutable());
				}
			}
			helper.assertTrue(gates.equals(List.of(new BlockPos(2, 0, 1), new BlockPos(3, 0, 1), new BlockPos(4, 0, 1))),
				"the built palisade gate's fence gates stand at " + gates + ", wanted exactly (2..4, 0, 1)");
			ServerLevel level = helper.getLevel();
			for (int x = 2; x <= 4; x++) {
				BlockPos cell = ORIGIN.offset(x, 0, 1);
				BlockState shut = helper.getBlockState(cell);
				helper.assertTrue(shut.is(Blocks.SPRUCE_FENCE_GATE), "the gate at " + cell + " is " + shut);
				helper.assertTrue(!shut.getValue(FenceGateBlock.OPEN), "the gate at " + cell + " was built open");
				helper.assertTrue(shut.getValue(FenceGateBlock.FACING).getAxis() == Direction.Axis.Z,
					"the gate at " + cell + " faces " + shut.getValue(FenceGateBlock.FACING) + ": its panel doesn't run across the way");
				helper.assertTrue(!shut.getCollisionShape(level, helper.absolutePos(cell)).isEmpty(), "the shut gate at " + cell + " can be walked through");
				helper.setBlock(cell, shut.setValue(FenceGateBlock.OPEN, true));
			}
			for (int x = 2; x <= 4; x++) {
				for (int y = 0; y <= 1; y++) {
					BlockPos cell = ORIGIN.offset(x, y, 1);
					helper.assertTrue(helper.getBlockState(cell).getCollisionShape(level, helper.absolutePos(cell)).isEmpty(),
						"with the gates open, " + cell + " (" + helper.getBlockState(cell) + ") still blocks the way through");
				}
			}
		});
	}

	/**
	 * The Palisade Tower from exactly its list. Its brief: 2 to 16 ladders, one at (2, 5, 3), each hanging on something
	 * solid; something solid in the centre column (2, y >= 1, 2).
	 */
	//$ gametest_ticks_batch BIG '12000' '"qaStreet1122b_palisadeTowerFromExactlyItsListHasItsLaddersAndCore"'
	@GameTest(template = BIG, timeoutTicks = 12000, batch = "qaStreet1122b_palisadeTowerFromExactlyItsListHasItsLaddersAndCore")
	public void palisadeTowerFromExactlyItsListHasItsLaddersAndCore(GameTestHelper helper) {
		StarterBlueprints.Entry tower = StarterBlueprints.PALISADE_TOWER;
		buildExactlyThen(helper, tower, () -> {
			int ladders = 0;
			for (BlockPos p : box(tower)) {
				BlockState s = helper.getBlockState(p);
				if (s.is(Blocks.LADDER)) {
					ladders++;
					helper.assertTrue(s.canSurvive(helper.getLevel(), helper.absolutePos(p)), "the ladder at " + p.subtract(ORIGIN) + " hangs on nothing");
				}
			}
			helper.assertTrue(ladders >= 2 && ladders <= 16, "the built palisade tower has " + ladders + " ladders, wanted 2 to 16");
			helper.assertTrue(helper.getBlockState(ORIGIN.offset(2, 5, 3)).is(Blocks.LADDER), "no ladder at (2, 5, 3) of the palisade tower");
			boolean core = false;
			for (int y = 1; y < tower.size().getY(); y++) {
				BlockState s = helper.getBlockState(ORIGIN.offset(2, y, 2));
				core |= !s.isAir() && s.getFluidState().isEmpty() && !s.canBeReplaced();
			}
			helper.assertTrue(core, "the palisade tower's centre column (2, y, 2) is empty above y = 0");
		});
	}

	/** The Stone Wall from exactly its list: no gate block of any kind in it (its brief), and the outer face's ladder column. */
	//$ gametest_ticks_batch BIG '12000' '"qaStreet1122b_stoneWallFromExactlyItsListHasNoGate"'
	@GameTest(template = BIG, timeoutTicks = 12000, batch = "qaStreet1122b_stoneWallFromExactlyItsListHasNoGate")
	public void stoneWallFromExactlyItsListHasNoGate(GameTestHelper helper) {
		StarterBlueprints.Entry wall = StarterBlueprints.STONE_WALL;
		buildExactlyThen(helper, wall, () -> {
			for (BlockPos p : box(wall)) {
				BlockState s = helper.getBlockState(p);
				helper.assertTrue(!(s.getBlock() instanceof FenceGateBlock) && !s.is(net.minecraft.tags.BlockTags.DOORS) && !s.is(Blocks.IRON_BARS),
					"the built stone wall has a gate block at " + p.subtract(ORIGIN) + ": " + s);
			}
			ServerLevel level = helper.getLevel();
			for (int x = 0; x <= 6; x++) {
				for (int y = 0; y <= 3; y++) {
					BlockPos p = ORIGIN.offset(x, y, 2);
					helper.assertTrue(!helper.getBlockState(p).getCollisionShape(level, helper.absolutePos(p)).isEmpty(),
						"the stone wall's outer face has a gap at " + p.subtract(ORIGIN));
				}
			}
		});
	}

	//$ gametest_ticks_batch BIG '16000' '"qaStreet1122b_wallTowerFromExactlyItsList"'
	@GameTest(template = BIG, timeoutTicks = 16000, batch = "qaStreet1122b_wallTowerFromExactlyItsList")
	public void wallTowerFromExactlyItsList(GameTestHelper helper) {
		buildExactlyThen(helper, StarterBlueprints.WALL_TOWER, () -> {
		});
	}

	// ------------------------------------------------------------------------------- 2. restarts and short lists

	/**
	 * A server restart in the middle of a Palisade Tower (the site and the builder saved and loaded, once it is in its
	 * STRUCTURE stage): the builder carries on and it finishes as drawn with nothing skipped.
	 */
	//$ gametest_ticks_batch BIG '14000' '"qaStreet1122b_palisadeTowerResumesAfterARestartMidBuild"'
	@GameTest(template = BIG, timeoutTicks = 14000, batch = "qaStreet1122b_palisadeTowerResumesAfterARestartMidBuild")
	public void palisadeTowerResumesAfterARestartMidBuild(GameTestHelper helper) {
		Leftovers.clear(helper);
		QaDecorations1104fGameTests.Job job = QaDecorations1104fGameTests.exact(helper, StarterBlueprints.PALISADE_TOWER, ORIGIN);
		ServerLevel level = helper.getLevel();
		boolean[] restarted = {false};
		int[] skipped = {0};
		helper.onEachTick(() -> {
			BuildSite live = BuildSiteManager.get(level).get(job.site().id());
			if (live != null) {
				skipped[0] = live.skipped();
			}
			if (!restarted[0] && live != null && live.stage() == BuildPlan.Stage.STRUCTURE && live.placed() > 4) {
				restarted[0] = true;
				var registries = level.registryAccess();
				level.getDataStorage().set(BuildSiteManager.NAME, BuildSiteManager.load(BuildSiteManager.get(level).save(new CompoundTag(), registries), registries));
				Villager old = job.villager();
				CompoundTag tag = new CompoundTag();
				helper.assertTrue(old.save(tag), "the builder wasn't saved");
				old.discard();
				Entity loaded = EntityType.loadEntityRecursive(tag, level, e -> e);
				helper.assertTrue(loaded instanceof Villager, "the builder didn't load as a villager: " + loaded);
				level.addFreshEntity(loaded);
				LOGGER.info("qa street 1122b: restarted the palisade tower at {}", where(helper, BuildSiteManager.get(level).get(job.site().id())));
			}
		});
		helper.succeedWhen(() -> {
			helper.assertTrue(restarted[0], "the palisade tower never reached its STRUCTURE stage with blocks placed");
			BuildSite live = BuildSiteManager.get(level).get(job.site().id());
			helper.assertTrue(live == null, "after the restart the palisade tower is still building: " + where(helper, live));
			List<BlockPos> unfinished = job.plan().unfinished(level);
			helper.assertTrue(unfinished.isEmpty(), "after the restart " + unfinished.size() + " block(s) of the palisade tower are not as drawn");
			helper.assertTrue(skipped[0] == 0, "after the restart the palisade tower skipped " + skipped[0] + " block(s)");
		});
	}

	/**
	 * The Park Bench with one material of its walls left out of the chests: it doesn't finish, its site says it is
	 * missing that item; once the player puts exactly the missing count in a chest it finishes as drawn, nothing skipped.
	 */
	//$ gametest_ticks_batch BIG '16000' '"qaStreet1122b_parkBenchShortOfOneMaterialWaitsThenFinishes"'
	@GameTest(template = BIG, timeoutTicks = 16000, batch = "qaStreet1122b_parkBenchShortOfOneMaterialWaitsThenFinishes")
	public void parkBenchShortOfOneMaterialWaitsThenFinishes(GameTestHelper helper) {
		Leftovers.clear(helper);
		QaDecorations1104fGameTests.Job job = QaDecorations1104fGameTests.exact(helper, StarterBlueprints.PARK_BENCH, ORIGIN);
		ServerLevel level = helper.getLevel();
		// the STRUCTURE item the list asks most of
		Map<Item, Integer> structure = new LinkedHashMap<>();
		for (BuildPlan.Step s : job.plan().steps(BuildPlan.Stage.STRUCTURE)) {
			Item item = s.state().getBlock().asItem();
			if (job.list().containsKey(item)) {
				structure.merge(item, 1, Integer::sum);
			}
		}
		helper.assertTrue(!structure.isEmpty(), "the park bench's list has nothing its STRUCTURE stage places");
		Item short_ = structure.entrySet().stream().max(Map.Entry.comparingByValue()).orElseThrow().getKey();
		int count = job.list().get(short_);
		for (BlockPos chest : CHESTS) {
			Container c = helper.getBlockEntity(chest);
			for (int slot = 0; slot < c.getContainerSize(); slot++) {
				if (c.getItem(slot).is(short_)) {
					c.setItem(slot, ItemStack.EMPTY);
				}
			}
		}
		LOGGER.info("qa street 1122b: park bench short of {} x{}", short_, count);
		boolean[] sawWait = {false};
		boolean[] restocked = {false};
		int[] skipped = {0};
		helper.onEachTick(() -> {
			BuildSite live = BuildSiteManager.get(level).get(job.site().id());
			if (live != null) {
				skipped[0] = live.skipped();
				if (!restocked[0] && live.missing().containsKey(short_)) {
					sawWait[0] = true;
				}
			}
			if (!restocked[0] && helper.getTick() >= 4000) {
				helper.assertTrue(live != null, "the park bench finished without its " + short_ + " (" + count + " asked for)");
				helper.assertTrue(sawWait[0], "the park bench never said it was missing " + short_ + ": " + where(helper, live));
				restocked[0] = true;
				Container c = helper.getBlockEntity(CHESTS.get(0));
				int left = count;
				for (int slot = 0; slot < c.getContainerSize() && left > 0; slot++) {
					if (c.getItem(slot).isEmpty()) {
						int take = Math.min(left, short_.getDefaultMaxStackSize());
						c.setItem(slot, new ItemStack(short_, take));
						left -= take;
					}
				}
				helper.assertTrue(left == 0, "setup: no room in the chest to restock");
			}
		});
		helper.succeedWhen(() -> {
			helper.assertTrue(restocked[0], "not restocked yet");
			BuildSite live = BuildSiteManager.get(level).get(job.site().id());
			helper.assertTrue(live == null, "restocked, the park bench is still building: " + where(helper, live));
			List<BlockPos> unfinished = job.plan().unfinished(level);
			helper.assertTrue(unfinished.isEmpty(), "restocked, " + unfinished.size() + " block(s) of the park bench are not as drawn");
			helper.assertTrue(skipped[0] == 0, "restocked, the park bench skipped " + skipped[0] + " block(s)");
		});
	}

	// --------------------------------------------------------------------- 3. the list, its words and its plots

	/**
	 * Every piece 52.2 and 52.3 list loads as a blueprint (an old save's site naming it finds its drawing), its
	 * structure file is the size its entry declares, and its name reads in words in English.
	 */
	//$ gametest 'FabricGameTest.EMPTY_STRUCTURE'
	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
	public void everyListedPieceHasABlueprintAndAName(GameTestHelper helper) {
		List<String> bad = new ArrayList<>();
		for (StarterBlueprints.Entry e : LISTED) {
			if (BlueprintLibrary.get(helper.getLevel(), e.id()).isEmpty()) {
				bad.add(e.id() + ": no blueprint");
			}
			Vec3i size = templateSize(helper, e);
			if (!size.equals(e.size())) {
				bad.add(e.id() + ": drawn " + size.toShortString() + ", declared " + e.size().toShortString());
			}
			String name = io.github.jcondedata.aliveworkplace.blueprint.Blueprints.displayName(e.id()).getString();
			if (name.isBlank() || name.contains(".") || name.contains("_")) {
				bad.add(e.id() + ": its name reads \"" + name + "\"");
			}
		}
		helper.assertTrue(bad.isEmpty(), "listed pieces: " + bad);
		helper.succeed();
	}

	/**
	 * Each piece fits the plot its brief gives: Park Bench at most 7 x 7 across; Gazebo at most 13 x 13 and 16 tall;
	 * Palisade Gate exactly 7 wide and 2 deep; Stone Wall 7 wide and 3 deep; the Palisade Tower fits inside the Wall
	 * Tower that replaces it; each level II at least as big as its level I on every axis; the Camp Kitchen II within 20 x
	 * 27 x 24.
	 */
	//$ gametest 'FabricGameTest.EMPTY_STRUCTURE'
	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
	public void everyPieceFitsThePlotItsBriefGives(GameTestHelper helper) {
		List<String> bad = new ArrayList<>();
		Vec3i bench = templateSize(helper, StarterBlueprints.PARK_BENCH);
		if (bench.getX() > 7 || bench.getZ() > 7) {
			bad.add("park bench " + bench.toShortString());
		}
		Vec3i gazebo = templateSize(helper, StarterBlueprints.GAZEBO);
		if (gazebo.getX() > 13 || gazebo.getZ() > 13 || gazebo.getY() > 16) {
			bad.add("gazebo " + gazebo.toShortString());
		}
		Vec3i gate = templateSize(helper, StarterBlueprints.PALISADE_GATE);
		if (gate.getX() != 7 || gate.getZ() != 2) {
			bad.add("palisade gate " + gate.toShortString());
		}
		Vec3i wall = templateSize(helper, StarterBlueprints.STONE_WALL);
		if (wall.getX() != 7 || wall.getZ() != 3) {
			bad.add("stone wall " + wall.toShortString());
		}
		Vec3i ptower = templateSize(helper, StarterBlueprints.PALISADE_TOWER);
		Vec3i wtower = templateSize(helper, StarterBlueprints.WALL_TOWER);
		if (ptower.getX() > wtower.getX() || ptower.getZ() > wtower.getZ()) {
			bad.add("palisade tower " + ptower.toShortString() + " doesn't fit in the wall tower " + wtower.toShortString());
		}
		Vec3i kitchen2 = templateSize(helper, StarterBlueprints.CAMP_KITCHEN_2);
		if (kitchen2.getX() > 20 || kitchen2.getY() > 27 || kitchen2.getZ() > 24) {
			bad.add("camp kitchen II " + kitchen2.toShortString());
		}
		for (StarterBlueprints.Entry[] pair : new StarterBlueprints.Entry[][]{{StarterBlueprints.CAMP_KITCHEN, StarterBlueprints.CAMP_KITCHEN_2},
			{StarterBlueprints.STONE_WALL, StarterBlueprints.STONE_WALL_2}, {StarterBlueprints.WALL_TOWER, StarterBlueprints.WALL_TOWER_2},
			{StarterBlueprints.PALISADE, StarterBlueprints.PALISADE_2}}) {
			Vec3i one = templateSize(helper, pair[0]);
			Vec3i two = templateSize(helper, pair[1]);
			if (two.getX() < one.getX() || two.getY() < one.getY() || two.getZ() < one.getZ()) {
				bad.add(pair[1].id().getPath() + " " + two.toShortString() + " is smaller than " + pair[0].id().getPath() + " " + one.toShortString());
			}
		}
		helper.assertTrue(bad.isEmpty(), "pieces outside their briefs' plots: " + bad);
		helper.succeed();
	}

	/** The Camp Kitchen is a Cobblemon job: without Cobblemon (this suite) neither level is in the blueprint library's list. */
	//$ gametest 'FabricGameTest.EMPTY_STRUCTURE'
	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
	public void campKitchenIsNotOfferedWithoutCobblemon(GameTestHelper helper) {
		List<ResourceLocation> listed = BlueprintLibrary.list(helper.getLevel().getServer(), false);
		helper.assertTrue(!listed.contains(StarterBlueprints.CAMP_KITCHEN.id()) && !listed.contains(StarterBlueprints.CAMP_KITCHEN_2.id()),
			"without Cobblemon the blueprint library lists the Camp Kitchen");
		helper.assertTrue(listed.contains(StarterBlueprints.PALISADE_TOWER.id()) && listed.contains(StarterBlueprints.GAZEBO.id()),
			"the blueprint library doesn't list the Palisade Tower or the Gazebo");
		helper.succeed();
	}
}
