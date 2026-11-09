package io.github.jcondedata.aliveworkplace.compat;

import io.github.jcondedata.aliveworkplace.berry.BerryBreeders;
import io.github.jcondedata.aliveworkplace.habitat.HabitatKeepers;
import io.github.jcondedata.aliveworkplace.registry.ModBlocks;
import io.github.jcondedata.aliveworkplace.registry.ModVillagers;
import io.github.jcondedata.aliveworkplace.smith.BallSmiths;
import io.github.jcondedata.aliveworkplace.work.ChoiceList;
import io.github.jcondedata.aliveworkplace.work.ChoiceMenu;
import io.github.jcondedata.aliveworkplace.work.ChoiceView;
import io.github.jcondedata.aliveworkplace.work.Jobs;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import java.util.ArrayList;
import java.util.List;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.network.protocol.game.ServerboundContainerClickPacket;
import net.minecraft.network.protocol.game.ServerboundInteractPacket;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.village.poi.PoiTypes;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;

/**
 * The Cobblemon job menus on the list screen (ROADMAP 42.3), through the player's own clicks: a sneak-right-click with an
 * empty hand opens the Ball Smith's orders, the Berry Breeder's book and the Habitat Keeper's lures on the list menu type;
 * each row says what the chest's icon said (picked or not, found or not, her lure now), and each click is the chest click
 * it was (with its refusals). The plain menus are JobListMenusGameTests.
 */
public class JobListMenusCompatTests implements FabricGameTest {
	private static final String AREA = CompatGameTests.AREA;

	/** A sneak-right-click with an empty hand on {@code villager}, as a client sends it: the list screen it opens. */
	private static ChoiceMenu open(GameTestHelper helper, ServerPlayer player, Villager villager) {
		player.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
		player.moveTo(villager.getX() + 1.5, villager.getY(), villager.getZ(), 90f, 0f);
		player.connection.handleInteract(ServerboundInteractPacket.createInteractionPacket(villager, true, InteractionHand.MAIN_HAND,
			new Vec3(0, villager.getBbHeight() / 2, 0)));
		if (player.containerMenu == player.inventoryMenu) {
			player.connection.handleInteract(ServerboundInteractPacket.createInteractionPacket(villager, true, InteractionHand.MAIN_HAND));
		}
		player.setShiftKeyDown(false);
		helper.assertTrue(player.containerMenu instanceof ChoiceMenu, "a sneak-right-click didn't open the screen: " + player.containerMenu);
		ChoiceMenu menu = (ChoiceMenu) player.containerMenu;
		helper.assertTrue(menu.getType() == ModBlocks.CHOICE_LIST_MENU, "opened as " + menu.getType() + ", not the list");
		var view = ModBlocks.CHOICE_LIST_MENU.create(7, player.getInventory());
		helper.assertTrue(view instanceof ChoiceView && view.slots.size() == menu.slots.size(), "the client's copy is " + view);
		return menu;
	}

	/** A left click on {@code slot}, as the list screen sends it (the chest click on that slot). */
	private static void click(ServerPlayer p, ChoiceMenu menu, int slot) {
		p.connection.handleContainerClick(new ServerboundContainerClickPacket(menu.containerId, p.containerMenu.getStateId(), slot, 0, ClickType.PICKUP,
			ItemStack.EMPTY, new Int2ObjectOpenHashMap<>()));
	}

	private static ChoiceList.Mark row(GameTestHelper helper, ChoiceMenu menu, int slot) {
		ChoiceList.Mark mark = ChoiceList.mark(menu.icon(slot));
		helper.assertTrue(mark != null && mark.kind() == ChoiceList.Kind.ROW, "slot " + slot + " isn't a list row: " + mark + " " + menu.icon(slot));
		return mark;
	}

	private static ChoiceList.Info info(GameTestHelper helper, ChoiceMenu menu) {
		for (int slot = 0; slot < ChoiceMenu.SIZE; slot++) {
			ChoiceList.Info info = ChoiceList.info(menu.icon(slot));
			if (info != null) {
				return info;
			}
		}
		throw new AssertionError("the menu has no line of its own (money, hint, headings)");
	}

