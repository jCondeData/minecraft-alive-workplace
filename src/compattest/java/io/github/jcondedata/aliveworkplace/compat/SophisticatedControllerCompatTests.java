package io.github.jcondedata.aliveworkplace.compat;

import io.github.jcondedata.aliveworkplace.blueprint.Blueprint;
import io.github.jcondedata.aliveworkplace.blueprint.BlueprintLibrary;
import io.github.jcondedata.aliveworkplace.blueprint.BlueprintStyles;
import io.github.jcondedata.aliveworkplace.build.SupplyContainers;
import io.github.jcondedata.aliveworkplace.platform.ItemStores;
import io.github.jcondedata.aliveworkplace.registry.ModBlocks;
import io.github.jcondedata.aliveworkplace.store.Porters;
import io.github.jcondedata.aliveworkplace.store.StorageNetworks;
import io.github.jcondedata.aliveworkplace.store.StorehouseBoard;
import io.github.jcondedata.aliveworkplace.work.Village;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.ItemLore;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

/**
 * ROADMAP 37.9: a Sophisticated Storage controller in the Storehouse's area makes the storage blocks it links one store,
 * read and filled through the controller's own item storage (Fabric's transfer API), so nothing counts twice; breaking
 * the controller turns them back into stores of their own. The "Sophisticated" Storehouse style builds Sophisticated
 * chests and barrels and a controller on the Storehouse.
 */
public class SophisticatedControllerCompatTests implements FabricGameTest {
	private static final String AREA = CompatGameTests.AREA;
	private static final String HUGE_AREA = "aliveworkplace_compat:huge_area";
	private static final String MOD = "sophisticatedstorage";
	private static final String LINKED = "screen.aliveworkplace.storehouse.linked";

	/**
	 * A Storehouse, a controller and three barrels in a row from it, 10 cobblestone in each, the controller placed the
	 * way a player places it: the Storehouse lists only the controller and counts 30 cobblestone, not 60; it takes 50
	 * raw iron the way a porter puts a delivery away and gives 25 cobblestone the way a builder takes them, out of the
	 * barrels; the board says 3 blocks are linked. Broken, the controller leaves three stores with what they hold.
	 */
	//$ gametest_batch AREA '"sophisticated_controller"'
	@GameTest(template = AREA, batch = "sophisticated_controller")
	public void aControllerMakesThreeBarrelsOneStore(GameTestHelper helper) {
		boolean was = StorageNetworks.ENABLED;
		StorageNetworks.ENABLED = true;
		try {
			aControllerMakesThreeBarrelsOneStoreBody(helper);
		} finally {
			StorageNetworks.ENABLED = was;
		}
	}

	private void aControllerMakesThreeBarrelsOneStoreBody(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		BlockPos storehouse = new BlockPos(2, 2, 2);
		helper.setBlock(storehouse, ModBlocks.STOREHOUSE);
		List<BlockPos> barrels = List.of(helper.absolutePos(new BlockPos(4, 2, 4)), helper.absolutePos(new BlockPos(5, 2, 4)),
			helper.absolutePos(new BlockPos(6, 2, 4)));
		for (BlockPos barrel : barrels) {
			level.setBlockAndUpdate(barrel, block("barrel").defaultBlockState());
			helper.assertTrue(SupplyContainers.insert(level, List.of(barrel), new ItemStack(Items.COBBLESTONE, 10)).isEmpty(), "a barrel took nothing");
		}
		BlockPos at = storehouse(helper, storehouse);
		helper.assertTrue(SupplyContainers.find(level, at, null).size() == 3, "three barrels, no controller: " + SupplyContainers.find(level, at, null));
		ServerPlayer player = helper.makeMockServerPlayerInLevel();
		PartnerShowsCompatTests.after(helper, () -> level.getServer().getPlayerList().remove(player));
		player.teleportTo(at.getX() + 0.5, at.getY(), at.getZ() + 1.5);
		helper.assertTrue(linkedLine(player, storehouse, helper) == null, "a linked line without a controller");

		BlockPos controller = helper.absolutePos(new BlockPos(3, 2, 4));
		placeAsPlayer(level, controller, block("controller"), player);
		List<BlockPos> store = SupplyContainers.find(level, at, null);
		helper.assertTrue(store.equals(List.of(controller)), "the Storehouse's stores with a controller: " + store);
		long counted = SupplyContainers.count(level, store, Items.COBBLESTONE);
		helper.assertTrue(counted == 30, "the Storehouse counts " + counted + " cobblestone, not 30");
		Component line = linkedLine(player, storehouse, helper);
		helper.assertTrue(line != null && arg(line).equals("3"), "the board's linked line: " + line);

		helper.assertTrue(SupplyContainers.insert(level, store, new ItemStack(Items.RAW_IRON, 50)).isEmpty(), "the controller took no raw iron");
		helper.assertTrue(SupplyContainers.count(level, barrels, Items.RAW_IRON) == 50, "the raw iron isn't in the barrels");
		int taken = SupplyContainers.extract(level, store, Items.COBBLESTONE, 25);
		helper.assertTrue(taken == 25 && SupplyContainers.count(level, barrels, Items.COBBLESTONE) == 5,
			"a builder took " + taken + " of 25; the barrels hold " + SupplyContainers.count(level, barrels, Items.COBBLESTONE));

		level.destroyBlock(controller, false);
		List<BlockPos> after = SupplyContainers.find(level, at, null);
		helper.assertTrue(after.size() == 3 && after.containsAll(barrels), "after the controller broke: " + after);
		helper.assertTrue(SupplyContainers.count(level, after, Items.COBBLESTONE) == 5 && SupplyContainers.count(level, after, Items.RAW_IRON) == 50,
			"the barrels lost their goods");
		helper.assertTrue(linkedLine(player, storehouse, helper) == null, "a linked line with the controller gone");
		helper.succeed();
	}

