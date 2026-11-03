package io.github.jcondedata.aliveworkplace.test;

import io.github.jcondedata.aliveworkplace.city.CityPlanItem;
import io.github.jcondedata.aliveworkplace.city.Stewards;
import io.github.jcondedata.aliveworkplace.guard.Mercenaries;
import io.github.jcondedata.aliveworkplace.guard.Relief;
import io.github.jcondedata.aliveworkplace.guard.Scouts;
import io.github.jcondedata.aliveworkplace.hall.Chronicle;
import io.github.jcondedata.aliveworkplace.hall.HallBeds;
import io.github.jcondedata.aliveworkplace.hall.HallJobs;
import io.github.jcondedata.aliveworkplace.hall.HallPeople;
import io.github.jcondedata.aliveworkplace.hall.HallSections;
import io.github.jcondedata.aliveworkplace.hall.VillageHallBlockEntity;
import io.github.jcondedata.aliveworkplace.hall.VillageHalls;
import io.github.jcondedata.aliveworkplace.hall.VillageQuests;
import io.github.jcondedata.aliveworkplace.people.Retirement;
import io.github.jcondedata.aliveworkplace.registry.ModAttachments;
import io.github.jcondedata.aliveworkplace.registry.ModBlocks;
import io.github.jcondedata.aliveworkplace.registry.ModItems;
import io.github.jcondedata.aliveworkplace.registry.ModVillagers;
import io.github.jcondedata.aliveworkplace.story.ArcState;
import io.github.jcondedata.aliveworkplace.story.Arcs;
import io.github.jcondedata.aliveworkplace.story.Friendship;
import io.github.jcondedata.aliveworkplace.story.PersonalRequests;
import io.github.jcondedata.aliveworkplace.story.Quest;
import io.github.jcondedata.aliveworkplace.story.QuestFiles;
import io.github.jcondedata.aliveworkplace.story.SicknessStory;
import io.github.jcondedata.aliveworkplace.story.Stories;
import io.github.jcondedata.aliveworkplace.work.ChoiceMenu;
import io.github.jcondedata.aliveworkplace.work.Jobs;
import io.github.jcondedata.aliveworkplace.work.Stations;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.ai.village.poi.PoiTypes;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.npc.VillagerProfession;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;

/**
 * QA (lane-c, 2026-11-03): independent checks of the People table fixes B885 (a retired elder offered a job), B886 (a
 * retrained Steward kept the City Plan), B889 (visitors listed as jobless), B890 (a jobless friend's "Next level"
 * request left to fail) and B891 (a retrained scout's spyglass left raised), written from the bug texts and through the
 * hall's own menu clicks, beyond the original repro tests: other entry points (the jobless villager's job page, a list
 * opened before the change, forged picks), save and reload in between, more than one helper, and the controls beside.
 */
public class QaHallFixes1103cGameTests {
	private static final String AREA = "aliveworkplace_test:huge_area";
	private static final BlockPos HALL = new BlockPos(15, 2, 15);
	private static final BlockPos LECTERN = new BlockPos(6, 2, 6);
	private static final BlockPos STONECUTTER = new BlockPos(22, 2, 22);
	private static final String RETIRED_LINE = " has retired and takes no job now: they mentor the young instead.";

	// --- helpers ------------------------------------------------------------------------------------------------------

	private static int emeralds(ServerPlayer player) {
		return player.getInventory().countItem(Items.EMERALD);
	}

	private static int free(GameTestHelper helper, BlockPos relative) {
		return helper.getLevel().getPoiManager().getFreeTickets(helper.absolutePos(relative));
	}

	private static CompoundTag row(GameTestHelper helper, ChoiceMenu menu, Villager v) {
		return HallPeople.row(menu.icon(QaHallScreenGameTests.slotOf(helper, menu, v)));
	}

	private static boolean listed(ChoiceMenu menu, Villager v) {
		for (int slot = 0; slot < ChoiceMenu.SIZE; slot++) {
			CompoundTag row = HallPeople.row(menu.icon(slot));
			if (row != null && row.getString("uuid").equals(v.getStringUUID())) {
				return true;
			}
		}
		return false;
	}

	private static int id(VillagerProfession job) {
		return BuiltInRegistries.VILLAGER_PROFESSION.getId(job);
	}

	private static List<String> lore(ItemStack stack) {
		var lore = stack.get(net.minecraft.core.component.DataComponents.LORE);
		return lore == null ? List.of() : lore.lines().stream().map(Component::getString).toList();
	}

	/** Every {@code item} {@code v} has: equipment, its inventory, and dropped within 40 blocks of the test. */
	private static int owned(GameTestHelper helper, Villager v, Item item) {
		int n = 0;
		for (EquipmentSlot slot : EquipmentSlot.values()) {
			if (v.getItemBySlot(slot).is(item)) {
				n += v.getItemBySlot(slot).getCount();
			}
		}
		n += v.getInventory().countItem(item);
		AABB box = new AABB(helper.absolutePos(BlockPos.ZERO)).inflate(40);
		for (ItemEntity drop : helper.getLevel().getEntitiesOfClass(ItemEntity.class, box)) {
			if (drop.getItem().is(item)) {
				n += drop.getItem().getCount();
			}
		}
		return n;
	}

	/** As a save and load of the world would bring a villager back: its save read into a new villager, the old one gone. */
	private static Villager reloaded(GameTestHelper helper, Villager v) {
		CompoundTag tag = v.saveWithoutId(new CompoundTag());
		v.discard();
		Villager copy = EntityType.VILLAGER.create(helper.getLevel());
		copy.load(tag);
		helper.getLevel().addFreshEntity(copy);
		return copy;
	}

	/** The stories (and so the open requests) saved and loaded again, as a server restart does. */
	private static void reloadStories(ServerLevel level) {
		Stories.Data loaded = Stories.Data.load(Stories.Data.get(level).save(new CompoundTag(), level.registryAccess()), level.registryAccess());
		level.getDataStorage().set("aliveworkplace_stories", loaded);
	}

