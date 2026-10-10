package io.github.jcondedata.aliveworkplace.test;

import io.github.jcondedata.aliveworkplace.colony.Colonies;
import io.github.jcondedata.aliveworkplace.colony.ColonyCharterItem;
import io.github.jcondedata.aliveworkplace.colony.Settlers;
import io.github.jcondedata.aliveworkplace.hall.Caravans;
import io.github.jcondedata.aliveworkplace.hall.VillageHallBlockEntity;
import io.github.jcondedata.aliveworkplace.hall.VillageHallScreen;
import io.github.jcondedata.aliveworkplace.hall.VillageHalls;
import io.github.jcondedata.aliveworkplace.hall.VillageNeeds;
import io.github.jcondedata.aliveworkplace.hall.VillageRanks;
import io.github.jcondedata.aliveworkplace.people.Couples;
import io.github.jcondedata.aliveworkplace.realm.RealmData;
import io.github.jcondedata.aliveworkplace.registry.ModAttachments;
import io.github.jcondedata.aliveworkplace.registry.ModBlocks;
import io.github.jcondedata.aliveworkplace.registry.ModComponents;
import io.github.jcondedata.aliveworkplace.registry.ModItems;
import io.github.jcondedata.aliveworkplace.registry.ModVillagers;
import io.github.jcondedata.aliveworkplace.store.StorehouseBoard;
import io.github.jcondedata.aliveworkplace.trade.TradePage;
import io.github.jcondedata.aliveworkplace.work.ChoiceMenu;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Predicate;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.npc.VillagerProfession;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

/**
 * ROADMAP 33.9, the settlers set out: a right-click on the hall with its charter, the spot chosen, has two settlers
 * volunteer (the lowest-levelled of two builders or a jobless grown-up, and a jobless villager or the lowest-levelled
 * of three of a trade; never a guard, a guard's partner, the only worker of a job or a child; a married volunteer's
 * partner comes too), takes the supplies out of the Storehouse (the missing ones on the village's wants and the
 * Storehouse board, and after 3 days they leave without), gathers them at the hall, and puts them on the road as saved
 * villagers with the supplies, the journey 3 minutes plus a tick a block. The Colonies tab shows the order and calls
 * it off until they leave, which puts everyone and everything back. The order survives a save and reload. Refused:
 * no spot, not the owner, the switch off, another hall's charter, nobody to spare, a colony already on the road.
 * Colonies are off in GameTests, so every test turns them on, alone in its batch.
 */
public class ColonySettlersGameTests implements FabricGameTest {
	private static final String AREA = "aliveworkplace_test:big_area";
	private static final BlockPos HALL = new BlockPos(11, 2, 11);
	private static final BlockPos STOREHOUSE = new BlockPos(13, 2, 9);
	private static final BlockPos CHEST = new BlockPos(14, 2, 9);

	private record Village(ServerLevel level, BlockPos hall, VillageHallBlockEntity entity, ServerPlayer player, String name) {
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
	 * A City's hall owned by a survival player, the village shrunk to the test's area, colonies on, no hall rounds, the
	 * colony records empty; the settlers gather as soon as they're ready ({@code musterDelay} 0) unless the test wants
	 * the morning (-1). Everything is put back when the test ends.
	 */
	private static Village village(GameTestHelper helper, int musterDelay) {
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
		ServerPlayer player = ProtectionSpecGameTests.player(helper);
		VillageHallBlockEntity entity = helper.getBlockEntity(HALL);
		entity.setOwner(player.getUUID(), "Owner");
		entity.setRank(VillageRanks.Rank.CITY);
		return new Village(level, hall, entity, player, VillageHalls.name(level, hall).getString());
	}

	/** A named villager standing still at {@code pos}: of {@code job} at {@code jobLevel} with a workstation, or jobless. */
	private static Villager villager(GameTestHelper helper, BlockPos pos, String name, VillagerProfession job, int jobLevel) {
		Villager v = helper.spawn(EntityType.VILLAGER, pos);
		v.setNoAi(true);
		v.setCustomName(Component.literal(name));
		if (job != VillagerProfession.NONE) {
			v.setVillagerData(v.getVillagerData().setProfession(job).setLevel(jobLevel));
			v.getBrain().setMemory(MemoryModuleType.JOB_SITE, GlobalPos.of(helper.getLevel().dimension(), helper.absolutePos(pos)));
		}
		return v;
	}

