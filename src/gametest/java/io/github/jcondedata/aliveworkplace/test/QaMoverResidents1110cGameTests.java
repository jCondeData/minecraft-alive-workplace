package io.github.jcondedata.aliveworkplace.test;

import io.github.jcondedata.aliveworkplace.blueprint.BlueprintData;
import io.github.jcondedata.aliveworkplace.build.BuildSite;
import io.github.jcondedata.aliveworkplace.build.BuildSiteManager;
import io.github.jcondedata.aliveworkplace.hall.HallBeds;
import io.github.jcondedata.aliveworkplace.move.MovePlan;
import io.github.jcondedata.aliveworkplace.move.MoveSite;
import io.github.jcondedata.aliveworkplace.move.MoveSites;
import io.github.jcondedata.aliveworkplace.move.MoverWork;
import io.github.jcondedata.aliveworkplace.move.PendingMoves;
import io.github.jcondedata.aliveworkplace.registry.ModBlocks;
import io.github.jcondedata.aliveworkplace.registry.ModVillagers;
import io.github.jcondedata.aliveworkplace.work.Jobs;
import java.util.UUID;
import java.util.function.Predicate;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.Vec3i;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.village.poi.PoiTypes;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.npc.VillagerProfession;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BedPart;

/**
 * QA (lane c, 2026-11-10) for 45.4 "The residents move too", written from the spec: villagers who live or work in the
 * moved building keep it, and nobody else is touched. Edges the builder's tests leave out: a neighbor living elsewhere,
 * a resident asleep in the house when the move starts, a worker who sleeps elsewhere, a village with no inn (they sleep
 * by the Hall), a save and reload while the house is away, and a resident far off when the move starts.
 */
public class QaMoverResidents1110cGameTests implements FabricGameTest {
	static final String AREA = "aliveworkplace_test:huge_area";
	static final BlockPos MIN = new BlockPos(2, 2, 2);
	static final BlockPos MAX = new BlockPos(8, 6, 8);
	static final BlockPos TO = new BlockPos(16, 2, 16);
	static final BlockPos BED_A = new BlockPos(3, 3, 7);
	static final BlockPos BED_B = new BlockPos(5, 3, 7);
	static final BlockPos LECTERN = new BlockPos(7, 3, 4);
	static final BlockPos HALL = new BlockPos(15, 2, 2);
	static final BlockPos INN = new BlockPos(25, 2, 3);
	static final BlockPos INN_BED_1 = new BlockPos(24, 2, 6);
	static final BlockPos INN_BED_2 = new BlockPos(27, 2, 6);
	/** A bed and a lectern outside the house, for the neighbor and the commuter. */
	static final BlockPos OUT_BED = new BlockPos(12, 2, 26);
	static final BlockPos OUT_LECTERN = new BlockPos(14, 2, 26);
	/** A bed by the Hall, the fallback when the village has no inn. */
	static final BlockPos HALL_BED = new BlockPos(17, 2, 6);
	static final int MAX_STEPS = 5000;

	record Setup(MovePlan before, BlockPos hall, BlockPos from, BlockPos to, Villager mover, ResourceLocation id) {
	}

	static void set(ServerLevel level, BlockPos abs, BlockState state) {
		level.setBlock(abs, state, Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE);
	}

	static void bed(GameTestHelper helper, BlockPos head) {
		BlockState bed = Blocks.RED_BED.defaultBlockState().setValue(BedBlock.FACING, Direction.SOUTH);
		set(helper.getLevel(), helper.absolutePos(head.north()), bed.setValue(BedBlock.PART, BedPart.FOOT));
		set(helper.getLevel(), helper.absolutePos(head), bed.setValue(BedBlock.PART, BedPart.HEAD));
	}

