package io.github.jcondedata.aliveworkplace.test;

import io.github.jcondedata.aliveworkplace.blueprint.BlueprintData;
import io.github.jcondedata.aliveworkplace.blueprint.BlueprintItem;
import io.github.jcondedata.aliveworkplace.blueprint.Blueprints;
import io.github.jcondedata.aliveworkplace.blueprint.SupplyReport;
import io.github.jcondedata.aliveworkplace.build.BlueprintSupplies;
import io.github.jcondedata.aliveworkplace.build.BuildSite;
import io.github.jcondedata.aliveworkplace.build.Builders;
import io.github.jcondedata.aliveworkplace.registry.ModBlocks;
import io.github.jcondedata.aliveworkplace.registry.ModComponents;
import io.github.jcondedata.aliveworkplace.registry.ModItems;
import io.github.jcondedata.aliveworkplace.store.StorehouseBoard;
import io.github.jcondedata.aliveworkplace.work.ChoiceMenu;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Vec3i;
import net.minecraft.core.component.DataComponents;
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
 * QA (2026-10-24) of the count-line fixes B377, B378, B395, B402, B403 and B404, from the bugs' own words: each line read
 * at the counts 0 (where a player can reach it), 1 and 2 through the player's own clicks, which the existing tests
 * (CountWordsGameTests, QaBuilderWordsBugTests, QaBuilderLimitsBugTests, BuilderSiteListGameTests) leave out: the
 * Storehouse's stock line at 0 (B377, B395), the board's "Room for N more items" (B402, only code-read there), "2 blocks
 * away" (B402), the builder's chat list at "5 items of 1 kind" and "2 items of 2 kinds" (B402, B404's kin), the
 * blueprint tooltip's "short of N items" (B402, only code-read there), the too-far line well past, just past and exactly
 * at the builder's reach (B378), a repair of two blocks (B403) and a porter who carried two items (B403).
 */
public class QaBuilderCountsGameTests implements net.fabricmc.fabric.api.gametest.v1.FabricGameTest {
	private static final String AREA = "aliveworkplace_test:build_area";
	private static final String BIG = "aliveworkplace_test:big_area";
	private static final org.slf4j.Logger LOGGER = org.slf4j.LoggerFactory.getLogger("aliveworkplace_test");
	private static final ResourceLocation TEST_HUT = ResourceLocation.fromNamespaceAndPath("aliveworkplace_test", "test_hut");
	private static final BlockPos STOREHOUSE = new BlockPos(3, 2, 3);
	private static final BlockPos STORE_CHEST = new BlockPos(5, 2, 3);

	private static void noProtection(GameTestHelper helper) {
		boolean enabled = io.github.jcondedata.aliveworkplace.hall.VillageProtection.ENABLED;
		io.github.jcondedata.aliveworkplace.hall.VillageProtection.ENABLED = false;
		Leftovers.after(helper, () -> io.github.jcondedata.aliveworkplace.hall.VillageProtection.ENABLED = enabled);
	}

	/** The lore of the board's info book, the board opened by the player's right-click on the Storehouse. */
	private static List<String> infoLore(GameTestHelper helper, ServerPlayer p, BlockPos storehouse) {
		QaStageFourGameTests.press(helper, p, storehouse);
		helper.assertTrue(p.containerMenu instanceof ChoiceMenu, "the board didn't open: " + p.containerMenu);
		List<String> lore = QaStageSixGameTests.lore(((ChoiceMenu) p.containerMenu).icon(StorehouseBoard.INFO_SLOT));
		p.closeContainer();
		return lore;
	}

	private static String line(List<String> lore, String start) {
		return lore.stream().filter(l -> l.startsWith(start)).findFirst().orElse("(no '" + start + "' line in " + lore + ")");
	}

