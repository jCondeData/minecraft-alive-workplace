package io.github.jcondedata.aliveworkplace.test;

import static io.github.jcondedata.aliveworkplace.test.StationsSpecGameTests.listeningPlayer;

import io.github.jcondedata.aliveworkplace.hall.HallBeds;
import io.github.jcondedata.aliveworkplace.hall.HallJobs;
import io.github.jcondedata.aliveworkplace.hall.HallPeople;
import io.github.jcondedata.aliveworkplace.hall.HallSections;
import io.github.jcondedata.aliveworkplace.hall.VillageHallScreen;
import io.github.jcondedata.aliveworkplace.hall.VillageNeeds;
import io.github.jcondedata.aliveworkplace.move.MoveSite;
import io.github.jcondedata.aliveworkplace.move.MoveSites;
import io.github.jcondedata.aliveworkplace.people.Homes;
import io.github.jcondedata.aliveworkplace.work.ChoiceMenu;
import io.github.jcondedata.aliveworkplace.work.Jobs;
import java.util.ArrayList;
import java.util.List;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.village.poi.PoiTypes;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.npc.VillagerProfession;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Rotation;

/**
 * QA (lane C, 2026-11-14): the Village Hall's People table (40.2-40.4) meeting a finished building moved with its
 * residents (45.4), through the hall's own clicks. What QaMoveHallBed1111c, QaMoverResidents1110c and the inn tests
 * leave out: the Bed ▾ list after the move (the moved bed his, named by the moved building), another villager's list
 * offering a swap with him on the moved bed (and the swap working), the list mid-move (his inn bed is no one else's free
 * bed), a swap at the inn mid-move surviving the house going up, the People row mid-move (the kept job, no "No bed"),
 * Job ▾ No job after the move freeing the moved lectern for a jobless neighbor's Job ▾, and a save and reload of the
 * move between the pick and the list. Built on QaMoveHallBed1111cGameTests' house, inn, bed in the open and hall.
 */
public class QaHallCross1114cGameTests implements FabricGameTest {
	private static final String AREA = QaMoveHallBed1111cGameTests.AREA;

	private static BlockPos moved(QaMoveHallBed1111cGameTests.Setup s, BlockPos helperPos) {
		return s.before().at(helperPos.subtract(QaMoveHallBed1111cGameTests.MIN), s.to(), Rotation.CLOCKWISE_90);
	}

	private static Villager lena(GameTestHelper helper) {
		Villager lena = helper.spawn(EntityType.VILLAGER, new BlockPos(10, 2, 8));
		lena.setNoAi(true);
		lena.setCustomName(Component.literal("Lena"));
		return lena;
	}

	/** The People row of {@code villager}, as the opened hall shows it. */
	private static CompoundTag row(GameTestHelper helper, ServerPlayer player, Villager villager) {
		ChoiceMenu[] menu = new ChoiceMenu[1];
		int slot = QaMoveHallBed1111cGameTests.openPeople(helper, player, menu, villager);
		return HallPeople.row(menu[0].icon(slot));
	}

	/** Bed ▾ on {@code villager}'s row; {@code out[0]} the menu. */
	private static List<ItemStack> beds(GameTestHelper helper, ServerPlayer player, ChoiceMenu[] out, Villager villager) {
		int slot = QaMoveHallBed1111cGameTests.openPeople(helper, player, out, villager);
		helper.assertTrue(out[0].clickMenuButton(player, HallBeds.OPEN + slot), "Bed ▾ wasn't taken");
		List<ItemStack> lines = HallBeds.lines(out[0].icon(slot));
		helper.assertTrue(!lines.isEmpty(), "Bed ▾ opened no list for " + villager.getName().getString());
		return lines;
	}

	private static HallBeds.State state(ItemStack line) {
		return HallBeds.State.of(HallBeds.mark(line).getString("state"));
	}

	private static String show(List<ItemStack> lines) {
		return lines.stream().map(l -> l.getHoverName().getString() + " (" + HallBeds.mark(l).getString("state") + ")").toList().toString();
	}

