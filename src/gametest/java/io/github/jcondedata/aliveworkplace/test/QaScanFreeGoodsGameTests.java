package io.github.jcondedata.aliveworkplace.test;

import io.github.jcondedata.aliveworkplace.blueprint.BlueprintData;
import io.github.jcondedata.aliveworkplace.blueprint.BlueprintItem;
import io.github.jcondedata.aliveworkplace.build.BuildSite;
import io.github.jcondedata.aliveworkplace.build.BuildSiteManager;
import io.github.jcondedata.aliveworkplace.build.Builders;
import io.github.jcondedata.aliveworkplace.registry.ModBlocks;
import io.github.jcondedata.aliveworkplace.registry.ModGameRules;
import io.github.jcondedata.aliveworkplace.registry.ModItems;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.animal.Bee;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.AbstractFurnaceBlock;
import net.minecraft.world.level.block.BeehiveBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.ChiseledBookShelfBlock;
import net.minecraft.world.level.block.ComposterBlock;
import net.minecraft.world.level.block.CropBlock;
import net.minecraft.world.level.block.JukeboxBlock;
import net.minecraft.world.level.block.LayeredCauldronBlock;
import net.minecraft.world.level.block.LecternBlock;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.NetherWartBlock;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.SweetBerryBushBlock;
import net.minecraft.world.level.block.entity.BeehiveBlockEntity;
import net.minecraft.world.level.block.entity.ChiseledBookShelfBlockEntity;
import net.minecraft.world.level.block.entity.DecoratedPotBlockEntity;
import net.minecraft.world.level.block.entity.JukeboxBlockEntity;
import net.minecraft.world.level.block.entity.LecternBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * QA (lane-b-1025-1838), from the builder's promise (builder.md: "Builders never copy what is inside containers";
 * BlockEntityData: nothing that would hand out free items or mobs is copied) and the B429/B434/B449 specs, beyond the
 * cases their own tests cover. A player builds something, scans it with real Scan Tool clicks, and a real builder builds
 * the blueprint from a chest holding exactly the material list. What a player can take out of the build must have been
 * paid for, and what each block shows must be what it holds.
 */
public class QaScanFreeGoodsGameTests implements net.fabricmc.fabric.api.gametest.v1.FabricGameTest {
	private static final String HUGE = "aliveworkplace_test:huge_area";
	private static final Logger LOGGER = LoggerFactory.getLogger("aliveworkplace-qa");
	private static final BlockPos BENCH = new BlockPos(2, 2, 2);
	private static final BlockPos CHEST = new BlockPos(2, 2, 4);
	/** The scanned row runs east from here (the floor under it, y = 1, is the scan's lower corner). */
	private static final BlockPos SRC = new BlockPos(14, 2, 24);

	/** A scanned build, started by a builder with exactly its material list in a chest. */
	private record Job(ServerLevel level, BuildSite site, Map<Item, Integer> asked, String list, BlockPos origin, Villager[] builder) {
		boolean asks(Item item) {
			return asked.containsKey(item);
		}
	}