	/** A walled 7 × 5 × 7 house with two beds and a lectern, the hall's record of it, an inn (unless {@code inn} is false). */
	static Setup setup(GameTestHelper helper, String name, boolean inn) {
		Leftovers.clear(helper);
		ServerLevel level = helper.getLevel();
		for (int x = MIN.getX(); x <= MAX.getX(); x++) {
			for (int z = MIN.getZ(); z <= MAX.getZ(); z++) {
				for (int y = MIN.getY(); y <= MAX.getY(); y++) {
					boolean edge = x == MIN.getX() || x == MAX.getX() || z == MIN.getZ() || z == MAX.getZ();
					BlockState state = y == MIN.getY() ? Blocks.COBBLESTONE.defaultBlockState()
						: y == MAX.getY() ? Blocks.SPRUCE_PLANKS.defaultBlockState()
						: edge ? Blocks.OAK_PLANKS.defaultBlockState() : Blocks.AIR.defaultBlockState();
					set(level, helper.absolutePos(new BlockPos(x, y, z)), state);
				}
			}
		}
		bed(helper, BED_A);
		bed(helper, BED_B);
		set(level, helper.absolutePos(LECTERN), Blocks.LECTERN.defaultBlockState());
		bed(helper, OUT_BED);
		set(level, helper.absolutePos(OUT_LECTERN), Blocks.LECTERN.defaultBlockState());
		if (inn) {
			set(level, helper.absolutePos(INN), ModBlocks.INN_COUNTER.defaultBlockState());
			bed(helper, INN_BED_1);
			bed(helper, INN_BED_2);
		} else {
			bed(helper, HALL_BED);
		}
		ResourceLocation id = ResourceLocation.fromNamespaceAndPath("aliveworkplace_test", "qa_residents_" + name + "_" + Long.toHexString(level.getGameTime()));
		level.getStructureManager().getOrCreate(id).fillFromWorld(level, helper.absolutePos(MIN), new Vec3i(7, 5, 7), false, Blocks.STRUCTURE_VOID);
		BlueprintData.Placement placement = new BlueprintData.Placement(level.dimension().location(), helper.absolutePos(MIN), Rotation.NONE, Mirror.NONE);
		BuildSiteManager sites = BuildSiteManager.get(level);
		sites.recordFinished(id, placement, UUID.randomUUID());
		MovePlan before = MovePlan.capture(level, sites.finishedAt(level, id, helper.absolutePos(MIN)).orElseThrow()).orElseThrow();
		BlockPos hall = helper.absolutePos(HALL);
		BlockPos from = helper.absolutePos(MIN);
		BlockPos to = helper.absolutePos(TO);
		Leftovers.after(helper, () -> {
			MoveSites.get(level).cancel(level, from);
			MoveSites.get(level).all().stream().filter(s -> s.from().equals(from)).forEach(s -> {
				while (s.stage() != MoveSite.Stage.DONE) {
					MoveSites.get(level).step(level, s);
				}
			});
			PendingMoves.get(level).remove(from);
			sites.forgetFinished(placement);
			sites.finishedIn(level).stream().filter(f -> f.structure().equals(id)).forEach(f -> sites.forgetFinished(f.placement()));
			sites.all().stream().filter(b -> b.structure().equals(id)).map(BuildSite::id).toList().forEach(sites::remove);
		});
		BlockPos crate = new BlockPos(12, 2, 11);
		helper.setBlock(crate, ModBlocks.MOVERS_CRATE);
		Villager mover = helper.spawn(EntityType.VILLAGER, crate.south());
		mover.setNoAi(true);
		mover.setVillagerData(mover.getVillagerData().setProfession(ModVillagers.MOVER));
		mover.getBrain().setMemory(MemoryModuleType.JOB_SITE, GlobalPos.of(level.dimension(), helper.absolutePos(crate)));
		return new Setup(before, hall, from, to, mover, id);
	}

	static void confirm(GameTestHelper helper, Setup s) {
		PendingMoves.get(helper.getLevel()).put(new PendingMoves.Move(s.hall(), s.from(), s.id(), s.to(), Rotation.CLOCKWISE_90, null));
	}

	static Villager villager(GameTestHelper helper, BlockPos at, String name) {
		Villager villager = helper.spawn(EntityType.VILLAGER, at);
		villager.setNoAi(true);
		villager.setCustomName(net.minecraft.network.chat.Component.literal(name));
		return villager;
	}

	static MoveSite start(ServerLevel level, Setup s) {
		return MoveSites.get(level).work(level, s.hall()).orElseThrow();
	}

