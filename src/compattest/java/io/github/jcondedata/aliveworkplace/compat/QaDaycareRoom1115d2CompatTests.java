package io.github.jcondedata.aliveworkplace.compat;

import static io.github.jcondedata.aliveworkplace.compat.QaDaycareFallbacks1115dCompatTests.*;

import com.cobblemon.mod.common.Cobblemon;
import com.cobblemon.mod.common.api.Priority;
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
import java.util.function.Consumer;
import kotlin.Unit;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.npc.Villager;

/**
 * QA (lane D, 2026-11-15, second pass): B1284 and B1285's fix, from its promise. Take back places both Pokémon or
 * neither (a PC with room for just one, or one party slot and a full PC: she keeps the pair and says to make room for
 * both); the rancher's Collect is refused before charging when party and PC are full, then works and charges once; a
 * hatchling with nowhere to go isn't put anywhere (so the refunded egg is the only outcome), then hatches once room is
 * made; a keeper's or rancher's death with a full PC puts the Pokémon in Cobblemon's PC overflow (both of the pair, for
 * an online or an offline trainer, saved with the PC); the egg's price line only when the money ran out, by one dollar
 * either side of the price. Every new line is checked word for word against the spec's text.
 */
public class QaDaycareRoom1115d2CompatTests implements FabricGameTest {
	private static final String AREA = CompatGameTests.AREA;

	static final String NO_ROOM_PAIR = "Your party and PC are full: make room for both before taking your pair back.";
	static final String NO_ROOM_EGG = "Your inventory is full: make room for the egg first.";
	static final String NO_ROOM_HATCHLING = "Your party and PC are full: make room for the hatchling first.";

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

	/** Frees {@code slots} places in a full PC (removing filler Magikarp). */
	static void freePc(PCStore pc, int slots) {
		List<Pokemon> out = new ArrayList<>();
		for (Pokemon p : pc) {
			if (out.size() < slots && p.getSpecies().getName().equals("Magikarp")) {
				out.add(p);
			}
		}
		out.forEach(pc::remove);
	}

	static int eevees(Iterable<Pokemon> store) {
		int n = 0;
		for (Pokemon p : store) {
			if (p.getSpecies().getName().equals("Eevee") && p.getLevel() == 1) {
				n++;
			}
		}
		return n;
	}

	// ---- Take back: both or neither ----

	/**
	 * Party full and a PC with room for exactly one: Take back (through the screen) is refused, both stay with her, the
	 * PC is as it was (the first wasn't left in it), and the player reads the no-room line. Once a second place is free,
	 * Take back brings the same two Pokémon back.
	 */
	//$ gametest_ticks_batch AREA '100' '"qaDaycareRoom1115d2PcOne"'
	@GameTest(template = AREA, timeoutTicks = 100, batch = "qaDaycareRoom1115d2PcOne")
	public void qaTakeBackWithRoomForOnlyOneInThePcKeepsBoth(GameTestHelper helper) {
		Villager keeper = keeper(helper);
		List<String> seen = new ArrayList<>();
		ServerPlayer player = player(helper, seen, 0, TWO_EEVEE_AND_ONE, "eevee gender=female level=20", "eevee gender=male level=20");
		UUID[] pair = leavePair(helper, keeper, player);
		fillParty(player);
		PCStore pc = QaDaycareFallbacks1115dBugCompatTests.fillPc(Cobblemon.INSTANCE.getStorage().getPC(player));
		freePc(pc, 1);
		int before = pcCount(pc);
		helper.assertTrue(pc.getFirstAvailablePosition() != null, "setup: the PC has no free place");
		ChoiceMenu menu = open(helper, player, keeper);
		seen.clear();
		click(player, menu, CobblemonDaycareKeeper.TAKE_BACK);
		helper.assertValueEqual(DaycareKeepers.pairs(keeper).size(), 1, "pairs she holds after a refused Take back");
		for (UUID id : pair) {
			helper.assertTrue(!inPc(pc, id) && !inParty(player, id) && !inBackup(pc, id), "one of the pair left her anyway: " + id + " (PC "
				+ inPc(pc, id) + ", party " + inParty(player, id) + ", overflow " + inBackup(pc, id) + ")");
		}
		helper.assertValueEqual(pcCount(pc), before, "Pokémon in the PC after a refused Take back");
		helper.assertTrue(pc.getFirstAvailablePosition() != null, "the PC's one free place was taken");
		helper.assertValueEqual(party(player).size(), 6, "party size");
		helper.assertTrue(Component.translatable("message.aliveworkplace.daycare_keeper.no_room_pair").getString().equals(NO_ROOM_PAIR),
			"the no-room line reads: " + Component.translatable("message.aliveworkplace.daycare_keeper.no_room_pair").getString());
		helper.assertTrue(seen.contains(NO_ROOM_PAIR) && !seen.contains("You took your pair back."), "the player was told: " + seen);
		// Room for both now: the same pair comes back
		freePc(pc, 1);
		seen.clear();
		click(player, menu, CobblemonDaycareKeeper.TAKE_BACK);
		helper.assertTrue(inPc(pc, pair[0]) && inPc(pc, pair[1]), "with two places free, not both in the PC (mother " + inPc(pc, pair[0]) + ", father "
			+ inPc(pc, pair[1]) + "); told " + seen);
		helper.assertTrue(DaycareKeepers.pairs(keeper).isEmpty() && seen.contains("You took your pair back."), "pairs " + DaycareKeepers.pairs(keeper).size()
			+ ", told " + seen);
		helper.succeed();
	}

