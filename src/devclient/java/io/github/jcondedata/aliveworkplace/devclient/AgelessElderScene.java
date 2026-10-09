package io.github.jcondedata.aliveworkplace.devclient;

import io.github.jcondedata.aliveworkplace.grave.GraveBlockEntity;
import io.github.jcondedata.aliveworkplace.hall.Chronicle;
import io.github.jcondedata.aliveworkplace.hall.VillageHallBlock;
import io.github.jcondedata.aliveworkplace.hall.VillageHallBlockEntity;
import io.github.jcondedata.aliveworkplace.hall.VillageHallScreen;
import io.github.jcondedata.aliveworkplace.people.LifeStages;
import io.github.jcondedata.aliveworkplace.registry.ModBlocks;
import io.github.jcondedata.aliveworkplace.registry.ModItems;
import io.github.jcondedata.aliveworkplace.work.ChoiceMenu;
import java.util.List;
import net.minecraft.client.CloudStatus;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
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
import net.minecraft.world.item.component.ItemLore;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.GameType;

/**
 * SCENE=ageless_elder (ROADMAP 34.19a): two elders by a Village Hall, Bram (a Master farmer, two days from his time)
 * and Dara (an ordinary fletcher, her time tonight), and a player with one Evergreen Charm. Stills: Bram's card on the
 * hall's list before (an elder, his time near); the charm's tooltip; Dara refusing it (the reason over the hotbar, the
 * charm kept); Bram taking it (used up); the night after both have had 45 elder days, Dara's grave beside Bram, who is
 * still there; Bram's card after, with the gold leaf badge. Its checks: each of those, read from the game.
 */
