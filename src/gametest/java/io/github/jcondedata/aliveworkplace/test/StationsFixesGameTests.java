package io.github.jcondedata.aliveworkplace.test;

import io.github.jcondedata.aliveworkplace.registry.ModAttachments;
import io.github.jcondedata.aliveworkplace.registry.ModBlocks;
import io.github.jcondedata.aliveworkplace.registry.ModVillagers;
import io.github.jcondedata.aliveworkplace.work.Jobs;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.village.poi.PoiManager;
import net.minecraft.world.entity.ai.village.poi.PoiType;
import net.minecraft.world.entity.ai.village.poi.PoiTypes;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.npc.VillagerProfession;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.storage.ChunkSerializer;
import net.minecraft.world.level.chunk.storage.RegionStorageInfo;

/**
 * The tester's second round on ROADMAP 21.1a: variants of the three fixed findings (old worlds' new workstations, the
 * shepherd's shears, beekeepers and their hives) that the bug tests don't cover. Clicks go through the server's packet
 * handler ({@link StationsSpecGameTests#rightClick}).
 */
public class StationsFixesGameTests implements net.fabricmc.fabric.api.gametest.v1.FabricGameTest {
	private static final String AREA = "aliveworkplace_test:big_area";
	private static final BlockPos STATION = new BlockPos(3, 2, 3);
	private static final BlockPos STANDING = new BlockPos(4, 2, 4);

	/** {@code at}, or the nearest spot along x (in the area) with no other block within 2 of it (their composters go beside them). */
	private static BlockPos clearOf(Map<BlockPos, Block> taken, BlockPos at) {
		BlockPos spot = at;
		for (int step = 0; step < 20 && clash(taken, spot); step++) {
			spot = new BlockPos(2 + (spot.getX() + 3 - 2) % 17, spot.getY(), spot.getZ());
		}
		return spot;
	}

	private static boolean clash(Map<BlockPos, Block> taken, BlockPos spot) {
		for (BlockPos t : taken.keySet()) {
			if (t.distManhattan(spot) <= 2 || near(t, spot)) {
				return true;
			}
		}
		return false;
	}

	private static boolean near(BlockPos a, BlockPos b) {
		return Math.abs(a.getX() - b.getX()) <= 2 && Math.abs(a.getY() - b.getY()) <= 1 && Math.abs(a.getZ() - b.getZ()) <= 1;
	}

	/** A spot in the big area (x 1-20, y 2-17, z 1-20) whose position inside its chunk section is {@code rel} on every axis. */
	private static BlockPos sectionCorner(GameTestHelper helper, int rel) {
		return inSection(helper, rel, rel, rel);
	}

	/** A spot in the big area (x 1-20, y 2-17, z 1-20) at ({@code rx}, {@code ry}, {@code rz}) inside its chunk section. */
	private static BlockPos inSection(GameTestHelper helper, int rx, int ry, int rz) {
		int x = 1;
		int y = 2;
		int z = 1;
		while ((helper.absolutePos(new BlockPos(x, 2, 1)).getX() & 15) != rx) {
			x++;
		}
		while ((helper.absolutePos(new BlockPos(1, y, 1)).getY() & 15) != ry) {
			y++;
		}
		while ((helper.absolutePos(new BlockPos(1, 2, z)).getZ() & 15) != rz) {
			z++;
		}
		return new BlockPos(x, y, z);
	}

