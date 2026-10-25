package io.github.jcondedata.aliveworkplace.test;

import io.github.jcondedata.aliveworkplace.blueprint.BlueprintData;
import io.github.jcondedata.aliveworkplace.blueprint.BlueprintItem;
import io.github.jcondedata.aliveworkplace.blueprint.BlueprintLibrary;
import io.github.jcondedata.aliveworkplace.blueprint.BlueprintOutline;
import io.github.jcondedata.aliveworkplace.blueprint.Blueprints;
import io.github.jcondedata.aliveworkplace.build.BuildSite;
import io.github.jcondedata.aliveworkplace.build.BuildSiteManager;
import io.github.jcondedata.aliveworkplace.build.Builders;
import io.github.jcondedata.aliveworkplace.registry.ModBlocks;
import io.github.jcondedata.aliveworkplace.registry.ModComponents;
import io.github.jcondedata.aliveworkplace.registry.ModGameRules;
import io.github.jcondedata.aliveworkplace.registry.ModItems;
import io.github.jcondedata.aliveworkplace.table.TablePayloads;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Vec3i;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;

/**
 * QA (lane-b-1024-0938) of the builder's limits, from the player guide (docs/guide/builder.md: "Busy builders queue up to
 * five more blueprints", "Up to three builders with nothing to do join a build near their table"), en_us.json
 * (queue.added, queue.full, assign.too_far) and B337 ("A Table no builder works at opens as usual"): each limit from
 * both sides, through the player's own clicks, with the sentences the player reads.
 */
public class QaBuilderLimitsGameTests implements net.fabricmc.fabric.api.gametest.v1.FabricGameTest {
	private static final String AREA = "aliveworkplace_test:build_area";
	private static final String HUGE = "aliveworkplace_test:huge_area";
	private static final org.slf4j.Logger LOGGER = org.slf4j.LoggerFactory.getLogger("aliveworkplace_test");
	private static final ResourceLocation TEST_HUT = ResourceLocation.fromNamespaceAndPath("aliveworkplace_test", "test_hut");
	private static final BlockPos TABLE = new BlockPos(2, 2, 2);
	private static final BlockPos CHEST = new BlockPos(2, 2, 4);
	private static final BlockPos VILLAGER = new BlockPos(3, 2, 3);
	private static final BlockPos HUT = new BlockPos(6, 2, 6);

	private static void noVillageProtection(GameTestHelper helper) {
		boolean enabled = io.github.jcondedata.aliveworkplace.hall.VillageProtection.ENABLED;
		io.github.jcondedata.aliveworkplace.hall.VillageProtection.ENABLED = false;
		Leftovers.after(helper, () -> io.github.jcondedata.aliveworkplace.hall.VillageProtection.ENABLED = enabled);
	}

	/** Every site this test's builder was handed is taken off the world's list when the test ends (passed or failed). */
	private static void forgetSitesOf(GameTestHelper helper, Villager builder) {
		ServerLevel level = helper.getLevel();
		Leftovers.after(helper, () -> {
			BuildSiteManager sites = BuildSiteManager.get(level);
			for (BuildSite site : List.copyOf(sites.all())) {
				if (builder.getUUID().equals(site.builder())) {
					sites.remove(site.id());
				}
			}
		});
	}

	private static List<String> texts(List<Component> seen) {
		return seen.stream().map(Component::getString).toList();
	}

	/** A placed test-hut blueprint whose outline's centre is {@code offset} from {@code bench}. */
	static ItemStack placedHut(ServerLevel level, BlockPos bench, Vec3i offset) {
		Vec3i size = BlueprintLibrary.get(level, TEST_HUT).orElseThrow().size();
		BlueprintData.Placement atBench = new BlueprintData.Placement(level.dimension().location(), bench, Rotation.NONE, Mirror.NONE);
		BlockPos centre = BlueprintOutline.bounds(atBench, size).getCenter();
		BlockPos origin = bench.offset(bench.subtract(centre)).offset(offset);
		BlueprintData.Placement placement = new BlueprintData.Placement(level.dimension().location(), origin, Rotation.NONE, Mirror.NONE);
		ItemStack blueprint = BlueprintItem.create(TEST_HUT, size);
		blueprint.set(ModComponents.BLUEPRINT, BlueprintItem.data(blueprint).orElseThrow().withPlacement(Optional.of(placement)));
		return blueprint;
	}

	/** How far the outline's centre of a placed blueprint is from {@code bench}, worked out as the guide means it. */
	static double distance(ServerLevel level, ItemStack blueprint, BlockPos bench) {
		BlueprintData.Placement placement = BlueprintItem.data(blueprint).orElseThrow().placement().orElseThrow();
		Vec3i size = BlueprintLibrary.get(level, TEST_HUT).orElseThrow().size();
		return Math.sqrt(BlueprintOutline.bounds(placement, size).getCenter().distSqr(bench));
	}

