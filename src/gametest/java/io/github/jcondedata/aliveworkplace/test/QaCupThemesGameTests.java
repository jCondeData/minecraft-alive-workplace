package io.github.jcondedata.aliveworkplace.test;

import io.github.jcondedata.aliveworkplace.AliveWorkplace;
import io.github.jcondedata.aliveworkplace.cup.CupChampions;
import io.github.jcondedata.aliveworkplace.cup.CupPage;
import io.github.jcondedata.aliveworkplace.cup.CupThemes;
import java.io.Reader;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.ItemStack;

/**
 * QA for ROADMAP 28.22 (the eight Cup themes) without Cobblemon, from the spec: the shipped files, read from the game's
 * own data (not the loaded set another test may swap), hold each theme's rules, the exact wares at 4 to 16 emeralds,
 * its dish, a vanilla disc, two firework colours that make its Cup banner's colours; every theme's Cup page rules read
 * as sentences (no raw keys).
 */
public class QaCupThemesGameTests implements FabricGameTest {
	private record Spec(String id, String title, int order, boolean doubles, int level, int bring, List<String> types, String stage, List<String> wares,
		String dish, String disc, DyeColor cup, DyeColor field) {
	}

	private static final List<Spec> SPECS = List.of(
		new Spec("blossom_cup", "Blossom Cup", 1, false, 50, 3, List.of("grass", "bug", "fairy"), "any",
			List.of("miracle_seed", "silver_powder", "fairy_feather", "leaf_stone", "shiny_stone"), "flower_sweet", "music_disc_chirp", DyeColor.PINK, null),
		new Spec("little_cup", "Little Cup", 2, false, 5, 3, List.of(), "first",
			List.of("eviolite", "everstone", "oval_stone", "link_cable", "exp_candy_xs"), "casteliacone", "music_disc_cat", DyeColor.YELLOW, DyeColor.WHITE),
		new Spec("sun_cup", "Sun Cup", 3, true, 50, 4, List.of("fire", "water", "electric"), "any",
			List.of("charcoal_stick", "mystic_water", "magnet", "fire_stone", "water_stone", "thunder_stone"), "lava_cookie", "music_disc_blocks", DyeColor.ORANGE, DyeColor.BLUE),
		new Spec("workers_cup", "Workers' Cup", 4, false, 50, 3, List.of(), "any",
			List.of("black_belt", "hard_stone", "metal_coat", "soft_sand", "power_weight"), "pewter_crunchies", "music_disc_mall", DyeColor.YELLOW, null),
		new Spec("harvest_cup", "Harvest Cup", 5, false, 50, 3, List.of("ground", "rock", "normal"), "any",
			List.of("soft_sand", "hard_stone", "silk_scarf", "sun_stone", "big_root"), "leek_and_potato_stew", "music_disc_far", DyeColor.ORANGE, DyeColor.YELLOW),
		new Spec("lantern_cup", "Lantern Cup", 6, false, 50, 3, List.of("ghost", "dark", "psychic"), "any",
			List.of("spell_tag", "black_glasses", "twisted_spoon", "dusk_stone", "reaper_cloth"), "sinister_tea", "music_disc_13", DyeColor.PURPLE, DyeColor.WHITE),
		new Spec("frost_cup", "Frost Cup", 7, false, 50, 3, List.of("ice", "steel", "dragon"), "any",
			List.of("never_melt_ice", "metal_coat", "dragon_fang", "ice_stone", "razor_claw"), "smoked_tail_curry", "music_disc_strad", DyeColor.LIGHT_BLUE, DyeColor.WHITE),
		new Spec("grand_cup", "Grand Cup", 8, false, 100, 6, List.of(), "any",
			List.of("ability_capsule", "life_orb", "leftovers", "choice_scarf", "rare_candy"), "big_malasada", "music_disc_creator", DyeColor.YELLOW, DyeColor.RED));

	/** The mod's own theme files, read from the server's data as the reload listener reads them. */
	private static Map<ResourceLocation, CupThemes.Theme> shipped(GameTestHelper helper) {
		Map<ResourceLocation, String> files = new LinkedHashMap<>();
		for (Map.Entry<ResourceLocation, Resource> e : helper.getLevel().getServer().getResourceManager()
			.listResources(CupThemes.FOLDER, p -> p.getNamespace().equals(AliveWorkplace.MOD_ID) && p.getPath().endsWith(".json")).entrySet()) {
			try (Reader reader = e.getValue().openAsReader()) {
				StringBuilder text = new StringBuilder();
				char[] buf = new char[4096];
				for (int n; (n = reader.read(buf)) > 0; ) {
					text.append(buf, 0, n);
				}
				files.put(e.getKey(), text.toString());
			} catch (Exception ex) {
				throw new AssertionError("can't read " + e.getKey() + ": " + ex);
			}
		}
		return CupThemes.load(files);
	}

