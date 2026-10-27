package io.github.jcondedata.aliveworkplace.test;

import io.github.jcondedata.aliveworkplace.blueprint.BlueprintData;
import io.github.jcondedata.aliveworkplace.build.BuildPlan;
import io.github.jcondedata.aliveworkplace.build.BuildSite;
import io.github.jcondedata.aliveworkplace.build.BuildSiteManager;
import io.github.jcondedata.aliveworkplace.build.BuilderStatusSync;
import io.github.jcondedata.aliveworkplace.build.Builders;
import io.github.jcondedata.aliveworkplace.registry.ModBlocks;
import io.github.jcondedata.aliveworkplace.registry.ModGameRules;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Container;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.village.poi.PoiTypes;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;

/**
 * What a player sees while a builder's Storehouse has run dry of what the build needs (QA, 2026-10-27). The spec: a
 * builder that can't get a material waits for it, and "if it really waits, the missing list says for what" (B21); the
 * line over its head says what it needs ("Needs: …", BuilderStatusSync), not that it is fetching when nothing can be
 * fetched. The planks are taken out of the Storehouse while the builder walks there for them.
 */
public class QaBuilderWaitStatus1027GameTests {
	private static final String HUGE = "aliveworkplace_test:huge_area";
	private static final ResourceLocation TEST_HUT = ResourceLocation.fromNamespaceAndPath("aliveworkplace_test", "test_hut");
	private static final int WAIT = 800;
	/** Ticks after the planks are gone by which the builder has found out (a walk there and the recheck). */
	private static final int SETTLE = 400;

	private static String key(Component c) {
		return c.getContents() instanceof TranslatableContents t ? t.getKey() : c.getString();
	}

