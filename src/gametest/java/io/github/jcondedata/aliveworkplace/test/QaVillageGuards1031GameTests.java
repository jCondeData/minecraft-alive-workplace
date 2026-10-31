package io.github.jcondedata.aliveworkplace.test;

import io.github.jcondedata.aliveworkplace.blueprint.BlueprintData;
import io.github.jcondedata.aliveworkplace.blueprint.BlueprintItem;
import io.github.jcondedata.aliveworkplace.blueprint.BlueprintLibrary;
import io.github.jcondedata.aliveworkplace.build.BuildSite;
import io.github.jcondedata.aliveworkplace.build.BuildSiteManager;
import io.github.jcondedata.aliveworkplace.build.Builders;
import io.github.jcondedata.aliveworkplace.build.Friends;
import io.github.jcondedata.aliveworkplace.camp.CampBeds;
import io.github.jcondedata.aliveworkplace.camp.SettlersWagonItem;
import io.github.jcondedata.aliveworkplace.hall.VillageHalls;
import io.github.jcondedata.aliveworkplace.mail.DeliveryNoteItem;
import io.github.jcondedata.aliveworkplace.mail.PostmanWork;
import io.github.jcondedata.aliveworkplace.mail.RouteData;
import io.github.jcondedata.aliveworkplace.registry.ModBlocks;
import io.github.jcondedata.aliveworkplace.registry.ModComponents;
import io.github.jcondedata.aliveworkplace.registry.ModItems;
import io.github.jcondedata.aliveworkplace.shop.ShopCounterBlockEntity;
import io.github.jcondedata.aliveworkplace.shop.Shops;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.GlobalPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.Container;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BedPart;
import net.minecraft.world.level.block.state.properties.ChestType;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

/**
 * QA (2026-10-31) of the four village-guard fixes, from their specs (ROADMAP B715-B718), past what the fixer's own tests
 * cover: boundaries from both sides and the likeliest ways each fix breaks.
 * <ul>
 * <li>B715: a placed camp's bedrolls stay wagon beds when its campfire, hay and table are gone and after a reload; a
 * cabin's bed just past the camp-table look stays a home.</li>
 * <li>B716: a chest out of the nearer counter's (vertical) reach stays the other shop's, a double chest whose halves tie
 * belongs to exactly one shop, and a full shop's payment never spills into the neighbour's chest.</li>
 * <li>B717: a stranger can't point a route's destination into a protected village, the village's edge both sides, and a
 * friend's route stops once he isn't one.</li>
 * <li>B718: a build touching the village's edge is refused, one block out is taken, with the message and the blueprint
 * kept; the owner's friend's build is taken.</li>
 * </ul>
 */
public class QaVillageGuards1031GameTests implements net.fabricmc.fabric.api.gametest.v1.FabricGameTest {
	private static final String HUGE = "aliveworkplace_test:huge_area";
	private static final String BIG = "aliveworkplace_test:big_area";
	private static final ResourceLocation TEST_HUT = ResourceLocation.fromNamespaceAndPath("aliveworkplace_test", "test_hut");

	// --- shared helpers -----------------------------------------------------------------------------------------------

	static List<String> said(QaStageFiveGameTests.Client c) {
		return c.seen().stream().map(Component::getString).toList();
	}

	static BlockPos home(Villager v) {
		return v.getBrain().getMemory(MemoryModuleType.HOME).map(GlobalPos::pos).orElse(null);
	}

	/** A bed (foot at {@code foot}, head south of it); its head, absolute. */
	static BlockPos bed(GameTestHelper helper, BlockPos foot, net.minecraft.world.level.block.Block bed) {
		helper.setBlock(foot, bed.defaultBlockState().setValue(BedBlock.FACING, Direction.SOUTH).setValue(BedBlock.PART, BedPart.FOOT));
		helper.setBlock(foot.south(), bed.defaultBlockState().setValue(BedBlock.FACING, Direction.SOUTH).setValue(BedBlock.PART, BedPart.HEAD));
		return helper.absolutePos(foot.south());
	}

