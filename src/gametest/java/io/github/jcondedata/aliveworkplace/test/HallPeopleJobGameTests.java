package io.github.jcondedata.aliveworkplace.test;

import static io.github.jcondedata.aliveworkplace.test.StationsSpecGameTests.listeningPlayer;

import io.github.jcondedata.aliveworkplace.blueprint.BlueprintData;
import io.github.jcondedata.aliveworkplace.blueprint.BlueprintItem;
import io.github.jcondedata.aliveworkplace.build.BuildSite;
import io.github.jcondedata.aliveworkplace.build.BuildSiteManager;
import io.github.jcondedata.aliveworkplace.build.Builders;
import io.github.jcondedata.aliveworkplace.hall.HallJobs;
import io.github.jcondedata.aliveworkplace.hall.HallPeople;
import io.github.jcondedata.aliveworkplace.hall.HallSections;
import io.github.jcondedata.aliveworkplace.hall.HallSections.Section;
import io.github.jcondedata.aliveworkplace.hall.VillageHalls;
import io.github.jcondedata.aliveworkplace.registry.ModAttachments;
import io.github.jcondedata.aliveworkplace.registry.ModBlocks;
import io.github.jcondedata.aliveworkplace.registry.ModGameRules;
import io.github.jcondedata.aliveworkplace.registry.ModItems;
import io.github.jcondedata.aliveworkplace.registry.ModVillagers;
import io.github.jcondedata.aliveworkplace.work.ChoiceMenu;
import io.github.jcondedata.aliveworkplace.work.Jobs;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.locale.Language;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.village.poi.PoiTypes;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.npc.VillagerProfession;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.ItemLore;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;

/**
 * ROADMAP 40.3: Job ▾ in the hall's People table. Every check goes through the player's own entry points: a right-click
 * on the hall, the sidebar's People, then the clicks the table sends (Job ▾ on a row, a job on its list, once to see the
 * price and again to pay). A change that works, a job with no free block, not enough emeralds, to and from the Builder (a
 * busy builder hands his build back), No job, a jobless villager given a block for nothing, children and nitwits, the
 * rule off, and a save and reload.
 */
public class HallPeopleJobGameTests {
	private static final String AREA = "aliveworkplace_test:huge_area";
	private static final BlockPos HALL = new BlockPos(15, 2, 15);
	private static final BlockPos LECTERN = new BlockPos(6, 2, 6);
	private static final BlockPos STONECUTTER = new BlockPos(22, 2, 22);
	private static final ResourceLocation TEST_HUT = ResourceLocation.fromNamespaceAndPath("aliveworkplace_test", "test_hut");

	private static void village(GameTestHelper helper) {
		Leftovers.clear(helper);
		HallSections.forget();
		HallJobs.forget();
		int was = VillageHalls.RADIUS;
		VillageHalls.RADIUS = 14;
		Leftovers.after(helper, () -> VillageHalls.RADIUS = was);
		helper.setBlock(HALL, ModBlocks.VILLAGE_HALL);
	}

	/** Dara, a librarian at the lectern, at {@code level} (Novice 1 to Master 5), with trades. */
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

	private static ServerPlayer payer(GameTestHelper helper, List<String> seen, int emeralds) {
		ServerPlayer player = listeningPlayer(helper, seen);
		if (emeralds > 0) {
			player.getInventory().setItem(9, new ItemStack(Items.EMERALD, emeralds));
		}
		return player;
	}

	/** Opens the hall as a player does (a right-click on the block) and its People section (the sidebar's click). */
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

	/** Job ▾ on {@code villager}'s row, as the client sends it; returns the list's lines now on their row. */
	private static List<ItemStack> jobDropdown(GameTestHelper helper, ServerPlayer player, ChoiceMenu menu, Villager villager) {
		helper.assertTrue(menu.clickMenuButton(player, HallJobs.OPEN + slotOf(helper, menu, villager)), "Job ▾ wasn't taken");
		return lines(helper, menu, villager);
	}

	private static List<ItemStack> lines(GameTestHelper helper, ChoiceMenu menu, Villager villager) {
		return HallJobs.lines(menu.icon(slotOf(helper, menu, villager)));
	}

