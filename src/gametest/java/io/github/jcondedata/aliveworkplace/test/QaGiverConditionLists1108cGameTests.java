package io.github.jcondedata.aliveworkplace.test;

import com.google.gson.JsonParser;
import io.github.jcondedata.aliveworkplace.rules.Conditions;
import java.util.List;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;

/**
 * QA (lane c, 2026-11-08) for the giver conditions of personal requests (ROADMAP 31.9, docs/wiki/personal-requests.md):
 * {@code job} takes {@code jobs} and {@code villager_type} takes {@code types}, each a list of ids. A pack author who
 * writes one id as a plain string (or a number, or an object) instead of a list gets the file refused with a message
 * that names the field and says it wants a list, not a bare Gson "Not a JSON Array". Written for the one survivor of a
 * local mutation run on GiverConditions (20 of 21 killed): line 65's {@code ||} turned into {@code &&}.
 */
public class QaGiverConditionLists1108cGameTests implements FabricGameTest {

	private static String refusal(String json) {
		try {
			Conditions.parse(JsonParser.parseString(json).getAsJsonObject());
			return null;
		} catch (IllegalArgumentException e) {
			return e.getMessage();
		}
	}

	/** A list field given as anything but a list is refused, and the message names the field and asks for a list of ids. */
	//$ gametest 'FabricGameTest.EMPTY_STRUCTURE'
	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
	public void aJobOrTypeWrittenAsOneIdInsteadOfAListIsRefusedNamingTheField(GameTestHelper helper) {
		for (String[] c : List.of(
			new String[] {"job", "jobs", "\"minecraft:farmer\""},
			new String[] {"job", "jobs", "3"},
			new String[] {"job", "jobs", "{\"id\": \"minecraft:farmer\"}"},
			new String[] {"villager_type", "types", "\"minecraft:desert\""},
			new String[] {"villager_type", "types", "true"})) {
			String json = "{\"type\": \"" + c[0] + "\", \"" + c[1] + "\": " + c[2] + "}";
			String message = refusal(json);
			helper.assertTrue(message != null, "expected " + json + " to be refused, it was read");
			helper.assertTrue(message.contains("'" + c[1] + "'") && message.contains("a list of ids"),
				"expected the refusal of " + json + " to name '" + c[1] + "' and ask for a list of ids, it says: " + message);
		}
		// The same fields written as lists are read (the control).
		helper.assertTrue(refusal("{\"type\": \"job\", \"jobs\": [\"minecraft:farmer\"]}") == null, "a job list of one id is refused");
		helper.assertTrue(refusal("{\"type\": \"villager_type\", \"types\": [\"minecraft:desert\"]}") == null, "a type list of one id is refused");
		helper.succeed();
	}
}
