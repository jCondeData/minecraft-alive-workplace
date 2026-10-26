package io.github.jcondedata.aliveworkplace.test;

import static io.github.jcondedata.aliveworkplace.test.StationsSpecGameTests.listeningPlayer;

import io.github.jcondedata.aliveworkplace.hall.HallBeds;
import io.github.jcondedata.aliveworkplace.hall.HallJobs;
import io.github.jcondedata.aliveworkplace.hall.HallPeople;
import io.github.jcondedata.aliveworkplace.hall.HallSections;
import io.github.jcondedata.aliveworkplace.hall.HallSections.Section;
import io.github.jcondedata.aliveworkplace.hall.VillageHalls;
import io.github.jcondedata.aliveworkplace.hall.VillageNeeds;
import io.github.jcondedata.aliveworkplace.people.Moods;
import io.github.jcondedata.aliveworkplace.registry.ModBlocks;
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
import net.minecraft.world.level.block.state.properties.BedPart;

/**
 * QA (lane-c-1026-1254): adversarial checks of the Village Hall's new screen, ROADMAP 40.1-40.4, written from the
 * items' Done when. Every check goes through the clicks the hall's screen sends (a right-click on the hall, the sidebar,
 * Job ▾ / Bed ▾ on a row, a line of the list), including the ones a stale or hand-made screen could send: paying twice,
 * changing the pick between the offer and the pay, the work block gone before the pay, the villager dead with the list
 * open, picks with no list open, the bed of a swap partner broken, a bed just outside the village, a bed picked for the
 * villager who already sleeps there, the "No bed" mood word, and the sections that start empty.
 */
public class QaHallScreenGameTests {
	private static final String AREA = "aliveworkplace_test:huge_area";
	private static final BlockPos HALL = new BlockPos(15, 2, 15);
	private static final BlockPos LECTERN = new BlockPos(6, 2, 6);
	private static final BlockPos STONECUTTER = new BlockPos(22, 2, 22);
	private static final BlockPos COMPOSTER = new BlockPos(22, 2, 8);
	private static final BlockPos NEAR = new BlockPos(10, 2, 10);
	private static final BlockPos FAR = new BlockPos(20, 2, 18);

	private static void village(GameTestHelper helper) {
		Leftovers.clear(helper);
		HallSections.forget();
		HallJobs.forget();
		HallBeds.forget();
		int was = VillageHalls.RADIUS;
		VillageHalls.RADIUS = 14;
		Leftovers.after(helper, () -> VillageHalls.RADIUS = was);
		helper.setBlock(HALL, ModBlocks.VILLAGE_HALL);
	}

	private static Villager librarian(GameTestHelper helper, int level) {
		helper.setBlock(LECTERN, Blocks.LECTERN);
		Villager v = helper.spawn(EntityType.VILLAGER, LECTERN.offset(1, 0, 1));
		v.setNoAi(true);
		Jobs.employ(helper.getLevel(), v, helper.absolutePos(LECTERN), PoiTypes.LIBRARIAN, VillagerProfession.LIBRARIAN);
		v.setVillagerData(v.getVillagerData().setLevel(level));
		v.setVillagerXp(level == 1 ? 5 : 70 * level);
		v.setCustomName(Component.literal("Dara"));
		return v;
	}

	private static Villager villager(GameTestHelper helper, String name, BlockPos at) {
		Villager v = helper.spawn(EntityType.VILLAGER, at);
		v.setNoAi(true);
		v.setCustomName(Component.literal(name));
		return v;
	}

	private static ServerPlayer payer(GameTestHelper helper, List<String> seen, int emeralds) {
		ServerPlayer player = listeningPlayer(helper, seen);
		if (emeralds > 0) {
			player.getInventory().setItem(9, new ItemStack(Items.EMERALD, emeralds));
		}
		return player;
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
		helper.fail("no row for " + villager.getName().getString());
		return -1;
	}

	private static CompoundTag row(GameTestHelper helper, ChoiceMenu menu, Villager villager) {
		return HallPeople.row(menu.icon(slotOf(helper, menu, villager)));
	}

	private static int id(VillagerProfession job) {
		return BuiltInRegistries.VILLAGER_PROFESSION.getId(job);
	}

	private static void jobDropdown(GameTestHelper helper, ServerPlayer player, ChoiceMenu menu, Villager villager) {
		helper.assertTrue(menu.clickMenuButton(player, HallJobs.OPEN + slotOf(helper, menu, villager)), "Job ▾ wasn't taken");
		helper.assertTrue(row(helper, menu, villager).getBoolean("jobs_open"), "Job ▾ didn't open the list");
	}

	private static void pickJob(GameTestHelper helper, ServerPlayer player, ChoiceMenu menu, VillagerProfession job) {
		helper.assertTrue(menu.clickMenuButton(player, HallJobs.PICK + id(job)), "the pick wasn't taken");
	}

	private static Optional<BlockPos> site(Villager villager) {
		return villager.getBrain().getMemory(MemoryModuleType.JOB_SITE).map(GlobalPos::pos);
	}

	private static int emeralds(ServerPlayer player) {
		return player.getInventory().countItem(Items.EMERALD);
	}

	/** A red bed with its foot at {@code foot}, head to the south; returns its head, absolute. */
	private static BlockPos bed(GameTestHelper helper, BlockPos foot) {
		helper.setBlock(foot, Blocks.RED_BED.defaultBlockState().setValue(BedBlock.FACING, Direction.SOUTH).setValue(BedBlock.PART, BedPart.FOOT));
		helper.setBlock(foot.south(), Blocks.RED_BED.defaultBlockState().setValue(BedBlock.FACING, Direction.SOUTH).setValue(BedBlock.PART, BedPart.HEAD));
		return helper.absolutePos(foot.south());
	}

	private static void breakBed(GameTestHelper helper, BlockPos foot) {
		helper.setBlock(foot.south(), Blocks.AIR);
		helper.setBlock(foot, Blocks.AIR);
	}

