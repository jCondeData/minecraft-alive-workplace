package io.github.jcondedata.aliveworkplace.test;

import io.github.jcondedata.aliveworkplace.WorkplaceConfig;
import io.github.jcondedata.aliveworkplace.hall.Chronicle;
import io.github.jcondedata.aliveworkplace.hall.FamilyPage;
import io.github.jcondedata.aliveworkplace.hall.VillageGrowth;
import io.github.jcondedata.aliveworkplace.hall.VillageHallBlockEntity;
import io.github.jcondedata.aliveworkplace.hall.VillageHallScreen;
import io.github.jcondedata.aliveworkplace.hall.VillageHalls;
import io.github.jcondedata.aliveworkplace.hall.VillageNeeds;
import io.github.jcondedata.aliveworkplace.hall.VillageRanks;
import io.github.jcondedata.aliveworkplace.mc.Nbt;
import io.github.jcondedata.aliveworkplace.people.Couples;
import io.github.jcondedata.aliveworkplace.people.FamilyBook;
import io.github.jcondedata.aliveworkplace.people.Families;
import io.github.jcondedata.aliveworkplace.people.Moods;
import io.github.jcondedata.aliveworkplace.people.Names;
import io.github.jcondedata.aliveworkplace.printer.FamilyTree;
import io.github.jcondedata.aliveworkplace.registry.ModAttachments;
import io.github.jcondedata.aliveworkplace.registry.ModBlocks;
import io.github.jcondedata.aliveworkplace.registry.ModVillagers;
import io.github.jcondedata.aliveworkplace.story.LifeStory;
import io.github.jcondedata.aliveworkplace.work.ChoiceMenu;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponents;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.npc.VillagerProfession;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.ItemLore;
import net.minecraft.world.item.component.WrittenBookContent;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.properties.BedPart;

/**
 * Family trees (ROADMAP 34.21): three generations born through the village's own births (family names taken from a
 * parent, the birth's chronicle line naming the grandparents, the third generation noted once), the Family page from
 * a villager's page, a death marked in it, the Printer's tree for 2 emeralds; the Book of Families' 400 with the longest
 * dead forgotten first; a hall and a villager through a save; an old Parents record without ids; {@code familyNames}
 * off; and every new sentence a player reads.
 */
public class FamilyTreeGameTests implements FabricGameTest {
	private static final String AREA = "aliveworkplace_test:big_area";
	private static final BlockPos HALL = new BlockPos(11, 2, 11);
	private static final BlockPos CHEST = new BlockPos(17, 2, 19);

