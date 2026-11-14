package io.github.jcondedata.aliveworkplace.test;

import static io.github.jcondedata.aliveworkplace.test.StationsSpecGameTests.listeningPlayer;

import io.github.jcondedata.aliveworkplace.hall.HallBeds;
import io.github.jcondedata.aliveworkplace.hall.HallJobs;
import io.github.jcondedata.aliveworkplace.hall.VillageHallScreen;
import io.github.jcondedata.aliveworkplace.hall.VillageHalls;
import io.github.jcondedata.aliveworkplace.hall.VillageNeeds;
import io.github.jcondedata.aliveworkplace.inn.InnCounters;
import io.github.jcondedata.aliveworkplace.inn.Innkeepers;
import io.github.jcondedata.aliveworkplace.move.MoveResidents;
import io.github.jcondedata.aliveworkplace.move.MoveSite;
import io.github.jcondedata.aliveworkplace.move.MoveSites;
import io.github.jcondedata.aliveworkplace.move.PendingMoves;
import io.github.jcondedata.aliveworkplace.registry.ModBlocks;
import io.github.jcondedata.aliveworkplace.registry.ModVillagers;
import io.github.jcondedata.aliveworkplace.work.ChoiceMenu;
import io.github.jcondedata.aliveworkplace.work.Jobs;
import io.github.jcondedata.aliveworkplace.work.Retraining;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.village.poi.PoiTypes;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.npc.VillagerProfession;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Rotation;

/**
 * QA verification (lane C, 2026-11-11) of four fixes, written from the bugs' text: B1194 (a pick made at the Village
 * Hall while a house is on the move stands), B1201 (a resident who never comes back doesn't hold the moved bed or
 * workstation for good), B1203 and B1205 (a Shop Counter inn stays the inn while its Innkeeper is away). The bugs' own
 * tests cover the single happy path each; these cover the edges: another job instead of No job, a save and reload mid
 * move, two residents of whom only one is given another bed, a dead worker's workstation, an unloaded resident waited
 * for exactly {@link MoveResidents#KEEP_TICKS} and no longer, one who comes back in time, an Innkeeper in an unloaded
 * chunk (and the remembered counters saved and loaded), one retrained or killed while away, and the village's edge for a
 * remembered counter.
 */
public class QaMoveInnVerify1111cGameTests implements FabricGameTest {
	static final String AREA = "aliveworkplace_test:huge_area";
	private static final BlockPos HALL = new BlockPos(15, 2, 2);
	private static final BlockPos STONECUTTER = new BlockPos(19, 2, 8);
	private static final BlockPos AWAY = new BlockPos(15, 2, 27); // 25 blocks from the Hall

	// --- helpers

	private static int free(ServerLevel level, BlockPos pos) {
		return level.getPoiManager().getFreeTickets(pos);
	}

	private static boolean waitedFor(ServerLevel level, Villager v) {
		return MoveSites.get(level).homecomings().stream().anyMatch(r -> r.id().equals(v.getUUID()));
	}

	private static String job(Villager v) {
		return v.getBrain().getMemory(MemoryModuleType.JOB_SITE).map(g -> g.pos().toShortString()).orElse("none");
	}

	/** A villager going into a chunk that unloads (saved as the chunk saves him); {@link #back} loads him again. */
	static CompoundTag unload(Villager v) {
		CompoundTag tag = new CompoundTag();
		v.save(tag);
		v.remove(Entity.RemovalReason.UNLOADED_TO_CHUNK);
		return tag;
	}

	static Villager back(ServerLevel level, CompoundTag tag) {
		Villager v = (Villager) EntityType.create(tag, level).orElseThrow();
		level.addFreshEntity(v);
		return v;
	}

