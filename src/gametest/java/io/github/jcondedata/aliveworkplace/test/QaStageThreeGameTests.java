package io.github.jcondedata.aliveworkplace.test;

import io.github.jcondedata.aliveworkplace.blueprint.BlueprintItem;
import io.github.jcondedata.aliveworkplace.blueprint.SupplyReport;
import io.github.jcondedata.aliveworkplace.city.CityPlan;
import io.github.jcondedata.aliveworkplace.colony.ColonyCharterItem;
import io.github.jcondedata.aliveworkplace.craft.LuxuryRecipes;
import io.github.jcondedata.aliveworkplace.cup.StandSeat;
import io.github.jcondedata.aliveworkplace.farm.FieldData;
import io.github.jcondedata.aliveworkplace.fish.FishingBobber;
import io.github.jcondedata.aliveworkplace.furnish.FurnitureSeat;
import io.github.jcondedata.aliveworkplace.guard.PatrolMapItem;
import io.github.jcondedata.aliveworkplace.hall.CradleSeat;
import io.github.jcondedata.aliveworkplace.hall.Cradles;
import io.github.jcondedata.aliveworkplace.hall.VillageBannerItem;
import io.github.jcondedata.aliveworkplace.hall.VillageLedgerItem;
import io.github.jcondedata.aliveworkplace.mail.RouteData;
import io.github.jcondedata.aliveworkplace.registry.ModBlocks;
import io.github.jcondedata.aliveworkplace.registry.ModComponents;
import io.github.jcondedata.aliveworkplace.registry.ModEntities;
import io.github.jcondedata.aliveworkplace.registry.ModItems;
import io.netty.buffer.Unpooled;
import java.util.ArrayList;
import java.util.BitSet;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.npc.VillagerProfession;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BannerPattern;
import net.minecraft.world.level.block.entity.BannerPatternLayers;
import net.minecraft.world.level.block.entity.BannerPatterns;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

/**
 * QA stage 3 (2026-10-23): the items, data components, recipe type and entities stage 1's inventory found no test for,
 * driven through a player's own entry points (right-clicks go through {@code ServerPlayerGameMode}, crafting through
 * the recipe manager, saves through {@code ItemStack.save}/{@code parse} and the network through
 * {@code ItemStack.STREAM_CODEC}). Expectations come from CHANGELOG.md (Delivery Note: "right-click the container to
 * take from and then the one to bring to ... hold an item in your other hand and right-click the air with the note to
 * carry only that kind (up to 9 kinds)"; Scan Tool: "mark two corners ... save it as a blueprint (for a Blank
 * Blueprint) ... Name it by renaming the tool in an anvil", at most 48 on a side per its message; Village Banner: "any
 * banner and a gold ingot make a Village Banner of that design"), the en_us.json lines and the entities' own javadoc in
 * ModEntities (the bobber is never saved, seats are saved with their rider, none can be summoned).
 */
public class QaStageThreeGameTests implements FabricGameTest {
	private static final String AREA = "aliveworkplace_test:build_area";

	/** A survival player who keeps every chat and action-bar line they're shown. */
	static ServerPlayer listener(GameTestHelper helper, List<Component> seen) {
		ServerLevel level = helper.getLevel();
		var cookie = net.minecraft.server.network.CommonListenerCookie.createInitial(
			new com.mojang.authlib.GameProfile(UUID.randomUUID(), "qa3-" + Long.toHexString(level.getGameTime() % 0xfffff)), false);
		ServerPlayer player = new ServerPlayer(level.getServer(), level, cookie.gameProfile(), cookie.clientInformation()) {
			@Override
			public void displayClientMessage(Component message, boolean overlay) {
				seen.add(message);
				super.displayClientMessage(message, overlay);
			}

			@Override
			public void sendSystemMessage(Component message, boolean overlay) {
				seen.add(message);
				super.sendSystemMessage(message, overlay);
			}
		};
		net.minecraft.network.Connection connection = new net.minecraft.network.Connection(net.minecraft.network.protocol.PacketFlow.SERVERBOUND);
		new io.netty.channel.embedded.EmbeddedChannel(connection);
		level.getServer().getPlayerList().placeNewPlayer(connection, player, cookie);
		player.setGameMode(GameType.SURVIVAL);
		player.moveTo(Vec3.atCenterOf(helper.absolutePos(new BlockPos(1, 2, 1))));
		Leftovers.after(helper, () -> level.getServer().getPlayerList().remove(player));
		return player;
	}

	static String key(Component c) {
		if (c.getContents() instanceof TranslatableContents t) {
			return t.getKey();
		}
		return c.getSiblings().isEmpty() ? c.getString() : key(c.getSiblings().get(0));
	}

	static String last(List<Component> seen) {
		return seen.isEmpty() ? "(nothing)" : key(seen.get(seen.size() - 1));
	}

