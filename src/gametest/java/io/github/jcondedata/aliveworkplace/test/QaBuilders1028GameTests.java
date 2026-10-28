package io.github.jcondedata.aliveworkplace.test;

import static io.github.jcondedata.aliveworkplace.test.StationsSpecGameTests.listeningPlayer;

import io.github.jcondedata.aliveworkplace.build.BuilderBag;
import io.github.jcondedata.aliveworkplace.furnish.FurnitureChoice;
import io.github.jcondedata.aliveworkplace.furnish.FurnitureSpot;
import io.github.jcondedata.aliveworkplace.hall.HallJobs;
import io.github.jcondedata.aliveworkplace.hall.HallPeople;
import io.github.jcondedata.aliveworkplace.hall.HallSections;
import io.github.jcondedata.aliveworkplace.hall.HallSections.Section;
import io.github.jcondedata.aliveworkplace.hall.VillageHalls;
import io.github.jcondedata.aliveworkplace.mine.Miners;
import io.github.jcondedata.aliveworkplace.mine.QuarrySite;
import io.github.jcondedata.aliveworkplace.mine.QuarrySiteManager;
import io.github.jcondedata.aliveworkplace.registry.ModAttachments;
import io.github.jcondedata.aliveworkplace.registry.ModBlocks;
import io.github.jcondedata.aliveworkplace.registry.ModGameRules;
import io.github.jcondedata.aliveworkplace.registry.ModVillagers;
import io.github.jcondedata.aliveworkplace.work.ChoiceMenu;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.SectionPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.village.poi.PoiManager;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.npc.VillagerProfession;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BedPart;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.levelgen.structure.BoundingBox;

/**
 * QA (lane-b-1028-0338) for the builders' landed bug fixes, from their specs: B635 (a built Carpenter's Workshop's
 * crafting table is a free Carpenter block in the Village Hall, also in an older save), B229 (every block the Miner digs
 * reaches the Storehouse) and B166 (a furnished home's bed spot only keeps a bed villagers sleep in, also from a save).
 * Every hall check goes through the hall's own clicks (the block right-clicked, People, Job ▾, a job picked).
 */
public class QaBuilders1028GameTests implements net.fabricmc.fabric.api.gametest.v1.FabricGameTest {
	private static final String HUGE = "aliveworkplace_test:huge_area";
	private static final String BUILD = "aliveworkplace_test:build_area";
	private static final BlockPos HALL = new BlockPos(15, 2, 24);
	private static final BlockPos TABLE = new BlockPos(15, 2, 19);

	// --- B635: the hall and the Carpenter's crafting table ----------------------------------------------------------

	private static void village(GameTestHelper helper, int radius) {
		Leftovers.clear(helper);
		HallSections.forget();
		HallJobs.forget();
		int was = VillageHalls.RADIUS;
		VillageHalls.RADIUS = radius;
		Leftovers.after(helper, () -> VillageHalls.RADIUS = was);
		helper.setBlock(HALL, ModBlocks.VILLAGE_HALL);
	}

	private static Villager jobless(GameTestHelper helper, BlockPos at, String name) {
		Villager v = helper.spawn(EntityType.VILLAGER, at);
		v.setNoAi(true);
		v.setVillagerData(v.getVillagerData().setProfession(VillagerProfession.NONE));
		v.setCustomName(Component.literal(name));
		return v;
	}

	private static ChoiceMenu openPeople(GameTestHelper helper, ServerPlayer player) {
		BlockPos hall = helper.absolutePos(HALL);
		player.teleportTo(hall.getX() + 1.5, hall.getY(), hall.getZ() + 0.5);
		helper.useBlock(HALL, player);
		helper.assertTrue(player.containerMenu instanceof ChoiceMenu, "the hall didn't open: " + player.containerMenu);
		ChoiceMenu menu = (ChoiceMenu) player.containerMenu;
		helper.assertTrue(menu.clickMenuButton(player, Section.PEOPLE.ordinal()), "the sidebar didn't take People");
		return menu;
	}

	private static int slotOf(GameTestHelper helper, ChoiceMenu menu, Villager villager) {
		for (int slot = 0; slot < ChoiceMenu.SIZE; slot++) {
			CompoundTag row = HallPeople.row(menu.icon(slot));
			if (row != null && row.getString("uuid").equals(villager.getStringUUID())) {
				return slot;
			}
		}
		helper.fail("no row for " + villager.getName().getString());
		return -1;
	}

