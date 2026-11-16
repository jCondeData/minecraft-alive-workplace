package io.github.jcondedata.aliveworkplace.test;

import io.github.jcondedata.aliveworkplace.blueprint.BlueprintData;
import io.github.jcondedata.aliveworkplace.blueprint.StarterBlueprints;
import io.github.jcondedata.aliveworkplace.build.BlueprintSupplies;
import io.github.jcondedata.aliveworkplace.build.BuildPlan;
import io.github.jcondedata.aliveworkplace.build.BuildSite;
import io.github.jcondedata.aliveworkplace.build.BuildSiteManager;
import io.github.jcondedata.aliveworkplace.build.Builders;
import io.github.jcondedata.aliveworkplace.hall.UpgradesPage;
import io.github.jcondedata.aliveworkplace.hall.VillageHalls;
import io.github.jcondedata.aliveworkplace.registry.ModAttachments;
import io.github.jcondedata.aliveworkplace.registry.ModBlocks;
import io.github.jcondedata.aliveworkplace.registry.ModGameRules;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.Container;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings;

/**
 * QA (lane b, 2026-11-16, session lane-b-1116-1238) of a building UPGRADE across a save and reload, from the spec, not
 * the code: CLAUDE.md "All progress lives in BuildSite (saved). BuilderWork must stay restartable at any tick"; wiki
 * builder.md "Progress never drops after a restart (B46, B64)", "the record is saved, so a Builder can stop at any tick
 * and carry on exactly where it was", "What that list is short of goes on the Storehouse's requests board ... the
 * Builder builds on with what he has"; scaffolding.md "the scaffolding goes back into its chests". The upgrade is the
 * player's own entry point (the Village Hall's Upgrades page, {@link UpgradesPage#order}) on a finished Starter
 * Cottage, which becomes a Starter Cottage II (a whole new storey, so there is a long STRUCTURE to interrupt).
 * Gaps the earlier QA left (ROADMAP Notes 2026-11-16, lane-b-1116-0637): QaBuilderReloadGameTests' upgrade is the test
 * hut's one-lantern level II and checks neither the cursor, the materials nor the scaffolding across the restart.
 */
public class QaBuilderUpgrade1116bGameTests {
	private static final String BIG_AREA = "aliveworkplace_test:big_area";
	private static final ResourceLocation COTTAGE = StarterBlueprints.STARTER_COTTAGE.id();
	private static final ResourceLocation COTTAGE_2 = StarterBlueprints.STARTER_COTTAGE_2.id();
	/** The cottage (11 wide, 11 deep as Cottage II) stands at x 1-11, z 1-11. */
	private static final BlockPos COTTAGE_REL = new BlockPos(1, 2, 1);
	private static final BlockPos BENCH = new BlockPos(16, 2, 3);
	private static final BlockPos CHEST = new BlockPos(16, 2, 5);
	private static final BlockPos CHEST_B = new BlockPos(16, 2, 7);
	private static final BlockPos HALL = new BlockPos(18, 2, 16);
	private static final BlockPos VILLAGER = new BlockPos(15, 2, 4);
	private static final int SCAFFOLDS = 16;

	private record Up(ServerLevel level, Villager[] builder, BlueprintData.Placement placement, ServerPlayer player, List<ItemStack> stock) {
		Villager villager() {
			return builder[0];
		}

		BuildSite live() {
			return BuildSiteManager.get(level).all().stream().filter(s -> s.placement().equals(placement) && !s.isDone()).findFirst().orElse(null);
		}
	}

	// ------------------------------------------------------------------------------------------------- helpers

