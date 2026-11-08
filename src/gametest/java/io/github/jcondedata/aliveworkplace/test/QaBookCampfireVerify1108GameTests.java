package io.github.jcondedata.aliveworkplace.test;

import io.github.jcondedata.aliveworkplace.blueprint.BlueprintData;
import io.github.jcondedata.aliveworkplace.blueprint.BlueprintItem;
import io.github.jcondedata.aliveworkplace.build.BuildSite;
import io.github.jcondedata.aliveworkplace.build.BuildSiteManager;
import io.github.jcondedata.aliveworkplace.build.BuilderBag;
import io.github.jcondedata.aliveworkplace.build.Builders;
import io.github.jcondedata.aliveworkplace.registry.ModAttachments;
import io.github.jcondedata.aliveworkplace.registry.ModBlocks;
import io.github.jcondedata.aliveworkplace.registry.ModComponents;
import io.github.jcondedata.aliveworkplace.registry.ModGameRules;
import io.github.jcondedata.aliveworkplace.registry.ModItems;
import io.github.jcondedata.aliveworkplace.table.TablePayloads;
import io.github.jcondedata.aliveworkplace.wonder.Wonder;
import io.github.jcondedata.aliveworkplace.wonder.WonderPlanItem;
import io.github.jcondedata.aliveworkplace.wonder.Wonders;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Vec3i;
import net.minecraft.core.component.DataComponents;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.locale.Language;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.Filterable;
import net.minecraft.world.Container;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.WritableBookContent;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CampfireBlock;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.entity.CampfireBlockEntity;
import net.minecraft.world.phys.AABB;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * QA (qa-lane-b-1108-1238) verifying the fixes of B1042 and B1043 from their ROADMAP text, for what the fixers' tests
 * (QaJournalBook1108bBugTests, BuilderWrittenBookGameTests, QaTakeDownCampfire1108bGameTests) leave out.
 *
 * <p>B1042: "only a blank Book and Quill (no pages or only blank pages) becomes the list; one with pages is left as it
 * was", with the line message.aliveworkplace.site_list.blank_book_needed; an off-hand written book is left out of the
 * Builder click (it trades), the Bench (chests line) and the Blueprint Table (opens); BlueprintItem.writeMaterialList
 * (a blueprint or Wonder Plan with a book in the other hand) refuses it too. Boundaries from both sides: no pages, one
 * page of only a space (blank) against one page of one letter and writing only on the third page; the book's saved
 * form is identical afterwards.
 *
 * <p>B1043: food cooking on a build's campfire comes back when a Builder takes the build down, into his bag or by the
 * bench when it is full. Four foods on a lit campfire, a lit soul campfire, an unlit campfire, a full bag (and full
 * chest), and a save and reload half way through the take-down: every food item comes back exactly once.
 */
public class QaBookCampfireVerify1108GameTests implements net.fabricmc.fabric.api.gametest.v1.FabricGameTest {
	private static final org.slf4j.Logger LOGGER = org.slf4j.LoggerFactory.getLogger("aliveworkplace_test");
	private static final String AREA = "aliveworkplace_test:build_area";
	private static final ResourceLocation TEST_HUT = ResourceLocation.fromNamespaceAndPath("aliveworkplace_test", "test_hut");
	private static final ResourceLocation FOLLY = ResourceLocation.fromNamespaceAndPath("aliveworkplace_test", "test_folly");
	private static final Vec3i HUT_SIZE = new Vec3i(5, 4, 5);
	private static final BlockPos TABLE = new BlockPos(2, 2, 2);
	private static final BlockPos CHEST = new BlockPos(2, 2, 4);
	private static final BlockPos VILLAGER = new BlockPos(3, 2, 3);
	private static final BlockPos HUT = new BlockPos(6, 2, 6);
	private static final BlockPos BUILD = new BlockPos(6, 2, 6);
	private static final String NEEDED = "message.aliveworkplace.site_list.blank_book_needed";
	private static final String NEEDED_TEXT = "This Book and Quill has your writing in it. Use a blank one for the material list.";