	static MoveSite work(ServerLevel level, Setup s, MoveSite site, Predicate<MoveSite> stop) {
		for (int i = 0; i < MAX_STEPS && site.stage() != MoveSite.Stage.DONE && !stop.test(site); i++) {
			MoverWork.work(level, s.mover(), site);
		}
		return site;
	}

	static BlockPos moved(Setup s, BlockPos helperPos) {
		return s.before().at(helperPos.subtract(MIN), s.to(), Rotation.CLOCKWISE_90);
	}

	static String memory(Villager villager, MemoryModuleType<GlobalPos> memory) {
		return villager.getBrain().getMemory(memory).map(g -> g.pos().toShortString()).orElse("none");
	}

	static boolean remembers(ServerLevel level, Villager villager, MemoryModuleType<GlobalPos> memory, BlockPos pos) {
		return villager.getBrain().getMemory(memory).filter(g -> g.equals(GlobalPos.of(level.dimension(), pos))).isPresent();
	}

	static boolean noted(MoveSite site, Villager villager) {
		return site.residents().stream().anyMatch(r -> r.id().equals(villager.getUUID()));
	}

	/** A neighbor whose bed and lectern are outside the house is not a resident: their bed and job stay as they were. */
	//$ gametest_batch AREA '"qaMoverResidentsNeighbor"'
	@GameTest(template = AREA, batch = "qaMoverResidentsNeighbor")
	public void aNeighborLivingElsewhereIsNotTouched(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		Setup s = setup(helper, "neighbor", true);
		helper.runAfterDelay(2, () -> {
			confirm(helper, s);
			Villager tenant = villager(helper, new BlockPos(10, 2, 3), "Tomas");
			helper.assertTrue(HallBeds.moveIn(level, tenant, helper.absolutePos(BED_A)), "setup: Tomas didn't get his bed");
			Villager neighbor = villager(helper, new BlockPos(10, 2, 24), "Nell");
			helper.assertTrue(HallBeds.moveIn(level, neighbor, helper.absolutePos(OUT_BED)), "setup: Nell didn't get her bed");
			Jobs.employ(level, neighbor, helper.absolutePos(OUT_LECTERN), PoiTypes.LIBRARIAN, VillagerProfession.LIBRARIAN);
			MoveSite site = start(level, s);
			helper.assertTrue(noted(site, tenant), "Tomas, whose bed is in the house, isn't noted as a resident");
			helper.assertFalse(noted(site, neighbor), "Nell, who lives and works elsewhere, was noted as a resident of the moved house");
			work(level, s, site, x -> false);
			helper.assertTrue(site.stage() == MoveSite.Stage.DONE, "the move ended " + site.stage());
			helper.assertTrue(remembers(level, neighbor, MemoryModuleType.HOME, helper.absolutePos(OUT_BED)), "Nell's bed moved to " + memory(neighbor, MemoryModuleType.HOME));
			helper.assertTrue(remembers(level, neighbor, MemoryModuleType.JOB_SITE, helper.absolutePos(OUT_LECTERN)), "Nell's lectern is now " + memory(neighbor, MemoryModuleType.JOB_SITE));
			helper.assertTrue(neighbor.getVillagerData().getProfession() == VillagerProfession.LIBRARIAN, "Nell lost her job");
			helper.assertTrue(level.getPoiManager().getFreeTickets(helper.absolutePos(OUT_BED)) == 0, "Nell's bed was let go");
			helper.assertTrue(remembers(level, tenant, MemoryModuleType.HOME, moved(s, BED_A)), "Tomas's bed is " + memory(tenant, MemoryModuleType.HOME) + ", not the moved one");
			helper.succeed();
		});
	}

