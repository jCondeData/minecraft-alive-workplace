package io.github.jcondedata.aliveworkplace.test;

import com.google.gson.JsonParser;
import io.github.jcondedata.aliveworkplace.WorkplaceConfig;
import io.github.jcondedata.aliveworkplace.guard.BanditCamps;
import io.github.jcondedata.aliveworkplace.guard.VillageRaids;
import io.github.jcondedata.aliveworkplace.hall.Caravans;
import io.github.jcondedata.aliveworkplace.hall.CivicEffects;
import io.github.jcondedata.aliveworkplace.hall.VillageHallBlockEntity;
import io.github.jcondedata.aliveworkplace.hall.VillageHalls;
import io.github.jcondedata.aliveworkplace.legend.Legend;
import io.github.jcondedata.aliveworkplace.legend.LegendPowers;
import io.github.jcondedata.aliveworkplace.legend.LegendRecord;
import io.github.jcondedata.aliveworkplace.legend.LegendSites;
import io.github.jcondedata.aliveworkplace.legend.Legends;
import io.github.jcondedata.aliveworkplace.legend.OldSage;
import io.github.jcondedata.aliveworkplace.registry.ModAttachments;
import io.github.jcondedata.aliveworkplace.registry.ModBlocks;
import io.github.jcondedata.aliveworkplace.registry.ModVillagers;
import io.github.jcondedata.aliveworkplace.threat.Lairs;
import io.github.jcondedata.aliveworkplace.threat.ThreatData;
import io.github.jcondedata.aliveworkplace.threat.Threats;
import io.github.jcondedata.aliveworkplace.threat.VillageFire;
import io.github.jcondedata.aliveworkplace.work.Stations;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponents;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.Difficulty;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.properties.AttachFace;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.phys.Vec3;

/**
 * QA verification (2026-11-01) of four fixed bugs, written from each bug's expected behaviour, not from its fix:
 *
 * <ul>
 *   <li>B345: a found Legend at their camp takes the meal or the riddle's answer from the off hand when the main hand is
 *   empty; an off-hand item that isn't wanted leaves it an empty-handed click; a wrong item in the main hand wins over a
 *   right one in the off hand (vanilla asks the main hand first and the click is answered).</li>
 *   <li>B364: a lair standing when the world goes Peaceful (or the threat level goes to 0) leaves in peace by the next
 *   dawn, through the hall's own round: the "left in peace" line, no "broken up" chat or chronicle, no rest days; back
 *   on Normal, a chief a player kills still breaks the camp up.</li>
 *   <li>B382: the desert raiders' sand ramp still rises over a player's sand at the wall's outside foot (a corner block,
 *   a gappy row, a row on the desert's sand ground, a row through a save and load), and the player's sand stays.</li>
 *   <li>B391: the fire chronicle names an unnamed Firewarden "the Firewarden", never "Firewarden the Firewarden"; a
 *   named one (a Name Tag) still by name; with two, whoever put out the most.</li>
 * </ul>
 */
public class QaThreatCobbleVerify1101GameTests implements FabricGameTest {
	private static final String HUGE = "aliveworkplace_test:huge_area";
	private static final String BIG = "aliveworkplace_test:big_area";
	private static final long DAY = 24000;

	// ==== B345: a found Legend's off hand ===========================================================================

	private static final BlockPos LEGEND_HALL = new BlockPos(2, 2, 2);
	private static final BlockPos MIDDLE = new BlockPos(15, 1, 15);

	private static Legend found(String site) {
		return Legends.read(ResourceLocation.fromNamespaceAndPath("aliveworkplace_test", "qav1101_found_" + site), JsonParser.parseString(
			"{\"rarity\": \"rare\", \"job\": \"minecraft:cartographer\", \"title\": \"Found " + site + "\", \"lore\": \"the lore\","
				+ " \"names\": [\"Wren of the " + site + "\"], \"arrive\": [{\"way\": \"found\", \"site\": \"" + site + "\"}]}").getAsJsonObject());
	}

	private static void legendHall(GameTestHelper helper, ServerPlayer owner) {
		helper.setBlock(LEGEND_HALL, ModBlocks.VILLAGE_HALL);
		((VillageHallBlockEntity) helper.getBlockEntity(LEGEND_HALL)).setOwner(owner.getUUID(), owner.getName().getString());
		ServerLevel level = helper.getLevel();
		BlockPos hall = helper.absolutePos(LEGEND_HALL);
		Caravans.Data.get(level).setWants(hall, Component.literal("Test"), List.of());
		int radius = VillageHalls.RADIUS;
		VillageHalls.RADIUS = 16;
		Leftovers.after(helper, () -> {
			VillageHalls.RADIUS = radius;
			Caravans.Data.get(level).remove(hall);
			LegendRecord record = LegendRecord.get(level);
			for (Villager v : level.getEntitiesOfClass(Villager.class, helper.getBounds().inflate(16), v -> true)) {
				record.forget(v.getUUID());
				record.forgetCaptive(v.getUUID());
				v.discard();
			}
			for (LegendRecord.Captive c : record.captives()) {
				if (c.hall().equals(Optional.of(hall))) {
					record.forgetCaptive(c.villager());
				}
			}
			OldSage.forget();
			CivicEffects.forget();
			LegendPowers.forget();
		});
	}

