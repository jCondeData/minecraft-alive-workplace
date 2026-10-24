package io.github.jcondedata.aliveworkplace.compat;

import io.github.jcondedata.aliveworkplace.blueprint.Blueprint;
import io.github.jcondedata.aliveworkplace.blueprint.BlueprintLibrary;
import io.github.jcondedata.aliveworkplace.blueprint.StarterBlueprints;
import io.github.jcondedata.aliveworkplace.build.BuildSiteManager;
import io.github.jcondedata.aliveworkplace.build.SupplyContainers;
import io.github.jcondedata.aliveworkplace.furnish.FurnitureChoice;
import io.github.jcondedata.aliveworkplace.platform.ItemStores;
import io.github.jcondedata.aliveworkplace.registry.ModBlocks;
import io.github.jcondedata.aliveworkplace.store.StorageNetworks;
import io.github.jcondedata.aliveworkplace.store.StorehouseBoard;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.locale.Language;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.ItemLore;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

/**
 * QA (lane-c-1024-0653), written from the specs of ROADMAP 37.8, 37.9, 37.10 and 37.17 rather than the code: exact
 * room at the edges of Sophisticated limited barrels and tiers, a builder clearing a site where the blueprint wants a
 * block over a Sophisticated chest, controllers and connectors with nothing attached or with a block broken out of their
 * network (nothing lost, nothing counted twice), overflowing deliveries conserved, the storage builds' Tom's copies
 * holding what the spec lists, and the board's sentences in the lang file.
 */
public class QaModdedStorageCompatTests implements FabricGameTest {
	private static final String AREA = CompatGameTests.AREA;
	private static final String SOPH = "sophisticatedstorage";
	private static final String TOMS = "toms_storage";

	/**
	 * 37.8: a limited barrel I's one slot takes exactly the room it reports, then not one more, and no second kind of
	 * item while it holds the first; emptied, it gives back exactly what went in.
	 */
	//$ gametest AREA
	@GameTest(template = AREA)
	public void aLimitedBarrelTakesExactlyTheRoomItShows(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		BlockPos rel = new BlockPos(3, 2, 3);
		helper.setBlock(rel, block(SOPH, "limited_barrel_1"));
		List<BlockPos> one = List.of(helper.absolutePos(rel));
		ItemStores.Room empty = SupplyContainers.room(level, one);
		helper.assertTrue(empty.bigSlots() && empty.freeSlots() == 1 && empty.freeItems() > 64, "an empty limited barrel I: " + empty);
		int room = (int) empty.freeItems();
		ItemStack rest = SupplyContainers.insert(level, one, new ItemStack(Items.COBBLESTONE, room));
		helper.assertTrue(rest.isEmpty(), "the room shown was " + room + " but " + rest.getCount() + " were left over");
		helper.assertTrue(SupplyContainers.count(level, one, Items.COBBLESTONE) == room, "holds " + SupplyContainers.count(level, one, Items.COBBLESTONE) + " of " + room);
		ItemStores.Room full = SupplyContainers.room(level, one);
		helper.assertTrue(full.freeItems() == 0 && full.freeSlots() == 0 && SupplyContainers.freeStacks(level, one) == 0, "full: " + full);
		helper.assertTrue(SupplyContainers.insert(level, one, new ItemStack(Items.COBBLESTONE, 1)).getCount() == 1, "a full limited barrel took one more");
		helper.assertTrue(SupplyContainers.insert(level, one, new ItemStack(Items.DIRT, 5)).getCount() == 5, "its one slot took a second kind of item");
		int taken = SupplyContainers.extract(level, one, Items.COBBLESTONE, room + 100);
		helper.assertTrue(taken == room && SupplyContainers.count(level, one, Items.COBBLESTONE) == 0, "asked for " + (room + 100) + ", got " + taken);
		helper.assertTrue(SupplyContainers.room(level, one).freeItems() == room, "emptied, the room is " + SupplyContainers.room(level, one));
		helper.succeed();
	}