	private static void marry(Villager a, Villager b) {
		ModAttachments.PARTNER.set(a, new Couples.Partner(b.getUUID(), b.getDisplayName(), 1, true));
		ModAttachments.PARTNER.set(b, new Couples.Partner(a.getUUID(), a.getDisplayName(), 1, true));
	}

	/** Two builders (Bram the lower) and the jobless Tomas and Finn: a village that can spare Bram and Tomas. */
	private static List<Villager> plainVillage(GameTestHelper helper) {
		return List.of(villager(helper, new BlockPos(9, 2, 9), "Bram", ModVillagers.BUILDER, 1),
			villager(helper, new BlockPos(9, 2, 13), "Hale", ModVillagers.BUILDER, 3),
			villager(helper, new BlockPos(10, 2, 11), "Tomas", VillagerProfession.NONE, 1),
			villager(helper, new BlockPos(13, 2, 13), "Finn", VillagerProfession.NONE, 1));
	}

	/** A Storehouse with a chest holding {@code stacks}. */
	private static ChestBlockEntity storehouse(GameTestHelper helper, ItemStack... stacks) {
		helper.setBlock(STOREHOUSE, ModBlocks.STOREHOUSE);
		helper.setBlock(CHEST, Blocks.CHEST);
		ChestBlockEntity chest = helper.getBlockEntity(CHEST);
		for (int i = 0; i < stacks.length; i++) {
			chest.setItem(i, stacks[i]);
		}
		return chest;
	}

	/** Everything the settlers take. */
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

	/** The village's charter, the spot chosen 400 blocks east, named Newbrook. */
	private static ItemStack charter(Village v) {
		ItemStack stack = ColonyCharterItem.of(v.level(), v.hall());
		stack.set(ModComponents.COLONY_CHARTER, stack.get(ModComponents.COLONY_CHARTER).withSpot(v.hall().offset(400, 0, 0)));
		stack.set(DataComponents.CUSTOM_NAME, Component.literal("Newbrook"));
		return stack;
	}

	/** {@code player} right-clicks the hall with {@code stack} in hand: the way a player sends the settlers. */
	private static ItemStack click(Village v, ServerPlayer player, ItemStack stack) {
		player.setItemInHand(InteractionHand.MAIN_HAND, stack);
		ItemStack held = player.getItemInHand(InteractionHand.MAIN_HAND);
		player.gameMode.useItemOn(player, v.level(), held, InteractionHand.MAIN_HAND, new BlockHitResult(Vec3.atCenterOf(v.hall()), Direction.UP, v.hall(), false));
		return player.getItemInHand(InteractionHand.MAIN_HAND);
	}

	private static ChoiceMenu coloniesTab(Village v, ServerPlayer player) {
		ChoiceMenu menu = VillageHallScreen.forTest(player, v.hall());
		menu.press(VillageHallScreen.ROUTES, player);
		menu.press(TradePage.Tab.COLONIES.slot(), player);
		return menu;
	}

	private static List<String> names(List<Villager> villagers) {
		return villagers.stream().map(x -> x.getDisplayName().getString()).toList();
	}

	private static List<UUID> ids(List<Villager> villagers) {
		return villagers.stream().map(Entity::getUUID).toList();
	}

