package io.github.jcondedata.aliveworkplace.compat;

import static io.github.jcondedata.aliveworkplace.compat.QaDaycareFallbacks1115dBugCompatTests.fillPc;
import static io.github.jcondedata.aliveworkplace.compat.QaDaycareFallbacks1115dCompatTests.*;

import com.cobblemon.mod.common.Cobblemon;
import com.cobblemon.mod.common.api.storage.party.PlayerPartyStore;
import com.cobblemon.mod.common.api.storage.pc.PCStore;
import com.cobblemon.mod.common.pokemon.Pokemon;
import io.github.jcondedata.aliveworkplace.compat.cobbledollars.CobbleDollarsBank;
import io.github.jcondedata.aliveworkplace.compat.cobblemon.CobblemonDaycare;
import io.github.jcondedata.aliveworkplace.compat.cobblemon.CobblemonDaycareKeeper;
import io.github.jcondedata.aliveworkplace.daycare.DaycareKeepers;
import io.github.jcondedata.aliveworkplace.ranch.Daycare;
import io.github.jcondedata.aliveworkplace.work.ChoiceMenu;
import io.github.jcondedata.aliveworkplace.work.Money;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.item.ItemStack;

/**
 * QA (lane C, 2026-11-15): B1284/B1285's fix from the spec (ROADMAP 28.12 "to the party or the PC"; the bug texts: Take
 * back places both or neither and she keeps the pair with a line saying the PC is full; Collect refused before charging;
 * a hatchling or egg with nowhere to go refunded and left waiting; the price line only when the money ran out). The gaps
 * lane D's QaDaycareFallbacks1115d tests leave: exactly one PC slot (or one party slot) for a pair of two, the rancher's
 * Collect with no room (uncharged, then working once room is made), a PC with one slot for a boarder, the egg's
 * no-room line and what happens once room is made, no money and no room, Cobbreeding eggs with a full party and PC, the
 * hatchling with no room and then room, and every new sentence read in full.
 */
public class QaDaycareRoom1115cCompatTests implements FabricGameTest {
	private static final String AREA = CompatGameTests.AREA;

	// ---- shared ----

	static int pcCount(PCStore pc) {
		int n = 0;
		for (Pokemon ignored : pc) {
			n++;
		}
		return n;
	}

	static boolean inBackup(PCStore pc, UUID id) {
		return pc.getBackupStore().getPokemon().stream().anyMatch(p -> p.getUuid().equals(id));
	}

	/** Fills the PC (one box, as lane D's helper) and frees exactly one slot in it. */
	static PCStore pcWithOneFreeSlot(ServerPlayer player) {
		PCStore pc = fillPc(Cobblemon.INSTANCE.getStorage().getPC(player));
		Pokemon any = pc.iterator().next();
		pc.remove(any);
		return pc;
	}

	static void removeOneFromParty(ServerPlayer player) {
		PlayerPartyStore store = Cobblemon.INSTANCE.getStorage().getParty(player);
		List<Pokemon> now = party(player);
		store.remove(now.get(now.size() - 1));
	}

	static String text(String key, Object... args) {
		return Component.translatable(key, args).getString();
	}

	static long eggPrice() {
		return (long) DaycareKeepers.EGG_PRICE * Money.DOLLARS_PER_EMERALD;
	}

	// ---- the keeper's Take back: both or neither ----