	private static Villager librarian(GameTestHelper helper, String name) {
		helper.setBlock(LECTERN, Blocks.LECTERN);
		Villager v = QaHallScreenGameTests.villager(helper, name, LECTERN.offset(1, 0, 1));
		Jobs.employ(helper.getLevel(), v, helper.absolutePos(LECTERN), PoiTypes.LIBRARIAN, VillagerProfession.LIBRARIAN);
		v.setVillagerXp(5);
		return v;
	}

	private static boolean open(ServerLevel level, BlockPos hall, UUID quest) {
		return Stories.open(level, hall).stream().anyMatch(q -> q.id.equals(quest));
	}

	private static void twoClicks(GameTestHelper helper, ServerPlayer player, ChoiceMenu menu, Villager v, VillagerProfession job) {
		QaHallScreenGameTests.jobDropdown(helper, player, menu, v);
		QaHallScreenGameTests.pickJob(helper, player, menu, job);
		QaHallScreenGameTests.pickJob(helper, player, menu, job);
	}

	// --- B889: visitors aren't the village's jobless ------------------------------------------------------------------

	/**
	 * A hired band of three mercenaries and an allied village's relief rider stand by the hall beside Pip, an ordinary
	 * jobless villager. The People header counts one jobless (Pip), none of the four visitors is listed as jobless or
	 * offered a job; Job ▾ on a mercenary says his job can't be changed here, and a forged Mason pick after it changes
	 * nothing, pays nothing and leaves the stonecutter free. Pip, beside them, still gets the Mason job for nothing.
	 */
	//$ gametest_ticks_batch AREA '200' '"qaHfVisitors"'
	@GameTest(template = AREA, timeoutTicks = 200, batch = "qaHfVisitors")
	public void visitorsAreNotCountedJoblessButTheirJoblessNeighbourIs(GameTestHelper helper) {
		QaHallScreenGameTests.village(helper);
		helper.setBlock(STONECUTTER, Blocks.STONECUTTER);
		ServerLevel level = helper.getLevel();
		BlockPos hall = helper.absolutePos(HALL);
		int edge = Relief.EDGE;
		Relief.EDGE = 5;
		Leftovers.after(helper, () -> {
			Relief.EDGE = edge;
			Relief.forget();
		});
		Villager pip = QaHallScreenGameTests.villager(helper, "Pip", new BlockPos(13, 2, 18));
		List<String> seen = new ArrayList<>();
		ServerPlayer player = QaHallScreenGameTests.payer(helper, seen, 30);
		helper.runAfterDelay(3, () -> {
			Mercenaries.hire(level, hall, player);
			List<Villager> band = Mercenaries.near(level, hall);
			Relief.arrive(level, new Relief.Band(GlobalPos.of(level.dimension(), hall), GlobalPos.of(level.dimension(), hall.offset(200, 0, 0)),
				Component.literal("Oakford"), 1, level.getGameTime(), level.getGameTime(), false));
			List<Villager> riders = Relief.riders(level, hall);
			Leftovers.after(helper, () -> {
				band.forEach(Villager::discard);
				riders.forEach(r -> {
					if (r.getVehicle() != null) {
						r.getVehicle().discard();
					}
					r.discard();
				});
			});
			helper.assertTrue(band.size() == 3 && riders.size() == 1, "setup: band " + band.size() + ", riders " + riders.size());
			int paid = emeralds(player);
			ChoiceMenu menu = QaHallScreenGameTests.openPeople(helper, player);
			helper.assertTrue(menu.state(HallSections.PEOPLE_JOBLESS) == 1,
				"the People header says " + menu.state(HallSections.PEOPLE_JOBLESS) + " jobless with 3 mercenaries, a relief rider and one jobless villager (Pip)");
			List<Villager> visitors = new ArrayList<>(band);
			visitors.addAll(riders);
			for (Villager v : visitors) {
				CompoundTag r = row(helper, menu, v);
				helper.assertTrue(!r.getString("kind").equals("jobless") && r.getString("job_action").isEmpty(),
					"a visitor (" + (Relief.isRider(v) ? "relief rider" : "mercenary") + ") is listed as '" + r.getString("kind") + "' with Job ▾ '"
						+ r.getString("job_action") + "'");
				helper.assertTrue(!r.getString("job").equals("screen.aliveworkplace.hall.people.no_job"), "a visitor's Job column reads No job: " + r);
			}
			Villager merc = band.get(0);
			seen.clear();
			menu.clickMenuButton(player, HallJobs.OPEN + QaHallScreenGameTests.slotOf(helper, menu, merc));
			helper.assertTrue(!row(helper, menu, merc).getBoolean("jobs_open"), "Job ▾ opened a job list for a mercenary");
			helper.assertTrue(seen.contains("Mercenary's job can't be changed here."), "Job ▾ on a mercenary told the player: " + seen);
			// A forged pick (no list open for him): twice, as a retraining's confirm would be.
			QaHallScreenGameTests.pickJob(helper, player, menu, VillagerProfession.MASON);
			QaHallScreenGameTests.pickJob(helper, player, menu, VillagerProfession.MASON);
			for (Villager v : visitors) {
				helper.assertTrue(v.getVillagerData().getProfession() == ModVillagers.GUARD, "a forged Mason pick made a visitor a " + v.getVillagerData().getProfession());
				helper.assertTrue(!VillageHalls.assign(level, v, new VillageHalls.FreeStation(helper.absolutePos(STONECUTTER), VillagerProfession.MASON,
					BuiltInRegistries.POINT_OF_INTEREST_TYPE.getHolderOrThrow(PoiTypes.MASON))), "the hall handed a visitor the stonecutter");
			}
			helper.assertTrue(free(helper, STONECUTTER) == 1 && emeralds(player) == paid, "after the forged picks: stonecutter free " + free(helper, STONECUTTER)
				+ ", emeralds " + emeralds(player) + " of " + paid);
			// The control: the jobless villager beside them.
			QaHallScreenGameTests.jobDropdown(helper, player, menu, pip);
			QaHallScreenGameTests.pickJob(helper, player, menu, VillagerProfession.MASON);
			helper.assertTrue(pip.getVillagerData().getProfession() == VillagerProfession.MASON && free(helper, STONECUTTER) == 0,
				"Pip (jobless, beside the visitors) wasn't given the Mason job: " + pip.getVillagerData() + "; told " + seen);
			helper.assertTrue(seen.contains("Pip is now a Mason") && emeralds(player) == paid, "Pip's job: told " + seen + ", emeralds " + emeralds(player));
			helper.succeed();
		});
	}

