package io.github.jcondedata.aliveworkplace.test;

import io.github.jcondedata.aliveworkplace.blueprint.BlueprintData;
import io.github.jcondedata.aliveworkplace.build.BuildPlan;
import io.github.jcondedata.aliveworkplace.build.BuildSite;
import io.github.jcondedata.aliveworkplace.build.BuildSiteManager;
import io.github.jcondedata.aliveworkplace.build.BuilderBag;
import io.github.jcondedata.aliveworkplace.build.Builders;
import io.github.jcondedata.aliveworkplace.build.Employer;
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
import java.util.stream.Collectors;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
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
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.phys.AABB;

/**
 * QA (2026-11-02), from docs/wiki/builder.md section 3 ("Switches": workplaceFreeMaterials, workplaceBuildDelay "8 (min
 * 1)", workplaceFoundationDepth, workplaceLevelGround "2 (0 to 8)"), section 1 ("The materials come from chests and
 * barrels near the bench ... If the chests run short, the Builder waits and tells you what is missing"; "the village (a
 * storehouse ...)") and CLAUDE.md ("BuilderWork must stay restartable at any tick"; "FOUNDATION and LANDSCAPE depend on
 * the terrain, so a site loaded mid-stage restarts that list; steps already done are skipped"). Corners the older tests
 * leave out: a restart in the middle of FOUNDATION (with the rule changed across it), workplaceLevelGround at its first
 * step (1) across a restart, the build delay at its minimum, free materials switched on and off in the middle of
 * STRUCTURE, and a Builder with no chest of his own fed only by the Storehouse running short mid-build, with a restart
 * while he waits. Each ends with the build as drawn and every material accounted for (nothing lost, nothing doubled).
 */
public class QaBuilderConfig1102GameTests {
	static final String HUGE = "aliveworkplace_test:huge_area";
	static final String AREA = "aliveworkplace_test:build_area";
	static final ResourceLocation TEST_HUT = ResourceLocation.fromNamespaceAndPath("aliveworkplace_test", "test_hut");
	static final List<Item> HUT_ITEMS = List.of(Items.COBBLESTONE, Items.OAK_PLANKS, Items.OAK_DOOR, Items.TORCH);
	private static final UUID JESSE = UUID.fromString("5f1b2c3d-0000-4000-8000-0000000b1102");

	/** On the lawn tests (huge area): grass at y 3, dirt at y 2, the hut's floor at y 4. */
	private static final int LAWN = 3;
	private static final BlockPos LAWN_BENCH = new BlockPos(2, LAWN + 1, 2);
	private static final BlockPos LAWN_CHEST = new BlockPos(2, LAWN + 1, 4);
	/** The hut on the lawn: x 12..16, z 12..16. */
	private static final BlockPos LAWN_HUT = new BlockPos(12, LAWN + 1, 12);

	/** Flat build area tests. */
	static final BlockPos BENCH = new BlockPos(2, 2, 2);
	static final BlockPos CHEST = new BlockPos(2, 2, 4);
	static final BlockPos HUT = new BlockPos(6, 2, 6);

	/** Storehouse tests (huge area): the bench alone at (2,2,2), the Storehouse far off with its two chests. */
	private static final BlockPos STOREHOUSE = new BlockPos(26, 2, 26);
	private static final BlockPos[] STORE_CHESTS = {new BlockPos(23, 2, 26), new BlockPos(26, 2, 23)};

	record Run(ServerLevel level, Villager[] builder, UUID site, BuildPlan plan, BlueprintData.Placement placement, BlockPos chest) {
		Villager villager() {
			return builder[0];
		}

		BuildSite live() {
			return BuildSiteManager.get(level).get(site);
		}
	}

	// ------------------------------------------------------------------------------------------------- helpers

	static ServerLevel rules(GameTestHelper helper, boolean free, int delay) {
		Leftovers.clear(helper);
		ServerLevel level = helper.getLevel();
		helper.setDayTime(2000);
		boolean wasFree = level.getGameRules().getBoolean(ModGameRules.FREE_MATERIALS);
		int wasDelay = level.getGameRules().getInt(ModGameRules.BUILD_DELAY);
		boolean help = level.getGameRules().getBoolean(ModGameRules.BUILDERS_HELP);
		int depth = level.getGameRules().getInt(ModGameRules.FOUNDATION_DEPTH);
		int margin = level.getGameRules().getInt(ModGameRules.LEVEL_GROUND);
		Leftovers.after(helper, () -> {
			level.getGameRules().getRule(ModGameRules.FREE_MATERIALS).set(wasFree, level.getServer());
			level.getGameRules().getRule(ModGameRules.BUILD_DELAY).set(wasDelay, level.getServer());
			level.getGameRules().getRule(ModGameRules.BUILDERS_HELP).set(help, level.getServer());
			level.getGameRules().getRule(ModGameRules.FOUNDATION_DEPTH).set(depth, level.getServer());
			level.getGameRules().getRule(ModGameRules.LEVEL_GROUND).set(margin, level.getServer());
		});
		level.getGameRules().getRule(ModGameRules.FREE_MATERIALS).set(free, level.getServer());
		level.getGameRules().getRule(ModGameRules.BUILD_DELAY).set(delay, level.getServer());
		level.getGameRules().getRule(ModGameRules.BUILDERS_HELP).set(false, level.getServer());
		return level;
	}