	private static String key(Component c) {
		return c != null && c.getContents() instanceof TranslatableContents t ? t.getKey() : String.valueOf(c);
	}

	private static int slotOf(ChoiceMenu menu, net.minecraft.world.item.Item item, int from, int to) {
		for (int slot = from; slot < to; slot++) {
			if (menu.icon(slot).is(item)) {
				return slot;
			}
		}
		return -1;
	}

	/**
	 * The Ball Smith's orders: "Makes any ball" at the top, a row per ball with Pick; a click picks the Azure Ball (its row
	 * gold, Unpick, the top "Makes the picked balls"), a second click unpicks it. A player who isn't her employer or a friend
	 * is refused the screen, as before.
	 */
	//$ gametest AREA
	@GameTest(template = AREA)
	public void smithOrdersAsAList(GameTestHelper helper) {
		helper.setDayTime(2000);
		BlockPos bench = new BlockPos(2, 2, 2);
		helper.setBlock(bench, ModBlocks.BALL_WORKBENCH);
		Villager smith = helper.spawn(EntityType.VILLAGER, new BlockPos(3, 2, 3));
		Jobs.employ(helper.getLevel(), smith, helper.absolutePos(bench), ModVillagers.BALL_WORKBENCH_POI, ModVillagers.BALL_SMITH);
		smith.setNoAi(true);
		ServerPlayer player = TutorListMenuCompatTests.player(helper, new ArrayList<>());
		ChoiceMenu menu = open(helper, player, smith);
		ChoiceList.Info info = info(helper, menu);
		helper.assertTrue(key(info.money()).equals("screen.aliveworkplace.ball_smith.mode_any") && key(info.hint()).equals("screen.aliveworkplace.ball_smith.list_hint")
			&& info.columns().size() == 3, "the smith's own line: " + info);
		int azure = slotOf(menu, com.cobblemon.mod.common.CobblemonItems.AZURE_BALL, BallSmiths.FIRST_BALL_SLOT, ChoiceMenu.SIZE);
		helper.assertTrue(azure >= 0, "no Azure Ball row");
		ChoiceList.Mark mark = row(helper, menu, azure);
		helper.assertTrue(!mark.on() && !mark.off() && key(mark.button()).equals("screen.aliveworkplace.orders.button_pick")
			&& key(mark.second()).equals("screen.aliveworkplace.ball_smith.off"), "the Azure Ball's row before a click: " + mark);
		click(player, menu, azure);
		helper.assertTrue(BallSmiths.orders(smith).equals(List.of(BuiltInRegistries.ITEM.getKey(com.cobblemon.mod.common.CobblemonItems.AZURE_BALL))),
			"orders after the click: " + BallSmiths.orders(smith));
		mark = row(helper, menu, azure);
		helper.assertTrue(mark.on() && key(mark.button()).equals("screen.aliveworkplace.orders.button_unpick")
			&& key(mark.second()).equals("screen.aliveworkplace.ball_smith.on"), "the picked row: " + mark);
		helper.assertTrue(key(info(helper, menu).money()).equals("screen.aliveworkplace.ball_smith.mode_only"), "the top after a pick: " + info(helper, menu));
		// A ball beyond a Novice smith's level says the level it needs, in the price column
		boolean needsLevel = false;
		for (int slot = BallSmiths.FIRST_BALL_SLOT; slot < ChoiceMenu.SIZE; slot++) {
			ChoiceList.Mark m = ChoiceList.mark(menu.icon(slot));
			needsLevel |= m != null && m.price() != null && key(m.price()).startsWith("merchant.level.");
		}
		helper.assertTrue(needsLevel, "no ball says the level it needs");
		click(player, menu, azure);
		helper.assertTrue(BallSmiths.orders(smith).isEmpty() && !row(helper, menu, azure).on(), "a second click didn't unpick: " + BallSmiths.orders(smith));
		player.closeContainer();
		// Someone else's smith: refused, no screen
		List<String> seen = new ArrayList<>();
		ServerPlayer stranger = TutorListMenuCompatTests.player(helper, seen);
		stranger.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
		stranger.moveTo(smith.getX() + 1.5, smith.getY(), smith.getZ(), 90f, 0f);
		stranger.connection.handleInteract(ServerboundInteractPacket.createInteractionPacket(smith, true, InteractionHand.MAIN_HAND,
			new Vec3(0, smith.getBbHeight() / 2, 0)));
		helper.assertTrue(!(stranger.containerMenu instanceof ChoiceMenu), "a stranger got the orders screen");
		helper.succeed();
	}