	/**
	 * Bram and Ilse's daughter Dara marries Tomas; their baby, born at the village's own birth, takes Dara's family name
	 * (Bram's), the chronicle names the grandparents and notes the family's third generation once. Dara's Family page
	 * (from her page in the hall) shows parents, partner and child; the baby's shows the grandparents. Bram dies and is
	 * marked; the Printer prints the family's tree for 2 emeralds (not without a Printer, not without the emeralds); the
	 * hall's book and a villager's family name and parent ids come through a save, and a hall saved before the book loads empty.
	 */
	//$ gametest_ticks_batch AREA '100' '"familyThreeGenerations"'
	@GameTest(template = AREA, timeoutTicks = 100, batch = "familyThreeGenerations")
	public void threeGenerationsADeathAndTheTree(GameTestHelper helper) {
		Leftovers.clear(helper);
		village(helper);
		helper.setBlock(new BlockPos(17, 2, 17), ModBlocks.STOREHOUSE);
		helper.setBlock(CHEST, Blocks.CHEST);
		helper.setBlock(new BlockPos(3, 2, 15), Blocks.RED_BED.defaultBlockState().setValue(BedBlock.FACING, Direction.SOUTH).setValue(BedBlock.PART, BedPart.FOOT));
		helper.setBlock(new BlockPos(3, 2, 16), Blocks.RED_BED.defaultBlockState().setValue(BedBlock.FACING, Direction.SOUTH).setValue(BedBlock.PART, BedPart.HEAD));
		Villager bram = villager(helper, new BlockPos(9, 2, 4), "Bram");
		Villager ilse = otherFamily(helper, new BlockPos(10, 2, 4), "Ilse", bram);
		Villager dara = villager(helper, new BlockPos(4, 2, 13), "Dara");
		Villager tomas = otherFamily(helper, new BlockPos(8, 2, 8), "Tomas", bram);
		helper.setBlock(HALL, ModBlocks.VILLAGE_HALL);
		ServerPlayer player = player(helper);
		boolean names = Names.ENABLED;
		Names.ENABLED = true; // (off in GameTests) the baby is named at birth
		Leftovers.after(helper, () -> Names.ENABLED = names);
		helper.runAfterDelay(5, () -> {
			ServerLevel level = helper.getLevel();
			BlockPos hall = helper.absolutePos(HALL);
			VillageHallBlockEntity entity = ready(helper);
			long today = Chronicle.day(level);
			Families.born(dara, bram, ilse); // Dara is Bram and Ilse's (grown up long since)
			ModAttachments.PARTNER.set(dara, new Couples.Partner(tomas.getUUID(), tomas.getDisplayName(), today, true));
			ModAttachments.PARTNER.set(tomas, new Couples.Partner(dara.getUUID(), dara.getDisplayName(), today, true));
			FamilyBook.round(level, hall, entity);
			int ashby = FamilyBook.family(bram);
			String a = FamilyBook.familyName(ashby).getString();
			String i = FamilyBook.familyName(FamilyBook.family(ilse)).getString();
			helper.assertTrue(ashby == FamilyBook.founded(bram.getUUID()) && FamilyBook.family(ilse) == FamilyBook.founded(ilse.getUUID()),
				"founders' families by UUID: " + ashby + ", " + FamilyBook.family(ilse));
			helper.assertTrue(FamilyBook.family(dara) == ashby, "Dara didn't take Bram's family: " + FamilyBook.family(dara));
			helper.assertTrue(name(level, hall, dara).equals("Dara (" + a + ")") && dara.getCustomName().getString().equals("Dara"),
				"the hall's name: " + name(level, hall, dara) + ", over her head: " + dara.getCustomName().getString());

			// The village's own birth: Dara (nearer the free bed) and Tomas.
			Container chest = helper.getBlockEntity(CHEST);
			chest.setItem(0, new ItemStack(Items.BREAD, 16));
			Villager baby = VillageGrowth.grow(level, hall, new VillageNeeds.Needs(2, 2, 2, 2, 2, 0, 0, 1f), 0);
			helper.assertTrue(baby != null && baby.isBaby(), "no baby: " + VillageGrowth.blocker(level, hall, new VillageNeeds.Needs(2, 2, 2, 2, 2, 0, 0, 1f), 0));
			baby.setNoAi(true);
			Families.Parents parents = Families.parents(baby);
			helper.assertTrue(parents != null && dara.getUUID().equals(parents.motherId()) && tomas.getUUID().equals(parents.fatherId()),
				"the baby's parents by id: " + parents);
			helper.assertTrue(FamilyBook.family(baby) == ashby, "the baby didn't take Dara's family: " + FamilyBook.family(baby));
			helper.assertTrue(baby.hasCustomName(), "the baby wasn't named at birth");
			String mira = baby.getName().getString();
			List<String> lines = chronicle(entity);
			String birth = mira + " was born to Dara and Tomas, grandchild of Bram " + a + " and Ilse " + i;
			helper.assertTrue(lines.contains(birth), "the birth line: " + lines);
			String third = "The " + a + " family has its third generation: " + mira;
			helper.assertTrue(entity.chronicle().stream().anyMatch(e -> e.kind() == Chronicle.Kind.LIFE && e.text().getString().equals(third)),
				"the third generation not noted: " + lines);
			FamilyBook.round(level, hall, entity);
			helper.assertTrue(!FamilyBook.noteGeneration(level, hall, entity.families(), baby)
				&& chronicle(entity).stream().filter(third::equals).count() == 1, "the third generation noted twice");
			FamilyBook.Person babyEntry = entity.families().get(baby.getUUID());
			helper.assertTrue(babyEntry != null && babyEntry.bornHere() && babyEntry.born() == today && FamilyBook.generation(entity.families(), babyEntry) == 3,
				"the baby's entry: " + babyEntry);

			// Dara's page has a Family button: parents, partner, child.
			ChoiceMenu menu = VillageHallScreen.forTest(player, hall);
			LifeStory.render(menu, level, hall, dara, 0, player);
			helper.assertTrue(menu.icon(FamilyPage.BUTTON).is(Items.FLOWERING_AZALEA) && menu.icon(FamilyPage.BUTTON).getHoverName().getString().equals("Family")
				&& lore(menu.icon(FamilyPage.BUTTON)).equals(List.of("Dara's parents, grandparents, partner, brothers and sisters, children and grandchildren")),
				"the Family button: " + menu.icon(FamilyPage.BUTTON) + " " + lore(menu.icon(FamilyPage.BUTTON)));
			menu.press(FamilyPage.BUTTON, player);
			helper.assertTrue(title(menu, FamilyPage.WHO).equals("The family of Dara (" + a + ")"), "the page's title: " + title(menu, FamilyPage.WHO));
			helper.assertTrue(lore(menu.icon(FamilyPage.WHO)).containsAll(List.of("† marks the dead", "Click anyone to see their family")), "the title's lore: " + lore(menu.icon(FamilyPage.WHO)));
			helper.assertTrue(title(menu, FamilyPage.PARENTS).equals("Parents") && title(menu, FamilyPage.PARENTS + 1).equals("Bram (" + a + ")")
				&& title(menu, FamilyPage.PARENTS + 2).equals("Ilse (" + i + ")"), "parents: " + title(menu, FamilyPage.PARENTS + 1) + ", " + title(menu, FamilyPage.PARENTS + 2));
			helper.assertTrue(title(menu, FamilyPage.PARTNER).equals("Partner") && title(menu, FamilyPage.PARTNER + 1).startsWith("Tomas ("), "partner: " + title(menu, FamilyPage.PARTNER + 1));
			helper.assertTrue(title(menu, FamilyPage.CHILDREN).equals("Children") && title(menu, FamilyPage.CHILDREN + 1).equals(mira + " (" + a + ")")
				&& menu.icon(FamilyPage.CHILDREN + 1).is(Items.EGG) && lore(menu.icon(FamilyPage.CHILDREN + 1)).contains("Born on day " + today)
				&& lore(menu.icon(FamilyPage.CHILDREN + 1)).contains("Click: their family"), "child: " + title(menu, FamilyPage.CHILDREN + 1) + " " + lore(menu.icon(FamilyPage.CHILDREN + 1)));
			helper.assertTrue(lore(menu.icon(FamilyPage.GRANDPARENTS)).equals(List.of("None known")) && title(menu, FamilyPage.GRANDCHILDREN).equals("Grandchildren")
				&& title(menu, FamilyPage.SIBLINGS).equals("Brothers and sisters"), "the empty groups");
			helper.assertTrue(menu.icon(FamilyPage.PRINT).is(Items.WRITABLE_BOOK) && lore(menu.icon(FamilyPage.PRINT)).equals(List.of("The village needs a Printer to print it"))
				&& title(menu, FamilyPage.PRINT).equals("Print the tree of the " + a + " family"), "no Printer: " + title(menu, FamilyPage.PRINT) + " " + lore(menu.icon(FamilyPage.PRINT)));
			player.getInventory().add(new ItemStack(Items.EMERALD, 2));
			menu.press(FamilyPage.PRINT, player);
			helper.assertTrue(player.getInventory().countItem(Items.WRITTEN_BOOK) == 0 && player.getInventory().countItem(Items.EMERALD) == 2, "printed without a Printer");

			// A death: Bram, dated in the book and marked on his granddaughter's page.
			bram.kill();
			FamilyBook.Person bramEntry = entity.families().get(bram.getUUID());
			helper.assertTrue(bramEntry != null && bramEntry.dead() && bramEntry.died() == today, "Bram's death not in the book: " + bramEntry);
			menu.press(FamilyPage.CHILDREN + 1, player); // the baby's own family
			helper.assertTrue(title(menu, FamilyPage.WHO).equals("The family of " + mira + " (" + a + ")"), "the baby's page: " + title(menu, FamilyPage.WHO));
			helper.assertTrue(title(menu, FamilyPage.GRANDPARENTS).equals("Grandparents") && title(menu, FamilyPage.GRANDPARENTS + 1).equals("Bram (" + a + ") †")
				&& menu.icon(FamilyPage.GRANDPARENTS + 1).is(Items.SKELETON_SKULL) && lore(menu.icon(FamilyPage.GRANDPARENTS + 1)).contains("Died on day " + today)
				&& title(menu, FamilyPage.GRANDPARENTS + 2).equals("Ilse (" + i + ")") && menu.icon(FamilyPage.GRANDPARENTS + 2).is(Items.NAME_TAG),
				"grandparents: " + title(menu, FamilyPage.GRANDPARENTS + 1) + " " + lore(menu.icon(FamilyPage.GRANDPARENTS + 1)) + ", " + title(menu, FamilyPage.GRANDPARENTS + 2));
			helper.assertTrue(title(menu, FamilyPage.PARENTS + 1).equals("Dara (" + a + ")") && title(menu, FamilyPage.PARENTS + 2).startsWith("Tomas ("), "the baby's parents");

			// The Printer's tree: 2 emeralds, not without them.
			Villager printer = villager(helper, new BlockPos(14, 2, 6), "Odo");
			printer.setVillagerData(printer.getVillagerData().setProfession(ModVillagers.PRINTER).setLevel(1));
			printer.setVillagerXp(1);
			menu.press(FamilyPage.PARENTS + 1, player); // back to Dara's
			helper.assertTrue(menu.icon(FamilyPage.PRINT).is(Items.WRITTEN_BOOK) && lore(menu.icon(FamilyPage.PRINT)).equals(List.of("Odo prints it for 2 emeralds")),
				"the print button: " + lore(menu.icon(FamilyPage.PRINT)));
			player.getInventory().clearContent();
			player.getInventory().add(new ItemStack(Items.EMERALD, 1));
			menu.press(FamilyPage.PRINT, player);
			helper.assertTrue(player.getInventory().countItem(Items.WRITTEN_BOOK) == 0 && player.getInventory().countItem(Items.EMERALD) == 1, "printed for 1 emerald");
			player.getInventory().add(new ItemStack(Items.EMERALD, 2));
			menu.press(FamilyPage.PRINT, player);
			helper.assertTrue(player.getInventory().countItem(Items.EMERALD) == 1 && player.getInventory().countItem(Items.WRITTEN_BOOK) == 1, "not paid 2 emeralds for one book");
			ItemStack tree = find(player, Items.WRITTEN_BOOK);
			WrittenBookContent content = tree.get(DataComponents.WRITTEN_BOOK_CONTENT);
			helper.assertTrue(content != null && content.title().raw().equals("The " + a + " Family") && content.author().equals("Odo"), "the book: " + content);
			List<String> pages = content.pages().stream().map(p -> p.raw().getString()).toList();
			String village = VillageHalls.name(level, hall).getString();
			// (Bram, Dara and the baby; Ilse or Tomas too in the rare case their UUID founds the same family)
			int people = FamilyBook.members(entity.families(), ashby).size();
			helper.assertTrue(pages.get(0).equals("The " + a + " Family\n\nof " + village + "\nprinted on day " + today + "\n\n" + people + " people\n3 generations\n\n† marks the dead"),
				"the cover: " + pages.get(0));
			String all = String.join("\n\n", pages.subList(1, pages.size()));
			helper.assertTrue(people >= 3 && pages.size() == 1 + (people + 1) / FamilyTree.PER_PAGE
				&& pages.get(1).startsWith((people == 3 ? "Bram " + a + " †\nGeneration 1\nDay " + today + " to day " + today : ""))
				&& all.contains("Bram " + a + " †\nGeneration 1\nDay " + today + " to day " + today)
				&& all.contains("Dara " + a + "\nGeneration 2\nChild of Bram and Ilse\nPartner: Tomas ")
				&& all.contains(mira + " " + a + "\nGeneration 3\nChild of Dara and Tomas\nSince day " + today), "the pages: " + pages);

			// Through a save: the hall's book (and its note), a villager's family and parents' ids; an older hall loads empty.
			List<FamilyBook.Person> before = List.copyOf(entity.families().people());
			CompoundTag saved = entity.saveWithFullMetadata(level.registryAccess());
			entity.loadWithComponents(saved, level.registryAccess());
			helper.assertTrue(List.copyOf(entity.families().people()).equals(before) && entity.families().noted(ashby), "the book changed in a save: "
				+ entity.families().people());
			Villager copy = reload(level, baby);
			helper.assertTrue(FamilyBook.family(copy) == ashby && Families.parents(copy) != null && dara.getUUID().equals(Families.parents(copy).motherId())
				&& tomas.getUUID().equals(Families.parents(copy).fatherId()), "the baby's family or parents lost in a save");
			copy.discard();
			CompoundTag old = saved.copy();
			old.remove("families");
			old.remove("familiesNoted");
			entity.loadWithComponents(old, level.registryAccess());
			helper.assertTrue(entity.families().size() == 0 && !entity.families().noted(ashby), "a hall from before the book loaded with one");
			helper.succeed();
		});
	}

