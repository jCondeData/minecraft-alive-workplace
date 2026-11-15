package io.github.jcondedata.aliveworkplace.compat;

import static io.github.jcondedata.aliveworkplace.compat.QaDaycareFallbacks1115dCompatTests.*;
import static io.github.jcondedata.aliveworkplace.compat.QaDaycareRoom1115d2CompatTests.inBackup;
import static io.github.jcondedata.aliveworkplace.compat.QaDaycareRoom1115d2CompatTests.pcCount;
import static io.github.jcondedata.aliveworkplace.compat.QaDaycareRoom1115d2CompatTests.waiting;

import com.cobblemon.mod.common.Cobblemon;
import com.cobblemon.mod.common.api.storage.pc.PCStore;
import com.cobblemon.mod.common.pokemon.Pokemon;
import io.github.jcondedata.aliveworkplace.daycare.DaycareKeepers;
import io.github.jcondedata.aliveworkplace.ranch.Daycare;
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
 * QA bug tests (lane D, 2026-11-15, second pass; each fails on main): B1284's fix sends the Pokémon a keeper's or
 * rancher's death can't fit in a full PC to Cobblemon's PC overflow (its backup store). Cobblemon 1.7.3 writes the
 * backup store when it saves a PC ("BackupStore" in PCStore.saveToNBT and saveToJSON) but never reads it back
 * (PCStore.loadFromNBT and loadFromJSON don't), so those Pokémon are gone at the next restart, or when an offline
 * trainer's PC is unloaded and loaded again: the promise "no Pokémon lost" holds only until the PC is next loaded.
 * Unregistered: register them with the fix.
 */
public class QaDaycareRoom1115d2BugCompatTests implements FabricGameTest {
	private static final String AREA = CompatGameTests.AREA;

	static PCStore reloaded(PCStore pc, UUID owner, GameTestHelper helper) {
		var access = helper.getLevel().registryAccess();
		CompoundTag saved = pc.saveToNBT(new CompoundTag(), access);
		PCStore loaded = new PCStore(owner);
		loaded.loadFromNBT(saved, access);
		return loaded;
	}

	/** The keeper dies with the trainer's party and PC full; the PC is saved and loaded (a restart): the pair is still there. */
	//$ gametest_ticks_batch AREA '100' '"qaDaycareRoom1115d2BugKeeperReload"'
	@GameTest(template = AREA, timeoutTicks = 100, batch = "qaDaycareRoom1115d2BugKeeperReload")
	public void qaAPairInTheOverflowSurvivesARestart(GameTestHelper helper) {
		Villager keeper = keeper(helper);
		List<String> seen = new ArrayList<>();
		ServerPlayer player = player(helper, seen, 0, TWO_EEVEE_AND_ONE, "eevee gender=female level=20", "eevee gender=male level=20");
		UUID[] pair = leavePair(helper, keeper, player);
		fillParty(player);
		PCStore pc = QaDaycareFallbacks1115dBugCompatTests.fillPc(Cobblemon.INSTANCE.getStorage().getPC(player));
		keeper.kill();
		helper.runAfterDelay(2, () -> {
			helper.assertTrue(waiting(pc, pair[0]) && waiting(pc, pair[1]) && !inBackup(pc, pair[0]) && !inBackup(pc, pair[1]),
				"setup: the pair isn't waiting for the PC (or went to Cobblemon's overflow)");
			PCStore loaded = reloaded(pc, player.getUUID(), helper);
			helper.assertValueEqual(pcCount(loaded), pcCount(pc), "setup: the boxes' Pokémon after the reload");
			boolean mother = inPc(loaded, pair[0]) || inBackup(loaded, pair[0]) || waiting(loaded, pair[0]);
			boolean father = inPc(loaded, pair[1]) || inBackup(loaded, pair[1]) || waiting(loaded, pair[1]);
			helper.assertTrue(mother && father, "after the keeper's death and a save and load of the full PC: mother " + mother + ", father " + father
				+ " (overflow before " + pc.getBackupStore().getPokemon().size() + ", after " + loaded.getBackupStore().getPokemon().size() + ")");
			helper.succeed();
		});
	}

	/** The rancher dies with the trainer's PC full; the PC is saved and loaded: the boarder is still there. */
	//$ gametest_ticks_batch AREA '100' '"qaDaycareRoom1115d2BugRancherReload"'
	@GameTest(template = AREA, timeoutTicks = 100, batch = "qaDaycareRoom1115d2BugRancherReload")
	public void qaABoarderInTheOverflowSurvivesARestart(GameTestHelper helper) {
		Villager rancher = rancher(helper);
		var access = helper.getLevel().registryAccess();
		UUID owner = UUID.randomUUID();
		QaDaycareFallbacks1115dBugCompatTests.fillPc(Cobblemon.INSTANCE.getStorage().getPC(owner, access));
		Pokemon kid = make("charmander level=5");
		Daycare.setBoarders(rancher, List.of(new Daycare.Boarder(owner, "away", kid.saveToNBT(access, new CompoundTag()), helper.getLevel().getGameTime())));
		rancher.kill();
		helper.runAfterDelay(2, () -> {
			PCStore now = Cobblemon.INSTANCE.getStorage().getPC(owner, access);
			helper.assertTrue(waiting(now, kid.getUuid()) && !inBackup(now, kid.getUuid()), "setup: the boarder isn't waiting for the PC (or went to Cobblemon's overflow)");
			PCStore loaded = reloaded(now, owner, helper);
			helper.assertTrue(inPc(loaded, kid.getUuid()) || inBackup(loaded, kid.getUuid()) || waiting(loaded, kid.getUuid()), "after the rancher's death and a save and load of the offline "
				+ "trainer's full PC the boarder is gone (overflow before " + now.getBackupStore().getPokemon().size() + ", after "
				+ loaded.getBackupStore().getPokemon().size() + ")");
			helper.succeed();
		});
	}
}
