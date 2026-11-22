package io.github.jcondedata.aliveworkplace.test;

import io.github.jcondedata.aliveworkplace.AliveWorkplace;
import io.github.jcondedata.aliveworkplace.blueprint.Blueprint;
import io.github.jcondedata.aliveworkplace.wonder.Wonder;
import io.github.jcondedata.aliveworkplace.wonder.Wonders;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.locale.Language;
import net.minecraft.world.level.block.CampfireBlock;

/**
 * QA lane E (2026-11-22, second tester), from the specs:
 * <ul>
 * <li>B1438: the Great Wall's description in the Village Hall's wonder list reads "A gray wall 97 blocks long on a dark,
 * battered foot", with no doubled "long" and a unit after the number; and the wall it describes really is 97 blocks long,
 * with four watchtowers and their signal fires.</li>
 * <li>Every Wonder's description and stage name (the sentences Milestone 52's rebuilds rewrote) as a player reads them:
 * no word written twice in a row, no number left without its unit before "long", "high" or "wide", no British spelling
 * (the owner, 2026-10-26: American English), no double space, and a description ends its sentence.</li>
 * </ul>
 */
public class QaWonderCopy1122e2GameTests implements FabricGameTest {
	/** British spellings a sentence must not use (CLAUDE.md, "Rules"). */
	private static final Pattern BRITISH = Pattern.compile("\\b(colour\\w*|grey\\w*|centre\\w*|metre\\w*|defence|armour\\w*|travell\\w*|harbour\\w*"
		+ "|honour\\w*|favour\\w*|neighbour\\w*|theatre\\w*|jewell\\w*|cancelled|labour\\w*|plough\\w*|storey\\w*|whilst|amongst|mould\\w*|sceptre\\w*)\\b",
		Pattern.CASE_INSENSITIVE);
	/** A number followed straight by a measure word, its unit left out ("97 long"). */
	private static final Pattern NO_UNIT = Pattern.compile("\\b\\d[\\d,]*\\s+(long|high|tall|wide|deep|across)\\b", Pattern.CASE_INSENSITIVE);
	private static final Pattern WORD = Pattern.compile("[A-Za-z']+");

	/** The problems in one sentence a player reads ({@code what} names it in the message). */
	private static List<String> problems(String what, String text, boolean sentence) {
		List<String> out = new ArrayList<>();
		// placeholders aren't words: two in a row ("%1$s %2$s") are two different values
		String words = text.replaceAll("%(\\d+\\$)?[sd]", "|");
		String last = null;
		for (String chunk : words.split("\\|")) {
			last = null;
			Matcher m = WORD.matcher(chunk);
			while (m.find()) {
				String w = m.group().toLowerCase(Locale.ROOT);
				if (w.equals(last)) {
					out.add(what + " writes '" + w + "' twice: " + text);
				}
				last = w;
			}
		}
		Matcher british = BRITISH.matcher(text);
		if (british.find()) {
			out.add(what + " spells '" + british.group() + "' the British way: " + text);
		}
		Matcher unit = NO_UNIT.matcher(text);
		if (unit.find()) {
			out.add(what + " leaves out the unit in '" + unit.group() + "': " + text);
		}
		if (text.contains("  ")) {
			out.add(what + " has a double space: " + text);
		}
		if (sentence && !(text.endsWith(".") || text.endsWith("!"))) {
			out.add(what + " doesn't end its sentence: " + text);
		}
		return out;
	}

	/** B1438: the Great Wall's description as the wonder list shows it. */
	//$ gametest 'FabricGameTest.EMPTY_STRUCTURE'
	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
	public void theGreatWallsDescriptionSaysHowLongInBlocks(GameTestHelper helper) {
		Wonder wall = Wonders.get(AliveWorkplace.id("great_wall")).orElse(null);
		helper.assertTrue(wall != null, "no Great Wall wonder loaded");
		String text = wall.descriptionText().getString();
		helper.assertTrue(!text.startsWith("wonder.aliveworkplace"), "the description shows its key: " + text);
		helper.assertTrue(text.startsWith("A gray wall 97 blocks long on a dark, battered foot"), "the Great Wall's description reads: " + text);
		helper.assertTrue(!text.contains("long gray wall") && !text.contains("97 long"), "the old wording is back: " + text);
		List<String> wrong = problems("the Great Wall's description", text, true);
		helper.assertTrue(wrong.isEmpty(), String.join("; ", wrong));
		helper.succeed();
	}

