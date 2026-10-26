package io.github.jcondedata.aliveworkplace.test;

import io.github.jcondedata.aliveworkplace.build.Builders;
import io.github.jcondedata.aliveworkplace.hall.HallPeople;
import io.github.jcondedata.aliveworkplace.hall.HallRetraining;
import io.github.jcondedata.aliveworkplace.hall.HallSections;
import io.github.jcondedata.aliveworkplace.hall.HallSections.Section;
import io.github.jcondedata.aliveworkplace.hall.VillageHallScreen;
import io.github.jcondedata.aliveworkplace.hall.VillageHalls;
import io.github.jcondedata.aliveworkplace.people.Moods;
import io.github.jcondedata.aliveworkplace.registry.ModBlocks;
import io.github.jcondedata.aliveworkplace.registry.ModVillagers;
import io.github.jcondedata.aliveworkplace.work.ChoiceMenu;
import io.github.jcondedata.aliveworkplace.work.Jobs;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.locale.Language;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.village.poi.PoiType;
import net.minecraft.world.entity.ai.village.poi.PoiTypes;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.npc.VillagerProfession;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.ItemLore;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.resources.ResourceKey;

/**
 * ROADMAP 40.2: the hall's People section as a table (Name, Job, Level, Mood) under "8 · 6 working · 2 jobless". Every
 * check goes through the hall's own entry point (a right-click on the block, the sidebar's click, the rows' clicks and
 * the table's scroll as the client sends them): the rows and counts of a village of 8 and of 60 (scrolled to its last
 * villager), the details of a builder, a guard, a child and a nitwit, and a villager who dies or leaves while the
 * screen is open.
 */
public class HallPeopleGameTests {
	private static final String AREA = "aliveworkplace_test:huge_area";
	private static final BlockPos HALL = new BlockPos(15, 2, 15);

	/** The village kept to this test's area (the hall's reach shrunk while the test runs). */
	private static void village(GameTestHelper helper, int radius) {
		Leftovers.clear(helper);
		HallSections.forget();
		int was = VillageHalls.RADIUS;
		VillageHalls.RADIUS = radius;
		Leftovers.after(helper, () -> VillageHalls.RADIUS = was);
		helper.setBlock(HALL, ModBlocks.VILLAGE_HALL);
	}

	/** Moods on for this test (the test server runs with them off), read afresh. */
	private static void moodsOn(GameTestHelper helper) {
		boolean was = Moods.ENABLED;
		Moods.ENABLED = true;
		Leftovers.after(helper, () -> Moods.ENABLED = was);
		Moods.forget();
	}

	private static Villager villager(GameTestHelper helper, int x, int z) {
		Villager v = helper.spawn(EntityType.VILLAGER, new BlockPos(x, 2, z));
		v.setNoAi(true); // they stay where they're put, and keep their jobs
		return v;
	}

	/** Opens the hall as a player does (a right-click on the block) and its People section (the sidebar's click). */
	private static ChoiceMenu openPeople(GameTestHelper helper, ServerPlayer player) {
		BlockPos hall = helper.absolutePos(HALL);
		player.teleportTo(hall.getX() + 1.5, hall.getY(), hall.getZ() + 0.5);
		helper.useBlock(HALL, player);
		helper.assertTrue(player.containerMenu instanceof ChoiceMenu, "the hall didn't open: " + player.containerMenu);
		ChoiceMenu menu = (ChoiceMenu) player.containerMenu;
		helper.assertTrue(menu.clickMenuButton(player, Section.PEOPLE.ordinal()), "the sidebar didn't take People");
		helper.assertTrue(HallSections.current(menu) == Section.PEOPLE, "People isn't open: " + HallSections.current(menu));
		return menu;
	}

	/** The table's rows loaded now: slot and columns. */
	private record Row(int slot, CompoundTag columns, ItemStack icon) {
	}

	private static List<Row> rows(ChoiceMenu menu) {
		List<Row> out = new ArrayList<>();
		for (int slot = 0; slot < ChoiceMenu.SIZE; slot++) {
			CompoundTag row = HallPeople.row(menu.icon(slot));
			if (row != null) {
				out.add(new Row(slot, row, menu.icon(slot)));
			}
		}
		return out;
	}

