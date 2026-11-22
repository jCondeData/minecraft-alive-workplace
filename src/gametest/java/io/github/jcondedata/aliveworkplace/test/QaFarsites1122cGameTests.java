package io.github.jcondedata.aliveworkplace.test;

import io.github.jcondedata.aliveworkplace.blueprint.BlueprintData;
import io.github.jcondedata.aliveworkplace.blueprint.BlueprintLibrary;
import io.github.jcondedata.aliveworkplace.blueprint.BlueprintOutline;
import io.github.jcondedata.aliveworkplace.build.BuildSite;
import io.github.jcondedata.aliveworkplace.build.Builders;
import io.github.jcondedata.aliveworkplace.farm.FieldJob;
import io.github.jcondedata.aliveworkplace.farm.Fields;
import io.github.jcondedata.aliveworkplace.orchard.Orchards;
import io.github.jcondedata.aliveworkplace.registry.ModAttachments;
import io.github.jcondedata.aliveworkplace.registry.ModBlocks;
import io.github.jcondedata.aliveworkplace.registry.ModGameRules;
import io.github.jcondedata.aliveworkplace.registry.ModVillagers;
import io.github.jcondedata.aliveworkplace.wood.TreeFarms;
import io.github.jcondedata.aliveworkplace.work.Jobs;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.function.BooleanSupplier;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Vec3i;
import net.minecraft.core.registries.Registries;
import net.minecraft.data.worldgen.features.TreeFeatures;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.Container;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.village.poi.PoiTypes;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.npc.VillagerProfession;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CropBlock;
import net.minecraft.world.level.block.SweetBerryBushBlock;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.structure.BoundingBox;

/**
 * QA (2026-11-22, lane C) for B666 and B667, from 36.20's spec: "A worker whose far site sits in unloaded chunks waits
 * there as today (no chunk loading)". On top of FarSiteUnloadedGameTests (a far quarry and a far build kept and resumed)
 * and QaFarReach1028(b)BugTests (miner, farmer, lumberjack and builder load nothing): the orchard keeper, whose guard
 * was never tested, with his orchard saved and loaded while it waits; the lumberjack and the orchard keeper still
 * working round their own block while their far area is away; the farmer's far field going on (not dropped, not
 * 'finished') once its chunks are back; a field half in loaded and half in unloaded chunks, and one right at the
 * loaded edge; and the other side of the switch: with workplaceKeepWorkLoaded on and the build's owner online, the far
 * build is loaded and built.
 */
public class QaFarsites1122cGameTests {
	private static final String AREA = "aliveworkplace_test:build_area";
	private static final String BIG = "aliveworkplace_test:big_area";
	private static final ResourceLocation TEST_HUT = ResourceLocation.fromNamespaceAndPath("aliveworkplace_test", "test_hut");
	private static final BlockPos WORK = new BlockPos(3, 2, 3);
	private static final BlockPos AT = new BlockPos(4, 2, 4);

	private static void buildDelay(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		int delay = level.getGameRules().getInt(ModGameRules.BUILD_DELAY);
		Leftovers.after(helper, () -> level.getGameRules().getRule(ModGameRules.BUILD_DELAY).set(delay, level.getServer()));
		level.getGameRules().getRule(ModGameRules.BUILD_DELAY).set(2, level.getServer());
	}

	/**
	 * 100 ticks with the worker still (whatever the hand-over loaded may unload), then at work until the end: the far
	 * chunks must be unloaded at tick 100 and never loaded again, and {@code kept} must hold; problems go in the list.
	 */
	private static List<String> watch(GameTestHelper helper, Villager worker, List<ChunkPos> far, BooleanSupplier kept, String what, int until) {
		ServerLevel level = helper.getLevel();
		List<String> problems = new ArrayList<>();
		int[] ticks = {0};
		helper.onEachTick(() -> {
			ticks[0]++;
			if (ticks[0] < 100 || ticks[0] > until || !problems.isEmpty()) {
				return;
			}
			List<ChunkPos> now = QaFarReach1028bGameTests.loaded(level, far);
			if (ticks[0] == 100) {
				if (!now.isEmpty()) {
					problems.add("setup: " + what + "'s far chunks still loaded 100 ticks after the hand-over (nobody at work yet): " + now);
				}
				worker.setNoAi(false);
				return;
			}
			boolean has = kept.getAsBoolean();
			if (!now.isEmpty() || !has) {
				problems.add("tick " + ticks[0] + " (at work): far chunks loaded " + now + ", " + what + (has ? " still his" : " gone (dropped or finished)")
					+ ", worker at " + worker.blockPosition().toShortString() + " " + worker.getBrain().getActiveNonCoreActivity());
			}
		});
		return problems;
	}