	private static List<ItemStack> bedDropdown(GameTestHelper helper, ServerPlayer player, ChoiceMenu menu, Villager villager) {
		helper.assertTrue(menu.clickMenuButton(player, HallBeds.OPEN + slotOf(helper, menu, villager)), "Bed ▾ wasn't taken");
		return HallBeds.lines(menu.icon(slotOf(helper, menu, villager)));
	}

	private static String bedState(ItemStack line) {
		return HallBeds.mark(line).getString("state");
	}

	private static ItemStack firstBed(GameTestHelper helper, List<ItemStack> lines, String state) {
		for (ItemStack line : lines) {
			if (bedState(line).equals(state)) {
				return line;
			}
		}
		helper.fail("no " + state + " line in " + lines.stream().map(l -> l.getHoverName().getString()).toList());
		return ItemStack.EMPTY;
	}

	private static void pickBed(GameTestHelper helper, ServerPlayer player, ChoiceMenu menu, ItemStack line) {
		helper.assertTrue(menu.clickMenuButton(player, HallBeds.PICK + HallBeds.mark(line).getInt("id")), "the pick wasn't taken");
	}

	private static BlockPos home(Villager v) {
		return v.getBrain().getMemory(MemoryModuleType.HOME).map(GlobalPos::pos).orElse(null);
	}

	private static int housed(GameTestHelper helper) {
		return VillageNeeds.count(helper.getLevel(), helper.absolutePos(HALL)).housed();
	}

	// --- Job ▾ (40.3) ---------------------------------------------------------------------------------------------------

	/**
	 * "Click again to pay" pays once: a double-click race (the second and third clicks on the same job arriving after the
	 * pay) takes 10 emeralds once, and the villager is retrained once (still a Novice Mason at the stonecutter).
	 */
	//$ gametest_ticks_batch AREA '200' '"qaHallJobPaysOnce"'
	@GameTest(template = AREA, timeoutTicks = 200, batch = "qaHallJobPaysOnce")
	public void retrainingPaysOnceOnRepeatedClicks(GameTestHelper helper) {
		village(helper);
		helper.setBlock(STONECUTTER, Blocks.STONECUTTER);
		Villager dara = librarian(helper, 2);
		List<String> seen = new ArrayList<>();
		ServerPlayer player = payer(helper, seen, 40);
		helper.runAfterDelay(3, () -> {
			ChoiceMenu menu = openPeople(helper, player);
			jobDropdown(helper, player, menu, dara);
			for (int i = 0; i < 4; i++) {
				pickJob(helper, player, menu, VillagerProfession.MASON);
			}
			helper.assertTrue(dara.getVillagerData().getProfession() == VillagerProfession.MASON && dara.getVillagerData().getLevel() == 1,
				"now " + dara.getVillagerData());
			helper.assertTrue(emeralds(player) == 30, "paid " + (40 - emeralds(player)) + " for one change");
			helper.assertTrue(seen.stream().filter(s -> s.startsWith("Dara gave up the Librarian job and was retrained")).count() == 1, "told: " + seen);
			helper.assertTrue(!HallJobs.isOpen(menu, dara), "the list stayed open after the pay");
			helper.succeed();
		});
	}

	/**
	 * The second click pays only for the job the first one offered: offer Mason, then click Farmer: that's Farmer's offer
	 * (nothing paid); Mason again is a new offer, not a pay. Only a second click on the same job pays, at the price shown.
	 */
	//$ gametest_ticks_batch AREA '200' '"qaHallJobSwitchPick"'
	@GameTest(template = AREA, timeoutTicks = 200, batch = "qaHallJobSwitchPick")
	public void switchingThePickBetweenClicksPaysNothing(GameTestHelper helper) {
		village(helper);
		helper.setBlock(STONECUTTER, Blocks.STONECUTTER);
		helper.setBlock(COMPOSTER, Blocks.COMPOSTER);
		Villager dara = librarian(helper, 1);
		List<String> seen = new ArrayList<>();
		ServerPlayer player = payer(helper, seen, 5);
		helper.runAfterDelay(3, () -> {
			ChoiceMenu menu = openPeople(helper, player);
			jobDropdown(helper, player, menu, dara);
			pickJob(helper, player, menu, VillagerProfession.MASON);
			pickJob(helper, player, menu, VillagerProfession.FARMER);
			helper.assertTrue(dara.getVillagerData().getProfession() == VillagerProfession.LIBRARIAN && emeralds(player) == 5, "Farmer after Mason's offer paid");
			helper.assertTrue(HallJobs.picked(menu) == VillagerProfession.FARMER, "the pick is " + HallJobs.picked(menu));
			helper.assertTrue(seen.stream().anyMatch(s -> s.contains("can be retrained as a Farmer for 5 emeralds")), "no Farmer offer: " + seen);
			pickJob(helper, player, menu, VillagerProfession.MASON);
			helper.assertTrue(dara.getVillagerData().getProfession() == VillagerProfession.LIBRARIAN && emeralds(player) == 5, "Mason after Farmer's offer paid");
			pickJob(helper, player, menu, VillagerProfession.MASON);
			helper.assertTrue(dara.getVillagerData().getProfession() == VillagerProfession.MASON && emeralds(player) == 0, "the confirmed pick: " + dara.getVillagerData());
			helper.assertTrue(helper.getLevel().getPoiManager().getFreeTickets(helper.absolutePos(COMPOSTER)) == 1, "the composter was taken too");
			helper.succeed();
		});
	}