	/** Bed ▾ on {@code villager}'s People row, then the free line for the bed at {@code bed} (found by the Hall's words for its place). */
	private static void pickBed(GameTestHelper helper, ServerPlayer player, Villager villager, BlockPos bed) {
		ChoiceMenu[] menu = new ChoiceMenu[1];
		int slot = QaMoveHallBed1111cGameTests.openPeople(helper, player, menu, villager);
		helper.assertTrue(menu[0].clickMenuButton(player, HallBeds.OPEN + slot), "Bed ▾ wasn't taken");
		List<ItemStack> lines = HallBeds.lines(menu[0].icon(slot));
		String where = VillageHallScreen.where(helper.absolutePos(HALL), helper.absolutePos(bed)).getString();
		List<ItemStack> ours = new ArrayList<>();
		for (ItemStack line : lines) {
			if (HallBeds.State.of(HallBeds.mark(line).getString("state")) == HallBeds.State.FREE && line.getHoverName().getString().endsWith(where)) {
				ours.add(line);
			}
		}
		helper.assertTrue(ours.size() == 1, "Bed ▾ for " + villager.getName().getString() + " has " + ours.size() + " free lines for the bed " + where + ": "
			+ lines.stream().map(l -> l.getHoverName().getString()).toList());
		helper.assertTrue(menu[0].clickMenuButton(player, HallBeds.PICK + HallBeds.mark(ours.get(0)).getInt("id")), "the bed pick wasn't taken");
	}

	// --- B1194: the player's choice at the Hall stands

	/**
	 * Juniper, the house's Librarian, is retrained as a Mason at the Hall (Job ▾, Mason, paid) while his lectern is packed:
	 * when the Mover puts the lectern up he stays a Mason at his Stonecutter, and the moved lectern is free for anyone.
	 */
	//$ gametest_batch AREA '"qaVerifyRetrainMidMove"'
	@GameTest(template = AREA, batch = "qaVerifyRetrainMidMove")
	public void aWorkerRetrainedAtTheHallWhileHisLecternIsAwayKeepsTheNewJob(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		QaMoveHallBed1111cGameTests.Setup s = QaMoveHallBed1111cGameTests.setup(helper, "retrain");
		helper.setBlock(STONECUTTER, Blocks.STONECUTTER);
		helper.runAfterDelay(2, () -> {
			PendingMoves.get(level).put(new PendingMoves.Move(s.hall(), s.from(), s.id(), s.to(), Rotation.CLOCKWISE_90, null));
			Villager juniper = helper.spawn(EntityType.VILLAGER, new BlockPos(10, 2, 6));
			juniper.setNoAi(true);
			juniper.setCustomName(Component.literal("Juniper"));
			Jobs.employ(level, juniper, helper.absolutePos(QaMoveHallBed1111cGameTests.LECTERN), PoiTypes.LIBRARIAN, VillagerProfession.LIBRARIAN);
			juniper.setVillagerXp(5);
			MoveSite site = QaMoveHallBed1111cGameTests.work(level, s, x -> x.stage() == MoveSite.Stage.FOUNDATION);
			helper.assertTrue(site.stage() == MoveSite.Stage.FOUNDATION, "the house wasn't taken down: " + site.stage());
			List<String> seen = new ArrayList<>();
			ServerPlayer player = listeningPlayer(helper, seen);
			player.getInventory().setItem(9, new ItemStack(Items.EMERALD, 64));
			ChoiceMenu[] menu = new ChoiceMenu[1];
			int slot = QaMoveHallBed1111cGameTests.openPeople(helper, player, menu, juniper);
			helper.assertTrue(menu[0].clickMenuButton(player, HallJobs.OPEN + slot), "Job ▾ wasn't taken");
			int mason = HallJobs.PICK + BuiltInRegistries.VILLAGER_PROFESSION.getId(VillagerProfession.MASON);
			helper.assertTrue(menu[0].clickMenuButton(player, mason) && menu[0].clickMenuButton(player, mason), "Mason wasn't taken");
			helper.assertTrue(juniper.getVillagerData().getProfession() == VillagerProfession.MASON,
				"the Hall didn't retrain Juniper as a Mason mid-move: " + juniper.getVillagerData().getProfession() + "; told " + seen);
			String stonecutter = job(juniper);
			QaMoveHallBed1111cGameTests.work(level, s, x -> false);
			helper.assertTrue(site.stage() == MoveSite.Stage.DONE, "the move ended " + site.stage());
			BlockPos lectern = s.before().at(QaMoveHallBed1111cGameTests.LECTERN.subtract(QaMoveHallBed1111cGameTests.MIN), s.to(), Rotation.CLOCKWISE_90);
			helper.assertTrue(level.getBlockState(lectern).is(Blocks.LECTERN), "the lectern isn't standing at " + lectern.toShortString());
			helper.assertTrue(juniper.getVillagerData().getProfession() == VillagerProfession.MASON && job(juniper).equals(stonecutter),
				"the Mover undid the Hall's retraining: Juniper is a " + juniper.getVillagerData().getProfession() + " working at " + job(juniper)
					+ " (the player made him a Mason at " + stonecutter + ")");
			helper.assertTrue(free(level, lectern) == 1, "the moved lectern is held for Juniper, who works a Stonecutter now: " + free(level, lectern) + " places free");
			helper.assertTrue(!waitedFor(level, juniper), "Juniper is left waited for as a resident");
			helper.succeed();
		});
	}

