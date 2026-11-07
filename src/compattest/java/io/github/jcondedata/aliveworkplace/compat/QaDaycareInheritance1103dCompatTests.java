package io.github.jcondedata.aliveworkplace.compat;

import com.cobblemon.mod.common.Cobblemon;
import com.cobblemon.mod.common.api.Priority;
import com.cobblemon.mod.common.api.abilities.Abilities;
import com.cobblemon.mod.common.api.moves.Move;
import com.cobblemon.mod.common.api.moves.Moves;
import com.cobblemon.mod.common.api.pokemon.PokemonProperties;
import com.cobblemon.mod.common.api.pokemon.stats.Stats;
import com.cobblemon.mod.common.api.storage.party.PlayerPartyStore;
import com.cobblemon.mod.common.pokemon.Pokemon;
import io.github.jcondedata.aliveworkplace.compat.cobblemon.CobblemonDaycareKeeper;
import io.github.jcondedata.aliveworkplace.daycare.DaycareKeepers;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.levelgen.SingleThreadedRandomSource;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;

/**
 * QA (2026-11-03, lane d) of ROADMAP 28.12's hatchling without Cobbreeding, from the spec: "the hatchling itself at
 * level 1, in the base form of the mother (or the parent that isn't Ditto), with 3 IVs from the parents (5 when one
 * holds a Destiny Knot), the nature of a parent holding an Everstone, the mother's ball, a 1 in 5 chance of a hidden
 * ability the mother has, and the egg moves both parents know; to the party or the PC", and the +10% at Expert and
 * Master as the dawn really rolls it. The parents go through a save and load first, as they do at the keeper's.
 */
public class QaDaycareInheritance1103dCompatTests implements FabricGameTest {
	private static final String AREA = CompatGameTests.AREA;
	private static final Stats[] STATS = {Stats.HP, Stats.ATTACK, Stats.DEFENCE, Stats.SPECIAL_ATTACK, Stats.SPECIAL_DEFENCE, Stats.SPEED};

	private static Pokemon make(String properties) {
		return PokemonProperties.Companion.parse(properties, " ", "=").create();
	}

	/** {@code pokemon} saved and loaded again, as the keeper holds a pair. */
	private static Pokemon stored(GameTestHelper helper, Pokemon pokemon) {
		Pokemon loaded = CobblemonDaycareKeeper.load(helper.getLevel(), pokemon.saveToNBT(helper.getLevel().registryAccess(), new CompoundTag()));
		helper.assertTrue(loaded != null, "a parent didn't load");
		return loaded;
	}

	private static String id(Pokemon pokemon) {
		return pokemon.getSpecies().getResourceIdentifier().getPath();
	}

	private static void ivs(Pokemon pokemon, int value) {
		for (Stats stat : STATS) {
			pokemon.getIvs().set(stat, value);
		}
	}

	private static void hold(Pokemon pokemon, String item) {
		pokemon.swapHeldItem(new ItemStack(BuiltInRegistries.ITEM.get(ResourceLocation.fromNamespaceAndPath("cobblemon", item))), false, false);
	}

	private static void teach(Pokemon pokemon, String... moves) {
		for (int i = 0; i < moves.length; i++) {
			pokemon.getMoveSet().setMove(i, Moves.getByName(moves[i]).create());
		}
	}

	private static Set<String> moves(Pokemon pokemon) {
		Set<String> out = new HashSet<>();
		for (Move move : pokemon.getMoveSet().getMoves()) {
			out.add(move.getName());
		}
		return out;
	}

	/** Stats whose IV matches the mother's (31) or the father's (30). */
	private static int fromParents(Pokemon baby) {
		int n = 0;
		for (Stats stat : STATS) {
			int iv = baby.getIvs().getOrDefault(stat);
			n += iv == 30 || iv == 31 ? 1 : 0;
		}
		return n;
	}