	// --- B1042 helpers -------------------------------------------------------------------------------------------------

	private static void noVillageProtection(GameTestHelper helper) {
		boolean enabled = io.github.jcondedata.aliveworkplace.hall.VillageProtection.ENABLED;
		io.github.jcondedata.aliveworkplace.hall.VillageProtection.ENABLED = false;
		Leftovers.after(helper, () -> io.github.jcondedata.aliveworkplace.hall.VillageProtection.ENABLED = enabled);
	}

	/** A builder at {@code job} with a chest by it and the test hut under way (he stands still: the list is read as it is). */
	private static Villager builderWithHut(GameTestHelper helper, Block job) {
		Leftovers.clear(helper);
		noVillageProtection(helper);
		ServerLevel level = helper.getLevel();
		helper.setBlock(TABLE, job);
		helper.setBlock(CHEST, Blocks.CHEST);
		Villager builder = helper.spawn(EntityType.VILLAGER, VILLAGER);
		builder.setNoAi(true);
		Builders.employ(level, builder, helper.absolutePos(TABLE));
		BuildSite site = Builders.start(level, builder, null, TEST_HUT,
			new BlueprintData.Placement(level.dimension().location(), helper.absolutePos(HUT), Rotation.NONE, Mirror.NONE));
		helper.assertTrue(site != null, "the test hut didn't start");
		Leftovers.after(helper, () -> {
			if (BuildSiteManager.get(level).get(site.id()) != null) {
				BuildSiteManager.get(level).remove(site.id());
			}
		});
		return builder;
	}

	/** A Book and Quill with {@code pages}; null for one with no content at all (fresh from the crafting table). */
	private static ItemStack book(String... pages) {
		ItemStack book = new ItemStack(Items.WRITABLE_BOOK);
		if (pages != null) {
			List<Filterable<String>> list = new ArrayList<>();
			for (String p : pages) {
				list.add(Filterable.passThrough(p));
			}
			book.set(DataComponents.WRITABLE_BOOK_CONTENT, new WritableBookContent(list));
		}
		return book;
	}

	private static String saved(GameTestHelper helper, ItemStack stack) {
		return stack.isEmpty() ? "empty" : stack.save(helper.getLevel().registryAccess()).toString();
	}

	private static List<String> texts(List<Component> seen) {
		return seen.stream().map(Component::getString).toList();
	}

	/** The written book in {@code hand} is still exactly the one handed in (its saved form unchanged), and the player was told. */
	private static void assertKept(GameTestHelper helper, ServerPlayer p, InteractionHand hand, String before, List<Component> seen, boolean told, String what) {
		String after = saved(helper, p.getItemInHand(hand));
		helper.assertTrue(after.equals(before), what + ": the player's Book and Quill changed: before " + before + ", after " + after + "; saw " + texts(seen));
		helper.assertTrue(p.getInventory().countItem(Items.WRITTEN_BOOK) == 0, what + ": a material list was written somewhere: saw " + texts(seen));
		Component line = QaStageFourGameTests.find(seen, NEEDED);
		if (told) {
			helper.assertTrue(line != null, what + ": the player wasn't told a blank Book and Quill is needed: " + QaStageFourGameTests.keys(seen));
			helper.assertTrue(line.getString().equals(NEEDED_TEXT), what + ": the line reads " + line.getString());
		} else {
			helper.assertTrue(line == null, what + ": an off-hand book the click leaves alone still said " + line);
		}
	}

	private static void assertWritten(GameTestHelper helper, ServerPlayer p, InteractionHand hand, List<Component> seen, String what) {
		helper.assertTrue(p.getItemInHand(hand).is(Items.WRITTEN_BOOK), what + ": a blank Book and Quill wasn't written: " + p.getItemInHand(hand)
			+ ", saw " + texts(seen));
		helper.assertTrue(QaStageFourGameTests.find(seen, NEEDED) == null, what + ": a blank book was refused as written in");
	}