	/**
	 * Party full, the PC with exactly ONE free slot: the first of the pair would fit, the second wouldn't. Neither is
	 * placed, she keeps the pair, the line is the no-room one, and the PC still has that one free slot (the first was
	 * taken back out, not duplicated or lost). Then one party slot is freed: Take back works, both are placed.
	 */
	//$ gametest_ticks_batch AREA '100' '"qaDaycareRoom1115cOnePcSlot"'
	@GameTest(template = AREA, timeoutTicks = 100, batch = "qaDaycareRoom1115cOnePcSlot")
	public void qaTakeBackWithOnePcSlotPlacesNeitherAndKeepsTheSlot(GameTestHelper helper) {
		Villager keeper = keeper(helper);
		List<String> seen = new ArrayList<>();
		ServerPlayer player = player(helper, seen, 0, TWO_EEVEE_AND_ONE, "eevee gender=female level=20", "eevee gender=male level=20");
		UUID[] pair = leavePair(helper, keeper, player);
		fillParty(player);
		PCStore pc = pcWithOneFreeSlot(player);
		int pcBefore = pcCount(pc);
		helper.assertTrue(pc.getFirstAvailablePosition() != null, "setup: no free PC slot");
		ChoiceMenu menu = open(helper, player, keeper);
		seen.clear();
		click(player, menu, CobblemonDaycareKeeper.TAKE_BACK);
		String noRoom = "Your party and PC are full: make room for both before taking your pair back.";
		helper.assertTrue(seen.contains(noRoom), "with one PC slot for two, told " + seen);
		helper.assertTrue(!seen.contains("You took your pair back."), "told the pair was taken back: " + seen);
		for (UUID id : pair) {
			helper.assertTrue(!inParty(player, id) && !inPc(pc, id) && !inBackup(pc, id), "one of the pair was placed: party " + inParty(player, id)
				+ ", PC " + inPc(pc, id) + ", overflow " + inBackup(pc, id));
		}
		helper.assertValueEqual(pcCount(pc), pcBefore, "Pokémon in the PC after the refused Take back");
		helper.assertTrue(pc.getFirstAvailablePosition() != null, "the PC's one free slot was used up by a refused Take back");
		helper.assertValueEqual(party(player).size(), 6, "party size after the refusal");
		List<DaycareKeepers.Pair> kept = DaycareKeepers.pairs(keeper);
		helper.assertTrue(kept.size() == 1 && kept.get(0).owner().equals(player.getUUID()), "she doesn't hold the pair any more: " + kept);
		Pokemon m = CobblemonDaycareKeeper.load(helper.getLevel(), kept.get(0).first());
		Pokemon f = CobblemonDaycareKeeper.load(helper.getLevel(), kept.get(0).second());
		helper.assertTrue(m != null && f != null && m.getUuid().equals(pair[0]) && f.getUuid().equals(pair[1]), "the pair she holds isn't the one left");
		// Room for both now (one party slot + one PC slot): Take back works
		removeOneFromParty(player);
		seen.clear();
		click(player, menu, CobblemonDaycareKeeper.TAKE_BACK);
		helper.assertTrue(seen.contains("You took your pair back."), "with room for both, told " + seen);
		helper.assertTrue(inParty(player, pair[0]) && inPc(pc, pair[1]), "after making room: mother in party " + inParty(player, pair[0])
			+ ", father in PC " + inPc(pc, pair[1]));
		helper.assertTrue(DaycareKeepers.pairs(keeper).isEmpty(), "she still holds the pair after it was taken back");
		helper.assertTrue(pc.getFirstAvailablePosition() == null && pcCount(pc) == pcBefore + 1, "PC count " + pcCount(pc) + ", expected "
			+ (pcBefore + 1));
		helper.succeed();
	}

