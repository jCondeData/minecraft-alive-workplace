package io.github.jcondedata.aliveworkplace.test;

import io.github.jcondedata.aliveworkplace.blueprint.BlueprintData;
import io.github.jcondedata.aliveworkplace.blueprint.StarterBlueprints;
import io.github.jcondedata.aliveworkplace.build.BuildPlan;
import io.github.jcondedata.aliveworkplace.build.BuildSite;
import io.github.jcondedata.aliveworkplace.build.BuildSiteManager;
import io.github.jcondedata.aliveworkplace.build.Builders;
import io.github.jcondedata.aliveworkplace.build.Upkeep;
import io.github.jcondedata.aliveworkplace.registry.ModAttachments;
import io.github.jcondedata.aliveworkplace.registry.ModBlocks;
import io.github.jcondedata.aliveworkplace.registry.ModGameRules;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.Container;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.village.poi.PoiTypes;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.phys.AABB;

/**
 * QA (2026-10-26) of a builder's job disturbed by the world and the player, from the spec, not the code:
 * <ul>
 *   <li>CLAUDE.md "How the builder works": all progress lives in the saved site, the work is restartable at any tick,
 *   "Done" checks use {@code MaterialRules.matches}; ROADMAP 23.1 "every build finishes; no item is duplicated or lost
 *   (count them before and after)"; 23.2 recovery "never breaks a placed block".</li>
 *   <li>docs/guide/builder.md: "Short of something? The builder waits and tells you what's missing."</li>
 *   <li>docs/guide/blueprint-table.md: "Breaking it ... A builder who has leveled up keeps the job and takes a free
 *   table nearby by themselves."</li>
 *   <li>docs/wiki/builder.md: CLEAR "takes away what is in the way"; a Builder with a build "takes no fall damage ...
 *   Any other harm still hurts him" (so lava on the site is a real danger he must survive by not walking into it).</li>
 * </ul>
 * Every test builds the test hut for real from the chest by the table, disturbs it once mid-STRUCTURE, and checks the
 * end: the hut exactly as drawn, the builder alive and still a builder, and every item counted.
 */
public class QaBuilderDisruptionGameTests implements FabricGameTest {
	private static final String AREA = "aliveworkplace_test:build_area";
	private static final String BIG = "aliveworkplace_test:big_area";
	private static final org.slf4j.Logger LOGGER = org.slf4j.LoggerFactory.getLogger("aliveworkplace_test");
	private static final ResourceLocation TEST_HUT = ResourceLocation.fromNamespaceAndPath("aliveworkplace_test", "test_hut");
	private static final BlockPos TABLE = new BlockPos(2, 2, 2);
	private static final BlockPos CHEST = new BlockPos(2, 2, 4);
	private static final BlockPos VILLAGER = new BlockPos(3, 2, 3);
	private static final BlockPos HUT = new BlockPos(6, 2, 6);
	/** The chest and the barrels beside it, all within the table's reach. */
	private static final List<BlockPos> SUPPLIES = List.of(CHEST, new BlockPos(1, 2, 4), new BlockPos(3, 2, 4), new BlockPos(4, 2, 4), new BlockPos(5, 2, 4));
	/** What each test puts in the chest over the hut's own list, of every material. */
	private static final int EXTRA = 2;

	record Run(ServerLevel level, Villager builder, BuildSite site, BuildPlan plan, Map<Item, Integer> stocked) {
	}

	// ------------------------------------------------------------------------------------------------ setup helpers

	/** A builder at {@code table} (a Builder's Bench or Blueprint Table) with the hut's list plus EXTRA of each in the chest. */
	static Run start(GameTestHelper helper, Block table) {
		return start(helper, table, TEST_HUT, HUT);
	}

