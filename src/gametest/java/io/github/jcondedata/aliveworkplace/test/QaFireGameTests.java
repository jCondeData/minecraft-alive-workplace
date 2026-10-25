package io.github.jcondedata.aliveworkplace.test;

import io.github.jcondedata.aliveworkplace.blueprint.BlueprintData;
import io.github.jcondedata.aliveworkplace.build.BuildSiteManager;
import io.github.jcondedata.aliveworkplace.build.Builders;
import io.github.jcondedata.aliveworkplace.build.Upkeep;
import io.github.jcondedata.aliveworkplace.hall.Chronicle;
import io.github.jcondedata.aliveworkplace.hall.VillageHallBlockEntity;
import io.github.jcondedata.aliveworkplace.hall.VillageHalls;
import io.github.jcondedata.aliveworkplace.registry.ModBlocks;
import io.github.jcondedata.aliveworkplace.registry.ModVillagers;
import io.github.jcondedata.aliveworkplace.threat.Sieges;
import io.github.jcondedata.aliveworkplace.threat.ThreatData;
import io.github.jcondedata.aliveworkplace.threat.Threats;
import io.github.jcondedata.aliveworkplace.threat.VillageFire;
import io.github.jcondedata.aliveworkplace.work.FirewardenWork;
import io.github.jcondedata.aliveworkplace.work.Stations;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.Vec3i;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.Container;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LightningBolt;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.monster.Skeleton;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.npc.VillagerProfession;
import net.minecraft.world.entity.schedule.Activity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.block.BaseFireBlock;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LayeredCauldronBlock;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.AttachFace;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

/**
 * QA (ROADMAP 32.16, 32.16a), written from the spec: the gaps FireGameTests leaves. A player's block inside a builders'
 * build (and a builder's block a player swapped) never burns while the builders' own blocks around it do; a raider's
 * arrow or a lightning bolt never sets a player's build alight; a fire that really burns the roof puts every burned
 * block on the repair list and the builder puts the build back as it was (no progress lost); the 16-block water reach
 * from both sides, with a water cauldron at its very edge; the bell reaches 24 blocks (from both sides) and never a
 * villager at work; a Journeyman rings it of their own accord and an Apprentice never does; a splash reaches 2 blocks
 * and they refill as often as needed; the chronicle counts what burned; and its sentences read right (one block, the
 * village). The cases that fail against the spec today are in QaFireBugTests.
 */
public class QaFireGameTests {
	static final String AREA = "aliveworkplace_test:big_area";
	static final String HUGE = "aliveworkplace_test:huge_area";
	static final BlockPos HALL = new BlockPos(19, 2, 2);
	/** The hut's corner: oak logs at its four corners (y 2 and 3), a 5 x 5 roof (y 4). */
	static final BlockPos HUT = new BlockPos(12, 2, 12);
	static final BlockPos ROOF_TOP = new BlockPos(14, 5, 14);
	static final BlockPos GRINDSTONE = new BlockPos(3, 2, 3);

	record Hut(ServerLevel level, BlockPos hall, BlueprintData.Placement placement, ResourceLocation id, List<BlockPos> blocks) {
	}

	/** A village (its hall at {@code hall}, area {@code radius}) with fire ticking, clear skies and nobody else's Firewarden. */
	static ServerLevel village(GameTestHelper helper, BlockPos hall, int radius) {
		Leftovers.clear(helper);
		Leftovers.halls(helper);
		Leftovers.finished(helper);
		ServerLevel level = helper.getLevel();
		int was = VillageHalls.RADIUS;
		boolean fireTick = level.getGameRules().getBoolean(GameRules.RULE_DOFIRETICK);
		boolean griefing = level.getGameRules().getBoolean(GameRules.RULE_MOBGRIEFING);
		VillageHalls.RADIUS = radius;
		level.getGameRules().getRule(GameRules.RULE_DOFIRETICK).set(true, level.getServer());
		VillageFire.reset();
		level.setWeatherParameters(6000, 0, false, false);
		level.setRainLevel(0f);
		level.setThunderLevel(0f);
		Vec3 middle = helper.getBounds().getCenter();
		for (Villager other : level.getEntities(net.minecraft.world.level.entity.EntityTypeTest.forClass(Villager.class), FirewardenWork::isFirewarden)) {
			if (other.getBrain().getMemory(MemoryModuleType.JOB_SITE)
				.filter(g -> g.dimension() == level.dimension() && Vec3.atCenterOf(g.pos()).distanceTo(middle) <= VillageFire.ANSWER + 32).isPresent()) {
				other.discard();
			}
		}
		Leftovers.after(helper, () -> {
			VillageHalls.RADIUS = was;
			level.getGameRules().getRule(GameRules.RULE_DOFIRETICK).set(fireTick, level.getServer());
			level.getGameRules().getRule(GameRules.RULE_MOBGRIEFING).set(griefing, level.getServer());
			level.setWeatherParameters(6000, 0, false, false);
			FirewardenWork.ENABLED = true;
			Sieges.DAMAGE = true;
			for (BlockPos p : BlockPos.betweenClosed(BlockPos.containing(helper.getBounds().getMinPosition()), BlockPos.containing(helper.getBounds().getMaxPosition()))) {
				if (level.getBlockState(p).is(BlockTags.FIRE)) {
					level.removeBlock(p, false);
				}
				ThreatData.get(level).repaired(p.immutable());
			}
			VillageFire.reset();
		});
		helper.setBlock(hall, ModBlocks.VILLAGE_HALL);
		return level;
	}

