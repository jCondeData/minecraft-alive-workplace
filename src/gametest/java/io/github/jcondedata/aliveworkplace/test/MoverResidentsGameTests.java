package io.github.jcondedata.aliveworkplace.test;

import io.github.jcondedata.aliveworkplace.blueprint.BlueprintData;
import io.github.jcondedata.aliveworkplace.build.BuildSite;
import io.github.jcondedata.aliveworkplace.build.BuildSiteManager;
import io.github.jcondedata.aliveworkplace.build.Builders;
import io.github.jcondedata.aliveworkplace.hall.HallBeds;
import io.github.jcondedata.aliveworkplace.hall.VillageNeeds;
import io.github.jcondedata.aliveworkplace.move.MovePlan;
import io.github.jcondedata.aliveworkplace.move.MoveResidents;
import io.github.jcondedata.aliveworkplace.move.MoveSite;
import io.github.jcondedata.aliveworkplace.move.MoveSites;
import io.github.jcondedata.aliveworkplace.move.MoverWork;
import io.github.jcondedata.aliveworkplace.move.PendingMoves;
import io.github.jcondedata.aliveworkplace.people.Couples;
import io.github.jcondedata.aliveworkplace.people.Homes;
import io.github.jcondedata.aliveworkplace.people.Households;
import io.github.jcondedata.aliveworkplace.registry.ModAttachments;
import io.github.jcondedata.aliveworkplace.registry.ModBlocks;
import io.github.jcondedata.aliveworkplace.registry.ModVillagers;
import io.github.jcondedata.aliveworkplace.work.Jobs;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
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
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.village.poi.PoiTypes;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.npc.VillagerProfession;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BedPart;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;

/**
 * 45.4: the residents move too. A small house the hall knows, with a married couple's two beds, a Librarian's lectern
 * and a Builder's Bench, is moved by a Mover a quarter turn to a new spot. While it's away the couple sleep at the
 * village's inn (two free beds round an Inn Counter); then each bed, workstation and bench is the new block's: their
 * home and job-site memories, the beds' and blocks' one place held for them, their home the moved building (the Hall's
 * People page and census read these), the couple still a married household, the inn's beds free again. The worker keeps
 * his job while his lectern is packed even when vanilla forgets his job site; the builder's bench stays his, and his
 * queued build's bench goes with it. Through a save and reload every few steps, and with the move called off half way
 * (everyone back at the old spot).
 */
public class MoverResidentsGameTests implements FabricGameTest {
	private static final String AREA = "aliveworkplace_test:huge_area";
	/** The house (helper coordinates): 7 × 5 × 7, lowest corner MIN. */
	private static final BlockPos MIN = new BlockPos(2, 2, 2);
	private static final BlockPos MAX = new BlockPos(8, 6, 8);
	/** Where it goes (helper coordinates), its new lowest corner, turned a quarter. */
	private static final BlockPos TO = new BlockPos(16, 2, 16);
	private static final BlockPos DOOR = new BlockPos(5, 3, 2);
	private static final BlockPos BED_A = new BlockPos(3, 3, 7);
	private static final BlockPos BED_B = new BlockPos(5, 3, 7);
	private static final BlockPos LECTERN = new BlockPos(7, 3, 4);
	private static final BlockPos BENCH = new BlockPos(3, 3, 3);
	private static final BlockPos HALL = new BlockPos(15, 2, 2);
	/** The inn: its counter and two free beds by it (heads), well away from both spots. */
	private static final BlockPos INN = new BlockPos(25, 2, 3);
	private static final BlockPos INN_BED_1 = new BlockPos(24, 2, 6);
	private static final BlockPos INN_BED_2 = new BlockPos(27, 2, 6);
	private static final int MAX_STEPS = 5000;

	private static void set(ServerLevel level, BlockPos abs, BlockState state) {
		level.setBlock(abs, state, Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE);
	}