	/**
	 * The old-world fix, through the game's real chunk load: a Crafting Table, Jukebox, Mailbox and Blueprint Table without
	 * a record (as 0.137.0 saved them), each in a chunk section that already holds a workstation, two of them in the
	 * sections' far corners; the chunk is saved and read back with the game's own chunk reader, the only place loading a
	 * chunk checks its workstations. Each gets its record, of the right kind, and no record appears where there is no
	 * such block; a crafting table a carpenter already works at keeps its record and stays taken.
	 */
	//$ gametest AREA
	@GameTest(template = AREA)
	public void oldBlocksBecomeWorkstationsThroughTheRealChunkReader(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		PoiManager poi = level.getPoiManager();
		Map<BlockPos, Block> blocks = new LinkedHashMap<>();
		blocks.put(sectionCorner(helper, 15), Blocks.CRAFTING_TABLE);
		blocks.put(sectionCorner(helper, 0), Blocks.JUKEBOX);
		blocks.put(inSection(helper, 6, 9, 0), Blocks.CRAFTING_TABLE); // on a section's first row, at an odd height
		// The two anywhere-spots step aside from the section spots (which move with where the test area lands: once the
		// third landed on the mailbox's spot).
		blocks.put(clearOf(blocks, new BlockPos(8, 5, 12)), ModBlocks.MAILBOX);
		blocks.put(clearOf(blocks, new BlockPos(12, 7, 8)), ModBlocks.BLUEPRINT_TABLE);
		Map<Block, ResourceKey<PoiType>> kinds = Map.of(Blocks.CRAFTING_TABLE, ModVillagers.CRAFTING_TABLE_POI, Blocks.JUKEBOX,
			ModVillagers.JUKEBOX_POI, ModBlocks.MAILBOX, ModVillagers.MAILBOX_POI, ModBlocks.BLUEPRINT_TABLE, ModVillagers.BLUEPRINT_TABLE_POI);
		helper.assertTrue(blocks.size() == 5, "setup: two blocks at one spot " + blocks.keySet());
		Set<ChunkPos> chunks = new LinkedHashSet<>();
		for (Map.Entry<BlockPos, Block> e : blocks.entrySet()) {
			BlockPos at = helper.absolutePos(e.getKey());
			// A composter beside it, inside the same chunk section: that section's records are complete as far as the game knows.
			BlockPos mate = (at.getX() & 15) == 15 ? e.getKey().west() : e.getKey().east();
			helper.setBlock(mate, Blocks.COMPOSTER);
			helper.setBlock(e.getKey(), e.getValue());
			helper.assertTrue(SectionPos.of(at).equals(SectionPos.of(helper.absolutePos(mate))), "setup: not one chunk section");
			poi.remove(at); // 0.137.0 had no point of interest for this block
			helper.assertTrue(poi.getType(at).isEmpty() && poi.getType(helper.absolutePos(mate)).isPresent(), "setup: records");
			chunks.add(new ChunkPos(at));
		}
		// A crafting table a carpenter works at, whose record is there: it must stay one record, and stay taken.
		BlockPos takenLocal = new BlockPos(16, 3, 16);
		BlockPos taken = helper.absolutePos(takenLocal);
		helper.setBlock(takenLocal, Blocks.CRAFTING_TABLE);
		Villager carpenter = helper.spawn(EntityType.VILLAGER, new BlockPos(16, 2, 17));
		Jobs.employ(level, carpenter, taken, ModVillagers.CRAFTING_TABLE_POI, ModVillagers.CARPENTER);
		helper.assertTrue(poi.getFreeTickets(taken) == 0, "setup: the carpenter's crafting table has free places: " + poi.getFreeTickets(taken));
		chunks.add(new ChunkPos(taken));

		for (ChunkPos pos : chunks) {
			LevelChunk chunk = level.getChunk(pos.x, pos.z);
			CompoundTag saved = ChunkSerializer.write(level, chunk);
			ListTag sections = saved.getList("sections", Tag.TAG_COMPOUND);
			for (int i = 0; i < sections.size(); i++) {
				sections.getCompound(i).remove("BlockLight"); // (the live chunk's light is left alone)
				sections.getCompound(i).remove("SkyLight");
			}
			ChunkSerializer.read(level, poi, new RegionStorageInfo("test", level.dimension(), "chunk"), pos, saved); // the chunk loads
		}
		List<String> wrong = new ArrayList<>();
		for (Map.Entry<BlockPos, Block> e : blocks.entrySet()) {
			BlockPos at = helper.absolutePos(e.getKey());
			String what = BuiltInRegistries.BLOCK.getKey(e.getValue()) + " at section spot " + (at.getX() & 15) + "," + (at.getY() & 15) + ","
				+ (at.getZ() & 15);
			Optional<ResourceKey<PoiType>> kind = poi.getType(at).flatMap(h -> h.unwrapKey());
			if (!kind.equals(Optional.of(kinds.get(e.getValue())))) {
				wrong.add(what + ": " + kind);
			}
		}
		helper.assertTrue(wrong.isEmpty(), "after the chunk loaded, old blocks without the right workstation record: " + wrong);
		List<String> phantoms = new ArrayList<>();
		poi.getInSquare(h -> kinds.values().stream().anyMatch(h::is), helper.absolutePos(new BlockPos(10, 8, 10)), 40, PoiManager.Occupancy.ANY)
			.forEach(r -> {
				if (PoiTypes.forState(level.getBlockState(r.getPos())).filter(h -> h.equals(r.getPoiType())).isEmpty()) {
					phantoms.add(r.getPoiType().unwrapKey().map(k -> k.location().toString()).orElse("?") + " at " + r.getPos() + " on "
						+ BuiltInRegistries.BLOCK.getKey(level.getBlockState(r.getPos()).getBlock()));
				}
			});
		helper.assertTrue(phantoms.isEmpty(), "workstation records where there is no such block: " + phantoms);
		helper.assertTrue(poi.getFreeTickets(taken) == 0, "the carpenter's crafting table became free on load: " + poi.getFreeTickets(taken));
		helper.assertTrue(poi.getType(taken).flatMap(h -> h.unwrapKey()).equals(Optional.of(ModVillagers.CRAFTING_TABLE_POI)),
			"the carpenter's crafting table record changed: " + poi.getType(taken));
		helper.succeed();
	}

