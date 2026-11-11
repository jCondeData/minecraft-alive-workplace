package io.github.jcondedata.aliveworkplace.test;

import io.github.jcondedata.aliveworkplace.blueprint.BlueprintData;
import io.github.jcondedata.aliveworkplace.blueprint.BlueprintLibrary;
import io.github.jcondedata.aliveworkplace.blueprint.StarterBlueprints;
import io.github.jcondedata.aliveworkplace.build.BuildPlan;
import io.github.jcondedata.aliveworkplace.build.BuildSite;
import io.github.jcondedata.aliveworkplace.build.BuildSiteManager;
import io.github.jcondedata.aliveworkplace.build.Builders;
import io.github.jcondedata.aliveworkplace.registry.ModAttachments;
import io.github.jcondedata.aliveworkplace.registry.ModBlocks;
import io.github.jcondedata.aliveworkplace.registry.ModGameRules;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Container;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.entity.BaseContainerBlockEntity;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.phys.AABB;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * QA 2026-11-10 (lane b), B181's fix ("Keep a builder crew busy to the end of each stage", BuilderWork): a helper keeps
 * its claimed block wherever the lead's cursor is, helpers pick from the steps ahead that still need work, and a lead
 * short of an item a helper close by carries takes it from that helper. Written from the spec (ROADMAP B181 and the
 * commit message), on the stone house with a real crew of two (a lead and one employed helper, BuilderCrewGameTests's
 * yard), adversarially: the only stock of one material in the helper's bag, the helper killed while it holds a claim,
 * the helper's blocks broken or a wrong block put on its claimed spot, and a server restart mid-structure. Every test
 * ends with the house matching its drawing; the exact-list tests also prove nothing is duplicated or lost.
 */
public class QaCrewBusy1110bGameTests implements net.fabricmc.fabric.api.gametest.v1.FabricGameTest {
	private static final Logger LOGGER = LoggerFactory.getLogger("aliveworkplace-qa");
	private static final String HUGE = "aliveworkplace_test:huge_area";
	private static final BlockPos BENCH = new BlockPos(2, 2, 2);
	private static final BlockPos CHEST = new BlockPos(2, 2, 4);
	private static final BlockPos ORIGIN = new BlockPos(12, 2, 12);
	private static final BlockPos LEAD_START = new BlockPos(3, 2, 3);
	private static final BlockPos HELPER_BENCH = new BlockPos(7, 2, 2);
	private static final BlockPos[] BARRELS = {new BlockPos(1, 2, 4), new BlockPos(3, 2, 4), new BlockPos(4, 2, 4),
		new BlockPos(5, 2, 4), new BlockPos(1, 2, 5), new BlockPos(3, 2, 5), new BlockPos(4, 2, 5), new BlockPos(5, 2, 5)};

	/** The lead, the helper (both replaced on a reload) and the site's id (the live site is looked up each time). */
	private static final class Crew {
		final ServerLevel level;
		final UUID siteId;
		final Map<Item, Integer> materials;
		Villager lead;
		Villager mate;
		/** Claims a helper dropped while they still needed work and the lead's cursor had reached or passed them. */
		int drops;
		final List<String> dropped = new ArrayList<>();
		BlockPos lastClaim;

		Crew(ServerLevel level, UUID siteId, Map<Item, Integer> materials, Villager lead, Villager mate) {
			this.level = level;
			this.siteId = siteId;
			this.materials = materials;
			this.lead = lead;
			this.mate = mate;
		}

		/** The site as last seen: a finished build's site leaves the manager, so the last one seen is kept. */
		BuildSite last;

		BuildSite site() {
			BuildSite live = BuildSiteManager.get(level).get(siteId);
			if (live != null) {
				last = live;
			}
			return last;
		}

		String where() {
			BuildSite s = site();
			if (s == null) {
				return "site gone";
			}
			BuildPlan plan = s.plan(level);
			return "stage " + s.stage() + ", status " + s.status() + ", placed " + s.placed() + ", skipped " + s.skipped()
				+ ", unfinished " + (plan == null ? "?" : plan.unfinished(level).size());
		}
	}

