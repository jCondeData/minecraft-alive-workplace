package io.github.jcondedata.aliveworkplace.test;

import io.github.jcondedata.aliveworkplace.camp.CampBeds;
import io.github.jcondedata.aliveworkplace.city.Stewards;
import io.github.jcondedata.aliveworkplace.hall.Chronicle;
import io.github.jcondedata.aliveworkplace.hall.HallBeds;
import io.github.jcondedata.aliveworkplace.hall.HallJobs;
import io.github.jcondedata.aliveworkplace.hall.HallPeople;
import io.github.jcondedata.aliveworkplace.hall.VillageNeeds;
import io.github.jcondedata.aliveworkplace.legend.LegendData;
import io.github.jcondedata.aliveworkplace.legend.Legends;
import io.github.jcondedata.aliveworkplace.people.Retirement;
import io.github.jcondedata.aliveworkplace.registry.ModAttachments;
import io.github.jcondedata.aliveworkplace.registry.ModItems;
import io.github.jcondedata.aliveworkplace.registry.ModVillagers;
import io.github.jcondedata.aliveworkplace.work.ChoiceMenu;
import io.github.jcondedata.aliveworkplace.work.Jobs;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.village.poi.PoiTypes;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.npc.VillagerProfession;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.properties.BedPart;
import net.minecraft.world.level.levelgen.structure.BoundingBox;

/**
 * QA (lane-c-1103-1253): the Village Hall's People table (ROADMAP 40.3 Job ▾, 40.4 Bed ▾) used on villagers who hold
 * other roles, every change through the hall's real menu clicks: the village's Steward retrained or given No job (the
 * hall's place freed for a new Steward), a Legend (whose job can't be changed), a retired elder (never re-employed,
 * 34.20), No job then a job again (nothing charged, the block free for the next), a worker's bed kept through a job
 * change, a swap with a partner asleep in his bed (woken first), a sleeper moved to a free bed, a dead villager's bed
 * given to the next, a settlers' wagon bed left off the list, and a price paid from emeralds split over stacks.
 */
public class QaHallRoles1103cGameTests {
	private static final String AREA = "aliveworkplace_test:huge_area";
	private static final BlockPos HALL = new BlockPos(15, 2, 15);
	private static final BlockPos LECTERN = new BlockPos(6, 2, 6);
	private static final BlockPos STONECUTTER = new BlockPos(22, 2, 22);
	private static final BlockPos NEAR = new BlockPos(10, 2, 10);
	private static final BlockPos FAR = new BlockPos(20, 2, 18);
	private static final BlockPos THIRD = new BlockPos(18, 2, 8);
	private static final ResourceLocation OLD_SAGE = ResourceLocation.fromNamespaceAndPath("aliveworkplace", "old_sage");

	private static Villager librarian(GameTestHelper helper, String name, int level) {
		helper.setBlock(LECTERN, Blocks.LECTERN);
		Villager v = QaHallScreenGameTests.villager(helper, name, LECTERN.offset(1, 0, 1));
		Jobs.employ(helper.getLevel(), v, helper.absolutePos(LECTERN), PoiTypes.LIBRARIAN, VillagerProfession.LIBRARIAN);
		v.setVillagerData(v.getVillagerData().setLevel(level));
		v.setVillagerXp(level == 1 ? 5 : 70 * level);
		return v;
	}

	private static int emeralds(ServerPlayer player) {
		return player.getInventory().countItem(Items.EMERALD);
	}

	private static BlockPos home(Villager v) {
		return v.getBrain().getMemory(MemoryModuleType.HOME).map(GlobalPos::pos).orElse(null);
	}

	private static CompoundTag row(GameTestHelper helper, ChoiceMenu menu, Villager v) {
		return HallPeople.row(menu.icon(QaHallScreenGameTests.slotOf(helper, menu, v)));
	}

	private static List<ItemStack> bedDropdown(GameTestHelper helper, ServerPlayer player, ChoiceMenu menu, Villager v) {
		helper.assertTrue(menu.clickMenuButton(player, HallBeds.OPEN + QaHallScreenGameTests.slotOf(helper, menu, v)), "Bed ▾ wasn't taken");
		return HallBeds.lines(menu.icon(QaHallScreenGameTests.slotOf(helper, menu, v)));
	}