	/** The same for any blueprint, its list plus EXTRA of each in the chest and the barrels beside it. */
	static Run start(GameTestHelper helper, Block table, ResourceLocation id, BlockPos origin) {
		Leftovers.clear(helper);
		Leftovers.buildSites(helper);
		ServerLevel level = helper.getLevel();
		helper.setDayTime(2000);
		int delay = level.getGameRules().getInt(ModGameRules.BUILD_DELAY);
		boolean help = level.getGameRules().getBoolean(ModGameRules.BUILDERS_HELP);
		level.getGameRules().getRule(ModGameRules.BUILD_DELAY).set(2, level.getServer());
		level.getGameRules().getRule(ModGameRules.BUILDERS_HELP).set(false, level.getServer());
		Leftovers.after(helper, () -> {
			level.getGameRules().getRule(ModGameRules.BUILD_DELAY).set(delay, level.getServer());
			level.getGameRules().getRule(ModGameRules.BUILDERS_HELP).set(help, level.getServer());
		});
		helper.setBlock(TABLE, table);
		ensurePoi(level, helper.absolutePos(TABLE));
		for (BlockPos p : SUPPLIES) {
			helper.setBlock(p, p.equals(CHEST) ? Blocks.CHEST : Blocks.BARREL);
		}
		Villager builder = helper.spawn(EntityType.VILLAGER, VILLAGER);
		Builders.employ(level, builder, helper.absolutePos(TABLE));
		BlueprintData.Placement placement = new BlueprintData.Placement(level.dimension().location(), helper.absolutePos(origin), Rotation.NONE, Mirror.NONE);
		BuildSite site = Builders.start(level, builder, null, id, placement);
		BuildPlan plan = site.plan(level);
		if (plan == null) {
			throw new GameTestAssertException("no blueprint " + id);
		}
		Leftovers.after(helper, () -> {
			if (BuildSiteManager.get(level).get(site.id()) != null) {
				BuildSiteManager.get(level).remove(site.id());
			}
		});
		Map<Item, Integer> stocked = new HashMap<>();
		plan.materials().forEach((item, n) -> stocked.put(item, n + EXTRA));
		List<ItemStack> stock = new ArrayList<>();
		for (Map.Entry<Item, Integer> e : stocked.entrySet()) {
			int left = e.getValue();
			while (left > 0) {
				int n = Math.min(left, e.getKey().getDefaultMaxStackSize());
				stock.add(new ItemStack(e.getKey(), n));
				left -= n;
			}
		}
		helper.assertTrue(stock.size() <= SUPPLIES.size() * 27, "setup: " + stock.size() + " stacks don't fit the supplies");
		for (int i = 0; i < stock.size(); i++) {
			Container c = helper.getBlockEntity(SUPPLIES.get(i / 27));
			c.setItem(i % 27, stock.get(i));
		}
		LOGGER.info("qa disruption: {} materials {}, {} steps, bounds {}", id, plan.materials(), plan.placeableCount(), plan.bounds());
		return new Run(level, builder, site, plan, stocked);
	}

	static void ensurePoi(ServerLevel level, BlockPos pos) {
		if (level.getPoiManager().getType(pos).isEmpty()) {
			PoiTypes.forState(level.getBlockState(pos)).ifPresent(type -> level.getPoiManager().add(pos.immutable(), type));
		}
	}

	/** The builder is on a site of his (a repair) other than the first one. */
	static boolean repairing(Run r) {
		BuildSite now = Builders.activeSite(r.level(), r.builder());
		return now != null && !now.id().equals(r.site().id());
	}

	static boolean building(Run r) {
		return BuildSiteManager.get(r.level()).get(r.site().id()) != null;
	}

	/** STRUCTURE steps whose block stands in the world as drawn. */
	static List<BuildPlan.Step> placed(Run r) {
		List<BuildPlan.Step> out = new ArrayList<>();
		for (BuildPlan.Step s : r.plan().steps(BuildPlan.Stage.STRUCTURE)) {
			if (!s.state().isAir() && r.level().getBlockState(s.pos()).is(s.state().getBlock())) {
				out.add(s);
			}
		}
		return out;
	}

	/** STRUCTURE steps of a solid block whose place is still empty air standing on something solid. */
	static List<BuildPlan.Step> open(Run r) {
		List<BuildPlan.Step> out = new ArrayList<>();
		for (BuildPlan.Step s : r.plan().steps(BuildPlan.Stage.STRUCTURE)) {
			if (!s.state().isAir() && s.state().isSolid() && r.level().getBlockState(s.pos()).isAir()
				&& r.level().getBlockState(s.pos().below()).isSolid()) {
				out.add(s);
			}
		}
		return out;
	}