	//$ gametest_ticks_batch HUGE '2400' '"qaWaitStatus1027"'
	@GameTest(template = HUGE, timeoutTicks = 2400, batch = "qaWaitStatus1027")
	public void qaABuilderWhoseStorehouseRanDryShowsWhatItNeedsNotFetching(GameTestHelper helper) {
		Leftovers.clear(helper);
		Leftovers.players(helper);
		Leftovers.halls(helper);
		Leftovers.buildSites(helper);
		Leftovers.finished(helper);
		ServerLevel level = helper.getLevel();
		helper.setDayTime(2000);
		boolean free = level.getGameRules().getBoolean(ModGameRules.FREE_MATERIALS);
		int delay = level.getGameRules().getInt(ModGameRules.BUILD_DELAY);
		boolean help = level.getGameRules().getBoolean(ModGameRules.BUILDERS_HELP);
		int ticking = level.getGameRules().getInt(GameRules.RULE_RANDOMTICKING);
		level.getGameRules().getRule(ModGameRules.FREE_MATERIALS).set(false, level.getServer());
		level.getGameRules().getRule(ModGameRules.BUILD_DELAY).set(2, level.getServer());
		level.getGameRules().getRule(ModGameRules.BUILDERS_HELP).set(false, level.getServer());
		level.getGameRules().getRule(GameRules.RULE_RANDOMTICKING).set(0, level.getServer());
		Leftovers.after(helper, () -> {
			level.getGameRules().getRule(ModGameRules.FREE_MATERIALS).set(free, level.getServer());
			level.getGameRules().getRule(ModGameRules.BUILD_DELAY).set(delay, level.getServer());
			level.getGameRules().getRule(ModGameRules.BUILDERS_HELP).set(help, level.getServer());
			level.getGameRules().getRule(GameRules.RULE_RANDOMTICKING).set(ticking, level.getServer());
		});
		Leftovers.village(helper, 48);
		BlockPos bench = new BlockPos(3, 2, 3);
		BlockPos hut = new BlockPos(3, 2, 10);
		BlockPos store = new BlockPos(14, 2, 25);
		BlockPos[] chests = {new BlockPos(11, 2, 25), new BlockPos(17, 2, 25)};
		Leftovers.stores(helper, helper.absolutePos(bench));
		helper.setBlock(store, ModBlocks.STOREHOUSE);
		BlockPos storeAbs = helper.absolutePos(store);
		if (level.getPoiManager().getType(storeAbs).isEmpty()) {
			PoiTypes.forState(level.getBlockState(storeAbs)).ifPresent(type -> level.getPoiManager().add(storeAbs.immutable(), type));
		}
		for (BlockPos c : chests) {
			helper.setBlock(c, Blocks.CHEST);
		}
		Container a = helper.getBlockEntity(chests[0]);
		a.setItem(0, new ItemStack(Items.COBBLESTONE, 25));
		a.setItem(1, new ItemStack(Items.OAK_DOOR));
		a.setItem(2, new ItemStack(Items.TORCH));
		Container b = helper.getBlockEntity(chests[1]);
		b.setItem(0, new ItemStack(Items.OAK_PLANKS, 55));
		helper.setBlock(bench, ModBlocks.BUILDERS_BENCH);
		Villager villager = helper.spawn(EntityType.VILLAGER, bench.south());
		Builders.employ(level, villager, helper.absolutePos(bench));
		BuildSite site = Builders.start(level, villager, null, TEST_HUT,
			new BlueprintData.Placement(level.dimension().location(), helper.absolutePos(hut), Rotation.NONE, Mirror.NONE));
		BuildPlan plan = site.plan(level);
		helper.assertTrue(plan != null, "no test hut blueprint");

		long[] t = {-1, 0}; // planks taken at, ticks
		int[] aside = {0};
		boolean[] near = {false};
		int[] trips = {0};
		Map<String, Integer> statuses = new TreeMap<>();
		Map<String, Integer> lines = new TreeMap<>();
		String[] chat = {null};
		StringBuilder timeline = new StringBuilder();
		String[] last = {""};
		helper.onEachTick(() -> {
			t[1]++;
			BuildSite live = BuildSiteManager.get(level).get(site.id());
			if (t[0] < 0 && live != null && live.status() == BuildSite.Status.FETCHING
					&& ((Container) helper.getBlockEntity(chests[1])).countItem(Items.OAK_PLANKS) > 0) {
				for (BlockPos c : chests) {
					Container chest = helper.getBlockEntity(c);
					for (int i = 0; i < chest.getContainerSize(); i++) {
						if (chest.getItem(i).is(Items.OAK_PLANKS)) {
							aside[0] += chest.getItem(i).getCount();
							chest.setItem(i, ItemStack.EMPTY);
						}
					}
				}
				t[0] = t[1];
			}
			if (t[0] < 0 || t[1] - t[0] >= WAIT || live == null) {
				return;
			}
			// A trip: the builder comes within 4 blocks of the storehouse from further off.
			boolean isNear = villager.distanceToSqr(storeAbs.getX() + 0.5, storeAbs.getY(), storeAbs.getZ() + 0.5) <= 16;
			if (isNear && !near[0]) {
				trips[0]++;
			}
			near[0] = isNear;
			BuilderStatusSync.Status now = BuilderStatusSync.status(level, live, villager);
			String state = live.status().name() + "/" + (now == null ? "-" : now.line().getString());
			if (!state.equals(last[0])) {
				timeline.append(" @").append(t[1] - t[0]).append(' ').append(state);
				last[0] = state;
			}
			if (t[1] - t[0] >= SETTLE) {
				statuses.merge(live.status().name(), 1, Integer::sum);
				BuilderStatusSync.Status shown = BuilderStatusSync.status(level, live, villager);
				if (shown != null) {
					lines.merge(key(shown.line()) + " \"" + shown.line().getString() + "\"", 1, Integer::sum);
				}
			}
			if (t[1] - t[0] == WAIT - 1) {
				chat[0] = Builders.statusText(level, live, villager).getString().replace('\n', '|');
			}
		});
		helper.runAfterDelay(2300, () -> {
			String seen = "statuses " + statuses + "; overhead lines " + lines + "; trips to the storehouse in " + WAIT + " ticks: " + trips[0]
				+ "; status chat: " + chat[0] + "; timeline after the planks went:" + timeline;
			// put the planks back, so nothing is left lying about
			if (aside[0] > 0) {
				((Container) helper.getBlockEntity(chests[1])).setItem(0, new ItemStack(Items.OAK_PLANKS, aside[0]));
			}
			helper.assertTrue(t[0] >= 0, "the builder never set out for the storehouse's planks");
			int needs = lines.entrySet().stream().filter(e -> e.getKey().startsWith("message.aliveworkplace.status.needs")
				&& (e.getKey().contains("Oak Planks") || e.getKey().contains("oak_planks"))).mapToInt(Map.Entry::getValue).sum();
			int total = lines.values().stream().mapToInt(Integer::intValue).sum();
			helper.assertTrue(total > 0 && needs == total, "with the storehouse out of planks for " + (WAIT - SETTLE)
				+ " ticks, the line over the builder's head doesn't always say it needs Oak Planks: " + seen);
			helper.assertTrue(chat[0] != null && chat[0].contains("waiting for materials") && (chat[0].contains("Oak Planks") || chat[0].contains("oak_planks")),
				"the builder's status (clicked) doesn't say it is waiting for Oak Planks: " + seen);
			helper.assertTrue(trips[0] <= 2, "the builder walked to the empty storehouse " + trips[0] + " times in " + WAIT + " ticks: " + seen);
			helper.succeed();
		});
	}
}