	/**
	 * The berry book: the found count at the top, her goal as the footer; a row per berry ("Found" for Oran in the chest,
	 * "Not found yet" for Lum, the first pair that makes it) with Set goal; a click makes Lum her goal (gold, "Her goal"), and the
	 * Clear row comes first in the list and clears it.
	 */
	//$ gametest_batch AREA '"jobListBerry"'
	@GameTest(template = AREA, batch = "jobListBerry")
	public void berryBookAsAList(GameTestHelper helper) {
		helper.setDayTime(2000);
		BlockPos composter = new BlockPos(2, 2, 2);
		BlockPos chestPos = new BlockPos(2, 2, 4);
		helper.setBlock(composter, Blocks.COMPOSTER);
		helper.setBlock(chestPos, Blocks.CHEST);
		net.minecraft.world.Container chest = helper.getBlockEntity(chestPos);
		chest.setItem(0, new ItemStack(BuiltInRegistries.ITEM.get(berry("oran")), 4));
		chest.setItem(1, new ItemStack(BuiltInRegistries.ITEM.get(berry("cheri")), 4));
		helper.setBlock(new BlockPos(1, 2, 7), ModBlocks.VILLAGE_HALL);
		Villager breeder = helper.spawn(EntityType.VILLAGER, new BlockPos(3, 2, 2));
		Jobs.employ(helper.getLevel(), breeder, helper.absolutePos(composter), PoiTypes.FARMER, ModVillagers.BERRY_BREEDER);
		breeder.setNoAi(true);
		helper.assertTrue(BerryBreeders.isBreeder(breeder), "not a berry breeder");
		ServerPlayer player = TutorListMenuCompatTests.player(helper, new ArrayList<>());
		ChoiceMenu menu = open(helper, player, breeder);
		ChoiceList.Info info = info(helper, menu);
		helper.assertTrue(key(info.money()).equals("message.aliveworkplace.berry_breeder.book.found_short") && info.columns().size() == 3
			&& key(info.hint()).equals("message.aliveworkplace.berry_breeder.goal.none"), "the book's own line: " + info);
		int lum = find(menu, player, berry("lum"));
		helper.assertTrue(lum >= 0, "Lum isn't in the book");
		ChoiceList.Mark mark = row(helper, menu, lum);
		helper.assertTrue(key(mark.second()).equals("message.aliveworkplace.berry_breeder.book.unfound")
			&& key(mark.price()).equals("message.aliveworkplace.berry_breeder.book.pair")
			&& key(mark.button()).equals("message.aliveworkplace.berry_breeder.book.button_set") && !mark.on(),
			"Lum's row: " + mark);
		click(player, menu, lum);
		helper.assertTrue(berry("lum").equals(BerryBreeders.goal(breeder)), "her goal after the click: " + BerryBreeders.goal(breeder));
		lum = find(menu, player, berry("lum"));
		mark = row(helper, menu, lum);
		helper.assertTrue(mark.on() && key(mark.button()).equals("message.aliveworkplace.berry_breeder.book.button_goal"), "Lum's row as her goal: " + mark);
		helper.assertTrue(key(info(helper, menu).hint()).startsWith("message.aliveworkplace.berry_breeder.goal."), "the footer: " + info(helper, menu).hint());
		ChoiceList.Mark clear = row(helper, menu, 48);
		helper.assertTrue(clear.order() < 0 && key(clear.button()).equals("message.aliveworkplace.berry_breeder.book.button_clear"), "the Clear row: " + clear);
		int oran = find(menu, player, berry("oran"));
		helper.assertTrue(key(row(helper, menu, oran).second()).equals("message.aliveworkplace.berry_breeder.book.lit_short"), "Oran (in the chest) isn't Found");
		click(player, menu, 48);
		helper.assertTrue(BerryBreeders.goal(breeder) == null && ChoiceList.mark(menu.icon(48)) == null, "Clear left her goal at " + BerryBreeders.goal(breeder));
		helper.succeed();
	}

