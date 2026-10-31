package io.github.jcondedata.aliveworkplace.test;

import io.github.jcondedata.aliveworkplace.build.BuildSite;
import io.github.jcondedata.aliveworkplace.build.BuildSiteManager;
import io.github.jcondedata.aliveworkplace.build.Friends;
import io.github.jcondedata.aliveworkplace.colony.Colonies;
import io.github.jcondedata.aliveworkplace.colony.ColonyArrival;
import io.github.jcondedata.aliveworkplace.colony.ColonyCharterItem;
import io.github.jcondedata.aliveworkplace.colony.ColonyMap;
import io.github.jcondedata.aliveworkplace.colony.Settlers;
import io.github.jcondedata.aliveworkplace.hall.Caravans;
import io.github.jcondedata.aliveworkplace.hall.Chronicle;
import io.github.jcondedata.aliveworkplace.hall.VillageHallBlockEntity;
import io.github.jcondedata.aliveworkplace.hall.VillageHallScreen;
import io.github.jcondedata.aliveworkplace.hall.VillageHalls;
import io.github.jcondedata.aliveworkplace.hall.VillageNeeds;
import io.github.jcondedata.aliveworkplace.hall.VillageRanks;
import io.github.jcondedata.aliveworkplace.legend.LegendData;
import io.github.jcondedata.aliveworkplace.people.Couples;
import io.github.jcondedata.aliveworkplace.realm.RealmData;
import io.github.jcondedata.aliveworkplace.registry.ModAttachments;
import io.github.jcondedata.aliveworkplace.registry.ModBlocks;
import io.github.jcondedata.aliveworkplace.registry.ModComponents;
import io.github.jcondedata.aliveworkplace.registry.ModItems;
import io.github.jcondedata.aliveworkplace.registry.ModVillagers;
import io.github.jcondedata.aliveworkplace.trade.TradePage;
import io.github.jcondedata.aliveworkplace.travel.TravelNetwork;
import io.github.jcondedata.aliveworkplace.work.ChoiceMenu;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
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
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.npc.VillagerProfession;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

/**
 * QA (lane-c-1025-0653): adversarial tests written from ROADMAP 33.8 (the Colony Charter), 33.9 (the settlers set out)
 * and 33.10 (a colony is born), aimed at what {@link ColonyCharterGameTests}, {@link ColonySettlersGameTests},
 * {@link ColonyArrivalGameTests} and {@link QaRealmGameTests} leave open: the rank checked again at the hall (one rank
 * below refused, with its sentence, for colonyRank City and Town), the wait's last day, whole emeralds from the
 * treasury, a friend of the owner, the ring's numbers on the diagonals, "in the same dimension", the only farmer's
 * husband and a Legend staying home, two of a trade not being three, the muster interrupted (every settler killed, the
 * hall broken), the call-off at the muster, and the sentences the owner reads (sent, bell, called off, arrived, no room).
 * Colonies are off in GameTests, so every test turns them on, alone in its batch, and puts everything back.
 */
public class QaColonyCharterGameTests implements FabricGameTest {
	private static final String SMALL = "aliveworkplace_test:build_area";
	private static final String BIG = "aliveworkplace_test:big_area";
	private static final String COLONY = "aliveworkplace_test:colony_area";
	private static final String HUGE = "aliveworkplace_test:huge_area";
	private static final BlockPos CHARTER_HALL = new BlockPos(3, 2, 3);
	private static final BlockPos HALL = new BlockPos(11, 2, 11);
	private static final BlockPos STOREHOUSE = new BlockPos(13, 2, 9);
	private static final BlockPos CHEST = new BlockPos(14, 2, 9);
	private static final ResourceLocation OLD_SAGE = ResourceLocation.fromNamespaceAndPath("aliveworkplace", "old_sage");

	private record Village(ServerLevel level, BlockPos hall, VillageHallBlockEntity entity, ServerPlayer player, String name, List<Component> heard) {
		GlobalPos mother() {
			return GlobalPos.of(level.dimension(), hall);
		}

		RealmData realms() {
			return RealmData.get(level.getServer());
		}

		RealmData.Order order() {
			return realms().orderOf(mother());
		}

		boolean heard(String line) {
			return heard.stream().anyMatch(c -> c.getString().equals(line));
		}

		List<String> lines() {
			return heard.stream().map(Component::getString).toList();
		}
	}

	/** Every switch and number the colony tests touch, set to the spec's values, and put back when the test ends. */
	private static void colonies(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		boolean enabled = Colonies.ENABLED;
		VillageRanks.Rank needed = Colonies.RANK;
		int cooldown = Colonies.COOLDOWN_DAYS;
		int cap = Colonies.PER_VILLAGE;
		int every = VillageNeeds.CHECK_EVERY;
		int radius = VillageHalls.RADIUS;
		int wait = Settlers.WAIT_TICKS;
		int delay = Settlers.MUSTER_DELAY;
		int muster = Settlers.MUSTER_TICKS;
		int leave = Settlers.LEAVE_TICKS;
		int room = ColonyArrival.ROOM;
		Colonies.ENABLED = true;
		Colonies.RANK = VillageRanks.Rank.CITY;
		Colonies.COOLDOWN_DAYS = 7;
		Colonies.PER_VILLAGE = 3;
		VillageNeeds.CHECK_EVERY = 1_000_000;
		RealmData.get(level.getServer()).clearColonies();
		Leftovers.after(helper, () -> {
			Colonies.ENABLED = enabled;
			Colonies.RANK = needed;
			Colonies.COOLDOWN_DAYS = cooldown;
			Colonies.PER_VILLAGE = cap;
			VillageNeeds.CHECK_EVERY = every;
			VillageHalls.RADIUS = radius;
			Settlers.WAIT_TICKS = wait;
			Settlers.MUSTER_DELAY = delay;
			Settlers.MUSTER_TICKS = muster;
			Settlers.LEAVE_TICKS = leave;
			ColonyArrival.ROOM = room;
			RealmData.get(level.getServer()).clearColonies();
		});
	}