	/** A right-click on the top of {@code rel} with what's in the main hand, the way the server handles one. */
	static net.minecraft.world.InteractionResult click(GameTestHelper helper, ServerPlayer player, BlockPos rel) {
		BlockPos abs = helper.absolutePos(rel);
		BlockHitResult hit = new BlockHitResult(Vec3.atCenterOf(abs).add(0, 0.5, 0), Direction.UP, abs, false);
		return player.gameMode.useItemOn(player, helper.getLevel(), player.getMainHandItem(), InteractionHand.MAIN_HAND, hit);
	}

	/** A right-click on the air with what's in the main hand. */
	static net.minecraft.world.InteractionResult clickAir(GameTestHelper helper, ServerPlayer player) {
		return player.gameMode.useItem(player, helper.getLevel(), player.getMainHandItem(), InteractionHand.MAIN_HAND);
	}

	static <T> T saveAndLoad(GameTestHelper helper, ItemStack stack, DataComponentType<T> type) {
		var registries = helper.getLevel().registryAccess();
		Tag saved = stack.save(registries);
		ItemStack back = ItemStack.parseOptional(registries, (CompoundTag) saved);
		return back.get(type);
	}

	static ItemStack overTheNetwork(GameTestHelper helper, ItemStack stack) {
		RegistryFriendlyByteBuf buf = new RegistryFriendlyByteBuf(Unpooled.buffer(), helper.getLevel().registryAccess());
		ItemStack.STREAM_CODEC.encode(buf, stack);
		ItemStack back = ItemStack.STREAM_CODEC.decode(buf);
		helper.assertTrue(buf.readableBytes() == 0, stack.getItem() + ": " + buf.readableBytes() + " bytes left unread after decoding");
		return back;
	}

	// ---- Delivery Note --------------------------------------------------------------------------------------------

	/**
	 * A marked note (as a player leaves it after marking two containers) refuses a block that isn't a container and
	 * stays as it was; a sneak-right-click, even on a chest, clears the two marks but keeps the kinds, without opening
	 * the chest; the route survives a save and the trip to a client.
	 */
	//$ gametest AREA
	@GameTest(template = AREA, batch = "qa3DeliveryNote")
	public void deliveryNoteRefusesANonContainerAndASneakClickClearsIt(GameTestHelper helper) {
		Leftovers.clear(helper);
		List<Component> seen = new ArrayList<>();
		ServerPlayer player = listener(helper, seen);
		BlockPos chest = new BlockPos(3, 2, 3);
		helper.setBlock(chest, Blocks.CHEST);
		helper.setBlock(new BlockPos(4, 2, 5), Blocks.STONE);
		RouteData marked = new RouteData(Optional.of(helper.absolutePos(chest)), Optional.of(helper.absolutePos(new BlockPos(5, 2, 3))),
			List.of(Items.WHEAT, Items.CARROT));
		ItemStack note = new ItemStack(ModItems.DELIVERY_NOTE);
		note.set(ModComponents.ROUTE, marked);
		player.setItemInHand(InteractionHand.MAIN_HAND, note);

		RouteData reloaded = saveAndLoad(helper, player.getMainHandItem(), ModComponents.ROUTE);
		helper.assertTrue(marked.equals(reloaded), "the route changed over a save: " + marked + " -> " + reloaded);
		RouteData sent = overTheNetwork(helper, player.getMainHandItem()).get(ModComponents.ROUTE);
		helper.assertTrue(marked.equals(sent), "the route changed over the network: " + sent);

		var result = click(helper, player, new BlockPos(4, 2, 5));
		RouteData after = player.getMainHandItem().getOrDefault(ModComponents.ROUTE, RouteData.EMPTY);
		helper.assertTrue(after.equals(marked), "a stone block changed the note: " + after);
		helper.assertTrue(!result.consumesAction(), "a stone block was accepted: " + result);
		helper.assertTrue(last(seen).equals("message.aliveworkplace.route.not_container"), "stone not refused: " + last(seen));

		player.setShiftKeyDown(true);
		click(helper, player, chest);
		player.setShiftKeyDown(false);
		after = player.getMainHandItem().getOrDefault(ModComponents.ROUTE, RouteData.EMPTY);
		helper.assertTrue(after.from().isEmpty() && after.to().isEmpty(), "sneak-right-click should clear the marks, got " + after);
		helper.assertTrue(after.filter().equals(marked.filter()), "clearing the marks lost the kinds: " + after.filter());
		helper.assertTrue(last(seen).equals("message.aliveworkplace.route.reset"), "no 'cleared' message: " + last(seen));
		helper.assertTrue(player.containerMenu == player.inventoryMenu, "a sneak-right-click opened the chest");
		helper.succeed();
	}

