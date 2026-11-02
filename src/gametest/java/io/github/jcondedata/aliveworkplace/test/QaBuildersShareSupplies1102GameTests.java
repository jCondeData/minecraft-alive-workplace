package io.github.jcondedata.aliveworkplace.test;

import io.github.jcondedata.aliveworkplace.blueprint.BlueprintData;
import io.github.jcondedata.aliveworkplace.build.BuildPlan;
import io.github.jcondedata.aliveworkplace.build.BuildSite;
import io.github.jcondedata.aliveworkplace.build.BuildSiteManager;
import io.github.jcondedata.aliveworkplace.build.Builders;
import io.github.jcondedata.aliveworkplace.build.Employer;
import io.github.jcondedata.aliveworkplace.build.MaterialRules;
import io.github.jcondedata.aliveworkplace.registry.ModAttachments;
import io.github.jcondedata.aliveworkplace.registry.ModBlocks;
import io.github.jcondedata.aliveworkplace.registry.ModGameRules;
import io.github.jcondedata.aliveworkplace.store.StorehouseBlockEntity;
import io.github.jcondedata.aliveworkplace.work.Village;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Container;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.village.poi.PoiTypes;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;

/**
 * QA (2026-11-02) of Builders competing for scarce supplies, from the spec, not the code: wiki builder.md ("Whichever
 * table": a Builder "counts and takes the village's stock"; the bag holds "never more of a thing than the stretch of work
 * ahead still needs (B132: one block's worth too many on every trip left two Builders sharing a Storehouse each waiting
 * for what the other carried)"; "If the chests run short, the Builder waits and tells you what is missing"; "A helper
 * that dies drops only its bag; the steps it claimed go back to the crew and the lead finishes the build (B516)"),
 * ROADMAP 23.1 ("no item is duplicated or lost (count them before and after)") and 23.5 (self-healing supply).
 * <p>Every test counts each of the test hut's materials on every tick: what stands in the huts plus what is in the
 * Storehouse, the tables' chests, every living builder's bag and inventory and on the ground is exactly what was stocked.
 * Not repeated here: crews from twice the list (QaBuilderCrewGameTests), crews of two and three from exactly the list
 * (QaBuilderSupply1027GameTests), the lead killed and a helper killed then reloaded with spare stock
 * (QaBuilderFixes1026GameTests), one builder and the Storehouse (QaBuilderStockGameTests, QaBuilderSupply1026bGameTests).
 */
public class QaBuildersShareSupplies1102GameTests implements net.fabricmc.fabric.api.gametest.v1.FabricGameTest {
	private static final String HUGE = "aliveworkplace_test:huge_area";
	private static final org.slf4j.Logger LOGGER = org.slf4j.LoggerFactory.getLogger("aliveworkplace_test");
	private static final ResourceLocation TEST_HUT = ResourceLocation.fromNamespaceAndPath("aliveworkplace_test", "test_hut");
	private static final UUID OWNER = UUID.fromString("5f1b2c3d-0000-4000-8000-0000000b1102");
	private static final BlockPos STOREHOUSE = new BlockPos(26, 2, 26);
	private static final BlockPos[] STORE_CHESTS = {new BlockPos(23, 2, 26), new BlockPos(26, 2, 23)};
	private static final BlockPos TABLE_A = new BlockPos(2, 2, 2);
	private static final BlockPos CHEST_A = new BlockPos(2, 2, 3);
	private static final BlockPos HUT_A = new BlockPos(6, 2, 6);
	private static final BlockPos TABLE_B = new BlockPos(2, 2, 20);
	private static final BlockPos CHEST_B = new BlockPos(2, 2, 21);
	private static final BlockPos HUT_B = new BlockPos(6, 2, 16);
	/** An idle builder's own table, near enough the lead's to help (no chest by it). */
	private static final BlockPos MATE_TABLE = new BlockPos(14, 2, 2);
	private static final List<BuildPlan.Stage> BUILT = List.of(BuildPlan.Stage.FOUNDATION, BuildPlan.Stage.STRUCTURE, BuildPlan.Stage.DECORATION);

	/** The world being tested: the builders (replaced when reloaded), their sites by id and every hut material stocked. */
	private static final class World {
		final GameTestHelper helper;
		final ServerLevel level;
		final net.minecraft.world.phys.AABB bounds;
		final List<Villager> builders = new ArrayList<>();
		final List<UUID> sites = new ArrayList<>();
		final List<BuildPlan> plans = new ArrayList<>();
		final Map<Item, Integer> stock = new LinkedHashMap<>();
		String problem;