	private static ServerLevel rules(GameTestHelper helper) {
		Leftovers.clear(helper);
		ServerLevel level = helper.getLevel();
		helper.setDayTime(2000);
		boolean free = level.getGameRules().getBoolean(ModGameRules.FREE_MATERIALS);
		int delay = level.getGameRules().getInt(ModGameRules.BUILD_DELAY);
		boolean help = level.getGameRules().getBoolean(ModGameRules.BUILDERS_HELP);
		level.getGameRules().getRule(ModGameRules.FREE_MATERIALS).set(false, level.getServer());
		level.getGameRules().getRule(ModGameRules.BUILD_DELAY).set(2, level.getServer());
		level.getGameRules().getRule(ModGameRules.BUILDERS_HELP).set(false, level.getServer());
		int radius = VillageHalls.RADIUS;
		VillageHalls.RADIUS = 16;
		Leftovers.after(helper, () -> {
			VillageHalls.RADIUS = radius;
			level.getGameRules().getRule(ModGameRules.FREE_MATERIALS).set(free, level.getServer());
			level.getGameRules().getRule(ModGameRules.BUILD_DELAY).set(delay, level.getServer());
			level.getGameRules().getRule(ModGameRules.BUILDERS_HELP).set(help, level.getServer());
		});
		return level;
	}

	/**
	 * A finished Starter Cottage, a Village Hall, a Builder at his bench and the chests beside it stocked with what the
	 * Cottage II still needs (less {@code withhold} of its biggest need, and plus {@value #SCAFFOLDS} scaffolding).
	 */
	private static Up setup(GameTestHelper helper, int withhold, Item[] withheldItem, int[] withheld) {
		ServerLevel level = rules(helper);
		BlueprintData.Placement placement = new BlueprintData.Placement(level.dimension().location(), helper.absolutePos(COTTAGE_REL), Rotation.NONE, Mirror.NONE);
		BuildSiteManager sites = BuildSiteManager.get(level);
		Runnable forget = () -> {
			sites.forgetFinished(placement);
			for (BuildSite site : List.copyOf(sites.all())) {
				if (site.placement().equals(placement)) {
					sites.remove(site.id());
				}
			}
		};
		forget.run(); // (a site left here by an interrupted earlier run would read as "already building")
		Leftovers.after(helper, forget);
		helper.setBlock(HALL, ModBlocks.VILLAGE_HALL);
		BlockPos origin = helper.absolutePos(COTTAGE_REL);
		level.getStructureManager().get(COTTAGE).orElseThrow().placeInWorld(level, origin, origin, new StructurePlaceSettings(), RandomSource.create(1L), 2);
		sites.recordFinished(COTTAGE, placement, UUID.randomUUID());

		helper.setBlock(BENCH, ModBlocks.BUILDERS_BENCH);
		helper.setBlock(CHEST, Blocks.CHEST);
		helper.setBlock(CHEST_B, Blocks.CHEST);
		var report = BlueprintSupplies.check(level, new BlueprintData(COTTAGE_2, Optional.of(StarterBlueprints.STARTER_COTTAGE_2.size()), Optional.of(placement)))
			.orElseThrow(() -> new GameTestAssertException("no supply report for the Cottage II"));
		final int wanted = withhold;
		// The item withheld is one the old cottage has none of, so nothing taken off its roof can stand in for it.
		java.util.Set<Item> inCottage = new java.util.HashSet<>();
		BlockPos.betweenClosedStream(origin, origin.offset(StarterBlueprints.STARTER_COTTAGE_2.size()).offset(-1, -1, -1))
			.forEach(p -> inCottage.add(level.getBlockState(p).getBlock().asItem()));
		var biggest = report.missing().stream().filter(m -> wanted == 0 || !inCottage.contains(m.item())).max((x, y) -> Integer.compare(x.count(), y.count()))
			.orElseThrow(() -> new GameTestAssertException("setup: the supply report wants nothing new for the Cottage II: " + report.missing()));
		if (withhold > 0) {
			withhold = Math.min(withhold, biggest.count());
		}
		withheldItem[0] = biggest.item();
		withheld[0] = withhold;
		List<ItemStack> stacks = new ArrayList<>();
		for (var m : report.missing()) {
			int count = m.count() - (m.item() == biggest.item() ? withhold : 0);
			for (int left = count; left > 0; left -= m.item().getDefaultMaxStackSize()) {
				stacks.add(new ItemStack(m.item(), Math.min(left, m.item().getDefaultMaxStackSize())));
			}
		}
		stacks.add(new ItemStack(Items.SCAFFOLDING, SCAFFOLDS));
		helper.assertTrue(stacks.size() <= 54, "setup: the Cottage II needs " + stacks.size() + " stacks: more than two chests");
		Container a = helper.getBlockEntity(CHEST);
		Container b = helper.getBlockEntity(CHEST_B);
		for (int i = 0; i < stacks.size(); i++) {
			(i < 27 ? a : b).setItem(i % 27, stacks.get(i).copy());
		}
		Villager villager = helper.spawn(EntityType.VILLAGER, VILLAGER);
		Builders.employ(level, villager, helper.absolutePos(BENCH));
		ServerPlayer player = helper.makeMockServerPlayerInLevel();
		return new Up(level, new Villager[]{villager}, placement, player, stacks);
	}

