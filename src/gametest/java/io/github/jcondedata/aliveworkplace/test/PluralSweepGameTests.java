package io.github.jcondedata.aliveworkplace.test;

import io.github.jcondedata.aliveworkplace.story.Objectives;
import io.github.jcondedata.aliveworkplace.work.Words;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.item.Items;

/**
 * B413 (and B406/B410's lines with no test of their own): countdowns and counters that pass through 1 read "1 day",
 * "1 hour", "1 battle", never "1 days". Each line picks its {@code .one} form through {@link Words#counted} (or
 * {@link Words#countedTwo} for "days and hours"), and the lang must have it.
 */
public class PluralSweepGameTests implements FabricGameTest {
	private static void check(List<String> wrong, String got, String want) {
		if (!got.equals(want)) {
			wrong.add("\"" + got + "\" (want \"" + want + "\")");
		}
	}

	/** Every countdown and counter fixed in the sweep, at 1 and at 2. */
	//$ gametest 'FabricGameTest.EMPTY_STRUCTURE'
	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
	public void countdownsAndCountersReadOneInTheSingular(GameTestHelper helper) {
		List<String> wrong = new ArrayList<>();
		Map<String, String[]> lines = new LinkedHashMap<>(); // key -> {one, two}, the count is the only argument
		lines.put("quest.aliveworkplace.wait", new String[] {"Wait 1 day", "Wait 2 days"});
		lines.put("legend.aliveworkplace.overhead.sulk_days", new String[] {"Back to work in 1 day", "Back to work in 2 days"});
		lines.put("screen.aliveworkplace.colonies.wait", new String[] {"The next colony can set out in 1 day", "The next colony can set out in 2 days"});
		lines.put("screen.aliveworkplace.realm.vote_left", new String[] {"About 1 hour left", "About 2 hours left"});
		lines.put("screen.aliveworkplace.pact.ending", new String[] {"Ends in 1 hour", "Ends in 2 hours"});
		lines.put("screen.aliveworkplace.feud.war_too_soon", new String[] {"Can't be pressed yet: the feud must stand a day (1 hour left).",
			"Can't be pressed yet: the feud must stand a day (2 hours left)."});
		lines.put("screen.aliveworkplace.hall.elder_days_left", new String[] {"Their time comes in 1 day", "Their time comes in 2 days"});
		lines.put("screen.aliveworkplace.hall.elder_days_left_charm", new String[] {"Their time comes in 1 day (an Evergreen Charm keeps them)",
			"Their time comes in 2 days (an Evergreen Charm keeps them)"});
		lines.put("screen.aliveworkplace.request.before_festival", new String[] {"Before the next festival (1 day)", "Before the next festival (2 days)"});
		lines.put("screen.aliveworkplace.hall.quest_days", new String[] {"Up for 1 more day", "Up for 2 more days"});
		lines.put("screen.aliveworkplace.inn.staying", new String[] {"Staying 1 more day", "Staying 2 more days"});
		lines.put("screen.aliveworkplace.legends.dead", new String[] {"Dead with no grave: their place is kept 1 more day",
			"Dead with no grave: their place is kept 2 more days"});
		lines.put("message.aliveworkplace.trainer.state", new String[] {"right-click to battle · 1 battle", "right-click to battle · 2 battles"});
		lines.put("message.aliveworkplace.pokemon_trader.state", new String[] {"right-click to trade Pokémon · 1 trade", "right-click to trade Pokémon · 2 trades"});
		lines.put("message.aliveworkplace.shop.log.header_items", new String[] {"Your shop: 1 sale. Latest:", "Your shop: 2 sales. Latest:"});
		lines.put("screen.aliveworkplace.life_story.next_hearts", new String[] {"They'll have something to tell you at 1 heart",
			"They'll have something to tell you at 2 hearts"});
		lines.put("screen.aliveworkplace.life_story.at_hearts", new String[] {"A story for 1 heart", "A story for 2 hearts"});
		lines.put("message.aliveworkplace.money.emeralds", new String[] {"1 emerald", "2 emeralds"});
		lines.forEach((key, want) -> {
			check(wrong, Words.counted(key, 1, 1).getString(), want[0]);
			check(wrong, Words.counted(key, 2, 2).getString(), want[1]);
		});
		// the count after other words
		check(wrong, Words.counted("legend.aliveworkplace.overhead.mood_wants", 1, "Gold", 1).getString(), "Wants Gold in the chest, 1 day left");
		check(wrong, Words.counted("message.aliveworkplace.colony.cooldown", 1, "Oakvale", 1).getString(), "Oakvale can send its next colony in 1 day.");
		check(wrong, Words.counted("message.aliveworkplace.feud.refused_too_soon", 1, "Elmford", 1).getString(),
			"The feud with Elmford must stand a day before it can become a war (1 hour left).");
		check(wrong, Words.counted("command.aliveworkplace.classes.settling", 1, "Thornholm", 1).getString(), "Thornholm is settling in: 1 day left.");
		check(wrong, Words.counted("message.aliveworkplace.fossil.taken", 1, "Ada", "Helix Fossil", 1).getString(),
			"Ada takes the Helix Fossil: it'll be revived in about 1 minute of their work.");
		check(wrong, Words.counted("message.aliveworkplace.fossil.queued", 1, "Ada", "Helix Fossil", 1, 2).getString(),
			"Ada takes the Helix Fossil: it'll be revived in about 1 minute of their work (2 ahead of it).");
		check(wrong, Words.counted("message.aliveworkplace.nurse.wait", 1, "Joy", 1).getString(), "Joy just treated you. Come back in 1 second.");
		check(wrong, Words.counted("message.aliveworkplace.shop.log.header", 1, 1, "3 CobbleDollars").getString(), "Your shop: 1 sale, 3 CobbleDollars taken in all. Latest:");
		check(wrong, Words.counted("message.aliveworkplace.trainer.won_emeralds", 1, "Trainer", 1).getString(), "You beat Trainer! +1 emerald");
		check(wrong, Words.counted("message.aliveworkplace.mail.posted", 1, 1, "Bob").getString(), "Posted 1 item to Bob. A postman will pick it up.");
		check(wrong, Words.counted("message.aliveworkplace.mail.posted", 3, 3, "Bob").getString(), "Posted 3 items to Bob. A postman will pick them up.");
		check(wrong, Words.counted("screen.aliveworkplace.shapes.count", 1, 1, "Stone Bricks").getString(), "1 block of Stone Bricks");
		check(wrong, Words.counted("screen.aliveworkplace.shapes.count", 2, 2, "Stone Bricks").getString(), "2 blocks of Stone Bricks");
		check(wrong, Words.counted("screen.aliveworkplace.table.size", 1, 1, 1, 1, 1).getString(), "1 × 1 × 1 · 1 block");
		check(wrong, Words.counted("screen.aliveworkplace.table.size_below", 1, 1, 1, 1, 1, 2).getString(), "1 × 1 × 1 · 1 block, and 2 below ground");
		// two counts in one line, each in its own form
		for (String key : List.of("screen.aliveworkplace.realm.invite_left", "screen.aliveworkplace.pact.waiting")) {
			check(wrong, Words.countedTwo(key, 1, 1, 1, 1).getString(), "Waiting for an answer: 1 day and 1 hour left");
			check(wrong, Words.countedTwo(key, 1, 5, 1, 5).getString(), "Waiting for an answer: 1 day and 5 hours left");
			check(wrong, Words.countedTwo(key, 2, 1, 2, 1).getString(), "Waiting for an answer: 2 days and 1 hour left");
			check(wrong, Words.countedTwo(key, 2, 5, 2, 5).getString(), "Waiting for an answer: 2 days and 5 hours left");
		}
		check(wrong, Words.countedTwo("screen.aliveworkplace.feud.at_war", 1, 1, 1, 1).getString(), "At war: 1 day and 1 hour left");
		check(wrong, Words.countedTwo("screen.aliveworkplace.feud.fades", 2, 1, 2, 1).getString(), "Fades in 2 days and 1 hour without a war");
		check(wrong, Words.countedTwo("screen.aliveworkplace.table.all_materials_title", 1, 64, 1, 64).getString(), "1 material · 64 items");
		check(wrong, Words.countedTwo("screen.aliveworkplace.table.all_materials_title", 1, 1, 1, 1).getString(), "1 material · 1 item");
		check(wrong, Words.countedTwo("screen.aliveworkplace.table.all_materials_title", 3, 40, 3, 40).getString(), "3 materials · 40 items");
		helper.assertTrue(wrong.isEmpty(), String.join("; ", wrong));
		helper.succeed();
	}

