package io.github.jcondedata.aliveworkplace.compat;

import com.cobblemon.mod.common.Cobblemon;
import com.cobblemon.mod.common.api.pokemon.PokemonProperties;
import com.cobblemon.mod.common.api.storage.party.PlayerPartyStore;
import com.cobblemon.mod.common.api.types.ElementalType;
import com.cobblemon.mod.common.entity.pokemon.PokemonEntity;
import com.cobblemon.mod.common.pokemon.Pokemon;
import io.github.jcondedata.aliveworkplace.AliveWorkplace;
import io.github.jcondedata.aliveworkplace.compat.cobblemon.CobblemonCupMatches;
import io.github.jcondedata.aliveworkplace.compat.cobblemon.CobblemonPartners;
import io.github.jcondedata.aliveworkplace.compat.cobblemon.CobblemonTrainers;
import io.github.jcondedata.aliveworkplace.cup.CupDays;
import io.github.jcondedata.aliveworkplace.cup.CupPage;
import io.github.jcondedata.aliveworkplace.cup.CupThemes;
import io.github.jcondedata.aliveworkplace.hall.Chronicle;
import io.github.jcondedata.aliveworkplace.registry.ModVillagers;
import io.github.jcondedata.aliveworkplace.work.Partners;
import io.github.jcondedata.aliveworkplace.work.PokemonPartners;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.schedule.Activity;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.trading.MerchantOffer;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;

/**
 * ROADMAP 28.22 with the real Cobblemon: the eight Cup themes as shipped. For each, a delegate's team follows it (as many
 * as it brings, at its level, its types, stage and bans), the player filter takes and refuses the right Pokémon (and
 * says why), the fair sells its wares at 4 to 16 emeralds; and the Workers' Cup's counter grows by one for a day of
 * helping a villager at work and never twice a day.
 */
public class CupThemesCompatTests implements FabricGameTest {
	private static final String AREA = CompatGameTests.AREA;

	private static CupThemes.Theme theme(GameTestHelper helper, String name, String title) {
		CupThemes.Theme theme = CupThemes.get(AliveWorkplace.id(name));
		helper.assertTrue(theme != null, name + " isn't loaded: " + CupThemes.loaded().keySet());
		helper.assertTrue(Component.translatable(theme.name()).getString().equals(title), name + "'s name: " + Component.translatable(theme.name()).getString());
		return theme;
	}

	private static Set<String> types(Pokemon p) {
		Set<String> out = new java.util.HashSet<>();
		for (ElementalType t : p.getSpecies().getStandardForm().getTypes()) {
			out.add(t.getName().toLowerCase(Locale.ROOT));
		}
		return out;
	}

	/** A delegate's (and a host trainer's) team for the theme: as many as it brings, at its level, within its rules; the same every time. */
	private static void team(GameTestHelper helper, CupThemes.Theme theme) {
		for (String who : List.of("mira", "dara")) {
			UUID leader = UUID.nameUUIDFromBytes(who.getBytes());
			List<Pokemon> team = CobblemonTrainers.team(leader, 4, theme);
			helper.assertTrue(team.size() == theme.bring(), theme.id() + ": " + who + " brings " + team.size());
			Set<String> allowed = theme.trainerTypes();
			for (Pokemon p : team) {
				String n = theme.id().getPath() + ": " + p.getSpecies().getName();
				helper.assertTrue(p.getLevel() == theme.level(), n + " is level " + p.getLevel());
				helper.assertTrue(allowed.isEmpty() || types(p).stream().anyMatch(allowed::contains), n + " isn't one of " + allowed);
				helper.assertTrue(p.getSpecies().getLabels().stream().noneMatch(theme.banned()::contains), n + " is banned");
				if (theme.stage().equals("first")) {
					helper.assertTrue(p.getSpecies().getPreEvolution() == null && !p.getSpecies().getEvolutions().isEmpty(), n + " isn't a first stage that can evolve");
				}
			}
			List<String> again = CobblemonTrainers.team(leader, 4, theme).stream().map(p -> p.getSpecies().getName()).toList();
			helper.assertTrue(again.equals(team.stream().map(p -> p.getSpecies().getName()).toList()), theme.id() + ": another team the second time");
		}
	}

	/** The player filter: {@code takes} go (in order), {@code refuses} stay home, each with a reason. */
	private static List<Pokemon> filter(GameTestHelper helper, CupThemes.Theme theme, List<String> takes, List<String> refuses) {
		return filter(helper, theme, takes, refuses, List.of());
	}

