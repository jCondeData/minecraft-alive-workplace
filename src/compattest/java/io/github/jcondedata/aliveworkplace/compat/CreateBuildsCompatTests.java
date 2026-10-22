package io.github.jcondedata.aliveworkplace.compat;

import io.github.jcondedata.aliveworkplace.blueprint.BlueprintData;
import io.github.jcondedata.aliveworkplace.blueprint.BlueprintLibrary;
import io.github.jcondedata.aliveworkplace.blueprint.StarterBlueprints;
import io.github.jcondedata.aliveworkplace.build.BuildPlan;
import io.github.jcondedata.aliveworkplace.build.BuildSite;
import io.github.jcondedata.aliveworkplace.build.BuildSiteManager;
import io.github.jcondedata.aliveworkplace.build.Builders;
import io.github.jcondedata.aliveworkplace.mc.Nbt;
import io.github.jcondedata.aliveworkplace.mechanic.CreateBuilds;
import io.github.jcondedata.aliveworkplace.registry.ModBlocks;
import io.github.jcondedata.aliveworkplace.registry.ModGameRules;
import io.github.jcondedata.aliveworkplace.table.TableServer;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
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
import net.minecraft.world.level.block.state.BlockState;

/**
 * ROADMAP 39.4 and 39.8 with the owner's Create port: the Watermill, the Windmill and the Workshop, the Brass Foundry,
 * the Sawmill, the Crushing Mill, the Greenhouse, the Clock Tower and the Pump House are at the Blueprint Table, and
 * a builder finishes each from barrels holding exactly its materials, nothing skipped, with its machine turning once
 * it's done: the speed is read from the machine's block entity data by name ({@code Speed}), no Create class. Without
 * Create: {@code CreateBuildsGameTests}.
 */
public class CreateBuildsCompatTests implements FabricGameTest {
	private static final String HUGE_AREA = "aliveworkplace_compat:huge_area";

	/** A block that must stand at a template spot when the build is done. */
	private record Expect(BlockPos spot, String block) {
	}

	private static Expect at(int x, int y, int z, String block) {
		return new Expect(new BlockPos(x, y, z), block);
	}

	/** With Create (and createBuilds on, as in every GameTest) the three builds are at the Blueprint Table. */
	//$ gametest 'FabricGameTest.EMPTY_STRUCTURE'
	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
	public void theCreateBuildsAreInTheTable(GameTestHelper helper) {
		List<ResourceLocation> listed = TableServer.listing(helper.getLevel().getServer()).stream().map(e -> e.id()).toList();
		for (StarterBlueprints.Entry entry : StarterBlueprints.CREATE_ONLY) {
			helper.assertTrue(listed.contains(entry.id()), entry.id() + " isn't in the Blueprint Table");
			helper.assertTrue(CreateBuilds.listed(entry.id()), entry.id() + " isn't listed with Create");
		}
		helper.succeed();
	}

	/** The Watermill: its millstone turns from the wheel the falling water drives. */
	//$ gametest_ticks_batch HUGE_AREA '30000' '"watermill_build"'
	@GameTest(template = HUGE_AREA, timeoutTicks = 30000, batch = "watermill_build")
	public void aBuilderBuildsTheWatermillAndItTurns(GameTestHelper helper) {
		build(helper, StarterBlueprints.WATERMILL, List.of(at(7, 1, 2, "minecraft:composter"), at(1, 1, 4, "create:water_wheel"),
			at(4, 1, 4, "create:gearbox")), new BlockPos(4, 2, 4));
	}

	/** The Windmill: the builder starts the bearing, its sails turn the millstone at the foot of the tower. */
	//$ gametest_ticks_batch HUGE_AREA '30000' '"windmill_build"'
	@GameTest(template = HUGE_AREA, timeoutTicks = 30000, batch = "windmill_build")
	public void aBuilderBuildsTheWindmillAndStartsIt(GameTestHelper helper) {
		build(helper, StarterBlueprints.WINDMILL, List.of(at(2, 1, 6, "minecraft:composter"), at(4, 9, 1, "create:windmill_bearing")),
			new BlockPos(4, 2, 5));
	}

