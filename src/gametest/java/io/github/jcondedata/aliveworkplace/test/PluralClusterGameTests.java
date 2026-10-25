package io.github.jcondedata.aliveworkplace.test;

import io.github.jcondedata.aliveworkplace.craft.JobShortages;
import io.github.jcondedata.aliveworkplace.work.Words;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;

/**
 * B444, B445, B461, B466, B469, B471, B472 and B426 (kin of B413): lines whose count is often exactly one read "1
 * expedition", "1 villager", "1 meal", never "1 expeditions". Each line goes through {@link Words} the way its code does
 * (counted, countedFirst, countedTwo or countedIfAny), at 1 and at 2, and the real English is checked.
 */
public class PluralClusterGameTests implements FabricGameTest {
	private static void check(List<String> wrong, String got, String want) {
		if (!got.equals(want)) {
			wrong.add("\"" + got + "\" (want \"" + want + "\")");
		}
	}

	/** B444: the counting titles above workers' heads, and the state lines that count, at 1 and 2. */
	//$ gametest 'FabricGameTest.EMPTY_STRUCTURE'
	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
	public void workerTitlesCountOneInTheSingular(GameTestHelper helper) {
		List<String> wrong = new ArrayList<>();
		Map<String, String[]> lines = new LinkedHashMap<>(); // key -> {at 1, at 2}; the count is the first argument
		lines.put("message.aliveworkplace.netherworker.title", new String[] {"Netherworker · 1 expedition", "Netherworker · 2 expeditions"});
		lines.put("message.aliveworkplace.explorer.title", new String[] {"Explorer · 1 expedition", "Explorer · 2 expeditions"});
		lines.put("message.aliveworkplace.teacher.title", new String[] {"Teacher · 1 pupil taught", "Teacher · 2 pupils taught"});
		lines.put("message.aliveworkplace.beekeeper.title", new String[] {"Beekeeper · 1 harvest", "Beekeeper · 2 harvests"});
		lines.put("message.aliveworkplace.lumberjack.title", new String[] {"Lumberjack · 1 tree", "Lumberjack · 2 trees"});
		lines.put("message.aliveworkplace.innkeeper.title", new String[] {"Innkeeper · 1 guest hosted", "Innkeeper · 2 guests hosted"});
		lines.put("message.aliveworkplace.daycare_keeper.title", new String[] {"Daycare Keeper · 1 pair", "Daycare Keeper · 2 pairs"});
		lines.put("message.aliveworkplace.camp_cook.title", new String[] {"Camp Cook · 1 dish cooked", "Camp Cook · 2 dishes cooked"});
		lines.put("message.aliveworkplace.alchemist.title", new String[] {"Cleric · 1 potion brewed", "Cleric · 2 potions brewed"});
		lines.put("message.aliveworkplace.herder.title", new String[] {"Butcher · 1 bucket of milk", "Butcher · 2 buckets of milk"});
		lines.put("message.aliveworkplace.florist.title", new String[] {"Florist · 1 flower grown", "Florist · 2 flowers grown"});
		lines.put("message.aliveworkplace.berry_breeder.title", new String[] {"Berry Breeder · 1 berry picked", "Berry Breeder · 2 berries picked"});
		lines.put("message.aliveworkplace.gem_grower.title", new String[] {"Gem Grower · 1 cluster picked", "Gem Grower · 2 clusters picked"});
		lines.put("message.aliveworkplace.smelter.title", new String[] {"Armorer · 1 ingot smelted", "Armorer · 2 ingots smelted"});
		lines.put("message.aliveworkplace.weaponsmith_crafting.title", new String[] {"Weaponsmith · 1 sword made", "Weaponsmith · 2 swords made"});
		lines.put("message.aliveworkplace.innkeeper.state.hosting", new String[] {"1 guest staying", "2 guests staying"});
		lines.put("message.aliveworkplace.innkeeper.state.full", new String[] {"the inn is full (1 guest)", "the inn is full (2 guests)"});
		lines.put("message.aliveworkplace.daycare_keeper.state.watching", new String[] {"watching 1 pair in the pasture", "watching 2 pairs in the pasture"});
		lines.put("message.aliveworkplace.explorer.state.home", new String[] {"Heading home with 1 find", "Heading home with 2 finds"});
		lines.forEach((key, want) -> {
			check(wrong, Words.countedFirst(key, 1).getString(), want[0]);
			check(wrong, Words.countedFirst(key, 2).getString(), want[1]);
		});
		// a title with no count word keeps its one form (countedFirst falls back): "Carpenter · 1 made"
		check(wrong, Words.countedFirst("message.aliveworkplace.carpenter.title", 1).getString(), "Carpenter · 1 made");
		String eggs = "message.aliveworkplace.daycare_keeper.state.eggs";
		check(wrong, Words.countedTwo(eggs, 1, 1, 1, 1).getString(), "watching 1 pair: 1 egg waiting");
		check(wrong, Words.countedTwo(eggs, 1, 3, 1, 3).getString(), "watching 1 pair: 3 eggs waiting");
		check(wrong, Words.countedTwo(eggs, 2, 1, 2, 1).getString(), "watching 2 pairs: 1 egg waiting");
		check(wrong, Words.countedTwo(eggs, 2, 3, 2, 3).getString(), "watching 2 pairs: 3 eggs waiting");
		helper.assertTrue(wrong.isEmpty(), String.join("; ", wrong));
		helper.succeed();
	}

