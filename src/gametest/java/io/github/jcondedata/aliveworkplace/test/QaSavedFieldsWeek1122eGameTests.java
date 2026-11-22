package io.github.jcondedata.aliveworkplace.test;

import io.github.jcondedata.aliveworkplace.blueprint.Blueprint;
import io.github.jcondedata.aliveworkplace.blueprint.BlueprintData;
import io.github.jcondedata.aliveworkplace.build.BuildSiteManager;
import io.github.jcondedata.aliveworkplace.build.MaterialRules;
import io.github.jcondedata.aliveworkplace.build.Refreshes;
import io.github.jcondedata.aliveworkplace.guard.BanditCamps;
import io.github.jcondedata.aliveworkplace.hall.VillageHalls;
import io.github.jcondedata.aliveworkplace.hall.VillageWaystones;
import io.github.jcondedata.aliveworkplace.mc.Lookup;
import io.github.jcondedata.aliveworkplace.registry.ModBlocks;
import io.github.jcondedata.aliveworkplace.threat.CampCleanup;
import io.github.jcondedata.aliveworkplace.threat.Lairs;
import io.github.jcondedata.aliveworkplace.threat.ThreatData;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;

/**
 * QA (lane-e-1122-1313): saved fields added in the week before 2026-11-22 survive a save and reload, and a save made
 * before they existed (the owner's live server) loads with the right default and the feature keeps working.
 * <ul>
 * <li>B1188 (VillageWaystones.Data "plazas"/"plazaHalls", whose village each Waystone Plaza is): kept across a reload, so
 * after a neighbor's hall is broken its hall-less plaza is still its own and not taken by this village's round; an older
 * save without the record fills it at the next hall rounds ("each plaza to its nearest hall, as it counted before") and
 * then keeps it the same way.</li>
 * <li>52.5 (CampCleanup.Clearing "drawing", which drawing a camp coming down was set down with): a save made before 52.5
 * halfway through an old camp's clearing has none; the drawing is found again by matching and the whole old camp still
 * comes down.</li>
 * </ul>
 * (The other fields of the week, built_from/built_from_of, left_scaffolds, a Wonder's drawing, the home villages record,
 * already have reload and old-save tests: QaRebuildHistorySweep1120e, B1322GameTests, WonderDrawingsGameTests,
 * QuestRewardVillageGameTests.)
 */
public class QaSavedFieldsWeek1122eGameTests implements FabricGameTest {
	private static final org.slf4j.Logger LOGGER = org.slf4j.LoggerFactory.getLogger("aliveworkplace_test");
	private static final String HUGE = "aliveworkplace_test:huge_area";
	private static final String WAYSTONES = "aliveworkplace_village_waystones";

	// ---- B1188: whose plaza is whose -----------------------------------------------------------------------------------

	private static final BlockPos HALL_A = new BlockPos(1, 2, 1);
	private static final BlockPos HALL_B = new BlockPos(28, 2, 28);
	private static final BlockPos PLAZA_A = new BlockPos(3, 2, 3);
	private static final BlockPos PLAZA_B = new BlockPos(19, 2, 19);

	private static BlueprintData.Placement placement(GameTestHelper helper, BlockPos origin) {
		return new BlueprintData.Placement(helper.getLevel().dimension().location(), helper.absolutePos(origin), Rotation.NONE, Mirror.NONE);
	}

	/** Two villages, Ashford (A) and Brookmere (B), each with its hall and its finished plaza. */
	private static void twoVillages(GameTestHelper helper) {
		Leftovers.clear(helper);
		Leftovers.halls(helper);
		Leftovers.finished(helper);
		ServerLevel level = helper.getLevel();
		BuildSiteManager manager = BuildSiteManager.get(level);
		for (io.github.jcondedata.aliveworkplace.build.BuildSite site : List.copyOf(manager.all())) {
			if (VillageWaystones.isPlaza(site.structure()) && site.placement().origin().closerThan(helper.absolutePos(HALL_A), 160)) {
				manager.remove(site.id());
			}
		}
		UUID owner = UUID.randomUUID();
		helper.setBlock(HALL_A, ModBlocks.VILLAGE_HALL);
		helper.setBlock(HALL_B, ModBlocks.VILLAGE_HALL);
		((io.github.jcondedata.aliveworkplace.hall.VillageHallBlockEntity) helper.getBlockEntity(HALL_A)).setOwner(owner, "Jesse");
		((io.github.jcondedata.aliveworkplace.hall.VillageHallBlockEntity) helper.getBlockEntity(HALL_B)).setOwner(owner, "Jesse");
		manager.recordFinished(VillageWaystones.PLAZA, placement(helper, PLAZA_A), owner);
		manager.recordFinished(VillageWaystones.PLAZA, placement(helper, PLAZA_B), owner);
		VillageHalls.forgetNearest();
		VillageWaystones.Data data = VillageWaystones.Data.get(level);
		data.forget(helper.absolutePos(HALL_A));
		data.forget(helper.absolutePos(HALL_B));
		Leftovers.after(helper, () -> {
			manager.forgetFinished(placement(helper, PLAZA_A));
			manager.forgetFinished(placement(helper, PLAZA_B));
			VillageHalls.forgetNearest();
		});
	}

