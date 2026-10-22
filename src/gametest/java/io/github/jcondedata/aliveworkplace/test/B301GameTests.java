package io.github.jcondedata.aliveworkplace.test;

import io.github.jcondedata.aliveworkplace.AliveWorkplace;
import io.github.jcondedata.aliveworkplace.compat.mobsbegone.MobsBeGoneBlocklist;
import io.github.jcondedata.aliveworkplace.hall.Foes;
import io.github.jcondedata.aliveworkplace.hall.Reforms;
import io.github.jcondedata.aliveworkplace.hall.VillageHallBlockEntity;
import io.github.jcondedata.aliveworkplace.hall.VillageHallScreen;
import io.github.jcondedata.aliveworkplace.hall.VillageHalls;
import io.github.jcondedata.aliveworkplace.hall.VillageQuests;
import io.github.jcondedata.aliveworkplace.registry.ModBlocks;
import io.github.jcondedata.aliveworkplace.story.Objectives;
import io.github.jcondedata.aliveworkplace.story.Quest;
import io.github.jcondedata.aliveworkplace.story.QuestFiles;
import io.github.jcondedata.aliveworkplace.story.Stories;
import io.github.jcondedata.aliveworkplace.threat.Lairs;
import io.github.jcondedata.aliveworkplace.work.ChoiceMenu;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;

/**
 * B301: in Cobbleverse (MobsBeGone removes the monsters) the hall offered "Clear out 8 monsters", which could never be
 * finished. Where no monster can come it offers none (nor a reform step asking for one); wild Pokémon count instead with
 * Cobblemon ("Defeat 8 wild Pokémon"), bandits always; a stuck quest already up is swapped in the hall's round.
 */
public class B301GameTests implements FabricGameTest {
	private static final String AREA = "aliveworkplace_test:big_area";
	private static final BlockPos HALL = new BlockPos(9, 2, 9);

	/** A hall (radius 16) whose own rounds post nothing; the switches put back at the end. */
	private static VillageHallBlockEntity hall(GameTestHelper helper, Boolean hostiles, Boolean wild) {
		int radius = VillageHalls.RADIUS;
		VillageHalls.RADIUS = 16;
		Foes.HOSTILES_FOR_TESTS = hostiles;
		Foes.WILD_FOR_TESTS = wild;
		Leftovers.after(helper, () -> {
			VillageHalls.RADIUS = radius;
			Foes.HOSTILES_FOR_TESTS = null;
			Foes.WILD_FOR_TESTS = null;
		});
		helper.setBlock(HALL, ModBlocks.VILLAGE_HALL);
		VillageHallBlockEntity entity = (VillageHallBlockEntity) helper.getLevel().getBlockEntity(helper.absolutePos(HALL));
		entity.setLastQuestDay(Long.MAX_VALUE / 2);
		return entity;
	}

	private static Quest slay(ServerLevel level, String giver) {
		long now = level.getGameTime();
		return new Quest(UUID.randomUUID(), AliveWorkplace.id("daily/slay"), giver, Optional.empty(), "Ana", now, now + VillageQuests.LASTS,
			List.of(new Objectives.Kill("monster", 8, false)), new int[1], List.of());
	}

	private static boolean clearsMonsters(Quest q) {
		return q != null && q.objectives.stream().anyMatch(o -> o instanceof Objectives.Kill k && k.entity().equals("monster"));
	}

	private static ServerPlayer player(GameTestHelper helper) {
		ServerPlayer player = helper.makeMockServerPlayerInLevel();
		player.setGameMode(GameType.SURVIVAL);
		return player;
	}

	/** No monster can come and no Pokémon count: the board never offers a clearing-out quest, nor a reform its step. */
	//$ gametest_ticks_batch AREA '60' '"noSlayWhereNoMonsterCanCome"'
	@GameTest(template = AREA, timeoutTicks = 60, batch = "noSlayWhereNoMonsterCanCome")
	public void noSlayWhereNoMonsterCanCome(GameTestHelper helper) {
		Leftovers.clear(helper);
		ServerLevel level = helper.getLevel();
		hall(helper, true, false);
		BlockPos hall = helper.absolutePos(HALL);
		// With monsters the board's clearing-out file can be asked for (so the check below can see it).
		Objectives.Context context = new Objectives.Context(level, hall, VillageHalls.census(level, hall), RandomSource.create(1), "");
		Objectives.Objective slayFile = QuestFiles.get(AliveWorkplace.id("daily/slay")).orElseThrow().objectives().get(0);
		helper.assertTrue(slayFile.resolve(context) != null, "not asked for even with monsters about");
		Foes.HOSTILES_FOR_TESTS = false;
		for (int seed = 0; seed < 60; seed++) {
			Quest made = Stories.make(level, hall, 1f, RandomSource.create(seed));
			helper.assertTrue(!clearsMonsters(made), "offered with no monsters (seed " + seed + "): " + made);
		}
		helper.assertTrue(slayFile.resolve(context) == null, "the board's clearing-out file can still be asked for");
		// A reform's clearing-out step, and a battle's stand-in for one, become bringing bread at the same pay.
		Reforms.Step slayStep = new Reforms.Step(VillageQuests.Kind.SLAY, "minecraft:air", 32, 6, Optional.empty(), Optional.empty());
		Reforms.Step asked = slayStep.resolve(level, hall);
		helper.assertTrue(asked.kind() == VillageQuests.Kind.BRING && asked.item().equals("minecraft:bread") && asked.count() == 32 && asked.reward() == 6,
			"the reform step: " + asked);
		Reforms.Step battle = new Reforms.Step(VillageQuests.Kind.BATTLE, "minecraft:air", 1, 8, Optional.empty(), Optional.empty()).resolve(false, false);
		helper.assertTrue(battle.kind() == VillageQuests.Kind.BRING && battle.count() == 8 && battle.reward() == 8,
			"the battle's stand-in: " + battle);
		helper.assertTrue(new Reforms.Step(VillageQuests.Kind.SLAY, "minecraft:air", 100, 6, Optional.empty(), Optional.empty()).resolve(true, false).count() == 64,
			"more bread than a stack");
		// With wild Pokémon counting, the clearing-out quest can go up again.
		Foes.WILD_FOR_TESTS = true;
		helper.assertTrue(slayStep.resolve(level, hall).kind() == VillageQuests.Kind.SLAY, "wild Pokémon count, but the step changed");
		helper.assertTrue(slayFile.resolve(context) != null, "not asked for with wild Pokémon counting");
		helper.succeed();
	}