	/**
	 * The work block gone between the offer and the pay (broken by someone): the second click changes nothing (job,
	 * level, lectern, money) and says the job needs a free Stonecutter.
	 */
	//$ gametest_ticks_batch AREA '200' '"qaHallJobBlockGone"'
	@GameTest(template = AREA, timeoutTicks = 200, batch = "qaHallJobBlockGone")
	public void workBlockBrokenBeforeThePay(GameTestHelper helper) {
		village(helper);
		helper.setBlock(STONECUTTER, Blocks.STONECUTTER);
		Villager dara = librarian(helper, 3);
		List<String> seen = new ArrayList<>();
		ServerPlayer player = payer(helper, seen, 30);
		helper.runAfterDelay(3, () -> {
			ChoiceMenu menu = openPeople(helper, player);
			jobDropdown(helper, player, menu, dara);
			pickJob(helper, player, menu, VillagerProfession.MASON);
			helper.setBlock(STONECUTTER, Blocks.AIR);
			pickJob(helper, player, menu, VillagerProfession.MASON);
			helper.assertTrue(dara.getVillagerData().getProfession() == VillagerProfession.LIBRARIAN && dara.getVillagerData().getLevel() == 3
				&& site(dara).equals(Optional.of(helper.absolutePos(LECTERN))), "changed: " + dara.getVillagerData() + " at " + site(dara));
			helper.assertTrue(emeralds(player) == 30, "paid " + (30 - emeralds(player)) + " for a job with no block");
			helper.assertTrue(seen.contains("The Mason job needs a free Stonecutter: place one in the village first."), "not told: " + seen);
			helper.succeed();
		});
	}

	/**
	 * Game rule workplaceRetraining turned off between the offer and the pay (the screen still open): the pay click is
	 * refused on the server, nothing changes and nothing is paid, and the list closes.
	 */
	//$ gametest_ticks_batch AREA '200' '"qaHallJobRuleOffMidway"'
	@GameTest(template = AREA, timeoutTicks = 200, batch = "qaHallJobRuleOffMidway")
	public void ruleTurnedOffBeforeThePay(GameTestHelper helper) {
		village(helper);
		helper.setBlock(STONECUTTER, Blocks.STONECUTTER);
		Villager dara = librarian(helper, 1);
		List<String> seen = new ArrayList<>();
		ServerPlayer player = payer(helper, seen, 30);
		net.minecraft.server.level.ServerLevel level = helper.getLevel();
		helper.runAfterDelay(3, () -> {
			ChoiceMenu menu = openPeople(helper, player);
			jobDropdown(helper, player, menu, dara);
			pickJob(helper, player, menu, VillagerProfession.MASON);
			level.getGameRules().getRule(io.github.jcondedata.aliveworkplace.registry.ModGameRules.RETRAINING).set(false, level.getServer());
			try {
				pickJob(helper, player, menu, VillagerProfession.MASON);
				helper.assertTrue(dara.getVillagerData().getProfession() == VillagerProfession.LIBRARIAN && site(dara).equals(Optional.of(helper.absolutePos(LECTERN))),
					"retrained with the rule off: " + dara.getVillagerData());
				helper.assertTrue(emeralds(player) == 30, "paid with the rule off");
				helper.assertTrue(!HallJobs.isOpen(menu, dara) && row(helper, menu, dara).getBoolean("job_hidden"), "the list or button is still there: " + row(helper, menu, dara));
			} finally {
				level.getGameRules().getRule(io.github.jcondedata.aliveworkplace.registry.ModGameRules.RETRAINING).set(true, level.getServer());
			}
			helper.succeed();
		});
	}

	/** Exactly the price in emeralds (a Journeyman, 20, with 20 held) is enough: she's retrained and the purse is empty. */
	//$ gametest_ticks_batch AREA '200' '"qaHallJobExactPrice"'
	@GameTest(template = AREA, timeoutTicks = 200, batch = "qaHallJobExactPrice")
	public void exactlyThePriceIsEnough(GameTestHelper helper) {
		village(helper);
		helper.setBlock(STONECUTTER, Blocks.STONECUTTER);
		Villager dara = librarian(helper, 3);
		List<String> seen = new ArrayList<>();
		ServerPlayer player = payer(helper, seen, 20);
		helper.runAfterDelay(3, () -> {
			ChoiceMenu menu = openPeople(helper, player);
			jobDropdown(helper, player, menu, dara);
			pickJob(helper, player, menu, VillagerProfession.MASON);
			pickJob(helper, player, menu, VillagerProfession.MASON);
			helper.assertTrue(dara.getVillagerData().getProfession() == VillagerProfession.MASON && emeralds(player) == 0,
				"with exactly 20: " + dara.getVillagerData() + ", " + emeralds(player) + " left");
			helper.succeed();
		});
	}

	/**
	 * Clicks a stale or hand-made screen could send change nothing: a pick with no list open, Job ▾ on a slot that's no
	 * person, a pick of the Nitwit job or of her own job, a pick after the list was closed. Job, block and money stay.
	 */
	//$ gametest_ticks_batch AREA '200' '"qaHallJobForged"'
	@GameTest(template = AREA, timeoutTicks = 200, batch = "qaHallJobForged")
	public void forgedJobClicksChangeNothing(GameTestHelper helper) {
		village(helper);
		helper.setBlock(STONECUTTER, Blocks.STONECUTTER);
		Villager dara = librarian(helper, 1);
		List<String> seen = new ArrayList<>();
		ServerPlayer player = payer(helper, seen, 64);
		helper.runAfterDelay(3, () -> {
			ChoiceMenu menu = openPeople(helper, player);
			// No list open.
			pickJob(helper, player, menu, VillagerProfession.MASON);
			pickJob(helper, player, menu, VillagerProfession.MASON);
			// Job ▾ on slot 0 (the village's name, no person) and past the screen's end.
			helper.assertTrue(menu.clickMenuButton(player, HallJobs.OPEN), "Job ▾ on slot 0 refused outright");
			helper.assertTrue(menu.clickMenuButton(player, HallJobs.OPEN + ChoiceMenu.SIZE + 5), "Job ▾ past the end refused outright");
			helper.assertTrue(HallJobs.picked(menu) == null && !HallJobs.isOpen(menu, dara), "a list opened");
			// Her list: the Nitwit job and her own job.
			jobDropdown(helper, player, menu, dara);
			for (int i = 0; i < 2; i++) {
				pickJob(helper, player, menu, VillagerProfession.NITWIT);
				pickJob(helper, player, menu, VillagerProfession.LIBRARIAN);
			}
			// An offer, then the list closed, then the pay.
			pickJob(helper, player, menu, VillagerProfession.MASON);
			helper.assertTrue(menu.clickMenuButton(player, HallJobs.CLOSE), "close wasn't taken");
			helper.assertTrue(!row(helper, menu, dara).getBoolean("jobs_open"), "the list didn't close");
			pickJob(helper, player, menu, VillagerProfession.MASON);
			helper.assertTrue(dara.getVillagerData().getProfession() == VillagerProfession.LIBRARIAN && site(dara).equals(Optional.of(helper.absolutePos(LECTERN)))
				&& dara.getVillagerData().getLevel() == 1, "changed: " + dara.getVillagerData());
			helper.assertTrue(emeralds(player) == 64, "money was taken");
			helper.assertTrue(helper.getLevel().getPoiManager().getFreeTickets(helper.absolutePos(STONECUTTER)) == 1, "the stonecutter was taken");
			helper.succeed();
		});
	}

