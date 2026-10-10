package io.github.jcondedata.aliveworkplace.compat;

import io.github.jcondedata.aliveworkplace.AliveWorkplace;
import io.github.jcondedata.aliveworkplace.hall.Chronicle;
import io.github.jcondedata.aliveworkplace.hall.CivicEffects;
import io.github.jcondedata.aliveworkplace.hall.VillageHallBlockEntity;
import io.github.jcondedata.aliveworkplace.hall.VillageHalls;
import io.github.jcondedata.aliveworkplace.registry.ModBlocks;
import io.github.jcondedata.aliveworkplace.registry.ModVillagers;
import io.github.jcondedata.aliveworkplace.story.Friendship;
import io.github.jcondedata.aliveworkplace.story.Objectives;
import io.github.jcondedata.aliveworkplace.story.PersonalRequests;
import io.github.jcondedata.aliveworkplace.story.Quest;
import io.github.jcondedata.aliveworkplace.story.QuestFiles;
import io.github.jcondedata.aliveworkplace.story.Stories;
import io.github.jcondedata.aliveworkplace.trainer.Trainers;
import java.util.List;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.GlobalPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.npc.VillagerProfession;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;

/**
 * The two Cobblemon personal requests (ROADMAP 31.9) with the real Cobblemon installed: <b>Help me train</b>, a
 * Trainer beaten in battle on three different days (through the trainers' own battle-over hook), and <b>A Pokémon
 * friend</b>, a Pokémon of a type that helps their job in a real Pasture Block within 16 blocks of their workstation at
 * dawn. Without Cobblemon neither is ever offered ({@code PersonalRequestGameTests}).
 */
public class PersonalRequestCompatTests implements FabricGameTest {
	private static final String AREA = CompatGameTests.AREA;
	private static final BlockPos HALL = new BlockPos(8, 2, 8);

	/** A hall with requests and friendship on; everything is put back afterwards. */
	private static void village(GameTestHelper helper) {
		boolean friendship = Friendship.ENABLED;
		boolean requests = PersonalRequests.ENABLED;
		ServerLevel level = helper.getLevel();
		BlockPos hall = helper.absolutePos(HALL);
		Friendship.ENABLED = true;
		PersonalRequests.ENABLED = true;
		PersonalRequests.forget();
		Stories.Data.get(level).remove(hall);
		PartnerShowsCompatTests.after(helper, () -> {
			Friendship.ENABLED = friendship;
			PersonalRequests.ENABLED = requests;
			PersonalRequests.forget();
			Stories.Data.get(level).remove(hall);
			CivicEffects.forget();
		});
		helper.setBlock(HALL, ModBlocks.VILLAGE_HALL);
	}

	private static Villager worker(GameTestHelper helper, BlockPos at, String name, VillagerProfession job) {
		ServerLevel level = helper.getLevel();
		Villager v = EntityType.VILLAGER.create(level);
		Vec3 pos = helper.absoluteVec(Vec3.atBottomCenterOf(at));
		v.moveTo(pos.x, pos.y, pos.z, 0, 0);
		v.setNoAi(true);
		v.setCustomName(Component.literal(name));
		v.setVillagerData(v.getVillagerData().setProfession(job).setLevel(2));
		level.addFreshEntity(v);
		return v;
	}

	private static ServerPlayer player(GameTestHelper helper, BlockPos at) {
		ServerPlayer player = helper.makeMockServerPlayerInLevel();
		player.setGameMode(GameType.SURVIVAL);
		BlockPos by = helper.absolutePos(at); // a mock player starts at the world spawn: bring it to the test
		player.teleportTo(by.getX() + 0.5, by.getY(), by.getZ() + 0.5);
		PartnerShowsCompatTests.after(helper, () -> helper.getLevel().getServer().getPlayerList().remove(player));
		return player;
	}

	private static Quest resolve(GameTestHelper helper, String name, Villager villager, ServerPlayer player) {
		ServerLevel level = helper.getLevel();
		BlockPos hall = helper.absolutePos(HALL);
		QuestFiles.QuestFile file = QuestFiles.get(AliveWorkplace.id("personal/" + name)).orElseThrow();
		return PersonalRequests.resolve(level, hall, (VillageHallBlockEntity) level.getBlockEntity(hall), VillageHalls.census(level, hall), file, villager, player,
			RandomSource.create(7));
	}

