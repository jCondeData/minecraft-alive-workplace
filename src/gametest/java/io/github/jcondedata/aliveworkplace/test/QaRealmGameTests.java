package io.github.jcondedata.aliveworkplace.test;

import io.github.jcondedata.aliveworkplace.build.BuildSite;
import io.github.jcondedata.aliveworkplace.build.BuildSiteManager;
import io.github.jcondedata.aliveworkplace.colony.Colonies;
import io.github.jcondedata.aliveworkplace.colony.ColonyCharterItem;
import io.github.jcondedata.aliveworkplace.colony.Settlers;
import io.github.jcondedata.aliveworkplace.hall.Caravans;
import io.github.jcondedata.aliveworkplace.hall.VillageHallBlockEntity;
import io.github.jcondedata.aliveworkplace.hall.VillageHalls;
import io.github.jcondedata.aliveworkplace.hall.VillageNeeds;
import io.github.jcondedata.aliveworkplace.hall.VillageProtection;
import io.github.jcondedata.aliveworkplace.hall.VillageRanks;
import io.github.jcondedata.aliveworkplace.realm.RealmData;
import io.github.jcondedata.aliveworkplace.registry.ModBlocks;
import io.github.jcondedata.aliveworkplace.registry.ModComponents;
import io.github.jcondedata.aliveworkplace.registry.ModItems;
import io.github.jcondedata.aliveworkplace.registry.ModVillagers;
import io.github.jcondedata.aliveworkplace.travel.TravelNetwork;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Predicate;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.npc.VillagerProfession;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

/**
 * QA (lane-c-1024-0353) for Milestone 33's settlers and colonies, written from the ROADMAP's "Done when" (33.8-33.10)
 * and README's Settler's Wagon line, through the player's own clicks: the charter and the wagon from the main hand,
 * the off hand and on a sneak-click, the wagon on another player's protected ground, the hall broken while the
 * settlers get ready, a settler dying at the muster, the mother's hall gone while they are on the road, the journey's
 * length (3 minutes plus a tick a block) and the Colonies tab's lines rendered with their real arguments.
 */
public class QaRealmGameTests implements FabricGameTest {
	static final String AREA = "aliveworkplace_test:big_area";
	static final String BUILD = "aliveworkplace_test:build_area";
	static final String COLONY = "aliveworkplace_test:colony_area";
	static final BlockPos HALL = new BlockPos(11, 2, 11);
	static final BlockPos STOREHOUSE = new BlockPos(13, 2, 9);
	static final BlockPos CHEST = new BlockPos(14, 2, 9);

	record Village(ServerLevel level, BlockPos hall, VillageHallBlockEntity entity, ServerPlayer owner, List<Component> seen, String name) {
		GlobalPos mother() {
			return GlobalPos.of(level.dimension(), hall);
		}

		RealmData realms() {
			return RealmData.get(level.getServer());
		}

		RealmData.Order order() {
			return realms().orderOf(mother());
		}
	}

	/**
	 * A City's hall owned by a survival player whose chat and action bar are recorded, colonies on, the village shrunk
	 * to the area, the colony records empty; the settlers gather {@code musterDelay} ticks after they're ready (-1: the
	 * next morning). Everything is put back after.
	 */
	/**
	 * Forgets the villages an earlier batch's tests left within the charter's clearance of a far spot ({@code x},
	 * {@code z}), all but {@code own}: in a full build a 400-blocks-north click once landed by another test's hall and
	 * was refused as "too near a village" (lane-c-1024-0353).
	 */
	static void forgetVillagesNear(ServerLevel level, BlockPos own, int x, int z) {
		Caravans.Data data = Caravans.Data.get(level);
		for (Caravans.Village other : List.copyOf(data.villages())) {
			BlockPos h = other.hall();
			double dx = h.getX() - x, dz = h.getZ() - z;
			if (!h.equals(own) && Math.sqrt(dx * dx + dz * dz) <= io.github.jcondedata.aliveworkplace.colony.ColonyMap.CLEAR + 16) {
				data.remove(h);
			}
		}
	}

