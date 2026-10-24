package io.github.jcondedata.aliveworkplace.compat;

import io.github.jcondedata.aliveworkplace.build.BuildSiteManager;
import io.github.jcondedata.aliveworkplace.build.SupplyContainers;
import io.github.jcondedata.aliveworkplace.platform.ItemStores;
import io.github.jcondedata.aliveworkplace.registry.ModBlocks;
import io.github.jcondedata.aliveworkplace.store.StorageNetworks;
import io.github.jcondedata.aliveworkplace.store.StorehouseBoard;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponents;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.ItemLore;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

/**
 * QA (lane-c-1024-2152), written from the specs of ROADMAP 37.8 and 37.9 rather than the code, at the boundaries the
 * earlier tests leave open: a stack-upgraded slot holding exactly a stack, a stack and one, and its whole room; the
 * board's "linked through a storage controller" count right after a block joins or leaves the network (one block
 * included); a builder clearing a site where a controller and its barrel stand; a controller's network saved and
 * loaded again, counted once; and a barrel just beyond the controller's reach staying a store of its own.
 */
public class QaSophisticatedStorageCompatTests implements net.fabricmc.fabric.api.gametest.v1.FabricGameTest {
	private static final String AREA = CompatGameTests.AREA;
	private static final String HUGE_AREA = "aliveworkplace_compat:huge_area";
	private static final String LINKED = "screen.aliveworkplace.storehouse.linked";

	/**
	 * 37.8: free room is measured in items, slot by slot. A barrel with a stack upgrade: one slot holding exactly 64
	 * cobblestone still has room (a stack is not full here), holding 65 one less, and filled to its own room it has none,
	 * while the other 26 slots keep theirs; the board's room line says the same number each time.
	 */
	//$ gametest_batch AREA '"qa_soph_stack_edge"'
	@GameTest(template = AREA, batch = "qa_soph_stack_edge")
	public void qaAStackUpgradedSlotIsMeasuredInItemsAtTheStackEdge(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		BlockPos storehouse = new BlockPos(2, 2, 2);
		BlockPos barrel = new BlockPos(2, 2, 4);
		helper.setBlock(storehouse, ModBlocks.STOREHOUSE);
		helper.setBlock(barrel, SophisticatedControllerCompatTests.block("barrel"));
		List<BlockPos> store = SupplyContainers.find(level, helper.absolutePos(storehouse), null);
		helper.assertTrue(store.equals(List.of(helper.absolutePos(barrel))), "the store: " + store);
		ServerPlayer player = helper.makeMockServerPlayerInLevel();
		PartnerShowsCompatTests.after(helper, () -> level.getServer().getPlayerList().remove(player));
		BlockPos at = helper.absolutePos(barrel);
		player.teleportTo(at.getX() + 0.5, at.getY(), at.getZ() + 2.5);
		ItemStack upgrade = new ItemStack(SophisticatedStorageCompatTests.item("stack_upgrade_tier_2"));
		player.setItemInHand(InteractionHand.MAIN_HAND, upgrade);
		player.setShiftKeyDown(true);
		Direction front = SophisticatedStorageCompatTests.front(level.getBlockState(at));
		player.gameMode.useItemOn(player, level, upgrade, InteractionHand.MAIN_HAND,
			new BlockHitResult(Vec3.atCenterOf(at).relative(front, 0.5), front, at, false));
		player.setShiftKeyDown(false);

		ItemStores.Room up = SupplyContainers.room(level, store);
		helper.assertTrue(up.bigSlots() && up.freeSlots() == 27 && up.freeItems() % 27 == 0 && up.freeItems() > 27L * 64, "upgraded: " + up);
		long perSlot = up.freeItems() / 27;

		helper.assertTrue(SupplyContainers.insert(level, store, new ItemStack(Items.COBBLESTONE, 64)).isEmpty(), "64 cobblestone didn't go in");
		ItemStores.Room sixtyFour = SupplyContainers.room(level, store);
		helper.assertTrue(sixtyFour.freeSlots() == 26 && sixtyFour.freeItems() == up.freeItems() - 64,
			"one slot holding exactly a stack of " + perSlot + ": " + sixtyFour + ", wanted " + (up.freeItems() - 64) + " items free");
		helper.assertTrue(sixtyFour.freeStacks() == 26 * (perSlot / 64) + (perSlot - 64) / 64, "free stacks with 64 in: " + sixtyFour);
		boardSays(helper, player, storehouse, sixtyFour.freeItems());

		helper.assertTrue(SupplyContainers.insert(level, store, new ItemStack(Items.COBBLESTONE, 1)).isEmpty(), "the 65th cobblestone didn't go in");
		ItemStores.Room sixtyFive = SupplyContainers.room(level, store);
		helper.assertTrue(sixtyFive.freeSlots() == 26 && sixtyFive.freeItems() == up.freeItems() - 65, "65 in one slot: " + sixtyFive);
		helper.assertTrue(sixtyFive.freeStacks() == 26 * (perSlot / 64) + (perSlot - 65) / 64, "free stacks with 65 in: " + sixtyFive);
		boardSays(helper, player, storehouse, sixtyFive.freeItems());

		helper.assertTrue(SupplyContainers.insert(level, store, new ItemStack(Items.COBBLESTONE, (int) perSlot - 65)).isEmpty(), "the slot didn't fill");
		ItemStores.Room slotFull = SupplyContainers.room(level, store);
		helper.assertTrue(slotFull.freeSlots() == 26 && slotFull.freeItems() == 26 * perSlot && slotFull.freeStacks() == 26 * (perSlot / 64),
			"one slot full (" + perSlot + "), 26 empty: " + slotFull + ", wanted " + (26 * perSlot) + " items free");
		helper.assertTrue(SupplyContainers.count(level, store, Items.COBBLESTONE) == perSlot, "counts " + SupplyContainers.count(level, store, Items.COBBLESTONE));
		boardSays(helper, player, storehouse, slotFull.freeItems());
		helper.succeed();
	}