	/** The villager dies with the list open, after the offer: the pay click takes nothing, the stonecutter stays free, the list closes. */
	//$ gametest_ticks_batch AREA '200' '"qaHallJobDead"'
	@GameTest(template = AREA, timeoutTicks = 200, batch = "qaHallJobDead")
	public void villagerDiesBetweenOfferAndPay(GameTestHelper helper) {
		village(helper);
		helper.setBlock(STONECUTTER, Blocks.STONECUTTER);
		Villager dara = librarian(helper, 2);
		List<String> seen = new ArrayList<>();
		ServerPlayer player = payer(helper, seen, 30);
		helper.runAfterDelay(3, () -> {
			ChoiceMenu menu = openPeople(helper, player);
			jobDropdown(helper, player, menu, dara);
			pickJob(helper, player, menu, VillagerProfession.MASON);
			dara.kill();
			pickJob(helper, player, menu, VillagerProfession.MASON);
			helper.assertTrue(emeralds(player) == 30, "paid for a dead villager");
			helper.assertTrue(helper.getLevel().getPoiManager().getFreeTickets(helper.absolutePos(STONECUTTER)) == 1, "the stonecutter was claimed by the dead");
			helper.assertTrue(HallJobs.picked(menu) == null && !HallJobs.isOpen(menu, dara), "the list stayed open");
			helper.assertTrue(seen.stream().noneMatch(s -> s.contains("was retrained")), "told of a retraining: " + seen);
			helper.succeed();
		});
	}

	/**
	 * "No job" sent for a villager who has none (the line isn't shown to them): nothing is said or changed. Job ▾ then
	 * Bed ▾ on the same row: one list at a time.
	 */
	//$ gametest_ticks_batch AREA '200' '"qaHallJobNoJobJobless"'
	@GameTest(template = AREA, timeoutTicks = 200, batch = "qaHallJobNoJobJobless")
	public void noJobOnAJoblessVillagerDoesNothing(GameTestHelper helper) {
		village(helper);
		Villager pip = villager(helper, "Pip", new BlockPos(10, 2, 12));
		bed(helper, NEAR);
		List<String> seen = new ArrayList<>();
		ServerPlayer player = payer(helper, seen, 10);
		helper.runAfterDelay(3, () -> {
			ChoiceMenu menu = openPeople(helper, player);
			jobDropdown(helper, player, menu, pip);
			pickJob(helper, player, menu, VillagerProfession.NONE);
			pickJob(helper, player, menu, VillagerProfession.NONE);
			helper.assertTrue(pip.getVillagerData().getProfession() == VillagerProfession.NONE && emeralds(player) == 10, "changed: " + pip.getVillagerData());
			helper.assertTrue(seen.stream().noneMatch(s -> s.contains("give up") || s.contains("gave up")), "told of giving up a job: " + seen);
			helper.assertTrue(row(helper, menu, pip).getBoolean("jobs_open"), "the list closed");
			List<ItemStack> beds = bedDropdown(helper, player, menu, pip);
			CompoundTag r = row(helper, menu, pip);
			helper.assertTrue(r.getBoolean("beds_open") && !r.getBoolean("jobs_open") && !beds.isEmpty(), "two lists open at once: " + r);
			helper.assertTrue(menu.clickMenuButton(player, HallJobs.OPEN + slotOf(helper, menu, pip)), "Job ▾ wasn't taken");
			r = row(helper, menu, pip);
			helper.assertTrue(r.getBoolean("jobs_open") && !r.getBoolean("beds_open"), "Job ▾ didn't close Bed ▾: " + r);
			helper.succeed();
		});
	}

	/**
	 * The list's words with real arguments: two free stonecutters count 2 with the nearest one named (the plural is
	 * QaHallScreenBugTests' on tests/hall-m40); a job with no block says what to place; No job's line says what she gives up.
	 */
	//$ gametest_ticks_batch AREA '200' '"qaHallJobWords"'
	@GameTest(template = AREA, timeoutTicks = 200, batch = "qaHallJobWords")
	public void theJobListsWords(GameTestHelper helper) {
		village(helper);
		helper.setBlock(STONECUTTER, Blocks.STONECUTTER);
		helper.setBlock(new BlockPos(20, 2, 20), Blocks.STONECUTTER);
		Villager dara = librarian(helper, 1);
		List<String> seen = new ArrayList<>();
		ServerPlayer player = payer(helper, seen, 0);
		helper.runAfterDelay(3, () -> {
			ChoiceMenu menu = openPeople(helper, player);
			jobDropdown(helper, player, menu, dara);
			List<ItemStack> lines = HallJobs.lines(menu.icon(slotOf(helper, menu, dara)));
			ItemStack mason = jobLine(helper, lines, VillagerProfession.MASON);
			helper.assertTrue(mason.getHoverName().getString().startsWith("Mason · 2 free Stonecutter"), "the Mason line: " + mason.getHoverName().getString());
			helper.assertTrue(lore(mason).contains("The nearest free one: 7 blocks south-east"), "the nearest: " + lore(mason));
			ItemStack armorer = jobLine(helper, lines, VillagerProfession.ARMORER);
			helper.assertTrue(lore(armorer).contains("No Blast Furnace in the village is free: place one, and this job can be picked."), "the hint: " + lore(armorer));
			ItemStack none = lines.get(lines.size() - 1);
			helper.assertTrue(lore(none).contains("Click: see what Dara gives up as a Librarian"), "No job's line: " + lore(none));
			helper.succeed();
		});
	}

