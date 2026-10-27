package io.github.jcondedata.aliveworkplace.compat;

import com.cobblemon.mod.common.Cobblemon;
import com.cobblemon.mod.common.api.pokemon.PokemonSpecies;
import com.cobblemon.mod.common.api.storage.party.PlayerPartyStore;
import com.cobblemon.mod.common.pokemon.Pokemon;
import com.cobblemon.mod.common.pokemon.Species;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import io.github.jcondedata.aliveworkplace.compat.cobblemon.CobblemonTraders;
import io.github.jcondedata.aliveworkplace.registry.ModAttachments;
import io.github.jcondedata.aliveworkplace.registry.ModBlocks;
import io.github.jcondedata.aliveworkplace.registry.ModVillagers;
import io.github.jcondedata.aliveworkplace.trader.PokemonTradeView;
import io.github.jcondedata.aliveworkplace.trader.PokemonTraders;
import io.github.jcondedata.aliveworkplace.work.ChoiceMenu;
import io.github.jcondedata.aliveworkplace.work.Jobs;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import java.util.function.Predicate;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import net.fabricmc.fabric.api.event.player.UseEntityCallback;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.network.protocol.game.ServerboundContainerClickPacket;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.ItemLore;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.Vec3;

/**
 * QA for the Pokémon Trader's own screen (ROADMAP 28.23), written from the spec: every click goes through the real
 * packet handler, as the client sends it. Covers the cards matching the real offers (name, level, ball, shiny mark, cost),
 * the level boundary from both sides, a Pokémon that leaves the party between the two clicks, click spam and the used-up
 * state, an offer that expired overnight, walking away and a dead trader while the screen is open, and every sentence the
 * screen shows going through en_us.json with the right number of arguments.
 */
public class QaPokemonTradeScreenCompatTests implements FabricGameTest {
	private static final String AREA = CompatGameTests.AREA;

	private record Setup(Villager trader, ServerPlayer player, PlayerPartyStore party, CobblemonTraders.Offer offer) {
	}

	/** A trader at a Trade Board (the given tier; a fixed UUID when {@code id} isn't null) and a survival player next to him. */
	private static Setup setup(GameTestHelper helper, int tier, UUID id) {
		helper.setDayTime(2000);
		BlockPos board = new BlockPos(2, 2, 2);
		helper.setBlock(board, ModBlocks.TRADE_BOARD);
		Villager trader;
		if (id == null) {
			trader = helper.spawn(EntityType.VILLAGER, new BlockPos(3, 2, 3));
		} else {
			trader = EntityType.VILLAGER.create(helper.getLevel());
			trader.setUUID(id);
			Vec3 at = Vec3.atBottomCenterOf(helper.absolutePos(new BlockPos(3, 2, 3)));
			trader.moveTo(at.x, at.y, at.z, 0, 0);
			helper.getLevel().addFreshEntity(trader);
		}
		Jobs.employ(helper.getLevel(), trader, helper.absolutePos(board), ModVillagers.TRADE_BOARD_POI, ModVillagers.POKEMON_TRADER);
		trader.setVillagerData(trader.getVillagerData().setLevel(tier));
		trader.setNoAi(true);
		ServerPlayer player = helper.makeMockServerPlayerInLevel();
		player.setGameMode(GameType.SURVIVAL);
		Vec3 at = Vec3.atBottomCenterOf(helper.absolutePos(new BlockPos(4, 2, 4)));
		player.moveTo(at.x, at.y, at.z);
		PlayerPartyStore party = Cobblemon.INSTANCE.getStorage().getParty(player);
		return new Setup(trader, player, party, CobblemonTraders.offers(trader).get(0));
	}

