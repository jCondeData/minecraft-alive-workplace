package io.github.jcondedata.aliveworkplace.test;

import static io.github.jcondedata.aliveworkplace.test.StationsSpecGameTests.listeningPlayer;

import io.github.jcondedata.aliveworkplace.AliveWorkplace;
import io.github.jcondedata.aliveworkplace.WorkplaceConfig;
import io.github.jcondedata.aliveworkplace.hall.HallBeds;
import io.github.jcondedata.aliveworkplace.hall.HallJobs;
import io.github.jcondedata.aliveworkplace.hall.HallPeople;
import io.github.jcondedata.aliveworkplace.hall.HallSections;
import io.github.jcondedata.aliveworkplace.hall.HallSections.Section;
import io.github.jcondedata.aliveworkplace.hall.VillageHallScreen;
import io.github.jcondedata.aliveworkplace.hall.VillageHalls;
import io.github.jcondedata.aliveworkplace.hall.VillageNeeds;
import io.github.jcondedata.aliveworkplace.move.MoveSite;
import io.github.jcondedata.aliveworkplace.move.MoveSites;
import io.github.jcondedata.aliveworkplace.people.ClassPerks;
import io.github.jcondedata.aliveworkplace.people.Homes;
import io.github.jcondedata.aliveworkplace.people.SocialClasses;
import io.github.jcondedata.aliveworkplace.registry.ModBlocks;
import io.github.jcondedata.aliveworkplace.registry.ModVillagers;
import io.github.jcondedata.aliveworkplace.work.ChoiceMenu;
import io.github.jcondedata.aliveworkplace.work.Jobs;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.registries.BuiltInRegistries;
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
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Rotation;

/**
 * QA (lane C, 2026-11-16): the Village Hall's People table (40.2-40.4) meeting features that landed after it, through
 * the hall's own clicks. What QaHallCross1114c, QaMoveHallBed1111c, QaMoverResidents* and ClassJobGameTests leave out:
 * <ul>
 * <li>a move called off (45.3/45.4) after a Bed ▾ pick mid-move keeps the player's pick; without a pick the table shows
 * the old bed back in its building at once;</li>
 * <li>mid-move, the moment the Mover puts the bed (or the lectern) up at the new spot it is the resident's: another
 * villager's Bed ▾ (Job ▾) never offers it free, also after a save and reload of the move;</li>
 * <li>the Mover in the table: his row says Mover, his own Job ▾ "Mover · their job now", and a jobless villager's Job ▾
 * offers "Mover · 1 free Mover's Crate" for a free crate only (his own isn't counted), a click making them a Mover;</li>
 * <li>class-gated jobs (34.8) on Job ▾: a Peasant worker's line reads "Shopkeeper · needs Artisan class or better", the
 * two clicks pay nothing and change nothing; a jobless Peasant's one click takes nothing; an Artisan's do.</li>
 * </ul>
 */
public class QaHallPeopleCross1116cGameTests implements FabricGameTest {
	private static final String MOVE_AREA = QaMoveHallBed1111cGameTests.AREA;
	private static final String AREA = "aliveworkplace_test:big_area";
	private static final BlockPos HALL = new BlockPos(14, 2, 14);
	private static final BlockPos COUNTER = new BlockPos(8, 2, 4);
	private static final BlockPos COMPOSTER = new BlockPos(4, 2, 4);
	private static final BlockPos CRATE = new BlockPos(4, 2, 10);
	private static final BlockPos FREE_CRATE = new BlockPos(20, 2, 6);

	// ---- shared helpers ------------------------------------------------------------------------------------------------

	private static BlockPos moved(QaMoveHallBed1111cGameTests.Setup s, BlockPos helperPos) {
		return s.before().at(helperPos.subtract(QaMoveHallBed1111cGameTests.MIN), s.to(), Rotation.CLOCKWISE_90);
	}

	private static Villager lena(GameTestHelper helper) {
		Villager lena = helper.spawn(EntityType.VILLAGER, new BlockPos(10, 2, 8));
		lena.setNoAi(true);
		lena.setCustomName(Component.literal("Lena"));
		return lena;
	}

	private static HallBeds.State bedState(ItemStack line) {
		return HallBeds.State.of(HallBeds.mark(line).getString("state"));
	}

	private static String showBeds(List<ItemStack> lines) {
		return lines.stream().map(l -> l.getHoverName().getString() + " (" + HallBeds.mark(l).getString("state") + ")").toList().toString();
	}

	private static String showJobs(List<ItemStack> lines) {
		return lines.stream().map(l -> l.getHoverName().getString() + " (" + HallJobs.mark(l).getString("state") + ")").toList().toString();
	}

