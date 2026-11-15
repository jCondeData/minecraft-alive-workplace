package io.github.jcondedata.aliveworkplace.compat;

import static io.github.jcondedata.aliveworkplace.compat.QaDaycareFallbacks1115dCompatTests.*;

import com.cobblemon.mod.common.Cobblemon;
import com.cobblemon.mod.common.api.storage.pc.PCBox;
import com.cobblemon.mod.common.api.storage.pc.PCStore;
import com.cobblemon.mod.common.pokemon.Pokemon;
import io.github.jcondedata.aliveworkplace.compat.cobbledollars.CobbleDollarsBank;
import io.github.jcondedata.aliveworkplace.compat.cobblemon.CobblemonDaycare;
import io.github.jcondedata.aliveworkplace.compat.cobblemon.CobblemonDaycareKeeper;
import io.github.jcondedata.aliveworkplace.daycare.DaycareKeepers;
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

/**
 * QA bug tests (lane D, 2026-11-15; each fails on main): the daycares' party-or-PC fallbacks ignore whether the PC took
 * the Pokemon (PCStore.add returns false when it's full), so with a full PC a Pokemon taken back, returned at a keeper's
 * or rancher's death, or hatched without Cobbreeding vanishes; and an egg that can't be given for want of room is
 * refused with "An egg costs ..." to a player who can pay. Unregistered: register them with the fix.
 */
public class QaDaycareFallbacks1115dBugCompatTests implements FabricGameTest {
	private static final String AREA = CompatGameTests.AREA;

	// ---- a full PC: a fallback with nowhere to go ----

	static PCStore fillPc(PCStore pc) {
		List<PCBox> boxes = pc.getBoxes();
		while (boxes.size() > 1) {
			boxes.remove(boxes.size() - 1);
		}
		while (pc.getFirstAvailablePosition() != null) {
			pc.add(make("magikarp level=1"));
		}
		return pc;
	}

	static boolean anywhere(ServerPlayer player, UUID id) {
		PCStore pc = Cobblemon.INSTANCE.getStorage().getPC(player);
		boolean backup = pc.getBackupStore().getPokemon().stream().anyMatch(p -> p.getUuid().equals(id));
		return inParty(player, id) || inPc(pc, id) || backup || io.github.jcondedata.aliveworkplace.compat.cobblemon.WaitingPokemon.count(player.getUUID(), id) > 0;
	}

	/** Party and PC full when the pair is taken back: neither Pokémon may vanish (she keeps them, or they go somewhere the trainer owns). */
	//$ gametest_ticks_batch AREA '100' '"qaDaycareFallbacks1115dBugPcFullTakeBack"'
	@GameTest(template = AREA, timeoutTicks = 100, batch = "qaDaycareFallbacks1115dBugPcFullTakeBack")
	public void qaTakeBackWithPartyAndPcFullLosesNoPokemon(GameTestHelper helper) {
		Villager keeper = keeper(helper);
		List<String> seen = new ArrayList<>();
		ServerPlayer player = player(helper, seen, 0, TWO_EEVEE_AND_ONE, "eevee gender=female level=20", "eevee gender=male level=20");
		UUID[] pair = leavePair(helper, keeper, player);
		fillParty(player);
		fillPc(Cobblemon.INSTANCE.getStorage().getPC(player));
		Component said = CobblemonDaycareKeeper.takeBack(player, keeper);
		boolean kept = !DaycareKeepers.pairs(keeper).isEmpty();
		helper.assertTrue(kept || (anywhere(player, pair[0]) && anywhere(player, pair[1])), "with party and PC full, taking back: mother "
			+ anywhere(player, pair[0]) + ", father " + anywhere(player, pair[1]) + ", still with her " + kept + "; told '" + said.getString() + "'");
		helper.succeed();
	}