	/**
	 * A lead at its bench and one employed helper at its own, the stone house started at the normal yard, and
	 * {@code times} the house's material list in the barrels.
	 */
	private static Crew setUp(GameTestHelper helper, int times) {
		Leftovers.clear(helper);
		Leftovers.halls(helper);
		ServerLevel level = helper.getLevel();
		int delay = level.getGameRules().getInt(ModGameRules.BUILD_DELAY);
		boolean help = level.getGameRules().getBoolean(ModGameRules.BUILDERS_HELP);
		boolean free = level.getGameRules().getBoolean(ModGameRules.FREE_MATERIALS);
		Leftovers.after(helper, () -> {
			level.getGameRules().getRule(ModGameRules.BUILD_DELAY).set(delay, level.getServer());
			level.getGameRules().getRule(ModGameRules.BUILDERS_HELP).set(help, level.getServer());
			level.getGameRules().getRule(ModGameRules.FREE_MATERIALS).set(free, level.getServer());
		});
		helper.setDayTime(3000);
		level.getGameRules().getRule(ModGameRules.BUILD_DELAY).set(2, level.getServer());
		level.getGameRules().getRule(ModGameRules.BUILDERS_HELP).set(true, level.getServer());
		level.getGameRules().getRule(ModGameRules.FREE_MATERIALS).set(false, level.getServer());
		helper.setBlock(BENCH, ModBlocks.BUILDERS_BENCH);
		helper.setBlock(CHEST, Blocks.CHEST);
		for (BlockPos barrel : BARRELS) {
			helper.setBlock(barrel, Blocks.BARREL);
		}
		Villager lead = helper.spawn(EntityType.VILLAGER, LEAD_START);
		Builders.employ(level, lead, helper.absolutePos(BENCH));
		helper.setBlock(HELPER_BENCH, ModBlocks.BUILDERS_BENCH);
		Villager mate = helper.spawn(EntityType.VILLAGER, HELPER_BENCH.offset(1, 0, 1));
		Builders.employ(level, mate, helper.absolutePos(HELPER_BENCH));

		BlueprintData.Placement placement = new BlueprintData.Placement(level.dimension().location(), helper.absolutePos(ORIGIN),
			Rotation.NONE, Mirror.NONE);
		BuildPlan bare = BuildPlan.create(BlueprintLibrary.get(level.getServer(), StarterBlueprints.STONE_HOUSE.id()).orElseThrow(), placement);
		BoundingBox box = bare.bounds();
		for (BlockPos pos : BlockPos.betweenClosed(box.minX(), box.minY(), box.minZ(), box.maxX(), box.maxY(), box.maxZ())) {
			level.setBlock(pos, Blocks.AIR.defaultBlockState(), 2);
		}
		BuildSite site = Builders.start(level, lead, null, StarterBlueprints.STONE_HOUSE.id(), placement);
		if (site == null) {
			throw new GameTestAssertException("the stone house didn't start");
		}
		BuildPlan plan = site.plan(level);
		if (plan == null) {
			throw new GameTestAssertException("no plan for the stone house");
		}
		Map<Item, Integer> materials = new HashMap<>();
		plan.materials().forEach((item, n) -> materials.put(item, n * times));
		stock(helper, materials);
		return new Crew(level, site.id(), plan.materials(), lead, mate);
	}

	private static void stock(GameTestHelper helper, Map<Item, Integer> materials) {
		List<ItemStack> stock = new ArrayList<>();
		for (Map.Entry<Item, Integer> e : materials.entrySet()) {
			int left = e.getValue();
			while (left > 0) {
				int n = Math.min(left, e.getKey().getDefaultMaxStackSize());
				stock.add(new ItemStack(e.getKey(), n));
				left -= n;
			}
		}
		helper.assertTrue(stock.size() <= BARRELS.length * 27, "test needs more barrels: " + stock.size() + " stacks");
		for (int i = 0; i < BARRELS.length; i++) {
			BaseContainerBlockEntity barrel = helper.getBlockEntity(BARRELS[i]);
			barrel.clearContent();
			for (int slot = 0; slot < barrel.getContainerSize() && i * 27 + slot < stock.size(); slot++) {
				barrel.setItem(slot, stock.get(i * 27 + slot));
			}
		}
	}

