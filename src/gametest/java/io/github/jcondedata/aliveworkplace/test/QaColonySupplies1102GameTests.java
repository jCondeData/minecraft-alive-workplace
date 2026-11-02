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
import io.github.jcondedata.aliveworkplace.realm.RealmData;
import io.github.jcondedata.aliveworkplace.registry.ModBlocks;
import io.github.jcondedata.aliveworkplace.registry.ModComponents;
import io.github.jcondedata.aliveworkplace.registry.ModVillagers;
import io.github.jcondedata.aliveworkplace.trade.TradePage;
import io.github.jcondedata.aliveworkplace.work.ChoiceMenu;
import java.util.List;
import java.util.Map;
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
import net.minecraft.tags.ItemTags;
import net.minecraft.world.Container;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.npc.VillagerProfession;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.state.properties.ChestType;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

/**
 * QA (lane-c-1102-1853), ROADMAP 33.9's supplies, from the spec: "supplies come out of the Storehouses: 64 logs, 64
 * planks, 64 cobblestone, 32 bread, 16 torches, 12 glass panes and 3 beds. What's missing goes on the village's wants
 * (so partners' caravans bring it) and the requests board ... calling it off ... puts everything back". The parts
 * ColonySettlersGameTests and QaColonyCharterGameTests leave out: supplies split over two Storehouses (and a chest by
 * both), supplies brought later, a Storehouse outside the village, a double chest and a barrel, logs of mixed kinds at
 * exactly 64 and at 63, a call-off into a nearly full chest, and a short order through a save and reload. Each test is
 * alone in its batch, colonies on.
 */
public class QaColonySupplies1102GameTests implements FabricGameTest {
	private static final String AREA = "aliveworkplace_test:big_area";
	private static final BlockPos HALL = new BlockPos(11, 2, 11);
	private static final BlockPos STOREHOUSE = new BlockPos(13, 2, 9);
	private static final BlockPos CHEST = new BlockPos(14, 2, 9);

	private record Village(ServerLevel level, BlockPos hall, VillageHallBlockEntity entity, ServerPlayer player) {
		RealmData realms() {
			return RealmData.get(level.getServer());
		}

		GlobalPos mother() {
			return GlobalPos.of(level.dimension(), hall);
		}

		RealmData.Order order() {
			return realms().orderOf(mother());
		}
	}

