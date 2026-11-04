package io.github.jcondedata.aliveworkplace.compat;

import com.cobblemon.mod.common.Cobblemon;
import com.cobblemon.mod.common.api.pokemon.PokemonProperties;
import com.cobblemon.mod.common.api.storage.party.PlayerPartyStore;
import com.cobblemon.mod.common.pokemon.Pokemon;
import io.github.jcondedata.aliveworkplace.compat.cobbledollars.CobbleDollarsBank;
import io.github.jcondedata.aliveworkplace.compat.cobblemon.CobblemonDaycareKeeper;
import io.github.jcondedata.aliveworkplace.daycare.DaycareKeepers;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameType;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;

/**
 * QA (2026-11-04, lane d) of B854 and ROADMAP 28.12's hatchling form, from the wiki (villager-jobs.md, "Daycare
 * hatchlings"): "A Daycare Keeper's pair hatches the mother's base form in her regional form: a female Alolan Ninetales
 * hatches an Alolan Vulpix, a Kanto mother a Kanto Vulpix whatever the father. The hatchling takes the pre-evolution
 * form whose evolution leads to the mother's form; Cobbreeding's egg is made from the same hatchling." and the roadmap's
 * "in the base form of the mother (or the parent that isn't Ditto)". Regions other than Alola, regional evolutions that
 * are species of their own (Perrserker, Clodsire, Sneasler, Obstagoon), regional evolutions of a plain baby (Alolan
 * Raichu, Mr. Rime), Ditto pairs, parents loaded from their saved form (as the keeper stores them), the sentence a
 * player reads, and Cobbreeding's egg (in the compat test mods) down to its hatching.
 */
public class QaDaycareRegional1104d2CompatTests implements FabricGameTest {
	private static final String AREA = CompatGameTests.AREA;

	private static Pokemon make(String properties) {
		return PokemonProperties.Companion.parse(properties, " ", "=").create();
	}

	/** {@code pokemon} saved and loaded again, as the keeper holds her boarders. */
	private static Pokemon stored(GameTestHelper helper, Pokemon pokemon) {
		Pokemon loaded = CobblemonDaycareKeeper.load(helper.getLevel(), pokemon.saveToNBT(helper.getLevel().registryAccess(), new CompoundTag()));
		helper.assertTrue(loaded != null, "a parent didn't load: " + pokemon.getSpecies().getName());
		return loaded;
	}

	private static String id(Pokemon pokemon) {
		return pokemon.getSpecies().getResourceIdentifier().getPath();
	}

	private static String describe(Pokemon pokemon) {
		return pokemon.getForm().getName() + " " + id(pokemon) + " " + pokemon.getForm().getAspects();
	}

	/** The mother (first) and father (second) as stored, and their hatchling. */
	private static Pokemon hatch(GameTestHelper helper, String mother, String father, int seed) {
		Pokemon m = stored(helper, make(mother));
		Pokemon f = stored(helper, make(father));
		return CobblemonDaycareKeeper.hatchling(f, m, RandomSource.create(seed));
	}

	/** Asserts {@code baby} is a level-1 {@code species} in the form named {@code form} (Cobblemon's form name). */
	private static void expect(GameTestHelper helper, String parents, Pokemon baby, String species, String form) {
		helper.assertTrue(id(baby).equals(species) && baby.getForm().getName().equalsIgnoreCase(form) && baby.getLevel() == 1,
			parents + " hatched a level-" + baby.getLevel() + " " + describe(baby) + "; wanted a " + form + " " + species);
		helper.assertTrue(baby.getSpecies().getForms().contains(baby.getForm()) || baby.getForm() == baby.getSpecies().getStandardForm(),
			parents + ": the hatchling's form " + baby.getForm().getName() + " isn't one of " + species + "'s");
	}