	static ItemStack[] hutMaterials() {
		return new ItemStack[]{new ItemStack(Items.COBBLESTONE, 25), new ItemStack(Items.OAK_PLANKS, 55), new ItemStack(Items.OAK_DOOR),
			new ItemStack(Items.TORCH)};
	}

	/** A Builder at his bench with a chest beside it holding {@code items}, starting the test hut at {@code hut}. */
	static Run start(GameTestHelper helper, BlockPos bench, BlockPos chestAt, BlockPos hut, ItemStack... items) {
		ServerLevel level = helper.getLevel();
		helper.setBlock(bench, ModBlocks.BUILDERS_BENCH);
		if (chestAt != null) {
			helper.setBlock(chestAt, Blocks.CHEST);
			Container chest = helper.getBlockEntity(chestAt);
			for (int i = 0; i < items.length; i++) {
				chest.setItem(i, items[i].copy());
			}
		}
		Villager villager = helper.spawn(EntityType.VILLAGER, bench.east());
		Builders.employ(level, villager, helper.absolutePos(bench));
		ModAttachments.BUILDER_EMPLOYER.set(villager, new Employer(JESSE, "Jesse"));
		BlueprintData.Placement placement = new BlueprintData.Placement(level.dimension().location(), helper.absolutePos(hut), Rotation.NONE, Mirror.NONE);
		BuildSite site = Builders.start(level, villager, JESSE, "Jesse", TEST_HUT, placement);
		helper.assertTrue(site != null, "the hut didn't start");
		BuildPlan plan = site.plan(level);
		helper.assertTrue(plan != null, "no test hut blueprint");
		return new Run(level, new Villager[]{villager}, site.id(), plan, placement, chestAt);
	}

	/** A server restart: every build site and the Builder saved and loaded again (the Builder as a fresh entity). */
	static void restart(GameTestHelper helper, Run r) {
		ServerLevel level = r.level();
		var registries = level.registryAccess();
		level.getDataStorage().set(BuildSiteManager.NAME, BuildSiteManager.load(BuildSiteManager.get(level).save(new CompoundTag(), registries), registries));
		Villager old = r.builder()[0];
		CompoundTag tag = new CompoundTag();
		helper.assertTrue(old.save(tag), "the builder wasn't saved");
		old.discard();
		Entity loaded = EntityType.loadEntityRecursive(tag, level, e -> e);
		helper.assertTrue(loaded instanceof Villager, "the builder didn't load as a villager: " + loaded);
		level.addFreshEntity(loaded);
		r.builder()[0] = (Villager) loaded;
	}

	static Map<Item, Integer> bag(Villager villager) {
		Map<Item, Integer> out = new LinkedHashMap<>();
		BuilderBag bag = ModAttachments.BUILDER_BAG.getOrCreate(villager);
		for (ItemStack stack : bag.stacks()) {
			if (!stack.isEmpty()) {
				out.merge(stack.getItem(), stack.getCount(), Integer::sum);
			}
		}
		return out;
	}

	static int chestCount(GameTestHelper helper, BlockPos chest, Item item) {
		return ((Container) helper.getBlockEntity(chest)).countItem(item);
	}

	/** The hut's materials not yet in the world: in the chest(s) plus in the Builder's bag. */
	static Map<Item, Integer> held(GameTestHelper helper, Villager villager, BlockPos... chests) {
		Map<Item, Integer> bag = bag(villager);
		Map<Item, Integer> out = new LinkedHashMap<>();
		for (Item item : HUT_ITEMS) {
			int n = bag.getOrDefault(item, 0);
			for (BlockPos c : chests) {
				n += chestCount(helper, c, item);
			}
			out.put(item, n);
		}
		return out;
	}

	static int onTheGround(GameTestHelper helper, Item item) {
		AABB area = helper.getBounds().inflate(3);
		return helper.getLevel().getEntitiesOfClass(ItemEntity.class, area, e -> e.getItem().is(item)).stream().mapToInt(e -> e.getItem().getCount()).sum();
	}

