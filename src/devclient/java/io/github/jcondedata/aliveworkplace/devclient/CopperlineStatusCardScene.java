package io.github.jcondedata.aliveworkplace.devclient;

import io.github.jcondedata.aliveworkplace.card.CardToasts;
import io.github.jcondedata.aliveworkplace.card.JobCards;
import io.github.jcondedata.aliveworkplace.card.StatusCard;
import io.github.jcondedata.aliveworkplace.card.StatusCardMenu;
import io.github.jcondedata.aliveworkplace.client.CopperToast;
import io.github.jcondedata.aliveworkplace.client.StatusCardScreen;
import io.github.jcondedata.aliveworkplace.ui.CopperLayout;
import io.github.jcondedata.aliveworkplace.ui.StatusCardLayout;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.npc.VillagerProfession;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameRules;

/**
 * SCENE=copperline_status_card (ROADMAP 48.3): the villager status card, its tooltips and the toast in Copperline, like the
 * VillagerCard board. A builder's card laid out as the board's pinned card (a builder mid-build: her site and its progress,
 * what she needs with bars, the Hall page button grayed out with its reason, In chat the copper one); the mouse on a line
 * (its tooltip), then on the grayed-out button (its reason as a tooltip); a builder's "finished" toast sent through the
 * real packet; at 320x180 (the GUI scale 3 rule) the same card. The card's contents are set here so the stills read like
 * the board; the job cards' real contents are filmed by SCENE=status_card.
 */
final class CopperlineStatusCardScene {
	private int tick;
	private Villager mira;

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
				level.setDayTime(6000);
				Villager v = new Villager(EntityType.VILLAGER, level);
				v.setUUID(new java.util.UUID(0xc0ffe3L, 3));
				v.moveTo(0.5, -60, 4.5, 180, 0);
				v.setCustomName(Component.literal("Mira"));
				v.setVillagerData(v.getVillagerData().setProfession(VillagerProfession.MASON).setLevel(2));
				level.addFreshEntity(v);
				mira = v;
				player(server).teleportTo(level, 0.5, -60, 1.5, 0, 10);
			});
		}
		if (tick == 60) {
			server.execute(() -> StatusCardMenu.open(player(server), mira, card(mira.getId()), (p, v, button) -> { }));
		}
		if (tick == 80 && mc.screen instanceof StatusCardScreen screen) {
			mc.getToasts().clear();
			point(mc, screen.lineCentre(0));
		}
		if (tick == 95) {
			Showcase.check(mc.screen instanceof StatusCardScreen, "the status card opened");
			if (mc.screen instanceof StatusCardScreen screen) {
				checkLayout(screen, "at GUI scale 2");
				Showcase.check(screen.copperIndex() == 1, "In chat is the one copper button (Hall page is grayed out)");
			}
			ScreenshotHarness.shot(mc, "01_card_tooltip");
		}
		if (tick == 100 && mc.screen instanceof StatusCardScreen screen) {
			point(mc, screen.centre(JobCards.HALL));
		}
		if (tick == 115) {
			ScreenshotHarness.shot(mc, "02_card_refused");
			server.execute(() -> CardToasts.send(player(server),
				CardToasts.built(Component.literal("Mira"), Component.literal("Stone House"), false)));
		}
		if (tick == 125 && mc.screen instanceof StatusCardScreen screen) {
			point(mc, screen.centre(StatusCardMenu.CLOSE));
		}
		if (tick == 140) {
			CopperToast toast = mc.getToasts().getToast(CopperToast.class, net.minecraft.client.gui.components.toasts.Toast.NO_TOKEN);
			Showcase.check(toast != null && toast.line().getString().equals("Mira finished it."),
				"the builder's toast arrived through the packet" + (toast == null ? " (none)" : " (" + toast.line().getString() + ")"));
			ScreenshotHarness.shot(mc, "03_card_toast");
		}
		if (tick == 150 && mc.screen instanceof StatusCardScreen screen) {
			screen.resize(mc, 320, 180);
		}
		if (tick == 165 && mc.screen instanceof StatusCardScreen screen) {
			Showcase.check(screen.width == 320 && screen.height == 180, "the card laid out at 320x180 (" + screen.width + "x" + screen.height + ")");
			checkLayout(screen, "at 320x180");
			ScreenshotHarness.shot(mc, "04_card_compact");
		}
		if (tick == 180) {
			mc.setScreen(null);
			mc.stop();
		}
	}

	/** A builder's card like the board's pinned one. */
	private static StatusCard card(int id) {
		List<StatusCard.Line> lines = List.of(
			new StatusCard.Line(new ItemStack(Items.OAK_LOG), Component.literal("Oak Log"), Component.literal("14/30"), StatusCard.ORANGE, 14 / 30f),
			new StatusCard.Line(new ItemStack(Items.COBBLESTONE), Component.literal("Cobblestone"), Component.literal("64/64"), StatusCard.GREEN, 1f),
			new StatusCard.Line(new ItemStack(Items.GLASS_PANE), Component.literal("Glass Pane"), Component.literal("0/8"), StatusCard.RED, 0f),
			new StatusCard.Line(new ItemStack(Items.EXPERIENCE_BOTTLE), Component.literal("Experience"), Component.literal("Level 2"), StatusCard.WHITE, 0.4f));
		List<StatusCard.Button> buttons = List.of(
			StatusCard.Button.of(JobCards.HALL, Component.translatable("screen.aliveworkplace.card.job.hall"),
				Component.literal("There's no Village Hall near her.")),
			StatusCard.Button.of(JobCards.CHAT, Component.translatable("screen.aliveworkplace.card.job.chat"), null),
			StatusCard.Button.of(StatusCardMenu.CLOSE, Component.translatable("screen.aliveworkplace.card.close"), null));
		return new StatusCard(id, Component.literal("Mira"), Component.literal("Builder · level 2"),
			Component.literal("Building Stone House: 64% done. Next: fetch 16 Oak Log."), Component.literal("What she needs"), lines,
			Component.empty(), buttons, Component.literal("Sneak and use on a worker to open"));
	}

	private static void point(Minecraft mc, int[] at) {
		double scale = mc.getWindow().getGuiScale();
		ScreenshotHarness.setMouse(mc, at[0] * scale, at[1] * scale);
	}

	/** The card inside the screen; the buttons inside it, apart, under the reason box. */
	private static void checkLayout(StatusCardScreen screen, String what) {
		StatusCardLayout.Boxes b = screen.boxes();
		int[] c = b.card();
		boolean ok = c[0] >= 0 && c[1] >= 0 && c[0] + c[2] <= screen.width && c[1] + c[3] <= screen.height && b.buttons().length == 2;
		int[][] bs = b.buttons();
		for (int i = 0; i < bs.length; i++) {
			ok &= bs[i][0] >= c[0] && bs[i][0] + bs[i][2] <= c[0] + c[2] && bs[i][1] + bs[i][3] <= c[1] + c[3]
				&& bs[i][1] >= b.refusal()[1] + b.refusal()[3];
			for (int j = 0; j < i; j++) {
				ok &= !CopperLayout.overlap(bs[i], bs[j]);
			}
		}
		Showcase.check(ok, what + ": the card inside the screen, Hall page and In chat inside it, apart, under the reason");
	}

	private static ServerPlayer player(MinecraftServer server) {
		return server.getPlayerList().getPlayers().get(0);
	}
}
