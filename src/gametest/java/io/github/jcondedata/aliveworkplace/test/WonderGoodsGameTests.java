package io.github.jcondedata.aliveworkplace.test;

import io.github.jcondedata.aliveworkplace.blueprint.BlueprintData;
import io.github.jcondedata.aliveworkplace.build.BuildSite;
import io.github.jcondedata.aliveworkplace.build.BuildSiteManager;
import io.github.jcondedata.aliveworkplace.guard.GuardRally;
import io.github.jcondedata.aliveworkplace.hall.Chronicle;
import io.github.jcondedata.aliveworkplace.hall.VillageHallBlockEntity;
import io.github.jcondedata.aliveworkplace.hall.VillageQuests;
import io.github.jcondedata.aliveworkplace.hall.VillageRanks;
import io.github.jcondedata.aliveworkplace.mc.Ids;
import io.github.jcondedata.aliveworkplace.registry.ModAttachments;
import io.github.jcondedata.aliveworkplace.registry.ModBlocks;
import io.github.jcondedata.aliveworkplace.registry.ModVillagers;
import io.github.jcondedata.aliveworkplace.store.StorehouseBoard;
import io.github.jcondedata.aliveworkplace.wonder.Dedications;
import io.github.jcondedata.aliveworkplace.wonder.Wonder;
import io.github.jcondedata.aliveworkplace.wonder.WonderGoods;
import io.github.jcondedata.aliveworkplace.wonder.WonderManager;
import io.github.jcondedata.aliveworkplace.wonder.WonderSite;
import io.github.jcondedata.aliveworkplace.wonder.WonderStages;
import io.github.jcondedata.aliveworkplace.wonder.Wonders;
import io.github.jcondedata.aliveworkplace.work.ChoiceMenu;
import io.github.jcondedata.aliveworkplace.work.Jobs;
import io.github.jcondedata.aliveworkplace.work.Requests;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponents;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.alchemy.PotionContents;
import net.minecraft.world.item.alchemy.Potions;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

/**
 * A Wonder's goods (ROADMAP 35.7): the folly's stage II (three lines) doesn't start until all three are in, and its
 * dedication takes exactly them; a porter carries bread from the storehouse to the stockpile; a chef cooks a short line
 * as a stock order; a click on the requests board hands a line over; anyone can hand goods in at the stone; a cancel
 * gives them back (and nothing for a dedicated stage); the dedication waits for the morning, gathers the village (the
 * guards in line, the Bard beside the stone) and ends when the stone is broken; the goods survive a save and load and old
 * saves read with the defaults; with Wonders off nothing is taken or dedicated; the stone shows every line and who's
 * missing; the hall's daily quest asks for the line furthest behind.
 */
public class WonderGoodsGameTests implements FabricGameTest {
	private static final String AREA = "aliveworkplace_test:build_area";
	private static final String BIG = "aliveworkplace_test:big_area";
	private static final String HUGE = "aliveworkplace_test:huge_area";
	private static final ResourceLocation FOLLY = ResourceLocation.fromNamespaceAndPath("aliveworkplace_test", "test_folly");

	/** A village and its folly waiting at a stage: the owner, the hall, the stone, the stockpile's chest, the Wonder. */
	private record W(ServerLevel level, ServerPlayer owner, BlockPos hall, BlockPos stone, Container pile, UUID id) {
		WonderSite site() {
			return WonderManager.get(level).site(id).orElseThrow();
		}
	}

	/**
	 * A Town owned by a player round {@code hallRel}, and the folly begun with its stone at {@code stoneRel} (its corner
	 * one block in from it), a chest of its stockpile at {@code pileRel}, at stage {@code stage} waiting for its goods.
	 */
	private static W village(GameTestHelper helper, int stage, BlockPos hallRel, BlockPos stoneRel, BlockPos pileRel) {
		Leftovers.clear(helper);
		ServerLevel level = helper.getLevel();
		helper.setDayTime(2000);
		helper.setBlock(hallRel, ModBlocks.VILLAGE_HALL);
		ServerPlayer owner = ProtectionSpecGameTests.player(helper);
		VillageHallBlockEntity hall = helper.getBlockEntity(hallRel);
		hall.setOwner(owner.getUUID(), "Owner");
		hall.setRank(VillageRanks.Rank.TOWN);
		helper.setBlock(stoneRel, ModBlocks.FOUNDATION_STONE);
		helper.setBlock(pileRel, Blocks.CHEST);
		BlockPos stone = helper.absolutePos(stoneRel);
		WonderManager manager = WonderManager.get(level);
		WonderSite site = manager.begin(FOLLY, helper.absolutePos(hallRel), owner.getUUID(), "Owner", Chronicle.day(level));
		Leftovers.after(helper, () -> forget(level, site.id()));
		manager.place(site.id(), new BlueprintData.Placement(Ids.of(level.dimension()), stone.offset(1, 0, 1), Rotation.NONE, Mirror.NONE), stone);
		manager.setStage(site.id(), stage);
		return new W(level, owner, helper.absolutePos(hallRel), stone, helper.getBlockEntity(pileRel), site.id());
	}