	// --- B1042 tests ----------------------------------------------------------------------------------------------------

	/**
	 * The blank/written boundary on the Builder, main hand: a book with no content, one page of only a space (blank: no
	 * writing) become the list; one page of one letter, and a book whose writing is only on its third page (two empty
	 * pages first), are kept byte for byte and the player gets the blank_book_needed line.
	 */
	//$ gametest_ticks_batch AREA '80' '"qaVerify1108BookEdges"'
	@GameTest(template = AREA, timeoutTicks = 80, batch = "qaVerify1108BookEdges")
	public void theBlankAndWrittenBoundaryOnTheBuilder(GameTestHelper helper) {
		Villager builder = builderWithHut(helper, ModBlocks.BLUEPRINT_TABLE);
		QaStageFiveGameTests.Client c = QaStageSixGameTests.client(helper, GameType.SURVIVAL, QaStageSixGameTests.name("qvE"));
		ServerPlayer p = c.player();

		record Case(String what, ItemStack book, boolean blank) {
		}
		List<Case> cases = List.of(
			new Case("no pages", book((String[]) null), true),
			new Case("one page of a single space", book(" "), true),
			new Case("one page of a single letter", book("a"), false),
			new Case("writing only on the third page", book("", "", "x"), false));
		for (Case k : cases) {
			c.seen().clear();
			p.getInventory().clearContent();
			p.setItemInHand(InteractionHand.MAIN_HAND, k.book().copy());
			String before = saved(helper, p.getMainHandItem());
			QaStageSixGameTests.clickVillager(p, builder, c.seen());
			if (k.blank()) {
				assertWritten(helper, p, InteractionHand.MAIN_HAND, c.seen(), k.what());
			} else {
				assertKept(helper, p, InteractionHand.MAIN_HAND, before, c.seen(), true, k.what());
			}
			p.closeContainer();
			LOGGER.info("qa verify 1108 book edges: {} -> {}", k.what(), p.getMainHandItem());
		}
		helper.succeed();
	}

	/**
	 * Off hand, main hand empty, on the Builder: a one-letter book is left exactly as it was and no blank_book_needed line
	 * is said (the click is a plain one, to trade); a one-space book (blank) still becomes the list from the off hand.
	 */
	//$ gametest_ticks_batch AREA '80' '"qaVerify1108BookOffHand"'
	@GameTest(template = AREA, timeoutTicks = 80, batch = "qaVerify1108BookOffHand")
	public void theOffHandBoundaryOnTheBuilder(GameTestHelper helper) {
		Villager builder = builderWithHut(helper, ModBlocks.BLUEPRINT_TABLE);
		QaStageFiveGameTests.Client c = QaStageSixGameTests.client(helper, GameType.SURVIVAL, QaStageSixGameTests.name("qvO"));
		ServerPlayer p = c.player();
		p.setItemInHand(InteractionHand.OFF_HAND, book("a"));
		String before = saved(helper, p.getOffhandItem());
		QaStageSixGameTests.clickVillager(p, builder, c.seen());
		assertKept(helper, p, InteractionHand.OFF_HAND, before, c.seen(), false, "off hand, one letter");
		helper.assertTrue(p.getMainHandItem().isEmpty(), "the main hand got " + p.getMainHandItem());
		p.closeContainer();

		c.seen().clear();
		p.setItemInHand(InteractionHand.OFF_HAND, book(" "));
		QaStageSixGameTests.clickVillager(p, builder, c.seen());
		assertWritten(helper, p, InteractionHand.OFF_HAND, c.seen(), "off hand, one space");
		helper.succeed();
	}