		World(GameTestHelper helper) {
			this.helper = helper;
			this.level = helper.getLevel();
			this.bounds = helper.getBounds();
		}

		BuildSite site(int i) {
			return BuildSiteManager.get(level).get(sites.get(i));
		}

		boolean finished(int i) {
			return site(i) == null;
		}

		void stocked(Item item, int n) {
			stock.merge(item, n, Integer::sum);
		}
	}

	// --- two builders, two sites, one Storehouse ------------------------------------------------------------------------

	/**
	 * Two builders on two huts share one Storehouse that holds the oak planks for ONE hut (55); each table's chest holds
	 * the rest of its own hut. A player expects one hut to be finished and the other builder to wait, saying he needs
	 * planks, never both stuck half-built (B132: "each waiting for what the other carried, so neither finished"). Every
	 * plank is accounted for on every tick (Storehouse + chests + bags + ground + placed = 55).
	 */
	//$ gametest_ticks_batch HUGE '5000' '"qaShareOneStock"'
	@GameTest(template = HUGE, timeoutTicks = 5000, batch = "qaShareOneStock")
	public void qaTwoBuildersWithPlanksForOneHutFinishOneAndTheOtherWaits(GameTestHelper helper) {
		World w = twoBuilders(helper, 1);
		watch(helper, w);
		helper.succeedWhen(() -> {
			helper.assertTrue(w.problem == null, w.problem == null ? "" : w.problem);
			helper.assertTrue(helper.getTick() >= 2400, "waiting for the builds to settle");
			int done = (w.finished(0) ? 1 : 0) + (w.finished(1) ? 1 : 0);
			helper.assertTrue(done == 1, "with planks for one hut in the shared Storehouse, " + done + " huts are finished (wanted 1): " + where(w));
			int waiting = w.finished(0) ? 1 : 0;
			int built = 1 - waiting;
			helper.assertTrue(w.plans.get(built).unfinished(w.level).isEmpty(), "the finished hut has holes: " + where(w));
			BuildSite site = w.site(waiting);
			helper.assertTrue(site.status() == BuildSite.Status.WAITING_FOR_MATERIALS, "the other builder isn't waiting for materials: " + where(w));
			int unbuilt = unbuilt(w, waiting, Items.OAK_PLANKS);
			helper.assertTrue(unbuilt > 0, "the waiting hut has every plank: " + where(w));
			Village.forget(w.builders.get(waiting));
			String status = Builders.statusText(w.level, site, w.builders.get(waiting)).getString();
			helper.assertTrue(site.missing().getOrDefault(Items.OAK_PLANKS, 0) == unbuilt,
				"the waiting hut lacks " + unbuilt + " planks but says it misses " + site.missing() + ": " + status);
			helper.assertTrue(status.contains("Needs:") && status.contains("Oak Planks"), "the waiting builder's status doesn't say it needs oak planks: " + status);
			helper.assertTrue(count(w, Items.OAK_PLANKS, false) == 0, "planks lie unused while a hut waits for them: " + where(w));
			LOGGER.info("qa share1102 one stock: hut {} finished, hut {} waits for {} planks", built, waiting, unbuilt);
		});
	}