	/** B445, B461, B469: hall, golem, mail, chronicle, sickness, bank, Wonder and City Plan lines at 1 and 2. */
	//$ gametest 'FabricGameTest.EMPTY_STRUCTURE'
	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
	public void chatAndHallLinesCountOneInTheSingular(GameTestHelper helper) {
		List<String> wrong = new ArrayList<>();
		check(wrong, Words.counted("message.aliveworkplace.hall.recalled", 1, 1).getString(), "1 villager came home");
		check(wrong, Words.counted("message.aliveworkplace.hall.recalled", 2, 2).getString(), "2 villagers came home");
		check(wrong, Words.counted("screen.aliveworkplace.hall.golem.hauling", 1, 1).getString(), "carrying 1 item to the Storehouse");
		check(wrong, Words.counted("screen.aliveworkplace.hall.golem.hauling", 2, 2).getString(), "carrying 2 items to the Storehouse");
		check(wrong, Words.counted("screen.aliveworkplace.hall.golem.harvesting", 1, 1).getString(), "carrying 1 item of harvest");
		check(wrong, Words.counted("screen.aliveworkplace.hall.golem.harvesting", 2, 2).getString(), "carrying 2 items of harvest");
		check(wrong, Words.counted("message.aliveworkplace.mail.track_to", 1, "Alex", 1, "on its way").getString(), "To Alex: 1 item, on its way");
		check(wrong, Words.counted("message.aliveworkplace.mail.track_to", 2, "Alex", 2, "on its way").getString(), "To Alex: 2 items, on its way");
		check(wrong, Words.counted("message.aliveworkplace.mail.track_from", 1, "Alex", 1, "on its way").getString(), "From Alex: 1 item, on its way");
		check(wrong, Words.counted("message.aliveworkplace.mail.track_from", 2, "Alex", 2, "on its way").getString(), "From Alex: 2 items, on its way");
		check(wrong, Words.countedIfAny("chronicle.aliveworkplace.coven.hex", 1, 1).getString(), "The witch coven hexed 1 villager in the night");
		check(wrong, Words.countedIfAny("chronicle.aliveworkplace.coven.hex", 2, 2).getString(), "The witch coven hexed 2 villagers in the night");
		// the chat line reads right at 1 already, so it has no one form and countedIfAny keeps it
		check(wrong, Words.countedIfAny("message.aliveworkplace.coven.hex", 1, "Oakvale", 1).getString(),
			"A swirl of witch-light over Oakvale: the coven has hexed 1 of its villagers. No nurse's remedy lifts a hex; only breaking the coven does.");
		check(wrong, Words.countedFirst("message.aliveworkplace.sickness.cured", 1).getString(), "The Gray Cough is gone: 1 villager is well again.");
		check(wrong, Words.countedFirst("message.aliveworkplace.sickness.cured", 2).getString(), "The Gray Cough is gone: 2 villagers are well again.");
		check(wrong, Words.countedFirst("chronicle.aliveworkplace.sickness.cured", 1, "Rowan").getString(),
			"The Three-Herb Tonic went round and the Gray Cough was gone: 1 villager was well again, thanks to Rowan.");
		check(wrong, Words.countedFirst("chronicle.aliveworkplace.sickness.cured", 2, "Rowan").getString(),
			"The Three-Herb Tonic went round and the Gray Cough was gone: 2 villagers were well again, thanks to Rowan.");
		check(wrong, Words.countedFirst("chronicle.aliveworkplace.sickness.spread", 1).getString(), "The Gray Cough passed to 1 more villager in the night.");
		check(wrong, Words.countedFirst("chronicle.aliveworkplace.sickness.spread", 2).getString(), "The Gray Cough passed to 2 more villagers in the night.");
		check(wrong, Words.counted("message.aliveworkplace.bank.deposited", 1, "1 emerald", 1).getString(), "You put in 1 emerald. Your account: 1 emerald.");
		check(wrong, Words.counted("message.aliveworkplace.bank.deposited", 2, "1 emerald", 2).getString(), "You put in 1 emerald. Your account: 2 emeralds.");
		check(wrong, Words.counted("message.aliveworkplace.bank.withdrawn", 1, "1 emerald", 1).getString(), "You took out 1 emerald. Your account: 1 emerald.");
		check(wrong, Words.counted("message.aliveworkplace.bank.withdrawn", 2, "1 emerald", 2).getString(), "You took out 1 emerald. Your account: 2 emeralds.");
		check(wrong, Words.counted("wonder.aliveworkplace.refused.server_limit", 1, "Test Folly", 1).getString(),
			"The Test Folly may only be built in 1 village on this server, and it's taken.");
		check(wrong, Words.counted("wonder.aliveworkplace.refused.server_limit", 2, "Test Folly", 2).getString(),
			"The Test Folly may only be built in 2 villages on this server, and they're taken.");
		check(wrong, Words.counted("message.aliveworkplace.city_plan.other_village", 1, 1).getString(), "1 cell is nearer another village: left out");
		check(wrong, Words.counted("message.aliveworkplace.city_plan.other_village", 2, 2).getString(), "2 cells are nearer another village: left out");
		check(wrong, Words.counted("message.aliveworkplace.city_plan.no_zone", 1, "Oakvale", 1).getString(), "No zone here on Oakvale's plan (1 zone)");
		check(wrong, Words.counted("message.aliveworkplace.city_plan.no_zone", 2, "Oakvale", 2).getString(), "No zone here on Oakvale's plan (2 zones)");
		helper.assertTrue(wrong.isEmpty(), String.join("; ", wrong));
		helper.succeed();
	}

