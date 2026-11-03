package io.github.jcondedata.aliveworkplace.test;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import io.github.jcondedata.aliveworkplace.AliveWorkplace;
import io.github.jcondedata.aliveworkplace.hall.Chronicle;
import io.github.jcondedata.aliveworkplace.hall.VillageHallBlockEntity;
import io.github.jcondedata.aliveworkplace.hall.VillageHalls;
import io.github.jcondedata.aliveworkplace.story.ArcState;
import io.github.jcondedata.aliveworkplace.story.Arcs;
import io.github.jcondedata.aliveworkplace.story.Quest;
import io.github.jcondedata.aliveworkplace.story.QuestFiles;
import io.github.jcondedata.aliveworkplace.story.Stories;
import io.github.jcondedata.aliveworkplace.threat.Threats;
import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.commands.CommandSource;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.util.RandomSource;
import net.minecraft.world.Difficulty;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.Vec2;
import net.minecraft.world.phys.Vec3;

/**
 * QA (lane-c-1103-1253), ROADMAP 31.2 and 31.4, written from the spec: the gaps the engines' own tests left. A broken
 * quest file is skipped with exactly one log line naming it; the real {@code /reload} path reads quest and arc files
 * again; an arc whose file was removed ends quietly with one log line after a save and reload; a {@code threat} arc
 * never starts in a quiet village (At peace, threat level 0, Peaceful) but does in a threatened one; the story commands
 * are for operators only; {@code villageQuests} off leaves the arcs and their quests alone; every story sentence is in
 * en_us.json in American English.
 */
public class QaStoryVerify1103cGameTests implements FabricGameTest {
	private static final String HUGE = "aliveworkplace_test:huge_area";
	private static final String TALL = QaSicknessGameTests.TALL;
	private static final BlockPos HALL = new BlockPos(5, 2, 5);
	private static final ResourceLocation THREE = ResourceLocation.fromNamespaceAndPath("aliveworkplace_test", "test/three_chapters");
	private static final ResourceLocation APPLES = ResourceLocation.fromNamespaceAndPath("aliveworkplace_test", "test/apples");
	private static final ResourceLocation THREAT = ResourceLocation.fromNamespaceAndPath("aliveworkplace_test", "qa/threat_1103c");
	private static final long DAY = 24000;

	// ---------------------------------------------------------------- shared

	/** Every log line of {@code level} or above the mod writes while the test runs. */
	private static List<String> logged(GameTestHelper helper, String name, org.apache.logging.log4j.Level level) {
		List<String> seen = java.util.Collections.synchronizedList(new ArrayList<>());
		var appender = new org.apache.logging.log4j.core.appender.AbstractAppender(name, null, null, true,
			org.apache.logging.log4j.core.config.Property.EMPTY_ARRAY) {
			@Override
			public void append(org.apache.logging.log4j.core.LogEvent event) {
				if (event.getLevel().isMoreSpecificThan(level)) {
					seen.add(event.getMessage().getFormattedMessage());
				}
			}
		};
		var root = ((org.apache.logging.log4j.core.LoggerContext) org.apache.logging.log4j.LogManager.getContext(false))
			.getConfiguration().getRootLogger();
		appender.start();
		root.addAppender(appender, null, null);
		Leftovers.after(helper, () -> {
			root.removeAppender(name);
			appender.stop();
		});
		return seen;
	}

	private static List<String> matching(List<String> lines, String part) {
		synchronized (lines) {
			return lines.stream().filter(l -> l.contains(part)).toList();
		}
	}

	private static Resource text(Resource like, String json) {
		return new Resource(like.source(), () -> new ByteArrayInputStream(json.getBytes(StandardCharsets.UTF_8)));
	}

	/** Reads the arc files from the server's data again (puts back what a test changed). */
	private static void reloadArcs(MinecraftServer server) {
		ResourceManager manager = server.getResourceManager();
		Arcs.load(manager.listResources(Arcs.FOLDER, p -> p.getPath().endsWith(".json")),
			manager.listResources(Arcs.SPAWNS, p -> p.getPath().endsWith(".json")));
	}