	/** Asserts {@code baby} is a {@code species} in no regional form (none of the regional aspects). */
	private static void expectPlain(GameTestHelper helper, String parents, Pokemon baby, String species) {
		helper.assertTrue(id(baby).equals(species), parents + " hatched a " + describe(baby) + "; wanted a " + species);
		for (String aspect : baby.getForm().getAspects()) {
			helper.assertTrue(!List.of("alolan", "galarian", "hisuian", "paldean").contains(aspect),
				parents + " hatched a regional " + describe(baby) + "; wanted a plain " + species);
		}
		helper.assertTrue(baby.getSpecies().getForms().contains(baby.getForm()) || baby.getForm() == baby.getSpecies().getStandardForm(),
			parents + ": the hatchling's form " + baby.getForm().getName() + " isn't one of " + species + "'s");
	}

	/** Galar: a female Galarian Rapidash with a (Kanto) Rapidash hatches a Galarian Ponyta; a Galarian Ponyta mother too. */
	//$ gametest 'FabricGameTest.EMPTY_STRUCTURE'
	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
	public void qaGalarianMothersHatchGalarianBabies(GameTestHelper helper) {
		expect(helper, "Galarian Rapidash x Rapidash", hatch(helper, "rapidash galarian gender=female level=40", "rapidash gender=male level=40", 1), "ponyta", "galar");
		expect(helper, "Galarian Ponyta x Ponyta", hatch(helper, "ponyta galarian gender=female level=20", "ponyta gender=male level=20", 2), "ponyta", "galar");
		expect(helper, "Galarian Zigzagoon x Linoone", hatch(helper, "zigzagoon galarian gender=female level=20", "linoone gender=male level=40", 3), "zigzagoon", "galar");
		expect(helper, "Galarian Slowbro x Slowking", hatch(helper, "slowbro galarian gender=female level=40", "slowking gender=male level=40", 4), "slowpoke", "galar");
		helper.succeed();
	}

	/** Regional evolutions that are species of their own lead back to the regional base form: Perrserker, Sirfetch'd, Cursola, Obstagoon (two steps). */
	//$ gametest 'FabricGameTest.EMPTY_STRUCTURE'
	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
	public void qaRegionalSpeciesHatchTheirRegionalBase(GameTestHelper helper) {
		expect(helper, "Perrserker x Persian", hatch(helper, "perrserker gender=female level=40", "persian gender=male level=40", 1), "meowth", "galar");
		expect(helper, "Sirfetch'd x Farfetch'd", hatch(helper, "sirfetchd gender=female level=40", "farfetchd gender=male level=40", 2), "farfetchd", "galar");
		expect(helper, "Cursola x Corsola", hatch(helper, "cursola gender=female level=40", "corsola gender=male level=40", 3), "corsola", "galar");
		expect(helper, "Obstagoon x Linoone", hatch(helper, "obstagoon gender=female level=40", "linoone gender=male level=40", 4), "zigzagoon", "galar");
		helper.succeed();
	}

	/** Hisui: Hisuian Arcanine hatches a Hisuian Growlithe, Sneasler a Hisuian Sneasel, Overqwil a Hisuian Qwilfish. */
	//$ gametest 'FabricGameTest.EMPTY_STRUCTURE'
	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
	public void qaHisuianMothersHatchHisuianBabies(GameTestHelper helper) {
		expect(helper, "Hisuian Arcanine x Arcanine", hatch(helper, "arcanine hisuian gender=female level=40", "arcanine gender=male level=40", 1), "growlithe", "hisui");
		expect(helper, "Hisuian Growlithe x Hisuian Growlithe", hatch(helper, "growlithe hisuian gender=female level=20", "growlithe hisuian gender=male level=20", 2), "growlithe", "hisui");
		expect(helper, "Sneasler x Weavile", hatch(helper, "sneasler gender=female level=40", "weavile gender=male level=40", 3), "sneasel", "hisui");
		expect(helper, "Overqwil x Qwilfish", hatch(helper, "overqwil gender=female level=40", "qwilfish gender=male level=40", 4), "qwilfish", "hisui");
		helper.succeed();
	}

	/** Paldea: a Paldean Wooper mother hatches a Paldean Wooper, and so does Clodsire (Paldean Wooper's own evolution). */
	//$ gametest 'FabricGameTest.EMPTY_STRUCTURE'
	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
	public void qaPaldeanMothersHatchPaldeanBabies(GameTestHelper helper) {
		expect(helper, "Paldean Wooper x Wooper", hatch(helper, "wooper paldean gender=female level=20", "wooper gender=male level=20", 1), "wooper", "paldea");
		expect(helper, "Clodsire x Quagsire", hatch(helper, "clodsire gender=female level=40", "quagsire gender=male level=40", 2), "wooper", "paldea");
		helper.succeed();
	}

