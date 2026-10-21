package io.github.jcondedata.aliveworkplace.test;

import static io.github.jcondedata.aliveworkplace.test.StationsSpecGameTests.listeningPlayer;

import io.github.jcondedata.aliveworkplace.blueprint.BlueprintData;
import io.github.jcondedata.aliveworkplace.blueprint.BlueprintItem;
import io.github.jcondedata.aliveworkplace.build.BuildSite;
import io.github.jcondedata.aliveworkplace.build.BuildSiteManager;
import io.github.jcondedata.aliveworkplace.build.Builders;
import io.github.jcondedata.aliveworkplace.hall.HallRetraining;
import io.github.jcondedata.aliveworkplace.hall.VillageHallScreen;
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
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffects;
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
 * Changing a villager's job from the Village Hall (ROADMAP 36.18), through the hall's own screen: a worker's page has a
 * Change job button listing the free job blocks; a first click shows the price by level, a second pays, and the new job
 * and its block are theirs on the click, before any walking. One walled off from the block is moved beside it; not enough
 * emeralds changes nothing; the old block is left free; a busy builder hands the build back; the rule off hides the
 * button; and it all survives a save.
 */
public class HallRetrainingGameTests implements FabricGameTest {
	private static final String AREA = "aliveworkplace_test:big_area";
	private static final BlockPos HALL = new BlockPos(11, 2, 11);
	/** The librarian's lectern. */
	private static final BlockPos LECTERN = new BlockPos(4, 2, 4);
	/** Where the librarian stands, away from the Storehouse. */
	private static final BlockPos STANDING = new BlockPos(5, 2, 5);
	/** The free job block: a Storehouse (the Porter's), across the village. */
	private static final BlockPos STOREHOUSE = new BlockPos(18, 2, 17);
	private static final ResourceLocation TEST_HUT = ResourceLocation.fromNamespaceAndPath("aliveworkplace_test", "test_hut");

	/** The village: the hall and a free Storehouse, the village kept to this test's area. */
	private static void village(GameTestHelper helper) {
		Leftovers.clear(helper);
		int radius = VillageHalls.RADIUS;
		VillageHalls.RADIUS = 16;
		Leftovers.after(helper, () -> VillageHalls.RADIUS = radius);
		helper.setBlock(HALL, ModBlocks.VILLAGE_HALL);
		helper.setBlock(STOREHOUSE, ModBlocks.STOREHOUSE);
	}

	/** Dara, a librarian at the lectern, at {@code level} (Novice 1 to Master 5), with trades. */
	private static Villager librarian(GameTestHelper helper, int level, boolean ai) {
		helper.setBlock(LECTERN, Blocks.LECTERN);
		Villager villager = helper.spawn(EntityType.VILLAGER, STANDING);
		villager.setNoAi(!ai);
		Jobs.employ(helper.getLevel(), villager, helper.absolutePos(LECTERN), PoiTypes.LIBRARIAN, VillagerProfession.LIBRARIAN);
		villager.setVillagerData(villager.getVillagerData().setLevel(level));
		villager.setVillagerXp(level == 1 ? 5 : 70 * level);
		villager.setCustomName(Component.literal("Dara"));
		return villager;
	}

	private static ServerPlayer payer(GameTestHelper helper, List<String> seen, int emeralds) {
		ServerPlayer player = listeningPlayer(helper, seen);
		if (emeralds > 0) {
			player.getInventory().setItem(9, new ItemStack(Items.EMERALD, emeralds));
		}
		return player;
	}

	private static List<String> lore(ItemStack stack) {
		return stack.getOrDefault(DataComponents.LORE, ItemLore.EMPTY).lines().stream().map(Component::getString).toList();
	}

	/** The residents list's slot for the villager called {@code name}. */
	private static int resident(GameTestHelper helper, ChoiceMenu menu, String name) {
		for (int s = VillageHallScreen.FIRST_PERSON; s < ChoiceMenu.SIZE; s++) {
			if (menu.icon(s).getHoverName().getString().startsWith(name)) {
				return s;
			}
		}
		throw new net.minecraft.gametest.framework.GameTestAssertException(name + " isn't on the hall's list");
	}

	/** Right-clicks {@code name} on the list, as the client sends it: their page, with Change job. */
	private static void rightClick(ChoiceMenu menu, int slot, ServerPlayer player) {
		menu.clicked(slot, 1, net.minecraft.world.inventory.ClickType.PICKUP, player);
	}