	/** A bed with its head at {@code head} (helper coordinates), its foot to the north. */
	private static void bed(GameTestHelper helper, BlockPos head) {
		BlockState bed = Blocks.RED_BED.defaultBlockState().setValue(BedBlock.FACING, Direction.SOUTH);
		set(helper.getLevel(), helper.absolutePos(head.north()), bed.setValue(BedBlock.PART, BedPart.FOOT));
		set(helper.getLevel(), helper.absolutePos(head), bed.setValue(BedBlock.PART, BedPart.HEAD));
	}

	private record Setup(MovePlan before, BlockPos hall, BlockPos from, BlockPos to, Villager mover, ResourceLocation id) {
	}

	/** The people of the house, given their beds and blocks once the level has filed them. */
	private record People(Villager husband, Villager wife, Villager librarian, Villager builder, BuildSite queued) {
	}

	private static Setup setup(GameTestHelper helper, String name) {
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
		BlockState door = Blocks.OAK_DOOR.defaultBlockState().setValue(DoorBlock.FACING, Direction.NORTH);
		set(level, helper.absolutePos(DOOR), door.setValue(DoorBlock.HALF, DoubleBlockHalf.LOWER));
		set(level, helper.absolutePos(DOOR.above()), door.setValue(DoorBlock.HALF, DoubleBlockHalf.UPPER));
		bed(helper, BED_A);
		bed(helper, BED_B);
		set(level, helper.absolutePos(LECTERN), Blocks.LECTERN.defaultBlockState());
		set(level, helper.absolutePos(BENCH), ModBlocks.BUILDERS_BENCH.defaultBlockState());
		// the inn, away from both spots
		set(level, helper.absolutePos(INN), ModBlocks.INN_COUNTER.defaultBlockState());
		bed(helper, INN_BED_1);
		bed(helper, INN_BED_2);

		ResourceLocation id = ResourceLocation.fromNamespaceAndPath("aliveworkplace_test", "residents_" + name + "_" + Long.toHexString(level.getGameTime()));
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
		return new Setup(before, hall, from, to, mover(helper, new BlockPos(12, 2, 12)), id);
	}

	/** The move confirmed on the Move page; the house's people given their beds, lectern and bench (their blocks filed by now). */
	private static People people(GameTestHelper helper, Setup s) {
		ServerLevel level = helper.getLevel();
		PendingMoves.get(level).put(new PendingMoves.Move(s.hall(), s.from(), s.id(), s.to(), Rotation.CLOCKWISE_90, null));
		Villager husband = resident(helper, new BlockPos(10, 2, 3), "Tomas");
		Villager wife = resident(helper, new BlockPos(10, 2, 5), "Dara");
		helper.assertTrue(HallBeds.moveIn(level, husband, helper.absolutePos(BED_A)) && HallBeds.moveIn(level, wife, helper.absolutePos(BED_B)),
			"setup: the couple didn't get their beds");
		ModAttachments.PARTNER.set(husband, new Couples.Partner(wife.getUUID(), wife.getDisplayName(), 0L, true));
		ModAttachments.PARTNER.set(wife, new Couples.Partner(husband.getUUID(), husband.getDisplayName(), 0L, true));
		Villager librarian = resident(helper, new BlockPos(10, 2, 7), "Juniper");
		Jobs.employ(level, librarian, helper.absolutePos(LECTERN), PoiTypes.LIBRARIAN, VillagerProfession.LIBRARIAN);
		librarian.setVillagerXp(0); // a Novice who took the lectern by himself: vanilla would take his job with it gone
		Villager builder = resident(helper, new BlockPos(10, 2, 9), "Bram");
		Builders.employ(level, builder, helper.absolutePos(BENCH));
		// a build queued at his bench, far off: its bench goes where his goes
		BuildSite queued = BuildSiteManager.get(level).create(UUID.randomUUID(), "test", s.id(),
			new BlueprintData.Placement(level.dimension().location(), helper.absolutePos(new BlockPos(2, 2, 20)), Rotation.NONE, Mirror.NONE));
		queued.setBench(helper.absolutePos(BENCH));
		return new People(husband, wife, librarian, builder, queued);
	}

