package io.github.jcondedata.aliveworkplace.compat;

import io.github.jcondedata.aliveworkplace.blueprint.BlueprintData;
import io.github.jcondedata.aliveworkplace.build.BuildPlan;
import io.github.jcondedata.aliveworkplace.build.BuildSite;
import io.github.jcondedata.aliveworkplace.build.BuildSiteManager;
import io.github.jcondedata.aliveworkplace.build.Builders;
import io.github.jcondedata.aliveworkplace.mc.Nbt;
import io.github.jcondedata.aliveworkplace.mechanic.Contraptions;
import io.github.jcondedata.aliveworkplace.mechanic.CreateBuilds;
import io.github.jcondedata.aliveworkplace.mechanic.KineticParts;
import io.github.jcondedata.aliveworkplace.registry.ModBlocks;
import io.github.jcondedata.aliveworkplace.registry.ModGameRules;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.BiConsumer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
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
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.SlabBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.SlabType;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;

/**
 * ROADMAP 39.5 with the owner's Create port: builders build contraptions. A player's own contraption (a water wheel fed
 * from a header tank, two shafts, a three-block belt and two cogwheels) is saved as a blueprint the way a Structure
 * Block saves it (its belt's data naming its controller where it stood), taken away, and built by a builder somewhere
 * else from barrels holding exactly its materials: in a contraption's order (what holds it up, then shafts, cogwheels,
 * belts), with the belt finding its controller in the new place, and checked to turn when it's done.
 */
public class ContraptionCompatTests implements FabricGameTest {
	private static final String HUGE_AREA = "aliveworkplace_compat:huge_area";
	/** Where the player's own contraption stands before it's saved (out of the build's way). */
	private static final BlockPos ORIGINAL = new BlockPos(18, 2, 18);
	private static final Vec3i SIZE = new Vec3i(8, 6, 9);
	/** The contraption's spots, from its corner. */
	static final BlockPos WHEEL = new BlockPos(1, 1, 5);
	static final BlockPos COG = new BlockPos(5, 1, 3);
	static final BlockPos COG_2 = new BlockPos(5, 2, 3);
	static final BlockPos TANK = new BlockPos(0, 3, 4);
	static final BlockPos WATER = new BlockPos(1, 3, 4);
	/** A shaft standing on its own, joined to nothing: it never turns. */
	static final BlockPos LONE_SHAFT = new BlockPos(6, 1, 7);

	static BlockState state(String path, String... properties) {
		BlockState state = MechanicCompatTests.create(path).defaultBlockState();
		for (int i = 0; i < properties.length; i += 2) {
			state = MechanicCompatTests.with(state, properties[i], properties[i + 1]);
		}
		return state;
	}

	/** The contraption, block by block from its corner (its water is poured once the rest stands). */
	static Map<BlockPos, BlockState> contraption(boolean loneShaft) {
		Map<BlockPos, BlockState> out = new LinkedHashMap<>();
		BlockState bricks = Blocks.STONE_BRICKS.defaultBlockState();
		for (int x = 0; x < 8; x++) {
			for (int z = 2; z < 9; z++) {
				if (x == 1 && z >= 4 && z <= 7) {
					continue; // the mill race the water runs down
				}
				out.put(new BlockPos(x, 0, z), bricks);
			}
		}
		for (int y = 1; y <= 2; y++) { // the tank's legs
			out.put(new BlockPos(0, y, 3), bricks);
			out.put(new BlockPos(2, y, 3), bricks);
		}
		for (int x = 0; x <= 2; x++) { // the tank, round its water
			for (int z = 3; z <= 5; z++) {
				if (x != 1 || z != 4) {
					out.put(new BlockPos(x, 3, z), bricks);
				}
			}
		}
		out.put(new BlockPos(1, 4, 4), Blocks.STONE_BRICK_SLAB.defaultBlockState().setValue(SlabBlock.TYPE, SlabType.BOTTOM));
		out.put(WHEEL, state("water_wheel", "facing", "east"));
		out.put(new BlockPos(2, 1, 5), state("shaft", "axis", "x"));
		out.put(new BlockPos(3, 1, 5), state("shaft", "axis", "x"));
		String[] belt = {"facing", "north", "slope", "horizontal", "casing", "false"};
		out.put(new BlockPos(4, 1, 5), with(state("belt", belt), "part", "start"));
		out.put(new BlockPos(4, 1, 4), with(state("belt", belt), "part", "middle"));
		out.put(new BlockPos(4, 1, 3), with(state("belt", belt), "part", "end"));
		out.put(COG, state("cogwheel", "axis", "x"));
		out.put(COG_2, state("cogwheel", "axis", "x"));
		if (loneShaft) {
			out.put(LONE_SHAFT, state("shaft", "axis", "z"));
		}
		return out;
	}