	/** Bed ▾ on {@code villager}'s row of the move setup's hall. */
	private static List<ItemStack> beds(GameTestHelper helper, ServerPlayer player, Villager villager) {
		ChoiceMenu[] menu = new ChoiceMenu[1];
		int slot = QaMoveHallBed1111cGameTests.openPeople(helper, player, menu, villager);
		helper.assertTrue(menu[0].clickMenuButton(player, HallBeds.OPEN + slot), "Bed ▾ wasn't taken");
		List<ItemStack> lines = HallBeds.lines(menu[0].icon(slot));
		helper.assertTrue(!lines.isEmpty(), "Bed ▾ opened no list for " + villager.getName().getString());
		return lines;
	}

	private static CompoundTag moveRow(GameTestHelper helper, ServerPlayer player, Villager villager) {
		ChoiceMenu[] menu = new ChoiceMenu[1];
		int slot = QaMoveHallBed1111cGameTests.openPeople(helper, player, menu, villager);
		return HallPeople.row(menu[0].icon(slot));
	}

	private static HallBeds.Option option(ServerLevel level, BlockPos hall, BlockPos bed) {
		return HallBeds.options(level, hall).stream().filter(o -> o.bed().equals(bed)).findFirst().orElse(null);
	}

	private static int free(ServerLevel level, BlockPos pos) {
		return level.getPoiManager().getFreeTickets(pos);
	}

	private static Optional<BlockPos> site(Villager villager) {
		return villager.getBrain().getMemory(MemoryModuleType.JOB_SITE).map(GlobalPos::pos);
	}

	private static int id(VillagerProfession job) {
		return BuiltInRegistries.VILLAGER_PROFESSION.getId(job);
	}

	private static ItemStack jobLine(GameTestHelper helper, List<ItemStack> lines, VillagerProfession job) {
		for (ItemStack line : lines) {
			CompoundTag mark = HallJobs.mark(line);
			if (mark != null && mark.getInt("id") == id(job) && !mark.getString("state").equals("header")) {
				return line;
			}
		}
		helper.fail("no line for " + job + " in " + showJobs(lines));
		return ItemStack.EMPTY;
	}

	private static String jobState(ItemStack line) {
		return HallJobs.mark(line).getString("state");
	}

	// ---- a move called off and the People table --------------------------------------------------------------------------

	/**
	 * Mid-move the player gives Tomas the bed in the open from his Bed ▾; then the move is called off and the house put
	 * back. The pick stands (moving-buildings.md: "A bed or job you give a resident at the Village Hall's People page while
	 * their building is away stands"): Tomas still sleeps in the bed in the open, his Bed ▾ says so, and his old bed, put
	 * back up, is free for someone else.
	 */
	//$ gametest_batch MOVE_AREA '"qaHallPplXCalledOffPick"'
	@GameTest(template = MOVE_AREA, batch = "qaHallPplXCalledOffPick")
	public void aMoveCalledOffAfterAHallPickKeepsThePick(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		QaMoveHallBed1111cGameTests.Setup s = QaMoveHallBed1111cGameTests.setup(helper, "pxcalloffpick");
		helper.runAfterDelay(2, () -> {
			Villager tomas = QaMoveHallBed1111cGameTests.tomas(helper, s);
			MoveSite site = QaMoveHallBed1111cGameTests.work(level, s, x -> x.stage() == MoveSite.Stage.FOUNDATION);
			helper.assertTrue(site.stage() == MoveSite.Stage.FOUNDATION, "the house wasn't taken down: " + site.stage());
			ServerPlayer player = listeningPlayer(helper, new ArrayList<>());
			QaMoveHallBed1111cGameTests.pickTheFreeBed(helper, player, tomas);
			BlockPos open = helper.absolutePos(QaMoveHallBed1111cGameTests.OTHER_BED);
			helper.assertTrue(open.equals(QaMoveHallBed1111cGameTests.home(tomas)), "setup: the pick didn't give Tomas the bed in the open");
			helper.assertTrue(MoveSites.get(level).cancel(level, s.from()), "the move couldn't be called off");
			site = QaMoveHallBed1111cGameTests.work(level, s, x -> false);
			helper.assertTrue(site.stage() == MoveSite.Stage.DONE, "the called-off move ended " + site.stage());
			BlockPos old = helper.absolutePos(QaMoveHallBed1111cGameTests.BED);
			helper.assertTrue(level.getBlockState(old).getBlock() instanceof BedBlock, "the house's bed wasn't put back at " + old.toShortString());
			helper.assertTrue(open.equals(QaMoveHallBed1111cGameTests.home(tomas)) && open.equals(VillageNeeds.bed(level, tomas)),
				"calling the move off pulled Tomas out of the bed the player gave him: he sleeps at " + QaMoveHallBed1111cGameTests.at(QaMoveHallBed1111cGameTests.home(tomas)));
			helper.assertTrue(free(level, open) == 0, "the bed the player gave Tomas is free for anyone after the call-off");
			helper.assertTrue(free(level, old) == 1, "his old bed, put back, is held for nobody: " + free(level, old) + " places free");
			helper.assertTrue(free(level, helper.absolutePos(QaMoveHallBed1111cGameTests.INN_BED)) == 1, "the inn bed is still held");
			HallBeds.forget();
			List<ItemStack> lines = beds(helper, player, tomas);
			String where = VillageHallScreen.where(s.hall(), open).getString();
			List<ItemStack> current = lines.stream().filter(l -> bedState(l) == HallBeds.State.CURRENT).toList();
			helper.assertTrue(current.size() == 1 && current.get(0).getHoverName().getString().contains(where),
				"Tomas's Bed ▾ after the call-off doesn't say the bed in the open (" + where + ") is his: " + showBeds(lines));
			helper.succeed();
		});
	}

