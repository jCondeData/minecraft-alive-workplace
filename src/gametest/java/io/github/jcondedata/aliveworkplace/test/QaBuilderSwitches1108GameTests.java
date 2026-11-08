package io.github.jcondedata.aliveworkplace.test;

import io.github.jcondedata.aliveworkplace.build.BuildPlan;
import io.github.jcondedata.aliveworkplace.build.BuildSite;
import io.github.jcondedata.aliveworkplace.build.Builders;
import io.github.jcondedata.aliveworkplace.build.Employer;
import io.github.jcondedata.aliveworkplace.registry.ModAttachments;
import io.github.jcondedata.aliveworkplace.registry.ModBlocks;
import io.github.jcondedata.aliveworkplace.registry.ModGameRules;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Container;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.item.Item;

/**
 * QA (qa-lane-b-1108-1238), from docs/wiki/builder.md's switch table ("workplaceBuildersHelp | true | Idle Builders
 * help builds near their bench") and ModGameRules ("workplaceBuilderOwnership: a builder only takes orders from the
 * player who hired it, their friends and operators"; Builders.recruit: "a hired builder only helps its employer and
 * their friends"), plus CLAUDE.md ("BuilderWork must stay restartable at any tick"). The older tests switch
 * workplaceBuildersHelp off as setup only; these switch it (and ownership) while a second Builder stands idle beside a
 * build, and check the build ends as drawn with every material accounted for (nothing lost, nothing doubled).
 */
public class QaBuilderSwitches1108GameTests {
	private static final String HUGE = QaBuilderConfig1102GameTests.HUGE;
	private static final BlockPos BENCH = new BlockPos(2, 2, 2);
	private static final BlockPos CHEST = new BlockPos(2, 2, 4);
	private static final BlockPos HUT = new BlockPos(12, 2, 12);
	private static final BlockPos IDLE_BENCH = new BlockPos(7, 2, 2);
	private static final UUID STRANGER = UUID.fromString("5f1b2c3d-0000-4000-8000-0000000b1108");

	// ------------------------------------------------------------------------------------------------- helpers

	private static Villager idleBuilder(GameTestHelper helper) {
		helper.setBlock(IDLE_BENCH, ModBlocks.BUILDERS_BENCH);
		Villager idle = helper.spawn(EntityType.VILLAGER, IDLE_BENCH.offset(1, 0, 1));
		Builders.employ(helper.getLevel(), idle, helper.absolutePos(IDLE_BENCH));
		ModAttachments.BUILDER_BAG.getOrCreate(idle).takeAll();
		return idle;
	}

	/** The hut's materials not yet placed: in the chest plus every builder's bag. */
	private static Map<Item, Integer> held(GameTestHelper helper, Villager... builders) {
		Map<Item, Integer> out = new LinkedHashMap<>();
		Container chest = helper.getBlockEntity(CHEST);
		for (Item item : QaBuilderConfig1102GameTests.HUT_ITEMS) {
			int n = chest.countItem(item);
			for (Villager v : builders) {
				n += ModAttachments.BUILDER_BAG.getOrCreate(v).count(item);
			}
			out.put(item, n);
		}
		return out;
	}

	private static Map<Item, Integer> start(GameTestHelper helper) {
		Map<Item, Integer> out = new LinkedHashMap<>();
		for (var stack : QaBuilderConfig1102GameTests.hutMaterials()) {
			out.merge(stack.getItem(), stack.getCount(), Integer::sum);
		}
		return out;
	}

	/** Every material either built into the hut (the plan's list) or still held; none on the ground, none extra. */
	private static void assertAccounted(GameTestHelper helper, QaBuilderConfig1102GameTests.Run r, Map<Item, Integer> before,
		Villager... builders) {
		Map<Item, Integer> used = r.plan().materials();
		Map<Item, Integer> now = held(helper, builders);
		List<String> wrong = new ArrayList<>();
		for (Item item : QaBuilderConfig1102GameTests.HUT_ITEMS) {
			int expected = before.getOrDefault(item, 0) - used.getOrDefault(item, 0);
			int ground = QaBuilderConfig1102GameTests.onTheGround(helper, item);
			if (now.get(item) + ground != expected) {
				wrong.add(item + ": held " + now.get(item) + " + ground " + ground + ", expected " + expected);
			}
		}
		helper.assertTrue(wrong.isEmpty(), "materials don't add up: " + wrong);
	}

