package io.github.jcondedata.aliveworkplace.compat;

import io.github.jcondedata.aliveworkplace.blueprint.BlueprintData;
import io.github.jcondedata.aliveworkplace.blueprint.StarterBlueprints;
import io.github.jcondedata.aliveworkplace.build.BuildPlan;
import io.github.jcondedata.aliveworkplace.build.BuildSite;
import io.github.jcondedata.aliveworkplace.build.BuildSiteManager;
import io.github.jcondedata.aliveworkplace.build.Builders;
import io.github.jcondedata.aliveworkplace.mc.Nbt;
import io.github.jcondedata.aliveworkplace.mechanic.CreateBuilds;
import io.github.jcondedata.aliveworkplace.registry.ModBlocks;
import io.github.jcondedata.aliveworkplace.registry.ModGameRules;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Container;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;

/**
 * QA (lane E, 2026-10-24), ROADMAP 39.4 and 39.5 from the player's side: a player turns or flips a blueprint at the
 * Blueprint Table before handing it to a builder (the table's rotate and mirror), and a Create build must still be
 * built "with each block's axis and facing kept" and turn. {@code CreateBuildsCompatTests} builds every Create build
 * only as drawn, and {@code ContraptionCompatTests} turns its small test contraption a quarter, never flips it: a flipped
 * build turns a belt end for end, so its segments' saved place on the belt can't be copied (Contraptions.blockData
 * leaves it to Create). Also: {@code createBuilds} switched off while a Create build is under way doesn't strand it.
 * Each build comes from barrels holding exactly its materials; done means every block as drawn, nothing skipped, the
 * job block a job site and the machine turning (its {@code Speed} read from block entity data by name).
 */
public class QaCreateBuildsTurnedCompatTests implements FabricGameTest {
	private static final String HUGE_AREA = "aliveworkplace_compat:huge_area";

	/** The Workshop flipped left to right: its belt runs the other way and still drives the press over the depot. */
	//$ gametest_ticks_batch HUGE_AREA '30000' '"qa_workshop_mirrored"'
	@GameTest(template = HUGE_AREA, timeoutTicks = 30000, batch = "qa_workshop_mirrored")
	public void aFlippedWorkshopIsBuiltAndItsBeltDrivesThePress(GameTestHelper helper) {
		Run run = start(helper, StarterBlueprints.WORKSHOP, Rotation.NONE, Mirror.FRONT_BACK);
		helper.succeedWhen(() -> assertBuiltAndTurning(helper, run, new BlockPos(10, 1, 6), "minecraft:blast_furnace",
			List.of(new BlockPos(4, 3, 2), new BlockPos(4, 1, 4))));
	}

	/** The Workshop turned round (facing the other way): built whole, its belt and press turning. */
	//$ gametest_ticks_batch HUGE_AREA '30000' '"qa_workshop_180"'
	@GameTest(template = HUGE_AREA, timeoutTicks = 30000, batch = "qa_workshop_180")
	public void aWorkshopTurnedRoundIsBuiltAndItsBeltDrivesThePress(GameTestHelper helper) {
		Run run = start(helper, StarterBlueprints.WORKSHOP, Rotation.CLOCKWISE_180, Mirror.NONE);
		helper.succeedWhen(() -> assertBuiltAndTurning(helper, run, new BlockPos(10, 1, 6), "minecraft:blast_furnace",
			List.of(new BlockPos(4, 3, 2), new BlockPos(4, 1, 4))));
	}

	/** The Watermill flipped left to right: the wheel still meets the falling water and turns the millstone. */
	//$ gametest_ticks_batch HUGE_AREA '30000' '"qa_watermill_mirrored"'
	@GameTest(template = HUGE_AREA, timeoutTicks = 30000, batch = "qa_watermill_mirrored")
	public void aFlippedWatermillIsBuiltAndItTurns(GameTestHelper helper) {
		Run run = start(helper, StarterBlueprints.WATERMILL, Rotation.NONE, Mirror.FRONT_BACK);
		helper.succeedWhen(() -> assertBuiltAndTurning(helper, run, new BlockPos(7, 1, 2), "minecraft:composter",
			List.of(new BlockPos(4, 2, 4))));
	}

