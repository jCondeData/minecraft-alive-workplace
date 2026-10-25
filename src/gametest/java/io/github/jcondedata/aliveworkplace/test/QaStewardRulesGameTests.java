package io.github.jcondedata.aliveworkplace.test;

import com.google.gson.JsonElement;
import com.google.gson.JsonNull;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonPrimitive;
import io.github.jcondedata.aliveworkplace.AliveWorkplace;
import io.github.jcondedata.aliveworkplace.city.StewardConditions;
import io.github.jcondedata.aliveworkplace.city.StewardRules;
import io.github.jcondedata.aliveworkplace.city.StewardWishes;
import io.github.jcondedata.aliveworkplace.hall.VillageHallBlockEntity;
import io.github.jcondedata.aliveworkplace.hall.VillageHalls;
import io.github.jcondedata.aliveworkplace.hall.VillageRanks;
import io.github.jcondedata.aliveworkplace.registry.ModBlocks;
import io.github.jcondedata.aliveworkplace.work.Words;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.npc.Villager;

/**
 * QA for ROADMAP 27.6, the Steward's rules: cooldown_days at N-1 and N days, min_rank below and at, requires with a
 * mod that's there and one that isn't, priority's ends and the order rules rank in, files that aren't rules at all, and
 * the "why" lines in English with their numbers (singular and plural). Complements StewardRulesGameTests.
 */
public class QaStewardRulesGameTests implements net.fabricmc.fabric.api.gametest.v1.FabricGameTest {
	private static final String AREA = "aliveworkplace_test:big_area";
	private static final BlockPos HALL = new BlockPos(11, 2, 11);
	private static final String JOBS = "{\"when\": [{\"jobless\": {}}], \"do\": {\"assign_jobs\": {}}, \"why\": \"x\"";

	private static BlockPos hall(GameTestHelper helper) {
		Leftovers.clear(helper);
		int radius = VillageHalls.RADIUS;
		VillageHalls.RADIUS = 16;
		Leftovers.after(helper, () -> VillageHalls.RADIUS = radius);
		helper.setBlock(HALL, ModBlocks.VILLAGE_HALL);
		return helper.absolutePos(HALL);
	}

	private static StewardRules.Rule rule(String name, String json) {
		return StewardRules.read(AliveWorkplace.id(name), JsonParser.parseString(json));
	}

	private static Villager villager(GameTestHelper helper, int x, int z) {
		Villager v = helper.spawn(EntityType.VILLAGER, new BlockPos(x, 2, z));
		v.setNoAi(true);
		return v;
	}

	/** cooldown_days N: resting for days 0..N-1 after it was carried out, wished for again on day N. */
	//$ gametest_ticks_batch AREA '40' '"qaRuleCooldown"'
	@GameTest(template = AREA, timeoutTicks = 40, batch = "qaRuleCooldown")
	public void aCooldownRestsUntilExactlyItsDays(GameTestHelper helper) {
		BlockPos hall = hall(helper);
		ServerLevel level = helper.getLevel();
		villager(helper, 3, 4);
		long day = StewardWishes.day(level);
		StewardConditions.Facts facts = StewardConditions.Facts.of(level, hall);
		StewardWishes.rankIfDue(level, hall);
		StewardWishes.carriedOut(level, hall, AliveWorkplace.id("qa_cool"));
		StewardWishes.State state = StewardWishes.of(level, hall);
		for (int n : new int[] {1, 2, 3, 7}) {
			StewardRules.Rule r = rule("qa_cool", JOBS + ", \"cooldown_days\": " + n + "}");
			for (int after = 0; after <= n; after++) {
				StewardWishes.Status s = StewardWishes.judge(r, facts, state, day + after).status();
				StewardWishes.Status want = after < n ? StewardWishes.Status.COOLING : StewardWishes.Status.HELD;
				helper.assertTrue(s == want, "cooldown " + n + ", " + after + " days after: " + s + ", want " + want);
			}
		}
		StewardRules.Rule none = rule("qa_cool", JOBS + ", \"cooldown_days\": 0}");
		helper.assertTrue(StewardWishes.judge(none, facts, state, day).status() == StewardWishes.Status.HELD, "no cooldown, still resting");
		// a rule never carried out has nothing to rest from
		StewardRules.Rule fresh = rule("qa_never", JOBS + ", \"cooldown_days\": 5}");
		helper.assertTrue(StewardWishes.judge(fresh, facts, state, day).status() == StewardWishes.Status.HELD, "a rule never done is resting");
		helper.succeed();
	}

