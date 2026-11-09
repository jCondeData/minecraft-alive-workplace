package io.github.jcondedata.aliveworkplace.compat;

import com.cobblemon.mod.common.Cobblemon;
import com.cobblemon.mod.common.api.pokemon.PokemonProperties;
import com.cobblemon.mod.common.battles.BattleRegistry;
import com.cobblemon.mod.common.pokemon.Pokemon;
import io.github.jcondedata.aliveworkplace.card.StatusCard;
import io.github.jcondedata.aliveworkplace.card.StatusCardMenu;
import io.github.jcondedata.aliveworkplace.compat.cobblemon.CobblemonTrainers;
import io.github.jcondedata.aliveworkplace.fossil.FossilCard;
import io.github.jcondedata.aliveworkplace.fossil.FossilScientists;
import io.github.jcondedata.aliveworkplace.fossil.Revival;
import io.github.jcondedata.aliveworkplace.nurse.NurseCard;
import io.github.jcondedata.aliveworkplace.registry.ModAttachments;
import io.github.jcondedata.aliveworkplace.registry.ModBlocks;
import io.github.jcondedata.aliveworkplace.registry.ModVillagers;
import io.github.jcondedata.aliveworkplace.trainer.TrainerMenu;
import io.github.jcondedata.aliveworkplace.trainer.TrainerScreens;
import io.github.jcondedata.aliveworkplace.trainer.TrainerView;
import io.github.jcondedata.aliveworkplace.trainer.Trainers;
import io.github.jcondedata.aliveworkplace.work.Jobs;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.locale.Language;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.network.protocol.game.ServerboundContainerButtonClickPacket;
import net.minecraft.network.protocol.game.ServerboundInteractPacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.village.poi.PoiTypes;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;

/**
 * QA (2026-11-09, lane d), written from the specs of ROADMAP 43.1 (the Trainer screen) and 43.3 (the Nurse's and the
 * Fossil Scientist's cards), not from the code. Adversarial cases the feature tests leave out: a screen whose view went
 * stale before its button was pressed (the trainer or nurse fell asleep, the trainer started another battle, died, or
 * the player walked off), a button pressed twice, the party lines per Pokémon, the team reveal per player, the rules
 * line, Hand over with something else in hand or a full party, and every line a player reads being real English. Every
 * click and button press goes through the server's packet handler, as a client sends it.
 */
public class QaPokemonScreens1109dCompatTests implements FabricGameTest {
	static final String AREA = CompatGameTests.AREA;

	// ------------------------------------------------------------------ helpers

	record Fight(Villager trainer, ServerPlayer player, List<String> seen, List<ServerPlayer> players) {
	}

	/** A Trainer (or Leader) by day and a logged-in survival player next to it with a level 30 Charmander. */
	static Fight trainer(GameTestHelper helper, boolean leader, String name) {
		PartnerShowsCompatTests.clearLeftovers(helper);
		PartnerShowsCompatTests.clearHalls(helper);
		helper.setDayTime(2000);
		List<ServerPlayer> players = new ArrayList<>();
		// registered before the players, so it runs before they're logged out: no battle outlives the test
		PartnerShowsCompatTests.after(helper, () -> players.forEach(p -> finish(p, false)));
		BlockPos post = new BlockPos(2, 2, 2);
		helper.setBlock(post, leader ? ModBlocks.LEADERS_PODIUM : ModBlocks.TRAINING_POST);
		Villager trainer = helper.spawn(EntityType.VILLAGER, post.east());
		trainer.setNoAi(true);
		Jobs.employ(helper.getLevel(), trainer, helper.absolutePos(post), leader ? ModVillagers.LEADERS_PODIUM_POI : ModVillagers.TRAINING_POST_POI,
			leader ? ModVillagers.TRAINER_LEADER : ModVillagers.TRAINER);
		List<String> seen = new ArrayList<>();
		ServerPlayer player = joiner(helper, players, seen, name);
		party(player, "charmander level=30");
		return new Fight(trainer, player, seen, players);
	}

	static ServerPlayer joiner(GameTestHelper helper, List<ServerPlayer> players, List<String> seen, String name) {
		ServerPlayer player = QaFossilScientist1102dCompatTests.player(helper, seen, UUID.randomUUID(), name);
		players.add(player);
		return player;
	}

	static Pokemon party(ServerPlayer player, String properties) {
		Pokemon pokemon = PokemonProperties.Companion.parse(properties, " ", "=").create();
		Cobblemon.INSTANCE.getStorage().getParty(player).add(pokemon);
		return pokemon;
	}

	/** A client's empty-hand right-click on {@code villager}, through the packet handler ("interact at", then "interact"). */
	static void rightClick(ServerPlayer player, Villager villager) {
		player.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
		player.setShiftKeyDown(false);
		player.moveTo(villager.getX() + 1.5, villager.getY(), villager.getZ(), 90f, 0f);
		player.connection.handleInteract(ServerboundInteractPacket.createInteractionPacket(villager, false, InteractionHand.MAIN_HAND,
			new Vec3(0, villager.getBbHeight() / 2, 0)));
		if (player.containerMenu == player.inventoryMenu) {
			player.connection.handleInteract(ServerboundInteractPacket.createInteractionPacket(villager, false, InteractionHand.MAIN_HAND));
		}
	}

