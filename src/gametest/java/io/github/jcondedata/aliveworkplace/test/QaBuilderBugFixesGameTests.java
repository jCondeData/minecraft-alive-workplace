package io.github.jcondedata.aliveworkplace.test;

import io.github.jcondedata.aliveworkplace.blueprint.BlueprintData;
import io.github.jcondedata.aliveworkplace.blueprint.BlueprintItem;
import io.github.jcondedata.aliveworkplace.blueprint.ScanToolItem;
import io.github.jcondedata.aliveworkplace.build.BuildSite;
import io.github.jcondedata.aliveworkplace.build.Builders;
import io.github.jcondedata.aliveworkplace.farm.FieldData;
import io.github.jcondedata.aliveworkplace.registry.ModBlocks;
import io.github.jcondedata.aliveworkplace.registry.ModGameRules;
import io.github.jcondedata.aliveworkplace.registry.ModItems;
import io.github.jcondedata.aliveworkplace.store.StockOrders;
import io.github.jcondedata.aliveworkplace.store.StorehouseBoard;
import io.github.jcondedata.aliveworkplace.table.TablePayloads;
import io.github.jcondedata.aliveworkplace.work.ChoiceMenu;
import io.github.jcondedata.aliveworkplace.work.Village;
import io.github.jcondedata.aliveworkplace.work.Words;
import java.util.List;
import java.util.Optional;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Vec3i;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.locale.Language;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.resources.ResourceLocation;
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
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplateManager;

/**
 * QA (lane-b-1024-1537) of the landed builder and Storehouse fixes B337, B351, B355, B365, B366, B369 and B374, from
 * their ROADMAP text, for the cases the fixers' own tests leave out: a Book and Quill on a Table no builder works at, the
 * Storehouse reach from both sides with fractional distances and another village size, the material list from either
 * hand order with its singular and "everything" lines, the Scan Tool from the off hand on a barrel and a sneak-click,
 * and the Storehouse board's orders page from the off hand through to the order kept.
 */
public class QaBuilderBugFixesGameTests implements FabricGameTest {
	private static final String AREA = "aliveworkplace_test:build_area";
	private static final String HUGE = QaBuilderStockGameTests.HUGE;
	private static final ResourceLocation TEST_HUT = ResourceLocation.fromNamespaceAndPath("aliveworkplace_test", "test_hut");
	private static final Vec3i HUT_SIZE = new Vec3i(5, 4, 5);
	private static final BlockPos TABLE = new BlockPos(2, 2, 2);

	/** Village protection isn't what these tests are about (see QaBuilderClicksGameTests.noVillageProtection). */
	private static void noVillageProtection(GameTestHelper helper) {
		boolean enabled = io.github.jcondedata.aliveworkplace.hall.VillageProtection.ENABLED;
		io.github.jcondedata.aliveworkplace.hall.VillageProtection.ENABLED = false;
		Leftovers.after(helper, () -> io.github.jcondedata.aliveworkplace.hall.VillageProtection.ENABLED = enabled);
	}

	static String english(String key, Object... args) {
		return String.format(Language.getInstance().getOrDefault(key), args);
	}

	static List<String> texts(List<Component> seen) {
		return seen.stream().map(Component::getString).toList();
	}

	// --- B337: a Table no builder works at ----------------------------------------------------------------------------