	/** The option (from the Hall's own list order) at {@code bed}, or null. */
	private static HallBeds.Option option(ServerLevel level, BlockPos hall, BlockPos bed) {
		return HallBeds.options(level, hall).stream().filter(o -> o.bed().equals(bed)).findFirst().orElse(null);
	}

	private static int free(ServerLevel level, BlockPos pos) {
		return level.getPoiManager().getFreeTickets(pos);
	}

	/**
	 * After the move, Tomas's Bed ▾ says he sleeps in the moved bed, named by the moved house's building name (the same
	 * name it had before the move), and the bed's old spot is no longer listed.
	 */
	//$ gametest_batch AREA '"qaHallCrossNamed"'
	@GameTest(template = AREA, batch = "qaHallCrossNamed")
	public void afterTheMoveBedListNamesTheMovedBedByItsBuilding(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		QaMoveHallBed1111cGameTests.Setup s = QaMoveHallBed1111cGameTests.setup(helper, "xnamed");
		helper.runAfterDelay(2, () -> {
			Villager tomas = QaMoveHallBed1111cGameTests.tomas(helper, s);
			BlockPos old = helper.absolutePos(QaMoveHallBed1111cGameTests.BED);
			Homes.Building before = Homes.building(level, old).orElse(null);
			helper.assertTrue(before != null, "setup: the house's bed isn't in a known building before the move");
			String name = before.home().name().getString();
			MoveSite site = QaMoveHallBed1111cGameTests.work(level, s, x -> false);
			helper.assertTrue(site.stage() == MoveSite.Stage.DONE, "the move ended " + site.stage());
			BlockPos bed = moved(s, QaMoveHallBed1111cGameTests.BED);
			helper.assertTrue(bed.equals(QaMoveHallBed1111cGameTests.home(tomas)), "Tomas sleeps at " + QaMoveHallBed1111cGameTests.at(QaMoveHallBed1111cGameTests.home(tomas)));
			ChoiceMenu[] menu = new ChoiceMenu[1];
			List<ItemStack> lines = beds(helper, listeningPlayer(helper, new ArrayList<>()), menu, tomas);
			List<ItemStack> current = lines.stream().filter(l -> state(l) == HallBeds.State.CURRENT).toList();
			helper.assertTrue(current.size() == 1, "Tomas's Bed ▾ has " + current.size() + " 'their bed now' lines: " + show(lines));
			helper.assertTrue(current.get(0).getHoverName().getString().contains(name),
				"the moved bed isn't named by its building '" + name + "' on Tomas's list: " + show(lines));
			HallBeds.Option o = option(level, s.hall(), bed);
			helper.assertTrue(o != null && o.inBuilding(), "the Hall lists the moved bed as " + (o == null ? "missing" : "outside any building"));
			helper.assertTrue(option(level, s.hall(), old) == null, "the Hall still lists a bed at the house's old spot " + old.toShortString());
			helper.succeed();
		});
	}