	private static Villager resident(GameTestHelper helper, BlockPos at, String name) {
		Villager villager = helper.spawn(EntityType.VILLAGER, at);
		villager.setNoAi(true);
		villager.setCustomName(net.minecraft.network.chat.Component.literal(name));
		return villager;
	}

	/** A Mover at {@code at}, hired at his own Mover's Crate just north of him. */
	private static Villager mover(GameTestHelper helper, BlockPos at) {
		helper.setBlock(at.north(), ModBlocks.MOVERS_CRATE);
		Villager villager = helper.spawn(EntityType.VILLAGER, at);
		villager.setNoAi(true);
		villager.setVillagerData(villager.getVillagerData().setProfession(ModVillagers.MOVER));
		villager.getBrain().setMemory(MemoryModuleType.JOB_SITE, GlobalPos.of(helper.getLevel().dimension(), helper.absolutePos(at.north())));
		return villager;
	}

	private static MoveSite work(ServerLevel level, Setup s, java.util.function.Predicate<MoveSite> stop) {
		MoveSite site = MoveSites.get(level).work(level, s.hall()).orElseThrow();
		for (int i = 0; i < MAX_STEPS && site.stage() != MoveSite.Stage.DONE && !stop.test(site); i++) {
			MoverWork.work(level, s.mover(), site);
		}
		return site;
	}

	private static MoveSite reload(ServerLevel level, Setup s) {
		CompoundTag saved = MoveSites.get(level).save(new CompoundTag(), level.registryAccess());
		level.getDataStorage().set(MoveSites.NAME, MoveSites.load(saved, level.registryAccess()));
		return MoveSites.get(level).forBuilding(s.from()).orElseThrow();
	}

	/** Where a block of the house (helper coordinates) stands after the move. */
	private static BlockPos moved(GameTestHelper helper, Setup s, BlockPos helperPos) {
		return s.before().at(helperPos.subtract(MIN), s.to(), Rotation.CLOCKWISE_90);
	}

	private static String memory(Villager villager, MemoryModuleType<GlobalPos> memory) {
		return villager.getBrain().getMemory(memory).map(g -> g.pos().toShortString()).orElse("none");
	}

	private static boolean remembers(ServerLevel level, Villager villager, MemoryModuleType<GlobalPos> memory, BlockPos pos) {
		return villager.getBrain().getMemory(memory).filter(g -> g.equals(GlobalPos.of(level.dimension(), pos))).isPresent();
	}

	/** {@code villager}'s bed is {@code bed}: remembered, held for them, counted by the census, in the moved building. */
	private static void assertBed(GameTestHelper helper, Setup s, Villager villager, BlockPos bed, String who) {
		ServerLevel level = helper.getLevel();
		helper.assertTrue(remembers(level, villager, MemoryModuleType.HOME, bed), who + "'s home is " + memory(villager, MemoryModuleType.HOME) + ", not " + bed.toShortString());
		helper.assertTrue(level.getBlockState(bed).getBlock() instanceof BedBlock, who + "'s bed isn't standing at " + bed.toShortString());
		helper.assertTrue(level.getPoiManager().getFreeTickets(bed) == 0, who + "'s bed isn't held for them: " + level.getPoiManager().getFreeTickets(bed) + " places free");
		helper.assertTrue(bed.equals(VillageNeeds.bed(level, villager)), "the Hall's People page doesn't see " + who + "'s bed: " + VillageNeeds.bed(level, villager));
		Optional<Homes.Home> home = Homes.of(level, villager);
		helper.assertTrue(home.isPresent() && home.get().structure().equals(s.id()), who + "'s home isn't the moved house: " + home);
	}

