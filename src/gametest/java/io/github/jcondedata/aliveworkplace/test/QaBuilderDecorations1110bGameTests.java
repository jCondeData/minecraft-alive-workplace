package io.github.jcondedata.aliveworkplace.test;

import io.github.jcondedata.aliveworkplace.blueprint.BlueprintData;
import io.github.jcondedata.aliveworkplace.build.BuildPlan;
import io.github.jcondedata.aliveworkplace.build.BuildSite;
import io.github.jcondedata.aliveworkplace.build.BuildSiteManager;
import io.github.jcondedata.aliveworkplace.build.BuilderStatusSync;
import io.github.jcondedata.aliveworkplace.build.BuilderWork;
import io.github.jcondedata.aliveworkplace.build.Builders;
import io.github.jcondedata.aliveworkplace.craft.JobShortages;
import io.github.jcondedata.aliveworkplace.registry.ModAttachments;
import io.github.jcondedata.aliveworkplace.registry.ModBlocks;
import io.github.jcondedata.aliveworkplace.registry.ModGameRules;
import io.github.jcondedata.aliveworkplace.registry.ModVillagers;
import io.github.jcondedata.aliveworkplace.work.Jobs;
import io.github.jcondedata.aliveworkplace.work.Requests;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Vec3i;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Container;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.village.poi.PoiTypes;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.decoration.GlowItemFrame;
import net.minecraft.world.entity.decoration.ItemFrame;
import net.minecraft.world.entity.decoration.Painting;
import net.minecraft.world.entity.decoration.PaintingVariants;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.npc.VillagerProfession;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.phys.AABB;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * QA for B1148 (lane-b-1110-1837), written from the spec: a blueprint's item frames, paintings and armor stands are asked
 * for like blocks (Needs list, requests board, the jobs that make them) and named on the shortage line; the finished build
 * waits for a missing one while one is spare or a job makes it, else DECORATION_WAIT, never past 3x that, then ends without
 * it (hanging what it has); the wait start is saved and absent in older saves.
 */
public class QaBuilderDecorations1110bGameTests {
	private static final Logger LOG = LoggerFactory.getLogger("QaBuilderDecorations1110b");
	private static final String AREA = "aliveworkplace_test:build_area";
	private static final BlockPos BENCH = new BlockPos(2, 2, 2);
	private static final BlockPos CHEST = new BlockPos(2, 2, 4);
	private static final BlockPos STATION = new BlockPos(14, 2, 2);
	private static final BlockPos ROW = new BlockPos(6, 2, 8);
	/** Where a hanging decoration goes: on the first plank's south face. */
	private static final BlockPos FACE = ROW.south();

	// ---- who makes what ---------------------------------------------------------------------------------------------

	/** A painting in the blueprint: the Shepherd makes it from the chest's sticks and wool, and it is hung. */
	//$ gametest_ticks_batch AREA '4000' '"qa1110bPainting"'
	@GameTest(template = AREA, timeoutTicks = 4000, batch = "qa1110bPainting")
	public void theShepherdMakesTheBlueprintsPaintingAndItIsHung(GameTestHelper helper) {
		prepare(helper);
		ResourceLocation id = blueprint(helper, "painting", 1, 1, level -> List.of(
			new Painting(level, helper.absolutePos(FACE), Direction.SOUTH,
				level.registryAccess().registryOrThrow(Registries.PAINTING_VARIANT).getHolderOrThrow(PaintingVariants.KEBAB))));
		Container chest = chest(helper, new ItemStack(Items.OAK_PLANKS, 4), new ItemStack(Items.STICK, 16), new ItemStack(Items.WHITE_WOOL, 2));
		Villager builder = builder(helper);
		BuildSite site = start(helper, builder, id);
		Integer asked = Builders.remainingNeed(helper.getLevel(), site, site.plan(helper.getLevel())).get(Items.PAINTING);
		helper.assertTrue(asked != null && asked == 1, "the painting isn't on the builder's list: " + asked);
		helper.setBlock(STATION, Blocks.LOOM);
		Villager shepherd = helper.spawn(EntityType.VILLAGER, STATION.south());
		Jobs.employ(helper.getLevel(), shepherd, helper.absolutePos(STATION), PoiTypes.SHEPHERD, VillagerProfession.SHEPHERD);
		UUID siteId = site.id();
		helper.succeedWhen(() -> {
			BuildSite open = BuildSiteManager.get(helper.getLevel()).get(siteId);
			helper.assertTrue(open == null, "still building: " + state(open));
			helper.assertTrue(count(helper, Painting.class, FACE) == 1, count(helper, Painting.class, FACE) + " paintings hang on the plank");
			helper.assertTrue(chest.countItem(Items.WHITE_WOOL) < 2, "the painting wasn't made from the chest's wool");
			LOG.info("[QA B1148] painting made by the Shepherd and hung");
		});
	}

