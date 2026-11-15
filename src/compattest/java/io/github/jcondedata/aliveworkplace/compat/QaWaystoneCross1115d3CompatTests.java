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
 * QA (lane D, 2026-11-15), Waystones (41.1-41.3) crossed with what landed after it was verified: a Mover (45.x) moving a
 * village's waystone plaza, and a village whose saved name is blank (B1229/B1233's made-up name). From the specs, what a
 * player expects: the moved plaza's stone is a working waystone of the Waystones mod with the village's name at its new
 * spot and nothing left registered at the old one (no ghost in the mod's waystone list), through a save and reload half
 * way; the hall's Map lists the stone where it now stands; caravans
 * still step through it; a move called off after the stone came down puts the same working stone back; and a village with
 * a blank name gives its stone the made-up name (read as words), which a rename then replaces.
 */
public class QaWaystoneCross1115d3CompatTests implements FabricGameTest {
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
	 * Ashford's plaza moved a quarter turn by a Mover, through a save and reload of the move just after the stone came
	 * down: at the new spot stands a working waystone of the mod's (both halves) named Ashford's name; the mod's own list
	 * has nothing left at the old spot and one stone of that name; the hall's network keeps the new spot, and its Map reads
	 * "Moorhaven, this village: not discovered yet"; a player can discover the moved stone.
	 */
	//$ gametest_ticks_batch AREA '200' '"qaWaystoneCross1115d3Moved"'
	@GameTest(template = AREA, timeoutTicks = 200, batch = "qaWaystoneCross1115d3Moved")
	public void aMovedPlazaHasOneWorkingNamedStoneAtItsNewSpotAndNoGhost(GameTestHelper helper) {
		setUp(helper, "Moorhaven", null, false);
		ServerLevel level = helper.getLevel();
		BlockPos hall = helper.absolutePos(HALL_A);
		BlockPos old = WaystoneQaCompatTests.stone(helper, PLAZA_A);
		try {
			Villager mover = mover(helper);
			MoveSite site = beginMove(helper, Rotation.CLOCKWISE_90);
			MovePlan plan = site.plan();
			untilTheStoneIsDown(helper, mover, site);
			CompoundTag saved = MoveSites.get(level).save(new CompoundTag(), level.registryAccess());
			level.getDataStorage().set(MoveSites.NAME, MoveSites.load(saved, level.registryAccess()));
			site = MoveSites.get(level).forBuilding(helper.absolutePos(PLAZA_A)).orElseThrow(() -> new GameTestAssertException("the move is gone after a reload"));
			untilDone(helper, mover, site);
			VillageWaystones.round(level, hall);

			BlockPos now = newStone(helper, plan, Rotation.CLOCKWISE_90);
			helper.assertTrue(VillageWaystones.isStone(level.getBlockState(now)) && level.getBlockState(now.above()).is(level.getBlockState(now).getBlock()),
				"no whole waystone at the new spot " + now + ": " + level.getBlockState(now) + " / " + level.getBlockState(now.above()));
			helper.assertTrue(nameAt(level, now).equals(Optional.of("Moorhaven")), "the moved stone is " + nameAt(level, now) + ", not a working waystone named Moorhaven");
			helper.assertTrue(registeredAt(level, old).isEmpty(), "the Waystones mod still lists a stone at the old spot " + old + ": " + describe(registeredAt(level, old)));
			helper.assertTrue(registeredAt(level, now).size() == 1, "the mod lists at the new spot: " + describe(registeredAt(level, now)));
			helper.assertTrue(registeredNamed(level, "Moorhaven").size() == 1, "the mod's list has " + describe(registeredNamed(level, "Moorhaven")) + " named Moorhaven");
			helper.assertTrue(VillageWaystones.stoneOf(level, hall).equals(Optional.of(now)), "the network keeps the stone at " + VillageWaystones.stoneOf(level, hall) + ", not " + now);
			ServerPlayer player = helper.makeMockServerPlayerInLevel();
			List<String> lines = WaystoneQaCompatTests.texts(WaystoneQaCompatTests.mapLore(player, hall), LINE);
			helper.assertTrue(lines.equals(List.of("Waystone network (1):", "Moorhaven, this village: not discovered yet")), "Moorhaven's Map after the move: " + lines);
			Waystone w = WaystonesAPI.getWaystoneAt(level, now).filter(Waystone::isValid).orElseThrow(() -> new GameTestAssertException("no waystone to discover"));
			WaystonesAPI.activateWaystone(player, w);
			lines = WaystoneQaCompatTests.texts(WaystoneQaCompatTests.mapLore(player, hall), LINE);
			helper.assertTrue(lines.contains("Moorhaven, this village: discovered"), "after discovering the moved stone the Map reads " + lines);
		} finally {
			cleanUp(helper);
		}
		helper.succeed();
	}

	/**
	 * Ashford's plaza moved, Brookmere's standing: the two villages are still linked by waystone, a caravan from Ashford to
	 * Brookmere goes by waystone, and travelers step in beside the moved stone (not the old spot).
	 */
	//$ gametest_ticks_batch AREA '200' '"qaWaystoneCross1115d3Caravan"'
	@GameTest(template = AREA, timeoutTicks = 200, batch = "qaWaystoneCross1115d3Caravan")
	public void caravansStillStepThroughTheMovedStone(GameTestHelper helper) {
		setUp(helper, "Tarnwick", "Selbourne", true);
		ServerLevel level = helper.getLevel();
		BlockPos a = helper.absolutePos(HALL_A);
		BlockPos b = helper.absolutePos(HALL_B);
		try {
			helper.assertTrue(VillageWaystones.linked(level, a, b), "setup: not linked");
			Villager mover = mover(helper);
			MoveSite site = beginMove(helper, Rotation.CLOCKWISE_180);
			MovePlan plan = site.plan();
			untilDone(helper, mover, site);
			VillageWaystones.round(level, a);
			BlockPos now = newStone(helper, plan, Rotation.CLOCKWISE_180);
			helper.assertTrue(VillageWaystones.linked(level, a, b), "after Tarnwick's plaza was moved the villages aren't linked by waystone: stone kept at "
				+ VillageWaystones.stoneOf(level, a) + ", moved to " + now + " " + level.getBlockState(now));
			BlockPos step = VillageWaystones.beside(level, VillageWaystones.stoneOf(level, a).orElseThrow(), b);
			helper.assertTrue(step.distManhattan(now) <= 5, "travelers step in at " + step + ", not beside the moved stone " + now);

			helper.setBlock(new BlockPos(14, 2, 1), ModBlocks.STOREHOUSE);
			helper.setBlock(new BlockPos(14, 2, 3), Blocks.CHEST);
			helper.setBlock(new BlockPos(24, 2, 14), ModBlocks.STOREHOUSE);
			helper.setBlock(new BlockPos(24, 2, 16), Blocks.CHEST);
			Container chest = helper.getBlockEntity(new BlockPos(14, 2, 3));
			chest.setItem(0, new ItemStack(Items.OAK_LOG, 64));
			Caravans.Data data = Caravans.Data.get(level);
			data.setWants(a, Component.literal("Tarnwick"), List.of());
			data.setWants(b, Component.literal("Selbourne"), List.of(new Caravans.Want(Items.OAK_LOG, 16)));
			if (!data.routesFrom(a).contains(b)) {
				helper.assertTrue(data.toggleRoute(a, b), "setup: no route");
			}
			data.sent(a, -1);
			Caravans.round(level, a, null);
			Caravans.Shipment s = data.onTheRoad().stream().filter(x -> x.from().equals(a) && x.to().equals(b)).reduce((x, y) -> y)
				.orElseThrow(() -> new GameTestAssertException("no caravan left Tarnwick: " + data.onTheRoad()));
			helper.assertTrue(s.byWaystone(), "the caravan from Tarnwick walks though both stones stand: " + s);
		} finally {
			cleanUp(helper);
		}
		helper.succeed();
	}

	/**
	 * The move called off after the Mover took the stone down (45.3: everything taken down goes back where it was): the
	 * same spot holds a working waystone named Hollinsby again, the mod lists exactly one Hollinsby, and nothing is
	 * registered at the spot it was going to.
	 */
	//$ gametest_ticks_batch AREA '200' '"qaWaystoneCross1115d3CalledOff"'
	@GameTest(template = AREA, timeoutTicks = 200, batch = "qaWaystoneCross1115d3CalledOff")
	public void aMoveCalledOffAfterTheStoneCameDownPutsAWorkingStoneBack(GameTestHelper helper) {
		setUp(helper, "Hollinsby", null, false);
		ServerLevel level = helper.getLevel();
		BlockPos hall = helper.absolutePos(HALL_A);
		BlockPos old = WaystoneQaCompatTests.stone(helper, PLAZA_A);
		try {
			Villager mover = mover(helper);
			MoveSite site = beginMove(helper, Rotation.NONE);
			BlockPos would = newStone(helper, site.plan(), Rotation.NONE);
			untilTheStoneIsDown(helper, mover, site);
			MoveSites.get(level).cancel(level, helper.absolutePos(PLAZA_A));
			untilDone(helper, mover, site);
			VillageWaystones.round(level, hall);
			helper.assertTrue(VillageWaystones.isStone(level.getBlockState(old)), "no stone back at " + old + ": " + level.getBlockState(old));
			helper.assertTrue(nameAt(level, old).equals(Optional.of("Hollinsby")), "the stone put back is " + nameAt(level, old) + ", not a working waystone named Hollinsby");
			helper.assertTrue(registeredNamed(level, "Hollinsby").size() == 1, "the mod's list has " + describe(registeredNamed(level, "Hollinsby")) + " named Hollinsby");
			helper.assertTrue(registeredAt(level, would).isEmpty(), "a stone is registered where the plaza was going: " + describe(registeredAt(level, would)));
			helper.assertTrue(VillageWaystones.standing(level, hall) && VillageWaystones.stoneOf(level, hall).equals(Optional.of(old)),
				"the network: " + VillageWaystones.stoneOf(level, hall) + " standing " + VillageWaystones.standing(level, hall));
		} finally {
			cleanUp(helper);
		}
		helper.succeed();
	}

	/**
	 * A village whose hall carries a blank name (only spaces: what commands can set, B1229) gives its stone the village's
	 * made-up name, read as words; a Name Tag's real name then reaches the stone, and the name made blank again gives the
	 * stone the made-up name back (never the old name, never blank). The Map's own line names it the same way.
	 */
	//$ gametest_batch AREA '"qaWaystoneCross1115d3Blank"'
	@GameTest(template = AREA, batch = "qaWaystoneCross1115d3Blank")
	public void aBlankNamedVillageStoneCarriesItsMadeUpNameAndTakesARename(GameTestHelper helper) {
		RaidHelperCompatTests.clearAround(helper);
		VillageHalls.forgetNearest();
		cleanUp(helper);
		ServerLevel level = helper.getLevel();
		BlockPos hallPos = helper.absolutePos(HALL_A);
		BlockPos stone = WaystoneQaCompatTests.stone(helper, PLAZA_A);
		try {
			VillageHallBlockEntity hall = WaystoneQaCompatTests.hall(helper, HALL_A, null, UUID.randomUUID());
			hall.setCustomName(Component.literal("   "));
			WaystoneQaCompatTests.plaza(helper, PLAZA_A);
			VillageWaystones.round(level, hallPos);
			String made = VillageHalls.madeUpName(hallPos).getString();
			helper.assertTrue(!made.isBlank() && !made.contains("aliveworkplace"), "the made-up name reads '" + made + "'");
			helper.assertTrue(nameAt(level, stone).equals(Optional.of(made)), "a blank-named village's stone is " + nameAt(level, stone) + ", not " + made);
			ServerPlayer player = helper.makeMockServerPlayerInLevel();
			List<String> lines = WaystoneQaCompatTests.texts(WaystoneQaCompatTests.mapLore(player, hallPos), LINE);
			helper.assertTrue(lines.equals(List.of("Waystone network (1):", made + ", this village: not discovered yet")), "the Map: " + lines);

			hall.setCustomName(Component.literal("Riverholm"));
			helper.assertTrue(nameAt(level, stone).equals(Optional.of("Riverholm")), "after a rename from the blank name the stone is " + nameAt(level, stone));
			hall.setCustomName(Component.literal(" \t "));
			helper.assertTrue(nameAt(level, stone).equals(Optional.of(made)), "after the name was made blank again the stone is " + nameAt(level, stone) + ", not " + made);
			helper.assertTrue(registeredAt(level, stone).size() == 1, "the mod lists at the stone: " + describe(registeredAt(level, stone)));
		} finally {
			cleanUp(helper);
			level.removeBlock(hallPos, false);
		}
		helper.succeed();
	}
}