	/**
	 * The Crushing Mill (39.8) flipped left to right: both wheels still turn into the gap (the opposite ways at one speed),
	 * so Create's crusher between them stands valid. Flipped wheels turning out of the gap would leave it idle.
	 */
	//$ gametest_ticks_batch HUGE_AREA '30000' '"qa_crushing_mill_mirrored"'
	@GameTest(template = HUGE_AREA, timeoutTicks = 30000, batch = "qa_crushing_mill_mirrored")
	public void aFlippedCrushingMillStillCrushes(GameTestHelper helper) {
		Run run = start(helper, StarterBlueprints.CRUSHING_MILL, Rotation.NONE, Mirror.FRONT_BACK);
		helper.succeedWhen(() -> {
			assertBuiltAndTurning(helper, run, new BlockPos(9, 1, 2), "minecraft:blast_furnace", List.of(new BlockPos(5, 2, 5), new BlockPos(7, 2, 5)));
			float a = speed(run, new BlockPos(5, 2, 5));
			float b = speed(run, new BlockPos(7, 2, 5));
			helper.assertTrue(a == -b, "the crushing wheels don't turn the opposite ways (" + a + ", " + b + ")");
			net.minecraft.world.level.block.state.BlockState crusher = run.level().getBlockState(run.world(new BlockPos(6, 2, 5)));
			String valid = crusher.getProperties().stream().filter(p -> p.getName().equals("valid")).findFirst()
				.map(p -> crusher.getValue(p).toString()).orElse("?");
			helper.assertTrue("true".equals(valid), "the crusher between the flipped wheels isn't working: " + crusher);
		});
	}

	/** The Brass Foundry (39.8) turned a quarter: its gear train still gets the mixer over the basin up to mixing speed (30). */
	//$ gametest_ticks_batch HUGE_AREA '30000' '"qa_brass_foundry_90"'
	@GameTest(template = HUGE_AREA, timeoutTicks = 30000, batch = "qa_brass_foundry_90")
	public void aBrassFoundryTurnedAQuarterStillMixes(GameTestHelper helper) {
		Run run = start(helper, StarterBlueprints.BRASS_FOUNDRY, Rotation.CLOCKWISE_90, Mirror.NONE);
		helper.succeedWhen(() -> {
			assertBuiltAndTurning(helper, run, new BlockPos(9, 1, 2), "minecraft:smoker", List.of(new BlockPos(8, 4, 8), new BlockPos(4, 1, 4)));
			float mixer = speed(run, new BlockPos(8, 4, 8));
			helper.assertTrue(Math.abs(mixer) >= 30, "the mixer turns at " + mixer + " RPM, too slow to mix (30)");
		});
	}

	/** The Sawmill (39.8) flipped left to right: the belt still runs through both saws to the depot, both saws turning. */
	//$ gametest_ticks_batch HUGE_AREA '30000' '"qa_sawmill_mirrored"'
	@GameTest(template = HUGE_AREA, timeoutTicks = 30000, batch = "qa_sawmill_mirrored")
	public void aFlippedSawmillIsBuiltAndBothSawsTurn(GameTestHelper helper) {
		Run run = start(helper, StarterBlueprints.SAWMILL, Rotation.NONE, Mirror.FRONT_BACK);
		helper.succeedWhen(() -> assertBuiltAndTurning(helper, run, new BlockPos(10, 1, 2), "minecraft:fletching_table",
			List.of(new BlockPos(4, 1, 4), new BlockPos(4, 1, 3), new BlockPos(4, 1, 6))));
	}

