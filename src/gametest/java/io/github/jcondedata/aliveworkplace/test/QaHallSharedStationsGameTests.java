package io.github.jcondedata.aliveworkplace.test;

import static io.github.jcondedata.aliveworkplace.test.StationsSpecGameTests.listeningPlayer;

import io.github.jcondedata.aliveworkplace.hall.FreeJobsPage;
import io.github.jcondedata.aliveworkplace.hall.HallJobs;
import io.github.jcondedata.aliveworkplace.hall.HallPages;
import io.github.jcondedata.aliveworkplace.hall.HallPeople;
import io.github.jcondedata.aliveworkplace.hall.HallSections;
import io.github.jcondedata.aliveworkplace.hall.HallSections.Section;
import io.github.jcondedata.aliveworkplace.hall.VillageHallScreen;
import io.github.jcondedata.aliveworkplace.hall.VillageHalls;
import io.github.jcondedata.aliveworkplace.registry.ModBlocks;
import io.github.jcondedata.aliveworkplace.registry.ModVillagers;
import io.github.jcondedata.aliveworkplace.work.ChoiceMenu;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.npc.VillagerProfession;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.ItemLore;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;

/**
 * QA for B635 (the hall lists blocks a villager never takes by themselves: a crafting table for the Carpenter, a jukebox
 * for the Bard, a Mailbox for the Postman), written from the spec: 40.3 (Job ▾ shows every job, those with a free work
 * block with how many, "Mason · 1 free Stonecutter", those without grayed "place a Lectern first"), 36.9 (the Free job
 * blocks page) and B635's own text. Every check goes through the hall's own clicks: the block right-clicked, People,
 * Job ▾ and a job, as the client sends them.
 */
public class QaHallSharedStationsGameTests {
	static final String AREA = "aliveworkplace_test:huge_area";
	static final BlockPos HALL = new BlockPos(15, 2, 24);
	static final BlockPos BLOCK = new BlockPos(15, 2, 19);
	static final BlockPos PIP = new BlockPos(12, 2, 22);

	static void village(GameTestHelper helper, int radius) {
		Leftovers.clear(helper);
		HallSections.forget();
		HallJobs.forget();
		int was = VillageHalls.RADIUS;
		VillageHalls.RADIUS = radius;
		Leftovers.after(helper, () -> VillageHalls.RADIUS = was);
		helper.setBlock(HALL, ModBlocks.VILLAGE_HALL);
	}

	static Villager jobless(GameTestHelper helper, BlockPos at, String name) {
		Villager v = helper.spawn(EntityType.VILLAGER, at);
		v.setNoAi(true);
		v.setVillagerData(v.getVillagerData().setProfession(VillagerProfession.NONE));
		v.setCustomName(Component.literal(name));
		return v;
	}

	/** The hall opened as a player does (a right-click on the block), on its People section. */
	static ChoiceMenu openPeople(GameTestHelper helper, ServerPlayer player) {
		BlockPos hall = helper.absolutePos(HALL);
		player.teleportTo(hall.getX() + 1.5, hall.getY(), hall.getZ() + 0.5);
		helper.useBlock(HALL, player);
		helper.assertTrue(player.containerMenu instanceof ChoiceMenu, "the hall didn't open: " + player.containerMenu);
		ChoiceMenu menu = (ChoiceMenu) player.containerMenu;
		helper.assertTrue(menu.clickMenuButton(player, Section.PEOPLE.ordinal()), "the sidebar didn't take People");
		return menu;
	}

	static int slotOf(GameTestHelper helper, ChoiceMenu menu, Villager villager) {
		for (int slot = 0; slot < ChoiceMenu.SIZE; slot++) {
			CompoundTag row = HallPeople.row(menu.icon(slot));
			if (row != null && row.getString("uuid").equals(villager.getStringUUID())) {
				return slot;
			}
		}
		helper.fail("no People row for " + villager.getName().getString());
		return -1;
	}

