package io.github.jcondedata.aliveworkplace.compat;

import com.cobblemon.mod.common.Cobblemon;
import com.cobblemon.mod.common.api.storage.party.PlayerPartyStore;
import com.cobblemon.mod.common.api.pokemon.PokemonProperties;
import com.cobblemon.mod.common.pokemon.Pokemon;
import io.github.jcondedata.aliveworkplace.compat.cobblemon.CobblemonDaycareKeeper;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameType;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;

/**
 * QA (lane-d-1109-0108): verifies B1031, B1040 and B1041 (ROADMAP 28.12, the villager-jobs wiki's "Daycare
 * hatchlings") on the cases the bug texts name as "likely also" that no earlier test covers, written from the spec:
 * the hatchling takes the pre-evolution form whose evolution leads to the mother's form, the mother's choice features
 * that the baby shares are copied so the form survives a save and load, a gender form's aspects are ignored when
 * finding that pre-evolution form, and Cobbreeding's egg carries the choice features as well as the form.
 * <ul>
 * <li>B1031: Oricorio Pom-Pom and Pa'u, Burmy's three cloaks (plant: the random default), Wormadam's cloaks two steps
 * through a gender-only evolution, Deerling's four seasons (spring: the random default) and Sawsbuck back to Deerling,
 * each hatched 6 times with fixed seeds and saved and loaded twice.</li>
 * <li>B1040's neighbours: the other feature-set pre-evolutions in Cobblemon's species data (Dusk and Midnight
 * Lycanroc, Antique and Phony Polteageist, Masterpiece and plain Sinistcha), and a female Basculegion through reload.</li>
 * <li>B1041: Cobbreeding eggs of an Aqua Breed Tauros, a West and an East Sea Gastrodon, a Blue Florges, a Pom-Pom and
 * a Baile Oricorio, a Winter Sawsbuck and a female Basculegion, hatched the player's way and saved and loaded.</li>
 * </ul>
 */
public class QaDaycareFormsVerify1109dCompatTests implements FabricGameTest {
	private static final String AREA = CompatGameTests.AREA;

	private static Pokemon make(String properties) {
		return PokemonProperties.Companion.parse(properties, " ", "=").create();
	}

	/** {@code pokemon} saved and loaded again (the keeper's boarders, a party or PC after a restart). */
	private static Pokemon stored(GameTestHelper helper, Pokemon pokemon) {
		Pokemon loaded = CobblemonDaycareKeeper.load(helper.getLevel(), pokemon.saveToNBT(helper.getLevel().registryAccess(), new CompoundTag()));
		helper.assertTrue(loaded != null, "a Pokémon didn't load: " + pokemon.getSpecies().getName());
		return loaded;
	}

	private static String id(Pokemon pokemon) {
		return pokemon.getSpecies().getResourceIdentifier().getPath();
	}

	private static String describe(Pokemon pokemon) {
		return pokemon.getForm().getName() + " " + id(pokemon) + " (level " + pokemon.getLevel() + ", aspects " + pokemon.getAspects() + ")";
	}

	private static boolean is(Pokemon p, String species, String form) {
		return id(p).equals(species) && p.getForm().getName().equalsIgnoreCase(form);
	}

