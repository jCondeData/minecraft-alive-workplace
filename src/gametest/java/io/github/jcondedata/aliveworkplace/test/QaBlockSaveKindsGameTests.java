package io.github.jcondedata.aliveworkplace.test;

import static io.github.jcondedata.aliveworkplace.test.QaEveryBlockSaveGameTests.AT;

import io.github.jcondedata.aliveworkplace.hall.VillageHallBlockEntity;
import io.github.jcondedata.aliveworkplace.hall.VillageQuests;
import io.github.jcondedata.aliveworkplace.registry.ModBlocks;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;

/**
 * QA (lane E, saves, 2026-10-26), with {@link QaEveryBlockSaveGameTests}: a block's saved data with one key of another
 * kind still loads. A chunk drops a block's data whole when its load throws ({@link BlockEntity#loadStatic} logs
 * "Failed to load data for block entity" and returns nothing), so one odd key in a Village Hall's save would cost the
 * village its treasury, rank, research, families and chronicle.
 */
public class QaBlockSaveKindsGameTests implements FabricGameTest {
	/**
	 * Each key a block reads, holding a value of another kind (a word, a number, an empty or odd list, an empty
	 * compound, an int array with out-of-range numbers), and each list key holding an entry whose own keys are all of
	 * another kind: the block still loads.
	 */
	//$ gametest_batch 'FabricGameTest.EMPTY_STRUCTURE' '"qaBlockSaves"'
	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE, batch = "qaBlockSaves")
	public void aKeyOfAnotherKindDoesNotLoseTheBlocksData(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		HolderLookup.Provider registries = level.registryAccess();
		BlockPos pos = helper.absolutePos(AT);
		List<String> wrong = new ArrayList<>();
		int tried = 0;
		for (Map.Entry<BlockEntityType<?>, Block> e : QaEveryBlockSaveGameTests.blocks().entrySet()) {
			String name = QaEveryBlockSaveGameTests.name(e.getKey());
			BlockState state = e.getValue().defaultBlockState();
			CompoundTag saved = QaEveryBlockSaveGameTests.placedSave(helper, e.getValue());
			if (saved == null) {
				wrong.add(name + ": placed, it has no block entity");
				continue;
			}
			Set<String> keys = new LinkedHashSet<>(saved.getAllKeys());
			keys.addAll(QaEveryBlockSaveGameTests.MORE_KEYS.get(name));
			keys.removeAll(List.of("id", "x", "y", "z"));
			for (String key : keys) {
				List<Tag> values = new ArrayList<>();
				for (Tag odd : QaEveryBlockSaveGameTests.odd()) {
					Tag had = saved.get(key);
					if (had == null || !had.equals(odd)) {
						values.add(odd);
					}
				}
				for (Tag odd : QaEveryBlockSaveGameTests.odd()) {
					ListTag entries = new ListTag();
					entries.add(QaEveryBlockSaveGameTests.entryOf(odd));
					values.add(entries);
					values.add(QaEveryBlockSaveGameTests.entryOf(odd));
				}
				for (Tag value : values) {
					CompoundTag tag = saved.copy();
					tag.put(key, value);
					tried++;
					Throwable thrown = QaEveryBlockSaveGameTests.load(e.getKey(), state, pos, tag, registries);
					if (thrown != null && wrong.size() < 40) {
						wrong.add(name + " with " + key + " = " + value + ": " + thrown);
					}
				}
			}
		}
		helper.assertTrue(tried > 500, "only " + tried + " odd saves were tried");
		helper.assertTrue(wrong.isEmpty(), wrong.size() + " block saves lost to one key of another kind: " + wrong);
		helper.succeed();
	}

	/**
	 * A Village Hall with 5.00 emeralds in its treasury and two quests on its board, one of them of a kind this jar
	 * doesn't know (a world opened with an older jar after a newer one added a kind, or one edited by hand), loaded as
	 * its chunk loads it: the hall keeps its treasury and the quest it knows; only the unknown quest is left out.
	 */
	//$ gametest_batch 'FabricGameTest.EMPTY_STRUCTURE' '"qaBlockSaves"'
	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE, batch = "qaBlockSaves")
	public void aQuestOfAnUnknownKindDoesNotCostTheVillageItsHall(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		HolderLookup.Provider registries = level.registryAccess();
		helper.setBlock(AT, ModBlocks.VILLAGE_HALL);
		BlockPos pos = helper.absolutePos(AT);
		VillageHallBlockEntity hall = (VillageHallBlockEntity) level.getBlockEntity(pos);
		hall.setTreasury(500);
		VillageQuests.Quest bring = new VillageQuests.Quest(new UUID(1, 2), VillageQuests.Kind.BRING, "minecraft:wheat", 12, 3, 4, 100L, "Dara", Optional.empty());
		VillageQuests.Quest other = new VillageQuests.Quest(new UUID(3, 4), VillageQuests.Kind.SLAY, "minecraft:air", 5, 0, 6, 100L, "Bram", Optional.empty());
		hall.setQuests(List.of(bring, other));
		CompoundTag tag = hall.saveWithFullMetadata(registries);
		ListTag quests = tag.getList("quests", Tag.TAG_COMPOUND);
		helper.assertTrue(quests.size() == 2, "the hall saved " + quests.size() + " quests, not 2: " + tag.get("quests"));
		quests.getCompound(1).putString("kind", "TRAIN");
		helper.setBlock(AT, net.minecraft.world.level.block.Blocks.AIR);

		BlockEntity back = BlockEntity.loadStatic(pos, ModBlocks.VILLAGE_HALL.defaultBlockState(), tag, registries);
		helper.assertTrue(back instanceof VillageHallBlockEntity, "the hall's data was dropped whole for a quest of kind TRAIN: loaded " + back);
		VillageHallBlockEntity loaded = (VillageHallBlockEntity) back;
		helper.assertTrue(loaded.treasury() == 500, "the treasury came back as " + loaded.treasury() + ", not 500");
		helper.assertTrue(loaded.quests().stream().anyMatch(q -> q.id().equals(bring.id()) && q.progress() == 3),
			"the BRING quest it knows was lost: " + loaded.quests());
		helper.succeed();
	}
}
