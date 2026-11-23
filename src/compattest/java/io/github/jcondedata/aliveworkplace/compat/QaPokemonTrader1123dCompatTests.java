package io.github.jcondedata.aliveworkplace.compat;

import com.cobblemon.mod.common.Cobblemon;
import com.cobblemon.mod.common.api.pokemon.PokemonSpecies;
import com.cobblemon.mod.common.api.storage.party.PlayerPartyStore;
import com.cobblemon.mod.common.pokemon.Pokemon;
import com.cobblemon.mod.common.pokemon.Species;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import io.github.jcondedata.aliveworkplace.compat.cobblemon.CobblemonTraders;
import io.github.jcondedata.aliveworkplace.people.Traits;
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
import java.util.Map;
import java.util.UUID;
import java.util.function.Predicate;
import net.fabricmc.fabric.api.event.player.UseEntityCallback;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ServerboundContainerClickPacket;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.npc.VillagerProfession;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.properties.BedPart;
import net.minecraft.world.phys.Vec3;

/**
 * Lane D QA, 2026-11-23: the Pokémon Trader (docs/wiki/pokemon-trader.md; ROADMAP 28.23, B312, 43.2, B1354), the
 * promises the earlier QA classes (QaTraderOffers1107d, QaPokemonTradeScreen) leave open: a trader who falls asleep
 * closes the screen and a sleeping one opens none; a held item comes back even with a full inventory; your only Pokémon,
 * a shiny one, trades; 6 experience a trade and the rank-up's extra offer; only an empty hand, a grown-up trader and no
 * sneak open the screen; the offers the same all day for everyone; the special request shiny one time in four; the trade
 * count saved; and the sentences the page quotes, word for word, in American English.
 */
public class QaPokemonTrader1123dCompatTests implements FabricGameTest {
	private static final String AREA = CompatGameTests.AREA;

	private record Setup(Villager trader, ServerPlayer player, PlayerPartyStore party, CobblemonTraders.Offer offer) {
	}

	/** A UUID (from a fixed seed) whose villager has no trait that changes the experience he gains. */
	private static UUID plainId(long seed) {
		for (long i = 0; ; i++) {
			UUID id = new UUID(0x1123DL + seed * 104729L + i, 0x7EADE5L + i);
			if (!Traits.of(id).contains(Traits.Trait.CLEVER)) {
				return id;
			}
		}
	}

	private static Setup setup(GameTestHelper helper, int tier, long seed) {
		BlockPos board = new BlockPos(2, 2, 2);
		helper.setBlock(board, ModBlocks.TRADE_BOARD);
		Villager trader = EntityType.VILLAGER.create(helper.getLevel());
		trader.setUUID(plainId(seed));
		Vec3 at = Vec3.atBottomCenterOf(helper.absolutePos(new BlockPos(3, 2, 3)));
		trader.moveTo(at.x, at.y, at.z, 0, 0);
		helper.getLevel().addFreshEntity(trader);
		Jobs.employ(helper.getLevel(), trader, helper.absolutePos(board), ModVillagers.TRADE_BOARD_POI, ModVillagers.POKEMON_TRADER);
		trader.setVillagerData(trader.getVillagerData().setLevel(tier));
		trader.setNoAi(true);
		ServerPlayer player = fresh(helper);
		return new Setup(trader, player, Cobblemon.INSTANCE.getStorage().getParty(player), CobblemonTraders.offers(trader).get(0));
	}

