package io.github.jcondedata.aliveworkplace.compat;

import io.github.jcondedata.aliveworkplace.AliveWorkplace;
import io.github.jcondedata.aliveworkplace.mechanic.MachineJob;
import io.github.jcondedata.aliveworkplace.mechanic.MachineWork;
import io.github.jcondedata.aliveworkplace.mechanic.MachineWorkFiles;
import io.github.jcondedata.aliveworkplace.registry.ModAttachments;
import io.github.jcondedata.aliveworkplace.registry.ModBlocks;
import io.github.jcondedata.aliveworkplace.registry.ModVillagers;
import io.github.jcondedata.aliveworkplace.work.Stations;
import java.util.List;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Container;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.npc.VillagerProfession;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;

/**
 * ROADMAP 39.3 with the real Create: villagers work Create's machines as the shipped {@code machine_work} files say. For
 * each machine (the farmer's millstone, the armorer's press over a depot, the chef's mixer over a basin, the miner's
 * crushing wheels): with the machine turned by a creative motor, the store's input is fed to it and what it makes ends up
 * in the store; with the machine standing still nothing is fed; a full store pauses the work; a worker saved and loaded
 * mid-trip, carrying the input, finishes the job. Config {@code machineWork} off: nothing is fed.
 */
public class MachineWorkCompatTests implements FabricGameTest {
	private static final String AREA = CompatGameTests.AREA;
	private static final BlockPos STATION = new BlockPos(2, 2, 2);
	private static final BlockPos CHEST = new BlockPos(2, 2, 4);

	/** A machine, its worker and workstation, what goes in and what comes out. */
	enum Kind {
		MILL("millstone", "minecraft:farmer", "wheat", "wheat_flour"),
		PRESS("press", "minecraft:armorer", "iron_ingot", "iron_sheet"),
		MIXER("mixer", "aliveworkplace:chef", "wheat_flour", "dough"),
		CRUSHER("crushing", "aliveworkplace:miner", "raw_iron", "crushed_raw_iron");

		final String file;
		final String job;
		final String input;
		final String output;

		Kind(String file, String job, String input, String output) {
			this.file = file;
			this.job = job;
			this.input = input;
			this.output = output;
		}

		Item input() {
			return input.equals("wheat") ? Items.WHEAT : input.equals("iron_ingot") ? Items.IRON_INGOT : input.equals("raw_iron") ? Items.RAW_IRON
				: MechanicCompatTests.createItem(input);
		}

		Item output() {
			return MechanicCompatTests.createItem(output);
		}

		VillagerProfession profession() {
			return net.minecraft.core.registries.BuiltInRegistries.VILLAGER_PROFESSION.get(net.minecraft.resources.ResourceLocation.parse(job));
		}

		Block station() {
			return switch (this) {
				case MILL -> Blocks.COMPOSTER;
				case PRESS -> Blocks.BLAST_FURNACE;
				case MIXER -> ModBlocks.KITCHEN_STOVE;
				case CRUSHER -> ModBlocks.MINERS_BENCH;
			};
		}
	}

	private static Block create(String path) {
		return MechanicCompatTests.create(path);
	}

	private static final java.util.concurrent.atomic.AtomicInteger WORKSHOPS = new java.util.concurrent.atomic.AtomicInteger();

	/**
	 * B243: makes the whole area, from the floor up, a building the village's builders finished (an empty blueprint of
	 * its size, recorded as finished and forgotten after the test), so its machines and chests are the village's own.
	 */
	static void villageWorkshop(GameTestHelper helper) {
		villageBuilding(helper, new BlockPos(0, 2, 0), new net.minecraft.core.Vec3i(17, 6, 17));
	}

	/** A finished village building of {@code size} at {@code from} (relative), with an empty blueprint; forgotten after the test. */
	static void villageBuilding(GameTestHelper helper, BlockPos from, net.minecraft.core.Vec3i size) {
		ServerLevel level = helper.getLevel();
		net.minecraft.resources.ResourceLocation id = AliveWorkplace.id("compat_test/village_workshop_" + WORKSHOPS.incrementAndGet());
		net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate template = level.getStructureManager().getOrCreate(id);
		template.fillFromWorld(level, helper.absolutePos(from), size, false, Blocks.AIR);
		helper.assertTrue(io.github.jcondedata.aliveworkplace.blueprint.BlueprintLibrary.get(level, id).isPresent(), "setup: could not register the village building");
		io.github.jcondedata.aliveworkplace.blueprint.BlueprintData.Placement placement = new io.github.jcondedata.aliveworkplace.blueprint.BlueprintData.Placement(
			level.dimension().location(), helper.absolutePos(from), net.minecraft.world.level.block.Rotation.NONE, net.minecraft.world.level.block.Mirror.NONE);
		io.github.jcondedata.aliveworkplace.build.BuildSiteManager.get(level).recordFinished(id, placement, java.util.UUID.randomUUID());
		PartnerShowsCompatTests.after(helper, () -> io.github.jcondedata.aliveworkplace.build.BuildSiteManager.get(level).forgetFinished(placement));
	}

	/** B243: forgets every finished building earlier tests left over this area, so nothing here is the village's. */
	static void noVillage(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		var sites = io.github.jcondedata.aliveworkplace.build.BuildSiteManager.get(level);
		net.minecraft.world.phys.AABB area = helper.getBounds().inflate(MachineWork.REACH + 8);
		for (var f : sites.finishedIn(level)) {
			var blueprint = io.github.jcondedata.aliveworkplace.blueprint.BlueprintLibrary.get(level, f.structure());
			if (blueprint.isEmpty()) {
				continue;
			}
			var box = io.github.jcondedata.aliveworkplace.blueprint.BlueprintOutline.bounds(f.placement(), blueprint.get().size());
			if (area.intersects(new net.minecraft.world.phys.AABB(box.minX(), box.minY(), box.minZ(), box.maxX() + 1, box.maxY() + 1, box.maxZ() + 1))) {
				sites.forgetFinished(f.placement());
			}
		}
	}