	static TrainerMenu openTrainer(GameTestHelper helper, ServerPlayer player, Villager trainer) {
		rightClick(player, trainer);
		helper.assertTrue(player.containerMenu instanceof TrainerMenu, "no Trainer screen opened: " + player.containerMenu.getClass().getSimpleName());
		return (TrainerMenu) player.containerMenu;
	}

	/** The client's button press on the open screen {@code containerId}. */
	static void press(ServerPlayer player, int containerId, int button) {
		player.connection.handleContainerButtonClick(new ServerboundContainerButtonClickPacket(containerId, button));
	}

	static boolean battling(ServerPlayer player) {
		return BattleRegistry.getBattleByParticipatingPlayer(player) != null;
	}

	static void finish(ServerPlayer player, boolean won) {
		var battle = BattleRegistry.getBattleByParticipatingPlayer(player);
		if (battle != null) {
			CobblemonTrainers.finishForTest(battle, won);
		}
	}

	static long count(List<String> seen, String part) {
		return seen.stream().filter(line -> line.contains(part)).count();
	}

	static String key(Component c) {
		return c.getContents() instanceof TranslatableContents t ? t.getKey() : c.getString();
	}

	/** {@code c} reads as English: no raw key, no unfilled placeholder, and every one of our keys in it has its text. */
	static void assertReadable(GameTestHelper helper, Component c, String what) {
		String s = c.getString();
		helper.assertFalse(s.contains("aliveworkplace.") || s.contains("cobblemon.") || s.contains("%s") || s.matches("(?s).*%\\d\\$s.*"),
			what + " shows a raw key or placeholder: \"" + s + "\"");
		assertKeys(helper, c, what);
	}

	static void assertKeys(GameTestHelper helper, Component c, String what) {
		if (c.getContents() instanceof TranslatableContents t) {
			if (t.getKey().contains("aliveworkplace")) {
				helper.assertTrue(Language.getInstance().has(t.getKey()), what + ": " + t.getKey() + " has no English text");
			}
			for (Object arg : t.getArgs()) {
				if (arg instanceof Component inner) {
					assertKeys(helper, inner, what);
				}
			}
		}
		for (Component sibling : c.getSiblings()) {
			assertKeys(helper, sibling, what);
		}
	}

	static void assertReadable(GameTestHelper helper, TrainerView v, String when) {
		for (Component c : List.of(v.name(), v.title(), v.rank(), v.record(), v.today(), v.rules(), v.reward(), v.teamHeading(), v.teamHint(),
				v.partyNote(), v.refusal())) {
			assertReadable(helper, c, "the Trainer screen (" + when + ")");
		}
		for (TrainerView.PartyLine line : v.party()) {
			assertReadable(helper, line.name(), "a party line's name (" + when + ")");
			assertReadable(helper, line.state(), "a party line's state (" + when + ")");
		}
	}

	static void assertReadable(GameTestHelper helper, StatusCard card, String which) {
		for (Component c : List.of(card.title(), card.tag(), card.subtitle(), card.heading(), card.empty(), card.hint())) {
			assertReadable(helper, c, which);
		}
		for (StatusCard.Line line : card.lines()) {
			assertReadable(helper, line.label(), which + "'s line label");
			assertReadable(helper, line.value(), which + "'s line value");
		}
		for (StatusCard.Button button : card.buttons()) {
			assertReadable(helper, button.label(), which + "'s button");
			assertReadable(helper, button.refusal(), which + "'s button reason");
		}
	}

	// ------------------------------------------------------------------ the Trainer screen (43.1)

	/** The screen opened while the trainer was awake; it falls asleep; Challenge then starts nothing and says why. */
	//$ gametest_ticks_batch AREA '200' '"qa43TrainerAsleep"'
	@GameTest(template = AREA, timeoutTicks = 200, batch = "qa43TrainerAsleep")
	public void staleChallengeAfterTheTrainerFellAsleep(GameTestHelper helper) {
		Fight f = trainer(helper, false, "qa43asleep");
		TrainerMenu menu = openTrainer(helper, f.player(), f.trainer());
		helper.assertTrue(menu.view().canChallenge(), "Challenge grayed out at the start: " + menu.view().refusal().getString());
		f.trainer().startSleeping(f.trainer().blockPosition());
		press(f.player(), menu.containerId, TrainerMenu.CHALLENGE);
		helper.assertFalse(battling(f.player()), "a sleeping trainer battled");
		helper.assertTrue(count(f.seen(), "is asleep") == 1, "the player isn't told the trainer is asleep: " + f.seen());
		f.trainer().stopSleeping();
		helper.succeed();
	}

