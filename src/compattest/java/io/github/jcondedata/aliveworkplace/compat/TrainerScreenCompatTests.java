package io.github.jcondedata.aliveworkplace.compat;

import com.cobblemon.mod.common.Cobblemon;
import com.cobblemon.mod.common.api.pokemon.PokemonProperties;
import com.cobblemon.mod.common.battles.BattleRegistry;
import com.cobblemon.mod.common.pokemon.Pokemon;
import io.github.jcondedata.aliveworkplace.compat.cobblemon.CobblemonTrainers;
import io.github.jcondedata.aliveworkplace.registry.ModAttachments;
import io.github.jcondedata.aliveworkplace.registry.ModBlocks;
import io.github.jcondedata.aliveworkplace.registry.ModVillagers;
import io.github.jcondedata.aliveworkplace.trainer.TrainerMenu;
import io.github.jcondedata.aliveworkplace.trainer.TrainerScreens;
import io.github.jcondedata.aliveworkplace.trainer.TrainerView;
import io.github.jcondedata.aliveworkplace.trainer.Trainers;
import io.github.jcondedata.aliveworkplace.work.Jobs;
import net.fabricmc.fabric.api.event.player.UseEntityCallback;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.level.GameType;

/**
 * The Trainer screen (ROADMAP 43.1) with Cobblemon: an empty-hand click opens it instead of a battle; Challenge starts the
 * battle a click used to; each refusal grays Challenge out with its reason (and the button then starts nothing); the
 * team is half hidden until the player wins once; Trade opens the trades; the record and the reveal survive a reload.
 */
public class TrainerScreenCompatTests implements FabricGameTest {
	private static final String AREA = CompatGameTests.AREA;

	private record Setup(Villager trainer, ServerPlayer player, Pokemon pokemon) {
	}

	private static Setup setup(GameTestHelper helper, boolean leader, BlockPos at) {
		helper.setDayTime(2000);
		BlockPos post = at;
		helper.setBlock(post, leader ? ModBlocks.LEADERS_PODIUM : ModBlocks.TRAINING_POST);
		Villager trainer = helper.spawn(EntityType.VILLAGER, at.east());
		Jobs.employ(helper.getLevel(), trainer, helper.absolutePos(post), leader ? ModVillagers.LEADERS_PODIUM_POI : ModVillagers.TRAINING_POST_POI,
			leader ? ModVillagers.TRAINER_LEADER : ModVillagers.TRAINER);
		ServerPlayer player = helper.makeMockServerPlayerInLevel();
		player.setGameMode(GameType.SURVIVAL);
		player.teleportTo(trainer.getX() + 1, trainer.getY(), trainer.getZ());
		Pokemon pokemon = PokemonProperties.Companion.parse("charmander level=30", " ", "=").create();
		Cobblemon.INSTANCE.getStorage().getParty(player).add(pokemon);
		return new Setup(trainer, player, pokemon);
	}

	private static Setup setup(GameTestHelper helper, boolean leader) {
		return setup(helper, leader, new BlockPos(2, 2, 2));
	}

	/** The player's own click: an empty hand on the trainer. */
	private static InteractionResult click(ServerPlayer player, Villager trainer) {
		return UseEntityCallback.EVENT.invoker().interact(player, player.level(), InteractionHand.MAIN_HAND, trainer, null);
	}

	private static TrainerMenu openByClick(GameTestHelper helper, Setup s) {
		InteractionResult result = click(s.player(), s.trainer());
		helper.assertTrue(result.consumesAction(), "the click did nothing: " + result);
		helper.assertTrue(s.player().containerMenu instanceof TrainerMenu, "the click didn't open the Trainer screen: " + s.player().containerMenu);
		return (TrainerMenu) s.player().containerMenu;
	}

	private static String key(Component c) {
		return c.getContents() instanceof TranslatableContents t ? t.getKey() : c.getString();
	}

	private static boolean battling(ServerPlayer player) {
		return BattleRegistry.getBattleByParticipatingPlayer(player) != null;
	}

	private static void finish(ServerPlayer player, boolean won) {
		var battle = BattleRegistry.getBattleByParticipatingPlayer(player);
		if (battle != null) {
			CobblemonTrainers.finishForTest(battle, won);
		}
	}

	/** A refusal: the screen opens, Challenge is grayed with {@code key}, and pressing it anyway starts no battle. */
	private static void assertRefused(GameTestHelper helper, Setup s, String expected) {
		TrainerMenu menu = openByClick(helper, s);
		TrainerView view = menu.view();
		helper.assertFalse(view.canChallenge(), "Challenge isn't grayed out (expected " + expected + ")");
		helper.assertValueEqual(key(view.refusal()), expected, "the reason under Challenge");
		menu.clickMenuButton(s.player(), TrainerMenu.CHALLENGE);
		helper.assertFalse(battling(s.player()), "a refused Challenge started a battle (" + expected + ")");
	}