	private static ItemStack jobLine(GameTestHelper helper, List<ItemStack> lines, VillagerProfession job) {
		for (ItemStack line : lines) {
			CompoundTag mark = HallJobs.mark(line);
			if (mark != null && mark.getInt("id") == id(job) && !mark.getString("state").equals("header")) {
				return line;
			}
		}
		helper.fail("no line for " + job);
		return ItemStack.EMPTY;
	}

	private static List<String> lore(ItemStack stack) {
		return stack.getOrDefault(net.minecraft.core.component.DataComponents.LORE, net.minecraft.world.item.component.ItemLore.EMPTY).lines().stream()
			.map(Component::getString).toList();
	}

	// --- Bed ▾ (40.4) ---------------------------------------------------------------------------------------------------

	/**
	 * A village bed taken by someone from outside the village: listed as taken with why, and a click on it (a stale or
	 * hand-made screen) changes nobody's bed.
	 */
	//$ gametest_ticks_batch AREA '200' '"qaHallBedOutsider"'
	@GameTest(template = AREA, timeoutTicks = 200, batch = "qaHallBedOutsider")
	public void aBedTakenFromOutsideTheVillage(GameTestHelper helper) {
		village(helper);
		BlockPos near = bed(helper, NEAR);
		Villager dara = villager(helper, "Dara", new BlockPos(12, 2, 15));
		Villager stranger = villager(helper, "Stranger", new BlockPos(1, 2, 4)); // 14 west and 11 north: past a reach of 14
		List<String> seen = new ArrayList<>();
		ServerPlayer player = listeningPlayer(helper, seen);
		helper.runAfterDelay(3, () -> {
			helper.assertTrue(HallBeds.moveIn(helper.getLevel(), stranger, near), "setup: the stranger has no bed");
			ChoiceMenu menu = openPeople(helper, player);
			helper.assertTrue(menu.state(HallSections.PEOPLE_TOTAL) == 1, "the stranger is listed: " + menu.state(HallSections.PEOPLE_TOTAL));
			List<ItemStack> lines = bedDropdown(helper, player, menu, dara);
			ItemStack taken = firstBed(helper, lines, "taken");
			helper.assertTrue(taken.getHoverName().getString().equals("6 blocks north-west · taken"), "the taken line: " + taken.getHoverName().getString());
			helper.assertTrue(lore(taken).contains("Someone from outside the village sleeps here."), "why: " + lore(taken));
			helper.assertTrue(lines.stream().anyMatch(l -> bedState(l).equals("plan")), "no free bed and no Plan a house");
			pickBed(helper, player, menu, taken);
			helper.assertTrue(VillageNeeds.bed(helper.getLevel(), dara) == null && near.equals(home(stranger)), "a bed changed hands: " + home(dara));
			helper.succeed();
		});
	}

	/**
	 * A swap partner who moved to another bed after the list was laid out: the stale "swap with Jem" changes nothing and
	 * says the bed changed hands; the list then shows the bed as free.
	 */
	//$ gametest_ticks_batch AREA '200' '"qaHallBedChangedHands"'
	@GameTest(template = AREA, timeoutTicks = 200, batch = "qaHallBedChangedHands")
	public void aSwapAfterThePartnerMoved(GameTestHelper helper) {
		village(helper);
		BlockPos near = bed(helper, NEAR);
		BlockPos far = bed(helper, FAR);
		BlockPos third = bed(helper, new BlockPos(18, 2, 8));
		Villager dara = villager(helper, "Dara", new BlockPos(12, 2, 15));
		Villager jem = villager(helper, "Jem", new BlockPos(18, 2, 15));
		List<String> seen = new ArrayList<>();
		ServerPlayer player = listeningPlayer(helper, seen);
		helper.runAfterDelay(3, () -> {
			helper.assertTrue(HallBeds.moveIn(helper.getLevel(), dara, near) && HallBeds.moveIn(helper.getLevel(), jem, far), "setup: no beds");
			ChoiceMenu menu = openPeople(helper, player);
			ItemStack swap = firstBed(helper, bedDropdown(helper, player, menu, dara), "swap");
			helper.assertTrue(HallBeds.moveIn(helper.getLevel(), jem, third), "setup: Jem didn't move");
			pickBed(helper, player, menu, swap);
			helper.assertTrue(near.equals(VillageNeeds.bed(helper.getLevel(), dara)) && third.equals(VillageNeeds.bed(helper.getLevel(), jem)),
				"beds after a stale swap: Dara " + home(dara) + ", Jem " + home(jem));
			helper.assertTrue(helper.getLevel().getPoiManager().getFreeTickets(far) == 1, "Jem's old bed isn't free");
			helper.assertTrue(seen.contains("That bed changed hands: the list shows it as it is now."), "not told: " + seen);
			List<ItemStack> now = HallBeds.lines(menu.icon(slotOf(helper, menu, dara)));
			helper.assertTrue(now.stream().anyMatch(l -> bedState(l).equals("free")), "the list doesn't show Jem's old bed free now");
			helper.succeed();
		});
	}