	/**
	 * {@code times} hatchlings of {@code mother} with {@code father} (both stored first), seeds 1..times: each a level-1
	 * {@code species} in form {@code form} right after hatching, after one save and load, and after a second.
	 */
	private static void hatches(GameTestHelper helper, String mother, String father, String species, String form, int times) {
		Pokemon m = stored(helper, make(mother));
		Pokemon f = stored(helper, make(father));
		List<String> wrong = new ArrayList<>();
		for (String part : mother.split(" ")) {
			// The pack's data (Mega Showdown) renames some choices: a choice it doesn't know would give a random mother.
			if (part.contains("=") && !part.startsWith("gender=") && !part.startsWith("level=")) {
				String choice = part.substring(part.indexOf('=') + 1).replace("'", "");
				helper.assertTrue(m.getAspects().stream().anyMatch(a -> a.startsWith(choice)) || m.getForm().getName().equalsIgnoreCase(form)
					|| m.getForm().getName().toLowerCase(java.util.Locale.ROOT).contains(choice), "the test's mother " + mother + " didn't parse: " + describe(m));
			}
		}
		for (int seed = 1; seed <= times; seed++) {
			Pokemon baby = CobblemonDaycareKeeper.hatchling(f, m, RandomSource.create(seed));
			Pokemon once = stored(helper, baby);
			Pokemon twice = stored(helper, once);
			if (!is(baby, species, form) || baby.getLevel() != 1) {
				wrong.add("hatched #" + seed + ": " + describe(baby));
			}
			if (!is(once, species, form)) {
				wrong.add("after a reload #" + seed + ": " + describe(once));
			}
			if (!is(twice, species, form)) {
				wrong.add("after two reloads #" + seed + ": " + describe(twice));
			}
		}
		helper.assertTrue(wrong.isEmpty(), mother + " x " + father + " (mother " + describe(m) + ") should hatch a level-1 " + form + " " + species
			+ " every time; " + wrong.size() + " of " + (3 * times) + " checks weren't: " + wrong);
	}

	// --- B1031: choice-feature forms whose default is random, through save and load ---

	/** A Pom-Pom and a Pa'u Oricorio (with a male of another style, and with Ditto) hatch their own style and keep it. */
	//$ gametest 'FabricGameTest.EMPTY_STRUCTURE'
	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
	public void qaPomPomAndPauOricorioHatchTheirStyleThroughReload(GameTestHelper helper) {
		hatches(helper, "oricorio dance_style=pom-pom gender=female level=30", "oricorio dance_style=baile gender=male level=30", "oricorio", "pom-pom", 6);
		hatches(helper, "oricorio dance_style=pau gender=female level=30", "oricorio dance_style=sensu gender=male level=30", "oricorio", "pa’u", 6);
		hatches(helper, "ditto level=40", "oricorio dance_style=baile gender=male level=30", "oricorio", "normal", 6);
		helper.succeed();
	}

	/** Burmy's three cloaks (Plant being the form without aspects) hatch their own cloak and keep it after reloads. */
	//$ gametest 'FabricGameTest.EMPTY_STRUCTURE'
	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
	public void qaBurmyHatchesItsCloakThroughReload(GameTestHelper helper) {
		hatches(helper, "burmy bagworm_cloak=plant gender=female level=15", "burmy bagworm_cloak=trash gender=male level=15", "burmy", "normal", 6);
		hatches(helper, "burmy bagworm_cloak=sandy gender=female level=15", "burmy bagworm_cloak=plant gender=male level=15", "burmy", "sandy", 6);
		hatches(helper, "ditto level=40", "burmy bagworm_cloak=trash gender=male level=15", "burmy", "trash", 6);
		helper.succeed();
	}

	/** A Wormadam (female only; Burmy evolves by gender) hatches a Burmy in her cloak: Plant, Sandy and Trash. */
	//$ gametest 'FabricGameTest.EMPTY_STRUCTURE'
	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
	public void qaWormadamHatchesABurmyInHerCloak(GameTestHelper helper) {
		hatches(helper, "wormadam bagworm_cloak=plant level=30", "mothim level=30", "burmy", "normal", 6);
		hatches(helper, "wormadam bagworm_cloak=sandy level=30", "ditto level=40", "burmy", "sandy", 6);
		hatches(helper, "wormadam bagworm_cloak=trash level=30", "mothim level=30", "burmy", "trash", 6);
		helper.succeed();
	}

	/** Deerling in each season (Spring being the form without aspects) hatches that season and keeps it after reloads. */
	//$ gametest 'FabricGameTest.EMPTY_STRUCTURE'
	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
	public void qaDeerlingHatchesItsSeasonThroughReload(GameTestHelper helper) {
		hatches(helper, "deerling season=spring gender=female level=20", "deerling season=winter gender=male level=20", "deerling", "normal", 6);
		hatches(helper, "deerling season=summer gender=female level=20", "deerling season=spring gender=male level=20", "deerling", "summer", 6);
		hatches(helper, "deerling season=autumn gender=female level=20", "ditto level=40", "deerling", "autumn", 6);
		hatches(helper, "ditto level=40", "deerling season=winter gender=male level=20", "deerling", "winter", 6);
		helper.succeed();
	}