	/**
	 * 37.9: the board's "Linked through a storage controller: N blocks" follows the network as it changes: 2 barrels, a
	 * third built onto them (3), one broken (2), another broken ("1 block", the lang file's one-block sentence), and what
	 * lies in the barrels left is counted once each time.
	 */
	//$ gametest_ticks_batch AREA '300' '"qa_soph_linked_count"'
	@GameTest(template = AREA, timeoutTicks = 300, batch = "qa_soph_linked_count")
	public void qaTheLinkedCountFollowsBlocksAddedAndBroken(GameTestHelper helper) {
		PartnerShowsCompatTests.clearLeftovers(helper);
		networksOn(helper);
		ServerLevel level = helper.getLevel();
		BlockPos storehouse = new BlockPos(2, 2, 2);
		helper.setBlock(storehouse, ModBlocks.STOREHOUSE);
		BlockPos at = helper.absolutePos(storehouse);
		ServerPlayer player = helper.makeMockServerPlayerInLevel();
		PartnerShowsCompatTests.after(helper, () -> level.getServer().getPlayerList().remove(player));
		player.teleportTo(at.getX() + 0.5, at.getY(), at.getZ() + 1.5);
		List<BlockPos> barrels = List.of(helper.absolutePos(new BlockPos(4, 2, 4)), helper.absolutePos(new BlockPos(5, 2, 4)),
			helper.absolutePos(new BlockPos(6, 2, 4)));
		for (int i = 0; i < 2; i++) {
			level.setBlockAndUpdate(barrels.get(i), SophisticatedControllerCompatTests.block("barrel").defaultBlockState());
			helper.assertTrue(SupplyContainers.insert(level, List.of(barrels.get(i)), new ItemStack(Items.COBBLESTONE, 10)).isEmpty(), "a barrel took nothing");
		}
		BlockPos controller = helper.absolutePos(new BlockPos(3, 2, 4));
		SophisticatedControllerCompatTests.placeAsPlayer(level, controller, SophisticatedControllerCompatTests.block("controller"), player);
		linkedSays(helper, player, storehouse, 2, 20);

		// A builder adds the third barrel on the end and runs its after-block hook.
		level.setBlock(barrels.get(2), SophisticatedControllerCompatTests.block("barrel").defaultBlockState(), Block.UPDATE_ALL);
		StorageNetworks.afterBuilt(level, barrels.get(2), level.getBlockState(barrels.get(2)));
		helper.assertTrue(SupplyContainers.insert(level, List.of(barrels.get(2)), new ItemStack(Items.COBBLESTONE, 10)).isEmpty(), "the third barrel took nothing");
		helper.runAfterDelay(5, () -> {
			linkedSays(helper, player, storehouse, 3, 30);
			level.destroyBlock(barrels.get(2), false);
			helper.runAfterDelay(5, () -> {
				linkedSays(helper, player, storehouse, 2, 20);
				level.destroyBlock(barrels.get(1), false);
				helper.runAfterDelay(5, () -> {
					Component one = linkedSays(helper, player, storehouse, 1, 10);
					helper.assertTrue(one.getString().equals("Linked through a storage controller: 1 block"), "one linked block reads " + one.getString());
					helper.succeed();
				});
			});
		});
	}

