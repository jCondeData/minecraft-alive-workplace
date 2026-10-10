package io.github.jcondedata.aliveworkplace.devclient;

import io.github.jcondedata.aliveworkplace.AliveWorkplace;
import io.github.jcondedata.aliveworkplace.blueprint.StarterBlueprints;
import io.github.jcondedata.aliveworkplace.registry.ModVillagers;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.CloudStatus;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Vec3i;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.npc.VillagerProfession;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.levelgen.structure.templatesystem.BlockIgnoreProcessor;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * SCENE=luxury_workshops (ROADMAP 34.13): the Winery and the Tailor's Shop, both tiers of each, placed in a row, then
 * the two as villages grow them (the plains houses, each with its villager already in the job): a still of each from
 * the front (the street) and from behind, where the upgrades add their part. Checks that each build has its job block
 * where its worker takes it (the cauldron vat, the loom), that no Winery has a barrel, and that each village house
 * came with its Vintner or its Tailor.
 */
final class LuxuryWorkshopsScene {
	private static final int SPACING = 40;
	private static final int FIRST = 60;
	private static final int EACH = 60;

	/** One thing shown: a structure, its size, where its job block is, and the job of the villager it comes with (village houses). */
	private record Piece(String name, ResourceLocation template, Vec3i size, BlockPos spot, Block block, VillagerProfession worker) {
	}

	private static Piece build(StarterBlueprints.Entry entry, BlockPos spot, Block block) {
		return new Piece(entry.id().getPath(), entry.id(), entry.size(), spot, block, null);
	}

	private static Piece house(String name, Block block, VillagerProfession worker) {
		return new Piece("village_" + name, AliveWorkplace.id("village/plains_" + name), new Vec3i(9, 10, 10), new BlockPos(2, 1, 7), block, worker);
	}

	private static final List<Piece> PIECES = List.of(
		build(StarterBlueprints.WINERY, new BlockPos(7, 4, 4), Blocks.CAULDRON),
		build(StarterBlueprints.WINERY_2, new BlockPos(7, 4, 4), Blocks.CAULDRON),
		build(StarterBlueprints.TAILORS_SHOP, new BlockPos(2, 1, 5), Blocks.LOOM),
		build(StarterBlueprints.TAILORS_SHOP_2, new BlockPos(2, 1, 5), Blocks.LOOM),
		house("winery", Blocks.CAULDRON, ModVillagers.VINTNER),
		house("tailors_shop", Blocks.LOOM, ModVillagers.TAILOR));

	private int tick;
	private final List<String> wrong = java.util.Collections.synchronizedList(new ArrayList<>());

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
				level.setDayTime(3000);
				for (int i = 0; i < PIECES.size(); i++) {
					Piece piece = PIECES.get(i);
					BlockPos origin = origin(i);
					// A village house as a village places it: its pool element skips the template's air and structure voids.
					StructurePlaceSettings settings = piece.worker() == null ? new StructurePlaceSettings()
						: new StructurePlaceSettings().setFinalizeEntities(true).addProcessor(BlockIgnoreProcessor.STRUCTURE_AND_AIR);
					level.getStructureManager().get(piece.template()).orElseThrow().placeInWorld(level, origin, origin, settings, level.getRandom(), 2);
					if (!level.getBlockState(origin.offset(piece.spot())).is(piece.block())) {
						wrong.add(piece.name() + " has no " + piece.block().getName().getString() + " at its worker's place");
					}
					AABB box = new AABB(origin).expandTowards(piece.size().getX(), piece.size().getY(), piece.size().getZ());
					if (piece.name().startsWith("winery") && BlockPos.betweenClosedStream(origin, origin.offset(piece.size()))
						.anyMatch(p -> level.getBlockState(p).is(Blocks.BARREL))) {
						wrong.add(piece.name() + " has a barrel (the fisherman's job block)");
					}
					if (piece.worker() != null) {
						List<Villager> villagers = level.getEntitiesOfClass(Villager.class, box);
						if (villagers.size() != 1 || villagers.get(0).getVillagerData().getProfession() != piece.worker()) {
							wrong.add(piece.name() + " came with " + villagers.stream().map(v -> v.getVillagerData().getProfession().name()).toList());
						}
					}
				}
			});
		}
		if (tick % 20 == 0) {
			// Slimes from the superflat world's slime chunks hop into the shots.
			server.execute(() -> server.overworld().getEntitiesOfClass(net.minecraft.world.entity.monster.Slime.class,
				new AABB(origin(0)).inflate(SPACING * PIECES.size())).forEach(net.minecraft.world.entity.Entity::discard));
		}
		int front = tick - FIRST;
		if (front >= 0 && front % EACH == 0 && front / EACH < PIECES.size()) {
			look(server, front / EACH, false);
		}
		if (front >= 40 && (front - 40) % EACH == 0 && (front - 40) / EACH < PIECES.size()) {
			int i = (front - 40) / EACH;
			ScreenshotHarness.shot(mc, String.format("%02d_%s", i * 2 + 10, PIECES.get(i).name()));
			look(server, i, true);
		}
		if (front >= 55 && (front - 55) % EACH == 0 && (front - 55) / EACH < PIECES.size()) {
			int i = (front - 55) / EACH;
			ScreenshotHarness.shot(mc, String.format("%02d_%s_back", i * 2 + 11, PIECES.get(i).name()));
		}
		if (tick == FIRST + PIECES.size() * EACH + 10) {
			Showcase.check(wrong.isEmpty(), wrong.isEmpty()
				? "the Winery and the Tailor's Shop stand in both tiers with the vat and the loom, and the village's two came with their Vintner and Tailor"
				: String.join("; ", wrong));
			mc.stop();
		}
	}

	private static BlockPos origin(int i) {
		return new BlockPos(i * SPACING, -60, 0);
	}

	/** The camera on piece {@code i}, from the front (the street, north) or from behind. */
	private static void look(MinecraftServer server, int i, boolean back) {
		Vec3i size = PIECES.get(i).size();
		Vec3 center = Vec3.atLowerCornerOf(origin(i)).add(size.getX() / 2.0, size.getY() * 0.3, size.getZ() / 2.0);
		double dist = Math.max(Math.max(size.getX(), size.getZ()), size.getY()) * 0.75 + 5;
		Vec3 eye = back ? center.add(-dist * 0.55, size.getY() * 0.35 + 3, dist) : center.add(dist * 0.55, size.getY() * 0.35 + 2, -dist);
		server.execute(() -> ScreenshotHarness.hoverLookingAt(server.getPlayerList().getPlayers().get(0), eye, center));
	}
}
