package io.github.jcondedata.aliveworkplace.test;

import static io.github.jcondedata.aliveworkplace.test.QaLegendGuestsGameTests.AREA;
import static io.github.jcondedata.aliveworkplace.test.QaLegendGuestsGameTests.HALL;
import static io.github.jcondedata.aliveworkplace.test.QaLegendGuestsGameTests.PACE;
import static io.github.jcondedata.aliveworkplace.test.QaLegendGuestsGameTests.at;
import static io.github.jcondedata.aliveworkplace.test.QaLegendGuestsGameTests.hall;
import static io.github.jcondedata.aliveworkplace.test.QaLegendGuestsGameTests.legend;
import static io.github.jcondedata.aliveworkplace.test.QaLegendGuestsGameTests.setUp;
import static io.github.jcondedata.aliveworkplace.test.QaLegendGuestsGameTests.staged;

import io.github.jcondedata.aliveworkplace.blueprint.BlueprintData;
import io.github.jcondedata.aliveworkplace.blueprint.StarterBlueprints;
import io.github.jcondedata.aliveworkplace.build.BuildSiteManager;
import io.github.jcondedata.aliveworkplace.hall.Festivals;
import io.github.jcondedata.aliveworkplace.hall.VillageHallBlockEntity;
import io.github.jcondedata.aliveworkplace.hall.VillageRanks;
import io.github.jcondedata.aliveworkplace.inn.Innkeepers;
import io.github.jcondedata.aliveworkplace.legend.Legend;
import io.github.jcondedata.aliveworkplace.legend.LegendData;
import io.github.jcondedata.aliveworkplace.legend.LegendGuests;
import io.github.jcondedata.aliveworkplace.legend.LegendSlots;
import io.github.jcondedata.aliveworkplace.legend.Legends;
import io.github.jcondedata.aliveworkplace.registry.ModAttachments;
import io.github.jcondedata.aliveworkplace.registry.ModBlocks;
import io.github.jcondedata.aliveworkplace.registry.ModVillagers;
import io.github.jcondedata.aliveworkplace.work.Jobs;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.properties.BedPart;

/**
 * QA (lane d, 2026-10-27), written from the spec of 29.3 and 29.8, for what the earlier tests leave out: each place from
 * the side where the guest must NOT come (the inn with no free bed or in the afternoon, a festival one short of its
 * crowd, the Chapel off the full moon or not built), the Legends switch off, and a Mythic guest held to the village's
 * rank cap and heard by a player 5,000 blocks away. Every roster and clock change is made and undone within one tick.
 */
public class QaLegendCapsVisitsGameTests implements net.fabricmc.fabric.api.gametest.v1.FabricGameTest {
	private static Legend sure(String name, String place, String extra) {
		return Legends.read(QaLegendGuestsGameTests.id(name), com.google.gson.JsonParser.parseString("{\"rarity\": \"rare\", \"job\": \"minecraft:mason\", \"title\": \""
			+ name + "\", \"lore\": \"the lore\", \"names\": [\"Ada of " + name + "\"], \"arrive\": [{\"way\": \"visit\", \"place\": \"" + place
			+ "\", \"chance\": 1.0" + extra + "}], \"powers\": [" + PACE + "]}").getAsJsonObject());
	}

	private static void bed(GameTestHelper helper, BlockPos foot) {
		helper.setBlock(foot, Blocks.RED_BED.defaultBlockState().setValue(BedBlock.FACING, Direction.SOUTH).setValue(BedBlock.PART, BedPart.FOOT));
		helper.setBlock(foot.south(), Blocks.RED_BED.defaultBlockState().setValue(BedBlock.FACING, Direction.SOUTH).setValue(BedBlock.PART, BedPart.HEAD));
	}

	private static boolean isLegend(Villager guest, Legend legend) {
		LegendData data = guest == null ? null : ModAttachments.LEGEND.get(guest);
		return data != null && data.guest() && data.id().equals(legend.id());
	}

