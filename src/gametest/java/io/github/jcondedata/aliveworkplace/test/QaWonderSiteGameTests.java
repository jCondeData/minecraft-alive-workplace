package io.github.jcondedata.aliveworkplace.test;

import io.github.jcondedata.aliveworkplace.blueprint.BlueprintData;
import io.github.jcondedata.aliveworkplace.blueprint.BlueprintItem;
import io.github.jcondedata.aliveworkplace.build.BuildSite;
import io.github.jcondedata.aliveworkplace.build.BuildSiteManager;
import io.github.jcondedata.aliveworkplace.build.Builders;
import io.github.jcondedata.aliveworkplace.hall.Chronicle;
import io.github.jcondedata.aliveworkplace.hall.VillageHallBlockEntity;
import io.github.jcondedata.aliveworkplace.hall.VillageRanks;
import io.github.jcondedata.aliveworkplace.mc.Ids;
import io.github.jcondedata.aliveworkplace.registry.ModBlocks;
import io.github.jcondedata.aliveworkplace.registry.ModComponents;
import io.github.jcondedata.aliveworkplace.registry.ModGameRules;
import io.github.jcondedata.aliveworkplace.registry.ModItems;
import io.github.jcondedata.aliveworkplace.registry.ModVillagers;
import io.github.jcondedata.aliveworkplace.wonder.Wonder;
import io.github.jcondedata.aliveworkplace.wonder.WonderGoods;
import io.github.jcondedata.aliveworkplace.wonder.WonderManager;
import io.github.jcondedata.aliveworkplace.wonder.WonderPlanItem;
import io.github.jcondedata.aliveworkplace.wonder.WonderSite;
import io.github.jcondedata.aliveworkplace.wonder.WonderStages;
import io.github.jcondedata.aliveworkplace.wonder.Wonders;
import io.github.jcondedata.aliveworkplace.work.Requests;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

/**
 * QA (ROADMAP 35.4, 35.7): adversarial checks of the Foundation Stone and a Wonder's goods, from the spec, through the
 * player's own entry points (a real block break, a right-click on the stone, the hand-over to a builder, the command):
 * who may end a Wonder and what comes back (the plan exactly once, the goods only of a stage not yet dedicated), the
 * stockpile's reach of 8 blocks from both sides, goods short by one, goods handed in kept through a reload and taken
 * once, and the sentences a player reads.
 */
public class QaWonderSiteGameTests implements FabricGameTest {
	private static final String AREA = "aliveworkplace_test:build_area";
	private static final ResourceLocation FOLLY = ResourceLocation.fromNamespaceAndPath("aliveworkplace_test", "test_folly");
	private static final BlockPos HALL = new BlockPos(14, 2, 14);
	private static final BlockPos STONE = new BlockPos(3, 2, 3);

	/** A Town owned by {@code owner}, and the folly begun with its stone at {@link #STONE}, waiting at {@code stage} for its goods. */
	private record W(ServerLevel level, ServerPlayer owner, BlockPos hall, BlockPos stone, UUID id) {
		WonderSite site() {
			return WonderManager.get(level).site(id).orElseThrow();
		}
	}

	private static W wonder(GameTestHelper helper, int stage, ServerPlayer owner) {
		Leftovers.clear(helper);
		ServerLevel level = helper.getLevel();
		helper.setDayTime(2000);
		// the owner stands by the stone (a plan or goods dropped at their feet land in the area, where they're counted)
		Vec3 by = Vec3.atBottomCenterOf(helper.absolutePos(new BlockPos(8, 2, 1)));
		owner.moveTo(by.x, by.y, by.z);
		helper.setBlock(HALL, ModBlocks.VILLAGE_HALL);
		VillageHallBlockEntity hall = helper.getBlockEntity(HALL);
		hall.setOwner(owner.getUUID(), "Owner");
		hall.setRank(VillageRanks.Rank.TOWN);
		helper.setBlock(STONE, ModBlocks.FOUNDATION_STONE);
		BlockPos stone = helper.absolutePos(STONE);
		WonderManager manager = WonderManager.get(level);
		WonderSite site = manager.begin(FOLLY, helper.absolutePos(HALL), owner.getUUID(), "Owner", Chronicle.day(level));
		Leftovers.after(helper, () -> forget(level, site.id(), stone));
		manager.place(site.id(), new BlueprintData.Placement(Ids.of(level.dimension()), stone.offset(1, 0, 1), Rotation.NONE, Mirror.NONE), stone);
		manager.setStage(site.id(), stage);
		return new W(level, owner, helper.absolutePos(HALL), stone, site.id());
	}