	/** Her own bed picked (the "their bed now" line, as a stale screen could send it): nothing changes, the bed stays hers and claimed. */
	//$ gametest_ticks_batch AREA '200' '"qaHallBedOwn"'
	@GameTest(template = AREA, timeoutTicks = 200, batch = "qaHallBedOwn")
	public void pickingTheirOwnBedChangesNothing(GameTestHelper helper) {
		village(helper);
		BlockPos near = bed(helper, NEAR);
		bed(helper, FAR);
		Villager dara = villager(helper, "Dara", new BlockPos(12, 2, 15));
		List<String> seen = new ArrayList<>();
		ServerPlayer player = listeningPlayer(helper, seen);
		helper.runAfterDelay(3, () -> {
			helper.assertTrue(HallBeds.moveIn(helper.getLevel(), dara, near), "setup: no bed");
			ChoiceMenu menu = openPeople(helper, player);
			List<ItemStack> lines = bedDropdown(helper, player, menu, dara);
			ItemStack mine = firstBed(helper, lines, "current");
			pickBed(helper, player, menu, mine);
			pickBed(helper, player, menu, mine);
			helper.assertTrue(near.equals(home(dara)) && near.equals(VillageNeeds.bed(helper.getLevel(), dara)), "her home: " + home(dara));
			helper.assertTrue(helper.getLevel().getPoiManager().getFreeTickets(near) == 0, "her bed was freed");
			helper.assertTrue(housed(helper) == 1, "housed: " + housed(helper));
			helper.assertTrue(seen.stream().noneMatch(s -> s.contains("sleeps at") || s.contains("swapped")), "told of a move: " + seen);
			helper.succeed();
		});
	}

	/**
	 * A swap whose partner's bed is broken after the list was laid out: the click changes nothing, Dara keeps her bed
	 * (nobody gets it), the player is told the bed is gone, and the census counts one housed.
	 */
	//$ gametest_ticks_batch AREA '200' '"qaHallBedSwapBroken"'
	@GameTest(template = AREA, timeoutTicks = 200, batch = "qaHallBedSwapBroken")
	public void swapWithABrokenBed(GameTestHelper helper) {
		village(helper);
		BlockPos near = bed(helper, NEAR);
		BlockPos far = bed(helper, FAR);
		Villager dara = villager(helper, "Dara", new BlockPos(12, 2, 15));
		Villager jem = villager(helper, "Jem", new BlockPos(18, 2, 15));
		List<String> seen = new ArrayList<>();
		ServerPlayer player = listeningPlayer(helper, seen);
		helper.runAfterDelay(3, () -> {
			helper.assertTrue(HallBeds.moveIn(helper.getLevel(), dara, near) && HallBeds.moveIn(helper.getLevel(), jem, far), "setup: no beds");
			ChoiceMenu menu = openPeople(helper, player);
			List<ItemStack> lines = bedDropdown(helper, player, menu, dara);
			ItemStack swap = firstBed(helper, lines, "swap");
			helper.assertTrue(swap.getHoverName().getString().endsWith("swap with Jem"), "the swap line: " + swap.getHoverName().getString());
			breakBed(helper, FAR);
			pickBed(helper, player, menu, swap);
			helper.assertTrue(near.equals(VillageNeeds.bed(helper.getLevel(), dara)), "Dara lost her bed: " + home(dara));
			helper.assertTrue(VillageNeeds.bed(helper.getLevel(), jem) == null, "Jem has a bed: " + VillageNeeds.bed(helper.getLevel(), jem));
			helper.assertTrue(helper.getLevel().getPoiManager().getFreeTickets(near) == 0, "Dara's bed was freed");
			helper.assertTrue(housed(helper) == 1, "housed: " + housed(helper));
			helper.assertTrue(seen.contains("That bed is gone."), "not told: " + seen);
			helper.succeed();
		});
	}

	/**
	 * Only the village's beds: a bed just past the hall's reach isn't on the list, one inside is; the dead can't be given a
	 * bed (the list open, she dies, the pick leaves the bed free).
	 */
	//$ gametest_ticks_batch AREA '200' '"qaHallBedOutside"'
	@GameTest(template = AREA, timeoutTicks = 200, batch = "qaHallBedOutside")
	public void onlyTheVillagesBedsAndNotForTheDead(GameTestHelper helper) {
		village(helper);
		BlockPos inside = bed(helper, NEAR);
		BlockPos outside = bed(helper, new BlockPos(1, 2, 1)); // head at (1, 2, 2): 14 west and 13 north, past a reach of 14
		Villager dara = villager(helper, "Dara", new BlockPos(12, 2, 15));
		List<String> seen = new ArrayList<>();
		ServerPlayer player = listeningPlayer(helper, seen);
		helper.runAfterDelay(3, () -> {
			helper.assertTrue(!VillageHalls.contains(helper.absolutePos(HALL), outside) && VillageHalls.contains(helper.absolutePos(HALL), inside), "setup: reach");
			ChoiceMenu menu = openPeople(helper, player);
			List<ItemStack> lines = bedDropdown(helper, player, menu, dara);
			long beds = lines.stream().filter(l -> List.of("free", "swap", "taken", "current").contains(bedState(l))).count();
			helper.assertTrue(beds == 1, "beds on the list: " + lines.stream().map(l -> l.getHoverName().getString()).toList());
			ItemStack free = firstBed(helper, lines, "free");
			dara.kill();
			pickBed(helper, player, menu, free);
			helper.assertTrue(helper.getLevel().getPoiManager().getFreeTickets(inside) == 1, "a dead villager claimed the bed");
			helper.assertTrue(seen.stream().noneMatch(s -> s.contains("sleeps at")), "told of a move: " + seen);
			helper.succeed();
		});
	}