	private static Row rowOf(GameTestHelper helper, ChoiceMenu menu, Villager villager) {
		for (Row row : rows(menu)) {
			if (row.columns().getString("uuid").equals(villager.getStringUUID())) {
				return row;
			}
		}
		helper.fail("no row for " + villager.getName().getString());
		return null;
	}

	/** Whether {@code icon}'s details (what the open row shows) have a line of {@code key}. */
	private static boolean says(ItemStack icon, String key) {
		ItemLore lore = icon.get(DataComponents.LORE);
		if (lore == null) {
			return false;
		}
		for (Component line : lore.lines()) {
			if (has(line, key)) {
				return true;
			}
		}
		return false;
	}

	private static boolean has(Component c, String key) {
		if (c.getContents() instanceof TranslatableContents t) {
			if (t.getKey().equals(key)) {
				return true;
			}
			for (Object arg : t.getArgs()) {
				if (arg instanceof Component a && has(a, key)) {
					return true;
				}
			}
		}
		for (Component sibling : c.getSiblings()) {
			if (has(sibling, key)) {
				return true;
			}
		}
		return false;
	}

	private static void employ(GameTestHelper helper, Villager v, BlockPos at, ResourceKey<PoiType> poi, VillagerProfession job) {
		Jobs.employ(helper.getLevel(), v, helper.absolutePos(at), poi, job);
	}

	/**
	 * A village of 8: six at work and two without a job. The header says "8 · 6 working · 2 jobless", there's a row for
	 * each with their job, level and a mood word, the jobless say "No job", and every sentence the table shows is in the
	 * language file.
	 */
	//$ gametest_ticks_batch AREA '200' '"hallPeopleEight"'
	@GameTest(template = AREA, timeoutTicks = 200, batch = "hallPeopleEight")
	public void villageOfEightRowsAndCounts(GameTestHelper helper) {
		village(helper, 16);
		Block[] blocks = {Blocks.STONECUTTER, Blocks.LECTERN, Blocks.COMPOSTER, Blocks.FLETCHING_TABLE, Blocks.CARTOGRAPHY_TABLE, Blocks.BREWING_STAND};
		List<ResourceKey<PoiType>> pois = List.of(PoiTypes.MASON, PoiTypes.LIBRARIAN, PoiTypes.FARMER, PoiTypes.FLETCHER, PoiTypes.CARTOGRAPHER, PoiTypes.CLERIC);
		VillagerProfession[] jobs = {VillagerProfession.MASON, VillagerProfession.LIBRARIAN, VillagerProfession.FARMER, VillagerProfession.FLETCHER,
			VillagerProfession.CARTOGRAPHER, VillagerProfession.CLERIC};
		List<Villager> workers = new ArrayList<>();
		for (int i = 0; i < 6; i++) {
			helper.setBlock(new BlockPos(8 + 2 * i, 2, 8), blocks[i]);
			workers.add(villager(helper, 8 + 2 * i, 10));
		}
		List<Villager> jobless = List.of(villager(helper, 10, 20), villager(helper, 14, 20));
		ServerPlayer player = helper.makeMockServerPlayerInLevel();
		helper.runAfterDelay(3, () -> {
			for (int i = 0; i < 6; i++) {
				employ(helper, workers.get(i), new BlockPos(8 + 2 * i, 2, 8), pois.get(i), jobs[i]);
			}
			moodsOn(helper);
			ChoiceMenu menu = openPeople(helper, player);
			helper.assertTrue(menu.state(HallSections.PEOPLE_TOTAL) == 8 && menu.state(HallSections.PEOPLE_WORKING) == 6
				&& menu.state(HallSections.PEOPLE_JOBLESS) == 2 && menu.state(HallSections.PEOPLE_CHILDREN) == 0, "counts: "
				+ menu.state(HallSections.PEOPLE_TOTAL) + " " + menu.state(HallSections.PEOPLE_WORKING) + " " + menu.state(HallSections.PEOPLE_JOBLESS));
			String header = HallPeople.header(8, 6, 2, 0).getString();
			helper.assertTrue(header.equals("8 · 6 working · 2 jobless"), "the header reads " + header);
			List<Row> rows = rows(menu);
			helper.assertTrue(rows.size() == 8, "rows: " + rows.size());
			for (int i = 0; i < 6; i++) {
				Row row = rowOf(helper, menu, workers.get(i));
				helper.assertTrue(row.columns().getString("kind").equals("worker"), "worker " + i + " is " + row.columns().getString("kind"));
				helper.assertTrue(row.columns().getString("job").equals("entity.minecraft.villager." + jobs[i].name()), "job: " + row.columns().getString("job"));
				helper.assertTrue(row.columns().getInt("level") >= 1, "a worker's level: " + row.columns().getInt("level"));
				HallPeople.MoodWord mood = HallPeople.MoodWord.valueOf(row.columns().getString("mood").toUpperCase());
				helper.assertTrue(mood != HallPeople.MoodWord.NONE, "a grown villager in a village has a mood");
				Moods.Mood m = Moods.of(workers.get(i));
				// Nobody here has a bed: unless they're happy anyway, that's the word ("No bed", in red).
				helper.assertTrue(m.score() >= Moods.HAPPY ? mood == HallPeople.MoodWord.HAPPY : mood == HallPeople.MoodWord.NO_BED,
					"no bed and a mood of " + m.score() + " shows " + mood);
			}
			for (Villager v : jobless) {
				Row row = rowOf(helper, menu, v);
				helper.assertTrue(row.columns().getString("kind").equals("jobless") && row.columns().getString("job").equals("screen.aliveworkplace.hall.people.no_job")
					&& row.columns().getInt("level") == 0 && row.columns().getString("job_action").equals("assign"), "a jobless row: " + row.columns());
			}
			// The words the table shows.
			Language lang = Language.getInstance();
			for (String key : List.of("header", "header_children", "hint", "col.name", "col.job", "col.level", "col.mood", "mood.happy", "mood.content",
				"mood.unhappy", "mood.no_bed", "child", "no_job", "job_button", "bed_button", "find_button", "job_retrain", "job_assign", "job_none", "bed_hint")) {
				helper.assertTrue(lang.has("screen.aliveworkplace.hall.people." + key), "no text for screen.aliveworkplace.hall.people." + key);
			}
			helper.assertTrue(HallPeople.MoodWord.NO_BED.word().getString().equals("No bed"), "the no-bed word: " + HallPeople.MoodWord.NO_BED.word().getString());
			helper.assertTrue(HallPeople.header(10, 6, 2, 2).getString().equals("10 · 6 working · 2 jobless · 2 children"), "with children: "
				+ HallPeople.header(10, 6, 2, 2).getString());
			helper.succeed();
		});
	}