	static void aScannedChargedAnchorSnowCauldronAndLitFurnaceAreBuiltEmpty(GameTestHelper helper, boolean bugs) {
		Leftovers.clear(helper);
		Job job = scanAndBuild(helper, 4, 1, Rotation.NONE, () -> {
			helper.setBlock(SRC, Blocks.RESPAWN_ANCHOR.defaultBlockState().setValue(BlockStateProperties.RESPAWN_ANCHOR_CHARGES, 4));
			helper.setBlock(SRC.east(1), Blocks.POWDER_SNOW_CAULDRON.defaultBlockState().setValue(LayeredCauldronBlock.LEVEL, 3));
			helper.setBlock(SRC.east(2), Blocks.WATER_CAULDRON.defaultBlockState().setValue(LayeredCauldronBlock.LEVEL, 3));
			helper.setBlock(SRC.east(3), Blocks.FURNACE.defaultBlockState().setValue(AbstractFurnaceBlock.LIT, true));
		});
		helper.succeedWhen(() -> {
			done(helper, job);
			List<String> free = new ArrayList<>();
			BlockState anchor = level(job, Blocks.RESPAWN_ANCHOR, helper);
			if (anchor.getValue(BlockStateProperties.RESPAWN_ANCHOR_CHARGES) > 0 && !job.asks(Items.GLOWSTONE)) {
				free.add("a respawn anchor with " + anchor.getValue(BlockStateProperties.RESPAWN_ANCHOR_CHARGES) + " charges (free glowstone, free respawns)");
			}
			if (find(job, Blocks.POWDER_SNOW_CAULDRON) != null && !job.asks(Items.POWDER_SNOW_BUCKET)) {
				free.add("a powder snow cauldron (a powder snow bucket for the taking) from a plain cauldron");
			}
			helper.assertTrue(find(job, Blocks.CAULDRON) != null || find(job, Blocks.POWDER_SNOW_CAULDRON) != null, "no cauldron built for the powder snow one");
			helper.assertTrue(find(job, Blocks.WATER_CAULDRON) != null || find(job, Blocks.CAULDRON) != null, "no cauldron built for the water one");
			BlockPos furnace = find(job, Blocks.FURNACE);
			helper.assertTrue(furnace != null, "no furnace built");
			Container inside = (Container) job.level().getBlockEntity(furnace);
			if (bugs && job.level().getBlockState(furnace).getValue(AbstractFurnaceBlock.LIT) && inside.getItem(1).isEmpty()) {
				free.add("a furnace burning (and giving light) with no fuel in it");
			}
			helper.assertTrue(free.isEmpty(), "the builder gave away for the material list " + job.list() + ": " + free);
			LOGGER.info("qa scan free goods A: anchor, snow cauldron and furnace built as empty as paid for ({})", job.list());
		});
	}

	static void aScannedLecternAndJukeboxShowOnlyWhatTheyHold(GameTestHelper helper, boolean bugs) {
		Leftovers.clear(helper);
		Job job = scanAndBuild(helper, 2, 1, Rotation.NONE, () -> {
			helper.setBlock(SRC, Blocks.LECTERN.defaultBlockState().setValue(LecternBlock.HAS_BOOK, true));
			LecternBlockEntity lectern = helper.getBlockEntity(SRC);
			lectern.setBook(new ItemStack(Items.WRITABLE_BOOK));
			helper.setBlock(SRC.east(), Blocks.JUKEBOX.defaultBlockState().setValue(JukeboxBlock.HAS_RECORD, true));
			JukeboxBlockEntity jukebox = helper.getBlockEntity(SRC.east());
			jukebox.setTheItem(new ItemStack(Items.MUSIC_DISC_CAT));
			helper.assertTrue(lectern.hasBook() && !jukebox.getTheItem().isEmpty(), "the scanned lectern or jukebox holds nothing");
		});
		helper.succeedWhen(() -> {
			done(helper, job);
			List<String> wrong = new ArrayList<>();
			BlockPos lecternAt = find(job, Blocks.LECTERN);
			BlockPos jukeboxAt = find(job, Blocks.JUKEBOX);
			helper.assertTrue(lecternAt != null && jukeboxAt != null, "lectern or jukebox not built");
			LecternBlockEntity lectern = (LecternBlockEntity) job.level().getBlockEntity(lecternAt);
			JukeboxBlockEntity jukebox = (JukeboxBlockEntity) job.level().getBlockEntity(jukeboxAt);
			if (lectern.hasBook() && !job.asks(Items.WRITABLE_BOOK)) {
				wrong.add("a free book on the lectern");
			}
			if (!jukebox.getTheItem().isEmpty() && !job.asks(Items.MUSIC_DISC_CAT)) {
				wrong.add("a free music disc in the jukebox");
			}
			boolean showsBook = job.level().getBlockState(lecternAt).getValue(LecternBlock.HAS_BOOK);
			if (bugs && showsBook != lectern.hasBook()) {
				wrong.add("the lectern shows " + (showsBook ? "a book" : "no book") + " but holds " + (lectern.hasBook() ? "one" : "none"));
			}
			boolean showsDisc = job.level().getBlockState(jukeboxAt).getValue(JukeboxBlock.HAS_RECORD);
			if (bugs && showsDisc != !jukebox.getTheItem().isEmpty()) {
				wrong.add("the jukebox shows " + (showsDisc ? "a disc" : "no disc") + " but holds " + (jukebox.getTheItem().isEmpty() ? "none" : "one"));
			}
			helper.assertTrue(wrong.isEmpty(), "for the material list " + job.list() + ": " + wrong);
			LOGGER.info("qa scan free goods B: lectern and jukebox built empty and looking it");
		});
	}

