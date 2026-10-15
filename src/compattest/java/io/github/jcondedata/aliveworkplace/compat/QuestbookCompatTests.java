package io.github.jcondedata.aliveworkplace.compat;

import io.github.jcondedata.aliveworkplace.compat.ftbquests.FtbQuestsBook;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.nbt.TagParser;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.item.Items;

/**
 * The questbook's FTB Quests chapters (tools/questbook/generate.py) point only at things that exist: every item (task,
 * reward, icon), entity, block and advancement id is looked up in this server's registries, with Minecraft, our mod and
 * the pack's mods loaded. RCT isn't loaded here: its ids are the ones listed below, checked against the pack's RCT jar,
 * and quests that use them are optional, so a server without RCT can still finish every chapter. Also: every file
 * parses, every FTB Quests id is a valid, unique id, every dependency is one of our quests, every quest has words, and
 * rewards are emeralds and experience only.
 */
public class QuestbookCompatTests implements FabricGameTest {
	/** Ids from pack mods that aren't loaded in the compat tests, as found in their jars (RCT 0.18.1, the Cobbleverse's). */
	static final Set<String> PACK_ONLY = Set.of(
		"rctmod:trainers/defeat_any",          // data/rctmod/advancement/trainers/defeat_any.json
		"rctmod:trainers/defeat_leader_brock"  // data/rctmod/advancement/trainers/defeat_leader_brock.json
	);
	static final Set<String> TASKS = Set.of("item", "checkmark", "advancement", "kill", "observation");
	static final Set<String> REWARDS = Set.of("item", "xp", "command");
	static final Pattern ID = Pattern.compile("[1-7][0-9A-F]{15}");

	//$ gametest 'FabricGameTest.EMPTY_STRUCTURE'
	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
	public void everyQuestbookIdExists(GameTestHelper helper) {
		MinecraftServer server = helper.getLevel().getServer();
		List<String> problems = new ArrayList<>();
		Set<String> ids = new HashSet<>();
		Set<String> questIds = new HashSet<>();
		List<String[]> dependencies = new ArrayList<>();
		int quests = 0;
		int lookedUp = 0;
		List<FtbQuestsBook.Chapter> chapters;
		try {
			chapters = FtbQuestsBook.manifest();
		} catch (IOException e) {
			throw new AssertionError("can't read the questbook manifest", e);
		}
		for (FtbQuestsBook.Chapter chapter : chapters) {
			String where = chapter.file();
			CompoundTag nbt;
			try (InputStream in = FtbQuestsBook.resource(chapter.file())) {
				nbt = TagParser.parseTag(new String(in.readAllBytes(), StandardCharsets.UTF_8));
			} catch (Exception e) {
				problems.add(where + " doesn't parse: " + e.getMessage());
				continue;
			}
			id(nbt, where, ids, problems);
			if (!(nbt.getString("filename") + ".snbt").equals(chapter.file())) {
				problems.add(where + ": its filename is " + nbt.getString("filename"));
			}
			if (nbt.getString("title").isBlank()) {
				problems.add(where + " has no title");
			}
			lookedUp += item(nbt.getCompound("icon"), where + " icon", problems);
			ListTag list = nbt.getList("quests", Tag.TAG_COMPOUND);
			if (list.isEmpty()) {
				problems.add(where + " has no quests");
			}
			for (int i = 0; i < list.size(); i++) {
				CompoundTag quest = list.getCompound(i);
				quests++;
				String q = where + " / " + quest.getString("title");
				questIds.add(id(quest, q, ids, problems));
				if (quest.getString("title").isBlank() || quest.getList("description", Tag.TAG_STRING).isEmpty()) {
					problems.add(q + " has no title or no description");
				}
				ListTag deps = quest.getList("dependencies", Tag.TAG_STRING);
				for (int d = 0; d < deps.size(); d++) {
					dependencies.add(new String[] {q, deps.getString(d)});
				}
				if (quest.contains("icon")) {
					lookedUp += item(quest.getCompound("icon"), q + " icon", problems);
				}
				ListTag tasks = quest.getList("tasks", Tag.TAG_COMPOUND);
				if (tasks.isEmpty()) {
					problems.add(q + " has no task");
				}
				for (int t = 0; t < tasks.size(); t++) {
					CompoundTag task = tasks.getCompound(t);
					id(task, q + " task", ids, problems);
					String type = task.getString("type");
					if (!TASKS.contains(type)) {
						problems.add(q + ": task type " + type);
					}
					switch (type) {
						case "item" -> lookedUp += item(task.getCompound("item"), q, problems);
						case "kill" -> lookedUp += lookUp(server, task.getString("entity"), "entity", q, quest, problems);
						case "observation" -> lookedUp += lookUp(server, task.getString("to_observe"), "block", q, quest, problems);
						case "advancement" -> lookedUp += lookUp(server, task.getString("advancement"), "advancement", q, quest, problems);
						case "checkmark" -> {
							if (task.getString("title").isBlank()) {
								problems.add(q + ": a checkmark without words");
							}
						}
						default -> {
						}
					}
				}
				ListTag rewards = quest.getList("rewards", Tag.TAG_COMPOUND);
				for (int r = 0; r < rewards.size(); r++) {
					CompoundTag reward = rewards.getCompound(r);
					id(reward, q + " reward", ids, problems);
					String type = reward.getString("type");
					if (!REWARDS.contains(type)) {
						problems.add(q + ": reward type " + type);
					} else if (type.equals("command")) {
						// Only our blueprint unlock (26.10), for a blueprint that exists: "/workplace unlock {p} <id>".
						String[] words = reward.getString("command").trim().split("\\s+");
						if (words.length != 4 || !words[0].equals("/workplace") || !words[1].equals("unlock")
							|| net.minecraft.resources.ResourceLocation.tryParse(words[3]) == null
							|| server.getStructureManager().get(net.minecraft.resources.ResourceLocation.tryParse(words[3])).isEmpty()) {
							problems.add(q + ": reward command " + reward.getString("command"));
						}
					} else if (type.equals("item") && !reward.getCompound("item").getString("id").equals("minecraft:emerald")) {
						problems.add(q + ": rewards " + reward.getCompound("item").getString("id") + " (emeralds and experience only)");
					}
				}
			}
		}
		for (String[] dep : dependencies) {
			if (!questIds.contains(dep[1])) {
				problems.add(dep[0] + " depends on " + dep[1] + ", which isn't one of our quests");
			}
		}
		helper.assertTrue(chapters.size() >= 14 && quests >= 100 && lookedUp >= 100,
			chapters.size() + " chapters, " + quests + " quests, " + lookedUp + " ids looked up: is the questbook all there?");
		helper.assertTrue(problems.isEmpty(), problems.size() + " questbook problems: "
			+ String.join("; ", problems.subList(0, Math.min(30, problems.size()))));
		helper.succeed();
	}