	/** min_rank: a rank below is refused, the rank itself and above are not. */
	//$ gametest_ticks_batch AREA '40' '"qaRuleMinRank"'
	@GameTest(template = AREA, timeoutTicks = 40, batch = "qaRuleMinRank")
	public void minRankHoldsAtItsRank(GameTestHelper helper) {
		BlockPos hall = hall(helper);
		ServerLevel level = helper.getLevel();
		villager(helper, 3, 4);
		VillageHallBlockEntity entity = (VillageHallBlockEntity) level.getBlockEntity(hall);
		CompoundTag tag = entity.saveWithoutMetadata(level.registryAccess());
		tag.putInt("rank", VillageRanks.Rank.TOWN.ordinal());
		entity.loadWithComponents(tag, level.registryAccess());
		StewardConditions.Facts facts = StewardConditions.Facts.of(level, hall);
		helper.assertTrue(facts.rank() == VillageRanks.Rank.TOWN, "setup: a " + facts.rank());
		long day = StewardWishes.day(level);
		for (String rank : List.of("hamlet", "village", "town", "city")) {
			StewardWishes.Status s = StewardWishes.judge(rule("qa_rank", JOBS + ", \"min_rank\": \"" + rank + "\"}"), facts, StewardWishes.State.EMPTY, day).status();
			StewardWishes.Status want = rank.equals("city") ? StewardWishes.Status.RANK : StewardWishes.Status.HELD;
			helper.assertTrue(s == want, "a Town with min_rank " + rank + ": " + s + ", want " + want);
		}
		helper.succeed();
	}

	/** requires: every listed mod loaded, or the rule is ignored; ignored rules never become wishes. */
	//$ gametest_ticks_batch AREA '40' '"qaRuleRequires"'
	@GameTest(template = AREA, timeoutTicks = 40, batch = "qaRuleRequires")
	public void requiresNeedsEveryModListed(GameTestHelper helper) {
		BlockPos hall = hall(helper);
		ServerLevel level = helper.getLevel();
		villager(helper, 3, 4);
		StewardConditions.Facts facts = StewardConditions.Facts.of(level, hall);
		long day = StewardWishes.day(level);
		StewardRules.Rule here = rule("qa_here", JOBS + ", \"requires\": [\"minecraft\", \"aliveworkplace\"]}");
		StewardRules.Rule missing = rule("qa_missing", JOBS + ", \"requires\": [\"minecraft\", \"qa_no_such_mod\"], \"priority\": 100}");
		helper.assertTrue(StewardWishes.judge(here, facts, StewardWishes.State.EMPTY, day).status() == StewardWishes.Status.HELD, "installed mods refused");
		helper.assertTrue(StewardWishes.judge(missing, facts, StewardWishes.State.EMPTY, day).status() == StewardWishes.Status.MOD_MISSING, "a missing mod allowed");
		List<StewardWishes.Wish> wishes = StewardWishes.rank(List.of(missing, here), facts, StewardWishes.State.EMPTY, day);
		helper.assertTrue(wishes.size() == 1 && wishes.get(0).rule().getPath().equals("qa_here"), "wishes: " + wishes);
		helper.succeed();
	}