	/**
	 * A village of 60: the table loads a window of people and the scroll (the client's menu-button click) moves it, down
	 * to the last villager; every one of the 60 has a row on the way, and a scroll past the end stops at the last.
	 */
	//$ gametest_ticks_batch AREA '200' '"hallPeopleSixty"'
	@GameTest(template = AREA, timeoutTicks = 200, batch = "hallPeopleSixty")
	public void villageOfSixtyScrolls(GameTestHelper helper) {
		village(helper, 24);
		List<Villager> all = new ArrayList<>();
		for (int i = 0; i < 60; i++) {
			int x = 3 + (i % 10) * 2 + (i / 10 % 2);
			int z = 4 + (i / 10) * 4;
			if (x == HALL.getX() && z == HALL.getZ()) {
				z++;
			}
			all.add(villager(helper, x, z));
		}
		ServerPlayer player = helper.makeMockServerPlayerInLevel();
		helper.runAfterDelay(2, () -> {
			ChoiceMenu menu = openPeople(helper, player);
			helper.assertTrue(menu.state(HallSections.PEOPLE_TOTAL) == 60 && menu.state(HallSections.PEOPLE_JOBLESS) == 60,
				"counts: " + menu.state(HallSections.PEOPLE_TOTAL) + " " + menu.state(HallSections.PEOPLE_JOBLESS));
			helper.assertTrue(menu.state(HallSections.PEOPLE_FIRST) == 0 && rows(menu).size() == VillageHallScreen.PER_PAGE, "first window: "
				+ menu.state(HallSections.PEOPLE_FIRST) + ", " + rows(menu).size() + " rows");
			Set<String> seen = new HashSet<>();
			rows(menu).forEach(r -> seen.add(r.columns().getString("uuid")));
			for (int first : new int[]{12, 30, 35}) {
				helper.assertTrue(menu.clickMenuButton(player, HallPeople.SCROLL + first), "the scroll wasn't taken");
				helper.assertTrue(menu.state(HallSections.PEOPLE_FIRST) == first && rows(menu).size() == VillageHallScreen.PER_PAGE,
					"scrolled to " + first + ": first " + menu.state(HallSections.PEOPLE_FIRST) + ", " + rows(menu).size() + " rows");
				helper.assertTrue(HallSections.current(menu) == Section.PEOPLE, "the scroll left People");
				rows(menu).forEach(r -> seen.add(r.columns().getString("uuid")));
			}
			for (Villager v : all) {
				helper.assertTrue(seen.contains(v.getStringUUID()), "a villager never had a row");
			}
			helper.assertTrue(menu.clickMenuButton(player, HallPeople.SCROLL + 500), "the scroll wasn't taken");
			helper.assertTrue(menu.state(HallSections.PEOPLE_FIRST) == 35 && rows(menu).size() == VillageHallScreen.PER_PAGE,
				"past the end: first " + menu.state(HallSections.PEOPLE_FIRST) + ", " + rows(menu).size() + " rows");
			helper.succeed();
		});
	}