	/**
	 * B377, B395 at 0: a Storehouse with no chest reads "0 chests, 0 items, 0 empty slots"; with one empty chest "1 chest,
	 * 0 items, 27 empty slots"; with that chest full of cobblestone "1 chest, 1728 items, 0 empty slots". The board is
	 * opened by the player's click on the Storehouse each time.
	 */
	//$ gametest_ticks_batch AREA '60' '"qaCountsStockZero"'
	@GameTest(template = AREA, timeoutTicks = 60, batch = "qaCountsStockZero")
	public void theStockLineCountsZeroChestsItemsAndSlots(GameTestHelper helper) {
		Leftovers.clear(helper);
		helper.setBlock(STOREHOUSE, ModBlocks.STOREHOUSE);
		ServerPlayer p = QaStageSixGameTests.client(helper, GameType.SURVIVAL, QaStageSixGameTests.name("qacZ")).player();
		String none = line(infoLore(helper, p, STOREHOUSE), "Storehouse: ");
		helper.assertTrue(none.equals("Storehouse: 0 chests, 0 items, 0 empty slots"), "no chest: " + none);
		helper.setBlock(STORE_CHEST, Blocks.CHEST);
		String empty = line(infoLore(helper, p, STOREHOUSE), "Storehouse: ");
		helper.assertTrue(empty.equals("Storehouse: 1 chest, 0 items, 27 empty slots"), "an empty chest: " + empty);
		Container chest = helper.getBlockEntity(STORE_CHEST);
		for (int i = 0; i < chest.getContainerSize(); i++) {
			chest.setItem(i, new ItemStack(Items.COBBLESTONE, 64));
		}
		String full = line(infoLore(helper, p, STOREHOUSE), "Storehouse: ");
		helper.assertTrue(full.equals("Storehouse: 1 chest, " + 27 * 64 + " items, 0 empty slots"), "a full chest: " + full);
		LOGGER.info("qa counts: stock lines '{}' / '{}' / '{}'", none, empty, full);
		helper.succeed();
	}

	/**
	 * B402's "Room for %s more items", shown where a slot holds more than its item's usual stack: a chest full of
	 * cobblestone, its last slot a stack that may hold 99 (the max_stack_size component, as /give can make) holding 98.
	 * Room for one more: "Room for 1 more item (some slots hold more than a stack)"; holding 97, "Room for 2 more items".
	 */
	//$ gametest_ticks_batch AREA '60' '"qaCountsRoom"'
	@GameTest(template = AREA, timeoutTicks = 60, batch = "qaCountsRoom")
	public void theRoomLineCountsOneMoreItemAndTwo(GameTestHelper helper) {
		Leftovers.clear(helper);
		helper.setBlock(STOREHOUSE, ModBlocks.STOREHOUSE);
		helper.setBlock(STORE_CHEST, Blocks.CHEST);
		Container chest = helper.getBlockEntity(STORE_CHEST);
		int last = chest.getContainerSize() - 1;
		for (int i = 0; i < last; i++) {
			chest.setItem(i, new ItemStack(Items.COBBLESTONE, 64));
		}
		ServerPlayer p = QaStageSixGameTests.client(helper, GameType.SURVIVAL, QaStageSixGameTests.name("qacR")).player();
		String[] want = {"Room for 1 more item (some slots hold more than a stack)", "Room for 2 more items (some slots hold more than a stack)"};
		for (int room = 1; room <= 2; room++) {
			ItemStack big = new ItemStack(Items.COBBLESTONE, 99 - room);
			big.set(DataComponents.MAX_STACK_SIZE, 99);
			chest.setItem(last, big);
			String seen = line(infoLore(helper, p, STOREHOUSE), "Room for ");
			helper.assertTrue(seen.equals(want[room - 1]), "room for " + room + ": " + seen + " (want '" + want[room - 1] + "')");
		}
		helper.succeed();
	}