	/** Opens the hall, right-clicks {@code name} on the list and then Change job: the page of jobs they could change to. */
	private static ChoiceMenu jobsPage(GameTestHelper helper, ServerPlayer player, String name) {
		ChoiceMenu menu = VillageHallScreen.forTest(player, helper.absolutePos(HALL));
		rightClick(menu, resident(helper, menu, name), player);
		ItemStack change = menu.icon(HallRetraining.CHANGE);
		helper.assertTrue(change.is(Items.EMERALD) && change.getHoverName().getString().equals("Change job"), "no Change job button: " + change);
		menu.press(HallRetraining.CHANGE, player);
		return menu;
	}

	private static Optional<BlockPos> site(Villager villager) {
		return villager.getBrain().getMemory(MemoryModuleType.JOB_SITE).map(GlobalPos::pos);
	}

	/**
	 * The owner's case: a Journeyman Librarian made the Porter from the hall. The first click on the Storehouse shows the
	 * price (20 emeralds) and changes nothing; the second pays, and at once, while she still stands by her old lectern,
	 * she's a Novice Porter whose job site is the Storehouse and whose claim on it is made; she's on her way; the lectern
	 * is free for someone else; and the hall's list shows her new job.
	 */
	//$ gametest_ticks_batch AREA '100' '"hallRetrainPorter"'
	@GameTest(template = AREA, timeoutTicks = 100, batch = "hallRetrainPorter")
	public void aWorkerChangesJobFromTheHall(GameTestHelper helper) {
		village(helper);
		ServerLevel level = helper.getLevel();
		BlockPos hall = helper.absolutePos(HALL);
		BlockPos store = helper.absolutePos(STOREHOUSE);
		BlockPos lectern = helper.absolutePos(LECTERN);
		Villager dara = librarian(helper, 3, false);
		List<String> seen = new ArrayList<>();
		ServerPlayer player = payer(helper, seen, 25);
		helper.runAfterDelay(2, () -> {
			ChoiceMenu list = VillageHallScreen.forTest(player, hall);
			int daraSlot = resident(helper, list, "Dara");
			helper.assertTrue(lore(list.icon(daraSlot)).contains("Right-click: change their job"),
				"the list doesn't say a right-click changes her job: " + lore(list.icon(daraSlot)));
			// a plain click only makes her glow, as before: the list stays
			list.clicked(daraSlot, 0, net.minecraft.world.inventory.ClickType.PICKUP, player);
			helper.assertTrue(dara.hasEffect(MobEffects.GLOWING) && !list.icon(HallRetraining.CHANGE).is(Items.EMERALD)
				&& resident(helper, list, "Dara") == daraSlot, "a plain click didn't just make her glow");
			ChoiceMenu menu = jobsPage(helper, player, "Dara");
			ItemStack storeIcon = menu.icon(VillageHallScreen.FIRST_ROW);
			helper.assertTrue(storeIcon.is(ModBlocks.STOREHOUSE.asItem()) && storeIcon.getHoverName().getString().equals("Porter"), "the Storehouse first: " + storeIcon);
			helper.assertTrue(lore(storeIcon).contains("Retraining costs 20 emeralds (Journeyman)"), "the price: " + lore(storeIcon));

			menu.press(VillageHallScreen.FIRST_ROW, player);
			helper.assertTrue(dara.getVillagerData().getProfession() == VillagerProfession.LIBRARIAN, "the first click retrained her");
			helper.assertTrue(player.getInventory().countItem(Items.EMERALD) == 25, "the first click took money");
			helper.assertTrue(seen.contains("Dara (Librarian, Journeyman) can be retrained as a Porter for 20 emeralds. Their trades and level are lost: they start again as a Novice."),
				"the offer wasn't shown: " + seen);
			helper.assertTrue(lore(menu.icon(VillageHallScreen.FIRST_ROW)).contains("Click again to pay 20 emeralds: Dara becomes a Porter"),
				"the Storehouse doesn't ask for the second click: " + lore(menu.icon(VillageHallScreen.FIRST_ROW)));

			BlockPos stood = dara.blockPosition();
			menu.press(VillageHallScreen.FIRST_ROW, player);
			helper.assertTrue(dara.getVillagerData().getProfession() == ModVillagers.PORTER, "not a Porter: " + dara.getVillagerData().getProfession());
			helper.assertTrue(dara.getVillagerData().getLevel() == 1, "level " + dara.getVillagerData().getLevel());
			helper.assertTrue(site(dara).equals(Optional.of(store)), "job site: " + site(dara));
			helper.assertTrue(dara.blockPosition().equals(stood) && !dara.blockPosition().closerThan(store, 4), "she was moved on the click");
			helper.assertTrue(level.getPoiManager().getFreeTickets(store) == 0, "the Storehouse isn't claimed");
			helper.assertTrue(ModAttachments.RETRAIN_WALK.has(dara) && dara.getBrain().getMemory(MemoryModuleType.WALK_TARGET)
				.map(w -> w.getTarget().currentBlockPosition().equals(store)).orElse(false), "not on her way to the Storehouse");
			helper.assertTrue(player.getInventory().countItem(Items.EMERALD) == 5, "paid " + (25 - player.getInventory().countItem(Items.EMERALD)));
			helper.assertTrue(level.getPoiManager().getFreeTickets(lectern) == 1, "the lectern is still held");
			helper.assertTrue(VillageHalls.freeStations(level, hall).stream().anyMatch(f -> f.pos().equals(lectern) && f.profession() == VillagerProfession.LIBRARIAN),
				"the lectern isn't among the free job blocks");
			helper.assertTrue(menu.icon(resident(helper, menu, "Dara")).is(ModBlocks.STOREHOUSE.asItem()), "the hall doesn't show her new job");
			helper.assertTrue(seen.stream().anyMatch(s -> s.startsWith("Dara gave up the Librarian job and was retrained as a Porter")), "not told: " + seen);
			helper.succeed();
		});
	}