	private static W village(GameTestHelper helper, int stage) {
		return village(helper, stage, new BlockPos(14, 2, 14), new BlockPos(2, 2, 2), new BlockPos(2, 2, 5));
	}

	private static void forget(ServerLevel level, UUID wonder) {
		BuildSiteManager sites = BuildSiteManager.get(level);
		for (BuildSite s : List.copyOf(sites.all())) {
			if (wonder.equals(s.wonder())) {
				sites.remove(s.id());
			}
		}
		Requests.clearAt(level, WonderManager.get(level).site(wonder).flatMap(WonderSite::stone).orElse(BlockPos.ZERO));
		WonderManager.get(level).remove(wonder);
	}

	private static ItemStack healing(int n) {
		return PotionContents.createItemStack(Items.POTION, Potions.HEALING).copyWithCount(n);
	}

	private static int handed(WonderSite site, int line) {
		return site.delivered().stream().filter(h -> h.line() == line).mapToInt(WonderSite.Handed::count).sum();
	}

	private static int count(Container box, java.util.function.Predicate<ItemStack> test) {
		int n = 0;
		for (int i = 0; i < box.getContainerSize(); i++) {
			if (test.test(box.getItem(i))) {
				n += box.getItem(i).getCount();
			}
		}
		return n;
	}

	private static int count(ServerPlayer player, net.minecraft.world.item.Item item) {
		return player.getInventory().countItem(item);
	}

	/** Every translation key in {@code c} and its siblings and arguments. */
	private static List<String> keys(Component c) {
		List<String> out = new ArrayList<>();
		if (c.getContents() instanceof TranslatableContents t) {
			out.add(t.getKey());
			for (Object arg : t.getArgs()) {
				if (arg instanceof Component a) {
					out.addAll(keys(a));
				}
			}
		}
		for (Component s : c.getSiblings()) {
			out.addAll(keys(s));
		}
		return out;
	}

	private static boolean chronicled(W w, String key) {
		VillageHallBlockEntity hall = (VillageHallBlockEntity) w.level().getBlockEntity(w.hall());
		return hall.chronicle().stream().anyMatch(e -> e.kind() == Chronicle.Kind.WONDER && keys(e.text()).contains(key));
	}

	private static List<Requests.Request> posts(W w) {
		return Requests.atBlocks(w.level(), w.stone(), 4).stream().filter(Requests.Request::forBlock).toList();
	}

	// ---------------------------------------------------------------- a stage waits for all its goods

