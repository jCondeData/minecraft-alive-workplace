package io.github.jcondedata.aliveworkplace.compat;

import io.github.jcondedata.aliveworkplace.AliveWorkplace;
import io.github.jcondedata.aliveworkplace.blueprint.BlueprintData;
import io.github.jcondedata.aliveworkplace.blueprint.StarterBlueprints;
import io.github.jcondedata.aliveworkplace.build.BuildSiteManager;
import io.github.jcondedata.aliveworkplace.fossil.FossilScientists;
import io.github.jcondedata.aliveworkplace.hall.CivicEffects;
import io.github.jcondedata.aliveworkplace.hall.Guilds;
import io.github.jcondedata.aliveworkplace.hall.VillageHallBlockEntity;
import io.github.jcondedata.aliveworkplace.hall.VillageHalls;
import io.github.jcondedata.aliveworkplace.hall.VillageNeeds;
import io.github.jcondedata.aliveworkplace.hall.VillageRanks;
import io.github.jcondedata.aliveworkplace.people.Moods;
import io.github.jcondedata.aliveworkplace.registry.ModBlocks;
import io.github.jcondedata.aliveworkplace.registry.ModVillagers;
import io.github.jcondedata.aliveworkplace.trainer.Trainers;
import io.github.jcondedata.aliveworkplace.tutor.Tutors;
import java.util.List;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.npc.VillagerProfession;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;

/**
 * The Trainers' Guild (ROADMAP 30.20), which loads only with Cobblemon: its data, and with it founded a Master Move
 * Tutor's hardest lesson costs 20 emeralds (not 24), a revival 7 (not 8: a fifth less, rounded up), and a trainer
 * earns 10 experience for a battle they win (not 8), 7 for one they lose (not 5).
 */
public class GuildTrainersCompatTests implements net.fabricmc.fabric.api.gametest.v1.FabricGameTest {
	private static final String AREA = CompatGameTests.AREA;
	private static final BlockPos HALL = new BlockPos(11, 2, 11);
	private static final BlockPos GUILDHALL = new BlockPos(2, 2, 14);