	/** 37.8: a limited iron barrel IV holds four kinds of item, one a slot, refuses a fifth, and gives each back exactly. */
	//$ gametest AREA
	@GameTest(template = AREA)
	public void aFourSlotLimitedBarrelHoldsFourKinds(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		BlockPos rel = new BlockPos(3, 2, 3);
		helper.setBlock(rel, block(SOPH, "limited_iron_barrel_4"));
		List<BlockPos> one = List.of(helper.absolutePos(rel));
		List<Item> kinds = List.of(Items.COBBLESTONE, Items.DIRT, Items.SAND, Items.GRAVEL);
		for (Item kind : kinds) {
			helper.assertTrue(SupplyContainers.insert(level, one, new ItemStack(kind, 100)).isEmpty(), "no room for 100 " + kind);
		}
		helper.assertTrue(SupplyContainers.room(level, one).freeSlots() == 0, "four kinds in, free slots " + SupplyContainers.room(level, one));
		helper.assertTrue(SupplyContainers.insert(level, one, new ItemStack(Items.OAK_LOG, 3)).getCount() == 3, "a fifth kind went in");
		for (Item kind : kinds) {
			helper.assertTrue(SupplyContainers.count(level, one, kind) == 100, SupplyContainers.count(level, one, kind) + " " + kind + " of 100");
			helper.assertTrue(SupplyContainers.extract(level, one, kind, 150) == 100, "gave back other than 100 " + kind);
		}
		helper.assertTrue(SupplyContainers.contents(level, one).values().stream().mapToLong(Long::longValue).sum() == 0, "left: " + SupplyContainers.contents(level, one));
		helper.succeed();
	}

	/** 37.8: every tier up holds more: an empty chest or barrel of each tier has more free slots than the tier below. */
	//$ gametest AREA
	@GameTest(template = AREA)
	public void eachTierUpHasMoreRoom(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		List<String> tiers = List.of("", "copper_", "iron_", "gold_", "diamond_", "netherite_");
		int z = 2;
		for (String kind : List.of("chest", "barrel")) {
			int last = 0;
			for (int i = 0; i < tiers.size(); i++) {
				BlockPos rel = new BlockPos(2 + i * 2, 2, z);
				helper.setBlock(rel, block(SOPH, tiers.get(i) + kind));
				ItemStores.Room room = SupplyContainers.room(level, List.of(helper.absolutePos(rel)));
				helper.assertTrue(room.freeSlots() > last && room.freeItems() == room.freeSlots() * 64L,
					tiers.get(i) + kind + ": " + room + ", the tier below had " + last + " slots");
				last = room.freeSlots();
			}
			z += 4;
		}
		helper.succeed();
	}

