package io.github.jcondedata.aliveworkplace.compat;

import com.cobblemon.mod.common.Cobblemon;
import com.cobblemon.mod.common.api.pokemon.PokemonProperties;
import com.cobblemon.mod.common.api.storage.party.PlayerPartyStore;
import com.cobblemon.mod.common.pokemon.Pokemon;
import io.github.jcondedata.aliveworkplace.compat.cobblemon.CobblemonDaycareKeeper;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.GameType;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;

/**
 * QA (2026-11-04, lane d): a failing test for a bug found in ROADMAP 28.12's hatchling (B854's area). NOT registered in
 * the compat test mod's fabric.mod.json (main stays green); the builder registers this class with the fix. Bug: a Blaze
 * Breed Paldean Tauros (with Ditto) hatches a Blaze Breed Tauros, but saved and loaded again (the party or PC after a
 * restart) it is a Combat Breed Tauros. The hatchling's form is set with setForm from the form's aspects
 * ("blaze-breed paldean"), but "blaze-breed" isn't a property Cobblemon parses (the feature is bull_breed=blaze), so
 * the saved feature stays the default (combat) and picks Combat Breed again on load.
 */
public class QaDaycareRegional1104d2BugCompatTests implements FabricGameTest {
	private static final String AREA = CompatGameTests.AREA;

	private static Pokemon make(String properties) {
		return PokemonProperties.Companion.parse(properties, " ", "=").create();
	}

	private static Pokemon stored(GameTestHelper helper, Pokemon pokemon) {
		Pokemon loaded = CobblemonDaycareKeeper.load(helper.getLevel(), pokemon.saveToNBT(helper.getLevel().registryAccess(), new CompoundTag()));
		helper.assertTrue(loaded != null, "a Pokémon didn't load");
		return loaded;
	}

	/** A Blaze Breed Tauros with Ditto hatches a Blaze Breed Tauros that stays one in the party after it's saved and loaded again. */
	//$ gametest AREA
	@GameTest(template = AREA)
	public void qaABlazeBreedTaurosHatchlingStaysBlazeAfterReload(GameTestHelper helper) {
		Pokemon tauros = stored(helper, make("tauros paldean bull_breed=blaze level=40"));
		helper.assertTrue(tauros.getForm().getName().equalsIgnoreCase("paldea-blaze"), "the mother isn't a Blaze Breed even saved: " + tauros.getForm().getName());
		Pokemon ditto = stored(helper, make("ditto level=40"));
		ServerPlayer player = helper.makeMockServerPlayerInLevel();
		player.setGameMode(GameType.SURVIVAL);
		PlayerPartyStore party = Cobblemon.INSTANCE.getStorage().getParty(player);
		CobblemonDaycareKeeper.giveEgg(player, ditto, tauros, RandomSource.create(3), false);
		Pokemon baby = null;
		for (Pokemon p : party) {
			if (p.getLevel() == 1) {
				baby = p;
			}
		}
		helper.assertTrue(baby != null && baby.getForm().getName().equalsIgnoreCase("paldea-blaze"),
			"the hatchling isn't a Blaze Breed Tauros: " + (baby == null ? null : baby.getForm().getName()));
		Pokemon loaded = stored(helper, baby);
		helper.assertTrue(loaded.getForm().getName().equalsIgnoreCase("paldea-blaze"),
			"a Blaze Breed hatchling loaded again as " + loaded.getForm().getName() + " (aspects " + loaded.getAspects() + ", before saving "
				+ baby.getAspects() + ")");
		helper.succeed();
	}

	/** B955's fix in general: an Aqua Breed Tauros (with Ditto) hatches an Aqua Breed Tauros, still one after a save and load. */
	//$ gametest 'FabricGameTest.EMPTY_STRUCTURE'
	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
	public void qaAnAquaBreedTaurosHatchlingStaysAquaAfterReload(GameTestHelper helper) {
		Pokemon tauros = stored(helper, make("tauros paldean bull_breed=aqua level=40"));
		helper.assertTrue(tauros.getForm().getName().equalsIgnoreCase("paldea-aqua"), "the mother isn't an Aqua Breed even saved: " + tauros.getForm().getName());
		Pokemon baby = CobblemonDaycareKeeper.hatchling(stored(helper, make("ditto level=40")), tauros, RandomSource.create(5));
		helper.assertTrue(baby.getForm().getName().equalsIgnoreCase("paldea-aqua"), "the hatchling isn't an Aqua Breed Tauros: " + baby.getForm().getName());
		Pokemon loaded = stored(helper, baby);
		helper.assertTrue(loaded.getForm().getName().equalsIgnoreCase("paldea-aqua"),
			"an Aqua Breed hatchling loaded again as " + loaded.getForm().getName() + " (aspects " + loaded.getAspects() + ")");
		helper.succeed();
	}
}
