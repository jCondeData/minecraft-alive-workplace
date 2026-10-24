package io.github.jcondedata.aliveworkplace.test;

import io.github.jcondedata.aliveworkplace.blueprint.BlueprintData;
import io.github.jcondedata.aliveworkplace.blueprint.BlueprintItem;
import io.github.jcondedata.aliveworkplace.blueprint.Blueprints;
import io.github.jcondedata.aliveworkplace.blueprint.ScanToolItem;
import io.github.jcondedata.aliveworkplace.build.BuildPlan;
import io.github.jcondedata.aliveworkplace.build.BuildSite;
import io.github.jcondedata.aliveworkplace.build.BuildSiteManager;
import io.github.jcondedata.aliveworkplace.build.Builders;
import io.github.jcondedata.aliveworkplace.build.MaterialRules;
import io.github.jcondedata.aliveworkplace.registry.ModBlocks;
import io.github.jcondedata.aliveworkplace.registry.ModComponents;
import io.github.jcondedata.aliveworkplace.registry.ModItems;
import io.github.jcondedata.aliveworkplace.work.ChoiceMenu;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Vec3i;
import net.minecraft.core.component.DataComponents;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.WrittenBookContent;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.phys.AABB;

/**
 * QA (2026-10-24) of the builder's clicks fixed in B279, B335, B337, B342 and B344, from their specs (ROADMAP, README,
 * docs/wiki/builder.md, en_us.json), for the cases the fixers' own tests leave out: the Book and Quill on the builder's
 * Blueprint Table (with his build, with no build, with no builder), from the off hand, after a save and reload of the
 * site mid-STRUCTURE, a stack of them with a full inventory; the Scan Tool's second corner on a barrel and a sneak-click
 * on a chest; a placed blueprint handed over from the off hand; and the Storehouse board opened again right after a
 * hand-over and once more after the builder has used some. The test hut needs 25 cobblestone, 55 oak planks, an oak
 * door and a torch.
 */
public class QaBuilderClicksGameTests implements net.fabricmc.fabric.api.gametest.v1.FabricGameTest {
	private static final String AREA = "aliveworkplace_test:build_area";
	private static final org.slf4j.Logger LOGGER = org.slf4j.LoggerFactory.getLogger("aliveworkplace_test");
	private static final ResourceLocation TEST_HUT = ResourceLocation.fromNamespaceAndPath("aliveworkplace_test", "test_hut");
	private static final BlockPos TABLE = new BlockPos(2, 2, 2);
	private static final BlockPos CHEST = new BlockPos(2, 2, 4);
	private static final BlockPos VILLAGER = new BlockPos(3, 2, 3);
	private static final BlockPos HUT = new BlockPos(6, 2, 6);

	private record Setup(ServerLevel level, Villager builder, BuildSite site, BuildPlan plan, Container chest) {
	}

	/**
	 * Every test here clicks blocks as a stranger to any village. A protected Village Hall left by an earlier test within
	 * {@code VillageHalls.RADIUS} (64) blocks (CityPlanScreenGameTests and CityPlanGameTests leave theirs protected) refuses
	 * those clicks, as it should for a stranger, so in the full suite the Storehouse board didn't open. Village protection
	 * isn't what these tests are about: it is off for this test's batch and put back after.
	 */
	private static void noVillageProtection(GameTestHelper helper) {
		boolean enabled = io.github.jcondedata.aliveworkplace.hall.VillageProtection.ENABLED;
		io.github.jcondedata.aliveworkplace.hall.VillageProtection.ENABLED = false;
		Leftovers.after(helper, () -> io.github.jcondedata.aliveworkplace.hall.VillageProtection.ENABLED = enabled);
	}

