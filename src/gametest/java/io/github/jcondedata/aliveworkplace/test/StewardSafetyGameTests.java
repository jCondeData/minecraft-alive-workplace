package io.github.jcondedata.aliveworkplace.test;

import io.github.jcondedata.aliveworkplace.AliveWorkplace;
import io.github.jcondedata.aliveworkplace.blueprint.BlueprintData;
import io.github.jcondedata.aliveworkplace.build.BuildSite;
import io.github.jcondedata.aliveworkplace.build.BuildSiteManager;
import io.github.jcondedata.aliveworkplace.build.Builders;
import io.github.jcondedata.aliveworkplace.city.CityPlan;
import io.github.jcondedata.aliveworkplace.city.CityPlanItem;
import io.github.jcondedata.aliveworkplace.city.PlayerBuilt;
import io.github.jcondedata.aliveworkplace.city.Plots;
import io.github.jcondedata.aliveworkplace.city.StewardDesk;
import io.github.jcondedata.aliveworkplace.city.StewardRules;
import io.github.jcondedata.aliveworkplace.city.StewardSafety;
import io.github.jcondedata.aliveworkplace.city.StewardWishes;
import io.github.jcondedata.aliveworkplace.city.Stewards;
import io.github.jcondedata.aliveworkplace.hall.StewardDeskPage;
import io.github.jcondedata.aliveworkplace.hall.VillageHallBlockEntity;
import io.github.jcondedata.aliveworkplace.hall.VillageHallScreen;
import io.github.jcondedata.aliveworkplace.registry.ModBlocks;
import io.github.jcondedata.aliveworkplace.registry.ModGameRules;
import io.github.jcondedata.aliveworkplace.registry.ModItems;
import io.github.jcondedata.aliveworkplace.store.StorehouseBoard;
import io.github.jcondedata.aliveworkplace.work.ChoiceMenu;
import java.util.ArrayList;
import java.util.BitSet;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponents;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestGenerator;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.gametest.framework.TestFunction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

/**
 * ROADMAP 27.19, safe by design. A village on flat grass (hall, Blueprint Table and its builder, a Steward appointed by
 * the hall's owner, a Homes zone east of the hall), and the Well the desk tests propose. Blocks a player places go
 * through the block item itself, so the ledger hears of them as it would in play.
 */
public class StewardSafetyGameTests implements net.fabricmc.fabric.api.gametest.v1.FabricGameTest {
	private static final String AREA = "aliveworkplace_test:huge_area";
	private static final BlockPos HALL = new BlockPos(2, 2, 14);
	private static final BlockPos TABLE = new BlockPos(2, 2, 10);
	private static final ResourceLocation WELL = AliveWorkplace.id("well");
	private static final ResourceLocation RULE = AliveWorkplace.id("test/safety_well");
	private static final BlockPos SPOT = new BlockPos(11, 2, 13);
	static final long[] SEEDS = {27_191L, 27_192L, 27_193L};

	private record Village(ServerLevel level, BlockPos hall, ServerPlayer owner, Villager steward, Villager builder) {
		VillageHallBlockEntity entity() {
			return (VillageHallBlockEntity) level.getBlockEntity(hall);
		}
	}