	/**
	 * Who volunteers: the lower of two builders and the nearest jobless villager, whose wife comes too. With one builder
	 * left he stays, and so do the guard, the guard's husband, the only farmer and the child: nobody can be spared and
	 * the hall refuses. With three farmers the lowest-levelled goes, and the order names the settlers.
	 */
	//$ gametest_ticks_batch AREA '100' '"colonyVolunteers"'
	@GameTest(template = AREA, timeoutTicks = 100, batch = "colonyVolunteers")
	public void theRightVillagersVolunteer(GameTestHelper helper) {
		Village v = village(helper, -1);
		Villager bram = villager(helper, new BlockPos(9, 2, 9), "Bram", ModVillagers.BUILDER, 1);
		Villager hale = villager(helper, new BlockPos(9, 2, 13), "Hale", ModVillagers.BUILDER, 3);
		villager(helper, new BlockPos(13, 2, 7), "Fern", VillagerProfession.FARMER, 2);
		Villager gus = villager(helper, new BlockPos(15, 2, 11), "Gus", ModVillagers.GUARD, 1);
		Villager tomas = villager(helper, new BlockPos(10, 2, 11), "Tomas", VillagerProfession.NONE, 1);
		Villager dara = villager(helper, new BlockPos(11, 2, 14), "Dara", VillagerProfession.NONE, 1);
		Villager finn = villager(helper, new BlockPos(13, 2, 13), "Finn", VillagerProfession.NONE, 1);
		Villager pip = villager(helper, new BlockPos(12, 2, 12), "Pip", VillagerProfession.NONE, 1);
		pip.setBaby(true);
		marry(tomas, dara);
		marry(finn, gus);

		Settlers.Volunteers first = Settlers.volunteers(v.level(), v.hall());
		helper.assertTrue(first != null && first.builder() == bram && first.second() == tomas && first.all().equals(List.of(bram, tomas, dara)),
			"two builders, a jobless couple: " + (first == null ? null : names(first.all())));

		// one builder left: he stays; Tomas would be the builder, but Finn's wife is a guard, the farmer is the only one
		hale.discard();
		helper.assertTrue(Settlers.volunteers(v.level(), v.hall()) == null, "nobody but Tomas and Dara can be spared, that's one volunteer short");
		ItemStack held = click(v, v.player(), charter(v));
		helper.assertTrue(v.order() == null && held.is(ModItems.COLONY_CHARTER) && held.getCount() == 1, "refused, the charter should stay: " + held + " " + v.order());
		helper.assertTrue(Settlers.send(v.level(), v.hall(), v.player(), charter(v)).message().getString().equals(v.name()
			+ " can't spare two settlers: it needs a second builder or a jobless grown-up, and one more villager without a job or of a trade three share."),
			"the refusal: " + Settlers.send(v.level(), v.hall(), v.player(), charter(v)).message().getString());

		// three farmers: the lowest-levelled is the second settler
		Villager flo = villager(helper, new BlockPos(14, 2, 7), "Flo", VillagerProfession.FARMER, 1);
		villager(helper, new BlockPos(15, 2, 7), "Fay", VillagerProfession.FARMER, 3);
		Settlers.Volunteers second = Settlers.volunteers(v.level(), v.hall());
		helper.assertTrue(second != null && second.builder() == tomas && second.second() == flo && second.all().equals(List.of(tomas, dara, flo)),
			"a jobless builder-to-be, his wife and the lowest farmer: " + (second == null ? null : names(second.all())));

		held = click(v, v.player(), charter(v));
		RealmData.Order order = v.order();
		helper.assertTrue(order != null && held.isEmpty(), "the order wasn't given, or the charter stayed: " + order + " " + held);
		helper.assertTrue(order.volunteers().equals(ids(List.of(tomas, dara, flo))) && order.builder().orElseThrow().equals(tomas.getUUID()),
			"the order's settlers: " + order.volunteers());
		helper.assertTrue(order.spot().equals(v.hall().offset(400, 0, 0)) && order.name().orElseThrow().getString().equals("Newbrook")
			&& order.state().equals(RealmData.GATHERING), "the order: " + order);
		for (Villager stays : List.of(bram, gus, finn, pip)) {
			helper.assertTrue(!order.volunteers().contains(stays.getUUID()), stays.getDisplayName().getString() + " shouldn't go");
		}
		helper.succeed();
	}