	/**
	 * B402: the board's request line "For the Builder, N blocks away", N from the builder's bench to the player who opened
	 * it. A Storehouse beside the builder's chest: the player who clicks it stands about two blocks from the bench (the
	 * line can't read 0: the player stands on top of the block he clicks). It reads "2 blocks away".
	 */
	//$ gametest_ticks_batch AREA '1200' '"qaCountsBoardTwo"'
	@GameTest(template = AREA, timeoutTicks = 1200, batch = "qaCountsBoardTwo")
	public void aWorkerTwoBlocksAwayReadsTwoBlocksAway(GameTestHelper helper) {
		QaStageSixGameTests.Waiting w = QaStageSixGameTests.waitingBuilder(helper);
		noProtection(helper);
		helper.runAfterDelay(300, () -> {
			helper.assertTrue(w.site().status() == BuildSite.Status.WAITING_FOR_MATERIALS, "the builder isn't waiting: " + w.site().status());
			ServerPlayer p = QaStageSixGameTests.client(helper, GameType.SURVIVAL, QaStageSixGameTests.name("qacB")).player();
			helper.setBlock(w.storehouse(), Blocks.AIR);
			BlockPos near = new BlockPos(0, 2, 4);
			helper.setBlock(near, ModBlocks.STOREHOUSE);
			QaStageFourGameTests.press(helper, p, near);
			helper.assertTrue(p.containerMenu instanceof ChoiceMenu, "the board didn't open: " + p.containerMenu);
			ChoiceMenu board = (ChoiceMenu) p.containerMenu;
			int slot = QaStageSixGameTests.slotOf(board, Items.COBBLESTONE);
			helper.assertTrue(slot >= 0, "the board doesn't ask for cobblestone");
			String forLine = line(QaStageSixGameTests.lore(board.icon(slot)), "For the ");
			p.closeContainer();
			double d = Math.sqrt(p.blockPosition().distSqr(helper.absolutePos(new BlockPos(2, 2, 2))));
			helper.assertTrue(Math.round(d) == 2, "the player stands " + d + " blocks from the bench");
			helper.assertTrue(forLine.endsWith(", 2 blocks away"), "two blocks from the bench the board reads '" + forLine + "' (want '..., 2 blocks away')");
			helper.succeed();
		});
	}

	/**
	 * B402 (and B404's words in chat): the builder's list in chat, by a sneak-click with an empty hand. The test hut needs
	 * 25 cobblestone, 55 oak planks, a door and a torch. Five planks short and nothing else: "Still to bring for the Test
	 * Hut: 5 items of 1 kind"; one plank and the torch short: "2 items of 2 kinds"; nothing short: "Everything the Test Hut
	 * still needs is in the chests or the village store."
	 */
	//$ gametest_ticks_batch AREA '60' '"qaCountsChat"'
	@GameTest(template = AREA, timeoutTicks = 60, batch = "qaCountsChat")
	public void theChatListCountsFiveOfOneKindTwoOfTwoKindsAndNone(GameTestHelper helper) {
		Leftovers.clear(helper);
		noProtection(helper);
		ServerLevel level = helper.getLevel();
		BlockPos bench = new BlockPos(2, 2, 2);
		BlockPos chestPos = new BlockPos(2, 2, 4);
		helper.setBlock(bench, ModBlocks.BUILDERS_BENCH);
		helper.setBlock(chestPos, Blocks.CHEST);
		Container chest = helper.getBlockEntity(chestPos);
		chest.setItem(0, new ItemStack(Items.COBBLESTONE, 25));
		chest.setItem(2, new ItemStack(Items.OAK_DOOR));
		Villager builder = helper.spawn(EntityType.VILLAGER, new BlockPos(3, 2, 3));
		builder.setNoAi(true);
		Builders.employ(level, builder, helper.absolutePos(bench));
		BuildSite site = Builders.start(level, builder, null, TEST_HUT,
			new BlueprintData.Placement(level.dimension().location(), helper.absolutePos(new BlockPos(6, 2, 6)), Rotation.NONE, Mirror.NONE));
		helper.assertTrue(site != null, "the test hut didn't start");
		QaStageFiveGameTests.Client c = QaStageSixGameTests.client(helper, GameType.SURVIVAL, QaStageSixGameTests.name("qacC"));
		ServerPlayer p = c.player();
		String hut = Blueprints.displayName(TEST_HUT).getString();
		record Case(int planks, boolean torch, String want) {
		}
		for (Case k : List.of(new Case(50, true, "Still to bring for the " + hut + ": 5 items of 1 kind"),
			new Case(54, false, "Still to bring for the " + hut + ": 2 items of 2 kinds"),
			new Case(55, true, "Everything the " + hut + " still needs is in the chests or the village store."))) {
			chest.setItem(1, new ItemStack(Items.OAK_PLANKS, k.planks()));
			chest.setItem(3, k.torch() ? new ItemStack(Items.TORCH) : ItemStack.EMPTY);
			c.seen().clear();
			p.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
			p.setShiftKeyDown(true);
			QaStageFourGameTests.pressOn(p, builder, c.seen());
			p.setShiftKeyDown(false);
			List<String> seen = c.seen().stream().map(Component::getString).toList();
			String heading = seen.stream().filter(t -> t.startsWith("Still to bring for the ") || t.startsWith("Everything the "))
				.map(t -> t.lines().findFirst().orElse("")).findFirst().orElse(null);
			helper.assertTrue(heading != null, k.planks() + " planks, torch " + k.torch() + ": no list in chat: " + seen);
			helper.assertTrue(heading.equals(k.want()), k.planks() + " planks, torch " + k.torch() + ": the chat reads '" + heading + "' (want '" + k.want() + "')");
		}
		helper.succeed();
	}

