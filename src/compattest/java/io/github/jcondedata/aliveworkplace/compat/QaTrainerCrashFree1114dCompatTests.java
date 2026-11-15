package io.github.jcondedata.aliveworkplace.compat;

import com.cobblemon.mod.common.Cobblemon;
import com.cobblemon.mod.common.api.battles.model.PokemonBattle;
import com.cobblemon.mod.common.api.pokemon.PokemonProperties;
import com.cobblemon.mod.common.battles.BattleRegistry;
import io.github.jcondedata.aliveworkplace.compat.cobblemon.CobblemonTrainers;
import io.github.jcondedata.aliveworkplace.registry.ModAttachments;
import io.github.jcondedata.aliveworkplace.registry.ModBlocks;
import io.github.jcondedata.aliveworkplace.registry.ModVillagers;
import io.github.jcondedata.aliveworkplace.trainer.TrainerMenu;
import io.github.jcondedata.aliveworkplace.trainer.TrainerScreens;
import io.github.jcondedata.aliveworkplace.trainer.TrainerView;
import io.github.jcondedata.aliveworkplace.trainer.Trainers;
import io.github.jcondedata.aliveworkplace.work.Jobs;
import java.util.UUID;
import net.fabricmc.fabric.api.event.player.UseEntityCallback;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.level.GameType;

/**
 * QA (lane D, 2026-11-14), B1256 verified from the spec: "a trainer whose battle is gone (BattleRegistry no longer has
 * it) is free again, and the battle is settled or called off". A battle is stopped the way Cobblemon stops one whose tick
 * threw ({@link PokemonBattle#stop()}: no BATTLE_VICTORY, no BATTLE_FLED). Checked through what the player uses and reads:
 * the click that opens the Trainer screen, its Challenge button and refusal line, the record line ("0 won · 0 lost"),
 * and the old direct challenge. The other side of the boundary: a battle Cobblemon still runs keeps the trainer busy, with
 * the real English "… is already in a battle.". Every trainer has a fixed UUID (its team is seeded from it, B1210) and
 * every player a fixed party.
 */
public class QaTrainerCrashFree1114dCompatTests implements FabricGameTest {
	private static final String AREA = CompatGameTests.AREA;
	private static final String BUSY = " is already in a battle.";

	private static Villager trainer(GameTestHelper helper, UUID id) {
		PartnerShowsCompatTests.clearLeftovers(helper);
		helper.setDayTime(2000);
		BlockPos post = new BlockPos(2, 2, 2);
		helper.setBlock(post, ModBlocks.TRAINING_POST);
		net.minecraft.world.entity.Entity old = helper.getLevel().getEntity(id); // an earlier copy of this test's trainer (repeats)
		if (old != null) {
			old.discard();
		}
		// The UUID is set before the villager joins the world (B1210's way): set after, the level would still know it by
		// its old one, and the battle's end couldn't find the trainer.
		Villager trainer = new Villager(EntityType.VILLAGER, helper.getLevel());
		trainer.setUUID(id);
		BlockPos at = helper.absolutePos(new BlockPos(3, 2, 3));
		trainer.moveTo(at.getX() + 0.5, at.getY(), at.getZ() + 0.5, 0f, 0f);
		trainer.setNoAi(true);
		helper.assertTrue(helper.getLevel().addFreshEntity(trainer), "setup: the trainer " + id + " didn't join the world");
		Jobs.employ(helper.getLevel(), trainer, helper.absolutePos(post), ModVillagers.TRAINING_POST_POI, ModVillagers.TRAINER);
		return trainer;
	}

	private static ServerPlayer player(GameTestHelper helper, Villager trainer, double dx, String species) {
		ServerPlayer player = helper.makeMockServerPlayerInLevel();
		player.setGameMode(GameType.SURVIVAL);
		player.teleportTo(trainer.getX() + dx, trainer.getY(), trainer.getZ());
		Cobblemon.INSTANCE.getStorage().getParty(player).add(PokemonProperties.Companion.parse(species, " ", "=").create());
		return player;
	}

	/** Every battle these players are still in is ended in the teardown, pass or fail, so no trainer stays busy. */
	private static void endBattlesAfter(GameTestHelper helper, ServerPlayer... players) {
		PartnerShowsCompatTests.after(helper, () -> {
			for (ServerPlayer p : players) {
				PokemonBattle left = BattleRegistry.getBattleByParticipatingPlayer(p);
				if (left != null) {
					CobblemonTrainers.finishForTest(left, false);
				}
			}
		});
	}