	static Village village(GameTestHelper helper, int musterDelay) {
		Leftovers.clear(helper);
		Leftovers.halls(helper);
		ServerLevel level = helper.getLevel();
		boolean enabled = Colonies.ENABLED;
		VillageRanks.Rank needed = Colonies.RANK;
		int every = VillageNeeds.CHECK_EVERY;
		int radius = VillageHalls.RADIUS;
		int wait = Settlers.WAIT_TICKS;
		int delay = Settlers.MUSTER_DELAY;
		int muster = Settlers.MUSTER_TICKS;
		int leave = Settlers.LEAVE_TICKS;
		Colonies.ENABLED = true;
		Colonies.RANK = VillageRanks.Rank.CITY;
		VillageNeeds.CHECK_EVERY = 1_000_000;
		VillageHalls.RADIUS = 8;
		Settlers.MUSTER_DELAY = musterDelay;
		RealmData.get(level.getServer()).clearColonies();
		helper.setBlock(HALL, ModBlocks.VILLAGE_HALL);
		BlockPos hall = helper.absolutePos(HALL);
		Leftovers.after(helper, () -> {
			Colonies.ENABLED = enabled;
			Colonies.RANK = needed;
			VillageNeeds.CHECK_EVERY = every;
			VillageHalls.RADIUS = radius;
			Settlers.WAIT_TICKS = wait;
			Settlers.MUSTER_DELAY = delay;
			Settlers.MUSTER_TICKS = muster;
			Settlers.LEAVE_TICKS = leave;
			RealmData.get(level.getServer()).clearColonies();
			Caravans.Data.get(level).remove(hall);
		});
		List<Component> seen = new ArrayList<>();
		ServerPlayer owner = QaStageFourGameTests.player(helper, seen, GameType.SURVIVAL);
		VillageHallBlockEntity entity = helper.getBlockEntity(HALL);
		entity.setOwner(owner.getUUID(), "Owner");
		entity.setRank(VillageRanks.Rank.CITY);
		return new Village(level, hall, entity, owner, seen, VillageHalls.name(level, hall).getString());
	}

	/** A named villager standing still at {@code pos}: of {@code job} with a workstation memory, or jobless. */
	static Villager villager(GameTestHelper helper, BlockPos pos, String name, VillagerProfession job, int jobLevel) {
		Villager v = helper.spawn(EntityType.VILLAGER, pos);
		v.setNoAi(true);
		v.setCustomName(Component.literal(name));
		if (job != VillagerProfession.NONE) {
			v.setVillagerData(v.getVillagerData().setProfession(job).setLevel(jobLevel));
			v.getBrain().setMemory(MemoryModuleType.JOB_SITE, GlobalPos.of(helper.getLevel().dimension(), helper.absolutePos(pos)));
			v.refreshBrain(helper.getLevel());
		}
		return v;
	}

	/** Two builders (Bram the lower) and the jobless Tomas and Finn: the village spares Bram and Tomas. */
	static List<Villager> plainVillage(GameTestHelper helper) {
		return List.of(villager(helper, new BlockPos(9, 2, 9), "Bram", ModVillagers.BUILDER, 1),
			villager(helper, new BlockPos(9, 2, 13), "Hale", ModVillagers.BUILDER, 3),
			villager(helper, new BlockPos(10, 2, 11), "Tomas", VillagerProfession.NONE, 1),
			villager(helper, new BlockPos(13, 2, 13), "Finn", VillagerProfession.NONE, 1));
	}

	static ChestBlockEntity storehouse(GameTestHelper helper, ItemStack... stacks) {
		helper.setBlock(STOREHOUSE, ModBlocks.STOREHOUSE);
		helper.setBlock(CHEST, Blocks.CHEST);
		ChestBlockEntity chest = helper.getBlockEntity(CHEST);
		for (int i = 0; i < stacks.length; i++) {
			chest.setItem(i, stacks[i]);
		}
		return chest;
	}

	static ItemStack[] allSupplies() {
		return new ItemStack[] {new ItemStack(Items.OAK_LOG, 64), new ItemStack(Items.OAK_PLANKS, 64), new ItemStack(Items.COBBLESTONE, 64),
			new ItemStack(Items.BREAD, 32), new ItemStack(Items.TORCH, 16), new ItemStack(Items.GLASS_PANE, 12), new ItemStack(Items.WHITE_BED),
			new ItemStack(Items.WHITE_BED), new ItemStack(Items.WHITE_BED)};
	}

	static int count(ChestBlockEntity chest, Predicate<ItemStack> test) {
		int n = 0;
		for (int i = 0; i < chest.getContainerSize(); i++) {
			if (test.test(chest.getItem(i))) {
				n += chest.getItem(i).getCount();
			}
		}
		return n;
	}

