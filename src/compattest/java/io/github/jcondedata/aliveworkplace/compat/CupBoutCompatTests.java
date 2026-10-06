package io.github.jcondedata.aliveworkplace.compat;

import com.cobblemon.mod.common.CobblemonEntities;
import com.cobblemon.mod.common.api.pokemon.PokemonSpecies;
import com.cobblemon.mod.common.api.types.ElementalType;
import com.cobblemon.mod.common.entity.pokemon.PokemonEntity;
import com.cobblemon.mod.common.pokemon.Pokemon;
import com.google.gson.JsonParser;
import io.github.jcondedata.aliveworkplace.AliveWorkplace;
import io.github.jcondedata.aliveworkplace.compat.cobblemon.CobblemonCupBouts;
import io.github.jcondedata.aliveworkplace.compat.cobblemon.CobblemonTrainers;
import io.github.jcondedata.aliveworkplace.cup.CupBattlers;
import io.github.jcondedata.aliveworkplace.cup.CupBout;
import io.github.jcondedata.aliveworkplace.cup.CupBouts;
import io.github.jcondedata.aliveworkplace.cup.CupData;
import io.github.jcondedata.aliveworkplace.cup.CupThemes;
import io.github.jcondedata.aliveworkplace.registry.ModVillagers;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.phys.AABB;

/**
 * ROADMAP 28.18 with the real Cobblemon: a themed team follows its theme (types, stage, labels, how many, level) and is
 * the same for the same trainer; a bout between two trainers at the ring sends real Pokémon out (uncatchable, still,
 * sent out again when one goes missing), ends with a winner and leaves no Pokémon behind; a Cup Pokémon nobody sent out
 * this run is removed as it loads. The chart, the seed and the XP: {@code CupBoutGameTests}.
 */
public class CupBoutCompatTests implements FabricGameTest {
	private static final String HUGE_AREA = "aliveworkplace_compat:huge_area";

	/** A Fire theme of fully evolved Pokémon, three each at level 50. */
	static CupThemes.Theme fireTheme() {
		return CupThemes.read(AliveWorkplace.id("test_fire_cup"), JsonParser.parseString("""
			{"name": "cup.aliveworkplace.grand_cup", "order": 99, "level": 50, "bring": 3, "types": ["fire"], "stage": "final",
			 "banned": ["legendary", "mythical", "ultra_beast", "paradox"], "dish": "minecraft:bread", "fireworks": ["#FF0000"],
			 "disc": "minecraft:music_disc_cat"}""").getAsJsonObject());
	}

	/** A quick theme: two first-stage Pokémon each at level 20. */
	static CupThemes.Theme quickTheme() {
		return CupThemes.read(AliveWorkplace.id("test_quick_cup"), JsonParser.parseString("""
			{"name": "cup.aliveworkplace.grand_cup", "order": 98, "level": 20, "bring": 2, "stage": "first",
			 "banned": ["legendary", "mythical", "ultra_beast", "paradox"], "dish": "minecraft:bread", "fireworks": ["#FF0000"],
			 "disc": "minecraft:music_disc_cat"}""").getAsJsonObject());
	}

	/** A themed team: Fire, fully evolved, none banned, three, at level 50, the same every time for the same trainer; as fighters too. */
	//$ gametest 'FabricGameTest.EMPTY_STRUCTURE'
	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
	public void aThemedTeamFollowsTheTheme(GameTestHelper helper) {
		CupThemes.Theme theme = fireTheme();
		UUID mira = UUID.nameUUIDFromBytes("mira".getBytes());
		List<Pokemon> team = CobblemonTrainers.team(mira, 4, theme);
		helper.assertTrue(team.size() == 3, "the team has " + team.size());
		for (Pokemon p : team) {
			boolean fire = false;
			for (ElementalType t : p.getTypes()) {
				fire |= t.getName().toLowerCase(Locale.ROOT).equals("fire");
			}
			helper.assertTrue(fire, p.getSpecies().getName() + " isn't Fire");
			helper.assertTrue(p.getSpecies().getEvolutions().isEmpty(), p.getSpecies().getName() + " isn't fully evolved");
			helper.assertTrue(p.getLevel() == 50, p.getSpecies().getName() + " is level " + p.getLevel());
			helper.assertTrue(p.getSpecies().getLabels().stream().noneMatch(theme.banned()::contains), p.getSpecies().getName() + " is banned");
		}
		List<String> again = CobblemonTrainers.team(mira, 4, theme).stream().map(p -> p.getSpecies().getName()).toList();
		helper.assertTrue(again.equals(team.stream().map(p -> p.getSpecies().getName()).toList()), "the same trainer fielded another team: " + again);
		List<CupBout.Fighter> fighters = CupBattlers.EXTENSION.call(b -> b.team(mira, 4, theme), List.of());
		helper.assertTrue(fighters.size() == 3 && fighters.stream().allMatch(f -> f.types().contains("fire") && f.level() == 50 && f.hp() > 0
			&& f.speed() > 0), "as fighters: " + fighters);
		helper.assertTrue(fighters.stream().allMatch(f -> f.moves().stream().allMatch(m -> m.power() > 0)), "a fighter has a status move: " + fighters);
		// the first stage only, any type
		for (Pokemon p : CobblemonTrainers.team(mira, 2, quickTheme())) {
			helper.assertTrue(p.getSpecies().getPreEvolution() == null && p.getLevel() == 20, p.getSpecies().getName() + " in a first-stage Cup");
		}
		helper.succeed();
	}