	private static int id(VillagerProfession job) {
		return BuiltInRegistries.VILLAGER_PROFESSION.getId(job);
	}

	/** Job ▾ opened on {@code villager}'s row: the Carpenter line's text and state. */
	private static String[] carpenterLine(GameTestHelper helper, ServerPlayer player, ChoiceMenu menu, Villager villager) {
		helper.assertTrue(menu.clickMenuButton(player, HallJobs.OPEN + slotOf(helper, menu, villager)), "Job ▾ wasn't taken");
		List<ItemStack> lines = HallJobs.lines(menu.icon(slotOf(helper, menu, villager)));
		for (ItemStack line : lines) {
			CompoundTag mark = HallJobs.mark(line);
			if (mark != null && mark.getInt("id") == id(ModVillagers.CARPENTER) && !mark.getString("state").equals("header")) {
				return new String[] {line.getHoverName().getString(), mark.getString("state")};
			}
		}
		helper.fail("no Carpenter line in " + lines.stream().map(l -> l.getHoverName().getString()).toList());
		return null;
	}

	private static Optional<BlockPos> site(Villager villager) {
		return villager.getBrain().getMemory(MemoryModuleType.JOB_SITE).map(GlobalPos::pos);
	}

	private static boolean listedAsCarpenter(ServerLevel level, BlockPos hall, BlockPos table) {
		return VillageHalls.freeStations(level, hall).stream()
			.anyMatch(f -> f.pos().equals(table) && f.profession() == ModVillagers.CARPENTER);
	}

	/**
	 * The block broken mid-job: Job ▾ is open on a jobless villager's row with "Carpenter · 1 free Crafting Table", then
	 * the table is broken before the player clicks Carpenter. The click must not make a Carpenter whose workplace is air,
	 * and Job ▾ opened again says to place a Crafting Table.
	 */
	//$ gametest_ticks_batch HUGE '200' '"qaB635Broken"'
	@GameTest(template = HUGE, timeoutTicks = 200, batch = "qaB635Broken")
	public void qaB635TheTableBrokenBeforeThePickMakesNoCarpenter(GameTestHelper helper) {
		village(helper, 8);
		ServerLevel level = helper.getLevel();
		helper.setBlock(TABLE, Blocks.CRAFTING_TABLE);
		Villager pip = jobless(helper, new BlockPos(12, 2, 22), "Pip");
		List<String> seen = new ArrayList<>();
		ServerPlayer player = listeningPlayer(helper, seen);
		helper.runAfterDelay(3, () -> {
			BlockPos table = helper.absolutePos(TABLE);
			ChoiceMenu menu = openPeople(helper, player);
			String[] line = carpenterLine(helper, player, menu, pip);
			helper.assertTrue(line[0].equals("Carpenter · 1 free Crafting Table") && line[1].equals("free"),
				"before the break, the Carpenter line: " + line[0] + " " + line[1]);
			level.destroyBlock(table, true);
			helper.assertTrue(level.getPoiManager().getType(table).isEmpty(), "the broken table still has its workstation record");
			helper.assertTrue(!listedAsCarpenter(level, helper.absolutePos(HALL), table), "the broken table is still listed free");
			menu.clickMenuButton(player, HallJobs.PICK + id(ModVillagers.CARPENTER));
			helper.assertTrue(pip.getVillagerData().getProfession() != ModVillagers.CARPENTER,
				"Pip was made a Carpenter at a broken table: " + pip.getVillagerData() + " site " + site(pip) + "; told " + seen);
			helper.assertTrue(site(pip).isEmpty(), "Pip has a workplace at air: " + site(pip));
			ChoiceMenu again = openPeople(helper, player);
			String[] after = carpenterLine(helper, player, again, pip);
			helper.assertTrue(after[0].equals("Carpenter · place a Crafting Table first") && after[1].equals("no_block"),
				"after the break, the Carpenter line: " + after[0] + " " + after[1]);
			player.closeContainer();
			helper.succeed();
		});
	}