	/** Village radius 18 for this test, and every wagon bed listed in the area taken off the list afterwards. */
	static void campArea(GameTestHelper helper) {
		Leftovers.clear(helper);
		int radius = VillageHalls.RADIUS;
		VillageHalls.RADIUS = 18;
		ServerLevel level = helper.getLevel();
		CampBeds.Data live = CampBeds.Data.get(level);
		BoundingBox box = BoundingBox.fromCorners(helper.absolutePos(new BlockPos(-2, 0, -2)), helper.absolutePos(new BlockPos(31, 12, 31)));
		Leftovers.after(helper, () -> {
			VillageHalls.RADIUS = radius;
			level.getDataStorage().set(CampBeds.Data.NAME, live);
			live.forget(box);
		});
	}

	// --- B715: wagon beds -------------------------------------------------------------------------------------------

	/**
	 * B207/B715: a Settlers' Wagon's camp, really placed (its beds listed at placement). Its bedrolls stay wagon beds after
	 * the list is saved and loaded (not sent back to "unverified" like a pre-fix list) and after the camp's campfire, hay
	 * and Blueprint Table are all gone; the hall's census counts them as wagon beds, not homes.
	 */
	//$ gametest_ticks_batch HUGE '200' '"qaGuardsPlacedCampReload"'
	@GameTest(template = HUGE, timeoutTicks = 200, batch = "qaGuardsPlacedCampReload")
	public void aPlacedCampsBedrollsStayWagonBedsAfterAReloadWithTheCampGone(GameTestHelper helper) {
		campArea(helper);
		ServerLevel level = helper.getLevel();
		ServerPlayer player = ProtectionSpecGameTests.player(helper);
		List<Villager> settlers = SettlersWagonItem.makeCamp(level, player, helper.absolutePos(new BlockPos(15, 2, 4)));
		settlers.forEach(v -> v.setNoAi(true));
		List<BlockPos> rolls = new ArrayList<>();
		int fires = 0;
		for (BlockPos p : BlockPos.betweenClosed(helper.absolutePos(new BlockPos(0, 1, 0)), helper.absolutePos(new BlockPos(29, 10, 29)))) {
			BlockState state = level.getBlockState(p);
			if ((state.is(Blocks.BROWN_BED) || state.is(Blocks.GREEN_BED)) && state.getValue(BedBlock.PART) == BedPart.HEAD) {
				rolls.add(p.immutable());
			}
		}
		helper.assertTrue(!rolls.isEmpty(), "setup: the placed camp has no bedrolls");
		CampBeds.Data data = CampBeds.Data.get(level);
		for (BlockPos roll : rolls) {
			helper.assertTrue(data.has(roll) && !data.unverified(roll), "a placed camp's bedroll " + roll + " isn't listed as checked (has " + data.has(roll)
				+ ", unverified " + data.unverified(roll) + ")");
		}
		CampBeds.Data reloaded = CampBeds.Data.load(data.save(new CompoundTag(), level.registryAccess()), level.registryAccess());
		for (BlockPos roll : rolls) {
			helper.assertTrue(reloaded.has(roll) && !reloaded.unverified(roll), "after a save and reload the placed camp's bedroll " + roll
				+ " is has " + reloaded.has(roll) + ", unverified " + reloaded.unverified(roll) + " (a v2 list must stay checked)");
		}
		level.getDataStorage().set(CampBeds.Data.NAME, reloaded);
		// the camp's look goes: campfire, hay and the builder's table taken away
		for (BlockPos p : BlockPos.betweenClosed(helper.absolutePos(new BlockPos(0, 1, 0)), helper.absolutePos(new BlockPos(29, 10, 29)))) {
			BlockState state = level.getBlockState(p);
			if (state.is(BlockTags.CAMPFIRES) || state.is(Blocks.HAY_BLOCK) || state.is(ModBlocks.BLUEPRINT_TABLE) || state.is(ModBlocks.BUILDERS_BENCH)) {
				level.setBlockAndUpdate(p, Blocks.AIR.defaultBlockState());
				fires++;
			}
		}
		helper.assertTrue(fires >= 3, "setup: the camp's campfire, hay and table weren't found to take away (" + fires + ")");
		helper.runAfterDelay(2, () -> {
			for (BlockPos roll : rolls) {
				helper.assertTrue(CampBeds.isWagonBed(level, roll), "the placed camp's bedroll " + roll + " stopped being a wagon bed once its camp was cleared away");
			}
			helper.setBlock(new BlockPos(15, 2, 20), ModBlocks.VILLAGE_HALL);
			helper.runAfterDelay(60, () -> {
				VillageHalls.Census census = VillageHalls.census(level, helper.absolutePos(new BlockPos(15, 2, 20)));
				helper.assertTrue(census.wagonBeds() == rolls.size() && census.beds() == 0,
					"the hall counts beds " + census.beds() + ", wagon beds " + census.wagonBeds() + " (expected 0 and " + rolls.size() + ")");
				for (Villager v : settlers) {
					helper.assertTrue(home(v) == null || !rolls.contains(home(v)), "a settler still has a bedroll as his home after the hall's round: " + home(v));
				}
				helper.succeed();
			});
		});
	}