	/** The inn: no guest with no free bed, none in the afternoon; with a bed in the morning, the Legend instead of the traveller. */
	//$ gametest_ticks_batch AREA '100' '"qaCapsInn"'
	@GameTest(template = AREA, timeoutTicks = 100, batch = "qaCapsInn")
	public void qaInnOnlyWithAFreeBedAndInTheMorning(GameTestHelper helper) {
		List<BlueprintData.Placement> recorded = new ArrayList<>();
		setUp(helper, recorded);
		Legend inn = sure("qa_caps_inn_mason", "inn", "");
		helper.setBlock(new BlockPos(11, 2, 11), ModBlocks.INN_COUNTER);
		helper.runAfterDelay(2, () -> staged(helper, List.of(inn), () -> {
			ServerLevel level = helper.getLevel();
			BlockPos hallAt = helper.absolutePos(HALL);
			BlockPos counter = helper.absolutePos(new BlockPos(11, 2, 11));
			Villager innkeeper = helper.spawn(EntityType.VILLAGER, new BlockPos(12, 2, 12));
			innkeeper.setNoAi(true);
			Jobs.employ(level, innkeeper, counter, ModVillagers.INN_COUNTER_POI, ModVillagers.INNKEEPER);
			// the inn's bed search kept inside this test's area for this tick: beds other batches left nearby aren't this inn's
			int radius = Innkeepers.RADIUS;
			Innkeepers.RADIUS = 6;
			try {
				at(level, 1, 1000);
				String state = Innkeepers.tend(level, innkeeper, counter);
				helper.assertTrue("no_bed".equals(state), "an inn with no bed: " + state);
				helper.assertTrue(LegendGuests.guest(level, hallAt) == null, "a Legend came to an inn with no free bed");

				bed(helper, new BlockPos(13, 2, 13));
				at(level, 1, 7000); // the afternoon
				Innkeepers.tend(level, innkeeper, counter);
				helper.assertTrue(LegendGuests.guest(level, hallAt) == null, "a Legend came to the inn in the afternoon");

				at(level, 2, 1000);
				helper.assertTrue("hosting".equals(Innkeepers.tend(level, innkeeper, counter)), "the inn isn't hosting");
				Villager guest = LegendGuests.guest(level, hallAt);
				helper.assertTrue(isLegend(guest, inn) && ModAttachments.LEGEND.get(guest).way().equals("visit:inn"), "no inn guest with a bed in the morning: " + guest);
				helper.assertTrue(Innkeepers.guests(level, counter).isEmpty(), "a traveler came as well as the Legend");
			} finally {
				Innkeepers.RADIUS = radius;
			}
			helper.succeed();
		}));
	}

	/** A festival whose crowd is one short of the file's number brings nobody; one that reaches it brings the guest. */
	//$ gametest_ticks_batch AREA '100' '"qaCapsFestival"'
	@GameTest(template = AREA, timeoutTicks = 100, batch = "qaCapsFestival")
	public void qaFestivalCrowdOneShortThenEnough(GameTestHelper helper) {
		List<BlueprintData.Placement> recorded = new ArrayList<>();
		setUp(helper, recorded);
		Legend festival = sure("qa_caps_fest_mason", "festival", ", \"crowd\": 3");
		helper.setBlock(new BlockPos(3, 2, 9), Blocks.BELL);
		helper.runAfterDelay(2, () -> staged(helper, List.of(festival), () -> {
			ServerLevel level = helper.getLevel();
			BlockPos hallAt = helper.absolutePos(HALL);
			VillageHallBlockEntity entity = hall(helper, HALL);
			at(level, 1, Festivals.FIREWORKS);
			BlockPos square = Festivals.square(level, hallAt);
			List<Villager> crowd = new ArrayList<>();
			for (int i = 0; i < 2; i++) {
				Villager v = EntityType.VILLAGER.spawn(level, square.offset(i - 1, 0, 1), MobSpawnType.COMMAND);
				v.setNoAi(true);
				crowd.add(v);
			}
			Villager guest = Festivals.fireworks(level, hallAt, entity, square, RandomSource.create(41L));
			helper.assertTrue(entity.festivalCrowd() == 2, "the crowd counted: " + entity.festivalCrowd());
			helper.assertTrue(guest == null && LegendGuests.guest(level, hallAt) == null, "a Legend who wants a crowd of 3 came to one of 2");

			at(level, 2, Festivals.FIREWORKS);
			Villager third = EntityType.VILLAGER.spawn(level, square.offset(1, 0, 1), MobSpawnType.COMMAND);
			third.setNoAi(true);
			guest = Festivals.fireworks(level, hallAt, entity, square, RandomSource.create(42L));
			helper.assertTrue(entity.festivalCrowd() == 3, "the crowd counted: " + entity.festivalCrowd());
			helper.assertTrue(isLegend(guest, festival) && ModAttachments.LEGEND.get(guest).way().equals("visit:festival"),
				"no guest at a festival that reached its crowd of 3: " + guest);
			helper.succeed();
		}));
	}