	/**
	 * B337: "A Table no builder works at opens as usual". A Book and Quill used on a Blueprint Table nobody builds from,
	 * from the main hand and then from the off hand: the table's screen opens (its library is sent), there is no "No build
	 * is under way from this bench" line and no list, and the Book and Quill stays as it was.
	 */
	//$ gametest_ticks_batch AREA '60' '"qaFixLoneTable"'
	@GameTest(template = AREA, timeoutTicks = 60, batch = "qaFixLoneTable")
	public void aBookAndQuillOnATableNoBuilderWorksAtOpensItAsUsual(GameTestHelper helper) {
		Leftovers.clear(helper);
		noVillageProtection(helper);
		helper.setBlock(TABLE, ModBlocks.BLUEPRINT_TABLE);
		QaStageFiveGameTests.Client c = QaStageSixGameTests.client(helper, GameType.SURVIVAL, QaStageSixGameTests.name("qafxL"));
		ServerPlayer p = c.player();
		String noBuild = english("message.aliveworkplace.site_list.no_build_at_bench");
		for (InteractionHand hand : InteractionHand.values()) {
			p.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
			p.setItemInHand(InteractionHand.OFF_HAND, ItemStack.EMPTY);
			p.setItemInHand(hand, new ItemStack(Items.WRITABLE_BOOK));
			c.seen().clear();
			c.sent();
			QaStageFourGameTests.press(helper, p, TABLE, Direction.NORTH);
			List<TablePayloads.Open> open = c.sent(TablePayloads.Open.class);
			helper.assertFalse(texts(c.seen()).contains(noBuild), hand + ": a Table no builder works at answered " + texts(c.seen()));
			helper.assertFalse(QaStageFourGameTests.keys(c.seen()).stream().anyMatch(k -> k.startsWith("message.aliveworkplace.site_list")),
				hand + ": a site-list line at a lone Table: " + QaStageFourGameTests.keys(c.seen()));
			helper.assertTrue(open.size() == 1 && open.get(0).table().equals(helper.absolutePos(TABLE)),
				hand + ": the lone Table didn't open as usual with a Book and Quill: sent " + open);
			helper.assertTrue(p.getItemInHand(hand).is(Items.WRITABLE_BOOK) && p.getItemInHand(hand).getCount() == 1
				&& p.getInventory().countItem(Items.WRITTEN_BOOK) == 0, hand + ": the book changed: " + p.getItemInHand(hand));
		}
		helper.succeed();
	}

	// --- B355: the Storehouse's distance against the village's reach -------------------------------------------------

	/** The builder's status with a Storehouse at {@code storehouse} in a {@code radius}-block village; the hut's planks in the table's chest. */
	private static Component status(GameTestHelper helper, BlockPos storehouse, int radius, QaBuilderStockGameTests.Builder[] out) {
		ServerLevel level = QaBuilderStockGameTests.setup(helper, storehouse, 25, 1);
		Leftovers.village(helper, radius);
		QaBuilderStockGameTests.fill(helper.getBlockEntity(QaBuilderStockGameTests.TABLE_CHEST), new ItemStack(Items.OAK_PLANKS, 55), new ItemStack(Items.TORCH));
		QaBuilderStockGameTests.Builder b = QaBuilderStockGameTests.builder(helper, QaBuilderStockGameTests.HUT);
		Village.forget(b.villager());
		out[0] = b;
		return Builders.statusText(level, b.site(), b.villager());
	}

	/** The far line, rendered, must read exactly as en_us.json with the distance rounded up and the village's reach. */
	private static void assertFarLine(GameTestHelper helper, Component status, BlockPos storehouse, int shown, int reach) {
		TranslatableContents far = QaBuilderStockGameTests.find(status, "message.aliveworkplace.status.storehouse_far");
		helper.assertTrue(far != null, "the status doesn't say the Storehouse is too far: " + status.getString());
		BlockPos at = helper.absolutePos(storehouse);
		String rendered = Component.translatable(far.getKey(), far.getArgs()).getString();
		String expected = english("message.aliveworkplace.status.storehouse_far", at.getX() + " " + at.getY() + " " + at.getZ(), shown, reach);
		helper.assertTrue(rendered.equals(expected), "the far line reads \"" + rendered + "\", expected \"" + expected + "\"");
		helper.assertTrue(shown > reach, "test setup: " + shown + " isn't past " + reach);
	}