	/** With {@code moddedStorageNetworks} off (as on main until M37 ships), the barrels stay three stores. */
	//$ gametest_batch AREA '"sophisticated_controller"'
	@GameTest(template = AREA, batch = "sophisticated_controller")
	public void switchOffKeepsThreeStores(GameTestHelper helper) {
		boolean was = StorageNetworks.ENABLED;
		StorageNetworks.ENABLED = false;
		try {
			switchOffKeepsThreeStoresBody(helper);
		} finally {
			StorageNetworks.ENABLED = was;
		}
	}

	private void switchOffKeepsThreeStoresBody(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		BlockPos storehouse = new BlockPos(2, 2, 2);
		helper.setBlock(storehouse, ModBlocks.STOREHOUSE);
		for (int x = 4; x <= 6; x++) {
			helper.setBlock(new BlockPos(x, 2, 4), block("barrel"));
		}
		BlockPos controller = helper.absolutePos(new BlockPos(3, 2, 4));
		placeAsPlayer(level, controller, block("controller"), null);
		List<BlockPos> store = SupplyContainers.find(level, storehouse(helper, storehouse), null);
		helper.assertTrue(store.size() == 3 && !store.contains(controller), "switch off: " + store);
		helper.succeed();
	}

	/**
	 * A porter carries a miner's 100 raw iron into a Storehouse whose store is a controller with three barrels: it all
	 * ends in the barrels, through the controller.
	 */
	//$ gametest_ticks_batch AREA '1600' '"sophisticated_controller_porter"'
	@GameTest(template = AREA, timeoutTicks = 1600, batch = "sophisticated_controller_porter")
	public void aPorterDeliversThroughTheController(GameTestHelper helper) {
		PartnerShowsCompatTests.clearLeftovers(helper);
		networks(helper, true);
		Village.RADIUS = 48;
		PartnerShowsCompatTests.after(helper, () -> Village.RADIUS = 0);
		helper.setDayTime(2000); // work hours
		ServerLevel level = helper.getLevel();
		BlockPos minersBench = new BlockPos(2, 2, 2);
		BlockPos minersBarrel = new BlockPos(2, 2, 4);
		helper.setBlock(minersBench, ModBlocks.MINERS_BENCH);
		helper.setBlock(minersBarrel, block("iron_barrel"));
		List<BlockPos> source = List.of(helper.absolutePos(minersBarrel));
		helper.assertTrue(SupplyContainers.insert(level, source, new ItemStack(Items.RAW_IRON, 64)).isEmpty()
			&& SupplyContainers.insert(level, source, new ItemStack(Items.RAW_IRON, 36)).isEmpty(), "the miner's barrel took no raw iron");
		Villager miner = helper.spawn(EntityType.VILLAGER, new BlockPos(3, 2, 3));
		io.github.jcondedata.aliveworkplace.mine.Miners.employ(level, miner, helper.absolutePos(minersBench));

		BlockPos storehouse = new BlockPos(13, 2, 13);
		helper.setBlock(storehouse, ModBlocks.STOREHOUSE);
		List<BlockPos> barrels = List.of(helper.absolutePos(new BlockPos(12, 2, 11)), helper.absolutePos(new BlockPos(14, 2, 11)),
			helper.absolutePos(new BlockPos(13, 3, 11)));
		for (BlockPos barrel : barrels) {
			level.setBlockAndUpdate(barrel, block("barrel").defaultBlockState());
		}
		BlockPos controller = helper.absolutePos(new BlockPos(13, 2, 11));
		placeAsPlayer(level, controller, block("controller"), null);
		List<BlockPos> store = SupplyContainers.find(level, helper.absolutePos(storehouse), null);
		helper.assertTrue(store.equals(List.of(controller)), "the Storehouse's stores: " + store);
		Villager porter = helper.spawn(EntityType.VILLAGER, new BlockPos(12, 2, 12));
		Porters.employ(level, porter, helper.absolutePos(storehouse));
		helper.succeedWhen(() -> {
			long stored = SupplyContainers.count(level, barrels, Items.RAW_IRON);
			long left = SupplyContainers.count(level, source, Items.RAW_IRON);
			helper.assertTrue(stored == 100 && left == 0, "raw iron: " + stored + " in the barrels, " + left + " left with the miner");
			helper.assertTrue(miner.isAlive() && porter.isAlive(), "a worker is gone");
		});
	}

	/**
	 * The "Sophisticated" style, offered for the Storehouse only while the switch is on: its chests and barrels become
	 * Sophisticated ones and the lantern on the Storehouse a controller, which touches the chests on both sides; other
	 * builds aren't changed by it.
	 */
	//$ gametest_batch AREA '"sophisticated_controller"'
	@GameTest(template = AREA, batch = "sophisticated_controller")
	public void theSophisticatedStyleAddsAController(GameTestHelper helper) {
		boolean was = StorageNetworks.ENABLED;
		StorageNetworks.ENABLED = true;
		try {
			theSophisticatedStyleAddsAControllerBody(helper);
		} finally {
			StorageNetworks.ENABLED = was;
		}
	}