	/**
	 * No pick: the move is called off once the house is taken down. When it stands again Tomas is back in his old bed (as
	 * 45.4 says) and the People table shows it at once: his Bed ▾ "their bed now" names the house's building, the inn bed is
	 * let go, his mood isn't No bed, and the counts are what they were.
	 */
	//$ gametest_batch MOVE_AREA '"qaHallPplXCalledOffBack"'
	@GameTest(template = MOVE_AREA, batch = "qaHallPplXCalledOffBack")
	public void aCalledOffMoveShowsTheOldBedBackOnTheTable(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		QaMoveHallBed1111cGameTests.Setup s = QaMoveHallBed1111cGameTests.setup(helper, "pxcalloffback");
		helper.runAfterDelay(2, () -> {
			Villager tomas = QaMoveHallBed1111cGameTests.tomas(helper, s);
			BlockPos old = helper.absolutePos(QaMoveHallBed1111cGameTests.BED);
			Homes.Building building = Homes.building(level, old).orElse(null);
			helper.assertTrue(building != null, "setup: the house's bed isn't in a known building");
			String name = building.home().name().getString();
			ServerPlayer player = listeningPlayer(helper, new ArrayList<>());
			CompoundTag before = moveRow(helper, player, tomas);
			QaMoveHallBed1111cGameTests.work(level, s, x -> x.stage() == MoveSite.Stage.FOUNDATION);
			BlockPos inn = helper.absolutePos(QaMoveHallBed1111cGameTests.INN_BED);
			helper.assertTrue(inn.equals(QaMoveHallBed1111cGameTests.home(tomas)), "setup: mid-move Tomas sleeps at " + QaMoveHallBed1111cGameTests.at(QaMoveHallBed1111cGameTests.home(tomas)));
			helper.assertTrue(MoveSites.get(level).cancel(level, s.from()), "the move couldn't be called off");
			MoveSite site = QaMoveHallBed1111cGameTests.work(level, s, x -> false);
			helper.assertTrue(site.stage() == MoveSite.Stage.DONE, "the called-off move ended " + site.stage());
			helper.assertTrue(old.equals(QaMoveHallBed1111cGameTests.home(tomas)) && old.equals(VillageNeeds.bed(level, tomas)),
				"after the call-off Tomas sleeps at " + QaMoveHallBed1111cGameTests.at(QaMoveHallBed1111cGameTests.home(tomas)) + ", the census says "
					+ QaMoveHallBed1111cGameTests.at(VillageNeeds.bed(level, tomas)) + ", not his old bed " + old.toShortString());
			helper.assertTrue(free(level, inn) == 1, "the inn bed wasn't let go after the call-off");
			List<ItemStack> lines = beds(helper, player, tomas);
			List<ItemStack> current = lines.stream().filter(l -> bedState(l) == HallBeds.State.CURRENT).toList();
			helper.assertTrue(current.size() == 1 && current.get(0).getHoverName().getString().contains(name),
				"Tomas's Bed ▾ after the call-off doesn't name his house '" + name + "': " + showBeds(lines));
			helper.assertTrue(lines.stream().noneMatch(l -> bedState(l) == HallBeds.State.FREE && l.getHoverName().getString().contains(name)),
				"his own house's bed is offered to him as free: " + showBeds(lines));
			CompoundTag after = moveRow(helper, player, tomas);
			helper.assertTrue(!"no_bed".equals(after.getString("mood")), "after the call-off Tomas's mood reads No bed");
			helper.assertTrue(before.getString("job").equals(after.getString("job")), "his job column changed: " + before.getString("job") + " -> " + after.getString("job"));
			helper.succeed();
		});
	}

