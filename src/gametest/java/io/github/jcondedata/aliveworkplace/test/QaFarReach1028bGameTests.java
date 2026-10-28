package io.github.jcondedata.aliveworkplace.test;

import io.github.jcondedata.aliveworkplace.blueprint.BlueprintData;
import io.github.jcondedata.aliveworkplace.blueprint.BlueprintItem;
import io.github.jcondedata.aliveworkplace.blueprint.BlueprintLibrary;
import io.github.jcondedata.aliveworkplace.blueprint.BlueprintOutline;
import io.github.jcondedata.aliveworkplace.build.BuildSite;
import io.github.jcondedata.aliveworkplace.build.BuildSiteManager;
import io.github.jcondedata.aliveworkplace.build.Builders;
import io.github.jcondedata.aliveworkplace.farm.FieldData;
import io.github.jcondedata.aliveworkplace.farm.Fields;
import io.github.jcondedata.aliveworkplace.hall.VillageHalls;
import io.github.jcondedata.aliveworkplace.hall.VillageProtection;
import io.github.jcondedata.aliveworkplace.mc.Ids;
import io.github.jcondedata.aliveworkplace.registry.ModAttachments;
import io.github.jcondedata.aliveworkplace.registry.ModBlocks;
import io.github.jcondedata.aliveworkplace.registry.ModComponents;
import io.github.jcondedata.aliveworkplace.registry.ModGameRules;
import io.github.jcondedata.aliveworkplace.registry.ModItems;
import io.github.jcondedata.aliveworkplace.registry.ModVillagers;
import io.github.jcondedata.aliveworkplace.wood.TreeFarms;
import io.github.jcondedata.aliveworkplace.work.Jobs;
import io.github.jcondedata.aliveworkplace.work.KeepLoaded;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.BooleanSupplier;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Vec3i;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.npc.VillagerProfession;
import net.minecraft.world.entity.ai.village.poi.PoiTypes;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.levelgen.structure.BoundingBox;

/**
 * QA (2026-10-28, second pass) for 36.20, "A worker whose far site sits in unloaded chunks waits there as today (no chunk
 * loading)": a farmer's field, a lumberjack's tree farm and a builder's build, each marked about 120 blocks out (outside
 * a village of radius 16, inside the long reach of 128) in chunks nobody has loaded, handed over through the player's
 * own entry point (Field Marker, placed Blueprint); keepWorkLoaded and the villages' keep-loaded off, so nothing but
 * the worker could load them. With the worker on shift for 300 ticks the far chunks are never loaded and the site stays
 * his. And the builder's long reach measured on a tall build: a pillar standing on the ground with its outline's
 * centre exactly 128 blocks out horizontally.
 */
public class QaFarReach1028bGameTests {
	private static final String AREA = "aliveworkplace_test:build_area";
	private static final ResourceLocation TEST_HUT = ResourceLocation.fromNamespaceAndPath("aliveworkplace_test", "test_hut");
	private static final ResourceLocation PILLAR = ResourceLocation.fromNamespaceAndPath("aliveworkplace_test", "test_pillar");
	private static final BlockPos WORK = new BlockPos(3, 2, 3);
	private static final BlockPos AT = new BlockPos(4, 2, 4);

	static List<ChunkPos> chunks(BoundingBox box) {
		List<ChunkPos> out = new ArrayList<>();
		for (int cx = box.minX() >> 4; cx <= box.maxX() >> 4; cx++) {
			for (int cz = box.minZ() >> 4; cz <= box.maxZ() >> 4; cz++) {
				out.add(new ChunkPos(cx, cz));
			}
		}
		return out;
	}

	static List<ChunkPos> loaded(ServerLevel level, List<ChunkPos> chunks) {
		return chunks.stream().filter(c -> level.getChunkSource().getChunkNow(c.x, c.z) != null).toList();
	}