	/** The village and a hut the builders finished: log corners, a roof of {@code roof} (planks burn fast, logs slowly). */
	static Hut hut(GameTestHelper helper, String name, net.minecraft.world.level.block.Block roof) {
		ServerLevel level = village(helper, HALL, 20);
		List<BlockPos> blocks = new ArrayList<>();
		for (int x = 0; x < 5; x++) {
			for (int z = 0; z < 5; z++) {
				if ((x == 0 || x == 4) && (z == 0 || z == 4)) {
					for (int y = 0; y < 2; y++) {
						helper.setBlock(HUT.offset(x, y, z), Blocks.OAK_LOG);
						blocks.add(HUT.offset(x, y, z));
					}
				}
				helper.setBlock(HUT.offset(x, 2, z), roof);
				blocks.add(HUT.offset(x, 2, z));
			}
		}
		ResourceLocation id = ResourceLocation.fromNamespaceAndPath("aliveworkplace_test", "qafire_" + name + "_" + Long.toHexString(level.getGameTime()));
		level.getStructureManager().getOrCreate(id).fillFromWorld(level, helper.absolutePos(HUT), new Vec3i(5, 3, 5), false, Blocks.STRUCTURE_VOID);
		BlueprintData.Placement placement = new BlueprintData.Placement(level.dimension().location(), helper.absolutePos(HUT), Rotation.NONE, Mirror.NONE);
		BuildSiteManager.get(level).recordFinished(id, placement, UUID.randomUUID());
		Leftovers.after(helper, () -> BuildSiteManager.get(level).forgetFinished(placement));
		VillageFire.forgetBuilds();
		VillageFire.forgetFactors();
		return new Hut(level, helper.absolutePos(HALL), placement, id, List.copyOf(blocks));
	}

	static void fire(GameTestHelper helper, BlockPos local) {
		BlockPos at = helper.absolutePos(local);
		helper.getLevel().setBlockAndUpdate(at, BaseFireBlock.getState(helper.getLevel(), at));
	}

	/** Lights a flame on top of the first of {@code hut}'s wooden blocks that still stands with air above it. */
	static void relight(GameTestHelper helper, Hut hut) {
		ServerLevel level = helper.getLevel();
		for (BlockPos local : hut.blocks()) {
			BlockPos at = helper.absolutePos(local);
			if ((level.getBlockState(at).is(Blocks.OAK_PLANKS) || level.getBlockState(at).is(Blocks.OAK_LOG)) && level.getBlockState(at.above()).isAir()) {
				level.setBlockAndUpdate(at.above(), BaseFireBlock.getState(level, at.above()));
				return;
			}
		}
	}

	static long fires(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		return BlockPos.betweenClosedStream(helper.getBounds()).filter(p -> level.getBlockState(p).is(BlockTags.FIRE)).count();
	}

	static void putOut(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		for (BlockPos p : BlockPos.betweenClosed(BlockPos.containing(helper.getBounds().getMinPosition()), BlockPos.containing(helper.getBounds().getMaxPosition()))) {
			if (level.getBlockState(p).is(BlockTags.FIRE)) {
				level.removeBlock(p, false);
			}
		}
	}

	static Villager firewarden(GameTestHelper helper, BlockPos standing) {
		helper.setBlock(GRINDSTONE, Blocks.GRINDSTONE.defaultBlockState().setValue(BlockStateProperties.ATTACH_FACE, AttachFace.FLOOR)
			.setValue(BlockStateProperties.HORIZONTAL_FACING, Direction.SOUTH));
		Villager v = helper.spawn(EntityType.VILLAGER, standing);
		Stations.assign(helper.getLevel(), v, helper.absolutePos(GRINDSTONE), ModVillagers.FIREWARDEN);
		return v;
	}

	static Optional<Chronicle.Entry> fireEntry(ServerLevel level, BlockPos hall) {
		if (!(level.getBlockEntity(hall) instanceof VillageHallBlockEntity entity)) {
			return Optional.empty();
		}
		return entity.chronicle().stream().filter(e -> e.kind() == Chronicle.Kind.FIRE).reduce((a, b) -> b);
	}