	static void aScannedRipeHarvestIsNotGrownForFree(GameTestHelper helper, boolean bugs) {
		Leftovers.clear(helper);
		Job job = scanAndBuild(helper, 4, 3, Rotation.NONE, () -> {
			helper.setBlock(SRC, Blocks.FARMLAND);
			helper.setBlock(SRC.above(), Blocks.WHEAT.defaultBlockState().setValue(CropBlock.AGE, CropBlock.MAX_AGE));
			helper.setBlock(SRC.east(1), Blocks.SOUL_SAND);
			helper.setBlock(SRC.east(1).above(), Blocks.NETHER_WART.defaultBlockState().setValue(NetherWartBlock.AGE, NetherWartBlock.MAX_AGE));
			helper.setBlock(SRC.east(2), Blocks.DIRT);
			helper.setBlock(SRC.east(2).above(), Blocks.SWEET_BERRY_BUSH.defaultBlockState().setValue(SweetBerryBushBlock.AGE, SweetBerryBushBlock.MAX_AGE));
			helper.setBlock(SRC.east(3).above(2), Blocks.STONE);
			helper.setBlock(SRC.east(3).above(), Blocks.CAVE_VINES.defaultBlockState().setValue(BlockStateProperties.BERRIES, true));
		});
		helper.succeedWhen(() -> {
			done(helper, job);
			List<String> free = new ArrayList<>();
			BlockPos wheat = find(job, Blocks.WHEAT);
			BlockPos wart = find(job, Blocks.NETHER_WART);
			BlockPos bush = find(job, Blocks.SWEET_BERRY_BUSH);
			BlockPos vines = find(job, Blocks.CAVE_VINES);
			helper.assertTrue(wheat != null && wart != null && bush != null && vines != null, "not all planted: wheat " + wheat + ", wart " + wart + ", bush " + bush + ", vines " + vines);
			ServerLevel level = job.level();
			if (bugs && level.getBlockState(wheat).getValue(CropBlock.AGE) == CropBlock.MAX_AGE && !job.asks(Items.WHEAT)) {
				free.add("ripe wheat (wheat and seeds for the taking) from " + job.asked().getOrDefault(Items.WHEAT_SEEDS, 0) + " seed");
			}
			if (bugs && level.getBlockState(wart).getValue(NetherWartBlock.AGE) == NetherWartBlock.MAX_AGE) {
				free.add("ripe nether wart (2-4 wart) from " + job.asked().getOrDefault(Items.NETHER_WART, 0) + " wart");
			}
			if (bugs && level.getBlockState(bush).getValue(SweetBerryBushBlock.AGE) >= 2) {
				free.add("a sweet berry bush at age " + level.getBlockState(bush).getValue(SweetBerryBushBlock.AGE) + " (berries to pick) from "
					+ job.asked().getOrDefault(Items.SWEET_BERRIES, 0) + " berry");
			}
			if (bugs && level.getBlockState(vines).getValue(BlockStateProperties.BERRIES)) {
				free.add("cave vines with a glow berry to pick, planted from " + job.asked().getOrDefault(Items.GLOW_BERRIES, 0) + " glow berry");
			}
			helper.assertTrue(free.isEmpty(), "the builder gave away for the material list " + job.list() + ": " + free);
			LOGGER.info("qa scan free goods C: the field is planted, not ripe");
		});
	}