	/**
	 * The same, and once the Storehouse's planks are gone (one hut built, the other waiting) the player puts another 55 in
	 * the Storehouse: the waiting hut is finished from them, both huts whole, and not one plank left anywhere (110 stocked,
	 * 110 standing).
	 */
	//$ gametest_ticks_batch HUGE '6000' '"qaShareTopUp"'
	@GameTest(template = HUGE, timeoutTicks = 6000, batch = "qaShareTopUp")
	public void qaTheStorehouseToppedUpLetsTheWaitingBuilderFinish(GameTestHelper helper) {
		World w = twoBuilders(helper, 1);
		watch(helper, w);
		long[] toppedAt = {-1};
		long[] dryFrom = {-1};
		helper.onEachTick(() -> {
			if (toppedAt[0] >= 0) {
				return;
			}
			boolean dry = count(w, Items.OAK_PLANKS, true) == 0 && (w.finished(0) || w.finished(1)
				|| w.site(0).status() == BuildSite.Status.WAITING_FOR_MATERIALS && w.site(1).status() == BuildSite.Status.WAITING_FOR_MATERIALS);
			if (!dry) {
				dryFrom[0] = -1;
				return;
			}
			if (dryFrom[0] < 0) {
				dryFrom[0] = helper.getTick();
			}
			if (helper.getTick() - dryFrom[0] >= 300) {
				LOGGER.info("qa share1102 top-up at tick {}: {}", helper.getTick(), where(w));
				Container store = helper.getBlockEntity(STORE_CHESTS[0]);
				store.setItem(5, new ItemStack(Items.OAK_PLANKS, 55));
				store.setChanged();
				w.stocked(Items.OAK_PLANKS, 55);
				toppedAt[0] = helper.getTick();
			}
		});
		helper.succeedWhen(() -> {
			helper.assertTrue(w.problem == null, w.problem == null ? "" : w.problem);
			helper.assertTrue(toppedAt[0] >= 0, "the Storehouse's planks never ran out with both builders settled: " + where(w));
			helper.assertTrue(w.finished(0) && w.finished(1), "after the top-up both huts aren't finished: " + where(w));
			for (int i = 0; i < 2; i++) {
				helper.assertTrue(w.plans.get(i).unfinished(w.level).isEmpty(), "hut " + i + " has holes after the top-up: " + where(w));
			}
			helper.assertTrue(count(w, Items.OAK_PLANKS, false) == 0, "planks left over after both huts (110 stocked, 110 drawn): " + where(w));
			for (Villager v : w.builders) {
				helper.assertTrue(ModAttachments.BUILDER_BAG.getOrCreate(v).stacks().stream().allMatch(ItemStack::isEmpty), "a bag isn't empty after both huts: " + where(w));
			}
		});
	}

	/**
	 * A restart while both builders are fetching from the Storehouse (enough planks for both huts): the sites and both
	 * builders are saved and loaded. Both huts are finished whole, nothing doubled or lost on any tick, the Storehouse ends
	 * empty of planks and both bags empty.
	 */
	//$ gametest_ticks_batch HUGE '6000' '"qaShareReload"'
	@GameTest(template = HUGE, timeoutTicks = 6000, batch = "qaShareReload")
	public void qaASaveAndLoadWhileBothBuildersFetchLosesAndDoublesNothing(GameTestHelper helper) {
		World w = twoBuilders(helper, 2);
		watch(helper, w);
		String[] reloaded = {null};
		helper.onEachTick(() -> {
			if (reloaded[0] != null || w.finished(0) || w.finished(1)) {
				return;
			}
			boolean a = w.site(0).status() == BuildSite.Status.FETCHING;
			boolean b = w.site(1).status() == BuildSite.Status.FETCHING;
			if (a && b || (a || b) && helper.getTick() >= 1200) {
				reloaded[0] = "tick " + helper.getTick() + (a && b ? ", both fetching" : ", one fetching") + ": " + where(w);
				restart(helper, w);
				LOGGER.info("qa share1102 reloaded at {}", reloaded[0]);
			}
		});
		helper.succeedWhen(() -> {
			helper.assertTrue(w.problem == null, w.problem == null ? "" : w.problem);
			helper.assertTrue(reloaded[0] != null, "setup: no builder was ever fetching: " + where(w));
			helper.assertTrue(w.finished(0) && w.finished(1), "after a reload mid-fetch the huts aren't both finished (" + reloaded[0] + "); now " + where(w));
			for (int i = 0; i < 2; i++) {
				helper.assertTrue(w.plans.get(i).unfinished(w.level).isEmpty(), "hut " + i + " has holes after the reload: " + where(w));
			}
			helper.assertTrue(count(w, Items.OAK_PLANKS, false) == 0, "planks left over after both huts: " + where(w));
			for (Villager v : w.builders) {
				helper.assertTrue(ModAttachments.BUILDER_BAG.getOrCreate(v).stacks().stream().allMatch(ItemStack::isEmpty), "a bag isn't empty after both huts: " + where(w));
			}
		});
	}

	// --- a crew on one site -----------------------------------------------------------------------------------------------