	/**
	 * B402's blueprint tooltip line "The builder's chests are short of %s items (hold Shift)". The report it is drawn from
	 * is made on the server and carried on the blueprint (BlueprintSupplies.tick, as a held blueprint does each 40 ticks):
	 * the hut placed by a bench whose chest holds everything but the torch is one item short, the torch and the door two.
	 * The client draws the line as Words.counted(missing_summary, total) (BlueprintTooltip.missingLines, client code:
	 * read, not run here); this renders that with the carried totals.
	 */
	//$ gametest_ticks_batch AREA '60' '"qaCountsTooltip"'
	@GameTest(template = AREA, timeoutTicks = 60, batch = "qaCountsTooltip")
	public void theTooltipSummaryCountsOneItemAndTwo(GameTestHelper helper) {
		Leftovers.clear(helper);
		ServerLevel level = helper.getLevel();
		helper.setBlock(new BlockPos(2, 2, 2), ModBlocks.BUILDERS_BENCH);
		BlockPos chestPos = new BlockPos(2, 2, 4);
		helper.setBlock(chestPos, Blocks.CHEST);
		Container chest = helper.getBlockEntity(chestPos);
		chest.setItem(0, new ItemStack(Items.COBBLESTONE, 25));
		chest.setItem(1, new ItemStack(Items.OAK_PLANKS, 55));
		chest.setItem(2, new ItemStack(Items.OAK_DOOR));
		Vec3i size = io.github.jcondedata.aliveworkplace.blueprint.BlueprintLibrary.get(level, TEST_HUT).orElseThrow().size();
		ItemStack blueprint = BlueprintItem.create(TEST_HUT, size);
		blueprint.set(ModComponents.BLUEPRINT, BlueprintItem.data(blueprint).orElseThrow().withPlacement(java.util.Optional.of(
			new BlueprintData.Placement(level.dimension().location(), helper.absolutePos(new BlockPos(6, 2, 6)), Rotation.NONE, Mirror.NONE))));
		String[] want = {"The builder's chests are short of 1 item (hold Shift)", "The builder's chests are short of 2 items (hold Shift)"};
		for (int missing = 1; missing <= 2; missing++) {
			if (missing == 2) {
				chest.setItem(2, ItemStack.EMPTY);
			}
			BlueprintSupplies.tick(level, blueprint, (int) ((BlueprintSupplies.EVERY - level.getGameTime() % BlueprintSupplies.EVERY) % BlueprintSupplies.EVERY));
			SupplyReport report = blueprint.get(ModComponents.SUPPLY_REPORT);
			helper.assertTrue(report != null && report.bench().isPresent(), "the blueprint carries no report by the bench: " + report);
			helper.assertTrue(report.totalMissing() == missing, "short of " + report.totalMissing() + " (want " + missing + "): " + report.missing());
			String shown = io.github.jcondedata.aliveworkplace.work.Words.counted("tooltip.aliveworkplace.blueprint.missing_summary", report.totalMissing(),
				report.totalMissing()).getString();
			helper.assertTrue(shown.equals(want[missing - 1]), "short of " + missing + ": '" + shown + "' (want '" + want[missing - 1] + "')");
		}
		helper.succeed();
	}