	/** The reverse: a Kanto (plain) mother hatches the plain form whatever the father's region. */
	//$ gametest 'FabricGameTest.EMPTY_STRUCTURE'
	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
	public void qaAPlainMotherHatchesThePlainFormWhateverTheFather(GameTestHelper helper) {
		expectPlain(helper, "Rapidash x Galarian Rapidash", hatch(helper, "rapidash gender=female level=40", "rapidash galarian gender=male level=40", 1), "ponyta");
		expectPlain(helper, "Growlithe x Hisuian Arcanine", hatch(helper, "growlithe gender=female level=20", "arcanine hisuian gender=male level=40", 2), "growlithe");
		expectPlain(helper, "Quagsire x Clodsire", hatch(helper, "quagsire gender=female level=40", "clodsire gender=male level=40", 3), "wooper");
		expectPlain(helper, "Persian x Perrserker", hatch(helper, "persian gender=female level=40", "perrserker gender=male level=40", 4), "meowth");
		expectPlain(helper, "Vulpix x Alolan Ninetales", hatch(helper, "vulpix gender=female level=20", "ninetales alolan gender=male level=40", 5), "vulpix");
		helper.succeed();
	}

	/**
	 * A regional evolution of a baby with no regional form hatches that plain baby (a valid form, no crash): Alolan
	 * Raichu a Pichu, Mr. Rime and Galarian Mr. Mime a Mime Jr., Hisuian Typhlosion a Cyndaquil, Alolan Marowak a
	 * Cubone, Galarian Weezing a Koffing, Alolan Exeggutor an Exeggcute.
	 */
	//$ gametest 'FabricGameTest.EMPTY_STRUCTURE'
	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
	public void qaARegionalEvolutionOfAPlainBabyHatchesThePlainBaby(GameTestHelper helper) {
		expectPlain(helper, "Alolan Raichu x Raichu", hatch(helper, "raichu alolan gender=female level=40", "raichu gender=male level=40", 1), "pichu");
		expectPlain(helper, "Mr. Rime x Mr. Mime", hatch(helper, "mrrime gender=female level=40", "mrmime gender=male level=40", 2), "mimejr");
		expectPlain(helper, "Galarian Mr. Mime x Mr. Mime", hatch(helper, "mrmime galarian gender=female level=40", "mrmime gender=male level=40", 3), "mimejr");
		expectPlain(helper, "Hisuian Typhlosion x Typhlosion", hatch(helper, "typhlosion hisuian gender=female level=40", "typhlosion gender=male level=40", 4), "cyndaquil");
		expectPlain(helper, "Alolan Marowak x Marowak", hatch(helper, "marowak alolan gender=female level=40", "marowak gender=male level=40", 5), "cubone");
		expectPlain(helper, "Galarian Weezing x Weezing", hatch(helper, "weezing galarian gender=female level=40", "weezing gender=male level=40", 6), "koffing");
		expectPlain(helper, "Alolan Exeggutor x Exeggutor", hatch(helper, "exeggutor alolan gender=female level=40", "exeggutor gender=male level=40", 7), "exeggcute");
		helper.succeed();
	}

