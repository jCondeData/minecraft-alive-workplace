package io.github.jcondedata.aliveworkplace.compat;

import io.github.jcondedata.aliveworkplace.blueprint.Blueprint;
import io.github.jcondedata.aliveworkplace.blueprint.BlueprintData;
import io.github.jcondedata.aliveworkplace.blueprint.BlueprintLibrary;
import io.github.jcondedata.aliveworkplace.build.BuildPlan;
import io.github.jcondedata.aliveworkplace.build.BuildSiteManager;
import io.github.jcondedata.aliveworkplace.colony.Settlers;
import io.github.jcondedata.aliveworkplace.hall.Caravans;
import io.github.jcondedata.aliveworkplace.hall.VillageHallBlockEntity;
import io.github.jcondedata.aliveworkplace.hall.VillageHallScreen;
import io.github.jcondedata.aliveworkplace.hall.VillageHalls;
import io.github.jcondedata.aliveworkplace.hall.VillageWaystones;
import io.github.jcondedata.aliveworkplace.move.MovePlan;
import io.github.jcondedata.aliveworkplace.move.MoveSite;
import io.github.jcondedata.aliveworkplace.move.MoveSites;
import io.github.jcondedata.aliveworkplace.move.MoverWork;
import io.github.jcondedata.aliveworkplace.move.PendingMoves;
import io.github.jcondedata.aliveworkplace.realm.RealmData;
import io.github.jcondedata.aliveworkplace.registry.ModBlocks;
import io.github.jcondedata.aliveworkplace.registry.ModVillagers;
import io.github.jcondedata.aliveworkplace.story.LostCaravanStory;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.BooleanSupplier;
import java.util.function.Supplier;
import net.blay09.mods.waystones.api.Waystone;
import net.blay09.mods.waystones.api.WaystoneTeleportContext;
import net.blay09.mods.waystones.api.WaystonesAPI;
import net.blay09.mods.waystones.api.error.WaystoneTeleportError;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;

/**
 * QA (lane D, 2026-11-16), Waystones (41.1-41.3) crossed with the Mover (45.1/45.3), the cases the 1115d3 run left open.
 * From the specs: a moved building keeps what it had (45.1), so a moved plaza's stone is the village's waystone at its new
 * spot, and everything that travels by it (41.3) or lists it (41.2) uses the new spot, never the old one.
 * <ul>
 * <li>A caravan to a FAR village (1,600 blocks east, its chunks unloaded) after Ashford's plaza was moved: it goes by
 * waystone, waits on the road while the far village is unloaded and steps out there when it loads, its goods all there;
 * settlers through the far stone step in beside Ashford's moved stone, not at the old spot.</li>
 * <li>A player travels with the Waystones mod's own teleport (its API, from Brookmere's stone) to the moved stone, after
 * discovering it again (B1310 is known): they land by the new spot, the old spot is no waystone, and both halls' Maps
 * list it as discovered, Brookmere's with the distance and direction of the new spot.</li>
 * <li>The moved stone takes the village's rename (41.1: "renamed when the village is"), and broken at its new spot the
 * Maps say it's broken and caravans walk (41.2/41.3), though nothing stands at the old spot either.</li>
 * </ul>
 * Helpers follow QaWaystoneCross1115d3CompatTests and QaWaystoneFarAway1107dCompatTests (private there).
 */
public class QaWaystoneMoved1116dCompatTests implements FabricGameTest {
	private static final org.slf4j.Logger LOGGER = org.slf4j.LoggerFactory.getLogger("aliveworkplace-qa");
	private static final String AREA = WaystoneQaCompatTests.AREA;
	private static final String LINE = "screen.aliveworkplace.hall.waystone";
	/** The stone (lower half) in the plaza, from its corner (as tools/blueprints/waystones.py draws it). */
	private static final BlockPos STONE = new BlockPos(3, 2, 3);
	private static final BlockPos HALL_A = WaystoneQaCompatTests.HALL_A;
	private static final BlockPos HALL_B = WaystoneQaCompatTests.HALL_B;
	private static final BlockPos PLAZA_A = WaystoneQaCompatTests.PLAZA_A;
	private static final BlockPos PLAZA_B = WaystoneQaCompatTests.PLAZA_B;
	/** Where Ashford's plaza is moved to: the new corner, still nearest Ashford's hall. */
	private static final BlockPos TO = new BlockPos(3, 2, 14);
	private static final BlockPos MOVER = new BlockPos(12, 2, 3);
	private static final BlockPos STORE_A = new BlockPos(14, 2, 1);
	private static final BlockPos CHEST_A = new BlockPos(14, 2, 3);
	private static final int AWAY = 1600;
	private static final int UNLOAD_TICKS = 1000;

