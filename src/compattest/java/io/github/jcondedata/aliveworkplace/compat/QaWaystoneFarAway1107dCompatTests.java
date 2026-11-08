package io.github.jcondedata.aliveworkplace.compat;

import io.github.jcondedata.aliveworkplace.blueprint.Blueprint;
import io.github.jcondedata.aliveworkplace.blueprint.BlueprintData;
import io.github.jcondedata.aliveworkplace.blueprint.BlueprintLibrary;
import io.github.jcondedata.aliveworkplace.build.BuildPlan;
import io.github.jcondedata.aliveworkplace.build.BuildSiteManager;
import io.github.jcondedata.aliveworkplace.colony.Settlers;
import io.github.jcondedata.aliveworkplace.hall.Caravans;
import io.github.jcondedata.aliveworkplace.hall.VillageHallBlockEntity;
import io.github.jcondedata.aliveworkplace.hall.VillageWaystones;
import io.github.jcondedata.aliveworkplace.realm.RealmData;
import io.github.jcondedata.aliveworkplace.registry.ModBlocks;
import io.github.jcondedata.aliveworkplace.story.LostCaravanStory;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.BooleanSupplier;
import java.util.function.Supplier;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Container;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;

/**
 * QA (lane D, 2026-11-07), ROADMAP 41.3 "Caravans and settlers travel by waystone" with the real Waystones mod, when the
 * other village is FAR off and its chunks are NOT loaded (the usual case on a server: villages are hundreds or thousands
 * of blocks apart, and the player is at one of them). Spec: "Between two villages that both have a waystone, trade
 * caravans and settlers walk to their stone and step out at the other village's instead of walking the whole way; if
 * either stone is missing or broken, they walk as today." Each far village is 1,600 blocks east of Ashford, built while
 * its chunks are loaded and then left until they unload.
 * <ul>
 * <li>A caravan to the far, unloaded village goes by waystone, waits on the road (goods kept) while that village is
 * unloaded, and steps out there when it loads again: 32 logs in its chest, 32 left at home, none on the road.</li>
 * <li>Control: the same with the far village kept loaded (forced).</li>
 * <li>A caravan FROM the far village, saved and read back mid-trip, steps out at Ashford with the far village unloaded.</li>
 * <li>The far stone broken before its village unloads: the caravan walks the road, and arrives with its goods.</li>
 * <li>Settlers through the far, unloaded village's stone step out there (not walking) when their time is up.</li>
 * <li>The far stone broken while the settlers are on their way (then the village unloads): they walk the whole road.</li>
 * </ul>
 * The village helpers follow WaystoneTravelCompatTests' and WaystoneMapQaCompatTests' (private there).
 */
public class QaWaystoneFarAway1107dCompatTests implements FabricGameTest {
	private static final org.slf4j.Logger LOGGER = org.slf4j.LoggerFactory.getLogger("aliveworkplace-qa");
	private static final String AREA = "aliveworkplace_compat:huge_area";
	private static final String CARAVANS = "aliveworkplace_caravans";
	private static final BlockPos HALL_A = new BlockPos(1, 2, 1);
	private static final BlockPos PLAZA_A = new BlockPos(3, 2, 3);
	private static final BlockPos STORE_A = new BlockPos(14, 2, 1);
	private static final BlockPos CHEST_A = new BlockPos(14, 2, 3);
	/** The stone's spot in the plaza (lower half), as tools/blueprints/waystones.py draws it. */
	private static final BlockPos STONE = new BlockPos(3, 2, 3);
	/** How far east the far village stands. */
	private static final int AWAY = 1600;
	private static final int UNLOAD_TICKS = 1000;

	/** A far village: its hall, plaza, stone (lower half), Storehouse and chest, all absolute. */
	private record Far(BlockPos hall, BlockPos origin, BlockPos stone, BlockPos store, BlockPos chest, BuildPlan plan) {
		List<BlockPos> spots() {
			return List.of(hall, stone, store, chest);
		}
	}

	/** One test's state: what to undo at the end, once. */
	private static final class Run {
		final GameTestHelper helper;
		final Far far;
		final int chance = LostCaravanStory.CHANCE;
		final List<long[]> forced = new ArrayList<>();
		boolean cleaned;

		Run(GameTestHelper helper, Far far) {
			this.helper = helper;
			this.far = far;
		}