	/** An armor stand in the blueprint: the Carpenter makes it from the chest's sticks and smooth stone slab; it is stood up. */
	//$ gametest_ticks_batch AREA '4000' '"qa1110bStand"'
	@GameTest(template = AREA, timeoutTicks = 4000, batch = "qa1110bStand")
	public void theCarpenterMakesTheBlueprintsArmorStandAndItIsStoodUp(GameTestHelper helper) {
		prepare(helper);
		ResourceLocation id = blueprint(helper, "stand", 1, 3, level -> {
			ArmorStand stand = new ArmorStand(level, helper.absolutePos(ROW.above()).getX() + 0.5, helper.absolutePos(ROW.above()).getY(),
				helper.absolutePos(ROW.above()).getZ() + 0.5);
			return List.of(stand);
		});
		Container chest = chest(helper, new ItemStack(Items.OAK_PLANKS, 4), new ItemStack(Items.STICK, 16), new ItemStack(Items.SMOOTH_STONE_SLAB, 2));
		Villager builder = builder(helper);
		BuildSite site = start(helper, builder, id);
		Integer asked = Builders.remainingNeed(helper.getLevel(), site, site.plan(helper.getLevel())).get(Items.ARMOR_STAND);
		helper.assertTrue(asked != null && asked == 1, "the armor stand isn't on the builder's list: " + asked);
		helper.setBlock(STATION, ModBlocks.CARPENTERS_BENCH);
		Villager carpenter = helper.spawn(EntityType.VILLAGER, STATION.south());
		Jobs.employ(helper.getLevel(), carpenter, helper.absolutePos(STATION), ModVillagers.CARPENTERS_BENCH_POI, ModVillagers.CARPENTER);
		UUID siteId = site.id();
		helper.succeedWhen(() -> {
			BuildSite open = BuildSiteManager.get(helper.getLevel()).get(siteId);
			helper.assertTrue(open == null, "still building: " + state(open));
			int stands = helper.getLevel().getEntitiesOfClass(ArmorStand.class, new AABB(helper.absolutePos(ROW.above())).inflate(1), Entity::isAlive).size();
			helper.assertTrue(stands == 1, stands + " armor stands stand on the plank");
			helper.assertTrue(chest.countItem(Items.SMOOTH_STONE_SLAB) < 2, "the stand wasn't made from the chest's slab");
			helper.assertTrue(ModAttachments.ITEMS_CRAFTED.getOrElse(carpenter, 0) >= 1, "the Carpenter made " + ModAttachments.ITEMS_CRAFTED.getOrElse(carpenter, 0));
			LOG.info("[QA B1148] armor stand made by the Carpenter and stood up");
		});
	}

	// ---- quantities -------------------------------------------------------------------------------------------------

