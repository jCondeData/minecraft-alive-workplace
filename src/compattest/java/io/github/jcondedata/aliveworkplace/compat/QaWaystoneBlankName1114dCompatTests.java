package io.github.jcondedata.aliveworkplace.compat;

import io.github.jcondedata.aliveworkplace.hall.VillageHallBlockEntity;
import io.github.jcondedata.aliveworkplace.hall.VillageHalls;
import io.github.jcondedata.aliveworkplace.hall.VillageWaystones;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.Blocks;

/**
 * QA (lane D, 2026-11-14), B1229's expectation carried to the waystone network (41.1, 41.2): "a name a player can read; a
 * blank saved or custom name counts as missing and falls back to the made-up name". A hall with a blank custom name (a
 * name tag with an empty name, only from commands) names its stone, and reads on the hall's Map, by its made-up name.
 * Uses {@link WaystonesCompatTests}' plaza and hall.
 */
public class QaWaystoneBlankName1114dCompatTests implements FabricGameTest {
	private static final String AREA = CompatGameTests.AREA;
	private static final String LINE = "screen.aliveworkplace.hall.waystone";

	private static void cleanUp(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		PartnerShowsCompatTests.after(helper, () -> {
			io.github.jcondedata.aliveworkplace.build.BuildSiteManager.get(level).forgetFinished(WaystonesCompatTests.placement(helper));
			VillageWaystones.Data.get(level).forget(helper.absolutePos(WaystonesCompatTests.HALL));
			level.setBlock(WaystonesCompatTests.stone(helper).above(), Blocks.AIR.defaultBlockState(), 3);
			level.setBlock(WaystonesCompatTests.stone(helper), Blocks.AIR.defaultBlockState(), 3);
			level.removeBlock(helper.absolutePos(WaystonesCompatTests.HALL), false);
		});
	}

	/** The Map's waystone lines on the hall's screen, as the player reads them. */
	private static List<String> mapLines(GameTestHelper helper, ServerPlayer player, BlockPos hall) {
		return WaystoneQaCompatTests.texts(WaystoneQaCompatTests.mapLore(player, hall), LINE);
	}

	/**
	 * The hall named with an empty name (and one of spaces), its plaza's stone standing: the stone carries the made-up
	 * name, never a blank one, at the round and the moment the hall is renamed; and the Map's "this village" line reads it.
	 */
	//$ gametest_batch AREA '"qaWaystoneBlankName1114dHere"'
	@GameTest(template = AREA, batch = "qaWaystoneBlankName1114dHere")
	public void aHallWithABlankNameNamesItsStoneWithTheMadeUpName(GameTestHelper helper) {
		RaidHelperCompatTests.clearAround(helper); // B541: halls and stones earlier batches left nearby
		VillageHalls.forgetNearest();
		ServerLevel level = helper.getLevel();
		cleanUp(helper);
		BlockPos at = helper.absolutePos(WaystonesCompatTests.HALL);
		String made = VillageHalls.madeUpName(at).getString();
		helper.assertTrue(!made.isBlank() && !made.contains("village_name."), "setup: the made-up name doesn't read: '" + made + "'");
		VillageHallBlockEntity hall = WaystonesCompatTests.hall(helper, "Oldmere");
		WaystonesCompatTests.paste(helper, VillageWaystones.PLAZA, UUID.randomUUID());
		VillageWaystones.round(level, at);
		helper.assertTrue(WaystonesCompatTests.nameAt(helper).equals(Optional.of("Oldmere")),
			"setup: the stone after the round: " + WaystonesCompatTests.nameAt(helper) + " " + WaystonesCompatTests.debug(helper));
		hall.setCustomName(Component.literal("")); // a name tag with an empty name
		helper.assertTrue(WaystonesCompatTests.nameAt(helper).equals(Optional.of(made)),
			"the hall was given an empty name and its stone is now called '" + WaystonesCompatTests.nameAt(helper).orElse("(none)") + "', not the made-up " + made);
		helper.assertTrue(VillageWaystones.stoneName(level, at).equals(made), "the stone's name for a hall with an empty name is '" + VillageWaystones.stoneName(level, at) + "'");
		hall.setCustomName(Component.literal("   "));
		VillageWaystones.round(level, at);
		helper.assertTrue(WaystonesCompatTests.nameAt(helper).equals(Optional.of(made)),
			"a hall named with spaces gives its stone the name '" + WaystonesCompatTests.nameAt(helper).orElse("(none)") + "', not the made-up " + made);
		ServerPlayer player = helper.makeMockServerPlayerInLevel();
		List<String> lines = mapLines(helper, player, at);
		helper.assertTrue(lines.stream().anyMatch(l -> l.startsWith(made + ", this village: ")),
			"the Map doesn't list the village by its made-up name: " + lines);
		helper.assertFalse(lines.stream().anyMatch(l -> l.startsWith(",") || l.startsWith(" ")), "the Map lists a village with no name: " + lines);
		helper.succeed();
	}
}
