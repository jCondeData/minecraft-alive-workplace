package io.github.jcondedata.aliveworkplace.compat;

import com.cobblemon.mod.common.Cobblemon;
import com.cobblemon.mod.common.api.pokemon.PokemonSpecies;
import com.cobblemon.mod.common.api.storage.party.PlayerPartyStore;
import com.cobblemon.mod.common.api.types.ElementalType;
import com.cobblemon.mod.common.pokemon.Pokemon;
import com.cobblemon.mod.common.pokemon.Species;
import io.github.jcondedata.aliveworkplace.compat.cobblemon.CobblemonTraders;
import io.github.jcondedata.aliveworkplace.registry.ModAttachments;
import io.github.jcondedata.aliveworkplace.registry.ModBlocks;
import io.github.jcondedata.aliveworkplace.registry.ModVillagers;
import io.github.jcondedata.aliveworkplace.trader.PokemonTraders;
import io.github.jcondedata.aliveworkplace.work.Jobs;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.Vec3;

/**
 * Lane D QA, 2026-11-07: the Pokémon Trader's offers and trades, from the wiki page (docs/wiki/pokemon-trader.md) and
 * the class's own promises. Legendary, mythical, Ultra Beast and Paradox Pokémon are never offered or requested; each
 * tier's offer count and level range (Novice 1 at 5-15 .. Master 3 at 50-70, plus the Expert's and Master's special
 * request); a special request takes any Pokémon of the line, at its wanted level and not one below; a Pokémon whose
 * second type is wanted fits; an untradeable Pokémon is refused with its own sentence; a full party still trades; and
 * one trade a day is per player, back the next day; Novices offer first forms and Masters final ones, and only a
 * Master's regular offers are ever shiny.
 */
public class QaTraderOffers1107dCompatTests implements FabricGameTest {
	private static final String AREA = CompatGameTests.AREA;
	private static final Set<String> NOT_FOR_TRADE = Set.of("legendary", "mythical", "ultra_beast", "paradox");
	private static final int[] COUNT = {1, 2, 2, 4, 4};
	private static final int[] MIN = {5, 15, 25, 35, 50};
	private static final int[] MAX = {15, 25, 35, 50, 70};

	private static List<Species> all() {
		List<Species> all = new ArrayList<>(PokemonSpecies.getImplemented());
		all.sort(Comparator.comparing(Species::getName));
		return all;
	}

	private static boolean hasType(Iterable<ElementalType> types, ElementalType wanted) {
		for (ElementalType t : types) {
			if (t.getName().equals(wanted.getName())) {
				return true;
			}
		}
		return false;
	}

	private static Species root(Species s) {
		Species current = s;
		for (int i = 0; i < 5 && current.getPreEvolution() != null; i++) {
			current = current.getPreEvolution().getSpecies();
		}
		return current;
	}

	private static String key(Component c) {
		return c != null && c.getContents() instanceof TranslatableContents t ? t.getKey() : String.valueOf(c);
	}

	/** The sentence's key without the "an" variant Words.articled picks before a vowel sound ("wrong_type.an"). */
	private static String base(Component c) {
		String k = key(c);
		return k.endsWith(".an") ? k.substring(0, k.length() - 3) : k;
	}

	private static boolean contains(PlayerPartyStore party, Pokemon pokemon) {
		for (Pokemon p : party) {
			if (p == pokemon) {
				return true;
			}
		}
		return false;
	}

	private static Villager trader(GameTestHelper helper, int tier) {
		BlockPos board = new BlockPos(2, 2, 2);
		helper.setBlock(board, ModBlocks.TRADE_BOARD);
		Villager trader = helper.spawn(EntityType.VILLAGER, new BlockPos(3, 2, 3));
		Jobs.employ(helper.getLevel(), trader, helper.absolutePos(board), ModVillagers.TRADE_BOARD_POI, ModVillagers.POKEMON_TRADER);
		trader.setVillagerData(trader.getVillagerData().setLevel(tier));
		trader.setNoAi(true);
		return trader;
	}

	private static ServerPlayer player(GameTestHelper helper) {
		ServerPlayer player = helper.makeMockServerPlayerInLevel();
		player.setGameMode(GameType.SURVIVAL);
		Vec3 at = Vec3.atBottomCenterOf(helper.absolutePos(new BlockPos(4, 2, 4)));
		player.moveTo(at.x, at.y, at.z);
		return player;
	}