	//$ gametest_ticks_batch AREA '200' '"guild_trainers"'
	@GameTest(template = AREA, timeoutTicks = 200, batch = "guild_trainers")
	public void trainersGuildMakesLessonsCheaperAndTrainersFaster(GameTestHelper helper) {
		Guilds.Guild g = Guilds.get(AliveWorkplace.id("trainers"));
		helper.assertTrue(g != null, "the Trainers' Guild didn't load with Cobblemon: " + Guilds.all().keySet());
		helper.assertTrue(g.name().getString().equals("Trainers' Guild"), "name: " + g.name().getString());
		helper.assertTrue(g.trades().equals(List.of(AliveWorkplace.id("trainer"), AliveWorkplace.id("trainer_leader"), AliveWorkplace.id("tutor"),
			AliveWorkplace.id("pokemon_trader"), AliveWorkplace.id("fossil_scientist"))), "trades: " + g.trades());
		helper.assertTrue(g.perk().getString().equals(
				"Move Tutors' lessons and Fossil Scientists' revivals cost a fifth less, and Trainers and Trainer Leaders rank up a quarter faster."),
			"perk: " + g.perk().getString());
		helper.assertTrue(g.perks().size() == 2 && g.perks().get(0) instanceof Guilds.LessonPrice l && l.percent() == -20
			&& g.perks().get(1) instanceof Guilds.TrainerXp x && x.percent() == 25, "perks: " + g.perks());

		ServerLevel level = helper.getLevel();
		int radius = VillageHalls.RADIUS;
		VillageHalls.RADIUS = 16;
		BlueprintData.Placement at = new BlueprintData.Placement(level.dimension().location(), helper.absolutePos(GUILDHALL), Rotation.NONE, Mirror.NONE);
		PartnerShowsCompatTests.after(helper, () -> {
			VillageHalls.RADIUS = radius;
			Guilds.ENABLED = true;
			BuildSiteManager.get(level).forgetFinished(at);
			VillageNeeds.forget();
			CivicEffects.forget();
			Guilds.forget();
			Moods.forget();
		});
		helper.setBlock(HALL, ModBlocks.VILLAGE_HALL);
		Villager tutor = villager(helper, new BlockPos(16, 2, 4), "Tova", ModVillagers.TUTOR, 5);
		Villager scientist = villager(helper, new BlockPos(17, 2, 4), "Fitz", ModVillagers.FOSSIL_SCIENTIST, 3);
		Villager trainer = villager(helper, new BlockPos(18, 2, 4), "Rex", ModVillagers.TRAINER, 2);
		Villager mason = villager(helper, new BlockPos(19, 2, 4), "Mo", VillagerProfession.MASON, 2);
		helper.runAfterDelay(5, () -> {
			VillageHallBlockEntity hall = (VillageHallBlockEntity) level.getBlockEntity(helper.absolutePos(HALL));
			hall.setNeeds(new VillageNeeds.Needs(1, 1, 1, 1, 1, 1, 0, 0.5f));
			hall.setRank(VillageRanks.Rank.CITY);
			hall.setGuilds(List.of());
			VillageNeeds.forget();
			CivicEffects.forget();
			Guilds.forget();
			Guilds.Offer offer = Guilds.grant(level, tutor);
			helper.assertTrue(offer.outcome() == Guilds.Outcome.GRANTED, "chartered: " + offer.message().getString());
			helper.assertTrue(offer.message().getString().contains("Trainers' Guild"), "the tutor's guild: " + offer.message().getString());
			ResourceLocation id = hall.guilds().get(0).id();
			helper.assertTrue(!Guilds.founded(level, hall, id), "not founded without a Guildhall");
			helper.assertTrue(Tutors.price(tutor, 5) == 24 && Tutors.dollars(tutor, 5) == 2400, "not founded: 24, got " + Tutors.price(tutor, 5));
			helper.assertTrue(FossilScientists.price(scientist) == 8, "not founded: a revival is 8");
			helper.assertTrue(Trainers.battleXp(trainer, false) == 8 && Trainers.battleXp(trainer, true) == 5, "not founded: 8 and 5");

			BuildSiteManager.get(level).recordFinished(StarterBlueprints.GUILDHALL.id(), at, UUID.randomUUID());
			Guilds.round(level, helper.absolutePos(HALL), hall);
			helper.assertTrue(Guilds.founded(level, hall, id), "founded: " + hall.guilds());
			helper.assertTrue(Tutors.price(tutor, 5) == 20 && Tutors.dollars(tutor, 5) == 2000, "founded: 20, got " + Tutors.price(tutor, 5));
			helper.assertTrue(Tutors.price(tutor, 1) == 3 && Tutors.price(tutor, 3) == 8, "founded: 3 stays 3, 10 is 8");
			helper.assertTrue(FossilScientists.price(scientist) == 7, "founded: a revival is 7, got " + FossilScientists.price(scientist));
			helper.assertTrue(Trainers.battleXp(trainer, false) == 10 && Trainers.battleXp(trainer, true) == 7,
				"founded: 10 and 7, got " + Trainers.battleXp(trainer, false) + "/" + Trainers.battleXp(trainer, true));
			helper.assertTrue(Guilds.lessonPrice(mason, 24) == 24, "a mason's prices aren't the guild's");
			try {
				Guilds.ENABLED = false;
				helper.assertTrue(Tutors.price(tutor, 5) == 24 && Trainers.battleXp(trainer, false) == 8, "guilds off: 24 and 8");
			} finally {
				Guilds.ENABLED = true;
			}
			helper.succeed();
		});
	}

	private static Villager villager(GameTestHelper helper, BlockPos pos, String name, VillagerProfession job, int lvl) {
		Villager v = helper.spawn(EntityType.VILLAGER, pos);
		v.setNoAi(true);
		v.setCustomName(Component.literal(name));
		v.setVillagerData(v.getVillagerData().setProfession(job).setLevel(lvl));
		return v;
	}
}