	/** A builder at {@code job} (his Blueprint Table or Builder's Bench) with a chest by it (4 cobblestone, 20 planks), the test hut handed to him. */
	private static Setup setup(GameTestHelper helper, net.minecraft.world.level.block.Block job) {
		Leftovers.clear(helper);
		noVillageProtection(helper);
		ServerLevel level = helper.getLevel();
		helper.setBlock(TABLE, job);
		helper.setBlock(CHEST, Blocks.CHEST);
		Container chest = helper.getBlockEntity(CHEST);
		chest.setItem(0, new ItemStack(Items.COBBLESTONE, 4));
		chest.setItem(1, new ItemStack(Items.OAK_PLANKS, 20));
		Villager builder = helper.spawn(EntityType.VILLAGER, VILLAGER);
		builder.setNoAi(true); // the list is read as it stands
		Builders.employ(level, builder, helper.absolutePos(TABLE));
		BuildSite site = Builders.start(level, builder, null, TEST_HUT,
			new BlueprintData.Placement(level.dimension().location(), helper.absolutePos(HUT), Rotation.NONE, Mirror.NONE));
		helper.assertTrue(site != null, "the test hut didn't start");
		BuildPlan plan = site.plan(level);
		helper.assertTrue(plan != null, "no plan for the test hut");
		return new Setup(level, builder, site, plan, chest);
	}

	/** The first {@code n} cobblestone blocks of the plan stand. */
	private static int placeCobblestone(Setup s, int n) {
		int placed = 0;
		for (BuildPlan.Stage stage : List.of(BuildPlan.Stage.FOUNDATION, BuildPlan.Stage.STRUCTURE, BuildPlan.Stage.DECORATION)) {
			for (BuildPlan.Step step : s.plan().steps(stage)) {
				if (placed < n && step.state().is(Blocks.COBBLESTONE)) {
					s.level().setBlockAndUpdate(step.pos(), step.state());
					placed++;
				}
			}
		}
		return placed;
	}

	/** Worked out from the spec: every step from the site's stage to the end not standing, less what the chest holds. */
	private static Map<Item, Integer> expectedToBring(Setup s, BuildSite site) {
		Map<Item, Integer> need = new HashMap<>();
		for (BuildPlan.Step step : site.upcoming(s.plan(), Integer.MAX_VALUE)) {
			if (!MaterialRules.matches(s.level().getBlockState(step.pos()), step.state())) {
				for (MaterialRules.Requirement r : step.requirements()) {
					need.merge(r.item(), r.count(), Integer::sum);
				}
			}
		}
		Map<Item, Integer> out = new HashMap<>();
		need.forEach((item, n) -> out.put(item, Math.max(0, n - s.chest().countItem(item))));
		return out;
	}

	private static List<String> pages(ItemStack book) {
		WrittenBookContent content = book.get(DataComponents.WRITTEN_BOOK_CONTENT);
		return content == null ? List.of() : content.pages().stream().map(p -> p.raw().getString()).toList();
	}

	private static List<String> texts(List<Component> seen) {
		return seen.stream().map(Component::getString).toList();
	}

	private static String hut() {
		return Blueprints.displayName(TEST_HUT).getString();
	}

	private static final String HUT_LIST_AT_9 = "☐ 35 × Oak Planks (of 55)\n☐ 12 × Cobblestone (of 16)\n☐ 1 × Oak Door\n☐ 1 × Torch";

	// --- B337: the Book and Quill on the builder's Blueprint Table --------------------------------------------------

