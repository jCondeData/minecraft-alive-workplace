package io.github.jcondedata.aliveworkplace.test;

import static io.github.jcondedata.aliveworkplace.test.StationsSpecGameTests.listeningPlayer;

import io.github.jcondedata.aliveworkplace.guard.Mercenaries;
import io.github.jcondedata.aliveworkplace.guard.Relief;
import io.github.jcondedata.aliveworkplace.hall.Chronicle;
import io.github.jcondedata.aliveworkplace.hall.FreeJobsPage;
import io.github.jcondedata.aliveworkplace.hall.HallPages;
import io.github.jcondedata.aliveworkplace.hall.VillageHallScreen;
import io.github.jcondedata.aliveworkplace.hall.VillageHalls;
import io.github.jcondedata.aliveworkplace.people.LifeStages;
import io.github.jcondedata.aliveworkplace.people.Retirement;
import io.github.jcondedata.aliveworkplace.registry.ModAttachments;
import io.github.jcondedata.aliveworkplace.registry.ModBlocks;
import io.github.jcondedata.aliveworkplace.registry.ModVillagers;
import io.github.jcondedata.aliveworkplace.story.Arcs;
import io.github.jcondedata.aliveworkplace.work.ChoiceMenu;
import io.github.jcondedata.aliveworkplace.work.Jobs;
import io.github.jcondedata.aliveworkplace.work.Stations;
import io.github.jcondedata.aliveworkplace.work.Words;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.component.DataComponents;
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
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.ItemLore;
import net.minecraft.world.level.block.Blocks;

/**
 * QA (lane-a, 2026-11-04): B892 and B906 checked from the spec, through the player's own entry points.
 * <p>
 * B892: the Village Hall's "Free job blocks" page offers a free block to nobody the hall refuses: not a retired elder (a
 * plain elder still is), not a relief rider or Doctor Wren (an ordinary villager beside them still is), not after a save
 * and reload either; and once a mercenary's contract ends, the guard post he held is free and offered to the village.
 * <p>
 * B906: a sneak-right-click with a job's item (the real interact packet) refuses a hired visitor with "X's job can't be
 * changed here." (a mercenary at his post, a relief rider, a reloaded mercenary), while an ordinary villager, a plain
 * elder and a retired elder each still get their own answer.
 */
public class QaHallVisitorJobs1104cGameTests implements net.fabricmc.fabric.api.gametest.v1.FabricGameTest {
	// --- B892: the free-jobs page (the big_area village of FreeJobsRefusalGameTests) -------------------------------------
	private static final String FREE_AREA = "aliveworkplace_test:big_area";
	private static final BlockPos FREE_HALL = new BlockPos(11, 2, 11);
	/** The Porter's block, the only free job block in the village. */
	private static final BlockPos STOREHOUSE = new BlockPos(5, 2, 8);

	// --- B906: the job item (the huge_area village of QaHallScreenGameTests) ------------------------------------------
	private static final String ITEM_AREA = "aliveworkplace_test:huge_area";
	private static final BlockPos ITEM_HALL = new BlockPos(15, 2, 15);
	private static final BlockPos CUTTER = new BlockPos(14, 2, 13);
	private static final BlockPos BY_CUTTER = new BlockPos(13, 2, 14);

	private static void freeVillage(GameTestHelper helper, BlockPos block, net.minecraft.world.level.block.Block kind) {
		Leftovers.clear(helper);
		int radius = VillageHalls.RADIUS;
		VillageHalls.RADIUS = 16;
		Leftovers.after(helper, () -> VillageHalls.RADIUS = radius);
		helper.setBlock(FREE_HALL, ModBlocks.VILLAGE_HALL);
		helper.setBlock(block, kind);
	}

	private static Villager jobless(GameTestHelper helper, String name, BlockPos at) {
		Villager v = helper.spawn(EntityType.VILLAGER, at);
		v.setNoAi(true);
		v.setVillagerData(v.getVillagerData().setProfession(VillagerProfession.NONE));
		v.setCustomName(Component.literal(name));
		return v;
	}

