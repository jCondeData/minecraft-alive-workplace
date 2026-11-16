package io.github.jcondedata.aliveworkplace.compat;

import com.cobblemon.mod.common.Cobblemon;
import com.cobblemon.mod.common.api.storage.pc.PCStore;
import com.cobblemon.mod.common.pokemon.Pokemon;
import io.github.jcondedata.aliveworkplace.WorkplaceConfig;
import io.github.jcondedata.aliveworkplace.compat.cobblemon.WaitingPokemon;
import io.github.jcondedata.aliveworkplace.daycare.DaycareKeepers;
import io.github.jcondedata.aliveworkplace.realm.RealmTreasury;
import io.github.jcondedata.aliveworkplace.trainer.Trainers;
import io.github.jcondedata.aliveworkplace.work.Money;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.npc.Villager;

/**
 * QA (lane-d-1116-1907), from the specs of two Cobblemon/CobbleDollars text fixes.
 * <ul>
 * <li>B1309: the daycare's login reminder reads in the singular for one waiting Pokemon ("as B444's '1 expedition'")
 * and in the plural with its count for more. From four down to one across logins, and one left across a restart of
 * the waiting list, at the next logins.</li>
 * <li>B1326: the realm treasury's CobbleDollars hint gives "the same grouped number as elsewhere" (B466's thousands
 * commas), at every rate the config allows (1 to 10,000 per emerald, read from the file, out-of-range clamped), and a
 * trainer's prize line at 999 / 1,000 and beyond.</li>
 * </ul>
 */
public class QaWaitingAndMoney1116dCompatTests implements FabricGameTest {
	private static final String AREA = CompatGameTests.AREA;
	private static final String ONE = "Your PC is full: 1 Pokémon from the daycare is waiting for a free place in it.";
	private static final String CAME_EEVEE = "Eevee came to your PC from the daycare.";

	private static String many(int n) {
		return "Your PC is full: Pokémon from the daycare are waiting for a free place in it (" + n + ").";
	}

	private static long reminders(List<String> seen) {
		return QaDaycareWaiting1115c2CompatTests.reminders(seen);
	}

	private static long count(List<String> seen, String line) {
		return seen.stream().filter(line::equals).count();
	}

	private static DaycareKeepers.Pair pair(GameTestHelper helper, UUID owner, String name, List<UUID> ids) {
		var access = helper.getLevel().registryAccess();
		Pokemon mother = QaDaycareFallbacks1115dCompatTests.make("eevee gender=female level=20");
		Pokemon father = QaDaycareFallbacks1115dCompatTests.make("eevee gender=male level=20");
		ids.add(mother.getUuid());
		ids.add(father.getUuid());
		return new DaycareKeepers.Pair(owner, name, mother.saveToNBT(access, new CompoundTag()), father.saveToNBT(access, new CompoundTag()), 0,
			DaycareKeepers.day(helper.getLevel()));
	}

	/**
	 * Two pairs of an offline trainer with a full PC wait when the keeper dies (4). At login: the plural with (4), once.
	 * Three places freed: three come; at the next login the reminder for the one left is the singular line, once, and
	 * no plural.
	 */
	//$ gametest_ticks_batch AREA '300' '"qaWaiting1116dFourToOne"'
	@GameTest(template = AREA, timeoutTicks = 300, batch = "qaWaiting1116dFourToOne")
	public void qaFourWaitingDownToOneReadsTheSingularAtTheNextLogin(GameTestHelper helper) {
		Villager keeper = QaDaycareFallbacks1115dCompatTests.keeper(helper);
		var access = helper.getLevel().registryAccess();
		UUID owner = UUID.randomUUID();
		QaDaycareRoom1115cBugCompatTests.fillWholePc(Cobblemon.INSTANCE.getStorage().getPC(owner, access), access);
		List<UUID> ids = new ArrayList<>();
		DaycareKeepers.setPairs(keeper, List.of(pair(helper, owner, "qaFour", ids), pair(helper, owner, "qaFour", ids)));
		keeper.kill();
		helper.runAfterDelay(5, () -> {
			helper.assertValueEqual(QaDaycareWaiting1115c2CompatTests.waiting(helper, owner), 4, "setup: waiting");
			List<String> seen = new ArrayList<>();
			ServerPlayer first = QaDaycareWaiting1115c2CompatTests.login(helper, seen, owner, "qaFour");
			helper.runAfterDelay(10, () -> {
				helper.assertValueEqual(count(seen, many(4)), 1L, "the reminder for four: " + seen);
				helper.assertValueEqual(reminders(seen), 1L, "reminders at the first login: " + seen);
				PCStore pc = Cobblemon.INSTANCE.getStorage().getPC(first);
				QaDaycareRoom1115d2CompatTests.freePc(pc, 3);
				helper.runAfterDelay(10, () -> {
					helper.assertValueEqual(count(seen, CAME_EEVEE), 3L, "came lines: " + seen);
					helper.assertValueEqual(QaDaycareWaiting1115c2CompatTests.waiting(helper, owner), 1, "left waiting");
					QaDaycareWaiting1115c2CompatTests.logout(first);
					List<String> back = new ArrayList<>();
					QaDaycareWaiting1115c2CompatTests.login(helper, back, owner, "qaFour");
					helper.runAfterDelay(10, () -> {
						helper.assertValueEqual(count(back, ONE), 1L, "the reminder for the one left: " + back);
						helper.assertValueEqual(reminders(back), 1L, "reminders at the second login: " + back);
						helper.assertTrue(back.stream().noneMatch(s -> s.contains("are waiting") || s.contains("(1)")),
							"a plural or \"(1)\" reminder for one: " + back);
						helper.succeed();
					});
				});
			});
		});
	}

