package io.github.jcondedata.aliveworkplace.test;

import io.github.jcondedata.aliveworkplace.AliveWorkplace;
import io.github.jcondedata.aliveworkplace.WorkplaceConfig;
import io.github.jcondedata.aliveworkplace.registry.ModBlocks;
import io.github.jcondedata.aliveworkplace.story.Arcs;
import io.github.jcondedata.aliveworkplace.wonder.Wonders;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.commands.CommandSource;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.phys.Vec2;
import net.minecraft.world.phys.Vec3;

/**
 * QA (lane-d-1116-1907), B1323 from the spec: "both lists [disabledArcs, disabledWonders] trim and lower-case their
 * entries, so the named story or Wonder is off". Written from the config file the server owner edits, through the
 * game's own start (the file read, written back, applied), and through what the player or operator then meets: the
 * Wonder list and its refusal, and {@code /workplace story start}. Tabs and line breaks round a name, mixed case with a
 * namespace, case-only repeats, an entry that is no id beside good ones, and the story switched back on.
 */
public class QaConfigIds1116dGameTests implements FabricGameTest {
	private static final String AREA = "aliveworkplace_test:big_area";

	private static Path dirWith(String body) {
		try {
			Path dir = Files.createTempDirectory("aliveworkplace-qa1116d");
			Files.writeString(dir.resolve(WorkplaceConfig.FILE), "{\"configVersion\": 5, " + body + "}");
			return dir;
		} catch (IOException e) {
			throw new GameTestAssertException("no temp dir: " + e);
		}
	}

	private static String read(Path dir) {
		try {
			return Files.readString(dir.resolve(WorkplaceConfig.FILE));
		} catch (IOException e) {
			throw new GameTestAssertException("can't read the file back: " + e);
		}
	}

	private static boolean wonderListed(ResourceLocation id) {
		return Wonders.all().stream().anyMatch(w -> w.id().equals(id));
	}