	/**
	 * One free party slot and a full PC: refused, the first of the pair isn't left in the party, both stay with her and
	 * the party is the five it was.
	 */
	//$ gametest_ticks_batch AREA '100' '"qaDaycareRoom1115d2PartyOne"'
	@GameTest(template = AREA, timeoutTicks = 100, batch = "qaDaycareRoom1115d2PartyOne")
	public void qaTakeBackWithOnePartySlotAndAFullPcKeepsBoth(GameTestHelper helper) {
		Villager keeper = keeper(helper);
		List<String> seen = new ArrayList<>();
		ServerPlayer player = player(helper, seen, 0, TWO_EEVEE_AND_ONE, "eevee gender=female level=20", "eevee gender=male level=20");
		UUID[] pair = leavePair(helper, keeper, player);
		fillParty(player);
		Cobblemon.INSTANCE.getStorage().getParty(player).remove(party(player).get(5));
		List<UUID> five = party(player).stream().map(Pokemon::getUuid).toList();
		helper.assertValueEqual(five.size(), 5, "setup: party size");
		PCStore pc = QaDaycareFallbacks1115dBugCompatTests.fillPc(Cobblemon.INSTANCE.getStorage().getPC(player));
		int before = pcCount(pc);
		ChoiceMenu menu = open(helper, player, keeper);
		seen.clear();
		click(player, menu, CobblemonDaycareKeeper.TAKE_BACK);
		helper.assertValueEqual(DaycareKeepers.pairs(keeper).size(), 1, "pairs she holds after a refused Take back");
		for (UUID id : pair) {
			helper.assertTrue(!inParty(player, id) && !inPc(pc, id) && !inBackup(pc, id), "one of the pair left her anyway: " + id + " (party "
				+ inParty(player, id) + ", PC " + inPc(pc, id) + ", overflow " + inBackup(pc, id) + ")");
		}
		helper.assertTrue(party(player).stream().map(Pokemon::getUuid).toList().equals(five), "the party changed: " + party(player).size());
		helper.assertValueEqual(pcCount(pc), before, "Pokémon in the PC");
		helper.assertTrue(seen.contains(NO_ROOM_PAIR) && !seen.contains("You took your pair back."), "the player was told: " + seen);
		helper.succeed();
	}

	// ---- the rancher's Collect ----

