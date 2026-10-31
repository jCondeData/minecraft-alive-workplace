package io.github.jcondedata.aliveworkplace.test;

import io.github.jcondedata.aliveworkplace.blueprint.BlueprintData;
import io.github.jcondedata.aliveworkplace.build.BuildPlan;
import io.github.jcondedata.aliveworkplace.build.BuildSite;
import io.github.jcondedata.aliveworkplace.build.BuildSiteManager;
import io.github.jcondedata.aliveworkplace.build.Builders;
import io.github.jcondedata.aliveworkplace.build.MaterialRules;
import io.github.jcondedata.aliveworkplace.build.Scaffolds;
import io.github.jcondedata.aliveworkplace.build.Upkeep;
import io.github.jcondedata.aliveworkplace.craft.Crafting;
import io.github.jcondedata.aliveworkplace.craft.JobShortages;
import io.github.jcondedata.aliveworkplace.registry.ModAttachments;
import io.github.jcondedata.aliveworkplace.registry.ModBlocks;
import io.github.jcondedata.aliveworkplace.registry.ModGameRules;
import io.github.jcondedata.aliveworkplace.work.Requests;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Vec3i;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Container;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.LadderBlock;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;

/**
 * QA (lane-b-1027-0038) of four builder supply fixes, written from their spec, plus kill-tests for two nightly mutation
 * survivors (run 40296140142).
 * <ul>
 * <li>B499 (wiki scaffolding.md, "Asking for it": the shortfall shows on the Storehouse's requests board and the Village
 * Hall "while the builder carries on"; "The request goes away once the builder finds enough in its chests"; switch
 * {@code builderScaffolding} off: "those blocks are skipped as before"; free materials: "put up and taken down for
 * nothing").</li>
 * <li>B519 (wiki builder.md: the bag holds "never more of a thing than the stretch of work ahead still needs"): crews of
 * two and three with no spare at all, and a helper saved and loaded mid-job.</li>
 * <li>B489 (wiki crafter.md: "The line names what the chest is short of ... It goes further down only when that uses
 * something that's there"; "a coal for every eight fired"): ingots, raw iron and ore in the chest; the fuel count at 8
 * and 9 fired (kills the CrafterWork.fuelShort rounding survivor).</li>
 * <li>B512/B261 (a repair's building's chests count): a trapped chest after a reload, at night until morning, and with
 * one material in the trapped chest and the other in a plain chest by the bench.</li>
 * <li>Survivor BuildSite.java:563 (wiki builder.md saved data: {@code left_out} holds "the structure and decoration
 * blocks given up for good").</li>
 * </ul>
 */
public class QaBuilderSupply1027GameTests {
	private static final String AREA = "aliveworkplace_test:build_area";
	private static final String BIG = "aliveworkplace_test:big_area";
	private static final org.slf4j.Logger LOG = org.slf4j.LoggerFactory.getLogger("aliveworkplace-qa");

	// =============================================================================================================== B499

	private static boolean asksForScaffolding(QaScaffoldingGameTests.Site s) {
		for (Requests.Request r : QaScaffoldingGameTests.board(s)) {
			if (r.icon().is(Items.SCAFFOLDING)) {
				return true;
			}
		}
		return false;
	}

	/**
	 * No scaffolding: the board asks for it while the build goes on. Then a player puts 64 in the chest: before the build
	 * ends there is a moment the site is still going and the board no longer asks ("The request goes away once the
	 * builder finds enough in its chests"), the build ends, and nothing is left standing.
	 */
	//$ gametest_ticks_batch BIG '12000' '"qaS1027Refill"'
	@GameTest(template = BIG, timeoutTicks = 12000, batch = "qaS1027Refill")
	public void scaffoldingBroughtLaterTakesItOffTheBoard(GameTestHelper helper) {
		QaScaffoldingGameTests.Site s = QaScaffoldingGameTests.pillar(helper, 0);
		long[] askedAt = {-1};
		long[] clearedAt = {-1};
		helper.onEachTick(() -> {
			boolean going = BuildSiteManager.get(s.level()).get(s.site().id()) != null;
			if (!going) {
				return;
			}
			boolean asks = asksForScaffolding(s);
			if (askedAt[0] < 0 && asks) {
				askedAt[0] = helper.getTick();
				Container chest = helper.getBlockEntity(QaScaffoldingGameTests.CHEST);
				chest.setItem(5, new ItemStack(Items.SCAFFOLDING, 64));
			} else if (askedAt[0] >= 0 && clearedAt[0] < 0 && !asks) {
				clearedAt[0] = helper.getTick();
			}
		});
		helper.succeedWhen(() -> {
			helper.assertTrue(askedAt[0] >= 0, "the board never asked for scaffolding with none in the chest");
			// (blocks given up before the scaffolding came are skipped, as the spec says: only the end of the build is checked)
			helper.assertTrue(BuildSiteManager.get(s.level()).get(s.site().id()) == null, "still building: stage " + s.site().stage());
			helper.assertTrue(clearedAt[0] >= 0, "64 scaffolding were put in the chest at tick " + askedAt[0]
				+ " but the board still asked for scaffolding until the build ended");
			QaScaffoldingGameTests.assertNoScaffolding(helper);
			LOG.info("[QA S1027] board asked at {}, scaffolding brought, board cleared at {}", askedAt[0], clearedAt[0]);
		});
	}