	/**
	 * 37.8 and 37.9: where the blueprint wants planks, a controller and the barrel it links (30 cobblestone) stand on
	 * the site: the builder finishes without breaking either, the barrel still holds its 30 and the controller still
	 * reaches them, and no plank is made or lost.
	 */
	//$ gametest_ticks_batch AREA '2400' '"qa_soph_clear_controller"'
	@GameTest(template = AREA, timeoutTicks = 2400, batch = "qa_soph_clear_controller")
	public void qaABuilderClearingLeavesAControllerAndItsBarrel(GameTestHelper helper) {
		PartnerShowsCompatTests.clearLeftovers(helper);
		networksOn(helper);
		ServerLevel level = helper.getLevel();
		Map<BlockPos, BlockState> design = new HashMap<>();
		for (int x = 0; x < 4; x++) {
			design.put(new BlockPos(x, 0, 0), Blocks.OAK_PLANKS.defaultBlockState());
		}
		ResourceLocation id = CompatGameTests.blueprintFrom(helper, "qa_soph_clear_controller", design, Map.of());
		BlockPos controllerAt = helper.absolutePos(CompatGameTests.ORIGIN.offset(1, 0, 0));
		BlockPos barrelAt = helper.absolutePos(CompatGameTests.ORIGIN.offset(2, 0, 0));
		level.setBlockAndUpdate(barrelAt, SophisticatedControllerCompatTests.block("barrel").defaultBlockState());
		helper.assertTrue(SupplyContainers.insert(level, List.of(barrelAt), new ItemStack(Items.COBBLESTONE, 30)).isEmpty(), "the barrel took nothing");
		SophisticatedControllerCompatTests.placeAsPlayer(level, controllerAt, SophisticatedControllerCompatTests.block("controller"), null);
		helper.assertTrue(SupplyContainers.count(level, List.of(controllerAt), Items.COBBLESTONE) == 30, "the controller doesn't reach the barrel");
		CompatGameTests.Setup s = CompatGameTests.setup(helper, id, new ItemStack(Items.OAK_PLANKS, 4));
		List<BlockPos> benchChest = List.of(helper.absolutePos(CompatGameTests.CHEST));
		helper.succeedWhen(() -> {
			helper.assertTrue(BuildSiteManager.get(level).get(s.site().id()) == null,
				"still building: stage=" + s.site().stage() + " status=" + s.site().status() + " missing=" + s.site().missing());
			helper.assertTrue(level.getBlockState(controllerAt).is(SophisticatedControllerCompatTests.block("controller")), "the controller was broken: " + level.getBlockState(controllerAt));
			helper.assertTrue(level.getBlockState(barrelAt).is(SophisticatedControllerCompatTests.block("barrel")), "the barrel was broken: " + level.getBlockState(barrelAt));
			helper.assertTrue(SupplyContainers.count(level, List.of(barrelAt), Items.COBBLESTONE) == 30, SupplyContainers.count(level, List.of(barrelAt), Items.COBBLESTONE) + " of 30 cobblestone left");
			helper.assertTrue(SupplyContainers.count(level, List.of(controllerAt), Items.COBBLESTONE) == 30, "the controller no longer reaches the barrel");
			helper.assertTrue(level.getBlockState(s.at(new BlockPos(0, 0, 0))).is(Blocks.OAK_PLANKS) && level.getBlockState(s.at(new BlockPos(3, 0, 0))).is(Blocks.OAK_PLANKS),
				"the free ends of the wall weren't built");
			long planks = SupplyContainers.count(level, benchChest, Items.OAK_PLANKS) + s.villager().getInventory().countItem(Items.OAK_PLANKS);
			helper.assertTrue(planks == 2, planks + " planks left of 4 after placing 2");
			helper.assertTrue(SupplyContainers.count(level, benchChest, Items.COBBLESTONE) == 0 && s.villager().getInventory().countItem(Items.COBBLESTONE) == 0,
				"the barrel's cobblestone was copied to the builder");
		});
	}

