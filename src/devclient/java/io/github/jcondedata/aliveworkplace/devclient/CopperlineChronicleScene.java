package io.github.jcondedata.aliveworkplace.devclient;

import io.github.jcondedata.aliveworkplace.client.ChoiceTableScreen;
import io.github.jcondedata.aliveworkplace.client.VillageHallMenuScreen;
import io.github.jcondedata.aliveworkplace.hall.Chronicle;
import io.github.jcondedata.aliveworkplace.hall.HallSections;
import io.github.jcondedata.aliveworkplace.hall.VillageHallBlock;
import io.github.jcondedata.aliveworkplace.hall.VillageHallBlockEntity;
import io.github.jcondedata.aliveworkplace.hall.VillageHallScreen;
import io.github.jcondedata.aliveworkplace.registry.ModBlocks;
import io.github.jcondedata.aliveworkplace.work.ChoiceMenu;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.npc.VillagerProfession;
import net.minecraft.world.level.GameRules;

/**
 * SCENE=copperline_chronicle (ROADMAP 48.4): the Village Hall's Chronicle in Copperline, like the Chronicle board, and a
 * list menu with its tabs on the rail. A hall named Oakhaven with a chronicle of a few days (a storehouse, a raid, a
 * festival, newcomers, a birth); the Chronicle page opened from the Calendar with the raid's entry open (its whole text)
 * and the mouse on it; the Defense filter clicked (only the raid and the sighting left, the whisper line on the filter);
 * the page at 320x180 (the icon column); then the Research menu, a real list menu, with its two tabs on the rail (the
 * scholars' and, with the Old Sage settled in the village, the Ancient Lore).
 */
final class CopperlineChronicleScene {
	private static final BlockPos HALL = new BlockPos(0, -60, 0);
	private int tick;
	private Villager scholar;

	void tick(Minecraft mc, MinecraftServer server) {
		tick++;
		if (tick == 1) {
			mc.options.renderDistance().set(4);
		}
		if (tick == 20) {
			server.execute(() -> {
				ServerLevel level = server.overworld();
				level.getGameRules().getRule(GameRules.RULE_DAYLIGHT).set(false, server);
				level.getGameRules().getRule(GameRules.RULE_DOMOBSPAWNING).set(false, server);
				level.setDayTime(47L * 24000 + 6000); // day 48
				level.setBlockAndUpdate(HALL, ModBlocks.VILLAGE_HALL.defaultBlockState().setValue(VillageHallBlock.FACING, Direction.SOUTH));
				if (level.getBlockEntity(HALL) instanceof VillageHallBlockEntity hall) {
					hall.setOwner(player(server).getUUID(), player(server).getGameProfile().getName());
					hall.setCustomName(Component.literal("Oakhaven"));
				}
				chronicle(level);
				Villager v = new Villager(EntityType.VILLAGER, level);
				v.moveTo(2.5, -60, 3.5, 180, 0);
				v.setCustomName(Component.literal("Ilse"));
				v.setVillagerData(v.getVillagerData().setProfession(VillagerProfession.LIBRARIAN).setLevel(3));
				v.setNoAi(true);
				level.addFreshEntity(v);
				// the Old Sage settled here, so Research has a second tab (the Ancient Lore) beside the scholars'
				io.github.jcondedata.aliveworkplace.legend.Legends.get(io.github.jcondedata.aliveworkplace.legend.OldSage.ID)
					.ifPresent(legend -> io.github.jcondedata.aliveworkplace.legend.Legends.make(level, v, legend, "showcase", java.util.Optional.of(HALL)));
				scholar = v;
				player(server).teleportTo(level, 0.5, -60, 3.5, 180, 10);
			});
		}
		if (tick == 50) {
			server.execute(() -> {
				ServerPlayer player = player(server);
				ServerLevel level = server.overworld();
				HallSections.forget();
				VillageHallScreen.open(player, HALL);
				if (player.containerMenu instanceof ChoiceMenu m) {
					HallSections.choose(m, level, HALL, player, HallSections.Section.CALENDAR); // the rail's click
					VillageHallScreen.renderChronicle(m, level, HALL); // the Calendar's Chronicle button
				}
			});
		}
		if (tick == 75 && mc.screen instanceof VillageHallMenuScreen screen) {
			int raid = slotNamed(screen, "Raid at the south gate");
			if (raid >= 0) {
				screen.openChronicleEntry(raid);
			}
		}
		if (tick == 80 && mc.screen instanceof VillageHallMenuScreen screen) {
			int raid = slotNamed(screen, "Raid at the south gate");
			if (raid >= 0) {
				point(mc, screen.centre(raid));
			}
		}
		if (tick == 95) {
			Showcase.check(mc.screen instanceof VillageHallMenuScreen s && slotNamed(s, "Raid at the south gate") >= 0,
				"the Chronicle page opened with the raid's entry");
			ScreenshotHarness.shot(mc, "01_chronicle");
		}
		int defense = VillageHallScreen.CHRONICLE_FILTERS + Chronicle.Group.DEFENSE.ordinal();
		if (tick == 100 && mc.screen instanceof VillageHallMenuScreen screen) {
			point(mc, screen.centre(defense));
			screen.press(defense, 0, false); // the Show filter's click
		}
		if (tick == 120) {
			boolean only = mc.screen instanceof VillageHallMenuScreen s && slotNamed(s, "Raid at the south gate") >= 0
				&& slotNamed(s, "The Harvest Fair is 3 days away") < 0;
			Showcase.check(only, "Defense shows the raid and not the festival");
			ScreenshotHarness.shot(mc, "02_chronicle_defense");
		}
		if (tick == 125 && mc.screen instanceof VillageHallMenuScreen screen) {
			screen.press(VillageHallScreen.CHRONICLE_FILTERS, 0, false); // All again
		}
		if (tick == 135 && mc.screen instanceof VillageHallMenuScreen screen) {
			screen.resize(mc, 320, 180);
			point(mc, new int[]{1, 1});
		}
		if (tick == 150) {
			Showcase.check(mc.screen instanceof VillageHallMenuScreen s && s.width == 320 && s.height == 180,
				"the Chronicle laid out at 320x180");
			ScreenshotHarness.shot(mc, "03_chronicle_compact");
		}
		if (tick == 155) {
			mc.setScreen(null);
			mc.resizeDisplay();
		}
		if (tick == 165) {
			server.execute(() -> io.github.jcondedata.aliveworkplace.research.ResearchScreen.open(player(server), scholar));
		}
		if (tick == 185 && mc.screen instanceof ChoiceTableScreen screen) {
			int[] first = screen.centre(io.github.jcondedata.aliveworkplace.research.ResearchScreen.TAB_SLOTS[0]);
			int[] second = screen.centre(io.github.jcondedata.aliveworkplace.research.ResearchScreen.TAB_SLOTS[1]);
			Showcase.check(first[0] < screen.width / 4 && second[0] < screen.width / 4 && (first[0] != second[0] || first[1] != second[1]),
				"both tabs on the rail, apart (" + first[0] + "," + first[1] + " and " + second[0] + "," + second[1] + ")");
			point(mc, first);
		}
		if (tick == 200) {
			Showcase.check(mc.screen instanceof ChoiceTableScreen s && !(s instanceof VillageHallMenuScreen)
				&& s.getMenu().slots.get(io.github.jcondedata.aliveworkplace.research.ResearchScreen.TAB_SLOTS[1]).getItem().getCount() > 0,
				"the Research list menu opened with the scholars' and the Ancient Lore's tabs");
			ScreenshotHarness.shot(mc, "04_list_rail");
		}
		if (tick == 210) {
			mc.setScreen(null);
			mc.stop();
		}
	}

