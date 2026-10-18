package io.github.jcondedata.aliveworkplace.devclient;

import io.github.jcondedata.aliveworkplace.blueprint.Blueprint;
import io.github.jcondedata.aliveworkplace.blueprint.BlueprintLibrary;
import io.github.jcondedata.aliveworkplace.blueprint.BlueprintStyles;
import io.github.jcondedata.aliveworkplace.blueprint.StarterBlueprints;
import io.github.jcondedata.aliveworkplace.furnish.Furnishings;
import io.github.jcondedata.aliveworkplace.furnish.FurnitureChoice;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.CloudStatus;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.ai.village.poi.PoiTypes;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.phys.Vec3;

/**
 * SCENE=carved_styles (ROADMAP 37.7), filmed with -Pfurniture=true (which brings Carved Wood and Rechiseled; with
 * -Pcobblemon=true, as CobbleFurnies in that set needs it): the Library in the Spruce style, with Carved Wood's spruce
 * bookshelves, lectern, ladders and lanterns, and the Starter Cottage in the Grand style, its floors and wall panels
 * Rechiseled's patterned dark oak planks and andesite, each built as this server would build it (furniture too). Checks
 * the Library's shelves are all Carved Wood's, its lectern Carved Wood's and a Librarian's job site, and the cottage
 * stands with Rechiseled blocks.
 */
final class CarvedStylesScene {
	private record Site(ResourceLocation id, BlockPos origin) {
	}

	private static final Site LIBRARY = new Site(BlueprintStyles.styled(StarterBlueprints.LIBRARY.id(), "spruce"), new BlockPos(0, -60, 0));
	private static final Site COTTAGE = new Site(BlueprintStyles.styled(StarterBlueprints.STARTER_COTTAGE.id(), "grand"), new BlockPos(22, -60, 0));

	/** The stills: a name, where the camera is and what it looks at (relative to a building's origin), which building. */
	private record View(String name, Vec3 eye, Vec3 at, Site site) {
	}

	private static final List<View> VIEWS = List.of(
		new View("01_library_front", new Vec3(6.5, 5.5, -9.5), new Vec3(6.5, 4, 5), LIBRARY),
		new View("02_library_shelves", new Vec3(6.5, 3.4, 4.2), new Vec3(6.5, 2, 10), LIBRARY),
		new View("03_grand_cottage", new Vec3(5.5, 5, -9.5), new Vec3(5.5, 3, 5), COTTAGE),
		new View("04_grand_cottage_floor", new Vec3(5.5, 3.6, 3.4), new Vec3(5.5, 1, 8), COTTAGE));
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
				Furnishings.ENABLED = true; // M37's switch (off on main until the milestone ships)
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
		if (!BuiltInRegistries.BLOCK.containsKey(ResourceLocation.parse("carved_wood:spruce_bookshelf"))
			|| !BuiltInRegistries.BLOCK.containsKey(ResourceLocation.parse("rechiseled:andesite_squares"))) {
			wrong.add("Carved Wood or Rechiseled isn't loaded: film this scene with -Pfurniture=true");
			return;
		}
		int shelves = 0;
		int vanillaShelves = 0;
		int rechiseled = 0;
		BlockPos lectern = null;
		for (Site site : List.of(LIBRARY, COTTAGE)) {
			Blueprint styled = BlueprintLibrary.get(level, site.id()).orElse(null);
			if (styled == null) {
				wrong.add("no blueprint " + site.id());
				continue;
			}
			FurnitureChoice choice = FurnitureChoice.forNewBuild(site.id());
			for (Blueprint.Entry e : choice.apply(styled, site.id()).blocks()) {
				BlockPos at = site.origin().offset(e.pos());
				level.setBlock(at, e.state(), 2 | 16);
				String id = BuiltInRegistries.BLOCK.getKey(e.state().getBlock()).toString();
				shelves += id.equals("carved_wood:spruce_bookshelf") ? 1 : 0;
				vanillaShelves += id.equals("minecraft:bookshelf") ? 1 : 0;
				rechiseled += site == COTTAGE && id.startsWith("rechiseled:") ? 1 : 0;
				if (id.equals("carved_wood:spruce_lectern")) {
					lectern = at;
				}
			}
		}
		if (shelves == 0 || vanillaShelves > 0) {
			wrong.add("the spruce Library has " + shelves + " Carved Wood bookshelves and " + vanillaShelves + " vanilla ones");
		}
		if (lectern == null || !PoiTypes.forState(level.getBlockState(lectern)).map(h -> h.is(PoiTypes.LIBRARIAN)).orElse(false)) {
			wrong.add("the spruce Library's lectern isn't a Carved Wood lectern that is a Librarian's job site");
		}
		if (rechiseled == 0) {
			wrong.add("the Grand cottage has no Rechiseled block");
		}
		summary = "the Library in Spruce with " + shelves + " Carved Wood bookshelves and a Carved Wood lectern (a Librarian's job site), the Starter Cottage in Grand with "
			+ rechiseled + " Rechiseled blocks";
	}
}