	// ---- the move (as QaWaystoneCross1115d3CompatTests does it) ----------------------------------------------------------

	private static Villager mover(GameTestHelper helper) {
		helper.setBlock(MOVER.north(), ModBlocks.MOVERS_CRATE);
		Villager mover = helper.spawn(EntityType.VILLAGER, MOVER);
		mover.setNoAi(true);
		mover.setVillagerData(mover.getVillagerData().setProfession(ModVillagers.MOVER));
		mover.getBrain().setMemory(MemoryModuleType.JOB_SITE, GlobalPos.of(helper.getLevel().dimension(), helper.absolutePos(MOVER.north())));
		return mover;
	}

	/**
	 * Ashford's plaza moved by a Mover to {@link #TO} turned {@code rotation}, the move run to its end and the hall's round
	 * after it; returns where the moved stone's lower half stands, checked to be the network's stone for Ashford.
	 */
	private static BlockPos move(GameTestHelper helper, Rotation rotation) {
		ServerLevel level = helper.getLevel();
		BlockPos hall = helper.absolutePos(HALL_A);
		Villager mover = mover(helper);
		PendingMoves.get(level).put(new PendingMoves.Move(hall, helper.absolutePos(PLAZA_A), VillageWaystones.PLAZA, helper.absolutePos(TO), rotation, null));
		MoveSite site = MoveSites.get(level).work(level, hall).orElseThrow(() -> new GameTestAssertException("the plaza's move didn't begin"));
		MovePlan plan = site.plan();
		for (int i = 0; i < 8000 && site.stage() != MoveSite.Stage.DONE; i++) {
			MoverWork.work(level, mover, site);
		}
		helper.assertTrue(site.stage() == MoveSite.Stage.DONE, "the move didn't end: " + site.stage());
		mover.discard();
		VillageWaystones.round(level, hall);
		BlockPos now = plan.at(STONE, helper.absolutePos(TO), rotation);
		BlockPos old = WaystoneQaCompatTests.stone(helper, PLAZA_A);
		helper.assertTrue(VillageWaystones.isStone(level.getBlockState(now)), "setup: no stone at the new spot " + now.toShortString() + ": " + level.getBlockState(now));
		helper.assertTrue(level.getBlockState(old).isAir(), "setup: the old stone's spot " + old.toShortString() + " holds " + level.getBlockState(old));
		helper.assertTrue(VillageWaystones.stoneOf(level, hall).equals(Optional.of(now)) && VillageWaystones.standing(level, hall),
			"after the move the network keeps Ashford's stone at " + VillageWaystones.stoneOf(level, hall) + " (standing " + VillageWaystones.standing(level, hall)
				+ "), not " + now.toShortString());
		return now;
	}

	/** Takes this class's moves, plazas (wherever they stand now) and stones off every list, and the stones down. */
	private static void cleanUp(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		PendingMoves.get(level).remove(helper.absolutePos(PLAZA_A));
		MoveSites.get(level).cancel(level, helper.absolutePos(PLAZA_A));
		BuildSiteManager sites = BuildSiteManager.get(level);
		net.minecraft.world.phys.AABB box = helper.getBounds();
		sites.finishedIn(level).stream().filter(f -> VillageWaystones.isPlaza(f.structure()) && box.contains(net.minecraft.world.phys.Vec3.atCenterOf(f.placement().origin())))
			.map(BuildSiteManager.Finished::placement).toList().forEach(sites::forgetFinished);
		WaystoneQaCompatTests.cleanUp(helper);
		for (BlockPos p : BlockPos.betweenClosed(helper.absolutePos(new BlockPos(0, 1, 0)), helper.absolutePos(new BlockPos(29, 8, 29)))) {
			if (VillageWaystones.isStone(level.getBlockState(p))) {
				level.setBlock(p.above(), Blocks.AIR.defaultBlockState(), 3);
				level.setBlock(p.immutable(), Blocks.AIR.defaultBlockState(), 3);
			}
		}
	}