	/**
	 * B715 boundary, the home side: a cabin's brown bed with a campfire and a hay bale 6 blocks off and a Blueprint Table
	 * 13 blocks off (one past the camp look's 12) stays its resident's home through the hall's round and is a counted bed.
	 */
	//$ gametest_ticks_batch HUGE '200' '"qaGuardsCabinTable13"'
	@GameTest(template = HUGE, timeoutTicks = 200, batch = "qaGuardsCabinTable13")
	public void aCabinBedThirteenBlocksFromABlueprintTableStaysAHome(GameTestHelper helper) {
		campArea(helper);
		ServerLevel level = helper.getLevel();
		BlockPos head = bed(helper, new BlockPos(6, 2, 10), Blocks.BROWN_BED);
		helper.setBlock(new BlockPos(12, 2, 10), Blocks.CAMPFIRE);
		helper.setBlock(new BlockPos(0, 2, 10), Blocks.HAY_BLOCK);
		helper.setBlock(new BlockPos(19, 2, 11), ModBlocks.BLUEPRINT_TABLE); // 13 east of the bed's head
		Villager resident = helper.spawn(EntityType.VILLAGER, new BlockPos(7, 2, 8));
		resident.setNoAi(true);
		helper.runAfterDelay(3, () -> {
			helper.assertTrue(CampBeds.claim(level, resident, head), "setup: the resident couldn't take the cabin's bed");
			helper.setBlock(new BlockPos(15, 2, 20), ModBlocks.VILLAGE_HALL);
			helper.runAfterDelay(60, () -> {
				helper.assertTrue(!CampBeds.isWagonBed(level, head) && !CampBeds.Data.get(level).has(head),
					"a cabin's brown bed with a Blueprint Table 13 blocks off (past the camp look's 12) is taken for a wagon bed");
				helper.assertTrue(head.equals(home(resident)), "the cabin's resident lost his home at the hall's round: " + home(resident));
				VillageHalls.Census census = VillageHalls.census(level, helper.absolutePos(new BlockPos(15, 2, 20)));
				helper.assertTrue(census.beds() == 1 && census.wagonBeds() == 0, "beds " + census.beds() + ", wagon beds " + census.wagonBeds());
				helper.succeed();
			});
		});
	}

	// --- B716: a shop's own chests ----------------------------------------------------------------------------------