	static String where(GameTestHelper helper, Run r) {
		BuildSite site = r.live();
		return site == null ? "(no site)" : site.stage() + " " + site.status() + " cursor " + site.cursor() + " skipped " + site.skipped()
			+ " missing " + site.missing() + "; builder at " + helper.relativePos(r.villager().blockPosition()) + " alive " + r.villager().isAlive()
			+ " bag " + bag(r.villager());
	}

	static void assertBuilt(GameTestHelper helper, Run r) {
		assertBuilt(helper, r, List.of());
	}

	/** As drawn, but for {@code notCounted} (relative spots a changed rule took out of the plan, such as the foundation). */
	static void assertBuilt(GameTestHelper helper, Run r, List<BlockPos> notCounted) {
		helper.assertTrue(r.live() == null, "still building: " + where(helper, r));
		List<BlockPos> wrong = new ArrayList<>(r.plan().unfinished(r.level()));
		java.util.Set<BlockPos> skip = notCounted.stream().map(helper::absolutePos).collect(Collectors.toSet());
		wrong.removeIf(skip::contains);
		helper.assertTrue(wrong.isEmpty(), wrong.size() + " block(s) of the hut wrong, e.g. " + wrong.stream().limit(6)
			.map(p -> p.subtract(r.placement().origin()).toShortString() + "=" + r.level().getBlockState(p)).collect(Collectors.joining(", ")));
	}

	private static void lawn(GameTestHelper helper) {
		for (int x = 0; x < 30; x++) {
			for (int z = 0; z < 30; z++) {
				helper.setBlock(new BlockPos(x, LAWN - 1, z), Blocks.DIRT);
				helper.setBlock(new BlockPos(x, LAWN, z), Blocks.GRASS_BLOCK);
			}
		}
	}

	private static Item itemOf(Block block) {
		return block.asItem();
	}

	// ------------------------------------------------------------------------------------------------- FOUNDATION restarts

	/** Digs a dip two deep under three of the hut's columns; returns the six emptied spots. */
	private static List<BlockPos> dip(GameTestHelper helper) {
		List<BlockPos> spots = new ArrayList<>();
		for (int x = 13; x <= 15; x++) {
			BlockPos up = new BlockPos(x, LAWN, 14);
			helper.setBlock(up, Blocks.AIR);
			helper.setBlock(up.below(), Blocks.AIR);
			spots.add(up);
			spots.add(up.below());
		}
		return spots;
	}