	/** B471, B472: the Steward's reasons (picked by their first number) and the two condition lines, at 1 and 2. */
	//$ gametest 'FabricGameTest.EMPTY_STRUCTURE'
	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
	public void stewardReasonsCountOneInTheSingular(GameTestHelper helper) {
		List<String> wrong = new ArrayList<>();
		Map<String, String[]> lines = new LinkedHashMap<>();
		lines.put("food", new String[] {"The store is 1 meal short", "The store is 2 meals short"});
		lines.put("better_homes", new String[] {"1 grown-up lives in a plain home", "2 grown-ups live in plain homes"});
		lines.put("homes_class", new String[] {"1 household wants a grander home", "2 households want a grander home"});
		lines.put("research", new String[] {"Nothing is being researched, and 1 topic is open", "Nothing is being researched, and 2 topics are open"});
		lines.put("street_lamps", new String[] {"1 bed is in the dark", "2 beds are in the dark"});
		lines.put("chapel", new String[] {"1 couple is courting and there's no chapel to marry in", "2 couples are courting and there's no chapel to marry in"});
		lines.put("lookout_tower", new String[] {"The village is 1 guard short", "The village is 2 guards short"});
		lines.put("barracks", new String[] {"The town is 1 guard short", "The town is 2 guards short"});
		lines.forEach((why, want) -> {
			check(wrong, Words.countedFirst("steward.aliveworkplace.why." + why, 1L, 0L).getString(), want[0]);
			check(wrong, Words.countedFirst("steward.aliveworkplace.why." + why, 2L, 0L).getString(), want[1]);
		});
		String idle = "steward.aliveworkplace.condition.research_idle";
		check(wrong, Words.countedIfAny(idle, 1, "nothing", 1).getString(), "Being researched: nothing (1 topic open)");
		check(wrong, Words.countedIfAny(idle, 2, "nothing", 2).getString(), "Being researched: nothing (2 topics open)");
		String raid = "steward.aliveworkplace.condition.raided_within";
		check(wrong, Words.countedIfAny(raid, 1, 1, 7).getString(), "Last raid: 1 day ago (fewer than 7)");
		check(wrong, Words.countedIfAny(raid, 2, 2, 7).getString(), "Last raid: 2 days ago (fewer than 7)");
		check(wrong, Words.countedIfAny(raid + "_never", 1, 1).getString(), "Never raided (wants a raid in the last 1 day)");
		check(wrong, Words.countedIfAny(raid + "_never", 7, 7).getString(), "Never raided (wants a raid in the last 7 days)");
		helper.assertTrue(wrong.isEmpty(), String.join("; ", wrong));
		helper.succeed();
	}