	private static BlockState with(BlockState state, String name, String value) {
		return MechanicCompatTests.with(state, name, value);
	}

	/** A build of the contraption under way: its site, plan, placement and builder, and when each block went in. */
	static final class Run {
		ServerLevel level;
		BuildSite site;
		BuildPlan plan;
		BlueprintData.Placement placement;
		Villager builder;
		/** Game tick each structure step's block first stood as drawn. */
		final Map<BlockPos, Long> placedAt = new HashMap<>();

		BlockPos world(BlockPos local) {
			return placement.origin().offset(StructureTemplate.transform(local, placement.mirror(), placement.rotation(), BlockPos.ZERO));
		}

		boolean started() {
			return site != null;
		}
	}

	/**
	 * Sets up the player's contraption by (18, 2, 18), saves it as blueprint {@code name} with a Structure Block's own
	 * call, takes it away, and has a builder start it at {@code origin} turned by {@code rotation}, from barrels holding
	 * exactly its materials (plus {@code extra}). {@code beforeStart} runs just before the site is made.
	 */
	static Run start(GameTestHelper helper, String name, boolean loneShaft, BlockPos origin, Rotation rotation, List<ItemStack> extra,
					 Runnable beforeStart, Runnable afterStart) {
		PartnerShowsCompatTests.clearLeftovers(helper);
		Run run = new Run();
		ServerLevel level = helper.getLevel();
		run.level = level;
		helper.setDayTime(2000);
		level.getGameRules().getRule(ModGameRules.BUILD_DELAY).set(1, level.getServer());
		level.getGameRules().getRule(ModGameRules.BUILDERS_HELP).set(false, level.getServer());
		Map<BlockPos, BlockState> blocks = contraption(loneShaft);
		blocks.forEach((p, s) -> helper.setBlock(ORIGINAL.offset(p), s));
		helper.setBlock(ORIGINAL.offset(WATER), Blocks.WATER);
		// By now the belt has worked out its controller and length, and the water has fallen through the wheel and down the
		// race, so the blueprint holds it running (its speeds saved too), as a player's would
		helper.runAfterDelay(80, () -> {
			ResourceLocation id = ResourceLocation.fromNamespaceAndPath("aliveworkplace_compat", "contraption_" + name);
			StructureTemplate template = level.getStructureManager().getOrCreate(id);
			template.fillFromWorld(level, helper.absolutePos(ORIGINAL), SIZE, false, Blocks.STRUCTURE_VOID);
			BlockPos corner = helper.absolutePos(ORIGINAL);
			for (int y = SIZE.getY() - 1; y >= 0; y--) { // taken away, nothing dropped
				for (int x = 0; x < SIZE.getX(); x++) {
					for (int z = 0; z < SIZE.getZ(); z++) {
						level.setBlock(corner.offset(x, y, z), Blocks.AIR.defaultBlockState(), Block.UPDATE_CLIENTS | Block.UPDATE_SUPPRESS_DROPS);
					}
				}
			}
			PartnerShowsCompatTests.clearLeftovers(helper);
			BlockPos bench = new BlockPos(2, 2, 2);
			helper.setBlock(bench, ModBlocks.BUILDERS_BENCH);
			Villager builder = helper.spawn(EntityType.VILLAGER, new BlockPos(3, 2, 3));
			Builders.employ(level, builder, helper.absolutePos(bench));
			run.builder = builder;
			run.placement = new BlueprintData.Placement(level.dimension().location(), helper.absolutePos(origin), rotation, Mirror.NONE);
			beforeStart.run();
			BuildSite site = Builders.start(level, builder, null, id, run.placement);
			afterStart.run();
			BuildPlan plan = site.plan(level);
			if (plan == null) {
				throw new GameTestAssertException("no blueprint " + id);
			}
			List<ItemStack> stock = new ArrayList<>();
			for (Map.Entry<Item, Integer> e : plan.materials().entrySet()) {
				for (int left = e.getValue(); left > 0; left -= e.getKey().getDefaultMaxStackSize()) {
					stock.add(new ItemStack(e.getKey(), Math.min(left, e.getKey().getDefaultMaxStackSize())));
				}
			}
			stock.addAll(extra);
			for (int i = 0; i * 27 < stock.size(); i++) {
				BlockPos barrel = new BlockPos(1 + i % 6, 2 + i / 6, 6);
				helper.setBlock(barrel, Blocks.BARREL);
				Container container = helper.getBlockEntity(barrel);
				for (int slot = 0; slot < 27 && i * 27 + slot < stock.size(); slot++) {
					container.setItem(slot, stock.get(i * 27 + slot));
				}
			}
			run.plan = plan;
			run.site = site;
		});
		helper.onEachTick(() -> {
			if (!run.started()) {
				return;
			}
			for (BuildPlan.Step s : run.plan.steps(BuildPlan.Stage.STRUCTURE)) {
				if (!run.placedAt.containsKey(s.pos()) && level.getBlockState(s.pos()).getBlock() == s.state().getBlock()) {
					run.placedAt.put(s.pos(), level.getGameTime());
				}
			}
		});
		return run;
	}