	private static String state(ItemStack line) {
		return HallBeds.mark(line).getString("state");
	}

	private static ItemStack firstBed(GameTestHelper helper, List<ItemStack> lines, String state) {
		for (ItemStack line : lines) {
			if (state(line).equals(state)) {
				return line;
			}
		}
		helper.fail("no " + state + " line in " + names(lines));
		return ItemStack.EMPTY;
	}

	private static List<String> names(List<ItemStack> lines) {
		return lines.stream().map(l -> l.getHoverName().getString()).toList();
	}

	private static void pickBed(GameTestHelper helper, ServerPlayer player, ChoiceMenu menu, ItemStack line) {
		helper.assertTrue(menu.clickMenuButton(player, HallBeds.PICK + HallBeds.mark(line).getInt("id")), "the pick wasn't taken");
	}

	private static int free(GameTestHelper helper, BlockPos absolute) {
		return helper.getLevel().getPoiManager().getFreeTickets(absolute);
	}

	private static boolean occupied(GameTestHelper helper, BlockPos absolute) {
		var s = helper.getLevel().getBlockState(absolute);
		return s.hasProperty(BedBlock.OCCUPIED) && s.getValue(BedBlock.OCCUPIED);
	}

	// --- Job ▾ on villagers with roles (40.3) ---------------------------------------------------------------------------

	/**
	 * The Steward given No job (two clicks, free): jobless, nothing paid, the hall's place free again; a Journeyman Builder
	 * can then be named Steward by the hall's own sneak-click with a City Plan.
	 */
	//$ gametest_ticks_batch AREA '200' '"qaHrStewardNoJob"'
	@GameTest(template = AREA, timeoutTicks = 200, batch = "qaHrStewardNoJob")
	public void theStewardGivenNoJobLetsANewOneBeNamed(GameTestHelper helper) {
		QaHallScreenGameTests.village(helper);
		ServerLevel level = helper.getLevel();
		BlockPos hall = helper.absolutePos(HALL);
		Villager sam = QaHallScreenGameTests.villager(helper, "Sam", new BlockPos(13, 2, 15));
		List<String> seen = new ArrayList<>();
		ServerPlayer player = QaHallScreenGameTests.payer(helper, seen, 7);
		helper.runAfterDelay(3, () -> {
			io.github.jcondedata.aliveworkplace.work.Stations.assign(level, sam, hall, ModVillagers.STEWARD);
			sam.setVillagerData(sam.getVillagerData().setProfession(ModVillagers.STEWARD).setLevel(3));
			helper.assertTrue(Stewards.stewardOf(level, hall) == sam, "setup: Sam isn't the Steward");
			ChoiceMenu menu = QaHallScreenGameTests.openPeople(helper, player);
			QaHallScreenGameTests.jobDropdown(helper, player, menu, sam);
			QaHallScreenGameTests.pickJob(helper, player, menu, VillagerProfession.NONE);
			helper.assertTrue(Stewards.isSteward(sam), "the first click of No job took the job");
			QaHallScreenGameTests.pickJob(helper, player, menu, VillagerProfession.NONE);
			helper.assertTrue(sam.getVillagerData().getProfession() == VillagerProfession.NONE && QaHallScreenGameTests.site(sam).isEmpty(),
				"still working: " + sam.getVillagerData() + " at " + QaHallScreenGameTests.site(sam));
			helper.assertTrue(emeralds(player) == 7, "No job cost " + (7 - emeralds(player)));
			helper.assertTrue(seen.contains("Sam gave up the Steward job and has no job now."), "told: " + seen);
			helper.assertTrue(Stewards.stewardOf(level, hall) == null && free(helper, hall) == 1, "the hall's place is still held: " + free(helper, hall));
			helper.succeed();
		});
	}

