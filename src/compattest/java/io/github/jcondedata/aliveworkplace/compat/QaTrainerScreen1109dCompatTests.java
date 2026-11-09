package io.github.jcondedata.aliveworkplace.compat;

import static io.github.jcondedata.aliveworkplace.compat.QaPokemonScreens1109dCompatTests.assertReadable;
import static io.github.jcondedata.aliveworkplace.compat.QaPokemonScreens1109dCompatTests.battling;
import static io.github.jcondedata.aliveworkplace.compat.QaPokemonScreens1109dCompatTests.finish;
import static io.github.jcondedata.aliveworkplace.compat.QaPokemonScreens1109dCompatTests.key;
import static io.github.jcondedata.aliveworkplace.compat.QaPokemonScreens1109dCompatTests.openTrainer;
import static io.github.jcondedata.aliveworkplace.compat.QaPokemonScreens1109dCompatTests.press;
import static io.github.jcondedata.aliveworkplace.compat.QaPokemonScreens1109dCompatTests.rightClick;
import static io.github.jcondedata.aliveworkplace.compat.QaPokemonScreens1109dCompatTests.trainer;

import io.github.jcondedata.aliveworkplace.build.BuilderLevels;
import io.github.jcondedata.aliveworkplace.compat.QaPokemonScreens1109dCompatTests.Fight;
import io.github.jcondedata.aliveworkplace.cup.CupData;
import io.github.jcondedata.aliveworkplace.hall.Chronicle;
import io.github.jcondedata.aliveworkplace.registry.ModBlocks;
import io.github.jcondedata.aliveworkplace.registry.ModVillagers;
import io.github.jcondedata.aliveworkplace.trainer.TrainerMenu;
import io.github.jcondedata.aliveworkplace.trainer.TrainerScreens;
import io.github.jcondedata.aliveworkplace.trainer.TrainerView;
import io.github.jcondedata.aliveworkplace.trainer.Trainers;
import io.github.jcondedata.aliveworkplace.work.Jobs;
import java.util.List;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.locale.Language;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ServerboundInteractPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.inventory.MerchantMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.trading.ItemCost;
import net.minecraft.world.item.trading.MerchantOffer;
import net.minecraft.world.phys.Vec3;

/**
 * QA (2026-11-09, lane d, second pass), written from ROADMAP 43.1's spec (the Trainer screen), not from the code. The
 * cases the feature's tests and the first QA pass leave out: "an empty hand" both sides (an item in the main hand opens
 * no screen; an empty main hand with a torch in the off hand does); a Leader away at another village's Cup (not a
 * delegate) and that refusal's boundaries; the "come back tomorrow" and the daily prize ending at the next day while the
 * reveal stays; the senior Leader's own screen; the reveal kept per trainer; hidden slots giving nothing away, even
 * after the trip to the client; forged and stale button clicks (unknown ids, an old screen's id, Trade grayed out); the
 * screen closing when the player walks off or the trainer dies; a Leader's daily challenge and a loss surviving a
 * reload; and the screen's own labels being American English. Every click and press goes through the packet handler.
 */
public class QaTrainerScreen1109dCompatTests implements FabricGameTest {
	private static final String AREA = CompatGameTests.AREA;

	/** A client's right-click on {@code villager} with whatever is in the hands now (main hand, then the off hand on a pass). */
	private static void clickHolding(ServerPlayer player, Villager villager) {
		player.setShiftKeyDown(false);
		player.moveTo(villager.getX() + 1.5, villager.getY(), villager.getZ(), 90f, 0f);
		for (InteractionHand hand : InteractionHand.values()) {
			if (player.containerMenu != player.inventoryMenu) {
				return;
			}
			player.connection.handleInteract(ServerboundInteractPacket.createInteractionPacket(villager, false, hand,
				new Vec3(0, villager.getBbHeight() / 2, 0)));
			if (player.containerMenu == player.inventoryMenu) {
				player.connection.handleInteract(ServerboundInteractPacket.createInteractionPacket(villager, false, hand));
			}
		}
	}

