package io.github.jcondedata.aliveworkplace.devclient;

import io.github.jcondedata.aliveworkplace.blueprint.BlueprintData;
import io.github.jcondedata.aliveworkplace.blueprint.StarterBlueprints;
import io.github.jcondedata.aliveworkplace.hall.Arenas;
import io.github.jcondedata.aliveworkplace.registry.ModVillagers;
import java.util.List;
import net.minecraft.client.CloudStatus;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.StairBlock;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings;
import net.minecraft.world.phys.Vec3;

/**
 * SCENE=arena (ROADMAP 28.16, with Cobblemon): the Arena's three tiers side by side, each from the front (the way in,
 * north) and from above: the ring with its lines and centre circle, the trainer's boxes, the benches; Arena II's stands,
 * gate arch and fair lane; Arena III's covered grandstand, trainers' rooms and champion's pole. Two Trainer Leaders face
 * each other from Arena III's boxes. Checks: every tier's spots (Arenas.at) hold what they should, and Arena III's
 * rooms have their Healing Machines.
 */
final class ArenaScene {
	/** The tiers' origins (the front, z = 0, faces north); the superflat ground is y = -61. */
	private static final List<BlockPos> ORIGINS = List.of(new BlockPos(0, -60, 0), new BlockPos(40, -60, 0), new BlockPos(80, -60, 0));
	private static final int GAP = 45;

	private int tick;

	void tick(Minecraft mc, MinecraftServer server) {
		tick++;
		if (tick == 1) {
			mc.options.renderDistance().set(8);
			mc.options.cloudStatus().set(CloudStatus.OFF);
			mc.options.hideGui = true;
		}
		if (tick == 20) {
			server.execute(() -> {
				ServerLevel level = server.overworld();
				level.getGameRules().getRule(GameRules.RULE_DAYLIGHT).set(false, server);
				level.getGameRules().getRule(GameRules.RULE_DOMOBSPAWNING).set(false, server);
				level.setDayTime(5000);
				for (int i = 0; i < 3; i++) {
					BlockPos origin = ORIGINS.get(i);
					level.getStructureManager().get(StarterBlueprints.ARENAS.get(i).id()).orElseThrow()
						.placeInWorld(level, origin, origin, new StructurePlaceSettings(), level.getRandom(), 2);
				}
				level.getEntitiesOfClass(net.minecraft.world.entity.monster.Slime.class, new net.minecraft.world.phys.AABB(ORIGINS.get(1)).inflate(128))
					.forEach(net.minecraft.world.entity.Entity::discard);
				Arenas.Arena top = arena(level, 2);
				for (int b = 0; b < 2; b++) { // two Leaders face each other across Arena III's ring
					Villager leader = EntityType.VILLAGER.spawn(level, top.boxes().get(b), MobSpawnType.COMMAND);
					if (leader != null) {
						leader.setVillagerData(leader.getVillagerData().setProfession(ModVillagers.TRAINER_LEADER).setLevel(4));
						leader.setNoAi(true);
						float yaw = b == 0 ? -90 : 90;
						leader.setYRot(yaw);
						leader.setYHeadRot(yaw);
						leader.setYBodyRot(yaw);
					}
				}
				check(level);
				front(server, 0);
			});
		}
		for (int i = 0; i < 3; i++) {
			int frontAt = 80 + i * 2 * GAP;
			int aboveAt = frontAt + GAP;
			String name = i == 0 ? "arena" : "arena_" + (i + 1);
			int tier = i;
			if (tick == frontAt) {
				ScreenshotHarness.shot(mc, String.format("%02d_%s_front", 2 * i + 1, name));
				server.execute(() -> above(server, tier));
			}
			if (tick == aboveAt) {
				ScreenshotHarness.shot(mc, String.format("%02d_%s_above", 2 * i + 2, name));
				if (tier < 2) {
					server.execute(() -> front(server, tier + 1));
				}
			}
		}
		if (tick == 80 + 5 * GAP + 20) {
			mc.stop();
		}
	}

	private static Arenas.Arena arena(ServerLevel level, int i) {
		return Arenas.at(StarterBlueprints.ARENAS.get(i).id(),
			new BlueprintData.Placement(level.dimension().location(), ORIGINS.get(i), Rotation.NONE, Mirror.NONE));
	}

	/** Each tier's spots hold what they should; Arena III has a Healing Machine in each trainers' room. */
	private static void check(ServerLevel level) {
		for (int i = 0; i < 3; i++) {
			Arenas.Arena a = arena(level, i);
			String name = StarterBlueprints.ARENAS.get(i).id().getPath();
			Showcase.check(level.getBlockState(a.ring().below()).is(Blocks.WHITE_GLAZED_TERRACOTTA), name + ": the ring's centre spot is where Arenas says");
			Showcase.check(a.boxes().stream().allMatch(b -> level.getBlockState(b.below()).is(Blocks.POLISHED_ANDESITE)), name + ": both trainer's boxes are on their daises");
			long seats = a.seats().stream().filter(s -> level.getBlockState(s).getBlock() instanceof StairBlock).count();
			Showcase.check(seats == a.seats().size() && seats == new int[]{12, 30, 52}[i], name + ": " + seats + " seats");
		}
		BlockPos o = ORIGINS.get(2);
		long machines = List.of(o.offset(3, 1, 19), o.offset(21, 1, 19)).stream()
			.filter(p -> BuiltInRegistries.BLOCK.getKey(level.getBlockState(p).getBlock()).equals(ModVillagers.HEALING_MACHINE_BLOCK)).count();
		Showcase.check(machines == 2, "Arena III: a Healing Machine in each trainers' room (" + machines + ")");
	}

	/** The camera before the way in, a little up, looking over the ring to the back. */
	private static void front(MinecraftServer server, int i) {
		BlockPos o = ORIGINS.get(i);
		ScreenshotHarness.hoverLookingAt(player(server), new Vec3(o.getX() + 12.5, -51, o.getZ() - 13), new Vec3(o.getX() + 12.5, -58, o.getZ() + 11));
	}

	/** The camera high over the front, looking down on the whole build. */
	private static void above(MinecraftServer server, int i) {
		BlockPos o = ORIGINS.get(i);
		ScreenshotHarness.hoverLookingAt(player(server), new Vec3(o.getX() + 12.5, -26, o.getZ() - 3), new Vec3(o.getX() + 12.5, -60, o.getZ() + 14));
	}

	private static ServerPlayer player(MinecraftServer server) {
		return server.getPlayerList().getPlayers().get(0);
	}
}