	private static PokemonBattle battle(ServerPlayer player) {
		return BattleRegistry.getBattleByParticipatingPlayer(player);
	}

	/** The player's own click with an empty hand: it opens the Trainer screen. */
	private static TrainerMenu open(GameTestHelper helper, ServerPlayer player, Villager trainer) {
		// The mod hands a new player its guide book a moment after joining; the player puts it away to click empty-handed.
		player.setItemInHand(InteractionHand.MAIN_HAND, net.minecraft.world.item.ItemStack.EMPTY);
		InteractionResult result = UseEntityCallback.EVENT.invoker().interact(player, player.level(), InteractionHand.MAIN_HAND, trainer, null);
		helper.assertTrue(result.consumesAction(), "the click did nothing: " + result + " (trainer " + Trainers.isTrainer(trainer) + ", "
			+ trainer.getVillagerData().getProfession() + ", main hand " + player.getMainHandItem() + ", off hand " + player.getOffhandItem()
			+ ", spectator " + player.isSpectator() + ", sneaking " + player.isShiftKeyDown() + ")");
		helper.assertTrue(player.containerMenu instanceof TrainerMenu, "the click didn't open the Trainer screen: " + player.containerMenu);
		return (TrainerMenu) player.containerMenu;
	}

	/** Opens the screen and presses Challenge; the battle that started (fails if none did). */
	private static PokemonBattle challengeByScreen(GameTestHelper helper, ServerPlayer player, Villager trainer, String what) {
		TrainerMenu menu = open(helper, player, trainer);
		TrainerView view = menu.view();
		helper.assertTrue(view.canChallenge(), what + ": Challenge is grayed out: " + (view.refusal() == null ? "" : view.refusal().getString()));
		menu.clickMenuButton(player, TrainerMenu.CHALLENGE);
		PokemonBattle started = battle(player);
		helper.assertTrue(started != null, what + ": pressing Challenge started no battle");
		return started;
	}

	private static void assertRecord(GameTestHelper helper, ServerPlayer player, Villager trainer, String expected, String when) {
		helper.assertValueEqual(TrainerScreens.view(player, trainer).record().getString(), expected, "the record line " + when);
	}

	/**
	 * The same player, after the battle stopped on an error: the screen shows Challenge open and the record untouched,
	 * Challenge starts a real new battle, and that one (lost) is the only one counted.
	 */
	//$ gametest_ticks_batch AREA '400' '"qa_trainer_crash_free_1114d_same"'
	@GameTest(template = AREA, timeoutTicks = 400, batch = "qa_trainer_crash_free_1114d_same")
	public void theSamePlayerChallengesAgainAfterTheBattleStoppedOnAnError(GameTestHelper helper) {
		Villager trainer = trainer(helper, UUID.fromString("0b1256aa-0000-4000-8000-000000000001"));
		ServerPlayer player = player(helper, trainer, 1.5, "snorlax level=50");
		endBattlesAfter(helper, player);
		PokemonBattle first = challengeByScreen(helper, player, trainer, "the first challenge");
		UUID firstId = first.getBattleId();
		first.stop(); // what Cobblemon does when the battle's tick throws
		helper.runAfterDelay(40, () -> {
			helper.assertTrue(BattleRegistry.getBattle(firstId) == null, "Cobblemon still runs the stopped battle");
			helper.assertTrue(battle(player) == null, "the player is still in the stopped battle");
			assertRecord(helper, player, trainer, "0 won · 0 lost", "after the stopped battle");
			helper.assertValueEqual(ModAttachments.TRAINER_BATTLES.getOrElse(trainer, 0), 0, "battles on the trainer's record after the stopped one");
			helper.assertValueEqual(Trainers.record(player.getUUID(), trainer)[2], -1L, "the day of the last battle after only a stopped one");
			PokemonBattle second = challengeByScreen(helper, player, trainer, "the challenge after the stopped battle");
			helper.assertTrue(!second.getBattleId().equals(firstId), "the second challenge gave back the stopped battle");
			helper.assertTrue(BattleRegistry.getBattle(second.getBattleId()) != null, "the second battle isn't running");
			CobblemonTrainers.finishForTest(second, false);
			assertRecord(helper, player, trainer, "0 won · 1 lost", "after the second battle, lost");
			helper.assertValueEqual(ModAttachments.TRAINER_BATTLES.getOrElse(trainer, 0), 1, "battles on the trainer's record after the second one");
			helper.succeed();
		});
	}

