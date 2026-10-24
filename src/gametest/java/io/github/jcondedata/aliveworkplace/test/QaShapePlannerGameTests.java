package io.github.jcondedata.aliveworkplace.test;

import io.github.jcondedata.aliveworkplace.blueprint.Blueprint;
import io.github.jcondedata.aliveworkplace.blueprint.BlueprintData;
import io.github.jcondedata.aliveworkplace.blueprint.BlueprintItem;
import io.github.jcondedata.aliveworkplace.blueprint.BlueprintLibrary;
import io.github.jcondedata.aliveworkplace.blueprint.Shapes;
import io.github.jcondedata.aliveworkplace.build.BuildSite;
import io.github.jcondedata.aliveworkplace.build.BuildSiteManager;
import io.github.jcondedata.aliveworkplace.build.Builders;
import io.github.jcondedata.aliveworkplace.registry.ModBlocks;
import io.github.jcondedata.aliveworkplace.registry.ModComponents;
import io.github.jcondedata.aliveworkplace.registry.ModGameRules;
import io.github.jcondedata.aliveworkplace.registry.ModItems;
import io.github.jcondedata.aliveworkplace.work.ChoiceMenu;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;

/**
 * QA (lane-b-1024-0938) of the Shape Planner, from its guide page (docs/guide/shape-planner.md: "Right-click to open it.
 * Pick a box, cylinder, dome, sphere, cone, pyramid or arch, its size, solid or hollow, and a block you're carrying to
 * build it in. Then draw it: you get a Blueprint, and it takes a Blank Blueprint." and "Builders build it like any other
 * blueprint"), the planner's own lines in en_us.json and Shapes.MAX ("up to 32 a side"), all through the screen the
 * right-click opens, clicked as the client clicks it.
 */
public class QaShapePlannerGameTests implements net.fabricmc.fabric.api.gametest.v1.FabricGameTest {
	private static final String AREA = "aliveworkplace_test:build_area";
	private static final org.slf4j.Logger LOGGER = org.slf4j.LoggerFactory.getLogger("aliveworkplace_test");

	private static List<String> texts(List<Component> seen) {
		return seen.stream().map(Component::getString).toList();
	}

	/** The planner in the main hand, opened by a right-click on the air; the screen it opened. */
	private static ChoiceMenu open(GameTestHelper helper, ServerPlayer p) {
		QaStageFourGameTests.pressAir(helper, p);
		helper.assertTrue(p.containerMenu instanceof ChoiceMenu, "a right-click with the planner opened " + p.containerMenu.getClass().getSimpleName());
		return (ChoiceMenu) p.containerMenu;
	}

	private static void click(ServerPlayer p, int slot, boolean shift) {
		QaStageSixGameTests.click(p, slot, 0, shift ? ClickType.QUICK_MOVE : ClickType.PICKUP);
	}

	private static Shapes.Settings settings(ServerPlayer p) {
		return p.getMainHandItem().get(ModComponents.SHAPE);
	}

	private static List<ItemStack> blueprints(ServerPlayer p) {
		List<ItemStack> out = new ArrayList<>();
		for (int i = 0; i < p.getInventory().getContainerSize(); i++) {
			if (p.getInventory().getItem(i).is(ModItems.BLUEPRINT)) {
				out.add(p.getInventory().getItem(i));
			}
		}
		return out;
	}