		void clean() {
			if (cleaned) {
				return;
			}
			cleaned = true;
			ServerLevel level = helper.getLevel();
			LostCaravanStory.CHANCE = chance;
			for (long[] c : forced) {
				level.setChunkForced((int) c[0], (int) c[1], false);
			}
			Caravans.Data data = Caravans.Data.get(level);
			for (BlockPos hall : List.of(a(helper), far.hall())) {
				data.remove(hall);
				VillageWaystones.Data.get(level).forget(hall);
			}
			RealmData.get(level.getServer()).removeOrder(GlobalPos.of(level.dimension(), a(helper)));
			BuildSiteManager.get(level).forgetFinished(placement(level, helper.absolutePos(PLAZA_A)));
			BuildSiteManager.get(level).forgetFinished(placement(level, far.origin()));
			if (level.getBlockEntity(far.chest()) instanceof Container c) {
				c.clearContent();
			}
			if (helper.getLevel().getBlockEntity(helper.absolutePos(CHEST_A)) instanceof Container c) {
				c.clearContent();
			}
			for (BuildPlan.Stage stage : List.of(BuildPlan.Stage.LANDSCAPE, BuildPlan.Stage.DECORATION, BuildPlan.Stage.STRUCTURE, BuildPlan.Stage.FOUNDATION)) {
				for (BuildPlan.Step step : far.plan().steps(stage)) {
					if (step.secondaryPos() != null) {
						level.setBlock(step.secondaryPos(), Blocks.AIR.defaultBlockState(), 3);
					}
					level.setBlock(step.pos(), Blocks.AIR.defaultBlockState(), 3);
				}
			}
			for (BlockPos p : List.of(far.chest(), far.store(), far.hall())) {
				level.setBlock(p, Blocks.AIR.defaultBlockState(), 3);
			}
		}

		/** Runs {@code body}; anything it throws cleans up first. */
		void guarded(Runnable body) {
			try {
				body.run();
			} catch (RuntimeException | Error e) {
				clean();
				throw e;
			}
		}

		/** Polls {@code ready} every 5 ticks for up to {@code ticks}, then runs {@code then}; fails with {@code fail} if it never is. */
		void waitFor(BooleanSupplier ready, int ticks, Supplier<String> fail, Runnable then) {
			waitFor(ready, ticks, 5, fail, then);
		}

		/** The same, polling every {@code step} ticks (1: a state that may last a tick or two is seen). */
		void waitFor(BooleanSupplier ready, int ticks, int step, Supplier<String> fail, Runnable then) {
			guarded(() -> {
				if (ready.getAsBoolean()) {
					then.run();
					return;
				}
				if (ticks <= 0) {
					throw new GameTestAssertException(fail.get());
				}
				helper.runAfterDelay(step, () -> waitFor(ready, ticks - step, step, fail, then));
			});
		}

		/**
		 * Polls {@code ready} every tick until it is, then runs {@code then}; fails with {@code fail} once {@code clock} is
		 * overdue after at least {@code minTicks} ticks (B1036: chunks load and unload on other threads, in real time).
		 */
		void waitOnChunks(BooleanSupplier ready, ChunkWaits.Clock clock, int minTicks, Supplier<String> fail, Runnable then) {
			guarded(() -> {
				if (ready.getAsBoolean()) {
					then.run();
					return;
				}
				if (clock.overdue(minTicks)) {
					throw new GameTestAssertException(fail.get() + " (waited " + clock + ")");
				}
				helper.runAfterDelay(1, () -> waitOnChunks(ready, clock, minTicks, fail, then));
			});
		}

		/** Waits until the far village's chunks are no longer loaded (its spots), then runs {@code then}. */
		void whenUnloaded(Runnable then) {
			ServerLevel level = helper.getLevel();
			ChunkWaits.Clock clock = ChunkWaits.start(level);
			waitOnChunks(() -> far.spots().stream().noneMatch(level::isLoaded), clock, UNLOAD_TICKS,
				() -> "setup: the far village's chunks never unloaded (loaded: " + far.spots().stream().filter(level::isLoaded).map(BlockPos::toShortString).toList() + ")",
				() -> {
					LOGGER.info("QA 41.3 far: the far village at {} unloaded after {}", far.hall().toShortString(), clock);
					then.run();
				});
		}

		/**
		 * Once Farbrook's chunk (loaded by the test: forced, or a block set in it) is running, its hall's block entity
		 * ticking (B1036: its neighbours and entities load on other threads after the forcing), runs {@code then}.
		 */
		void whenRunning(Runnable then) {
			ServerLevel level = helper.getLevel();
			ChunkWaits.Clock clock = ChunkWaits.start(level);
			waitOnChunks(() -> ChunkWaits.running(level, far.hall()), clock, ChunkWaits.MIN_TICKS,
				() -> "setup: Farbrook's chunk never started running (" + ChunkWaits.state(level, far.hall()) + ")",
				() -> {
					if (clock.ticks() > 0) {
						LOGGER.info("QA 41.3 far: Farbrook's chunk running after {}", clock);
					}
					then.run();
				});
		}

