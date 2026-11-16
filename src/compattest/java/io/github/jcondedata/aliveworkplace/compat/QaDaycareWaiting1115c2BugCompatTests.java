package io.github.jcondedata.aliveworkplace.compat;

import static io.github.jcondedata.aliveworkplace.compat.QaDaycareFallbacks1115dCompatTests.*;
import static io.github.jcondedata.aliveworkplace.compat.QaDaycareWaiting1115c2CompatTests.*;

import com.cobblemon.mod.common.Cobblemon;
import com.cobblemon.mod.common.api.storage.party.PlayerPartyStore;
import com.cobblemon.mod.common.pokemon.Pokemon;
import io.github.jcondedata.aliveworkplace.compat.cobblemon.CobblemonDaycare;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.npc.Villager;

/**
 * QA (lane C, 2026-11-15, after B1295/B1296/B1287's fix): the login reminder for ONE waiting Pokémon reads in the
 * plural ("Pokémon from the daycare are waiting for a free place in it (1)."). The mod says its counts in the singular
 * at one elsewhere (B444: "1 expedition"); this line has no singular form.
 */
public class QaDaycareWaiting1115c2BugCompatTests implements FabricGameTest {
	private static final String AREA = CompatGameTests.AREA;

	/** An offline trainer's boarder waits; at the login with the PC still full the reminder for one is in the singular. */
	//$ gametest_ticks_batch AREA '200' '"qaDaycareWaiting1115c2BugOne"'
	@GameTest(template = AREA, timeoutTicks = 200, batch = "qaDaycareWaiting1115c2BugOne")
	public void qaTheLoginReminderForOnePokemonIsSingular(GameTestHelper helper) {
		Villager rancher = rancher(helper);
		UUID uuid = UUID.randomUUID();
		List<String> seen = new ArrayList<>();
		ServerPlayer player = login(helper, seen, uuid, "qaOne");
		PlayerPartyStore party = Cobblemon.INSTANCE.getStorage().getParty(player);
		party.add(make("bulbasaur level=10"));
		Pokemon kid = make("charmander level=5");
		party.add(kid);
		helper.assertTrue(CobblemonDaycare.leave(player, rancher, kid), "setup: not left");
		QaDaycareRoom1115cBugCompatTests.fillWholePc(pc(helper, uuid), helper.getLevel().registryAccess());
		logout(player);
		rancher.kill();
		helper.runAfterDelay(5, () -> {
			helper.assertValueEqual(waiting(helper, uuid), 1, "setup: waiting");
			List<String> back = new ArrayList<>();
			login(helper, back, uuid, "qaOne");
			helper.runAfterDelay(10, () -> {
				List<String> lines = back.stream().filter(s -> s.startsWith("Your PC is full:")).toList();
				helper.assertValueEqual(lines.size(), 1, "reminders at the login: " + back);
				helper.assertTrue(!lines.get(0).contains("are waiting"),
					"the reminder for one Pokémon is in the plural: \"" + lines.get(0) + "\"");
				helper.assertValueEqual(lines.get(0), "Your PC is full: 1 Pokémon from the daycare is waiting for a free place in it.", "the reminder for one");
				helper.succeed();
			});
		});
	}
}