	/**
	 * The Mood column's "No bed": a jobless villager with no bed and not happy reads "No bed"; given a bed from Bed ▾, her
	 * row stops saying so straight away; her bed broken, it says "No bed" again.
	 */
	//$ gametest_ticks_batch AREA '200' '"qaHallMoodNoBed"'
	@GameTest(template = AREA, timeoutTicks = 200, batch = "qaHallMoodNoBed")
	public void moodSaysNoBedUntilGivenOne(GameTestHelper helper) {
		village(helper);
		boolean was = Moods.ENABLED;
		Moods.ENABLED = true;
		Leftovers.after(helper, () -> Moods.ENABLED = was);
		Moods.forget();
		BlockPos near = bed(helper, NEAR);
		Villager pip = villager(helper, "Pip", new BlockPos(12, 2, 15));
		List<String> seen = new ArrayList<>();
		ServerPlayer player = listeningPlayer(helper, seen);
		helper.runAfterDelay(3, () -> {
			ChoiceMenu menu = openPeople(helper, player);
			Moods.Mood mood = Moods.of(pip);
			helper.assertTrue(mood != null && mood.score() < Moods.HAPPY, "setup: Pip is happy with no bed and no job: " + (mood == null ? null : mood.score()));
			helper.assertTrue(row(helper, menu, pip).getString("mood").equals("no_bed"), "with no bed: " + row(helper, menu, pip).getString("mood"));
			List<ItemStack> lines = bedDropdown(helper, player, menu, pip);
			pickBed(helper, player, menu, firstBed(helper, lines, "free"));
			helper.assertTrue(near.equals(VillageNeeds.bed(helper.getLevel(), pip)), "no bed: " + home(pip));
			helper.assertTrue(!row(helper, menu, pip).getString("mood").equals("no_bed"), "still No bed with a bed: " + row(helper, menu, pip));
			breakBed(helper, NEAR);
			ChoiceMenu again = openPeople(helper, player);
			helper.assertTrue(row(helper, again, pip).getString("mood").equals("no_bed") || Moods.of(pip).score() >= Moods.HAPPY,
				"a broken bed: " + row(helper, again, pip).getString("mood"));
			helper.assertTrue(housed(helper) == 0, "a broken bed houses: " + housed(helper));
			helper.succeed();
		});
	}

	/** A swap from the table, saved and loaded: each keeps the other's old bed, and both beds stay claimed. */
	//$ gametest_ticks_batch AREA '200' '"qaHallBedSwapSaved"'
	@GameTest(template = AREA, timeoutTicks = 200, batch = "qaHallBedSwapSaved")
	public void aSwapSurvivesASaveAndReload(GameTestHelper helper) {
		village(helper);
		BlockPos near = bed(helper, NEAR);
		BlockPos far = bed(helper, FAR);
		Villager dara = villager(helper, "Dara", new BlockPos(12, 2, 15));
		Villager jem = villager(helper, "Jem", new BlockPos(18, 2, 15));
		List<String> seen = new ArrayList<>();
		ServerPlayer player = listeningPlayer(helper, seen);
		helper.runAfterDelay(3, () -> {
			helper.assertTrue(HallBeds.moveIn(helper.getLevel(), dara, near) && HallBeds.moveIn(helper.getLevel(), jem, far), "setup: no beds");
			ChoiceMenu menu = openPeople(helper, player);
			pickBed(helper, player, menu, firstBed(helper, bedDropdown(helper, player, menu, dara), "swap"));
			helper.assertTrue(seen.contains("Dara and Jem swapped beds."), "not told: " + seen);
			for (Villager v : List.of(dara, jem)) {
				Villager copy = EntityType.VILLAGER.create(helper.getLevel());
				copy.load(v.saveWithoutId(new CompoundTag()));
				BlockPos expected = v == dara ? far : near;
				helper.assertTrue(expected.equals(home(copy)), v.getName().getString() + " loaded with home " + home(copy));
				copy.discard();
			}
			helper.assertTrue(helper.getLevel().getPoiManager().getFreeTickets(near) == 0 && helper.getLevel().getPoiManager().getFreeTickets(far) == 0,
				"a swapped bed was left free");
			helper.assertTrue(housed(helper) == 2, "housed: " + housed(helper));
			helper.succeed();
		});
	}

	/**
	 * A big village scrolled to its end: Bed ▾ and a pick on the last villager's row give that villager the bed (not
	 * whoever sat in that slot before the scroll), and the table stays scrolled where it was.
	 */
	//$ gametest_ticks_batch AREA '300' '"qaHallBedScrolled"'
	@GameTest(template = AREA, timeoutTicks = 300, batch = "qaHallBedScrolled")
	public void aBedForSomeoneFarDownTheTable(GameTestHelper helper) {
		village(helper);
		BlockPos near = bed(helper, NEAR);
		List<Villager> all = new ArrayList<>();
		for (int i = 0; i < 36; i++) {
			all.add(villager(helper, "V" + i, new BlockPos(6 + (i % 9) * 2, 2, 5 + (i / 9) * 2 + (i / 9 >= 2 ? 12 : 0))));
		}
		List<String> seen = new ArrayList<>();
		ServerPlayer player = listeningPlayer(helper, seen);
		helper.runAfterDelay(3, () -> {
			ChoiceMenu menu = openPeople(helper, player);
			helper.assertTrue(menu.state(HallSections.PEOPLE_TOTAL) == 36, "listed: " + menu.state(HallSections.PEOPLE_TOTAL));
			helper.assertTrue(menu.clickMenuButton(player, HallPeople.SCROLL + 36), "the scroll wasn't taken");
			int first = menu.state(HallSections.PEOPLE_FIRST);
			helper.assertTrue(first > 0, "the table didn't scroll: " + first);
			// The last row on the screen now.
			Villager last = null;
			for (int slot = ChoiceMenu.SIZE - 1; slot >= 0 && last == null; slot--) {
				CompoundTag r = HallPeople.row(menu.icon(slot));
				if (r != null) {
					for (Villager v : all) {
						if (v.getStringUUID().equals(r.getString("uuid"))) {
							last = v;
						}
					}
				}
			}
			helper.assertTrue(last != null, "no rows after the scroll");
			Villager chosen = last;
			List<ItemStack> lines = bedDropdown(helper, player, menu, chosen);
			helper.assertTrue(menu.state(HallSections.PEOPLE_FIRST) == first, "Bed ▾ moved the table: " + menu.state(HallSections.PEOPLE_FIRST));
			pickBed(helper, player, menu, firstBed(helper, lines, "free"));
			helper.assertTrue(near.equals(VillageNeeds.bed(helper.getLevel(), chosen)), chosen.getName().getString() + " didn't get the bed");
			helper.assertTrue(all.stream().filter(v -> v != chosen).noneMatch(v -> VillageNeeds.bed(helper.getLevel(), v) != null), "someone else got the bed");
			helper.assertTrue(menu.state(HallSections.PEOPLE_FIRST) == first, "the pick moved the table: " + menu.state(HallSections.PEOPLE_FIRST));
			helper.succeed();
		});
	}