	private static List<Container> stores(GameTestHelper helper) {
		List<Container> stores = new ArrayList<>();
		stores.add(helper.getBlockEntity(CHEST));
		for (BlockPos barrel : BARRELS) {
			stores.add(helper.getBlockEntity(barrel));
		}
		return stores;
	}

	private static int inStores(GameTestHelper helper, Item item) {
		int n = 0;
		for (Container c : stores(helper)) {
			n += c.countItem(item);
		}
		return n;
	}

	/** Takes every one of {@code item} out of the chest and barrels; returns how many. */
	private static int takeFromStores(GameTestHelper helper, Item item) {
		int n = 0;
		for (Container c : stores(helper)) {
			for (int slot = 0; slot < c.getContainerSize(); slot++) {
				if (c.getItem(slot).is(item)) {
					n += c.getItem(slot).getCount();
					c.setItem(slot, ItemStack.EMPTY);
				}
			}
		}
		return n;
	}

	private static int onGround(GameTestHelper helper, Item item) {
		AABB area = new AABB(helper.absolutePos(BlockPos.ZERO)).expandTowards(30, 30, 30).inflate(8);
		return helper.getLevel().getEntitiesOfClass(ItemEntity.class, area, e -> e.isAlive() && e.getItem().is(item)).stream()
			.mapToInt(e -> e.getItem().getCount()).sum();
	}

	/** Plan materials left anywhere (stores, bags, pockets, the ground) once the build is done. */
	private static Map<Item, Integer> leftOver(GameTestHelper helper, Crew c) {
		Map<Item, Integer> left = new HashMap<>();
		for (Item item : c.materials.keySet()) {
			int n = inStores(helper, item) + onGround(helper, item);
			for (Villager v : List.of(c.lead, c.mate)) {
				if (v.isAlive()) {
					n += ModAttachments.BUILDER_BAG.getOrCreate(v).count(item) + v.getInventory().countItem(item);
				}
			}
			if (n > 0) {
				left.put(item, n);
			}
		}
		return left;
	}

	/**
	 * Tracks the helper's claim each tick: a claim it had while the block still needed work and the lead's cursor had
	 * reached or passed it, replaced by another claim while that block still needs work, is a drop (B181 1).
	 */
	private static void watchClaims(Crew c) {
		BuildSite site = c.site();
		if (site == null || !c.mate.isAlive()) {
			c.lastClaim = null;
			return;
		}
		BlockPos now = site.claim(c.mate.getUUID());
		BlockPos was = c.lastClaim;
		c.lastClaim = now == null ? null : now.immutable();
		if (was == null || now == null || was.equals(now) || site.stage() != BuildPlan.Stage.STRUCTURE) {
			return;
		}
		BuildPlan plan = site.plan(c.level);
		if (plan == null) {
			return;
		}
		List<BuildPlan.Step> list = plan.steps(site.stage());
		int at = -1;
		BuildPlan.Step step = null;
		for (int i = 0; i < list.size(); i++) {
			if (list.get(i).pos().equals(was)) {
				at = i;
				step = list.get(i);
				break;
			}
		}
		BuildPlan.Step lead = site.current(plan);
		int cursor = lead == null ? list.size() : list.indexOf(lead);
		if (step != null && at <= cursor
			&& !io.github.jcondedata.aliveworkplace.build.MaterialRules.matches(c.level.getBlockState(was), step.state())) {
			c.drops++;
			if (c.dropped.size() < 5) {
				c.dropped.add(was.toShortString() + " (step " + at + ", cursor " + cursor + ") for " + now.toShortString());
			}
		}
	}