	/** A Pokémon that fits {@code offer} (its wanted type), {@code skip} species into the sorted list, at {@code level}. */
	private static Pokemon fitting(CobblemonTraders.Offer offer, int skip, int level) {
		return all().stream().filter(s -> hasType(s.getTypes(), offer.wanted())).skip(skip).findFirst().orElseThrow().create(level);
	}

	/** No legendary, mythical, Ultra Beast or Paradox Pokémon is ever offered or asked for, at any tier on any day. */
	//$ gametest 'FabricGameTest.EMPTY_STRUCTURE'
	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
	public void qaNoLegendsOfferedOrRequested(GameTestHelper helper) {
		int checked = 0;
		for (int u = 0; u < 4; u++) {
			UUID id = new UUID(0x51ACE0L + u * 7919L, 0xD00DL * (u + 1));
			for (int tier = 1; tier <= 5; tier++) {
				for (long day = 0; day < 25; day++) {
					for (CobblemonTraders.Offer offer : CobblemonTraders.offers(id, tier, day)) {
						for (String label : offer.species().getLabels()) {
							helper.assertFalse(NOT_FOR_TRADE.contains(label), "tier " + tier + " day " + day + " offers " + offer.species().getName() + " (" + label + ")");
						}
						if (offer.wantedSpecies() != null) {
							for (String label : offer.wantedSpecies().getLabels()) {
								helper.assertFalse(NOT_FOR_TRADE.contains(label), "tier " + tier + " day " + day + " asks for " + offer.wantedSpecies().getName() + " (" + label + ")");
							}
						}
						checked++;
					}
				}
			}
		}
		helper.assertTrue(checked > 300, "only " + checked + " offers checked");
		helper.succeed();
	}

	/**
	 * Every tier's day: Novice 1 offer, Apprentice and Journeyman 2, Expert and Master 3 plus a special request; the
	 * offers are in the tier's level range and two different Pokémon; each wants a level the player can meet at or below
	 * the offer's own, never under 5; the special request is one of the trader's at the top of his range for a line's first
	 * form, from 10 levels under his lowest.
	 */
	//$ gametest 'FabricGameTest.EMPTY_STRUCTURE'
	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
	public void qaEveryTiersCountsAndLevels(GameTestHelper helper) {
		UUID id = new UUID(0xBEEFL, 0x7EADL);
		for (int tier = 1; tier <= 5; tier++) {
			int t = tier - 1;
			for (long day = 0; day < 12; day++) {
				List<CobblemonTraders.Offer> offers = CobblemonTraders.offers(id, tier, day);
				String where = "tier " + tier + " day " + day + ": ";
				helper.assertValueEqual(offers.size(), COUNT[t], where + "offers");
				Set<String> species = new HashSet<>();
				for (CobblemonTraders.Offer offer : offers) {
					helper.assertTrue(species.add(offer.species().getName()), where + offer.species().getName() + " offered twice");
					if (offer.wantedSpecies() == null) {
						helper.assertTrue(offer.level() >= MIN[t] && offer.level() <= MAX[t], where + "level " + offer.level());
						helper.assertTrue(offer.minLevel() >= 5 && offer.minLevel() <= offer.level(), where + "wants level " + offer.minLevel() + " for a " + offer.level());
						helper.assertFalse(hasType(offer.species().getTypes(), offer.wanted()), where + "wants the type it gives away");
					} else {
						helper.assertTrue(tier >= 4, where + "a special request below Expert");
						helper.assertValueEqual(offer.level(), MAX[t], where + "the special request's level");
						helper.assertValueEqual(offer.minLevel(), MIN[t] - 10, where + "the special request's wanted level");
						helper.assertTrue(offer.wantedSpecies().getPreEvolution() == null && !offer.wantedSpecies().getEvolutions().isEmpty(),
							where + "the request " + offer.wantedSpecies().getName() + " isn't the first of a line");
					}
				}
			}
		}
		helper.succeed();
	}

