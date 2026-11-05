package io.github.jcondedata.aliveworkplace.test;

import io.github.jcondedata.aliveworkplace.blueprint.BlueprintData;
import io.github.jcondedata.aliveworkplace.blueprint.BlueprintItem;
import io.github.jcondedata.aliveworkplace.blueprint.StarterBlueprints;
import io.github.jcondedata.aliveworkplace.build.BlueprintSupplies;
import io.github.jcondedata.aliveworkplace.build.BuildPlan;
import io.github.jcondedata.aliveworkplace.build.BuildSite;
import io.github.jcondedata.aliveworkplace.build.BuildSiteManager;
import io.github.jcondedata.aliveworkplace.build.BuilderBag;
import io.github.jcondedata.aliveworkplace.build.Builders;
import io.github.jcondedata.aliveworkplace.registry.ModAttachments;
import io.github.jcondedata.aliveworkplace.registry.ModBlocks;
import io.github.jcondedata.aliveworkplace.registry.ModComponents;
import io.github.jcondedata.aliveworkplace.registry.ModGameRules;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.BiConsumer;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.InteractionHand;
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
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.Fluids;

/**
 * QA (lane-b-1105-0038) of B961's fix, from the bug's spec ("take-down: every block gone and exactly what it cost back:
 * no water left, nothing flooded"), the fix's promise (CHANGELOG / commit 76ca349a6: poured liquids are scooped first
 * with an empty bucket from his bag or the bench's chests; with no empty bucket nothing is given back, a bucket is never
 * made from nothing) and the guide's take-down (place the blueprint over the finished build and sneak while handing it to
 * a builder). Each build is made from exactly the Material List the player reads, then taken down through the sneak
 * hand-over, and 100 ticks after it finishes: none of its blocks standing, no liquid left in or round its footprint (but a
 * player's own), the bag empty, every non-bucket item back exactly (a container it holds stays, as builders never break
 * containers), and the buckets neither made nor lost: the full buckets left over from the build (a source that filled
 * itself before he poured it) plus one per liquid source he had an empty bucket for, the rest of the empties still empty.
 * Covers: a save and reload half way through the scooping, no empty bucket at all, only four, some in his bag and some in
 * a chest, a turned and mirrored pond,
 * a player's own pool beside the pond, the Fountain (a raised basin), the Well and the Gem Grotto (lava).
 */
public class QaTakeDown1105GameTests implements FabricGameTest {
	private static final String BIG = "aliveworkplace_test:big_area";
	private static final org.slf4j.Logger LOGGER = org.slf4j.LoggerFactory.getLogger("aliveworkplace_test");
	private static final BlockPos BENCH = new BlockPos(2, 2, 2);
	private static final BlockPos VILLAGER = new BlockPos(3, 2, 3);
	private static final List<BlockPos> CHESTS = List.of(new BlockPos(2, 2, 4), new BlockPos(4, 2, 4), new BlockPos(1, 2, 5), new BlockPos(4, 2, 1));
	private static final BlockPos ORIGIN = new BlockPos(9, 2, 9);

	private static final BiConsumer<GameTestHelper, Run> NOTHING = (h, r) -> {
	};

	/** What a test changes and expects. */
	private static final class Case {
		final StarterBlueprints.Entry entry;
		BlockPos origin = ORIGIN;
		Rotation rotation = Rotation.NONE;
		Mirror mirror = Mirror.NONE;
		/** Run just before the hand-over (the build finished, its leftovers in the chests). */
		BiConsumer<GameTestHelper, Run> beforeDown = NOTHING;
		/** Fluid spots (relative) that are the player's and must still be sources at the end. */
		Set<BlockPos> playersWater = Set.of();
		/** Save and reload the world once, when half the build's liquid has been scooped. */
		boolean reloadHalfway;

		Case(StarterBlueprints.Entry entry) {
			this.entry = entry;
		}
	}