	/** No monsters, wild Pokémon count: a bandit's kill and a wild Pokémon beaten count, a cow doesn't; the quest says so. */
	//$ gametest_ticks_batch AREA '60' '"banditsAndWildPokemonCount"'
	@GameTest(template = AREA, timeoutTicks = 60, batch = "banditsAndWildPokemonCount")
	public void banditsAndWildPokemonCount(GameTestHelper helper) {
		Leftovers.clear(helper);
		ServerLevel level = helper.getLevel();
		hall(helper, false, true);
		BlockPos hall = helper.absolutePos(HALL);
		Quest quest = slay(level, "hall");
		Stories.post(level, hall, quest);
		ServerPlayer player = player(helper);

		var cow = helper.spawn(EntityType.COW, new BlockPos(5, 2, 5));
		cow.hurt(level.damageSources().playerAttack(player), 1000f);
		helper.assertTrue(quest.progress[0] == 0, "a cow counted: " + quest.progress[0]);
		var bandit = helper.spawn(EntityType.COW, new BlockPos(6, 2, 5));
		bandit.addTag(Lairs.BANDIT_TAG);
		bandit.hurt(level.damageSources().playerAttack(player), 1000f);
		helper.assertTrue(quest.progress[0] == 1, "the bandit didn't count: " + quest.progress[0]);
		var raider = helper.spawn(EntityType.VINDICATOR, new BlockPos(7, 2, 5));
		raider.hurt(level.damageSources().playerAttack(player), 1000f);
		helper.assertTrue(quest.progress[0] == 2, "the vindicator didn't count: " + quest.progress[0]);

		// A battle won against two wild Pokémon by the hall (the Cobblemon integration's call).
		VillageQuests.onWildDefeated(level, hall.east(3), player, 2);
		helper.assertTrue(quest.progress[0] == 4, "the wild Pokémon didn't count: " + quest.progress[0]);
		// Far from any hall: nothing.
		VillageQuests.onWildDefeated(level, hall.east(200), player, 1);
		helper.assertTrue(quest.progress[0] == 4, "counted far away: " + quest.progress[0]);

		// The words match: on the quest, the hall's page and its hint.
		helper.assertTrue(quest.title().getString().equals("Defeat 8 wild Pokémon"), "title: " + quest.title().getString());
		ChoiceMenu menu = ChoiceMenu.detached(player, m -> VillageHallScreen.renderQuests(m, level, hall, player));
		ItemStack icon = menu.icon(VillageHallScreen.QUEST_SLOTS[0]);
		helper.assertTrue(icon.getHoverName().getString().equals("Defeat 8 wild Pokémon"), "page: " + icon.getHoverName().getString());
		helper.assertTrue(Foes.hint(16).getString().equals("Battle or catch wild Pokémon, or defeat bandits, within 16 blocks of the hall"),
			"hint: " + Foes.hint(16).getString());

		// Where monsters come, Pokémon don't count and the words are the old ones.
		Foes.HOSTILES_FOR_TESTS = true;
		VillageQuests.onWildDefeated(level, hall.east(3), player, 1);
		helper.assertTrue(quest.progress[0] == 4, "a Pokémon counted as a monster: " + quest.progress[0]);
		helper.assertTrue(quest.title().getString().equals("Clear out 8 monsters"), "title with monsters: " + quest.title().getString());
		helper.assertTrue(Foes.hint(16).getString().equals("Defeat monsters within 16 blocks of the hall"), "hint with monsters");
		helper.assertTrue(Component.translatable("quest.aliveworkplace.slay_wild", 3).getString().equals("Defeat 3 wild Pokémon"), "lang");
		helper.succeed();
	}