	/** The keeper dies while the trainer's PC is full: neither Pokémon of the pair may vanish. */
	//$ gametest_ticks_batch AREA '100' '"qaDaycareFallbacks1115dBugPcFullDeath"'
	@GameTest(template = AREA, timeoutTicks = 100, batch = "qaDaycareFallbacks1115dBugPcFullDeath")
	public void qaKeepersDeathWithAFullPcLosesNoPokemon(GameTestHelper helper) {
		Villager keeper = keeper(helper);
		List<String> seen = new ArrayList<>();
		ServerPlayer player = player(helper, seen, 0, TWO_EEVEE_AND_ONE, "eevee gender=female level=20", "eevee gender=male level=20");
		UUID[] pair = leavePair(helper, keeper, player);
		fillPc(Cobblemon.INSTANCE.getStorage().getPC(player));
		keeper.kill();
		helper.runAfterDelay(2, () -> {
			helper.assertTrue(anywhere(player, pair[0]) && anywhere(player, pair[1]), "with the PC full, her death: mother " + anywhere(player, pair[0])
				+ ", father " + anywhere(player, pair[1]));
			helper.succeed();
		});
	}

	/** The rancher dies while the trainer's PC is full: the boarder may not vanish. */
	//$ gametest_ticks_batch AREA '100' '"qaDaycareFallbacks1115dBugPcFullRancher"'
	@GameTest(template = AREA, timeoutTicks = 100, batch = "qaDaycareFallbacks1115dBugPcFullRancher")
	public void qaRanchersDeathWithAFullPcLosesNoPokemon(GameTestHelper helper) {
		Villager rancher = rancher(helper);
		List<String> seen = new ArrayList<>();
		ServerPlayer player = player(helper, seen, 0, TWO_EEVEE_AND_ONE, "charmander level=5");
		Pokemon kid = party(player).get(1);
		UUID id = kid.getUuid();
		helper.assertTrue(CobblemonDaycare.leave(player, rancher, kid), "setup: not left");
		fillPc(Cobblemon.INSTANCE.getStorage().getPC(player));
		rancher.kill();
		helper.runAfterDelay(2, () -> {
			helper.assertTrue(anywhere(player, id), "with the PC full, the rancher's death lost the boarder");
			helper.succeed();
		});
	}

	/** Without Cobbreeding, party and PC full: the hatchling may not vanish after being paid for. */
	//$ gametest_ticks_batch AREA '100' '"qaDaycareFallbacks1115dBugPcFullHatch"'
	@GameTest(template = AREA, timeoutTicks = 100, batch = "qaDaycareFallbacks1115dBugPcFullHatch")
	public void qaAHatchlingWithPartyAndPcFullIsNotLost(GameTestHelper helper) {
		PartnerShowsCompatTests.clearLeftovers(helper);
		List<String> seen = new ArrayList<>();
		ServerPlayer player = player(helper, seen, 0, "rattata level=3");
		fillParty(player);
		PCStore pc = fillPc(Cobblemon.INSTANCE.getStorage().getPC(player));
		Component said = CobblemonDaycareKeeper.giveEgg(player, make("eevee gender=female level=20"), make("eevee gender=male level=20"),
			RandomSource.create(3), false);
		boolean somewhere = party(player).stream().anyMatch(p -> p.getSpecies().getName().equals("Eevee"))
			|| pc.getBackupStore().getPokemon().stream().anyMatch(p -> p.getSpecies().getName().equals("Eevee"));
		for (Pokemon p : pc) {
			somewhere |= p.getSpecies().getName().equals("Eevee");
		}
		helper.assertTrue(said == null || somewhere, "with party and PC full the hatchling was reported ('" + (said == null ? "" : said.getString())
			+ "') but is nowhere");
		helper.succeed();
	}

	/**
	 * An egg can't be given because the inventory is full: the player, who has the money, isn't told "An egg costs ..."
	 * (the wrong reason); the line names the real one.
	 */
	//$ gametest_ticks_batch AREA '100' '"qaDaycareFallbacks1115dBugNoRoomLine"'
	@GameTest(template = AREA, timeoutTicks = 100, batch = "qaDaycareFallbacks1115dBugNoRoomLine")
	public void qaAnEggWithNoRoomIsntRefusedAsTooExpensive(GameTestHelper helper) {
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
		helper.assertTrue(CobbleDollarsBank.balance(player) == 3 * price && DaycareKeepers.pairs(keeper).get(0).eggs() == 1, "setup: the egg was given or charged");
		helper.assertTrue(!seen.isEmpty() && seen.stream().noneMatch(l -> l.startsWith("An egg costs ")),
			"a player with " + 3 * price + " CobbleDollars and a full inventory was told: " + seen);
		helper.succeed();
	}
}