	// ------------------------------------------------------------------------------------------------- tests

	/**
	 * workplaceBuildersHelp off: an idle Builder 5 blocks from a running build never joins it, also across a server
	 * restart in the middle of STRUCTURE. Switched on again, he joins as a helper and the hut is finished as drawn with
	 * every material accounted for.
	 */
	//$ gametest_ticks_batch HUGE '6000' '"qaSw1108HelpOff"'
	@GameTest(template = HUGE, timeoutTicks = 6000, batch = "qaSw1108HelpOff")
	public void helpOffKeepsAnIdleBuilderOutThenOnHeJoinsAndTheHutAddsUp(GameTestHelper helper) {
		ServerLevel level = QaBuilderConfig1102GameTests.rules(helper, false, 4);
		Map<Item, Integer> before = start(helper);
		QaBuilderConfig1102GameTests.Run r = QaBuilderConfig1102GameTests.start(helper, BENCH, CHEST, HUT,
			QaBuilderConfig1102GameTests.hutMaterials());
		Villager idle = idleBuilder(helper);
		List<String> joinedWhileOff = new ArrayList<>();
		boolean[] on = {false};
		boolean[] joined = {false};
		helper.onEachTick(() -> {
			helper.setDayTime(2000);
			BuildSite site = r.live();
			if (!on[0]) {
				if (ModAttachments.BUILDER_JOB.has(idle) && joinedWhileOff.isEmpty()) {
					joinedWhileOff.add("job " + ModAttachments.BUILDER_JOB.get(idle) + " at " + QaBuilderConfig1102GameTests.where(helper, r));
				}
				if (site != null && site.stage() == BuildPlan.Stage.STRUCTURE && site.cursor() >= 10) {
					QaBuilderConfig1102GameTests.restart(helper, r);
					level.getGameRules().getRule(ModGameRules.BUILDERS_HELP).set(true, level.getServer());
					on[0] = true;
				}
				return;
			}
			if (Builders.isHelping(idle)) {
				joined[0] = true;
			}
		});
		helper.succeedWhen(() -> {
			helper.assertTrue(joinedWhileOff.isEmpty(), "with workplaceBuildersHelp off the idle Builder joined: " + joinedWhileOff);
			helper.assertTrue(on[0], "the build never reached STRUCTURE cursor 10: " + QaBuilderConfig1102GameTests.where(helper, r));
			QaBuilderConfig1102GameTests.assertBuilt(helper, r);
			helper.assertTrue(joined[0], "with workplaceBuildersHelp back on the idle Builder never helped");
			assertAccounted(helper, r, before, r.villager(), idle);
		});
	}

	/**
	 * workplaceBuildersHelp switched off while a helper is working on the hut: the build still ends as drawn, with no
	 * step lost or doubled and every material accounted for (whatever the helper carried is back in play).
	 */
	//$ gametest_ticks_batch HUGE '6000' '"qaSw1108HelpOffMid"'
	@GameTest(template = HUGE, timeoutTicks = 6000, batch = "qaSw1108HelpOffMid")
	public void helpSwitchedOffWhileAHelperWorksStillFinishesTheHutWithNothingLost(GameTestHelper helper) {
		ServerLevel level = QaBuilderConfig1102GameTests.rules(helper, false, 4);
		level.getGameRules().getRule(ModGameRules.BUILDERS_HELP).set(true, level.getServer());
		Map<Item, Integer> before = start(helper);
		QaBuilderConfig1102GameTests.Run r = QaBuilderConfig1102GameTests.start(helper, BENCH, CHEST, HUT,
			QaBuilderConfig1102GameTests.hutMaterials());
		Villager idle = idleBuilder(helper);
		boolean[] off = {false};
		helper.onEachTick(() -> {
			helper.setDayTime(2000);
			BuildSite site = r.live();
			if (!off[0] && site != null && Builders.isHelping(idle) && site.stage() == BuildPlan.Stage.STRUCTURE && site.cursor() >= 5) {
				level.getGameRules().getRule(ModGameRules.BUILDERS_HELP).set(false, level.getServer());
				off[0] = true;
			}
		});
		helper.succeedWhen(() -> {
			helper.assertTrue(off[0], "setup: the idle Builder never helped in STRUCTURE: " + QaBuilderConfig1102GameTests.where(helper, r));
			QaBuilderConfig1102GameTests.assertBuilt(helper, r);
			assertAccounted(helper, r, before, r.villager(), idle);
			helper.assertFalse(Builders.isHelping(idle), "the helper still holds a helper job on a finished build");
		});
	}