	/** The player orders the upgrade from the hall's Upgrades page (unless the village took it up on its own). */
	private static BuildSite order(GameTestHelper helper, Up u) {
		if (u.live() == null) {
			UpgradesPage.Outcome outcome = UpgradesPage.order(u.level(), helper.absolutePos(HALL), u.player(), u.placement());
			helper.assertTrue(outcome == UpgradesPage.Outcome.STARTED, "the upgrade wasn't ordered: " + outcome);
		}
		BuildSite site = u.live();
		helper.assertTrue(site != null && site.structure().equals(COTTAGE_2), "the upgrade's site is " + site);
		return site;
	}

	/** A server restart: every build site and the Builder saved and loaded again (the Builder as a fresh entity). */
	private static void restart(GameTestHelper helper, Up u) {
		ServerLevel level = u.level();
		var registries = level.registryAccess();
		level.getDataStorage().set(BuildSiteManager.NAME, BuildSiteManager.load(BuildSiteManager.get(level).save(new CompoundTag(), registries), registries));
		Villager old = u.builder()[0];
		CompoundTag tag = new CompoundTag();
		helper.assertTrue(old.save(tag), "the builder wasn't saved");
		old.discard();
		Entity loaded = EntityType.loadEntityRecursive(tag, level, e -> e);
		helper.assertTrue(loaded instanceof Villager, "the builder didn't load as a villager: " + loaded);
		level.addFreshEntity(loaded);
		u.builder()[0] = (Villager) loaded;
	}

	/** Every item in the two chests, the Builder's bag and on the ground near the test. */
	private static Map<Item, Integer> held(GameTestHelper helper, Up u) {
		Map<Item, Integer> out = new LinkedHashMap<>();
		for (BlockPos p : List.of(CHEST, CHEST_B)) {
			if (helper.getLevel().getBlockEntity(helper.absolutePos(p)) instanceof Container c) {
				for (int i = 0; i < c.getContainerSize(); i++) {
					ItemStack s = c.getItem(i);
					if (!s.isEmpty()) {
						out.merge(s.getItem(), s.getCount(), Integer::sum);
					}
				}
			}
		}
		for (ItemStack s : ModAttachments.BUILDER_BAG.getOrCreate(u.villager()).stacks()) {
			if (!s.isEmpty()) {
				out.merge(s.getItem(), s.getCount(), Integer::sum);
			}
		}
		for (ItemEntity e : helper.getLevel().getEntitiesOfClass(ItemEntity.class, helper.getBounds().inflate(8))) {
			out.merge(e.getItem().getItem(), e.getItem().getCount(), Integer::sum);
		}
		return out;
	}

	private static int bagTotal(Villager v) {
		int n = 0;
		for (ItemStack s : ModAttachments.BUILDER_BAG.getOrCreate(v).stacks()) {
			n += s.getCount();
		}
		return n;
	}

