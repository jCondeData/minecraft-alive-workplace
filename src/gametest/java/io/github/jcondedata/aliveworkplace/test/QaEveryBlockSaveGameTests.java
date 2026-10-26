package io.github.jcondedata.aliveworkplace.test;

import io.github.jcondedata.aliveworkplace.registry.ModBlocks;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.IntArrayTag;
import net.minecraft.nbt.IntTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.LongTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;

/**
 * QA (lane E, saves, 2026-10-26): every block of the mod that keeps data in the chunk (its block entity: the Village
 * Hall with the whole village's record, the Storehouse, Mailbox, Drop Box, Shop Counter, Blueprint Table, Grave and
 * Harvest Idol). CLAUDE.md: "Don't break existing saves: new saved fields need defaults". A chunk reads a block's data
 * with {@link BlockEntity#loadStatic}, which drops the data whole (logs "Failed to load data for block entity") when its
 * load throws: for the Village Hall that is the village's treasury, rank, research, families and chronicle gone. So,
 * for each block:
 * <ul>
 * <li>a block saved before any of its fields existed (only its id and place) loads, and what the jar writes for it
 * reads back to the same save (a second save is the first);</li>
 * <li>each key it reads, holding a value of another kind (a key that changed kind between versions, a hand-edited or
 * NBT-edited chunk), still loads, and so does a list of entries whose own keys are of another kind.</li>
 * </ul>
 * The kind-check ({@link QaBlockSaveKindsGameTests}) calls the load directly (as loadStatic does inside its catch), so a
 * throw is reported with its key.
 */
public class QaEveryBlockSaveGameTests implements FabricGameTest {
	static final BlockPos AT = new BlockPos(1, 2, 1);

	/** Each of the mod's block entity types, by the block that has it. */
	static Map<BlockEntityType<?>, Block> blocks() {
		Map<BlockEntityType<?>, Block> all = new LinkedHashMap<>();
		all.put(ModBlocks.VILLAGE_HALL_ENTITY, ModBlocks.VILLAGE_HALL);
		all.put(ModBlocks.STOREHOUSE_ENTITY, ModBlocks.STOREHOUSE);
		all.put(ModBlocks.MAILBOX_ENTITY, ModBlocks.MAILBOX);
		all.put(ModBlocks.DROP_BOX_ENTITY, ModBlocks.DROP_BOX);
		all.put(ModBlocks.SHOP_COUNTER_ENTITY, ModBlocks.SHOP_COUNTER);
		all.put(ModBlocks.BLUEPRINT_TABLE_ENTITY, ModBlocks.BLUEPRINT_TABLE);
		all.put(ModBlocks.GRAVE_ENTITY, ModBlocks.GRAVE);
		all.put(ModBlocks.HARVEST_IDOL_ENTITY, ModBlocks.HARVEST_IDOL);
		return all;
	}

	/**
	 * The keys each block's load reads that a fresh block doesn't write (written only once there is something to keep:
	 * an owner, a disaster, an anthem), so they get a value of another kind too. Read from the loaders, 2026-10-26.
	 */
	static final Map<String, List<String>> MORE_KEYS = Map.of(
		"village_hall", List.of("CustomName", "anthemBook", "anthemInstrument", "anthemNotes", "atPeace", "ballDay", "ballTurn", "bannerBase",
			"bannerPatterns", "banquetDay", "banquetEaten", "berriesFound", "births", "blueprintUnlocks", "blueprintUnlocksSeeded", "chronicle",
			"classGraceUntil", "classesVersion", "disaster", "disasterEnds", "disasterSince", "edicts", "extraMeals", "fairBunting", "fairDay",
			"feastDay", "festivalCalled", "festivalCrowd", "festivalDay", "festivalMissed", "founderMade", "founderMoodDay", "founderTried",
			"founderWagonDay", "guilds", "habitat", "hornDay", "lastBirth", "lastDisasterDay", "lastMarketDay", "lastQuestDay", "lastRaidDay",
			"lastTaxDay", "moodRolledDay", "noMoodUntil", "owner", "ownerName", "peaceSince", "piece_looks", "plan", "pokedex", "protected",
			"quests", "questsDone", "raidWorkUntil", "rank", "reforms", "research", "rushUntil", "services", "servicesDay", "skyChart", "steward",
			"stewardPlaceChecked", "steward_desk", "treasury", "treasuryTotal", "wonderWedding", "bankDeposits", "bankWeek", "legendGuest",
			"legendRolled", "legendVisits", "seer", "families", "familiesNoted"),
		"storehouse", List.of("orders", "owner", "ownerName", "Items"),
		"mailbox", List.of("owner", "ownerName", "Items", "CustomName"),
		"drop_box", List.of("Items"),
		"shop_counter", List.of("owner", "ownerName", "sales", "totalDollars", "totalSales", "Items"),
		"blueprint_table", List.of("stored"),
		"grave", List.of("died", "funeral", "level", "name", "profession", "villager"),
		"harvest_idol", List.of());

	/** The keys an entry of a block's list reads (a chronicle line, a sale, a standing order, a sky chart find). */
	static final List<String> ENTRY_KEYS = List.of("text", "day", "kind", "buyer", "goods", "dollars", "paid", "item", "keep",
		"structure", "x", "y", "z", "id", "count", "components", "Slot", "region", "kindHour", "done", "week", "found", "player", "cents");