	/** A few days of a village's life, like the board's. */
	private static void chronicle(ServerLevel level) {
		Chronicle.atHall(level, HALL, Chronicle.Kind.FOUNDED, Component.literal("The Village Hall of Oakhaven was founded"), 1);
		Chronicle.atHall(level, HALL, Chronicle.Kind.ARRIVED, Component.literal("Suna moved in from Pinewatch"), 44);
		Chronicle.atHall(level, HALL, Chronicle.Kind.MARKET, Component.literal("A caravan from Pinewatch brought 12 wool and took 30 bread"), 44);
		Chronicle.atHall(level, HALL, Chronicle.Kind.FESTIVAL, Component.literal("The Harvest Fair is 3 days away"), 46);
		Chronicle.atHall(level, HALL, Chronicle.Kind.RAID, Component.literal("Raid at the south gate: nine zombies came up the south road. "
			+ "Garth and Wren held the wall until sunrise. Nobody was hurt; two fences were lost and Mira has them on her list"), 46);
		Chronicle.atHall(level, HALL, Chronicle.Kind.SIGHTING, Component.literal("A pillager scout was seen on the ridge"), 46);
		Chronicle.atHall(level, HALL, Chronicle.Kind.BIRTH, Component.literal("Garth and Wren had a baby, Tam"), 47);
		Chronicle.atHall(level, HALL, Chronicle.Kind.BUILT, Component.literal("The Storehouse reached level 3"), 47);
	}

	private static int slotNamed(VillageHallMenuScreen screen, String start) {
		for (int slot = VillageHallScreen.FIRST_ROW; slot < ChoiceMenu.SIZE; slot++) {
			if (screen.getMenu().slots.get(slot).getItem().getHoverName().getString().startsWith(start)) {
				return slot;
			}
		}
		return -1;
	}

	private static void point(Minecraft mc, int[] at) {
		double scale = mc.getWindow().getGuiScale();
		ScreenshotHarness.setMouse(mc, at[0] * scale, at[1] * scale);
	}

	private static ServerPlayer player(MinecraftServer server) {
		return server.getPlayerList().getPlayers().get(0);
	}
}
