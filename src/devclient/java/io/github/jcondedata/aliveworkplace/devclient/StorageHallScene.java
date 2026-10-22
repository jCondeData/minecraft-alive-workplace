package io.github.jcondedata.aliveworkplace.devclient;

import io.github.jcondedata.aliveworkplace.blueprint.Blueprint;
import io.github.jcondedata.aliveworkplace.blueprint.BlueprintLibrary;
import io.github.jcondedata.aliveworkplace.blueprint.ModdedLibrary;
import io.github.jcondedata.aliveworkplace.blueprint.StarterBlueprints;
import io.github.jcondedata.aliveworkplace.furnish.Furnishings;
import io.github.jcondedata.aliveworkplace.furnish.FurnitureChoice;
import io.github.jcondedata.aliveworkplace.store.StorageNetworks;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.CloudStatus;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.phys.Vec3;

/**
 * SCENE=storage_hall (ROADMAP 37.17), filmed with -Pfurniture=true -Pcobblemon=true (Tom's Simple Storage comes with the
 * furniture mods): the Storage Hall, the Terminal Nook and the Warehouse from their Tom's copies, and the vanilla Storage
 * Hall beside them for comparison. Checks each Tom's copy is chosen, its connector joins the inventories drawn for it
 * (29, 6 and 60), and the vanilla hall has no block of Tom's.
 */
final class StorageHallScene {
	private record Site(ResourceLocation id, boolean toms, BlockPos origin, BlockPos connector, int members) {
	}

	private static final Site HALL = new Site(StarterBlueprints.STORAGE_HALL.id(), true, new BlockPos(0, -60, 0), new BlockPos(6, 1, 8), 29);
	private static final Site VANILLA_HALL = new Site(StarterBlueprints.STORAGE_HALL.id(), false, new BlockPos(-18, -60, 0), null, 0);
	private static final Site NOOK = new Site(StarterBlueprints.TERMINAL_NOOK.id(), true, new BlockPos(18, -60, 0), new BlockPos(4, 1, 6), 6);
	private static final Site WAREHOUSE = new Site(StarterBlueprints.WAREHOUSE.id(), true, new BlockPos(32, -60, 0), new BlockPos(9, 1, 6), 60);

	/** The stills (inside, the camera's feet on the floor: its eye 1.62 above): a name, where the camera is and what it looks at (relative to a building's origin), which building. */
	private record View(String name, Vec3 eye, Vec3 at, Site site) {
	}

	private static final List<View> VIEWS = List.of(
		new View("01_halls", new Vec3(-3, 10, -18), new Vec3(-3, 4, 6), HALL),
		new View("02_hall_counter", new Vec3(6.5, 1, 3.6), new Vec3(6.5, 1, 9), HALL),
		new View("03_hall_island", new Vec3(2.5, 1, 4.5), new Vec3(6.5, 1.5, 9), HALL),
		new View("04_nook_inside", new Vec3(4.5, 1, 3.5), new Vec3(4.5, 1.2, 6.5), NOOK),
		new View("05_warehouse", new Vec3(9.5, 8, -12), new Vec3(9.5, 4, 8), WAREHOUSE),
		new View("06_warehouse_inside", new Vec3(13.5, 1, 5.5), new Vec3(6, 1.5, 11), WAREHOUSE));
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
				Furnishings.ENABLED = true; // M37's switches (off on main until the milestone ships)
				ModdedLibrary.ENABLED = true;
				StorageNetworks.ENABLED = true;
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
		if (tick == FIRST + 20) {
			server.execute(() -> joined(server.overworld()));
		}
		if (tick == FIRST + VIEWS.size() * EACH + 10) {
			Showcase.check(wrong.isEmpty(), wrong.isEmpty() ? summary : String.join("; ", wrong));
			mc.stop();
		}
	}

	private void place(ServerLevel level) {
		for (Site site : List.of(HALL, VANILLA_HALL, NOOK, WAREHOUSE)) {
			if (site.toms() && !ModdedLibrary.complete(site.id())) {
				wrong.add(site.id() + "'s Tom's copy lacks " + ModdedLibrary.missing(site.id()) + ": film this scene with -Pfurniture=true -Pcobblemon=true");
				continue;
			}
			Blueprint base = BlueprintLibrary.get(level, site.id()).orElse(null);
			if (base == null) {
				wrong.add("no blueprint " + site.id());
				continue;
			}
			if (site.toms() && !FurnitureChoice.forNewBuild(site.id()).modded()) {
				wrong.add(site.id() + " isn't built from its Tom's copy with Tom's installed");
			}
			FurnitureChoice choice = FurnitureChoice.forNewBuild(site.id()).withLibrary(site.toms());
			int toms = 0;
			for (Blueprint.Entry e : choice.apply(base, site.id()).blocks()) {
				level.setBlock(site.origin().offset(e.pos()), e.state(), 2 | 16);
				toms += BuiltInRegistries.BLOCK.getKey(e.state().getBlock()).getNamespace().equals("toms_storage") ? 1 : 0;
			}
			if (!site.toms() && toms > 0) {
				wrong.add("the vanilla Storage Hall has " + toms + " blocks of Tom's");
			}
		}
	}

	private void joined(ServerLevel level) {
		List<String> counts = new ArrayList<>();
		for (Site site : List.of(HALL, NOOK, WAREHOUSE)) {
			int n = StorageNetworks.members(level, site.origin().offset(site.connector())).size();
			counts.add(n + "");
			if (n != site.members()) {
				wrong.add(site.id().getPath() + "'s connector joins " + n + " inventories, wanted " + site.members());
			}
		}
		summary = "the Storage Hall, the Terminal Nook and the Warehouse built of Tom's Simple Storage, their connectors joining "
			+ String.join(", ", counts) + " chests, barrels and cabinets, and the vanilla Storage Hall beside them";
	}
}