		/** Forces the far village's chunks loaded (a player there) until the end of the test. */
		void forceLoad() {
			ServerLevel level = helper.getLevel();
			for (int cx = (far.hall().getX() - 12) >> 4; cx <= (far.hall().getX() + 12) >> 4; cx++) {
				for (int cz = (far.hall().getZ() - 12) >> 4; cz <= (far.hall().getZ() + 12) >> 4; cz++) {
					level.setChunkForced(cx, cz, true);
					forced.add(new long[] {cx, cz});
				}
			}
		}

		/**
		 * The far village as a player left it: loaded (forced) until its hall has had its first round (it's on the caravans'
		 * list and its Storehouse is known), then {@code then}.
		 */
		void settled(Runnable then) {
			ServerLevel level = helper.getLevel();
			// its first round is its first tick (VillageHallBlockEntity.serverTick), so 200 ticks of a running chunk are plenty
			whenRunning(() -> waitFor(() -> level.isLoaded(far.hall()) && Caravans.Data.get(level).village(far.hall()) != null, 200,
				() -> "setup: Farbrook's hall never had its first round while its chunk was running (" + ChunkWaits.state(level, far.hall()) + ")", then));
		}

		/** The player leaves the far village: its chunks no longer forced; once they've unloaded, {@code then}. */
		void leave(Runnable then) {
			ServerLevel level = helper.getLevel();
			for (long[] c : forced) {
				level.setChunkForced((int) c[0], (int) c[1], false);
			}
			forced.clear();
			whenUnloaded(then);
		}

		void pass(String checked, Object... args) {
			clean();
			LOGGER.info("QA 41.3 far: " + checked, args);
			helper.succeed();
		}
	}

	private static BlockPos a(GameTestHelper helper) {
		return helper.absolutePos(HALL_A);
	}

	private static BlueprintData.Placement placement(ServerLevel level, BlockPos origin) {
		return new BlueprintData.Placement(level.dimension().location(), origin, Rotation.NONE, Mirror.NONE);
	}

	/** Sets every block of the plaza at the absolute {@code origin} as a finished one stands, and records it finished. */
	private static BuildPlan plaza(ServerLevel level, BlockPos origin) {
		Blueprint blueprint = BlueprintLibrary.get(level, VillageWaystones.PLAZA).orElseThrow(() -> new GameTestAssertException("no plaza blueprint"));
		BuildPlan plan = BuildPlan.create(blueprint, placement(level, origin));
		for (BuildPlan.Stage stage : List.of(BuildPlan.Stage.FOUNDATION, BuildPlan.Stage.STRUCTURE, BuildPlan.Stage.DECORATION, BuildPlan.Stage.LANDSCAPE)) {
			for (BuildPlan.Step step : plan.steps(stage)) {
				level.setBlock(step.pos(), step.state(), 2);
				if (step.secondaryPos() != null && step.secondaryState() != null) {
					level.setBlock(step.secondaryPos(), step.secondaryState(), 2);
				}
			}
		}
		BuildSiteManager.get(level).recordFinished(VillageWaystones.PLAZA, placement(level, origin), UUID.randomUUID());
		return plan;
	}

	private static void hall(ServerLevel level, BlockPos at, String name, UUID owner) {
		level.setBlock(at, ModBlocks.VILLAGE_HALL.defaultBlockState(), 3);
		if (!(level.getBlockEntity(at) instanceof VillageHallBlockEntity hall)) {
			throw new GameTestAssertException("setup: no hall at " + at.toShortString());
		}
		hall.setCustomName(Component.literal(name));
		hall.setOwner(owner, "Jesse");
	}