	private static ShopCounterBlockEntity counter(GameTestHelper helper, BlockPos at, UUID owner, String name) {
		helper.setBlock(at, ModBlocks.SHOP_COUNTER);
		ShopCounterBlockEntity counter = helper.getBlockEntity(at);
		counter.setOwner(owner, name);
		counter.setItem(0, new ItemStack(Items.COBBLESTONE, 16));
		counter.setItem(ShopCounterBlockEntity.COLUMNS, new ItemStack(Items.EMERALD, 1));
		return counter;
	}

	/**
	 * B716 reach boundary: a chest 7 blocks from Frank's counter has Bob's counter right above it. Bob's counter 5 up is
	 * nearer but can't reach it (4 up is the most), so it stays Frank's stock; 4 up it reaches it and it is Bob's alone.
	 */
	//$ gametest_ticks_batch BIG '100' '"qaGuardsShopVertical"'
	@GameTest(template = BIG, timeoutTicks = 100, batch = "qaGuardsShopVertical")
	public void aChestTheNearerCounterCantReachStaysTheOtherShops(GameTestHelper helper) {
		QaEconomyGameTests.Shop frank = QaEconomyGameTests.shop(helper, new ItemStack(Items.COBBLESTONE, 16), new ItemStack(Items.EMERALD, 1));
		ServerLevel level = helper.getLevel();
		BlockPos chestAt = new BlockPos(2, 2, 9);
		helper.setBlock(chestAt, Blocks.CHEST);
		Container chest = helper.getBlockEntity(chestAt);
		chest.setItem(0, new ItemStack(Items.COBBLESTONE, 32));
		BlockPos chest0 = helper.absolutePos(chestAt);
		UUID bobId = UUID.randomUUID();
		ShopCounterBlockEntity high = counter(helper, new BlockPos(2, 7, 9), bobId, "Bob");
		helper.assertTrue(Shops.stock(level, frank.counter()).contains(chest0),
			"a chest Bob's counter (5 blocks up) can't reach isn't Frank's stock any more: " + Shops.stock(level, frank.counter()));
		helper.assertTrue(!Shops.stock(level, high).contains(chest0), "Bob's counter 5 blocks up lists a chest out of its reach");
		Shops.refreshOffers(level, frank.keeper());
		helper.assertTrue(!frank.keeper().getOffers().isEmpty() && frank.keeper().getOffers().get(0).getMaxUses() == 2,
			"Frank's shop doesn't offer the 2 lots in its chest out of Bob's reach: " + frank.keeper().getOffers().size() + " offers");
		helper.setBlock(new BlockPos(2, 7, 9), Blocks.AIR);
		ShopCounterBlockEntity low = counter(helper, new BlockPos(2, 6, 9), bobId, "Bob");
		helper.assertTrue(!Shops.stock(level, frank.counter()).contains(chest0), "a chest right under Bob's counter (4 up, in reach) is still Frank's stock");
		helper.assertTrue(Shops.stock(level, low).contains(chest0), "a chest right under Bob's counter (4 up) isn't Bob's stock");
		Shops.refreshOffers(level, frank.keeper());
		helper.assertTrue(frank.keeper().getOffers().isEmpty() || frank.keeper().getOffers().get(0).isOutOfStock(),
			"Frank's empty shop still offers the cobblestone in the chest under Bob's counter");
		helper.succeed();
	}

