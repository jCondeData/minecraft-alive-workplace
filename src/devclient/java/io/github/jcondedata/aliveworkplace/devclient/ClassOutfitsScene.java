package io.github.jcondedata.aliveworkplace.devclient;

import io.github.jcondedata.aliveworkplace.AliveWorkplace;
import io.github.jcondedata.aliveworkplace.client.ClassLookLayer;
import io.github.jcondedata.aliveworkplace.hall.Chronicle;
import io.github.jcondedata.aliveworkplace.people.ClassLook;
import io.github.jcondedata.aliveworkplace.people.ClassOutfits;
import io.github.jcondedata.aliveworkplace.people.LifeStages;
import io.github.jcondedata.aliveworkplace.people.SocialClasses;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import net.minecraft.client.CloudStatus;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.npc.VillagerProfession;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.GameType;

/**
 * SCENE=class_outfits (ROADMAP 34.18): the four classes side by side (Peasant, Artisan, Burgher, Noble, left to right),
 * each in three jobs (a farmer, a librarian and a toolsmith, back to front), plus an elder Noble farmer at the front, in
 * daylight. Stills: the whole group from the front, then the elder up close beside a grown Noble. Its checks: the client
 * was told each villager's class and stage of life, and each look names the layer {@link ClassOutfits} gives them.
 */
final class ClassOutfitsScene {
	private static final List<String> CLASSES = List.of("peasant", "artisan", "burgher", "noble");
	private static final List<VillagerProfession> JOBS = List.of(VillagerProfession.FARMER, VillagerProfession.LIBRARIAN, VillagerProfession.TOOLSMITH);
	private final List<Villager> villagers = new ArrayList<>();
	private Villager elder;
	private int tick;

	void tick(Minecraft mc) {
		MinecraftServer server = mc.getSingleplayerServer();
		tick++;
		if (tick == 1) {
			mc.options.renderDistance().set(6);
			mc.options.cloudStatus().set(CloudStatus.OFF);
			mc.options.guiScale().set(2);
			mc.resizeDisplay();
			mc.options.hideGui = true;
		}
		if (tick == 20) {
			server.execute(() -> {
				ServerLevel level = server.overworld();
				level.getGameRules().getRule(GameRules.RULE_DAYLIGHT).set(false, server);
				level.getGameRules().getRule(GameRules.RULE_DOMOBSPAWNING).set(false, server);
				level.setDayTime(6000);
			});
		}
		if (tick == 40) {
			server.execute(() -> stage(server));
		}
		if (tick == 80) {
			Showcase.check(ClassLook.ENABLED, "classOutfits is on");
			long today = Chronicle.day(server.overworld());
			int told = 0;
			for (Villager v : villagers) {
				ClassLook.Look look = ClassLookLayer.told(v.getId());
				SocialClasses.SocialClass c = SocialClasses.of(v);
				boolean right = look != null && look.cls().equals(Optional.ofNullable(c).map(SocialClasses.SocialClass::id))
					&& look.outfit().equals(Optional.ofNullable(ClassOutfits.outfit(c))) && look.lifeStage() == LifeStages.stage(v, today);
				Showcase.check(right, v.getName().getString() + "'s look reached the client: " + look);
				told += right ? 1 : 0;
			}
			ClassLook.Look old = ClassLookLayer.told(elder.getId());
			Showcase.check(old != null && old.lifeStage() == LifeStages.Stage.ELDER
				&& old.outfit().equals(Optional.of(AliveWorkplace.id("textures/entity/villager/class/noble.png"))), "the elder Noble's look: " + old);
			AliveWorkplace.LOG.info("[class_outfits] {} of {} looks told to the client, elder: {}", told, villagers.size(), old);
		}
		if (tick == 100) {
			ScreenshotHarness.shot(mc, "01_class_outfits_rows");
		}
		if (tick == 110) {
			server.execute(() -> player(server).teleportTo(server.overworld(), 4.0, -60, 6.2, 180f, 12f));
		}
		if (tick == 140) {
			ScreenshotHarness.shot(mc, "02_class_outfits_elder");
		}
		if (tick == 160) {
			mc.stop();
		}
	}

	private static ServerPlayer player(MinecraftServer server) {
		return server.getPlayerList().getPlayers().get(0);
	}

	/** Twelve villagers in four columns (one per class) and three rows (one per job), and an elder in front; the player faces them. */
	private void stage(MinecraftServer server) {
		ServerLevel level = server.overworld();
		ServerPlayer player = player(server);
		long today = Chronicle.day(level);
		for (int c = 0; c < CLASSES.size(); c++) {
			for (int j = 0; j < JOBS.size(); j++) {
				Villager v = villager(level, CLASSES.get(c), JOBS.get(j), -3.0 + 2.0 * c, -2.5 + 1.5 * j);
				LifeStages.setAdultSince(v, today - 10);
				villagers.add(v);
			}
		}
		elder = villager(level, "noble", VillagerProfession.FARMER, 4.5, 2.5);
		LifeStages.setAdultSince(elder, today - LifeStages.ELDER_DAYS - 5);
		elder.setCustomName(Component.literal("Bram"));
		for (Villager v : villagers) {
			level.addFreshEntity(v);
		}
		level.addFreshEntity(elder);
		player.getInventory().clearContent();
		player.setGameMode(GameType.SPECTATOR);
		player.teleportTo(level, 0.0, -58.5, 10.5, 180f, 14f);
		Showcase.check(LifeStages.isElder(elder, today), "Bram is an elder (grown " + LifeStages.grownDays(elder, today) + " days)");
	}

	private static Villager villager(ServerLevel level, String cls, VillagerProfession job, double x, double z) {
		Villager v = EntityType.VILLAGER.create(level);
		v.setVillagerData(v.getVillagerData().setProfession(job).setLevel(2));
		v.setVillagerXp(10);
		v.setNoAi(true);
		SocialClasses.SocialClass c = SocialClasses.get(AliveWorkplace.id(cls));
		Showcase.check(c != null, "the " + cls + " class is loaded");
		if (c != null) {
			SocialClasses.seed(v, c);
		}
		v.moveTo(x, -60, z, 0f, 0f);
		v.setYHeadRot(0f);
		v.setYBodyRot(0f);
		return v;
	}
}