	/** The village's charter, the spot 400 blocks east, named Newbrook. */
	static ItemStack charter(Village v) {
		ItemStack stack = ColonyCharterItem.of(v.level(), v.hall());
		stack.set(ModComponents.COLONY_CHARTER, stack.get(ModComponents.COLONY_CHARTER).withSpot(v.hall().offset(400, 0, 0)));
		stack.set(DataComponents.CUSTOM_NAME, Component.literal("Newbrook"));
		return stack;
	}

	/**
	 * A right-click on the top of {@code rel} the way the client sends it (main hand, then off hand; the block, then the
	 * item's own use), with the player left where they stand.
	 */
	static InteractionResult pressHere(GameTestHelper helper, ServerPlayer player, BlockPos rel) {
		ServerLevel level = helper.getLevel();
		BlockPos abs = helper.absolutePos(rel);
		BlockHitResult hit = new BlockHitResult(Vec3.atCenterOf(abs).relative(Direction.UP, 0.5), Direction.UP, abs, false);
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

	static ItemStack held(ServerPlayer player, Item item) {
		for (InteractionHand hand : InteractionHand.values()) {
			if (player.getItemInHand(hand).is(item)) {
				return player.getItemInHand(hand);
			}
		}
		return ItemStack.EMPTY;
	}

	static List<Villager> villagers(GameTestHelper helper, int reach) {
		return helper.getLevel().getEntitiesOfClass(Villager.class, new AABB(helper.absolutePos(BlockPos.ZERO)).inflate(reach), Villager::isAlive);
	}

	// --- The charter at the hall and on the ground --------------------------------------------------------------------

	/**
	 * The control for the off-hand and sneak cases (QaRealmBugTests): the owner's right-click on the hall with the
	 * charter in the main hand sends Bram and Tomas, uses the charter up, and the chat line names them and Newbrook,
	 * fully rendered.
	 */
	//$ gametest_ticks_batch AREA '100' '"qaRealmMainHand"'
	@GameTest(template = AREA, timeoutTicks = 100, batch = "qaRealmMainHand")
	public void theCharterInTheMainHandSendsTheSettlers(GameTestHelper helper) {
		Village v = village(helper, -1);
		plainVillage(helper);
		storehouse(helper, allSupplies());
		v.owner().setItemInHand(InteractionHand.MAIN_HAND, charter(v));
		QaStageFourGameTests.press(helper, v.owner(), HALL);
		RealmData.Order order = v.order();
		helper.assertTrue(order != null && order.state().equals(RealmData.GATHERING) && order.volunteers().size() == 2, "no order: " + order);
		helper.assertTrue(held(v.owner(), ModItems.COLONY_CHARTER).isEmpty(), "the charter stayed in hand");
		Component sent = QaStageFourGameTests.find(v.seen(), "message.aliveworkplace.colony.sent");
		helper.assertTrue(sent != null, "no 'will set out' line: saw " + QaStageFourGameTests.keys(v.seen()));
		QaStageFourGameTests.assertRendered(helper, sent, "Bram", "Tomas", "Newbrook");
		helper.succeed();
	}

	/**
	 * 33.8: "right-click the ground with the charter where you stand". From the off hand (main hand empty) and on a
	 * sneak-click alike, standing 300 blocks west of the hall, the spot is where the player stands and the action bar
	 * says so; by the hall it's refused as too near and the spot doesn't move.
	 */
	//$ gametest_ticks_batch AREA '100' '"qaRealmGround"'
	@GameTest(template = AREA, timeoutTicks = 100, batch = "qaRealmGround")
	public void theCharterChoosesTheSpotFromEitherHand(GameTestHelper helper) {
		Village v = village(helper, -1);
		ServerPlayer p = v.owner();
		ItemStack charter = ColonyCharterItem.of(v.level(), v.hall());
		p.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
		p.setItemInHand(InteractionHand.OFF_HAND, charter);
		BlockPos ground = new BlockPos(5, 1, 5);

		p.setPos(v.hall().getX() + 2.5, v.hall().getY(), v.hall().getZ() + 2.5);
		pressHere(helper, p, ground);
		Component near = QaStageFourGameTests.find(v.seen(), "message.aliveworkplace.colony.too_near");
		helper.assertTrue(near != null, "by the hall, from the off hand: saw " + QaStageFourGameTests.keys(v.seen()));
		QaStageFourGameTests.assertRendered(helper, near, "256");
		helper.assertTrue(p.getOffhandItem().get(ModComponents.COLONY_CHARTER).spot().isEmpty(), "a spot by the hall was kept");

		p.setPos(v.hall().getX() - 300 + 0.5, v.hall().getY() + 3, v.hall().getZ() + 0.5);
		forgetVillagesNear(v.level(), v.hall(), v.hall().getX() - 300, v.hall().getZ());
		pressHere(helper, p, ground);
		Component chosen = QaStageFourGameTests.find(v.seen(), "message.aliveworkplace.colony.chosen");
		helper.assertTrue(chosen != null, "300 blocks west, from the off hand: saw " + QaStageFourGameTests.keys(v.seen()));
		QaStageFourGameTests.assertRendered(helper, chosen, "300 blocks west", v.name());
		BlockPos spot = p.getOffhandItem().get(ModComponents.COLONY_CHARTER).spot().orElse(null);
		helper.assertTrue(new BlockPos(v.hall().getX() - 300, v.hall().getY() + 3, v.hall().getZ()).equals(spot), "the off hand's spot: " + spot);

		// sneaking, main hand, 400 blocks north: the item's click still chooses
		p.setItemInHand(InteractionHand.OFF_HAND, ItemStack.EMPTY);
		p.setItemInHand(InteractionHand.MAIN_HAND, charter.copy());
		p.setShiftKeyDown(true);
		p.setPos(v.hall().getX() + 0.5, v.hall().getY() + 1, v.hall().getZ() - 400 + 0.5);
		forgetVillagesNear(v.level(), v.hall(), v.hall().getX(), v.hall().getZ() - 400);
		v.seen().clear();
		pressHere(helper, p, ground);
		p.setShiftKeyDown(false);
		BlockPos north = p.getMainHandItem().get(ModComponents.COLONY_CHARTER).spot().orElse(null);
		helper.assertTrue(new BlockPos(v.hall().getX(), v.hall().getY() + 1, v.hall().getZ() - 400).equals(north),
			"a sneak-click 400 blocks north: " + north + " saw " + QaStageFourGameTests.keys(v.seen()));
		helper.assertTrue(v.order() == null, "choosing a spot sent the settlers");
		helper.succeed();
	}

	/**
	 * A stranger who picked up the owner's charter can choose nothing with it at the hall: right-clicking the hall
	 * sends no settlers, says why, and the charter stays. The owner's friend list doesn't include them.
	 */
	//$ gametest_ticks_batch AREA '100' '"qaRealmStranger"'
	@GameTest(template = AREA, timeoutTicks = 100, batch = "qaRealmStranger")
	public void aStrangerWithTheCharterSendsNobody(GameTestHelper helper) {
		Village v = village(helper, -1);
		plainVillage(helper);
		ChestBlockEntity chest = storehouse(helper, allSupplies());
		List<Component> seen = new ArrayList<>();
		ServerPlayer stranger = QaStageFourGameTests.player(helper, seen, GameType.SURVIVAL);
		stranger.setItemInHand(InteractionHand.MAIN_HAND, charter(v));
		QaStageFourGameTests.press(helper, stranger, HALL);
		helper.assertTrue(v.order() == null, "a stranger sent the settlers: " + v.order());
		helper.assertTrue(stranger.getMainHandItem().is(ModItems.COLONY_CHARTER), "the stranger's charter went: " + stranger.getMainHandItem());
		helper.assertTrue(count(chest, s -> s.is(Items.OAK_LOG)) == 64, "supplies left the Storehouse");
		Component why = QaStageFourGameTests.find(seen, "message.aliveworkplace.colony.send_not_yours");
		helper.assertTrue(why != null, "no reason given: saw " + QaStageFourGameTests.keys(seen));
		QaStageFourGameTests.assertRendered(helper, why, "Owner", v.name());
		helper.succeed();
	}

	// --- The Settler's Wagon --------------------------------------------------------------------------------------------

	/**
	 * README's Settler's Wagon ("right-click open ground: two settlers make camp there"), from the OFF hand with the
	 * main hand empty: the camp line, two settlers, the wagon used up.
	 */
	//$ gametest_batch BUILD '"qaRealmWagonOff"'
	@GameTest(template = BUILD, batch = "qaRealmWagonOff")
	public void theWagonInTheOffHandMakesCamp(GameTestHelper helper) {
		Leftovers.clear(helper);
		Leftovers.halls(helper);
		List<Component> seen = new ArrayList<>();
		ServerPlayer player = QaStageFourGameTests.player(helper, seen, GameType.SURVIVAL);
		player.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
		player.setItemInHand(InteractionHand.OFF_HAND, new ItemStack(ModItems.SETTLERS_WAGON));
		player.setYRot(0f);
		QaStageFourGameTests.press(helper, player, new BlockPos(11, 1, 6));
		Component made = QaStageFourGameTests.find(seen, "message.aliveworkplace.camp.made");
		helper.assertTrue(made != null, "no camp from the off hand: saw " + QaStageFourGameTests.keys(seen));
		QaStageFourGameTests.assertRendered(helper, made);
		helper.assertTrue(villagers(helper, 24).size() == 2, "settlers: " + villagers(helper, 24).size());
		helper.assertTrue(player.getOffhandItem().isEmpty(), "the off hand's wagon wasn't used: " + player.getOffhandItem());
		helper.succeed();
	}

	/** The same on a sneak-click with the wagon in the main hand: sneaking only skips the block's own use. */
	//$ gametest_batch BUILD '"qaRealmWagonSneak"'
	@GameTest(template = BUILD, batch = "qaRealmWagonSneak")
	public void aSneakClickWithTheWagonMakesCamp(GameTestHelper helper) {
		Leftovers.clear(helper);
		Leftovers.halls(helper);
		List<Component> seen = new ArrayList<>();
		ServerPlayer player = QaStageFourGameTests.player(helper, seen, GameType.SURVIVAL);
		player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(ModItems.SETTLERS_WAGON));
		player.setYRot(0f);
		player.setShiftKeyDown(true);
		QaStageFourGameTests.press(helper, player, new BlockPos(11, 1, 6));
		player.setShiftKeyDown(false);
		helper.assertTrue(QaStageFourGameTests.saw(seen, "message.aliveworkplace.camp.made"), "no camp on a sneak-click: saw " + QaStageFourGameTests.keys(seen));
		helper.assertTrue(villagers(helper, 24).size() == 2 && player.getMainHandItem().isEmpty(), "settlers " + villagers(helper, 24).size() + ", hand " + player.getMainHandItem());
		helper.succeed();
	}