	/**
	 * A Legend's job can't be changed (wiki: "Children, nitwits and Legends get a grayed Job ▾"): his row offers no
	 * change, Job ▾ says so and opens nothing, and forged picks of a job or No job change nothing and take no money.
	 */
	//$ gametest_ticks_batch AREA '200' '"qaHrLegend"'
	@GameTest(template = AREA, timeoutTicks = 200, batch = "qaHrLegend")
	public void aLegendsJobCantBeChanged(GameTestHelper helper) {
		QaHallScreenGameTests.village(helper);
		helper.setBlock(STONECUTTER, Blocks.STONECUTTER);
		ServerLevel level = helper.getLevel();
		Villager odo = librarian(helper, "Odo", 5);
		List<String> seen = new ArrayList<>();
		ServerPlayer player = QaHallScreenGameTests.payer(helper, seen, 64);
		helper.runAfterDelay(3, () -> {
			ModAttachments.LEGEND.set(odo, LegendData.settled(OLD_SAGE, "", Optional.of(helper.absolutePos(HALL)), Chronicle.day(level) - 1, "test"));
			helper.assertTrue(Legends.of(odo).isPresent(), "setup: the Legend didn't load");
			ChoiceMenu menu = QaHallScreenGameTests.openPeople(helper, player);
			helper.assertTrue(row(helper, menu, odo).getString("job_action").isEmpty(), "a Legend's row offers a change: " + row(helper, menu, odo));
			helper.assertTrue(menu.clickMenuButton(player, HallJobs.OPEN + QaHallScreenGameTests.slotOf(helper, menu, odo)), "Job ▾ refused outright");
			helper.assertTrue(!HallJobs.isOpen(menu, odo) && !row(helper, menu, odo).getBoolean("jobs_open"), "a Legend's list opened");
			helper.assertTrue(seen.contains("Odo's job can't be changed here."), "not told: " + seen);
			for (int i = 0; i < 2; i++) {
				QaHallScreenGameTests.pickJob(helper, player, menu, VillagerProfession.MASON);
				QaHallScreenGameTests.pickJob(helper, player, menu, VillagerProfession.NONE);
			}
			helper.assertTrue(odo.getVillagerData().getProfession() == VillagerProfession.LIBRARIAN && odo.getVillagerData().getLevel() == 5
				&& QaHallScreenGameTests.site(odo).equals(Optional.of(helper.absolutePos(LECTERN))), "the Legend changed: " + odo.getVillagerData());
			helper.assertTrue(emeralds(player) == 64 && free(helper, helper.absolutePos(STONECUTTER)) == 1, "money or the stonecutter taken");
			helper.assertTrue(Legends.of(odo).isPresent(), "no longer a Legend");
			helper.succeed();
		});
	}