	private static Run start(GameTestHelper helper, String name, Rotation rotation) {
		return start(helper, name, false, origin(rotation), rotation, List.of(), () -> {
		}, () -> {
		});
	}

	/** Where the build goes for {@code rotation}: its blocks between (9, 2, 3) and (17, 7, 11). */
	static BlockPos origin(Rotation rotation) {
		return rotation == Rotation.CLOCKWISE_90 ? new BlockPos(17, 2, 3) : new BlockPos(9, 2, 3);
	}

	/** The build is done: every block as drawn, nothing skipped, and its check found everything turning. */
	static void assertBuiltAndTurning(GameTestHelper helper, Run run) {
		helper.assertTrue(run.started(), "the build hasn't started");
		helper.assertTrue(BuildSiteManager.get(run.level).get(run.site.id()) == null,
			"still building: " + run.site.stage() + " " + run.site.status() + " missing " + run.site.missing());
		List<BlockPos> unfinished = run.plan.unfinished(run.level);
		helper.assertTrue(unfinished.isEmpty(), unfinished.size() + " wrong, e.g. " + unfinished.stream().limit(8)
			.map(p -> p.toShortString() + "=" + run.level.getBlockState(p)).toList());
		helper.assertTrue(run.site.skipped() == 0, run.site.skipped() + " skipped");
		helper.assertTrue(BuildSiteManager.get(run.level).notTurning(run.placement).isPresent(), "not checked yet (turning check due "
			+ BuildSiteManager.get(run.level).turnChecks().stream().filter(c -> c.placement().equals(run.placement)).map(c -> c.due() + "/" + c.tries()).toList()
			+ ", now " + run.level.getGameTime() + ")");
		List<BlockPos> still = BuildSiteManager.get(run.level).notTurning(run.placement).get();
		helper.assertTrue(still.isEmpty(), still.size() + " parts don't turn: " + still.stream().map(p -> p.toShortString() + "=" + run.level.getBlockState(p)).toList());
		for (BlockPos local : List.of(WHEEL, COG, COG_2, new BlockPos(4, 1, 4))) {
			helper.assertTrue(Math.abs(speed(run.level, run.world(local))) > 0, "the part at " + local.toShortString() + " doesn't turn: "
				+ run.level.getBlockState(run.world(local)));
		}
	}