	/** A hall of {@code rank} at {@code at}, owned by {@code player} (a listener who keeps every chat line, or a mock). */
	private static Village village(GameTestHelper helper, BlockPos at, VillageRanks.Rank rank, int treasury, boolean listening) {
		Leftovers.clear(helper);
		Leftovers.halls(helper);
		colonies(helper);
		ServerLevel level = helper.getLevel();
		helper.setBlock(at, ModBlocks.VILLAGE_HALL);
		BlockPos hall = helper.absolutePos(at);
		Caravans.Data data = Caravans.Data.get(level);
		for (Caravans.Village left : data.villages()) {
			if (ColonyMap.distance(hall, left.hall().getX(), left.hall().getZ()) <= 2 * ColonyMap.SPAN) {
				data.remove(left.hall());
			}
		}
		List<BlockPos> before = data.villages().stream().map(Caravans.Village::hall).toList();
		Leftovers.after(helper, () -> {
			for (Caravans.Village v : data.villages()) {
				if (!before.contains(v.hall())) {
					data.remove(v.hall());
				}
			}
			data.remove(hall);
		});
		List<Component> heard = new ArrayList<>();
		ServerPlayer player = listening ? RealmGameTests.listener(helper, heard, "colony-owner") : ProtectionSpecGameTests.player(helper);
		player.setPos(hall.getX() + 0.5, hall.getY(), hall.getZ() + 3.5);
		VillageHallBlockEntity entity = helper.getBlockEntity(at);
		entity.setOwner(player.getUUID(), "Owner");
		entity.setRank(rank);
		entity.setTreasury(treasury);
		return new Village(level, hall, entity, player, VillageHalls.name(level, hall).getString(), heard);
	}

	/** A City's village in big_area, its census shrunk to 8 blocks round the hall. */
	private static Village settlersVillage(GameTestHelper helper, int musterDelay, boolean listening) {
		Village v = village(helper, HALL, VillageRanks.Rank.CITY, 0, listening);
		VillageHalls.RADIUS = 8;
		Settlers.MUSTER_DELAY = musterDelay;
		return v;
	}

	private static Villager villager(GameTestHelper helper, BlockPos pos, String name, VillagerProfession job, int jobLevel) {
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

	private static void marry(Villager a, Villager b) {
		ModAttachments.PARTNER.set(a, new Couples.Partner(b.getUUID(), b.getDisplayName(), 1, true));
		ModAttachments.PARTNER.set(b, new Couples.Partner(a.getUUID(), a.getDisplayName(), 1, true));
	}

	/** Two builders (Bram the lower) and the jobless Tomas and Finn, all by the hall. */
	private static List<Villager> plainVillage(GameTestHelper helper) {
		return List.of(villager(helper, new BlockPos(9, 2, 9), "Bram", ModVillagers.BUILDER, 1),
			villager(helper, new BlockPos(9, 2, 13), "Hale", ModVillagers.BUILDER, 3),
			villager(helper, new BlockPos(10, 2, 11), "Tomas", VillagerProfession.NONE, 1),
			villager(helper, new BlockPos(13, 2, 13), "Finn", VillagerProfession.NONE, 1));
	}

	/** Bram (the lower builder) and the jobless Tomas six blocks either side of the hall, Hale the other builder: a muster that waits. */
	private static List<Villager> farVillage(GameTestHelper helper) {
		return List.of(villager(helper, new BlockPos(5, 2, 11), "Bram", ModVillagers.BUILDER, 1),
			villager(helper, new BlockPos(17, 2, 11), "Tomas", VillagerProfession.NONE, 1),
			villager(helper, new BlockPos(11, 2, 5), "Hale", ModVillagers.BUILDER, 3));
	}

	private static ChestBlockEntity storehouse(GameTestHelper helper, ItemStack... stacks) {
		helper.setBlock(STOREHOUSE, ModBlocks.STOREHOUSE);
		helper.setBlock(CHEST, Blocks.CHEST);
		ChestBlockEntity chest = helper.getBlockEntity(CHEST);
		for (int i = 0; i < stacks.length; i++) {
			chest.setItem(i, stacks[i]);
		}
		return chest;
	}

	private static ItemStack[] allSupplies() {
		return new ItemStack[] {new ItemStack(Items.OAK_LOG, 64), new ItemStack(Items.SPRUCE_PLANKS, 64), new ItemStack(Items.COBBLESTONE, 64),
			new ItemStack(Items.BREAD, 32), new ItemStack(Items.TORCH, 16), new ItemStack(Items.GLASS_PANE, 12), new ItemStack(Items.RED_BED),
			new ItemStack(Items.RED_BED), new ItemStack(Items.WHITE_BED)};
	}

	private static int count(ChestBlockEntity chest, Predicate<ItemStack> test) {
		int n = 0;
		for (int i = 0; i < chest.getContainerSize(); i++) {
			if (test.test(chest.getItem(i))) {
				n += chest.getItem(i).getCount();
			}
		}
		return n;
	}

	/** What lies dropped round the hall, matching {@code test}. */
	private static int dropped(Village v, Predicate<ItemStack> test) {
		int n = 0;
		for (ItemEntity e : v.level().getEntitiesOfClass(ItemEntity.class, new AABB(v.hall()).inflate(4))) {
			if (e.isAlive() && test.test(e.getItem())) {
				n += e.getItem().getCount();
			}
		}
		return n;
	}

	/** The village's charter, its spot 400 blocks east, named {@code name} (null: unnamed). */
	private static ItemStack charter(Village v, String name) {
		ItemStack stack = ColonyCharterItem.of(v.level(), v.hall());
		stack.set(ModComponents.COLONY_CHARTER, stack.get(ModComponents.COLONY_CHARTER).withSpot(v.hall().offset(400, 0, 0)));
		if (name != null) {
			stack.set(DataComponents.CUSTOM_NAME, Component.literal(name));
		}
		return stack;
	}

	/** {@code player} right-clicks the hall with {@code stack} in the main hand; what's in the hand after. */
	private static ItemStack clickHall(Village v, ServerPlayer player, ItemStack stack) {
		player.setItemInHand(InteractionHand.MAIN_HAND, stack);
		ItemStack held = player.getItemInHand(InteractionHand.MAIN_HAND);
		player.gameMode.useItemOn(player, v.level(), held, InteractionHand.MAIN_HAND, new BlockHitResult(Vec3.atCenterOf(v.hall()), Direction.UP, v.hall(), false));
		return player.getItemInHand(InteractionHand.MAIN_HAND);
	}

	/** A click on the charter's map, {@code dx}, {@code dz} from the hall: the packet the screen sends. */
	private static Colonies.Chosen clickMap(Village v, int dx, int dz) {
		return Colonies.clicked(new Colonies.Choose(true, v.hall().getX() + dx, v.hall().getZ() + dz), v.player());
	}

	private static Optional<BlockPos> spot(ItemStack charter) {
		ColonyCharterItem.Charter data = charter.get(ModComponents.COLONY_CHARTER);
		return data == null ? Optional.empty() : data.spot();
	}

	private static ChoiceMenu coloniesTab(Village v, ServerPlayer player) {
		ChoiceMenu menu = VillageHallScreen.forTest(player, v.hall());
		menu.press(VillageHallScreen.ROUTES, player);
		menu.press(TradePage.Tab.COLONIES.slot(), player);
		return menu;
	}

	private static List<ItemStack> charters(ServerPlayer player) {
		List<ItemStack> out = new ArrayList<>();
		for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
			if (player.getInventory().getItem(i).is(ModItems.COLONY_CHARTER)) {
				out.add(player.getInventory().getItem(i));
			}
		}
		return out;
	}