final class AgelessElderScene {
	private static final BlockPos HALL = new BlockPos(0, -60, -3);
	private static final String BADGE = "❦ Ageless: will never leave us";
	private int tick;
	private Villager bram;
	private Villager dara;
	private int bramSlot = -1;

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
			});
		}
		if (tick == 40) {
			server.execute(() -> stage(server));
		}
		// Bram's card before: an elder, and his time is near.
		if (tick == 70) {
			server.execute(() -> VillageHallScreen.open(player(server), HALL));
		}
		if (tick == 85) {
			server.execute(() -> {
				List<String> card = findBram(server);
				Showcase.check(card.contains("Elder · grown 158 days") && card.contains("Their time comes in 2 days (an Evergreen Charm keeps them)")
					&& !card.contains(BADGE), "Bram's card before: an elder of 158 grown days, his time in 2 days (" + String.join(" | ", card) + ")");
			});
		}
		if (tick == 100) {
			ScreenshotHarness.pointAt(mc, bramSlot);
		}
		if (tick == 115) {
			ScreenshotHarness.shot(mc, "01_ageless_elder_before");
		}
		// The charm's tooltip.
		if (tick == 125) {
			mc.setScreen(new InventoryScreen(mc.player));
		}
		if (tick == 135) {
			double scale = mc.getWindow().getGuiScale();
			int left = (mc.getWindow().getGuiScaledWidth() - 176) / 2;
			int top = (mc.getWindow().getGuiScaledHeight() - 166) / 2;
			setMouse(mc, (left + 8 + 8) * scale, (top + 142 + 8) * scale); // hotbar slot 0
		}
		if (tick == 155) {
			mc.getToasts().clear();
			ScreenshotHarness.shot(mc, "02_ageless_elder_charm");
			List<String> tip = Screen.getTooltipFromItem(mc, mc.player.getInventory().getItem(0)).stream().map(Component::getString).toList();
			Showcase.check(mc.screen instanceof InventoryScreen && tip.contains("Evergreen Charm")
				&& tip.contains("Sneak-right-click an elder: they become ageless and never pass away. One charm, one villager."),
				"the charm's tooltip says what it does: " + tip);
		}
		if (tick == 165) {
			mc.setScreen(null);
		}
		// Dara, an ordinary elder, won't take it.
		if (tick == 190) {
			server.execute(() -> {
				ServerPlayer player = player(server);
				LifeStages.Offer offer = LifeStages.offer(player, dara, player.getMainHandItem()); // the sneak-right-click's own path
				Showcase.check(offer.outcome() == LifeStages.Outcome.NO_GOOD_TRAIT && player.getMainHandItem().is(ModItems.EVERGREEN_CHARM)
					&& !LifeStages.isAgeless(dara), "Dara refused the charm and the player kept it: " + offer.message().getString());
			});
		}
		if (tick == 205) {
			ScreenshotHarness.shot(mc, "03_ageless_elder_refused");
		}
		// Bram, a Master of his trade, does.
		if (tick == 270) {
			server.execute(() -> {
				ServerPlayer player = player(server);
				LifeStages.Offer offer = LifeStages.offer(player, bram, player.getMainHandItem());
				Showcase.check(offer.outcome() == LifeStages.Outcome.ACCEPTED && offer.trait() == LifeStages.Trait.MASTER && LifeStages.isAgeless(bram),
					"Bram took the charm: " + offer.message().getString());
				Showcase.check(player.getMainHandItem().isEmpty(), "the charm was used up");
				VillageHallBlockEntity hall = (VillageHallBlockEntity) server.overworld().getBlockEntity(HALL);
				Showcase.check(hall.chronicle().stream().anyMatch(e -> e.kind() == Chronicle.Kind.LIFE && e.text().getString().startsWith("Bram will never leave us")),
					"the chronicle says Bram will never leave us");
			});
		}
		if (tick == 280) {
			ScreenshotHarness.shot(mc, "04_ageless_elder_accepted");
		}
		// 45 elder days for both, and night falls: Dara passes and leaves a grave; Bram stays.
		if (tick == 350) {
			server.execute(() -> {
				ServerLevel level = server.overworld();
				long today = Chronicle.day(level);
				LifeStages.setAdultSince(bram, today - LifeStages.ELDER_DAYS - 45);
				LifeStages.setAdultSince(dara, today - LifeStages.ELDER_DAYS - 45);
				level.setDayTime(13500);
				LifeStages.round(level, HALL, List.of(bram, dara)); // the hall's round, tonight
				Showcase.check(!dara.isAlive(), "Dara, an elder of 45 days, passed in the night");
				Showcase.check(bram.isAlive() && LifeStages.daysLeft(bram, today) == -1, "Bram, ageless, is still here after 45 elder days");
			});
		}
		if (tick == 395) {
			server.execute(() -> {
				ServerLevel level = server.overworld();
				boolean grave = false;
				for (BlockPos p : BlockPos.betweenClosed(HALL.offset(-6, -1, -3), HALL.offset(6, 2, 8))) {
					if (level.getBlockEntity(p) instanceof GraveBlockEntity g && g.name().getString().equals("Dara")) {
						grave = true;
					}
				}
				Showcase.check(grave, "Dara left a grave");
			});
			ScreenshotHarness.shot(mc, "05_ageless_elder_night");
		}
		// Bram's card after: the gold leaf badge.
		if (tick == 405) {
			server.execute(() -> {
				server.overworld().setDayTime(1000);
				VillageHallScreen.open(player(server), HALL);
			});
		}
		if (tick == 420) {
			server.execute(() -> {
				List<String> card = findBram(server);
				Showcase.check(card.contains("Elder · grown 165 days") && card.contains(BADGE) && card.stream().noneMatch(l -> l.startsWith("Their time")),
					"Bram's card after: the gold leaf badge, no time coming (" + String.join(" | ", card) + ")");
			});
		}
		if (tick == 435) {
			ScreenshotHarness.pointAt(mc, bramSlot);
		}
		if (tick == 450) {
			ScreenshotHarness.shot(mc, "06_ageless_elder_badge");
		}
		if (tick == 465) {
			mc.stop();
		}
	}

	private static ServerPlayer player(MinecraftServer server) {
		return server.getPlayerList().getPlayers().get(0);
	}

	/** A Village Hall, the two elders in front of it facing the player, and the charm in the player's hand. */
	private void stage(MinecraftServer server) {
		ServerLevel level = server.overworld();
		ServerPlayer player = player(server);
		level.setBlockAndUpdate(HALL, ModBlocks.VILLAGE_HALL.defaultBlockState().setValue(VillageHallBlock.FACING, Direction.SOUTH));
		VillageHallBlockEntity hall = (VillageHallBlockEntity) level.getBlockEntity(HALL);
		hall.setOwner(player.getUUID(), player.getGameProfile().getName());
		long today = Chronicle.day(level);
		bram = elder(level, "Bram", VillagerProfession.FARMER, 5, -1.5);
		LifeStages.setAdultSince(bram, today - LifeStages.ELDER_DAYS - 38);
		dara = elder(level, "Dara", VillagerProfession.FLETCHER, 2, 2.5);
		LifeStages.setAdultSince(dara, today - LifeStages.ELDER_DAYS - 40);
		player.getInventory().clearContent();
		player.getInventory().setItem(0, new ItemStack(ModItems.EVERGREEN_CHARM));
		player.getInventory().selected = 0;
		player.setGameMode(GameType.SURVIVAL); // a charm is used up
		player.getAbilities().flying = false;
		player.onUpdateAbilities();
		player.teleportTo(level, 0.5, -60, 4.5, 180f, 8f);
		Showcase.check(LifeStages.isElder(bram, today) && LifeStages.isElder(dara, today) && LifeStages.goodTrait(dara) == null,
			"two elders were staged: Bram a Master farmer, Dara an ordinary fletcher");
	}

	private static Villager elder(ServerLevel level, String name, VillagerProfession job, int lvl, double x) {
		Villager v = EntityType.VILLAGER.create(level);
		v.setVillagerData(v.getVillagerData().setProfession(job).setLevel(lvl));
		v.setVillagerXp(10);
		v.setCustomName(Component.literal(name));
		v.setNoAi(true); // they stay in the picture
		v.moveTo(x, -60, 0.5, 0f, 0f);
		v.setYHeadRot(0f);
		v.setYBodyRot(0f);
		level.addFreshEntity(v);
		return v;
	}

	/** Finds Bram's card on the hall's list (its slot is kept for the pointer) and returns its lines. */
	private List<String> findBram(MinecraftServer server) {
		if (!(player(server).containerMenu instanceof ChoiceMenu m)) {
			Showcase.check(false, "the hall's list opened");
			return List.of();
		}
		for (int s = VillageHallScreen.FIRST_PERSON; s < ChoiceMenu.SIZE; s++) {
			ItemStack icon = m.icon(s);
			if (icon.getHoverName().getString().startsWith("Bram")) {
				bramSlot = s;
				ItemLore lore = icon.get(DataComponents.LORE);
				return lore == null ? List.of() : lore.lines().stream().map(Component::getString).toList();
			}
		}
		Showcase.check(false, "Bram is on the hall's list");
		return List.of();
	}

	/** Puts the mouse at window pixel ({@code x}, {@code y}); the harness has no real mouse. */
	private static void setMouse(Minecraft mc, double x, double y) {
		try {
			var xpos = net.minecraft.client.MouseHandler.class.getDeclaredField("xpos");
			var ypos = net.minecraft.client.MouseHandler.class.getDeclaredField("ypos");
			xpos.setAccessible(true);
			ypos.setAccessible(true);
			xpos.setDouble(mc.mouseHandler, x);
			ypos.setDouble(mc.mouseHandler, y);
		} catch (ReflectiveOperationException e) {
			Showcase.check(false, "couldn't move the mouse: " + e);
		}
	}
}