	// ---- mid-move: put up means his -------------------------------------------------------------------------------------

	/**
	 * Mid-move, the Mover has just put Tomas's bed up at the new spot (the house not finished). It is his at once
	 * (45.4: "nobody else can take it"): Lena's Bed ▾ offers it only as a swap with Tomas, never free; the Hall's list has it
	 * taken by Tomas; and the same after the move is saved and read back (the lists rebuilt as a restart does).
	 */
	//$ gametest_batch MOVE_AREA '"qaHallPplXBedUpMid"'
	@GameTest(template = MOVE_AREA, batch = "qaHallPplXBedUpMid")
	public void aBedPutUpMidMoveIsNoOneElsesFreeBed(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		QaMoveHallBed1111cGameTests.Setup s = QaMoveHallBed1111cGameTests.setup(helper, "pxbedup");
		helper.runAfterDelay(2, () -> {
			Villager tomas = QaMoveHallBed1111cGameTests.tomas(helper, s);
			Villager lena = lena(helper);
			BlockPos open = helper.absolutePos(QaMoveHallBed1111cGameTests.OTHER_BED);
			helper.assertTrue(HallBeds.moveIn(level, lena, open), "setup: Lena didn't get the bed in the open");
			BlockPos bed = moved(s, QaMoveHallBed1111cGameTests.BED);
			MoveSite site = QaMoveHallBed1111cGameTests.work(level, s, x -> x.stage().rebuilding() && level.getBlockState(bed).getBlock() instanceof BedBlock);
			helper.assertTrue(site.stage() != MoveSite.Stage.DONE && level.getBlockState(bed).getBlock() instanceof BedBlock,
				"setup: couldn't stop with the bed up and the house unfinished: " + site.stage());
			helper.assertTrue(bed.equals(QaMoveHallBed1111cGameTests.home(tomas)), "the bed just put up isn't Tomas's at once: he sleeps at "
				+ QaMoveHallBed1111cGameTests.at(QaMoveHallBed1111cGameTests.home(tomas)) + " (" + site.stage() + ")");
			ServerPlayer player = listeningPlayer(helper, new ArrayList<>());
			for (int pass = 0; pass < 2; pass++) {
				String when = pass == 0 ? "mid-move" : "mid-move after a reload";
				HallBeds.Option o = option(level, s.hall(), bed);
				helper.assertTrue(o != null && o.taken() && o.sleeper() == tomas,
					when + " the Hall lists the bed just put up as " + (o == null ? "missing" : (o.taken() ? "taken by " + o.sleeper() : "free")));
				List<ItemStack> lines = beds(helper, player, lena);
				String where = VillageHallScreen.where(s.hall(), bed).getString();
				helper.assertTrue(lines.stream().noneMatch(l -> bedState(l) == HallBeds.State.FREE && l.getHoverName().getString().contains(where)),
					when + ", Lena's Bed ▾ offers Tomas's bed just put up (" + where + ") as free: " + showBeds(lines));
				helper.assertTrue(lines.stream().anyMatch(l -> bedState(l) == HallBeds.State.SWAP && l.getHoverName().getString().contains("Tomas")),
					when + ", Lena's Bed ▾ has no swap with Tomas: " + showBeds(lines));
				helper.assertTrue(free(level, bed) == 0, when + " the bed just put up is free for anyone");
				CompoundTag saved = MoveSites.get(level).save(new CompoundTag(), level.registryAccess());
				level.getDataStorage().set(MoveSites.NAME, MoveSites.load(saved, level.registryAccess()));
				HallBeds.forget();
			}
			QaMoveHallBed1111cGameTests.work(level, s, x -> false);
			helper.assertTrue(bed.equals(QaMoveHallBed1111cGameTests.home(tomas)) && open.equals(QaMoveHallBed1111cGameTests.home(lena)),
				"after the move Tomas sleeps at " + QaMoveHallBed1111cGameTests.at(QaMoveHallBed1111cGameTests.home(tomas)) + ", Lena at "
					+ QaMoveHallBed1111cGameTests.at(QaMoveHallBed1111cGameTests.home(lena)));
			helper.succeed();
		});
	}