	static AABB around(GameTestHelper helper) {
		return helper.getBounds().inflate(16);
	}

	/** {@code item} in the chest, in every villager's bag near the test and lying on the ground near it. */
	static int loose(GameTestHelper helper, Item item) {
		int n = 0;
		for (BlockPos p : SUPPLIES) {
			n += ((Container) helper.getBlockEntity(p)).countItem(item);
		}
		for (Villager v : helper.getLevel().getEntitiesOfClass(Villager.class, around(helper))) {
			n += ModAttachments.BUILDER_BAG.getOrCreate(v).count(item);
		}
		for (ItemEntity e : helper.getLevel().getEntitiesOfClass(ItemEntity.class, around(helper))) {
			if (e.getItem().is(item)) {
				n += e.getItem().getCount();
			}
		}
		return n;
	}

	static int bag(Villager v, Item item) {
		return ModAttachments.BUILDER_BAG.getOrCreate(v).count(item);
	}

	/** Blocks of {@code block} standing in the build's box. */
	static int standing(Run r, Block block) {
		int n = 0;
		for (BlockPos p : BlockPos.betweenClosed(r.plan().bounds().minX(), r.plan().bounds().minY(), r.plan().bounds().minZ(),
			r.plan().bounds().maxX(), r.plan().bounds().maxY(), r.plan().bounds().maxZ())) {
			if (r.level().getBlockState(p).is(block)) {
				n++;
			}
		}
		return n;
	}

	static String state(GameTestHelper helper, Run r) {
		return "stage=" + r.site().stage() + " status=" + r.site().status() + " progress=" + Math.round(r.site().progress(r.plan()) * 100)
			+ "% skipped=" + r.site().skipped() + " missing=" + r.site().missing() + " builder alive=" + r.builder().isAlive()
			+ " at " + helper.relativePos(r.builder().blockPosition());
	}

	/** The hut is finished, every block as drawn but those in {@code except}, and the builder is alive. */
	static void assertFinished(GameTestHelper helper, Run r, List<BlockPos> except) {
		helper.assertTrue(r.builder().isAlive(), "the builder died: " + state(helper, r));
		helper.assertTrue(!building(r), "still building: " + state(helper, r));
		List<BlockPos> wrong = r.plan().unfinished(r.level()).stream().filter(p -> !except.contains(p)).toList();
		if (!wrong.isEmpty()) {
			throw new GameTestAssertException(wrong.size() + " block(s) not as drawn after the build, e.g. " + wrong.stream().limit(5)
				.map(p -> helper.relativePos(p) + "=" + r.level().getBlockState(p)).collect(Collectors.joining(", "))
				+ " (skipped " + r.site().skipped() + ")");
		}
	}

	/** After the build every material's loose count (chest + bags + ground) is what was stocked less what stands, plus {@code adjust}. */
	static void assertNothingMadeOrLost(GameTestHelper helper, Run r, Map<Item, Integer> adjust) {
		for (Map.Entry<Item, Integer> e : r.plan().materials().entrySet()) {
			Item item = e.getKey();
			int want = EXTRA + adjust.getOrDefault(item, 0);
			int got = loose(helper, item);
			helper.assertTrue(got == want, item + ": " + got + " left in the chest, bags and on the ground, wanted " + want
				+ " (stocked " + r.stocked().get(item) + ", the hut takes " + e.getValue() + ")");
		}
	}

	// ----------------------------------------------------------------------------------- a player breaks a placed block