	/** The Workshop: the wheel drives the belt, and the belt's far pulley the press over the depot. */
	//$ gametest_ticks_batch HUGE_AREA '30000' '"workshop_build"'
	@GameTest(template = HUGE_AREA, timeoutTicks = 30000, batch = "workshop_build")
	public void aBuilderBuildsTheWorkshopAndItTurns(GameTestHelper helper) {
		build(helper, StarterBlueprints.WORKSHOP, List.of(at(10, 1, 6, "minecraft:blast_furnace"), at(4, 1, 2, "create:depot"),
			at(4, 1, 4, "create:belt")), new BlockPos(4, 3, 2));
	}

	/** The Brass Foundry (39.8): the gear train takes the wheel's 8 RPM up to 32, so the mixer over the basin works (from 30). */
	//$ gametest_ticks_batch HUGE_AREA '30000' '"brass_foundry_build"'
	@GameTest(template = HUGE_AREA, timeoutTicks = 30000, batch = "brass_foundry_build")
	public void aBuilderBuildsTheBrassFoundryAndItTurns(GameTestHelper helper) {
		build(helper, StarterBlueprints.BRASS_FOUNDRY, List.of(at(9, 1, 2, "minecraft:smoker"), at(8, 4, 8, "create:mechanical_mixer"),
			at(8, 2, 8, "create:basin"), at(8, 1, 8, "create:blaze_burner"), at(4, 1, 2, "create:depot"), at(4, 1, 4, "create:belt")),
			new BlockPos(8, 4, 8), (level, origin) -> {
				float speed = speed(level, origin.offset(8, 4, 8));
				helper.assertTrue(Math.abs(speed) >= 30, "the mixer turns at " + speed + " RPM, too slow to mix (30)");
			});
	}

	/** The Sawmill (39.8): the belt from the wheel runs through two saws to the depot, and both saws turn. */
	//$ gametest_ticks_batch HUGE_AREA '30000' '"sawmill_build"'
	@GameTest(template = HUGE_AREA, timeoutTicks = 30000, batch = "sawmill_build")
	public void aBuilderBuildsTheSawmillAndItTurns(GameTestHelper helper) {
		build(helper, StarterBlueprints.SAWMILL, List.of(at(10, 1, 2, "minecraft:fletching_table"), at(4, 1, 4, "create:mechanical_saw"),
			at(4, 1, 3, "create:mechanical_saw"), at(4, 1, 2, "create:depot"), at(4, 1, 6, "create:belt")),
			new BlockPos(4, 1, 4), (level, origin) -> {
				float second = speed(level, origin.offset(4, 1, 3));
				helper.assertTrue(Math.abs(second) > 0, "the second saw doesn't turn (Speed " + second + ")");
			});
	}

	/**
	 * The Crushing Mill (39.8): both wheels turn, the opposite ways at the same speed, so Create's crusher stands valid in
	 * the gap between them (the block the miner's work file names).
	 */
	//$ gametest_ticks_batch HUGE_AREA '30000' '"crushing_mill_build"'
	@GameTest(template = HUGE_AREA, timeoutTicks = 30000, batch = "crushing_mill_build")
	public void aBuilderBuildsTheCrushingMillAndItTurns(GameTestHelper helper) {
		build(helper, StarterBlueprints.CRUSHING_MILL, List.of(at(9, 1, 2, "minecraft:blast_furnace"), at(5, 2, 5, "create:crushing_wheel"),
			at(7, 2, 5, "create:crushing_wheel"), at(6, 2, 5, "create:crushing_wheel_controller"), at(6, 1, 3, "create:item_drain"),
			at(6, 3, 7, "create:belt")),
			new BlockPos(5, 2, 5), (level, origin) -> {
				float a = speed(level, origin.offset(5, 2, 5));
				float b = speed(level, origin.offset(7, 2, 5));
				helper.assertTrue(a == -b, "the crushing wheels don't turn the opposite ways (" + a + ", " + b + ")");
				BlockState crusher = level.getBlockState(origin.offset(6, 2, 5));
				String valid = crusher.getProperties().stream().filter(p -> p.getName().equals("valid")).findFirst()
					.map(p -> crusher.getValue(p).toString()).orElse("?");
				helper.assertTrue("true".equals(valid), "the crusher between the wheels isn't working: " + crusher);
			});
	}

