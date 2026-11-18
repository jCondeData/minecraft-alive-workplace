package io.github.jcondedata.aliveworkplace.compat;

import io.github.jcondedata.aliveworkplace.blueprint.BlueprintData;
import io.github.jcondedata.aliveworkplace.blueprint.StarterBlueprints;
import io.github.jcondedata.aliveworkplace.build.BuildPlan;
import io.github.jcondedata.aliveworkplace.build.BuildSite;
import io.github.jcondedata.aliveworkplace.build.BuildSiteManager;
import io.github.jcondedata.aliveworkplace.build.Builders;
import io.github.jcondedata.aliveworkplace.mc.Nbt;
import io.github.jcondedata.aliveworkplace.mechanic.MechanicWork;
import io.github.jcondedata.aliveworkplace.registry.ModBlocks;
import io.github.jcondedata.aliveworkplace.registry.ModGameRules;
import io.github.jcondedata.aliveworkplace.registry.ModVillagers;
import io.github.jcondedata.aliveworkplace.work.Stations;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
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
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;

/**
 * QA lane E, 2026-11-03 (the lead left in the Notes by lane-e-1103-1013): ROADMAP 39.2, "a Mechanic mends a finished
 * Create build from its blueprint", in a build the player flipped at the Blueprint Table (39.4/39.5). A flipped build's
 * belt is put up by the builder without its place on the belt (Contraptions.blockData: Create works it out), but the
 * Mechanic mending it copies the blueprint's Index, IsController and moved Controller (MechanicWork.beltData). Here a
 * builder puts up the flipped Workshop (and the flipped Sawmill) from barrels holding exactly its materials; then a
 * player breaks one belt segment (Create takes the whole belt down, leaving its pulleys as shafts) and mines those
 * shafts too, and a Mechanic with belt connectors in his store puts the whole belt back. (Only the middle gone, the
 * shafts left standing, is B669.) Done means every belt segment stands where it stood, each names a controller segment of its own belt,
 * the segments of one belt are numbered 0 to its length once each, the machines it drives turn again, one belt
 * connector came from the store and none was ever made from nothing (B669: on main on 2026-11-03 these fail with 250 to
 * 570 connectors on the ground, the plain Workshop as much as the flipped ones).
 */
public class QaMechanicFlippedBelt1103eCompatTests implements FabricGameTest {
	private static final String HUGE_AREA = "aliveworkplace_compat:huge_area";
	private static final BlockPos TOOLBOX = new BlockPos(2, 2, 10);
	private static final BlockPos STORE = new BlockPos(2, 2, 12);

	/** The Workshop flipped: its belt broken and mended, it carries on driving the press. */
	//$ gametest_ticks_batch HUGE_AREA '40000' '"qa_e1103_flipped_workshop_mend"'
	@GameTest(template = HUGE_AREA, timeoutTicks = 40000, batch = "qa_e1103_flipped_workshop_mend")
	public void aFlippedWorkshopsBrokenBeltIsMendedWhole(GameTestHelper helper) {
		mendAfterBuild(helper, StarterBlueprints.WORKSHOP, Rotation.NONE, Mirror.FRONT_BACK, List.of(new BlockPos(4, 3, 2), new BlockPos(4, 1, 4)));
	}

	/** The Workshop as drawn, the control: its belt taken down and mended the same way. */
	//$ gametest_ticks_batch HUGE_AREA '40000' '"qa_e1103_plain_workshop_mend"'
	@GameTest(template = HUGE_AREA, timeoutTicks = 40000, batch = "qa_e1103_plain_workshop_mend")
	public void aWorkshopsBrokenBeltIsMendedWhole(GameTestHelper helper) {
		mendAfterBuild(helper, StarterBlueprints.WORKSHOP, Rotation.NONE, Mirror.NONE, List.of(new BlockPos(4, 3, 2), new BlockPos(4, 1, 4)));
	}

	/** The Workshop flipped and turned a quarter: the same. */
	//$ gametest_ticks_batch HUGE_AREA '40000' '"qa_e1103_flipped_workshop_90_mend"'
	@GameTest(template = HUGE_AREA, timeoutTicks = 40000, batch = "qa_e1103_flipped_workshop_90_mend")
	public void aFlippedTurnedWorkshopsBrokenBeltIsMendedWhole(GameTestHelper helper) {
		mendAfterBuild(helper, StarterBlueprints.WORKSHOP, Rotation.CLOCKWISE_90, Mirror.FRONT_BACK, List.of(new BlockPos(4, 3, 2), new BlockPos(4, 1, 4)));
	}