	/** A sees the trainer free; B challenges it first; A's Challenge starts nothing, says it's busy, and leaves B's battle alone. */
	//$ gametest_ticks_batch AREA '200' '"qa43TrainerBusy"'
	@GameTest(template = AREA, timeoutTicks = 200, batch = "qa43TrainerBusy")
	public void staleChallengeAfterAnotherPlayerStartedABattle(GameTestHelper helper) {
		Fight f = trainer(helper, false, "qa43busyA");
		ServerPlayer b = joiner(helper, f.players(), new ArrayList<>(), "qa43busyB");
		party(b, "squirtle level=30");
		TrainerMenu menu = openTrainer(helper, f.player(), f.trainer());
		helper.assertTrue(menu.view().canChallenge(), "Challenge grayed out at the start: " + menu.view().refusal().getString());
		b.moveTo(f.trainer().getX() - 1.5, f.trainer().getY(), f.trainer().getZ());
		Trainers.challenge(b, f.trainer());
		helper.assertTrue(battling(b), "setup: B's battle didn't start");
		var theirs = BattleRegistry.getBattleByParticipatingPlayer(b);
		press(f.player(), menu.containerId, TrainerMenu.CHALLENGE);
		helper.assertFalse(battling(f.player()), "A battled a trainer already in a battle");
		helper.assertTrue(count(f.seen(), "already in a battle") == 1, "A isn't told the trainer is busy: " + f.seen());
		helper.assertTrue(BattleRegistry.getBattleByParticipatingPlayer(b) == theirs, "A's press disturbed B's battle");
		helper.succeed();
	}

	/** The player opens the screen, walks 30 blocks off (past its reach), and presses Challenge: no battle. */
	//$ gametest_ticks_batch AREA '200' '"qa43TrainerFar"'
	@GameTest(template = AREA, timeoutTicks = 200, batch = "qa43TrainerFar")
	public void challengeFromAPlayerWhoWalkedAway(GameTestHelper helper) {
		Fight f = trainer(helper, false, "qa43far");
		TrainerMenu menu = openTrainer(helper, f.player(), f.trainer());
		f.player().teleportTo(f.trainer().getX() + 30, f.trainer().getY(), f.trainer().getZ());
		press(f.player(), menu.containerId, TrainerMenu.CHALLENGE);
		helper.assertFalse(battling(f.player()), "a Challenge pressed 30 blocks away started a battle");
		helper.succeed();
	}

	/** The trainer dies while the screen is open: Challenge starts nothing (and nothing breaks). */
	//$ gametest_ticks_batch AREA '200' '"qa43TrainerDied"'
	@GameTest(template = AREA, timeoutTicks = 200, batch = "qa43TrainerDied")
	public void challengeAfterTheTrainerDied(GameTestHelper helper) {
		Fight f = trainer(helper, false, "qa43died");
		TrainerMenu menu = openTrainer(helper, f.player(), f.trainer());
		f.trainer().kill();
		press(f.player(), menu.containerId, TrainerMenu.CHALLENGE);
		helper.assertFalse(battling(f.player()), "a dead trainer battled");
		helper.succeed();
	}

	/** Challenge pressed twice (the packet sent twice, then the old screen's button again): one battle, one "accepts". */
	//$ gametest_ticks_batch AREA '200' '"qa43TrainerTwice"'
	@GameTest(template = AREA, timeoutTicks = 200, batch = "qa43TrainerTwice")
	public void challengePressedTwiceStartsOneBattle(GameTestHelper helper) {
		Fight f = trainer(helper, false, "qa43twice");
		TrainerMenu menu = openTrainer(helper, f.player(), f.trainer());
		press(f.player(), menu.containerId, TrainerMenu.CHALLENGE);
		helper.assertTrue(battling(f.player()), "Challenge started no battle");
		var first = BattleRegistry.getBattleByParticipatingPlayer(f.player());
		press(f.player(), menu.containerId, TrainerMenu.CHALLENGE);
		menu.clickMenuButton(f.player(), TrainerMenu.CHALLENGE);
		helper.assertTrue(BattleRegistry.getBattleByParticipatingPlayer(f.player()) == first, "a second press replaced the battle");
		helper.assertValueEqual(count(f.seen(), "accepts your challenge"), 1L, "challenges accepted (" + f.seen() + ")");
		helper.succeed();
	}