	/**
	 * A hut over a dip two deep under three of its columns (workplaceFoundationDepth 12), from a chest holding the hut's
	 * materials and 6 cobblestone and 6 planks more. The server restarts the first tick FOUNDATION has filled something.
	 * When {@code depthAfter} isn't -1, workplaceFoundationDepth is set to it at that restart (an admin changing the rule
	 * while the server is down). The hut must end as drawn, nothing on the ground, nothing left in the Builder's bag, and
	 * the chest's extra cobblestone and planks less exactly what went into the dip (nothing lost, nothing doubled).
	 */
	private static void foundationRestart(GameTestHelper helper, int depthAfter) {
		ServerLevel level = rules(helper, false, 2);
		level.getGameRules().getRule(ModGameRules.FOUNDATION_DEPTH).set(12, level.getServer());
		lawn(helper);
		List<BlockPos> spots = dip(helper);
		ItemStack[] items = {new ItemStack(Items.COBBLESTONE, 31), new ItemStack(Items.OAK_PLANKS, 61), new ItemStack(Items.OAK_DOOR),
			new ItemStack(Items.TORCH)};
		Run r = start(helper, LAWN_BENCH, LAWN_CHEST, LAWN_HUT, items);
		helper.assertTrue(!r.plan().steps(BuildPlan.Stage.FOUNDATION).isEmpty(), "the hut over a dip two deep has no foundation steps");
		String[] note = {null};
		helper.onEachTick(() -> {
			BuildSite site = r.live();
			if (note[0] != null || site == null || site.stage() != BuildPlan.Stage.FOUNDATION) {
				return;
			}
			long filled = spots.stream().filter(p -> !helper.getBlockState(p).isAir()).count();
			if (filled == 0) {
				return;
			}
			Map<Item, Integer> before = held(helper, r.villager(), LAWN_CHEST);
			restart(helper, r);
			if (depthAfter >= 0) {
				level.getGameRules().getRule(ModGameRules.FOUNDATION_DEPTH).set(depthAfter, level.getServer());
			}
			BuildSite back = r.live();
			helper.assertTrue(back != null && back.stage() == BuildPlan.Stage.FOUNDATION, "after a restart in FOUNDATION the site is "
				+ (back == null ? "gone" : back.stage()));
			helper.assertTrue(held(helper, r.villager(), LAWN_CHEST).equals(before), "the restart in FOUNDATION changed the materials held from "
				+ before + " to " + held(helper, r.villager(), LAWN_CHEST));
			note[0] = "restarted with " + filled + " of 6 dip spots filled, bag " + bag(r.villager());
		});
		helper.succeedWhen(() -> {
			helper.assertTrue(note[0] != null, "the build never reached FOUNDATION with something filled: " + where(helper, r));
			// with the depth set to 0 at the restart the rest of the dip is no longer part of the build (the plan of the start had it)
			assertBuilt(helper, r, depthAfter < 0 ? List.of() : spots);
			Map<Item, Integer> inDip = new LinkedHashMap<>();
			for (BlockPos p : spots) {
				if (!helper.getBlockState(p).isAir()) {
					inDip.merge(itemOf(helper.getBlockState(p).getBlock()), 1, Integer::sum);
				}
			}
			if (depthAfter < 0) {
				helper.assertTrue(spots.stream().noneMatch(p -> helper.getBlockState(p).isAir()), "with workplaceFoundationDepth 12 the dip isn't filled after a restart in FOUNDATION: "
					+ spots.stream().map(p -> p.toShortString() + "=" + helper.getBlockState(p)).collect(Collectors.joining(", ")));
			}
			Map<Item, Integer> left = held(helper, r.villager(), LAWN_CHEST);
			Map<Item, Integer> want = new LinkedHashMap<>();
			want.put(Items.COBBLESTONE, 6 - inDip.getOrDefault(Items.COBBLESTONE, 0));
			want.put(Items.OAK_PLANKS, 6 - inDip.getOrDefault(Items.OAK_PLANKS, 0));
			want.put(Items.OAK_DOOR, 0);
			want.put(Items.TORCH, 0);
			helper.assertTrue(left.equals(want), "after the restart in FOUNDATION (" + note[0] + ") the chest and bag hold " + left + ", wanted " + want
				+ " (the dip holds " + inDip + ")");
			for (Item item : HUT_ITEMS) {
				helper.assertTrue(onTheGround(helper, item) == 0, onTheGround(helper, item) + " " + item + " on the ground after the build");
			}
			helper.assertTrue(bag(r.villager()).isEmpty(), "the Builder still carries " + bag(r.villager()) + " after the hut");
		});
	}

	/** A restart in the middle of FOUNDATION: the dip filled to its bottom once, the chest short exactly what went in. */
	//$ gametest_ticks_batch HUGE '6000' '"qaCfgFoundationRestart"'
	@GameTest(template = HUGE, timeoutTicks = 6000, batch = "qaCfgFoundationRestart")
	public void aRestartMidFoundationFillsTheDipOnceFromTheChest(GameTestHelper helper) {
		foundationRestart(helper, -1);
	}

	/**
	 * workplaceFoundationDepth set to 0 ("no foundations") while the server is down in the middle of FOUNDATION: the
	 * hut is still finished as drawn and nothing is lost or made (what the dip already holds came out of the chest, the rest
	 * stays there).
	 */
	//$ gametest_ticks_batch HUGE '6000' '"qaCfgFoundationZeroRestart"'
	@GameTest(template = HUGE, timeoutTicks = 6000, batch = "qaCfgFoundationZeroRestart")
	public void foundationDepthSetToZeroAtARestartMidFoundationLosesNothing(GameTestHelper helper) {
		foundationRestart(helper, 0);
	}

	// ------------------------------------------------------------------------------------------------- workplaceLevelGround 1