	/**
	 * B355 from inside: a Storehouse 19.92 blocks from the table (19 across, 6 down) in a 20-block village is within the
	 * reach, though it rounds up to 20: it is counted (the hut lacks nothing) and the status doesn't call it too far.
	 */
	//$ gametest_batch HUGE '"qaFixReachInside"'
	@GameTest(template = HUGE, batch = "qaFixReachInside")
	public void aStorehouseAFractionInsideTheReachIsCounted(GameTestHelper helper) {
		QaBuilderStockGameTests.Builder[] b = new QaBuilderStockGameTests.Builder[1];
		Component status = status(helper, new BlockPos(21, 2, 8), 20, b);
		helper.assertTrue(b[0].site().missing().isEmpty(), "a Storehouse 19.92 blocks off in a 20-block village wasn't counted: needs " + b[0].site().missing());
		helper.assertTrue(QaBuilderStockGameTests.find(status, "message.aliveworkplace.status.storehouse_far") == null,
			"a Storehouse inside the reach is called too far: " + status.getString());
		helper.succeed();
	}

	/**
	 * B355 from outside, a hair past: 20.02 blocks (20 across, 1 down) in a 20-block village isn't counted, and the line
	 * reads "Not counting the Storehouse at x y z: it is 21 blocks from his table, and a village reaches 20".
	 */
	//$ gametest_batch HUGE '"qaFixReachHair"'
	@GameTest(template = HUGE, batch = "qaFixReachHair")
	public void aStorehouseAHairPastTheReachReadsOneBlockPast(GameTestHelper helper) {
		BlockPos storehouse = new BlockPos(22, 2, 3);
		QaBuilderStockGameTests.Builder[] b = new QaBuilderStockGameTests.Builder[1];
		Component status = status(helper, storehouse, 20, b);
		helper.assertTrue(!b[0].site().missing().isEmpty(), "a Storehouse 20.02 blocks off was counted in a 20-block village");
		assertFarLine(helper, status, storehouse, 21, 20);
		helper.succeed();
	}

	/**
	 * B355 in another village size, a whole number past: 26 blocks straight along x in a 25-block village reads "it is 26
	 * blocks from his table, and a village reaches 25" (a whole distance isn't rounded up past itself).
	 */
	//$ gametest_batch HUGE '"qaFixReach25"'
	@GameTest(template = HUGE, batch = "qaFixReach25")
	public void aStorehouseAWholeBlockPastAQuarterHundredReach(GameTestHelper helper) {
		BlockPos storehouse = new BlockPos(28, 2, 2);
		QaBuilderStockGameTests.Builder[] b = new QaBuilderStockGameTests.Builder[1];
		Component status = status(helper, storehouse, 25, b);
		helper.assertTrue(!b[0].site().missing().isEmpty(), "a Storehouse 26 blocks off was counted in a 25-block village");
		assertFarLine(helper, status, storehouse, 26, 25);
		helper.succeed();
	}

	// --- B365 + B366: the Blueprint's material list from either hand ------------------------------------------------

	/** A one-off blueprint made of what stands at {@code from} ({@code size}), only in this server's memory. */
	private static ResourceLocation template(GameTestHelper helper, String name, BlockPos from, Vec3i size) {
		ServerLevel level = helper.getLevel();
		StructureTemplateManager manager = level.getServer().getStructureManager();
		ResourceLocation id = ResourceLocation.fromNamespaceAndPath("aliveworkplace_test", name);
		StructureTemplate template = manager.getOrCreate(id);
		template.fillFromWorld(level, helper.absolutePos(from), size, false, Blocks.STRUCTURE_VOID);
		Leftovers.after(helper, () -> manager.remove(id));
		return id;
	}