	/** A spot about {@code distance} blocks round {@code from} whose {@code half}-wide box sits in chunks nobody has loaded. */
	static BlockPos unloadedSpot(GameTestHelper helper, BlockPos from, int distance, int half) {
		ServerLevel level = helper.getLevel();
		for (int angle = 0; angle < 360; angle += 15) {
			double a = Math.toRadians(angle);
			BlockPos at = from.offset((int) Math.round(distance * Math.cos(a)), 0, (int) Math.round(distance * Math.sin(a)));
			BoundingBox box = new BoundingBox(at.getX() - half, at.getY(), at.getZ() - half, at.getX() + half, at.getY(), at.getZ() + half);
			if (loaded(level, chunks(box)).isEmpty()) {
				return at;
			}
		}
		throw new net.minecraft.gametest.framework.GameTestAssertException("setup: every spot " + distance + " blocks round the work block is in a loaded chunk");
	}

	/** A small village round the test (radius 16), keep-loaded off, the day at 2000 (on shift). */
	static void setUp(GameTestHelper helper) {
		Leftovers.clear(helper);
		ServerLevel level = helper.getLevel();
		boolean protection = VillageProtection.ENABLED;
		int radius = VillageHalls.RADIUS;
		boolean keep = level.getGameRules().getBoolean(ModGameRules.KEEP_WORK_LOADED);
		boolean villages = KeepLoaded.VILLAGES;
		Leftovers.after(helper, () -> {
			VillageProtection.ENABLED = protection;
			VillageHalls.RADIUS = radius;
			VillageHalls.forgetNearest();
			level.getGameRules().getRule(ModGameRules.KEEP_WORK_LOADED).set(keep, level.getServer());
			KeepLoaded.VILLAGES = villages;
		});
		VillageProtection.ENABLED = false;
		level.getGameRules().getRule(ModGameRules.KEEP_WORK_LOADED).set(false, level.getServer());
		KeepLoaded.VILLAGES = false;
		helper.setBlock(new BlockPos(1, 2, 1), ModBlocks.VILLAGE_HALL);
		VillageHalls.RADIUS = 16;
		VillageHalls.forgetNearest();
		helper.setDayTime(2000);
	}

	/**
	 * 100 ticks with the worker still (whatever handing the site over loaded may unload), then 300 at work: the far chunks
	 * must be unloaded at tick 100 and never loaded after it, and {@code kept} must hold throughout.
	 */
	static void watch(GameTestHelper helper, Villager worker, List<ChunkPos> far, BooleanSupplier kept, String what, boolean chunks) {
		ServerLevel level = helper.getLevel();
		List<String> problems = new ArrayList<>();
		int[] ticks = {0};
		BlockPos start = worker.blockPosition();
		helper.onEachTick(() -> {
			ticks[0]++;
			if (ticks[0] < 100) {
				return;
			}
			if (ticks[0] == 100) {
				if (chunks && !loaded(level, far).isEmpty()) {
					problems.add("setup: " + what + "'s far chunks were still loaded 100 ticks after it was handed over (nobody at work yet): " + loaded(level, far));
				}
				worker.setNoAi(false);
				return;
			}
			List<ChunkPos> now = chunks ? loaded(level, far) : List.of();
			boolean has = kept.getAsBoolean();
			if (problems.isEmpty() && (!now.isEmpty() || !has)) {
				problems.add("tick " + ticks[0] + " (at work): far chunks loaded " + now + ", " + what + (has ? " still his" : " gone (dropped or finished)")
					+ ", worker at " + worker.blockPosition().toShortString() + " " + worker.getBrain().getActiveNonCoreActivity());
			}
		});
		helper.runAfterDelay(400, () -> {
			helper.assertTrue(problems.isEmpty(), what + " in unloaded chunks: " + problems);
			// a worker who walked out to his site may be saved into its chunk when it unloads: he waits there (spec)
			boolean waitingThere = worker.getRemovalReason() == net.minecraft.world.entity.Entity.RemovalReason.UNLOADED_TO_CHUNK;
			helper.assertTrue(worker.isAlive() || waitingThere, "the worker is gone: " + worker.getRemovalReason() + ", health " + worker.getHealth() + ", last at " + worker.blockPosition().toShortString() + " from " + start.toShortString() + ", " + worker.getLastDamageSource());
			helper.assertTrue(kept.getAsBoolean(), what + " isn't the worker's any more");
			helper.assertTrue(waitingThere || worker.getBrain().isActive(net.minecraft.world.entity.schedule.Activity.WORK),
				"setup: the worker wasn't on shift: " + worker.getBrain().getActiveNonCoreActivity());
			helper.getLevel().getServer().sendSystemMessage(Component.literal("[qa1028b] " + what + (chunks ? ": far chunks never loaded in 300 work ticks" : ": kept for 300 work ticks") + "; worker moved from "
				+ start.toShortString() + " to " + worker.blockPosition().toShortString() + (waitingThere ? " (saved into an unloaded chunk there)" : "")));
			helper.succeed();
		});
	}

