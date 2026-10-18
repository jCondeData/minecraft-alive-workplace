package io.github.jcondedata.aliveworkplace.devclient;

import io.github.jcondedata.aliveworkplace.blueprint.Blueprint;
import io.github.jcondedata.aliveworkplace.blueprint.BlueprintLibrary;
import io.github.jcondedata.aliveworkplace.blueprint.ModdedLibrary;
import io.github.jcondedata.aliveworkplace.blueprint.StarterBlueprints;
import io.github.jcondedata.aliveworkplace.furnish.Furnishings;
import io.github.jcondedata.aliveworkplace.furnish.FurnitureChoice;
import java.util.ArrayList;
import java.util.List;
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
import net.minecraft.world.phys.Vec3;

/**
 * SCENE=modded_library (ROADMAP 37.12), filmed with -Pfurniture=true -Pcobblemon=true (the pack's building mods): the
 * Starter Cottage II from the vanilla library beside the same cottage from the modded library (daub and braces for the
 * plaster, herringbone floors, Carved Wood chests and shelves, crockery on the Handcrafted table), and the modded Inn II.
 * Each is built as this server builds it. Checks the modded copies are chosen and use all five mods' blocks, and the
 * vanilla cottage none of the building mods' blocks.
 */
final class ModdedLibraryScene {
	private record Site(ResourceLocation id, boolean modded, BlockPos origin) {
	}

	private static final Site VANILLA = new Site(StarterBlueprints.STARTER_COTTAGE_2.id(), false, new BlockPos(0, -60, 0));
	private static final Site MODDED = new Site(StarterBlueprints.STARTER_COTTAGE_2.id(), true, new BlockPos(16, -60, 0));
	private static final Site INN = new Site(StarterBlueprints.INN_2.id(), true, new BlockPos(34, -60, 0));
	private static final List<String> BUILDING_MODS = List.of("rechiseled", "supplementaries", "carved_wood", "chipped");

	/** The stills: a name, where the camera is and what it looks at (relative to a building's origin), which building. */
	private record View(String name, Vec3 eye, Vec3 at, Site site) {
	}

	private static final List<View> VIEWS = List.of(
		new View("01_both_cottages", new Vec3(13.5, 9, -16), new Vec3(13.5, 4, 5), VANILLA),
		new View("02_modded_cottage", new Vec3(5.5, 5, -9.5), new Vec3(5.5, 4, 5), MODDED),
		new View("03_modded_cottage_back", new Vec3(5.5, 6, 20), new Vec3(5.5, 4, 5), MODDED),
		new View("04_modded_cottage_inside", new Vec3(5.5, 3.6, 2.5), new Vec3(4, 1, 6), MODDED),
		new View("05_modded_inn", new Vec3(8, 7, -12), new Vec3(8, 4, 6), INN));
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

	private void place(ServerLevel level) {
		if (!ModdedLibrary.complete(MODDED.id())) {
			wrong.add("the modded cottage lacks " + ModdedLibrary.missing(MODDED.id()) + ": film this scene with -Pfurniture=true -Pcobblemon=true");
			return;
		}
		int dressed = 0;
		for (Site site : List.of(VANILLA, MODDED, INN)) {
			Blueprint base = BlueprintLibrary.get(level, site.id()).orElse(null);
			if (base == null) {
				wrong.add("no blueprint " + site.id());
				continue;
			}
			FurnitureChoice choice = FurnitureChoice.forNewBuild(site.id()).withLibrary(site.modded());
			if (site.modded() && !FurnitureChoice.forNewBuild(site.id()).modded()) {
				wrong.add(site.id() + " isn't chosen from the modded library with its mods installed");
			}
			Set<String> mods = new TreeSet<>();
			for (Blueprint.Entry e : choice.apply(base, site.id()).blocks()) {
				level.setBlock(site.origin().offset(e.pos()), e.state(), 2 | 16);
				String ns = BuiltInRegistries.BLOCK.getKey(e.state().getBlock()).getNamespace();
				if (BUILDING_MODS.contains(ns) || ns.equals("handcrafted")) {
					mods.add(ns);
					dressed += site.modded() ? 1 : 0;
				}
			}
			if (!site.modded() && mods.stream().anyMatch(BUILDING_MODS::contains)) {
				wrong.add("the vanilla cottage has blocks of " + mods);
			}
			if (site == MODDED && !mods.containsAll(BUILDING_MODS)) {
				wrong.add("the modded cottage uses only " + mods);
			}
		}
		summary = "the Starter Cottage II from both libraries side by side and the modded Inn II, " + dressed
			+ " blocks of Rechiseled, Supplementaries, Carved Wood, Chipped and Handcrafted in the modded two";
	}
}
