package io.github.jcondedata.aliveworkplace.test;

import io.github.jcondedata.aliveworkplace.blueprint.BlueprintData;
import io.github.jcondedata.aliveworkplace.build.BuildPlan;
import io.github.jcondedata.aliveworkplace.build.BuildSite;
import io.github.jcondedata.aliveworkplace.build.BuildSiteManager;
import io.github.jcondedata.aliveworkplace.build.Builders;
import io.github.jcondedata.aliveworkplace.registry.ModAttachments;
import io.github.jcondedata.aliveworkplace.registry.ModBlocks;
import io.github.jcondedata.aliveworkplace.registry.ModGameRules;
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
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;

/**
 * QA (2026-10-31), from the builder's spec (CLAUDE.md "How the builder works"; docs/wiki/builder.md "Stages"): corners
 * the earlier builder suites leave open.
 * <ul>
 * <li>CLEAR never breaks a container: the other vanilla containers (dispenser, dropper, the three furnaces, brewing
 * stand, crafter, chiseled bookshelf, decorated pot) standing inside the hut, and a barrel standing on dirt CLEAR takes
 * away from under it.</li>
 * <li>FOUNDATION never fills over a container standing under a raised build.</li>
 * <li>LANDSCAPE never breaks a container on the leveled ring, and never waits for materials (holes to fill with dirt,
 * no dirt in the chests).</li>
 * <li>{@code workplaceFreeMaterials} switched off mid-build stops the Builder cleanly (he waits, places nothing he
 * doesn't have) and he finishes once the chest is filled.</li>
 * <li>A block a player breaks mid-STRUCTURE, then a server restart: the hut still ends whole.</li>
 * </ul>
 */
public class QaBuilderInvariants1031GameTests {
	private static final String AREA = "aliveworkplace_test:build_area";
	private static final String BIG_AREA = "aliveworkplace_test:big_area";
	private static final String HUGE = "aliveworkplace_test:huge_area";
	private static final ResourceLocation TEST_HUT = ResourceLocation.fromNamespaceAndPath("aliveworkplace_test", "test_hut");

	private static final BlockPos BENCH = new BlockPos(2, 2, 2);
	private static final BlockPos CHEST = new BlockPos(2, 2, 4);
	private static final BlockPos VILLAGER = new BlockPos(3, 2, 3);
	/** The hut stands at x 6-10, y 2-5, z 6-10. */
	private static final BlockPos HUT_ORIGIN = new BlockPos(6, 2, 6);

	private record Run(ServerLevel level, Villager[] builder, UUID site, BuildPlan plan, BlueprintData.Placement placement) {
		Villager villager() {
			return builder[0];
		}

		BuildSite live() {
			return BuildSiteManager.get(level).get(site);
		}
	}

	// ------------------------------------------------------------------------------------------------- helpers

	private static ServerLevel rules(GameTestHelper helper, boolean freeMaterials) {
		Leftovers.clear(helper);
		ServerLevel level = helper.getLevel();
		helper.setDayTime(2000);
		boolean free = level.getGameRules().getBoolean(ModGameRules.FREE_MATERIALS);
		int delay = level.getGameRules().getInt(ModGameRules.BUILD_DELAY);
		boolean help = level.getGameRules().getBoolean(ModGameRules.BUILDERS_HELP);
		level.getGameRules().getRule(ModGameRules.FREE_MATERIALS).set(freeMaterials, level.getServer());
		level.getGameRules().getRule(ModGameRules.BUILD_DELAY).set(2, level.getServer());
		level.getGameRules().getRule(ModGameRules.BUILDERS_HELP).set(false, level.getServer());
		Leftovers.after(helper, () -> {
			level.getGameRules().getRule(ModGameRules.FREE_MATERIALS).set(free, level.getServer());
			level.getGameRules().getRule(ModGameRules.BUILD_DELAY).set(delay, level.getServer());
			level.getGameRules().getRule(ModGameRules.BUILDERS_HELP).set(help, level.getServer());
		});
		return level;
	}

	/** A Builder at his bench, an empty chest beside it, starting the test hut at {@code origin}. */
	private static Run start(GameTestHelper helper, BlockPos origin) {
		ServerLevel level = helper.getLevel();
		helper.setBlock(BENCH, ModBlocks.BUILDERS_BENCH);
		helper.setBlock(CHEST, Blocks.CHEST);
		Villager villager = helper.spawn(EntityType.VILLAGER, VILLAGER);
		Builders.employ(level, villager, helper.absolutePos(BENCH));
		BlueprintData.Placement placement = new BlueprintData.Placement(level.dimension().location(), helper.absolutePos(origin), Rotation.NONE, Mirror.NONE);
		BuildSite site = Builders.start(level, villager, null, TEST_HUT, placement);
		helper.assertTrue(site != null, "the test hut wasn't started");
		BuildPlan plan = site.plan(level);
		helper.assertTrue(plan != null, "no test hut blueprint");
		return new Run(level, new Villager[]{villager}, site.id(), plan, placement);
	}

	/** Fills the supply chest with {@code needs}, plus {@code spare} more of each kind. */
	private static void fill(GameTestHelper helper, Map<Item, Integer> needs, int spare) {
		Container chest = helper.getBlockEntity(CHEST);
		int slot = 0;
		for (Map.Entry<Item, Integer> e : needs.entrySet()) {
			int left = e.getValue() + spare;
			while (left > 0) {
				int n = Math.min(left, e.getKey().getDefaultMaxStackSize());
				helper.assertTrue(slot < chest.getContainerSize(), "setup: the materials don't fit a chest: " + needs);
				chest.setItem(slot++, new ItemStack(e.getKey(), n));
				left -= n;
			}
		}
	}

	private static String rel(GameTestHelper helper, BlockPos abs) {
		return abs.subtract(helper.absolutePos(BlockPos.ZERO)).toShortString();
	}