	private static int scaffoldingStanding(GameTestHelper helper) {
		int[] n = {0};
		BlockPos.betweenClosedStream(helper.getBounds()).forEach(p -> {
			if (helper.getLevel().getBlockState(p).is(Blocks.SCAFFOLDING)) {
				n[0]++;
			}
		});
		return n[0];
	}

	private static String where(Up u) {
		BuildSite s = u.live();
		return s == null ? "(no site)" : s.stage() + " " + s.status() + " cursor " + s.cursor() + " placed " + s.placed() + " skipped " + s.skipped()
			+ " missing " + s.missing() + " scaffolds " + s.scaffolds().size();
	}

	/** Done: no site left, recorded as Cottage II, every block of it as drawn, every scaffold back in the chests. */
	private static void assertUpgraded(GameTestHelper helper, Up u, BuildPlan plan) {
		ServerLevel level = u.level();
		helper.assertTrue(u.live() == null, "still upgrading: " + where(u));
		helper.assertTrue(BuildSiteManager.get(level).finishedIn(level).stream().anyMatch(f -> f.placement().equals(u.placement()) && f.structure().equals(COTTAGE_2)),
			"the cottage isn't recorded as its Cottage II");
		List<BlockPos> wrong = plan.unfinished(level);
		BlockPos origin = u.placement().origin();
		helper.assertTrue(wrong.isEmpty(), wrong.size() + " block(s) of the Cottage II wrong, e.g. " + wrong.stream().limit(6)
			.map(p -> p.subtract(origin).toShortString() + "=" + level.getBlockState(p)).collect(Collectors.joining(", ")));
		helper.assertTrue(scaffoldingStanding(helper) == 0, scaffoldingStanding(helper) + " scaffolding still standing after the upgrade");
		int scaffolds = held(helper, u).getOrDefault(Items.SCAFFOLDING, 0);
		helper.assertTrue(scaffolds == SCAFFOLDS, "the chests, bag and ground hold " + scaffolds + " scaffolding after the upgrade, stocked " + SCAFFOLDS);
		helper.assertTrue(bagTotal(u.villager()) == 0, "the finished builder still carries " + ModAttachments.BUILDER_BAG.getOrCreate(u.villager()).stacks());
	}

