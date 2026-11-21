package io.github.jcondedata.aliveworkplace.compat;

import io.github.jcondedata.aliveworkplace.build.BuildSite;
import io.github.jcondedata.aliveworkplace.build.BuildSiteManager;
import io.github.jcondedata.aliveworkplace.build.Builders;
import io.github.jcondedata.aliveworkplace.build.Upkeep;
import io.github.jcondedata.aliveworkplace.hall.VillageHalls;
import io.github.jcondedata.aliveworkplace.hall.VillageWaystones;
import io.github.jcondedata.aliveworkplace.move.MovePlan;
import io.github.jcondedata.aliveworkplace.move.MoveSite;
import io.github.jcondedata.aliveworkplace.move.MoveSites;
import io.github.jcondedata.aliveworkplace.move.MoverWork;
import io.github.jcondedata.aliveworkplace.move.PendingMoves;
import io.github.jcondedata.aliveworkplace.realm.RealmData;
import io.github.jcondedata.aliveworkplace.registry.ModAttachments;
import io.github.jcondedata.aliveworkplace.registry.ModBlocks;
import io.github.jcondedata.aliveworkplace.registry.ModGameRules;
import io.github.jcondedata.aliveworkplace.registry.ModVillagers;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.function.IntFunction;
import net.blay09.mods.waystones.api.Waystone;
import net.blay09.mods.waystones.api.WaystonesAPI;
import net.blay09.mods.waystones.core.WaystoneManagerImpl;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * QA (lane D, 2026-11-21): B1310 and B1375 (Waystones x the Mover) verified at chunk borders. B1375 was first "not
 * reproduced" and suspected to need the plaza across a chunk border (its old stone at chunk-local z 0) or a repeat, so
 * these tests FORCE the case the random test position only sometimes gives: the plaza is laid so its stone stands at
 * chunk-local x 0, x 15, z 0 or z 15 (worked out from the test's own position), and the move takes it across that border
 * (and across two borders at once, and across and back). From the specs (41.1 a village's plaza waystone; 45.1 "a moved
 * building keeps what it had"; B1310, B1375): after the move the stone at the new spot is the same waystone of the
 * Waystones mod's (same id), still discovered by the player who had found it (the hall's Map says "discovered") and not by
 * one who hadn't; the mod lists exactly one stone of the village, at the new spot, with the village's name, and nothing at
 * the old spot; that still holds after the mod's list is saved and loaded again; and a moved stone broken at its new spot
 * and put back by the builder's upkeep leaves still exactly one entry of the village, at the new spot, its name kept.
 */
public class QaWaystoneChunkMove1121dCompatTests implements FabricGameTest {
	private static final org.slf4j.Logger LOGGER = org.slf4j.LoggerFactory.getLogger("aliveworkplace-qa");
	private static final String AREA = WaystoneQaCompatTests.AREA;
	private static final String LINE = "screen.aliveworkplace.hall.waystone";
	/** The stone (lower half) in the plaza, from its corner (7 x 5 x 7, tools/blueprints/waystones.py). */
	private static final BlockPos STONE = new BlockPos(3, 2, 3);
	/** The hall, the Mover and the builder's things stand in column x = 0; every plaza corner here has x >= 1. */
	private static final BlockPos HALL = new BlockPos(0, 2, 0);
	private static final BlockPos MOVER = new BlockPos(0, 2, 28);
	private static final BlockPos BENCH = new BlockPos(0, 2, 12);
	private static final BlockPos CHEST = new BlockPos(0, 2, 14);
	private static final BlockPos BUILDER = new BlockPos(0, 2, 10);

	// ---- layout ---------------------------------------------------------------------------------------------------------

	/** The smallest corner coordinate c in [min, max] whose stone (c + 3 along the axis) stands at chunk-local {@code local}. */
	private static int corner(IntFunction<Integer> absOfStone, int min, int max, int local) {
		for (int c = min; c <= max; c++) {
			if (Math.floorMod(absOfStone.apply(c), 16) == local) {
				return c;
			}
		}
		throw new GameTestAssertException("setup: no corner in " + min + ".." + max + " puts the stone at chunk-local " + local);
	}

	private static int cornerX(GameTestHelper helper, int min, int max, int local) {
		return corner(c -> helper.absolutePos(new BlockPos(c, 2, 0).offset(STONE)).getX(), min, max, local);
	}

	private static int cornerZ(GameTestHelper helper, int min, int max, int local) {
		return corner(c -> helper.absolutePos(new BlockPos(0, 2, c).offset(STONE)).getZ(), min, max, local);
	}

	// ---- the Waystones mod's list ---------------------------------------------------------------------------------------

	/** The waystones the mod lists named {@code name} round this test (the local test world keeps the list between runs). */
	private static List<Waystone> named(GameTestHelper helper, String name) {
		AABB near = helper.getBounds().inflate(VillageHalls.RADIUS + 32);
		return WaystoneManagerImpl.get(helper.getLevel().getServer()).getWaystones()
			.filter(w -> w.getName().getString().equals(name) && w.getDimension().equals(helper.getLevel().dimension()) && near.contains(Vec3.atCenterOf(w.getPos())))
			.toList();
	}

	/** The waystones the mod lists with either half at {@code pos}. */
	private static List<Waystone> at(ServerLevel level, BlockPos pos) {
		return WaystoneManagerImpl.get(level.getServer()).getWaystones()
			.filter(w -> w.getDimension().equals(level.dimension()) && (w.getPos().equals(pos) || w.getPos().equals(pos.above())))
			.toList();
	}

	private static String describe(List<Waystone> list) {
		return list.stream().map(w -> w.getName().getString() + "@" + w.getPos().toShortString() + (w.isValid() ? "" : " (invalid)")
			+ (w.isTransient() ? " (transient)" : "")).toList().toString();
	}

	private static Waystone working(ServerLevel level, BlockPos pos, String what) {
		return WaystonesAPI.getWaystoneAt(level, pos).filter(Waystone::isValid)
			.orElseThrow(() -> new GameTestAssertException("no working waystone " + what + " " + pos.toShortString() + ": " + level.getBlockState(pos)));
	}

	/** Saves the Waystones mod's list and loads it again, as a server restart does. */
	private static void reloadWaystones(ServerLevel level) {
		CompoundTag stones = WaystoneManagerImpl.get(level.getServer()).save(new CompoundTag(), level.registryAccess());
		level.getServer().overworld().getDataStorage().set("waystones", WaystoneManagerImpl.read(stones, level.registryAccess()));
	}

	// ---- the village and the move ---------------------------------------------------------------------------------------

	/** A clean area: halls, plazas and stones earlier batches left nearby taken away. */
	private static void clear(GameTestHelper helper) {
		RaidHelperCompatTests.clearAround(helper); // B541/B1375: halls and plazas earlier batches left nearby
		VillageHalls.forgetNearest();
		ServerLevel level = helper.getLevel();
		VillageWaystones.Data.get(level).halls().forEach(VillageWaystones.Data.get(level)::forget);
	}

	/** The village's hall named {@code name} and its plaza at {@code from} with its stone raised; returns the stone's waystone. */
	private static Waystone village(GameTestHelper helper, String name, BlockPos from) {
		ServerLevel level = helper.getLevel();
		WaystoneQaCompatTests.hall(helper, HALL, name, new UUID(0x1121dL, name.hashCode()));
		WaystoneQaCompatTests.plaza(helper, from);
		BlockPos hall = helper.absolutePos(HALL);
		VillageWaystones.round(level, hall);
		BlockPos stone = helper.absolutePos(from).offset(STONE);
		helper.assertTrue(VillageWaystones.standing(level, hall) && VillageWaystones.stoneOf(level, hall).equals(Optional.of(stone)),
			"setup: " + name + "'s stone doesn't stand at " + stone.toShortString() + ": " + VillageWaystones.stoneOf(level, hall));
		helper.assertTrue(named(helper, name).size() == 1, "setup: the mod lists " + describe(named(helper, name)) + " for " + name);
		return working(level, stone, "at the plaza's stone");
	}

	private static Villager mover(GameTestHelper helper) {
		helper.setBlock(MOVER.north(), ModBlocks.MOVERS_CRATE);
		Villager mover = helper.spawn(EntityType.VILLAGER, MOVER);
		mover.setNoAi(true);
		mover.setVillagerData(mover.getVillagerData().setProfession(ModVillagers.MOVER));
		mover.getBrain().setMemory(MemoryModuleType.JOB_SITE, GlobalPos.of(helper.getLevel().dimension(), helper.absolutePos(MOVER.north())));
		return mover;
	}

	/** Moves the plaza at {@code from} (test-relative corner) to {@code to} by the Mover's own work; returns the new stone. */
	private static BlockPos move(GameTestHelper helper, Villager mover, BlockPos from, BlockPos to) {
		ServerLevel level = helper.getLevel();
		BlockPos hall = helper.absolutePos(HALL);
		PendingMoves.get(level).put(new PendingMoves.Move(hall, helper.absolutePos(from), VillageWaystones.PLAZA, helper.absolutePos(to), Rotation.NONE, null));
		MoveSite site = MoveSites.get(level).work(level, hall).orElseThrow(() -> new GameTestAssertException("the plaza's move from " + from.toShortString() + " didn't begin"));
		MovePlan plan = site.plan();
		for (int i = 0; i < 8000 && site.stage() != MoveSite.Stage.DONE; i++) {
			MoverWork.work(level, mover, site);
		}
		helper.assertTrue(site.stage() == MoveSite.Stage.DONE, "the move from " + from.toShortString() + " didn't end: " + site.stage());
		VillageWaystones.round(level, hall);
		BlockPos now = plan.at(STONE, helper.absolutePos(to), Rotation.NONE);
		BlockPos old = helper.absolutePos(from).offset(STONE);
		helper.assertTrue(VillageWaystones.isStone(level.getBlockState(now)) && !VillageWaystones.isStone(level.getBlockState(old)),
			"after the move the new spot " + now.toShortString() + " holds " + level.getBlockState(now) + ", the old " + old.toShortString() + " " + level.getBlockState(old));
		return now;
	}

	/** Every expectation on the village's one stone after a move from {@code old} to {@code now}. */
	private static void checkMoved(GameTestHelper helper, String name, UUID id, BlockPos old, BlockPos now, ServerPlayer found, ServerPlayer stranger, String when) {
		ServerLevel level = helper.getLevel();
		BlockPos hall = helper.absolutePos(HALL);
		Waystone after = working(level, now, when + ": at the new spot");
		helper.assertTrue(after.getWaystoneUid().equals(id), when + ": the stone at the new spot is a new waystone of the mod's (" + after.getWaystoneUid()
			+ "), not the village's " + id + "; the mod lists " + describe(named(helper, name)));
		helper.assertTrue(after.getName().getString().equals(name) && !after.isTransient(), when + ": the moved stone is named '" + after.getName().getString()
			+ "' (transient " + after.isTransient() + "), not " + name);
		List<Waystone> list = named(helper, name);
		helper.assertTrue(list.size() == 1 && (list.get(0).getPos().equals(now) || list.get(0).getPos().equals(now.above())),
			when + ": the Waystones mod should list exactly one stone of " + name + ", at " + now.toShortString() + "; it lists " + describe(list));
		helper.assertTrue(at(level, old).isEmpty(), when + ": the mod still lists a stone at the old spot " + old.toShortString() + ": " + describe(at(level, old)));
		helper.assertTrue(VillageWaystones.stoneOf(level, hall).equals(Optional.of(now)) && VillageWaystones.standing(level, hall),
			when + ": the network's stone of " + name + " is " + VillageWaystones.stoneOf(level, hall) + ", not " + now.toShortString());
		List<String> foundLines = WaystoneQaCompatTests.texts(WaystoneQaCompatTests.mapLore(found, hall), LINE);
		helper.assertTrue(WaystonesAPI.isWaystoneActivated(found, after) && foundLines.contains(name + ", this village: discovered"),
			when + ": the player who discovered " + name + "'s stone before the move: activated " + WaystonesAPI.isWaystoneActivated(found, after) + ", the Map reads " + foundLines);
		List<String> strangerLines = WaystoneQaCompatTests.texts(WaystoneQaCompatTests.mapLore(stranger, hall), LINE);
		helper.assertTrue(!WaystonesAPI.isWaystoneActivated(stranger, after) && strangerLines.contains(name + ", this village: not discovered yet"),
			when + ": the player who never found " + name + "'s stone: activated " + WaystonesAPI.isWaystoneActivated(stranger, after) + ", the Map reads " + strangerLines);
	}

	/** The village's hall, plazas (at every corner given), moves, stones, sites and mock players taken off every list. */
	private static void cleanUp(GameTestHelper helper, List<BlockPos> corners, List<ServerPlayer> players) {
		ServerLevel level = helper.getLevel();
		BlockPos hall = helper.absolutePos(HALL);
		for (BlockPos c : corners) {
			PendingMoves.get(level).remove(helper.absolutePos(c));
			MoveSites.get(level).cancel(level, helper.absolutePos(c));
		}
		BuildSiteManager sites = BuildSiteManager.get(level);
		AABB box = helper.getBounds();
		sites.finishedIn(level).stream().filter(f -> VillageWaystones.isPlaza(f.structure()) && box.contains(Vec3.atCenterOf(f.placement().origin())))
			.map(BuildSiteManager.Finished::placement).toList().forEach(sites::forgetFinished);
		sites.all().stream().filter(s -> box.contains(Vec3.atCenterOf(s.placement().origin()))).toList().forEach(s -> sites.remove(s.id()));
		VillageWaystones.Data.get(level).forget(hall);
		RealmData.get(level.getServer()).forgetKnown(GlobalPos.of(level.dimension(), hall));
		for (BlockPos p : BlockPos.betweenClosed(helper.absolutePos(new BlockPos(0, 1, 0)), helper.absolutePos(new BlockPos(29, 8, 29)))) {
			if (VillageWaystones.isStone(level.getBlockState(p))) {
				level.setBlock(p.above(), Blocks.AIR.defaultBlockState(), 3);
				level.setBlock(p.immutable(), Blocks.AIR.defaultBlockState(), 3);
			}
		}
		level.removeBlock(hall, false);
		for (ServerPlayer p : players) {
			level.getServer().getPlayerList().remove(p);
		}
	}

	/**
	 * The whole check for one move: the village {@code name} with its plaza at {@code from}, a player who discovered its
	 * stone and one who didn't; the Mover moves the plaza to {@code to}, across the chunk border(s) {@code axes} names
	 * ("x", "z" or "xz"); then the stone is the same, discovered by the one player only, listed once at the new spot, and so
	 * again after the mod's list is saved and loaded.
	 */
	private static void crossing(GameTestHelper helper, String name, BlockPos from, BlockPos to, String axes) {
		clear(helper);
		ServerLevel level = helper.getLevel();
		List<ServerPlayer> players = new ArrayList<>();
		List<BlockPos> corners = List.of(from, to);
		try {
			Waystone before = village(helper, name, from);
			UUID id = before.getWaystoneUid();
			ServerPlayer found = helper.makeMockServerPlayerInLevel();
			ServerPlayer stranger = helper.makeMockServerPlayerInLevel();
			players.add(found);
			players.add(stranger);
			WaystonesAPI.activateWaystone(found, before);
			BlockPos old = helper.absolutePos(from).offset(STONE);
			BlockPos now = move(helper, mover(helper), from, to);
			ChunkPos a = new ChunkPos(old);
			ChunkPos b = new ChunkPos(now);
			// (the sideways shift that keeps the two footprints apart may cross the other axis's border too: that's allowed)
			helper.assertTrue((!axes.contains("x") || a.x != b.x) && (!axes.contains("z") || a.z != b.z),
				"setup: the move from " + old.toShortString() + " (chunk " + a + ", local " + (old.getX() & 15) + "," + (old.getZ() & 15) + ") to "
					+ now.toShortString() + " (chunk " + b + ") doesn't cross the " + axes + " border(s) as laid out");
			checkMoved(helper, name, id, old, now, found, stranger, "after the move");
			reloadWaystones(level);
			VillageWaystones.round(level, helper.absolutePos(HALL));
			checkMoved(helper, name, id, old, now, found, stranger, "after the move, the list saved and loaded");
			LOGGER.info("QA B1310/B1375: {} moved {} (chunk {}, local {},{}) -> {} (chunk {}): one stone {}, still discovered", name, old.toShortString(), a,
				old.getX() & 15, old.getZ() & 15, now.toShortString(), b, describe(named(helper, name)));
		} finally {
			cleanUp(helper, corners, players);
		}
		helper.succeed();
	}

	// ---- one border -------------------------------------------------------------------------------------------------------

	/** The stone at chunk-local x 0, moved one block west (into the chunk west of it) and 11 south. */
	//$ gametest_ticks_batch AREA '300' '"qaWaystoneChunkMove1121dX0"'
	@GameTest(template = AREA, timeoutTicks = 300, batch = "qaWaystoneChunkMove1121dX0")
	public void aStoneAtChunkLocalX0MovedWestIsStillTheVillagesOneDiscoveredStone(GameTestHelper helper) {
		int c = cornerX(helper, 2, 17, 0);
		crossing(helper, "Westerholt", new BlockPos(c, 2, 3), new BlockPos(c - 1, 2, 14), "x");
	}

	/** The stone at chunk-local x 15, moved one block east (into the chunk east of it) and 11 south. */
	//$ gametest_ticks_batch AREA '300' '"qaWaystoneChunkMove1121dX15"'
	@GameTest(template = AREA, timeoutTicks = 300, batch = "qaWaystoneChunkMove1121dX15")
	public void aStoneAtChunkLocalX15MovedEastIsStillTheVillagesOneDiscoveredStone(GameTestHelper helper) {
		int c = cornerX(helper, 1, 16, 15);
		crossing(helper, "Eastwell", new BlockPos(c, 2, 3), new BlockPos(c + 1, 2, 14), "x");
	}

	/** The stone at chunk-local z 0 (where B1375's failing run had it), moved one block north (into the chunk north) and 11 east. */
	//$ gametest_ticks_batch AREA '300' '"qaWaystoneChunkMove1121dZ0"'
	@GameTest(template = AREA, timeoutTicks = 300, batch = "qaWaystoneChunkMove1121dZ0")
	public void aStoneAtChunkLocalZ0MovedNorthIsStillTheVillagesOneDiscoveredStone(GameTestHelper helper) {
		int c = cornerZ(helper, 1, 16, 0);
		crossing(helper, "Northby", new BlockPos(3, 2, c), new BlockPos(14, 2, c - 1), "z");
	}

	/** The stone at chunk-local z 15, moved one block south (into the chunk south) and 11 east. */
	//$ gametest_ticks_batch AREA '300' '"qaWaystoneChunkMove1121dZ15"'
	@GameTest(template = AREA, timeoutTicks = 300, batch = "qaWaystoneChunkMove1121dZ15")
	public void aStoneAtChunkLocalZ15MovedSouthIsStillTheVillagesOneDiscoveredStone(GameTestHelper helper) {
		int c = cornerZ(helper, 0, 15, 15);
		crossing(helper, "Southam", new BlockPos(3, 2, c), new BlockPos(14, 2, c + 1), "z");
	}

	// ---- two borders and repeats ------------------------------------------------------------------------------------------

	/** The stone at chunk-local x 15, z 15, moved into the chunk diagonally beyond it (across both borders at once). */
	//$ gametest_ticks_batch AREA '300' '"qaWaystoneChunkMove1121dDiagonal"'
	@GameTest(template = AREA, timeoutTicks = 300, batch = "qaWaystoneChunkMove1121dDiagonal")
	public void aStoneMovedAcrossTwoChunkBordersAtOnceIsStillTheVillagesOneDiscoveredStone(GameTestHelper helper) {
		int x = cornerX(helper, 1, 16, 15);
		int z = cornerZ(helper, 0, 15, 15);
		crossing(helper, "Crosswick", new BlockPos(x, 2, z), new BlockPos(x + 1, 2, z + 7), "xz");
	}

	/**
	 * The stone at chunk-local z 0 moved across the border, then moved again back across it (a repeat): after each move the
	 * stone is the village's same one, discovered by the one player, listed once where it stands now.
	 */
	//$ gametest_ticks_batch AREA '400' '"qaWaystoneChunkMove1121dBack"'
	@GameTest(template = AREA, timeoutTicks = 400, batch = "qaWaystoneChunkMove1121dBack")
	public void aStoneMovedAcrossTheBorderAndBackIsStillTheVillagesOneDiscoveredStone(GameTestHelper helper) {
		clear(helper);
		int c = cornerZ(helper, 1, 16, 0);
		BlockPos first = new BlockPos(3, 2, c);
		BlockPos second = new BlockPos(14, 2, c - 1);
		BlockPos third = new BlockPos(22, 2, c);
		String name = "Backmoor";
		List<ServerPlayer> players = new ArrayList<>();
		try {
			Waystone before = village(helper, name, first);
			UUID id = before.getWaystoneUid();
			ServerPlayer found = helper.makeMockServerPlayerInLevel();
			ServerPlayer stranger = helper.makeMockServerPlayerInLevel();
			players.add(found);
			players.add(stranger);
			WaystonesAPI.activateWaystone(found, before);
			Villager mover = mover(helper);
			BlockPos old = helper.absolutePos(first).offset(STONE);
			BlockPos mid = move(helper, mover, first, second);
			helper.assertTrue(new ChunkPos(old).z != new ChunkPos(mid).z, "setup: the first move doesn't cross a chunk border");
			checkMoved(helper, name, id, old, mid, found, stranger, "after the first move");
			BlockPos now = move(helper, mover, second, third);
			helper.assertTrue(new ChunkPos(mid).z != new ChunkPos(now).z, "setup: the second move doesn't cross back");
			checkMoved(helper, name, id, mid, now, found, stranger, "after the second move (back across the border)");
			helper.assertTrue(at(helper.getLevel(), old).isEmpty(), "the mod lists a stone at the first spot " + old.toShortString() + ": " + describe(at(helper.getLevel(), old)));
		} finally {
			cleanUp(helper, List.of(first, second, third), players);
		}
		helper.succeed();
	}

	// ---- broken at the new spot and repaired ------------------------------------------------------------------------------

	/**
	 * The builder's plaza moved across a chunk border by a Mover; the moved stone is broken at its new spot; the builder's
	 * upkeep puts a waystone back at the NEW spot (never the old one) and the Waystones mod still lists exactly one stone of
	 * the village, there, with its name kept; so again after the mod's list is saved and loaded.
	 */
	private static void repaired(GameTestHelper helper, String name, BlockPos from, BlockPos to) {
		clear(helper);
		ServerLevel level = helper.getLevel();
		helper.setDayTime(2000);
		boolean was = Upkeep.ENABLED;
		boolean asks = Upkeep.ASK;
		int delay = level.getGameRules().getInt(ModGameRules.BUILD_DELAY);
		BlockPos hall = helper.absolutePos(HALL);
		BlockPos old = helper.absolutePos(from).offset(STONE);
		PartnerShowsCompatTests.after(helper, () -> {
			Upkeep.ENABLED = was;
			Upkeep.ASK = asks;
			level.getGameRules().getRule(ModGameRules.BUILD_DELAY).set(delay, level.getServer());
			cleanUp(helper, List.of(from, to), List.of());
		});
		level.getGameRules().getRule(ModGameRules.BUILD_DELAY).set(1, level.getServer());
		village(helper, name, from);
		helper.setBlock(BENCH, ModBlocks.BUILDERS_BENCH);
		Villager builder = helper.spawn(EntityType.VILLAGER, BUILDER);
		Builders.employ(level, builder, helper.absolutePos(BENCH));
		BuildSiteManager sites = BuildSiteManager.get(level);
		sites.forgetFinished(WaystoneQaCompatTests.placement(helper, from));
		sites.recordFinished(VillageWaystones.PLAZA, WaystoneQaCompatTests.placement(helper, from), builder.getUUID()); // the builder's own build
		Villager mover = mover(helper);
		BlockPos now = move(helper, mover, from, to);
		mover.discard();
		helper.assertTrue(!new ChunkPos(old).equals(new ChunkPos(now)), "setup: the move doesn't cross a chunk border: " + old.toShortString() + " -> " + now.toShortString());
		helper.assertTrue(named(helper, name).size() == 1, "after the move, before the break, the mod lists " + describe(named(helper, name)));

		BlockState lower = level.getBlockState(now);
		level.setBlock(now.above(), Blocks.AIR.defaultBlockState(), 3);
		level.setBlock(now, Blocks.AIR.defaultBlockState(), 3);
		helper.assertFalse(VillageWaystones.standing(level, hall), "setup: the broken moved stone still stands");
		VillageWaystones.round(level, hall);
		helper.setBlock(CHEST, Blocks.CHEST);
		Container chest = helper.getBlockEntity(CHEST);
		chest.setItem(0, new ItemStack(lower.getBlock().asItem()));
		Upkeep.ENABLED = true;
		Upkeep.ASK = false; // 36.15: repairs without asking
		helper.startSequence()
			.thenExecuteAfter(2, () -> {
				BuildSite repair = ModAttachments.BUILDER_JOB.has(builder) ? Builders.activeSite(level, builder) : Upkeep.look(level, builder);
				helper.assertTrue(repair != null && repair.isRepair(), "no repair of the moved plaza started");
			})
			.thenWaitUntil(() -> {
				helper.assertFalse(VillageWaystones.isStone(level.getBlockState(old)) || VillageWaystones.isStone(level.getBlockState(old.above())),
					"the builder put a waystone back at the OLD spot " + old.toShortString() + " (the plaza now stands at " + now.toShortString() + ")");
				helper.assertTrue(VillageWaystones.isStone(level.getBlockState(now)), "no stone back at the moved spot yet");
				VillageWaystones.round(level, hall);
				Optional<String> stoneName = VillageWaystones.EXTENSION.call(s -> s.nameAt(level, now), Optional.<String>empty());
				helper.assertTrue(stoneName.equals(Optional.of(name)), "the stone put back at the moved spot is named " + stoneName + ", not " + name);
			})
			.thenExecute(() -> {
				for (String when : List.of("after the repair", "after the repair, the list saved and loaded")) {
					if (when.endsWith("loaded")) {
						reloadWaystones(level);
						VillageWaystones.round(level, hall);
					}
					List<Waystone> list = named(helper, name);
					helper.assertTrue(list.size() == 1 && (list.get(0).getPos().equals(now) || list.get(0).getPos().equals(now.above())) && list.get(0).isValid(),
						when + ": the Waystones mod should list exactly one stone of " + name + ", at " + now.toShortString() + "; it lists " + describe(list));
					helper.assertTrue(at(level, old).isEmpty(), when + ": the mod lists a stone at the old spot " + old.toShortString() + ": " + describe(at(level, old)));
					helper.assertTrue(VillageWaystones.stoneOf(level, hall).equals(Optional.of(now)) && VillageWaystones.standing(level, hall),
						when + ": the network keeps " + VillageWaystones.stoneOf(level, hall) + ", not the moved spot " + now.toShortString());
				}
				LOGGER.info("QA B1375: {}'s moved stone (old {} chunk-local {},{}) broken at {} and repaired: {}", name, old.toShortString(), old.getX() & 15,
					old.getZ() & 15, now.toShortString(), describe(named(helper, name)));
			})
			.thenSucceed();
	}

	/** Old stone at chunk-local z 0 (B1375's failing run), moved north across the border, broken there and repaired. */
	//$ gametest_ticks_batch AREA '1600' '"qaWaystoneChunkMove1121dRepairZ0"'
	@GameTest(template = AREA, timeoutTicks = 1600, batch = "qaWaystoneChunkMove1121dRepairZ0")
	public void aMovedStoneAcrossTheZBorderBrokenAndRepairedIsListedOnce(GameTestHelper helper) {
		int c = cornerZ(helper, 1, 16, 0);
		repaired(helper, "Mendham", new BlockPos(3, 2, c), new BlockPos(14, 2, c - 1));
	}

	/** Old stone at chunk-local x 15, moved east across the border, broken there and repaired. */
	//$ gametest_ticks_batch AREA '1600' '"qaWaystoneChunkMove1121dRepairX15"'
	@GameTest(template = AREA, timeoutTicks = 1600, batch = "qaWaystoneChunkMove1121dRepairX15")
	public void aMovedStoneAcrossTheXBorderBrokenAndRepairedIsListedOnce(GameTestHelper helper) {
		int c = cornerX(helper, 1, 16, 15);
		repaired(helper, "Fixbury", new BlockPos(c, 2, 3), new BlockPos(c + 1, 2, 14));
	}
}