	/** An empty hand opens the screen, not a battle; it shows who the trainer is, half the team, the party and the rules. */
	//$ gametest AREA
	@GameTest(template = AREA)
	public void clickOpensTheScreenInsteadOfABattle(GameTestHelper helper) {
		Setup s = setup(helper, false);
		TrainerMenu menu = openByClick(helper, s);
		helper.assertFalse(battling(s.player()), "the click started a battle at once");
		TrainerView view = menu.view();
		helper.assertTrue(view.canChallenge(), "Challenge is grayed out: " + view.refusal().getString());
		helper.assertValueEqual(view.entityId(), s.trainer().getId(), "the portrait's villager");
		helper.assertValueEqual(key(view.title()), "screen.aliveworkplace.trainer.trainer", "the title");
		helper.assertValueEqual(key(view.record()), "screen.aliveworkplace.trainer.record_line", "the record line");
		helper.assertValueEqual(view.record().getString(), "0 won · 0 lost", "the record");
		helper.assertValueEqual(key(view.today()), "screen.aliveworkplace.trainer.today.none", "today's line");
		helper.assertValueEqual(key(view.rules()), "screen.aliveworkplace.trainer.rules.no_cap", "the rules without RCT");
		helper.assertValueEqual(view.reward().getString(), "Win to earn 100 CobbleDollars.", "a Novice's reward");
		int size = CobblemonTrainers.teamOf(s.trainer()).size();
		helper.assertValueEqual(view.team().size(), size, "the team's size");
		long hidden = view.team().stream().filter(TrainerView.TeamSlot::hidden).count();
		helper.assertValueEqual((int) hidden, size - (size + 1) / 2, "hidden before a win");
		helper.assertTrue(view.team().stream().filter(t -> !t.hidden()).allMatch(t -> t.level() > 0 && !t.icon().isEmpty()), "a shown Pokémon has no model or level");
		helper.assertValueEqual(key(view.teamHint()), "screen.aliveworkplace.trainer.team.hidden", "the hint under the team");
		helper.assertValueEqual(view.party().size(), 1, "the party's lines");
		helper.assertValueEqual(view.party().get(0).level(), 30, "the party line's level");
		helper.assertValueEqual(key(view.party().get(0).state()), "screen.aliveworkplace.trainer.party.ready", "the party line's state");
		// sneaking still trades: no screen
		s.player().closeContainer();
		s.player().setShiftKeyDown(true);
		helper.assertTrue(click(s.player(), s.trainer()) == InteractionResult.PASS, "a sneak-click no longer goes to the trades");
		helper.succeed();
	}

	/** Challenge does what the click did: the battle starts and the screen closes; a win pays the prize. */
	//$ gametest_ticks AREA '400'
	@GameTest(template = AREA, timeoutTicks = 400)
	public void challengeStartsTheBattle(GameTestHelper helper) {
		Setup s = setup(helper, false);
		TrainerMenu menu = openByClick(helper, s);
		helper.assertTrue(menu.clickMenuButton(s.player(), TrainerMenu.CHALLENGE), "Challenge wasn't taken");
		helper.assertTrue(battling(s.player()), "Challenge started no battle");
		helper.assertFalse(s.player().containerMenu instanceof TrainerMenu, "the screen stayed open over the battle");
		long before = io.github.jcondedata.aliveworkplace.compat.cobbledollars.CobbleDollarsBank.balance(s.player());
		finish(s.player(), true);
		helper.assertValueEqual(io.github.jcondedata.aliveworkplace.compat.cobbledollars.CobbleDollarsBank.balance(s.player()) - before, 100L, "the prize");
		helper.succeed();
	}

	/** After a win the whole team shows, the record and today's line say so, and the reward says the prize is had. */
	//$ gametest_ticks AREA '400'
	@GameTest(template = AREA, timeoutTicks = 400)
	public void theTeamIsRevealedAfterAWin(GameTestHelper helper) {
		Setup s = setup(helper, false);
		openByClick(helper, s).clickMenuButton(s.player(), TrainerMenu.CHALLENGE);
		finish(s.player(), false);
		TrainerView lost = openByClick(helper, s).view();
		helper.assertValueEqual(lost.record().getString(), "0 won · 1 lost", "the record after a loss");
		helper.assertValueEqual(key(lost.today()), "screen.aliveworkplace.trainer.today.battled", "today after a loss");
		helper.assertTrue(lost.team().stream().anyMatch(TrainerView.TeamSlot::hidden), "a loss showed the whole team");
		s.player().containerMenu.clickMenuButton(s.player(), TrainerMenu.CHALLENGE);
		helper.assertTrue(battling(s.player()), "no second battle");
		finish(s.player(), true);
		TrainerView won = openByClick(helper, s).view();
		helper.assertValueEqual(won.record().getString(), "1 won · 1 lost", "the record after a win");
		helper.assertTrue(won.team().stream().noneMatch(TrainerView.TeamSlot::hidden), "the team is still hidden after a win");
		helper.assertTrue(won.teamHint().getString().isEmpty(), "the hint stayed after a win");
		helper.assertValueEqual(key(won.today()), "screen.aliveworkplace.trainer.today.won", "today after a win");
		helper.assertValueEqual(key(won.reward()), "screen.aliveworkplace.trainer.reward.paid", "the reward after today's prize");
		helper.succeed();
	}