	/**
	 * Spec: "never to a block the builders didn't place, so players' own builds are safe". A plank the player hung under
	 * the hut's roof (where the blueprint has air, inside the build's outline) and a roof plank the player swapped for a
	 * spruce one both stay for 400 ticks with fire above, below and beside them, while the builders' own roof around them
	 * does burn (the fire spreads by the game's own fire to builders' blocks).
	 */
	//$ gametest_ticks_batch AREA '500' '"qaFirePlayerInside"'
	@GameTest(template = AREA, timeoutTicks = 500, batch = "qaFirePlayerInside")
	public void aPlayersBlockInsideTheBuildNeverBurnsWhileTheBuildersRoofDoes(GameTestHelper helper) {
		Hut hut = hut(helper, "inside", Blocks.OAK_PLANKS);
		BlockPos hung = HUT.offset(2, 1, 2); // under the roof's middle: air in the blueprint
		BlockPos swapped = HUT.offset(4, 2, 2); // a roof plank, swapped by the player
		helper.setBlock(hung, Blocks.OAK_PLANKS);
		helper.setBlock(swapped, Blocks.SPRUCE_PLANKS);
		ServerLevel level = hut.level();
		helper.runAfterDelay(2, () -> {
			helper.assertTrue(VillageFire.buildAt(level, helper.absolutePos(hung)).isPresent(), "the hung plank isn't inside the build's outline");
			helper.assertFalse(VillageFire.mayBurn(level, helper.absolutePos(hung)), "a player's plank inside a build may burn");
			helper.assertFalse(VillageFire.mayBurn(level, helper.absolutePos(swapped)), "a roof plank the player swapped for spruce may burn");
			helper.assertTrue(VillageFire.mayBurn(level, helper.absolutePos(HUT.offset(3, 2, 2))), "the builders' roof plank can't burn");
			for (int x = 0; x < 5; x++) {
				for (int z = 0; z < 5; z++) {
					fire(helper, HUT.offset(x, 3, z));
				}
			}
			fire(helper, HUT.offset(2, 0, 2)); // right under the hung plank, on the floor
			fire(helper, HUT.offset(1, 1, 2)); // beside it, hanging from the roof
			fire(helper, HUT.offset(3, 1, 2));
		});
		helper.onEachTick(() -> {
			helper.assertBlockPresent(Blocks.OAK_PLANKS, hung);
			helper.assertBlockPresent(Blocks.SPRUCE_PLANKS, swapped);
		});
		helper.runAfterDelay(402, () -> {
			int burned = VillageFire.incident(level, hut.hall()).map(VillageFire.Incident::burned).orElse(-1);
			long gone = hut.blocks().stream().filter(p -> !p.equals(swapped) && !level.getBlockState(helper.absolutePos(p)).is(Blocks.OAK_PLANKS)
				&& !level.getBlockState(helper.absolutePos(p)).is(Blocks.OAK_LOG)).count();
			helper.assertTrue(gone > 0, "in 400 ticks of fire the builders' plank roof never burned (incident burned " + burned + ")");
			helper.succeed();
		});
	}

	/**
	 * Spec: the burning arrows "light the wooden block they hit" and lightning sets "a finished wooden build's roof"
	 * alight, but a fire never goes to a block the builders didn't place. A raider's arrows at a player's wooden wall in
	 * the village never light it (100 tries); a lightning bolt in a thunderstorm in a village with only a player's
	 * wooden house sets nothing alight, and a real bolt striking the player's plank roof leaves it standing.
	 */
	//$ gametest_ticks_batch AREA '400' '"qaFirePlayerSpared"'
	@GameTest(template = AREA, timeoutTicks = 400, batch = "qaFirePlayerSpared")
	public void arrowsAndLightningSpareAPlayersBuild(GameTestHelper helper) {
		ServerLevel level = village(helper, HALL, 20);
		List<BlockPos> roof = new ArrayList<>();
		for (int x = 10; x <= 14; x++) {
			for (int z = 10; z <= 14; z++) {
				helper.setBlock(new BlockPos(x, 4, z), Blocks.OAK_PLANKS);
				roof.add(new BlockPos(x, 4, z));
			}
		}
		helper.setBlock(new BlockPos(10, 2, 10), Blocks.OAK_PLANKS);
		helper.setBlock(new BlockPos(10, 3, 10), Blocks.OAK_PLANKS);
		Skeleton raider = helper.spawn(EntityType.SKELETON, new BlockPos(3, 2, 18));
		raider.setNoAi(true);
		raider.addTag(Threats.RAIDER_TAG);
		helper.runAfterDelay(2, () -> {
			level.getGameRules().getRule(GameRules.RULE_MOBGRIEFING).set(true, level.getServer());
			BlockPos wall = helper.absolutePos(new BlockPos(12, 4, 12));
			BlockHitResult hit = new BlockHitResult(Vec3.atCenterOf(wall).add(0, 0.5, 0), Direction.UP, wall, false);
			RandomSource random = RandomSource.create(42L);
			for (int i = 0; i < 100; i++) {
				helper.assertTrue(VillageFire.arrow(level, raider, hit, random).isEmpty(), "a raider's arrow lit a player's plank roof");
			}
			level.setWeatherParameters(0, 6000, true, true);
			level.setRainLevel(0f); // (the sky's rain would put out a fire: none should be set at all)
			RandomSource sky = RandomSource.create(42L);
			for (int i = 0; i < 100; i++) {
				helper.assertTrue(VillageFire.lightning(level, wall.above(), sky).isEmpty(), "lightning set a player's house alight");
			}
			LightningBolt bolt = EntityType.LIGHTNING_BOLT.create(level);
			bolt.moveTo(Vec3.atBottomCenterOf(wall.above()));
			level.addFreshEntity(bolt);
		});
		helper.onEachTick(() -> {
			for (BlockPos p : roof) {
				helper.assertBlockPresent(Blocks.OAK_PLANKS, p);
			}
		});
		helper.runAfterDelay(380, () -> {
			level.setWeatherParameters(6000, 0, false, false);
			helper.succeed();
		});
	}