	private static long today(GameTestHelper helper) {
		return Chronicle.day(helper.getLevel());
	}

	/** Grown 1300 days ago: past LifeStages.ELDER_DAYS. */
	private static void makeElder(GameTestHelper helper, Villager v) {
		LifeStages.setAdultSince(v, today(helper) - (LifeStages.ELDER_DAYS + 100));
	}

	private static void retire(GameTestHelper helper, Villager v) {
		makeElder(helper, v);
		ModAttachments.RETIRED.set(v, new Retirement.Retired(today(helper) - 1, "minecraft:farmer", 3));
	}

	private static void click(ChoiceMenu menu, int slot, ServerPlayer player) {
		menu.clicked(slot, 0, ClickType.PICKUP, player);
	}

	private static List<String> lore(ItemStack stack) {
		return stack.getOrDefault(DataComponents.LORE, ItemLore.EMPTY).lines().stream().map(Component::getString).toList();
	}

	/** The hall's Free job blocks page, as the player opens it (the tab), with the first row checked to be {@code kind}. */
	private static ChoiceMenu openList(GameTestHelper helper, ServerPlayer player, net.minecraft.world.item.Item kind) {
		ChoiceMenu menu = VillageHallScreen.forTest(player, helper.absolutePos(FREE_HALL));
		click(menu, HallPages.slot(FreeJobsPage.PAGE), player);
		helper.assertTrue(menu.icon(VillageHallScreen.FIRST_ROW).is(kind), "the free block isn't listed first: " + menu.icon(VillageHallScreen.FIRST_ROW));
		return menu;
	}

	/** Then the block's own line: its page, with who could take it. */
	private static ChoiceMenu openBlock(GameTestHelper helper, ServerPlayer player, net.minecraft.world.item.Item kind) {
		ChoiceMenu menu = openList(helper, player, kind);
		click(menu, VillageHallScreen.FIRST_ROW, player);
		helper.assertTrue(menu.icon(4).is(kind), "the block's page didn't open: " + menu.icon(4));
		return menu;
	}

	private static String offer(Villager v, VillagerProfession job) {
		return Component.translatable("screen.aliveworkplace.hall.give_job", v.getDisplayName(), Stations.name(job)).getString();
	}

	private static List<String> offered(ChoiceMenu menu) {
		List<String> out = new ArrayList<>();
		for (int slot = VillageHallScreen.FIRST_ROW; slot < ChoiceMenu.SIZE; slot++) {
			out.addAll(lore(menu.icon(slot)));
		}
		return out;
	}

	/** The row on the block's page that offers it to {@code v}, or -1. */
	private static int rowOf(ChoiceMenu menu, Villager v, VillagerProfession job) {
		for (int slot = VillageHallScreen.FIRST_ROW; slot < ChoiceMenu.SIZE; slot++) {
			if (lore(menu.icon(slot)).contains(offer(v, job))) {
				return slot;
			}
		}
		return -1;
	}

	private static String takersLine(int n) {
		return Words.counted("screen.aliveworkplace.free_jobs.takers", n, n).getString();
	}

	/** {@code v} as a save keeps them: written out and read back into a new villager (a new UUID), put in the world. */
	private static Villager reload(GameTestHelper helper, Villager v) {
		ServerLevel level = helper.getLevel();
		CompoundTag saved = v.saveWithoutId(new CompoundTag());
		saved.remove("UUID");
		v.discard();
		Villager copy = EntityType.VILLAGER.create(level);
		copy.load(saved);
		copy.setUUID(java.util.UUID.randomUUID());
		level.addFreshEntity(copy);
		copy.setNoAi(true);
		Leftovers.after(helper, copy::discard);
		return copy;
	}

	// ================================================ B892 =========================================================