	/** The waystone record written and read back, as the world's save does, and put in the level's place for it. */
	private static VillageWaystones.Data reload(GameTestHelper helper, boolean dropPlazaRecord) {
		ServerLevel level = helper.getLevel();
		CompoundTag tag = VillageWaystones.Data.get(level).save(new CompoundTag(), level.registryAccess());
		if (dropPlazaRecord) { // as a save from before B1188 has it
			tag.remove("plazas");
			tag.remove("plazaHalls");
		}
		VillageWaystones.Data back = VillageWaystones.Data.load(tag, level.registryAccess());
		level.getDataStorage().set(WAYSTONES, back);
		helper.assertTrue(VillageWaystones.Data.get(level) == back, "setup: the read-back record isn't the level's");
		return back;
	}

	/** Each hall's round, through the lookup the round itself uses for the village's stones. */
	private static void rounds(GameTestHelper helper, BlockPos... halls) {
		for (BlockPos hall : halls) {
			VillageWaystones.stones(helper.getLevel(), helper.absolutePos(hall));
		}
	}

	private static Optional<BlockPos> owner(GameTestHelper helper, BlockPos plaza) {
		return VillageWaystones.Data.get(helper.getLevel()).owner(helper.absolutePos(plaza));
	}

	/** Breaks Brookmere's hall as a player would, and checks its plaza's nearest hall is now Ashford's. */
	private static void breakHallB(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		level.destroyBlock(helper.absolutePos(HALL_B), false);
		VillageHalls.forgetNearest();
		helper.assertTrue(VillageHalls.nearest(level, helper.absolutePos(PLAZA_B)).equals(Optional.of(helper.absolutePos(HALL_A))),
			"setup: Brookmere's plaza's nearest hall is " + VillageHalls.nearest(level, helper.absolutePos(PLAZA_B)) + ", not Ashford's");
	}

	/**
	 * B1188's record across a save and reload: both villages' rounds keep each plaza as its own; the world is saved and
	 * loaded; Brookmere's hall is broken and Ashford goes round three times. Brookmere's plaza is still Brookmere's,
	 * Ashford's still Ashford's.
	 */
	//$ gametest_batch HUGE '"qaPlazaOwnersReload1122e"'
	@GameTest(template = HUGE, batch = "qaPlazaOwnersReload1122e")
	public void whosePlazaIsWhoseSurvivesAReload(GameTestHelper helper) {
		twoVillages(helper);
		rounds(helper, HALL_A, HALL_B);
		helper.assertTrue(owner(helper, PLAZA_B).equals(Optional.of(helper.absolutePos(HALL_B))), "setup: Brookmere's plaza is kept as " + owner(helper, PLAZA_B));
		reload(helper, false);
		helper.assertTrue(owner(helper, PLAZA_A).equals(Optional.of(helper.absolutePos(HALL_A))) && owner(helper, PLAZA_B).equals(Optional.of(helper.absolutePos(HALL_B))),
			"after a reload the plazas are kept as A: " + owner(helper, PLAZA_A) + ", B: " + owner(helper, PLAZA_B));
		breakHallB(helper);
		rounds(helper, HALL_A, HALL_A, HALL_A);
		helper.assertTrue(owner(helper, PLAZA_B).equals(Optional.of(helper.absolutePos(HALL_B))),
			"after a reload and Brookmere's hall broken, Ashford's rounds took Brookmere's plaza: it's kept as " + owner(helper, PLAZA_B));
		helper.assertTrue(owner(helper, PLAZA_A).equals(Optional.of(helper.absolutePos(HALL_A))), "Ashford's own plaza is kept as " + owner(helper, PLAZA_A));
		LOGGER.info("B1188's plaza record survived a reload: Brookmere's plaza stayed its own after its hall was broken");
		helper.succeed();
	}