	/**
	 * 32.16a: "no progress is ever lost; builders repair anything it does". A fire really burns the hut's plank roof (the
	 * game's own fire); every block that burned is on the builders' repair list; once it's out the builder puts the
	 * hut back block for block.
	 */
	//$ gametest_ticks_batch AREA '4000' '"qaFireRepair"'
	@GameTest(template = AREA, timeoutTicks = 4000, batch = "qaFireRepair")
	public void everyBlockARealFireBurnsIsPutBack(GameTestHelper helper) {
		Hut hut = hut(helper, "repair", Blocks.OAK_PLANKS);
		ServerLevel level = hut.level();
		boolean was = Upkeep.ENABLED;
		boolean asks = Upkeep.ASK;
		Upkeep.ENABLED = true;
		Upkeep.ASK = false;
		Leftovers.after(helper, () -> {
			Upkeep.ENABLED = was;
			Upkeep.ASK = asks;
		});
		BlockPos chestPos = new BlockPos(6, 2, 2);
		helper.setBlock(chestPos, Blocks.CHEST);
		Container chest = helper.getBlockEntity(chestPos);
		chest.setItem(0, new ItemStack(Items.OAK_PLANKS, 64));
		chest.setItem(1, new ItemStack(Items.OAK_LOG, 16));
		BlockPos bench = new BlockPos(2, 2, 2);
		helper.setBlock(bench, ModBlocks.BUILDERS_BENCH);
		Villager builder = helper.spawn(EntityType.VILLAGER, new BlockPos(3, 2, 3));
		Builders.employ(level, builder, helper.absolutePos(bench));
		BuildSiteManager.get(level).recordFinished(hut.id(), hut.placement(), builder.getUUID());
		VillageFire.forgetBuilds();
		long[] stopped = {-1};
		List<BlockPos> lost = new ArrayList<>();
		helper.runAfterDelay(2, () -> {
			fire(helper, ROOF_TOP);
			fire(helper, ROOF_TOP.offset(1, 0, 1));
			fire(helper, ROOF_TOP.offset(-1, 0, -1));
		});
		helper.onEachTick(() -> {
			if (stopped[0] >= 0 || helper.getTick() < 10) {
				return;
			}
			long gone = hut.blocks().stream().filter(p -> !level.getBlockState(helper.absolutePos(p)).is(Blocks.OAK_PLANKS)
				&& !level.getBlockState(helper.absolutePos(p)).is(Blocks.OAK_LOG)).count();
			// B405: fire dies by chance; a fire that went out on its own would end the incident as "burned out" before the
			// Firewarden comes, so the fire is kept alive on the hut until it is put out (a fresh flame joins the same incident).
			if (fires(helper) == 0) {
				relight(helper, hut);
			}
			if (gone >= 3 || helper.getTick() >= 600) {
				level.getGameRules().getRule(GameRules.RULE_DOFIRETICK).set(false, level.getServer());
				putOut(helper);
				stopped[0] = helper.getTick();
				for (BlockPos p : hut.blocks()) {
					BlockState now = level.getBlockState(helper.absolutePos(p));
					if (!now.is(Blocks.OAK_PLANKS) && !now.is(Blocks.OAK_LOG)) {
						lost.add(p);
					}
				}
				helper.assertTrue(!lost.isEmpty(), "in 600 ticks the fire burned nothing of the plank roof");
				List<BlockPos> listed = ThreatData.get(level).damaged();
				for (BlockPos p : lost) {
					helper.assertTrue(listed.contains(helper.absolutePos(p)), "the burned block at " + p.toShortString()
						+ " isn't on the builders' repair list (" + lost.size() + " burned, " + listed.size() + " listed)");
				}
			}
		});
		helper.succeedWhen(() -> {
			helper.assertTrue(stopped[0] >= 0, "the fire is still burning");
			for (BlockPos p : hut.blocks()) {
				BlockState now = level.getBlockState(helper.absolutePos(p));
				helper.assertTrue(now.is(Blocks.OAK_PLANKS) || now.is(Blocks.OAK_LOG), "the burned block at " + p.toShortString() + " is "
					+ now.getBlock() + " (" + lost.size() + " burned: " + lost + ")");
			}
			helper.assertFalse(Builders.activeSite(level, builder) != null, "still repairing");
		});
	}