	/**
	 * The mean number of parents' IVs over 40 hatchlings (fixed seeds) for {@code mother} and {@code father}. A
	 * hatchling's own random IVs match a parent's 2 times in 32, so the mean sits just above the inherited count.
	 */
	private static double meanInherited(GameTestHelper helper, Pokemon mother, Pokemon father, int atLeast) {
		int total = 0;
		for (int seed = 0; seed < 40; seed++) {
			Pokemon baby = CobblemonDaycareKeeper.hatchling(mother, father, RandomSource.create(seed));
			int n = fromParents(baby);
			helper.assertTrue(n >= atLeast, "seed " + seed + ": only " + n + " IVs from the parents, wanted at least " + atLeast);
			total += n;
		}
		return total / 40.0;
	}

	/** Without a Destiny Knot, exactly 3 IVs come from the parents (never 4 or 5 every time), from either parent. */
	//$ gametest 'FabricGameTest.EMPTY_STRUCTURE'
	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
	public void qaThreeIvsWithoutADestinyKnot(GameTestHelper helper) {
		Pokemon mother = make("eevee gender=female level=30");
		Pokemon father = make("eevee gender=male level=30");
		ivs(mother, 31);
		ivs(father, 30);
		mother = stored(helper, mother);
		father = stored(helper, father);
		double mean = meanInherited(helper, mother, father, 3);
		helper.assertTrue(mean < 3.6, "without a Destiny Knot, " + mean + " IVs from the parents on average (3 wanted)");
		// Both parents pass some: over the 40 hatchlings, both the mother's 31 and the father's 30 appear
		int mothers = 0, fathers = 0;
		for (int seed = 0; seed < 40; seed++) {
			Pokemon baby = CobblemonDaycareKeeper.hatchling(mother, father, RandomSource.create(seed));
			for (Stats stat : STATS) {
				mothers += baby.getIvs().getOrDefault(stat) == 31 ? 1 : 0;
				fathers += baby.getIvs().getOrDefault(stat) == 30 ? 1 : 0;
			}
		}
		helper.assertTrue(mothers > 20 && fathers > 20, "IVs from the mother " + mothers + ", from the father " + fathers + " over 40 hatchlings");
		// Another held item isn't a Destiny Knot: an Everstone on one parent and an Exp. Candy on the other still pass 3
		Pokemon withEverstone = make("eevee gender=male level=30");
		Pokemon withCandy = make("eevee gender=female level=30");
		ivs(withEverstone, 30);
		ivs(withCandy, 31);
		hold(withEverstone, "everstone");
		hold(withCandy, "exp_candy_xs");
		double held = meanInherited(helper, stored(helper, withCandy), stored(helper, withEverstone), 3);
		helper.assertTrue(held < 3.6, "with an Everstone and an Exp. Candy held (no Destiny Knot), " + held + " IVs from the parents on average (3 wanted)");
		helper.succeed();
	}

	/** A Destiny Knot passes exactly 5 IVs, whichever parent holds it: the father, or the mother. */
	//$ gametest 'FabricGameTest.EMPTY_STRUCTURE'
	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
	public void qaDestinyKnotOnEitherParentPassesFive(GameTestHelper helper) {
		for (String holder : List.of("father", "mother")) {
			Pokemon mother = make("eevee gender=female level=30");
			Pokemon father = make("eevee gender=male level=30");
			ivs(mother, 31);
			ivs(father, 30);
			hold(holder.equals("mother") ? mother : father, "destiny_knot");
			mother = stored(helper, mother);
			father = stored(helper, father);
			// The father first, the mother second: the order she was given them in mustn't matter
			double mean = meanInherited(helper, father, mother, 5);
			helper.assertTrue(mean < 5.5, "a Destiny Knot on the " + holder + ": " + mean + " IVs on average (5 wanted)");
		}
		helper.succeed();
	}