	/**
	 * The details of a builder, a guard, a child and a nitwit: what each row opens to (traits, what they're doing,
	 * needs, mood and why, class), each marked as what they are; Job ▾ on the builder opens today's Change job page.
	 */
	//$ gametest_ticks_batch AREA '200' '"hallPeopleDetails"'
	@GameTest(template = AREA, timeoutTicks = 200, batch = "hallPeopleDetails")
	public void detailsOfBuilderGuardChildAndNitwit(GameTestHelper helper) {
		village(helper, 16);
		BlockPos table = new BlockPos(8, 2, 8);
		BlockPos post = new BlockPos(20, 2, 8);
		helper.setBlock(table, ModBlocks.BLUEPRINT_TABLE);
		helper.setBlock(post, ModBlocks.GUARD_POST);
		Villager builder = villager(helper, 9, 10);
		Villager guard = villager(helper, 20, 10);
		Villager child = villager(helper, 12, 20);
		child.setAge(-24000);
		Villager nitwit = villager(helper, 18, 20);
		nitwit.setVillagerData(nitwit.getVillagerData().setProfession(VillagerProfession.NITWIT));
		ServerPlayer player = helper.makeMockServerPlayerInLevel();
		helper.runAfterDelay(3, () -> {
			Builders.employ(helper.getLevel(), builder, helper.absolutePos(table));
			employ(helper, guard, post, ModVillagers.GUARD_POST_POI, ModVillagers.GUARD);
			moodsOn(helper);
			ChoiceMenu menu = openPeople(helper, player);
			helper.assertTrue(menu.state(HallSections.PEOPLE_TOTAL) == 4 && menu.state(HallSections.PEOPLE_WORKING) == 2
				&& menu.state(HallSections.PEOPLE_JOBLESS) == 1 && menu.state(HallSections.PEOPLE_CHILDREN) == 1, "counts: "
				+ menu.state(HallSections.PEOPLE_TOTAL) + " " + menu.state(HallSections.PEOPLE_WORKING) + " " + menu.state(HallSections.PEOPLE_JOBLESS)
				+ " " + menu.state(HallSections.PEOPLE_CHILDREN));

			Row b = rowOf(helper, menu, builder);
			helper.assertTrue(b.columns().getString("kind").equals("builder") && b.columns().getString("job").equals("entity.minecraft.villager." + ModVillagers.BUILDER.name())
				&& b.columns().getInt("level") >= 1, "the builder's row: " + b.columns());
			helper.assertTrue(says(b.icon(), "screen.aliveworkplace.hall.level") || says(b.icon(), "screen.aliveworkplace.hall.level_max"), "the builder's details lack a level");
			helper.assertTrue(says(b.icon(), "screen.aliveworkplace.hall.mood"), "the builder's details lack the mood and why");
			helper.assertTrue(says(b.icon(), "screen.aliveworkplace.hall.no_bed"), "the builder's details don't say they need a bed");

			Row g = rowOf(helper, menu, guard);
			helper.assertTrue(g.columns().getString("kind").equals("guard") && g.columns().getString("job").equals("entity.minecraft.villager." + ModVillagers.GUARD.name()),
				"the guard's row: " + g.columns());
			helper.assertTrue(says(g.icon(), "screen.aliveworkplace.hall.mood") && says(g.icon(), "screen.aliveworkplace.hall.no_bed"), "the guard's details");

			Row c = rowOf(helper, menu, child);
			helper.assertTrue(c.columns().getString("kind").equals("child") && c.columns().getString("job").equals("screen.aliveworkplace.hall.people.child")
				&& c.columns().getInt("level") == 0 && c.columns().getString("mood").equals("none") && c.columns().getString("job_action").isEmpty(),
				"the child's row: " + c.columns());
			helper.assertTrue(says(c.icon(), "screen.aliveworkplace.hall.click_to_find"), "the child's details lack the find line");

			Row n = rowOf(helper, menu, nitwit);
			helper.assertTrue(n.columns().getString("kind").equals("nitwit") && n.columns().getString("job").equals("entity.minecraft.villager.nitwit")
				&& n.columns().getString("job_action").isEmpty(), "the nitwit's row: " + n.columns());
			helper.assertTrue(says(n.icon(), "screen.aliveworkplace.hall.mood"), "the nitwit's details lack the mood");
			// Every one's details say where they are and what they're doing (the last lines of today's pane).
			for (Row row : List.of(b, g, c, n)) {
				ItemLore lore = row.icon().get(DataComponents.LORE);
				helper.assertTrue(lore != null && lore.lines().size() >= 3, "too few details for " + row.columns().getString("kind"));
			}

			// Job ▾ on the builder: today's Change job (a right-click on the row), when retraining is on.
			helper.assertTrue(b.columns().getString("job_action").equals(HallRetraining.canRetrain(helper.getLevel(), builder) ? "retrain" : ""),
				"the builder's Job ▾: " + b.columns().getString("job_action"));
			if (b.columns().getString("job_action").equals("retrain")) {
				menu.clicked(b.slot(), 1, ClickType.PICKUP, player);
				helper.assertTrue(rows(menu).isEmpty(), "Job ▾ didn't open the builder's page");
			}
			// The child's and the nitwit's rows only make them glow.
			ChoiceMenu again = openPeople(helper, player);
			again.clicked(rowOf(helper, again, nitwit).slot(), 0, ClickType.PICKUP, player);
			helper.assertTrue(HallSections.current(again) == Section.PEOPLE && !rows(again).isEmpty(), "the nitwit's row left the table");
			helper.succeed();
		});
	}