	/** As above, then {@code over}: eligible ones after the theme's count is reached, who stay home too. */
	private static List<Pokemon> filter(GameTestHelper helper, CupThemes.Theme theme, List<String> takes, List<String> refuses, List<String> over) {
		ServerPlayer player = helper.makeMockServerPlayerInLevel();
		player.setGameMode(GameType.SURVIVAL);
		PlayerPartyStore party = Cobblemon.INSTANCE.getStorage().getParty(player);
		for (String spec : refuses) {
			party.add(PokemonProperties.Companion.parse(spec + " level=30", " ", "=").create());
		}
		for (String spec : takes) {
			party.add(PokemonProperties.Companion.parse(spec + " level=30", " ", "=").create());
		}
		for (String spec : over) {
			party.add(PokemonProperties.Companion.parse(spec + " level=30", " ", "=").create());
		}
		List<String> left = new ArrayList<>(refuses);
		left.addAll(over);
		return check(helper, theme, party, takes, left);
	}

	private static List<Pokemon> check(GameTestHelper helper, CupThemes.Theme theme, PlayerPartyStore party, List<String> takes, List<String> refuses) {
		List<Component> leftOut = new ArrayList<>();
		List<Pokemon> going = CobblemonCupMatches.eligible(party, theme, leftOut);
		List<String> names = going.stream().map(p -> p.getSpecies().getName().toLowerCase(Locale.ROOT)).toList();
		List<String> expected = takes.stream().map(s -> s.split(" ")[0]).toList();
		helper.assertTrue(names.equals(expected), theme.id().getPath() + " takes " + names + ", expected " + expected);
		helper.assertTrue(leftOut.size() == refuses.size(), theme.id().getPath() + " left out " + leftOut.stream().map(Component::getString).toList());
		return going;
	}

	/** The fair: each of the theme's wares for its price in emeralds (4 to 16), every item real with Cobblemon. */
	private static void fair(GameTestHelper helper, CupThemes.Theme theme, int count) {
		List<MerchantOffer> offers = CupDays.offers(theme);
		helper.assertTrue(theme.wares().size() == count && offers.size() == count, theme.id().getPath() + ": " + offers.size() + " of " + theme.wares().size() + " wares sold");
		for (int i = 0; i < count; i++) {
			CupThemes.Ware w = theme.wares().get(i);
			MerchantOffer o = offers.get(i);
			helper.assertTrue(BuiltInRegistries.ITEM.getKey(o.getResult().getItem()).equals(w.item()) && o.getCostA().is(Items.EMERALD)
				&& o.getCostA().getCount() == w.price() && w.price() >= 4 && w.price() <= 16, theme.id().getPath() + ": " + w + " sold as " + o.getResult());
		}
		helper.assertTrue(BuiltInRegistries.ITEM.containsKey(theme.dish()), theme.id().getPath() + "'s dish " + theme.dish() + " isn't an item");
		helper.assertTrue(BuiltInRegistries.ITEM.getOptional(theme.disc()).map(i -> new net.minecraft.world.item.ItemStack(i)
			.has(net.minecraft.core.component.DataComponents.JUKEBOX_PLAYABLE)).orElse(false), theme.id().getPath() + "'s disc " + theme.disc());
		helper.assertTrue(theme.fireworks().size() == 2, theme.id().getPath() + "'s fireworks " + theme.fireworks());
	}

	private static boolean rule(CupThemes.Theme theme, String line) {
		return CupPage.rules(theme).stream().anyMatch(c -> c.getString().equals(line));
	}