	private static void assertBuilt(GameTestHelper helper, Crew c) {
		BuildSite site = c.site();
		helper.assertTrue(site != null && site.isDone(), "still building: " + c.where());
		BuildPlan plan = site.plan(c.level);
		helper.assertTrue(plan != null && plan.unfinished(c.level).isEmpty(), "the stone house isn't finished: " + c.where()
			+ (plan == null ? "" : " first left " + plan.unfinished(c.level).stream().limit(5).map(p -> p.toShortString()
			+ "=" + c.level.getBlockState(p).getBlock()).toList()));
	}

	/**
	 * B181 3 and conservation: every one of the material of the structure's last step (the roof, where the lead used to
	 * walk down to the chests for its last slab) is in the helper's bag, none in the chest or barrels; the rest is
	 * exactly the material list. The lead, needing it for its own steps, gets it from the helper (if the helper lays
	 * every one itself before the lead's cursor comes to one, the lead never needs it: B1199), and the house is
	 * finished from exactly the list: nothing of any material is left anywhere (so nothing was made from nothing) and
	 * none is missing (else the house couldn't be finished), with no step skipped.
	 */
	//$ gametest_ticks_batch HUGE '12000' '"qa1110bCrewOnlyHelperHas"'
	@GameTest(template = HUGE, timeoutTicks = 12000, batch = "qa1110bCrewOnlyHelperHas")
	public void aMaterialOnlyTheHelperCarriesIsUsedExactlyOnce(GameTestHelper helper) {
		Crew c = setUp(helper, 1);
		List<BuildPlan.Step> structure = c.site().plan(c.level).steps(BuildPlan.Stage.STRUCTURE);
		Item roof = structure.get(structure.size() - 1).state().getBlock().asItem();
		int count = takeFromStores(helper, roof);
		helper.assertTrue(count == c.materials.get(roof) && count > 0, "setup: took " + count + " " + roof + " of " + c.materials.get(roof));
		int misfit = ModAttachments.BUILDER_BAG.getOrCreate(c.mate).addAll(roof, count);
		helper.assertTrue(misfit == 0, "setup: " + misfit + " " + roof + " didn't fit the helper's bag");
		int[] leadGot = {-1, -1};
		// B1199: what became of the helper's stock: laid by the helper itself before the lead had any, or passed to the
		// lead; and the roof steps the lead's cursor came to unbuilt with the helper not on them (the lead needed one).
		int[] trace = {count, 0, 0, 0}; // the helper's count last tick, laid by the helper, such steps, ticks on them
		BlockPos[] leadOn = {null};
		helper.onEachTick(() -> {
			helper.setDayTime(3000);
			watchClaims(c);
			if (leadGot[0] < 0 && ModAttachments.BUILDER_BAG.getOrCreate(c.lead).count(roof) + c.lead.getInventory().countItem(roof) > 0) {
				leadGot[0] = (int) helper.getTick();
				leadGot[1] = (int) Math.round(c.lead.distanceTo(c.mate));
			}
			int mateHas = c.mate.isAlive() ? ModAttachments.BUILDER_BAG.getOrCreate(c.mate).count(roof) + c.mate.getInventory().countItem(roof) : 0;
			if (mateHas < trace[0] && leadGot[0] < 0) {
				trace[1] += trace[0] - mateHas;
			}
			trace[0] = mateHas;
			BuildSite site = c.site();
			BuildPlan plan = site == null ? null : site.plan(c.level);
			BuildPlan.Step at = plan == null || site.stage() != BuildPlan.Stage.STRUCTURE ? null : site.current(plan);
			if (at != null && at.state().getBlock().asItem() == roof && !at.pos().equals(site.claim(c.mate.getUUID()))
				&& !io.github.jcondedata.aliveworkplace.build.MaterialRules.matches(c.level.getBlockState(at.pos()), at.state())) {
				if (!at.pos().equals(leadOn[0])) {
					trace[2]++;
					leadOn[0] = at.pos().immutable();
				}
				trace[3]++;
			}
		});
		helper.succeedWhen(() -> {
			assertBuilt(helper, c);
			Map<Item, Integer> left = leftOver(helper, c);
			helper.assertTrue(left.isEmpty(), "materials left over after building from exactly the list (made from nothing): " + left);
			helper.assertTrue(c.site().skipped() == 0, "steps were skipped: " + c.where());
			String roofTrace = "; the helper laid " + trace[1] + " of " + count + " itself, the lead's cursor came to " + trace[2]
				+ " unbuilt " + roof + " steps the helper wasn't on (" + trace[3] + " ticks)";
			// B1199: the lead needs it only when its cursor comes to such a step before the helper has laid them all; a
			// helper a little ahead of a slower lead lays every one itself (CI: 2 runs of many), the lead then never has any
			helper.assertTrue(trace[2] == 0 || leadGot[0] >= 0, "the lead needed " + roof + " but never got any from the helper: " + c.where() + roofTrace);
			helper.assertTrue(leadGot[0] >= 0 || trace[1] == count, "the lead never had any " + roof + ", which only the helper carried, and the helper "
				+ "didn't lay them all: " + c.where() + roofTrace);
			helper.assertTrue(c.drops == 0, "the helper dropped " + c.drops + " claims still to build once the lead's cursor reached them: " + c.dropped);
			LOGGER.info("qa 1110b crew only helper has: {} x{}, the lead first had it at tick {}, {} blocks from the helper{}; placed lead {} helper {}; {}",
				roof, count, leadGot[0], leadGot[1], roofTrace, c.site().placedBy(c.lead.getUUID(), false), c.site().placedBy(c.mate.getUUID(), false), c.where());
		});
	}

