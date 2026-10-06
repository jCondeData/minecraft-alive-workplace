package io.github.jcondedata.aliveworkplace.devclient;

import io.github.jcondedata.aliveworkplace.AliveWorkplace;
import io.github.jcondedata.aliveworkplace.blueprint.BlueprintData;
import io.github.jcondedata.aliveworkplace.blueprint.StarterBlueprints;
import io.github.jcondedata.aliveworkplace.cup.CupBouts;
import io.github.jcondedata.aliveworkplace.cup.CupData;
import io.github.jcondedata.aliveworkplace.cup.CupMatches;
import io.github.jcondedata.aliveworkplace.hall.Arenas;
import io.github.jcondedata.aliveworkplace.hall.Caravans;
import io.github.jcondedata.aliveworkplace.hall.VillageHallBlock;
import io.github.jcondedata.aliveworkplace.hall.VillageHallBlockEntity;
import io.github.jcondedata.aliveworkplace.registry.ModBlocks;
import io.github.jcondedata.aliveworkplace.registry.ModVillagers;
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
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * SCENE=cup_match (ROADMAP 28.20, with Cobblemon): at Thornholm's Arena the player's Grand Cup bout against its Leader
 * Mira is called in chat (a bell, [I'm ready]); the player, standing in the west box, clicks it: Mewtwo is left out
 * (legendaries are banned) and a real Cobblemon battle starts at the ring, the stands behind; then the player wins and
 * the result and the purse are read out. Checks: the call went out, the battle started with only the eligible
 * Pokémon, and the win is on the Cup's results.
 */
final class CupMatchScene {
	private static final BlockPos HALL = new BlockPos(12, -60, -12);
	private static final BlockPos ORIGIN = new BlockPos(0, -60, 0);
	private static final BlockPos ASHFORD = new BlockPos(700, -60, 300);
	private int tick;
	private volatile boolean called;
	private volatile boolean started;
	private volatile boolean eligibleOnly;
	private volatile boolean decided;
	private volatile UUID battle;

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
			server.execute(() -> call(server));
		}
		if (tick == 90) {
			ScreenshotHarness.shot(mc, "01_call");
		}
		if (tick == 110) {
			server.execute(() -> {
				ServerPlayer player = server.getPlayerList().getPlayers().get(0);
				player.sendSystemMessage(CupMatches.ready(player));
				var b = com.cobblemon.mod.common.battles.BattleRegistry.getBattleByParticipatingPlayer(player);
				started = b != null;
				if (b != null) {
					battle = b.getBattleId();
					for (var actor : b.getActors()) {
						if (actor.getUuid().equals(player.getUUID())) {
							eligibleOnly = actor.getPokemonList().size() == 3 && actor.getPokemonList().stream()
								.noneMatch(bp -> bp.getEffectedPokemon().getSpecies().getName().equalsIgnoreCase("mewtwo"));
						}
					}
				}
			});
		}
		if (tick == 220) {
			ScreenshotHarness.shot(mc, "02_battle");
		}
		if (tick == 260) {
			server.execute(() -> {
				ServerPlayer player = server.getPlayerList().getPlayers().get(0);
				if (battle != null) {
					CupMatches.decided(server, battle, player.getUUID(), false);
					var b = com.cobblemon.mod.common.battles.BattleRegistry.getBattle(battle);
					if (b != null) {
						b.end();
					}
				}
				CupData.Cup cup = CupData.get(server.overworld()).existing(HALL);
				decided = cup != null && cup.call == null && cup.results.stream().anyMatch(r -> r.winner().equals(player.getUUID()));
			});
		}
		if (tick == 300) {
			ScreenshotHarness.shot(mc, "03_result");
			Showcase.check(called, "the player's bout was called with [I'm ready]");
			Showcase.check(started, "a real battle started at the ring");
			Showcase.check(eligibleOnly, "only the eligible Pokémon went into the battle (Mewtwo left out)");
			Showcase.check(decided, "the player's win is on the Cup's results");
			mc.stop();
		}
	}

	private static Arenas.Arena arena(ServerLevel level) {
		return Arenas.at(StarterBlueprints.ARENA.id(), new BlueprintData.Placement(level.dimension().location(), ORIGIN, Rotation.NONE, Mirror.NONE));
	}

	/** Arena I built and on record, Thornholm's hall, Mira in the east box, the player's party (Mewtwo first). */
	private static void stage(MinecraftServer server) {
		ServerLevel level = server.overworld();
		level.getGameRules().getRule(GameRules.RULE_DAYLIGHT).set(false, server);
		level.getGameRules().getRule(GameRules.RULE_DOMOBSPAWNING).set(false, server);
		level.setDayTime(7000);
		level.getStructureManager().get(StarterBlueprints.ARENA.id()).orElseThrow()
			.placeInWorld(level, ORIGIN, ORIGIN, new StructurePlaceSettings(), level.getRandom(), 2);
		io.github.jcondedata.aliveworkplace.build.BuildSiteManager.get(level).recordFinished(StarterBlueprints.ARENA.id(),
			new BlueprintData.Placement(level.dimension().location(), ORIGIN, Rotation.NONE, Mirror.NONE), UUID.randomUUID());
		level.setBlockAndUpdate(HALL, ModBlocks.VILLAGE_HALL.defaultBlockState().setValue(VillageHallBlock.FACING, Direction.SOUTH));
		((VillageHallBlockEntity) level.getBlockEntity(HALL)).setCustomName(Component.literal("Thornholm"));
		Caravans.Data.get(level).setWants(ASHFORD, Component.literal("Ashford"), List.of());
		level.getEntitiesOfClass(net.minecraft.world.entity.monster.Slime.class, new AABB(ORIGIN).inflate(96)).forEach(net.minecraft.world.entity.Entity::discard);
		Arenas.Arena arena = arena(level);
		Villager v = EntityType.VILLAGER.spawn(level, arena.boxes().get(1), MobSpawnType.COMMAND);
		v.setVillagerData(v.getVillagerData().setProfession(ModVillagers.TRAINER_LEADER).setLevel(5));
		v.setCustomName(Component.literal("Mira"));
		v.setNoAi(true);
		ServerPlayer player = server.getPlayerList().getPlayers().get(0);
		var party = com.cobblemon.mod.common.Cobblemon.INSTANCE.getStorage().getParty(player);
		for (String p : List.of("mewtwo level=70", "charizard level=50", "blastoise level=50", "venusaur level=50")) {
			party.add(com.cobblemon.mod.common.api.pokemon.PokemonProperties.Companion.parse(p, " ", "=").create());
		}
		player.setGameMode(GameType.SURVIVAL);
		Vec3 box = Vec3.atBottomCenterOf(arena.boxes().get(0));
		player.teleportTo(level, box.x, box.y, box.z, -90, 10);
	}

	/** The Grand Cup's first-round bout, the player (seed 1) against Mira, called. */
	private void call(MinecraftServer server) {
		ServerLevel level = server.overworld();
		Arenas.Arena arena = arena(level);
		ServerPlayer player = server.getPlayerList().getPlayers().get(0);
		Villager mira = level.getEntitiesOfClass(Villager.class, new AABB(ORIGIN).inflate(40), Villager::hasCustomName).get(0);
		CupData.Cup cup = CupData.get(level).cup(HALL);
		cup.theme = AliveWorkplace.id("grand_cup");
		cup.day = -1;
		cup.closed = true;
		CupData.Entrant a = new CupData.Entrant(CupData.Kind.PLAYER, player.getUUID(), player.getGameProfile().getName(), 0, 0, HALL);
		CupData.Entrant b = new CupData.Entrant(CupData.Kind.LEADER, mira.getUUID(), "Mira", 5, 400, HALL);
		cup.entrants.clear();
		cup.entrants.addAll(List.of(a, b));
		cup.bracket.clear();
		cup.bracket.addAll(List.of(a, b));
		CupMatches.call(level, HALL, cup, new CupBouts.Pairing(1, a, b), arena.ring(), arena.boxes().get(0), arena.boxes().get(1));
		called = cup.call != null;
	}
}