	/** Job ▾ clicked on {@code villager}'s row: the list's lines. */
	static List<ItemStack> jobDropdown(GameTestHelper helper, ServerPlayer player, ChoiceMenu menu, Villager villager) {
		helper.assertTrue(menu.clickMenuButton(player, HallJobs.OPEN + slotOf(helper, menu, villager)), "Job ▾ wasn't taken");
		List<ItemStack> lines = HallJobs.lines(menu.icon(slotOf(helper, menu, villager)));
		helper.assertTrue(!lines.isEmpty(), "Job ▾ opened no list for " + villager.getName().getString());
		return lines;
	}

	static int id(VillagerProfession job) {
		return BuiltInRegistries.VILLAGER_PROFESSION.getId(job);
	}

	static ItemStack lineOf(GameTestHelper helper, List<ItemStack> lines, VillagerProfession job) {
		for (ItemStack line : lines) {
			CompoundTag mark = HallJobs.mark(line);
			if (mark != null && mark.getInt("id") == id(job) && !mark.getString("state").equals("header")) {
				return line;
			}
		}
		helper.fail("no " + job + " line in " + names(lines));
		return ItemStack.EMPTY;
	}

	static List<String> names(List<ItemStack> lines) {
		return lines.stream().map(l -> l.getHoverName().getString()).toList();
	}

	static String state(ItemStack line) {
		return HallJobs.mark(line).getString("state");
	}

	static void pick(GameTestHelper helper, ServerPlayer player, ChoiceMenu menu, VillagerProfession job) {
		helper.assertTrue(menu.clickMenuButton(player, HallJobs.PICK + id(job)), "the pick wasn't taken");
	}

	static Optional<BlockPos> site(Villager villager) {
		return villager.getBrain().getMemory(MemoryModuleType.JOB_SITE).map(GlobalPos::pos);
	}

	static List<String> lore(ItemStack stack) {
		return stack.getOrDefault(DataComponents.LORE, ItemLore.EMPTY).lines().stream().map(Component::getString).toList();
	}

	/** The Free job blocks tab's lines, as the hall's main page shows them. */
	static List<String> freeTab(GameTestHelper helper, ServerPlayer player) {
		ChoiceMenu menu = VillageHallScreen.forTest(player, helper.absolutePos(HALL));
		int tab = HallPages.slot(FreeJobsPage.PAGE);
		helper.assertTrue(tab >= VillageHallScreen.PAGE_ROW, "the Free job blocks page has no tab: " + tab);
		return lore(menu.icon(tab));
	}

	/** Asserts Job ▾ offers {@code job} at {@code n} free blocks with exactly {@code text}. */
	static void assertFreeLine(GameTestHelper helper, List<ItemStack> lines, VillagerProfession job, String text) {
		ItemStack line = lineOf(helper, lines, job);
		helper.assertTrue(line.getHoverName().getString().equals(text) && state(line).equals("free"),
			"wanted \"" + text + "\" (free), got \"" + line.getHoverName().getString() + "\" (" + state(line) + ")");
	}

	/** A jobless villager is given the job of the lone free {@code block} with one click on Job ▾'s line. */
	static void oneClickJob(GameTestHelper helper, Block block, VillagerProfession job, String line, String told) {
		village(helper, 8);
		helper.setBlock(BLOCK, block);
		Villager pip = jobless(helper, PIP, "Pip");
		List<String> seen = new ArrayList<>();
		ServerPlayer player = listeningPlayer(helper, seen);
		helper.runAfterDelay(3, () -> {
			BlockPos at = helper.absolutePos(BLOCK);
			helper.assertTrue(freeTab(helper, player).contains("1 free job block"), "the tab: " + freeTab(helper, player));
			ChoiceMenu menu = openPeople(helper, player);
			assertFreeLine(helper, jobDropdown(helper, player, menu, pip), job, line);
			pick(helper, player, menu, job);
			helper.assertTrue(pip.getVillagerData().getProfession() == job && site(pip).equals(Optional.of(at)),
				"Pip isn't the " + job + " at the block: " + pip.getVillagerData().getProfession() + " " + site(pip));
			helper.assertTrue(seen.contains(told), "not told \"" + told + "\": " + seen);
			helper.assertTrue(helper.getLevel().getPoiManager().getFreeTickets(at) == 0, "the block is still free");
			helper.assertTrue(freeTab(helper, player).contains("0 free job blocks"), "the tab after: " + freeTab(helper, player));
			helper.succeed();
		});
	}

