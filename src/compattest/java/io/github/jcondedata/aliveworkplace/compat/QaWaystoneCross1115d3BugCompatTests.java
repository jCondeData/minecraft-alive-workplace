package io.github.jcondedata.aliveworkplace.compat;

import io.github.jcondedata.aliveworkplace.build.BuildSiteManager;
import io.github.jcondedata.aliveworkplace.hall.Caravans;
import io.github.jcondedata.aliveworkplace.hall.VillageHallBlockEntity;
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
import java.util.List;
import java.util.Optional;
import java.util.UUID;
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
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Rotation;

/**
 * B1310 (lane D QA 2026-11-15; registered with the fix, lane A 2026-11-17). Waystones (41.1-41.3) crossed with what landed after it was verified: a Mover (45.x) moving a
 * village's waystone plaza, and a village whose saved name is blank (B1229/B1233's made-up name). From the specs, what a
 * player expects: the moved plaza's stone is a working waystone of the Waystones mod with the village's name at its new
 * spot and nothing left registered at the old one (no ghost in the mod's waystone list), through a save and reload half
 * way; a player who had discovered the stone still has it; the hall's Map lists the stone where it now stands; caravans
 * still step through it; a move called off after the stone came down puts the same working stone back; and a village with
 * a blank name gives its stone the made-up name (read as words), which a rename then replaces.
 */
public class QaWaystoneCross1115d3BugCompatTests implements FabricGameTest {
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

	/** The waystones the Waystones mod has registered (its own saved list) with their lower or upper half at {@code pos}. */
	private static List<Waystone> registeredAt(ServerLevel level, BlockPos pos) {
		return WaystoneManagerImpl.get(level.getServer()).getWaystones()
			.filter(w -> w.getDimension().equals(level.dimension()) && (w.getPos().equals(pos) || w.getPos().equals(pos.above())))
			.toList();
	}

	/** The waystones of the mod's list named {@code name}, anywhere. */
	private static List<Waystone> registeredNamed(ServerLevel level, String name) {
		return WaystoneManagerImpl.get(level.getServer()).getWaystones().filter(w -> w.getName().getString().equals(name)).toList();
	}

	private static String describe(List<Waystone> list) {
		return list.stream().map(w -> w.getName().getString() + "@" + w.getPos() + (w.isValid() ? "" : " (invalid)")).toList().toString();
	}

	private static Optional<String> nameAt(ServerLevel level, BlockPos stone) {
		return VillageWaystones.EXTENSION.call(s -> s.nameAt(level, stone), Optional.<String>empty());
	}

	/** A Mover with his crate, not walking (his swings are driven by the test). */
	private static Villager mover(GameTestHelper helper) {
		helper.setBlock(MOVER.north(), ModBlocks.MOVERS_CRATE);
		Villager mover = helper.spawn(EntityType.VILLAGER, MOVER);
		mover.setNoAi(true);
		mover.setVillagerData(mover.getVillagerData().setProfession(ModVillagers.MOVER));
		mover.getBrain().setMemory(MemoryModuleType.JOB_SITE, GlobalPos.of(helper.getLevel().dimension(), helper.absolutePos(MOVER.north())));
		return mover;
	}

	/** Ashford's plaza, asked to be moved to {@link #TO} turned {@code rotation}; the move begun. */
	private static MoveSite beginMove(GameTestHelper helper, Rotation rotation) {
		ServerLevel level = helper.getLevel();
		BlockPos hall = helper.absolutePos(HALL_A);
		BlockPos from = helper.absolutePos(PLAZA_A);
		PendingMoves.get(level).put(new PendingMoves.Move(hall, from, VillageWaystones.PLAZA, helper.absolutePos(TO), rotation, null));
		return MoveSites.get(level).work(level, hall).orElseThrow(() -> new GameTestAssertException("the plaza's move didn't begin"));
	}

	/** Swings until the old stone's spot holds no waystone (the stone is down), the move not yet over. */
	private static void untilTheStoneIsDown(GameTestHelper helper, Villager mover, MoveSite site) {
		ServerLevel level = helper.getLevel();
		BlockPos old = WaystoneQaCompatTests.stone(helper, PLAZA_A);
		for (int i = 0; i < 5000 && site.stage() != MoveSite.Stage.DONE && VillageWaystones.isStone(level.getBlockState(old)); i++) {
			MoverWork.work(level, mover, site);
		}
		helper.assertTrue(site.stage() != MoveSite.Stage.DONE && level.getBlockState(old).isAir() && level.getBlockState(old.above()).isAir(),
			"the stone wasn't taken down before the move ended: " + site.stage() + " " + level.getBlockState(old) + " / " + level.getBlockState(old.above()));
	}

