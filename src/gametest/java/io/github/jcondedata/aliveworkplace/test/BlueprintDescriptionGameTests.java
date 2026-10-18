package io.github.jcondedata.aliveworkplace.test;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import io.github.jcondedata.aliveworkplace.AliveWorkplace;
import io.github.jcondedata.aliveworkplace.blueprint.BlueprintLibrary;
import io.github.jcondedata.aliveworkplace.blueprint.BlueprintStyles;
import io.github.jcondedata.aliveworkplace.blueprint.Blueprints;
import io.github.jcondedata.aliveworkplace.blueprint.StarterBlueprints;
import io.github.jcondedata.aliveworkplace.table.BlueprintUnlocks;
import io.github.jcondedata.aliveworkplace.table.TablePayloads;
import io.github.jcondedata.aliveworkplace.table.TableServer;
import io.github.jcondedata.aliveworkplace.wonder.WonderBlueprints;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Stream;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.loader.api.ModContainer;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;

/**
 * ROADMAP 36.6: a line about every blueprint. Every build the Blueprint Table lists (levels, the Cobblemon builds and looks,
 * the sold-only homes and the Wonders included, whatever is installed or open) has a {@code blueprint_desc} key in
 * en_us.json: one plain sentence under {@link Blueprints#DESCRIPTION_MAX} characters. A build in another style is described
 * as its base; an imported build has no key and its pane shows nothing.
 */
public class BlueprintDescriptionGameTests implements FabricGameTest {
	private static JsonObject lang() {
		ModContainer mod = FabricLoader.getInstance().getModContainer(AliveWorkplace.MOD_ID).orElseThrow();
		try (InputStream in = Files.newInputStream(mod.findPath("assets/aliveworkplace/lang/en_us.json").orElseThrow())) {
			return JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonObject();
		} catch (IOException e) {
			throw new GameTestAssertException("can't read en_us.json: " + e);
		}
	}

	/** Whether {@code id} is a structure this mod ships (not a build a test or a player imported into the world). */
	private static boolean shipped(ResourceLocation id) {
		ModContainer mod = FabricLoader.getInstance().getModContainer(AliveWorkplace.MOD_ID).orElseThrow();
		return id.getNamespace().equals(AliveWorkplace.MOD_ID)
			&& mod.findPath("data/aliveworkplace/structure/" + id.getPath() + ".nbt").map(Files::isRegularFile).orElse(false);
	}

	/**
	 * Every blueprint the Table can list: today's listing, plus every shipped build it would list with every mod installed
	 * and every expansion open (the Cobblemon builds, an expansion's workplaces), the sold-only homes and every Wonder.
	 */
	private static Set<ResourceLocation> listed(MinecraftServer server) {
		Set<ResourceLocation> ids = new TreeSet<>();
		for (TablePayloads.Entry entry : TableServer.listing(server)) {
			if (!entry.hidden() && (shipped(entry.id()) || entry.id().getNamespace().equals(AliveWorkplace.MOD_ID) && WonderBlueprints.wonderOf(entry.id()).isPresent())) { // not a build other tests imported
				ids.add(entry.id());
			}
		}
		for (ResourceLocation id : BlueprintLibrary.list(server, true)) {
			if (shipped(id) && !BlueprintLibrary.isWorldgenPiece(id) && !io.github.jcondedata.aliveworkplace.city.Roads.isSegment(id)) {
				ids.add(id);
			}
		}
		// The sold-only homes and the Wonders; ours only (the test mod's own test Wonder, like a datapack's, has no line)
		BlueprintUnlocks.rareExtras(server).stream().filter(id -> id.getNamespace().equals(AliveWorkplace.MOD_ID)).forEach(ids::add);
		Stream.of(StarterBlueprints.SOLD_ONLY, StarterBlueprints.COBBLEMON_ONLY, StarterBlueprints.JOB_BUILDS)
			.flatMap(List::stream).forEach(e -> ids.add(e.id()));
		// Every Wonder shipped, the Stadium too (loaded only with Cobblemon)
		ModContainer mod = FabricLoader.getInstance().getModContainer(AliveWorkplace.MOD_ID).orElseThrow();
		Path wonders = mod.findPath("data/aliveworkplace/wonders").orElseThrow();
		try (Stream<Path> files = Files.list(wonders)) {
			files.map(p -> p.getFileName().toString()).filter(n -> n.endsWith(".json"))
				.forEach(n -> ids.add(WonderBlueprints.idOf(AliveWorkplace.id(n.substring(0, n.length() - ".json".length())))));
		} catch (IOException e) {
			throw new GameTestAssertException("can't list the Wonders: " + e);
		}
		return ids;
	}