	/** A resident asleep in the house when the move starts is woken, sleeps at the inn meanwhile, and gets the moved bed. */
	//$ gametest_batch AREA '"qaMoverResidentsSleeper"'
	@GameTest(template = AREA, batch = "qaMoverResidentsSleeper")
	public void aResidentAsleepInTheHouseWakesAndMovesIn(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		Setup s = setup(helper, "sleeper", true);
		helper.runAfterDelay(2, () -> {
			confirm(helper, s);
			Villager sleeper = villager(helper, new BlockPos(4, 3, 5), "Dara");
			helper.assertTrue(HallBeds.moveIn(level, sleeper, helper.absolutePos(BED_A)), "setup: Dara didn't get her bed");
			sleeper.startSleeping(helper.absolutePos(BED_A));
			helper.assertTrue(sleeper.isSleeping(), "setup: Dara isn't asleep");
			MoveSite site = start(level, s);
			helper.assertTrue(noted(site, sleeper), "Dara, asleep in the house, isn't noted as a resident");
			helper.assertFalse(sleeper.isSleeping() && sleeper.getSleepingPos().filter(helper.absolutePos(BED_A)::equals).isPresent()
				&& site.residents().stream().anyMatch(r -> r.stay() != null), "Dara was given a bed at the inn but is still asleep in her old one");
			BlockPos stay = sleeper.getBrain().getMemory(MemoryModuleType.HOME).map(GlobalPos::pos).orElse(null);
			helper.assertTrue(helper.absolutePos(INN_BED_1).equals(stay) || helper.absolutePos(INN_BED_2).equals(stay), "while the house is away Dara sleeps at " + stay);
			work(level, s, site, x -> false);
			helper.assertTrue(site.stage() == MoveSite.Stage.DONE, "the move ended " + site.stage());
			BlockPos bed = moved(s, BED_A);
			helper.assertTrue(remembers(level, sleeper, MemoryModuleType.HOME, bed), "Dara's bed is " + memory(sleeper, MemoryModuleType.HOME) + ", not the moved one " + bed.toShortString());
			helper.assertFalse(sleeper.isSleeping() && !sleeper.getSleepingPos().filter(bed::equals).isPresent(),
				"Dara is still 'asleep' at " + sleeper.getSleepingPos() + " where no bed stands");
			helper.assertTrue(level.getPoiManager().getFreeTickets(stay) == 1, "her bed at the inn wasn't given back");
			helper.succeed();
		});
	}

	/** A worker whose lectern is in the house but whose bed is elsewhere keeps his own bed (no inn bed) and the moved lectern. */
	//$ gametest_batch AREA '"qaMoverResidentsCommuter"'
	@GameTest(template = AREA, batch = "qaMoverResidentsCommuter")
	public void aWorkerWhoSleepsElsewhereKeepsHisBedAndGetsTheMovedLectern(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		Setup s = setup(helper, "commuter", true);
		helper.runAfterDelay(2, () -> {
			confirm(helper, s);
			Villager worker = villager(helper, new BlockPos(10, 2, 24), "Juniper");
			helper.assertTrue(HallBeds.moveIn(level, worker, helper.absolutePos(OUT_BED)), "setup: Juniper didn't get his bed");
			Jobs.employ(level, worker, helper.absolutePos(LECTERN), PoiTypes.LIBRARIAN, VillagerProfession.LIBRARIAN);
			MoveSite site = start(level, s);
			helper.assertTrue(noted(site, worker), "Juniper, whose lectern is in the house, isn't noted");
			helper.assertTrue(remembers(level, worker, MemoryModuleType.HOME, helper.absolutePos(OUT_BED)), "Juniper was moved out of his own bed to " + memory(worker, MemoryModuleType.HOME));
			for (BlockPos inn : new BlockPos[] {INN_BED_1, INN_BED_2}) {
				helper.assertTrue(level.getPoiManager().getFreeTickets(helper.absolutePos(inn)) == 1, "an inn bed was taken for a worker who has his own bed");
			}
			work(level, s, site, x -> false);
			helper.assertTrue(site.stage() == MoveSite.Stage.DONE, "the move ended " + site.stage());
			BlockPos lectern = moved(s, LECTERN);
			helper.assertTrue(remembers(level, worker, MemoryModuleType.JOB_SITE, lectern), "Juniper's job site is " + memory(worker, MemoryModuleType.JOB_SITE) + ", not " + lectern.toShortString());
			helper.assertTrue(worker.getVillagerData().getProfession() == VillagerProfession.LIBRARIAN, "Juniper lost his job");
			helper.assertTrue(remembers(level, worker, MemoryModuleType.HOME, helper.absolutePos(OUT_BED)), "Juniper's bed is now " + memory(worker, MemoryModuleType.HOME));
			helper.assertTrue(level.getPoiManager().getFreeTickets(helper.absolutePos(OUT_BED)) == 0, "Juniper's own bed was let go");
			helper.succeed();
		});
	}

