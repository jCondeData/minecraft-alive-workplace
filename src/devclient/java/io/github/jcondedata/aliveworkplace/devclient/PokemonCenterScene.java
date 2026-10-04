package io.github.jcondedata.aliveworkplace.devclient;

import com.cobblemon.mod.common.Cobblemon;
import com.cobblemon.mod.common.api.pokemon.PokemonProperties;
import com.cobblemon.mod.common.block.entity.HealingMachineBlockEntity;
import com.cobblemon.mod.common.pokemon.Pokemon;
import io.github.jcondedata.aliveworkplace.blueprint.StarterBlueprints;
import io.github.jcondedata.aliveworkplace.nurse.Nurses;
import io.github.jcondedata.aliveworkplace.registry.ModVillagers;
import io.github.jcondedata.aliveworkplace.work.Jobs;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.CloudStatus;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings;
import net.minecraft.world.phys.Vec3;

/**
 * SCENE=pokemon_center (ROADMAP 28.7, with Cobblemon): the Pokémon Center from the street, then Pokémon Center II over
 * it (the lodge and the garden behind), then inside: the nurse at the counter puts the player's hurt team in her
 * Healing Machine, which runs, and everyone comes out full.
 */
final class PokemonCenterScene {
	/** The blueprint's origin (its front, z = 0, faces north); the superflat ground is y = -61. */
	private static final BlockPos ORIGIN = new BlockPos(0, -60, 0);
	private static final BlockPos MACHINE = ORIGIN.offset(6, 1, 7);
	/** Pokémon Center II stands beside it, placed at the start so its chunks are drawn by the time it's filmed. */
	private static final BlockPos ORIGIN_2 = new BlockPos(34, -60, 0);
	private static final int HEAL = 210;

	private int tick;
	private volatile Villager nurse;
	private final List<Pokemon> team = new ArrayList<>();
	private volatile boolean healed;

	void tick(Minecraft mc, MinecraftServer server) {
		tick++;
		if (tick == 1) {
			mc.options.renderDistance().set(6);
			mc.options.cloudStatus().set(CloudStatus.OFF);
			mc.options.hideGui = true;
		}
		if (tick == 20) {
			server.execute(() -> {
				ServerLevel level = server.overworld();
				level.getGameRules().getRule(GameRules.RULE_DAYLIGHT).set(false, server);
				level.getGameRules().getRule(GameRules.RULE_DOMOBSPAWNING).set(false, server);
				level.setDayTime(5000);
				place(level, StarterBlueprints.POKEMON_CENTER, ORIGIN);
				place(level, StarterBlueprints.POKEMON_CENTER_2, ORIGIN_2);
				level.getEntitiesOfClass(net.minecraft.world.entity.monster.Slime.class, new net.minecraft.world.phys.AABB(ORIGIN).inflate(96))
					.forEach(net.minecraft.world.entity.Entity::discard);
				ScreenshotHarness.hoverLookingAt(player(server), new Vec3(-5.5, -54, -12), new Vec3(6.5, -57.5, 5));
			});
		}
		if (tick == 70) {
			ScreenshotHarness.shot(mc, "01_pokemon_center");
			server.execute(() -> {
				Showcase.check(server.overworld().getBlockEntity(MACHINE) instanceof HealingMachineBlockEntity, "the Pokémon Center's Healing Machine is on the counter");
				ScreenshotHarness.hoverLookingAt(player(server), new Vec3(56, -50, 34), new Vec3(40.5, -57, 13));
			});
		}
		if (tick == 170) {
			ScreenshotHarness.shot(mc, "02_pokemon_center_2");
			server.execute(() -> {
				Showcase.check(ScreenshotHarness.cobblemonBlock("pasture").getBlock()
					== server.overworld().getBlockState(ORIGIN_2.offset(6, 1, 22)).getBlock(), "Pokémon Center II's garden has its Pasture Block");
				setUpHealing(server);
			});
		}
		if (tick == HEAL) {
			mc.options.hideGui = false; // the nurse's words show over the hotbar
			server.execute(() -> {
				Nurses.resetCooldowns();
				Nurses.treat(player(server), nurse);
				HealingMachineBlockEntity machine = (HealingMachineBlockEntity) server.overworld().getBlockEntity(MACHINE);
				Showcase.check(machine != null && machine.isInUse(), "the nurse put the team in the Healing Machine");
			});
		}
		if (tick == HEAL + 14) {
			ScreenshotHarness.shot(mc, "03_healing");
		}
		if (tick > HEAL + 14 && tick % 5 == 0 && !healed) {
			server.execute(() -> {
				HealingMachineBlockEntity machine = (HealingMachineBlockEntity) server.overworld().getBlockEntity(MACHINE);
				healed = machine != null && !machine.isInUse() && team.stream().allMatch(Pokemon::isFullHealth);
			});
		}
		if (healed && tick > HEAL + 18) {
			ScreenshotHarness.shot(mc, "04_healed");
			Showcase.check(true, "every Pokémon came out of the machine full");
			healed = false;
			tick = -100000;
		}
		if (tick == -100000 + 20) {
			mc.stop();
		}
		if (tick == HEAL + 900) {
			Showcase.check(false, "the team was healed (gave up at the time limit)");
			ScreenshotHarness.shot(mc, "99_timeout");
			mc.stop();
		}
	}

	private static ServerPlayer player(MinecraftServer server) {
		return server.getPlayerList().getPlayers().get(0);
	}

	private static void place(ServerLevel level, StarterBlueprints.Entry entry, BlockPos origin) {
		level.getStructureManager().get(entry.id()).orElseThrow()
			.placeInWorld(level, origin, origin, new StructurePlaceSettings(), level.getRandom(), 2);
	}

	/** The nurse behind the counter at her machine, three hurt Pokémon in the player's party, the camera in the hall. */
	private void setUpHealing(MinecraftServer server) {
		ServerLevel level = server.overworld();
		nurse = EntityType.VILLAGER.spawn(level, MACHINE.south(), MobSpawnType.COMMAND);
		Jobs.employ(level, nurse, MACHINE, ModVillagers.HEALING_MACHINE_POI, ModVillagers.NURSE);
		nurse.setNoAi(true);
		nurse.setYRot(180);
		nurse.setYHeadRot(180);
		ServerPlayer player = player(server);
		for (String species : List.of("pikachu level=25", "squirtle level=22", "bulbasaur level=20")) {
			Pokemon pokemon = PokemonProperties.Companion.parse(species, " ", "=").create();
			Cobblemon.INSTANCE.getStorage().getParty(player).add(pokemon);
			pokemon.setCurrentHealth(Math.max(1, pokemon.getMaxHealth() / 5));
			team.add(pokemon);
		}
		ScreenshotHarness.hoverLookingAt(player, new Vec3(8.5, -57.3, 4.2), new Vec3(6.5, -58.6, 7.5));
	}
}
