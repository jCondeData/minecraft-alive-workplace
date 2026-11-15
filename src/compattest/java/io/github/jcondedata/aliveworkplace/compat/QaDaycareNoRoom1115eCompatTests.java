package io.github.jcondedata.aliveworkplace.compat;

import static io.github.jcondedata.aliveworkplace.compat.QaDaycareFallbacks1115dBugCompatTests.anywhere;
import static io.github.jcondedata.aliveworkplace.compat.QaDaycareFallbacks1115dBugCompatTests.fillPc;
import static io.github.jcondedata.aliveworkplace.compat.QaDaycareFallbacks1115dCompatTests.TWO_EEVEE_AND_ONE;
import static io.github.jcondedata.aliveworkplace.compat.QaDaycareFallbacks1115dCompatTests.click;
import static io.github.jcondedata.aliveworkplace.compat.QaDaycareFallbacks1115dCompatTests.fillInventory;
import static io.github.jcondedata.aliveworkplace.compat.QaDaycareFallbacks1115dCompatTests.fillParty;
import static io.github.jcondedata.aliveworkplace.compat.QaDaycareFallbacks1115dCompatTests.inParty;
import static io.github.jcondedata.aliveworkplace.compat.QaDaycareFallbacks1115dCompatTests.inPc;
import static io.github.jcondedata.aliveworkplace.compat.QaDaycareFallbacks1115dCompatTests.keeper;
import static io.github.jcondedata.aliveworkplace.compat.QaDaycareFallbacks1115dCompatTests.leavePair;
import static io.github.jcondedata.aliveworkplace.compat.QaDaycareFallbacks1115dCompatTests.open;
import static io.github.jcondedata.aliveworkplace.compat.QaDaycareFallbacks1115dCompatTests.party;
import static io.github.jcondedata.aliveworkplace.compat.QaDaycareFallbacks1115dCompatTests.player;
import static io.github.jcondedata.aliveworkplace.compat.QaDaycareFallbacks1115dCompatTests.rancher;

import com.cobblemon.mod.common.Cobblemon;
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
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.npc.Villager;

/**
 * QA (lane E, 2026-11-15; sentences and saves): B1284/B1285's new lines, read exactly as a player reads them, and the
 * edges around them, from the specs (28.12: "to the party or the PC", "if she dies, the pairs go to their trainers' PCs";
 * the fix: "Take back now places both or neither", "Collect is refused before charging", a death with the PC full goes
 * to Cobblemon's overflow store, "saved with the PC").
 * <ul>
 * <li>The rancher's Collect with party and PC full, through the screen: "Your party and PC are full: make room before
 * collecting Charmander", nothing charged, the boarder kept; one place freed, the same button brings it back.</li>
 * <li>Take back with room for only one of the pair: the refusal line, the pair still with her and no copy left in the
 * PC (the one place still free); a second place freed, both come back exactly once.</li>
 * <li>An egg with a full inventory: "Your inventory is full: make room for the egg first."</li>
 * </ul>
 */
public class QaDaycareNoRoom1115eCompatTests implements FabricGameTest {
	private static final String AREA = CompatGameTests.AREA;

	static final String RANCHER_NO_ROOM = "Your party and PC are full: make room before collecting Charmander";
	static final String PAIR_NO_ROOM = "Your party and PC are full: make room for both before taking your pair back.";
	static final String EGG_NO_ROOM = "Your inventory is full: make room for the egg first.";
	static final String TAKEN_BACK = "You took your pair back.";

	/** Takes {@code n} of the filler Magikarp out of {@code pc}, making that many free places. */
	static void freePlaces(PCStore pc, int n) {
		List<Pokemon> karp = new ArrayList<>();
		for (Pokemon p : pc) {
			if (p.getSpecies().getName().equals("Magikarp")) {
				karp.add(p);
			}
		}
		for (int i = 0; i < n; i++) {
			pc.remove(karp.get(i));
		}
	}

	static int countInPc(PCStore pc, UUID id) {
		int n = 0;
		for (Pokemon p : pc) {
			if (p.getUuid().equals(id)) {
				n++;
			}
		}
		return n;
	}

	static int countInParty(ServerPlayer player, UUID id) {
		return (int) party(player).stream().filter(p -> p.getUuid().equals(id)).count();
	}

	static boolean inBackup(PCStore pc, UUID id) {
		return pc.getBackupStore().getPokemon().stream().anyMatch(p -> p.getUuid().equals(id));
	}

	// ---- the rancher's Collect ----

