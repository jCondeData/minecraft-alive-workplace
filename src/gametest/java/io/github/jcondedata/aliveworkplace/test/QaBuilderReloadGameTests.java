package io.github.jcondedata.aliveworkplace.test;

import io.github.jcondedata.aliveworkplace.blueprint.BlueprintData;
import io.github.jcondedata.aliveworkplace.build.BuildPlan;
import io.github.jcondedata.aliveworkplace.build.BuildSite;
import io.github.jcondedata.aliveworkplace.build.BuildSiteManager;
import io.github.jcondedata.aliveworkplace.build.BuilderBag;
import io.github.jcondedata.aliveworkplace.build.Builders;
import io.github.jcondedata.aliveworkplace.hall.UpgradesPage;
import io.github.jcondedata.aliveworkplace.hall.VillageHalls;
import io.github.jcondedata.aliveworkplace.registry.ModAttachments;
import io.github.jcondedata.aliveworkplace.registry.ModBlocks;
import io.github.jcondedata.aliveworkplace.registry.ModGameRules;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.Container;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings;

/**
 * QA, from the spec (docs/wiki/builder.md "How it works": "the record is saved, so a Builder can stop at any tick (server
 * stopped, chunk unloaded, Builder asleep) and carry on exactly where it was"; CLAUDE.md "BuilderWork must stay
 * restartable at any tick", "FOUNDATION and LANDSCAPE ... restart that list", "Don't break existing saves"): a server
 * restart (every site and the Builder saved and loaded) in the middle of CLEAR, DECORATION and LANDSCAPE, with the
 * Builder's bag holding what he carries; in the middle of an upgrade (level I to II); in the middle of taking a building
 * down; and a site saved before the newer optional fields existed. Each ends as drawn, with nothing lost or doubled.
 */
public class QaBuilderReloadGameTests {
	private static final String AREA = "aliveworkplace_test:build_area";
	private static final String BIG_AREA = "aliveworkplace_test:big_area";
	private static final ResourceLocation TEST_HUT = ResourceLocation.fromNamespaceAndPath("aliveworkplace_test", "test_hut");
	private static final ResourceLocation HUT_2 = ResourceLocation.fromNamespaceAndPath("aliveworkplace_test", "test_hut_2");

	private static final BlockPos BENCH = new BlockPos(2, 2, 2);
	private static final BlockPos CHEST = new BlockPos(2, 2, 4);
	private static final BlockPos VILLAGER = new BlockPos(3, 2, 3);
	/** The hut stands at x 6-10, y 2-5, z 6-10. */
	private static final BlockPos HUT_ORIGIN = new BlockPos(6, 2, 6);
	private static final List<Item> HUT_ITEMS = List.of(Items.COBBLESTONE, Items.OAK_PLANKS, Items.OAK_DOOR, Items.TORCH);

	private record Run(ServerLevel level, Villager[] builder, UUID site, BuildPlan plan, BlueprintData.Placement placement) {
		Villager villager() {
			return builder[0];
		}

		BuildSite live() {
			return BuildSiteManager.get(level).get(site);
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
		Leftovers.after(helper, () -> {
			level.getGameRules().getRule(ModGameRules.FREE_MATERIALS).set(free, level.getServer());
			level.getGameRules().getRule(ModGameRules.BUILD_DELAY).set(delay, level.getServer());
			level.getGameRules().getRule(ModGameRules.BUILDERS_HELP).set(help, level.getServer());
		});
		return level;
	}

	private static ItemStack[] hutMaterials() {
		return new ItemStack[]{new ItemStack(Items.COBBLESTONE, 25), new ItemStack(Items.OAK_PLANKS, 55), new ItemStack(Items.OAK_DOOR),
			new ItemStack(Items.TORCH)};
	}