	/** A survival player holding the planner, carrying {@code carried}. */
	private static QaStageFiveGameTests.Client planner(GameTestHelper helper, String prefix, ItemStack... carried) {
		QaStageFiveGameTests.Client c = QaStageSixGameTests.client(helper, GameType.SURVIVAL, QaStageSixGameTests.name(prefix));
		ServerPlayer p = c.player();
		p.getInventory().clearContent();
		p.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(ModItems.SHAPE_PLANNER));
		for (int i = 0; i < carried.length; i++) {
			p.getInventory().setItem(1 + i, carried[i].copy());
		}
		return c;
	}

	/**
	 * "its size": each side goes from 1 to 32 (Shapes.MAX) and no further. Width clicked down past 1 (five shift-clicks
	 * of -5) stays 1; clicked up (shift-clicks of +5) stops at 32; the screen's label reads "Width: 32" and the planner
	 * keeps it when the screen is opened again. Height and depth the same.
	 */
	//$ gametest_ticks_batch AREA '60' '"qaShapesSize"'
	@GameTest(template = AREA, timeoutTicks = 60, batch = "qaShapesSize")
	public void eachSideStopsAtOneAndAtThirtyTwo(GameTestHelper helper) {
		Leftovers.clear(helper);
		QaStageFiveGameTests.Client c = planner(helper, "qasS", new ItemStack(Items.STONE_BRICKS, 64));
		ServerPlayer p = c.player();
		record Side(String name, int slot, java.util.function.ToIntFunction<Shapes.Settings> get) {
		}
		for (Side side : List.of(new Side("Width", Shapes.WIDTH, Shapes.Settings::width), new Side("Height", Shapes.HEIGHT, Shapes.Settings::height),
			new Side("Depth", Shapes.DEPTH, Shapes.Settings::depth))) {
			ChoiceMenu menu = open(helper, p);
			for (int i = 0; i < 12; i++) {
				click(p, side.slot() - 1, true);
			}
			helper.assertTrue(side.get().applyAsInt(settings(p)) == 1, side.name() + " clicked down went to " + side.get().applyAsInt(settings(p)));
			click(p, side.slot() - 1, false);
			helper.assertTrue(side.get().applyAsInt(settings(p)) == 1, side.name() + " went below 1: " + side.get().applyAsInt(settings(p)));
			click(p, side.slot() + 1, false);
			helper.assertTrue(side.get().applyAsInt(settings(p)) == 2, side.name() + " +1 from 1 is " + side.get().applyAsInt(settings(p)));
			for (int i = 0; i < 8; i++) {
				click(p, side.slot() + 1, true);
			}
			helper.assertTrue(side.get().applyAsInt(settings(p)) == Shapes.MAX, side.name() + " clicked up went to " + side.get().applyAsInt(settings(p)));
			helper.assertTrue(menu.icon(side.slot()).getHoverName().getString().equals(side.name() + ": 32"),
				side.name() + "'s label reads " + menu.icon(side.slot()).getHoverName().getString());
			p.closeContainer();
			open(helper, p);
			helper.assertTrue(side.get().applyAsInt(settings(p)) == Shapes.MAX, side.name() + " wasn't kept: " + side.get().applyAsInt(settings(p)));
			p.closeContainer();
		}
		helper.assertTrue(Shapes.MAX == 32, "Shapes.MAX is " + Shapes.MAX);
		LOGGER.info("qa shapes: sizes {}", settings(p));
		helper.succeed();
	}

	/**
	 * "it takes a Blank Blueprint": a survival player with no Blank Blueprint who draws gets no blueprint, keeps the
	 * planner and the blocks, and is told he needs one. "a block you're carrying": with no block picked, drawing says
	 * "Pick a block to build it in first (carry some)." and uses no Blank Blueprint.
	 */
	//$ gametest_ticks_batch AREA '60' '"qaShapesNoBlank"'
	@GameTest(template = AREA, timeoutTicks = 60, batch = "qaShapesNoBlank")
	public void withNoBlankOrNoBlockNothingIsDrawnAndNothingIsTaken(GameTestHelper helper) {
		Leftovers.clear(helper);
		QaStageFiveGameTests.Client c = planner(helper, "qasN", new ItemStack(Items.STONE_BRICKS, 64), new ItemStack(ModItems.BLANK_BLUEPRINT));
		ServerPlayer p = c.player();
		ChoiceMenu menu = open(helper, p);
		helper.assertTrue(settings(p) == null || settings(p).material().isEmpty(), "a new planner has a block picked: " + settings(p));
		click(p, Shapes.DRAW, false);
		helper.assertTrue(texts(c.seen()).contains("Pick a block to build it in first (carry some)."), "drawing with no block said " + texts(c.seen()));
		helper.assertTrue(blueprints(p).isEmpty(), "a blueprint was drawn with no block");
		helper.assertTrue(p.getInventory().countItem(ModItems.BLANK_BLUEPRINT) == 1, "the Blank Blueprint went with no block picked");

		p.getInventory().clearContent();
		p.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(ModItems.SHAPE_PLANNER));
		p.getInventory().setItem(1, new ItemStack(Items.STONE_BRICKS, 64));
		menu = open(helper, p);
		click(p, Shapes.FIRST_MATERIAL, false);
		helper.assertTrue(settings(p) != null && settings(p).material().isPresent(), "picking the stone bricks didn't stick: " + settings(p));
		c.seen().clear();
		click(p, Shapes.DRAW, false);
		helper.assertTrue(blueprints(p).isEmpty(), "a blueprint was drawn without a Blank Blueprint");
		helper.assertTrue(p.getMainHandItem().is(ModItems.SHAPE_PLANNER) && p.getInventory().countItem(Items.STONE_BRICKS) == 64,
			"the planner or the blocks went: " + p.getMainHandItem() + ", " + p.getInventory().countItem(Items.STONE_BRICKS) + " stone bricks");
		helper.assertTrue(texts(c.seen()).stream().anyMatch(t -> t.contains("Blank Blueprint")), "drawing without a Blank Blueprint said " + texts(c.seen()));
		LOGGER.info("qa shapes, no blank: saw {}", texts(c.seen()));
		helper.succeed();
	}

	/**
	 * "Then draw it: you get a Blueprint, and it takes a Blank Blueprint": a hollow 5 x 4 x 5 box of stone bricks drawn
	 * twice with two Blank Blueprints gives two blueprints of the same build and uses both blanks; the player reads
	 * "Drew <name>: <n> blocks of Stone Bricks. Give it to a builder." for each draw, n the blueprint's own block count; the box is
	 * hollow (fewer blocks than the 100 of a solid one) and 5 x 4 x 5.
	 */
	//$ gametest_ticks_batch AREA '60' '"qaShapesDraw"'
	@GameTest(template = AREA, timeoutTicks = 60, batch = "qaShapesDraw")
	public void drawingTwiceGivesTheSameBuildForTwoBlanks(GameTestHelper helper) {
		Leftovers.clear(helper);
		QaStageFiveGameTests.Client c = planner(helper, "qasD", new ItemStack(Items.STONE_BRICKS, 64), new ItemStack(ModItems.BLANK_BLUEPRINT, 2));
		ServerPlayer p = c.player();
		open(helper, p);
		click(p, Shapes.FIRST_KIND + Shapes.Kind.BOX.ordinal(), false);
		click(p, Shapes.FIRST_MATERIAL, false);
		for (int slot : List.of(Shapes.WIDTH, Shapes.HEIGHT, Shapes.DEPTH)) {
			for (int i = 0; i < 8; i++) {
				click(p, slot - 1, true);
			}
		}
		for (int i = 0; i < 4; i++) {
			click(p, Shapes.WIDTH + 1, false);
			click(p, Shapes.DEPTH + 1, false);
		}
		for (int i = 0; i < 3; i++) {
			click(p, Shapes.HEIGHT + 1, false);
		}
		if (!settings(p).hollow()) {
			click(p, Shapes.HOLLOW, false);
		}
		Shapes.Settings s = settings(p);
		helper.assertTrue(s.shape() == Shapes.Kind.BOX && s.width() == 5 && s.height() == 4 && s.depth() == 5 && s.hollow(), "settings: " + s);
		click(p, Shapes.DRAW, false);
		helper.assertTrue(blueprints(p).size() == 1, "the first draw gave " + blueprints(p).size() + " blueprints; saw " + texts(c.seen()));
		helper.assertTrue(p.containerMenu == p.inventoryMenu, "the screen stayed open after drawing");
		open(helper, p);
		click(p, Shapes.DRAW, false);
		List<ItemStack> drawn = blueprints(p);
		int total = drawn.stream().mapToInt(ItemStack::getCount).sum();
		helper.assertTrue(total == 2, "two draws gave " + total + " blueprints");
		helper.assertTrue(p.getInventory().countItem(ModItems.BLANK_BLUEPRINT) == 0, "a Blank Blueprint is left after two draws");
		BlueprintData first = BlueprintItem.data(drawn.get(0)).orElseThrow();
		helper.assertTrue(drawn.stream().allMatch(b -> BlueprintItem.data(b).orElseThrow().structure().equals(first.structure())), "the two draws differ");
		Blueprint blueprint = BlueprintLibrary.get(helper.getLevel(), first.structure()).orElseThrow();
		helper.assertTrue(blueprint.size().getX() == 5 && blueprint.size().getY() == 4 && blueprint.size().getZ() == 5, "size " + blueprint.size());
		int count = blueprint.solidBlockCount();
		helper.assertTrue(count > 0 && count < 100, "a hollow 5 x 4 x 5 box has " + count + " blocks");
		String name = io.github.jcondedata.aliveworkplace.blueprint.Blueprints.displayName(first.structure()).getString();
		helper.assertTrue(texts(c.seen()).stream().filter(t -> t.equals("Drew " + name + ": " + count + " blocks of Stone Bricks. Give it to a builder.")).count() >= 2,
			"the draws said " + texts(c.seen()));
		LOGGER.info("qa shapes, drawn twice: {} ({} blocks)", first.structure(), count);
		helper.succeed();
	}

	/** Sets up a box of {@code w} x {@code h} x {@code d} in the first block carried, hollow or solid, on the open screen. */
	private static void box(ServerPlayer p, int w, int h, int d, boolean hollow) {
		click(p, Shapes.FIRST_KIND + Shapes.Kind.BOX.ordinal(), false);
		click(p, Shapes.FIRST_MATERIAL, false);
		for (int slot : List.of(Shapes.WIDTH, Shapes.HEIGHT, Shapes.DEPTH)) {
			for (int i = 0; i < 8; i++) {
				click(p, slot - 1, true);
			}
		}
		for (int i = 1; i < w; i++) {
			click(p, Shapes.WIDTH + 1, false);
		}
		for (int i = 1; i < h; i++) {
			click(p, Shapes.HEIGHT + 1, false);
		}
		for (int i = 1; i < d; i++) {
			click(p, Shapes.DEPTH + 1, false);
		}
		if (settings(p).hollow() != hollow) {
			click(p, Shapes.HOLLOW, false);
		}
	}

	/**
	 * "solid or hollow": a hollow 4 x 3 x 4 box and then a solid one of the same size and block are two different builds:
	 * the solid one has all 48 blocks, the hollow one fewer, and the second draw doesn't hand out the first's.
	 */
	//$ gametest_ticks_batch AREA '60' '"qaShapesHollowSolid"'
	@GameTest(template = AREA, timeoutTicks = 60, batch = "qaShapesHollowSolid")
	public void aHollowAndASolidBoxOfOneSizeAreTwoBuilds(GameTestHelper helper) {
		Leftovers.clear(helper);
		QaStageFiveGameTests.Client c = planner(helper, "qasH", new ItemStack(Items.STONE_BRICKS, 64), new ItemStack(ModItems.BLANK_BLUEPRINT, 2));
		ServerPlayer p = c.player();
		List<Integer> counts = new ArrayList<>();
		List<net.minecraft.resources.ResourceLocation> ids = new ArrayList<>();
		for (boolean hollow : List.of(true, false)) {
			open(helper, p);
			box(p, 4, 3, 4, hollow);
			helper.assertTrue(settings(p).hollow() == hollow, "the box isn't " + (hollow ? "hollow" : "solid") + ": " + settings(p));
			int before = blueprints(p).stream().mapToInt(ItemStack::getCount).sum();
			click(p, Shapes.DRAW, false);
			ItemStack drawn = blueprints(p).stream().filter(b -> ids.stream().noneMatch(id -> BlueprintItem.data(b).orElseThrow().structure().equals(id)))
				.findFirst().orElse(ItemStack.EMPTY);
			helper.assertTrue(blueprints(p).stream().mapToInt(ItemStack::getCount).sum() == before + 1, (hollow ? "hollow" : "solid") + ": no blueprint drawn");
			helper.assertTrue(!drawn.isEmpty(), (hollow ? "the hollow" : "the solid") + " box drew the same build as before: " + ids);
			var id = BlueprintItem.data(drawn).orElseThrow().structure();
			ids.add(id);
			counts.add(BlueprintLibrary.get(helper.getLevel(), id).orElseThrow().solidBlockCount());
		}
		helper.assertTrue(counts.get(1) == 48, "the solid 4 x 3 x 4 box has " + counts.get(1) + " blocks");
		helper.assertTrue(counts.get(0) < 48, "the hollow box has " + counts.get(0) + " blocks, the solid " + counts.get(1));
		LOGGER.info("qa shapes, hollow {} ({}), solid {} ({})", ids.get(0), counts.get(0), ids.get(1), counts.get(1));
		helper.succeed();
	}

	/**
	 * "it takes a Blank Blueprint", wherever it is carried: with the planner in the second hotbar slot and the Blank
	 * Blueprint in the first, drawing uses that blank. With every slot full (two blanks in the first slot, so using one
	 * frees none), the drawn blueprint isn't lost: it lands at the player's feet.
	 */
	//$ gametest_ticks_batch AREA '60' '"qaShapesSlots"'
	@GameTest(template = AREA, timeoutTicks = 60, batch = "qaShapesSlots")
	public void theBlankInTheFirstSlotIsUsedAndAFullBagDropsTheBlueprint(GameTestHelper helper) {
		Leftovers.clear(helper);
		QaStageFiveGameTests.Client c = QaStageSixGameTests.client(helper, GameType.SURVIVAL, QaStageSixGameTests.name("qasF"));
		ServerPlayer p = c.player();
		p.getInventory().clearContent();
		p.getInventory().setItem(0, new ItemStack(ModItems.BLANK_BLUEPRINT));
		p.getInventory().setItem(1, new ItemStack(ModItems.SHAPE_PLANNER));
		p.getInventory().setItem(2, new ItemStack(Items.STONE_BRICKS, 64));
		p.getInventory().selected = 1;
		open(helper, p);
		box(p, 2, 2, 2, false);
		click(p, Shapes.DRAW, false);
		helper.assertTrue(p.getInventory().countItem(ModItems.BLANK_BLUEPRINT) == 0, "the Blank Blueprint in the first slot wasn't used; saw " + texts(c.seen())
			+ "; slots 0-3: " + p.getInventory().items.subList(0, 4));
		helper.assertTrue(blueprints(p).size() == 1, "no blueprint for the blank in the first slot; saw " + texts(c.seen()));

		// every slot full: a Blank Blueprint, the planner (as set up), the block, and dirt everywhere else
		ItemStack planner = p.getInventory().getItem(1).copy();
		for (int i = 0; i < p.getInventory().items.size(); i++) {
			p.getInventory().items.set(i, new ItemStack(Items.DIRT, 64));
		}
		p.getInventory().items.set(0, new ItemStack(ModItems.BLANK_BLUEPRINT, 2)); // two: using one frees no slot
		p.getInventory().items.set(1, planner);
		p.getInventory().items.set(2, new ItemStack(Items.STONE_BRICKS, 64));
		p.setItemInHand(InteractionHand.OFF_HAND, new ItemStack(Items.DIRT, 64));
		p.getInventory().selected = 1;
		open(helper, p);
		click(p, Shapes.DRAW, false);
		helper.assertTrue(p.getInventory().countItem(ModItems.BLANK_BLUEPRINT) == 1, "one blank of two should be used with a full bag; saw " + texts(c.seen()));
		List<net.minecraft.world.entity.item.ItemEntity> dropped = helper.getLevel().getEntitiesOfClass(net.minecraft.world.entity.item.ItemEntity.class,
			p.getBoundingBox().inflate(4), e -> e.getItem().is(ModItems.BLUEPRINT));
		helper.assertTrue(dropped.size() == 1, "with every slot full the drawn blueprint isn't at the player's feet: " + dropped.size() + " found; saw " + texts(c.seen()));
		dropped.forEach(net.minecraft.world.entity.Entity::discard);
		helper.succeed();
	}

	/**
	 * "Builders build it like any other blueprint: stock the block you picked in the chests round their table." A solid
	 * 3 x 2 x 3 box of stone bricks drawn with the planner, placed with a right-click and handed to a builder with 18
	 * stone bricks in the chest by his table, stands complete, and the chest is empty.
	 */
	//$ gametest_ticks_batch AREA '2400' '"qaShapesBuilt"'
	@GameTest(template = AREA, timeoutTicks = 2400, batch = "qaShapesBuilt")
	public void aBuilderBuildsADrawnShapeFromTheChest(GameTestHelper helper) {
		Leftovers.clear(helper);
		ServerLevel level = helper.getLevel();
		helper.setDayTime(2000);
		var rules = level.getGameRules();
		int delay = rules.getRule(ModGameRules.BUILD_DELAY).get();
		boolean help = rules.getRule(ModGameRules.BUILDERS_HELP).get();
		rules.getRule(ModGameRules.BUILD_DELAY).set(2, level.getServer());
		rules.getRule(ModGameRules.BUILDERS_HELP).set(false, level.getServer());
		boolean protection = io.github.jcondedata.aliveworkplace.hall.VillageProtection.ENABLED;
		io.github.jcondedata.aliveworkplace.hall.VillageProtection.ENABLED = false;
		Leftovers.after(helper, () -> {
			rules.getRule(ModGameRules.BUILD_DELAY).set(delay, level.getServer());
			rules.getRule(ModGameRules.BUILDERS_HELP).set(help, level.getServer());
			io.github.jcondedata.aliveworkplace.hall.VillageProtection.ENABLED = protection;
		});
		QaStageFiveGameTests.Client c = planner(helper, "qasB", new ItemStack(Items.STONE_BRICKS, 1), new ItemStack(ModItems.BLANK_BLUEPRINT));
		ServerPlayer p = c.player();
		open(helper, p);
		click(p, Shapes.FIRST_KIND + Shapes.Kind.BOX.ordinal(), false);
		click(p, Shapes.FIRST_MATERIAL, false);
		for (int slot : List.of(Shapes.WIDTH, Shapes.HEIGHT, Shapes.DEPTH)) {
			for (int i = 0; i < 8; i++) {
				click(p, slot - 1, true);
			}
		}
		click(p, Shapes.WIDTH + 1, false);
		click(p, Shapes.WIDTH + 1, false);
		click(p, Shapes.DEPTH + 1, false);
		click(p, Shapes.DEPTH + 1, false);
		click(p, Shapes.HEIGHT + 1, false);
		if (settings(p).hollow()) {
			click(p, Shapes.HOLLOW, false);
		}
		click(p, Shapes.DRAW, false);
		List<ItemStack> drawn = blueprints(p);
		helper.assertTrue(drawn.size() == 1, "nothing drawn: " + texts(c.seen()));
		ItemStack blueprint = drawn.get(0).copy();
		var id = BlueprintItem.data(blueprint).orElseThrow().structure();
		helper.assertTrue(BlueprintLibrary.get(level, id).orElseThrow().solidBlockCount() == 18, "a solid 3 x 2 x 3 box has "
			+ BlueprintLibrary.get(level, id).orElseThrow().solidBlockCount() + " blocks");
		p.getInventory().clearContent();
		p.setItemInHand(InteractionHand.MAIN_HAND, blueprint);
		p.setYRot(0);
		p.setYHeadRot(0);
		QaStageFourGameTests.press(helper, p, new BlockPos(8, 1, 9), net.minecraft.core.Direction.UP);
		BlueprintData.Placement placement = BlueprintItem.data(p.getMainHandItem()).orElseThrow().placement().orElse(null);
		helper.assertTrue(placement != null, "the drawn blueprint wasn't placed: " + texts(c.seen()));

		BlockPos table = new BlockPos(2, 2, 2);
		BlockPos chestPos = new BlockPos(2, 2, 4);
		helper.setBlock(table, ModBlocks.BLUEPRINT_TABLE);
		helper.setBlock(chestPos, Blocks.CHEST);
		Container chest = helper.getBlockEntity(chestPos);
		chest.setItem(0, new ItemStack(Items.STONE_BRICKS, 18));
		Villager builder = helper.spawn(EntityType.VILLAGER, new BlockPos(3, 2, 3));
		Builders.employ(level, builder, helper.absolutePos(table));
		Leftovers.after(helper, () -> {
			BuildSiteManager sites = BuildSiteManager.get(level);
			for (BuildSite site : List.copyOf(sites.all())) {
				if (builder.getUUID().equals(site.builder())) {
					sites.remove(site.id());
				}
			}
		});
		QaStageSixGameTests.clickVillager(p, builder, c.seen());
		BuildSite site = Builders.activeSite(level, builder);
		helper.assertTrue(site != null && site.structure().equals(id), "the builder didn't take the drawn shape: " + texts(c.seen()));
		var plan = site.plan(level);
		helper.succeedWhen(() -> {
			helper.assertTrue(BuildSiteManager.get(level).get(site.id()) == null, "still building: " + site.stage() + ", missing " + site.missing());
			helper.assertTrue(plan.unfinished(level).isEmpty(), plan.unfinished(level).size() + " block(s) of the box wrong");
			helper.assertTrue(chest.countItem(Items.STONE_BRICKS) == 0, chest.countItem(Items.STONE_BRICKS) + " stone bricks left in the chest");
		});
	}
}