	static void aTurnedScanOfShelfNestAndPotHoldsNothing(GameTestHelper helper, boolean bugs) {
		Leftovers.clear(helper);
		Job job = scanAndBuild(helper, 3, 1, Rotation.CLOCKWISE_90, () -> {
			helper.setBlock(SRC, Blocks.CHISELED_BOOKSHELF.defaultBlockState().setValue(BlockStateProperties.HORIZONTAL_FACING, Direction.SOUTH));
			ChiseledBookShelfBlockEntity shelf = helper.getBlockEntity(SRC);
			shelf.setItem(0, new ItemStack(Items.ENCHANTED_BOOK));
			shelf.setItem(2, new ItemStack(Items.BOOK));
			shelf.setItem(4, new ItemStack(Items.WRITTEN_BOOK));
			helper.setBlock(SRC.east(1), Blocks.BEE_NEST.defaultBlockState().setValue(BeehiveBlock.FACING, Direction.SOUTH)
				.setValue(BeehiveBlock.HONEY_LEVEL, BeehiveBlock.MAX_HONEY_LEVELS));
			BeehiveBlockEntity nest = helper.getBlockEntity(SRC.east(1));
			for (int i = 0; i < 2; i++) {
				Bee bee = helper.spawn(EntityType.BEE, SRC.east(1).south());
				nest.addOccupant(bee);
			}
			helper.assertTrue(nest.getOccupantCount() == 2, "the scanned nest holds " + nest.getOccupantCount() + " bees, not 2");
			helper.setBlock(SRC.east(2), Blocks.DECORATED_POT);
			DecoratedPotBlockEntity pot = helper.getBlockEntity(SRC.east(2));
			pot.setTheItem(new ItemStack(Items.DIAMOND, 3));
		});
		helper.succeedWhen(() -> {
			done(helper, job);
			ServerLevel level = job.level();
			List<String> free = new ArrayList<>();
			BlockPos shelfAt = find(job, Blocks.CHISELED_BOOKSHELF);
			BlockPos nestAt = find(job, Blocks.BEE_NEST);
			BlockPos potAt = find(job, Blocks.DECORATED_POT);
			helper.assertTrue(shelfAt != null && nestAt != null && potAt != null, "not built: shelf " + shelfAt + ", nest " + nestAt + ", pot " + potAt);
			ChiseledBookShelfBlockEntity shelf = (ChiseledBookShelfBlockEntity) level.getBlockEntity(shelfAt);
			BlockState shelfState = level.getBlockState(shelfAt);
			int shown = 0;
			for (var slot : ChiseledBookShelfBlock.SLOT_OCCUPIED_PROPERTIES) {
				shown += shelfState.getValue(slot) ? 1 : 0;
			}
			if (shelf.count() > 0) {
				free.add("the shelf holds " + shelf.count() + " free books");
			}
			if (shown != shelf.count()) {
				free.add("the turned shelf shows " + shown + " books but holds " + shelf.count());
			}
			helper.assertTrue(shelfState.getValue(BlockStateProperties.HORIZONTAL_FACING) == Direction.WEST, "the shelf wasn't turned with the build: " + shelfState);
			BeehiveBlockEntity nest = (BeehiveBlockEntity) level.getBlockEntity(nestAt);
			if (bugs && nest.getOccupantCount() > 0) {
				net.minecraft.nbt.ListTag stored = nest.saveWithoutMetadata(level.registryAccess()).getList("bees", net.minecraft.nbt.Tag.TAG_COMPOUND);
				String data = stored.isEmpty() ? "?" : stored.getCompound(0).getCompound("entity_data").getAllKeys().size() + " keys of bee data, id "
					+ stored.getCompound(0).getCompound("entity_data").getString("id");
				free.add("the nest holds " + nest.getOccupantCount() + " bees (" + data + ")");
			}
			if (level.getBlockState(nestAt).getValue(BeehiveBlock.HONEY_LEVEL) > 0) {
				free.add("the nest has honey level " + level.getBlockState(nestAt).getValue(BeehiveBlock.HONEY_LEVEL));
			}
			int bees = level.getEntitiesOfClass(Bee.class, new net.minecraft.world.phys.AABB(job.origin()).inflate(12)).size();
			if (bugs && bees > 0) {
				free.add(bees + " bees came with the build");
			}
			DecoratedPotBlockEntity pot = (DecoratedPotBlockEntity) level.getBlockEntity(potAt);
			if (!pot.getTheItem().isEmpty()) {
				free.add("the pot holds " + pot.getTheItem());
			}
			helper.assertTrue(free.isEmpty(), "the builder gave away for the material list " + job.list() + ": " + free);
			LOGGER.info("qa scan free goods D: turned shelf, nest and pot built empty");
		});
	}