	/** The live state of one run (the villager changes on a reload). */
	static final class Run {
		ServerLevel level;
		Villager villager;
		BuildSite site;
		BuildPlan plan;
		Map<Item, Integer> list;
		BlueprintData.Placement placement;
		UUID downId;
		int liquids;
		int emptiesAtHandOver = -1;
		/** Water or lava buckets he had left over when the build finished (a source can fill itself before he pours it). */
		int fullAtHandOver;
		int halfwayReloads;
	}

	// ------------------------------------------------------------------------------------------------------ helpers

	private static void ensurePoi(ServerLevel level, BlockPos pos) {
		if (level.getPoiManager().getType(pos).isEmpty()) {
			PoiTypes.forState(level.getBlockState(pos)).ifPresent(type -> level.getPoiManager().add(pos.immutable(), type));
		}
	}

	/** The list a player reads on the held, placed blueprint with empty chests beside the bench. */
	private static Map<Item, Integer> playerList(GameTestHelper helper, StarterBlueprints.Entry entry, BlueprintData.Placement placement) {
		BlueprintData data = new BlueprintData(entry.id(), Optional.of(entry.size()), Optional.of(placement));
		BlueprintSupplies.Checklist list = BlueprintSupplies.checklist(helper.getLevel(), data)
			.orElseThrow(() -> new GameTestAssertException(entry.id() + ": the held blueprint has no material list"));
		Map<Item, Integer> out = new LinkedHashMap<>();
		for (BlueprintSupplies.Line line : list.lines()) {
			out.merge(line.item(), line.toBring(), Integer::sum);
		}
		helper.assertTrue(!out.isEmpty(), entry.id() + ": the material list is empty");
		return out;
	}

	private static void stock(GameTestHelper helper, Map<Item, Integer> list) {
		List<ItemStack> stacks = new ArrayList<>();
		list.forEach((item, n) -> {
			for (int left = n; left > 0; ) {
				int take = Math.min(left, item.getDefaultMaxStackSize());
				stacks.add(new ItemStack(item, take));
				left -= take;
			}
		});
		int i = 0;
		for (BlockPos chest : CHESTS) {
			Container c = helper.getBlockEntity(chest);
			for (int slot = 0; slot < c.getContainerSize() && i < stacks.size(); slot++) {
				c.setItem(slot, stacks.get(i++));
			}
		}
		helper.assertTrue(i == stacks.size(), "setup: more stacks than chests (" + stacks.size() + ")");
	}

	/** {@code item} in the supply chests, the builder's bag and on the ground near the test. */
	private static int held(GameTestHelper helper, Villager villager, Item item) {
		int n = 0;
		for (BlockPos chest : CHESTS) {
			if (helper.getLevel().getBlockEntity(helper.absolutePos(chest)) instanceof Container c) {
				n += c.countItem(item);
			}
		}
		n += ModAttachments.BUILDER_BAG.getOrCreate(villager).count(item);
		for (ItemEntity e : helper.getLevel().getEntitiesOfClass(ItemEntity.class, helper.getBounds().inflate(16))) {
			if (e.getItem().is(item)) {
				n += e.getItem().getCount();
			}
		}
		return n;
	}

	/** Takes up to {@code keep}-less empty buckets out of the chests and the bag, leaving {@code keep} in the first chest. */
	private static void leaveEmptyBuckets(GameTestHelper helper, Run run, int keep) {
		leaveEmptyBuckets(helper, run, keep, 0);
	}

	/** As {@link #leaveEmptyBuckets(GameTestHelper, Run, int)}, plus {@code inBag} more empty buckets in the builder's bag. */
	private static void leaveEmptyBuckets(GameTestHelper helper, Run run, int keep, int inBag) {
		BuilderBag bag = ModAttachments.BUILDER_BAG.getOrCreate(run.villager);
		bag.remove(Items.BUCKET, bag.count(Items.BUCKET));
		for (BlockPos chest : CHESTS) {
			Container c = helper.getBlockEntity(chest);
			for (int slot = 0; slot < c.getContainerSize(); slot++) {
				if (c.getItem(slot).is(Items.BUCKET)) {
					c.setItem(slot, ItemStack.EMPTY);
				}
			}
		}
		if (keep > 0) {
			Container c = helper.getBlockEntity(CHESTS.get(0));
			for (int slot = 0; slot < c.getContainerSize(); slot++) {
				if (c.getItem(slot).isEmpty()) {
					c.setItem(slot, new ItemStack(Items.BUCKET, keep));
					break;
				}
			}
		}
		if (inBag > 0) {
			helper.assertTrue(bag.addAll(Items.BUCKET, inBag) == 0, "setup: the bag took no buckets");
		}
		helper.assertTrue(held(helper, run.villager, Items.BUCKET) == keep + inBag, "setup: " + held(helper, run.villager, Items.BUCKET) + " empty buckets, wanted "
			+ (keep + inBag));
	}