	/**
	 * README: hiring is as before, the vanilla job's item on a villager who already has that job. The items that also pick
	 * a job at another block (shears and a glass bottle pick the Beekeeper, a glass bottle the Cleric, an iron ingot the
	 * Toolsmith and the Weaponsmith) still hire when a free block of that other kind stands right by the villager, and
	 * the villager keeps their job and block.
	 */
	//$ gametest AREA
	@GameTest(template = AREA)
	public void hiringItemsStillHireWithOtherJobsBlocksInReach(GameTestHelper helper) {
		record Hire(Block block, ResourceKey<PoiType> poi, VillagerProfession job, Item item) {
		}
		List<Hire> hires = List.of(
			new Hire(Blocks.LOOM, PoiTypes.SHEPHERD, VillagerProfession.SHEPHERD, Items.SHEARS),
			new Hire(Blocks.BREWING_STAND, PoiTypes.CLERIC, VillagerProfession.CLERIC, Items.GLASS_BOTTLE),
			new Hire(Blocks.SMITHING_TABLE, PoiTypes.TOOLSMITH, VillagerProfession.TOOLSMITH, Items.IRON_INGOT),
			new Hire(Blocks.GRINDSTONE, PoiTypes.WEAPONSMITH, VillagerProfession.WEAPONSMITH, Items.IRON_INGOT),
			new Hire(Blocks.BLAST_FURNACE, PoiTypes.ARMORER, VillagerProfession.ARMORER, Items.COAL),
			new Hire(Blocks.FLETCHING_TABLE, PoiTypes.FLETCHER, VillagerProfession.FLETCHER, Items.FLINT),
			new Hire(Blocks.SMOKER, PoiTypes.BUTCHER, VillagerProfession.BUTCHER, Items.LEAD),
			new Hire(Blocks.LECTERN, PoiTypes.LIBRARIAN, VillagerProfession.LIBRARIAN, Items.LAPIS_LAZULI),
			new Hire(Blocks.CARTOGRAPHY_TABLE, PoiTypes.CARTOGRAPHER, VillagerProfession.CARTOGRAPHER, Items.COMPASS));
		// Free blocks where these items pick another job, all within reach of the villager.
		helper.setBlock(new BlockPos(5, 2, 3), Blocks.BEEHIVE);
		helper.setBlock(new BlockPos(5, 2, 5), Blocks.BREWING_STAND);
		helper.setBlock(new BlockPos(3, 2, 5), Blocks.SMITHING_TABLE);
		helper.setBlock(new BlockPos(2, 2, 5), Blocks.GRINDSTONE);
		ServerPlayer player = StationsSpecGameTests.player(helper);
		List<String> wrong = new ArrayList<>();
		for (Hire hire : hires) {
			helper.setBlock(STATION, hire.block());
			Villager villager = helper.spawn(EntityType.VILLAGER, STANDING);
			Jobs.employ(helper.getLevel(), villager, helper.absolutePos(STATION), hire.poi(), hire.job());
			StationsSpecGameTests.rightClick(player, villager, new ItemStack(hire.item()), true);
			String what = StationsSpecGameTests.name(hire.job()) + " with " + BuiltInRegistries.ITEM.getKey(hire.item());
			if (ModAttachments.BUILDER_EMPLOYER.get(villager) == null) {
				wrong.add(what + ": not hired");
			}
			if (StationsSpecGameTests.job(villager) != hire.job()
				|| !StationsSpecGameTests.site(villager).equals(Optional.of(helper.absolutePos(STATION)))) {
				wrong.add(what + ": now " + StationsSpecGameTests.name(StationsSpecGameTests.job(villager)) + " at " + StationsSpecGameTests.site(villager));
			}
			villager.discard();
			helper.setBlock(STATION, Blocks.AIR);
		}
		helper.assertTrue(wrong.isEmpty(), wrong.toString());
		helper.succeed();
	}