	/** Trade opens the trainer's trades, as a sneak-click does. */
	//$ gametest AREA
	@GameTest(template = AREA)
	public void tradeOpensTheTrades(GameTestHelper helper) {
		Setup s = setup(helper, false);
		// a Novice Trainer may have nothing to sell yet: Trade is grayed out then, as a sneak-click only gets a shake of the head
		TrainerView none = openByClick(helper, s).view();
		helper.assertValueEqual(none.canTrade(), !s.trainer().getOffers().isEmpty(), "Trade's state with " + s.trainer().getOffers().size() + " offers");
		s.player().closeContainer();
		if (s.trainer().getOffers().isEmpty()) {
			s.trainer().getOffers().add(new net.minecraft.world.item.trading.MerchantOffer(new net.minecraft.world.item.trading.ItemCost(net.minecraft.world.item.Items.EMERALD),
				new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.BREAD), 4, 1, 0.05f));
		}
		TrainerMenu menu = openByClick(helper, s);
		helper.assertTrue(menu.view().canTrade(), "Trade is grayed out with offers");
		helper.assertTrue(menu.clickMenuButton(s.player(), TrainerMenu.TRADE), "Trade wasn't taken");
		helper.assertTrue(s.trainer().getTradingPlayer() == s.player(), "Trade didn't open the trades");
		helper.assertTrue(s.player().containerMenu instanceof net.minecraft.world.inventory.MerchantMenu, "no trading screen: " + s.player().containerMenu);
		helper.assertFalse(battling(s.player()), "Trade started a battle");
		s.trainer().setTradingPlayer(null);
		s.player().closeContainer();
		// Close closes
		TrainerMenu again = openByClick(helper, s);
		again.clickMenuButton(s.player(), TrainerMenu.CLOSE);
		helper.assertFalse(s.player().containerMenu instanceof TrainerMenu, "Close left the screen open");
		helper.succeed();
	}

	/** Asleep: Challenge grayed with "asleep", Trade too. */
	//$ gametest AREA
	@GameTest(template = AREA)
	public void refusedWhileAsleep(GameTestHelper helper) {
		Setup s = setup(helper, false);
		s.trainer().startSleeping(s.trainer().blockPosition());
		assertRefused(helper, s, "message.aliveworkplace.trainer.asleep");
		helper.assertFalse(openByClick(helper, s).view().canTrade(), "Trade with a sleeping trainer");
		s.trainer().stopSleeping();
		helper.succeed();
	}

	/** No Pokémon in the party: grayed with "no team". A fainted one is no refusal (the battle heals the party), its line says so. */
	//$ gametest_ticks AREA '400'
	@GameTest(template = AREA, timeoutTicks = 400)
	public void refusedWithNoTeam(GameTestHelper helper) {
		Setup s = setup(helper, false);
		s.pokemon().setCurrentHealth(0);
		TrainerView fainted = openByClick(helper, s).view();
		helper.assertTrue(fainted.canChallenge(), "a fainted Pokémon refused the challenge: " + fainted.refusal().getString());
		helper.assertValueEqual(key(fainted.party().get(0).state()), "screen.aliveworkplace.trainer.party.fainted", "a fainted Pokémon's line");
		helper.assertValueEqual(fainted.party().get(0).color(), TrainerView.RED, "a fainted Pokémon's line color");
		helper.assertTrue(s.pokemon().isFainted(), "opening the screen healed the party");
		s.player().closeContainer();
		Cobblemon.INSTANCE.getStorage().getParty(s.player()).remove(s.pokemon());
		assertRefused(helper, s, "message.aliveworkplace.trainer.no_team");
		TrainerView empty = openByClick(helper, s).view();
		helper.assertTrue(empty.party().isEmpty(), "party lines with no party");
		helper.assertValueEqual(key(empty.partyNote()), "screen.aliveworkplace.trainer.party.none", "the note for no party");
		helper.succeed();
	}

	/** The trainer busy with another player: grayed with "busy"; the player already battling: grayed with "in a battle". */
	//$ gametest_ticks AREA '400'
	@GameTest(template = AREA, timeoutTicks = 400)
	public void refusedWhileBusyOrInABattle(GameTestHelper helper) {
		Setup s = setup(helper, false);
		ServerPlayer other = helper.makeMockServerPlayerInLevel();
		other.setGameMode(GameType.SURVIVAL);
		other.teleportTo(s.trainer().getX() - 1, s.trainer().getY(), s.trainer().getZ());
		Cobblemon.INSTANCE.getStorage().getParty(other).add(PokemonProperties.Companion.parse("squirtle level=30", " ", "=").create());
		Trainers.challenge(other, s.trainer());
		helper.assertTrue(battling(other), "the other player's battle didn't start");
		assertRefused(helper, s, "message.aliveworkplace.trainer.busy");
		TrainerView mine = TrainerScreens.view(other, s.trainer());
		helper.assertValueEqual(key(mine.refusal()), "message.aliveworkplace.trainer.in_battle", "the reason for a player already in a battle");
		finish(other, false);
		helper.succeed();
	}

	/** A second Trainer Leader nearby with less experience: grayed with "not the leader". */
	//$ gametest AREA
	@GameTest(template = AREA)
	public void refusedWhenNotTheLeader(GameTestHelper helper) {
		Setup s = setup(helper, true);
		Villager senior = helper.spawn(EntityType.VILLAGER, new BlockPos(6, 2, 6));
		BlockPos podium = new BlockPos(6, 2, 4);
		helper.setBlock(podium, ModBlocks.LEADERS_PODIUM);
		Jobs.employ(helper.getLevel(), senior, helper.absolutePos(podium), ModVillagers.LEADERS_PODIUM_POI, ModVillagers.TRAINER_LEADER);
		senior.setVillagerXp(s.trainer().getVillagerXp() + 50);
		assertRefused(helper, s, "message.aliveworkplace.trainer.not_the_leader");
		helper.succeed();
	}

	/** A Leader takes one challenge a day: the second time the screen says come back tomorrow, and today's line says so. */
	//$ gametest_ticks AREA '400'
	@GameTest(template = AREA, timeoutTicks = 400)
	public void refusedUntilTomorrowByALeader(GameTestHelper helper) {
		Setup s = setup(helper, true);
		TrainerView first = openByClick(helper, s).view();
		helper.assertValueEqual(key(first.title()), "message.aliveworkplace.trainer.leader_title", "a Leader's title");
		helper.assertValueEqual(first.reward().getString(), "Win to earn 3,000 CobbleDollars.", "a Leader's reward");
		s.player().containerMenu.clickMenuButton(s.player(), TrainerMenu.CHALLENGE);
		helper.assertTrue(battling(s.player()), "the Leader's battle didn't start");
		finish(s.player(), false);
		assertRefused(helper, s, "message.aliveworkplace.trainer.leader_tomorrow");
		helper.assertValueEqual(key(openByClick(helper, s).view().today()), "screen.aliveworkplace.trainer.today.challenged", "today's line");
		helper.succeed();
	}

	/** A Leader's delegate at the Cup takes no challenges: grayed with the Cup's reason. */
	//$ gametest AREA
	@GameTest(template = AREA)
	public void refusedAwayAtTheCup(GameTestHelper helper) {
		Setup s = setup(helper, true);
		ModAttachments.CUP_DELEGATE.set(s.trainer(), new io.github.jcondedata.aliveworkplace.cup.CupDays.Delegate(BlockPos.ZERO,
			java.util.UUID.randomUUID(), 4, BlockPos.ZERO, helper.getLevel().getDayTime() / 24000L));
		assertRefused(helper, s, "message.aliveworkplace.cup.delegate_busy");
		helper.succeed();
	}

	/** The record and the revealed team are saved with the trainer. */
	//$ gametest_ticks AREA '400'
	@GameTest(template = AREA, timeoutTicks = 400)
	public void recordAndRevealSurviveAReload(GameTestHelper helper) {
		Setup s = setup(helper, false);
		openByClick(helper, s).clickMenuButton(s.player(), TrainerMenu.CHALLENGE);
		finish(s.player(), true);
		CompoundTag saved = s.trainer().saveWithoutId(new CompoundTag());
		Villager copy = EntityType.VILLAGER.create(helper.getLevel());
		copy.load(saved);
		TrainerView view = TrainerScreens.view(s.player(), copy);
		helper.assertValueEqual(view.record().getString(), "1 won · 0 lost", "the record after a reload");
		helper.assertTrue(!view.team().isEmpty() && view.team().stream().noneMatch(TrainerView.TeamSlot::hidden), "the team is hidden again after a reload");
		helper.assertValueEqual(key(view.today()), "screen.aliveworkplace.trainer.today.won", "today after a reload");
		helper.assertValueEqual(view.team().size(), CobblemonTrainers.teamOf(s.trainer()).size(), "the team after a reload");
		copy.discard();
		helper.succeed();
	}
}