	/**
	 * workplaceLevelGround 1 (its first step above 0): a pit right beside the hut (1 out) is filled, one 2 out is left,
	 * even though the server restarted in the middle of DECORATION, before LANDSCAPE was worked out.
	 */
	//$ gametest_ticks_batch HUGE '6000' '"qaCfgLevelGroundOne"'
	@GameTest(template = HUGE, timeoutTicks = 6000, batch = "qaCfgLevelGroundOne")
	public void levelGroundOneFillsThePitOneOutAndNotTwoOutAcrossARestart(GameTestHelper helper) {
		ServerLevel level = rules(helper, false, 2);
		level.getGameRules().getRule(ModGameRules.LEVEL_GROUND).set(1, level.getServer());
		lawn(helper);
		BlockPos in = new BlockPos(17, LAWN, 14); // 1 out east of x 12..16
		BlockPos out = new BlockPos(10, LAWN, 14); // 2 out west
		helper.setBlock(in, Blocks.AIR);
		helper.setBlock(out, Blocks.AIR);
		ItemStack[] items = {new ItemStack(Items.COBBLESTONE, 25), new ItemStack(Items.OAK_PLANKS, 55), new ItemStack(Items.OAK_DOOR),
			new ItemStack(Items.TORCH), new ItemStack(Items.DIRT, 8)};
		Run r = start(helper, LAWN_BENCH, LAWN_CHEST, LAWN_HUT, items);
		String[] note = {null};
		helper.onEachTick(() -> {
			BuildSite site = r.live();
			if (note[0] != null || site == null || site.stage() != BuildPlan.Stage.DECORATION) {
				return;
			}
			restart(helper, r);
			BuildSite back = r.live();
			helper.assertTrue(back != null && back.stage() == BuildPlan.Stage.DECORATION, "after a restart in DECORATION the site is "
				+ (back == null ? "gone" : back.stage()));
			note[0] = "restarted in DECORATION at cursor " + back.cursor();
		});
		helper.succeedWhen(() -> {
			helper.assertTrue(note[0] != null, "the build never reached DECORATION: " + where(helper, r));
			assertBuilt(helper, r);
			helper.assertTrue(!helper.getBlockState(in).isAir(), "with workplaceLevelGround 1 the pit right beside the hut wasn't filled (" + note[0] + ")");
			helper.assertTrue(helper.getBlockState(out).isAir(), "with workplaceLevelGround 1 the pit 2 out was filled: " + helper.getBlockState(out));
			Map<Item, Integer> left = held(helper, r.villager(), LAWN_CHEST);
			left.values().removeIf(n -> n == 0);
			helper.assertTrue(left.isEmpty(), "the hut is built but its materials are still held (doubled): " + left);
		});
	}

	// ------------------------------------------------------------------------------------------------- workplaceBuildDelay 1

	/** workplaceBuildDelay at its minimum, 1: the hut is still finished as drawn from exactly the chest's materials. */
	//$ gametest_ticks_batch AREA '3000' '"qaCfgDelayOne"'
	@GameTest(template = AREA, timeoutTicks = 3000, batch = "qaCfgDelayOne")
	public void buildDelayOneFinishesTheHutFromExactlyTheChest(GameTestHelper helper) {
		rules(helper, false, 1);
		Run r = start(helper, BENCH, CHEST, HUT, hutMaterials());
		helper.succeedWhen(() -> {
			assertBuilt(helper, r);
			Map<Item, Integer> left = held(helper, r.villager(), CHEST);
			left.values().removeIf(n -> n == 0);
			helper.assertTrue(left.isEmpty(), "with workplaceBuildDelay 1 the hut is built but its materials are still held (doubled): " + left);
			for (Item item : HUT_ITEMS) {
				helper.assertTrue(onTheGround(helper, item) == 0, onTheGround(helper, item) + " " + item + " on the ground after the build");
			}
		});
	}

	// ------------------------------------------------------------------------------------------------- free materials mid-build

	/**
	 * workplaceFreeMaterials switched on in the middle of STRUCTURE ("Builders place blocks without needing materials"):
	 * from then on the chest never holds less of any of the hut's materials than at the switch, and the hut is finished.
	 */
	//$ gametest_ticks_batch AREA '4000' '"qaCfgFreeOn"'
	@GameTest(template = AREA, timeoutTicks = 4000, batch = "qaCfgFreeOn")
	public void freeMaterialsSwitchedOnMidStructureTakesNothingMoreFromTheChest(GameTestHelper helper) {
		ServerLevel level = rules(helper, false, 2);
		Run r = start(helper, BENCH, CHEST, HUT, hutMaterials());
		Map<Item, Integer> atSwitch = new LinkedHashMap<>();
		List<String> taken = new ArrayList<>();
		helper.onEachTick(() -> {
			BuildSite site = r.live();
			if (atSwitch.isEmpty()) {
				if (site != null && site.stage() == BuildPlan.Stage.STRUCTURE && site.cursor() >= 5) {
					for (Item item : HUT_ITEMS) {
						atSwitch.put(item, chestCount(helper, CHEST, item));
					}
					level.getGameRules().getRule(ModGameRules.FREE_MATERIALS).set(true, level.getServer());
				}
				return;
			}
			for (Item item : HUT_ITEMS) {
				int now = chestCount(helper, CHEST, item);
				if (now < atSwitch.get(item) && taken.isEmpty()) {
					taken.add(item + " went from " + atSwitch.get(item) + " to " + now + " in " + where(helper, r));
				}
			}
		});
		helper.succeedWhen(() -> {
			helper.assertTrue(!atSwitch.isEmpty(), "the build never reached STRUCTURE cursor 5: " + where(helper, r));
			helper.assertTrue(taken.isEmpty(), "with free materials switched on the Builder still took from the chest: " + taken);
			assertBuilt(helper, r);
		});
	}