	/**
	 * One party slot and a full PC: the first of the pair would join the party, the second has nowhere to go. Neither
	 * is placed (the first is taken back out of the party), the party is unchanged.
	 */
	//$ gametest_ticks_batch AREA '100' '"qaDaycareRoom1115cOnePartySlot"'
	@GameTest(template = AREA, timeoutTicks = 100, batch = "qaDaycareRoom1115cOnePartySlot")
	public void qaTakeBackWithOnePartySlotAndAFullPcPlacesNeither(GameTestHelper helper) {
		Villager keeper = keeper(helper);
		List<String> seen = new ArrayList<>();
		ServerPlayer player = player(helper, seen, 0, TWO_EEVEE_AND_ONE, "eevee gender=female level=20", "eevee gender=male level=20");
		UUID[] pair = leavePair(helper, keeper, player);
		fillParty(player);
		removeOneFromParty(player);
		List<UUID> partyBefore = party(player).stream().map(Pokemon::getUuid).toList();
		PCStore pc = fillPc(Cobblemon.INSTANCE.getStorage().getPC(player));
		int pcBefore = pcCount(pc);
		ChoiceMenu menu = open(helper, player, keeper);
		seen.clear();
		click(player, menu, CobblemonDaycareKeeper.TAKE_BACK);
		helper.assertTrue(seen.contains("Your party and PC are full: make room for both before taking your pair back."), "told " + seen);
		List<UUID> partyAfter = party(player).stream().map(Pokemon::getUuid).toList();
		helper.assertTrue(partyAfter.equals(partyBefore), "the party changed: " + partyBefore.size() + " -> " + partyAfter.size());
		helper.assertTrue(!inParty(player, pair[0]) && !inParty(player, pair[1]) && !inBackup(pc, pair[0]) && !inBackup(pc, pair[1]),
			"one of the pair stayed placed");
		helper.assertValueEqual(pcCount(pc), pcBefore, "Pokémon in the PC");
		helper.assertValueEqual(DaycareKeepers.pairs(keeper).size(), 1, "pairs she holds");
		helper.succeed();
	}

	// ---- the rancher's Collect: refused before charging ----

	/**
	 * Party and PC full: Collect (through the screen) is refused, nothing is charged, the boarder stays with her, and
	 * the line names it. Then a party slot is freed and Collect works (and charges).
	 */
	//$ gametest_ticks_batch AREA '100' '"qaDaycareRoom1115cRancherFull"'
	@GameTest(template = AREA, timeoutTicks = 100, batch = "qaDaycareRoom1115cRancherFull")
	public void qaRanchersCollectWithPartyAndPcFullIsRefusedUncharged(GameTestHelper helper) {
		Villager rancher = rancher(helper);
		List<String> seen = new ArrayList<>();
		ServerPlayer player = player(helper, seen, 100_000, TWO_EEVEE_AND_ONE, "charmander level=5");
		Pokemon kid = party(player).get(1);
		UUID id = kid.getUuid();
		helper.assertTrue(CobblemonDaycare.leave(player, rancher, kid), "setup: not left");
		Daycare.Boarder b = Daycare.boarders(rancher).get(0);
		// Two days in her care: levels gained, so a price above one emerald
		Daycare.setBoarders(rancher, List.of(new Daycare.Boarder(b.owner(), b.ownerName(), b.pokemon(), b.since() - 2 * 24000)));
		fillParty(player);
		PCStore pc = fillPc(Cobblemon.INSTANCE.getStorage().getPC(player));
		ChoiceMenu menu = open(helper, player, rancher);
		long before = CobbleDollarsBank.balance(player);
		seen.clear();
		click(player, menu, CobblemonDaycare.FIRST_BOARDER_SLOT);
		helper.assertTrue(seen.contains("Your party and PC are full: make room before collecting Charmander"), "with party and PC full, told " + seen);
		helper.assertValueEqual(CobbleDollarsBank.balance(player), before, "balance after a refused Collect");
		helper.assertValueEqual(Daycare.boarders(rancher).size(), 1, "boarders after a refused Collect");
		helper.assertTrue(!inParty(player, id) && !inPc(pc, id) && !inBackup(pc, id), "the boarder left her anyway");
		// Room made: Collect works
		removeOneFromParty(player);
		seen.clear();
		click(player, menu, CobblemonDaycare.FIRST_BOARDER_SLOT);
		helper.assertTrue(inParty(player, id) && Daycare.boarders(rancher).isEmpty(), "Collect with a free party slot didn't bring it back; told " + seen);
		helper.assertTrue(CobbleDollarsBank.balance(player) < before, "Collect after making room charged nothing");
		helper.assertTrue(seen.stream().anyMatch(l -> l.startsWith("Charmander is back with you (level 5 → ")), "told " + seen);
		helper.succeed();
	}