	/**
	 * The player gives Tomas the bed in the open at the Hall while his house is packed; the world is saved and loaded (the
	 * move's record read back) before the Mover puts the house up. The player's pick still stands after the reload.
	 */
	//$ gametest_batch AREA '"qaVerifyPickReload"'
	@GameTest(template = AREA, batch = "qaVerifyPickReload")
	public void aHallBedPickSurvivesASaveAndReloadMidMove(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		QaMoveHallBed1111cGameTests.Setup s = QaMoveHallBed1111cGameTests.setup(helper, "reload");
		helper.runAfterDelay(2, () -> {
			Villager tomas = QaMoveHallBed1111cGameTests.tomas(helper, s);
			MoveSite site = QaMoveHallBed1111cGameTests.work(level, s, x -> x.stage() == MoveSite.Stage.FOUNDATION);
			helper.assertTrue(site.stage() == MoveSite.Stage.FOUNDATION, "the house wasn't taken down: " + site.stage());
			QaMoveHallBed1111cGameTests.pickTheFreeBed(helper, listeningPlayer(helper, new ArrayList<>()), tomas);
			BlockPos chosen = helper.absolutePos(QaMoveHallBed1111cGameTests.OTHER_BED);
			helper.assertTrue(chosen.equals(QaMoveHallBed1111cGameTests.home(tomas)), "the Hall's pick didn't give Tomas the bed in the open");
			CompoundTag moving = MoveSites.get(level).save(new CompoundTag(), level.registryAccess());
			CompoundTag pending = PendingMoves.get(level).save(new CompoundTag(), level.registryAccess());
			level.getDataStorage().set(MoveSites.NAME, MoveSites.load(moving, level.registryAccess()));
			level.getDataStorage().set(PendingMoves.NAME, PendingMoves.load(pending, level.registryAccess()));
			MoveSite loaded = QaMoveHallBed1111cGameTests.work(level, s, x -> false);
			helper.assertTrue(loaded.stage() == MoveSite.Stage.DONE, "after the reload the move ended " + loaded.stage());
			BlockPos moved = s.before().at(QaMoveHallBed1111cGameTests.BED.subtract(QaMoveHallBed1111cGameTests.MIN), s.to(), Rotation.CLOCKWISE_90);
			helper.assertTrue(level.getBlockState(moved).getBlock() instanceof BedBlock, "the moved bed isn't standing");
			helper.assertTrue(chosen.equals(QaMoveHallBed1111cGameTests.home(tomas)),
				"after a save and reload the Mover undid the player's Hall pick: Tomas sleeps at "
					+ QaMoveHallBed1111cGameTests.at(QaMoveHallBed1111cGameTests.home(tomas)) + ", not the bed the player gave him at " + chosen.toShortString());
			helper.assertTrue(chosen.equals(VillageNeeds.bed(level, tomas)), "the People page shows Tomas's bed as " + VillageNeeds.bed(level, tomas));
			helper.assertTrue(free(level, chosen) == 0, "the bed the player gave Tomas is free for anyone after the reload");
			helper.assertTrue(free(level, moved) == 1, "after the reload the moved bed nobody sleeps in is held: " + free(level, moved) + " places free");
			helper.succeed();
		});
	}