	/**
	 * An older save: a Carpenter's Workshop's crafting table that was saved without its workstation record (a section
	 * whose records the game had marked complete before 21.1a). When the chunk loads, the table is a free Carpenter block
	 * in the hall, and one click in Job ▾ makes a jobless villager the Carpenter there.
	 */
	//$ gametest_ticks_batch HUGE '200' '"qaB635OldSave"'
	@GameTest(template = HUGE, timeoutTicks = 200, batch = "qaB635OldSave")
	public void qaB635AnOldSavesTableIsAFreeCarpenterBlockAfterTheChunkLoads(GameTestHelper helper) {
		village(helper, 8);
		ServerLevel level = helper.getLevel();
		helper.setBlock(TABLE, Blocks.CRAFTING_TABLE);
		// Another workstation in the same chunk section (most houses have a bed or one): its records are complete.
		BlockPos bed = null;
		for (BlockPos p : BlockPos.betweenClosed(TABLE.offset(-3, 0, -3), TABLE.offset(3, 0, 3))) {
			if (!p.equals(TABLE) && SectionPos.of(helper.absolutePos(p)).equals(SectionPos.of(helper.absolutePos(TABLE)))) {
				bed = p.immutable();
				break;
			}
		}
		helper.assertTrue(bed != null, "setup: no spot beside the table in its chunk section");
		helper.setBlock(bed, Blocks.COMPOSTER);
		BlockPos composter = bed;
		Villager pip = jobless(helper, new BlockPos(12, 2, 22), "Pip");
		List<String> seen = new ArrayList<>();
		ServerPlayer player = listeningPlayer(helper, seen);
		helper.runAfterDelay(3, () -> {
			BlockPos table = helper.absolutePos(TABLE);
			BlockPos hall = helper.absolutePos(HALL);
			PoiManager poi = level.getPoiManager();
			helper.assertTrue(level.getPoiManager().getType(helper.absolutePos(composter)).isPresent(), "setup: the composter has no record");
			poi.remove(table); // saved before the table was a workstation
			helper.assertTrue(!listedAsCarpenter(level, hall, table), "setup: the table without a record is listed");
			LevelChunk chunk = level.getChunkAt(table);
			poi.checkConsistencyWithBlocks(SectionPos.of(table), chunk.getSection(chunk.getSectionIndex(table.getY()))); // the chunk loads
			helper.assertTrue(listedAsCarpenter(level, hall, table), "the old table isn't a free Carpenter block after the chunk loads: "
				+ VillageHalls.freeStations(level, hall));
			ChoiceMenu menu = openPeople(helper, player);
			String[] line = carpenterLine(helper, player, menu, pip);
			helper.assertTrue(line[0].equals("Carpenter · 1 free Crafting Table") && line[1].equals("free"),
				"the Carpenter line: " + line[0] + " " + line[1]);
			helper.assertTrue(menu.clickMenuButton(player, HallJobs.PICK + id(ModVillagers.CARPENTER)), "the pick wasn't taken");
			helper.assertTrue(pip.getVillagerData().getProfession() == ModVillagers.CARPENTER && site(pip).equals(Optional.of(table)),
				"Pip isn't the Carpenter at the old table: " + pip.getVillagerData() + " " + site(pip));
			helper.assertTrue(seen.contains("Pip is now a Carpenter"), "not told: " + seen);
			player.closeContainer();
			helper.succeed();
		});
	}

	/**
	 * Boundary from both sides: the hall's village reaches 8 blocks. A table 8 blocks from the hall is a free Carpenter
	 * block; one 9 blocks away isn't, and Job ▾ offers only the near one ("1 free Crafting Table").
	 */
	//$ gametest_ticks_batch HUGE '200' '"qaB635Edge"'
	@GameTest(template = HUGE, timeoutTicks = 200, batch = "qaB635Edge")
	public void qaB635OnlyTablesWithinTheVillageAreOffered(GameTestHelper helper) {
		village(helper, 8);
		ServerLevel level = helper.getLevel();
		BlockPos near = HALL.offset(0, 0, -8);
		BlockPos far = HALL.offset(-9, 0, 0);
		helper.setBlock(near, Blocks.CRAFTING_TABLE);
		helper.setBlock(far, Blocks.CRAFTING_TABLE);
		Villager pip = jobless(helper, new BlockPos(12, 2, 22), "Pip");
		ServerPlayer player = listeningPlayer(helper, new ArrayList<>());
		helper.runAfterDelay(3, () -> {
			BlockPos hall = helper.absolutePos(HALL);
			helper.assertTrue(listedAsCarpenter(level, hall, helper.absolutePos(near)), "the table 8 blocks away isn't listed");
			helper.assertTrue(!listedAsCarpenter(level, hall, helper.absolutePos(far)), "the table 9 blocks away is listed");
			String[] line = carpenterLine(helper, player, openPeople(helper, player), pip);
			helper.assertTrue(line[0].equals("Carpenter · 1 free Crafting Table") && line[1].equals("free"),
				"the Carpenter line: " + line[0] + " " + line[1]);
			player.closeContainer();
			helper.succeed();
		});
	}