	/**
	 * B181 2: the helper doesn't stand about while the structure still has open steps ahead of the lead. Every tick of
	 * the structure with at least {@link #OPEN_AHEAD} steps after the lead's cursor still to build, the helper should
	 * hold a claim; the longest stretch without one (a fetch trip from the chests included) must stay under
	 * {@link #MOST_IDLE} ticks and such ticks must be under half, and the helper must lay at least 40% of the
	 * blocks (a crew of two takes about half the time of one builder; before B181's fix the helper laid 36%).
	 */
	//$ gametest_ticks_batch HUGE '12000' '"qa1110bCrewHelperBusy"'
	@GameTest(template = HUGE, timeoutTicks = 12000, batch = "qa1110bCrewHelperBusy")
	public void theHelperHoldsAClaimWhileOpenStepsRemainAhead(GameTestHelper helper) {
		Crew c = setUp(helper, 1);
		int[] idle = {0, 0, 0, 0}; // current stretch, longest, ticks with open steps, of them without a claim
		helper.onEachTick(() -> {
			helper.setDayTime(3000);
			watchClaims(c);
			BuildSite site = c.site();
			BuildPlan plan = site.plan(c.level);
			if (site.stage() != BuildPlan.Stage.STRUCTURE || plan == null || site.isRetrying()) {
				idle[0] = 0;
				return;
			}
			List<BuildPlan.Step> list = plan.steps(BuildPlan.Stage.STRUCTURE);
			BuildPlan.Step at = site.current(plan);
			int open = 0;
			for (int i = at == null ? list.size() : list.indexOf(at) + 1; i < list.size() && open < OPEN_AHEAD; i++) {
				if (!io.github.jcondedata.aliveworkplace.build.MaterialRules.matches(c.level.getBlockState(list.get(i).pos()), list.get(i).state())) {
					open++;
				}
			}
			if (open < OPEN_AHEAD) {
				idle[0] = 0;
				return;
			}
			idle[2]++;
			if (site.claim(c.mate.getUUID()) == null) {
				idle[0]++;
				idle[3]++;
				idle[1] = Math.max(idle[1], idle[0]);
			} else {
				idle[0] = 0;
			}
		});
		helper.succeedWhen(() -> {
			assertBuilt(helper, c);
			int mine = c.site().placedBy(c.mate.getUUID(), false);
			String line = "the helper went " + idle[1] + " ticks at most without a claim, " + idle[3] + " of " + idle[2]
				+ " structure ticks with open steps ahead; it placed " + mine + " of " + c.site().placed() + " blocks";
			helper.assertTrue(c.drops == 0, "the helper dropped " + c.drops + " claims still to build once the lead's cursor reached them: " + c.dropped);
			helper.assertTrue(idle[1] < MOST_IDLE, line);
			helper.assertTrue(idle[3] * 2 < idle[2], line); // 2026-11-10: 216 of 732 (30%), the walks between blocks
			helper.assertTrue(mine * 10 >= c.site().placed() * 4, line); // 2026-11-10: 210 of 454 (46%); the spec: two take half the time
			LOGGER.info("qa 1110b crew helper busy: {}; {}", line, c.where());
		});
	}