	/**
	 * A two-bed house: Tomas and Rosa sleep in it. While it's packed the player gives Tomas a bed out in the open at the
	 * Hall, Rosa nothing. When it goes up Rosa sleeps in her moved bed (45.4), Tomas keeps the player's, and Tomas's moved
	 * bed is free: nobody's bed is held twice and nobody is pulled back.
	 */
	//$ gametest_batch AREA '"qaVerifyTwoResidents"'
	@GameTest(template = AREA, batch = "qaVerifyTwoResidents")
	public void onlyTheResidentGivenAnotherBedAtTheHallLeavesHisMovedBed(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		QaMoverResidents1110cGameTests.Setup s = QaMoverResidents1110cGameTests.setup(helper, "qaverify2", true);
		helper.setBlock(HALL, ModBlocks.VILLAGE_HALL);
		helper.runAfterDelay(2, () -> {
			Villager tomas = QaMoverResidents1110cGameTests.villager(helper, new BlockPos(10, 2, 4), "Tomas");
			Villager rosa = QaMoverResidents1110cGameTests.villager(helper, new BlockPos(10, 2, 6), "Rosa");
			helper.assertTrue(HallBeds.moveIn(level, tomas, helper.absolutePos(QaMoverResidents1110cGameTests.BED_A)), "setup: Tomas didn't get his bed");
			helper.assertTrue(HallBeds.moveIn(level, rosa, helper.absolutePos(QaMoverResidents1110cGameTests.BED_B)), "setup: Rosa didn't get her bed");
			QaMoverResidents1110cGameTests.confirm(helper, s);
			MoveSite site = QaMoverResidents1110cGameTests.start(level, s);
			QaMoverResidents1110cGameTests.work(level, s, site, x -> x.stage() == MoveSite.Stage.FOUNDATION);
			helper.assertTrue(site.stage() == MoveSite.Stage.FOUNDATION, "the house wasn't taken down: " + site.stage());
			pickBed(helper, listeningPlayer(helper, new ArrayList<>()), tomas, QaMoverResidents1110cGameTests.OUT_BED);
			BlockPos chosen = helper.absolutePos(QaMoverResidents1110cGameTests.OUT_BED);
			helper.assertTrue(chosen.equals(QaMoveHallBed1111cGameTests.home(tomas)), "the Hall's pick didn't give Tomas the bed in the open");
			QaMoverResidents1110cGameTests.work(level, s, site, x -> false);
			helper.assertTrue(site.stage() == MoveSite.Stage.DONE, "the move ended " + site.stage());
			BlockPos movedA = QaMoverResidents1110cGameTests.moved(s, QaMoverResidents1110cGameTests.BED_A);
			BlockPos movedB = QaMoverResidents1110cGameTests.moved(s, QaMoverResidents1110cGameTests.BED_B);
			helper.assertTrue(movedB.equals(QaMoveHallBed1111cGameTests.home(rosa)),
				"Rosa, given nothing at the Hall, doesn't sleep in her moved bed at " + movedB.toShortString() + " but at " + QaMoveHallBed1111cGameTests.at(QaMoveHallBed1111cGameTests.home(rosa)));
			helper.assertTrue(free(level, movedB) == 0, "Rosa's moved bed is free for anyone");
			helper.assertTrue(chosen.equals(QaMoveHallBed1111cGameTests.home(tomas)), "the Mover pulled Tomas back: he sleeps at " + QaMoveHallBed1111cGameTests.at(QaMoveHallBed1111cGameTests.home(tomas)));
			helper.assertTrue(free(level, chosen) == 0, "the bed the player gave Tomas is free for anyone");
			helper.assertTrue(free(level, movedA) == 1, "Tomas's moved bed, his no more, is held: " + free(level, movedA) + " places free");
			helper.assertTrue(!waitedFor(level, tomas) && !waitedFor(level, rosa), "a resident at home is left waited for");
			helper.assertTrue(free(level, helper.absolutePos(QaMoverResidents1110cGameTests.INN_BED_1)) == 1
				&& free(level, helper.absolutePos(QaMoverResidents1110cGameTests.INN_BED_2)) == 1, "an inn bed is still held after both are home");
			helper.succeed();
		});
	}