	/** The price goes by level, as at a Blueprint Table: 5, 10, 20, 35 and 50 emeralds; a Master pays exactly 50. */
	//$ gametest_ticks_batch AREA '100' '"hallRetrainPrice"'
	@GameTest(template = AREA, timeoutTicks = 100, batch = "hallRetrainPrice")
	public void thePriceGoesByLevel(GameTestHelper helper) {
		village(helper);
		Villager dara = librarian(helper, 1, false);
		List<String> seen = new ArrayList<>();
		ServerPlayer player = payer(helper, seen, 50);
		helper.runAfterDelay(2, () -> {
			String[] levels = {"Novice", "Apprentice", "Journeyman", "Expert", "Master"};
			int[] prices = {5, 10, 20, 35, 50};
			for (int i = 0; i < 5; i++) {
				dara.setVillagerData(dara.getVillagerData().setLevel(i + 1));
				ChoiceMenu menu = VillageHallScreen.forTest(player, helper.absolutePos(HALL));
				rightClick(menu, resident(helper, menu, "Dara"), player);
				String line = "Retraining costs " + prices[i] + " emeralds (" + levels[i] + ")";
				helper.assertTrue(lore(menu.icon(HallRetraining.CHANGE)).contains(line), "not '" + line + "': " + lore(menu.icon(HallRetraining.CHANGE)));
			}
			ChoiceMenu menu = jobsPage(helper, player, "Dara");
			menu.press(VillageHallScreen.FIRST_ROW, player);
			menu.press(VillageHallScreen.FIRST_ROW, player);
			helper.assertTrue(dara.getVillagerData().getProfession() == ModVillagers.PORTER, "a Master with 50 emeralds wasn't retrained");
			helper.assertTrue(player.getInventory().countItem(Items.EMERALD) == 0, "a Master paid " + (50 - player.getInventory().countItem(Items.EMERALD)));
			helper.succeed();
		});
	}

	/** One emerald short: both clicks change nothing: the job, the lectern, the free Storehouse and the money all stay. */
	//$ gametest_ticks_batch AREA '100' '"hallRetrainShort"'
	@GameTest(template = AREA, timeoutTicks = 100, batch = "hallRetrainShort")
	public void notEnoughEmeraldsChangesNothing(GameTestHelper helper) {
		village(helper);
		ServerLevel level = helper.getLevel();
		BlockPos store = helper.absolutePos(STOREHOUSE);
		Villager dara = librarian(helper, 3, false);
		List<String> seen = new ArrayList<>();
		ServerPlayer player = payer(helper, seen, 19);
		helper.runAfterDelay(2, () -> {
			ChoiceMenu menu = jobsPage(helper, player, "Dara");
			menu.press(VillageHallScreen.FIRST_ROW, player);
			menu.press(VillageHallScreen.FIRST_ROW, player);
			helper.assertTrue(dara.getVillagerData().getProfession() == VillagerProfession.LIBRARIAN && dara.getVillagerData().getLevel() == 3,
				"changed anyway: " + dara.getVillagerData());
			helper.assertTrue(site(dara).equals(Optional.of(helper.absolutePos(LECTERN))), "job site: " + site(dara));
			helper.assertTrue(level.getPoiManager().getFreeTickets(store) == 1, "the Storehouse was claimed");
			helper.assertTrue(player.getInventory().countItem(Items.EMERALD) == 19, "money was taken");
			helper.assertTrue(!ModAttachments.RETRAIN_WALK.has(dara), "she's on her way somewhere");
			helper.assertTrue(seen.contains("Retraining Dara costs 20 emeralds: you're 1 emeralds short. Nothing changed.")
				|| seen.stream().anyMatch(s -> s.startsWith("Retraining Dara costs 20 emeralds: you're 1 emerald")), "not told: " + seen);
			helper.assertTrue(menu.icon(VillageHallScreen.FIRST_ROW).is(ModBlocks.STOREHOUSE.asItem()), "the jobs page went away: " + menu.icon(VillageHallScreen.FIRST_ROW));
			helper.succeed();
		});
	}