	/** After the move, Lena (bed in the open) sees "swap with Tomas" on the moved bed; clicking it swaps the two at once. */
	//$ gametest_batch AREA '"qaHallCrossSwapAfter"'
	@GameTest(template = AREA, batch = "qaHallCrossSwapAfter")
	public void afterTheMoveAnotherVillagerCanSwapIntoTheMovedBed(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		QaMoveHallBed1111cGameTests.Setup s = QaMoveHallBed1111cGameTests.setup(helper, "xswapafter");
		helper.runAfterDelay(2, () -> {
			Villager tomas = QaMoveHallBed1111cGameTests.tomas(helper, s);
			Villager lena = lena(helper);
			BlockPos open = helper.absolutePos(QaMoveHallBed1111cGameTests.OTHER_BED);
			helper.assertTrue(HallBeds.moveIn(level, lena, open), "setup: Lena didn't get the bed in the open");
			QaMoveHallBed1111cGameTests.work(level, s, x -> false);
			BlockPos bed = moved(s, QaMoveHallBed1111cGameTests.BED);
			helper.assertTrue(bed.equals(QaMoveHallBed1111cGameTests.home(tomas)), "Tomas sleeps at " + QaMoveHallBed1111cGameTests.at(QaMoveHallBed1111cGameTests.home(tomas)));
			ServerPlayer player = listeningPlayer(helper, new ArrayList<>());
			ChoiceMenu[] menu = new ChoiceMenu[1];
			List<ItemStack> lines = beds(helper, player, menu, lena);
			List<ItemStack> swaps = lines.stream().filter(l -> state(l) == HallBeds.State.SWAP && l.getHoverName().getString().contains("Tomas")).toList();
			helper.assertTrue(swaps.size() == 1, "Lena's Bed ▾ has " + swaps.size() + " 'swap with Tomas' lines after his house moved: " + show(lines));
			helper.assertTrue(lines.stream().noneMatch(l -> state(l) == HallBeds.State.FREE && l.getHoverName().getString().contains(Homes.building(level, bed).map(b -> b.home().name().getString()).orElse("\u0000"))),
				"the moved house's bed (Tomas's) is offered to Lena as free: " + show(lines));
			helper.assertTrue(menu[0].clickMenuButton(player, HallBeds.PICK + HallBeds.mark(swaps.get(0)).getInt("id")), "the swap wasn't taken");
			helper.assertTrue(bed.equals(QaMoveHallBed1111cGameTests.home(lena)) && bed.equals(VillageNeeds.bed(level, lena)),
				"after the swap Lena sleeps at " + QaMoveHallBed1111cGameTests.at(QaMoveHallBed1111cGameTests.home(lena)) + ", not the moved bed " + bed.toShortString());
			helper.assertTrue(open.equals(QaMoveHallBed1111cGameTests.home(tomas)) && open.equals(VillageNeeds.bed(level, tomas)),
				"after the swap Tomas sleeps at " + QaMoveHallBed1111cGameTests.at(QaMoveHallBed1111cGameTests.home(tomas)) + ", not Lena's old bed");
			helper.assertTrue(free(level, bed) == 0 && free(level, open) == 0, "a swapped bed is free for anyone: moved " + free(level, bed) + ", open " + free(level, open));
			helper.succeed();
		});
	}

	/** Mid-move, Tomas sleeps at the inn: Lena's Bed ▾ offers that bed only as "swap with Tomas", never as free. */
	//$ gametest_batch AREA '"qaHallCrossInnBedTaken"'
	@GameTest(template = AREA, batch = "qaHallCrossInnBedTaken")
	public void midMoveTheInnBedOfAResidentIsNoOneElsesFreeBed(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		QaMoveHallBed1111cGameTests.Setup s = QaMoveHallBed1111cGameTests.setup(helper, "xinntaken");
		helper.runAfterDelay(2, () -> {
			Villager tomas = QaMoveHallBed1111cGameTests.tomas(helper, s);
			Villager lena = lena(helper);
			helper.assertTrue(HallBeds.moveIn(level, lena, helper.absolutePos(QaMoveHallBed1111cGameTests.OTHER_BED)), "setup: Lena didn't get the bed in the open");
			MoveSite site = QaMoveHallBed1111cGameTests.work(level, s, x -> x.stage() == MoveSite.Stage.FOUNDATION);
			helper.assertTrue(site.stage() == MoveSite.Stage.FOUNDATION, "the house wasn't taken down: " + site.stage());
			BlockPos inn = helper.absolutePos(QaMoveHallBed1111cGameTests.INN_BED);
			helper.assertTrue(inn.equals(QaMoveHallBed1111cGameTests.home(tomas)), "with his house packed Tomas sleeps at " + QaMoveHallBed1111cGameTests.at(QaMoveHallBed1111cGameTests.home(tomas)));
			HallBeds.Option o = option(level, s.hall(), inn);
			helper.assertTrue(o != null && o.taken() && o.sleeper() == tomas, "the Hall lists Tomas's inn bed as " + (o == null ? "missing" : (o.taken() ? "taken by " + o.sleeper() : "free")));
			ChoiceMenu[] menu = new ChoiceMenu[1];
			List<ItemStack> lines = beds(helper, listeningPlayer(helper, new ArrayList<>()), menu, lena);
			// beds left by other tests' areas may be listed: Tomas's inn bed is found by the words the Hall gives its place
			String innWhere = VillageHallScreen.where(s.hall(), inn).getString();
			helper.assertTrue(lines.stream().noneMatch(l -> state(l) == HallBeds.State.FREE && l.getHoverName().getString().endsWith(innWhere)),
				"Lena's Bed ▾ offers Tomas's inn bed (" + innWhere + ") as free mid-move: " + show(lines));
			helper.assertTrue(lines.stream().filter(l -> state(l) == HallBeds.State.SWAP && l.getHoverName().getString().startsWith(innWhere) && l.getHoverName().getString().contains("Tomas")).count() == 1,
				"Lena's Bed ▾ doesn't offer a swap with Tomas for his inn bed (" + innWhere + "): " + show(lines));
			QaMoveHallBed1111cGameTests.work(level, s, x -> false);
			helper.succeed();
		});
	}