	/**
	 * The Builder's Bench: a written book used from the main hand is kept and the player told; one in the off hand with
	 * the main hand empty is left alone with no list and no blank_book_needed line (the bench answers as for an empty
	 * hand); a blank one in the off hand still becomes the list.
	 */
	//$ gametest_ticks_batch AREA '80' '"qaVerify1108BookBench"'
	@GameTest(template = AREA, timeoutTicks = 80, batch = "qaVerify1108BookBench")
	public void aWrittenBookAtTheBuildersBenchFromEitherHand(GameTestHelper helper) {
		builderWithHut(helper, ModBlocks.BUILDERS_BENCH);
		QaStageFiveGameTests.Client c = QaStageSixGameTests.client(helper, GameType.SURVIVAL, QaStageSixGameTests.name("qvB"));
		ServerPlayer p = c.player();
		p.setItemInHand(InteractionHand.MAIN_HAND, book("Mine at 12 -40 88"));
		String before = saved(helper, p.getMainHandItem());
		QaStageFourGameTests.press(helper, p, TABLE, Direction.NORTH);
		assertKept(helper, p, InteractionHand.MAIN_HAND, before, c.seen(), true, "bench, main hand");
		p.closeContainer();

		c.seen().clear();
		p.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
		p.setItemInHand(InteractionHand.OFF_HAND, book("Mine at 12 -40 88"));
		before = saved(helper, p.getOffhandItem());
		QaStageFourGameTests.press(helper, p, TABLE, Direction.NORTH);
		assertKept(helper, p, InteractionHand.OFF_HAND, before, c.seen(), false, "bench, off hand");
		p.closeContainer();

		c.seen().clear();
		p.setItemInHand(InteractionHand.OFF_HAND, book(""));
		QaStageFourGameTests.press(helper, p, TABLE, Direction.NORTH);
		assertWritten(helper, p, InteractionHand.OFF_HAND, c.seen(), "bench, off hand, blank");
		helper.succeed();
	}

	/**
	 * The builder's Blueprint Table: a written book from the main hand is kept and the player told (no screen); from the
	 * off hand with the main hand empty it is left alone and the table opens as for an empty hand.
	 */
	//$ gametest_ticks_batch AREA '80' '"qaVerify1108BookTable"'
	@GameTest(template = AREA, timeoutTicks = 80, batch = "qaVerify1108BookTable")
	public void aWrittenBookAtTheBlueprintTableFromEitherHand(GameTestHelper helper) {
		builderWithHut(helper, ModBlocks.BLUEPRINT_TABLE);
		QaStageFiveGameTests.Client c = QaStageSixGameTests.client(helper, GameType.SURVIVAL, QaStageSixGameTests.name("qvT"));
		ServerPlayer p = c.player();
		p.setItemInHand(InteractionHand.MAIN_HAND, book("Portal room plans"));
		String before = saved(helper, p.getMainHandItem());
		c.sent();
		QaStageFourGameTests.press(helper, p, TABLE, Direction.NORTH);
		assertKept(helper, p, InteractionHand.MAIN_HAND, before, c.seen(), true, "table, main hand");
		helper.assertTrue(c.sent(TablePayloads.Open.class).isEmpty(), "table, main hand: the refused book opened the Table too");
		p.closeContainer();

		c.seen().clear();
		p.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
		p.setItemInHand(InteractionHand.OFF_HAND, book("Portal room plans"));
		before = saved(helper, p.getOffhandItem());
		c.sent();
		QaStageFourGameTests.press(helper, p, TABLE, Direction.NORTH);
		List<TablePayloads.Open> open = c.sent(TablePayloads.Open.class);
		assertKept(helper, p, InteractionHand.OFF_HAND, before, c.seen(), false, "table, off hand");
		helper.assertTrue(open.size() == 1 && open.get(0).table().equals(helper.absolutePos(TABLE)),
			"table, off hand: the Table didn't open as for an empty hand: sent " + open + ", saw " + texts(c.seen()));
		p.closeContainer();
		helper.succeed();
	}

