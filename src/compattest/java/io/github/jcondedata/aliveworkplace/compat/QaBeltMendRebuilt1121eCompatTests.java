package io.github.jcondedata.aliveworkplace.compat;

import io.github.jcondedata.aliveworkplace.blueprint.BlueprintData;
import io.github.jcondedata.aliveworkplace.blueprint.Blueprints;
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
import io.github.jcondedata.aliveworkplace.work.WorkerStatus;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Container;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import net.minecraft.world.phys.AABB;

/**
 * QA lane E, 2026-11-21: B669 (39.2 Mechanic, with Create) on the rebuilt Create builds with belts that no test covered
 * (the Brass Foundry, the Crushing Mill and the Greenhouse, rebuilt in Milestones 50/51). Each is put up by a builder
 * from barrels holding exactly its materials (as QaMechanicFlippedBelt1103eCompatTests does); then a player breaks a
 * belt segment (Create takes the whole belt down, leaving its pulleys as shafts) and mines those shafts too. A Mechanic
 * with shafts and two belt connectors in his store puts the belt back whole: every segment where the blueprint draws
 * it, one controller, numbered 0 to its length once each, for exactly one belt connector and two shafts, and whatever
 * turned as built turns again. Belt connectors (in the store, on the ground, with the Mechanic, one in a standing belt)
 * are counted every tick and never grow. With no connector in the store, nothing is laid and he says what he waits for.
 */
public class QaBeltMendRebuilt1121eCompatTests implements FabricGameTest {
	private static final String HUGE_AREA = "aliveworkplace_compat:huge_area";
	private static final BlockPos TOOLBOX = new BlockPos(2, 2, 10);
	private static final BlockPos STORE = new BlockPos(2, 2, 12);
	private static final int CONNECTORS = 2;
	private static final int SHAFTS = 8;
	private static final int SETTLE = 100;

	/** The belts as the rebuilt blueprints draw them (template coordinates, read from the .nbt files). */
	private static List<BlockPos> line(int x, int y, int z0, int z1) {
		List<BlockPos> out = new ArrayList<>();
		for (int z = z0; z <= z1; z++) {
			out.add(new BlockPos(x, y, z));
		}
		return out;
	}

	private static final List<BlockPos> FOUNDRY_BELT = line(4, 1, 3, 10);
	private static final List<BlockPos> MILL_BELT = line(6, 3, 6, 8);
	private static final List<BlockPos> GREENHOUSE_BELT = line(6, 1, 3, 9);

	/** The Brass Foundry (8-long belt): broken, its shafts mined, mended whole. */
	//$ gametest_ticks_batch HUGE_AREA '40000' '"qa_e1121_foundry_belt_mend"'
	@GameTest(template = HUGE_AREA, timeoutTicks = 40000, batch = "qa_e1121_foundry_belt_mend")
	public void aBrassFoundrysBrokenBeltIsMendedWhole(GameTestHelper helper) {
		mendAfterBuild(helper, StarterBlueprints.BRASS_FOUNDRY, Rotation.NONE, FOUNDRY_BELT, CONNECTORS);
	}

	/** The Crushing Mill (3-long belt, the shortest): the same. */
	//$ gametest_ticks_batch HUGE_AREA '40000' '"qa_e1121_mill_belt_mend"'
	@GameTest(template = HUGE_AREA, timeoutTicks = 40000, batch = "qa_e1121_mill_belt_mend")
	public void aCrushingMillsBrokenBeltIsMendedWhole(GameTestHelper helper) {
		mendAfterBuild(helper, StarterBlueprints.CRUSHING_MILL, Rotation.NONE, MILL_BELT, CONNECTORS);
	}

	/** The Greenhouse (7-long belt): the same. */
	//$ gametest_ticks_batch HUGE_AREA '40000' '"qa_e1121_greenhouse_belt_mend"'
	@GameTest(template = HUGE_AREA, timeoutTicks = 40000, batch = "qa_e1121_greenhouse_belt_mend")
	public void aGreenhousesBrokenBeltIsMendedWhole(GameTestHelper helper) {
		mendAfterBuild(helper, StarterBlueprints.GREENHOUSE, Rotation.NONE, GREENHOUSE_BELT, CONNECTORS);
	}