	/** Right-clicks the air with the blueprint in {@code blueprintHand} and a Book and Quill in the other; the written book. */
	private static ItemStack writeList(GameTestHelper helper, ServerPlayer player, ItemStack blueprint, InteractionHand blueprintHand) {
		InteractionHand bookHand = blueprintHand == InteractionHand.MAIN_HAND ? InteractionHand.OFF_HAND : InteractionHand.MAIN_HAND;
		player.setItemInHand(blueprintHand, blueprint);
		player.setItemInHand(bookHand, new ItemStack(Items.WRITABLE_BOOK));
		QaFurnishedGameTests.rightClickAir(player);
		ItemStack book = player.getItemInHand(bookHand);
		helper.assertTrue(book.is(Items.WRITTEN_BOOK), "blueprint in the " + blueprintHand + ": no list written, the book's hand holds " + book);
		helper.assertTrue(player.getItemInHand(blueprintHand).is(ModItems.BLUEPRINT), "the blueprint went from the " + blueprintHand);
		return book;
	}

	/**
	 * B365 + B366: an unplaced Blueprint's "everything" line, from both hand orders. The test hut (82 items of 4 kinds,
	 * MaterialListGameTests), a one-plank build ("It needs 1 item of 1 kind.") and a nine-plank floor ("It needs 9 items of
	 * 1 kind."), each with the blueprint in the off hand and then in the main hand: the same first line both ways.
	 */
	//$ gametest_ticks_batch AREA '60' '"qaFixEverything"'
	@GameTest(template = AREA, timeoutTicks = 60, batch = "qaFixEverything")
	public void theEverythingLineCountsInTheSingularFromEitherHand(GameTestHelper helper) {
		Leftovers.clear(helper);
		QaFurnishedGameTests.resetAfter(helper);
		helper.setBlock(new BlockPos(1, 2, 7), Blocks.OAK_PLANKS);
		for (int x = 4; x < 7; x++) {
			for (int z = 6; z < 9; z++) {
				helper.setBlock(new BlockPos(x, 2, z), Blocks.OAK_PLANKS);
			}
		}
		ResourceLocation one = template(helper, "qa_fix_one_plank", new BlockPos(1, 2, 7), new Vec3i(1, 1, 1));
		ResourceLocation floor = template(helper, "qa_fix_plank_floor", new BlockPos(4, 2, 6), new Vec3i(3, 1, 3));
		ServerPlayer player = (ServerPlayer) helper.makeMockServerPlayerInLevel();
		record Case(ItemStack blueprint, String line) {
		}
		List<Case> cases = List.of(
			new Case(BlueprintItem.create(TEST_HUT, HUT_SIZE), english("book.aliveworkplace.material_list.everything", 82, 4)),
			new Case(BlueprintItem.create(one, new Vec3i(1, 1, 1)), english("book.aliveworkplace.material_list.everything.one", 1, 1)),
			new Case(BlueprintItem.create(floor, new Vec3i(3, 1, 3)), english("book.aliveworkplace.material_list.everything.one_kind", 9, 1)));
		helper.assertTrue(cases.get(1).line().equals("It needs 1 item of 1 kind.") && cases.get(2).line().equals("It needs 9 items of 1 kind.")
			&& cases.get(0).line().equals("It needs 82 items of 4 kinds."), "en_us.json's everything lines: " + cases.stream().map(Case::line).toList());
		for (Case c : cases) {
			for (InteractionHand hand : List.of(InteractionHand.OFF_HAND, InteractionHand.MAIN_HAND)) {
				String first = QaFurnishedGameTests.page(writeList(helper, player, c.blueprint().copy(), hand), 0);
				helper.assertTrue(first != null && first.contains(c.line()) && !first.contains("1 items") && !first.contains("1 kinds"),
					"blueprint in the " + hand + ": the first page reads \"" + (first == null ? null : first.replace("\n", " ")) + "\", expected \"" + c.line() + "\"");
			}
		}
		helper.succeed();
	}