	/**
	 * The supplies come out of the Storehouse at once; the planks and the bed it lacks are on the village's wants and the
	 * Storehouse board, in a settler's name; handed over there they are taken too, and the leaving is set for the morning.
	 */
	//$ gametest_ticks_batch AREA '200' '"colonySupplies"'
	@GameTest(template = AREA, timeoutTicks = 200, batch = "colonySupplies")
	public void suppliesAreTakenAndTheMissingBecomeWants(GameTestHelper helper) {
		Village v = village(helper, -1);
		plainVillage(helper);
		ChestBlockEntity chest = storehouse(helper, new ItemStack(Items.OAK_LOG, 40), new ItemStack(Items.BIRCH_LOG, 30), new ItemStack(Items.SPRUCE_PLANKS, 40),
			new ItemStack(Items.COBBLESTONE, 64), new ItemStack(Items.BREAD, 40), new ItemStack(Items.TORCH, 16), new ItemStack(Items.GLASS_PANE, 12),
			new ItemStack(Items.RED_BED), new ItemStack(Items.WHITE_BED), new ItemStack(Items.DIRT, 10));
		click(v, v.player(), charter(v));
		RealmData.Order order = v.order();
		helper.assertTrue(order != null, "no order");
		helper.assertTrue(Settlers.held(order, Settlers.Supply.LOGS) == 64 && Settlers.held(order, Settlers.Supply.PLANKS) == 40
			&& Settlers.held(order, Settlers.Supply.COBBLESTONE) == 64 && Settlers.held(order, Settlers.Supply.BREAD) == 32
			&& Settlers.held(order, Settlers.Supply.TORCHES) == 16 && Settlers.held(order, Settlers.Supply.GLASS_PANES) == 12
			&& Settlers.held(order, Settlers.Supply.BEDS) == 2, "taken: " + order.supplies());
		helper.assertTrue(count(chest, s -> s.is(ItemTags.LOGS)) == 6 && count(chest, s -> s.is(Items.BREAD)) == 8 && count(chest, s -> s.is(Items.DIRT)) == 10
			&& count(chest, s -> s.is(ItemTags.PLANKS) || s.is(ItemTags.BEDS) || s.is(Items.COBBLESTONE) || s.is(Items.TORCH) || s.is(Items.GLASS_PANE)) == 0,
			"the chest should keep 6 logs, 8 bread and the dirt");
		helper.assertTrue(Settlers.missing(order).equals(Map.of(Settlers.Supply.PLANKS, 24, Settlers.Supply.BEDS, 1)), "missing: " + Settlers.missing(order));
		helper.assertTrue(order.leaves() == 0, "short of supplies, the leaving shouldn't be set yet");

		// on the village's wants, where partners' caravans read them
		Caravans.round(v.level(), v.hall(), VillageHalls.census(v.level(), v.hall()));
		List<Caravans.Want> wants = Caravans.Data.get(v.level()).village(v.hall()).wants();
		helper.assertTrue(wants.contains(new Caravans.Want(Items.OAK_PLANKS, 24)) && wants.contains(new Caravans.Want(Items.WHITE_BED, 1)), "the wants: " + wants);

		// on the Colonies tab, with the call-off beside it
		ChoiceMenu tab = coloniesTab(v, v.player());
		List<String> lore = BoardTradeGameTests.lore(tab.icon(Colonies.ON_ROAD));
		helper.assertTrue(lore.contains("Getting ready for Newbrook") && lore.contains("Settlers: Bram and Tomas") && lore.contains("Waiting for: 24 planks, 1 bed")
			&& lore.contains("They leave with what there is in 3 days"), "the tab's order: " + lore);
		helper.assertTrue(tab.icon(Settlers.CALL_OFF).getHoverName().getString().equals("Call the colony off"), "no call-off: " + tab.icon(Settlers.CALL_OFF));

		// on the Storehouse board: the player hands the planks and the bed over, and the settlers take them
		ChoiceMenu board = StorehouseBoard.boardForTest(v.player(), helper.absolutePos(STOREHOUSE));
		List<String> asked = new ArrayList<>();
		for (int slot = StorehouseBoard.FIRST_REQUEST; slot < StorehouseBoard.FIRST_REQUEST + 2; slot++) {
			asked.add(board.icon(slot).getHoverName().getString());
		}
		helper.assertTrue(asked.equals(List.of("24 planks", "1 bed")), "the board asks for: " + asked);
		v.player().getInventory().add(new ItemStack(Items.BIRCH_PLANKS, 30));
		v.player().getInventory().add(new ItemStack(Items.BLUE_BED));
		board.press(StorehouseBoard.FIRST_REQUEST, v.player());
		board.press(StorehouseBoard.FIRST_REQUEST + 1, v.player());
		helper.assertTrue(v.player().getInventory().countItem(Items.BIRCH_PLANKS) == 6 && v.player().getInventory().countItem(Items.BLUE_BED) == 0,
			"the board should take 24 planks and the bed");
		helper.succeedWhen(() -> {
			RealmData.Order now = v.order();
			helper.assertTrue(now != null && Settlers.missing(now).isEmpty(), "still missing: " + (now == null ? null : Settlers.missing(now)));
			helper.assertTrue(now.state().equals(RealmData.GATHERING) && now.leaves() > v.level().getGameTime()
				&& now.leaves() - v.level().getGameTime() <= 24000, "the leaving should be set for the next morning: " + now.leaves());
			helper.assertTrue(BoardTradeGameTests.lore(coloniesTab(v, v.player()).icon(Colonies.ON_ROAD)).contains("Ready for Newbrook: they leave in the morning"),
				"the tab when ready: " + BoardTradeGameTests.lore(coloniesTab(v, v.player()).icon(Colonies.ON_ROAD)));
			helper.assertTrue(Caravans.Data.get(v.level()).village(v.hall()) != null && StorehouseBoard.boardForTest(v.player(), helper.absolutePos(STOREHOUSE))
				.icon(StorehouseBoard.FIRST_REQUEST).getHoverName().getString().matches(".*(planks|bed).*") == false, "the board still asks for something");
		});
	}