	/** Up to 400 people: over that, the longest dead are forgotten first, never the living; the book comes through a save. */
	//$ gametest 'FabricGameTest.EMPTY_STRUCTURE'
	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
	public void theBookKeepsFourHundredTheLongestDeadForgottenFirst(GameTestHelper helper) {
		FamilyBook.Book book = new FamilyBook.Book();
		List<UUID> living = new ArrayList<>();
		List<UUID> dead = new ArrayList<>();
		for (int n = 0; n < 150; n++) {
			UUID id = UUID.randomUUID();
			living.add(id);
			book.put(new FamilyBook.Person(id, Component.literal("L" + n), 1, n, false, -1, null, null, null, "minecraft:farmer"));
		}
		for (int n = 0; n < 300; n++) {
			UUID id = UUID.randomUUID();
			dead.add(id);
			// written in newest-dead first, so the forgotten aren't simply the first written in
			book.put(new FamilyBook.Person(id, Component.literal("D" + n), 2, 1, false, 900 - n, living.get(0), null, null, ""));
		}
		helper.assertTrue(book.size() == FamilyBook.MAX && FamilyBook.MAX == 400, "the book's size: " + book.size());
		helper.assertTrue(living.stream().allMatch(id -> book.get(id) != null), "someone living was forgotten");
		helper.assertTrue(dead.subList(250, 300).stream().allMatch(id -> book.get(id) == null), "the longest dead are still there");
		helper.assertTrue(dead.subList(0, 250).stream().allMatch(id -> book.get(id) != null), "the wrong dead were forgotten");
		CompoundTag tag = new CompoundTag();
		book.save(tag, helper.getLevel().registryAccess());
		FamilyBook.Book loaded = new FamilyBook.Book();
		loaded.load(tag, helper.getLevel().registryAccess());
		helper.assertTrue(List.copyOf(loaded.people()).equals(List.copyOf(book.people())), "the book changed in a save");
		CompoundTag firstSaved = Nbt.compoundAt(Nbt.getList(tag, "families", Tag.TAG_COMPOUND), 0);
		helper.assertTrue(firstSaved.contains("trade") && !firstSaved.contains("died"), "a living person saved with a death day: " + firstSaved);
		// Nobody dead: the first written in goes.
		FamilyBook.Book full = new FamilyBook.Book();
		UUID first = UUID.randomUUID();
		full.put(new FamilyBook.Person(first, Component.literal("First"), 1, 0, false, -1, null, null, null, ""));
		for (int n = 0; n < 400; n++) {
			full.put(new FamilyBook.Person(UUID.randomUUID(), Component.literal("P" + n), 1, n, false, -1, null, null, null, ""));
		}
		helper.assertTrue(full.size() == 400 && full.get(first) == null, "the first written in kept: " + full.size());
		helper.succeed();
	}

