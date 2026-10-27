package io.github.jcondedata.aliveworkplace.devclient;

import io.github.jcondedata.aliveworkplace.AliveWorkplace;
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
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BedPart;
import net.minecraft.world.phys.Vec3;

/**
 * SCENE=furnished_home (ROADMAP 37.4), filmed with -Pfurniture=true (the pack's furniture mods: Handcrafted, Beautify
 * and CobbleFurnies): Starter Cottage II furnished as this server would build it, then walked room by room: the front,
 * the dining corner (a table with two chairs, a shelf of cups, a picture, a clock), the bedroom corner (the fancy bed,
 * a nightstand, a lamp, a couch), the upstairs bedroom and sitting corner, and the same dining corner in the plain
 * vanilla cottage beside it. Checks every furniture spot holds the piece the server chose, and that pieces of at least
 * two furniture mods stand in it. Then (B260) the owner's Stone House, furnished the same way: the way in from its door,
 * clear to the ladder, and its attic bedroom (the bed's head to the gable, a nightstand, a chest at its foot, a lamp
 * hung from the ridge); checks nothing stands in the door's way and every piece has what it stands on. And (B639) the
 * Clinic: its ward of four white beds, heads to the back wall, and its ceiling closed where the roof's stairs meet at the
 * ridge; checks the beds are whole and nothing is open under either ridge.
 */
final class FurnishedHomeScene {
	private static final ResourceLocation COTTAGE = StarterBlueprints.STARTER_COTTAGE_2.id();
	private static final BlockPos FURNISHED = new BlockPos(0, -60, 0);
	private static final BlockPos VANILLA = new BlockPos(24, -60, 0);
	private static final ResourceLocation STONE_HOUSE = StarterBlueprints.STONE_HOUSE.id();
	private static final BlockPos STONE = new BlockPos(48, -60, 0);
	private static final ResourceLocation TERRACE = AliveWorkplace.id("terrace");
	private static final BlockPos TERRACE_AT = new BlockPos(72, -60, 0);
	private static final ResourceLocation CLINIC = StarterBlueprints.CLINIC.id();
	private static final BlockPos CLINIC_AT = new BlockPos(96, -60, 0);

	/** The stills: a name, where the camera is and what it looks at (relative to a cottage's origin), which cottage. */
	private record View(String name, Vec3 eye, Vec3 at, BlockPos cottage) {
	}

	private static final List<View> VIEWS = List.of(
		new View("01_front", new Vec3(13, 7, -9), new Vec3(5.5, 4, 5), FURNISHED),
		new View("02_dining", new Vec3(7.5, 3.1, 4.2), new Vec3(2.5, 1.6, 5), FURNISHED),
		new View("03_bedroom", new Vec3(3.6, 3.1, 4.2), new Vec3(7, 1.4, 7.2), FURNISHED),
		new View("04_upstairs", new Vec3(7.5, 8.1, 4.5), new Vec3(4, 6.6, 6), FURNISHED),
		new View("05_sitting", new Vec3(4.5, 8.1, 6.8), new Vec3(6.5, 6.6, 2), FURNISHED),
		new View("06_vanilla_dining", new Vec3(7.5, 3.1, 4.2), new Vec3(2.5, 1.6, 5), VANILLA),
		new View("07_stone_doorway", new Vec3(6.5, 3.4, 5.5), new Vec3(5.5, 1.2, 1.5), STONE),
		new View("08_stone_attic", new Vec3(7.5, 7.7, 4.5), new Vec3(2.5, 6.4, 4.5), STONE),
		new View("09_terrace_west", new Vec3(2.6, 3.2, 2.3), new Vec3(4.5, 1.4, 5.5), TERRACE_AT),
		new View("10_terrace_east", new Vec3(10.4, 3.2, 2.3), new Vec3(7.5, 1.4, 5.5), TERRACE_AT),
		new View("11_clinic_ward", new Vec3(6.5, 3.6, 3.2), new Vec3(6.5, 1.0, 9.0), CLINIC_AT),
		new View("12_clinic_ceiling", new Vec3(2.5, 2.2, 7.5), new Vec3(10.5, 8.0, 8.5), CLINIC_AT));
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