	/** Ashford and Brookmere (one owner), each with a standing plaza stone. */
	private static void twoVillages(GameTestHelper helper, String a, String b) {
		RaidHelperCompatTests.clearAround(helper); // B541: halls and stones earlier batches left nearby
		VillageHalls.forgetNearest();
		cleanUp(helper);
		ServerLevel level = helper.getLevel();
		VillageWaystones.Data.get(level).halls().forEach(VillageWaystones.Data.get(level)::forget);
		UUID owner = new UUID(0x1116dL, 0x41L);
		WaystoneQaCompatTests.hall(helper, HALL_A, a, owner);
		WaystoneQaCompatTests.hall(helper, HALL_B, b, owner);
		WaystoneQaCompatTests.plaza(helper, PLAZA_A);
		WaystoneQaCompatTests.plaza(helper, PLAZA_B);
		VillageWaystones.round(level, helper.absolutePos(HALL_A));
		VillageWaystones.round(level, helper.absolutePos(HALL_B));
		helper.assertTrue(VillageWaystones.linked(level, helper.absolutePos(HALL_A), helper.absolutePos(HALL_B)), "setup: the two villages aren't linked");
	}

	private static Waystone waystoneAt(ServerLevel level, BlockPos stone, String what) {
		return WaystonesAPI.getWaystoneAt(level, stone).filter(Waystone::isValid).orElseThrow(() -> new GameTestAssertException("no working waystone " + what + " at " + stone.toShortString()));
	}

	private static double flat(BlockPos x, BlockPos y) {
		return Math.hypot(x.getX() - y.getX(), x.getZ() - y.getZ());
	}

	// ---- case 2: a player's own travel ------------------------------------------------------------------------------------