	/** Every fluid block (source or flowing) in the area above the floor, relative. */
	private static List<BlockPos> fluids(GameTestHelper helper) {
		List<BlockPos> out = new ArrayList<>();
		for (BlockPos p : BlockPos.betweenClosed(new BlockPos(0, 1, 0), new BlockPos(21, 17, 21))) {
			if (!helper.getBlockState(p).getFluidState().isEmpty()) {
				out.add(p.immutable());
			}
		}
		return out;
	}

	/** The absolute spots where the built plan pours a liquid source. */
	private static Set<BlockPos> liquidSpots(BuildPlan plan) {
		Set<BlockPos> out = new HashSet<>();
		for (BuildPlan.Stage stage : List.of(BuildPlan.Stage.FOUNDATION, BuildPlan.Stage.STRUCTURE, BuildPlan.Stage.DECORATION)) {
			for (BuildPlan.Step s : plan.steps(stage)) {
				if (s.state().getFluidState().isSource()) {
					out.add(s.pos().immutable());
				}
			}
		}
		return out;
	}

	/** As a save and load of the world: every build site from its save, and the builder from its save. */
	private static Villager reload(GameTestHelper helper, ServerLevel level, Villager old) {
		BuildSiteManager loaded = BuildSiteManager.load(BuildSiteManager.get(level).save(new CompoundTag(), level.registryAccess()), level.registryAccess());
		level.getDataStorage().set(BuildSiteManager.NAME, loaded);
		CompoundTag tag = new CompoundTag();
		helper.assertTrue(old.save(tag), "the builder wasn't saved");
		old.discard();
		Entity back = EntityType.loadEntityRecursive(tag, level, e -> e);
		helper.assertTrue(back instanceof Villager, "the builder didn't load as a villager: " + back);
		level.addFreshEntity(back);
		return (Villager) back;
	}

	private static boolean gone(ServerLevel level, UUID id) {
		return BuildSiteManager.get(level).get(id) == null;
	}