	/**
	 * Mid-move, the Mover has just put Tomas's lectern up at the new spot. It's his at once (45.4): jobless Lena's Job ▾
	 * doesn't offer Librarian as free, and her click on that line gives her nothing; Tomas keeps it.
	 */
	//$ gametest_batch MOVE_AREA '"qaHallPplXLecternUpMid"'
	@GameTest(template = MOVE_AREA, batch = "qaHallPplXLecternUpMid")
	public void aLecternPutUpMidMoveIsNotOfferedToAJoblessNeighbor(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		QaMoveHallBed1111cGameTests.Setup s = QaMoveHallBed1111cGameTests.setup(helper, "pxlecternup");
		helper.runAfterDelay(2, () -> {
			Villager tomas = QaMoveHallBed1111cGameTests.tomas(helper, s);
			Jobs.employ(level, tomas, helper.absolutePos(QaMoveHallBed1111cGameTests.LECTERN), PoiTypes.LIBRARIAN, VillagerProfession.LIBRARIAN);
			tomas.setVillagerXp(5);
			Villager lena = lena(helper);
			BlockPos lectern = moved(s, QaMoveHallBed1111cGameTests.LECTERN);
			MoveSite site = QaMoveHallBed1111cGameTests.work(level, s, x -> x.stage().rebuilding() && level.getBlockState(lectern).is(Blocks.LECTERN));
			helper.assertTrue(site.stage() != MoveSite.Stage.DONE && level.getBlockState(lectern).is(Blocks.LECTERN),
				"setup: couldn't stop with the lectern up and the house unfinished: " + site.stage());
			helper.assertTrue(site(tomas).filter(lectern::equals).isPresent(), "the lectern just put up isn't Tomas's at once: his job site " + site(tomas));
			ServerPlayer player = listeningPlayer(helper, new ArrayList<>());
			ChoiceMenu[] menu = new ChoiceMenu[1];
			int slot = QaMoveHallBed1111cGameTests.openPeople(helper, player, menu, lena);
			helper.assertTrue(menu[0].clickMenuButton(player, HallJobs.OPEN + slot), "Job ▾ wasn't taken");
			List<ItemStack> lines = HallJobs.lines(menu[0].icon(slot));
			ItemStack librarian = jobLine(helper, lines, VillagerProfession.LIBRARIAN);
			// lecterns left standing by other tests' areas may be listed too: the one just put up must not be among the free ones
			HallJobs.Option lib = HallJobs.options(level, s.hall(), lena).stream().filter(o -> o.job() == VillagerProfession.LIBRARIAN).findFirst().orElse(null);
			helper.assertTrue(lib != null && lib.free().stream().noneMatch(f -> f.pos().equals(lectern)),
				"mid-move Lena's Job ▾ counts Tomas's lectern just put up as free: " + librarian.getHoverName().getString());
			helper.assertTrue(lib.free().isEmpty() == !jobState(librarian).equals("free"), "the Librarian line (" + librarian.getHoverName().getString()
				+ ", " + jobState(librarian) + ") doesn't match the " + lib.free().size() + " free lecterns");
			menu[0].clickMenuButton(player, HallJobs.PICK + id(VillagerProfession.LIBRARIAN));
			helper.assertTrue(site(lena).filter(lectern::equals).isEmpty(), "Lena's click on Librarian gave her Tomas's lectern just put up");
			helper.assertTrue(site(tomas).filter(lectern::equals).isPresent() && tomas.getVillagerData().getProfession() == VillagerProfession.LIBRARIAN,
				"Tomas lost the lectern: " + site(tomas) + " " + tomas.getVillagerData().getProfession());
			QaMoveHallBed1111cGameTests.work(level, s, x -> false);
			helper.assertTrue(site(tomas).filter(lectern::equals).isPresent(), "after the move Tomas's lectern is " + site(tomas));
			helper.succeed();
		});
	}

	// ---- the Mover in the People table ---------------------------------------------------------------------------------

	private static void classesOn(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		SocialClasses.ENABLED = true;
		SocialClasses.load(SocialClasses.files(level.getServer().getResourceManager()));
		ClassPerks.forget();
		Leftovers.after(helper, () -> {
			new WorkplaceConfig().apply();
			ClassPerks.forget();
		});
	}

	private static void village(GameTestHelper helper) {
		Leftovers.clear(helper);
		Leftovers.halls(helper);
		HallSections.forget();
		HallJobs.forget();
		HallBeds.forget();
		int was = VillageHalls.RADIUS;
		VillageHalls.RADIUS = 16;
		Leftovers.after(helper, () -> VillageHalls.RADIUS = was);
		helper.setBlock(HALL, ModBlocks.VILLAGE_HALL);
	}

	private static Villager villager(GameTestHelper helper, BlockPos at, String name, String cls) {
		Villager v = helper.spawn(EntityType.VILLAGER, at);
		v.setNoAi(true);
		v.setCustomName(Component.literal(name));
		if (cls != null) {
			SocialClasses.seed(v, SocialClasses.get(AliveWorkplace.id(cls)));
		}
		return v;
	}