	/**
	 * Mid-move, the player swaps Lena (bed in the open) and Tomas (at the inn) at the Hall. When the house goes up, the
	 * Mover doesn't pull Lena out of the inn bed the player gave her, and Tomas and Lena never share a bed.
	 */
	//$ gametest_batch AREA '"qaHallCrossSwapMid"'
	@GameTest(template = AREA, batch = "qaHallCrossSwapMid")
	public void aSwapAtTheInnMidMoveSurvivesTheHouseGoingUp(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		QaMoveHallBed1111cGameTests.Setup s = QaMoveHallBed1111cGameTests.setup(helper, "xswapmid");
		helper.runAfterDelay(2, () -> {
			Villager tomas = QaMoveHallBed1111cGameTests.tomas(helper, s);
			Villager lena = lena(helper);
			BlockPos open = helper.absolutePos(QaMoveHallBed1111cGameTests.OTHER_BED);
			BlockPos inn = helper.absolutePos(QaMoveHallBed1111cGameTests.INN_BED);
			helper.assertTrue(HallBeds.moveIn(level, lena, open), "setup: Lena didn't get the bed in the open");
			QaMoveHallBed1111cGameTests.work(level, s, x -> x.stage() == MoveSite.Stage.FOUNDATION);
			ServerPlayer player = listeningPlayer(helper, new ArrayList<>());
			ChoiceMenu[] menu = new ChoiceMenu[1];
			List<ItemStack> lines = beds(helper, player, menu, lena);
			ItemStack swap = lines.stream().filter(l -> state(l) == HallBeds.State.SWAP && l.getHoverName().getString().contains("Tomas")).findFirst().orElse(null);
			helper.assertTrue(swap != null, "no swap with Tomas on Lena's list: " + show(lines));
			helper.assertTrue(menu[0].clickMenuButton(player, HallBeds.PICK + HallBeds.mark(swap).getInt("id")), "the swap wasn't taken");
			helper.assertTrue(inn.equals(QaMoveHallBed1111cGameTests.home(lena)), "the swap didn't give Lena the inn bed: " + QaMoveHallBed1111cGameTests.at(QaMoveHallBed1111cGameTests.home(lena)));
			MoveSite site = QaMoveHallBed1111cGameTests.work(level, s, x -> false);
			helper.assertTrue(site.stage() == MoveSite.Stage.DONE, "the move ended " + site.stage());
			BlockPos lenaBed = VillageNeeds.bed(level, lena);
			BlockPos tomasBed = VillageNeeds.bed(level, tomas);
			helper.assertTrue(inn.equals(lenaBed), "the house going up took Lena out of the inn bed the player gave her: she sleeps at " + QaMoveHallBed1111cGameTests.at(lenaBed));
			helper.assertTrue(tomasBed != null && !tomasBed.equals(lenaBed), "Tomas's bed after the move is " + QaMoveHallBed1111cGameTests.at(tomasBed) + ", Lena's " + QaMoveHallBed1111cGameTests.at(lenaBed));
			helper.assertTrue(free(level, inn) == 0, "the inn bed the player gave Lena is free for anyone");
			helper.succeed();
		});
	}