	/**
	 * Party and PC full: Collect (the boarder's row on the screen) is refused before any charge, the boarder stays and the
	 * line names it. With one PC place free, Collect brings it to the PC (the party is full) and charges once.
	 */
	//$ gametest_ticks_batch AREA '100' '"qaDaycareRoom1115d2Rancher"'
	@GameTest(template = AREA, timeoutTicks = 100, batch = "qaDaycareRoom1115d2Rancher")
	public void qaRanchersCollectWithPartyAndPcFullIsRefusedBeforeCharging(GameTestHelper helper) {
		Villager rancher = rancher(helper);
		List<String> seen = new ArrayList<>();
		long start = 10L * Money.DOLLARS_PER_EMERALD;
		ServerPlayer player = player(helper, seen, start, TWO_EEVEE_AND_ONE, "charmander level=5");
		Pokemon kid = party(player).get(1);
		UUID id = kid.getUuid();
		helper.assertTrue(CobblemonDaycare.leave(player, rancher, kid), "setup: not left");
		fillParty(player);
		PCStore pc = QaDaycareFallbacks1115dBugCompatTests.fillPc(Cobblemon.INSTANCE.getStorage().getPC(player));
		int before = pcCount(pc);
		ChoiceMenu menu = open(helper, player, rancher);
		seen.clear();
		click(player, menu, CobblemonDaycare.FIRST_BOARDER_SLOT);
		helper.assertValueEqual(CobbleDollarsBank.balance(player), start, "balance after a refused Collect");
		helper.assertValueEqual(Daycare.boarders(rancher).size(), 1, "boarders after a refused Collect");
		helper.assertTrue(!inParty(player, id) && !inPc(pc, id) && !inBackup(pc, id) && pcCount(pc) == before, "the boarder left the rancher anyway");
		String line = "Your party and PC are full: make room before collecting Charmander";
		helper.assertTrue(seen.contains(line), "the player was told " + seen + ", not '" + line + "'");
		// One place free: Collect works, to the PC, for one price
		freePc(pc, 1);
		seen.clear();
		click(player, menu, CobblemonDaycare.FIRST_BOARDER_SLOT);
		helper.assertTrue(inPc(pc, id) && !inParty(player, id) && !inBackup(pc, id), "after making room the boarder is: PC " + inPc(pc, id) + ", party "
			+ inParty(player, id) + ", overflow " + inBackup(pc, id) + "; told " + seen);
		helper.assertTrue(Daycare.boarders(rancher).isEmpty(), "she still boards it");
		helper.assertValueEqual(CobbleDollarsBank.balance(player), start - Money.DOLLARS_PER_EMERALD, "balance after one Collect (one emerald's worth, no levels gained)");
		helper.assertTrue(seen.stream().anyMatch(l -> l.startsWith("Charmander is back with you")), "the player was told: " + seen);
		// A second click on the now-empty row charges nothing more
		click(player, menu, CobblemonDaycare.FIRST_BOARDER_SLOT);
		helper.assertValueEqual(CobbleDollarsBank.balance(player), start - Money.DOLLARS_PER_EMERALD, "balance after clicking the empty row");
		helper.succeed();
	}

	// ---- the hatchling (without Cobbreeding) ----