	private static Village village(GameTestHelper helper) {
		Leftovers.clear(helper);
		ServerLevel level = helper.getLevel();
		BlockPos a = helper.absolutePos(BlockPos.ZERO);
		BoundingBox near = new BoundingBox(a.getX() - 40, level.getMinBuildHeight(), a.getZ() - 40, a.getX() + 70, level.getMaxBuildHeight(), a.getZ() + 70);
		BuildSiteManager manager = BuildSiteManager.get(level);
		for (BuildSite site : new ArrayList<>(manager.all())) {
			if (near.isInside(site.placement().origin())) {
				manager.remove(site.id());
			}
		}
		for (BuildSiteManager.Finished f : manager.finishedIn(level)) {
			if (near.isInside(f.placement().origin())) {
				manager.forgetFinished(f.placement());
			}
		}
		PlayerBuilt.get(level).forget(near);
		for (int x = 0; x < 30; x++) {
			for (int z = 0; z < 30; z++) {
				helper.setBlock(new BlockPos(x, 0, z), Blocks.DIRT);
				helper.setBlock(new BlockPos(x, 1, z), Blocks.GRASS_BLOCK);
				for (int y = 2; y < 12; y++) {
					helper.setBlock(new BlockPos(x, y, z), Blocks.AIR);
				}
			}
		}
		helper.setBlock(HALL, ModBlocks.VILLAGE_HALL);
		helper.setBlock(TABLE, ModBlocks.BLUEPRINT_TABLE);
		BlockPos hall = helper.absolutePos(HALL);
		ServerPlayer owner = helper.makeMockServerPlayerInLevel();
		VillageHallBlockEntity entity = (VillageHallBlockEntity) level.getBlockEntity(hall);
		entity.setOwner(owner.getUUID(), owner.getGameProfile().getName());
		BitSet cells = new BitSet();
		for (int x = 6; x <= 29; x++) {
			for (int z = 2; z <= 25; z++) {
				cells.set(CityPlan.cellAt(hall, helper.absolutePos(new BlockPos(x, 2, z))));
			}
		}
		entity.setPlan(CityPlan.EMPTY.addZone("homes", "Homes 1", "").paint(0, cells));
		Villager builder = helper.spawn(EntityType.VILLAGER, TABLE.east());
		Builders.employ(level, builder, helper.absolutePos(TABLE));
		Villager steward = StewardGameTests.seasoned(helper.spawn(EntityType.VILLAGER, HALL.south(2)));
		ItemStack plan = new ItemStack(ModItems.CITY_PLAN);
		CityPlanItem.bind(level, owner, plan, hall);
		helper.assertTrue(Stewards.appoint(owner, steward, plan) == InteractionResult.SUCCESS, "setup: Steward not appointed");
		return new Village(level, hall, owner, steward, builder);
	}

	private static StewardWishes.Wish wish() {
		return new StewardWishes.Wish(RULE, new StewardRules.Effect(StewardRules.Kind.BUILD, Optional.of(WELL), Optional.of("homes"),
			Optional.empty(), Optional.empty()), 50, "steward.aliveworkplace.why.beds", List.of(3L));
	}

	private static Plots.Verdict checkSpot(GameTestHelper helper, Village v) {
		return Plots.check(v.level(), v.hall(), v.entity().plan(), "homes", WELL, helper.absolutePos(SPOT), Rotation.COUNTERCLOCKWISE_90, Mirror.NONE);
	}

	private static StewardDesk.Proposal propose(GameTestHelper helper, Village v) {
		Plots.Verdict verdict = checkSpot(helper, v);
		helper.assertTrue(verdict.plot().isPresent(), "setup: the spot doesn't fit: " + verdict);
		Plots.Plot plot = verdict.plot().get();
		Optional<StewardDesk.Proposal> p = StewardDesk.offer(v.level(), v.hall(), wish(), plot.blueprint(), plot.placement(), plot.zone(), false);
		helper.assertTrue(p.isPresent(), "setup: not proposed");
		return p.get();
	}

	/** A player places {@code block} at {@code at} (absolute) the way a click does: through the block item. */
	static boolean place(GameTestHelper helper, ServerPlayer player, BlockPos at, Block block) {
		ItemStack stack = new ItemStack(block.asItem());
		player.setItemInHand(InteractionHand.MAIN_HAND, stack);
		BlockPlaceContext context = new BlockPlaceContext(player, InteractionHand.MAIN_HAND, stack,
			new BlockHitResult(Vec3.atCenterOf(at), Direction.UP, at, false));
		return ((BlockItem) block.asItem()).place(context).consumesAction();
	}

	private static String lore(ItemStack stack) {
		return String.valueOf(stack.get(DataComponents.LORE));
	}