	/**
	 * 37.8: where the blueprint wants planks, a Sophisticated netherite chest (20 diamonds) and a limited gold barrel II
	 * (300 cobblestone, more than a stack) stand on the site: the builder finishes without breaking either, and each still
	 * holds exactly what it held; the planks it couldn't place aren't conjured, and no diamond is copied anywhere.
	 */
	//$ gametest_ticks_batch AREA '2400' '"qa_soph_clear"'
	@GameTest(template = AREA, timeoutTicks = 2400, batch = "qa_soph_clear")
	public void aBuilderNeverBreaksSophisticatedStorageWhereTheBlueprintWantsABlock(GameTestHelper helper) {
		PartnerShowsCompatTests.clearLeftovers(helper);
		ServerLevel level = helper.getLevel();
		Map<BlockPos, BlockState> design = new HashMap<>();
		for (int x = 0; x < 4; x++) {
			design.put(new BlockPos(x, 0, 0), Blocks.OAK_PLANKS.defaultBlockState());
		}
		ResourceLocation id = CompatGameTests.blueprintFrom(helper, "qa_soph_clear", design, Map.of());
		BlockPos chestAt = CompatGameTests.ORIGIN.offset(1, 0, 0);
		BlockPos barrelAt = CompatGameTests.ORIGIN.offset(2, 0, 0);
		helper.setBlock(chestAt, block(SOPH, "netherite_chest"));
		helper.setBlock(barrelAt, block(SOPH, "limited_gold_barrel_2"));
		List<BlockPos> chest = List.of(helper.absolutePos(chestAt));
		List<BlockPos> barrel = List.of(helper.absolutePos(barrelAt));
		helper.assertTrue(SupplyContainers.insert(level, chest, new ItemStack(Items.DIAMOND, 20)).isEmpty(), "no diamonds in the chest");
		helper.assertTrue(SupplyContainers.insert(level, barrel, new ItemStack(Items.COBBLESTONE, 300)).isEmpty(), "no cobblestone in the barrel");
		CompatGameTests.Setup s = CompatGameTests.setup(helper, id, new ItemStack(Items.OAK_PLANKS, 4));
		List<BlockPos> benchChest = List.of(helper.absolutePos(CompatGameTests.CHEST));
		helper.succeedWhen(() -> {
			helper.assertTrue(BuildSiteManager.get(level).get(s.site().id()) == null,
				"still building: stage=" + s.site().stage() + " status=" + s.site().status() + " missing=" + s.site().missing());
			helper.assertTrue(level.getBlockState(chest.get(0)).is(block(SOPH, "netherite_chest")), "the chest was broken: " + level.getBlockState(chest.get(0)));
			helper.assertTrue(level.getBlockState(barrel.get(0)).is(block(SOPH, "limited_gold_barrel_2")), "the barrel was broken: " + level.getBlockState(barrel.get(0)));
			helper.assertTrue(SupplyContainers.count(level, chest, Items.DIAMOND) == 20, SupplyContainers.count(level, chest, Items.DIAMOND) + " of 20 diamonds left");
			helper.assertTrue(SupplyContainers.count(level, barrel, Items.COBBLESTONE) == 300, SupplyContainers.count(level, barrel, Items.COBBLESTONE) + " of 300 cobblestone left");
			helper.assertTrue(level.getBlockState(s.at(new BlockPos(0, 0, 0))).is(Blocks.OAK_PLANKS) && level.getBlockState(s.at(new BlockPos(3, 0, 0))).is(Blocks.OAK_PLANKS),
				"the free ends of the wall weren't built");
			long planks = SupplyContainers.count(level, benchChest, Items.OAK_PLANKS) + s.villager().getInventory().countItem(Items.OAK_PLANKS);
			helper.assertTrue(planks == 2, planks + " planks left of 4 after placing 2 (no planks made or lost)");
			helper.assertTrue(SupplyContainers.count(level, benchChest, Items.DIAMOND) == 0 && s.villager().getInventory().countItem(Items.DIAMOND) == 0,
				"diamonds were copied to the builder");
		});
	}

	/**
	 * 37.9: a controller linking a single barrel takes a delivery bigger than the barrel through itself only up to the
	 * barrel's 27 stacks, hands the rest back exactly, and gives out no more than it holds.
	 */
	//$ gametest_batch AREA '"qa_soph_controller_full"'
	@GameTest(template = AREA, batch = "qa_soph_controller_full")
	public void aControllerTakesNoMoreThanItsBarrelHolds(GameTestHelper helper) {
		networksOn(helper);
		ServerLevel level = helper.getLevel();
		helper.setBlock(new BlockPos(2, 2, 2), ModBlocks.STOREHOUSE);
		BlockPos barrel = helper.absolutePos(new BlockPos(4, 2, 4));
		level.setBlockAndUpdate(barrel, block(SOPH, "barrel").defaultBlockState());
		BlockPos controller = helper.absolutePos(new BlockPos(3, 2, 4));
		SophisticatedControllerCompatTests.placeAsPlayer(level, controller, block(SOPH, "controller"), null);
		List<BlockPos> store = SupplyContainers.find(level, helper.absolutePos(new BlockPos(2, 2, 2)), null);
		helper.assertTrue(store.equals(List.of(controller)), "the store: " + store);
		ItemStack rest = SupplyContainers.insert(level, store, new ItemStack(Items.COBBLESTONE, 2000));
		long held = SupplyContainers.count(level, List.of(barrel), Items.COBBLESTONE);
		helper.assertTrue(held == 27 * 64 && rest.getCount() == 2000 - 27 * 64, "of 2000: " + held + " in the barrel, " + rest.getCount() + " handed back");
		helper.assertTrue(SupplyContainers.count(level, store, Items.COBBLESTONE) == held, "the store counts " + SupplyContainers.count(level, store, Items.COBBLESTONE));
		helper.assertTrue(SupplyContainers.freeSlots(level, store) == 0, "a full network shows " + SupplyContainers.freeSlots(level, store) + " free slots");
		int taken = SupplyContainers.extract(level, store, Items.COBBLESTONE, 5000);
		helper.assertTrue(taken == 27 * 64 && SupplyContainers.count(level, List.of(barrel), Items.COBBLESTONE) == 0, "asked for 5000, got " + taken);
		helper.succeed();
	}