	/** The Sawmill flipped: its belt through both saws broken and mended, both saws turning. */
	//$ gametest_ticks_batch HUGE_AREA '40000' '"qa_e1103_flipped_sawmill_mend"'
	@GameTest(template = HUGE_AREA, timeoutTicks = 40000, batch = "qa_e1103_flipped_sawmill_mend")
	public void aFlippedSawmillsBrokenBeltIsMendedWhole(GameTestHelper helper) {
		mendAfterBuild(helper, StarterBlueprints.SAWMILL, Rotation.NONE, Mirror.FRONT_BACK,
			List.of(new BlockPos(4, 1, 4), new BlockPos(4, 1, 3), new BlockPos(4, 1, 6)));
	}

	private static void mendAfterBuild(GameTestHelper helper, StarterBlueprints.Entry entry, Rotation rotation, Mirror mirror, List<BlockPos> machines) {
		PartnerShowsCompatTests.clearLeftovers(helper);
		Run run = start(helper, entry, rotation, mirror);
		ServerLevel level = run.level();
		Map<BlockPos, BlockState> belt = new HashMap<>();
		Villager[] mechanic = new Villager[1];
		BlockPos[] broken = new BlockPos[1];
		long[] brokenAt = new long[1];
		helper.onEachTick(() -> {
			if (broken[0] != null) {
				// Never a belt connector from nothing (B669): the drops of the break were cleared, so any on the ground came after
				if (level.getGameTime() < brokenAt[0] + 20) {
					for (net.minecraft.world.entity.item.ItemEntity drop : level.getEntitiesOfClass(net.minecraft.world.entity.item.ItemEntity.class,
							new net.minecraft.world.phys.AABB(broken[0]).inflate(24))) {
						drop.discard(); // anything Create drops from the belt it takes down, a tick or two late
					}
					return;
				}
				int ground = groundConnectors(level, broken[0]);
				if (ground > 0) {
					throw new GameTestAssertException("mending the belt made " + ground + " belt connector(s) from nothing, lying on the ground"
						+ " (the store still holds " + MechanicCompatTests.count((Container) helper.getBlockEntity(STORE),
							MechanicCompatTests.createItem("belt_connector")) + " of 4); the Mechanic says " + said(helper, mechanic[0]));
				}
				return;
			}
			if (BuildSiteManager.get(level).get(run.site().id()) != null) {
				return;
			}
			// Built: every belt segment as Create worked it out, then the player breaks the one in the middle of the first belt
			List<BlockPos> segments = beltsOf(run);
			if (segments.isEmpty()) {
				throw new GameTestAssertException("the finished " + entry.id() + " has no belt");
			}
			assertBeltsWhole(helper, run, "as built");
			for (BlockPos pos : segments) {
				belt.put(pos.immutable(), level.getBlockState(pos));
			}
			// Earlier tests' finished builds at this spot (their blocks cleared) would be holes in this Mechanic's reach
			BuildSiteManager sites = BuildSiteManager.get(level);
			for (BuildSiteManager.Finished f : sites.finishedIn(level)) {
				if (!f.placement().equals(run.placement()) && f.placement().origin().distManhattan(run.placement().origin()) < 96) {
					sites.forgetFinished(f.placement());
				}
			}
			helper.setBlock(STORE, Blocks.CHEST);
			Container store = (Container) helper.getBlockEntity(STORE);
			store.setItem(0, new ItemStack(MechanicCompatTests.createItem("belt_connector"), 4));
			store.setItem(1, new ItemStack(MechanicCompatTests.createItem("shaft"), 8));
			helper.setBlock(TOOLBOX, MechanicCompatTests.create("brown_toolbox").defaultBlockState());
			mechanic[0] = helper.spawn(EntityType.VILLAGER, TOOLBOX.offset(1, 0, 1));
			Stations.assign(level, mechanic[0], helper.absolutePos(TOOLBOX), ModVillagers.MECHANIC);
			helper.setDayTime(2000); // a long build can run into the night; the Mechanic mends by day
			broken[0] = segments.get(segments.size() / 2).immutable();
			brokenAt[0] = level.getGameTime();
			level.destroyBlock(broken[0], false);
			// Create takes the belt down and leaves its pulleys as shafts; the player mines those too (a lone middle
			// segment between the shafts is B669), so the whole belt is a hole for the Mechanic
			for (BlockPos pos : segments) {
				level.setBlock(pos, Blocks.AIR.defaultBlockState(), net.minecraft.world.level.block.Block.UPDATE_ALL);
			}
			for (net.minecraft.world.entity.item.ItemEntity drop : level.getEntitiesOfClass(net.minecraft.world.entity.item.ItemEntity.class,
					new net.minecraft.world.phys.AABB(broken[0]).inflate(12))) {
				drop.discard(); // the dropped connector and shafts: the Mechanic takes his from the store
			}
		});
		helper.succeedWhen(() -> {
			helper.assertTrue(broken[0] != null, "still building: " + run.site().stage() + " " + run.site().status() + " missing " + run.site().missing());
			helper.assertTrue(MechanicWork.isMechanic(mechanic[0]), "no Mechanic at the toolbox");
			List<String> wrong = new ArrayList<>();
			for (Map.Entry<BlockPos, BlockState> e : belt.entrySet()) {
				BlockState now = level.getBlockState(e.getKey());
				if (!now.is(e.getValue().getBlock())) {
					wrong.add(e.getKey().subtract(run.placement().origin()).toShortString() + "=" + now);
				}
			}
			helper.assertTrue(wrong.isEmpty(), "belt segments not back after the break at "
				+ broken[0].subtract(run.placement().origin()).toShortString() + " (" + belt.size() + " were standing): " + wrong
				+ "; the Mechanic still sees missing " + MechanicWork.missingParts(level, helper.absolutePos(TOOLBOX)).stream()
					.map(p -> p.pos().subtract(run.placement().origin()).toShortString() + " " + p.cost()).toList()
				+ "; he says " + said(helper, mechanic[0]) + ", stands at " + helper.relativePos(mechanic[0].blockPosition())
				+ " holding " + mechanic[0].getMainHandItem() + ", day time " + level.getDayTime() % 24000 + ", the store holds "
				+ MechanicCompatTests.count((Container) helper.getBlockEntity(STORE), MechanicCompatTests.createItem("belt_connector")) + " belt connectors, "
				+ groundConnectors(level, broken[0]) + " lie on the ground");
			assertBeltsWhole(helper, run, "mended");
			int left = MechanicCompatTests.count((Container) helper.getBlockEntity(STORE), MechanicCompatTests.createItem("belt_connector"));
			helper.assertTrue(left == 3, "the mended belt took " + (4 - left) + " belt connectors from the store, not one");
			for (BlockPos machine : machines) {
				BlockPos at = run.world(machine);
				BlockEntity be = level.getBlockEntity(at);
				float speed = be == null ? 0 : Nbt.getFloat(be.saveWithoutMetadata(level.registryAccess()), "Speed");
				helper.assertTrue(Math.abs(speed) > 0, "after the mend the machine at " + machine.toShortString() + " ("
					+ level.getBlockState(at) + ") doesn't turn (Speed " + speed + ")");
			}
		});
	}

