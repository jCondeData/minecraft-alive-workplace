package io.github.jcondedata.aliveworkplace.test;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.locale.Language;
import net.minecraft.network.chat.Component;

/**
 * QA (lane E, the sentences players read; kin of B413 and B444): chat, chronicle and hall lines whose count is often
 * exactly one (one villager called home, a golem carrying one item, a parcel of one item, one villager hexed or cured,
 * an account of one emerald) must not read "1 villagers" or "1 items". Each passes with a {@code .one} form, as
 * {@code Words.counted} uses (ROADMAP 24.5), or with a sentence that reads right at 1.
 */
public class QaCountedLinesBugTests implements FabricGameTest {
	private static final Pattern WRONG = Pattern.compile("\\b1 (villagers|items|emeralds)\\b");

	/** Each key and its arguments with the count at 1 (the other arguments as the game fills them). */
	private static final List<Object[]> LINES = List.of(
		new Object[] {"message.aliveworkplace.hall.recalled", 1},
		new Object[] {"screen.aliveworkplace.hall.golem.hauling", 1},
		new Object[] {"screen.aliveworkplace.hall.golem.harvesting", 1},
		new Object[] {"message.aliveworkplace.mail.track_to", "Alex", 1, "on its way"},
		new Object[] {"message.aliveworkplace.mail.track_from", "Alex", 1, "on its way"},
		new Object[] {"chronicle.aliveworkplace.coven.hex", 1},
		new Object[] {"message.aliveworkplace.sickness.cured", 1},
		new Object[] {"chronicle.aliveworkplace.sickness.cured", 1, "Rowan"},
		new Object[] {"message.aliveworkplace.bank.deposited", "1 emerald", 1},
		new Object[] {"message.aliveworkplace.bank.withdrawn", "1 emerald", 1});

	//$ gametest 'FabricGameTest.EMPTY_STRUCTURE'
	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
	public void linesCountingOneReadInTheSingular(GameTestHelper helper) {
		Language lang = Language.getInstance();
		List<String> wrong = new ArrayList<>();
		for (Object[] line : LINES) {
			String key = (String) line[0];
			helper.assertTrue(lang.has(key), "no such line: " + key);
			Object[] args = java.util.Arrays.copyOfRange(line, 1, line.length);
			String read = Component.translatable(key, args).getString();
			if (!lang.has(key + ".one") && WRONG.matcher(read).find()) {
				wrong.add(key + " ('" + read + "')");
			}
		}
		helper.assertTrue(wrong.isEmpty(), wrong.size() + " of " + LINES.size() + " lines read wrong at 1: " + wrong);
		helper.succeed();
	}
}