	// ---- 33.8: the charter -------------------------------------------------------------------------------------------

	/**
	 * "A City (colonyRank) can found a sister village": a charter bought while a City, its spot chosen, is refused at
	 * the hall once the village has fallen to a Town, by the real right-click, with the rank's sentence, and the charter
	 * stays in hand. With colonyRank Town, a Village (one below) can't buy, and a Town passes the rank at the hall.
	 */
	//$ gametest_ticks_batch SMALL '100' '"qaColChRank"'
	@GameTest(template = SMALL, timeoutTicks = 100, batch = "qaColChRank")
	public void oneRankBelowIsRefusedAtTheHallWithItsSentence(GameTestHelper helper) {
		Village v = village(helper, CHARTER_HALL, VillageRanks.Rank.CITY, 6400, false);
		Colonies.Bought bought = Colonies.buy(v.level(), v.hall(), v.player());
		helper.assertTrue(bought.ok() && charters(v.player()).size() == 1, "a City couldn't buy a charter: " + bought.message().getString());
		ItemStack held = charters(v.player()).get(0).copy();
		v.player().getInventory().clearContent();
		v.player().setItemInHand(InteractionHand.MAIN_HAND, held);
		helper.assertTrue(clickMap(v, 400, 0).ok(), "the spot wasn't chosen");
		ItemStack charter = v.player().getMainHandItem().copy();

		v.entity().setRank(VillageRanks.Rank.TOWN);
		ItemStack after = clickHall(v, v.player(), charter.copy());
		helper.assertTrue(v.order() == null && after.is(ModItems.COLONY_CHARTER) && after.getCount() == 1, "a Town sent settlers, or the charter went: " + v.order() + " " + after);
		String why = Settlers.send(v.level(), v.hall(), v.player(), charter.copy()).message().getString();
		helper.assertTrue(why.equals(v.name() + " must be a City to found a colony (it is a Town)."), "the refusal at the hall: " + why);

		Colonies.RANK = VillageRanks.Rank.TOWN; // colonyRank: town
		v.entity().setRank(VillageRanks.Rank.VILLAGE);
		Colonies.Bought village = Colonies.buy(v.level(), v.hall(), v.player());
		helper.assertTrue(village.refusal() == Colonies.Refusal.RANK && village.message().getString().equals(v.name() + " must be a Town to found a colony (it is a Village)."),
			"colonyRank town, a Village: " + village.message().getString());
		helper.assertTrue(v.entity().treasury() == 3200, "a refusal took money: " + v.entity().treasury());
		v.entity().setRank(VillageRanks.Rank.TOWN);
		String town = Settlers.send(v.level(), v.hall(), v.player(), charter.copy()).message().getString();
		helper.assertTrue(town.equals(v.name() + " can't spare two settlers: it needs a second builder or a jobless grown-up, and one more villager without a job or of a trade three share."),
			"colonyRank town, a Town should pass the rank (and find nobody to spare here): " + town);
		helper.succeed();
	}

	/** 7 days between colonies: six days after one, "in 1 day" (singular) on the refusal and the tab; on the seventh it may. */
	//$ gametest_ticks_batch SMALL '100' '"qaColChWait"'
	@GameTest(template = SMALL, timeoutTicks = 100, batch = "qaColChWait")
	public void theWaitEndsOnTheSeventhDayAndSaysOneDayTheDayBefore(GameTestHelper helper) {
		Village v = village(helper, CHARTER_HALL, VillageRanks.Rank.CITY, 6400, false);
		long today = Chronicle.day(v.level());
		GlobalPos colony = GlobalPos.of(v.level().dimension(), v.hall().offset(500, 0, 0));
		v.realms().recordFounded(v.mother(), colony, today - 6);
		Colonies.Bought refused = Colonies.buy(v.level(), v.hall(), v.player());
		helper.assertTrue(refused.refusal() == Colonies.Refusal.COOLDOWN && refused.message().getString().equals(v.name() + " can send its next colony in 1 day."),
			"six days after a colony: " + refused.message().getString());
		List<String> count = BoardTradeGameTests.lore(coloniesTab(v, v.player()).icon(Colonies.COUNT));
		helper.assertTrue(count.contains("The next colony can set out in 1 day") && count.contains("A village waits 7 days between colonies"), "the tab's count: " + count);
		helper.assertTrue(charters(v.player()).isEmpty() && v.entity().treasury() == 6400, "sold inside the wait");

		v.realms().clearColonies();
		v.realms().recordFounded(v.mother(), colony, today - 7);
		v.entity().setRank(VillageRanks.Rank.CITY); // (the tab counted this empty test village again)
		helper.assertTrue(Colonies.limit(v.level(), v.hall()) == null, "seven days after, the wait should be over: " + Colonies.limit(v.level(), v.hall()));
		Colonies.Bought bought = Colonies.buy(v.level(), v.hall(), v.player());
		helper.assertTrue(bought.ok() && charters(v.player()).size() == 1, "on the seventh day: " + bought.message().getString());
		helper.succeed();
	}