	/**
	 * BlueprintItem.writeMaterialList through a right-click on the air: a written book with the blueprint in the other
	 * hand, both hand orders, is kept byte for byte (the blueprint unchanged too) and the player told; a blank book in each
	 * order still gets the list. The line resolves in en_us.json to the spec's American English text.
	 */
	//$ gametest_ticks_batch AREA '60' '"qaVerify1108BookBlueprint"'
	@GameTest(template = AREA, timeoutTicks = 60, batch = "qaVerify1108BookBlueprint")
	public void aBlueprintAndAWrittenBookInEitherHandOrder(GameTestHelper helper) {
		Leftovers.clear(helper);
		QaFurnishedGameTests.resetAfter(helper);
		String english = Language.getInstance().getOrDefault(NEEDED);
		helper.assertTrue(english.equals(NEEDED_TEXT), "en_us.json's " + NEEDED + " reads " + english);
		helper.assertFalse(english.toLowerCase(java.util.Locale.ROOT).matches(".*(colour|favour|armour|grey|cancelled|defence|realise|organise).*"),
			"not American English: " + english);
		QaStageFiveGameTests.Client c = QaStageSixGameTests.client(helper, GameType.SURVIVAL, QaStageSixGameTests.name("qvP"));
		ServerPlayer p = c.player();
		for (InteractionHand bpHand : List.of(InteractionHand.MAIN_HAND, InteractionHand.OFF_HAND)) {
			InteractionHand bookHand = bpHand == InteractionHand.MAIN_HAND ? InteractionHand.OFF_HAND : InteractionHand.MAIN_HAND;
			c.seen().clear();
			p.getInventory().clearContent();
			p.setItemInHand(bpHand, BlueprintItem.create(TEST_HUT, HUT_SIZE));
			p.setItemInHand(bookHand, book("Shopping: 3 iron, 2 string"));
			String before = saved(helper, p.getItemInHand(bookHand));
			String blueprint = saved(helper, p.getItemInHand(bpHand));
			QaFurnishedGameTests.rightClickAir(p);
			assertKept(helper, p, bookHand, before, c.seen(), true, "blueprint in the " + bpHand);
			helper.assertTrue(saved(helper, p.getItemInHand(bpHand)).equals(blueprint), "blueprint in the " + bpHand + ": the blueprint changed: "
				+ p.getItemInHand(bpHand));

			c.seen().clear();
			p.setItemInHand(bookHand, book(" "));
			QaFurnishedGameTests.rightClickAir(p);
			assertWritten(helper, p, bookHand, c.seen(), "blueprint in the " + bpHand + ", blank book");
			helper.assertTrue(p.getItemInHand(bpHand).is(ModItems.BLUEPRINT), "the blueprint went from the " + bpHand);
		}
		helper.succeed();
	}

	/**
	 * A Wonder Plan in the main hand and a written book in the off hand: the book is kept byte for byte and the player
	 * told; a blank one gets the whole Wonder's list (the same writeMaterialList).
	 */
	//$ gametest_ticks_batch AREA '60' '"qaVerify1108BookWonder"'
	@GameTest(template = AREA, timeoutTicks = 60, batch = "qaVerify1108BookWonder")
	public void aWonderPlanAndAWrittenBook(GameTestHelper helper) {
		Leftovers.clear(helper);
		QaFurnishedGameTests.resetAfter(helper);
		Wonder folly = Wonders.loaded().get(FOLLY);
		helper.assertTrue(folly != null, "the test folly isn't loaded: " + Wonders.problems());
		ServerLevel level = helper.getLevel();
		QaStageFiveGameTests.Client c = QaStageSixGameTests.client(helper, GameType.SURVIVAL, QaStageSixGameTests.name("qvW"));
		ServerPlayer p = c.player();
		p.setItemInHand(InteractionHand.MAIN_HAND, WonderPlanItem.of(level, helper.absolutePos(TABLE), folly, Component.literal("Testholm")));
		p.setItemInHand(InteractionHand.OFF_HAND, book("Wonder ideas: a tall tower"));
		String before = saved(helper, p.getOffhandItem());
		QaFurnishedGameTests.rightClickAir(p);
		assertKept(helper, p, InteractionHand.OFF_HAND, before, c.seen(), true, "Wonder Plan");

		c.seen().clear();
		p.setItemInHand(InteractionHand.OFF_HAND, book((String[]) null));
		QaFurnishedGameTests.rightClickAir(p);
		assertWritten(helper, p, InteractionHand.OFF_HAND, c.seen(), "Wonder Plan, blank book");
		helper.succeed();
	}