	private static void forget(ServerLevel level, UUID wonder, BlockPos stone) {
		BuildSiteManager sites = BuildSiteManager.get(level);
		for (BuildSite s : List.copyOf(sites.all())) {
			if (wonder.equals(s.wonder())) {
				sites.remove(s.id());
			}
		}
		Requests.clearAt(level, stone);
		WonderManager.get(level).remove(wonder);
	}

	private static Container chest(GameTestHelper helper, BlockPos rel) {
		helper.setBlock(rel, Blocks.CHEST);
		return helper.getBlockEntity(rel);
	}

	private static int count(Container box, net.minecraft.world.item.Item item) {
		int n = 0;
		for (int i = 0; i < box.getContainerSize(); i++) {
			if (box.getItem(i).is(item)) {
				n += box.getItem(i).getCount();
			}
		}
		return n;
	}

	private static int handed(WonderSite site) {
		return site.delivered().stream().mapToInt(WonderSite.Handed::count).sum();
	}

	/** Plans lying on the ground round the test area (where the owner stands: see {@link #wonder}). */
	private static int plansOnTheGround(GameTestHelper helper) {
		AABB box = new AABB(helper.absolutePos(BlockPos.ZERO)).inflate(24);
		int n = 0;
		for (ItemEntity e : helper.getLevel().getEntitiesOfClass(ItemEntity.class, box, e -> e.getItem().is(ModItems.WONDER_PLAN))) {
			n += e.getItem().getCount();
		}
		return n;
	}

	private static boolean said(List<Component> seen, String text) {
		return seen.stream().anyMatch(c -> c.getString().contains(text));
	}

	private static String all(List<Component> seen) {
		return seen.stream().map(Component::getString).toList().toString();
	}

	/** Right-clicks the stone holding what's in the main hand, as a player does (the item first, then the block). */
	private static void rightClick(ServerLevel level, ServerPlayer player, BlockPos stone) {
		BlockHitResult hit = new BlockHitResult(Vec3.atCenterOf(stone), Direction.UP, stone, false);
		var state = level.getBlockState(stone);
		var used = state.useItemOn(player.getMainHandItem(), level, player, InteractionHand.MAIN_HAND, hit);
		if (used == net.minecraft.world.ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION) {
			state.useWithoutItem(level, player, hit);
		}
	}

	// ---------------------------------------------------------------- who may end a Wonder, and what comes back