	/** A Builder at his bench, the chest beside it holding {@code chestItems}, starting the test hut. */
	private static Run start(GameTestHelper helper, ItemStack... chestItems) {
		ServerLevel level = helper.getLevel();
		helper.setBlock(BENCH, ModBlocks.BUILDERS_BENCH);
		helper.setBlock(CHEST, Blocks.CHEST);
		Container chest = helper.getBlockEntity(CHEST);
		for (int i = 0; i < chestItems.length; i++) {
			chest.setItem(i, chestItems[i].copy());
		}
		Villager villager = helper.spawn(EntityType.VILLAGER, VILLAGER);
		Builders.employ(level, villager, helper.absolutePos(BENCH));
		BlueprintData.Placement placement = new BlueprintData.Placement(level.dimension().location(), helper.absolutePos(HUT_ORIGIN), Rotation.NONE, Mirror.NONE);
		BuildSite site = Builders.start(level, villager, null, TEST_HUT, placement);
		BuildPlan plan = site.plan(level);
		helper.assertTrue(plan != null, "no test hut blueprint");
		return new Run(level, new Villager[]{villager}, site.id(), plan, placement);
	}

	/** A server restart: every build site and the Builder saved and loaded again (the Builder as a fresh entity). */
	private static void restart(GameTestHelper helper, ServerLevel level, Villager[] builder) {
		var registries = level.registryAccess();
		level.getDataStorage().set(BuildSiteManager.NAME, BuildSiteManager.load(BuildSiteManager.get(level).save(new CompoundTag(), registries), registries));
		restartVillager(helper, level, builder);
	}

	private static void restartVillager(GameTestHelper helper, ServerLevel level, Villager[] builder) {
		Villager old = builder[0];
		CompoundTag tag = new CompoundTag();
		helper.assertTrue(old.save(tag), "the builder wasn't saved");
		old.discard();
		Entity loaded = EntityType.loadEntityRecursive(tag, level, e -> e);
		helper.assertTrue(loaded instanceof Villager, "the builder didn't load as a villager: " + loaded);
		level.addFreshEntity(loaded);
		builder[0] = (Villager) loaded;
	}

	private static Map<Item, Integer> bag(Villager villager) {
		Map<Item, Integer> out = new LinkedHashMap<>();
		BuilderBag bag = ModAttachments.BUILDER_BAG.getOrCreate(villager);
		for (ItemStack stack : bag.stacks()) {
			if (!stack.isEmpty()) {
				out.merge(stack.getItem(), stack.getCount(), Integer::sum);
			}
		}
		return out;
	}

	/** The hut's materials not yet in the world: in the chest plus in the Builder's bag. */
	private static Map<Item, Integer> held(GameTestHelper helper, Villager villager) {
		Container chest = helper.getBlockEntity(CHEST);
		Map<Item, Integer> bag = bag(villager);
		Map<Item, Integer> out = new LinkedHashMap<>();
		for (Item item : HUT_ITEMS) {
			out.put(item, chest.countItem(item) + bag.getOrDefault(item, 0));
		}
		return out;
	}

	private static String where(GameTestHelper helper, Run r) {
		BuildSite site = r.live();
		return site == null ? "(no site)" : site.stage() + " " + site.status() + " cursor " + site.cursor() + " skipped " + site.skipped()
			+ " missing " + site.missing() + "; builder at " + helper.relativePos(r.villager().blockPosition()) + " alive " + r.villager().isAlive()
			+ " bag " + bag(r.villager());
	}

	private static void assertBuilt(GameTestHelper helper, Run r) {
		helper.assertTrue(r.live() == null, "still building after the restart: " + where(helper, r));
		List<BlockPos> wrong = r.plan().unfinished(r.level());
		helper.assertTrue(wrong.isEmpty(), wrong.size() + " block(s) of the hut wrong after the restart, e.g. " + wrong.stream().limit(6)
			.map(p -> p.subtract(r.placement().origin()).toShortString() + "=" + r.level().getBlockState(p)).collect(Collectors.joining(", ")));
	}

