package io.github.jcondedata.aliveworkplace.test;

import io.github.jcondedata.aliveworkplace.build.BuildSite;
import io.github.jcondedata.aliveworkplace.build.Builders;
import io.github.jcondedata.aliveworkplace.camp.SettlersWagonItem;
import io.github.jcondedata.aliveworkplace.colony.ColonyArrival;
import io.github.jcondedata.aliveworkplace.wonder.WonderManager;
import io.github.jcondedata.aliveworkplace.wonder.WonderSite;
import java.util.List;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.levelgen.structure.BoundingBox;

/**
 * QA (qa-lane-b-1027-0638): adversarial checks of two landed fixes, written from the spec.
 * <ul>
 * <li>B424: the Settler's Wagon camp's chest of supplies and the Colony camp's barrels open where they stand. Beyond the
 * original ray-pick tests: the Colony camp turned a quarter either way still has every container reachable. (A third
 * test, the chest really opening for a player's click, fails: the wagon's wool cover sits on the chest; it is on branch
 * tests/camp-chest-open-1027.)</li>
 * <li>B467: when a Wonder crew's lead dies or is retrained mid-stage, the member who took the part over is named. Beyond
 * the original tests: the lead dying twice in a row (the name handed on again) and surviving a restart; and a crew of
 * one whose only builder dies (no lead, then the next builder to come is named).</li>
 * </ul>
 */
public class QaBuilderFixes1027cGameTests {
	private static final String AREA = "aliveworkplace_test:big_area";
	private static final String HUGE = "aliveworkplace_test:huge_area";
	private static final String TALL = "aliveworkplace_test:tall_area";

	// ---- B424 ----

	/** The Colony camp turned a quarter clockwise: every chest and barrel still opens where it stands. */
	//$ gametest_ticks_batch HUGE '40' '"qaB424ColonyCw"'
	@GameTest(template = HUGE, timeoutTicks = 40, batch = "qaB424ColonyCw")
	public void theColonyCampTurnedClockwiseStillOpens(GameTestHelper helper) {
		colonyTurned(helper, Rotation.CLOCKWISE_90);
	}

	/** The Colony camp turned a quarter counter-clockwise: every chest and barrel still opens where it stands. */
	//$ gametest_ticks_batch HUGE '40' '"qaB424ColonyCcw"'
	@GameTest(template = HUGE, timeoutTicks = 40, batch = "qaB424ColonyCcw")
	public void theColonyCampTurnedCounterClockwiseStillOpens(GameTestHelper helper) {
		colonyTurned(helper, Rotation.COUNTERCLOCKWISE_90);
	}

	private static void colonyTurned(GameTestHelper helper, Rotation rotation) {
		Leftovers.clear(helper);
		ServerLevel level = helper.getLevel();
		ServerPlayer player = helper.makeMockServerPlayerInLevel();
		try {
			// centre the turned camp in the area, whichever way its box grows from the front
			BlockPos probe = helper.absolutePos(new BlockPos(15, 2, 15));
			BoundingBox at = SettlersWagonItem.campBox(level, ColonyArrival.COLONY_CAMP, probe, rotation);
			helper.assertTrue(at != null, "the colony camp's template is missing");
			BlockPos centre = helper.absolutePos(new BlockPos(15, 2, 15));
			BlockPos front = probe.offset(centre.getX() - (at.minX() + at.maxX()) / 2, 0, centre.getZ() - (at.minZ() + at.maxZ()) / 2);
			BoundingBox box = SettlersWagonItem.placeCamp(level, ColonyArrival.COLONY_CAMP, front, rotation);
			helper.assertTrue(box != null, "the colony camp wasn't placed");
			List<String> shut = QaCampReachGameTests.unopenable(helper, player, box);
			helper.assertTrue(shut.isEmpty(), "in the colony camp turned " + rotation + " no player can open: " + shut);
		} finally {
			level.getServer().getPlayerList().remove(player);
		}
		helper.succeed();
	}

	// ---- B467 ----