	/**
	 * A lead and a helper on one hut, exactly its list stocked, but its single torch (the decoration's last block) is in
	 * the Storehouse only: the torch is put up once, never counted twice on any tick, the Storehouse ends without it and
	 * every bag ends empty.
	 */
	//$ gametest_ticks_batch HUGE '5000' '"qaShareCrewTorch"'
	@GameTest(template = HUGE, timeoutTicks = 5000, batch = "qaShareCrewTorch")
	public void qaACrewWithTheOneTorchInTheStorehousePutsItUpOnce(GameTestHelper helper) {
		World w = crew(helper, 0, Items.TORCH);
		helper.assertTrue(w.stock.getOrDefault(Items.TORCH, 0) == 1, "setup: the hut lists " + w.stock.get(Items.TORCH) + " torches, not 1");
		watch(helper, w);
		boolean[] helped = {false};
		helper.onEachTick(() -> helped[0] |= Builders.isHelping(w.builders.get(1)));
		helper.succeedWhen(() -> {
			helper.assertTrue(w.problem == null, w.problem == null ? "" : w.problem);
			helper.assertTrue(helped[0], "setup: the idle builder never helped: " + where(w));
			helper.assertTrue(w.finished(0), "the crew's hut isn't finished: " + where(w));
			helper.assertTrue(w.plans.get(0).unfinished(w.level).isEmpty(), "the crew's hut has holes: " + where(w));
			helper.assertTrue(placed(w, Items.TORCH) == 1, "the torch put up " + placed(w, Items.TORCH) + " times");
			Map<Item, Integer> loose = new LinkedHashMap<>();
			for (Item item : w.stock.keySet()) {
				int n = count(w, item, false);
				if (n != 0) {
					loose.put(item, n);
				}
			}
			helper.assertTrue(loose.isEmpty(), "left over after a hut built from exactly its list: " + loose + "; " + where(w));
		});
	}

	/**
	 * A lead and a helper with exactly the hut's list but one plank: nobody sits on planks while the lead waits. The lead
	 * waits saying he needs exactly 1 oak plank, only one plank of the hut is unbuilt, no bag holds a plank; one plank put
	 * in the chest finishes the hut with nothing left anywhere.
	 */
	//$ gametest_ticks_batch HUGE '6000' '"qaShareCrewShort"'
	@GameTest(template = HUGE, timeoutTicks = 6000, batch = "qaShareCrewShort")
	public void qaACrewOnePlankShortWaitsForExactlyOnePlank(GameTestHelper helper) {
		World w = crew(helper, 1, null);
		watch(helper, w);
		long[] waitingFrom = {-1};
		String[] checked = {null};
		helper.onEachTick(() -> {
			if (checked[0] != null || w.finished(0)) {
				return;
			}
			BuildSite site = w.site(0);
			if (site.status() != BuildSite.Status.WAITING_FOR_MATERIALS) {
				waitingFrom[0] = -1;
				return;
			}
			if (waitingFrom[0] < 0) {
				waitingFrom[0] = helper.getTick();
			}
			if (helper.getTick() - waitingFrom[0] < 300) {
				return;
			}
			checked[0] = where(w);
			int unbuilt = unbuilt(w, 0, Items.OAK_PLANKS);
			helper.assertTrue(unbuilt == 1, "one plank short, " + unbuilt + " planks of the hut are unbuilt: " + checked[0]);
			helper.assertTrue(Map.of(Items.OAK_PLANKS, 1).equals(site.missing()), "one plank short, the lead says he misses " + site.missing() + ": " + checked[0]);
			for (Villager v : w.builders) {
				helper.assertTrue(ModAttachments.BUILDER_BAG.getOrCreate(v).count(Items.OAK_PLANKS) == 0, "a bag holds planks while the lead waits for one: " + checked[0]);
			}
			Container chest = helper.getBlockEntity(CHEST_A);
			chest.setItem(26, new ItemStack(Items.OAK_PLANKS, 1));
			chest.setChanged();
			w.stocked(Items.OAK_PLANKS, 1);
			LOGGER.info("qa share1102 crew short: one plank put in at tick {}: {}", helper.getTick(), checked[0]);
		});
		helper.succeedWhen(() -> {
			helper.assertTrue(w.problem == null, w.problem == null ? "" : w.problem);
			helper.assertTrue(checked[0] != null, "the lead never waited 300 ticks for the missing plank: " + where(w));
			helper.assertTrue(w.finished(0), "the hut isn't finished after the plank was put in: " + where(w));
			helper.assertTrue(w.plans.get(0).unfinished(w.level).isEmpty(), "the hut has holes: " + where(w));
			for (Item item : w.stock.keySet()) {
				helper.assertTrue(count(w, item, false) == 0, count(w, item, false) + " " + item + " left over: " + where(w));
			}
		});
	}

