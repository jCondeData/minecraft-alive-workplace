package io.github.jcondedata.aliveworkplace.test;

import io.github.jcondedata.aliveworkplace.blueprint.BlueprintData;
import io.github.jcondedata.aliveworkplace.blueprint.BlueprintItem;
import io.github.jcondedata.aliveworkplace.blueprint.Blueprints;
import io.github.jcondedata.aliveworkplace.build.BuildPlan;
import io.github.jcondedata.aliveworkplace.build.BuildSite;
import io.github.jcondedata.aliveworkplace.build.BuildSiteManager;
import io.github.jcondedata.aliveworkplace.build.Builders;
import io.github.jcondedata.aliveworkplace.build.MaterialRules;
import io.github.jcondedata.aliveworkplace.registry.ModAttachments;
import io.github.jcondedata.aliveworkplace.registry.ModBlocks;
import io.github.jcondedata.aliveworkplace.registry.ModComponents;
import io.github.jcondedata.aliveworkplace.registry.ModGameRules;
import io.github.jcondedata.aliveworkplace.registry.ModItems;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.Container;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings;
import net.minecraft.world.phys.AABB;

/**
 * QA (lane-b-1026-0338) of taking a building down and cancelling a build, from the spec: the player guide ("Changed your
 * mind? Place the blueprint over the finished building and sneak while you hand it to a builder: they take it down and
 * put the blocks back in the chests"), the wiki (CLEAR "never breaks containers, its own bench or any other Builder's
 * Bench"; "Builders never copy what is inside containers"; `/workplace cancel <site>` "(the blueprint stays placed; hand
 * it back to carry on)"; the bag "empties at the chests when it finishes or stops helping"; 23.8 "cancel with materials
 * returned"), docs/guide/command-cancel.md and the lines en_us.json gives (assign.deconstruct, finished_deconstruct,
 * cancelled, not_your_builder, the blueprint's deconstruct_hint). Every order goes through the player's own clicks and
 * commands; the builder works through real ticks.
 */
public class QaTakeDownGameTests {
	private static final String AREA = "aliveworkplace_test:build_area";
	private static final ResourceLocation TEST_HUT = ResourceLocation.fromNamespaceAndPath("aliveworkplace_test", "test_hut");
	private static final ResourceLocation CAMP = ResourceLocation.fromNamespaceAndPath("aliveworkplace", "camp/settlers_camp");
	private static final BlockPos TABLE = new BlockPos(2, 2, 2);
	private static final BlockPos CHEST = new BlockPos(2, 2, 4);
	private static final BlockPos VILLAGER = new BlockPos(3, 2, 3);
	/** The hut stands at x 6-10, y 2-5, z 6-10. */
	private static final BlockPos HUT = new BlockPos(6, 2, 6);
	private static final List<Item> HUT_ITEMS = List.of(Items.COBBLESTONE, Items.OAK_PLANKS, Items.OAK_DOOR, Items.TORCH);
	private static final Map<Item, Integer> HUT_LIST = Map.of(Items.COBBLESTONE, 25, Items.OAK_PLANKS, 55, Items.OAK_DOOR, 1, Items.TORCH, 1);

	// ------------------------------------------------------------------------------------------------- helpers

	private static List<String> texts(List<Component> seen) {
		return seen.stream().map(Component::getString).toList();
	}

	/** Quick builds, no helpers, daytime, no village protection; all put back after the test. */
	private static ServerLevel rules(GameTestHelper helper) {
		Leftovers.clear(helper);
		ServerLevel level = helper.getLevel();
		helper.setDayTime(2000);
		var rules = level.getGameRules();
		int delay = rules.getRule(ModGameRules.BUILD_DELAY).get();
		boolean help = rules.getRule(ModGameRules.BUILDERS_HELP).get();
		boolean free = rules.getRule(ModGameRules.FREE_MATERIALS).get();
		boolean ownership = rules.getRule(ModGameRules.BUILDER_OWNERSHIP).get();
		rules.getRule(ModGameRules.BUILD_DELAY).set(2, level.getServer());
		rules.getRule(ModGameRules.BUILDERS_HELP).set(false, level.getServer());
		rules.getRule(ModGameRules.FREE_MATERIALS).set(false, level.getServer());
		boolean protection = io.github.jcondedata.aliveworkplace.hall.VillageProtection.ENABLED;
		io.github.jcondedata.aliveworkplace.hall.VillageProtection.ENABLED = false;
		Leftovers.after(helper, () -> {
			rules.getRule(ModGameRules.BUILD_DELAY).set(delay, level.getServer());
			rules.getRule(ModGameRules.BUILDERS_HELP).set(help, level.getServer());
			rules.getRule(ModGameRules.FREE_MATERIALS).set(free, level.getServer());
			rules.getRule(ModGameRules.BUILDER_OWNERSHIP).set(ownership, level.getServer());
			io.github.jcondedata.aliveworkplace.hall.VillageProtection.ENABLED = protection;
		});
		return level;
	}