	/**
	 * A player built a little house in the Homes zone and took it down again: the ledger keeps the spot (no plot there,
	 * the Steward doesn't approve his own proposal there), while the owner approving it by hand overrides the ledger.
	 */
	//$ gametest_ticks_batch AREA '40' '"safetyHouse"'
	@GameTest(template = AREA, timeoutTicks = 40, batch = "safetyHouse")
	public void noPlanThroughAPlayersHouseInHomes(GameTestHelper helper) {
		Village v = village(helper);
		StewardDesk.Proposal proposal = propose(helper, v); // fits while nobody has built there
		BlockPos wall = SPOT.above();
		helper.assertTrue(place(helper, v.owner(), helper.absolutePos(wall), Blocks.OAK_PLANKS), "setup: the player's plank wasn't placed");
		helper.assertTrue(PlayerBuilt.get(v.level()).marked(helper.absolutePos(wall)), "the ledger didn't mark the player's block");
		Plots.Verdict verdict = checkSpot(helper, v);
		helper.assertTrue(verdict.plot().isEmpty(), "a plot through the player's house: " + verdict);
		helper.setBlock(wall, Blocks.AIR); // the house taken down: the ground is natural again, the ledger remembers
		verdict = checkSpot(helper, v);
		helper.assertTrue(verdict.reason().equals(Optional.of(Plots.Reason.UNSAFE)), "the ledger didn't keep the spot: " + verdict);
		helper.assertTrue(StewardDesk.approve(v.level(), v.hall(), null, proposal.id()) == StewardDesk.Outcome.UNSAFE,
			"the Steward approved his own plan through a player's build");
		helper.assertTrue(StewardDesk.approve(v.level(), v.hall(), v.owner(), proposal.id()).ok(), "the owner's approval by hand was refused");
		helper.succeed();
	}

	/** A second village's hall nearer the spot than ours: no plot reaching into it, and no wall piece or self-approval either. */
	//$ gametest_ticks_batch AREA '40' '"safetyNeighbour"'
	@GameTest(template = AREA, timeoutTicks = 40, batch = "safetyNeighbour")
	public void noProposalReachesIntoTheNextVillage(GameTestHelper helper) {
		Village v = village(helper);
		helper.assertTrue(checkSpot(helper, v).plot().isPresent(), "setup: the spot doesn't fit with one village");
		helper.setBlock(new BlockPos(18, 2, 13), ModBlocks.VILLAGE_HALL);
		Plots.Verdict verdict = checkSpot(helper, v);
		helper.assertTrue(verdict.reason().equals(Optional.of(Plots.Reason.UNSAFE)), "a plot reaching into the next village: " + verdict);
		BoundingBox box = new BoundingBox(helper.absolutePos(new BlockPos(14, 2, 11)).getX(), helper.absolutePos(SPOT).getY(),
			helper.absolutePos(new BlockPos(14, 2, 11)).getZ(), helper.absolutePos(new BlockPos(16, 2, 15)).getX(), helper.absolutePos(SPOT).getY() + 3,
			helper.absolutePos(new BlockPos(16, 2, 15)).getZ());
		helper.assertTrue(StewardSafety.check(v.level(), v.hall(), box, true).equals(Optional.of(StewardSafety.Refusal.OTHER_VILLAGE)),
			"by hand or not, the next village is not his: " + StewardSafety.check(v.level(), v.hall(), box, true));
		helper.succeed();
	}