	/**
	 * Builds {@code c.entry} from exactly its list, hands its placed blueprint over while sneaking, and checks the take-down
	 * as the class comment says.
	 */
	private static void takeDown(GameTestHelper helper, Case c) {
		Leftovers.clear(helper);
		Run run = new Run();
		ServerLevel level = helper.getLevel();
		run.level = level;
		helper.setDayTime(2000);
		level.getGameRules().getRule(ModGameRules.BUILD_DELAY).set(2, level.getServer());
		level.getGameRules().getRule(ModGameRules.BUILDERS_HELP).set(false, level.getServer());
		level.getGameRules().getRule(ModGameRules.FREE_MATERIALS).set(false, level.getServer());
		helper.setBlock(BENCH, ModBlocks.BUILDERS_BENCH);
		ensurePoi(level, helper.absolutePos(BENCH));
		for (BlockPos chest : CHESTS) {
			helper.setBlock(chest, Blocks.CHEST);
		}
		run.placement = new BlueprintData.Placement(level.dimension().location(), helper.absolutePos(c.origin), c.rotation, c.mirror);
		run.list = playerList(helper, c.entry, run.placement);
		stock(helper, run.list);
		run.villager = helper.spawn(EntityType.VILLAGER, VILLAGER);
		Builders.employ(level, run.villager, helper.absolutePos(BENCH));
		run.site = Builders.start(level, run.villager, null, c.entry.id(), run.placement);
		helper.assertTrue(run.site != null, c.entry.id() + " didn't start");
		run.plan = run.site.plan(level);
		helper.assertTrue(run.plan != null, "blueprint not found: " + c.entry.id());
		UUID buildId = run.site.id();
		// the footprint must lie inside the area, clear of the bench and chests
		net.minecraft.world.level.levelgen.structure.BoundingBox inner = net.minecraft.world.level.levelgen.structure.BoundingBox.fromCorners(
			helper.absolutePos(new BlockPos(6, 1, 6)), helper.absolutePos(new BlockPos(21, 17, 21)));
		helper.assertTrue(inner.isInside(new BlockPos(run.plan.bounds().minX(), run.plan.bounds().minY(), run.plan.bounds().minZ()))
				&& inner.isInside(new BlockPos(run.plan.bounds().maxX(), run.plan.bounds().maxY(), run.plan.bounds().maxZ())),
			"setup: " + c.entry.id() + " covers " + run.plan.bounds() + ", outside " + inner);
		Set<BlockPos> liquids = liquidSpots(run.plan);
		run.liquids = liquids.size();
		helper.assertTrue(run.liquids > 0 || c.entry == StarterBlueprints.FIRE_PIT, "setup: " + c.entry.id() + " pours no liquid");
		int paidFull = run.list.getOrDefault(Items.WATER_BUCKET, 0) + run.list.getOrDefault(Items.LAVA_BUCKET, 0);
		helper.assertTrue(paidFull == run.liquids, "setup: " + c.entry.id() + "'s list asks for " + paidFull + " liquid buckets for " + run.liquids + " sources");
		Leftovers.after(helper, () -> {
			if (!gone(level, buildId)) {
				BuildSiteManager.get(level).remove(buildId);
			}
			if (run.downId != null && !gone(level, run.downId)) {
				BuildSiteManager.get(level).remove(run.downId);
			}
		});
		ServerPlayer player = helper.makeMockServerPlayerInLevel();
		long[] downAt = {-1};
		helper.onEachTick(() -> {
			helper.setDayTime(2000);
			if (run.downId == null && gone(level, buildId) && helper.getTick() > 5) {
				helper.assertTrue(run.plan.unfinished(level).isEmpty() && run.site.skipped() == 0, c.entry.id() + " from exactly its list didn't finish as drawn: "
					+ run.plan.unfinished(level).size() + " block(s) off, skipped " + run.site.skipped());
				c.beforeDown.accept(helper, run);
				run.emptiesAtHandOver = held(helper, run.villager, Items.BUCKET);
				run.fullAtHandOver = held(helper, run.villager, Items.WATER_BUCKET) + held(helper, run.villager, Items.LAVA_BUCKET);
				helper.assertTrue(run.fullAtHandOver + run.emptiesAtHandOver == run.liquids || c.beforeDown != NOTHING,
					"setup: the build left " + run.fullAtHandOver + " full and " + run.emptiesAtHandOver + " empty buckets of the " + run.liquids + " paid");
				ItemStack print = BlueprintItem.create(c.entry.id(), null);
				print.set(ModComponents.BLUEPRINT, BlueprintItem.data(print).orElseThrow().withPlacement(Optional.of(run.placement)));
				player.setItemInHand(InteractionHand.MAIN_HAND, print);
				Builders.assign(player, run.villager, print, true);
				BuildSite down = Builders.activeSite(level, run.villager);
				helper.assertTrue(down != null && down.isDeconstruction(), "the sneak hand-over of the finished " + c.entry.id() + " started no take-down");
				run.downId = down.id();
				LOGGER.info("qa takedown 1105 {}: built, {} liquid sources, {} full and {} empty buckets at the hand-over", c.entry.id(), run.liquids, run.fullAtHandOver,
					run.emptiesAtHandOver);
			}
			if (run.downId != null && c.reloadHalfway && run.halfwayReloads == 0 && downAt[0] < 0) {
				int left = 0;
				for (BlockPos p : liquids) {
					if (level.getFluidState(p).isSource()) {
						left++;
					}
				}
				if (left > 0 && left <= run.liquids / 2) {
					run.villager = reload(helper, level, run.villager);
					run.halfwayReloads++;
					LOGGER.info("qa takedown 1105 {}: saved and reloaded with {} of {} sources left", c.entry.id(), left, run.liquids);
				}
			}
			if (run.downId != null && downAt[0] < 0 && gone(level, run.downId)) {
				downAt[0] = helper.getTick();
			}
		});
		helper.succeedWhen(() -> {
			helper.assertTrue(downAt[0] >= 0, run.downId == null ? "not built yet: " + run.site.stage() + " " + run.site.status() + " " + run.site.missing()
				: "still taking down: " + (gone(level, run.downId) ? "?" : BuildSiteManager.get(level).get(run.downId).stage()));
			helper.assertTrue(helper.getTick() - downAt[0] >= 100, "waiting 100 ticks after the take-down");
			helper.assertTrue(!c.reloadHalfway || run.halfwayReloads == 1, "the take-down never got to half its liquid scooped to reload at");
			List<String> standing = new ArrayList<>();
			Set<Item> keptContainers = new HashSet<>();
			for (BuildPlan.Stage stage : List.of(BuildPlan.Stage.STRUCTURE, BuildPlan.Stage.DECORATION)) {
				for (BuildPlan.Step s : run.plan.steps(stage)) {
					BlockState now = level.getBlockState(s.pos());
					if (level.getBlockEntity(s.pos()) instanceof Container) {
						keptContainers.add(now.getBlock().asItem()); // builders never break containers (CLAUDE.md, the wiki)
						continue;
					}
					if (!now.isAir() && !now.canBeReplaced() || !now.getFluidState().isEmpty()) {
						standing.add(s.pos().subtract(run.placement.origin()).toShortString() + "=" + BuiltInRegistries.BLOCK.getKey(now.getBlock()).getPath());
					}
				}
			}
			helper.assertTrue(standing.isEmpty(), c.entry.id() + " taken down still has " + standing.size() + " of its built blocks standing, e.g. "
				+ standing.subList(0, Math.min(6, standing.size())));
			List<BlockPos> fluid = fluids(helper);
			List<String> stray = new ArrayList<>();
			for (BlockPos p : fluid) {
				if (!c.playersWater.contains(p)) {
					stray.add(p.toShortString() + "=" + (helper.getBlockState(p).getFluidState().isSource() ? "source " : "flowing ")
						+ BuiltInRegistries.FLUID.getKey(helper.getBlockState(p).getFluidState().getType()).getPath());
				}
			}
			helper.assertTrue(stray.isEmpty(), c.entry.id() + " taken down left " + stray.size() + " liquid block(s) in or round its footprint, e.g. "
				+ stray.subList(0, Math.min(6, stray.size())));
			for (BlockPos p : c.playersWater) {
				helper.assertTrue(helper.getBlockState(p).getFluidState().is(Fluids.WATER) && helper.getBlockState(p).getFluidState().isSource(),
					"the player's own water source at " + p.toShortString() + " is gone after the take-down: " + helper.getBlockState(p));
			}
			List<BlockPos> fire = new ArrayList<>();
			for (BlockPos p : BlockPos.betweenClosed(new BlockPos(0, 1, 0), new BlockPos(21, 17, 21))) {
				BlockState s = helper.getBlockState(p);
				if (s.is(Blocks.FIRE) || s.is(Blocks.SOUL_FIRE)) {
					fire.add(p.immutable());
				}
			}
			helper.assertTrue(fire.isEmpty(), c.entry.id() + " taken down left fire at " + fire);
			helper.assertTrue(ModAttachments.BUILDER_BAG.getOrCreate(run.villager).stacks().stream().allMatch(ItemStack::isEmpty),
				"after the take-down the builder still carries " + ModAttachments.BUILDER_BAG.getOrCreate(run.villager).stacks());
			List<String> wrong = new ArrayList<>();
			for (Map.Entry<Item, Integer> e : run.list.entrySet()) {
				if (e.getKey() == Items.WATER_BUCKET || e.getKey() == Items.LAVA_BUCKET || e.getKey() == Items.BUCKET || keptContainers.contains(e.getKey())) {
					continue;
				}
				int got = held(helper, run.villager, e.getKey());
				if (got != e.getValue()) {
					wrong.add(BuiltInRegistries.ITEM.getKey(e.getKey()).getPath() + " " + got + " (paid " + e.getValue() + ")");
				}
			}
			int water = held(helper, run.villager, Items.WATER_BUCKET);
			int lava = held(helper, run.villager, Items.LAVA_BUCKET);
			int empty = held(helper, run.villager, Items.BUCKET);
			int wantFull = run.fullAtHandOver + Math.min(run.emptiesAtHandOver, run.liquids);
			int wantEmpty = run.emptiesAtHandOver - Math.min(run.emptiesAtHandOver, run.liquids);
			int paidWater = run.list.getOrDefault(Items.WATER_BUCKET, 0);
			int paidLava = run.list.getOrDefault(Items.LAVA_BUCKET, 0);
			if (water + lava != wantFull || empty != wantEmpty || water > paidWater || lava > paidLava) {
				wrong.add("buckets: " + water + " water + " + lava + " lava + " + empty + " empty, wanted " + wantFull + " full + " + wantEmpty + " empty (paid "
					+ paidWater + " water and " + paidLava + " lava buckets; " + run.fullAtHandOver + " full and " + run.emptiesAtHandOver + " empty buckets at the hand-over)");
			}
			helper.assertTrue(wrong.isEmpty(), c.entry.id() + " built and taken down gives back the wrong things: " + wrong);
			LOGGER.info("qa takedown 1105 {}: down at tick {}, {} water + {} lava + {} empty buckets back, {} of the player's water kept", c.entry.id(), downAt[0],
				water, lava, empty, c.playersWater.size());
		});
	}

