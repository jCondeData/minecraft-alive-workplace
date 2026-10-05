package io.github.jcondedata.aliveworkplace.devclient;

import io.github.jcondedata.aliveworkplace.AliveWorkplace;
import io.github.jcondedata.aliveworkplace.hall.Edicts;
import io.github.jcondedata.aliveworkplace.hall.Reforms;
import io.github.jcondedata.aliveworkplace.hall.VillageHallBlock;
import io.github.jcondedata.aliveworkplace.hall.VillageHallBlockEntity;
import io.github.jcondedata.aliveworkplace.people.Chatter;
import io.github.jcondedata.aliveworkplace.registry.ModBlocks;
import java.util.List;
import net.minecraft.client.CloudStatus;
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
import net.minecraft.world.level.GameType;

/**
 * SCENE=village_talk (ROADMAP 30.21): three villagers in front of the Village Hall talk of Long Shifts in force ("Long
 * shifts again... my back."), then the village finishes The Shift Bell and they talk of it reformed ("The shift bell's
 * rung. Home we go."). Its checks: the topics Chatter picks from are the edict's, then the reformed edict's.
 */
final class VillageTalkScene {
	private static final BlockPos HALL = new BlockPos(0, -60, 0);
	private static final String LONG_SHIFTS = AliveWorkplace.id("long_shifts").toString();
	private static final String[] NAMES = {"Bram", "Iris", "Mae"};
	private static final VillagerProfession[] JOBS = {VillagerProfession.FARMER, VillagerProfession.LIBRARIAN, VillagerProfession.MASON};
	private final Villager[] talkers = new Villager[3];
	private int tick;

	void tick(Minecraft mc) {
		MinecraftServer server = mc.getSingleplayerServer();
		tick++;
		if (tick == 1) {
			mc.options.renderDistance().set(6);
			mc.options.cloudStatus().set(CloudStatus.OFF);
			mc.options.guiScale().set(2);
			mc.resizeDisplay();
		}
		if (tick == 20) {
			server.execute(() -> {
				ServerLevel level = server.overworld();
				level.getGameRules().getRule(GameRules.RULE_DAYLIGHT).set(false, server);
				level.getGameRules().getRule(GameRules.RULE_DOMOBSPAWNING).set(false, server);
				level.setDayTime(12000); // evening: off work, talking
				Chatter.ENABLED = false; // the scene picks the lines
			});
		}
		if (tick == 40) {
			server.execute(() -> stage(server));
		}
		if (tick == 80) {
			server.execute(() -> talk(server, "edict_long_shifts"));
		}
		if (tick == 110) {
			ScreenshotHarness.shot(mc, "01_village_talk_long_shifts");
		}
		if (tick == 160) {
			server.execute(() -> {
				ServerLevel level = server.overworld();
				VillageHallBlockEntity hall = (VillageHallBlockEntity) level.getBlockEntity(HALL);
				hall.setReforms(List.of(new Reforms.Progress(LONG_SHIFTS, 3, true, 0)));
				talk(server, "reformed_long_shifts");
			});
		}
		if (tick == 190) {
			ScreenshotHarness.shot(mc, "02_village_talk_shift_bell");
		}
		if (tick == 230) {
			mc.stop();
		}
	}

	/** Each villager says a line of {@code topic}, which must be one Chatter would pick now. */
	private void talk(MinecraftServer server, String topic) {
		ServerLevel level = server.overworld();
		ServerPlayer player = server.getPlayerList().getPlayers().get(0);
		for (int i = 0; i < talkers.length; i++) {
			Villager v = talkers[i];
			Showcase.check(Chatter.civicTopics(level, v, HALL).contains(topic), NAMES[i] + " talks of " + topic + ": " + Chatter.civicTopics(level, v, HALL));
			Component line = Chatter.say(level, v, player, topic, i % 2, "");
			v.setYHeadRot(0f);
			AliveWorkplace.LOG.info("[village_talk] {}: {}", NAMES[i], line.getString());
		}
	}

	private void stage(MinecraftServer server) {
		ServerLevel level = server.overworld();
		ServerPlayer player = server.getPlayerList().getPlayers().get(0);
		level.setBlockAndUpdate(HALL, ModBlocks.VILLAGE_HALL.defaultBlockState().setValue(VillageHallBlock.FACING, Direction.SOUTH));
		VillageHallBlockEntity hall = (VillageHallBlockEntity) level.getBlockEntity(HALL);
		hall.setOwner(player.getUUID(), player.getGameProfile().getName());
		Edicts.Result r = Edicts.proclaim(level, HALL, null, Edicts.find("long_shifts").orElseThrow());
		Showcase.check(r.done(), "Long Shifts proclaimed: " + r.message().getString());
		double[] xs = {-2.0, 0.5, 3.0};
		for (int i = 0; i < 3; i++) {
			Villager v = EntityType.VILLAGER.create(level);
			v.setVillagerData(v.getVillagerData().setProfession(JOBS[i]).setLevel(2));
			v.setVillagerXp(10);
			v.setCustomName(Component.literal(NAMES[i]));
			v.setNoAi(true); // they stay in the picture
			v.moveTo(xs[i], HALL.getY(), HALL.getZ() + 3.5, 0f, 0f);
			v.setYHeadRot(0f);
			level.addFreshEntity(v);
			talkers[i] = v;
		}
		player.setGameMode(GameType.SURVIVAL);
		player.getAbilities().flying = false;
		player.onUpdateAbilities();
		player.teleportTo(level, 0.5, HALL.getY(), HALL.getZ() + 9.5, 180f, 8f);
	}
}
