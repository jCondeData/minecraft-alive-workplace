package io.github.jcondedata.aliveworkplace.devclient;

import io.github.jcondedata.aliveworkplace.blueprint.Blueprint;
import io.github.jcondedata.aliveworkplace.blueprint.BlueprintLibrary;
import io.github.jcondedata.aliveworkplace.blueprint.StarterBlueprints;
import io.github.jcondedata.aliveworkplace.furnish.FurnitureChoice;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import net.minecraft.client.CloudStatus;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

/**
 * SCENE=furnished_home (ROADMAP 37.4), filmed with -Pfurniture=true (the pack's furniture mods: Handcrafted, Beautify
 * and CobbleFurnies): Starter Cottage II furnished as this server would build it, then walked room by room: the front,
 * the dining corner (a table with two chairs, a shelf of cups, a picture, a clock), the bedroom corner (the fancy bed,
 * a nightstand, a lamp, a couch), the upstairs bedroom and sitting corner, and the same dining corner in the plain
 * vanilla cottage beside it. Checks every furniture spot holds the piece the server chose, and that pieces of at least
 * two furniture mods stand in it.
 */
final class FurnishedHomeScene {
	private static final ResourceLocation COTTAGE = StarterBlueprints.STARTER_COTTAGE_2.id();
	private static final BlockPos FURNISHED = new BlockPos(0, -60, 0);
	private static final BlockPos VANILLA = new BlockPos(24, -60, 0);

	/** The stills: a name, where the camera is and what it looks at (relative to a cottage's origin), which cottage. */
	private record View(String name, Vec3 eye, Vec3 at, BlockPos cottage) {
	}

	private static final List<View> VIEWS = List.of(
		new View("01_front", new Vec3(13, 7, -9), new Vec3(5.5, 4, 5), FURNISHED),
		new View("02_dining", new Vec3(7.5, 3.1, 4.2), new Vec3(2.5, 1.6, 5), FURNISHED),
		new View("03_bedroom", new Vec3(3.6, 3.1, 4.2), new Vec3(7, 1.4, 7.2), FURNISHED),
		new View("04_upstairs", new Vec3(7.5, 8.1, 4.5), new Vec3(4, 6.6, 6), FURNISHED),
		new View("05_sitting", new Vec3(4.5, 8.1, 6.8), new Vec3(6.5, 6.6, 2), FURNISHED),
		new View("06_vanilla_dining", new Vec3(7.5, 3.1, 4.2), new Vec3(2.5, 1.6, 5), VANILLA));
	private static final int FIRST = 80;
	private static final int EACH = 50;

	private int tick;
	private final List<String> wrong = java.util.Collections.synchronizedList(new ArrayList<>());
	private volatile String summary = "";

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
				place(level);
			});
		}
		int t = tick - FIRST;
		if (t >= 0 && t % EACH == 0 && t / EACH < VIEWS.size()) {
			View view = VIEWS.get(t / EACH);
			Vec3 base = Vec3.atLowerCornerOf(view.cottage());
			server.execute(() -> ScreenshotHarness.hoverLookingAt(server.getPlayerList().getPlayers().get(0), base.add(view.eye()), base.add(view.at())));
		}
		if (t >= 40 && (t - 40) % EACH == 0 && (t - 40) / EACH < VIEWS.size()) {
			ScreenshotHarness.shot(mc, VIEWS.get((t - 40) / EACH).name());
		}
		if (tick == FIRST + VIEWS.size() * EACH + 10) {
			Showcase.check(wrong.isEmpty(), wrong.isEmpty() ? summary : String.join("; ", wrong));
			mc.stop();
		}
	}

	/** The furnished cottage as a builder on this server would build it, and the vanilla one beside it. */
	private void place(ServerLevel level) {
		Blueprint base = BlueprintLibrary.get(level, COTTAGE).orElse(null);
		if (base == null) {
			wrong.add("no Starter Cottage II blueprint");
			return;
		}
		FurnitureChoice choice = FurnitureChoice.forNewBuild(COTTAGE);
		for (Blueprint.Entry e : choice.apply(base, COTTAGE).blocks()) {
			level.setBlock(FURNISHED.offset(e.pos()), e.state(), 2 | 16);
		}
		for (Blueprint.Entry e : base.blocks()) {
			level.setBlock(VANILLA.offset(e.pos()), e.state(), 2 | 16);
		}
		java.util.Set<String> mods = new java.util.TreeSet<>();
		int pieces = 0;
		for (FurnitureChoice.Placed placed : choice.spots().values()) {
			for (Map.Entry<BlockPos, BlockState> cell : placed.cells().entrySet()) {
				BlockState there = level.getBlockState(FURNISHED.offset(placed.pos()).offset(cell.getKey()));
				if (!there.is(cell.getValue().getBlock())) {
					wrong.add(placed.kind() + " at " + placed.pos().offset(cell.getKey()).toShortString() + " is "
						+ BuiltInRegistries.BLOCK.getKey(there.getBlock()) + ", not " + BuiltInRegistries.BLOCK.getKey(cell.getValue().getBlock()));
				}
			}
			mods.add(placed.mod());
			pieces++;
		}
		boolean modsHere = BuiltInRegistries.BLOCK.containsKey(ResourceLocation.parse("handcrafted:oak_chair"));
		if (modsHere && mods.size() < 2) {
			wrong.add("pieces of only " + mods + " in the furnished cottage (" + pieces + " pieces)");
		}
		if (!modsHere) {
			wrong.add("the furniture mods aren't loaded: film this scene with -Pfurniture=true");
		}
		summary = modsHere ? "Starter Cottage II furnished room by room: " + pieces + " pieces of " + String.join(", ", mods)
			+ " in its dining corner, bedrooms and sitting corner, every one where its spot is"
			: "no furniture mods loaded: the cottage is the vanilla build (run with -Pfurniture=true)";
	}
}