	/**
	 * "The player pays what the treasury lacks": a treasury of 31.99 emeralds pays 31 whole ones, and a player with none
	 * is told he lacks "the other 1 emerald"; with one he pays it and 0.99 stay in the treasury. A friend of the owner
	 * may buy the village's charter; it's bound to the village, not to him.
	 */
	//$ gametest_ticks_batch SMALL '100' '"qaColChCoins"'
	@GameTest(template = SMALL, timeoutTicks = 100, batch = "qaColChCoins")
	public void theTreasuryPaysWholeEmeraldsAndAFriendMayBuy(GameTestHelper helper) {
		Village v = village(helper, CHARTER_HALL, VillageRanks.Rank.CITY, 3199, false);
		Colonies.Bought broke = Colonies.buy(v.level(), v.hall(), v.player());
		helper.assertTrue(broke.refusal() == Colonies.Refusal.MONEY && broke.message().getString()
			.equals("A Colony Charter costs 32 emeralds: the treasury pays 31 emeralds and you don't have the other 1 emerald."), "one emerald short: " + broke.message().getString());
		helper.assertTrue(v.entity().treasury() == 3199, "a refusal took from the treasury: " + v.entity().treasury());
		v.player().getInventory().add(new ItemStack(Items.EMERALD, 1));
		Colonies.Bought bought = Colonies.buy(v.level(), v.hall(), v.player());
		helper.assertTrue(bought.ok() && bought.fromTreasury() == 31 && bought.fromPlayer() == 1 && v.entity().treasury() == 99
			&& v.player().getInventory().countItem(Items.EMERALD) == 0, "who paid: " + bought + " treasury " + v.entity().treasury());
		helper.assertTrue(bought.message().getString().equals("A Colony Charter of " + v.name() + ": the treasury paid 31 emeralds and you paid 1 emerald."),
			"the message: " + bought.message().getString());

		ServerPlayer friend = ProtectionSpecGameTests.player(helper);
		Friends friends = Friends.get(v.level().getServer());
		friends.add(v.player().getUUID(), friend.getUUID(), "Mira");
		Leftovers.after(helper, () -> friends.remove(v.player().getUUID(), friend.getUUID()));
		v.entity().setTreasury(3200);
		Colonies.Bought theirs = Colonies.buy(v.level(), v.hall(), friend);
		helper.assertTrue(theirs.ok() && charters(friend).size() == 1, "the owner's friend couldn't buy: " + theirs.message().getString());
		ColonyCharterItem.Charter data = charters(friend).get(0).get(ModComponents.COLONY_CHARTER);
		helper.assertTrue(data != null && data.hall().equals(v.mother()), "the friend's charter isn't the village's: " + data);
		helper.succeed();
	}

	/**
	 * The ring is measured round: on the diagonals, 1,023.9 blocks out is in and 1,024.6 out; 255.97 is too near and
	 * 256.7 in. Another hall's 128 blocks are round too: 127.98 from it is refused, 128.7 is fine.
	 */
	//$ gametest_ticks_batch SMALL '100' '"qaColChRing"'
	@GameTest(template = SMALL, timeoutTicks = 100, batch = "qaColChRing")
	public void theRingAndTheOtherHallsCirclesHoldOnTheDiagonals(GameTestHelper helper) {
		Village v = village(helper, CHARTER_HALL, VillageRanks.Rank.CITY, 6400, false);
		v.player().setItemInHand(InteractionHand.MAIN_HAND, ColonyCharterItem.of(v.level(), v.hall()));
		helper.assertTrue(clickMap(v, 724, 724).ok() && clickMap(v, -724, -724).ok(), "1,023.9 blocks out on a diagonal should do");
		helper.assertTrue(!clickMap(v, 725, 724).ok() && !clickMap(v, -724, 725).ok(), "1,024.6 blocks out should be too far");
		helper.assertTrue(clickMap(v, 182, 181).ok() && !clickMap(v, 181, 181).ok() && !clickMap(v, -181, 181).ok(), "255.97 too near, 256.7 fine");
		BlockPos ashford = v.hall().offset(-600, 0, 0);
		Caravans.Data.get(v.level()).setWants(ashford, Component.literal("Ashford"), List.of());
		Colonies.Chosen crowded = clickMap(v, -600 + 90, 91); // 127.98 from Ashford
		helper.assertTrue(!crowded.ok() && crowded.message().getString().equals("Too near Ashford: a colony keeps 128 blocks from every other Village Hall."),
			"127.98 blocks from Ashford: " + crowded.message().getString());
		helper.assertTrue(clickMap(v, -600 + 91, 91).ok(), "128.7 blocks from Ashford should do");
		BlockPos kept = spot(v.player().getMainHandItem()).orElse(null);
		helper.assertTrue(kept != null && kept.getX() == v.hall().getX() - 509 && kept.getZ() == v.hall().getZ() + 91, "the last good spot: " + kept);
		helper.succeed();
	}

	/**
	 * "In the same dimension": the charter of a hall in the Nether chooses nothing in the Overworld, from the map or the
	 * ground, and says so; at an Overworld hall it sends nobody and stays in the hand.
	 */
	//$ gametest_ticks_batch SMALL '100' '"qaColChWorld"'
	@GameTest(template = SMALL, timeoutTicks = 100, batch = "qaColChWorld")
	public void aCharterFromAnotherDimensionChoosesNothingHere(GameTestHelper helper) {
		Village v = village(helper, CHARTER_HALL, VillageRanks.Rank.CITY, 6400, true);
		ItemStack nether = ColonyCharterItem.of(v.level(), v.hall());
		nether.set(ModComponents.COLONY_CHARTER, new ColonyCharterItem.Charter(GlobalPos.of(Level.NETHER, v.hall()), Component.literal("Emberfall"), Optional.empty()));
		v.player().setItemInHand(InteractionHand.MAIN_HAND, nether);
		Colonies.Chosen map = clickMap(v, 400, 0);
		helper.assertTrue(!map.ok() && map.message().getString().equals("A colony of Emberfall must stay in its own dimension."), "from the map: " + map.message().getString());
		helper.assertTrue(spot(v.player().getMainHandItem()).isEmpty(), "a spot was kept: " + spot(v.player().getMainHandItem()));
		// on the ground, 300 blocks west
		BlockPos block = helper.absolutePos(new BlockPos(5, 1, 5));
		v.player().setPos(v.hall().getX() - 300 + 0.5, v.hall().getY(), v.hall().getZ() + 0.5);
		v.player().gameMode.useItemOn(v.player(), v.level(), v.player().getMainHandItem(), InteractionHand.MAIN_HAND,
			new BlockHitResult(Vec3.atCenterOf(block), Direction.UP, block, false));
		helper.assertTrue(spot(v.player().getMainHandItem()).isEmpty(), "the ground click kept a spot: " + spot(v.player().getMainHandItem()));
		helper.assertTrue(v.heard("A colony of Emberfall must stay in its own dimension."), "the ground click said: " + v.lines());
		// at this hall
		v.player().setPos(v.hall().getX() + 0.5, v.hall().getY(), v.hall().getZ() + 3.5);
		ItemStack withSpot = nether.copy();
		withSpot.set(ModComponents.COLONY_CHARTER, withSpot.get(ModComponents.COLONY_CHARTER).withSpot(v.hall().offset(400, 0, 0)));
		ItemStack after = clickHall(v, v.player(), withSpot);
		helper.assertTrue(v.order() == null && after.is(ModItems.COLONY_CHARTER), "a Nether charter sent settlers from here");
		helper.assertTrue(v.heard("This charter is Emberfall's: take it to that village's hall."), "at the hall: " + v.lines());
		helper.succeed();
	}