	/**
	 * 37.9: a controller with nothing linked beside the Storehouse, and a vanilla chest: a porter's delivery put away in
	 * the store lands in the chest, none lost into the empty controller; the board claims no linked blocks.
	 */
	//$ gametest_batch AREA '"qa_soph_controller_lone"'
	@GameTest(template = AREA, batch = "qa_soph_controller_lone")
	public void aControllerWithNothingLinkedLosesNoDelivery(GameTestHelper helper) {
		networksOn(helper);
		ServerLevel level = helper.getLevel();
		BlockPos storehouse = new BlockPos(2, 2, 2);
		helper.setBlock(storehouse, ModBlocks.STOREHOUSE);
		BlockPos controller = helper.absolutePos(new BlockPos(4, 2, 4));
		SophisticatedControllerCompatTests.placeAsPlayer(level, controller, block(SOPH, "controller"), null);
		BlockPos chest = new BlockPos(7, 2, 2);
		helper.setBlock(chest, Blocks.CHEST);
		List<BlockPos> store = SupplyContainers.find(level, helper.absolutePos(storehouse), null);
		helper.assertTrue(store.contains(helper.absolutePos(chest)), "the chest isn't in the store: " + store);
		ItemStack rest = SupplyContainers.insert(level, store, new ItemStack(Items.RAW_IRON, 40));
		long inChest = SupplyContainers.count(level, List.of(helper.absolutePos(chest)), Items.RAW_IRON);
		helper.assertTrue(rest.isEmpty() && inChest == 40, "of 40 raw iron: " + inChest + " in the chest, " + rest.getCount() + " handed back");
		helper.assertTrue(SupplyContainers.count(level, store, Items.RAW_IRON) == 40, "the store counts " + SupplyContainers.count(level, store, Items.RAW_IRON));
		ServerPlayer player = helper.makeMockServerPlayerInLevel();
		PartnerShowsCompatTests.after(helper, () -> level.getServer().getPlayerList().remove(player));
		BlockPos at = helper.absolutePos(storehouse);
		player.teleportTo(at.getX() + 0.5, at.getY(), at.getZ() + 1.5);
		Component line = line(player, helper, storehouse, "screen.aliveworkplace.storehouse.linked");
		helper.assertTrue(line == null || arg(line).equals("0"), "the board says a lone controller links " + (line == null ? "" : arg(line)) + " blocks");
		helper.succeed();
	}