	private static void staged(GameTestHelper helper, List<Legend> legends, Runnable body) {
		ServerLevel level = helper.getLevel();
		long time = level.getDayTime();
		try {
			Map<ResourceLocation, Legend> map = new java.util.LinkedHashMap<>();
			legends.forEach(l -> map.put(l.id(), l));
			Legends.setForTest(map);
			body.run();
		} finally {
			level.setDayTime(time);
			Legends.reload(level.getServer().getResourceManager());
			LegendPowers.forget();
		}
	}

	private static Villager castaway(GameTestHelper helper, Legend legend) {
		Villager v = LegendSites.place(helper.getLevel(), LegendSites.site("shipwreck"), new LegendSites.Match(helper.absolutePos(LEGEND_HALL), legend),
			helper.absolutePos(MIDDLE), RandomSource.create(29));
		helper.assertTrue(v != null, "the shipwreck camp is set down");
		return v;
	}

	/** The Old Sage at his hut, his three riddles set to {@code asked} (1 map, 2 compass, 3 clock, 4 book, 7 water bucket). */
	private static Villager sage(GameTestHelper helper, List<Integer> asked) {
		Villager v = OldSage.placeHut(helper.getLevel(), helper.absolutePos(LEGEND_HALL), helper.absolutePos(MIDDLE), RandomSource.create(14));
		helper.assertTrue(v != null, "the hut is set down");
		ModAttachments.SAGE_RIDDLES.set(v, new OldSage.Riddles(asked, 0, 0));
		return v;
	}

	private static void sageStaged(GameTestHelper helper, Runnable body) {
		Legend sage = Legends.get(OldSage.ID).orElse(null);
		helper.assertTrue(sage != null, "old_sage.json not loaded");
		staged(helper, List.of(sage), body);
	}

	private static boolean freed(GameTestHelper helper, Villager v) {
		return LegendRecord.get(helper.getLevel()).captive(v.getUUID()).map(LegendRecord.Captive::freed).orElse(false);
	}

	private static QaStageFiveGameTests.Client survivor(GameTestHelper helper, String name) {
		QaStageFiveGameTests.Client c = QaStageSixGameTests.client(helper, GameType.SURVIVAL, QaStageSixGameTests.name(name));
		helper.assertFalse(c.player().hasPermissions(2), "the player is an operator; the check would prove nothing");
		return c;
	}

	private static List<String> keys(QaStageFiveGameTests.Client c) {
		return QaStageFourGameTests.keys(c.seen());
	}