	/**
	 * Three frames in the blueprint, one in the chest, nobody to make them: the list counts 3, the waiting line asks for
	 * exactly 2 more ("Needs: 2× Item Frame"), and once the wait is over the build hangs the one it has and ends, its
	 * request gone from the board.
	 */
	//$ gametest_ticks_batch AREA '2000' '"qa1110bThree"'
	@GameTest(template = AREA, timeoutTicks = 2000, batch = "qa1110bThree")
	public void threeFramesOneInTheChestAsksForExactlyTwoThenHangsTheOne(GameTestHelper helper) {
		prepare(helper);
		shortWait(helper, 300);
		ResourceLocation id = blueprint(helper, "three", 3, 1, level -> {
			List<Entity> out = new ArrayList<>();
			for (int x = 0; x < 3; x++) {
				out.add(new ItemFrame(level, helper.absolutePos(FACE.east(x)), Direction.SOUTH));
			}
			return out;
		});
		Container chest = chest(helper, new ItemStack(Items.OAK_PLANKS, 6), new ItemStack(Items.ITEM_FRAME, 1));
		Villager builder = builder(helper);
		BuildSite site = start(helper, builder, id);
		ServerLevel level = helper.getLevel();
		Integer all = Builders.remainingNeed(level, site, site.plan(level)).get(Items.ITEM_FRAME);
		helper.assertTrue(all != null && all == 3, "the builder's list should count all 3 frames: " + all);
		Watch w = watch(helper, builder, site.id());
		helper.succeedWhen(() -> {
			helper.assertTrue(w.line.get() != null, "the finished build never waited for its frames");
			helper.assertTrue("Needs: 2× Item Frame".equals(w.line.get()), "the builder's waiting line" + ": " + w.line.get() + ", expected " + "Needs: 2× Item Frame");
			helper.assertTrue(w.short_.get() != null && w.short_.get() == 2, "short of " + w.short_.get() + " frames, expected exactly 2");
			helper.assertTrue(w.board.get().contains("minecraft:item_frame"), "the requests board while waiting: " + w.board.get());
			BuildSite open = BuildSiteManager.get(level).get(site.id());
			helper.assertTrue(open == null, "still building: " + state(open));
			// one box over the three spots (a box per spot would also catch the neighbour's frame)
			int hung = helper.getLevel().getEntitiesOfClass(ItemFrame.class,
				new AABB(helper.absolutePos(FACE)).expandTowards(2, 0, 0), Entity::isAlive).size();
			helper.assertTrue(hung == 1, hung + " frames hang; expected the 1 the chest had");
			helper.assertTrue(chest.countItem(Items.ITEM_FRAME) == 0, "the chest's frame wasn't used: " + chest.countItem(Items.ITEM_FRAME));
			assertNothingDangling(helper, builder, site.id());
			LOG.info("[QA B1148] 3 frames, 1 in the chest: line '{}', 1 hung, nothing left on the board", w.line.get());
		});
	}

	/** A frame already in the bench's chest, no Leatherworker: hung at once, never waited for nor asked of anyone. */
	//$ gametest_ticks_batch AREA '1200' '"qa1110bInChest"'
	@GameTest(template = AREA, timeoutTicks = 1200, batch = "qa1110bInChest")
	public void aFrameAlreadyInTheChestIsHungWithoutWaiting(GameTestHelper helper) {
		prepare(helper);
		ResourceLocation id = frameBlueprint(helper, false);
		Container chest = chest(helper, new ItemStack(Items.OAK_PLANKS, 4), new ItemStack(Items.ITEM_FRAME, 1));
		Villager builder = builder(helper);
		BuildSite site = start(helper, builder, id);
		ServerLevel level = helper.getLevel();
		AtomicBoolean waited = new AtomicBoolean();
		AtomicBoolean listed = new AtomicBoolean();
		helper.onEachTick(() -> {
			BuildSite open = BuildSiteManager.get(level).get(site.id());
			if (open != null && open.isGettingDecorations()) {
				waited.set(true);
			}
			if (open != null && open.missing().containsKey(Items.ITEM_FRAME)) {
				listed.set(true);
			}
		});
		helper.succeedWhen(() -> {
			BuildSite open = BuildSiteManager.get(level).get(site.id());
			helper.assertTrue(open == null, "still building: " + state(open));
			helper.assertTrue(!waited.get(), "the build waited for a frame the chest held");
			helper.assertTrue(!listed.get(), "a frame the chest held was on the shortage list");
			helper.assertTrue(count(helper, ItemFrame.class, FACE) == 1, count(helper, ItemFrame.class, FACE) + " frames hang");
			helper.assertTrue(chest.countItem(Items.ITEM_FRAME) == 0, "the chest's frame is still there");
			LOG.info("[QA B1148] frame in the chest hung with no wait");
		});
	}