	private static Quest accepted(GameTestHelper helper, String name, Villager villager, ServerPlayer player) {
		ServerLevel level = helper.getLevel();
		Quest quest = resolve(helper, name, villager, player);
		helper.assertTrue(quest != null, villager.getDisplayName().getString() + " has no " + name + " to ask");
		PersonalRequests.propose(level, helper.absolutePos(HALL), villager, player, quest, Chronicle.day(level));
		level.getServer().getCommands().performPrefixedCommand(player.createCommandSourceStack(), "workplace quest accept " + quest.id);
		helper.assertTrue(PersonalRequests.of(villager) == quest && quest.helpers.containsKey(player.getUUID()), name + " wasn't accepted");
		return quest;
	}

	private static List<String> friendLines(GameTestHelper helper) {
		VillageHallBlockEntity entity = (VillageHallBlockEntity) helper.getLevel().getBlockEntity(helper.absolutePos(HALL));
		return entity.chronicle().stream().filter(e -> e.kind() == Chronicle.Kind.FRIEND).map(e -> e.text().getString()).toList();
	}

	/** Help me train: only a Trainer asks; a win counts once a day, a loss never; three days of wins and it's done. */
	//$ gametest_ticks_batch AREA '100' '"request_train"'
	@GameTest(template = AREA, timeoutTicks = 100, batch = "request_train")
	public void helpMeTrainTakesWinsOnThreeDays(GameTestHelper helper) {
		village(helper);
		helper.runAfterDelay(2, () -> {
			CivicEffects.forget(); // the hall's POI goes in after the tick it was placed
			ServerLevel level = helper.getLevel();
			long today = Chronicle.day(level);
			helper.assertTrue(Trainers.COBBLEMON, "Cobblemon isn't loaded");
			Villager tara = worker(helper, new BlockPos(5, 2, 5), "Tara", ModVillagers.TRAINER);
			Villager dara = worker(helper, new BlockPos(7, 2, 5), "Dara", VillagerProfession.FARMER);
			ServerPlayer me = player(helper, new BlockPos(5, 2, 7));
			Friendship.add(tara, me, 300);
			Friendship.add(dara, me, 300);
			helper.assertTrue(resolve(helper, "help_me_train", dara, me) == null, "a farmer asked for a battle");
			Quest quest = accepted(helper, "help_me_train", tara, me);
			String name = me.getGameProfile().getName();
			helper.assertTrue(quest.objectives.get(0) instanceof Objectives.BeatGiver beat && beat.days() == 3 && quest.emeralds() == 0, "the request: " + quest);
			helper.assertTrue(PersonalRequests.wants(level, quest).getString().equals("Tara wants a real rival: beat them in battle on 3 different days within 7 days"),
				PersonalRequests.wants(level, quest).getString());
			// A lost battle counts for nothing; a won one counts, once that day.
			Trainers.battleOver(level.getServer(), tara.getUUID(), me.getUUID(), false);
			helper.assertTrue(quest.progress[0] == 0, "a loss counted");
			Trainers.battleOver(level.getServer(), tara.getUUID(), me.getUUID(), true);
			helper.assertTrue(quest.progress[0] == 1 && quest.mark == today, "after the first win: " + quest.progress[0] + ", marked day " + quest.mark + " on " + today);
			Trainers.battleOver(level.getServer(), tara.getUUID(), me.getUUID(), true);
			helper.assertTrue(quest.progress[0] == 1, "a second win the same day counted");
			// The next two days.
			helper.assertTrue(PersonalRequests.onBattleWon(level, tara, me, today + 1) && !PersonalRequests.onBattleWon(level, tara, me, today + 1) && quest.progress[0] == 2,
				"the second day: " + quest.progress[0]);
			helper.assertTrue(PersonalRequests.of(tara) == quest && Friendship.points(tara, me.getUUID()) == 300, "paid before the third day");
			helper.assertTrue(PersonalRequests.onBattleWon(level, tara, me, today + 2) && PersonalRequests.of(tara) == null, "not done on the third day");
			helper.assertTrue(Friendship.points(tara, me.getUUID()) == 300 + Friendship.Favour.REQUEST.points, "friendship: " + Friendship.points(tara, me.getUUID()));
			helper.assertTrue(friendLines(helper).contains(name + " helped Tara: Help me train"), "the chronicle: " + friendLines(helper));
			helper.succeed();
		});
	}