	/** With no inn in the village the residents sleep in a free bed by the Hall meanwhile, then each gets their own moved bed. */
	//$ gametest_batch AREA '"qaMoverResidentsNoInn"'
	@GameTest(template = AREA, batch = "qaMoverResidentsNoInn")
	public void withNoInnTheyStayByTheHallAndEachGetsTheirOwnBed(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		Setup s = setup(helper, "noinn", false);
		helper.runAfterDelay(2, () -> {
			// other batches' test areas nearby may have left an inn standing: this village has none
			level.getPoiManager().findAll(h -> h.is(ModVillagers.INN_COUNTER_POI), p -> true, s.hall(), 80, net.minecraft.world.entity.ai.village.poi.PoiManager.Occupancy.ANY)
				.map(BlockPos::immutable).toList().forEach(p -> level.removeBlock(p, false));
			confirm(helper, s);
			Villager a = villager(helper, new BlockPos(10, 2, 3), "Tomas");
			Villager b = villager(helper, new BlockPos(10, 2, 5), "Dara");
			helper.assertTrue(HallBeds.moveIn(level, a, helper.absolutePos(BED_A)) && HallBeds.moveIn(level, b, helper.absolutePos(BED_B)), "setup: beds");
			MoveSite site = start(level, s);
			helper.assertTrue(noted(site, a) && noted(site, b), "both residents should be noted: " + site.residents().size());
			// one free bed by the Hall: one of them gets it, never both
			boolean aStays = remembers(level, a, MemoryModuleType.HOME, helper.absolutePos(HALL_BED));
			boolean bStays = remembers(level, b, MemoryModuleType.HOME, helper.absolutePos(HALL_BED));
			helper.assertTrue(aStays ^ bStays, "the one bed by the Hall " + helper.absolutePos(HALL_BED).toShortString() + ": Tomas sleeps at " + memory(a, MemoryModuleType.HOME)
				+ ", Dara at " + memory(b, MemoryModuleType.HOME) + "; the move found " + io.github.jcondedata.aliveworkplace.move.MoveResidents.stayBed(level, site) + "; old " + site.oldBox() + " new " + site.newBox());
			work(level, s, site, x -> false);
			helper.assertTrue(site.stage() == MoveSite.Stage.DONE, "the move ended " + site.stage());
			BlockPos bedA = moved(s, BED_A);
			BlockPos bedB = moved(s, BED_B);
			helper.assertTrue(remembers(level, a, MemoryModuleType.HOME, bedA), "Tomas's bed is " + memory(a, MemoryModuleType.HOME) + ", not " + bedA.toShortString());
			helper.assertTrue(remembers(level, b, MemoryModuleType.HOME, bedB), "Dara's bed is " + memory(b, MemoryModuleType.HOME) + ", not " + bedB.toShortString());
			helper.assertTrue(level.getPoiManager().getFreeTickets(helper.absolutePos(HALL_BED)) == 1, "the bed by the Hall wasn't given back");
			helper.succeed();
		});
	}