	/**
	 * A plain elder (old, never retired) is still the village's to employ: offered the Storehouse beside a retired elder who
	 * isn't, the list's line counts one taker, and the click makes the plain elder the Porter.
	 */
	//$ gametest_ticks_batch FREE_AREA '100' '"qaFreeJobsElders1104c"'
	@GameTest(template = FREE_AREA, timeoutTicks = 100, batch = "qaFreeJobsElders1104c")
	public void freeJobsOffersAPlainElderButNotARetiredOne(GameTestHelper helper) {
		freeVillage(helper, STOREHOUSE, ModBlocks.STOREHOUSE);
		Villager bram = jobless(helper, "Bram", new BlockPos(9, 2, 12)); // nearer the hall
		Villager cora = jobless(helper, "Cora", new BlockPos(14, 2, 15));
		List<String> seen = new ArrayList<>();
		ServerPlayer player = listeningPlayer(helper, seen);
		helper.runAfterDelay(2, () -> {
			retire(helper, bram);
			makeElder(helper, cora);
			helper.assertTrue(LifeStages.isElder(cora) && !Retirement.isRetired(cora), "setup: Cora isn't a plain elder");
			ChoiceMenu menu = openList(helper, player, ModBlocks.STOREHOUSE.asItem());
			List<String> row = lore(menu.icon(VillageHallScreen.FIRST_ROW));
			helper.assertTrue(row.contains(takersLine(1)) && row.contains("1 villager without a job could take it"),
				"the Storehouse's line doesn't count one taker: " + row);
			click(menu, VillageHallScreen.FIRST_ROW, player);
			List<String> lines = offered(menu);
			helper.assertTrue(rowOf(menu, bram, ModVillagers.PORTER) < 0, "the retired elder is offered the Storehouse: " + lines);
			int coraRow = rowOf(menu, cora, ModVillagers.PORTER);
			helper.assertTrue(coraRow >= 0, "the plain elder isn't offered it: " + lines);
			helper.assertTrue(lines.contains("Click: Cora becomes a Porter"), "the offer reads differently: " + lines);
			click(menu, coraRow, player);
			helper.assertTrue(cora.getVillagerData().getProfession() == ModVillagers.PORTER, "Cora isn't a Porter: " + cora.getVillagerData());
			helper.assertTrue(seen.contains("Cora is now a Porter"), "not told Cora is now a Porter: " + seen);
			helper.assertTrue(bram.getVillagerData().getProfession() == VillagerProfession.NONE, "the retired elder works again: " + bram.getVillagerData());
			helper.succeed();
		});
	}