	/**
	 * Ashford (in the test area: hall, plaza with its stone, a Storehouse and chest) and Farbrook {@link #AWAY} blocks east
	 * ({@code dz} south), both villages' stones raised by their halls. {@code logsAt}: whose chest gets 64 oak logs.
	 */
	private static Run villages(GameTestHelper helper, int dz, boolean logsAtHome) {
		RaidHelperCompatTests.clearAround(helper); // B541: halls and stones earlier batches left nearby
		io.github.jcondedata.aliveworkplace.hall.VillageHalls.forgetNearest();
		ServerLevel level = helper.getLevel();
		VillageWaystones.Data.get(level).halls().forEach(VillageWaystones.Data.get(level)::forget);
		BlockPos farHall = a(helper).offset(AWAY, 0, dz);
		BlockPos origin = farHall.offset(2, 0, 2);
		Far far = new Far(farHall, origin, origin.offset(STONE), farHall.offset(-8, 0, 0), farHall.offset(-8, 0, 2), BuildPlan.create(
			BlueprintLibrary.get(level, VillageWaystones.PLAZA).orElseThrow(() -> new GameTestAssertException("no plaza blueprint")), placement(level, origin)));
		Run run = new Run(helper, far);
		Caravans.Data.get(level).remove(a(helper));
		Caravans.Data.get(level).remove(farHall);
		run.guarded(() -> {
			helper.assertFalse(level.isLoaded(farHall), "setup: the far village's spot is loaded before the test builds it: " + farHall.toShortString());
			run.forceLoad(); // a player is at Farbrook while it's built (released by leave())
			LostCaravanStory.CHANCE = 0; // no caravan goes missing on the long road (random)
			UUID owner = UUID.randomUUID();
			hall(level, a(helper), "Ashford", owner);
			plaza(level, helper.absolutePos(PLAZA_A));
			helper.setBlock(STORE_A, ModBlocks.STOREHOUSE);
			helper.setBlock(CHEST_A, Blocks.CHEST);
			hall(level, farHall, "Farbrook", owner);
			plaza(level, origin);
			level.setBlock(far.store(), ModBlocks.STOREHOUSE.defaultBlockState(), 3);
			level.setBlock(far.chest(), Blocks.CHEST.defaultBlockState(), 3);
			Container logs = (Container) level.getBlockEntity(logsAtHome ? helper.absolutePos(CHEST_A) : far.chest());
			logs.setItem(0, new ItemStack(Items.OAK_LOG, 64));
			VillageWaystones.round(level, a(helper));
			VillageWaystones.round(level, farHall);
			helper.assertTrue(VillageWaystones.stoneOf(level, farHall).equals(Optional.of(far.stone())) && VillageWaystones.linked(level, a(helper), farHall),
				"setup: the two stones don't stand (Ashford " + VillageWaystones.stoneOf(level, a(helper)) + ", Farbrook " + VillageWaystones.stoneOf(level, farHall) + ")");
		});
		return run;
	}

	private static int logs(ServerLevel level, BlockPos chest) {
		if (!(level.getBlockEntity(chest) instanceof Container c)) {
			throw new GameTestAssertException("no chest at " + chest.toShortString());
		}
		int n = 0;
		for (int i = 0; i < c.getContainerSize(); i++) {
			if (c.getItem(i).is(Items.OAK_LOG)) {
				n += c.getItem(i).getCount();
			}
		}
		return n;
	}

	private static List<Caravans.Shipment> road(Caravans.Data data, BlockPos from, BlockPos to) {
		return data.onTheRoad().stream().filter(s -> s.from().equals(from) && s.to().equals(to)).toList();
	}

	private static int count(List<Caravans.Shipment> shipments) {
		return shipments.stream().flatMap(s -> s.goods().stream()).filter(s -> s.is(Items.OAK_LOG)).mapToInt(ItemStack::getCount).sum();
	}

	private static boolean said(ServerLevel level, BlockPos hall, String key) {
		return level.getBlockEntity(hall) instanceof VillageHallBlockEntity e
			&& e.chronicle().stream().anyMatch(x -> x.text().getContents() instanceof TranslatableContents t && t.getKey().equals(key));
	}

	/** {@code from} has a route to {@code to}, which waits for 32 oak logs; today's caravan from {@code from} is sent. */
	private static Caravans.Shipment send(GameTestHelper helper, BlockPos from, String fromName, BlockPos to, String toName) {
		Caravans.Data data = Caravans.Data.get(helper.getLevel());
		data.setWants(from, Component.literal(fromName), List.of());
		data.setWants(to, Component.literal(toName), List.of(new Caravans.Want(Items.OAK_LOG, 32)));
		helper.assertTrue(data.toggleRoute(from, to), "setup: no route");
		data.sent(from, -1);
		Caravans.round(helper.getLevel(), from, null);
		return road(data, from, to).stream().reduce((x, y) -> y).orElseThrow(() -> new GameTestAssertException("no caravan left " + fromName + ": " + data.onTheRoad()));
	}

	/**
	 * The caravan's arrival at a far village that's loaded again, checked: none on the road, 32 logs in the far chest and 32
	 * left at home (not lost, not doubled), and the far chronicle's line {@code came}.
	 */
	private static void cameToFar(Run run, String came, String what) {
		GameTestHelper helper = run.helper;
		ServerLevel level = helper.getLevel();
		BlockPos far = run.far.hall();
		Caravans.Data data = Caravans.Data.get(level);
		run.whenRunning(() -> run.waitFor(() -> level.isLoaded(far) && road(data, a(helper), far).isEmpty(), 600,
			() -> "Farbrook loaded again (" + ChunkWaits.state(level, far) + "), but the caravan never came in: still on the road " + road(data, a(helper), far)
				+ " (now " + level.getGameTime() + ")",
			() -> {
				int there = logs(level, run.far.chest());
				int home = logs(level, helper.absolutePos(CHEST_A));
				helper.assertTrue(there == 32 && home == 32, "logs lost or doubled on the way: " + there + " in Farbrook's chest (want 32), " + home + " left at Ashford (want 32)");
				helper.assertTrue(said(level, far, came), "Farbrook's chronicle has no '" + came + "' line");
				if (!came.equals("chronicle.aliveworkplace.waystone_walked")) {
					helper.assertFalse(came.endsWith("waystone_came") && said(level, far, "chronicle.aliveworkplace.waystone_walked"), "it walked with both stones standing");
				}
				run.pass(what + ": 32 logs in Farbrook's chest, 32 left at Ashford, none on the road, chronicle '{}'", came);
			}));
	}