	private static ResourceLocation berry(String name) {
		return ResourceLocation.fromNamespaceAndPath("cobblemon", name + "_berry");
	}

	/** {@code berry}'s slot in the open book, turning pages (the Next page arrow, a page mark) until it's there; -1 if not. */
	private static int find(ChoiceMenu menu, ServerPlayer player, ResourceLocation berry) {
		ItemStack want = new ItemStack(BuiltInRegistries.ITEM.get(berry));
		for (int turn = 0; turn < 6; turn++) {
			for (int slot = 0; slot < 45; slot++) {
				if (ItemStack.isSameItem(menu.icon(slot), want)) {
					return slot;
				}
			}
			ChoiceList.Mark next = ChoiceList.mark(menu.icon(53));
			if (next == null || next.kind() != ChoiceList.Kind.PAGE || !next.on()) {
				return -1;
			}
			click(player, menu, 53);
		}
		return -1;
	}

	/**
	 * The lures: her lure now at the top ("any snack" first); a row per lure with its berries and Choose; a click on Fire
	 * makes it her lure (gold, Chosen; the top says Fire type), and "Any snack", listed first, puts her back.
	 */
	//$ gametest_batch AREA '"jobListLures"'
	@GameTest(template = AREA, batch = "jobListLures")
	public void habitatLuresAsAList(GameTestHelper helper) {
		Villager keeper = HabitatKeeperCompatTests.keeper(helper);
		keeper.setNoAi(true);
		List<String> seen = new ArrayList<>();
		ServerPlayer player = TutorListMenuCompatTests.player(helper, seen);
		ChoiceMenu menu = open(helper, player, keeper);
		ChoiceList.Info info = info(helper, menu);
		helper.assertTrue(key(info.money()).equals("message.aliveworkplace.habitat_keeper.lures.now") && info.columns().size() == 2
			&& key(info.hint()).equals("message.aliveworkplace.habitat_keeper.lures.list_hint"), "the lures' own line: " + info);
		ChoiceList.Mark any = row(helper, menu, HabitatKeepers.ANY_SLOT);
		helper.assertTrue(any.on() && any.order() < 0 && key(any.button()).equals("message.aliveworkplace.habitat_keeper.lures.button_chosen"),
			"Any snack with no lure: " + any);
		int fire = HabitatKeepers.lures().indexOf("typing/fire");
		helper.assertTrue(fire >= 0 && fire < 45, "Fire isn't on the first page: " + HabitatKeepers.lures());
		ChoiceList.Mark mark = row(helper, menu, fire);
		helper.assertTrue(!mark.on() && mark.second().getString().contains("Occa") && key(mark.button()).equals("message.aliveworkplace.habitat_keeper.lures.button_choose"),
			"Fire's row: " + mark);
		click(player, menu, fire);
		helper.assertTrue("typing/fire".equals(HabitatKeepers.lure(keeper)), "her lure after the click: " + HabitatKeepers.lure(keeper));
		mark = row(helper, menu, fire);
		helper.assertTrue(mark.on() && key(mark.button()).equals("message.aliveworkplace.habitat_keeper.lures.button_chosen"), "Fire's row once chosen: " + mark);
		helper.assertTrue(!row(helper, menu, HabitatKeepers.ANY_SLOT).on(), "Any snack still gold");
		helper.assertTrue(info(helper, menu).money().getString().contains("Fire"), "the top: " + info(helper, menu).money().getString());
		helper.assertTrue(seen.stream().anyMatch(s -> s.contains("Fire")), "she didn't say her lure: " + seen);
		click(player, menu, HabitatKeepers.ANY_SLOT);
		helper.assertTrue(HabitatKeepers.lure(keeper) == null && row(helper, menu, HabitatKeepers.ANY_SLOT).on(), "Any snack left her lure at " + HabitatKeepers.lure(keeper));
		helper.succeed();
	}
}
