package io.github.jcondedata.aliveworkplace.devclient;

import io.github.jcondedata.aliveworkplace.hall.Chronicle;
import io.github.jcondedata.aliveworkplace.hall.FamilyPage;
import io.github.jcondedata.aliveworkplace.hall.VillageHallBlock;
import io.github.jcondedata.aliveworkplace.hall.VillageHallBlockEntity;
import io.github.jcondedata.aliveworkplace.hall.VillageHallScreen;
import io.github.jcondedata.aliveworkplace.people.Chatter;
import io.github.jcondedata.aliveworkplace.people.Couples;
import io.github.jcondedata.aliveworkplace.people.FamilyBook;
import io.github.jcondedata.aliveworkplace.people.Families;
import io.github.jcondedata.aliveworkplace.registry.ModAttachments;
import io.github.jcondedata.aliveworkplace.registry.ModBlocks;
import io.github.jcondedata.aliveworkplace.registry.ModVillagers;
import io.github.jcondedata.aliveworkplace.story.LifeStory;
import io.github.jcondedata.aliveworkplace.work.ChoiceMenu;
import java.util.List;
import net.minecraft.client.CloudStatus;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.BookViewScreen;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.npc.VillagerProfession;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.WrittenBookContent;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.GameType;

/**
 * SCENE=family_tree (ROADMAP 34.21): three generations by a Village Hall. Bram (who has died) and Ilse founded the
 * family; their daughter Dara married Tomas; their baby Mira is the family's third generation, which the chronicle
 * notes. The hall's list says "Dara (Ashby)" (still 1); Dara's page has a Family button, and her Family page shows her
 * parents, partner and child (still 2); Mira's shows her grandparents, Bram marked dead (still 3); Odo the Printer
 * prints the family's tree for 2 emeralds, and it opens as a book (still 4). Each step is checked from the game.
 */
final class FamilyTreeScene {
	private static final BlockPos HALL = new BlockPos(0, -60, -3);
	private Villager bram;
	private Villager ilse;
	private Villager dara;
	private Villager tomas;
	private Villager mira;
	private int tick;
	private int daraSlot = -1;

	void tick(Minecraft mc) {
		MinecraftServer server = mc.getSingleplayerServer();
		tick++;
		if (tick == 1) {
			mc.options.renderDistance().set(6);
			mc.options.cloudStatus().set(CloudStatus.OFF);
			mc.options.guiScale().set(2);
			mc.resizeDisplay();
			mc.options.hideGui = false;
		}
		if (tick == 20) {
			server.execute(() -> {
				ServerLevel level = server.overworld();
				level.getGameRules().getRule(GameRules.RULE_DAYLIGHT).set(false, server);
				level.getGameRules().getRule(GameRules.RULE_DOMOBSPAWNING).set(false, server);
				level.setDayTime(1000);
				Chatter.ENABLED = false;
			});
		}
		if (tick == 40) {
			server.execute(() -> stage(server));
		}
		if (tick == 60) {
			server.execute(() -> {
				VillageHallScreen.open(player(server), HALL);
				if (player(server).containerMenu instanceof ChoiceMenu m) {
					for (int s = VillageHallScreen.FIRST_PERSON; s < ChoiceMenu.SIZE; s++) {
						if (m.icon(s).getHoverName().getString().startsWith("Dara")) {
							daraSlot = s;
							Showcase.check(m.icon(s).getHoverName().getString().equals("Dara (" + family() + ")") && dara.getCustomName().getString().equals("Dara"),
								"the hall's list says \"Dara (" + family() + ")\", and her own name is still Dara");
						}
					}
				}
				Showcase.check(daraSlot >= 0, "Dara is on the hall's list");
			});
		}
		if (tick == 75) {
			ScreenshotHarness.pointAt(mc, daraSlot);
		}
		if (tick == 90) {
			ScreenshotHarness.shot(mc, "01_family_tree_hall");
		}
		if (tick == 100) {
			server.execute(() -> {
				ServerPlayer player = player(server);
				if (player.containerMenu instanceof ChoiceMenu m) {
					LifeStory.render(m, server.overworld(), HALL, dara, 0, player);
					Showcase.check(m.icon(FamilyPage.BUTTON).getHoverName().getString().equals("Family"), "Dara's page has a Family button");
					m.press(FamilyPage.BUTTON, player);
					m.broadcastChanges();
					Showcase.check(m.icon(FamilyPage.PARENTS + 1).getHoverName().getString().startsWith("Bram")
						&& m.icon(FamilyPage.PARTNER + 1).getHoverName().getString().startsWith("Tomas")
						&& m.icon(FamilyPage.CHILDREN + 1).getHoverName().getString().startsWith("Mira"),
						"Dara's Family page: her parents, Tomas, and Mira");
				}
			});
		}
		if (tick == 115) {
			ScreenshotHarness.pointAt(mc, FamilyPage.CHILDREN + 1);
		}
		if (tick == 130) {
			ScreenshotHarness.shot(mc, "02_family_tree_page");
		}
		if (tick == 140) {
			server.execute(() -> {
				ServerPlayer player = player(server);
				if (player.containerMenu instanceof ChoiceMenu m) {
					m.press(FamilyPage.CHILDREN + 1, player);
					m.broadcastChanges();
					ItemStack grandparent = m.icon(FamilyPage.GRANDPARENTS + 1);
					Showcase.check(grandparent.is(Items.SKELETON_SKULL) && grandparent.getHoverName().getString().equals("Bram (" + family() + ") †"),
						"Mira's grandparents, Bram marked dead: " + grandparent.getHoverName().getString());
				}
			});
		}
		if (tick == 155) {
			ScreenshotHarness.pointAt(mc, FamilyPage.GRANDPARENTS + 1);
		}
		if (tick == 170) {
			ScreenshotHarness.shot(mc, "03_family_tree_grandparents");
		}
		if (tick == 180) {
			server.execute(() -> {
				ServerPlayer player = player(server);
				if (player.containerMenu instanceof ChoiceMenu m) {
					m.press(FamilyPage.PRINT, player);
					Showcase.check(player.getInventory().countItem(Items.WRITTEN_BOOK) == 1 && player.getInventory().countItem(Items.EMERALD) == 0,
						"Odo the Printer printed the family's tree for 2 emeralds");
				}
				player.closeContainer();
			});
		}
		if (tick == 200) {
			ItemStack book = ItemStack.EMPTY;
			for (int s = 0; s < mc.player.getInventory().getContainerSize(); s++) {
				if (mc.player.getInventory().getItem(s).is(Items.WRITTEN_BOOK)) {
					book = mc.player.getInventory().getItem(s);
				}
			}
			if (!book.isEmpty()) {
				mc.setScreen(new BookViewScreen(BookViewScreen.BookAccess.fromItem(book)));
			}
		}
		if (tick == 215) {
			if (mc.screen instanceof BookViewScreen view) {
				view.setPage(1);
			}
		}
		if (tick == 230) {
			ScreenshotHarness.shot(mc, "04_family_tree_book");
			ItemStack held = ItemStack.EMPTY;
			for (int s = 0; s < mc.player.getInventory().getContainerSize(); s++) {
				if (mc.player.getInventory().getItem(s).is(Items.WRITTEN_BOOK)) {
					held = mc.player.getInventory().getItem(s);
				}
			}
			WrittenBookContent content = held.get(DataComponents.WRITTEN_BOOK_CONTENT);
			Showcase.check(mc.screen instanceof BookViewScreen && content != null && content.title().raw().equals("The " + family() + " Family")
				&& content.pages().size() > 1 && content.pages().get(1).raw().getString().startsWith("Bram " + family() + " †"),
				"the tree opens as a book: " + (content == null ? "none" : content.title().raw()));
		}
		if (tick == 250) {
			mc.stop();
		}
	}