	/**
	 * Bed ▾ isn't Job ▾: with game rule workplaceRetraining off (Job ▾ hidden), a nitwit, who can't change jobs, is still
	 * given a bed from the table, and the census counts them housed.
	 */
	//$ gametest_ticks_batch AREA '200' '"qaHallBedRuleOff"'
	@GameTest(template = AREA, timeoutTicks = 200, batch = "qaHallBedRuleOff")
	public void aNitwitGetsABedWithRetrainingOff(GameTestHelper helper) {
		village(helper);
		BlockPos near = bed(helper, NEAR);
		Villager nit = villager(helper, "Nit", new BlockPos(12, 2, 15));
		nit.setVillagerData(nit.getVillagerData().setProfession(VillagerProfession.NITWIT));
		List<String> seen = new ArrayList<>();
		ServerPlayer player = listeningPlayer(helper, seen);
		net.minecraft.server.level.ServerLevel level = helper.getLevel();
		helper.runAfterDelay(3, () -> {
			level.getGameRules().getRule(io.github.jcondedata.aliveworkplace.registry.ModGameRules.RETRAINING).set(false, level.getServer());
			try {
				ChoiceMenu menu = openPeople(helper, player);
				helper.assertTrue(row(helper, menu, nit).getBoolean("job_hidden") && row(helper, menu, nit).getString("kind").equals("nitwit"),
					"the nitwit's row: " + row(helper, menu, nit));
				List<ItemStack> lines = bedDropdown(helper, player, menu, nit);
				helper.assertTrue(row(helper, menu, nit).getBoolean("beds_open"), "Bed ▾ hidden with the rule off");
				pickBed(helper, player, menu, firstBed(helper, lines, "free"));
				helper.assertTrue(near.equals(VillageNeeds.bed(level, nit)) && housed(helper) == 1, "the nitwit has no bed: " + home(nit));
				helper.assertTrue(seen.stream().anyMatch(s -> s.startsWith("Nit sleeps at ")), "not told: " + seen);
			} finally {
				level.getGameRules().getRule(io.github.jcondedata.aliveworkplace.registry.ModGameRules.RETRAINING).set(true, level.getServer());
			}
			helper.succeed();
		});
	}

	// --- the sidebar (40.1) ---------------------------------------------------------------------------------------------

	/**
	 * Homes & Food and Buildings in a hall with no beds, no food and nothing going up say they're empty (and keep their
	 * buttons); a bed placed, Homes & Food isn't. The section chosen last is the one a new screen opens on, even after the
	 * People table and a list were used.
	 */
	//$ gametest_ticks_batch AREA '200' '"qaHallSidebarHomes"'
	@GameTest(template = AREA, timeoutTicks = 200, batch = "qaHallSidebarHomes")
	public void homesAndBuildingsStartEmpty(GameTestHelper helper) {
		village(helper);
		List<String> seen = new ArrayList<>();
		ServerPlayer player = listeningPlayer(helper, seen);
		helper.runAfterDelay(3, () -> {
			ChoiceMenu menu = openPeople(helper, player);
			for (Section s : List.of(Section.HOMES, Section.BUILDINGS)) {
				helper.assertTrue(menu.clickMenuButton(player, s.ordinal()), "the sidebar didn't take " + s);
				helper.assertTrue(HallSections.current(menu) == s, "open: " + HallSections.current(menu));
				helper.assertTrue(HallSections.isEmpty(menu, s), s + " doesn't say it's empty");
				helper.assertTrue(!HallSections.slots(menu, s).isEmpty(), s + " lost its buttons");
			}
			helper.assertTrue(Component.translatable("screen.aliveworkplace.hall.section.homes.empty").getString()
				.equals("No beds and no food yet. Place beds near the hall and keep food in a chest."), "the Homes sentence");
			helper.assertTrue(Component.translatable("screen.aliveworkplace.hall.section.buildings.empty").getString().equals("Nothing is being built right now."),
				"the Buildings sentence");
			player.closeContainer();
			bed(helper, NEAR);
			helper.runAfterDelay(2, () -> {
				helper.useBlock(HALL, player);
				ChoiceMenu again = (ChoiceMenu) player.containerMenu;
				helper.assertTrue(HallSections.current(again) == Section.BUILDINGS, "reopened on " + HallSections.current(again));
				helper.assertTrue(again.clickMenuButton(player, Section.HOMES.ordinal()), "the sidebar didn't take Homes");
				helper.assertTrue(!HallSections.isEmpty(again, Section.HOMES), "Homes & Food still empty with a bed");
				player.closeContainer();
				helper.useBlock(HALL, player);
				helper.assertTrue(HallSections.current((ChoiceMenu) player.containerMenu) == Section.HOMES, "not remembered: Homes");
				helper.succeed();
			});
		});
	}
}