	// ------------------------------------------------------------------------------------------------------ tests

	/**
	 * The world is saved and loaded when half the Lily Pond's water has been scooped (the builder and every build site
	 * from their saves, the plan worked out again from the ground): the take-down carries on and ends dry, with the 15
	 * water buckets back and nothing more.
	 */
	//$ gametest_ticks_batch BIG '18000' '"qaTd1105Reload"'
	@GameTest(template = BIG, timeoutTicks = 18000, batch = "qaTd1105Reload")
	public void lilyPondTakenDownAcrossASaveAndReloadEndsDryWithItsBucketsBack(GameTestHelper helper) {
		Case c = new Case(StarterBlueprints.LILY_POND);
		c.reloadHalfway = true;
		takeDown(helper, c);
	}

	/**
	 * Every empty bucket taken away before the hand-over (none in the chests or the bag): the water still goes, none runs
	 * over the ground, and no bucket of any kind comes back (none made from nothing); every other item comes back.
	 */
	//$ gametest_ticks_batch BIG '18000' '"qaTd1105NoBucket"'
	@GameTest(template = BIG, timeoutTicks = 18000, batch = "qaTd1105NoBucket")
	public void lilyPondTakenDownWithNoEmptyBucketMakesNoBucketAndLeavesNoWater(GameTestHelper helper) {
		Case c = new Case(StarterBlueprints.LILY_POND);
		c.beforeDown = (h, run) -> leaveEmptyBuckets(h, run, 0);
		takeDown(helper, c);
	}