	/**
	 * Mid-STRUCTURE, a player breaks a plank the builder already placed and keeps it. 23.1: "every build finishes; no item
	 * is duplicated or lost"; guide: builders "put back only the holes" of what they built (here with Ask Before Repairs
	 * off, so the repair needs no answer). The plank is back, before the build ends or by the repair after it, from the
	 * chest's spare: the spare planks are one fewer, nothing made up, nothing lost.
	 */
	//$ gametest_ticks_batch AREA '6000' '"qaDisruptBreak"'
	@GameTest(template = AREA, timeoutTicks = 6000, batch = "qaDisruptBreak")
	public void aPlankAPlayerBreaksMidBuildIsPutBackFromTheSpare(GameTestHelper helper) {
		Run r = start(helper, ModBlocks.BUILDERS_BENCH);
		boolean upkeep = Upkeep.ENABLED;
		boolean ask = Upkeep.ASK;
		Upkeep.ENABLED = true;
		Upkeep.ASK = false;
		Leftovers.after(helper, () -> {
			Upkeep.ENABLED = upkeep;
			Upkeep.ASK = ask;
		});
		BlockPos[] broken = new BlockPos[1];
		helper.onEachTick(() -> {
			if (broken[0] != null || r.site().stage() != BuildPlan.Stage.STRUCTURE) {
				return;
			}
			List<BuildPlan.Step> planks = placed(r).stream().filter(s -> s.state().is(Blocks.OAK_PLANKS)).toList();
			if (planks.size() >= 3) {
				broken[0] = planks.get(0).pos().immutable();
				r.level().destroyBlock(broken[0], false); // the player mined it and keeps the plank
				LOGGER.info("qa disruption break: plank at {} broken, {}", helper.relativePos(broken[0]), state(helper, r));
			}
		});
		helper.succeedWhen(() -> {
			helper.assertTrue(broken[0] != null, "setup: no plank was ever placed to break: " + state(helper, r));
			helper.assertTrue(r.level().getBlockState(broken[0]).is(Blocks.OAK_PLANKS), "the broken plank at " + helper.relativePos(broken[0])
				+ " wasn't put back: " + r.level().getBlockState(broken[0]) + "; " + state(helper, r) + ", repairing " + repairing(r));
			helper.assertFalse(repairing(r), "still repairing");
			assertFinished(helper, r, List.of());
			assertNothingMadeOrLost(helper, r, Map.of(Items.OAK_PLANKS, -1));
		});
	}

	// ----------------------------------------------------------------------- a player's own block where the drawing has one

	/**
	 * Mid-STRUCTURE, a player puts a gold block where the drawing has a plank not yet placed. Whatever the builder does
	 * with it (leaves it as a player's block and skips the step, or clears it and builds the plank), the gold is never
	 * lost: it stands there, or it is in the chest, a bag or on the ground. Every other block is as drawn, the build ends,
	 * and the planks add up (the skipped plank's item is left over, or it was used).
	 */
	//$ gametest_ticks_batch AREA '6000' '"qaDisruptForeign"'
	@GameTest(template = AREA, timeoutTicks = 6000, batch = "qaDisruptForeign")
	public void aGoldBlockAPlayerPutsWhereAPlankGoesIsNeverLost(GameTestHelper helper) {
		Run r = start(helper, ModBlocks.BUILDERS_BENCH);
		ServerPlayer player = helper.makeMockServerPlayerInLevel();
		BlockPos[] at = new BlockPos[1];
		helper.onEachTick(() -> {
			if (at[0] != null || r.site().stage() != BuildPlan.Stage.STRUCTURE || placed(r).size() < 3) {
				return;
			}
			List<BuildPlan.Step> spots = open(r).stream().filter(s -> s.state().is(Blocks.OAK_PLANKS)).toList();
			if (spots.isEmpty()) {
				return;
			}
			BuildPlan.Step spot = spots.get(spots.size() - 1);
			helper.assertTrue(StewardSafetyGameTests.place(helper, player, spot.pos(), Blocks.GOLD_BLOCK), "setup: the gold block wasn't placed");
			helper.assertTrue(r.level().getBlockState(spot.pos()).is(Blocks.GOLD_BLOCK), "setup: placing put " + r.level().getBlockState(spot.pos()));
			at[0] = spot.pos().immutable();
			LOGGER.info("qa disruption foreign: gold at {} where a plank goes, {}", helper.relativePos(at[0]), state(helper, r));
		});
		helper.succeedWhen(() -> {
			helper.assertTrue(at[0] != null, "setup: no open plank spot was found: " + state(helper, r));
			boolean stands = r.level().getBlockState(at[0]).is(Blocks.GOLD_BLOCK);
			int goldItems = loose(helper, Items.GOLD_BLOCK);
			helper.assertTrue(stands || goldItems == 1, "the player's gold block is gone (now " + r.level().getBlockState(at[0]) + " at "
				+ helper.relativePos(at[0]) + ", " + goldItems + " gold blocks loose): " + state(helper, r));
			assertFinished(helper, r, stands ? List.of(at[0]) : List.of());
			if (stands) {
				helper.assertTrue(r.site().skipped() > 0, "the gold block stands in the plank's place but nothing was counted skipped");
			} else {
				helper.assertTrue(r.level().getBlockState(at[0]).is(Blocks.OAK_PLANKS), "the gold was cleared but no plank built there: "
					+ r.level().getBlockState(at[0]));
			}
			assertNothingMadeOrLost(helper, r, Map.of(Items.OAK_PLANKS, stands ? 1 : 0));
			LOGGER.info("qa disruption foreign: done, gold {}", stands ? "left standing (skipped " + r.site().skipped() + ")" : "cleared into the chest");
		});
	}