	/** Builds {@code kind}'s machine (turned by creative motors when {@code powered}); returns the machine's position. */
	static BlockPos rig(GameTestHelper helper, Kind kind, boolean powered) {
		switch (kind) {
			case MILL -> {
				BlockPos mill = new BlockPos(6, 3, 2);
				if (powered) {
					helper.setBlock(mill.below(), MechanicCompatTests.with(create("creative_motor").defaultBlockState(), "facing", "up"));
				} else {
					helper.setBlock(mill.below(), Blocks.STONE);
				}
				helper.setBlock(mill, create("millstone").defaultBlockState());
				return mill;
			}
			case PRESS -> {
				BlockPos press = new BlockPos(6, 4, 2);
				if (powered) {
					helper.setBlock(press.east(), MechanicCompatTests.with(create("creative_motor").defaultBlockState(), "facing", "west"));
				}
				helper.setBlock(press.below(2), create("depot").defaultBlockState());
				helper.setBlock(press, MechanicCompatTests.with(create("mechanical_press").defaultBlockState(), "facing", "east"));
				return press;
			}
			case MIXER -> {
				BlockPos mixer = new BlockPos(6, 4, 2);
				if (powered) {
					// the mixer is driven as a cogwheel, by a cogwheel beside it, and works from medium speed (30 RPM)
					BlockPos cog = mixer.east();
					helper.setBlock(cog.above(), MechanicCompatTests.with(create("creative_motor").defaultBlockState(), "facing", "down"));
					motorSpeed(helper, cog.above(), 64);
					helper.setBlock(cog, MechanicCompatTests.with(create("cogwheel").defaultBlockState(), "axis", "y"));
				}
				helper.setBlock(mixer.below(2), create("basin").defaultBlockState());
				helper.setBlock(mixer, create("mechanical_mixer").defaultBlockState());
				return mixer;
			}
			case CRUSHER -> {
				BlockPos gap = new BlockPos(6, 3, 2);
				if (powered) {
					// the two wheels turn opposite ways, inwards at the top, so what's dropped in is pulled down between them
					helper.setBlock(gap.west().south(), MechanicCompatTests.with(create("creative_motor").defaultBlockState(), "facing", "north"));
					helper.setBlock(gap.east().north(), MechanicCompatTests.with(create("creative_motor").defaultBlockState(), "facing", "south"));
				}
				helper.setBlock(gap.west(), MechanicCompatTests.with(create("crushing_wheel").defaultBlockState(), "axis", "z"));
				helper.setBlock(gap.east(), MechanicCompatTests.with(create("crushing_wheel").defaultBlockState(), "axis", "z"));
				return gap;
			}
		}
		throw new IllegalArgumentException(kind.name());
	}

	/**
	 * Takes away every Create block (and dropped item) left within reach of this area by earlier tests: a worker or a
	 * Mechanic would otherwise work another test's machine. Run before a test builds and once it has passed.
	 */
	static void clearMachines(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		BlockPos centre = helper.absolutePos(new BlockPos(4, 2, 4));
		int reach = MachineWork.REACH + 8;
		for (int cx = (centre.getX() - reach) >> 4; cx <= (centre.getX() + reach) >> 4; cx++) {
			for (int cz = (centre.getZ() - reach) >> 4; cz <= (centre.getZ() + reach) >> 4; cz++) {
				net.minecraft.world.level.chunk.LevelChunk chunk = level.getChunkSource().getChunkNow(cx, cz);
				if (chunk == null) {
					continue;
				}
				for (BlockPos pos : List.copyOf(chunk.getBlockEntities().keySet())) {
					if ("create".equals(net.minecraft.core.registries.BuiltInRegistries.BLOCK.getKey(level.getBlockState(pos).getBlock()).getNamespace())) {
						level.removeBlockEntity(pos);
						level.setBlock(pos, Blocks.AIR.defaultBlockState(), 2 | 16);
					}
				}
			}
		}
		for (net.minecraft.world.entity.item.ItemEntity e : level.getEntitiesOfClass(net.minecraft.world.entity.item.ItemEntity.class,
				helper.getBounds().inflate(reach))) {
			e.discard();
		}
	}

	/** Sets the creative motor at {@code pos} to {@code rpm}, as a player does with its value panel (saved as {@code ScrollValue}). */
	private static void motorSpeed(GameTestHelper helper, BlockPos pos, int rpm) {
		net.minecraft.world.level.block.entity.BlockEntity be = helper.getBlockEntity(pos);
		CompoundTag tag = be.saveWithoutMetadata(helper.getLevel().registryAccess());
		tag.putInt("ScrollValue", rpm);
		be.loadWithComponents(tag, helper.getLevel().registryAccess());
		be.setChanged();
	}

	private static Villager worker(GameTestHelper helper, Kind kind) {
		helper.setBlock(STATION, kind.station());
		Villager villager = helper.spawn(EntityType.VILLAGER, new BlockPos(3, 2, 3));
		Stations.assign(helper.getLevel(), villager, helper.absolutePos(STATION), kind.profession());
		helper.assertTrue(villager.getVillagerData().getProfession() == kind.profession(), "no " + kind.job + " at the workstation");
		helper.setDayTime(2000);
		return villager;
	}

	private static Container chest(GameTestHelper helper, ItemStack... stacks) {
		helper.setBlock(CHEST, Blocks.CHEST.defaultBlockState());
		Container chest = (Container) helper.getBlockEntity(CHEST);
		for (int i = 0; i < stacks.length; i++) {
			chest.setItem(i, stacks[i]);
		}
		return chest;
	}

	private static int count(Container container, Item item) {
		int n = 0;
		for (int i = 0; i < container.getContainerSize(); i++) {
			if (container.getItem(i).is(item)) {
				n += container.getItem(i).getCount();
			}
		}
		return n;
	}

	private static MachineWorkFiles.Machine file(Kind kind) {
		return MachineWorkFiles.all().stream().filter(m -> m.id().equals(AliveWorkplace.id(kind.file))).findFirst()
			.orElseThrow(() -> new AssertionError("no machine_work file " + kind.file + ": " + MachineWorkFiles.all()));
	}

	/** The worker feeds the turning machine from the store and brings what it makes back; a still twin gets nothing. */
	private static void works(GameTestHelper helper, Kind kind) {
		PartnerShowsCompatTests.clearLeftovers(helper);
		clearMachines(helper);
		villageWorkshop(helper);
		MachineWorkFiles.Machine file = file(kind);
		Container chest = chest(helper, new ItemStack(kind.input(), 20));
		BlockPos machine = rig(helper, kind, true);
		worker(helper, kind);
		helper.runAfterDelay(40, () -> helper.assertTrue(MachineWork.turning(helper.getLevel(), helper.absolutePos(machine), file.minSpeed()),
			kind + ": the motor doesn't turn the " + kind.file + " (" + helper.getBlockState(machine) + ", "
				+ helper.getBlockEntity(machine).saveWithoutMetadata(helper.getLevel().registryAccess()) + ")"));
		helper.succeedWhen(() -> {
			helper.assertTrue(count(chest, kind.input()) < 20, kind + ": no " + kind.input + " taken to the " + kind.file + " yet");
			helper.assertTrue(count(chest, kind.output()) > 0, kind + ": no " + kind.output + " in the store yet (the machine: "
				+ helper.getBlockEntity(machine.offset(file.feed().at())).saveWithoutMetadata(helper.getLevel().registryAccess()) + ")");
			clearMachines(helper);
		});
	}