	/**
	 * The crafting table is the Carpenter's alone (21.1a's list): with one free table and nothing else, Job ▾ shows
	 * "Carpenter · 1 free Crafting Table" and no other job as free; the Free job blocks page lists it once, as the
	 * Carpenter's (not as a block several jobs share).
	 */
	//$ gametest_ticks_batch AREA '200' '"qaSharedOnlyCarpenter"'
	@GameTest(template = AREA, timeoutTicks = 200, batch = "qaSharedOnlyCarpenter")
	public void onlyTheCarpenterIsOfferedACraftingTable(GameTestHelper helper) {
		village(helper, 8);
		helper.setBlock(BLOCK, Blocks.CRAFTING_TABLE);
		Villager pip = jobless(helper, PIP, "Pip");
		ServerPlayer player = listeningPlayer(helper, new ArrayList<>());
		helper.runAfterDelay(3, () -> {
			ChoiceMenu menu = openPeople(helper, player);
			List<ItemStack> lines = jobDropdown(helper, player, menu, pip);
			assertFreeLine(helper, lines, ModVillagers.CARPENTER, "Carpenter · 1 free Crafting Table");
			List<String> others = new ArrayList<>();
			for (ItemStack line : lines) {
				CompoundTag mark = HallJobs.mark(line);
				if (mark != null && mark.getInt("id") != id(ModVillagers.CARPENTER) && mark.getString("state").equals("free")) {
					others.add(line.getHoverName().getString());
				}
			}
			helper.assertTrue(others.isEmpty(), "other jobs offered as free with only a crafting table: " + others);
			ChoiceMenu page = VillageHallScreen.forTest(player, helper.absolutePos(HALL));
			page.press(HallPages.slot(FreeJobsPage.PAGE), player);
			ItemStack row = page.icon(VillageHallScreen.FIRST_ROW);
			helper.assertTrue(row.is(Blocks.CRAFTING_TABLE.asItem()) && row.getHoverName().getString().equals("Carpenter"),
				"the page's row: " + row + " \"" + row.getHoverName().getString() + "\"");
			helper.assertTrue(page.icon(VillageHallScreen.FIRST_ROW + 1).isEmpty(), "a second row: " + page.icon(VillageHallScreen.FIRST_ROW + 1));
			helper.succeed();
		});
	}

	/** A jukebox is the Bard's: "Bard · 1 free Jukebox", one click makes a jobless villager the Bard there. */
	//$ gametest_ticks_batch AREA '200' '"qaSharedJukebox"'
	@GameTest(template = AREA, timeoutTicks = 200, batch = "qaSharedJukebox")
	public void aJukeboxIsAFreeBardBlock(GameTestHelper helper) {
		oneClickJob(helper, Blocks.JUKEBOX, ModVillagers.BARD, "Bard · 1 free Jukebox", "Pip is now a Bard");
	}

	/** A Mailbox is the Postman's: "Postman · 1 free Mailbox", one click makes a jobless villager the Postman there. */
	//$ gametest_ticks_batch AREA '200' '"qaSharedMailbox"'
	@GameTest(template = AREA, timeoutTicks = 200, batch = "qaSharedMailbox")
	public void aMailboxIsAFreePostmanBlock(GameTestHelper helper) {
		oneClickJob(helper, ModBlocks.MAILBOX, ModVillagers.POSTMAN, "Postman · 1 free Mailbox", "Pip is now a Postman");
	}

	/** Two free crafting tables: "Carpenter · 2 free Crafting Tables", and the tab counts 2. */
	//$ gametest_ticks_batch AREA '200' '"qaSharedTwoTables"'
	@GameTest(template = AREA, timeoutTicks = 200, batch = "qaSharedTwoTables")
	public void twoFreeCraftingTablesArePlural(GameTestHelper helper) {
		village(helper, 8);
		helper.setBlock(new BlockPos(13, 2, 19), Blocks.CRAFTING_TABLE);
		helper.setBlock(new BlockPos(17, 2, 19), Blocks.CRAFTING_TABLE);
		Villager pip = jobless(helper, PIP, "Pip");
		ServerPlayer player = listeningPlayer(helper, new ArrayList<>());
		helper.runAfterDelay(3, () -> {
			helper.assertTrue(freeTab(helper, player).contains("2 free job blocks"), "the tab: " + freeTab(helper, player));
			ChoiceMenu menu = openPeople(helper, player);
			assertFreeLine(helper, jobDropdown(helper, player, menu, pip), ModVillagers.CARPENTER, "Carpenter · 2 free Crafting Tables");
			helper.succeed();
		});
	}