	private void theSophisticatedStyleAddsAControllerBody(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		BlueprintStyles.Style style = BlueprintStyles.get("sophisticated").orElseThrow(() -> new GameTestAssertException("no sophisticated style"));
		helper.assertTrue(BlueprintStyles.all().contains(style), "the style isn't offered with the switch on");
		for (String name : List.of("storehouse", "storehouse_2", "storehouse_3")) {
			ResourceLocation baseId = ResourceLocation.fromNamespaceAndPath("aliveworkplace", name);
			Blueprint base = BlueprintLibrary.get(level, baseId).orElseThrow(() -> new GameTestAssertException("no blueprint " + name));
			helper.assertTrue(BlueprintStyles.changes(style, base), name + ": the style changes nothing");
			Blueprint styled = BlueprintLibrary.get(level, BlueprintStyles.styled(baseId, "sophisticated"))
				.orElseThrow(() -> new GameTestAssertException("no styled " + name));
			Map<BlockPos, BlockState> at = styled.blocks().stream().collect(Collectors.toMap(Blueprint.Entry::pos, Blueprint.Entry::state, (a, b) -> a));
			List<BlockPos> controllers = at.entrySet().stream().filter(e -> StorageNetworks.isController(e.getValue())).map(Map.Entry::getKey).toList();
			helper.assertTrue(controllers.size() == 1, name + ": controllers " + controllers);
			BlockPos c = controllers.get(0);
			helper.assertTrue(at.getOrDefault(c.below(), net.minecraft.world.level.block.Blocks.AIR.defaultBlockState()).is(ModBlocks.STOREHOUSE),
				name + ": the controller isn't on the Storehouse");
			long beside = java.util.Arrays.stream(net.minecraft.core.Direction.values())
				.filter(d -> StorageNetworks.isSophisticatedStorage(at.getOrDefault(c.relative(d), net.minecraft.world.level.block.Blocks.AIR.defaultBlockState()))).count();
			helper.assertTrue(beside >= 2, name + ": the controller touches " + beside + " Sophisticated chests");
			boolean vanillaChestLeft = at.values().stream().anyMatch(s -> s.is(net.minecraft.world.level.block.Blocks.CHEST) || s.is(net.minecraft.world.level.block.Blocks.BARREL));
			helper.assertTrue(!vanillaChestLeft, name + ": a vanilla chest or barrel is left");
		}
		Blueprint house = BlueprintLibrary.get(level, ResourceLocation.fromNamespaceAndPath("aliveworkplace", "storehouse")).orElseThrow();
		helper.assertTrue(house.blocks().stream().noneMatch(e -> StorageNetworks.isController(e.state())), "the plain Storehouse has a controller");
		helper.assertTrue(BlueprintLibrary.get(level, ResourceLocation.fromNamespaceAndPath("aliveworkplace", "kitchen"))
			.map(b -> !BlueprintStyles.changes(style, b)).orElse(true), "the style changes a Kitchen");
		StorageNetworks.ENABLED = false;
		helper.assertTrue(!BlueprintStyles.all().contains(style), "the style is offered with the switch off");
		helper.succeed();
	}

	/**
	 * A builder builds a controller between Sophisticated barrels (a builder sets blocks without the placing that links a
	 * controller): once it stands, the controller has linked them, so a Storehouse beside it lists the controller and what
	 * goes in through it lands in the barrels.
	 */
	//$ gametest_ticks_batch AREA '2400' '"sophisticated_controller_builder"'
	@GameTest(template = AREA, timeoutTicks = 2400, batch = "sophisticated_controller_builder")
	public void aBuiltControllerLinksItsBarrels(GameTestHelper helper) {
		PartnerShowsCompatTests.clearLeftovers(helper);
		networks(helper, true);
		ServerLevel level = helper.getLevel();
		Map<BlockPos, BlockState> design = new java.util.HashMap<>();
		design.put(new BlockPos(0, 0, 0), block("barrel").defaultBlockState());
		design.put(new BlockPos(1, 0, 0), block("controller").defaultBlockState());
		design.put(new BlockPos(2, 0, 0), block("barrel").defaultBlockState());
		design.put(new BlockPos(1, 1, 0), block("barrel").defaultBlockState());
		for (Map.Entry<BlockPos, BlockState> e : design.entrySet()) {
			helper.setBlock(CompatGameTests.ORIGIN.offset(e.getKey()), e.getValue());
		}
		ResourceLocation id = CompatGameTests.blueprintFrom(helper, "sophisticated_controller", design, Map.of());
		CompatGameTests.Setup s = CompatGameTests.setup(helper, id, new ItemStack(block("barrel").asItem(), 3), new ItemStack(block("controller").asItem()));
		BlockPos storehouse = new BlockPos(6, 2, 10);
		helper.setBlock(storehouse, ModBlocks.STOREHOUSE);
		helper.succeedWhen(() -> {
			CompatGameTests.assertBuilt(helper, s);
			BlockPos controller = s.at(new BlockPos(1, 0, 0));
			List<BlockPos> barrels = List.of(s.at(new BlockPos(0, 0, 0)), s.at(new BlockPos(2, 0, 0)), s.at(new BlockPos(1, 1, 0)));
			List<BlockPos> store = SupplyContainers.find(level, helper.absolutePos(storehouse), null);
			helper.assertTrue(store.contains(controller) && barrels.stream().noneMatch(store::contains), "the Storehouse's stores: " + store);
			helper.assertTrue(SupplyContainers.insert(level, List.of(controller), new ItemStack(Items.COBBLESTONE, 20)).isEmpty()
				&& SupplyContainers.count(level, barrels, Items.COBBLESTONE) == 20, "the built controller didn't link its barrels");
		});
	}