	// --- B1201: a resident who isn't coming back

	/** Juniper, the house's Librarian, dies while his lectern is packed: the moved lectern is free at once, not held for him. */
	//$ gametest_batch AREA '"qaVerifyWorkerDies"'
	@GameTest(template = AREA, batch = "qaVerifyWorkerDies")
	public void aWorkerWhoDiesMidMoveLetsGoOfHisMovedLectern(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		QaMoveHallBed1111cGameTests.Setup s = QaMoveHallBed1111cGameTests.setup(helper, "workerdies");
		helper.runAfterDelay(2, () -> {
			PendingMoves.get(level).put(new PendingMoves.Move(s.hall(), s.from(), s.id(), s.to(), Rotation.CLOCKWISE_90, null));
			Villager juniper = helper.spawn(EntityType.VILLAGER, new BlockPos(10, 2, 6));
			juniper.setNoAi(true);
			juniper.setCustomName(Component.literal("Juniper"));
			Jobs.employ(level, juniper, helper.absolutePos(QaMoveHallBed1111cGameTests.LECTERN), PoiTypes.LIBRARIAN, VillagerProfession.LIBRARIAN);
			juniper.setVillagerXp(5);
			MoveSite site = QaMoveHallBed1111cGameTests.work(level, s, x -> x.stage() == MoveSite.Stage.FOUNDATION);
			helper.assertTrue(site.residents().stream().anyMatch(r -> r.id().equals(juniper.getUUID())), "Juniper wasn't noted as a resident");
			juniper.kill(); // a zombie got him while his lectern was packed
			juniper.remove(Entity.RemovalReason.KILLED);
			QaMoveHallBed1111cGameTests.work(level, s, x -> false);
			helper.assertTrue(site.stage() == MoveSite.Stage.DONE, "the move ended " + site.stage());
			BlockPos lectern = s.before().at(QaMoveHallBed1111cGameTests.LECTERN.subtract(QaMoveHallBed1111cGameTests.MIN), s.to(), Rotation.CLOCKWISE_90);
			helper.assertTrue(level.getBlockState(lectern).is(Blocks.LECTERN), "the lectern isn't standing");
			helper.assertTrue(free(level, lectern) == 1, "the moved lectern is held for the dead Juniper: " + free(level, lectern) + " places free");
			helper.assertTrue(!waitedFor(level, juniper), "the dead Juniper is waited for as a homecoming");
			helper.succeed();
		});
	}