	/** The Chapel: no guest at midnight off the full moon, none at a full moon with no finished Chapel; both, the guest. */
	//$ gametest_ticks_batch AREA '100' '"qaCapsChapel"'
	@GameTest(template = AREA, timeoutTicks = 100, batch = "qaCapsChapel")
	public void qaChapelOnlyAtAFullMoonAndAFinishedChapel(GameTestHelper helper) {
		List<BlueprintData.Placement> recorded = new ArrayList<>();
		setUp(helper, recorded);
		Legend chapel = sure("qa_caps_chapel_mason", "chapel", "");
		helper.runAfterDelay(2, () -> staged(helper, List.of(chapel), () -> {
			ServerLevel level = helper.getLevel();
			BlockPos hallAt = helper.absolutePos(HALL);
			// a full moon (day 1), midnight, no Chapel
			at(level, 1, 18000);
			helper.assertTrue(level.getMoonPhase() == 0, "day 1 isn't a full moon: " + level.getMoonPhase());
			LegendGuests.round(level, hallAt, RandomSource.create(43L));
			helper.assertTrue(LegendGuests.guest(level, hallAt) == null, "a Chapel guest with no Chapel");

			BlueprintData.Placement placement = new BlueprintData.Placement(level.dimension().location(), helper.absolutePos(new BlockPos(0, 1, 0)),
				Rotation.NONE, Mirror.NONE);
			BuildSiteManager.get(level).recordFinished(StarterBlueprints.CHAPEL.id(), placement, UUID.randomUUID());
			recorded.add(placement);
			helper.assertTrue(LegendGuests.chapel(level, hallAt).isPresent(), "the finished Chapel isn't found");
			// midnight on day 2: a waning moon
			at(level, 2, 18000);
			helper.assertTrue(level.getMoonPhase() != 0, "day 2 is a full moon");
			LegendGuests.round(level, hallAt, RandomSource.create(44L));
			helper.assertTrue(LegendGuests.guest(level, hallAt) == null, "a Chapel guest off the full moon");
			// the next full moon (day 9), midnight
			at(level, 9, 18000);
			helper.assertTrue(level.getMoonPhase() == 0, "day 9 isn't a full moon: " + level.getMoonPhase());
			LegendGuests.round(level, hallAt, RandomSource.create(45L));
			Villager guest = LegendGuests.guest(level, hallAt);
			helper.assertTrue(isLegend(guest, chapel) && ModAttachments.LEGEND.get(guest).way().equals("visit:chapel"), "no Chapel guest at the full moon: " + guest);
			helper.succeed();
		}));
	}

	/** Legends switched off: no visit at any place, the hall's round brings nobody, and a guest already there isn't one. */
	//$ gametest_ticks_batch AREA '100' '"qaCapsOff"'
	@GameTest(template = AREA, timeoutTicks = 100, batch = "qaCapsOff")
	public void qaLegendsOffBringsNoGuest(GameTestHelper helper) {
		List<BlueprintData.Placement> recorded = new ArrayList<>();
		setUp(helper, recorded);
		Legend plain = sure("qa_caps_off_mason", "hall", "");
		helper.runAfterDelay(2, () -> staged(helper, List.of(plain), () -> {
			ServerLevel level = helper.getLevel();
			BlockPos hallAt = helper.absolutePos(HALL);
			boolean was = Legends.ENABLED;
			try {
				Legends.ENABLED = false;
				at(level, 1, 1000);
				LegendGuests.round(level, hallAt, RandomSource.create(46L));
				helper.assertTrue(LegendGuests.guest(level, hallAt) == null, "a guest came with Legends off");
				for (String place : LegendGuests.PLACES) {
					helper.assertTrue(LegendGuests.visit(level, hallAt, place, hallAt, RandomSource.create(47L)) == null, "a " + place + " guest with Legends off");
				}
				helper.assertTrue(!Legends.canCome(level, hallAt, plain), "canCome says yes with Legends off");
				helper.assertTrue(hall(helper, HALL).legendGuests().rolled().isEmpty(), "a day's roll was spent with Legends off");
				// switched back on, the same morning's roll is still there to have
				Legends.ENABLED = true;
				Villager guest = LegendGuests.visit(level, hallAt, "hall", hallAt, RandomSource.create(48L));
				helper.assertTrue(isLegend(guest, plain), "no guest once Legends are back on");
				Legends.ENABLED = false;
				helper.assertTrue(!LegendGuests.isGuest(guest) && LegendGuests.guest(level, hallAt) == null, "still a guest with Legends off");
			} finally {
				Legends.ENABLED = was;
			}
			helper.succeed();
		}));
	}