	/** The Pump House (39.8) turned a quarter the other way: the wheel's cogwheel still turns the pump. */
	//$ gametest_ticks_batch HUGE_AREA '30000' '"qa_pump_house_270"'
	@GameTest(template = HUGE_AREA, timeoutTicks = 30000, batch = "qa_pump_house_270")
	public void aPumpHouseTurnedTheOtherWayStillPumps(GameTestHelper helper) {
		Run run = start(helper, StarterBlueprints.PUMP_HOUSE, Rotation.COUNTERCLOCKWISE_90, Mirror.NONE);
		helper.succeedWhen(() -> assertBuiltAndTurning(helper, run, new BlockPos(10, 1, 2), "minecraft:cauldron",
			List.of(new BlockPos(6, 1, 4), new BlockPos(6, 1, 5))));
	}

	/**
	 * {@code createBuilds} switched off while a builder is half way up the Watermill (a server owner trying the switch):
	 * the build already under way is finished whole and turns, as any started build is; the switch only takes the Create
	 * builds off the table.
	 */
	//$ gametest_ticks_batch HUGE_AREA '30000' '"qa_watermill_switch_mid"'
	@GameTest(template = HUGE_AREA, timeoutTicks = 30000, batch = "qa_watermill_switch_mid")
	public void aWatermillUnderWayWhenTheSwitchGoesOffIsFinished(GameTestHelper helper) {
		boolean was = CreateBuilds.ENABLED;
		Run run = start(helper, StarterBlueprints.WATERMILL, Rotation.NONE, Mirror.NONE);
		boolean[] switched = {false};
		helper.onEachTick(() -> {
			if (!switched[0] && run.site.stage() == BuildPlan.Stage.STRUCTURE && run.site.cursor() > 10) {
				switched[0] = true;
				CreateBuilds.ENABLED = false;
			}
		});
		helper.succeedWhen(() -> {
			if (BuildSiteManager.get(run.level).get(run.site.id()) == null) {
				CreateBuilds.ENABLED = was; // the build is over: the switch back as the other tests expect it
			}
			helper.assertTrue(switched[0], "the switch never went off mid-build (stage " + run.site.stage() + ")");
			assertBuiltAndTurning(helper, run, new BlockPos(7, 1, 2), "minecraft:composter", List.of(new BlockPos(4, 2, 4)));
		});
	}

	/** A build under way: where it stands and how it's turned. */
	private record Run(ServerLevel level, StarterBlueprints.Entry entry, BuildSite site, BuildPlan plan, BlueprintData.Placement placement) {
		/** Where the template spot {@code spot} is in the world. */
		BlockPos world(BlockPos spot) {
			return placement.origin().offset(StructureTemplate.transform(spot, placement.mirror(), placement.rotation(), BlockPos.ZERO));
		}
	}