	/**
	 * Ashford's plaza moved a quarter turn. A player who discovers the moved stone (again, B1310) and stands at Brookmere's
	 * stone travels to Ashford with the Waystones mod's own teleport: they land by the new stone (within 3 blocks), not near
	 * the old spot; the old spot is no waystone in the mod's eyes; Ashford's Map reads "this village: discovered" and
	 * Brookmere's Map gives Ashford's distance and direction to the NEW stone.
	 */
	//$ gametest_ticks_batch AREA '200' '"qaWaystoneMoved1116dTeleport"'
	@GameTest(template = AREA, timeoutTicks = 200, batch = "qaWaystoneMoved1116dTeleport")
	public void aPlayerTravelsByTheWaystonesModToTheMovedStoneAndLandsAtItsNewSpot(GameTestHelper helper) {
		twoVillages(helper, "Wrenfield", "Oakhollow");
		ServerLevel level = helper.getLevel();
		BlockPos a = helper.absolutePos(HALL_A);
		BlockPos b = helper.absolutePos(HALL_B);
		BlockPos old = WaystoneQaCompatTests.stone(helper, PLAZA_A);
		try {
			BlockPos now = move(helper, Rotation.CLOCKWISE_90);
			helper.assertTrue(WaystonesAPI.getWaystoneAt(level, old).filter(Waystone::isValid).isEmpty() && !VillageWaystones.isStone(level.getBlockState(old))
				&& !VillageWaystones.isStone(level.getBlockState(old.above())), "the old spot " + old.toShortString() + " is still a waystone: " + level.getBlockState(old)
				+ ", the mod's: " + WaystonesAPI.getWaystoneAt(level, old).map(w -> w.getName().getString() + (w.isValid() ? "" : " (invalid)")));

			ServerPlayer player = helper.makeMockServerPlayerInLevel();
			Waystone target = waystoneAt(level, now, "at the moved spot");
			Waystone home = waystoneAt(level, WaystoneQaCompatTests.stone(helper, PLAZA_B), "of Oakhollow");
			helper.assertTrue(target.getName().getString().equals("Wrenfield"), "the moved stone is named " + target.getName().getString());
			WaystonesAPI.activateWaystone(player, home);
			WaystonesAPI.activateWaystone(player, target); // walked there and found it again (B1310)
			BlockPos start = VillageWaystones.beside(level, home.getPos(), b);
			player.teleportTo(start.getX() + 0.5, start.getY(), start.getZ() + 0.5);

			var context = WaystonesAPI.createDefaultTeleportContext(player, target, c -> c.setFromWaystone(home));
			WaystoneTeleportContext ctx = context.left().orElseThrow(() -> new GameTestAssertException("the Waystones mod refused the trip: "
				+ context.right().map(WaystoneTeleportError::getClass).map(Class::getSimpleName).orElse("?")));
			var result = WaystonesAPI.tryTeleport(ctx);
			helper.assertTrue(result.left().isPresent(), "the trip failed: " + result.right().map(e -> e.getClass().getSimpleName()).orElse("?"));
			BlockPos landed = player.blockPosition();
			helper.assertTrue(flat(landed, now) <= 3 && Math.abs(landed.getY() - now.getY()) <= 2, "the player landed at " + landed.toShortString()
				+ ", not by the moved stone " + now.toShortString() + " (the old spot: " + old.toShortString() + ")");
			helper.assertTrue(flat(landed, old) > 5, "the player landed by the old spot " + old.toShortString() + ": " + landed.toShortString());

			List<String> here = WaystoneQaCompatTests.texts(WaystoneQaCompatTests.mapLore(player, a), LINE);
			helper.assertTrue(here.contains("Wrenfield, this village: discovered"), "Wrenfield's Map after the trip: " + here);
			String where = VillageHallScreen.where(b, now).getString();
			String whereOld = VillageHallScreen.where(b, old).getString();
			helper.assertFalse(where.equals(whereOld), "setup: the new and old spots read the same from Oakhollow (" + where + ")");
			List<String> there = WaystoneQaCompatTests.texts(WaystoneQaCompatTests.mapLore(player, b), LINE);
			helper.assertTrue(there.contains("Wrenfield, " + where + ": discovered"), "Oakhollow's Map should list Wrenfield at the moved stone ('" + where
				+ "', the old spot read '" + whereOld + "'): " + there);
			LOGGER.info("QA 41 x 45 moved: a player travelled by the Waystones mod to the moved stone {} and landed at {}; Oakhollow's Map: {}", now.toShortString(), landed.toShortString(), there);
		} finally {
			cleanUp(helper);
		}
		helper.succeed();
	}

	// ---- case 3: rename and break at the new spot -------------------------------------------------------------------------