	// --- B229: every block the Miner digs reaches the Storehouse -------------------------------------------------------

	private static final BlockPos BENCH = new BlockPos(2, 2, 2);
	private static final BlockPos MINER = new BlockPos(3, 2, 3);
	private static final BlockPos MIN = new BlockPos(6, 2, 6);
	private static final BlockPos MAX = new BlockPos(9, 4, 9);
	private static final BlockPos STOREHOUSE = new BlockPos(14, 2, 14);
	private static final BlockPos STORE_CHEST = new BlockPos(14, 2, 12);
	private static final BlockPos STORE_BARREL = new BlockPos(12, 2, 14);

	private static long stored(GameTestHelper helper, Item item) {
		long n = 0;
		for (BlockPos c : new BlockPos[] {STORE_CHEST, STORE_BARREL}) {
			n += ((Container) helper.getBlockEntity(c)).countItem(item);
		}
		return n;
	}

	/**
	 * A Miner with no chests of their own and a Storehouse across the area, saved and loaded twice while digging, each
	 * time with finds in the bag: every block dug still reaches the Storehouse, none on the ground, none lost with the bag.
	 */
	//$ gametest_ticks_batch BUILD '5000' '"qaB229Reload"'
	@GameTest(template = BUILD, timeoutTicks = 5000, batch = "qaB229Reload")
	public void qaB229EveryDugBlockReachesTheStorehouseAcrossReloads(GameTestHelper helper) {
		minerToStorehouse(helper, false, 2);
	}

	/**
	 * The village's own Village Hall stands in the area (B229's cause was another village's hall in reach): the Miner's
	 * finds go to this hall's Storehouse, every one of them.
	 */
	//$ gametest_ticks_batch BUILD '5000' '"qaB229OwnHall"'
	@GameTest(template = BUILD, timeoutTicks = 5000, batch = "qaB229OwnHall")
	public void qaB229WithItsOwnHallEveryDugBlockReachesTheHallsStorehouse(GameTestHelper helper) {
		minerToStorehouse(helper, true, 0);
	}

