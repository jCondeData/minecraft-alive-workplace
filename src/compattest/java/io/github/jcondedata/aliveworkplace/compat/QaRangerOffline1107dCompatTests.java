package io.github.jcondedata.aliveworkplace.compat;

import com.cobblemon.mod.common.Cobblemon;
import com.cobblemon.mod.common.api.pokemon.PokemonProperties;
import com.cobblemon.mod.common.api.storage.pc.PCStore;
import com.cobblemon.mod.common.block.entity.PokemonPastureBlockEntity;
import com.cobblemon.mod.common.entity.pokemon.PokemonEntity;
import com.cobblemon.mod.common.pokemon.Pokemon;
import io.github.jcondedata.aliveworkplace.compat.cobblemon.CobblemonRanger;
import io.github.jcondedata.aliveworkplace.legend.WildPokemon;
import java.util.UUID;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.phys.Vec3;

/**
 * QA (lane D, 2026-11-07) of the Pokémon Ranger with Cobblemon (ROADMAP 29.22, CobblemonRanger.befriend): a wild Pokémon
 * the Ranger befriends goes to the village owner's PC first, and into the village's pasture only when the owner is there
 * to let it out; with the owner offline it waits in their PC ("befriended_pc" in the chronicle). Kills the nightly
 * survivor at CobblemonRanger:133 (the online and offline PC lookups swapped), which no test reached: every Ranger test
 * has its owner online.
 */
public class QaRangerOffline1107dCompatTests implements FabricGameTest {
	private static final String AREA = CompatGameTests.AREA;

	/**
	 * The owner is offline (never on this server): the befriended wild Pidgey is gone from the world, the answer is
	 * "in the PC", the pasture stays empty, and the Pidgey is in that owner's PC, there after the PC is looked up again.
	 */
	//$ gametest_ticks_batch AREA '100' '"qa_ranger_offline_1107d"'
	@GameTest(template = AREA, timeoutTicks = 100, batch = "qa_ranger_offline_1107d")
	public void aPokemonBefriendedForAnOfflineOwnerWaitsInTheirPc(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		BlockPos pasture = PastureCompatTests.pasture(helper, new BlockPos(10, 2, 10));
		PokemonEntity pidgey = PokemonProperties.Companion.parse("pidgey level=12", " ", "=").createEntity(level);
		pidgey.setPos(helper.absoluteVec(new Vec3(5.5, 2, 5.5)));
		pidgey.setNoAi(true);
		level.addFreshEntity(pidgey);
		Pokemon pokemon = pidgey.getPokemon();
		UUID owner = UUID.randomUUID();
		helper.assertTrue(level.getServer().getPlayerList().getPlayer(owner) == null, "setup: the owner should be offline");
		WildPokemon.Befriended result = new CobblemonRanger().befriend(level, pidgey, pasture, owner);
		helper.assertTrue(result == WildPokemon.Befriended.IN_PC, "an offline owner's befriended Pidgey: expected IN_PC, got " + result);
		helper.assertTrue(!pidgey.isAlive(), "the befriended Pidgey is still out in the world");
		PokemonPastureBlockEntity block = (PokemonPastureBlockEntity) level.getBlockEntity(pasture);
		helper.assertTrue(block.getTetheredPokemon().isEmpty(), "the pasture let out a Pokémon with its owner offline");
		PCStore pc = Cobblemon.INSTANCE.getStorage().getPC(owner, level.registryAccess());
		boolean found = false;
		for (Pokemon p : pc) {
			found |= p.getUuid().equals(pokemon.getUuid());
		}
		helper.assertTrue(found, "the Pidgey isn't in the offline owner's PC");
		helper.succeed();
	}
}