	/**
	 * 37.9: a controller linking three barrels in a row, 10 cobblestone in each; the middle barrel is broken (without
	 * drops). Whatever the controller still links, the Storehouse counts the 20 left exactly once, the far barrel
	 * included, and a builder can take all 20.
	 */
	//$ gametest_ticks_batch AREA '200' '"qa_soph_controller_broken"'
	@GameTest(template = AREA, timeoutTicks = 200, batch = "qa_soph_controller_broken")
	public void aBarrelBrokenOutOfTheNetworkLeavesTheRestCountedOnce(GameTestHelper helper) {
		networksOn(helper);
		ServerLevel level = helper.getLevel();
		BlockPos storehouse = helper.absolutePos(new BlockPos(2, 2, 2));
		helper.setBlock(new BlockPos(2, 2, 2), ModBlocks.STOREHOUSE);
		List<BlockPos> barrels = List.of(helper.absolutePos(new BlockPos(4, 2, 4)), helper.absolutePos(new BlockPos(5, 2, 4)),
			helper.absolutePos(new BlockPos(6, 2, 4)));
		for (BlockPos b : barrels) {
			level.setBlockAndUpdate(b, block(SOPH, "barrel").defaultBlockState());
			helper.assertTrue(SupplyContainers.insert(level, List.of(b), new ItemStack(Items.COBBLESTONE, 10)).isEmpty(), "a barrel took nothing");
		}
		BlockPos controller = helper.absolutePos(new BlockPos(3, 2, 4));
		SophisticatedControllerCompatTests.placeAsPlayer(level, controller, block(SOPH, "controller"), null);
		helper.assertTrue(SupplyContainers.count(level, SupplyContainers.find(level, storehouse, null), Items.COBBLESTONE) == 30, "not 30 at the start");
		level.destroyBlock(barrels.get(1), false);
		helper.runAfterDelay(5, () -> {
			List<BlockPos> store = SupplyContainers.find(level, storehouse, null);
			long counted = SupplyContainers.count(level, store, Items.COBBLESTONE);
			helper.assertTrue(counted == 20, "with the middle barrel broken, the Storehouse " + store + " counts " + counted + " cobblestone, wanted 20");
			int taken = SupplyContainers.extract(level, store, Items.COBBLESTONE, 64);
			helper.assertTrue(taken == 20, "a builder asking for 64 got " + taken + " of the 20 left");
			long left = SupplyContainers.count(level, List.of(barrels.get(0), barrels.get(2)), Items.COBBLESTONE);
			helper.assertTrue(left == 0, left + " cobblestone left in the two barrels after taking 20");
			helper.succeed();
		});
	}

	/**
	 * 37.10: a Tom's inventory connector joined to nothing beside the Storehouse, and a vanilla chest away from it: the
	 * delivery lands in the chest, none lost into the empty connector, and the store counts it once.
	 */
	//$ gametest_ticks_batch AREA '400' '"qa_toms_lone"'
	@GameTest(template = AREA, timeoutTicks = 400, batch = "qa_toms_lone")
	public void aConnectorJoinedToNothingLosesNoDelivery(GameTestHelper helper) {
		PartnerShowsCompatTests.clearLeftovers(helper);
		networksOn(helper);
		ServerLevel level = helper.getLevel();
		BlockPos storehouse = new BlockPos(2, 2, 2);
		helper.setBlock(storehouse, ModBlocks.STOREHOUSE);
		helper.setBlock(new BlockPos(5, 2, 5), block(TOMS, "inventory_connector"));
		BlockPos chest = new BlockPos(8, 2, 2);
		helper.setBlock(chest, Blocks.CHEST);
		helper.runAfterDelay(40, () -> {
			List<BlockPos> store = SupplyContainers.find(level, helper.absolutePos(storehouse), null);
			helper.assertTrue(store.contains(helper.absolutePos(chest)), "the chest isn't in the store: " + store);
			ItemStack rest = SupplyContainers.insert(level, store, new ItemStack(Items.RAW_IRON, 40));
			long inChest = SupplyContainers.count(level, List.of(helper.absolutePos(chest)), Items.RAW_IRON);
			helper.assertTrue(rest.isEmpty() && inChest == 40, "of 40 raw iron: " + inChest + " in the chest, " + rest.getCount() + " handed back");
			helper.assertTrue(SupplyContainers.count(level, store, Items.RAW_IRON) == 40, "the store counts " + SupplyContainers.count(level, store, Items.RAW_IRON));
			// Nothing left for later batches' porters and builders to find.
			helper.setBlock(new BlockPos(5, 2, 5), Blocks.AIR);
			helper.succeed();
		});
	}