	/**
	 * Stage I done, the folly's stage II waits for its three lines (flowers, potions of healing, books), each posted for
	 * the stone; one line, two, a line one short or the wrong potion don't start it; with all three in, it is dedicated:
	 * exactly its goods are taken from the stockpile (the spare flowers and book, and the water bottle, stay), its part
	 * goes down, the chronicle says so and the posts come down.
	 */
	//$ gametest_batch AREA '"wonderGoodsStageTwo"'
	@GameTest(template = AREA, batch = "wonderGoodsStageTwo")
	public void stageTwoWaitsForAllThreeLines(GameTestHelper helper) {
		W w = village(helper, 1);
		ServerLevel level = w.level();
		Wonder folly = Wonders.loaded().get(FOLLY);
		helper.assertTrue(folly.stages().get(1).goods().size() == 3, "the folly's stage II has three lines: " + folly.stages().get(1).goods());
		List<BuildSite> first = WonderStages.start(level, w.id(), w.site().placement().orElseThrow(), w.stone(), null);
		for (BuildSite part : first) {
			for (int i = 0; i < 20 && !part.isDone(); i++) {
				part.finishList();
			}
		}
		WonderStages.tick(level);
		helper.assertTrue(w.site().stage() == 2 && !w.site().started() && w.site().parts().isEmpty(), "stage II began without its goods: " + w.site());
		helper.assertTrue(posts(w).size() == 3, "stage II's three lines aren't on the board: " + posts(w).size());

		w.pile().setItem(0, new ItemStack(Items.POPPY, 6)); // two spare
		WonderStages.tick(level);
		helper.assertTrue(!w.site().started(), "stage II began with only the flowers in");
		helper.assertTrue(posts(w).size() == 2, "the flowers' line is still posted: " + posts(w).size());

		w.pile().setItem(1, healing(1));
		w.pile().setItem(2, healing(1));
		w.pile().setItem(3, PotionContents.createItemStack(Items.POTION, Potions.WATER)); // not a potion of healing
		w.pile().setItem(4, new ItemStack(Items.BOOK, 1)); // one short
		WonderStages.tick(level);
		helper.assertTrue(!w.site().started() && w.site().parts().isEmpty(), "stage II began with a book short");
		List<WonderGoods.Line> lines = WonderGoods.lines(level, w.site());
		helper.assertTrue(lines.get(0).full() && lines.get(1).full() && lines.get(2).in() == 1 && lines.get(2).left() == 1,
			"the lines as counted: " + lines);

		w.pile().setItem(5, new ItemStack(Items.BOOK, 2)); // one spare
		WonderStages.tick(level);
		WonderSite now = w.site();
		helper.assertTrue(now.stage() == 2 && now.started() && now.dedicated() && now.parts().size() == 1, "stage II wasn't dedicated: " + now);
		helper.assertTrue(count(w.pile(), s -> s.is(Items.POPPY)) == 2 && count(w.pile(), s -> s.is(Items.BOOK)) == 1
				&& count(w.pile(), s -> WonderGoods.matches(folly.stages().get(1).goods().get(1), s)) == 0
				&& count(w.pile(), s -> s.is(Items.POTION)) == 1,
			"the dedication didn't take exactly the goods: " + count(w.pile(), s -> s.is(Items.POPPY)) + " poppies, "
				+ count(w.pile(), s -> s.is(Items.BOOK)) + " books, " + count(w.pile(), s -> s.is(Items.POTION)) + " potions left");
		helper.assertTrue(chronicled(w, "chronicle.aliveworkplace.wonder.dedicated"), "the chronicle doesn't say so");
		helper.assertTrue(posts(w).isEmpty(), "the lines are still on the board");
		helper.assertTrue(Dedications.isOn(level, w.id()), "no dedication going on");
		WonderStages.tick(level);
		helper.assertTrue(w.site().parts().equals(now.parts()) && count(w.pile(), s -> s.is(Items.POPPY)) == 2, "dedicated twice");
		helper.succeed();
	}

	// ---------------------------------------------------------------- the village sees to it

	/** A porter takes the eight bread stage I lacks from the storehouse (and only those) to the stockpile, and the stage is dedicated. */
	//$ gametest_ticks_batch HUGE '3000' '"wonderGoodsPorter"'
	@GameTest(template = HUGE, timeoutTicks = 3000, batch = "wonderGoodsPorter")
	public void aPorterCarriesBreadToTheStockpile(GameTestHelper helper) {
		W w = village(helper, 1, new BlockPos(14, 2, 26), new BlockPos(3, 2, 3), new BlockPos(3, 2, 6));
		ServerLevel level = w.level();
		BlockPos storehouse = new BlockPos(24, 2, 24);
		helper.setBlock(storehouse, ModBlocks.STOREHOUSE);
		helper.setBlock(storehouse.east(), Blocks.CHEST);
		Container store = helper.getBlockEntity(storehouse.east());
		store.setItem(0, new ItemStack(Items.BREAD, 12));
		Villager porter = helper.spawn(EntityType.VILLAGER, storehouse.south());
		Jobs.employ(level, porter, helper.absolutePos(storehouse), ModVillagers.STOREHOUSE_POI, ModVillagers.PORTER);
		helper.onEachTick(() -> {
			if (level.getGameTime() % 200 == 0) {
				helper.setDayTime(2000);
			}
		});
		helper.succeedWhen(() -> {
			helper.assertTrue(w.site().started(), "stage I still waits: " + WonderGoods.lines(level, w.site()) + ", porter at "
				+ helper.relativePos(porter.blockPosition()) + ", bag " + ModAttachments.BUILDER_BAG.getOrCreate(porter).stacks());
			helper.assertTrue(count(store, s -> s.is(Items.BREAD)) == 4, "the porter took " + (12 - count(store, s -> s.is(Items.BREAD))) + " bread, not 8");
			helper.assertTrue(count(w.pile(), s -> s.is(Items.BREAD)) == 0, "the dedication left bread in the stockpile");
			helper.assertTrue(ModAttachments.ITEMS_CARRIED.getOrElse(porter, 0) >= 8, "the porter carried " + ModAttachments.ITEMS_CARRIED.getOrElse(porter, 0));
		});
	}