	/**
	 * Party and PC full: giving the hatchling fails (so collect refunds the egg) and leaves no Eevee anywhere, not even in
	 * the overflow (that would be an egg refunded and a hatchling given). Once a PC place is free it hatches into the PC,
	 * exactly one. The refund itself goes through collect only without Cobbreeding, which the compat pack has; the line
	 * is checked word for word.
	 */
	//$ gametest_ticks_batch AREA '100' '"qaDaycareRoom1115d2Hatchling"'
	@GameTest(template = AREA, timeoutTicks = 100, batch = "qaDaycareRoom1115d2Hatchling")
	public void qaAHatchlingWithNowhereToGoIsNotGivenThenHatchesOnceThereIsRoom(GameTestHelper helper) {
		PartnerShowsCompatTests.clearLeftovers(helper);
		List<String> seen = new ArrayList<>();
		ServerPlayer player = player(helper, seen, 0, "rattata level=3");
		fillParty(player);
		PCStore pc = QaDaycareFallbacks1115dBugCompatTests.fillPc(Cobblemon.INSTANCE.getStorage().getPC(player));
		Pokemon mother = make("eevee gender=female level=20");
		Pokemon father = make("eevee gender=male level=20");
		Component first = CobblemonDaycareKeeper.giveEgg(player, mother, father, RandomSource.create(3), false);
		helper.assertTrue(first == null, "with party and PC full the hatchling was given: " + (first == null ? "" : first.getString()));
		int anywhere = eevees(party(player)) + eevees(pc) + eevees(pc.getBackupStore());
		helper.assertValueEqual(anywhere, 0, "level 1 Eevee in the party, PC and overflow after a refused hatch");
		String line = Component.translatable("message.aliveworkplace.daycare_keeper.no_room_hatchling").getString();
		helper.assertTrue(line.equals(NO_ROOM_HATCHLING), "the hatchling's no-room line reads: " + line);
		freePc(pc, 1);
		Component second = CobblemonDaycareKeeper.giveEgg(player, mother, father, RandomSource.create(3), false);
		helper.assertTrue(second != null && second.getString().equals("Eevee, just hatched"), "after making room the hatchling reads: " + second);
		helper.assertValueEqual(eevees(pc), 1, "hatchlings in the PC");
		helper.assertValueEqual(eevees(party(player)) + eevees(pc.getBackupStore()), 0, "hatchlings in the party or overflow");
		helper.succeed();
	}

	// ---- a death with a full PC: Cobblemon's overflow ----

	/**
	 * The keeper dies while the online trainer's party and PC are full: both of the pair are in the PC's overflow (two,
	 * not in the boxes or the party) and the PC is marked changed (so it's saved). Whether the overflow survives a save
	 * and load is QaDaycareRoom1115d2BugCompatTests's.
	 */
	//$ gametest_ticks_batch AREA '100' '"qaDaycareRoom1115d2DeathOnline"'
	@GameTest(template = AREA, timeoutTicks = 100, batch = "qaDaycareRoom1115d2DeathOnline")
	public void qaKeepersDeathWithAFullPcPutsThePairInTheOverflow(GameTestHelper helper) {
		Villager keeper = keeper(helper);
		List<String> seen = new ArrayList<>();
		ServerPlayer player = player(helper, seen, 0, TWO_EEVEE_AND_ONE, "eevee gender=female level=20", "eevee gender=male level=20");
		UUID[] pair = leavePair(helper, keeper, player);
		fillParty(player);
		PCStore pc = QaDaycareFallbacks1115dBugCompatTests.fillPc(Cobblemon.INSTANCE.getStorage().getPC(player));
		int before = pc.getBackupStore().getPokemon().size();
		int[] changed = {0};
		var subscription = pc.getPcChangeObservable().subscribe(Priority.NORMAL, (Consumer<Unit>) u -> changed[0]++);
		keeper.kill();
		helper.runAfterDelay(2, () -> {
			pc.getPcChangeObservable().unsubscribe(subscription);
			helper.assertTrue(inBackup(pc, pair[0]) && inBackup(pc, pair[1]), "not both in the overflow (mother " + inBackup(pc, pair[0]) + ", father "
				+ inBackup(pc, pair[1]) + ")");
			helper.assertValueEqual(pc.getBackupStore().getPokemon().size() - before, 2, "Pokémon added to the overflow");
			helper.assertTrue(!inPc(pc, pair[0]) && !inPc(pc, pair[1]) && !inParty(player, pair[0]) && !inParty(player, pair[1]), "one is also elsewhere");
			helper.assertTrue(DaycareKeepers.pairs(keeper).isEmpty(), "she still holds them");
			helper.assertTrue(changed[0] > 0, "the PC wasn't marked changed, so the overflow may not be saved");
			helper.succeed();
		});
	}