	/**
	 * B716 tie, double chest: Frank's counter at (2, 2, 2), Bob's at (9, 2, 2), a double chest at (5..6, 2, 6) whose west
	 * half is 5 from Frank and east half 5 from Bob. Exactly one shop has it, asked from either side, three times over.
	 */
	//$ gametest_ticks_batch BIG '100' '"qaGuardsShopDoubleTie"'
	@GameTest(template = BIG, timeoutTicks = 100, batch = "qaGuardsShopDoubleTie")
	public void aDoubleChestWhoseHalvesTieIsExactlyOneShops(GameTestHelper helper) {
		QaEconomyGameTests.Shop frank = QaEconomyGameTests.shop(helper, new ItemStack(Items.COBBLESTONE, 16), new ItemStack(Items.EMERALD, 1));
		ServerLevel level = helper.getLevel();
		ShopCounterBlockEntity bob = counter(helper, new BlockPos(9, 2, 2), UUID.randomUUID(), "Bob");
		BlockPos west = new BlockPos(5, 2, 6);
		BlockPos east = new BlockPos(6, 2, 6);
		helper.setBlock(west, Blocks.CHEST.defaultBlockState().setValue(ChestBlock.FACING, Direction.NORTH).setValue(ChestBlock.TYPE, ChestType.LEFT));
		helper.setBlock(east, Blocks.CHEST.defaultBlockState().setValue(ChestBlock.FACING, Direction.NORTH).setValue(ChestBlock.TYPE, ChestType.RIGHT));
		BlockPos w = helper.absolutePos(west);
		BlockPos e = helper.absolutePos(east);
		helper.assertTrue(level.getBlockState(w).getValue(ChestBlock.TYPE) != ChestType.SINGLE && level.getBlockState(e).getValue(ChestBlock.TYPE) != ChestType.SINGLE
			&& ChestBlock.getConnectedDirection(level.getBlockState(w)) == Direction.EAST, "setup: the two halves aren't one double chest");
		Container half = helper.getBlockEntity(west);
		half.setItem(0, new ItemStack(Items.COBBLESTONE, 32));
		for (int i = 0; i < 3; i++) {
			List<BlockPos> franks = Shops.stock(level, frank.counter());
			List<BlockPos> bobs = Shops.stock(level, bob);
			boolean f = franks.contains(w) || franks.contains(e);
			boolean b = bobs.contains(w) || bobs.contains(e);
			helper.assertTrue(f != b, "the double chest whose halves tie is Frank's " + f + " and Bob's " + b + " (should be exactly one shop's)");
		}
		helper.succeed();
	}

	/**
	 * B716 payment: Frank's only chest is full, Bob's chest by Bob's counter 6 blocks off is empty. A sale at Frank's shop
	 * (the shop screen) takes Frank's cobblestone, and the emerald goes on Frank's counter, never into Bob's chest.
	 */
	//$ gametest_ticks_batch BIG '100' '"qaGuardsShopFullPay"'
	@GameTest(template = BIG, timeoutTicks = 100, batch = "qaGuardsShopFullPay")
	public void aFullShopsPaymentNeverSpillsIntoTheNeighboursChest(GameTestHelper helper) {
		ItemStack[] full = new ItemStack[27];
		full[0] = new ItemStack(Items.COBBLESTONE, 64);
		for (int i = 1; i < full.length; i++) {
			full[i] = new ItemStack(Items.DIRT, 64);
		}
		QaEconomyGameTests.Shop frank = QaEconomyGameTests.shop(helper, new ItemStack(Items.COBBLESTONE, 16), new ItemStack(Items.EMERALD, 1), full);
		counter(helper, new BlockPos(8, 2, 2), UUID.randomUUID(), "Bob");
		helper.setBlock(new BlockPos(8, 2, 4), Blocks.CHEST);
		Container bobs = helper.getBlockEntity(new BlockPos(8, 2, 4));
		ServerPlayer p = QaEconomyGameTests.buyer(helper).player();
		p.getInventory().setItem(9, new ItemStack(Items.EMERALD, 3));
		boolean bought = Shops.buy(p, frank.keeper(), 0);
		helper.assertTrue(bought, "the sale at Frank's full shop didn't go through");
		helper.assertTrue(frank.chest().countItem(Items.COBBLESTONE) == 48, "Frank's chest should have 48 cobblestone left, not " + frank.chest().countItem(Items.COBBLESTONE));
		helper.assertTrue(bobs.isEmpty(), "Frank's payment went into Bob's chest by Bob's counter: " + bobs.countItem(Items.EMERALD) + " emeralds");
		BlockPos at = helper.absolutePos(new BlockPos(2, 2, 2));
		int dropped = helper.getLevel().getEntitiesOfClass(ItemEntity.class, new AABB(at).inflate(2), i -> i.getItem().is(Items.EMERALD)).stream()
			.mapToInt(i -> i.getItem().getCount()).sum();
		helper.assertTrue(dropped == 1, "the emerald that didn't fit Frank's chest should land on his counter: " + dropped + " there");
		helper.succeed();
	}

