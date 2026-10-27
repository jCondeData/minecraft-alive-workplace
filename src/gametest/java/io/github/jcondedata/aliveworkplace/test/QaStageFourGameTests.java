package io.github.jcondedata.aliveworkplace.test;

import io.github.jcondedata.aliveworkplace.blueprint.ScanToolItem;
import io.github.jcondedata.aliveworkplace.farm.FieldMarkerItem;
import io.github.jcondedata.aliveworkplace.guard.PatrolMapItem;
import io.github.jcondedata.aliveworkplace.mine.QuarryData;
import io.github.jcondedata.aliveworkplace.mine.QuarryMarkerItem;
import io.github.jcondedata.aliveworkplace.registry.ModAttachments;
import io.github.jcondedata.aliveworkplace.registry.ModBlocks;
import io.github.jcondedata.aliveworkplace.registry.ModComponents;
import io.github.jcondedata.aliveworkplace.registry.ModItems;
import io.github.jcondedata.aliveworkplace.registry.ModVillagers;
import io.github.jcondedata.aliveworkplace.work.Jobs;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.network.protocol.game.ServerboundInteractPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.npc.VillagerProfession;
import net.minecraft.world.entity.ai.village.poi.PoiTypes;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

/**
 * QA stage 4 (2026-10-23): the player's entry points clicked the way a client clicks them, after B333 (a Delivery Note
 * on a chest opened the chest) showed that tests which set an item's data directly miss a vanilla handler taking the
 * click first. Block clicks go through {@link #press}: like the client, the main hand then the off hand, each first as
 * a click on the block ({@code ServerPlayerGameMode.useItemOn}, where the block's own use, the Fabric use-block
 * callbacks and then the item's {@code useOn} run), then as a use of the item. Clicks on a villager go through the
 * real {@code ServerboundInteractPacket} the client sends (where the use-entity callbacks run).
 *
 * <p>Expectations come from README.md and CHANGELOG.md, not the code: the markers ("Right-click one corner block ...,
 * then the opposite corner"; "sneak-right-click the ground to clear it"; "give the marker to any Farmer"; "hand them a
 * marked Quarry Marker"; "sneak-right-click a guard with it"; "give a blank note to end them"), the Remedy ("one used
 * up, the bottle back", 32.18), the Travel Post ("players who right-click it can travel here") and en_us.json.
 */
public class QaStageFourGameTests implements net.fabricmc.fabric.api.gametest.v1.FabricGameTest {
	private static final String AREA = "aliveworkplace_test:build_area";

	// --- How a client clicks ---------------------------------------------------------------------------------------

	static ServerPlayer player(GameTestHelper helper, List<Component> seen, GameType mode) {
		ServerPlayer player = QaStageThreeGameTests.listener(helper, seen);
		player.setGameMode(mode);
		return player;
	}

	/**
	 * A right-click on face {@code face} of {@code rel}, the way the client sends it: for the main hand and then the off
	 * hand, the click on the block, and if that didn't take, the item's own use; it stops at the first that takes.
	 */
	static InteractionResult press(GameTestHelper helper, ServerPlayer player, BlockPos rel, Direction face) {
		ServerLevel level = helper.getLevel();
		BlockPos abs = helper.absolutePos(rel);
		BlockHitResult hit = new BlockHitResult(Vec3.atCenterOf(abs).relative(face, 0.5), face, abs, false);
		player.moveTo(Vec3.atBottomCenterOf(abs).add(1.5, 1, 0), player.getYRot(), player.getXRot());
		for (InteractionHand hand : InteractionHand.values()) {
			ItemStack stack = player.getItemInHand(hand);
			InteractionResult result = player.gameMode.useItemOn(player, level, stack, hand, hit);
			if (result.consumesAction() || result == InteractionResult.FAIL) {
				return result;
			}
			if (!stack.isEmpty()) {
				InteractionResult used = player.gameMode.useItem(player, level, stack, hand);
				if (used.consumesAction()) {
					return used;
				}
			}
		}
		return InteractionResult.PASS;
	}

	static InteractionResult press(GameTestHelper helper, ServerPlayer player, BlockPos rel) {
		return press(helper, player, rel, Direction.UP);
	}

	/** A right-click on the air: the main hand's item, then the off hand's. */
	static InteractionResult pressAir(GameTestHelper helper, ServerPlayer player) {
		for (InteractionHand hand : InteractionHand.values()) {
			ItemStack stack = player.getItemInHand(hand);
			if (!stack.isEmpty() && player.gameMode.useItem(player, helper.getLevel(), stack, hand).consumesAction()) {
				return InteractionResult.SUCCESS;
			}
		}
		return InteractionResult.PASS;
	}

	/**
	 * A right-click on {@code target} with the main hand, as the client sends it: the click at a spot on them (where the
	 * use-entity callbacks run); if nothing answered (no line shown, no screen opened), the plain click too.
	 */
	static void pressOn(ServerPlayer player, net.minecraft.world.entity.Entity target, List<Component> seen) {
		player.moveTo(target.getX() + 1.5, target.getY(), target.getZ(), 90f, 0f);
		int before = seen.size();
		player.connection.handleInteract(ServerboundInteractPacket.createInteractionPacket(target, player.isShiftKeyDown(),
			InteractionHand.MAIN_HAND, new Vec3(0, target.getBbHeight() / 2, 0)));
		if (seen.size() == before && player.containerMenu == player.inventoryMenu) {
			player.connection.handleInteract(ServerboundInteractPacket.createInteractionPacket(target, player.isShiftKeyDown(), InteractionHand.MAIN_HAND));
		}
	}

	static String key(Component c) {
		return QaStageThreeGameTests.key(c);
	}

	static boolean saw(List<Component> seen, String key) {
		return seen.stream().anyMatch(c -> key.equals(key(c)));
	}

	static Component find(List<Component> seen, String key) {
		return seen.stream().filter(c -> key.equals(key(c))).findFirst().orElse(null);
	}

	static List<String> keys(List<Component> seen) {
		return seen.stream().map(QaStageFourGameTests::key).toList();
	}

	/** {@code c} rendered, its arguments in place: no raw key, no {@code %s} left over. */
	static void assertRendered(GameTestHelper helper, Component c, String... parts) {
		String text = c.getString();
		helper.assertFalse(text.startsWith("message.") || text.contains("%s") || text.contains("%1$s"), "not rendered: " + text);
		for (String part : parts) {
			helper.assertTrue(text.contains(part), "'" + text + "' doesn't say " + part);
		}
	}

	static void fill(ServerPlayer player) {
		for (int i = 0; i < player.getInventory().items.size(); i++) {
			if (player.getInventory().items.get(i).isEmpty()) {
				player.getInventory().items.set(i, new ItemStack(Items.DIRT, 64));
			}
		}
	}

	static int count(ServerPlayer player, net.minecraft.world.item.Item item) {
		return player.getInventory().countItem(item);
	}