	/**
	 * B181 3: the lead, short of the roof's slabs while the helper working beside it carries some, has them passed over
	 * rather than walking to the chests for them alone. The stores start with the list less its slabs and the helper
	 * carries the slabs; once the structure starts (the lead stocked up for it) as many slabs again go into the chest. A
	 * trip to the stores from which the lead comes back with slabs and nothing else, started (its last block laid)
	 * with the helper within passing distance (8 blocks) holding slabs, is the walk the fix removed. At the end the
	 * spare slabs are all still somewhere (nothing made or lost) and the house is finished with no step skipped.
	 */
	//$ gametest_ticks_batch HUGE '12000' '"qa1110bCrewLeadTakesFromHelper"'
	@GameTest(template = HUGE, timeoutTicks = 12000, batch = "qa1110bCrewLeadTakesFromHelper")
	public void theLeadTakesTheRoofSlabsFromTheHelperNotTheChests(GameTestHelper helper) {
		Crew c = setUp(helper, 1);
		List<BuildPlan.Step> structure = c.site().plan(c.level).steps(BuildPlan.Stage.STRUCTURE);
		Item roof = structure.get(structure.size() - 1).state().getBlock().asItem();
		int count = takeFromStores(helper, roof);
		int misfit = ModAttachments.BUILDER_BAG.getOrCreate(c.mate).addAll(roof, count);
		helper.assertTrue(misfit == 0 && count == c.materials.get(roof), "setup: " + count + " " + roof + " moved, misfit " + misfit);
		Map<Item, Integer> had = new HashMap<>();
		c.materials.keySet().forEach(i -> had.put(i, carried(c.lead, i)));
		// [placed by the lead, ticks, helper's distance at the lead's last block, helper's slabs then, injected, slab-only trips]
		double[] last = {0, -1, 99, 0};
		boolean[] injected = {false};
		int[] trips = {0};
		String[] off = {null};
		helper.onEachTick(() -> {
			helper.setDayTime(3000);
			BuildSite site = c.site();
			if (!injected[0] && site.stage() == BuildPlan.Stage.STRUCTURE) {
				injected[0] = true;
				Container chest = helper.getBlockEntity(CHEST);
				for (int slot = 0; slot < chest.getContainerSize(); slot++) {
					if (chest.getItem(slot).isEmpty()) {
						chest.setItem(slot, new ItemStack(roof, count));
						break;
					}
				}
			}
			int placed = site.placedBy(c.lead.getUUID(), false);
			if (placed != (int) last[0]) {
				last[0] = placed;
				last[1] = helper.getTick();
				last[2] = c.lead.distanceTo(c.mate);
				last[3] = c.mate.isAlive() ? ModAttachments.BUILDER_BAG.getOrCreate(c.mate).count(roof) : 0;
			}
			List<Item> gained = new ArrayList<>();
			for (Item i : c.materials.keySet()) {
				int now = carried(c.lead, i);
				if (now > had.get(i)) {
					gained.add(i);
				}
				had.put(i, now);
			}
			boolean atStores = c.lead.position().distanceTo(net.minecraft.world.phys.Vec3.atCenterOf(helper.absolutePos(CHEST))) < 6;
			if (injected[0] && atStores && gained.equals(List.of(roof))) {
				trips[0]++;
				if (off[0] == null && last[2] <= 8 && last[3] > 0) {
					off[0] = "at tick " + helper.getTick() + " the lead fetched only " + roof + " from the chests; at its last block (tick "
						+ (int) last[1] + ") the helper was " + Math.round(last[2]) + " blocks away with " + (int) last[3] + " of them";
				}
			}
		});
		helper.succeedWhen(() -> {
			assertBuilt(helper, c);
			helper.assertTrue(injected[0], "setup: the slabs never went into the chest");
			helper.assertTrue(off[0] == null, off[0] == null ? "" : off[0]);
			int left = inStores(helper, roof) + onGround(helper, roof) + carried(c.lead, roof) + (c.mate.isAlive() ? carried(c.mate, roof) : 0);
			helper.assertTrue(left == count, "wanted the " + count + " spare " + roof + " left, found " + left);
			helper.assertTrue(c.site().skipped() == 0, "steps were skipped: " + c.where());
			LOGGER.info("qa 1110b crew lead takes from helper: {} slab-only trips (none with the helper close), {} x{} spare left, stores {}; {}",
				trips[0], roof, left, inStores(helper, roof), c.where());
		});
	}

