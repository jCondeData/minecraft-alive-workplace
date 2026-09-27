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

	/** Trainer teams: the same for the same trainer and tier, bigger and stronger as the tier goes up, no legendaries. */
	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
	public void trainerTeamsScaleWithTier(GameTestHelper helper) {
		java.util.UUID id = java.util.UUID.randomUUID();
		java.util.List<Pokemon> novice = io.github.jcondedata.aliveworkplace.compat.cobblemon.CobblemonTrainers.team(id, 1);
		java.util.List<Pokemon> master = io.github.jcondedata.aliveworkplace.compat.cobblemon.CobblemonTrainers.team(id, 5);
		helper.assertTrue(novice.size() == 2 && master.size() == 6, "team sizes " + novice.size() + " / " + master.size());
		helper.assertTrue(novice.stream().allMatch(p -> p.getLevel() >= 5 && p.getLevel() <= 12), "novice levels");
		helper.assertTrue(master.stream().allMatch(p -> p.getLevel() >= 80), "master levels");
		helper.assertTrue(master.stream().noneMatch(p -> p.getSpecies().getLabels().contains("legendary")), "a legendary in a trainer's team");
		java.util.List<String> again = io.github.jcondedata.aliveworkplace.compat.cobblemon.CobblemonTrainers.team(id, 5).stream()
			.map(p -> p.getSpecies().getName()).toList();
		helper.assertTrue(again.equals(master.stream().map(p -> p.getSpecies().getName()).toList()), "the team changed between battles");
		helper.succeed();
	}

	/** A player challenges a trainer: a real battle starts; winning pays and both sides learn from it. */
	@GameTest(template = AREA, timeoutTicks = 400)
	public void trainerBattleStartsAndPays(GameTestHelper helper) {
		helper.setDayTime(2000);
		BlockPos post = new BlockPos(2, 1, 2);
		helper.setBlock(post, ModBlocks.TRAINING_POST);
		Villager trainer = helper.spawn(EntityType.VILLAGER, new BlockPos(3, 1, 3));
		Jobs.employ(helper.getLevel(), trainer, helper.absolutePos(post), ModVillagers.TRAINING_POST_POI, ModVillagers.TRAINER);

		ServerPlayer player = helper.makeMockServerPlayerInLevel();
		player.setGameMode(GameType.SURVIVAL);
		PlayerPartyStore party = Cobblemon.INSTANCE.getStorage().getParty(player);
		party.add(PokemonProperties.Companion.parse("charmander level=30", " ", "=").create());

		io.github.jcondedata.aliveworkplace.trainer.Trainers.challenge(player, trainer);
		var battle = com.cobblemon.mod.common.battles.BattleRegistry.getBattleByParticipatingPlayer(player);
		helper.assertTrue(battle != null, "no battle started");
		int xpBefore = trainer.getVillagerXp();
		io.github.jcondedata.aliveworkplace.compat.cobblemon.CobblemonTrainers.finishForTest(battle, true);
		helper.assertTrue(player.getInventory().countItem(net.minecraft.world.item.Items.EMERALD) == 1, "no prize (1 emerald without CobbleDollars)");
		helper.assertTrue(trainer.getVillagerXp() > xpBefore, "the trainer got no XP from the battle");
		helper.assertTrue(trainer.getAttachedOrElse(io.github.jcondedata.aliveworkplace.registry.ModAttachments.TRAINER_BATTLES, 0) == 1, "battle not counted");
		helper.succeed();
	}
}