	/**
	 * Spec: water "within 16 blocks of their grindstone". From both sides: a water source 16 blocks out is found, 17 is
	 * not (along x and along z); a water cauldron 16 blocks out is found.
	 */
	//$ gametest_ticks_batch AREA '100' '"qaFireWaterReach"'
	@GameTest(template = AREA, timeoutTicks = 100, batch = "qaFireWaterReach")
	public void waterIsLookedForWithin16BlocksOfTheGrindstone(GameTestHelper helper) {
		ServerLevel level = village(helper, HALL, 20);
		BlockPos post = new BlockPos(2, 2, 2);
		helper.setBlock(post, Blocks.GRINDSTONE.defaultBlockState().setValue(BlockStateProperties.ATTACH_FACE, AttachFace.FLOOR));
		BlockPos abs = helper.absolutePos(post);
		helper.runAfterDelay(1, () -> {
			helper.assertTrue(FirewardenWork.findWater(level, abs).isEmpty(), "water found with none about");
			for (BlockPos[] pair : new BlockPos[][] {{new BlockPos(18, 1, 2), new BlockPos(19, 1, 2)}, {new BlockPos(2, 1, 18), new BlockPos(2, 1, 19)}}) {
				helper.setBlock(pair[1], Blocks.WATER);
				helper.assertTrue(FirewardenWork.findWater(level, abs).isEmpty(), "water 17 blocks from the grindstone was found: " + pair[1].toShortString());
				helper.setBlock(pair[0], Blocks.WATER);
				helper.assertTrue(FirewardenWork.findWater(level, abs).equals(Optional.of(helper.absolutePos(pair[0]))),
					"water 16 blocks from the grindstone wasn't found: " + FirewardenWork.findWater(level, abs));
				helper.setBlock(pair[0], Blocks.SMOOTH_STONE);
				helper.setBlock(pair[1], Blocks.SMOOTH_STONE);
			}
			helper.setBlock(new BlockPos(18, 2, 2), Blocks.WATER_CAULDRON.defaultBlockState().setValue(LayeredCauldronBlock.LEVEL, 3));
			helper.assertTrue(FirewardenWork.findWater(level, abs).isPresent(), "a water cauldron 16 blocks from the grindstone wasn't found");
			helper.setBlock(new BlockPos(18, 2, 2), Blocks.CAULDRON);
			helper.assertTrue(FirewardenWork.findWater(level, abs).isEmpty(), "an empty cauldron counts as water");
			helper.succeed();
		});
	}

	/**
	 * A Firewarden with empty buckets walks to the water cauldron at the edge of their reach, 16 blocks from the
	 * grindstone, and fills a bucket from it (the cauldron is left empty).
	 */
	//$ gametest_ticks_batch AREA '900' '"qaFireCauldron"'
	@GameTest(template = AREA, timeoutTicks = 900, batch = "qaFireCauldron")
	public void anEmptyFirewardenFillsABucketAtAWaterCauldron16BlocksOut(GameTestHelper helper) {
		village(helper, HALL, 20);
		BlockPos cauldron = new BlockPos(3, 2, 19);
		helper.setBlock(cauldron, Blocks.WATER_CAULDRON.defaultBlockState().setValue(LayeredCauldronBlock.LEVEL, 3));
		Villager warden = firewarden(helper, new BlockPos(4, 2, 3));
		FirewardenWork.setWater(warden, 0);
		helper.succeedWhen(() -> {
			helper.assertTrue(FirewardenWork.water(warden) >= 1, "the firewarden has " + FirewardenWork.water(warden) + " buckets of water, at "
				+ warden.blockPosition().subtract(helper.absolutePos(BlockPos.ZERO)).toShortString());
			helper.assertBlockPresent(Blocks.CAULDRON, cauldron);
		});
	}

	/** The water beyond reach: 17 blocks from the grindstone, an empty Firewarden never fetches it. */
	//$ gametest_ticks_batch AREA '500' '"qaFireTooFar"'
	@GameTest(template = AREA, timeoutTicks = 500, batch = "qaFireTooFar")
	public void waterBeyond16BlocksIsLeftAlone(GameTestHelper helper) {
		village(helper, HALL, 20);
		helper.setBlock(new BlockPos(3, 1, 20), Blocks.WATER);
		Villager warden = firewarden(helper, new BlockPos(4, 2, 3));
		FirewardenWork.setWater(warden, 0);
		helper.runAfterDelay(400, () -> {
			helper.assertTrue(FirewardenWork.water(warden) == 0, "the firewarden filled " + FirewardenWork.water(warden) + " buckets 17 blocks out");
			helper.succeed();
		});
	}

