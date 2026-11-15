package io.github.jcondedata.aliveworkplace.compat;

import io.github.jcondedata.aliveworkplace.craft.CraftJobs;
import io.github.jcondedata.aliveworkplace.craft.Crafting;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;

/**
 * QA (lane E, 2026-11-15, third run), with the compat suite's whole pack of mods (Create, Cobblemon, Chipped,
 * Supplementaries, Handcrafted, Sophisticated Storage...): B489/B1267/B1277/B1279 read past their own examples, over every
 * item a job makes (CraftJobs.maker, about 3,200), each from an empty chest the way that job reads a builder's shortage line.
 * crafter.md: "never iron ore a player only gets with Silk Touch" and "need an ore (a piston names Redstone Dust, not
 * Redstone Ore)" (B489, B1277); "never Create's Crushed Raw Iron, which only a crushing wheel makes" and "never the Crushed
 * Raw Zinc" (B1267, B1279). So no line names an ore (c:ores) or a crushed raw metal, for any mod's thing.
 */
public class QaShortLineSweep1115e3CompatTests implements FabricGameTest {
	static final TagKey<Item> ORES = TagKey.create(Registries.ITEM, ResourceLocation.fromNamespaceAndPath("c", "ores"));

	/** An item, the job that makes it and the kind of recipes that job's shortage line reads. */
	record Case(Item item, CraftJobs.Job job, Crafting.Kind kind) {
	}

	/** What's wrong with one line (null when nothing). */
	interface Judge {
		String judge(ServerLevel level, Case c, Map<Item, Integer> lack);
	}

	/** Every job-made thing from nothing names no ore and no crushed raw metal. */
	//$ gametest_ticks_batch 'FabricGameTest.EMPTY_STRUCTURE' '20000' '"qaShortLineSweep1115e3Ores"'
	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE, timeoutTicks = 20000, batch = "qaShortLineSweep1115e3Ores")
	public void noJobsLineFromNothingNamesAnOreOrACrushedMetal(GameTestHelper helper) {
		sweep(helper, (level, c, lack) -> {
			for (Item k : lack.keySet()) {
				String id = BuiltInRegistries.ITEM.getKey(k).toString();
				if (k.builtInRegistryHolder().is(ORES) || id.startsWith("create:crushed_raw_")) {
					return "names " + id;
				}
				if (lack.get(k) <= 0) {
					return "names " + lack.get(k) + " " + id;
				}
			}
			return null;
		});
	}

	/** The kind a job's shortage line reads (CrafterWork.lackFor: the Tinkerer's forge, the Glassblower's kiln, a Mason's cut first). */
	static Crafting.Kind kindOf(ServerLevel level, CraftJobs.Job job, Item item) {
		return switch (job) {
			case TINKERER -> Crafting.Kind.WORKSHOP;
			case GLASSBLOWER -> Crafting.Kind.KILN;
			case MASON -> has(level, Crafting.Kind.STONECUTTING, item) ? Crafting.Kind.STONECUTTING
				: has(level, Crafting.Kind.CRAFTING, item) ? Crafting.Kind.CRAFTING : Crafting.Kind.KILN;
			default -> Crafting.Kind.CRAFTING;
		};
	}

	static boolean has(ServerLevel level, Crafting.Kind kind, Item item) {
		return !Crafting.recipesFor(level.getRecipeManager(), level.registryAccess(), kind, item).isEmpty();
	}

	static String ns(Item item) {
		return BuiltInRegistries.ITEM.getKey(item).getNamespace();
	}

	/**
	 * Reads every job-made thing's line from nothing, 40 a tick at most and never past the tick's planning (a plan cut short
	 * by a spent budget reads as "nothing short", so it's read again next tick), then fails with every line {@code judge}
	 * flags, or succeeds.
	 */
	static void sweep(GameTestHelper helper, Judge judge) {
		ServerLevel level = helper.getLevel();
		List<Case> cases = new ArrayList<>();
		for (Item item : BuiltInRegistries.ITEM) {
			CraftJobs.Job job = CraftJobs.maker(level, item);
			if (job == null) {
				continue;
			}
			Crafting.Kind kind = kindOf(level, job, item);
			if (has(level, kind, item)) {
				cases.add(new Case(item, job, kind));
			}
		}
		helper.assertTrue(cases.size() >= 2000, "setup: only " + cases.size() + " items have a job and a recipe");
		step(helper, judge, cases, new int[] {0}, new LinkedHashMap<>());
	}

	private static void step(GameTestHelper helper, Judge judge, List<Case> cases, int[] at, Map<String, String> wrong) {
		ServerLevel level = helper.getLevel();
		int done = 0;
		while (at[0] < cases.size() && done < 40 && !Crafting.planningSpent(level)) {
			Case c = cases.get(at[0]);
			Map<Item, Integer> lack = Crafting.lacking(level, c.kind(), c.item(), 1, Map.of());
			if (lack.isEmpty() && Crafting.planningSpent(level)) {
				break; // the tick's planning ran out in this plan: read it again next tick
			}
			String why = judge.judge(level, c, lack);
			if (why != null) {
				wrong.put(BuiltInRegistries.ITEM.getKey(c.item()) + " (" + c.job() + ")", why + " in " + named(lack));
			}
			at[0]++;
			done++;
		}
		if (at[0] >= cases.size()) {
			helper.assertTrue(wrong.isEmpty(), wrong.size() + " of " + cases.size() + " lines are wrong: " + wrong);
			helper.succeed();
			return;
		}
		helper.runAfterDelay(1, () -> step(helper, judge, cases, at, wrong));
	}

	static String named(Map<Item, Integer> lack) {
		StringBuilder s = new StringBuilder("{");
		lack.forEach((k, v) -> s.append(BuiltInRegistries.ITEM.getKey(k)).append('=').append(v).append(' '));
		return s.toString().trim() + "}";
	}
}