	/** Party full and ONE free PC slot (the boundary of "room"): Collect is allowed, charged once, and the boarder goes to the PC. */
	//$ gametest_ticks_batch AREA '100' '"qaDaycareRoom1115cRancherOnePc"'
	@GameTest(template = AREA, timeoutTicks = 100, batch = "qaDaycareRoom1115cRancherOnePc")
	public void qaRanchersCollectWithOnePcSlotSendsTheBoarderToThePc(GameTestHelper helper) {
		Villager rancher = rancher(helper);
		List<String> seen = new ArrayList<>();
		ServerPlayer player = player(helper, seen, 100_000, TWO_EEVEE_AND_ONE, "charmander level=5");
		Pokemon kid = party(player).get(1);
		UUID id = kid.getUuid();
		helper.assertTrue(CobblemonDaycare.leave(player, rancher, kid), "setup: not left");
		fillParty(player);
		PCStore pc = pcWithOneFreeSlot(player);
		ChoiceMenu menu = open(helper, player, rancher);
		long before = CobbleDollarsBank.balance(player);
		seen.clear();
		click(player, menu, CobblemonDaycare.FIRST_BOARDER_SLOT);
		helper.assertTrue(inPc(pc, id) && !inParty(player, id) && !inBackup(pc, id), "not in the PC's last slot: PC " + inPc(pc, id) + ", overflow "
			+ inBackup(pc, id) + "; told " + seen);
		helper.assertValueEqual(before - CobbleDollarsBank.balance(player), (long) Money.DOLLARS_PER_EMERALD, "charged for a boarder that gained nothing");
		helper.assertTrue(Daycare.boarders(rancher).isEmpty() && seen.contains("Charmander is back with you (level 5 → 5)"), "told " + seen);
		helper.succeed();
	}

	// ---- the keeper's eggs: the room line, then room ----

	/**
	 * No room for what an egg gives (the inventory with Cobbreeding, the party and PC without): the line is the no-room
	 * one, read in full, the money is exactly what it was and the egg waits. Then room is made: one egg is collected and
	 * charged exactly once.
	 */
	//$ gametest_ticks_batch AREA '100' '"qaDaycareRoom1115cEggRoom"'
	@GameTest(template = AREA, timeoutTicks = 100, batch = "qaDaycareRoom1115cEggRoom")
	public void qaAnEggWithNoRoomSaysSoThenCollectsOnceRoomIsMade(GameTestHelper helper) {
		Villager keeper = keeper(helper);
		long price = eggPrice();
		List<String> seen = new ArrayList<>();
		ServerPlayer player = player(helper, seen, 3 * price, TWO_EEVEE_AND_ONE, "eevee gender=female level=20", "eevee gender=male level=20");
		leavePair(helper, keeper, player);
		DaycareKeepers.Pair p = DaycareKeepers.pairs(keeper).get(0);
		DaycareKeepers.setPairs(keeper, List.of(p.withEggs(1, p.day())));
		boolean eggs = CobblemonDaycareKeeper.cobbreeding();
		ChoiceMenu menu = open(helper, player, keeper);
		PCStore pc = Cobblemon.INSTANCE.getStorage().getPC(player);
		if (eggs) {
			fillInventory(player, 0);
		} else {
			fillParty(player);
			fillPc(pc);
		}
		seen.clear();
		click(player, menu, CobblemonDaycareKeeper.EGGS);
		String expected = eggs ? "Your inventory is full: make room for the egg first." : "Your party and PC are full: make room for the hatchling first.";
		helper.assertTrue(seen.contains(expected), "with no room (Cobbreeding " + eggs + "), told " + seen);
		helper.assertValueEqual(CobbleDollarsBank.balance(player), 3 * price, "balance after a no-room refusal");
		helper.assertValueEqual(DaycareKeepers.pairs(keeper).get(0).eggs(), 1, "eggs waiting after a no-room refusal");
		// Room made
		if (eggs) {
			player.getInventory().items.set(0, ItemStack.EMPTY);
		} else {
			removeOneFromParty(player);
		}
		seen.clear();
		click(player, menu, CobblemonDaycareKeeper.EGGS);
		helper.assertValueEqual(CobbleDollarsBank.balance(player), 2 * price, "balance after collecting one egg");
		helper.assertValueEqual(DaycareKeepers.pairs(keeper).get(0).eggs(), 0, "eggs waiting after collecting");
		helper.assertTrue(seen.stream().anyMatch(l -> l.startsWith("You collected ")), "told " + seen);
		if (eggs) {
			helper.assertValueEqual(eggItemsAround(helper, player), 1L, "egg items after collecting");
		}
		helper.succeed();
	}