	/** Belt connectors lying on the ground near the belt (none were left there at the break). */
	private static int groundConnectors(ServerLevel level, BlockPos near) {
		int n = 0;
		for (net.minecraft.world.entity.item.ItemEntity drop : level.getEntitiesOfClass(net.minecraft.world.entity.item.ItemEntity.class,
				new net.minecraft.world.phys.AABB(near).inflate(24))) {
			if (drop.getItem().is(MechanicCompatTests.createItem("belt_connector"))) {
				n += drop.getItem().getCount();
			}
		}
		return n;
	}

	private static String said(GameTestHelper helper, Villager villager) {
		io.github.jcondedata.aliveworkplace.work.WorkerStatus.Entry e = io.github.jcondedata.aliveworkplace.work.WorkerStatus.get(villager, helper.getLevel().getGameTime());
		return e == null ? "nothing" : "'" + e.title().getString() + ": " + e.line().getString() + "'";
	}

	/** A build under way: where it stands and how it's turned. */
	private record Run(ServerLevel level, StarterBlueprints.Entry entry, BuildSite site, BuildPlan plan, BlueprintData.Placement placement) {
		BlockPos world(BlockPos spot) {
			return placement.origin().offset(StructureTemplate.transform(spot, placement.mirror(), placement.rotation(), BlockPos.ZERO));
		}
	}