	/** Every build the Table lists finds its description in en_us.json, and the walk covers the whole library. */
	//$ gametest 'FabricGameTest.EMPTY_STRUCTURE'
	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
	public void everyListedBlueprintHasADescription(GameTestHelper helper) {
		JsonObject lang = lang();
		Set<ResourceLocation> ids = listed(helper.getLevel().getServer());
		List<String> missing = new ArrayList<>();
		for (ResourceLocation id : ids) {
			String key = Blueprints.descriptionKey(id);
			if (key == null || !lang.has(key)) {
				missing.add(id + " (" + key + ")");
			}
		}
		helper.assertTrue(missing.isEmpty(), missing.size() + " blueprints the Table lists have no description: " + missing);
		// The walk saw the levels, a Cobblemon look, a sold-only home and the Wonders, not just today's listing
		for (ResourceLocation id : List.of(StarterBlueprints.STARTER_COTTAGE_4.id(), AliveWorkplace.id("looks/lodge/pokemon_center_2"),
			StarterBlueprints.MANOR_4.id(), WonderBlueprints.idOf(AliveWorkplace.id("stadium")), WonderBlueprints.idOf(AliveWorkplace.id("colossus")))) {
			helper.assertTrue(ids.contains(id), "the walk missed " + id);
		}
		helper.assertTrue(ids.size() >= 170, "only " + ids.size() + " blueprints walked");
		// And no description is left over for a build that isn't there (a renamed or removed build)
		List<String> orphans = new ArrayList<>();
		Set<String> keys = new TreeSet<>();
		ids.forEach(id -> keys.add(Blueprints.descriptionKey(id)));
		for (String key : lang.keySet()) {
			if (key.startsWith("blueprint_desc.") && !keys.contains(key)) {
				orphans.add(key);
			}
		}
		helper.assertTrue(orphans.isEmpty(), "descriptions of no listed blueprint: " + orphans);
		helper.succeed();
	}

	/** Each description is one plain sentence under the limit: a capital first, a full stop last and none inside. */
	//$ gametest 'FabricGameTest.EMPTY_STRUCTURE'
	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
	public void descriptionsAreOneShortSentence(GameTestHelper helper) {
		JsonObject lang = lang();
		List<String> problems = new ArrayList<>();
		int count = 0;
		for (String key : lang.keySet()) {
			if (!key.startsWith("blueprint_desc.")) {
				continue;
			}
			count++;
			String text = lang.get(key).getAsString();
			if (text.length() >= Blueprints.DESCRIPTION_MAX) {
				problems.add(key + " is " + text.length() + " characters");
			}
			if (text.isEmpty() || !Character.isUpperCase(text.charAt(0)) || !text.endsWith(".")) {
				problems.add(key + " isn't a sentence: " + text);
			}
			if (text.substring(0, Math.max(0, text.length() - 1)).contains(". ") || text.contains("%")) {
				problems.add(key + " isn't one plain sentence: " + text);
			}
		}
		helper.assertTrue(count >= 170, "only " + count + " descriptions");
		helper.assertTrue(problems.isEmpty(), problems.toString());
		helper.succeed();
	}

	/**
	 * What the pane shows: a build's own line (a level's says what it adds), a style's is its base's, and an imported build,
	 * a village house's outside and a road have none.
	 */
	//$ gametest 'FabricGameTest.EMPTY_STRUCTURE'
	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
	public void thePaneShowsTheRightLineOrNone(GameTestHelper helper) {
		JsonObject lang = lang();
		ResourceLocation smithy = StarterBlueprints.SMITHY.id();
		helper.assertTrue("blueprint_desc.aliveworkplace.smithy".equals(Blueprints.descriptionKey(smithy)), "smithy key: " + Blueprints.descriptionKey(smithy));
		Component line = Blueprints.description(smithy, lang::has);
		helper.assertTrue(line != null && line.getString().contains("Weaponsmith"), "the Smithy's line: " + (line == null ? null : line.getString()));
		Component level = Blueprints.description(StarterBlueprints.SMITHY_2.id(), lang::has);
		helper.assertTrue(level != null && level.getString().startsWith("Adds "), "Smithy II says what it adds: " + (level == null ? null : level.getString()));
		ResourceLocation styled = BlueprintStyles.styled(StarterBlueprints.STARTER_COTTAGE.id(), "sandstone");
		helper.assertTrue(!styled.equals(StarterBlueprints.STARTER_COTTAGE.id())
				&& "blueprint_desc.aliveworkplace.starter_cottage".equals(Blueprints.descriptionKey(styled)),
			"a style is described as its base: " + styled + " -> " + Blueprints.descriptionKey(styled));
		helper.assertTrue("blueprint_desc.aliveworkplace.looks.lodge.pokemon_center".equals(Blueprints.descriptionKey(AliveWorkplace.id("looks/lodge/pokemon_center"))),
			"a look's key has dots for slashes");
		helper.assertTrue("blueprint_desc.aliveworkplace.wonder.colossus".equals(Blueprints.descriptionKey(WonderBlueprints.idOf(AliveWorkplace.id("colossus")))),
			"a Wonder's key");
		// An imported build: a key no lang file holds, so no line at all (not the raw key)
		ResourceLocation imported = AliveWorkplace.id("imported/my_castle");
		helper.assertTrue(Blueprints.description(imported, lang::has) == null, "an imported build shows a description");
		helper.assertTrue(Blueprints.description(ResourceLocation.fromNamespaceAndPath("minecraft", "my_house"), lang::has) == null,
			"a Structure Block save shows a description");
		// The server's own language (Fabric loads our en_us.json there too) agrees with the file
		helper.assertTrue(Blueprints.description(smithy, net.minecraft.locale.Language.getInstance()::has) != null
				&& Blueprints.description(imported, net.minecraft.locale.Language.getInstance()::has) == null,
			"the game's language disagrees with en_us.json");
		helper.assertTrue(Blueprints.descriptionKey(AliveWorkplace.id(io.github.jcondedata.aliveworkplace.city.Roads.FOLDER + "straight")) == null,
			"a road segment has a description key");
		helper.succeed();
	}
}
