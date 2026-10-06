package io.github.jcondedata.aliveworkplace.devclient;

import io.github.jcondedata.aliveworkplace.AliveWorkplace;
import io.github.jcondedata.aliveworkplace.blueprint.BlueprintData;
import io.github.jcondedata.aliveworkplace.blueprint.StarterBlueprints;
import io.github.jcondedata.aliveworkplace.cup.CupBattlers;
import io.github.jcondedata.aliveworkplace.cup.CupBout;
import io.github.jcondedata.aliveworkplace.cup.CupBouts;
import io.github.jcondedata.aliveworkplace.cup.CupData;
import io.github.jcondedata.aliveworkplace.cup.CupThemes;
import io.github.jcondedata.aliveworkplace.hall.Arenas;
import io.github.jcondedata.aliveworkplace.hall.Caravans;
import io.github.jcondedata.aliveworkplace.hall.VillageHallBlock;
import io.github.jcondedata.aliveworkplace.hall.VillageHallBlockEntity;
import io.github.jcondedata.aliveworkplace.registry.ModBlocks;
import io.github.jcondedata.aliveworkplace.registry.ModVillagers;
import java.util.List;
import net.minecraft.client.CloudStatus;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * SCENE=cup_bout (ROADMAP 28.18, with Cobblemon): at Thornholm's Arena, its Leader Mira meets Dara of Ashford in a
 * Grand Cup bout: each stands in their box, their Pokémon come out one at a time beside the ring, face each other and
 * trade moves (the attack animations and type impacts), a fainted one is called back and the next comes out, until one
 * side has none left (90 seconds at most). The result is read out in chat. Checks: real Pokémon came out for both
 * sides, the bout ended with a winner on the Cup's results, and no Pokémon is left at the ring.
 */
final class CupBoutScene {
	private static final BlockPos HALL = new BlockPos(12, -60, -12);
	private static final BlockPos ORIGIN = new BlockPos(0, -60, 0);
	private static final BlockPos ASHFORD = new BlockPos(700, -60, 300);
	/** How many of each side's Pokémon (the Grand Cup brings six; three keep the scene short). */
	private static final int BRING = 3;
	private int tick;
	private int endedAt = -1;
	private volatile boolean bothOut;
	private volatile boolean decided;
	private volatile boolean cleared;
	private int shots;

	void tick(Minecraft mc) {
		MinecraftServer server = mc.getSingleplayerServer();
		tick++;
		if (tick == 1) {
			mc.options.renderDistance().set(6);
			mc.options.cloudStatus().set(CloudStatus.OFF);
		}
		if (tick == 20) {
			server.execute(() -> stage(server));
		}
		if (tick == 60) {
			server.execute(() -> {
				ServerLevel level = server.overworld();
				Arenas.Arena arena = arena(level);
				ScreenshotHarness.hoverLookingAt(server.getPlayerList().getPlayers().get(0),
					Vec3.atBottomCenterOf(arena.ring()).add(0, 6, -13), Vec3.atBottomCenterOf(arena.ring()).add(0, 0, 0));
				begin(level);
			});
		}
		if (tick == 100) {
			server.execute(() -> bothOut = CupBouts.shown(server.overworld(), HALL, 0) != null && CupBouts.shown(server.overworld(), HALL, 1) != null);
			ScreenshotHarness.shot(mc, "01_bout_start");
		}
		if (tick > 100 && endedAt < 0 && tick % 150 == 0 && shots < 8) {
			shots++;
			ScreenshotHarness.shot(mc, String.format("bout_%02d", shots));
		}
		if (tick > 100 && endedAt < 0 && tick % 10 == 0) {
			server.execute(() -> {
				CupData.Cup cup = CupData.get(server.overworld()).existing(HALL);
				if (cup != null && cup.bout == null && !cup.results.isEmpty()) {
					decided = true;
				}
			});
			if (decided) {
				endedAt = tick;
			}
		}
		if (endedAt > 0 && tick == endedAt + 10) {
			ScreenshotHarness.shot(mc, "02_bout_result");
		}
		if (endedAt > 0 && tick == endedAt + 60) {
			server.execute(() -> cleared = CupBattlers.EXTENSION.call(b -> b.inBox(server.overworld(), new AABB(arena(server.overworld()).ring())
				.inflate(24, 12, 24)), List.of()).isEmpty());
		}
		if (endedAt > 0 && tick == endedAt + 70 || tick == 2400) {
			Showcase.check(bothOut, "both trainers' Pokémon came out beside the ring");
			Showcase.check(decided, "the bout ended with a winner on the Cup's results");
			Showcase.check(cleared, "no Pokémon is left at the ring after the bout");
			ScreenshotHarness.shot(mc, "03_ring_cleared");
			mc.stop();
		}
	}