	/** Saved and reloaded while the house is down: the residents still get their own beds and the worker his lectern. */
	//$ gametest_batch AREA '"qaMoverResidentsReload"'
	@GameTest(template = AREA, batch = "qaMoverResidentsReload")
	public void aReloadWhileTheHouseIsAwayKeepsEachResidentsClaim(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		Setup s = setup(helper, "reload", true);
		helper.runAfterDelay(2, () -> {
			confirm(helper, s);
			Villager a = villager(helper, new BlockPos(10, 2, 3), "Tomas");
			Villager b = villager(helper, new BlockPos(10, 2, 5), "Dara");
			helper.assertTrue(HallBeds.moveIn(level, a, helper.absolutePos(BED_A)) && HallBeds.moveIn(level, b, helper.absolutePos(BED_B)), "setup: beds");
			Jobs.employ(level, a, helper.absolutePos(LECTERN), PoiTypes.LIBRARIAN, VillagerProfession.LIBRARIAN);
			MoveSite site = start(level, s);
			work(level, s, site, x -> x.stage() == MoveSite.Stage.FOUNDATION);
			helper.assertTrue(site.stage() == MoveSite.Stage.FOUNDATION, "the house didn't come down: " + site.stage());
			CompoundTag saved = MoveSites.get(level).save(new CompoundTag(), level.registryAccess());
			level.getDataStorage().set(MoveSites.NAME, MoveSites.load(saved, level.registryAccess()));
			site = MoveSites.get(level).forBuilding(s.from()).orElseThrow();
			helper.assertTrue(site.residents().size() == 2, "after the reload the site knows " + site.residents().size() + " residents");
			work(level, s, site, x -> false);
			helper.assertTrue(site.stage() == MoveSite.Stage.DONE, "the move ended " + site.stage());
			helper.assertTrue(remembers(level, a, MemoryModuleType.HOME, moved(s, BED_A)), "Tomas's bed is " + memory(a, MemoryModuleType.HOME));
			helper.assertTrue(remembers(level, b, MemoryModuleType.HOME, moved(s, BED_B)), "Dara's bed is " + memory(b, MemoryModuleType.HOME));
			helper.assertTrue(remembers(level, a, MemoryModuleType.JOB_SITE, moved(s, LECTERN)), "Tomas's lectern is " + memory(a, MemoryModuleType.JOB_SITE));
			helper.assertTrue(a.getVillagerData().getProfession() == VillagerProfession.LIBRARIAN, "Tomas lost his job");
			helper.succeed();
		});
	}

	/**
	 * A resident who is far off (out trading, over 128 blocks away) when the move starts still lives there: once the house
	 * stands at its new spot and they are back, their bed is the moved one, not the empty spot where it stood.
	 */
	//$ gametest_batch AREA '"qaMoverResidentsAway"'
	@GameTest(template = AREA, batch = "qaMoverResidentsAway")
	public void aResidentFarAwayDuringTheMoveStillGetsTheMovedBed(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		Setup s = setup(helper, "away", true);
		helper.runAfterDelay(2, () -> {
			confirm(helper, s);
			BlockPos far = helper.absolutePos(new BlockPos(10, 2, 3)).offset(200, 0, 0);
			ChunkPos chunk = new ChunkPos(far);
			level.setChunkForced(chunk.x, chunk.z, true);
			Villager away = EntityType.VILLAGER.spawn(level, far, MobSpawnType.COMMAND);
			try {
				helper.assertTrue(away != null, "setup: couldn't spawn the traveler");
				away.setNoAi(true);
				helper.assertTrue(HallBeds.moveIn(level, away, helper.absolutePos(BED_A)), "setup: the traveler didn't get her bed");
				MoveSite site = start(level, s);
				work(level, s, site, x -> false);
				helper.assertTrue(site.stage() == MoveSite.Stage.DONE, "the move ended " + site.stage());
				// back home: she walks in and her villager ticks run
				away.teleportTo(helper.absolutePos(new BlockPos(10, 2, 3)).getX() + 0.5, helper.absolutePos(new BlockPos(10, 2, 3)).getY(), helper.absolutePos(new BlockPos(10, 2, 3)).getZ() + 0.5);
				away.tickCount = 13;
				io.github.jcondedata.aliveworkplace.move.MoveResidents.tick(away);
				BlockPos bed = moved(s, BED_A);
				helper.assertTrue(remembers(level, away, MemoryModuleType.HOME, bed),
					"a resident 200 blocks off when the move started has her bed at " + memory(away, MemoryModuleType.HOME) + ", not the moved bed " + bed.toShortString());
			} finally {
				if (away != null) {
					away.discard();
				}
				level.setChunkForced(chunk.x, chunk.z, false);
			}
			helper.succeed();
		});
	}
}