	/** Two waiting builds: one shopping list adding up both, on the desk and the Storehouse's requests board. */
	//$ gametest_ticks_batch AREA '40' '"safetyShopping"'
	@GameTest(template = AREA, timeoutTicks = 40, batch = "safetyShopping")
	public void theShoppingListAddsUpTwoWaitingSites(GameTestHelper helper) {
		Village v = village(helper);
		List<BuildSite> sites = waitingSites(helper, v, Map.of(Items.GLASS, 40, Items.OAK_PLANKS, 12), Map.of(Items.GLASS, 10, Items.STONE_BRICKS, 5));
		List<Map.Entry<Item, Integer>> list = StewardSafety.shoppingList(v.level(), v.hall());
		helper.assertTrue(list.size() == 3, "expected 3 materials, got " + list);
		helper.assertTrue(list.get(0).getKey() == Items.GLASS && list.get(0).getValue() == 50, "glass not added up first: " + list);
		helper.assertTrue(list.get(1).getKey() == Items.OAK_PLANKS && list.get(1).getValue() == 12, "planks: " + list);
		helper.assertTrue(list.get(2).getKey() == Items.STONE_BRICKS && list.get(2).getValue() == 5, "stone bricks: " + list);
		ChoiceMenu menu = VillageHallScreen.forTest(v.owner(), v.hall());
		menu.press(VillageHallScreen.ADVICE, v.owner());
		String head = lore(menu.icon(StewardDeskPage.STEWARD));
		helper.assertTrue(head.contains("screen.aliveworkplace.desk.shopping") || head.contains("Shopping list"), "no shopping list on the desk: " + head);
		helper.setBlock(new BlockPos(4, 2, 18), ModBlocks.STOREHOUSE);
		ChoiceMenu board = StorehouseBoard.boardForTest(v.owner(), helper.absolutePos(new BlockPos(4, 2, 18)));
		String shopping = lore(board.icon(StorehouseBoard.SHOPPING_SLOT));
		helper.assertTrue(shopping.contains("50") && shopping.contains("12"), "the Storehouse board doesn't show the list: " + shopping);
		// supplied: nothing to buy
		sites.forEach(s -> s.setStatus(BuildSite.Status.WORKING));
		helper.assertTrue(StewardSafety.shoppingList(v.level(), v.hall()).isEmpty(), "a list with nothing waiting");
		helper.succeed();
	}

	private static List<BuildSite> waitingSites(GameTestHelper helper, Village v, Map<Item, Integer> first, Map<Item, Integer> second) {
		List<BuildSite> out = new ArrayList<>();
		int n = 0;
		for (Map<Item, Integer> missing : List.of(first, second)) {
			BlueprintData.Placement placement = new BlueprintData.Placement(v.level().dimension().location(),
				helper.absolutePos(new BlockPos(20 + 5 * n++, 2, 24)), Rotation.NONE, Mirror.NONE);
			BuildSite site = BuildSiteManager.get(v.level()).create(v.owner().getUUID(), "", AliveWorkplace.id("stone_house"), placement);
			site.setStewardHall(v.hall());
			site.setStatus(BuildSite.Status.WAITING_FOR_MATERIALS);
			site.setMissing(new HashMap<>(missing));
			out.add(site);
		}
		return out;
	}

	/** Two builds waiting a whole day: no new proposal; once supplied, the Well is proposed again. */
	//$ gametest_ticks_batch AREA '1200' '"safetyPause"'
	@GameTest(template = AREA, timeoutTicks = 1200, batch = "safetyPause")
	public void proposalsStopWhileTwoSitesWaitAndStartOnceSupplied(GameTestHelper helper) {
		Village v = village(helper);
		List<BuildSite> sites = waitingSites(helper, v, Map.of(Items.GLASS, 4), Map.of(Items.GLASS, 4));
		long day = StewardWishes.day(v.level());
		v.entity().setStewardWishes(new StewardWishes.State(day, List.of(wish()), Map.of()));
		StewardDesk.plan(v.level(), v.steward(), v.hall());
		sites.forEach(s -> s.setWaitingSince(v.level().getGameTime() - StewardSafety.PAUSE_TICKS - 1));
		helper.assertTrue(StewardSafety.paused(v.level(), v.hall()), "not paused with two builds waiting a day");
		AtomicLong supplied = new AtomicLong(-1);
		helper.onEachTick(() -> {
			if (v.level().getGameTime() % 20 == 0) {
				StewardDesk.plan(v.level(), v.steward(), v.hall());
			}
		});
		helper.runAfterDelay(400, () -> {
			helper.assertTrue(StewardDesk.of(v.level(), v.hall()).proposals().isEmpty(),
				"proposed while two builds waited a day: " + StewardDesk.of(v.level(), v.hall()).proposals());
			sites.forEach(s -> s.setStatus(BuildSite.Status.WORKING)); // supplied: back at work
			supplied.set(v.level().getGameTime());
		});
		helper.succeedWhen(() -> {
			helper.assertTrue(supplied.get() >= 0, "not supplied yet");
			helper.assertTrue(!StewardSafety.paused(v.level(), v.hall()), "still paused once supplied");
			helper.assertTrue(!StewardDesk.of(v.level(), v.hall()).proposals().isEmpty(), "no proposal once supplied: wishes "
				+ StewardWishes.of(v.level(), v.hall()).wishes().size() + ", plot " + Plots.result(v.level(), v.hall(), new Plots.Request(List.of(WELL), "homes"))
				+ ", spot " + checkSpot(helper, v));
		});
	}