	/**
	 * A wagon on another player's protected village ground makes no camp: the protection line names the village and
	 * its owner, no settler appears and the wagon stays (main hand and off hand alike). The owner's own wagon there does.
	 */
	//$ gametest_batch BUILD '"qaRealmWagonProtected"'
	@GameTest(template = BUILD, batch = "qaRealmWagonProtected")
	public void aWagonOnAnotherPlayersGroundMakesNoCamp(GameTestHelper helper) {
		Leftovers.clear(helper);
		Leftovers.halls(helper);
		int radius = VillageHalls.RADIUS;
		boolean enabled = VillageProtection.ENABLED;
		VillageHalls.RADIUS = 16;
		VillageProtection.ENABLED = true;
		List<Component> ownerSeen = new ArrayList<>();
		ServerPlayer owner = QaStageFourGameTests.player(helper, ownerSeen, GameType.SURVIVAL);
		helper.setBlock(new BlockPos(1, 2, 1), ModBlocks.VILLAGE_HALL);
		VillageHallBlockEntity hall = helper.getBlockEntity(new BlockPos(1, 2, 1));
		hall.setOwner(owner.getUUID(), "Owner");
		hall.setProtected(true);
		Leftovers.after(helper, () -> {
			hall.setProtected(false);
			VillageHalls.RADIUS = radius;
			VillageProtection.ENABLED = enabled;
		});
		List<Component> seen = new ArrayList<>();
		ServerPlayer stranger = QaStageFourGameTests.player(helper, seen, GameType.SURVIVAL);
		stranger.setYRot(0f);
		for (InteractionHand hand : InteractionHand.values()) {
			stranger.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
			stranger.setItemInHand(InteractionHand.OFF_HAND, ItemStack.EMPTY);
			stranger.setItemInHand(hand, new ItemStack(ModItems.SETTLERS_WAGON));
			seen.clear();
			QaStageFourGameTests.press(helper, stranger, new BlockPos(11, 1, 6));
			Component denied = QaStageFourGameTests.find(seen, "message.aliveworkplace.protection.denied");
			helper.assertTrue(denied != null && !QaStageFourGameTests.saw(seen, "message.aliveworkplace.camp.made"), hand + ": saw " + QaStageFourGameTests.keys(seen));
			QaStageFourGameTests.assertRendered(helper, denied, "Owner");
			helper.assertTrue(stranger.getItemInHand(hand).is(ModItems.SETTLERS_WAGON), hand + ": the refused wagon went");
			helper.assertTrue(villagers(helper, 24).isEmpty(), hand + ": settlers appeared on protected ground: " + villagers(helper, 24).size());
		}
		owner.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(ModItems.SETTLERS_WAGON));
		owner.setYRot(0f);
		QaStageFourGameTests.press(helper, owner, new BlockPos(11, 1, 6));
		helper.assertTrue(QaStageFourGameTests.saw(ownerSeen, "message.aliveworkplace.camp.made"), "the owner's own wagon: saw " + QaStageFourGameTests.keys(ownerSeen));
		helper.succeed();
	}

	// --- Mid-muster and mid-journey ---------------------------------------------------------------------------------

	/**
	 * The hall broken by its owner while the settlers get ready: nobody leaves and nothing is lost. The order is gone,
	 * every settler is still in the village, the 64 logs are back (in the Storehouse or dropped at the hall) and the
	 * charter, its spot and name kept, drops at the hall.
	 */
	//$ gametest_ticks_batch AREA '200' '"qaRealmHallBroken"'
	@GameTest(template = AREA, timeoutTicks = 200, batch = "qaRealmHallBroken")
	public void theHallBrokenWhileTheyGetReadyPutsEverythingBack(GameTestHelper helper) {
		Village v = village(helper, -1);
		List<Villager> all = plainVillage(helper);
		ChestBlockEntity chest = storehouse(helper, allSupplies());
		v.owner().setItemInHand(InteractionHand.MAIN_HAND, charter(v));
		QaStageFourGameTests.press(helper, v.owner(), HALL);
		helper.assertTrue(v.order() != null && count(chest, s -> s.is(Items.OAK_LOG)) == 0, "not sent, or the logs not taken: " + v.order());
		v.owner().setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
		v.owner().gameMode.destroyBlock(v.hall());
		helper.assertTrue(!v.level().getBlockState(v.hall()).is(ModBlocks.VILLAGE_HALL), "the owner couldn't break the hall");
		helper.succeedWhen(() -> {
			helper.assertTrue(v.order() == null, "the order outlived its hall: " + v.order());
			for (Villager stays : all) {
				helper.assertTrue(stays.isAlive() && v.level().getEntity(stays.getUUID()) == stays, stays.getDisplayName().getString() + " is gone");
			}
			List<ItemEntity> drops = v.level().getEntitiesOfClass(ItemEntity.class, new AABB(v.hall()).inflate(4));
			int logs = count(chest, s -> s.is(Items.OAK_LOG)) + drops.stream().filter(e -> e.getItem().is(Items.OAK_LOG)).mapToInt(e -> e.getItem().getCount()).sum();
			int beds = count(chest, s -> s.is(Items.WHITE_BED)) + drops.stream().filter(e -> e.getItem().is(Items.WHITE_BED)).mapToInt(e -> e.getItem().getCount()).sum();
			helper.assertTrue(logs == 64 && beds == 3, "supplies lost: logs " + logs + ", beds " + beds);
			ItemStack back = drops.stream().map(ItemEntity::getItem).filter(s -> s.is(ModItems.COLONY_CHARTER)).findFirst().orElse(ItemStack.EMPTY);
			ColonyCharterItem.Charter c = back.get(ModComponents.COLONY_CHARTER);
			helper.assertTrue(c != null && c.spot().orElseThrow().equals(v.hall().offset(400, 0, 0)) && back.getHoverName().getString().equals("Newbrook"),
				"the charter didn't come back whole: " + back);
		});
	}

	/**
	 * A volunteer killed while the settlers get ready: the other still sets out, alone, and the road keeps only him (a
	 * dead villager is never carried to the colony). The tab's line counts one settler.
	 */
	//$ gametest_ticks_batch AREA '400' '"qaRealmSettlerDies"'
	@GameTest(template = AREA, timeoutTicks = 400, batch = "qaRealmSettlerDies")
	public void aSettlerKilledBeforeTheyLeaveIsLeftBehind(GameTestHelper helper) {
		Village v = village(helper, 0);
		Settlers.MUSTER_TICKS = 20;
		Settlers.LEAVE_TICKS = 40;
		List<Villager> all = plainVillage(helper);
		Villager bram = all.get(0);
		Villager tomas = all.get(2);
		storehouse(helper, allSupplies());
		v.owner().setItemInHand(InteractionHand.MAIN_HAND, charter(v));
		QaStageFourGameTests.press(helper, v.owner(), HALL);
		RealmData.Order sent = v.order();
		helper.assertTrue(sent != null && sent.volunteers().equals(List.of(bram.getUUID(), tomas.getUUID())), "Bram and Tomas should volunteer: " + sent);
		tomas.kill();
		helper.succeedWhen(() -> {
			RealmData.Order order = v.order();
			helper.assertTrue(order != null && order.state().equals(RealmData.ON_ROAD), "not on the road: " + (order == null ? null : order.state()));
			helper.assertTrue(order.settlers().size() == 1 && order.settlers().get(0).getUUID("UUID").equals(bram.getUUID()),
				"the road should carry Bram alone: " + order.settlers().size());
			helper.assertTrue(order.arrives() > v.level().getGameTime(), "no journey ahead");
		});
	}

	/**
	 * The journey takes 3 minutes plus a tick a block (33.9): 400 blocks east is 3,600 + 400 ticks, 612 blocks
	 * north-east 3,600 + 612. The Colonies tab's line for an order on the road with 2 minutes left says so, and a minute
	 * or less says "a minute"; every line is rendered, no key and no %s.
	 */
	//$ gametest_ticks_batch AREA '100' '"qaRealmJourney"'
	@GameTest(template = AREA, timeoutTicks = 100, batch = "qaRealmJourney")
	public void theJourneyIsThreeMinutesAndATickABlock(GameTestHelper helper) {
		Village v = village(helper, -1);
		long now = v.level().getGameTime();
		RealmData.Order east = new RealmData.Order(v.mother(), v.hall().offset(400, 0, 0), Optional.of(Component.literal("Newbrook")), RealmData.ON_ROAD,
			now - 10, now + 2400, Colonies.COST * 100, now - 100, List.of(), Optional.empty(), Map.of(), List.of());
		helper.assertTrue(Settlers.journey(east) == 3600 + 400, "400 blocks: " + Settlers.journey(east));
		RealmData.Order diagonal = east.withState(RealmData.ON_ROAD, now - 10, now + 1200);
		RealmData.Order ne = new RealmData.Order(v.mother(), v.hall().offset(433, 0, -433), Optional.empty(), RealmData.ON_ROAD,
			now - 10, now + 1200, Colonies.COST * 100, now - 100, List.of(), Optional.empty(), Map.of(), List.of());
		helper.assertTrue(Settlers.journey(ne) == 3600 + 612, "612 blocks: " + Settlers.journey(ne));
		String two = Settlers.status(v.level(), east).getString();
		helper.assertTrue(two.equals("On the road to Newbrook, there in 2 minutes"), "2 minutes left: " + two);
		String one = Settlers.status(v.level(), diagonal).getString();
		helper.assertTrue(one.equals("On the road to Newbrook, there in a minute"), "a minute left: " + one);
		String unnamed = Settlers.status(v.level(), ne).getString();
		helper.assertFalse(unnamed.contains("%") || unnamed.contains("screen.aliveworkplace"), "an unnamed colony's line: " + unnamed);
		for (RealmData.Order o : List.of(east, ne, east.withState(RealmData.GATHERING, 0, 0), east.withState(RealmData.MUSTER, now, 0),
			east.withState(RealmData.ARRIVING, 0, now), east.withState(RealmData.RETURNING, 0, 0))) {
			String line = Settlers.status(v.level(), o).getString();
			helper.assertFalse(line.isBlank() || line.contains("%") || line.contains("screen.aliveworkplace"), o.state() + ": " + line);
		}
		helper.succeed();
	}

	/**
	 * The mother's hall broken while the settlers are on the road (travel time 0, room at the spot): they aren't lost.
	 * The colony is still founded at the spot, its hall named Newbrook, and Tomas and Dara step out of the camp.
	 */
	//$ gametest_ticks_batch COLONY '200' '"qaRealmMotherGone"'
	@GameTest(template = COLONY, timeoutTicks = 200, batch = "qaRealmMotherGone")
	public void theSettlersStillArriveWhenTheMothersHallIsGone(GameTestHelper helper) {
		Leftovers.clear(helper);
		Leftovers.halls(helper);
		Leftovers.finished(helper);
		ServerLevel level = helper.getLevel();
		boolean wasEnabled = Colonies.ENABLED;
		int every = VillageNeeds.CHECK_EVERY;
		int radius = VillageHalls.RADIUS;
		Colonies.ENABLED = true;
		VillageNeeds.CHECK_EVERY = 1_000_000;
		VillageHalls.RADIUS = 8;
		RealmData realms = RealmData.get(level.getServer());
		realms.clearColonies();
		// the barrier roof off: the settlers find the ground by the height map
		AABB box = helper.getBounds();
		for (BlockPos p : BlockPos.betweenClosed(BlockPos.containing(box.minX, box.maxY - 3, box.minZ), BlockPos.containing(box.maxX, box.maxY + 3, box.maxZ))) {
			if (level.getBlockState(p).is(Blocks.BARRIER)) {
				level.setBlock(p, Blocks.AIR.defaultBlockState(), 2);
			}
		}
		BlockPos at = new BlockPos(3, 2, 22);
		helper.setBlock(at, ModBlocks.VILLAGE_HALL);
		BlockPos hall = helper.absolutePos(at);
		ServerPlayer owner = ProtectionSpecGameTests.player(helper);
		VillageHallBlockEntity entity = helper.getBlockEntity(at);
		entity.setCustomName(Component.literal("Thornholm"));
		entity.setOwner(owner.getUUID(), "Owner");
		GlobalPos mother = GlobalPos.of(level.dimension(), hall);
		Leftovers.after(helper, () -> {
			Colonies.ENABLED = wasEnabled;
			VillageNeeds.CHECK_EVERY = every;
			VillageHalls.RADIUS = radius;
			Caravans.Data caravans = Caravans.Data.get(level);
			for (RealmData.Founded f : realms.founded()) {
				caravans.remove(f.hall().pos());
				TravelNetwork network = TravelNetwork.get(level.getServer());
				for (BlockPos p : BlockPos.betweenClosed(f.hall().pos().offset(-8, -2, -8), f.hall().pos().offset(8, 2, 8))) {
					network.remove(GlobalPos.of(level.dimension(), p.immutable()));
				}
			}
			caravans.remove(hall);
			caravans.clearRoad(hall);
			realms.clearColonies();
			BuildSiteManager sites = BuildSiteManager.get(level);
			for (BuildSite site : List.copyOf(sites.all())) {
				if (box.inflate(16).contains(site.placement().origin().getCenter())) {
					sites.remove(site.id());
				}
			}
			for (Villager x : level.getEntitiesOfClass(Villager.class, box.inflate(16))) {
				x.discard();
			}
		});
		CompoundTag tomas = saved(helper, "Tomas", ModVillagers.BUILDER, 2);
		CompoundTag dara = saved(helper, "Dara", VillagerProfession.FARMER, 1);
		Map<Item, Integer> supplies = new LinkedHashMap<>();
		supplies.put(Items.OAK_LOG, 64);
		supplies.put(Items.BREAD, 32);
		helper.runAfterDelay(2, () -> {
			helper.setBlock(at, Blocks.AIR); // the mother's hall is broken while they're on the road
			long now = level.getGameTime();
			realms.putOrder(new RealmData.Order(mother, helper.absolutePos(new BlockPos(22, 2, 22)), Optional.of(Component.literal("Newbrook")), RealmData.ON_ROAD,
				now - 100, now, Colonies.COST * 100, now - 200, List.of(), Optional.of(tomas.getUUID("UUID")), supplies, List.of(tomas, dara)));
		});
		helper.succeedWhen(() -> {
			helper.assertTrue(realms.orders().isEmpty(), "the order is stuck on the road: " + realms.orders());
			List<Villager> out = level.getEntitiesOfClass(Villager.class, box.inflate(8), Villager::isAlive);
			List<String> names = out.stream().map(x -> x.getDisplayName().getString()).toList();
			helper.assertTrue(names.contains("Tomas") && names.contains("Dara"), "the settlers were lost: " + names);
			helper.assertTrue(realms.foundedBy(mother).size() == 1, "no colony founded: " + realms.founded());
			BlockPos colony = realms.foundedBy(mother).get(0).hall().pos();
			helper.assertTrue(VillageHalls.name(level, colony).getString().equals("Newbrook"), "the colony's name: " + VillageHalls.name(level, colony).getString());
		});
	}

	/** A settler as the road keeps them: made, saved and taken out of the world. */
	static CompoundTag saved(GameTestHelper helper, String name, VillagerProfession job, int jobLevel) {
		Villager v = helper.spawn(EntityType.VILLAGER, new BlockPos(2, 2, 2));
		v.setCustomName(Component.literal(name));
		v.setVillagerData(v.getVillagerData().setProfession(job).setLevel(jobLevel));
		CompoundTag tag = new CompoundTag();
		v.saveWithoutId(tag);
		v.discard();
		return tag;
	}
}