	// ---- 33.9: the settlers -------------------------------------------------------------------------------------------

	/**
	 * "Never ... the only worker of a job, a Legend; a married volunteer brings their partner": Tomas, married to the
	 * village's only farmer, can't go (she'd have to come), and Lena, the Legend by the hall, never volunteers; so Bram
	 * and Finn go, sent by the real right-click on the hall.
	 */
	//$ gametest_ticks_batch BIG '100' '"qaColChStay"'
	@GameTest(template = BIG, timeoutTicks = 100, batch = "qaColChStay")
	public void theOnlyFarmersHusbandAndALegendStayHome(GameTestHelper helper) {
		Village v = settlersVillage(helper, -1, false);
		Villager bram = villager(helper, new BlockPos(9, 2, 9), "Bram", ModVillagers.BUILDER, 1);
		villager(helper, new BlockPos(9, 2, 13), "Hale", ModVillagers.BUILDER, 3);
		Villager tomas = villager(helper, new BlockPos(10, 2, 11), "Tomas", VillagerProfession.NONE, 1);
		Villager fern = villager(helper, new BlockPos(13, 2, 7), "Fern", VillagerProfession.FARMER, 2);
		Villager lena = villager(helper, new BlockPos(12, 2, 11), "Lena", VillagerProfession.NONE, 1);
		Villager finn = villager(helper, new BlockPos(15, 2, 14), "Finn", VillagerProfession.NONE, 1);
		marry(tomas, fern);
		ModAttachments.LEGEND.set(lena, LegendData.settled(OLD_SAGE, "", Optional.of(v.hall()), Chronicle.day(v.level()), "test"));

		Settlers.Volunteers who = Settlers.volunteers(v.level(), v.hall());
		helper.assertTrue(who != null && who.builder() == bram && who.second() == finn && who.all().equals(List.of(bram, finn)),
			"Bram and Finn should go: " + (who == null ? null : who.all().stream().map(x -> x.getDisplayName().getString()).toList()));
		ItemStack after = clickHall(v, v.player(), charter(v, "Newbrook"));
		RealmData.Order order = v.order();
		helper.assertTrue(order != null && after.isEmpty(), "the order wasn't given: " + order);
		helper.assertTrue(order.volunteers().equals(List.of(bram.getUUID(), finn.getUUID())), "the order's settlers: " + order.volunteers());
		for (Villager stays : List.of(tomas, fern, lena)) {
			helper.assertTrue(!order.volunteers().contains(stays.getUUID()), stays.getDisplayName().getString() + " shouldn't go");
		}
		helper.succeed();
	}

	/**
	 * "The lowest-levelled worker of a job the village has three or more of": two farmers aren't three, so a village with
	 * no jobless villager can't send its second settler and the hall says why; a third farmer and the lowest of the
	 * three goes.
	 */
	//$ gametest_ticks_batch BIG '100' '"qaColChThree"'
	@GameTest(template = BIG, timeoutTicks = 100, batch = "qaColChThree")
	public void twoOfATradeAreNotThree(GameTestHelper helper) {
		Village v = settlersVillage(helper, -1, true);
		Villager bram = villager(helper, new BlockPos(9, 2, 9), "Bram", ModVillagers.BUILDER, 1);
		villager(helper, new BlockPos(9, 2, 13), "Hale", ModVillagers.BUILDER, 3);
		villager(helper, new BlockPos(13, 2, 7), "Fern", VillagerProfession.FARMER, 2);
		villager(helper, new BlockPos(14, 2, 7), "Fay", VillagerProfession.FARMER, 3);
		helper.assertTrue(Settlers.volunteers(v.level(), v.hall()) == null, "two farmers gave up one");
		ItemStack after = clickHall(v, v.player(), charter(v, "Newbrook"));
		helper.assertTrue(v.order() == null && after.is(ModItems.COLONY_CHARTER), "sent with nobody to spare");
		helper.assertTrue(v.heard(v.name() + " can't spare two settlers: it needs a second builder or a jobless grown-up, and one more villager without a job or of a trade three share."),
			"what the owner is told: " + v.lines());
		Villager flo = villager(helper, new BlockPos(15, 2, 7), "Flo", VillagerProfession.FARMER, 1);
		Settlers.Volunteers who = Settlers.volunteers(v.level(), v.hall());
		helper.assertTrue(who != null && who.builder() == bram && who.second() == flo && who.all().size() == 2, "with three farmers the lowest should go: "
			+ (who == null ? null : who.all().stream().map(x -> x.getDisplayName().getString()).toList()));
		helper.succeed();
	}

