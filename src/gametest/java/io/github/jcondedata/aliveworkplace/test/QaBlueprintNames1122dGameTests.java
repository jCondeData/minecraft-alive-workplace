package io.github.jcondedata.aliveworkplace.test;

import io.github.jcondedata.aliveworkplace.AliveWorkplace;
import io.github.jcondedata.aliveworkplace.blueprint.BlueprintItem;
import io.github.jcondedata.aliveworkplace.blueprint.BlueprintLibrary;
import io.github.jcondedata.aliveworkplace.blueprint.Blueprints;
import io.github.jcondedata.aliveworkplace.table.TableServer;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.locale.Language;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;

/**
 * QA lane D (2026-11-22, lane-d-1122-0408) for B1467, from the bug's text: every upgrade level ({@code _2} to {@code _4})
 * and every culture look ({@code looks/<culture>/<build>}) reads its own {@code blueprint.aliveworkplace.*} name, never
 * the fallback made from the id ("Stone Wall 2"). The fix's own test lists the structure files; these list the builds
 * the way the game shows them: the Blueprint Table's own listing ({@link TableServer#listing}, every entry a player
 * sees, locked ones too) and the library's full list ({@link BlueprintLibrary#list} with {@code everything}) narrowed
 * to the mod's upgrade levels and looks, so a blueprint added later without a name fails here. A shown name is checked
 * whole: every translation key in it and in its arguments must be in en_us.json, and it must not end in the fallback's
 * " 2", " 3" or " 4".
 */
public class QaBlueprintNames1122dGameTests implements FabricGameTest {
	private static final Pattern LEVEL = Pattern.compile(".*_[234]$");
	private static final Pattern FALLBACK_DIGIT = Pattern.compile(".* [234]$");

	/** Translation keys in {@code c} (and its arguments and siblings) that en_us.json lacks; a fallback given in code doesn't count as a name. */
	private static void missing(Component c, List<String> out) {
		if (c.getContents() instanceof TranslatableContents t) {
			if (!Language.getInstance().has(t.getKey())) {
				out.add(t.getKey());
			}
			for (Object arg : t.getArgs()) {
				if (arg instanceof Component a) {
					missing(a, out);
				}
			}
		}
		c.getSiblings().forEach(s -> missing(s, out));
	}

	private static List<String> unnamed(Iterable<ResourceLocation> ids) {
		List<String> bad = new ArrayList<>();
		for (ResourceLocation id : ids) {
			Component name = Blueprints.displayName(id);
			List<String> keys = new ArrayList<>();
			missing(name, keys);
			String shown = name.getString();
			if (!keys.isEmpty() || FALLBACK_DIGIT.matcher(shown).matches()) {
				bad.add(id.getPath() + " (\"" + shown + "\"" + (keys.isEmpty() ? "" : ", no " + keys) + ")");
			}
		}
		return bad;
	}

	/**
	 * A build the mod (or a data pack) ships, not one saved into this world (a Structure Block save, an import, a renewal
	 * snapshot, other tests' saves): those carry the player's own name, which is not the lang file's to give.
	 */
	private static boolean shipped(MinecraftServer server, ResourceLocation id) {
		return server.getResourceManager().getResource(ResourceLocation.fromNamespaceAndPath(id.getNamespace(), "structure/" + id.getPath() + ".nbt")).isPresent();
	}

	/** Every mod build the Blueprint Table lists (unlocked and locked, not the "???" stand-ins) reads a name of its own. */
	//$ gametest EMPTY_STRUCTURE
	@GameTest(template = EMPTY_STRUCTURE)
	public void qaEveryBuildTheBlueprintTableListsHasItsOwnName(GameTestHelper helper) {
		Set<ResourceLocation> ids = new LinkedHashSet<>();
		TableServer.listing(helper.getLevel(), null).stream()
			.map(e -> e.id())
			.filter(id -> id.getNamespace().equals(AliveWorkplace.MOD_ID) && !id.getPath().startsWith("hidden/"))
			.filter(id -> shipped(helper.getLevel().getServer(), id))
			.forEach(ids::add);
		helper.assertTrue(ids.size() > 100, "the Table lists only " + ids.size() + " of the mod's builds");
		helper.assertTrue(ids.contains(AliveWorkplace.id("stone_wall_2")), "the Table doesn't list stone_wall_2: " + ids.stream().filter(i -> i.getPath().startsWith("stone")).toList());
		List<String> bad = unnamed(ids);
		helper.assertTrue(bad.isEmpty(), bad.size() + " of the Table's builds show no name of their own: " + bad);
		helper.succeed();
	}

	/**
	 * Every upgrade level and culture look the library knows (the full list, as an operator's command sees it, so builds
	 * an expansion hasn't opened yet count too) reads a name of its own.
	 */
	//$ gametest EMPTY_STRUCTURE
	@GameTest(template = EMPTY_STRUCTURE)
	public void qaEveryUpgradeLevelAndLookTheLibraryKnowsHasItsOwnName(GameTestHelper helper) {
		MinecraftServer server = helper.getLevel().getServer();
		List<ResourceLocation> ids = BlueprintLibrary.list(server, true).stream()
			.filter(id -> id.getNamespace().equals(AliveWorkplace.MOD_ID) && !BlueprintLibrary.isWorldgenPiece(id))
			.filter(id -> LEVEL.matcher(id.getPath()).matches() || id.getPath().startsWith("looks/"))
			.filter(id -> shipped(server, id))
			.toList();
		long levels = ids.stream().filter(id -> !id.getPath().contains("/")).count();
		long looks = ids.stream().filter(id -> id.getPath().startsWith("looks/")).count();
		helper.assertTrue(levels >= 60, "only " + levels + " upgrade levels found");
		helper.assertTrue(looks >= 100, "only " + looks + " culture looks found");
		for (String wall : List.of("stone_wall_2", "wall_tower_2", "palisade_2", "gatehouse_2")) {
			helper.assertTrue(ids.contains(AliveWorkplace.id(wall)), wall + " isn't among the library's upgrade levels");
		}
		List<String> bad = unnamed(ids);
		helper.assertTrue(bad.isEmpty(), bad.size() + " upgrade levels and looks show no name of their own: " + bad);
		helper.succeed();
	}

	/** The report's four walls by the names every other upgrade has, in the Table's words and on the blueprint item. */
	//$ gametest EMPTY_STRUCTURE
	@GameTest(template = EMPTY_STRUCTURE)
	public void qaTheLevelTwoWallsReadAsRomanNumeralsOnTheBlueprintItemToo(GameTestHelper helper) {
		String[][] expected = {{"stone_wall_2", "Stone Wall II"}, {"wall_tower_2", "Wall Tower II"}, {"palisade_2", "Palisade II"}, {"gatehouse_2", "Gatehouse II"}};
		for (String[] e : expected) {
			ResourceLocation id = AliveWorkplace.id(e[0]);
			String shown = Blueprints.displayName(id).getString();
			helper.assertTrue(shown.equals(e[1]), e[0] + " reads \"" + shown + "\", not \"" + e[1] + "\"");
			String item = BlueprintItem.create(id, null).getHoverName().getString();
			helper.assertTrue(item.equals("Blueprint: " + e[1]), "the blueprint item of " + e[0] + " reads \"" + item + "\"");
		}
		// A look of a level-two build: its own name, not "Starter Cottage 2: ..."
		String look = Blueprints.displayName(AliveWorkplace.id("looks/desert/starter_cottage_2")).getString();
		helper.assertTrue(!look.contains(" 2") && look.contains("II"), "looks/desert/starter_cottage_2 reads \"" + look + "\"");
		helper.succeed();
	}
}