	/** A machine that doesn't turn is fed nothing. */
	private static void stopped(GameTestHelper helper, Kind kind) {
		PartnerShowsCompatTests.clearLeftovers(helper);
		clearMachines(helper);
		villageWorkshop(helper);
		Container chest = chest(helper, new ItemStack(kind.input(), 20));
		rig(helper, kind, false);
		Villager villager = worker(helper, kind);
		helper.runAfterDelay(400, () -> {
			helper.assertTrue(count(chest, kind.input()) == 20, kind + ": " + kind.input + " went to a " + kind.file + " that doesn't turn: " + count(chest, kind.input()) + " left");
			helper.assertTrue(!MachineWork.busy(villager), kind + ": the worker set off for a machine that doesn't turn: " + ModAttachments.MACHINE_JOB.get(villager));
			clearMachines(helper);
			helper.succeed();
		});
	}

	/** A full store: the worker doesn't feed the machine. */
	private static void full(GameTestHelper helper, Kind kind) {
		PartnerShowsCompatTests.clearLeftovers(helper);
		clearMachines(helper);
		villageWorkshop(helper);
		Container chest = chest(helper, new ItemStack(kind.input(), 20));
		for (int i = 1; i < chest.getContainerSize(); i++) {
			chest.setItem(i, new ItemStack(Items.COBBLESTONE, 64));
		}
		rig(helper, kind, true);
		worker(helper, kind);
		helper.runAfterDelay(400, () -> {
			helper.assertTrue(count(chest, kind.input()) == 20, kind + ": the store is full but " + kind.input + " went to the " + kind.file);
			clearMachines(helper);
			helper.succeed();
		});
	}

	/** A worker saved mid-trip carrying the input comes back after the load and finishes the job. */
	private static void reload(GameTestHelper helper, Kind kind) {
		PartnerShowsCompatTests.clearLeftovers(helper);
		clearMachines(helper);
		ServerLevel level = helper.getLevel();
		villageWorkshop(helper);
		Container chest = chest(helper);
		BlockPos machine = rig(helper, kind, true);
		Villager villager = worker(helper, kind);
		MachineJob trip = new MachineJob(helper.absolutePos(machine), file(kind).id(), MachineJob.Stage.FEED, List.of(new ItemStack(kind.input(), 8)));
		ModAttachments.MACHINE_JOB.set(villager, trip);
		CompoundTag saved = new CompoundTag();
		villager.save(saved);
		villager.discard();
		Villager copy = (Villager) EntityType.loadEntityRecursive(saved, level, e -> e);
		helper.assertTrue(copy != null, "the worker didn't load");
		helper.assertTrue(trip.equals(ModAttachments.MACHINE_JOB.get(copy)), "the trip didn't survive: " + ModAttachments.MACHINE_JOB.get(copy));
		level.addFreshEntity(copy);
		helper.succeedWhen(() -> {
			helper.assertTrue(count(chest, kind.output()) > 0, kind + ": the reloaded worker hasn't brought any " + kind.output + " to the store ("
				+ ModAttachments.MACHINE_JOB.get(copy) + ")");
			helper.assertTrue(count(chest, kind.input()) == 0, kind + ": the carried " + kind.input + " came back unworked");
			clearMachines(helper);
		});
	}

	/**
	 * A job with machine work keeps its schedule: the rest of its work waits while a trip to a machine is on, but the
	 * schedule update (vanilla's priority 99) is never held up by one, so the worker still goes home at night.
	 */
	//$ gametest 'FabricGameTest.EMPTY_STRUCTURE'
	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
	public void aMachineTripNeverHoldsUpTheSchedule(GameTestHelper helper) {
		var work = net.minecraft.world.entity.ai.behavior.VillagerGoalPackages.getWorkPackage(VillagerProfession.FARMER, 0.5F);
		var out = MachineWork.packages(VillagerProfession.FARMER, work);
		helper.assertTrue(out.size() == work.size() + 1, "setup: the farmer's work has no machine work added (" + out.size() + " of " + work.size() + ")");
		int always = 0;
		for (var entry : work) {
			Object behavior = entry.getSecond();
			boolean kept = out.stream().anyMatch(p -> p.getSecond() == behavior);
			if (entry.getFirst() >= 99) {
				always++;
				helper.assertTrue(kept, "the farmer's " + behavior + " (priority " + entry.getFirst() + ") waits on machine trips");
			} else {
				helper.assertTrue(!kept, "the farmer's " + behavior + " (priority " + entry.getFirst() + ") runs during a machine trip");
			}
		}
		helper.assertTrue(always > 0, "setup: the farmer's work has no schedule update");
		helper.succeed();
	}

	//$ gametest_ticks_batch AREA '2400' '"machine_mill_works"'
	@GameTest(template = AREA, timeoutTicks = 2400, batch = "machine_mill_works")
	public void farmerMillsWheat(GameTestHelper helper) {
		works(helper, Kind.MILL);
	}

	//$ gametest_ticks_batch AREA '600' '"machine_mill_stopped"'
	@GameTest(template = AREA, timeoutTicks = 600, batch = "machine_mill_stopped")
	public void stillMillstoneGetsNothing(GameTestHelper helper) {
		stopped(helper, Kind.MILL);
	}

	//$ gametest_ticks_batch AREA '600' '"machine_mill_full"'
	@GameTest(template = AREA, timeoutTicks = 600, batch = "machine_mill_full")
	public void fullStorePausesTheMill(GameTestHelper helper) {
		full(helper, Kind.MILL);
	}

	//$ gametest_ticks_batch AREA '2400' '"machine_mill_reload"'
	@GameTest(template = AREA, timeoutTicks = 2400, batch = "machine_mill_reload")
	public void millTripSurvivesReload(GameTestHelper helper) {
		reload(helper, Kind.MILL);
	}

	//$ gametest_ticks_batch AREA '2400' '"machine_press_works"'
	@GameTest(template = AREA, timeoutTicks = 2400, batch = "machine_press_works")
	public void armorerPressesSheets(GameTestHelper helper) {
		works(helper, Kind.PRESS);
	}

	//$ gametest_ticks_batch AREA '600' '"machine_press_stopped"'
	@GameTest(template = AREA, timeoutTicks = 600, batch = "machine_press_stopped")
	public void stillPressGetsNothing(GameTestHelper helper) {
		stopped(helper, Kind.PRESS);
	}

	//$ gametest_ticks_batch AREA '600' '"machine_press_full"'
	@GameTest(template = AREA, timeoutTicks = 600, batch = "machine_press_full")
	public void fullStorePausesThePress(GameTestHelper helper) {
		full(helper, Kind.PRESS);
	}

	//$ gametest_ticks_batch AREA '2400' '"machine_press_reload"'
	@GameTest(template = AREA, timeoutTicks = 2400, batch = "machine_press_reload")
	public void pressTripSurvivesReload(GameTestHelper helper) {
		reload(helper, Kind.PRESS);
	}