	/**
	 * The journey interrupted at the muster: both settlers killed while the village waits for them at the hall. Nobody
	 * is left to go, so the order is called off: every supply is back (in the Storehouse, or dropped at the hall), the
	 * charter with its spot and name lies at the hall, and the others stay.
	 */
	//$ gametest_ticks_batch BIG '200' '"qaColChKilled"'
	@GameTest(template = BIG, timeoutTicks = 200, batch = "qaColChKilled")
	public void everySettlerKilledAtTheMusterCallsItOffAndNothingIsLost(GameTestHelper helper) {
		Village v = settlersVillage(helper, 0, false);
		Settlers.MUSTER_TICKS = 5000;
		List<Villager> all = farVillage(helper);
		ChestBlockEntity chest = storehouse(helper, allSupplies());
		clickHall(v, v.player(), charter(v, "Newbrook"));
		v.player().setPos(v.hall().getX() + 0.5, v.hall().getY(), v.hall().getZ() + 30.5); // (out of reach of what drops at the hall)
		helper.assertTrue(v.order() != null && v.order().state().equals(RealmData.MUSTER) && v.order().volunteers().equals(List.of(all.get(0).getUUID(), all.get(1).getUUID())),
			"Bram and Tomas should be called to the hall: " + v.order());
		helper.runAfterDelay(5, () -> {
			helper.assertTrue(v.order() != null && v.order().state().equals(RealmData.MUSTER), "the bell rang without them: " + v.order());
			all.get(0).kill();
			all.get(1).kill();
		});
		helper.runAfterDelay(15, () -> {
			helper.assertTrue(v.order() == null, "the order still stands with its settlers dead: " + v.order());
			int logs = count(chest, s -> s.is(ItemTags.LOGS)) + dropped(v, s -> s.is(ItemTags.LOGS));
			int beds = count(chest, s -> s.is(ItemTags.BEDS)) + dropped(v, s -> s.is(ItemTags.BEDS));
			int bread = count(chest, s -> s.is(Items.BREAD)) + dropped(v, s -> s.is(Items.BREAD));
			helper.assertTrue(logs == 64 && beds == 3 && bread == 32, "the supplies aren't all back: " + logs + " logs, " + beds + " beds, " + bread + " bread");
			ItemStack back = ItemStack.EMPTY;
			for (ItemEntity e : v.level().getEntitiesOfClass(ItemEntity.class, new AABB(v.hall()).inflate(4))) {
				if (e.getItem().is(ModItems.COLONY_CHARTER)) {
					back = e.getItem();
				}
			}
			helper.assertTrue(spot(back).orElse(BlockPos.ZERO).equals(v.hall().offset(400, 0, 0)) && back.getHoverName().getString().equals("Newbrook"),
				"the charter at the hall: " + back);
			helper.assertTrue(all.get(2).isAlive(), "Hale should stay");
			helper.succeed();
		});
	}

	/**
	 * The mother's hall broken at the muster (the settlers called, not yet walking out): nobody leaves, nothing is lost,
	 * and the settlers are free to go back to their lives.
	 */
	//$ gametest_ticks_batch BIG '200' '"qaColChHallMuster"'
	@GameTest(template = BIG, timeoutTicks = 200, batch = "qaColChHallMuster")
	public void theHallBrokenAtTheMusterKeepsEveryoneHome(GameTestHelper helper) {
		Village v = settlersVillage(helper, 0, false);
		Settlers.MUSTER_TICKS = 5000;
		List<Villager> all = farVillage(helper);
		ChestBlockEntity chest = storehouse(helper, allSupplies());
		clickHall(v, v.player(), charter(v, "Newbrook"));
		v.player().setPos(v.hall().getX() + 0.5, v.hall().getY(), v.hall().getZ() + 30.5); // (out of reach of what drops at the hall)
		helper.assertTrue(v.order() != null && v.order().state().equals(RealmData.MUSTER), "not at the muster: " + v.order());
		helper.runAfterDelay(5, () -> helper.setBlock(HALL, Blocks.AIR));
		helper.runAfterDelay(70, () -> {
			helper.assertTrue(v.order() == null, "the order outlived its hall: " + v.order());
			for (Villager stays : all) {
				helper.assertTrue(stays.isAlive() && v.level().getEntity(stays.getUUID()) == stays, stays.getDisplayName().getString() + " left");
				helper.assertTrue(!Settlers.called(stays), stays.getDisplayName().getString() + " is still called to a hall that's gone");
			}
			int logs = count(chest, s -> s.is(ItemTags.LOGS)) + dropped(v, s -> s.is(ItemTags.LOGS));
			int torches = count(chest, s -> s.is(Items.TORCH)) + dropped(v, s -> s.is(Items.TORCH));
			helper.assertTrue(logs == 64 && torches == 16, "the supplies aren't all back: " + logs + " logs, " + torches + " torches");
			helper.assertTrue(dropped(v, s -> s.is(ModItems.COLONY_CHARTER)) == 1, "the charter should lie at the hall");
			helper.succeed();
		});
	}

	/**
	 * "Can call it off until they leave": at the muster, the bell not yet rung, the owner calls it off on the tab and is
	 * told so in the spec's words; the settlers stay, the supplies and the charter come back.
	 */
	//$ gametest_ticks_batch BIG '200' '"qaColChCallMuster"'
	@GameTest(template = BIG, timeoutTicks = 200, batch = "qaColChCallMuster")
	public void callingItOffAtTheMusterPutsEveryoneBackAndSaysSo(GameTestHelper helper) {
		Village v = settlersVillage(helper, 0, true);
		Settlers.MUSTER_TICKS = 5000;
		List<Villager> all = farVillage(helper);
		ChestBlockEntity chest = storehouse(helper, allSupplies());
		clickHall(v, v.player(), charter(v, "Newbrook"));
		helper.assertTrue(v.order() != null && v.order().state().equals(RealmData.MUSTER), "not at the muster: " + v.order());
		helper.runAfterDelay(5, () -> {
			ChoiceMenu tab = coloniesTab(v, v.player());
			helper.assertTrue(tab.icon(Settlers.CALL_OFF).is(Items.BARRIER), "no call-off at the muster: " + tab.icon(Settlers.CALL_OFF));
			List<String> lore = BoardTradeGameTests.lore(tab.icon(Colonies.ON_ROAD));
			helper.assertTrue(lore.contains("Setting out for Newbrook"), "the tab at the muster: " + lore);
			tab.press(Settlers.CALL_OFF, v.player());
			helper.assertTrue(v.order() == null, "not called off: " + v.order());
			helper.assertTrue(v.heard("The colony Newbrook is called off: the settlers stay in " + v.name()
				+ ", the supplies are back in the Storehouses and the charter is yours again."), "what the owner is told: " + v.lines());
			helper.assertTrue(count(chest, s -> s.is(ItemTags.LOGS)) == 64 && count(chest, s -> s.is(ItemTags.PLANKS)) == 64 && count(chest, s -> s.is(ItemTags.BEDS)) == 3
				&& count(chest, s -> s.is(Items.GLASS_PANE)) == 12, "the supplies aren't back in the Storehouse");
			helper.assertTrue(charters(v.player()).size() == 1 && spot(charters(v.player()).get(0)).isPresent(), "the charter didn't come back");
		});
		helper.runAfterDelay(60, () -> {
			for (Villager stays : all) {
				helper.assertTrue(stays.isAlive() && !Settlers.called(stays), stays.getDisplayName().getString() + " is gone or still called");
			}
			helper.assertTrue(v.order() == null, "the order came back");
			helper.succeed();
		});
	}