	static void aScannedBuildSavedAndLoadedMidBuildStillGivesNothing(GameTestHelper helper, boolean bugs) {
		Leftovers.clear(helper);
		Job job = scanAndBuild(helper, 8, 1, Rotation.NONE, () -> {
			for (int dx = 0; dx < 3; dx++) {
				helper.setBlock(SRC.east(dx), Blocks.COBBLESTONE);
			}
			helper.setBlock(SRC.east(3), Blocks.LAVA_CAULDRON);
			helper.setBlock(SRC.east(4), Blocks.COMPOSTER.defaultBlockState().setValue(ComposterBlock.LEVEL, ComposterBlock.READY));
			helper.setBlock(SRC.east(5), Blocks.RESPAWN_ANCHOR.defaultBlockState().setValue(BlockStateProperties.RESPAWN_ANCHOR_CHARGES, 4));
			helper.setBlock(SRC.east(6), Blocks.CHISELED_BOOKSHELF.defaultBlockState().setValue(BlockStateProperties.HORIZONTAL_FACING, Direction.SOUTH));
			ChiseledBookShelfBlockEntity shelf = helper.getBlockEntity(SRC.east(6));
			for (int i = 0; i < 6; i++) {
				shelf.setItem(i, new ItemStack(Items.BOOK));
			}
			helper.setBlock(SRC.east(7), Blocks.LECTERN.defaultBlockState().setValue(LecternBlock.HAS_BOOK, true));
			((LecternBlockEntity) helper.getBlockEntity(SRC.east(7))).setBook(new ItemStack(Items.WRITABLE_BOOK));
		});
		UUID id = job.site().id();
		BlockPos firstStone = job.origin().offset(0, 1, 0);
		boolean[] reloaded = {false};
		helper.onEachTick(() -> {
			BuildSite now = BuildSiteManager.get(job.level()).get(id);
			if (reloaded[0] || now == null || !job.level().getBlockState(firstStone).is(Blocks.COBBLESTONE)) {
				return;
			}
			reloaded[0] = true;
			helper.assertTrue(find(job, Blocks.LECTERN) == null, "the whole row went up before the reload: nothing left to build after it");
			job.builder()[0] = reload(helper, job.level(), job.builder()[0]);
			LOGGER.info("qa scan free goods E: saved and loaded mid-build at {}", now.stage());
		});
		helper.succeedWhen(() -> {
			helper.assertTrue(reloaded[0], "never reloaded mid-build");
			done(helper, job);
			ServerLevel level = job.level();
			List<String> free = new ArrayList<>();
			if (find(job, Blocks.LAVA_CAULDRON) != null && !job.asks(Items.LAVA_BUCKET)) {
				free.add("a lava cauldron from a plain cauldron");
			}
			BlockState composter = level(job, Blocks.COMPOSTER, helper);
			if (composter.getValue(ComposterBlock.LEVEL) > 0) {
				free.add("a composter at level " + composter.getValue(ComposterBlock.LEVEL));
			}
			BlockState anchor = level(job, Blocks.RESPAWN_ANCHOR, helper);
			if (anchor.getValue(BlockStateProperties.RESPAWN_ANCHOR_CHARGES) > 0) {
				free.add("a respawn anchor with " + anchor.getValue(BlockStateProperties.RESPAWN_ANCHOR_CHARGES) + " charges");
			}
			BlockPos shelfAt = find(job, Blocks.CHISELED_BOOKSHELF);
			BlockPos lecternAt = find(job, Blocks.LECTERN);
			helper.assertTrue(shelfAt != null && lecternAt != null, "shelf or lectern not built");
			int shown = 0;
			for (var slot : ChiseledBookShelfBlock.SLOT_OCCUPIED_PROPERTIES) {
				shown += level.getBlockState(shelfAt).getValue(slot) ? 1 : 0;
			}
			int held = ((ChiseledBookShelfBlockEntity) level.getBlockEntity(shelfAt)).count();
			if (held > 0 || shown > 0) {
				free.add("the shelf holds " + held + " and shows " + shown + " books");
			}
			LecternBlockEntity lectern = (LecternBlockEntity) level.getBlockEntity(lecternAt);
			if (lectern.hasBook()) {
				free.add("the lectern holds a free book");
			}
			if (bugs && level.getBlockState(lecternAt).getValue(LecternBlock.HAS_BOOK)) {
				free.add("the lectern shows a book it doesn't hold");
			}
			helper.assertTrue(free.isEmpty(), "after a save and load mid-build, for the material list " + job.list() + ": " + free);
			LOGGER.info("qa scan free goods E: reloaded build gave nothing away");
		});
	}