	private static String where(GameTestHelper helper, Run r) {
		BuildSite site = r.live();
		return site == null ? "(no site)" : site.stage() + " " + site.status() + " cursor " + site.cursor() + " skipped " + site.skipped()
			+ " missing " + site.missing() + "; builder at " + rel(helper, r.villager().blockPosition()) + " alive " + r.villager().isAlive();
	}

	/** Relative spots inside the hut's walls where the drawing puts nothing (CLEAR leaves them air), lowest first. */
	private static List<BlockPos> airInside(GameTestHelper helper, BuildPlan plan) {
		List<BlockPos> out = new ArrayList<>();
		for (int y = 3; y <= 4; y++) {
			for (int x = 7; x <= 9; x++) {
				for (int z = 7; z <= 9; z++) {
					BlockPos p = new BlockPos(x, y, z);
					BlockState drawn = plan.targets().get(helper.absolutePos(p));
					if (drawn == null || drawn.isAir()) {
						out.add(p);
					}
				}
			}
		}
		return out;
	}

	/** Every tick, notes the first time each watched block stops being its kind. */
	private static void watch(GameTestHelper helper, Map<BlockPos, Block> blocks, List<String> broken, Run r) {
		helper.onEachTick(() -> blocks.forEach((pos, block) -> {
			if (!helper.getBlockState(pos).is(block) && broken.stream().noneMatch(s -> s.startsWith(pos.toShortString() + " "))) {
				broken.add(pos.toShortString() + " " + block + " became " + helper.getBlockState(pos) + " in " + where(helper, r));
			}
		}));
	}

	private static void assertHolds(GameTestHelper helper, BlockPos pos, Block kind, Item thing, int count) {
		Container c = helper.getBlockEntity(pos) instanceof Container cc ? cc : null;
		helper.assertTrue(c != null && c.countItem(thing) == count, kind + " at " + pos.toShortString() + " lost its " + thing + ": "
			+ (c == null ? "no container there, " + helper.getBlockState(pos) : c.countItem(thing) + " of " + count));
	}

	private static void assertNothingDropped(GameTestHelper helper, List<Item> things) {
		List<ItemEntity> dropped = helper.getLevel().getEntitiesOfClass(ItemEntity.class, new AABB(helper.absolutePos(BlockPos.ZERO)).inflate(24)).stream()
			.filter(e -> things.contains(e.getItem().getItem())).toList();
		helper.assertTrue(dropped.isEmpty(), "a container's things were dropped on the ground: " + dropped);
	}