	/**
	 * A real break by a player who is neither the owner nor an operator, sneaking, is refused with a sentence naming the
	 * owner: the stone stands and the Wonder goes on with the goods handed in. An operator (not the owner), sneaking, ends
	 * it: the plan and the handed-in goods go to the owner, not to the operator.
	 */
	//$ gametest_batch AREA '"qaWonderStoneOp"'
	@GameTest(template = AREA, batch = "qaWonderStoneOp")
	public void aStrangersBreakIsRefusedAnOperatorsEndsItForTheOwner(GameTestHelper helper) {
		List<Component> ownerSaw = new ArrayList<>();
		List<Component> strangerSaw = new ArrayList<>();
		List<Component> opSaw = new ArrayList<>();
		ServerPlayer owner = WonderFinishedGameTests.listener(helper, ownerSaw, "QaOwner");
		W w = wonder(helper, 1, owner);
		ServerLevel level = w.level();
		owner.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.BREAD, 5));
		rightClick(level, owner, w.stone());
		helper.assertTrue(handed(w.site()) == 5 && owner.getMainHandItem().isEmpty(), "the owner's bread wasn't handed in: " + w.site().delivered());

		ServerPlayer stranger = WonderFinishedGameTests.listener(helper, strangerSaw, "QaStranger");
		helper.assertTrue(!stranger.hasPermissions(2), "setup: the stranger is an operator");
		stranger.setShiftKeyDown(true);
		boolean broke = stranger.gameMode.destroyBlock(w.stone());
		stranger.setShiftKeyDown(false);
		helper.assertTrue(!broke && level.getBlockState(w.stone()).is(ModBlocks.FOUNDATION_STONE), "a stranger's sneak-break broke the stone");
		helper.assertTrue(WonderManager.get(level).site(w.id()).isPresent() && handed(w.site()) == 5, "a stranger's break ended the Wonder or lost its goods");
		helper.assertTrue(said(strangerSaw, "Only the owner of the Test Folly (Owner) or an operator may abandon it"), "the stranger was told: " + all(strangerSaw));
		helper.assertTrue(stranger.getInventory().countItem(ModItems.WONDER_PLAN) == 0 && stranger.getInventory().countItem(Items.BREAD) == 0,
			"the stranger got something");

		ServerPlayer op = WonderFinishedGameTests.listener(helper, opSaw, "QaOperator");
		var server = level.getServer();
		server.getPlayerList().getOps().add(new net.minecraft.server.players.ServerOpListEntry(op.getGameProfile(), 4, false));
		Leftovers.after(helper, () -> server.getPlayerList().deop(op.getGameProfile()));
		helper.assertTrue(op.hasPermissions(2), "setup: not an operator");
		op.setShiftKeyDown(false);
		helper.assertTrue(!op.gameMode.destroyBlock(w.stone()) && WonderManager.get(level).site(w.id()).isPresent(), "an operator's plain break ended it");
		helper.assertTrue(said(opSaw, "Sneak while breaking the stone to abandon the Test Folly"), "the operator wasn't told to sneak: " + all(opSaw));
		op.setShiftKeyDown(true);
		boolean ended = op.gameMode.destroyBlock(w.stone());
		op.setShiftKeyDown(false);
		helper.assertTrue(ended && !level.getBlockState(w.stone()).is(ModBlocks.FOUNDATION_STONE), "the operator's sneak-break was refused");
		helper.assertTrue(WonderManager.get(level).site(w.id()).isEmpty(), "the Wonder goes on after the operator's break");
		helper.assertTrue(owner.getInventory().countItem(ModItems.WONDER_PLAN) == 1 && owner.getInventory().countItem(Items.BREAD) == 5,
			"the owner got back " + owner.getInventory().countItem(ModItems.WONDER_PLAN) + " plans and " + owner.getInventory().countItem(Items.BREAD) + " bread");
		helper.assertTrue(op.getInventory().countItem(ModItems.WONDER_PLAN) == 0 && op.getInventory().countItem(Items.BREAD) == 0
			&& plansOnTheGround(helper) == 0, "the plan or goods went to the operator or the ground: op " + op.getInventory().countItem(ModItems.WONDER_PLAN)
			+ " plans, " + op.getInventory().countItem(Items.BREAD) + " bread; " + plansOnTheGround(helper) + " plans on the ground at "
			+ level.getEntitiesOfClass(ItemEntity.class, new AABB(helper.absolutePos(BlockPos.ZERO)).inflate(24), e -> e.getItem().is(ModItems.WONDER_PLAN)).stream()
			.map(e -> helper.relativePos(e.blockPosition()) + " age " + e.getAge()).toList() + "; players at " + level.players().stream()
			.map(p -> p.getName().getString() + "@" + helper.relativePos(p.blockPosition())).toList());
		helper.assertTrue(said(ownerSaw, "The Test Folly is abandoned") && said(ownerSaw, "came back to you"), "the owner wasn't told: " + all(ownerSaw));
		helper.succeed();
	}

	/**
	 * The owner's own sneaking break, through the real break: what's built stays, the plan comes back exactly once (none
	 * dropped as well), and neither a second cancel nor breaking a stone laid again at the spot gives another.
	 */
	//$ gametest_batch AREA '"qaWonderStoneOnce"'
	@GameTest(template = AREA, batch = "qaWonderStoneOnce")
	public void theOwnersBreakGivesThePlanBackExactlyOnce(GameTestHelper helper) {
		List<Component> seen = new ArrayList<>();
		ServerPlayer owner = WonderFinishedGameTests.listener(helper, seen, "QaOwnerOnce");
		W w = wonder(helper, 2, owner);
		ServerLevel level = w.level();
		BlockPos built = helper.absolutePos(new BlockPos(6, 2, 6));
		level.setBlockAndUpdate(built, Blocks.STONE_BRICKS.defaultBlockState());
		owner.setShiftKeyDown(true);
		helper.assertTrue(owner.gameMode.destroyBlock(w.stone()), "the owner's sneak-break was refused");
		helper.assertTrue(WonderManager.get(level).site(w.id()).isEmpty(), "the Wonder goes on");
		helper.assertTrue(owner.getInventory().countItem(ModItems.WONDER_PLAN) == 1 && plansOnTheGround(helper) == 0,
			"plans: " + owner.getInventory().countItem(ModItems.WONDER_PLAN) + " in hand, " + plansOnTheGround(helper) + " on the ground");
		helper.assertTrue(level.getBlockState(built).is(Blocks.STONE_BRICKS), "what's built was taken down");
		helper.assertTrue(WonderStages.cancel(level, w.id()).isEmpty(), "a second cancel gave a plan");
		level.setBlockAndUpdate(w.stone(), ModBlocks.FOUNDATION_STONE.defaultBlockState());
		helper.assertTrue(owner.gameMode.destroyBlock(w.stone()), "a stray stone can't be broken");
		owner.setShiftKeyDown(false);
		helper.assertTrue(owner.getInventory().countItem(ModItems.WONDER_PLAN) == 1 && plansOnTheGround(helper) == 0,
			"a stray stone gave another plan: " + owner.getInventory().countItem(ModItems.WONDER_PLAN) + " + " + plansOnTheGround(helper));
		// the returned plan is a real, unplaced Wonder Plan of the folly for the same village
		ItemStack back = ItemStack.EMPTY;
		for (int i = 0; i < owner.getInventory().getContainerSize(); i++) {
			if (owner.getInventory().getItem(i).is(ModItems.WONDER_PLAN)) {
				back = owner.getInventory().getItem(i);
			}
		}
		Optional<WonderPlanItem.Plan> plan = WonderPlanItem.plan(back);
		helper.assertTrue(plan.isPresent() && plan.get().wonder().equals(FOLLY) && plan.get().hall().pos().equals(w.hall()),
			"the plan that came back: " + plan);
		helper.succeed();
	}

	/**
	 * The command's cancel of a stage already dedicated: the Wonder ends and the plan comes back once, but the goods handed
	 * in for that stage were taken by the dedication and don't come back.
	 */
	//$ gametest_batch AREA '"qaWonderCommandCancel"'
	@GameTest(template = AREA, batch = "qaWonderCommandCancel")
	public void aCommandCancelAfterTheDedicationGivesNoGoodsBack(GameTestHelper helper) {
		List<Component> seen = new ArrayList<>();
		ServerPlayer owner = WonderFinishedGameTests.listener(helper, seen, "QaOwnerCmd");
		W w = wonder(helper, 3, owner);
		ServerLevel level = w.level();
		owner.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.WHITE_CANDLE, 4));
		rightClick(level, owner, w.stone());
		helper.assertTrue(handed(w.site()) == 4, "the candles weren't handed in: " + w.site().delivered());
		WonderStages.tick(level);
		WonderSite now = w.site();
		helper.assertTrue(now.started() && now.dedicated() && now.parts().size() == 1, "not dedicated: " + now);
		helper.assertTrue(!owner.hasPermissions(2), "setup: the owner is an operator");
		UUID part = now.parts().get(0);
		seen.clear();
		level.getServer().getCommands().performPrefixedCommand(owner.createCommandSourceStack(), "workplace cancel " + part);
		helper.assertTrue(WonderManager.get(level).site(w.id()).isEmpty(), "the command didn't end the Wonder: " + all(seen));
		helper.assertTrue(BuildSiteManager.get(level).get(part) == null, "its part is still open");
		helper.assertTrue(owner.getInventory().countItem(ModItems.WONDER_PLAN) == 1 && plansOnTheGround(helper) == 0, "plans back: "
			+ owner.getInventory().countItem(ModItems.WONDER_PLAN) + " + " + plansOnTheGround(helper));
		helper.assertTrue(owner.getInventory().countItem(Items.WHITE_CANDLE) == 0, "a dedicated stage's goods came back: "
			+ owner.getInventory().countItem(Items.WHITE_CANDLE));
		helper.assertTrue(said(seen, "Canceled the Wonder") && !said(seen, "came back to you"), "told: " + all(seen));
		helper.succeed();
	}

	// ---------------------------------------------------------------- the stockpile

	/**
	 * The stockpile is the chests within 8 blocks of the stone: candles 9 blocks away don't count and stay; 3 of 4 within
	 * reach (8 along, and 8 and 8 on the diagonal) never start the stage; with 6 in reach it is dedicated, taking exactly 4.
	 */
	//$ gametest_batch AREA '"qaWonderReach"'
	@GameTest(template = AREA, batch = "qaWonderReach")
	public void theStockpileReachesEightBlocksNotNine(GameTestHelper helper) {
		List<Component> seen = new ArrayList<>();
		W w = wonder(helper, 3, WonderFinishedGameTests.listener(helper, seen, "QaOwnerReach"));
		ServerLevel level = w.level();
		Container far = chest(helper, STONE.offset(9, 0, 0));
		far.setItem(0, new ItemStack(Items.WHITE_CANDLE, 4));
		Container farZ = chest(helper, STONE.offset(0, 0, 9));
		farZ.setItem(0, new ItemStack(Items.WHITE_CANDLE, 4));
		WonderStages.tick(level);
		helper.assertTrue(!w.site().started() && WonderGoods.lines(level, w.site()).get(0).in() == 0,
			"candles 9 blocks away counted: " + WonderGoods.lines(level, w.site()));

		Container near = chest(helper, STONE.offset(8, 0, 0));
		near.setItem(0, new ItemStack(Items.WHITE_CANDLE, 3));
		WonderStages.tick(level);
		WonderGoods.Line line = WonderGoods.lines(level, w.site()).get(0);
		helper.assertTrue(!w.site().started() && line.in() == 3 && line.left() == 1, "one short: " + line + ", started " + w.site().started());

		Container corner = chest(helper, STONE.offset(8, 0, 8));
		corner.setItem(0, new ItemStack(Items.WHITE_CANDLE, 3));
		WonderStages.tick(level);
		helper.assertTrue(w.site().started() && w.site().dedicated(), "not dedicated with 6 in reach: " + WonderGoods.lines(level, w.site()));
		helper.assertTrue(count(near, Items.WHITE_CANDLE) + count(corner, Items.WHITE_CANDLE) == 2, "took "
			+ (6 - count(near, Items.WHITE_CANDLE) - count(corner, Items.WHITE_CANDLE)) + " candles from the stockpile, not 4");
		helper.assertTrue(count(far, Items.WHITE_CANDLE) == 4 && count(farZ, Items.WHITE_CANDLE) == 4, "candles taken from a chest out of reach");
		helper.succeed();
	}

	/**
	 * Goods handed in at the stone, then a save and load of the Wonders (at night: the stage waits), then by day the
	 * dedication: the handed-in count read back, only the rest taken from the chest, and a further reload and round
	 * after the dedication take nothing more.
	 */
	//$ gametest_batch AREA '"qaWonderReloadOnce"'
	@GameTest(template = AREA, batch = "qaWonderReloadOnce")
	public void handedInGoodsSurviveAReloadAndAreTakenOnce(GameTestHelper helper) {
		List<Component> seen = new ArrayList<>();
		ServerPlayer owner = WonderFinishedGameTests.listener(helper, seen, "QaOwnerReload");
		W w = wonder(helper, 3, owner);
		ServerLevel level = w.level();
		var registries = level.registryAccess();
		owner.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.WHITE_CANDLE, 2));
		rightClick(level, owner, w.stone());
		helper.assertTrue(handed(w.site()) == 2 && owner.getMainHandItem().isEmpty(), "not handed in: " + w.site().delivered());
		Container pile = chest(helper, STONE.offset(0, 0, 3));
		pile.setItem(0, new ItemStack(Items.WHITE_CANDLE, 5));
		helper.setDayTime(14000);
		try {
			WonderStages.tick(level);
			helper.assertTrue(!w.site().started(), "dedicated at night");
			level.getDataStorage().set(WonderManager.NAME, WonderManager.load(WonderManager.get(level).save(new CompoundTag(), registries), registries));
			helper.assertTrue(handed(w.site()) == 2 && !w.site().started(), "the handed-in candles didn't read back: " + w.site());
			helper.setDayTime(24000 + 500);
			WonderStages.tick(level);
			helper.assertTrue(w.site().started() && w.site().delivered().isEmpty(), "not dedicated in the morning: " + w.site());
			helper.assertTrue(count(pile, Items.WHITE_CANDLE) == 3, "the dedication took " + (5 - count(pile, Items.WHITE_CANDLE))
				+ " from the chest, not the 2 the hand-in lacked");
			level.getDataStorage().set(WonderManager.NAME, WonderManager.load(WonderManager.get(level).save(new CompoundTag(), registries), registries));
			WonderStages.tick(level);
			WonderStages.tick(level);
			helper.assertTrue(w.site().dedicated() && w.site().parts().size() == 1 && count(pile, Items.WHITE_CANDLE) == 3,
				"taken twice or started again after a reload: " + w.site() + ", " + count(pile, Items.WHITE_CANDLE) + " left");
			helper.assertTrue(owner.getInventory().countItem(Items.WHITE_CANDLE) == 0, "candles came back to the owner");
		} finally {
			helper.setDayTime(2000);
		}
		helper.succeed();
	}

	// ---------------------------------------------------------------- what players read

	/**
	 * Right-clicking the stone with an empty hand tells, in plain English, the goods line with "no Chef in the village"
	 * while there's no Chef (no raw key, no unfilled %s), and without that once a Chef lives there; holding something
	 * that's no good of the stage shows the same status rather than taking it.
	 */
	//$ gametest_batch AREA '"qaWonderStoneText"'
	@GameTest(template = AREA, batch = "qaWonderStoneText")
	public void theStoneReadsInPlainEnglish(GameTestHelper helper) {
		List<Component> seen = new ArrayList<>();
		ServerPlayer owner = WonderFinishedGameTests.listener(helper, seen, "QaOwnerText");
		W w = wonder(helper, 1, owner);
		ServerLevel level = w.level();
		owner.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
		rightClick(level, owner, w.stone());
		helper.assertTrue(!seen.isEmpty(), "the right-click said nothing");
		String text = seen.get(seen.size() - 1).getString();
		helper.assertTrue(text.contains("The Test Folly of") && text.contains("0 of 8 Bread · Chef (no Chef in the village)")
			&& text.contains("Before it begins, this stage needs its goods (in the chests within 8 blocks, or handed in here):"), "the stone said: " + text);
		helper.assertTrue(!text.contains("aliveworkplace.") && !text.contains("%s") && !text.contains("%%") && !text.contains("entity.minecraft"),
			"a raw key or placeholder in: " + text);

		Villager chef = helper.spawn(EntityType.VILLAGER, new BlockPos(12, 2, 12));
		chef.setVillagerData(chef.getVillagerData().setProfession(ModVillagers.CHEF));
		chef.setNoAi(true);
		owner.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.DIRT, 2));
		seen.clear();
		rightClick(level, owner, w.stone());
		helper.assertTrue(owner.getMainHandItem().getCount() == 2 && handed(w.site()) == 0, "the stone took dirt");
		helper.assertTrue(!seen.isEmpty(), "holding dirt, the stone said nothing");
		text = seen.get(seen.size() - 1).getString();
		helper.assertTrue(text.contains("0 of 8 Bread · Chef") && !text.contains("no Chef"), "with a Chef in the village the stone said: " + text);
		chef.discard();
		helper.succeed();
	}

	/**
	 * While one Wonder goes up, handing the plan of another to the village's builder is refused with a sentence naming the
	 * one going up, the plan kept; a stranger handing a plan to the owner's builder is refused too, saying the builder
	 * works for someone else.
	 */
	//$ gametest_batch AREA '"qaWonderSecondPlan"'
	@GameTest(template = AREA, batch = "qaWonderSecondPlan")
	public void aSecondWonderWhileOneGoesUpIsRefusedWithASentence(GameTestHelper helper) {
		Leftovers.clear(helper);
		ServerLevel level = helper.getLevel();
		helper.setDayTime(2000);
		level.getGameRules().getRule(ModGameRules.BUILD_DELAY).set(2, level.getServer());
		List<Component> ownerSaw = new ArrayList<>();
		List<Component> strangerSaw = new ArrayList<>();
		ServerPlayer owner = WonderFinishedGameTests.listener(helper, ownerSaw, "QaOwnerTwo");
		helper.setBlock(HALL, ModBlocks.VILLAGE_HALL);
		VillageHallBlockEntity hall = helper.getBlockEntity(HALL);
		hall.setOwner(owner.getUUID(), "Owner");
		hall.setRank(VillageRanks.Rank.CITY);
		helper.setBlock(new BlockPos(2, 2, 14), ModBlocks.BUILDERS_BENCH);
		Villager builder = helper.spawn(EntityType.VILLAGER, new BlockPos(3, 2, 14));
		Builders.employ(level, builder, helper.absolutePos(new BlockPos(2, 2, 14)));
		BlockPos hallPos = helper.absolutePos(HALL);

		ItemStack folly = placed(helper, level, hallPos, FOLLY, helper.absolutePos(new BlockPos(4, 2, 4)));
		helper.assertTrue(WonderStages.handOver(owner, builder, folly) == InteractionResult.SUCCESS, "the folly wasn't handed over: " + all(ownerSaw));
		List<WonderSite> here = WonderManager.get(level).in(hallPos);
		helper.assertTrue(here.size() == 1, "not begun: " + here);
		WonderSite site = here.get(0);
		Leftovers.after(helper, () -> {
			site.stone().ifPresent(s -> level.setBlockAndUpdate(s, Blocks.AIR.defaultBlockState()));
			forget(level, site.id(), site.stone().orElse(BlockPos.ZERO));
		});

		ResourceLocation other = Wonders.loaded().keySet().stream().filter(id -> !id.equals(FOLLY)).sorted().findFirst().orElseThrow();
		ItemStack second = placed(helper, level, hallPos, other, helper.absolutePos(new BlockPos(4, 2, 4)).offset(40, 0, 0));
		ownerSaw.clear();
		helper.assertTrue(WonderStages.handOver(owner, builder, second) == InteractionResult.CONSUME && second.getCount() == 1
			&& WonderManager.get(level).in(hallPos).size() == 1, "a second Wonder was begun while the folly goes up");
		helper.assertTrue(said(ownerSaw, "The Test Folly is still going up: one Wonder at a time."), "the owner was told: " + all(ownerSaw));

		ServerPlayer stranger = WonderFinishedGameTests.listener(helper, strangerSaw, "QaStrangerTwo");
		helper.assertTrue(WonderStages.handOver(stranger, builder, second) == InteractionResult.CONSUME && second.getCount() == 1
			&& WonderManager.get(level).in(hallPos).size() == 1, "a stranger handed a plan to the owner's builder");
		helper.assertTrue(said(strangerSaw, "works for someone else: hand the plan to one of your own builders"), "the stranger was told: " + all(strangerSaw));
		for (Component c : ownerSaw) {
			helper.assertTrue(!c.getString().contains("aliveworkplace."), "a raw key: " + c.getString());
		}
		helper.succeed();
	}

	/** A Wonder Plan of {@code wonder} for the hall at {@code hall}, placed with its origin at {@code origin}. */
	static ItemStack placed(GameTestHelper helper, ServerLevel level, BlockPos hall, ResourceLocation wonder, BlockPos origin) {
		Wonder w = Wonders.loaded().get(wonder);
		helper.assertTrue(w != null, "no Wonder " + wonder + ": " + Wonders.problems());
		ItemStack plan = WonderPlanItem.of(level, hall, w, Component.literal("Testholm"));
		BlueprintData data = BlueprintItem.data(plan).orElseThrow();
		plan.set(ModComponents.BLUEPRINT, data.withPlacement(Optional.of(new BlueprintData.Placement(level.dimension().location(), origin, Rotation.NONE, Mirror.NONE))));
		return plan;
	}
}