	/**
	 * A child whose Parents record is from before the ids (names only) loads without ids, takes the family of a villager
	 * living here by the first parent's name, and their Family page lists the parents by name only; the hall still says
	 * whose child they are.
	 */
	//$ gametest_ticks_batch AREA '100' '"familyOldRecord"'
	@GameTest(template = AREA, timeoutTicks = 100, batch = "familyOldRecord")
	public void anOldParentsRecordWithoutIds(GameTestHelper helper) {
		Leftovers.clear(helper);
		village(helper);
		Villager mira = villager(helper, new BlockPos(5, 2, 5), "Mira");
		villager(helper, new BlockPos(7, 2, 5), "Tom");
		Villager pip = villager(helper, new BlockPos(9, 2, 5), "Pip");
		helper.setBlock(HALL, ModBlocks.VILLAGE_HALL);
		ServerPlayer player = player(helper);
		helper.runAfterDelay(5, () -> {
			ServerLevel level = helper.getLevel();
			BlockPos hall = helper.absolutePos(HALL);
			VillageHallBlockEntity entity = ready(helper);
			// What a save from before 34.21 holds: the names only.
			Tag saved = Families.Parents.CODEC.encodeStart(NbtOps.INSTANCE,
				new Families.Parents(Component.literal("Mira"), Component.literal("Tom"), "", "minecraft:farmer", true)).result().orElseThrow();
			helper.assertTrue(saved instanceof CompoundTag c && !c.contains("mother_id") && !c.contains("father_id"), "ids written for an old record: " + saved);
			Families.Parents old = Families.Parents.CODEC.parse(NbtOps.INSTANCE, saved).result().orElseThrow();
			helper.assertTrue(old.motherId() == null && old.fatherId() == null && old.mother().getString().equals("Mira") && old.grownUp(), "the old record: " + old);
			ModAttachments.PARENTS.set(pip, old);
			ModAttachments.FAMILY_NAME.remove(pip);
			ModAttachments.FAMILY_NAME.remove(mira);
			FamilyBook.round(level, hall, entity);
			helper.assertTrue(FamilyBook.family(mira) == FamilyBook.founded(mira.getUUID()) && FamilyBook.family(pip) == FamilyBook.family(mira),
				"Pip didn't take Mira's family: " + FamilyBook.family(pip) + ", Mira's " + FamilyBook.family(mira));
			FamilyBook.Person entry = entity.families().get(pip.getUUID());
			helper.assertTrue(entry != null && entry.mother() == null && entry.father() == null && !entry.bornHere(), "Pip's entry: " + entry);
			String m = FamilyBook.familyName(FamilyBook.family(mira)).getString();
			helper.assertTrue(name(level, hall, pip).equals("Pip (" + m + ")") && lore(VillageHallScreen.person(level, hall, pip)).contains("Child of Mira and Tom"),
				"Pip on the hall's list: " + name(level, hall, pip));
			ChoiceMenu menu = VillageHallScreen.forTest(player, hall);
			LifeStory.render(menu, level, hall, pip, 0, player);
			menu.press(FamilyPage.BUTTON, player);
			helper.assertTrue(menu.icon(FamilyPage.PARENTS + 1).is(Items.PAPER) && title(menu, FamilyPage.PARENTS + 1).equals("Mira")
				&& title(menu, FamilyPage.PARENTS + 2).equals("Tom")
				&& lore(menu.icon(FamilyPage.PARENTS + 1)).equals(List.of("Known by name only: from before the Book of Families")),
				"the parents by name: " + title(menu, FamilyPage.PARENTS + 1) + " " + lore(menu.icon(FamilyPage.PARENTS + 1)));
			menu.press(FamilyPage.PARENTS + 1, player); // a name, not a page
			helper.assertTrue(title(menu, FamilyPage.WHO).equals("The family of Pip (" + m + ")"), "a name-only parent opened a page");
			helper.succeed();
		});
	}