	private static int id(VillagerProfession job) {
		return BuiltInRegistries.VILLAGER_PROFESSION.getId(job);
	}

	/** The list's line for {@code job}. */
	private static ItemStack lineFor(GameTestHelper helper, List<ItemStack> lines, VillagerProfession job) {
		for (ItemStack line : lines) {
			CompoundTag mark = HallJobs.mark(line);
			if (mark != null && mark.getInt("id") == id(job) && !mark.getString("state").equals("header")) {
				return line;
			}
		}
		helper.fail("no line for " + job + " in " + lines.stream().map(l -> l.getHoverName().getString()).toList());
		return ItemStack.EMPTY;
	}

	private static String state(ItemStack line) {
		return HallJobs.mark(line).getString("state");
	}

	private static List<String> lore(ItemStack stack) {
		return stack.getOrDefault(DataComponents.LORE, ItemLore.EMPTY).lines().stream().map(Component::getString).toList();
	}

	/** Clicks {@code job} on the open list, as the client sends it. */
	private static void pick(GameTestHelper helper, ServerPlayer player, ChoiceMenu menu, VillagerProfession job) {
		helper.assertTrue(menu.clickMenuButton(player, HallJobs.PICK + id(job)), "the pick wasn't taken");
	}

	private static Optional<BlockPos> site(Villager villager) {
		return villager.getBrain().getMemory(MemoryModuleType.JOB_SITE).map(GlobalPos::pos);
	}

	/**
	 * The change that works: an Apprentice librarian made a Mason. The list says "Mason · 1 free Stonecutter" and the
	 * price by her level on top; the first click shows the offer and changes nothing, the second pays 10 emeralds: a
	 * Novice Mason at the stonecutter, her lectern left free, the list closed and her row saying Mason.
	 */
	//$ gametest_ticks_batch AREA '200' '"hallJobWorks"'
	@GameTest(template = AREA, timeoutTicks = 200, batch = "hallJobWorks")
	public void aChangeThatWorks(GameTestHelper helper) {
		village(helper);
		helper.setBlock(STONECUTTER, Blocks.STONECUTTER);
		Villager dara = librarian(helper, 2);
		List<String> seen = new ArrayList<>();
		ServerPlayer player = payer(helper, seen, 15);
		helper.runAfterDelay(3, () -> {
			ChoiceMenu menu = openPeople(helper, player);
			CompoundTag before = row(helper, menu, dara);
			helper.assertTrue(before.getString("job_action").equals("retrain") && !before.getBoolean("job_hidden") && !before.getBoolean("jobs_open"),
				"Dara's row before Job ▾: " + before);
			List<ItemStack> lines = jobDropdown(helper, player, menu, dara);
			helper.assertTrue(row(helper, menu, dara).getBoolean("jobs_open") && !lines.isEmpty(), "Job ▾ didn't open the list");
			ItemStack header = lines.get(0);
			helper.assertTrue(state(header).equals("header") && header.getHoverName().getString().equals("A new job for Dara"), "the header: " + header.getHoverName().getString());
			helper.assertTrue(lore(header).contains("Retraining costs 10 emeralds (Apprentice)")
				&& lore(header).contains("Their trades and level are lost: they start again as a Novice"), "the price isn't shown first: " + lore(header));
			ItemStack mason = lineFor(helper, lines, VillagerProfession.MASON);
			helper.assertTrue(mason.getHoverName().getString().equals("Mason · 1 free Stonecutter") && state(mason).equals("free"),
				"the Mason line: " + mason.getHoverName().getString() + " " + state(mason));
			helper.assertTrue(lineFor(helper, lines, VillagerProfession.LIBRARIAN).getHoverName().getString().equals("Librarian · their job now"),
				"her own job: " + lineFor(helper, lines, VillagerProfession.LIBRARIAN).getHoverName().getString());
			// Every job a villager takes at a block is listed; the free ones come first, No job last.
			helper.assertTrue(lines.get(1) == mason || state(lines.get(1)).equals("free"), "a free job isn't first: " + lines.get(1).getHoverName().getString());
			helper.assertTrue(lines.size() > 15, "too few jobs listed: " + lines.size());
			ItemStack last = lines.get(lines.size() - 1);
			helper.assertTrue(last.getHoverName().getString().equals("No job") && state(last).equals("no_job"), "No job isn't last: " + last.getHoverName().getString());

			pick(helper, player, menu, VillagerProfession.MASON);
			helper.assertTrue(dara.getVillagerData().getProfession() == VillagerProfession.LIBRARIAN && player.getInventory().countItem(Items.EMERALD) == 15,
				"the first click changed something");
			helper.assertTrue(seen.contains("Dara (Librarian, Apprentice) can be retrained as a Mason for 10 emeralds. Their trades and level are lost: they start again as a Novice."),
				"no offer: " + seen);
			ItemStack picked = lineFor(helper, lines(helper, menu, dara), VillagerProfession.MASON);
			helper.assertTrue(state(picked).equals("picked") && lore(picked).contains("Click again to pay 10 emeralds: Dara becomes a Mason"),
				"the picked line: " + state(picked) + " " + lore(picked));

			pick(helper, player, menu, VillagerProfession.MASON);
			helper.assertTrue(dara.getVillagerData().getProfession() == VillagerProfession.MASON && dara.getVillagerData().getLevel() == 1,
				"now " + dara.getVillagerData());
			helper.assertTrue(site(dara).equals(Optional.of(helper.absolutePos(STONECUTTER))), "working at " + site(dara));
			helper.assertTrue(player.getInventory().countItem(Items.EMERALD) == 5, "paid " + (15 - player.getInventory().countItem(Items.EMERALD)));
			helper.assertTrue(helper.getLevel().getPoiManager().getFreeTickets(helper.absolutePos(LECTERN)) == 1, "the lectern isn't free");
			helper.assertTrue(seen.contains("Dara gave up the Librarian job and was retrained as a Mason at the Stonecutter, for 10 emeralds."), "not told: " + seen);
			CompoundTag after = row(helper, menu, dara);
			helper.assertTrue(!after.getBoolean("jobs_open") && after.getString("job").equals("entity.minecraft.villager.mason")
				&& HallSections.current(menu) == Section.PEOPLE, "the row after: " + after);
			helper.succeed();
		});
	}