	/**
	 * 37.9, don't break saves: a controller linking three barrels (10 cobblestone each) is saved and loaded again, with
	 * its barrels (each block entity's saved data read back into it): the Storehouse still lists only the
	 * controller, counts 30 (not 60), the board still says 3 blocks, and a delivery and a take go through it exactly.
	 */
	//$ gametest_ticks_batch AREA '300' '"qa_soph_controller_reload"'
	@GameTest(template = AREA, timeoutTicks = 300, batch = "qa_soph_controller_reload")
	public void qaAControllerSavedAndLoadedCountsOnce(GameTestHelper helper) {
		PartnerShowsCompatTests.clearLeftovers(helper);
		networksOn(helper);
		ServerLevel level = helper.getLevel();
		BlockPos storehouse = new BlockPos(2, 2, 2);
		helper.setBlock(storehouse, ModBlocks.STOREHOUSE);
		BlockPos at = helper.absolutePos(storehouse);
		ServerPlayer player = helper.makeMockServerPlayerInLevel();
		PartnerShowsCompatTests.after(helper, () -> level.getServer().getPlayerList().remove(player));
		player.teleportTo(at.getX() + 0.5, at.getY(), at.getZ() + 1.5);
		List<BlockPos> barrels = List.of(helper.absolutePos(new BlockPos(4, 2, 4)), helper.absolutePos(new BlockPos(5, 2, 4)),
			helper.absolutePos(new BlockPos(6, 2, 4)));
		for (BlockPos b : barrels) {
			level.setBlockAndUpdate(b, SophisticatedControllerCompatTests.block("barrel").defaultBlockState());
			helper.assertTrue(SupplyContainers.insert(level, List.of(b), new ItemStack(Items.COBBLESTONE, 10)).isEmpty(), "a barrel took nothing");
		}
		BlockPos controller = helper.absolutePos(new BlockPos(3, 2, 4));
		SophisticatedControllerCompatTests.placeAsPlayer(level, controller, SophisticatedControllerCompatTests.block("controller"), player);
		linkedSays(helper, player, storehouse, 3, 30);

		List<BlockPos> reloaded = new java.util.ArrayList<>(barrels);
		reloaded.add(controller);
		reloaded.add(at);
		for (BlockPos p : reloaded) {
			BlockEntity be = level.getBlockEntity(p);
			if (be == null) {
				continue;
			}
			// What the world saves is read back into the block entity, as when its chunk loads.
			CompoundTag disk = be.saveWithFullMetadata(level.registryAccess());
			be.loadWithComponents(disk, level.registryAccess());
			be.setChanged();
		}
		helper.runAfterDelay(5, () -> {
			helper.assertTrue(SupplyContainers.count(level, List.of(controller), Items.COBBLESTONE) == 30 && SupplyContainers.count(level, barrels, Items.COBBLESTONE) == 30,
				"after loading, the controller holds " + SupplyContainers.count(level, List.of(controller), Items.COBBLESTONE) + " and the barrels "
				+ SupplyContainers.count(level, barrels, Items.COBBLESTONE) + " of 30 cobblestone");
			List<BlockPos> store = SupplyContainers.find(level, at, null);
			helper.assertTrue(store.equals(List.of(controller)), "after loading, the Storehouse's stores: " + store);
			linkedSays(helper, player, storehouse, 3, 30);
			helper.assertTrue(SupplyContainers.insert(level, store, new ItemStack(Items.RAW_IRON, 6)).isEmpty(), "after loading, the controller took no raw iron");
			helper.assertTrue(SupplyContainers.count(level, barrels, Items.RAW_IRON) == 6 && SupplyContainers.count(level, store, Items.RAW_IRON) == 6,
				"6 raw iron put in: the barrels hold " + SupplyContainers.count(level, barrels, Items.RAW_IRON) + ", the store counts " + SupplyContainers.count(level, store, Items.RAW_IRON));
			int taken = SupplyContainers.extract(level, store, Items.COBBLESTONE, 100);
			helper.assertTrue(taken == 30 && SupplyContainers.count(level, barrels, Items.COBBLESTONE) == 0, "a builder asking for 100 got " + taken + " of 30");
			helper.succeed();
		});
	}