	// --- B378: the builder's reach -------------------------------------------------------------------------------

	private static final Pattern TOO_FAR = Pattern.compile("That spot is ([0-9]+) blocks from the builder's Blueprint Table — keep it within ([0-9]+)\\.");

	/** A placed test-hut blueprint whose outline's centre is {@code offset} from {@code bench} (as QaBuilderLimitsBugTests). */
	private static ItemStack placedHut(ServerLevel level, BlockPos bench, Vec3i offset) {
		Vec3i size = io.github.jcondedata.aliveworkplace.blueprint.BlueprintLibrary.get(level, TEST_HUT).orElseThrow().size();
		var atBench = new BlueprintData.Placement(level.dimension().location(), bench, Rotation.NONE, Mirror.NONE);
		BlockPos centre = io.github.jcondedata.aliveworkplace.blueprint.BlueprintOutline.bounds(atBench, size).getCenter();
		BlockPos origin = bench.offset(bench.subtract(centre)).offset(offset);
		ItemStack blueprint = BlueprintItem.create(TEST_HUT, size);
		blueprint.set(ModComponents.BLUEPRINT, BlueprintItem.data(blueprint).orElseThrow().withPlacement(java.util.Optional.of(
			new BlueprintData.Placement(level.dimension().location(), origin, Rotation.NONE, Mirror.NONE))));
		return blueprint;
	}

	private static double distance(ServerLevel level, ItemStack blueprint, BlockPos bench) {
		var placement = BlueprintItem.data(blueprint).orElseThrow().placement().orElseThrow();
		Vec3i size = io.github.jcondedata.aliveworkplace.blueprint.BlueprintLibrary.get(level, TEST_HUT).orElseThrow().size();
		return Math.sqrt(io.github.jcondedata.aliveworkplace.blueprint.BlueprintOutline.bounds(placement, size).getCenter().distSqr(bench));
	}

	/**
	 * B378: "whenever the spot is refused, the distance shown is past the reach". Handed to the builder by a right-click:
	 * a spot 60 blocks off is refused with "60 blocks ... within 48"; one a hair past the reach (48.01) is refused with a
	 * number past 48 (49); a spot exactly at the reach (48.0) is taken, with no too-far line.
	 */
	//$ gametest_ticks_batch AREA '60' '"qaCountsReach"'
	@GameTest(template = AREA, timeoutTicks = 60, batch = "qaCountsReach")
	public void theTooFarLineWellPastJustPastAndAtTheReach(GameTestHelper helper) {
		Leftovers.clear(helper);
		noProtection(helper);
		ServerLevel level = helper.getLevel();
		BlockPos table = new BlockPos(2, 2, 2);
		helper.setBlock(table, ModBlocks.BLUEPRINT_TABLE);
		Villager builder = helper.spawn(EntityType.VILLAGER, new BlockPos(3, 2, 3));
		builder.setNoAi(true);
		BlockPos bench = helper.absolutePos(table);
		Builders.employ(level, builder, bench);
		int reach = Builders.MAX_SITE_DISTANCE;
		helper.assertTrue(reach == 48, "the reach is " + reach);
		QaStageFiveGameTests.Client c = QaStageSixGameTests.client(helper, GameType.SURVIVAL, QaStageSixGameTests.name("qacF"));
		ServerPlayer p = c.player();
		record Spot(Vec3i offset, int shown) {
		}
		for (Spot spot : List.of(new Spot(new Vec3i(60, 0, 0), 60), new Spot(new Vec3i(reach, 0, 1), 49))) {
			ItemStack stack = placedHut(level, bench, spot.offset());
			double d = distance(level, stack, bench);
			helper.assertTrue(d > reach, "the spot is " + d + " blocks off");
			c.seen().clear();
			p.setItemInHand(InteractionHand.MAIN_HAND, stack);
			QaStageSixGameTests.clickVillager(p, builder, c.seen());
			helper.assertTrue(Builders.activeSite(level, builder) == null, "a spot " + d + " blocks off was taken");
			helper.assertTrue(p.getMainHandItem().is(ModItems.BLUEPRINT), "the refused blueprint went");
			List<String> said = c.seen().stream().map(Component::getString).toList();
			Matcher m = said.stream().map(TOO_FAR::matcher).filter(Matcher::matches).findFirst().orElse(null);
			helper.assertTrue(m != null, "no too-far line for " + d + " blocks: " + said);
			helper.assertTrue(Integer.parseInt(m.group(1)) == spot.shown() && Integer.parseInt(m.group(2)) == reach,
				String.format("a spot %.3f blocks off reads \"%s\" (want %d blocks, within %d)", d, m.group(0), spot.shown(), reach));
		}
		ItemStack at = placedHut(level, bench, new Vec3i(reach, 0, 0));
		double d = distance(level, at, bench);
		helper.assertTrue(d == reach, "the spot at the reach is " + d + " blocks off");
		c.seen().clear();
		p.setItemInHand(InteractionHand.MAIN_HAND, at);
		QaStageSixGameTests.clickVillager(p, builder, c.seen());
		List<String> said = c.seen().stream().map(Component::getString).toList();
		BuildSite site = Builders.activeSite(level, builder);
		if (site != null) {
			Builders.cancel(level, site);
		}
		helper.assertTrue(said.stream().noneMatch(t -> TOO_FAR.matcher(t).matches()), "a spot exactly at the reach was called too far: " + said);
		helper.assertTrue(site != null, "a spot exactly " + reach + " blocks off wasn't taken: " + said);
		LOGGER.info("qa counts: at the reach the builder said {}", said);
		helper.succeed();
	}