	/** Only four empty buckets left in a chest: four water buckets back, no empty one, and no more than four. */
	//$ gametest_ticks_batch BIG '18000' '"qaTd1105FourBuckets"'
	@GameTest(template = BIG, timeoutTicks = 18000, batch = "qaTd1105FourBuckets")
	public void lilyPondTakenDownWithFourEmptyBucketsFillsOnlyFour(GameTestHelper helper) {
		Case c = new Case(StarterBlueprints.LILY_POND);
		c.beforeDown = (h, run) -> leaveEmptyBuckets(h, run, 4);
		takeDown(helper, c);
	}

	/**
	 * Two empty buckets in the builder's own bag and three in a chest by the bench: five water buckets back, the bag's two
	 * as well as the chest's three (none lost, none taken twice for one scoop).
	 */
	//$ gametest_ticks_batch BIG '18000' '"qaTd1105BagAndChest"'
	@GameTest(template = BIG, timeoutTicks = 18000, batch = "qaTd1105BagAndChest")
	public void lilyPondTakenDownWithBucketsInHisBagAndAChestFillsEveryOne(GameTestHelper helper) {
		Case c = new Case(StarterBlueprints.LILY_POND);
		c.beforeDown = (h, run) -> leaveEmptyBuckets(h, run, 3, 2);
		takeDown(helper, c);
	}