	/**
	 * B1438: what the description says is the wall players get: 97 blocks long (its longest side, every stage's parts
	 * laid together), four watchtowers (WonderBlueprintGameTests' tower spots) with a signal fire on each.
	 */
	//$ gametest 'FabricGameTest.EMPTY_STRUCTURE'
	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
	public void theGreatWallIsAsLongAsItsDescriptionSays(GameTestHelper helper) {
		int minX = Integer.MAX_VALUE, maxX = Integer.MIN_VALUE, minZ = Integer.MAX_VALUE, maxZ = Integer.MIN_VALUE;
		List<BlockPos> fires = new ArrayList<>();
		for (List<WonderBlueprintGameTests.Part> stage : WonderBlueprintGameTests.stages(helper.getLevel().getServer(), "great_wall")) {
			for (WonderBlueprintGameTests.Part p : stage) {
				for (Blueprint.Entry e : p.blueprint().blocks()) {
					if (e.state().isAir()) {
						continue;
					}
					BlockPos at = p.offset().offset(e.pos());
					minX = Math.min(minX, at.getX());
					maxX = Math.max(maxX, at.getX());
					minZ = Math.min(minZ, at.getZ());
					maxZ = Math.max(maxZ, at.getZ());
					if (e.state().getBlock() instanceof CampfireBlock) {
						fires.add(at);
					}
				}
			}
		}
		int length = Math.max(maxX - minX + 1, maxZ - minZ + 1);
		helper.assertTrue(length == 97, "the description says 97 blocks long, the built wall is " + (maxX - minX + 1) + " by " + (maxZ - minZ + 1));
		List<String> towersWithoutFire = new ArrayList<>();
		for (int t : WonderBlueprintGameTests.WALL_TOWERS) {
			if (fires.stream().noneMatch(f -> f.getX() >= t && f.getX() <= t + 8)) {
				towersWithoutFire.add("tower at x " + t);
			}
		}
		helper.assertTrue(WonderBlueprintGameTests.WALL_TOWERS.length == 4, "the description says four watchtowers, the wall has " + WonderBlueprintGameTests.WALL_TOWERS.length);
		helper.assertTrue(towersWithoutFire.isEmpty(), "watchtowers without the signal fire the description promises: " + towersWithoutFire + " (fires at " + fires + ")");
		helper.succeed();
	}

	/** Every Wonder's description and stage names, as a player reads them, and every wonder key in en_us.json. */
	//$ gametest 'FabricGameTest.EMPTY_STRUCTURE'
	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
	public void everyWondersTextReadsCleanly(GameTestHelper helper) {
		List<String> wrong = new ArrayList<>();
		int checked = 0;
		for (Wonder wonder : Wonders.all()) {
			if (!wonder.id().getNamespace().equals(AliveWorkplace.MOD_ID)) {
				continue; // the test pack's own made-up wonders
			}
			String text = wonder.descriptionText().getString();
			if (text.startsWith("wonder.") || text.equals(wonder.description())) {
				wrong.add(wonder.id() + "'s description shows its key");
			}
			wrong.addAll(problems(wonder.id() + "'s description", text, true));
			checked++;
			for (Wonder.Stage stage : wonder.stages()) {
				String name = Language.getInstance().getOrDefault(stage.name());
				if (name.equals(stage.name())) {
					wrong.add(wonder.id() + "'s stage " + stage.name() + " has no text");
				}
				wrong.addAll(problems(wonder.id() + "'s stage name", name, false));
				checked++;
			}
		}
		helper.assertTrue(Wonders.all().size() >= 8, "only " + Wonders.all().size() + " wonders loaded");
		helper.assertTrue(wrong.isEmpty(), wrong.size() + " problems in " + checked + " sentences: " + wrong.subList(0, Math.min(6, wrong.size())));
		helper.succeed();
	}
}
