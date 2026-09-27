package io.github.jcondedata.aliveworkplace.compat;

import com.cobblemon.mod.common.Cobblemon;
import com.cobblemon.mod.common.api.pokemon.PokemonProperties;
import com.cobblemon.mod.common.api.storage.party.PlayerPartyStore;
import com.cobblemon.mod.common.pokemon.Pokemon;
import io.github.jcondedata.aliveworkplace.nurse.Nurses;
import io.github.jcondedata.aliveworkplace.registry.ModBlocks;
import io.github.jcondedata.aliveworkplace.registry.ModVillagers;
import io.github.jcondedata.aliveworkplace.work.Jobs;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.level.GameType;

/** Alive Workplace with the real Cobblemon installed. */
public class CobblemonCompatTests implements FabricGameTest {
	private static final String AREA = CompatGameTests.AREA;

	/** A nurse heals the player's Pokémon too. */
	@GameTest(template = AREA)
	public void nurseHealsTheParty(GameTestHelper helper) {
		helper.setDayTime(2000);
		BlockPos station = new BlockPos(2, 1, 2);
		helper.setBlock(station, ModBlocks.NURSE_STATION);
		Villager nurse = helper.spawn(EntityType.VILLAGER, new BlockPos(3, 1, 3));
		Jobs.employ(helper.getLevel(), nurse, helper.absolutePos(station), ModVillagers.NURSE_STATION_POI, ModVillagers.NURSE);

		ServerPlayer player = helper.makeMockServerPlayerInLevel();
		player.setGameMode(GameType.SURVIVAL);
		PlayerPartyStore party = Cobblemon.INSTANCE.getStorage().getParty(player);
		Pokemon pokemon = PokemonProperties.Companion.parse("pikachu level=20", " ", "=").create();
		party.add(pokemon);
		pokemon.setCurrentHealth(1);
		helper.assertFalse(pokemon.isFullHealth(), "setup: the Pokémon should be hurt");

		Nurses.resetCooldowns();
		Nurses.treat(player, nurse);
		helper.assertTrue(pokemon.isFullHealth(), "the Pokémon was not healed (" + pokemon.getCurrentHealth() + "/" + pokemon.getMaxHealth() + ")");
		helper.succeed();
	}
}
