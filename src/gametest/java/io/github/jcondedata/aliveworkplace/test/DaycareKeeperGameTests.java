package io.github.jcondedata.aliveworkplace.test;

import io.github.jcondedata.aliveworkplace.daycare.DaycareKeepers;
import io.github.jcondedata.aliveworkplace.registry.ModVillagers;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.locale.Language;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.npc.VillagerData;
import net.minecraft.world.entity.npc.VillagerType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/** ROADMAP 28.12 without Cobblemon: the Daycare Keeper's odds, her dawns, her saved pairs and her text. */
public class DaycareKeeperGameTests implements FabricGameTest {
	/** 70%, 50%, 20% by how well a pair gets along, 10 more at Expert and Master, never for a pair that doesn't. */
	//$ gametest 'FabricGameTest.EMPTY_STRUCTURE'
	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
	public void theOddsFollowHowWellTheyGetAlong(GameTestHelper helper) {
		int[][] expected = {{0, 20, 50, 70}, {0, 20, 50, 70}, {0, 20, 50, 70}, {0, 30, 60, 80}, {0, 30, 60, 80}};
		for (int level = 1; level <= 5; level++) {
			for (int along = DaycareKeepers.NOT_AT_ALL; along <= DaycareKeepers.VERY_WELL; along++) {
				int chance = DaycareKeepers.chance(along, level);
				helper.assertTrue(chance == expected[level - 1][along], "level " + level + ", " + DaycareKeepers.GET_ALONG[along] + ": " + chance + "%");
			}
		}
		helper.assertTrue(!DaycareKeepers.isEgg(new ItemStack(Items.EGG)), "an egg picks the job without Cobblemon");
		helper.succeed();
	}

	/**
	 * Without the Pokémon side (no Cobblemon) the dawns still pass: a pair's day moves on and no egg is found; a pair left
	 * today isn't rolled until the next dawn; the eggs she keeps never pass three.
	 */
	//$ gametest 'FabricGameTest.EMPTY_STRUCTURE'
	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
	public void dawnsPassWithoutThePokemonSide(GameTestHelper helper) {
		Villager keeper = EntityType.VILLAGER.create(helper.getLevel());
		long today = DaycareKeepers.day(helper.getLevel());
		DaycareKeepers.setPairs(keeper, List.of(
			new DaycareKeepers.Pair(UUID.randomUUID(), "a", new CompoundTag(), new CompoundTag(), 0, today - 5),
			new DaycareKeepers.Pair(UUID.randomUUID(), "b", new CompoundTag(), new CompoundTag(), 3, today)));
		DaycareKeepers.dawn(helper.getLevel(), keeper, RandomSource.create(1));
		List<DaycareKeepers.Pair> pairs = DaycareKeepers.pairs(keeper);
		helper.assertTrue(pairs.get(0).day() == today && pairs.get(0).eggs() == 0, "the first pair: " + pairs.get(0));
		helper.assertTrue(pairs.get(1).day() == today && pairs.get(1).eggs() == DaycareKeepers.MAX_EGGS, "the second pair: " + pairs.get(1));
		helper.assertTrue(DaycareKeepers.MAX_PAIRS == 3 && DaycareKeepers.EGG_PRICE == 4, "three pairs, four emeralds an egg");
		helper.succeed();
	}

	/** Her job runs our work package, and with no pairs she says so; with the switch off she says that. */
	//$ gametest 'FabricGameTest.EMPTY_STRUCTURE'
	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
	public void sheIsAWorker(GameTestHelper helper) {
		helper.assertTrue(ModVillagers.isWorker(ModVillagers.DAYCARE_KEEPER), "the Daycare Keeper doesn't run our work package");
		Villager villager = EntityType.VILLAGER.create(helper.getLevel());
		villager.setVillagerData(new VillagerData(VillagerType.PLAINS, ModVillagers.DAYCARE_KEEPER, 1));
		helper.assertTrue(DaycareKeepers.isKeeper(villager), "not a keeper");
		helper.succeed();
	}

	/** Every sentence she says is in the language file. */
	//$ gametest 'FabricGameTest.EMPTY_STRUCTURE'
	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
	public void herTextIsTranslated(GameTestHelper helper) {
		List<String> missing = new ArrayList<>();
		for (String state : new String[]{"off", "empty", "watching", "eggs"}) {
			check(missing, "message.aliveworkplace.daycare_keeper.state." + state);
		}
		for (String along : DaycareKeepers.GET_ALONG) {
			check(missing, "screen.aliveworkplace.daycare_keeper.get_along." + along);
		}
		for (String key : List.of("entity.minecraft.villager.daycare_keeper", "entity.minecraft.zombie_villager.daycare_keeper",
				"message.aliveworkplace.daycare_keeper.title", "message.aliveworkplace.daycare_keeper.left", "message.aliveworkplace.daycare_keeper.egg",
				"message.aliveworkplace.daycare_keeper.hatched", "message.aliveworkplace.daycare_keeper.and", "message.aliveworkplace.daycare_keeper.collected",
				"message.aliveworkplace.daycare_keeper.cant_afford", "message.aliveworkplace.daycare_keeper.taken_back",
				"screen.aliveworkplace.daycare_keeper", "screen.aliveworkplace.daycare_keeper.info", "screen.aliveworkplace.daycare_keeper.pairs",
				"screen.aliveworkplace.daycare_keeper.odds", "screen.aliveworkplace.daycare_keeper.eggs", "screen.aliveworkplace.daycare_keeper.no_eggs",
				"screen.aliveworkplace.daycare_keeper.collect", "screen.aliveworkplace.daycare_keeper.take_back",
				"screen.aliveworkplace.daycare_keeper.take_back.lore", "screen.aliveworkplace.daycare_keeper.collect_first",
				"screen.aliveworkplace.daycare_keeper.have_pair", "screen.aliveworkplace.daycare_keeper.full", "screen.aliveworkplace.daycare_keeper.last",
				"screen.aliveworkplace.daycare_keeper.pick", "screen.aliveworkplace.daycare_keeper.picked", "screen.aliveworkplace.daycare_keeper.with",
				"screen.aliveworkplace.daycare_keeper.chance", "station.aliveworkplace.item.aliveworkplace.daycare_keeper",
				"aliveworkplace.config.daycareKeepers", "aliveworkplace.config.daycareKeepers.tooltip")) {
			check(missing, key);
		}
		helper.assertTrue(missing.isEmpty(), "untranslated: " + missing);
		String odds = Component.translatable("screen.aliveworkplace.daycare_keeper.odds", 70, 50, 20).getString();
		helper.assertTrue(odds.equals("An egg at dawn: 70% very well, 50% well, 20% so-so"), "reads: " + odds);
		helper.succeed();
	}

	private static void check(List<String> missing, String key) {
		if (!Language.getInstance().has(key)) {
			missing.add(key);
		}
	}
}