	/**
	 * No job, then a job again: No job is free (nothing charged on either click), the lectern is let go so a jobless
	 * neighbour's list offers it and one click makes her the Librarian for nothing; Dara's own list then has no free
	 * lectern, and her next job (Mason) is given for nothing too, as a jobless villager's first job is. Money untouched.
	 */
	//$ gametest_ticks_batch AREA '200' '"qaHrNoJobThenJob"'
	@GameTest(template = AREA, timeoutTicks = 200, batch = "qaHrNoJobThenJob")
	public void noJobThenAJobAgainChargesNothing(GameTestHelper helper) {
		QaHallScreenGameTests.village(helper);
		helper.setBlock(STONECUTTER, Blocks.STONECUTTER);
		Villager dara = librarian(helper, "Dara", 3);
		Villager ivy = QaHallScreenGameTests.villager(helper, "Ivy", new BlockPos(12, 2, 15));
		List<String> seen = new ArrayList<>();
		ServerPlayer player = QaHallScreenGameTests.payer(helper, seen, 20);
		BlockPos lectern = helper.absolutePos(LECTERN);
		helper.runAfterDelay(3, () -> {
			ChoiceMenu menu = QaHallScreenGameTests.openPeople(helper, player);
			QaHallScreenGameTests.jobDropdown(helper, player, menu, dara);
			QaHallScreenGameTests.pickJob(helper, player, menu, VillagerProfession.NONE);
			QaHallScreenGameTests.pickJob(helper, player, menu, VillagerProfession.NONE);
			helper.assertTrue(dara.getVillagerData().getProfession() == VillagerProfession.NONE && emeralds(player) == 20,
				"No job: " + dara.getVillagerData() + ", " + emeralds(player) + " left");
			helper.assertTrue(free(helper, lectern) == 1, "the lectern wasn't let go");
			// Ivy, jobless, is offered the lectern and takes it in one click, for nothing.
			QaHallScreenGameTests.jobDropdown(helper, player, menu, ivy);
			ItemStack line = QaHallScreenGameTests.jobLine(helper, HallJobs.lines(menu.icon(QaHallScreenGameTests.slotOf(helper, menu, ivy))), VillagerProfession.LIBRARIAN);
			helper.assertTrue(HallJobs.mark(line).getString("state").equals("free"), "the lectern isn't free on Ivy's list: " + line.getHoverName().getString());
			QaHallScreenGameTests.pickJob(helper, player, menu, VillagerProfession.LIBRARIAN);
			helper.assertTrue(ivy.getVillagerData().getProfession() == VillagerProfession.LIBRARIAN && QaHallScreenGameTests.site(ivy).equals(Optional.of(lectern)),
				"Ivy: " + ivy.getVillagerData() + " at " + QaHallScreenGameTests.site(ivy));
			helper.assertTrue(free(helper, lectern) == 0 && emeralds(player) == 20, "the lectern or the money");
			// Dara again: the lectern is gone from her free jobs, the stonecutter is hers for nothing.
			QaHallScreenGameTests.jobDropdown(helper, player, menu, dara);
			ItemStack librarianLine = QaHallScreenGameTests.jobLine(helper, HallJobs.lines(menu.icon(QaHallScreenGameTests.slotOf(helper, menu, dara))), VillagerProfession.LIBRARIAN);
			helper.assertTrue(!HallJobs.mark(librarianLine).getString("state").equals("free"), "a taken lectern offered: " + librarianLine.getHoverName().getString());
			QaHallScreenGameTests.pickJob(helper, player, menu, VillagerProfession.LIBRARIAN);
			helper.assertTrue(dara.getVillagerData().getProfession() == VillagerProfession.NONE && QaHallScreenGameTests.site(ivy).equals(Optional.of(lectern)),
				"Dara took Ivy's lectern");
			QaHallScreenGameTests.pickJob(helper, player, menu, VillagerProfession.MASON);
			helper.assertTrue(dara.getVillagerData().getProfession() == VillagerProfession.MASON && dara.getVillagerData().getLevel() == 1
				&& QaHallScreenGameTests.site(dara).equals(Optional.of(helper.absolutePos(STONECUTTER))), "Dara: " + dara.getVillagerData());
			helper.assertTrue(emeralds(player) == 20, "a jobless villager's job cost " + (20 - emeralds(player)));
			helper.succeed();
		});
	}

	/** A price paid from emeralds over several stacks: 2 + 2 is short for a Novice (5, nothing changes), 2 + 2 + 1 pays and empties all three. */
	//$ gametest_ticks_batch AREA '200' '"qaHrSplitStacks"'
	@GameTest(template = AREA, timeoutTicks = 200, batch = "qaHrSplitStacks")
	public void thePriceIsPaidFromSplitStacks(GameTestHelper helper) {
		QaHallScreenGameTests.village(helper);
		helper.setBlock(STONECUTTER, Blocks.STONECUTTER);
		Villager dara = librarian(helper, "Dara", 1);
		List<String> seen = new ArrayList<>();
		ServerPlayer player = QaHallScreenGameTests.payer(helper, seen, 0);
		player.getInventory().setItem(9, new ItemStack(Items.EMERALD, 2));
		player.getInventory().setItem(20, new ItemStack(Items.EMERALD, 2));
		helper.runAfterDelay(3, () -> {
			ChoiceMenu menu = QaHallScreenGameTests.openPeople(helper, player);
			QaHallScreenGameTests.jobDropdown(helper, player, menu, dara);
			QaHallScreenGameTests.pickJob(helper, player, menu, VillagerProfession.MASON);
			QaHallScreenGameTests.pickJob(helper, player, menu, VillagerProfession.MASON);
			helper.assertTrue(dara.getVillagerData().getProfession() == VillagerProfession.LIBRARIAN && emeralds(player) == 4,
				"short by one: " + dara.getVillagerData() + ", " + emeralds(player) + " left");
			helper.assertTrue(seen.stream().anyMatch(s -> s.contains("1 emerald")), "not told how short: " + seen);
			player.getInventory().setItem(30, new ItemStack(Items.EMERALD, 1));
			QaHallScreenGameTests.pickJob(helper, player, menu, VillagerProfession.MASON);
			QaHallScreenGameTests.pickJob(helper, player, menu, VillagerProfession.MASON);
			helper.assertTrue(dara.getVillagerData().getProfession() == VillagerProfession.MASON && emeralds(player) == 0,
				"with 2 + 2 + 1: " + dara.getVillagerData() + ", " + emeralds(player) + " left");
			helper.succeed();
		});
	}