	/**
	 * No money AND no room: the money ran out first, so the price line (the bug text: "the price line only when the
	 * money ran out"); nothing charged, the egg waits.
	 */
	//$ gametest_ticks_batch AREA '100' '"qaDaycareRoom1115cNoMoneyNoRoom"'
	@GameTest(template = AREA, timeoutTicks = 100, batch = "qaDaycareRoom1115cNoMoneyNoRoom")
	public void qaAnEggWithNoMoneyAndNoRoomSaysThePrice(GameTestHelper helper) {
		Villager keeper = keeper(helper);
		long price = eggPrice();
		List<String> seen = new ArrayList<>();
		ServerPlayer player = player(helper, seen, price - 1, TWO_EEVEE_AND_ONE, "eevee gender=female level=20", "eevee gender=male level=20");
		leavePair(helper, keeper, player);
		DaycareKeepers.Pair p = DaycareKeepers.pairs(keeper).get(0);
		DaycareKeepers.setPairs(keeper, List.of(p.withEggs(2, p.day())));
		ChoiceMenu menu = open(helper, player, keeper);
		fillInventory(player, 0);
		fillParty(player);
		fillPc(Cobblemon.INSTANCE.getStorage().getPC(player));
		seen.clear();
		click(player, menu, CobblemonDaycareKeeper.EGGS);
		String priceLine = "An egg costs " + Money.describe(price, DaycareKeepers.EGG_PRICE).getString() + ".";
		helper.assertTrue(seen.contains(priceLine), "with " + (price - 1) + " of " + price + " and no room, told " + seen + " (expected '" + priceLine + "')");
		helper.assertValueEqual(CobbleDollarsBank.balance(player), price - 1, "balance");
		helper.assertValueEqual(DaycareKeepers.pairs(keeper).get(0).eggs(), 2, "eggs waiting");
		helper.succeed();
	}

	/**
	 * With Cobbreeding, an egg is an item: a full party AND a full PC must not stop it (the fix's room checks are for
	 * Pokémon, not eggs). The egg is given and charged.
	 */
	//$ gametest_ticks_batch AREA '100' '"qaDaycareRoom1115cEggFullPc"'
	@GameTest(template = AREA, timeoutTicks = 100, batch = "qaDaycareRoom1115cEggFullPc")
	public void qaACobbreedingEggIgnoresAFullPartyAndPc(GameTestHelper helper) {
		Villager keeper = keeper(helper);
		if (!CobblemonDaycareKeeper.cobbreeding()) {
			helper.succeed();
			return;
		}
		long price = eggPrice();
		List<String> seen = new ArrayList<>();
		ServerPlayer player = player(helper, seen, 3 * price, TWO_EEVEE_AND_ONE, "eevee gender=female level=20", "eevee gender=male level=20");
		leavePair(helper, keeper, player);
		DaycareKeepers.Pair p = DaycareKeepers.pairs(keeper).get(0);
		DaycareKeepers.setPairs(keeper, List.of(p.withEggs(1, p.day())));
		ChoiceMenu menu = open(helper, player, keeper);
		fillParty(player);
		fillPc(Cobblemon.INSTANCE.getStorage().getPC(player));
		seen.clear();
		click(player, menu, CobblemonDaycareKeeper.EGGS);
		helper.assertValueEqual(eggItemsAround(helper, player), 1L, "egg items with a full party and PC; told " + seen);
		helper.assertValueEqual(CobbleDollarsBank.balance(player), 2 * price, "balance");
		helper.assertTrue(seen.contains("You collected an Eevee Egg.") || seen.stream().anyMatch(l -> l.startsWith("You collected ")), "told " + seen);
		helper.succeed();
	}