	/**
	 * A relief rider without a job and Doctor Wren (the Sickness story's Cleric, without a stand of her own) aren't offered
	 * the Storehouse; the ordinary villager beside them is, and is the only taker the list counts.
	 */
	//$ gametest_ticks_batch FREE_AREA '100' '"qaFreeJobsVisitors1104c"'
	@GameTest(template = FREE_AREA, timeoutTicks = 100, batch = "qaFreeJobsVisitors1104c")
	public void freeJobsOffersNeitherARiderNorDoctorWrenButAVillager(GameTestHelper helper) {
		freeVillage(helper, STOREHOUSE, ModBlocks.STOREHOUSE);
		Villager rider = jobless(helper, "Rider", new BlockPos(9, 2, 12));
		Villager wren = jobless(helper, "Doctor Wren", new BlockPos(10, 2, 13));
		Villager ada = jobless(helper, "Ada", new BlockPos(14, 2, 15));
		List<String> seen = new ArrayList<>();
		ServerPlayer player = listeningPlayer(helper, seen);
		helper.runAfterDelay(2, () -> {
			ModAttachments.RELIEF_RIDER.set(rider, new Relief.Rider(helper.absolutePos(FREE_HALL),
				GlobalPos.of(helper.getLevel().dimension(), helper.absolutePos(FREE_HALL))));
			wren.addTag(Arcs.MOB_TAG);
			wren.setVillagerData(wren.getVillagerData().setProfession(VillagerProfession.CLERIC).setLevel(3));
			wren.setVillagerXp(70);
			ChoiceMenu menu = openList(helper, player, ModBlocks.STOREHOUSE.asItem());
			List<String> row = lore(menu.icon(VillageHallScreen.FIRST_ROW));
			helper.assertTrue(row.contains(takersLine(1)), "the Storehouse's line doesn't count just Ada: " + row);
			click(menu, VillageHallScreen.FIRST_ROW, player);
			List<String> lines = offered(menu);
			helper.assertTrue(rowOf(menu, rider, ModVillagers.PORTER) < 0, "the relief rider is offered the Storehouse: " + lines);
			helper.assertTrue(rowOf(menu, wren, ModVillagers.PORTER) < 0, "Doctor Wren is offered the Storehouse: " + lines);
			int adaRow = rowOf(menu, ada, ModVillagers.PORTER);
			helper.assertTrue(adaRow == VillageHallScreen.FIRST_ROW && menu.icon(VillageHallScreen.FIRST_ROW + 1).isEmpty(),
				"Ada isn't the one taker: " + lines);
			click(menu, adaRow, player);
			helper.assertTrue(ada.getVillagerData().getProfession() == ModVillagers.PORTER, "Ada isn't a Porter: " + ada.getVillagerData());
			helper.assertTrue(rider.getVillagerData().getProfession() == VillagerProfession.NONE, "the rider was given a job: " + rider.getVillagerData());
			helper.assertTrue(wren.getVillagerData().getProfession() == VillagerProfession.CLERIC, "Doctor Wren lost her Cleric's job: " + wren.getVillagerData());
			helper.assertTrue(seen.contains("Ada is now a Porter") && seen.stream().noneMatch(s -> s.equals("Someone took that workstation first")),
				"told something else: " + seen);
			helper.succeed();
		});
	}

	/** A retired elder saved and loaded again is still retired and still not offered the free block; Ada still is. */
	//$ gametest_ticks_batch FREE_AREA '100' '"qaFreeJobsReload1104c"'
	@GameTest(template = FREE_AREA, timeoutTicks = 100, batch = "qaFreeJobsReload1104c")
	public void freeJobsStillLeavesOutARetiredElderAfterAReload(GameTestHelper helper) {
		freeVillage(helper, STOREHOUSE, ModBlocks.STOREHOUSE);
		Villager bram = jobless(helper, "Bram", new BlockPos(9, 2, 12));
		Villager ada = jobless(helper, "Ada", new BlockPos(14, 2, 15));
		ServerPlayer player = listeningPlayer(helper, new ArrayList<>());
		helper.runAfterDelay(2, () -> {
			retire(helper, bram);
			Villager back = reload(helper, bram);
			helper.assertTrue(Retirement.isRetired(back) && back.getDisplayName().getString().equals("Bram"), "the reload lost the retirement or the name");
			helper.runAfterDelay(2, () -> {
				ChoiceMenu menu = openBlock(helper, player, ModBlocks.STOREHOUSE.asItem());
				List<String> lines = offered(menu);
				helper.assertTrue(!lines.contains("Click: Bram becomes a Porter"), "the reloaded retired elder is offered the Storehouse: " + lines);
				helper.assertTrue(rowOf(menu, ada, ModVillagers.PORTER) == VillageHallScreen.FIRST_ROW, "Ada isn't offered it: " + lines);
				helper.succeed();
			});
		});
	}