	/**
	 * A job with no free block: grayed, "Librarian · place a Lectern first"; two clicks on it change nothing and say a
	 * Lectern is needed. With no free block at all, only the jobs' grayed lines and No job are there.
	 */
	//$ gametest_ticks_batch AREA '200' '"hallJobNoBlock"'
	@GameTest(template = AREA, timeoutTicks = 200, batch = "hallJobNoBlock")
	public void aJobWithNoFreeBlock(GameTestHelper helper) {
		village(helper);
		Villager dara = librarian(helper, 1);
		List<String> seen = new ArrayList<>();
		ServerPlayer player = payer(helper, seen, 64);
		helper.runAfterDelay(3, () -> {
			ChoiceMenu menu = openPeople(helper, player);
			List<ItemStack> lines = jobDropdown(helper, player, menu, dara);
			ItemStack mason = lineFor(helper, lines, VillagerProfession.MASON);
			helper.assertTrue(mason.getHoverName().getString().equals("Mason · place a Stonecutter first") && state(mason).equals("no_block"),
				"the Mason line: " + mason.getHoverName().getString() + " " + state(mason));
			ItemStack armorer = lineFor(helper, lines, VillagerProfession.ARMORER);
			helper.assertTrue(armorer.getHoverName().getString().equals("Armorer · place a Blast Furnace first"), "the Armorer line: " + armorer.getHoverName().getString());
			helper.assertTrue(lineFor(helper, lines, ModVillagers.BUILDER).getHoverName().getString().equals("Builder · place a Blueprint Table first"),
				"the Builder line: " + lineFor(helper, lines, ModVillagers.BUILDER).getHoverName().getString());
			helper.assertTrue(lines.stream().noneMatch(l -> state(l).equals("free")), "a job is free with no free block");
			pick(helper, player, menu, VillagerProfession.MASON);
			pick(helper, player, menu, VillagerProfession.MASON);
			helper.assertTrue(dara.getVillagerData().getProfession() == VillagerProfession.LIBRARIAN && site(dara).equals(Optional.of(helper.absolutePos(LECTERN)))
				&& player.getInventory().countItem(Items.EMERALD) == 64, "something changed: " + dara.getVillagerData());
			helper.assertTrue(seen.contains("The Mason job needs a free Stonecutter: place one in the village first."), "not told: " + seen);
			helper.assertTrue(row(helper, menu, dara).getBoolean("jobs_open"), "the list closed");
			helper.succeed();
		});
	}