	/** A glow item frame is asked for as a Glow Item Frame (not a plain one), named so, and the build ends without it. */
	//$ gametest_ticks_batch AREA '2000' '"qa1110bGlow"'
	@GameTest(template = AREA, timeoutTicks = 2000, batch = "qa1110bGlow")
	public void aGlowItemFrameIsAskedForByItsOwnName(GameTestHelper helper) {
		prepare(helper);
		shortWait(helper, 300);
		ResourceLocation id = frameBlueprint(helper, true);
		chest(helper, new ItemStack(Items.OAK_PLANKS, 4), new ItemStack(Items.ITEM_FRAME, 1)); // a plain frame doesn't do
		Villager builder = builder(helper);
		BuildSite site = start(helper, builder, id);
		ServerLevel level = helper.getLevel();
		Map<Item, Integer> need = Builders.remainingNeed(level, site, site.plan(level));
		helper.assertTrue(Integer.valueOf(1).equals(need.get(Items.GLOW_ITEM_FRAME)) && !need.containsKey(Items.ITEM_FRAME),
			"the list should hold 1 glow item frame and no plain one: " + need);
		Watch w = watch(helper, builder, site.id());
		helper.succeedWhen(() -> {
			helper.assertTrue("Needs: 1× Glow Item Frame".equals(w.line.get()), "the builder's waiting line" + ": " + w.line.get() + ", expected " + "Needs: 1× Glow Item Frame");
			helper.assertTrue(w.board.get().contains("minecraft:glow_item_frame"), "the requests board: " + w.board.get());
			BuildSite open = BuildSiteManager.get(level).get(site.id());
			helper.assertTrue(open == null, "still building: " + state(open));
			helper.assertTrue(count(helper, ItemFrame.class, FACE) == 0, "a frame was hung in place of the glow frame");
			LOG.info("[QA B1148] glow frame named '{}'", w.line.get());
		});
	}

	// ---- the wait's bounds ------------------------------------------------------------------------------------------

	/**
	 * Nothing spare, nobody makes it: the build ends no sooner than DECORATION_WAIT after the wait began and not much later;
	 * the builder then takes the next blueprint in his queue.
	 */
	//$ gametest_ticks_batch AREA '2400' '"qa1110bBound"'
	@GameTest(template = AREA, timeoutTicks = 2400, batch = "qa1110bBound")
	public void theWaitEndsAfterDecorationWaitNotBeforeAndTheNextJobStarts(GameTestHelper helper) {
		prepare(helper);
		int wait = shortWait(helper, 300);
		ResourceLocation id = frameBlueprint(helper, false);
		chest(helper, new ItemStack(Items.OAK_PLANKS, 8));
		Villager builder = builder(helper);
		BuildSite site = start(helper, builder, id);
		ServerLevel level = helper.getLevel();
		BlockPos next = ROW.east(4);
		BuildSite queued = Builders.enqueue(level, builder, null, id, new BlueprintData.Placement(level.dimension().location(), helper.absolutePos(next),
			Rotation.NONE, Mirror.NONE));
		AtomicLong since = new AtomicLong(-1);
		AtomicLong ended = new AtomicLong(-1);
		helper.onEachTick(() -> {
			BuildSite open = BuildSiteManager.get(level).get(site.id());
			if (open != null && open.isGettingDecorations() && since.get() < 0) {
				since.set(open.decorationWaitSince());
			}
			if (open == null && ended.get() < 0) {
				ended.set(level.getGameTime());
			}
		});
		helper.succeedWhen(() -> {
			helper.assertTrue(since.get() >= 0, "the finished build never waited");
			helper.assertTrue(ended.get() >= 0, "the first build never ended");
			long took = ended.get() - since.get();
			helper.assertTrue(took >= wait, "the build ended " + took + " ticks into a " + wait + "-tick wait");
			helper.assertTrue(took < 2L * wait, "the build waited " + took + " ticks with nobody making the frame (wait " + wait + ")");
			assertNothingDangling(helper, builder, site.id());
			helper.assertTrue(helper.getBlockState(next).is(Blocks.OAK_PLANKS), "the builder didn't build his next queued blueprint");
			LOG.info("[QA B1148] waited {} of {} ticks, then the queued build", took, wait);
		});
	}