	/** The keeper dies while an offline trainer's PC is full: both of the pair go to that trainer's overflow, two of them. */
	//$ gametest_ticks_batch AREA '100' '"qaDaycareRoom1115d2DeathOffline"'
	@GameTest(template = AREA, timeoutTicks = 100, batch = "qaDaycareRoom1115d2DeathOffline")
	public void qaKeepersDeathWithAnOfflineTrainersFullPcPutsThePairInTheOverflow(GameTestHelper helper) {
		Villager keeper = keeper(helper);
		var access = helper.getLevel().registryAccess();
		UUID owner = UUID.randomUUID();
		PCStore pc = QaDaycareFallbacks1115dBugCompatTests.fillPc(Cobblemon.INSTANCE.getStorage().getPC(owner, access));
		int full = pcCount(pc);
		Pokemon mother = make("eevee gender=female level=20");
		Pokemon father = make("eevee gender=male level=20");
		DaycareKeepers.setPairs(keeper, List.of(new DaycareKeepers.Pair(owner, "away", mother.saveToNBT(access, new CompoundTag()),
			father.saveToNBT(access, new CompoundTag()), 0, DaycareKeepers.day(helper.getLevel()))));
		keeper.kill();
		helper.runAfterDelay(2, () -> {
			PCStore now = Cobblemon.INSTANCE.getStorage().getPC(owner, access);
			helper.assertTrue(inBackup(now, mother.getUuid()) && inBackup(now, father.getUuid()), "not both in the offline trainer's overflow (mother "
				+ inBackup(now, mother.getUuid()) + ", father " + inBackup(now, father.getUuid()) + ")");
			long ours = now.getBackupStore().getPokemon().stream().filter(p -> p.getUuid().equals(mother.getUuid()) || p.getUuid().equals(father.getUuid())).count();
			helper.assertValueEqual(ours, 2L, "the pair's entries in the overflow");
			helper.assertValueEqual(pcCount(now), full, "Pokémon in the full PC's boxes");
			helper.assertTrue(DaycareKeepers.pairs(keeper).isEmpty(), "she still holds them");
			helper.succeed();
		});
	}

	/** The rancher dies while the trainer's PC is full: the boarder is in the overflow, once. */
	//$ gametest_ticks_batch AREA '100' '"qaDaycareRoom1115d2DeathRancher"'
	@GameTest(template = AREA, timeoutTicks = 100, batch = "qaDaycareRoom1115d2DeathRancher")
	public void qaRanchersDeathWithAFullPcPutsTheBoarderInTheOverflow(GameTestHelper helper) {
		Villager rancher = rancher(helper);
		List<String> seen = new ArrayList<>();
		ServerPlayer player = player(helper, seen, 0, TWO_EEVEE_AND_ONE, "charmander level=5");
		Pokemon kid = party(player).get(1);
		UUID id = kid.getUuid();
		helper.assertTrue(CobblemonDaycare.leave(player, rancher, kid), "setup: not left");
		fillParty(player);
		PCStore pc = QaDaycareFallbacks1115dBugCompatTests.fillPc(Cobblemon.INSTANCE.getStorage().getPC(player));
		rancher.kill();
		helper.runAfterDelay(2, () -> {
			long n = pc.getBackupStore().getPokemon().stream().filter(p -> p.getUuid().equals(id)).count();
			helper.assertValueEqual(n, 1L, "the boarder's entries in the overflow");
			helper.assertTrue(!inPc(pc, id) && !inParty(player, id), "the boarder is also elsewhere");
			helper.assertTrue(Daycare.boarders(rancher).isEmpty(), "she still boards it");
			helper.succeed();
		});
	}

	// ---- B1285: the egg's lines, by the price's boundary ----