	/** Values of every other kind a key could be found holding. */
	static List<Tag> odd() {
		ListTag strings = new ListTag();
		strings.add(StringTag.valueOf("x"));
		return List.of(StringTag.valueOf("x"), StringTag.valueOf(""), IntTag.valueOf(-7), LongTag.valueOf(Long.MIN_VALUE), new CompoundTag(),
			strings, new ListTag(), new IntArrayTag(new int[] {1, 2, 3}), new IntArrayTag(new int[] {-1, 999, Integer.MIN_VALUE}));
	}

	/** A compound whose every entry key holds {@code value}; and a list of one such entry. */
	static CompoundTag entryOf(Tag value) {
		CompoundTag entry = new CompoundTag();
		for (String key : ENTRY_KEYS) {
			entry.put(key, value.copy());
		}
		return entry;
	}

	static String name(BlockEntityType<?> type) {
		return BuiltInRegistries.BLOCK_ENTITY_TYPE.getKey(type).getPath();
	}

	/** The block placed, its fresh data as a chunk saves it, then the block taken away again. */
	static CompoundTag placedSave(GameTestHelper helper, Block block) {
		helper.setBlock(AT, block);
		ServerLevel level = helper.getLevel();
		BlockEntity be = level.getBlockEntity(helper.absolutePos(AT));
		CompoundTag tag = be == null ? null : be.saveWithFullMetadata(level.registryAccess());
		helper.setBlock(AT, Blocks.AIR);
		return tag;
	}

	/** The load a chunk does, without its catch: null when it loaded, else what it threw. */
	static Throwable load(BlockEntityType<?> type, BlockState state, BlockPos pos, CompoundTag tag, HolderLookup.Provider registries) {
		BlockEntity fresh = type.create(pos, state);
		if (fresh == null) {
			return new IllegalStateException("the type made no block entity");
		}
		try {
			fresh.loadWithComponents(tag.copy(), registries);
			return null;
		} catch (Throwable e) {
			return e;
		}
	}

	/** Every block entity type the mod registers is on the list. */
	//$ gametest_batch 'FabricGameTest.EMPTY_STRUCTURE' '"qaBlockSaves"'
	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE, batch = "qaBlockSaves")
	public void everyBlockThatKeepsDataIsListed(GameTestHelper helper) {
		Set<String> registered = new LinkedHashSet<>();
		for (var id : BuiltInRegistries.BLOCK_ENTITY_TYPE.keySet()) {
			if (id.getNamespace().equals("aliveworkplace")) {
				registered.add(id.getPath());
			}
		}
		Set<String> listed = new LinkedHashSet<>();
		blocks().keySet().forEach(t -> listed.add(name(t)));
		helper.assertTrue(registered.equals(listed), "the mod registers " + registered + ", the test lists " + listed + " (add the new one)");
		helper.assertTrue(MORE_KEYS.keySet().equals(listed), "keys listed for " + MORE_KEYS.keySet());
		helper.succeed();
	}

	/**
	 * A block from before its fields (its id and place only) loads as a chunk loads it; and the save the jar writes for
	 * a fresh block reads back to the same save, twice.
	 */
	//$ gametest_batch 'FabricGameTest.EMPTY_STRUCTURE' '"qaBlockSaves"'
	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE, batch = "qaBlockSaves")
	public void aBlockFromBeforeLoadsAndSavesTheSameTwice(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		HolderLookup.Provider registries = level.registryAccess();
		BlockPos pos = helper.absolutePos(AT);
		List<String> wrong = new ArrayList<>();
		for (Map.Entry<BlockEntityType<?>, Block> e : blocks().entrySet()) {
			String name = name(e.getKey());
			BlockState state = e.getValue().defaultBlockState();
			CompoundTag bare = new CompoundTag();
			bare.putString("id", BuiltInRegistries.BLOCK_ENTITY_TYPE.getKey(e.getKey()).toString());
			bare.putInt("x", pos.getX());
			bare.putInt("y", pos.getY());
			bare.putInt("z", pos.getZ());
			BlockEntity old = BlockEntity.loadStatic(pos, state, bare, registries);
			if (old == null) {
				wrong.add(name + ": a block saved with only its id and place loads as nothing");
				continue;
			}
			CompoundTag placed = placedSave(helper, e.getValue());
			if (placed == null) {
				wrong.add(name + ": placed, it has no block entity");
				continue;
			}
			BlockEntity back = BlockEntity.loadStatic(pos, state, placed, registries);
			if (back == null) {
				wrong.add(name + ": its own fresh save " + placed + " loads as nothing");
				continue;
			}
			CompoundTag second = back.saveWithFullMetadata(registries);
			if (!placed.equals(second)) {
				wrong.add(name + ": saved " + placed + ", read back and saved again " + second);
			}
			CompoundTag fromOld = old.saveWithFullMetadata(registries);
			BlockEntity oldBack = BlockEntity.loadStatic(pos, state, fromOld, registries);
			if (oldBack == null || !fromOld.equals(oldBack.saveWithFullMetadata(registries))) {
				wrong.add(name + ": a block from before saves " + fromOld + " and doesn't read back the same");
			}
		}
		helper.assertTrue(wrong.isEmpty(), "blocks whose saves don't load or come back changed: " + wrong);
		helper.succeed();
	}
}