	/** Grass, Bug and Fairy; singles; level 50; bring 3. */
	//$ gametest_batch 'FabricGameTest.EMPTY_STRUCTURE' '"cupThemesCompat"'
	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE, batch = "cupThemesCompat")
	public void theBlossomCup(GameTestHelper helper) {
		CupThemes.Theme t = theme(helper, "blossom_cup", "Blossom Cup");
		helper.assertTrue(t.order() == 1 && !t.doubles() && t.level() == 50 && t.bring() == 3 && t.types().equals(List.of("grass", "bug", "fairy"))
			&& t.disc().getPath().equals("music_disc_chirp") && t.dish().getPath().equals("flower_sweet"), "rules " + t);
		team(helper, t);
		filter(helper, t, List.of("bulbasaur", "caterpie", "clefairy"), List.of("charmander", "celebi"));
		fair(helper, t, 5);
		helper.succeed();
	}

	/** First-stage Pokémon that can still evolve; singles; level 5; bring 3. */
	//$ gametest_batch 'FabricGameTest.EMPTY_STRUCTURE' '"cupThemesCompat"'
	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE, batch = "cupThemesCompat")
	public void theLittleCup(GameTestHelper helper) {
		CupThemes.Theme t = theme(helper, "little_cup", "Little Cup");
		helper.assertTrue(t.order() == 2 && !t.doubles() && t.level() == 5 && t.bring() == 3 && t.types().isEmpty() && t.stage().equals("first")
			&& t.disc().getPath().equals("music_disc_cat") && t.dish().getPath().equals("casteliacone"), "rules " + t);
		helper.assertTrue(rule(t, "Only first-stage Pokémon that can still evolve"), "the Cup page's rules " + CupPage.rules(t).stream().map(Component::getString).toList());
		team(helper, t);
		// Charmeleon has evolved; Tauros never evolves; the fourth that could go stays home (three each)
		filter(helper, t, List.of("charmander", "pichu", "magikarp"), List.of("charmeleon", "tauros"), List.of("squirtle"));
		fair(helper, t, 5);
		helper.succeed();
	}

	/** Fire, Water and Electric; doubles; level 50; bring 4. */
	//$ gametest_batch 'FabricGameTest.EMPTY_STRUCTURE' '"cupThemesCompat"'
	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE, batch = "cupThemesCompat")
	public void theSunCup(GameTestHelper helper) {
		CupThemes.Theme t = theme(helper, "sun_cup", "Sun Cup");
		helper.assertTrue(t.order() == 3 && t.doubles() && t.level() == 50 && t.bring() == 4 && t.types().equals(List.of("fire", "water", "electric"))
			&& t.disc().getPath().equals("music_disc_blocks") && t.dish().getPath().equals("lava_cookie"), "rules " + t);
		helper.assertTrue(CobblemonCupMatches.format(t).getBattleType().getActorsPerSide() == 1
			&& CobblemonCupMatches.format(t).getBattleType().getSlotsPerActor() == 2, "not a double battle");
		team(helper, t);
		filter(helper, t, List.of("charmander", "squirtle", "pikachu", "growlithe"), List.of("bulbasaur", "zapdos"));
		fair(helper, t, 6);
		helper.succeed();
	}

	/** Only Pokémon that have helped a villager at work on 3 days or more; any type; villager trainers field the jobs' partners' types. */
	//$ gametest_batch 'FabricGameTest.EMPTY_STRUCTURE' '"cupThemesCompat"'
	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE, batch = "cupThemesCompat")
	public void theWorkersCup(GameTestHelper helper) {
		CupThemes.Theme t = theme(helper, "workers_cup", "Workers' Cup");
		helper.assertTrue(t.order() == 4 && !t.doubles() && t.level() == 50 && t.bring() == 3 && t.types().isEmpty() && t.partnerDays() == 3 && t.workerTypes()
			&& t.disc().getPath().equals("music_disc_mall") && t.dish().getPath().equals("pewter_crunchies"), "rules " + t);
		helper.assertTrue(t.trainerTypes().equals(Partners.allTypes()) && t.trainerTypes().contains("fighting") && !t.trainerTypes().contains("ghost"),
			"villager trainers' types " + t.trainerTypes());
		helper.assertTrue(rule(t, "Only Pokémon that have helped a villager at work on 3 days or more")
			&& rule(t, "Village trainers bring Pokémon of the types that help at work"), "the Cup page's rules " + CupPage.rules(t).stream().map(Component::getString).toList());
		team(helper, t);
		ServerPlayer player = helper.makeMockServerPlayerInLevel();
		PlayerPartyStore party = Cobblemon.INSTANCE.getStorage().getParty(player);
		int[] days = {2, 0, 3, 5};
		String[] species = {"machop", "gastly", "geodude", "onix"};
		for (int i = 0; i < days.length; i++) {
			Pokemon p = PokemonProperties.Companion.parse(species[i] + " level=30", " ", "=").create();
			for (int d = 1; d <= days[i]; d++) {
				CobblemonPartners.countDay(p, d);
			}
			party.add(p);
		}
		// any type goes (a Ghost would too); only the days helping count
		check(helper, t, party, List.of("geodude", "onix"), List.of("machop", "gastly"));
		List<Component> leftOut = new ArrayList<>();
		CobblemonCupMatches.eligible(party, t, leftOut);
		helper.assertTrue(leftOut.get(0).getString().contains("it has helped a villager at work on 2 days, and this Cup asks for 3"), "why: " + leftOut.get(0).getString());
		fair(helper, t, 5);
		helper.succeed();
	}

	/** Ground, Rock and Normal; singles; level 50; bring 3. */
	//$ gametest_batch 'FabricGameTest.EMPTY_STRUCTURE' '"cupThemesCompat"'
	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE, batch = "cupThemesCompat")
	public void theHarvestCup(GameTestHelper helper) {
		CupThemes.Theme t = theme(helper, "harvest_cup", "Harvest Cup");
		helper.assertTrue(t.order() == 5 && !t.doubles() && t.level() == 50 && t.bring() == 3 && t.types().equals(List.of("ground", "rock", "normal"))
			&& t.disc().getPath().equals("music_disc_far") && t.dish().getPath().equals("leek_and_potato_stew"), "rules " + t);
		team(helper, t);
		filter(helper, t, List.of("geodude", "rattata", "diglett"), List.of("squirtle", "regirock"));
		fair(helper, t, 5);
		helper.succeed();
	}

	/** Ghost, Dark and Psychic; singles; level 50; bring 3; from dusk until dawn. */
	//$ gametest_batch 'FabricGameTest.EMPTY_STRUCTURE' '"cupThemesCompat"'
	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE, batch = "cupThemesCompat")
	public void theLanternCup(GameTestHelper helper) {
		CupThemes.Theme t = theme(helper, "lantern_cup", "Lantern Cup");
		helper.assertTrue(t.order() == 6 && !t.doubles() && t.level() == 50 && t.bring() == 3 && t.types().equals(List.of("ghost", "dark", "psychic"))
			&& t.disc().getPath().equals("music_disc_13") && t.dish().getPath().equals("sinister_tea"), "rules " + t);
		helper.assertTrue(t.start() == 12000 && t.end() == 24000, "hours " + t.start() + "-" + t.end());
		helper.assertTrue(rule(t, "Bouts from dusk until dawn"), "the Cup page's rules " + CupPage.rules(t).stream().map(Component::getString).toList());
		team(helper, t);
		filter(helper, t, List.of("gastly", "abra", "umbreon"), List.of("pikachu", "mewtwo"));
		fair(helper, t, 5);
		helper.succeed();
	}

	/** Ice, Steel and Dragon; singles; level 50; bring 3. */
	//$ gametest_batch 'FabricGameTest.EMPTY_STRUCTURE' '"cupThemesCompat"'
	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE, batch = "cupThemesCompat")
	public void theFrostCup(GameTestHelper helper) {
		CupThemes.Theme t = theme(helper, "frost_cup", "Frost Cup");
		helper.assertTrue(t.order() == 7 && !t.doubles() && t.level() == 50 && t.bring() == 3 && t.types().equals(List.of("ice", "steel", "dragon"))
			&& t.disc().getPath().equals("music_disc_strad") && t.dish().getPath().equals("smoked_tail_curry"), "rules " + t);
		team(helper, t);
		filter(helper, t, List.of("snorunt", "magnemite", "dratini"), List.of("rattata", "kyurem"));
		fair(helper, t, 5);
		helper.succeed();
	}

	/** Any Pokémon a village trainer may use; singles; level 100; bring 6; Species and Item Clause. */
	//$ gametest_batch 'FabricGameTest.EMPTY_STRUCTURE' '"cupThemesCompat"'
	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE, batch = "cupThemesCompat")
	public void theGrandCup(GameTestHelper helper) {
		CupThemes.Theme t = theme(helper, "grand_cup", "Grand Cup");
		helper.assertTrue(t.order() == 8 && !t.doubles() && t.level() == 100 && t.bring() == 6 && t.types().isEmpty()
			&& t.rules().equals(List.of("Species Clause", "Item Clause")) && t.disc().getPath().equals("music_disc_creator"), "rules " + t);
		team(helper, t);
		filter(helper, t, List.of("charizard", "gastly", "tauros", "dratini"), List.of("mew", "mewtwo"));
		fair(helper, t, 5);
		// the themes come in the spec's order, the Grand Cup last (by each file's order: another test may have put its own themes in)
		List<String> order = CupThemes.all().stream().filter(x -> x.id().getNamespace().equals(AliveWorkplace.MOD_ID) && !x.id().getPath().startsWith("test_"))
			.sorted(java.util.Comparator.comparingInt(CupThemes.Theme::order)).map(x -> x.id().getPath()).toList();
		helper.assertTrue(order.equals(List.of("blossom_cup", "little_cup", "sun_cup", "workers_cup", "harvest_cup", "lantern_cup", "frost_cup", "grand_cup")), "order " + order);
		helper.succeed();
	}

	/**
	 * The Workers' Cup's counter: a pastured partner helping a builder at work gets one day; a second look the same day
	 * (the same worker, or another) adds nothing; the next day adds one; a worker not at work counts nothing; the
	 * counter is in the Pokémon's own saved data.
	 */
	//$ gametest_ticks_batch AREA '200' '"cupWorkersCounter"'
	@GameTest(template = AREA, timeoutTicks = 200, batch = "cupWorkersCounter")
	public void theWorkersCounterGrowsOnceADay(GameTestHelper helper) {
		ServerPlayer owner = helper.makeMockServerPlayerInLevel();
		BlockPos by = helper.absolutePos(new BlockPos(1, 2, 8));
		owner.teleportTo(by.getX() + 0.5, by.getY(), by.getZ() + 0.5);
		BlockPos pasture = PastureCompatTests.pasture(helper, new BlockPos(6, 2, 6));
		PastureCompatTests.pastured(helper, pasture, owner, "machop", Direction.NORTH);
		BlockPos bench = new BlockPos(9, 2, 6);
		helper.setBlock(bench, Blocks.CRAFTING_TABLE);
		Villager first = worker(helper, new BlockPos(9, 2, 8), bench);
		Villager second = worker(helper, new BlockPos(10, 2, 8), bench);
		ServerLevel level = helper.getLevel();
		helper.runAfterDelay(10, () -> {
			PokemonEntity machop = PartnersAtWorkCompatTests.pokemon(helper, "machop");
			Pokemon p = machop.getPokemon();
			long today = Chronicle.day(level);
			Partners.forgetCounted();
			// not at work: nothing counted
			first.getBrain().setActiveActivityIfPossible(Activity.IDLE);
			Partners.countDay(first);
			helper.assertTrue(CobblemonPartners.partnerDays(p) == 0, "counted while the worker was idle: " + CobblemonPartners.partnerDays(p));
			first.getBrain().setActiveActivityIfPossible(Activity.WORK);
			helper.assertTrue(first.getBrain().isActive(Activity.WORK), "the worker isn't at work");
			Partners.factor(first); // the pace of a job step, as work asks for it
			helper.assertTrue(CobblemonPartners.partnerDays(p) == 1, "a day of helping: " + CobblemonPartners.partnerDays(p));
			Partners.factor(first);
			Partners.forgetCounted();
			Partners.countDay(first);
			second.getBrain().setActiveActivityIfPossible(Activity.WORK);
			Partners.countDay(second);
			helper.assertTrue(CobblemonPartners.partnerDays(p) == 1, "counted twice the same day: " + CobblemonPartners.partnerDays(p));
			// the next day
			int helped = PokemonPartners.EXTENSION.call(x -> x.countPartnerDay(level, helper.absolutePos(bench), Partners.RADIUS, Set.of("fighting"), 3, today + 1), 0);
			helper.assertTrue(helped == 1 && CobblemonPartners.partnerDays(p) == 2, "the next day: " + helped + " helped, " + CobblemonPartners.partnerDays(p) + " days");
			helper.assertTrue(PokemonPartners.EXTENSION.call(x -> x.partnerDays(machop), 0) == 2, "the days through the extension");
			// kept with the Pokémon by Cobblemon
			CompoundTag saved = p.saveToNBT(level.registryAccess(), new CompoundTag());
			Pokemon back = Pokemon.Companion.loadFromNBT(level.registryAccess(), saved);
			helper.assertTrue(CobblemonPartners.partnerDays(back) == 2, "after a save and load: " + CobblemonPartners.partnerDays(back));
			level.getServer().getPlayerList().remove(owner);
			helper.succeed();
		});
	}

	private static Villager worker(GameTestHelper helper, BlockPos at, BlockPos bench) {
		Villager v = helper.spawn(EntityType.VILLAGER, at);
		v.setNoAi(true);
		v.setVillagerData(v.getVillagerData().setProfession(ModVillagers.BUILDER).setLevel(2));
		v.getBrain().setMemory(MemoryModuleType.JOB_SITE, GlobalPos.of(helper.getLevel().dimension(), helper.absolutePos(bench)));
		return v;
	}
}