	/**
	 * The base form of the MOTHER: a female Ivysaur with a male Charmeleon (both Monster group) hatches a Bulbasaur,
	 * whichever order they are in; a female Charmeleon with a male Ivysaur hatches a Charmander. Level 1 each time.
	 */
	//$ gametest 'FabricGameTest.EMPTY_STRUCTURE'
	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
	public void qaTheMothersBaseFormNotTheFathers(GameTestHelper helper) {
		Pokemon ivysaur = stored(helper, make("ivysaur gender=female level=30"));
		Pokemon charmeleon = stored(helper, make("charmeleon gender=male level=30"));
		helper.assertTrue(CobblemonDaycareKeeper.getAlong(ivysaur, charmeleon) == DaycareKeepers.WELL,
			"Ivysaur and Charmeleon (Monster) get along " + CobblemonDaycareKeeper.getAlong(ivysaur, charmeleon));
		for (Pokemon[] order : List.of(new Pokemon[]{ivysaur, charmeleon}, new Pokemon[]{charmeleon, ivysaur})) {
			Pokemon baby = CobblemonDaycareKeeper.hatchling(order[0], order[1], RandomSource.create(5));
			helper.assertTrue(id(baby).equals("bulbasaur") && baby.getLevel() == 1,
				"a female Ivysaur and a male Charmeleon (" + id(order[0]) + " first) hatched a level " + baby.getLevel() + " " + id(baby));
		}
		Pokemon mother = stored(helper, make("charmeleon gender=female level=30"));
		Pokemon father = stored(helper, make("ivysaur gender=male level=30"));
		Pokemon baby = CobblemonDaycareKeeper.hatchling(father, mother, RandomSource.create(5));
		helper.assertTrue(id(baby).equals("charmander"), "a female Charmeleon and a male Ivysaur hatched a " + id(baby));
		helper.succeed();
	}

	/**
	 * The mother's ball: a father in an Ultra Ball first, a mother in a Love Ball second, gives a Love Ball; with Ditto
	 * (in a Master Ball) and a female in a Dusk Ball, the Dusk Ball.
	 */
	//$ gametest 'FabricGameTest.EMPTY_STRUCTURE'
	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
	public void qaTheMothersBallWhicheverOrder(GameTestHelper helper) {
		Pokemon father = stored(helper, make("eevee gender=male level=20 pokeball=ultra_ball"));
		Pokemon mother = stored(helper, make("eevee gender=female level=20 pokeball=love_ball"));
		for (Pokemon[] order : List.of(new Pokemon[]{father, mother}, new Pokemon[]{mother, father})) {
			Pokemon baby = CobblemonDaycareKeeper.hatchling(order[0], order[1], RandomSource.create(4));
			helper.assertTrue(baby.getCaughtBall() == mother.getCaughtBall(),
				"hatched in a " + baby.getCaughtBall().getName() + ", not the mother's " + mother.getCaughtBall().getName());
		}
		Pokemon ditto = stored(helper, make("ditto level=20 pokeball=master_ball"));
		Pokemon female = stored(helper, make("pikachu gender=female level=20 pokeball=dusk_ball"));
		Pokemon baby = CobblemonDaycareKeeper.hatchling(ditto, female, RandomSource.create(4));
		helper.assertTrue(baby.getCaughtBall() == female.getCaughtBall(),
			"Ditto and a female in a " + female.getCaughtBall().getName() + " hatched in a " + baby.getCaughtBall().getName());
		helper.succeed();
	}

