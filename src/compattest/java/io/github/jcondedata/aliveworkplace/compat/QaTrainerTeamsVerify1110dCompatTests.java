package io.github.jcondedata.aliveworkplace.compat;

import static io.github.jcondedata.aliveworkplace.compat.QaPokemonScreens1109dCompatTests.openTrainer;
import static io.github.jcondedata.aliveworkplace.compat.QaPokemonScreens1109dCompatTests.press;

import com.cobblemon.mod.common.api.battles.model.actor.BattleActor;
import com.cobblemon.mod.common.api.moves.Move;
import com.cobblemon.mod.common.battles.BattleRegistry;
import com.cobblemon.mod.common.battles.pokemon.BattlePokemon;
import com.cobblemon.mod.common.item.PokemonItem;
import com.cobblemon.mod.common.pokemon.Gender;
import com.cobblemon.mod.common.pokemon.Pokemon;
import io.github.jcondedata.aliveworkplace.compat.QaPokemonScreens1109dCompatTests.Fight;
import io.github.jcondedata.aliveworkplace.compat.cobblemon.CobblemonTrainers;
import io.github.jcondedata.aliveworkplace.registry.ModBlocks;
import io.github.jcondedata.aliveworkplace.registry.ModVillagers;
import io.github.jcondedata.aliveworkplace.trainer.TrainerMenu;
import io.github.jcondedata.aliveworkplace.trainer.TrainerView;
import io.github.jcondedata.aliveworkplace.trainer.Trainers;
import io.github.jcondedata.aliveworkplace.work.Jobs;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.UUID;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.item.ItemStack;

/**
 * QA (2026-11-10, lane d): an independent check of B1104 and B1122 (commit 09f9646a4). A villager trainer's Cobblemon
 * team must be the same every time it is built: species, gender, shininess, form, held item and moves (the Trainer
 * screen's models and the battle), for every trainer, at every rank, across a save and reload, while different trainers
 * still get different teams.
 */
public class QaTrainerTeamsVerify1110dCompatTests implements FabricGameTest {
	private static final String AREA = CompatGameTests.AREA;

	/** What a player sees of a team member on the screen and in battle: species, form, look, held item, moves, level. */
	static String look(Pokemon p) {
		List<String> moves = new ArrayList<>();
		for (Move move : p.getMoveSet().getMoves()) {
			moves.add(move.getName());
		}
		return p.getSpecies().getName() + "/" + p.getForm().getName() + new TreeSet<>(p.getAspects()) + " " + p.getGender()
			+ (p.getShiny() ? " shiny" : "") + " L" + p.getLevel() + " @" + p.heldItem().getItem() + " " + moves;
	}

	static List<String> looks(List<Pokemon> team) {
		return team.stream().map(QaTrainerTeamsVerify1110dCompatTests::look).toList();
	}

	/** Fixed trainers, so every run checks the same teams. */
	static UUID id(int n) {
		return new UUID(0x51A7E5L * (n + 1) ^ 0x1110D000L, 0x7EA3L * (n + 7) ^ 0x8000000000000000L);
	}

	/**
	 * As {@link QaPokemonScreens1109dCompatTests#trainer}, but the Trainer has a fixed UUID (so a run always meets the
	 * same team) and the given rank: a Trainer at its Training Post by day, and a player with a level 30 Charmander.
	 */
	static Fight fixedTrainer(GameTestHelper helper, UUID uuid, int rank, String name) {
		PartnerShowsCompatTests.clearLeftovers(helper);
		PartnerShowsCompatTests.clearHalls(helper);
		helper.setDayTime(2000);
		List<ServerPlayer> players = new ArrayList<>();
		PartnerShowsCompatTests.after(helper, () -> players.forEach(p -> QaPokemonScreens1109dCompatTests.finish(p, false)));
		BlockPos post = new BlockPos(2, 2, 2);
		helper.setBlock(post, ModBlocks.TRAINING_POST);
		Villager trainer = new Villager(EntityType.VILLAGER, helper.getLevel());
		trainer.setUUID(uuid);
		BlockPos at = helper.absolutePos(post.east());
		trainer.moveTo(at.getX() + 0.5, at.getY(), at.getZ() + 0.5, 0f, 0f);
		trainer.setNoAi(true);
		helper.getLevel().addFreshEntity(trainer);
		Jobs.employ(helper.getLevel(), trainer, helper.absolutePos(post), ModVillagers.TRAINING_POST_POI, ModVillagers.TRAINER);
		trainer.setVillagerData(trainer.getVillagerData().setLevel(rank));
		List<String> seen = new ArrayList<>();
		ServerPlayer player = QaPokemonScreens1109dCompatTests.joiner(helper, players, seen, name);
		QaPokemonScreens1109dCompatTests.party(player, "charmander level=30");
		return new Fight(trainer, player, seen, players);
	}