	/**
	 * The People row mid-move: Tomas, a Librarian whose lectern and bed are packed, is still a Librarian in the Job column
	 * (45.4: he keeps his job), his mood isn't "No bed" (he sleeps at the inn), and after the move his row has no
	 * "(no workstation)".
	 */
	//$ gametest_batch AREA '"qaHallCrossRowMid"'
	@GameTest(template = AREA, batch = "qaHallCrossRowMid")
	public void thePeopleRowMidMoveKeepsHisJobAndBed(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		QaMoveHallBed1111cGameTests.Setup s = QaMoveHallBed1111cGameTests.setup(helper, "xrowmid");
		helper.runAfterDelay(2, () -> {
			Villager tomas = QaMoveHallBed1111cGameTests.tomas(helper, s);
			Jobs.employ(level, tomas, helper.absolutePos(QaMoveHallBed1111cGameTests.LECTERN), PoiTypes.LIBRARIAN, VillagerProfession.LIBRARIAN);
			tomas.setVillagerXp(5);
			QaMoveHallBed1111cGameTests.work(level, s, x -> x.stage() == MoveSite.Stage.FOUNDATION);
			ServerPlayer player = listeningPlayer(helper, new ArrayList<>());
			CompoundTag mid = row(helper, player, tomas);
			String librarian = Component.translatable(mid.getString("job")).getString();
			helper.assertTrue(tomas.getVillagerData().getProfession() == VillagerProfession.LIBRARIAN && mid.getString("job").equals("entity.minecraft.villager.librarian"),
				"mid-move, his lectern packed, Tomas is a " + tomas.getVillagerData().getProfession() + " and the People row's job reads " + mid.getString("job") + " (" + mid.getString("kind") + ")");
			helper.assertTrue(!"no_bed".equals(mid.getString("mood")), "mid-move, sleeping at the inn, Tomas's mood reads No bed");
			QaMoveHallBed1111cGameTests.work(level, s, x -> false);
			CompoundTag after = row(helper, player, tomas);
			helper.assertTrue(after.getString("job").contains("librarian"), "after the move the People row's job reads " + after.getString("job"));
			helper.assertTrue(!after.getBoolean("no_site"), "after the move with his lectern put up, Tomas's row reads " + HallPeople.job(after).getString());
			helper.assertTrue(!"no_bed".equals(after.getString("mood")), "after the move, home in his moved bed, Tomas's mood reads No bed");
			helper.assertTrue(!librarian.isEmpty(), "the job column has no text");
			helper.succeed();
		});
	}

	/**
	 * After the move, Tomas (Librarian at the moved lectern) is let go at the Hall: the moved lectern is free at once, and
	 * Lena's Job ▾ (jobless) offers Librarian with that lectern free.
	 */
	//$ gametest_batch AREA '"qaHallCrossLetGoAfter"'
	@GameTest(template = AREA, batch = "qaHallCrossLetGoAfter")
	public void lettingGoAMovedWorkerOffersTheMovedLecternToANeighbor(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		QaMoveHallBed1111cGameTests.Setup s = QaMoveHallBed1111cGameTests.setup(helper, "xletgoafter");
		helper.runAfterDelay(2, () -> {
			Villager tomas = QaMoveHallBed1111cGameTests.tomas(helper, s);
			Jobs.employ(level, tomas, helper.absolutePos(QaMoveHallBed1111cGameTests.LECTERN), PoiTypes.LIBRARIAN, VillagerProfession.LIBRARIAN);
			tomas.setVillagerXp(5);
			Villager lena = lena(helper);
			QaMoveHallBed1111cGameTests.work(level, s, x -> false);
			BlockPos lectern = moved(s, QaMoveHallBed1111cGameTests.LECTERN);
			helper.assertTrue(level.getBlockState(lectern).is(Blocks.LECTERN), "the lectern isn't standing at " + lectern.toShortString());
			helper.assertTrue(tomas.getBrain().getMemory(MemoryModuleType.JOB_SITE).map(GlobalPos::pos).filter(lectern::equals).isPresent(), "setup: the moved lectern isn't Tomas's");
			helper.assertTrue(HallJobs.options(level, s.hall(), lena).stream().filter(o -> o.job() == VillagerProfession.LIBRARIAN).allMatch(o -> o.free().stream().noneMatch(f -> f.pos().equals(lectern))),
				"before Tomas is let go, Lena's Job ▾ offers his moved lectern as free");
			List<String> seen = new ArrayList<>();
			QaMoveHallBed1111cGameTests.letGoAtTheHall(helper, listeningPlayer(helper, seen), tomas);
			helper.assertTrue(tomas.getVillagerData().getProfession() == VillagerProfession.NONE, "the Hall's No job didn't let Tomas go: " + tomas.getVillagerData().getProfession() + "; told " + seen);
			helper.assertTrue(free(level, lectern) == 1, "the moved lectern is still held after Tomas was let go: " + free(level, lectern) + " places free");
			HallJobs.Option lib = HallJobs.options(level, s.hall(), lena).stream().filter(o -> o.job() == VillagerProfession.LIBRARIAN).findFirst().orElse(null);
			// other test areas' lecterns may be listed too: the moved one must be among the free ones
			helper.assertTrue(lib != null && lib.refusal() == null && lib.free().stream().anyMatch(f -> f.pos().equals(lectern)), "Lena's Job ▾ doesn't offer Librarian at the moved lectern: "
				+ (lib == null ? "no line" : lib.free().size() + " free, refusal " + (lib.refusal() == null ? "none" : lib.refusal().getString())));
			helper.succeed();
		});
	}