	// --- B403 --------------------------------------------------------------------------------------------------

	/**
	 * B403 at 2: the finished four-plank wall of QaBuilderWordsBugTests' one-block repair, with two planks knocked out:
	 * the owner is told "(2 blocks missing)".
	 */
	//$ gametest_ticks_batch AREA '200' '"qaCountsRepairTwo"'
	@GameTest(template = AREA, timeoutTicks = 200, batch = "qaCountsRepairTwo")
	public void aRepairOfTwoBlocksSaysTwoBlocksMissing(GameTestHelper helper) {
		Leftovers.clear(helper);
		boolean was = io.github.jcondedata.aliveworkplace.build.Upkeep.ENABLED;
		boolean asks = io.github.jcondedata.aliveworkplace.build.Upkeep.ASK;
		io.github.jcondedata.aliveworkplace.build.Upkeep.ENABLED = true;
		io.github.jcondedata.aliveworkplace.build.Upkeep.ASK = false;
		Leftovers.after(helper, () -> {
			io.github.jcondedata.aliveworkplace.build.Upkeep.ENABLED = was;
			io.github.jcondedata.aliveworkplace.build.Upkeep.ASK = asks;
		});
		ServerLevel level = helper.getLevel();
		helper.setDayTime(2000);
		QaStageFiveGameTests.Client c = QaStageSixGameTests.client(helper, GameType.SURVIVAL, QaStageSixGameTests.name("qacP"));
		ServerPlayer owner = c.player();
		BlockPos src = new BlockPos(6, 2, 6);
		for (int x = 0; x < 4; x++) {
			helper.setBlock(src.offset(x, 0, 0), Blocks.OAK_PLANKS);
		}
		ResourceLocation wall = ResourceLocation.fromNamespaceAndPath("aliveworkplace_test", "qa_counts_wall_" + Long.toHexString(level.getGameTime()));
		level.getStructureManager().getOrCreate(wall).fillFromWorld(level, helper.absolutePos(src), new Vec3i(4, 1, 1), false, Blocks.STRUCTURE_VOID);
		helper.setBlock(src.offset(1, 0, 0), Blocks.AIR);
		helper.setBlock(src.offset(2, 0, 0), Blocks.AIR);
		BlockPos chestPos = new BlockPos(2, 2, 4);
		helper.setBlock(chestPos, Blocks.CHEST);
		Container chest = helper.getBlockEntity(chestPos);
		chest.setItem(0, new ItemStack(Items.OAK_PLANKS, 4));
		BlockPos bench = new BlockPos(2, 2, 2);
		helper.setBlock(bench, ModBlocks.BUILDERS_BENCH);
		Villager builder = helper.spawn(EntityType.VILLAGER, new BlockPos(3, 2, 3));
		Builders.employ(level, builder, helper.absolutePos(bench));
		io.github.jcondedata.aliveworkplace.registry.ModAttachments.BUILDER_EMPLOYER.set(builder,
			new io.github.jcondedata.aliveworkplace.build.Employer(owner.getUUID(), owner.getGameProfile().getName()));
		var placement = new BlueprintData.Placement(level.dimension().location(), helper.absolutePos(src), Rotation.NONE, Mirror.NONE);
		io.github.jcondedata.aliveworkplace.build.BuildSiteManager.get(level).recordFinished(wall, placement, owner.getUUID());
		helper.runAfterDelay(2, () -> {
			if (!io.github.jcondedata.aliveworkplace.registry.ModAttachments.BUILDER_JOB.has(builder)) {
				BuildSite site = io.github.jcondedata.aliveworkplace.build.Upkeep.look(level, builder);
				helper.assertTrue(site != null && site.isRepair(), "no repair started");
			}
		});
		helper.succeedWhen(() -> {
			List<String> seen = c.seen().stream().map(Component::getString).toList();
			String line = seen.stream().filter(t -> t.contains(" is repairing the ")).findFirst().orElse(null);
			helper.assertTrue(line != null, "the owner wasn't told of the repair: " + seen);
			helper.assertTrue(line.endsWith("(2 blocks missing)"), "a repair of two blocks reads '" + line + "' (want '... (2 blocks missing)')");
		});
	}