	/** Plenty of scaffolding (64): the board never asks for scaffolding at any tick of the build, and the build ends. */
	//$ gametest_ticks_batch BIG '9000' '"qaS1027Enough"'
	@GameTest(template = BIG, timeoutTicks = 9000, batch = "qaS1027Enough")
	public void enoughScaffoldingIsNeverAskedFor(GameTestHelper helper) {
		QaScaffoldingGameTests.Site s = QaScaffoldingGameTests.pillar(helper, QaScaffoldingGameTests.SCAFFOLDING);
		long[] asked = {-1};
		int[] most = {0};
		helper.onEachTick(() -> {
			most[0] = Math.max(most[0], s.site().scaffolds().size());
			if (asked[0] < 0 && BuildSiteManager.get(s.level()).get(s.site().id()) != null && asksForScaffolding(s)) {
				asked[0] = helper.getTick();
			}
		});
		helper.succeedWhen(() -> {
			QaScaffoldingGameTests.assertFinished(helper, s);
			helper.assertTrue(most[0] > 0, "setup: no scaffolding went up");
			helper.assertTrue(asked[0] < 0, "with 64 scaffolding in the chest the board asked for scaffolding at tick " + asked[0]);
		});
	}

	/** Three scaffolding, too few for a column: the board asks, for a positive count named "Scaffolding" (the item's name). */
	//$ gametest_ticks_batch BIG '12000' '"qaS1027TooFew"'
	@GameTest(template = BIG, timeoutTicks = 12000, batch = "qaS1027TooFew")
	public void tooFewScaffoldingIsAskedFor(GameTestHelper helper) {
		QaScaffoldingGameTests.Site s = QaScaffoldingGameTests.pillar(helper, 3);
		String[] shown = {null};
		helper.onEachTick(() -> {
			if (shown[0] == null && BuildSiteManager.get(s.level()).get(s.site().id()) != null) {
				for (Requests.Request r : QaScaffoldingGameTests.board(s)) {
					if (r.icon().is(Items.SCAFFOLDING)) {
						shown[0] = r.count() + " " + r.what().getString();
					}
				}
			}
		});
		helper.succeedWhen(() -> {
			helper.assertTrue(BuildSiteManager.get(s.level()).get(s.site().id()) == null, "still building: stage " + s.site().stage());
			helper.assertTrue(shown[0] != null, "with 3 scaffolding (too few for a column) the board never asked for more");
			helper.assertTrue(shown[0].matches("[1-9][0-9]* Scaffolding"), "the board read: " + shown[0]);
			LOG.info("[QA S1027] 3 in the chest, board asked for: {}", shown[0]);
		});
	}

	/** builderScaffolding off, none in the chest: no scaffolding put up, never asked for, the build ends. */
	//$ gametest_ticks_batch BIG '9000' '"qaS1027Off"'
	@GameTest(template = BIG, timeoutTicks = 9000, batch = "qaS1027Off")
	public void withScaffoldingOffItIsNeverAskedFor(GameTestHelper helper) {
		boolean was = Scaffolds.ENABLED;
		Scaffolds.ENABLED = false;
		Leftovers.after(helper, () -> Scaffolds.ENABLED = was);
		QaScaffoldingGameTests.Site s = QaScaffoldingGameTests.pillar(helper, 0);
		long[] asked = {-1};
		int[] most = {0};
		helper.onEachTick(() -> {
			most[0] = Math.max(most[0], s.site().scaffolds().size());
			if (asked[0] < 0 && BuildSiteManager.get(s.level()).get(s.site().id()) != null
				&& (asksForScaffolding(s) || s.site().missing().containsKey(Items.SCAFFOLDING))) {
				asked[0] = helper.getTick();
			}
		});
		helper.succeedWhen(() -> {
			helper.assertTrue(BuildSiteManager.get(s.level()).get(s.site().id()) == null, "still building: stage " + s.site().stage());
			helper.assertTrue(most[0] == 0, most[0] + " scaffold(s) went up with builderScaffolding off");
			helper.assertTrue(asked[0] < 0, "with builderScaffolding off scaffolding was asked for at tick " + asked[0]);
			QaScaffoldingGameTests.assertNoScaffolding(helper);
		});
	}

	/** Free materials on, no scaffolding anywhere: scaffolding goes up "for nothing" and is never asked for. */
	//$ gametest_ticks_batch BIG '9000' '"qaS1027Free"'
	@GameTest(template = BIG, timeoutTicks = 9000, batch = "qaS1027Free")
	public void withFreeMaterialsScaffoldingIsNeverAskedFor(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		boolean was = level.getGameRules().getBoolean(ModGameRules.FREE_MATERIALS);
		level.getGameRules().getRule(ModGameRules.FREE_MATERIALS).set(true, level.getServer());
		Leftovers.after(helper, () -> level.getGameRules().getRule(ModGameRules.FREE_MATERIALS).set(was, level.getServer()));
		QaScaffoldingGameTests.Site s = QaScaffoldingGameTests.pillar(helper, 0);
		long[] asked = {-1};
		helper.onEachTick(() -> {
			if (asked[0] < 0 && BuildSiteManager.get(s.level()).get(s.site().id()) != null && asksForScaffolding(s)) {
				asked[0] = helper.getTick();
			}
		});
		helper.succeedWhen(() -> {
			QaScaffoldingGameTests.assertFinished(helper, s);
			helper.assertTrue(asked[0] < 0, "with free materials scaffolding was asked for at tick " + asked[0]);
		});
	}

	// =============================================================================================================== B519

	private static final ResourceLocation TEST_HUT = ResourceLocation.fromNamespaceAndPath("aliveworkplace_test", "test_hut");
	private static final BlockPos TABLE = new BlockPos(2, 2, 2);
	private static final BlockPos CREW_CHEST = new BlockPos(2, 2, 4);
	private static final BlockPos LEAD = new BlockPos(3, 2, 3);
	private static final BlockPos HUT = new BlockPos(6, 2, 6);
	private static final BlockPos[] MATE_TABLES = {new BlockPos(13, 2, 2), new BlockPos(15, 2, 2)};

	private record Crew(ServerLevel level, Villager[] all, BuildSite site, BuildPlan plan, Map<Item, Integer> materials) {
		boolean finished() {
			return BuildSiteManager.get(level).get(site.id()) == null;
		}
	}