	private static void restart(GameTestHelper helper, Run r) {
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

	// ------------------------------------------------------------------------------------------------- CLEAR

	/**
	 * Nine other kinds of vanilla container, each holding something, stand inside the hut where the drawing has air
	 * (CLEAR would take any other block there away). Every one stays standing with what it holds, nothing is dropped,
	 * and the hut finishes round them: the only blocks left not as drawn are the containers' own spots.
	 */
	//$ gametest_ticks_batch AREA '3000' '"qaInv1031ClearKinds"'
	@GameTest(template = AREA, timeoutTicks = 3000, batch = "qaInv1031ClearKinds")
	public void clearKeepsEveryOtherKindOfContainerInsideTheHut(GameTestHelper helper) {
		rules(helper, true);
		Run r = start(helper, HUT_ORIGIN);
		List<BlockPos> spots = airInside(helper, r.plan());
		Block[] kinds = {Blocks.DISPENSER, Blocks.DROPPER, Blocks.FURNACE, Blocks.SMOKER, Blocks.BLAST_FURNACE, Blocks.BREWING_STAND, Blocks.CRAFTER,
			Blocks.CHISELED_BOOKSHELF, Blocks.DECORATED_POT};
		Item[] things = {Items.EMERALD, Items.DIAMOND, Items.RAW_IRON, Items.BEEF, Items.RAW_GOLD, Items.POTION, Items.REDSTONE, Items.BOOK, Items.AMETHYST_SHARD};
		helper.assertTrue(spots.size() >= kinds.length, "setup: only " + spots.size() + " air spots inside the hut: " + spots);
		Map<BlockPos, Block> watched = new LinkedHashMap<>();
		for (int i = 0; i < kinds.length; i++) {
			BlockPos p = spots.get(i);
			helper.setBlock(p, kinds[i]);
			Container c = (Container) helper.getBlockEntity(p);
			c.setItem(0, new ItemStack(things[i], 1));
			watched.put(p, kinds[i]);
		}
		List<String> broken = new ArrayList<>();
		watch(helper, watched, broken, r);
		helper.succeedWhen(() -> {
			helper.assertTrue(broken.isEmpty(), "a container inside the hut was broken: " + broken);
			helper.assertTrue(r.live() == null, "still building: " + where(helper, r));
			for (int i = 0; i < kinds.length; i++) {
				assertHolds(helper, spots.get(i), kinds[i], things[i], 1);
			}
			assertNothingDropped(helper, List.of(things));
			// (absolute positions: GameTestHelper.relativePos turns an unrotated test half round on 1.21.1)
			List<BlockPos> spotsAbs = watched.keySet().stream().map(helper::absolutePos).toList();
			List<String> wrong = r.plan().unfinished(r.level()).stream().filter(p -> !spotsAbs.contains(p)).map(p -> rel(helper, p)).toList();
			helper.assertTrue(wrong.isEmpty(), "the hut isn't finished round the containers: " + wrong);
		});
	}

	/**
	 * A barrel holding emeralds stands on a block of dirt inside the hut. CLEAR (top-down) takes the dirt away from under
	 * it, but the barrel stays where it is with its emeralds.
	 */
	//$ gametest_ticks_batch AREA '3000' '"qaInv1031ClearUnder"'
	@GameTest(template = AREA, timeoutTicks = 3000, batch = "qaInv1031ClearUnder")
	public void aBarrelOnDirtInTheHutKeepsItsPlaceWhileTheDirtUnderItIsCleared(GameTestHelper helper) {
		rules(helper, true);
		BlockPos dirt = new BlockPos(8, 3, 8);
		BlockPos barrel = new BlockPos(8, 4, 8);
		helper.setBlock(dirt, Blocks.DIRT);
		helper.setBlock(barrel, Blocks.BARREL);
		((Container) helper.getBlockEntity(barrel)).setItem(0, new ItemStack(Items.EMERALD, 7));
		Run r = start(helper, HUT_ORIGIN);
		helper.assertTrue(airInside(helper, r.plan()).containsAll(List.of(dirt, barrel)),
			"setup: the drawing doesn't have air at the dirt and the barrel");
		List<String> broken = new ArrayList<>();
		watch(helper, Map.of(barrel, Blocks.BARREL), broken, r);
		helper.succeedWhen(() -> {
			helper.assertTrue(broken.isEmpty(), "the barrel on the dirt was broken: " + broken);
			helper.assertTrue(r.live() == null, "still building: " + where(helper, r));
			helper.assertTrue(helper.getBlockState(dirt).isAir(), "the dirt under the barrel wasn't cleared: " + helper.getBlockState(dirt));
			assertHolds(helper, barrel, Blocks.BARREL, Items.EMERALD, 7);
			assertNothingDropped(helper, List.of(Items.EMERALD));
		});
	}

	// ------------------------------------------------------------------------------------------------- FOUNDATION

	/**
	 * The hut placed two blocks above the ground, so FOUNDATION fills under it; a chest and a barrel, each holding
	 * something, stand in the gap under the floor, and a hopper is put on a planned foundation spot after the plan is made.
	 * FOUNDATION fills round them, never over them: each stays with what it holds and the hut finishes.
	 */
	//$ gametest_ticks_batch BIG_AREA '4000' '"qaInv1031Foundation"'
	@GameTest(template = BIG_AREA, timeoutTicks = 4000, batch = "qaInv1031Foundation")
	public void foundationFillsRoundContainersUnderARaisedHut(GameTestHelper helper) {
		rules(helper, true);
		BlockPos chest = new BlockPos(8, 2, 8);
		BlockPos barrel = new BlockPos(6, 3, 6);
		helper.setBlock(chest, Blocks.CHEST);
		((Container) helper.getBlockEntity(chest)).setItem(0, new ItemStack(Items.DIAMOND, 4));
		helper.setBlock(barrel, Blocks.BARREL);
		((Container) helper.getBlockEntity(barrel)).setItem(0, new ItemStack(Items.GOLD_INGOT, 5));
		Run r = start(helper, new BlockPos(6, 4, 6));
		List<BuildPlan.Step> foundation = r.plan().steps(BuildPlan.Stage.FOUNDATION);
		helper.assertTrue(foundation.size() >= 25, "setup: the raised hut has only " + foundation.size() + " foundation steps");
		// and a hopper of iron a player puts on a planned foundation spot after the plan was made
		BlockPos hopper = new BlockPos(10, 2, 10);
		helper.assertTrue(foundation.stream().anyMatch(st -> st.pos().equals(helper.absolutePos(hopper))), "setup: " + hopper.toShortString()
			+ " isn't a foundation step");
		helper.setBlock(hopper, Blocks.HOPPER);
		((Container) helper.getBlockEntity(hopper)).setItem(0, new ItemStack(Items.IRON_INGOT, 2));
		Map<BlockPos, Block> watched = Map.of(chest, Blocks.CHEST, barrel, Blocks.BARREL, hopper, Blocks.HOPPER);
		List<String> broken = new ArrayList<>();
		watch(helper, watched, broken, r);
		helper.succeedWhen(() -> {
			helper.assertTrue(broken.isEmpty(), "FOUNDATION broke a container under the hut: " + broken);
			helper.assertTrue(r.live() == null, "still building: " + where(helper, r));
			assertHolds(helper, chest, Blocks.CHEST, Items.DIAMOND, 4);
			assertHolds(helper, barrel, Blocks.BARREL, Items.GOLD_INGOT, 5);
			assertHolds(helper, hopper, Blocks.HOPPER, Items.IRON_INGOT, 2);
			assertNothingDropped(helper, List.of(Items.DIAMOND, Items.GOLD_INGOT, Items.IRON_INGOT));
			BlockPos hopperAbs = helper.absolutePos(hopper);
			List<String> wrong = r.plan().unfinished(r.level()).stream().filter(p -> !p.equals(hopperAbs)).map(p -> rel(helper, p)).toList();
			helper.assertTrue(wrong.isEmpty(), "the raised hut isn't finished round the hopper: " + wrong);
		});
	}

	// ------------------------------------------------------------------------------------------------- LANDSCAPE

	/**
	 * Dirt mounds round the hut for LANDSCAPE to dig away; a chest holding diamonds stands on one of them and a barrel
	 * holding emeralds stands on the ground of the leveled ring, and once the plan is made a player swaps another mound for a
	 * barrel of gold (so a landscaping step meets it). LANDSCAPE levels the other mounds but never breaks a container, and
	 * nothing they hold is dropped.
	 */
	//$ gametest_ticks_batch AREA '3000' '"qaInv1031Landscape"'
	@GameTest(template = AREA, timeoutTicks = 3000, batch = "qaInv1031Landscape")
	public void landscapingNeverBreaksAContainerOnTheRing(GameTestHelper helper) {
		rules(helper, true);
		List<BlockPos> mounds = List.of(new BlockPos(5, 2, 8), new BlockPos(11, 2, 8), new BlockPos(8, 2, 11), new BlockPos(5, 2, 10));
		for (BlockPos p : mounds) {
			helper.setBlock(p, Blocks.DIRT);
		}
		BlockPos chest = new BlockPos(5, 3, 8);
		BlockPos barrel = new BlockPos(11, 2, 6);
		helper.setBlock(chest, Blocks.CHEST);
		((Container) helper.getBlockEntity(chest)).setItem(0, new ItemStack(Items.DIAMOND, 3));
		helper.setBlock(barrel, Blocks.BARREL);
		((Container) helper.getBlockEntity(barrel)).setItem(0, new ItemStack(Items.EMERALD, 6));
		Run r = start(helper, HUT_ORIGIN);
		long planned = r.plan().steps(BuildPlan.Stage.LANDSCAPE).stream().filter(s -> mounds.stream().anyMatch(m -> helper.absolutePos(m).equals(s.pos()))).count();
		helper.assertTrue(planned >= 3, "setup: only " + planned + " of the mounds are in the landscaping plan");
		// a player swaps one planned mound for a barrel of gold after the plan was made: the step meets a container
		BlockPos swapped = mounds.get(3);
		helper.assertTrue(r.plan().steps(BuildPlan.Stage.LANDSCAPE).stream().anyMatch(st -> st.pos().equals(helper.absolutePos(swapped))),
			"setup: the mound at " + swapped.toShortString() + " isn't in the landscaping plan");
		helper.setBlock(swapped, Blocks.BARREL);
		((Container) helper.getBlockEntity(swapped)).setItem(0, new ItemStack(Items.GOLD_INGOT, 4));
		List<String> broken = new ArrayList<>();
		watch(helper, Map.of(chest, Blocks.CHEST, barrel, Blocks.BARREL, swapped, Blocks.BARREL), broken, r);
		helper.succeedWhen(() -> {
			helper.assertTrue(broken.isEmpty(), "LANDSCAPE broke a container: " + broken);
			helper.assertTrue(r.live() == null, "still building: " + where(helper, r));
			assertHolds(helper, chest, Blocks.CHEST, Items.DIAMOND, 3);
			assertHolds(helper, barrel, Blocks.BARREL, Items.EMERALD, 6);
			assertHolds(helper, swapped, Blocks.BARREL, Items.GOLD_INGOT, 4);
			assertNothingDropped(helper, List.of(Items.DIAMOND, Items.EMERALD, Items.GOLD_INGOT));
			for (BlockPos p : List.of(mounds.get(1), mounds.get(2))) {
				helper.assertTrue(helper.getBlockState(p).isAir(), "the mound at " + p.toShortString() + " wasn't leveled: " + helper.getBlockState(p));
			}
		});
	}

	/**
	 * Holes in the ground round the hut for LANDSCAPE to fill with dirt, and the chest holds exactly the hut's material
	 * list (no dirt at all). LANDSCAPE never waits for materials: the site never says it is waiting while landscaping, no
	 * dirt is ever asked for, and the build is done.
	 */
	//$ gametest_ticks_batch AREA '3000' '"qaInv1031LandscapeNoDirt"'
	@GameTest(template = AREA, timeoutTicks = 3000, batch = "qaInv1031LandscapeNoDirt")
	public void landscapingWithNoDirtInTheChestNeverWaits(GameTestHelper helper) {
		rules(helper, false);
		List<BlockPos> holes = List.of(new BlockPos(5, 1, 8), new BlockPos(11, 1, 8), new BlockPos(8, 1, 11), new BlockPos(5, 1, 10));
		for (BlockPos p : holes) {
			helper.setBlock(p, Blocks.AIR);
		}
		Run r = start(helper, HUT_ORIGIN);
		Map<Item, Integer> needs = r.plan().materials();
		helper.assertTrue(!needs.containsKey(Items.DIRT), "setup: the hut's material list asks for dirt: " + needs);
		long planned = r.plan().steps(BuildPlan.Stage.LANDSCAPE).stream().filter(s -> holes.stream().anyMatch(h -> helper.absolutePos(h).equals(s.pos()))).count();
		helper.assertTrue(planned >= 1, "setup: none of the holes is in the landscaping plan: "
			+ r.plan().steps(BuildPlan.Stage.LANDSCAPE).stream().map(s -> rel(helper, s.pos()) + "=" + s.state()).limit(12).toList());
		fill(helper, needs, 0);
		List<String> waits = new ArrayList<>();
		boolean[] landscaped = {false};
		helper.onEachTick(() -> {
			BuildSite s = r.live();
			if (s == null) {
				return;
			}
			if (s.stage() == BuildPlan.Stage.LANDSCAPE) {
				landscaped[0] = true;
				if (s.status() == BuildSite.Status.WAITING_FOR_MATERIALS && waits.isEmpty()) {
					waits.add(where(helper, r));
				}
			}
			if (s.missing().containsKey(Items.DIRT) && waits.stream().noneMatch(w -> w.startsWith("dirt"))) {
				waits.add("dirt asked for in " + where(helper, r));
			}
		});
		helper.succeedWhen(() -> {
			helper.assertTrue(waits.isEmpty(), "LANDSCAPE waited for materials: " + waits);
			helper.assertTrue(r.live() == null, "still building: " + where(helper, r));
			helper.assertTrue(landscaped[0], "the build never went through LANDSCAPE");
			List<String> wrong = r.plan().unfinished(r.level()).stream().map(p -> rel(helper, p)).toList();
			helper.assertTrue(wrong.isEmpty(), "the hut isn't finished: " + wrong);
		});
	}

	// ------------------------------------------------------------------------------------------------- the switch

	/**
	 * {@code workplaceFreeMaterials} on, the chest empty: the Builder puts up part of the hut, then the rule is switched
	 * off. He stops cleanly: the site waits for materials, says what is missing, and for 100 ticks no block of the hut
	 * goes up (he has nothing to put up). Then the chest is filled and he finishes the hut, taking from it.
	 */
	//$ gametest_ticks_batch AREA '4000' '"qaInv1031FreeOff"'
	@GameTest(template = AREA, timeoutTicks = 4000, batch = "qaInv1031FreeOff")
	public void freeMaterialsSwitchedOffMidBuildWaitsAndFinishesWhenFilled(GameTestHelper helper) {
		ServerLevel level = rules(helper, true);
		Run r = start(helper, HUT_ORIGIN);
		Map<Item, Integer> needs = r.plan().materials();
		int[] phase = {0};
		int[] mark = {0, 0};
		String[] waited = {null};
		helper.onEachTick(() -> {
			BuildSite s = r.live();
			long built = r.plan().targets().size() - r.plan().unfinished(level).size();
			switch (phase[0]) {
				case 0 -> {
					if (s != null && s.stage() == BuildPlan.Stage.STRUCTURE && s.cursor() >= 6) {
						level.getGameRules().getRule(ModGameRules.FREE_MATERIALS).set(false, level.getServer());
						phase[0] = 1;
					}
				}
				case 1 -> {
					helper.assertTrue(s != null, "the build ended though the rule was switched off with an empty chest");
					if (s.status() == BuildSite.Status.WAITING_FOR_MATERIALS && !s.missing().isEmpty()) {
						mark[0] = (int) built;
						mark[1] = 0;
						waited[0] = s.stage() + " missing " + s.missing();
						phase[0] = 2;
					}
				}
				case 2 -> {
					helper.assertTrue(s != null, "the build ended while waiting for materials");
					helper.assertTrue(built <= mark[0], "a block of the hut went up while the Builder waited with an empty chest: " + mark[0] + " -> " + built
						+ " (" + where(helper, r) + ")");
					if (++mark[1] >= 100) {
						fill(helper, needs, 0);
						phase[0] = 3;
					}
				}
				default -> {
				}
			}
		});
		helper.succeedWhen(() -> {
			helper.assertTrue(phase[0] == 3, "never reached the wait and the refill: phase " + phase[0] + ", " + where(helper, r));
			helper.assertTrue(r.live() == null, "still building after the refill: " + where(helper, r));
			List<String> wrong = r.plan().unfinished(level).stream().map(p -> rel(helper, p)).toList();
			helper.assertTrue(wrong.isEmpty(), "the hut isn't finished after the refill: " + wrong);
			Container chest = helper.getBlockEntity(CHEST);
			int left = needs.keySet().stream().mapToInt(chest::countItem).sum();
			int given = needs.values().stream().mapToInt(Integer::intValue).sum();
			helper.assertTrue(left < given, "the Builder finished without taking anything from the chest (waited: " + waited[0] + ")");
		});
	}

	// ------------------------------------------------------------------------------------------------- break + restart

	/**
	 * Mid-STRUCTURE a player breaks a block the Builder already laid, and the server restarts right after. The hut still
	 * ends whole (the broken block is laid again), from a chest holding the material list plus one spare of each.
	 */
	//$ gametest_ticks_batch AREA '4000' '"qaInv1031BreakRestart"'
	@GameTest(template = AREA, timeoutTicks = 4000, batch = "qaInv1031BreakRestart")
	public void aBlockBrokenMidStructureThenARestartStillEndsWhole(GameTestHelper helper) {
		ServerLevel level = rules(helper, false);
		Run r = start(helper, HUT_ORIGIN);
		fill(helper, r.plan().materials(), 1);
		String[] done = {null};
		helper.onEachTick(() -> {
			BuildSite s = r.live();
			if (done[0] != null || s == null || s.stage() != BuildPlan.Stage.STRUCTURE || s.cursor() < 12) {
				return;
			}
			List<BuildPlan.Step> steps = r.plan().steps(BuildPlan.Stage.STRUCTURE);
			BlockPos victim = null;
			for (int i = 0; i < Math.min(s.cursor(), steps.size()); i++) {
				BuildPlan.Step step = steps.get(i);
				if (!step.state().isAir() && level.getBlockState(step.pos()).is(step.state().getBlock())) {
					victim = step.pos();
					break;
				}
			}
			helper.assertTrue(victim != null, "no laid structure block to break at cursor " + s.cursor());
			level.destroyBlock(victim, false);
			helper.assertTrue(level.getBlockState(victim).isAir(), "the block wasn't broken");
			restart(helper, r);
			done[0] = rel(helper, victim) + " at cursor " + s.cursor();
		});
		helper.succeedWhen(() -> {
			helper.assertTrue(done[0] != null, "never got far enough into STRUCTURE: " + where(helper, r));
			helper.assertTrue(r.live() == null, "still building: " + where(helper, r));
			List<String> wrong = r.plan().unfinished(level).stream().map(p -> rel(helper, p) + "=" + level.getBlockState(p)).collect(Collectors.toList());
			helper.assertTrue(wrong.isEmpty(), "after a block was broken (" + done[0] + ") and a restart the hut isn't whole: " + wrong);
		});
	}
	// ------------------------------------------------------------------------------------------------- restart while waiting

	/**
	 * The chest empty and materials not free: the Builder waits for materials. The server restarts while he waits. After
	 * it he is still waiting, asks for the same things, and for 60 ticks puts up nothing. Then the chest is filled with
	 * exactly the material list and the hut is built from it, all of it used (nothing doubled, nothing short).
	 */
	//$ gametest_ticks_batch AREA '4000' '"qaInv1031WaitRestart"'
	@GameTest(template = AREA, timeoutTicks = 4000, batch = "qaInv1031WaitRestart")
	public void aRestartWhileWaitingForMaterialsKeepsTheShortfallAndBuildsNothing(GameTestHelper helper) {
		ServerLevel level = rules(helper, false);
		Run r = start(helper, HUT_ORIGIN);
		Map<Item, Integer> needs = r.plan().materials();
		int[] phase = {0};
		int[] mark = {0, 0};
		String[] before = {null};
		helper.onEachTick(() -> {
			BuildSite s = r.live();
			long built = r.plan().targets().size() - r.plan().unfinished(level).size();
			switch (phase[0]) {
				case 0 -> {
					if (s != null && s.status() == BuildSite.Status.WAITING_FOR_MATERIALS && !s.missing().isEmpty()) {
						before[0] = s.stage() + " missing " + s.missing();
						Map<Item, Integer> missing = new LinkedHashMap<>(s.missing());
						mark[0] = (int) built;
						restart(helper, r);
						BuildSite back = r.live();
						helper.assertTrue(back != null, "the waiting site is gone after the restart");
						helper.assertTrue(back.stage() == s.stage(), "the restart moved the waiting site from " + s.stage() + " to " + back.stage());
						helper.assertTrue(back.missing().isEmpty() || back.missing().equals(missing), "the restart changed what is missing from " + missing
							+ " to " + back.missing());
						phase[0] = 1;
					}
				}
				case 1 -> {
					helper.assertTrue(s != null, "the build ended with an empty chest after the restart");
					helper.assertTrue(built <= mark[0], "a block of the hut went up after the restart with an empty chest: " + mark[0] + " -> " + built
						+ " (" + where(helper, r) + ")");
					if (++mark[1] >= 60) {
						helper.assertTrue(s.status() == BuildSite.Status.WAITING_FOR_MATERIALS && !s.missing().isEmpty(),
							"60 ticks after the restart the empty-handed Builder isn't waiting for materials: " + where(helper, r) + " (before: " + before[0] + ")");
						fill(helper, needs, 0);
						phase[0] = 2;
					}
				}
				default -> {
				}
			}
		});
		helper.succeedWhen(() -> {
			helper.assertTrue(phase[0] == 2, "never waited for materials: phase " + phase[0] + ", " + where(helper, r));
			helper.assertTrue(r.live() == null, "still building after the refill: " + where(helper, r));
			List<String> wrong = r.plan().unfinished(level).stream().map(p -> rel(helper, p)).toList();
			helper.assertTrue(wrong.isEmpty(), "the hut isn't finished after the refill: " + wrong);
			Container chest = helper.getBlockEntity(CHEST);
			Map<Item, Integer> left = new LinkedHashMap<>();
			for (Item item : needs.keySet()) {
				int n = chest.countItem(item) + ModAttachments.BUILDER_BAG.getOrCreate(r.villager()).stacks().stream().filter(st -> st.is(item))
					.mapToInt(ItemStack::getCount).sum();
				if (n > 0) {
					left.put(item, n);
				}
			}
			helper.assertTrue(left.isEmpty(), "the hut is built but some of its exact material list is left over (doubled across the restart): " + left);
		});
	}

	// ------------------------------------------------------------------------------------------------- a crew

	/**
	 * A lead Builder and an idle Builder at a bench nearby (helping on), a barrel holding emeralds where a side wall goes
	 * and a dropper holding redstone inside the hut. The helper joins, and neither he nor the lead breaks either container:
	 * both stay with their things and the hut finishes round them.
	 */
	//$ gametest_ticks_batch AREA '4000' '"qaInv1031Crew"'
	@GameTest(template = AREA, timeoutTicks = 4000, batch = "qaInv1031Crew")
	public void aCrewNeverBreaksAContainerInTheHut(GameTestHelper helper) {
		ServerLevel level = rules(helper, false);
		level.getGameRules().getRule(ModGameRules.BUILDERS_HELP).set(true, level.getServer());
		BlockPos mateBench = new BlockPos(2, 2, 8);
		helper.setBlock(mateBench, ModBlocks.BUILDERS_BENCH);
		Villager mate = helper.spawn(EntityType.VILLAGER, new BlockPos(3, 2, 9));
		Builders.employ(level, mate, helper.absolutePos(mateBench));
		BlockPos wall = new BlockPos(6, 3, 8);
		BlockPos inside = new BlockPos(8, 3, 8);
		Run r = start(helper, HUT_ORIGIN);
		helper.assertTrue(r.plan().targets().containsKey(helper.absolutePos(wall)), "setup: the drawing has nothing at the wall spot " + wall.toShortString());
		helper.assertTrue(airInside(helper, r.plan()).contains(inside), "setup: the drawing isn't air at " + inside.toShortString());
		fill(helper, r.plan().materials(), 0);
		helper.setBlock(wall, Blocks.BARREL);
		((Container) helper.getBlockEntity(wall)).setItem(0, new ItemStack(Items.EMERALD, 5));
		helper.setBlock(inside, Blocks.DROPPER);
		((Container) helper.getBlockEntity(inside)).setItem(0, new ItemStack(Items.REDSTONE, 9));
		Map<BlockPos, Block> watched = Map.of(wall, Blocks.BARREL, inside, Blocks.DROPPER);
		List<String> broken = new ArrayList<>();
		watch(helper, watched, broken, r);
		boolean[] helped = {false};
		helper.onEachTick(() -> helped[0] |= Builders.isHelping(mate));
		helper.succeedWhen(() -> {
			helper.assertTrue(broken.isEmpty(), "the crew broke a container: " + broken);
			helper.assertTrue(r.live() == null, "still building: " + where(helper, r));
			helper.assertTrue(helped[0], "setup: the idle Builder never helped");
			assertHolds(helper, wall, Blocks.BARREL, Items.EMERALD, 5);
			assertHolds(helper, inside, Blocks.DROPPER, Items.REDSTONE, 9);
			assertNothingDropped(helper, List.of(Items.EMERALD, Items.REDSTONE));
		});
	}
	// ------------------------------------------------------------------------------------------------- more containers

	/**
	 * A player's ender chest stands inside the hut. It is a chest to the player (it opens his ender inventory) and, broken
	 * without Silk Touch, turns into obsidian, so CLEAR must leave it standing like every other chest.
	 */
	//$ gametest_ticks_batch AREA '3000' '"qaInv1031EnderChest"'
	@GameTest(template = AREA, timeoutTicks = 3000, batch = "qaInv1031EnderChest")
	public void clearKeepsAnEnderChestInsideTheHut(GameTestHelper helper) {
		rules(helper, true);
		BlockPos ender = new BlockPos(8, 3, 8);
		helper.setBlock(ender, Blocks.ENDER_CHEST);
		Run r = start(helper, HUT_ORIGIN);
		helper.assertTrue(airInside(helper, r.plan()).contains(ender), "setup: the drawing isn't air at " + ender.toShortString());
		List<String> broken = new ArrayList<>();
		watch(helper, Map.of(ender, Blocks.ENDER_CHEST), broken, r);
		helper.succeedWhen(() -> {
			helper.assertTrue(r.live() == null, "still building: " + where(helper, r));
			helper.assertTrue(broken.isEmpty(), "CLEAR broke the ender chest: " + broken);
		});
	}

	/**
	 * A lectern holding a written book and a jukebox holding a music disc stand inside the hut. Whatever CLEAR does with
	 * the blocks, the book and the disc are never lost: each is still in its block, or in the Builder's chest or bag, or
	 * on the ground for the player.
	 */
	//$ gametest_ticks_batch AREA '3000' '"qaInv1031BookDisc"'
	@GameTest(template = AREA, timeoutTicks = 3000, batch = "qaInv1031BookDisc")
	public void clearNeverLosesALecternsBookOrAJukeboxsDisc(GameTestHelper helper) {
		ServerLevel level = rules(helper, true);
		BlockPos lectern = new BlockPos(7, 3, 8);
		BlockPos jukebox = new BlockPos(9, 3, 8);
		Run r = start(helper, HUT_ORIGIN);
		helper.assertTrue(airInside(helper, r.plan()).containsAll(List.of(lectern, jukebox)), "setup: the drawing isn't air at the lectern and jukebox");
		helper.setBlock(lectern, Blocks.LECTERN);
		net.minecraft.world.level.block.LecternBlock.tryPlaceBook(null, level, helper.absolutePos(lectern), helper.getBlockState(lectern),
			new ItemStack(Items.WRITABLE_BOOK));
		helper.setBlock(jukebox, Blocks.JUKEBOX);
		((net.minecraft.world.level.block.entity.JukeboxBlockEntity) helper.getBlockEntity(jukebox)).setTheItem(new ItemStack(Items.MUSIC_DISC_CAT));
		helper.assertTrue(helper.getBlockEntity(lectern) instanceof net.minecraft.world.level.block.entity.LecternBlockEntity l && l.hasBook(),
			"setup: the lectern has no book");
		helper.succeedWhen(() -> {
			helper.assertTrue(r.live() == null, "still building: " + where(helper, r));
			int books = count(helper, r, Items.WRITABLE_BOOK)
				+ (helper.getBlockEntity(lectern) instanceof net.minecraft.world.level.block.entity.LecternBlockEntity l && l.hasBook() ? 1 : 0);
			int discs = count(helper, r, Items.MUSIC_DISC_CAT)
				+ (helper.getBlockEntity(jukebox) instanceof net.minecraft.world.level.block.entity.JukeboxBlockEntity j && j.getTheItem().is(Items.MUSIC_DISC_CAT) ? 1 : 0);
			helper.assertTrue(books == 1, "the lectern's book is " + (books == 0 ? "lost" : "doubled: " + books) + "; lectern spot is " + helper.getBlockState(lectern));
			helper.assertTrue(discs == 1, "the jukebox's disc is " + (discs == 0 ? "lost" : "doubled: " + discs) + "; jukebox spot is " + helper.getBlockState(jukebox));
		});
	}

	/** How many of {@code item} are in the supply chest, the Builder's bag and on the ground round the test. */
	private static int count(GameTestHelper helper, Run r, Item item) {
		Container chest = helper.getBlockEntity(CHEST);
		int n = chest.countItem(item);
		n += ModAttachments.BUILDER_BAG.getOrCreate(r.villager()).stacks().stream().filter(st -> st.is(item)).mapToInt(ItemStack::getCount).sum();
		n += helper.getLevel().getEntitiesOfClass(ItemEntity.class, new AABB(helper.absolutePos(BlockPos.ZERO)).inflate(24)).stream()
			.filter(e -> e.getItem().is(item)).mapToInt(e -> e.getItem().getCount()).sum();
		return n;
	}

	/**
	 * Mid-STRUCTURE a player puts a barrel holding emeralds on the spot where the hut's torch (a DECORATION step) goes.
	 * The Builder never breaks it: the torch step is put off and skipped, the barrel keeps its emeralds, and the build ends.
	 */
	//$ gametest_ticks_batch AREA '3000' '"qaInv1031DecorSpot"'
	@GameTest(template = AREA, timeoutTicks = 3000, batch = "qaInv1031DecorSpot")
	public void aBarrelPutOnTheTorchSpotMidBuildIsKeptAndTheTorchSkipped(GameTestHelper helper) {
		ServerLevel level = rules(helper, true);
		// a mound for LANDSCAPE, so the site is still there to read the skipped count after DECORATION ends
		helper.setBlock(new BlockPos(5, 2, 8), Blocks.DIRT);
		helper.setBlock(new BlockPos(11, 2, 8), Blocks.DIRT);
		Run r = start(helper, HUT_ORIGIN);
		BuildPlan.Step torch = r.plan().steps(BuildPlan.Stage.DECORATION).stream().filter(st -> st.state().is(Blocks.TORCH) || st.state().is(Blocks.WALL_TORCH))
			.findFirst().orElse(null);
		helper.assertTrue(torch != null, "setup: the hut has no torch to decorate with: " + r.plan().steps(BuildPlan.Stage.DECORATION));
		BlockPos spot = torch.pos();
		boolean[] placed = {false};
		int[] maxSkipped = {0};
		List<String> broken = new ArrayList<>();
		String[] last = {""};
		helper.onEachTick(() -> {
			BuildSite s = r.live();
			if (s != null) {
				maxSkipped[0] = Math.max(maxSkipped[0], s.skipped());
				last[0] = where(helper, r);
			}
			if (!placed[0] && s != null && s.stage() == BuildPlan.Stage.STRUCTURE && s.cursor() >= 4) {
				level.setBlockAndUpdate(spot, Blocks.BARREL.defaultBlockState());
				((Container) level.getBlockEntity(spot)).setItem(0, new ItemStack(Items.EMERALD, 8));
				placed[0] = true;
			}
			if (placed[0] && !level.getBlockState(spot).is(Blocks.BARREL) && broken.isEmpty()) {
				broken.add("became " + level.getBlockState(spot) + " in " + where(helper, r));
			}
		});
		helper.succeedWhen(() -> {
			helper.assertTrue(placed[0], "never got into STRUCTURE to put the barrel down: " + where(helper, r));
			helper.assertTrue(r.live() == null, "still building: " + where(helper, r));
			helper.assertTrue(broken.isEmpty(), "the barrel on the torch spot was broken: " + broken);
			Container c = (Container) level.getBlockEntity(spot);
			helper.assertTrue(c != null && c.countItem(Items.EMERALD) == 8, "the barrel lost its emeralds: " + (c == null ? "none" : c.countItem(Items.EMERALD)));
			helper.assertTrue(maxSkipped[0] >= 1, "the torch step blocked by the barrel was never counted as skipped (last seen: " + last[0]
				+ "; torch at " + rel(helper, spot) + ")");
			assertNothingDropped(helper, List.of(Items.EMERALD));
		});
	}
	// ------------------------------------------------------------------------------------------------- Scan Tool

	/**
	 * A row of containers the earlier scan tests leave out (hopper, dispenser, dropper, barrel, shulker box, crafter,
	 * trapped chest), each holding five diamonds, scanned with real Scan Tool clicks and built by a Builder from exactly
	 * the material list. Builders never copy what is inside containers: the list never asks for a diamond, every built
	 * container is empty, and no diamond turns up anywhere (chest, bag or ground).
	 */
	//$ gametest_ticks_batch HUGE '4000' '"qaInv1031ScanKinds"'
	@GameTest(template = HUGE, timeoutTicks = 4000, batch = "qaInv1031ScanKinds")
	public void aScannedRowOfFullContainersIsBuiltEmpty(GameTestHelper helper) {
		ServerLevel level = rules(helper, false);
		BlockPos src = new BlockPos(14, 2, 24);
		Block[] kinds = {Blocks.HOPPER, Blocks.DISPENSER, Blocks.DROPPER, Blocks.BARREL, Blocks.SHULKER_BOX, Blocks.CRAFTER, Blocks.TRAPPED_CHEST};
		for (int i = 0; i < kinds.length; i++) {
			helper.setBlock(src.east(i), kinds[i]);
			((Container) helper.getBlockEntity(src.east(i))).setItem(0, new ItemStack(Items.DIAMOND, 5));
		}
		List<net.minecraft.network.chat.Component> seen = new ArrayList<>();
		net.minecraft.server.level.ServerPlayer player = QaStageThreeGameTests.listener(helper, seen);
		player.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND, new ItemStack(io.github.jcondedata.aliveworkplace.registry.ModItems.SCAN_TOOL));
		player.getInventory().add(new ItemStack(io.github.jcondedata.aliveworkplace.registry.ModItems.BLANK_BLUEPRINT, 1));
		QaStageThreeGameTests.click(helper, player, src.below());
		QaStageThreeGameTests.click(helper, player, src.east(kinds.length));
		player.setShiftKeyDown(true);
		QaStageThreeGameTests.clickAir(helper, player);
		player.setShiftKeyDown(false);
		helper.assertTrue(QaStageThreeGameTests.last(seen).equals("message.aliveworkplace.scan.saved"), "the scan said " + QaStageThreeGameTests.last(seen));
		ItemStack blueprint = player.getInventory().items.stream().filter(st -> st.is(io.github.jcondedata.aliveworkplace.registry.ModItems.BLUEPRINT))
			.findFirst().orElse(ItemStack.EMPTY);
		helper.assertTrue(!blueprint.isEmpty(), "the scan gave no blueprint");
		ResourceLocation id = io.github.jcondedata.aliveworkplace.blueprint.BlueprintItem.data(blueprint).orElseThrow().structure();
		// the source row taken away, its diamonds with it, so any diamond seen later came from the build
		for (int i = 0; i <= kinds.length; i++) {
			BlockPos p = src.east(i);
			if (level.getBlockEntity(helper.absolutePos(p)) instanceof Container c) {
				c.clearContent();
			}
			helper.setBlock(p, Blocks.AIR);
		}
		helper.setBlock(BENCH, ModBlocks.BUILDERS_BENCH);
		Villager builder = helper.spawn(EntityType.VILLAGER, BENCH.east());
		Builders.employ(level, builder, helper.absolutePos(BENCH));
		BlockPos origin = new BlockPos(10, 1, 10);
		BlueprintData.Placement placement = new BlueprintData.Placement(level.dimension().location(), helper.absolutePos(origin), Rotation.NONE, Mirror.NONE);
		BuildSite site = Builders.start(level, builder, null, id, placement);
		helper.assertTrue(site != null, "the builder didn't take the scanned blueprint " + id);
		BuildPlan plan = site.plan(level);
		Map<Item, Integer> asked = plan.materials();
		helper.assertTrue(!asked.containsKey(Items.DIAMOND), "the scanned row's list asks for diamonds: " + asked);
		for (Block kind : kinds) {
			helper.assertTrue(asked.containsKey(kind.asItem()), "the scanned row's list doesn't ask for a " + kind + ": " + asked);
		}
		helper.setBlock(CHEST, Blocks.CHEST);
		fill(helper, asked, 0);
		Run r = new Run(level, new Villager[]{builder}, site.id(), plan, placement);
		helper.succeedWhen(() -> {
			helper.assertTrue(r.live() == null, "still building: " + where(helper, r));
			List<String> wrong = plan.unfinished(level).stream().map(p -> rel(helper, p) + "=" + level.getBlockState(p)).toList();
			helper.assertTrue(wrong.isEmpty(), "the scanned row isn't built: " + wrong);
			List<String> full = new ArrayList<>();
			int built = 0;
			for (BlockPos p : plan.targets().keySet()) {
				if (level.getBlockEntity(p) instanceof Container c) {
					built++;
					if (!c.isEmpty()) {
						full.add(rel(helper, p) + " " + level.getBlockState(p).getBlock() + " holds " + c.countItem(Items.DIAMOND) + " diamonds");
					}
				}
			}
			helper.assertTrue(built >= kinds.length, "only " + built + " containers were built of " + kinds.length);
			helper.assertTrue(full.isEmpty(), "a built container holds what the scanned one held: " + full);
			helper.assertTrue(count(helper, r, Items.DIAMOND) == 0, count(helper, r, Items.DIAMOND) + " diamonds turned up round the build");
		});
	}
}