	/**
	 * B516 with no spare: a lead and a helper from exactly the hut's list, the helper killed while it holds the hut's
	 * materials and a claimed step. From then on placed + chest + the lead's bag + the ground is exactly the stock on every
	 * tick (the dead helper's bag dropped once, never also kept or handed back). The lead builds on, then waits for exactly
	 * what lies on the ground; a player puts the dropped items in the chest and the lead finishes with nothing left.
	 */
	//$ gametest_ticks_batch HUGE '6000' '"qaShareCrewKilled"'
	@GameTest(template = HUGE, timeoutTicks = 6000, batch = "qaShareCrewKilled")
	public void qaAHelperKilledWithNoSpareDropsItsBagOnceAndTheLeadFinishes(GameTestHelper helper) {
		World w = crew(helper, 0, null);
		watch(helper, w);
		String[] killed = {null};
		String[] returned = {null};
		long[] waitingFrom = {-1};
		helper.onEachTick(() -> {
			if (w.finished(0)) {
				return;
			}
			Villager mate = w.builders.get(1);
			BuildSite site = w.site(0);
			if (killed[0] == null) {
				Map<Item, Integer> bag = bag(mate);
				bag.keySet().retainAll(w.stock.keySet());
				if (Builders.isHelping(mate) && !bag.isEmpty() && site.claim(mate.getUUID()) != null) {
					killed[0] = "tick " + helper.getTick() + ", helper's bag " + bag + ", " + where(w);
					mate.kill();
					helper.assertTrue(w.site(0) != null, "a helper's death ended the lead's build: " + killed[0]);
					LOGGER.info("qa share1102 killed the helper at {}", killed[0]);
				}
				return;
			}
			if (returned[0] != null) {
				return;
			}
			if (site.status() != BuildSite.Status.WAITING_FOR_MATERIALS) {
				waitingFrom[0] = -1;
				return;
			}
			if (waitingFrom[0] < 0) {
				waitingFrom[0] = helper.getTick();
			}
			if (helper.getTick() - waitingFrom[0] < 200) {
				return;
			}
			Map<Item, Integer> ground = ground(helper, w);
			returned[0] = "tick " + helper.getTick() + ", ground " + ground + ", " + where(w);
			helper.assertTrue(!ground.isEmpty(), "the lead waits but nothing of the dead helper's bag lies on the ground: " + returned[0]);
			helper.assertTrue(ground.equals(site.missing()), "the lead waits saying he misses " + site.missing() + ", but what dropped is " + ground + ": " + returned[0]);
			// the player picks it all up and puts it in the chest
			Container chest = helper.getBlockEntity(CHEST_A);
			for (ItemEntity e : helper.getLevel().getEntitiesOfClass(ItemEntity.class, helper.getBounds().inflate(12), e -> w.stock.containsKey(e.getItem().getItem()))) {
				ItemStack stack = e.getItem().copy();
				e.discard();
				for (int slot = 0; slot < chest.getContainerSize() && !stack.isEmpty(); slot++) {
					if (chest.getItem(slot).isEmpty()) {
						chest.setItem(slot, stack);
						stack = ItemStack.EMPTY;
					}
				}
				helper.assertTrue(stack.isEmpty(), "setup: no room in the chest for " + stack);
			}
			chest.setChanged();
			LOGGER.info("qa share1102 returned the dropped bag at {}", returned[0]);
		});
		helper.succeedWhen(() -> {
			helper.assertTrue(w.problem == null, w.problem == null ? "" : w.problem);
			helper.assertTrue(killed[0] != null, "setup: the helper never held materials with a claimed step: " + where(w));
			helper.assertTrue(returned[0] != null, "the lead never waited for the dropped materials (killed " + killed[0] + "); now " + where(w));
			helper.assertTrue(w.finished(0), "the hut isn't finished after the dropped bag went back in the chest: " + where(w));
			helper.assertTrue(w.plans.get(0).unfinished(w.level).isEmpty(), "the hut has holes: " + where(w));
			for (Item item : w.stock.keySet()) {
				helper.assertTrue(count(w, item, false) == 0, count(w, item, false) + " " + item + " left over: " + where(w));
			}
		});
	}