	/**
	 * Taken, then given up: the new Carpenter's table leaves the count (0 free job blocks); "No job" from Job ▾ (two
	 * clicks) leaves him jobless, the table is free again (1 free job block) and Job ▾ offers it once more.
	 */
	//$ gametest_ticks_batch AREA '200' '"qaSharedNoJob"'
	@GameTest(template = AREA, timeoutTicks = 200, batch = "qaSharedNoJob")
	public void aTableGivenUpIsFreeAgain(GameTestHelper helper) {
		village(helper, 8);
		helper.setBlock(BLOCK, Blocks.CRAFTING_TABLE);
		Villager pip = jobless(helper, PIP, "Pip");
		List<String> seen = new ArrayList<>();
		ServerPlayer player = listeningPlayer(helper, seen);
		helper.runAfterDelay(3, () -> {
			ServerLevel level = helper.getLevel();
			BlockPos table = helper.absolutePos(BLOCK);
			ChoiceMenu menu = openPeople(helper, player);
			jobDropdown(helper, player, menu, pip);
			pick(helper, player, menu, ModVillagers.CARPENTER);
			helper.assertTrue(pip.getVillagerData().getProfession() == ModVillagers.CARPENTER && site(pip).equals(Optional.of(table)),
				"Pip isn't the Carpenter: " + pip.getVillagerData().getProfession() + " " + site(pip));
			helper.assertTrue(freeTab(helper, player).contains("0 free job blocks"), "taken, the tab: " + freeTab(helper, player));
			List<ItemStack> lines = jobDropdown(helper, player, menu, pip);
			ItemStack now = lineOf(helper, lines, ModVillagers.CARPENTER);
			helper.assertTrue(now.getHoverName().getString().equals("Carpenter · their job now"), "his own line: " + now.getHoverName().getString());
			ItemStack none = lineOf(helper, lines, VillagerProfession.NONE);
			helper.assertTrue(none.getHoverName().getString().equals("No job"), "the No job line: " + names(lines));
			seen.clear();
			pick(helper, player, menu, VillagerProfession.NONE);
			helper.assertTrue(pip.getVillagerData().getProfession() == ModVillagers.CARPENTER, "the first click already took the job");
			helper.assertTrue(seen.stream().anyMatch(s -> s.startsWith("Pip (Carpenter, ") && s.endsWith("can give up their job for nothing. Their trades and level are lost, and their work block is left free.")),
				"the offer: " + seen);
			pick(helper, player, menu, VillagerProfession.NONE);
			helper.assertTrue(pip.getVillagerData().getProfession() == VillagerProfession.NONE && site(pip).isEmpty(),
				"Pip still works: " + pip.getVillagerData().getProfession() + " " + site(pip));
			helper.assertTrue(seen.contains("Pip gave up the Carpenter job and has no job now."), "not told: " + seen);
			helper.assertTrue(level.getPoiManager().getFreeTickets(table) == 1, "the table isn't free: " + level.getPoiManager().getFreeTickets(table));
			helper.assertTrue(freeTab(helper, player).contains("1 free job block"), "given up, the tab: " + freeTab(helper, player));
			ChoiceMenu again = openPeople(helper, player);
			assertFreeLine(helper, jobDropdown(helper, player, again, pip), ModVillagers.CARPENTER, "Carpenter · 1 free Crafting Table");
			helper.succeed();
		});
	}