	/**
	 * Egg moves both parents know: Curse (a Bulbasaur egg move) known by both is on the hatchling; Toxic (an egg move)
	 * known only by the father and Ingrain known only by the mother are not; Swords Dance (no egg move) known by both
	 * is not either.
	 */
	//$ gametest 'FabricGameTest.EMPTY_STRUCTURE'
	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
	public void qaOnlyEggMovesBothParentsKnow(GameTestHelper helper) {
		Pokemon mother = make("ivysaur gender=female level=30");
		Pokemon father = make("charmeleon gender=male level=30");
		teach(mother, "curse", "ingrain", "swordsdance", "tackle");
		teach(father, "curse", "toxic", "swordsdance", "scratch");
		mother = stored(helper, mother);
		father = stored(helper, father);
		helper.assertTrue(moves(mother).containsAll(List.of("curse", "ingrain")) && moves(father).containsAll(List.of("curse", "toxic")),
			"the parents' moves didn't stick: " + moves(mother) + " / " + moves(father));
		for (int seed = 0; seed < 5; seed++) {
			Pokemon baby = CobblemonDaycareKeeper.hatchling(father, mother, RandomSource.create(seed));
			Set<String> got = moves(baby);
			helper.assertTrue(got.contains("curse"), "seed " + seed + ": the egg move both know (curse) is missing: " + got);
			helper.assertFalse(got.contains("toxic"), "seed " + seed + ": the father's own egg move (toxic) was passed: " + got);
			helper.assertFalse(got.contains("ingrain"), "seed " + seed + ": the mother's own egg move (ingrain) was passed: " + got);
			helper.assertFalse(got.contains("swordsdance"), "seed " + seed + ": a move that isn't an egg move was passed: " + got);
			helper.assertTrue(got.contains("tackle") && got.contains("growl"), "seed " + seed + ": the hatchling lost its own level-1 moves: " + got);
		}
		helper.succeed();
	}

	/**
	 * Hidden ability: a mother without hers (Eevee with Run Away) never passes Anticipation over 200 fixed seeds; a mother
	 * with it does about 1 time in 5 (between 10% and 32% of 200), and a hatchling without it has a normal ability.
	 */
	//$ gametest 'FabricGameTest.EMPTY_STRUCTURE'
	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
	public void qaHiddenAbilityOnlyFromAMotherWithIt(GameTestHelper helper) {
		Pokemon father = make("eevee gender=male level=30");
		father.updateAbility(Abilities.get("anticipation").create(false, Priority.LOW));
		father = stored(helper, father);
		Pokemon plain = stored(helper, make("eevee gender=female level=30 ability=runaway"));
		int hidden = 0;
		for (int seed = 0; seed < 200; seed++) {
			hidden += CobblemonDaycareKeeper.hatchling(father, plain, RandomSource.create(seed)).getAbility().getName().equals("anticipation") ? 1 : 0;
		}
		helper.assertTrue(hidden == 0, "a mother without her hidden ability (only the father has it) passed it " + hidden + " times in 200");
		Pokemon mother = make("eevee gender=female level=30");
		mother.updateAbility(Abilities.get("anticipation").create(false, Priority.LOW));
		mother = stored(helper, mother);
		helper.assertTrue(mother.getAbility().getName().equals("anticipation"), "the mother lost Anticipation in a save: " + mother.getAbility().getName());
		hidden = 0;
		for (int seed = 0; seed < 200; seed++) {
			Pokemon baby = CobblemonDaycareKeeper.hatchling(mother, make("eevee gender=male level=30"), RandomSource.create(seed));
			String ability = baby.getAbility().getName();
			hidden += ability.equals("anticipation") ? 1 : 0;
			helper.assertTrue(ability.equals("anticipation") || ability.equals("runaway") || ability.equals("adaptability"),
				"seed " + seed + ": a hatchling with " + ability);
		}
		helper.assertTrue(hidden >= 20 && hidden <= 64, "a mother with her hidden ability passed it " + hidden + " times in 200 (about 40 wanted)");
		helper.succeed();
	}