	/**
	 * Restarts the server once the upgrade's site is in {@code stage} with something in the bag and at least {@code minPlaced}
	 * blocks placed (or {@code fallback} ticks into the stage), and checks the restart kept everything as it was: the site, its
	 * stage, cursor and blocks placed (not back to the start), its scaffolds, the Builder's bag and every item held.
	 */
	private static boolean restartIfIn(GameTestHelper helper, Up u, BuildPlan.Stage stage, int minPlaced, int fallback, int[] inStage) {
		BuildSite site = u.live();
		if (site == null || site.stage() != stage) {
			return false;
		}
		inStage[0]++;
		if ((bagTotal(u.villager()) == 0 || site.placed() < minPlaced) && inStage[0] < fallback) {
			return false;
		}
		UUID id = site.id();
		int cursor = site.cursor();
		int placed = site.placed();
		List<BlockPos> scaffolds = List.copyOf(site.scaffolds());
		Map<Item, Integer> heldBefore = held(helper, u);
		List<ItemStack> bagBefore = ModAttachments.BUILDER_BAG.getOrCreate(u.villager()).stacks().stream().filter(s -> !s.isEmpty()).map(ItemStack::copy).toList();
		restart(helper, u);
		BuildSite back = u.live();
		helper.assertTrue(back != null && back.id().equals(id) && back.structure().equals(COTTAGE_2), "after a restart in " + stage + " the upgrade's site is " + back);
		helper.assertTrue(back.stage() == stage, "a restart in " + stage + " put the upgrade in " + back.stage());
		helper.assertTrue(stage == BuildPlan.Stage.FOUNDATION || stage == BuildPlan.Stage.LANDSCAPE || back.cursor() == cursor,
			"a restart in " + stage + " moved the upgrade's cursor from " + cursor + " to " + back.cursor());
		helper.assertTrue(back.placed() == placed, "a restart in " + stage + " changed the blocks placed from " + placed + " to " + back.placed());
		helper.assertTrue(back.scaffolds().equals(scaffolds), "a restart in " + stage + " changed the tracked scaffolds from " + scaffolds + " to " + back.scaffolds());
		helper.assertTrue(back.builder() != null && back.builder().equals(u.villager().getUUID()), "after the restart the upgrade's builder is " + back.builder());
		Map<Item, Integer> heldAfter = held(helper, u);
		helper.assertTrue(heldAfter.equals(heldBefore), "a restart in " + stage + " changed the items held from " + heldBefore + " to " + heldAfter);
		List<ItemStack> bagAfter = ModAttachments.BUILDER_BAG.getOrCreate(u.villager()).stacks().stream().filter(s -> !s.isEmpty()).toList();
		helper.assertTrue(bagAfter.size() == bagBefore.size() && java.util.stream.IntStream.range(0, bagAfter.size())
			.allMatch(i -> ItemStack.matches(bagAfter.get(i), bagBefore.get(i))), "a restart in " + stage + " changed the bag from " + bagBefore + " to " + bagAfter);
		u.level().getServer().sendSystemMessage(net.minecraft.network.chat.Component.literal("[qa-upgrade-1116b] restarted in " + stage + " cursor " + cursor
			+ " placed " + placed + " scaffolds " + scaffolds.size() + " bag " + bagBefore));
		return true;
	}

	// ------------------------------------------------------------------------------------------------- the tests

	/**
	 * The Cottage II ordered from the hall, the server restarted twice: in the middle of its STRUCTURE (the new storey,
	 * the Builder carrying blocks, well into the list) and in its DECORATION. Each restart keeps the site, its stage,
	 * cursor, blocks placed, scaffolds, the bag and every item held exactly; the upgrade then finishes as drawn, recorded
	 * as Cottage II, every scaffold back in the chests and the bag empty.
	 */
	//$ gametest_ticks_batch BIG_AREA '12000' '"qaUpg1116bReload"'
	@GameTest(template = BIG_AREA, timeoutTicks = 12000, batch = "qaUpg1116bReload")
	public void anUpgradeRestartedMidStructureAndDecorationCarriesOnWhereItWas(GameTestHelper helper) {
		Up u = setup(helper, 0, new Item[1], new int[1]);
		BuildPlan[] plan = {null};
		int[] ticks = {0};
		int[] inStructure = {0};
		int[] inDecoration = {0};
		boolean[] done = {false, false};
		boolean[] scaffolded = {false};
		helper.onEachTick(() -> {
			ticks[0]++;
			helper.setDayTime(2000);
			if (ticks[0] < 3) {
				return;
			}
			if (plan[0] == null) {
				plan[0] = order(helper, u).plan(u.level());
				helper.assertTrue(plan[0].steps(BuildPlan.Stage.STRUCTURE).size() >= 60, "setup: the Cottage II has only "
					+ plan[0].steps(BuildPlan.Stage.STRUCTURE).size() + " structure steps");
				return;
			}
			if (!done[0]) {
				done[0] = restartIfIn(helper, u, BuildPlan.Stage.STRUCTURE, 60, 3000, inStructure);
			} else if (!done[1]) {
				done[1] = restartIfIn(helper, u, BuildPlan.Stage.DECORATION, 2, 10, inDecoration);
			}
			// and once more the first tick scaffolding is up (if the builder ever needs it): the site keeps every scaffold
			BuildSite live = u.live();
			if (!scaffolded[0] && live != null && !live.scaffolds().isEmpty()) {
				scaffolded[0] = true;
				restartIfIn(helper, u, live.stage(), 0, 0, new int[1]);
			}
		});
		helper.succeedWhen(() -> {
			helper.assertTrue(done[0], "the upgrade was never restarted mid-STRUCTURE: " + where(u));
			helper.assertTrue(done[1], "the upgrade was never restarted mid-DECORATION: " + where(u));
			assertUpgraded(helper, u, plan[0]);
		});
	}