	/**
	 * workplaceBuilderOwnership on: an idle Builder hired by another player never helps Jesse's hut. Switched off, the
	 * rule no longer keeps him out: he joins as a helper and the hut ends as drawn with every material accounted for.
	 */
	//$ gametest_ticks_batch HUGE '6000' '"qaSw1108Ownership"'
	@GameTest(template = HUGE, timeoutTicks = 6000, batch = "qaSw1108Ownership")
	public void ownershipKeepsAStrangersBuilderOutThenOffHeHelps(GameTestHelper helper) {
		ServerLevel level = QaBuilderConfig1102GameTests.rules(helper, false, 4);
		boolean owned = level.getGameRules().getBoolean(ModGameRules.BUILDER_OWNERSHIP);
		Leftovers.after(helper, () -> level.getGameRules().getRule(ModGameRules.BUILDER_OWNERSHIP).set(owned, level.getServer()));
		level.getGameRules().getRule(ModGameRules.BUILDER_OWNERSHIP).set(true, level.getServer());
		level.getGameRules().getRule(ModGameRules.BUILDERS_HELP).set(true, level.getServer());
		Map<Item, Integer> before = start(helper);
		QaBuilderConfig1102GameTests.Run r = QaBuilderConfig1102GameTests.start(helper, BENCH, CHEST, HUT,
			QaBuilderConfig1102GameTests.hutMaterials());
		Villager idle = idleBuilder(helper);
		ModAttachments.BUILDER_EMPLOYER.set(idle, new Employer(STRANGER, "Stranger"));
		List<String> joinedWhileOwned = new ArrayList<>();
		boolean[] free = {false};
		boolean[] joined = {false};
		helper.onEachTick(() -> {
			helper.setDayTime(2000);
			BuildSite site = r.live();
			if (!free[0]) {
				if (ModAttachments.BUILDER_JOB.has(idle) && joinedWhileOwned.isEmpty()) {
					joinedWhileOwned.add("job " + ModAttachments.BUILDER_JOB.get(idle) + " at " + QaBuilderConfig1102GameTests.where(helper, r));
				}
				if (site != null && site.stage() == BuildPlan.Stage.STRUCTURE && site.cursor() >= 10) {
					level.getGameRules().getRule(ModGameRules.BUILDER_OWNERSHIP).set(false, level.getServer());
					free[0] = true;
				}
				return;
			}
			if (Builders.isHelping(idle)) {
				joined[0] = true;
			}
		});
		helper.succeedWhen(() -> {
			helper.assertTrue(joinedWhileOwned.isEmpty(), "a stranger's Builder helped Jesse's hut with ownership on: " + joinedWhileOwned);
			helper.assertTrue(free[0], "the build never reached STRUCTURE cursor 10: " + QaBuilderConfig1102GameTests.where(helper, r));
			QaBuilderConfig1102GameTests.assertBuilt(helper, r);
			helper.assertTrue(joined[0], "with workplaceBuilderOwnership off the stranger's idle Builder never helped");
			assertAccounted(helper, r, before, r.villager(), idle);
		});
	}
}