	/**
	 * After the move, the village is renamed: the stone at the new spot takes the new name, and the mod's list has no stone
	 * of the old name. Then the moved stone is broken: both Maps say the village's waystone is broken (not "discovered" from
	 * some other spot), the villages are no longer linked, and a caravan between them walks.
	 */
	//$ gametest_ticks_batch AREA '200' '"qaWaystoneMoved1116dRenameBreak"'
	@GameTest(template = AREA, timeoutTicks = 200, batch = "qaWaystoneMoved1116dRenameBreak")
	public void theMovedStoneTakesARenameAndBrokenItCountsAsBroken(GameTestHelper helper) {
		twoVillages(helper, "Thornbury", "Kestrelby");
		ServerLevel level = helper.getLevel();
		BlockPos a = helper.absolutePos(HALL_A);
		BlockPos b = helper.absolutePos(HALL_B);
		try {
			BlockPos now = move(helper, Rotation.COUNTERCLOCKWISE_90);
			VillageHallBlockEntity hall = helper.getBlockEntity(HALL_A);
			hall.setCustomName(Component.literal("Saltmarsh Green"));
			Optional<String> name = VillageWaystones.EXTENSION.call(s -> s.nameAt(level, now), Optional.<String>empty());
			helper.assertTrue(name.equals(Optional.of("Saltmarsh Green")), "after the rename the moved stone is " + name + ", not Saltmarsh Green");
			List<String> stale = net.blay09.mods.waystones.core.WaystoneManagerImpl.get(level.getServer()).getWaystones()
				.filter(w -> w.getName().getString().equals("Thornbury")).map(w -> w.getPos().toShortString()).toList();
			helper.assertTrue(stale.isEmpty(), "the mod still lists a stone named Thornbury at " + stale);
			ServerPlayer player = helper.makeMockServerPlayerInLevel();
			List<String> lines = WaystoneQaCompatTests.texts(WaystoneQaCompatTests.mapLore(player, a), LINE);
			helper.assertTrue(lines.contains("Saltmarsh Green, this village: not discovered yet"), "the Map after the rename: " + lines);

			level.setBlock(now.above(), Blocks.AIR.defaultBlockState(), 3);
			level.setBlock(now, Blocks.AIR.defaultBlockState(), 3);
			VillageWaystones.round(level, a);
			lines = WaystoneQaCompatTests.texts(WaystoneQaCompatTests.mapLore(player, a), LINE);
			helper.assertTrue(lines.contains("Saltmarsh Green, this village: its waystone is broken"), "the moved stone broken, the Map reads " + lines);
			String where = VillageHallScreen.where(b, now).getString();
			List<String> there = WaystoneQaCompatTests.texts(WaystoneQaCompatTests.mapLore(player, b), LINE);
			helper.assertTrue(there.contains("Saltmarsh Green, " + where + ": its waystone is broken"), "Kestrelby's Map with the moved stone broken: " + there);
			helper.assertFalse(VillageWaystones.linked(level, a, b), "with the moved stone broken the villages are still linked by waystone");

			helper.setBlock(STORE_A, ModBlocks.STOREHOUSE);
			helper.setBlock(CHEST_A, Blocks.CHEST);
			helper.setBlock(new BlockPos(24, 2, 14), ModBlocks.STOREHOUSE);
			helper.setBlock(new BlockPos(24, 2, 16), Blocks.CHEST);
			Container chest = helper.getBlockEntity(CHEST_A);
			chest.setItem(0, new ItemStack(Items.OAK_LOG, 64));
			Caravans.Data data = Caravans.Data.get(level);
			data.setWants(a, Component.literal("Saltmarsh Green"), List.of());
			data.setWants(b, Component.literal("Kestrelby"), List.of(new Caravans.Want(Items.OAK_LOG, 16)));
			if (!data.routesFrom(a).contains(b)) {
				helper.assertTrue(data.toggleRoute(a, b), "setup: no route");
			}
			data.sent(a, -1);
			Caravans.round(level, a, null);
			Caravans.Shipment s = data.onTheRoad().stream().filter(x -> x.from().equals(a) && x.to().equals(b)).reduce((x, y) -> y)
				.orElseThrow(() -> new GameTestAssertException("no caravan left Saltmarsh Green: " + data.onTheRoad()));
			helper.assertFalse(s.byWaystone(), "with the moved stone broken the caravan still goes by waystone: " + s);
		} finally {
			cleanUp(helper);
		}
		helper.succeed();
	}

	// ---- case 1: a caravan and settlers to a far village --------------------------------------------------------------------

	/** The far village: its hall, plaza origin, stone, Storehouse and chest (absolute), and its plan. */
	private record Far(BlockPos hall, BlockPos origin, BlockPos stone, BlockPos store, BlockPos chest, BuildPlan plan) {
		List<BlockPos> spots() {
			return List.of(hall, stone, store, chest);
		}
	}

	private static BlueprintData.Placement placement(ServerLevel level, BlockPos origin) {
		return new BlueprintData.Placement(level.dimension().location(), origin, Rotation.NONE, Mirror.NONE);
	}