	//$ gametest_ticks_batch AREA '2400' '"machine_mixer_works"'
	@GameTest(template = AREA, timeoutTicks = 2400, batch = "machine_mixer_works")
	public void chefMixesDough(GameTestHelper helper) {
		works(helper, Kind.MIXER);
	}

	//$ gametest_ticks_batch AREA '600' '"machine_mixer_stopped"'
	@GameTest(template = AREA, timeoutTicks = 600, batch = "machine_mixer_stopped")
	public void stillMixerGetsNothing(GameTestHelper helper) {
		stopped(helper, Kind.MIXER);
	}

	//$ gametest_ticks_batch AREA '600' '"machine_mixer_full"'
	@GameTest(template = AREA, timeoutTicks = 600, batch = "machine_mixer_full")
	public void fullStorePausesTheMixer(GameTestHelper helper) {
		full(helper, Kind.MIXER);
	}

	//$ gametest_ticks_batch AREA '2400' '"machine_mixer_reload"'
	@GameTest(template = AREA, timeoutTicks = 2400, batch = "machine_mixer_reload")
	public void mixerTripSurvivesReload(GameTestHelper helper) {
		reload(helper, Kind.MIXER);
	}

	/**
	 * QA lane E, 2026-10-27, the wiki's "the speed that counts as turning (min_speed ... 30 for the mixer)", from both
	 * sides: a mixer turned at 29 RPM is fed nothing in 800 ticks; one at 30 RPM gets the Chef's flour.
	 */
	//$ gametest_ticks_batch AREA '1000' '"qa_mixer_29_rpm"'
	@GameTest(template = AREA, timeoutTicks = 1000, batch = "qa_mixer_29_rpm")
	public void aMixerTurnedAtTwentyNineRpmIsFedNothing(GameTestHelper helper) {
		mixerAt(helper, 29, false);
	}

	//$ gametest_ticks_batch AREA '2400' '"qa_mixer_30_rpm"'
	@GameTest(template = AREA, timeoutTicks = 2400, batch = "qa_mixer_30_rpm")
	public void aMixerTurnedAtThirtyRpmIsFed(GameTestHelper helper) {
		mixerAt(helper, 30, true);
	}

	/**
	 * QA lane E, 2026-10-27 (nightly survivor MachineWork:572): a trip set off to fetch for the mill, and the store filled
	 * up before the fetch (a player's cobblestone in every other slot): the farmer ends the trip and takes none of the
	 * wheat, as a full store pauses the mill.
	 */
	// Its own batch (B653): it clears every entity and Create block around it, so a neighbour in its batch (once
	// QaMachineFetchCompatTests' test, under the same name) lost its farmer and millstone.
	//$ gametest_ticks_batch AREA '600' '"machine_work_fetch_full_store"'
	@GameTest(template = AREA, timeoutTicks = 600, batch = "machine_work_fetch_full_store")
	public void aTripWhoseStoreFilledUpBeforeTheFetchTakesNothing(GameTestHelper helper) {
		PartnerShowsCompatTests.clearLeftovers(helper);
		clearMachines(helper);
		villageWorkshop(helper);
		Container chest = chest(helper, new ItemStack(Kind.MILL.input(), 20));
		for (int i = 1; i < chest.getContainerSize(); i++) {
			chest.setItem(i, new ItemStack(Items.COBBLESTONE, 64));
		}
		BlockPos mill = rig(helper, Kind.MILL, true);
		Villager farmer = worker(helper, Kind.MILL);
		ModAttachments.MACHINE_JOB.set(farmer, new MachineJob(helper.absolutePos(mill), file(Kind.MILL).id(), MachineJob.Stage.FETCH, List.of()));
		helper.runAfterDelay(300, () -> {
			helper.assertTrue(count(chest, Kind.MILL.input()) == 20, "the store is full but the farmer fetched wheat for the mill: "
				+ count(chest, Kind.MILL.input()) + " left, trip " + ModAttachments.MACHINE_JOB.get(farmer));
			helper.assertTrue(!MachineWork.busy(farmer), "the trip to fetch for a full store didn't end: " + ModAttachments.MACHINE_JOB.get(farmer));
			clearMachines(helper);
			helper.succeed();
		});
	}

	private static void mixerAt(GameTestHelper helper, int rpm, boolean fed) {
		PartnerShowsCompatTests.clearLeftovers(helper);
		clearMachines(helper);
		villageWorkshop(helper);
		Container chest = chest(helper, new ItemStack(Kind.MIXER.input(), 20));
		BlockPos mixer = new BlockPos(6, 4, 2);
		BlockPos cog = mixer.east();
		helper.setBlock(cog.above(), MechanicCompatTests.with(create("creative_motor").defaultBlockState(), "facing", "down"));
		motorSpeed(helper, cog.above(), rpm);
		helper.setBlock(cog, MechanicCompatTests.with(create("cogwheel").defaultBlockState(), "axis", "y"));
		helper.setBlock(mixer.below(2), create("basin").defaultBlockState());
		helper.setBlock(mixer, create("mechanical_mixer").defaultBlockState());
		Villager chef = worker(helper, Kind.MIXER);
		helper.runAfterDelay(40, () -> {
			float speed = io.github.jcondedata.aliveworkplace.mc.Nbt.getFloat(helper.getBlockEntity(mixer).saveWithoutMetadata(helper.getLevel().registryAccess()), "Speed");
			helper.assertTrue(Math.abs(speed) == rpm, "setup: the mixer turns at " + speed + " RPM, not " + rpm);
		});
		if (fed) {
			helper.succeedWhen(() -> {
				helper.assertTrue(count(chest, Kind.MIXER.input()) < 20 || MachineWork.busy(chef),
					"a mixer at " + rpm + " RPM (the file's min_speed is " + file(Kind.MIXER).minSpeed() + ") got no flour");
				clearMachines(helper);
			});
		} else {
			helper.runAfterDelay(800, () -> {
				helper.assertTrue(count(chest, Kind.MIXER.input()) == 20 && !MachineWork.busy(chef), "a mixer at " + rpm
					+ " RPM (under the file's min_speed " + file(Kind.MIXER).minSpeed() + ") was fed: " + count(chest, Kind.MIXER.input())
					+ " flour left, trip " + ModAttachments.MACHINE_JOB.get(chef));
				clearMachines(helper);
				helper.succeed();
			});
		}
	}

	//$ gametest_ticks_batch AREA '2400' '"machine_crusher_works"'
	@GameTest(template = AREA, timeoutTicks = 2400, batch = "machine_crusher_works")
	public void minerCrushesOre(GameTestHelper helper) {
		works(helper, Kind.CRUSHER);
	}

	//$ gametest_ticks_batch AREA '600' '"machine_crusher_stopped"'
	@GameTest(template = AREA, timeoutTicks = 600, batch = "machine_crusher_stopped")
	public void stillCrusherGetsNothing(GameTestHelper helper) {
		stopped(helper, Kind.CRUSHER);
	}