	/** As QaCreateBuildsTurnedCompatTests.start: a builder on the turned build, barrels holding exactly its materials. */
	private static Run start(GameTestHelper helper, StarterBlueprints.Entry entry, Rotation rotation, Mirror mirror) {
		ServerLevel level = helper.getLevel();
		helper.setDayTime(2000);
		level.getGameRules().getRule(ModGameRules.BUILD_DELAY).set(1, level.getServer());
		level.getGameRules().getRule(ModGameRules.BUILDERS_HELP).set(false, level.getServer());
		BlockPos bench = new BlockPos(2, 2, 2);
		helper.setBlock(bench, ModBlocks.BUILDERS_BENCH);
		Villager builder = helper.spawn(EntityType.VILLAGER, new BlockPos(3, 2, 3));
		Builders.employ(level, builder, helper.absolutePos(bench));
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

	private static List<BlockPos> beltsOf(Run run) {
		List<BlockPos> out = new ArrayList<>();
		for (BlockPos pos : BlockPos.betweenClosed(run.world(BlockPos.ZERO).offset(-30, -2, -30), run.world(BlockPos.ZERO).offset(30, 20, 30))) {
			if ("create:belt".equals(BuiltInRegistries.BLOCK.getKey(run.level().getBlockState(pos).getBlock()).toString())) {
				out.add(pos.immutable());
			}
		}
		return out;
	}

	/**
	 * As QaCreateBuildsTurnedCompatTests.assertBeltsWhole: every segment names (by its saved {@code Controller}) a belt
	 * segment that is a controller, and the segments of one belt are numbered 0 to its length - 1 once each.
	 */
	private static void assertBeltsWhole(GameTestHelper helper, Run run, String when) {
		ServerLevel level = run.level();
		Map<BlockPos, List<Integer>> indexes = new HashMap<>();
		Map<BlockPos, Integer> lengths = new HashMap<>();
		for (BlockPos pos : beltsOf(run)) {
			String at = pos.subtract(run.placement().origin()).toShortString();
			BlockEntity be = level.getBlockEntity(pos);
			helper.assertTrue(be != null, when + ": a belt segment without its block entity at " + at);
			CompoundTag tag = be.saveWithoutMetadata(level.registryAccess());
			BlockPos controller = readPos(tag.get("Controller"));
			helper.assertTrue(controller != null, when + ": the belt segment at " + at + " has no controller: " + tag);
			helper.assertTrue("create:belt".equals(BuiltInRegistries.BLOCK.getKey(level.getBlockState(controller).getBlock()).toString()),
				when + ": the belt segment at " + at + " names a controller that isn't a belt: "
					+ controller.subtract(run.placement().origin()).toShortString() + " = " + level.getBlockState(controller));
			BlockEntity head = level.getBlockEntity(controller);
			helper.assertTrue(head != null && Nbt.getBoolean(head.saveWithoutMetadata(level.registryAccess()), "IsController"),
				when + ": the controller the belt segment at " + at + " names ("
					+ controller.subtract(run.placement().origin()).toShortString() + ") isn't one");
			indexes.computeIfAbsent(controller.immutable(), c -> new ArrayList<>()).add(Nbt.getInt(tag, "Index"));
			lengths.put(controller.immutable(), Nbt.getInt(tag, "Length"));
		}
		for (Map.Entry<BlockPos, List<Integer>> b : indexes.entrySet()) {
			List<Integer> seen = b.getValue().stream().sorted().toList();
			List<Integer> expected = java.util.stream.IntStream.range(0, lengths.get(b.getKey())).boxed().toList();
			helper.assertTrue(seen.equals(expected), when + ": the belt from " + b.getKey().subtract(run.placement().origin()).toShortString()
				+ " is numbered " + seen + ", its length " + lengths.get(b.getKey()));
		}
	}

	private static BlockPos readPos(net.minecraft.nbt.Tag tag) {
		if (tag instanceof net.minecraft.nbt.IntArrayTag array && array.getAsIntArray().length == 3) {
			int[] xyz = array.getAsIntArray();
			return new BlockPos(xyz[0], xyz[1], xyz[2]);
		}
		if (tag instanceof CompoundTag xyz && xyz.contains("X")) {
			return new BlockPos(Nbt.getInt(xyz, "X"), Nbt.getInt(xyz, "Y"), Nbt.getInt(xyz, "Z"));
		}
		return null;
	}
}