	/** A job change keeps the bed: a Librarian with a bed retrained as a Mason, then given No job, sleeps in the same bed, still claimed and counted housed. */
	//$ gametest_ticks_batch AREA '200' '"qaHrJobKeepsBed"'
	@GameTest(template = AREA, timeoutTicks = 200, batch = "qaHrJobKeepsBed")
	public void aJobChangeKeepsTheirBed(GameTestHelper helper) {
		QaHallScreenGameTests.village(helper);
		helper.setBlock(STONECUTTER, Blocks.STONECUTTER);
		BlockPos near = QaHallScreenGameTests.bed(helper, NEAR);
		Villager dara = librarian(helper, "Dara", 2);
		List<String> seen = new ArrayList<>();
		ServerPlayer player = QaHallScreenGameTests.payer(helper, seen, 10);
		ServerLevel level = helper.getLevel();
		helper.runAfterDelay(3, () -> {
			helper.assertTrue(HallBeds.moveIn(level, dara, near), "setup: no bed");
			ChoiceMenu menu = QaHallScreenGameTests.openPeople(helper, player);
			QaHallScreenGameTests.jobDropdown(helper, player, menu, dara);
			QaHallScreenGameTests.pickJob(helper, player, menu, VillagerProfession.MASON);
			QaHallScreenGameTests.pickJob(helper, player, menu, VillagerProfession.MASON);
			helper.assertTrue(dara.getVillagerData().getProfession() == VillagerProfession.MASON, "not retrained: " + dara.getVillagerData());
			helper.assertTrue(near.equals(VillageNeeds.bed(level, dara)) && free(helper, near) == 0, "retraining lost her bed: " + home(dara));
			QaHallScreenGameTests.jobDropdown(helper, player, menu, dara);
			QaHallScreenGameTests.pickJob(helper, player, menu, VillagerProfession.NONE);
			QaHallScreenGameTests.pickJob(helper, player, menu, VillagerProfession.NONE);
			helper.assertTrue(dara.getVillagerData().getProfession() == VillagerProfession.NONE, "No job didn't take: " + dara.getVillagerData());
			helper.assertTrue(near.equals(VillageNeeds.bed(level, dara)) && free(helper, near) == 0, "No job lost her bed: " + home(dara));
			helper.assertTrue(VillageNeeds.count(level, helper.absolutePos(HALL)).housed() == 1, "housed: " + VillageNeeds.count(level, helper.absolutePos(HALL)).housed());
			helper.succeed();
		});
	}

	// --- Bed ▾ (40.4) -----------------------------------------------------------------------------------------------------