	/**
	 * workplaceFreeMaterials switched off in the middle of STRUCTURE with an empty chest: the Builder waits for materials
	 * and places nothing more; once a player fills the chest he finishes, taking no more than the blocks still to place.
	 */
	//$ gametest_ticks_batch AREA '5000' '"qaCfgFreeOff"'
	@GameTest(template = AREA, timeoutTicks = 5000, batch = "qaCfgFreeOff")
	public void freeMaterialsSwitchedOffMidStructureWaitsForTheChest(GameTestHelper helper) {
		ServerLevel level = rules(helper, true, 2);
		Run r = start(helper, BENCH, CHEST, HUT);
		int given = java.util.Arrays.stream(hutMaterials()).mapToInt(ItemStack::getCount).sum();
		int[] tick = {0};
		int[] switchedAt = {-1};
		int[] waitingFrom = {-1};
		int[] unfinishedAtWait = {-1};
		int[] unfinishedAtFill = {-1};
		helper.onEachTick(() -> {
			tick[0]++;
			BuildSite site = r.live();
			if (switchedAt[0] < 0) {
				if (site != null && site.stage() == BuildPlan.Stage.STRUCTURE && site.cursor() >= 5) {
					level.getGameRules().getRule(ModGameRules.FREE_MATERIALS).set(false, level.getServer());
					switchedAt[0] = tick[0];
				}
				return;
			}
			if (unfinishedAtFill[0] >= 0) {
				return;
			}
			helper.assertTrue(site != null, "with free materials off and an empty chest the hut was finished anyway");
			if (waitingFrom[0] < 0) {
				if (site.status() == BuildSite.Status.WAITING_FOR_MATERIALS) {
					waitingFrom[0] = tick[0];
					unfinishedAtWait[0] = r.plan().unfinished(level).size();
				} else {
					helper.assertTrue(tick[0] - switchedAt[0] < 600, "600 ticks after free materials were switched off with an empty chest the Builder isn't waiting: "
						+ where(helper, r));
				}
				return;
			}
			if (tick[0] - waitingFrom[0] == 200) {
				int now = r.plan().unfinished(level).size();
				helper.assertTrue(now == unfinishedAtWait[0], "while waiting with an empty chest " + (unfinishedAtWait[0] - now)
					+ " more block(s) of the hut were placed from nothing");
				Village.forget(r.villager());
				Builders.statusText(level, site, r.villager());
				helper.assertTrue(!site.missing().isEmpty(), "waiting with an empty chest, the status names nothing missing");
				unfinishedAtFill[0] = now;
				Container chest = helper.getBlockEntity(CHEST);
				ItemStack[] items = hutMaterials();
				for (int i = 0; i < items.length; i++) {
					chest.setItem(i, items[i].copy());
				}
			}
		});
		helper.succeedWhen(() -> {
			helper.assertTrue(unfinishedAtFill[0] >= 0, "never got to filling the chest: switched " + switchedAt[0] + ", waiting " + waitingFrom[0] + ", "
				+ where(helper, r));
			assertBuilt(helper, r);
			int left = held(helper, r.villager(), CHEST).values().stream().mapToInt(Integer::intValue).sum();
			int took = given - left;
			helper.assertTrue(took > 0 && took <= unfinishedAtFill[0], "the Builder took " + took + " item(s) for the " + unfinishedAtFill[0]
				+ " block(s) still to place after free materials were switched off");
		});
	}

	// ------------------------------------------------------------------------------------------------- the Storehouse as the only supply

	private static void ensurePoi(ServerLevel level, BlockPos pos) {
		if (level.getPoiManager().getType(pos).isEmpty()) {
			PoiTypes.forState(level.getBlockState(pos)).ifPresent(type -> level.getPoiManager().add(pos.immutable(), type));
		}
	}

	private static ServerLevel storehouse(GameTestHelper helper, int cobble) {
		ServerLevel level = rules(helper, false, 2);
		Leftovers.players(helper);
		Leftovers.halls(helper);
		Leftovers.storehouses(helper);
		Leftovers.village(helper, 48);
		io.github.jcondedata.aliveworkplace.hall.VillageHalls.forgetNearest();
		helper.setBlock(STOREHOUSE, ModBlocks.STOREHOUSE);
		ensurePoi(level, helper.absolutePos(STOREHOUSE));
		((StorehouseBlockEntity) helper.getBlockEntity(STOREHOUSE)).setOwner(JESSE, "Jesse");
		for (BlockPos chest : STORE_CHESTS) {
			helper.setBlock(chest, Blocks.CHEST);
		}
		Container a = helper.getBlockEntity(STORE_CHESTS[0]);
		a.setItem(0, new ItemStack(Items.OAK_PLANKS, 55));
		a.setItem(1, new ItemStack(Items.TORCH));
		a.setItem(2, new ItemStack(Items.OAK_DOOR));
		Container b = helper.getBlockEntity(STORE_CHESTS[1]);
		b.setItem(0, new ItemStack(Items.COBBLESTONE, cobble));
		return level;
	}