	/** One far test's state: what to undo at the end, once (as QaWaystoneFarAway1107dCompatTests.Run). */
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
			data.remove(helper.absolutePos(HALL_A));
			data.remove(far.hall());
			VillageWaystones.Data.get(level).forget(far.hall());
			RealmData.get(level.getServer()).forgetKnown(GlobalPos.of(level.dimension(), far.hall()));
			BuildSiteManager.get(level).forgetFinished(placement(level, far.origin()));
			if (level.getBlockEntity(far.chest()) instanceof Container c) {
				c.clearContent();
			}
			if (level.getBlockEntity(helper.absolutePos(CHEST_A)) instanceof Container c) {
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
			cleanUp(helper);
		}

		void guarded(Runnable body) {
			try {
				body.run();
			} catch (RuntimeException | Error e) {
				clean();
				throw e;
			}
		}

		void waitFor(BooleanSupplier ready, int ticks, Supplier<String> fail, Runnable then) {
			guarded(() -> {
				if (ready.getAsBoolean()) {
					then.run();
					return;
				}
				if (ticks <= 0) {
					throw new GameTestAssertException(fail.get());
				}
				helper.runAfterDelay(5, () -> waitFor(ready, ticks - 5, fail, then));
			});
		}

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

		void whenRunning(Runnable then) {
			ServerLevel level = helper.getLevel();
			waitOnChunks(() -> ChunkWaits.running(level, far.hall()), ChunkWaits.start(level), ChunkWaits.MIN_TICKS,
				() -> "setup: the far village's chunk never started running (" + ChunkWaits.state(level, far.hall()) + ")", then);
		}

		void forceLoad() {
			ServerLevel level = helper.getLevel();
			for (int cx = (far.hall().getX() - 12) >> 4; cx <= (far.hall().getX() + 12) >> 4; cx++) {
				for (int cz = (far.hall().getZ() - 12) >> 4; cz <= (far.hall().getZ() + 12) >> 4; cz++) {
					level.setChunkForced(cx, cz, true);
					forced.add(new long[] {cx, cz});
				}
			}
		}

		void settled(Runnable then) {
			ServerLevel level = helper.getLevel();
			whenRunning(() -> waitFor(() -> level.isLoaded(far.hall()) && Caravans.Data.get(level).village(far.hall()) != null, 200,
				() -> "setup: the far hall never had its first round while its chunk was running (" + ChunkWaits.state(level, far.hall()) + ")", then));
		}

		void leave(Runnable then) {
			ServerLevel level = helper.getLevel();
			for (long[] c : forced) {
				level.setChunkForced((int) c[0], (int) c[1], false);
			}
			forced.clear();
			waitOnChunks(() -> far.spots().stream().noneMatch(level::isLoaded), ChunkWaits.start(level), UNLOAD_TICKS,
				() -> "setup: the far village's chunks never unloaded", then);
		}
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

	private static boolean said(ServerLevel level, BlockPos hall, String key) {
		return level.getBlockEntity(hall) instanceof VillageHallBlockEntity e
			&& e.chronicle().stream().anyMatch(x -> x.text().getContents() instanceof TranslatableContents t && t.getKey().equals(key));
	}