	// ---- the cases (bugs: also the checks QaScanFreeGoodsBugGameTests keeps, failing until the mod is fixed) ----

	//$ gametest_ticks_batch HUGE '2400' '"qaScanFreeA"'
	@GameTest(template = HUGE, timeoutTicks = 2400, batch = "qaScanFreeA")
	public void aScannedChargedAnchorSnowCauldronAndLitFurnaceAreBuiltEmpty(GameTestHelper helper) {
		aScannedChargedAnchorSnowCauldronAndLitFurnaceAreBuiltEmpty(helper, false);
	}

	//$ gametest_ticks_batch HUGE '2400' '"qaScanFreeB"'
	@GameTest(template = HUGE, timeoutTicks = 2400, batch = "qaScanFreeB")
	public void aScannedLecternAndJukeboxShowOnlyWhatTheyHold(GameTestHelper helper) {
		aScannedLecternAndJukeboxShowOnlyWhatTheyHold(helper, false);
	}

	//$ gametest_ticks_batch HUGE '2400' '"qaScanFreeC"'
	@GameTest(template = HUGE, timeoutTicks = 2400, batch = "qaScanFreeC")
	public void aScannedRipeHarvestIsNotGrownForFree(GameTestHelper helper) {
		aScannedRipeHarvestIsNotGrownForFree(helper, false);
	}

	//$ gametest_ticks_batch HUGE '2400' '"qaScanFreeD"'
	@GameTest(template = HUGE, timeoutTicks = 2400, batch = "qaScanFreeD")
	public void aTurnedScanOfShelfNestAndPotHoldsNothing(GameTestHelper helper) {
		aTurnedScanOfShelfNestAndPotHoldsNothing(helper, false);
	}

	//$ gametest_ticks_batch HUGE '3600' '"qaScanFreeE"'
	@GameTest(template = HUGE, timeoutTicks = 3600, batch = "qaScanFreeE")
	public void aScannedBuildSavedAndLoadedMidBuildStillGivesNothing(GameTestHelper helper) {
		aScannedBuildSavedAndLoadedMidBuildStillGivesNothing(helper, false);
	}

	// ---- helpers ----

	/**
	 * Builds the source row (by {@code setup}, at {@link #SRC} eastward, {@code width} wide, {@code height} tall above the
	 * floor), scans it with real Scan Tool clicks (floor corner to top corner), clears it, and has a builder start the
	 * blueprint with exactly its material list in a chest, materials not free.
	 */
	private static Job scanAndBuild(GameTestHelper helper, int width, int height, Rotation rotation, Runnable setup) {
		ServerLevel level = helper.getLevel();
		setup.run();
		List<Component> seen = new ArrayList<>();
		ServerPlayer player = QaStageThreeGameTests.listener(helper, seen);
		player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(ModItems.SCAN_TOOL));
		player.getInventory().add(new ItemStack(ModItems.BLANK_BLUEPRINT, 1));
		QaStageThreeGameTests.click(helper, player, SRC.below());
		// (the top corner one column past the row, on air: a click on a lectern, jukebox or furnace would use that block instead)
		QaStageThreeGameTests.click(helper, player, SRC.east(width).above(height - 1));
		player.setShiftKeyDown(true);
		QaStageThreeGameTests.clickAir(helper, player);
		player.setShiftKeyDown(false);
		helper.assertTrue(QaStageThreeGameTests.last(seen).equals("message.aliveworkplace.scan.saved"), "the scan said " + QaStageThreeGameTests.last(seen));
		ItemStack blueprint = ItemStack.EMPTY;
		for (ItemStack s : player.getInventory().items) {
			if (s.is(ModItems.BLUEPRINT)) {
				blueprint = s;
			}
		}
		helper.assertTrue(!blueprint.isEmpty(), "the scan gave no blueprint");
		ResourceLocation id = BlueprintItem.data(blueprint).orElseThrow().structure();
		for (int dx = 0; dx < width; dx++) {
			for (int dy = 0; dy < height; dy++) {
				helper.setBlock(SRC.east(dx).above(dy), Blocks.AIR);
			}
		}
		for (Entity e : level.getEntitiesOfClass(Bee.class, new net.minecraft.world.phys.AABB(helper.absolutePos(SRC)).inflate(4))) {
			e.discard();
		}