	private static VillageHallBlockEntity hall(GameTestHelper helper, BlockPos at) {
		ServerLevel level = helper.getLevel();
		BlockPos abs = helper.absolutePos(at);
		int radius = VillageHalls.RADIUS;
		int cooldown = Arcs.COOLDOWN_DAYS;
		int atOnce = Arcs.AT_ONCE;
		boolean enabled = Arcs.ENABLED;
		boolean auto = Arcs.AUTO;
		VillageHalls.RADIUS = 8;
		Arcs.AUTO = false; // (only the test starts arcs)
		Leftovers.after(helper, () -> {
			Arcs.stopAll(level, abs);
			VillageHalls.RADIUS = radius;
			Arcs.COOLDOWN_DAYS = cooldown;
			Arcs.AT_ONCE = atOnce;
			Arcs.ENABLED = enabled;
			Arcs.AUTO = auto;
		});
		helper.setBlock(at, io.github.jcondedata.aliveworkplace.registry.ModBlocks.VILLAGE_HALL);
		VillageHallBlockEntity entity = (VillageHallBlockEntity) level.getBlockEntity(abs);
		entity.setLastQuestDay(Long.MAX_VALUE / 2);
		Arcs.stopAll(level, abs);
		return entity;
	}

	private static Arcs.Arc arc(GameTestHelper helper, ResourceLocation id) {
		Arcs.Arc arc = Arcs.get(id).orElse(null);
		helper.assertTrue(arc != null, "the test pack's arc " + id + " didn't load");
		return arc;
	}

	private static void setDay(ServerLevel level, long day) {
		level.setDayTime((day - 1) * DAY + 1000);
	}

	private static List<Quest> arcQuests(ServerLevel level, BlockPos hall) {
		return Stories.open(level, hall).stream().filter(q -> q.arc != null).toList();
	}

	private static CommandSourceStack source(ServerLevel level, BlockPos at, int permission, List<String> said) {
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
		return new CommandSourceStack(out, Vec3.atCenterOf(at.south(2)), Vec2.ZERO, level, permission, "qa", Component.literal("qa"), level.getServer(), null);
	}

	// ---------------------------------------------------------------- 31.2: quest files

	/**
	 * Two broken quest files (a bad objective type, and text that isn't JSON) are each skipped with exactly one warning
	 * naming that file; every other file still loads, the test pack's apples among them.
	 */
	//$ gametest 'FabricGameTest.EMPTY_STRUCTURE'
	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
	public void qaABrokenQuestFileIsOneWarningNamingItAndTheRestLoad(GameTestHelper helper) {
		var manager = helper.getLevel().getServer().getResourceManager();
		Map<ResourceLocation, Resource> real = manager.listResources(QuestFiles.FOLDER, p -> p.getPath().endsWith(".json"));
		int count = QuestFiles.all().size();
		Resource any = real.values().iterator().next();
		Map<ResourceLocation, Resource> withBroken = new HashMap<>(real);
		withBroken.put(ResourceLocation.fromNamespaceAndPath("aliveworkplace_test", "quests/test/broken_type.json"),
			text(any, "{\"giver\": \"hall\", \"objectives\": [{\"type\": \"fetch\", \"item\": \"minecraft:apple\", \"count\": 1}]}"));
		withBroken.put(ResourceLocation.fromNamespaceAndPath("aliveworkplace_test", "quests/test/broken_text.json"),
			text(any, "{\"giver\": \"hall\", \"objectives\": [ this is not json"));
		List<String> warnings = logged(helper, "qaBrokenQuestFile1103c", org.apache.logging.log4j.Level.WARN);
		try {
			QuestFiles.load(withBroken);
			List<String> type = matching(warnings, "aliveworkplace_test:quests/test/broken_type");
			List<String> textLines = matching(warnings, "aliveworkplace_test:quests/test/broken_text");
			helper.assertTrue(type.size() == 1 && type.get(0).startsWith("Skipping quest file"), "the bad type's warnings: " + type);
			helper.assertTrue(textLines.size() == 1 && textLines.get(0).startsWith("Skipping quest file"), "the bad text's warnings: " + textLines);
			helper.assertTrue(matching(warnings, "Skipping quest file").size() == 2, "other files warned: " + matching(warnings, "Skipping quest file"));
			helper.assertTrue(QuestFiles.all().size() == count, "the good files: " + QuestFiles.all().size() + " of " + count);
			helper.assertTrue(QuestFiles.get(APPLES).isPresent() && QuestFiles.get(AliveWorkplace.id("daily/slay")).isPresent(), "a good file is missing");
		} finally {
			QuestFiles.load(real);
		}
		helper.assertTrue(QuestFiles.all().size() == count, "not put back");
		helper.succeed();
	}