	/** Tabs and line breaks round the names (a file edited by hand) read as the names: the story and both Wonders are off. */
	//$ gametest_batch 'FabricGameTest.EMPTY_STRUCTURE' '"qaConfigIds1116d"'
	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE, batch = "qaConfigIds1116d")
	public void qaTabsAndLineBreaksRoundANameStillSwitchItOff(GameTestHelper helper) {
		List<String> problems = new ArrayList<>();
		try {
			Path dir = dirWith("\"disabledArcs\": [\"\\tBandit_King\\n\", \"\\tAliveWorkplace:The_Sickness \"],"
				+ " \"disabledWonders\": [\"\\tGreat_Cathedral\\r\\n\", \" ALIVEWORKPLACE:LIGHTHOUSE\\t\"]");
			WorkplaceConfig c = WorkplaceConfig.loadAndApply(dir);
			if (!Arcs.disabled(AliveWorkplace.id("bandit_king")) || !Arcs.disabled(AliveWorkplace.id("the_sickness"))) {
				problems.add("stories off: bandit_king " + Arcs.disabled(AliveWorkplace.id("bandit_king")) + ", the_sickness "
					+ Arcs.disabled(AliveWorkplace.id("the_sickness")));
			}
			if (Arcs.disabled(AliveWorkplace.id("lost_caravan"))) {
				problems.add("a story nobody named is off");
			}
			for (String w : List.of("great_cathedral", "lighthouse")) {
				ResourceLocation id = AliveWorkplace.id(w);
				if (wonderListed(id)) {
					problems.add(w + " is still in the Wonder list");
				}
			}
			if (Wonders.get(AliveWorkplace.id("colossus")).isPresent() && !wonderListed(AliveWorkplace.id("colossus"))) {
				problems.add("the Colossus, never named, is gone from the Wonder list");
			}
			if (!c.disabledArcs.equals(List.of("bandit_king", "aliveworkplace:the_sickness"))
				|| !c.disabledWonders.equals(List.of("great_cathedral", "aliveworkplace:lighthouse"))) {
				problems.add("read as " + c.disabledArcs + " and " + c.disabledWonders);
			}
			String written = read(dir);
			if (written.contains("\\t") || written.contains("Bandit_King") || written.contains("LIGHTHOUSE")) {
				problems.add("not written back plain: " + written.substring(Math.max(0, written.indexOf("disabledArcs") - 2),
					Math.min(written.length(), written.indexOf("disabledArcs") + 80)));
			}
		} finally {
			new WorkplaceConfig().apply();
		}
		helper.assertTrue(problems.isEmpty(), String.join("; ", problems));
		helper.succeed();
	}

	/**
	 * A Wonder written with capitals is refused when a village asks to begin it, with the line a player reads, and
	 * one never named is not refused for that; the same name in two cases is one entry.
	 */
	//$ gametest_batch 'FabricGameTest.EMPTY_STRUCTURE' '"qaConfigIds1116d"'
	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE, batch = "qaConfigIds1116d")
	public void qaAWonderWrittenWithCapitalsIsRefusedToTheVillage(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		BlockPos nowhere = helper.absolutePos(new BlockPos(0, 1, 0));
		List<String> problems = new ArrayList<>();
		try {
			WorkplaceConfig c = WorkplaceConfig.loadAndApply(dirWith("\"disabledWonders\": [\"Hanging_Gardens\", \"HANGING_GARDENS\", \"hanging_gardens \"]"));
			if (!c.disabledWonders.equals(List.of("hanging_gardens"))) {
				problems.add("three spellings of one Wonder read as " + c.disabledWonders);
			}
			Optional<Component> why = Wonders.capRefusal(level, nowhere, AliveWorkplace.id("hanging_gardens"));
			if (why.isEmpty() || !why.get().getString().equals("There's no Wonder aliveworkplace:hanging_gardens on this server.")) {
				problems.add("the refusal for the Hanging Gardens: " + why.map(Component::getString).orElse("none"));
			}
			if (wonderListed(AliveWorkplace.id("hanging_gardens"))) {
				problems.add("the Hanging Gardens are still in the Wonder list");
			}
			Optional<Component> other = Wonders.capRefusal(level, nowhere, AliveWorkplace.id("observatory"));
			if (other.isPresent() && other.get().getString().startsWith("There's no Wonder")) {
				problems.add("the Observatory, never named, is refused as unknown: " + other.get().getString());
			}
		} finally {
			new WorkplaceConfig().apply();
		}
		helper.assertTrue(problems.isEmpty(), String.join("; ", problems));
		helper.succeed();
	}

	/** An entry that is no id ("great library", a space inside) is skipped; the good entries beside it still apply. */
	//$ gametest_batch 'FabricGameTest.EMPTY_STRUCTURE' '"qaConfigIds1116d"'
	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE, batch = "qaConfigIds1116d")
	public void qaABadEntryDoesntStopTheGoodOnesBesideIt(GameTestHelper helper) {
		List<String> problems = new ArrayList<>();
		try {
			WorkplaceConfig.loadAndApply(dirWith("\"disabledWonders\": [\"great library\", \"Great_Wall\", \"\", \"  \"],"
				+ " \"disabledArcs\": [\"\", \"Midwives\", \"   \"]"));
			if (wonderListed(AliveWorkplace.id("great_wall"))) {
				problems.add("Great_Wall beside a bad entry is still in the Wonder list");
			}
			if (!Arcs.disabled(AliveWorkplace.id("midwives"))) {
				problems.add("Midwives beside blank entries is still on");
			}
			if (!wonderListed(AliveWorkplace.id("great_library")) && Wonders.get(AliveWorkplace.id("great_library")).isPresent()) {
				problems.add("\"great library\" (not an id) switched the Great Library off");
			}
		} finally {
			new WorkplaceConfig().apply();
		}
		helper.assertTrue(problems.isEmpty(), String.join("; ", problems));
		helper.succeed();
	}

	/**
	 * The operator's {@code /workplace story start bandit_king} is refused with the line naming the story while the
	 * file lists it as " Bandit_King "; with the list emptied (the server's config read again) it is no longer refused
	 * as switched off.
	 */
	//$ gametest_ticks_batch AREA '40' '"qaConfigIds1116dStory"'
	@GameTest(template = AREA, timeoutTicks = 40, batch = "qaConfigIds1116dStory")
	public void qaTheStoryCommandSaysAStoryWrittenWithCapitalsIsOff(GameTestHelper helper) {
		Leftovers.clear(helper);
		Leftovers.halls(helper);
		ServerLevel level = helper.getLevel();
		BlockPos at = new BlockPos(4, 2, 4);
		BlockPos hall = helper.absolutePos(at);
		Leftovers.after(helper, () -> Arcs.stopAll(level, hall));
		helper.setBlock(at, ModBlocks.VILLAGE_HALL);
		Arcs.stopAll(level, hall);
		List<String> said = new ArrayList<>();
		CommandSource out = new CommandSource() {
			@Override
			public void sendSystemMessage(Component message) {
				said.add(message.getString());
			}

			@Override
			public boolean acceptsSuccess() {
				return true;
			}

			@Override
			public boolean acceptsFailure() {
				return true;
			}

			@Override
			public boolean shouldInformAdmins() {
				return false;
			}
		};
		CommandSourceStack op = new CommandSourceStack(out, Vec3.atCenterOf(hall.south(2)), Vec2.ZERO, level, 4, "qa", Component.literal("qa"),
			level.getServer(), null);
		String off = "The Bandit King is switched off on this server (disabledArcs in the config).";
		try {
			WorkplaceConfig.loadAndApply(dirWith("\"disabledArcs\": [\" Bandit_King \"]"));
			level.getServer().getCommands().performPrefixedCommand(op, "workplace story start bandit_king");
			helper.assertTrue(said.contains(off), "the story command with \" Bandit_King \" in disabledArcs: " + said);
			helper.assertTrue(Arcs.running(level, hall) == null, "the Bandit King started though switched off");
			said.clear();
			WorkplaceConfig.loadAndApply(dirWith("\"disabledArcs\": []"));
			level.getServer().getCommands().performPrefixedCommand(op, "workplace story start bandit_king");
			helper.assertTrue(!said.isEmpty() && !said.contains(off), "switched back on, the story command still says: " + said);
		} finally {
			Arcs.stopAll(level, hall);
			new WorkplaceConfig().apply();
		}
		helper.succeed();
	}
}