	/** Moves the world's clock on by {@code days} for this test, and back to the morning when it ends. */
	private static void laterDays(GameTestHelper helper, int days) {
		ServerLevel level = helper.getLevel();
		PartnerShowsCompatTests.after(helper, () -> level.setDayTime(2000));
		level.setDayTime(level.getDayTime() + 24000L * days);
	}

	private static Villager secondTrainer(GameTestHelper helper, boolean leader) {
		BlockPos post = new BlockPos(6, 2, 4);
		helper.setBlock(post, leader ? ModBlocks.LEADERS_PODIUM : ModBlocks.TRAINING_POST);
		Villager v = helper.spawn(EntityType.VILLAGER, new BlockPos(6, 2, 6));
		v.setNoAi(true);
		Jobs.employ(helper.getLevel(), v, helper.absolutePos(post), leader ? ModVillagers.LEADERS_PODIUM_POI : ModVillagers.TRAINING_POST_POI,
			leader ? ModVillagers.TRAINER_LEADER : ModVillagers.TRAINER);
		return v;
	}

	private static long hidden(TrainerView v) {
		return v.team().stream().filter(TrainerView.TeamSlot::hidden).count();
	}

	// ------------------------------------------------------------------ the player's entry point

	/**
	 * "An empty hand" opens the screen: an item in the main hand doesn't (and starts no battle); an empty main hand with a
	 * torch in the off hand does, once. The left side names the trainer, their title and rank.
	 */
	//$ gametest_ticks_batch AREA '200' '"qa43bHand"'
	@GameTest(template = AREA, timeoutTicks = 200, batch = "qa43bHand")
	public void onlyAnEmptyMainHandOpensTheScreen(GameTestHelper helper) {
		Fight f = trainer(helper, false, "qa43bhand");
		f.trainer().setCustomName(Component.literal("Mira"));
		ServerPlayer p = f.player();
		p.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.COBBLESTONE));
		clickHolding(p, f.trainer());
		helper.assertFalse(p.containerMenu instanceof TrainerMenu, "a hand holding cobblestone opened the Trainer screen");
		helper.assertFalse(battling(p), "a hand holding cobblestone started a battle");
		f.trainer().setTradingPlayer(null);
		p.closeContainer();
		p.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
		p.setItemInHand(InteractionHand.OFF_HAND, new ItemStack(Items.TORCH));
		clickHolding(p, f.trainer());
		helper.assertTrue(p.containerMenu instanceof TrainerMenu, "an empty main hand (torch in the off hand) didn't open the screen: "
			+ p.containerMenu.getClass().getSimpleName());
		helper.assertFalse(battling(p), "the click started a battle at once");
		TrainerView v = ((TrainerMenu) p.containerMenu).view();
		helper.assertValueEqual(v.name().getString(), "Mira", "the trainer's name");
		helper.assertValueEqual(v.title().getString(), "Pokémon Trainer", "the trainer's title");
		String rank = v.rank().getString();
		helper.assertTrue(rank.contains(BuilderLevels.levelName(Trainers.tier(f.trainer())).getString()), "the rank doesn't name the tier: " + rank);
		helper.assertValueEqual(p.getOffhandItem().getCount(), 1, "the off hand's torch was used up");
		assertReadable(helper, v, "a named trainer");
		helper.succeed();
	}

	// ------------------------------------------------------------------ refusals and their boundaries

	/**
	 * A Leader entered in another village's Cup today is away: the screen grays Challenge with the Cup's reason, and a press
	 * anyway starts nothing. The same entry in a Cup at the Leader's own village, or a Cup that isn't today's, refuses nothing.
	 */
	//$ gametest_ticks_batch AREA '200' '"qa43bCup"'
	@GameTest(template = AREA, timeoutTicks = 200, batch = "qa43bCup")
	public void leaderAwayAtAnotherVillagesCup(GameTestHelper helper) {
		Fight f = trainer(helper, true, "qa43bcup");
		ServerLevel level = helper.getLevel();
		BlockPos host = helper.absolutePos(new BlockPos(0, 40, 0)).offset(3000, 0, 3000);
		BlockPos home = helper.absolutePos(new BlockPos(2, 2, 2));
		CupData data = CupData.get(level);
		PartnerShowsCompatTests.after(helper, () -> data.forget(host));
		CupData.Cup cup = data.cup(host);
		CupData.Entrant me = new CupData.Entrant(CupData.Kind.LEADER, f.trainer().getUUID(), "Leader", 4, 0, home);
		cup.closed = true;
		cup.entrants.add(me);
		cup.bracket.add(me);
		cup.day = Chronicle.day(level) - 1; // yesterday's Cup: not away
		helper.assertTrue(TrainerScreens.view(f.player(), f.trainer()).canChallenge(), "yesterday's Cup still keeps the Leader away");
		cup.day = Chronicle.day(level);
		TrainerMenu menu = openTrainer(helper, f.player(), f.trainer());
		helper.assertFalse(menu.view().canChallenge(), "Challenge isn't grayed out with the Leader away at the Cup");
		helper.assertValueEqual(key(menu.view().refusal()), "message.aliveworkplace.cup.away", "the reason under Challenge");
		assertReadable(helper, menu.view(), "away at the Cup");
		press(f.player(), menu.containerId, TrainerMenu.CHALLENGE);
		helper.assertFalse(battling(f.player()), "a Leader away at the Cup battled");
		f.player().closeContainer();
		data.forget(host);
		CupData.Cup own = data.cup(home);
		CupData.Entrant atHome = new CupData.Entrant(CupData.Kind.LEADER, f.trainer().getUUID(), "Leader", 4, 0, home);
		own.closed = true;
		own.entrants.add(atHome);
		own.bracket.add(atHome);
		own.day = Chronicle.day(level);
		boolean free = TrainerScreens.view(f.player(), f.trainer()).canChallenge();
		data.forget(home);
		helper.assertTrue(free, "a Leader whose own village hosts the Cup counts as away");
		helper.succeed();
	}

	/** The village's senior Leader is the one who takes challenges: its own screen offers Challenge, and it starts the battle. */
	//$ gametest_ticks_batch AREA '200' '"qa43bSenior"'
	@GameTest(template = AREA, timeoutTicks = 200, batch = "qa43bSenior")
	public void theSeniorLeaderTakesTheChallenge(GameTestHelper helper) {
		Fight f = trainer(helper, true, "qa43bsenior");
		Villager senior = secondTrainer(helper, true);
		senior.setVillagerXp(f.trainer().getVillagerXp() + 50);
		helper.assertValueEqual(key(TrainerScreens.view(f.player(), f.trainer()).refusal()), "message.aliveworkplace.trainer.not_the_leader",
			"setup: the junior Leader's reason");
		TrainerMenu menu = openTrainer(helper, f.player(), senior);
		helper.assertTrue(menu.view().canChallenge(), "the senior Leader refuses: " + menu.view().refusal().getString());
		press(f.player(), menu.containerId, TrainerMenu.CHALLENGE);
		helper.assertTrue(battling(f.player()), "the senior Leader's Challenge started no battle");
		helper.succeed();
	}

	/**
	 * "Come back tomorrow" ends with the day: the next day the Leader's screen offers Challenge again, today's line is
	 * reset, and the press starts the battle.
	 */
	//$ gametest_ticks_batch AREA '200' '"qa43bTomorrow"'
	@GameTest(template = AREA, timeoutTicks = 200, batch = "qa43bTomorrow")
	public void comeBackTomorrowEndsTheNextDay(GameTestHelper helper) {
		Fight f = trainer(helper, true, "qa43btomorrow");
		press(f.player(), openTrainer(helper, f.player(), f.trainer()).containerId, TrainerMenu.CHALLENGE);
		helper.assertTrue(battling(f.player()), "setup: the Leader's battle didn't start");
		finish(f.player(), false);
		helper.assertValueEqual(key(TrainerScreens.view(f.player(), f.trainer()).refusal()), "message.aliveworkplace.trainer.leader_tomorrow",
			"setup: the reason after today's challenge");
		laterDays(helper, 1);
		TrainerMenu menu = openTrainer(helper, f.player(), f.trainer());
		TrainerView v = menu.view();
		helper.assertTrue(v.canChallenge(), "still refused the next day: " + v.refusal().getString());
		helper.assertValueEqual(key(v.today()), "screen.aliveworkplace.trainer.today.none", "today's line the next day");
		helper.assertValueEqual(v.record().getString(), "0 won · 1 lost", "the record the next day");
		press(f.player(), menu.containerId, TrainerMenu.CHALLENGE);
		helper.assertTrue(battling(f.player()), "the next day's Challenge started no battle");
		helper.succeed();
	}

	// ------------------------------------------------------------------ the team, the reward, the reveal

	/**
	 * Beaten once is beaten for good: the next day the whole team still shows, while the reward offers the prize again
	 * and today's line is reset. A win against one trainer reveals nothing of another's team.
	 */
	//$ gametest_ticks_batch AREA '400' '"qa43bReveal"'
	@GameTest(template = AREA, timeoutTicks = 400, batch = "qa43bReveal")
	public void theRevealOutlastsTheDayAndStaysWithItsTrainer(GameTestHelper helper) {
		Fight f = trainer(helper, false, "qa43breveal");
		Villager other = secondTrainer(helper, false);
		TrainerView before = TrainerScreens.view(f.player(), other);
		helper.assertTrue(hidden(before) > 0, "setup: the other trainer's team hides nothing (" + before.team().size() + ")");
		press(f.player(), openTrainer(helper, f.player(), f.trainer()).containerId, TrainerMenu.CHALLENGE);
		helper.assertTrue(battling(f.player()), "setup: the battle didn't start");
		finish(f.player(), true);
		TrainerView theirs = openTrainer(helper, f.player(), other).view();
		helper.assertValueEqual(hidden(theirs), hidden(before), "beating one trainer revealed another's team");
		helper.assertValueEqual(theirs.record().getString(), "0 won · 0 lost", "the other trainer's record");
		f.player().closeContainer();
		laterDays(helper, 1);
		TrainerView next = openTrainer(helper, f.player(), f.trainer()).view();
		helper.assertValueEqual(hidden(next), 0L, "the team hid again the next day");
		helper.assertValueEqual(key(next.reward()), "screen.aliveworkplace.trainer.reward", "the next day's reward line");
		helper.assertValueEqual(key(next.today()), "screen.aliveworkplace.trainer.today.none", "the next day's today line");
		helper.assertTrue(next.canChallenge(), "a trainer refuses the next day: " + next.refusal().getString());
		helper.succeed();
	}

	/**
	 * A Pokémon not seen yet gives nothing away: no model, no level, no "scaled" mark, on the server's view and after the
	 * trip to the client. Which slots show, and their levels, are the same each time the screen opens (the models' look:
	 * QaTrainerScreen1109dBugCompatTests).
	 */
	//$ gametest_ticks_batch AREA '200' '"qa43bHidden"'
	@GameTest(template = AREA, timeoutTicks = 200, batch = "qa43bHidden")
	public void hiddenPokemonGiveNothingAway(GameTestHelper helper) {
		Fight f = trainer(helper, false, "qa43bhidden");
		TrainerView first = openTrainer(helper, f.player(), f.trainer()).view();
		f.player().closeContainer();
		TrainerView again = openTrainer(helper, f.player(), f.trainer()).view();
		helper.assertTrue(hidden(first) > 0, "setup: nothing hidden in a team of " + first.team().size());
		var buf = new net.minecraft.network.RegistryFriendlyByteBuf(io.netty.buffer.Unpooled.buffer(), helper.getLevel().registryAccess());
		TrainerView.STREAM_CODEC.encode(buf, first);
		TrainerView client = TrainerView.STREAM_CODEC.decode(buf);
		for (TrainerView v : List.of(first, client)) {
			String which = v == first ? "on the server" : "on the client";
			for (TrainerView.TeamSlot slot : v.team()) {
				if (slot.hidden()) {
					helper.assertTrue(slot.level() == 0 && !slot.scaled(), "a hidden Pokémon shows its level " + slot.level() + " " + which);
				}
			}
			helper.assertValueEqual(hidden(v), hidden(first), "hidden Pokémon " + which);
			helper.assertFalse(v.canChallenge() != first.canChallenge(), "Challenge's state changed " + which);
		}
		for (int i = 0; i < first.team().size(); i++) {
			TrainerView.TeamSlot a = first.team().get(i);
			TrainerView.TeamSlot b = again.team().get(i);
			helper.assertTrue(a.hidden() == b.hidden() && a.level() == b.level() && a.icon().getItem() == b.icon().getItem(),
				"slot " + i + " changed between two opens: " + a.level() + " / " + b.level());
		}
		helper.succeed();
	}

	// ------------------------------------------------------------------ forged and stale clicks

	/**
	 * Clicks the client's screen never sends: unknown button ids do nothing (no battle, no trades, the screen stays); an old
	 * screen's Challenge after Close starts nothing; Trade grayed out for a sleeping trainer opens no trades; Trade from 30
	 * blocks off opens none.
	 */
	//$ gametest_ticks_batch AREA '200' '"qa43bForged"'
	@GameTest(template = AREA, timeoutTicks = 200, batch = "qa43bForged")
	public void forgedAndStaleButtonsDoNothing(GameTestHelper helper) {
		Fight f = trainer(helper, false, "qa43bforged");
		ServerPlayer p = f.player();
		Villager t = f.trainer();
		t.getOffers().add(new MerchantOffer(new ItemCost(Items.EMERALD), new ItemStack(Items.BREAD), 4, 1, 0.05f));
		TrainerMenu menu = openTrainer(helper, p, t);
		for (int id : new int[]{3, 4, -1, 99}) {
			press(p, menu.containerId, id);
			helper.assertFalse(battling(p), "button " + id + " started a battle");
			helper.assertTrue(t.getTradingPlayer() == null, "button " + id + " opened the trades");
			helper.assertTrue(p.containerMenu == menu, "button " + id + " closed or replaced the screen");
		}
		int old = menu.containerId;
		press(p, old, TrainerMenu.CLOSE);
		helper.assertTrue(p.containerMenu == p.inventoryMenu, "Close left the screen open");
		press(p, old, TrainerMenu.CHALLENGE);
		helper.assertFalse(battling(p), "a closed screen's Challenge started a battle");
		// Trade grayed out (asleep): pressed anyway
		t.startSleeping(t.blockPosition());
		TrainerMenu asleep = openTrainer(helper, p, t);
		helper.assertFalse(asleep.view().canTrade(), "setup: Trade isn't grayed out for a sleeping trainer");
		press(p, asleep.containerId, TrainerMenu.TRADE);
		helper.assertFalse(p.containerMenu instanceof MerchantMenu, "a grayed-out Trade opened the trades");
		helper.assertTrue(t.getTradingPlayer() == null, "a sleeping trainer is trading");
		t.stopSleeping();
		p.closeContainer();
		// Trade from far off
		TrainerMenu far = openTrainer(helper, p, t);
		p.teleportTo(t.getX() + 30, t.getY(), t.getZ());
		press(p, far.containerId, TrainerMenu.TRADE);
		helper.assertFalse(p.containerMenu instanceof MerchantMenu, "Trade pressed 30 blocks away opened the trades");
		helper.assertTrue(t.getTradingPlayer() == null, "Trade pressed 30 blocks away: the trainer is trading");
		helper.succeed();
	}

	/** The screen closes by itself when the player walks off past its reach. */
	//$ gametest_ticks_batch AREA '200' '"qa43bWalkOff"'
	@GameTest(template = AREA, timeoutTicks = 200, batch = "qa43bWalkOff")
	public void theScreenClosesWhenThePlayerWalksOff(GameTestHelper helper) {
		Fight f = trainer(helper, false, "qa43bwalk");
		openTrainer(helper, f.player(), f.trainer());
		f.player().teleportTo(f.trainer().getX() + 4, f.trainer().getY(), f.trainer().getZ());
		helper.runAfterDelay(3, () -> {
			helper.assertTrue(f.player().containerMenu instanceof TrainerMenu, "the screen closed with the player 4 blocks off");
			TrainerMenu menu = (TrainerMenu) f.player().containerMenu;
			f.player().teleportTo(f.trainer().getX() + 30, f.trainer().getY(), f.trainer().getZ());
			helper.assertFalse(menu.stillValid(f.player()), "the screen still counts as in reach 30 blocks away");
			// the server's own player tick (the test's player stands in a chunk the test world may not tick)
			f.player().tick();
			helper.assertTrue(f.player().containerMenu == f.player().inventoryMenu, "the screen stayed open 30 blocks away");
			helper.assertFalse(battling(f.player()), "walking off started a battle");
			helper.succeed();
		});
	}

	/** The screen closes by itself when the trainer dies. */
	//$ gametest_ticks_batch AREA '200' '"qa43bDies"'
	@GameTest(template = AREA, timeoutTicks = 200, batch = "qa43bDies")
	public void theScreenClosesWhenTheTrainerDies(GameTestHelper helper) {
		Fight f = trainer(helper, false, "qa43bdies");
		openTrainer(helper, f.player(), f.trainer());
		f.trainer().kill();
		helper.runAfterDelay(3, () -> {
			helper.assertTrue(f.player().containerMenu == f.player().inventoryMenu, "the screen stayed open on a dead trainer");
			helper.succeed();
		});
	}

	// ------------------------------------------------------------------ save and reload

	/** A Leader's used challenge and a loss are saved with the trainer: after a reload, still "come back tomorrow" and "1 lost". */
	//$ gametest_ticks_batch AREA '200' '"qa43bReload"'
	@GameTest(template = AREA, timeoutTicks = 200, batch = "qa43bReload")
	public void aLeadersDayAndALossSurviveAReload(GameTestHelper helper) {
		Fight f = trainer(helper, true, "qa43breload");
		press(f.player(), openTrainer(helper, f.player(), f.trainer()).containerId, TrainerMenu.CHALLENGE);
		helper.assertTrue(battling(f.player()), "setup: the Leader's battle didn't start");
		finish(f.player(), false);
		CompoundTag saved = f.trainer().saveWithoutId(new CompoundTag());
		Villager copy = EntityType.VILLAGER.create(helper.getLevel());
		copy.load(saved);
		TrainerView v = TrainerScreens.view(f.player(), copy);
		helper.assertFalse(v.canChallenge(), "the day's challenge was forgotten in the reload");
		helper.assertValueEqual(key(v.refusal()), "message.aliveworkplace.trainer.leader_tomorrow", "the reason after a reload");
		helper.assertValueEqual(v.record().getString(), "0 won · 1 lost", "the loss after a reload");
		helper.assertValueEqual(key(v.today()), "screen.aliveworkplace.trainer.today.challenged", "today's line after a reload");
		helper.assertTrue(hidden(v) > 0, "a loss revealed the team after a reload");
		copy.discard();
		helper.succeed();
	}

	// ------------------------------------------------------------------ text

	/** The screen's own labels (buttons, headings, hints) have English text, filled in, in American spelling. */
	//$ gametest AREA
	@GameTest(template = AREA)
	public void theScreensLabelsAreAmericanEnglish(GameTestHelper helper) {
		Language lang = Language.getInstance();
		List<String> keys = List.of("record", "today", "rules_heading", "challenge_heading", "team.scaled", "team.unseen", "level", "party",
			"party.over_cap", "button.challenge", "button.trade", "button.close", "hint", "trainer", "rank", "rank_village");
		List<String> british = List.of("colour", "grey", "honour", "defence", "armour", "cancelled", "centre", "favourite", "travelled", "practise");
		for (String k : keys) {
			String full = "screen.aliveworkplace.trainer." + k;
			helper.assertTrue(lang.has(full), full + " has no English text");
			String text = Component.translatable(full, "Mira", "Expert").getString();
			helper.assertFalse(text.isBlank() || text.contains("aliveworkplace.") || text.contains("%"), full + " reads \"" + text + "\"");
			for (String word : british) {
				helper.assertFalse(text.toLowerCase().contains(word), full + " uses British spelling: " + text);
			}
		}
		helper.assertValueEqual(Component.translatable("screen.aliveworkplace.trainer.party.over_cap", 50).getString(), "Over the cap of 50",
			"the over-the-cap line");
		helper.succeed();
	}
}