	/** A Sawsbuck hatches a Deerling of her season (Spring and Winter), kept after reloads. */
	//$ gametest 'FabricGameTest.EMPTY_STRUCTURE'
	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
	public void qaSawsbuckHatchesADeerlingOfHerSeason(GameTestHelper helper) {
		hatches(helper, "sawsbuck season=spring gender=female level=40", "sawsbuck season=autumn gender=male level=40", "deerling", "normal", 6);
		hatches(helper, "sawsbuck season=winter gender=female level=40", "sawsbuck season=summer gender=male level=40", "deerling", "winter", 6);
		helper.succeed();
	}

	/** The B1031 reported cases, through two reloads now (the bug tests check one): West Shellos, Green Squawkabilly, Curly Tatsugiri. */
	//$ gametest 'FabricGameTest.EMPTY_STRUCTURE'
	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
	public void qaB1031CasesSurviveTwoReloadsWithAMaleOfAnotherForm(GameTestHelper helper) {
		hatches(helper, "shellos sea=west gender=female level=20", "shellos sea=east gender=male level=20", "shellos", "normal", 6);
		hatches(helper, "squawkabilly squawkabilly_color=green gender=female level=20", "squawkabilly squawkabilly_color=yellow gender=male level=20", "squawkabilly", "normal", 6);
		hatches(helper, "tatsugiri tatsugiri_texture=curly gender=female level=20", "tatsugiri tatsugiri_texture=stretchy gender=male level=20", "tatsugiri", "normal", 6);
		helper.succeed();
	}

	// --- B1040's neighbours: forms whose pre-evolution is set by a feature ---

	/** A female Basculegion hatches a White-Striped Basculin that is still White-Striped after reloads (B1040 through save and load). */
	//$ gametest 'FabricGameTest.EMPTY_STRUCTURE'
	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
	public void qaAFemaleBasculegionsBasculinStaysWhiteStriped(GameTestHelper helper) {
		hatches(helper, "basculegion gender=female level=40", "ditto level=40", "basculin", "white-striped", 6);
		hatches(helper, "basculegion gender=female level=40", "basculegion gender=male level=40", "basculin", "white-striped", 6);
		helper.succeed();
	}

	/** A Dusk Lycanroc hatches a Dusk Rockruff (the only Rockruff that evolves into Dusk); a Midnight one a plain Rockruff. */
	//$ gametest 'FabricGameTest.EMPTY_STRUCTURE'
	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
	public void qaLycanrocHatchesTheRockruffThatEvolvesIntoHerForm(GameTestHelper helper) {
		hatches(helper, "lycanroc wolf_form=dusk gender=female level=40", "lycanroc wolf_form=midday gender=male level=40", "rockruff", "dusk", 6);
		hatches(helper, "lycanroc wolf_form=midnight gender=female level=40", "ditto level=40", "rockruff", "normal", 6);
		hatches(helper, "lycanroc wolf_form=midday gender=female level=40", "lycanroc wolf_form=dusk gender=male level=40", "rockruff", "normal", 6);
		helper.succeed();
	}

	/** Genderless with Ditto: an Antique Polteageist hatches an Antique Sinistea, a Phony one a plain Sinistea. */
	//$ gametest 'FabricGameTest.EMPTY_STRUCTURE'
	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
	public void qaPolteageistHatchesTheSinisteaOfItsAuthenticity(GameTestHelper helper) {
		hatches(helper, "polteageist tea_authenticity=antique level=40", "ditto level=40", "sinistea", "antique", 6);
		hatches(helper, "polteageist tea_authenticity=phony level=40", "ditto level=40", "sinistea", "normal", 6);
		helper.succeed();
	}