	/**
	 * Ashford's plaza moved half a turn by a Mover; Farbrook stands 1,600 blocks east and is left until its chunks unload.
	 * A caravan from Ashford to Farbrook goes by waystone (the short trip), waits on the road with its 32 logs while Farbrook
	 * is unloaded, and steps out there when Farbrook loads: 32 logs in Farbrook's chest, 32 left at home. Settlers through
	 * Farbrook's stone step in beside Ashford's MOVED stone (never at the old spot), and their trip runs from Farbrook's stone.
	 */
	//$ gametest_ticks_batch AREA '22400' '"qaWaystoneMoved1116dFar"'
	@GameTest(template = AREA, timeoutTicks = 22400, batch = "qaWaystoneMoved1116dFar")
	public void aCaravanToAFarUnloadedVillageGoesThroughTheMovedStoneAndArrives(GameTestHelper helper) {
		RaidHelperCompatTests.clearAround(helper); // B541
		VillageHalls.forgetNearest();
		cleanUp(helper);
		ServerLevel level = helper.getLevel();
		VillageWaystones.Data.get(level).halls().forEach(VillageWaystones.Data.get(level)::forget);
		BlockPos home = helper.absolutePos(HALL_A);
		BlockPos farHall = home.offset(AWAY, 0, 240);
		BlockPos origin = farHall.offset(2, 0, 2);
		Blueprint blueprint = BlueprintLibrary.get(level, VillageWaystones.PLAZA).orElseThrow(() -> new GameTestAssertException("no plaza blueprint"));
		Far far = new Far(farHall, origin, origin.offset(STONE), farHall.offset(-8, 0, 0), farHall.offset(-8, 0, 2), BuildPlan.create(blueprint, placement(level, origin)));
		Run run = new Run(helper, far);
		BlockPos old = WaystoneQaCompatTests.stone(helper, PLAZA_A);
		BlockPos[] moved = new BlockPos[1];
		Caravans.Data.get(level).remove(home);
		Caravans.Data.get(level).remove(farHall);
		run.guarded(() -> {
			helper.assertFalse(level.isLoaded(farHall), "setup: the far village's spot is loaded before the test builds it: " + farHall.toShortString());
			run.forceLoad();
			LostCaravanStory.CHANCE = 0; // no caravan goes missing on the long road (random)
			UUID owner = new UUID(0x1116dL, 0x42L);
			WaystoneQaCompatTests.hall(helper, HALL_A, "Ashford", owner);
			WaystoneQaCompatTests.plaza(helper, PLAZA_A);
			level.setBlock(farHall, ModBlocks.VILLAGE_HALL.defaultBlockState(), 3);
			VillageHallBlockEntity fh = (VillageHallBlockEntity) level.getBlockEntity(farHall);
			fh.setCustomName(Component.literal("Farbrook"));
			fh.setOwner(owner, "Jesse");
			BuildPlan plan = far.plan();
			for (BuildPlan.Stage stage : List.of(BuildPlan.Stage.FOUNDATION, BuildPlan.Stage.STRUCTURE, BuildPlan.Stage.DECORATION, BuildPlan.Stage.LANDSCAPE)) {
				for (BuildPlan.Step step : plan.steps(stage)) {
					level.setBlock(step.pos(), step.state(), 2);
					if (step.secondaryPos() != null && step.secondaryState() != null) {
						level.setBlock(step.secondaryPos(), step.secondaryState(), 2);
					}
				}
			}
			BuildSiteManager.get(level).recordFinished(VillageWaystones.PLAZA, placement(level, origin), new UUID(0x1116dL, 0x43L));
			VillageWaystones.round(level, home);
			VillageWaystones.round(level, farHall);
			moved[0] = move(helper, Rotation.CLOCKWISE_180);
			helper.setBlock(STORE_A, ModBlocks.STOREHOUSE);
			helper.setBlock(CHEST_A, Blocks.CHEST);
			level.setBlock(far.store(), ModBlocks.STOREHOUSE.defaultBlockState(), 3);
			level.setBlock(far.chest(), Blocks.CHEST.defaultBlockState(), 3);
			((Container) helper.getBlockEntity(CHEST_A)).setItem(0, new ItemStack(Items.OAK_LOG, 64));
			helper.assertTrue(VillageWaystones.stoneOf(level, farHall).equals(Optional.of(far.stone())) && VillageWaystones.linked(level, home, farHall),
				"setup: Ashford's moved stone and Farbrook's don't link (Ashford " + VillageWaystones.stoneOf(level, home) + ", Farbrook " + VillageWaystones.stoneOf(level, farHall) + ")");
		});
		BlockPos now = moved[0];
		run.settled(() -> run.leave(() -> {
			Caravans.Data data = Caravans.Data.get(level);
			helper.assertTrue(VillageWaystones.linked(level, home, farHall), "with Farbrook unloaded, Ashford's moved stone and Farbrook's aren't linked: Ashford "
				+ VillageWaystones.stoneOf(level, home) + " standing " + VillageWaystones.standing(level, home) + ", Farbrook standing " + VillageWaystones.standing(level, farHall));

			// settlers through Farbrook's stone: they step in beside Ashford's moved stone, and the trip runs from Farbrook's
			RealmData.Order order = new RealmData.Order(GlobalPos.of(level.dimension(), home), farHall.offset(300, 0, 300), Optional.of(Component.literal("Newbrook")),
				RealmData.MUSTER, level.getGameTime(), 0, 0, level.getGameTime(), List.of(), Optional.empty(), Map.of(), List.of(), false, Optional.of(farHall));
			helper.assertTrue(Settlers.byWaystone(level, order), "settlers via Farbrook don't go by waystone after Ashford's plaza moved");
			BlockPos edge = Settlers.edge(level, order);
			helper.assertTrue(flat(edge, now) <= 3 && flat(edge, old) > 5, "the settlers step in at " + edge.toShortString() + ", not beside the moved stone "
				+ now.toShortString() + " (old spot " + old.toShortString() + ")");
			long byStone = Settlers.JOURNEY_TICKS + Math.round(flat(far.stone(), order.spot()));
			helper.assertTrue(Settlers.journey(order, level) == byStone, "the settlers' trip by waystone: " + Settlers.journey(order, level) + " ticks, want " + byStone);

			long at = level.getGameTime();
			data.setWants(home, Component.literal("Ashford"), List.of());
			data.setWants(farHall, Component.literal("Farbrook"), List.of(new Caravans.Want(Items.OAK_LOG, 32)));
			helper.assertTrue(data.toggleRoute(home, farHall), "setup: no route");
			data.sent(home, -1);
			Caravans.round(level, home, null);
			Caravans.Shipment s = road(data, home, farHall).stream().reduce((x, y) -> y)
				.orElseThrow(() -> new GameTestAssertException("no caravan left Ashford: " + data.onTheRoad()));
			helper.assertTrue(s.byWaystone() && s.arrives() - at == Caravans.MIN_TRAVEL / 2, "through the moved stone to unloaded Farbrook the caravan should go by waystone in "
				+ Caravans.MIN_TRAVEL / 2 + " ticks: by waystone " + s.byWaystone() + ", " + (s.arrives() - at) + " ticks");
			helper.assertTrue(said(level, home, "chronicle.aliveworkplace.waystone_left"), "Ashford's chronicle doesn't say the caravan left by waystone");
			data.hurry(farHall);
			helper.runAfterDelay(40, () -> run.guarded(() -> {
				List<Caravans.Shipment> waiting = road(data, home, farHall);
				helper.assertTrue(waiting.size() == 1 && waiting.get(0).byWaystone(), "due with Farbrook unloaded, the caravan should wait on the road by waystone: " + waiting);
				run.forceLoad(); // the player comes to Farbrook
				run.whenRunning(() -> run.waitFor(() -> level.isLoaded(farHall) && road(data, home, farHall).isEmpty(), 600,
					() -> "Farbrook loaded again, but the caravan never came in: " + road(data, home, farHall),
					() -> {
						int there = logs(level, far.chest());
						int left = logs(level, helper.absolutePos(CHEST_A));
						helper.assertTrue(there == 32 && left == 32, "logs lost or doubled: " + there + " in Farbrook's chest (want 32), " + left + " left at Ashford (want 32)");
						helper.assertTrue(said(level, farHall, "chronicle.aliveworkplace.waystone_came") && !said(level, farHall, "chronicle.aliveworkplace.waystone_walked"),
							"Farbrook's chronicle should say the caravan came out of its stone");
						run.clean();
						LOGGER.info("QA 41 x 45 moved: a caravan through Ashford's moved stone {} reached unloaded Farbrook with 32 logs; settlers step in at {}", now.toShortString(), edge.toShortString());
						helper.succeed();
					}));
			}));
		}));
	}
}