	/**
	 * Spec: "from Journeyman they ring the bell and two villagers off work within 24 blocks join". The reach from both
	 * sides (23 blocks: called; 25: not), and a villager at work nearer than either is never called.
	 */
	//$ gametest_ticks_batch HUGE '100' '"qaFireBellReach"'
	@GameTest(template = HUGE, timeoutTicks = 100, batch = "qaFireBellReach")
	public void theBellCallsVillagersOffWorkWithin24Blocks(GameTestHelper helper) {
		ServerLevel level = village(helper, new BlockPos(15, 2, 15), 40);
		Villager warden = firewarden(helper, new BlockPos(3, 2, 4));
		warden.setNoAi(true);
		warden.setVillagerData(warden.getVillagerData().setLevel(3));
		BlockPos fireAt = new BlockPos(2, 2, 2);
		Villager near = helper.spawn(EntityType.VILLAGER, new BlockPos(2, 2, 25)); // 23 blocks
		Villager far = helper.spawn(EntityType.VILLAGER, new BlockPos(27, 2, 2)); // 25 blocks
		Villager busy = helper.spawn(EntityType.VILLAGER, new BlockPos(8, 2, 2)); // 6 blocks, at work
		near.setNoAi(true);
		far.setNoAi(true);
		busy.setNoAi(true);
		helper.setBlock(new BlockPos(9, 2, 4), Blocks.COMPOSTER);
		busy.setVillagerData(busy.getVillagerData().setProfession(VillagerProfession.FARMER).setLevel(2));
		busy.getBrain().setMemory(MemoryModuleType.JOB_SITE, GlobalPos.of(level.dimension(), helper.absolutePos(new BlockPos(9, 2, 4))));
		helper.runAfterDelay(2, () -> {
			level.getGameRules().getRule(GameRules.RULE_DOFIRETICK).set(false, level.getServer());
			fire(helper, fireAt);
			helper.assertTrue(VillageFire.incident(level, helper.absolutePos(new BlockPos(15, 2, 15))).isPresent(), "the fire wasn't noticed");
			busy.getBrain().setActiveActivityIfPossible(Activity.WORK);
			helper.assertTrue(busy.getBrain().isActive(Activity.WORK), "the farmer isn't at work: the test doesn't test that");
			List<Villager> called = VillageFire.ring(level, warden, helper.absolutePos(fireAt));
			helper.assertFalse(called.contains(far), "the bell called a villager 25 blocks from the fire");
			helper.assertFalse(called.contains(busy), "the bell called a villager at work");
			helper.assertTrue(called.contains(near), "the bell didn't call a villager 23 blocks from the fire: " + called.size() + " called");
			helper.succeed();
		});
	}

	/**
	 * A Journeyman Firewarden at their grindstone, left to themselves: a fire starts, and they ring the bell on their own
	 * (the two villagers off work nearby are called to the brigade); the fire is put out.
	 */
	//$ gametest_ticks_batch AREA '1200' '"qaFireJourneyman"'
	@GameTest(template = AREA, timeoutTicks = 1200, batch = "qaFireJourneyman")
	public void aJourneymanRingsTheBellOfTheirOwnAccord(GameTestHelper helper) {
		Hut hut = hut(helper, "journeyman", Blocks.OAK_LOG);
		Villager warden = firewarden(helper, new BlockPos(3, 2, 4));
		warden.setVillagerData(warden.getVillagerData().setLevel(3));
		Villager a = helper.spawn(EntityType.VILLAGER, new BlockPos(8, 2, 9));
		Villager b = helper.spawn(EntityType.VILLAGER, new BlockPos(9, 2, 8));
		boolean[] rung = {false};
		boolean[] called = {false, false};
		helper.runAfterDelay(2, () -> {
			fire(helper, ROOF_TOP);
			hut.level().getGameRules().getRule(GameRules.RULE_DOFIRETICK).set(false, hut.level().getServer());
		});
		helper.onEachTick(() -> {
			VillageFire.incident(hut.level(), hut.hall()).ifPresent(i -> rung[0] |= i.rung());
			called[0] |= VillageFire.brigadeOf(a).isPresent();
			called[1] |= VillageFire.brigadeOf(b).isPresent();
		});
		helper.succeedWhen(() -> {
			helper.assertTrue(rung[0], "the Journeyman never rang the bell (fire blocks " + fires(helper) + ")");
			helper.assertTrue(called[0] && called[1], "the bell didn't call both villagers off work nearby: " + called[0] + ", " + called[1]);
			helper.assertTrue(fires(helper) == 0, "still burning");
		});
	}