	/** A clearing-out quest already up where nothing can be fought is swapped in the hall's round: board, story and reform. */
	//$ gametest_ticks_batch AREA '60' '"stuckSlaySwappedOnLoad"'
	@GameTest(template = AREA, timeoutTicks = 60, batch = "stuckSlaySwappedOnLoad")
	public void stuckSlaySwappedOnLoad(GameTestHelper helper) {
		Leftovers.clear(helper);
		ServerLevel level = helper.getLevel();
		VillageHallBlockEntity entity = hall(helper, false, false);
		BlockPos hall = helper.absolutePos(HALL);
		Quest board = slay(level, "hall");
		board.progress[0] = 5;
		Quest story = slay(level, "arc");
		story.arc = "aliveworkplace:test";
		Stories.post(level, hall, board);
		Stories.post(level, hall, story);
		UUID reformId = UUID.randomUUID();
		VillageQuests.Quest reform = new VillageQuests.Quest(reformId, VillageQuests.Kind.SLAY, "minecraft:air", 32, 9, 6, level.getGameTime(), "",
			Optional.empty(), Optional.of(new VillageQuests.ReformStep("aliveworkplace:curfew", 0)));
		entity.setQuests(List.of(reform));
		// As loaded from a save: the hall and the dimension's quests written and read back.
		CompoundTag tag = entity.saveWithFullMetadata(level.registryAccess());
		entity.loadWithComponents(tag, level.registryAccess());
		Stories.Data loaded = Stories.Data.load(Stories.Data.get(level).save(new CompoundTag(), level.registryAccess()), level.registryAccess());
		helper.assertTrue(loaded != null, "the quests didn't load");

		VillageQuests.tick(level, hall, entity);
		List<Quest> open = Stories.open(level, hall);
		helper.assertTrue(open.stream().noneMatch(B301GameTests::clearsMonsters), "still asks for monsters: " + open);
		helper.assertTrue(open.stream().noneMatch(q -> q.id.equals(board.id)), "the board's quest stayed: " + open);
		Quest swapped = open.stream().filter(q -> q.id.equals(story.id)).findFirst().orElse(null);
		helper.assertTrue(swapped != null && swapped.objectives.get(0) instanceof Objectives.Bring b && b.item().equals("minecraft:bread")
			&& b.count() == 8 && swapped.progress[0] == 0 && "aliveworkplace:test".equals(swapped.arc) && swapped.due == story.due, "the story's: " + swapped);
		helper.assertTrue(swapped.title().getString().equals("Bring 8 Bread"), "its words: " + swapped.title().getString());
		VillageQuests.Quest step = entity.quests().stream().filter(q -> q.id().equals(reformId)).findFirst().orElse(null);
		helper.assertTrue(step != null && step.kind() == VillageQuests.Kind.BRING && step.item().equals("minecraft:bread") && step.count() == 32
			&& step.progress() == 0 && step.reward() == 6 && step.reform().equals(reform.reform()), "the reform step: " + step);

		// The bread is handed in at the hall like any bring.
		ServerPlayer player = player(helper);
		player.getInventory().add(new ItemStack(Items.BREAD, 8));
		helper.assertTrue(VillageQuests.handIn(player, hall, story.id) == 8, "the bread wasn't taken");
		helper.assertTrue(Stories.open(level, hall).stream().noneMatch(q -> q.id.equals(story.id)), "not finished");

		// A second round changes nothing more.
		List<Quest> before = Stories.open(level, hall);
		VillageQuests.tick(level, hall, entity);
		helper.assertTrue(Stories.open(level, hall).equals(before), "swapped twice");
		helper.succeed();
	}

	/** MobsBeGone's file is read whatever its layout; Cobbleverse's names every night monster, so none can come. */
	//$ gametest 'FabricGameTest.EMPTY_STRUCTURE'
	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
	public void mobsBeGoneBlocklistIsRead(GameTestHelper helper) {
		Set<String> ids = MobsBeGoneBlocklist.parse("{\"blacklist\": [\"minecraft:zombie\", \"minecraft:skeleton\",\n \"creeper\", \"minecraft:spider\","
			+ " \"minecraft:iron_golem\"]}");
		helper.assertTrue(ids.contains("minecraft:zombie") && ids.contains("minecraft:creeper") && ids.contains("minecraft:iron_golem"), "read: " + ids);
		helper.assertTrue(Foes.NIGHT_MONSTERS.stream().allMatch(id -> ids.contains(id.toString())), "a night monster missing: " + ids);
		Set<String> some = MobsBeGoneBlocklist.parse("[\"minecraft:zombie\", \"minecraft:pillager\"]");
		helper.assertTrue(!Foes.NIGHT_MONSTERS.stream().allMatch(id -> some.contains(id.toString())), "creepers still come: " + some);
		helper.assertTrue(Foes.isFoe(EntityType.ZOMBIE.create(helper.getLevel())) && !Foes.isFoe(EntityType.COW.create(helper.getLevel())), "foes");
		helper.succeed();
	}
}