	private static void untilDone(GameTestHelper helper, Villager mover, MoveSite site) {
		for (int i = 0; i < 8000 && site.stage() != MoveSite.Stage.DONE; i++) {
			MoverWork.work(helper.getLevel(), mover, site);
		}
		helper.assertTrue(site.stage() == MoveSite.Stage.DONE, "the move didn't end: " + site.stage());
	}

	/** Where the moved stone's lower half stands. */
	private static BlockPos newStone(GameTestHelper helper, MovePlan plan, Rotation rotation) {
		return plan.at(STONE, helper.absolutePos(TO), rotation);
	}

	/** Ashford (and Brookmere when {@code both}) with a standing plaza stone; Ashford's realm entry named {@code name}. */
	private static UUID setUp(GameTestHelper helper, String nameA, String nameB, boolean both) {
		RaidHelperCompatTests.clearAround(helper); // B541: halls and stones earlier batches left nearby
		VillageHalls.forgetNearest();
		cleanUp(helper);
		ServerLevel level = helper.getLevel();
		VillageWaystones.Data.get(level).halls().forEach(VillageWaystones.Data.get(level)::forget);
		UUID owner = UUID.randomUUID();
		WaystoneQaCompatTests.hall(helper, HALL_A, nameA, owner);
		WaystoneQaCompatTests.plaza(helper, PLAZA_A);
		if (both) {
			WaystoneQaCompatTests.hall(helper, HALL_B, nameB, owner);
			WaystoneQaCompatTests.plaza(helper, PLAZA_B);
			VillageWaystones.round(level, helper.absolutePos(HALL_B));
		}
		VillageWaystones.round(level, helper.absolutePos(HALL_A));
		helper.assertTrue(VillageWaystones.standing(level, helper.absolutePos(HALL_A)), "setup: " + nameA + "'s stone doesn't stand");
		helper.assertTrue(registeredNamed(level, nameA).size() == 1, "setup: the mod lists " + describe(registeredNamed(level, nameA)) + " for " + nameA);
		return owner;
	}

	/** Takes the halls, plazas (wherever they stand now), moves and stones of these tests off every list. */
	private static void cleanUp(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		PendingMoves.get(level).remove(helper.absolutePos(PLAZA_A));
		MoveSites.get(level).cancel(level, helper.absolutePos(PLAZA_A));
		BuildSiteManager sites = BuildSiteManager.get(level);
		net.minecraft.world.phys.AABB box = helper.getBounds();
		sites.finishedIn(level).stream().filter(f -> VillageWaystones.isPlaza(f.structure()) && box.contains(net.minecraft.world.phys.Vec3.atCenterOf(f.placement().origin())))
			.map(BuildSiteManager.Finished::placement).toList().forEach(sites::forgetFinished);
		WaystoneQaCompatTests.cleanUp(helper);
		// the stones broken, so the Waystones mod takes them off its list
		for (BlockPos p : BlockPos.betweenClosed(helper.absolutePos(new BlockPos(0, 1, 0)), helper.absolutePos(new BlockPos(29, 8, 29)))) {
			if (VillageWaystones.isStone(level.getBlockState(p))) {
				level.setBlock(p.above(), Blocks.AIR.defaultBlockState(), 3);
				level.setBlock(p.immutable(), Blocks.AIR.defaultBlockState(), 3);
			}
		}
	}

	/**
	 * A player who had discovered the plaza's stone before the Mover moved the plaza still has it discovered: the Map
	 * reads "Fallowmere, this village: discovered", and the moved stone counts as activated for that player.
	 */
	//$ gametest_ticks_batch AREA '200' '"qaWaystoneCross1115d3Known"'
	@GameTest(template = AREA, timeoutTicks = 200, batch = "qaWaystoneCross1115d3Known")
	public void aPlayerWhoDiscoveredTheStoneStillHasItAfterTheMove(GameTestHelper helper) {
		setUp(helper, "Fallowmere", null, false);
		ServerLevel level = helper.getLevel();
		BlockPos hall = helper.absolutePos(HALL_A);
		try {
			ServerPlayer player = helper.makeMockServerPlayerInLevel();
			Waystone before = WaystonesAPI.getWaystoneAt(level, WaystoneQaCompatTests.stone(helper, PLAZA_A)).filter(Waystone::isValid)
				.orElseThrow(() -> new GameTestAssertException("setup: no waystone"));
			WaystonesAPI.activateWaystone(player, before);
			helper.assertTrue(WaystoneQaCompatTests.texts(WaystoneQaCompatTests.mapLore(player, hall), LINE).contains("Fallowmere, this village: discovered"), "setup: not discovered");
			Villager mover = mover(helper);
			MoveSite site = beginMove(helper, Rotation.NONE);
			MovePlan plan = site.plan();
			untilDone(helper, mover, site);
			VillageWaystones.round(level, hall);
			BlockPos now = newStone(helper, plan, Rotation.NONE);
			Waystone after = WaystonesAPI.getWaystoneAt(level, now).filter(Waystone::isValid)
				.orElseThrow(() -> new GameTestAssertException("no working waystone at the new spot " + now + ": " + level.getBlockState(now)));
			List<String> lines = WaystoneQaCompatTests.texts(WaystoneQaCompatTests.mapLore(player, hall), LINE);
			helper.assertTrue(WaystonesAPI.isWaystoneActivated(player, after) && lines.contains("Fallowmere, this village: discovered"),
				"the player discovered Fallowmere's stone before the move; after it the stone is " + (WaystonesAPI.isWaystoneActivated(player, after) ? "" : "not ")
					+ "activated for them and the Map reads " + lines);
		} finally {
			cleanUp(helper);
		}
		helper.succeed();
	}