	/**
	 * Ready, the settlers are called to the hall, the bell rings and they walk out; half a minute later at most they are
	 * on the road: out of the world, saved in the order as they were, with the supplies, 3 minutes and 400 ticks from the
	 * spot 400 blocks away. From then on the order can't be called off, and a second charter is refused.
	 */
	//$ gametest_ticks_batch AREA '400' '"colonyLeaves"'
	@GameTest(template = AREA, timeoutTicks = 400, batch = "colonyLeaves")
	public void theSettlersLeaveAndAreSaved(GameTestHelper helper) {
		Village v = village(helper, 0);
		Settlers.MUSTER_TICKS = 40;
		Settlers.LEAVE_TICKS = 60;
		List<Villager> all = plainVillage(helper);
		Villager bram = all.get(0);
		Villager tomas = all.get(2);
		Villager far = villager(helper, new BlockPos(4, 2, 11), "Dara", VillagerProfession.NONE, 1);
		marry(tomas, far);
		storehouse(helper, allSupplies());
		click(v, v.player(), charter(v));
		helper.assertTrue(v.order() != null && v.order().state().equals(RealmData.MUSTER) && Settlers.missing(v.order()).isEmpty(),
			"with everything there and no morning to wait for, they gather at once: " + v.order());
		helper.runAfterDelay(12, () -> {
			helper.assertTrue(v.order().state().equals(RealmData.MUSTER), "Dara is 7 blocks from the hall: the bell waits for her");
			BlockPos to = far.getBrain().getMemory(MemoryModuleType.WALK_TARGET).map(w -> w.getTarget().currentBlockPosition()).orElse(null);
			helper.assertTrue(v.hall().equals(to), "Dara should be walking to the hall, not " + to);
		});
		helper.succeedWhen(() -> {
			RealmData.Order order = v.order();
			helper.assertTrue(order != null && order.state().equals(RealmData.ON_ROAD), "not on the road yet: " + (order == null ? null : order.state()));
			for (Villager gone : List.of(bram, tomas, far)) {
				helper.assertTrue(gone.isRemoved() && v.level().getEntity(gone.getUUID()) == null, gone.getDisplayName().getString() + " is still in the world");
			}
			helper.assertTrue(all.get(1).isAlive() && all.get(3).isAlive(), "Hale and Finn stay");
			List<String> saved = order.settlers().stream().map(t -> Component.Serializer.fromJson(t.getString("CustomName"), v.level().registryAccess()).getString()).toList();
			helper.assertTrue(saved.equals(List.of("Bram", "Tomas", "Dara")) && order.volunteers().isEmpty(), "saved in the order: " + saved);
			helper.assertTrue(order.settlers().get(0).getCompound("VillagerData").getString("profession").equals(
				BuiltInRegistries.VILLAGER_PROFESSION.getKey(ModVillagers.BUILDER).toString()), "Bram's trade: " + order.settlers().get(0).getCompound("VillagerData"));
			helper.assertTrue(Settlers.missing(order).isEmpty() && Settlers.held(order, Settlers.Supply.BEDS) == 3, "the supplies travel with them: " + order.supplies());
			long left = order.arrives() - v.level().getGameTime();
			helper.assertTrue(Settlers.journey(order) == 3600 + 400 && left > 3600 && left <= 4000, "3 minutes and 400 ticks: " + left);
			ChoiceMenu tab = coloniesTab(v, v.player());
			helper.assertTrue(BoardTradeGameTests.lore(tab.icon(Colonies.ON_ROAD)).contains("On the road to Newbrook, there in 4 minutes")
				&& BoardTradeGameTests.lore(tab.icon(Colonies.ON_ROAD)).contains("3 settlers"), "the tab: " + BoardTradeGameTests.lore(tab.icon(Colonies.ON_ROAD)));
			helper.assertTrue(!tab.icon(Settlers.CALL_OFF).is(Items.BARRIER) && !Settlers.callOff(v.level(), v.hall(), v.player()) && v.order() != null,
				"on the road it can't be called off");
			v.entity().setRank(VillageRanks.Rank.CITY); // (opening the hall's screen counted this test's five villagers a Hamlet again)
			ItemStack held = click(v, v.player(), charter(v));
			helper.assertTrue(held.is(ModItems.COLONY_CHARTER) && v.order().equals(order), "a second order while one is on the road");
			helper.assertTrue(Settlers.send(v.level(), v.hall(), v.player(), charter(v)).message().getString().equals(v.name() + " already has a colony on the road."),
				"the refusal: " + Settlers.send(v.level(), v.hall(), v.player(), charter(v)).message().getString());
		});
	}

