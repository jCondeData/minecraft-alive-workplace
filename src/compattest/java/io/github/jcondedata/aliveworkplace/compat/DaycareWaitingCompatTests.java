package io.github.jcondedata.aliveworkplace.compat;

import static io.github.jcondedata.aliveworkplace.compat.QaDaycareFallbacks1115dCompatTests.*;

import com.cobblemon.mod.common.Cobblemon;
import com.cobblemon.mod.common.api.storage.pc.PCStore;
import com.cobblemon.mod.common.pokemon.Pokemon;
import io.github.jcondedata.aliveworkplace.compat.cobblemon.CobblemonDaycare;
import io.github.jcondedata.aliveworkplace.compat.cobblemon.WaitingPokemon;
import io.github.jcondedata.aliveworkplace.daycare.DaycareKeepers;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.npc.Villager;

/**
 * B1295, B1296, B1287: what a daycare gives back at a keeper's or rancher's death while the trainer's PC is full waits
 * on our side (saved with the world), never in Cobblemon's PC overflow, and goes into the PC once a place is free.
 */
public class DaycareWaitingCompatTests implements FabricGameTest {
	private static final String AREA = CompatGameTests.AREA;

	static final String WAITING_CHARMANDER = "Your PC is full: Charmander is waiting for you and will come to your PC as soon as a place in it is free.";
	static final String CAME_CHARMANDER = "Charmander came to your PC from the daycare.";

	/** The rancher dies with the PC full: the trainer is told, and the boarder comes to the PC the moment a slot is freed. */
	//$ gametest_ticks_batch AREA '200' '"daycareWaitingFreedSlot"'
	@GameTest(template = AREA, timeoutTicks = 200, batch = "daycareWaitingFreedSlot")
	public void aBoarderWaitingForAFullPcComesWhenASlotIsFreed(GameTestHelper helper) {
		Villager rancher = rancher(helper);
		List<String> seen = new ArrayList<>();
		ServerPlayer player = player(helper, seen, 0, TWO_EEVEE_AND_ONE, "charmander level=5");
		Pokemon kid = party(player).get(1);
		UUID id = kid.getUuid();
		helper.assertTrue(CobblemonDaycare.leave(player, rancher, kid), "setup: not left");
		PCStore pc = QaDaycareRoom1115cBugCompatTests.fillWholePc(Cobblemon.INSTANCE.getStorage().getPC(player), helper.getLevel().registryAccess());
		seen.clear();
		rancher.kill();
		helper.runAfterDelay(5, () -> {
			helper.assertValueEqual(WaitingPokemon.count(helper.getLevel().getServer(), player.getUUID(), id), 1, "the boarder's entries waiting");
			helper.assertTrue(!inPc(pc, id) && !inParty(player, id) && pc.getBackupStore().getPokemon().isEmpty(), "the boarder went somewhere else");
			helper.assertTrue(seen.contains(WAITING_CHARMANDER), "the trainer wasn't told where the boarder is: " + seen);
			helper.assertTrue(!seen.contains(CAME_CHARMANDER), "told it came while the PC is full: " + seen);
			pc.remove(pc.iterator().next());
			helper.runAfterDelay(3, () -> {
				helper.assertTrue(inPc(pc, id), "a slot was freed but the boarder didn't come to the PC");
				helper.assertValueEqual(WaitingPokemon.count(helper.getLevel().getServer(), player.getUUID(), id), 0, "entries still waiting");
				helper.assertValueEqual(seen.stream().filter(CAME_CHARMANDER::equals).count(), 1L, "lines saying it came: " + seen);
				helper.succeed();
			});
		});
	}