	private static int carried(Villager v, Item item) {
		return ModAttachments.BUILDER_BAG.getOrCreate(v).count(item) + v.getInventory().countItem(item);
	}

	/** Open steps ahead of the lead that count as work waiting for the helper. */
	private static final int OPEN_AHEAD = 8;
	/** The longest the helper may go without a claim while that much work waits (2026-11-10: 35 every run, a fetch trip included). */
	private static final int MOST_IDLE = 100;

	/**
	 * B181 1: the helper is killed mid-structure while it holds a claim (the claim it would now keep wherever the
	 * lead's cursor is). The lead alone finishes the house, its claimed block included, without waiting forever on a
	 * dead helper's claim. Twice the material list, as what the helper carried is lost with it.
	 */
	//$ gametest_ticks_batch HUGE '12000' '"qa1110bCrewHelperKilled"'
	@GameTest(template = HUGE, timeoutTicks = 12000, batch = "qa1110bCrewHelperKilled")
	public void theLeadFinishesWhenTheHelperDiesHoldingAClaim(GameTestHelper helper) {
		Crew c = setUp(helper, 2);
		BlockPos[] claimed = {null};
		helper.onEachTick(() -> {
			helper.setDayTime(3000);
			BuildSite site = c.site();
			if (claimed[0] == null && site != null && site.stage() == BuildPlan.Stage.STRUCTURE && site.placed() > 60
				&& site.claim(c.mate.getUUID()) != null) {
				claimed[0] = site.claim(c.mate.getUUID()).immutable();
				c.mate.kill();
			}
		});
		helper.succeedWhen(() -> {
			helper.assertTrue(claimed[0] != null, "setup: the helper never had a block claimed in the structure: " + c.where());
			assertBuilt(helper, c);
			LOGGER.info("qa 1110b crew helper killed holding {}: {}", claimed[0], c.where());
		});
	}