	/**
	 * B365 + B366 together: a placed nook whose Table's chest holds all but the chair, the blueprint in the OFF hand and
	 * the Book and Quill in the main hand: the list is written into the main hand and reads "Still to bring: 1 item of 1
	 * kind." (the fixers' tests check the singular only with the blueprint in the main hand).
	 */
	//$ gametest_ticks_batch AREA '60' '"qaFixOffHandOne"'
	@GameTest(template = AREA, timeoutTicks = 60, batch = "qaFixOffHandOne")
	public void theOffHandBlueprintsListReadsInTheSingular(GameTestHelper helper) {
		Leftovers.clear(helper);
		QaFurnishedGameTests.resetAfter(helper);
		ServerLevel level = helper.getLevel();
		level.getGameRules().getRule(ModGameRules.FREE_MATERIALS).set(false, level.getServer());
		helper.setBlock(QaFurnishedGameTests.BENCH, ModBlocks.BLUEPRINT_TABLE);
		helper.setBlock(QaFurnishedGameTests.CHEST, Blocks.CHEST);
		((Container) helper.getBlockEntity(QaFurnishedGameTests.CHEST)).setItem(0, new ItemStack(Items.OAK_PLANKS, 9));
		io.github.jcondedata.aliveworkplace.furnish.Furnishings.MODS = List.of(QaFurnishedGameTests.PIECES);
		ServerPlayer player = (ServerPlayer) helper.makeMockServerPlayerInLevel();
		player.moveTo(helper.absoluteVec(new net.minecraft.world.phys.Vec3(9.5, 2, 4.5)), 0, 0);
		player.setItemInHand(InteractionHand.MAIN_HAND, BlueprintItem.create(QaFurnishedGameTests.NOOK, QaFurnishedGameTests.NOOK_SIZE));
		QaFurnishedGameTests.rightClickGround(player, helper.absolutePos(new BlockPos(9, 1, 9)));
		ItemStack placed = player.getMainHandItem().copy();
		Optional<BlueprintData> data = BlueprintItem.data(placed);
		helper.assertTrue(data.isPresent() && data.get().placement().isPresent(), "the blueprint wasn't placed by the click");
		String expected = english("book.aliveworkplace.material_list.to_bring.one", 1, 1);
		helper.assertTrue(expected.equals("Still to bring: 1 item of 1 kind."), "en_us.json's singular: " + expected);
		helper.runAfterDelay(2, () -> {
			String first = QaFurnishedGameTests.page(writeList(helper, player, placed, InteractionHand.OFF_HAND), 0);
			helper.assertTrue(first != null && first.contains(expected) && !first.contains("1 items"),
				"blueprint off hand, book main: the first page reads " + (first == null ? null : first.replace("\n", " ")));
			helper.succeed();
		});
	}

	/** A builder at his Blueprint Table building the test hut, his chest holding everything but the torch; a player by it. */
	record OneShort(Villager builder, QaStageFiveGameTests.Client client) {
	}

	static OneShort oneThingShort(GameTestHelper helper, String name) {
		Leftovers.clear(helper);
		noVillageProtection(helper);
		ServerLevel level = helper.getLevel();
		level.getGameRules().getRule(ModGameRules.FREE_MATERIALS).set(false, level.getServer());
		helper.setBlock(TABLE, ModBlocks.BLUEPRINT_TABLE);
		BlockPos chestPos = new BlockPos(2, 2, 4);
		helper.setBlock(chestPos, Blocks.CHEST);
		QaBuilderStockGameTests.fill(helper.getBlockEntity(chestPos), new ItemStack(Items.COBBLESTONE, 25), new ItemStack(Items.OAK_PLANKS, 55),
			new ItemStack(Items.OAK_DOOR));
		Villager builder = helper.spawn(EntityType.VILLAGER, new BlockPos(3, 2, 3));
		builder.setNoAi(true);
		Builders.employ(level, builder, helper.absolutePos(TABLE));
		BuildSite site = Builders.start(level, builder, null, TEST_HUT,
			new BlueprintData.Placement(level.dimension().location(), helper.absolutePos(new BlockPos(6, 2, 6)), Rotation.NONE, Mirror.NONE));
		helper.assertTrue(site != null, "the test hut didn't start");
		return new OneShort(builder, QaStageSixGameTests.client(helper, GameType.SURVIVAL, QaStageSixGameTests.name(name)));
	}