	/**
	 * "Or the parent that isn't Ditto": a male Galarian Rapidash with Ditto hatches a Galarian Ponyta (either order), a
	 * Paldean Tauros (Blaze Breed, all male) a Blaze Breed Tauros, and a male Alolan Ninetales an Alolan Vulpix.
	 */
	//$ gametest 'FabricGameTest.EMPTY_STRUCTURE'
	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
	public void qaDittoPairsHatchTheOtherParentsRegionalForm(GameTestHelper helper) {
		Pokemon ditto = stored(helper, make("ditto level=40"));
		Pokemon rapidash = stored(helper, make("rapidash galarian gender=male level=40"));
		expect(helper, "Ditto x Galarian Rapidash", CobblemonDaycareKeeper.hatchling(ditto, rapidash, RandomSource.create(1)), "ponyta", "galar");
		expect(helper, "Galarian Rapidash x Ditto", CobblemonDaycareKeeper.hatchling(rapidash, ditto, RandomSource.create(2)), "ponyta", "galar");
		Pokemon tauros = stored(helper, make("tauros paldean bull_breed=blaze level=40"));
		helper.assertTrue(tauros.getForm().getName().equalsIgnoreCase("paldea-blaze"), "the Tauros isn't a Blaze Breed: " + describe(tauros));
		Pokemon blaze = CobblemonDaycareKeeper.hatchling(tauros, ditto, RandomSource.create(3));
		expect(helper, "Blaze Tauros x Ditto", blaze, "tauros", "paldea-blaze");
		// (Saved and loaded again, it turns Combat Breed: QaDaycareRegional1104d2BugCompatTests, not registered)
		Pokemon ninetales = stored(helper, make("ninetales alolan gender=male level=40"));
		expect(helper, "Ditto x male Alolan Ninetales", CobblemonDaycareKeeper.hatchling(ditto, ninetales, RandomSource.create(4)), "vulpix", "alola");
		helper.succeed();
	}

	/**
	 * A shiny Alolan mother (her own aspects then hold "shiny" besides "alolan") still hatches an Alolan Vulpix, and the
	 * hatchling keeps its regional form when it's saved and loaded again (the PC, a restart).
	 */
	//$ gametest 'FabricGameTest.EMPTY_STRUCTURE'
	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
	public void qaAShinyRegionalMotherAndTheSavedHatchling(GameTestHelper helper) {
		Pokemon baby = hatch(helper, "ninetales alolan shiny=true gender=female level=40", "ninetales gender=male level=40", 1);
		expect(helper, "shiny Alolan Ninetales x Ninetales", baby, "vulpix", "alola");
		Pokemon galar = hatch(helper, "rapidash galarian gender=female level=40", "rapidash gender=male level=40", 2);
		for (Pokemon hatchling : List.of(baby, galar)) {
			Pokemon loaded = stored(helper, hatchling);
			helper.assertTrue(id(loaded).equals(id(hatchling)) && loaded.getForm() == hatchling.getForm()
					&& loaded.getAspects().containsAll(hatchling.getForm().getAspects()),
				"a " + describe(hatchling) + " loaded again as a " + describe(loaded) + " (aspects " + loaded.getAspects() + ")");
		}
		helper.succeed();
	}

	/**
	 * Without Cobbreeding, the hatchling goes to the party in its regional form and the sentence names the baby, not the
	 * mother: "Ponyta, just hatched" for a Galarian Rapidash mother, "Meowth, just hatched" for Perrserker.
	 */
	//$ gametest AREA
	@GameTest(template = AREA)
	public void qaTheHatchlingReachesThePartyAndIsNamedRight(GameTestHelper helper) {
		ServerPlayer player = helper.makeMockServerPlayerInLevel();
		player.setGameMode(GameType.SURVIVAL);
		PlayerPartyStore party = Cobblemon.INSTANCE.getStorage().getParty(player);
		party.add(make("bulbasaur level=10"));
		String[][] cases = {
			{"rapidash galarian gender=female level=40", "rapidash gender=male level=40", "ponyta", "galar", "Ponyta, just hatched"},
			{"perrserker gender=female level=40", "persian gender=male level=40", "meowth", "galar", "Meowth, just hatched"},
			{"clodsire gender=female level=40", "quagsire gender=male level=40", "wooper", "paldea", "Wooper, just hatched"},
		};
		int seed = 0;
		for (String[] c : cases) {
			Pokemon mother = stored(helper, make(c[0]));
			Pokemon father = stored(helper, make(c[1]));
			Component said = CobblemonDaycareKeeper.giveEgg(player, father, mother, RandomSource.create(++seed), false);
			helper.assertTrue(said != null && said.getString().equals(c[4]), c[0] + ": it says \"" + (said == null ? null : said.getString()) + "\", wanted \"" + c[4] + "\"");
			List<Pokemon> hatched = new ArrayList<>();
			for (Pokemon p : party) {
				if (p.getLevel() == 1 && id(p).equals(c[2])) {
					hatched.add(p);
				}
			}
			helper.assertTrue(hatched.size() == 1, c[0] + ": " + hatched.size() + " level-1 " + c[2] + " in the party");
			expect(helper, c[0] + " (in the party)", hatched.get(0), c[2], c[3]);
		}
		helper.succeed();
	}