	/**
	 * A caravan from Ashford to Farbrook, 1,600 blocks off and unloaded: it goes by waystone (Farbrook's stone known though
	 * its chunk isn't loaded), takes the short trip, waits on the road with all its goods while Farbrook stays unloaded,
	 * and steps out there when Farbrook loads again (its hall's first round): the logs in Farbrook's chest, none lost or
	 * doubled.
	 */
	//$ gametest_ticks_batch AREA '22400' '"qaWaystoneFarCaravan1107d"'
	@GameTest(template = AREA, timeoutTicks = 22400, batch = "qaWaystoneFarCaravan1107d")
	public void aCaravanToAFarUnloadedVillageGoesByWaystoneAndStepsOutWhenItLoads(GameTestHelper helper) {
		Run run = villages(helper, 0, true);
		ServerLevel level = helper.getLevel();
		BlockPos far = run.far.hall();
		run.settled(() -> run.leave(() -> {
			Caravans.Data data = Caravans.Data.get(level);
			helper.assertTrue(data.village(far) != null, "Farbrook left the caravans' list when its chunks unloaded");
			helper.assertTrue(VillageWaystones.linked(level, a(helper), far), "with Farbrook unloaded its standing stone doesn't count: stone "
				+ VillageWaystones.stoneOf(level, far) + ", standing " + VillageWaystones.standing(level, far));
			long now = level.getGameTime();
			Caravans.Shipment s = send(helper, a(helper), "Ashford", far, "Farbrook");
			long trip = Caravans.trip(level, a(helper), far, data);
			helper.assertTrue(s.byWaystone() && s.arrives() - now == Caravans.MIN_TRAVEL / 2,
				"to unloaded Farbrook the caravan should go by waystone in " + Caravans.MIN_TRAVEL / 2 + " ticks: by waystone " + s.byWaystone()
					+ ", " + (s.arrives() - now) + " ticks (the road: " + trip + ")");
			helper.assertTrue(count(List.of(s)) == 32, "the caravan should carry 32 logs: " + s.goods());
			LOGGER.info("QA 41.3 far: sending the caravan left Farbrook's chunk loaded: {}", level.isLoaded(far));
			data.hurry(far); // its time is up while Farbrook is still unloaded
			helper.runAfterDelay(40, () -> run.guarded(() -> {
				List<Caravans.Shipment> waiting = road(data, a(helper), far);
				helper.assertTrue(waiting.size() == 1 && waiting.get(0).byWaystone() && count(waiting) == 32,
					"due, with Farbrook unloaded, the caravan should wait on the road by waystone with its 32 logs: " + waiting);
				run.forceLoad(); // the player comes to Farbrook
				cameToFar(run, "chronicle.aliveworkplace.waystone_came", "a caravan to unloaded Farbrook went by waystone and stepped out when it loaded");
			}));
		}));
	}

	/** Control: the same with Farbrook kept loaded (forced) all along: by waystone, and out of Farbrook's stone. */
	//$ gametest_ticks_batch AREA '20600' '"qaWaystoneFarCaravanLoaded1107d"'
	@GameTest(template = AREA, timeoutTicks = 20600, batch = "qaWaystoneFarCaravanLoaded1107d")
	public void controlACaravanToAFarVillageKeptLoadedStepsOutThere(GameTestHelper helper) {
		Run run = villages(helper, 40, true);
		ServerLevel level = helper.getLevel();
		BlockPos far = run.far.hall();
		run.settled(() -> run.guarded(() -> {
			Caravans.Data data = Caravans.Data.get(level);
			long now = level.getGameTime();
			Caravans.Shipment s = send(helper, a(helper), "Ashford", far, "Farbrook");
			helper.assertTrue(s.byWaystone() && s.arrives() - now == Caravans.MIN_TRAVEL / 2,
				"to loaded Farbrook the caravan should go by waystone: " + s.byWaystone() + ", " + (s.arrives() - now) + " ticks");
			data.hurry(far);
			Caravans.round(level, far, null);
			cameToFar(run, "chronicle.aliveworkplace.waystone_came", "control, Farbrook kept loaded");
		}));
	}