	// ---- the hatchling (no Cobbreeding): nowhere, then somewhere ----

	/**
	 * Without Cobbreeding, party and PC full: no hatchling is made anywhere (no orphan in the overflow, nothing reported),
	 * so the caller can refund. Then one PC slot is freed: the next hatchling lands there. (giveEgg directly: this run
	 * has Cobbreeding, so the screen takes the egg path.)
	 */
	//$ gametest_ticks_batch AREA '100' '"qaDaycareRoom1115cHatchRoom"'
	@GameTest(template = AREA, timeoutTicks = 100, batch = "qaDaycareRoom1115cHatchRoom")
	public void qaAHatchlingWithNoRoomIsNotMadeThenLandsInTheFreedSlot(GameTestHelper helper) {
		PartnerShowsCompatTests.clearLeftovers(helper);
		List<String> seen = new ArrayList<>();
		ServerPlayer player = player(helper, seen, 0, "rattata level=3");
		fillParty(player);
		PCStore pc = fillPc(Cobblemon.INSTANCE.getStorage().getPC(player));
		int pcBefore = pcCount(pc);
		Pokemon mother = make("eevee gender=female level=20");
		Pokemon father = make("eevee gender=male level=20");
		Component none = CobblemonDaycareKeeper.giveEgg(player, mother, father, RandomSource.create(3), false);
		helper.assertTrue(none == null, "with party and PC full, the hatchling was reported: " + (none == null ? "" : none.getString()));
		helper.assertTrue(pcCount(pc) == pcBefore && party(player).size() == 6 && pc.getBackupStore().getPokemon().isEmpty(),
			"a hatchling was put somewhere: PC " + pcCount(pc) + "/" + pcBefore + ", overflow " + pc.getBackupStore().getPokemon().size());
		pc.remove(pc.iterator().next());
		Component born = CobblemonDaycareKeeper.giveEgg(player, mother, father, RandomSource.create(3), false);
		helper.assertTrue(born != null && born.getString().equals("Eevee, just hatched"), "with one PC slot, the hatchling reads " + born);
		int eevees = 0;
		for (Pokemon q : pc) {
			if (q.getSpecies().getName().equals("Eevee") && q.getLevel() == 1) {
				eevees++;
			}
		}
		helper.assertValueEqual(eevees, 1, "hatchlings in the PC");
		helper.succeed();
	}

	// ---- the new sentences ----

	/** Every new line, rendered with its real arguments, exactly as the bug texts ask (American English, the reason named). */
	//$ gametest_ticks_batch AREA '20' '"qaDaycareRoom1115cLines"'
	@GameTest(template = AREA, timeoutTicks = 20, batch = "qaDaycareRoom1115cLines")
	public void qaTheNewDaycareLinesReadRight(GameTestHelper helper) {
		Pokemon charmander = make("charmander level=5");
		String[][] lines = {
			{text("message.aliveworkplace.daycare.no_room", charmander.getDisplayName(false)), "Your party and PC are full: make room before collecting Charmander"},
			{text("message.aliveworkplace.daycare_keeper.no_room_pair"), "Your party and PC are full: make room for both before taking your pair back."},
			{text("message.aliveworkplace.daycare_keeper.no_room_egg"), "Your inventory is full: make room for the egg first."},
			{text("message.aliveworkplace.daycare_keeper.no_room_hatchling"), "Your party and PC are full: make room for the hatchling first."},
		};
		for (String[] l : lines) {
			helper.assertTrue(l[0].equals(l[1]), "reads '" + l[0] + "', expected '" + l[1] + "'");
			helper.assertTrue(!l[0].contains("%") && !l[0].contains("aliveworkplace") && !l[0].toLowerCase().contains("colour"), "raw text: " + l[0]);
		}
		helper.succeed();
	}
}