	/** The Greenhouse turned a quarter at the Blueprint Table: the belt runs along x now, mended the same. */
	//$ gametest_ticks_batch HUGE_AREA '40000' '"qa_e1121_greenhouse_90_belt_mend"'
	@GameTest(template = HUGE_AREA, timeoutTicks = 40000, batch = "qa_e1121_greenhouse_90_belt_mend")
	public void aTurnedGreenhousesBrokenBeltIsMendedWhole(GameTestHelper helper) {
		mendAfterBuild(helper, StarterBlueprints.GREENHOUSE, Rotation.CLOCKWISE_90, GREENHOUSE_BELT, CONNECTORS);
	}

	/** The Crushing Mill with no belt connector in the store: no belt laid, none made, and the Mechanic says he needs one. */
	//$ gametest_ticks_batch HUGE_AREA '40000' '"qa_e1121_mill_belt_no_connector"'
	@GameTest(template = HUGE_AREA, timeoutTicks = 40000, batch = "qa_e1121_mill_belt_no_connector")
	public void aCrushingMillWithNoConnectorWaitsAndSaysSo(GameTestHelper helper) {
		mendAfterBuild(helper, StarterBlueprints.CRUSHING_MILL, Rotation.NONE, MILL_BELT, 0);
	}

	/**
	 * Builds {@code entry} turned by {@code rotation}, then breaks its belt by hand and mines the pulley shafts; a store
	 * with {@link #SHAFTS} shafts and {@code connectors} belt connectors, and a Mechanic. With connectors, the belt must
	 * come back whole for one; without, nothing is laid and he says he needs a belt connector.
	 */
	private static void mendAfterBuild(GameTestHelper helper, StarterBlueprints.Entry entry, Rotation rotation, List<BlockPos> drawn, int connectors) {
		PartnerShowsCompatTests.clearLeftovers(helper);
		Run run = start(helper, entry, rotation);
		ServerLevel level = run.level();
		Item connector = MechanicCompatTests.createItem("belt_connector");
		Item shaft = MechanicCompatTests.createItem("shaft");
		List<BlockPos> segments = drawn.stream().map(run::world).toList();
		Set<BlockPos> turnedAsBuilt = new LinkedHashSet<>();
		Map<BlockPos, String> stoppedByBreak = new LinkedHashMap<>();
		Villager[] mechanic = new Villager[1];
		long[] doneAt = {-1};
		long[] brokenAt = {-1};
		long[] readyAt = {-1};
		helper.onEachTick(() -> {
			long now = level.getGameTime();
			if (readyAt[0] >= 0) {
				// Every tick after the setup: belt connectors never grow in number (B669), and with none nothing is laid
				int total = connectorsEverywhere(helper, run, segments, mechanic[0], connector);
				if (total > connectors) {
					throw new GameTestAssertException(connectors + " belt connector(s) became " + total + " on tick " + (now - readyAt[0])
						+ " of the mend: " + MechanicCompatTests.count(store(helper), connector) + " in the store, "
						+ ground(level, run, connector) + " on the ground, " + held(mechanic[0], connector) + " with the Mechanic, a belt standing "
						+ anyBelt(level, segments) + "; he says " + said(helper, mechanic[0]));
				}
				if (connectors == 0) {
					for (BlockPos p : segments) {
						if (isBelt(level.getBlockState(p))) {
							throw new GameTestAssertException("with no belt connector in the store a belt segment was put at "
								+ rel(run, p) + " on tick " + (now - readyAt[0]) + "; he says " + said(helper, mechanic[0]));
						}
					}
				}
				return;
			}
			if (brokenAt[0] >= 0) {
				// Anything Create drops from the belt it takes down, a tick or two late: the Mechanic takes his from the store
				discardDrops(level, run);
				if (now == brokenAt[0] + 10) {
					for (BlockPos p : turnedAsBuilt) {
						float speed = speed(level, p);
						if (speed == 0) {
							stoppedByBreak.put(p, rel(run, p).toShortString() + " " + BuiltInRegistries.BLOCK.getKey(level.getBlockState(p).getBlock()).getPath());
						}
					}
				}
				if (now >= brokenAt[0] + 20) {
					helper.setBlock(STORE, Blocks.CHEST);
					Container store = store(helper);
					if (connectors > 0) {
						store.setItem(0, new ItemStack(connector, connectors));
					}
					store.setItem(1, new ItemStack(shaft, SHAFTS));
					helper.setBlock(TOOLBOX, MechanicCompatTests.create("brown_toolbox").defaultBlockState());
					mechanic[0] = helper.spawn(EntityType.VILLAGER, TOOLBOX.offset(1, 0, 1));
					Stations.assign(level, mechanic[0], helper.absolutePos(TOOLBOX), ModVillagers.MECHANIC);
					helper.setDayTime(2000); // a long build can run into the night; the Mechanic mends by day
					readyAt[0] = now;
				}
				return;
			}
			if (BuildSiteManager.get(level).get(run.site().id()) != null) {
				return;
			}
			if (doneAt[0] < 0) {
				doneAt[0] = now; // the water wheel and what it drives settle for a few seconds before anything is measured
			}
			if (now < doneAt[0] + SETTLE) {
				return;
			}
			// Built: the belt stands where the blueprint draws it, whole, and nowhere else
			List<BlockPos> built = beltsOf(run);
			helper.assertTrue(new LinkedHashSet<>(built).equals(new LinkedHashSet<>(segments)), "as built, the " + entry.id().getPath()
				+ "'s belt stands at " + built.stream().map(p -> rel(run, p).toShortString()).toList() + ", the blueprint draws it at "
				+ segments.stream().map(p -> rel(run, p).toShortString()).toList());
			assertBeltWhole(helper, run, segments, "as built");
			turnedAsBuilt.addAll(turning(run));
			helper.assertTrue(segments.stream().allMatch(turnedAsBuilt::contains), "as built, the " + entry.id().getPath()
				+ "'s belt doesn't turn: turning are " + turnedAsBuilt.stream().map(p -> rel(run, p).toShortString()).toList());
			// Earlier tests' finished builds at this spot (their blocks cleared) would be holes in this Mechanic's reach,
			// asked the way the Mechanic asks (B1535: a build whose origin lay over 96 blocks away still reached him)
			BuildSiteManager sites = BuildSiteManager.get(level);
			for (BuildSiteManager.Finished f : sites.finishedReaching(level, helper.absolutePos(TOOLBOX), MechanicWork.REACH * 2)) {
				if (!f.placement().equals(run.placement())) {
					sites.forgetFinished(f.placement());
				}
			}
			// The builder's barrels are emptied: the only belt connectors and shafts are the store's
			for (BlockPos p : BlockPos.betweenClosed(helper.absolutePos(new BlockPos(0, 1, 0)), helper.absolutePos(new BlockPos(7, 8, 0)))) {
				if (level.getBlockEntity(p) instanceof Container c) {
					c.clearContent();
				}
			}
			BlockPos broken = segments.get(segments.size() / 2);
			level.destroyBlock(broken, false);
			// Create takes the belt down and leaves its pulleys as shafts; the player mines those too
			for (BlockPos p : segments) {
				level.setBlock(p, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
			}
			discardDrops(level, run);
			brokenAt[0] = level.getGameTime();
		});
		if (connectors == 0) {
			helper.succeedWhen(() -> {
				helper.assertTrue(readyAt[0] >= 0, "still building: " + run.site().stage() + " " + run.site().status() + " missing " + run.site().missing());
				helper.assertTrue(level.getGameTime() >= readyAt[0] + 1200, "waiting 1200 ticks with no belt connector");
				helper.assertTrue(MechanicWork.isMechanic(mechanic[0]), "no Mechanic at the toolbox");
				String expected = Component.translatable("message.aliveworkplace.mechanic.state.waiting", connector.getDescription(),
					Blueprints.displayName(entry.id())).getString();
				String line = line(helper, mechanic[0]);
				helper.assertTrue(line.equals(expected), "with no belt connector the Mechanic says '" + line + "', not '" + expected
					+ "'; he sees missing " + missing(helper, run));
				helper.assertTrue(MechanicCompatTests.count(store(helper), shaft) == SHAFTS - 2, "the pulley shafts took "
					+ (SHAFTS - MechanicCompatTests.count(store(helper), shaft)) + " shafts from the store, not 2");
			});
			return;
		}
		helper.succeedWhen(() -> {
			helper.assertTrue(readyAt[0] >= 0, "still building: " + run.site().stage() + " " + run.site().status() + " missing " + run.site().missing());
			helper.assertTrue(MechanicWork.isMechanic(mechanic[0]), "no Mechanic at the toolbox");
			List<String> wrong = new ArrayList<>();
			for (BlockPos p : segments) {
				if (!isBelt(level.getBlockState(p))) {
					wrong.add(rel(run, p).toShortString() + "=" + level.getBlockState(p));
				}
			}
			helper.assertTrue(wrong.isEmpty(), "belt segments not back (" + segments.size() + " drawn): " + wrong + "; he sees missing "
				+ missing(helper, run) + "; he says " + said(helper, mechanic[0]) + ", stands at " + helper.relativePos(mechanic[0].blockPosition())
				+ " holding " + mechanic[0].getMainHandItem() + ", day time " + level.getDayTime() % 24000 + ", the store holds "
				+ MechanicCompatTests.count(store(helper), connector) + " belt connectors and " + MechanicCompatTests.count(store(helper), shaft)
				+ " shafts, " + ground(level, run, connector) + " connectors lie on the ground");
			helper.assertTrue(new LinkedHashSet<>(beltsOf(run)).equals(new LinkedHashSet<>(segments)), "after the mend belt segments stand at "
				+ beltsOf(run).stream().map(p -> rel(run, p).toShortString()).toList() + ", not only where drawn");
			assertBeltWhole(helper, run, segments, "mended");
			int left = MechanicCompatTests.count(store(helper), connector);
			helper.assertTrue(left == connectors - 1, "the mended belt took " + (connectors - left) + " belt connectors from the store, not one");
			int shaftsLeft = MechanicCompatTests.count(store(helper), shaft);
			helper.assertTrue(shaftsLeft == SHAFTS - 2, "the mended belt took " + (SHAFTS - shaftsLeft) + " shafts from the store, not two");
			helper.assertTrue(ground(level, run, connector) == 0, ground(level, run, connector) + " belt connectors on the ground");
			helper.assertTrue(held(mechanic[0], connector) == 0, "the Mechanic still carries a belt connector");
			List<String> still = new ArrayList<>();
			for (BlockPos p : turnedAsBuilt) {
				if (speed(level, p) == 0) {
					still.add(rel(run, p).toShortString() + " " + BuiltInRegistries.BLOCK.getKey(level.getBlockState(p).getBlock()).getPath());
				}
			}
			helper.assertTrue(still.isEmpty(), "after the mend these turned as built but don't turn: " + still
				+ " (the break stopped " + stoppedByBreak.values() + ")");
		});
	}

	private static Container store(GameTestHelper helper) {
		return (Container) helper.getBlockEntity(STORE);
	}

	private static boolean isBelt(BlockState state) {
		return "create:belt".equals(BuiltInRegistries.BLOCK.getKey(state.getBlock()).toString());
	}

	private static boolean anyBelt(ServerLevel level, List<BlockPos> segments) {
		return segments.stream().anyMatch(p -> isBelt(level.getBlockState(p)));
	}

	private static BlockPos rel(Run run, BlockPos p) {
		return p.subtract(run.placement().origin());
	}

	private static AABB around(Run run) {
		return new AABB(run.placement().origin()).inflate(24);
	}

	private static void discardDrops(ServerLevel level, Run run) {
		for (ItemEntity drop : level.getEntitiesOfClass(ItemEntity.class, around(run))) {
			drop.discard();
		}
	}

	private static int ground(ServerLevel level, Run run, Item item) {
		int n = 0;
		for (ItemEntity drop : level.getEntitiesOfClass(ItemEntity.class, around(run))) {
			if (drop.getItem().is(item)) {
				n += drop.getItem().getCount();
			}
		}
		return n;
	}

	private static int held(Villager villager, Item item) {
		return (villager.getMainHandItem().is(item) ? villager.getMainHandItem().getCount() : 0) + villager.getInventory().countItem(item);
	}

	/** Every belt connector there is: the store, the ground, the Mechanic, and the one a standing belt was laid with. */
	private static int connectorsEverywhere(GameTestHelper helper, Run run, List<BlockPos> segments, Villager mechanic, Item connector) {
		return MechanicCompatTests.count(store(helper), connector) + ground(run.level(), run, connector) + held(mechanic, connector)
			+ (anyBelt(run.level(), segments) ? 1 : 0);
	}

	private static String line(GameTestHelper helper, Villager villager) {
		WorkerStatus.Entry e = WorkerStatus.get(villager, helper.getLevel().getGameTime());
		return e == null ? "" : e.line().getString();
	}

	private static String said(GameTestHelper helper, Villager villager) {
		return "'" + line(helper, villager) + "'";
	}

	private static List<String> missing(GameTestHelper helper, Run run) {
		return MechanicWork.missingParts(helper.getLevel(), helper.absolutePos(TOOLBOX)).stream()
			.map(p -> rel(run, p.pos()).toShortString() + " " + p.cost()).toList();
	}

	private static float speed(ServerLevel level, BlockPos p) {
		BlockEntity be = level.getBlockEntity(p);
		return be == null ? 0 : Nbt.getFloat(be.saveWithoutMetadata(level.registryAccess()), "Speed");
	}

	/** The Create blocks inside the build that turn. */
	private static List<BlockPos> turning(Run run) {
		List<BlockPos> out = new ArrayList<>();
		BlockPos a = run.world(BlockPos.ZERO);
		BlockPos b = run.world(new BlockPos(run.entry().size().getX() - 1, run.entry().size().getY() - 1, run.entry().size().getZ() - 1));
		for (BlockPos p : BlockPos.betweenClosed(a, b)) {
			if ("create".equals(BuiltInRegistries.BLOCK.getKey(run.level().getBlockState(p).getBlock()).getNamespace())
				&& Math.abs(speed(run.level(), p)) > 0) {
				out.add(p.immutable());
			}
		}
		return out;
	}

	/** A build under way: where it stands and how it's turned. */
	private record Run(ServerLevel level, StarterBlueprints.Entry entry, BuildSite site, BuildPlan plan, BlueprintData.Placement placement) {
		BlockPos world(BlockPos spot) {
			return placement.origin().offset(StructureTemplate.transform(spot, placement.mirror(), placement.rotation(), BlockPos.ZERO));
		}
	}

	/** As QaMechanicFlippedBelt1103eCompatTests.start: a builder on the turned build, barrels holding exactly its materials. */
	private static Run start(GameTestHelper helper, StarterBlueprints.Entry entry, Rotation rotation) {
		Mirror mirror = Mirror.NONE;
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
		// Only inside the build: the tests beside this one leave their own builds standing
		BlockPos a = run.world(BlockPos.ZERO);
		BlockPos b = run.world(new BlockPos(run.entry().size().getX() - 1, run.entry().size().getY() - 1, run.entry().size().getZ() - 1));
		for (BlockPos pos : BlockPos.betweenClosed(a, b)) {
			if (isBelt(run.level().getBlockState(pos))) {
				out.add(pos.immutable());
			}
		}
		return out;
	}

	/**
	 * As QaMechanicFlippedBelt1103eCompatTests.assertBeltsWhole, for the one belt: every segment names (by its saved
	 * {@code Controller}) the same segment, which is a controller, and the segments are numbered 0 to the drawn length
	 * - 1 once each.
	 */
	private static void assertBeltWhole(GameTestHelper helper, Run run, List<BlockPos> segments, String when) {
		ServerLevel level = run.level();
		Set<BlockPos> controllers = new LinkedHashSet<>();
		List<Integer> indexes = new ArrayList<>();
		Map<BlockPos, Integer> lengths = new HashMap<>();
		for (BlockPos pos : segments) {
			String at = rel(run, pos).toShortString();
			BlockEntity be = level.getBlockEntity(pos);
			helper.assertTrue(be != null, when + ": a belt segment without its block entity at " + at);
			CompoundTag tag = be.saveWithoutMetadata(level.registryAccess());
			BlockPos controller = readPos(tag.get("Controller"));
			helper.assertTrue(controller != null, when + ": the belt segment at " + at + " has no controller: " + tag);
			helper.assertTrue(isBelt(level.getBlockState(controller)), when + ": the belt segment at " + at + " names a controller that isn't a belt: "
				+ rel(run, controller).toShortString() + " = " + level.getBlockState(controller));
			BlockEntity head = level.getBlockEntity(controller);
			helper.assertTrue(head != null && Nbt.getBoolean(head.saveWithoutMetadata(level.registryAccess()), "IsController"),
				when + ": the controller the belt segment at " + at + " names (" + rel(run, controller).toShortString() + ") isn't one");
			controllers.add(controller.immutable());
			indexes.add(Nbt.getInt(tag, "Index"));
			lengths.put(pos, Nbt.getInt(tag, "Length"));
		}
		helper.assertTrue(controllers.size() == 1, when + ": the belt's segments name " + controllers.size() + " controllers: "
			+ controllers.stream().map(c -> rel(run, c).toShortString()).toList());
		helper.assertTrue(lengths.values().stream().allMatch(l -> l == segments.size()), when + ": the belt's segments give lengths "
			+ lengths.values() + ", the blueprint draws " + segments.size());
		List<Integer> seen = indexes.stream().sorted().toList();
		List<Integer> expected = java.util.stream.IntStream.range(0, segments.size()).boxed().toList();
		helper.assertTrue(seen.equals(expected), when + ": the belt is numbered " + seen + ", not " + expected);
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