	private static Crew crew(GameTestHelper helper, int mates, int stock) {
		Leftovers.clear(helper);
		Leftovers.finished(helper);
		ServerLevel level = helper.getLevel();
		helper.setDayTime(2000);
		boolean protection = io.github.jcondedata.aliveworkplace.hall.VillageProtection.ENABLED;
		io.github.jcondedata.aliveworkplace.hall.VillageProtection.ENABLED = false;
		var rules = level.getGameRules();
		boolean help = rules.getRule(ModGameRules.BUILDERS_HELP).get();
		int delay = rules.getRule(ModGameRules.BUILD_DELAY).get();
		boolean free = rules.getRule(ModGameRules.FREE_MATERIALS).get();
		rules.getRule(ModGameRules.BUILDERS_HELP).set(true, level.getServer());
		rules.getRule(ModGameRules.BUILD_DELAY).set(2, level.getServer());
		rules.getRule(ModGameRules.FREE_MATERIALS).set(false, level.getServer());
		Leftovers.after(helper, () -> {
			io.github.jcondedata.aliveworkplace.hall.VillageProtection.ENABLED = protection;
			rules.getRule(ModGameRules.BUILDERS_HELP).set(help, level.getServer());
			rules.getRule(ModGameRules.BUILD_DELAY).set(delay, level.getServer());
			rules.getRule(ModGameRules.FREE_MATERIALS).set(free, level.getServer());
		});
		BuildSiteManager sites = BuildSiteManager.get(level);
		for (BuildSite other : new ArrayList<>(sites.all())) {
			if (other.bench() != null && other.bench().closerThan(helper.absolutePos(TABLE), 3 * Builders.MAX_SITE_DISTANCE)) {
				sites.remove(other.id());
			}
		}
		helper.setBlock(TABLE, ModBlocks.BUILDERS_BENCH);
		helper.setBlock(CREW_CHEST, Blocks.CHEST);
		Villager[] all = new Villager[mates + 1];
		all[0] = helper.spawn(EntityType.VILLAGER, LEAD);
		Builders.employ(level, all[0], helper.absolutePos(TABLE));
		for (int i = 0; i < mates; i++) {
			helper.setBlock(MATE_TABLES[i], ModBlocks.BUILDERS_BENCH);
			all[i + 1] = helper.spawn(EntityType.VILLAGER, MATE_TABLES[i].south().east());
			Builders.employ(level, all[i + 1], helper.absolutePos(MATE_TABLES[i]));
		}
		BlueprintData.Placement placement = new BlueprintData.Placement(level.dimension().location(), helper.absolutePos(HUT), Rotation.NONE, Mirror.NONE);
		BuildSite site = Builders.start(level, all[0], null, TEST_HUT, placement);
		helper.assertTrue(site != null, "setup: the hut didn't start");
		BuildPlan plan = site.plan(level);
		helper.assertTrue(plan != null, "setup: no plan for the test hut");
		Leftovers.after(helper, () -> BuildSiteManager.get(level).remove(site.id()));
		Map<Item, Integer> materials = plan.materials();
		Container chest = helper.getBlockEntity(CREW_CHEST);
		int slot = 0;
		for (Map.Entry<Item, Integer> e : materials.entrySet()) {
			int left = e.getValue() * stock;
			while (left > 0) {
				int n = Math.min(left, e.getKey().getDefaultMaxStackSize());
				chest.setItem(slot++, new ItemStack(e.getKey(), n));
				left -= n;
			}
		}
		return new Crew(level, all, site, plan, materials);
	}

	private static Map<Item, Integer> bag(Villager villager) {
		Map<Item, Integer> out = new LinkedHashMap<>();
		for (ItemStack stack : ModAttachments.BUILDER_BAG.getOrCreate(villager).stacks()) {
			if (!stack.isEmpty()) {
				out.merge(stack.getItem(), stack.getCount(), Integer::sum);
			}
		}
		return out;
	}

	/** What the build still needs, by item: every foundation, structure and decoration step not yet as drawn. */
	private static Map<Item, Integer> stillNeeded(ServerLevel level, BuildPlan plan) {
		Map<Item, Integer> out = new LinkedHashMap<>();
		for (BuildPlan.Stage stage : List.of(BuildPlan.Stage.FOUNDATION, BuildPlan.Stage.STRUCTURE, BuildPlan.Stage.DECORATION)) {
			for (BuildPlan.Step s : plan.steps(stage)) {
				if (!MaterialRules.matches(level.getBlockState(s.pos()), s.state())) {
					for (MaterialRules.Requirement req : s.requirements()) {
						out.merge(req.item(), req.count(), Integer::sum);
					}
				}
			}
		}
		return out;
	}

	/** The hut's materials in the chest, every crew member's bag and on the ground round the area. */
	private static Map<Item, Integer> held(GameTestHelper helper, Crew c, Villager[] all) {
		Container chest = helper.getBlockEntity(CREW_CHEST);
		Map<Item, Integer> out = new LinkedHashMap<>();
		for (Item item : c.materials().keySet()) {
			int n = chest.countItem(item);
			for (Villager v : all) {
				n += v.isAlive() ? ModAttachments.BUILDER_BAG.getOrCreate(v).count(item) : 0;
			}
			for (ItemEntity e : helper.getLevel().getEntitiesOfClass(ItemEntity.class, helper.getBounds().inflate(8), e -> e.getItem().is(item))) {
				n += e.getItem().getCount();
			}
			out.put(item, n);
		}
		return out;
	}