	// ------------------------------------------------------------------------------------- water and lava on the site

	/**
	 * Mid-STRUCTURE, water poured where a wall block goes (it spreads over the floor). CLEAR "takes away what is in the
	 * way" and steps are done when the block stands: the hut ends as drawn, no water is left in its box, the builder is
	 * alive, and every material adds up.
	 */
	//$ gametest_ticks_batch AREA '6000' '"qaDisruptWater"'
	@GameTest(template = AREA, timeoutTicks = 6000, batch = "qaDisruptWater")
	public void waterPouredOnTheSiteMidBuildLeavesTheHutAsDrawn(GameTestHelper helper) {
		fluidOnTheSite(helper, Blocks.WATER);
	}

	/**
	 * Mid-STRUCTURE, lava poured where a wall block goes (fire spread off, so only the lava itself is the disruption). The
	 * builder survives (wiki: "Any other harm still hurts him"), the hut ends as drawn with no lava in its box, and every
	 * material adds up.
	 */
	//$ gametest_ticks_batch AREA '6000' '"qaDisruptLava"'
	@GameTest(template = AREA, timeoutTicks = 6000, batch = "qaDisruptLava")
	public void lavaPouredOnTheSiteMidBuildLeavesTheHutAsDrawnAndTheBuilderAlive(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		boolean fire = level.getGameRules().getBoolean(GameRules.RULE_DOFIRETICK);
		level.getGameRules().getRule(GameRules.RULE_DOFIRETICK).set(false, level.getServer());
		Leftovers.after(helper, () -> level.getGameRules().getRule(GameRules.RULE_DOFIRETICK).set(fire, level.getServer()));
		fluidOnTheSite(helper, Blocks.LAVA);
	}