	/**
	 * 37.10: a connector with one chest touching it: a delivery of 2000 cobblestone through the store fills the chest's
	 * 27 stacks and hands back exactly the rest; a builder asking for more than there is gets exactly what's there.
	 */
	//$ gametest_ticks_batch AREA '400' '"qa_toms_full"'
	@GameTest(template = AREA, timeoutTicks = 400, batch = "qa_toms_full")
	public void theConnectorTakesNoMoreThanItsChestHolds(GameTestHelper helper) {
		PartnerShowsCompatTests.clearLeftovers(helper);
		networksOn(helper);
		ServerLevel level = helper.getLevel();
		BlockPos storehouse = new BlockPos(2, 2, 2);
		helper.setBlock(storehouse, ModBlocks.STOREHOUSE);
		BlockPos connector = new BlockPos(5, 2, 5);
		BlockPos chest = new BlockPos(6, 2, 5);
		helper.setBlock(connector, block(TOMS, "inventory_connector"));
		helper.setBlock(chest, Blocks.CHEST);
		List<BlockPos> inChest = List.of(helper.absolutePos(chest));
		helper.runAfterDelay(40, () -> {
			List<BlockPos> store = SupplyContainers.find(level, helper.absolutePos(storehouse), null);
			helper.assertTrue(store.equals(List.of(helper.absolutePos(connector))), "the store: " + store.stream().map(helper::relativePos).toList());
			ItemStack rest = SupplyContainers.insert(level, store, new ItemStack(Items.COBBLESTONE, 2000));
			long held = SupplyContainers.count(level, inChest, Items.COBBLESTONE);
			helper.assertTrue(held == 27 * 64 && rest.getCount() == 2000 - 27 * 64, "of 2000: " + held + " in the chest, " + rest.getCount() + " handed back");
			helper.assertTrue(SupplyContainers.count(level, store, Items.COBBLESTONE) == held, "the store counts " + SupplyContainers.count(level, store, Items.COBBLESTONE));
			int taken = SupplyContainers.extract(level, store, Items.COBBLESTONE, 5000);
			helper.assertTrue(taken == 27 * 64 && SupplyContainers.count(level, inChest, Items.COBBLESTONE) == 0, "asked for 5000, got " + taken);
			// Nothing left for later batches' porters and builders to find.
			helper.setBlock(connector, Blocks.AIR);
			helper.succeed();
		});
	}

	/**
	 * 37.17 with Tom's: the builds are made of what the spec lists. Storage Hall: 24+ chests and barrels, one inventory
	 * connector, inventory cables (plain or framed), a storage terminal and a crafting terminal, filing cabinets. Terminal Nook: a connector,
	 * 6 chests, one terminal. Warehouse: a connector and a crafting terminal, on two floors. Each is one Storehouse.
	 */
	//$ gametest 'FabricGameTest.EMPTY_STRUCTURE'
	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
	public void theTomsStorageBuildsHoldWhatTheSpecLists(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		Map<StarterBlueprints.Entry, Map<String, Long>> counts = new HashMap<>();
		for (StarterBlueprints.Entry entry : StarterBlueprints.STORAGE_HALLS) {
			ResourceLocation id = entry.id();
			Blueprint base = BlueprintLibrary.get(level, id).orElseThrow(() -> new GameTestAssertException("no blueprint " + id));
			FurnitureChoice choice = FurnitureChoice.forNewBuild(id);
			helper.assertTrue(choice.modded(), id + " isn't built from its Tom's copy");
			Blueprint built = choice.apply(base, id);
			Map<String, Long> n = new HashMap<>();
			for (Blueprint.Entry e : built.blocks()) {
				BlockState s = e.state();
				String key = s.is(Blocks.CHEST) || s.is(Blocks.BARREL) || s.is(Blocks.TRAPPED_CHEST) ? "stores"
					: s.is(ModBlocks.STOREHOUSE) ? "storehouse" : BuiltInRegistries.BLOCK.getKey(s.getBlock()).toString();
				n.merge(key, 1L, Long::sum);
			}
			counts.put(entry, n);
			helper.assertTrue(n.getOrDefault("storehouse", 0L) == 1, id + ": " + n.getOrDefault("storehouse", 0L) + " Storehouse blocks");
			helper.assertTrue(n.getOrDefault(TOMS + ":inventory_connector", 0L) >= 1, id + ": no inventory connector");
		}
		Map<String, Long> hall = counts.get(StarterBlueprints.STORAGE_HALL);
		helper.assertTrue(hall.getOrDefault("stores", 0L) >= 24, "the Storage Hall has " + hall.getOrDefault("stores", 0L) + " chests and barrels");
		// Inventory cables, plain or framed (37.10 counts framed cables and cable connectors as the network's cables).
		long cables = hall.entrySet().stream().filter(e -> e.getKey().startsWith(TOMS + ":inventory_cable")).mapToLong(Map.Entry::getValue).sum();
		helper.assertTrue(cables >= 1, "the Storage Hall has no inventory cable: " + hall);
		helper.assertTrue(hall.getOrDefault(TOMS + ":storage_terminal", 0L) >= 1 && hall.getOrDefault(TOMS + ":crafting_terminal", 0L) >= 1,
			"the Storage Hall's terminals: " + hall);
		helper.assertTrue(hall.getOrDefault(TOMS + ":filing_cabinet", 0L) >= 1, "the Storage Hall has no filing cabinet");
		Map<String, Long> nook = counts.get(StarterBlueprints.TERMINAL_NOOK);
		helper.assertTrue(nook.getOrDefault("stores", 0L) >= 6, "the Terminal Nook has " + nook.getOrDefault("stores", 0L) + " chests");
		long nookTerminals = nook.getOrDefault(TOMS + ":storage_terminal", 0L) + nook.getOrDefault(TOMS + ":crafting_terminal", 0L);
		helper.assertTrue(nookTerminals == 1 && nook.getOrDefault(TOMS + ":inventory_connector", 0L) == 1, "the Terminal Nook: " + nook);
		Map<String, Long> warehouse = counts.get(StarterBlueprints.WAREHOUSE);
		helper.assertTrue(warehouse.getOrDefault(TOMS + ":crafting_terminal", 0L) >= 1, "the Warehouse has no crafting terminal: " + warehouse);
		helper.assertTrue(StarterBlueprints.WAREHOUSE.size().getY() >= 8, "the Warehouse is " + StarterBlueprints.WAREHOUSE.size().getY() + " tall: not two floors");
		helper.succeed();
	}