	/** Each of the eight files, against the spec's list: rules, the exact wares at 4 to 16 emeralds, the dish, a vanilla disc, the fireworks' banner colours. */
	//$ gametest_batch 'FabricGameTest.EMPTY_STRUCTURE' '"qaCupThemes"'
	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE, batch = "qaCupThemes")
	public void theEightShippedThemesMatchTheSpec(GameTestHelper helper) {
		Map<ResourceLocation, CupThemes.Theme> themes = shipped(helper);
		helper.assertTrue(themes.keySet().stream().map(ResourceLocation::getPath).toList().equals(SPECS.stream().map(Spec::id).toList()),
			"the shipped themes, in order: " + themes.keySet());
		for (Spec spec : SPECS) {
			CupThemes.Theme t = themes.get(AliveWorkplace.id(spec.id()));
			String n = spec.id() + ": ";
			helper.assertTrue(Component.translatable(t.name()).getString().equals(spec.title()), n + "name " + Component.translatable(t.name()).getString());
			helper.assertTrue(t.order() == spec.order() && t.doubles() == spec.doubles() && t.level() == spec.level() && t.bring() == spec.bring()
				&& t.types().equals(spec.types()) && t.stage().equals(spec.stage()), n + "rules " + t);
			helper.assertTrue(t.wares().stream().map(w -> w.item().toString()).toList().equals(spec.wares().stream().map(w -> "cobblemon:" + w).toList()),
				n + "wares " + t.wares());
			helper.assertTrue(t.wares().stream().allMatch(w -> w.price() >= 4 && w.price() <= 16), n + "a ware outside 4 to 16 emeralds: " + t.wares());
			helper.assertTrue(t.dish().toString().equals("cobblemon:" + spec.dish()), n + "dish " + t.dish());
			// discs are vanilla's: a real, playable disc even without Cobblemon
			helper.assertTrue(t.disc().toString().equals("minecraft:" + spec.disc()) && BuiltInRegistries.ITEM.getOptional(t.disc())
				.map(i -> new ItemStack(i).has(DataComponents.JUKEBOX_PLAYABLE)).orElse(false), n + "disc " + t.disc());
			helper.assertTrue(t.fireworks().size() == 2, n + "fireworks " + t.fireworks());
			CupChampions.Colours c = CupChampions.colours(t);
			helper.assertTrue(c.cup() == spec.cup() && (spec.field() == null || c.field() == spec.field()) && c.cup() != c.field(),
				n + "the Cup banner's colours " + c);
			helper.assertTrue(t.partnerDays() == (spec.id().equals("workers_cup") ? 3 : 0) && t.workerTypes() == spec.id().equals("workers_cup"), n + "the Workers' rules");
			boolean lantern = spec.id().equals("lantern_cup");
			helper.assertTrue(lantern ? t.start() == 12000 && t.end() == 24000 : t.start() == CupThemes.NOON && t.end() == CupThemes.MIDNIGHT,
				n + "hours " + t.start() + "-" + t.end());
			for (Component line : CupPage.rules(t)) {
				String s = line.getString();
				helper.assertTrue(!s.isBlank() && !s.contains("aliveworkplace.") && !s.contains("%"), n + "a Cup page rule reads " + s);
			}
		}
		helper.assertTrue(themes.get(AliveWorkplace.id("grand_cup")).rules().equals(List.of("Species Clause", "Item Clause")), "the Grand Cup's clauses");
		helper.assertTrue(SPECS.stream().filter(s -> !s.id().equals("grand_cup")).allMatch(s -> themes.get(AliveWorkplace.id(s.id())).rules().isEmpty()),
			"a clause on a theme the spec gives none");
		helper.succeed();
	}

	/** Every Cup page rule line of every theme, word for word where the spec words it. */
	//$ gametest_batch 'FabricGameTest.EMPTY_STRUCTURE' '"qaCupThemes"'
	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE, batch = "qaCupThemes")
	public void theCupPageRulesReadAsTheSpecSays(GameTestHelper helper) {
		Map<ResourceLocation, CupThemes.Theme> themes = shipped(helper);
		Map<String, List<String>> lines = new LinkedHashMap<>();
		themes.forEach((id, t) -> lines.put(id.getPath(), CupPage.rules(t).stream().map(Component::getString).toList()));
		helper.assertTrue(lines.get("little_cup").contains("Only first-stage Pokémon that can still evolve"), "Little Cup " + lines.get("little_cup"));
		helper.assertTrue(lines.get("lantern_cup").contains("Bouts from dusk until dawn"), "Lantern Cup " + lines.get("lantern_cup"));
		helper.assertTrue(lines.get("workers_cup").contains("Only Pokémon that have helped a villager at work on 3 days or more"), "Workers' Cup " + lines.get("workers_cup"));
		helper.assertTrue(!lines.get("blossom_cup").contains("Bouts from dusk until dawn") && !lines.get("blossom_cup").contains("Only first-stage Pokémon that can still evolve"),
			"Blossom Cup carries another theme's rule " + lines.get("blossom_cup"));
		for (Map.Entry<String, List<String>> e : lines.entrySet()) {
			helper.assertTrue(!e.getValue().isEmpty(), e.getKey() + " has no rules on its page");
			String all = String.join(" | ", e.getValue());
			helper.assertTrue(!all.contains("colour") && !all.contains("Colour"), e.getKey() + " is in British English: " + all);
		}
		helper.succeed();
	}
}