	/**
	 * Tomas is in an unloaded chunk when his moved bed goes up: it's held for him for exactly five days (KEEP_TICKS) and let
	 * go the tick after, and then another villager can really move into it.
	 */
	//$ gametest_batch AREA '"qaVerifyKeepEdge"'
	@GameTest(template = AREA, batch = "qaVerifyKeepEdge")
	public void anUnloadedResidentsBedIsHeldFiveDaysThenFreeForSomeoneElse(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		QaMoveHallBed1111cGameTests.Setup s = QaMoveHallBed1111cGameTests.setup(helper, "keepedge");
		helper.runAfterDelay(2, () -> {
			Villager tomas = QaMoveHallBed1111cGameTests.tomas(helper, s);
			MoveSite site = QaMoveHallBed1111cGameTests.work(level, s, x -> x.stage() == MoveSite.Stage.FOUNDATION);
			helper.assertTrue(site.stage() == MoveSite.Stage.FOUNDATION, "the house wasn't taken down: " + site.stage());
			unload(tomas);
			QaMoveHallBed1111cGameTests.work(level, s, x -> false);
			long now = level.getGameTime();
			helper.assertTrue(site.stage() == MoveSite.Stage.DONE, "the move ended " + site.stage());
			BlockPos moved = s.before().at(QaMoveHallBed1111cGameTests.BED.subtract(QaMoveHallBed1111cGameTests.MIN), s.to(), Rotation.CLOCKWISE_90);
			helper.assertTrue(free(level, moved) == 0 && waitedFor(level, tomas), "the moved bed isn't held for Tomas, away in an unloaded chunk: "
				+ free(level, moved) + " places free, waited for " + waitedFor(level, tomas));
			MoveSites.get(level).sweep(level, now + MoveResidents.KEEP_TICKS);
			helper.assertTrue(free(level, moved) == 0 && waitedFor(level, tomas),
				"Tomas's moved bed was let go before five days were up: " + free(level, moved) + " places free, waited for " + waitedFor(level, tomas));
			MoveSites.get(level).sweep(level, now + MoveResidents.KEEP_TICKS + 1);
			helper.assertTrue(free(level, moved) == 1 && !waitedFor(level, tomas),
				"five days and a tick after the move Tomas's moved bed is still held for him: " + free(level, moved) + " places free, waited for " + waitedFor(level, tomas));
			Villager rosa = QaMoverResidents1110cGameTests.villager(helper, new BlockPos(20, 2, 20), "Rosa");
			helper.assertTrue(HallBeds.moveIn(level, rosa, moved), "Rosa can't move into the bed let go");
			helper.assertTrue(moved.equals(QaMoveHallBed1111cGameTests.home(rosa)) && free(level, moved) == 0, "Rosa didn't get the bed let go");
			helper.succeed();
		});
	}

	/**
	 * Tomas is in an unloaded chunk when his moved bed goes up and comes back on the last tick of the five days: he gets his
	 * moved bed (the inn's bed let go), and a later sweep doesn't take it from him.
	 */
	//$ gametest_batch AREA '"qaVerifyKeepBack"'
	@GameTest(template = AREA, batch = "qaVerifyKeepBack")
	public void anUnloadedResidentWhoComesBackInTimeGetsHisMovedBed(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		QaMoveHallBed1111cGameTests.Setup s = QaMoveHallBed1111cGameTests.setup(helper, "keepback");
		helper.runAfterDelay(2, () -> {
			Villager tomas = QaMoveHallBed1111cGameTests.tomas(helper, s);
			QaMoveHallBed1111cGameTests.work(level, s, x -> x.stage() == MoveSite.Stage.FOUNDATION);
			CompoundTag saved = unload(tomas);
			MoveSite site = QaMoveHallBed1111cGameTests.work(level, s, x -> false);
			long now = level.getGameTime();
			helper.assertTrue(site.stage() == MoveSite.Stage.DONE, "the move ended " + site.stage());
			BlockPos moved = s.before().at(QaMoveHallBed1111cGameTests.BED.subtract(QaMoveHallBed1111cGameTests.MIN), s.to(), Rotation.CLOCKWISE_90);
			MoveSites.get(level).sweep(level, now + MoveResidents.KEEP_TICKS);
			Villager back = back(level, saved);
			back.tickCount = 13; // his once-a-second look (VillagerMixin -> MoveResidents.tick)
			MoveResidents.tick(back);
			helper.assertTrue(moved.equals(QaMoveHallBed1111cGameTests.home(back)),
				"Tomas came back within five days but sleeps at " + QaMoveHallBed1111cGameTests.at(QaMoveHallBed1111cGameTests.home(back)) + ", not his moved bed " + moved.toShortString());
			helper.assertTrue(!waitedFor(level, back), "Tomas is home but still waited for");
			helper.assertTrue(free(level, helper.absolutePos(QaMoveHallBed1111cGameTests.INN_BED)) == 1, "the inn's bed wasn't given back when Tomas came home");
			MoveSites.get(level).sweep(level, now + MoveResidents.KEEP_TICKS + 24000);
			helper.assertTrue(moved.equals(QaMoveHallBed1111cGameTests.home(back)) && free(level, moved) == 0,
				"a later sweep took Tomas's moved bed from him: he sleeps at " + QaMoveHallBed1111cGameTests.at(QaMoveHallBed1111cGameTests.home(back)) + ", " + free(level, moved) + " places free");
			helper.succeed();
		});
	}