	/**
	 * The village's edge, from both sides: a table exactly the hall's radius away is offered; one a block further is
	 * not ("place a Crafting Table first", and the click changes nothing).
	 */
	//$ gametest_ticks_batch AREA '200' '"qaSharedOutside"'
	@GameTest(template = AREA, timeoutTicks = 200, batch = "qaSharedOutside")
	public void aTableOutsideTheVillageIsNotOffered(GameTestHelper helper) {
		village(helper, 8);
		BlockPos outside = new BlockPos(15, 2, 15); // 9 blocks from the hall
		helper.setBlock(outside, Blocks.CRAFTING_TABLE);
		Villager pip = jobless(helper, PIP, "Pip");
		List<String> seen = new ArrayList<>();
		ServerPlayer player = listeningPlayer(helper, seen);
		helper.runAfterDelay(3, () -> {
			helper.assertTrue(freeTab(helper, player).contains("0 free job blocks"), "outside, the tab: " + freeTab(helper, player));
			ChoiceMenu menu = openPeople(helper, player);
			ItemStack line = lineOf(helper, jobDropdown(helper, player, menu, pip), ModVillagers.CARPENTER);
			helper.assertTrue(line.getHoverName().getString().equals("Carpenter · place a Crafting Table first") && state(line).equals("no_block"),
				"outside, the line: " + line.getHoverName().getString() + " (" + state(line) + ")");
			helper.assertTrue(lore(line).contains("No Crafting Table in the village is free: place one, and this job can be picked."), "its hint: " + lore(line));
			pick(helper, player, menu, ModVillagers.CARPENTER);
			helper.assertTrue(pip.getVillagerData().getProfession() == VillagerProfession.NONE, "Pip was given the far table");
			helper.assertTrue(seen.contains("The Carpenter job needs a free Crafting Table: place one in the village first."), "not told: " + seen);
			helper.setBlock(outside, Blocks.AIR);
			helper.setBlock(new BlockPos(15, 2, 16), Blocks.CRAFTING_TABLE); // 8 blocks: inside
			helper.runAfterDelay(2, () -> {
				helper.assertTrue(freeTab(helper, player).contains("1 free job block"), "at the edge, the tab: " + freeTab(helper, player));
				ChoiceMenu edge = openPeople(helper, player);
				assertFreeLine(helper, jobDropdown(helper, player, edge, pip), ModVillagers.CARPENTER, "Carpenter · 1 free Crafting Table");
				helper.succeed();
			});
		});
	}

	/** Saved and loaded (the jobless villager and the hall), the free table is still offered and still gives the job. */
	//$ gametest_ticks_batch AREA '200' '"qaSharedReload"'
	@GameTest(template = AREA, timeoutTicks = 200, batch = "qaSharedReload")
	public void aFreeTableIsStillOfferedAfterAReload(GameTestHelper helper) {
		village(helper, 8);
		helper.setBlock(BLOCK, Blocks.CRAFTING_TABLE);
		Villager pip = jobless(helper, PIP, "Pip");
		List<String> seen = new ArrayList<>();
		ServerPlayer player = listeningPlayer(helper, seen);
		helper.runAfterDelay(3, () -> {
			ServerLevel level = helper.getLevel();
			CompoundTag saved = pip.saveWithoutId(new CompoundTag());
			pip.discard();
			Villager copy = EntityType.VILLAGER.create(level);
			copy.load(saved);
			copy.setUUID(java.util.UUID.randomUUID());
			level.addFreshEntity(copy);
			helper.runAfterDelay(2, () -> {
				helper.assertTrue(freeTab(helper, player).contains("1 free job block"), "reloaded, the tab: " + freeTab(helper, player));
				ChoiceMenu menu = openPeople(helper, player);
				assertFreeLine(helper, jobDropdown(helper, player, menu, copy), ModVillagers.CARPENTER, "Carpenter · 1 free Crafting Table");
				pick(helper, player, menu, ModVillagers.CARPENTER);
				helper.assertTrue(copy.getVillagerData().getProfession() == ModVillagers.CARPENTER && site(copy).equals(Optional.of(helper.absolutePos(BLOCK))),
					"reloaded Pip isn't the Carpenter: " + copy.getVillagerData().getProfession());
				helper.assertTrue(seen.contains("Pip is now a Carpenter"), "not told: " + seen);
				helper.succeed();
			});
		});
	}
}