	/**
	 * A special request takes any Pokémon of the line ("a Pikachu line"): an evolved member at the wanted level fits, one
	 * level under is refused as too low, and a Pokémon of another line of the wanted type is refused as the wrong Pokémon.
	 */
	//$ gametest 'FabricGameTest.EMPTY_STRUCTURE'
	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
	public void qaSpecialRequestTakesTheWholeLine(GameTestHelper helper) {
		List<Species> all = all();
		int tried = 0;
		for (int u = 0; u < 6 && tried < 3; u++) {
			UUID id = new UUID(0xC0FFEEL + u, 0x1107L);
			for (long day = 0; day < 8 && tried < 3; day++) {
				for (CobblemonTraders.Offer offer : CobblemonTraders.offers(id, 5, day)) {
					Species wanted = offer.wantedSpecies();
					if (wanted == null) {
						continue;
					}
					Species evolved = all.stream().filter(s -> s.getPreEvolution() != null && root(s) == wanted).findFirst().orElse(null);
					if (evolved == null) {
						continue;
					}
					tried++;
					String what = evolved.getName() + " for a " + wanted.getName() + " request";
					Pokemon fits = evolved.create(offer.minLevel());
					helper.assertTrue(offer.refusal(fits) == null, what + " at the wanted level was refused: " + key(offer.refusal(fits)));
					Pokemon low = evolved.create(offer.minLevel() - 1);
					helper.assertValueEqual(key(offer.refusal(low)), "message.aliveworkplace.pokemon_trader.too_low", what + " one level under");
					Species other = all.stream().filter(s -> root(s) != wanted && hasType(s.getTypes(), wanted.getPrimaryType())).findFirst().orElseThrow();
					Pokemon wrong = other.create(Math.max(offer.minLevel(), 1) + 5);
					helper.assertValueEqual(base(offer.refusal(wrong)), "message.aliveworkplace.pokemon_trader.wrong_species",
						other.getName() + " (same type, another line) for a " + wanted.getName() + " request");
				}
			}
		}
		helper.assertTrue(tried >= 3, "only " + tried + " special requests with an evolved form found");
		helper.succeed();
	}

	/** "Any Water" means any Pokémon with Water among its types: one whose second type is the wanted one fits. */
	//$ gametest 'FabricGameTest.EMPTY_STRUCTURE'
	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
	public void qaSecondTypeFits(GameTestHelper helper) {
		List<Species> all = all();
		int tried = 0;
		for (long day = 0; day < 20 && tried < 5; day++) {
			for (CobblemonTraders.Offer offer : CobblemonTraders.offers(new UUID(0xAB12L, day), 2, day)) {
				Species dual = all.stream().filter(s -> s.getSecondaryType() != null
					&& s.getSecondaryType().getName().equals(offer.wanted().getName())
					&& !s.getPrimaryType().getName().equals(offer.wanted().getName())).findFirst().orElse(null);
				if (dual == null) {
					continue;
				}
				tried++;
				Pokemon p = dual.create(offer.minLevel());
				helper.assertTrue(offer.refusal(p) == null, dual.getName() + " (" + dual.getPrimaryType().getName() + "/" + dual.getSecondaryType().getName()
					+ ") refused for any " + offer.wanted().getName() + ": " + key(offer.refusal(p)));
				Species single = all.stream().filter(s -> !hasType(s.getTypes(), offer.wanted())).findFirst().orElseThrow();
				helper.assertValueEqual(base(offer.refusal(single.create(offer.minLevel() + 5))), "message.aliveworkplace.pokemon_trader.wrong_type",
					single.getName() + " for any " + offer.wanted().getName());
			}
		}
		helper.assertTrue(tried >= 5, "only " + tried + " offers with a dual-type fit found");
		helper.succeed();
	}