	private static void minerToStorehouse(GameTestHelper helper, boolean hall, int wanted) {
		Leftovers.clear(helper);
		Leftovers.halls(helper);
		Leftovers.storehouses(helper);
		ServerLevel level = helper.getLevel();
		helper.setDayTime(2000);
		int delay = level.getGameRules().getInt(ModGameRules.BUILD_DELAY);
		Leftovers.after(helper, () -> level.getGameRules().getRule(ModGameRules.BUILD_DELAY).set(delay, level.getServer()));
		level.getGameRules().getRule(ModGameRules.BUILD_DELAY).set(2, level.getServer());
		Map<Item, Integer> expect = new LinkedHashMap<>();
		Block[] rocks = {Blocks.STONE, Blocks.ANDESITE, Blocks.DIRT, Blocks.COBBLESTONE, Blocks.GRANITE};
		int i = 0;
		for (BlockPos p : BlockPos.betweenClosed(MIN, MAX)) {
			Block block = p.equals(new BlockPos(7, 3, 7)) ? Blocks.IRON_ORE : rocks[i++ % rocks.length];
			helper.setBlock(p, block);
			Item drop = block == Blocks.STONE ? Items.COBBLESTONE : block == Blocks.IRON_ORE ? Items.RAW_IRON : block.asItem();
			expect.merge(drop, 1, Integer::sum);
		}
		if (hall) {
			helper.setBlock(new BlockPos(2, 2, 14), ModBlocks.VILLAGE_HALL);
		}
		helper.setBlock(STOREHOUSE, ModBlocks.STOREHOUSE);
		helper.setBlock(STORE_CHEST, Blocks.CHEST);
		helper.setBlock(STORE_BARREL, Blocks.BARREL);
		helper.setBlock(BENCH, Blocks.BLAST_FURNACE);
		Villager first = helper.spawn(EntityType.VILLAGER, MINER);
		Miners.employ(level, first, helper.absolutePos(BENCH));
		ModAttachments.BUILDER_BAG.getOrCreate(first).setSlots(4);
		first.setItemSlot(net.minecraft.world.entity.EquipmentSlot.MAINHAND, new ItemStack(Items.IRON_PICKAXE));
		BoundingBox box = BoundingBox.fromCorners(helper.absolutePos(MIN), helper.absolutePos(MAX));
		QuarrySite site = Miners.start(level, first, null, box, box.getYSpan());
		site.setStairs(false);
		Villager[] miner = {first};
		int[] reloads = {0};
		Runnable reload = () -> {
			if (QuarrySiteManager.get(level).get(site.id()) == null) {
				return; // already done
			}
			BuilderBag bag = ModAttachments.BUILDER_BAG.getOrCreate(miner[0]);
			CompoundTag tag = new CompoundTag();
			helper.assertTrue(miner[0].save(tag), "the miner wasn't saved");
			miner[0].discard();
			Entity loaded = EntityType.loadEntityRecursive(tag, level, e -> e);
			helper.assertTrue(loaded instanceof Villager, "the save didn't load as a villager");
			level.addFreshEntity(loaded);
			miner[0] = (Villager) loaded;
			reloads[0]++;
			LoggerHolder.LOG.info("[QA B229] reload {}: the bag was {} at the save", reloads[0], bag.isEmpty() ? "empty" : "carrying finds");
		};
		// Saved and loaded while the bag holds finds, at least 40 ticks apart, {@code wanted} times.
		long[] last = {0};
		helper.onEachTick(() -> {
			long now = helper.getTick();
			if (reloads[0] < wanted && now - last[0] >= 40 && !ModAttachments.BUILDER_BAG.getOrCreate(miner[0]).isEmpty()) {
				last[0] = now;
				reload.run();
			}
		});
		helper.succeedWhen(() -> {
			helper.assertTrue(QuarrySiteManager.get(level).get(site.id()) == null,
				"still digging: " + Math.round(site.progress() * 100) + "% (" + site.status() + ")");
			helper.assertTrue(reloads[0] == wanted, "reloaded " + reloads[0] + " times with finds in the bag, not " + wanted + ": the test proves less");
			for (Map.Entry<Item, Integer> e : expect.entrySet()) {
				long have = stored(helper, e.getKey());
				int bag = ModAttachments.BUILDER_BAG.getOrCreate(miner[0]).count(e.getKey());
				int ground = 0;
				for (net.minecraft.world.entity.item.ItemEntity drop : level.getEntitiesOfClass(net.minecraft.world.entity.item.ItemEntity.class,
					helper.getBounds().inflate(32))) {
					if (drop.getItem().is(e.getKey())) {
						ground += drop.getItem().getCount();
					}
				}
				helper.assertTrue(have == e.getValue(), "the Storehouse holds " + have + " " + e.getKey() + ", dug " + e.getValue()
					+ " (bag " + bag + ", on the ground " + ground + ", reloads " + reloads[0] + ", hall " + hall + ")");
			}
			LoggerHolder.LOG.info("[QA B229] every dug block is in the Storehouse (hall {}, {} reloads): {}", hall, reloads[0], expect);
		});
	}

	private static final class LoggerHolder {
		static final org.slf4j.Logger LOG = org.slf4j.LoggerFactory.getLogger("aliveworkplace-qa");
	}

	// --- B166: a bed spot keeps only a bed villagers sleep in, also from a save --------------------------------------

	private static Map<BlockPos, BlockState> bed(Block block) {
		Map<BlockPos, BlockState> cells = new LinkedHashMap<>();
		cells.put(BlockPos.ZERO, block.defaultBlockState().setValue(BedBlock.FACING, Direction.NORTH).setValue(BedBlock.PART, BedPart.FOOT));
		cells.put(BlockPos.ZERO.north(), block.defaultBlockState().setValue(BedBlock.FACING, Direction.NORTH).setValue(BedBlock.PART, BedPart.HEAD));
		return cells;
	}

