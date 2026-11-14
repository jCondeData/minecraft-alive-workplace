package io.github.jcondedata.aliveworkplace.compat;

import com.cobblemon.mod.common.Cobblemon;
import com.cobblemon.mod.common.api.battles.model.PokemonBattle;
import com.cobblemon.mod.common.api.pokemon.PokemonProperties;
import com.cobblemon.mod.common.battles.BattleRegistry;
import io.github.jcondedata.aliveworkplace.compat.cobblemon.CobblemonTrainers;
import io.github.jcondedata.aliveworkplace.registry.ModAttachments;
import io.github.jcondedata.aliveworkplace.registry.ModBlocks;
import io.github.jcondedata.aliveworkplace.registry.ModVillagers;
import io.github.jcondedata.aliveworkplace.trainer.Trainers;
import io.github.jcondedata.aliveworkplace.work.Jobs;
import java.util.UUID;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.Vec3;

/**
 * B1256: when a battle's tick throws, Cobblemon logs "Exception while ticking a battle. Saving battle log." and stops the
 * battle (PokemonBattle.stop: a forced tie, then end, which closes it in the BattleRegistry) without a BATTLE_VICTORY or
 * BATTLE_FLED event. The trainer then answered every challenge "busy" until the server restarted. This stops a trainer's
 * battle exactly that way and challenges the trainer again: the second battle starts, and the stopped one counts for
 * nothing (no battle on the trainer's record).
 */
public class TrainerCrashedBattleCompatTests implements FabricGameTest {
	private static final String AREA = CompatGameTests.AREA;

	//$ gametest_ticks_batch AREA '400' '"b1256_crashed_battle"'
	@GameTest(template = AREA, timeoutTicks = 400, batch = "b1256_crashed_battle")
	public void aTrainerWhoseBattleCrashedTakesTheNextChallenge(GameTestHelper helper) {
		PartnerShowsCompatTests.clearLeftovers(helper);
		helper.setDayTime(2000);
		BlockPos post = new BlockPos(2, 2, 2);
		helper.setBlock(post, ModBlocks.TRAINING_POST);
		Villager trainer = helper.spawn(EntityType.VILLAGER, new BlockPos(3, 2, 3));
		trainer.setNoAi(true);
		Jobs.employ(helper.getLevel(), trainer, helper.absolutePos(post), ModVillagers.TRAINING_POST_POI, ModVillagers.TRAINER);

		ServerPlayer player = helper.makeMockServerPlayerInLevel();
		player.setGameMode(GameType.SURVIVAL);
		player.moveTo(helper.absoluteVec(new Vec3(6.5, 2, 6.5)));
		Cobblemon.INSTANCE.getStorage().getParty(player).add(PokemonProperties.Companion.parse("snorlax level=50", " ", "=").create());
		PartnerShowsCompatTests.after(helper, () -> {
			PokemonBattle left = BattleRegistry.getBattleByParticipatingPlayer(player);
			if (left != null) {
				CobblemonTrainers.finishForTest(left, false);
			}
		});

		helper.assertTrue(Trainers.refusal(player, trainer) == null, "the trainer refuses the first challenge: " + Trainers.refusal(player, trainer));
		Trainers.challenge(player, trainer);
		PokemonBattle first = BattleRegistry.getBattleByParticipatingPlayer(player);
		helper.assertTrue(first != null, "the first battle didn't start");
		UUID firstId = first.getBattleId();
		// What Cobblemon does when the battle's tick throws: no victory, no flight.
		first.stop();

		helper.runAfterDelay(40, () -> {
			helper.assertTrue(BattleRegistry.getBattle(firstId) == null, "Cobblemon still has the stopped battle");
			helper.assertValueEqual(ModAttachments.TRAINER_BATTLES.getOrElse(trainer, 0), 0, "battles on the trainer's record after the called-off one");
			Component refusal = Trainers.refusal(player, trainer);
			helper.assertTrue(refusal == null, "the trainer refuses a new challenge after its battle crashed: "
				+ (refusal == null ? "" : refusal.getString()));
			Trainers.challenge(player, trainer);
			PokemonBattle second = BattleRegistry.getBattleByParticipatingPlayer(player);
			helper.assertTrue(second != null && !second.getBattleId().equals(firstId), "the second challenge didn't start a battle");
			helper.assertTrue(CobblemonTrainers.isBattling(trainer), "the trainer isn't battling during the second battle");
			CobblemonTrainers.finishForTest(second, true);
			helper.assertTrue(!CobblemonTrainers.isBattling(trainer), "the trainer is still busy after the second battle ended");
			helper.assertValueEqual(ModAttachments.TRAINER_BATTLES.getOrElse(trainer, 0), 1, "battles on the trainer's record after the second one");
			helper.succeed();
		});
	}
}
