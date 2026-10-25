package io.github.jcondedata.aliveworkplace.test;

import io.github.jcondedata.aliveworkplace.work.Stations;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

/**
 * QA (lane E, the player-facing sentences): a sentence that puts a job's name after "a" must read "an" before a vowel.
 * The workstation tooltip (21.1a, Shift held) says "A villager without a job takes it as a %s"; for every workstation
 * whose own job begins with a vowel (the Armorer's blast furnace, the Innkeeper's, the Orchard Keeper's, the
 * Undertaker's) that reads "as a Armorer". Every workstation item's Shift lines are read as a player sees them.
 */
public class QaArticleGameTests implements FabricGameTest {
	/** "a" before a word that starts with a vowel sound (a Unicorn, a Uniform, a Useful one are fine). */
	private static final Pattern A_BEFORE_VOWEL = Pattern.compile("\\b[aA] (?:[AEIOaeio]|U(?!ni|s[eu]))");

	//$ gametest 'FabricGameTest.EMPTY_STRUCTURE'
	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
	public void aStationsTooltipSaysAnBeforeAJobThatStartsWithAVowel(GameTestHelper helper) {
		List<String> wrong = new ArrayList<>();
		int read = 0;
		for (Item item : BuiltInRegistries.ITEM) {
			if (!(item instanceof BlockItem)) {
				continue;
			}
			for (Component line : Stations.tooltip(new ItemStack(item), true)) {
				read++;
				String text = line.getString();
				if (A_BEFORE_VOWEL.matcher(text).find()) {
					wrong.add(BuiltInRegistries.ITEM.getKey(item).getPath() + ": \"" + text + "\"");
				}
			}
		}
		helper.assertTrue(read > 20, "setup: only " + read + " workstation tooltip lines read");
		helper.assertTrue(wrong.isEmpty(), wrong.size() + " workstation tooltips say \"a\" before a vowel: " + wrong);
		helper.succeed();
	}
}