	/** The object's FTB Quests id: 16 hex digits, the first 1 to 7 (FTB Quests reads them as signed longs), unique. */
	private static String id(CompoundTag nbt, String where, Set<String> ids, List<String> problems) {
		String id = nbt.getString("id");
		if (!ID.matcher(id).matches()) {
			problems.add(where + ": id " + id);
		} else if (!ids.add(id)) {
			problems.add(where + ": id " + id + " is used twice");
		}
		return id;
	}

	private static int item(CompoundTag stack, String where, List<String> problems) {
		String id = stack.getString("id");
		ResourceLocation rl = ResourceLocation.tryParse(id);
		if (rl == null || !FabricLoader.getInstance().isModLoaded(rl.getNamespace()) && !rl.getNamespace().equals("minecraft")) {
			problems.add(where + ": item " + id + " is from a mod that isn't in the test pack");
		} else if (!BuiltInRegistries.ITEM.containsKey(rl) || BuiltInRegistries.ITEM.get(rl) == Items.AIR) {
			problems.add(where + ": no item " + id);
		}
		return 1;
	}

	private static int lookUp(MinecraftServer server, String id, String kind, String where, CompoundTag quest, List<String> problems) {
		ResourceLocation rl = ResourceLocation.tryParse(id);
		if (rl == null) {
			problems.add(where + ": bad " + kind + " id " + id);
			return 1;
		}
		boolean loaded = rl.getNamespace().equals("minecraft") || FabricLoader.getInstance().isModLoaded(rl.getNamespace());
		if (!loaded) {
			if (!PACK_ONLY.contains(id)) {
				problems.add(where + ": " + kind + " " + id + " is from a mod that isn't in the test pack, and not in PACK_ONLY");
			} else if (!quest.getBoolean("optional")) {
				problems.add(where + ": uses " + id + " from a mod a server may not have, so it must be optional");
			}
			return 1;
		}
		boolean exists = switch (kind) {
			case "entity" -> BuiltInRegistries.ENTITY_TYPE.containsKey(rl);
			case "block" -> BuiltInRegistries.BLOCK.containsKey(rl);
			default -> server.getAdvancements().get(rl) != null;
		};
		if (!exists) {
			problems.add(where + ": no " + kind + " " + id);
		}
		return 1;
	}

}