	/** The ledger and a site's player-block count survive save and reload; old saves load with defaults. */
	//$ gametest_ticks_batch EMPTY_STRUCTURE '20' '"safetySave"'
	@GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 20, batch = "safetySave")
	public void theLedgerSurvivesSaveAndReload(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		PlayerBuilt ledger = new PlayerBuilt();
		BlockPos pos = helper.absolutePos(new BlockPos(1, 2, 1));
		ledger.mark(pos);
		CompoundTag tag = ledger.save(new CompoundTag(), level.registryAccess());
		PlayerBuilt loaded = PlayerBuilt.load(tag, level.registryAccess());
		helper.assertTrue(loaded.marked(pos) && loaded.size() == 1, "the ledger lost its mark");
		helper.assertTrue(!loaded.marked(pos.offset(16, 0, 0)), "the next section marked too");
		helper.assertTrue(PlayerBuilt.load(new CompoundTag(), level.registryAccess()).size() == 0, "an old world's ledger isn't empty");
		BuildSite site = new BuildSite(java.util.UUID.randomUUID(), java.util.UUID.randomUUID(), "", WELL,
			new BlueprintData.Placement(level.dimension().location(), pos, Rotation.NONE, Mirror.NONE));
		CompoundTag old = site.save();
		BuildSite before = BuildSite.load(old);
		helper.assertTrue(before != null && before.waitingSince() == BuildSite.NOT_WAITING && before.playerBlocks() == 0, "an old site's defaults are wrong");
		site.setWaitingSince(1234);
		site.playerBlockInTheWay();
		BuildSite after = BuildSite.load(site.save());
		helper.assertTrue(after != null && after.waitingSince() == 1234 && after.playerBlocks() == 1, "waiting and player blocks not saved");
		helper.succeed();
	}

	// ---- a block a player places in a running site ---------------------------------------------------------------

	/** Starts the Well at {@link #SPOT} by Run the village, with free materials and a quick builder. */
	private static BuildSite runningSite(GameTestHelper helper, Village v) {
		ServerLevel level = v.level();
		GameRules rules = level.getGameRules();
		boolean free = rules.getBoolean(ModGameRules.FREE_MATERIALS);
		int delay = rules.getInt(ModGameRules.BUILD_DELAY);
		rules.getRule(ModGameRules.FREE_MATERIALS).set(true, level.getServer());
		rules.getRule(ModGameRules.BUILD_DELAY).set(2, level.getServer());
		Leftovers.after(helper, () -> {
			rules.getRule(ModGameRules.FREE_MATERIALS).set(free, level.getServer());
			rules.getRule(ModGameRules.BUILD_DELAY).set(delay, level.getServer());
		});
		StewardDesk.Proposal proposal = propose(helper, v);
		helper.assertTrue(StewardDesk.approve(level, v.hall(), null, proposal.id()).ok(), "setup: the Steward didn't start the Well");
		List<BuildSite> open = StewardDesk.openSites(level, v.hall());
		helper.assertTrue(open.size() == 1, "setup: " + open.size() + " sites");
		return open.get(0);
	}

	/** A cobblestone in the middle of the well's spot and planks where its wall goes: both still there when it's done. */
	//$ gametest_ticks_batch AREA '6000' '"safetyRunning"'
	@GameTest(template = AREA, timeoutTicks = 6000, batch = "safetyRunning")
	public void aBlockAPlayerPlacesInARunningSiteStays(GameTestHelper helper) {
		Village v = village(helper);
		BuildSite site = runningSite(helper, v);
		BoundingBox box = site.plan(v.level()).bounds();
		BlockPos wantedSolid = null;
		BlockPos wantedAir = null;
		Map<BlockPos, Boolean> solid = new HashMap<>();
		for (var stage : List.of(io.github.jcondedata.aliveworkplace.build.BuildPlan.Stage.STRUCTURE)) {
			for (var step : site.plan(v.level()).steps(stage)) {
				solid.put(step.pos(), !step.state().isAir());
			}
		}
		for (int y = box.minY(); y <= box.maxY() && (wantedSolid == null || wantedAir == null); y++) {
			for (int x = box.minX(); x <= box.maxX(); x++) {
				for (int z = box.minZ(); z <= box.maxZ(); z++) {
					BlockPos p = new BlockPos(x, y, z);
					if (!v.level().getBlockState(p).isAir() || !v.level().getBlockState(p.below()).isSolid()) {
						continue;
					}
					if (wantedSolid == null && solid.getOrDefault(p, false)) {
						wantedSolid = p;
					} else if (wantedAir == null && !solid.containsKey(p)) {
						wantedAir = p;
					}
				}
			}
		}
		helper.assertTrue(wantedSolid != null, "setup: no open spot where the well wants a block");
		List<BlockPos> placed = new ArrayList<>();
		placed.add(wantedSolid);
		if (wantedAir != null) {
			placed.add(wantedAir);
		}
		for (BlockPos p : placed) {
			helper.assertTrue(place(helper, v.owner(), p, Blocks.COBBLESTONE), "setup: not placed at " + p);
			helper.assertTrue(v.level().getBlockState(p).is(Blocks.COBBLESTONE), "setup: placing put " + v.level().getBlockState(p) + " at " + p);
			helper.assertTrue(StewardSafety.playersBlock(v.level(), p, v.level().getBlockState(p)), "setup: not seen as a player's block at " + p);
		}
		Map<BlockPos, String> seen = new HashMap<>();
		helper.onEachTick(() -> {
			for (BlockPos p : placed) {
				if (v.level().getBlockState(p).is(Blocks.COBBLESTONE)) {
					seen.put(p, "stage " + site.stage() + " cursor step " + (site.current(site.plan(v.level())) == null ? "-"
						: site.current(site.plan(v.level())).pos() + "=" + site.current(site.plan(v.level())).state()) + " skipped " + site.skipped());
				}
			}
		});
		helper.succeedWhen(() -> {
			for (BlockPos p : placed) {
				helper.assertTrue(v.level().getBlockState(p).is(Blocks.COBBLESTONE), "the player's block at " + helper.relativePos(p)
					+ " is gone (now " + v.level().getBlockState(p) + "), last seen at " + seen.get(p) + "; site box " + box);
			}
			helper.assertTrue(site.isDone() || BuildSiteManager.get(v.level()).get(site.id()) == null, "still building: " + site.stage());
			for (BlockPos p : placed) {
				helper.assertTrue(v.level().getBlockState(p).is(Blocks.COBBLESTONE), "the player's block at " + helper.relativePos(p) + " is gone");
			}
			helper.assertTrue(site.playerBlocks() > 0 && site.skipped() > 0, "the player's block wasn't counted as skipped: "
				+ site.playerBlocks() + "/" + site.skipped());
		});
	}

	/** The desk names it on the build: "a player's block is in the way". */
	//$ gametest_ticks_batch AREA '40' '"safetyDesk"'
	@GameTest(template = AREA, timeoutTicks = 40, batch = "safetyDesk")
	public void theDeskSaysAPlayersBlockIsInTheWay(GameTestHelper helper) {
		Village v = village(helper);
		BuildSite site = waitingSites(helper, v, Map.of(Items.GLASS, 1), Map.of(Items.GLASS, 1)).get(0);
		site.playerBlockInTheWay();
		ChoiceMenu menu = VillageHallScreen.forTest(v.owner(), v.hall());
		menu.press(VillageHallScreen.ADVICE, v.owner());
		String all = lore(menu.icon(StewardDeskPage.FIRST_OPEN)) + lore(menu.icon(StewardDeskPage.FIRST_OPEN + 1));
		helper.assertTrue(all.contains("screen.aliveworkplace.desk.player_block") || all.contains("A player's block is in the way"),
			"the desk doesn't say a player's block is in the way: " + all);
		helper.succeed();
	}

	// ---- chaos: a self-run village while a player keeps building in its site ------------------------------------

	@GameTestGenerator
	public Collection<TestFunction> chaos() {
		List<TestFunction> out = new ArrayList<>();
		for (long seed : SEEDS) {
			String name = "steward_safety_chaos_" + seed;
			out.add(new TestFunction(name, name, AREA, 8000, 0, true, helper -> chaos(helper, seed)));
		}
		return out;
	}

	private static final Block[] PLAYER_BLOCKS = {Blocks.COBBLESTONE, Blocks.OAK_PLANKS, Blocks.GLASS, Blocks.BRICKS, Blocks.OAK_FENCE};

	/**
	 * Run the village approves the Well itself; every 40 to 120 ticks a player puts a block somewhere in or around the
	 * site (where there's room). At the end every block the player placed is where they put it.
	 */
	static void chaos(GameTestHelper helper, long seed) {
		Village v = village(helper);
		RandomSource rnd = RandomSource.create(seed);
		AliveWorkplace.LOG.info("[chaos] steward safety seed {}", seed);
		BuildSite site = runningSite(helper, v);
		BoundingBox box = site.plan(v.level()).bounds().inflatedBy(1);
		Map<BlockPos, Block> placed = new HashMap<>();
		AtomicInteger next = new AtomicInteger(20);
		AtomicInteger ticks = new AtomicInteger();
		helper.onEachTick(() -> {
			if (ticks.incrementAndGet() < next.get() || site.isDone()) {
				return;
			}
			next.addAndGet(40 + rnd.nextInt(81));
			for (int tries = 0; tries < 10; tries++) {
				BlockPos p = new BlockPos(box.minX() + rnd.nextInt(box.getXSpan()), helper.absolutePos(SPOT).getY() + rnd.nextInt(3),
					box.minZ() + rnd.nextInt(box.getZSpan()));
				if (!v.level().getBlockState(p).isAir() || v.level().getEntities((net.minecraft.world.entity.Entity) null,
					new net.minecraft.world.phys.AABB(p)).stream().anyMatch(e -> e instanceof Villager)) {
					continue;
				}
				Block block = PLAYER_BLOCKS[rnd.nextInt(PLAYER_BLOCKS.length)];
				if (place(helper, v.owner(), p, block)) {
					placed.put(p, block);
				}
				break;
			}
			for (Map.Entry<BlockPos, Block> e : placed.entrySet()) {
				helper.assertTrue(v.level().getBlockState(e.getKey()).is(e.getValue()), "the player's " + e.getValue() + " at "
					+ helper.relativePos(e.getKey()) + " is gone (stage " + site.stage() + "); seed " + seed);
			}
		});
		helper.succeedWhen(() -> {
			helper.assertTrue(site.isDone() || BuildSiteManager.get(v.level()).get(site.id()) == null, "still building: " + site.stage() + "; seed " + seed);
			helper.assertTrue(placed.size() >= 3, "only " + placed.size() + " player blocks placed; seed " + seed);
			for (Map.Entry<BlockPos, Block> e : placed.entrySet()) {
				helper.assertTrue(v.level().getBlockState(e.getKey()).is(e.getValue()), "the player's " + e.getValue() + " at "
					+ helper.relativePos(e.getKey()) + " is gone; seed " + seed);
			}
			AliveWorkplace.LOG.info("[chaos] steward safety seed {}: {} player blocks kept, {} steps skipped", seed, placed.size(), site.skipped());
		});
	}
}