	/** priority: 0 and 100 are fine, -1 and 101 are not; the wishes rank highest first, at most MAX_WISHES. */
	//$ gametest_ticks_batch AREA '40' '"qaRulePriority"'
	@GameTest(template = AREA, timeoutTicks = 40, batch = "qaRulePriority")
	public void priorityEndsAndOrder(GameTestHelper helper) {
		BlockPos hall = hall(helper);
		ServerLevel level = helper.getLevel();
		villager(helper, 3, 4);
		for (int p : new int[] {0, 100}) {
			helper.assertTrue(rule("qa_p", JOBS + ", \"priority\": " + p + "}").priority() == p, "priority " + p);
		}
		for (int p : new int[] {-1, 101}) {
			try {
				rule("qa_p", JOBS + ", \"priority\": " + p + "}");
				helper.fail("priority " + p + " accepted");
			} catch (StewardRules.BadRule bad) {
				helper.assertTrue(bad.field.equals("priority"), "priority " + p + " refused on " + bad.field);
			}
		}
		helper.assertTrue(rule("qa_p", JOBS + "}").priority() == 50, "the default priority isn't 50");
		List<StewardRules.Rule> rules = new ArrayList<>();
		int[] priorities = {10, 90, 50, 0, 100, 70, 30, 60, 20, 80};
		Map<ResourceLocation, JsonElement> files = new LinkedHashMap<>();
		for (int i = 0; i < priorities.length; i++) {
			files.put(ResourceLocation.fromNamespaceAndPath("aliveworkplace_test", "steward_rules/r" + i + ".json"),
				JsonParser.parseString(JOBS + ", \"priority\": " + priorities[i] + "}"));
		}
		rules.addAll(StewardRules.readAll(files));
		for (int i = 0; i + 1 < rules.size(); i++) {
			helper.assertTrue(rules.get(i).priority() >= rules.get(i + 1).priority(), "loaded out of order: " + rules.stream().map(StewardRules.Rule::priority).toList());
		}
		List<StewardWishes.Wish> wishes = StewardWishes.rank(rules, StewardConditions.Facts.of(level, hall), StewardWishes.State.EMPTY, StewardWishes.day(level));
		helper.assertTrue(wishes.size() == Math.min(StewardWishes.MAX_WISHES, priorities.length), "wishes: " + wishes.size());
		List<Integer> top = java.util.Arrays.stream(priorities).boxed().sorted(java.util.Comparator.reverseOrder()).limit(wishes.size()).toList();
		helper.assertTrue(wishes.stream().map(StewardWishes.Wish::priority).toList().equals(top),
			"not the highest first: " + wishes.stream().map(StewardWishes.Wish::priority).toList() + ", want " + top);
		helper.succeed();
	}

	/** Files that aren't a rule at all (a list, a number, null, a broken field) are skipped; the rest still load. */
	//$ gametest EMPTY_STRUCTURE
	@GameTest(template = EMPTY_STRUCTURE)
	public void filesThatArentRulesDontStopTheOthers(GameTestHelper helper) {
		Map<ResourceLocation, JsonElement> files = new LinkedHashMap<>();
		files.put(ResourceLocation.fromNamespaceAndPath("aliveworkplace_test", "steward_rules/a_list.json"), JsonParser.parseString("[1, 2]"));
		files.put(ResourceLocation.fromNamespaceAndPath("aliveworkplace_test", "steward_rules/b_good.json"), JsonParser.parseString(JOBS + "}"));
		files.put(ResourceLocation.fromNamespaceAndPath("aliveworkplace_test", "steward_rules/c_number.json"), new JsonPrimitive(7));
		files.put(ResourceLocation.fromNamespaceAndPath("aliveworkplace_test", "steward_rules/d_null.json"), JsonNull.INSTANCE);
		files.put(ResourceLocation.fromNamespaceAndPath("aliveworkplace_test", "steward_rules/e_empty.json"), new JsonObject());
		files.put(ResourceLocation.fromNamespaceAndPath("aliveworkplace_test", "steward_rules/f_when.json"),
			JsonParser.parseString("{\"when\": {\"jobless\": {}}, \"do\": {\"assign_jobs\": {}}, \"why\": \"x\"}"));
		files.put(ResourceLocation.fromNamespaceAndPath("aliveworkplace_test", "steward_rules/g_max.json"), JsonParser.parseString(JOBS + ", \"max\": 0}"));
		files.put(ResourceLocation.fromNamespaceAndPath("aliveworkplace_test", "steward_rules/h_cool.json"), JsonParser.parseString(JOBS + ", \"cooldown_days\": -1}"));
		files.put(ResourceLocation.fromNamespaceAndPath("aliveworkplace_test", "steward_rules/i_cond.json"),
			JsonParser.parseString("{\"when\": [\"jobless\"], \"do\": {\"assign_jobs\": {}}, \"why\": \"x\"}"));
		files.put(ResourceLocation.fromNamespaceAndPath("aliveworkplace_test", "steward_rules/j_requires.json"), JsonParser.parseString(JOBS + ", \"requires\": \"cobblemon\"}"));
		files.put(ResourceLocation.fromNamespaceAndPath("aliveworkplace_test", "steward_rules/k_good.json"), JsonParser.parseString(JOBS + ", \"priority\": 99}"));
		List<StewardRules.Rule> loaded = StewardRules.readAll(files);
		helper.assertTrue(loaded.stream().map(r -> r.id().getPath()).toList().equals(List.of("k_good", "b_good")),
			"loaded: " + loaded.stream().map(StewardRules.Rule::id).toList());
		helper.succeed();
	}

