package io.github.jcondedata.aliveworkplace.compat;

import com.cobblemon.mod.common.Cobblemon;
import com.cobblemon.mod.common.api.pokemon.PokemonProperties;
import com.cobblemon.mod.common.api.storage.party.PlayerPartyStore;
import com.cobblemon.mod.common.pokemon.Pokemon;
import io.github.jcondedata.aliveworkplace.compat.cobblemon.CobblemonDaycareKeeper;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameType;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;

/**
 * QA (2026-11-08, lane d): failing tests for two bugs in ROADMAP 28.12's hatchling ("in the base form of the mother (or
 * the parent that isn't Ditto)"), found past B955's fix. NOT registered in the compat test mod's fabric.mod.json (main
 * stays green); the builder registers this class with the fix.
 * <ul>
 * <li>A mother in her species' standard form whose form is picked by a choice feature with a random default (a West Sea
 * Gastrodon or Shellos: sea, default random; a Baile Oricorio: dance_style, default random): the standard FormData has no
 * aspects, so the hatchling is made as a bare "shellos", its sea is rolled at random, and setForm(standard) only hides it
 * until the hatchling is saved and loaded: about half of them come back East Sea (or another style).</li>
 * <li>A female Basculegion (form F, aspects [female]) hatches a Red-Striped Basculin: no Basculin form evolves into
 * Basculegion with the aspects [female], so preForm falls back to Cobblemon's resolution of "basculin fish_stripes=white",
 * the standard Red-Striped. The male Basculegion (QaDaycareChoiceForms1108dCompatTests) hatches White-Striped.</li>
 * <li>With Cobbreeding (the pack's path): a Blaze Breed Tauros's egg holds form=paldeablaze but hatches a Combat Breed
 * Tauros: the givepokemonegg command carries the form, not the bull_breed=blaze feature B955 sets on the hatchling.</li>
 * </ul>
 */
public class QaDaycareChoiceForms1108dBugCompatTests implements FabricGameTest {
	private static final String AREA = CompatGameTests.AREA;

	private static Pokemon make(String properties) {
		return PokemonProperties.Companion.parse(properties, " ", "=").create();
	}

	/** {@code pokemon} saved and loaded again (the keeper's boarders, and a party or PC after a restart). */
	private static Pokemon stored(GameTestHelper helper, Pokemon pokemon) {
		Pokemon loaded = CobblemonDaycareKeeper.load(helper.getLevel(), pokemon.saveToNBT(helper.getLevel().registryAccess(), new CompoundTag()));
		helper.assertTrue(loaded != null, "a Pokémon didn't load: " + pokemon.getSpecies().getName());
		return loaded;
	}

	private static String id(Pokemon pokemon) {
		return pokemon.getSpecies().getResourceIdentifier().getPath();
	}

	private static String describe(Pokemon pokemon) {
		return pokemon.getForm().getName() + " " + id(pokemon) + " (aspects " + pokemon.getAspects() + ")";
	}

	private static String features(Pokemon pokemon) {
		StringBuilder out = new StringBuilder();
		for (var feature : pokemon.getFeatures()) {
			out.append(feature.getName()).append('=').append(feature instanceof com.cobblemon.mod.common.api.pokemon.feature.StringSpeciesFeature s ? s.getValue() : "?").append(' ');
		}
		return out.toString().trim();
	}

	/**
	 * Hatches {@code mother} with {@code father} (both as stored) {@code times} times, and asserts every hatchling is a
	 * level-1 {@code species} in the form named {@code form}, before and after it is saved and loaded.
	 */
	private static void expect(GameTestHelper helper, String mother, String father, String species, String form, int times) {
		Pokemon m = stored(helper, make(mother));
		Pokemon f = stored(helper, make(father));
		for (int i = 0; i < times; i++) {
			Pokemon baby = CobblemonDaycareKeeper.hatchling(f, m, RandomSource.create(100 + i));
			helper.assertTrue(id(baby).equals(species) && baby.getForm().getName().equalsIgnoreCase(form) && baby.getLevel() == 1,
				mother + " x " + father + " hatched a level-" + baby.getLevel() + " " + describe(baby) + " (hatch " + (i + 1) + "); wanted a " + form + " " + species
					+ " (mother " + describe(m) + " features " + features(m) + ")");
			Pokemon loaded = stored(helper, baby);
			helper.assertTrue(id(loaded).equals(species) && loaded.getForm().getName().equalsIgnoreCase(form),
				mother + " x " + father + ": a " + form + " " + species + " hatchling loaded again as " + describe(loaded) + " (hatch " + (i + 1)
					+ "; before saving " + describe(baby) + " features " + features(baby) + ", mother " + describe(m) + ")");
		}
	}