	/**
	 * While a hired mercenary holds a guard post it isn't a free block; when his contract ends he leaves, the post is free
	 * again, and the page offers it to a villager of the village, whose click makes them its Guard.
	 */
	//$ gametest_ticks_batch FREE_AREA '200' '"qaFreeJobsMercGone1104c"'
	@GameTest(template = FREE_AREA, timeoutTicks = 200, batch = "qaFreeJobsMercGone1104c")
	public void freeJobsOffersAGuardPostOnceTheMercenarysContractEnds(GameTestHelper helper) {
		BlockPos post = STOREHOUSE;
		freeVillage(helper, post, ModBlocks.GUARD_POST);
		ServerLevel level = helper.getLevel();
		BlockPos hall = helper.absolutePos(FREE_HALL);
		List<String> seen = new ArrayList<>();
		ServerPlayer player = QaHallScreenGameTests.payer(helper, seen, 60);
		Villager ada = jobless(helper, "Ada", new BlockPos(14, 2, 15));
		helper.runAfterDelay(3, () -> {
			Mercenaries.hire(level, hall, player);
			List<Villager> band = Mercenaries.near(level, hall);
			Leftovers.after(helper, () -> band.forEach(Villager::discard));
			helper.assertTrue(band.size() == Mercenaries.BAND, "setup: band " + band.size());
			Villager merc = band.get(0); // keeps his AI: his contract's end runs in his brain's server step
			band.subList(1, band.size()).forEach(v -> v.setNoAi(true)); // a band-mate with AI would take the post he leaves
			Jobs.employ(level, merc, helper.absolutePos(post), ModVillagers.GUARD_POST_POI, ModVillagers.GUARD);
			helper.assertTrue(level.getPoiManager().getFreeTickets(helper.absolutePos(post)) == 0, "setup: the mercenary doesn't hold the post");
			String none = Words.counted("screen.aliveworkplace.free_jobs.count", 0, 0).getString();
			ItemStack tab = VillageHallScreen.forTest(player, hall).icon(HallPages.slot(FreeJobsPage.PAGE));
			helper.assertTrue(lore(tab).contains(none), "the post he holds counts as free: " + lore(tab));
			ModAttachments.MERCENARY_UNTIL.set(merc, level.getGameTime()); // his time is up
			helper.runAfterDelay(5, () -> {
				helper.assertTrue(merc.isRemoved(), "the mercenary whose contract ended is still here");
				helper.assertTrue(level.getPoiManager().getFreeTickets(helper.absolutePos(post)) == 1, "the post isn't free after he left");
				ChoiceMenu menu = openBlock(helper, player, ModBlocks.GUARD_POST.asItem());
				int adaRow = rowOf(menu, ada, ModVillagers.GUARD);
				helper.assertTrue(adaRow == VillageHallScreen.FIRST_ROW, "Ada isn't offered the post: " + offered(menu));
				for (Villager other : band) {
					helper.assertTrue(other == merc || rowOf(menu, other, ModVillagers.GUARD) < 0, "a mercenary still hired is offered the post");
				}
				seen.clear();
				click(menu, adaRow, player);
				helper.assertTrue(ada.getVillagerData().getProfession() == ModVillagers.GUARD, "Ada isn't a Guard: " + ada.getVillagerData());
				helper.assertTrue(seen.contains("Ada is now a Guard"), "not told: " + seen);
				helper.succeed();
			});
		});
	}

	// ================================================ B906 =========================================================

	private static final String CANT = "message.aliveworkplace.hall.jobs.cant";

	private static String cant(Villager v) {
		return Component.translatable(CANT, v.getDisplayName()).getString();
	}

	/** One sneak-right-click with clay (the Mason's item) through the game's interact packet, as the player's own. */
	static void clay(ServerPlayer player, Villager v) {
		StationsSpecGameTests.rightClick(player, v, new ItemStack(Items.CLAY_BALL), true);
	}

	static List<Villager> hireBand(GameTestHelper helper, ServerPlayer player) {
		ServerLevel level = helper.getLevel();
		BlockPos hall = helper.absolutePos(ITEM_HALL);
		Mercenaries.hire(level, hall, player);
		List<Villager> band = Mercenaries.near(level, hall);
		Leftovers.after(helper, () -> band.forEach(Villager::discard));
		helper.assertTrue(band.size() == Mercenaries.BAND, "setup: band " + band.size());
		return band;
	}

	static void standByCutter(GameTestHelper helper, Villager v) {
		v.setNoAi(true);
		BlockPos at = helper.absolutePos(BY_CUTTER);
		v.teleportTo(at.getX() + 0.5, at.getY(), at.getZ() + 0.5);
	}