	/**
	 * B366's singular for the builder's own list: a builder at his Blueprint Table building the test hut, his chest
	 * holding everything but the torch. The Book and Quill on his table reads "Still to bring: 1 item of 1 kind."
	 * (from the off hand: QaBuilderBugFixesBugTests).
	 */
	//$ gametest_ticks_batch AREA '60' '"qaFixSiteOne"'
	@GameTest(template = AREA, timeoutTicks = 60, batch = "qaFixSiteOne")
	public void theBuildersTableListWithOneThingToBringReadsInTheSingular(GameTestHelper helper) {
		assertTableListSingular(helper, oneThingShort(helper, "qafxO"), InteractionHand.MAIN_HAND);
		helper.succeed();
	}

	static void assertTableListSingular(GameTestHelper helper, OneShort s, InteractionHand hand) {
		ServerPlayer p = s.client().player();
		p.setItemInHand(hand, new ItemStack(Items.WRITABLE_BOOK));
		QaStageFourGameTests.press(helper, p, TABLE, Direction.NORTH);
		String page = QaFurnishedGameTests.page(p.getItemInHand(hand), 0);
		helper.assertTrue(page != null && page.contains(english("book.aliveworkplace.material_list.to_bring.one", 1, 1)),
			"Book and Quill in the " + hand + " on the builder's Table: " + (page == null ? p.getItemInHand(hand) + ", saw " + texts(s.client().seen())
				+ ", sent " + s.client().sent().stream().map(x -> x.getClass().getSimpleName()).toList() : page.replace("\n", " ")));
	}

	// --- B374: the Scan Tool from the off hand ----------------------------------------------------------------------

	/**
	 * B374 (and B335's "the click marks it and the block stays shut"): with the Scan Tool in the off hand and the main
	 * hand empty, a chest marks the first corner and a barrel the opposite one: "Marked 3 × 1 × 3. …", neither opens.
	 * Then a sneak-right-click on the chest, still from the off hand, starts over ("Scan Tool cleared: …") without
	 * opening it.
	 */
	//$ gametest_ticks_batch AREA '60' '"qaFixScanBarrel"'
	@GameTest(template = AREA, timeoutTicks = 60, batch = "qaFixScanBarrel")
	public void theOffHandScanToolMarksAChestThenABarrelAndStartsOver(GameTestHelper helper) {
		Leftovers.clear(helper);
		noVillageProtection(helper);
		BlockPos chest = new BlockPos(2, 2, 3);
		BlockPos barrel = new BlockPos(4, 2, 5);
		helper.setBlock(chest, Blocks.CHEST);
		helper.setBlock(barrel, Blocks.BARREL);
		QaStageFiveGameTests.Client c = QaStageSixGameTests.client(helper, GameType.SURVIVAL, QaStageSixGameTests.name("qafxS"));
		ServerPlayer p = c.player();
		p.setItemInHand(InteractionHand.OFF_HAND, new ItemStack(ModItems.SCAN_TOOL));
		QaStageFourGameTests.press(helper, p, chest, Direction.NORTH);
		helper.assertTrue(p.containerMenu == p.inventoryMenu, "the chest opened: " + p.containerMenu.getClass().getSimpleName());
		QaStageFourGameTests.press(helper, p, barrel, Direction.NORTH);
		helper.assertTrue(p.containerMenu == p.inventoryMenu, "the barrel opened: " + p.containerMenu.getClass().getSimpleName());
		FieldData data = ScanToolItem.data(p.getOffhandItem());
		helper.assertTrue(data.first().equals(Optional.of(helper.absolutePos(chest))) && data.second().equals(Optional.of(helper.absolutePos(barrel))),
			"the off-hand Scan Tool's corners: " + data + ", saw " + texts(c.seen()));
		String marked = english("message.aliveworkplace.scan.marked", 3, 1, 3);
		helper.assertTrue(texts(c.seen()).contains(marked), "expected \"" + marked + "\", saw " + texts(c.seen()));
		helper.assertTrue(p.getMainHandItem().isEmpty(), "the main hand got something: " + p.getMainHandItem());

		c.seen().clear();
		p.setShiftKeyDown(true);
		QaStageFourGameTests.press(helper, p, chest, Direction.NORTH);
		p.setShiftKeyDown(false);
		boolean opened = p.containerMenu != p.inventoryMenu;
		p.closeContainer();
		helper.assertTrue(!opened && ScanToolItem.data(p.getOffhandItem()).first().isEmpty()
				&& texts(c.seen()).contains(english("message.aliveworkplace.scan.reset")),
			"sneak-click from the off hand on the chest: opened " + opened + ", data " + ScanToolItem.data(p.getOffhandItem()) + ", saw " + texts(c.seen()));
		helper.succeed();
	}

