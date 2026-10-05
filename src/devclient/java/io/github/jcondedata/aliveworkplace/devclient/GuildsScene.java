package io.github.jcondedata.aliveworkplace.devclient;

import io.github.jcondedata.aliveworkplace.AliveWorkplace;
import io.github.jcondedata.aliveworkplace.blueprint.BlueprintData;
import io.github.jcondedata.aliveworkplace.blueprint.StarterBlueprints;
import io.github.jcondedata.aliveworkplace.build.BuildSiteManager;
import io.github.jcondedata.aliveworkplace.hall.EdictBook;
import io.github.jcondedata.aliveworkplace.hall.Guilds;
import io.github.jcondedata.aliveworkplace.hall.VillageHallBlock;
import io.github.jcondedata.aliveworkplace.hall.VillageHallBlockEntity;
import io.github.jcondedata.aliveworkplace.hall.VillageHallScreen;
import io.github.jcondedata.aliveworkplace.hall.VillageRanks;
import io.github.jcondedata.aliveworkplace.registry.ModBlocks;
import io.github.jcondedata.aliveworkplace.registry.ModItems;
import io.github.jcondedata.aliveworkplace.registry.ModVillagers;
import io.github.jcondedata.aliveworkplace.work.ChoiceMenu;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.CloudStatus;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.npc.VillagerProfession;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.ItemLore;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings;
import net.minecraft.world.phys.Vec3;

/**
 * SCENE=guilds (ROADMAP 30.18, 30.19, 30.20): a City (four guilds a rank here) with ten finished Guildhalls; the player
 * charters a Master miner, weaponsmith, lumberjack, farmer, shepherd, scholar, nurse, innkeeper and guard (and, with
 * Cobblemon, a move tutor), and the Miners', Smiths', Woodsmen's, Harvest, Herders', Scholars', Healers', Merchants' and
 * Wardens' (and Trainers') Guilds are founded in them. Stills: each Guild Master's card on the hall's list (Guild Master
 * of their guild, and 15% faster through it for the guilds that make their trades faster), then the guilds' row of the
 * Book of Edicts, page by page (B78: more guilds than the row holds page five at a time). Its checks: all chartered and
 * founded, each card says so, the 30.19 and 30.20 numbers, and every guild on one of the Book's two guild pages.
 */
final class GuildsScene {
	private static final BlockPos HALL = new BlockPos(30, -60, 17);
	private static final BlockPos[] HALLS = {new BlockPos(0, -60, 0), new BlockPos(22, -60, 0), new BlockPos(44, -60, 0),
		new BlockPos(0, -60, 20), new BlockPos(22, -60, 20), new BlockPos(44, -60, 20), new BlockPos(0, -60, 40), new BlockPos(22, -60, 40),
		new BlockPos(44, -60, 40), new BlockPos(66, -60, 20)};
	private static final String[] NAMES = {"Brokk", "Hilde", "Rowan", "Wren", "Ebba", "Odo", "Mira", "Mara", "Wulf", "Tova"};
	private static final String[] GUILDS = {"Miners' Guild", "Smiths' Guild", "Woodsmen's Guild", "Harvest Guild", "Herders' Guild", "Scholars' Guild",
		"Healers' Guild", "Merchants' Guild", "Wardens' Guild", "Trainers' Guild"};
	private static final String[] IDS = {"miners", "smiths", "woodsmen", "harvest", "herders", "scholars", "healers", "merchants", "wardens", "trainers"};
	/** Whether the guild makes its trades work faster (the Wardens' and Trainers' don't). */
	private static final boolean[] PACE = {true, true, true, true, true, true, true, true, false, false};
	private static final String[] SHOTS = {"01_guilds_miner", "02_guilds_smith", "03_guilds_woodsman", "04_guilds_farmer", "05_guilds_shepherd",
		"06_guilds_scholar", "08_guilds_nurse", "09_guilds_innkeeper", "10_guilds_guard", "11_guilds_tutor"};
	private int tick;
	/** Nine guilds, ten with Cobblemon (the Trainers' Guild loads only with it); counted when the scene starts. */
	private int count = NAMES.length - 1;
	private final List<Villager> masters = new ArrayList<>();
	private final int[] slots = {-1, -1, -1, -1, -1, -1, -1, -1, -1, -1};

