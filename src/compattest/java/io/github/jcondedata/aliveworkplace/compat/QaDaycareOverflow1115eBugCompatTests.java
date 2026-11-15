package io.github.jcondedata.aliveworkplace.compat;

import static io.github.jcondedata.aliveworkplace.compat.QaDaycareFallbacks1115dBugCompatTests.fillPc;
import static io.github.jcondedata.aliveworkplace.compat.QaDaycareFallbacks1115dCompatTests.TWO_EEVEE_AND_ONE;
import static io.github.jcondedata.aliveworkplace.compat.QaDaycareFallbacks1115dCompatTests.keeper;
import static io.github.jcondedata.aliveworkplace.compat.QaDaycareFallbacks1115dCompatTests.leavePair;
import static io.github.jcondedata.aliveworkplace.compat.QaDaycareFallbacks1115dCompatTests.party;
import static io.github.jcondedata.aliveworkplace.compat.QaDaycareFallbacks1115dCompatTests.player;
import static io.github.jcondedata.aliveworkplace.compat.QaDaycareFallbacks1115dCompatTests.rancher;
import static io.github.jcondedata.aliveworkplace.compat.QaDaycareNoRoom1115eCompatTests.countInPc;
import static io.github.jcondedata.aliveworkplace.compat.QaDaycareNoRoom1115eCompatTests.freePlaces;
import static io.github.jcondedata.aliveworkplace.compat.QaDaycareNoRoom1115eCompatTests.inBackup;

import com.cobblemon.mod.common.Cobblemon;
import com.cobblemon.mod.common.api.storage.pc.PCStore;
import com.cobblemon.mod.common.pokemon.Pokemon;
import io.github.jcondedata.aliveworkplace.compat.cobblemon.CobblemonDaycare;
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
 * QA bug tests (lane E, 2026-11-15; each fails on main, B1284's fix): a daycare keeper's or rancher's death with the
 * trainer's PC full sends the Pokémon to the PC's overflow (Cobblemon's backup store), "saved with the PC". It is
 * saved (the PC's BackupStore tag holds them), but Cobblemon 1.7.3's PCStore.loadFromNBT never reads that tag back, so
 * at the next load of the PC (a restart, or the trainer's PC loaded again) they are gone, even with room made.
 * Expected (28.12, "if she dies, the pairs go to their trainers' PCs"; B1284: none is lost): after the PC is saved and
 * loaded again they are in its boxes, each once.
 */
public class QaDaycareOverflow1115eBugCompatTests implements FabricGameTest {
	private static final String AREA = CompatGameTests.AREA;

	// ---- a death with the PC full: the overflow, saved with the PC ----

	/**
	 * The keeper dies, the trainer's PC full: the pair goes to the PC's overflow and the PC is marked changed (what makes
	 * Cobblemon save it). Room made, the PC saved and loaded again: both are in its boxes, each once, the overflow empty.
	 */
	//$ gametest_ticks_batch AREA '100' '"qaDaycareOverflow1115eKeeper"'
	@GameTest(template = AREA, timeoutTicks = 100, batch = "qaDaycareOverflow1115eKeeper")
	public void qaAKeepersPairInAFullPcsOverflowIsInItsBoxesAfterTheNextLoad(GameTestHelper helper) {
		Villager keeper = keeper(helper);
		List<String> seen = new ArrayList<>();
		ServerPlayer player = player(helper, seen, 0, TWO_EEVEE_AND_ONE, "eevee gender=female level=20", "eevee gender=male level=20");
		UUID[] pair = leavePair(helper, keeper, player);
		PCStore pc = fillPc(Cobblemon.INSTANCE.getStorage().getPC(player));
		int[] changes = {0};
		pc.getPcChangeObservable().subscribe(unit -> changes[0]++);
		keeper.kill();
		helper.runAfterDelay(2, () -> {
			for (UUID id : pair) {
				helper.assertTrue(inBackup(pc, id), "with the PC full, her death didn't put " + id + " in the PC's overflow");
			}
			helper.assertTrue(changes[0] > 0, "the PC wasn't marked changed, so Cobblemon wouldn't save the overflow");
			reloadedHasEach(helper, player, pc, pair);
			helper.succeed();
		});
	}

	/** The same for the rancher's boarder. */
	//$ gametest_ticks_batch AREA '100' '"qaDaycareOverflow1115eRancher"'
	@GameTest(template = AREA, timeoutTicks = 100, batch = "qaDaycareOverflow1115eRancher")
	public void qaARanchersBoarderInAFullPcsOverflowIsInItsBoxesAfterTheNextLoad(GameTestHelper helper) {
		Villager rancher = rancher(helper);
		List<String> seen = new ArrayList<>();
		ServerPlayer player = player(helper, seen, 0, TWO_EEVEE_AND_ONE, "charmander level=5");
		Pokemon kid = party(player).get(1);
		UUID id = kid.getUuid();
		helper.assertTrue(CobblemonDaycare.leave(player, rancher, kid), "setup: not left");
		PCStore pc = fillPc(Cobblemon.INSTANCE.getStorage().getPC(player));
		int[] changes = {0};
		pc.getPcChangeObservable().subscribe(unit -> changes[0]++);
		rancher.kill();
		helper.runAfterDelay(2, () -> {
			helper.assertTrue(inBackup(pc, id), "with the PC full, the rancher's death didn't put the boarder in the PC's overflow");
			helper.assertTrue(changes[0] > 0, "the PC wasn't marked changed, so Cobblemon wouldn't save the overflow");
			reloadedHasEach(helper, player, pc, new UUID[] {id});
			helper.succeed();
		});
	}

	/** Frees as many places as {@code ids}, saves {@code pc} and loads it into a fresh store: each id is in a box once. */
	static void reloadedHasEach(GameTestHelper helper, ServerPlayer player, PCStore pc, UUID[] ids) {
		freePlaces(pc, ids.length);
		CompoundTag saved = pc.saveToNBT(new CompoundTag(), player.registryAccess());
		PCStore loaded = new PCStore(pc.getUuid());
		loaded.loadFromNBT(saved, player.registryAccess());
		int karp = 0;
		for (Pokemon p : loaded) {
			karp += p.getSpecies().getName().equals("Magikarp") ? 1 : 0;
		}
		// The round trip itself keeps what is in the boxes (the filler Magikarp), so a miss below is the overflow's
		int karpBefore = 0;
		for (Pokemon p : pc) {
			karpBefore += p.getSpecies().getName().equals("Magikarp") ? 1 : 0;
		}
		helper.assertValueEqual(karp, karpBefore, "Magikarp in the boxes after the PC is saved and loaded");
		for (UUID id : ids) {
			int boxes = countInPc(loaded, id);
			helper.assertTrue(boxes == 1 && !inBackup(loaded, id), "after the PC is saved and loaded with room: " + id + " in its boxes " + boxes
				+ " times, in the overflow " + inBackup(loaded, id) + " (the saved PC's overflow held " + saved.getCompound("BackupStore").size()
				+ " entries; " + karp + " Magikarp kept)");
		}
	}
}