	/**
	 * A party of three, in order: a healthy level 30, a fainted one and a hurt one. One line each, in the party's order, with
	 * its level: Ready (green), Fainted (red), Ready (green). Without RCT there is no cap: no line says over the cap, no
	 * Pokémon of the trainer's is marked as scaled down, and the rules say there's no level cap. Opening heals nobody.
	 */
	//$ gametest_ticks_batch AREA '200' '"qa43PartyLines"'
	@GameTest(template = AREA, timeoutTicks = 200, batch = "qa43PartyLines")
	public void partyLinesShowEachPokemonsState(GameTestHelper helper) {
		Fight f = trainer(helper, false, "qa43party");
		Pokemon fainted = party(f.player(), "squirtle level=12");
		fainted.setCurrentHealth(0);
		Pokemon hurt = party(f.player(), "bulbasaur level=17");
		hurt.setCurrentHealth(1);
		TrainerView v = openTrainer(helper, f.player(), f.trainer()).view();
		helper.assertValueEqual(v.party().size(), 3, "party lines");
		helper.assertValueEqual(v.party().get(0).level(), 30, "line 1's level");
		helper.assertValueEqual(v.party().get(1).level(), 12, "line 2's level");
		helper.assertValueEqual(v.party().get(2).level(), 17, "line 3's level");
		helper.assertValueEqual(v.party().get(0).name().getString(), "Charmander", "line 1's name");
		helper.assertValueEqual(v.party().get(1).name().getString(), "Squirtle", "line 2's name");
		helper.assertValueEqual(key(v.party().get(0).state()), "screen.aliveworkplace.trainer.party.ready", "a healthy Pokémon's line");
		helper.assertValueEqual(key(v.party().get(1).state()), "screen.aliveworkplace.trainer.party.fainted", "a fainted Pokémon's line");
		helper.assertValueEqual(key(v.party().get(2).state()), "screen.aliveworkplace.trainer.party.ready", "a hurt (not fainted) Pokémon's line");
		helper.assertTrue(v.party().get(0).color() == TrainerView.GREEN && v.party().get(1).color() == TrainerView.RED
			&& v.party().get(2).color() == TrainerView.GREEN, "line colors");
		helper.assertTrue(v.party().stream().noneMatch(l -> key(l.state()).endsWith("over_cap")), "an over-the-cap line with no cap");
		helper.assertTrue(v.team().stream().noneMatch(TrainerView.TeamSlot::scaled), "a team Pokémon marked as scaled with no cap");
		helper.assertValueEqual(v.rules().getString(), "Singles · no level cap", "the rules line without a cap");
		helper.assertTrue(v.party().stream().allMatch(l -> !l.icon().isEmpty()), "a party line without its Pokémon's model");
		helper.assertTrue(fainted.isFainted() && hurt.getCurrentHealth() == 1, "opening the screen healed the party");
		assertReadable(helper, v, "a mixed party");
		helper.succeed();
	}

	/** A beats the trainer: A's screen shows the whole team; B (who never won) still has the unseen half hidden. */
	//$ gametest_ticks_batch AREA '400' '"qa43RevealPerPlayer"'
	@GameTest(template = AREA, timeoutTicks = 400, batch = "qa43RevealPerPlayer")
	public void teamRevealIsPerPlayer(GameTestHelper helper) {
		Fight f = trainer(helper, false, "qa43revealA");
		ServerPlayer b = joiner(helper, f.players(), new ArrayList<>(), "qa43revealB");
		party(b, "squirtle level=30");
		int size = CobblemonTrainers.teamOf(f.trainer()).size();
		helper.assertTrue(size >= 2, "setup: a team of " + size + " hides nothing");
		press(f.player(), openTrainer(helper, f.player(), f.trainer()).containerId, TrainerMenu.CHALLENGE);
		helper.assertTrue(battling(f.player()), "A's battle didn't start");
		finish(f.player(), true);
		TrainerView mine = openTrainer(helper, f.player(), f.trainer()).view();
		helper.assertTrue(mine.team().stream().noneMatch(TrainerView.TeamSlot::hidden), "A's team still hidden after A's win");
		f.player().closeContainer();
		TrainerView theirs = openTrainer(helper, b, f.trainer()).view();
		long hidden = theirs.team().stream().filter(TrainerView.TeamSlot::hidden).count();
		helper.assertValueEqual((int) hidden, size - (size + 1) / 2, "Pokémon hidden from B after A's win");
		helper.assertValueEqual(theirs.record().getString(), "0 won · 0 lost", "B's record after A's win");
		helper.assertValueEqual(key(theirs.today()), "screen.aliveworkplace.trainer.today.none", "B's today after A's win");
		helper.assertValueEqual(key(theirs.teamHint()), "screen.aliveworkplace.trainer.team.hidden", "B's hint");
		helper.assertTrue(theirs.canChallenge(), "B can't challenge after A's battle: " + theirs.refusal().getString());
		assertReadable(helper, mine, "after a win");
		assertReadable(helper, theirs, "another player after a win");
		helper.succeed();
	}

