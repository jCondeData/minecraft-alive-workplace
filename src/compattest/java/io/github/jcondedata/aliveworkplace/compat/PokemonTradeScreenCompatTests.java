package io.github.jcondedata.aliveworkplace.compat;

import com.cobblemon.mod.common.Cobblemon;
import com.cobblemon.mod.common.api.pokemon.PokemonSpecies;
import com.cobblemon.mod.common.api.storage.party.PlayerPartyStore;
import com.cobblemon.mod.common.pokemon.Pokemon;
import com.cobblemon.mod.common.pokemon.Species;
import io.github.jcondedata.aliveworkplace.compat.cobblemon.CobblemonTraders;
import io.github.jcondedata.aliveworkplace.registry.ModAttachments;
import io.github.jcondedata.aliveworkplace.registry.ModBlocks;
import io.github.jcondedata.aliveworkplace.registry.ModVillagers;
import io.github.jcondedata.aliveworkplace.trader.PokemonTradeView;
import io.github.jcondedata.aliveworkplace.trader.PokemonTraders;
import io.github.jcondedata.aliveworkplace.work.ChoiceMenu;
import io.github.jcondedata.aliveworkplace.work.Jobs;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.function.Predicate;
import net.fabricmc.fabric.api.event.player.UseEntityCallback;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.Vec3;

/**
 * The Pokémon Trader's own screen (ROADMAP 28.23): a right-click with an empty hand opens it (not a chest), its cards
 * show each offer's ball and cost, a trade goes through with two clicks on a Pokémon that fits, one the player can't pay
 * for is refused, and the day's trade is still remembered after the trader is saved and loaded.
 */
public class PokemonTradeScreenCompatTests implements FabricGameTest {
	private static final String AREA = CompatGameTests.AREA;

	private record Setup(Villager trader, ServerPlayer player, PlayerPartyStore party, CobblemonTraders.Offer offer) {
	}

	private static Setup setup(GameTestHelper helper) {
		helper.setDayTime(2000);
		BlockPos board = new BlockPos(2, 2, 2);
		helper.setBlock(board, ModBlocks.TRADE_BOARD);
		Villager trader = helper.spawn(EntityType.VILLAGER, new BlockPos(3, 2, 3));
		Jobs.employ(helper.getLevel(), trader, helper.absolutePos(board), ModVillagers.TRADE_BOARD_POI, ModVillagers.POKEMON_TRADER);
		trader.setNoAi(true);
		ServerPlayer player = helper.makeMockServerPlayerInLevel();
		player.setGameMode(GameType.SURVIVAL);
		Vec3 at = Vec3.atBottomCenterOf(helper.absolutePos(new BlockPos(4, 2, 4)));
		player.moveTo(at.x, at.y, at.z);
		PlayerPartyStore party = Cobblemon.INSTANCE.getStorage().getParty(player);
		return new Setup(trader, player, party, CobblemonTraders.offers(trader).get(0));
	}

	private static Predicate<Species> fits(CobblemonTraders.Offer offer) {
		return s -> {
			for (var type : s.getTypes()) {
				if (type.getName().equals(offer.wanted().getName())) {
					return true;
				}
			}
			return false;
		};
	}

	private static Species first(Predicate<Species> filter) {
		List<Species> all = new ArrayList<>(PokemonSpecies.getImplemented());
		all.sort(Comparator.comparing(Species::getName));
		return all.stream().filter(filter).findFirst().orElseThrow();
	}

	private static boolean contains(PlayerPartyStore party, Pokemon pokemon) {
		for (Pokemon p : party) {
			if (p == pokemon) {
				return true;
			}
		}
		return false;
	}

	private static String key(ChoiceMenu menu, int slot) {
		Component name = menu.icon(slot).get(DataComponents.CUSTOM_NAME);
		return name != null && name.getContents() instanceof TranslatableContents t ? t.getKey() : "";
	}

	/** Clicks a button the way the client's screen does (a plain left click on the slot). */
	private static void click(ChoiceMenu menu, int slot, ServerPlayer player) {
		menu.clicked(slot, 0, ClickType.PICKUP, player);
	}