	/** Every sentence the modded storage shows a player reads from the lang file, with its number in it. */
	//$ gametest 'FabricGameTest.EMPTY_STRUCTURE'
	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
	public void theStorageSentencesAreInTheLangFile(GameTestHelper helper) {
		Language lang = Language.getInstance();
		for (String key : List.of("screen.aliveworkplace.storehouse.linked", "screen.aliveworkplace.storehouse.connector",
			"screen.aliveworkplace.storehouse.room", "blueprint.aliveworkplace.storage_hall", "blueprint.aliveworkplace.terminal_nook",
			"blueprint.aliveworkplace.warehouse")) {
			helper.assertTrue(lang.has(key), key + " isn't in the lang file");
		}
		String linked = Component.translatable("screen.aliveworkplace.storehouse.linked", 3).getString();
		helper.assertTrue(linked.equals("Linked through a storage controller: 3 blocks"), "the controller line reads " + linked);
		String room = Component.translatable("screen.aliveworkplace.storehouse.room", 1728).getString();
		helper.assertTrue(room.contains("1728") && !room.contains("%"), "the room line reads " + room);
		helper.succeed();
	}

	/** Turns {@code moddedStorageNetworks} on for this test and back as it was afterwards. */
	private static void networksOn(GameTestHelper helper) {
		boolean was = StorageNetworks.ENABLED;
		StorageNetworks.ENABLED = true;
		PartnerShowsCompatTests.after(helper, () -> StorageNetworks.ENABLED = was);
	}

	private static Component line(ServerPlayer player, GameTestHelper helper, BlockPos storehouse, String key) {
		ItemLore lore = StorehouseBoard.boardForTest(player, helper.absolutePos(storehouse)).icon(StorehouseBoard.INFO_SLOT).get(DataComponents.LORE);
		helper.assertTrue(lore != null, "the board's info has no text");
		return lore.lines().stream().filter(l -> l.getContents() instanceof TranslatableContents t && t.getKey().equals(key)).findFirst().orElse(null);
	}

	private static String arg(Component line) {
		return String.valueOf(((TranslatableContents) line.getContents()).getArgs()[0]);
	}

	private static Block block(String mod, String id) {
		return BuiltInRegistries.BLOCK.getOptional(ResourceLocation.fromNamespaceAndPath(mod, id))
			.orElseThrow(() -> new GameTestAssertException("no block " + mod + ":" + id));
	}
}