	private static void fluidOnTheSite(GameTestHelper helper, Block fluid) {
		Run r = start(helper, ModBlocks.BUILDERS_BENCH);
		BlockPos[] at = new BlockPos[1];
		String[] died = new String[1];
		helper.onEachTick(() -> {
			if (died[0] == null && !r.builder().isAlive()) {
				died[0] = state(helper, r) + " (last hurt by " + r.builder().getLastDamageSource() + ")";
			}
			if (at[0] != null || r.site().stage() != BuildPlan.Stage.STRUCTURE || placed(r).size() < 3) {
				return;
			}
			BlockPos me = r.builder().blockPosition();
			List<BuildPlan.Step> spots = open(r).stream().filter(s -> s.pos().distManhattan(me) > 2).toList();
			if (spots.isEmpty()) {
				return;
			}
			at[0] = spots.get(spots.size() / 2).pos().immutable();
			r.level().setBlock(at[0], fluid.defaultBlockState(), Block.UPDATE_ALL);
			LOGGER.info("qa disruption {}: poured at {} where {} goes, {}", fluid, helper.relativePos(at[0]),
				r.plan().steps(BuildPlan.Stage.STRUCTURE).stream().filter(s -> s.pos().equals(at[0])).findFirst().map(s -> s.state().toString()).orElse("?"),
				state(helper, r));
		});
		helper.succeedWhen(() -> {
			helper.assertTrue(died[0] == null, "the builder died on the site: " + died[0]);
			helper.assertTrue(at[0] != null, "setup: no open spot to pour on: " + state(helper, r));
			assertFinished(helper, r, List.of());
			BoundingBox box = r.plan().bounds();
			for (BlockPos p : BlockPos.betweenClosed(box.minX(), box.minY(), box.minZ(), box.maxX(), box.maxY(), box.maxZ())) {
				var f = r.level().getFluidState(p);
				helper.assertTrue(f.isEmpty() || !(f.is(FluidTags.WATER) || f.is(FluidTags.LAVA)) || r.level().getBlockState(p).hasProperty(
						net.minecraft.world.level.block.state.properties.BlockStateProperties.WATERLOGGED),
					"the " + fluid + " is still in the finished hut at " + helper.relativePos(p) + ": " + r.level().getBlockState(p));
			}
			assertNothingMadeOrLost(helper, r, Map.of());
		});
	}

	// ------------------------------------------------------------------------------- the chest emptied, then refilled

	/**
	 * Guide: "Short of something? The builder waits and tells you what's missing." The Starter Cottage (more than a bag's
	 * stretch of work, so he goes back for more) from the chest and barrels by the bench. Mid-STRUCTURE the player empties
	 * them all. Until he has waited with a list of what's missing for 100 ticks, nothing is made up (blocks standing plus
	 * what he carries never grows) and the build doesn't end. The player puts everything back: the cottage is finished as
	 * drawn and the supplies hold exactly the spare.
	 */
	//$ gametest_ticks_batch BIG '12000' '"qaDisruptEmptied"'
	@GameTest(template = BIG, timeoutTicks = 12000, batch = "qaDisruptEmptied")
	public void theSuppliesEmptiedMidBuildHoldTheBuilderUntilTheyAreRefilled(GameTestHelper helper) {
		Run r = start(helper, ModBlocks.BUILDERS_BENCH, StarterBlueprints.STARTER_COTTAGE.id(), new BlockPos(9, 2, 9));
		Map<BlockPos, List<ItemStack>> taken = new HashMap<>();
		long[] emptiedAt = {-1};
		long[] waitedAt = {-1};
		int[] ceiling = new int[1];
		boolean[] refilled = {false};
		List<String> grew = new ArrayList<>();
		helper.onEachTick(() -> {
			long now = helper.getTick();
			if (emptiedAt[0] < 0) {
				if (r.site().stage() == BuildPlan.Stage.STRUCTURE && r.plan().placeableCount() - r.plan().unfinished(r.level()).size() >= 30) {
					for (BlockPos p : SUPPLIES) {
						Container c = helper.getBlockEntity(p);
						List<ItemStack> out = new ArrayList<>();
						for (int i = 0; i < c.getContainerSize(); i++) {
							out.add(c.removeItemNoUpdate(i));
						}
						c.setChanged();
						taken.put(p, out);
					}
					emptiedAt[0] = now;
					ceiling[0] = carriedAndPlaced(r);
					LOGGER.info("qa disruption emptied: at {} placed+bag {}, bag {}, {}", now, ceiling[0],
						ModAttachments.BUILDER_BAG.getOrCreate(r.builder()).stacks(), state(helper, r));
				}
				return;
			}
			if (refilled[0]) {
				return;
			}
			int sum = carriedAndPlaced(r);
			if (sum > ceiling[0] && grew.isEmpty()) {
				grew.add("at tick " + now + " placed+bag grew to " + sum + " from " + ceiling[0] + " with the supplies empty: " + state(helper, r));
			}
			if (waitedAt[0] < 0 && r.site().status() == BuildSite.Status.WAITING_FOR_MATERIALS && !r.site().missing().isEmpty()) {
				waitedAt[0] = now;
				LOGGER.info("qa disruption emptied: waiting at {} ({} after emptying), {}", now, now - emptiedAt[0], state(helper, r));
			}
			helper.assertTrue(grew.isEmpty(), "something was made up while the supplies were empty: " + grew);
			helper.assertTrue(building(r), "the build ended with the supplies emptied: " + state(helper, r));
			helper.assertTrue(now - emptiedAt[0] < 3000, "the builder never waited with a list of what's missing in 3000 ticks: " + state(helper, r));
			if (waitedAt[0] >= 0 && now - waitedAt[0] >= 100) {
				for (Map.Entry<BlockPos, List<ItemStack>> e : taken.entrySet()) {
					Container c = helper.getBlockEntity(e.getKey());
					for (int i = 0; i < e.getValue().size(); i++) {
						c.setItem(i, e.getValue().get(i));
					}
					c.setChanged();
				}
				refilled[0] = true;
				LOGGER.info("qa disruption emptied: refilled at {}, {}", now, state(helper, r));
			}
		});
		helper.succeedWhen(() -> {
			helper.assertTrue(refilled[0], "not refilled yet: " + state(helper, r));
			assertFinished(helper, r, List.of());
			assertNothingMadeOrLost(helper, r, Map.of());
		});
	}