	// --- B337: a Blueprint Table nobody works at -------------------------------------------------------------------

	/**
	 * B337: "A Table no builder works at opens as usual." A Book and Quill used on a Blueprint Table with no builder opens
	 * the table's library (the Open packet is sent), says nothing about a build ("No build is under way from this
	 * bench ..." is for a builder's table) and the book is kept as it was.
	 */
	//$ gametest_ticks_batch AREA '60' '"qaLimitsLoneTable"'
	@GameTest(template = AREA, timeoutTicks = 60, batch = "qaLimitsLoneTable")
	public void aBookAndQuillOnATableNobodyWorksAtOpensTheTable(GameTestHelper helper) {
		Leftovers.clear(helper);
		noVillageProtection(helper);
		helper.setBlock(TABLE, ModBlocks.BLUEPRINT_TABLE);
		BlockPos abs = helper.absolutePos(TABLE);
		for (InteractionHand hand : InteractionHand.values()) {
			QaStageFiveGameTests.Client c = QaStageSixGameTests.client(helper, GameType.SURVIVAL, QaStageSixGameTests.name("qalT"));
			ServerPlayer p = c.player();
			p.setItemInHand(hand, new ItemStack(Items.WRITABLE_BOOK));
			c.sent();
			QaStageFourGameTests.press(helper, p, TABLE, Direction.NORTH);
			List<TablePayloads.Open> opened = c.sent(TablePayloads.Open.class);
			helper.assertTrue(opened.size() == 1 && opened.get(0).table().equals(abs),
				hand + ": the lone table didn't open, sent " + opened.size() + " Open, saw " + texts(c.seen()));
			helper.assertFalse(texts(c.seen()).stream().anyMatch(t -> t.startsWith("No build is under way")),
				hand + ": a table nobody works at spoke of a build: " + texts(c.seen()));
			helper.assertTrue(p.getItemInHand(hand).is(Items.WRITABLE_BOOK) && p.getItemInHand(hand).getCount() == 1,
				hand + ": the book went: " + p.getItemInHand(hand));
			LOGGER.info("qa lone table, book in the {}: opened, saw {}", hand, texts(c.seen()));
			p.closeContainer();
		}
		helper.succeed();
	}

	// --- The queue: five more, not six -----------------------------------------------------------------------------

	/**
	 * Guide: "Busy builders queue up to five more blueprints and build them in order." Seven placed blueprints handed to
	 * one builder by a player's clicks: the first starts ("... will build Test Hut! ..."), the next five are lined up as
	 * numbers 1 to 5 ("(number n in line)"), and the seventh is refused with "<builder> already has 5 blueprints lined
	 * up. Hire another builder, or wait." and stays in the player's hand.
	 */
	//$ gametest_ticks_batch AREA '80' '"qaLimitsQueue"'
	@GameTest(template = AREA, timeoutTicks = 80, batch = "qaLimitsQueue")
	public void theQueueTakesFiveMoreAndRefusesTheSixth(GameTestHelper helper) {
		Leftovers.clear(helper);
		noVillageProtection(helper);
		ServerLevel level = helper.getLevel();
		helper.setBlock(TABLE, ModBlocks.BLUEPRINT_TABLE);
		Villager builder = helper.spawn(EntityType.VILLAGER, VILLAGER);
		builder.setNoAi(true);
		BlockPos bench = helper.absolutePos(TABLE);
		Builders.employ(level, builder, bench);
		forgetSitesOf(helper, builder);
		QaStageFiveGameTests.Client c = QaStageSixGameTests.client(helper, GameType.SURVIVAL, QaStageSixGameTests.name("qalQ"));
		ServerPlayer p = c.player();
		String name = builder.getDisplayName().getString();
		String hut = Blueprints.displayName(TEST_HUT).getString();
		for (int i = 0; i < 7; i++) {
			ItemStack blueprint = placedHut(level, bench, new Vec3i(6 + 6 * i, 0, 6));
			helper.assertTrue(distance(level, blueprint, bench) <= Builders.MAX_SITE_DISTANCE, "blueprint " + i + " is out of reach");
			p.setItemInHand(InteractionHand.MAIN_HAND, blueprint);
			c.seen().clear();
			QaStageSixGameTests.clickVillager(p, builder, c.seen());
			List<String> said = texts(c.seen());
			int lined = Builders.queue(level, builder).size();
			if (i == 0) {
				helper.assertTrue(Builders.activeSite(level, builder) != null, "the first blueprint started nothing: " + said);
				helper.assertTrue(said.stream().anyMatch(t -> t.startsWith(name + " will build " + hut + "!")), "the first hand-over said " + said);
				helper.assertTrue(p.getMainHandItem().isEmpty(), "the first blueprint stayed in hand");
			} else if (i <= Builders.MAX_QUEUE) {
				helper.assertTrue(lined == i, "after blueprint " + (i + 1) + ", " + lined + " lined up (should be " + i + "); saw " + said);
				helper.assertTrue(said.stream().anyMatch(t -> t.contains("(number " + lined + " in line)")), "blueprint " + (i + 1) + " said " + said);
				helper.assertTrue(p.getMainHandItem().isEmpty(), "blueprint " + (i + 1) + " stayed in hand");
			} else {
				helper.assertTrue(lined == Builders.MAX_QUEUE, "the seventh blueprint was lined up too: " + lined);
				helper.assertTrue(said.contains(name + " already has 5 blueprints lined up. Hire another builder, or wait."), "the seventh said " + said);
				helper.assertTrue(p.getMainHandItem().is(ModItems.BLUEPRINT) && p.getMainHandItem().getCount() == 1,
					"the refused blueprint wasn't kept: " + p.getMainHandItem());
			}
			LOGGER.info("qa queue, blueprint {}: {} lined up, saw {}", i + 1, lined, said);
		}
		helper.assertTrue(Builders.MAX_QUEUE == 5, "the guide says five more, the code lines up " + Builders.MAX_QUEUE);
		helper.succeed();
	}