	//$ gametest_ticks_batch AREA '600' '"machine_crusher_full"'
	@GameTest(template = AREA, timeoutTicks = 600, batch = "machine_crusher_full")
	public void fullStorePausesTheCrusher(GameTestHelper helper) {
		full(helper, Kind.CRUSHER);
	}

	//$ gametest_ticks_batch AREA '2400' '"machine_crusher_reload"'
	@GameTest(template = AREA, timeoutTicks = 2400, batch = "machine_crusher_reload")
	public void crusherTripSurvivesReload(GameTestHelper helper) {
		reload(helper, Kind.CRUSHER);
	}

	/**
	 * A player's own work on a machine is left alone: gold ingots on the press's depot (Create presses them into golden
	 * sheets, which no file asks for) and bones in the millstone (milled into bone meal) are still being worked on, so
	 * seeing to the machine never carries them off to the village's store, and the machine gets no batch on top of them.
	 */
	//$ gametest_ticks_batch AREA '200' '"machine_players_input"'
	@GameTest(template = AREA, timeoutTicks = 200, batch = "machine_players_input")
	public void aPlayersInputIsLeftInTheMachine(GameTestHelper helper) {
		PartnerShowsCompatTests.clearLeftovers(helper);
		clearMachines(helper);
		ServerLevel level = helper.getLevel();
		villageWorkshop(helper);
		Container chest = chest(helper, new ItemStack(Items.IRON_INGOT, 16), new ItemStack(Items.WHEAT, 16));
		List<BlockPos> store = List.of(helper.absolutePos(CHEST));
		BlockPos press = rig(helper, Kind.PRESS, false);
		BlockPos depot = helper.absolutePos(press.offset(file(Kind.PRESS).feed().at()));
		BlockPos mill = new BlockPos(2, 3, 7);
		helper.setBlock(mill.below(), Blocks.STONE);
		helper.setBlock(mill, create("millstone").defaultBlockState());
		var stores = io.github.jcondedata.aliveworkplace.platform.Platform.get().items();
		helper.assertTrue(stores.insert(level, List.of(depot), new ItemStack(Items.GOLD_INGOT, 8)).isEmpty(), "setup: the depot takes no gold ingots");
		helper.assertTrue(stores.insert(level, List.of(helper.absolutePos(mill)), new ItemStack(Items.BONE, 8)).isEmpty(), "setup: the millstone takes no bones");
		MachineWork.tend(level, store, helper.absolutePos(press), file(Kind.PRESS), helper.absolutePos(CHEST));
		MachineWork.tend(level, store, helper.absolutePos(mill), file(Kind.MILL), helper.absolutePos(CHEST));
		helper.assertTrue(count(chest, Items.GOLD_INGOT) == 0, "the player's gold ingots were carried off the depot to the store");
		helper.assertTrue(count(chest, Items.BONE) == 0, "the player's bones were carried out of the millstone to the store");
		helper.assertTrue(stores.count(level, List.of(depot), Items.GOLD_INGOT) == 8, "the depot doesn't hold the player's 8 gold ingots");
		helper.assertTrue(stores.count(level, List.of(helper.absolutePos(mill)), Items.BONE) == 8, "the millstone doesn't hold the player's 8 bones");
		helper.assertTrue(count(chest, Items.IRON_INGOT) == 16 && count(chest, Items.WHEAT) == 16, "a machine still busy with a player's input got a batch from the store");
		clearMachines(helper);
		helper.succeed();
	}

	/** A worker killed on the way to the machine drops the wheat they carried; it doesn't vanish with them. */
	//$ gametest_ticks_batch AREA '200' '"machine_trip_death"'
	@GameTest(template = AREA, timeoutTicks = 200, batch = "machine_trip_death")
	public void aWorkerKilledMidTripDropsWhatTheyCarried(GameTestHelper helper) {
		PartnerShowsCompatTests.clearLeftovers(helper);
		clearMachines(helper);
		chest(helper);
		BlockPos machine = rig(helper, Kind.MILL, false);
		Villager villager = worker(helper, Kind.MILL);
		ModAttachments.MACHINE_JOB.set(villager, new MachineJob(helper.absolutePos(machine), file(Kind.MILL).id(), MachineJob.Stage.FEED,
			List.of(new ItemStack(Items.WHEAT, 12))));
		villager.hurt(helper.getLevel().damageSources().genericKill(), Float.MAX_VALUE);
		helper.assertTrue(villager.isDeadOrDying(), "setup: the worker didn't die");
		helper.runAfterDelay(5, () -> {
			int dropped = helper.getLevel().getEntitiesOfClass(net.minecraft.world.entity.item.ItemEntity.class, helper.getBounds().inflate(4),
				e -> e.getItem().is(Items.WHEAT)).stream().mapToInt(e -> e.getItem().getCount()).sum();
			helper.assertTrue(dropped == 12, "the killed worker dropped " + dropped + " of the 12 wheat they carried");
			clearMachines(helper);
			helper.succeed();
		});
	}

	/**
	 * A store with room for one stack takes one stack of what the millstone made; the rest stays in the millstone for
	 * the next time (it isn't dropped on the ground, where it would despawn).
	 */
	//$ gametest_ticks_batch AREA '200' '"machine_output_room"'
	@GameTest(template = AREA, timeoutTicks = 200, batch = "machine_output_room")
	public void whatDoesntFitStaysInTheMachine(GameTestHelper helper) {
		PartnerShowsCompatTests.clearLeftovers(helper);
		clearMachines(helper);
		ServerLevel level = helper.getLevel();
		villageWorkshop(helper);
		Container chest = chest(helper);
		for (int i = 1; i < chest.getContainerSize(); i++) {
			chest.setItem(i, new ItemStack(Items.COBBLESTONE, 64));
		}
		BlockPos mill = rig(helper, Kind.MILL, false);
		var be = helper.getBlockEntity(mill);
		CompoundTag tag = be.saveWithoutMetadata(level.registryAccess());
		CompoundTag out = tag.getCompound("OutputInventory");
		net.minecraft.nbt.ListTag items = new net.minecraft.nbt.ListTag();
		ItemStack[] made = {new ItemStack(Kind.MILL.output(), 64), new ItemStack(Kind.MILL.output(), 64), new ItemStack(Items.WHEAT_SEEDS, 10)};
		for (int i = 0; i < made.length; i++) {
			CompoundTag item = (CompoundTag) made[i].save(level.registryAccess(), new CompoundTag());
			item.putInt("Slot", i);
			items.add(item);
		}
		out.put("Items", items);
		tag.put("OutputInventory", out);
		be.loadWithComponents(tag, level.registryAccess());
		var stores = io.github.jcondedata.aliveworkplace.platform.Platform.get().items();
		BlockPos at = helper.absolutePos(mill);
		long before = stores.count(level, List.of(at), Kind.MILL.output()) + stores.count(level, List.of(at), Items.WHEAT_SEEDS);
		helper.assertTrue(before == 138, "setup: the millstone holds " + before + " of the 138 made items put in it (" + tag + ")");
		MachineWork.tend(level, List.of(helper.absolutePos(CHEST)), at, file(Kind.MILL), helper.absolutePos(CHEST));
		long left = stores.count(level, List.of(at), Kind.MILL.output()) + stores.count(level, List.of(at), Items.WHEAT_SEEDS);
		long stored = count(chest, Kind.MILL.output()) + count(chest, Items.WHEAT_SEEDS);
		int dropped = level.getEntitiesOfClass(net.minecraft.world.entity.item.ItemEntity.class, helper.getBounds().inflate(4)).stream()
			.mapToInt(e -> e.getItem().getCount()).sum();
		helper.assertTrue(dropped == 0, dropped + " made items were dropped on the ground for want of room in the store");
		helper.assertTrue(stored > 0 && stored + left == 138, "the store got " + stored + " and the millstone kept " + left + " of 138");
		clearMachines(helper);
		helper.succeed();
	}