	/** Not enough emeralds: both clicks change nothing (job, block, level, money), the player is told how short, and the list stays. */
	//$ gametest_ticks_batch AREA '200' '"hallJobShort"'
	@GameTest(template = AREA, timeoutTicks = 200, batch = "hallJobShort")
	public void notEnoughEmeraldsChangesNothing(GameTestHelper helper) {
		village(helper);
		helper.setBlock(STONECUTTER, Blocks.STONECUTTER);
		Villager dara = librarian(helper, 3);
		List<String> seen = new ArrayList<>();
		ServerPlayer player = payer(helper, seen, 19);
		helper.runAfterDelay(3, () -> {
			ChoiceMenu menu = openPeople(helper, player);
			List<ItemStack> lines = jobDropdown(helper, player, menu, dara);
			helper.assertTrue(lore(lines.get(0)).contains("Retraining costs 20 emeralds (Journeyman)"), "the price: " + lore(lines.get(0)));
			pick(helper, player, menu, VillagerProfession.MASON);
			pick(helper, player, menu, VillagerProfession.MASON);
			helper.assertTrue(dara.getVillagerData().getProfession() == VillagerProfession.LIBRARIAN && dara.getVillagerData().getLevel() == 3
				&& site(dara).equals(Optional.of(helper.absolutePos(LECTERN))), "changed: " + dara.getVillagerData());
			helper.assertTrue(player.getInventory().countItem(Items.EMERALD) == 19, "money was taken");
			helper.assertTrue(helper.getLevel().getPoiManager().getFreeTickets(helper.absolutePos(STONECUTTER)) == 1, "the stonecutter was taken");
			helper.assertTrue(seen.stream().anyMatch(s -> s.startsWith("Retraining Dara costs 20 emeralds: you're 1 emerald")), "not told: " + seen);
			List<ItemStack> after = lines(helper, menu, dara);
			helper.assertTrue(!after.isEmpty() && state(lineFor(helper, after, VillagerProfession.MASON)).equals("free"), "the list closed or kept the pick");
			helper.succeed();
		});
	}

	/** To the Builder: with a free Blueprint Table, "Builder · 1 free Blueprint Table", and two clicks make her the builder there. */
	//$ gametest_ticks_batch AREA '200' '"hallJobToBuilder"'
	@GameTest(template = AREA, timeoutTicks = 200, batch = "hallJobToBuilder")
	public void toTheBuilder(GameTestHelper helper) {
		village(helper);
		BlockPos table = new BlockPos(20, 2, 8);
		helper.setBlock(table, ModBlocks.BLUEPRINT_TABLE);
		Villager dara = librarian(helper, 1);
		List<String> seen = new ArrayList<>();
		ServerPlayer player = payer(helper, seen, 5);
		helper.runAfterDelay(3, () -> {
			ChoiceMenu menu = openPeople(helper, player);
			List<ItemStack> lines = jobDropdown(helper, player, menu, dara);
			ItemStack builder = lineFor(helper, lines, ModVillagers.BUILDER);
			helper.assertTrue(builder.getHoverName().getString().equals("Builder · 1 free Blueprint Table") && state(builder).equals("free"),
				"the Builder line: " + builder.getHoverName().getString());
			pick(helper, player, menu, ModVillagers.BUILDER);
			pick(helper, player, menu, ModVillagers.BUILDER);
			helper.assertTrue(Builders.isBuilder(dara) && site(dara).equals(Optional.of(helper.absolutePos(table))), "not the builder: " + dara.getVillagerData());
			helper.assertTrue(player.getInventory().countItem(Items.EMERALD) == 0, "a Novice's price isn't 5");
			helper.assertTrue(row(helper, menu, dara).getString("kind").equals("builder"), "her row: " + row(helper, menu, dara));
			helper.succeed();
		});
	}

