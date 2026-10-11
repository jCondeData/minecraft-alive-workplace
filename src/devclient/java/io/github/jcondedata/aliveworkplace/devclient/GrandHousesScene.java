package io.github.jcondedata.aliveworkplace.devclient;

import io.github.jcondedata.aliveworkplace.blueprint.Blueprint;
import io.github.jcondedata.aliveworkplace.blueprint.BlueprintLibrary;
import io.github.jcondedata.aliveworkplace.blueprint.BlueprintStyles;
import io.github.jcondedata.aliveworkplace.blueprint.StarterBlueprints;
import io.github.jcondedata.aliveworkplace.people.HomeGrades;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.CloudStatus;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Vec3i;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.level.block.state.properties.BedPart;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * SCENE=grand_houses (ROADMAP 34.15): the Artisan's House (I, II) and the Burgher's Townhouse (I, II, III) as drawn,
 * then the Artisan's House and the Townhouse III in each of the five styles, placed in a row: a still of each from the
 * front (the street) and from behind, where the upgrades add their part. Checks each drawn tier's beds and grade (2
 * beds and grade I, 3 and II; the Townhouse 3 and II, 4 and III, 4 and III) and that every styled one is placed.
 */
final class GrandHousesScene {
	static final List<String> STYLES = List.of("stonework", "sandstone", "dark_oak", "cherry", "grand");
	private static final int SPACING = 32;
	private static final int FIRST = 60;
	private static final int EACH = 60;

	/** One piece shown: its blueprint and size, with the beds and grade a drawn tier must have (-1: not checked). */
	private record Piece(String name, ResourceLocation id, Vec3i size, int beds, int grade) {
	}

	private static final List<Piece> PIECES = pieces();

	private static List<Piece> pieces() {
		List<Piece> out = new ArrayList<>();
		out.add(new Piece("artisans_house", StarterBlueprints.ARTISANS_HOUSE.id(), StarterBlueprints.ARTISANS_HOUSE.size(), 2, 1));
		out.add(new Piece("artisans_house_2", StarterBlueprints.ARTISANS_HOUSE_2.id(), StarterBlueprints.ARTISANS_HOUSE_2.size(), 3, 2));
		out.add(new Piece("townhouse", StarterBlueprints.TOWNHOUSE.id(), StarterBlueprints.TOWNHOUSE.size(), 3, 2));
		out.add(new Piece("townhouse_2", StarterBlueprints.TOWNHOUSE_2.id(), StarterBlueprints.TOWNHOUSE_2.size(), 4, 3));
		out.add(new Piece("townhouse_3", StarterBlueprints.TOWNHOUSE_3.id(), StarterBlueprints.TOWNHOUSE_3.size(), 4, 3));
		for (String style : STYLES) {
			out.add(new Piece(style + "_artisans_house", BlueprintStyles.styled(StarterBlueprints.ARTISANS_HOUSE.id(), style),
				StarterBlueprints.ARTISANS_HOUSE.size(), -1, 1));
			out.add(new Piece(style + "_townhouse_3", BlueprintStyles.styled(StarterBlueprints.TOWNHOUSE_3.id(), style),
				StarterBlueprints.TOWNHOUSE_3.size(), -1, 3));
		}
		return List.copyOf(out);
	}

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
					place(level, PIECES.get(i), origin(i));
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
				? "the Artisan's House (I, II) and the Townhouse (I, II, III) stand with their beds and grades, and both in all five styles"
				: String.join("; ", wrong));
			mc.stop();
		}
	}

	/** Places a piece's blueprint (a styled one with its swaps, as {@link BlueprintLibrary} reads it) and checks it. */
	private void place(ServerLevel level, Piece piece, BlockPos origin) {
		Blueprint blueprint = BlueprintLibrary.get(level, piece.id()).orElse(null);
		if (blueprint == null) {
			wrong.add(piece.name() + " is missing");
			return;
		}
		for (Blueprint.Entry e : blueprint.blocks()) {
			level.setBlock(origin.offset(e.pos()), e.state(), 2 | 16);
		}
		int beds = (int) blueprint.blocks().stream()
			.filter(e -> e.state().getBlock() instanceof BedBlock && e.state().getValue(BedBlock.PART) == BedPart.HEAD)
			.filter(e -> level.getBlockState(origin.offset(e.pos())).getBlock() instanceof BedBlock).count();
		if (piece.beds() >= 0 && beds != piece.beds()) {
			wrong.add(piece.name() + " has " + beds + " beds, not " + piece.beds());
		}
		if (HomeGrades.grade(piece.id()) != piece.grade()) {
			wrong.add(piece.name() + " is grade " + HomeGrades.grade(piece.id()) + ", not " + piece.grade());
		}
	}

	private static BlockPos origin(int i) {
		return new BlockPos(i * SPACING, -60, 0);
	}

	/** The camera on piece {@code i}, from the front (the street, north) or from behind. */
	private static void look(MinecraftServer server, int i, boolean back) {
		Vec3i size = PIECES.get(i).size();
		Vec3 center = Vec3.atLowerCornerOf(origin(i)).add(size.getX() / 2.0, size.getY() * 0.35, size.getZ() / 2.0);
		double dist = Math.max(Math.max(size.getX(), size.getZ()), size.getY()) * 0.75 + 5;
		Vec3 eye = back ? center.add(-dist * 0.55, size.getY() * 0.3 + 3, dist) : center.add(dist * 0.55, size.getY() * 0.3 + 2, -dist);
		server.execute(() -> ScreenshotHarness.hoverLookingAt(server.getPlayerList().getPlayers().get(0), eye, center));
	}
}