	/**
	 * A beehive and a bee nest, three villagers by them, each sneak-right-clicked with a glass bottle or shears: the first
	 * two become beekeepers, one at each (a nest counts as a hive); the third (shears) is told a Beehive is needed and
	 * stays jobless. Later, both beekeepers still have their own.
	 */
	//$ gametest_ticks_batch AREA '400' '"stationsFixesThreeByTwoHives"'
	@GameTest(template = AREA, timeoutTicks = 400, batch = "stationsFixesThreeByTwoHives")
	public void threeVillagersByAHiveAndANest(GameTestHelper helper) {
		Leftovers.clear(helper);
		helper.setDayTime(2000);
		helper.setBlock(STATION, Blocks.BEEHIVE);
		helper.setBlock(new BlockPos(6, 2, 3), Blocks.BEE_NEST);
		List<String> seen = new ArrayList<>();
		ServerPlayer player = StationsSpecGameTests.listeningPlayer(helper, seen);
		Villager first = helper.spawn(EntityType.VILLAGER, STANDING);
		Villager second = helper.spawn(EntityType.VILLAGER, new BlockPos(5, 2, 4));
		Villager third = helper.spawn(EntityType.VILLAGER, new BlockPos(4, 2, 5));
		StationsSpecGameTests.rightClick(player, first, new ItemStack(Items.GLASS_BOTTLE), true);
		StationsSpecGameTests.rightClick(player, second, new ItemStack(Items.SHEARS), true);
		seen.clear();
		StationsSpecGameTests.rightClick(player, third, new ItemStack(Items.SHEARS), true);
		Optional<BlockPos> a = StationsSpecGameTests.site(first);
		Optional<BlockPos> b = StationsSpecGameTests.site(second);
		helper.assertTrue(StationsSpecGameTests.job(first) == ModVillagers.BEEKEEPER && StationsSpecGameTests.job(second) == ModVillagers.BEEKEEPER,
			"the first two should be beekeepers: " + StationsSpecGameTests.name(StationsSpecGameTests.job(first)) + ", "
				+ StationsSpecGameTests.name(StationsSpecGameTests.job(second)));
		helper.assertTrue(a.isPresent() && b.isPresent() && !a.equals(b), "the two beekeepers should have one hive each: " + a + ", " + b);
		helper.assertTrue(StationsSpecGameTests.job(third) == VillagerProfession.NONE,
			"a third beekeeper by two taken hives: " + StationsSpecGameTests.name(StationsSpecGameTests.job(third)) + " at " + StationsSpecGameTests.site(third));
		// Both hives in reach are taken: the message says a free one is needed (round 2's wording finding).
		helper.assertTrue(seen.stream().anyMatch(s -> s.startsWith("The Beekeeper job needs a free Beehive")),
			"the player wasn't told a hive is needed; saw: " + seen);
		helper.runAfterDelay(300, () -> {
			helper.assertTrue(StationsSpecGameTests.site(first).equals(a) && StationsSpecGameTests.site(second).equals(b),
				"a beekeeper lost their hive: first " + StationsSpecGameTests.site(first) + " (was " + a + "), second "
					+ StationsSpecGameTests.site(second) + " (was " + b + ")");
			helper.succeed();
		});
	}

	/**
	 * Decision (1) of 21.1a: our workers take a free block of their kind again by themselves, as vanilla workers do. The
	 * yardstick: an orchard keeper whose composter is moved takes the new one.
	 */
	//$ gametest_ticks_batch AREA '800' '"stationsFixesComposterMoved"'
	@GameTest(template = AREA, timeoutTicks = 800, batch = "stationsFixesComposterMoved")
	public void anOrchardKeeperWhoseComposterMovedTakesTheNewOne(GameTestHelper helper) {
		Leftovers.clear(helper);
		helper.setDayTime(2000);
		helper.setBlock(STATION, Blocks.COMPOSTER);
		Villager keeper = helper.spawn(EntityType.VILLAGER, STANDING);
		StationsSpecGameTests.rightClick(StationsSpecGameTests.player(helper), keeper, new ItemStack(Items.SWEET_BERRIES), true);
		helper.assertTrue(StationsSpecGameTests.job(keeper) == ModVillagers.ORCHARD_KEEPER, "setup: not an orchard keeper");
		helper.setBlock(STATION, Blocks.AIR);
		BlockPos moved = new BlockPos(7, 2, 6);
		helper.setBlock(moved, Blocks.COMPOSTER);
		helper.succeedWhen(() -> helper.assertTrue(StationsSpecGameTests.site(keeper).equals(Optional.of(helper.absolutePos(moved)))
			&& StationsSpecGameTests.job(keeper) == ModVillagers.ORCHARD_KEEPER,
			"the orchard keeper works at " + StationsSpecGameTests.site(keeper) + " as " + StationsSpecGameTests.name(StationsSpecGameTests.job(keeper))));
	}
}