	/** Genderless with Ditto: a Masterpiece Sinistcha hatches an Artisan Poltchageist, a plain one a plain Poltchageist. */
	//$ gametest 'FabricGameTest.EMPTY_STRUCTURE'
	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
	public void qaSinistchaHatchesThePoltchageistOfItsAuthenticity(GameTestHelper helper) {
		hatches(helper, "sinistcha matcha_authenticity=masterpiece level=40", "ditto level=40", "poltchageist", "artisan", 6);
		hatches(helper, "sinistcha matcha_authenticity=unremarkable level=40", "ditto level=40", "poltchageist", "normal", 6);
		helper.succeed();
	}

	// --- B1041: Cobbreeding eggs carry the choice features ---

	/** Cobbreeding's stored properties of an egg item (EggUtilities.extractProperties, by reflection: runtime classpath only). */
	private static PokemonProperties eggProperties(ItemStack egg) {
		try {
			return (PokemonProperties) Class.forName("ludichat.cobbreeding.EggUtilities").getMethod("extractProperties", ItemStack.class).invoke(null, egg);
		} catch (ReflectiveOperationException e) {
			throw new IllegalStateException("Cobbreeding's EggUtilities.extractProperties isn't reachable", e);
		}
	}

	/**
	 * The keeper's Cobbreeding egg of {@code mother} and {@code father}, hatched the player's way (in hand, Cobbreeding's
	 * hatch command, the egg's ticks): returns a problem, or null when the hatchling is a level-1 {@code species} in
	 * {@code form} and still is after a save and load.
	 */
	private static String eggProblem(GameTestHelper helper, String mother, String father, String species, String form, int seed) {
		ServerPlayer player = helper.makeMockServerPlayerInLevel();
		player.setGameMode(GameType.SURVIVAL);
		player.getInventory().clearContent();
		PlayerPartyStore party = Cobblemon.INSTANCE.getStorage().getParty(player);
		Pokemon m = stored(helper, make(mother));
		Component said = CobblemonDaycareKeeper.giveEgg(player, stored(helper, make(father)), m, RandomSource.create(seed), true);
		if (said == null) {
			return mother + ": no egg given";
		}
		int slot = -1;
		for (int i = 0; i < player.getInventory().items.size(); i++) {
			if (CobblemonDaycareKeeper.isCobbreedingEgg(player.getInventory().items.get(i))) {
				slot = i;
			}
		}
		if (slot < 0) {
			return mother + ": no Cobbreeding egg in the inventory (said \"" + said.getString() + "\")";
		}
		ItemStack egg = player.getInventory().items.get(slot);
		String held = eggProperties(egg).asString(" ");
		player.getInventory().selected = 0;
		if (slot != 0) {
			ItemStack first = player.getInventory().items.get(0);
			player.getInventory().items.set(0, egg);
			player.getInventory().items.set(slot, first);
		}
		if (!CobblemonDaycareKeeper.isCobbreedingEgg(player.getItemInHand(InteractionHand.MAIN_HAND))) {
			return mother + ": the egg isn't in hand";
		}
		var source = player.createCommandSourceStack().withSuppressedOutput().withPermission(4);
		try {
			helper.getLevel().getServer().getCommands().getDispatcher().execute("hatchpokemonegg @s", source);
		} catch (com.mojang.brigadier.exceptions.CommandSyntaxException e) {
			throw new IllegalStateException("hatchpokemonegg failed: " + e.getMessage(), e);
		}
		for (int i = 0; i < 60 && CobblemonDaycareKeeper.isCobbreedingEgg(player.getInventory().items.get(0)); i++) {
			player.getInventory().items.get(0).inventoryTick(helper.getLevel(), player, 0, true);
		}
		List<Pokemon> hatched = new ArrayList<>();
		for (Pokemon p : party) {
			if (id(p).equals(species) && p.getLevel() == 1) {
				hatched.add(p);
			}
		}
		if (hatched.size() != 1) {
			return mother + ": after hatching, " + hatched.size() + " level-1 " + species + " in the party (egg held " + held + ")";
		}
		Pokemon baby = hatched.get(0);
		if (!is(baby, species, form)) {
			return mother + ": the egg (" + held + ") hatched a " + describe(baby) + "; wanted " + form;
		}
		Pokemon loaded = stored(helper, baby);
		if (!is(loaded, species, form)) {
			return mother + ": the egg's " + form + " " + species + " loaded again as " + describe(loaded) + " (egg held " + held + ")";
		}
		return null;
	}