	/**
	 * A Builder whose bench has no chest near it builds from the village Storehouse alone, which holds only 15 of the hut's
	 * 25 cobblestone. He builds until it runs out, then waits; the status asks for exactly the 10 short, and in 200 ticks of
	 * waiting no cobblestone block is put up from nothing. When {@code restartWhileWaiting}, the server restarts while he
	 * waits and the status must ask for the same 10 again. A player then brings exactly 10: the hut is finished and the
	 * Storehouse is left with no cobblestone (nothing lost, nothing doubled).
	 */
	private static void storehouseShort(GameTestHelper helper, boolean restartWhileWaiting) {
		ServerLevel level = storehouse(helper, 15);
		Run r = start(helper, BENCH, null, HUT);
		java.util.Set<BlockPos> cobbleSpots = new java.util.HashSet<>();
		for (BuildPlan.Stage stage : BuildPlan.Stage.values()) {
			r.plan().steps(stage).stream().filter(s -> s.state().is(Blocks.COBBLESTONE)).forEach(s -> cobbleSpots.add(s.pos()));
		}
		helper.assertTrue(cobbleSpots.size() == 25, "the test hut has " + cobbleSpots.size() + " cobblestone blocks, this test expects 25");
		int[] tick = {0};
		int[] waitingFrom = {-1};
		long[] cobbleLeftAtWait = {-1};
		boolean[] filled = {false};
		helper.onEachTick(() -> {
			tick[0]++;
			if (filled[0]) {
				return;
			}
			BuildSite site = r.live();
			helper.assertTrue(site != null, "the hut was finished with only 15 of its 25 cobblestone anywhere");
			int inStore = chestCount(helper, STORE_CHESTS[1], Items.COBBLESTONE);
			if (waitingFrom[0] < 0) {
				if (site.status() == BuildSite.Status.WAITING_FOR_MATERIALS && inStore == 0 && bag(r.villager()).getOrDefault(Items.COBBLESTONE, 0) == 0) {
					waitingFrom[0] = tick[0];
					cobbleLeftAtWait[0] = r.plan().unfinished(level).stream().filter(cobbleSpots::contains).count();
				}
				return;
			}
			int waited = tick[0] - waitingFrom[0];
			if (restartWhileWaiting && waited == 100) {
				restart(helper, r);
			}
			if (waited == 200) {
				long cobbleLeft = r.plan().unfinished(level).stream().filter(cobbleSpots::contains).count();
				helper.assertTrue(cobbleLeft == cobbleLeftAtWait[0], "while waiting with no cobblestone anywhere " + (cobbleLeftAtWait[0] - cobbleLeft)
					+ " cobblestone block(s) were put up from nothing");
				helper.assertTrue(cobbleLeft == 10, "with 15 of 25 cobblestone used, " + cobbleLeft + " cobblestone blocks of the hut are still to place, wanted 10");
				BuildSite now = r.live();
				helper.assertTrue(now != null, "the site is gone while waiting" + (restartWhileWaiting ? " (after a restart)" : ""));
				Village.forget(r.villager());
				Builders.statusText(level, now, r.villager());
				Integer asked = now.missing().get(Items.COBBLESTONE);
				helper.assertTrue(asked != null && asked == 10, "waiting on the Storehouse for cobblestone" + (restartWhileWaiting ? " after a restart" : "")
					+ ", the status asks for " + asked + " cobblestone, wanted 10: missing " + now.missing());
				((Container) helper.getBlockEntity(STORE_CHESTS[1])).setItem(0, new ItemStack(Items.COBBLESTONE, 10));
				filled[0] = true;
			}
		});
		helper.succeedWhen(() -> {
			helper.assertTrue(filled[0], "the Builder never waited for cobblestone with the Storehouse empty: " + where(helper, r) + ", Storehouse holds "
				+ chestCount(helper, STORE_CHESTS[1], Items.COBBLESTONE));
			assertBuilt(helper, r);
			Map<Item, Integer> left = held(helper, r.villager(), STORE_CHESTS);
			left.values().removeIf(n -> n == 0);
			helper.assertTrue(left.isEmpty(), "the hut is built but the Storehouse and the Builder's bag still hold " + left + " (doubled)");
			helper.assertTrue(onTheGround(helper, Items.COBBLESTONE) == 0, onTheGround(helper, Items.COBBLESTONE) + " cobblestone on the ground");
		});
	}