	/** A West Sea Gastrodon (and a West Sea Shellos with Ditto) hatches West Sea Shellos that stay West Sea after a save and load, every time. */
	//$ gametest 'FabricGameTest.EMPTY_STRUCTURE'
	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
	public void qaAWestSeaMothersHatchlingsStayWestSeaAfterReload(GameTestHelper helper) {
		expect(helper, "gastrodon sea=west gender=female level=40", "gastrodon sea=east gender=male level=40", "shellos", "normal", 8);
		expect(helper, "ditto level=40", "shellos sea=west gender=male level=20", "shellos", "normal", 8);
		helper.succeed();
	}

	/** A Baile Oricorio (the standard form) hatches Baile Oricorio that stay Baile after a save and load, every time. */
	//$ gametest 'FabricGameTest.EMPTY_STRUCTURE'
	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
	public void qaABaileOricoriosHatchlingsStayBaileAfterReload(GameTestHelper helper) {
		expect(helper, "oricorio dance_style=baile gender=female level=30", "oricorio dance_style=sensu gender=male level=30", "oricorio", "normal", 8);
		helper.succeed();
	}

	/** A female Basculegion, with a male Basculegion or with Ditto, hatches a White-Striped Basculin (the only Basculin that evolves into Basculegion). */
	//$ gametest 'FabricGameTest.EMPTY_STRUCTURE'
	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
	public void qaAFemaleBasculegionHatchesAWhiteStripedBasculin(GameTestHelper helper) {
		expect(helper, "basculegion gender=female level=40", "basculegion gender=male level=40", "basculin", "white-striped", 3);
		expect(helper, "basculegion gender=female level=40", "ditto level=40", "basculin", "white-striped", 3);
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

	/**
	 * With Cobbreeding: the egg of a Blaze Breed Tauros (stored) and Ditto is named "a Tauros Egg", holds a Blaze
	 * Breed Tauros, and hatched the player's way (in hand, Cobbreeding's hatch command, the egg's ticks) gives a level-1
	 * Blaze Breed Tauros that is still Blaze Breed after it is saved and loaded (B955 through the egg).
	 */
	//$ gametest AREA
	@GameTest(template = AREA)
	public void qaACobbreedingEggOfABlazeBreedTaurosHatchesOneThatStaysBlaze(GameTestHelper helper) {
		helper.assertTrue(CobblemonDaycareKeeper.cobbreeding(), "Cobbreeding isn't loaded in the compat tests");
		ServerPlayer player = helper.makeMockServerPlayerInLevel();
		player.setGameMode(GameType.SURVIVAL);
		player.getInventory().clearContent();
		PlayerPartyStore party = Cobblemon.INSTANCE.getStorage().getParty(player);
		Pokemon tauros = stored(helper, make("tauros paldean bull_breed=blaze gender=female level=40"));
		helper.assertTrue(tauros.getForm().getName().equalsIgnoreCase("paldea-blaze"), "the mother isn't a Blaze Breed: " + describe(tauros));
		Component said = CobblemonDaycareKeeper.giveEgg(player, stored(helper, make("ditto level=40")), tauros, RandomSource.create(7), true);
		helper.assertTrue(said != null && said.getString().equals("a Tauros Egg"), "the egg says \"" + (said == null ? null : said.getString()) + "\"");
		int slot = -1;
		for (int i = 0; i < player.getInventory().items.size(); i++) {
			if (CobblemonDaycareKeeper.isCobbreedingEgg(player.getInventory().items.get(i))) {
				slot = i;
			}
		}
		helper.assertTrue(slot >= 0, "no Cobbreeding egg in the inventory");
		ItemStack egg = player.getInventory().items.get(slot);
		PokemonProperties props = eggProperties(helper, egg);
		// Cobbreeding keeps the form as its Showdown id ("paldeablaze")
		helper.assertTrue(props.getForm() != null && props.getForm().replace("-", "").equalsIgnoreCase("paldeablaze"),
			"the egg holds " + props.asString(" ") + "; wanted a Blaze Breed Tauros (form=paldeablaze)");
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
			player.getInventory().items.get(0).inventoryTick(helper.getLevel(), player, 0, true);
		}
		List<Pokemon> hatched = new ArrayList<>();
		for (Pokemon p : party) {
			if (id(p).equals("tauros") && p.getLevel() == 1) {
				hatched.add(p);
			}
		}
		helper.assertTrue(hatched.size() == 1, "after hatching, " + hatched.size() + " level-1 Tauros in the party");
		helper.assertTrue(hatched.get(0).getForm().getName().equalsIgnoreCase("paldea-blaze"), "Cobbreeding hatched a " + describe(hatched.get(0)));
		Pokemon loaded = stored(helper, hatched.get(0));
		helper.assertTrue(loaded.getForm().getName().equalsIgnoreCase("paldea-blaze"), "the egg's Blaze Breed Tauros loaded again as " + describe(loaded));
		helper.succeed();
	}
}