	/**
	 * A caravan FROM Farbrook to Ashford, sent while Farbrook is loaded; the server saves and loads mid-trip; Farbrook
	 * unloads (the player is at Ashford). When it's due, it steps out of Ashford's stone (Farbrook's stone known unloaded):
	 * 32 logs in Ashford's chest, none lost on the reload, none still on the road; Farbrook's chest keeps the other 32.
	 */
	//$ gametest_ticks_batch AREA '22400' '"qaWaystoneFarCaravanIn1107d"'
	@GameTest(template = AREA, timeoutTicks = 22400, batch = "qaWaystoneFarCaravanIn1107d")
	public void aCaravanFromAFarVillageSavedMidTripStepsOutAtHomeWithThatVillageUnloaded(GameTestHelper helper) {
		Run run = villages(helper, 80, false);
		ServerLevel level = helper.getLevel();
		BlockPos far = run.far.hall();
		BlockPos home = a(helper);
		run.settled(() -> run.guarded(() -> {
			long now = level.getGameTime();
			Caravans.Shipment s = send(helper, far, "Farbrook", home, "Ashford");
			helper.assertTrue(s.byWaystone() && s.arrives() - now == Caravans.MIN_TRAVEL / 2,
				"from Farbrook the caravan should go by waystone: " + s.byWaystone() + ", " + (s.arrives() - now) + " ticks");
			// The server saves and loads mid-trip (Ashford's own hall round mustn't take it in before Farbrook unloads: its
			// arrival read back far off, then hurried below).
			CompoundTag saved = Caravans.Data.get(level).save(new CompoundTag(), level.registryAccess());
			ListTag list = saved.getList("road", Tag.TAG_COMPOUND);
			for (int i = 0; i < list.size(); i++) {
				if (list.getCompound(i).getLong("to") == home.asLong()) {
					list.getCompound(i).putLong("arrives", now + 1_000_000);
				}
			}
			level.getDataStorage().set(CARAVANS, Caravans.Data.load(saved, level.registryAccess()));
			run.leave(() -> {
				Caravans.Data data = Caravans.Data.get(level);
				List<Caravans.Shipment> back = road(data, far, home);
				helper.assertTrue(back.size() == 1 && back.get(0).byWaystone() && count(back) == 32,
					"after the reload, with Farbrook unloaded, the caravan should still be on the road by waystone with 32 logs: " + back);
				data.hurry(home);
				Caravans.round(level, home, null);
				helper.assertTrue(road(data, far, home).isEmpty(), "due, the caravan from unloaded Farbrook didn't come in: " + road(data, far, home));
				helper.assertTrue(said(level, home, "chronicle.aliveworkplace.waystone_came") && !said(level, home, "chronicle.aliveworkplace.waystone_walked"),
					"from unloaded Farbrook it should step out of Ashford's stone: came by stone " + said(level, home, "chronicle.aliveworkplace.waystone_came")
						+ ", walked " + said(level, home, "chronicle.aliveworkplace.waystone_walked") + ", by road " + said(level, home, "chronicle.aliveworkplace.caravan_came"));
				int here = logs(level, helper.absolutePos(CHEST_A));
				int there = logs(level, run.far.chest());
				helper.assertTrue(here == 32 && there == 32, "logs lost or doubled: " + here + " in Ashford's chest (want 32), " + there + " left at Farbrook (want 32)");
				run.pass("a caravan from unloaded Farbrook, saved and read back mid-trip, stepped out at Ashford with 32 logs; 32 left at Farbrook");
			});
		}));
	}

	/**
	 * Farbrook's stone broken (while Farbrook is loaded), then Farbrook unloads: Ashford's caravan walks the road (the
	 * chronicle says it left by road), and when it's due and Farbrook loads it comes in with all 32 logs.
	 */
	//$ gametest_ticks_batch AREA '22400' '"qaWaystoneFarBroken1107d"'
	@GameTest(template = AREA, timeoutTicks = 22400, batch = "qaWaystoneFarBroken1107d")
	public void aFarStoneBrokenBeforeItsVillageUnloadsTheCaravanWalks(GameTestHelper helper) {
		Run run = villages(helper, 120, true);
		ServerLevel level = helper.getLevel();
		BlockPos far = run.far.hall();
		run.guarded(() -> {
			level.setBlock(run.far.stone().above(), Blocks.AIR.defaultBlockState(), 3);
			level.setBlock(run.far.stone(), Blocks.AIR.defaultBlockState(), 3);
			helper.assertFalse(VillageWaystones.linked(level, a(helper), far), "setup: a broken stone still links the villages");
		});
		run.settled(() -> run.leave(() -> {
			Caravans.Data data = Caravans.Data.get(level);
			helper.assertFalse(VillageWaystones.standing(level, far), "Farbrook's stone, broken before it unloaded, counts as standing once it's unloaded");
			long now = level.getGameTime();
			Caravans.Shipment s = send(helper, a(helper), "Ashford", far, "Farbrook");
			long trip = Caravans.trip(level, a(helper), far, data);
			helper.assertTrue(!s.byWaystone() && s.arrives() - now == trip,
				"with Farbrook's stone broken the caravan should walk the road (" + trip + " ticks): by waystone " + s.byWaystone() + ", " + (s.arrives() - now) + " ticks");
			helper.assertTrue(said(level, a(helper), "chronicle.aliveworkplace.caravan_left") && !said(level, a(helper), "chronicle.aliveworkplace.waystone_left"),
				"Ashford's chronicle should say the caravan left by road");
			data.hurry(far);
			run.forceLoad();
			cameToFar(run, "chronicle.aliveworkplace.caravan_came", "Farbrook's stone broken, then unloaded: the caravan walked and came in");
		}));
	}

