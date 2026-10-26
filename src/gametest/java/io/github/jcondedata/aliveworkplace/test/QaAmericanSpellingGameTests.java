package io.github.jcondedata.aliveworkplace.test;

import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;

/**
 * QA (lane E, translations, 2026-10-26): CLAUDE.md, "Player-visible text ... Write it in American English (owner,
 * 2026-10-26): defense, color, armor, jeweler, canceled, gray; never rename a key or id to match". Every sentence a
 * player reads in English, every value of {@code en_us.json} (the keys stay as they are), uses no British spelling of
 * the list below (whole words, any case). "Draught" is left alone: a potion's draught is spelled so in American
 * English too (the Smith's Draught).
 */
public class QaAmericanSpellingGameTests implements FabricGameTest {
	/** British spellings a player could meet, as whole words. */
	static final Pattern BRITISH = Pattern.compile("\\b("
		+ "(?:colo|hono|favo|labo|neighbo|harbo|flavo|humo|rumo|savo|vapo|behavio|endeavo|splendo|parlo|armo|valo|vigo|odo|clamo)ur\\w*"
		+ "|\\w*(?:centre|theatre|metre|fibre|litre|sombre|calibre)s?"
		+ "|defence\\w*|offence\\w*|licence\\w*|pretence|grey\\w*|jeweller\\w*"
		+ "|cancell(?:ed|ing)|travell(?:ed|ing|er|ers)|levell(?:ed|ing)|modell(?:ed|ing)|fuell(?:ed|ing)|labell(?:ed|ing)|signall(?:ed|ing)"
		+ "|marvellous|woollen|plough\\w*|mould\\w*|tyres?|catalogue\\w*|programme\\w*|cheques?|storeys|manoeuvr\\w*|aluminium"
		+ "|(?:organi|reali|recogni|apologi|speciali|priori|summari|memori|categori|civili|criti|emphasi|minimi|maximi|optimi|standardi"
		+ "|symboli|sympathi|visuali|finali|authori|characteri|customi|harmoni|itemi|moderni|neutrali|personali|stabili|utili)s(?:e|es|ed|ing|ation|ations)"
		+ "|practis\\w+|analys(?:e|es|ed|ing)|enrol|enrolment|fulfil|fulfilment|instalment|skilful|wilful|sceptic\\w*"
		+ ")\\b", Pattern.CASE_INSENSITIVE);

	static com.google.gson.JsonObject english() {
		try (java.io.InputStream in = Files.newInputStream(net.fabricmc.loader.api.FabricLoader.getInstance()
				.getModContainer(io.github.jcondedata.aliveworkplace.AliveWorkplace.MOD_ID).orElseThrow()
				.findPath("assets/aliveworkplace/lang/en_us.json").orElseThrow())) {
			return com.google.gson.JsonParser.parseReader(new java.io.InputStreamReader(in, java.nio.charset.StandardCharsets.UTF_8)).getAsJsonObject();
		} catch (Exception e) {
			throw new GameTestAssertException("can't read en_us.json: " + e);
		}
	}

	/** No value of en_us.json spells a word the British way. */
	//$ gametest 'FabricGameTest.EMPTY_STRUCTURE'
	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
	public void everyEnglishLineIsSpelledTheAmericanWay(GameTestHelper helper) {
		com.google.gson.JsonObject lang = english();
		List<String> british = new ArrayList<>();
		for (Map.Entry<String, com.google.gson.JsonElement> e : lang.entrySet()) {
			Matcher m = BRITISH.matcher(e.getValue().getAsString());
			while (m.find()) {
				british.add(e.getKey() + " says '" + m.group(1) + "'");
			}
		}
		helper.assertTrue(lang.size() > 1000, "only " + lang.size() + " lines read from en_us.json");
		helper.assertTrue(british.isEmpty(), british.size() + " British spelling(s) in what players read: " + british);
		helper.succeed();
	}

	/** The pattern itself: it finds each of CLAUDE.md's examples and leaves their American forms and look-alikes alone. */
	//$ gametest 'FabricGameTest.EMPTY_STRUCTURE'
	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
	public void theListFindsTheBritishFormsOnly(GameTestHelper helper) {
		for (String word : List.of("Defence", "colour", "Colours", "armour", "jeweller", "cancelled", "grey", "the town centre", "organised", "Ploughing")) {
			helper.assertTrue(BRITISH.matcher(word).find(), "'" + word + "' isn't caught");
		}
		for (String word : List.of("Defense", "color", "armor", "jeweler", "canceled", "gray", "center", "organized", "your", "four", "hour",
				"tour", "pour", "flour", "rise", "promise", "exercise", "Smith's Draught", "mountain", "treasure", "contour")) {
			helper.assertTrue(!BRITISH.matcher(word).find(), "'" + word + "' is caught as British");
		}
		helper.succeed();
	}
}