	/** An untradeable Pokémon is refused with its own sentence even when it fits, and a trade with it changes nothing. */
	//$ gametest AREA
	@GameTest(template = AREA)
	public void qaUntradeableRefused(GameTestHelper helper) {
		helper.setDayTime(2000);
		Villager trader = trader(helper, 1);
		ServerPlayer player = player(helper);
		PlayerPartyStore party = Cobblemon.INSTANCE.getStorage().getParty(player);
		CobblemonTraders.Offer offer = CobblemonTraders.offers(trader).get(0);
		Pokemon bound = fitting(offer, 0, offer.minLevel() + 5);
		helper.assertTrue(offer.refusal(bound) == null, "the control doesn't fit: " + key(offer.refusal(bound)));
		bound.setTradeable(false);
		party.add(bound);
		helper.assertValueEqual(key(offer.refusal(bound)), "message.aliveworkplace.pokemon_trader.untradeable", "the refusal");
		helper.assertFalse(CobblemonTraders.trade(player, trader, bound, offer), "an untradeable Pokémon was traded");
		helper.assertTrue(contains(party, bound), "the untradeable Pokémon left the party");
		helper.assertFalse(PokemonTraders.tradedToday(trader, player.getUUID()), "the refused trade used up the day");
		helper.assertValueEqual(ModAttachments.POKEMON_TRADE_COUNT.getOrElse(trader, 0), 0, "trades");
		// A fitting Pokémon that isn't in the player's party (a stale screen) is no trade either.
		Pokemon elsewhere = fitting(offer, 1, offer.minLevel() + 5);
		helper.assertFalse(CobblemonTraders.trade(player, trader, elsewhere, offer), "a Pokémon outside the party was traded");
		helper.assertFalse(PokemonTraders.tradedToday(trader, player.getUUID()), "a trade with a Pokémon outside the party used up the day");
		helper.succeed();
	}

	/** With a full party of six the trade still goes through: yours leaves, the trader's takes its place in the party. */
	//$ gametest AREA
	@GameTest(template = AREA)
	public void qaFullPartyStillTrades(GameTestHelper helper) {
		helper.setDayTime(2000);
		Villager trader = trader(helper, 1);
		ServerPlayer player = player(helper);
		PlayerPartyStore party = Cobblemon.INSTANCE.getStorage().getParty(player);
		CobblemonTraders.Offer offer = CobblemonTraders.offers(trader).get(0);
		List<Pokemon> six = new ArrayList<>();
		for (int i = 0; i < 6; i++) {
			Pokemon p = fitting(offer, i, offer.minLevel() + 3);
			six.add(p);
			helper.assertTrue(party.add(p), "couldn't fill the party (" + i + ")");
		}
		helper.assertValueEqual(party.occupied(), 6, "party size before");
		helper.assertTrue(CobblemonTraders.trade(player, trader, six.get(3), offer), "a full party couldn't trade");
		helper.assertFalse(contains(party, six.get(3)), "the traded Pokémon is still in the party");
		helper.assertValueEqual(party.occupied(), 6, "party size after");
		boolean got = false;
		for (Pokemon p : party) {
			got |= p.getSpecies() == offer.species() && p.getLevel() == offer.level();
		}
		helper.assertTrue(got, "the trader's " + offer.species().getName() + " isn't in the party");
		// Like an NPC trade in the games, the Pokémon comes with the trader as its original trainer.
		String name = trader.getDisplayName().getString();
		for (Pokemon p : party) {
			if (p.getSpecies() == offer.species() && p.getLevel() == offer.level()) {
				helper.assertTrue(name.equals(p.getOriginalTrainer()) || name.equals(p.getOriginalTrainerName()),
					"the traded Pokémon's original trainer is " + p.getOriginalTrainer() + " / " + p.getOriginalTrainerName() + ", not " + name);
			}
		}
		helper.succeed();
	}

	/**
	 * One trade per trader per day is per player: a second player trades the same day; the first can't again until the
	 * next day, when he can.
	 */
	//$ gametest_batch AREA '"qaTradeDayPerPlayer1107d"'
	@GameTest(template = AREA, batch = "qaTradeDayPerPlayer1107d")
	public void qaOneTradeADayIsPerPlayer(GameTestHelper helper) {
		PartnerShowsCompatTests.clearLeftovers(helper);
		long base = 24000L * 40 + 2000;
		helper.setDayTime((int) base);
		try {
			Villager trader = trader(helper, 1);
			ServerPlayer a = player(helper);
			ServerPlayer b = player(helper);
			PlayerPartyStore partyA = Cobblemon.INSTANCE.getStorage().getParty(a);
			PlayerPartyStore partyB = Cobblemon.INSTANCE.getStorage().getParty(b);
			CobblemonTraders.Offer offer = CobblemonTraders.offers(trader).get(0);
			Pokemon a1 = fitting(offer, 0, offer.minLevel() + 5);
			Pokemon a2 = fitting(offer, 1, offer.minLevel() + 5);
			Pokemon b1 = fitting(offer, 2, offer.minLevel() + 5);
			partyA.add(a1);
			partyA.add(a2);
			partyB.add(b1);
			helper.assertTrue(CobblemonTraders.trade(a, trader, a1, offer), "the first player's trade");
			helper.assertFalse(CobblemonTraders.trade(a, trader, a2, offer), "the first player traded twice in a day");
			helper.assertTrue(CobblemonTraders.trade(b, trader, b1, offer), "a second player couldn't trade the same day");
			helper.assertValueEqual(ModAttachments.POKEMON_TRADE_COUNT.getOrElse(trader, 0), 2, "trades");

			helper.setDayTime((int) (base + 24000));
			CobblemonTraders.Offer tomorrow = CobblemonTraders.offers(trader).get(0);
			Pokemon a3 = fitting(tomorrow, 3, tomorrow.minLevel() + 5);
			partyA.add(a3);
			helper.assertFalse(PokemonTraders.tradedToday(trader, a.getUUID()), "the first player is still marked as traded the next day");
			helper.assertTrue(CobblemonTraders.trade(a, trader, a3, tomorrow), "the first player couldn't trade the next day");
			helper.assertValueEqual(ModAttachments.POKEMON_TRADE_COUNT.getOrElse(trader, 0), 3, "trades");
		} finally {
			helper.setDayTime(2000);
		}
		helper.succeed();
	}