	/**
	 * Restarts the server once, the first tick the site is in {@code stage} with something in the Builder's bag (or, if
	 * his bag stays empty, {@code fallback} ticks into the stage or half through its list), and checks the restart itself kept the bag and the
	 * stage as they were. Then the hut must finish exactly from the chest: nothing of its materials left over (doubled)
	 * and nothing short (lost: the build would wait).
	 */
	private static void restartIn(GameTestHelper helper, Run r, BuildPlan.Stage stage, int fallback) {
		restartIn(helper, r, stage, fallback, () -> {
		});
	}

	private static void restartIn(GameTestHelper helper, Run r, BuildPlan.Stage stage, int fallback, Runnable alsoAtTheEnd) {
		int[] inStage = {0};
		String[] restarted = {null};
		helper.onEachTick(() -> {
			BuildSite site = r.live();
			if (restarted[0] != null || site == null || site.stage() != stage) {
				return;
			}
			inStage[0]++;
			Map<Item, Integer> bagBefore = bag(r.villager());
			if (bagBefore.isEmpty() && inStage[0] < fallback && site.cursor() * 2 < r.plan().steps(stage).size()) {
				return;
			}
			Map<Item, Integer> heldBefore = held(helper, r.villager());
			int cursor = site.cursor();
			restart(helper, r.level(), r.builder());
			BuildSite back = r.live();
			helper.assertTrue(back != null && back.stage() == stage, "after a restart in " + stage + " the site is " + (back == null ? "gone" : back.stage()));
			helper.assertTrue(stage == BuildPlan.Stage.LANDSCAPE || stage == BuildPlan.Stage.FOUNDATION || back.cursor() == cursor,
				"a restart in " + stage + " moved the cursor from " + cursor + " to " + back.cursor());
			Map<Item, Integer> bagAfter = bag(r.villager());
			helper.assertTrue(bagAfter.equals(bagBefore), "a restart in " + stage + " changed the Builder's bag from " + bagBefore + " to " + bagAfter);
			helper.assertTrue(held(helper, r.villager()).equals(heldBefore), "a restart in " + stage + " changed the materials held from " + heldBefore
				+ " to " + held(helper, r.villager()));
			restarted[0] = "bag " + bagBefore + ", " + inStage[0] + " ticks into " + stage + ", cursor " + cursor + " of " + r.plan().steps(stage).size();
			r.level().getServer().sendSystemMessage(net.minecraft.network.chat.Component.literal("[qa-reload] restarted with " + restarted[0]));
		});
		helper.succeedWhen(() -> {
			helper.assertTrue(restarted[0] != null, "the build never reached " + stage + " for a restart: " + where(helper, r));
			assertBuilt(helper, r);
			Map<Item, Integer> left = held(helper, r.villager());
			left.values().removeIf(n -> n == 0);
			helper.assertTrue(left.isEmpty(), "after a restart in " + stage + " the hut is built but its materials are still held (doubled): " + left
				+ " (" + restarted[0] + ")");
			alsoAtTheEnd.run();
		});
	}

	// ------------------------------------------------------------------------------------------------- the stages

	/** Dirt in the hut's way (two layers of its floor plan): a restart while clearing it (with dirt in his bag, or halfway). */
	//$ gametest_ticks_batch AREA '4000' '"qaReloadClear"'
	@GameTest(template = AREA, timeoutTicks = 4000, batch = "qaReloadClear")
	public void aRestartMidClearingFinishesTheHutFromTheChest(GameTestHelper helper) {
		rules(helper);
		for (int x = 7; x <= 9; x++) {
			for (int z = 7; z <= 9; z++) {
				helper.setBlock(new BlockPos(x, 3, z), Blocks.DIRT);
				helper.setBlock(new BlockPos(x, 4, z), Blocks.DIRT);
			}
		}
		Run r = start(helper, hutMaterials());
		helper.assertTrue(!r.plan().steps(BuildPlan.Stage.CLEAR).isEmpty(), "nothing in the hut's way to clear");
		restartIn(helper, r, BuildPlan.Stage.CLEAR, Integer.MAX_VALUE);
	}