	/**
	 * Holding an item in the other hand and right-clicking the air adds that kind, again removes it, up to 9 kinds; an
	 * empty other hand changes nothing; the kinds survive a save.
	 */
	//$ gametest AREA
	@GameTest(template = AREA, batch = "qa3DeliveryFilter")
	public void deliveryNoteCarriesOnlyUpToNineKindsPickedInTheOtherHand(GameTestHelper helper) {
		Leftovers.clear(helper);
		List<Component> seen = new ArrayList<>();
		ServerPlayer player = listener(helper, seen);
		player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(ModItems.DELIVERY_NOTE));
		clickAir(helper, player);
		helper.assertTrue(player.getMainHandItem().getOrDefault(ModComponents.ROUTE, RouteData.EMPTY).filter().isEmpty(),
			"right-clicking the air with an empty other hand changed the kinds");
		Item[] kinds = {Items.COBBLESTONE, Items.DIRT, Items.OAK_LOG, Items.WHEAT, Items.IRON_INGOT, Items.COAL, Items.SAND,
			Items.GRAVEL, Items.CLAY_BALL, Items.EMERALD};
		for (int i = 0; i < 9; i++) {
			player.setItemInHand(InteractionHand.OFF_HAND, new ItemStack(kinds[i], 5));
			clickAir(helper, player);
			helper.assertTrue(last(seen).equals("message.aliveworkplace.route.filter_add"), "adding kind " + (i + 1) + " said " + last(seen));
		}
		List<Item> filter = player.getMainHandItem().getOrDefault(ModComponents.ROUTE, RouteData.EMPTY).filter();
		helper.assertTrue(filter.size() == 9 && filter.containsAll(List.of(kinds).subList(0, 9)), "nine kinds not kept: " + filter);
		player.setItemInHand(InteractionHand.OFF_HAND, new ItemStack(kinds[9]));
		clickAir(helper, player);
		filter = player.getMainHandItem().getOrDefault(ModComponents.ROUTE, RouteData.EMPTY).filter();
		helper.assertTrue(filter.size() == 9 && !filter.contains(kinds[9]), "a tenth kind was taken: " + filter);
		helper.assertTrue(player.getOffhandItem().is(kinds[9]) && player.getOffhandItem().getCount() == 1, "the other hand's item was used up");
		List<Item> reloaded = saveAndLoad(helper, player.getMainHandItem(), ModComponents.ROUTE).filter();
		helper.assertTrue(reloaded.equals(filter), "the kinds changed over a save: " + reloaded);