	/** Puts {@code id} up in the world at {@code rel}, turned and mirrored, as a finished building would stand. */
	private static BlueprintData.Placement stand(GameTestHelper helper, ResourceLocation id, BlockPos rel, Rotation rotation, Mirror mirror) {
		ServerLevel level = helper.getLevel();
		BlockPos origin = helper.absolutePos(rel);
		level.getStructureManager().get(id).orElseThrow().placeInWorld(level, origin, origin,
			new StructurePlaceSettings().setRotation(rotation).setMirror(mirror), RandomSource.create(7L), Block.UPDATE_ALL);
		return new BlueprintData.Placement(level.dimension().location(), origin, rotation, mirror);
	}

	private static ItemStack placedBlueprint(ResourceLocation id, BlueprintData.Placement placement) {
		ItemStack blueprint = BlueprintItem.create(id, null);
		blueprint.set(ModComponents.BLUEPRINT, BlueprintItem.data(blueprint).orElseThrow().withPlacement(Optional.of(placement)));
		return blueprint;
	}

	private static Villager builder(GameTestHelper helper, BlockPos table, BlockPos at) {
		ServerLevel level = helper.getLevel();
		Villager villager = helper.spawn(EntityType.VILLAGER, at);
		Builders.employ(level, villager, helper.absolutePos(table));
		Leftovers.after(helper, () -> {
			BuildSiteManager sites = BuildSiteManager.get(level);
			for (BuildSite site : List.copyOf(sites.all())) {
				if (villager.getUUID().equals(site.builder())) {
					sites.remove(site.id());
				}
			}
		});
		return villager;
	}

	/** The player hands what's in their main hand to the builder, sneaking or not, as the client sends the click. */
	private static void handOver(QaStageFiveGameTests.Client c, Villager villager, boolean sneak) {
		c.player().setShiftKeyDown(sneak);
		QaStageSixGameTests.clickVillager(c.player(), villager, c.seen());
		c.player().setShiftKeyDown(false);
	}