	/**
	 * A villager who leaves the village and one who dies while the screen is open: clicking either's old row (Job ▾,
	 * Find) does nothing to them and the table lays itself out again without them; a scroll does the same.
	 */
	//$ gametest_ticks_batch AREA '200' '"hallPeopleGone"'
	@GameTest(template = AREA, timeoutTicks = 200, batch = "hallPeopleGone")
	public void villagerWhoDiesOrLeavesWhileOpen(GameTestHelper helper) {
		village(helper, 12);
		Villager dies = villager(helper, 12, 12);
		Villager leaves = villager(helper, 18, 12);
		Villager stays = villager(helper, 15, 19);
		ServerPlayer player = helper.makeMockServerPlayerInLevel();
		helper.runAfterDelay(2, () -> {
			ChoiceMenu menu = openPeople(helper, player);
			helper.assertTrue(menu.state(HallSections.PEOPLE_TOTAL) == 3, "three live here: " + menu.state(HallSections.PEOPLE_TOTAL));
			int leftSlot = rowOf(helper, menu, leaves).slot();
			BlockPos far = helper.absolutePos(new BlockPos(29, 2, 29));
			leaves.teleportTo(far.getX() + 0.5, far.getY(), far.getZ() + 0.5); // out of the hall's reach of 12
			menu.clicked(leftSlot, 0, ClickType.PICKUP, player);
			helper.assertTrue(HallSections.current(menu) == Section.PEOPLE && menu.state(HallSections.PEOPLE_TOTAL) == 2, "after leaving: "
				+ menu.state(HallSections.PEOPLE_TOTAL) + " in " + HallSections.current(menu));
			helper.assertTrue(rows(menu).stream().noneMatch(r -> r.columns().getString("uuid").equals(leaves.getStringUUID())), "the one who left still has a row");
			helper.assertTrue(!rows(menu).isEmpty() && HallSections.slots(menu, Section.OVERVIEW).size() > 0, "not back on the table");

			int deadSlot = rowOf(helper, menu, dies).slot();
			dies.kill();
			menu.clicked(deadSlot, 1, ClickType.PICKUP, player); // Job ▾ on the dead villager's row
			helper.assertTrue(menu.state(HallSections.PEOPLE_TOTAL) == 1 && rows(menu).size() == 1
				&& rows(menu).get(0).columns().getString("uuid").equals(stays.getStringUUID()), "after a death: " + menu.state(HallSections.PEOPLE_TOTAL));
			helper.assertTrue(menu.clickMenuButton(player, HallPeople.SCROLL), "the scroll wasn't taken");
			helper.assertTrue(menu.state(HallSections.PEOPLE_TOTAL) == 1, "a scroll brought someone back");
			leaves.discard();
			helper.succeed();
		});
	}