	static float speed(ServerLevel level, BlockPos pos) {
		BlockEntity be = level.getBlockEntity(pos);
		if (be == null) {
			return 0;
		}
		return Nbt.getFloat(be.saveWithoutMetadata(level.registryAccess()), "Speed");
	}

	/** Each kinetic group went in after everything before it: what holds it up, then shafts, cogwheels, belts. */
	static void assertContraptionOrder(GameTestHelper helper, Run run) {
		Map<Integer, long[]> span = new HashMap<>(); // group -> {first, last}
		for (BuildPlan.Step s : run.plan.steps(BuildPlan.Stage.STRUCTURE)) {
			Long t = run.placedAt.get(s.pos());
			helper.assertTrue(t != null, "never saw " + s.state() + " at " + s.pos().toShortString() + " go in");
			long[] g = span.computeIfAbsent(KineticParts.group(s.state()), k -> new long[] {Long.MAX_VALUE, Long.MIN_VALUE});
			g[0] = Math.min(g[0], t);
			g[1] = Math.max(g[1], t);
		}
		for (int g = 0; g <= 2; g++) {
			helper.assertTrue(span.containsKey(g), "no parts of group " + g + " in the contraption");
			long[] before = span.get(g - 1);
			helper.assertTrue(before[1] <= span.get(g)[0], "group " + g + " went in at " + span.get(g)[0] + ", before group " + (g - 1)
				+ " was done (" + before[1] + ")");
		}
	}

	/**
	 * The contraption, saved and turned a quarter: built in a contraption's order, every block as drawn with its axis and
	 * facing turned with it, the belt finding its controller in the new place, and the finished check finds it all turning.
	 */
	//$ gametest_ticks_batch HUGE_AREA '12000' '"contraption_build"'
	@GameTest(template = HUGE_AREA, timeoutTicks = 12000, batch = "contraption_build")
	public void aBuilderBuildsAContraptionAndItTurns(GameTestHelper helper) {
		Run run = start(helper, "build", Rotation.CLOCKWISE_90);
		helper.succeedWhen(() -> {
			assertBuiltAndTurning(helper, run);
			assertContraptionOrder(helper, run);
			BlockPos controller = run.world(new BlockPos(4, 1, 5)); // the belt's start is its controller, where it was saved too
			BlockEntity be = run.level.getBlockEntity(run.world(new BlockPos(4, 1, 3)));
			helper.assertTrue(be != null, "no belt at its end");
			CompoundTag belt = be.saveWithoutMetadata(run.level.registryAccess());
			helper.assertTrue(Nbt.getInt(belt, "Length") == 3, "the belt's length is " + Nbt.getInt(belt, "Length"));
			helper.assertTrue(belt.contains("Controller"), "the belt names no controller: " + belt);
			// The blueprint's belt named its controller where it was saved; the builder's names it here
			List<BlockPos> controllers = new ArrayList<>();
			for (BlockPos local : List.of(new BlockPos(4, 1, 5), new BlockPos(4, 1, 4), new BlockPos(4, 1, 3))) {
				BlockEntity segment = run.level.getBlockEntity(run.world(local));
				helper.assertTrue(segment != null, "no belt at " + local.toShortString());
				CompoundTag data = segment.saveWithoutMetadata(run.level.registryAccess());
				if (Nbt.getBoolean(data, "IsController")) {
					controllers.add(run.world(local));
				}
			}
			int[] named = Nbt.getIntArray(belt, "Controller");
			helper.assertTrue(controllers.size() == 1 && named.length == 3 && controllers.get(0).equals(new BlockPos(named[0], named[1], named[2])),
				"the belt's end names " + java.util.Arrays.toString(named) + " as its controller, the controller is at " + controllers);
			for (BuildPlan.Step step : run.plan.steps(BuildPlan.Stage.STRUCTURE)) { // the saved speeds aren't copied
				helper.assertTrue(step.nbt() == null || !step.nbt().contains("Speed") && !step.nbt().contains("Network"),
					"the plan copies " + step.state() + "'s running data: " + step.nbt());
			}
			helper.assertTrue(Math.abs(speed(run.level, controller)) > 0, "the belt's controller doesn't turn");
			List<BlockPos> parts = new ArrayList<>();
			for (int i = 0; i < 8; i++) {
				parts.add(controller.above(i));
			}
			String line = Contraptions.line(run.level, new BuildSiteManager.TurnCheck(run.site.structure(), run.placement, run.site.owner(),
				"Bob", parts, 0, 0), List.of()).getString();
			helper.assertTrue(line.startsWith("Bob checked the ") && line.endsWith(": all 8 moving parts turn."), "the owner reads: " + line);
		});
	}