	/**
	 * B374's rule is "the main hand empty": with something else in the main hand (a stick) the main hand's click is the
	 * vanilla one, so the chest opens and the off-hand Scan Tool marks nothing; it never does both.
	 */
	//$ gametest_ticks_batch AREA '60' '"qaFixScanStick"'
	@GameTest(template = AREA, timeoutTicks = 60, batch = "qaFixScanStick")
	public void aStickInTheMainHandLeavesTheOffHandScanToolAlone(GameTestHelper helper) {
		Leftovers.clear(helper);
		noVillageProtection(helper);
		BlockPos chest = new BlockPos(2, 2, 3);
		helper.setBlock(chest, Blocks.CHEST);
		QaStageFiveGameTests.Client c = QaStageSixGameTests.client(helper, GameType.SURVIVAL, QaStageSixGameTests.name("qafxT"));
		ServerPlayer p = c.player();
		p.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.STICK));
		p.setItemInHand(InteractionHand.OFF_HAND, new ItemStack(ModItems.SCAN_TOOL));
		QaStageFourGameTests.press(helper, p, chest, Direction.NORTH);
		boolean opened = p.containerMenu != p.inventoryMenu;
		Optional<BlockPos> first = ScanToolItem.data(p.getOffhandItem()).first();
		p.closeContainer();
		helper.assertTrue(opened && first.isEmpty(), "stick in the main hand, Scan Tool in the off hand, on a chest: opened " + opened
			+ ", first corner " + first + ", saw " + texts(c.seen()));
		helper.succeed();
	}

	// --- B351: the Storehouse board's orders from the off hand ------------------------------------------------------

	/**
	 * B351: "Hold an item and click it (top right) to order it" from the off hand, through to the order: bread in the off
	 * hand (main hand empty) gets the button "Order Bread (keep 16)"; clicking it orders 16 bread at this Storehouse, the
	 * order is listed, and the bread stays in the hand. With a torch in the main hand and bread in the off hand, the
	 * button is the torch's (the main hand is the one held).
	 */
	//$ gametest_ticks_batch AREA '200' '"qaFixOffOrder"'
	@GameTest(template = AREA, timeoutTicks = 200, batch = "qaFixOffOrder")
	public void anOffHandItemIsOrderedAndKeptAndTheMainHandWins(GameTestHelper helper) {
		Leftovers.clear(helper);
		noVillageProtection(helper);
		ServerLevel level = helper.getLevel();
		BlockPos storehouse = new BlockPos(6, 2, 6);
		helper.setBlock(storehouse, ModBlocks.STOREHOUSE);
		helper.setBlock(new BlockPos(6, 2, 8), Blocks.CHEST);
		BlockPos at = helper.absolutePos(storehouse);
		QaStageFiveGameTests.Client c = QaStageSixGameTests.client(helper, GameType.SURVIVAL, QaStageSixGameTests.name("qafxB"));
		ServerPlayer p = c.player();
		QaStageFourGameTests.press(helper, p, storehouse);
		helper.assertTrue(p.containerMenu instanceof ChoiceMenu, "the board didn't open: " + p.containerMenu);
		ChoiceMenu board = (ChoiceMenu) p.containerMenu;
		p.setItemInHand(InteractionHand.OFF_HAND, new ItemStack(Items.BREAD, 3));
		QaStageSixGameTests.click(p, StorehouseBoard.ORDERS_SLOT, 0, ClickType.PICKUP);
		ItemStack add = board.icon(StorehouseBoard.ORDERS_SLOT);
		String name = english("screen.aliveworkplace.storehouse.order_add", Items.BREAD.getDescription().getString(), StockOrders.STEPS.get(0));
		helper.assertTrue(add.is(Items.BREAD) && add.getHoverName().getString().equals(name),
			"the off hand's button: " + add + " \"" + add.getHoverName().getString() + "\", expected \"" + name + "\"");
		QaStageSixGameTests.click(p, StorehouseBoard.ORDERS_SLOT, 0, ClickType.PICKUP);
		helper.assertTrue(StockOrders.of(level, at).getOrDefault(Items.BREAD, 0).equals(StockOrders.STEPS.get(0)),
			"ordering the off hand's bread: orders " + StockOrders.of(level, at));
		helper.assertTrue(p.getOffhandItem().is(Items.BREAD) && p.getOffhandItem().getCount() == 3 && p.containerMenu.getCarried().isEmpty(),
			"the bread went: off hand " + p.getOffhandItem() + ", carried " + p.containerMenu.getCarried());
		helper.assertFalse(board.icon(StorehouseBoard.ORDERS_SLOT).is(Items.BREAD) && board.icon(StorehouseBoard.ORDERS_SLOT).getHoverName().getString().equals(name),
			"bread already ordered is still offered to order");
		boolean listed = false;
		for (int s = 0; s < ChoiceMenu.SIZE; s++) {
			ItemStack icon = board.icon(s);
			listed |= icon.is(Items.BREAD) && icon.getHoverName().getString().equals(english("screen.aliveworkplace.storehouse.order", 16, Items.BREAD.getDescription().getString()));
		}
		helper.assertTrue(listed, "the order isn't listed on the orders page");

		// both hands full: the main hand is the one held
		p.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.TORCH, 5));
		QaStageSixGameTests.click(p, StorehouseBoard.BACK_SLOT, 0, ClickType.PICKUP);
		QaStageSixGameTests.click(p, StorehouseBoard.ORDERS_SLOT, 0, ClickType.PICKUP);
		ItemStack both = board.icon(StorehouseBoard.ORDERS_SLOT);
		p.closeContainer();
		helper.assertTrue(both.is(Items.TORCH), "torch in the main hand, bread in the off hand: the button is " + both + " (" + both.getHoverName().getString() + ")");
		helper.succeed();
	}

	// --- B369: the board's linked lines ------------------------------------------------------------------------------

	/**
	 * B369, for the Tom's inventory connector line the compat test leaves out (that one checks the Sophisticated
	 * controller's): the board writes both counted lines with {@code Words.counted}, so their en_us.json forms must read
	 * "… 1 block" for one and "… 2 blocks" for two.
	 */
	//$ gametest 'FabricGameTest.EMPTY_STRUCTURE'
	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
	public void theBoardsLinkedLinesCountOneBlockAndTwoBlocks(GameTestHelper helper) {
		List<String> got = List.of(
			Words.counted("screen.aliveworkplace.storehouse.connector", 1, 1).getString(),
			Words.counted("screen.aliveworkplace.storehouse.connector", 2, 2).getString(),
			Words.counted("screen.aliveworkplace.storehouse.linked", 1, 1).getString(),
			Words.counted("screen.aliveworkplace.storehouse.linked", 2, 2).getString());
		List<String> want = List.of("Linked through an inventory connector: 1 block", "Linked through an inventory connector: 2 blocks",
			"Linked through a storage controller: 1 block", "Linked through a storage controller: 2 blocks");
		helper.assertTrue(got.equals(want), "the board's linked lines read " + got);
		helper.succeed();
	}
}