	/**
	 * Watches every bag each tick: whenever one grows, it holds no more of any of the hut's materials than the hut still
	 * needs of that thing. Then the hut is whole, nothing skipped, bags empty, and the chest holds exactly
	 * {@code stock - 1} times the materials (nothing lost, nothing doubled).
	 */
	private static void crewCarry(GameTestHelper helper, int mates, int stock, String tag) {
		Crew c = crew(helper, mates, stock);
		Villager[] all = c.all();
		List<Map<Item, Integer>> last = new ArrayList<>();
		for (int i = 0; i < all.length; i++) {
			last.add(Map.of());
		}
		boolean[] helped = {false};
		int[] fills = {0};
		helper.onEachTick(() -> {
			helper.setDayTime(2000);
			if (c.finished()) {
				return;
			}
			Map<Item, Integer> needed = stillNeeded(c.level(), c.plan());
			for (int i = 0; i < all.length; i++) {
				helped[0] |= i > 0 && Builders.isHelping(all[i]);
				Map<Item, Integer> now = bag(all[i]);
				Map<Item, Integer> before = last.get(i);
				if (now.entrySet().stream().anyMatch(e -> e.getValue() > before.getOrDefault(e.getKey(), 0))) {
					fills[0]++;
					for (Map.Entry<Item, Integer> e : now.entrySet()) {
						if (c.materials().containsKey(e.getKey())) {
							int need = needed.getOrDefault(e.getKey(), 0);
							if (e.getValue() > need) {
								throw new GameTestAssertException((i == 0 ? "the lead" : "helper " + i) + " filled its bag to " + now + ": " + e.getValue()
									+ " " + e.getKey() + " with only " + need + " still needed (stage " + c.site().stage() + ", crew of " + all.length + ")");
							}
						}
					}
				}
				last.set(i, now);
			}
		});
		helper.succeedWhen(() -> {
			helper.assertTrue(helped[0], "setup: no idle builder ever helped");
			helper.assertTrue(c.finished(), "the hut isn't finished: stage " + c.site().stage() + " status " + c.site().status() + " missing "
				+ c.site().missing());
			List<BlockPos> wrong = c.plan().unfinished(c.level());
			helper.assertTrue(wrong.isEmpty(), wrong.size() + " block(s) of the hut missing or wrong");
			helper.assertTrue(c.site().skipped() == 0, c.site().skipped() + " step(s) skipped with exactly enough materials and a crew of " + all.length);
			for (int i = 0; i < all.length; i++) {
				helper.assertTrue(bag(all[i]).isEmpty(), "builder " + i + "'s bag isn't empty after the hut: " + bag(all[i]));
			}
			Map<Item, Integer> held = held(helper, c, all);
			Map<Item, Integer> spare = new LinkedHashMap<>();
			c.materials().forEach((item, n) -> spare.put(item, n * (stock - 1)));
			helper.assertTrue(held.equals(spare), "after the hut the chest should hold " + spare + " but the materials held are " + held);
			LOG.info("[QA S1027] {}: {} bag fills checked", tag, fills[0]);
		});
	}

	/** A lead and one helper, exactly the hut's materials in the chest (no spare): no bag past what's still needed. */
	//$ gametest_ticks_batch AREA '4000' '"qaS1027CrewTwo"'
	@GameTest(template = AREA, timeoutTicks = 4000, batch = "qaS1027CrewTwo")
	public void aCrewOfTwoWithNoSpareNeverOverfillsABag(GameTestHelper helper) {
		crewCarry(helper, 1, 1, "crew of 2, no spare");
	}

	/** A lead and two helpers, exactly the hut's materials: no bag past what's still needed, and nothing skipped. */
	//$ gametest_ticks_batch AREA '4000' '"qaS1027CrewThree"'
	@GameTest(template = AREA, timeoutTicks = 4000, batch = "qaS1027CrewThree")
	public void aCrewOfThreeWithNoSpareNeverOverfillsABag(GameTestHelper helper) {
		crewCarry(helper, 2, 1, "crew of 3, no spare");
	}

	/**
	 * A lead and one helper, twice the materials; the helper is saved and loaded (like a chunk unload) the first time it
	 * carries something on the job. The hut is still finished, every bag ends empty and exactly the spare is back.
	 */
	//$ gametest_ticks_batch AREA '4000' '"qaS1027CrewReload"'
	@GameTest(template = AREA, timeoutTicks = 4000, batch = "qaS1027CrewReload")
	public void aHelperSavedAndLoadedMidJobLosesNothing(GameTestHelper helper) {
		Crew c = crew(helper, 1, 2);
		Villager[] all = c.all().clone();
		boolean[] reloaded = {false};
		List<Map<Item, Integer>> carried = new ArrayList<>(List.of(Map.of()));
		helper.onEachTick(() -> {
			helper.setDayTime(2000);
			if (!reloaded[0] && !c.finished() && Builders.isHelping(all[1]) && !bag(all[1]).isEmpty()) {
				reloaded[0] = true;
				carried.set(0, bag(all[1]));
				all[1] = reload(c.level(), all[1]);
				helper.assertTrue(bag(all[1]).equals(carried.get(0)), "the helper's bag before the reload " + carried.get(0) + ", after " + bag(all[1]));
			}
		});
		helper.succeedWhen(() -> {
			helper.assertTrue(reloaded[0], "the helper never carried anything on the job: stage " + c.site().stage());
			helper.assertTrue(c.finished(), "the hut isn't finished: stage " + c.site().stage() + " missing " + c.site().missing());
			List<BlockPos> wrong = c.plan().unfinished(c.level());
			helper.assertTrue(wrong.isEmpty(), wrong.size() + " block(s) of the hut missing or wrong");
			for (int i = 0; i < all.length; i++) {
				helper.assertTrue(bag(all[i]).isEmpty(), "builder " + i + "'s bag isn't empty after the hut: " + bag(all[i]));
			}
			Map<Item, Integer> held = held(helper, c, all);
			helper.assertTrue(held.equals(c.materials()), "after the hut the chest should hold the spare " + c.materials() + " but holds " + held
				+ " (the helper carried " + carried.get(0) + " when reloaded)");
		});
	}