	/**
	 * A job that keeps saying it's making the frame (JobShortages.covered every tick, as a crafter stuck mid-job would)
	 * holds the build past DECORATION_WAIT, but never past 3x it.
	 */
	//$ gametest_ticks_batch AREA '2400' '"qa1110bCap"'
	@GameTest(template = AREA, timeoutTicks = 2400, batch = "qa1110bCap")
	public void aJobForeverOnItHoldsTheBuildNoLongerThanThreeWaits(GameTestHelper helper) {
		prepare(helper);
		int wait = shortWait(helper, 200);
		ResourceLocation id = frameBlueprint(helper, false);
		chest(helper, new ItemStack(Items.OAK_PLANKS, 4));
		Villager builder = builder(helper);
		BuildSite site = start(helper, builder, id);
		ServerLevel level = helper.getLevel();
		AtomicLong since = new AtomicLong(-1);
		AtomicLong ended = new AtomicLong(-1);
		helper.onEachTick(() -> {
			BuildSite open = BuildSiteManager.get(level).get(site.id());
			if (open != null) {
				JobShortages.covered(site.id(), Items.ITEM_FRAME, level.getGameTime());
				if (open.isGettingDecorations() && since.get() < 0) {
					since.set(open.decorationWaitSince());
				}
			} else if (ended.get() < 0) {
				ended.set(level.getGameTime());
			}
		});
		helper.succeedWhen(() -> {
			helper.assertTrue(since.get() >= 0, "the finished build never waited");
			helper.assertTrue(ended.get() >= 0, "the build never ended");
			long took = ended.get() - since.get();
			helper.assertTrue(took >= 3L * wait, "with a job on it the build ended after only " + took + " ticks (wait " + wait + ")");
			helper.assertTrue(took < 3L * wait + 150, "the build waited " + took + " ticks, past 3x the " + wait + "-tick wait");
			helper.assertTrue(count(helper, ItemFrame.class, FACE) == 0, "a frame appeared from nowhere");
			LOG.info("[QA B1148] job forever on it: ended after {} ticks (3x{})", took, wait);
		});
	}

	// ---- saves ------------------------------------------------------------------------------------------------------

	/**
	 * An older save (no decoration_wait_since) loaded while the finished build waits: it loads with no wait (the default)
	 * and the build still ends, without the frame, after a fresh wait.
	 */
	//$ gametest_ticks_batch AREA '2400' '"qa1110bOldSave"'
	@GameTest(template = AREA, timeoutTicks = 2400, batch = "qa1110bOldSave")
	public void anOlderSaveWithoutTheWaitLoadsAndStillFinishes(GameTestHelper helper) {
		prepare(helper);
		int wait = shortWait(helper, 300);
		ResourceLocation id = frameBlueprint(helper, false);
		chest(helper, new ItemStack(Items.OAK_PLANKS, 4));
		Villager[] builder = {builder(helper)};
		BuildSite site = start(helper, builder[0], id);
		ServerLevel level = helper.getLevel();
		AtomicBoolean reloaded = new AtomicBoolean();
		AtomicLong loadedAt = new AtomicLong(-1);
		AtomicLong ended = new AtomicLong(-1);
		helper.onEachTick(() -> {
			BuildSite open = BuildSiteManager.get(level).get(site.id());
			if (open == null) {
				if (ended.get() < 0) {
					ended.set(level.getGameTime());
				}
				return;
			}
			if (reloaded.get() || !open.isGettingDecorations() || level.getGameTime() - open.decorationWaitSince() < 100) {
				return;
			}
			reloaded.set(true);
			CompoundTag saved = BuildSiteManager.get(level).save(new CompoundTag(), level.registryAccess());
			int stripped = strip(saved, "decoration_wait_since");
			helper.assertTrue(stripped == 1, "expected the waiting site's decoration_wait_since in the save, found " + stripped);
			BuildSiteManager loaded = BuildSiteManager.load(saved, level.registryAccess());
			level.getDataStorage().set(BuildSiteManager.NAME, loaded);
			BuildSite back = loaded.get(site.id());
			helper.assertTrue(back != null && back.isDone() && back.decorationWaitSince() == BuildSite.NOT_WAITING && !back.isGettingDecorations(),
				"an old save should load finished and not waiting: " + (back == null ? "gone" : back.stage() + " since " + back.decorationWaitSince()));
			loadedAt.set(level.getGameTime());
			builder[0] = QaBuilderTerrain1102GameTests.reload(helper, level, builder[0]);
		});
		helper.succeedWhen(() -> {
			helper.assertTrue(reloaded.get(), "never reloaded mid-wait");
			helper.assertTrue(ended.get() >= 0, "the build loaded from an older save never ended: " + state(BuildSiteManager.get(level).get(site.id())));
			helper.assertTrue(ended.get() - loadedAt.get() >= wait, "after the old save the build ended " + (ended.get() - loadedAt.get())
				+ " ticks in, before a fresh " + wait + "-tick wait");
			helper.assertTrue(helper.getBlockState(ROW).is(Blocks.OAK_PLANKS), "the plank isn't there");
			helper.assertTrue(count(helper, ItemFrame.class, FACE) == 0, "a frame appeared from nowhere");
			LOG.info("[QA B1148] old save loaded mid-wait, ended {} ticks later", ended.get() - loadedAt.get());
		});
	}