	private static int cutterFree(GameTestHelper helper) {
		return helper.getLevel().getPoiManager().getFreeTickets(helper.absolutePos(CUTTER));
	}

	/**
	 * A mercenary at the guard post he took, sneak-clicked twice with clay by a free Stonecutter through the real packet:
	 * he stays the Guard at his post, nothing is paid, the Stonecutter stays free, and the player reads exactly
	 * "Mercenary's job can't be changed here." (and no "took the Mason job").
	 */
	//$ gametest_ticks_batch ITEM_AREA '200' '"qaJobItemMercPacket1104c"'
	@GameTest(template = ITEM_AREA, timeoutTicks = 200, batch = "qaJobItemMercPacket1104c")
	public void aSneakClickWithAJobItemRefusesAMercenaryAtHisPost(GameTestHelper helper) {
		QaHallScreenGameTests.village(helper);
		BlockPos post = new BlockPos(12, 2, 15);
		helper.setBlock(post, ModBlocks.GUARD_POST);
		helper.setBlock(CUTTER, Blocks.STONECUTTER);
		List<String> seen = new ArrayList<>();
		ServerPlayer player = QaHallScreenGameTests.payer(helper, seen, 60);
		helper.runAfterDelay(3, () -> {
			Villager merc = hireBand(helper, player).get(0);
			standByCutter(helper, merc);
			Jobs.employ(helper.getLevel(), merc, helper.absolutePos(post), ModVillagers.GUARD_POST_POI, ModVillagers.GUARD);
			int money = player.getInventory().countItem(Items.EMERALD);
			seen.clear();
			clay(player, merc);
			clay(player, merc);
			helper.assertTrue(merc.getVillagerData().getProfession() == ModVillagers.GUARD && Mercenaries.isMercenary(merc),
				"the mercenary's job changed: " + merc.getVillagerData() + "; told " + seen);
			helper.assertTrue(merc.getBrain().getMemory(MemoryModuleType.JOB_SITE).map(g -> g.pos().equals(helper.absolutePos(post))).orElse(false),
				"he left his post: " + merc.getBrain().getMemory(MemoryModuleType.JOB_SITE));
			helper.assertTrue(money == player.getInventory().countItem(Items.EMERALD), "emeralds were taken");
			helper.assertTrue(cutterFree(helper) == 1, "the free Stonecutter was taken");
			helper.assertTrue(cant(merc).equals("Mercenary's job can't be changed here."), "the line reads: " + cant(merc));
			helper.assertTrue(seen.contains(cant(merc)), "the player wasn't told why: " + seen);
			helper.assertTrue(seen.stream().noneMatch(s -> s.contains("took the Mason job") || s.contains("emeralds")), "told something else: " + seen);
			helper.succeed();
		});
	}

	/** The other side: an ordinary jobless villager sneak-clicked with clay by the same free Stonecutter becomes its Mason. */
	//$ gametest_ticks_batch ITEM_AREA '200' '"qaJobItemVillager1104c"'
	@GameTest(template = ITEM_AREA, timeoutTicks = 200, batch = "qaJobItemVillager1104c")
	public void aSneakClickWithAJobItemStillGivesAnOrdinaryVillagerTheJob(GameTestHelper helper) {
		QaHallScreenGameTests.village(helper);
		helper.setBlock(CUTTER, Blocks.STONECUTTER);
		Villager ada = QaHallScreenGameTests.villager(helper, "Ada", BY_CUTTER);
		List<String> seen = new ArrayList<>();
		ServerPlayer player = QaHallScreenGameTests.payer(helper, seen, 0);
		helper.runAfterDelay(3, () -> {
			ada.setVillagerData(ada.getVillagerData().setProfession(VillagerProfession.NONE));
			clay(player, ada);
			helper.assertTrue(ada.getVillagerData().getProfession() == VillagerProfession.MASON, "Ada isn't a Mason: " + ada.getVillagerData() + "; told " + seen);
			helper.assertTrue(cutterFree(helper) == 0, "the Stonecutter isn't hers");
			helper.assertTrue(seen.contains("Ada took the Mason job at the Stonecutter."), "not told: " + seen);
			helper.assertTrue(!seen.contains(cant(ada)), "an ordinary villager was told her job can't be changed: " + seen);
			helper.succeed();
		});
	}