	/**
	 * Room in the inventory but one dollar short of an egg: no egg, nothing charged, the egg still waits, and the line is
	 * the price. With exactly the price: the egg is given and the balance is 0.
	 */
	//$ gametest_ticks_batch AREA '100' '"qaDaycareRoom1115d2Price"'
	@GameTest(template = AREA, timeoutTicks = 100, batch = "qaDaycareRoom1115d2Price")
	public void qaAnEggOneDollarShortIsThePriceLineAndExactlyThePriceBuysIt(GameTestHelper helper) {
		Villager keeper = keeper(helper);
		long price = (long) DaycareKeepers.EGG_PRICE * Money.DOLLARS_PER_EMERALD;
		List<String> seen = new ArrayList<>();
		ServerPlayer player = player(helper, seen, price - 1, TWO_EEVEE_AND_ONE, "eevee gender=female level=20", "eevee gender=male level=20");
		leavePair(helper, keeper, player);
		DaycareKeepers.Pair p = DaycareKeepers.pairs(keeper).get(0);
		DaycareKeepers.setPairs(keeper, List.of(p.withEggs(1, p.day())));
		ChoiceMenu menu = open(helper, player, keeper);
		fillInventory(player, 5);
		seen.clear();
		click(player, menu, CobblemonDaycareKeeper.EGGS);
		String priceLine = "An egg costs " + Money.describe(price, DaycareKeepers.EGG_PRICE).getString() + ".";
		helper.assertTrue(eggItemsAround(helper, player) == 0 && CobbleDollarsBank.balance(player) == price - 1 && DaycareKeepers.pairs(keeper).get(0).eggs() == 1,
			"one dollar short: " + eggItemsAround(helper, player) + " eggs, balance " + CobbleDollarsBank.balance(player) + ", waiting "
				+ DaycareKeepers.pairs(keeper).get(0).eggs());
		helper.assertTrue(seen.contains(priceLine) && !seen.contains(NO_ROOM_EGG), "one dollar short, told " + seen + ", not '" + priceLine + "'");
		// Exactly the price
		CobbleDollarsBank.add(player, 1);
		seen.clear();
		click(player, menu, CobblemonDaycareKeeper.EGGS);
		helper.assertTrue(eggItemsAround(helper, player) == 1 && CobbleDollarsBank.balance(player) == 0 && DaycareKeepers.pairs(keeper).get(0).eggs() == 0,
			"exactly the price: " + eggItemsAround(helper, player) + " eggs, balance " + CobbleDollarsBank.balance(player) + ", waiting "
				+ DaycareKeepers.pairs(keeper).get(0).eggs() + "; told " + seen);
		helper.assertTrue(seen.stream().anyMatch(l -> l.startsWith("You collected ") && l.contains("Eevee Egg")) && !seen.contains(priceLine),
			"the player was told: " + seen);
		helper.succeed();
	}