	/** A chef cooks the eight bread stage I lacks from the wheat by the stove, as a stock order for the stockpile. */
	//$ gametest_ticks_batch HUGE '3000' '"wonderGoodsChef"'
	@GameTest(template = HUGE, timeoutTicks = 3000, batch = "wonderGoodsChef")
	public void aChefCooksAShortLine(GameTestHelper helper) {
		W w = village(helper, 1, new BlockPos(14, 2, 26), new BlockPos(3, 2, 3), new BlockPos(3, 2, 6));
		ServerLevel level = w.level();
		BlockPos stove = new BlockPos(24, 2, 24);
		helper.setBlock(stove, ModBlocks.KITCHEN_STOVE);
		helper.setBlock(stove.south(2), Blocks.CHEST);
		Container own = helper.getBlockEntity(stove.south(2));
		own.setItem(0, new ItemStack(Items.WHEAT, 24));
		Villager chef = helper.spawn(EntityType.VILLAGER, stove.east());
		Jobs.employ(level, chef, helper.absolutePos(stove), ModVillagers.KITCHEN_STOVE_POI, ModVillagers.CHEF);
		helper.onEachTick(() -> {
			if (level.getGameTime() % 200 == 0) {
				helper.setDayTime(2000);
			}
		});
		helper.succeedWhen(() -> {
			helper.assertTrue(w.site().started(), "stage I still waits: " + WonderGoods.lines(level, w.site()) + "; by the stove "
				+ count(own, s -> s.is(Items.WHEAT)) + " wheat, " + count(own, s -> s.is(Items.BREAD)) + " bread");
			helper.assertTrue(count(own, s -> s.is(Items.WHEAT)) == 0 && count(own, s -> s.is(Items.BREAD)) == 0,
				"the bread didn't all go to the stockpile: by the stove " + count(own, s -> s.is(Items.WHEAT)) + " wheat, " + count(own, s -> s.is(Items.BREAD)) + " bread");
			helper.assertTrue(ModAttachments.ITEMS_CRAFTED.getOrElse(chef, 0) >= 8, "the chef cooked " + ModAttachments.ITEMS_CRAFTED.getOrElse(chef, 0));
		});
	}

	/**
	 * The stone's lines are on a Storehouse's board, for the Wonder and naming the job; a click hands the line over (only
	 * what it lacks), and with stage I's goods in, it's dedicated.
	 */
	//$ gametest_batch AREA '"wonderGoodsBoard"'
	@GameTest(template = AREA, batch = "wonderGoodsBoard")
	public void aClickOnTheBoardHandsALineOver(GameTestHelper helper) {
		W w = village(helper, 1);
		ServerLevel level = w.level();
		BlockPos storehouse = new BlockPos(14, 2, 4);
		helper.setBlock(storehouse, ModBlocks.STOREHOUSE);
		helper.setBlock(storehouse.east(), Blocks.CHEST);
		w.owner().getInventory().add(new ItemStack(Items.BREAD, 10));
		WonderStages.tick(level); // the stone posts its line
		ChoiceMenu board = StorehouseBoard.boardForTest(w.owner(), helper.absolutePos(storehouse));
		int slot = -1;
		for (int i = StorehouseBoard.FIRST_REQUEST; i < ChoiceMenu.SIZE; i++) {
			if (board.icon(i).is(Items.BREAD)) {
				slot = i;
				break;
			}
		}
		helper.assertTrue(slot >= 0, "the bread line isn't on the board");
		ItemStack icon = board.icon(slot);
		String lore = icon.get(DataComponents.LORE).lines().stream().map(Component::getString).toList().toString();
		helper.assertTrue(icon.getHoverName().getString().contains("8") && lore.contains("Test Folly") && lore.contains("Made by the Chef"),
			"the line on the board: " + icon.getHoverName().getString() + " " + lore);
		board.press(slot, w.owner());
		helper.assertTrue(handed(w.site(), 0) == 8 && count(w.owner(), Items.BREAD) == 2, "the click handed over " + handed(w.site(), 0) + ", "
			+ count(w.owner(), Items.BREAD) + " bread left");
		WonderStages.tick(level);
		helper.assertTrue(w.site().started() && w.site().delivered().isEmpty() && w.site().parts().size() == 1, "stage I wasn't dedicated: " + w.site());
		helper.assertTrue(count(w.owner(), Items.BREAD) == 2, "the dedication took the player's bread");
		helper.succeed();
	}