	/**
	 * Main hand wins: a stick in the main hand and a cooked meal in the off hand is a click with the stick ("something
	 * cooked, please"), the meal kept and the castaway still waiting. With the stick put away (main hand empty), the
	 * same off-hand meal is handed over: the castaway is freed, the last meal of the stack eaten and the off hand empty.
	 */
	//$ gametest_ticks_batch HUGE '100' '"qav1101CastawayHands"'
	@GameTest(template = HUGE, timeoutTicks = 100, batch = "qav1101CastawayHands")
	public void qaAWrongMainHandWinsOverTheOffHandsMealThenTheMealIsTaken(GameTestHelper helper) {
		Leftovers.clear(helper);
		QaStageFiveGameTests.Client c = survivor(helper, "qav1101cast");
		ServerPlayer p = c.player();
		legendHall(helper, p);
		Legend legend = found("shipwreck");
		staged(helper, List.of(legend), () -> {
			Villager castaway = castaway(helper, legend);
			p.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.STICK, 2));
			p.setItemInHand(InteractionHand.OFF_HAND, new ItemStack(Items.COOKED_PORKCHOP, 1));
			QaStageSixGameTests.clickVillager(p, castaway, c.seen());
			helper.assertFalse(freed(helper, castaway), "a stick in the main hand fed the castaway from the off hand: saw " + keys(c));
			helper.assertTrue(QaStageFourGameTests.saw(c.seen(), "message.aliveworkplace.legend_site.hungry"), "a stick in the main hand: saw " + keys(c));
			helper.assertTrue(p.getOffhandItem().is(Items.COOKED_PORKCHOP) && p.getOffhandItem().getCount() == 1 && p.getMainHandItem().getCount() == 2,
				"with a stick in the main hand something was taken: " + p.getMainHandItem() + " / " + p.getOffhandItem());
			p.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
			c.seen().clear();
			QaStageSixGameTests.clickVillager(p, castaway, c.seen());
			helper.assertTrue(freed(helper, castaway), "the off hand's last porkchop, main hand empty, didn't free the castaway: saw " + keys(c));
			helper.assertTrue(p.getOffhandItem().isEmpty(), "the porkchop is still in the off hand: " + p.getOffhandItem());
			helper.assertTrue(QaStageFourGameTests.saw(c.seen(), "message.aliveworkplace.legend_site.thanks.shipwreck"), "no thanks: saw " + keys(c));
		});
		helper.succeed();
	}

	/**
	 * Every kind of cooked meal counts from the off hand, not just beef: a bowl of rabbit stew (main hand empty) frees the
	 * castaway, and nothing in the main hand (a sneaking click) changes that. A second click on the freed castaway with a
	 * new meal in the off hand takes nothing.
	 */
	//$ gametest_ticks_batch HUGE '100' '"qav1101CastawayStew"'
	@GameTest(template = HUGE, timeoutTicks = 100, batch = "qav1101CastawayStew")
	public void qaAStewInTheOffHandFreesTheCastawayOnce(GameTestHelper helper) {
		Leftovers.clear(helper);
		QaStageFiveGameTests.Client c = survivor(helper, "qav1101stew");
		ServerPlayer p = c.player();
		legendHall(helper, p);
		Legend legend = found("shipwreck");
		staged(helper, List.of(legend), () -> {
			Villager castaway = castaway(helper, legend);
			p.setShiftKeyDown(true);
			p.setItemInHand(InteractionHand.OFF_HAND, new ItemStack(Items.RABBIT_STEW));
			QaStageSixGameTests.clickVillager(p, castaway, c.seen());
			helper.assertTrue(freed(helper, castaway), "rabbit stew in the off hand, sneaking, didn't free the castaway: saw " + keys(c));
			helper.assertFalse(p.getOffhandItem().is(Items.RABBIT_STEW), "the stew wasn't handed over: " + p.getOffhandItem());
			p.setItemInHand(InteractionHand.OFF_HAND, new ItemStack(Items.BAKED_POTATO, 4));
			p.setShiftKeyDown(false);
			QaStageSixGameTests.clickVillager(p, castaway, c.seen());
			helper.assertTrue(p.getOffhandItem().getCount() == 4, "the freed castaway took a potato from the off hand: " + p.getOffhandItem());
		});
		helper.succeed();
	}

	/**
	 * The Sage asks for a compass. A clock in the off hand (the answer to a later riddle, not this one), main hand empty,
	 * is an empty-handed click: the riddle again, no miss, the clock kept. A stick in the main hand with the compass in
	 * the off hand: the main hand wins, a shake of the head, one miss, the compass kept. The stick put away: the compass
	 * is taken from the off hand, "Just so.", and the next riddle (the clock) is asked.
	 */
	//$ gametest_ticks_batch HUGE '100' '"qav1101SageHands"'
	@GameTest(template = HUGE, timeoutTicks = 100, batch = "qav1101SageHands")
	public void qaTheSageTakesOnlyThisRiddlesAnswerFromTheOffHandAndTheMainHandWins(GameTestHelper helper) {
		Leftovers.clear(helper);
		QaStageFiveGameTests.Client c = survivor(helper, "qav1101sage");
		ServerPlayer p = c.player();
		legendHall(helper, p);
		sageStaged(helper, () -> {
			Villager sage = sage(helper, List.of(2, 3, 4));
			p.setItemInHand(InteractionHand.OFF_HAND, new ItemStack(Items.CLOCK));
			QaStageSixGameTests.clickVillager(p, sage, c.seen());
			OldSage.Riddles r = ModAttachments.SAGE_RIDDLES.get(sage);
			helper.assertTrue(r.solved() == 0 && r.misses() == 0, "a clock in the off hand counted for the compass riddle: " + r + ", saw " + keys(c));
			helper.assertTrue(QaStageFourGameTests.saw(c.seen(), "message.aliveworkplace.sage.riddle"), "a clock in the off hand: saw " + keys(c));
			helper.assertFalse(QaStageFourGameTests.saw(c.seen(), "message.aliveworkplace.sage.wrong"), "a clock in the off hand got a shake of the head");
			helper.assertTrue(p.getOffhandItem().is(Items.CLOCK), "the clock was taken: " + p.getOffhandItem());

			p.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.STICK, 3));
			p.setItemInHand(InteractionHand.OFF_HAND, new ItemStack(Items.COMPASS));
			c.seen().clear();
			QaStageSixGameTests.clickVillager(p, sage, c.seen());
			r = ModAttachments.SAGE_RIDDLES.get(sage);
			helper.assertTrue(r.solved() == 0, "the compass in the off hand answered while a stick was in the main hand: " + r);
			helper.assertTrue(r.misses() == 1 && QaStageFourGameTests.saw(c.seen(), "message.aliveworkplace.sage.wrong"),
				"the stick in the main hand wasn't a wrong answer: " + r + ", saw " + keys(c));
			helper.assertTrue(p.getOffhandItem().is(Items.COMPASS) && p.getMainHandItem().getCount() == 3, "something was taken: "
				+ p.getMainHandItem() + " / " + p.getOffhandItem());

			p.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
			c.seen().clear();
			QaStageSixGameTests.clickVillager(p, sage, c.seen());
			r = ModAttachments.SAGE_RIDDLES.get(sage);
			helper.assertTrue(r.solved() == 1 && p.getOffhandItem().isEmpty(), "the compass in the off hand wasn't taken: " + r + ", " + p.getOffhandItem());
			helper.assertTrue(QaStageFourGameTests.saw(c.seen(), "message.aliveworkplace.sage.right"), "no \"Just so.\": " + keys(c));
			helper.assertTrue(QaStageFourGameTests.saw(c.seen(), "message.aliveworkplace.sage.riddle"), "the next riddle wasn't asked: " + keys(c));
		});
		helper.succeed();
	}

	/**
	 * All three riddles answered from the off hand alone (water bucket, compass, clock): each taken, the bucket coming
	 * back empty, and the Sage freed; the main hand empty at every click.
	 */
	//$ gametest_ticks_batch HUGE '100' '"qav1101SageAllOff"'
	@GameTest(template = HUGE, timeoutTicks = 100, batch = "qav1101SageAllOff")
	public void qaAllThreeRiddlesAnsweredFromTheOffHandFreeTheSage(GameTestHelper helper) {
		Leftovers.clear(helper);
		QaStageFiveGameTests.Client c = survivor(helper, "qav1101sageoff");
		ServerPlayer p = c.player();
		legendHall(helper, p);
		sageStaged(helper, () -> {
			Villager sage = sage(helper, List.of(7, 2, 3));
			p.setItemInHand(InteractionHand.OFF_HAND, new ItemStack(Items.WATER_BUCKET));
			QaStageSixGameTests.clickVillager(p, sage, c.seen());
			helper.assertTrue(ModAttachments.SAGE_RIDDLES.get(sage).solved() == 1, "the water bucket in the off hand wasn't taken: "
				+ ModAttachments.SAGE_RIDDLES.get(sage) + ", saw " + keys(c));
			helper.assertFalse(p.getOffhandItem().is(Items.WATER_BUCKET), "the water is still in the off hand");
			int buckets = p.getInventory().countItem(Items.BUCKET);
			helper.assertTrue(buckets == 1, "the empty bucket didn't come back: " + buckets + " buckets, off hand " + p.getOffhandItem());
			// The bucket comes back into the first free slot, the empty main hand: the player puts it away before the next
			// answer (with it in the main hand, the main hand would be asked first: a bucket answers nothing).
			if (p.getMainHandItem().is(Items.BUCKET)) {
				p.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
			}
			for (ItemStack answer : List.of(new ItemStack(Items.COMPASS), new ItemStack(Items.CLOCK))) {
				p.setItemInHand(InteractionHand.OFF_HAND, answer);
				c.seen().clear();
				QaStageSixGameTests.clickVillager(p, sage, c.seen());
				helper.assertTrue(p.getOffhandItem().isEmpty(), answer + " in the off hand wasn't taken: saw " + keys(c));
			}
			helper.assertTrue(freed(helper, sage), "three answers from the off hand didn't free the Sage: " + ModAttachments.SAGE_RIDDLES.get(sage));
		});
		helper.succeed();
	}

	// ==== B364: a lair when the world goes Peaceful =================================================================

	private static final BlockPos HALL = new BlockPos(1, 2, 1);
	private static final BlockPos CAMP = new BlockPos(12, 1, 12);
	private static final String LEFT = "The raiders of the Bandit camp packed up and left the village in peace";

	private static void at(ServerLevel level, long day, long time) {
		level.setDayTime((day - 1) * DAY + time);
	}

	/** A hall (village radius 16) with four villagers; config, clock, difficulty, threat level and threats put back at the end. */
	private static BlockPos lairVillage(GameTestHelper helper) {
		Leftovers.clear(helper);
		Leftovers.halls(helper);
		ServerLevel level = helper.getLevel();
		Lairs.forget(level);
		int radius = VillageHalls.RADIUS;
		long time = level.getDayTime();
		boolean lairs = Lairs.ENABLED;
		int threat = Threats.LEVEL;
		Difficulty difficulty = level.getDifficulty();
		VillageHalls.RADIUS = 16;
		Lairs.ENABLED = true;
		helper.setBlock(HALL, ModBlocks.VILLAGE_HALL);
		BlockPos hall = helper.absolutePos(HALL);
		ThreatData.get(level).forgetPast(hall);
		ThreatData.get(level).forgetClock(hall);
		Leftovers.after(helper, () -> {
			level.getServer().setDifficulty(difficulty, true);
			Threats.LEVEL = threat;
			level.getEntitiesOfClass(Mob.class, helper.getBounds().inflate(48),
				m -> m.getTags().contains(Lairs.TAG) || m.getTags().contains(BanditCamps.TAG) || m.getTags().contains(VillageRaids.TAG)).forEach(Mob::discard);
			Lairs.forget(level);
			BanditCamps.forget(level);
			VillageRaids.forget();
			ThreatData.get(level).end(hall);
			ThreatData.get(level).forgetPast(hall);
			ThreatData.get(level).forgetClock(hall);
			VillageHalls.RADIUS = radius;
			Lairs.ENABLED = lairs;
			level.setDayTime(time);
			new WorkplaceConfig().apply();
			Threats.LEVEL = threat;
		});
		for (int i = 0; i < 4; i++) {
			helper.spawn(EntityType.VILLAGER, new BlockPos(2 + i, 2, 3));
		}
		return hall;
	}

	/** A player standing in the village who hears the camp's news. */
	private static QaStageFiveGameTests.Client listener(GameTestHelper helper, String name) {
		QaStageFiveGameTests.Client c = QaStageSixGameTests.client(helper, GameType.SURVIVAL, QaStageSixGameTests.name(name));
		c.player().moveTo(Vec3.atBottomCenterOf(helper.absolutePos(new BlockPos(7, 2, 5))));
		return c;
	}

	/** The hall's own round on its next tick (as every {@code VillageNeeds.CHECK_EVERY} ticks in play). */
	private static void hallRound(GameTestHelper helper, BlockPos hall) {
		((VillageHallBlockEntity) helper.getLevel().getBlockEntity(hall)).setNeeds(null);
	}

	private static List<String> chronicle(VillageHallBlockEntity entity) {
		return entity.chronicle().stream().map(e -> e.text().getString()).toList();
	}

	private static List<String> brokenNews(QaStageFiveGameTests.Client c) {
		return keys(c).stream().filter(k -> k.contains("broken")).toList();
	}

	/** No line in the chronicle says the camp was broken up or its chief fell. */
	private static void noBreakUp(GameTestHelper helper, VillageHallBlockEntity entity, QaStageFiveGameTests.Client c, String when) {
		for (String line : chronicle(entity)) {
			helper.assertFalse(line.contains("broken up") || line.contains("broke up") || line.contains(" fell"),
				when + ": the chronicle says '" + line + "'");
		}
		helper.assertTrue(brokenNews(c).isEmpty(), when + ": players near were told the camp was broken up: " + brokenNews(c));
	}

	/**
	 * Through the hall's own round: a bandit camp stands, the world is set to Peaceful, the game takes the chief away. By
	 * the round after the next dawn the camp has left in peace: the chronicle's last line is the "left in peace" line, the
	 * player in the village was told "packed up and gone", nobody heard it was broken up, no line says the chief fell,
	 * and the village has no rest days.
	 */
	//$ gametest_ticks_batch BIG '80' '"qav1101PeacefulHallRound"'
	@GameTest(template = BIG, timeoutTicks = 80, batch = "qav1101PeacefulHallRound")
	public void qaPeacefulSendsTheCampAwayThroughTheHallsOwnRound(GameTestHelper helper) {
		BlockPos hall = lairVillage(helper);
		ServerLevel level = helper.getLevel();
		VillageHallBlockEntity entity = (VillageHallBlockEntity) level.getBlockEntity(hall);
		QaStageFiveGameTests.Client c = listener(helper, "qav1101peace");
		UUID[] chief = {null};
		helper.runAfterDelay(2, () -> {
			at(level, 95, 13000);
			helper.assertTrue(BanditCamps.found(level, hall, helper.absolutePos(CAMP)) != null, "no camp");
			chief[0] = Lairs.near(level, hall).orElseThrow().captain();
			// (heard from here on: the hall's own timed round may come any tick after Peaceful took the chief and send
			// the camp off before the round this test asks for, B770)
			c.seen().clear();
			level.getServer().setDifficulty(Difficulty.PEACEFUL, true);
		});
		helper.runAfterDelay(12, () -> {
			helper.assertTrue(level.getEntity(chief[0]) == null, "Peaceful didn't take the chief away; the test proves nothing");
			hallRound(helper, hall);
		});
		helper.runAfterDelay(16, () -> {
			noBreakUp(helper, entity, c, "the round after Peaceful took the chief");
			at(level, 96, 100);
			hallRound(helper, hall);
		});
		helper.runAfterDelay(20, () -> {
			helper.assertTrue(Lairs.near(level, hall).isEmpty(), "the camp still stands the round after the dawn on Peaceful");
			noBreakUp(helper, entity, c, "the dawn after Peaceful");
			List<String> lines = chronicle(entity);
			helper.assertTrue(!lines.isEmpty() && lines.get(lines.size() - 1).equals(LEFT), "the chronicle doesn't end with the left-in-peace line: " + lines);
			helper.assertTrue(QaStageFourGameTests.saw(c.seen(), "message.aliveworkplace.lair.left"), "the player wasn't told it left in peace: " + keys(c));
			helper.assertTrue(Lairs.resting(level, hall) == 0, "rest days after leaving in peace: " + Lairs.resting(level, hall));
			helper.succeed();
		});
	}

	/**
	 * Threat level 0 (the chief still standing, as the game doesn't take him): the camp stands the rest of the day the
	 * level went to 0 (it leaves "at the next dawn", not at once), and at the round after the next dawn it has left in
	 * peace, chief and band gone with it, nothing broken up and no rest days. On the three days after, no camp comes.
	 */
	//$ gametest_ticks_batch BIG '80' '"qav1101ThreatZero"'
	@GameTest(template = BIG, timeoutTicks = 80, batch = "qav1101ThreatZero")
	public void qaAtThreatLevelZeroTheCampLeavesInPeaceAtTheNextDawnNotBefore(GameTestHelper helper) {
		BlockPos hall = lairVillage(helper);
		ServerLevel level = helper.getLevel();
		VillageHallBlockEntity entity = (VillageHallBlockEntity) level.getBlockEntity(hall);
		QaStageFiveGameTests.Client c = listener(helper, "qav1101zero");
		UUID[] chief = {null};
		helper.runAfterDelay(2, () -> {
			at(level, 95, 13000);
			helper.assertTrue(BanditCamps.found(level, hall, helper.absolutePos(CAMP)) != null, "no camp");
			chief[0] = Lairs.near(level, hall).orElseThrow().captain();
			Threats.LEVEL = 0;
			helper.assertTrue(Threats.quiet(level, hall), "threat level 0 isn't quiet");
			c.seen().clear();
			hallRound(helper, hall);
		});
		helper.runAfterDelay(5, () -> {
			helper.assertTrue(Lairs.near(level, hall).isPresent(), "the camp left the same day the threat level went to 0 (expected: at the next dawn)");
			helper.assertTrue(level.getEntity(chief[0]) != null, "the chief went before the dawn");
			at(level, 95, 23500); // just before the next dawn
			hallRound(helper, hall);
		});
		helper.runAfterDelay(8, () -> {
			helper.assertTrue(Lairs.near(level, hall).isPresent(), "the camp left before the next dawn (23500)");
			at(level, 96, 100);
			hallRound(helper, hall);
		});
		helper.runAfterDelay(11, () -> {
			helper.assertTrue(Lairs.near(level, hall).isEmpty(), "at threat level 0 the camp still stands after the next dawn");
			helper.assertTrue(level.getEntity(chief[0]) == null, "the chief stayed behind when the camp left");
			noBreakUp(helper, entity, c, "threat level 0");
			List<String> lines = chronicle(entity);
			helper.assertTrue(!lines.isEmpty() && lines.get(lines.size() - 1).equals(LEFT), "threat level 0: the chronicle " + lines);
			helper.assertTrue(QaStageFourGameTests.saw(c.seen(), "message.aliveworkplace.lair.left"), "the player wasn't told it left in peace: " + keys(c));
			helper.assertTrue(Lairs.resting(level, hall) == 0, "rest days after leaving in peace: " + Lairs.resting(level, hall));
			RandomSource always = new net.minecraft.world.level.levelgen.LegacyRandomSource(7L) {
				@Override
				public float nextFloat() {
					return 0f;
				}
			};
			for (long day = 96; day < 99; day++) {
				at(level, day, 6000);
				Lairs.round(level, hall, true, always);
			}
			helper.assertTrue(Lairs.near(level, hall).isEmpty(), "a camp came at threat level 0");
			helper.succeed();
		});
	}

	/**
	 * The counter-test: a camp leaves in peace on Peaceful; the world back on Normal, a new camp makes camp and a player
	 * kills its chief. That one is broken up at once: players near hear it, the chronicle names the player, the village
	 * rests.
	 */
	//$ gametest_ticks_batch BIG '80' '"qav1101BackToNormal"'
	@GameTest(template = BIG, timeoutTicks = 80, batch = "qav1101BackToNormal")
	public void qaBackOnNormalAChiefAPlayerKillsStillBreaksTheCampUp(GameTestHelper helper) {
		BlockPos hall = lairVillage(helper);
		ServerLevel level = helper.getLevel();
		VillageHallBlockEntity entity = (VillageHallBlockEntity) level.getBlockEntity(hall);
		QaStageFiveGameTests.Client c = listener(helper, "qav1101normal");
		ServerPlayer p = c.player();
		helper.runAfterDelay(2, () -> {
			at(level, 95, 13000);
			helper.assertTrue(BanditCamps.found(level, hall, helper.absolutePos(CAMP)) != null, "no camp");
			level.getServer().setDifficulty(Difficulty.PEACEFUL, true);
		});
		helper.runAfterDelay(12, () -> {
			hallRound(helper, hall);
		});
		helper.runAfterDelay(15, () -> {
			at(level, 96, 100);
			hallRound(helper, hall);
		});
		helper.runAfterDelay(18, () -> {
			helper.assertTrue(Lairs.near(level, hall).isEmpty() && Lairs.resting(level, hall) == 0, "the first camp didn't leave in peace on Peaceful");
			level.getServer().setDifficulty(Difficulty.NORMAL, true);
			at(level, 100, 13000);
			helper.assertTrue(BanditCamps.found(level, hall, helper.absolutePos(CAMP)) != null, "no second camp on Normal");
		});
		helper.runAfterDelay(21, () -> {
			Lairs.Lair lair = Lairs.near(level, hall).orElseThrow();
			Mob chief = (Mob) level.getEntity(lair.captain());
			helper.assertTrue(chief != null && chief.isAlive(), "the second camp has no chief");
			c.seen().clear();
			chief.hurt(level.damageSources().playerAttack(p), 1000f);
		});
		helper.runAfterDelay(24, () -> {
			helper.assertTrue(Lairs.near(level, hall).isEmpty(), "a camp whose chief a player killed still stands");
			helper.assertTrue(!brokenNews(c).isEmpty(), "the player near wasn't told the camp was broken up: " + keys(c));
			List<String> lines = chronicle(entity);
			String last = lines.isEmpty() ? "" : lines.get(lines.size() - 1);
			helper.assertTrue(last.contains(p.getGameProfile().getName()) && last.contains("broke up"), "the kill isn't remembered: " + lines);
			helper.assertTrue(Lairs.resting(level, hall) > 0, "no rest days after the chief fell");
			helper.succeed();
		});
	}

	/**
	 * A player switches the world to Peaceful for a moment (as players do to clear the night's mobs) and back to Normal
	 * before the hall's next round: the game took the chief away, nobody brought him down. The camp is a lair that stood
	 * when the world went Peaceful; it must not be "broken up" as if its chief fell (no such chronicle line, no
	 * broken-up chat, no rest days).
	 */
	//$ gametest_ticks_batch BIG '80' '"qav1101PeacefulBlip"'
	@GameTest(template = BIG, timeoutTicks = 80, batch = "qav1101PeacefulBlip")
	public void qaAMomentOfPeacefulBeforeTheHallsRoundIsNoKill(GameTestHelper helper) {
		BlockPos hall = lairVillage(helper);
		ServerLevel level = helper.getLevel();
		VillageHallBlockEntity entity = (VillageHallBlockEntity) level.getBlockEntity(hall);
		QaStageFiveGameTests.Client c = listener(helper, "qav1101blip");
		UUID[] chief = {null};
		helper.runAfterDelay(2, () -> {
			at(level, 95, 13000);
			helper.assertTrue(BanditCamps.found(level, hall, helper.absolutePos(CAMP)) != null, "no camp");
			chief[0] = Lairs.near(level, hall).orElseThrow().captain();
			level.getServer().setDifficulty(Difficulty.PEACEFUL, true);
		});
		helper.runAfterDelay(12, () -> {
			helper.assertTrue(level.getEntity(chief[0]) == null, "Peaceful didn't take the chief away; the test proves nothing");
			level.getServer().setDifficulty(Difficulty.NORMAL, true);
			c.seen().clear();
			hallRound(helper, hall);
		});
		helper.runAfterDelay(15, () -> {
			at(level, 96, 100);
			hallRound(helper, hall);
		});
		helper.runAfterDelay(18, () -> {
			noBreakUp(helper, entity, c, "Peaceful for a moment, then Normal");
			helper.assertTrue(Lairs.resting(level, hall) == 0, "rest days though nobody killed the chief: " + Lairs.resting(level, hall)
				+ "; chronicle " + chronicle(entity));
			helper.succeed();
		});
	}

	// ==== B382: the desert raiders' ramp over a player's sand =======================================================

	/** One block of the player's sand at the west end of the Stone Wall's outside foot (its corner, not in front of the husk). */
	//$ gametest_ticks_batch HUGE '900' '"qav1101SandCorner"'
	@GameTest(template = HUGE, timeoutTicks = 900, batch = "qav1101SandCorner")
	public void qaARampRisesWithAPlayersSandBlockAtTheWallsCorner(GameTestHelper helper) {
		QaRaiderCulturesGameTests.atTheWallsFoot(helper, List.of(11), Blocks.SAND, null, true, false);
	}

	/** Every other block of the wall's outside foot holds the player's sand (gaps between): the ramp still rises, the sand stays. */
	//$ gametest_ticks_batch HUGE '900' '"qav1101SandGappy"'
	@GameTest(template = HUGE, timeoutTicks = 900, batch = "qav1101SandGappy")
	public void qaARampRisesOverAGappyRowOfAPlayersSand(GameTestHelper helper) {
		QaRaiderCulturesGameTests.atTheWallsFoot(helper, List.of(11, 13, 15, 17), Blocks.SAND, null, true, false);
	}

	/** A player's row of sand at the foot of a wall standing on the desert's own sand ground: the ramp rises, ground and row stay. */
	//$ gametest_ticks_batch HUGE '900' '"qav1101SandOnSand"'
	@GameTest(template = HUGE, timeoutTicks = 900, batch = "qav1101SandOnSand")
	public void qaARampRisesOverAPlayersRowOfSandOnTheDesertsSandGround(GameTestHelper helper) {
		QaRaiderCulturesGameTests.atTheWallsFoot(helper, List.of(11, 12, 13, 14, 15, 16, 17), Blocks.SAND, Blocks.SAND, true, false);
	}

	/**
	 * A player's row of sand at the foot, and the threats saved and loaded once the ramp is up: after the siege the
	 * raiders' sand is all gone and the player's row is still there (the load mustn't count the player's sand as the siege's).
	 */
	//$ gametest_ticks_batch HUGE '900' '"qav1101SandReload"'
	@GameTest(template = HUGE, timeoutTicks = 900, batch = "qav1101SandReload")
	public void qaAPlayersRowOfSandStaysAfterASiegeSavedAndLoaded(GameTestHelper helper) {
		QaRaiderCulturesGameTests.atTheWallsFoot(helper, List.of(11, 12, 13, 14, 15, 16, 17), Blocks.SAND, null, true, true);
	}

	// ==== B391: the fire chronicle's Firewarden ====================================================================

	private static final BlockPos SECOND_GRINDSTONE = new BlockPos(3, 2, 8);

	private static Villager secondFirewarden(GameTestHelper helper, BlockPos standing) {
		helper.setBlock(SECOND_GRINDSTONE, Blocks.GRINDSTONE.defaultBlockState().setValue(BlockStateProperties.ATTACH_FACE, AttachFace.FLOOR)
			.setValue(BlockStateProperties.HORIZONTAL_FACING, Direction.SOUTH));
		Villager v = helper.spawn(EntityType.VILLAGER, standing);
		Stations.assign(helper.getLevel(), v, helper.absolutePos(SECOND_GRINDSTONE), ModVillagers.FIREWARDEN);
		return v;
	}

	private static String fireLine(ServerLevel level, BlockPos hall) {
		return QaFireGameTests.fireEntry(level, hall).map(e -> e.text().getString()).orElse("");
	}

	/** A name given the way a player does: a Name Tag named {@code name}, right-clicked on the villager. */
	private static void nameTag(GameTestHelper helper, Villager v, String name) {
		ServerPlayer p = helper.makeMockServerPlayerInLevel();
		Leftovers.after(helper, () -> helper.getLevel().getServer().getPlayerList().remove(p));
		p.setGameMode(GameType.SURVIVAL);
		ItemStack tag = new ItemStack(Items.NAME_TAG);
		tag.set(DataComponents.CUSTOM_NAME, Component.literal(name));
		p.setItemInHand(InteractionHand.MAIN_HAND, tag);
		p.moveTo(v.getX() + 1, v.getY(), v.getZ());
		p.interactOn(v, InteractionHand.MAIN_HAND);
		helper.assertTrue(v.hasCustomName() && v.getCustomName().getString().equals(name), "the Name Tag didn't name the villager: " + v.getCustomName());
	}

	/**
	 * The player's view: an unnamed Firewarden (villager names are off in tests) walks to the fire on the roof himself and
	 * puts it out; the chronicle reads "...; the Firewarden put it out." and never "Firewarden the Firewarden".
	 */
	//$ gametest_ticks_batch BIG '700' '"qav1101FireWalks"'
	@GameTest(template = BIG, timeoutTicks = 700, batch = "qav1101FireWalks")
	public void qaAnUnnamedFirewardenWhoWalksToTheFireIsTheFirewarden(GameTestHelper helper) {
		QaFireGameTests.Hut hut = QaFireGameTests.hut(helper, "qav1101walk", Blocks.OAK_LOG);
		ServerLevel level = hut.level();
		QaFireGameTests.dryReach(helper, QaFireGameTests.GRINDSTONE);
		helper.setBlock(new BlockPos(4, 1, 6), Blocks.WATER);
		Villager warden = QaFireGameTests.firewarden(helper, new BlockPos(3, 2, 4));
		helper.assertFalse(warden.hasCustomName(), "the Firewarden has a name; the test proves nothing");
		helper.runAfterDelay(2, () -> {
			level.getGameRules().getRule(GameRules.RULE_DOFIRETICK).set(false, level.getServer());
			QaFireGameTests.fire(helper, QaFireGameTests.ROOF_TOP);
		});
		helper.runAfterDelay(4, () -> helper.succeedWhen(() -> {
			String text = fireLine(level, hut.hall());
			helper.assertTrue(text.startsWith("Fire in the "), "no fire in the chronicle yet (fires left " + QaFireGameTests.fires(helper) + ")");
			helper.assertFalse(text.contains("Firewarden the Firewarden"), "the chronicle says: " + text);
			helper.assertTrue(text.endsWith("; the Firewarden put it out."), "the unnamed Firewarden who put it out isn't credited: " + text);
		}));
	}

	/** A Firewarden a player named "Rowan" with a Name Tag puts the fire out: "...; Rowan the Firewarden put it out." */
	//$ gametest_ticks_batch BIG '300' '"qav1101FireTag"'
	@GameTest(template = BIG, timeoutTicks = 300, batch = "qav1101FireTag")
	public void qaAFirewardenNamedWithANameTagIsCalledByName(GameTestHelper helper) {
		QaFireGameTests.Hut hut = QaFireGameTests.hut(helper, "qav1101tag", Blocks.OAK_LOG);
		ServerLevel level = hut.level();
		Villager warden = QaFireGameTests.firewarden(helper, new BlockPos(3, 2, 4));
		warden.setNoAi(true);
		nameTag(helper, warden, "Rowan");
		helper.runAfterDelay(2, () -> {
			level.getGameRules().getRule(GameRules.RULE_DOFIRETICK).set(false, level.getServer());
			QaFireGameTests.fire(helper, QaFireGameTests.ROOF_TOP);
			VillageFire.douse(level, helper.absolutePos(QaFireGameTests.ROOF_TOP), 0, warden);
		});
		helper.runAfterDelay(120, () -> {
			String text = fireLine(level, hut.hall());
			helper.assertTrue(text.startsWith("Fire in the "), "no fire in the chronicle: " + text);
			helper.assertTrue(text.endsWith("; Rowan the Firewarden put it out."), "the named Firewarden: " + text);
			helper.succeed();
		});
	}

	/**
	 * Two Firewardens: Rowan (named) puts out one fire block, an unnamed one two. The one who put out the most is credited,
	 * the unnamed one: "...; the Firewarden put it out.", no "Rowan", no "Firewarden the Firewarden".
	 */
	//$ gametest_ticks_batch BIG '300' '"qav1101FireTwoUnnamed"'
	@GameTest(template = BIG, timeoutTicks = 300, batch = "qav1101FireTwoUnnamed")
	public void qaWhenTheUnnamedFirewardenPutOutMostHeIsTheFirewarden(GameTestHelper helper) {
		twoWardens(helper, "qav1101two_a", false);
	}

	/** The mirror: Rowan puts out two, the unnamed one one: "...; Rowan the Firewarden put it out." */
	//$ gametest_ticks_batch BIG '300' '"qav1101FireTwoNamed"'
	@GameTest(template = BIG, timeoutTicks = 300, batch = "qav1101FireTwoNamed")
	public void qaWhenTheNamedFirewardenPutOutMostHeIsCalledByName(GameTestHelper helper) {
		twoWardens(helper, "qav1101two_b", true);
	}

	private static void twoWardens(GameTestHelper helper, String name, boolean namedDoesMost) {
		QaFireGameTests.Hut hut = QaFireGameTests.hut(helper, name, Blocks.OAK_LOG);
		ServerLevel level = hut.level();
		Villager unnamed = QaFireGameTests.firewarden(helper, new BlockPos(3, 2, 4));
		unnamed.setNoAi(true);
		Villager rowan = secondFirewarden(helper, new BlockPos(4, 2, 9));
		rowan.setNoAi(true);
		rowan.setCustomName(Component.literal("Rowan"));
		helper.assertFalse(unnamed.hasCustomName(), "the unnamed Firewarden has a name");
		List<BlockPos> fires = List.of(new BlockPos(13, 5, 14), new BlockPos(14, 5, 14), new BlockPos(15, 5, 14));
		helper.runAfterDelay(2, () -> {
			level.getGameRules().getRule(GameRules.RULE_DOFIRETICK).set(false, level.getServer());
			fires.forEach(f -> QaFireGameTests.fire(helper, f));
		});
		helper.runAfterDelay(3, () -> {
			Villager most = namedDoesMost ? rowan : unnamed;
			Villager least = namedDoesMost ? unnamed : rowan;
			helper.assertTrue(VillageFire.douse(level, helper.absolutePos(fires.get(0)), 0, most) == 1, "the first fire block didn't go out");
			helper.assertTrue(VillageFire.douse(level, helper.absolutePos(fires.get(1)), 0, least) == 1, "the second fire block didn't go out");
			helper.assertTrue(VillageFire.douse(level, helper.absolutePos(fires.get(2)), 0, most) == 1, "the third fire block didn't go out");
		});
		helper.runAfterDelay(120, () -> {
			String text = fireLine(level, hut.hall());
			helper.assertTrue(text.startsWith("Fire in the "), "no fire in the chronicle: " + text);
			helper.assertFalse(text.contains("Firewarden the Firewarden"), "the chronicle says: " + text);
			if (namedDoesMost) {
				helper.assertTrue(text.endsWith("; Rowan the Firewarden put it out."), "Rowan put out two of three: " + text);
			} else {
				helper.assertTrue(text.endsWith("; the Firewarden put it out.") && !text.contains("Rowan"), "the unnamed Firewarden put out two of three: " + text);
			}
			helper.succeed();
		});
	}
}