	// --- B1203, B1205: the Shop Counter inn while its Innkeeper is away

	/** A Shop Counter inn (the move's setup, no Inn Counter), the village's edge at 12 (the counter 10 from the Hall), the remembered counter forgotten after. */
	static QaMoverResidents1110cGameTests.Setup innVillage(GameTestHelper helper, String name) {
		QaMoverResidents1110cGameTests.Setup s = QaInnShopCounter1111cGameTests.shopInn(helper, name);
		QaInnCounters1111dGameTests.radius(helper, 12);
		BlockPos counter = helper.absolutePos(QaInnShopCounter1111cGameTests.INN);
		Leftovers.after(helper, () -> InnCounters.get(helper.getLevel()).forget(counter));
		return s;
	}

	static Villager seenInnkeeper(GameTestHelper helper, QaMoverResidents1110cGameTests.Setup s) {
		InnShopCounterB1177GameTests.noOtherInns(helper.getLevel(), s.hall());
		Villager keeper = InnShopCounterB1177GameTests.counter(helper, ModVillagers.INNKEEPER, "Ina");
		helper.assertTrue(QaInnShopCounter1111cGameTests.innRolls(helper), "setup: the Innkeeper's Shop Counter isn't the inn with him by it");
		return keeper;
	}

	static void sendAway(GameTestHelper helper, Villager v) {
		BlockPos away = helper.absolutePos(AWAY);
		v.teleportTo(away.getX() + 0.5, away.getY(), away.getZ() + 0.5);
	}

	/**
	 * The Innkeeper walks out to a chunk that unloads, and the world is saved and loaded meanwhile: his Shop Counter is
	 * still the inn (a legend rolls there, a move's residents stay there), as the counter remembered was saved.
	 */
	//$ gametest_batch AREA '"qaVerifyInnUnloaded"'
	@GameTest(template = AREA, batch = "qaVerifyInnUnloaded")
	public void anInnkeeperInAnUnloadedChunkKeepsHisCounterTheInnAcrossAReload(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		QaMoverResidents1110cGameTests.Setup s = innVillage(helper, "qaverifyunl");
		helper.runAfterDelay(2, () -> {
			Villager keeper = seenInnkeeper(helper, s);
			sendAway(helper, keeper);
			unload(keeper);
			CompoundTag saved = InnCounters.get(level).save(new CompoundTag(), level.registryAccess());
			level.getDataStorage().set(InnCounters.NAME, InnCounters.load(saved, level.registryAccess()));
			BlockPos counter = helper.absolutePos(QaInnShopCounter1111cGameTests.INN);
			helper.assertTrue(keeper.getUUID().equals(InnCounters.get(level).keeper(counter)), "the reload forgot who works the inn's Shop Counter");
			helper.assertTrue(Innkeepers.counters(level, s.hall(), VillageHalls.RADIUS).contains(counter),
				"with the Innkeeper in an unloaded chunk (after a reload) his Shop Counter is no inn: counters " + Innkeepers.counters(level, s.hall(), VillageHalls.RADIUS));
			helper.assertTrue(QaInnShopCounter1111cGameTests.innRolls(helper), "no legend can come to the inn while the Innkeeper is in an unloaded chunk");
			Optional<BlockPos> bed = QaInnShopCounter1111cGameTests.stayBed(helper, s);
			helper.assertTrue(QaInnShopCounter1111cGameTests.atTheInn(helper, bed), "a move's residents should stay at the inn while the Innkeeper is unloaded, got " + bed);
			helper.succeed();
		});
	}