	/**
	 * The Stone House furnished as a builder here would build it (B260): its door's way in (two blocks, feet and head)
	 * is open, and every piece the server chose stands on, against or under a block as its kind needs.
	 */
	private void placeStoneHouse(ServerLevel level) {
		Blueprint base = BlueprintLibrary.get(level, STONE_HOUSE).orElse(null);
		if (base == null) {
			wrong.add("no Stone House blueprint");
			return;
		}
		FurnitureChoice choice = FurnitureChoice.forNewBuild(STONE_HOUSE);
		for (Blueprint.Entry e : choice.apply(base, STONE_HOUSE).blocks()) {
			level.setBlock(STONE.offset(e.pos()), e.state(), 2 | 16);
		}
		for (int z = 2; z <= 3; z++) {
			for (int y = 1; y <= 2; y++) {
				BlockState way = level.getBlockState(STONE.offset(5, y, z));
				if (!way.isAir()) {
					wrong.add("the Stone House's way in is blocked at 5," + y + "," + z + " by " + BuiltInRegistries.BLOCK.getKey(way.getBlock()));
				}
			}
		}
		for (FurnitureChoice.Placed placed : choice.spots().values()) {
			BlockPos at = STONE.offset(placed.pos());
			boolean held = !level.getBlockState(at.below()).isAir() || placed.cells().size() > 1
				|| java.util.Arrays.stream(net.minecraft.core.Direction.values()).anyMatch(d -> !level.getBlockState(at.relative(d)).isAir());
			if (!held) {
				wrong.add("the Stone House's " + placed.kind() + " at " + placed.pos().toShortString() + " floats");
			}
		}
	}

	/**
	 * The Terrace as a builder here would build it (B281): both homes' kitchens get the mods' table and chair (the east
	 * one's table stands against the party wall), and each kitchen has its own lantern.
	 */
	private void placeTerrace(ServerLevel level) {
		Blueprint base = BlueprintLibrary.get(level, TERRACE).orElse(null);
		if (base == null) {
			wrong.add("no Terrace blueprint");
			return;
		}
		FurnitureChoice choice = FurnitureChoice.forNewBuild(TERRACE);
		for (Blueprint.Entry e : choice.apply(base, TERRACE).blocks()) {
			level.setBlock(TERRACE_AT.offset(e.pos()), e.state(), 2 | 16);
		}
		boolean modsHere = BuiltInRegistries.BLOCK.containsKey(ResourceLocation.parse("handcrafted:oak_chair"));
		for (boolean east : new boolean[] {false, true}) {
			long modded = choice.spots().values().stream().filter(p -> p.pos().getY() == 1 && (p.pos().getX() > 6) == east).count();
			if (modsHere && modded < 2) {
				wrong.add("the Terrace's " + (east ? "east" : "west") + " kitchen has " + modded + " modded pieces");
			}
		}
	}

	/**
	 * The Clinic as a builder here would build it (B639): its ward's beds are whole beds (not cots of slab and carpet), and
	 * no cell under its two roofs' ridges is open.
	 */
	private void placeClinic(ServerLevel level) {
		Blueprint base = BlueprintLibrary.get(level, CLINIC).orElse(null);
		if (base == null) {
			wrong.add("no Clinic blueprint");
			return;
		}
		FurnitureChoice choice = FurnitureChoice.forNewBuild(CLINIC);
		for (Blueprint.Entry e : choice.apply(base, CLINIC).blocks()) {
			level.setBlock(CLINIC_AT.offset(e.pos()), e.state(), 2 | 16);
		}
		long beds = base.blocks().stream().filter(e -> e.state().getBlock() instanceof BedBlock && e.state().getValue(BedBlock.PART) == BedPart.HEAD).count();
		if (beds < 4) {
			wrong.add("the Clinic's ward has " + beds + " beds");
		}
		for (int x = 1; x <= 11; x++) {
			if (level.getBlockState(CLINIC_AT.offset(x, 8, 8)).isAir()) {
				wrong.add("the Clinic's ward ceiling is open under the ridge at " + x + ",8,8");
			}
		}
		for (int z = 2; z <= 6; z++) {
			if (level.getBlockState(CLINIC_AT.offset(6, 9, z)).isAir()) {
				wrong.add("the Clinic's front ceiling is open under the ridge at 6,9," + z);
			}
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
		placeStoneHouse(level);
		placeTerrace(level);
		placeClinic(level);
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
			+ " in its dining corner, bedrooms and sitting corner, every one where its spot is; the Stone House's way in clear and its attic a bedroom; the Clinic's ward has "
				+ "4 white beds and a closed ceiling"
			: "no furniture mods loaded: the cottage is the vanilla build (run with -Pfurniture=true)";
	}
}