	/**
	 * A mercenary's hire ends (his time is up): he leaves, and the People table no longer lists him or counts him; the
	 * stonecutter nobody gave him stays free and Pip still takes it.
	 */
	//$ gametest_ticks_batch AREA '200' '"qaHfMercGone"'
	@GameTest(template = AREA, timeoutTicks = 200, batch = "qaHfMercGone")
	public void aMercenaryWhoseHireEndsLeavesTheTable(GameTestHelper helper) {
		QaHallScreenGameTests.village(helper);
		helper.setBlock(STONECUTTER, Blocks.STONECUTTER);
		ServerLevel level = helper.getLevel();
		BlockPos hall = helper.absolutePos(HALL);
		Villager pip = QaHallScreenGameTests.villager(helper, "Pip", new BlockPos(13, 2, 18));
		List<String> seen = new ArrayList<>();
		ServerPlayer player = QaHallScreenGameTests.payer(helper, seen, 12);
		helper.runAfterDelay(3, () -> {
			Mercenaries.hire(level, hall, player);
			List<Villager> band = Mercenaries.near(level, hall);
			Leftovers.after(helper, () -> band.forEach(Villager::discard));
			helper.assertTrue(band.size() == 3, "setup: band " + band.size());
			ChoiceMenu before = QaHallScreenGameTests.openPeople(helper, player);
			QaHallScreenGameTests.jobDropdown(helper, player, before, pip); // a list left open on the screen while the band leaves
			player.closeContainer();
			band.forEach(m -> ModAttachments.MERCENARY_UNTIL.set(m, level.getGameTime()));
			helper.runAfterDelay(3, () -> {
				helper.assertTrue(band.stream().allMatch(Villager::isRemoved), "a mercenary whose time is up is still here: "
					+ band.stream().map(m -> m.isRemoved() ? "gone" : "here").toList());
				ChoiceMenu menu = QaHallScreenGameTests.openPeople(helper, player);
				for (Villager m : band) {
					helper.assertTrue(!listed(menu, m), "a mercenary who left is still in the People table");
				}
				helper.assertTrue(menu.state(HallSections.PEOPLE_JOBLESS) == 1 && menu.state(HallSections.PEOPLE_TOTAL) == 1,
					"after the band left the header says " + menu.state(HallSections.PEOPLE_TOTAL) + " people, " + menu.state(HallSections.PEOPLE_JOBLESS) + " jobless");
				helper.assertTrue(free(helper, STONECUTTER) == 1, "the stonecutter is held after the band left");
				QaHallScreenGameTests.jobDropdown(helper, player, menu, pip);
				QaHallScreenGameTests.pickJob(helper, player, menu, VillagerProfession.MASON);
				helper.assertTrue(pip.getVillagerData().getProfession() == VillagerProfession.MASON, "Pip didn't get the Mason job: " + seen);
				helper.succeed();
			});
		});
	}

	/**
	 * Doctor Wren (the Sickness story's visiting physician, a Cleric with no workstation) by the hall: she isn't counted
	 * among the village's jobless, her row offers no job, Job ▾ on her says so, a forged pick changes nothing, the hall
	 * never hands her the free stonecutter, and she stays the arc's healer.
	 */
	//$ gametest_ticks_batch AREA '200' '"qaHfWren"'
	@GameTest(template = AREA, timeoutTicks = 200, batch = "qaHfWren")
	public void doctorWrenIsNotCountedJoblessOrGivenABlock(GameTestHelper helper) {
		Leftovers.clear(helper);
		QaSicknessGameTests.settings(helper);
		HallSections.forget();
		HallJobs.forget();
		HallBeds.forget();
		ServerLevel level = helper.getLevel();
		long time = level.getDayTime();
		Leftovers.after(helper, () -> level.setDayTime(time));
		QaSicknessGameTests.hall(helper);
		BlockPos hall = helper.absolutePos(HALL);
		QaSicknessGameTests.village(helper, 8);
		BlockPos cutter = STONECUTTER.offset(-6, 0, -4);
		helper.setBlock(cutter, Blocks.STONECUTTER);
		List<String> seen = new ArrayList<>();
		ServerPlayer player = QaHallScreenGameTests.payer(helper, seen, 20);
		helper.runAfterDelay(3, () -> {
			long day = Chronicle.day(level) + 1;
			QaSicknessGameTests.setDay(level, day, 1000);
			ArcState s = Arcs.start(level, hall, QaSicknessGameTests.arc(helper));
			helper.assertTrue(s != null, "setup: the arc didn't start");
			QaSicknessGameTests.setDay(level, day + 1, 1000);
			SicknessStory.tick(level);
			Villager wren = SicknessStory.healer(level, s);
			helper.assertTrue(wren != null && wren.getCustomName() != null && wren.getCustomName().getString().equals("Doctor Wren"), "setup: no Doctor Wren");
			VillageHalls.Census census = VillageHalls.census(level, hall);
			helper.assertTrue(!census.jobless().contains(wren), "the hall counts Doctor Wren among the village's jobless");
			ChoiceMenu menu = QaHallScreenGameTests.openPeople(helper, player);
			helper.assertTrue(menu.state(HallSections.PEOPLE_JOBLESS) == census.jobless().size(), "header jobless " + menu.state(HallSections.PEOPLE_JOBLESS));
			CompoundTag r = row(helper, menu, wren);
			helper.assertTrue(!r.getString("kind").equals("jobless") && r.getString("job_action").isEmpty() && r.getString("job").equals("entity.minecraft.villager.cleric"),
				"Doctor Wren's row: " + r);
			seen.clear();
			menu.clickMenuButton(player, HallJobs.OPEN + QaHallScreenGameTests.slotOf(helper, menu, wren));
			helper.assertTrue(seen.contains("Doctor Wren's job can't be changed here."), "Job ▾ on Doctor Wren told the player: " + seen);
			int money = emeralds(player);
			QaHallScreenGameTests.pickJob(helper, player, menu, VillagerProfession.MASON);
			QaHallScreenGameTests.pickJob(helper, player, menu, VillagerProfession.MASON);
			helper.assertTrue(!VillageHalls.assign(level, wren, new VillageHalls.FreeStation(helper.absolutePos(cutter), VillagerProfession.MASON,
				BuiltInRegistries.POINT_OF_INTEREST_TYPE.getHolderOrThrow(PoiTypes.MASON))), "the hall handed Doctor Wren the stonecutter");
			helper.assertTrue(wren.getVillagerData().getProfession() == VillagerProfession.CLERIC && free(helper, cutter) == 1 && emeralds(player) == money,
				"after forged Mason picks Doctor Wren is " + wren.getVillagerData().getProfession() + ", stonecutter free " + free(helper, cutter) + ", emeralds " + emeralds(player));
			helper.assertTrue(SicknessStory.healer(level, s) == wren, "Doctor Wren is no longer the arc's healer");
			helper.succeed();
		});
	}