	/** B466, B426: the Habitat Keeper's two counts, the Cup's partner days, and the crafters' shortage line's item names. */
	//$ gametest 'FabricGameTest.EMPTY_STRUCTURE'
	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
	public void habitatCupAndShortageLinesCountTheirWords(GameTestHelper helper) {
		List<String> wrong = new ArrayList<>();
		String habitat = "message.aliveworkplace.habitat_keeper.state.watching";
		check(wrong, Words.countedTwo(habitat, 1, 1, 1, 1).getString(), "watching the wild Pokémon: 1 lure spot kept, 1 Saccharine log known");
		check(wrong, Words.countedTwo(habitat, 1, 0, 1, 0).getString(), "watching the wild Pokémon: 1 lure spot kept, 0 Saccharine logs known");
		check(wrong, Words.countedTwo(habitat, 2, 1, 2, 1).getString(), "watching the wild Pokémon: 2 lure spots kept, 1 Saccharine log known");
		check(wrong, Words.countedTwo(habitat, 2, 3, 2, 3).getString(), "watching the wild Pokémon: 2 lure spots kept, 3 Saccharine logs known");
		String partner = "message.aliveworkplace.cup.why_partner";
		check(wrong, Words.counted(partner, 1, 3, 1).getString(), "it has helped a villager at work on 1 day, and this Cup asks for 3");
		check(wrong, Words.counted(partner, 2, 3, 2).getString(), "it has helped a villager at work on 2 days, and this Cup asks for 3");
		Map<Item, Integer> lack = new LinkedHashMap<>();
		lack.put(Items.OAK_LOG, 4);
		check(wrong, JobShortages.lackLine(lack, 3, Items.BARREL).getString(), "short of 4 Oak Logs to make 3 Barrels");
		lack.put(Items.OAK_LOG, 1);
		check(wrong, JobShortages.lackLine(lack, 1, Items.BARREL).getString(), "short of 1 Oak Log to make 1 Barrel");
		lack.put(Items.STICK, 1);
		check(wrong, JobShortages.lackLine(lack, 4, Items.BARREL).getString(), "short of 1 Oak Log, 1 Stick to make 4 Barrels");
		helper.assertTrue(wrong.isEmpty(), String.join("; ", wrong));
		helper.succeed();
	}
}