	/** Two settlers called to Ashford's hall for Newbrook, 300 blocks beyond Farbrook. */
	private static RealmData.Order settlers(GameTestHelper helper, Run run, List<Villager> out) {
		ServerLevel level = helper.getLevel();
		Villager dara = helper.spawn(EntityType.VILLAGER, new BlockPos(1, 2, 3));
		Villager tomas = helper.spawn(EntityType.VILLAGER, new BlockPos(2, 2, 3));
		out.add(dara);
		out.add(tomas);
		RealmData.Order order = new RealmData.Order(GlobalPos.of(level.dimension(), a(helper)), run.far.hall().offset(300, 0, 300), Optional.of(Component.literal("Newbrook")),
			RealmData.MUSTER, level.getGameTime(), 0, 0, level.getGameTime(), List.of(dara.getUUID(), tomas.getUUID()), Optional.of(dara.getUUID()), Map.of(), List.of());
		RealmData.get(level.getServer()).putOrder(order);
		return order;
	}

	private static Optional<RealmData.Order> order(GameTestHelper helper) {
		GlobalPos mother = GlobalPos.of(helper.getLevel().dimension(), a(helper));
		return RealmData.get(helper.getLevel().getServer()).orders().stream().filter(o -> o.mother().equals(mother)).findFirst();
	}

	private static double flat(BlockPos x, BlockPos y) {
		return Math.hypot(x.getX() - y.getX(), x.getZ() - y.getZ());
	}

	/** The settlers on the road through Farbrook's stone, checked; then {@code then}. */
	private static void onTheRoad(Run run, List<Villager> people, Runnable then) {
		GameTestHelper helper = run.helper;
		ServerLevel level = helper.getLevel();
		run.waitFor(() -> order(helper).map(o -> o.state().equals(RealmData.ON_ROAD)).orElse(false), 600,
			() -> "the settlers never set out: " + order(helper).map(RealmData.Order::state).orElse("no order"), () -> {
				RealmData.Order o = order(helper).orElseThrow();
				helper.assertTrue(o.via().equals(Optional.of(run.far.hall())), "the settlers should go through unloaded Farbrook's stone, via " + o.via());
				helper.assertTrue(o.settlers().size() == 2 && people.stream().noneMatch(Villager::isAlive), "the settlers didn't go: " + o.settlers().size() + " in the order");
				long byStone = Settlers.JOURNEY_TICKS + Math.round(flat(run.far.stone(), o.spot()));
				long left = o.arrives() - level.getGameTime();
				helper.assertTrue(left <= byStone && left >= byStone - 6 && byStone < Settlers.journey(o),
					"the journey by waystone: " + left + " ticks left, expected " + byStone + " (walking: " + Settlers.journey(o) + ")");
				then.run();
			});
	}

	/**
	 * Settlers for a colony beyond Farbrook (1,600 blocks off, unloaded) go through Farbrook's stone; when their time is up
	 * (the real colony tick) they step out there and arrive, not sent back to walk the whole road, and Ashford's chronicle
	 * says nothing of walking.
	 */
	//$ gametest_ticks_batch AREA '22400' '"qaWaystoneFarSettlers1107d"'
	@GameTest(template = AREA, timeoutTicks = 22400, batch = "qaWaystoneFarSettlers1107d")
	public void settlersThroughAFarUnloadedVillagesStoneStepOutThere(GameTestHelper helper) {
		Run run = villages(helper, 160, true);
		ServerLevel level = helper.getLevel();
		run.settled(() -> run.leave(() -> {
			List<Villager> people = new ArrayList<>();
			settlers(helper, run, people);
			onTheRoad(run, people, () -> {
				RealmData.Order o = order(helper).orElseThrow();
				RealmData.get(level.getServer()).putOrder(o.withState(RealmData.ON_ROAD, o.leaves(), level.getGameTime())); // their time is up
				// polled every tick: stepped out, the order is ARRIVING only until the spot's chunks wake and the camp is
				// pitched, which can be within 5 ticks (seen: 3 of 7 runs found the order already gone)
				run.waitFor(() -> order(helper).map(x -> !x.state().equals(RealmData.ON_ROAD) || x.arrives() > level.getGameTime()).orElse(true), 100, 1,
					() -> "their time up, the settlers' order didn't move on: " + order(helper).map(x -> x.state() + " arrives " + x.arrives()).orElse("gone"), () -> {
						RealmData.Order now = order(helper).orElseThrow(() -> new GameTestAssertException("the settlers' order is gone at the step-out"));
						RealmData.get(level.getServer()).removeOrder(now.mother()); // (no camp built 1,900 blocks off)
						helper.assertTrue(now.state().equals(RealmData.ARRIVING) && now.via().equals(Optional.of(run.far.hall())) && now.settlers().size() == 2,
							"through unloaded Farbrook's standing stone the settlers should step out and arrive: state " + now.state() + ", via " + now.via()
								+ ", " + now.settlers().size() + " settlers, arrives in " + (now.arrives() - level.getGameTime()));
						helper.assertFalse(said(level, a(helper), "chronicle.aliveworkplace.settlers_walked"), "Ashford's chronicle says they walked, with both stones standing");
						run.pass("settlers through unloaded Farbrook's stone stepped out there and are arriving (Farbrook loaded: {})", level.isLoaded(run.far.hall()));
					});
			});
		}));
	}