	/** A Pokémon friend: a Pokémon of a type that helps the job, pastured within 16 blocks of the workstation, judged at dawn. */
	//$ gametest_ticks_batch AREA '200' '"request_partner"'
	@GameTest(template = AREA, timeoutTicks = 200, batch = "request_partner")
	public void aPokemonFriendIsJudgedAtDawn(GameTestHelper helper) {
		village(helper);
		ServerLevel level = helper.getLevel();
		BlockPos hall = helper.absolutePos(HALL);
		BlockPos station = new BlockPos(2, 2, 2);
		helper.setBlock(station, Blocks.COMPOSTER);
		BlockPos pasture = PastureCompatTests.pasture(helper, new BlockPos(11, 2, 11));
		Villager[] dara = new Villager[1];
		ServerPlayer[] me = new ServerPlayer[1];
		Quest[] quest = new Quest[1];
		helper.runAfterDelay(2, () -> {
			CivicEffects.forget();
			dara[0] = worker(helper, new BlockPos(4, 2, 4), "Dara", VillagerProfession.FARMER);
			dara[0].getBrain().setMemory(MemoryModuleType.JOB_SITE, GlobalPos.of(level.dimension(), helper.absolutePos(station)));
			me[0] = player(helper, new BlockPos(5, 2, 7));
			Friendship.add(dara[0], me[0], 300);
			// Someone whose job no Pokémon helps with, or who has no workstation, has nothing to ask.
			Villager libby = worker(helper, new BlockPos(6, 2, 4), "Libby", VillagerProfession.LIBRARIAN);
			libby.getBrain().setMemory(MemoryModuleType.JOB_SITE, GlobalPos.of(level.dimension(), helper.absolutePos(station)));
			Villager odo = worker(helper, new BlockPos(7, 2, 4), "Odo", VillagerProfession.FARMER);
			Friendship.add(libby, me[0], 300);
			Friendship.add(odo, me[0], 300);
			helper.assertTrue(resolve(helper, "pokemon_friend", libby, me[0]) == null && resolve(helper, "pokemon_friend", odo, me[0]) == null,
				"a librarian or a farmer without a workstation asked for a Pokémon");
			quest[0] = accepted(helper, "pokemon_friend", dara[0], me[0]);
			helper.assertTrue(quest[0].objectives.get(0) instanceof Objectives.PartnerPokemon partner && partner.radius() == 16
				&& partner.types().equals(List.of("grass", "ground", "water")), "the request: " + quest[0].objectives);
			String wants = PersonalRequests.wants(level, quest[0]).getString();
			helper.assertTrue(wants.startsWith("Dara wants a Pokémon friend at work (") && wants.contains("Water type, pastured within 16 blocks of their workstation at dawn) within 5 days"),
				wants);
			// A Pikachu in the pasture: not a type that helps a farmer.
			PastureCompatTests.pastured(helper, pasture, me[0], "pikachu", Direction.WEST);
		});
		helper.runAfterDelay(20, () -> {
			PersonalRequests.look(level, hall, (VillageHallBlockEntity) level.getBlockEntity(hall), true);
			helper.assertTrue(PersonalRequests.of(dara[0]) == quest[0], "a Pikachu was friend enough for a farmer");
			PastureCompatTests.pastured(helper, pasture, me[0], "bulbasaur", Direction.NORTH);
		});
		helper.runAfterDelay(40, () -> {
			VillageHallBlockEntity entity = (VillageHallBlockEntity) level.getBlockEntity(hall);
			helper.assertTrue(Objectives.PartnerPokemon.partners(level, dara[0], 16, List.of("grass", "ground", "water")) == 1, "the Bulbasaur isn't counted near her workstation");
			// Any other time of day nothing is judged; at dawn it is.
			PersonalRequests.look(level, hall, entity, false);
			helper.assertTrue(PersonalRequests.of(dara[0]) == quest[0], "judged before dawn");
			PersonalRequests.look(level, hall, entity, true);
			helper.assertTrue(PersonalRequests.of(dara[0]) == null && Friendship.points(dara[0], me[0].getUUID()) == 300 + Friendship.Favour.REQUEST.points,
				"at dawn: " + PersonalRequests.of(dara[0]) + ", " + Friendship.points(dara[0], me[0].getUUID()) + " points");
			helper.assertTrue(friendLines(helper).contains(me[0].getGameProfile().getName() + " helped Dara: A Pokémon friend"), "the chronicle: " + friendLines(helper));
			// With a partner already, a farmer at the same workstation has nothing to ask for.
			Villager finn = worker(helper, new BlockPos(6, 2, 6), "Finn", VillagerProfession.FARMER);
			finn.getBrain().setMemory(MemoryModuleType.JOB_SITE, GlobalPos.of(level.dimension(), helper.absolutePos(station)));
			Friendship.add(finn, me[0], 300);
			helper.assertTrue(resolve(helper, "pokemon_friend", finn, me[0]) == null, "asked for a Pokémon friend with one by the workstation");
			helper.succeed();
		});
	}
}