		player.setItemInHand(InteractionHand.OFF_HAND, new ItemStack(kinds[3]));
		clickAir(helper, player);
		filter = player.getMainHandItem().getOrDefault(ModComponents.ROUTE, RouteData.EMPTY).filter();
		helper.assertTrue(filter.size() == 8 && !filter.contains(kinds[3]), "right-clicking again with wheat should drop it: " + filter);
		helper.assertTrue(last(seen).equals("message.aliveworkplace.route.filter_remove"), "removing said " + last(seen));
		helper.succeed();
	}

	// ---- Scan Tool ------------------------------------------------------------------------------------------------

	private static ItemStack findBlueprint(ServerPlayer player) {
		for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
			if (player.getInventory().getItem(i).is(ModItems.BLUEPRINT)) {
				return player.getInventory().getItem(i);
			}
		}
		return ItemStack.EMPTY;
	}

	/**
	 * Right-click one corner, then the opposite one, then sneak-right-click the air: a survival player's Blank
	 * Blueprint becomes a blueprint of exactly that box. Sneaking on the air before both corners are marked saves
	 * nothing and says so; a plain right-click on the air does nothing.
	 */
	//$ gametest AREA
	@GameTest(template = AREA, batch = "qa3Scan")
	public void scanToolMarksTwoCornersAndSavesABlueprintFromABlank(GameTestHelper helper) {
		Leftovers.clear(helper);
		List<Component> seen = new ArrayList<>();
		ServerPlayer player = listener(helper, seen);
		helper.setBlock(new BlockPos(3, 2, 3), Blocks.OAK_PLANKS);
		helper.setBlock(new BlockPos(5, 3, 4), Blocks.COBBLESTONE);
		player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(ModItems.SCAN_TOOL));
		player.getInventory().add(new ItemStack(ModItems.BLANK_BLUEPRINT, 3));

		player.setShiftKeyDown(true);
		clickAir(helper, player);
		player.setShiftKeyDown(false);
		helper.assertTrue(last(seen).equals("message.aliveworkplace.scan.unmarked"), "unmarked save said " + last(seen));
		helper.assertTrue(findBlueprint(player).isEmpty() && player.getInventory().countItem(ModItems.BLANK_BLUEPRINT) == 3,
			"an unmarked tool saved something");

		click(helper, player, new BlockPos(3, 2, 3));
		helper.assertTrue(last(seen).equals("message.aliveworkplace.scan.first"), "first corner said " + last(seen));
		click(helper, player, new BlockPos(5, 3, 4));
		helper.assertTrue(last(seen).equals("message.aliveworkplace.scan.marked"), "second corner said " + last(seen));
		FieldData data = player.getMainHandItem().getOrDefault(ModComponents.SCAN, FieldData.EMPTY);
		helper.assertTrue(data.isComplete(), "both corners not marked: " + data);
		helper.assertTrue(saveAndLoad(helper, player.getMainHandItem(), ModComponents.SCAN).equals(data), "the marks changed over a save");
		helper.assertTrue(overTheNetwork(helper, player.getMainHandItem()).get(ModComponents.SCAN).equals(data), "the marks changed over the network");

		int before = seen.size();
		clickAir(helper, player);
		helper.assertTrue(seen.size() == before && findBlueprint(player).isEmpty(), "a plain right-click on the air saved or said something");

		player.setShiftKeyDown(true);
		clickAir(helper, player);
		player.setShiftKeyDown(false);
		helper.assertTrue(last(seen).equals("message.aliveworkplace.scan.saved"), "sneak-right-click on the air said " + last(seen));
		ItemStack blueprint = findBlueprint(player);
		helper.assertTrue(!blueprint.isEmpty(), "no blueprint in the inventory after saving");
		helper.assertTrue(player.getInventory().countItem(ModItems.BLANK_BLUEPRINT) == 2, "saving should use exactly one Blank Blueprint");
		var size = BlueprintItem.data(blueprint).orElseThrow().size().orElseThrow();
		helper.assertTrue(size.getX() == 3 && size.getY() == 2 && size.getZ() == 2, "the blueprint is " + size + ", the box was 3x2x2");
		helper.succeed();
	}

	/** A scan is at most 48 on a side: 48 is marked, 49 is refused with the sizes and keeps the first corner. */
	//$ gametest AREA
	@GameTest(template = AREA, batch = "qa3ScanSize")
	public void scanToolTakesFortyEightOnASideAndRefusesFortyNine(GameTestHelper helper) {
		Leftovers.clear(helper);
		List<Component> seen = new ArrayList<>();
		ServerPlayer player = listener(helper, seen);
		player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(ModItems.SCAN_TOOL));
		helper.setBlock(new BlockPos(1, 1, 1), Blocks.STONE);
		BlockPos a = helper.absolutePos(new BlockPos(1, 1, 1));

		ServerLevel level = helper.getLevel();
		BlockPos far = a.offset(48, 0, 0); // 49 blocks across
		level.setBlockAndUpdate(far, Blocks.STONE.defaultBlockState());
		BlockPos edge = a.offset(47, 0, 0); // 48 across
		level.setBlockAndUpdate(edge, Blocks.STONE.defaultBlockState());
		try {
			click(helper, player, new BlockPos(1, 1, 1));
			var result = player.gameMode.useItemOn(player, level, player.getMainHandItem(), InteractionHand.MAIN_HAND,
				new BlockHitResult(Vec3.atCenterOf(far), Direction.UP, far, false));
			helper.assertTrue(last(seen).equals("message.aliveworkplace.scan.too_big"), "49 across said " + last(seen));
			helper.assertTrue(seen.get(seen.size() - 1).getString().contains("49"), "the refusal doesn't give the size: " + seen.get(seen.size() - 1).getString());
			FieldData data = player.getMainHandItem().getOrDefault(ModComponents.SCAN, FieldData.EMPTY);
			helper.assertTrue(!data.isComplete() && data.first().equals(Optional.of(a)), "a refused corner changed the marks: " + data + " (" + result + ")");

			player.gameMode.useItemOn(player, level, player.getMainHandItem(), InteractionHand.MAIN_HAND,
				new BlockHitResult(Vec3.atCenterOf(edge), Direction.UP, edge, false));
			helper.assertTrue(last(seen).equals("message.aliveworkplace.scan.marked"), "48 across said " + last(seen));
			helper.assertTrue(player.getMainHandItem().getOrDefault(ModComponents.SCAN, FieldData.EMPTY).isComplete(), "48 across not marked");
		} finally {
			level.setBlockAndUpdate(far, Blocks.AIR.defaultBlockState());
			level.setBlockAndUpdate(edge, Blocks.AIR.defaultBlockState());
		}
		helper.succeed();
	}

	/**
	 * "Name it by renaming the tool in an anvil": a build named with a full stop in it ("St. Mary's Chapel") is saved
	 * under its whole name, not cut off at the full stop.
	 */
	//$ gametest AREA
	@GameTest(template = AREA, batch = "qa3ScanName")
	public void scanToolKeepsTheWholeAnvilNameWithAFullStop(GameTestHelper helper) {
		Leftovers.clear(helper);
		List<Component> seen = new ArrayList<>();
		ServerPlayer player = listener(helper, seen);
		helper.setBlock(new BlockPos(3, 2, 3), Blocks.OAK_PLANKS);
		ItemStack tool = new ItemStack(ModItems.SCAN_TOOL);
		tool.set(DataComponents.CUSTOM_NAME, Component.literal("St. Mary's Chapel"));
		player.setItemInHand(InteractionHand.MAIN_HAND, tool);
		player.getInventory().add(new ItemStack(ModItems.BLANK_BLUEPRINT));
		click(helper, player, new BlockPos(3, 2, 3));
		click(helper, player, new BlockPos(4, 3, 4));
		player.setShiftKeyDown(true);
		clickAir(helper, player);
		player.setShiftKeyDown(false);
		ItemStack blueprint = findBlueprint(player);
		helper.assertTrue(!blueprint.isEmpty(), "not saved: " + last(seen));
		ResourceLocation id = BlueprintItem.data(blueprint).orElseThrow().structure();
		helper.assertTrue(id.getPath().contains("mary") && id.getPath().contains("chapel"),
			"the anvil name \"St. Mary's Chapel\" was saved as " + id + " (\"" + seen.get(seen.size() - 1).getString() + "\")");
		helper.succeed();
	}

	// ---- Village Banner recipe ------------------------------------------------------------------------------------

	private static ItemStack craft(GameTestHelper helper, int width, int height, ItemStack... grid) {
		Level level = helper.getLevel();
		CraftingInput input = CraftingInput.of(width, height, List.of(grid));
		return level.getRecipeManager().getRecipeFor(RecipeType.CRAFTING, input, level)
			.map(r -> r.value().assemble(input, level.registryAccess())).orElse(ItemStack.EMPTY);
	}

	/**
	 * Any banner and one gold ingot, anywhere in the grid, make a Village Banner of that banner's colour and patterns;
	 * a second gold ingot, a second banner, a stray item, or a banner or gold ingot alone make nothing.
	 */
	//$ gametest 'FabricGameTest.EMPTY_STRUCTURE'
	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
	public void villageBannerIsCraftedFromAnyBannerAndOneGoldIngot(GameTestHelper helper) {
		ItemStack e = ItemStack.EMPTY;
		ItemStack gold = new ItemStack(Items.GOLD_INGOT);
		var patternReg = helper.getLevel().registryAccess().registryOrThrow(Registries.BANNER_PATTERN);
		BannerPatternLayers layers = new BannerPatternLayers.Builder()
			.add(patternReg.getHolderOrThrow(BannerPatterns.STRIPE_BOTTOM), DyeColor.YELLOW)
			.add(patternReg.getHolderOrThrow(BannerPatterns.CROSS), DyeColor.BLACK).build();
		ItemStack red = new ItemStack(Items.RED_BANNER);
		red.set(DataComponents.BANNER_PATTERNS, layers);

		ItemStack made = craft(helper, 3, 3, e, e, e, e, red.copy(), e, e, e, gold.copy());
		helper.assertTrue(made.is(ModItems.VILLAGE_BANNER), "a red patterned banner and gold made " + made);
		helper.assertTrue(VillageBannerItem.base(made) == DyeColor.RED, "base colour " + VillageBannerItem.base(made));
		helper.assertTrue(VillageBannerItem.patterns(made).equals(layers), "patterns lost: " + VillageBannerItem.patterns(made));
		ItemStack plain = craft(helper, 2, 1, gold.copy(), new ItemStack(Items.LIGHT_BLUE_BANNER));
		helper.assertTrue(plain.is(ModItems.VILLAGE_BANNER) && VillageBannerItem.base(plain) == DyeColor.LIGHT_BLUE
			&& VillageBannerItem.patterns(plain).layers().isEmpty(), "a plain light blue banner made " + plain);
		helper.assertTrue(made.getMaxStackSize() == 16, "a Village Banner stacks to " + made.getMaxStackSize());

		helper.assertTrue(craft(helper, 3, 1, red.copy(), gold.copy(), gold.copy()).isEmpty(), "two gold ingots made something");
		helper.assertTrue(craft(helper, 3, 1, red.copy(), new ItemStack(Items.WHITE_BANNER), gold.copy()).isEmpty(), "two banners made something");
		helper.assertTrue(craft(helper, 3, 1, red.copy(), gold.copy(), new ItemStack(Items.STICK)).isEmpty(), "a stray stick made something");
		helper.assertTrue(craft(helper, 2, 1, red.copy(), e).isEmpty(), "a banner alone made something");
		helper.assertTrue(!craft(helper, 2, 1, gold.copy(), e).is(ModItems.VILLAGE_BANNER), "a gold ingot alone made a Village Banner");
		helper.assertTrue(layers.equals(saveAndLoad(helper, made, DataComponents.BANNER_PATTERNS)), "the patterns changed over a save");
		helper.assertTrue(saveAndLoad(helper, made, ModComponents.VILLAGE_BANNER) == DyeColor.RED, "the base colour was lost over a save");
		ItemStack sent = overTheNetwork(helper, made);
		helper.assertTrue(sent.get(ModComponents.VILLAGE_BANNER) == DyeColor.RED && layers.equals(sent.get(DataComponents.BANNER_PATTERNS)),
			"the design changed over the network");
		helper.succeed();
	}

	// ---- Data components over a save and the network --------------------------------------------------------------

	private static <T> void roundTrip(GameTestHelper helper, Item item, DataComponentType<T> type, T value) {
		ItemStack stack = new ItemStack(item);
		stack.set(type, value);
		T saved = saveAndLoad(helper, stack, type);
		helper.assertTrue(value.equals(saved), type + " on " + item + " changed over a save: " + value + " -> " + saved);
		T sent = overTheNetwork(helper, stack).get(type);
		helper.assertTrue(value.equals(sent), type + " on " + item + " changed over the network: " + value + " -> " + sent);
	}

	/**
	 * Each item component a player carries around (a Patrol Map's route, a City Plan's hall, a Colony Charter, a Village
	 * Hall's city plan, a luxury good's made day) comes back the same after a world save and after being sent to a
	 * client, with every optional part both set and unset. A placed blueprint's supply report goes to the client but
	 * is never saved.
	 */
	//$ gametest 'FabricGameTest.EMPTY_STRUCTURE'
	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
	public void itemComponentsComeBackTheSameAfterASaveAndOverTheNetwork(GameTestHelper helper) {
		ResourceLocation overworld = Level.OVERWORLD.location();
		ResourceLocation nether = Level.NETHER.location();
		roundTrip(helper, ModItems.PATROL_MAP, ModComponents.PATROL, new PatrolMapItem.Route(Optional.of(nether),
			List.of(new BlockPos(1, 64, -3), new BlockPos(-30000000 + 1, -64, 29999999), new BlockPos(0, 320, 0))));
		roundTrip(helper, ModItems.PATROL_MAP, ModComponents.PATROL, PatrolMapItem.Route.EMPTY);

		GlobalPos hall = GlobalPos.of(Level.NETHER, new BlockPos(-120, 70, 455));
		roundTrip(helper, ModItems.CITY_PLAN, ModComponents.CITY_PLAN_HALL,
			new VillageLedgerItem.Ledger(hall, Component.literal("Ölberg — \"Nord\"")));
		roundTrip(helper, ModItems.CITY_PLAN, ModComponents.CITY_PLAN_HALL,
			new VillageLedgerItem.Ledger(hall, Component.translatable("block.aliveworkplace.village_hall")));
		roundTrip(helper, ModItems.COLONY_CHARTER, ModComponents.COLONY_CHARTER,
			new ColonyCharterItem.Charter(hall, Component.literal("Riverside"), Optional.of(new BlockPos(500, 63, -800))));
		roundTrip(helper, ModItems.COLONY_CHARTER, ModComponents.COLONY_CHARTER,
			new ColonyCharterItem.Charter(hall, Component.literal("Riverside"), Optional.empty()));

		BitSet cells = new BitSet();
		cells.set(0);
		cells.set(63);
		cells.set(64);
		cells.set(CityPlan.GRID * CityPlan.GRID - 1);
		CityPlan plan = new CityPlan(
			List.of(new CityPlan.Zone("homes", "North End", "desert", true, cells), new CityPlan.Zone("farms", "Fields", "", false, new BitSet())),
			List.of(new CityPlan.Road(List.of(new BlockPos(1, 64, 1), new BlockPos(30, 64, 1)), CityPlan.Road.AVENUE, "cobble", true,
					List.of(new BlockPos(1, 64, 1), new BlockPos(2, 64, 1)), true, List.of(0, 3), false, 2, 5, Optional.of(new BlockPos(400, 0, -2))),
				new CityPlan.Road(List.of(new BlockPos(5, 64, 5)), CityPlan.Road.LANE, "", false, List.of(), false, List.of(), true)),
			Optional.of(new CityPlan.Wall(List.of(new BlockPos(0, 64, 0), new BlockPos(40, 64, 0), new BlockPos(40, 64, 40)), false, "stone", true, true)),
			CityPlan.Mode.RUN);
		roundTrip(helper, ModBlocks.VILLAGE_HALL.asItem(), ModComponents.CITY_PLAN, plan);
		roundTrip(helper, ModBlocks.VILLAGE_HALL.asItem(), ModComponents.CITY_PLAN, CityPlan.EMPTY);

		roundTrip(helper, Items.HONEY_BOTTLE, ModComponents.MADE_DAY, new LuxuryRecipes.MadeDay(4_000_000_000L, 7));
		roundTrip(helper, Items.HONEY_BOTTLE, ModComponents.MADE_DAY, new LuxuryRecipes.MadeDay(0, 0));
		roundTrip(helper, ModItems.SCAN_TOOL, ModComponents.SCAN, new FieldData(Optional.of(overworld), Optional.of(new BlockPos(1, 2, 3)), Optional.empty()));
		roundTrip(helper, ModItems.DELIVERY_NOTE, ModComponents.ROUTE,
			new RouteData(Optional.empty(), Optional.of(new BlockPos(9, 9, 9)), List.of(Items.DIAMOND, Items.COBBLESTONE)));

		ItemStack placed = new ItemStack(ModItems.BLUEPRINT);
		SupplyReport report = new SupplyReport(Optional.of(new BlockPos(3, 70, -9)), 4,
			List.of(new SupplyReport.Missing(Items.OAK_PLANKS, 120), new SupplyReport.Missing(Items.GLASS_PANE, 1)));
		placed.set(ModComponents.SUPPLY_REPORT, report);
		helper.assertTrue(report.equals(overTheNetwork(helper, placed).get(ModComponents.SUPPLY_REPORT)), "the supply report changed over the network");
		helper.assertTrue(saveAndLoad(helper, placed, ModComponents.SUPPLY_REPORT) == null, "the supply report, never saved, came back from a save");
		helper.succeed();
	}

	// ---- Entities -------------------------------------------------------------------------------------------------

	/** None of the four can be summoned with /summon, and the bobber is never saved (ModEntities). */
	//$ gametest 'FabricGameTest.EMPTY_STRUCTURE'
	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
	public void theBobberAndSeatsCantBeSummonedAndTheBobberIsNeverSaved(GameTestHelper helper) {
		for (EntityType<?> type : List.<EntityType<?>>of(ModEntities.FISHING_BOBBER, ModEntities.CRADLE_SEAT, ModEntities.STAND_SEAT, ModEntities.FURNITURE_SEAT)) {
			helper.assertTrue(!type.canSummon(), type + " can be summoned");
		}
		var server = helper.getLevel().getServer();
		var source = server.createCommandSourceStack().withPermission(4).withSuppressedOutput()
			.withPosition(Vec3.atCenterOf(helper.absolutePos(BlockPos.ZERO)));
		int before = helper.getLevel().getEntities(ModEntities.CRADLE_SEAT, e -> true).size();
		server.getCommands().performPrefixedCommand(source, "summon aliveworkplace:cradle_seat ~ ~1 ~");
		helper.assertTrue(helper.getLevel().getEntities(ModEntities.CRADLE_SEAT, e -> true).size() == before, "/summon made a cradle seat");
		helper.assertTrue(!ModEntities.FISHING_BOBBER.canSerialize(), "the bobber is saved");
		for (EntityType<?> type : List.<EntityType<?>>of(ModEntities.CRADLE_SEAT, ModEntities.STAND_SEAT, ModEntities.FURNITURE_SEAT)) {
			helper.assertTrue(type.canSerialize(), type + " isn't saved, so its rider would be lost with it");
		}
		helper.succeed();
	}

	private static Villager villager(GameTestHelper helper, int x, int z) {
		Villager v = helper.spawn(EntityType.VILLAGER, x, 2, z);
		v.setNoAi(true);
		return v;
	}

	/** The bobber goes when its fisherman dies or walks more than 16 blocks away, and stays while they fish nearby. */
	//$ gametest AREA
	@GameTest(template = AREA, batch = "qa3Bobber", timeoutTicks = 100)
	public void theBobberGoesWhenItsFishermanDiesOrWalksOff(GameTestHelper helper) {
		Leftovers.clear(helper);
		ServerLevel level = helper.getLevel();
		Villager near = villager(helper, 2, 2);
		near.setVillagerData(near.getVillagerData().setProfession(VillagerProfession.FISHERMAN));
		Villager walker = villager(helper, 5, 5);
		walker.setVillagerData(walker.getVillagerData().setProfession(VillagerProfession.FISHERMAN));
		FishingBobber kept = FishingBobber.cast(level, near, Vec3.atCenterOf(helper.absolutePos(new BlockPos(3, 2, 3))));
		FishingBobber left = FishingBobber.cast(level, walker, Vec3.atCenterOf(helper.absolutePos(new BlockPos(5, 2, 6))));
		helper.assertTrue(kept.owner() == near && left.owner() == walker, "a bobber doesn't know its fisherman");
		helper.runAfterDelay(5, () -> {
			helper.assertTrue(!kept.isRemoved() && !left.isRemoved(), "a bobber went while its fisherman stood by");
			walker.teleportTo(walker.getX() + 17, walker.getY(), walker.getZ());
			near.kill();
		});
		helper.runAfterDelay(10, () -> {
			helper.assertTrue(kept.isRemoved(), "the bobber stayed after its fisherman died");
			helper.assertTrue(left.isRemoved(), "the bobber stayed with its fisherman 17 blocks away");
			walker.discard();
			helper.succeed();
		});
	}

	/** Saves {@code vehicle} with its riders and loads it back as a world save would; returns the loaded vehicle. */
	private static Entity reloadWithRiders(GameTestHelper helper, Entity vehicle) {
		ServerLevel level = helper.getLevel();
		CompoundTag tag = new CompoundTag();
		helper.assertTrue(vehicle.save(tag), vehicle + " wasn't saved");
		List<Entity> riders = new ArrayList<>(vehicle.getPassengers());
		riders.forEach(Entity::discard);
		vehicle.discard();
		Entity back = EntityType.loadEntityRecursive(tag, level, e -> {
			e.setUUID(UUID.randomUUID());
			return e;
		});
		helper.assertTrue(back != null, "nothing loaded from " + tag);
		level.tryAddFreshEntityWithPassengers(back);
		return back;
	}

	/**
	 * A child asleep in a cradle stays in its seat through the night and through a save (it's saved with the seat),
	 * and is let go, with the seat gone, as soon as the cradle is broken.
	 */
	//$ gametest AREA
	@GameTest(template = AREA, batch = "qa3Cradle", timeoutTicks = 200)
	public void aChildInACradleIsSavedWithItsSeatAndLetGoWhenTheCradleBreaks(GameTestHelper helper) {
		Leftovers.clear(helper);
		ServerLevel level = helper.getLevel();
		helper.assertTrue(Cradles.ENABLED, "cradles are off in the test world");
		long day = level.getDayTime();
		level.setDayTime(day - day % 24000 + 18000);
		Leftovers.after(helper, () -> level.setDayTime(level.getDayTime() - level.getDayTime() % 24000 + 1000));
		BlockPos cradle = new BlockPos(3, 2, 3);
		helper.setBlock(cradle, ModBlocks.CRADLE);
		Villager child = villager(helper, 4, 4);
		child.setAge(-24000);
		CradleSeat seat = CradleSeat.at(level, helper.absolutePos(cradle));
		level.addFreshEntity(seat);
		helper.assertTrue(child.startRiding(seat, true), "the child couldn't get into the cradle");
		helper.runAfterDelay(25, () -> {
			helper.assertTrue(!seat.isRemoved() && child.getVehicle() == seat, "the child was let go at night in a standing cradle");
			Entity back = reloadWithRiders(helper, seat);
			helper.assertTrue(back instanceof CradleSeat && back.getPassengers().size() == 1 && back.getPassengers().get(0) instanceof Villager v && v.isBaby(),
				"the child wasn't saved with the cradle seat: " + back.getPassengers());
			helper.runAfterDelay(25, () -> {
				helper.assertTrue(!back.isRemoved() && back.getPassengers().size() == 1, "a reloaded seat let the child go at night");
				Villager kid = (Villager) back.getPassengers().get(0);
				helper.destroyBlock(cradle);
				helper.runAfterDelay(21, () -> {
					helper.assertTrue(kid.getVehicle() == null, "the child still sits where the cradle was broken");
					helper.assertTrue(back.isRemoved(), "the cradle seat stayed after the cradle was broken");
					helper.assertTrue(kid.isAlive(), "the child died getting out");
					kid.discard();
					helper.succeed();
				});
			});
		});
	}

	/**
	 * With no Cup or Stadium Day, the stands are closed: a villager put on a stand seat is let go within a second,
	 * the seat goes, and they stand up outside the stair, not inside it (B94).
	 */
	//$ gametest AREA
	@GameTest(template = AREA, batch = "qa3Stand", timeoutTicks = 100)
	public void aStandSeatLetsItsVillagerGoOutsideTheStairWhenNoCupIsOn(GameTestHelper helper) {
		Leftovers.clear(helper);
		ServerLevel level = helper.getLevel();
		BlockPos bench = new BlockPos(3, 2, 3);
		helper.setBlock(bench, Blocks.OAK_STAIRS.defaultBlockState().setValue(net.minecraft.world.level.block.StairBlock.FACING, Direction.NORTH));
		Villager v = villager(helper, 5, 5);
		StandSeat seat = StandSeat.at(level, helper.absolutePos(bench));
		level.addFreshEntity(seat);
		helper.assertTrue(v.startRiding(seat, true), "the villager couldn't sit");
		helper.runAfterDelay(22, () -> {
			helper.assertTrue(v.getVehicle() == null, "the villager still sits in closed stands");
			helper.assertTrue(seat.isRemoved(), "the stand seat stayed with no one on it");
			helper.assertTrue(!v.blockPosition().equals(helper.absolutePos(bench)), "the villager stood up inside the stair");
			helper.assertTrue(level.noCollision(v), "the villager stood up inside a block at " + v.blockPosition());
			helper.succeed();
		});
	}

	/**
	 * A furniture seat loaded from a save lets its villager go on its first tick (a villager's seat isn't kept over a
	 * save, M37), and the villager comes back with it rather than being lost; an empty seat goes at once.
	 */
	//$ gametest AREA
	@GameTest(template = AREA, batch = "qa3FurnitureSeat", timeoutTicks = 100)
	public void aFurnitureSeatLoadedFromASaveStandsItsVillagerUp(GameTestHelper helper) {
		Leftovers.clear(helper);
		ServerLevel level = helper.getLevel();
		BlockPos chair = new BlockPos(3, 2, 3);
		Villager v = villager(helper, 5, 5);
		v.setVillagerData(v.getVillagerData().setProfession(VillagerProfession.LIBRARIAN));
		FurnitureSeat seat = FurnitureSeat.at(level, helper.absolutePos(chair));
		level.addFreshEntity(seat);
		helper.assertTrue(v.startRiding(seat, true), "the villager couldn't sit");
		FurnitureSeat empty = FurnitureSeat.at(level, helper.absolutePos(new BlockPos(5, 2, 2)));
		level.addFreshEntity(empty);
		Entity back = reloadWithRiders(helper, seat);
		helper.assertTrue(back instanceof FurnitureSeat && back.getPassengers().size() == 1, "the villager wasn't saved with the seat");
		Villager sitter = (Villager) back.getPassengers().get(0);
		helper.assertTrue(sitter.getVillagerData().getProfession() == VillagerProfession.LIBRARIAN, "the villager came back as someone else");
		helper.runAfterDelay(3, () -> {
			helper.assertTrue(sitter.getVehicle() == null, "a seat loaded from a save kept its villager past its first ticks");
			helper.assertTrue(back.isRemoved(), "the loaded seat stayed");
			helper.assertTrue(empty.isRemoved(), "an empty seat stayed");
			helper.assertTrue(sitter.isAlive() && level.noCollision(sitter), "the villager stood up inside a block at " + sitter.blockPosition());
			sitter.discard();
			helper.succeed();
		});
	}
}