	/** Force-loads every chunk from {@code work} out to {@code site} and its margin until the test ends (the player is back). */
	private static void bringBack(GameTestHelper helper, BlockPos work, BoundingBox site) {
		ServerLevel level = helper.getLevel();
		int m = 20;
		List<ChunkPos> forced = new ArrayList<>();
		for (int cx = (Math.min(work.getX(), site.minX()) - m) >> 4; cx <= (Math.max(work.getX(), site.maxX()) + m) >> 4; cx++) {
			for (int cz = (Math.min(work.getZ(), site.minZ()) - m) >> 4; cz <= (Math.max(work.getZ(), site.maxZ()) + m) >> 4; cz++) {
				force(helper, forced, cx, cz);
			}
		}
	}

	private static void force(GameTestHelper helper, List<ChunkPos> forced, int cx, int cz) {
		ServerLevel level = helper.getLevel();
		if (!level.getForcedChunks().contains(ChunkPos.asLong(cx, cz))) {
			level.setChunkForced(cx, cz, true);
			ChunkPos c = new ChunkPos(cx, cz);
			forced.add(c);
			Leftovers.after(helper, () -> level.setChunkForced(c.x, c.z, false));
		}
	}

	// --- the orchard keeper (no far-site test of his before) ------------------------------------------------------------

	/**
	 * An orchard 120 out in unloaded chunks, handed over with the Field Marker: for 300 work ticks its chunks stay
	 * away and it stays his; meanwhile he picks the ripe bush beside his basket (he works round his block); and saved and
	 * loaded while it waits, he still has the same orchard.
	 */
	//$ gametest_ticks_batch AREA '1200' '"qaFarsites1122cOrchard"'
	@GameTest(template = AREA, timeoutTicks = 1200, batch = "qaFarsites1122cOrchard")
	public void aFarOrchardInUnloadedChunksIsLeftAloneWhileTheKeeperPicksRoundHisBasket(GameTestHelper helper) {
		QaFarReach1028bGameTests.setUp(helper);
		buildDelay(helper);
		ServerLevel level = helper.getLevel();
		helper.setBlock(WORK, ModBlocks.FRUIT_BASKET);
		helper.setBlock(new BlockPos(3, 2, 5), Blocks.CHEST);
		BlockPos bush = new BlockPos(6, 2, 2);
		OrchardGameTests.bush(helper, bush, 3);
		BlockPos work = helper.absolutePos(WORK);
		BlockPos at = QaFarReach1028bGameTests.unloadedSpot(helper, work, 120, 2);
		Villager keeper = helper.spawn(EntityType.VILLAGER, AT);
		keeper.setNoAi(true);
		Jobs.employ(level, keeper, work, ModVillagers.FRUIT_BASKET_POI, ModVillagers.ORCHARD_KEEPER);
		Leftovers.after(helper, () -> Orchards.release(level, keeper, null));
		BoundingBox[] box = new BoundingBox[1];
		List<ChunkPos> far = QaFarReach1028bGameTests.markField(helper, keeper, at, () -> (box[0] = Orchards.orchard(keeper)) != null);
		List<String> problems = watch(helper, keeper, far, () -> box[0].equals(Orchards.orchard(keeper)), "the orchard keeper's far orchard", 1200);
		helper.succeedWhen(() -> {
			helper.assertTrue(problems.isEmpty(), "the far orchard in unloaded chunks: " + problems);
			helper.assertTrue(helper.getTick() >= 400, "not yet 300 ticks at work");
			int age = helper.getBlockState(bush).getValue(SweetBerryBushBlock.AGE);
			helper.assertTrue(age < 3, "the ripe bush beside the basket wasn't picked while the far orchard was away (age " + age + ", keeper at "
				+ keeper.blockPosition().toShortString() + " " + keeper.getBrain().getActiveNonCoreActivity() + ")");
			CompoundTag tag = new CompoundTag();
			keeper.save(tag);
			Villager copy = (Villager) EntityType.create(tag, level).orElseThrow();
			helper.assertTrue(box[0].equals(Orchards.orchard(copy)), "saved and loaded while it waits, the keeper's orchard is " + Orchards.orchard(copy) + ", not " + box[0]);
			copy.discard();
		});
	}