	/**
	 * Guide: queued blueprints are built "in order"; /workplace cancel: "the blueprint is still placed there". Four
	 * blueprints handed over (one started, three lined up), then the world saved and loaded (the sites and the builder):
	 * the line is the same three in the same order. The player cancels each build in turn with /workplace cancel: each
	 * time the next in line is the one the builder takes up ("<builder> is starting on Test Hut next."), and the
	 * cancelled blueprint comes back to the player still placed where it was.
	 */
	//$ gametest_ticks_batch AREA '80' '"qaLimitsQueueReload"'
	@GameTest(template = AREA, timeoutTicks = 80, batch = "qaLimitsQueueReload")
	public void theQueueKeepsItsOrderOverASaveAndEachCancelTakesUpTheNext(GameTestHelper helper) {
		Leftovers.clear(helper);
		noVillageProtection(helper);
		ServerLevel level = helper.getLevel();
		helper.setBlock(TABLE, ModBlocks.BLUEPRINT_TABLE);
		Villager builder = helper.spawn(EntityType.VILLAGER, VILLAGER);
		builder.setNoAi(true);
		BlockPos bench = helper.absolutePos(TABLE);
		Builders.employ(level, builder, bench);
		java.util.UUID id = builder.getUUID();
		Leftovers.after(helper, () -> {
			BuildSiteManager sites = BuildSiteManager.get(level);
			for (BuildSite site : List.copyOf(sites.all())) {
				if (id.equals(site.builder())) {
					sites.remove(site.id());
				}
			}
		});
		QaStageFiveGameTests.Client c = QaStageSixGameTests.client(helper, GameType.SURVIVAL, QaStageSixGameTests.name("qalO"));
		ServerPlayer p = c.player();
		List<BlueprintData.Placement> handed = new ArrayList<>();
		// handed over in an order that isn't the order of their places
		for (int x : List.of(30, 6, 18, 12)) {
			ItemStack blueprint = placedHut(level, bench, new Vec3i(x, 0, 6));
			handed.add(BlueprintItem.data(blueprint).orElseThrow().placement().orElseThrow());
			p.setItemInHand(InteractionHand.MAIN_HAND, blueprint);
			QaStageSixGameTests.clickVillager(p, builder, c.seen());
		}
		helper.assertTrue(Builders.queue(level, builder).size() == 3, "3 should be lined up: " + Builders.queue(level, builder).size() + "; saw " + texts(c.seen()));

		BuildSiteManager loaded = BuildSiteManager.load(BuildSiteManager.get(level).save(new net.minecraft.nbt.CompoundTag(), level.registryAccess()), level.registryAccess());
		level.getDataStorage().set(BuildSiteManager.NAME, loaded);
		net.minecraft.nbt.CompoundTag tag = new net.minecraft.nbt.CompoundTag();
		helper.assertTrue(builder.save(tag), "the builder wasn't saved");
		builder.discard();
		net.minecraft.world.entity.Entity back = EntityType.loadEntityRecursive(tag, level, e -> e);
		helper.assertTrue(back instanceof Villager, "the builder didn't load: " + back);
		level.addFreshEntity(back);
		Villager again = (Villager) back;

		List<BlueprintData.Placement> line = Builders.queue(level, again).stream().map(BuildSite::placement).toList();
		helper.assertTrue(line.equals(handed.subList(1, 4)), "after the reload the line is " + line + ", handed over " + handed.subList(1, 4));
		String hut = Blueprints.displayName(TEST_HUT).getString();
		for (int i = 0; i < 4; i++) {
			BuildSite active = Builders.activeSite(level, again);
			helper.assertTrue(active != null && active.placement().equals(handed.get(i)), "build " + (i + 1) + " should be at " + handed.get(i) + ", it is "
				+ (active == null ? "none" : active.placement()));
			c.seen().clear();
			p.getInventory().clearContent();
			QaStageSixGameTests.command(c, "/workplace cancel " + active.id());
			helper.assertTrue(texts(c.seen()).contains("Canceled the build of " + hut + ". Its blueprint is still placed there: hand it back to carry on, or click the ground to move it."), "cancelling build " + (i + 1) + " said " + texts(c.seen()));
			ItemStack returned = p.getInventory().items.stream().filter(s -> s.is(ModItems.BLUEPRINT)).findFirst().orElse(ItemStack.EMPTY);
			helper.assertTrue(!returned.isEmpty(), "build " + (i + 1) + "'s blueprint didn't come back to the player");
			helper.assertTrue(BlueprintItem.data(returned).orElseThrow().placement().equals(Optional.of(handed.get(i))),
				"build " + (i + 1) + "'s blueprint came back placed at " + BlueprintItem.data(returned).orElseThrow().placement());
			if (i < 3) {
				BuildSite next = Builders.activeSite(level, again);
				helper.assertTrue(next != null && next.placement().equals(handed.get(i + 1)), "after cancelling build " + (i + 1) + " the builder took up "
					+ (next == null ? "nothing" : next.placement()) + ", next in line was " + handed.get(i + 1));
				helper.assertTrue(texts(c.seen()).contains(again.getDisplayName().getString() + " is starting on " + hut + " next."),
					"taking up build " + (i + 2) + " said " + texts(c.seen()));
			}
		}
		helper.assertTrue(Builders.activeSite(level, again) == null && Builders.queue(level, again).isEmpty(), "something is left after cancelling all four");
		LOGGER.info("qa queue over a reload: order {} kept, each cancel took up the next", handed);
		helper.succeed();
	}