	/**
	 * B403 at 2: two iron swords in a Drop Box by the Storehouse; once the porter has taken both to the store his title
	 * reads "Porter · 2 items carried".
	 */
	//$ gametest_ticks_batch BIG '1200' '"qaCountsPorterTwo"'
	@GameTest(template = BIG, timeoutTicks = 1200, batch = "qaCountsPorterTwo")
	public void aPorterWhoCarriedTwoItemsSaysTwoItemsCarried(GameTestHelper helper) {
		Leftovers.clear(helper);
		helper.setDayTime(2000);
		BlockPos boxPos = new BlockPos(8, 2, 8);
		helper.setBlock(boxPos, ModBlocks.DROP_BOX);
		Container box = helper.getBlockEntity(boxPos);
		box.setItem(0, new ItemStack(Items.IRON_SWORD));
		box.setItem(1, new ItemStack(Items.IRON_SWORD));
		BlockPos storehouse = new BlockPos(19, 2, 19);
		BlockPos storeChest = new BlockPos(19, 2, 17);
		helper.setBlock(storehouse, ModBlocks.STOREHOUSE);
		helper.setBlock(storeChest, Blocks.CHEST);
		Villager porter = helper.spawn(EntityType.VILLAGER, new BlockPos(18, 2, 18));
		io.github.jcondedata.aliveworkplace.store.Porters.employ(helper.getLevel(), porter, helper.absolutePos(storehouse));
		helper.succeedWhen(() -> {
			Container store = helper.getBlockEntity(storeChest);
			helper.assertTrue(store.countItem(Items.IRON_SWORD) == 2, "the swords aren't in the store yet");
			int carried = io.github.jcondedata.aliveworkplace.registry.ModAttachments.ITEMS_CARRIED.getOrElse(porter, 0);
			helper.assertTrue(carried == 2, "carried " + carried);
			var entry = io.github.jcondedata.aliveworkplace.work.WorkerStatus.get(porter, helper.getLevel().getGameTime());
			helper.assertTrue(entry != null, "no status for the porter");
			String title = entry.title().getString();
			helper.assertTrue(title.contains("2 items carried"), "a porter who carried two items reads '" + title + "' (want 'Porter · 2 items carried')");
		});
	}
}