	/** A relief rider (a Guard without a post) sneak-clicked with clay keeps his job and the player reads the refusal. */
	//$ gametest_ticks_batch ITEM_AREA '200' '"qaJobItemRider1104c"'
	@GameTest(template = ITEM_AREA, timeoutTicks = 200, batch = "qaJobItemRider1104c")
	public void aSneakClickWithAJobItemRefusesAReliefRider(GameTestHelper helper) {
		QaHallScreenGameTests.village(helper);
		helper.setBlock(CUTTER, Blocks.STONECUTTER);
		Villager rider = QaHallScreenGameTests.villager(helper, "Rider", BY_CUTTER);
		List<String> seen = new ArrayList<>();
		ServerPlayer player = QaHallScreenGameTests.payer(helper, seen, 60);
		helper.runAfterDelay(3, () -> {
			rider.setVillagerData(rider.getVillagerData().setProfession(ModVillagers.GUARD).setLevel(3));
			rider.setVillagerXp(70);
			BlockPos hall = helper.absolutePos(ITEM_HALL);
			ModAttachments.RELIEF_RIDER.set(rider, new Relief.Rider(hall, GlobalPos.of(helper.getLevel().dimension(), hall)));
			seen.clear();
			clay(player, rider);
			clay(player, rider);
			helper.assertTrue(rider.getVillagerData().getProfession() == ModVillagers.GUARD, "the rider's job changed: " + rider.getVillagerData() + "; told " + seen);
			helper.assertTrue(cutterFree(helper) == 1, "the free Stonecutter was taken");
			helper.assertTrue(seen.contains("Rider's job can't be changed here."), "the player wasn't told why: " + seen);
			helper.succeed();
		});
	}

	/**
	 * Elders, both sides: a retired elder handed clay is told he has retired (not that his job can't be changed here), and
	 * the Stonecutter stays free; a plain elder handed clay then takes it.
	 */
	//$ gametest_ticks_batch ITEM_AREA '200' '"qaJobItemElders1104c"'
	@GameTest(template = ITEM_AREA, timeoutTicks = 200, batch = "qaJobItemElders1104c")
	public void aSneakClickWithAJobItemTellsARetiredElderWhyAndEmploysAPlainOne(GameTestHelper helper) {
		QaHallScreenGameTests.village(helper);
		helper.setBlock(CUTTER, Blocks.STONECUTTER);
		Villager bram = QaHallScreenGameTests.villager(helper, "Bram", BY_CUTTER);
		Villager cora = QaHallScreenGameTests.villager(helper, "Cora", CUTTER.offset(1, 0, 1));
		List<String> seen = new ArrayList<>();
		ServerPlayer player = QaHallScreenGameTests.payer(helper, seen, 0);
		helper.runAfterDelay(3, () -> {
			bram.setVillagerData(bram.getVillagerData().setProfession(VillagerProfession.NONE));
			cora.setVillagerData(cora.getVillagerData().setProfession(VillagerProfession.NONE));
			retire(helper, bram);
			makeElder(helper, cora);
			clay(player, bram);
			helper.assertTrue(bram.getVillagerData().getProfession() == VillagerProfession.NONE, "the retired elder took a job: " + bram.getVillagerData());
			helper.assertTrue(cutterFree(helper) == 1, "the Stonecutter is held for a retired elder");
			helper.assertTrue(seen.contains("Bram has retired and takes no job now: they mentor the young instead."), "not told why: " + seen);
			helper.assertTrue(!seen.contains(cant(bram)), "a retired elder was told his job can't be changed here: " + seen);
			seen.clear();
			clay(player, cora);
			helper.assertTrue(cora.getVillagerData().getProfession() == VillagerProfession.MASON, "the plain elder isn't a Mason: " + cora.getVillagerData() + "; " + seen);
			helper.assertTrue(seen.contains("Cora took the Mason job at the Stonecutter."), "not told: " + seen);
			helper.succeed();
		});
	}

