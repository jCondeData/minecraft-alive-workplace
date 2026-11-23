package io.github.jcondedata.aliveworkplace.test;

import io.github.jcondedata.aliveworkplace.blueprint.BlueprintData;
import io.github.jcondedata.aliveworkplace.blueprint.StarterBlueprints;
import io.github.jcondedata.aliveworkplace.build.BuildSite;
import io.github.jcondedata.aliveworkplace.build.BuildSiteManager;
import io.github.jcondedata.aliveworkplace.build.BuilderJob;
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
import io.github.jcondedata.aliveworkplace.registry.ModVillagers;
import io.github.jcondedata.aliveworkplace.trade.TradePage;
import io.github.jcondedata.aliveworkplace.work.ChoiceMenu;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.npc.VillagerProfession;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

/**
 * QA (lane-c-1123-0953), written from docs/wiki/colony-settlers.md (33.9) for what the other settler tests leave open:
 * a builder in the middle of a build stays home and the next builder (or a jobless grown-up) goes; a player 47.5 blocks
 * off keeps the settlers in sight until the half minute is up, one at 48.5 lets them take to the road at once; the
 * call-off button's line for the owner and for a stranger, and the stranger's sentence when he clicks it; "Too late"
 * from a Colonies tab left open while they leave; three settlers named "Tomas, Dara and Flo" in the sending and the
 * bell; and the 3 days' wait ending neither early nor late. Colonies are off in GameTests, so every test turns them on,
 * alone in its batch, and puts everything back.
 */
public class QaColonySettlers1123cGameTests implements FabricGameTest {
	private static final String AREA = "aliveworkplace_test:big_area";
	private static final BlockPos HALL = new BlockPos(11, 2, 11);
	private static final BlockPos STOREHOUSE = new BlockPos(13, 2, 9);
	private static final BlockPos CHEST = new BlockPos(14, 2, 9);

	private record Village(ServerLevel level, BlockPos hall, VillageHallBlockEntity entity, ServerPlayer player, String name, List<Component> heard) {
		GlobalPos mother() {
			return GlobalPos.of(level.dimension(), hall);
		}

		RealmData.Order order() {
			return RealmData.get(level.getServer()).orderOf(mother());
		}

		boolean heard(String line) {
			return heard.stream().anyMatch(c -> c.getString().equals(line));
		}

		List<String> lines() {
			return heard.stream().map(Component::getString).toList();
		}
	}