	/**
	 * B1285's own line: a full inventory with the money for it says to make room, word for word, and charges nothing;
	 * a full inventory and one dollar short is the price line (the money ran out first).
	 */
	//$ gametest_ticks_batch AREA '100' '"qaDaycareRoom1115d2NoRoomEgg"'
	@GameTest(template = AREA, timeoutTicks = 100, batch = "qaDaycareRoom1115d2NoRoomEgg")
	public void qaAnEggWithAFullInventorySaysToMakeRoom(GameTestHelper helper) {
		Villager keeper = keeper(helper);
		long price = (long) DaycareKeepers.EGG_PRICE * Money.DOLLARS_PER_EMERALD;
		List<String> seen = new ArrayList<>();
		ServerPlayer player = player(helper, seen, price, TWO_EEVEE_AND_ONE, "eevee gender=female level=20", "eevee gender=male level=20");
		leavePair(helper, keeper, player);
		DaycareKeepers.Pair p = DaycareKeepers.pairs(keeper).get(0);
		DaycareKeepers.setPairs(keeper, List.of(p.withEggs(1, p.day())));
		ChoiceMenu menu = open(helper, player, keeper);
		fillInventory(player, 0);
		seen.clear();
		click(player, menu, CobblemonDaycareKeeper.EGGS);
		helper.assertTrue(Component.translatable("message.aliveworkplace.daycare_keeper.no_room_egg").getString().equals(NO_ROOM_EGG), "the line reads: "
			+ Component.translatable("message.aliveworkplace.daycare_keeper.no_room_egg").getString());
		helper.assertTrue(seen.contains(NO_ROOM_EGG), "a full inventory with the money, told " + seen + ", not '" + NO_ROOM_EGG + "'");
		helper.assertTrue(CobbleDollarsBank.balance(player) == price && DaycareKeepers.pairs(keeper).get(0).eggs() == 1 && eggItemsAround(helper, player) == 0,
			"charged or given: balance " + CobbleDollarsBank.balance(player) + ", waiting " + DaycareKeepers.pairs(keeper).get(0).eggs());
		// Full inventory and one dollar short: the money is the reason
		CobbleDollarsBank.add(player, -1);
		seen.clear();
		click(player, menu, CobblemonDaycareKeeper.EGGS);
		String priceLine = "An egg costs " + Money.describe(price, DaycareKeepers.EGG_PRICE).getString() + ".";
		helper.assertTrue(seen.contains(priceLine) && !seen.contains(NO_ROOM_EGG), "full and one dollar short, told " + seen);
		helper.assertValueEqual(CobbleDollarsBank.balance(player), price - 1, "balance");
		helper.succeed();
	}

	/**
	 * The rancher dies: each boarder goes back with what it gained while boarded (her own comment: "with what it
	 * gained"), the boundary both sides: 19 ticks is under a second and gains nothing, 20 ticks gains, a long stay
	 * gains in full. Three offline trainers, so each lands in its own PC.
	 */
	//$ gametest_ticks_batch AREA '100' '"qaDaycareRoom1115d2DeathGains"'
	@GameTest(template = AREA, timeoutTicks = 100, batch = "qaDaycareRoom1115d2DeathGains")
	public void qaRanchersDeathReturnsEachBoarderWithWhatItGained(GameTestHelper helper) {
		Villager rancher = rancher(helper);
		var access = helper.getLevel().registryAccess();
		long now = helper.getLevel().getGameTime();
		long[] stays = {19, 20, 40_000};
		List<UUID> owners = new ArrayList<>();
		List<Pokemon> kids = new ArrayList<>();
		List<Daycare.Boarder> boarders = new ArrayList<>();
		for (long stay : stays) {
			UUID owner = UUID.randomUUID();
			Pokemon kid = make("charmander level=5");
			owners.add(owner);
			kids.add(kid);
			boarders.add(new Daycare.Boarder(owner, "away", kid.saveToNBT(access, new CompoundTag()), now - stay));
		}
		Daycare.setBoarders(rancher, boarders);
		int[] before = new int[stays.length];
		for (int i = 0; i < stays.length; i++) {
			before[i] = kids.get(i).getExperience();
		}
		int[] expected = new int[stays.length];
		for (int i = 0; i < stays.length; i++) {
			expected[i] = Daycare.experience(rancher, stays[i]);
		}
		helper.assertTrue(expected[0] == 0 && expected[1] > 0 && expected[2] > expected[1], "setup: gains " + java.util.Arrays.toString(expected));
		rancher.kill();
		helper.runAfterDelay(2, () -> {
			for (int i = 0; i < stays.length; i++) {
				PCStore pc = Cobblemon.INSTANCE.getStorage().getPC(owners.get(i), access);
				Pokemon back = null;
				for (Pokemon p : pc) {
					if (p.getUuid().equals(kids.get(i).getUuid())) {
						back = p;
					}
				}
				helper.assertTrue(back != null, "boarded " + stays[i] + " ticks: not in its trainer's PC");
				int gained = back.getExperience() - before[i];
				helper.assertTrue(gained == expected[i], "boarded " + stays[i] + " ticks: came back with "
					+ gained + " experience gained, expected " + expected[i] + " (level " + back.getLevel() + ")");
			}
			helper.succeed();
		});
	}
}