	/**
	 * B181 1 against a player: five times in the structure, the block the helper has just laid on its claim is broken
	 * again (set to air), and five times a dirt block is put on the spot the helper has just claimed. The crew ends with
	 * every block of the house matching its drawing. Twice the material list (the broken blocks are gone).
	 */
	//$ gametest_ticks_batch HUGE '12000' '"qa1110bCrewClaimsMessedWith"'
	@GameTest(template = HUGE, timeoutTicks = 12000, batch = "qa1110bCrewClaimsMessedWith")
	public void theHouseMatchesItsDrawingWhenAPlayerMessesWithTheHelpersClaims(GameTestHelper helper) {
		Crew c = setUp(helper, 2);
		int[] broken = {0};
		int[] dirt = {0};
		BlockPos[] prev = {null};
		List<BlockPos> touched = new ArrayList<>();
		helper.onEachTick(() -> {
			helper.setDayTime(3000);
			BuildSite site = c.site();
			if (site == null || site.stage() != BuildPlan.Stage.STRUCTURE) {
				prev[0] = null;
				return;
			}
			BuildPlan plan = site.plan(c.level);
			BlockPos now = site.claim(c.mate.getUUID());
			BlockPos was = prev[0];
			prev[0] = now == null ? null : now.immutable();
			if (plan == null || site.placed() < 40) {
				return;
			}
			// a claim just laid (it matches its drawing now): break it once
			if (was != null && broken[0] < 5 && !touched.contains(was)) {
				for (BuildPlan.Step s : plan.steps(BuildPlan.Stage.STRUCTURE)) {
					if (s.pos().equals(was) && io.github.jcondedata.aliveworkplace.build.MaterialRules.matches(c.level.getBlockState(was), s.state())) {
						c.level.setBlock(was, Blocks.AIR.defaultBlockState(), 3);
						touched.add(was);
						broken[0]++;
						break;
					}
				}
			}
			// a new claim on an empty spot: a wrong block on it
			if (now != null && !now.equals(was) && dirt[0] < 5 && !touched.contains(now) && c.level.getBlockState(now).isAir()) {
				c.level.setBlock(now, Blocks.DIRT.defaultBlockState(), 3);
				touched.add(now.immutable());
				dirt[0]++;
			}
		});
		helper.succeedWhen(() -> {
			helper.assertTrue(broken[0] == 5 && dirt[0] == 5, "setup: broke " + broken[0] + " and dirtied " + dirt[0] + " of the helper's claims, not 5 and 5: "
				+ c.where());
			assertBuilt(helper, c);
			List<BlockPos> wrong = touched.stream().filter(p -> c.level.getBlockState(p).is(Blocks.DIRT)).toList();
			helper.assertTrue(wrong.isEmpty(), "dirt left in the house at " + wrong);
			LOGGER.info("qa 1110b crew claims messed with: broke {}, dirt {}; {}", broken[0], dirt[0], c.where());
		});
	}

	/**
	 * Save and reload mid-structure: while the helper holds a claim, the server restarts (every build site, the lead
	 * and the helper saved and loaded). The crew finishes the house from exactly the material list: nothing left over
	 * anywhere (no block placed twice from copied items) and nothing missing, with no step skipped, and the helper never
	 * drops a claim still to build once the lead's cursor reaches it.
	 */
	//$ gametest_ticks_batch HUGE '12000' '"qa1110bCrewRestart"'
	@GameTest(template = HUGE, timeoutTicks = 12000, batch = "qa1110bCrewRestart")
	public void aCrewRestartedMidStructureFinishesFromExactlyTheList(GameTestHelper helper) {
		Crew c = setUp(helper, 1);
		int[] at = {-1};
		helper.onEachTick(() -> {
			helper.setDayTime(3000);
			BuildSite site = c.site();
			if (at[0] < 0 && site != null && site.stage() == BuildPlan.Stage.STRUCTURE && site.placed() > 80
				&& site.claim(c.mate.getUUID()) != null) {
				at[0] = site.placed();
				var registries = c.level.registryAccess();
				c.level.getDataStorage().set(BuildSiteManager.NAME,
					BuildSiteManager.load(BuildSiteManager.get(c.level).save(new CompoundTag(), registries), registries));
				c.lead = QaCrewPass1107bGameTests.reload(c.level, c.lead);
				c.mate = QaCrewPass1107bGameTests.reload(c.level, c.mate);
				c.lastClaim = null;
			}
			watchClaims(c);
		});
		helper.succeedWhen(() -> {
			helper.assertTrue(at[0] >= 0, "setup: never restarted mid-structure with the helper holding a claim: " + c.where());
			assertBuilt(helper, c);
			Map<Item, Integer> left = leftOver(helper, c);
			helper.assertTrue(left.isEmpty(), "materials left over after a restart, building from exactly the list (duplicated): " + left);
			helper.assertTrue(c.site().skipped() == 0, "steps were skipped after the restart: " + c.where());
			helper.assertTrue(c.drops == 0, "the helper dropped " + c.drops + " claims still to build once the lead's cursor reached them: " + c.dropped);
			LOGGER.info("qa 1110b crew restart at {} placed: {}", at[0], c.where());
		});
	}
}
