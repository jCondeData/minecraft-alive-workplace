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

	/** Config {@code machineWork} off: the farmer leaves the turning millstone alone. */
	//$ gametest_ticks_batch AREA '600' '"machine_work_off"'
	@GameTest(template = AREA, timeoutTicks = 600, batch = "machine_work_off")
	public void machineWorkSwitchedOff(GameTestHelper helper) {
		PartnerShowsCompatTests.clearLeftovers(helper);
		clearMachines(helper);
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
}