	/**
	 * A Builder working at a Blueprint Table (no chest by it), fed by the village Storehouse alone (the hut's whole list
	 * there), and the server restarted in the middle of STRUCTURE while he carries something: the same stage and cursor
	 * after the restart, and at the end the hut as drawn with the Storehouse and his bag holding none of the hut's
	 * materials (nothing lost, nothing doubled).
	 */
	//$ gametest_ticks_batch HUGE '9000' '"qaCfgTableStoreRestart"'
	@GameTest(template = HUGE, timeoutTicks = 9000, batch = "qaCfgTableStoreRestart")
	public void aBlueprintTableBuilderRestartedMidStructureFinishesFromTheStorehouseExactly(GameTestHelper helper) {
		ServerLevel level = storehouse(helper, 25);
		helper.setBlock(BENCH, ModBlocks.BLUEPRINT_TABLE);
		ensurePoi(level, helper.absolutePos(BENCH));
		Villager villager = helper.spawn(EntityType.VILLAGER, BENCH.east());
		Builders.employ(level, villager, helper.absolutePos(BENCH));
		ModAttachments.BUILDER_EMPLOYER.set(villager, new Employer(JESSE, "Jesse"));
		BlueprintData.Placement placement = new BlueprintData.Placement(level.dimension().location(), helper.absolutePos(HUT), Rotation.NONE, Mirror.NONE);
		BuildSite site = Builders.start(level, villager, JESSE, "Jesse", TEST_HUT, placement);
		helper.assertTrue(site != null, "the hut didn't start at the Blueprint Table");
		Run r = new Run(level, new Villager[]{villager}, site.id(), site.plan(level), placement, null);
		String[] note = {null};
		helper.onEachTick(() -> {
			BuildSite live = r.live();
			if (note[0] != null || live == null || live.stage() != BuildPlan.Stage.STRUCTURE || live.cursor() < 10 || bag(r.villager()).isEmpty()) {
				return;
			}
			int cursor = live.cursor();
			Map<Item, Integer> before = held(helper, r.villager(), STORE_CHESTS);
			restart(helper, r);
			BuildSite back = r.live();
			helper.assertTrue(back != null && back.stage() == BuildPlan.Stage.STRUCTURE && back.cursor() == cursor, "after a restart in STRUCTURE at cursor "
				+ cursor + " the site is " + (back == null ? "gone" : back.stage() + " cursor " + back.cursor()));
			helper.assertTrue(held(helper, r.villager(), STORE_CHESTS).equals(before), "the restart changed the materials held from " + before + " to "
				+ held(helper, r.villager(), STORE_CHESTS));
			helper.assertTrue(Builders.benchPos(r.villager()).map(b -> b.equals(helper.absolutePos(BENCH))).orElse(false),
				"after the restart the Builder no longer works at his Blueprint Table: " + Builders.benchPos(r.villager()));
			note[0] = "restarted at cursor " + cursor + " with bag " + before;
		});
		helper.succeedWhen(() -> {
			helper.assertTrue(note[0] != null, "never restarted in STRUCTURE with a full bag: " + where(helper, r));
			assertBuilt(helper, r);
			Map<Item, Integer> left = held(helper, r.villager(), STORE_CHESTS);
			left.values().removeIf(n -> n == 0);
			helper.assertTrue(left.isEmpty(), "the hut is built but the Storehouse and the Builder's bag still hold " + left + " (doubled; " + note[0] + ")");
			for (Item item : HUT_ITEMS) {
				helper.assertTrue(onTheGround(helper, item) == 0, onTheGround(helper, item) + " " + item + " on the ground after the build");
			}
		});
	}

	//$ gametest_ticks_batch HUGE '9000' '"qaCfgStoreShort"'
	@GameTest(template = HUGE, timeoutTicks = 9000, batch = "qaCfgStoreShort")
	public void aBuilderWithNoChestBuildsFromTheStorehouseAndWaitsForExactlyTheShortfall(GameTestHelper helper) {
		storehouseShort(helper, false);
	}

	//$ gametest_ticks_batch HUGE '9000' '"qaCfgStoreShortRestart"'
	@GameTest(template = HUGE, timeoutTicks = 9000, batch = "qaCfgStoreShortRestart")
	public void aRestartWhileWaitingOnTheStorehouseAsksForTheSameShortfall(GameTestHelper helper) {
		storehouseShort(helper, true);
	}
}