	/** The Greenhouse (39.8): the wheel's belt runs down the aisle between the beds to the depot, the crops on watered farmland. */
	//$ gametest_ticks_batch HUGE_AREA '30000' '"greenhouse_build"'
	@GameTest(template = HUGE_AREA, timeoutTicks = 30000, batch = "greenhouse_build")
	public void aBuilderBuildsTheGreenhouseAndItTurns(GameTestHelper helper) {
		build(helper, StarterBlueprints.GREENHOUSE, List.of(at(12, 1, 10, "minecraft:composter"), at(6, 1, 6, "create:belt"),
			at(6, 2, 7, "create:mechanical_harvester"), at(6, 2, 9, "create:mechanical_plough"), at(6, 1, 2, "create:depot"),
			at(7, 0, 5, "minecraft:farmland"), at(7, 1, 5, "minecraft:wheat")), new BlockPos(6, 1, 9), (level, origin) -> {
				float end = speed(level, origin.offset(6, 1, 3));
				helper.assertTrue(Math.abs(end) > 0, "the belt's far pulley doesn't turn (Speed " + end + ")");
			});
	}

	/** The Clock Tower (39.8): the wheel's drive goes up the tower's shaft and forward to the cuckoo clock, which turns. */
	//$ gametest_ticks_batch HUGE_AREA '30000' '"clock_tower_build"'
	@GameTest(template = HUGE_AREA, timeoutTicks = 30000, batch = "clock_tower_build")
	public void aBuilderBuildsTheClockTowerAndItTurns(GameTestHelper helper) {
		build(helper, StarterBlueprints.CLOCK_TOWER, List.of(at(8, 1, 2, "minecraft:cartography_table"), at(6, 10, 1, "create:cuckoo_clock"),
			at(6, 10, 5, "create:gearbox"), at(6, 5, 5, "create:shaft")), new BlockPos(6, 10, 1));
	}

	/** The Pump House (39.8): the wheel's cogwheel turns the pump beside it, between the sump's pipe and the fluid tank. */
	//$ gametest_ticks_batch HUGE_AREA '30000' '"pump_house_build"'
	@GameTest(template = HUGE_AREA, timeoutTicks = 30000, batch = "pump_house_build")
	public void aBuilderBuildsThePumpHouseAndItTurns(GameTestHelper helper) {
		build(helper, StarterBlueprints.PUMP_HOUSE, List.of(at(10, 1, 2, "minecraft:cauldron"), at(6, 1, 4, "create:mechanical_pump"),
			at(5, 1, 4, "create:fluid_pipe"), at(7, 1, 4, "create:fluid_pipe"), at(8, 1, 4, "create:fluid_tank"),
			at(8, 2, 4, "create:fluid_tank"), at(6, 1, 5, "create:cogwheel")), new BlockPos(6, 1, 4));
	}

	/** The saved {@code Speed} of the block entity at {@code pos}, read by name (0 if there's none). */
	private static float speed(ServerLevel level, BlockPos pos) {
		BlockEntity be = level.getBlockEntity(pos);
		return be == null ? 0 : Nbt.getFloat(be.saveWithoutMetadata(level.registryAccess()), "Speed");
	}

	private static void build(GameTestHelper helper, StarterBlueprints.Entry entry, List<Expect> expect, BlockPos machine) {
		build(helper, entry, expect, machine, (level, origin) -> {
		});
	}