	// --- setup ----------------------------------------------------------------------------------------------------------

	/** Clean area and rules, the owner's Storehouse at STOREHOUSE with its two (empty) chests. */
	private static ServerLevel prepare(GameTestHelper helper, boolean help) {
		Leftovers.clear(helper);
		Leftovers.players(helper);
		Leftovers.halls(helper);
		Leftovers.storehouses(helper);
		Leftovers.finished(helper);
		Leftovers.village(helper, 48);
		io.github.jcondedata.aliveworkplace.hall.VillageHalls.forgetNearest();
		ServerLevel level = helper.getLevel();
		helper.setDayTime(2000);
		boolean protection = io.github.jcondedata.aliveworkplace.hall.VillageProtection.ENABLED;
		io.github.jcondedata.aliveworkplace.hall.VillageProtection.ENABLED = false;
		var rules = level.getGameRules();
		boolean wasHelp = rules.getRule(ModGameRules.BUILDERS_HELP).get();
		int delay = rules.getRule(ModGameRules.BUILD_DELAY).get();
		boolean free = rules.getRule(ModGameRules.FREE_MATERIALS).get();
		rules.getRule(ModGameRules.BUILDERS_HELP).set(help, level.getServer());
		rules.getRule(ModGameRules.BUILD_DELAY).set(2, level.getServer());
		rules.getRule(ModGameRules.FREE_MATERIALS).set(false, level.getServer());
		Leftovers.after(helper, () -> {
			io.github.jcondedata.aliveworkplace.hall.VillageProtection.ENABLED = protection;
			rules.getRule(ModGameRules.BUILDERS_HELP).set(wasHelp, level.getServer());
			rules.getRule(ModGameRules.BUILD_DELAY).set(delay, level.getServer());
			rules.getRule(ModGameRules.FREE_MATERIALS).set(free, level.getServer());
		});
		BuildSiteManager sites = BuildSiteManager.get(level);
		for (BuildSite other : new ArrayList<>(sites.all())) {
			if (other.bench() != null && other.bench().closerThan(helper.absolutePos(TABLE_A), 3 * Builders.MAX_SITE_DISTANCE)) {
				sites.remove(other.id());
			}
		}
		helper.setBlock(STOREHOUSE, ModBlocks.STOREHOUSE);
		ensurePoi(level, helper.absolutePos(STOREHOUSE));
		((StorehouseBlockEntity) helper.getBlockEntity(STOREHOUSE)).setOwner(OWNER, "Jesse");
		for (BlockPos chest : STORE_CHESTS) {
			helper.setBlock(chest, Blocks.CHEST);
		}
		return level;
	}

	/** The owner's builder at {@code table} (a Blueprint Table, with a chest at {@code chest} if not null). */
	private static Villager builderAt(GameTestHelper helper, BlockPos table, BlockPos chest) {
		ServerLevel level = helper.getLevel();
		helper.setBlock(table, ModBlocks.BLUEPRINT_TABLE);
		ensurePoi(level, helper.absolutePos(table));
		if (chest != null) {
			helper.setBlock(chest, Blocks.CHEST);
		}
		Villager villager = helper.spawn(EntityType.VILLAGER, table.east());
		Builders.employ(level, villager, helper.absolutePos(table));
		ModAttachments.BUILDER_EMPLOYER.set(villager, new Employer(OWNER, "Jesse"));
		return villager;
	}

	private static void start(GameTestHelper helper, World w, Villager villager, BlockPos hut) {
		BlueprintData.Placement placement = new BlueprintData.Placement(w.level.dimension().location(), helper.absolutePos(hut), Rotation.NONE, Mirror.NONE);
		BuildSite site = Builders.start(w.level, villager, OWNER, "Jesse", TEST_HUT, placement);
		if (site == null || site.plan(w.level) == null) {
			throw new GameTestAssertException("setup: the hut at " + hut + " didn't start");
		}
		UUID id = site.id();
		Leftovers.after(helper, () -> BuildSiteManager.get(w.level).remove(id));
		w.sites.add(id);
		w.plans.add(site.plan(w.level));
	}