	private static ServerPlayer fresh(GameTestHelper helper) {
		ServerPlayer player = helper.makeMockServerPlayerInLevel();
		player.setGameMode(GameType.SURVIVAL);
		Vec3 at = Vec3.atBottomCenterOf(helper.absolutePos(new BlockPos(4, 2, 4)));
		player.moveTo(at.x, at.y, at.z);
		return player;
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

	private static void click(ServerPlayer p, int containerId, int slot) {
		p.connection.handleContainerClick(new ServerboundContainerClickPacket(containerId, p.containerMenu.getStateId(), slot, 0, ClickType.PICKUP,
			ItemStack.EMPTY, new Int2ObjectOpenHashMap<>()));
	}

	private static void rightClick(GameTestHelper helper, ServerPlayer player, Villager villager) {
		UseEntityCallback.EVENT.invoker().interact(player, helper.getLevel(), InteractionHand.MAIN_HAND, villager, null);
	}

	private static boolean tradeScreenOpen(ServerPlayer player) {
		return player.containerMenu instanceof ChoiceMenu m && m.getType() == ModBlocks.POKEMON_TRADER_MENU;
	}

	private static Item item(String ns, String path) {
		return BuiltInRegistries.ITEM.get(ResourceLocation.fromNamespaceAndPath(ns, path));
	}

	/**
	 * "The screen closes if ... the trader ... falls asleep": a trader asleep when right-clicked opens no screen; one who
	 * falls asleep with it open closes it, and the pick waiting for its second click trades nothing.
	 */
	//$ gametest_ticks AREA '100'
	@GameTest(template = AREA, timeoutTicks = 100)
	public void qaSleepingTraderClosesTheScreen(GameTestHelper helper) {
		helper.setDayTime(2000);
		Setup s = setup(helper, 1, 1);
		Pokemon mine = first(fits(s.offer()), 0).create(s.offer().minLevel() + 5);
		s.party().add(mine);
		// A real bed (a sleeper whose bed is gone wakes up): foot at z 5, head at z 6.
		helper.setBlock(new BlockPos(3, 2, 5), Blocks.RED_BED.defaultBlockState().setValue(BedBlock.FACING, Direction.SOUTH).setValue(BedBlock.PART, BedPart.FOOT));
		helper.setBlock(new BlockPos(3, 2, 6), Blocks.RED_BED.defaultBlockState().setValue(BedBlock.FACING, Direction.SOUTH).setValue(BedBlock.PART, BedPart.HEAD));
		BlockPos bed = helper.absolutePos(new BlockPos(3, 2, 6));
		s.trader().startSleeping(bed);
		helper.assertTrue(s.trader().isSleeping(), "the trader didn't fall asleep");
		rightClick(helper, s.player(), s.trader());
		helper.assertFalse(tradeScreenOpen(s.player()), "a sleeping trader's screen opened");
		s.trader().stopSleeping();
		rightClick(helper, s.player(), s.trader());
		helper.assertTrue(tradeScreenOpen(s.player()), "the awake trader's screen didn't open: " + s.player().containerMenu);
		ChoiceMenu menu = (ChoiceMenu) s.player().containerMenu;
		int id = menu.containerId;
		int slot = slotOf(s.party(), mine);
		click(s.player(), id, slot);
		s.trader().startSleeping(bed);
		helper.assertFalse(menu.stillValid(s.player()), "the screen still counts as valid with the trader asleep");
		helper.runAfterDelay(10, () -> {
			helper.assertFalse(s.player().containerMenu == menu, "the screen stayed open after the trader fell asleep");
			click(s.player(), id, slot);
			helper.assertTrue(contains(s.party(), mine), "a sleeping trader traded");
			helper.assertValueEqual(ModAttachments.POKEMON_TRADE_COUNT.getOrElse(s.trader(), 0), 0, "trades");
			s.trader().stopSleeping();
			helper.succeed();
		});
	}

	/** "A held item comes back to you": with every inventory slot full it lands at the player's feet, not lost. */
	//$ gametest_batch AREA '"qaHeldItemFullInventory1123d"'
	@GameTest(template = AREA, batch = "qaHeldItemFullInventory1123d")
	public void qaHeldItemComesBackWithAFullInventory(GameTestHelper helper) {
		PartnerShowsCompatTests.clearLeftovers(helper);
		helper.setDayTime(2000);
		Setup s = setup(helper, 1, 2);
		Item berry = item("cobblemon", "oran_berry");
		Pokemon mine = first(fits(s.offer()), 0).create(s.offer().minLevel() + 5);
		mine.swapHeldItem(new ItemStack(berry), false, false);
		s.party().add(mine);
		for (int i = 0; i < s.player().getInventory().getContainerSize(); i++) {
			s.player().getInventory().setItem(i, new ItemStack(Items.COBBLESTONE, 64));
		}
		helper.assertTrue(s.player().getInventory().getFreeSlot() < 0, "the inventory isn't full");
		helper.assertTrue(CobblemonTraders.trade(s.player(), s.trader(), mine, s.offer()), "the trade didn't go through");
		helper.assertFalse(contains(s.party(), mine), "the traded Pokémon is still in the party");
		int dropped = 0;
		for (ItemEntity e : helper.getLevel().getEntitiesOfClass(ItemEntity.class, s.player().getBoundingBox().inflate(4))) {
			if (e.getItem().is(berry)) {
				dropped += e.getItem().getCount();
				e.discard();
			}
		}
		helper.assertValueEqual(dropped, 1, "Oran Berries dropped at the player's feet");
		helper.succeed();
	}

	/**
	 * A Pokémon fits when it's tradeable, of the wanted type and level: a shiny one fits too, and it can be the player's
	 * only Pokémon (it leaves before the trader's joins, so the party is never empty). Clicked twice on the real screen.
	 */
	//$ gametest AREA
	@GameTest(template = AREA)
	public void qaOnlyPokemonShinyTrades(GameTestHelper helper) {
		helper.setDayTime(2000);
		Setup s = setup(helper, 1, 3);
		Pokemon mine = first(fits(s.offer()), 0).create(s.offer().minLevel());
		mine.setShiny(true);
		s.party().add(mine);
		helper.assertValueEqual(s.party().occupied(), 1, "party before");
		helper.assertTrue(s.offer().refusal(mine) == null, "a shiny that fits was refused");
		rightClick(helper, s.player(), s.trader());
		helper.assertTrue(tradeScreenOpen(s.player()), "no trade screen: " + s.player().containerMenu);
		ChoiceMenu menu = (ChoiceMenu) s.player().containerMenu;
		int slot = slotOf(s.party(), mine);
		click(s.player(), menu.containerId, slot);
		click(s.player(), menu.containerId, slot);
		helper.assertFalse(contains(s.party(), mine), "the player's only Pokémon wasn't traded");
		helper.assertValueEqual(s.party().occupied(), 1, "party after");
		Pokemon got = s.party().get(0);
		helper.assertTrue(got != null && got.getSpecies() == s.offer().species() && got.getLevel() == s.offer().level(),
			"the party holds " + (got == null ? "nothing" : got.getSpecies().getName() + " Lv. " + got.getLevel()) + ", not the offer");
		s.player().closeContainer();
		helper.succeed();
	}

	/**
	 * "The trader gains 6 experience per trade", and a Novice who reaches Apprentice has the Apprentice's 2 offers at
	 * once.
	 */
	//$ gametest AREA
	@GameTest(template = AREA)
	public void qaTradeGivesSixXpAndRanksUp(GameTestHelper helper) {
		helper.setDayTime(2000);
		Setup s = setup(helper, 1, 4);
		Villager trader = s.trader();
		trader.setVillagerXp(0);
		Pokemon a = first(fits(s.offer()), 0).create(s.offer().minLevel() + 5);
		s.party().add(a);
		helper.assertTrue(CobblemonTraders.trade(s.player(), trader, a, s.offer()), "the first trade");
		helper.assertValueEqual(trader.getVillagerXp(), PokemonTraders.XP_PER_TRADE, "experience after one trade");
		helper.assertValueEqual(PokemonTraders.XP_PER_TRADE, 6, "the experience per trade");
		helper.assertValueEqual(PokemonTraders.tier(trader), 1, "tier after 6 experience");
		ServerPlayer other = fresh(helper);
		PlayerPartyStore otherParty = Cobblemon.INSTANCE.getStorage().getParty(other);
		Pokemon b = first(fits(s.offer()), 1).create(s.offer().minLevel() + 5);
		otherParty.add(b);
		helper.assertTrue(CobblemonTraders.trade(other, trader, b, s.offer()), "the second player's trade");
		helper.assertValueEqual(trader.getVillagerXp(), 12, "experience after two trades");
		helper.assertValueEqual(PokemonTraders.tier(trader), 2, "a Novice with 12 experience didn't become an Apprentice");
		helper.assertValueEqual(CobblemonTraders.offers(trader).size(), 2, "the new Apprentice's offers");
		helper.succeed();
	}

	/**
	 * Only "a right-click with an empty hand" opens the Pokémon trade screen: not with an item in hand, not while
	 * sneaking, not on a child of the profession and not on a villager of another job.
	 */
	//$ gametest AREA
	@GameTest(template = AREA)
	public void qaOnlyAnEmptyHandOnAGrownTraderOpens(GameTestHelper helper) {
		helper.setDayTime(2000);
		Setup s = setup(helper, 1, 5);
		ServerPlayer p = s.player();
		p.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.EMERALD));
		rightClick(helper, p, s.trader());
		helper.assertFalse(tradeScreenOpen(p), "the screen opened with an emerald in hand");
		p.closeContainer();
		p.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
		p.setShiftKeyDown(true);
		rightClick(helper, p, s.trader());
		helper.assertFalse(tradeScreenOpen(p), "the screen opened while sneaking");
		p.closeContainer();
		p.setShiftKeyDown(false);