	/** A quest's wait reads "Wait 1 day" through the objective itself, and a bring names many of an item in the plural. */
	//$ gametest 'FabricGameTest.EMPTY_STRUCTURE'
	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
	public void questLinesCountTheirDaysAndItems(GameTestHelper helper) {
		List<String> wrong = new ArrayList<>();
		check(wrong, new Objectives.Wait(1).line().getString(), "Wait 1 day");
		check(wrong, new Objectives.Wait(3).line().getString(), "Wait 3 days");
		check(wrong, new Objectives.Bring("minecraft:torch", 32, "hall", Optional.empty()).line().getString(), "Bring 32 Torches");
		check(wrong, new Objectives.Bring("minecraft:torch", 1, "hall", Optional.empty()).line().getString(), "Bring 1 Torch");
		check(wrong, new Objectives.Bring("minecraft:bread", 16, "hall", Optional.empty()).line().getString(), "Bring 16 Bread");
		// an item the lang has no plural for keeps its own name
		check(wrong, Words.itemName(Items.DIRT, 5).getString(), "Dirt");
		check(wrong, Words.itemName(Items.COOKIE, 8).getString(), "Cookies");
		check(wrong, Words.itemName(Items.COOKIE, 1).getString(), "Cookie");
		helper.assertTrue(wrong.isEmpty(), String.join("; ", wrong));
		helper.succeed();
	}
	/** B568: a kill step reads right for one and for several, and a creature with no plural in the lang is counted with ×. */
	//$ gametest 'FabricGameTest.EMPTY_STRUCTURE'
	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
	public void killLinesCountTheirCreatures(GameTestHelper helper) {
		List<String> wrong = new ArrayList<>();
		check(wrong, new Objectives.Kill("minecraft:zombie", 1, false).line().getString(), "Defeat 1 Zombie");
		check(wrong, new Objectives.Kill("minecraft:zombie", 2, false).line().getString(), "Defeat 2 Zombies");
		check(wrong, new Objectives.Kill("minecraft:vindicator", 6, false).line().getString(), "Defeat 6 Vindicators");
		check(wrong, new Objectives.Kill("minecraft:witch", 3, false).line().getString(), "Defeat 3 Witches");
		check(wrong, new Objectives.Kill("minecraft:drowned", 4, false).line().getString(), "Defeat 4 Drowned");
		// no plural in the lang (a modded creature, here one we left out on purpose): the count goes after the name
		check(wrong, new Objectives.Kill("minecraft:allay", 2, false).line().getString(), "Defeat Allay ×2");
		check(wrong, new Objectives.Kill("minecraft:allay", 1, false).line().getString(), "Defeat 1 Allay");
		check(wrong, new Objectives.Bring("minecraft:apple", 5, "hall", Optional.empty()).line().getString(), "Bring 5 Apples");
		helper.assertTrue(wrong.isEmpty(), String.join("; ", wrong));
		helper.succeed();
	}
}