	// --- B1043 ----------------------------------------------------------------------------------------------------------

	private record Fire(BlockState state, List<Item> food, boolean fullBag, boolean reload) {
	}

	/**
	 * Builds a campfire (state {@code f.state}) on the ground with a three-plank tower beside it, saves both as a blueprint,
	 * puts {@code f.food} on the campfire, has a builder take it down (sneak hand-over by a player) and checks every food
	 * item comes back exactly once (raw or cooked) and the build's own blocks do too.
	 */
	private static void takeDownCampfire(GameTestHelper helper, String name, Fire f) {
		Leftovers.clear(helper);
		ServerLevel level = helper.getLevel();
		helper.setDayTime(2000);
		var rules = level.getGameRules();
		int delay = rules.getRule(ModGameRules.BUILD_DELAY).get();
		boolean help = rules.getRule(ModGameRules.BUILDERS_HELP).get();
		rules.getRule(ModGameRules.BUILD_DELAY).set(2, level.getServer());
		rules.getRule(ModGameRules.BUILDERS_HELP).set(false, level.getServer());
		noVillageProtection(helper);
		Leftovers.after(helper, () -> {
			rules.getRule(ModGameRules.BUILD_DELAY).set(delay, level.getServer());
			rules.getRule(ModGameRules.BUILDERS_HELP).set(help, level.getServer());
		});

		BlockPos tower = BUILD.east();
		helper.setBlock(BUILD, f.state());
		for (int y = 0; y < 3; y++) {
			helper.setBlock(tower.above(y), Blocks.OAK_PLANKS);
		}
		ResourceLocation id = ResourceLocation.fromNamespaceAndPath("aliveworkplace_test", "qa_verify1108_" + name + "_" + Long.toHexString(level.getGameTime()));
		var manager = level.getStructureManager();
		manager.getOrCreate(id).fillFromWorld(level, helper.absolutePos(BUILD), new Vec3i(2, 3, 1), false, Blocks.STRUCTURE_VOID);
		Leftovers.after(helper, () -> manager.remove(id));
		CampfireBlockEntity campfire = (CampfireBlockEntity) helper.getBlockEntity(BUILD);
		for (Item food : f.food()) {
			helper.assertTrue(campfire.placeFood(null, new ItemStack(food), 1_000_000), "the " + food + " didn't go on the campfire");
		}
		BlueprintData.Placement placement = new BlueprintData.Placement(level.dimension().location(), helper.absolutePos(BUILD), Rotation.NONE, Mirror.NONE);

		helper.setBlock(TABLE, ModBlocks.BLUEPRINT_TABLE);
		helper.setBlock(CHEST, Blocks.CHEST);
		Container chest = (Container) helper.getBlockEntity(CHEST);
		Villager[] villager = {helper.spawn(EntityType.VILLAGER, VILLAGER)};
		Builders.employ(level, villager[0], helper.absolutePos(TABLE));
		UUID builderId = villager[0].getUUID();
		Leftovers.after(helper, () -> {
			BuildSiteManager sites = BuildSiteManager.get(level);
			for (BuildSite s : List.copyOf(sites.all())) {
				if (builderId.equals(s.builder())) {
					sites.remove(s.id());
				}
			}
		});
		if (f.fullBag()) {
			BuilderBag bag = ModAttachments.BUILDER_BAG.getOrCreate(villager[0]);
			for (int i = 0; i < BuilderBag.SLOTS; i++) {
				helper.assertTrue(bag.add(new ItemStack(Items.COBBLESTONE, 64)).isEmpty(), "set-up: the bag took fewer than 27 stacks");
				chest.setItem(i, new ItemStack(Items.COBBLESTONE, 64));
			}
			helper.assertTrue(bag.add(new ItemStack(Items.BEEF)).getCount() == 1, "set-up: the bag isn't full");
		}

		QaStageFiveGameTests.Client c = QaStageSixGameTests.client(helper, GameType.SURVIVAL, QaStageSixGameTests.name("qvC"));
		ItemStack blueprint = BlueprintItem.create(id, null);
		blueprint.set(ModComponents.BLUEPRINT, BlueprintItem.data(blueprint).orElseThrow().withPlacement(Optional.of(placement)));
		c.player().setItemInHand(InteractionHand.MAIN_HAND, blueprint);
		c.player().setShiftKeyDown(true);
		QaStageSixGameTests.clickVillager(c.player(), villager[0], c.seen());
		c.player().setShiftKeyDown(false);
		BuildSite site = Builders.activeSite(level, villager[0]);
		helper.assertTrue(site != null && site.isDeconstruction(), "the sneak hand-over started no take-down: " + texts(c.seen()));
		UUID siteId = site.id();

		int[] reloads = {0};
		boolean[] campfireStoodAtReload = {false};
		helper.onEachTick(() -> {
			if (f.reload() && reloads[0] == 0 && helper.getBlockState(tower.above(2)).isAir()) {
				campfireStoodAtReload[0] = helper.getBlockState(BUILD).is(f.state().getBlock());
				villager[0] = reload(helper, level, villager[0]);
				reloads[0]++;
				LOGGER.info("qa verify 1108 {}: saved and reloaded mid take-down, campfire standing {}", name, campfireStoodAtReload[0]);
			}
		});
		helper.succeedWhen(() -> {
			helper.assertTrue(BuildSiteManager.get(level).get(siteId) == null && helper.getBlockState(BUILD).isAir()
				&& helper.getBlockState(tower).isAir(), "the take-down isn't finished: " + helper.getBlockState(BUILD) + ", " + helper.getBlockState(tower));
			if (f.reload()) {
				helper.assertTrue(reloads[0] == 1 && campfireStoodAtReload[0], "the reload didn't come mid take-down with the campfire standing: "
					+ reloads[0] + " reload(s), campfire standing " + campfireStoodAtReload[0]);
			}
			Villager v = villager[0];
			int planks = everywhere(helper, chest, v, Items.OAK_PLANKS);
			int fires = everywhere(helper, chest, v, f.state().getBlock().asItem());
			helper.assertTrue(planks == 3 && fires == 1, "the build's own blocks: " + planks + " planks (3), " + fires + " campfires (1)");
			List<String> counts = new ArrayList<>();
			boolean ok = true;
			for (Item food : f.food()) {
				int n = everywhere(helper, chest, v, food) + everywhere(helper, chest, v, cooked(food));
				counts.add(food + "=" + n + " (bag " + ModAttachments.BUILDER_BAG.getOrCreate(v).count(food) + ", chest " + chest.countItem(food)
					+ ", ground " + ground(helper, food) + ")");
				ok &= n == f.food().stream().filter(x -> x == food).count();
			}
			helper.assertTrue(ok, "the food on the campfire didn't come back exactly once each: " + counts);
			if (f.fullBag()) {
				for (Item food : f.food()) {
					helper.assertTrue(ground(helper, food) + ground(helper, cooked(food)) >= 1,
						"with the bag (and chest) full, the " + food + " should lie by the bench: " + counts);
				}
			}
			LOGGER.info("qa verify 1108 {}: {}", name, counts);
		});
	}