	// ---- helpers ----------------------------------------------------------------------------------------------------

	static void prepare(GameTestHelper helper) {
		Leftovers.clear(helper);
		Leftovers.village(helper, 48);
		ServerLevel level = helper.getLevel();
		helper.setDayTime(2000);
		level.getGameRules().getRule(ModGameRules.BUILD_DELAY).set(2, level.getServer());
		level.getGameRules().getRule(ModGameRules.BUILDERS_HELP).set(false, level.getServer());
		level.getGameRules().getRule(ModGameRules.FREE_MATERIALS).set(false, level.getServer());
	}

	/** Shortens BuilderWork.DECORATION_WAIT for this test, put back after it. Returns the wait in force. */
	static int shortWait(GameTestHelper helper, int ticks) {
		int was = BuilderWork.DECORATION_WAIT;
		BuilderWork.DECORATION_WAIT = ticks;
		Leftovers.after(helper, () -> BuilderWork.DECORATION_WAIT = was);
		return ticks;
	}

	/** One plank with an item frame (or a glow item frame) on its south face. */
	static ResourceLocation frameBlueprint(GameTestHelper helper, boolean glow) {
		return blueprint(helper, glow ? "glow" : "frame", 1, 1, level -> List.of(glow
			? new GlowItemFrame(level, helper.absolutePos(FACE), Direction.SOUTH)
			: new ItemFrame(level, helper.absolutePos(FACE), Direction.SOUTH)));
	}

	/**
	 * A blueprint of {@code width} oak planks in a row from ROW going east, with the given decorations, saved from the
	 * world (the region is {@code width} x {@code height} x 2, the second z row in front of the planks).
	 */
	private static ResourceLocation blueprint(GameTestHelper helper, String name, int width, int height, Function<ServerLevel, List<Entity>> decorations) {
		ServerLevel level = helper.getLevel();
		for (int x = 0; x < width; x++) {
			helper.setBlock(ROW.east(x), Blocks.OAK_PLANKS);
		}
		List<Entity> made = decorations.apply(level);
		for (Entity e : made) {
			helper.assertTrue(!(e instanceof net.minecraft.world.entity.decoration.HangingEntity h) || h.survives(), "setup: the " + name + " can't hang");
			level.addFreshEntity(e);
		}
		ResourceLocation id = ResourceLocation.fromNamespaceAndPath("aliveworkplace_test",
			"qa1110b_" + name + "_" + Long.toHexString(level.getGameTime()) + "_" + helper.absolutePos(ROW).getX());
		level.getStructureManager().getOrCreate(id).fillFromWorld(level, helper.absolutePos(ROW), new Vec3i(width, height, 2), true, Blocks.STRUCTURE_VOID);
		made.forEach(Entity::discard);
		for (int x = 0; x < width; x++) {
			helper.setBlock(ROW.east(x), Blocks.AIR);
		}
		return id;
	}