	/** A Trainer Leader's screen: the rules line names singles and the level cap (none without RCT); its text is all English. */
	//$ gametest_ticks_batch AREA '200' '"qa43LeaderRules"'
	@GameTest(template = AREA, timeoutTicks = 200, batch = "qa43LeaderRules")
	public void leaderRulesLineNamesSinglesAndTheCap(GameTestHelper helper) {
		Fight f = trainer(helper, true, "qa43leader");
		TrainerView v = openTrainer(helper, f.player(), f.trainer()).view();
		helper.assertTrue(v.canChallenge(), "a lone Leader refuses: " + v.refusal().getString());
		String rules = v.rules().getString();
		helper.assertTrue(rules.startsWith("Singles") && rules.contains("level cap"), "the Leader's rules line: " + rules);
		helper.assertValueEqual(v.title().getString(), "Trainer Leader", "the Leader's title");
		assertReadable(helper, v, "a Leader");
		helper.succeed();
	}

	/** Every refusal the screen can show reads as English (asleep, busy, in a battle, no team, tomorrow, not the leader). */
	//$ gametest_ticks_batch AREA '400' '"qa43TrainerText"'
	@GameTest(template = AREA, timeoutTicks = 400, batch = "qa43TrainerText")
	public void everyTrainerRefusalIsEnglish(GameTestHelper helper) {
		Fight f = trainer(helper, true, "qa43text");
		f.trainer().startSleeping(f.trainer().blockPosition());
		assertReadable(helper, TrainerScreens.view(f.player(), f.trainer()), "asleep");
		f.trainer().stopSleeping();
		Trainers.challenge(f.player(), f.trainer());
		helper.assertTrue(battling(f.player()), "setup: the Leader's battle didn't start");
		TrainerView inBattle = TrainerScreens.view(f.player(), f.trainer());
		helper.assertFalse(inBattle.canChallenge(), "Challenge isn't grayed out during the player's own battle");
		assertReadable(helper, inBattle, "in a battle");
		finish(f.player(), false);
		TrainerView tomorrow = TrainerScreens.view(f.player(), f.trainer());
		helper.assertValueEqual(key(tomorrow.refusal()), "message.aliveworkplace.trainer.leader_tomorrow", "after the day's challenge");
		assertReadable(helper, tomorrow, "come back tomorrow");
		ServerPlayer empty = joiner(helper, f.players(), new ArrayList<>(), "qa43textB");
		empty.moveTo(f.trainer().getX() - 1.5, f.trainer().getY(), f.trainer().getZ());
		TrainerView none = TrainerScreens.view(empty, f.trainer());
		helper.assertFalse(none.canChallenge(), "Challenge isn't grayed out with no party");
		assertReadable(helper, none, "no team");
		helper.succeed();
	}

	// ------------------------------------------------------------------ the Nurse's card (43.3)

	static Villager nurse(GameTestHelper helper) {
		QaNurseMachine1102dCompatTests.clean(helper);
		helper.setBlock(new BlockPos(2, 2, 2), Blocks.BREWING_STAND);
		Villager nurse = helper.spawn(EntityType.VILLAGER, new BlockPos(3, 2, 3));
		nurse.setNoAi(true);
		Jobs.employ(helper.getLevel(), nurse, helper.absolutePos(new BlockPos(2, 2, 2)), PoiTypes.CLERIC, ModVillagers.NURSE);
		nurse.setCustomName(Component.literal("Joy"));
		return nurse;
	}

	/** Everyone at full health: Heal still works (as a click did), heals nobody into anything odd, and says so in English. */
	//$ gametest_ticks_batch AREA '200' '"qa43NurseFull"'
	@GameTest(template = AREA, timeoutTicks = 200, batch = "qa43NurseFull")
	public void nurseHealWithEveryoneAtFullHealth(GameTestHelper helper) {
		Villager nurse = nurse(helper);
		QaNurseMachine1102dCompatTests.Visitor v = QaNurseMachine1102dCompatTests.visitor(helper, "qa43full");
		Pokemon a = QaNurseMachine1102dCompatTests.pokemon(v, "bulbasaur", -1);
		StatusCardMenu menu = StatusCardCompatTests.open(helper, v.player(), nurse);
		StatusCard card = menu.card();
		helper.assertValueEqual(StatusCardCompatTests.text(card.lines().get(0)), "You: 20 / 20 HP", "the player's line");
		helper.assertTrue(card.lines().get(0).color() == StatusCard.GREEN && card.lines().get(1).color() == StatusCard.GREEN, "full lines aren't green");
		helper.assertTrue(card.button(NurseCard.HEAL).usable(), "Heal grayed out at full health: " + card.button(NurseCard.HEAL).refusal().getString());
		assertReadable(helper, card, "the Nurse's card");
		int before = v.seen().size();
		StatusCardCompatTests.press(v.player(), menu, NurseCard.HEAL);
		helper.assertTrue(a.isFullHealth() && v.player().getHealth() == v.player().getMaxHealth(), "Heal hurt someone");
		helper.assertTrue(v.seen().size() > before, "Heal at full health said nothing");
		String said = v.seen().get(v.seen().size() - 1);
		helper.assertFalse(said.contains(" 0 Pokémon") || said.contains("aliveworkplace."), "her line: " + said);
		helper.succeed();
	}