	/**
	 * A save from before B1188 (no "plazas"/"plazaHalls"): it loads with no plaza kept, the next hall rounds give each
	 * plaza to its nearest hall, and from then on B1188 holds: Brookmere's hall broken, Ashford's rounds leave Brookmere's
	 * plaza Brookmere's. The record is then written with the plazas in it.
	 */
	//$ gametest_batch HUGE '"qaPlazaOwnersOldSave1122e"'
	@GameTest(template = HUGE, batch = "qaPlazaOwnersOldSave1122e")
	public void aSaveFromBeforeThePlazaRecordFillsItAtTheNextRounds(GameTestHelper helper) {
		twoVillages(helper);
		rounds(helper, HALL_A, HALL_B);
		VillageWaystones.Data back = reload(helper, true);
		helper.assertTrue(back.owner(helper.absolutePos(PLAZA_A)).isEmpty() && back.owner(helper.absolutePos(PLAZA_B)).isEmpty(),
			"an old save already keeps plazas: A " + back.owner(helper.absolutePos(PLAZA_A)) + ", B " + back.owner(helper.absolutePos(PLAZA_B)));
		rounds(helper, HALL_B, HALL_A);
		helper.assertTrue(owner(helper, PLAZA_A).equals(Optional.of(helper.absolutePos(HALL_A))) && owner(helper, PLAZA_B).equals(Optional.of(helper.absolutePos(HALL_B))),
			"after the first rounds on an old save each plaza isn't its nearest hall's: A " + owner(helper, PLAZA_A) + ", B " + owner(helper, PLAZA_B));
		breakHallB(helper);
		rounds(helper, HALL_A, HALL_A, HALL_A);
		helper.assertTrue(owner(helper, PLAZA_B).equals(Optional.of(helper.absolutePos(HALL_B))),
			"on an old save, after Brookmere's hall was broken Ashford's rounds took Brookmere's plaza: it's kept as " + owner(helper, PLAZA_B));
		ServerLevel level = helper.getLevel();
		CompoundTag saved = VillageWaystones.Data.get(level).save(new CompoundTag(), level.registryAccess());
		helper.assertTrue(saved.contains("plazas", Tag.TAG_LONG_ARRAY) && saved.getLongArray("plazas").length >= 2
				&& saved.getLongArray("plazaHalls").length == saved.getLongArray("plazas").length,
			"the filled record isn't written back: " + saved);
		LOGGER.info("an old waystone save loaded with no plaza kept, the next rounds filled it, and B1188 held after");
		helper.succeed();
	}

	// ---- 52.5: an old camp's clearing from a save made before the drawing was recorded ----------------------------------

	private static final BlockPos HALL = new BlockPos(1, 2, 1);
	private static final BlockPos CAMP = new BlockPos(15, 1, 15);
	private static final BlockPos FIRE = new BlockPos(7, 1, 6);
	/** The Bandit Camp's drawing on the owner's server before 52.5. */
	private static final String OLD = "v0.150.0";

	private static BlockPos village(GameTestHelper helper) {
		Leftovers.clear(helper);
		ServerLevel level = helper.getLevel();
		int radius = VillageHalls.RADIUS;
		VillageHalls.RADIUS = 16;
		helper.setBlock(HALL, ModBlocks.VILLAGE_HALL);
		BlockPos hall = helper.absolutePos(HALL);
		ThreatData.get(level).forgetPast(hall);
		BanditCamps.forget(level);
		Leftovers.after(helper, () -> {
			level.getEntitiesOfClass(Mob.class, helper.getBounds().inflate(48),
				m -> m.getTags().contains(Lairs.TAG) || m.getTags().contains(BanditCamps.TAG)).forEach(Mob::discard);
			level.getEntitiesOfClass(ItemEntity.class, helper.getBounds().inflate(8)).forEach(Entity::discard);
			BanditCamps.forget(level);
			ThreatData.get(level).forgetPast(hall);
			VillageHalls.RADIUS = radius;
		});
		return hall;
	}

	private static StructurePlaceSettings turn(int r) {
		return new StructurePlaceSettings().setRotation(Rotation.values()[r]);
	}

	private static BlockPos at(BlockPos middle, int r, BlockPos local) {
		BlockPos origin = middle.subtract(StructureTemplate.calculateRelativePosition(turn(r), FIRE));
		return origin.offset(StructureTemplate.calculateRelativePosition(turn(r), local));
	}

	private static List<Blueprint.Entry> standing(Blueprint d) {
		return d.blocks().stream().filter(e -> e.pos().getY() >= 1 && !e.state().isAir()).toList();
	}

	private static BlockState turned(Blueprint.Entry e, int r) {
		return e.state().mirror(Mirror.NONE).rotate(Rotation.values()[r]);
	}