	/** The Lily Pond turned clockwise and mirrored front to back (origin x 17, z 16: it covers x 11-17, z 8-16) comes down dry. */
	//$ gametest_ticks_batch BIG '18000' '"qaTd1105Turned"'
	@GameTest(template = BIG, timeoutTicks = 18000, batch = "qaTd1105Turned")
	public void aTurnedMirroredLilyPondComesDownDryWithItsBucketsBack(GameTestHelper helper) {
		Case c = new Case(StarterBlueprints.LILY_POND);
		c.origin = new BlockPos(17, 2, 16);
		c.rotation = Rotation.CLOCKWISE_90;
		c.mirror = Mirror.FRONT_BACK;
		takeDown(helper, c);
	}

	/**
	 * A player's own pool of three water sources (in a stone basin of his, at the pond's water height) a block east of the
	 * pond's footprint: the take-down leaves it full and takes no bucket from it (15 back, not more), and no water other
	 * than his is left.
	 */
	//$ gametest_ticks_batch BIG '18000' '"qaTd1105PlayersPool"'
	@GameTest(template = BIG, timeoutTicks = 18000, batch = "qaTd1105PlayersPool")
	public void aPlayersOwnPoolBesideTheLilyPondIsLeftAlone(GameTestHelper helper) {
		Case c = new Case(StarterBlueprints.LILY_POND);
		// the pond covers x 9-17, z 9-15, its water at y 3; the pool at x 19, z 11-13, y 3
		Set<BlockPos> pool = Set.of(new BlockPos(19, 3, 11), new BlockPos(19, 3, 12), new BlockPos(19, 3, 13));
		c.playersWater = pool;
		c.beforeDown = (h, run) -> {
			for (int z = 10; z <= 14; z++) {
				for (int x = 18; x <= 20; x++) {
					h.setBlock(new BlockPos(x, 2, z), Blocks.STONE);
					h.setBlock(new BlockPos(x, 3, z), Blocks.STONE);
				}
			}
			for (BlockPos p : pool) {
				h.setBlock(p, Blocks.WATER);
			}
		};
		takeDown(helper, c);
	}

	/** The Fountain: four sources round its pillar and one raised in a basin on top. All scooped; nothing runs down. */
	//$ gametest_ticks_batch BIG '18000' '"qaTd1105Fountain"'
	@GameTest(template = BIG, timeoutTicks = 18000, batch = "qaTd1105Fountain")
	public void fountainTakenDownLeavesNoWaterAndGivesBackItsCost(GameTestHelper helper) {
		takeDown(helper, new Case(StarterBlueprints.FOUNTAIN));
	}

	/** The Well: its water down the shaft and in the bowl, under a lantern on a chain. */
	//$ gametest_ticks_batch BIG '18000' '"qaTd1105Well"'
	@GameTest(template = BIG, timeoutTicks = 18000, batch = "qaTd1105Well")
	public void wellTakenDownLeavesNoWaterAndGivesBackItsCost(GameTestHelper helper) {
		takeDown(helper, new Case(StarterBlueprints.WELL));
	}

	/** The Gem Grotto's lava pool behind glass: scooped into lava buckets, no lava and no fire left. */
	//$ gametest_ticks_batch BIG '40000' '"qaTd1105Grotto"'
	@GameTest(template = BIG, timeoutTicks = 40000, batch = "qaTd1105Grotto")
	public void gemGrottoTakenDownScoopsItsLavaBackIntoBuckets(GameTestHelper helper) {
		Case c = new Case(StarterBlueprints.GEM_GROTTO);
		c.origin = new BlockPos(7, 2, 7);
		takeDown(helper, c);
	}
}