	/** Config {@code machineWork} off: the farmer leaves the turning millstone alone. */
	//$ gametest_ticks_batch AREA '600' '"machine_work_off"'
	@GameTest(template = AREA, timeoutTicks = 600, batch = "machine_work_off")
	public void machineWorkSwitchedOff(GameTestHelper helper) {
		PartnerShowsCompatTests.clearLeftovers(helper);
		clearMachines(helper);
		villageWorkshop(helper);
		Container chest = chest(helper, new ItemStack(Items.WHEAT, 20));
		rig(helper, Kind.MILL, true);
		worker(helper, Kind.MILL);
		boolean before = MachineWork.ENABLED;
		MachineWork.ENABLED = false;
		helper.runAfterDelay(400, () -> {
			MachineWork.ENABLED = before;
			helper.assertTrue(count(chest, Items.WHEAT) == 20, "machineWork off, but wheat went to the millstone");
			clearMachines(helper);
			helper.succeed();
		});
	}

	// ---- B243: a player's own machines and chests ----------------------------------------------------------------------

	/** Puts {@code made} into the millstone's output, as a player's automation leaves it there. */
	private static void millOutput(GameTestHelper helper, BlockPos mill, ItemStack... made) {
		ServerLevel level = helper.getLevel();
		var be = helper.getBlockEntity(mill);
		CompoundTag tag = be.saveWithoutMetadata(level.registryAccess());
		CompoundTag out = tag.getCompound("OutputInventory");
		net.minecraft.nbt.ListTag items = new net.minecraft.nbt.ListTag();
		for (int i = 0; i < made.length; i++) {
			CompoundTag item = (CompoundTag) made[i].save(level.registryAccess(), new CompoundTag());
			item.putInt("Slot", i);
			items.add(item);
		}
		out.put("Items", items);
		tag.put("OutputInventory", out);
		be.loadWithComponents(tag, level.registryAccess());
	}

	/** The chest and the workstation only (x 0-4): the machines at x 5 and beyond stand outside it. */
	private static void villageStoreOnly(GameTestHelper helper) {
		villageBuilding(helper, new BlockPos(0, 2, 0), new net.minecraft.core.Vec3i(5, 4, 8));
	}

	/**
	 * B243: a player's own chest by a Create Toolbox, outside any village building, keeps its iron ingots and wheat: the
	 * Mechanic hired at the toolbox and an armorer by the same chest never feed them to the player's turning press or
	 * millstone, and neither machine is on anyone's round.
	 */
	//$ gametest_ticks_batch AREA '800' '"machine_players_toolbox"'
	@GameTest(template = AREA, timeoutTicks = 800, batch = "machine_players_toolbox")
	public void aPlayersChestByAToolboxKeepsItsIronIngots(GameTestHelper helper) {
		PartnerShowsCompatTests.clearLeftovers(helper);
		clearMachines(helper);
		noVillage(helper);
		ServerLevel level = helper.getLevel();
		Container chest = chest(helper, new ItemStack(Items.IRON_INGOT, 64), new ItemStack(Items.WHEAT, 16));
		BlockPos press = rig(helper, Kind.PRESS, true);
		BlockPos mill = new BlockPos(10, 3, 2);
		helper.setBlock(mill.below(), MechanicCompatTests.with(create("creative_motor").defaultBlockState(), "facing", "up"));
		helper.setBlock(mill, create("millstone").defaultBlockState());
		Villager armorer = worker(helper, Kind.PRESS);
		BlockPos toolbox = new BlockPos(2, 2, 6);
		helper.setBlock(toolbox, create("brown_toolbox").defaultBlockState());
		Villager mechanic = helper.spawn(EntityType.VILLAGER, new BlockPos(3, 2, 5));
		Stations.assign(level, mechanic, helper.absolutePos(toolbox), ModVillagers.MECHANIC);
		helper.assertTrue(io.github.jcondedata.aliveworkplace.mechanic.MechanicWork.isMechanic(mechanic), "setup: no Mechanic at the toolbox");
		helper.assertTrue(!io.github.jcondedata.aliveworkplace.mechanic.VillageMachines.village(level, helper.absolutePos(CHEST)),
			"setup: the player's chest stands in a village building");
		BlockPos depot = helper.absolutePos(press.offset(file(Kind.PRESS).feed().at()));
		var stores = io.github.jcondedata.aliveworkplace.platform.Platform.get().items();
		helper.runAfterDelay(40, () -> {
			helper.assertTrue(MachineWork.turning(level, helper.absolutePos(press), file(Kind.PRESS).minSpeed()), "setup: the player's press doesn't turn");
			helper.assertTrue(MachineWork.turning(level, helper.absolutePos(mill), file(Kind.MILL).minSpeed()), "setup: the player's millstone doesn't turn");
		});
		helper.runAfterDelay(700, () -> {
			helper.assertTrue(count(chest, Items.IRON_INGOT) == 64, "the player's iron ingots went to the player's press: " + count(chest, Items.IRON_INGOT) + " of 64 left");
			helper.assertTrue(count(chest, Items.WHEAT) == 16, "the player's wheat went to the player's millstone: " + count(chest, Items.WHEAT) + " of 16 left");
			helper.assertTrue(stores.count(level, List.of(depot), Items.IRON_INGOT) == 0 && stores.count(level, List.of(depot), Kind.PRESS.output()) == 0,
				"the player's press's depot was given iron");
			helper.assertTrue(count(chest, Kind.PRESS.output()) == 0 && count(chest, Kind.MILL.output()) == 0, "sheets or flour turned up in the player's chest");
			helper.assertTrue(io.github.jcondedata.aliveworkplace.mechanic.MechanicWork.findMachines(level, helper.absolutePos(toolbox)).isEmpty(),
				"the player's machines are on the Mechanic's round: " + io.github.jcondedata.aliveworkplace.mechanic.MechanicWork.findMachines(level, helper.absolutePos(toolbox)));
			helper.assertTrue(MachineWork.findMachines(level, helper.absolutePos(STATION), file(Kind.PRESS)).isEmpty(), "the player's press counts as the armorer's machine");
			helper.assertTrue(!MachineWork.busy(armorer) && !MachineWork.busy(mechanic), "a worker set off for the player's machine");
			clearMachines(helper);
			helper.succeed();
		});
	}