	/**
	 * Builds {@code entry} by (9, 2, 3) from barrels holding exactly its materials; when it's done every block is right,
	 * nothing was skipped, each {@code expect} block stands at its spot, the first is a job site, and the machine at
	 * {@code machine} turns (its saved {@code Speed} isn't 0).
	 */
	private static void build(GameTestHelper helper, StarterBlueprints.Entry entry, List<Expect> expect, BlockPos machine,
		java.util.function.BiConsumer<ServerLevel, BlockPos> more) {
		ServerLevel level = helper.getLevel();
		helper.setDayTime(2000);
		level.getGameRules().getRule(ModGameRules.BUILD_DELAY).set(1, level.getServer());
		level.getGameRules().getRule(ModGameRules.BUILDERS_HELP).set(false, level.getServer());
		BlockPos bench = new BlockPos(2, 2, 2);
		helper.setBlock(bench, ModBlocks.BUILDERS_BENCH);
		Villager builder = helper.spawn(EntityType.VILLAGER, new BlockPos(3, 2, 3));
		Builders.employ(level, builder, helper.absolutePos(bench));
		// At (9, 2, 3), moved in so a wide or deep build keeps room round it in the area for its landscaping (two blocks)
		BlockPos origin = new BlockPos(Math.min(9, 29 - entry.size().getX()), 2, Math.min(3, 29 - entry.size().getZ()));
		BlueprintData.Placement placement = new BlueprintData.Placement(level.dimension().location(), helper.absolutePos(origin),
			Rotation.NONE, Mirror.NONE);
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
			BlockPos barrel = new BlockPos(1 + i % 6, 2 + i / 6, 5);
			helper.setBlock(barrel, Blocks.BARREL);
			Container container = helper.getBlockEntity(barrel);
			for (int slot = 0; slot < 27 && i * 27 + slot < stock.size(); slot++) {
				container.setItem(slot, stock.get(i * 27 + slot));
			}
		}
		helper.succeedWhen(() -> {
			helper.assertTrue(BuildSiteManager.get(level).get(site.id()) == null,
				"still building: " + site.stage() + " " + site.status() + " missing " + site.missing());
			// A started windmill's sails and hub leave the world as blocks: the bearing carries them, turning (its front plane)
			List<BlockPos> unfinished = plan.unfinished(level).stream().filter(p -> !(entry == StarterBlueprints.WINDMILL
				&& p.subtract(placement.origin()).getZ() == 0 && p.subtract(placement.origin()).getY() >= 5)).toList();
			helper.assertTrue(unfinished.isEmpty(), unfinished.size() + " wrong, e.g. " + unfinished.stream().limit(12)
				.map(p -> p.subtract(placement.origin()).toShortString() + "=" + level.getBlockState(p)).toList());
			helper.assertTrue(site.skipped() == 0, site.skipped() + " skipped");
			for (Expect e : expect) {
				BlockState there = level.getBlockState(placement.origin().offset(e.spot()));
				helper.assertTrue(BuiltInRegistries.BLOCK.getKey(there.getBlock()).toString().equals(e.block()),
					"expected " + e.block() + " at " + e.spot() + ", found " + there);
			}
			BlockPos job = placement.origin().offset(expect.get(0).spot());
			helper.assertTrue(level.getPoiManager().getType(job).isPresent(), "the job block at " + expect.get(0).spot() + " is no job site");
			BlockPos at = placement.origin().offset(machine);
			BlockEntity be = level.getBlockEntity(at);
			helper.assertTrue(be != null, "no block entity at the machine " + machine.toShortString() + ": " + level.getBlockState(at));
			CompoundTag tag = be.saveWithoutMetadata(level.registryAccess());
			float speed = Nbt.getFloat(tag, "Speed");
			helper.assertTrue(Math.abs(speed) > 0, "the machine at " + machine.toShortString() + " doesn't turn (Speed " + speed + ")");
			more.accept(level, placement.origin());
		});
	}
}