	/** Without Cobbreeding, a full party sends the hatchling to the PC: the party stays at six, the PC gains a level-1 Eevee. */
	//$ gametest AREA
	@GameTest(template = AREA)
	public void qaAFullPartySendsTheHatchlingToThePc(GameTestHelper helper) {
		ServerPlayer player = helper.makeMockServerPlayerInLevel();
		player.setGameMode(GameType.SURVIVAL);
		PlayerPartyStore party = Cobblemon.INSTANCE.getStorage().getParty(player);
		for (int i = 0; i < 6; i++) {
			party.add(make("pikachu level=10"));
		}
		int pcBefore = 0;
		for (Pokemon p : Cobblemon.INSTANCE.getStorage().getPC(player)) {
			pcBefore++;
		}
		Pokemon mother = stored(helper, make("eevee gender=female level=20"));
		Pokemon father = stored(helper, make("eevee gender=male level=20"));
		Component said = CobblemonDaycareKeeper.giveEgg(player, mother, father, RandomSource.create(1), false);
		helper.assertTrue(said != null && said.getString().equals("Eevee, just hatched"), "it says: " + (said == null ? null : said.getString()));
		int partySize = 0;
		for (Pokemon p : party) {
			partySize++;
			helper.assertTrue(p.getLevel() != 1, "a hatchling squeezed into a full party");
		}
		helper.assertTrue(partySize == 6, "the party has " + partySize);
		List<Pokemon> hatched = new ArrayList<>();
		int pcAfter = 0;
		for (Pokemon p : Cobblemon.INSTANCE.getStorage().getPC(player)) {
			pcAfter++;
			if (p.getLevel() == 1 && id(p).equals("eevee")) {
				hatched.add(p);
			}
		}
		helper.assertTrue(pcAfter == pcBefore + 1 && hatched.size() == 1, "the PC went from " + pcBefore + " to " + pcAfter + ", hatchlings in it: " + hatched.size());
		// With room in the party, the next one goes there instead
		party.remove(party.iterator().next());
		CobblemonDaycareKeeper.giveEgg(player, mother, father, RandomSource.create(2), false);
		int inParty = 0;
		for (Pokemon p : party) {
			inParty += p.getLevel() == 1 ? 1 : 0;
		}
		helper.assertTrue(inParty == 1, "with room, " + inParty + " hatchlings in the party");
		helper.succeed();
	}

	/** A fixed roll: every nextInt(100) gives {@code value}. */
	private static RandomSource rolling(int value) {
		return new SingleThreadedRandomSource(1) {
			@Override
			public int nextInt(int bound) {
				return value;
			}
		};
	}

	/**
	 * The dawn itself rolls the levelled odds: a pair that gets along well (50%, 60% at Expert and Master) finds an egg on
	 * a roll of 55 with an Expert or a Master keeper, never with a Novice to Journeyman; a roll of 59 still hits at Master,
	 * 60 never does.
	 */
	//$ gametest AREA
	@GameTest(template = AREA)
	public void qaTheDawnRollsTheExpertBonus(GameTestHelper helper) {
		Villager keeper = DaycareKeeperCompatTests.keeper(helper);
		Pokemon mother = make("eevee gender=female level=20");
		Pokemon father = make("eevee gender=male level=20");
		ServerPlayer player = helper.makeMockServerPlayerInLevel();
		PlayerPartyStore party = Cobblemon.INSTANCE.getStorage().getParty(player);
		party.add(make("bulbasaur level=10"));
		party.add(mother);
		party.add(father);
		helper.assertTrue(CobblemonDaycareKeeper.leave(player, keeper, mother, father), "the pair wasn't left");
		int along = DaycareKeepers.pairs(keeper).isEmpty() ? -1 : new CobblemonDaycareKeeper().getAlong(helper.getLevel(), DaycareKeepers.pairs(keeper).get(0));
		helper.assertTrue(along == DaycareKeepers.WELL, "the stored pair gets along " + along);
		int[][] cases = {{1, 55, 0}, {2, 55, 0}, {3, 55, 0}, {4, 55, 1}, {5, 55, 1}, {5, 59, 1}, {5, 60, 0}, {3, 49, 1}, {3, 50, 0}};
		for (int[] c : cases) {
			keeper.setVillagerData(keeper.getVillagerData().setLevel(c[0]));
			DaycareKeepers.Pair pair = DaycareKeepers.pairs(keeper).get(0);
			DaycareKeepers.setPairs(keeper, List.of(pair.withEggs(0, DaycareKeepers.day(helper.getLevel()) - 1)));
			DaycareKeepers.dawn(helper.getLevel(), keeper, rolling(c[1]));
			int eggs = DaycareKeepers.pairs(keeper).get(0).eggs();
			helper.assertTrue(eggs == c[2], "level " + c[0] + ", roll " + c[1] + ": " + eggs + " eggs (wanted " + c[2] + ")");
		}
		helper.succeed();
	}
}
