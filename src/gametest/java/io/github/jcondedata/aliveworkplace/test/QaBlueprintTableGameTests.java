package io.github.jcondedata.aliveworkplace.test;

import io.github.jcondedata.aliveworkplace.AliveWorkplace;
import io.github.jcondedata.aliveworkplace.blueprint.Blueprint;
import io.github.jcondedata.aliveworkplace.blueprint.BlueprintItem;
import io.github.jcondedata.aliveworkplace.blueprint.BlueprintLibrary;
import io.github.jcondedata.aliveworkplace.blueprint.Blueprints;
import io.github.jcondedata.aliveworkplace.blueprint.StarterBlueprints;
import io.github.jcondedata.aliveworkplace.registry.ModBlocks;
import io.github.jcondedata.aliveworkplace.registry.ModItems;
import io.github.jcondedata.aliveworkplace.table.BlueprintTableBlockEntity;
import io.github.jcondedata.aliveworkplace.table.BuildJobs;
import io.github.jcondedata.aliveworkplace.table.TableLibrary;
import io.github.jcondedata.aliveworkplace.table.TablePayloads;
import io.github.jcondedata.aliveworkplace.table.TableSearch;
import io.github.jcondedata.aliveworkplace.table.TableServer;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Vec3i;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * QA (lane-b-1024-1237) for the Blueprint Table's library (36.13), search (36.10) and basement preview (36.1), written
 * from the ROADMAP specs: "stores bought blueprints ... sees them listed with a count, and takes one back out whenever they
 * like"; "Stored blueprints are the table's own (a broken table drops them; the table saves them across restarts)";
 * "typing filters the library live by name, id and the jobs a build brings ... case-insensitive, with the style/level
 * rows kept under their build"; "the preview payload gets the basement's depth ... cells use floor division ... the size
 * line adds 'and N below ground'". The library goes through the packets a client sends ({@link QaStageFiveGameTests.Client})
 * and the Open payload the Stored tab is drawn from; the search through the listing the server sends the screen.
 */
public class QaBlueprintTableGameTests implements FabricGameTest {
	private static final String AREA = "aliveworkplace_test:build_area";
	private static final BlockPos TABLE = new BlockPos(3, 2, 3);
	private static final ResourceLocation COTTAGE = StarterBlueprints.STARTER_COTTAGE.id();
	private static final ResourceLocation STALL = StarterBlueprints.MARKET_STALL.id();
	private static final ResourceLocation INN = AliveWorkplace.id("inn");
	private static final ResourceLocation FISHERS_HUT = AliveWorkplace.id("fishers_hut");

	private static ItemStack blueprint(ResourceLocation id) {
		return BlueprintItem.create(id, null);
	}

	private static boolean isBlueprintOf(ItemStack stack, ResourceLocation id) {
		return BlueprintItem.data(stack).map(d -> d.structure().equals(id)).orElse(false);
	}