	/**
	 * Settlers on the road through Farbrook's stone; Farbrook's stone is broken meanwhile (a visit), and Farbrook unloads
	 * again. When their time is up they walk the whole road from Ashford (Ashford's chronicle says so), all of them.
	 */
	//$ gametest_ticks_batch AREA '23600' '"qaWaystoneFarSettlersBroken1107d"'
	@GameTest(template = AREA, timeoutTicks = 23600, batch = "qaWaystoneFarSettlersBroken1107d")
	public void settlersWhoseFarStoneBreaksMidTripWalkTheWholeRoad(GameTestHelper helper) {
		Run run = villages(helper, 200, true);
		ServerLevel level = helper.getLevel();
		run.settled(() -> run.leave(() -> {
			List<Villager> people = new ArrayList<>();
			settlers(helper, run, people);
			onTheRoad(run, people, () -> {
				// B1035: the visit is a player at Farbrook: its chunks loaded and ticked before the stone is broken. Broken in the
				// very tick a bare setBlock loads the chunk, the Waystones block entity isn't bound to its waystone yet (Balm runs
				// its onLoad at the next block-entity tick), so the mod never takes the stone off its saved list.
				run.forceLoad();
				run.whenRunning(() -> run.waitFor(() -> level.isLoaded(run.far.stone()) && VillageWaystones.standing(level, run.far.hall()), 200,
					() -> "setup: at the visit Farbrook's stone doesn't stand as a working waystone (" + ChunkWaits.state(level, run.far.stone()) + ")", () -> {
				level.setBlock(run.far.stone().above(), Blocks.AIR.defaultBlockState(), 3);
				level.setBlock(run.far.stone(), Blocks.AIR.defaultBlockState(), 3);
				helper.assertFalse(VillageWaystones.standing(level, run.far.hall()), "setup: Farbrook's stone, broken at the visit, still stands");
				run.leave(() -> {
					RealmData.Order o = order(helper).orElseThrow(() -> new GameTestAssertException("the settlers' order is gone mid-trip"));
					RealmData.get(level.getServer()).putOrder(o.withState(RealmData.ON_ROAD, o.leaves(), level.getGameTime()));
					long at = level.getGameTime();
					run.waitFor(() -> order(helper).map(x -> !x.state().equals(RealmData.ON_ROAD) || x.arrives() > level.getGameTime()).orElse(true), 100,
						() -> "their time up, the settlers' order didn't move on: " + order(helper).map(x -> x.state() + " arrives " + x.arrives()).orElse("gone"), () -> {
							RealmData.Order now = order(helper).orElseThrow(() -> new GameTestAssertException("the settlers' order is gone at the step-out"));
							RealmData.get(level.getServer()).removeOrder(now.mother());
							long walk = Settlers.journey(now);
							long left = now.arrives() - at;
							helper.assertTrue(now.state().equals(RealmData.ON_ROAD) && now.via().isEmpty() && left >= walk - 10 && left <= walk && now.settlers().size() == 2,
								"Farbrook's stone broken mid-trip, the settlers should walk the whole road (" + walk + " ticks): state " + now.state() + ", via " + now.via()
									+ ", arrive in " + left + ", " + now.settlers().size() + " settlers");
							helper.assertTrue(said(level, a(helper), "chronicle.aliveworkplace.settlers_walked"), "Ashford's chronicle doesn't say the settlers walk");
							run.pass("Farbrook's stone broken mid-trip, then unloaded: the settlers walk the whole road ({} ticks)", walk);
						});
				});
				}));
			});
		}));
	}
}