	// --- B890: a jobless friend's "Next level" request -----------------------------------------------------------------

	/**
	 * Dara asks for "Next level before the festival"; two players take it on. No job from the hall, then the hall's
	 * round: the request is called off, each helper reads the yellow line, nobody loses friendship, Dara isn't let down,
	 * and nothing is missed when its deadline comes.
	 */
	//$ gametest_ticks_batch AREA '200' '"qaHfCallOffTwo"'
	@GameTest(template = AREA, timeoutTicks = 200, batch = "qaHfCallOffTwo")
	public void aJoblessFriendsLevelRequestIsCalledOffForEveryHelper(GameTestHelper helper) {
		QaHallScreenGameTests.village(helper);
		QaHallRolesDuty1103bGameTests.requestsOn(helper);
		ServerLevel level = helper.getLevel();
		BlockPos hall = helper.absolutePos(HALL);
		Villager dara = librarian(helper, "Dara");
		List<String> seen = new ArrayList<>();
		List<String> seen2 = new ArrayList<>();
		ServerPlayer player = QaHallScreenGameTests.payer(helper, seen, 0);
		ServerPlayer second = QaHallScreenGameTests.payer(helper, seen2, 0);
		helper.runAfterDelay(3, () -> {
			player.teleportTo(hall.getX() + 1.5, hall.getY(), hall.getZ() + 0.5);
			second.teleportTo(hall.getX() - 0.5, hall.getY(), hall.getZ() + 0.5);
			Friendship.add(dara, player, 450);
			Friendship.add(dara, second, 450);
			Quest quest = QaHallRolesDuty1103bGameTests.accepted(helper, "level_up", dara, player);
			PersonalRequestGameTests.command(second, "workplace quest accept " + quest.id);
			helper.assertTrue(PersonalRequests.helps(quest, second.getUUID()), "setup: the second player didn't join: " + seen2);
			int one = Friendship.points(dara, player.getUUID());
			int two = Friendship.points(dara, second.getUUID());
			ChoiceMenu menu = QaHallScreenGameTests.openPeople(helper, player);
			twoClicks(helper, player, menu, dara, VillagerProfession.NONE);
			helper.assertTrue(dara.getVillagerData().getProfession() == VillagerProfession.NONE, "setup: No job didn't take: " + dara.getVillagerData());
			seen.clear();
			seen2.clear();
			VillageQuests.tick(level, hall, (VillageHallBlockEntity) level.getBlockEntity(hall));
			String line = "Dara has no job now, so \"Next level before the festival\" is called off. No friendship is lost.";
			helper.assertTrue(!open(level, hall, quest.id) && PersonalRequests.of(dara) == null, "Dara, jobless, still has her open level request after the hall's round");
			helper.assertTrue(seen.contains(line), "the player who took it on wasn't told it's called off: " + seen);
			helper.assertTrue(seen2.contains(line), "the second helper wasn't told it's called off: " + seen2);
			helper.assertTrue(Friendship.points(dara, player.getUUID()) == one && Friendship.points(dara, second.getUUID()) == two,
				"friendship lost: " + (one - Friendship.points(dara, player.getUUID())) + " and " + (two - Friendship.points(dara, second.getUUID())));
			helper.assertTrue(!ModAttachments.REQUEST_LET_DOWN.has(dara), "Dara is let down by a request nobody could finish");
			helper.assertTrue(PersonalRequests.overdue(level, hall, quest.due) == 0, "the called-off request was still missed at its deadline");
			helper.assertTrue(seen.stream().noneMatch(s -> s.startsWith("You didn't help")) && seen2.stream().noneMatch(s -> s.startsWith("You didn't help")),
				"a helper read the 'missed' line: " + seen + " / " + seen2);
			helper.succeed();
		});
	}

	/** The same through a save and load between No job and the hall's round: the request is still called off, no friendship lost. */
	//$ gametest_ticks_batch AREA '200' '"qaHfCallOffReload"'
	@GameTest(template = AREA, timeoutTicks = 200, batch = "qaHfCallOffReload")
	public void aLevelRequestIsCalledOffAfterASaveAndLoad(GameTestHelper helper) {
		QaHallScreenGameTests.village(helper);
		QaHallRolesDuty1103bGameTests.requestsOn(helper);
		ServerLevel level = helper.getLevel();
		BlockPos hall = helper.absolutePos(HALL);
		Villager dara = librarian(helper, "Dara");
		List<String> seen = new ArrayList<>();
		ServerPlayer player = QaHallScreenGameTests.payer(helper, seen, 0);
		helper.runAfterDelay(3, () -> {
			player.teleportTo(hall.getX() + 1.5, hall.getY(), hall.getZ() + 0.5);
			Friendship.add(dara, player, 450);
			Quest quest = QaHallRolesDuty1103bGameTests.accepted(helper, "level_up", dara, player);
			int points = Friendship.points(dara, player.getUUID());
			ChoiceMenu menu = QaHallScreenGameTests.openPeople(helper, player);
			twoClicks(helper, player, menu, dara, VillagerProfession.NONE);
			player.closeContainer();
			Villager back = reloaded(helper, dara);
			reloadStories(level);
			helper.assertTrue(open(level, hall, quest.id), "setup: the request didn't survive the reload");
			helper.assertTrue(back.getVillagerData().getProfession() == VillagerProfession.NONE, "setup: Dara has a job after the reload: " + back.getVillagerData());
			seen.clear();
			VillageQuests.tick(level, hall, (VillageHallBlockEntity) level.getBlockEntity(hall));
			helper.assertTrue(!open(level, hall, quest.id), "after a save and load, jobless Dara's level request is still open after the hall's round");
			helper.assertTrue(Friendship.points(back, player.getUUID()) == points, "friendship lost: " + (points - Friendship.points(back, player.getUUID())));
			helper.assertTrue(seen.contains("Dara has no job now, so \"Next level before the festival\" is called off. No friendship is lost."), "told: " + seen);
			helper.succeed();
		});
	}