	private static Setup setup(GameTestHelper helper) {
		return setup(helper, 1, null);
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

	private static Species first(Predicate<Species> filter, int skip) {
		List<Species> all = new ArrayList<>(PokemonSpecies.getImplemented());
		all.sort(Comparator.comparing(Species::getName));
		return all.stream().filter(filter).skip(skip).findFirst().orElseThrow();
	}

	private static boolean contains(PlayerPartyStore party, Pokemon pokemon) {
		for (Pokemon p : party) {
			if (p == pokemon) {
				return true;
			}
		}
		return false;
	}

	private static int slotOf(PlayerPartyStore party, Pokemon pokemon) {
		int i = 0;
		for (Pokemon p : party) {
			if (p == pokemon) {
				return PokemonTradeView.PARTY + i;
			}
			i++;
		}
		return -1;
	}

	private static boolean gotOffer(PlayerPartyStore party, CobblemonTraders.Offer offer) {
		for (Pokemon p : party) {
			if (p.getSpecies() == offer.species() && p.getLevel() == offer.level()) {
				return true;
			}
		}
		return false;
	}

	/** Right-clicks the trader with an empty hand and returns the screen it opened. */
	private static ChoiceMenu open(GameTestHelper helper, Setup s) {
		UseEntityCallback.EVENT.invoker().interact(s.player(), helper.getLevel(), InteractionHand.MAIN_HAND, s.trader(), null);
		helper.assertTrue(s.player().containerMenu instanceof ChoiceMenu m && m.getType() == ModBlocks.POKEMON_TRADER_MENU,
			"the right-click didn't open the trader's screen: " + s.player().containerMenu);
		return (ChoiceMenu) s.player().containerMenu;
	}

	/** A left click on {@code slot} of screen {@code containerId}, sent the way the client sends it. */
	private static void click(ServerPlayer p, int containerId, int slot) {
		p.connection.handleContainerClick(new ServerboundContainerClickPacket(containerId, p.containerMenu.getStateId(), slot, 0, ClickType.PICKUP,
			ItemStack.EMPTY, new Int2ObjectOpenHashMap<>()));
	}

	private static void click(ServerPlayer p, ChoiceMenu menu, int slot) {
		click(p, menu.containerId, slot);
	}

	private static String key(Component c) {
		return c != null && c.getContents() instanceof TranslatableContents t ? t.getKey() : "";
	}

	private static String nameKey(ChoiceMenu menu, int slot) {
		return key(menu.icon(slot).get(DataComponents.CUSTOM_NAME));
	}

	private static List<String> loreKeys(ChoiceMenu menu, int slot) {
		ItemLore lore = menu.icon(slot).get(DataComponents.LORE);
		List<String> keys = new ArrayList<>();
		if (lore != null) {
			for (Component line : lore.lines()) {
				keys.add(key(line));
			}
		}
		return keys;
	}

	private static int trades(Setup s) {
		return ModAttachments.POKEMON_TRADE_COUNT.getOrElse(s.trader(), 0);
	}

	/**
	 * Each card shows the real offer: the Pokémon's name and level, a ★ only on a shiny, the ball it comes in, and what it
	 * costs (the wanted type or line and the level). A Master trader shows all four of his offers and no fifth card.
	 */
	//$ gametest AREA
	@GameTest(template = AREA)
	public void qaCardsShowTheRealOffers(GameTestHelper helper) {
		// A fixed trader (found deterministically) whose Master offers on day 0 include a shiny and a plain one.
		UUID id = null;
		for (long n = 0; n < 500 && id == null; n++) {
			UUID candidate = new UUID(0x2823_0000_0000_0001L, n);
			List<CobblemonTraders.Offer> offers = CobblemonTraders.offers(candidate, 5, 0);
			if (offers.size() == 4 && offers.stream().anyMatch(CobblemonTraders.Offer::shiny) && offers.stream().anyMatch(o -> !o.shiny())) {
				id = candidate;
			}
		}
		helper.assertTrue(id != null, "no Master trader with a shiny offer in 500 tries");
		Setup s = setup(helper, 5, id);
		List<CobblemonTraders.Offer> offers = CobblemonTraders.offers(s.trader());
		helper.assertValueEqual(offers.size(), 4, "a Master's offers");
		ChoiceMenu menu = open(helper, s);
		for (int i = 0; i < offers.size(); i++) {
			CobblemonTraders.Offer offer = offers.get(i);
			String who = "card " + i + " (" + offer.species().getName() + " Lv. " + offer.level() + (offer.shiny() ? " shiny" : "") + ")";
			ItemStack card = menu.icon(PokemonTradeView.OFFERS + i);
			helper.assertFalse(card.isEmpty(), who + " is missing");
			Component name = card.get(DataComponents.CUSTOM_NAME);
			helper.assertTrue(name != null && name.getContents() instanceof TranslatableContents, who + " has no translated name: " + name);
			TranslatableContents t = (TranslatableContents) name.getContents();
			helper.assertValueEqual(t.getKey(), "message.aliveworkplace.tutor.pokemon", who + "'s name sentence");
			helper.assertValueEqual(key((Component) t.getArgs()[0]), key(offer.species().getTranslatedName()), who + "'s Pokémon name");
			helper.assertValueEqual(String.valueOf(t.getArgs()[1]), String.valueOf(offer.level()), who + "'s level");
			boolean star = name.getSiblings().stream().anyMatch(c -> c.getString().contains("★"));
			helper.assertValueEqual(star, offer.shiny(), who + "'s shiny mark");

			ItemStack ball = menu.icon(PokemonTradeView.BALLS + i);
			helper.assertFalse(ball.isEmpty(), who + " shows no ball");
			ResourceLocation ballId = BuiltInRegistries.ITEM.getKey(ball.getItem());
			helper.assertTrue(ballId.getNamespace().equals("cobblemon") && ballId.getPath().endsWith("_ball"), who + "'s ball is " + ballId);
			Component ballName = ball.get(DataComponents.CUSTOM_NAME);
			helper.assertTrue(key(ballName).startsWith("screen.aliveworkplace.pokemon_trader.ball"), who + "'s ball label: " + key(ballName));
			Object ballArg = ((TranslatableContents) ballName.getContents()).getArgs()[0];
			helper.assertValueEqual(key((Component) ballArg), key(ball.getItem().getName(ball)), who + "'s ball label names another ball");

			Component cost = menu.icon(PokemonTradeView.COSTS + i).get(DataComponents.CUSTOM_NAME);
			helper.assertTrue(cost != null && cost.getContents() instanceof TranslatableContents, who + " has no cost");
			TranslatableContents c = (TranslatableContents) cost.getContents();
			if (offer.wantedSpecies() != null) {
				helper.assertTrue(c.getKey().startsWith("screen.aliveworkplace.pokemon_trader.cost_species"), who + "'s cost: " + c.getKey());
				helper.assertValueEqual(key((Component) c.getArgs()[0]), key(offer.wantedSpecies().getTranslatedName()), who + "'s wanted line");
			} else {
				helper.assertValueEqual(c.getKey(), "screen.aliveworkplace.pokemon_trader.cost", who + "'s cost");
				helper.assertValueEqual(key((Component) c.getArgs()[0]), key(offer.wanted().getDisplayName()), who + "'s wanted type");
			}
			helper.assertValueEqual(String.valueOf(c.getArgs()[1]), String.valueOf(offer.minLevel()), who + "'s wanted level");
		}
		for (int slot = PokemonTradeView.OFFERS + offers.size(); slot < PokemonTradeView.INFO; slot++) {
			helper.assertTrue(menu.icon(slot).isEmpty(), "an extra card in slot " + slot + ": " + menu.icon(slot));
		}
		s.player().closeContainer();
		s.trader().discard();
		helper.succeed();
	}

	/**
	 * The level boundary from both sides: a Pokémon of the right type one level short is refused (greyed out, two clicks
	 * do nothing); one at exactly the wanted level is accepted, and the item it held comes back to the player.
	 */
	//$ gametest AREA
	@GameTest(template = AREA)
	public void qaExactLevelTradesOneShortRefused(GameTestHelper helper) {
		Setup s = setup(helper);
		int min = s.offer().minLevel();
		helper.assertTrue(min > 1, "the offer wants level " + min + ": no level below it to try");
		Pokemon short1 = first(fits(s.offer()), 0).create(min - 1);
		Pokemon exact = first(fits(s.offer()), 1).create(min);
		ItemStack berry = new ItemStack(BuiltInRegistries.ITEM.get(ResourceLocation.fromNamespaceAndPath("cobblemon", "oran_berry")));
		exact.swapHeldItem(berry.copy(), false, false);
		s.party().add(short1);
		s.party().add(exact);
		ChoiceMenu menu = open(helper, s);

		int shortSlot = slotOf(s.party(), short1);
		helper.assertTrue(loreKeys(menu, shortSlot).contains("message.aliveworkplace.pokemon_trader.too_low"),
			"one level short isn't marked too low: " + loreKeys(menu, shortSlot));
		click(s.player(), menu, shortSlot);
		click(s.player(), menu, shortSlot);
		helper.assertTrue(contains(s.party(), short1), "a Pokémon one level short was traded");
		helper.assertValueEqual(trades(s), 0, "trades after clicking one a level short");
		helper.assertValueEqual(nameKey(menu, PokemonTradeView.STATUS), "screen.aliveworkplace.pokemon_trader.pick", "the status after the refused one");

		int exactSlot = slotOf(s.party(), exact);
		helper.assertTrue(loreKeys(menu, exactSlot).contains("message.aliveworkplace.pokemon_trader.click"),
			"a Pokémon at exactly the wanted level isn't offered: " + loreKeys(menu, exactSlot));
		click(s.player(), menu, exactSlot);
		helper.assertTrue(contains(s.party(), exact), "one click already traded");
		click(s.player(), menu, exactSlot);
		helper.assertFalse(contains(s.party(), exact), "a Pokémon at exactly the wanted level was refused");
		helper.assertTrue(gotOffer(s.party(), s.offer()), "the trader's " + s.offer().species().getName() + " didn't arrive");
		helper.assertValueEqual(trades(s), 1, "trades");
		helper.assertTrue(s.player().getInventory().countItem(berry.getItem()) == 1, "the held Oran Berry didn't come back to the player");
		s.player().closeContainer();
		helper.succeed();
	}

	/**
	 * A Pokémon picked with the first click that leaves the party before the second (released, given away) isn't traded,
	 * and the Pokémon that moves into its button isn't traded either on that one click.
	 */
	//$ gametest AREA
	@GameTest(template = AREA)
	public void qaPokemonGoneBeforeSecondClick(GameTestHelper helper) {
		Setup s = setup(helper);
		Pokemon gone = first(fits(s.offer()), 0).create(s.offer().minLevel() + 5);
		Pokemon next = first(fits(s.offer()), 1).create(s.offer().minLevel() + 5);
		s.party().add(gone);
		s.party().add(next);
		ChoiceMenu menu = open(helper, s);
		int slot = slotOf(s.party(), gone);
		click(s.player(), menu, slot);
		helper.assertValueEqual(nameKey(menu, PokemonTradeView.STATUS), "message.aliveworkplace.pokemon_trader.confirm", "the status after one click");
		helper.assertTrue(s.party().remove(gone), "couldn't take the Pokémon out of the party");

		click(s.player(), menu, slot);
		helper.assertTrue(contains(s.party(), next), "the Pokémon that moved into the button was traded on one click");
		helper.assertFalse(gotOffer(s.party(), s.offer()), "the trader's Pokémon arrived for one the player no longer had");
		helper.assertValueEqual(trades(s), 0, "trades");
		helper.assertFalse(PokemonTraders.tradedToday(s.trader(), s.player().getUUID()), "the day's trade was used up by a trade that didn't happen");
		helper.assertTrue(!"message.aliveworkplace.pokemon_trader.come_back".equals(nameKey(menu, PokemonTradeView.STATUS)),
			"the screen says the player traded today");

		// The next one still trades normally, with its own two clicks.
		int nextSlot = slotOf(s.party(), next);
		click(s.player(), menu, nextSlot);
		helper.assertTrue(contains(s.party(), next), "one click traded");
		click(s.player(), menu, nextSlot);
		helper.assertFalse(contains(s.party(), next), "the remaining Pokémon couldn't be traded afterwards");
		helper.assertValueEqual(trades(s), 1, "trades");
		s.player().closeContainer();
		helper.succeed();
	}

	/**
	 * Clicking a fitting Pokémon over and over trades exactly once; afterwards every party button is greyed out with "come
	 * back tomorrow", clicks on them do nothing, and a fresh right-click shows the same used-up screen.
	 */
	//$ gametest AREA
	@GameTest(template = AREA)
	public void qaSpamClicksTradeOnce(GameTestHelper helper) {
		Setup s = setup(helper);
		Pokemon a = first(fits(s.offer()), 0).create(s.offer().minLevel() + 5);
		Pokemon b = first(fits(s.offer()), 1).create(s.offer().minLevel() + 5);
		s.party().add(a);
		s.party().add(b);
		ChoiceMenu menu = open(helper, s);
		int slotA = slotOf(s.party(), a);
		int slotB = slotOf(s.party(), b);
		for (int i = 0; i < 6; i++) {
			click(s.player(), menu, slotA);
		}
		for (int i = 0; i < 6; i++) {
			click(s.player(), menu, slotB);
		}
		helper.assertValueEqual(trades(s), 1, "trades after clicking again and again");
		helper.assertFalse(contains(s.party(), a), "the first Pokémon clicked wasn't traded");
		helper.assertTrue(contains(s.party(), b), "a second Pokémon was traded the same day");
		s.player().closeContainer();

		ChoiceMenu again = open(helper, s);
		helper.assertValueEqual(nameKey(again, PokemonTradeView.STATUS), "message.aliveworkplace.pokemon_trader.come_back", "the reopened screen's status");
		helper.assertTrue(loreKeys(again, PokemonTradeView.INFO).contains("message.aliveworkplace.pokemon_trader.come_back"),
			"the info book doesn't say come back: " + loreKeys(again, PokemonTradeView.INFO));
		int members = 0;
		for (Pokemon ignored : s.party()) {
			members++;
		}
		for (int i = 0; i < members; i++) {
			helper.assertTrue(loreKeys(again, PokemonTradeView.PARTY + i).contains("message.aliveworkplace.pokemon_trader.come_back"),
				"party button " + i + " isn't greyed out with come back: " + loreKeys(again, PokemonTradeView.PARTY + i));
			click(s.player(), again, PokemonTradeView.PARTY + i);
			click(s.player(), again, PokemonTradeView.PARTY + i);
		}
		helper.assertValueEqual(trades(s), 1, "trades after clicking the used-up screen");
		s.player().closeContainer();
		helper.succeed();
	}

	/**
	 * An offer picked yesterday expires overnight: the second click after the day turns doesn't trade for yesterday's
	 * Pokémon, and the cards now show today's offers.
	 */
	//$ gametest_batch AREA '"qaTradeScreenNewDay"'
	@GameTest(template = AREA, batch = "qaTradeScreenNewDay")
	public void qaYesterdaysOfferRefused(GameTestHelper helper) {
		Setup s = setup(helper);
		Pokemon mine = first(fits(s.offer()), 0).create(70);
		s.party().add(mine);
		ChoiceMenu menu = open(helper, s);
		int slot = slotOf(s.party(), mine);
		click(s.player(), menu, slot);
		helper.assertValueEqual(nameKey(menu, PokemonTradeView.STATUS), "message.aliveworkplace.pokemon_trader.confirm", "the status after one click");
		helper.setDayTime(2000 + 24000);
		try {
			List<CobblemonTraders.Offer> today = CobblemonTraders.offers(s.trader());
			helper.assertFalse(today.contains(s.offer()), "the offers didn't change overnight");
			click(s.player(), menu, slot);
			helper.assertTrue(contains(s.party(), mine), "yesterday's offer was traded on the second click after the day turned");
			helper.assertFalse(gotOffer(s.party(), s.offer()), "yesterday's Pokémon arrived");
			helper.assertValueEqual(trades(s), 0, "trades");
			Component name = menu.icon(PokemonTradeView.OFFERS).get(DataComponents.CUSTOM_NAME);
			Object[] args = ((TranslatableContents) name.getContents()).getArgs();
			helper.assertValueEqual(key((Component) args[0]) + " " + args[1], key(today.get(0).species().getTranslatedName()) + " " + today.get(0).level(),
				"the first card after the day turned");
		} finally {
			helper.setDayTime(2000);
		}
		s.player().closeContainer();
		helper.succeed();
	}

	/**
	 * Walking more than 8 blocks away closes the screen and lets the trader go back to work; a click sent for the closed
	 * screen trades nothing.
	 */
	//$ gametest_ticks AREA '100'
	@GameTest(template = AREA, timeoutTicks = 100)
	public void qaWalkingAwayClosesTheScreen(GameTestHelper helper) {
		Setup s = setup(helper);
		Pokemon mine = first(fits(s.offer()), 0).create(s.offer().minLevel() + 5);
		s.party().add(mine);
		ChoiceMenu menu = open(helper, s);
		int id = menu.containerId;
		int slot = slotOf(s.party(), mine);
		click(s.player(), menu, slot);
		helper.assertTrue(s.trader().getTradingPlayer() == s.player(), "the trader isn't trading with the player");
		Vec3 far = s.trader().position().add(PokemonTraders.REACH + 4, 0, 0);
		s.player().moveTo(far.x, far.y, far.z);
		helper.assertFalse(menu.stillValid(s.player()), "the screen still counts as valid " + String.format("%.1f", s.player().distanceTo(s.trader()))
			+ " blocks away");
		helper.runAfterDelay(10, () -> {
			helper.assertFalse(s.player().containerMenu == menu, "the screen stayed open after the player walked away");
			helper.assertTrue(s.trader().getTradingPlayer() == null, "the trader is still held after the screen closed");
			click(s.player(), id, slot);
			helper.assertTrue(contains(s.party(), mine), "a click on the closed screen traded");
			helper.assertValueEqual(trades(s), 0, "trades");
			helper.succeed();
		});
	}

	/** A trader who dies with the screen open: the screen closes and the pending pick can't be confirmed. */
	//$ gametest_ticks AREA '100'
	@GameTest(template = AREA, timeoutTicks = 100)
	public void qaDeadTraderTradesNothing(GameTestHelper helper) {
		Setup s = setup(helper);
		Pokemon mine = first(fits(s.offer()), 0).create(s.offer().minLevel() + 5);
		s.party().add(mine);
		ChoiceMenu menu = open(helper, s);
		int id = menu.containerId;
		int slot = slotOf(s.party(), mine);
		click(s.player(), menu, slot);
		s.trader().kill();
		helper.assertFalse(s.trader().isAlive(), "the trader didn't die");
		click(s.player(), id, slot);
		helper.assertTrue(contains(s.party(), mine), "a dead trader traded");
		helper.assertFalse(gotOffer(s.party(), s.offer()), "a dead trader's Pokémon arrived");
		helper.runAfterDelay(10, () -> {
			helper.assertFalse(s.player().containerMenu == menu, "the screen stayed open after the trader died");
			helper.assertTrue(contains(s.party(), mine), "the Pokémon left the party");
			helper.succeed();
		});
	}

	private static final Pattern ARG = Pattern.compile("%(\\d+\\$)?s");

	private static JsonObject lang() {
		try (var in = QaPokemonTradeScreenCompatTests.class.getResourceAsStream("/assets/aliveworkplace/lang/en_us.json")) {
			return JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonObject();
		} catch (Exception e) {
			throw new IllegalStateException("can't read en_us.json", e);
		}
	}

	/** Every translated piece of {@code c} that's ours: its key is in en_us.json and it gets as many arguments as it asks for. */
	private static void checkText(Component c, JsonObject lang, String where, List<String> problems, List<String> seen) {
		if (c.getContents() instanceof TranslatableContents t) {
			if (t.getKey().contains("aliveworkplace")) {
				seen.add(t.getKey());
				if (!lang.has(t.getKey())) {
					problems.add(where + ": " + t.getKey() + " isn't in en_us.json");
				} else {
					String text = lang.get(t.getKey()).getAsString();
					Matcher m = ARG.matcher(text);
					int wanted = 0;
					int plain = 0;
					while (m.find()) {
						if (m.group(1) != null) {
							wanted = Math.max(wanted, Integer.parseInt(m.group(1).substring(0, m.group(1).length() - 1)));
						} else {
							plain++;
						}
					}
					wanted = Math.max(wanted, plain);
					if (wanted != t.getArgs().length) {
						problems.add(where + ": " + t.getKey() + " wants " + wanted + " argument(s), got " + t.getArgs().length);
					}
				}
			}
			for (Object arg : t.getArgs()) {
				if (arg instanceof Component a) {
					checkText(a, lang, where, problems, seen);
				}
			}
		}
		for (Component sibling : c.getSiblings()) {
			checkText(sibling, lang, where, problems, seen);
		}
	}

	private static void checkScreen(ChoiceMenu menu, JsonObject lang, List<String> problems, List<String> seen) {
		for (int slot = 0; slot < ChoiceMenu.SIZE; slot++) {
			ItemStack icon = menu.icon(slot);
			if (icon.isEmpty()) {
				continue;
			}
			Component name = icon.get(DataComponents.CUSTOM_NAME);
			if (name != null) {
				checkText(name, lang, "slot " + slot + " name", problems, seen);
			}
			ItemLore lore = icon.get(DataComponents.LORE);
			if (lore != null) {
				for (Component line : lore.lines()) {
					checkText(line, lang, "slot " + slot + " lore", problems, seen);
				}
			}
		}
	}

	/**
	 * Every sentence on the screen goes through en_us.json with the right arguments: an Expert's cards (with the special
	 * request), a party with each kind of refusal (untradeable, wrong type, too low, wrong line), the click and held-item
	 * hints, the second-click confirm and the used-up state; and the texts the client screen draws itself exist.
	 */
	//$ gametest AREA
	@GameTest(template = AREA)
	public void qaEverySentenceThroughLang(GameTestHelper helper) {
		Setup s = setup(helper, 4, null);
		JsonObject lang = lang();
		List<String> problems = new ArrayList<>();
		List<String> seen = new ArrayList<>();
		for (String k : List.of("screen.aliveworkplace.pokemon_trader.party", "screen.aliveworkplace.pokemon_trader.no_offers",
			"screen.aliveworkplace.pokemon_trader.no_party")) {
			if (!lang.has(k)) {
				problems.add("the screen draws " + k + ", which isn't in en_us.json");
			}
		}
		Pokemon held = first(fits(s.offer()), 0).create(s.offer().minLevel() + 5);
		held.swapHeldItem(new ItemStack(BuiltInRegistries.ITEM.get(ResourceLocation.fromNamespaceAndPath("cobblemon", "oran_berry"))), false, false);
		Pokemon untradeable = first(fits(s.offer()), 1).create(s.offer().minLevel() + 5);
		untradeable.setTradeable(false);
		Pokemon wrong = first(fits(s.offer()).negate(), 0).create(s.offer().minLevel() + 5);
		Pokemon low = first(fits(s.offer()), 2).create(Math.max(1, s.offer().minLevel() - 3));
		Pokemon plain = first(fits(s.offer()), 3).create(s.offer().minLevel() + 5);
		for (Pokemon p : List.of(held, untradeable, wrong, low, plain)) {
			s.party().add(p);
		}
		ChoiceMenu menu = open(helper, s);
		checkScreen(menu, lang, problems, seen);
		click(s.player(), menu, slotOf(s.party(), plain));
		checkScreen(menu, lang, problems, seen);
		// The Expert's special request (the last card).
		int special = -1;
		List<CobblemonTraders.Offer> offers = CobblemonTraders.offers(s.trader());
		for (int i = 0; i < offers.size(); i++) {
			if (offers.get(i).wantedSpecies() != null) {
				special = i;
			}
		}
		helper.assertTrue(special >= 0, "the Expert has no special request: " + offers);
		click(s.player(), menu, PokemonTradeView.OFFERS + special);
		checkScreen(menu, lang, problems, seen);
		click(s.player(), menu, PokemonTradeView.OFFERS);
		int plainSlot = slotOf(s.party(), plain);
		click(s.player(), menu, plainSlot);
		click(s.player(), menu, plainSlot);
		helper.assertValueEqual(trades(s), 1, "the trade for the used-up state");
		checkScreen(menu, lang, problems, seen);

		for (String k : List.of("message.aliveworkplace.tutor.pokemon", "screen.aliveworkplace.pokemon_trader.cost",
			"screen.aliveworkplace.pokemon_trader.pick", "message.aliveworkplace.pokemon_trader.confirm",
			"message.aliveworkplace.pokemon_trader.untradeable", "message.aliveworkplace.pokemon_trader.too_low",
			"message.aliveworkplace.pokemon_trader.click", "message.aliveworkplace.pokemon_trader.held_item",
			"message.aliveworkplace.pokemon_trader.come_back", "message.aliveworkplace.pokemon_trader.info")) {
			if (!seen.contains(k)) {
				problems.add("the screen never showed " + k);
			}
		}
		if (seen.stream().noneMatch(k -> k.startsWith("message.aliveworkplace.pokemon_trader.wrong_type"))) {
			problems.add("the screen never showed wrong_type");
		}
		if (seen.stream().noneMatch(k -> k.startsWith("message.aliveworkplace.pokemon_trader.wrong_species"))) {
			problems.add("the screen never showed wrong_species");
		}
		if (seen.stream().noneMatch(k -> k.startsWith("screen.aliveworkplace.pokemon_trader.cost_species"))) {
			problems.add("the screen never showed cost_species");
		}
		helper.assertTrue(problems.isEmpty(), String.join("; ", problems));
		s.player().closeContainer();
		helper.succeed();
	}
}