	static List<ItemEntity> dropped(GameTestHelper helper, ServerPlayer player, net.minecraft.world.item.Item item) {
		return helper.getLevel().getEntitiesOfClass(ItemEntity.class, new AABB(player.blockPosition()).inflate(4), e -> e.getItem().is(item));
	}

	// --- Markers on blocks -----------------------------------------------------------------------------------------

	/**
	 * Every marker clicked on the ground from the OFF hand, the main hand empty: the client tries the main hand first
	 * (nothing there), then the off hand, so the first corner is set and said, in survival and in creative. A
	 * sneak-click with the marker in the off hand clears it (README: "sneak-right-click the ground to clear it").
	 */
	//$ gametest_batch AREA '"qa4OffHand"'
	@GameTest(template = AREA, batch = "qa4OffHand")
	public void markersWorkFromTheOffHand(GameTestHelper helper) {
		Leftovers.clear(helper);
		helper.setBlock(new BlockPos(2, 1, 2), Blocks.STONE);
		helper.setBlock(new BlockPos(5, 1, 5), Blocks.STONE);
		for (GameType mode : List.of(GameType.SURVIVAL, GameType.CREATIVE)) {
			record Marker(net.minecraft.world.item.Item item, String first, String reset) {
			}
			for (Marker m : List.of(new Marker(ModItems.FIELD_MARKER, "message.aliveworkplace.field.first", "message.aliveworkplace.field.reset"),
				new Marker(ModItems.QUARRY_MARKER, "message.aliveworkplace.quarry.first", "message.aliveworkplace.quarry.reset"),
				new Marker(ModItems.SCAN_TOOL, "message.aliveworkplace.scan.first", "message.aliveworkplace.scan.reset"))) {
				List<Component> seen = new ArrayList<>();
				ServerPlayer player = player(helper, seen, mode);
				ItemStack marker = new ItemStack(m.item());
				player.setItemInHand(InteractionHand.OFF_HAND, marker);
				press(helper, player, new BlockPos(2, 1, 2));
				BlockPos at = helper.absolutePos(new BlockPos(2, 1, 2));
				Component first = find(seen, m.first());
				helper.assertTrue(first != null, mode + " " + m.item() + " in the off hand: no first corner, saw " + keys(seen));
				assertRendered(helper, first, String.valueOf(at.getX()), String.valueOf(at.getZ()));
				ItemStack held = player.getItemInHand(InteractionHand.OFF_HAND);
				Optional<BlockPos> stored = held.is(ModItems.FIELD_MARKER) ? FieldMarkerItem.data(held).first()
					: held.is(ModItems.SCAN_TOOL) ? ScanToolItem.data(held).first() : QuarryMarkerItem.data(held).first();
				helper.assertTrue(stored.equals(Optional.of(at)), mode + " " + m.item() + ": the marker holds " + stored);
				player.setShiftKeyDown(true);
				press(helper, player, new BlockPos(5, 1, 5));
				player.setShiftKeyDown(false);
				helper.assertTrue(saw(seen, m.reset()), mode + " " + m.item() + ": sneak-click didn't clear it, saw " + keys(seen));
				held = player.getItemInHand(InteractionHand.OFF_HAND);
				stored = held.is(ModItems.FIELD_MARKER) ? FieldMarkerItem.data(held).first()
					: held.is(ModItems.SCAN_TOOL) ? ScanToolItem.data(held).first() : QuarryMarkerItem.data(held).first();
				helper.assertTrue(stored.isEmpty(), mode + " " + m.item() + ": still marked after the sneak-click: " + stored);
			}
		}
		helper.succeed();
	}