	/**
	 * A builder adds a barrel to a controller's network, touching only a barrel the controller already links (a builder
	 * sets blocks without the placing that would link it; B238): once the builder's after-block hook has run, the
	 * controller links the new barrel too, so what lies in it is counted through the controller.
	 */
	//$ gametest_ticks_batch AREA '400' '"sophisticated_controller_added"'
	@GameTest(template = AREA, timeoutTicks = 400, batch = "sophisticated_controller_added")
	public void aBarrelBuiltOntoTheNetworkIsLinked(GameTestHelper helper) {
		PartnerShowsCompatTests.clearLeftovers(helper);
		networks(helper, true);
		ServerLevel level = helper.getLevel();
		ServerPlayer player = helper.makeMockServerPlayerInLevel();
		PartnerShowsCompatTests.after(helper, () -> level.getServer().getPlayerList().remove(player));
		BlockPos first = helper.absolutePos(new BlockPos(4, 2, 4));
		level.setBlockAndUpdate(first, block("barrel").defaultBlockState());
		BlockPos controller = helper.absolutePos(new BlockPos(3, 2, 4));
		placeAsPlayer(level, controller, block("controller"), player);
		helper.assertTrue(SupplyContainers.insert(level, List.of(first), new ItemStack(Items.COBBLESTONE, 10)).isEmpty(), "the first barrel took nothing");
		helper.assertTrue(SupplyContainers.count(level, List.of(controller), Items.COBBLESTONE) == 10, "the controller doesn't link the first barrel");

		// The builder sets the second barrel beside the first (two blocks from the controller) and calls its hook.
		BlockPos added = helper.absolutePos(new BlockPos(5, 2, 4));
		level.setBlock(added, block("barrel").defaultBlockState(), Block.UPDATE_ALL);
		StorageNetworks.afterBuilt(level, added, level.getBlockState(added));
		helper.assertTrue(SupplyContainers.insert(level, List.of(added), new ItemStack(Items.OAK_PLANKS, 7)).isEmpty(), "the added barrel took nothing");
		helper.succeedWhen(() -> {
			long planks = SupplyContainers.count(level, List.of(controller), Items.OAK_PLANKS);
			helper.assertTrue(planks == 7, "the controller counts " + planks + " planks of the built barrel's 7: it wasn't linked");
		});
	}

	/**
	 * B371: a controller links a barrel with a stack upgrade (tier 2) and a plain barrel. Empty, the board shows their room
	 * (27 slots of four stacks and 27 of one: 8640). 100 cobblestone go in through the controller: the board shows 100
	 * less, not the upgraded barrel's room counted again for each way the controller tries it, and exactly that much more
	 * goes in, then not one more.
	 */
	//$ gametest_ticks_batch AREA '400' '"sophisticated_controller_room"'
	@GameTest(template = AREA, timeoutTicks = 400, batch = "sophisticated_controller_room")
	public void theRoomBehindAControllerHoldingGoodsIsTheRealRoom(GameTestHelper helper) {
		PartnerShowsCompatTests.clearLeftovers(helper);
		networks(helper, true);
		ServerLevel level = helper.getLevel();
		BlockPos storehouse = new BlockPos(2, 2, 2);
		helper.setBlock(storehouse, ModBlocks.STOREHOUSE);
		ServerPlayer player = helper.makeMockServerPlayerInLevel();
		PartnerShowsCompatTests.after(helper, () -> level.getServer().getPlayerList().remove(player));
		BlockPos upgraded = helper.absolutePos(new BlockPos(4, 2, 4));
		BlockPos plain = helper.absolutePos(new BlockPos(5, 2, 4));
		level.setBlockAndUpdate(upgraded, block("barrel").defaultBlockState());
		level.setBlockAndUpdate(plain, block("barrel").defaultBlockState());
		// The stack upgrade goes in the mod's own way: sneak-clicking the barrel's front with it.
		player.teleportTo(upgraded.getX() + 0.5, upgraded.getY(), upgraded.getZ() + 2.5);
		ItemStack upgrade = new ItemStack(SophisticatedStorageCompatTests.item("stack_upgrade_tier_2"));
		player.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND, upgrade);
		player.setShiftKeyDown(true);
		net.minecraft.core.Direction front = SophisticatedStorageCompatTests.front(level.getBlockState(upgraded));
		var hit = new net.minecraft.world.phys.BlockHitResult(net.minecraft.world.phys.Vec3.atCenterOf(upgraded).relative(front, 0.5), front, upgraded, false);
		player.gameMode.useItemOn(player, level, upgrade, net.minecraft.world.InteractionHand.MAIN_HAND, hit);
		player.setShiftKeyDown(false);
		helper.assertTrue(SupplyContainers.room(level, List.of(upgraded)).freeItems() == 27L * 256,
			"the stack upgrade isn't in the barrel: " + SupplyContainers.room(level, List.of(upgraded)));
		BlockPos controller = helper.absolutePos(new BlockPos(3, 2, 4));
		placeAsPlayer(level, controller, block("controller"), player);
		List<BlockPos> store = SupplyContainers.find(level, storehouse(helper, storehouse), null);
		helper.assertTrue(store.equals(List.of(controller)), "the Storehouse's stores with a controller: " + store);

		long empty = 27L * 256 + 27L * 64;
		helper.assertTrue(SupplyContainers.room(level, store).freeItems() == empty, "empty, the room is " + SupplyContainers.room(level, store));
		Component line = SophisticatedStorageCompatTests.roomLine(SophisticatedStorageCompatTests.info(player, storehouse, helper));
		helper.assertTrue(line != null && arg(line).equals(String.valueOf(empty)), "empty, the board's room line: " + line);