	private static Item cooked(Item raw) {
		if (raw == Items.BEEF) return Items.COOKED_BEEF;
		if (raw == Items.PORKCHOP) return Items.COOKED_PORKCHOP;
		if (raw == Items.MUTTON) return Items.COOKED_MUTTON;
		if (raw == Items.CHICKEN) return Items.COOKED_CHICKEN;
		if (raw == Items.COD) return Items.COOKED_COD;
		if (raw == Items.RABBIT) return Items.COOKED_RABBIT;
		return raw;
	}

	/** As a save and load of the world: every build site from its save, and the builder from its save (QaTakeDown1105GameTests). */
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

	private static int ground(GameTestHelper helper, Item item) {
		int n = 0;
		AABB box = new AABB(helper.absolutePos(BlockPos.ZERO)).inflate(24);
		for (ItemEntity e : helper.getLevel().getEntitiesOfClass(ItemEntity.class, box, e -> e.isAlive() && e.getItem().is(item))) {
			n += e.getItem().getCount();
		}
		return n;
	}

	/** {@code item} in the chest, the builder's bag and lying on the ground round the test. */
	private static int everywhere(GameTestHelper helper, Container chest, Villager villager, Item item) {
		return chest.countItem(item) + ModAttachments.BUILDER_BAG.getOrCreate(villager).count(item) + ground(helper, item);
	}