	/**
	 * The sentences of setting out, with their arguments: sent with supplies missing ("still miss 64 planks, 1 bed"),
	 * sent with all packed ("they leave in the morning"), an unnamed colony ("a new village") and the bell's line to the
	 * owner standing by.
	 */
	//$ gametest_ticks_batch BIG '200' '"qaColChWords"'
	@GameTest(template = BIG, timeoutTicks = 200, batch = "qaColChWords")
	public void theSettingOutSentencesReadRight(GameTestHelper helper) {
		Village v = settlersVillage(helper, -1, true);
		Settlers.MUSTER_TICKS = 40;
		plainVillage(helper);
		ChestBlockEntity chest = storehouse(helper, new ItemStack(Items.OAK_LOG, 64), new ItemStack(Items.COBBLESTONE, 64), new ItemStack(Items.BREAD, 32),
			new ItemStack(Items.TORCH, 16), new ItemStack(Items.GLASS_PANE, 12), new ItemStack(Items.RED_BED), new ItemStack(Items.RED_BED));
		clickHall(v, v.player(), charter(v, "Newbrook"));
		String missing = "Bram and Tomas will set out for Newbrook. The Storehouses still miss 64 planks, 1 bed: bring it, or they leave in 3 days with what there is.";
		helper.assertTrue(v.order() != null && v.heard(missing), "sent short: " + v.lines());
		helper.assertTrue(Settlers.callOff(v.level(), v.hall(), v.player()) && v.order() == null, "couldn't call it off");
		chest.setItem(20, new ItemStack(Items.BIRCH_PLANKS, 64));
		chest.setItem(21, new ItemStack(Items.WHITE_BED));
		v.player().getInventory().clearContent();
		clickHall(v, v.player(), charter(v, "Newbrook"));
		helper.assertTrue(v.order() != null && v.heard("Bram and Tomas will set out for Newbrook. Their supplies are packed: they leave in the morning."), "sent packed: " + v.lines());
		helper.assertTrue(Settlers.callOff(v.level(), v.hall(), v.player()) && v.order() == null, "couldn't call it off again");

		Settlers.MUSTER_DELAY = 0;
		v.player().getInventory().clearContent();
		clickHall(v, v.player(), charter(v, null));
		helper.assertTrue(v.order() != null && v.heard("Bram and Tomas will set out for a new village. Their supplies are packed: they leave in the morning."),
			"sent unnamed: " + v.lines());
		helper.succeedWhen(() -> {
			helper.assertTrue(v.order() != null && v.order().gone(), "the bell hasn't rung: " + (v.order() == null ? null : v.order().state()));
			helper.assertTrue(v.heard("The bell rings: Bram and Tomas set out from " + v.name() + " for a new village."), "the bell's line: " + v.lines());
		});
	}

	// ---- 33.10: arriving ----------------------------------------------------------------------------------------------