	private static ChoiceMenu openPeople(GameTestHelper helper, ServerPlayer player) {
		BlockPos hall = helper.absolutePos(HALL);
		player.teleportTo(hall.getX() + 1.5, hall.getY(), hall.getZ() + 0.5);
		helper.useBlock(HALL, player);
		helper.assertTrue(player.containerMenu instanceof ChoiceMenu, "the hall didn't open: " + player.containerMenu);
		ChoiceMenu menu = (ChoiceMenu) player.containerMenu;
		helper.assertTrue(menu.clickMenuButton(player, Section.PEOPLE.ordinal()), "the sidebar didn't take People");
		return menu;
	}

	private static int slotOf(GameTestHelper helper, ChoiceMenu menu, Villager villager) {
		for (int slot = 0; slot < ChoiceMenu.SIZE; slot++) {
			CompoundTag row = HallPeople.row(menu.icon(slot));
			if (row != null && row.getString("uuid").equals(villager.getStringUUID())) {
				return slot;
			}
		}
		helper.fail("no People row for " + villager.getName().getString());
		return -1;
	}

	private static List<ItemStack> jobs(GameTestHelper helper, ServerPlayer player, ChoiceMenu menu, Villager villager) {
		int slot = slotOf(helper, menu, villager);
		helper.assertTrue(menu.clickMenuButton(player, HallJobs.OPEN + slot), "Job ▾ wasn't taken");
		List<ItemStack> lines = HallJobs.lines(menu.icon(slotOf(helper, menu, villager)));
		helper.assertTrue(!lines.isEmpty(), "Job ▾ opened no list for " + villager.getName().getString());
		return lines;
	}

	/**
	 * The Mover (45.3) in the People table: his row's job reads Mover, his own Job ▾ "Mover · their job now". A jobless
	 * villager's Job ▾ offers "Mover · 1 free Mover's Crate" (the free crate only: the Mover's own crate is his), and one
	 * click makes them a Mover at a free crate; afterwards neither taken crate is offered to anyone.
	 */
	//$ gametest_ticks_batch AREA '200' '"qaHallPplXMover"'
	@GameTest(template = AREA, timeoutTicks = 200, batch = "qaHallPplXMover")
	public void theMoverInThePeopleTable(GameTestHelper helper) {
		village(helper);
		ServerLevel level = helper.getLevel();
		helper.setBlock(CRATE, ModBlocks.MOVERS_CRATE);
		helper.setBlock(FREE_CRATE, ModBlocks.MOVERS_CRATE);
		helper.runAfterDelay(3, () -> {
			Villager mover = villager(helper, CRATE.south(), "Bram", null);
			Jobs.employ(level, mover, helper.absolutePos(CRATE), ModVillagers.MOVERS_CRATE_POI, ModVillagers.MOVER);
			Villager ivy = villager(helper, new BlockPos(10, 2, 10), "Ivy", null);
			List<String> seen = new ArrayList<>();
			ServerPlayer player = listeningPlayer(helper, seen);
			ChoiceMenu menu = openPeople(helper, player);
			CompoundTag row = HallPeople.row(menu.icon(slotOf(helper, menu, mover)));
			helper.assertTrue(row.getString("job").equals("entity.minecraft.villager.mover")
					&& Component.translatable(row.getString("job")).getString().equals("Mover"),
				"the Mover's row reads job '" + row.getString("job") + "'");
			List<ItemStack> own = jobs(helper, player, menu, mover);
			helper.assertTrue(jobLine(helper, own, ModVillagers.MOVER).getHoverName().getString().equals("Mover · their job now"),
				"the Mover's own Job ▾: " + showJobs(own));
			menu = openPeople(helper, player);
			List<ItemStack> lines = jobs(helper, player, menu, ivy);
			ItemStack line = jobLine(helper, lines, ModVillagers.MOVER);
			BlockPos his = helper.absolutePos(CRATE);
			BlockPos spare = helper.absolutePos(FREE_CRATE);
			// crates left by other tests' areas may be counted too: the spare one must be, the Mover's own never
			HallJobs.Option opt = HallJobs.options(level, helper.absolutePos(HALL), ivy).stream().filter(o -> o.job() == ModVillagers.MOVER).findFirst().orElse(null);
			helper.assertTrue(opt != null && opt.free().stream().anyMatch(f -> f.pos().equals(spare)) && opt.free().stream().noneMatch(f -> f.pos().equals(his)),
				"Ivy's Mover option's free crates: " + (opt == null ? "no option" : opt.free().stream().map(f -> helper.relativePos(f.pos()).toShortString()).toList()));
			String want = opt.free().size() == 1 ? "Mover · 1 free Mover's Crate" : "Mover · " + opt.free().size() + " free Mover's Crates";
			helper.assertTrue(line.getHoverName().getString().equals(want) && jobState(line).equals("free"),
				"Ivy's Mover line: " + line.getHoverName().getString() + " (" + jobState(line) + "), wanted " + want);
			helper.assertTrue(menu.clickMenuButton(player, HallJobs.PICK + id(ModVillagers.MOVER)), "the pick wasn't taken");
			helper.assertTrue(ivy.getVillagerData().getProfession() == ModVillagers.MOVER && site(ivy).filter(p -> !p.equals(his) && level.getBlockState(p).is(ModBlocks.MOVERS_CRATE)).isPresent(),
				"Ivy's one click didn't make her a Mover at a free crate: " + ivy.getVillagerData().getProfession() + " at " + site(ivy) + "; told " + seen);
			helper.assertTrue(site(mover).filter(his::equals).isPresent() && mover.getVillagerData().getProfession() == ModVillagers.MOVER, "the Mover lost his crate: " + site(mover));
			BlockPos ivys = site(ivy).get();
			Villager kit = villager(helper, new BlockPos(12, 2, 8), "Kit", null);
			HallJobs.Option kits = HallJobs.options(level, helper.absolutePos(HALL), kit).stream().filter(o -> o.job() == ModVillagers.MOVER).findFirst().orElse(null);
			helper.assertTrue(kits != null && kits.free().stream().noneMatch(f -> f.pos().equals(his) || f.pos().equals(ivys)),
				"Kit's Job ▾ still offers a crate the Mover or Ivy has");
			helper.succeed();
		});
	}