	private static int asDrawn(ServerLevel level, Blueprint d, BlockPos middle, int r) {
		int n = 0;
		for (Blueprint.Entry e : standing(d)) {
			if (MaterialRules.matches(level.getBlockState(at(middle, r, e.pos())), turned(e, r))) {
				n++;
			}
		}
		return n;
	}

	private static Blueprint today(ServerLevel level, ResourceLocation id) {
		StructureTemplate template = level.getStructureManager().get(id).orElseThrow(() -> new IllegalStateException("no template " + id));
		return Blueprint.fromTemplate(id, template, Lookup.lookup(BuiltInRegistries.BLOCK));
	}

	/**
	 * A camp of the old drawing (as on the owner's server before 52.5) loses its chief and starts coming down; halfway,
	 * the game is saved with the clearing as a save from before 52.5 has it (no "drawing" in it) and loaded. The camp is
	 * still known as the old drawing (found again by matching), all of it comes down, and the clearing ends.
	 */
	//$ gametest_ticks_batch HUGE '400' '"qaOldCampNoDrawing1122e"'
	@GameTest(template = HUGE, timeoutTicks = 400, batch = "qaOldCampNoDrawing1122e")
	public void anOldCampsClearingSavedBeforeTheDrawingRecordStillComesDownWhole(GameTestHelper helper) {
		BlockPos hall = village(helper);
		ServerLevel level = helper.getLevel();
		helper.runAfterDelay(2, () -> {
			helper.assertTrue(BanditCamps.found(level, hall, helper.absolutePos(CAMP)) != null, "setup: no camp was made");
			Lairs.Lair lair = Lairs.near(level, hall).orElseThrow();
			BlockPos middle = lair.pos();
			Blueprint now = today(level, Lairs.BANDIT_CAMP);
			int r = -1;
			for (int t = 0; t < 4 && r < 0; t++) {
				if (asDrawn(level, now, middle, t) == standing(now).size()) {
					r = t;
				}
			}
			helper.assertTrue(r >= 0, "setup: today's camp doesn't stand whole");
			int turn = r;
			Blueprint old = Refreshes.olderDrawing(level.getServer(), Lairs.BANDIT_CAMP, OLD).orElseThrow();
			int flags = Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE;
			for (Blueprint.Entry e : standing(now)) {
				level.removeBlockEntity(at(middle, turn, e.pos()));
				level.setBlock(at(middle, turn, e.pos()), Blocks.AIR.defaultBlockState(), flags);
			}
			for (Blueprint.Entry e : old.blocks()) {
				if (!e.state().isAir()) {
					level.setBlock(at(middle, turn, e.pos()), turned(e, turn), flags);
				}
			}
			int whole = standing(old).size();
			helper.assertTrue(asDrawn(level, old, middle, turn) == whole, "setup: the old camp doesn't stand whole");
			if (level.getEntity(lair.captain()) instanceof LivingEntity chief) {
				chief.kill();
			}
			helper.assertTrue(CampCleanup.clearing(level).size() == 1, "setup: the old camp isn't coming down: " + CampCleanup.clearing(level));
			helper.runAfterDelay(CampCleanup.DELAY + CampCleanup.CLEAR_TICKS / 2, () -> {
				Lairs.Data data = Lairs.Data.get(level);
				CompoundTag tag = data.save(new CompoundTag(), level.registryAccess());
				ListTag going = tag.getList("clearing", Tag.TAG_COMPOUND);
				helper.assertTrue(going.size() == 1, "setup: the save holds " + going.size() + " clearings");
				for (int i = 0; i < going.size(); i++) {
					going.getCompound(i).remove("drawing"); // as a save from before 52.5 has it
				}
				int before = asDrawn(level, old, middle, turn);
				helper.assertTrue(before > 0 && before < whole, "setup: halfway, " + before + " of " + whole + " old blocks stand");
				data.read(tag);
				helper.assertTrue(CampCleanup.clearing(level).size() == 1, "the old save's clearing was dropped on load: " + CampCleanup.clearing(level));
				helper.runAfterDelay(CampCleanup.CLEAR_TICKS / 2 + 15, () -> {
					List<String> problems = new ArrayList<>();
					int left = asDrawn(level, old, middle, turn);
					if (left > 0) {
						problems.add(left + " of the old camp's " + whole + " blocks still stand (" + before + " stood at the reload)");
					}
					if (!CampCleanup.clearing(level).isEmpty()) {
						problems.add("the clearing never ended: " + CampCleanup.clearing(level));
					}
					helper.assertTrue(problems.isEmpty(), "turn " + turn + ", old save without the drawing: " + String.join("; ", problems));
					LOGGER.info("an old camp's clearing from a save before 52.5 (no drawing) came down whole: " + whole + " blocks");
					helper.succeed();
				});
			});
		});
	}
}