	/**
	 * Starts a builder on {@code entry} turned by {@code rotation} and flipped by {@code mirror}, its blocks from (9, 2, 3)
	 * on (moved in for a wide build, so its landscaping has room), with barrels holding exactly its materials.
	 */
	private static Run start(GameTestHelper helper, StarterBlueprints.Entry entry, Rotation rotation, Mirror mirror) {
		ServerLevel level = helper.getLevel();
		helper.setDayTime(2000);
		level.getGameRules().getRule(ModGameRules.BUILD_DELAY).set(1, level.getServer());
		level.getGameRules().getRule(ModGameRules.BUILDERS_HELP).set(false, level.getServer());
		BlockPos bench = new BlockPos(2, 2, 2);
		helper.setBlock(bench, ModBlocks.BUILDERS_BENCH);
		Villager builder = helper.spawn(EntityType.VILLAGER, new BlockPos(3, 2, 3));
		Builders.employ(level, builder, helper.absolutePos(bench));
		// The corner the turned build's blocks start from; the origin is where the template's (0, 0, 0) lands
		BlockPos far = StructureTemplate.transform(new BlockPos(entry.size().getX() - 1, 0, entry.size().getZ() - 1), mirror, rotation, BlockPos.ZERO);
		int width = Math.abs(far.getX()) + 1;
		int depth = Math.abs(far.getZ()) + 1;
		BlockPos corner = new BlockPos(Math.min(9, 29 - width), 2, Math.min(3, 29 - depth));
		BlockPos origin = corner.offset(-Math.min(0, far.getX()), 0, -Math.min(0, far.getZ()));
		BlueprintData.Placement placement = new BlueprintData.Placement(level.dimension().location(), helper.absolutePos(origin), rotation, mirror);
		BuildSite site = Builders.start(level, builder, null, entry.id(), placement);
		BuildPlan plan = site.plan(level);
		if (plan == null) {
			throw new GameTestAssertException("no blueprint " + entry.id());
		}
		List<ItemStack> stock = new ArrayList<>();
		for (Map.Entry<Item, Integer> e : plan.materials().entrySet()) {
			for (int left = e.getValue(); left > 0; left -= e.getKey().getDefaultMaxStackSize()) {
				stock.add(new ItemStack(e.getKey(), Math.min(left, e.getKey().getDefaultMaxStackSize())));
			}
		}
		for (int i = 0; i * 27 < stock.size(); i++) {
			BlockPos barrel = new BlockPos(1 + i % 6, 2 + i / 6, 0);
			helper.setBlock(barrel, Blocks.BARREL);
			Container container = helper.getBlockEntity(barrel);
			for (int slot = 0; slot < 27 && i * 27 + slot < stock.size(); slot++) {
				container.setItem(slot, stock.get(i * 27 + slot));
			}
		}
		return new Run(level, entry, site, plan, placement);
	}

	/** Done: every block as drawn, nothing skipped, the job block a job site, and every machine listed turning. */
	private static void assertBuiltAndTurning(GameTestHelper helper, Run run, BlockPos job, String jobBlock, List<BlockPos> machines) {
		ServerLevel level = run.level();
		helper.assertTrue(BuildSiteManager.get(level).get(run.site().id()) == null,
			"still building: " + run.site().stage() + " " + run.site().status() + " missing " + run.site().missing());
		List<BlockPos> unfinished = run.plan().unfinished(level);
		helper.assertTrue(unfinished.isEmpty(), unfinished.size() + " wrong, e.g. " + unfinished.stream().limit(12)
			.map(p -> p.subtract(run.placement().origin()).toShortString() + "=" + level.getBlockState(p)).toList());
		helper.assertTrue(run.site().skipped() == 0, run.site().skipped() + " skipped");
		BlockPos jobAt = run.world(job);
		String there = BuiltInRegistries.BLOCK.getKey(level.getBlockState(jobAt).getBlock()).toString();
		helper.assertTrue(there.equals(jobBlock), "expected " + jobBlock + " at " + job.toShortString() + ", found " + there);
		helper.assertTrue(level.getPoiManager().getType(jobAt).isPresent(), "the job block at " + job.toShortString() + " is no job site");
		for (BlockPos machine : machines) {
			BlockPos at = run.world(machine);
			BlockEntity be = level.getBlockEntity(at);
			helper.assertTrue(be != null, "no block entity at the machine " + machine.toShortString() + ": " + level.getBlockState(at));
			float speed = Nbt.getFloat(be.saveWithoutMetadata(level.registryAccess()), "Speed");
			helper.assertTrue(Math.abs(speed) > 0, "the machine at " + machine.toShortString() + " (" + level.getBlockState(at)
				+ ") doesn't turn (Speed " + speed + ")");
		}
		assertBeltsWhole(helper, run);
	}