	/**
	 * {@code familyNames} off: the hall says "Dara", the chronicle's lines and the printed tree go by first names ("Bram's
	 * family"), and the family names are still kept for when it's on again.
	 */
	//$ gametest_ticks_batch AREA '100' '"familyNamesOff"'
	@GameTest(template = AREA, timeoutTicks = 100, batch = "familyNamesOff")
	public void familyNamesOffGoesByFirstNames(GameTestHelper helper) {
		Leftovers.clear(helper);
		village(helper);
		Villager bram = villager(helper, new BlockPos(5, 2, 5), "Bram");
		Villager ilse = otherFamily(helper, new BlockPos(6, 2, 5), "Ilse", bram); // so Bram alone founds the line
		Villager dara = villager(helper, new BlockPos(7, 2, 5), "Dara");
		Villager tomas = villager(helper, new BlockPos(8, 2, 5), "Tomas");
		Villager printer = villager(helper, new BlockPos(9, 2, 7), "Odo");
		printer.setVillagerData(printer.getVillagerData().setProfession(ModVillagers.PRINTER).setLevel(1));
		helper.setBlock(HALL, ModBlocks.VILLAGE_HALL);
		WorkplaceConfig config = new WorkplaceConfig();
		config.familyNames = false;
		config.apply();
		Leftovers.after(helper, () -> new WorkplaceConfig().apply());
		helper.runAfterDelay(5, () -> {
			ServerLevel level = helper.getLevel();
			BlockPos hall = helper.absolutePos(HALL);
			VillageHallBlockEntity entity = ready(helper);
			helper.assertTrue(!FamilyBook.NAMES && FamilyBook.ENABLED, "the switch didn't reach FamilyBook");
			Families.born(dara, bram, ilse);
			Villager baby = villager(helper, new BlockPos(7, 2, 7), "Mira");
			baby.setAge(-24000);
			Families.born(baby, dara, tomas);
			FamilyBook.round(level, hall, entity);
			helper.assertTrue(FamilyBook.family(dara) > 0 && FamilyBook.family(baby) == FamilyBook.family(bram), "family names not kept while hidden");
			helper.assertTrue(name(level, hall, dara).equals("Dara"), "the hall's name: " + name(level, hall, dara));
			helper.assertTrue(chronicle(entity).contains("Bram's family has its third generation: Mira"), "the third generation: " + chronicle(entity));
			helper.assertTrue(FamilyBook.birthLine(level, hall, baby, dara, tomas).getString().equals("Mira was born to Dara and Tomas, grandchild of Bram and Ilse"),
				"the birth line: " + FamilyBook.birthLine(level, hall, baby, dara, tomas).getString());
			FamilyBook.Person me = entity.families().get(baby.getUUID());
			ItemStack tree = FamilyTree.print(level, hall, entity.families(), me, printer);
			WrittenBookContent content = tree.get(DataComponents.WRITTEN_BOOK_CONTENT);
			List<String> pages = content.pages().stream().map(p -> p.raw().getString()).toList();
			helper.assertTrue(content.title().raw().equals("Bram's Family") && pages.get(0).startsWith("Bram's Family\n\nof ")
				&& pages.get(1).startsWith("Bram\nGeneration 1"), "the tree: " + content.title().raw() + " " + pages);
			ServerPlayer player = player(helper);
			ChoiceMenu menu = VillageHallScreen.forTest(player, hall);
			LifeStory.render(menu, level, hall, dara, 0, player);
			menu.press(FamilyPage.BUTTON, player);
			helper.assertTrue(title(menu, FamilyPage.WHO).equals("The family of Dara") && title(menu, FamilyPage.PRINT).equals("Print the tree of Bram's family")
				&& title(menu, FamilyPage.CHILDREN + 1).equals("Mira"), "the page: " + title(menu, FamilyPage.WHO) + ", " + title(menu, FamilyPage.PRINT));
			helper.succeed();
		});
	}