	// ---- class-gated jobs on Job ▾ ---------------------------------------------------------------------------------------

	private static Villager farmer(GameTestHelper helper, BlockPos composter, String name, String cls) {
		helper.setBlock(composter, Blocks.COMPOSTER);
		Villager v = villager(helper, composter.south(), name, cls);
		Jobs.employ(helper.getLevel(), v, helper.absolutePos(composter), PoiTypes.FARMER, VillagerProfession.FARMER);
		v.setVillagerXp(5);
		return v;
	}

	/**
	 * A Peasant Farmer's Job ▾ (34.8 meeting 40.3): with a free Shop Counter, the Shopkeeper line reads "Shopkeeper ·
	 * needs Artisan class or better" and isn't free; both clicks (offer, then pay) pay nothing, change nothing, the counter
	 * stays free, and the player is told why ("Pip is Peasant class; Shopkeeper needs Artisan or better"). An Artisan
	 * Farmer's two clicks pay and make her the Shopkeeper at the counter.
	 */
	//$ gametest_ticks_batch AREA '200' '"qaHallPplXClassWorker"'
	@GameTest(template = AREA, timeoutTicks = 200, batch = "qaHallPplXClassWorker")
	public void aPeasantWorkerCannotBuyAJobAboveTheirClass(GameTestHelper helper) {
		village(helper);
		classesOn(helper);
		ServerLevel level = helper.getLevel();
		helper.setBlock(COUNTER, ModBlocks.SHOP_COUNTER);
		helper.runAfterDelay(3, () -> {
			Villager pip = farmer(helper, COMPOSTER, "Pip", "peasant");
			Villager ann = farmer(helper, new BlockPos(4, 2, 18), "Ann", "artisan");
			List<String> seen = new ArrayList<>();
			ServerPlayer player = listeningPlayer(helper, seen);
			player.getInventory().setItem(9, new ItemStack(Items.EMERALD, 64));
			BlockPos counter = helper.absolutePos(COUNTER);
			helper.assertTrue(free(level, counter) == 1, "setup: the counter isn't free");

			ChoiceMenu menu = openPeople(helper, player);
			List<ItemStack> lines = jobs(helper, player, menu, pip);
			ItemStack shop = jobLine(helper, lines, ModVillagers.SHOPKEEPER);
			helper.assertTrue(jobState(shop).equals("class") && shop.getHoverName().getString().equals("Shopkeeper · needs Artisan class or better"),
				"Pip's Shopkeeper line: " + shop.getHoverName().getString() + " (" + jobState(shop) + ")");
			int pick = HallJobs.PICK + id(ModVillagers.SHOPKEEPER);
			menu.clickMenuButton(player, pick);
			menu.clickMenuButton(player, pick);
			helper.assertTrue(pip.getVillagerData().getProfession() == VillagerProfession.FARMER, "Pip's clicks made him a " + pip.getVillagerData().getProfession());
			helper.assertTrue(player.getInventory().countItem(Items.EMERALD) == 64, "Pip's refused clicks cost " + (64 - player.getInventory().countItem(Items.EMERALD)) + " emeralds");
			helper.assertTrue(free(level, counter) == 1, "Pip's refused clicks took the counter");
			helper.assertTrue(seen.stream().anyMatch(m -> m.equals("Pip is Peasant class; Shopkeeper needs Artisan or better")),
				"the player isn't told why: " + seen);

			ChoiceMenu theirs = openPeople(helper, player);
			ItemStack annShop = jobLine(helper, jobs(helper, player, theirs, ann), ModVillagers.SHOPKEEPER);
			helper.assertTrue(jobState(annShop).equals("free"), "Ann (Artisan) sees Shopkeeper as " + annShop.getHoverName().getString() + " (" + jobState(annShop) + ")");
			theirs.clickMenuButton(player, pick);
			helper.assertTrue(ann.getVillagerData().getProfession() == VillagerProfession.FARMER && player.getInventory().countItem(Items.EMERALD) == 64,
				"Ann's first click changed something");
			theirs.clickMenuButton(player, pick);
			helper.assertTrue(ann.getVillagerData().getProfession() == ModVillagers.SHOPKEEPER && site(ann).filter(counter::equals).isPresent(),
				"Ann's second click didn't make her the Shopkeeper at the counter: " + ann.getVillagerData().getProfession() + " at " + site(ann) + "; told " + seen);
			helper.assertTrue(player.getInventory().countItem(Items.EMERALD) < 64, "Ann's retraining was free");
			helper.succeed();
		});
	}