	/** Cobbreeding's stored properties of an egg item (EggUtilities.extractProperties, by reflection: it's only on the runtime classpath). */
	private static PokemonProperties eggProperties(GameTestHelper helper, ItemStack egg) {
		try {
			Object out = Class.forName("ludichat.cobbreeding.EggUtilities").getMethod("extractProperties", ItemStack.class).invoke(null, egg);
			helper.assertTrue(out instanceof PokemonProperties, "the egg holds no Pokémon properties: " + out);
			return (PokemonProperties) out;
		} catch (ReflectiveOperationException e) {
			throw new IllegalStateException("Cobbreeding's EggUtilities.extractProperties isn't reachable", e);
		}
	}

	private static int eggSlot(ServerPlayer player) {
		for (int i = 0; i < player.getInventory().items.size(); i++) {
			if (CobblemonDaycareKeeper.isCobbreedingEgg(player.getInventory().items.get(i))) {
				return i;
			}
		}
		return -1;
	}

	/**
	 * With Cobbreeding (in the compat test mods), through the keeper: a Galarian Rapidash pair left with her, an egg
	 * collected for 4 emeralds' worth, says "You collected a Ponyta Egg." and is a Cobbreeding egg of a Galarian Ponyta;
	 * hatched (Cobbreeding's own /hatchpokemonegg, then the egg's tick), a level-1 Galarian Ponyta joins the party.
	 */
	//$ gametest AREA
	@GameTest(template = AREA)
	public void qaACobbreedingEggKeepsTheRegionalForm(GameTestHelper helper) {
		helper.assertTrue(CobblemonDaycareKeeper.cobbreeding(), "Cobbreeding isn't loaded in the compat tests");
		Villager keeper = DaycareKeeperCompatTests.keeper(helper);
		Pokemon mother = make("rapidash galarian gender=female level=40");
		Pokemon father = make("rapidash gender=male level=40");
		ServerPlayer player = helper.makeMockServerPlayerInLevel();
		player.setGameMode(GameType.SURVIVAL);
		PlayerPartyStore party = Cobblemon.INSTANCE.getStorage().getParty(player);
		party.add(make("bulbasaur level=10"));
		party.add(mother);
		party.add(father);
		helper.assertTrue(CobblemonDaycareKeeper.leave(player, keeper, mother, father), "the pair wasn't left");
		DaycareKeepers.setPairs(keeper, List.of(DaycareKeepers.pairs(keeper).get(0).withEggs(1, DaycareKeepers.day(helper.getLevel()))));
		CobbleDollarsBank.add(player, 1000);
		Component said = CobblemonDaycareKeeper.collect(player, keeper);
		helper.assertTrue(said != null && said.getString().equals("You collected a Ponyta Egg."), "collecting says \"" + (said == null ? null : said.getString()) + "\"");
		int slot = eggSlot(player);
		helper.assertTrue(slot >= 0, "no Cobbreeding egg in the inventory");
		ItemStack egg = player.getInventory().items.get(slot);
		PokemonProperties props = eggProperties(helper, egg);
		String species = props.getSpecies() == null ? "" : props.getSpecies();
		helper.assertTrue(species.substring(species.indexOf(':') + 1).equalsIgnoreCase("ponyta"), "the egg holds a " + props.asString(" "));
		helper.assertTrue("galar".equalsIgnoreCase(props.getForm()), "the egg holds " + props.asString(" ") + "; wanted a Galarian Ponyta (form=galar)");
		// Hatch it the player's way: in hand, Cobbreeding's hatch command, then the egg ticks in the inventory
		player.getInventory().selected = 0;
		if (slot != 0) {
			ItemStack first = player.getInventory().items.get(0);
			player.getInventory().items.set(0, egg);
			player.getInventory().items.set(slot, first);
		}
		helper.assertTrue(CobblemonDaycareKeeper.isCobbreedingEgg(player.getItemInHand(InteractionHand.MAIN_HAND)), "the egg isn't in hand");
		var source = player.createCommandSourceStack().withSuppressedOutput().withPermission(4);
		try {
			helper.getLevel().getServer().getCommands().getDispatcher().execute("hatchpokemonegg @s", source);
		} catch (com.mojang.brigadier.exceptions.CommandSyntaxException e) {
			throw new IllegalStateException("hatchpokemonegg failed: " + e.getMessage(), e);
		}
		for (int i = 0; i < 60 && CobblemonDaycareKeeper.isCobbreedingEgg(player.getInventory().items.get(0)); i++) {
			ItemStack held = player.getInventory().items.get(0);
			held.inventoryTick(helper.getLevel(), player, 0, true);
		}
		List<Pokemon> hatched = new ArrayList<>();
		for (Pokemon p : party) {
			if (id(p).equals("ponyta") && p.getLevel() == 1) {
				hatched.add(p);
			}
		}
		helper.assertTrue(hatched.size() == 1, "after hatching, " + hatched.size() + " level-1 Ponyta in the party (still held: "
			+ player.getInventory().items.get(0) + " " + player.getInventory().items.get(0).getComponentsPatch() + ")");
		helper.assertTrue(hatched.get(0).getForm().getName().equalsIgnoreCase("galar"), "Cobbreeding hatched a " + describe(hatched.get(0)));
		helper.succeed();
	}