	/** The control: Dara retrained as a Mason (she still has a job and can level) keeps her request through the hall's round. */
	//$ gametest_ticks_batch AREA '200' '"qaHfRetrainedKeeps"'
	@GameTest(template = AREA, timeoutTicks = 200, batch = "qaHfRetrainedKeeps")
	public void aRetrainedFriendsLevelRequestStaysOpen(GameTestHelper helper) {
		QaHallScreenGameTests.village(helper);
		QaHallRolesDuty1103bGameTests.requestsOn(helper);
		helper.setBlock(STONECUTTER, Blocks.STONECUTTER);
		ServerLevel level = helper.getLevel();
		BlockPos hall = helper.absolutePos(HALL);
		Villager dara = librarian(helper, "Dara");
		List<String> seen = new ArrayList<>();
		ServerPlayer player = QaHallScreenGameTests.payer(helper, seen, 5);
		helper.runAfterDelay(3, () -> {
			player.teleportTo(hall.getX() + 1.5, hall.getY(), hall.getZ() + 0.5);
			Friendship.add(dara, player, 450);
			Quest quest = QaHallRolesDuty1103bGameTests.accepted(helper, "level_up", dara, player);
			ChoiceMenu menu = QaHallScreenGameTests.openPeople(helper, player);
			twoClicks(helper, player, menu, dara, VillagerProfession.MASON);
			helper.assertTrue(dara.getVillagerData().getProfession() == VillagerProfession.MASON, "setup: not retrained: " + seen);
			seen.clear();
			VillageQuests.tick(level, hall, (VillageHallBlockEntity) level.getBlockEntity(hall));
			helper.assertTrue(open(level, hall, quest.id) && PersonalRequests.of(dara) == quest, "Dara, retrained as a Mason, lost her level request at the hall's round");
			helper.assertTrue(seen.stream().noneMatch(s -> s.contains("is called off")), "told it's called off: " + seen);
			helper.succeed();
		});
	}

	/** A request of another kind (a letter to family) on a friend given No job isn't called off: a jobless villager can still be helped with it. */
	//$ gametest_ticks_batch AREA '200' '"qaHfOtherRequest"'
	@GameTest(template = AREA, timeoutTicks = 200, batch = "qaHfOtherRequest")
	public void aJoblessFriendsOtherRequestStaysOpen(GameTestHelper helper) {
		QaHallScreenGameTests.village(helper);
		QaHallRolesDuty1103bGameTests.requestsOn(helper);
		ServerLevel level = helper.getLevel();
		BlockPos hall = helper.absolutePos(HALL);
		Villager dara = librarian(helper, "Dara");
		List<String> seen = new ArrayList<>();
		ServerPlayer player = QaHallScreenGameTests.payer(helper, seen, 0);
		helper.runAfterDelay(3, () -> {
			player.teleportTo(hall.getX() + 1.5, hall.getY(), hall.getZ() + 0.5);
			Friendship.add(dara, player, 450);
			Quest quest = null;
			String kind = null;
			for (String name : List.of("letter_to_family", "taste_of_home", "cat_by_the_door", "home", "lost_heirloom")) {
				QuestFiles.QuestFile file = QuestFiles.get(PersonalRequestGameTests.file(name)).orElse(null);
				Quest q = file == null ? null : PersonalRequests.resolve(level, hall, (VillageHallBlockEntity) level.getBlockEntity(hall),
					VillageHalls.census(level, hall), file, dara, player, RandomSource.create(7));
				if (q != null) {
					quest = q;
					kind = name;
					break;
				}
			}
			helper.assertTrue(quest != null, "setup: Dara has no other request to ask");
			PersonalRequests.propose(level, hall, dara, player, quest, Chronicle.day(level));
			PersonalRequestGameTests.command(player, "workplace quest accept " + quest.id);
			helper.assertTrue(PersonalRequests.of(dara) == quest, "setup: " + kind + " wasn't accepted");
			ChoiceMenu menu = QaHallScreenGameTests.openPeople(helper, player);
			twoClicks(helper, player, menu, dara, VillagerProfession.NONE);
			helper.assertTrue(dara.getVillagerData().getProfession() == VillagerProfession.NONE, "setup: No job didn't take");
			seen.clear();
			VillageQuests.tick(level, hall, (VillageHallBlockEntity) level.getBlockEntity(hall));
			helper.assertTrue(open(level, hall, quest.id), "Dara's '" + quest.title().getString() + "' (" + kind + ") was taken down when she lost her job");
			helper.assertTrue(seen.stream().noneMatch(s -> s.contains("is called off")), "told it's called off: " + seen);
			helper.succeed();
		});
	}

	// --- B891: a scout given No job at the lookout ---------------------------------------------------------------------