	/** A restart while the door and torch go up, the Builder carrying them. */
	//$ gametest_ticks_batch AREA '4000' '"qaReloadDecoration"'
	@GameTest(template = AREA, timeoutTicks = 4000, batch = "qaReloadDecoration")
	public void aRestartMidDecorationFinishesTheHutFromTheChest(GameTestHelper helper) {
		rules(helper);
		Run r = start(helper, hutMaterials());
		helper.assertTrue(!r.plan().steps(BuildPlan.Stage.DECORATION).isEmpty(), "the hut has nothing to decorate");
		restartIn(helper, r, BuildPlan.Stage.DECORATION, 4);
	}

	/** Mounds of dirt round the hut: a restart while they are dug away (LANDSCAPE makes its list again from the terrain). */
	//$ gametest_ticks_batch AREA '4000' '"qaReloadLandscape"'
	@GameTest(template = AREA, timeoutTicks = 4000, batch = "qaReloadLandscape")
	public void aRestartMidLandscapingStillLevelsTheGround(GameTestHelper helper) {
		rules(helper);
		List<BlockPos> mounds = List.of(new BlockPos(5, 2, 8), new BlockPos(11, 2, 8), new BlockPos(8, 2, 11), new BlockPos(5, 2, 10), new BlockPos(11, 2, 6));
		for (BlockPos p : mounds) {
			helper.setBlock(p, Blocks.DIRT);
		}
		Run r = start(helper, hutMaterials());
		long planned = r.plan().steps(BuildPlan.Stage.LANDSCAPE).stream().filter(s -> mounds.stream().anyMatch(m -> helper.absolutePos(m).equals(s.pos()))).count();
		helper.assertTrue(planned == mounds.size(), "only " + planned + " of the " + mounds.size() + " mounds are in the landscaping plan");
		// The ground: every mound dug away once the build is done.
		restartIn(helper, r, BuildPlan.Stage.LANDSCAPE, 3, () -> {
			for (BlockPos p : mounds) {
				helper.assertTrue(helper.getBlockState(p).isAir(), "after a restart mid-landscaping the mound at " + p.toShortString() + " is still "
					+ helper.getBlockState(p));
			}
		});
	}

	// ------------------------------------------------------------------------------------------------- an upgrade