	/**
	 * B243: a player's own millstone outside the village's buildings is never fed from the village's store, and what the
	 * player's automation left in it (flour) is never carried off, by the farmer's trips or by seeing to it on the spot.
	 */
	//$ gametest_ticks_batch AREA '600' '"machine_players_mill"'
	@GameTest(template = AREA, timeoutTicks = 600, batch = "machine_players_mill")
	public void aPlayersMachineIsNeverFedNorEmptied(GameTestHelper helper) {
		PartnerShowsCompatTests.clearLeftovers(helper);
		clearMachines(helper);
		noVillage(helper);
		villageStoreOnly(helper);
		ServerLevel level = helper.getLevel();
		Container chest = chest(helper, new ItemStack(Items.WHEAT, 20));
		BlockPos mill = rig(helper, Kind.MILL, true);
		millOutput(helper, mill, new ItemStack(Kind.MILL.output(), 32));
		worker(helper, Kind.MILL);
		BlockPos at = helper.absolutePos(mill);
		var stores = io.github.jcondedata.aliveworkplace.platform.Platform.get().items();
		helper.assertTrue(stores.count(level, List.of(at), Kind.MILL.output()) == 32, "setup: the millstone doesn't hold the player's 32 flour");
		helper.assertTrue(io.github.jcondedata.aliveworkplace.mechanic.VillageMachines.village(level, helper.absolutePos(CHEST))
			&& !io.github.jcondedata.aliveworkplace.mechanic.VillageMachines.village(level, at), "setup: the store should be the village's and the millstone not");
		helper.runAfterDelay(400, () -> {
			helper.assertTrue(MachineWork.turning(level, at, file(Kind.MILL).minSpeed()), "setup: the player's millstone doesn't turn");
			helper.assertTrue(MachineWork.tend(level, List.of(helper.absolutePos(CHEST)), at, file(Kind.MILL), helper.absolutePos(CHEST))
				== io.github.jcondedata.aliveworkplace.mechanic.MechanicWork.Tended.FINE, "seeing to the player's millstone did something");
			helper.assertTrue(count(chest, Items.WHEAT) == 20, "the village's wheat went into the player's millstone: " + count(chest, Items.WHEAT) + " of 20 left");
			helper.assertTrue(count(chest, Kind.MILL.output()) == 0, "the player's flour was carried to the village's store");
			helper.assertTrue(stores.count(level, List.of(at), Kind.MILL.output()) == 32, "the player's millstone lost its flour: "
				+ stores.count(level, List.of(at), Kind.MILL.output()) + " of 32 left");
			clearMachines(helper);
			helper.succeed();
		});
	}

	/**
	 * B243, an older save: a worker loaded mid-trip to what is now a player's machine (outside the village's buildings)
	 * doesn't feed it: the iron they carried goes back to the village's store.
	 */
	//$ gametest_ticks_batch AREA '1200' '"machine_players_reload"'
	@GameTest(template = AREA, timeoutTicks = 1200, batch = "machine_players_reload")
	public void aTripSavedToAPlayersMachineBringsItsLoadBack(GameTestHelper helper) {
		PartnerShowsCompatTests.clearLeftovers(helper);
		clearMachines(helper);
		noVillage(helper);
		villageStoreOnly(helper);
		ServerLevel level = helper.getLevel();
		Container chest = chest(helper);
		BlockPos press = rig(helper, Kind.PRESS, true);
		Villager villager = worker(helper, Kind.PRESS);
		ModAttachments.MACHINE_JOB.set(villager, new MachineJob(helper.absolutePos(press), file(Kind.PRESS).id(), MachineJob.Stage.FEED,
			List.of(new ItemStack(Items.IRON_INGOT, 8))));
		CompoundTag saved = new CompoundTag();
		villager.save(saved);
		villager.discard();
		Villager copy = (Villager) EntityType.loadEntityRecursive(saved, level, e -> e);
		helper.assertTrue(copy != null, "the worker didn't load");
		level.addFreshEntity(copy);
		BlockPos depot = helper.absolutePos(press.offset(file(Kind.PRESS).feed().at()));
		var stores = io.github.jcondedata.aliveworkplace.platform.Platform.get().items();
		helper.succeedWhen(() -> {
			helper.assertTrue(stores.count(level, List.of(depot), Items.IRON_INGOT) == 0 && stores.count(level, List.of(depot), Kind.PRESS.output()) == 0,
				"the reloaded worker fed the player's press");
			helper.assertTrue(count(chest, Items.IRON_INGOT) == 8 && !MachineWork.busy(copy), "the carried iron isn't back in the store yet: "
				+ count(chest, Items.IRON_INGOT) + " (" + ModAttachments.MACHINE_JOB.get(copy) + ")");
			helper.assertTrue(count(chest, Kind.PRESS.output()) == 0, "iron sheets turned up in the store");
			clearMachines(helper);
		});
	}

	// ---- B244: a trip that can't go on puts its load back ----------------------------------------------------------------

	/** The wheat in the chest and lying on the ground around the area. */
	private static int wheatAround(GameTestHelper helper, Container chest) {
		return count(chest, Items.WHEAT) + helper.getLevel().getEntitiesOfClass(net.minecraft.world.entity.item.ItemEntity.class,
			helper.getBounds().inflate(8), e -> e.isAlive() && e.getItem().is(Items.WHEAT)).stream().mapToInt(e -> e.getItem().getCount()).sum();
	}