	/** Everything after a whole move: beds, lectern and bench the new spot's, the couple a household, the inn's beds free. */
	private static void assertAtHome(GameTestHelper helper, Setup s, People p) {
		ServerLevel level = helper.getLevel();
		assertBed(helper, s, p.husband(), moved(helper, s, BED_A), "Tomas");
		assertBed(helper, s, p.wife(), moved(helper, s, BED_B), "Dara");
		Couples.Partner partner = Couples.partner(p.husband());
		helper.assertTrue(partner != null && partner.married() && partner.id().equals(p.wife().getUUID()), "the couple isn't married any more: " + partner);
		helper.assertTrue(Households.of(List.of(p.husband(), p.wife())).size() == 1, "the couple aren't one household");
		for (BlockPos inn : List.of(INN_BED_1, INN_BED_2)) {
			helper.assertTrue(level.getPoiManager().getFreeTickets(helper.absolutePos(inn)) == 1, "the inn's bed at " + inn + " wasn't given back");
		}
		BlockPos lectern = moved(helper, s, LECTERN);
		helper.assertTrue(remembers(level, p.librarian(), MemoryModuleType.JOB_SITE, lectern),
			"the librarian's job site is " + memory(p.librarian(), MemoryModuleType.JOB_SITE) + ", not the moved lectern " + lectern.toShortString());
		helper.assertTrue(p.librarian().getVillagerData().getProfession() == VillagerProfession.LIBRARIAN, "the librarian lost his job: " + p.librarian().getVillagerData().getProfession());
		helper.assertTrue(level.getPoiManager().getFreeTickets(lectern) == 0, "the moved lectern isn't held for the librarian");
		BlockPos bench = moved(helper, s, BENCH);
		helper.assertTrue(Builders.benchPos(p.builder()).filter(bench::equals).isPresent(),
			"the builder's bench is " + Builders.benchPos(p.builder()) + ", not the moved one at " + bench.toShortString());
		helper.assertTrue(p.builder().getVillagerData().getProfession() == ModVillagers.BUILDER, "the builder lost his job");
		helper.assertTrue(level.getBlockState(bench).is(ModBlocks.BUILDERS_BENCH) && level.getPoiManager().getFreeTickets(bench) == 0,
			"the moved bench isn't held for the builder");
		helper.assertTrue(bench.equals(p.queued().bench()), "his queued build's bench is " + p.queued().bench() + ", not the moved one");
	}

	/** A family's beds: at the inn while the house is away, then the new house's two beds, still a married household. */
	//$ gametest_batch AREA '"moverResidentsFamily"'
	@GameTest(template = AREA, batch = "moverResidentsFamily")
	public void aFamilySleepsAtTheInnAndMovesInWithTheirBeds(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		Setup s = setup(helper, "family");
		helper.runAfterDelay(2, () -> {
			People p = people(helper, s);
			MoveSite site = MoveSites.get(level).work(level, s.hall()).orElseThrow();
			helper.assertTrue(site.residents().size() == 4, "residents noted: " + site.residents().size() + " (the couple, the librarian, the builder)");
			List<BlockPos> inn = List.of(helper.absolutePos(INN_BED_1), helper.absolutePos(INN_BED_2));
			BlockPos his = p.husband().getBrain().getMemory(MemoryModuleType.HOME).map(GlobalPos::pos).orElse(null);
			BlockPos hers = p.wife().getBrain().getMemory(MemoryModuleType.HOME).map(GlobalPos::pos).orElse(null);
			helper.assertTrue(his != null && hers != null && inn.contains(his) && inn.contains(hers) && !his.equals(hers), "while the house is away the couple sleep at " + his + " and " + hers
				+ ", not the inn's beds " + inn);
			helper.assertTrue(level.getPoiManager().getFreeTickets(helper.absolutePos(BED_A)) == 1, "Tomas's old bed wasn't let go");
			work(level, s, x -> x.stage() == MoveSite.Stage.FOUNDATION);
			helper.assertTrue(remembers(level, p.husband(), MemoryModuleType.HOME, his), "with the house packed Tomas sleeps at " + memory(p.husband(), MemoryModuleType.HOME));
			work(level, s, x -> false);
			helper.assertTrue(site.stage() == MoveSite.Stage.DONE, "the move ended " + site.stage());
			assertAtHome(helper, s, p);
			// none of this house's people is left waited for (the level's list also keeps other tests' residents, whose
			// villagers were discarded with their test areas: counting those made this fail whenever it ran after them)
			List<UUID> ours = List.of(p.husband().getUUID(), p.wife().getUUID(), p.librarian().getUUID(), p.builder().getUUID());
			long waiting = MoveSites.get(level).homecomings().stream().filter(r -> ours.contains(r.id())).count();
			helper.assertTrue(waiting == 0, "residents left waiting: " + waiting);
			helper.succeed();
		});
	}