	/**
	 * B1310 through a save and reload: the stone is down (packed in the Mover's crates) when the game saves and loads
	 * again (the move and the Waystones mod's own list of waystones both read back from what was saved), and the plaza is
	 * then put up at its new spot. The player who had discovered the stone still has it, it is the same waystone of the
	 * mod's as before the move (one in the mod's list, named after the village), and the Map reads "discovered".
	 */
	//$ gametest_ticks_batch AREA '200' '"qaWaystoneCross1115d3KnownReload"'
	@GameTest(template = AREA, timeoutTicks = 200, batch = "qaWaystoneCross1115d3KnownReload")
	public void aPlayerWhoDiscoveredTheStoneStillHasItAfterASaveMidMove(GameTestHelper helper) {
		setUp(helper, "Thornwick", null, false);
		ServerLevel level = helper.getLevel();
		BlockPos hall = helper.absolutePos(HALL_A);
		try {
			ServerPlayer player = helper.makeMockServerPlayerInLevel();
			Waystone before = WaystonesAPI.getWaystoneAt(level, WaystoneQaCompatTests.stone(helper, PLAZA_A)).filter(Waystone::isValid)
				.orElseThrow(() -> new GameTestAssertException("setup: no waystone"));
			UUID id = before.getWaystoneUid();
			WaystonesAPI.activateWaystone(player, before);
			Villager mover = mover(helper);
			MoveSite site = beginMove(helper, Rotation.CLOCKWISE_90);
			MovePlan plan = site.plan();
			untilTheStoneIsDown(helper, mover, site);
			// the game saves and loads: the move, and the Waystones mod's list of waystones
			CompoundTag saved = MoveSites.get(level).save(new CompoundTag(), level.registryAccess());
			level.getDataStorage().set(MoveSites.NAME, MoveSites.load(saved, level.registryAccess()));
			CompoundTag stones = WaystoneManagerImpl.get(level.getServer()).save(new CompoundTag(), level.registryAccess());
			level.getServer().overworld().getDataStorage().set("waystones", WaystoneManagerImpl.read(stones, level.registryAccess()));
			site = MoveSites.get(level).forBuilding(helper.absolutePos(PLAZA_A)).orElseThrow(() -> new GameTestAssertException("the move is gone after a reload"));
			untilDone(helper, mover, site);
			VillageWaystones.round(level, hall);
			BlockPos now = newStone(helper, plan, Rotation.CLOCKWISE_90);
			Waystone after = WaystonesAPI.getWaystoneAt(level, now).filter(Waystone::isValid)
				.orElseThrow(() -> new GameTestAssertException("no working waystone at the new spot " + now + ": " + level.getBlockState(now)));
			List<String> lines = WaystoneQaCompatTests.texts(WaystoneQaCompatTests.mapLore(player, hall), LINE);
			helper.assertTrue(WaystonesAPI.isWaystoneActivated(player, after) && lines.contains("Thornwick, this village: discovered"),
				"the player discovered Thornwick's stone before the move (saved and loaded with the stone down); after it the stone is "
					+ (WaystonesAPI.isWaystoneActivated(player, after) ? "" : "not ") + "activated for them and the Map reads " + lines);
			helper.assertTrue(after.getWaystoneUid().equals(id), "the moved stone is a new waystone of the mod's, not the one the village had");
			helper.assertTrue(registeredNamed(level, "Thornwick").size() == 1 && registeredAt(level, now).size() == 1,
				"the mod lists " + describe(registeredNamed(level, "Thornwick")) + " named Thornwick, " + describe(registeredAt(level, now)) + " at " + now);
			helper.assertTrue(registeredAt(level, WaystoneQaCompatTests.stone(helper, PLAZA_A)).isEmpty(), "the mod still lists a stone at the old spot: "
				+ describe(registeredAt(level, WaystoneQaCompatTests.stone(helper, PLAZA_A))));
		} finally {
			cleanUp(helper);
		}
		helper.succeed();
	}

}
