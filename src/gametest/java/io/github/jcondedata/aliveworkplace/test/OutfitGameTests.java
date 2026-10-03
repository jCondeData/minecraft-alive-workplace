package io.github.jcondedata.aliveworkplace.test;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import io.github.jcondedata.aliveworkplace.AliveWorkplace;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import javax.imageio.ImageIO;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.loader.api.ModContainer;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;

/**
 * Tester's check for ROADMAP 21.1 (every profession's outfit, as a villager and as a zombie villager, shown in game):
 * every villager profession the mod registers ships both outfits. Without one, vanilla's profession layer draws the
 * missing-texture checkerboard over that villager's (or zombie villager's) clothes, and the showcase's 'outfits' scene
 * only notices through the client log. This runs in every build and needs no client.
 */
public class OutfitGameTests implements FabricGameTest {
	/** The hats vanilla's villager texture metadata knows ({@code VillagerMetaDataSection.Hat}). */
	private static final Set<String> HATS = Set.of("none", "partial", "full");

	/**
	 * For every profession in our namespace, the two files vanilla's {@code VillagerProfessionLayer} looks up,
	 * {@code textures/entity/villager/profession/<id>.png} and {@code textures/entity/zombie_villager/profession/<id>.png}:
	 * there, 64x64 like vanilla's outfits, with something drawn; a {@code .mcmeta} beside one names a hat vanilla knows.
	 */
	//$ gametest 'FabricGameTest.EMPTY_STRUCTURE'
	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
	public void everyProfessionOfOursHasAVillagerAndAZombieVillagerOutfit(GameTestHelper helper) {
		ModContainer mod = FabricLoader.getInstance().getModContainer(AliveWorkplace.MOD_ID).orElseThrow();
		List<String> problems = new ArrayList<>();
		List<String> jobs = new ArrayList<>();
		for (var entry : BuiltInRegistries.VILLAGER_PROFESSION.entrySet()) {
			ResourceLocation id = entry.getKey().location();
			if (!id.getNamespace().equals(AliveWorkplace.MOD_ID)) {
				continue;
			}
			jobs.add(id.getPath());
			for (String kind : List.of("villager", "zombie_villager")) {
				String file = "assets/" + id.getNamespace() + "/textures/entity/" + kind + "/profession/" + id.getPath() + ".png";
				String problem = outfitProblem(mod, file);
				if (problem != null) {
					problems.add(id + " as a " + kind + ": " + problem);
				}
			}
		}
		helper.assertTrue(!jobs.isEmpty(), "the mod registers no professions");
		helper.assertTrue(problems.isEmpty(), problems.size() + " outfit problem(s): " + String.join("; ", problems));
		AliveLog.info("[test] outfits: " + jobs.size() + " professions, each with a villager and a zombie villager outfit");
		helper.succeed();
	}

	/** What is wrong with the outfit at {@code file} in the mod's resources, or null when nothing is. */
	private static String outfitProblem(ModContainer mod, String file) {
		Optional<Path> png = mod.findPath(file);
		if (png.isEmpty()) {
			return "no texture at " + file;
		}
		BufferedImage image;
		try (InputStream in = Files.newInputStream(png.get())) {
			image = ImageIO.read(in);
		} catch (IOException e) {
			return "unreadable " + file + " (" + e.getMessage() + ")";
		}
		if (image == null) {
			return file + " is not a PNG";
		}
		if (image.getWidth() != 64 || image.getHeight() != 64) {
			return file + " is " + image.getWidth() + "x" + image.getHeight() + ", not 64x64 like vanilla's outfits";
		}
		int drawn = 0;
		for (int y = 0; y < 64; y++) {
			for (int x = 0; x < 64; x++) {
				drawn += (image.getRGB(x, y) >>> 24) != 0 ? 1 : 0;
			}
		}
		if (drawn == 0) {
			return file + " draws nothing (every pixel transparent)";
		}
		Optional<Path> meta = mod.findPath(file + ".mcmeta");
		if (meta.isPresent()) {
			try {
				JsonElement json = JsonParser.parseString(Files.readString(meta.get()));
				JsonObject villager = json.isJsonObject() ? json.getAsJsonObject().getAsJsonObject("villager") : null;
				String hat = villager != null && villager.has("hat") ? villager.get("hat").getAsString() : null;
				if (hat != null && !HATS.contains(hat)) {
					return file + ".mcmeta names the hat '" + hat + "', which vanilla doesn't know";
				}
			} catch (IOException | RuntimeException e) {
				return "unreadable " + file + ".mcmeta (" + e.getMessage() + ")";
			}
		}
		return null;
	}

	/** Log lines the report can grep for ("[test] ..."). */
	private static final class AliveLog {
		private static final org.slf4j.Logger LOG = org.slf4j.LoggerFactory.getLogger("aliveworkplace_test");

		static void info(String message) {
			LOG.info(message);
		}
	}
}