	/** Every new sentence a player reads, in English, and the 60 family names: all there, all different. */
	//$ gametest 'FabricGameTest.EMPTY_STRUCTURE'
	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
	public void everyNewSentenceReads(GameTestHelper helper) {
		Map<String, String> texts = new java.util.LinkedHashMap<>();
		texts.put(t("screen.aliveworkplace.hall.family_name", "Dara", "Ashby"), "Dara (Ashby)");
		texts.put(t("family.aliveworkplace.full_name", "Bram", "Ashby"), "Bram Ashby");
		texts.put(t("family.aliveworkplace.the_family", "Ashby"), "the Ashby family");
		texts.put(t("family.aliveworkplace.founders_family", "Bram"), "Bram's family");
		texts.put(t("family.aliveworkplace.book_title", "Ashby"), "The Ashby Family");
		texts.put(t("family.aliveworkplace.book_title_founder", "Bram"), "Bram's Family");
		texts.put(t("screen.aliveworkplace.family.button"), "Family");
		texts.put(t("screen.aliveworkplace.family.not_in_book"), "Not in the Book of Families yet");
		texts.put(t("screen.aliveworkplace.family.parents"), "Parents");
		texts.put(t("screen.aliveworkplace.family.partner"), "Partner");
		texts.put(t("screen.aliveworkplace.family.children"), "Children");
		texts.put(t("screen.aliveworkplace.family.more", 3), "and 3 more");
		texts.put(t("screen.aliveworkplace.family.grown", 12), "Grown up by day 12");
		texts.put(t("screen.aliveworkplace.family.print_by", "Odo", "2 emeralds"), "Odo prints it for 2 emeralds");
		texts.put(t("message.aliveworkplace.family.no_printer"), "The village has no Printer to print a family's tree");
		texts.put(t("message.aliveworkplace.family.cant_pay", "2 emeralds"), "A family's tree costs 2 emeralds");
		texts.put(t("message.aliveworkplace.family.printed", "Odo", "the Ashby family"), "Odo printed the tree of the Ashby family");
		texts.put(t("chronicle.aliveworkplace.birth_grandchild_unnamed", "Dara", "Tomas", "Bram Ashby"), "A baby was born to Dara and Tomas, grandchild of Bram Ashby");
		texts.put(t("chronicle.aliveworkplace.family.a_baby"), "a baby");
		texts.put(t("book.aliveworkplace.family_tree.generation_trade", 2, "Farmer"), "Generation 2 · Farmer");
		texts.put(t("book.aliveworkplace.family_tree.people.one", 1), "1 person");
		texts.put(t("book.aliveworkplace.family_tree.generations.one", 1), "1 generation");
		texts.put(t("aliveworkplace.config.familyNames"), "Family Names");
		for (Map.Entry<String, String> e : texts.entrySet()) {
			helper.assertTrue(e.getKey().equals(e.getValue()), "reads \"" + e.getKey() + "\", not \"" + e.getValue() + "\"");
		}
		helper.assertTrue(t("aliveworkplace.config.familyNames.tooltip").startsWith("Every villager in a village with a hall has a family name"), "the tooltip");
		Set<String> names = new HashSet<>();
		for (int n = 1; n <= FamilyBook.NAMES_COUNT; n++) {
			String name = FamilyBook.familyName(n).getString();
			helper.assertTrue(!name.startsWith("family_name.") && !name.isBlank(), "family name " + n + " missing");
			names.add(name);
		}
		helper.assertTrue(names.size() == 60 && FamilyBook.familyName(1).getString().equals("Ashby"), "60 different names: " + names.size());
		helper.assertTrue(FamilyBook.founded(UUID.fromString("0f1e2d3c-4b5a-6978-8796-a5b4c3d2e1f0")) >= 1
			&& FamilyBook.founded(new UUID(Long.MIN_VALUE, 7)) <= 60, "a founded family out of range");
		helper.succeed();
	}

