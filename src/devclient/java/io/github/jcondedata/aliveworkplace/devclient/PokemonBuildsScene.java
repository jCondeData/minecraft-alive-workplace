package io.github.jcondedata.aliveworkplace.devclient;

import io.github.jcondedata.aliveworkplace.blueprint.StarterBlueprints;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.CloudStatus;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings;
import net.minecraft.world.phys.Vec3;

/**
 * SCENE=pokemon_builds (ROADMAP 28.13, with Cobblemon): the Pokémon jobs' builds, both tiers of each, placed in a row:
 * a still of each from the front (the street) and from behind, where the upgrades add their part. Checks that each
 * build placed its job block where the job's villager takes it.
 */
final class PokemonBuildsScene {
	private static final int SPACING = 40;
	private static final int FIRST = 60;
	private static final int EACH = 60;

	/** Each build's job block (template spot, block id): what the scene checks once they are placed. */
	private record JobBlock(StarterBlueprints.Entry entry, BlockPos spot, String block) {
	}

	private static final List<JobBlock> JOB_BLOCKS = List.of(
		new JobBlock(StarterBlueprints.CAMP_KITCHEN, new BlockPos(6, 1, 5), "cobblemon:campfire"),
		new JobBlock(StarterBlueprints.CAMP_KITCHEN_2, new BlockPos(6, 1, 5), "cobblemon:campfire"),
		new JobBlock(StarterBlueprints.BERRY_NURSERY, new BlockPos(5, 1, 8), "minecraft:composter"),
		new JobBlock(StarterBlueprints.BERRY_NURSERY_2, new BlockPos(5, 1, 8), "minecraft:composter"),
		new JobBlock(StarterBlueprints.DAYCARE, new BlockPos(7, 1, 12), "cobblemon:pasture"),
		new JobBlock(StarterBlueprints.DAYCARE_2, new BlockPos(7, 1, 12), "cobblemon:pasture"),
		new JobBlock(StarterBlueprints.HABITAT_GARDEN, new BlockPos(14, 1, 7), "cobblemon:pasture"),
		new JobBlock(StarterBlueprints.HABITAT_GARDEN_2, new BlockPos(14, 1, 7), "cobblemon:pasture"),
		new JobBlock(StarterBlueprints.GEM_GROTTO, new BlockPos(4, 1, 2), "minecraft:stonecutter"),
		new JobBlock(StarterBlueprints.GEM_GROTTO_2, new BlockPos(4, 1, 2), "minecraft:stonecutter"));

	private int tick;
	private final List<String> missing = java.util.Collections.synchronizedList(new ArrayList<>());

	void tick(Minecraft mc, MinecraftServer server) {
		tick++;
		List<StarterBlueprints.Entry> all = StarterBlueprints.JOB_BUILDS;
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
				level.setDayTime(3000);
				for (int i = 0; i < all.size(); i++) {
					BlockPos origin = origin(i);
					level.getStructureManager().get(all.get(i).id()).orElseThrow()
						.placeInWorld(level, origin, origin, new StructurePlaceSettings(), level.getRandom(), 2);
				}
				for (JobBlock job : JOB_BLOCKS) {
					BlockPos at = origin(all.indexOf(job.entry())).offset(job.spot());
					if (!BuiltInRegistries.BLOCK.getKey(level.getBlockState(at).getBlock()).toString().equals(job.block())) {
						missing.add(job.entry().id().getPath() + " (" + job.block() + ")");
					}
				}
			});
		}
		if (tick % 20 == 0) {
			// Slimes from the superflat world's slime chunks hop into the shots.
			server.execute(() -> server.overworld().getEntitiesOfClass(net.minecraft.world.entity.monster.Slime.class,
				new net.minecraft.world.phys.AABB(origin(0)).inflate(SPACING * all.size())).forEach(net.minecraft.world.entity.Entity::discard));
		}
		int front = tick - FIRST;
		if (front >= 0 && front % EACH == 0 && front / EACH < all.size()) {
			int i = front / EACH;
			look(server, i, false);
		}
		if (front >= 40 && (front - 40) % EACH == 0 && (front - 40) / EACH < all.size()) {
			int i = (front - 40) / EACH;
			ScreenshotHarness.shot(mc, String.format("%02d_%s", i * 2 + 10, all.get(i).id().getPath()));
			look(server, i, true);
		}
		if (front >= 55 && (front - 55) % EACH == 0 && (front - 55) / EACH < all.size()) {
			int i = (front - 55) / EACH;
			ScreenshotHarness.shot(mc, String.format("%02d_%s_back", i * 2 + 11, all.get(i).id().getPath()));
		}
		if (tick == FIRST + all.size() * EACH + 10) {
			Showcase.check(missing.isEmpty(), missing.isEmpty() ? "all " + all.size() + " builds stand with their job blocks"
				: "job blocks missing: " + String.join(", ", missing));
			mc.stop();
		}
	}

	private static BlockPos origin(int i) {
		return new BlockPos(i * SPACING, -60, 0);
	}

	/** The camera on build {@code i}, from the front (the street, north) or from behind. */
	private static void look(MinecraftServer server, int i, boolean back) {
		StarterBlueprints.Entry e = StarterBlueprints.JOB_BUILDS.get(i);
		Vec3 center = Vec3.atLowerCornerOf(origin(i)).add(e.size().getX() / 2.0, e.size().getY() * 0.3, e.size().getZ() / 2.0);
		double dist = Math.max(Math.max(e.size().getX(), e.size().getZ()), e.size().getY()) * 0.75 + 5;
		Vec3 eye = back ? center.add(-dist * 0.55, e.size().getY() * 0.35 + 3, dist) : center.add(dist * 0.55, e.size().getY() * 0.35 + 2, -dist);
		server.execute(() -> ScreenshotHarness.hoverLookingAt(server.getPlayerList().getPlayers().get(0), eye, center));
	}
}