	/**
	 * A saved furnished home whose bed spot holds no bed a villager sleeps in (a mod's bed that isn't a home, or a
	 * foot without its head) loads with that spot vanilla, while a real bed spot and a chair spot load as saved.
	 */
	//$ gametest EMPTY_STRUCTURE
	@GameTest(template = EMPTY_STRUCTURE)
	public void qaB166ASavedBedSpotLoadsOnlyWithABedVillagersSleepIn(GameTestHelper helper) {
		Map<Integer, FurnitureChoice.Placed> spots = new LinkedHashMap<>();
		spots.put(0, new FurnitureChoice.Placed(new BlockPos(1, 1, 1), FurnitureSpot.Kind.BED, "minecraft", bed(Blocks.RED_BED)));
		Map<BlockPos, BlockState> footOnly = new LinkedHashMap<>(bed(Blocks.BLUE_BED));
		footOnly.remove(BlockPos.ZERO.north());
		spots.put(1, new FurnitureChoice.Placed(new BlockPos(4, 1, 1), FurnitureSpot.Kind.BED, "somemod", footOnly));
		spots.put(2, new FurnitureChoice.Placed(new BlockPos(7, 1, 1), FurnitureSpot.Kind.BED, "somemod",
			Map.of(BlockPos.ZERO, Blocks.WHITE_WOOL.defaultBlockState())));
		spots.put(3, new FurnitureChoice.Placed(new BlockPos(9, 1, 1), FurnitureSpot.Kind.CHAIR, "somemod",
			Map.of(BlockPos.ZERO, Blocks.OAK_STAIRS.defaultBlockState())));
		FurnitureChoice saved = new FurnitureChoice(spots);
		ListTag tag = saved.save();
		helper.assertTrue(tag.size() == 4, "not every spot was saved: " + tag.size());
		FurnitureChoice loaded = FurnitureChoice.load(tag);
		helper.assertTrue(loaded.spots().containsKey(0) && loaded.spots().get(0).cells().equals(bed(Blocks.RED_BED)),
			"the vanilla bed spot didn't load as saved: " + loaded.spots().get(0));
		helper.assertTrue(!loaded.spots().containsKey(1), "a bed's foot alone loaded as a bed spot: " + loaded.spots().get(1));
		helper.assertTrue(!loaded.spots().containsKey(2), "a bed spot of wool loaded: " + loaded.spots().get(2));
		helper.assertTrue(loaded.spots().containsKey(3), "the chair spot was dropped: only bed spots need a sleepable bed");
		helper.assertTrue(FurnitureChoice.sleepable(bed(Blocks.RED_BED)) && !FurnitureChoice.sleepable(footOnly),
			"sleepable: a whole vanilla bed, not a foot");
		helper.succeed();
	}

	// --- B197 and B648: the blueprint checks' model of the game, checked against the game --------------------------------

	private static double top(BlockState state) {
		return state.getCollisionShape(net.minecraft.world.level.EmptyBlockGetter.INSTANCE, BlockPos.ZERO).max(Direction.Axis.Y);
	}

	private static double bottom(BlockState state) {
		return state.getCollisionShape(net.minecraft.world.level.EmptyBlockGetter.INSTANCE, BlockPos.ZERO).min(Direction.Axis.Y);
	}

