package io.github.jcondedata.aliveworkplace.compat;

import static io.github.jcondedata.aliveworkplace.compat.QaDaycareFallbacks1115dCompatTests.*;

import com.cobblemon.mod.common.Cobblemon;
import com.cobblemon.mod.common.api.storage.pc.PCStore;
import com.cobblemon.mod.common.pokemon.Pokemon;
import io.github.jcondedata.aliveworkplace.ranch.Daycare;
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
 * QA bug tests (lane C, 2026-11-15; fail on main, unregistered): B1284's fix sends a Pokémon returned at a keeper's or
 * rancher's death with a full PC to Cobblemon's overflow (PCStore's backup store). In Cobblemon 1.7.3 the PC saves that
 * store (saveToNBT/saveToJSON write "BackupStore") but loadFromNBT/loadFromJSON never read it back, and the only thing
 * that moves its Pokémon into the boxes is tryRestoreBackedUpPokemon, called from resize (a box-count change) and from
 * the loads (where the store is always empty). So the overflow Pokémon is out of reach in normal play and gone at the
 * next restart.
 */
public class QaDaycareRoom1115cBugCompatTests implements FabricGameTest {
	private static final String AREA = CompatGameTests.AREA;

	/** Fills every box the PC has (the default box count, a real full PC: no boxes removed). */
	static PCStore fillWholePc(PCStore pc, net.minecraft.core.RegistryAccess access) {
		Pokemon template = make("magikarp level=1");
		while (pc.getFirstAvailablePosition() != null) {
			Pokemon copy = template.clone(false, access);
			copy.setUuid(UUID.randomUUID());
			pc.add(copy);
		}
		return pc;
	}

	/** Cobblemon's own save then load of the PC (what a restart does with the player's PC file). */
	static PCStore restart(GameTestHelper helper, PCStore pc) {
		var access = helper.getLevel().registryAccess();
		CompoundTag saved = pc.saveToNBT(new CompoundTag(), access);
		PCStore loaded = new PCStore(pc.getUuid());
		loaded.loadFromNBT(saved, access);
		return loaded;
	}

	static boolean reachable(PCStore pc, UUID id) {
		return inPc(pc, id) || pc.getBackupStore().getPokemon().stream().anyMatch(p -> p.getUuid().equals(id))
			|| io.github.jcondedata.aliveworkplace.compat.cobblemon.WaitingPokemon.count(pc.getUuid(), id) > 0;
	}

	/** The rancher dies while the trainer's whole PC is full: after a restart the boarder must still exist. */
	//$ gametest_ticks_batch AREA '200' '"qaDaycareRoom1115cBugRestart"'
	@GameTest(template = AREA, timeoutTicks = 200, batch = "qaDaycareRoom1115cBugRestart")
	public void qaAnOverflowBoarderSurvivesARestart(GameTestHelper helper) {
		Villager rancher = rancher(helper);
		List<String> seen = new ArrayList<>();
		ServerPlayer player = player(helper, seen, 0, TWO_EEVEE_AND_ONE, "charmander level=5");
		Pokemon kid = party(player).get(1);
		UUID id = kid.getUuid();
		helper.assertTrue(CobblemonDaycare.leave(player, rancher, kid), "setup: not left");
		PCStore pc = fillWholePc(Cobblemon.INSTANCE.getStorage().getPC(player), helper.getLevel().registryAccess());
		rancher.kill();
		helper.runAfterDelay(2, () -> {
			helper.assertTrue(Daycare.boarders(rancher).isEmpty(), "setup: she still holds it");
			helper.assertTrue(reachable(pc, id), "setup: not in the PC or its overflow before the restart");
			PCStore after = restart(helper, pc);
			helper.assertTrue(reachable(after, id) || inParty(player, id), "after a restart the boarder sent to the overflow is gone (in the overflow before: "
				+ pc.getBackupStore().getPokemon().size() + ", after: " + after.getBackupStore().getPokemon().size() + "; boxes " + after.getBoxes().size() + ")");
			helper.succeed();
		});
	}

	/** The rancher dies while the PC is full; then the trainer frees a box slot: the boarder must come into it. */
	//$ gametest_ticks_batch AREA '200' '"qaDaycareRoom1115cBugFreed"'
	@GameTest(template = AREA, timeoutTicks = 200, batch = "qaDaycareRoom1115cBugFreed")
	public void qaAnOverflowBoarderComesIntoAFreedPcSlot(GameTestHelper helper) {
		Villager rancher = rancher(helper);
		List<String> seen = new ArrayList<>();
		ServerPlayer player = player(helper, seen, 0, TWO_EEVEE_AND_ONE, "charmander level=5");
		Pokemon kid = party(player).get(1);
		UUID id = kid.getUuid();
		helper.assertTrue(CobblemonDaycare.leave(player, rancher, kid), "setup: not left");
		PCStore pc = fillWholePc(Cobblemon.INSTANCE.getStorage().getPC(player), helper.getLevel().registryAccess());
		rancher.kill();
		helper.runAfterDelay(2, () -> {
			helper.assertTrue(!inPc(pc, id) && reachable(pc, id), "setup: not in the overflow");
			// The player releases one Pokémon from the PC, then opens it (Cobblemon's /pc, the player's own way)
			pc.remove(pc.iterator().next());
			helper.getLevel().getServer().getCommands().performPrefixedCommand(player.createCommandSourceStack().withPermission(2), "pc");
			helper.runAfterDelay(5, () -> {
				helper.assertTrue(inPc(pc, id), "a PC slot was freed and the PC opened, but the boarder stays in the overflow (not in a box: "
					+ pc.getBackupStore().getPokemon().size() + " in the overflow), out of the player's reach");
				helper.succeed();
			});
		});
	}
}