	/**
	 * A Mythic guest is held to the rank's cap (none to a Village, one to a Town) through the visit itself, and their
	 * coming is heard by a player 5,000 blocks away, in gold, where a Rare guest's isn't.
	 */
	//$ gametest_ticks_batch AREA '100' '"qaCapsMythicGuest"'
	@GameTest(template = AREA, timeoutTicks = 100, batch = "qaCapsMythicGuest")
	public void qaMythicGuestFollowsTheRankCapAndIsHeardFarAway(GameTestHelper helper) {
		List<BlueprintData.Placement> recorded = new ArrayList<>();
		setUp(helper, recorded);
		Legend mythic = legend("qa_caps_myth_mason", "mythic", ", \"chance\": 1.0", "", PACE);
		Legend rare = sure("qa_caps_rare_mason", "hall", "");
		ServerPlayer player = helper.makeMockServerPlayerInLevel();
		Leftovers.after(helper, () -> helper.getLevel().getServer().getPlayerList().remove(player));
		helper.runAfterDelay(2, () -> {
			int[] caps = Legends.MYTHIC_CAP;
			try {
				Legends.MYTHIC_CAP = new int[] {0, 0, 1, 2};
				staged(helper, List.of(mythic), () -> {
					ServerLevel level = helper.getLevel();
					BlockPos hallAt = helper.absolutePos(HALL);
					VillageHallBlockEntity entity = hall(helper, HALL);
					player.setPos(hallAt.getX() + 5000, hallAt.getY(), hallAt.getZ());
					at(level, 1, 1000);
					entity.setRank(VillageRanks.Rank.VILLAGE);
					helper.assertTrue(LegendGuests.visit(level, hallAt, "hall", hallAt, RandomSource.create(49L)) == null, "a Mythic guest came to a Village (cap 0)");
					entity.setRank(VillageRanks.Rank.TOWN);
					Villager guest = LegendGuests.visit(level, hallAt, "hall", hallAt, RandomSource.create(50L));
					helper.assertTrue(isLegend(guest, mythic), "no Mythic guest in a Town (cap 1): " + guest);
					helper.assertTrue(LegendSlots.announce(level, hallAt, guest, mythic, true).contains(player), "a Mythic guest wasn't heard 5,000 blocks away");
					String text = LegendSlots.message(level, hallAt, guest.blockPosition(), guest.getDisplayName(), mythic, true).getString();
					helper.assertTrue(text.startsWith("A Mythic Legend! Ada of qa_caps_myth_mason, the qa_caps_myth_mason, has come to ")
						&& text.contains(" as a guest, ") && text.contains(" blocks ") && text.endsWith(" of spawn."), "the Mythic guest line: " + text);
				});
				staged(helper, List.of(rare), () -> {
					ServerLevel level = helper.getLevel();
					BlockPos hallAt = helper.absolutePos(HALL);
					Villager mythicGuest = LegendGuests.guest(level, hallAt);
					if (mythicGuest != null) {
						LegendGuests.leave(level, hallAt, mythicGuest);
					}
					at(level, 2, 1000);
					Villager guest = LegendGuests.visit(level, hallAt, "hall", hallAt, RandomSource.create(51L));
					helper.assertTrue(isLegend(guest, rare), "no Rare guest: " + guest);
					helper.assertTrue(!LegendSlots.announce(level, hallAt, guest, rare, true).contains(player), "a Rare guest was heard 5,000 blocks away");
				});
			} finally {
				Legends.MYTHIC_CAP = caps;
			}
			helper.succeed();
		});
	}
}