		Villager child = helper.spawn(EntityType.VILLAGER, new BlockPos(6, 2, 3));
		child.setNoAi(true);
		child.setVillagerData(child.getVillagerData().setProfession(ModVillagers.POKEMON_TRADER));
		child.setBaby(true);
		rightClick(helper, p, child);
		helper.assertFalse(tradeScreenOpen(p), "a child villager opened the trade screen");
		p.closeContainer();

		Villager farmer = helper.spawn(EntityType.VILLAGER, new BlockPos(7, 2, 5));
		farmer.setNoAi(true);
		farmer.setVillagerData(farmer.getVillagerData().setProfession(VillagerProfession.FARMER).setLevel(2));
		rightClick(helper, p, farmer);
		helper.assertFalse(tradeScreenOpen(p), "a farmer opened the Pokémon trade screen");
		p.closeContainer();

		// The control: the same player, empty hand, standing, on the grown trader.
		rightClick(helper, p, s.trader());
		helper.assertTrue(tradeScreenOpen(p), "the control didn't open the screen: " + p.containerMenu);
		p.closeContainer();
		helper.succeed();
	}

	/**
	 * "Offers are the same all day for everyone and change every in-game day": the first tick and the last of a day give
	 * the same offers, two players' screens show the same cards, and the first tick of the next day gives new ones.
	 */
	//$ gametest_batch AREA '"qaSameAllDay1123d"'
	@GameTest(template = AREA, batch = "qaSameAllDay1123d")
	public void qaOffersSameAllDayForEveryone(GameTestHelper helper) {
		PartnerShowsCompatTests.clearLeftovers(helper);
		long base = 24000L * 60;
		try {
			helper.setDayTime((int) base);
			Setup s = setup(helper, 5, 6);
			List<CobblemonTraders.Offer> morning = CobblemonTraders.offers(s.trader());
			List<String> cardsA = cards(CobblemonTraders.menuForTest(s.player(), s.trader()));
			helper.setDayTime((int) (base + 12000));
			helper.assertTrue(CobblemonTraders.offers(s.trader()).equals(morning), "the offers changed at dusk");
			helper.setDayTime((int) (base + 23999));
			helper.assertTrue(CobblemonTraders.offers(s.trader()).equals(morning), "the offers changed on the day's last tick");
			ServerPlayer other = fresh(helper);
			List<String> cardsB = cards(CobblemonTraders.menuForTest(other, s.trader()));
			helper.assertTrue(cardsA.equals(cardsB), "two players see different cards: " + cardsA + " / " + cardsB);
			helper.assertValueEqual(cardsA.size(), 4, "a Master's cards");
			helper.setDayTime((int) (base + 24000));
			helper.assertFalse(CobblemonTraders.offers(s.trader()).equals(morning), "the offers didn't change the next day");
		} finally {
			helper.setDayTime(2000);
		}
		helper.succeed();
	}

	private static List<String> cards(ChoiceMenu menu) {
		List<String> out = new ArrayList<>();
		for (int i = 0; i < PokemonTradeView.MAX_OFFERS; i++) {
			ItemStack icon = menu.icon(PokemonTradeView.OFFERS + i);
			ItemStack cost = menu.icon(PokemonTradeView.COSTS + i);
			if (!icon.isEmpty()) {
				out.add(String.valueOf(icon.get(DataComponents.CUSTOM_NAME)) + " | " + cost.get(DataComponents.CUSTOM_NAME));
			}
		}
		return out;
	}

	/** "The special request is shiny one time in four", for Experts and Masters alike. */
	//$ gametest 'FabricGameTest.EMPTY_STRUCTURE'
	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
	public void qaSpecialRequestShinyOneInFour(GameTestHelper helper) {
		int specials = 0;
		int shiny = 0;
		for (int u = 0; u < 3; u++) {
			UUID id = new UUID(0x5EC1A1L + u * 31L, 0x1123DL);
			for (int tier = 4; tier <= 5; tier++) {
				for (long day = 0; day < 150; day++) {
					for (CobblemonTraders.Offer offer : CobblemonTraders.offers(id, tier, day)) {
						if (offer.wantedSpecies() != null) {
							specials++;
							shiny += offer.shiny() ? 1 : 0;
						}
					}
				}
			}
		}
		helper.assertValueEqual(specials, 900, "special requests over 150 days, 3 traders, 2 tiers");
		helper.assertTrue(shiny >= 170 && shiny <= 280, shiny + " of " + specials + " special requests shiny, not about one in four");
		helper.succeed();
	}

	/**
	 * Saved data: the trade count starts at 0 and, like the day each player last traded, survives a save and load.
	 */
	//$ gametest AREA
	@GameTest(template = AREA)
	public void qaTradeCountSurvivesReload(GameTestHelper helper) {
		helper.setDayTime(2000);
		Setup s = setup(helper, 1, 7);
		helper.assertValueEqual(ModAttachments.POKEMON_TRADE_COUNT.getOrElse(s.trader(), 0), 0, "a new trader's trades");
		helper.assertTrue(ModAttachments.POKEMON_TRADES.getOrElse(s.trader(), Map.of()).isEmpty(), "a new trader remembers trades");
		Pokemon a = first(fits(s.offer()), 0).create(s.offer().minLevel() + 5);
		s.party().add(a);
		helper.assertTrue(CobblemonTraders.trade(s.player(), s.trader(), a, s.offer()), "the first trade");
		ServerPlayer other = fresh(helper);
		Pokemon b = first(fits(s.offer()), 1).create(s.offer().minLevel() + 5);
		Cobblemon.INSTANCE.getStorage().getParty(other).add(b);
		helper.assertTrue(CobblemonTraders.trade(other, s.trader(), b, s.offer()), "the second player's trade");

		CompoundTag saved = s.trader().saveWithoutId(new CompoundTag());
		Villager copy = EntityType.VILLAGER.create(helper.getLevel());
		copy.load(saved);
		helper.assertValueEqual(ModAttachments.POKEMON_TRADE_COUNT.getOrElse(copy, 0), 2, "trades after a reload");
		helper.assertTrue(PokemonTraders.tradedToday(copy, s.player().getUUID()) && PokemonTraders.tradedToday(copy, other.getUUID()),
			"a player's trade was forgotten after a reload");
		helper.assertValueEqual(PokemonTraders.tier(copy), 2, "the tier after a reload (12 experience)");
		copy.discard();
		helper.succeed();
	}

	private static JsonObject lang() {
		try (var in = QaPokemonTrader1123dCompatTests.class.getResourceAsStream("/assets/aliveworkplace/lang/en_us.json")) {
			return JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonObject();
		} catch (Exception e) {
			throw new IllegalStateException("can't read en_us.json", e);
		}
	}

	/**
	 * The sentences the page quotes, word for word: "No offers today. Come back tomorrow!", "Your party", "For: any
	 * Water, Lv. 20+", "For: a Pikachu line, Lv. 25+", "can't be traded", the Cobblemon-missing line; and every trader
	 * sentence in American English.
	 */
	//$ gametest 'FabricGameTest.EMPTY_STRUCTURE'
	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
	public void qaQuotedSentencesWordForWord(GameTestHelper helper) {
		JsonObject lang = lang();
		List<String> problems = new ArrayList<>();
		String p = "screen.aliveworkplace.pokemon_trader.";
		String m = "message.aliveworkplace.pokemon_trader.";
		check(problems, lang, p + "no_offers", null, "No offers today. Come back tomorrow!");
		check(problems, lang, p + "party", null, "Your party");
		check(problems, lang, p + "cost", new Object[] {"Water", 20}, "For: any Water, Lv. 20+");
		check(problems, lang, p + "cost_species", new Object[] {"Pikachu", 25}, "For: a Pikachu line, Lv. 25+");
		check(problems, lang, p + "cost_species.an", new Object[] {"Eevee", 25}, "For: an Eevee line, Lv. 25+");
		check(problems, lang, "entity.minecraft.villager.pokemon_trader", null, "Pokémon Trader");
		String untradeable = text(lang, m + "untradeable");
		if (untradeable == null || !untradeable.contains("can't be traded")) {
			problems.add(m + "untradeable is " + untradeable + ", not \"... can't be traded\"");
		}
		String missing = text(lang, m + "no_cobblemon");
		if (missing == null || !missing.contains("Pokémon Traders") || !missing.contains("Cobblemon")) {
			problems.add(m + "no_cobblemon doesn't say Pokémon Traders need Cobblemon: " + missing);
		}
		List<String> british = List.of("colour", "grey", "favour", "honour", "centre", "cancelled", "armour", "defence", "licence",
			"realise", "organise", "travelled");
		for (String key : lang.keySet()) {
			if (key.contains("pokemon_trader")) {
				String text = lang.get(key).getAsString().toLowerCase();
				for (String word : british) {
					if (text.contains(word)) {
						problems.add(key + " uses British \"" + word + "\": " + text);
					}
				}
			}
		}
		helper.assertTrue(problems.isEmpty(), String.join("; ", problems));
		helper.succeed();
	}

	private static String text(JsonObject lang, String key) {
		return lang.has(key) ? lang.get(key).getAsString() : null;
	}

	private static void check(List<String> problems, JsonObject lang, String key, Object[] args, String wanted) {
		String text = text(lang, key);
		if (text == null) {
			problems.add(key + " isn't in en_us.json");
			return;
		}
		String got = args == null ? text : String.format(text, args);
		if (!got.equals(wanted)) {
			problems.add(key + " reads \"" + got + "\", the page says \"" + wanted + "\"");
		}
	}
}