	private static Villager reload(ServerLevel level, Villager villager) {
		CompoundTag tag = new CompoundTag();
		villager.saveWithoutId(tag);
		villager.discard();
		Villager copy = EntityType.VILLAGER.create(level);
		if (copy == null) {
			throw new GameTestAssertException("no villager made");
		}
		copy.load(tag);
		level.addFreshEntity(copy);
		io.github.jcondedata.aliveworkplace.work.Village.forget(copy);
		return copy;
	}

	// =============================================================================================================== B489

	/**
	 * Read off the Tinkerer's (workshop) recipes, three iron bars (six ingots): exactly six raw iron, or six ingots, lack
	 * nothing; five ingots lack exactly one ingot (the chest is short of an ingot, and nothing there goes further down);
	 * five ingots and one raw iron lack nothing (down through what's there); five raw iron lack one raw iron; nothing at
	 * all six raw iron; and never Deepslate Iron Ore in any of them.
	 */
	//$ gametest 'FabricGameTest.EMPTY_STRUCTURE'
	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
	public void ironBarsLackWhatTheChestIsShortOf(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		Crafting.Kind kind = Crafting.Kind.WORKSHOP;
		List<String> wrong = new ArrayList<>();
		Map<String, Object[]> cases = new LinkedHashMap<>();
		cases.put("6 raw iron", new Object[]{Map.of(Items.RAW_IRON, 6L), Map.of()});
		cases.put("6 ingots", new Object[]{Map.of(Items.IRON_INGOT, 6L), Map.of()});
		cases.put("5 ingots", new Object[]{Map.of(Items.IRON_INGOT, 5L), Map.of(Items.IRON_INGOT, 1)});
		cases.put("5 ingots + 1 raw iron", new Object[]{Map.of(Items.IRON_INGOT, 5L, Items.RAW_IRON, 1L), Map.of()});
		cases.put("5 raw iron", new Object[]{Map.of(Items.RAW_IRON, 5L), Map.of(Items.RAW_IRON, 1)});
		cases.put("nothing", new Object[]{Map.of(), Map.of(Items.RAW_IRON, 6)});
		for (Map.Entry<String, Object[]> e : cases.entrySet()) {
			@SuppressWarnings("unchecked") Map<Item, Long> have = (Map<Item, Long>) e.getValue()[0];
			Map<Item, Integer> lack = Crafting.lacking(level, kind, Items.IRON_BARS, 3, have);
			if (!lack.equals(e.getValue()[1])) {
				wrong.add(e.getKey() + ": lacks " + lack + ", expected " + e.getValue()[1]);
			}
		}
		// Iron ore (not raw iron) in the chest: what it names isn't settled by the spec, but never the Silk Touch deepslate ore
		for (long ore : new long[]{5, 6}) {
			Map<Item, Integer> lack = Crafting.lacking(level, kind, Items.IRON_BARS, 3, Map.of(Items.IRON_ORE, ore));
			LOG.info("[QA S1027] 3 iron bars from {} iron ore lack {}", ore, lack);
			if (lack.containsKey(Items.DEEPSLATE_IRON_ORE)) {
				wrong.add(ore + " iron ore: names Deepslate Iron Ore " + lack);
			}
		}
		LOG.info("[QA S1027] 3 iron bars from an iron block lack {}", Crafting.lacking(level, kind, Items.IRON_BARS, 3, Map.of(Items.IRON_BLOCK, 1L)));
		helper.assertTrue(wrong.isEmpty(), "what three iron bars lack: " + wrong);
		helper.succeed();
	}

	/** Five iron ingots and nothing else: "Can't be made yet: short of 1 Iron Ingot to make 3 Iron Bars", nothing taken. */
	//$ gametest_ticks_batch AREA '1400' '"qaS1027FiveIngots"'
	@GameTest(template = AREA, timeoutTicks = 1400, batch = "qaS1027FiveIngots")
	public void fiveIngotsNameTheOneIngotShort(GameTestHelper helper) {
		QaJobChainsGameTests.prepare(helper);
		QaJobChainsGameTests.Asker a = QaJobChainsGameTests.asker(helper, Blocks.IRON_BARS, new ItemStack(Items.IRON_INGOT, 5));
		Villager tinkerer = QaTinkererShortGameTests.tinkerer(helper);
		helper.runAfterDelay(1200, () -> {
			String expected = QaTinkererShortGameTests.cantMake("1", "Iron Ingot", "3", "Iron Bars");
			String text = QaTinkererShortGameTests.line(helper, a);
			Container chest = helper.getBlockEntity(a.chest());
			helper.assertTrue(chest.countItem(Items.IRON_INGOT) == 5, "the chest should still hold 5 ingots: " + chest.countItem(Items.IRON_INGOT));
			helper.assertTrue(ModAttachments.BUILDER_BAG.getOrCreate(tinkerer).isEmpty(), "the tinkerer took something");
			helper.assertTrue(expected.equals(text), "the builder's line: '" + text + "', expected '" + expected + "'");
			helper.succeed();
		});
	}

	/** Six iron ingots and no coal: nothing to fire, so no coal is wanted; the bars are made and built. */
	//$ gametest_ticks_batch AREA '4000' '"qaS1027SixIngots"'
	@GameTest(template = AREA, timeoutTicks = 4000, batch = "qaS1027SixIngots")
	public void sixIngotsAndNoCoalMakeTheBars(GameTestHelper helper) {
		QaJobChainsGameTests.prepare(helper);
		QaJobChainsGameTests.Asker a = QaJobChainsGameTests.asker(helper, Blocks.IRON_BARS, new ItemStack(Items.IRON_INGOT, 6));
		QaTinkererShortGameTests.tinkerer(helper);
		helper.succeedWhen(() -> {
			helper.assertTrue(BuildSiteManager.get(helper.getLevel()).get(a.site().id()) == null, "still building: missing " + a.site().missing()
				+ ", line " + QaTinkererShortGameTests.line(helper, a));
			helper.assertTrue(a.plan().unfinished(helper.getLevel()).isEmpty(), "bars missing from the row");
			Container chest = helper.getBlockEntity(a.chest());
			helper.assertValueEqual(chest.countItem(Items.IRON_INGOT), 0, "ingots left");
			helper.assertValueEqual(chest.countItem(Items.IRON_BARS), 13, "spare bars back");
		});
	}