	/**
	 * 37.9: "within the controller's reach". A row of 16 barrels east of a controller, all within the Storehouse's 8
	 * blocks: the 15th (1 glass) is the controller's, the 16th (3 planks) stays a store of its own, listed beside the
	 * controller, its planks counted once and given to a builder exactly.
	 */
	//$ gametest_ticks_batch HUGE_AREA '300' '"qa_soph_out_of_reach"'
	@GameTest(template = HUGE_AREA, timeoutTicks = 300, batch = "qa_soph_out_of_reach")
	public void qaABarrelJustBeyondTheControllersReachIsItsOwnStore(GameTestHelper helper) {
		PartnerShowsCompatTests.clearLeftovers(helper);
		networksOn(helper);
		ServerLevel level = helper.getLevel();
		BlockPos start = new BlockPos(1, 2, 1);
		BlockPos storehouse = helper.absolutePos(new BlockPos(9, 2, 3));
		helper.setBlock(new BlockPos(9, 2, 3), ModBlocks.STOREHOUSE);
		for (int i = 1; i <= 16; i++) {
			level.setBlockAndUpdate(helper.absolutePos(start.east(i)), SophisticatedControllerCompatTests.block("barrel").defaultBlockState());
		}
		BlockPos fifteenth = helper.absolutePos(start.east(15));
		BlockPos sixteenth = helper.absolutePos(start.east(16));
		helper.assertTrue(SupplyContainers.insert(level, List.of(fifteenth), new ItemStack(Items.GLASS, 1)).isEmpty(), "the 15th barrel took nothing");
		helper.assertTrue(SupplyContainers.insert(level, List.of(sixteenth), new ItemStack(Items.OAK_PLANKS, 3)).isEmpty(), "the 16th barrel took nothing");
		BlockPos controller = helper.absolutePos(start);
		SophisticatedControllerCompatTests.placeAsPlayer(level, controller, SophisticatedControllerCompatTests.block("controller"), null);
		helper.succeedWhen(() -> {
			List<BlockPos> store = SupplyContainers.find(level, storehouse, null);
			helper.assertTrue(store.size() == 2 && store.contains(controller) && store.contains(sixteenth), "the Storehouse's stores: " + store
				+ ", wanted the controller " + controller + " and the 16th barrel " + sixteenth);
			long glass = SupplyContainers.count(level, store, Items.GLASS);
			long planks = SupplyContainers.count(level, store, Items.OAK_PLANKS);
			helper.assertTrue(glass == 1 && planks == 3, "the Storehouse counts " + glass + " of 1 glass and " + planks + " of 3 planks");
			helper.assertTrue(SupplyContainers.extract(level, store, Items.OAK_PLANKS, 10) == 3, "a builder asking for 10 planks didn't get exactly 3");
			helper.assertTrue(SupplyContainers.count(level, List.of(sixteenth), Items.OAK_PLANKS) == 0, "planks left in the 16th barrel");
		});
	}

	/** Checks the board's linked line says {@code n} (the one-block sentence for 1) and the store counts {@code cobble}. */
	private static Component linkedSays(GameTestHelper helper, ServerPlayer player, BlockPos storehouse, int n, long cobble) {
		ServerLevel level = helper.getLevel();
		List<BlockPos> store = SupplyContainers.find(level, helper.absolutePos(storehouse), null);
		long counted = SupplyContainers.count(level, store, Items.COBBLESTONE);
		helper.assertTrue(counted == cobble, "with " + n + " linked, the Storehouse " + store + " counts " + counted + " cobblestone, wanted " + cobble);
		ItemLore lore = StorehouseBoard.boardForTest(player, helper.absolutePos(storehouse)).icon(StorehouseBoard.INFO_SLOT).get(DataComponents.LORE);
		helper.assertTrue(lore != null, "the board's info has no text");
		Component line = lore.lines().stream().filter(l -> l.getContents() instanceof TranslatableContents t && t.getKey().startsWith(LINKED))
			.findFirst().orElse(null);
		helper.assertTrue(line != null, "no linked line on the board with " + n + " linked");
		TranslatableContents t = (TranslatableContents) line.getContents();
		String wantKey = n == 1 ? LINKED + ".one" : LINKED;
		helper.assertTrue(t.getKey().equals(wantKey) && String.valueOf(t.getArgs()[0]).equals(String.valueOf(n)),
			"the board's linked line is " + t.getKey() + " " + String.valueOf(t.getArgs()[0]) + ", wanted " + wantKey + " " + n);
		helper.assertTrue(!line.getString().contains("%") && line.getString().contains(String.valueOf(n)), "the linked line reads " + line.getString());
		return line;
	}

	private static void boardSays(GameTestHelper helper, ServerPlayer player, BlockPos storehouse, long free) {
		Component line = SophisticatedStorageCompatTests.roomLine(SophisticatedStorageCompatTests.info(player, storehouse, helper));
		helper.assertTrue(line != null && String.valueOf(((TranslatableContents) line.getContents()).getArgs()[0]).equals(String.valueOf(free)),
			"the board's room line: " + (line == null ? "none" : line.getString()) + ", wanted " + free);
	}

	private static void networksOn(GameTestHelper helper) {
		boolean was = StorageNetworks.ENABLED;
		StorageNetworks.ENABLED = true;
		PartnerShowsCompatTests.after(helper, () -> StorageNetworks.ENABLED = was);
	}
}