	/**
	 * The lead dies, the member who took over is named; then that new lead dies too and the last member is named; and the
	 * name survives the Wonders being read back from their save (a restart).
	 */
	//$ gametest_ticks_batch TALL '160' '"qaB467LeadDiesTwice"'
	@GameTest(template = TALL, timeoutTicks = 160, batch = "qaB467LeadDiesTwice")
	public void aLeadWhoDiesTwiceIsHandedOnAgainAndSurvivesARestart(GameTestHelper helper) {
		QaWonderCrewGameTests.Village v = QaWonderCrewGameTests.village(helper, 3, 4);
		String[] names = {"Builder0", "Builder1", "Builder2"};
		for (int i = 0; i < 3; i++) {
			v.builders().get(i).setCustomName(Component.literal(names[i]));
		}
		ServerLevel level = v.level();
		WonderSite site = QaWonderCrewGameTests.begin(helper, v, v.builders().get(0));
		BuildSite part = Builders.activeSite(level, v.builders().get(0));
		helper.assertTrue(part != null, "the lead didn't start stage I");
		for (int i = 1; i < 3; i++) {
			v.builders().get(i).tickCount = 20;
			helper.assertTrue(Builders.activeSite(level, v.builders().get(i)) != null, "builder " + i + " didn't join");
		}
		v.builders().get(0).kill();
		helper.runAfterDelay(40, () -> {
			String first = QaWonderCrewGameTests.stoneLead(level, QaWonderCrewGameTests.now(level, site.id()));
			helper.assertTrue(first.equals("Builder1") || first.equals("Builder2"), "after the first death the stone names '" + first + "'");
			Villager second = v.builders().get(first.equals("Builder1") ? 1 : 2);
			Villager last = v.builders().get(first.equals("Builder1") ? 2 : 1);
			second.kill();
			helper.runAfterDelay(40, () -> {
				String named = QaWonderCrewGameTests.stoneLead(level, QaWonderCrewGameTests.now(level, site.id()));
				String want = last.getCustomName().getString();
				helper.assertTrue(named.equals(want), "after the new lead died too the stone names '" + named + "', not the last member " + want);
				String card = QaWonderCrewGameTests.hallCard(level, v.hall());
				helper.assertTrue(card.contains(want), "after the new lead died too the hall's card doesn't name " + want + ": " + card);
				var registries = level.registryAccess();
				level.getDataStorage().set(WonderManager.NAME, WonderManager.load(WonderManager.get(level).save(new CompoundTag(), registries), registries));
				WonderSite back = QaWonderCrewGameTests.now(level, site.id());
				helper.assertTrue(back != null && back.builder().equals(java.util.Optional.of(last.getUUID())),
					"after a restart the Wonder's lead is " + (back == null ? "gone" : back.builder()) + ", not " + want);
				String after = QaWonderCrewGameTests.stoneLead(level, back);
				helper.assertTrue(after.equals(want), "after a restart the stone names '" + after + "', not " + want);
				helper.succeed();
			});
		});
	}

	/**
	 * A crew of one: the only builder dies mid-stage, so nobody leads it; the next builder employed in the village who comes
	 * to the Wonder takes the part and is named its lead on the stone.
	 */
	//$ gametest_ticks_batch TALL '120' '"qaB467SoloLeadDies"'
	@GameTest(template = TALL, timeoutTicks = 120, batch = "qaB467SoloLeadDies")
	public void aCrewOfOneWhoseBuilderDiesIsLedByTheNextBuilderToCome(GameTestHelper helper) {
		QaWonderCrewGameTests.Village v = QaWonderCrewGameTests.village(helper, 1, 4);
		Villager only = v.builders().get(0);
		only.setCustomName(Component.literal("Solo"));
		ServerLevel level = v.level();
		WonderSite site = QaWonderCrewGameTests.begin(helper, v, only);
		BuildSite part = Builders.activeSite(level, only);
		helper.assertTrue(part != null, "the lead didn't start stage I");
		UUID dead = only.getUUID();
		only.kill();
		helper.runAfterDelay(40, () -> {
			WonderSite now = QaWonderCrewGameTests.now(level, site.id());
			helper.assertTrue(now != null, "the Wonder vanished when its only builder died");
			helper.assertTrue(!now.builder().equals(java.util.Optional.of(dead)), "the dead builder is still the Wonder's lead");
			Villager newcomer = QaWonderCrewGameTests.builder(helper, v, new BlockPos(9, 2, 28), new BlockPos(9, 2, 26));
			newcomer.setCustomName(Component.literal("Newcomer"));
			newcomer.tickCount = 20;
			BuildSite took = Builders.activeSite(level, newcomer);
			helper.assertTrue(took != null && took.id().equals(part.id()), "the next builder didn't take the Wonder's part: " + (took == null ? "nothing" : took.id()));
			String named = QaWonderCrewGameTests.stoneLead(level, QaWonderCrewGameTests.now(level, site.id()));
			helper.assertTrue(named.equals("Newcomer"), "the stone names '" + named + "', not the builder who now leads the part");
			helper.succeed();
		});
	}
}