	void tick(Minecraft mc) {
		MinecraftServer server = mc.getSingleplayerServer();
		tick++;
		if (tick == 1) {
			count = Guilds.get(AliveWorkplace.id("trainers")) != null ? NAMES.length : NAMES.length - 1;
			mc.options.renderDistance().set(6);
			mc.options.cloudStatus().set(CloudStatus.OFF);
			mc.options.guiScale().set(2);
			mc.resizeDisplay();
			mc.options.hideGui = false;
		}
		if (tick == 20) {
			server.execute(() -> stage(server));
		}
		if (tick == 40) {
			server.execute(() -> charter(server));
		}
		if (tick == 70) {
			server.execute(() -> VillageHallScreen.open(player(server), HALL));
		}
		if (tick == 85) {
			server.execute(() -> findCards(server));
		}
		for (int i = 0; i < count; i++) {
			int at = 100 + i * 30;
			if (tick == at) {
				ScreenshotHarness.pointAt(mc, slots[i]);
			}
			if (tick == at + 15) {
				ScreenshotHarness.shot(mc, SHOTS[i]);
			}
		}
		int book = 100 + count * 30;
		if (tick == book) {
			mc.setScreen(null);
			server.execute(() -> EdictBook.open(player(server), HALL));
		}
		if (tick == book + 20) {
			ScreenshotHarness.pointAt(mc, EdictBook.FIRST_GUILD + 4, 6);
		}
		if (tick == book + 35) {
			server.execute(() -> Showcase.check(player(server).containerMenu instanceof ChoiceMenu m
					&& m.icon(EdictBook.FIRST_GUILD).is(Items.IRON_PICKAXE) && m.icon(EdictBook.FIRST_GUILD + 1).is(Items.ANVIL)
					&& m.icon(EdictBook.FIRST_GUILD + 2).is(Items.IRON_AXE) && m.icon(EdictBook.FIRST_GUILD + 3).is(Items.WHEAT)
					&& m.icon(EdictBook.FIRST_GUILD + 4).is(Items.LEAD) && m.icon(EdictBook.MORE_GUILDS).is(Items.ARROW),
				"the Book's guild row, page one: the Miners', Smiths', Woodsmen's, Harvest and Herders' Guilds and the More guilds button"));
			ScreenshotHarness.shot(mc, "07_guilds_book");
		}
		// B78: the More guilds button turns the row to the rest.
		if (tick == book + 45) {
			server.execute(() -> {
				if (player(server).containerMenu instanceof ChoiceMenu m) {
					m.press(EdictBook.MORE_GUILDS, player(server));
				}
			});
		}
		if (tick == book + 55) {
			ScreenshotHarness.pointAt(mc, EdictBook.MORE_GUILDS, 6);
		}
		if (tick == book + 70) {
			server.execute(() -> {
				List<String> rest = new ArrayList<>();
				if (player(server).containerMenu instanceof ChoiceMenu m) {
					for (int x = 0; x < EdictBook.GUILDS_PER_PAGE; x++) {
						ItemStack icon = m.icon(EdictBook.FIRST_GUILD + x);
						if (!icon.is(Items.LIGHT_GRAY_STAINED_GLASS_PANE)) {
							rest.add(icon.getHoverName().getString());
						}
					}
				}
				List<String> want = java.util.Arrays.asList(GUILDS).subList(EdictBook.GUILDS_PER_PAGE, count);
				Showcase.check(rest.equals(want), "the Book's guild row, page two: " + want + " (shown: " + rest + ")");
			});
			ScreenshotHarness.shot(mc, "12_guilds_book_more");
		}
		if (tick == book + 95) {
			mc.stop();
		}
	}

	private static ServerPlayer player(MinecraftServer server) {
		return server.getPlayerList().getPlayers().get(0);
	}

	/** Six Guildhalls finished in two rows around a Village Hall, a Master of each trade in front of them. */
	private void stage(MinecraftServer server) {
		ServerLevel level = server.overworld();
		ServerPlayer player = player(server);
		level.getGameRules().getRule(GameRules.RULE_DAYLIGHT).set(false, server);
		level.getGameRules().getRule(GameRules.RULE_DOMOBSPAWNING).set(false, server);
		level.setDayTime(5000);
		for (int i = 0; i < count; i++) {
			BlockPos origin = HALLS[i];
			level.getStructureManager().get(StarterBlueprints.GUILDHALL.id()).orElseThrow()
				.placeInWorld(level, origin, origin, new StructurePlaceSettings(), level.getRandom(), 2);
			BuildSiteManager.get(level).recordFinished(StarterBlueprints.GUILDHALL.id(),
				new BlueprintData.Placement(level.dimension().location(), origin, Rotation.NONE, Mirror.NONE), player.getUUID());
		}
		level.setBlockAndUpdate(HALL, ModBlocks.VILLAGE_HALL.defaultBlockState().setValue(VillageHallBlock.FACING, Direction.SOUTH));
		VillageHallBlockEntity hall = (VillageHallBlockEntity) level.getBlockEntity(HALL);
		hall.setOwner(player.getUUID(), player.getGameProfile().getName());
		VillagerProfession[] jobs = {ModVillagers.MINER, VillagerProfession.WEAPONSMITH, ModVillagers.LUMBERJACK,
			VillagerProfession.FARMER, VillagerProfession.SHEPHERD, ModVillagers.SCHOLAR, ModVillagers.NURSE, ModVillagers.INNKEEPER, ModVillagers.GUARD,
			ModVillagers.TUTOR};
		for (int i = 0; i < count; i++) {
			Villager v = EntityType.VILLAGER.spawn(level, HALLS[i].offset(9, 0, -2), MobSpawnType.COMMAND);
			v.setVillagerData(v.getVillagerData().setProfession(jobs[i]).setLevel(5));
			v.setCustomName(Component.literal(NAMES[i]));
			v.setNoAi(true);
			masters.add(v);
		}
		ScreenshotHarness.hoverLookingAt(player, new Vec3(30, -44, -24), new Vec3(30, -56, 17));
	}