	static Container chest(GameTestHelper helper, ItemStack... items) {
		helper.setBlock(BENCH, ModBlocks.BUILDERS_BENCH);
		helper.setBlock(CHEST, Blocks.CHEST);
		Container container = helper.getBlockEntity(CHEST);
		for (int i = 0; i < items.length; i++) {
			container.setItem(i, items[i].copy());
		}
		return container;
	}

	static Villager builder(GameTestHelper helper) {
		Villager builder = helper.spawn(EntityType.VILLAGER, BENCH.south());
		Builders.employ(helper.getLevel(), builder, helper.absolutePos(BENCH));
		return builder;
	}

	static BuildSite start(GameTestHelper helper, Villager builder, ResourceLocation id) {
		ServerLevel level = helper.getLevel();
		BuildSite site = Builders.start(level, builder, null, id, new BlueprintData.Placement(level.dimension().location(), helper.absolutePos(ROW),
			Rotation.NONE, Mirror.NONE));
		BuildPlan plan = site == null ? null : site.plan(level);
		if (plan == null) {
			throw new GameTestAssertException("blueprint not found: " + id);
		}
		return site;
	}

	/** What the waiting build showed the first time it stood waiting for a decoration. */
	private record Watch(AtomicReference<String> line, AtomicReference<Integer> short_, AtomicReference<String> board) {
	}

	private static Watch watch(GameTestHelper helper, Villager builder, UUID siteId) {
		ServerLevel level = helper.getLevel();
		Watch w = new Watch(new AtomicReference<>(), new AtomicReference<>(), new AtomicReference<>(""));
		helper.onEachTick(() -> {
			BuildSite open = BuildSiteManager.get(level).get(siteId);
			if (open != null && open.isGettingDecorations() && open.status() == BuildSite.Status.WAITING_FOR_MATERIALS && w.line.get() == null
					&& !open.missing().isEmpty()) {
				w.line.set(BuilderStatusSync.status(level, open, builder).line().getString());
				Integer n = open.missing().get(Items.ITEM_FRAME);
				w.short_.set(n);
				w.board.set(board(level, builder));
			}
		});
		return w;
	}

	private static String board(ServerLevel level, Villager builder) {
		return Requests.of(level, builder).stream().map(r -> BuiltInRegistries.ITEM.getKey(r.icon().getItem()).toString()).toList().toString();
	}

	/** The build is over: no site on the builder, and no frame left on his requests board. */
	private static void assertNothingDangling(GameTestHelper helper, Villager builder, UUID siteId) {
		ServerLevel level = helper.getLevel();
		BuildSite active = Builders.activeSite(level, builder);
		helper.assertTrue(active == null || !active.id().equals(siteId), "the finished site is still the builder's active one");
		String board = board(level, builder);
		helper.assertTrue(!board.contains("item_frame"), "a frame is still asked for on the board after the build ended: " + board);
	}

	private static int count(GameTestHelper helper, Class<? extends Entity> type, BlockPos at) {
		return helper.getLevel().getEntitiesOfClass(type, new AABB(helper.absolutePos(at)).inflate(0.5), Entity::isAlive).size();
	}

	/** Removes {@code key} everywhere in {@code tag} (as an older save, written before the key existed); returns how many. */
	private static int strip(Tag tag, String key) {
		int n = 0;
		if (tag instanceof CompoundTag c) {
			if (c.contains(key)) {
				c.remove(key);
				n++;
			}
			for (String k : c.getAllKeys()) {
				n += strip(c.get(k), key);
			}
		} else if (tag instanceof ListTag l) {
			for (Tag t : l) {
				n += strip(t, key);
			}
		}
		return n;
	}

	private static String state(BuildSite site) {
		return site == null ? "site gone" : "stage=" + site.stage() + " status=" + site.status() + " missing=" + site.missing();
	}
}