	/**
	 * Two builders at their own tables, each building a test hut; each table's chest holds its hut's materials but the
	 * planks, and the Storehouse holds {@code hutsOfPlanks} huts' worth of planks.
	 */
	private static World twoBuilders(GameTestHelper helper, int hutsOfPlanks) {
		prepare(helper, false);
		World w = new World(helper);
		w.builders.add(builderAt(helper, TABLE_A, CHEST_A));
		w.builders.add(builderAt(helper, TABLE_B, CHEST_B));
		start(helper, w, w.builders.get(0), HUT_A);
		start(helper, w, w.builders.get(1), HUT_B);
		BlockPos[] chests = {CHEST_A, CHEST_B};
		int planks = 0;
		for (int i = 0; i < 2; i++) {
			Container chest = helper.getBlockEntity(chests[i]);
			int slot = 0;
			for (Map.Entry<Item, Integer> e : w.plans.get(i).materials().entrySet()) {
				if (e.getKey() == Items.OAK_PLANKS) {
					planks = e.getValue();
					continue;
				}
				chest.setItem(slot++, new ItemStack(e.getKey(), e.getValue()));
				w.stocked(e.getKey(), e.getValue());
			}
		}
		helper.assertTrue(planks == 55, "setup: the test hut lists " + planks + " oak planks, not 55");
		Container store = helper.getBlockEntity(STORE_CHESTS[0]);
		for (int k = 0; k < hutsOfPlanks; k++) {
			store.setItem(k, new ItemStack(Items.OAK_PLANKS, planks));
		}
		w.stocked(Items.OAK_PLANKS, planks * hutsOfPlanks);
		for (Villager v : w.builders) {
			Village.forget(v);
		}
		return w;
	}

	/**
	 * A lead at TABLE_A building the hut at HUT_A and an idle builder at MATE_TABLE: the chest holds exactly the hut's list
	 * less {@code planksShort} planks; {@code inStorehouse}'s whole share is in the Storehouse instead (if not null).
	 */
	private static World crew(GameTestHelper helper, int planksShort, Item inStorehouse) {
		prepare(helper, true);
		World w = new World(helper);
		w.builders.add(builderAt(helper, TABLE_A, CHEST_A));
		w.builders.add(builderAt(helper, MATE_TABLE, null));
		start(helper, w, w.builders.get(0), HUT_A);
		Container chest = helper.getBlockEntity(CHEST_A);
		Container store = helper.getBlockEntity(STORE_CHESTS[0]);
		int slot = 0;
		for (Map.Entry<Item, Integer> e : w.plans.get(0).materials().entrySet()) {
			int n = e.getValue() - (e.getKey() == Items.OAK_PLANKS ? planksShort : 0);
			(e.getKey() == inStorehouse ? store : chest).setItem(e.getKey() == inStorehouse ? 0 : slot++, new ItemStack(e.getKey(), n));
			w.stocked(e.getKey(), n);
		}
		for (Villager v : w.builders) {
			Village.forget(v);
		}
		return w;
	}

	/** Every tick: day, and every hut material accounted for exactly (the first difference is kept as the problem). */
	private static void watch(GameTestHelper helper, World w) {
		helper.onEachTick(() -> {
			helper.setDayTime(2000);
			if (w.problem != null) {
				return;
			}
			for (Map.Entry<Item, Integer> e : w.stock.entrySet()) {
				int now = count(w, e.getKey(), false) + placed(w, e.getKey());
				if (now != e.getValue()) {
					w.problem = "at tick " + helper.getTick() + " " + e.getKey() + " placed + loose is " + now + ", stocked " + e.getValue()
						+ " (" + (now > e.getValue() ? "doubled" : "lost") + "): placed " + placed(w, e.getKey()) + ", " + where(w);
					LOGGER.info("qa share1102 problem: {}", w.problem);
					return;
				}
			}
		});
	}

	/** The sites and every living builder saved and loaded again, as a server restart. */
	private static void restart(GameTestHelper helper, World w) {
		var registries = w.level.registryAccess();
		w.level.getDataStorage().set(BuildSiteManager.NAME,
			BuildSiteManager.load(BuildSiteManager.get(w.level).save(new CompoundTag(), registries), registries));
		for (int j = 0; j < w.builders.size(); j++) {
			Villager v = w.builders.get(j);
			if (!v.isAlive()) {
				continue;
			}
			CompoundTag tag = new CompoundTag();
			helper.assertTrue(v.save(tag), "a builder wasn't saved");
			v.discard();
			Entity loaded = EntityType.loadEntityRecursive(tag, w.level, e -> e);
			helper.assertTrue(loaded instanceof Villager, "a builder didn't load as a villager: " + loaded);
			w.level.addFreshEntity(loaded);
			w.builders.set(j, (Villager) loaded);
		}
	}