	// ------------------------------------------------------------------------- CrafterWork.fuelShort rounding survivor

	/** A builder with {@code items} in the chest, building a row of {@code n} {@code block}, a Mason with a furnace by. */
	private static QaCrafterFurnitureGameTests.Asker longRow(GameTestHelper helper, Block block, int n, ItemStack... items) {
		QaCrafterFurnitureGameTests.prepare(helper);
		ServerLevel level = helper.getLevel();
		BlockPos origin = QaJobChainFixesGameTests.ORIGIN;
		for (int x = 0; x < n; x++) {
			helper.setBlock(origin.offset(x, 0, 0), block);
		}
		ResourceLocation id = ResourceLocation.fromNamespaceAndPath("aliveworkplace_test",
			"qas1027_" + BuiltInRegistries.BLOCK.getKey(block).getPath() + n + "_" + Long.toHexString(level.getGameTime()));
		level.getStructureManager().getOrCreate(id).fillFromWorld(level, helper.absolutePos(origin), new Vec3i(n, 1, 1), false, Blocks.STRUCTURE_VOID);
		for (int x = 0; x < n; x++) {
			helper.setBlock(origin.offset(x, 0, 0), Blocks.AIR);
		}
		BlockPos bench = QaJobChainFixesGameTests.BENCH;
		BlockPos chestAt = QaJobChainFixesGameTests.CHEST;
		helper.setBlock(bench, ModBlocks.BUILDERS_BENCH);
		helper.setBlock(chestAt, Blocks.CHEST);
		Container container = helper.getBlockEntity(chestAt);
		for (int i = 0; i < items.length; i++) {
			container.setItem(i, items[i].copy());
		}
		Villager builder = helper.spawn(EntityType.VILLAGER, bench.south());
		Builders.employ(level, builder, helper.absolutePos(bench));
		BuildSite site = Builders.start(level, builder, null, id, new BlueprintData.Placement(level.dimension().location(), helper.absolutePos(origin),
			Rotation.NONE, Mirror.NONE));
		BuildPlan plan = site.plan(level);
		if (plan == null) {
			throw new GameTestAssertException("blueprint not found: " + id);
		}
		QaJobChainFixesGameTests.mason(helper);
		return new QaCrafterFurnitureGameTests.Asker(builder, site, plan, chestAt);
	}

	private static void glassLine(GameTestHelper helper, int sand, String coal) {
		QaCrafterFurnitureGameTests.Asker a = longRow(helper, Blocks.GLASS, sand, new ItemStack(Items.SAND, sand));
		helper.runAfterDelay(700, () -> {
			String lacks = String.format(QaTinkererShortGameTests.lang("message.aliveworkplace.job_chain.lacks"),
				String.format(QaTinkererShortGameTests.lang("message.aliveworkplace.job_chain.lack_item"), coal, "Coal"), sand, "Glass");
			String expected = String.format(QaTinkererShortGameTests.lang("message.aliveworkplace.job_chain.cant_make"), lacks);
			Component line = JobShortages.line(helper.getLevel(), a.site().id(), a.site().missing());
			String text = line == null ? null : line.getString();
			helper.assertTrue(expected.equals(text), "the builder's line with " + sand + " sand and no fuel: '" + text + "', expected '" + expected + "'");
			Container chest = helper.getBlockEntity(a.chest());
			helper.assertTrue(chest.countItem(Items.SAND) == sand, "sand left: " + chest.countItem(Items.SAND));
			helper.succeed();
		});
	}

	/** "A coal for every eight fired": eight sand and no fuel for eight glass is "short of 1 Coal to make 8 Glass". */
	//$ gametest_ticks_batch AREA '1200' '"qaS1027GlassEight"'
	@GameTest(template = AREA, timeoutTicks = 1200, batch = "qaS1027GlassEight")
	public void eightGlassNeedOneCoal(GameTestHelper helper) {
		glassLine(helper, 8, "1");
	}

	/** One more than eight: nine sand and no fuel for nine glass is "short of 2 Coal to make 9 Glass". */
	//$ gametest_ticks_batch AREA '1200' '"qaS1027GlassNine"'
	@GameTest(template = AREA, timeoutTicks = 1200, batch = "qaS1027GlassNine")
	public void nineGlassNeedTwoCoal(GameTestHelper helper) {
		glassLine(helper, 9, "2");
	}

	/** Seven sand and one coal (a coal fires up to eight): the seven glass are made and built, the coal burnt. */
	//$ gametest_ticks_batch AREA '4000' '"qaS1027GlassSeven"'
	@GameTest(template = AREA, timeoutTicks = 4000, batch = "qaS1027GlassSeven")
	public void sevenGlassAreFiredOnOneCoal(GameTestHelper helper) {
		QaCrafterFurnitureGameTests.Asker a = longRow(helper, Blocks.GLASS, 7, new ItemStack(Items.SAND, 7), new ItemStack(Items.COAL, 1));
		helper.succeedWhen(() -> {
			helper.assertTrue(BuildSiteManager.get(helper.getLevel()).get(a.site().id()) == null, "still building: missing " + a.site().missing()
				+ ", line " + JobShortages.line(helper.getLevel(), a.site().id(), a.site().missing()));
			helper.assertTrue(a.plan().unfinished(helper.getLevel()).isEmpty(), "glass missing from the row");
			Container chest = helper.getBlockEntity(a.chest());
			helper.assertTrue(chest.countItem(Items.SAND) == 0 && chest.countItem(Items.COAL) == 0,
				"left in the chest: " + chest.countItem(Items.SAND) + " sand, " + chest.countItem(Items.COAL) + " coal");
		});
	}