	/**
	 * B337/B279: mid-build (9 cobblestone up), a Book and Quill used on the builder's Blueprint Table becomes the list of
	 * the rest, less the chest by the table: 35 planks, 12 cobblestone, the door and the torch. The table's screen
	 * doesn't open, and the player is told "Wrote the material list for the Test Hut".
	 */
	//$ gametest_ticks_batch AREA '60' '"qaClicksTable"'
	@GameTest(template = AREA, timeoutTicks = 60, batch = "qaClicksTable")
	public void aBookAndQuillOnTheBuildersTableWritesTheListLessItsChest(GameTestHelper helper) {
		Setup s = setup(helper, ModBlocks.BLUEPRINT_TABLE);
		helper.assertTrue(placeCobblestone(s, 9) == 9, "the hut has fewer than 9 cobblestone blocks");
		QaStageFiveGameTests.Client c = QaStageSixGameTests.client(helper, GameType.SURVIVAL, QaStageSixGameTests.name("qacT"));
		ServerPlayer p = c.player();
		p.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.WRITABLE_BOOK));
		QaStageFourGameTests.press(helper, p, TABLE, Direction.NORTH);
		ItemStack book = p.getMainHandItem();
		helper.assertTrue(book.is(Items.WRITTEN_BOOK), "the Book and Quill on the table wasn't written: " + book + ", saw " + texts(c.seen()));
		helper.assertTrue(p.containerMenu == p.inventoryMenu, "the table's screen opened too: " + p.containerMenu.getClass().getSimpleName());
		List<String> pages = pages(book);
		helper.assertTrue(pages.size() >= 2 && pages.get(0).contains("Still to bring: 49 items of 4 kinds."), "the first page: " + pages);
		helper.assertTrue(pages.get(1).equals(HUT_LIST_AT_9), "the list page: " + pages.get(1));
		helper.assertTrue(texts(c.seen()).contains("Wrote the material list for the " + hut()), "the player saw " + texts(c.seen()));
		helper.succeed();
	}

	/**
	 * B337: "or 'no build at this bench' when none is under way": the Blueprint Table of a builder with no build says
	 * "No build is under way from this bench. Hand its builder a placed blueprint." and the Book and Quill is kept. A
	 * Blueprint Table no builder works at writes no list either: the book stays a Book and Quill.
	 */
	//$ gametest_ticks_batch AREA '60' '"qaClicksIdleTable"'
	@GameTest(template = AREA, timeoutTicks = 60, batch = "qaClicksIdleTable")
	public void aTableWithNoBuildOrNoBuilderKeepsTheBook(GameTestHelper helper) {
		Leftovers.clear(helper);
		noVillageProtection(helper);
		ServerLevel level = helper.getLevel();
		helper.setBlock(TABLE, ModBlocks.BLUEPRINT_TABLE);
		Villager idle = helper.spawn(EntityType.VILLAGER, VILLAGER);
		idle.setNoAi(true);
		Builders.employ(level, idle, helper.absolutePos(TABLE));
		QaStageFiveGameTests.Client c = QaStageSixGameTests.client(helper, GameType.SURVIVAL, QaStageSixGameTests.name("qacI"));
		ServerPlayer p = c.player();
		p.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.WRITABLE_BOOK));
		QaStageFourGameTests.press(helper, p, TABLE, Direction.NORTH);
		helper.assertTrue(p.getMainHandItem().is(Items.WRITABLE_BOOK) && p.getMainHandItem().getCount() == 1, "the book went: " + p.getMainHandItem());
		helper.assertTrue(p.getInventory().countItem(Items.WRITTEN_BOOK) == 0, "a list was written for a builder with no build");
		helper.assertTrue(texts(c.seen()).contains("No build is under way from this bench. Hand its builder a placed blueprint."),
			"the idle builder's table: saw " + texts(c.seen()));
		p.closeContainer();

		BlockPos lone = new BlockPos(13, 2, 13);
		helper.setBlock(lone, ModBlocks.BLUEPRINT_TABLE);
		c.seen().clear();
		QaStageFourGameTests.press(helper, p, lone, Direction.NORTH);
		helper.assertTrue(p.getMainHandItem().is(Items.WRITABLE_BOOK) && p.getMainHandItem().getCount() == 1, "the book went at a lone table: " + p.getMainHandItem());
		helper.assertTrue(p.getInventory().countItem(Items.WRITTEN_BOOK) == 0, "a list was written at a table nobody builds from");
		helper.assertFalse(texts(c.seen()).stream().anyMatch(t -> t.startsWith("Wrote the material list")), "told a list was written: " + texts(c.seen()));
		p.closeContainer();
		helper.succeed();
	}

	// --- B279 + B342: from the off hand, after a reload, a stack with a full bag ------------------------------------

	/**
	 * B279 + B342: a Book and Quill held in the off hand (main hand empty) and used on the builder writes the list into
	 * that hand, as from the main hand; no trade screen and no status line takes the click instead.
	 */
	//$ gametest_ticks_batch AREA '60' '"qaClicksOffBook"'
	@GameTest(template = AREA, timeoutTicks = 60, batch = "qaClicksOffBook")
	public void aBookAndQuillFromTheOffHandOnTheBuilder(GameTestHelper helper) {
		Setup s = setup(helper, ModBlocks.BLUEPRINT_TABLE);
		placeCobblestone(s, 9);
		QaStageFiveGameTests.Client c = QaStageSixGameTests.client(helper, GameType.SURVIVAL, QaStageSixGameTests.name("qacO"));
		ServerPlayer p = c.player();
		p.setItemInHand(InteractionHand.OFF_HAND, new ItemStack(Items.WRITABLE_BOOK));
		QaStageSixGameTests.clickVillager(p, s.builder(), c.seen());
		helper.assertTrue(p.getOffhandItem().is(Items.WRITTEN_BOOK), "off hand: the Book and Quill wasn't written: " + p.getOffhandItem() + ", saw "
			+ texts(c.seen()) + ", screen " + p.containerMenu.getClass().getSimpleName());
		helper.assertTrue(p.getMainHandItem().isEmpty(), "the main hand got " + p.getMainHandItem());
		helper.assertTrue(p.containerMenu == p.inventoryMenu, "off hand: a screen opened: " + p.containerMenu.getClass().getSimpleName());
		List<String> pages = pages(p.getOffhandItem());
		helper.assertTrue(pages.size() >= 2 && pages.get(1).equals(HUT_LIST_AT_9), "off hand: the list " + pages);
		helper.succeed();
	}

	/**
	 * B279 "after save and reload": the site moved on to STRUCTURE, the build sites saved and loaded back as on a server
	 * restart; a Book and Quill on the builder then writes "Building the structure" and the structure-on list (the
	 * site's own steps not standing, less the chest), and a sneak-click with an empty hand shows the same totals in chat.
	 */
	//$ gametest_ticks_batch AREA '60' '"qaClicksReload"'
	@GameTest(template = AREA, timeoutTicks = 60, batch = "qaClicksReload")
	public void theListAfterAReloadMidStructure(GameTestHelper helper) {
		Setup s = setup(helper, ModBlocks.BLUEPRINT_TABLE);
		ServerLevel level = s.level();
		while (s.site().stage() != BuildPlan.Stage.STRUCTURE) {
			helper.assertTrue(s.site().stage() != BuildPlan.Stage.DONE && s.site().stage() != BuildPlan.Stage.LANDSCAPE, "skipped STRUCTURE");
			s.site().finishList();
		}
		placeCobblestone(s, 6);
		BuildSiteManager original = BuildSiteManager.get(level);
		BuildSiteManager loaded = BuildSiteManager.load(original.save(new CompoundTag(), level.registryAccess()), level.registryAccess());
		level.getDataStorage().set(BuildSiteManager.NAME, loaded);
		try {
			BuildSite reloaded = BuildSiteManager.get(level).get(s.site().id());
			helper.assertTrue(reloaded != null && reloaded != s.site() && reloaded.stage() == BuildPlan.Stage.STRUCTURE,
				"the site didn't come back in STRUCTURE: " + (reloaded == null ? "gone" : reloaded.stage()));
			Map<Item, Integer> expected = expectedToBring(s, reloaded);
			int total = expected.values().stream().mapToInt(Integer::intValue).sum();
			long kinds = expected.values().stream().filter(n -> n > 0).count();
			helper.assertTrue(total > 0, "nothing left to bring in STRUCTURE: " + expected);

			QaStageFiveGameTests.Client c = QaStageSixGameTests.client(helper, GameType.SURVIVAL, QaStageSixGameTests.name("qacR"));
			ServerPlayer p = c.player();
			p.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.WRITABLE_BOOK));
			QaStageFourGameTests.pressOn(p, s.builder(), c.seen());
			ItemStack book = p.getMainHandItem();
			helper.assertTrue(book.is(Items.WRITTEN_BOOK), "after the reload no list was written: " + book + ", saw " + texts(c.seen()));
			List<String> pages = pages(book);
			helper.assertTrue(pages.get(0).contains("Building the structure."), "the first page: " + pages.get(0));
			helper.assertTrue(pages.get(0).contains("Still to bring: " + total + " items of " + kinds + " kinds."), "the first page: " + pages.get(0)
				+ ", expected " + expected);
			String listed = String.join("\n", pages.subList(1, pages.size()));
			for (Map.Entry<Item, Integer> e : expected.entrySet()) {
				if (e.getValue() > 0) {
					String line = "☐ " + e.getValue() + " × " + new ItemStack(e.getKey()).getHoverName().getString();
					helper.assertTrue(listed.contains(line), "the list lacks '" + line + "': " + listed);
				}
			}

			c.seen().clear();
			p.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
			p.setShiftKeyDown(true);
			QaStageFourGameTests.pressOn(p, s.builder(), c.seen());
			p.setShiftKeyDown(false);
			String heading = "Still to bring for the " + hut() + ": " + total + " items of " + kinds + " kinds";
			helper.assertTrue(texts(c.seen()).stream().anyMatch(t -> t.startsWith(heading)), "the chat after a reload, expected '" + heading + "': " + texts(c.seen()));
		} finally {
			level.getDataStorage().set(BuildSiteManager.NAME, original);
		}
		helper.succeed();
	}

	/**
	 * B279: a stack of two Book and Quills on the Builder's Bench, with every other inventory slot full: one is used up and
	 * the written list isn't lost (it lands in the inventory or at the player's feet).
	 */
	//$ gametest_ticks_batch AREA '60' '"qaClicksFullBag"'
	@GameTest(template = AREA, timeoutTicks = 60, batch = "qaClicksFullBag")
	public void aStackOfBooksWithAFullInventoryKeepsTheList(GameTestHelper helper) {
		Setup s = setup(helper, ModBlocks.BUILDERS_BENCH);
		placeCobblestone(s, 9);
		QaStageFiveGameTests.Client c = QaStageSixGameTests.client(helper, GameType.SURVIVAL, QaStageSixGameTests.name("qacF"));
		ServerPlayer p = c.player();
		p.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.WRITABLE_BOOK, 2));
		QaStageFourGameTests.fill(p);
		QaStageFourGameTests.press(helper, p, TABLE, Direction.NORTH);
		helper.assertTrue(p.getMainHandItem().is(Items.WRITABLE_BOOK) && p.getMainHandItem().getCount() == 1, "one book should be used: " + p.getMainHandItem());
		helper.runAfterDelay(2, () -> {
			List<ItemEntity> dropped = helper.getLevel().getEntitiesOfClass(ItemEntity.class, new AABB(p.blockPosition()).inflate(6),
				e -> e.getItem().is(Items.WRITTEN_BOOK));
			int written = p.getInventory().countItem(Items.WRITTEN_BOOK) + dropped.size();
			helper.assertTrue(written == 1, "the written list: " + written + " found (inventory " + p.getInventory().countItem(Items.WRITTEN_BOOK)
				+ ", ground " + dropped.size() + ")");
			ItemStack book = dropped.isEmpty() ? p.getInventory().items.stream().filter(st -> st.is(Items.WRITTEN_BOOK)).findFirst().orElseThrow()
				: dropped.get(0).getItem();
			helper.assertTrue(pages(book).size() >= 2 && pages(book).get(1).equals(HUT_LIST_AT_9), "the list: " + pages(book));
			dropped.forEach(ItemEntity::discard);
			helper.succeed();
		});
	}

	// --- B342: a placed blueprint from the off hand -----------------------------------------------------------------

	/**
	 * B342: "the other items BuilderEvents answers on a villager click": a placed blueprint held in the off hand (main
	 * hand empty) and handed to an idle builder starts his build, as from the main hand; no trade screen.
	 */
	//$ gametest_ticks_batch AREA '60' '"qaClicksOffBlueprint"'
	@GameTest(template = AREA, timeoutTicks = 60, batch = "qaClicksOffBlueprint")
	public void aPlacedBlueprintFromTheOffHand(GameTestHelper helper) {
		Leftovers.clear(helper);
		noVillageProtection(helper);
		ServerLevel level = helper.getLevel();
		helper.setBlock(TABLE, ModBlocks.BLUEPRINT_TABLE);
		Villager builder = helper.spawn(EntityType.VILLAGER, VILLAGER);
		builder.setNoAi(true);
		Builders.employ(level, builder, helper.absolutePos(TABLE));
		ItemStack blueprint = BlueprintItem.create(TEST_HUT, new Vec3i(5, 4, 5));
		blueprint.set(ModComponents.BLUEPRINT, BlueprintItem.data(blueprint).orElseThrow().withPlacement(Optional.of(
			new BlueprintData.Placement(level.dimension().location(), helper.absolutePos(HUT), Rotation.NONE, Mirror.NONE))));
		QaStageFiveGameTests.Client c = QaStageSixGameTests.client(helper, GameType.SURVIVAL, QaStageSixGameTests.name("qacB"));
		ServerPlayer p = c.player();
		p.setItemInHand(InteractionHand.OFF_HAND, blueprint);
		QaStageSixGameTests.clickVillager(p, builder, c.seen());
		BuildSite site = Builders.activeSite(level, builder);
		helper.assertTrue(site != null && TEST_HUT.equals(site.structure()), "off hand: the blueprint didn't start a build, saw " + texts(c.seen())
			+ ", screen " + p.containerMenu.getClass().getSimpleName());
		helper.assertTrue(p.containerMenu == p.inventoryMenu, "off hand: a screen opened: " + p.containerMenu.getClass().getSimpleName());
		helper.succeed();
	}

	// --- B335: the Scan Tool on containers -------------------------------------------------------------------------

	/**
	 * B335 + README ("Right-click one corner block ..., then the opposite corner"): the first corner on a chest (with
	 * something in it), the second on a barrel: both marked, "Marked 4 × 1 × 4. ..." said, neither opened and the chest's
	 * contents untouched. A sneak-click on the chest starts over ("Scan Tool cleared: right-click the first corner")
	 * without opening it.
	 */
	//$ gametest_ticks_batch AREA '60' '"qaClicksScan"'
	@GameTest(template = AREA, timeoutTicks = 60, batch = "qaClicksScan")
	public void scanToolCornersOnAChestThenABarrel(GameTestHelper helper) {
		Leftovers.clear(helper);
		noVillageProtection(helper);
		BlockPos chestPos = new BlockPos(2, 2, 3);
		BlockPos barrelPos = new BlockPos(5, 2, 6);
		helper.setBlock(chestPos, Blocks.CHEST);
		helper.setBlock(barrelPos, Blocks.BARREL);
		Container chest = helper.getBlockEntity(chestPos);
		chest.setItem(0, new ItemStack(Items.BREAD, 3));
		QaStageFiveGameTests.Client c = QaStageSixGameTests.client(helper, GameType.SURVIVAL, QaStageSixGameTests.name("qacS"));
		ServerPlayer p = c.player();
		p.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(ModItems.SCAN_TOOL));
		QaStageFourGameTests.press(helper, p, chestPos, Direction.NORTH);
		helper.assertTrue(p.containerMenu == p.inventoryMenu, "the chest opened: " + p.containerMenu.getClass().getSimpleName());
		BlockPos a = helper.absolutePos(chestPos);
		helper.assertTrue(texts(c.seen()).contains("First corner at " + a.getX() + ", " + a.getY() + ", " + a.getZ() + ": now right-click the opposite corner"),
			"the first corner: saw " + texts(c.seen()));
		c.seen().clear();
		QaStageFourGameTests.press(helper, p, barrelPos, Direction.NORTH);
		helper.assertTrue(p.containerMenu == p.inventoryMenu, "the barrel opened: " + p.containerMenu.getClass().getSimpleName());
		var data = ScanToolItem.data(p.getMainHandItem());
		helper.assertTrue(data.isComplete() && data.first().equals(Optional.of(a)) && data.second().equals(Optional.of(helper.absolutePos(barrelPos))),
			"the corners: " + data);
		helper.assertTrue(texts(c.seen()).contains("Marked 4 × 1 × 4. Sneak-right-click the air to save it as a blueprint (rename the tool in an anvil to name it)"),
			"the second corner: saw " + texts(c.seen()));
		helper.assertTrue(chest.countItem(Items.BREAD) == 3 && p.getInventory().countItem(Items.BREAD) == 0, "the chest's bread moved");

		c.seen().clear();
		p.setShiftKeyDown(true);
		QaStageFourGameTests.press(helper, p, chestPos, Direction.NORTH);
		p.setShiftKeyDown(false);
		helper.assertTrue(p.containerMenu == p.inventoryMenu, "a sneak-click opened the chest: " + p.containerMenu.getClass().getSimpleName());
		helper.assertTrue(ScanToolItem.data(p.getMainHandItem()).first().isEmpty(), "the sneak-click didn't start over: " + ScanToolItem.data(p.getMainHandItem()));
		helper.assertTrue(texts(c.seen()).contains("Scan Tool cleared: right-click the first corner"), "the sneak-click: saw " + texts(c.seen()));
		helper.succeed();
	}

	// --- B344: the Storehouse board right after a hand-over ---------------------------------------------------------

	/**
	 * B344: "the board's request drops by what was handed over at once, so a second click hands nothing more". A click
	 * hands the 25 cobblestone asked for; the board, shut and opened again the same tick and one tick later, asks for no
	 * cobblestone, and the player keeps the other 15. Once the builder has taken some out of his chest to build, the
	 * board still asks for none and a click on its old slot hands nothing.
	 */
	//$ gametest_ticks_batch AREA '2400' '"qaClicksBoard"'
	@GameTest(template = AREA, timeoutTicks = 2400, batch = "qaClicksBoard")
	public void theBoardOpenedAgainAfterAHandOverAsksForNothingMore(GameTestHelper helper) {
		QaStageSixGameTests.Waiting w = QaStageSixGameTests.waitingBuilder(helper);
		noVillageProtection(helper);
		helper.runAfterDelay(300, () -> {
			helper.assertTrue(w.site().status() == BuildSite.Status.WAITING_FOR_MATERIALS, "the builder isn't waiting: " + w.site().status());
			QaStageFiveGameTests.Client c = QaStageSixGameTests.client(helper, GameType.SURVIVAL, QaStageSixGameTests.name("qacW"));
			ServerPlayer p = c.player();
			p.getInventory().setItem(5, new ItemStack(Items.COBBLESTONE, 40));
			Container chest = helper.getBlockEntity(w.chest());
			QaStageFourGameTests.press(helper, p, w.storehouse());
			helper.assertTrue(p.containerMenu instanceof ChoiceMenu, "the board didn't open: " + p.containerMenu);
			int slot = QaStageSixGameTests.slotOf((ChoiceMenu) p.containerMenu, Items.COBBLESTONE);
			helper.assertTrue(slot >= 0, "the board doesn't ask for cobblestone");
			QaStageSixGameTests.click(p, slot, 0, ClickType.PICKUP);
			helper.assertTrue(chest.countItem(Items.COBBLESTONE) == 25 && p.getInventory().countItem(Items.COBBLESTONE) == 15,
				"the hand-over: chest " + chest.countItem(Items.COBBLESTONE) + ", kept " + p.getInventory().countItem(Items.COBBLESTONE));
			p.closeContainer();
			assertAsksNone(helper, p, w, "opened again the same tick");
			p.closeContainer();
			helper.runAfterDelay(1, () -> {
				assertAsksNone(helper, p, w, "opened again a tick later");
				QaStageSixGameTests.click(p, slot, 0, ClickType.PICKUP);
				helper.assertTrue(p.getInventory().countItem(Items.COBBLESTONE) == 15, "a click a tick later took " + (15 - p.getInventory().countItem(Items.COBBLESTONE)));
				p.closeContainer();
				waitForUse(helper, p, w, chest, slot, 0);
			});
		});
	}

	/** Polls until the builder has taken cobblestone from his chest, then opens the board once more. */
	private static void waitForUse(GameTestHelper helper, ServerPlayer p, QaStageSixGameTests.Waiting w, Container chest, int slot, int waited) {
		if (chest.countItem(Items.COBBLESTONE) < 25) {
			assertAsksNone(helper, p, w, "after the builder took " + (25 - chest.countItem(Items.COBBLESTONE)) + " from his chest");
			QaStageSixGameTests.click(p, slot, 0, ClickType.PICKUP);
			helper.assertTrue(p.getInventory().countItem(Items.COBBLESTONE) == 15,
				"after the builder used some, a click handed over " + (15 - p.getInventory().countItem(Items.COBBLESTONE)));
			p.closeContainer();
			LOGGER.info("qaClicksBoard: after {} ticks the builder took {} cobblestone; the board asked for none and the player kept 15",
				waited, 25 - chest.countItem(Items.COBBLESTONE));
			waitForPlaced(helper, p, w, slot, 0);
			return;
		}
		helper.assertTrue(waited < 1000, "the builder never took the cobblestone from his chest (" + w.site().status() + ")");
		helper.runAfterDelay(20, () -> waitForUse(helper, p, w, chest, slot, waited + 20));
	}

	/** Polls until cobblestone stands in the hut (the builder has used some), then opens the board once more. */
	private static void waitForPlaced(GameTestHelper helper, ServerPlayer p, QaStageSixGameTests.Waiting w, int slot, int waited) {
		BlockPos hut = helper.absolutePos(HUT);
		int placed = 0;
		for (BlockPos pos : BlockPos.betweenClosed(hut.offset(-1, -1, -1), hut.offset(7, 6, 7))) {
			if (helper.getLevel().getBlockState(pos).is(Blocks.COBBLESTONE)) {
				placed++;
			}
		}
		if (placed > 0) {
			assertAsksNone(helper, p, w, "after the builder laid " + placed + " cobblestone");
			QaStageSixGameTests.click(p, slot, 0, ClickType.PICKUP);
			helper.assertTrue(p.getInventory().countItem(Items.COBBLESTONE) == 15,
				"after the builder laid some, a click handed over " + (15 - p.getInventory().countItem(Items.COBBLESTONE)));
			p.closeContainer();
			LOGGER.info("qaClicksBoard: after the builder laid {} cobblestone the board asked for none and the player kept 15", placed);
			helper.succeed();
			return;
		}
		helper.assertTrue(waited < 900, "the builder laid no cobblestone in 900 ticks (" + w.site().status() + ", " + w.site().stage() + ")");
		helper.runAfterDelay(20, () -> waitForPlaced(helper, p, w, slot, waited + 20));
	}

	private static void assertAsksNone(GameTestHelper helper, ServerPlayer p, QaStageSixGameTests.Waiting w, String when) {
		QaStageFourGameTests.press(helper, p, w.storehouse());
		if (p.containerMenu instanceof ChoiceMenu board) {
			int s = QaStageSixGameTests.slotOf(board, Items.COBBLESTONE);
			helper.assertTrue(s < 0, when + ": the board still asks for " + (s < 0 ? 0 : board.icon(s).getCount()) + " cobblestone");
		}
		helper.assertTrue(p.getInventory().countItem(Items.COBBLESTONE) == 15, when + ": the player has " + p.getInventory().countItem(Items.COBBLESTONE));
	}
}