	/**
	 * A pair waits for an offline trainer; the waiting list is saved and read back (a restart); one place freed while
	 * away. At login one comes and the reminder for the other is singular; after another restart and login, still the
	 * singular, once.
	 */
	//$ gametest_ticks_batch AREA '300' '"qaWaiting1116dRestart"'
	@GameTest(template = AREA, timeoutTicks = 300, batch = "qaWaiting1116dRestart")
	public void qaOneLeftAcrossARestartIsStillSingular(GameTestHelper helper) {
		Villager keeper = QaDaycareFallbacks1115dCompatTests.keeper(helper);
		var access = helper.getLevel().registryAccess();
		UUID owner = UUID.randomUUID();
		QaDaycareRoom1115cBugCompatTests.fillWholePc(Cobblemon.INSTANCE.getStorage().getPC(owner, access), access);
		List<UUID> ids = new ArrayList<>();
		DaycareKeepers.setPairs(keeper, List.of(pair(helper, owner, "qaRestart", ids)));
		keeper.kill();
		helper.runAfterDelay(5, () -> {
			helper.assertValueEqual(QaDaycareWaiting1115c2CompatTests.waiting(helper, owner), 2, "setup: waiting");
			QaDaycareWaiting1115c2CompatTests.restartWaitingList(helper);
			helper.assertValueEqual(QaDaycareWaiting1115c2CompatTests.waiting(helper, owner), 2, "waiting after a restart");
			QaDaycareWaiting1115c2CompatTests.freeOne(QaDaycareWaiting1115c2CompatTests.pc(helper, owner), ids);
			List<String> seen = new ArrayList<>();
			ServerPlayer first = QaDaycareWaiting1115c2CompatTests.login(helper, seen, owner, "qaRestart");
			helper.runAfterDelay(20, () -> {
				helper.assertValueEqual(count(seen, CAME_EEVEE), 1L, "came lines at the login: " + seen);
				helper.assertValueEqual(count(seen, ONE), 1L, "the reminder for the one left: " + seen);
				helper.assertValueEqual(reminders(seen), 1L, "reminders at the login: " + seen);
				QaDaycareWaiting1115c2CompatTests.logout(first);
				QaDaycareWaiting1115c2CompatTests.restartWaitingList(helper);
				helper.assertValueEqual(QaDaycareWaiting1115c2CompatTests.waiting(helper, owner), 1, "waiting after the second restart");
				List<String> back = new ArrayList<>();
				QaDaycareWaiting1115c2CompatTests.login(helper, back, owner, "qaRestart");
				helper.runAfterDelay(20, () -> {
					helper.assertValueEqual(count(back, ONE), 1L, "the reminder after the restart: " + back);
					helper.assertValueEqual(reminders(back), 1L, "reminders after the restart: " + back);
					helper.succeed();
				});
			});
		});
	}

	// ---- B1326 ----

