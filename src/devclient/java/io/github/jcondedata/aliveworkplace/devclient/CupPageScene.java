package io.github.jcondedata.aliveworkplace.devclient;

import io.github.jcondedata.aliveworkplace.blueprint.BlueprintData;
import io.github.jcondedata.aliveworkplace.blueprint.StarterBlueprints;
import io.github.jcondedata.aliveworkplace.build.BuildSiteManager;
import io.github.jcondedata.aliveworkplace.cup.CupData;
import io.github.jcondedata.aliveworkplace.cup.CupPage;
import io.github.jcondedata.aliveworkplace.cup.Cups;
import io.github.jcondedata.aliveworkplace.hall.Caravans;
import io.github.jcondedata.aliveworkplace.hall.HallPages;
import io.github.jcondedata.aliveworkplace.hall.VillageHallBlock;
import io.github.jcondedata.aliveworkplace.hall.VillageHallBlockEntity;
import io.github.jcondedata.aliveworkplace.hall.VillageHallScreen;
import io.github.jcondedata.aliveworkplace.hall.VillageHalls;
import io.github.jcondedata.aliveworkplace.hall.VillageRanks;
import io.github.jcondedata.aliveworkplace.registry.ModBlocks;
import io.github.jcondedata.aliveworkplace.registry.ModVillagers;
import io.github.jcondedata.aliveworkplace.work.ChoiceMenu;
import java.util.List;
import java.util.UUID;
import net.minecraft.client.CloudStatus;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;

/**
 * SCENE=cup_page (ROADMAP 28.17): a Town with a finished Arena hosts the Grand Cup. Its circuit is the host and two
 * trade partners far off (their Trainer Leaders read from the caravans' list); the host's Leader Mira and two Trainers
 * stand by the hall. The player opens the hall's Cup page, signs up, and the page shows the Cup's card with its rules,
 * the circuit and who each village sends, the player on the list, the seeds and the roll of champions.
 */
final class CupPageScene {
	private static final BlockPos HALL = new BlockPos(0, -60, 3);
	private int tick;
	private volatile boolean signed;

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
				level.setDayTime(3000);
			});
		}
		if (tick == 60) {
			server.execute(() -> stage(server));
		}
		if (tick == 90) {
			server.execute(() -> {
				ServerPlayer player = server.getPlayerList().getPlayers().get(0);
				VillageHallScreen.open(player, HALL);
			});
		}
		if (tick == 105) {
			server.execute(() -> {
				ServerPlayer player = server.getPlayerList().getPlayers().get(0);
				if (player.containerMenu instanceof ChoiceMenu m) {
					m.press(HallPages.slot(CupPage.PAGE), player);
					m.press(CupPage.SIGN_UP, player);
					CupData.Cup cup = CupData.get(server.overworld()).existing(HALL);
					signed = cup != null && cup.signups.stream().anyMatch(s -> s.player().equals(player.getUUID()));
					Showcase.check(m.icon(CupPage.CARD).getHoverName().getString().equals("Grand Cup"), "the Cup page shows the Grand Cup's card ("
						+ m.icon(CupPage.CARD).getHoverName().getString() + ")");
				}
			});
		}
		if (tick == 120) {
			Showcase.check(signed, "the player signed up from the Cup page");
			ScreenshotHarness.pointAt(mc, CupPage.CARD);
		}
		if (tick == 135) {
			ScreenshotHarness.shot(mc, "01_cup_card");
			ScreenshotHarness.pointAt(mc, CupPage.CIRCUIT_ROW + 1);
		}
		if (tick == 150) {
			ScreenshotHarness.shot(mc, "02_cup_circuit");
			ScreenshotHarness.pointAt(mc, CupPage.CHAMPIONS);
		}
		if (tick == 165) {
			ScreenshotHarness.shot(mc, "03_cup_champions");
			ScreenshotHarness.pointAt(mc, CupPage.SEEDS_ROW);
		}
		if (tick == 180) {
			ScreenshotHarness.shot(mc, "04_cup_seeds");
		}
		if (tick == 195) {
			mc.stop();
		}
	}

	/** The host (a Town with an Arena on record), Mira and two Trainers, two far partners with Leaders on record, two past champions. */
	private void stage(MinecraftServer server) {
		ServerLevel level = server.overworld();
		level.setBlockAndUpdate(HALL, ModBlocks.VILLAGE_HALL.defaultBlockState().setValue(VillageHallBlock.FACING, Direction.SOUTH));
		VillageHallBlockEntity hall = (VillageHallBlockEntity) level.getBlockEntity(HALL);
		hall.setRank(VillageRanks.Rank.TOWN);
		hall.setCustomName(Component.literal("Thornholm"));
		BuildSiteManager.get(level).recordFinished(StarterBlueprints.ARENA_2.id(),
			new BlueprintData.Placement(level.dimension().location(), HALL.offset(8, 0, 4), Rotation.NONE, Mirror.NONE), UUID.randomUUID());
		Cups.COBBLEMON = true; // the scene runs with Cobblemon; this only makes sure a run without it still shows the page
		Caravans.Data data = Caravans.Data.get(level);
		data.setWants(HALL, Component.literal("Thornholm"), List.of());
		BlockPos ashford = new BlockPos(600, -60, -200);
		BlockPos bramble = new BlockPos(-900, -60, 500);
		data.setWants(ashford, Component.literal("Ashford"), List.of());
		data.setWants(bramble, Component.literal("Bramblewick"), List.of());
		data.toggleRoute(HALL, ashford, 5);
		data.toggleRoute(bramble, HALL, 5);
		data.setLeader(ashford, new Caravans.Leader(UUID.nameUUIDFromBytes("oren".getBytes()), "Oren", 5, 420, true));
		data.setLeader(bramble, new Caravans.Leader(UUID.nameUUIDFromBytes("tess".getBytes()), "Tess", 3, 90, false));
		villager(level, HALL.offset(-2, 0, 3), true, 4, "Mira");
		villager(level, HALL.offset(0, 0, 4), false, 3, "Ren");
		villager(level, HALL.offset(2, 0, 3), false, 2, "Pip");
		Cups.round(level, HALL, hall);
		CupData.Cup cup = CupData.get(level).cup(HALL);
		cup.closed = false;
		cup.champions.add(new CupData.Champion(9, io.github.jcondedata.aliveworkplace.AliveWorkplace.id("grand_cup"), "Oren", ashford));
		cup.champions.add(new CupData.Champion(17, io.github.jcondedata.aliveworkplace.AliveWorkplace.id("grand_cup"), "Mira", HALL));
		CupData.get(level).setDirty();
		Showcase.check(Cups.circuit(level, HALL).size() == 3, "the circuit is the host and its two trade partners");
		Showcase.check(VillageHalls.name(level, HALL).getString().equals("Thornholm"), "the host is Thornholm");
	}

	private static void villager(ServerLevel level, BlockPos at, boolean leader, int lvl, String name) {
		Villager v = EntityType.VILLAGER.spawn(level, at, MobSpawnType.COMMAND);
		v.setVillagerData(v.getVillagerData().setProfession(leader ? ModVillagers.TRAINER_LEADER : ModVillagers.TRAINER).setLevel(lvl));
		v.setCustomName(Component.literal(name));
		v.setNoAi(true);
	}
}