	// --- B717: Delivery Notes in a protected village -----------------------------------------------------------------

	private static void use(GameTestHelper helper, ServerPlayer player, BlockPos pos, ItemStack held) {
		BlockPos at = helper.absolutePos(pos);
		player.setItemInHand(InteractionHand.MAIN_HAND, held);
		player.gameMode.useItemOn(player, helper.getLevel(), held, InteractionHand.MAIN_HAND,
			new BlockHitResult(Vec3.atCenterOf(at).relative(Direction.UP, 0.5), Direction.UP, at, false));
	}

	private static Container diamonds(GameTestHelper helper, BlockPos pos) {
		helper.setBlock(pos, Blocks.CHEST);
		Container chest = helper.getBlockEntity(pos);
		chest.setItem(0, new ItemStack(Items.DIAMOND, 10));
		return chest;
	}

	/**
	 * B717, the route's other end: a stranger marks his own chest far outside the village (24 blocks from the hall), then
	 * clicks the owner's chest inside it as the destination. The note takes no destination, the chest stays shut, and he
	 * is told it's Owner's village.
	 */
	//$ gametest_ticks_batch HUGE '60' '"qaGuardsNoteInto"'
	@GameTest(template = HUGE, timeoutTicks = 60, batch = "qaGuardsNoteInto")
	public void aStrangerCantPointHisRouteIntoAProtectedVillage(GameTestHelper helper) {
		ProtectionSpecGameTests.protectedHall(helper, ProtectionSpecGameTests.player(helper));
		QaStageFiveGameTests.Client c = QaStageSixGameTests.client(helper, GameType.SURVIVAL, QaStageSixGameTests.name("qgNote"));
		ServerPlayer stranger = c.player();
		BlockPos his = new BlockPos(28, 2, 28);
		BlockPos owners = ProtectionSpecGameTests.HALL.offset(3, 0, 3);
		diamonds(helper, his);
		diamonds(helper, owners);
		use(helper, stranger, his, new ItemStack(ModItems.DELIVERY_NOTE));
		ItemStack note = stranger.getMainHandItem();
		helper.assertTrue(DeliveryNoteItem.data(note).from().equals(Optional.of(helper.absolutePos(his))),
			"the stranger's note didn't take his own chest outside any village: " + DeliveryNoteItem.data(note) + " " + said(c));
		c.seen().clear();
		use(helper, stranger, owners, note);
		RouteData after = DeliveryNoteItem.data(stranger.getMainHandItem());
		helper.assertTrue(after.to().isEmpty(), "a stranger's note took the owner's chest in a protected village as its destination: " + after);
		helper.assertTrue(after.from().equals(Optional.of(helper.absolutePos(his))), "the refused click lost the note's source: " + after);
		helper.assertTrue(stranger.containerMenu == stranger.inventoryMenu, "the stranger's note opened the owner's chest");
		helper.assertTrue(said(c).stream().anyMatch(s -> s.contains("Owner's village")), "the stranger wasn't told whose village it is: " + said(c));
		helper.succeed();
	}