	/** Cobbreeding's egg of other regions and species: Alolan Ninetales, Hisuian Arcanine, Clodsire, Perrserker; a plain mother's egg is plain. */
	//$ gametest AREA
	@GameTest(template = AREA)
	public void qaCobbreedingEggsOfEveryRegion(GameTestHelper helper) {
		helper.assertTrue(CobblemonDaycareKeeper.cobbreeding(), "Cobbreeding isn't loaded in the compat tests");
		ServerPlayer player = helper.makeMockServerPlayerInLevel();
		player.setGameMode(GameType.SURVIVAL);
		String[][] cases = {
			{"ninetales alolan gender=female level=40", "ninetales gender=male level=40", "vulpix", "alola"},
			{"arcanine hisuian gender=female level=40", "arcanine gender=male level=40", "growlithe", "hisui"},
			{"clodsire gender=female level=40", "quagsire gender=male level=40", "wooper", "paldea"},
			{"perrserker gender=female level=40", "persian gender=male level=40", "meowth", "galar"},
			{"ninetales gender=female level=40", "ninetales alolan gender=male level=40", "vulpix", "normal"},
			{"ninetales alolan gender=female level=40", "vulpix gender=male level=20", "vulpix", "alola"},
		};
		int seed = 0;
		for (String[] c : cases) {
			player.getInventory().clearContent();
			Pokemon mother = stored(helper, make(c[0]));
			Pokemon father = stored(helper, make(c[1]));
			Component said = CobblemonDaycareKeeper.giveEgg(player, father, mother, RandomSource.create(++seed), true);
			helper.assertTrue(said != null, c[0] + ": no egg given");
			int slot = eggSlot(player);
			helper.assertTrue(slot >= 0, c[0] + ": no Cobbreeding egg in the inventory");
			PokemonProperties props = eggProperties(helper, player.getInventory().items.get(slot));
			// Cobbreeding keeps the form as form=<showdown id> and turns it into the form's aspects when it hatches
			String species = props.getSpecies() == null ? "" : props.getSpecies();
			String form = props.getForm() == null || props.getForm().isEmpty() ? "normal" : props.getForm();
			helper.assertTrue(species.substring(species.indexOf(':') + 1).equalsIgnoreCase(c[2]) && form.equalsIgnoreCase(c[3]),
				c[0] + ": the egg holds " + props.asString(" ") + "; wanted a " + c[3] + " " + c[2]);
		}
		helper.succeed();
	}
}