	/**
	 * The Village Hall's upgrade of a finished hut (level I to II), the server restarted twice while it is built: it ends
	 * as level II, recorded so, with every block of the old hut still standing and the new lantern up.
	 */
	//$ gametest_ticks_batch BIG_AREA '3000' '"qaReloadUpgrade"'
	@GameTest(template = BIG_AREA, timeoutTicks = 3000, batch = "qaReloadUpgrade")
	public void anUpgradeRestartedMidBuildEndsAsLevelTwo(GameTestHelper helper) {
		ServerLevel level = rules(helper);
		int radius = VillageHalls.RADIUS;
		VillageHalls.RADIUS = 16;
		BlockPos hallRel = new BlockPos(15, 2, 11);
		BlockPos hutRel = new BlockPos(3, 2, 3);
		BlockPos lantern = new BlockPos(5, 6, 5);
		BlueprintData.Placement hut = new BlueprintData.Placement(level.dimension().location(), helper.absolutePos(hutRel), Rotation.NONE, Mirror.NONE);
		Leftovers.after(helper, () -> {
			VillageHalls.RADIUS = radius;
			BuildSiteManager sites = BuildSiteManager.get(level);
			sites.forgetFinished(hut);
			for (BuildSite site : List.copyOf(sites.all())) {
				if (site.placement().equals(hut)) {
					sites.remove(site.id());
				}
			}
		});
		// (a site left at this spot in the test world by an earlier, interrupted run would read as "already building")
		for (BuildSite stale : List.copyOf(BuildSiteManager.get(level).all())) {
			if (stale.placement().equals(hut)) {
				BuildSiteManager.get(level).remove(stale.id());
			}
		}
		BuildSiteManager.get(level).forgetFinished(hut);
		helper.setBlock(hallRel, ModBlocks.VILLAGE_HALL);
		BlockPos origin = helper.absolutePos(hutRel);
		level.getStructureManager().get(TEST_HUT).orElseThrow().placeInWorld(level, origin, origin, new StructurePlaceSettings(), RandomSource.create(1L), 2);
		BuildSiteManager.get(level).recordFinished(TEST_HUT, hut, UUID.randomUUID());
		BlockPos bench = new BlockPos(13, 2, 3);
		BlockPos chestPos = new BlockPos(13, 2, 5);
		helper.setBlock(bench, ModBlocks.BUILDERS_BENCH);
		helper.setBlock(chestPos, Blocks.CHEST);
		Container chest = helper.getBlockEntity(chestPos);
		chest.setItem(0, new ItemStack(Items.LANTERN, 4));
		Villager[] builder = {helper.spawn(EntityType.VILLAGER, new BlockPos(12, 2, 4))};
		Builders.employ(level, builder[0], helper.absolutePos(bench));
		ServerPlayer player = helper.makeMockServerPlayerInLevel();
		Predicate<BuildSite> upgrade = s -> s.placement().equals(hut) && !s.isDone();
		int[] restarts = {0};
		int[] ticks = {0};
		BuildPlan[] planOf = {null};
		helper.onEachTick(() -> {
			ticks[0]++;
			if (ticks[0] < 3) {
				return; // (the hall and the Builder settle in first)
			}
			if (planOf[0] == null) {
				// (the village may already have taken the upgrade up on its own; otherwise it is ordered from the hall)
				if (BuildSiteManager.get(level).all().stream().noneMatch(upgrade)) {
					UpgradesPage.Outcome outcome = UpgradesPage.order(level, helper.absolutePos(hallRel), player, hut);
					helper.assertTrue(outcome == UpgradesPage.Outcome.STARTED, "the upgrade wasn't ordered: " + outcome);
				}
				BuildSite site = BuildSiteManager.get(level).all().stream().filter(upgrade).findFirst().orElseThrow();
				helper.assertTrue(site.structure().equals(HUT_2), "the upgrade builds " + site.structure());
				planOf[0] = site.plan(level);
				return;
			}
			BuildSite live = BuildSiteManager.get(level).all().stream().filter(upgrade).findFirst().orElse(null);
			if (live == null) {
				return;
			}
			// once straight away, once with the lantern in his bag (or a while on, if he never carries it)
			boolean carrying = bag(builder[0]).getOrDefault(Items.LANTERN, 0) > 0;
			if (restarts[0] == 0 || restarts[0] == 1 && (carrying || ticks[0] >= 40)) {
				restarts[0]++;
				restart(helper, level, builder);
				BuildSite back = BuildSiteManager.get(level).all().stream().filter(upgrade).findFirst().orElse(null);
				helper.assertTrue(back != null && back.structure().equals(HUT_2) && builder[0].getUUID().equals(back.builder()),
					"after restart " + restarts[0] + " the upgrade's site is " + back);
				level.getServer().sendSystemMessage(net.minecraft.network.chat.Component.literal("[qa-reload-upgrade] restart " + restarts[0] + " at tick "
					+ ticks[0] + " in " + back.stage() + ", carrying the lantern " + carrying));
				helper.assertTrue(BuildSiteManager.get(level).finishedIn(level).stream().anyMatch(f -> f.placement().equals(hut) && f.structure().equals(TEST_HUT)),
					"after restart " + restarts[0] + " the hut isn't there as level I while its upgrade is built");
			}
		});
		helper.succeedWhen(() -> {
			helper.assertTrue(planOf[0] != null, "the upgrade never started");
			helper.assertTrue(restarts[0] == 2, "only " + restarts[0] + " restart(s) before the upgrade ended");
			BuildSite live = BuildSiteManager.get(level).all().stream().filter(upgrade).findFirst().orElse(null);
			helper.assertTrue(live == null, "still upgrading after the restarts: " + (live == null ? "" : live.stage() + " " + live.status() + " missing " + live.missing()));
			helper.assertTrue(BuildSiteManager.get(level).finishedIn(level).stream().anyMatch(f -> f.placement().equals(hut) && f.structure().equals(HUT_2)),
				"after the restarts the hut isn't recorded as its level II");
			helper.assertBlockPresent(Blocks.LANTERN, lantern);
			List<BlockPos> wrong = planOf[0].unfinished(level);
			helper.assertTrue(wrong.isEmpty(), wrong.size() + " block(s) of the level II (the old hut's included) wrong after the restarts, e.g. "
				+ wrong.stream().limit(6).map(p -> p.subtract(origin).toShortString() + "=" + level.getBlockState(p)).collect(Collectors.joining(", ")));
		});
	}