	private static void eggsHatch(GameTestHelper helper, String[][] cases) {
		helper.assertTrue(CobblemonDaycareKeeper.cobbreeding(), "Cobbreeding isn't loaded in the compat tests");
		List<String> wrong = new ArrayList<>();
		int seed = 11;
		for (String[] c : cases) {
			String problem = eggProblem(helper, c[0], c[1], c[2], c[3], seed++);
			if (problem != null) {
				wrong.add(problem);
			}
		}
		helper.assertTrue(wrong.isEmpty(), wrong.size() + " of " + cases.length + " Cobbreeding eggs hatched wrong: " + wrong);
	}

	/** Eggs of an Aqua, a Combat and a Blaze Breed Tauros (male only: with Ditto) hatch their own breed and keep their breed after a reload. */
	//$ gametest_batch AREA '"qaDaycareFormsVerify1109dEggs1"'
	@GameTest(template = AREA, batch = "qaDaycareFormsVerify1109dEggs1")
	public void qaCobbreedingEggsOfAquaAndCombatTaurosKeepTheirBreed(GameTestHelper helper) {
		eggsHatch(helper, new String[][] {
			{"tauros paldean bull_breed=aqua level=40", "ditto level=40", "tauros", "paldea-aqua"},
			{"tauros paldean bull_breed=combat level=40", "ditto level=40", "tauros", "paldea-combat"},
			{"tauros paldean bull_breed=blaze level=40", "ditto level=40", "tauros", "paldea-blaze"}});
		helper.succeed();
	}

	/** Eggs of a West and an East Sea Gastrodon hatch Shellos of her sea (West being the random default), kept after a reload. */
	//$ gametest_batch AREA '"qaDaycareFormsVerify1109dEggs2"'
	@GameTest(template = AREA, batch = "qaDaycareFormsVerify1109dEggs2")
	public void qaCobbreedingEggsOfGastrodonHatchHerSea(GameTestHelper helper) {
		eggsHatch(helper, new String[][] {
			{"gastrodon sea=east gender=female level=40", "gastrodon sea=west gender=male level=40", "shellos", "east"},
			{"gastrodon sea=west gender=female level=40", "gastrodon sea=east gender=male level=40", "shellos", "normal"},
			{"gastrodon sea=west gender=female level=40", "ditto level=40", "shellos", "normal"}});
		helper.succeed();
	}

	/** Eggs of a Blue Florges, a Pom-Pom and a Baile Oricorio and a Winter Sawsbuck hatch the mother's flower, style and season. */
	//$ gametest_batch AREA '"qaDaycareFormsVerify1109dEggs3"'
	@GameTest(template = AREA, batch = "qaDaycareFormsVerify1109dEggs3")
	public void qaCobbreedingEggsOfOtherChoiceFormsHatchTheMothersForm(GameTestHelper helper) {
		eggsHatch(helper, new String[][] {
			{"florges flower=blue level=40", "ditto level=40", "flabebe", "blue"},
			{"oricorio dance_style=pom-pom gender=female level=30", "oricorio dance_style=sensu gender=male level=30", "oricorio", "pom-pom"},
			{"oricorio dance_style=baile gender=female level=30", "oricorio dance_style=sensu gender=male level=30", "oricorio", "normal"},
			{"sawsbuck season=winter gender=female level=40", "ditto level=40", "deerling", "winter"}});
		helper.succeed();
	}

	/** The egg of a female Basculegion (B1040 on the pack's path) hatches a White-Striped Basculin. */
	//$ gametest_batch AREA '"qaDaycareFormsVerify1109dEggs4"'
	@GameTest(template = AREA, batch = "qaDaycareFormsVerify1109dEggs4")
	public void qaCobbreedingEggOfAFemaleBasculegionHatchesWhiteStriped(GameTestHelper helper) {
		eggsHatch(helper, new String[][] {
			{"basculegion gender=female level=40", "ditto level=40", "basculin", "white-striped"},
			{"basculegion gender=female level=40", "basculegion gender=male level=40", "basculin", "white-striped"}});
		helper.succeed();
	}
}