	/** A City's hall owned by a survival player, its village 8 blocks round, colonies on, leaving in the morning. */
	private static Village village(GameTestHelper helper) {
		Leftovers.clear(helper);
		Leftovers.halls(helper);
		ServerLevel level = helper.getLevel();
		boolean enabled = Colonies.ENABLED;
		VillageRanks.Rank needed = Colonies.RANK;
		int every = VillageNeeds.CHECK_EVERY;
		int radius = VillageHalls.RADIUS;
		int wait = Settlers.WAIT_TICKS;
		int delay = Settlers.MUSTER_DELAY;
		Colonies.ENABLED = true;
		Colonies.RANK = VillageRanks.Rank.CITY;
		VillageNeeds.CHECK_EVERY = 1_000_000;
		VillageHalls.RADIUS = 8;
		Settlers.MUSTER_DELAY = -1;
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
			RealmData.get(level.getServer()).clearColonies();
			Caravans.Data.get(level).remove(hall);
		});
		ServerPlayer player = ProtectionSpecGameTests.player(helper);
		VillageHallBlockEntity entity = helper.getBlockEntity(HALL);
		entity.setOwner(player.getUUID(), "Owner");
		entity.setRank(VillageRanks.Rank.CITY);
		// two builders (Bram the lower) and two jobless: a village that can spare Bram and Tomas
		villager(helper, new BlockPos(9, 2, 9), "Bram", ModVillagers.BUILDER, 1);
		villager(helper, new BlockPos(9, 2, 13), "Hale", ModVillagers.BUILDER, 3);
		villager(helper, new BlockPos(10, 2, 11), "Tomas", VillagerProfession.NONE, 1);
		villager(helper, new BlockPos(13, 2, 13), "Finn", VillagerProfession.NONE, 1);
		return new Village(level, hall, entity, player);
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

	/** A chest at {@code pos} holding {@code stacks}. */
	private static Container chest(GameTestHelper helper, BlockPos pos, ItemStack... stacks) {
		helper.setBlock(pos, Blocks.CHEST);
		Container chest = helper.getBlockEntity(pos);
		for (int i = 0; i < stacks.length; i++) {
			chest.setItem(i, stacks[i]);
		}
		return chest;
	}

	private static ItemStack[] allSupplies() {
		return new ItemStack[] {new ItemStack(Items.OAK_LOG, 64), new ItemStack(Items.OAK_PLANKS, 64), new ItemStack(Items.COBBLESTONE, 64),
			new ItemStack(Items.BREAD, 32), new ItemStack(Items.TORCH, 16), new ItemStack(Items.GLASS_PANE, 12), new ItemStack(Items.RED_BED),
			new ItemStack(Items.RED_BED), new ItemStack(Items.RED_BED)};
	}

	private static int count(Container chest, Predicate<ItemStack> test) {
		int n = 0;
		for (int i = 0; i < chest.getContainerSize(); i++) {
			if (test.test(chest.getItem(i))) {
				n += chest.getItem(i).getCount();
			}
		}
		return n;
	}

	private static int dropped(Village v, Predicate<ItemStack> test) {
		int n = 0;
		for (ItemEntity e : v.level().getEntitiesOfClass(ItemEntity.class, new AABB(v.hall()).inflate(4))) {
			if (e.isAlive() && test.test(e.getItem())) {
				n += e.getItem().getCount();
			}
		}
		return n;
	}

	private static ItemStack charter(Village v) {
		ItemStack stack = ColonyCharterItem.of(v.level(), v.hall());
		stack.set(ModComponents.COLONY_CHARTER, stack.get(ModComponents.COLONY_CHARTER).withSpot(v.hall().offset(400, 0, 0)));
		stack.set(DataComponents.CUSTOM_NAME, Component.literal("Newbrook"));
		return stack;
	}

	/** The owner right-clicks the hall with the charter; the order it gave. */
	private static RealmData.Order send(GameTestHelper helper, Village v) {
		v.player().setItemInHand(InteractionHand.MAIN_HAND, charter(v));
		v.player().gameMode.useItemOn(v.player(), v.level(), v.player().getItemInHand(InteractionHand.MAIN_HAND), InteractionHand.MAIN_HAND,
			new BlockHitResult(Vec3.atCenterOf(v.hall()), Direction.UP, v.hall(), false));
		RealmData.Order order = v.order();
		helper.assertTrue(order != null && v.player().getItemInHand(InteractionHand.MAIN_HAND).isEmpty(), "the charter gave no order");
		return order;
	}

	private static String held(RealmData.Order order) {
		StringBuilder out = new StringBuilder();
		for (Settlers.Supply s : Settlers.Supply.values()) {
			out.append(s).append('=').append(Settlers.held(order, s)).append(' ');
		}
		return out.toString().trim();
	}

	private static boolean holdsAll(RealmData.Order order) {
		for (Settlers.Supply s : Settlers.Supply.values()) {
			if (Settlers.held(order, s) != s.count) {
				return false;
			}
		}
		return true;
	}

	/**
	 * The supplies spread over the village's two Storehouses (one chest by the first only, one by the second only, one
	 * by both): every one is taken, each exactly as many as asked, the chest by both counted once, and what's over stays.
	 */
	//$ gametest_ticks_batch AREA '100' '"qaSup1102TwoStorehouses"'
	@GameTest(template = AREA, timeoutTicks = 100, batch = "qaSup1102TwoStorehouses")
	public void suppliesSplitOverTwoStorehousesAreAllTakenOnce(GameTestHelper helper) {
		Village v = village(helper);
		helper.setBlock(STOREHOUSE, ModBlocks.STOREHOUSE);
		helper.setBlock(new BlockPos(4, 2, 13), ModBlocks.STOREHOUSE);
		Container first = chest(helper, CHEST, new ItemStack(Items.OAK_LOG, 40), new ItemStack(Items.BREAD, 32), new ItemStack(Items.RED_BED),
			new ItemStack(Items.WHITE_BED));
		Container second = chest(helper, new BlockPos(3, 2, 13), new ItemStack(Items.BIRCH_LOG, 24), new ItemStack(Items.SPRUCE_PLANKS, 64),
			new ItemStack(Items.GLASS_PANE, 12));
		Container both = chest(helper, new BlockPos(8, 2, 9), new ItemStack(Items.COBBLESTONE, 64), new ItemStack(Items.COBBLESTONE, 10),
			new ItemStack(Items.TORCH, 16), new ItemStack(Items.BLUE_BED));
		RealmData.Order order = send(helper, v);
		helper.assertTrue(holdsAll(order) && Settlers.missing(order).isEmpty(), "taken from the two Storehouses: " + held(order));
		helper.assertTrue(first.isEmpty() && second.isEmpty(), "the chests by one Storehouse each should be empty");
		helper.assertTrue(count(both, s -> s.is(Items.COBBLESTONE)) == 10 && count(both, s -> !s.isEmpty()) == 10,
			"the chest by both Storehouses should keep the 10 spare cobblestone and nothing else");
		helper.assertTrue(Settlers.wants(v.level(), v.hall()).isEmpty(), "nothing should be wanted: " + Settlers.wants(v.level(), v.hall()));
		helper.succeed();
	}

	/**
	 * Short of bread and beds when the order is given, both are on the wants; put into the Storehouse's chest later,
	 * they are taken (only what was missing) and leave the wants, and the leaving is set.
	 */
	//$ gametest_ticks_batch AREA '200' '"qaSup1102Later"'
	@GameTest(template = AREA, timeoutTicks = 200, batch = "qaSup1102Later")
	public void suppliesBroughtLaterAreTakenAndLeaveTheWants(GameTestHelper helper) {
		Village v = village(helper);
		helper.setBlock(STOREHOUSE, ModBlocks.STOREHOUSE);
		Container chest = chest(helper, CHEST, new ItemStack(Items.OAK_LOG, 64), new ItemStack(Items.OAK_PLANKS, 64), new ItemStack(Items.COBBLESTONE, 64),
			new ItemStack(Items.BREAD, 30), new ItemStack(Items.TORCH, 16), new ItemStack(Items.GLASS_PANE, 12));
		RealmData.Order order = send(helper, v);
		helper.assertTrue(Settlers.missing(order).equals(Map.of(Settlers.Supply.BREAD, 2, Settlers.Supply.BEDS, 3)), "missing: " + Settlers.missing(order));
		helper.assertTrue(Settlers.wants(v.level(), v.hall()).equals(Map.of(Items.BREAD, 2, Items.WHITE_BED, 3)), "the wants: " + Settlers.wants(v.level(), v.hall()));
		helper.assertTrue(order.leaves() == 0, "short, the leaving shouldn't be set");
		// (the caravans' wants also hold the village's own, such as bread to eat: only the beds are the settlers' alone)
		Caravans.round(v.level(), v.hall(), VillageHalls.census(v.level(), v.hall()));
		List<Caravans.Want> before = Caravans.Data.get(v.level()).village(v.hall()).wants();
		helper.assertTrue(before.contains(new Caravans.Want(Items.WHITE_BED, 3)), "the caravans' wants before: " + before);
		helper.runAfterDelay(30, () -> {
			chest.setItem(0, new ItemStack(Items.BREAD, 7));
			chest.setItem(1, new ItemStack(Items.GREEN_BED));
			chest.setItem(2, new ItemStack(Items.GREEN_BED));
			chest.setItem(3, new ItemStack(Items.YELLOW_BED));
			chest.setItem(4, new ItemStack(Items.YELLOW_BED));
		});
		helper.succeedWhen(() -> {
			RealmData.Order now = v.order();
			helper.assertTrue(now != null && Settlers.missing(now).isEmpty(), "still missing: " + (now == null ? null : Settlers.missing(now)));
			helper.assertTrue(holdsAll(now), "held: " + held(now));
			helper.assertTrue(count(chest, s -> s.is(Items.BREAD)) == 5 && count(chest, s -> s.is(ItemTags.BEDS)) == 1,
				"the chest should keep 5 bread and 1 bed: " + count(chest, s -> s.is(Items.BREAD)) + " bread, " + count(chest, s -> s.is(ItemTags.BEDS)) + " beds");
			helper.assertTrue(Settlers.wants(v.level(), v.hall()).isEmpty(), "still wanted: " + Settlers.wants(v.level(), v.hall()));
			Caravans.round(v.level(), v.hall(), VillageHalls.census(v.level(), v.hall()));
			List<Caravans.Want> wants = Caravans.Data.get(v.level()).village(v.hall()).wants();
			helper.assertTrue(wants.stream().noneMatch(w -> w.item() == Items.WHITE_BED), "the caravans' wants: " + wants);
			helper.assertTrue(now.leaves() > 0, "complete, the leaving should be set");
		});
	}

	/**
	 * A Storehouse outside the village (past the hall's reach), its chest full of supplies, is another place's: nothing
	 * is taken from it and everything is missing.
	 */
	//$ gametest_ticks_batch AREA '100' '"qaSup1102Outside"'
	@GameTest(template = AREA, timeoutTicks = 100, batch = "qaSup1102Outside")
	public void aStorehouseOutsideTheVillageGivesNothing(GameTestHelper helper) {
		Village v = village(helper);
		helper.setBlock(new BlockPos(20, 2, 20), ModBlocks.STOREHOUSE);
		Container chest = chest(helper, new BlockPos(20, 2, 19), allSupplies());
		RealmData.Order order = send(helper, v);
		helper.assertTrue(order.supplies().isEmpty() && Settlers.missing(order).size() == Settlers.Supply.values().length,
			"taken from a Storehouse outside the village: " + held(order));
		helper.assertTrue(count(chest, s -> !s.isEmpty()) == 64 * 3 + 32 + 16 + 12 + 3, "its chest was touched");
		helper.succeed();
	}

	/** A double chest (the logs in one half, the planks in the other) and a barrel by the Storehouse: all taken, exactly. */
	//$ gametest_ticks_batch AREA '100' '"qaSup1102Double"'
	@GameTest(template = AREA, timeoutTicks = 100, batch = "qaSup1102Double")
	public void aDoubleChestAndABarrelByTheStorehouseGiveExactly(GameTestHelper helper) {
		Village v = village(helper);
		helper.setBlock(STOREHOUSE, ModBlocks.STOREHOUSE);
		BlockPos west = new BlockPos(14, 2, 7);
		BlockPos east = new BlockPos(15, 2, 7);
		helper.setBlock(west, Blocks.CHEST.defaultBlockState().setValue(ChestBlock.FACING, Direction.NORTH).setValue(ChestBlock.TYPE, ChestType.LEFT));
		helper.setBlock(east, Blocks.CHEST.defaultBlockState().setValue(ChestBlock.FACING, Direction.NORTH).setValue(ChestBlock.TYPE, ChestType.RIGHT));
		helper.assertTrue(ChestBlock.getConnectedDirection(helper.getBlockState(west)) == Direction.EAST, "setup: the halves aren't one double chest");
		Container a = helper.getBlockEntity(west);
		Container b = helper.getBlockEntity(east);
		a.setItem(0, new ItemStack(Items.OAK_LOG, 63));
		a.setItem(5, new ItemStack(Items.COBBLESTONE, 64));
		b.setItem(0, new ItemStack(Items.OAK_PLANKS, 64));
		b.setItem(1, new ItemStack(Items.OAK_LOG, 3));
		b.setItem(9, new ItemStack(Items.BREAD, 32));
		helper.setBlock(new BlockPos(12, 2, 7), Blocks.BARREL);
		Container barrel = helper.getBlockEntity(new BlockPos(12, 2, 7));
		barrel.setItem(0, new ItemStack(Items.TORCH, 16));
		barrel.setItem(1, new ItemStack(Items.GLASS_PANE, 12));
		barrel.setItem(2, new ItemStack(Items.BLACK_BED));
		barrel.setItem(3, new ItemStack(Items.BLACK_BED));
		barrel.setItem(4, new ItemStack(Items.BLACK_BED));
		barrel.setItem(5, new ItemStack(Items.BLACK_BED));
		RealmData.Order order = send(helper, v);
		helper.assertTrue(holdsAll(order) && Settlers.missing(order).isEmpty(), "taken: " + held(order));
		int logs = count(a, s -> s.is(ItemTags.LOGS)) + count(b, s -> s.is(ItemTags.LOGS));
		helper.assertTrue(logs == 2 && count(a, s -> !s.isEmpty()) + count(b, s -> !s.isEmpty()) == 2, "the double chest should keep 2 logs and nothing else: " + logs);
		helper.assertTrue(count(barrel, s -> s.is(ItemTags.BEDS)) == 1 && count(barrel, s -> !s.isEmpty()) == 1, "the barrel should keep one bed");
		helper.succeed();
	}

	/**
	 * Logs of three kinds summing exactly 64 (and planks of three kinds) are all of them; one fewer (63) is 63 taken and
	 * 1 log on the wants.
	 */
	//$ gametest_ticks_batch AREA '100' '"qaSup1102MixedLogs"'
	@GameTest(template = AREA, timeoutTicks = 100, batch = "qaSup1102MixedLogs")
	public void logsOfMixedKindsCountTowardSixtyFour(GameTestHelper helper) {
		Village v = village(helper);
		helper.setBlock(STOREHOUSE, ModBlocks.STOREHOUSE);
		Container chest = chest(helper, CHEST, new ItemStack(Items.OAK_LOG, 20), new ItemStack(Items.SPRUCE_LOG, 20), new ItemStack(Items.BIRCH_LOG, 23),
			new ItemStack(Items.ACACIA_PLANKS, 30), new ItemStack(Items.CHERRY_PLANKS, 30), new ItemStack(Items.OAK_PLANKS, 4),
			new ItemStack(Items.COBBLESTONE, 64), new ItemStack(Items.BREAD, 32), new ItemStack(Items.TORCH, 16), new ItemStack(Items.GLASS_PANE, 12),
			new ItemStack(Items.RED_BED), new ItemStack(Items.RED_BED), new ItemStack(Items.RED_BED));
		RealmData.Order order = send(helper, v);
		helper.assertTrue(Settlers.held(order, Settlers.Supply.LOGS) == 63 && Settlers.held(order, Settlers.Supply.PLANKS) == 64,
			"63 logs and 64 planks of mixed kinds: " + held(order));
		helper.assertTrue(Settlers.missing(order).equals(Map.of(Settlers.Supply.LOGS, 1)), "missing: " + Settlers.missing(order));
		helper.assertTrue(Settlers.wants(v.level(), v.hall()).equals(Map.of(Items.OAK_LOG, 1)), "the wants: " + Settlers.wants(v.level(), v.hall()));
		helper.assertTrue(chest.isEmpty(), "the chest should be empty");
		// the 64th log, of a fourth kind, and two spare: one is taken
		chest.setItem(0, new ItemStack(Items.DARK_OAK_LOG, 3));
		helper.succeedWhen(() -> {
			RealmData.Order now = v.order();
			helper.assertTrue(now != null && holdsAll(now) && Settlers.missing(now).isEmpty(), "held: " + (now == null ? null : held(now)));
			helper.assertTrue(count(chest, s -> s.is(Items.DARK_OAK_LOG)) == 2, "two dark oak logs should stay: " + count(chest, s -> s.is(Items.DARK_OAK_LOG)));
		});
	}

	/**
	 * Called off when the Storehouse's chest has room for only 2 stacks: 2 stacks go back in it, the rest drops at the
	 * hall, and every supply is there exactly once (nothing lost, nothing doubled).
	 */
	//$ gametest_ticks_batch AREA '100' '"qaSup1102CallOffFull"'
	@GameTest(template = AREA, timeoutTicks = 100, batch = "qaSup1102CallOffFull")
	public void callingItOffIntoANearlyFullChestLosesAndDoublesNothing(GameTestHelper helper) {
		Village v = village(helper);
		helper.setBlock(STOREHOUSE, ModBlocks.STOREHOUSE);
		Container chest = chest(helper, CHEST, allSupplies());
		RealmData.Order order = send(helper, v);
		helper.assertTrue(holdsAll(order) && chest.isEmpty(), "everything should be packed: " + held(order));
		for (int i = 0; i < chest.getContainerSize() - 2; i++) {
			chest.setItem(i, new ItemStack(Items.DIRT, 64));
		}
		ChoiceMenu tab = VillageHallScreen.forTest(v.player(), v.hall());
		tab.press(VillageHallScreen.ROUTES, v.player());
		tab.press(TradePage.Tab.COLONIES.slot(), v.player());
		tab.press(Settlers.CALL_OFF, v.player());
		v.player().teleportTo(v.hall().getX() + 0.5, v.hall().getY(), v.hall().getZ() + 40.5); // (away from what drops)
		helper.assertTrue(v.order() == null, "not called off: " + v.order());
		helper.assertTrue(count(chest, s -> s.is(Items.DIRT)) == 64 * (chest.getContainerSize() - 2), "the dirt in the chest changed");
		helper.assertTrue(count(chest, s -> !s.isEmpty() && !s.is(Items.DIRT)) > 0 && chest.getItem(chest.getContainerSize() - 1).getCount() > 0,
			"the 2 free slots should be filled first");
		Map<String, Predicate<ItemStack>> kinds = Map.of("logs", s -> s.is(ItemTags.LOGS), "planks", s -> s.is(ItemTags.PLANKS),
			"cobblestone", s -> s.is(Items.COBBLESTONE), "bread", s -> s.is(Items.BREAD), "torches", s -> s.is(Items.TORCH),
			"glass panes", s -> s.is(Items.GLASS_PANE), "beds", s -> s.is(ItemTags.BEDS));
		Map<String, Integer> want = Map.of("logs", 64, "planks", 64, "cobblestone", 64, "bread", 32, "torches", 16, "glass panes", 12, "beds", 3);
		StringBuilder wrong = new StringBuilder();
		for (Map.Entry<String, Predicate<ItemStack>> k : kinds.entrySet()) {
			int n = count(chest, k.getValue()) + dropped(v, k.getValue());
			if (n != want.get(k.getKey())) {
				wrong.append(k.getKey()).append(' ').append(n).append("; ");
			}
		}
		helper.assertTrue(wrong.isEmpty(), "back in the chest or at the hall, not as packed: " + wrong);
		helper.assertTrue(dropped(v, s -> !s.isEmpty()) > 0, "with the chest full the rest should drop at the hall");
		helper.succeed();
	}

	/**
	 * A short order read back from a save is the same order (what it took, what it waits for, no leaving set), and the
	 * missing supplies it waits for are still taken once they come.
	 */
	//$ gametest_ticks_batch AREA '200' '"qaSup1102ShortReload"'
	@GameTest(template = AREA, timeoutTicks = 200, batch = "qaSup1102ShortReload")
	public void aShortOrderSurvivesSaveAndReloadAndStillTakes(GameTestHelper helper) {
		Village v = village(helper);
		helper.setBlock(STOREHOUSE, ModBlocks.STOREHOUSE);
		Container chest = chest(helper, CHEST, new ItemStack(Items.JUNGLE_LOG, 50), new ItemStack(Items.OAK_PLANKS, 64), new ItemStack(Items.COBBLESTONE, 64),
			new ItemStack(Items.BREAD, 32), new ItemStack(Items.TORCH, 16), new ItemStack(Items.GLASS_PANE, 12), new ItemStack(Items.PINK_BED));
		RealmData.Order order = send(helper, v);
		RealmData back = RealmData.load(v.realms().save(new CompoundTag(), v.level().registryAccess()), v.level().registryAccess());
		RealmData.Order read = back.orderOf(v.mother());
		helper.assertTrue(order.equals(read), "the short order after a reload: " + read);
		helper.assertTrue(Settlers.missing(read).equals(Map.of(Settlers.Supply.LOGS, 14, Settlers.Supply.BEDS, 2)) && read.leaves() == 0
			&& read.supplies().getOrDefault(Items.JUNGLE_LOG, 0) == 50, "what it holds and waits for after a reload: " + read.supplies() + " " + Settlers.missing(read));
		chest.setItem(0, new ItemStack(Items.JUNGLE_LOG, 14));
		chest.setItem(1, new ItemStack(Items.PINK_BED));
		chest.setItem(2, new ItemStack(Items.PINK_BED));
		helper.succeedWhen(() -> {
			RealmData.Order now = v.order();
			helper.assertTrue(now != null && holdsAll(now) && chest.isEmpty(), "held: " + (now == null ? null : held(now)));
		});
	}
}