	/**
	 * A worker's workstation: the librarian (a Novice with no experience) keeps his job while his lectern is packed, even
	 * when vanilla forgets his job site; the moved lectern is held for him the moment it's put up, and is his.
	 */
	//$ gametest_batch AREA '"moverResidentsWorker"'
	@GameTest(template = AREA, batch = "moverResidentsWorker")
	public void aWorkerKeepsHisWorkstationThroughTheMove(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		Setup s = setup(helper, "worker");
		helper.runAfterDelay(2, () -> {
			People p = people(helper, s);
			BlockPos lectern = moved(helper, s, LECTERN);
			MoveSite site = work(level, s, x -> x.stage() == MoveSite.Stage.FOUNDATION);
			helper.assertTrue(p.librarian().getVillagerXp() >= 1, "a Novice with no experience would lose his job with his lectern packed");
			p.librarian().getBrain().eraseMemory(MemoryModuleType.JOB_SITE); // as vanilla does near a job site that's gone
			work(level, s, x -> level.getBlockState(lectern).is(Blocks.LECTERN));
			helper.assertTrue(level.getBlockState(lectern).is(Blocks.LECTERN), "the lectern wasn't put up: " + site.stage());
			helper.assertTrue(level.getPoiManager().getFreeTickets(lectern) == 0, "the lectern was free to anyone when it was put up");
			helper.assertTrue(remembers(level, p.librarian(), MemoryModuleType.JOB_SITE, lectern), "the lectern isn't his at once: " + memory(p.librarian(), MemoryModuleType.JOB_SITE));
			work(level, s, x -> false);
			assertAtHome(helper, s, p);
			helper.succeed();
		});
	}

	/** A builder's bench inside a moved house: his before and after, his queued build's bench moved with it. */
	//$ gametest_batch AREA '"moverResidentsBench"'
	@GameTest(template = AREA, batch = "moverResidentsBench")
	public void aBuildersBenchInAMovedHouseStaysHis(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		Setup s = setup(helper, "bench");
		helper.runAfterDelay(2, () -> {
			People p = people(helper, s);
			BlockPos bench = moved(helper, s, BENCH);
			MoveSite site = work(level, s, x -> level.getBlockState(bench).is(ModBlocks.BUILDERS_BENCH));
			helper.assertTrue(level.getBlockState(bench).is(ModBlocks.BUILDERS_BENCH), "the bench wasn't put up: " + site.stage());
			helper.assertTrue(level.getPoiManager().getFreeTickets(bench) == 0, "the bench was free to any jobless builder when it was put up");
			helper.assertTrue(Builders.benchPos(p.builder()).filter(bench::equals).isPresent(), "the bench isn't his at once: " + Builders.benchPos(p.builder()));
			work(level, s, x -> false);
			assertAtHome(helper, s, p);
			helper.succeed();
		});
	}