	/** Hands a 5 × 5 Field Marker area centred on {@code at} to {@code worker}; returns its chunks. */
	static List<ChunkPos> markField(GameTestHelper helper, Villager worker, BlockPos at, BooleanSupplier taken) {
		ServerLevel level = helper.getLevel();
		List<Component> seen = new ArrayList<>();
		ServerPlayer p = QaStageFourGameTests.player(helper, seen, GameType.SURVIVAL);
		ItemStack marker = new ItemStack(ModItems.FIELD_MARKER);
		marker.set(ModComponents.FIELD, new FieldData(Optional.of(Ids.of(level.dimension())), Optional.of(at.offset(-2, 0, -2)), Optional.of(at.offset(2, 0, 2))));
		p.setItemInHand(InteractionHand.MAIN_HAND, marker);
		QaStageSixGameTests.clickVillager(p, worker, seen);
		helper.assertTrue(taken.getAsBoolean(), "setup: the far area 120 out was refused: " + seen.stream().map(Component::getString).toList());
		p.discard();
		return chunks(new BoundingBox(at.getX() - 2, at.getY(), at.getZ() - 2, at.getX() + 2, at.getY(), at.getZ() + 2));
	}

	static void farField(GameTestHelper helper, boolean chunks) {
		setUp(helper);
		ServerLevel level = helper.getLevel();
		helper.setBlock(WORK, Blocks.COMPOSTER);
		BlockPos work = helper.absolutePos(WORK);
		BlockPos at = unloadedSpot(helper, work, 120, 2);
		Villager farmer = helper.spawn(EntityType.VILLAGER, AT);
		farmer.setNoAi(true);
		Jobs.employ(level, farmer, work, PoiTypes.FARMER, VillagerProfession.FARMER);
		Leftovers.after(helper, () -> ModAttachments.FARM_FIELD.remove(farmer));
		List<ChunkPos> far = markField(helper, farmer, at, () -> Fields.hasField(farmer));
		watch(helper, farmer, far, () -> Fields.hasField(farmer), "the farmer's field", chunks);
	}

	static void farTreeFarm(GameTestHelper helper, boolean chunks) {
		setUp(helper);
		ServerLevel level = helper.getLevel();
		helper.setBlock(WORK, ModBlocks.CHOPPING_BLOCK);
		helper.setBlock(new BlockPos(3, 2, 5), Blocks.CHEST);
		Container chest = helper.getBlockEntity(new BlockPos(3, 2, 5));
		chest.setItem(0, new ItemStack(Items.IRON_AXE));
		chest.setItem(1, new ItemStack(Items.OAK_SAPLING, 16));
		BlockPos work = helper.absolutePos(WORK);
		BlockPos at = unloadedSpot(helper, work, 120, 2);
		Villager lumberjack = helper.spawn(EntityType.VILLAGER, AT);
		lumberjack.setNoAi(true);
		Jobs.employ(level, lumberjack, work, ModVillagers.CHOPPING_BLOCK_POI, ModVillagers.LUMBERJACK);
		Leftovers.after(helper, () -> ModAttachments.TREE_FARM.remove(lumberjack));
		BoundingBox[] box = new BoundingBox[1];
		List<ChunkPos> far = markField(helper, lumberjack, at, () -> (box[0] = TreeFarms.farm(lumberjack)) != null);
		watch(helper, lumberjack, far, () -> box[0].equals(TreeFarms.farm(lumberjack)), "the lumberjack's tree farm", chunks);
	}