	/** The card opened by day; she falls asleep; Heal then heals nothing and says she's asleep. */
	//$ gametest_ticks_batch AREA '200' '"qa43NurseAsleep"'
	@GameTest(template = AREA, timeoutTicks = 200, batch = "qa43NurseAsleep")
	public void nurseHealAfterSheFellAsleepWithTheCardOpen(GameTestHelper helper) {
		Villager nurse = nurse(helper);
		QaNurseMachine1102dCompatTests.Visitor v = QaNurseMachine1102dCompatTests.visitor(helper, "qa43nasleep");
		Pokemon hurt = QaNurseMachine1102dCompatTests.pokemon(v, "bulbasaur", 3);
		v.player().setHealth(6f);
		StatusCardMenu menu = StatusCardCompatTests.open(helper, v.player(), nurse);
		helper.assertTrue(menu.card().button(NurseCard.HEAL).usable(), "Heal grayed out by day: " + menu.card().button(NurseCard.HEAL).refusal().getString());
		nurse.startSleeping(nurse.blockPosition());
		StatusCardCompatTests.press(v.player(), menu, NurseCard.HEAL);
		helper.assertTrue(hurt.getCurrentHealth() == 3 && v.player().getHealth() == 6f, "a sleeping nurse healed: "
			+ hurt.getCurrentHealth() + " HP, player " + v.player().getHealth());
		helper.assertTrue(QaNurseMachine1102dCompatTests.said(v, "Joy is asleep"), "not told she's asleep: " + v.seen());
		nurse.stopSleeping();
		helper.succeed();
	}

	/** The card opened, the player walks 30 blocks off, then presses Heal: nothing is healed. */
	//$ gametest_ticks_batch AREA '200' '"qa43NurseFar"'
	@GameTest(template = AREA, timeoutTicks = 200, batch = "qa43NurseFar")
	public void nurseHealFromAPlayerWhoWalkedAway(GameTestHelper helper) {
		Villager nurse = nurse(helper);
		QaNurseMachine1102dCompatTests.Visitor v = QaNurseMachine1102dCompatTests.visitor(helper, "qa43nfar");
		Pokemon hurt = QaNurseMachine1102dCompatTests.pokemon(v, "bulbasaur", 3);
		v.player().setHealth(6f);
		StatusCardMenu menu = StatusCardCompatTests.open(helper, v.player(), nurse);
		v.player().teleportTo(nurse.getX() + 30, nurse.getY(), nurse.getZ());
		StatusCardCompatTests.press(v.player(), menu, NurseCard.HEAL);
		helper.assertTrue(hurt.getCurrentHealth() == 3 && v.player().getHealth() == 6f, "healed from 30 blocks away");
		helper.succeed();
	}

	/** Heal pressed twice (the packet twice, then the old card's button again): healed once; the second is told to wait. */
	//$ gametest_ticks_batch AREA '200' '"qa43NurseTwice"'
	@GameTest(template = AREA, timeoutTicks = 200, batch = "qa43NurseTwice")
	public void nurseHealPressedTwiceHealsOnce(GameTestHelper helper) {
		Villager nurse = nurse(helper);
		QaNurseMachine1102dCompatTests.Visitor v = QaNurseMachine1102dCompatTests.visitor(helper, "qa43ntwice");
		Pokemon hurt = QaNurseMachine1102dCompatTests.pokemon(v, "bulbasaur", 3);
		StatusCardMenu menu = StatusCardCompatTests.open(helper, v.player(), nurse);
		StatusCardCompatTests.press(v.player(), menu, NurseCard.HEAL);
		helper.assertTrue(hurt.isFullHealth(), "the first Heal didn't heal");
		hurt.setCurrentHealth(2);
		StatusCardCompatTests.press(v.player(), menu, NurseCard.HEAL);
		menu.clickMenuButton(v.player(), NurseCard.HEAL);
		helper.assertValueEqual(hurt.getCurrentHealth(), 2, "a second Heal straight after healed again");
		helper.assertValueEqual(count(v.seen(), "patched you up"), 1L, "heals (" + v.seen() + ")");
		helper.succeed();
	}

	// ------------------------------------------------------------------ the Fossil Scientist's card (43.3)

	static Revival handOverDome(GameTestHelper helper, Villager scientist, ServerPlayer player, List<String> seen) {
		QaFossilScientist1102dCompatTests.hold(player, new ItemStack(QaFossilScientist1102dCompatTests.cobblemon("dome_fossil")), ItemStack.EMPTY);
		QaFossilScientist1102dCompatTests.click(player, scientist, false, seen);
		helper.assertTrue(FossilScientists.queue(scientist).size() == 1, "setup: the fossil wasn't taken: " + QaFossilScientist1102dCompatTests.last(seen));
		return FossilScientists.queue(scientist).get(0);
	}

