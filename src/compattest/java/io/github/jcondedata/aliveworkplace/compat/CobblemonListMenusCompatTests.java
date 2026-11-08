package io.github.jcondedata.aliveworkplace.compat;

import com.cobblemon.mod.common.Cobblemon;
import com.cobblemon.mod.common.api.pokemon.PokemonProperties;
import com.cobblemon.mod.common.api.storage.party.PlayerPartyStore;
import com.cobblemon.mod.common.pokemon.Pokemon;
import io.github.jcondedata.aliveworkplace.compat.cobbledollars.CobbleDollarsBank;
import io.github.jcondedata.aliveworkplace.compat.cobblemon.CobblemonDaycare;
import io.github.jcondedata.aliveworkplace.compat.cobblemon.CobblemonDaycareKeeper;
import io.github.jcondedata.aliveworkplace.compat.cobblemon.CobblemonProfessor;
import io.github.jcondedata.aliveworkplace.daycare.DaycareKeepers;
import io.github.jcondedata.aliveworkplace.legend.PokemonProfessor;
import io.github.jcondedata.aliveworkplace.ranch.Daycare;
import io.github.jcondedata.aliveworkplace.registry.ModBlocks;
import io.github.jcondedata.aliveworkplace.registry.ModVillagers;
import io.github.jcondedata.aliveworkplace.work.ChoiceList;
import io.github.jcondedata.aliveworkplace.work.ChoiceMenu;
import io.github.jcondedata.aliveworkplace.work.ChoiceView;
import io.github.jcondedata.aliveworkplace.work.Jobs;
import io.github.jcondedata.aliveworkplace.work.Money;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.network.protocol.game.ServerboundContainerClickPacket;
import net.minecraft.network.protocol.game.ServerboundInteractPacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;

/**
 * The Cobblemon menus on the list screen (ROADMAP 42.2), through the player's own clicks: a right-click with an empty
 * hand opens the Pokémon Professor's hints, the Rancher's Daycare and the Daycare Keeper's on the list menu type; every
 * row, price, button and refusal of the chest is there, and each click is the chest click it was.
 */
public class CobblemonListMenusCompatTests implements FabricGameTest {
	private static final String AREA = CompatGameTests.AREA;
	private static final String HUGE = "aliveworkplace_compat:huge_area";