	private String family() {
		return FamilyBook.familyName(FamilyBook.family(dara)).getString();
	}

	private static ServerPlayer player(MinecraftServer server) {
		return server.getPlayerList().getPlayers().get(0);
	}

	private void stage(MinecraftServer server) {
		ServerLevel level = server.overworld();
		ServerPlayer player = player(server);
		level.setBlockAndUpdate(HALL, ModBlocks.VILLAGE_HALL.defaultBlockState().setValue(VillageHallBlock.FACING, Direction.SOUTH));
		VillageHallBlockEntity hall = (VillageHallBlockEntity) level.getBlockEntity(HALL);
		hall.setOwner(player.getUUID(), player.getGameProfile().getName());
		long today = Chronicle.day(level);
		bram = villager(level, "Bram", -3.5, 2.5);
		ilse = villager(level, "Ilse", -2.5, 2.5);
		dara = villager(level, "Dara", -0.5, 0.5);
		tomas = villager(level, "Tomas", 0.5, 0.5);
		Villager odo = villager(level, "Odo", 3.5, 0.5);
		odo.setVillagerData(odo.getVillagerData().setProfession(ModVillagers.PRINTER).setLevel(2));
		odo.setVillagerXp(10);
		Families.born(dara, bram, ilse);
		ModAttachments.PARTNER.set(dara, new Couples.Partner(tomas.getUUID(), tomas.getDisplayName(), today, true));
		ModAttachments.PARTNER.set(tomas, new Couples.Partner(dara.getUUID(), dara.getDisplayName(), today, true));
		ModAttachments.PARTNER.set(bram, new Couples.Partner(ilse.getUUID(), ilse.getDisplayName(), today, true));
		ModAttachments.PARTNER.set(ilse, new Couples.Partner(bram.getUUID(), bram.getDisplayName(), today, true));
		FamilyBook.round(level, HALL, hall);
		mira = villager(level, "Mira", 0, 2);
		mira.setAge(-24000);
		Families.born(mira, dara, tomas);
		FamilyBook.round(level, HALL, hall);
		bram.kill(); // Bram has died: his grave by the hall, the cross in the book
		List<String> chronicle = hall.chronicle().stream().map(e -> e.text().getString()).toList();
		Showcase.check(chronicle.contains("The " + family() + " family has its third generation: Mira"), "the chronicle notes the third generation: " + chronicle);
		Showcase.check(FamilyBook.family(mira) == FamilyBook.family(bram) && hall.families().get(bram.getUUID()).dead(), "Mira took Dara's family; Bram's death is in the book");
		player.getInventory().clearContent();
		player.getInventory().add(new ItemStack(Items.EMERALD, 2));
		player.setGameMode(GameType.SURVIVAL);
		player.getAbilities().flying = false;
		player.onUpdateAbilities();
		player.teleportTo(level, 0.5, -60, 6.5, 180f, 8f);
	}

	private static Villager villager(ServerLevel level, String name, double x, double z) {
		Villager v = EntityType.VILLAGER.create(level);
		v.setVillagerData(v.getVillagerData().setProfession(VillagerProfession.NONE).setLevel(1));
		v.setCustomName(Component.literal(name));
		v.setNoAi(true);
		v.moveTo(x, -60, z, 0f, 0f);
		v.setYHeadRot(0f);
		v.setYBodyRot(0f);
		level.addFreshEntity(v);
		return v;
	}
}