	/** The keeper dies with room for only one of the pair: one goes to the PC, the other comes after the next freed slot. */
	//$ gametest_ticks_batch AREA '200' '"daycareWaitingOneSlot"'
	@GameTest(template = AREA, timeoutTicks = 200, batch = "daycareWaitingOneSlot")
	public void aPairWithOneFreeSlotSplitsAndTheSecondComesLater(GameTestHelper helper) {
		Villager keeper = keeper(helper);
		List<String> seen = new ArrayList<>();
		ServerPlayer player = player(helper, seen, 0, TWO_EEVEE_AND_ONE, "eevee gender=female level=20", "eevee gender=male level=20");
		UUID[] pair = leavePair(helper, keeper, player);
		PCStore pc = QaDaycareRoom1115cBugCompatTests.fillWholePc(Cobblemon.INSTANCE.getStorage().getPC(player), helper.getLevel().registryAccess());
		pc.remove(pc.iterator().next());
		keeper.kill();
		helper.runAfterDelay(5, () -> {
			helper.assertTrue(inPc(pc, pair[0]) != inPc(pc, pair[1]), "not exactly one of the pair in the PC");
			UUID other = inPc(pc, pair[0]) ? pair[1] : pair[0];
			helper.assertValueEqual(WaitingPokemon.count(helper.getLevel().getServer(), player.getUUID(), other), 1, "the other's entries waiting");
			helper.assertTrue(DaycareKeepers.pairs(keeper).isEmpty(), "she still holds them");
			for (Pokemon p : pc) {
				if (!p.getUuid().equals(pair[0]) && !p.getUuid().equals(pair[1])) {
					pc.remove(p);
					break;
				}
			}
			helper.runAfterDelay(3, () -> {
				helper.assertTrue(inPc(pc, pair[0]) && inPc(pc, pair[1]), "after a freed slot: mother " + inPc(pc, pair[0]) + ", father " + inPc(pc, pair[1]));
				helper.assertValueEqual(WaitingPokemon.count(helper.getLevel().getServer(), player.getUUID(), other), 0, "entries still waiting");
				helper.succeed();
			});
		});
	}

	/**
	 * The waiting list saved and loaded (a restart): the Pokémon is still there, whole; at the next login with a full PC
	 * the trainer is told how many wait, and it comes into the PC once there is room. A world from before has none.
	 */
	//$ gametest_ticks_batch AREA '100' '"daycareWaitingReload"'
	@GameTest(template = AREA, timeoutTicks = 100, batch = "daycareWaitingReload")
	public void theWaitingListSurvivesASaveAndLoad(GameTestHelper helper) {
		PartnerShowsCompatTests.clearLeftovers(helper);
		var access = helper.getLevel().registryAccess();
		List<String> seen = new ArrayList<>();
		ServerPlayer player = player(helper, seen, 0, TWO_EEVEE_AND_ONE);
		PCStore pc = QaDaycareRoom1115cBugCompatTests.fillWholePc(Cobblemon.INSTANCE.getStorage().getPC(player), access);
		Pokemon kid = make("charmander level=17");
		WaitingPokemon before = new WaitingPokemon();
		before.add(player.getUUID(), "listTutor", kid.saveToNBT(access, new CompoundTag()));
		CompoundTag saved = before.save(new CompoundTag(), access);
		WaitingPokemon loaded = WaitingPokemon.load(saved, access);
		List<Pokemon> back = loaded.waitingFor(player.getUUID(), access);
		helper.assertValueEqual(back.size(), 1, "Pokémon waiting after the reload");
		helper.assertTrue(back.get(0).getUuid().equals(kid.getUuid()) && back.get(0).getLevel() == 17
			&& back.get(0).getSpecies().getName().equals("Charmander"), "not the same Pokémon after the reload");
		helper.assertValueEqual(WaitingPokemon.load(new CompoundTag(), access).size(), 0, "Pokémon waiting in a world from before");
		seen.clear();
		loaded.handOver(player);
		helper.assertTrue(seen.contains("Your PC is full: 1 Pokémon from the daycare is waiting for a free place in it."), "not told at login: " + seen);
		helper.assertTrue(!inPc(pc, kid.getUuid()) && loaded.size() == 1, "handed over to a full PC");
		pc.remove(pc.iterator().next());
		loaded.handOver(player);
		helper.assertTrue(inPc(pc, kid.getUuid()), "not in the PC once a slot was free");
		helper.assertValueEqual(loaded.size(), 0, "Pokémon still waiting");
		helper.assertTrue(seen.contains(CAME_CHARMANDER), "not told it came: " + seen);
		helper.assertValueEqual(WaitingPokemon.load(loaded.save(new CompoundTag(), access), access).size(), 0, "Pokémon waiting after the next save");
		helper.succeed();
	}
}