	/** A placement of {@code structure} standing on the bench's level, its outline's centre {@code east} blocks east (x and z only). */
	static BlueprintData.Placement standing(ServerLevel level, ResourceLocation structure, BlockPos bench, BlockPos target) {
		Vec3i size = BlueprintLibrary.get(level, structure).orElseThrow().size();
		BlueprintData.Placement atBench = new BlueprintData.Placement(level.dimension().location(), bench, Rotation.NONE, Mirror.NONE);
		BlockPos centre = BlueprintOutline.bounds(atBench, size).getCenter();
		BlockPos origin = bench.offset(target.getX() - centre.getX(), 0, target.getZ() - centre.getZ());
		return new BlueprintData.Placement(level.dimension().location(), origin, Rotation.NONE, Mirror.NONE);
	}

	static ItemStack blueprint(ServerLevel level, ResourceLocation structure, BlueprintData.Placement placement) {
		Vec3i size = BlueprintLibrary.get(level, structure).orElseThrow().size();
		ItemStack blueprint = BlueprintItem.create(structure, size);
		blueprint.set(ModComponents.BLUEPRINT, BlueprintItem.data(blueprint).orElseThrow().withPlacement(Optional.of(placement)));
		return blueprint;
	}

	static void forgetSitesOf(GameTestHelper helper, Villager builder) {
		ServerLevel level = helper.getLevel();
		Leftovers.after(helper, () -> {
			BuildSiteManager sites = BuildSiteManager.get(level);
			for (BuildSite site : List.copyOf(sites.all())) {
				if (builder.getUUID().equals(site.builder())) {
					sites.remove(site.id());
				}
			}
		});
	}

	static void farBuild(GameTestHelper helper, boolean chunks) {
		setUp(helper);
		ServerLevel level = helper.getLevel();
		helper.setBlock(WORK, ModBlocks.BLUEPRINT_TABLE);
		BlockPos bench = helper.absolutePos(WORK);
		Vec3i size = BlueprintLibrary.get(level, TEST_HUT).orElseThrow().size();
		BlockPos at = unloadedSpot(helper, bench, 120, Math.max(size.getX(), size.getZ()) / 2 + 2);
		Villager builder = helper.spawn(EntityType.VILLAGER, AT);
		builder.setNoAi(true);
		Builders.employ(level, builder, bench);
		forgetSitesOf(helper, builder);
		QaStageFiveGameTests.Client c = QaStageSixGameTests.client(helper, GameType.SURVIVAL, QaStageSixGameTests.name("farU"));
		BlueprintData.Placement placement = standing(level, TEST_HUT, bench, at);
		c.player().setItemInHand(InteractionHand.MAIN_HAND, blueprint(level, TEST_HUT, placement));
		QaStageSixGameTests.clickVillager(c.player(), builder, c.seen());
		BuildSite site = Builders.activeSite(level, builder);
		helper.assertTrue(site != null, "setup: the build 120 out was refused: " + c.seen().stream().map(Component::getString).toList());
		java.util.UUID id = site.id();
		c.player().discard();
		List<ChunkPos> far = chunks(BlueprintOutline.bounds(placement, size));
		watch(helper, builder, far, () -> {
			BuildSite now = Builders.activeSite(level, builder);
			return now != null && now.id().equals(id) && !now.isDone();
		}, "the builder's far build", chunks);
	}

	/** Takes away every hall (block and point of interest) within 300 blocks of the work block (as QaFarReach1028GameTests). */
	static void noHalls(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		BlockPos work = helper.absolutePos(WORK);
		net.minecraft.world.entity.ai.village.poi.PoiManager pois = level.getPoiManager();
		pois.findAll(h -> h.is(ModVillagers.VILLAGE_HALL_POI), q -> true, work, 300, net.minecraft.world.entity.ai.village.poi.PoiManager.Occupancy.ANY).toList().forEach(other -> {
			level.setBlockAndUpdate(other, Blocks.AIR.defaultBlockState());
			if (pois.getType(other).isPresent()) {
				pois.remove(other);
			}
		});
		VillageHalls.forgetNearest();
	}

	// --- a far site in unloaded chunks stays the worker's (QaFarReach1028bBugTests: the same with the chunks checked) ----