	/** The player grants each Master a charter; each guild claims a finished Guildhall. */
	private void charter(MinecraftServer server) {
		ServerLevel level = server.overworld();
		VillageHallBlockEntity hall = (VillageHallBlockEntity) level.getBlockEntity(HALL);
		hall.setRank(VillageRanks.Rank.CITY);
		Guilds.PER_RANK = 4; // a City may have twelve
		for (Villager master : masters) {
			Guilds.Offer offer = Guilds.offer(player(server), master, new ItemStack(ModItems.GUILD_CHARTER));
			Showcase.check(offer.outcome() == Guilds.Outcome.GRANTED, master.getName().getString() + " was chartered: " + offer.message().getString());
		}
		Guilds.round(level, HALL, hall);
		for (int i = 0; i < count; i++) {
			Showcase.check(Guilds.founded(level, hall, AliveWorkplace.id(IDS[i])), "the " + IDS[i] + " guild was founded in a Guildhall");
		}
		// The Harvest, Herders' and Scholars' Guilds' numbers (30.19).
		int reach = io.github.jcondedata.aliveworkplace.farm.Fields.farmReach(masters.get(3));
		Showcase.check(reach == 24, "Wren's farm reaches 24 blocks from the composter: " + reach);
		int bred = io.github.jcondedata.aliveworkplace.ranch.RanchWork.cap(masters.get(4));
		Showcase.check(bred == 12, "Ebba breeds up to 12 of a kind: " + bred);
		io.github.jcondedata.aliveworkplace.research.Research.Cost cost =
			io.github.jcondedata.aliveworkplace.research.Research.Topic.SWIFT_HANDS.cost(2, level, hall);
		Showcase.check(cost.paper() == 24 && cost.books() == 2 && cost.emeralds() == 6,
			"Swift Hands II costs 24 paper, 2 books and 6 emeralds (not 32, 2, 8): " + cost);
		// The Healers', Merchants', Wardens' (and Trainers') Guilds' numbers (30.20).
		long days = io.github.jcondedata.aliveworkplace.people.Sickness.recovery(masters.get(0)) / io.github.jcondedata.aliveworkplace.hall.VillageNeeds.DAY;
		Showcase.check(days == 2, "the village's ill get well in 2 days (not 3): " + days);
		int look = io.github.jcondedata.aliveworkplace.nurse.NurseWork.cureRange(masters.get(6));
		Showcase.check(look == 48, "Mira looks 48 blocks out for the ill (not 32): " + look);
		int hire = Guilds.hirePrice(masters.get(7), 16);
		Showcase.check(hire == 12, "a 16-emerald traveller costs 12 to hire: " + hire);
		int upTo = io.github.jcondedata.aliveworkplace.guard.GuardPatrol.trainUpTo(masters.get(8));
		float strength = Guilds.strength(masters.get(8));
		Showcase.check(upTo == 5 && Math.abs(strength - 1.1f) < 1e-4f, "Wulf's guards train up to Master and hit 10% harder: " + upTo + ", " + strength);
		if (count > 9) {
			int lesson = io.github.jcondedata.aliveworkplace.tutor.Tutors.price(masters.get(9), 5);
			Showcase.check(lesson == 20, "Tova's hardest lesson costs 20 emeralds (not 24): " + lesson);
		}
	}

	/** Finds each master's card on the hall's list and checks it names their guild and its pace. */
	private void findCards(MinecraftServer server) {
		if (!(player(server).containerMenu instanceof ChoiceMenu m)) {
			Showcase.check(false, "the hall's list opened");
			return;
		}
		for (int s = VillageHallScreen.FIRST_PERSON; s < ChoiceMenu.SIZE; s++) {
			ItemStack icon = m.icon(s);
			for (int i = 0; i < count; i++) {
				if (icon.getHoverName().getString().startsWith(NAMES[i])) {
					slots[i] = s;
					ItemLore lore = icon.get(DataComponents.LORE);
					List<String> lines = lore == null ? List.of() : lore.lines().stream().map(Component::getString).toList();
					String guild = GUILDS[i];
					boolean master = lines.stream().anyMatch(l -> l.equals("Guild Master of the " + guild));
					boolean pace = !PACE[i] || lines.stream().anyMatch(l -> l.startsWith("Works ") && l.contains("faster") && l.contains("the " + guild));
					Showcase.check(master && pace, NAMES[i] + "'s card: Guild Master of the " + guild + (PACE[i] ? ", faster through it (" : " (") + String.join(" | ", lines) + ")");
				}
			}
		}
		for (int i = 0; i < count; i++) {
			Showcase.check(slots[i] >= 0, NAMES[i] + " is on the hall's list");
		}
	}
}