	/**
	 * A field's corner is often its composter (the farmer's own job block, at the edge of the field). A Field Marker
	 * clicked on an empty composter marks that corner and leaves the composter as it was; the second corner on farmland
	 * finishes the field ("Field marked: 3 × 3").
	 */
	//$ gametest_batch AREA '"qa4FieldComposter"'
	@GameTest(template = AREA, batch = "qa4FieldComposter")
	public void fieldMarkerCornerOnTheComposter(GameTestHelper helper) {
		Leftovers.clear(helper);
		helper.setBlock(new BlockPos(2, 1, 2), Blocks.COMPOSTER);
		helper.setBlock(new BlockPos(4, 1, 4), Blocks.FARMLAND);
		List<Component> seen = new ArrayList<>();
		ServerPlayer player = player(helper, seen, GameType.SURVIVAL);
		player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(ModItems.FIELD_MARKER));
		press(helper, player, new BlockPos(2, 1, 2));
		helper.assertTrue(saw(seen, "message.aliveworkplace.field.first"), "the composter corner: saw " + keys(seen));
		helper.assertTrue(helper.getBlockState(new BlockPos(2, 1, 2)).getValue(BlockStateProperties.LEVEL_COMPOSTER) == 0,
			"the marker went into the composter");
		press(helper, player, new BlockPos(4, 1, 4));
		Component marked = find(seen, "message.aliveworkplace.field.marked");
		helper.assertTrue(marked != null, "the second corner: saw " + keys(seen));
		assertRendered(helper, marked, "3 × 3", "Farmer");
		helper.assertTrue(FieldMarkerItem.data(player.getMainHandItem()).isComplete(), "the marker isn't complete");
		helper.succeed();
	}

	/**
	 * Right-clicking the Builder's Bench holding something that isn't a Book and Quill (here a stick) answers the same
	 * as an empty hand: how many supply chests it has. A Travel Post right-clicked holding a sword is found the first
	 * time and known the second (README: "players who right-click it can travel here").
	 */
	//$ gametest_batch AREA '"qa4JobBlocks"'
	@GameTest(template = AREA, batch = "qa4JobBlocks")
	public void jobBlocksAnswerAClickWithSomethingInHand(GameTestHelper helper) {
		Leftovers.clear(helper);
		helper.setBlock(new BlockPos(2, 2, 2), ModBlocks.BUILDERS_BENCH);
		helper.setBlock(new BlockPos(3, 2, 2), Blocks.CHEST);
		helper.setBlock(new BlockPos(6, 2, 6), ModBlocks.TRAVEL_POST);
		List<Component> seen = new ArrayList<>();
		ServerPlayer player = player(helper, seen, GameType.SURVIVAL);
		player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.STICK));
		press(helper, player, new BlockPos(2, 2, 2), Direction.NORTH);
		Component bench = seen.stream().filter(c -> key(c).startsWith("message.aliveworkplace.bench.info")).findFirst().orElse(null);
		helper.assertTrue(bench != null, "the bench with a stick: saw " + keys(seen));
		assertRendered(helper, bench, "1 supply chest");
		player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.IRON_SWORD));
		press(helper, player, new BlockPos(6, 2, 6), Direction.NORTH);
		Component found = find(seen, "message.aliveworkplace.travel.found");
		helper.assertTrue(found != null, "the travel post: saw " + keys(seen));
		assertRendered(helper, found, "Found the Travel Post");
		press(helper, player, new BlockPos(6, 2, 6), Direction.NORTH);
		helper.assertTrue(saw(seen, "message.aliveworkplace.travel.known"), "the travel post a second time: saw " + keys(seen));
		helper.assertTrue(helper.getBlockState(new BlockPos(6, 2, 6)).is(ModBlocks.TRAVEL_POST), "the post is gone");
		helper.succeed();
	}

	/**
	 * CHANGELOG 23.4: "hold a Book and Quill in your other hand and right-click with a blueprint": a right-click on the
	 * air with the blueprint in the main hand and the book in the off hand writes the list (the "list written" line,
	 * the book no longer blank). And B279: a Book and Quill on a Builder's Bench with no build under way says so.
	 */
	//$ gametest_batch AREA '"qa4BookLists"'
	@GameTest(template = AREA, batch = "qa4BookLists")
	public void bookAndQuillListsFromTheBlueprintAndTheBench(GameTestHelper helper) {
		Leftovers.clear(helper);
		List<Component> seen = new ArrayList<>();
		ServerPlayer player = player(helper, seen, GameType.SURVIVAL);
		player.setItemInHand(InteractionHand.MAIN_HAND, io.github.jcondedata.aliveworkplace.blueprint.BlueprintItem.create(
			io.github.jcondedata.aliveworkplace.blueprint.StarterBlueprints.MARKET_STALL.id(), null));
		player.setItemInHand(InteractionHand.OFF_HAND, new ItemStack(Items.WRITABLE_BOOK));
		pressAir(helper, player);
		Component written = find(seen, "message.aliveworkplace.blueprint.list_written");
		helper.assertTrue(written != null, "no list from the blueprint: saw " + keys(seen));
		assertRendered(helper, written);
		ItemStack book = player.getOffhandItem();
		helper.assertTrue(book.is(Items.WRITTEN_BOOK) || book.has(net.minecraft.core.component.DataComponents.WRITABLE_BOOK_CONTENT),
			"the book is still blank: " + book);
		helper.setBlock(new BlockPos(3, 2, 3), ModBlocks.BUILDERS_BENCH);
		seen.clear();
		player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.WRITABLE_BOOK));
		player.setItemInHand(InteractionHand.OFF_HAND, ItemStack.EMPTY);
		press(helper, player, new BlockPos(3, 2, 3), Direction.NORTH);
		helper.assertTrue(saw(seen, "message.aliveworkplace.site_list.no_build_at_bench"), "the bench with a book: saw " + keys(seen));
		helper.succeed();
	}

	/**
	 * README: "Craft a Settler's Wagon ... and right-click open ground: two settlers make camp there". A survival
	 * player's right-click on the ground: the camp line naming the two, two settlers, the wagon used up. A second wagon
	 * clicked on the same spot: no room, and that wagon is kept.
	 */
	//$ gametest_batch AREA '"qa4Wagon"'
	@GameTest(template = AREA, batch = "qa4Wagon")
	public void settlersWagonRightClickedOnTheGround(GameTestHelper helper) {
		Leftovers.clear(helper);
		// an owned hall an earlier batch left near this spot protects the ground: "protection.denied", no camp (full build, 2026-10-24)
		Leftovers.halls(helper);
		List<Component> seen = new ArrayList<>();
		ServerPlayer player = player(helper, seen, GameType.SURVIVAL);
		player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(ModItems.SETTLERS_WAGON, 2));
		player.setYRot(0f);
		press(helper, player, new BlockPos(11, 1, 6));
		Component made = find(seen, "message.aliveworkplace.camp.made");
		helper.assertTrue(made != null, "no camp: saw " + keys(seen));
		assertRendered(helper, made);
		List<Villager> settlers = helper.getLevel().getEntitiesOfClass(Villager.class, new AABB(helper.absolutePos(BlockPos.ZERO)).inflate(24));
		helper.assertTrue(settlers.size() == 2, "settlers: " + settlers.size());
		helper.assertTrue(player.getMainHandItem().getCount() == 1, "wagons left: " + player.getMainHandItem());
		press(helper, player, new BlockPos(11, 1, 6));
		helper.assertTrue(saw(seen, "message.aliveworkplace.camp.no_room"), "a second camp on the first: saw " + keys(seen));
		helper.assertTrue(player.getMainHandItem().getCount() == 1, "the refused wagon went: " + player.getMainHandItem());
		helper.succeed();
	}

	// --- Markers handed to villagers -------------------------------------------------------------------------------

	/**
	 * The whole Field Marker job the way a player does it: two corners right-clicked on farmland, then the marker
	 * right-clicked on a Farmer with a composter. He takes the field (the start line, with its size), the marker is
	 * used up in survival, and no trade screen opens.
	 */
	//$ gametest_batch AREA '"qa4FieldGive"'
	@GameTest(template = AREA, batch = "qa4FieldGive")
	public void fieldMarkerClickedOnAFarmer(GameTestHelper helper) {
		Leftovers.clear(helper);
		ServerLevel level = helper.getLevel();
		for (int x = 2; x <= 4; x++) {
			for (int z = 4; z <= 6; z++) {
				helper.setBlock(new BlockPos(x, 1, z), Blocks.FARMLAND);
			}
		}
		helper.setBlock(new BlockPos(6, 2, 2), Blocks.COMPOSTER);
		Villager farmer = helper.spawn(EntityType.VILLAGER, new BlockPos(6, 2, 4));
		Jobs.employ(level, farmer, helper.absolutePos(new BlockPos(6, 2, 2)), PoiTypes.FARMER, VillagerProfession.FARMER);
		List<Component> seen = new ArrayList<>();
		ServerPlayer player = player(helper, seen, GameType.SURVIVAL);
		player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(ModItems.FIELD_MARKER));
		press(helper, player, new BlockPos(2, 1, 4));
		press(helper, player, new BlockPos(4, 1, 6));
		helper.assertTrue(saw(seen, "message.aliveworkplace.field.marked"), "marking: saw " + keys(seen));
		pressOn(player, farmer, seen);
		Component started = find(seen, "message.aliveworkplace.field.started");
		helper.assertTrue(started != null, "the farmer didn't take the field: saw " + keys(seen));
		assertRendered(helper, started, "3 × 3");
		helper.assertTrue(ModAttachments.FARM_FIELD.has(farmer), "no field on the farmer");
		helper.assertTrue(player.getMainHandItem().isEmpty(), "the marker wasn't used up: " + player.getMainHandItem());
		helper.assertTrue(!(player.containerMenu instanceof net.minecraft.world.inventory.MerchantMenu), "a trade screen opened");
		helper.succeed();
	}

	/**
	 * A marked Quarry Marker (corners right-clicked) given by a right-click to a Miner who has no blast furnace: he
	 * answers that he has none, and the marker stays in hand. A blank one: "Mark the quarry first". Sneaking changes
	 * neither. No trade screen opens.
	 */
	//$ gametest_batch AREA '"qa4QuarryGive"'
	@GameTest(template = AREA, batch = "qa4QuarryGive")
	public void quarryMarkerClickedOnAMiner(GameTestHelper helper) {
		Leftovers.clear(helper);
		helper.setBlock(new BlockPos(2, 1, 2), Blocks.STONE);
		helper.setBlock(new BlockPos(5, 1, 5), Blocks.STONE);
		Villager miner = helper.spawn(EntityType.VILLAGER, new BlockPos(6, 2, 2));
		miner.setVillagerData(miner.getVillagerData().setProfession(ModVillagers.MINER).setLevel(2));
		for (boolean sneak : new boolean[] {false, true}) {
			List<Component> seen = new ArrayList<>();
			ServerPlayer player = player(helper, seen, GameType.SURVIVAL);
			player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(ModItems.QUARRY_MARKER));
			player.setShiftKeyDown(sneak);
			pressOn(player, miner, seen);
			Component blank = find(seen, "message.aliveworkplace.quarry.not_marked");
			helper.assertTrue(blank != null, "sneak " + sneak + ", a blank marker: saw " + keys(seen));
			assertRendered(helper, blank, "Mark the quarry first");
			player.setShiftKeyDown(false);
			press(helper, player, new BlockPos(2, 1, 2));
			press(helper, player, new BlockPos(5, 1, 5));
			helper.assertTrue(QuarryMarkerItem.data(player.getMainHandItem()).second().isPresent(), "marking: saw " + keys(seen));
			player.setShiftKeyDown(sneak);
			pressOn(player, miner, seen);
			helper.assertTrue(saw(seen, "message.aliveworkplace.quarry.no_bench"), "sneak " + sneak + ", marked: saw " + keys(seen));
			helper.assertTrue(player.getMainHandItem().is(ModItems.QUARRY_MARKER), "the marker went: " + player.getMainHandItem());
			helper.assertTrue(!(player.containerMenu instanceof net.minecraft.world.inventory.MerchantMenu), "a trade screen opened");
		}
		helper.succeed();
	}

	/**
	 * A blank Delivery Note right-clicked on a Postman with no routes: "Mark both containers on the Delivery Note
	 * first", the note kept and no trade screen.
	 */
	//$ gametest_batch AREA '"qa4NoteGive"'
	@GameTest(template = AREA, batch = "qa4NoteGive")
	public void blankDeliveryNoteClickedOnAPostman(GameTestHelper helper) {
		Leftovers.clear(helper);
		Villager postman = helper.spawn(EntityType.VILLAGER, new BlockPos(4, 2, 4));
		postman.setVillagerData(postman.getVillagerData().setProfession(ModVillagers.POSTMAN).setLevel(2));
		List<Component> seen = new ArrayList<>();
		ServerPlayer player = player(helper, seen, GameType.SURVIVAL);
		player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(ModItems.DELIVERY_NOTE));
		pressOn(player, postman, seen);
		Component line = find(seen, "message.aliveworkplace.route.incomplete");
		helper.assertTrue(line != null, "a blank note: saw " + keys(seen));
		assertRendered(helper, line, "Mark both containers");
		helper.assertTrue(player.getMainHandItem().is(ModItems.DELIVERY_NOTE), "the note went");
		helper.assertTrue(!(player.containerMenu instanceof net.minecraft.world.inventory.MerchantMenu), "a trade screen opened");
		helper.succeed();
	}

	/**
	 * README, Patrol routes: two points right-clicked on the ground, then a sneak-right-click on a guard hands him the
	 * route. The map keeps the route for the next guard.
	 */
	//$ gametest_batch AREA '"qa4PatrolGive"'
	@GameTest(template = AREA, batch = "qa4PatrolGive")
	public void patrolMapSneakClickedOnAGuard(GameTestHelper helper) {
		Leftovers.clear(helper);
		helper.setDayTime(2000);
		helper.setBlock(new BlockPos(1, 2, 1), ModBlocks.GUARD_POST);
		helper.setBlock(new BlockPos(2, 1, 6), Blocks.GRASS_BLOCK);
		helper.setBlock(new BlockPos(6, 1, 6), Blocks.GRASS_BLOCK);
		Villager guard = helper.spawn(EntityType.VILLAGER, new BlockPos(3, 2, 3));
		Jobs.employ(helper.getLevel(), guard, helper.absolutePos(new BlockPos(1, 2, 1)), ModVillagers.GUARD_POST_POI, ModVillagers.GUARD);
		List<Component> seen = new ArrayList<>();
		ServerPlayer player = player(helper, seen, GameType.SURVIVAL);
		player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(ModItems.PATROL_MAP));
		press(helper, player, new BlockPos(2, 1, 6));
		press(helper, player, new BlockPos(6, 1, 6));
		PatrolMapItem.Route route = player.getMainHandItem().getOrDefault(ModComponents.PATROL, PatrolMapItem.Route.EMPTY);
		helper.assertTrue(route.points().size() == 2, "two clicks on the ground made " + route.points() + ", saw " + keys(seen));
		player.setShiftKeyDown(true);
		pressOn(player, guard, seen);
		player.setShiftKeyDown(false);
		helper.assertTrue(ModAttachments.PATROL_ROUTE.getOrElse(guard, List.of()).size() == 2,
			"the guard has no route after the sneak-click: saw " + keys(seen));
		helper.assertTrue(player.getMainHandItem().is(ModItems.PATROL_MAP)
			&& player.getMainHandItem().getOrDefault(ModComponents.PATROL, PatrolMapItem.Route.EMPTY).points().size() == 2,
			"the map didn't keep the route: " + player.getMainHandItem());
		helper.succeed();
	}

	// --- Hiring with a sneak-right-click ---------------------------------------------------------------------------

	/**
	 * README's job table: "sneak-right-click with coal / an iron ingot / flint / shears / a lead / a glass bottle / lapis
	 * / a compass to hire them", each a vanilla villager at its own block. Each is clicked through the real packet with
	 * a rival block close by that the same item would pick another job at (README: "Where one item fits two blocks ...
	 * the block they work at ... decides"): a beehive by the Cleric, a smithing table by the Weaponsmith, a grindstone
	 * by the Toolsmith. Each is hired (a "hired" line naming them, the player their employer) and keeps their job.
	 */
	//$ gametest_batch AREA '"qa4Hire"'
	@GameTest(template = AREA, batch = "qa4Hire")
	public void sneakClickHiresEachVanillaWorker(GameTestHelper helper) {
		Leftovers.clear(helper);
		ServerLevel level = helper.getLevel();
		record Hire(VillagerProfession job, net.minecraft.world.level.block.Block block,
					net.minecraft.resources.ResourceKey<net.minecraft.world.entity.ai.village.poi.PoiType> poi,
					net.minecraft.world.item.Item item, String key, net.minecraft.world.level.block.Block rival) {
		}
		List<Hire> hires = List.of(
			new Hire(VillagerProfession.ARMORER, Blocks.BLAST_FURNACE, PoiTypes.ARMORER, Items.COAL, "message.aliveworkplace.smelter.hired", Blocks.AIR),
			new Hire(VillagerProfession.TOOLSMITH, Blocks.SMITHING_TABLE, PoiTypes.TOOLSMITH, Items.IRON_INGOT, "message.aliveworkplace.toolsmith.hired", Blocks.GRINDSTONE),
			new Hire(VillagerProfession.WEAPONSMITH, Blocks.GRINDSTONE, PoiTypes.WEAPONSMITH, Items.IRON_INGOT, "message.aliveworkplace.weaponsmith.hired", Blocks.SMITHING_TABLE),
			new Hire(VillagerProfession.FLETCHER, Blocks.FLETCHING_TABLE, PoiTypes.FLETCHER, Items.FLINT, "message.aliveworkplace.fletcher.hired", Blocks.AIR),
			new Hire(VillagerProfession.SHEPHERD, Blocks.LOOM, PoiTypes.SHEPHERD, Items.SHEARS, "message.aliveworkplace.shepherd.hired", Blocks.BEEHIVE),
			new Hire(VillagerProfession.BUTCHER, Blocks.SMOKER, PoiTypes.BUTCHER, Items.LEAD, "message.aliveworkplace.herder.hired", Blocks.AIR),
			new Hire(VillagerProfession.CLERIC, Blocks.BREWING_STAND, PoiTypes.CLERIC, Items.GLASS_BOTTLE, "message.aliveworkplace.alchemist.hired", Blocks.BEEHIVE),
			new Hire(VillagerProfession.LIBRARIAN, Blocks.LECTERN, PoiTypes.LIBRARIAN, Items.LAPIS_LAZULI, "message.aliveworkplace.scribe.hired", Blocks.AIR),
			new Hire(VillagerProfession.CARTOGRAPHER, Blocks.CARTOGRAPHY_TABLE, PoiTypes.CARTOGRAPHER, Items.COMPASS, "message.aliveworkplace.explorer.hired", Blocks.AIR));
		List<String> wrong = new ArrayList<>();
		for (int i = 0; i < hires.size(); i++) {
			Hire h = hires.get(i);
			int x = 1 + (i % 3) * 2;
			int z = 1 + (i / 3) * 2;
			BlockPos block = new BlockPos(x, 2, z);
			helper.setBlock(block, h.block());
			if (h.rival() != Blocks.AIR) {
				helper.setBlock(block.above(), h.rival());
			}
			Villager v = helper.spawn(EntityType.VILLAGER, new BlockPos(x + 1, 2, z));
			v.setNoAi(true);
			Jobs.employ(level, v, helper.absolutePos(block), h.poi(), h.job());
			List<Component> seen = new ArrayList<>();
			ServerPlayer player = player(helper, seen, GameType.SURVIVAL);
			player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(h.item(), 2));
			player.setShiftKeyDown(true);
			pressOn(player, v, seen);
			Component hired = find(seen, h.key());
			var employer = ModAttachments.BUILDER_EMPLOYER.get(v);
			if (hired == null || employer == null || !employer.id().equals(player.getUUID()) || v.getVillagerData().getProfession() != h.job()) {
				wrong.add(h.job() + " with " + h.item() + ": saw " + keys(seen) + ", employer " + employer + ", now " + v.getVillagerData().getProfession());
			} else {
				assertRendered(helper, hired, v.getDisplayName().getString());
			}
		}
		helper.assertTrue(wrong.isEmpty(), "not hired by a sneak-right-click: " + wrong);
		helper.succeed();
	}

	/**
	 * README: a hired worker "takes orders from them and the friends they add". A Fletcher hired by one player
	 * (sneak-right-click with flint) is sneak-clicked with flint by another: refused, the line naming his employer, who
	 * stays his employer.
	 */
	//$ gametest_batch AREA '"qa4HireStranger"'
	@GameTest(template = AREA, batch = "qa4HireStranger")
	public void aStrangerCannotHireAnothersWorker(GameTestHelper helper) {
		Leftovers.clear(helper);
		helper.setBlock(new BlockPos(2, 2, 2), Blocks.FLETCHING_TABLE);
		Villager fletcher = helper.spawn(EntityType.VILLAGER, new BlockPos(3, 2, 2));
		fletcher.setNoAi(true);
		Jobs.employ(helper.getLevel(), fletcher, helper.absolutePos(new BlockPos(2, 2, 2)), PoiTypes.FLETCHER, VillagerProfession.FLETCHER);
		List<Component> ownerSeen = new ArrayList<>();
		ServerPlayer owner = player(helper, ownerSeen, GameType.SURVIVAL);
		owner.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.FLINT));
		owner.setShiftKeyDown(true);
		pressOn(owner, fletcher, ownerSeen);
		helper.assertTrue(saw(ownerSeen, "message.aliveworkplace.fletcher.hired"), "the first player didn't hire him: " + keys(ownerSeen));
		helper.runAfterDelay(3, () -> { // a later tick: the stranger's name (from the game time) differs from the owner's
			List<Component> seen = new ArrayList<>();
			ServerPlayer stranger = player(helper, seen, GameType.SURVIVAL);
			helper.assertFalse(stranger.getGameProfile().getName().equals(owner.getGameProfile().getName()), "the two players share a name");
			stranger.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.FLINT));
			stranger.setShiftKeyDown(true);
			pressOn(stranger, fletcher, seen);
			Component refused = find(seen, "message.aliveworkplace.not_your_builder");
			helper.assertTrue(refused != null, "the stranger wasn't refused: " + keys(seen));
			assertRendered(helper, refused, owner.getGameProfile().getName(), stranger.getGameProfile().getName());
			helper.assertTrue(ModAttachments.BUILDER_EMPLOYER.get(fletcher).id().equals(owner.getUUID()), "the stranger took him over");
			helper.succeed();
		});
	}

	/** README: a Nurse, "right-click them to be healed" (an empty hand, not sneaking): the hurt player is healed whole. */
	//$ gametest_batch AREA '"qa4Nurse"'
	@GameTest(template = AREA, batch = "qa4Nurse")
	public void nurseHealsOnARightClick(GameTestHelper helper) {
		Leftovers.clear(helper);
		helper.setBlock(new BlockPos(2, 2, 2), Blocks.BREWING_STAND);
		Villager nurse = helper.spawn(EntityType.VILLAGER, new BlockPos(4, 2, 4));
		nurse.setNoAi(true);
		Jobs.employ(helper.getLevel(), nurse, helper.absolutePos(new BlockPos(2, 2, 2)), PoiTypes.CLERIC, ModVillagers.NURSE);
		helper.setDayTime(6000);
		List<Component> seen = new ArrayList<>();
		ServerPlayer player = player(helper, seen, GameType.SURVIVAL);
		player.setHealth(6f);
		pressOn(player, nurse, seen);
		helper.assertTrue(player.getHealth() == player.getMaxHealth(), "not healed: " + player.getHealth() + ", saw " + keys(seen));
		helper.assertTrue(!(player.containerMenu instanceof net.minecraft.world.inventory.MerchantMenu), "a trade screen opened");
		helper.succeed();
	}

	/**
	 * A builder right-clicked with a blueprint that was never placed, and sneak-right-clicked with it (the sneak goes to
	 * the builder, not to the job picker, since he's a builder already): "place it first", both times; the blueprint
	 * stays in hand.
	 */
	//$ gametest_batch AREA '"qa4BuilderGive"'
	@GameTest(template = AREA, batch = "qa4BuilderGive")
	public void unplacedBlueprintClickedOnABuilder(GameTestHelper helper) {
		Leftovers.clear(helper);
		helper.setBlock(new BlockPos(2, 2, 2), ModBlocks.BLUEPRINT_TABLE);
		Villager builder = helper.spawn(EntityType.VILLAGER, new BlockPos(4, 2, 4));
		builder.setNoAi(true);
		io.github.jcondedata.aliveworkplace.build.Builders.employ(helper.getLevel(), builder, helper.absolutePos(new BlockPos(2, 2, 2)));
		for (boolean sneak : new boolean[] {false, true}) {
			List<Component> seen = new ArrayList<>();
			ServerPlayer player = player(helper, seen, GameType.SURVIVAL);
			player.setItemInHand(InteractionHand.MAIN_HAND, io.github.jcondedata.aliveworkplace.blueprint.BlueprintItem.create(
				io.github.jcondedata.aliveworkplace.blueprint.StarterBlueprints.MARKET_STALL.id(), null));
			player.setShiftKeyDown(sneak);
			pressOn(player, builder, seen);
			Component line = find(seen, "message.aliveworkplace.assign.not_placed");
			helper.assertTrue(line != null, "sneak " + sneak + ": saw " + keys(seen));
			assertRendered(helper, line);
			helper.assertTrue(player.getMainHandItem().is(ModItems.BLUEPRINT), "sneak " + sneak + ": the blueprint went");
		}
		helper.succeed();
	}

	// --- Limits from the README, reached by clicking ---------------------------------------------------------------

	/**
	 * README, Quarry Marker: "Sneak-right-click the air to choose the depth (4, 8, 16, 32 or 64) or a strip mine", and
	 * since 36.22 the deeper 96, 128 and down to bedrock. Thirteen sneak-clicks on the air (the marker in the off hand,
	 * the main hand empty) go through every depth and the strip mine, and never to a depth outside the eight.
	 */
	//$ gametest_batch AREA '"qa4QuarryDepth"'
	@GameTest(template = AREA, batch = "qa4QuarryDepth")
	public void quarryDepthChosenBySneakClicksOnTheAir(GameTestHelper helper) {
		Leftovers.clear(helper);
		List<Component> seen = new ArrayList<>();
		ServerPlayer player = player(helper, seen, GameType.SURVIVAL);
		player.setItemInHand(InteractionHand.OFF_HAND, new ItemStack(ModItems.QUARRY_MARKER));
		player.setShiftKeyDown(true);
		java.util.Set<Integer> depths = new java.util.TreeSet<>();
		boolean strip = false;
		for (int i = 0; i < 13; i++) { // 36.22: 96, 128 and down to bedrock (-1) joined the choices
			pressAir(helper, player);
			QuarryData data = QuarryMarkerItem.data(player.getOffhandItem());
			if (data.isStripMine()) {
				strip = true;
			} else {
				depths.add(data.depth());
			}
		}
		helper.assertTrue(depths.equals(new java.util.TreeSet<>(List.of(QuarryData.BEDROCK, 4, 8, 16, 32, 64, 96, 128))), "depths chosen: " + depths + ", saw " + keys(seen));
		helper.assertTrue(strip, "never a strip mine in thirteen clicks: " + keys(seen));
		helper.assertTrue(saw(seen, "message.aliveworkplace.quarry.depth") && saw(seen, "message.aliveworkplace.quarry.strip_mine")
			&& saw(seen, "message.aliveworkplace.quarry.depth_level") && saw(seen, "message.aliveworkplace.quarry.depth_bedrock"),
			"the choices weren't said: " + keys(seen));
		helper.succeed();
	}

	/**
	 * README, Patrol Map: "right-click the ground at up to eight places"; "a blank map (sneak-right-click the ground to
	 * clear it)". Nine clicks on the ground keep at most eight points; a sneak-click on the ground clears them.
	 */
	//$ gametest_batch AREA '"qa4PatrolEight"'
	@GameTest(template = AREA, batch = "qa4PatrolEight")
	public void patrolMapKeepsAtMostEightPoints(GameTestHelper helper) {
		Leftovers.clear(helper);
		List<Component> seen = new ArrayList<>();
		ServerPlayer player = player(helper, seen, GameType.SURVIVAL);
		player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(ModItems.PATROL_MAP));
		for (int i = 0; i < 9; i++) {
			BlockPos p = new BlockPos(1 + i * 2, 1, 4);
			helper.setBlock(p, Blocks.GRASS_BLOCK);
			press(helper, player, p);
		}
		List<BlockPos> points = player.getMainHandItem().getOrDefault(ModComponents.PATROL, PatrolMapItem.Route.EMPTY).points();
		helper.assertTrue(points.size() == 8, "nine clicks left " + points.size() + " points, saw " + keys(seen));
		player.setShiftKeyDown(true);
		press(helper, player, new BlockPos(1, 1, 4));
		player.setShiftKeyDown(false);
		points = player.getMainHandItem().getOrDefault(ModComponents.PATROL, PatrolMapItem.Route.EMPTY).points();
		helper.assertTrue(points.isEmpty(), "a sneak-click on the ground left " + points);
		helper.succeed();
	}

	/**
	 * README, Rally Banner: "sneak-right-click up to twelve guards with it to enlist them (again to let one go), then
	 * right-click the air to raise it". Thirteen guards sneak-clicked: twelve enlisted, the thirteenth refused with the
	 * limit; a second sneak-click lets the first go, and then the thirteenth is taken; a right-click on the air raises it.
	 */
	//$ gametest_ticks_batch AREA '200' '"qa4Rally"'
	@GameTest(template = AREA, timeoutTicks = 200, batch = "qa4Rally")
	public void rallyBannerTakesTwelveGuards(GameTestHelper helper) {
		Leftovers.clear(helper);
		helper.setDayTime(2000);
		List<Villager> guards = new ArrayList<>();
		for (int i = 0; i < 13; i++) {
			BlockPos post = new BlockPos(1 + (i % 5) * 4, 2, 1 + (i / 5) * 4);
			helper.setBlock(post, ModBlocks.GUARD_POST);
			Villager guard = helper.spawn(EntityType.VILLAGER, post.east());
			guard.setNoAi(true);
			Jobs.employ(helper.getLevel(), guard, helper.absolutePos(post), ModVillagers.GUARD_POST_POI, ModVillagers.GUARD);
			guards.add(guard);
		}
		List<Component> seen = new ArrayList<>();
		ServerPlayer player = player(helper, seen, GameType.SURVIVAL);
		player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(ModItems.RALLY_BANNER));
		player.setShiftKeyDown(true);
		for (Villager guard : guards) {
			pressOn(player, guard, seen);
		}
		var rally = player.getMainHandItem().get(ModComponents.RALLY);
		helper.assertTrue(rally != null && rally.guards().size() == 12, "thirteen sneak-clicks enlisted " + (rally == null ? 0 : rally.guards().size())
			+ ", saw " + keys(seen));
		Component full = find(seen, "message.aliveworkplace.rally.full");
		helper.assertTrue(full != null && !rally.guards().contains(guards.get(12).getUUID()), "the thirteenth wasn't refused: " + keys(seen));
		assertRendered(helper, full, "12");
		pressOn(player, guards.get(0), seen);
		pressOn(player, guards.get(12), seen);
		rally = player.getMainHandItem().get(ModComponents.RALLY);
		helper.assertTrue(saw(seen, "message.aliveworkplace.rally.released") && rally.guards().size() == 12
			&& !rally.guards().contains(guards.get(0).getUUID()) && rally.guards().contains(guards.get(12).getUUID()),
			"letting one go and taking the thirteenth: " + rally.guards().size() + ", saw " + keys(seen));
		player.setShiftKeyDown(false);
		pressAir(helper, player);
		helper.assertTrue(player.getMainHandItem().get(ModComponents.RALLY).raised(), "a right-click on the air didn't raise it: " + keys(seen));
		pressAir(helper, player);
		helper.assertFalse(player.getMainHandItem().get(ModComponents.RALLY).raised(), "a second right-click didn't lower it");
		helper.succeed();
	}

	/**
	 * README, Shop Counter: "The owner can sneak-right-click the counter to see the latest sales." With an empty hand
	 * the sales log comes (here "no sales yet" or its header).
	 */
	//$ gametest_batch AREA '"qa4ShopLog"'
	@GameTest(template = AREA, batch = "qa4ShopLog")
	public void ownerSneakClicksTheCounterForTheSales(GameTestHelper helper) {
		Leftovers.clear(helper);
		helper.setBlock(new BlockPos(3, 2, 3), ModBlocks.SHOP_COUNTER);
		List<Component> seen = new ArrayList<>();
		ServerPlayer owner = player(helper, seen, GameType.SURVIVAL);
		((io.github.jcondedata.aliveworkplace.shop.ShopCounterBlockEntity) helper.getBlockEntity(new BlockPos(3, 2, 3)))
			.setOwner(owner.getUUID(), owner.getGameProfile().getName());
		owner.setShiftKeyDown(true);
		press(helper, owner, new BlockPos(3, 2, 3), Direction.NORTH);
		helper.assertTrue(seen.stream().anyMatch(c -> key(c).startsWith("message.aliveworkplace.shop.log")),
			"empty hand: no sales log, saw " + keys(seen));
		helper.assertTrue(owner.containerMenu == owner.inventoryMenu, "the counter opened on a sneak-click");
		helper.succeed();
	}

	// --- Commands, typed by a player who isn't an operator ---------------------------------------------------------

	private static int performed(ServerPlayer player, String command) {
		var source = player.createCommandSourceStack();
		var results = new int[] {0};
		player.server.getCommands().performPrefixedCommand(source.withCallback((ok, value) -> results[0] = ok ? Math.max(1, value) : 0), command);
		return results[0];
	}

	static boolean refused(List<Component> seen) {
		return seen.stream().anyMatch(c -> {
			String k = key(c);
			return k.startsWith("command.unknown") || k.startsWith("command.context") || k.equals("commands.help.failed");
		});
	}

	/**
	 * README's command table typed by a survival player who isn't an operator: the player commands answer (sites, mail,
	 * quests, standing, friend list, strip with a Quarry Marker in either hand), and the "(op)" ones are refused (blueprints, blueprint &lt;id&gt;, import, threat start).
	 */
	//$ gametest_batch AREA '"qa4Commands"'
	@GameTest(template = AREA, batch = "qa4Commands")
	public void commandsAsAPlayerWhoIsNotAnOperator(GameTestHelper helper) {
		Leftovers.clear(helper);
		List<Component> seen = new ArrayList<>();
		ServerPlayer player = player(helper, seen, GameType.SURVIVAL);
		helper.assertFalse(player.hasPermissions(2), "the test player is an operator; the op checks below would prove nothing");
		List<String> wrong = new ArrayList<>();
		// (friend add / remove take a player name, which the GameTest server can't look up: it has no profile cache, and
		// the command then fails with "An unexpected error". Not a mod bug; those two stay untested here.)
		for (String command : List.of("workplace sites", "workplace mail", "workplace quests", "workplace standing",
			"workplace friend list")) {
			seen.clear();
			int result = performed(player, command);
			if (refused(seen) || seen.isEmpty()) {
				wrong.add("/" + command + " -> " + result + " " + keys(seen));
			}
		}
		for (String command : List.of("workplace blueprints", "workplace blueprint aliveworkplace:market_stall", "workplace import",
			"workplace threat start bandits")) {
			seen.clear();
			int before = player.getInventory().countItem(ModItems.BLUEPRINT);
			int result = performed(player, command);
			if (result > 0 || player.getInventory().countItem(ModItems.BLUEPRINT) != before) {
				wrong.add("op-only /" + command + " ran for a non-operator: " + result + " " + keys(seen));
			}
		}
		for (InteractionHand hand : InteractionHand.values()) {
			seen.clear();
			player.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
			player.setItemInHand(InteractionHand.OFF_HAND, ItemStack.EMPTY);
			player.setItemInHand(hand, new ItemStack(ModItems.QUARRY_MARKER));
			performed(player, "workplace strip -20");
			QuarryData data = QuarryMarkerItem.data(player.getItemInHand(hand));
			Component line = find(seen, "message.aliveworkplace.quarry.strip_level");
			if (line == null || !data.isStripMine()) {
				wrong.add("/workplace strip -20 with the marker in the " + hand + ": " + keys(seen) + ", strip " + data.isStripMine());
			} else {
				assertRendered(helper, line, "-20");
			}
		}
		helper.assertTrue(wrong.isEmpty(), "commands: " + wrong);
		helper.succeed();
	}

	// --- A screen's packet ------------------------------------------------------------------------------------------

	/**
	 * The City Plan screen's edit packet, sent the way the client sends it (a custom payload to the server's
	 * connection): from a stranger to another player's hall it changes nothing and says so (CHANGELOG 27.x: only the
	 * owner, friends and operators change the plan); from the owner it switches the plan's mode.
	 */
	//$ gametest_batch AREA '"qa4PlanPacket"'
	@GameTest(template = AREA, batch = "qa4PlanPacket")
	public void cityPlanEditPacketFromAStrangerAndTheOwner(GameTestHelper helper) {
		Leftovers.clear(helper);
		Leftovers.halls(helper);
		helper.setBlock(new BlockPos(4, 2, 4), ModBlocks.VILLAGE_HALL);
		BlockPos hall = helper.absolutePos(new BlockPos(4, 2, 4));
		var entity = (io.github.jcondedata.aliveworkplace.hall.VillageHallBlockEntity) helper.getLevel().getBlockEntity(hall);
		List<Component> ownerSeen = new ArrayList<>();
		ServerPlayer owner = player(helper, ownerSeen, GameType.SURVIVAL);
		entity.setOwner(owner.getUUID(), owner.getGameProfile().getName());
		List<Component> seen = new ArrayList<>();
		ServerPlayer stranger = player(helper, seen, GameType.SURVIVAL);
		var before = entity.plan().mode();
		var other = before == io.github.jcondedata.aliveworkplace.city.CityPlan.Mode.RUN
			? io.github.jcondedata.aliveworkplace.city.CityPlan.Mode.REST : io.github.jcondedata.aliveworkplace.city.CityPlan.Mode.RUN;
		var edit = new io.github.jcondedata.aliveworkplace.city.CityPlans.Edit(hall, io.github.jcondedata.aliveworkplace.city.CityPlans.Op.MODE,
			-1, "", "", "", false, new java.util.BitSet(), other);
		stranger.connection.handleCustomPayload(new net.minecraft.network.protocol.common.ServerboundCustomPayloadPacket(edit));
		helper.assertTrue(entity.plan().mode() == before, "a stranger's packet changed the plan's mode to " + entity.plan().mode());
		Component denied = find(seen, "message.aliveworkplace.city_plan.denied");
		helper.assertTrue(denied != null, "the stranger wasn't told: " + keys(seen));
		assertRendered(helper, denied, owner.getGameProfile().getName());
		owner.connection.handleCustomPayload(new net.minecraft.network.protocol.common.ServerboundCustomPayloadPacket(edit));
		helper.assertTrue(entity.plan().mode() == other, "the owner's packet didn't change the mode: " + entity.plan().mode() + ", saw " + keys(ownerSeen));
		helper.succeed();
	}

	// --- The Remedy ------------------------------------------------------------------------------------------------

	/**
	 * A Remedy right-clicked on a well villager (32.18: "one used up, the bottle back"): "will stay well for 10 days",
	 * one Remedy fewer and a glass bottle. With a full inventory the bottle lands at the player's feet, never lost; in
	 * creative nothing is used up.
	 */
	//$ gametest_batch AREA '"qa4Remedy"'
	@GameTest(template = AREA, batch = "qa4Remedy")
	public void remedyClickedOnAVillager(GameTestHelper helper) {
		Leftovers.clear(helper);
		Villager villager = helper.spawn(EntityType.VILLAGER, new BlockPos(4, 2, 4));
		// survival, room in the bag
		List<Component> seen = new ArrayList<>();
		ServerPlayer player = player(helper, seen, GameType.SURVIVAL);
		player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(ModItems.REMEDY, 2));
		pressOn(player, villager, seen);
		Component line = find(seen, "message.aliveworkplace.remedy.kept_well");
		helper.assertTrue(line != null, "survival: saw " + keys(seen));
		assertRendered(helper, line, "10 days");
		helper.assertTrue(player.getMainHandItem().getCount() == 1, "survival: remedies left " + player.getMainHandItem());
		helper.assertTrue(count(player, Items.GLASS_BOTTLE) == 1, "survival: no bottle back");
		helper.assertTrue(!(player.containerMenu instanceof net.minecraft.world.inventory.MerchantMenu), "a trade screen opened");
		// survival, full bag
		List<Component> seen2 = new ArrayList<>();
		ServerPlayer full = player(helper, seen2, GameType.SURVIVAL);
		full.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(ModItems.REMEDY, 2));
		fill(full);
		pressOn(full, villager, seen2);
		helper.assertTrue(saw(seen2, "message.aliveworkplace.remedy.kept_well"), "full bag: saw " + keys(seen2));
		helper.assertTrue(full.getMainHandItem().getCount() == 1, "full bag: remedies left " + full.getMainHandItem());
		helper.assertTrue(count(full, Items.GLASS_BOTTLE) == 1 || !dropped(helper, full, Items.GLASS_BOTTLE).isEmpty(),
			"full bag: the bottle was lost");
		// creative
		List<Component> seen3 = new ArrayList<>();
		ServerPlayer creative = player(helper, seen3, GameType.CREATIVE);
		creative.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(ModItems.REMEDY, 2));
		pressOn(creative, villager, seen3);
		helper.assertTrue(saw(seen3, "message.aliveworkplace.remedy.kept_well"), "creative: saw " + keys(seen3));
		helper.assertTrue(creative.getMainHandItem().getCount() == 2, "creative: a remedy was used up");
		helper.succeed();
	}

	// --- Scan Tool with a full bag ---------------------------------------------------------------------------------

	/**
	 * The Scan Tool's save with every slot full (two Blank Blueprints, so taking one frees no slot): one Blank Blueprint
	 * used, the "Saved" line, and the blueprint is never lost: in the bag or at the player's feet.
	 */
	//$ gametest_batch AREA '"qa4ScanFull"'
	@GameTest(template = AREA, batch = "qa4ScanFull")
	public void scanWithAFullBagKeepsTheBlueprint(GameTestHelper helper) {
		Leftovers.clear(helper);
		helper.setBlock(new BlockPos(2, 2, 2), Blocks.OAK_PLANKS);
		helper.setBlock(new BlockPos(3, 2, 2), Blocks.OAK_PLANKS);
		List<Component> seen = new ArrayList<>();
		ServerPlayer player = player(helper, seen, GameType.SURVIVAL);
		player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(ModItems.SCAN_TOOL));
		player.getInventory().items.set(1, new ItemStack(ModItems.BLANK_BLUEPRINT, 2));
		fill(player);
		press(helper, player, new BlockPos(2, 2, 2));
		press(helper, player, new BlockPos(3, 2, 2));
		helper.assertTrue(saw(seen, "message.aliveworkplace.scan.marked"), "marking: saw " + keys(seen));
		player.setShiftKeyDown(true);
		pressAir(helper, player);
		player.setShiftKeyDown(false);
		helper.assertTrue(saw(seen, "message.aliveworkplace.scan.saved"), "saving: saw " + keys(seen));
		helper.assertTrue(count(player, ModItems.BLANK_BLUEPRINT) == 1, "blanks left: " + count(player, ModItems.BLANK_BLUEPRINT));
		helper.assertTrue(count(player, ModItems.BLUEPRINT) == 1 || !dropped(helper, player, ModItems.BLUEPRINT).isEmpty(),
			"the blueprint was lost with a full bag");
		helper.succeed();
	}
}