	/**
	 * B717 boundary (village reach 16 round the hall at (11, 2, 11)): a stranger's note on a chest exactly 16 blocks east of
	 * the hall is refused; on one 17 blocks east it marks the chest.
	 */
	//$ gametest_ticks_batch HUGE '60' '"qaGuardsNoteEdge"'
	@GameTest(template = HUGE, timeoutTicks = 60, batch = "qaGuardsNoteEdge")
	public void aStrangersNoteAtTheVillagesEdgeFromBothSides(GameTestHelper helper) {
		ProtectionSpecGameTests.protectedHall(helper, ProtectionSpecGameTests.player(helper));
		ServerPlayer stranger = ProtectionSpecGameTests.player(helper);
		BlockPos edge = new BlockPos(27, 2, 11);
		BlockPos out = new BlockPos(28, 2, 11);
		diamonds(helper, edge);
		diamonds(helper, out);
		use(helper, stranger, edge, new ItemStack(ModItems.DELIVERY_NOTE));
		helper.assertTrue(DeliveryNoteItem.data(stranger.getMainHandItem()).from().isEmpty(),
			"a stranger's note marked a chest exactly at the protected village's edge (16 from the hall)");
		use(helper, stranger, out, new ItemStack(ModItems.DELIVERY_NOTE));
		helper.assertTrue(DeliveryNoteItem.data(stranger.getMainHandItem()).from().equals(Optional.of(helper.absolutePos(out))),
			"a stranger's note didn't mark a chest one block outside the protected village (17 from the hall)");
		helper.succeed();
	}

	/**
	 * B717: a route the owner's friend marked may take from the village's chest while he's a friend, and not once the
	 * owner unfriends him (the check is at pickup, not only when the note was marked).
	 */
	//$ gametest_ticks_batch HUGE '60' '"qaGuardsNoteUnfriend"'
	@GameTest(template = HUGE, timeoutTicks = 60, batch = "qaGuardsNoteUnfriend")
	public void aFriendsRouteStopsOnceHeIsNoLongerAFriend(GameTestHelper helper) {
		ServerPlayer owner = ProtectionSpecGameTests.player(helper);
		ProtectionSpecGameTests.protectedHall(helper, owner);
		ServerPlayer friend = ProtectionSpecGameTests.player(helper);
		Friends friends = Friends.get(helper.getLevel().getServer());
		friends.add(owner.getUUID(), friend.getUUID(), "Friend");
		Leftovers.after(helper, () -> friends.remove(owner.getUUID(), friend.getUUID()));
		BlockPos chest = ProtectionSpecGameTests.HALL.offset(3, 0, 3);
		diamonds(helper, chest);
		use(helper, friend, chest, new ItemStack(ModItems.DELIVERY_NOTE));
		RouteData route = new RouteData(DeliveryNoteItem.data(friend.getMainHandItem()).from(), Optional.of(helper.absolutePos(new BlockPos(28, 2, 28))), List.of(),
			DeliveryNoteItem.data(friend.getMainHandItem()).by());
		helper.assertTrue(route.from().isPresent() && route.by().equals(Optional.of(friend.getUUID())), "setup: the friend's note: " + route);
		Villager postman = helper.spawn(EntityType.VILLAGER, new BlockPos(3, 2, 3));
		postman.setNoAi(true);
		helper.assertTrue(PostmanWork.mayTake(helper.getLevel(), postman, route), "the owner's friend's route may not take from the village's chest");
		friends.remove(owner.getUUID(), friend.getUUID());
		helper.assertTrue(!PostmanWork.mayTake(helper.getLevel(), postman, route), "a route of someone the owner unfriended still takes from his village's chest");
		helper.succeed();
	}

	// --- B718: builds reaching into a protected village --------------------------------------------------------------