	/**
	 * B244: a Novice farmer saved mid-trip with 12 wheat (the work day over, the trip waiting for the morning) whose
	 * composter is broken loses the job; the 12 wheat go back into the village's store, none kept in the record.
	 */
	//$ gametest_ticks_batch AREA '600' '"machine_trip_job_lost"'
	@GameTest(template = AREA, timeoutTicks = 600, batch = "machine_trip_job_lost")
	public void aWorkerWhoLosesTheJobMidTripPutsTheLoadBack(GameTestHelper helper) {
		PartnerShowsCompatTests.clearLeftovers(helper);
		clearMachines(helper);
		villageWorkshop(helper);
		Container chest = chest(helper);
		BlockPos mill = rig(helper, Kind.MILL, false);
		Villager villager = worker(helper, Kind.MILL);
		villager.setVillagerXp(0); // a Novice who never traded (Stations.assign gives 1 xp to hold the job): vanilla takes the job back
		helper.assertTrue(villager.getVillagerData().getLevel() == 1, "setup: the farmer isn't a Novice");
		ModAttachments.MACHINE_JOB.set(villager, new MachineJob(helper.absolutePos(mill), file(Kind.MILL).id(), MachineJob.Stage.FEED,
			List.of(new ItemStack(Items.WHEAT, 12))));
		helper.setDayTime(13000);
		helper.destroyBlock(STATION);
		helper.succeedWhen(() -> {
			helper.assertTrue(villager.getVillagerData().getProfession() == VillagerProfession.NONE, "the farmer hasn't lost the job yet");
			helper.assertTrue(!MachineWork.busy(villager), "the jobless farmer still holds the trip: " + ModAttachments.MACHINE_JOB.get(villager));
			helper.assertTrue(count(chest, Items.WHEAT) == 12, "the store got " + count(chest, Items.WHEAT) + " of the 12 wheat carried");
			helper.assertTrue(wheatAround(helper, chest) == 12, "the wheat came to " + wheatAround(helper, chest) + ", not 12");
			clearMachines(helper);
		});
	}

	/**
	 * B244: config {@code machineWork} switched off while a farmer carries 12 wheat to the turning millstone: the trip
	 * ends, the wheat goes back into the store and none into the millstone.
	 */
	//$ gametest_ticks_batch AREA '300' '"machine_trip_switched_off"'
	@GameTest(template = AREA, timeoutTicks = 300, batch = "machine_trip_switched_off")
	public void machineWorkSwitchedOffMidTripPutsTheLoadBack(GameTestHelper helper) {
		PartnerShowsCompatTests.clearLeftovers(helper);
		clearMachines(helper);
		villageWorkshop(helper);
		Container chest = chest(helper);
		BlockPos mill = rig(helper, Kind.MILL, true);
		Villager villager = worker(helper, Kind.MILL);
		ModAttachments.MACHINE_JOB.set(villager, new MachineJob(helper.absolutePos(mill), file(Kind.MILL).id(), MachineJob.Stage.FEED,
			List.of(new ItemStack(Items.WHEAT, 12))));
		boolean before = MachineWork.ENABLED;
		MachineWork.ENABLED = false;
		PartnerShowsCompatTests.after(helper, () -> MachineWork.ENABLED = before);
		var stores = io.github.jcondedata.aliveworkplace.platform.Platform.get().items();
		BlockPos feed = helper.absolutePos(mill.offset(file(Kind.MILL).feed().at()));
		helper.succeedWhen(() -> {
			helper.assertTrue(!MachineWork.busy(villager), "machineWork off, but the trip is still on: " + ModAttachments.MACHINE_JOB.get(villager));
			helper.assertTrue(stores.count(helper.getLevel(), List.of(feed), Items.WHEAT) == 0, "machineWork off, but wheat went into the millstone");
			helper.assertTrue(count(chest, Items.WHEAT) == 12, "the store got " + count(chest, Items.WHEAT) + " of the 12 wheat carried");
			helper.assertTrue(wheatAround(helper, chest) == 12, "the wheat came to " + wheatAround(helper, chest) + ", not 12");
			clearMachines(helper);
		});
	}

	/** B244: with the store full, a trip switched off drops all 12 wheat at the worker's feet: none is lost. */
	//$ gametest_ticks_batch AREA '300' '"machine_trip_off_full"'
	@GameTest(template = AREA, timeoutTicks = 300, batch = "machine_trip_off_full")
	public void aTripEndedWithAFullStoreDropsTheLoad(GameTestHelper helper) {
		PartnerShowsCompatTests.clearLeftovers(helper);
		clearMachines(helper);
		villageWorkshop(helper);
		Container chest = chest(helper);
		for (int i = 0; i < chest.getContainerSize(); i++) {
			chest.setItem(i, new ItemStack(Items.COBBLESTONE, 64));
		}
		BlockPos mill = rig(helper, Kind.MILL, true);
		Villager villager = worker(helper, Kind.MILL);
		ModAttachments.MACHINE_JOB.set(villager, new MachineJob(helper.absolutePos(mill), file(Kind.MILL).id(), MachineJob.Stage.RETURN,
			List.of(new ItemStack(Items.WHEAT, 12))));
		boolean before = MachineWork.ENABLED;
		MachineWork.ENABLED = false;
		PartnerShowsCompatTests.after(helper, () -> MachineWork.ENABLED = before);
		helper.succeedWhen(() -> {
			helper.assertTrue(!MachineWork.busy(villager), "machineWork off, but the trip is still on: " + ModAttachments.MACHINE_JOB.get(villager));
			int dropped = wheatAround(helper, chest) - count(chest, Items.WHEAT);
			helper.assertTrue(dropped == 12, "the worker dropped " + dropped + " of the 12 wheat carried");
			clearMachines(helper);
		});
	}

	/**
	 * B244: a farmer saved mid-trip with 12 wheat for the millstone (the work day over) who takes another job, an
	 * armorer's at a blast furnace, keeps no millstone trip: the 12 wheat go back into the village's store, none kept in
	 * the record, though the new job has machine work of its own (the press).
	 */
	//$ gametest_ticks_batch AREA '300' '"machine_trip_new_job"'
	@GameTest(template = AREA, timeoutTicks = 300, batch = "machine_trip_new_job")
	public void aWorkerWhoTakesAnotherJobMidTripPutsTheLoadBack(GameTestHelper helper) {
		PartnerShowsCompatTests.clearLeftovers(helper);
		clearMachines(helper);
		villageWorkshop(helper);
		Container chest = chest(helper);
		BlockPos mill = rig(helper, Kind.MILL, false);
		Villager villager = worker(helper, Kind.MILL);
		ModAttachments.MACHINE_JOB.set(villager, new MachineJob(helper.absolutePos(mill), file(Kind.MILL).id(), MachineJob.Stage.FEED,
			List.of(new ItemStack(Items.WHEAT, 12))));
		helper.setDayTime(13000);
		BlockPos furnace = new BlockPos(2, 2, 6);
		helper.setBlock(furnace, Blocks.BLAST_FURNACE);
		Stations.assign(helper.getLevel(), villager, helper.absolutePos(furnace), VillagerProfession.ARMORER);
		helper.assertTrue(villager.getVillagerData().getProfession() == VillagerProfession.ARMORER, "setup: the farmer didn't become an armorer");
		helper.succeedWhen(() -> {
			helper.assertTrue(!MachineWork.busy(villager), "the armorer still holds the farmer's millstone trip: " + ModAttachments.MACHINE_JOB.get(villager));
			helper.assertTrue(count(chest, Items.WHEAT) == 12, "the store got " + count(chest, Items.WHEAT) + " of the 12 wheat carried");
			helper.assertTrue(wheatAround(helper, chest) == 12, "the wheat came to " + wheatAround(helper, chest) + ", not 12");
			clearMachines(helper);
		});
	}
}