	// --- The supply chests: within 8 blocks of the table -----------------------------------------------------------

	/**
	 * Guide: "Stock the chests within 8 blocks of the builder's Blueprint Table" (and B261: "a chest one floor up"). Five
	 * chests round the table: 10 planks 8 blocks east, 5 cobblestone 8 blocks west and 8 south (the far corner), 3
	 * cobblestone 4 blocks up, the torch 9 blocks east and the door 9 blocks north. The Book and Quill used on the builder
	 * lists what's still to bring: the chests at 8 and the one upstairs count ("Still to bring: 64 items of 4 kinds.",
	 * "45 × Oak Planks (of 55)", "17 × Cobblestone (of 25)"), the two at 9 don't (the torch and the door unticked).
	 */
	//$ gametest_ticks_batch HUGE '80' '"qaLimitsChestReach"'
	@GameTest(template = HUGE, timeoutTicks = 80, batch = "qaLimitsChestReach")
	public void chestsEightBlocksFromTheTableAreCountedAndNineAreNot(GameTestHelper helper) {
		Leftovers.clear(helper);
		noVillageProtection(helper);
		ServerLevel level = helper.getLevel();
		helper.assertTrue(io.github.jcondedata.aliveworkplace.build.SupplyContainers.RADIUS == 8, "the guide says 8 blocks, the reach is "
			+ io.github.jcondedata.aliveworkplace.build.SupplyContainers.RADIUS);
		BlockPos table = new BlockPos(12, 2, 12);
		helper.setBlock(table, ModBlocks.BLUEPRINT_TABLE);
		record Stock(BlockPos at, ItemStack items) {
		}
		List<Stock> stocks = List.of(
			new Stock(table.east(8), new ItemStack(Items.OAK_PLANKS, 10)),
			new Stock(table.west(8).south(8), new ItemStack(Items.COBBLESTONE, 5)),
			new Stock(table.above(4), new ItemStack(Items.COBBLESTONE, 3)),
			new Stock(table.east(9), new ItemStack(Items.TORCH)),
			new Stock(table.north(9), new ItemStack(Items.OAK_DOOR)));
		for (Stock s : stocks) {
			helper.setBlock(s.at(), Blocks.CHEST);
			((Container) helper.getBlockEntity(s.at())).setItem(0, s.items().copy());
		}
		Villager builder = helper.spawn(EntityType.VILLAGER, table.south().east());
		builder.setNoAi(true);
		BlockPos bench = helper.absolutePos(table);
		Builders.employ(level, builder, bench);
		forgetSitesOf(helper, builder);
		QaStageFiveGameTests.Client c = QaStageSixGameTests.client(helper, GameType.SURVIVAL, QaStageSixGameTests.name("qalC"));
		ServerPlayer p = c.player();
		ItemStack blueprint = placedHut(level, bench, new Vec3i(4, 2, 4));
		p.setItemInHand(InteractionHand.MAIN_HAND, blueprint);
		QaStageSixGameTests.clickVillager(p, builder, c.seen());
		helper.assertTrue(Builders.activeSite(level, builder) != null, "the builder took no build: " + texts(c.seen()));
		p.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.WRITABLE_BOOK));
		c.seen().clear();
		QaStageSixGameTests.clickVillager(p, builder, c.seen());
		ItemStack book = p.getMainHandItem();
		var content = book.get(net.minecraft.core.component.DataComponents.WRITTEN_BOOK_CONTENT);
		helper.assertTrue(content != null, "no list was written: " + book + ", saw " + texts(c.seen()));
		String pages = String.join("\n", content.pages().stream().map(page -> page.raw().getString()).toList());
		for (String line : List.of("Still to bring: 64 items of 4 kinds.", "☐ 45 × Oak Planks (of 55)", "☐ 17 × Cobblestone (of 25)", "☐ 1 × Torch",
			"☐ 1 × Oak Door")) {
			helper.assertTrue(pages.contains(line), "the list lacks \"" + line + "\": " + pages);
		}
		LOGGER.info("qa chest reach: {}", pages.replace('\n', '|'));
		helper.succeed();
	}

	/**
	 * The wiki (builder.md): the chests "within supplyRadius of the bench (and 4 blocks up or down)", from both sides and
	 * below too (lane-b-1025-0937): 10 planks 4 blocks under the table count, 5 cobblestone 5 under and the torch 5 up
	 * don't. The Book and Quill used on the builder: "45 × Oak Planks (of 55)", the torch and the cobblestone unticked.
	 */
	//$ gametest_ticks_batch HUGE '80' '"qaLimitsChestHeight"'
	@GameTest(template = HUGE, timeoutTicks = 80, batch = "qaLimitsChestHeight")
	public void chestsFourBlocksUpOrDownAreCountedAndFiveAreNot(GameTestHelper helper) {
		Leftovers.clear(helper);
		noVillageProtection(helper);
		ServerLevel level = helper.getLevel();
		BlockPos table = new BlockPos(12, 8, 12);
		helper.setBlock(table.below(), Blocks.STONE);
		helper.setBlock(table, ModBlocks.BLUEPRINT_TABLE);
		record Stock(BlockPos at, ItemStack items) {
		}
		List<Stock> stocks = List.of(
			new Stock(table.below(4).east(2), new ItemStack(Items.OAK_PLANKS, 10)),
			new Stock(table.below(5).west(2), new ItemStack(Items.COBBLESTONE, 5)),
			new Stock(table.above(5).north(2), new ItemStack(Items.TORCH)));
		for (Stock s : stocks) {
			helper.setBlock(s.at(), Blocks.CHEST);
			((Container) helper.getBlockEntity(s.at())).setItem(0, s.items().copy());
		}
		Villager builder = helper.spawn(EntityType.VILLAGER, table.south().east());
		builder.setNoAi(true);
		BlockPos bench = helper.absolutePos(table);
		Builders.employ(level, builder, bench);
		forgetSitesOf(helper, builder);
		QaStageFiveGameTests.Client c = QaStageSixGameTests.client(helper, GameType.SURVIVAL, QaStageSixGameTests.name("qalH"));
		ServerPlayer p = c.player();
		ItemStack blueprint = placedHut(level, bench, new Vec3i(4, 2, 4));
		p.setItemInHand(InteractionHand.MAIN_HAND, blueprint);
		QaStageSixGameTests.clickVillager(p, builder, c.seen());
		helper.assertTrue(Builders.activeSite(level, builder) != null, "the builder took no build: " + texts(c.seen()));
		p.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.WRITABLE_BOOK));
		c.seen().clear();
		QaStageSixGameTests.clickVillager(p, builder, c.seen());
		ItemStack book = p.getMainHandItem();
		var content = book.get(net.minecraft.core.component.DataComponents.WRITTEN_BOOK_CONTENT);
		helper.assertTrue(content != null, "no list was written: " + book + ", saw " + texts(c.seen()));
		String pages = String.join("\n", content.pages().stream().map(page -> page.raw().getString()).toList());
		for (String line : List.of("☐ 45 × Oak Planks (of 55)", "☐ 1 × Torch")) {
			helper.assertTrue(pages.contains(line), "the list lacks \"" + line + "\": " + pages);
		}
		// the hut stands two above the table, so its foundation adds cobblestone: the line counts it all, none ticked off
		List<String> cobble = pages.lines().filter(l -> l.contains("× Cobblestone")).toList();
		helper.assertTrue(cobble.size() == 1 && cobble.get(0).startsWith("☐ ") && !cobble.get(0).contains("(of"),
			"the 5 cobblestone five blocks under the table were counted: " + cobble);
		LOGGER.info("qa chest height: {}", pages.replace('\n', '|'));
		helper.succeed();
	}

	/**
	 * en_us builder_died ("Your builder died while building %s. Their tools and the blueprint dropped at %s %s %s."), with
	 * a line behind him (lane-b-1025-0937): a hired builder with one build under way and two lined up is killed. His
	 * owner reads the sentence with the build's name and where he fell; the three blueprints (the one he was on and both
	 * in line) lie on the ground there for the player to pick up, and no site of his is left.
	 */
	//$ gametest_ticks_batch AREA '80' '"qaLimitsBuilderDies"'
	@GameTest(template = AREA, timeoutTicks = 80, batch = "qaLimitsBuilderDies")
	public void aBuilderWhoDiesDropsEveryBlueprintAndHisOwnerIsTold(GameTestHelper helper) {
		Leftovers.clear(helper);
		noVillageProtection(helper);
		ServerLevel level = helper.getLevel();
		helper.setBlock(TABLE, ModBlocks.BLUEPRINT_TABLE);
		Villager builder = helper.spawn(EntityType.VILLAGER, VILLAGER);
		builder.setNoAi(true);
		BlockPos bench = helper.absolutePos(TABLE);
		Builders.employ(level, builder, bench);
		forgetSitesOf(helper, builder);
		QaStageFiveGameTests.Client c = QaStageSixGameTests.client(helper, GameType.SURVIVAL, QaStageSixGameTests.name("qalD"));
		ServerPlayer p = c.player();
		for (int i = 0; i < 3; i++) {
			p.setItemInHand(InteractionHand.MAIN_HAND, placedHut(level, bench, new Vec3i(6 + 6 * i, 0, 6)));
			QaStageSixGameTests.clickVillager(p, builder, c.seen());
		}
		helper.assertTrue(Builders.activeSite(level, builder) != null && Builders.queue(level, builder).size() == 2,
			"not one build and two in line: " + texts(c.seen()));
		BlockPos at = builder.blockPosition();
		java.util.UUID id = builder.getUUID();
		c.seen().clear();
		builder.kill();
		String expected = "Your builder died while building " + Blueprints.displayName(TEST_HUT).getString() + ". Their tools and the blueprint dropped at "
			+ at.getX() + " " + at.getY() + " " + at.getZ() + ".";
		helper.assertTrue(texts(c.seen()).contains(expected), "the owner was told " + texts(c.seen()) + ", expected \"" + expected + "\"");
		int blueprints = 0;
		for (net.minecraft.world.entity.item.ItemEntity item : level.getEntitiesOfClass(net.minecraft.world.entity.item.ItemEntity.class,
			new net.minecraft.world.phys.AABB(at).inflate(4))) {
			blueprints += item.getItem().is(ModItems.BLUEPRINT) ? item.getItem().getCount() : 0;
		}
		helper.assertTrue(blueprints == 3, blueprints + " blueprint(s) dropped where he fell, not the 3 he held");
		for (BuildSite site : BuildSiteManager.get(level).all()) {
			helper.assertFalse(id.equals(site.builder()), "a site of the dead builder is left: " + site.structure() + " queued=" + site.isQueued());
		}
		helper.succeed();
	}

	/**
	 * en_us assign.overlaps and assign.wrong_dimension (lane-b-1025-0937), through the player's clicks: a builder busy on a
	 * placed hut is handed a second hut placed one block over (overlapping it): "That spot overlaps another build in
	 * progress (Test Hut).", the blueprint kept and nothing lined up; a hut placed in the Nether: "That blueprint is placed
	 * in another dimension.", kept; then a hut clear of the first is lined up as usual.
	 */
	//$ gametest_ticks_batch AREA '80' '"qaLimitsOverlap"'
	@GameTest(template = AREA, timeoutTicks = 80, batch = "qaLimitsOverlap")
	public void anOverlappingOrOtherDimensionSpotIsRefusedAndKept(GameTestHelper helper) {
		Leftovers.clear(helper);
		noVillageProtection(helper);
		ServerLevel level = helper.getLevel();
		helper.setBlock(TABLE, ModBlocks.BLUEPRINT_TABLE);
		Villager builder = helper.spawn(EntityType.VILLAGER, VILLAGER);
		builder.setNoAi(true);
		BlockPos bench = helper.absolutePos(TABLE);
		Builders.employ(level, builder, bench);
		forgetSitesOf(helper, builder);
		QaStageFiveGameTests.Client c = QaStageSixGameTests.client(helper, GameType.SURVIVAL, QaStageSixGameTests.name("qalV"));
		ServerPlayer p = c.player();
		p.setItemInHand(InteractionHand.MAIN_HAND, placedHut(level, bench, new Vec3i(6, 0, 6)));
		QaStageSixGameTests.clickVillager(p, builder, c.seen());
		helper.assertTrue(Builders.activeSite(level, builder) != null, "the first hut started nothing: " + texts(c.seen()));
		String hut = Blueprints.displayName(TEST_HUT).getString();
		// one block over: overlaps the hut under way
		p.setItemInHand(InteractionHand.MAIN_HAND, placedHut(level, bench, new Vec3i(7, 0, 6)));
		c.seen().clear();
		QaStageSixGameTests.clickVillager(p, builder, c.seen());
		helper.assertTrue(texts(c.seen()).contains("That spot overlaps another build in progress (" + hut + ")."), "the overlapping hut said " + texts(c.seen()));
		helper.assertTrue(p.getMainHandItem().is(ModItems.BLUEPRINT), "the refused overlapping blueprint wasn't kept: " + p.getMainHandItem());
		helper.assertTrue(Builders.queue(level, builder).isEmpty(), "the overlapping hut was lined up");
		// placed in the Nether
		ItemStack nether = placedHut(level, bench, new Vec3i(20, 0, 6));
		BlueprintData.Placement there = BlueprintItem.data(nether).orElseThrow().placement().orElseThrow();
		nether.set(ModComponents.BLUEPRINT, BlueprintItem.data(nether).orElseThrow().withPlacement(Optional.of(new BlueprintData.Placement(
			net.minecraft.world.level.Level.NETHER.location(), there.origin(), there.rotation(), there.mirror()))));
		p.setItemInHand(InteractionHand.MAIN_HAND, nether);
		c.seen().clear();
		QaStageSixGameTests.clickVillager(p, builder, c.seen());
		helper.assertTrue(texts(c.seen()).contains("That blueprint is placed in another dimension."), "the Nether hut said " + texts(c.seen()));
		helper.assertTrue(p.getMainHandItem().is(ModItems.BLUEPRINT), "the refused Nether blueprint wasn't kept: " + p.getMainHandItem());
		helper.assertTrue(Builders.queue(level, builder).isEmpty(), "the Nether hut was lined up");
		// clear of the first: lined up
		p.setItemInHand(InteractionHand.MAIN_HAND, placedHut(level, bench, new Vec3i(20, 0, 6)));
		QaStageSixGameTests.clickVillager(p, builder, c.seen());
		helper.assertTrue(Builders.queue(level, builder).size() == 1, "a hut clear of the first wasn't lined up: " + texts(c.seen()));
		helper.succeed();
	}

	// --- How far from the table ------------------------------------------------------------------------------------

	/**
	 * en_us assign.too_far ("That spot is %s blocks from the builder's Blueprint Table — keep it within %s."): a placed
	 * blueprint whose middle is exactly 48 blocks from the builder's table is taken; one 49 blocks off is refused with
	 * "That spot is 49 blocks from the builder's Blueprint Table — keep it within 48." and stays in hand.
	 */
	//$ gametest_ticks_batch AREA '60' '"qaLimitsReach"'
	@GameTest(template = AREA, timeoutTicks = 60, batch = "qaLimitsReach")
	public void aSpotAtTheTablesReachIsTakenAndOneBlockFurtherIsRefused(GameTestHelper helper) {
		Leftovers.clear(helper);
		noVillageProtection(helper);
		ServerLevel level = helper.getLevel();
		helper.setBlock(TABLE, ModBlocks.BLUEPRINT_TABLE);
		Villager builder = helper.spawn(EntityType.VILLAGER, VILLAGER);
		builder.setNoAi(true);
		BlockPos bench = helper.absolutePos(TABLE);
		Builders.employ(level, builder, bench);
		forgetSitesOf(helper, builder);
		int reach = Builders.MAX_SITE_DISTANCE;
		QaStageFiveGameTests.Client c = QaStageSixGameTests.client(helper, GameType.SURVIVAL, QaStageSixGameTests.name("qalR"));
		ServerPlayer p = c.player();

		ItemStack far = placedHut(level, bench, new Vec3i(0, 0, reach + 1));
		helper.assertTrue(distance(level, far, bench) == reach + 1, "the far spot is " + distance(level, far, bench));
		p.setItemInHand(InteractionHand.MAIN_HAND, far);
		QaStageSixGameTests.clickVillager(p, builder, c.seen());
		helper.assertTrue(Builders.activeSite(level, builder) == null, "a spot " + (reach + 1) + " blocks off was taken");
		helper.assertTrue(texts(c.seen()).contains("That spot is " + (reach + 1) + " blocks from the builder's Blueprint Table — keep it within " + reach + "."),
			"the far spot said " + texts(c.seen()));
		helper.assertTrue(p.getMainHandItem().is(ModItems.BLUEPRINT), "the refused blueprint went: " + p.getMainHandItem());

		ItemStack edge = placedHut(level, bench, new Vec3i(reach, 0, 0));
		helper.assertTrue(distance(level, edge, bench) == reach, "the edge spot is " + distance(level, edge, bench));
		p.setItemInHand(InteractionHand.MAIN_HAND, edge);
		c.seen().clear();
		QaStageSixGameTests.clickVillager(p, builder, c.seen());
		helper.assertTrue(Builders.activeSite(level, builder) != null, "a spot exactly " + reach + " blocks off was refused: " + texts(c.seen()));
		LOGGER.info("qa reach: {} taken, {} refused", reach, reach + 1);
		helper.succeed();
	}

	// --- The crew: three helpers, not four -------------------------------------------------------------------------

	/**
	 * Guide: "Up to three builders with nothing to do join a build near their table, so a crew of four ..." Four idle
	 * builders by a hut under way: three of them join (at some point three help at once) and never four, and the hut is
	 * finished.
	 */
	//$ gametest_ticks_batch AREA '3000' '"qaLimitsCrew"'
	@GameTest(template = AREA, timeoutTicks = 3000, batch = "qaLimitsCrew")
	public void fourIdleBuildersMakeACrewOfThreeHelpersNotFour(GameTestHelper helper) {
		Leftovers.clear(helper);
		noVillageProtection(helper);
		ServerLevel level = helper.getLevel();
		helper.setDayTime(2000);
		var rules = level.getGameRules();
		boolean help = rules.getRule(ModGameRules.BUILDERS_HELP).get();
		int delay = rules.getRule(ModGameRules.BUILD_DELAY).get();
		rules.getRule(ModGameRules.BUILDERS_HELP).set(true, level.getServer());
		rules.getRule(ModGameRules.BUILD_DELAY).set(2, level.getServer());
		Leftovers.after(helper, () -> {
			rules.getRule(ModGameRules.BUILDERS_HELP).set(help, level.getServer());
			rules.getRule(ModGameRules.BUILD_DELAY).set(delay, level.getServer());
		});
		BuildSiteManager sites = BuildSiteManager.get(level);
		for (BuildSite other : new ArrayList<>(sites.all())) {
			if (other.bench() != null && other.bench().closerThan(helper.absolutePos(TABLE), 3 * Builders.MAX_SITE_DISTANCE)) {
				sites.remove(other.id());
			}
		}
		helper.setBlock(TABLE, ModBlocks.BUILDERS_BENCH);
		helper.setBlock(CHEST, Blocks.CHEST);
		Container chest = helper.getBlockEntity(CHEST);
		chest.setItem(0, new ItemStack(Items.COBBLESTONE, 25));
		chest.setItem(1, new ItemStack(Items.OAK_PLANKS, 55));
		chest.setItem(2, new ItemStack(Items.OAK_DOOR));
		chest.setItem(3, new ItemStack(Items.TORCH));
		Villager lead = helper.spawn(EntityType.VILLAGER, VILLAGER);
		Builders.employ(level, lead, helper.absolutePos(TABLE));
		forgetSitesOf(helper, lead);
		List<Villager> mates = new ArrayList<>();
		for (BlockPos benchRel : List.of(new BlockPos(13, 2, 2), new BlockPos(15, 2, 2), new BlockPos(2, 2, 13), new BlockPos(2, 2, 15))) {
			helper.setBlock(benchRel, ModBlocks.BUILDERS_BENCH);
			Villager mate = helper.spawn(EntityType.VILLAGER, benchRel.south().east());
			Builders.employ(level, mate, helper.absolutePos(benchRel));
			mates.add(mate);
		}
		BuildSite site = Builders.start(level, lead, null, TEST_HUT,
			new BlueprintData.Placement(level.dimension().location(), helper.absolutePos(HUT), Rotation.NONE, Mirror.NONE));
		helper.assertTrue(site != null, "the hut didn't start");
		AtomicInteger most = new AtomicInteger();
		helper.onEachTick(() -> {
			int now = (int) mates.stream().filter(Builders::isHelping).count();
			if (now > most.get()) {
				most.set(now);
				LOGGER.info("qa crew: {} helping at tick {}", now, helper.getTick());
			}
			helper.assertTrue(now <= Builders.MAX_HELPERS, now + " idle builders help at once (at most " + Builders.MAX_HELPERS + ")");
		});
		helper.succeedWhen(() -> {
			helper.assertTrue(sites.get(site.id()) == null, "the hut isn't finished: stage " + site.stage() + ", missing " + site.missing()
				+ ", most helping " + most.get());
			helper.assertTrue(site.plan(level).unfinished(level).isEmpty(), "blocks wrong after the build: " + site.plan(level).unfinished(level).size());
			helper.assertTrue(most.get() == 3, "at most " + most.get() + " of the four idle builders helped at once (the guide says up to three)");
			helper.assertTrue(Builders.MAX_HELPERS == 3, "the guide says up to three helpers, the code has " + Builders.MAX_HELPERS);
		});
	}
}