	/** A Guard by a guard post, a sword in hand and a spyglass in the other (a scout). */
	private static Villager scout(GameTestHelper helper, String name) {
		BlockPos post = new BlockPos(12, 2, 15);
		helper.setBlock(post, ModBlocks.GUARD_POST);
		Villager v = QaHallScreenGameTests.villager(helper, name, post.offset(0, 0, 1));
		Jobs.employ(helper.getLevel(), v, helper.absolutePos(post), ModVillagers.GUARD_POST_POI, ModVillagers.GUARD);
		v.setNoAi(false);
		v.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.IRON_SWORD));
		v.setItemSlot(EquipmentSlot.OFFHAND, new ItemStack(Items.SPYGLASS));
		return v;
	}

	/**
	 * A scout looking through his spyglass at the lookout is given No job (not retrained): the mission is called off, the
	 * spyglass goes back to his off hand, and he has exactly the one spyglass and one sword he had (none lost, none
	 * duplicated, counted in his hands, his inventory and on the ground).
	 */
	//$ gametest_ticks_batch AREA '200' '"qaHfScoutNoJob"'
	@GameTest(template = AREA, timeoutTicks = 200, batch = "qaHfScoutNoJob")
	public void aScoutGivenNoJobAtTheLookoutPutsTheSpyglassAway(GameTestHelper helper) {
		QaHallScreenGameTests.village(helper);
		ServerLevel level = helper.getLevel();
		BlockPos hall = helper.absolutePos(HALL);
		Villager ash = scout(helper, "Ash");
		List<String> seen = new ArrayList<>();
		ServerPlayer player = QaHallScreenGameTests.payer(helper, seen, 0);
		helper.runAfterDelay(3, () -> {
			QaHallRolesDuty1103bGameTests.lair(helper, 12);
			helper.assertTrue(Scouts.send(level, hall) == Scouts.Sent.SENT, "setup: not sent");
			helper.runAfterDelay(3, () -> {
				helper.assertTrue(Scouts.missionOf(level, ash.getUUID()).map(m -> m.phase() == Scouts.Phase.LOOK).orElse(false)
					&& ash.getMainHandItem().is(Items.SPYGLASS), "setup: not looking: " + Scouts.missionOf(level, ash.getUUID()) + ", holding " + ash.getMainHandItem());
				ChoiceMenu menu = QaHallScreenGameTests.openPeople(helper, player);
				twoClicks(helper, player, menu, ash, VillagerProfession.NONE);
				helper.assertTrue(ash.getVillagerData().getProfession() == VillagerProfession.NONE, "setup: No job didn't take: " + ash.getVillagerData() + "; " + seen);
				helper.runAfterDelay(5, () -> {
					helper.assertTrue(!Scouts.out(ash), "the jobless Ash's scouting mission goes on: " + Scouts.missionOf(level, ash.getUUID()));
					helper.assertTrue(!ash.getMainHandItem().is(Items.SPYGLASS), "the jobless Ash still holds the spyglass up: main hand " + ash.getMainHandItem()
						+ ", off hand " + ash.getOffhandItem());
					helper.assertTrue(ash.getOffhandItem().is(Items.SPYGLASS), "the spyglass isn't back in Ash's off hand: off hand " + ash.getOffhandItem());
					helper.assertTrue(owned(helper, ash, Items.SPYGLASS) == 1, "Ash has " + owned(helper, ash, Items.SPYGLASS) + " spyglasses, not the 1 he had");
					helper.assertTrue(owned(helper, ash, Items.IRON_SWORD) == 1, "Ash has " + owned(helper, ash, Items.IRON_SWORD) + " iron swords, not the 1 he had");
					helper.succeed();
				});
			});
		});
	}

	// --- B885: a retired elder -----------------------------------------------------------------------------------------

	/**
	 * Bram, a retired elder, beside Pip, jobless: forged and stale picks on Bram (Mason twice, a job with no block, No
	 * job, and a Mason pick on a list opened before he retired) give him nothing, pay nothing, never say he's now a Mason,
	 * keep him retired and the stonecutter free; Pip then gets that stonecutter for nothing.
	 */
	//$ gametest_ticks_batch AREA '200' '"qaHfRetiredPicks"'
	@GameTest(template = AREA, timeoutTicks = 200, batch = "qaHfRetiredPicks")
	public void aRetiredEldersPicksGiveAndCostNothing(GameTestHelper helper) {
		QaHallScreenGameTests.village(helper);
		helper.setBlock(STONECUTTER, Blocks.STONECUTTER);
		ServerLevel level = helper.getLevel();
		Villager bram = QaHallScreenGameTests.villager(helper, "Bram", new BlockPos(13, 2, 15));
		Villager pip = QaHallScreenGameTests.villager(helper, "Pip", new BlockPos(13, 2, 18));
		List<String> seen = new ArrayList<>();
		ServerPlayer player = QaHallScreenGameTests.payer(helper, seen, 10);
		helper.runAfterDelay(3, () -> {
			ChoiceMenu menu = QaHallScreenGameTests.openPeople(helper, player);
			QaHallScreenGameTests.jobDropdown(helper, player, menu, bram); // opened while he can still work
			ModAttachments.RETIRED.set(bram, new Retirement.Retired(Chronicle.day(level) - 1, "minecraft:farmer", 3));
			QaHallScreenGameTests.pickJob(helper, player, menu, VillagerProfession.MASON); // the stale list's pick
			QaHallScreenGameTests.pickJob(helper, player, menu, VillagerProfession.MASON);
			QaHallScreenGameTests.pickJob(helper, player, menu, VillagerProfession.FLETCHER); // no fletching table in the village
			QaHallScreenGameTests.pickJob(helper, player, menu, VillagerProfession.NONE);
			QaHallScreenGameTests.pickJob(helper, player, menu, VillagerProfession.NONE);
			helper.assertTrue(bram.getVillagerData().getProfession() == VillagerProfession.NONE, "the retired Bram works again: " + bram.getVillagerData());
			helper.assertTrue(Retirement.isRetired(bram), "Bram is no longer retired");
			helper.assertTrue(QaHallScreenGameTests.site(bram).isEmpty() && free(helper, STONECUTTER) == 1, "the stonecutter is held for the retired Bram");
			helper.assertTrue(emeralds(player) == 10, "picks on a retired elder cost " + (10 - emeralds(player)) + " emeralds");
			helper.assertTrue(seen.stream().noneMatch(s -> s.startsWith("Bram is now a") || s.startsWith("Bram gave up")), "told Bram's job changed: " + seen);
			helper.assertTrue(seen.contains("Bram" + RETIRED_LINE), "a pick on the retired Bram didn't say why it's refused: " + seen);
			seen.clear();
			menu = QaHallScreenGameTests.openPeople(helper, player);
			QaHallScreenGameTests.jobDropdown(helper, player, menu, pip);
			QaHallScreenGameTests.pickJob(helper, player, menu, VillagerProfession.MASON);
			helper.assertTrue(pip.getVillagerData().getProfession() == VillagerProfession.MASON && free(helper, STONECUTTER) == 0 && emeralds(player) == 10,
				"Pip (jobless, never retired) didn't get the free stonecutter: " + pip.getVillagerData() + "; told " + seen);
			helper.succeed();
		});
	}

	/** Bram, retired (a free stonecutter in the village), and Pip, jobless, are listed: Bram's row isn't counted as one to give a job, Pip's is. */
	//$ gametest_ticks_batch AREA '200' '"qaHfRetiredList"'
	@GameTest(template = AREA, timeoutTicks = 200, batch = "qaHfRetiredList")
	public void aRetiredEldersStonecutterLineIsGrayedWithTheReason(GameTestHelper helper) {
		QaHallScreenGameTests.village(helper);
		helper.setBlock(STONECUTTER, Blocks.STONECUTTER);
		ServerLevel level = helper.getLevel();
		Villager bram = QaHallScreenGameTests.villager(helper, "Bram", new BlockPos(13, 2, 15));
		List<String> seen = new ArrayList<>();
		ServerPlayer player = QaHallScreenGameTests.payer(helper, seen, 0);
		helper.runAfterDelay(3, () -> {
			ModAttachments.RETIRED.set(bram, new Retirement.Retired(Chronicle.day(level) - 1, "minecraft:farmer", 3));
			ChoiceMenu menu = QaHallScreenGameTests.openPeople(helper, player);
			QaHallScreenGameTests.jobDropdown(helper, player, menu, bram);
			List<ItemStack> lines = HallJobs.lines(menu.icon(QaHallScreenGameTests.slotOf(helper, menu, bram)));
			ItemStack mason = QaHallScreenGameTests.jobLine(helper, lines, VillagerProfession.MASON);
			String state = HallJobs.mark(mason).getString("state");
			helper.assertTrue(!state.equals("free") && !state.equals("picked"), "the retired Bram's Mason line is offered as '" + state + "': " + mason.getHoverName().getString());
			helper.assertTrue(lore(mason).contains("Bram" + RETIRED_LINE), "the retired Bram's Mason line doesn't say why: " + lore(mason));
			helper.assertTrue(lines.stream().noneMatch(l -> HallJobs.mark(l) != null && HallJobs.mark(l).getString("state").equals("free")),
				"a job is offered to the retired Bram");
			helper.succeed();
		});
	}

	/**
	 * B885 through the other door: a press on the retired Bram's row opens the jobless villager's job page, and a click
	 * on the free stonecutter there gives him nothing and doesn't hold the block for nobody (the original bug: the block
	 * taken before the retirement rule said no), so Pip still gets it. (What the click says is
	 * QaHallFixes1103cBugTests#aRetiredEldersJobPageSaysWhyNotSomeoneTookIt.)
	 */
	//$ gametest_ticks_batch AREA '200' '"qaHfRetiredPageFree"'
	@GameTest(template = AREA, timeoutTicks = 200, batch = "qaHfRetiredPageFree")
	public void aRetiredEldersJobPageLeavesTheStonecutterFree(GameTestHelper helper) {
		QaHallScreenGameTests.village(helper);
		helper.setBlock(STONECUTTER, Blocks.STONECUTTER);
		ServerLevel level = helper.getLevel();
		Villager bram = QaHallScreenGameTests.villager(helper, "Bram", new BlockPos(13, 2, 15));
		Villager pip = QaHallScreenGameTests.villager(helper, "Pip", new BlockPos(13, 2, 18));
		List<String> seen = new ArrayList<>();
		ServerPlayer player = QaHallScreenGameTests.payer(helper, seen, 0);
		helper.runAfterDelay(3, () -> {
			ModAttachments.RETIRED.set(bram, new Retirement.Retired(Chronicle.day(level) - 1, "minecraft:farmer", 3));
			ChoiceMenu menu = QaHallScreenGameTests.openPeople(helper, player);
			menu.clicked(QaHallScreenGameTests.slotOf(helper, menu, bram), 0, ClickType.PICKUP, player); // his row's button
			for (int slot = 0; slot < ChoiceMenu.SIZE; slot++) {
				if (menu.icon(slot).is(Items.STONECUTTER)) {
					menu.clicked(slot, 0, ClickType.PICKUP, player);
					break;
				}
			}
			helper.assertTrue(bram.getVillagerData().getProfession() == VillagerProfession.NONE && QaHallScreenGameTests.site(bram).isEmpty(),
				"the retired Bram got a job from his job page: " + bram.getVillagerData() + " at " + QaHallScreenGameTests.site(bram));
			helper.assertTrue(free(helper, STONECUTTER) == 1, "after a click on the retired Bram's job page the stonecutter is held for nobody");
			helper.assertTrue(seen.stream().noneMatch(s -> s.startsWith("Bram is now a")), "told Bram has a job: " + seen);
			player.closeContainer();
			ChoiceMenu again = QaHallScreenGameTests.openPeople(helper, player);
			QaHallScreenGameTests.jobDropdown(helper, player, again, pip);
			QaHallScreenGameTests.pickJob(helper, player, again, VillagerProfession.MASON);
			helper.assertTrue(pip.getVillagerData().getProfession() == VillagerProfession.MASON, "Pip didn't get the stonecutter: " + seen);
			helper.succeed();
		});
	}

	// --- B886: the Steward retrained or given No job --------------------------------------------------------------------

	/** Sam, the hall's Steward, the City Plan prop in hand on today's round. */
	private static Villager steward(GameTestHelper helper, String name) {
		ServerLevel level = helper.getLevel();
		BlockPos hall = helper.absolutePos(HALL);
		Villager sam = QaHallScreenGameTests.villager(helper, name, new BlockPos(13, 2, 15));
		Stations.assign(level, sam, hall, ModVillagers.STEWARD);
		sam.setVillagerData(sam.getVillagerData().setProfession(ModVillagers.STEWARD).setLevel(1));
		sam.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(ModItems.CITY_PLAN));
		sam.setDropChance(EquipmentSlot.MAINHAND, 0f);
		ModAttachments.STEWARD_ROUND_DAY.set(sam, Chronicle.day(level));
		return sam;
	}

	/** Names {@code tom} (a seasoned Builder by the hall) Steward with the player's own plan, bound to the hall: what a player does next. */
	private static void nameSteward(GameTestHelper helper, ServerPlayer player, Villager tom, List<String> seen) {
		ServerLevel level = helper.getLevel();
		BlockPos hall = helper.absolutePos(HALL);
		ItemStack plan = new ItemStack(ModItems.CITY_PLAN);
		CityPlanItem.bind(level, player, plan, hall);
		player.setItemInHand(InteractionHand.MAIN_HAND, plan);
		InteractionResult result = Stewards.appoint(player, tom, player.getMainHandItem());
		helper.assertTrue(result == InteractionResult.SUCCESS && Stewards.stewardOf(level, hall) == tom,
			"a new Steward couldn't be named after the old one stepped down: " + result + "; told " + seen);
		helper.assertTrue(player.getMainHandItem().is(ModItems.CITY_PLAN), "naming the new Steward took the player's own City Plan");
	}

	private static boolean holdsAPlan(Villager v) {
		for (EquipmentSlot slot : EquipmentSlot.values()) {
			if (v.getItemBySlot(slot).is(ModItems.CITY_PLAN)) {
				return true;
			}
		}
		return v.getInventory().countItem(ModItems.CITY_PLAN) > 0;
	}

	/**
	 * The Steward retrained as a Mason, then the world saved and loaded: the Mason carries no City Plan anywhere (hands or
	 * inventory), his round is forgotten, he's still a Mason; and a seasoned Builder is named the new Steward with the
	 * player's plan, which the player keeps.
	 */
	//$ gametest_ticks_batch AREA '200' '"qaHfStewardRetrainReload"'
	@GameTest(template = AREA, timeoutTicks = 200, batch = "qaHfStewardRetrainReload")
	public void aRetrainedStewardCarriesNoPlanAfterAReloadAndANewOneIsNamed(GameTestHelper helper) {
		QaHallScreenGameTests.village(helper);
		helper.setBlock(STONECUTTER, Blocks.STONECUTTER);
		ServerLevel level = helper.getLevel();
		BlockPos hall = helper.absolutePos(HALL);
		List<String> seen = new ArrayList<>();
		ServerPlayer player = QaHallScreenGameTests.payer(helper, seen, 5);
		helper.runAfterDelay(3, () -> {
			Villager sam = steward(helper, "Sam");
			helper.assertTrue(Stewards.stewardOf(level, hall) == sam, "setup: Sam isn't the Steward");
			ChoiceMenu menu = QaHallScreenGameTests.openPeople(helper, player);
			twoClicks(helper, player, menu, sam, VillagerProfession.MASON);
			helper.assertTrue(sam.getVillagerData().getProfession() == VillagerProfession.MASON, "setup: not retrained: " + seen);
			player.closeContainer();
			Villager back = reloaded(helper, sam);
			helper.runAfterDelay(2, () -> {
				helper.assertTrue(back.getVillagerData().getProfession() == VillagerProfession.MASON, "after the reload Sam is " + back.getVillagerData().getProfession());
				helper.assertTrue(!holdsAPlan(back), "after a reload the Mason Sam still carries the Steward's City Plan: main hand " + back.getMainHandItem());
				helper.assertTrue(!ModAttachments.STEWARD_ROUND_DAY.has(back), "the Mason Sam still remembers a Steward's round day");
				helper.assertTrue(Stewards.stewardOf(level, hall) == null && level.getPoiManager().getFreeTickets(hall) == 1, "the hall's Steward place is held after the reload");
				Villager tom = StewardGameTests.seasoned(QaHallScreenGameTests.villager(helper, "Tom", HALL.offset(0, 0, 2)));
				nameSteward(helper, player, tom, seen);
				helper.assertTrue(!holdsAPlan(back) && back.getVillagerData().getProfession() == VillagerProfession.MASON, "naming Tom changed Sam");
				helper.succeed();
			});
		});
	}

	/** The Steward given No job, saved and loaded: jobless, no plan, no round; a new Steward is then named with the player's plan. */
	//$ gametest_ticks_batch AREA '200' '"qaHfStewardNoJobReload"'
	@GameTest(template = AREA, timeoutTicks = 200, batch = "qaHfStewardNoJobReload")
	public void aStewardGivenNoJobMakesRoomForANewOne(GameTestHelper helper) {
		QaHallScreenGameTests.village(helper);
		ServerLevel level = helper.getLevel();
		BlockPos hall = helper.absolutePos(HALL);
		List<String> seen = new ArrayList<>();
		ServerPlayer player = QaHallScreenGameTests.payer(helper, seen, 0);
		helper.runAfterDelay(3, () -> {
			Villager sam = steward(helper, "Sam");
			ChoiceMenu menu = QaHallScreenGameTests.openPeople(helper, player);
			twoClicks(helper, player, menu, sam, VillagerProfession.NONE);
			helper.assertTrue(sam.getVillagerData().getProfession() == VillagerProfession.NONE, "setup: No job didn't take: " + seen);
			player.closeContainer();
			Villager back = reloaded(helper, sam);
			helper.runAfterDelay(2, () -> {
				helper.assertTrue(!holdsAPlan(back) && !ModAttachments.STEWARD_ROUND_DAY.has(back),
					"after a reload the jobless Sam " + (holdsAPlan(back) ? "still carries the City Plan" : "still remembers a Steward's round"));
				Villager tom = StewardGameTests.seasoned(QaHallScreenGameTests.villager(helper, "Tom", HALL.offset(0, 0, 2)));
				nameSteward(helper, player, tom, seen);
				player.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY); // the hall opens for an empty hand (a plan would be bound to it)
				ChoiceMenu after = QaHallScreenGameTests.openPeople(helper, player);
				helper.assertTrue(row(helper, after, back).getString("kind").equals("jobless"), "Sam's row after: " + row(helper, after, back));
				helper.succeed();
			});
		});
	}
}