	/** The test hut (5 x 4 x 5) placed with its corner at {@code at} handed to a builder at a table at {@code benchAt}; the site, or null. */
	private static BuildSite handHut(GameTestHelper helper, ServerPlayer player, List<Component> seen, BlockPos benchAt, BlockPos at) {
		ServerLevel level = helper.getLevel();
		helper.setBlock(benchAt, ModBlocks.BLUEPRINT_TABLE);
		Villager builder = helper.spawn(EntityType.VILLAGER, benchAt.south());
		builder.setNoAi(true);
		Builders.employ(level, builder, helper.absolutePos(benchAt));
		ItemStack blueprint = BlueprintItem.create(TEST_HUT, BlueprintLibrary.get(level, TEST_HUT).orElseThrow().size());
		blueprint.set(ModComponents.BLUEPRINT, BlueprintItem.data(blueprint).orElseThrow()
			.withPlacement(Optional.of(new BlueprintData.Placement(level.dimension().location(), helper.absolutePos(at), Rotation.NONE, Mirror.NONE))));
		player.setItemInHand(InteractionHand.MAIN_HAND, blueprint);
		QaStageSixGameTests.clickVillager(player, builder, seen);
		BuildSite site = Builders.activeSite(level, builder);
		if (site != null) {
			BuildSiteManager.get(level).remove(site.id());
		}
		return site;
	}

	/**
	 * B718 boundary (reach 16 round the hall at (11, 2, 11)): a stranger's hut whose west side stands on x = 27 (its column
	 * (27, 11) exactly 16 from the hall) is refused, telling him it's Owner's village, and he keeps his blueprint; the same
	 * hut one block further east (17) is taken.
	 */
	//$ gametest_ticks_batch HUGE '60' '"qaGuardsBuildEdge"'
	@GameTest(template = HUGE, timeoutTicks = 60, batch = "qaGuardsBuildEdge")
	public void aStrangersBuildTouchingTheVillagesEdgeIsRefusedOneBlockOutIsTaken(GameTestHelper helper) {
		ProtectionSpecGameTests.protectedHall(helper, ProtectionSpecGameTests.player(helper));
		QaStageFiveGameTests.Client c = QaStageSixGameTests.client(helper, GameType.SURVIVAL, QaStageSixGameTests.name("qgBuild"));
		BuildSite edge = handHut(helper, c.player(), c.seen(), new BlockPos(22, 2, 26), new BlockPos(27, 2, 11));
		helper.assertTrue(edge == null, "a stranger's build touching the protected village's edge (16 from the hall) was taken");
		helper.assertTrue(said(c).stream().anyMatch(s -> s.contains("Owner's village")), "the stranger wasn't told whose village it is: " + said(c));
		helper.assertTrue(c.player().getMainHandItem().is(ModItems.BLUEPRINT), "the refused stranger lost his blueprint: " + c.player().getMainHandItem());
		c.seen().clear();
		BuildSite out = handHut(helper, c.player(), c.seen(), new BlockPos(26, 2, 25), new BlockPos(28, 2, 11));
		helper.assertTrue(out != null, "a stranger's build one block outside the protected village (17 from the hall) was refused: " + said(c));
		helper.succeed();
	}

	/** B718: the owner's friend hands over a hut reaching 3 blocks into the protected village: it's taken. */
	//$ gametest_ticks_batch HUGE '60' '"qaGuardsBuildFriend"'
	@GameTest(template = HUGE, timeoutTicks = 60, batch = "qaGuardsBuildFriend")
	public void theOwnersFriendsBuildReachingIntoTheVillageIsTaken(GameTestHelper helper) {
		ServerPlayer owner = ProtectionSpecGameTests.player(helper);
		ProtectionSpecGameTests.protectedHall(helper, owner);
		QaStageFiveGameTests.Client c = QaStageSixGameTests.client(helper, GameType.SURVIVAL, QaStageSixGameTests.name("qgFriend"));
		Friends friends = Friends.get(helper.getLevel().getServer());
		friends.add(owner.getUUID(), c.player().getUUID(), c.player().getGameProfile().getName());
		Leftovers.after(helper, () -> friends.remove(owner.getUUID(), c.player().getUUID()));
		BuildSite site = handHut(helper, c.player(), c.seen(), new BlockPos(28, 2, 18), new BlockPos(24, 2, 9));
		helper.assertTrue(site != null, "the village owner's friend's build reaching into the protected village was refused: " + said(c));
		helper.succeed();
	}
}