	/**
	 * Party and PC full, Collect pressed on the rancher's screen: the exact line, nothing charged, the boarder still in her
	 * care and nowhere else. One place freed in the PC, the same row's Collect brings it back to the PC and charges.
	 */
	//$ gametest_ticks_batch AREA '100' '"qaDaycareNoRoom1115eRancher"'
	@GameTest(template = AREA, timeoutTicks = 100, batch = "qaDaycareNoRoom1115eRancher")
	public void qaRanchersCollectWithPartyAndPcFullSaysMakeRoomAndChargesNothing(GameTestHelper helper) {
		Villager rancher = rancher(helper);
		List<String> seen = new ArrayList<>();
		ServerPlayer player = player(helper, seen, 100_000, TWO_EEVEE_AND_ONE, "charmander level=5");
		Pokemon kid = party(player).get(1);
		UUID id = kid.getUuid();
		helper.assertTrue(CobblemonDaycare.leave(player, rancher, kid), "setup: not left");
		fillParty(player);
		PCStore pc = fillPc(Cobblemon.INSTANCE.getStorage().getPC(player));
		ChoiceMenu menu = open(helper, player, rancher);
		seen.clear();
		click(player, menu, CobblemonDaycare.FIRST_BOARDER_SLOT);
		helper.assertTrue(seen.contains(RANCHER_NO_ROOM), "with party and PC full, Collect told: " + seen);
		helper.assertValueEqual(CobbleDollarsBank.balance(player), 100_000L, "the balance after a refused Collect");
		helper.assertTrue(Daycare.boarders(rancher).size() == 1 && !anywhere(player, id), "after a refused Collect: boarders "
			+ Daycare.boarders(rancher).size() + ", Charmander with the trainer " + anywhere(player, id));
		freePlaces(pc, 1);
		helper.runAfterDelay(3, () -> {
			seen.clear();
			click(player, menu, CobblemonDaycare.FIRST_BOARDER_SLOT);
			helper.assertTrue(Daycare.boarders(rancher).isEmpty() && countInPc(pc, id) == 1 && countInParty(player, id) == 0,
				"with one place free, Collect: boarders " + Daycare.boarders(rancher).size() + ", in the PC " + countInPc(pc, id) + "; told " + seen);
			helper.assertTrue(CobbleDollarsBank.balance(player) < 100_000L, "collected for free");
			helper.assertTrue(seen.stream().anyMatch(l -> l.startsWith("Charmander is back with you")), "the player was told: " + seen);
			helper.succeed();
		});
	}

	// ---- the keeper's Take back ----

	/**
	 * Party full and one place in the PC: the first of the pair would fit, the second wouldn't. She keeps both, the line
	 * says so, and the PC's one place is still free (the first wasn't left there). A second place freed, Take back brings
	 * each back exactly once.
	 */
	//$ gametest_ticks_batch AREA '100' '"qaDaycareNoRoom1115eOnePlace"'
	@GameTest(template = AREA, timeoutTicks = 100, batch = "qaDaycareNoRoom1115eOnePlace")
	public void qaTakeBackWithRoomForOnlyOneKeepsThePairAndLeavesNoCopy(GameTestHelper helper) {
		Villager keeper = keeper(helper);
		List<String> seen = new ArrayList<>();
		ServerPlayer player = player(helper, seen, 0, TWO_EEVEE_AND_ONE, "eevee gender=female level=20", "eevee gender=male level=20");
		UUID[] pair = leavePair(helper, keeper, player);
		fillParty(player);
		PCStore pc = fillPc(Cobblemon.INSTANCE.getStorage().getPC(player));
		freePlaces(pc, 1);
		ChoiceMenu menu = open(helper, player, keeper);
		seen.clear();
		click(player, menu, CobblemonDaycareKeeper.TAKE_BACK);
		helper.assertTrue(seen.contains(PAIR_NO_ROOM), "with room for one, Take back told: " + seen);
		helper.assertTrue(DaycareKeepers.pairs(keeper).size() == 1, "she didn't keep the pair");
		for (UUID id : pair) {
			helper.assertTrue(!inParty(player, id) && !inPc(pc, id) && !inBackup(pc, id), "a refused Take back left " + id + " with the trainer: party "
				+ inParty(player, id) + ", PC " + inPc(pc, id) + ", overflow " + inBackup(pc, id));
		}
		helper.assertTrue(pc.getFirstAvailablePosition() != null, "the PC's one free place was taken by a refused Take back");
		freePlaces(pc, 1);
		helper.runAfterDelay(3, () -> {
			seen.clear();
			click(player, menu, CobblemonDaycareKeeper.TAKE_BACK);
			helper.assertTrue(seen.contains(TAKEN_BACK), "with room for both, Take back told: " + seen);
			helper.assertTrue(DaycareKeepers.pairs(keeper).isEmpty(), "she still has the pair");
			for (UUID id : pair) {
				helper.assertValueEqual(countInPc(pc, id) + countInParty(player, id), 1, "copies of " + id + " with the trainer");
			}
			helper.succeed();
		});
	}

	// ---- the keeper's eggs ----

	/** An egg waits, the player can pay and has no free slot: the exact line, in place of the price. */
	//$ gametest_ticks_batch AREA '100' '"qaDaycareNoRoom1115eEggLine"'
	@GameTest(template = AREA, timeoutTicks = 100, batch = "qaDaycareNoRoom1115eEggLine")
	public void qaAnEggWithAFullInventoryReadsMakeRoomForTheEgg(GameTestHelper helper) {
		Villager keeper = keeper(helper);
		long price = (long) DaycareKeepers.EGG_PRICE * Money.DOLLARS_PER_EMERALD;
		List<String> seen = new ArrayList<>();
		ServerPlayer player = player(helper, seen, 3 * price, TWO_EEVEE_AND_ONE, "eevee gender=female level=20", "eevee gender=male level=20");
		leavePair(helper, keeper, player);
		DaycareKeepers.Pair p = DaycareKeepers.pairs(keeper).get(0);
		DaycareKeepers.setPairs(keeper, List.of(p.withEggs(1, p.day())));
		ChoiceMenu menu = open(helper, player, keeper);
		fillInventory(player, 0);
		seen.clear();
		click(player, menu, CobblemonDaycareKeeper.EGGS);
		helper.assertTrue(CobblemonDaycareKeeper.cobbreeding(), "setup: the compat suite runs with Cobbreeding");
		helper.assertTrue(seen.contains(EGG_NO_ROOM), "with a full inventory, collecting an egg told: " + seen);
		helper.assertValueEqual(CobbleDollarsBank.balance(player), 3 * price, "the balance after a refused egg");
		helper.succeed();
	}
}