	/**
	 * B547: a villager's life story is still reached from the People table. Its Story button sends the hall's own
	 * shift-click on their row (a QUICK_MOVE click), as the old list's shift-click did: a named worker's and a named
	 * jobless villager's life story opens (its Family button there); the row carries the hint the client shows Story by,
	 * and only for a named villager; the Story words are in the language file; one child reads "1 child".
	 */
	//$ gametest_ticks_batch AREA '200' '"hallPeopleStory"'
	@GameTest(template = AREA, timeoutTicks = 200, batch = "hallPeopleStory")
	public void theStoryButtonOpensTheirLifeStory(GameTestHelper helper) {
		village(helper, 16);
		helper.setBlock(new BlockPos(8, 2, 8), Blocks.LECTERN);
		Villager dara = villager(helper, 8, 10);
		dara.setCustomName(Component.literal("Dara"));
		Villager odo = villager(helper, 12, 20);
		odo.setCustomName(Component.literal("Odo"));
		Villager plain = villager(helper, 16, 20);
		ServerPlayer player = helper.makeMockServerPlayerInLevel();
		helper.runAfterDelay(3, () -> {
			employ(helper, dara, new BlockPos(8, 2, 8), PoiTypes.LIBRARIAN, VillagerProfession.LIBRARIAN);
			for (Villager who : List.of(dara, odo)) {
				ChoiceMenu menu = openPeople(helper, player);
				Row row = rowOf(helper, menu, who);
				helper.assertTrue(storyHint(row.icon()), who.getName().getString() + "'s row has no story hint to show Story by");
				menu.clicked(row.slot(), 0, ClickType.QUICK_MOVE, player); // Story: the client's shift-click on the row
				helper.assertTrue(menu.icon(io.github.jcondedata.aliveworkplace.hall.FamilyPage.BUTTON).getHoverName().getString().equals("Family"),
					who.getName().getString() + "'s life story didn't open: " + menu.icon(io.github.jcondedata.aliveworkplace.hall.FamilyPage.BUTTON));
				player.closeContainer();
			}
			ChoiceMenu menu = openPeople(helper, player);
			helper.assertFalse(storyHint(rowOf(helper, menu, plain).icon()), "an unnamed villager's row offers a story");
			Language lang = Language.getInstance();
			for (String key : List.of("story_button", "story_hint", "header_child")) {
				helper.assertTrue(lang.has("screen.aliveworkplace.hall.people." + key), "no text for screen.aliveworkplace.hall.people." + key);
			}
			helper.assertTrue(HallPeople.header(5, 3, 1, 1).getString().equals("5 · 3 working · 1 jobless · 1 child"), "one child: "
				+ HallPeople.header(5, 3, 1, 1).getString());
			helper.assertTrue(HallPeople.header(6, 3, 1, 2).getString().equals("6 · 3 working · 1 jobless · 2 children"), "two children: "
				+ HallPeople.header(6, 3, 1, 2).getString());
			helper.succeed();
		});
	}

	private static boolean storyHint(ItemStack icon) {
		ItemLore lore = icon.get(DataComponents.LORE);
		return lore != null && lore.lines().stream().anyMatch(l -> l.getContents() instanceof TranslatableContents t
			&& t.getKey().equals("screen.aliveworkplace.hall.life_story_hint"));
	}
}