	/** A mercenary saved and loaded again is still hired, and a sneak-click with clay still refuses him. */
	//$ gametest_ticks_batch ITEM_AREA '200' '"qaJobItemMercReload1104c"'
	@GameTest(template = ITEM_AREA, timeoutTicks = 200, batch = "qaJobItemMercReload1104c")
	public void aReloadedMercenaryIsStillRefusedByAJobItem(GameTestHelper helper) {
		QaHallScreenGameTests.village(helper);
		helper.setBlock(CUTTER, Blocks.STONECUTTER);
		List<String> seen = new ArrayList<>();
		ServerPlayer player = QaHallScreenGameTests.payer(helper, seen, 60);
		helper.runAfterDelay(3, () -> {
			Villager merc = hireBand(helper, player).get(0);
			standByCutter(helper, merc);
			Villager back = reload(helper, merc);
			helper.assertTrue(Mercenaries.isMercenary(back) && back.getVillagerData().getProfession() == ModVillagers.GUARD,
				"the reload lost the contract or the job: " + back.getVillagerData());
			helper.runAfterDelay(2, () -> {
				seen.clear();
				clay(player, back);
				helper.assertTrue(back.getVillagerData().getProfession() == ModVillagers.GUARD, "the reloaded mercenary's job changed: " + back.getVillagerData() + "; " + seen);
				helper.assertTrue(cutterFree(helper) == 1, "the free Stonecutter was taken");
				helper.assertTrue(seen.contains("Mercenary's job can't be changed here."), "the player wasn't told why: " + seen);
				helper.succeed();
			});
		});
	}

	/**
	 * Doctor Wren at her brewing stand handed another job's item (an iron axe, the Lumberjack's) by a free Fletching
	 * Table: refused as for clay, the table still free.
	 */
	//$ gametest_ticks_batch ITEM_AREA '200' '"qaJobItemWrenAxe1104c"'
	@GameTest(template = ITEM_AREA, timeoutTicks = 200, batch = "qaJobItemWrenAxe1104c")
	public void doctorWrenRefusesAnotherJobsItemToo(GameTestHelper helper) {
		QaHallScreenGameTests.village(helper);
		BlockPos stand = new BlockPos(12, 2, 15);
		helper.setBlock(stand, Blocks.BREWING_STAND);
		helper.setBlock(CUTTER, Blocks.FLETCHING_TABLE);
		Villager wren = QaHallScreenGameTests.villager(helper, "Doctor Wren", BY_CUTTER);
		wren.addTag(Arcs.MOB_TAG);
		List<String> seen = new ArrayList<>();
		ServerPlayer player = QaHallScreenGameTests.payer(helper, seen, 60);
		helper.runAfterDelay(3, () -> {
			Jobs.employ(helper.getLevel(), wren, helper.absolutePos(stand), PoiTypes.CLERIC, VillagerProfession.CLERIC);
			wren.setVillagerData(wren.getVillagerData().setLevel(3));
			seen.clear();
			StationsSpecGameTests.rightClick(player, wren, new ItemStack(Items.IRON_AXE), true);
			helper.assertTrue(wren.getVillagerData().getProfession() == VillagerProfession.CLERIC, "Doctor Wren's job changed: " + wren.getVillagerData() + "; " + seen);
			helper.assertTrue(cutterFree(helper) == 1, "the free Fletching Table was taken");
			helper.assertTrue(seen.contains("Doctor Wren's job can't be changed here."), "the player wasn't told why: " + seen);
			helper.succeed();
		});
	}
}