	// ------------------------------------------------------------------------------------------------- taking down

	/**
	 * A hut being taken down, the server restarted when about half of it is down (the Builder carrying what he took):
	 * it still comes down whole, and the chest ends with exactly the hut's blocks, nothing lost or doubled.
	 */
	//$ gametest_ticks_batch AREA '4000' '"qaReloadTakeDown"'
	@GameTest(template = AREA, timeoutTicks = 4000, batch = "qaReloadTakeDown")
	public void aRestartMidTakeDownStillReturnsExactlyTheHut(GameTestHelper helper) {
		ServerLevel level = rules(helper);
		BlockPos origin = helper.absolutePos(HUT_ORIGIN);
		level.getStructureManager().get(TEST_HUT).orElseThrow().placeInWorld(level, origin, origin, new StructurePlaceSettings(), RandomSource.create(1L),
			net.minecraft.world.level.block.Block.UPDATE_ALL);
		Run r = start(helper);
		r.live().setDeconstruction();
		BuildPlan plan = r.live().plan(level);
		helper.assertTrue(plan.isDeconstruction(), "the site isn't taking the hut down");
		int standing = plan.placeableCount();
		String[] restarted = {null};
		helper.onEachTick(() -> {
			BuildSite site = r.live();
			if (restarted[0] != null || site == null) {
				return;
			}
			int left = plan.unfinished(level).size();
			if (left > standing / 2 || bag(r.villager()).isEmpty()) {
				return;
			}
			Map<Item, Integer> heldBefore = held(helper, r.villager());
			Map<Item, Integer> bagBefore = bag(r.villager());
			restart(helper, level, r.builder());
			BuildSite back = r.live();
			helper.assertTrue(back != null && back.isDeconstruction(), "after a restart mid-take-down the site is " + (back == null ? "gone" : "building, not taking down"));
			helper.assertTrue(bag(r.villager()).equals(bagBefore), "a restart mid-take-down changed the bag from " + bagBefore + " to " + bag(r.villager()));
			helper.assertTrue(held(helper, r.villager()).equals(heldBefore), "a restart mid-take-down changed what is held from " + heldBefore);
			restarted[0] = left + " of " + standing + " left, bag " + bagBefore;
			level.getServer().sendSystemMessage(net.minecraft.network.chat.Component.literal("[qa-reload-takedown] restarted with " + restarted[0]));
		});
		helper.succeedWhen(() -> {
			helper.assertTrue(restarted[0] != null, "never half down with something in the bag: " + plan.unfinished(level).size() + " of " + standing
				+ " left; " + where(helper, r));
			helper.assertTrue(r.live() == null, "still taking it down after the restart: " + where(helper, r));
			helper.assertTrue(plan.unfinished(level).isEmpty(), plan.unfinished(level).size() + " block(s) of the hut still standing after the restart");
			Container chest = helper.getBlockEntity(CHEST);
			Map<Item, Integer> bagNow = bag(r.villager());
			helper.assertTrue(chest.countItem(Items.COBBLESTONE) == 25 && chest.countItem(Items.OAK_PLANKS) == 55 && chest.countItem(Items.OAK_DOOR) == 1
					&& chest.countItem(Items.TORCH) == 1 && bagNow.isEmpty(),
				"after a restart mid-take-down (" + restarted[0] + ") the chest has " + chest.countItem(Items.COBBLESTONE) + " cobblestone, "
					+ chest.countItem(Items.OAK_PLANKS) + " planks, " + chest.countItem(Items.OAK_DOOR) + " door, " + chest.countItem(Items.TORCH)
					+ " torch (wanted 25, 55, 1, 1: what the hut is drawn with, as its build takes) and the bag " + bagNow);
		});
	}