	/** Four foods (every slot) cooking on a lit campfire: all four come back. */
	//$ gametest_ticks_batch AREA '1200' '"qaVerify1108FourFoods"'
	@GameTest(template = AREA, timeoutTicks = 1200, batch = "qaVerify1108FourFoods")
	public void fourFoodsOnALitCampfireAllComeBack(GameTestHelper helper) {
		takeDownCampfire(helper, "four", new Fire(Blocks.CAMPFIRE.defaultBlockState(),
			List.of(Items.BEEF, Items.PORKCHOP, Items.MUTTON, Items.CHICKEN), false, false));
	}

	/** A lit soul campfire with two fish on it: both come back, and the soul campfire itself. */
	//$ gametest_ticks_batch AREA '1200' '"qaVerify1108Soul"'
	@GameTest(template = AREA, timeoutTicks = 1200, batch = "qaVerify1108Soul")
	public void foodOnASoulCampfireComesBack(GameTestHelper helper) {
		takeDownCampfire(helper, "soul", new Fire(Blocks.SOUL_CAMPFIRE.defaultBlockState(), List.of(Items.COD, Items.RABBIT), false, false));
	}

	/** An unlit campfire still holding food (put out with it on): the food comes back as from a lit one. */
	//$ gametest_ticks_batch AREA '1200' '"qaVerify1108Unlit"'
	@GameTest(template = AREA, timeoutTicks = 1200, batch = "qaVerify1108Unlit")
	public void foodOnAnUnlitCampfireComesBack(GameTestHelper helper) {
		takeDownCampfire(helper, "unlit", new Fire(Blocks.CAMPFIRE.defaultBlockState().setValue(CampfireBlock.LIT, false),
			List.of(Items.BEEF, Items.BEEF, Items.CHICKEN), false, false));
	}

	/** The builder's bag full (and the bench's chest): the four foods lie by the bench, none lost, none doubled. */
	//$ gametest_ticks_batch AREA '1200' '"qaVerify1108FullBag"'
	@GameTest(template = AREA, timeoutTicks = 1200, batch = "qaVerify1108FullBag")
	public void withAFullBagTheFoodGoesByTheBench(GameTestHelper helper) {
		takeDownCampfire(helper, "fullbag", new Fire(Blocks.CAMPFIRE.defaultBlockState(),
			List.of(Items.BEEF, Items.PORKCHOP, Items.MUTTON, Items.CHICKEN), true, false));
	}

	/** A save and reload after the tower's top plank is down but before the campfire: the food still comes back once. */
	//$ gametest_ticks_batch AREA '1200' '"qaVerify1108Reload"'
	@GameTest(template = AREA, timeoutTicks = 1200, batch = "qaVerify1108Reload")
	public void foodComesBackAcrossASaveAndReloadMidTakeDown(GameTestHelper helper) {
		takeDownCampfire(helper, "reload", new Fire(Blocks.CAMPFIRE.defaultBlockState(),
			List.of(Items.BEEF, Items.MUTTON), false, true));
	}
}