	/**
	 * Settlers on their own feet: called from six blocks away they walk to the hall, so the bell rings before its time
	 * is up, and then they walk out east, toward the spot.
	 */
	//$ gametest_ticks_batch AREA '400' '"colonyWalk"'
	@GameTest(template = AREA, timeoutTicks = 400, batch = "colonyWalk")
	public void theSettlersWalkToTheHallAndOutTowardTheSpot(GameTestHelper helper) {
		Village v = village(helper, 0);
		Settlers.MUSTER_TICKS = 200;
		Villager dara = villager(helper, new BlockPos(5, 2, 11), "Dara", VillagerProfession.NONE, 1);
		Villager tomas = villager(helper, new BlockPos(11, 2, 5), "Tomas", VillagerProfession.NONE, 1);
		dara.setNoAi(false);
		tomas.setNoAi(false);
		storehouse(helper, allSupplies());
		// the owner stands by the hall and watches: seen, the settlers aren't on the road until they are 40 blocks out
		v.player().setPos(v.hall().getX() + 0.5, v.hall().getY(), v.hall().getZ() + 3.5);
		click(v, v.player(), charter(v));
		helper.assertTrue(v.order() != null && v.order().state().equals(RealmData.MUSTER), "they should be called to the hall: " + v.order());
		long given = v.level().getGameTime();
		helper.succeedWhen(() -> {
			RealmData.Order order = v.order();
			helper.assertTrue(order != null && order.state().equals(RealmData.LEAVING), "the bell hasn't rung: " + (order == null ? null : order.state()));
			helper.assertTrue(order.leaves() - given < 200, "the bell rang because its time was up, not because they had gathered");
			for (Villager settler : List.of(dara, tomas)) {
				helper.assertTrue(settler.isAlive() && settler.getX() >= v.hall().getX() + 4,
					settler.getDisplayName().getString() + " hasn't walked out east yet: " + settler.position() + " (hall " + v.hall() + ")");
			}
		});
	}

	/**
	 * Calling it off on the Colonies tab: only the owner can; the settlers stay, every supply is back in the Storehouse
	 * and the charter, spot and name and all, back with the owner, who can send it again.
	 */
	//$ gametest_ticks_batch AREA '100' '"colonyCallOff"'
	@GameTest(template = AREA, timeoutTicks = 100, batch = "colonyCallOff")
	public void callingItOffPutsEveryoneAndEverythingBack(GameTestHelper helper) {
		Village v = village(helper, -1);
		List<Villager> all = plainVillage(helper);
		ChestBlockEntity chest = storehouse(helper, new ItemStack(Items.OAK_LOG, 64), new ItemStack(Items.SPRUCE_PLANKS, 20), new ItemStack(Items.BREAD, 32),
			new ItemStack(Items.RED_BED), new ItemStack(Items.TORCH, 20));
		click(v, v.player(), charter(v));
		RealmData.Order order = v.order();
		helper.assertTrue(order != null && count(chest, s -> !s.isEmpty()) == 4, "the order should have emptied the chest but for 4 torches: " + order);

		ServerPlayer stranger = ProtectionSpecGameTests.player(helper);
		coloniesTab(v, stranger).press(Settlers.CALL_OFF, stranger);
		helper.assertTrue(order.equals(v.order()) && count(chest, s -> !s.isEmpty()) == 4, "a stranger called the colony off");

		coloniesTab(v, v.player()).press(Settlers.CALL_OFF, v.player());
		helper.assertTrue(v.order() == null, "the order is still there: " + v.order());
		helper.assertTrue(count(chest, s -> s.is(Items.OAK_LOG)) == 64 && count(chest, s -> s.is(Items.SPRUCE_PLANKS)) == 20 && count(chest, s -> s.is(Items.BREAD)) == 32
			&& count(chest, s -> s.is(Items.RED_BED)) == 1 && count(chest, s -> s.is(Items.TORCH)) == 20, "the supplies aren't all back");
		for (Villager stays : all) {
			helper.assertTrue(stays.isAlive() && v.level().getEntity(stays.getUUID()) == stays, stays.getDisplayName().getString() + " is gone");
		}
		ItemStack back = ItemStack.EMPTY;
		for (int i = 0; i < v.player().getInventory().getContainerSize(); i++) {
			if (v.player().getInventory().getItem(i).is(ModItems.COLONY_CHARTER)) {
				back = v.player().getInventory().getItem(i);
			}
		}
		ColonyCharterItem.Charter charter = back.get(ModComponents.COLONY_CHARTER);
		helper.assertTrue(charter != null && charter.hall().equals(v.mother()) && charter.spot().orElseThrow().equals(v.hall().offset(400, 0, 0))
			&& back.getHoverName().getString().equals("Newbrook"), "the charter that came back: " + back + " " + charter);
		helper.assertTrue(!coloniesTab(v, v.player()).icon(Colonies.ON_ROAD).is(Items.LEATHER_BOOTS) && !coloniesTab(v, v.player()).icon(Settlers.CALL_OFF).is(Items.BARRIER),
			"the tab still shows the order");
		// and it can be sent again
		ItemStack again = back.copy();
		v.player().getInventory().clearContent();
		helper.assertTrue(click(v, v.player(), again).isEmpty() && v.order() != null, "the charter that came back sends nobody");
		helper.succeed();
	}