	// --- the lumberjack works round his block -------------------------------------------------------------------------

	/** Grows a real oak (the vanilla feature, fixed random) on a grass block at {@code base}. */
	private static void growOak(GameTestHelper helper, BlockPos base) {
		ServerLevel level = helper.getLevel();
		helper.setBlock(base.below(), Blocks.GRASS_BLOCK);
		var feature = level.registryAccess().registryOrThrow(Registries.CONFIGURED_FEATURE).getHolderOrThrow(TreeFeatures.OAK).value();
		if (!feature.place(level, level.getChunkSource().getGenerator(), RandomSource.create(20261122L), helper.absolutePos(base))) {
			throw new GameTestAssertException("setup: could not grow the test tree");
		}
	}

	/**
	 * A tree farm 120 out in unloaded chunks and a grown oak beside the Chopping Block: the far chunks are never loaded
	 * and the farm stays his, and he fells the oak by his block meanwhile.
	 */
	//$ gametest_ticks_batch BIG '3000' '"qaFarsites1122cLumberjack"'
	@GameTest(template = BIG, timeoutTicks = 3000, batch = "qaFarsites1122cLumberjack")
	public void aLumberjackFellsTheTreeByHisBlockWhileHisFarFarmIsAway(GameTestHelper helper) {
		QaFarReach1028bGameTests.setUp(helper);
		buildDelay(helper);
		ServerLevel level = helper.getLevel();
		helper.setBlock(WORK, ModBlocks.CHOPPING_BLOCK);
		helper.setBlock(new BlockPos(3, 2, 5), Blocks.CHEST);
		Container chest = helper.getBlockEntity(new BlockPos(3, 2, 5));
		chest.setItem(0, new ItemStack(Items.IRON_AXE));
		chest.setItem(1, new ItemStack(Items.OAK_SAPLING, 16)); // saplings for the far farm: he'd go and plant it if it were in
		BlockPos base = new BlockPos(10, 2, 10);
		growOak(helper, base);
		BlockPos work = helper.absolutePos(WORK);
		BlockPos at = QaFarReach1028bGameTests.unloadedSpot(helper, work, 120, 2);
		Villager lumberjack = helper.spawn(EntityType.VILLAGER, AT);
		lumberjack.setNoAi(true);
		Jobs.employ(level, lumberjack, work, ModVillagers.CHOPPING_BLOCK_POI, ModVillagers.LUMBERJACK);
		Leftovers.after(helper, () -> ModAttachments.TREE_FARM.remove(lumberjack));
		BoundingBox[] box = new BoundingBox[1];
		List<ChunkPos> far = QaFarReach1028bGameTests.markField(helper, lumberjack, at, () -> (box[0] = TreeFarms.farm(lumberjack)) != null);
		List<String> problems = watch(helper, lumberjack, far, () -> box[0].equals(TreeFarms.farm(lumberjack)), "the lumberjack's far tree farm", 3000);
		helper.succeedWhen(() -> {
			helper.assertTrue(problems.isEmpty(), "the far tree farm in unloaded chunks: " + problems);
			int felled = ModAttachments.TREES_FELLED.getOrElse(lumberjack, 0);
			helper.assertTrue(felled >= 1 && !helper.getBlockState(base.above()).is(Blocks.OAK_LOG), "the oak beside the Chopping Block isn't felled while the far farm is away (felled "
				+ felled + ", lumberjack at " + lumberjack.blockPosition().toShortString() + " " + lumberjack.getBrain().getActiveNonCoreActivity() + ")");
		});
	}

	// --- the farmer's far field goes on once it's back ----------------------------------------------------------------