	/** A cogwheel broken after it went in, while the build is still going, is put back before the build is called done. */
	//$ gametest_ticks_batch HUGE_AREA '12000' '"contraption_cog_broken"'
	@GameTest(template = HUGE_AREA, timeoutTicks = 12000, batch = "contraption_cog_broken")
	public void aCogwheelBrokenMidBuildIsRebuilt(GameTestHelper helper) {
		Run run = start(helper, "cog_broken", false, origin(Rotation.NONE), Rotation.NONE,
			List.of(new ItemStack(MechanicCompatTests.create("cogwheel"))), () -> {
			}, () -> {
			});
		boolean[] broken = {false};
		helper.onEachTick(() -> {
			if (!broken[0] && run.started() && run.level.getBlockState(run.world(COG)).getBlock() == MechanicCompatTests.create("cogwheel")) {
				broken[0] = true;
				run.level.setBlock(run.world(COG), Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL); // a player breaks it
			}
		});
		helper.succeedWhen(() -> {
			helper.assertTrue(broken[0], "the cogwheel never went in");
			assertBuiltAndTurning(helper, run);
			helper.assertTrue(run.site.kineticPasses() == 1, "went back over the parts " + run.site.kineticPasses() + " times, not once");
		});
	}

	/** Saved and loaded (the sites and the builder) halfway through its kinetic parts: it's finished, and turns. */
	//$ gametest_ticks_batch HUGE_AREA '12000' '"contraption_reload"'
	@GameTest(template = HUGE_AREA, timeoutTicks = 12000, batch = "contraption_reload")
	public void aContraptionSavedMidBuildIsFinished(GameTestHelper helper) {
		Run run = start(helper, "reload", Rotation.NONE);
		boolean[] reloaded = {false};
		boolean[] keptOrder = {false};
		helper.onEachTick(() -> {
			if (reloaded[0] || !run.started() || run.site.stage() != BuildPlan.Stage.STRUCTURE || run.site.cursor() <= run.plan.kineticStart(0)) {
				return;
			}
			reloaded[0] = true;
			ServerLevel level = run.level;
			HolderLookup.Provider registries = level.registryAccess();
			BuildSiteManager sites = BuildSiteManager.get(level);
			level.getDataStorage().set(BuildSiteManager.NAME, BuildSiteManager.load(sites.save(new CompoundTag(), registries), registries));
			BuildSite back = BuildSiteManager.get(level).get(run.site.id());
			helper.assertTrue(back != null, "the site didn't survive a save");
			keptOrder[0] = back.kineticOrder() && back.cursor() == run.site.cursor();
			CompoundTag tag = new CompoundTag();
			helper.assertTrue(run.builder.save(tag), "the builder wasn't saved");
			run.builder.discard();
			Entity loaded = EntityType.loadEntityRecursive(tag, level, e -> e);
			helper.assertTrue(loaded instanceof Villager, "the builder didn't load as a villager: " + loaded);
			level.addFreshEntity(loaded);
			run.builder = (Villager) loaded;
			run.site = back;
		});
		helper.succeedWhen(() -> {
			helper.assertTrue(reloaded[0], "never reloaded among the kinetic parts");
			helper.assertTrue(keptOrder[0], "the loaded site lost its contraption order or its place");
			assertBuiltAndTurning(helper, run);
		});
	}