	private static Arenas.Arena arena(ServerLevel level) {
		return Arenas.at(StarterBlueprints.ARENA.id(), new BlueprintData.Placement(level.dimension().location(), ORIGIN, Rotation.NONE, Mirror.NONE));
	}

	/** Arena I built and on record, Thornholm's hall, Ashford far off on the caravans' list, Mira and Dara in their boxes. */
	private static void stage(MinecraftServer server) {
		ServerLevel level = server.overworld();
		level.getGameRules().getRule(GameRules.RULE_DAYLIGHT).set(false, server);
		level.getGameRules().getRule(GameRules.RULE_DOMOBSPAWNING).set(false, server);
		level.setDayTime(7000);
		level.getStructureManager().get(StarterBlueprints.ARENA.id()).orElseThrow()
			.placeInWorld(level, ORIGIN, ORIGIN, new StructurePlaceSettings(), level.getRandom(), 2);
		io.github.jcondedata.aliveworkplace.build.BuildSiteManager.get(level).recordFinished(StarterBlueprints.ARENA.id(),
			new BlueprintData.Placement(level.dimension().location(), ORIGIN, Rotation.NONE, Mirror.NONE), java.util.UUID.randomUUID());
		level.setBlockAndUpdate(HALL, ModBlocks.VILLAGE_HALL.defaultBlockState().setValue(VillageHallBlock.FACING, Direction.SOUTH));
		((VillageHallBlockEntity) level.getBlockEntity(HALL)).setCustomName(Component.literal("Thornholm"));
		Caravans.Data.get(level).setWants(ASHFORD, Component.literal("Ashford"), List.of());
		level.getEntitiesOfClass(net.minecraft.world.entity.monster.Slime.class, new AABB(ORIGIN).inflate(96)).forEach(net.minecraft.world.entity.Entity::discard);
		Arenas.Arena arena = arena(level);
		trainer(level, arena.boxes().get(0), "Mira", ModVillagers.TRAINER_LEADER, -90);
		trainer(level, arena.boxes().get(1), "Dara", ModVillagers.TRAINER_LEADER, 90);
	}

	private static void trainer(ServerLevel level, BlockPos at, String name, net.minecraft.world.entity.npc.VillagerProfession job, float yaw) {
		Villager v = EntityType.VILLAGER.spawn(level, at, MobSpawnType.COMMAND);
		v.setVillagerData(v.getVillagerData().setProfession(job).setLevel(5));
		v.setCustomName(Component.literal(name));
		v.setNoAi(true);
		v.setYRot(yaw);
		v.setYHeadRot(yaw);
		v.setYBodyRot(yaw);
	}

	/** The Grand Cup's first-round bout, Mira (seed 1) against Dara (seed 2), their teams as the Cup fields them. */
	private static void begin(ServerLevel level) {
		Arenas.Arena arena = arena(level);
		List<Villager> trainers = level.getEntitiesOfClass(Villager.class, new AABB(ORIGIN).inflate(40), Villager::hasCustomName);
		Villager mira = trainers.stream().filter(v -> v.getCustomName().getString().equals("Mira")).findFirst().orElseThrow();
		Villager dara = trainers.stream().filter(v -> v.getCustomName().getString().equals("Dara")).findFirst().orElseThrow();
		CupData.Cup cup = CupData.get(level).cup(HALL);
		cup.theme = AliveWorkplace.id("grand_cup");
		cup.day = 1;
		cup.closed = true;
		CupData.Entrant a = new CupData.Entrant(CupData.Kind.LEADER, mira.getUUID(), "Mira", 5, 400, HALL);
		CupData.Entrant b = new CupData.Entrant(CupData.Kind.LEADER, dara.getUUID(), "Dara", 5, 380, ASHFORD);
		cup.entrants.clear();
		cup.entrants.addAll(List.of(a, b));
		CupThemes.Theme theme = CupThemes.get(cup.theme);
		Showcase.check(theme != null, "the Grand Cup is loaded");
		List<CupBout.Fighter> teamA = CupBouts.team(a, theme);
		List<CupBout.Fighter> teamB = CupBouts.team(b, theme);
		Showcase.check(teamA.size() == theme.bring() && teamB.size() == theme.bring(), "both trainers field the Grand Cup's six ("
			+ teamA.size() + ", " + teamB.size() + ")");
		CupBouts.begin(level, HALL, cup, new CupBout(a.id(), b.id(), teamA.subList(0, Math.min(BRING, teamA.size())),
			teamB.subList(0, Math.min(BRING, teamB.size())), 1, CupBout.seed(HALL, 1, 1, a.id(), b.id()), arena.ring(), arena.boxes().get(0),
			arena.boxes().get(1)));
	}
}