	/**
	 * A jobless Peasant's Job ▾: the Shopkeeper line needs Artisan; the one click (a jobless villager's free block) takes
	 * nothing and the counter stays free. A jobless Artisan's one click takes it, for nothing.
	 */
	//$ gametest_ticks_batch AREA '200' '"qaHallPplXClassJobless"'
	@GameTest(template = AREA, timeoutTicks = 200, batch = "qaHallPplXClassJobless")
	public void aJoblessPeasantIsNotGivenABlockAboveTheirClass(GameTestHelper helper) {
		village(helper);
		classesOn(helper);
		ServerLevel level = helper.getLevel();
		helper.setBlock(COUNTER, ModBlocks.SHOP_COUNTER);
		helper.runAfterDelay(3, () -> {
			Villager pip = villager(helper, new BlockPos(6, 2, 8), "Pip", "peasant");
			Villager ann = villager(helper, new BlockPos(10, 2, 8), "Ann", "artisan");
			List<String> seen = new ArrayList<>();
			ServerPlayer player = listeningPlayer(helper, seen);
			BlockPos counter = helper.absolutePos(COUNTER);
			int pick = HallJobs.PICK + id(ModVillagers.SHOPKEEPER);

			ChoiceMenu menu = openPeople(helper, player);
			ItemStack shop = jobLine(helper, jobs(helper, player, menu, pip), ModVillagers.SHOPKEEPER);
			helper.assertTrue(jobState(shop).equals("class"), "jobless Pip's Shopkeeper line: " + shop.getHoverName().getString() + " (" + jobState(shop) + ")");
			menu.clickMenuButton(player, pick);
			menu.clickMenuButton(player, pick);
			helper.assertTrue(pip.getVillagerData().getProfession() == VillagerProfession.NONE && site(pip).isEmpty(),
				"jobless Pip (Peasant) was made a " + pip.getVillagerData().getProfession() + " at " + site(pip));
			helper.assertTrue(free(level, counter) == 1, "Pip's refused click took the counter");
			helper.assertTrue(seen.stream().anyMatch(m -> m.equals("Pip is Peasant class; Shopkeeper needs Artisan or better")),
				"the player isn't told why: " + seen);

			ChoiceMenu theirs = openPeople(helper, player);
			ItemStack annShop = jobLine(helper, jobs(helper, player, theirs, ann), ModVillagers.SHOPKEEPER);
			helper.assertTrue(jobState(annShop).equals("free"), "jobless Ann (Artisan) sees Shopkeeper as " + annShop.getHoverName().getString());
			theirs.clickMenuButton(player, pick);
			helper.assertTrue(ann.getVillagerData().getProfession() == ModVillagers.SHOPKEEPER && site(ann).filter(counter::equals).isPresent(),
				"Ann's one click didn't give her the counter: " + ann.getVillagerData().getProfession() + " at " + site(ann) + "; told " + seen);
			helper.succeed();
		});
	}
}