	/**
	 * A swap whose partner is asleep in his bed at that moment ("Anyone asleep is woken first"): Jem wakes, his bed is no
	 * longer marked occupied, and both have the other's bed, both still claimed.
	 */
	//$ gametest_ticks_batch AREA '200' '"qaHrSwapAsleep"'
	@GameTest(template = AREA, timeoutTicks = 200, batch = "qaHrSwapAsleep")
	public void aSwapWakesAPartnerAsleepInHisBed(GameTestHelper helper) {
		QaHallScreenGameTests.village(helper);
		BlockPos near = QaHallScreenGameTests.bed(helper, NEAR);
		BlockPos far = QaHallScreenGameTests.bed(helper, FAR);
		Villager dara = QaHallScreenGameTests.villager(helper, "Dara", new BlockPos(12, 2, 15));
		Villager jem = QaHallScreenGameTests.villager(helper, "Jem", new BlockPos(18, 2, 15));
		List<String> seen = new ArrayList<>();
		ServerPlayer player = QaHallScreenGameTests.payer(helper, seen, 0);
		ServerLevel level = helper.getLevel();
		helper.runAfterDelay(3, () -> {
			helper.assertTrue(HallBeds.moveIn(level, dara, near) && HallBeds.moveIn(level, jem, far), "setup: no beds");
			jem.startSleeping(far);
			helper.assertTrue(jem.isSleeping() && occupied(helper, far), "setup: Jem isn't asleep");
			ChoiceMenu menu = QaHallScreenGameTests.openPeople(helper, player);
			ItemStack swap = firstBed(helper, bedDropdown(helper, player, menu, dara), "swap");
			pickBed(helper, player, menu, swap);
			helper.assertTrue(seen.contains("Dara and Jem swapped beds."), "not told: " + seen);
			helper.assertTrue(!jem.isSleeping(), "Jem sleeps on in the bed that's Dara's now");
			helper.assertTrue(!occupied(helper, far), "Dara's new bed is still marked occupied");
			helper.assertTrue(far.equals(VillageNeeds.bed(level, dara)) && near.equals(VillageNeeds.bed(level, jem)), "beds: Dara " + home(dara) + ", Jem " + home(jem));
			helper.assertTrue(free(helper, near) == 0 && free(helper, far) == 0, "a swapped bed left free");
			helper.succeed();
		});
	}

	/** A villager asleep in her bed given a free one: she wakes, her old bed is free (claimable, not marked occupied) and the new one is hers. */
	//$ gametest_ticks_batch AREA '200' '"qaHrMoveAsleep"'
	@GameTest(template = AREA, timeoutTicks = 200, batch = "qaHrMoveAsleep")
	public void aSleeperGivenAFreeBedLeavesHerOldOneFree(GameTestHelper helper) {
		QaHallScreenGameTests.village(helper);
		BlockPos near = QaHallScreenGameTests.bed(helper, NEAR);
		BlockPos third = QaHallScreenGameTests.bed(helper, THIRD);
		Villager dara = QaHallScreenGameTests.villager(helper, "Dara", new BlockPos(12, 2, 15));
		List<String> seen = new ArrayList<>();
		ServerPlayer player = QaHallScreenGameTests.payer(helper, seen, 0);
		ServerLevel level = helper.getLevel();
		helper.runAfterDelay(3, () -> {
			helper.assertTrue(HallBeds.moveIn(level, dara, near), "setup: no bed");
			dara.startSleeping(near);
			ChoiceMenu menu = QaHallScreenGameTests.openPeople(helper, player);
			pickBed(helper, player, menu, firstBed(helper, bedDropdown(helper, player, menu, dara), "free"));
			helper.assertTrue(third.equals(VillageNeeds.bed(level, dara)) && free(helper, third) == 0, "not moved: " + home(dara));
			helper.assertTrue(!dara.isSleeping() && !occupied(helper, near), "still asleep in the old bed: " + dara.isSleeping());
			helper.assertTrue(free(helper, near) == 1, "her old bed wasn't freed");
			helper.assertTrue(seen.stream().anyMatch(s -> s.startsWith("Dara sleeps at ")), "not told: " + seen);
			helper.succeed();
		});
	}