	// ------------------------------------------------------------------------------------------------- an old save

	/**
	 * A site saved mid-walls by an older version, before the newer optional fields (its bench, shown progress, waiting
	 * time, scaffolds, given-up blocks, furniture, build order, levelling switch, cellar flag): it loads with their
	 * defaults and the hut still finishes as drawn from the chest, nothing doubled.
	 */
	//$ gametest_ticks_batch AREA '4000' '"qaReloadOldSave"'
	@GameTest(template = AREA, timeoutTicks = 4000, batch = "qaReloadOldSave")
	public void aSiteFromAnOlderSaveLoadsWithDefaultsAndFinishes(GameTestHelper helper) {
		ServerLevel level = rules(helper);
		Run r = start(helper, hutMaterials());
		List<String> newer = List.of("shown_progress", "bench", "waiting_since", "player_blocks", "scaffolds", "gave_up", "kinetic_order", "kinetic_passes",
			"furniture_unchosen", "furniture", "cellar_only", "no_level_ground", "no_foundation");
		String[] restarted = {null};
		helper.onEachTick(() -> {
			BuildSite site = r.live();
			if (restarted[0] != null || site == null || site.stage() != BuildPlan.Stage.STRUCTURE || site.cursor() < 10) {
				return;
			}
			int cursor = site.cursor();
			Map<Item, Integer> heldBefore = held(helper, r.villager());
			var registries = level.registryAccess();
			CompoundTag tag = BuildSiteManager.get(level).save(new CompoundTag(), registries);
			ListTag list = tag.getList("sites", net.minecraft.nbt.Tag.TAG_COMPOUND);
			boolean found = false;
			for (int i = 0; i < list.size(); i++) {
				CompoundTag s = list.getCompound(i);
				if (s.hasUUID("id") && s.getUUID("id").equals(r.site())) {
					found = true;
					newer.forEach(s::remove);
				}
			}
			helper.assertTrue(found, "the site isn't in the save");
			level.getDataStorage().set(BuildSiteManager.NAME, BuildSiteManager.load(tag, registries));
			restartVillager(helper, level, r.builder());
			BuildSite back = r.live();
			helper.assertTrue(back != null && back.stage() == BuildPlan.Stage.STRUCTURE && back.cursor() == cursor && back.levelGround(),
				"the old save loaded as " + (back == null ? "nothing" : back.stage() + " cursor " + back.cursor() + " (was " + cursor + ") level ground " + back.levelGround()));
			helper.assertTrue(held(helper, r.villager()).equals(heldBefore), "the old save's restart changed what is held from " + heldBefore);
			restarted[0] = "cursor " + cursor;
		});
		helper.succeedWhen(() -> {
			helper.assertTrue(restarted[0] != null, "the build never got 10 steps into its walls: " + where(helper, r));
			assertBuilt(helper, r);
			Map<Item, Integer> left = held(helper, r.villager());
			left.values().removeIf(n -> n == 0);
			helper.assertTrue(left.isEmpty(), "after loading an old save the hut is built but its materials are still held (doubled): " + left);
		});
	}
}