	private static int carried(ServerPlayer player, ResourceLocation id) {
		int n = 0;
		for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
			ItemStack stack = player.getInventory().getItem(i);
			if (isBlueprintOf(stack, id)) {
				n += stack.getCount();
			}
		}
		return n;
	}

	/** How many of {@code id} the given Stored-tab payload lists. */
	private static int listed(TablePayloads.Open open, ResourceLocation id) {
		return open.stored().stream().filter(s -> isBlueprintOf(s.stack(), id)).mapToInt(TablePayloads.Stored::count).sum();
	}

	/** The last Stored-tab payload the server sent this client since the last read. */
	private static TablePayloads.Open lastOpen(GameTestHelper helper, QaStageFiveGameTests.Client c, String when) {
		List<TablePayloads.Open> opens = c.sent(TablePayloads.Open.class);
		helper.assertTrue(!opens.isEmpty(), when + ": the server didn't refresh the table's screen");
		return opens.get(opens.size() - 1);
	}

	private static QaStageFiveGameTests.Client clientAtTable(GameTestHelper helper) {
		helper.setBlock(TABLE, ModBlocks.BLUEPRINT_TABLE);
		QaStageFiveGameTests.Client c = QaStageFiveGameTests.client(helper, GameType.SURVIVAL);
		c.player().getInventory().clearContent();
		c.player().getInventory().selected = 0;
		c.player().moveTo(Vec3.atBottomCenterOf(helper.absolutePos(TABLE)).add(1.5, 0, 0));
		c.sent();
		c.seen().clear();
		return c;
	}

	private static BlueprintTableBlockEntity shelf(GameTestHelper helper, BlockPos rel) {
		BlockEntity entity = helper.getBlockEntity(rel);
		helper.assertTrue(entity instanceof BlueprintTableBlockEntity, "no Blueprint Table shelf at " + rel + ": " + entity);
		return (BlueprintTableBlockEntity) entity;
	}

	/** How many sounds the server played to this client since the last read (the custom payloads read are dropped). */
	private static int heard(QaStageFiveGameTests.Client c) {
		int n = 0;
		Object msg;
		while ((msg = c.channel().readOutbound()) != null) {
			if (msg instanceof net.minecraft.network.protocol.game.ClientboundSoundPacket) {
				n++;
			}
		}
		return n;
	}

	private static String english(Component c) {
		return JewellerGameTests.english(c);
	}

	private static String englishOf(List<Component> seen, String key) {
		Component c = QaStageFourGameTests.find(seen, key);
		return c == null ? null : english(c);
	}

	private static List<ItemEntity> blueprintDrops(GameTestHelper helper, BlockPos rel) {
		return helper.getLevel().getEntitiesOfClass(ItemEntity.class, new AABB(helper.absolutePos(rel)).inflate(3), e -> e.getItem().is(ModItems.BLUEPRINT));
	}

	private static int dropped(List<ItemEntity> drops, ResourceLocation id) {
		return drops.stream().filter(e -> isBlueprintOf(e.getItem(), id)).mapToInt(e -> e.getItem().getCount()).sum();
	}

	// --- 36.13: the library --------------------------------------------------------------------------------------

	/**
	 * Store held, then Store All, then Take One Out until one blueprint is gone, all as packets: every refresh of the
	 * Stored tab lists each blueprint with the right count; the last of a kind taken out leaves the list; one never
	 * stored can't be taken; the action-bar lines read in English with the real names and numbers.
	 */
	//$ gametest_batch AREA '"qaBtPackets"'
	@GameTest(template = AREA, batch = "qaBtPackets")
	public void storedTabCountsTwoBlueprintsDownToTheLastOne(GameTestHelper helper) {
		Leftovers.clear(helper);
		QaStageFiveGameTests.Client c = clientAtTable(helper);
		ServerPlayer p = c.player();
		BlockPos table = helper.absolutePos(TABLE);
		p.getInventory().setItem(0, blueprint(COTTAGE));
		c.send(new TablePayloads.Store(table, false));
		TablePayloads.Open open = lastOpen(helper, c, "store held");
		helper.assertTrue(open.storedCount() == 1 && listed(open, COTTAGE) == 1 && open.stored().size() == 1,
			"after storing the held cottage the tab lists " + open.stored() + " (" + open.storedCount() + " in all)");
		String one = englishOf(c.seen(), "message.aliveworkplace.table.stored");
		helper.assertTrue(("Stored " + blueprint(COTTAGE).getHoverName().getString() + " in the table").equals(one), "storing one reads: " + one);
		helper.assertTrue(p.getInventory().getItem(0).isEmpty(), "the stored cottage is still in hand");
		// Store All: two stalls and one more cottage, scattered over the bag and the hotbar.
		p.getInventory().setItem(4, blueprint(STALL));
		p.getInventory().setItem(22, blueprint(COTTAGE));
		p.getInventory().setItem(35, blueprint(STALL));
		c.seen().clear();
		c.send(new TablePayloads.Store(table, true));
		open = lastOpen(helper, c, "store all");
		helper.assertTrue(open.stored().size() == 2 && listed(open, COTTAGE) == 2 && listed(open, STALL) == 2 && open.storedCount() == 4,
			"after Store All: cottage x" + listed(open, COTTAGE) + ", stall x" + listed(open, STALL) + ", " + open.storedCount() + " in all; expected 2, 2, 4");
		String many = englishOf(c.seen(), "message.aliveworkplace.table.stored_many");
		helper.assertTrue("Stored 3 blueprints in the table".equals(many), "Store All of 3 reads: " + many + " (saw " + QaStageFiveGameTests.keys(c.seen()) + ")");
		helper.assertTrue(english(Component.translatable("screen.aliveworkplace.table.tab_stored", open.storedCount())).equals("Stored (4)"), "the tab's title with the real count");
		helper.assertTrue(english(Component.translatable("screen.aliveworkplace.table.stored_count", open.storedCount(), open.capacity())).equals("4 of 200 stored"),
			"the count line with the real numbers");
		// Take the stalls out one by one; the second empties that row.
		c.seen().clear();
		c.send(new TablePayloads.TakeStored(table, blueprint(STALL)));
		open = lastOpen(helper, c, "first take");
		helper.assertTrue(carried(p, STALL) == 1 && listed(open, STALL) == 1 && open.storedCount() == 3,
			"after one take: carried " + carried(p, STALL) + ", listed " + listed(open, STALL) + ", " + open.storedCount() + " in all");
		String took = englishOf(c.seen(), "message.aliveworkplace.table.took_out");
		helper.assertTrue(("Took " + blueprint(STALL).getHoverName().getString() + " out of the table").equals(took), "taking one out reads: " + took);
		c.send(new TablePayloads.TakeStored(table, blueprint(STALL)));
		open = lastOpen(helper, c, "last take");
		helper.assertTrue(carried(p, STALL) == 2 && listed(open, STALL) == 0 && open.stored().size() == 1 && listed(open, COTTAGE) == 2,
			"after taking the last stall the tab lists " + open.stored() + "; carried " + carried(p, STALL));
		// None left of it: refused, nothing more comes, the cottages stay.
		c.seen().clear();
		c.send(new TablePayloads.TakeStored(table, blueprint(STALL)));
		helper.assertTrue(carried(p, STALL) == 2, "a third stall came out of a table that held two");
		helper.assertTrue(QaStageFourGameTests.saw(c.seen(), "message.aliveworkplace.table.not_stored"), "not told it isn't stored: " + QaStageFiveGameTests.keys(c.seen()));
		helper.assertTrue(shelf(helper, TABLE).count() == 2 && carried(p, COTTAGE) == 0, "the cottages moved: shelf " + shelf(helper, TABLE).count());
		helper.succeed();
	}

	/**
	 * The full-inventory boundary from both sides: one free slot left, the blueprint comes out into it; no free slot
	 * (the off hand empty), it stays in the table and nothing lands on the floor; nothing is ever lost or doubled.
	 */
	//$ gametest_batch AREA '"qaBtFullBag"'
	@GameTest(template = AREA, batch = "qaBtFullBag")
	public void theLastFreeSlotTakesOneAndAFullBagLosesNothing(GameTestHelper helper) {
		Leftovers.clear(helper);
		QaStageFiveGameTests.Client c = clientAtTable(helper);
		ServerPlayer p = c.player();
		BlockPos table = helper.absolutePos(TABLE);
		BlueprintTableBlockEntity shelf = shelf(helper, TABLE);
		shelf.put(blueprint(COTTAGE));
		shelf.put(blueprint(COTTAGE));
		for (int i = 0; i < p.getInventory().items.size(); i++) {
			p.getInventory().items.set(i, i == 17 ? ItemStack.EMPTY : new ItemStack(Items.DIRT, 64));
		}
		c.send(new TablePayloads.TakeStored(table, blueprint(COTTAGE)));
		helper.assertTrue(isBlueprintOf(p.getInventory().getItem(17), COTTAGE), "the one free slot didn't take the cottage: " + p.getInventory().getItem(17));
		helper.assertTrue(shelf.count() == 1, "the table holds " + shelf.count() + " after one take, expected 1");
		// Now the bag is full.
		c.seen().clear();
		c.send(new TablePayloads.TakeStored(table, blueprint(COTTAGE)));
		int inBag = carried(p, COTTAGE) + (isBlueprintOf(p.getOffhandItem(), COTTAGE) ? 1 : 0);
		int onFloor = dropped(blueprintDrops(helper, TABLE), COTTAGE) + QaStageFourGameTests.dropped(helper, p, ModItems.BLUEPRINT).size();
		helper.assertTrue(inBag + shelf.count() == 2 && onFloor == 0, "a full bag: " + inBag + " carried, " + shelf.count() + " in the table, " + onFloor
			+ " on the floor; expected 2 in all, none dropped");
		helper.assertTrue(shelf.count() == 1, "the blueprint left the table into a full bag");
		String full = englishOf(c.seen(), "message.aliveworkplace.table.take_out_full");
		helper.assertTrue("Your inventory is full: the blueprint stays in the table".equals(full), "a full bag reads: " + full);
		helper.succeed();
	}

	/**
	 * The table's size from both sides with the Store-held packet: at 199 the held blueprint goes in; at 200 it is
	 * refused, stays in hand, and the player is told the real limit; taking one out makes room again.
	 */
	//$ gametest_batch AREA '"qaBtCapacity"'
	@GameTest(template = AREA, batch = "qaBtCapacity")
	public void aFullTableRefusesTheHeldBlueprintUntilOneIsTaken(GameTestHelper helper) {
		Leftovers.clear(helper);
		QaStageFiveGameTests.Client c = clientAtTable(helper);
		ServerPlayer p = c.player();
		BlockPos table = helper.absolutePos(TABLE);
		BlueprintTableBlockEntity shelf = shelf(helper, TABLE);
		for (int i = 0; i < BlueprintTableBlockEntity.CAPACITY - 1; i++) {
			shelf.put(blueprint(STALL));
		}
		p.getInventory().setItem(0, blueprint(COTTAGE));
		c.sent();
		c.send(new TablePayloads.Store(table, false));
		helper.assertTrue(shelf.count() == BlueprintTableBlockEntity.CAPACITY && p.getMainHandItem().isEmpty(), "at 199 the held cottage should go in: " + shelf.count());
		helper.assertTrue(heard(c) > 0, "no sound when the blueprint went in");
		p.getInventory().setItem(0, blueprint(COTTAGE));
		c.seen().clear();
		c.send(new TablePayloads.Store(table, false));
		helper.assertTrue(heard(c) == 0, "a sound for a store the full table refused");
		c.send(new TablePayloads.Store(table, false)); // asked again, to read the screen's refresh (heard() dropped it)
		helper.assertTrue(shelf.count() == BlueprintTableBlockEntity.CAPACITY, "the table went past its size: " + shelf.count());
		helper.assertTrue(isBlueprintOf(p.getMainHandItem(), COTTAGE), "the refused blueprint left the hand: " + p.getMainHandItem());
		String full = englishOf(c.seen(), "message.aliveworkplace.table.store_full");
		helper.assertTrue("The table is full: it holds 200 blueprints at most".equals(full), "a full table reads: " + full);
		TablePayloads.Open open = lastOpen(helper, c, "a full table");
		helper.assertTrue(open.storedCount() == 200 && listed(open, COTTAGE) == 1 && listed(open, STALL) == 199, "a full table lists " + open.storedCount());
		// One out, then the held one fits.
		p.getInventory().setItem(1, ItemStack.EMPTY);
		c.send(new TablePayloads.TakeStored(table, blueprint(STALL)));
		helper.assertTrue(shelf.count() == 199 && carried(p, STALL) == 1, "one stall should come out of a full table");
		p.getInventory().selected = 0;
		c.send(new TablePayloads.Store(table, false));
		helper.assertTrue(shelf.count() == 200 && carried(p, COTTAGE) == 0, "with room again the held cottage should go in: " + shelf.count());
		helper.succeed();
	}

	/** A stack of blueprints in hand (as a command or another mod can give) goes in one by one, and comes out one at a time. */
	//$ gametest_batch AREA '"qaBtStack"'
	@GameTest(template = AREA, batch = "qaBtStack")
	public void aStackInHandStoresEachBlueprintAndTakesOutOne(GameTestHelper helper) {
		Leftovers.clear(helper);
		QaStageFiveGameTests.Client c = clientAtTable(helper);
		ServerPlayer p = c.player();
		BlockPos table = helper.absolutePos(TABLE);
		ItemStack three = blueprint(COTTAGE);
		three.setCount(3);
		p.getInventory().setItem(0, three);
		c.send(new TablePayloads.Store(table, false));
		TablePayloads.Open open = lastOpen(helper, c, "store a stack");
		helper.assertTrue(listed(open, COTTAGE) == 3 && open.storedCount() == 3 && p.getMainHandItem().isEmpty(),
			"a stack of 3 in hand: listed x" + listed(open, COTTAGE) + ", in hand " + p.getMainHandItem());
		c.sent();
		c.send(new TablePayloads.TakeStored(table, blueprint(COTTAGE).copyWithCount(3))); // "three" itself was emptied by the store
		helper.assertTrue(carried(p, COTTAGE) == 1 && shelf(helper, TABLE).count() == 2, "Take One Out gave " + carried(p, COTTAGE) + ", left " + shelf(helper, TABLE).count());
		// The player hears it come out (and hears nothing when a take is refused).
		helper.assertTrue(heard(c) > 0, "no sound when a blueprint came out of the table");
		c.send(new TablePayloads.TakeStored(table, blueprint(STALL)));
		helper.assertTrue(heard(c) == 0, "a sound for a take that was refused");
		helper.succeed();
	}

	/**
	 * A survival player breaking the table with their hands gets exactly what was stored on the floor (no more, no
	 * fewer); a new table put in its place holds nothing; a table replaced by another block drops its blueprints too.
	 */
	//$ gametest_batch AREA '"qaBtBreak"'
	@GameTest(template = AREA, batch = "qaBtBreak")
	public void aTableBrokenByAPlayerDropsExactlyWhatItHeld(GameTestHelper helper) {
		Leftovers.clear(helper);
		QaStageFiveGameTests.Client c = clientAtTable(helper);
		ServerPlayer p = c.player();
		BlockPos table = helper.absolutePos(TABLE);
		p.getInventory().setItem(0, blueprint(COTTAGE));
		p.getInventory().setItem(1, blueprint(STALL));
		p.getInventory().setItem(2, blueprint(COTTAGE));
		p.getInventory().setItem(3, blueprint(COTTAGE));
		c.send(new TablePayloads.Store(table, true));
		helper.assertTrue(shelf(helper, TABLE).count() == 4, "Store All stored " + shelf(helper, TABLE).count());
		helper.assertTrue(p.gameMode.destroyBlock(table), "the player couldn't break the table");
		helper.assertBlockPresent(Blocks.AIR, TABLE);
		List<ItemEntity> drops = blueprintDrops(helper, TABLE);
		int total = drops.stream().mapToInt(e -> e.getItem().getCount()).sum();
		helper.assertTrue(dropped(drops, COTTAGE) == 3 && dropped(drops, STALL) == 1 && total == 4,
			"the broken table dropped " + dropped(drops, COTTAGE) + " cottages, " + dropped(drops, STALL) + " stalls, " + total + " blueprints in all; expected 3, 1, 4");
		drops.forEach(ItemEntity::discard);
		helper.setBlock(TABLE, ModBlocks.BLUEPRINT_TABLE);
		helper.assertTrue(shelf(helper, TABLE).count() == 0, "a new table in the broken one's place holds " + shelf(helper, TABLE).count());
		// Replaced by another block (as /setblock does): its blueprints still drop.
		shelf(helper, TABLE).put(blueprint(STALL));
		helper.setBlock(TABLE, Blocks.STONE);
		List<ItemEntity> again = blueprintDrops(helper, TABLE);
		helper.assertTrue(dropped(again, STALL) == 1 && again.size() == 1, "a table replaced by stone dropped " + again.size() + " blueprints, expected its 1 stall");
		again.forEach(ItemEntity::discard);
		helper.succeed();
	}

	/**
	 * Two tables side by side keep their own blueprints: storing at one never shows at the other, and through a save and
	 * reload each keeps its own blueprints, counts and order (three kinds, x3, x1, x2).
	 */
	//$ gametest_batch AREA '"qaBtTwoTables"'
	@GameTest(template = AREA, batch = "qaBtTwoTables")
	public void twoTablesKeepTheirOwnBlueprintsThroughAReload(GameTestHelper helper) {
		Leftovers.clear(helper);
		QaStageFiveGameTests.Client c = clientAtTable(helper);
		ServerPlayer p = c.player();
		BlockPos otherRel = new BlockPos(5, 2, 3);
		helper.setBlock(otherRel, ModBlocks.BLUEPRINT_TABLE);
		BlockPos a = helper.absolutePos(TABLE);
		BlockPos b = helper.absolutePos(otherRel);
		ResourceLocation inn = INN;
		p.getInventory().setItem(0, blueprint(COTTAGE));
		p.getInventory().setItem(1, blueprint(inn));
		p.getInventory().setItem(2, blueprint(COTTAGE));
		p.getInventory().setItem(3, blueprint(STALL));
		p.getInventory().setItem(4, blueprint(inn));
		p.getInventory().setItem(5, blueprint(COTTAGE));
		c.send(new TablePayloads.Store(a, true));
		p.getInventory().setItem(0, blueprint(STALL));
		c.send(new TablePayloads.Store(b, false));
		TablePayloads.Open atA = TableServer.openPayload(p, a);
		TablePayloads.Open atB = TableServer.openPayload(p, b);
		helper.assertTrue(atA.storedCount() == 6 && listed(atA, COTTAGE) == 3 && listed(atA, inn) == 2 && listed(atA, STALL) == 1,
			"table A lists " + atA.storedCount() + ": cottage x" + listed(atA, COTTAGE) + ", inn x" + listed(atA, inn) + ", stall x" + listed(atA, STALL));
		helper.assertTrue(atB.storedCount() == 1 && listed(atB, STALL) == 1 && listed(atB, COTTAGE) == 0, "table B shares A's blueprints: " + atB.stored());
		var registries = helper.getLevel().registryAccess();
		for (BlockPos rel : List.of(TABLE, otherRel)) {
			BlueprintTableBlockEntity before = shelf(helper, rel);
			CompoundTag tag = before.saveWithFullMetadata(registries);
			BlockEntity back = BlockEntity.loadStatic(before.getBlockPos(), before.getBlockState(), tag, registries);
			helper.assertTrue(back instanceof BlueprintTableBlockEntity, rel + ": the shelf didn't load");
			List<BlueprintTableBlockEntity.Group> g0 = before.groups();
			List<BlueprintTableBlockEntity.Group> g1 = ((BlueprintTableBlockEntity) back).groups();
			helper.assertTrue(g0.size() == g1.size(), rel + ": " + g0.size() + " kinds before the reload, " + g1.size() + " after");
			for (int i = 0; i < g0.size(); i++) {
				helper.assertTrue(g0.get(i).count() == g1.get(i).count() && ItemStack.isSameItemSameComponents(g0.get(i).stack(), g1.get(i).stack()),
					rel + ": kind " + i + " was x" + g0.get(i).count() + ", reloaded x" + g1.get(i).count());
			}
		}
		List<BlueprintTableBlockEntity.Group> order = shelf(helper, TABLE).groups();
		helper.assertTrue(isBlueprintOf(order.get(0).stack(), COTTAGE) && isBlueprintOf(order.get(1).stack(), inn) && isBlueprintOf(order.get(2).stack(), STALL),
			"table A's kinds aren't in the order they went in");
		helper.succeed();
	}

	/** The blueprints are the table's own: a second player at the table sees what the first stored and can take one out. */
	//$ gametest_batch AREA '"qaBtTwoPlayers"'
	@GameTest(template = AREA, batch = "qaBtTwoPlayers")
	public void anotherPlayerSeesAndTakesTheTablesBlueprints(GameTestHelper helper) {
		Leftovers.clear(helper);
		QaStageFiveGameTests.Client first = clientAtTable(helper);
		BlockPos table = helper.absolutePos(TABLE);
		first.player().getInventory().setItem(0, blueprint(COTTAGE));
		first.player().getInventory().setItem(1, blueprint(COTTAGE));
		first.send(new TablePayloads.Store(table, true));
		QaStageFiveGameTests.Client second = QaStageFiveGameTests.client(helper, GameType.SURVIVAL);
		second.player().getInventory().clearContent();
		second.player().moveTo(Vec3.atBottomCenterOf(table).add(-1.5, 0, 0));
		second.sent();
		helper.assertTrue(listed(TableServer.openPayload(second.player(), table), COTTAGE) == 2, "the second player doesn't see the 2 stored cottages");
		second.send(new TablePayloads.TakeStored(table, blueprint(COTTAGE)));
		helper.assertTrue(carried(second.player(), COTTAGE) == 1, "the second player couldn't take a cottage out");
		helper.assertTrue(listed(TableServer.openPayload(first.player(), table), COTTAGE) == 1, "the first player's view didn't count down to 1");
		helper.assertTrue(carried(first.player(), COTTAGE) == 0, "the first player got a blueprint back");
		helper.succeed();
	}

	// --- 36.10: the search ---------------------------------------------------------------------------------------

	private static List<TablePayloads.Entry> library(GameTestHelper helper) {
		return TableServer.listing(helper.getLevel().getServer());
	}

	private static List<ResourceLocation> ids(List<TablePayloads.Entry> entries) {
		return entries.stream().map(TablePayloads.Entry::id).toList();
	}

	/** Case and the spaces, tabs and line breaks round a search never change what it finds; words may come from name and job. */
	//$ gametest 'FabricGameTest.EMPTY_STRUCTURE'
	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
	public void searchIgnoresCaseAndWhitespaceAndMixesFields(GameTestHelper helper) {
		List<TablePayloads.Entry> all = library(helper);
		List<TablePayloads.Entry> byId = TableSearch.filter(all, "fishers_hut");
		helper.assertTrue(ids(byId).contains(FISHERS_HUT), "\"fishers_hut\" doesn't find the hut");
		for (String q : List.of("FiShErS_HuT", "\tfishers_hut\n", "  FISHERS_HUT  ", "ALIVEWORKPLACE:Fishers_Hut")) {
			List<TablePayloads.Entry> got = TableSearch.filter(all, q);
			helper.assertTrue(ids(got).contains(FISHERS_HUT), "\"" + q.replace("\t", "\\t").replace("\n", "\\n") + "\" misses the Fisher's Hut: " + ids(got));
			if (!q.contains(":")) {
				helper.assertTrue(got.equals(byId), "\"" + q.replace("\t", "\\t").replace("\n", "\\n") + "\" lists something else than \"fishers_hut\"");
			}
		}
		String jobName = BuildJobs.name(ResourceLocation.withDefaultNamespace("fisherman")).getString();
		helper.assertTrue(TableSearch.filter(all, jobName.toUpperCase(Locale.ROOT)).equals(TableSearch.filter(all, jobName.toLowerCase(Locale.ROOT))),
			"the job's name finds different builds in upper and lower case");
		// One word from the name, one from a job, either order.
		String nameWord = Blueprints.displayName(FISHERS_HUT).getString().split("\\s+")[0];
		for (String q : List.of(nameWord + " " + jobName, jobName.toUpperCase(Locale.ROOT) + "   " + nameWord.toLowerCase(Locale.ROOT))) {
			helper.assertTrue(ids(TableSearch.filter(all, q)).contains(FISHERS_HUT), "\"" + q + "\" (a name word and a job) misses the Fisher's Hut");
		}
		helper.succeed();
	}

	/** A search only a level's id matches ("inn_3") still lists the build first with all its levels under it, in order. */
	//$ gametest 'FabricGameTest.EMPTY_STRUCTURE'
	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
	public void aSearchOnlyALevelMatchesKeepsItsBuildAbove(GameTestHelper helper) {
		List<TablePayloads.Entry> all = library(helper);
		TablePayloads.Entry inn = all.stream().filter(e -> e.id().equals(INN)).findFirst().orElseThrow();
		helper.assertFalse(TableSearch.matches(inn, "inn_3"), "the Inn itself matches \"inn_3\": pick a query only the level matches");
		for (String q : List.of("inn_3", "INN_3", " aliveworkplace:inn_3 ")) {
			List<ResourceLocation> got = ids(TableSearch.filter(all, q));
			int at = got.indexOf(INN);
			helper.assertTrue(at >= 0, "\"" + q + "\" lists inn_3 without the Inn: " + got);
			helper.assertTrue(got.size() > at + 2 && got.get(at + 1).equals(AliveWorkplace.id("inn_2")) && got.get(at + 2).equals(AliveWorkplace.id("inn_3")),
				"\"" + q + "\" should list Inn, Inn II, Inn III together: " + got);
			helper.assertFalse(got.contains(COTTAGE), "\"" + q + "\" lists the Starter Cottage");
		}
		helper.succeed();
	}

	/**
	 * A build whose job block is shared by several jobs (the detail pane shows "A/B") is found by each of those jobs'
	 * names and ids, in any case.
	 */
	//$ gametest 'FabricGameTest.EMPTY_STRUCTURE'
	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
	public void eachJobOfASharedJobBlockFindsTheBuild(GameTestHelper helper) {
		List<TablePayloads.Entry> all = library(helper);
		int checked = 0;
		List<String> problems = new ArrayList<>();
		for (TablePayloads.Entry e : all) {
			if (e.hidden()) {
				continue;
			}
			Optional<TablePayloads.Details> details = TableServer.details(helper.getLevel(), null, e.id());
			if (details.isEmpty()) {
				continue;
			}
			for (TablePayloads.JobBlock block : details.get().jobs()) {
				if (block.jobs().size() < 2) {
					continue;
				}
				checked++;
				for (ResourceLocation job : block.jobs()) {
					for (String q : List.of(BuildJobs.name(job).getString().toUpperCase(Locale.ROOT), job.getPath())) {
						if (!TableSearch.filter(all, q).contains(e)) {
							problems.add("\"" + q + "\" misses " + e.id());
						}
					}
				}
			}
			if (checked >= 6) {
				break;
			}
		}
		helper.assertTrue(checked > 0, "no listed build has a job block shared by two jobs: pick another check");
		helper.assertTrue(problems.isEmpty(), String.join("; ", problems));
		helper.succeed();
	}

	// --- 36.1: the basement preview ------------------------------------------------------------------------------

	private static List<BlockPos> cells(TablePayloads.Preview preview) {
		List<BlockPos> out = new ArrayList<>();
		int[] b = preview.blocks();
		for (int i = 0; i < b.length; i += 4) {
			out.add(new BlockPos(b[i], b[i + 1], b[i + 2]));
		}
		return out;
	}

	/**
	 * Every shipped build with a basement previews its whole depth (the payload's depth is the basement's, the deepest
	 * cell is the basement's floor, the size above ground unchanged); every other shipped build has no depth and no cell
	 * under the ground.
	 */
	//$ gametest 'FabricGameTest.EMPTY_STRUCTURE'
	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
	public void everyBasementBuildPreviewsItsWholeDepth(GameTestHelper helper) {
		MinecraftServer server = helper.getLevel().getServer();
		List<String> problems = new ArrayList<>();
		Set<ResourceLocation> withBasement = new HashSet<>();
		int basements = 0;
		for (StarterBlueprints.Entry entry : StarterBlueprints.WITH_BASEMENTS) {
			withBasement.add(entry.id());
			Optional<TablePayloads.Preview> preview = TableServer.preview(server, entry.id(), true);
			Optional<Blueprint> bp = BlueprintLibrary.get(server, entry.id());
			if (preview.isEmpty() || bp.isEmpty()) {
				continue;
			}
			basements++;
			TablePayloads.Preview pv = preview.get();
			int depth = StarterBlueprints.basement(entry.id());
			int deepest = cells(pv).stream().mapToInt(BlockPos::getY).min().orElse(0);
			if (pv.below() != depth) {
				problems.add(entry.id().getPath() + ": depth " + pv.below() + ", basement " + depth);
			}
			if (deepest != Math.floorDiv(-depth, pv.scale())) {
				problems.add(entry.id().getPath() + ": deepest cell y " + deepest + " at scale " + pv.scale() + ", expected " + Math.floorDiv(-depth, pv.scale()));
			}
			if (pv.sizeY() != bp.get().size().getY()) {
				problems.add(entry.id().getPath() + ": height above ground " + pv.sizeY() + ", blueprint " + bp.get().size().getY());
			}
		}
		helper.assertTrue(basements >= 5, "only " + basements + " basement builds have a preview");
		int plain = 0;
		for (TablePayloads.Entry e : library(helper)) {
			if (e.hidden() || withBasement.contains(e.id()) || !e.id().getNamespace().equals(AliveWorkplace.MOD_ID)) {
				continue;
			}
			Optional<TablePayloads.Preview> preview = TableServer.preview(server, e.id(), true);
			if (preview.isEmpty()) {
				continue;
			}
			plain++;
			if (preview.get().below() != 0 || cells(preview.get()).stream().anyMatch(p -> p.getY() < 0)) {
				problems.add(e.id().getPath() + ": no basement, yet depth " + preview.get().below() + " or a cell below ground");
			}
		}
		helper.assertTrue(plain > 20, "only " + plain + " builds without a basement checked");
		helper.assertTrue(problems.isEmpty(), problems.size() + " wrong: " + String.join("; ", problems.subList(0, Math.min(6, problems.size()))));
		helper.succeed();
	}

	/**
	 * Floor division from both sides at scale 2 and 3, and the depth stays in blocks at any scale: a cellar block at
	 * y = -1, -2 and -3 lands in cell floor(y / scale), never in the ground floor's; Starter Cottage IV's depth is 4 at
	 * scale 1 and 2.
	 */
	//$ gametest 'FabricGameTest.EMPTY_STRUCTURE'
	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
	public void cellarCellsFloorAtEveryScaleAndTheDepthStaysInBlocks(GameTestHelper helper) {
		List<String> problems = new ArrayList<>();
		for (int y : new int[] {-1, -2, -3}) {
			for (int scale : new int[] {1, 2, 3}) {
				List<Blueprint.Entry> blocks = List.of(
					new Blueprint.Entry(new BlockPos(0, y, 0), Blocks.STONE.defaultBlockState(), null),
					new Blueprint.Entry(new BlockPos(0, 0, 0), Blocks.OAK_PLANKS.defaultBlockState(), null),
					new Blueprint.Entry(new BlockPos(0, 1, 0), Blocks.OAK_PLANKS.defaultBlockState(), null));
				Blueprint tiny = new Blueprint(AliveWorkplace.id("test/qa_cellar"), new Vec3i(1, 2, 1), blocks);
				TablePayloads.Preview pv = TableServer.buildPreview(tiny.id(), tiny, scale);
				List<BlockPos> cs = cells(pv);
				int want = Math.floorDiv(y, scale);
				if (pv.scale() != scale || pv.below() != -y) {
					problems.add("y " + y + " scale " + scale + ": got scale " + pv.scale() + ", depth " + pv.below());
				}
				if (cs.stream().noneMatch(p -> p.getY() == want)) {
					problems.add("y " + y + " scale " + scale + ": no cell at y " + want + " in " + cs);
				}
				if (cs.stream().noneMatch(p -> p.getY() == 0)) {
					problems.add("y " + y + " scale " + scale + ": the ground floor's cell is gone: " + cs);
				}
			}
		}
		MinecraftServer server = helper.getLevel().getServer();
		Blueprint bp = BlueprintLibrary.get(server, StarterBlueprints.STARTER_COTTAGE_4.id()).orElseThrow();
		for (int scale : new int[] {1, 2}) {
			TablePayloads.Preview pv = TableServer.buildPreview(bp.id(), bp, scale);
			if (pv.below() != 4) {
				problems.add("Starter Cottage IV at scale " + scale + ": depth " + pv.below() + ", expected 4 (in blocks)");
			}
		}
		helper.assertTrue(problems.isEmpty(), String.join("; ", problems));
		helper.succeed();
	}

	/**
	 * The detail pane's size line, from what a client receives after it asks for a build: Starter Cottage IV's preview
	 * says 4 below ground and the line reads "W × H × D · N blocks, and 4 below ground" with the listing's real numbers;
	 * the Starter Cottage's preview says 0, so its line is the plain size.
	 */
	//$ gametest_batch AREA '"qaBtSizeLine"'
	@GameTest(template = AREA, batch = "qaBtSizeLine")
	public void theSizeLineSaysHowFarBelowGround(GameTestHelper helper) {
		Leftovers.clear(helper);
		QaStageFiveGameTests.Client c = clientAtTable(helper);
		List<TablePayloads.Entry> all = library(helper);
		ResourceLocation four = StarterBlueprints.STARTER_COTTAGE_4.id();
		c.send(new TablePayloads.RequestDetails(four));
		List<TablePayloads.Preview> got = c.sent(TablePayloads.Preview.class);
		helper.assertTrue(got.size() == 1 && got.get(0).id().equals(four), "asking for Starter Cottage IV sent previews " + got.stream().map(TablePayloads.Preview::id).toList());
		TablePayloads.Preview pv = got.get(0);
		helper.assertTrue(pv.below() == 4, "Starter Cottage IV's preview says " + pv.below() + " below ground, expected 4");
		TablePayloads.Entry e = all.stream().filter(x -> x.id().equals(four)).findFirst().orElseThrow();
		String line = english(Component.translatable("screen.aliveworkplace.table.size_below", e.sizeX(), e.sizeY(), e.sizeZ(), e.blocks(), pv.below()));
		String want = e.sizeX() + " × " + e.sizeY() + " × " + e.sizeZ() + " · " + e.blocks() + " blocks, and 4 below ground";
		helper.assertTrue(want.equals(line), "the size line reads \"" + line + "\", expected \"" + want + "\"");
		helper.assertTrue(e.sizeY() == pv.sizeY(), "the listing's height " + e.sizeY() + " isn't the preview's height above ground " + pv.sizeY());
		c.send(new TablePayloads.RequestDetails(COTTAGE));
		List<TablePayloads.Preview> plain = c.sent(TablePayloads.Preview.class);
		helper.assertTrue(plain.size() == 1 && plain.get(0).below() == 0, "the Starter Cottage's preview: " + plain.stream().map(TablePayloads.Preview::below).toList());
		TablePayloads.Entry ce = all.stream().filter(x -> x.id().equals(COTTAGE)).findFirst().orElseThrow();
		String plainLine = english(Component.translatable("screen.aliveworkplace.table.size", ce.sizeX(), ce.sizeY(), ce.sizeZ(), ce.blocks()));
		helper.assertTrue(plainLine.equals(ce.sizeX() + " × " + ce.sizeY() + " × " + ce.sizeZ() + " · " + ce.blocks() + " blocks") && !plainLine.contains("below"),
			"the plain size line reads \"" + plainLine + "\"");
		helper.succeed();
	}
}
