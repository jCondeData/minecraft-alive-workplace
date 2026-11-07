package io.github.jcondedata.aliveworkplace.compat;

import io.github.jcondedata.aliveworkplace.habitat.HabitatKeepers;
import io.github.jcondedata.aliveworkplace.registry.ModAttachments;
import io.github.jcondedata.aliveworkplace.work.Money;
import java.util.ArrayList;
import java.util.List;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.npc.Villager;

/**
 * QA, lane D, 2026-11-04: B466 (the Habitat Keeper's status counts each in its own form; CobbleDollars read with their
 * thousands marked), through her real work loop, with exact text from en_us.json, at 0, 1 and 2 and after a save and
 * load. The plain-mod half (B133, B136, B426) is in {@code QaLaneD1104GameTests}.
 */
public class QaLaneD1104CompatTests implements FabricGameTest {
	private static final String AREA = QaCampHabitatCompatTests.AREA;
	private static final BlockPos LOG_A = new BlockPos(8, 2, 2);
	private static final BlockPos LOG_B = new BlockPos(8, 2, 5);

	/** {@code spots} snacks set out and marked as hers, and {@code logs} slathered Saccharine logs she knows. */
	private static Villager keeperWith(GameTestHelper helper, List<BlockPos> spots, List<BlockPos> logs) {
		PartnerShowsCompatTests.clearLeftovers(helper);
		Villager keeper = HabitatKeeperCompatTests.keeper(helper);
		for (BlockPos spot : spots) {
			helper.setBlock(spot, BuiltInRegistries.BLOCK.get(QaCampHabitatCompatTests.c("poke_snack")));
			HabitatKeepers.addSpot(keeper, helper.absolutePos(spot));
		}
		List<BlockPos> known = new ArrayList<>();
		for (BlockPos log : logs) {
			helper.setBlock(log, BuiltInRegistries.BLOCK.get(QaCampHabitatCompatTests.c("saccharine_log_slathered")));
			helper.assertTrue(HabitatKeepers.isSlathered(helper.getBlockState(log)), "not a slathered log: " + helper.getBlockState(log));
			known.add(helper.absolutePos(log));
		}
		ModAttachments.HONEY_LOGS.set(keeper, List.copyOf(known));
		return keeper;
	}

	private static String said(GameTestHelper helper, Villager keeper) {
		return QaCampHabitatCompatTests.said(helper, keeper);
	}

	/** Two lure spots and one known log: "2 lure spots kept, 1 Saccharine log known", each count in its own form. */
	//$ gametest_ticks_batch AREA '1200' '"qa1104_habitat_2_1"'
	@GameTest(template = AREA, timeoutTicks = 1200, batch = "qa1104_habitat_2_1")
	public void qaB466TwoSpotsAndOneLogReadInTheirOwnForms(GameTestHelper helper) {
		Villager keeper = keeperWith(helper, List.of(new BlockPos(9, 2, 9), new BlockPos(11, 2, 9)), List.of(LOG_A));
		helper.succeedWhen(() -> {
			String line = said(helper, keeper);
			helper.assertTrue(line.equals("watching the wild Pokémon: 2 lure spots kept, 1 Saccharine log known"), "her line reads: " + line);
		});
	}

	/** One lure spot and no log known: zero is plural, "1 lure spot kept, 0 Saccharine logs known". */
	//$ gametest_ticks_batch AREA '1200' '"qa1104_habitat_1_0"'
	@GameTest(template = AREA, timeoutTicks = 1200, batch = "qa1104_habitat_1_0")
	public void qaB466OneSpotAndNoLogSayZeroLogs(GameTestHelper helper) {
		Villager keeper = keeperWith(helper, List.of(new BlockPos(9, 2, 9)), List.of());
		helper.succeedWhen(() -> {
			String line = said(helper, keeper);
			helper.assertTrue(line.equals("watching the wild Pokémon: 1 lure spot kept, 0 Saccharine logs known"), "her line reads: " + line);
		});
	}

	/**
	 * Saved and loaded (the keeper written to NBT and read into a new villager, as a chunk reload does): one spot and two
	 * logs still read "1 lure spot kept, 2 Saccharine logs known" from her loaded spots and logs.
	 */
	//$ gametest_ticks_batch AREA '2400' '"qa1104_habitat_reload"'
	@GameTest(template = AREA, timeoutTicks = 2400, batch = "qa1104_habitat_reload")
	public void qaB466HerCountsReadRightAfterASaveAndLoad(GameTestHelper helper) {
		Villager keeper = keeperWith(helper, List.of(new BlockPos(9, 2, 9)), List.of(LOG_A, LOG_B));
		ServerLevel level = helper.getLevel();
		Villager[] now = {keeper};
		boolean[] reloaded = {false};
		helper.succeedWhen(() -> {
			String line = said(helper, now[0]);
			helper.assertTrue(line.startsWith("watching the wild"), "not watching yet: " + line);
			if (!reloaded[0]) {
				CompoundTag saved = now[0].saveWithoutId(new CompoundTag());
				now[0].discard();
				Villager back = EntityType.VILLAGER.create(level);
				helper.assertTrue(back != null, "no villager");
				back.load(saved);
				level.addFreshEntity(back);
				now[0] = back;
				reloaded[0] = true;
				helper.assertTrue(HabitatKeepers.spots(back).size() == 1 && ModAttachments.HONEY_LOGS.getOrElse(back, List.of()).size() == 2,
					"lost on load: spots " + HabitatKeepers.spots(back) + ", logs " + ModAttachments.HONEY_LOGS.getOrElse(back, List.of()));
				helper.fail("reloaded; waiting for her line again");
			}
			helper.assertTrue(line.equals("watching the wild Pokémon: 1 lure spot kept, 2 Saccharine logs known"), "after a load her line reads: " + line);
		});
	}

	/** CobbleDollars from both sides of a thousand, and a million: "999", "1,000", "1,234,567"; and nothing, "0". */
	//$ gametest 'FabricGameTest.EMPTY_STRUCTURE'
	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
	public void qaB466CobbleDollarsMarkThousandsFromBothSides(GameTestHelper helper) {
		helper.assertTrue(Money.cobbleDollars(), "CobbleDollars isn't in the compat pack");
		List<String> wrong = new ArrayList<>();
		long[] sums = {0, 999, 1_000, 150_000, 1_234_567};
		String[] want = {"0 CobbleDollars", "999 CobbleDollars", "1,000 CobbleDollars", "150,000 CobbleDollars", "1,234,567 CobbleDollars"};
		for (int i = 0; i < sums.length; i++) {
			String got = Money.describe(sums[i], 0).getString();
			if (!got.equals(want[i])) {
				wrong.add("\"" + got + "\" (want \"" + want[i] + "\")");
			}
		}
		helper.assertTrue(wrong.isEmpty(), String.join("; ", wrong));
		helper.succeed();
	}
}