	// ------------------------------------------------------------------------------- BuildSite.java:563 survivor

	/**
	 * Wiki builder.md, saved data: {@code left_out} holds "the structure and decoration blocks given up for good". A step
	 * given up (deferred, then deferred again on the retry) is left out in STRUCTURE and DECORATION only, never in CLEAR,
	 * FOUNDATION or LANDSCAPE, and the left-out structure block survives a save and load.
	 */
	//$ gametest 'BIG'
	@GameTest(template = BIG)
	public void onlyStructureAndDecorationStepsAreLeftOut(GameTestHelper helper) {
		Leftovers.clear(helper);
		ServerLevel level = helper.getLevel();
		helper.setBlock(QaScaffoldingGameTests.BENCH, ModBlocks.BUILDERS_BENCH);
		Villager villager = helper.spawn(EntityType.VILLAGER, QaScaffoldingGameTests.VILLAGER);
		Builders.employ(level, villager, helper.absolutePos(QaScaffoldingGameTests.BENCH));
		// some ground to clear and some to fill, so CLEAR and LANDSCAPE have steps
		for (int x = 0; x < 9; x++) {
			for (int z = 0; z < 9; z++) {
				helper.setBlock(new BlockPos(6 + x, 2, 6 + z), Blocks.DIRT);
			}
		}
		BuildSite site = Builders.start(level, villager, null, TEST_HUT, new BlueprintData.Placement(level.dimension().location(),
			helper.absolutePos(new BlockPos(6, 2, 6)), Rotation.NONE, Mirror.NONE));
		helper.assertTrue(site != null, "setup: the hut didn't start");
		BuildPlan plan = site.plan(level);
		// nobody works it but this test
		BuildSiteManager.get(level).remove(site.id());
		villager.discard();
		List<String> wrong = new ArrayList<>();
		List<String> seen = new ArrayList<>();
		BlockPos structureLeftOut = null;
		int nonStructureGivenUp = 0;
		while (site.stage() != BuildPlan.Stage.DONE && site.stage() != BuildPlan.Stage.DECONSTRUCT) {
			BuildPlan.Stage stage = site.stage();
			BuildPlan.Step first = site.current(plan);
			if (first == null) {
				site.finishList();
				continue;
			}
			BlockPos pos = first.pos().immutable();
			site.defer();
			while (site.current(plan) != null) {
				site.advance();
			}
			site.finishList(); // the retry
			BuildPlan.Step again = site.current(plan);
			if (again == null || !again.pos().equals(pos)) {
				wrong.add(stage + ": the retry didn't come back to the deferred step");
				break;
			}
			int skipped = site.skipped();
			site.defer(); // given up
			boolean out = site.leftOut(pos);
			boolean shouldBe = stage == BuildPlan.Stage.STRUCTURE || stage == BuildPlan.Stage.DECORATION;
			seen.add(stage + " skipped " + (site.skipped() - skipped) + " leftOut " + out);
			if (out != shouldBe) {
				wrong.add(stage + " step at " + helper.relativePos(pos) + " given up: left out " + out + ", expected " + shouldBe);
			}
			if (shouldBe && structureLeftOut == null) {
				structureLeftOut = pos;
			}
			if (!shouldBe) {
				nonStructureGivenUp++;
			}
			while (site.current(plan) != null) {
				site.advance();
			}
			site.finishList();
		}
		LOG.info("[QA S1027] left out per stage: {}", seen);
		helper.assertTrue(wrong.isEmpty(), "left out: " + wrong);
		helper.assertTrue(nonStructureGivenUp > 0, "setup: no CLEAR, FOUNDATION or LANDSCAPE step was given up (" + seen + ")");
		helper.assertTrue(structureLeftOut != null, "setup: no structure step was given up (" + seen + ")");
		BuildSite loaded = BuildSite.load(site.save());
		helper.assertTrue(loaded.leftOut(structureLeftOut), "the left-out structure block was lost in a save and load");
		helper.succeed();
	}

	// =============================================================================================================== B512

	/** The test shed: walls x 6-10, z 6-10, y 2-3, a roof at y 4, its door at x 8 on the front (z 6), as B261GameTests. */
	private static final BlockPos SHED = new BlockPos(6, 2, 6);
	private static final BlockPos SHED_BENCH = new BlockPos(8, 2, 4);
	private static final BlockPos HAY = new BlockPos(9, 2, 9);
	private static final BlockPos LADDER = new BlockPos(9, 2, 7);
	private static final BlockPos STORE = new BlockPos(7, 2, 8);
	/** A plain chest outside the shed, by the bench. */
	private static final BlockPos OUTSIDE = new BlockPos(6, 2, 3);

	private record Shed(Villager[] builder, Container trapped) {
	}