	/** A part that can't turn (a shaft joined to nothing) is listed when the build is done, by name and place. */
	//$ gametest_ticks_batch HUGE_AREA '12000' '"contraption_lone_shaft"'
	@GameTest(template = HUGE_AREA, timeoutTicks = 12000, batch = "contraption_lone_shaft")
	public void aPartThatDoesntTurnIsListed(GameTestHelper helper) {
		Run run = start(helper, "lone_shaft", true, origin(Rotation.NONE), Rotation.NONE, List.of(), () -> {
		}, () -> {
		});
		helper.succeedWhen(() -> {
			helper.assertTrue(run.started() && BuildSiteManager.get(run.level).get(run.site.id()) == null, "still building");
			var still = BuildSiteManager.get(run.level).notTurning(run.placement);
			helper.assertTrue(still.isPresent(), "not checked yet");
			BlockPos lone = run.world(LONE_SHAFT);
			helper.assertTrue(still.get().equals(List.of(lone)), "listed " + still.get() + ", not just the lone shaft at " + lone);
			BuildSiteManager.TurnCheck check = new BuildSiteManager.TurnCheck(run.site.structure(), run.placement, run.site.owner(), "Bob",
				List.of(lone, run.world(WHEEL)), 0, 0);
			String line = Contraptions.line(run.level, check, still.get()).getString();
			helper.assertTrue(line.startsWith("Bob checked the ") && line.endsWith(": 1 part doesn't turn: Shaft at "
				+ lone.getX() + " " + lone.getY() + " " + lone.getZ() + "."), "the owner reads: " + line);
		});
	}

	/** With {@code createBuilds} off when the build starts, it goes in today's order (layer by layer) and isn't checked. */
	//$ gametest_ticks_batch HUGE_AREA '12000' '"contraption_switch_off"'
	@GameTest(template = HUGE_AREA, timeoutTicks = 12000, batch = "contraption_switch_off")
	public void theSwitchOffBuildsInTodaysOrder(GameTestHelper helper) {
		boolean[] was = {true};
		Run run = start(helper, "switch_off", false, origin(Rotation.NONE), Rotation.NONE, List.of(),
			() -> {
				was[0] = CreateBuilds.ENABLED;
				CreateBuilds.ENABLED = false;
			}, () -> CreateBuilds.ENABLED = was[0]);
		helper.succeedWhen(() -> {
			helper.assertTrue(run.started() && !run.site.kineticOrder(), "the site builds in a contraption's order with the switch off");
			helper.assertTrue(BuildSiteManager.get(run.level).get(run.site.id()) == null, "still building: " + run.site.stage());
			helper.assertTrue(run.plan.unfinished(run.level).isEmpty(), "not as drawn: " + run.plan.unfinished(run.level));
			Long cog = run.placedAt.get(run.world(COG));
			Long tank = run.placedAt.get(run.world(TANK));
			helper.assertTrue(cog != null && tank != null && cog < tank, "the cogwheel (" + cog + ") didn't go in before the tank above it ("
				+ tank + ")");
			helper.assertTrue(BuildSiteManager.get(run.level).notTurning(run.placement).isEmpty()
				&& BuildSiteManager.get(run.level).turnChecks().stream().noneMatch(c -> c.placement().equals(run.placement)), "checked with the switch off");
		});
	}
}