	/**
	 * Stronger at higher tiers: Novices and Apprentices offer a line's first form, Journeymen an evolved or single-stage
	 * Pokémon (never a first form that still evolves), Experts and Masters only a final form.
	 */
	//$ gametest 'FabricGameTest.EMPTY_STRUCTURE'
	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
	public void qaHigherTiersOfferLaterForms(GameTestHelper helper) {
		int middle = 0;
		int single = 0;
		for (int u = 0; u < 3; u++) {
			UUID id = new UUID(0xF0F0L + u, 0x3L * u);
			for (int tier = 1; tier <= 5; tier++) {
				for (long day = 0; day < 15; day++) {
					for (CobblemonTraders.Offer offer : CobblemonTraders.offers(id, tier, day)) {
						Species s = offer.species();
						boolean basic = s.getPreEvolution() == null;
						boolean finalForm = s.getEvolutions().isEmpty();
						String where = "tier " + tier + " day " + day + " offers " + s.getName() + (basic ? " (first form" : " (evolved") + (finalForm ? ", final)" : ", evolves)");
						if (tier <= 2) {
							helper.assertTrue(basic, where);
						} else if (tier == 3) {
							helper.assertTrue(!basic || finalForm, where);
							middle += !basic && !finalForm ? 1 : 0;
							single += basic && finalForm ? 1 : 0;
						} else {
							helper.assertTrue(finalForm, where);
						}
					}
				}
			}
		}
		// Both kinds of Journeyman offer turn up over 45 days: a middle form (Ivysaur) and a single-stage Pokémon (Tauros).
		helper.assertTrue(middle > 0 && single > 0, "Journeymen offered " + middle + " middle forms and " + single + " single-stage Pokémon");
		helper.succeed();
	}

	/**
	 * "A Master has 3 (a shiny one time in ten)": over 200 days a Master's three regular offers include shinies, about
	 * one in ten; nobody below Master offers a shiny regular offer.
	 */
	//$ gametest 'FabricGameTest.EMPTY_STRUCTURE'
	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
	public void qaOnlyMastersOfferShinies(GameTestHelper helper) {
		UUID id = new UUID(0x5A1EL, 0x1107L);
		int regular = 0;
		int shiny = 0;
		for (long day = 0; day < 200; day++) {
			for (int tier = 1; tier <= 4; tier++) {
				for (CobblemonTraders.Offer offer : CobblemonTraders.offers(id, tier, day)) {
					helper.assertTrue(offer.wantedSpecies() != null || !offer.shiny(), "tier " + tier + " day " + day + " offers a shiny " + offer.species().getName());
				}
			}
			for (CobblemonTraders.Offer offer : CobblemonTraders.offers(id, 5, day)) {
				if (offer.wantedSpecies() == null) {
					regular++;
					shiny += offer.shiny() ? 1 : 0;
				}
			}
		}
		helper.assertValueEqual(regular, 600, "a Master's regular offers over 200 days");
		helper.assertTrue(shiny >= 30 && shiny <= 100, shiny + " of " + regular + " Master offers shiny, not about one in ten");
		helper.succeed();
	}
}