	/**
	 * Save and reload in between: the move is saved and read back mid-move, then finished; Tomas's People row and Bed ▾
	 * still say the moved bed is his (the list rebuilt from scratch, its cache forgotten as a restart does).
	 */
	//$ gametest_batch AREA '"qaHallCrossReload"'
	@GameTest(template = AREA, batch = "qaHallCrossReload")
	public void aReloadMidMoveThenTheBedListShowsTheMovedBed(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		QaMoveHallBed1111cGameTests.Setup s = QaMoveHallBed1111cGameTests.setup(helper, "xreload");
		helper.runAfterDelay(2, () -> {
			Villager tomas = QaMoveHallBed1111cGameTests.tomas(helper, s);
			QaMoveHallBed1111cGameTests.work(level, s, x -> x.stage() == MoveSite.Stage.FOUNDATION);
			CompoundTag saved = MoveSites.get(level).save(new CompoundTag(), level.registryAccess());
			level.getDataStorage().set(MoveSites.NAME, MoveSites.load(saved, level.registryAccess()));
			HallBeds.forget();
			HallJobs.forget();
			MoveSite site = QaMoveHallBed1111cGameTests.work(level, s, x -> false);
			helper.assertTrue(site.stage() == MoveSite.Stage.DONE, "the reloaded move ended " + site.stage());
			BlockPos bed = moved(s, QaMoveHallBed1111cGameTests.BED);
			helper.assertTrue(bed.equals(QaMoveHallBed1111cGameTests.home(tomas)), "after a reload mid-move Tomas sleeps at " + QaMoveHallBed1111cGameTests.at(QaMoveHallBed1111cGameTests.home(tomas)));
			ChoiceMenu[] menu = new ChoiceMenu[1];
			List<ItemStack> lines = beds(helper, listeningPlayer(helper, new ArrayList<>()), menu, tomas);
			HallBeds.Option o = option(level, s.hall(), bed);
			helper.assertTrue(o != null && o.sleeper() == tomas, "after a reload the Hall lists the moved bed's sleeper as " + (o == null ? "missing" : o.sleeper()));
			helper.assertTrue(lines.stream().filter(l -> state(l) == HallBeds.State.CURRENT).count() == 1, "Tomas's Bed ▾ after a reload has no 'their bed now': " + show(lines));
			helper.assertTrue(free(level, helper.absolutePos(QaMoveHallBed1111cGameTests.INN_BED)) == 1, "after a reload the inn bed stays held");
			helper.succeed();
		});
	}