	/** The Innkeeper, out past the village's edge, is given No job (as the Hall does): his Shop Counter is no inn any more. */
	//$ gametest_batch AREA '"qaVerifyInnLetGo"'
	@GameTest(template = AREA, batch = "qaVerifyInnLetGo")
	public void anInnkeeperLetGoWhileAwayLeavesNoInn(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		QaMoverResidents1110cGameTests.Setup s = innVillage(helper, "qaverifyletgo");
		helper.runAfterDelay(2, () -> {
			Villager keeper = seenInnkeeper(helper, s);
			sendAway(helper, keeper);
			helper.assertTrue(QaInnShopCounter1111cGameTests.innRolls(helper), "setup: the inn isn't the inn with its Innkeeper away (B1205)");
			Retraining.unemploy(level, listeningPlayer(helper, new ArrayList<>()), keeper);
			helper.assertFalse(QaInnShopCounter1111cGameTests.innRolls(helper), "the Innkeeper has no job any more, yet a legend still rolls at his old Shop Counter");
			helper.assertTrue(Innkeepers.counters(level, s.hall(), VillageHalls.RADIUS).isEmpty(), "a counter nobody keeps as an inn is the inn: "
				+ Innkeepers.counters(level, s.hall(), VillageHalls.RADIUS));
			Optional<BlockPos> bed = QaInnShopCounter1111cGameTests.stayBed(helper, s);
			helper.assertTrue(QaInnShopCounter1111cGameTests.byTheHall(helper, bed), "with no Innkeeper the residents should stay by the Hall, got " + bed);
			helper.succeed();
		});
	}

	/** The Innkeeper dies out past the village's edge: his Shop Counter is no inn any more. */
	//$ gametest_batch AREA '"qaVerifyInnDies"'
	@GameTest(template = AREA, batch = "qaVerifyInnDies")
	public void anInnkeeperKilledWhileAwayLeavesNoInn(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		QaMoverResidents1110cGameTests.Setup s = innVillage(helper, "qaverifydies");
		helper.runAfterDelay(2, () -> {
			Villager keeper = seenInnkeeper(helper, s);
			sendAway(helper, keeper);
			keeper.kill();
			keeper.remove(Entity.RemovalReason.KILLED);
			helper.assertFalse(QaInnShopCounter1111cGameTests.innRolls(helper), "the Innkeeper is dead, yet a legend still rolls at his Shop Counter");
			helper.assertTrue(InnCounters.get(level).keeper(helper.absolutePos(QaInnShopCounter1111cGameTests.INN)) == null, "the dead Innkeeper is still remembered at his counter");
			helper.succeed();
		});
	}

	/** The village's edge for an inn whose Innkeeper is away: the counter 10.05 blocks from the Hall is the inn of a village of radius 11, not of 10. */
	//$ gametest_batch AREA '"qaVerifyInnAwayEdge"'
	@GameTest(template = AREA, batch = "qaVerifyInnAwayEdge")
	public void anAwayInnkeepersCounterIsTheInnOnlyInsideTheVillagesEdge(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		QaMoverResidents1110cGameTests.Setup s = innVillage(helper, "qaverifyedge");
		helper.runAfterDelay(2, () -> {
			Villager keeper = seenInnkeeper(helper, s);
			sendAway(helper, keeper);
			VillageHalls.RADIUS = 11;
			helper.assertTrue(QaInnShopCounter1111cGameTests.innRolls(helper), "the inn 10.05 blocks from the Hall, its Innkeeper away, isn't the inn of a village of radius 11");
			VillageHalls.RADIUS = 10;
			helper.assertFalse(QaInnShopCounter1111cGameTests.innRolls(helper), "the inn 10.05 blocks from the Hall, its Innkeeper away, counts in a village of radius 10");
			helper.assertTrue(Innkeepers.counters(level, s.hall(), VillageHalls.RADIUS).isEmpty(), "a counter past the edge was found: " + Innkeepers.counters(level, s.hall(), VillageHalls.RADIUS));
			VillageHalls.RADIUS = 12;
			helper.assertTrue(QaInnShopCounter1111cGameTests.innRolls(helper), "the inn wasn't the inn again once the village grew back to 12");
			keeper.remove(Entity.RemovalReason.DISCARDED);
			helper.succeed();
		});
	}
}