	/** A field 120 out in unloaded chunks, the farmer on shift 300 ticks: the field is still his. */
	//$ gametest_ticks_batch AREA '500' '"qa1028bKeptField"'
	@GameTest(template = AREA, timeoutTicks = 500, batch = "qa1028bKeptField")
	public void aFarFieldInUnloadedChunksStaysTheFarmers(GameTestHelper helper) {
		farField(helper, false);
	}

	/** A tree farm 120 out in unloaded chunks, the lumberjack on shift 300 ticks: the farm is still his. */
	//$ gametest_ticks_batch AREA '500' '"qa1028bKeptTreeFarm"'
	@GameTest(template = AREA, timeoutTicks = 500, batch = "qa1028bKeptTreeFarm")
	public void aFarTreeFarmInUnloadedChunksStaysTheLumberjacks(GameTestHelper helper) {
		farTreeFarm(helper, false);
	}

	/** A build 120 out in unloaded chunks, the builder on shift 300 ticks: the build is still his, not finished. */
	//$ gametest_ticks_batch AREA '500' '"qa1028bKeptBuild"'
	@GameTest(template = AREA, timeoutTicks = 500, batch = "qa1028bKeptBuild")
	public void aFarBuildInUnloadedChunksStaysTheBuilders(GameTestHelper helper) {
		farBuild(helper, false);
	}

	// --- the builder's long reach on a tall build ------------------------------------------------------------------------

	/** No hall: a pillar standing on the ground centred 120 blocks east of the table is taken; 129 east is refused. */
	//$ gametest_ticks_batch AREA '200' '"qa1028bTall"'
	@GameTest(template = AREA, timeoutTicks = 200, batch = "qa1028bTall")
	public void aTallBuildStandingOnTheGroundIsMeasuredToItsOutline(GameTestHelper helper) {
		tall(helper, 129, 120);
	}

	/** Offers a pillar standing on the ground centred each of {@code easts} east of the table: refused past 128, else taken. */
	static void tall(GameTestHelper helper, int... easts) {
		Leftovers.clear(helper);
		ServerLevel level = helper.getLevel();
		boolean protection = VillageProtection.ENABLED;
		VillageProtection.ENABLED = false;
		Leftovers.after(helper, () -> {
			VillageProtection.ENABLED = protection;
			VillageHalls.forgetNearest();
		});
		noHalls(helper);
		helper.setBlock(WORK, ModBlocks.BLUEPRINT_TABLE);
		BlockPos bench = helper.absolutePos(WORK);
		Villager builder = helper.spawn(EntityType.VILLAGER, AT);
		builder.setNoAi(true);
		Builders.employ(level, builder, bench);
		forgetSitesOf(helper, builder);
		Vec3i size = BlueprintLibrary.get(level, PILLAR).orElseThrow().size();
		QaStageFiveGameTests.Client c = QaStageSixGameTests.client(helper, GameType.SURVIVAL, QaStageSixGameTests.name("tall"));
		for (int east : easts) {
			BlueprintData.Placement placement = standing(level, PILLAR, bench, bench.east(east));
			BlockPos centre = BlueprintOutline.bounds(placement, size).getCenter();
			helper.assertTrue(centre.getX() - bench.getX() == east && centre.getZ() == bench.getZ() && placement.origin().getY() == bench.getY(),
				"setup: the pillar's centre isn't " + east + " east at ground level: " + centre.toShortString() + " from " + bench.toShortString());
			c.seen().clear();
			c.player().setItemInHand(InteractionHand.MAIN_HAND, blueprint(level, PILLAR, placement));
			QaStageSixGameTests.clickVillager(c.player(), builder, c.seen());
			BuildSite site = Builders.activeSite(level, builder);
			if (site != null) {
				BuildSiteManager.get(level).remove(site.id());
			}
			List<String> said = c.seen().stream().map(Component::getString).toList();
			if (east > 128) {
				helper.assertTrue(site == null, "a pillar centred " + east + " out was taken");
			} else {
				helper.assertTrue(site != null, "a pillar " + size.getY() + " tall standing on the ground, its centre exactly " + east + " blocks east of the table, was refused: " + said);
			}
		}
		helper.succeed();
	}
}