	private static String t(String key, Object... args) {
		return Component.translatable(key, args).getString();
	}

	private static void village(GameTestHelper helper) {
		int radius = VillageHalls.RADIUS;
		VillageHalls.RADIUS = 16;
		Leftovers.after(helper, () -> {
			VillageHalls.RADIUS = radius;
			VillageNeeds.forget();
			Moods.forget();
		});
	}

	private static VillageHallBlockEntity ready(GameTestHelper helper) {
		VillageHallBlockEntity hall = (VillageHallBlockEntity) helper.getLevel().getBlockEntity(helper.absolutePos(HALL));
		hall.setRank(VillageRanks.Rank.HAMLET);
		hall.setNeeds(new VillageNeeds.Needs(2, 2, 2, 2, 2, 0, 0, 1f));
		VillageNeeds.forget();
		Moods.forget();
		return hall;
	}

	private static Villager villager(GameTestHelper helper, BlockPos pos, String name) {
		Villager v = helper.spawn(EntityType.VILLAGER, pos);
		v.setNoAi(true);
		v.setCustomName(Component.literal(name));
		v.setVillagerData(v.getVillagerData().setProfession(VillagerProfession.NONE).setLevel(1));
		return v;
	}

	/** A villager whose UUID founds another family than {@code other}'s (1 in 60 would found the same; spawned again then). */
	private static Villager otherFamily(GameTestHelper helper, BlockPos pos, String name, Villager other) {
		Villager v = villager(helper, pos, name);
		while (FamilyBook.founded(v.getUUID()) == FamilyBook.founded(other.getUUID())) {
			v.discard();
			v = villager(helper, pos, name);
		}
		return v;
	}

	private static ServerPlayer player(GameTestHelper helper) {
		ServerPlayer player = helper.makeMockServerPlayerInLevel();
		player.setGameMode(GameType.SURVIVAL);
		Leftovers.after(helper, () -> helper.getLevel().getServer().getPlayerList().remove(player));
		return player;
	}

	private static Villager reload(ServerLevel level, Villager villager) {
		CompoundTag tag = new CompoundTag();
		villager.saveWithoutId(tag);
		Villager copy = EntityType.VILLAGER.create(level);
		copy.load(tag);
		copy.setUUID(UUID.randomUUID());
		return copy;
	}

	private static ItemStack find(ServerPlayer player, net.minecraft.world.item.Item item) {
		for (int s = 0; s < player.getInventory().getContainerSize(); s++) {
			if (player.getInventory().getItem(s).is(item)) {
				return player.getInventory().getItem(s);
			}
		}
		return ItemStack.EMPTY;
	}

	private static String name(ServerLevel level, BlockPos hall, Villager villager) {
		return VillageHallScreen.person(level, hall, villager).getHoverName().getString();
	}

	private static String title(ChoiceMenu menu, int slot) {
		return menu.icon(slot).getHoverName().getString();
	}

	private static List<String> lore(ItemStack icon) {
		ItemLore lore = icon.get(DataComponents.LORE);
		return lore == null ? List.of() : lore.lines().stream().map(Component::getString).toList();
	}