	/**
	 * B197: check.py flags a step that leaves a villager under 1.95 blocks below the ceiling. Its numbers are checked here
	 * against the game's own: a villager's height, how high a carpet, a bed, a bottom slab and a shut bottom trapdoor lift
	 * the feet, and how low a top slab, a shut top trapdoor and a hanging lantern reach into the cell under them. So a
	 * carpet under a ceiling two blocks over the floor really leaves 1.94 (flagged), a bottom slab under one three up 2.5.
	 */
	//$ gametest EMPTY_STRUCTURE
	@GameTest(template = EMPTY_STRUCTURE)
	public void qaB197TheHeadroomRulesNumbersAreTheGames(GameTestHelper helper) {
		List<String> wrong = new ArrayList<>();
		java.util.function.BiConsumer<String, double[]> same = (what, v) -> {
			if (Math.abs(v[0] - v[1]) > 1e-6) {
				wrong.add(what + ": the game says " + v[0] + ", check.py " + v[1]);
			}
		};
		same.accept("a villager's height", new double[] {EntityType.VILLAGER.getHeight(), 1.95});
		same.accept("a carpet's top", new double[] {top(Blocks.RED_CARPET.defaultBlockState()), 1 / 16.0});
		same.accept("a bed's top", new double[] {top(Blocks.RED_BED.defaultBlockState()), 9 / 16.0});
		same.accept("a bottom slab's top", new double[] {top(Blocks.OAK_SLAB.defaultBlockState()), 0.5});
		same.accept("a shut bottom trapdoor's top", new double[] {top(Blocks.OAK_TRAPDOOR.defaultBlockState()), 3 / 16.0});
		same.accept("3 layers of snow's top", new double[] {top(Blocks.SNOW.defaultBlockState()
			.setValue(net.minecraft.world.level.block.SnowLayerBlock.LAYERS, 3)), 2 / 8.0});
		same.accept("a top slab's underside", new double[] {bottom(Blocks.OAK_SLAB.defaultBlockState()
			.setValue(net.minecraft.world.level.block.SlabBlock.TYPE, net.minecraft.world.level.block.state.properties.SlabType.TOP)), 0.5});
		same.accept("a shut top trapdoor's underside", new double[] {bottom(Blocks.OAK_TRAPDOOR.defaultBlockState()
			.setValue(net.minecraft.world.level.block.TrapDoorBlock.HALF, net.minecraft.world.level.block.state.properties.Half.TOP)), 13 / 16.0});
		same.accept("a hanging lantern's underside", new double[] {bottom(Blocks.LANTERN.defaultBlockState()
			.setValue(net.minecraft.world.level.block.LanternBlock.HANGING, true)), 1 / 16.0});
		helper.assertTrue(top(Blocks.OAK_TRAPDOOR.defaultBlockState().setValue(net.minecraft.world.level.block.TrapDoorBlock.OPEN, true)
			.setValue(net.minecraft.world.level.block.TrapDoorBlock.FACING, Direction.NORTH)) == 1.0, "an open trapdoor isn't a wall-side hatch");
		helper.assertTrue(2 - 1 / 16.0 < EntityType.VILLAGER.getHeight() && 3 - 0.5 >= EntityType.VILLAGER.getHeight(),
			"the rule's two examples: a carpet under 2 blocks too low, a bottom slab under 3 high enough");
		helper.assertTrue(wrong.isEmpty(), "check.py's headroom numbers aren't the game's: " + wrong);
		helper.succeed();
	}

	/** B648GameTests.ALLOWED (the ends meant to stand free, with their reason): its record is private, so read by reflection. */
	private static boolean allowedEnd(String path, BlockPos pos) {
		try {
			for (Object a : B648GameTests.ALLOWED) {
				java.lang.reflect.Method end = a.getClass().getDeclaredMethod("pathEnd");
				java.lang.reflect.Method at = a.getClass().getDeclaredMethod("pos");
				end.setAccessible(true);
				at.setAccessible(true);
				if (path.endsWith((String) end.invoke(a)) && at.invoke(a).equals(pos)) {
					return true;
				}
			}
			return false;
		} catch (ReflectiveOperationException e) {
			throw new IllegalStateException("couldn't read B648GameTests.ALLOWED: " + e);
		}
	}