	/**
	 * Walled in with no way to the Storehouse: she's the Porter on the click all the same, and once her time to walk
	 * there is up she's moved beside it, still the Porter working there.
	 */
	//$ gametest_ticks_batch AREA '400' '"hallRetrainWalledOff"'
	@GameTest(template = AREA, timeoutTicks = 400, batch = "hallRetrainWalledOff")
	public void aVillagerWalledOffStillGetsThere(GameTestHelper helper) {
		village(helper);
		int arrive = HallRetraining.ARRIVE_TICKS;
		HallRetraining.ARRIVE_TICKS = 100; // the minute, shortened for the test
		Leftovers.after(helper, () -> HallRetraining.ARRIVE_TICKS = arrive);
		BlockPos store = helper.absolutePos(STOREHOUSE);
		Villager dara = librarian(helper, 2, true);
		// a glass cell round her: two blocks of air, walls and a roof
		for (int x = 4; x <= 6; x++) {
			for (int z = 4; z <= 6; z++) {
				for (int y = 2; y <= 4; y++) {
					if (!(x == 5 && z == 5 && y < 4) && !(x == 4 && z == 4 && y == 2)) { // (her lectern makes the cell's corner)
						helper.setBlock(new BlockPos(x, y, z), Blocks.GLASS);
					}
				}
			}
		}
		List<String> seen = new ArrayList<>();
		ServerPlayer player = payer(helper, seen, 10);
		long[] clicked = new long[1];
		helper.runAfterDelay(2, () -> {
			ChoiceMenu menu = jobsPage(helper, player, "Dara");
			menu.press(VillageHallScreen.FIRST_ROW, player);
			menu.press(VillageHallScreen.FIRST_ROW, player);
			helper.assertTrue(dara.getVillagerData().getProfession() == ModVillagers.PORTER && site(dara).equals(Optional.of(store)),
				"not the Porter on the click: " + dara.getVillagerData().getProfession() + " " + site(dara));
			clicked[0] = helper.getLevel().getGameTime();
		});
		helper.succeedWhen(() -> {
			helper.assertTrue(clicked[0] > 0, "not clicked yet");
			helper.assertTrue(dara.position().distanceTo(store.getCenter()) <= 3.0, "not by the Storehouse: " + dara.blockPosition());
			helper.assertTrue(helper.getLevel().getGameTime() - clicked[0] >= 100, "got out of a glass cell on her own");
			helper.assertTrue(dara.getVillagerData().getProfession() == ModVillagers.PORTER && site(dara).equals(Optional.of(store)),
				"no longer the Porter at the Storehouse: " + dara.getVillagerData().getProfession() + " " + site(dara));
			helper.assertTrue(!ModAttachments.RETRAIN_WALK.has(dara), "still on her way");
		});
	}