	/**
	 * From the Builder: a builder mid-build made a Mason hands his build back (the site is no longer his, the blueprint
	 * returns to its owner still placed) and the Blueprint Table is free.
	 */
	//$ gametest_ticks_batch AREA '200' '"hallJobFromBuilder"'
	@GameTest(template = AREA, timeoutTicks = 200, batch = "hallJobFromBuilder")
	public void aBusyBuilderHandsHisBuildBack(GameTestHelper helper) {
		village(helper);
		ServerLevel level = helper.getLevel();
		helper.setBlock(STONECUTTER, Blocks.STONECUTTER);
		BlockPos table = new BlockPos(8, 2, 20);
		helper.setBlock(table, ModBlocks.BLUEPRINT_TABLE);
		List<String> seen = new ArrayList<>();
		ServerPlayer player = payer(helper, seen, 5);
		Villager bob = helper.spawn(EntityType.VILLAGER, new BlockPos(9, 2, 20));
		bob.setNoAi(true);
		bob.setCustomName(Component.literal("Bob"));
		Builders.employ(level, bob, helper.absolutePos(table));
		BlueprintData.Placement placement = new BlueprintData.Placement(level.dimension().location(), helper.absolutePos(new BlockPos(3, 2, 24)),
			Rotation.NONE, Mirror.NONE);
		BuildSite site = Builders.start(level, bob, player, TEST_HUT, placement);
		helper.assertTrue(Builders.activeSite(level, bob) == site, "setup: no build under way");
		helper.runAfterDelay(3, () -> {
			ChoiceMenu menu = openPeople(helper, player);
			helper.assertTrue(row(helper, menu, bob).getString("job_action").equals("retrain"), "a builder can't change job: " + row(helper, menu, bob));
			List<ItemStack> lines = jobDropdown(helper, player, menu, bob);
			helper.assertTrue(lineFor(helper, lines, ModVillagers.BUILDER).getHoverName().getString().equals("Builder · their job now"), "his own job");
			pick(helper, player, menu, VillagerProfession.MASON);
			pick(helper, player, menu, VillagerProfession.MASON);
			helper.assertTrue(bob.getVillagerData().getProfession() == VillagerProfession.MASON && !Builders.isBuilder(bob), "now " + bob.getVillagerData());
			helper.assertTrue(BuildSiteManager.get(level).get(site.id()) == null && !ModAttachments.BUILDER_JOB.has(bob), "the build is still Bob's");
			helper.assertTrue(seen.contains("Bob handed back the Test Hut: what's built stays, and its blueprint came back, still placed, for another builder to carry on."),
				"not told: " + seen);
			ItemStack blueprint = ItemStack.EMPTY;
			for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
				if (player.getInventory().getItem(i).is(ModItems.BLUEPRINT)) {
					blueprint = player.getInventory().getItem(i);
				}
			}
			helper.assertTrue(BlueprintItem.data(blueprint).flatMap(BlueprintData::placement).map(BlueprintData.Placement::origin)
				.equals(Optional.of(placement.origin())), "the blueprint didn't come back placed: " + blueprint);
			helper.assertTrue(level.getPoiManager().getFreeTickets(helper.absolutePos(table)) == 1, "Bob still holds the Blueprint Table");
			helper.succeed();
		});
	}

	/**
	 * No job: free, two clicks (the first says plainly what goes); she's jobless after, her lectern free, her level and
	 * trades gone, the money untouched, and her row says No job. Saved and loaded, she's still jobless.
	 */
	//$ gametest_ticks_batch AREA '200' '"hallJobNone"'
	@GameTest(template = AREA, timeoutTicks = 200, batch = "hallJobNone")
	public void noJob(GameTestHelper helper) {
		village(helper);
		Villager dara = librarian(helper, 4);
		List<String> seen = new ArrayList<>();
		ServerPlayer player = payer(helper, seen, 3);
		helper.runAfterDelay(3, () -> {
			ChoiceMenu menu = openPeople(helper, player);
			List<ItemStack> lines = jobDropdown(helper, player, menu, dara);
			ItemStack none = lines.get(lines.size() - 1);
			helper.assertTrue(lore(none).stream().anyMatch(l -> l.startsWith("Free. They stop working: their trades and level are lost")), "No job's tooltip: " + lore(none));
			pick(helper, player, menu, VillagerProfession.NONE);
			helper.assertTrue(dara.getVillagerData().getProfession() == VillagerProfession.LIBRARIAN, "the first click took her job");
			helper.assertTrue(seen.contains("Dara (Librarian, Expert) can give up their job for nothing. Their trades and level are lost, and their work block is left free."),
				"no offer: " + seen);
			ItemStack picked = lines(helper, menu, dara).get(lines(helper, menu, dara).size() - 1);
			helper.assertTrue(state(picked).equals("no_job_picked") && lore(picked).contains("Click again: Dara gives up the Librarian job"), "picked: " + lore(picked));
			pick(helper, player, menu, VillagerProfession.NONE);
			helper.assertTrue(dara.getVillagerData().getProfession() == VillagerProfession.NONE && dara.getVillagerData().getLevel() == 1
				&& dara.getVillagerXp() == 0 && site(dara).isEmpty(), "still working: " + dara.getVillagerData() + " at " + site(dara));
			helper.assertTrue(helper.getLevel().getPoiManager().getFreeTickets(helper.absolutePos(LECTERN)) == 1, "the lectern isn't free");
			helper.assertTrue(player.getInventory().countItem(Items.EMERALD) == 3, "No job cost money");
			helper.assertTrue(seen.contains("Dara gave up the Librarian job and has no job now."), "not told: " + seen);
			CompoundTag after = row(helper, menu, dara);
			helper.assertTrue(after.getString("kind").equals("jobless") && after.getString("job_action").equals("assign") && !after.getBoolean("jobs_open"),
				"her row after: " + after);
			Villager copy = EntityType.VILLAGER.create(helper.getLevel());
			copy.load(dara.saveWithoutId(new CompoundTag()));
			helper.assertTrue(copy.getVillagerData().getProfession() == VillagerProfession.NONE && site(copy).isEmpty(), "loaded as " + copy.getVillagerData());
			copy.discard();
			helper.succeed();
		});
	}

	/**
	 * A jobless villager: the list says they're given a block for nothing, there's no No job, and one click on a free
	 * job gives it (no money asked).
	 */
	//$ gametest_ticks_batch AREA '200' '"hallJobJobless"'
	@GameTest(template = AREA, timeoutTicks = 200, batch = "hallJobJobless")
	public void aJoblessVillagerIsGivenABlockForNothing(GameTestHelper helper) {
		village(helper);
		helper.setBlock(STONECUTTER, Blocks.STONECUTTER);
		Villager pip = helper.spawn(EntityType.VILLAGER, new BlockPos(10, 2, 10));
		pip.setNoAi(true);
		pip.setCustomName(Component.literal("Pip"));
		List<String> seen = new ArrayList<>();
		ServerPlayer player = payer(helper, seen, 0);
		helper.runAfterDelay(3, () -> {
			ChoiceMenu menu = openPeople(helper, player);
			List<ItemStack> lines = jobDropdown(helper, player, menu, pip);
			helper.assertTrue(lore(lines.get(0)).contains("They have no job yet: a free work block is theirs for nothing, in one click."), "header: " + lore(lines.get(0)));
			helper.assertTrue(lines.stream().noneMatch(l -> state(l).startsWith("no_job")), "a jobless villager is offered No job");
			ItemStack mason = lineFor(helper, lines, VillagerProfession.MASON);
			helper.assertTrue(lore(mason).contains("Click: Pip becomes a Mason"), "the Mason line: " + lore(mason));
			pick(helper, player, menu, VillagerProfession.MASON);
			helper.assertTrue(pip.getVillagerData().getProfession() == VillagerProfession.MASON && site(pip).equals(Optional.of(helper.absolutePos(STONECUTTER))),
				"not a mason: " + pip.getVillagerData());
			helper.assertTrue(seen.contains("Pip is now a Mason"), "not told: " + seen);
			helper.succeed();
		});
	}

	/** Children and nitwits can't: their rows offer no Job ▾, and the click (as a stale screen would send it) opens nothing. */
	//$ gametest_ticks_batch AREA '200' '"hallJobChildNitwit"'
	@GameTest(template = AREA, timeoutTicks = 200, batch = "hallJobChildNitwit")
	public void childrenAndNitwitsCant(GameTestHelper helper) {
		village(helper);
		helper.setBlock(STONECUTTER, Blocks.STONECUTTER);
		Villager child = helper.spawn(EntityType.VILLAGER, new BlockPos(10, 2, 10));
		child.setNoAi(true);
		child.setAge(-24000);
		Villager nitwit = helper.spawn(EntityType.VILLAGER, new BlockPos(12, 2, 10));
		nitwit.setNoAi(true);
		nitwit.setVillagerData(nitwit.getVillagerData().setProfession(VillagerProfession.NITWIT));
		List<String> seen = new ArrayList<>();
		ServerPlayer player = payer(helper, seen, 64);
		helper.runAfterDelay(3, () -> {
			ChoiceMenu menu = openPeople(helper, player);
			for (Villager v : List.of(child, nitwit)) {
				helper.assertTrue(row(helper, menu, v).getString("job_action").isEmpty(), "a child or nitwit has Job ▾: " + row(helper, menu, v));
				List<ItemStack> lines = jobDropdown(helper, player, menu, v);
				helper.assertTrue(lines.isEmpty() && !row(helper, menu, v).getBoolean("jobs_open"), "a list opened for " + row(helper, menu, v));
				helper.assertTrue(menu.clickMenuButton(player, HallJobs.PICK + id(VillagerProfession.MASON)), "the pick wasn't taken");
			}
			helper.assertTrue(nitwit.getVillagerData().getProfession() == VillagerProfession.NITWIT && child.getVillagerData().getProfession() == VillagerProfession.NONE,
				"a child or nitwit was given a job");
			helper.assertTrue(player.getInventory().countItem(Items.EMERALD) == 64, "money was taken");
			helper.succeed();
		});
	}

	/** Game rule workplaceRetraining off: Job ▾ is hidden on every row, and its click opens nothing and changes nothing. */
	//$ gametest_ticks_batch AREA '200' '"hallJobRuleOff"'
	@GameTest(template = AREA, timeoutTicks = 200, batch = "hallJobRuleOff")
	public void theRuleOffHidesTheButton(GameTestHelper helper) {
		village(helper);
		helper.setBlock(STONECUTTER, Blocks.STONECUTTER);
		Villager dara = librarian(helper, 1);
		Villager pip = helper.spawn(EntityType.VILLAGER, new BlockPos(10, 2, 10));
		pip.setNoAi(true);
		List<String> seen = new ArrayList<>();
		ServerPlayer player = payer(helper, seen, 64);
		ServerLevel level = helper.getLevel();
		helper.runAfterDelay(3, () -> {
			level.getGameRules().getRule(ModGameRules.RETRAINING).set(false, level.getServer());
			try {
				ChoiceMenu menu = openPeople(helper, player);
				for (Villager v : List.of(dara, pip)) {
					helper.assertTrue(row(helper, menu, v).getBoolean("job_hidden"), "Job ▾ shown with the rule off: " + row(helper, menu, v));
					List<ItemStack> lines = jobDropdown(helper, player, menu, v);
					helper.assertTrue(lines.isEmpty(), "the rule off still opens the list");
				}
				helper.assertTrue(menu.clickMenuButton(player, HallJobs.PICK + id(VillagerProfession.MASON)), "the pick wasn't taken");
				helper.assertTrue(dara.getVillagerData().getProfession() == VillagerProfession.LIBRARIAN && pip.getVillagerData().getProfession() == VillagerProfession.NONE
					&& player.getInventory().countItem(Items.EMERALD) == 64, "the rule off still changed a job");
			} finally {
				level.getGameRules().getRule(ModGameRules.RETRAINING).set(true, level.getServer());
			}
			ChoiceMenu again = openPeople(helper, player);
			helper.assertTrue(!row(helper, again, dara).getBoolean("job_hidden"), "the rule back on still hides Job ▾");
			helper.succeed();
		});
	}

	/**
	 * Saved and reloaded after a change from the table: still the Novice Mason at the stonecutter, still on her way there.
	 * The open list isn't saved: a new screen opens with it closed. Every new sentence is in the language file.
	 */
	//$ gametest_ticks_batch AREA '200' '"hallJobSave"'
	@GameTest(template = AREA, timeoutTicks = 200, batch = "hallJobSave")
	public void aChangeSurvivesASaveAndReload(GameTestHelper helper) {
		village(helper);
		helper.setBlock(STONECUTTER, Blocks.STONECUTTER);
		Villager dara = librarian(helper, 5);
		List<String> seen = new ArrayList<>();
		ServerPlayer player = payer(helper, seen, 50);
		helper.runAfterDelay(3, () -> {
			ChoiceMenu menu = openPeople(helper, player);
			jobDropdown(helper, player, menu, dara);
			pick(helper, player, menu, VillagerProfession.MASON);
			pick(helper, player, menu, VillagerProfession.MASON);
			helper.assertTrue(player.getInventory().countItem(Items.EMERALD) == 0, "a Master's price isn't 50");
			Villager copy = EntityType.VILLAGER.create(helper.getLevel());
			copy.load(dara.saveWithoutId(new CompoundTag()));
			helper.assertTrue(copy.getVillagerData().getProfession() == VillagerProfession.MASON && copy.getVillagerData().getLevel() == 1,
				"loaded as " + copy.getVillagerData());
			helper.assertTrue(site(copy).equals(Optional.of(helper.absolutePos(STONECUTTER))), "loaded working at " + site(copy));
			helper.assertTrue(ModAttachments.RETRAIN_WALK.get(copy) != null && ModAttachments.RETRAIN_WALK.get(copy).station().equals(helper.absolutePos(STONECUTTER)),
				"the walk was lost");
			copy.discard();
			// A new screen: the list closed.
			jobDropdown(helper, player, menu, dara);
			ChoiceMenu again = openPeople(helper, player);
			helper.assertTrue(!row(helper, again, dara).getBoolean("jobs_open"), "a new screen opened with the list open");
			Language lang = Language.getInstance();
			for (String key : List.of("screen.aliveworkplace.hall.jobs.title", "screen.aliveworkplace.hall.jobs.free_for_jobless", "screen.aliveworkplace.hall.jobs.free",
				"screen.aliveworkplace.hall.jobs.none", "screen.aliveworkplace.hall.jobs.none.an", "screen.aliveworkplace.hall.jobs.none_hint",
				"screen.aliveworkplace.hall.jobs.class", "screen.aliveworkplace.hall.jobs.current", "screen.aliveworkplace.hall.jobs.nearest",
				"screen.aliveworkplace.hall.jobs.no_job", "screen.aliveworkplace.hall.jobs.no_job_about", "screen.aliveworkplace.hall.jobs.no_job_pick",
				"screen.aliveworkplace.hall.jobs.no_job_pay", "screen.aliveworkplace.hall.people.job_open", "message.aliveworkplace.hall.jobs.cant",
				"message.aliveworkplace.hall.jobs.no_block", "message.aliveworkplace.hall.jobs.no_job_offer", "message.aliveworkplace.hall.jobs.no_job_done")) {
				helper.assertTrue(lang.has(key), "no text for " + key);
			}
			helper.assertTrue(Component.translatable("screen.aliveworkplace.hall.jobs.none.an", "Armorer", "Anvil").getString().equals("Armorer · place an Anvil first"),
				"the article");
			helper.succeed();
		});
	}
}