	/**
	 * Another player, after the first player's battle stopped on an error, using the old direct challenge (no screen asked
	 * first): their battle starts; the first player is then told the trainer is busy, in the real English, and their
	 * grayed Challenge starts nothing.
	 */
	//$ gametest_ticks_batch AREA '400' '"qa_trainer_crash_free_1114d_other"'
	@GameTest(template = AREA, timeoutTicks = 400, batch = "qa_trainer_crash_free_1114d_other")
	public void anotherPlayerTakesTheTrainerWhoseBattleStopped(GameTestHelper helper) {
		Villager trainer = trainer(helper, UUID.fromString("0b1256aa-0000-4000-8000-000000000002"));
		ServerPlayer first = player(helper, trainer, 1.5, "snorlax level=50");
		ServerPlayer second = player(helper, trainer, -1.5, "snorlax level=50");
		endBattlesAfter(helper, first, second);
		PokemonBattle stopped = challengeByScreen(helper, first, trainer, "the first player's challenge");
		stopped.stop();
		helper.runAfterDelay(40, () -> {
			Trainers.challenge(second, trainer);
			PokemonBattle theirs = battle(second);
			helper.assertTrue(theirs != null, "the other player's challenge started no battle after the first one stopped");
			helper.assertTrue(BattleRegistry.getBattle(theirs.getBattleId()) != null, "the other player's battle isn't running");
			TrainerMenu menu = open(helper, first, trainer);
			TrainerView view = menu.view();
			helper.assertFalse(view.canChallenge(), "Challenge is open for the first player while the trainer battles the other");
			helper.assertValueEqual(view.refusal().getString(), trainer.getDisplayName().getString() + BUSY, "the reason under Challenge");
			menu.clickMenuButton(first, TrainerMenu.CHALLENGE);
			helper.assertTrue(battle(first) == null, "the grayed Challenge started a battle against a busy trainer");
			helper.assertTrue(battle(second) == theirs, "the other player's battle changed");
			assertRecord(helper, first, trainer, "0 won · 0 lost", "of the first player (only a stopped battle)");
			CobblemonTrainers.finishForTest(theirs, true);
			assertRecord(helper, second, trainer, "1 won · 0 lost", "of the other player after winning");
			assertRecord(helper, first, trainer, "0 won · 0 lost", "of the first player after the other's win");
			helper.succeed();
		});
	}

	/**
	 * The boundary's other side: a battle Cobblemon still runs (40 ticks in) keeps the trainer busy for another player, on
	 * the screen and on the direct challenge, and asking doesn't drop the live battle.
	 */
	//$ gametest_ticks_batch AREA '400' '"qa_trainer_crash_free_1114d_live"'
	@GameTest(template = AREA, timeoutTicks = 400, batch = "qa_trainer_crash_free_1114d_live")
	public void aTrainerInARunningBattleIsStillBusy(GameTestHelper helper) {
		Villager trainer = trainer(helper, UUID.fromString("0b1256aa-0000-4000-8000-000000000003"));
		ServerPlayer first = player(helper, trainer, 1.5, "snorlax level=50");
		ServerPlayer second = player(helper, trainer, -1.5, "snorlax level=50");
		endBattlesAfter(helper, first, second);
		PokemonBattle live = challengeByScreen(helper, first, trainer, "the first player's challenge");
		helper.runAfterDelay(40, () -> {
			helper.assertTrue(BattleRegistry.getBattle(live.getBattleId()) != null && !live.getEnded(), "the live battle ended by itself");
			TrainerView view = TrainerScreens.view(second, trainer);
			helper.assertFalse(view.canChallenge(), "Challenge is open while the trainer is in a running battle");
			helper.assertValueEqual(view.refusal().getString(), trainer.getDisplayName().getString() + BUSY, "the reason under Challenge");
			helper.assertValueEqual(Trainers.refusal(second, trainer).getString(), trainer.getDisplayName().getString() + BUSY, "the refusal");
			Trainers.challenge(second, trainer);
			helper.assertTrue(battle(second) == null, "a second player started a battle against a trainer in a running battle");
			helper.assertTrue(CobblemonTrainers.isBattling(trainer), "asking dropped the trainer's running battle");
			// The running battle still counts when it ends.
			CobblemonTrainers.finishForTest(live, false);
			assertRecord(helper, first, trainer, "0 won · 1 lost", "after the running battle ended");
			helper.assertValueEqual(ModAttachments.TRAINER_BATTLES.getOrElse(trainer, 0), 1, "battles on the trainer's record");
			helper.assertTrue(Trainers.refusal(second, trainer) == null, "the trainer is still busy after its battle ended");
			helper.succeed();
		});
	}

