package io.github.jcondedata.aliveworkplace.test;

import io.github.jcondedata.aliveworkplace.hall.Chronicle;
import io.github.jcondedata.aliveworkplace.hall.CivicEffects;
import io.github.jcondedata.aliveworkplace.hall.EdictBook;
import io.github.jcondedata.aliveworkplace.hall.VillageHallBlockEntity;
import io.github.jcondedata.aliveworkplace.hall.VillageHallScreen;
import io.github.jcondedata.aliveworkplace.registry.ModBlocks;
import java.util.List;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;

/**
 * QA (lane-e-1031-2213, the sentences a player reads, counted sentences 24.5). Found by
 * {@link QaHallWordsAtExtremes1031GameTests}, which reads every line of the Village Hall two clicks deep:
 * <ul>
 * <li>the hall's Chronicle button says "What has happened here (1 entries): click to read" in a village whose
 * chronicle holds one entry ({@code screen.aliveworkplace.hall.chronicle_hint} has no {@code .one} form);</li>
 * <li>an edict, class or guild perk that speeds up exactly one trade reads "+ 1 trades work 50% faster"
 * ({@code screen.aliveworkplace.edicts.effect.pace_jobs}; data files only today: the shipped files name no job or ten,
 * the test pack's Builders' Drill names one).</li>
 * </ul>
 */
public class QaHallCountsOne1031BugTests implements FabricGameTest {
	private static final String AREA = "aliveworkplace_test:build_area";
	private static final BlockPos HALL = new BlockPos(3, 2, 3);

	//$ gametest_ticks_batch AREA '100' '"qaHallCountsOne1031"'
	@GameTest(template = AREA, timeoutTicks = 100, batch = "qaHallCountsOne1031")
	public void aChronicleOfOneEntryReadsInTheSingular(GameTestHelper helper) {
		Leftovers.clear(helper);
		Leftovers.halls(helper);
		ServerPlayer player = ProtectionSpecGameTests.player(helper);
		helper.setBlock(HALL, ModBlocks.VILLAGE_HALL);
		BlockPos hall = helper.absolutePos(HALL);
		player.setPos(hall.getX() + 0.5, hall.getY(), hall.getZ() + 3.5);
		VillageHallBlockEntity entity = helper.getBlockEntity(HALL);
		entity.setOwner(player.getUUID(), "Owner");
		helper.runAfterDelay(2, () -> {
			int before = entity.chronicle().size();
			for (int i = before; i < 1; i++) {
				Chronicle.record(helper.getLevel(), hall, Chronicle.Kind.BUILT, Component.literal("A well was dug"));
			}
			helper.assertTrue(entity.chronicle().size() == 1, "setup: the chronicle holds " + entity.chronicle().size() + " entries, not 1");
			List<String> one = RealmGameTests.lore(VillageHallScreen.forTest(player, hall).icon(VillageHallScreen.CHRONICLE));
			Chronicle.record(helper.getLevel(), hall, Chronicle.Kind.BUILT, Component.literal("A road was laid"));
			List<String> two = RealmGameTests.lore(VillageHallScreen.forTest(player, hall).icon(VillageHallScreen.CHRONICLE));
			helper.assertTrue(two.contains("What has happened here (2 entries): click to read"), "the control, two entries: " + two);
			helper.assertTrue(one.contains("What has happened here (1 entry): click to read"), "a chronicle of one entry: the hall's button reads " + one);
			helper.succeed();
		});
	}

	//$ gametest_batch 'FabricGameTest.EMPTY_STRUCTURE' '"qaHallCountsOne1031"'
	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE, batch = "qaHallCountsOne1031")
	public void aPaceForOneTradeReadsInTheSingular(GameTestHelper helper) {
		ResourceLocation builder = ResourceLocation.fromNamespaceAndPath("aliveworkplace", "builder");
		ResourceLocation mason = ResourceLocation.fromNamespaceAndPath("minecraft", "mason");
		String two = EdictBook.describe(new CivicEffects.WorkPace(50, List.of(builder, mason))).getString();
		String one = EdictBook.describe(new CivicEffects.WorkPace(50, List.of(builder))).getString();
		helper.assertTrue(two.equals("2 trades work 50% faster"), "the control, two trades: \"" + two + "\"");
		helper.assertTrue(!one.startsWith("1 trades"), "a pace for one trade (the test pack's Builders' Drill) reads \"" + one + "\"");
		helper.succeed();
	}
}