	/** An Apprentice Firewarden puts the fire out alone: the bell is a Journeyman's, nobody is called. */
	//$ gametest_ticks_batch AREA '900' '"qaFireApprentice"'
	@GameTest(template = AREA, timeoutTicks = 900, batch = "qaFireApprentice")
	public void anApprenticeNeverRingsTheBell(GameTestHelper helper) {
		Hut hut = hut(helper, "apprentice", Blocks.OAK_LOG);
		Villager warden = firewarden(helper, new BlockPos(3, 2, 4));
		warden.setVillagerData(warden.getVillagerData().setLevel(2));
		Villager a = helper.spawn(EntityType.VILLAGER, new BlockPos(8, 2, 9));
		boolean[] rung = {false};
		long[] lit = {-1};
		helper.runAfterDelay(2, () -> {
			fire(helper, ROOF_TOP);
			hut.level().getGameRules().getRule(GameRules.RULE_DOFIRETICK).set(false, hut.level().getServer());
			lit[0] = helper.getTick();
		});
		helper.onEachTick(() -> {
			VillageFire.incident(hut.level(), hut.hall()).ifPresent(i -> rung[0] |= i.rung());
			helper.assertFalse(VillageFire.brigadeOf(a).isPresent(), "an Apprentice's fire called a villager to the brigade");
		});
		helper.succeedWhen(() -> {
			helper.assertFalse(rung[0], "an Apprentice rang the bell");
			helper.assertTrue(lit[0] >= 0 && fires(helper) == 0, "still burning");
			helper.assertTrue(VillageFire.incident(hut.level(), hut.hall()).isEmpty(), "the fire isn't over yet");
		});
	}

	/**
	 * Spec: "fire within 2 blocks of the splash goes out", and they "refill as needed". Five fires held still on the roof,
	 * too far apart for one splash to reach two: with two buckets, the Firewarden has to go back to the water twice, and
	 * every fire goes out. A splash puts out fire 2 blocks from where it lands and leaves fire 3 blocks off.
	 */
	//$ gametest_ticks_batch AREA '2400' '"qaFireRefillAsNeeded"'
	@GameTest(template = AREA, timeoutTicks = 2400, batch = "qaFireRefillAsNeeded")
	public void aFirewardenRefillsAsOftenAsTheFireNeeds(GameTestHelper helper) {
		Hut hut = hut(helper, "refills", Blocks.OAK_LOG);
		ServerLevel level = hut.level();
		helper.setBlock(new BlockPos(4, 1, 6), Blocks.WATER);
		Villager warden = firewarden(helper, new BlockPos(3, 2, 4));
		List<BlockPos> lit = List.of(HUT.offset(0, 3, 0), HUT.offset(4, 3, 0), HUT.offset(0, 3, 4), HUT.offset(4, 3, 4), HUT.offset(2, 3, 2));
		int[] fills = {0};
		int[] last = {FirewardenWork.BUCKETS};
		helper.runAfterDelay(2, () -> {
			level.getGameRules().getRule(GameRules.RULE_DOFIRETICK).set(false, level.getServer());
			// the splash's reach, with a firewarden standing by (no AI: a throw of the test's own)
			Villager judge = helper.spawn(EntityType.VILLAGER, new BlockPos(8, 2, 16));
			judge.setNoAi(true);
			judge.setVillagerData(judge.getVillagerData().setProfession(ModVillagers.FIREWARDEN));
			BlockPos centre = new BlockPos(6, 2, 16);
			fire(helper, centre);
			fire(helper, centre.offset(2, 0, 0));
			fire(helper, centre.offset(0, 0, -3));
			int xp = judge.getVillagerXp();
			int out = FirewardenWork.splash(level, judge, helper.absolutePos(centre));
			helper.assertTrue(out == 2, "the splash put out " + out + " fire blocks, not the 2 within 2 blocks");
			helper.assertBlockPresent(Blocks.FIRE, centre.offset(0, 0, -3));
			helper.assertTrue(judge.getVillagerXp() > xp, "a splash that put fire out gave no experience");
			helper.assertTrue(FirewardenWork.water(judge) == FirewardenWork.BUCKETS - 1, "a splash didn't empty a bucket");
			level.removeBlock(helper.absolutePos(centre.offset(0, 0, -3)), false);
			judge.discard();
			for (BlockPos p : lit) {
				fire(helper, p);
			}
		});
		helper.onEachTick(() -> {
			int now = FirewardenWork.water(warden);
			if (now > last[0]) {
				fills[0]++;
			}
			last[0] = now;
		});
		helper.succeedWhen(() -> {
			helper.assertTrue(helper.getTick() > 3 && fires(helper) == 0, "still burning: " + fires(helper) + " fire blocks; the firewarden has "
				+ FirewardenWork.water(warden) + " buckets, filled " + fills[0] + " times, at " + warden.blockPosition().subtract(helper.absolutePos(BlockPos.ZERO)).toShortString());
			helper.assertTrue(fills[0] >= 1, "five fires out with two buckets and no refill");
		});
	}