	/** A right-click with an empty hand on {@code villager}, as a client sends it: the screen it opens. */
	private static ChoiceMenu open(GameTestHelper helper, ServerPlayer player, Villager villager) {
		player.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
		player.moveTo(villager.getX() + 1.5, villager.getY(), villager.getZ(), 90f, 0f);
		player.connection.handleInteract(ServerboundInteractPacket.createInteractionPacket(villager, false, InteractionHand.MAIN_HAND,
			new Vec3(0, villager.getBbHeight() / 2, 0)));
		if (player.containerMenu == player.inventoryMenu) {
			player.connection.handleInteract(ServerboundInteractPacket.createInteractionPacket(villager, false, InteractionHand.MAIN_HAND));
		}
		helper.assertTrue(player.containerMenu instanceof ChoiceMenu, "a right-click didn't open the screen: " + player.containerMenu);
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

	private static boolean colored(Component c, ChatFormatting color) {
		return c != null && c.getStyle().getColor() != null && c.getStyle().getColor().getValue() == color.getColor();
	}

	private static void checkInfo(GameTestHelper helper, ChoiceMenu menu, ServerPlayer player, String hint) {
		ChoiceList.Info info = info(helper, menu);
		helper.assertTrue(info.money() != null && info.money().getString().equals(Money.balance(player).getString()), "the money at the top: " + info.money());
		helper.assertTrue(info.columns().size() == 3 && key(info.hint()).equals(hint), "headings and hint: " + info);
	}

	private static Pokemon make(String spec) {
		return PokemonProperties.Companion.parse(spec, " ", "=").create();
	}

	private static boolean inParty(PlayerPartyStore party, Pokemon pokemon) {
		for (Pokemon p : party) {
			if (p.getUuid().equals(pokemon.getUuid())) {
				return true;
			}
		}
		return false;
	}

	// ---- the Pokémon Professor ----

	/**
	 * The Professor's hints as a list: the money and the Pokédex; each party member a row with its nature and Note it
	 * (the hints go to chat, the screen stays); today's stone a row with its price and Buy: red and refused with too
	 * little money, green once money comes in while the screen is open, bought with a click, then a gray "Sold today".
	 */
	//$ gametest_ticks_batch HUGE '200' '"professorList"'
	@GameTest(template = HUGE, timeoutTicks = 200, batch = "professorList")
	public void professorListHintsAndStone(GameTestHelper helper) {
		ProfessorCompatTests.setUp(helper);
		helper.runAfterDelay(5, () -> {
			Villager prof = ProfessorCompatTests.professor(helper, new BlockPos(13, 2, 13));
			List<String> seen = new ArrayList<>();
			ServerPlayer player = TutorListMenuCompatTests.player(helper, seen);
			CobbleDollarsBank.add(player, 500 - CobbleDollarsBank.balance(player));
			PlayerPartyStore party = Cobblemon.INSTANCE.getStorage().getParty(player);
			Pokemon eevee = make("eevee level=12 nature=timid");
			party.add(eevee);
			ProfessorCompatTests.research(helper, Map.of(PokemonProfessor.EVOLUTION_STUDIES, 1));
			ChoiceMenu menu = open(helper, player, prof);
			checkInfo(helper, menu, player, "screen.aliveworkplace.professor.list_hint");
			helper.assertFalse(menu.icon(CobblemonProfessor.POKEDEX).isEmpty(), "no village Pokédex row");

			ChoiceList.Mark mon = row(helper, menu, CobblemonProfessor.FIRST_PARTY_SLOT);
			Component nature = CobblemonProfessor.hints(eevee, PokemonProfessor.exact(prof)).get(0);
			helper.assertTrue(mon.second().getString().equals(nature.getString()) && key(mon.button()).equals("screen.aliveworkplace.professor.button_note"),
				"the Pokémon's row: " + mon);
			click(player, menu, CobblemonProfessor.FIRST_PARTY_SLOT);
			helper.assertTrue(seen.contains(nature.getString()), "Note it didn't put the hints in chat: " + seen);
			helper.assertTrue(player.containerMenu == menu, "the screen closed after Note it");

			var stone = PokemonProfessor.stoneOfDay(helper.getLevel());
			helper.assertTrue(stone != null && menu.icon(CobblemonProfessor.STONE).is(stone), "no stone row: " + menu.icon(CobblemonProfessor.STONE));
			ChoiceList.Mark mark = row(helper, menu, CobblemonProfessor.STONE);
			helper.assertTrue(!mark.off() && key(mark.button()).equals("screen.aliveworkplace.professor.button_buy") && colored(mark.price(), ChatFormatting.RED),
				"the stone's row with too little money: " + mark);
			click(player, menu, CobblemonProfessor.STONE);
			helper.assertTrue(player.getInventory().countItem(stone) == 0 && CobbleDollarsBank.balance(player) == 500, "sold without the money");
			String poor = Component.translatable("message.aliveworkplace.professor.stone_cant_afford",
				Money.describe((long) PokemonProfessor.STONE_PRICE * Money.DOLLARS_PER_EMERALD, PokemonProfessor.STONE_PRICE)).getString();
			helper.assertTrue(seen.contains(poor), "the player wasn't told the price: " + seen);
			CobbleDollarsBank.add(player, 2000);
			helper.runAfterDelay(3, () -> {
				helper.assertTrue(info(helper, menu).money().getString().equals(Money.balance(player).getString()), "the money at the top didn't follow");
				helper.assertTrue(colored(row(helper, menu, CobblemonProfessor.STONE).price(), ChatFormatting.GREEN), "the price didn't turn green");
				click(player, menu, CobblemonProfessor.STONE);
				helper.assertTrue(player.getInventory().countItem(stone) == 1, "no stone bought");
				helper.assertValueEqual(CobbleDollarsBank.balance(player), 2500L - PokemonProfessor.STONE_PRICE * Money.DOLLARS_PER_EMERALD, "the balance");
				ChoiceList.Mark sold = row(helper, menu, CobblemonProfessor.STONE);
				helper.assertTrue(sold.off() && sold.button() == null && key(sold.price()).equals("screen.aliveworkplace.professor.stone_sold_short"),
					"the sold stone's row: " + sold);
				click(player, menu, CobblemonProfessor.STONE);
				helper.assertTrue(player.getInventory().countItem(stone) == 1, "a second stone today");
				helper.succeed();
			});
		});
	}

	/** No Pokémon with you: one gray row says so, and the party joining while the screen is open brings its rows. */
	//$ gametest_ticks_batch HUGE '200' '"professorListEmpty"'
	@GameTest(template = HUGE, timeoutTicks = 200, batch = "professorListEmpty")
	public void professorListWithoutAParty(GameTestHelper helper) {
		ProfessorCompatTests.setUp(helper);
		helper.runAfterDelay(5, () -> {
			Villager prof = ProfessorCompatTests.professor(helper, new BlockPos(13, 2, 13));
			ServerPlayer player = TutorListMenuCompatTests.player(helper, new ArrayList<>());
			ChoiceMenu menu = open(helper, player, prof);
			ChoiceList.Mark none = row(helper, menu, CobblemonProfessor.FIRST_PARTY_SLOT);
			helper.assertTrue(none.off() && none.button() == null && menu.icon(CobblemonProfessor.FIRST_PARTY_SLOT).getHoverName().getString()
				.equals(Component.translatable("screen.aliveworkplace.professor.no_party").getString()), "the no-party row: " + none);
			helper.assertTrue(menu.icon(CobblemonProfessor.STONE).isEmpty(), "a stone without Evolution Studies");
			Cobblemon.INSTANCE.getStorage().getParty(player).add(make("pikachu level=5"));
			helper.runAfterDelay(3, () -> {
				ChoiceList.Mark mon = row(helper, menu, CobblemonProfessor.FIRST_PARTY_SLOT);
				helper.assertTrue(!mon.off() && mon.button() != null, "the party didn't show while the screen was open: " + mon);
				helper.succeed();
			});
		});
	}

	// ---- the Rancher's Daycare ----

	private static Villager rancher(GameTestHelper helper) {
		PartnerShowsCompatTests.clearLeftovers(helper);
		helper.setDayTime(2000);
		BlockPos trough = new BlockPos(2, 2, 2);
		helper.setBlock(trough, ModBlocks.FEED_TROUGH);
		Villager rancher = helper.spawn(EntityType.VILLAGER, new BlockPos(3, 2, 3));
		Jobs.employ(helper.getLevel(), rancher, helper.absolutePos(trough), ModVillagers.FEED_TROUGH_POI, ModVillagers.RANCHER);
		rancher.setNoAi(true);
		helper.assertTrue(Daycare.keepsDaycare(rancher), "not a rancher with a daycare");
		return rancher;
	}

	/**
	 * Leave, then Confirm leaves a Pokémon (the last one with you a gray row that can't be left); it comes back as a
	 * row with its levels, price and Collect: red and refused without the money, collected and paid once there is.
	 */
	//$ gametest_ticks_batch AREA '200' '"daycareList"'
	@GameTest(template = AREA, timeoutTicks = 200, batch = "daycareList")
	public void daycareListLeaveAndCollect(GameTestHelper helper) {
		Villager rancher = rancher(helper);
		List<String> seen = new ArrayList<>();
		ServerPlayer player = TutorListMenuCompatTests.player(helper, seen);
		CobbleDollarsBank.add(player, -CobbleDollarsBank.balance(player));
		PlayerPartyStore party = Cobblemon.INSTANCE.getStorage().getParty(player);
		Pokemon keep = make("bulbasaur level=10");
		Pokemon kid = make("charmander level=5");
		party.add(keep);
		party.add(kid);
		ChoiceMenu menu = open(helper, player, rancher);
		checkInfo(helper, menu, player, "screen.aliveworkplace.daycare.list_hint");
		int kidSlot = CobblemonDaycare.FIRST_PARTY_SLOT + 1;
		ChoiceList.Mark mark = row(helper, menu, kidSlot);
		helper.assertTrue(!mark.off() && !mark.on() && key(mark.button()).equals("screen.aliveworkplace.daycare.button_leave"), "the party row: " + mark);
		click(player, menu, kidSlot);
		mark = row(helper, menu, kidSlot);
		helper.assertTrue(mark.on() && key(mark.button()).equals("message.aliveworkplace.tutor.button_confirm") && inParty(party, kid),
			"after Leave the row isn't waiting for Confirm: " + mark);
		click(player, menu, kidSlot);
		helper.assertFalse(inParty(party, kid), "Confirm didn't leave the Pokémon");
		helper.assertValueEqual(Daycare.boarders(rancher).size(), 1, "boarders");

		ChoiceList.Mark last = row(helper, menu, CobblemonDaycare.FIRST_PARTY_SLOT);
		helper.assertTrue(last.off() && last.button() == null && key(last.second()).equals("screen.aliveworkplace.daycare.last"), "the last Pokémon's row: " + last);
		click(player, menu, CobblemonDaycare.FIRST_PARTY_SLOT);
		click(player, menu, CobblemonDaycare.FIRST_PARTY_SLOT);
		helper.assertTrue(inParty(party, keep), "the last Pokémon was left");

		ChoiceList.Mark boarder = row(helper, menu, CobblemonDaycare.FIRST_BOARDER_SLOT);
		helper.assertTrue(key(boarder.button()).equals("screen.aliveworkplace.daycare.button_collect") && key(boarder.second()).equals("screen.aliveworkplace.daycare.levels")
			&& colored(boarder.price(), ChatFormatting.RED), "the boarder's row without money: " + boarder);
		click(player, menu, CobblemonDaycare.FIRST_BOARDER_SLOT);
		helper.assertFalse(inParty(party, kid), "collected without paying");
		helper.assertTrue(seen.stream().anyMatch(l -> l.equals(Component.translatable("message.aliveworkplace.daycare.cant_afford",
			Money.describe(Money.DOLLARS_PER_EMERALD, 1)).getString())), "the player wasn't told the price: " + seen);
		CobbleDollarsBank.add(player, 1000);
		helper.runAfterDelay(3, () -> {
			helper.assertTrue(info(helper, menu).money().getString().equals(Money.balance(player).getString()), "the money at the top didn't follow");
			ChoiceList.Mark now = row(helper, menu, CobblemonDaycare.FIRST_BOARDER_SLOT);
			helper.assertTrue(colored(now.price(), ChatFormatting.GREEN), "the price didn't turn green: " + now);
			long before = CobbleDollarsBank.balance(player);
			click(player, menu, CobblemonDaycare.FIRST_BOARDER_SLOT);
			helper.assertTrue(inParty(party, kid), "Collect didn't bring the Pokémon back");
			helper.assertTrue(CobbleDollarsBank.balance(player) < before, "collected for free");
			helper.assertTrue(Daycare.boarders(rancher).isEmpty() && menu.icon(CobblemonDaycare.FIRST_BOARDER_SLOT).isEmpty(), "the boarder's row stayed");
			helper.assertTrue(player.containerMenu == menu, "the screen closed");
			helper.succeed();
		});
	}

	// ---- the Daycare Keeper ----

	/**
	 * Pick, then Pair leaves two Pokémon (Unpick undoes a pick; the second row says how they'd get along); the pair, how
	 * well they get along, the eggs and Take back are rows; the party is gray while a pair is here; eggs are refused
	 * without the money, collected with it, and Take back (gray while eggs wait) brings both home.
	 */
	//$ gametest_ticks_batch AREA '200' '"keeperList"'
	@GameTest(template = AREA, timeoutTicks = 200, batch = "keeperList")
	public void daycareKeeperListPairEggsAndTakeBack(GameTestHelper helper) {
		PartnerShowsCompatTests.clearLeftovers(helper);
		Villager keeper = DaycareKeeperCompatTests.keeper(helper);
		keeper.setNoAi(true);
		List<String> seen = new ArrayList<>();
		ServerPlayer player = TutorListMenuCompatTests.player(helper, seen);
		CobbleDollarsBank.add(player, -CobbleDollarsBank.balance(player));
		PlayerPartyStore party = Cobblemon.INSTANCE.getStorage().getParty(player);
		party.add(make("bulbasaur level=10"));
		Pokemon mother = make("eevee gender=female level=20");
		Pokemon father = make("eevee gender=male level=20");
		party.add(mother);
		party.add(father);
		ChoiceMenu menu = open(helper, player, keeper);
		checkInfo(helper, menu, player, "screen.aliveworkplace.daycare_keeper.list_hint");
		int m = CobblemonDaycareKeeper.FIRST_PARTY_SLOT + 1, f = CobblemonDaycareKeeper.FIRST_PARTY_SLOT + 2;
		helper.assertTrue(key(row(helper, menu, m).button()).equals("screen.aliveworkplace.daycare_keeper.button_pick"), "the party row: " + row(helper, menu, m));
		click(player, menu, m);
		helper.assertTrue(row(helper, menu, m).on() && key(row(helper, menu, m).button()).equals("screen.aliveworkplace.daycare_keeper.button_unpick"),
			"the picked row: " + row(helper, menu, m));
		ChoiceList.Mark partner = row(helper, menu, f);
		helper.assertTrue(key(partner.button()).equals("screen.aliveworkplace.daycare_keeper.button_pair")
			&& partner.second().getString().equals(Component.translatable("screen.aliveworkplace.daycare_keeper.with_short",
				Component.translatable("screen.aliveworkplace.daycare_keeper.get_along.well")).getString()), "the partner's row: " + partner);
		click(player, menu, m); // Unpick
		helper.assertTrue(!row(helper, menu, m).on() && key(row(helper, menu, f).button()).equals("screen.aliveworkplace.daycare_keeper.button_pick"), "Unpick didn't undo the pick");
		click(player, menu, m);
		click(player, menu, f);
		helper.assertValueEqual(DaycareKeepers.pairs(keeper).size(), 1, "pairs after Pick, Pair");
		helper.assertFalse(inParty(party, mother) || inParty(party, father), "the pair is still in the party");

		helper.assertTrue(key(row(helper, menu, CobblemonDaycareKeeper.FIRST).second()).equals("screen.aliveworkplace.daycare_keeper.in_care"), "the pair's rows");
		helper.assertTrue(menu.icon(CobblemonDaycareKeeper.GET_ALONG).getHoverName().getString().equals("well")
			&& key(row(helper, menu, CobblemonDaycareKeeper.GET_ALONG).second()).equals("screen.aliveworkplace.daycare_keeper.chance"), "the get-along row");
		ChoiceList.Mark eggs = row(helper, menu, CobblemonDaycareKeeper.EGGS);
		helper.assertTrue(eggs.off() && eggs.button() == null, "the eggs row with no eggs: " + eggs);
		helper.assertTrue(key(row(helper, menu, CobblemonDaycareKeeper.TAKE_BACK).button()).equals("screen.aliveworkplace.daycare_keeper.button_take_back"), "Take back");
		ChoiceList.Mark busy = row(helper, menu, CobblemonDaycareKeeper.FIRST_PARTY_SLOT);
		helper.assertTrue(busy.off() && key(busy.second()).equals("screen.aliveworkplace.daycare_keeper.have_pair"), "the party with a pair here: " + busy);

		// An egg waits: reopened, Collect is red without the money and Take back is gray.
		List<DaycareKeepers.Pair> pairs = new ArrayList<>();
		for (DaycareKeepers.Pair pair : DaycareKeepers.pairs(keeper)) {
			pairs.add(pair.withEggs(1, pair.day()));
		}
		DaycareKeepers.setPairs(keeper, pairs);
		player.closeContainer();
		ChoiceMenu again = open(helper, player, keeper);
		eggs = row(helper, again, CobblemonDaycareKeeper.EGGS);
		helper.assertTrue(!eggs.off() && key(eggs.button()).equals("screen.aliveworkplace.daycare.button_collect") && colored(eggs.price(), ChatFormatting.RED),
			"the eggs row without money: " + eggs);
		ChoiceList.Mark back = row(helper, again, CobblemonDaycareKeeper.TAKE_BACK);
		helper.assertTrue(back.off() && back.button() == null && key(back.second()).equals("screen.aliveworkplace.daycare_keeper.collect_first"), "Take back with eggs: " + back);
		click(player, again, CobblemonDaycareKeeper.TAKE_BACK);
		helper.assertValueEqual(DaycareKeepers.pairs(keeper).size(), 1, "taken back with an egg waiting");
		click(player, again, CobblemonDaycareKeeper.EGGS);
		helper.assertValueEqual(DaycareKeepers.pairs(keeper).get(0).eggs(), 1, "eggs without the money");
		String poor = Component.translatable("message.aliveworkplace.daycare_keeper.cant_afford",
			Money.describe((long) DaycareKeepers.EGG_PRICE * Money.DOLLARS_PER_EMERALD, DaycareKeepers.EGG_PRICE)).getString();
		helper.assertTrue(seen.contains(poor), "the player wasn't told the price: " + seen);
		CobbleDollarsBank.add(player, 10_000);
		helper.runAfterDelay(3, () -> {
			helper.assertTrue(colored(row(helper, again, CobblemonDaycareKeeper.EGGS).price(), ChatFormatting.GREEN), "the egg price didn't turn green");
			click(player, again, CobblemonDaycareKeeper.EGGS);
			helper.assertValueEqual(DaycareKeepers.pairs(keeper).get(0).eggs(), 0, "eggs after Collect");
			helper.assertValueEqual(CobbleDollarsBank.balance(player), 10_000L - DaycareKeepers.EGG_PRICE * Money.DOLLARS_PER_EMERALD, "the balance");
			click(player, again, CobblemonDaycareKeeper.TAKE_BACK);
			helper.assertTrue(DaycareKeepers.pairs(keeper).isEmpty(), "Take back left the pair");
			int eevees = 0;
			for (Pokemon p : party) {
				eevees += p.getSpecies().getName().equalsIgnoreCase("eevee") && p.getLevel() == 20 ? 1 : 0;
			}
			helper.assertValueEqual(eevees, 2, "the pair back in the party");
			helper.succeed();
		});
	}
}