	/** A bout between two trainers sends real Pokémon out, ends with a winner, and every Pokémon entity is gone afterwards. */
	//$ gametest_ticks_batch HUGE_AREA '2400' '"cupBoutCompat"'
	@GameTest(template = HUGE_AREA, timeoutTicks = 2400, batch = "cupBoutCompat")
	public void aBoutEndsWithAWinnerAndNoPokemonLeft(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		BlockPos host = helper.absolutePos(new BlockPos(15, 2, 26));
		BlockPos ring = helper.absolutePos(new BlockPos(15, 2, 12));
		AABB arena = new AABB(ring).inflate(20, 8, 20);
		CupThemes.Theme theme = quickTheme();
		Villager mira = trainer(helper, new BlockPos(4, 2, 12), "Mira");
		Villager dara = trainer(helper, new BlockPos(26, 2, 12), "Dara");
		CupData.Cup cup = CupData.get(level).cup(host);
		CupData.Entrant a = new CupData.Entrant(CupData.Kind.HOST_TRAINER, mira.getUUID(), "Mira", 3, 0, host);
		CupData.Entrant b = new CupData.Entrant(CupData.Kind.HOST_TRAINER, dara.getUUID(), "Dara", 3, 0, host);
		cup.entrants.addAll(List.of(a, b));
		CupBout bout = new CupBout(a.id(), b.id(), CupBouts.team(a, theme), CupBouts.team(b, theme), 1, CupBout.seed(host, 1, 1, a.id(), b.id()),
			ring, ring.west(10), ring.east(10));
		helper.assertTrue(bout.teams[0].size() == 2 && bout.teams[1].size() == 2, "teams " + bout.teams[0].size() + " and " + bout.teams[1].size());
		CupBouts.begin(level, host, cup, bout);
		Entity[] first = new Entity[1];
		helper.runAfterDelay(20, () -> {
			Entity out = CupBouts.shown(level, host, 0);
			helper.assertTrue(out instanceof PokemonEntity, "no Pokémon out for Mira: " + out);
			PokemonEntity p = (PokemonEntity) out;
			helper.assertTrue(p.getPokemon().isUncatchable() && p.isNoAi() && p.isInvulnerable(), "a Cup Pokémon can be caught, wander or be hurt");
			helper.assertTrue(CupBouts.shown(level, host, 1) instanceof PokemonEntity, "no Pokémon out for Dara");
			first[0] = out;
			out.discard(); // as if its chunk unloaded: it's sent out again
		});
		helper.runAfterDelay(80, () -> {
			if (cup.bout != null) {
				Entity again = CupBouts.shown(level, host, 0);
				helper.assertTrue(again != null && again != first[0] && again.isAlive(), "a missing Pokémon wasn't sent out again: " + again);
			}
		});
		helper.succeedWhen(() -> {
			helper.assertTrue(cup.bout == null, "still fighting: tick " + bout.ticks + ", step " + bout.step);
			helper.assertTrue(cup.results.size() == 1, "results " + cup.results);
			CupData.Result r = cup.results.get(0);
			helper.assertTrue(r.winner().equals(bout.trainers[bout.winner]) && !r.winner().equals(r.loser()), "result " + r);
			List<PokemonEntity> left = level.getEntitiesOfClass(PokemonEntity.class, arena, e -> true);
			helper.assertTrue(left.isEmpty(), left.size() + " Pokémon left at the ring");
		});
		helper.runAtTickTime(2390, () -> CupData.get(level).forget(host));
	}

	/** A Cup Pokémon that no bout running now sent out (left from a server that stopped mid-bout) is removed as it loads. */
	//$ gametest 'FabricGameTest.EMPTY_STRUCTURE'
	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
	public void aLeftoverCupPokemonIsRemovedOnLoad(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		Pokemon pokemon = PokemonSpecies.getByIdentifier(ResourceLocation.fromNamespaceAndPath("cobblemon", "charmander")).create(10);
		pokemon.getPersistentData().putBoolean(CobblemonCupBouts.CUP_POKEMON, true);
		PokemonEntity entity = new PokemonEntity(level, pokemon, CobblemonEntities.POKEMON);
		entity.moveTo(helper.absoluteVec(new net.minecraft.world.phys.Vec3(1, 2, 1)));
		level.addFreshEntity(entity);
		helper.assertTrue(entity.isRemoved(), "a leftover Cup Pokémon stays in the world");
		helper.succeed();
	}

	private static Villager trainer(GameTestHelper helper, BlockPos at, String name) {
		Villager v = helper.spawn(EntityType.VILLAGER, at);
		v.setNoAi(true);
		v.setVillagerData(v.getVillagerData().setProfession(ModVillagers.TRAINER).setLevel(3));
		v.setCustomName(Component.literal(name));
		return v;
	}
}