	/** Two battles in a row stop on an error: each time the trainer is free again, neither counts, the third one does. */
	//$ gametest_ticks_batch AREA '400' '"qa_trainer_crash_free_1114d_twice"'
	@GameTest(template = AREA, timeoutTicks = 400, batch = "qa_trainer_crash_free_1114d_twice")
	public void twoStoppedBattlesInARowCountForNothing(GameTestHelper helper) {
		Villager trainer = trainer(helper, UUID.fromString("0b1256aa-0000-4000-8000-000000000004"));
		ServerPlayer player = player(helper, trainer, 1.5, "snorlax level=50");
		endBattlesAfter(helper, player);
		PokemonBattle one = challengeByScreen(helper, player, trainer, "the first challenge");
		one.stop();
		helper.runAfterDelay(30, () -> {
			// The old click: a direct challenge, no screen asked first.
			Trainers.challenge(player, trainer);
			PokemonBattle two = battle(player);
			helper.assertTrue(two != null && !two.getBattleId().equals(one.getBattleId()), "the challenge after the first stopped battle started nothing");
			two.stop();
		});
		helper.runAfterDelay(60, () -> {
			assertRecord(helper, player, trainer, "0 won · 0 lost", "after two stopped battles");
			helper.assertValueEqual(ModAttachments.TRAINER_BATTLES.getOrElse(trainer, 0), 0, "battles on the trainer's record after two stopped ones");
			PokemonBattle three = challengeByScreen(helper, player, trainer, "the challenge after two stopped battles");
			CobblemonTrainers.finishForTest(three, true);
			assertRecord(helper, player, trainer, "1 won · 0 lost", "after the third battle, won");
			helper.assertValueEqual(ModAttachments.TRAINER_BATTLES.getOrElse(trainer, 0), 1, "battles on the trainer's record after the third one");
			helper.succeed();
		});
	}

	/**
	 * The trainer is unloaded (saved and removed) while its battle stops on an error, then loaded again: the loaded trainer
	 * takes a challenge, and the stopped battle isn't on its saved record.
	 */
	//$ gametest_ticks_batch AREA '400' '"qa_trainer_crash_free_1114d_reload"'
	@GameTest(template = AREA, timeoutTicks = 400, batch = "qa_trainer_crash_free_1114d_reload")
	public void aTrainerUnloadedWhileItsBattleStoppedIsFreeWhenLoaded(GameTestHelper helper) {
		UUID id = UUID.fromString("0b1256aa-0000-4000-8000-000000000005");
		Villager trainer = trainer(helper, id);
		ServerPlayer player = player(helper, trainer, 1.5, "snorlax level=50");
		endBattlesAfter(helper, player);
		PokemonBattle stopped = challengeByScreen(helper, player, trainer, "the first challenge");
		CompoundTag saved = trainer.saveWithoutId(new CompoundTag());
		trainer.discard(); // unloaded with its battle running
		stopped.stop();
		helper.runAfterDelay(40, () -> {
			Villager loaded = EntityType.VILLAGER.create(helper.getLevel());
			loaded.load(saved);
			helper.assertValueEqual(loaded.getUUID(), id, "the loaded trainer's UUID");
			helper.getLevel().addFreshEntity(loaded);
			helper.assertTrue(Trainers.isTrainer(loaded), "the loaded villager isn't a trainer");
			assertRecord(helper, player, loaded, "0 won · 0 lost", "of the loaded trainer");
			helper.assertValueEqual(ModAttachments.TRAINER_BATTLES.getOrElse(loaded, 0), 0, "battles on the loaded trainer's record");
			PokemonBattle next = challengeByScreen(helper, player, loaded, "the challenge to the loaded trainer");
			helper.assertTrue(!next.getBattleId().equals(stopped.getBattleId()), "the loaded trainer's battle is the stopped one");
			CobblemonTrainers.finishForTest(next, false);
			assertRecord(helper, player, loaded, "0 won · 1 lost", "of the loaded trainer after a lost battle");
			helper.succeed();
		});
	}
}