	/**
	 * Hand over with dirt in hand (put there after the card opened): nothing ready takes nothing and costs nothing; with the
	 * Kabuto ready it comes to the party and the dirt stays.
	 */
	//$ gametest_ticks_batch AREA '200' '"qa43FossilDirt"'
	@GameTest(template = AREA, timeoutTicks = 200, batch = "qa43FossilDirt")
	public void fossilHandOverWithSomethingElseInHand(GameTestHelper helper) {
		Villager scientist = QaFossilScientist1102dCompatTests.scientist(helper, 3600);
		List<String> seen = new ArrayList<>();
		ServerPlayer player = QaFossilScientist1102dCompatTests.player(helper, seen, UUID.randomUUID(), "qa43dirt");
		QaFossilScientist1102dCompatTests.setDollars(player, 5_000);
		helper.runAfterDelay(2, () -> {
			Revival r = handOverDome(helper, scientist, player, seen);
			long dollars = io.github.jcondedata.aliveworkplace.compat.cobbledollars.CobbleDollarsBank.balance(player);
			StatusCardMenu menu = StatusCardCompatTests.open(helper, player, scientist);
			player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.DIRT, 5));
			StatusCardCompatTests.press(player, menu, FossilCard.HAND_OVER);
			helper.assertTrue(player.getMainHandItem().is(Items.DIRT) && player.getMainHandItem().getCount() == 5, "the dirt: " + player.getMainHandItem());
			helper.assertTrue(FossilScientists.queue(scientist).size() == 1 && !FossilScientists.queue(scientist).get(0).ready(), "the queue changed");
			helper.assertValueEqual(io.github.jcondedata.aliveworkplace.compat.cobbledollars.CobbleDollarsBank.balance(player), dollars, "dollars");
			helper.assertTrue(QaFossilScientist1102dCompatTests.party(player).isEmpty(), "a Pokémon came before it was ready");

			ModAttachments.FOSSIL_REVIVALS.set(scientist, List.of(r.worked(r.total())));
			StatusCardMenu ready = StatusCardCompatTests.open(helper, player, scientist);
			player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.DIRT, 5));
			StatusCardCompatTests.press(player, ready, FossilCard.HAND_OVER);
			helper.assertTrue(QaFossilScientist1102dCompatTests.party(player).equals(List.of("kabuto")), "party: " + QaFossilScientist1102dCompatTests.party(player));
			helper.assertTrue(player.getMainHandItem().is(Items.DIRT) && player.getMainHandItem().getCount() == 5, "the dirt after: " + player.getMainHandItem());
			helper.assertTrue(FossilScientists.queue(scientist).isEmpty(), "still queued");
			helper.succeed();
		});
	}

	/** A Helix Fossil in hand when Hand over is pressed (the Dome still working): the Helix isn't lost (in hand, or queued). */
	//$ gametest_ticks_batch AREA '200' '"qa43FossilHelix"'
	@GameTest(template = AREA, timeoutTicks = 200, batch = "qa43FossilHelix")
	public void fossilHandOverWithAnotherFossilInHandLosesNothing(GameTestHelper helper) {
		Villager scientist = QaFossilScientist1102dCompatTests.scientist(helper, 3600);
		List<String> seen = new ArrayList<>();
		ServerPlayer player = QaFossilScientist1102dCompatTests.player(helper, seen, UUID.randomUUID(), "qa43helix");
		QaFossilScientist1102dCompatTests.setDollars(player, 5_000);
		helper.runAfterDelay(2, () -> {
			handOverDome(helper, scientist, player, seen);
			StatusCardMenu menu = StatusCardCompatTests.open(helper, player, scientist);
			var helix = QaFossilScientist1102dCompatTests.cobblemon("helix_fossil");
			player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(helix));
			StatusCardCompatTests.press(player, menu, FossilCard.HAND_OVER);
			long inHand = player.getInventory().countItem(helix);
			long queued = FossilScientists.queue(scientist).size() - 1L;
			helper.assertValueEqual(inHand + queued, 1L, "Helix Fossils in hand (" + inHand + ") and queued (" + queued + ")");
			helper.succeed();
		});
	}

	/** Hand over pressed twice (the packet twice, then the old card's button again): one Kabuto, in the party, none in the PC. */
	//$ gametest_ticks_batch AREA '200' '"qa43FossilTwice"'
	@GameTest(template = AREA, timeoutTicks = 200, batch = "qa43FossilTwice")
	public void fossilHandOverTwiceDeliversOne(GameTestHelper helper) {
		Villager scientist = QaFossilScientist1102dCompatTests.scientist(helper, 3600);
		List<String> seen = new ArrayList<>();
		ServerPlayer player = QaFossilScientist1102dCompatTests.player(helper, seen, UUID.randomUUID(), "qa43ftwice");
		QaFossilScientist1102dCompatTests.setDollars(player, 5_000);
		helper.runAfterDelay(2, () -> {
			Revival r = handOverDome(helper, scientist, player, seen);
			ModAttachments.FOSSIL_REVIVALS.set(scientist, List.of(r.worked(r.total())));
			StatusCardMenu menu = StatusCardCompatTests.open(helper, player, scientist);
			StatusCardCompatTests.press(player, menu, FossilCard.HAND_OVER);
			StatusCardCompatTests.press(player, menu, FossilCard.HAND_OVER);
			menu.clickMenuButton(player, FossilCard.HAND_OVER);
			helper.assertValueEqual(QaFossilScientist1102dCompatTests.count(QaFossilScientist1102dCompatTests.party(player), "kabuto")
				+ QaFossilScientist1102dCompatTests.count(QaFossilScientist1102dCompatTests.pc(player), "kabuto"), 1L, "Kabuto handed over");
			helper.assertTrue(FossilScientists.queue(scientist).isEmpty(), "still queued");
			helper.assertValueEqual(count(seen, "joins you"), 1L, "revived lines (" + seen + ")");
			helper.succeed();
		});
	}

	/** Hand over with a full party of six: the Kabuto goes to the PC, not lost; the queue empties. */
	//$ gametest_ticks_batch AREA '200' '"qa43FossilFull"'
	@GameTest(template = AREA, timeoutTicks = 200, batch = "qa43FossilFull")
	public void fossilHandOverWithAFullPartyLosesNothing(GameTestHelper helper) {
		Villager scientist = QaFossilScientist1102dCompatTests.scientist(helper, 3600);
		List<String> seen = new ArrayList<>();
		ServerPlayer player = QaFossilScientist1102dCompatTests.player(helper, seen, UUID.randomUUID(), "qa43ffull");
		QaFossilScientist1102dCompatTests.setDollars(player, 5_000);
		helper.runAfterDelay(2, () -> {
			for (int i = 0; i < 6; i++) {
				party(player, "rattata level=5");
			}
			Revival r = handOverDome(helper, scientist, player, seen);
			ModAttachments.FOSSIL_REVIVALS.set(scientist, List.of(r.worked(r.total())));
			StatusCardMenu menu = StatusCardCompatTests.open(helper, player, scientist);
			helper.assertTrue(menu.card().button(FossilCard.HAND_OVER).usable(), "Hand over grayed out with one ready");
			StatusCardCompatTests.press(player, menu, FossilCard.HAND_OVER);
			helper.assertValueEqual(QaFossilScientist1102dCompatTests.count(QaFossilScientist1102dCompatTests.pc(player), "kabuto"), 1L,
				"Kabuto in the PC (party " + QaFossilScientist1102dCompatTests.party(player) + ")");
			helper.assertTrue(FossilScientists.queue(scientist).isEmpty(), "still queued");
			helper.succeed();
		});
	}

	/**
	 * The card's progress near the end and after: two seconds short it reads 99% with Hand over grayed out as "not ready
	 * yet"; once the scientist finishes it by her own work, the Kabuto comes by itself and the card
	 * then lists nothing of the player's.
	 */
	//$ gametest_ticks_batch AREA '600' '"qa43FossilDone"'
	@GameTest(template = AREA, timeoutTicks = 600, batch = "qa43FossilDone")
	public void fossilProgressNearAndAfterTheEnd(GameTestHelper helper) {
		Villager scientist = QaFossilScientist1102dCompatTests.scientist(helper, 3600);
		List<String> seen = new ArrayList<>();
		ServerPlayer player = QaFossilScientist1102dCompatTests.player(helper, seen, UUID.randomUUID(), "qa43fdone");
		QaFossilScientist1102dCompatTests.setDollars(player, 5_000);
		helper.runAfterDelay(2, () -> {
			Revival r = handOverDome(helper, scientist, player, seen);
			ModAttachments.FOSSIL_REVIVALS.set(scientist, List.of(r.worked(r.total() - 40)));
			StatusCard almost = StatusCardCompatTests.open(helper, player, scientist).card();
			String line = StatusCardCompatTests.text(almost.lines().get(0));
			helper.assertValueEqual(line, "Dome Fossil: 99%", "two seconds short");
			helper.assertValueEqual(key(almost.button(FossilCard.HAND_OVER).refusal()), "screen.aliveworkplace.card.fossil.not_ready", "Hand over's reason");
			assertReadable(helper, almost, "the Fossil Scientist's card");
			player.closeContainer();
			player.moveTo(scientist.getX() + 2, scientist.getY(), scientist.getZ());
			helper.succeedWhen(() -> {
				helper.assertTrue(QaFossilScientist1102dCompatTests.party(player).contains("kabuto"), "the Kabuto hasn't come by itself yet: " + seen);
				StatusCard after = FossilCard.card(player, scientist);
				helper.assertTrue(after.lines().isEmpty(), "the card still lists a delivered fossil");
				helper.assertValueEqual(key(after.button(FossilCard.HAND_OVER).refusal()), "screen.aliveworkplace.card.fossil.none", "Hand over's reason after");
				assertReadable(helper, after, "the Fossil Scientist's card after");
			});
		});
	}
}