		boolean free = level.getGameRules().getBoolean(ModGameRules.FREE_MATERIALS);
		int delay = level.getGameRules().getInt(ModGameRules.BUILD_DELAY);
		boolean help = level.getGameRules().getBoolean(ModGameRules.BUILDERS_HELP);
		Leftovers.after(helper, () -> {
			level.getGameRules().getRule(ModGameRules.FREE_MATERIALS).set(free, level.getServer());
			level.getGameRules().getRule(ModGameRules.BUILD_DELAY).set(delay, level.getServer());
			level.getGameRules().getRule(ModGameRules.BUILDERS_HELP).set(help, level.getServer());
		});
		level.getGameRules().getRule(ModGameRules.FREE_MATERIALS).set(false, level.getServer());
		level.getGameRules().getRule(ModGameRules.BUILD_DELAY).set(2, level.getServer());
		level.getGameRules().getRule(ModGameRules.BUILDERS_HELP).set(false, level.getServer());
		helper.setDayTime(2000);
		helper.setBlock(BENCH, ModBlocks.BUILDERS_BENCH);
		Villager builder = helper.spawn(EntityType.VILLAGER, BENCH.east());
		Builders.employ(level, builder, helper.absolutePos(BENCH));
		BlockPos origin = helper.absolutePos(new BlockPos(10, 1, 10));
		BlueprintData.Placement placement = new BlueprintData.Placement(level.dimension().location(), origin, rotation, Mirror.NONE);
		BuildSite site = Builders.start(level, builder, null, id, placement);
		if (site == null) {
			throw new GameTestAssertException("the builder didn't take the scanned blueprint " + id);
		}
		Map<Item, Integer> asked = site.plan(level).materials();
		helper.setBlock(CHEST, Blocks.CHEST);
		Container chest = helper.getBlockEntity(CHEST);
		int slot = 0;
		for (Map.Entry<Item, Integer> e : asked.entrySet()) {
			int left = e.getValue();
			while (left > 0) {
				int n = Math.min(left, e.getKey().getDefaultMaxStackSize());
				chest.setItem(slot++, new ItemStack(e.getKey(), n));
				left -= n;
			}
		}
		String list = asked.entrySet().stream().map(e -> e.getValue() + " " + BuiltInRegistries.ITEM.getKey(e.getKey()).getPath()).toList().toString();
		return new Job(level, site, asked, list, origin, new Villager[] {builder});
	}

	private static void done(GameTestHelper helper, Job job) {
		BuildSite now = BuildSiteManager.get(job.level()).get(job.site().id());
		helper.assertTrue(now == null, "still building: " + (now == null ? "" : now.stage() + " " + now.status() + " missing " + now.missing()));
	}

	/** Where {@code block} was built in the build's box (any turn), or null. */
	private static BlockPos find(Job job, Block block) {
		for (BlockPos p : BlockPos.betweenClosed(job.origin().offset(-10, 0, -10), job.origin().offset(10, 4, 10))) {
			if (job.level().getBlockState(p).is(block)) {
				return p.immutable();
			}
		}
		return null;
	}

	private static BlockState level(Job job, Block block, GameTestHelper helper) {
		BlockPos at = find(job, block);
		helper.assertTrue(at != null, "no " + BuiltInRegistries.BLOCK.getKey(block).getPath() + " built");
		return job.level().getBlockState(at);
	}

	/** As a save and a load of the world would: every build site from its save, and the builder from its save. */
	private static Villager reload(GameTestHelper helper, ServerLevel level, Villager old) {
		BuildSiteManager loaded = BuildSiteManager.load(BuildSiteManager.get(level).save(new CompoundTag(), level.registryAccess()), level.registryAccess());
		level.getDataStorage().set(BuildSiteManager.NAME, loaded);
		CompoundTag tag = new CompoundTag();
		helper.assertTrue(old.save(tag), "the builder wasn't saved");
		old.discard();
		Entity back = EntityType.loadEntityRecursive(tag, level, e -> e);
		helper.assertTrue(back instanceof Villager, "the builder didn't load as a villager: " + back);
		level.addFreshEntity(back);
		return (Villager) back;
	}
}