	/**
	 * A builder mid-build changed to the Porter hands the build back: the site is gone from them, the blueprint returns to
	 * its owner still placed, and the Blueprint Table is free.
	 */
	//$ gametest_ticks_batch AREA '100' '"hallRetrainBuilder"'
	@GameTest(template = AREA, timeoutTicks = 100, batch = "hallRetrainBuilder")
	public void aBusyBuilderHandsTheBuildBack(GameTestHelper helper) {
		village(helper);
		ServerLevel level = helper.getLevel();
		BlockPos table = new BlockPos(3, 2, 8);
		helper.setBlock(table, ModBlocks.BLUEPRINT_TABLE);
		List<String> seen = new ArrayList<>();
		ServerPlayer player = payer(helper, seen, 5);
		Villager bob = helper.spawn(EntityType.VILLAGER, new BlockPos(4, 2, 8));
		bob.setNoAi(true);
		bob.setCustomName(Component.literal("Bob"));
		Builders.employ(level, bob, helper.absolutePos(table));
		BlueprintData.Placement placement = new BlueprintData.Placement(level.dimension().location(), helper.absolutePos(new BlockPos(2, 2, 14)),
			Rotation.NONE, Mirror.NONE);
		BuildSite site = Builders.start(level, bob, player, TEST_HUT, placement);
		helper.assertTrue(Builders.activeSite(level, bob) == site, "setup: no build under way");
		helper.runAfterDelay(2, () -> {
			ChoiceMenu menu = jobsPage(helper, player, "Bob");
			helper.assertTrue(menu.icon(VillageHallScreen.FIRST_ROW).is(ModBlocks.STOREHOUSE.asItem()), "the Storehouse: " + menu.icon(VillageHallScreen.FIRST_ROW));
			menu.press(VillageHallScreen.FIRST_ROW, player);
			menu.press(VillageHallScreen.FIRST_ROW, player);
			helper.assertTrue(bob.getVillagerData().getProfession() == ModVillagers.PORTER, "now a " + bob.getVillagerData().getProfession());
			helper.assertTrue(BuildSiteManager.get(level).get(site.id()) == null, "the site still stands as Bob's");
			helper.assertTrue(!ModAttachments.BUILDER_JOB.has(bob), "the porter still has the build");
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
	 * The Builder is on the list: with no Blueprint Table free it says one is needed; with one, a librarian becomes the
	 * Builder at it.
	 */
	//$ gametest_ticks_batch AREA '100' '"hallRetrainToBuilder"'
	@GameTest(template = AREA, timeoutTicks = 100, batch = "hallRetrainToBuilder")
	public void theBuilderIsOnTheList(GameTestHelper helper) {
		village(helper);
		Villager dara = librarian(helper, 1, false);
		List<String> seen = new ArrayList<>();
		ServerPlayer player = payer(helper, seen, 5);
		BlockPos table = new BlockPos(16, 2, 4);
		helper.runAfterDelay(2, () -> {
			ChoiceMenu menu = jobsPage(helper, player, "Dara");
			ItemStack builder = menu.icon(VillageHallScreen.FIRST_ROW + 1);
			helper.assertTrue(builder.is(ModBlocks.BLUEPRINT_TABLE.asItem()) && builder.getHoverName().getString().equals("Builder")
				&& lore(builder).stream().anyMatch(l -> l.contains("Blueprint Table")), "the Builder without a table: " + builder + " " + lore(builder));
			menu.press(VillageHallScreen.FIRST_ROW + 1, player);
			helper.assertTrue(dara.getVillagerData().getProfession() == VillagerProfession.LIBRARIAN, "the Builder without a table changed her");
			helper.setBlock(table, ModBlocks.BLUEPRINT_TABLE);
		});
		helper.runAfterDelay(5, () -> {
			ChoiceMenu menu = jobsPage(helper, player, "Dara");
			int slot = -1;
			for (int s = VillageHallScreen.FIRST_ROW; s < ChoiceMenu.SIZE; s++) {
				if (menu.icon(s).is(ModBlocks.BLUEPRINT_TABLE.asItem())) {
					slot = s;
				}
			}
			helper.assertTrue(slot >= 0 && lore(menu.icon(slot)).contains("Retraining costs 5 emeralds (Novice)"), "the free table isn't listed with its price");
			menu.press(slot, player);
			menu.press(slot, player);
			helper.assertTrue(dara.getVillagerData().getProfession() == ModVillagers.BUILDER && site(dara).equals(Optional.of(helper.absolutePos(table))),
				"not the Builder at the table: " + dara.getVillagerData().getProfession() + " " + site(dara));
			helper.succeed();
		});
	}

	/**
	 * With workplaceRetraining off, a worker's click only makes them glow: no page, no Change job. Children and nitwits
	 * are never offered it with the rule on either.
	 */
	//$ gametest_ticks_batch AREA '100' '"hallRetrainOff"'
	@GameTest(template = AREA, timeoutTicks = 100, batch = "hallRetrainOff")
	public void theRuleOffHidesTheButton(GameTestHelper helper) {
		village(helper);
		ServerLevel level = helper.getLevel();
		Villager dara = librarian(helper, 2, false);
		Villager child = helper.spawn(EntityType.VILLAGER, new BlockPos(8, 2, 16));
		child.setNoAi(true);
		child.setBaby(true);
		child.setVillagerData(child.getVillagerData().setProfession(VillagerProfession.LIBRARIAN));
		Villager nitwit = helper.spawn(EntityType.VILLAGER, new BlockPos(9, 2, 16));
		nitwit.setNoAi(true);
		nitwit.setCustomName(Component.literal("Nim"));
		nitwit.setVillagerData(nitwit.getVillagerData().setProfession(VillagerProfession.NITWIT));
		List<String> seen = new ArrayList<>();
		ServerPlayer player = payer(helper, seen, 64);
		helper.runAfterDelay(2, () -> {
			helper.assertTrue(!HallRetraining.canRetrain(level, child) && !HallRetraining.canRetrain(level, nitwit), "a child or a nitwit could be retrained");
			ChoiceMenu menu = VillageHallScreen.forTest(player, helper.absolutePos(HALL));
			rightClick(menu, resident(helper, menu, "Nim"), player);
			helper.assertTrue(!menu.icon(HallRetraining.CHANGE).is(Items.EMERALD), "a nitwit has a Change job button");
			level.getGameRules().getRule(ModGameRules.RETRAINING).set(false, level.getServer());
			try {
				menu = VillageHallScreen.forTest(player, helper.absolutePos(HALL));
				int slot = resident(helper, menu, "Dara");
				helper.assertTrue(lore(menu.icon(slot)).contains("Click: make them glow so you can find them"), "the list's click line: " + lore(menu.icon(slot)));
				helper.assertTrue(!lore(menu.icon(slot)).contains("Right-click: change their job"), "the list still offers a job change");
				menu.press(slot, player);
				helper.assertTrue(dara.hasEffect(MobEffects.GLOWING), "she doesn't glow");
				rightClick(menu, slot, player);
				helper.assertTrue(!menu.icon(HallRetraining.CHANGE).is(Items.EMERALD) && menu.icon(slot).is(Items.LECTERN), "the rule off still opens a Change job page");
				helper.assertTrue(dara.getVillagerData().getProfession() == VillagerProfession.LIBRARIAN && player.getInventory().countItem(Items.EMERALD) == 64,
					"something changed with the rule off");
			} finally {
				level.getGameRules().getRule(ModGameRules.RETRAINING).set(true, level.getServer());
			}
			helper.succeed();
		});
	}

	/** Retrained, saved and loaded: still the Novice Porter at the Storehouse, still on her way; an old save has no walk. */
	//$ gametest_ticks_batch AREA '100' '"hallRetrainSave"'
	@GameTest(template = AREA, timeoutTicks = 100, batch = "hallRetrainSave")
	public void aRetrainedVillagerSurvivesASave(GameTestHelper helper) {
		village(helper);
		BlockPos store = helper.absolutePos(STOREHOUSE);
		Villager dara = librarian(helper, 4, false);
		List<String> seen = new ArrayList<>();
		ServerPlayer player = payer(helper, seen, 35);
		helper.runAfterDelay(2, () -> {
			ChoiceMenu menu = jobsPage(helper, player, "Dara");
			menu.press(VillageHallScreen.FIRST_ROW, player);
			menu.press(VillageHallScreen.FIRST_ROW, player);
			helper.assertTrue(player.getInventory().countItem(Items.EMERALD) == 0, "an Expert's price isn't 35");
			Villager copy = EntityType.VILLAGER.create(helper.getLevel());
			copy.load(dara.saveWithoutId(new CompoundTag()));
			helper.assertTrue(copy.getVillagerData().getProfession() == ModVillagers.PORTER && copy.getVillagerData().getLevel() == 1,
				"loaded as " + copy.getVillagerData());
			helper.assertTrue(site(copy).equals(Optional.of(store)), "loaded working at " + site(copy));
			HallRetraining.Walk walk = ModAttachments.RETRAIN_WALK.get(copy);
			helper.assertTrue(walk != null && walk.equals(ModAttachments.RETRAIN_WALK.get(dara)) && walk.station().equals(store), "the walk lost in a reload: " + walk);
			helper.assertTrue(!ModAttachments.RETRAIN_WALK.has(EntityType.VILLAGER.create(helper.getLevel())), "an old save has a walk");
			copy.discard();
			helper.succeed();
		});
	}
}
