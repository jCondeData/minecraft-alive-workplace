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
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.Set;
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
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings;
import net.minecraft.world.phys.AABB;

/**
 * QA (lane-b-1101-0338), from CLAUDE.md "How the builder works": "All progress lives in BuildSite (saved). BuilderWork
 * must stay restartable at any tick", "Builders never break containers or the bench while clearing, and never copy
 * container contents", and docs/wiki/builder.md ("a Builder can stop at any tick ... and carry on exactly where it
 * was"). Corners the older reload tests leave out: many restarts through one whole build (every stage, CLEAR to
 * LANDSCAPE), a restart in the middle of STRUCTURE with the Builder carrying, the supply chest broken by a player in
 * the middle of STRUCTURE and put back with what fell out, and a chest and a second bench standing in the hut's empty
 * inside while it is cleared. Each must end with the hut as drawn and nothing lost or doubled.
 */
public class QaBuilderRestart1101GameTests {
	private static final String AREA = "aliveworkplace_test:build_area";
	private static final ResourceLocation TEST_HUT = ResourceLocation.fromNamespaceAndPath("aliveworkplace_test", "test_hut");

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
	private static void restartIn0(GameTestHelper helper, Run r, BuildPlan.Stage stage, int fallback) {
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

	private static Map<Item, Integer> nonZero(Map<Item, Integer> m) {
		m.values().removeIf(n -> n == 0);
		return m;
	}

	// ------------------------------------------------------------------------------------------------- tests

	/**
	 * Dirt in the hut's way and mounds round it, and the server restarted every 60 ticks from the first tick to the last
	 * (each restart a fresh Builder and a site re-read from its save): every stage is restarted at least once, and the
	 * hut still finishes as drawn, the mounds dug away, with exactly the chest's materials used (nothing left over,
	 * nothing short).
	 */
	//$ gametest_ticks_batch AREA '9000' '"qaRestartStorm1101"'
	@GameTest(template = AREA, timeoutTicks = 9000, batch = "qaRestartStorm1101")
	public void aServerRestartedEverySixtyTicksStillBuildsTheHutExactly(GameTestHelper helper) {
		rules(helper);
		for (int x = 7; x <= 9; x++) {
			for (int z = 7; z <= 9; z++) {
				helper.setBlock(new BlockPos(x, 3, z), Blocks.DIRT);
			}
		}
		List<BlockPos> mounds = List.of(new BlockPos(5, 2, 8), new BlockPos(11, 2, 8), new BlockPos(8, 2, 11));
		for (BlockPos p : mounds) {
			helper.setBlock(p, Blocks.DIRT);
		}
		Run r = start(helper, hutMaterials());
		Set<BuildPlan.Stage> restartedIn = EnumSet.noneOf(BuildPlan.Stage.class);
		int[] ticks = {0};
		int[] restarts = {0};
		helper.onEachTick(() -> {
			BuildSite site = r.live();
			if (site == null || ++ticks[0] % 60 != 0) {
				return;
			}
			Map<Item, Integer> heldBefore = held(helper, r.villager());
			BuildPlan.Stage stage = site.stage();
			restart(helper, r.level(), r.builder());
			BuildSite back = r.live();
			helper.assertTrue(back != null && back.stage() == stage, "restart " + (restarts[0] + 1) + " in " + stage + " left the site "
				+ (back == null ? "gone" : back.stage()));
			helper.assertTrue(held(helper, r.villager()).equals(heldBefore), "restart " + (restarts[0] + 1) + " in " + stage
				+ " changed the materials held from " + heldBefore + " to " + held(helper, r.villager()));
			restartedIn.add(stage);
			restarts[0]++;
		});
		helper.succeedWhen(() -> {
			assertBuilt(helper, r);
			for (BlockPos p : mounds) {
				helper.assertTrue(helper.getBlockState(p).isAir(), "after " + restarts[0] + " restarts the mound at " + p.toShortString() + " is still "
					+ helper.getBlockState(p));
			}
			Map<Item, Integer> left = nonZero(held(helper, r.villager()));
			helper.assertTrue(left.isEmpty(), "after " + restarts[0] + " restarts the hut is built but its materials are still held (doubled): " + left);
			helper.assertTrue(restartedIn.containsAll(List.of(BuildPlan.Stage.CLEAR, BuildPlan.Stage.STRUCTURE)),
				"the restarts never hit CLEAR and STRUCTURE (only " + restartedIn + " in " + restarts[0] + " restarts)");
		});
	}

	/** A restart in the middle of STRUCTURE with the Builder carrying planks or cobblestone: the same cursor, the same bag. */
	//$ gametest_ticks_batch AREA '4000' '"qaRestartStructure1101"'
	@GameTest(template = AREA, timeoutTicks = 4000, batch = "qaRestartStructure1101")
	public void aRestartMidStructureWithAFullBagFinishesTheHutFromTheChest(GameTestHelper helper) {
		rules(helper);
		Run r = start(helper, hutMaterials());
		helper.assertTrue(r.plan().steps(BuildPlan.Stage.STRUCTURE).size() >= 20, "the hut's walls are too few to restart in the middle of");
		restartIn0(helper, r, BuildPlan.Stage.STRUCTURE, 40);
	}

	/**
	 * A player breaks the supply chest in the middle of STRUCTURE while the Builder is carrying: what the chest still held
	 * falls out. The hut finishes from what he carries, and at the end what fell out is exactly what the chest held when
	 * it broke and nothing of the hut's is left in his bag (nothing lost, nothing doubled, nothing made).
	 */
	//$ gametest_ticks_batch AREA '6000' '"qaRestartChestBroken1101"'
	@GameTest(template = AREA, timeoutTicks = 6000, batch = "qaRestartChestBroken1101")
	public void theSupplyChestBrokenMidStructureLosesAndMakesNothing(GameTestHelper helper) {
		rules(helper);
		Run r = start(helper, hutMaterials());
		ServerLevel level = r.level();
		AABB area = new AABB(helper.absolutePos(BlockPos.ZERO)).expandTowards(16, 8, 16).inflate(2);
		Map<Item, Integer> inChest = new LinkedHashMap<>();
		String[] note = {null};
		helper.onEachTick(() -> {
			BuildSite site = r.live();
			if (note[0] != null || site == null || site.stage() != BuildPlan.Stage.STRUCTURE || site.cursor() < 5 || bag(r.villager()).isEmpty()) {
				return;
			}
			Container chest = helper.getBlockEntity(CHEST);
			for (Item item : HUT_ITEMS) {
				inChest.put(item, chest.countItem(item));
			}
			note[0] = "broken at cursor " + site.cursor() + " with bag " + bag(r.villager()) + ", chest " + inChest;
			helper.assertTrue(level.destroyBlock(helper.absolutePos(CHEST), true), "the chest wasn't broken");
		});
		helper.succeedWhen(() -> {
			helper.assertTrue(note[0] != null, "the chest was never broken: " + where(helper, r));
			assertBuilt(helper, r);
			Map<Item, Integer> dropped = new LinkedHashMap<>();
			for (Item item : HUT_ITEMS) {
				dropped.put(item, level.getEntitiesOfClass(ItemEntity.class, area, e -> e.getItem().is(item)).stream().mapToInt(e -> e.getItem().getCount()).sum());
			}
			helper.assertTrue(dropped.equals(inChest), "on the ground " + dropped + ", wanted exactly what the chest held: " + inChest + " (" + note[0] + ")");
			Map<Item, Integer> left = new LinkedHashMap<>(bag(r.villager()));
			left.keySet().retainAll(HUT_ITEMS);
			helper.assertTrue(nonZero(left).isEmpty(), "the hut is built but the Builder still carries " + left + " (doubled; " + note[0] + ")");
		});
	}

	/**
	 * A chest holding diamonds and a second Builder's Bench (nobody's) stand in the hut's empty inside before the build
	 * starts. CLEAR must leave both standing, the chest with its diamonds, and nothing copied: no diamonds anywhere else,
	 * and the hut otherwise finished.
	 */
	//$ gametest_ticks_batch AREA '4000' '"qaRestartInsideKept1101"'
	@GameTest(template = AREA, timeoutTicks = 4000, batch = "qaRestartInsideKept1101")
	public void aChestAndABenchInsideTheHutSurviveClearingWithNothingCopied(GameTestHelper helper) {
		rules(helper);
		BlockPos inChest = new BlockPos(8, 3, 8);
		BlockPos inBench = new BlockPos(7, 3, 9);
		helper.setBlock(inChest, Blocks.CHEST);
		Container box = helper.getBlockEntity(inChest);
		box.setItem(0, new ItemStack(Items.DIAMOND, 7));
		helper.setBlock(inBench, ModBlocks.BUILDERS_BENCH);
		Run r = start(helper, hutMaterials());
		Set<BlockPos> kept = Set.of(helper.absolutePos(inChest), helper.absolutePos(inBench));
		List<String> broken = new ArrayList<>();
		helper.onEachTick(() -> {
			if (!helper.getBlockState(inChest).is(Blocks.CHEST) && broken.isEmpty()) {
				broken.add("chest became " + helper.getBlockState(inChest) + " in " + where(helper, r));
			}
			if (!helper.getBlockState(inBench).is(ModBlocks.BUILDERS_BENCH) && broken.isEmpty()) {
				broken.add("bench became " + helper.getBlockState(inBench) + " in " + where(helper, r));
			}
		});
		helper.succeedWhen(() -> {
			helper.assertTrue(broken.isEmpty(), "the builder broke a container or bench inside the hut: " + broken);
			helper.assertTrue(r.live() == null, "still building: " + where(helper, r));
			List<BlockPos> wrong = r.plan().unfinished(r.level());
			wrong.removeIf(kept::contains);
			helper.assertTrue(wrong.isEmpty(), wrong.size() + " block(s) of the hut wrong besides the kept chest and bench, e.g. " + wrong.stream().limit(6)
				.map(p -> p.subtract(r.placement().origin()).toShortString() + "=" + r.level().getBlockState(p)).collect(Collectors.joining(", ")));
			Container still = helper.getBlockEntity(inChest);
			helper.assertTrue(still.countItem(Items.DIAMOND) == 7, "the chest inside the hut holds " + still.countItem(Items.DIAMOND) + " diamonds, wanted 7");
			int elsewhere = bag(r.villager()).getOrDefault(Items.DIAMOND, 0) + ((Container) helper.getBlockEntity(CHEST)).countItem(Items.DIAMOND)
				+ r.level().getEntitiesOfClass(ItemEntity.class, new AABB(helper.absolutePos(BlockPos.ZERO)).expandTowards(16, 8, 16))
					.stream().filter(e -> e.getItem().is(Items.DIAMOND)).mapToInt(e -> e.getItem().getCount()).sum();
			helper.assertTrue(elsewhere == 0, elsewhere + " diamonds turned up outside the chest inside the hut (copied)");
			Map<Item, Integer> left = nonZero(held(helper, r.villager()));
			helper.assertTrue(left.values().stream().mapToInt(Integer::intValue).sum() <= 8, "far more of the hut's materials left than the kept spots explain: " + left);
		});
	}
}