	/** What the take-down plan still finds standing, by the items its blocks are made of. */
	private static Map<Item, Integer> standing(ServerLevel level, BuildPlan plan) {
		Map<Item, Integer> out = new LinkedHashMap<>();
		for (BuildPlan.Step step : plan.steps(BuildPlan.Stage.DECONSTRUCT)) {
			if (MaterialRules.matches(level.getBlockState(step.pos()), step.state())) {
				for (MaterialRules.Requirement r : step.requirements()) {
					out.merge(r.item(), r.count(), Integer::sum);
				}
			}
		}
		return out;
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

	private static Map<Item, Integer> ground(GameTestHelper helper) {
		Map<Item, Integer> out = new LinkedHashMap<>();
		for (ItemEntity item : helper.getLevel().getEntitiesOfClass(ItemEntity.class, helper.getBounds().inflate(8))) {
			out.merge(item.getItem().getItem(), item.getItem().getCount(), Integer::sum);
		}
		return out;
	}

	private static int inventory(ServerPlayer p, Item item) {
		int n = 0;
		for (int i = 0; i < p.getInventory().getContainerSize(); i++) {
			if (p.getInventory().getItem(i).is(item)) {
				n += p.getInventory().getItem(i).getCount();
			}
		}
		return n;
	}

	/** Every item {@code items} of the hut: still standing, in the chest, in the bag, on the ground. */
	private static Map<Item, Integer> accounted(GameTestHelper helper, BuildPlan plan, Villager villager, Container chest) {
		Map<Item, Integer> standing = standing(helper.getLevel(), plan);
		Map<Item, Integer> bag = bag(villager);
		Map<Item, Integer> ground = ground(helper);
		Map<Item, Integer> out = new LinkedHashMap<>();
		for (Item item : HUT_ITEMS) {
			out.put(item, standing.getOrDefault(item, 0) + chest.countItem(item) + bag.getOrDefault(item, 0) + ground.getOrDefault(item, 0));
		}
		return out;
	}

	private static Map<Item, Integer> chestHut(Container chest) {
		Map<Item, Integer> out = new LinkedHashMap<>();
		for (Item item : HUT_ITEMS) {
			out.put(item, chest.countItem(item));
		}
		return out;
	}

	private static Map<Item, Integer> hutList() {
		Map<Item, Integer> out = new LinkedHashMap<>();
		for (Item item : HUT_ITEMS) {
			out.put(item, HUT_LIST.get(item));
		}
		return out;
	}

	private static QaStageFiveGameTests.Client player(GameTestHelper helper, String prefix) {
		return QaStageSixGameTests.client(helper, GameType.SURVIVAL, QaStageSixGameTests.name(prefix));
	}

	// ------------------------------------------------------------------------------------------------- the tests

	/**
	 * Guide: "sneak while you hand it to a builder: they take it down and put the blocks back in the chests"; wiki: a
	 * build never breaks "containers, its own bench"; "Builders never copy what is inside containers"; en_us: "Chests,
	 * furnaces and other blocks that hold things are left standing." A finished Settlers' Camp stands with the builder
	 * working at the camp's own Blueprint Table and 7 apples in the camp's chest. The player sneak-hands the camp's
	 * placed blueprint: the camp comes down, its table and chest stay where they stand, the builder still works at the
	 * table, the chest still holds exactly its 7 apples, and every other block of the camp's material list is back in
	 * the chests (nothing missing, nothing extra, no chest or table item made out of nothing), with the blueprint.
	 */
	//$ gametest_ticks_batch AREA '5000' '"qaTakeDownCamp"'
	@GameTest(template = AREA, timeoutTicks = 5000, batch = "qaTakeDownCamp")
	public void aCampTakenDownKeepsItsTableItsChestAndWhatTheChestHolds(GameTestHelper helper) {
		ServerLevel level = rules(helper);
		BlockPos campRel = new BlockPos(4, 2, 4); // x 4-14, y 2-6, z 4-12
		BlueprintData.Placement placement = stand(helper, CAMP, campRel, Rotation.NONE, Mirror.NONE);
		BlockPos tableRel = campRel.offset(9, 0, 3);
		BlockPos campChestRel = campRel.offset(8, 2, 4); // on the wagon's tailboard (B590)
		helper.assertBlockPresent(ModBlocks.BLUEPRINT_TABLE, tableRel);
		helper.assertBlockPresent(Blocks.CHEST, campChestRel);
		Container campChest = helper.getBlockEntity(campChestRel);
		campChest.setItem(3, new ItemStack(Items.APPLE, 7));
		int blueprintsBefore = campChest.countItem(ModItems.BLUEPRINT); // (the camp's chest comes with blueprints of its own)
		BlockPos supplyRel = new BlockPos(12, 2, 14);
		helper.setBlock(supplyRel, Blocks.CHEST);
		Container supply = helper.getBlockEntity(supplyRel);
		Villager villager = builder(helper, tableRel, new BlockPos(2, 2, 14));
		BuildPlan full = BuildPlan.create(io.github.jcondedata.aliveworkplace.blueprint.BlueprintLibrary.get(level, CAMP).orElseThrow(), placement);
		Map<Item, Integer> expected = new LinkedHashMap<>(full.materials());
		expected.remove(Items.CHEST);
		expected.remove(ModBlocks.BLUEPRINT_TABLE.asItem());

		QaStageFiveGameTests.Client c = player(helper, "qaTdC");
		c.player().setItemInHand(InteractionHand.MAIN_HAND, placedBlueprint(CAMP, placement));
		handOver(c, villager, true);
		BuildSite site = Builders.activeSite(level, villager);
		helper.assertTrue(site != null && site.isDeconstruction(), "the sneak hand-over of the camp started no take-down: " + texts(c.seen()));
		BuildPlan plan = site.plan(level);
		helper.succeedWhen(() -> {
			helper.assertTrue(BuildSiteManager.get(level).get(site.id()) == null, "still taking the camp down: " + site.stage() + ", " + site.status()
				+ ", " + plan.unfinished(level).size() + " block(s) standing");
			helper.assertBlockPresent(ModBlocks.BLUEPRINT_TABLE, tableRel);
			helper.assertBlockPresent(Blocks.CHEST, campChestRel);
			helper.assertTrue(Builders.benchPos(villager).map(p -> p.equals(helper.absolutePos(tableRel))).orElse(false),
				"the builder no longer works at the camp's table: " + Builders.benchPos(villager));
			Container chestNow = helper.getBlockEntity(campChestRel);
			Map<Item, Integer> ground = ground(helper);
			Map<Item, Integer> bag = bag(villager);
			int apples = chestNow.countItem(Items.APPLE) + supply.countItem(Items.APPLE) + ground.getOrDefault(Items.APPLE, 0) + bag.getOrDefault(Items.APPLE, 0);
			helper.assertTrue(apples == 7, "the camp chest's 7 apples became " + apples + " (camp chest " + chestNow.countItem(Items.APPLE) + ")");
			List<String> wrong = new ArrayList<>();
			java.util.Set<Item> items = new java.util.LinkedHashSet<>(expected.keySet());
			items.add(Items.CHEST);
			items.add(ModBlocks.BLUEPRINT_TABLE.asItem());
			for (Item item : items) {
				int got = chestNow.countItem(item) + supply.countItem(item) + ground.getOrDefault(item, 0) + bag.getOrDefault(item, 0);
				int want = expected.getOrDefault(item, 0);
				if (got != want) {
					wrong.add(item + " " + got + " (wanted " + want + ")");
				}
			}
			helper.assertTrue(wrong.isEmpty(), "after the camp came down the chests hold the wrong blocks: " + wrong);
			int blueprints = chestNow.countItem(ModItems.BLUEPRINT) + supply.countItem(ModItems.BLUEPRINT) + inventory(c.player(), ModItems.BLUEPRINT);
			helper.assertTrue(blueprints == blueprintsBefore + 1, blueprints + " blueprints after the take-down, " + blueprintsBefore + " before (wanted one more): camp chest " + chestNow.countItem(ModItems.BLUEPRINT) + ", supply chest "
				+ supply.countItem(ModItems.BLUEPRINT) + ", player " + inventory(c.player(), ModItems.BLUEPRINT) + ", bag " + bag.getOrDefault(ModItems.BLUEPRINT, 0)
				+ ", ground " + ground.getOrDefault(ModItems.BLUEPRINT, 0) + "; the supply chest holds " + java.util.stream.IntStream.range(0, supply.getContainerSize())
				.mapToObj(supply::getItem).filter(s -> s.is(ModItems.BLUEPRINT)).map(s -> BlueprintItem.data(s).map(d -> d.structure().toString()).orElse("?")).toList());
		});
	}

	/**
	 * A hut that stands turned a quarter and mirrored, its placed blueprint turned and mirrored the same: the sneak
	 * hand-over says "<builder> will take down Test Hut and put the blocks in the chests within 8 blocks of their
	 * Blueprint Table. ...", every block comes down, the chest ends with exactly the hut's list (25 cobblestone, 55 planks,
	 * a door and a torch) and the blueprint, the bag is empty, and the player is told "<builder> took down Test Hut. The
	 * blocks and the blueprint are in the supply chests."
	 */
	//$ gametest_ticks_batch AREA '4000' '"qaTakeDownTurned"'
	@GameTest(template = AREA, timeoutTicks = 4000, batch = "qaTakeDownTurned")
	public void aTurnedMirroredHutComesDownWithExactlyItsBlocks(GameTestHelper helper) {
		ServerLevel level = rules(helper);
		// Clockwise 90 with the origin at x 10: the hut covers x 6-10, z 6-10.
		BlueprintData.Placement placement = stand(helper, TEST_HUT, new BlockPos(10, 2, 6), Rotation.CLOCKWISE_90, Mirror.LEFT_RIGHT);
		helper.setBlock(TABLE, ModBlocks.BLUEPRINT_TABLE);
		helper.setBlock(CHEST, Blocks.CHEST);
		Container chest = helper.getBlockEntity(CHEST);
		Villager villager = builder(helper, TABLE, VILLAGER);
		QaStageFiveGameTests.Client c = player(helper, "qaTdT");
		c.player().setItemInHand(InteractionHand.MAIN_HAND, placedBlueprint(TEST_HUT, placement));
		handOver(c, villager, true);
		BuildSite site = Builders.activeSite(level, villager);
		helper.assertTrue(site != null && site.isDeconstruction(), "the sneak hand-over started no take-down: " + texts(c.seen()));
		BuildPlan plan = site.plan(level);
		helper.assertTrue(standing(level, plan).equals(hutList()), "setup: the turned, mirrored hut doesn't stand as its blueprint draws it: " + standing(level, plan));
		String hut = Blueprints.displayName(TEST_HUT).getString();
		String name = villager.getDisplayName().getString();
		helper.assertTrue(texts(c.seen()).stream().anyMatch(t -> t.startsWith(name + " will take down " + hut
			+ " and put the blocks in the chests within 8 blocks of their Blueprint Table.")), "the take-down said " + texts(c.seen()));
		helper.assertTrue(c.player().getMainHandItem().isEmpty(), "the blueprint stayed in the player's hand: " + c.player().getMainHandItem());
		helper.succeedWhen(() -> {
			helper.assertTrue(BuildSiteManager.get(level).get(site.id()) == null, "still taking it down: " + site.stage() + ", " + site.status());
			helper.assertTrue(plan.unfinished(level).isEmpty(), plan.unfinished(level).size() + " block(s) of the turned hut still stand");
			helper.assertTrue(chestHut(chest).equals(hutList()) && bag(villager).isEmpty(), "the chest holds " + chestHut(chest) + " (wanted " + hutList()
				+ "), the bag " + bag(villager) + ", on the ground " + ground(helper));
			helper.assertTrue(chest.countItem(ModItems.BLUEPRINT) == 1, "the blueprint isn't in the chest: " + chest.countItem(ModItems.BLUEPRINT));
			String done = name + " took down " + hut + ". The blocks and the blueprint are in the supply chests.";
			helper.assertTrue(texts(c.seen()).contains(done), "at the end the player saw " + texts(c.seen()));
		});
	}

	/**
	 * Only a sneak hand-over takes a building down: the same placed blueprint handed over a standing hut without
	 * sneaking never breaks a block of it, and nothing of it reaches the chest.
	 */
	//$ gametest_ticks_batch AREA '700' '"qaTakeDownPlain"'
	@GameTest(template = AREA, timeoutTicks = 700, batch = "qaTakeDownPlain")
	public void aPlainHandOverOverAStandingHutLeavesItStanding(GameTestHelper helper) {
		ServerLevel level = rules(helper);
		BlueprintData.Placement placement = stand(helper, TEST_HUT, HUT, Rotation.NONE, Mirror.NONE);
		helper.setBlock(TABLE, ModBlocks.BLUEPRINT_TABLE);
		helper.setBlock(CHEST, Blocks.CHEST);
		Container chest = helper.getBlockEntity(CHEST);
		Villager villager = builder(helper, TABLE, VILLAGER);
		BuildPlan takeDown = BuildPlan.deconstruct(io.github.jcondedata.aliveworkplace.blueprint.BlueprintLibrary.get(level, TEST_HUT).orElseThrow(), placement);
		QaStageFiveGameTests.Client c = player(helper, "qaTdP");
		c.player().setItemInHand(InteractionHand.MAIN_HAND, placedBlueprint(TEST_HUT, placement));
		handOver(c, villager, false);
		BuildSite site = Builders.activeSite(level, villager);
		helper.assertTrue(site == null || !site.isDeconstruction(), "a hand-over without sneaking started a take-down: " + texts(c.seen()));
		int[] ticks = {0};
		helper.onEachTick(() -> {
			Map<Item, Integer> now = standing(level, takeDown);
			if (!now.equals(hutList())) {
				helper.fail("a hand-over without sneaking took part of the hut down after " + ticks[0] + " ticks: standing " + now + ", chest " + chestHut(chest));
			}
			if (++ticks[0] == 600) {
				helper.assertTrue(chestHut(chest).values().stream().allMatch(n -> n == 0), "the hut's blocks reached the chest: " + chestHut(chest));
				helper.succeed();
			}
		});
	}

	/**
	 * en_us: "<builder> works for <boss>. Ask them to run /workplace friend add <you> to let you give orders." With
	 * builder ownership on, a stranger sneak-hands the placed blueprint to someone else's builder: they are told so, the
	 * blueprint stays in their hand, no take-down starts, and the hut still stands whole 200 ticks later.
	 */
	//$ gametest_ticks_batch AREA '400' '"qaTakeDownStranger"'
	@GameTest(template = AREA, timeoutTicks = 400, batch = "qaTakeDownStranger")
	public void aStrangerCannotHaveSomeonesBuilderTakeAHutDown(GameTestHelper helper) {
		ServerLevel level = rules(helper);
		level.getGameRules().getRule(ModGameRules.BUILDER_OWNERSHIP).set(true, level.getServer());
		BlueprintData.Placement placement = stand(helper, TEST_HUT, HUT, Rotation.NONE, Mirror.NONE);
		helper.setBlock(TABLE, ModBlocks.BLUEPRINT_TABLE);
		helper.setBlock(CHEST, Blocks.CHEST);
		Villager villager = builder(helper, TABLE, VILLAGER);
		QaStageFiveGameTests.Client boss = player(helper, "qaTdB");
		ModAttachments.BUILDER_EMPLOYER.set(villager, new io.github.jcondedata.aliveworkplace.build.Employer(boss.player().getUUID(),
			boss.player().getGameProfile().getName()));
		QaStageFiveGameTests.Client stranger = player(helper, "qaTdS");
		stranger.player().setItemInHand(InteractionHand.MAIN_HAND, placedBlueprint(TEST_HUT, placement));
		handOver(stranger, villager, true);
		String refused = villager.getDisplayName().getString() + " works for " + boss.player().getGameProfile().getName() + ". Ask them to run /workplace friend add "
			+ stranger.player().getGameProfile().getName() + " to let you give orders.";
		helper.assertTrue(texts(stranger.seen()).contains(refused), "the stranger saw " + texts(stranger.seen()));
		helper.assertTrue(Builders.activeSite(level, villager) == null, "the stranger's sneak hand-over started " + Builders.activeSite(level, villager));
		helper.assertTrue(stranger.player().getMainHandItem().is(ModItems.BLUEPRINT), "the stranger's blueprint was taken: " + stranger.player().getMainHandItem());
		BuildPlan takeDown = BuildPlan.deconstruct(io.github.jcondedata.aliveworkplace.blueprint.BlueprintLibrary.get(level, TEST_HUT).orElseThrow(), placement);
		helper.runAfterDelay(200, () -> {
			helper.assertTrue(standing(level, takeDown).equals(hutList()), "after a stranger's order the hut is " + standing(level, takeDown));
			helper.assertTrue(Builders.activeSite(level, villager) == null, "a site started anyway: " + Builders.activeSite(level, villager));
			helper.succeed();
		});
	}

	/**
	 * A take-down handed to a builder who is busy waits its turn: the player sneak-hands the standing hut's blueprint
	 * while the builder puts up another hut. The standing hut keeps every block while the first build goes on; then
	 * it comes down, and the chest ends with exactly the standing hut's blocks (the first hut used up exactly its list)
	 * and both blueprints.
	 */
	//$ gametest_ticks_batch AREA '6000' '"qaTakeDownQueued"'
	@GameTest(template = AREA, timeoutTicks = 6000, batch = "qaTakeDownQueued")
	public void aTakeDownHandedToABusyBuilderWaitsItsTurnThenComesDown(GameTestHelper helper) {
		ServerLevel level = rules(helper);
		BlueprintData.Placement standingHut = stand(helper, TEST_HUT, HUT, Rotation.NONE, Mirror.NONE);
		helper.setBlock(TABLE, ModBlocks.BLUEPRINT_TABLE);
		helper.setBlock(CHEST, Blocks.CHEST);
		Container chest = helper.getBlockEntity(CHEST);
		int slot = 0;
		for (Item item : HUT_ITEMS) {
			chest.setItem(slot++, new ItemStack(item, HUT_LIST.get(item)));
		}
		Villager villager = builder(helper, TABLE, VILLAGER);
		QaStageFiveGameTests.Client c = player(helper, "qaTdQ");
		BlueprintData.Placement newHut = new BlueprintData.Placement(level.dimension().location(), helper.absolutePos(new BlockPos(11, 2, 0)), Rotation.NONE, Mirror.NONE);
		c.player().setItemInHand(InteractionHand.MAIN_HAND, placedBlueprint(TEST_HUT, newHut));
		handOver(c, villager, false);
		BuildSite first = Builders.activeSite(level, villager);
		helper.assertTrue(first != null && !first.isDeconstruction(), "the first hand-over started no build: " + texts(c.seen()));
		c.player().setItemInHand(InteractionHand.MAIN_HAND, placedBlueprint(TEST_HUT, standingHut));
		handOver(c, villager, true);
		List<BuildSite> queue = Builders.queue(level, villager);
		helper.assertTrue(queue.size() == 1 && queue.get(0).isDeconstruction(), "the sneak hand-over to a busy builder queued " + queue + ": " + texts(c.seen()));
		BuildSite takeDown = queue.get(0);
		BuildPlan takeDownPlan = takeDown.plan(level);
		BuildPlan firstPlan = first.plan(level);
		helper.onEachTick(() -> {
			if (BuildSiteManager.get(level).get(first.id()) != null && !standing(level, takeDownPlan).equals(hutList())) {
				helper.fail("the queued take-down started before the first build was finished: standing " + standing(level, takeDownPlan) + ", first at "
					+ first.stage());
			}
		});
		helper.succeedWhen(() -> {
			helper.assertTrue(BuildSiteManager.get(level).get(first.id()) == null, "still on the first build: " + first.stage() + ", " + first.status());
			helper.assertTrue(firstPlan.unfinished(level).isEmpty(), firstPlan.unfinished(level).size() + " block(s) of the first hut wrong");
			helper.assertTrue(BuildSiteManager.get(level).get(takeDown.id()) == null, "still taking the hut down: " + takeDown.stage() + ", " + takeDown.status());
			helper.assertTrue(takeDownPlan.unfinished(level).isEmpty(), takeDownPlan.unfinished(level).size() + " block(s) of the standing hut still stand");
			helper.assertTrue(chestHut(chest).equals(hutList()) && bag(villager).isEmpty(), "the chest holds " + chestHut(chest) + " (wanted " + hutList()
				+ "), the bag " + bag(villager) + ", on the ground " + ground(helper));
			int blueprints = chest.countItem(ModItems.BLUEPRINT) + inventory(c.player(), ModItems.BLUEPRINT);
			helper.assertTrue(blueprints == 2, blueprints + " blueprints back after both jobs (wanted 2)");
		});
	}

	/**
	 * docs/guide/command-cancel.md: "Canceled the build of Fountain." ... "the blueprint is still placed there: hand it back
	 * to the builder to carry on"; the cancel button: "What the builder carried goes back to its chests; placed blocks
	 * stay." A take-down cancelled by its player with /workplace cancel once half the hut is down and the builder carries
	 * some of it: the site is gone, the bag is empty, every block of the hut is still standing or in the chest (none
	 * lost, none doubled), the blueprint is back in the player's inventory still placed over the hut, and nothing more
	 * comes down for 100 ticks. Sneak-handed back, the take-down carries on and the chest ends with exactly the hut.
	 */
	//$ gametest_ticks_batch AREA '5000' '"qaTakeDownCancel"'
	@GameTest(template = AREA, timeoutTicks = 5000, batch = "qaTakeDownCancel")
	public void aTakeDownCancelledHalfwayKeepsEveryBlockAndCarriesOnWhenHandedBack(GameTestHelper helper) {
		ServerLevel level = rules(helper);
		BlueprintData.Placement placement = stand(helper, TEST_HUT, HUT, Rotation.NONE, Mirror.NONE);
		helper.setBlock(TABLE, ModBlocks.BLUEPRINT_TABLE);
		helper.setBlock(CHEST, Blocks.CHEST);
		Container chest = helper.getBlockEntity(CHEST);
		Villager villager = builder(helper, TABLE, VILLAGER);
		QaStageFiveGameTests.Client c = player(helper, "qaTdX");
		c.player().setItemInHand(InteractionHand.MAIN_HAND, placedBlueprint(TEST_HUT, placement));
		handOver(c, villager, true);
		BuildSite site = Builders.activeSite(level, villager);
		helper.assertTrue(site != null && site.isDeconstruction(), "the sneak hand-over started no take-down: " + texts(c.seen()));
		BuildPlan plan = site.plan(level);
		String hut = Blueprints.displayName(TEST_HUT).getString();
		int total = HUT_LIST.values().stream().mapToInt(Integer::intValue).sum();
		int[] phase = {0};
		int[] cancelledAt = {0};
		Map<Item, Integer> afterCancel = new LinkedHashMap<>();
		BuildSite[] again = {null};
		helper.onEachTick(() -> {
			if (phase[0] == 0) {
				int left = standing(level, plan).values().stream().mapToInt(Integer::intValue).sum();
				if (left > total / 2 || bag(villager).isEmpty()) {
					return;
				}
				Map<Item, Integer> carried = bag(villager);
				QaStageSixGameTests.command(c, "/workplace cancel " + site.id());
				helper.assertTrue(texts(c.seen()).contains("Canceled the take-down of " + hut + ". Its blueprint is still placed there: hand it back to carry on taking it down, or click the ground to move it.") /* B526: a take-down's own line */,
					"/workplace cancel said " + texts(c.seen()));
				helper.assertTrue(BuildSiteManager.get(level).get(site.id()) == null, "the take-down is still on after the cancel");
				helper.assertTrue(bag(villager).isEmpty(), "after the cancel the builder still carries " + bag(villager) + " (had " + carried + ")");
				Map<Item, Integer> now = accounted(helper, plan, villager, chest);
				helper.assertTrue(now.equals(hutList()), "cancelled with " + left + " of " + total + " standing and " + carried + " carried: standing + chest + ground is "
					+ now + ", wanted " + hutList());
				helper.assertTrue(inventory(c.player(), ModItems.BLUEPRINT) == 1, "the blueprint didn't come back to the player: inventory has "
					+ inventory(c.player(), ModItems.BLUEPRINT) + ", chest " + chest.countItem(ModItems.BLUEPRINT));
				int slot = -1;
				for (int i = 0; i < c.player().getInventory().getContainerSize(); i++) {
					if (c.player().getInventory().getItem(i).is(ModItems.BLUEPRINT)) {
						slot = i;
					}
				}
				ItemStack back = c.player().getInventory().getItem(slot);
				// B513: it comes back still placed over the hut, so it's handed back exactly as it came
				afterCancel.putAll(standing(level, plan));
				cancelledAt[0] = 0;
				phase[0] = 1;
				c.player().getInventory().setItem(slot, ItemStack.EMPTY);
				c.player().setItemInHand(InteractionHand.MAIN_HAND, back);
			} else if (phase[0] == 1) {
				if (!standing(level, plan).equals(afterCancel)) {
					helper.fail("blocks kept coming down after the cancel: " + afterCancel + " then " + standing(level, plan));
				}
				if (++cancelledAt[0] == 100) {
					handOver(c, villager, true);
					again[0] = Builders.activeSite(level, villager);
					helper.assertTrue(again[0] != null && again[0].isDeconstruction(), "handed back, no take-down started: " + texts(c.seen()));
					phase[0] = 2;
				}
			}
		});
		helper.succeedWhen(() -> {
			helper.assertTrue(phase[0] == 2, "never cancelled and handed back (phase " + phase[0] + ", standing " + standing(level, plan) + ", bag " + bag(villager) + ")");
			helper.assertTrue(BuildSiteManager.get(level).get(again[0].id()) == null, "still taking it down after handing it back: " + again[0].stage() + ", " + again[0].status());
			helper.assertTrue(plan.unfinished(level).isEmpty(), plan.unfinished(level).size() + " block(s) still stand after carrying on");
			helper.assertTrue(chestHut(chest).equals(hutList()) && bag(villager).isEmpty(), "after the cancel and carrying on the chest holds " + chestHut(chest)
				+ " (wanted " + hutList() + "), the bag " + bag(villager) + ", on the ground " + ground(helper));
		});
	}

	/**
	 * Nothing is lost when the builder's table is broken in the middle of a take-down: a third of the hut down, the
	 * player breaks the Blueprint Table. 400 ticks later every block of the hut is still standing, in the chest, in the
	 * builder's bag or on the ground, exactly once.
	 */
	//$ gametest_ticks_batch AREA '4000' '"qaTakeDownTableBroken"'
	@GameTest(template = AREA, timeoutTicks = 4000, batch = "qaTakeDownTableBroken")
	public void aTableBrokenMidTakeDownLosesNoBlock(GameTestHelper helper) {
		ServerLevel level = rules(helper);
		BlueprintData.Placement placement = stand(helper, TEST_HUT, HUT, Rotation.NONE, Mirror.NONE);
		helper.setBlock(TABLE, ModBlocks.BLUEPRINT_TABLE);
		helper.setBlock(CHEST, Blocks.CHEST);
		Container chest = helper.getBlockEntity(CHEST);
		Villager villager = builder(helper, TABLE, VILLAGER);
		QaStageFiveGameTests.Client c = player(helper, "qaTdK");
		c.player().setItemInHand(InteractionHand.MAIN_HAND, placedBlueprint(TEST_HUT, placement));
		handOver(c, villager, true);
		BuildSite site = Builders.activeSite(level, villager);
		helper.assertTrue(site != null && site.isDeconstruction(), "the sneak hand-over started no take-down: " + texts(c.seen()));
		BuildPlan plan = site.plan(level);
		int total = HUT_LIST.values().stream().mapToInt(Integer::intValue).sum();
		int[] since = {-1};
		String[] when = {null};
		helper.onEachTick(() -> {
			if (since[0] < 0) {
				int left = standing(level, plan).values().stream().mapToInt(Integer::intValue).sum();
				if (left > total * 2 / 3 || bag(villager).isEmpty()) {
					return;
				}
				when[0] = left + " of " + total + " standing, bag " + bag(villager);
				c.player().gameMode.destroyBlock(helper.absolutePos(TABLE));
				helper.assertBlockNotPresent(ModBlocks.BLUEPRINT_TABLE, TABLE);
				since[0] = 0;
			} else if (++since[0] == 400) {
				Map<Item, Integer> now = accounted(helper, plan, villager, chest);
				helper.assertTrue(now.equals(hutList()), "the table broken with " + when[0] + ": 400 ticks later standing + chest + bag + ground is " + now
					+ ", wanted " + hutList() + " (standing " + standing(level, plan) + ", chest " + chestHut(chest) + ", bag " + bag(villager) + ", ground "
					+ ground(helper) + ")");
				helper.succeed();
			}
		});
	}

	/**
	 * en_us tooltip.aliveworkplace.blueprint.deconstruct_hint: a placed blueprint tells the player how to have a
	 * building taken down ("Sneak while giving it to have them take the building down instead"); one not placed yet
	 * doesn't (there is nothing to take down).
	 */
	//$ gametest AREA
	@GameTest(template = AREA)
	public void aPlacedBlueprintsTooltipTellsHowToTakeTheBuildingDown(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		BlueprintData.Placement placement = new BlueprintData.Placement(level.dimension().location(), helper.absolutePos(HUT), Rotation.NONE, Mirror.NONE);
		String hint = "Sneak while giving it to have them take the building down instead";
		List<String> placed = placedBlueprint(TEST_HUT, placement).getTooltipLines(Item.TooltipContext.of(level), null, TooltipFlag.Default.NORMAL)
			.stream().map(Component::getString).toList();
		List<String> loose = BlueprintItem.create(TEST_HUT, null).getTooltipLines(Item.TooltipContext.of(level), null, TooltipFlag.Default.NORMAL)
			.stream().map(Component::getString).toList();
		helper.assertTrue(placed.contains(hint), "a placed blueprint's tooltip is " + placed);
		helper.assertFalse(loose.contains(hint), "a blueprint not placed yet offers a take-down: " + loose);
		helper.succeed();
	}
}