	/**
	 * Every belt segment of the build knows its belt: its saved {@code Controller} (read by name) is a belt segment of this
	 * build that is the controller, and the segments of one belt number 0 to its length - 1 once each. A belt turned end
	 * for end with its old numbering, or with none, would stop carrying items though its pulleys still turn.
	 */
	private static void assertBeltsWhole(GameTestHelper helper, Run run) {
		ServerLevel level = run.level();
		Map<BlockPos, List<Integer>> indexes = new java.util.HashMap<>();
		Map<BlockPos, Integer> lengths = new java.util.HashMap<>();
		int belts = 0;
		for (BlockPos pos : BlockPos.betweenClosed(run.world(BlockPos.ZERO).offset(-30, -2, -30), run.world(BlockPos.ZERO).offset(30, 20, 30))) {
			if (!"create:belt".equals(BuiltInRegistries.BLOCK.getKey(level.getBlockState(pos).getBlock()).toString())) {
				continue;
			}
			belts++;
			BlockEntity be = level.getBlockEntity(pos);
			helper.assertTrue(be != null, "a belt segment without its block entity at " + pos.subtract(run.placement().origin()).toShortString());
			net.minecraft.nbt.CompoundTag tag = be.saveWithoutMetadata(level.registryAccess());
			BlockPos controller = readPos(tag.get("Controller"));
			helper.assertTrue(controller != null, "the belt segment at " + pos.subtract(run.placement().origin()).toShortString()
				+ " has no controller: " + tag);
			helper.assertTrue("create:belt".equals(BuiltInRegistries.BLOCK.getKey(level.getBlockState(controller).getBlock()).toString()),
				"the belt segment at " + pos.subtract(run.placement().origin()).toShortString() + " names a controller that isn't a belt: "
					+ controller.subtract(run.placement().origin()).toShortString() + " = " + level.getBlockState(controller));
			BlockEntity head = level.getBlockEntity(controller);
			helper.assertTrue(head != null && Nbt.getBoolean(head.saveWithoutMetadata(level.registryAccess()), "IsController"),
				"the controller the belt segment at " + pos.subtract(run.placement().origin()).toShortString() + " names isn't one");
			indexes.computeIfAbsent(controller.immutable(), c -> new ArrayList<>()).add(Nbt.getInt(tag, "Index"));
			lengths.put(controller.immutable(), Nbt.getInt(tag, "Length"));
		}
		for (Map.Entry<BlockPos, List<Integer>> belt : indexes.entrySet()) {
			List<Integer> seen = belt.getValue().stream().sorted().toList();
			List<Integer> expected = java.util.stream.IntStream.range(0, lengths.get(belt.getKey())).boxed().toList();
			helper.assertTrue(seen.equals(expected), "the belt from " + belt.getKey().subtract(run.placement().origin()).toShortString()
				+ " is numbered " + seen + ", its length " + lengths.get(belt.getKey()));
		}
		helper.assertTrue(run.entry() != StarterBlueprints.WORKSHOP || belts > 0, "the Workshop has no belt");
	}

	/** The saved {@code Speed} of the block entity at the template spot {@code spot}, read by name (0 if there's none). */
	private static float speed(Run run, BlockPos spot) {
		BlockEntity be = run.level().getBlockEntity(run.world(spot));
		return be == null ? 0 : Nbt.getFloat(be.saveWithoutMetadata(run.level().registryAccess()), "Speed");
	}

	/** A position saved as an int array or as X, Y and Z (null if neither). */
	private static BlockPos readPos(net.minecraft.nbt.Tag tag) {
		if (tag instanceof net.minecraft.nbt.IntArrayTag array && array.getAsIntArray().length == 3) {
			int[] xyz = array.getAsIntArray();
			return new BlockPos(xyz[0], xyz[1], xyz[2]);
		}
		if (tag instanceof net.minecraft.nbt.CompoundTag xyz && xyz.contains("X")) {
			return new BlockPos(Nbt.getInt(xyz, "X"), Nbt.getInt(xyz, "Y"), Nbt.getInt(xyz, "Z"));
		}
		return null;
	}
}