	// --- counting -------------------------------------------------------------------------------------------------------

	private static void ensurePoi(ServerLevel level, BlockPos pos) {
		if (level.getPoiManager().getType(pos).isEmpty()) {
			PoiTypes.forState(level.getBlockState(pos)).ifPresent(type -> level.getPoiManager().add(pos.immutable(), type));
		}
	}

	/** {@code item} standing in the huts (each built step's requirements). */
	private static int placed(World w, Item item) {
		int n = 0;
		for (BuildPlan plan : w.plans) {
			for (BuildPlan.Stage stage : BUILT) {
				for (BuildPlan.Step step : plan.steps(stage)) {
					if (MaterialRules.matches(w.level.getBlockState(step.pos()), step.state())) {
						for (MaterialRules.Requirement r : step.requirements()) {
							n += r.item() == item ? r.count() : 0;
						}
					}
				}
			}
		}
		return n;
	}

	/** Steps of hut {@code i} drawn needing {@code item} that aren't built yet. */
	private static int unbuilt(World w, int i, Item item) {
		int n = 0;
		for (BuildPlan.Stage stage : BUILT) {
			for (BuildPlan.Step step : w.plans.get(i).steps(stage)) {
				if (!MaterialRules.matches(w.level.getBlockState(step.pos()), step.state())) {
					for (MaterialRules.Requirement r : step.requirements()) {
						n += r.item() == item ? r.count() : 0;
					}
				}
			}
		}
		return n;
	}

	/**
	 * {@code item} loose: in the Storehouse's and tables' chests, every living builder's bag and inventory and on the
	 * ground (or, with {@code storeOnly}, the Storehouse's chests only).
	 */
	private static int count(World w, Item item, boolean storeOnly) {
		int n = 0;
		for (BlockPos chest : STORE_CHESTS) {
			n += containerAt(w, chest).countItem(item);
		}
		if (storeOnly) {
			return n;
		}
		for (BlockPos chest : new BlockPos[] {CHEST_A, CHEST_B}) {
			Container c = containerAtOrNull(w, chest);
			n += c == null ? 0 : c.countItem(item);
		}
		for (Villager v : w.builders) {
			if (v.isAlive()) {
				n += ModAttachments.BUILDER_BAG.getOrCreate(v).count(item) + v.getInventory().countItem(item);
			}
		}
		for (ItemEntity e : w.level.getEntitiesOfClass(ItemEntity.class, w.bounds.inflate(12), e -> e.getItem().is(item))) {
			n += e.getItem().getCount();
		}
		return n;
	}

	private static Map<Item, Integer> ground(GameTestHelper helper, World w) {
		Map<Item, Integer> out = new LinkedHashMap<>();
		for (ItemEntity e : helper.getLevel().getEntitiesOfClass(ItemEntity.class, helper.getBounds().inflate(12), e -> w.stock.containsKey(e.getItem().getItem()))) {
			out.merge(e.getItem().getItem(), e.getItem().getCount(), Integer::sum);
		}
		return out;
	}

	private static Container containerAt(World w, BlockPos relative) {
		Container c = containerAtOrNull(w, relative);
		if (c == null) {
			throw new GameTestAssertException("no container at " + relative);
		}
		return c;
	}

	private static Container containerAtOrNull(World w, BlockPos relative) {
		return w.level.getBlockEntity(w.helper.absolutePos(relative)) instanceof Container c ? c : null;
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

	private static String where(World w) {
		StringBuilder s = new StringBuilder();
		for (int i = 0; i < w.sites.size(); i++) {
			BuildSite site = w.site(i);
			s.append("hut ").append(i).append(site == null ? " finished" : ": stage " + site.stage() + " status " + site.status() + " placed " + site.placed()
				+ " missing " + site.missing()).append(", unbuilt planks ").append(unbuilt(w, i, Items.OAK_PLANKS)).append("; ");
		}
		s.append("storehouse planks ").append(count(w, Items.OAK_PLANKS, true)).append("; bags:");
		for (Villager v : w.builders) {
			s.append(' ').append(bag(v)).append(Builders.isHelping(v) ? " (helping)" : "").append(v.isAlive() ? "" : " (dead)");
		}
		return s.toString();
	}
}