	/**
	 * Short of (up to) 20 of the biggest need the old cottage has none of: the Builder builds on and then waits, the status saying "Needs" and
	 * naming it, the site missing no more than them; the server restarted while he waits keeps him waiting with the same
	 * shortfall and progress (never back to the start); topped up with them, he finishes it as drawn with nothing over.
	 */
	//$ gametest_ticks_batch BIG_AREA '14000' '"qaUpg1116bShort"'
	@GameTest(template = BIG_AREA, timeoutTicks = 14000, batch = "qaUpg1116bShort")
	public void anUpgradeShortWaitsSaysWhatItNeedsThroughARestartAndFinishesWhenSupplied(GameTestHelper helper) {
		Item[] shortItem = new Item[1];
		int[] withheld = {0};
		Up u = setup(helper, 20, shortItem, withheld);
		BuildPlan[] plan = {null};
		int[] ticks = {0};
		int[] waited = {0};
		int[] progress = {-1};
		boolean[] restarted = {false};
		boolean[] supplied = {false};
		helper.onEachTick(() -> {
			ticks[0]++;
			helper.setDayTime(2000);
			if (ticks[0] < 3) {
				return;
			}
			if (plan[0] == null) {
				plan[0] = order(helper, u).plan(u.level());
				return;
			}
			BuildSite site = u.live();
			if (supplied[0] || site == null) {
				return;
			}
			if (site.status() != BuildSite.Status.WAITING_FOR_MATERIALS) {
				waited[0] = 0;
				return;
			}
			waited[0]++;
			if (waited[0] < 100) {
				return; // (waiting for a good while, not just a moment)
			}
			if (!restarted[0]) {
				String status = Builders.statusText(u.level(), site, u.villager()).getString();
				helper.assertTrue(status.contains("Needs") && status.contains(new ItemStack(shortItem[0]).getHoverName().getString()),
					"waiting short of " + shortItem[0] + ", the status reads: " + status);
				int missing = site.missing().getOrDefault(shortItem[0], 0);
				helper.assertTrue(missing > 0 && missing <= withheld[0], "short of " + withheld[0] + " " + shortItem[0] + ", the site is missing " + site.missing());
				helper.assertTrue(site.missing().keySet().stream().allMatch(i -> i == shortItem[0]), "only " + shortItem[0] + " was withheld, the site is missing "
					+ site.missing());
				progress[0] = site.placed();
				int cursor = site.cursor();
				BuildPlan.Stage stage = site.stage();
				Map<Item, Integer> heldBefore = held(helper, u);
				restart(helper, u);
				BuildSite back = u.live();
				helper.assertTrue(back != null && back.stage() == stage && back.cursor() == cursor && back.placed() == progress[0],
					"a restart while waiting moved the upgrade from " + stage + " cursor " + cursor + " placed " + progress[0] + " to " + where(u));
				helper.assertTrue(held(helper, u).equals(heldBefore), "a restart while waiting changed the items held from " + heldBefore + " to " + held(helper, u));
				restarted[0] = true;
				waited[0] = 0;
				return;
			}
			// waiting again after the restart: same shortfall, no progress lost; then they are brought
			helper.assertTrue(site.placed() >= progress[0], "after the restart the upgrade's progress dropped from " + progress[0] + " to " + site.placed());
			Container chest = helper.getBlockEntity(CHEST_B);
			for (int i = 0; i < chest.getContainerSize(); i++) {
				if (chest.getItem(i).isEmpty()) {
					chest.setItem(i, new ItemStack(shortItem[0], withheld[0]));
					break;
				}
			}
			supplied[0] = true;
		});
		helper.succeedWhen(() -> {
			helper.assertTrue(restarted[0], "the upgrade never waited for its " + shortItem[0] + ": " + where(u));
			helper.assertTrue(supplied[0], "the upgrade never waited again after the restart: " + where(u));
			assertUpgraded(helper, u, plan[0]);
		});
	}