	/** The colony_area mother village Thornholm, owned by a listener, its roof taken off; arrival leftovers cleared after. */
	private static Village thornholm(GameTestHelper helper, BlockPos at) {
		Leftovers.finished(helper);
		AABB box = helper.getBounds();
		ServerLevel level = helper.getLevel();
		for (BlockPos p : BlockPos.betweenClosed(BlockPos.containing(box.minX, box.maxY - 3, box.minZ), BlockPos.containing(box.maxX, box.maxY + 3, box.maxZ))) {
			if (level.getBlockState(p).is(Blocks.BARRIER)) {
				level.setBlock(p, Blocks.AIR.defaultBlockState(), 2);
			}
		}
		Village v = village(helper, at, VillageRanks.Rank.HAMLET, 0, true);
		VillageHalls.RADIUS = 8;
		v.entity().setCustomName(Component.literal("Thornholm"));
		Leftovers.after(helper, () -> {
			Caravans.Data caravans = Caravans.Data.get(level);
			for (RealmData.Founded f : v.realms().founded()) {
				caravans.remove(f.hall().pos());
				TravelNetwork network = TravelNetwork.get(level.getServer());
				for (BlockPos p : BlockPos.betweenClosed(f.hall().pos().offset(-8, -2, -8), f.hall().pos().offset(8, 2, 8))) {
					network.remove(GlobalPos.of(level.dimension(), p.immutable()));
				}
			}
			caravans.clearRoad(v.hall());
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
		return v;
	}

	private static CompoundTag saved(GameTestHelper helper, String name, VillagerProfession job, int jobLevel, int xp) {
		Villager x = helper.spawn(EntityType.VILLAGER, new BlockPos(2, 2, 2));
		x.setCustomName(Component.literal(name));
		x.setVillagerData(x.getVillagerData().setProfession(job).setLevel(jobLevel));
		x.setVillagerXp(xp);
		CompoundTag tag = new CompoundTag();
		x.saveWithoutId(tag);
		x.discard();
		return tag;
	}

	/** Thornholm's order with Tomas (builder) and Dara (farmer) on the road, all supplies, there now. */
	private static RealmData.Order onTheRoad(GameTestHelper helper, Village v, BlockPos spot, Optional<Component> name) {
		CompoundTag tomas = saved(helper, "Tomas", ModVillagers.BUILDER, 2, 20);
		CompoundTag dara = saved(helper, "Dara", VillagerProfession.FARMER, 1, 0);
		Map<Item, Integer> supplies = new LinkedHashMap<>();
		supplies.put(Items.OAK_LOG, 64);
		supplies.put(Items.OAK_PLANKS, 64);
		supplies.put(Items.COBBLESTONE, 64);
		supplies.put(Items.BREAD, 32);
		supplies.put(Items.TORCH, 16);
		supplies.put(Items.GLASS_PANE, 12);
		supplies.put(Items.WHITE_BED, 3);
		long now = v.level().getGameTime();
		return new RealmData.Order(v.mother(), helper.absolutePos(spot), name, RealmData.ON_ROAD, now - 100, now, Colonies.COST * 100,
			now - 200, List.of(), Optional.of(tomas.getUUID("UUID")), supplies, List.of(tomas, dara));
	}

	private static List<String> chronicle(ServerLevel level, BlockPos hall) {
		return level.getBlockEntity(hall) instanceof VillageHallBlockEntity e ? e.chronicle().stream().map(x -> x.text().getString()).toList() : List.of();
	}

	/**
	 * "The hall is named from the charter (or made up)": an unnamed colony gets a made-up name, the same on its hall,
	 * its post, both chronicles and the line the owner, online, reads at once ("... A letter is on its way."); the
	 * colony is counted and the 7 days' wait starts.
	 */
	//$ gametest_ticks_batch COLONY '200' '"qaColChUnnamed"'
	@GameTest(template = COLONY, timeoutTicks = 200, batch = "qaColChUnnamed")
	public void anUnnamedColonyIsNamedAndTheOwnerOnlineHearsOfIt(GameTestHelper helper) {
		Village v = thornholm(helper, new BlockPos(3, 2, 22));
		helper.runAfterDelay(2, () -> v.realms().putOrder(onTheRoad(helper, v, new BlockPos(22, 2, 22), Optional.empty())));
		helper.succeedWhen(() -> {
			helper.assertTrue(v.realms().orders().isEmpty() && v.realms().foundedBy(v.mother()).size() == 1, "not founded yet: " + v.realms().orders());
			BlockPos hall = v.realms().foundedBy(v.mother()).get(0).hall().pos();
			String name = VillageHalls.name(v.level(), hall).getString();
			// B521: the made-up name comes from the colony hall's spot, which moves with the test's place in the batch, and
			// one spot in 400 (20 first halves by 20 second) is itself "Thornholm"; the colony must carry its own spot's made-up name, never the mother's.
			String madeUp = VillageHalls.madeUpName(hall).getString();
			helper.assertTrue(!name.isBlank() && !name.equals("a new village") && !name.contains("aliveworkplace") && name.equals(madeUp),
				"the colony's name: " + name + " (its spot's made-up name: " + madeUp + ")");
			helper.assertTrue(v.player().getUUID().equals(((VillageHallBlockEntity) v.level().getBlockEntity(hall)).owner()), "the colony isn't the owner's");
			TravelNetwork.Post post = null;
			for (BlockPos p : BlockPos.betweenClosed(hall.offset(-10, -3, -10), hall.offset(10, 3, 10))) {
				if (v.level().getBlockState(p).is(ModBlocks.TRAVEL_POST)) {
					post = TravelNetwork.get(v.level().getServer()).at(GlobalPos.of(v.level().dimension(), p.immutable()));
				}
			}
			helper.assertTrue(post != null && post.name().equals(name), "the post's name: " + (post == null ? null : post.name()) + ", the hall's " + name);
			long day = Chronicle.day(v.level());
			helper.assertTrue(chronicle(v.level(), hall).contains("Founded on day " + day + " by settlers from Thornholm: Tomas and Dara"), "the colony's chronicle: " + chronicle(v.level(), hall));
			helper.assertTrue(chronicle(v.level(), v.hall()).stream().anyMatch(l -> l.matches("Our settlers Tomas and Dara founded " + java.util.regex.Pattern.quote(name) + ", \\d+ blocks east")),
				"the mother's chronicle: " + chronicle(v.level(), v.hall()));
			helper.assertTrue(v.lines().stream().anyMatch(l -> l.matches("Tomas and Dara have founded " + java.util.regex.Pattern.quote(name) + ", \\d+ blocks east of Thornholm\\. A letter is on its way\\.")),
				"what the owner heard: " + v.lines());
			helper.assertTrue(v.realms().cooldown(v.mother()).founded() == 1 && Colonies.cooldownLeft(v.level(), v.hall()) == 7, "the wait didn't start: "
				+ v.realms().cooldown(v.mother()) + " " + Colonies.cooldownLeft(v.level(), v.hall()));
		});
	}

	/** No room within reach (a lake): the owner, online, reads what came home and what went back into the treasury. */
	//$ gametest_ticks_batch HUGE '200' '"qaColChNoRoom"'
	@GameTest(template = HUGE, timeoutTicks = 200, batch = "qaColChNoRoom")
	public void withNoRoomTheOwnerIsToldEverythingCameHome(GameTestHelper helper) {
		Village v = thornholm(helper, new BlockPos(1, 2, 1));
		ColonyArrival.ROOM = 4;
		v.entity().setTreasury(500);
		helper.setBlock(new BlockPos(1, 2, 3), ModBlocks.STOREHOUSE);
		helper.setBlock(new BlockPos(2, 2, 3), Blocks.CHEST);
		for (int x = 4; x <= 26; x++) {
			for (int z = 4; z <= 26; z++) {
				boolean rim = x == 4 || x == 26 || z == 4 || z == 26;
				helper.setBlock(new BlockPos(x, 2, z), rim ? Blocks.STONE_BRICKS : Blocks.WATER);
			}
		}
		helper.runAfterDelay(2, () -> v.realms().putOrder(onTheRoad(helper, v, new BlockPos(15, 2, 15), Optional.of(Component.literal("Newbrook")))));
		helper.succeedWhen(() -> {
			helper.assertTrue(v.realms().orders().isEmpty() && v.realms().founded().isEmpty(), "the order: " + v.realms().orders());
			helper.assertTrue(v.heard("No room for Newbrook: only water or cliffs within 4 blocks of the spot. Tomas and Dara came home to Thornholm with the supplies, "
				+ "and 32 emeralds went back into the treasury."), "what the owner heard: " + v.lines());
			helper.assertTrue(v.entity().treasury() == 500 + Colonies.COST * 100, "the treasury: " + v.entity().treasury());
			helper.assertTrue(v.realms().cooldown(v.mother()).founded() == 0 && Colonies.cooldownLeft(v.level(), v.hall()) == 0, "a colony that came home started the wait");
		});
	}
}