	/** Saved and reloaded every few steps: who the residents are and where they sleep meanwhile survive, and all end up home. */
	//$ gametest_batch AREA '"moverResidentsReload"'
	@GameTest(template = AREA, batch = "moverResidentsReload")
	public void theResidentsFollowThroughASaveAndReload(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		Setup s = setup(helper, "reload");
		helper.runAfterDelay(2, () -> {
			People p = people(helper, s);
			MoveSite site = MoveSites.get(level).work(level, s.hall()).orElseThrow();
			BlockPos stay = p.husband().getBrain().getMemory(MemoryModuleType.HOME).map(GlobalPos::pos).orElseThrow();
			int steps = 0;
			java.util.Set<MoveSite.Stage> reloadedIn = java.util.EnumSet.noneOf(MoveSite.Stage.class);
			while (site.stage() != MoveSite.Stage.DONE && steps++ < MAX_STEPS) {
				MoverWork.work(level, s.mover(), site);
				if (steps % 7 == 0 && site.stage() != MoveSite.Stage.DONE) {
					reloadedIn.add(site.stage());
					site = reload(level, s);
					helper.assertTrue(site.residents().size() == 4, "after a reload in " + site.stage() + " the site knows " + site.residents().size() + " residents");
					if (site.stage() == MoveSite.Stage.DISMANTLE) {
						MoveResidents.Resident his = site.residents().stream().filter(r -> r.id().equals(p.husband().getUUID())).findFirst().orElseThrow();
						helper.assertTrue(stay.equals(his.stay()), "after a reload Tomas's bed at the inn is " + his.stay() + ", not " + stay);
					}
				}
			}
			helper.assertTrue(reloadedIn.containsAll(List.of(MoveSite.Stage.DISMANTLE, MoveSite.Stage.FOUNDATION, MoveSite.Stage.STRUCTURE,
				MoveSite.Stage.DECORATION)), "reloaded only in " + reloadedIn);
			assertAtHome(helper, s, p);
			helper.succeed();
		});
	}

	/** Called off half way through putting it up: everyone's bed, lectern and bench are the old spot's again. */
	//$ gametest_batch AREA '"moverResidentsCancel"'
	@GameTest(template = AREA, batch = "moverResidentsCancel")
	public void aMoveCalledOffPutsTheResidentsBackHome(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		Setup s = setup(helper, "cancel");
		helper.runAfterDelay(2, () -> {
			People p = people(helper, s);
			MoveSite site = work(level, s, x -> x.stage() == MoveSite.Stage.STRUCTURE);
			helper.assertTrue(site.stage() == MoveSite.Stage.STRUCTURE, "it didn't get half way: " + site.stage());
			PendingMoves.get(level).remove(s.from()); // the Move page's Call off
			work(level, s, x -> false);
			helper.assertTrue(site.canceled() && site.stage() == MoveSite.Stage.DONE, "the move ended " + site.stage());
			BlockPos bedA = helper.absolutePos(BED_A);
			helper.assertTrue(remembers(level, p.husband(), MemoryModuleType.HOME, bedA) && level.getPoiManager().getFreeTickets(bedA) == 0,
				"Tomas isn't back in his old bed: " + memory(p.husband(), MemoryModuleType.HOME));
			helper.assertTrue(remembers(level, p.wife(), MemoryModuleType.HOME, helper.absolutePos(BED_B)), "Dara isn't back in her old bed: " + memory(p.wife(), MemoryModuleType.HOME));
			helper.assertTrue(remembers(level, p.librarian(), MemoryModuleType.JOB_SITE, helper.absolutePos(LECTERN)),
				"the librarian isn't back at his old lectern: " + memory(p.librarian(), MemoryModuleType.JOB_SITE));
			helper.assertTrue(Builders.benchPos(p.builder()).filter(helper.absolutePos(BENCH)::equals).isPresent(), "the builder isn't back at his old bench");
			helper.assertTrue(helper.absolutePos(BENCH).equals(p.queued().bench()), "his queued build's bench is " + p.queued().bench());
			for (BlockPos inn : List.of(INN_BED_1, INN_BED_2)) {
				helper.assertTrue(level.getPoiManager().getFreeTickets(helper.absolutePos(inn)) == 1, "the inn's bed at " + inn + " wasn't given back");
			}
			helper.succeed();
		});
	}
}