	/** {@code text} in English from the mod's en_us.json, with its %s / %n$s arguments filled. */
	static String english(Component text) {
		JsonObject lang;
		try (InputStream in = Files.newInputStream(FabricLoader.getInstance().getModContainer(AliveWorkplace.MOD_ID).orElseThrow()
				.findPath("assets/aliveworkplace/lang/en_us.json").orElseThrow())) {
			lang = JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonObject();
		} catch (Exception e) {
			throw new GameTestAssertException("can't read en_us.json: " + e);
		}
		return english(text, lang);
	}

	private static String english(Component text, JsonObject lang) {
		if (!(text.getContents() instanceof TranslatableContents t)) {
			return text.getString();
		}
		if (!lang.has(t.getKey())) {
			throw new GameTestAssertException("no English for " + t.getKey());
		}
		String pattern = lang.get(t.getKey()).getAsString();
		Object[] args = t.getArgs();
		StringBuilder out = new StringBuilder();
		int next = 0;
		for (int i = 0; i < pattern.length(); i++) {
			char c = pattern.charAt(i);
			if (c == '%' && i + 1 < pattern.length()) {
				char d = pattern.charAt(i + 1);
				if (d == '%') {
					out.append('%');
					i++;
					continue;
				}
				int index;
				int end;
				if (Character.isDigit(d) && i + 3 < pattern.length() && pattern.charAt(i + 2) == '$') {
					index = d - '1';
					end = i + 3;
				} else {
					index = next++;
					end = i + 1;
				}
				Object a = index < args.length ? args[index] : "?";
				out.append(a instanceof Component ac ? english(ac, lang) : String.valueOf(a));
				i = end;
				continue;
			}
			out.append(c);
		}
		return out.toString();
	}

	/** The why lines this item's starter rules use read right with their numbers: one and many. */
	//$ gametest EMPTY_STRUCTURE
	@GameTest(template = EMPTY_STRUCTURE)
	public void whyLinesReadRightForOneAndMany(GameTestHelper helper) {
		Map<String, String[]> want = new LinkedHashMap<>();
		want.put("steward.aliveworkplace.why.beds", new String[] {"3 villagers have no bed", "1 villager has no bed"});
		want.put("steward.aliveworkplace.why.jobless", new String[] {"3 villagers have no work", "1 villager has no work"});
		want.put("steward.aliveworkplace.why.workstation", new String[] {"3 workers have no workstation", "1 worker has no workstation"});
		for (Map.Entry<String, String[]> e : want.entrySet()) {
			String many = english(Words.countedFirst(e.getKey(), 3L));
			helper.assertTrue(many.equals(e.getValue()[0]), e.getKey() + " with 3: '" + many + "'");
		}
		// every starter rule's why is in the lang
		for (StewardRules.Rule r : StewardRules.all()) {
			english(Component.translatable(r.why(), 2L, 2L, 2L));
		}
		helper.succeed();
	}

	/** The singular why lines on the server (Words.countedFirst asks the server's Language for the ".one" key). */
	//$ gametest EMPTY_STRUCTURE
	@GameTest(template = EMPTY_STRUCTURE)
	public void oneVillagerWithoutABedReadsInTheSingular(GameTestHelper helper) {
		String one = english(Words.countedFirst("steward.aliveworkplace.why.beds", 1L));
		helper.assertTrue(one.equals("1 villager has no bed"), "'" + one + "'");
		String jobs = english(Words.countedFirst("steward.aliveworkplace.why.jobless", 1L));
		helper.assertTrue(jobs.equals("1 villager has no work"), "'" + jobs + "'");
		helper.succeed();
	}
}