	/**
	 * The real {@code /reload} (the server reloading its data packs) reads the quest and arc files again: a quest file and
	 * an arc that no pack holds any more are gone afterwards, and the test pack's own files are back.
	 */
	//$ gametest_ticks_batch HUGE '400' '"qaARealReloadReadsTheFilesAgain"'
	@GameTest(template = HUGE, timeoutTicks = 400, batch = "qaARealReloadReadsTheFilesAgain")
	public void qaARealReloadReadsTheFilesAgain(GameTestHelper helper) {
		MinecraftServer server = helper.getLevel().getServer();
		var manager = server.getResourceManager();
		Map<ResourceLocation, Resource> real = manager.listResources(QuestFiles.FOLDER, p -> p.getPath().endsWith(".json"));
		Resource any = real.values().iterator().next();
		ResourceLocation extraQuest = ResourceLocation.fromNamespaceAndPath("aliveworkplace_test", "qa/reload_1103c");
		Map<ResourceLocation, Resource> withExtra = new HashMap<>(real);
		withExtra.put(ResourceLocation.fromNamespaceAndPath("aliveworkplace_test", "quests/qa/reload_1103c.json"),
			text(any, "{\"giver\": \"hall\", \"objectives\": [{\"type\": \"wait\", \"days\": 1}]}"));
		QuestFiles.load(withExtra);
		JsonObject threat = JsonParser.parseString("{\"name\": \"QA reload\", \"trigger\": {\"chance\": 0.0}, \"chapters\": [{\"name\": \"One\","
			+ " \"intro\": [\"x\"], \"complete\": []}]}").getAsJsonObject();
		Arcs.add(Arcs.read(THREAT, threat));
		helper.assertTrue(QuestFiles.get(extraQuest).isPresent() && Arcs.get(THREAT).isPresent(), "the extra files didn't go in");
		boolean[] done = new boolean[1];
		Leftovers.after(helper, () -> {
			if (!done[0]) {
				QuestFiles.load(real);
				reloadArcs(server);
			}
		});
		var reload = server.reloadResources(server.getPackRepository().getSelectedIds());
		helper.succeedWhen(() -> {
			helper.assertTrue(reload.isDone(), "the reload is still running");
			helper.assertTrue(!reload.isCompletedExceptionally(), "the reload failed");
			done[0] = true;
			helper.assertTrue(QuestFiles.get(extraQuest).isEmpty(), "/reload kept a quest file no pack has");
			helper.assertTrue(Arcs.get(THREAT).isEmpty(), "/reload kept an arc no pack has");
			helper.assertTrue(QuestFiles.get(APPLES).isPresent() && Arcs.get(THREE).isPresent(), "/reload lost the test pack's files");
		});
	}