	/** A bed given from the table, then its sleeper dies: the bed is free again, and the next villager's list offers it and gives it to him. */
	//$ gametest_ticks_batch AREA '200' '"qaHrDeadBed"'
	@GameTest(template = AREA, timeoutTicks = 200, batch = "qaHrDeadBed")
	public void aDeadVillagersBedGoesToTheNext(GameTestHelper helper) {
		QaHallScreenGameTests.village(helper);
		BlockPos near = QaHallScreenGameTests.bed(helper, NEAR);
		Villager dara = QaHallScreenGameTests.villager(helper, "Dara", new BlockPos(12, 2, 15));
		Villager jem = QaHallScreenGameTests.villager(helper, "Jem", new BlockPos(18, 2, 15));
		List<String> seen = new ArrayList<>();
		ServerPlayer player = QaHallScreenGameTests.payer(helper, seen, 0);
		ServerLevel level = helper.getLevel();
		helper.runAfterDelay(3, () -> {
			ChoiceMenu menu = QaHallScreenGameTests.openPeople(helper, player);
			pickBed(helper, player, menu, firstBed(helper, bedDropdown(helper, player, menu, dara), "free"));
			helper.assertTrue(near.equals(VillageNeeds.bed(level, dara)), "setup: Dara got no bed");
			List<ItemStack> jems = bedDropdown(helper, player, menu, jem);
			helper.assertTrue(jems.stream().noneMatch(l -> state(l).equals("free")), "Dara's bed offered as free: " + names(jems));
			dara.kill();
			helper.runAfterDelay(2, () -> {
				helper.assertTrue(free(helper, near) == 1, "the dead villager's bed is still claimed");
				ChoiceMenu again = QaHallScreenGameTests.openPeople(helper, player);
				pickBed(helper, player, again, firstBed(helper, bedDropdown(helper, player, again, jem), "free"));
				helper.assertTrue(near.equals(VillageNeeds.bed(level, jem)) && free(helper, near) == 0, "Jem didn't get the bed: " + home(jem));
				helper.succeed();
			});
		});
	}

	/**
	 * A settlers' wagon bed in the village isn't on the list (40.4: "a wagon's bedrolls are no homes"); the village's red
	 * bed is the only bed line, and the list's last line says what's left out.
	 */
	//$ gametest_ticks_batch AREA '200' '"qaHrWagonBed"'
	@GameTest(template = AREA, timeoutTicks = 200, batch = "qaHrWagonBed")
	public void aWagonBedIsNotOffered(GameTestHelper helper) {
		QaHallScreenGameTests.village(helper);
		BlockPos near = QaHallScreenGameTests.bed(helper, NEAR);
		helper.setBlock(THIRD, Blocks.BROWN_BED.defaultBlockState().setValue(BedBlock.FACING, Direction.SOUTH).setValue(BedBlock.PART, BedPart.FOOT));
		helper.setBlock(THIRD.south(), Blocks.BROWN_BED.defaultBlockState().setValue(BedBlock.FACING, Direction.SOUTH).setValue(BedBlock.PART, BedPart.HEAD));
		BlockPos wagon = helper.absolutePos(THIRD.south());
		Leftovers.after(helper, () -> {
			helper.setBlock(THIRD.south(), Blocks.AIR);
			helper.setBlock(THIRD, Blocks.AIR);
		});
		Villager dara = QaHallScreenGameTests.villager(helper, "Dara", new BlockPos(12, 2, 15));
		List<String> seen = new ArrayList<>();
		ServerPlayer player = QaHallScreenGameTests.payer(helper, seen, 0);
		ServerLevel level = helper.getLevel();
		helper.runAfterDelay(3, () -> {
			CampBeds.record(level, BoundingBox.fromCorners(helper.absolutePos(THIRD), wagon));
			helper.assertTrue(CampBeds.isWagonBed(level, wagon), "setup: the brown bed isn't a wagon bed");
			ChoiceMenu menu = QaHallScreenGameTests.openPeople(helper, player);
			List<ItemStack> lines = bedDropdown(helper, player, menu, dara);
			long beds = lines.stream().filter(l -> List.of("free", "swap", "taken", "current").contains(state(l))).count();
			helper.assertTrue(beds == 1, "bed lines: " + names(lines));
			pickBed(helper, player, menu, firstBed(helper, lines, "free"));
			helper.assertTrue(near.equals(VillageNeeds.bed(level, dara)) && !wagon.equals(home(dara)),"the wagon bed was given: " + home(dara));
			helper.assertTrue(lines.get(lines.size() - 1).getHoverName().getString().equals(
				Component.translatable("screen.aliveworkplace.hall.bed_list.note").getString()) && state(lines.get(lines.size() - 1)).equals("note"), "the last line: " + names(lines));
			helper.succeed();
		});
	}

	static int professionId(VillagerProfession job) {
		return BuiltInRegistries.VILLAGER_PROFESSION.getId(job);
	}
}