	/** A right-click opens the trader's own screen, and two clicks on a Pokémon that fits make the trade. */
	//$ gametest AREA
	@GameTest(template = AREA)
	public void tradeScreenOpensAndTrades(GameTestHelper helper) {
		Setup s = setup(helper);
		Pokemon mine = first(fits(s.offer())).create(s.offer().minLevel() + 5);
		s.party().add(mine);

		InteractionResult result = UseEntityCallback.EVENT.invoker().interact(s.player(), helper.getLevel(), InteractionHand.MAIN_HAND, s.trader(), null);
		helper.assertTrue(result.consumesAction(), "the right-click wasn't taken: " + result);
		helper.assertTrue(s.player().containerMenu instanceof ChoiceMenu, "no trade screen: " + s.player().containerMenu);
		ChoiceMenu menu = (ChoiceMenu) s.player().containerMenu;
		helper.assertTrue(menu.getType() == ModBlocks.POKEMON_TRADER_MENU, "opened on a chest, not the trader's screen: " + menu.getType());
		helper.assertTrue(!menu.icon(PokemonTradeView.OFFERS).isEmpty(), "no offer card");
		helper.assertTrue(!menu.icon(PokemonTradeView.BALLS).isEmpty(), "the card shows no ball");
		helper.assertValueEqual(key(menu, PokemonTradeView.COSTS), "screen.aliveworkplace.pokemon_trader.cost", "the card's cost");
		helper.assertValueEqual(key(menu, PokemonTradeView.STATUS), "screen.aliveworkplace.pokemon_trader.pick", "the status before a pick");

		click(menu, PokemonTradeView.PARTY, s.player());
		helper.assertTrue(contains(s.party(), mine), "the first click already traded");
		helper.assertValueEqual(key(menu, PokemonTradeView.STATUS), "message.aliveworkplace.pokemon_trader.confirm", "the status after one click");
		click(menu, PokemonTradeView.PARTY, s.player());
		helper.assertFalse(contains(s.party(), mine), "the fitting Pokémon is still in the party");
		boolean got = false;
		for (Pokemon p : s.party()) {
			got |= p.getSpecies() == s.offer().species() && p.getLevel() == s.offer().level();
		}
		helper.assertTrue(got, "the trader's " + s.offer().species().getName() + " didn't arrive");
		helper.assertTrue(ModAttachments.POKEMON_TRADE_COUNT.getOrElse(s.trader(), 0) == 1, "trade not counted");
		helper.assertValueEqual(key(menu, PokemonTradeView.STATUS), "message.aliveworkplace.pokemon_trader.come_back", "the status after the trade");
		s.player().closeContainer();
		helper.succeed();
	}

	/** Pokémon the trader won't take (wrong type, too low a level) are greyed out and a click on them does nothing. */
	//$ gametest AREA
	@GameTest(template = AREA)
	public void tradeScreenRefusesWhatCantPay(GameTestHelper helper) {
		Setup s = setup(helper);
		Pokemon wrong = first(fits(s.offer()).negate()).create(s.offer().minLevel() + 5);
		Pokemon weak = first(fits(s.offer())).create(Math.max(1, s.offer().minLevel() - 3));
		s.party().add(wrong);
		s.party().add(weak);

		UseEntityCallback.EVENT.invoker().interact(s.player(), helper.getLevel(), InteractionHand.MAIN_HAND, s.trader(), null);
		helper.assertTrue(s.player().containerMenu instanceof ChoiceMenu m && m.getType() == ModBlocks.POKEMON_TRADER_MENU,
			"no trade screen: " + s.player().containerMenu);
		ChoiceMenu menu = (ChoiceMenu) s.player().containerMenu;
		for (int slot : new int[]{PokemonTradeView.PARTY, PokemonTradeView.PARTY + 1}) {
			click(menu, slot, s.player());
			click(menu, slot, s.player());
			helper.assertValueEqual(key(menu, PokemonTradeView.STATUS), "screen.aliveworkplace.pokemon_trader.pick", "the status after clicking slot " + slot);
		}
		helper.assertTrue(contains(s.party(), wrong) && contains(s.party(), weak), "a Pokémon the trader won't take was traded away");
		helper.assertTrue(ModAttachments.POKEMON_TRADE_COUNT.getOrElse(s.trader(), 0) == 0, "a refused trade was counted");
		helper.assertFalse(PokemonTraders.tradedToday(s.trader(), s.player().getUUID()), "a refused trade used up the day's trade");
		s.player().closeContainer();
		helper.succeed();
	}

	/** After a trade, a trader saved and loaded still remembers it: the screen says come back tomorrow and refuses another. */
	//$ gametest AREA
	@GameTest(template = AREA)
	public void tradeScreenSurvivesReload(GameTestHelper helper) {
		Setup s = setup(helper);
		Pokemon mine = first(fits(s.offer())).create(s.offer().minLevel() + 5);
		s.party().add(mine);
		ChoiceMenu menu = CobblemonTraders.menuForTest(s.player(), s.trader());
		click(menu, PokemonTradeView.PARTY, s.player());
		click(menu, PokemonTradeView.PARTY, s.player());
		helper.assertFalse(contains(s.party(), mine), "the trade didn't go through");

		CompoundTag saved = s.trader().saveWithoutId(new CompoundTag());
		Villager copy = EntityType.VILLAGER.create(helper.getLevel());
		copy.load(saved);
		helper.assertTrue(PokemonTraders.tradedToday(copy, s.player().getUUID()), "the day's trade was forgotten after a reload");
		helper.assertTrue(CobblemonTraders.offers(copy).equals(CobblemonTraders.offers(s.trader())), "the offers changed after a reload");
		Pokemon another = first(fits(s.offer())).create(s.offer().minLevel() + 5);
		s.party().add(another);
		ChoiceMenu again = CobblemonTraders.menuForTest(s.player(), copy);
		helper.assertValueEqual(key(again, PokemonTradeView.STATUS), "message.aliveworkplace.pokemon_trader.come_back", "the status after a reload");
		int slot = -1;
		int i = 0;
		for (Pokemon p : s.party()) {
			if (p == another) {
				slot = PokemonTradeView.PARTY + i;
			}
			i++;
		}
		helper.assertTrue(slot >= 0, "the new Pokémon isn't in the party");
		click(again, slot, s.player());
		click(again, slot, s.player());
		helper.assertTrue(contains(s.party(), another), "a second trade went through after a reload");
		copy.discard();
		helper.succeed();
	}
}