	/**
	 * {@code villageQuests} off stops the morning's new quests only: an arc still runs, still puts up its chapter's quest,
	 * and the quest still counts a hand-in.
	 */
	//$ gametest_ticks_batch HUGE '60' '"qaQuestsOffLeaveTheArcsAlone"'
	@GameTest(template = HUGE, timeoutTicks = 60, batch = "qaQuestsOffLeaveTheArcsAlone")
	public void qaQuestsOffLeaveTheArcsAlone(GameTestHelper helper) {
		Leftovers.clear(helper);
		ServerLevel level = helper.getLevel();
		VillageHallBlockEntity entity = hall(helper, HALL);
		BlockPos hall = helper.absolutePos(HALL);
		long dayTime = level.getDayTime();
		boolean quests = Stories.ENABLED;
		Leftovers.after(helper, () -> Stories.ENABLED = quests);
		Stories.ENABLED = false;
		Arcs.ENABLED = true;
		Arcs.AT_ONCE = 1000;
		setDay(level, Chronicle.day(level) + 1);
		ArcState s = Arcs.start(level, hall, arc(helper, THREE));
		helper.assertTrue(s != null, "the arc didn't start with villageQuests off");
		Arcs.next(level, hall);
		List<Quest> open = arcQuests(level, hall);
		helper.assertTrue(s.chapter == 1 && open.size() == 1, "the chapter's quest didn't go up with villageQuests off: " + open);
		ServerPlayer player = helper.makeMockServerPlayerInLevel();
		player.setGameMode(GameType.SURVIVAL);
		player.getInventory().add(new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.APPLE, 2));
		io.github.jcondedata.aliveworkplace.hall.VillageQuests.handIn(player, hall, open.get(0).id);
		Arcs.round(level, hall, entity);
		helper.assertTrue(s.chapter == 2, "the hand-in didn't move the arc on with villageQuests off: chapter " + (s.chapter + 1));
		helper.succeed();
	}

	// ---------------------------------------------------------------- 31.4: arcs

	/**
	 * Mid-chapter, the saved data goes round a save and load, then the arc's file is removed and the files are read
	 * again: the next round ends it quietly (its quest down, nothing told in the chronicle) with one log line naming it,
	 * and later rounds say nothing more.
	 */
	//$ gametest_ticks_batch HUGE '60' '"qaAnArcWhoseFileIsGoneEndsWithOneLine"'
	@GameTest(template = HUGE, timeoutTicks = 60, batch = "qaAnArcWhoseFileIsGoneEndsWithOneLine")
	public void qaAnArcWhoseFileIsGoneEndsWithOneLine(GameTestHelper helper) {
		Leftovers.clear(helper);
		ServerLevel level = helper.getLevel();
		MinecraftServer server = level.getServer();
		VillageHallBlockEntity entity = hall(helper, HALL);
		BlockPos hall = helper.absolutePos(HALL);
		Arcs.AT_ONCE = 1000;
		setDay(level, Chronicle.day(level) + 1);
		Arcs.start(level, hall, arc(helper, THREE));
		Arcs.next(level, hall);
		helper.assertTrue(arcQuests(level, hall).size() == 1, "chapter 2's quest");
		var saved = Stories.Data.get(level).save(new net.minecraft.nbt.CompoundTag(), level.registryAccess());
		level.getDataStorage().set("aliveworkplace_stories", Stories.Data.load(saved, level.registryAccess()));
		helper.assertTrue(Arcs.running(level, hall) != null && Arcs.running(level, hall).chapter == 1, "not carried over the reload");

		ResourceManager manager = server.getResourceManager();
		Map<ResourceLocation, Resource> files = new HashMap<>(manager.listResources(Arcs.FOLDER, p -> p.getPath().endsWith(".json")));
		files.remove(ResourceLocation.fromNamespaceAndPath("aliveworkplace_test", "arcs/test/three_chapters.json"));
		Leftovers.after(helper, () -> reloadArcs(server));
		List<String> lines = logged(helper, "qaArcFileGone1103c", org.apache.logging.log4j.Level.INFO);
		Arcs.load(files, manager.listResources(Arcs.SPAWNS, p -> p.getPath().endsWith(".json")));
		helper.assertTrue(Arcs.get(THREE).isEmpty(), "the file is still there");
		int told = entity.chronicle().size();
		Arcs.round(level, hall, entity);
		helper.assertTrue(Arcs.running(level, hall) == null && arcQuests(level, hall).isEmpty(), "the gone arc carried on");
		helper.assertTrue(entity.chronicle().size() == told, "it didn't end quietly: " + entity.chronicle());
		List<String> gone = matching(lines, "its file is gone");
		helper.assertTrue(gone.size() == 1 && gone.get(0).contains(THREE.toString()), "the line naming the gone arc: " + gone);
		int before = matching(lines, THREE.toString()).size();
		Arcs.round(level, hall, entity);
		helper.assertTrue(matching(lines, THREE.toString()).size() == before, "a later round spoke of it again: " + matching(lines, THREE.toString()));
		helper.succeed();
	}

	/**
	 * A {@code threat} arc doesn't start in a village set At peace, at threat level 0 or on Peaceful, and does start once
	 * the village is threatened (not At peace, threat level 1, Normal).
	 */
	//$ gametest_ticks_batch TALL '60' '"qaAThreatArcWaitsWhileTheVillageIsQuiet"'
	@GameTest(template = TALL, timeoutTicks = 60, batch = "qaAThreatArcWaitsWhileTheVillageIsQuiet")
	public void qaAThreatArcWaitsWhileTheVillageIsQuiet(GameTestHelper helper) {
		Leftovers.clear(helper);
		QaSicknessGameTests.settings(helper);
		VillageHallBlockEntity entity = QaSicknessGameTests.hall(helper);
		ServerLevel level = helper.getLevel();
		MinecraftServer server = level.getServer();
		JsonObject json = JsonParser.parseString("{\"name\": \"QA threat\", \"threat\": true, \"trigger\": {\"chance\": 1.0},"
			+ " \"chapters\": [{\"name\": \"Trouble\", \"intro\": [\"Trouble comes.\"], \"complete\": []}]}").getAsJsonObject();
		Arcs.add(Arcs.read(THREAT, json));
		Leftovers.after(helper, () -> reloadArcs(server));
		QaStoryArcsPeace1031GameTests.onlyArc(helper, THREAT);
		Difficulty difficulty = server.getWorldData().getDifficulty();
		Leftovers.after(helper, () -> server.setDifficulty(difficulty, true));
		BlockPos hall = helper.absolutePos(QaSicknessGameTests.HALL);
		long day = Chronicle.day(level);
		RandomSource random = RandomSource.create(1103);

		server.setDifficulty(Difficulty.NORMAL, true);
		entity.setAtPeace(true, day);
		Threats.LEVEL = 2;
		helper.assertTrue(QaStoryArcsPeace1031GameTests.roll(level, hall, random, 20) == null, "a threat arc started At peace");
		entity.setAtPeace(false, day);
		Threats.LEVEL = 0;
		helper.assertTrue(QaStoryArcsPeace1031GameTests.roll(level, hall, random, 20) == null, "a threat arc started at threat level 0");
		Threats.LEVEL = 2;
		server.setDifficulty(Difficulty.PEACEFUL, true);
		helper.assertTrue(QaStoryArcsPeace1031GameTests.roll(level, hall, random, 20) == null, "a threat arc started on Peaceful");
		server.setDifficulty(Difficulty.NORMAL, true);
		ArcState s = QaStoryArcsPeace1031GameTests.roll(level, hall, random, 20);
		helper.assertTrue(s != null && s.id.equals(THREAT.toString()), "a threat arc didn't start in a threatened village");
		helper.succeed();
	}

	/** {@code /workplace story start|next|stop} are for operators: a player without permission changes nothing. */
	//$ gametest_ticks_batch HUGE '40' '"qaStoryCommandsAreForOperatorsOnly"'
	@GameTest(template = HUGE, timeoutTicks = 40, batch = "qaStoryCommandsAreForOperatorsOnly")
	public void qaStoryCommandsAreForOperatorsOnly(GameTestHelper helper) {
		Leftovers.clear(helper);
		ServerLevel level = helper.getLevel();
		hall(helper, HALL);
		BlockPos hall = helper.absolutePos(HALL);
		Arcs.ENABLED = true;
		Arcs.AT_ONCE = 1000;
		setDay(level, Chronicle.day(level) + 1);
		var commands = level.getServer().getCommands();
		List<String> said = new ArrayList<>();
		commands.performPrefixedCommand(source(level, hall, 0, said), "workplace story start aliveworkplace_test:test/three_chapters");
		helper.assertTrue(Arcs.running(level, hall) == null, "a player without permission started a story");
		commands.performPrefixedCommand(source(level, hall, 2, said), "workplace story start aliveworkplace_test:test/three_chapters");
		ArcState s = Arcs.running(level, hall);
		helper.assertTrue(s != null, "the operator's start: " + said);
		int chapter = s.chapter;
		boolean started = s.started;
		commands.performPrefixedCommand(source(level, hall, 0, said), "workplace story next");
		helper.assertTrue(Arcs.running(level, hall) == s && s.chapter == chapter && s.started == started, "a player without permission moved the story on");
		commands.performPrefixedCommand(source(level, hall, 0, said), "workplace story stop");
		helper.assertTrue(Arcs.running(level, hall) != null, "a player without permission stopped the story");
		helper.succeed();
	}

	/**
	 * Every sentence the story engines show (keys under {@code arc}, {@code story} and {@code quest}) is in en_us.json,
	 * renders as text rather than its key, keeps its arguments, and is written in American English.
	 */
	//$ gametest 'FabricGameTest.EMPTY_STRUCTURE'
	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
	public void qaEveryStorySentenceIsAmericanEnglish(GameTestHelper helper) throws Exception {
		JsonObject lang;
		try (InputStream in = AliveWorkplace.class.getResourceAsStream("/assets/aliveworkplace/lang/en_us.json")) {
			helper.assertTrue(in != null, "no en_us.json");
			lang = JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonObject();
		}
		Pattern ours = Pattern.compile("\\.(arc|story|stories|quest|quests)(\\.|$)");
		Pattern british = Pattern.compile("(?i)\\b(colour\\w*|defence|armour\\w*|cancelled|cancelling|grey|jeweller\\w*|favourite\\w*|honour\\w*|centre|travell\\w*"
			+ "|organis\\w*|realis\\w*|recognis\\w*|neighbour\\w*|labour\\w*|behaviour|apologis\\w*)\\b");
		Pattern args = Pattern.compile("%(\\d+\\$)?s");
		int checked = 0;
		List<String> bad = new ArrayList<>();
		for (var e : lang.entrySet()) {
			if (!ours.matcher(e.getKey()).find()) {
				continue;
			}
			checked++;
			String value = e.getValue().getAsString();
			Matcher m = british.matcher(value);
			if (m.find()) {
				bad.add(e.getKey() + " says \"" + m.group() + "\"");
			}
			int n = 0;
			Matcher a = args.matcher(value);
			while (a.find()) {
				n++;
			}
			Object[] filled = new Object[n];
			for (int i = 0; i < n; i++) {
				filled[i] = "ARG" + i;
			}
			String shown = Component.translatable(e.getKey(), filled).getString();
			if (shown.equals(e.getKey()) || shown.contains("%s")) {
				bad.add(e.getKey() + " renders as \"" + shown + "\"");
			}
		}
		helper.assertTrue(checked > 20, "only " + checked + " story sentences found");
		helper.assertTrue(bad.isEmpty(), "story sentences: " + bad);
		helper.succeed();
	}
}