	/**
	 * Anyone can hand goods in at the stone (a stranger's flowers, up to what the line lacks; dirt isn't taken); the
	 * owner's sneak-break hands them back (and the book), what lies in the chests stays; and goods that fill a line are
	 * refused.
	 */
	//$ gametest_batch AREA '"wonderGoodsCancel"'
	@GameTest(template = AREA, batch = "wonderGoodsCancel")
	public void aCancelGivesThemBack(GameTestHelper helper) {
		W w = village(helper, 2);
		ServerLevel level = w.level();
		ServerPlayer stranger = ProtectionSpecGameTests.player(helper);
		BlockHitResult hit = new BlockHitResult(Vec3.atCenterOf(w.stone()), Direction.UP, w.stone(), false);
		stranger.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.DIRT, 3));
		helper.assertTrue(level.getBlockState(w.stone()).useItemOn(stranger.getMainHandItem(), level, stranger, InteractionHand.MAIN_HAND, hit)
			== ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION && stranger.getMainHandItem().getCount() == 3, "the stone took dirt");
		stranger.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.POPPY, 6));
		helper.assertTrue(level.getBlockState(w.stone()).useItemOn(stranger.getMainHandItem(), level, stranger, InteractionHand.MAIN_HAND, hit)
			== ItemInteractionResult.SUCCESS, "the stranger's flowers weren't taken");
		helper.assertTrue(stranger.getMainHandItem().getCount() == 2 && handed(w.site(), 0) == 4, "took " + handed(w.site(), 0) + " flowers, left "
			+ stranger.getMainHandItem().getCount());
		helper.assertTrue(level.getBlockState(w.stone()).useItemOn(stranger.getMainHandItem(), level, stranger, InteractionHand.MAIN_HAND, hit)
			== ItemInteractionResult.SUCCESS && stranger.getMainHandItem().getCount() == 2 && handed(w.site(), 0) == 4, "a full line took more");
		w.owner().setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.BOOK, 1));
		level.getBlockState(w.stone()).useItemOn(w.owner().getMainHandItem(), level, w.owner(), InteractionHand.MAIN_HAND, hit);
		helper.assertTrue(handed(w.site(), 2) == 1 && w.owner().getMainHandItem().isEmpty(), "the owner's book wasn't taken");
		w.pile().setItem(0, healing(1));

		w.owner().setShiftKeyDown(true);
		helper.assertTrue(WonderStages.breakStone(level, w.owner(), w.stone()), "the owner's sneak-break was refused");
		w.owner().setShiftKeyDown(false);
		helper.assertTrue(WonderManager.get(level).site(w.id()).isEmpty(), "the Wonder goes on");
		helper.assertTrue(count(w.owner(), Items.POPPY) == 4 && count(w.owner(), Items.BOOK) == 1, "the goods didn't come back: "
			+ count(w.owner(), Items.POPPY) + " poppies, " + count(w.owner(), Items.BOOK) + " books");
		helper.assertTrue(count(w.pile(), s -> s.is(Items.POTION)) == 1, "the chest's potion was taken");
		helper.assertTrue(posts(w).isEmpty(), "the lines stay on the board");
		helper.succeed();
	}

	// ---------------------------------------------------------------- the dedication

	/** With every good in at night the stage waits, the stone says it's for the morning; by day it is dedicated. */
	//$ gametest_batch AREA '"wonderGoodsNight"'
	@GameTest(template = AREA, batch = "wonderGoodsNight")
	public void theDedicationWaitsForTheMorning(GameTestHelper helper) {
		W w = village(helper, 3);
		ServerLevel level = w.level();
		w.pile().setItem(0, new ItemStack(Items.WHITE_CANDLE, 4));
		helper.setDayTime(14000);
		WonderStages.tick(level);
		helper.assertTrue(!w.site().started(), "dedicated at night");
		helper.assertTrue(keys(WonderStages.statusText(level, w.stone())).contains("message.aliveworkplace.wonder.goods.morning"), "the stone doesn't say it's for the morning");
		helper.setDayTime(24000 + 100);
		WonderStages.tick(level);
		helper.assertTrue(w.site().started() && count(w.pile(), s -> s.is(Items.WHITE_CANDLE)) == 0, "not dedicated in the morning: " + w.site());
		helper.setDayTime(2000);
		helper.succeed();
	}

	/**
	 * The dedication gathers the village: the guard stands in line, the Bard plays beside the stone, a villager off work
	 * comes; the stone broken in the middle of it ends it (the goods stay taken: nothing comes back), and the line breaks up.
	 */
	//$ gametest_ticks_batch BIG '600' '"wonderGoodsBroken"'
	@GameTest(template = BIG, timeoutTicks = 600, batch = "wonderGoodsBroken")
	public void breakingTheStoneMidDedication(GameTestHelper helper) {
		W w = village(helper, 3, new BlockPos(18, 2, 18), new BlockPos(3, 2, 3), new BlockPos(3, 2, 6));
		ServerLevel level = w.level();
		helper.setBlock(new BlockPos(16, 2, 4), ModBlocks.GUARD_POST);
		Villager guard = helper.spawn(EntityType.VILLAGER, new BlockPos(17, 2, 4));
		Jobs.employ(level, guard, helper.absolutePos(new BlockPos(16, 2, 4)), ModVillagers.GUARD_POST_POI, ModVillagers.GUARD);
		helper.setBlock(new BlockPos(16, 2, 10), ModBlocks.MUSIC_STAND);
		Villager bard = helper.spawn(EntityType.VILLAGER, new BlockPos(17, 2, 10));
		Jobs.employ(level, bard, helper.absolutePos(new BlockPos(16, 2, 10)), ModVillagers.MUSIC_STAND_POI, ModVillagers.BARD);
		Villager idle = helper.spawn(EntityType.VILLAGER, new BlockPos(14, 2, 3));
		w.pile().setItem(0, new ItemStack(Items.WHITE_CANDLE, 4));
		WonderStages.tick(level);
		helper.assertTrue(w.site().started() && Dedications.isOn(level, w.id()), "not dedicated: " + w.site());
		helper.assertTrue(GuardRally.inLine(guard), "the guard isn't in line");
		helper.assertTrue(Dedications.bardSpot(level, bard).isPresent(), "the Bard doesn't play at the stone");
		long[] broken = {-1};
		helper.onEachTick(() -> {
			if (broken[0] < 0 && idle.position().distanceTo(Vec3.atCenterOf(w.stone())) < 7) {
				w.owner().setShiftKeyDown(true);
				helper.assertTrue(WonderStages.breakStone(level, w.owner(), w.stone()), "the owner's sneak-break was refused");
				w.owner().setShiftKeyDown(false);
				broken[0] = level.getGameTime();
				helper.assertTrue(!Dedications.isOn(level, w.id()) && WonderManager.get(level).site(w.id()).isEmpty(), "the dedication goes on");
				helper.assertTrue(count(w.owner(), Items.WHITE_CANDLE) == 0, "a dedicated stage's goods came back");
			}
		});
		helper.succeedWhen(() -> {
			helper.assertTrue(broken[0] >= 0, "the villager off work never came to the stone: at " + helper.relativePos(idle.blockPosition()));
			helper.assertTrue(level.getGameTime() - broken[0] > 60 && !GuardRally.inLine(guard) && Dedications.bardSpot(level, bard).isEmpty(),
				"the line hasn't broken up");
		});
	}

	/**
	 * Goods handed in and whether a stage is dedicated read back from a save; a site saved before 35.7 reads with the
	 * defaults (a stage with its parts down counts as started); after the reload the rest goes in and it's dedicated.
	 */
	//$ gametest_batch AREA '"wonderGoodsSave"'
	@GameTest(template = AREA, batch = "wonderGoodsSave")
	public void theGoodsSurviveASaveAndLoad(GameTestHelper helper) {
		W w = village(helper, 2);
		ServerLevel level = w.level();
		var registries = level.registryAccess();
		w.owner().setItemInHand(InteractionHand.MAIN_HAND, healing(2));
		helper.assertTrue(WonderGoods.handInAtStone(w.owner(), w.stone(), w.owner().getMainHandItem()), "the potion line... not handed in");
		w.owner().setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.POPPY, 3));
		WonderGoods.handInAtStone(w.owner(), w.stone(), w.owner().getMainHandItem());
		WonderManager manager = WonderManager.get(level);
		WonderSite before = w.site();
		CompoundTag saved = manager.save(new CompoundTag(), registries);
		WonderManager back = WonderManager.load(saved, registries);
		helper.assertTrue(back.site(w.id()).orElseThrow().equals(before), "read back: " + back.site(w.id()) + " vs " + before);
		helper.assertTrue(handed(before, 0) == 3 && handed(before, 1) == 2 && before.delivered().stream().anyMatch(h -> h.potion().isPresent()), "handed: " + before.delivered());
		// as a 35.5 server saved it: no dedicated, no delivered
		CompoundTag old = saved.copy();
		ListTag list = old.getList("sites", 10);
		for (int i = 0; i < list.size(); i++) {
			list.getCompound(i).remove("dedicated");
			list.getCompound(i).remove("delivered");
		}
		WonderSite plain = WonderManager.load(old, registries).site(w.id()).orElseThrow();
		helper.assertTrue(!plain.dedicated() && plain.delivered().isEmpty() && !plain.started(), "a 35.5 site's defaults: " + plain);
		WonderSite going = new WonderSite(plain.id(), plain.wonder(), plain.hall(), plain.owner(), plain.ownerName(), plain.stage(), plain.begun(),
			plain.finished(), plain.placement(), plain.stone(), List.of(UUID.randomUUID()), plain.builder(), plain.called());
		helper.assertTrue(going.started(), "a 35.5 site with its parts down isn't started");
		// the reload: the manager read back carries on
		level.getDataStorage().set(WonderManager.NAME, back);
		w.pile().setItem(0, new ItemStack(Items.POPPY, 1));
		w.pile().setItem(1, new ItemStack(Items.BOOK, 2));
		WonderStages.tick(level);
		WonderSite now = WonderManager.get(level).site(w.id()).orElseThrow();
		helper.assertTrue(now.started() && now.delivered().isEmpty() && count(w.pile(), s -> !s.isEmpty()) == 0, "not dedicated after the reload: " + now);
		WonderSite again = WonderManager.load(WonderManager.get(level).save(new CompoundTag(), registries), registries).site(w.id()).orElseThrow();
		helper.assertTrue(again.dedicated() && again.equals(now), "the dedication read back: " + again);
		helper.succeed();
	}

	/** With Wonders off nothing is handed in, posted or dedicated; switched back on, the stage is dedicated. */
	//$ gametest_batch AREA '"wonderGoodsOff"'
	@GameTest(template = AREA, batch = "wonderGoodsOff")
	public void withWondersOffNothingIsTaken(GameTestHelper helper) {
		W w = village(helper, 3);
		ServerLevel level = w.level();
		w.pile().setItem(0, new ItemStack(Items.WHITE_CANDLE, 2));
		boolean on = Wonders.ENABLED;
		Wonders.ENABLED = false;
		try {
			WonderStages.tick(level);
			w.owner().setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.WHITE_CANDLE, 2));
			helper.assertTrue(!WonderGoods.handInAtStone(w.owner(), w.stone(), w.owner().getMainHandItem())
				&& WonderGoods.handIn(w.owner(), w.id(), 3, 0, true) == 0 && w.owner().getMainHandItem().getCount() == 2, "handed in with Wonders off");
			w.pile().setItem(1, new ItemStack(Items.WHITE_CANDLE, 2));
			WonderStages.tick(level);
			helper.assertTrue(!w.site().started() && !WonderGoods.dedicate(level, w.site()) && count(w.pile(), s -> s.is(Items.WHITE_CANDLE)) == 4,
				"dedicated with Wonders off");
			helper.assertTrue(posts(w).isEmpty(), "posted with Wonders off");
		} finally {
			Wonders.ENABLED = on;
		}
		WonderStages.tick(level);
		helper.assertTrue(w.site().started() && count(w.pile(), s -> s.is(Items.WHITE_CANDLE)) == 0, "not dedicated once back on: " + w.site());
		helper.succeed();
	}

	// ---------------------------------------------------------------- what players read

	/**
	 * The stone shows every line, "delivered of needed" with the job, and "no Florist in the village" for a line nobody
	 * there makes (not for the cleric's, with a Cleric about); with a line short it says how to hand goods in; once
	 * dedicated, that it is. Every sentence is in the language file.
	 */
	//$ gametest_batch AREA '"wonderGoodsStatus"'
	@GameTest(template = AREA, batch = "wonderGoodsStatus")
	public void theStoneShowsEveryLine(GameTestHelper helper) {
		W w = village(helper, 2);
		ServerLevel level = w.level();
		Villager cleric = helper.spawn(EntityType.VILLAGER, new BlockPos(12, 2, 12));
		cleric.setVillagerData(cleric.getVillagerData().setProfession(net.minecraft.world.entity.npc.VillagerProfession.CLERIC));
		cleric.setNoAi(true);
		w.pile().setItem(0, new ItemStack(Items.POPPY, 2));
		w.owner().setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.BOOK, 1));
		WonderGoods.handInAtStone(w.owner(), w.stone(), w.owner().getMainHandItem());
		Component status = WonderStages.statusText(level, w.stone());
		String text = status.getString();
		List<String> said = keys(status);
		helper.assertTrue(said.contains("message.aliveworkplace.wonder.goods.header") && said.contains("message.aliveworkplace.wonder.goods.how")
			&& said.stream().filter("message.aliveworkplace.wonder.goods.line"::equals).count() == 3, "the stone's goods: " + said);
		helper.assertTrue(text.contains("2 of 4 Flowers · Florist (no Florist in the village)") && text.contains("1 of 2 Book · Librarian (no Librarian in the village)")
			&& text.contains("0 of 2 Potion of Healing · Cleric") && !text.contains("no Cleric"), "the lines as shown: " + text);
		for (String key : said) {
			helper.assertTrue(net.minecraft.locale.Language.getInstance().has(key), "no text for " + key);
		}
		w.pile().setItem(1, new ItemStack(Items.POPPY, 2));
		w.pile().setItem(2, healing(1));
		w.pile().setItem(3, healing(1));
		w.pile().setItem(4, new ItemStack(Items.BOOK, 1));
		WonderStages.tick(level);
		said = keys(WonderStages.statusText(level, w.stone()));
		helper.assertTrue(said.contains("message.aliveworkplace.wonder.goods.dedicated") && !said.contains("message.aliveworkplace.wonder.goods.line"),
			"once dedicated: " + said);
		helper.succeed();
	}

	/**
	 * The hall's daily quest asks for the line furthest behind (the book: the flowers are in, a potion's kind can't be
	 * asked for), as many as it lacks, into the stockpile.
	 */
	//$ gametest_batch AREA '"wonderGoodsQuest"'
	@GameTest(template = AREA, batch = "wonderGoodsQuest")
	public void theHallsQuestAsksForTheShortestLine(GameTestHelper helper) {
		W w = village(helper, 2);
		ServerLevel level = w.level();
		w.pile().setItem(0, new ItemStack(Items.POPPY, 4));
		w.owner().setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.BOOK, 1));
		WonderGoods.handInAtStone(w.owner(), w.stone(), w.owner().getMainHandItem());
		Optional<WonderGoods.Wanted> wanted = WonderGoods.shortest(level, w.hall());
		helper.assertTrue(wanted.isPresent() && wanted.get().item().equals("minecraft:book") && wanted.get().count() == 1, "the shortest line: " + wanted);
		VillageQuests.Quest quest = VillageQuests.make(level, w.hall());
		helper.assertTrue(quest != null && quest.kind() == VillageQuests.Kind.BRING && quest.item().equals("minecraft:book") && quest.count() == 1
			&& quest.deliverTo().equals(Optional.of(w.stone())), "the hall's quest: " + quest);
		helper.succeed();
	}
}