	/**
	 * A field 120 out in unloaded chunks: 200 work ticks with it away (nothing loaded, the field still his), then the
	 * chunks come back with ripe wheat on it: the field is still his and he harvests it (it wasn't dropped or
	 * 'finished' while it was away, and his waiting doesn't outlast it).
	 */
	//$ gametest_ticks_batch AREA '2000' '"qaFarsites1122cFieldResumes"'
	@GameTest(template = AREA, timeoutTicks = 2000, batch = "qaFarsites1122cFieldResumes")
	public void aFarFieldGoesOnWhenItsChunksAreBack(GameTestHelper helper) {
		QaFarReach1028bGameTests.setUp(helper);
		buildDelay(helper);
		ServerLevel level = helper.getLevel();
		helper.setBlock(WORK, Blocks.COMPOSTER);
		helper.setBlock(new BlockPos(3, 2, 5), Blocks.CHEST);
		BlockPos work = helper.absolutePos(WORK);
		BlockPos at = QaFarReach1028bGameTests.unloadedSpot(helper, work, 120, 2);
		Villager farmer = helper.spawn(EntityType.VILLAGER, AT);
		farmer.setNoAi(true);
		Jobs.employ(level, farmer, work, PoiTypes.FARMER, VillagerProfession.FARMER);
		Leftovers.after(helper, () -> ModAttachments.FARM_FIELD.remove(farmer));
		List<ChunkPos> far = QaFarReach1028bGameTests.markField(helper, farmer, at, () -> Fields.hasField(farmer));
		FieldJob job = ModAttachments.FARM_FIELD.get(farmer);
		BoundingBox field = job.box();
		List<String> problems = watch(helper, farmer, far, () -> field.equals(ModAttachments.FARM_FIELD.get(farmer) == null ? null : ModAttachments.FARM_FIELD.get(farmer).box()),
			"the farmer's far field", 300);
		List<BlockPos> crops = new ArrayList<>();
		helper.runAfterDelay(301, () -> {
			helper.assertTrue(problems.isEmpty(), "the far field while its chunks were away: " + problems);
			bringBack(helper, work, field);
			for (int x = field.minX(); x <= field.maxX(); x++) {
				for (int z = field.minZ(); z <= field.maxZ(); z++) {
					BlockPos p = new BlockPos(x, field.minY(), z);
					level.setBlockAndUpdate(p.below(2), Blocks.STONE.defaultBlockState());
					level.setBlockAndUpdate(p.below(), Blocks.FARMLAND.defaultBlockState());
					level.setBlockAndUpdate(p, Blocks.WHEAT.defaultBlockState().setValue(CropBlock.AGE, CropBlock.MAX_AGE));
					for (int y = 1; y <= 3; y++) {
						level.setBlockAndUpdate(p.above(y), Blocks.AIR.defaultBlockState());
					}
					crops.add(p);
				}
			}
		});
		helper.succeedWhen(() -> {
			helper.assertTrue(!crops.isEmpty(), "the far field's chunks aren't back yet");
			FieldJob now = ModAttachments.FARM_FIELD.get(farmer);
			helper.assertTrue(now != null && now.box().equals(field), "the far field isn't the farmer's any more: " + now);
			long harvested = crops.stream().filter(p -> {
				var s = level.getBlockState(p);
				return !s.is(Blocks.WHEAT) || s.getValue(CropBlock.AGE) < CropBlock.MAX_AGE;
			}).count();
			helper.assertTrue(harvested > 0, "the far field is back with 25 ripe wheat and the farmer hasn't harvested any: farmer at "
				+ farmer.blockPosition().toShortString() + " " + farmer.getBrain().getActiveNonCoreActivity() + ", field " + field);
		});
	}

	// --- a field half in loaded chunks, and one at the loaded edge ----------------------------------------------------

	/** A farmer employed at a composter (and its chest) placed at {@code station}, standing next to it, his AI off. */
	private static Villager farFarmer(GameTestHelper helper, BlockPos station) {
		ServerLevel level = helper.getLevel();
		level.setBlockAndUpdate(station.below(), Blocks.STONE.defaultBlockState());
		level.setBlockAndUpdate(station, Blocks.COMPOSTER.defaultBlockState());
		level.setBlockAndUpdate(station.south(2).below(), Blocks.STONE.defaultBlockState());
		level.setBlockAndUpdate(station.south(2), Blocks.CHEST.defaultBlockState());
		Leftovers.after(helper, () -> {
			level.setBlockAndUpdate(station, Blocks.AIR.defaultBlockState());
			level.setBlockAndUpdate(station.south(2), Blocks.AIR.defaultBlockState());
		});
		Villager farmer = EntityType.VILLAGER.create(level);
		farmer.moveTo(station.getX() + 1.5, station.getY(), station.getZ() + 0.5);
		farmer.setNoAi(true);
		level.addFreshEntity(farmer);
		Leftovers.after(helper, () -> {
			ModAttachments.FARM_FIELD.remove(farmer);
			farmer.discard();
		});
		Jobs.employ(level, farmer, station, PoiTypes.FARMER, VillagerProfession.FARMER);
		return farmer;
	}