	private static List<String> chronicle(VillageHallBlockEntity hall) {
		return hall.chronicle().stream().map(e -> e.text().getString()).toList();
	}

	// --- B549: the village is the circle ------------------------------------------------------------------------

	/**
	 * A villager (no AI) standing in a corner of the square box round {@code hall} ({@link VillageHalls#area}), one block
	 * in from its edge on both axes ({@code sx} and {@code sz} pick the corner): outside the village's circle.
	 */
	static Villager outsideVillage(GameTestHelper helper, BlockPos hall, int sx, int sz, String name) {
		BlockPos corner = hall.offset(sx * (VillageHalls.RADIUS - 1), 0, sz * (VillageHalls.RADIUS - 1));
		helper.assertTrue(VillageHalls.area(hall).contains(corner.getCenter()) && !VillageHalls.contains(hall, corner), "the corner isn't outside the circle");
		Villager v = EntityType.VILLAGER.create(helper.getLevel());
		v.moveTo(corner.getX() + 0.5, corner.getY(), corner.getZ() + 0.5, 0, 0);
		v.setNoAi(true);
		v.setCustomName(Component.literal(name));
		helper.getLevel().addFreshEntity(v);
		return v;
	}

	/** B549: a villager in the square's corner, outside the village's circle, isn't written into its hall's family book. */
	//$ gametest_ticks_batch AREA '100' '"familyCorner"'
	@GameTest(template = AREA, timeoutTicks = 100, batch = "familyCorner")
	public void theFamilyBookLeavesOutTheSquaresCorner(GameTestHelper helper) {
		Leftovers.clear(helper);
		village(helper);
		helper.setBlock(HALL, ModBlocks.VILLAGE_HALL);
		Villager ada = villager(helper, new BlockPos(9, 2, 4), "Ada");
		BlockPos hall = helper.absolutePos(HALL);
		Villager far = outsideVillage(helper, hall, 1, 1, "Far");
		VillageHallBlockEntity entity = ready(helper);
		FamilyBook.round(helper.getLevel(), hall, entity);
		helper.assertTrue(entity.families().get(ada.getUUID()) != null, "Ada, in the village, isn't in the book");
		helper.assertTrue(entity.families().get(far.getUUID()) == null && FamilyBook.family(far) == 0,
			"a villager outside the village's circle was written into its book");
		far.discard();
		ada.discard();
		helper.succeed();
	}

	/**
	 * B549: a married villager whose partner stands in the square's corner, outside the village's circle, isn't the
	 * village's couple (the next couple is), and a courting pair out in the corners isn't counted as courting.
	 */
	//$ gametest_ticks_batch AREA '100' '"coupleCorner"'
	@GameTest(template = AREA, timeoutTicks = 100, batch = "coupleCorner")
	public void couplesLeaveOutTheSquaresCorners(GameTestHelper helper) {
		Leftovers.clear(helper);
		village(helper);
		helper.setBlock(HALL, ModBlocks.VILLAGE_HALL);
		ServerLevel level = helper.getLevel();
		BlockPos hall = helper.absolutePos(HALL);
		long today = Chronicle.day(level);
		Villager ada = villager(helper, new BlockPos(9, 2, 4), "Ada");
		Villager bo = outsideVillage(helper, hall, 1, 1, "Bo");
		Villager cy = villager(helper, new BlockPos(12, 2, 8), "Cy");
		Villager di = villager(helper, new BlockPos(14, 2, 8), "Di");
		Villager eli = villager(helper, new BlockPos(6, 2, 10), "Eli");
		Villager fay = villager(helper, new BlockPos(6, 2, 12), "Fay");
		Villager gus = outsideVillage(helper, hall, -1, 1, "Gus");
		Villager hal = outsideVillage(helper, hall, 1, -1, "Hal");
		for (Villager[] pair : List.of(new Villager[] {ada, bo}, new Villager[] {cy, di}, new Villager[] {eli, fay}, new Villager[] {gus, hal})) {
			boolean married = pair[0] != eli && pair[0] != gus;
			ModAttachments.PARTNER.set(pair[0], new Couples.Partner(pair[1].getUUID(), pair[1].getDisplayName(), today, married));
			ModAttachments.PARTNER.set(pair[1], new Couples.Partner(pair[0].getUUID(), pair[0].getDisplayName(), today, married));
		}
		List<Villager> couple = Couples.coupleNear(level, hall, ada.blockPosition());
		helper.assertTrue(couple.size() == 2 && couple.containsAll(List.of(cy, di)),
			"the couple nearest Ada isn't Cy and Di (Bo is outside the village): " + couple.stream().map(v -> v.getName().getString()).toList());
		helper.assertTrue(Couples.courting(level, hall) == 1, "courting couples in the village: " + Couples.courting(level, hall) + ", not 1");
		List.of(ada, bo, cy, di, eli, fay, gus, hal).forEach(Villager::discard);
		helper.succeed();
	}
}