	/**
	 * The chronicle's count is what burned: a fire that really burns the plank roof, put out by a Firewarden, is written
	 * up with as many blocks burned as are gone from the hut.
	 */
	//$ gametest_ticks_batch AREA '900' '"qaFireCount"'
	@GameTest(template = AREA, timeoutTicks = 900, batch = "qaFireCount")
	public void theChronicleCountsTheBlocksThatBurned(GameTestHelper helper) {
		Hut hut = hut(helper, "count", Blocks.OAK_PLANKS);
		ServerLevel level = hut.level();
		Villager rowan = firewarden(helper, new BlockPos(3, 2, 4));
		rowan.setNoAi(true);
		rowan.setCustomName(Component.literal("Rowan"));
		long[] stopped = {-1};
		int[] lost = {-1};
		helper.runAfterDelay(2, () -> {
			fire(helper, ROOF_TOP);
			fire(helper, ROOF_TOP.offset(1, 0, 1));
			fire(helper, ROOF_TOP.offset(-1, 0, -1));
		});
		helper.onEachTick(() -> {
			if (stopped[0] >= 0 || helper.getTick() < 10) {
				return;
			}
			long gone = hut.blocks().stream().filter(p -> !level.getBlockState(helper.absolutePos(p)).is(Blocks.OAK_PLANKS)
				&& !level.getBlockState(helper.absolutePos(p)).is(Blocks.OAK_LOG)).count();
			// B405: fire dies by chance; a fire that went out on its own would end the incident as "burned out" before the
			// Firewarden comes, so the fire is kept alive on the hut until it is put out (a fresh flame joins the same incident).
			if (fires(helper) == 0) {
				relight(helper, hut);
			}
			if (gone >= 3 || helper.getTick() >= 600) {
				level.getGameRules().getRule(GameRules.RULE_DOFIRETICK).set(false, level.getServer());
				for (BlockPos p : BlockPos.betweenClosed(BlockPos.containing(helper.getBounds().getMinPosition()), BlockPos.containing(helper.getBounds().getMaxPosition()))) {
					if (level.getBlockState(p).is(BlockTags.FIRE)) {
						VillageFire.douse(level, p.immutable(), 0, rowan);
					}
				}
				stopped[0] = helper.getTick();
				lost[0] = (int) hut.blocks().stream().filter(p -> !level.getBlockState(helper.absolutePos(p)).is(Blocks.OAK_PLANKS)
					&& !level.getBlockState(helper.absolutePos(p)).is(Blocks.OAK_LOG)).count();
			}
		});
		helper.succeedWhen(() -> {
			helper.assertTrue(stopped[0] >= 0 && VillageFire.incident(level, hut.hall()).isEmpty(), "the fire isn't over");
			String text = fireEntry(level, hut.hall()).map(e -> e.text().getString()).orElse("nothing");
			String want = lost[0] == 1 ? ": 1 block burned; " : ": " + lost[0] + " blocks burned; ";
			helper.assertTrue(text.contains(want) && text.endsWith("Rowan the Firewarden put it out."), lost[0] + " blocks burned, and the chronicle says: " + text);
		});
	}

	/**
	 * The chronicle's sentences as a player reads them: one block burned is "1 block burned" (and never "1 blocks"), with
	 * the Firewarden's name: "Fire in the …: 1 block burned; Rowan the Firewarden put it out."; and a fire outside any
	 * build that went out by itself is "Fire in the village: … before it burned out."
	 */
	//$ gametest_ticks_batch AREA '400' '"qaFireWords"'
	@GameTest(template = AREA, timeoutTicks = 400, batch = "qaFireWords")
	public void theChronicleReadsRightForOneBlockAndForTheVillage(GameTestHelper helper) {
		Hut hut = hut(helper, "words", Blocks.OAK_LOG);
		ServerLevel level = hut.level();
		Villager rowan = firewarden(helper, new BlockPos(3, 2, 4));
		rowan.setNoAi(true);
		rowan.setCustomName(Component.literal("Rowan"));
		helper.runAfterDelay(2, () -> {
			level.getGameRules().getRule(GameRules.RULE_DOFIRETICK).set(false, level.getServer());
			fire(helper, ROOF_TOP);
			helper.assertTrue(VillageFire.incident(level, hut.hall()).isPresent(), "the fire wasn't noticed");
			VillageFire.burned(level, helper.absolutePos(ROOF_TOP.below()));
			VillageFire.douse(level, helper.absolutePos(ROOF_TOP), 0, rowan);
		});
		helper.runAfterDelay(100, () -> {
			helper.assertTrue(VillageFire.incident(level, hut.hall()).isEmpty(), "the fire isn't over 98 ticks after it was put out");
			String text = fireEntry(level, hut.hall()).map(e -> e.text().getString()).orElse("nothing");
			helper.assertTrue(text.startsWith("Fire in the ") && text.endsWith(": 1 block burned; Rowan the Firewarden put it out."), "the chronicle says: " + text);
			fire(helper, new BlockPos(4, 2, 17)); // on the village's ground, in no build
		});
		helper.runAfterDelay(110, () -> level.removeBlock(helper.absolutePos(new BlockPos(4, 2, 17)), false));
		helper.runAfterDelay(200, () -> {
			String text = fireEntry(level, hut.hall()).map(e -> e.text().getString()).orElse("nothing");
			helper.assertTrue(text.equals("Fire in the village: 0 blocks burned before it burned out."), "the chronicle says: " + text);
			helper.succeed();
		});
	}
}