	/**
	 * Far out, three chunks in a row are force-loaded (a player stands there), so the chunks two further east are loaded
	 * at the edge and the next column isn't. One farmer's field straddles that edge (half its blocks in a loaded chunk,
	 * half in an unloaded one); another's ends on the last loaded column. Both farmers are on shift for 300 ticks: the
	 * unloaded column is never loaded and both fields stay theirs.
	 */
	//$ gametest_ticks_batch AREA '700' '"qaFarsites1122cEdge"'
	@GameTest(template = AREA, timeoutTicks = 700, batch = "qaFarsites1122cEdge")
	public void aFieldHalfInUnloadedChunksAndOneAtTheLoadedEdgeLoadNothing(GameTestHelper helper) {
		QaFarReach1028bGameTests.setUp(helper);
		ServerLevel level = helper.getLevel();
		BlockPos work = helper.absolutePos(WORK);
		// a spot ~200 out whose 7 × 3 chunks east of it nobody has loaded
		ChunkPos found = null;
		for (int angle = 0; angle < 360 && found == null; angle += 15) {
			double a = Math.toRadians(angle);
			ChunkPos candidate = new ChunkPos(work.offset((int) Math.round(200 * Math.cos(a)), 0, (int) Math.round(200 * Math.sin(a))));
			List<ChunkPos> around = new ArrayList<>();
			for (int dx = -3; dx <= 7; dx++) {
				for (int dz = -3; dz <= 3; dz++) {
					around.add(new ChunkPos(candidate.x + dx, candidate.z + dz));
				}
			}
			if (QaFarReach1028bGameTests.loaded(level, around).isEmpty()) {
				found = candidate;
			}
		}
		helper.assertTrue(found != null, "setup: no unloaded spot 200 out");
		ChunkPos c = found;
		List<ChunkPos> forced = new ArrayList<>();
		for (int dx = 0; dx <= 2; dx++) {
			force(helper, forced, c.x + dx, c.z);
		}
		int edge = (c.x + 5) << 4; // the first block of the column that stays unloaded
		List<ChunkPos> unloaded = new ArrayList<>();
		for (int dz = -2; dz <= 2; dz++) {
			unloaded.add(new ChunkPos(c.x + 5, c.z + dz));
		}
		int midZ = (c.z << 4) + 8;
		Villager[] farmers = new Villager[2];
		List<String> problems = new ArrayList<>();
		// 40 ticks for the forced chunks to load (a villager added before his chunk is in can't be clicked)
		helper.runAfterDelay(40, () -> {
			int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, edge - 34, midZ);
			// the composters in the last entity-ticking chunk (c.x + 2), about 35 blocks short of the edge
			farmers[0] = farFarmer(helper, new BlockPos(edge - 36, y, midZ - 4));
			farmers[1] = farFarmer(helper, new BlockPos(edge - 36, y, midZ + 3));
		});
		helper.runAfterDelay(45, () -> {
			int y = farmers[0].blockPosition().getY();
			Villager half = farmers[0];
			Villager inside = farmers[1];
			QaFarReach1028bGameTests.markField(helper, half, new BlockPos(edge, y, midZ - 4), () -> Fields.hasField(half)); // x edge-2 .. edge+2
			QaFarReach1028bGameTests.markField(helper, inside, new BlockPos(edge - 3, y, midZ + 4), () -> Fields.hasField(inside)); // x edge-5 .. edge-1
			BoundingBox halfBox = ModAttachments.FARM_FIELD.get(half).box();
			BoundingBox insideBox = ModAttachments.FARM_FIELD.get(inside).box();
			helper.assertTrue(halfBox.minX() < edge && halfBox.maxX() >= edge && insideBox.maxX() == edge - 1,
				"setup: the fields aren't where they should be: " + halfBox + ", " + insideBox + " (edge x " + edge + ")");
		});
		int[] ticks = {0};
		helper.onEachTick(() -> {
			ticks[0]++;
			if (ticks[0] < 150 || !problems.isEmpty()) {
				return;
			}
			Villager half = farmers[0];
			Villager inside = farmers[1];
			List<ChunkPos> now = QaFarReach1028bGameTests.loaded(level, unloaded);
			if (ticks[0] == 150) {
				ChunkPos lastLoaded = new ChunkPos(c.x + 4, c.z);
				if (!now.isEmpty() || QaFarReach1028bGameTests.loaded(level, List.of(lastLoaded)).isEmpty()) {
					problems.add("setup: 100 ticks after the hand-over the edge isn't where expected: unloaded column loaded " + now + ", " + lastLoaded + " loaded "
						+ !QaFarReach1028bGameTests.loaded(level, List.of(lastLoaded)).isEmpty());
				}
				half.setNoAi(false);
				inside.setNoAi(false);
				return;
			}
			boolean kept = ModAttachments.FARM_FIELD.get(half) != null && ModAttachments.FARM_FIELD.get(inside) != null;
			if (!now.isEmpty() || !kept) {
				problems.add("tick " + ticks[0] + " (at work): the unloaded column loaded " + now + ", fields kept " + kept);
			}
		});
		helper.runAfterDelay(450, () -> {
			helper.assertTrue(problems.isEmpty(), "fields at the edge of the loaded chunks: " + problems);
			helper.assertTrue(farmers[0].getBrain().isActive(net.minecraft.world.entity.schedule.Activity.WORK), "setup: the farmer wasn't on shift: "
				+ farmers[0].getBrain().getActiveNonCoreActivity());
			helper.succeed();
		});
	}

	// --- the other side of the switch: workplaceKeepWorkLoaded on, the owner online -----------------------------------

	/**
	 * The same far build in unloaded chunks, but with workplaceKeepWorkLoaded on and the player who handed it over still
	 * online (far away): the rule keeps the site loaded, so the builder goes on and places blocks there.
	 */
	//$ gametest_ticks_batch AREA '2400' '"qaFarsites1122cKeepOn"'
	@GameTest(template = AREA, timeoutTicks = 2400, batch = "qaFarsites1122cKeepOn")
	public void withKeepWorkLoadedOnAndTheOwnerOnlineTheFarBuildIsLoadedAndBuilt(GameTestHelper helper) {
		QaFarReach1028bGameTests.setUp(helper);
		ServerLevel level = helper.getLevel();
		boolean free = level.getGameRules().getBoolean(ModGameRules.FREE_MATERIALS);
		Leftovers.after(helper, () -> level.getGameRules().getRule(ModGameRules.FREE_MATERIALS).set(free, level.getServer()));
		level.getGameRules().getRule(ModGameRules.FREE_MATERIALS).set(true, level.getServer());
		helper.setBlock(WORK, ModBlocks.BLUEPRINT_TABLE);
		BlockPos bench = helper.absolutePos(WORK);
		Vec3i size = BlueprintLibrary.get(level, TEST_HUT).orElseThrow().size();
		BlockPos at = QaFarReach1028bGameTests.unloadedSpot(helper, bench, 120, Math.max(size.getX(), size.getZ()) / 2 + 2);
		Villager builder = helper.spawn(EntityType.VILLAGER, AT);
		Builders.employ(level, builder, bench);
		QaFarReach1028bGameTests.forgetSitesOf(helper, builder);
		QaStageFiveGameTests.Client client = QaStageSixGameTests.client(helper, GameType.SURVIVAL, QaStageSixGameTests.name("farK"));
		BlueprintData.Placement placement = QaFarReach1028bGameTests.standing(level, TEST_HUT, bench, at);
		client.player().setItemInHand(InteractionHand.MAIN_HAND, QaFarReach1028bGameTests.blueprint(level, TEST_HUT, placement));
		QaStageSixGameTests.clickVillager(client.player(), builder, client.seen());
		BuildSite site = Builders.activeSite(level, builder);
		helper.assertTrue(site != null, "setup: the build 120 out was refused: " + client.seen().stream().map(Component::getString).toList());
		UUID id = site.id();
		// the owner stays online (in the player list) but isn't near: his body leaves this spot, so he loads no chunks here
		client.player().discard();
		helper.assertTrue(level.getServer().getPlayerList().getPlayer(client.player().getUUID()) != null, "setup: the owner isn't online");
		level.getGameRules().getRule(ModGameRules.KEEP_WORK_LOADED).set(true, level.getServer());
		List<ChunkPos> far = QaFarReach1028bGameTests.chunks(BlueprintOutline.bounds(placement, size));
		helper.succeedWhen(() -> {
			BuildSite now = Builders.activeSite(level, builder);
			helper.assertTrue(now != null && now.id().equals(id), "the far build isn't the builder's any more");
			List<ChunkPos> loaded = QaFarReach1028bGameTests.loaded(level, far);
			helper.assertTrue(now.placed() > 0, "with workplaceKeepWorkLoaded on and the owner online the far build has no block placed: far chunks loaded "
				+ loaded.size() + " of " + far.size() + ", stage " + now.stage() + ", status " + now.status() + ", builder at " + builder.blockPosition().toShortString());
		});
	}
}