	/**
	 * Settling the look didn't flatten trainers into one: 24 Masters field (nearly) all different teams, and across the
	 * trainers both genders appear for species that can be either.
	 */
	//$ gametest_ticks_batch AREA '400' '"qaTeams1110dVariety"'
	@GameTest(template = AREA, timeoutTicks = 400, batch = "qaTeams1110dVariety")
	public void differentTrainersStillGetDifferentTeams(GameTestHelper helper) {
		Set<List<String>> teams = new HashSet<>();
		Set<Gender> genders = new HashSet<>();
		for (int n = 0; n < 24; n++) {
			List<Pokemon> team = CobblemonTrainers.team(id(100 + n), 5, false);
			teams.add(team.stream().map(p -> p.getSpecies().getName()).toList());
			for (Pokemon p : team) {
				if (p.getSpecies().getPossibleGenders().size() > 1) {
					genders.add(p.getGender());
				}
			}
		}
		helper.assertTrue(teams.size() >= 22, "24 Masters, only " + teams.size() + " different teams");
		helper.assertTrue(genders.contains(Gender.MALE) && genders.contains(Gender.FEMALE), "24 Masters' Pokémon were all " + genders);
		helper.succeed();
	}

	/**
	 * A trainer villager saved and loaded (as when its chunk unloads or the server restarts) fields the same team, and a
	 * trainer who moves up a rank and back gets each rank's team the same way each time.
	 */
	//$ gametest_ticks_batch AREA '200' '"qaTeams1110dReload"'
	@GameTest(template = AREA, timeoutTicks = 200, batch = "qaTeams1110dReload")
	public void theTeamSurvivesASaveAndARankChange(GameTestHelper helper) {
		Fight f = fixedTrainer(helper, id(0), 5, "qateams1110dreload");
		Villager v = f.trainer();
		helper.assertValueEqual(v.getUUID(), id(0), "the Trainer's UUID");
		helper.assertValueEqual(Trainers.tier(v), 5, "the Master's rank");
		List<String> before = looks(CobblemonTrainers.teamOf(v));
		CompoundTag saved = v.saveWithoutId(new CompoundTag());
		Villager loaded = new Villager(EntityType.VILLAGER, helper.getLevel());
		loaded.load(saved);
		helper.assertValueEqual(loaded.getUUID(), v.getUUID(), "the loaded villager's UUID");
		helper.assertValueEqual(looks(CobblemonTrainers.teamOf(loaded)), before, "the Master's team after a save and reload");

		v.setVillagerData(v.getVillagerData().setLevel(3));
		List<String> journeyman = looks(CobblemonTrainers.teamOf(v));
		helper.assertValueEqual(journeyman, looks(CobblemonTrainers.team(v.getUUID(), 3, false)), "the Journeyman's team");
		v.setVillagerData(v.getVillagerData().setLevel(4));
		List<String> expert = looks(CobblemonTrainers.teamOf(v));
		helper.assertFalse(expert.equals(journeyman), "an Expert fields its Journeyman team");
		helper.assertValueEqual(looks(CobblemonTrainers.teamOf(v)), expert, "the Expert's team, built again");
		v.setVillagerData(v.getVillagerData().setLevel(3));
		helper.assertValueEqual(looks(CobblemonTrainers.teamOf(v)), journeyman, "the Journeyman's team after a rank up and back");
		helper.succeed();
	}

	/**
	 * The screen shows the trainer's real team: each model on a Master's Trainer screen is the Pokémon it then sends into
	 * the battle started with the screen's Challenge button (same species, form, gender and shininess), in order.
	 */
	//$ gametest_ticks_batch AREA '200' '"qaTeams1110dBattle"'
	@GameTest(template = AREA, timeoutTicks = 200, batch = "qaTeams1110dBattle")
	public void theScreensModelsAreTheTeamInBattle(GameTestHelper helper) {
		Fight f = fixedTrainer(helper, id(0), 5, "qateams1110dbattle");
		TrainerMenu menu = openTrainer(helper, f.player(), f.trainer());
		TrainerView view = menu.view();
		press(f.player(), menu.containerId, TrainerMenu.CHALLENGE);
		var battle = BattleRegistry.getBattleByParticipatingPlayer(f.player());
		helper.assertTrue(battle != null, "Challenge on the screen started no battle");
		BattleActor them = null;
		for (BattleActor actor : battle.getActors()) {
			if (!actor.getUuid().equals(f.player().getUUID())) {
				them = actor;
			}
		}
		helper.assertTrue(them != null, "no trainer side in the battle");
		List<BattlePokemon> fielded = them.getPokemonList();
		helper.assertValueEqual(fielded.size(), view.team().size(), "Pokémon in battle vs on the screen");
		int compared = 0;
		for (int i = 0; i < fielded.size(); i++) {
			Pokemon p = fielded.get(i).getOriginalPokemon();
			ItemStack shown = view.team().get(i).icon();
			if (view.team().get(i).hidden()) {
				continue; // not seen yet: the screen shows no model there (43.1)
			}
			compared++;
			ItemStack inBattle = PokemonItem.from(p);
			helper.assertTrue(ItemStack.isSameItemSameComponents(shown, inBattle), "slot " + i + ": the screen shows "
				+ shown.getComponentsPatch() + ", the battle fields " + inBattle.getComponentsPatch());
		}
		helper.assertTrue(compared > 0, "the screen showed none of the Master's " + fielded.size() + " Pokémon");
		helper.succeed();
	}
}