		helper.assertTrue(SupplyContainers.insert(level, store, new ItemStack(Items.COBBLESTONE, 100)).isEmpty(), "the controller took no cobblestone");
		ItemStores.Room after = SupplyContainers.room(level, store);
		helper.assertTrue(after.freeItems() == empty - 100, "after 100 cobblestone the room is " + after + ", wanted " + (empty - 100));
		Component again = SophisticatedStorageCompatTests.roomLine(SophisticatedStorageCompatTests.info(player, storehouse, helper));
		helper.assertTrue(again != null && arg(again).equals(String.valueOf(empty - 100)), "after 100 cobblestone, the board's room line: " + again);
		helper.assertTrue(after.freeStacks() == (int) ((empty - 100) / 64), "free stacks " + after.freeStacks() + " for " + (empty - 100) + " items");

		// The room is real: that much more goes in through the controller, and then not one more.
		ItemStack rest = SupplyContainers.insert(level, store, new ItemStack(Items.COBBLESTONE, (int) (empty - 100)));
		ItemStores.Room full = SupplyContainers.room(level, store);
		helper.assertTrue(rest.isEmpty() && full.freeItems() == 0, "filled to the room shown: " + rest.getCount() + " left over, " + full);
		helper.assertTrue(SupplyContainers.count(level, List.of(upgraded, plain), Items.COBBLESTONE) == empty,
			"the barrels hold " + SupplyContainers.count(level, List.of(upgraded, plain), Items.COBBLESTONE) + " of " + empty);
		helper.assertTrue(SupplyContainers.insert(level, store, new ItemStack(Items.COBBLESTONE, 1)).getCount() == 1, "full, the controller took one more");
		helper.succeed();
	}

	/** Sets {@code moddedStorageNetworks} for this test and puts it back afterwards. */
	private static void networks(GameTestHelper helper, boolean on) {
		boolean was = StorageNetworks.ENABLED;
		StorageNetworks.ENABLED = on;
		PartnerShowsCompatTests.after(helper, () -> StorageNetworks.ENABLED = was);
	}

	private static BlockPos storehouse(GameTestHelper helper, BlockPos rel) {
		return helper.absolutePos(rel);
	}

	/** Sets the block and lets it react as to a player's placing it (the controller links its storage then). */
	static void placeAsPlayer(ServerLevel level, BlockPos pos, Block block, net.minecraft.world.entity.LivingEntity placer) {
		level.setBlockAndUpdate(pos, block.defaultBlockState());
		BlockState state = level.getBlockState(pos);
		block.setPlacedBy(level, pos, state, placer, new ItemStack(block.asItem()));
	}

	private static Component linkedLine(ServerPlayer player, BlockPos storehouse, GameTestHelper helper) {
		ItemLore lore = StorehouseBoard.boardForTest(player, helper.absolutePos(storehouse)).icon(StorehouseBoard.INFO_SLOT).get(DataComponents.LORE);
		helper.assertTrue(lore != null, "the board's info has no text");
		return lore.lines().stream().filter(l -> l.getContents() instanceof TranslatableContents t && t.getKey().equals(LINKED)).findFirst().orElse(null);
	}

	private static String arg(Component line) {
		return String.valueOf(((TranslatableContents) line.getContents()).getArgs()[0]);
	}

	static Block block(String id) {
		return BuiltInRegistries.BLOCK.getOptional(ResourceLocation.fromNamespaceAndPath(MOD, id))
			.orElseThrow(() -> new GameTestAssertException("no block " + MOD + ":" + id));
	}

	/**
	 * A builder adds a storage link to a controller's network (touching a barrel the controller links, two blocks from the
	 * controller) with a barrel behind it: once the builder's after-block hook has run, what lies in that barrel is counted
	 * once at the Storehouse (through the controller, or as a store of its own), never dropped.
	 */
	//$ gametest_ticks_batch AREA '400' '"sophisticated_controller_link"'
	@GameTest(template = AREA, timeoutTicks = 400, batch = "sophisticated_controller_link")
	public void aLinkBuiltOntoTheNetworkLosesNothing(GameTestHelper helper) {
		PartnerShowsCompatTests.clearLeftovers(helper);
		networks(helper, true);
		ServerLevel level = helper.getLevel();
		BlockPos storehouse = helper.absolutePos(new BlockPos(2, 2, 2));
		helper.setBlock(new BlockPos(2, 2, 2), ModBlocks.STOREHOUSE);
		ServerPlayer player = helper.makeMockServerPlayerInLevel();
		PartnerShowsCompatTests.after(helper, () -> level.getServer().getPlayerList().remove(player));
		BlockPos first = helper.absolutePos(new BlockPos(4, 2, 4));
		level.setBlockAndUpdate(first, block("barrel").defaultBlockState());
		BlockPos controller = helper.absolutePos(new BlockPos(3, 2, 4));
		placeAsPlayer(level, controller, block("controller"), player);
		helper.assertTrue(SupplyContainers.insert(level, List.of(first), new ItemStack(Items.COBBLESTONE, 10)).isEmpty(), "the first barrel took nothing");
		// The barrel behind the link, then the link between it and the first barrel (its back, which it links, east).
		BlockPos behind = helper.absolutePos(new BlockPos(6, 2, 4));
		level.setBlockAndUpdate(behind, block("barrel").defaultBlockState());
		helper.assertTrue(SupplyContainers.insert(level, List.of(behind), new ItemStack(Items.OAK_PLANKS, 7)).isEmpty(), "the barrel behind took nothing");
		BlockPos link = helper.absolutePos(new BlockPos(5, 2, 4));
		level.setBlock(link, TomsStorageCompatTests.facing(block("storage_link").defaultBlockState(), net.minecraft.core.Direction.WEST), Block.UPDATE_ALL);
		StorageNetworks.afterBuilt(level, link, level.getBlockState(link));
		helper.succeedWhen(() -> {
			List<BlockPos> store = SupplyContainers.find(level, storehouse, null);
			long planks = SupplyContainers.count(level, store, Items.OAK_PLANKS);
			long cobble = SupplyContainers.count(level, store, Items.COBBLESTONE);
			helper.assertTrue(planks == 7 && cobble == 10, "the Storehouse " + store + " counts " + planks + " of the 7 planks behind the link and "
				+ cobble + " of the 10 cobblestone; linked by our walk: " + StorageNetworks.linkedTo(level, controller));
			// Nearest first (a builder takes from the nearest store): the controller, then the barrel behind the link.
			helper.assertTrue(store.equals(List.of(controller, behind)), "the Storehouse's stores " + store + ", wanted the controller "
				+ controller + " then the barrel " + behind + ", nearest first");
		});
	}

	/**
	 * A storage link a player has linked to the controller with the mod's Storage Tool: the barrel behind it is the
	 * controller's, counted once through it (the tool's linking is the mod's own {@code ILinkable.linkToController}, called
	 * by name, as the tests have the mod only at run time).
	 */
	//$ gametest_ticks_batch AREA '400' '"sophisticated_controller_tool_link"'
	@GameTest(template = AREA, timeoutTicks = 400, batch = "sophisticated_controller_tool_link")
	public void aToolLinkedLinkCountsWhatLiesBehindItOnce(GameTestHelper helper) {
		PartnerShowsCompatTests.clearLeftovers(helper);
		networks(helper, true);
		ServerLevel level = helper.getLevel();
		BlockPos storehouse = helper.absolutePos(new BlockPos(2, 2, 2));
		helper.setBlock(new BlockPos(2, 2, 2), ModBlocks.STOREHOUSE);
		ServerPlayer player = helper.makeMockServerPlayerInLevel();
		PartnerShowsCompatTests.after(helper, () -> level.getServer().getPlayerList().remove(player));
		BlockPos first = helper.absolutePos(new BlockPos(4, 2, 4));
		level.setBlockAndUpdate(first, block("barrel").defaultBlockState());
		BlockPos controller = helper.absolutePos(new BlockPos(3, 2, 4));
		placeAsPlayer(level, controller, block("controller"), player);
		helper.assertTrue(SupplyContainers.insert(level, List.of(first), new ItemStack(Items.COBBLESTONE, 10)).isEmpty(), "the first barrel took nothing");
		BlockPos behind = helper.absolutePos(new BlockPos(6, 2, 4));
		level.setBlockAndUpdate(behind, block("barrel").defaultBlockState());
		helper.assertTrue(SupplyContainers.insert(level, List.of(behind), new ItemStack(Items.OAK_PLANKS, 7)).isEmpty(), "the barrel behind took nothing");
		BlockPos link = helper.absolutePos(new BlockPos(5, 2, 4));
		level.setBlock(link, TomsStorageCompatTests.facing(block("storage_link").defaultBlockState(), net.minecraft.core.Direction.WEST), Block.UPDATE_ALL);
		net.minecraft.world.level.block.entity.BlockEntity be = level.getBlockEntity(link);
		helper.assertTrue(be != null, "the link has no block entity");
		try {
			be.getClass().getMethod("linkToController", BlockPos.class).invoke(be, controller);
		} catch (ReflectiveOperationException e) {
			throw new GameTestAssertException("the link can't be linked: " + e);
		}
		helper.succeedWhen(() -> {
			List<BlockPos> store = SupplyContainers.find(level, storehouse, null);
			long planks = SupplyContainers.count(level, store, Items.OAK_PLANKS);
			long cobble = SupplyContainers.count(level, store, Items.COBBLESTONE);
			helper.assertTrue(planks == 7 && cobble == 10 && !store.contains(behind) && store.contains(controller), "the Storehouse " + store
				+ " counts " + planks + " of the 7 planks behind the linked link and " + cobble + " of the 10 cobblestone; linked by our walk: "
				+ StorageNetworks.linkedTo(level, controller));
		});
	}

	/**
	 * A storage link linked with the Storage Tool to a controller ten blocks away, with nothing between them (no cables,
	 * no storage): the controller reaches the barrel behind it all the same, so the Storehouse counts what lies in that
	 * barrel once, through the controller, and not again as a store of its own (B262).
	 */
	//$ gametest_ticks_batch AREA '400' '"sophisticated_controller_far_link"'
	@GameTest(template = AREA, timeoutTicks = 400, batch = "sophisticated_controller_far_link")
	public void aFarToolLinkedLinkCountsItsBarrelOnce(GameTestHelper helper) {
		PartnerShowsCompatTests.clearLeftovers(helper);
		networks(helper, true);
		ServerLevel level = helper.getLevel();
		BlockPos storehouse = helper.absolutePos(new BlockPos(8, 2, 8));
		helper.setBlock(new BlockPos(8, 2, 8), ModBlocks.STOREHOUSE);
		ServerPlayer player = helper.makeMockServerPlayerInLevel();
		PartnerShowsCompatTests.after(helper, () -> level.getServer().getPlayerList().remove(player));
		BlockPos first = helper.absolutePos(new BlockPos(1, 2, 9));
		level.setBlockAndUpdate(first, block("barrel").defaultBlockState());
		BlockPos controller = helper.absolutePos(new BlockPos(1, 2, 8));
		placeAsPlayer(level, controller, block("controller"), player);
		helper.assertTrue(SupplyContainers.insert(level, List.of(first), new ItemStack(Items.COBBLESTONE, 10)).isEmpty(), "the first barrel took nothing");
		// The far end: a barrel, and the link before it (its back, which it links, east), ten blocks from the controller.
		BlockPos behind = helper.absolutePos(new BlockPos(12, 2, 8));
		level.setBlockAndUpdate(behind, block("barrel").defaultBlockState());
		helper.assertTrue(SupplyContainers.insert(level, List.of(behind), new ItemStack(Items.OAK_PLANKS, 7)).isEmpty(), "the barrel behind took nothing");
		BlockPos link = helper.absolutePos(new BlockPos(11, 2, 8));
		level.setBlock(link, TomsStorageCompatTests.facing(block("storage_link").defaultBlockState(), net.minecraft.core.Direction.WEST), Block.UPDATE_ALL);
		net.minecraft.world.level.block.entity.BlockEntity be = level.getBlockEntity(link);
		helper.assertTrue(be != null, "the link has no block entity");
		try {
			be.getClass().getMethod("linkToController", BlockPos.class).invoke(be, controller);
		} catch (ReflectiveOperationException e) {
			throw new GameTestAssertException("the link can't be linked: " + e);
		}
		helper.succeedWhen(() -> {
			List<BlockPos> store = SupplyContainers.find(level, storehouse, null);
			long planks = SupplyContainers.count(level, store, Items.OAK_PLANKS);
			long cobble = SupplyContainers.count(level, store, Items.COBBLESTONE);
			helper.assertTrue(planks == 7 && cobble == 10 && !store.contains(behind) && store.contains(controller), "the Storehouse " + store
				+ " counts " + planks + " of the 7 planks behind the far linked link and " + cobble + " of the 10 cobblestone; linked by our walk: "
				+ StorageNetworks.linkedTo(level, controller));
		});
	}

	/**
	 * A storage link a player has linked with the Storage Tool to another controller, standing beside this controller's
	 * barrel: this controller doesn't reach through it (the link isn't its), so the barrel behind it isn't this
	 * controller's, and the Storehouse still counts what lies in it once.
	 */
	//$ gametest_ticks_batch AREA '400' '"sophisticated_controller_other_link"'
	@GameTest(template = AREA, timeoutTicks = 400, batch = "sophisticated_controller_other_link")
	public void aLinkToolLinkedToAnotherControllerIsntThisOnes(GameTestHelper helper) {
		PartnerShowsCompatTests.clearLeftovers(helper);
		networks(helper, true);
		ServerLevel level = helper.getLevel();
		BlockPos storehouse = helper.absolutePos(new BlockPos(2, 2, 2));
		helper.setBlock(new BlockPos(2, 2, 2), ModBlocks.STOREHOUSE);
		ServerPlayer player = helper.makeMockServerPlayerInLevel();
		PartnerShowsCompatTests.after(helper, () -> level.getServer().getPlayerList().remove(player));
		BlockPos first = helper.absolutePos(new BlockPos(4, 2, 4));
		level.setBlockAndUpdate(first, block("barrel").defaultBlockState());
		BlockPos controller = helper.absolutePos(new BlockPos(3, 2, 4));
		placeAsPlayer(level, controller, block("controller"), player);
		// The other controller, touching nothing, within reach of the link.
		BlockPos other = helper.absolutePos(new BlockPos(10, 2, 10));
		placeAsPlayer(level, other, block("controller"), player);
		helper.assertTrue(SupplyContainers.insert(level, List.of(first), new ItemStack(Items.COBBLESTONE, 10)).isEmpty(), "the first barrel took nothing");
		BlockPos behind = helper.absolutePos(new BlockPos(6, 2, 4));
		level.setBlockAndUpdate(behind, block("barrel").defaultBlockState());
		helper.assertTrue(SupplyContainers.insert(level, List.of(behind), new ItemStack(Items.OAK_PLANKS, 7)).isEmpty(), "the barrel behind took nothing");
		BlockPos link = helper.absolutePos(new BlockPos(5, 2, 4));
		level.setBlock(link, TomsStorageCompatTests.facing(block("storage_link").defaultBlockState(), net.minecraft.core.Direction.WEST), Block.UPDATE_ALL);
		net.minecraft.world.level.block.entity.BlockEntity be = level.getBlockEntity(link);
		helper.assertTrue(be != null, "the link has no block entity");
		try {
			be.getClass().getMethod("linkToController", BlockPos.class).invoke(be, other);
		} catch (ReflectiveOperationException e) {
			throw new GameTestAssertException("the link can't be linked: " + e);
		}
		helper.succeedWhen(() -> {
			java.util.Set<BlockPos> linked = StorageNetworks.linkedTo(level, controller);
			helper.assertTrue(linked.equals(java.util.Set.of(first)), "the controller links " + linked + ", wanted only its own barrel " + first
				+ " (the link behind it is the other controller's)");
			List<BlockPos> store = SupplyContainers.find(level, storehouse, null);
			long planks = SupplyContainers.count(level, store, Items.OAK_PLANKS);
			long cobble = SupplyContainers.count(level, store, Items.COBBLESTONE);
			helper.assertTrue(planks == 7 && cobble == 10, "the Storehouse " + store + " counts " + planks + " of the 7 planks behind the other controller's link and "
				+ cobble + " of the 10 cobblestone");
		});
	}

	/**
	 * The controller links storage up to 15 blocks away each way, the 15th included (as the mod's own
	 * {@code isWithinRange}): rows of 16 barrels running east, south and up from it join up to the 15th, not the 16th,
	 * and the controller itself agrees (it holds each 15th barrel's glass, not a 16th's planks).
	 */
	//$ gametest_ticks_batch HUGE_AREA '400' '"sophisticated_controller_reach"'
	@GameTest(template = HUGE_AREA, timeoutTicks = 400, batch = "sophisticated_controller_reach")
	public void theSixteenthBarrelEachWayIsOutOfTheControllersReach(GameTestHelper helper) {
		PartnerShowsCompatTests.clearLeftovers(helper);
		networks(helper, true);
		ServerLevel level = helper.getLevel();
		BlockPos start = new BlockPos(1, 2, 1);
		List<net.minecraft.core.Direction> ways = List.of(net.minecraft.core.Direction.EAST, net.minecraft.core.Direction.SOUTH, net.minecraft.core.Direction.UP);
		for (net.minecraft.core.Direction way : ways) {
			for (int i = 1; i <= 16; i++) {
				level.setBlockAndUpdate(helper.absolutePos(start.relative(way, i)), block("barrel").defaultBlockState());
			}
			helper.assertTrue(SupplyContainers.insert(level, List.of(helper.absolutePos(start.relative(way, 15))), new ItemStack(Items.GLASS, 1)).isEmpty(), "a 15th barrel took nothing");
			helper.assertTrue(SupplyContainers.insert(level, List.of(helper.absolutePos(start.relative(way, 16))), new ItemStack(Items.OAK_PLANKS, 1)).isEmpty(), "a 16th barrel took nothing");
		}
		BlockPos controller = helper.absolutePos(start);
		placeAsPlayer(level, controller, block("controller"), null);
		helper.succeedWhen(() -> {
			java.util.Set<BlockPos> linked = StorageNetworks.linkedTo(level, controller);
			List<String> wrong = new java.util.ArrayList<>();
			for (net.minecraft.core.Direction way : ways) {
				if (!linked.contains(helper.absolutePos(start.relative(way, 15)))) {
					wrong.add("the 15th barrel " + way + " isn't linked");
				}
				if (linked.contains(helper.absolutePos(start.relative(way, 16)))) {
					wrong.add("the 16th barrel " + way + " is linked");
				}
			}
			helper.assertTrue(wrong.isEmpty() && linked.size() == 45, "the controller links " + linked.size() + " barrels, wanted 45: " + wrong);
			long glass = SupplyContainers.count(level, List.of(controller), Items.GLASS);
			long planks = SupplyContainers.count(level, List.of(controller), Items.OAK_PLANKS);
			helper.assertTrue(glass == 3 && planks == 0, "the mod's controller holds " + glass + " of the 15th barrels' 3 glass and " + planks
				+ " of the 16th barrels' planks, wanted none");
		});
	}

	/**
	 * A builder adds a barrel behind a storage link a player has linked to the controller with the Storage Tool (the
	 * barrel touches only the link): once the builder's after-block hook has run, the controller links the new barrel
	 * too, so what lies in it is counted through the controller, once.
	 */
	//$ gametest_ticks_batch AREA '400' '"sophisticated_controller_behind_link"'
	@GameTest(template = AREA, timeoutTicks = 400, batch = "sophisticated_controller_behind_link")
	public void aBarrelBuiltBehindAToolLinkedLinkIsLinked(GameTestHelper helper) {
		PartnerShowsCompatTests.clearLeftovers(helper);
		networks(helper, true);
		ServerLevel level = helper.getLevel();
		BlockPos storehouse = helper.absolutePos(new BlockPos(2, 2, 2));
		helper.setBlock(new BlockPos(2, 2, 2), ModBlocks.STOREHOUSE);
		ServerPlayer player = helper.makeMockServerPlayerInLevel();
		PartnerShowsCompatTests.after(helper, () -> level.getServer().getPlayerList().remove(player));
		BlockPos first = helper.absolutePos(new BlockPos(4, 2, 4));
		level.setBlockAndUpdate(first, block("barrel").defaultBlockState());
		BlockPos controller = helper.absolutePos(new BlockPos(3, 2, 4));
		placeAsPlayer(level, controller, block("controller"), player);
		BlockPos link = helper.absolutePos(new BlockPos(5, 2, 4));
		level.setBlock(link, TomsStorageCompatTests.facing(block("storage_link").defaultBlockState(), net.minecraft.core.Direction.WEST), Block.UPDATE_ALL);
		net.minecraft.world.level.block.entity.BlockEntity be = level.getBlockEntity(link);
		helper.assertTrue(be != null, "the link has no block entity");
		try {
			be.getClass().getMethod("linkToController", BlockPos.class).invoke(be, controller);
		} catch (ReflectiveOperationException e) {
			throw new GameTestAssertException("the link can't be linked: " + e);
		}
		// The builder sets a barrel behind the link (touching only the link) and calls its hook.
		BlockPos added = helper.absolutePos(new BlockPos(6, 2, 4));
		level.setBlock(added, block("barrel").defaultBlockState(), Block.UPDATE_ALL);
		StorageNetworks.afterBuilt(level, added, level.getBlockState(added));
		helper.assertTrue(SupplyContainers.insert(level, List.of(added), new ItemStack(Items.OAK_PLANKS, 7)).isEmpty(), "the added barrel took nothing");
		helper.succeedWhen(() -> {
			long through = SupplyContainers.count(level, List.of(controller), Items.OAK_PLANKS);
			List<BlockPos> store = SupplyContainers.find(level, storehouse, null);
			long counted = SupplyContainers.count(level, store, Items.OAK_PLANKS);
			helper.assertTrue(through == 7 && counted == 7, "the controller holds " + through + " and the Storehouse " + store + " counts " + counted
				+ " of the built barrel's 7 planks");
		});
	}
}