	/** The shed with a trapped chest inside holding the hay (and the ladder unless {@code ladderOutside}); its repair started. */
	private static Shed trappedShed(GameTestHelper helper, boolean ladderOutside) {
		Leftovers.clear(helper);
		Leftovers.finished(helper);
		helper.setDayTime(2000);
		boolean upkeep = Upkeep.ENABLED;
		boolean asks = Upkeep.ASK;
		boolean scaffolds = Scaffolds.ENABLED;
		Upkeep.ENABLED = true;
		Upkeep.ASK = false;
		Scaffolds.ENABLED = false;
		Leftovers.after(helper, () -> {
			Upkeep.ENABLED = upkeep;
			Upkeep.ASK = asks;
			Scaffolds.ENABLED = scaffolds;
		});
		ServerLevel level = helper.getLevel();
		for (int x = 6; x <= 10; x++) {
			for (int z = 6; z <= 10; z++) {
				boolean wall = x == 6 || x == 10 || z == 6 || z == 10;
				for (int y = 2; y <= 3; y++) {
					helper.setBlock(new BlockPos(x, y, z), wall ? Blocks.COBBLESTONE : Blocks.AIR);
				}
				helper.setBlock(new BlockPos(x, 4, z), Blocks.OAK_PLANKS);
			}
		}
		helper.setBlock(new BlockPos(8, 2, 6), Blocks.AIR);
		helper.setBlock(new BlockPos(8, 3, 6), Blocks.AIR);
		helper.setBlock(HAY, Blocks.HAY_BLOCK);
		helper.setBlock(LADDER, Blocks.LADDER.defaultBlockState().setValue(LadderBlock.FACING, Direction.WEST));
		helper.setBlock(STORE, Blocks.TRAPPED_CHEST.defaultBlockState().setValue(ChestBlock.FACING, Direction.EAST));
		ResourceLocation id = ResourceLocation.fromNamespaceAndPath("aliveworkplace_test", "qas1027_trapped_" + Long.toHexString(level.getGameTime()));
		level.getStructureManager().getOrCreate(id).fillFromWorld(level, helper.absolutePos(SHED), new Vec3i(5, 3, 5), false, Blocks.STRUCTURE_VOID);
		helper.setBlock(HAY, Blocks.AIR);
		helper.setBlock(LADDER, Blocks.AIR);
		Container trapped = helper.getBlockEntity(STORE);
		trapped.setItem(3, new ItemStack(Items.HAY_BLOCK));
		trapped.setItem(6, new ItemStack(Items.COBBLESTONE, 5)); // not needed: stays
		if (ladderOutside) {
			helper.setBlock(OUTSIDE, Blocks.CHEST);
			Container outside = helper.getBlockEntity(OUTSIDE);
			outside.setItem(0, new ItemStack(Items.LADDER));
		} else {
			trapped.setItem(4, new ItemStack(Items.LADDER));
		}
		helper.setBlock(SHED_BENCH, ModBlocks.BUILDERS_BENCH);
		BlueprintData.Placement placement = new BlueprintData.Placement(level.dimension().location(), helper.absolutePos(SHED), Rotation.NONE, Mirror.NONE);
		Villager builder = helper.spawn(EntityType.VILLAGER, new BlockPos(8, 2, 2));
		Builders.employ(level, builder, helper.absolutePos(SHED_BENCH));
		BuildSiteManager.get(level).recordFinished(id, placement, builder.getUUID());
		helper.runAfterDelay(2, () -> {
			BuildSite site = ModAttachments.BUILDER_JOB.has(builder) ? Builders.activeSite(level, builder) : Upkeep.look(level, builder);
			helper.assertTrue(site != null && site.isRepair(), "no repair started");
		});
		return new Shed(new Villager[]{builder}, trapped);
	}

	private static void assertRepaired(GameTestHelper helper, Shed s) {
		helper.assertBlockPresent(Blocks.HAY_BLOCK, HAY);
		helper.assertBlockPresent(Blocks.LADDER, LADDER);
		helper.assertFalse(ModAttachments.BUILDER_JOB.has(s.builder()[0]), "still repairing");
		helper.assertTrue(s.trapped().countItem(Items.HAY_BLOCK) == 0, "the trapped chest still holds the hay");
		helper.assertTrue(s.trapped().countItem(Items.COBBLESTONE) == 5, "the repair took cobblestone it didn't need from the trapped chest: "
			+ s.trapped().countItem(Items.COBBLESTONE) + " left of 5");
	}

	/** The trapped-chest repair with the builder saved and loaded once it has started: still finished from the trapped chest. */
	//$ gametest_ticks_batch BIG '2000' '"qaS1027TrappedReload"'
	@GameTest(template = BIG, timeoutTicks = 2000, batch = "qaS1027TrappedReload")
	public void aTrappedChestRepairSurvivesAReload(GameTestHelper helper) {
		Shed s = trappedShed(helper, false);
		helper.runAfterDelay(20, () -> s.builder()[0] = reload(helper.getLevel(), s.builder()[0]));
		helper.succeedWhen(() -> {
			helper.assertTrue(helper.getTick() > 20, "not reloaded yet");
			assertRepaired(helper, s);
			helper.assertTrue(s.trapped().countItem(Items.LADDER) == 0, "the trapped chest still holds the ladder");
		});
	}

	/** Hay in the trapped chest inside the shed, the ladder in a plain chest outside by the bench: both found, repaired. */
	//$ gametest_ticks_batch BIG '2000' '"qaS1027TrappedSplit"'
	@GameTest(template = BIG, timeoutTicks = 2000, batch = "qaS1027TrappedSplit")
	public void aRepairTakesFromATrappedChestInsideAndAChestOutside(GameTestHelper helper) {
		Shed s = trappedShed(helper, true);
		helper.succeedWhen(() -> {
			assertRepaired(helper, s);
			Container outside = helper.getBlockEntity(OUTSIDE);
			helper.assertTrue(outside.countItem(Items.LADDER) == 0, "the outside chest still holds the ladder");
		});
	}

	/**
	 * The repair asked for at night (B512: a builder rests out of work hours, intended): kept at night for 400 ticks, then
	 * morning comes, and the repair is finished from the trapped chest well within the timeout.
	 */
	//$ gametest_ticks_batch BIG '2400' '"qaS1027TrappedNight"'
	@GameTest(template = BIG, timeoutTicks = 2400, batch = "qaS1027TrappedNight")
	public void aTrappedChestRepairAskedAtNightIsDoneInTheMorning(GameTestHelper helper) {
		Shed s = trappedShed(helper, false);
		helper.onEachTick(() -> {
			if (helper.getTick() < 400) {
				helper.setDayTime(14000);
			} else if (helper.getTick() == 400) {
				helper.setDayTime(1000);
			}
		});
		helper.succeedWhen(() -> {
			helper.assertTrue(helper.getTick() > 400, "still night");
			assertRepaired(helper, s);
		});
	}
}