	/**
	 * B648 by the game's own rules: check.py and B648GameTests read the joins the blueprint kit stored, but the game works
	 * a pane's joins out again from its neighbours as it is placed (a pane joins another pane, iron bars, a wall or a
	 * sturdy face). Here every shipped structure's panes and bars are joined as the game joins them: none may end up joined
	 * on one side only with air on the other (but for B648GameTests' listed ends). A Wonder's stage parts are left out: each
	 * is built on the stages before it, so its glass meets walls another file holds.
	 */
	//$ gametest EMPTY_STRUCTURE
	@GameTest(template = EMPTY_STRUCTURE)
	public void qaB648NoShippedGlassEndsInTheAirAsTheGameJoinsIt(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		Map<net.minecraft.resources.ResourceLocation, net.minecraft.server.packs.resources.Resource> files = level.getServer().getResourceManager()
			.listResources("structure", id -> id.getNamespace().equals("aliveworkplace") && id.getPath().endsWith(".nbt"));
		var blocksLookup = level.holderLookup(net.minecraft.core.registries.Registries.BLOCK);
		List<String> problems = new ArrayList<>();
		int structures = 0;
		int panes = 0;
		java.util.Set<String> unknown = new java.util.TreeSet<>();
		int wonderParts = 0;
		for (Map.Entry<net.minecraft.resources.ResourceLocation, net.minecraft.server.packs.resources.Resource> e : files.entrySet()) {
			String path = e.getKey().getPath().substring("structure/".length(), e.getKey().getPath().length() - ".nbt".length());
			CompoundTag tag;
			try (java.io.InputStream in = e.getValue().open()) {
				tag = net.minecraft.nbt.NbtIo.readCompressed(in, net.minecraft.nbt.NbtAccounter.unlimitedHeap());
			} catch (Exception ex) {
				throw new IllegalStateException("couldn't read " + e.getKey() + ": " + ex);
			}
			if (path.startsWith("wonder/")) {
				// A Wonder's stage is saved in parts, each built standing on the stages before it (a rose window set in an
				// earlier stage's wall): its glass can't be judged one file alone. check.py checks the stages together.
				wonderParts++;
				continue;
			}
			structures++;
			ListTag palette = tag.getList("palette", net.minecraft.nbt.Tag.TAG_COMPOUND);
			List<BlockState> states = new ArrayList<>();
			for (int i = 0; i < palette.size(); i++) {
				BlockState state = net.minecraft.nbt.NbtUtils.readBlockState(blocksLookup, palette.getCompound(i));
				String name = palette.getCompound(i).getString("Name");
				if (state.isAir() && !name.endsWith(":air") && !name.endsWith(":cave_air")) {
					state = Blocks.STONE.defaultBlockState(); // another mod's block, not loaded here: a building block, a pane's jamb
					unknown.add(name);
				}
				states.add(state);
			}
			Map<BlockPos, BlockState> blocks = new java.util.HashMap<>();
			for (net.minecraft.nbt.Tag t : tag.getList("blocks", net.minecraft.nbt.Tag.TAG_COMPOUND)) {
				CompoundTag block = (CompoundTag) t;
				ListTag pos = block.getList("pos", net.minecraft.nbt.Tag.TAG_INT);
				BlockState state = states.get(block.getInt("state"));
				if (!state.isAir() && !state.is(Blocks.STRUCTURE_VOID)) {
					blocks.put(new BlockPos(pos.getInt(0), pos.getInt(1), pos.getInt(2)), state);
				}
			}
			net.minecraft.world.level.BlockGetter getter = new net.minecraft.world.level.BlockGetter() {
				@Override
				public net.minecraft.world.level.block.entity.BlockEntity getBlockEntity(BlockPos pos) {
					return null;
				}

				@Override
				public BlockState getBlockState(BlockPos pos) {
					return blocks.getOrDefault(pos, Blocks.AIR.defaultBlockState());
				}

				@Override
				public net.minecraft.world.level.material.FluidState getFluidState(BlockPos pos) {
					return getBlockState(pos).getFluidState();
				}

				@Override
				public int getHeight() {
					return 512;
				}

				@Override
				public int getMinBuildHeight() {
					return -256;
				}
			};
			for (Map.Entry<BlockPos, BlockState> b : blocks.entrySet()) {
				if (!(b.getValue().getBlock() instanceof net.minecraft.world.level.block.IronBarsBlock bars)) {
					continue;
				}
				panes++;
				List<Direction> joined = new ArrayList<>();
				for (Direction d : Direction.Plane.HORIZONTAL) {
					BlockPos n = b.getKey().relative(d);
					BlockState other = getter.getBlockState(n);
					if (bars.attachsTo(other, other.isFaceSturdy(getter, n, d.getOpposite()))) {
						joined.add(d);
					}
				}
				if (joined.size() != 1) {
					continue;
				}
				Direction open = joined.get(0).getOpposite();
				BlockPos at = b.getKey();
				if (!getter.getBlockState(at.relative(open)).isAir()
					|| allowedEnd(path, at)) {
					continue;
				}
				problems.add(path + ": " + BuiltInRegistries.BLOCK.getKey(b.getValue().getBlock()).getPath() + " at " + at.toShortString()
					+ " joins only to the " + joined.get(0).getName() + " (to " + BuiltInRegistries.BLOCK.getKey(getter.getBlockState(at.relative(joined.get(0))).getBlock()).getPath()
					+ "), air to the " + open.getName());
			}
		}
		LoggerHolder.LOG.info("[QA B648] by the game's joins: {} structures ({} Wonder parts left out), {} panes and bars ({} other mods' blocks read as stone), {} ending in the air{}",
			structures, wonderParts, panes, unknown.size(), problems.size(), problems.isEmpty() ? "" : ": " + problems);
		helper.assertTrue(structures > 300, "only " + structures + " structures read");
		helper.assertTrue(panes > 1000, "only " + panes + " panes and bars read");
		helper.assertTrue(problems.isEmpty(), "B648: " + problems.size() + " panes or bars end in the air as the game joins them, e.g. "
			+ problems.subList(0, Math.min(8, problems.size())));
		helper.succeed();
	}
}