	/** A City's hall owned by a listening survival player at the hall, village 8 blocks round, colonies on; all put back after. */
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
		AABB box = new AABB(hall).inflate(24);
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
			for (Villager x : level.getEntitiesOfClass(Villager.class, box)) {
				x.discard();
			}
		});
		List<Component> heard = new ArrayList<>();
		ServerPlayer player = RealmGameTests.listener(helper, heard, "qa-settlers-owner");
		player.setPos(hall.getX() + 0.5, hall.getY(), hall.getZ() + 3.5);
		VillageHallBlockEntity entity = helper.getBlockEntity(HALL);
		entity.setOwner(player.getUUID(), "Owner");
		entity.setRank(VillageRanks.Rank.CITY);
		return new Village(level, hall, entity, player, VillageHalls.name(level, hall).getString(), heard);
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

	/** Two builders (Bram 1, Hale 3) and two jobless grown-ups (Tomas, Finn): a village that can spare two. */
	private static List<Villager> twoBesideTheHall(GameTestHelper helper) {
		return List.of(villager(helper, new BlockPos(9, 2, 9), "Bram", ModVillagers.BUILDER, 1),
			villager(helper, new BlockPos(9, 2, 13), "Hale", ModVillagers.BUILDER, 3),
			villager(helper, new BlockPos(10, 2, 11), "Tomas", VillagerProfession.NONE, 1),
			villager(helper, new BlockPos(13, 2, 13), "Finn", VillagerProfession.NONE, 1));
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

	/** Everything but the beds. */
	private static ItemStack[] noBeds() {
		return new ItemStack[] {new ItemStack(Items.OAK_LOG, 64), new ItemStack(Items.SPRUCE_PLANKS, 64), new ItemStack(Items.COBBLESTONE, 64),
			new ItemStack(Items.BREAD, 32), new ItemStack(Items.TORCH, 16), new ItemStack(Items.GLASS_PANE, 12)};
	}

	private static ItemStack charter(Village v) {
		ItemStack stack = ColonyCharterItem.of(v.level(), v.hall());
		stack.set(ModComponents.COLONY_CHARTER, stack.get(ModComponents.COLONY_CHARTER).withSpot(v.hall().offset(400, 0, 0)));
		stack.set(DataComponents.CUSTOM_NAME, Component.literal("Newbrook"));
		return stack;
	}

	/** The player's own right-click on the hall with the charter in the main hand. */
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

	private static String state(RealmData.Order order) {
		return order == null ? "no order" : order.state();
	}

	private static BuildSite busy(GameTestHelper helper, Villager builder, int east) {
		BuildSiteManager sites = BuildSiteManager.get(helper.getLevel());
		BuildSite site = sites.create(UUID.randomUUID(), "Jesse", StarterBlueprints.WELL.id(), new BlueprintData.Placement(helper.getLevel().dimension().location(),
			helper.absolutePos(HALL).offset(200 + east, 0, 0), Rotation.NONE, Mirror.NONE));
		Leftovers.after(helper, () -> BuildSiteManager.get(helper.getLevel()).remove(site.id()));
		ModAttachments.BUILDER_JOB.set(builder, new BuilderJob(site.id()));
		return site;
	}

	// ---- who volunteers ---------------------------------------------------------------------------------------------

	/**
	 * "A builder in the middle of a build doesn't volunteer; the next builder or a jobless grown-up goes instead." Bram,
	 * the lower of two builders, is on a build: Hale goes. Both on a build: a jobless grown-up is the colony's builder and
	 * neither builder goes. The order the owner's click gives names them.
	 */
	//$ gametest_ticks_batch AREA '100' '"qaCs1123cMidBuild"'
	@GameTest(template = AREA, timeoutTicks = 100, batch = "qaCs1123cMidBuild")
	public void qaABuilderMidBuildStaysHomeAndTheNextGoes(GameTestHelper helper) {
		Village v = village(helper, -1);
		List<Villager> all = twoBesideTheHall(helper);
		Villager bram = all.get(0);
		Villager hale = all.get(1);
		storehouse(helper, allSupplies());
		busy(helper, bram, 0);
		Settlers.Volunteers first = Settlers.volunteers(v.level(), v.hall());
		helper.assertTrue(first != null && first.builder() == hale && !first.all().contains(bram),
			"Bram is mid-build, Hale should be the colony's builder: " + (first == null ? null : first.all().stream().map(x -> x.getDisplayName().getString()).toList()));

		busy(helper, hale, 50);
		Settlers.Volunteers second = Settlers.volunteers(v.level(), v.hall());
		helper.assertTrue(second != null && second.builder().getVillagerData().getProfession() == VillagerProfession.NONE
			&& !second.all().contains(bram) && !second.all().contains(hale),
			"both builders mid-build: a jobless grown-up should be the builder: " + (second == null ? null : second.all().stream().map(x -> x.getDisplayName().getString()).toList()));

		ItemStack held = click(v, v.player(), charter(v));
		RealmData.Order order = v.order();
		helper.assertTrue(order != null && held.isEmpty(), "the owner's click gave no order: " + v.lines());
		helper.assertTrue(!order.volunteers().contains(bram.getUUID()) && !order.volunteers().contains(hale.getUUID())
			&& order.builder().isPresent() && !order.builder().get().equals(bram.getUUID()) && !order.builder().get().equals(hale.getUUID()),
			"a builder mid-build went: " + order.volunteers());
		helper.succeed();
	}

	// ---- leaving: "once nobody can see them (no player within 48 blocks ...), or after 30 seconds" -----------------

	/** The two settlers beside the hall (NoAi: they never get 40 blocks out); the owner moved {@code dz} blocks south. */
	private static Village leaving(GameTestHelper helper, double dz) {
		Village v = village(helper, 0);
		Settlers.MUSTER_TICKS = 300;
		Settlers.LEAVE_TICKS = 60;
		villager(helper, new BlockPos(10, 2, 11), "Tomas", VillagerProfession.NONE, 1);
		villager(helper, new BlockPos(12, 2, 11), "Dara", VillagerProfession.NONE, 1);
		storehouse(helper, allSupplies());
		click(v, v.player(), charter(v));
		helper.assertTrue(v.order() != null && v.order().state().equals(RealmData.MUSTER), "they should be called to the hall at once: " + state(v.order()) + " " + v.lines());
		v.player().setPos(v.hall().getX() + 0.5, v.hall().getY(), v.hall().getZ() + 0.5 + dz);
		return v;
	}

	/**
	 * A player 47.5 blocks south of the hall (47.5 to 47.6 blocks from each settler) can see them: they stay in the world
	 * until the half minute (LEAVE_TICKS, 60 here) is up, then they are on the road anyway.
	 */
	//$ gametest_ticks_batch AREA '300' '"qaCs1123cSeen"'
	@GameTest(template = AREA, timeoutTicks = 300, batch = "qaCs1123cSeen")
	public void qaAPlayerJustWithinSightKeepsThemUntilTheTimeIsUp(GameTestHelper helper) {
		Village v = leaving(helper, 47.5);
		long[] bell = {-1};
		long[] road = {-1};
		helper.onEachTick(() -> {
			RealmData.Order o = v.order();
			long now = v.level().getGameTime();
			if (o != null && o.state().equals(RealmData.LEAVING) && bell[0] < 0) {
				bell[0] = now;
			}
			if (o != null && o.state().equals(RealmData.ON_ROAD) && road[0] < 0) {
				road[0] = now;
			}
		});
		helper.succeedWhen(() -> {
			helper.assertTrue(bell[0] >= 0, "the bell hasn't rung: " + state(v.order()));
			helper.assertTrue(road[0] >= 0, "not on the road yet: " + state(v.order()));
			helper.assertTrue(road[0] - bell[0] >= 59, "seen from 47.5 blocks, they took to the road " + (road[0] - bell[0])
				+ " ticks after the bell, before the half minute (60) was up");
			helper.assertTrue(road[0] - bell[0] <= 62, "they waited " + (road[0] - bell[0]) + " ticks, past the half minute (60)");
		});
	}

	/** A player 48.5 blocks south (just over 48 from each settler) can't see them: on the road right after the bell. */
	//$ gametest_ticks_batch AREA '300' '"qaCs1123cUnseen"'
	@GameTest(template = AREA, timeoutTicks = 300, batch = "qaCs1123cUnseen")
	public void qaAPlayerJustOutOfSightLetsThemGoAtOnce(GameTestHelper helper) {
		Village v = leaving(helper, 48.5);
		long[] bell = {-1};
		long[] road = {-1};
		helper.onEachTick(() -> {
			RealmData.Order o = v.order();
			long now = v.level().getGameTime();
			if (o != null && o.state().equals(RealmData.LEAVING) && bell[0] < 0) {
				bell[0] = now;
			}
			if (o != null && o.state().equals(RealmData.ON_ROAD) && road[0] < 0) {
				road[0] = now;
				if (bell[0] < 0) {
					bell[0] = now;
				}
			}
		});
		helper.succeedWhen(() -> {
			helper.assertTrue(road[0] >= 0, "not on the road yet: " + state(v.order()));
			helper.assertTrue(road[0] - bell[0] <= 3, "nobody within 48 blocks, yet they waited " + (road[0] - bell[0]) + " ticks after the bell");
			helper.assertTrue(v.order().settlers().size() == 2, "on the road with " + v.order().settlers().size() + " settlers");
		});
	}

	// ---- the call-off button ----------------------------------------------------------------------------------------

	/**
	 * "A Call the colony off button stands beside it (the owner and friends only)": the owner's says what a click does; a
	 * stranger's says who may, and his click is refused with its sentence, word for word, and changes nothing.
	 */
	//$ gametest_ticks_batch AREA '100' '"qaCs1123cStranger"'
	@GameTest(template = AREA, timeoutTicks = 100, batch = "qaCs1123cStranger")
	public void qaAStrangersCallOffButtonSaysWhoMayAndRefuses(GameTestHelper helper) {
		Village v = village(helper, -1);
		twoBesideTheHall(helper);
		ChestBlockEntity chest = storehouse(helper, noBeds());
		click(v, v.player(), charter(v));
		RealmData.Order order = v.order();
		helper.assertTrue(order != null && order.state().equals(RealmData.GATHERING), "not sent: " + v.lines());

		ItemStack mine = coloniesTab(v, v.player()).icon(Settlers.CALL_OFF);
		helper.assertTrue(mine.getHoverName().getString().equals("Call the colony off")
			&& BoardTradeGameTests.lore(mine).contains("Click: the settlers stay, supplies and charter come back"),
			"the owner's button: " + mine.getHoverName().getString() + " " + BoardTradeGameTests.lore(mine));

		List<Component> strangerHeard = new ArrayList<>();
		ServerPlayer stranger = RealmGameTests.listener(helper, strangerHeard, "qa-settlers-stranger");
		stranger.setPos(v.hall().getX() + 2.5, v.hall().getY(), v.hall().getZ() + 0.5);
		ChoiceMenu tab = coloniesTab(v, stranger);
		ItemStack theirs = tab.icon(Settlers.CALL_OFF);
		helper.assertTrue(BoardTradeGameTests.lore(theirs).contains("Only Owner and their friends can call it off"),
			"the stranger's button: " + theirs.getHoverName().getString() + " " + BoardTradeGameTests.lore(theirs));
		tab.press(Settlers.CALL_OFF, stranger);
		List<String> said = strangerHeard.stream().map(Component::getString).toList();
		helper.assertTrue(said.contains("Only Owner and their friends can call off a colony of " + v.name() + "."), "the stranger was told: " + said);
		helper.assertTrue(order.equals(v.order()), "a stranger's click changed the order: " + v.order());
		helper.assertTrue(chest.isEmpty() || chest.getItem(0).isEmpty(), "the supplies came back on a stranger's click");
		helper.assertTrue(stranger.getInventory().countItem(io.github.jcondedata.aliveworkplace.registry.ModItems.COLONY_CHARTER) == 0,
			"the stranger got the charter");
		helper.succeed();
	}

	/**
	 * "Calling off is possible ... not once the bell has rung": the owner opens the Colonies tab while they get ready and
	 * leaves it open; once they are on the road his click on the old button says "Too late: the settlers have left for
	 * Newbrook." and the order stays on the road.
	 */
	//$ gametest_ticks_batch AREA '300' '"qaCs1123cTooLate"'
	@GameTest(template = AREA, timeoutTicks = 300, batch = "qaCs1123cTooLate")
	public void qaATabLeftOpenSaysTooLateOnceTheyAreOnTheRoad(GameTestHelper helper) {
		Village v = village(helper, 0);
		Settlers.MUSTER_TICKS = 300;
		Settlers.LEAVE_TICKS = 20;
		villager(helper, new BlockPos(10, 2, 11), "Tomas", VillagerProfession.NONE, 1);
		villager(helper, new BlockPos(12, 2, 11), "Dara", VillagerProfession.NONE, 1);
		storehouse(helper, allSupplies());
		click(v, v.player(), charter(v));
		helper.assertTrue(v.order() != null && !v.order().gone(), "not sent: " + v.lines());
		ChoiceMenu open = coloniesTab(v, v.player());
		helper.assertTrue(open.icon(Settlers.CALL_OFF).getHoverName().getString().equals("Call the colony off"), "no call-off while they get ready");
		boolean[] pressed = {false};
		helper.succeedWhen(() -> {
			RealmData.Order order = v.order();
			helper.assertTrue(order != null && order.state().equals(RealmData.ON_ROAD), "not on the road yet: " + state(order));
			if (!pressed[0]) {
				pressed[0] = true;
				open.press(Settlers.CALL_OFF, v.player());
			}
			helper.assertTrue(v.heard("Too late: the settlers have left for Newbrook."), "the late click said: " + v.lines());
			helper.assertTrue(v.order() != null && v.order().gone() && v.order().settlers().size() == 2, "the order on the road was undone: " + state(v.order()));
			helper.assertTrue(v.player().getInventory().countItem(io.github.jcondedata.aliveworkplace.registry.ModItems.COLONY_CHARTER) == 0,
				"the charter came back though they had left");
		});
	}

	// ---- the sentences with three settlers ---------------------------------------------------------------------------

	/**
	 * "A married volunteer brings their partner, so a couple makes three or four settlers": Tomas (jobless, the builder to
	 * be), his wife Dara and Flo, the lowest of three farmers. The owner reads "Tomas, Dara and Flo will set out for
	 * Newbrook. Their supplies are packed: they leave in the morning." and, at the bell, "The bell rings: Tomas, Dara and
	 * Flo set out from <village> for Newbrook."
	 */
	//$ gametest_ticks_batch AREA '200' '"qaCs1123cThree"'
	@GameTest(template = AREA, timeoutTicks = 200, batch = "qaCs1123cThree")
	public void qaThreeSettlersAreNamedWithACommaAndAnAnd(GameTestHelper helper) {
		Village v = village(helper, 0);
		Settlers.MUSTER_TICKS = 300;
		Settlers.LEAVE_TICKS = 20;
		Villager tomas = villager(helper, new BlockPos(10, 2, 11), "Tomas", VillagerProfession.NONE, 1);
		Villager dara = villager(helper, new BlockPos(12, 2, 11), "Dara", VillagerProfession.NONE, 1);
		marry(tomas, dara);
		villager(helper, new BlockPos(11, 2, 10), "Flo", VillagerProfession.FARMER, 1);
		villager(helper, new BlockPos(14, 2, 7), "Fern", VillagerProfession.FARMER, 2);
		villager(helper, new BlockPos(15, 2, 7), "Fay", VillagerProfession.FARMER, 3);
		storehouse(helper, allSupplies());
		click(v, v.player(), charter(v));
		helper.assertTrue(v.order() != null, "not sent: " + v.lines());
		// (the couple comes in the order the hall finds them: Tomas and his wife, or Dara and her husband)
		String three = v.heard("Tomas, Dara and Flo will set out for Newbrook. Their supplies are packed: they leave in the morning.") ? "Tomas, Dara and Flo"
			: "Dara, Tomas and Flo";
		helper.assertTrue(v.heard(three + " will set out for Newbrook. Their supplies are packed: they leave in the morning."), "the sending said: " + v.lines());
		helper.succeedWhen(() -> helper.assertTrue(v.heard("The bell rings: " + three + " set out from " + v.name() + " for Newbrook."),
			"the bell said: " + v.lines()));
	}

	// ---- the 3 days -------------------------------------------------------------------------------------------------

	/**
	 * "After 3 days they stop waiting and leave with what there is": short of the beds, they still get ready one second
	 * before the wait (WAIT_TICKS, 100 here) is up, and are called to the hall within the second after, with no bed.
	 */
	//$ gametest_ticks_batch AREA '300' '"qaCs1123cThreeDays"'
	@GameTest(template = AREA, timeoutTicks = 300, batch = "qaCs1123cThreeDays")
	public void qaTheWaitEndsNeitherEarlyNorLate(GameTestHelper helper) {
		Village v = village(helper, 0);
		Settlers.WAIT_TICKS = 100;
		Settlers.MUSTER_TICKS = 300;
		Settlers.LEAVE_TICKS = 600;
		twoBesideTheHall(helper);
		storehouse(helper, noBeds());
		click(v, v.player(), charter(v));
		RealmData.Order sent = v.order();
		helper.assertTrue(sent != null && sent.state().equals(RealmData.GATHERING) && sent.leaves() == 0, "short of beds they should wait: " + state(sent));
		long ordered = sent.ordered();
		long[] moved = {-1};
		helper.onEachTick(() -> {
			RealmData.Order o = v.order();
			if (o != null && moved[0] < 0 && !(o.state().equals(RealmData.GATHERING) && o.leaves() == 0)) {
				moved[0] = v.level().getGameTime();
			}
		});
		helper.succeedWhen(() -> {
			helper.assertTrue(moved[0] >= 0, "still waiting " + (v.level().getGameTime() - ordered) + " ticks after the order: " + state(v.order()));
			long after = moved[0] - ordered;
			helper.assertTrue(after >= 100, "they stopped waiting " + after + " ticks after the order, before the 100 were up");
			helper.assertTrue(after <= 121, "they stopped waiting " + after + " ticks after the order, more than a second late");
			RealmData.Order o = v.order();
			helper.assertTrue(o != null && !o.state().equals(RealmData.GATHERING), "not called to the hall: " + state(o));
			helper.assertTrue(Settlers.held(o, Settlers.Supply.BEDS) == 0 && Settlers.held(o, Settlers.Supply.LOGS) == 64,
				"they should leave with what there is: " + o.supplies());
		});
	}
}