	/**
	 * The order is all in aliveworkplace_realms: written and read back while the settlers get ready and again mid-journey
	 * it is the same order, and a settler saved in it is a whole villager again when loaded (name, trade and level).
	 */
	//$ gametest_ticks_batch AREA '400' '"colonyReload"'
	@GameTest(template = AREA, timeoutTicks = 400, batch = "colonyReload")
	public void theOrderSurvivesSaveAndReload(GameTestHelper helper) {
		Village v = village(helper, 30);
		Settlers.MUSTER_TICKS = 20;
		Settlers.LEAVE_TICKS = 40;
		plainVillage(helper);
		storehouse(helper, allSupplies());
		click(v, v.player(), charter(v));
		RealmData.Order ready = v.order();
		helper.assertTrue(ready != null && ready.state().equals(RealmData.GATHERING) && ready.leaves() > 0 && ready.volunteers().size() == 2,
			"ready, waiting for the gathering: " + ready);
		RealmData early = RealmData.load(v.realms().save(new CompoundTag(), v.level().registryAccess()), v.level().registryAccess());
		helper.assertTrue(ready.equals(early.orderOf(v.mother())), "the order getting ready after a reload: " + early.orders());
		helper.succeedWhen(() -> {
			RealmData.Order order = v.order();
			helper.assertTrue(order != null && order.state().equals(RealmData.ON_ROAD) && order.settlers().size() == 2, "not on the road yet");
			RealmData back = RealmData.load(v.realms().save(new CompoundTag(), v.level().registryAccess()), v.level().registryAccess());
			RealmData.Order read = back.orderOf(v.mother());
			helper.assertTrue(order.equals(read), "the order mid-journey after a reload: " + read);
			helper.assertTrue(read.arrives() == order.arrives() && read.spot().equals(v.hall().offset(400, 0, 0)) && Settlers.missing(read).isEmpty(),
				"its arrival, spot or supplies changed: " + read);
			CompoundTag tag = read.settlers().get(0).copy();
			tag.putString("id", BuiltInRegistries.ENTITY_TYPE.getKey(EntityType.VILLAGER).toString());
			Entity loaded = EntityType.loadEntityRecursive(tag, v.level(), e -> e);
			helper.assertTrue(loaded instanceof Villager bram && bram.getDisplayName().getString().equals("Bram")
				&& bram.getVillagerData().getProfession() == ModVillagers.BUILDER && bram.getVillagerData().getLevel() == 1, "the settler read back: " + loaded);
		});
	}

