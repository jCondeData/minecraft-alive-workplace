package io.github.jcondedata.aliveworkplace.devclient;

import io.github.jcondedata.aliveworkplace.blueprint.Blueprint;
import io.github.jcondedata.aliveworkplace.blueprint.BlueprintLibrary;
import io.github.jcondedata.aliveworkplace.blueprint.StarterBlueprints;
import io.github.jcondedata.aliveworkplace.build.SupplyContainers;
import io.github.jcondedata.aliveworkplace.furnish.FurnitureChoice;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import net.minecraft.client.CloudStatus;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.phys.Vec3;

/**
 * SCENE=furnished_workplaces (ROADMAP 37.5), filmed with -Pfurniture=true (the pack's furniture mods) and Cobblemon:
 * the Supply Shop, the Library, the Schoolhouse and the Healing Center furnished as this server would build them, side
 * by side, then walked inside: the shop's counters, shelf, picture and hanging pots, the
 * Library's desks, the Schoolhouse's benches and Poké Ball desk, the Healing Center's waiting benches and desk. Checks
 * every furniture spot holds the piece the server chose, pieces of at least two mods stand there, and each building
 * keeps exactly its vanilla chests and barrels as storage (no furniture piece counted as a chest).
 */
final class FurnishedWorkplacesScene {
	private record Site(ResourceLocation id, BlockPos origin) {
	}

	private static final Site SHOP = new Site(StarterBlueprints.SUPPLY_SHOP.id(), new BlockPos(0, -60, 0));
	private static final Site LIBRARY = new Site(StarterBlueprints.LIBRARY.id(), new BlockPos(20, -60, 0));
	private static final Site SCHOOL = new Site(StarterBlueprints.SCHOOLHOUSE.id(), new BlockPos(40, -60, 0));
	private static final Site CLINIC = new Site(StarterBlueprints.HEALING_CENTER.id(), new BlockPos(60, -60, 0));
	private static final List<Site> SITES = List.of(SHOP, LIBRARY, SCHOOL, CLINIC);

	/** The stills: a name, where the camera is and what it looks at (relative to a building's origin), which building. */
	private record View(String name, Vec3 eye, Vec3 at, Site site) {
	}

	private static final List<View> VIEWS = List.of(
		new View("01_shop_counter", new Vec3(5.5, 2.9, 3.2), new Vec3(5.5, 2.2, 7), SHOP),
		new View("02_library_desks", new Vec3(6.5, 3.6, 3.4), new Vec3(6, 1.5, 7), LIBRARY),
		new View("03_schoolroom", new Vec3(5.5, 3.2, 3.2), new Vec3(5.5, 1.5, 8), SCHOOL),
		new View("04_clinic_benches", new Vec3(7.5, 2.7, 3.2), new Vec3(2.5, 1.3, 6), CLINIC));
	private static final int FIRST = 80;
	private static final int EACH = 50;

	private int tick;
	private final List<String> wrong = java.util.Collections.synchronizedList(new ArrayList<>());
	private volatile String summary = "";

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
				place(level);
			});
		}
		int t = tick - FIRST;
		if (t >= 0 && t % EACH == 0 && t / EACH < VIEWS.size()) {
			View view = VIEWS.get(t / EACH);
			Vec3 base = Vec3.atLowerCornerOf(view.site().origin());
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

	/** Each workplace as a builder on this server would build it. */
	private void place(ServerLevel level) {
		Set<String> mods = new TreeSet<>();
		int pieces = 0;
		for (Site site : SITES) {
			Blueprint base = BlueprintLibrary.get(level, site.id()).orElse(null);
			if (base == null) {
				wrong.add("no blueprint " + site.id());
				continue;
			}
			FurnitureChoice choice = FurnitureChoice.forNewBuild(site.id());
			for (Blueprint.Entry e : choice.apply(base, site.id()).blocks()) {
				level.setBlock(site.origin().offset(e.pos()), e.state(), 2 | 16);
			}
			for (FurnitureChoice.Placed placed : choice.spots().values()) {
				for (Map.Entry<BlockPos, BlockState> cell : placed.cells().entrySet()) {
					BlockState there = level.getBlockState(site.origin().offset(placed.pos()).offset(cell.getKey()));
					if (!there.is(cell.getValue().getBlock())) {
						wrong.add(site.id().getPath() + " " + placed.kind() + " at " + placed.pos().offset(cell.getKey()).toShortString() + " is "
							+ BuiltInRegistries.BLOCK.getKey(there.getBlock()) + ", not " + BuiltInRegistries.BLOCK.getKey(cell.getValue().getBlock()));
					}
				}
				mods.add(placed.mod());
				pieces++;
			}
		}
		storage(level);
		boolean modsHere = BuiltInRegistries.BLOCK.containsKey(ResourceLocation.parse("handcrafted:oak_desk"));
		if (modsHere && mods.size() < 2) {
			wrong.add("pieces of only " + mods + " in the workplaces (" + pieces + " pieces)");
		}
		if (!modsHere) {
			wrong.add("the furniture mods aren't loaded: film this scene with -Pfurniture=true");
		}
		summary = "the Supply Shop, Library, Schoolhouse and Healing Center furnished: " + pieces + " pieces of " + String.join(", ", mods)
			+ ", every one where its spot is, and each keeps only its own chests and barrels as storage";
	}

	/** Each building's storage is its vanilla template's: the Shop's 8 barrels, the Healing Center's chest, none in the others. */
	private void storage(ServerLevel level) {
		Map<Site, Integer> expected = Map.of(SHOP, 8, LIBRARY, 0, SCHOOL, 0, CLINIC, 1);
		for (Site site : SITES) {
			Blueprint base = BlueprintLibrary.get(level, site.id()).orElse(null);
			if (base == null) {
				continue;
			}
			BoundingBox box = BoundingBox.fromCorners(site.origin(), site.origin().offset(base.size()).offset(-1, -1, -1));
			long found = SupplyContainers.inside(level, box).stream()
				.filter(p -> !Set.of("aliveworkplace", "cobblemon").contains(BuiltInRegistries.BLOCK.getKey(level.getBlockState(p).getBlock()).getNamespace()))
				.count(); // the job blocks keep their own ledgers
			if (found != expected.get(site)) {
				wrong.add(site.id().getPath() + " has " + found + " stores, not " + expected.get(site));
			}
		}
	}
}