	/** The config read the way the game reads it (clamped), its rate put in place as apply() does; the old rate back after. */
	private static String hintAt(String written) {
		int before = Money.DOLLARS_PER_EMERALD;
		try {
			Path dir = Files.createTempDirectory("aliveworkplace-qa1116d-money");
			Files.writeString(dir.resolve(WorkplaceConfig.FILE), "{\"configVersion\": 5, \"dollarsPerEmerald\": " + written + "}");
			Money.DOLLARS_PER_EMERALD = WorkplaceConfig.load(dir).dollarsPerEmerald;
			return RealmTreasury.giveHint().getString();
		} catch (IOException e) {
			throw new GameTestAssertException("no temp dir: " + e);
		} finally {
			Money.DOLLARS_PER_EMERALD = before;
		}
	}

	private static String hint(String one, String stack) {
		return "Click to give " + one + " CobbleDollars (shift-click: " + stack + "), or click with emeralds";
	}

	/**
	 * The hint at the config's rates, both sides of the first comma (15: 960, 16: 1,024 a stack; 999 and 1,000 an
	 * emerald), the default 100 (6,400), the top 10,000 (640,000), and rates out of range clamped (0 reads as 1, 20000 as
	 * 10,000).
	 */
	//$ gametest 'FabricGameTest.EMPTY_STRUCTURE'
	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
	public void qaTheTreasuryHintGroupsItsNumbersAtEveryRate(GameTestHelper helper) {
		helper.assertTrue(Money.cobbleDollars(), "CobbleDollars isn't installed here; the test proves nothing");
		List<String> problems = new ArrayList<>();
		String[][] cases = {
			{"1", "1", "64"}, {"15", "15", "960"}, {"16", "16", "1,024"}, {"100", "100", "6,400"}, {"999", "999", "63,936"},
			{"1000", "1,000", "64,000"}, {"10000", "10,000", "640,000"}, {"0", "1", "64"}, {"20000", "10,000", "640,000"}};
		for (String[] c : cases) {
			String got = hintAt(c[0]);
			if (!got.equals(hint(c[1], c[2]))) {
				problems.add("at " + c[0] + ": \"" + got + "\"");
			}
		}
		helper.assertTrue(problems.isEmpty(), String.join("; ", problems));
		helper.succeed();
	}

	/** The hint's numbers are written as the other money lines write the same sums (Money.describe, B466). */
	//$ gametest 'FabricGameTest.EMPTY_STRUCTURE'
	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
	public void qaTheHintSaysTheSameNumberAsTheMoneyLines(GameTestHelper helper) {
		helper.assertTrue(Money.cobbleDollars(), "CobbleDollars isn't installed here; the test proves nothing");
		List<String> problems = new ArrayList<>();
		int before = Money.DOLLARS_PER_EMERALD;
		try {
			for (int rate : new int[] {7, 100, 250, 4321}) {
				Money.DOLLARS_PER_EMERALD = rate;
				String hint = RealmTreasury.giveHint().getString();
				String one = Money.describe(rate, 1).getString();
				String stack = Money.describe(64L * rate, 64).getString();
				if (!hint.contains("give " + one + " (shift-click: " + stack.replace(" CobbleDollars", "") + ")")) {
					problems.add("at " + rate + ": hint \"" + hint + "\", money lines \"" + one + "\" and \"" + stack + "\"");
				}
			}
		} finally {
			Money.DOLLARS_PER_EMERALD = before;
		}
		helper.assertTrue(problems.isEmpty(), String.join("; ", problems));
		helper.succeed();
	}

	/** A trainer's prize line at both sides of the first comma and at a million. */
	//$ gametest 'FabricGameTest.EMPTY_STRUCTURE'
	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
	public void qaAPrizeLineGroupsItsNumberFromAThousand(GameTestHelper helper) {
		helper.assertTrue(Money.cobbleDollars(), "CobbleDollars isn't installed here; the test proves nothing");
		Component mira = Component.literal("Mira");
		helper.assertValueEqual(Trainers.prizeLine(mira, 999, 10).getString(), "You beat Mira! +999 CobbleDollars", "999");
		helper.assertValueEqual(Trainers.prizeLine(mira, 1000, 10).getString(), "You beat Mira! +1,000 CobbleDollars", "1,000");
		helper.assertValueEqual(Trainers.prizeLine(mira, 1_000_000, 10).getString(), "You beat Mira! +1,000,000 CobbleDollars", "a million");
		helper.succeed();
	}
}