	/**
	 * Bed ▾ mid-move on Tomas himself: his "now" is the inn bed, and the bed in the open is free; picking it at the Hall
	 * and then reloading the move before the house goes up keeps the player's choice (the Mover doesn't pull him back).
	 */
	//$ gametest_batch AREA '"qaHallCrossPickReload"'
	@GameTest(template = AREA, batch = "qaHallCrossPickReload")
	public void aHallPickMidMoveSurvivesAReloadAndTheHouseGoingUp(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		QaMoveHallBed1111cGameTests.Setup s = QaMoveHallBed1111cGameTests.setup(helper, "xpickreload");
		helper.runAfterDelay(2, () -> {
			Villager tomas = QaMoveHallBed1111cGameTests.tomas(helper, s);
			QaMoveHallBed1111cGameTests.work(level, s, x -> x.stage() == MoveSite.Stage.FOUNDATION);
			QaMoveHallBed1111cGameTests.pickTheFreeBed(helper, listeningPlayer(helper, new ArrayList<>()), tomas);
			BlockPos open = helper.absolutePos(QaMoveHallBed1111cGameTests.OTHER_BED);
			CompoundTag saved = MoveSites.get(level).save(new CompoundTag(), level.registryAccess());
			level.getDataStorage().set(MoveSites.NAME, MoveSites.load(saved, level.registryAccess()));
			HallBeds.forget();
			MoveSite site = QaMoveHallBed1111cGameTests.work(level, s, x -> false);
			helper.assertTrue(site.stage() == MoveSite.Stage.DONE, "the reloaded move ended " + site.stage());
			helper.assertTrue(open.equals(QaMoveHallBed1111cGameTests.home(tomas)) && open.equals(VillageNeeds.bed(level, tomas)),
				"after a reload the house going up undid the Hall's pick: Tomas sleeps at " + QaMoveHallBed1111cGameTests.at(QaMoveHallBed1111cGameTests.home(tomas)));
			BlockPos bed = moved(s, QaMoveHallBed1111cGameTests.BED);
			helper.assertTrue(free(level, bed) == 1, "the moved bed nobody sleeps in is held after a reload: " + free(level, bed) + " places free");
			helper.succeed();
		});
	}

	private static String counts(GameTestHelper helper, ServerPlayer player, Villager anyone) {
		ChoiceMenu[] menu = new ChoiceMenu[1];
		QaMoveHallBed1111cGameTests.openPeople(helper, player, menu, anyone);
		return menu[0].state(HallSections.PEOPLE_TOTAL) + " · " + menu[0].state(HallSections.PEOPLE_WORKING) + " working · "
			+ menu[0].state(HallSections.PEOPLE_JOBLESS) + " jobless";
	}

	/**
	 * The People table's counts ("8 · 6 working · 2 jobless") don't change while a worker's house and lectern are on the
	 * move: Tomas keeps his job (45.4), so he is counted working before, during and after, nobody is added or lost.
	 */
	//$ gametest_batch AREA '"qaHallCrossCounts"'
	@GameTest(template = AREA, batch = "qaHallCrossCounts")
	public void thePeopleCountsHoldThroughTheMove(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		QaMoveHallBed1111cGameTests.Setup s = QaMoveHallBed1111cGameTests.setup(helper, "xcounts");
		helper.runAfterDelay(2, () -> {
			Villager tomas = QaMoveHallBed1111cGameTests.tomas(helper, s);
			Jobs.employ(level, tomas, helper.absolutePos(QaMoveHallBed1111cGameTests.LECTERN), PoiTypes.LIBRARIAN, VillagerProfession.LIBRARIAN);
			tomas.setVillagerXp(5);
			lena(helper);
			ServerPlayer player = listeningPlayer(helper, new ArrayList<>());
			String before = counts(helper, player, tomas);
			QaMoveHallBed1111cGameTests.work(level, s, x -> x.stage() == MoveSite.Stage.FOUNDATION);
			String mid = counts(helper, player, tomas);
			helper.assertTrue(before.equals(mid), "the People counts changed while Tomas's house is away: " + before + " before, " + mid + " mid-move");
			QaMoveHallBed1111cGameTests.work(level, s, x -> false);
			String after = counts(helper, player, tomas);
			helper.assertTrue(before.equals(after), "the People counts changed with the move: " + before + " before, " + after + " after");
			helper.succeed();
		});
	}
}
