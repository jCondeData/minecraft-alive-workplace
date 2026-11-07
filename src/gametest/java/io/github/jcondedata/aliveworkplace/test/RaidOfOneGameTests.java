package io.github.jcondedata.aliveworkplace.test;

import io.github.jcondedata.aliveworkplace.AliveWorkplace;
import io.github.jcondedata.aliveworkplace.guard.VillageRaids;
import io.github.jcondedata.aliveworkplace.hall.VillageHalls;
import io.github.jcondedata.aliveworkplace.threat.Culture;
import io.github.jcondedata.aliveworkplace.threat.Lairs;
import io.github.jcondedata.aliveworkplace.threat.Threats;
import io.github.jcondedata.aliveworkplace.work.Words;
import java.util.LinkedHashMap;
import java.util.Map;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;

/**
 * B824: a raid of one reads in the singular ("1 drowned pirate wades ashore", "1 of X's warband marches on"), in every
 * culture's chat line and chronicle line, named or not; a raid of three still reads in the plural.
 */
public class RaidOfOneGameTests {
	private static final String TALL = "aliveworkplace_test:tall_area";
	private static final Component TOWN = Component.literal("Birchgate");
	private static final Component CAPTAIN = Component.literal("Captain Brine");

	private static Lairs.Lair lair(String culture) {
		return new Lairs.Lair(BlockPos.ZERO, BlockPos.ZERO, null, 0, AliveWorkplace.id(culture), 8, 0, 0, 0, 0);
	}

	/** Every culture's lair line for one raider (as VillageRaids says it when a named lair's band of one sets out). */
	//$ gametest 'FabricGameTest.EMPTY_STRUCTURE'
	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
	public void aNamedRaidOfOneReadsSingularInEveryCulture(GameTestHelper helper) {
		Map<String, String> one = new LinkedHashMap<>();
		one.put("bandits", "1 of Captain Brine's bandits is raiding Birchgate!");
		one.put("monsters", "1 of Captain Brine's raiders is attacking Birchgate!");
		one.put("pillager_warband", "1 of Captain Brine's warband marches on Birchgate!");
		one.put("drowned_pirates", "1 of Captain Brine's drowned pirates wades ashore at Birchgate!");
		one.put("desert_raiders", "1 of Captain Brine's desert raiders marches on Birchgate out of the sand!");
		one.put("piglin_incursion", "1 of Captain Brine's piglins steps out of the portal and marches on Birchgate! Keep your gold out of its reach.");
		one.put("witch_coven", "1 of Captain Brine's coven creeps out of the swamp toward Birchgate! Her potions fly over walls: archers on the walls can reach her.");
		one.forEach((culture, expected) -> {
			String said = Lairs.message(lair(culture), "raid", true, 1, TOWN, CAPTAIN).getString();
			helper.assertTrue(said.equals(expected), culture + ", a raid of one: \"" + said + "\", expected \"" + expected + "\"");
			String three = Lairs.message(lair(culture), "raid", true, 3, TOWN, CAPTAIN).getString();
			helper.assertTrue(three.startsWith("3 of Captain Brine's") && !three.equals(expected.replace("1 of", "3 of")),
				culture + ", a raid of three should stay plural: \"" + three + "\"");
		});
		helper.succeed();
	}

	/** The lines of a raid with no lair behind it (the culture's own "raid" keys) and their chronicle lines. */
	//$ gametest 'FabricGameTest.EMPTY_STRUCTURE'
	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
	public void anUnnamedRaidOfOneReadsSingular(GameTestHelper helper) {
		Map<String, String[]> one = new LinkedHashMap<>();
		one.put("monsters", new String[]{"1 monster is raiding Birchgate!", "1 monster raided the village by night"});
		one.put("bandits", new String[]{"1 bandit is raiding Birchgate!", "1 bandit raided the village"});
		one.put("pillager_warband", new String[]{"1 of a pillager warband marches on Birchgate!", "1 of a pillager warband attacked the village"});
		one.put("drowned_pirates", new String[]{"1 drowned pirate wades ashore at Birchgate!", "1 drowned pirate came ashore and attacked the village"});
		one.put("desert_raiders", new String[]{"1 desert raider marches on Birchgate out of the sand!", "1 desert raider attacked the village"});
		one.put("piglin_incursion", new String[]{"1 piglin steps out of the portal and marches on Birchgate! Keep your gold out of its reach.",
			"1 piglin came through the portal and attacked the village"});
		one.put("witch_coven", new String[]{"1 of the coven creeps out of the swamp toward Birchgate! Her potions fly over walls: archers on the walls can reach her.",
			"1 of the coven's witches came out of the swamp and attacked the village"});
		one.forEach((id, expected) -> {
			Culture culture = Threats.get(AliveWorkplace.id(id)).orElse(null);
			helper.assertTrue(culture != null, "no culture " + id);
			String said = Words.countedFirst(culture.messages().key("raid", "message.aliveworkplace.raid.begins"), 1, TOWN).getString();
			String told = Words.countedFirst(culture.chronicle().key("raid", "chronicle.aliveworkplace.raid"), 1).getString();
			helper.assertTrue(said.equals(expected[0]), id + ": \"" + said + "\", expected \"" + expected[0] + "\"");
			helper.assertTrue(told.equals(expected[1]), id + " chronicle: \"" + told + "\", expected \"" + expected[1] + "\"");
		});
		String many = Words.countedFirst("message.aliveworkplace.pirates.raid", 4, TOWN).getString();
		helper.assertTrue(many.equals("4 drowned pirates wade ashore at Birchgate!"), "a raid of four: " + many);
		helper.succeed();
	}

	/** The bug as found (lane-d-1102-1008): a ship with one pirate left at home sends a raid of one. */
	//$ gametest_ticks_batch TALL '100' '"raidOfOnePirate"'
	@GameTest(template = TALL, timeoutTicks = 100, batch = "raidOfOnePirate")
	public void aRaidOfOnePirateSaysWades(GameTestHelper helper) {
		BlockPos hall = QaPiratesThreatLevelGameTests.coast(helper, 2);
		ServerLevel level = helper.getLevel();
		helper.runAfterDelay(2, () -> {
			QaPirates1102dGameTests.anchor(helper, hall, 86);
			Lairs.sent(level, hall, 7); // 7 out already: one at home to send
			QaPiratesThreatLevelGameTests.at(level, 86, 15000);
			VillageRaids.Raid raid = VillageRaids.start(level, hall, 52, 0, Double.NaN, QaPiratesThreatLevelGameTests.pirates(helper), RandomSource.create(5L));
			helper.assertTrue(raid != null && VillageRaids.raiders(level, hall).size() == 1, "a raid of one: " + VillageRaids.raiders(level, hall).size());
			Lairs.Lair now = Lairs.near(level, hall).orElseThrow();
			String chat = Lairs.message(now, "raid", true, 1, VillageHalls.name(level, hall), Lairs.captainName(now)).getString();
			String expected = "1 of " + Lairs.captainName(now).getString() + "'s drowned pirates wades ashore at " + VillageHalls.name(level, hall).getString() + "!";
			helper.assertTrue(chat.equals(expected), "the raid's chat line for one pirate: \"" + chat + "\", expected \"" + expected + "\"");
			helper.succeed();
		});
	}
}