	/**
	 * Refused, and nothing happens: a charter with no spot, a player who doesn't rule the village, the switch off, and
	 * another village's charter. Each says why, the charter stays in the hand and nobody is chosen.
	 */
	//$ gametest_ticks_batch AREA '100' '"colonyRefused"'
	@GameTest(template = AREA, timeoutTicks = 100, batch = "colonyRefused")
	public void sendingIsRefusedWithoutASpotAnOwnerOrTheSwitch(GameTestHelper helper) {
		Village v = village(helper, -1);
		plainVillage(helper);
		helper.assertTrue(Settlers.volunteers(v.level(), v.hall()) != null, "this village can spare its settlers");

		ItemStack noSpot = ColonyCharterItem.of(v.level(), v.hall());
		ItemStack held = click(v, v.player(), noSpot.copy());
		helper.assertTrue(v.order() == null && held.is(ModItems.COLONY_CHARTER), "sent with no spot chosen");
		helper.assertTrue(Settlers.send(v.level(), v.hall(), v.player(), noSpot).message().getString()
			.equals("Choose the colony's spot first: right-click the air with the charter for its map."), "no spot: "
			+ Settlers.send(v.level(), v.hall(), v.player(), noSpot).message().getString());

		ServerPlayer stranger = ProtectionSpecGameTests.player(helper);
		held = click(v, stranger, charter(v));
		helper.assertTrue(v.order() == null && held.is(ModItems.COLONY_CHARTER), "sent by a stranger");
		helper.assertTrue(Settlers.send(v.level(), v.hall(), stranger, charter(v)).message().getString()
			.equals("Only Owner and their friends can send settlers from " + v.name() + "."), "a stranger: "
			+ Settlers.send(v.level(), v.hall(), stranger, charter(v)).message().getString());

		Colonies.ENABLED = false;
		held = click(v, v.player(), charter(v));
		helper.assertTrue(v.order() == null && held.is(ModItems.COLONY_CHARTER), "sent with colonies switched off");
		helper.assertTrue(Settlers.send(v.level(), v.hall(), v.player(), charter(v)).message().getString().equals("Colonies are switched off on this server."),
			"switched off: " + Settlers.send(v.level(), v.hall(), v.player(), charter(v)).message().getString());
		Colonies.ENABLED = true;

		ItemStack other = ColonyCharterItem.of(v.level(), v.hall().offset(3, 0, 0)); // (no hall there: a village that is gone)
		other.set(ModComponents.COLONY_CHARTER, other.get(ModComponents.COLONY_CHARTER).withSpot(v.hall().offset(400, 0, 0)));
		held = click(v, v.player(), other.copy());
		helper.assertTrue(v.order() == null && held.is(ModItems.COLONY_CHARTER), "sent with another village's charter");
		helper.assertTrue(Settlers.send(v.level(), v.hall(), v.player(), other).message().getString().endsWith("'s: take it to that village's hall."),
			"another village's charter: " + Settlers.send(v.level(), v.hall(), v.player(), other).message().getString());

		// and the same village, its own charter, the owner: sent
		helper.assertTrue(click(v, v.player(), charter(v)).isEmpty() && v.order() != null, "the owner's own charter should send the settlers");
		helper.succeed();
	}

	/**
	 * A village with no Storehouse at all: the settlers wait, everything on the wants, and after the 3 days (shortened
	 * here) they leave with nothing rather than never.
	 */
	//$ gametest_ticks_batch AREA '400' '"colonyShort"'
	@GameTest(template = AREA, timeoutTicks = 400, batch = "colonyShort")
	public void afterThreeDaysTheyLeaveWithWhatThereIs(GameTestHelper helper) {
		Village v = village(helper, 0);
		Settlers.WAIT_TICKS = 60;
		Settlers.MUSTER_TICKS = 20;
		Settlers.LEAVE_TICKS = 40;
		plainVillage(helper);
		click(v, v.player(), charter(v));
		helper.assertTrue(v.order() != null && v.order().supplies().isEmpty() && Settlers.missing(v.order()).size() == Settlers.Supply.values().length,
			"no Storehouse, nothing taken: " + v.order());
		helper.assertTrue(Settlers.wants(v.level(), v.hall()).equals(Map.of(Items.OAK_LOG, 64, Items.OAK_PLANKS, 64, Items.COBBLESTONE, 64, Items.BREAD, 32,
			Items.TORCH, 16, Items.GLASS_PANE, 12, Items.WHITE_BED, 3)), "the wants: " + Settlers.wants(v.level(), v.hall()));
		long given = v.level().getGameTime();
		helper.runAfterDelay(30, () -> helper.assertTrue(v.order().state().equals(RealmData.GATHERING) && v.order().leaves() == 0,
			"they shouldn't leave before the wait is over: " + v.order().state()));
		helper.succeedWhen(() -> {
			RealmData.Order order = v.order();
			helper.assertTrue(order != null && order.state().equals(RealmData.ON_ROAD), "not on the road yet: " + (order == null ? null : order.state()));
			helper.assertTrue(v.level().getGameTime() - given >= 60, "they left before the wait was over");
			helper.assertTrue(order.settlers().size() == 2 && order.supplies().isEmpty() && Settlers.wants(v.level(), v.hall()).isEmpty(),
				"two settlers, no supplies, nothing wanted any more: " + order.supplies());
		});
	}
}