	/** Steps standing as drawn plus the build's materials in the builder's bag. */
	private static int carriedAndPlaced(Run r) {
		int n = r.plan().placeableCount() - r.plan().unfinished(r.level()).size();
		for (Item item : r.plan().materials().keySet()) {
			n += bag(r.builder(), item);
		}
		return n;
	}

	// --------------------------------------------------------------------------------- the Blueprint Table broken

	/**
	 * docs/guide/blueprint-table.md: "Mine it ...; it drops itself ... A builder who has leveled up keeps the job and takes
	 * a free table nearby by themselves." A level-2 builder mid-STRUCTURE whose table a player breaks, with a free table
	 * two blocks away: he is still a builder, works at the free table, and the hut is finished as drawn with every item
	 * counted.
	 */
	//$ gametest_ticks_batch AREA '7000' '"qaDisruptTable"'
	@GameTest(template = AREA, timeoutTicks = 7000, batch = "qaDisruptTable")
	public void aLeveledBuilderWhoseTableIsBrokenTakesTheFreeOneAndFinishes(GameTestHelper helper) {
		Run r = start(helper, ModBlocks.BLUEPRINT_TABLE);
		Villager b = r.builder();
		b.setVillagerData(b.getVillagerData().setLevel(2));
		b.setVillagerXp(20);
		BlockPos free = new BlockPos(4, 2, 2);
		helper.setBlock(free, ModBlocks.BLUEPRINT_TABLE);
		ensurePoi(r.level(), helper.absolutePos(free));
		boolean[] broke = {false};
		helper.onEachTick(() -> {
			if (broke[0] || r.site().stage() != BuildPlan.Stage.STRUCTURE || placed(r).size() < 3) {
				return;
			}
			helper.assertTrue(Builders.benchPos(b).map(p -> p.equals(helper.absolutePos(TABLE))).orElse(false),
				"setup: the builder doesn't work at the first table: " + Builders.benchPos(b));
			r.level().destroyBlock(helper.absolutePos(TABLE), true);
			broke[0] = true;
			LOGGER.info("qa disruption table: broken, {}", state(helper, r));
		});
		helper.succeedWhen(() -> {
			helper.assertTrue(broke[0], "setup: the table wasn't broken yet: " + state(helper, r));
			helper.assertTrue(Builders.isBuilder(b), "the leveled builder lost the job when his table was broken: " + b.getVillagerData()
				+ "; " + state(helper, r));
			helper.assertTrue(Builders.benchPos(b).map(p -> p.equals(helper.absolutePos(free))).orElse(false),
				"the builder didn't take the free table nearby: works at " + Builders.benchPos(b).map(helper::relativePos) + "; " + state(helper, r));
			assertFinished(helper, r, List.of());
			assertNothingMadeOrLost(helper, r, Map.of());
		});
	}
}