	/**
	 * The bench broken by a player in the middle of the upgrade's STRUCTURE: the upgrade's site and its progress stay
	 * (never removed, never back to the start: "Progress never drops"), nothing it was stocked with is lost or doubled
	 * (what the Builder carries stays in his bag or comes back); with the bench put back and the Builder at it again, he
	 * finishes the Cottage II as drawn.
	 */
	//$ gametest_ticks_batch BIG_AREA '12000' '"qaUpg1116bBench"'
	@GameTest(template = BIG_AREA, timeoutTicks = 12000, batch = "qaUpg1116bBench")
	public void theBenchBrokenMidUpgradeKeepsItsProgressAndItFinishesWhenTheBenchIsBack(GameTestHelper helper) {
		Up u = setup(helper, 0, new Item[1], new int[1]);
		BuildPlan[] plan = {null};
		int[] ticks = {0};
		int[] brokeAt = {-1};
		int[] placedAt = {-1};
		Map<Item, Integer>[] heldAt = new Map[1];
		boolean[] back = {false};
		helper.onEachTick(() -> {
			ticks[0]++;
			helper.setDayTime(2000);
			if (ticks[0] < 3) {
				return;
			}
			if (plan[0] == null) {
				plan[0] = order(helper, u).plan(u.level());
				return;
			}
			BuildSite site = u.live();
			if (brokeAt[0] < 0) {
				if (site != null && site.stage() == BuildPlan.Stage.STRUCTURE && site.placed() >= 60) {
					placedAt[0] = site.placed();
					heldAt[0] = held(helper, u);
					u.level().destroyBlock(helper.absolutePos(BENCH), false); // (the bench itself not dropped: only the materials are counted)
					brokeAt[0] = ticks[0];
				}
				return;
			}
			if (back[0]) {
				return;
			}
			helper.assertTrue(site != null && site.structure().equals(COTTAGE_2), "the bench broken mid-upgrade removed the upgrade's site (placed "
				+ placedAt[0] + " of it)");
			helper.assertTrue(site.placed() >= placedAt[0], "the bench broken mid-upgrade dropped its progress from " + placedAt[0] + " to " + site.placed());
			if (ticks[0] - brokeAt[0] >= 200) {
				// nothing lost or doubled while he had no bench: every item stocked is still held or now stands in the build
				Map<Item, Integer> now = held(helper, u);
				for (Map.Entry<Item, Integer> e : now.entrySet()) {
					int before = heldAt[0].getOrDefault(e.getKey(), 0);
					helper.assertTrue(e.getValue() <= before || e.getKey() == ModBlocks.BUILDERS_BENCH.asItem(),
						"with the bench broken " + e.getKey() + " went up from " + before + " to " + e.getValue() + " (doubled)");
				}
				u.level().getServer().sendSystemMessage(net.minecraft.network.chat.Component.literal("[qa-upgrade-1116b] 200 ticks after the bench broke at placed "
					+ placedAt[0] + ": " + where(u)));
				helper.setBlock(BENCH, ModBlocks.BUILDERS_BENCH);
				Builders.employ(u.level(), u.villager(), helper.absolutePos(BENCH));
				back[0] = true;
			}
		});
		helper.succeedWhen(() -> {
			helper.assertTrue(back[0], "the bench isn't back yet: " + where(u));
			assertUpgraded(helper, u, plan[0]);
		});
	}
}
