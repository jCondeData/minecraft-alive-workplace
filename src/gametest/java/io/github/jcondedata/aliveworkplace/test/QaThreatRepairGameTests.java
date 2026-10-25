package io.github.jcondedata.aliveworkplace.test;

import io.github.jcondedata.aliveworkplace.WorkplaceConfig;
import io.github.jcondedata.aliveworkplace.blueprint.BlueprintData;
import io.github.jcondedata.aliveworkplace.blueprint.StarterBlueprints;
import io.github.jcondedata.aliveworkplace.build.BuildSite;
import io.github.jcondedata.aliveworkplace.build.BuildSiteManager;
import io.github.jcondedata.aliveworkplace.build.Builders;
import io.github.jcondedata.aliveworkplace.build.Upkeep;
import io.github.jcondedata.aliveworkplace.guard.VillageRaids;
import io.github.jcondedata.aliveworkplace.hall.VillageHalls;
import io.github.jcondedata.aliveworkplace.registry.ModAttachments;
import io.github.jcondedata.aliveworkplace.registry.ModBlocks;
import io.github.jcondedata.aliveworkplace.threat.Culture;
import io.github.jcondedata.aliveworkplace.threat.Sieges;
import io.github.jcondedata.aliveworkplace.threat.ThreatData;
import io.github.jcondedata.aliveworkplace.threat.Threats;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.locale.Language;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.Container;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.FenceGateBlock;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings;

/**
 * QA for ROADMAP 32.16a, written from the owner's rule of 2026-10-12: only the gate blocks of walls and gates may break
 * under threats (rams); whatever threats damage the builders can repair; no progress is ever lost; {@code siegeDamage}
 * stays on by default. Each siege test walls a yard (the player's stone bricks) with a finished build in its front wall
 * and drives a real ravager ram through the raid's own entry point ({@code VillageRaids.track}). The tests look for what
 * the existing siege tests leave open: a ram left at work long after the first gate goes (no other block of the build,
 * nor the player's wall, ever goes); a wall build with no gate at all loses nothing; a gate put back faces as built and
 * stands with its full hit points in the next siege; a gate broken while the builder has no materials stays on the list
 * through the siege's end and a save, and goes back once the materials come; the build is still recorded finished
 * through it all; an old config file without the key keeps siege damage on.
 */
public class QaThreatRepairGameTests implements FabricGameTest {
	private static final String AREA = "aliveworkplace_test:huge_area";
	private static final ResourceLocation BESIEGERS = ResourceLocation.fromNamespaceAndPath("aliveworkplace_test", "qa_repair_besiegers");
	private static final BlockPos HALL = new BlockPos(14, 2, 8);
	private static final int WEST = 5;
	private static final int EAST = 23;
	private static final int BACK = 4;
	private static final int FRONT = 15;
	private static final BlockPos BENCH = new BlockPos(18, 2, 7);
	private static final BlockPos CHEST = new BlockPos(19, 2, 7);

	private record Yard(ServerLevel level, BlockPos hall, BlockPos origin, StarterBlueprints.Entry build, Culture culture, Villager villager,
		BlueprintData.Placement placement) {
		BlockPos at(int x, int y, int z) {
			return origin.offset(x, y, z);
		}
	}

	/** The yard with {@code build} finished in its front wall, a villager shut in a cell inside, and a ram-using culture loaded. */
	private static Yard yard(GameTestHelper helper, StarterBlueprints.Entry build) {
		Leftovers.clear(helper);
		Leftovers.finished(helper);
		ServerLevel level = helper.getLevel();
		int radius = VillageHalls.RADIUS;
		long time = level.getDayTime();
		boolean griefing = level.getGameRules().getBoolean(GameRules.RULE_MOBGRIEFING);
		boolean repairs = Upkeep.ENABLED;
		boolean threats = Upkeep.THREATS;
		level.getGameRules().getRule(GameRules.RULE_MOBGRIEFING).set(true, level.getServer());
		VillageHalls.RADIUS = 20;
		BlockPos hall = helper.absolutePos(HALL);
		Culture culture = Culture.read(BESIEGERS, com.google.gson.JsonParser.parseString("{\"where\": {\"min_villagers\": 100000}, \"roster\": ["
			+ "{\"entity\": \"minecraft:ravager\", \"share\": 100, \"role\": \"ram\"}], \"tactics\": [\"ram_gates\"]}").getAsJsonObject());
		Map<ResourceLocation, Culture> loaded = new LinkedHashMap<>();
		Threats.all().forEach(c -> loaded.put(c.id(), c));
		Map<ResourceLocation, Culture> with = new LinkedHashMap<>(loaded);
		with.put(BESIEGERS, culture);
		Threats.setForTest(with);
		Leftovers.after(helper, () -> {
			Threats.setForTest(loaded);
			level.getEntitiesOfClass(Mob.class, helper.getBounds().inflate(48), m -> m.getTags().contains(VillageRaids.TAG)).forEach(Mob::discard);
			Sieges.siege(level, hall).ifPresent(s -> Sieges.lift(level, s));
			VillageRaids.forget(hall);
			ThreatData.get(level).forgetSiege(hall);
			ThreatData.get(level).forgetPast(hall);
			ThreatData data = ThreatData.get(level);
			for (BlockPos p : List.copyOf(data.damaged())) {
				if (helper.getBounds().inflate(1).contains(p.getCenter())) {
					data.repaired(p);
				}
			}
			Sieges.forget();
			VillageHalls.RADIUS = radius;
			Upkeep.ENABLED = repairs;
			Upkeep.THREATS = threats;
			level.getGameRules().getRule(GameRules.RULE_MOBGRIEFING).set(griefing, level.getServer());
			level.setDayTime(time);
			new WorkplaceConfig().apply();
		});
		int width = build.size().getX();
		int depth = build.size().getZ();
		int x0 = 14 - width / 2;
		int z0 = FRONT - (depth - 1);
		for (int y = 2; y <= 5; y++) {
			for (int x = WEST; x <= EAST; x++) {
				helper.setBlock(new BlockPos(x, y, BACK), Blocks.STONE_BRICKS);
				if (x < x0 || x >= x0 + width) {
					helper.setBlock(new BlockPos(x, y, FRONT), Blocks.STONE_BRICKS);
				}
			}
			for (int z = BACK; z <= FRONT; z++) {
				helper.setBlock(new BlockPos(WEST, y, z), Blocks.STONE_BRICKS);
				helper.setBlock(new BlockPos(EAST, y, z), Blocks.STONE_BRICKS);
			}
		}
		BlockPos origin = helper.absolutePos(new BlockPos(x0, 2, z0));
		level.getStructureManager().get(build.id()).orElseThrow().placeInWorld(level, origin, origin, new StructurePlaceSettings(), RandomSource.create(1L), 2);
		BlueprintData.Placement placement = new BlueprintData.Placement(level.dimension().location(), origin, Rotation.NONE, Mirror.NONE);
		BuildSiteManager.get(level).recordFinished(build.id(), placement, UUID.randomUUID());
		Leftovers.after(helper, () -> BuildSiteManager.get(level).forgetFinished(placement));
		for (int x = 9; x <= 11; x++) {
			for (int z = 6; z <= 8; z++) {
				for (int y = 2; y <= 4; y++) {
					helper.setBlock(new BlockPos(x, y, z), x == 10 && z == 7 && y < 4 ? Blocks.AIR : Blocks.STONE_BRICKS);
				}
			}
		}
		Villager villager = helper.spawn(EntityType.VILLAGER, new BlockPos(10, 2, 7));
		return new Yard(level, hall, origin, build, culture, villager, placement);
	}

	/** The culture's ram, as a raid spawns it, outside the front wall, after the villager. */
	private static Mob ram(GameTestHelper helper, Yard yard) {
		ServerLevel level = yard.level();
		Culture.Member member = yard.culture().roster().get(0);
		Mob mob = Threats.create(level, member);
		helper.assertTrue(mob != null, "no " + member.entity());
		BlockPos base = helper.absolutePos(BlockPos.ZERO);
		mob.moveTo(base.getX() + 14.5, base.getY() + 2, base.getZ() + 21.5, 180f, 0f);
		mob.finalizeSpawn(level, level.getCurrentDifficultyAt(mob.blockPosition()), MobSpawnType.EVENT, null);
		Threats.outfit(level, mob, yard.culture(), member);
		mob.setTarget(yard.villager());
		level.addFreshEntityWithPassengers(mob);
		return mob;
	}

	/** Every non-air block of the build that isn't a gate block, as it stands now. */
	private static Map<BlockPos, BlockState> nonGates(Yard yard) {
		Map<BlockPos, BlockState> out = new HashMap<>();
		for (BlockPos p : BlockPos.betweenClosed(yard.origin(), yard.origin().offset(yard.build().size()).offset(-1, -1, -1))) {
			BlockState state = yard.level().getBlockState(p);
			if (!state.isAir() && !Sieges.isGate(state)) {
				out.put(p.immutable(), state);
			}
		}
		return out;
	}

	/** Every gate block of the build, as it stands now. */
	private static Map<BlockPos, BlockState> gates(Yard yard) {
		Map<BlockPos, BlockState> out = new HashMap<>();
		for (BlockPos p : BlockPos.betweenClosed(yard.origin(), yard.origin().offset(yard.build().size()).offset(-1, -1, -1))) {
			BlockState state = yard.level().getBlockState(p);
			if (Sieges.isGate(state)) {
				out.put(p.immutable(), state);
			}
		}
		return out;
	}

	/** Every block the build lost or changed against {@code before}, for a failure message (empty when none). */
	private static String changed(ServerLevel level, Map<BlockPos, BlockState> before) {
		StringBuilder out = new StringBuilder();
		before.forEach((p, s) -> {
			if (!level.getBlockState(p).is(s.getBlock())) {
				out.append(p.toShortString()).append(" was ").append(s.getBlock()).append(" now ").append(level.getBlockState(p).getBlock()).append("; ");
			}
		});
		return out.toString();
	}

	/** The player's yard walls: every stone brick of the four walls the test laid (helper positions). */
	private static long wallBricks(GameTestHelper helper) {
		long n = 0;
		for (int y = 2; y <= 5; y++) {
			for (int x = WEST; x <= EAST; x++) {
				for (int z : new int[] {BACK, FRONT}) {
					if (helper.getBlockState(new BlockPos(x, y, z)).is(Blocks.STONE_BRICKS)) {
						n++;
					}
				}
			}
			for (int z = BACK + 1; z < FRONT; z++) {
				for (int x : new int[] {WEST, EAST}) {
					if (helper.getBlockState(new BlockPos(x, y, z)).is(Blocks.STONE_BRICKS)) {
						n++;
					}
				}
			}
		}
		return n;
	}

	/** A builder employed at a bench in the yard, looking after the build, with a chest beside the bench holding {@code stock}. */
	private static Villager builder(GameTestHelper helper, Yard yard, ItemStack stock) {
		ServerLevel level = yard.level();
		helper.setBlock(BENCH, ModBlocks.BUILDERS_BENCH);
		helper.setBlock(CHEST, Blocks.CHEST);
		Container chest = helper.getBlockEntity(CHEST);
		if (!stock.isEmpty()) {
			chest.setItem(0, stock);
		}
		Villager builder = helper.spawn(EntityType.VILLAGER, new BlockPos(17, 2, 10));
		Builders.employ(level, builder, helper.absolutePos(BENCH));
		BuildSiteManager.get(level).recordFinished(yard.build().id(), yard.placement(), builder.getUUID());
		Upkeep.ENABLED = false; // builderRepairs off: only what threats took is repaired
		Upkeep.THREATS = true; // as in a player's game
		workingMorning(level);
		return builder;
	}

	/** Builders work from 2000 to 9000: the clock goes on to a working morning unless enough of the day is left. */
	private static void workingMorning(ServerLevel level) {
		long now = level.getDayTime();
		long timeOfDay = now % 24000L;
		if (timeOfDay < 2000L || timeOfDay > 6000L) {
			level.setDayTime(now - timeOfDay + (timeOfDay < 2000L ? 0L : 24000L) + 2000L);
		}
	}

	/** Lifts the siege and forgets it, as the first dawn after does, and drops every raider of it. */
	private static void endSiege(Yard yard) {
		ServerLevel level = yard.level();
		level.getEntitiesOfClass(Mob.class, new net.minecraft.world.phys.AABB(yard.hall()).inflate(48), m -> m.getTags().contains(VillageRaids.TAG)).forEach(Mob::discard);
		Sieges.siege(level, yard.hall()).ifPresent(s -> Sieges.lift(level, s));
		VillageRaids.forget(yard.hall());
		ThreatData.get(level).forgetSiege(yard.hall());
		Sieges.forget();
	}

	private static boolean stillFinished(Yard yard) {
		return BuildSiteManager.get(yard.level()).finishedNear(yard.level(), yard.hall(), 32).stream()
			.anyMatch(f -> f.structure().equals(yard.build().id()));
	}

	/**
	 * A ram left at the Palisade Gate for 1100 ticks, long after the first gate block went: whatever it breaks is a gate
	 * block, each one on the list for the builders; every other block of the build and every brick of the player's wall
	 * still stands, and the build is still recorded finished.
	 */
	//$ gametest_ticks_batch AREA '1300' '"qaRepairRamLong"'
	@GameTest(template = AREA, timeoutTicks = 1300, batch = "qaRepairRamLong")
	public void qaARamLeftAtWorkBreaksNothingButGateBlocks(GameTestHelper helper) {
		Yard yard = yard(helper, StarterBlueprints.PALISADE_GATE);
		ServerLevel level = yard.level();
		Map<BlockPos, BlockState> rest = nonGates(yard);
		Map<BlockPos, BlockState> gates = gates(yard);
		helper.assertTrue(rest.size() > 10 && gates.size() >= 3, "the Palisade Gate: " + rest.size() + " blocks, " + gates.size() + " gates");
		long bricks = wallBricks(helper);
		Mob ram = ram(helper, yard);
		VillageRaids.track(level, yard.hall(), yard.culture(), List.of(ram));
		helper.assertTrue(Sieges.siege(level, yard.hall()).isPresent(), "no siege on a walled village");
		helper.onEachTick(() -> {
			String lost = changed(level, rest);
			if (!lost.isEmpty()) {
				helper.fail("a block that isn't a gate went at tick " + helper.getTick() + ": " + lost);
			}
			if (wallBricks(helper) != bricks) {
				helper.fail("the ram broke the player's wall: " + wallBricks(helper) + " of " + bricks + " bricks left");
			}
		});
		helper.runAtTickTime(1100, () -> {
			List<BlockPos> broken = gates.keySet().stream().filter(p -> !level.getBlockState(p).is(gates.get(p).getBlock())).toList();
			helper.assertTrue(!broken.isEmpty(), "the ram broke no gate in 1100 ticks: the test can't see the rule");
			for (BlockPos p : broken) {
				helper.assertTrue(ThreatData.get(level).damaged().contains(p), "a broken gate isn't on the builders' list: " + p.toShortString()
					+ ", list " + ThreatData.get(level).damaged());
			}
			helper.assertTrue(stillFinished(yard), "the siege lost the build's record");
			helper.succeed();
		});
	}

	/**
	 * A village walled only by a Stone Wall (no gate in it): a ram besieging it for 800 ticks breaks no block of the wall,
	 * and nothing goes on the list for the builders.
	 */
	//$ gametest_ticks_batch AREA '1000' '"qaRepairWallOnly"'
	@GameTest(template = AREA, timeoutTicks = 1000, batch = "qaRepairWallOnly")
	public void qaARamNeverBreaksAWallWithNoGate(GameTestHelper helper) {
		Yard yard = yard(helper, StarterBlueprints.STONE_WALL);
		ServerLevel level = yard.level();
		Map<BlockPos, BlockState> rest = nonGates(yard);
		Map<BlockPos, BlockState> all = new HashMap<>(rest);
		all.putAll(gates(yard));
		helper.assertTrue(rest.size() > 10, "the Stone Wall has only " + rest.size() + " blocks");
		long bricks = wallBricks(helper);
		int listed = ThreatData.get(level).damaged().size();
		Mob ram = ram(helper, yard);
		VillageRaids.track(level, yard.hall(), yard.culture(), List.of(ram));
		helper.onEachTick(() -> {
			String lost = changed(level, rest);
			if (!lost.isEmpty()) {
				helper.fail("the ram broke the Stone Wall at tick " + helper.getTick() + ": " + lost);
			}
		});
		helper.runAtTickTime(800, () -> {
			helper.assertTrue(ram.isAlive(), "the ram died: the test saw nothing");
			helper.assertTrue(wallBricks(helper) == bricks, "the player's wall lost bricks");
			helper.assertTrue(changed(level, all).isEmpty(), "the wall changed: " + changed(level, all));
			helper.assertTrue(ThreatData.get(level).damaged().size() == listed, "something went on the builders' list: " + ThreatData.get(level).damaged());
			helper.assertTrue(stillFinished(yard), "the siege lost the wall's record");
			helper.succeed();
		});
	}

	/**
	 * The gate the ram broke is put back by a builder facing the way it was built; the build is still finished; and in
	 * the next siege that gate stands in the line with its full 60 hit points, not the ones it had when it went.
	 */
	//$ gametest_ticks_batch AREA '2400' '"qaRepairGateNextSiege"'
	@GameTest(template = AREA, timeoutTicks = 2400, batch = "qaRepairGateNextSiege")
	public void qaARepairedGateFacesAsBuiltAndHoldsTheNextSiegeWhole(GameTestHelper helper) {
		Yard yard = yard(helper, StarterBlueprints.PALISADE_GATE);
		ServerLevel level = yard.level();
		Map<BlockPos, BlockState> gates = gates(yard);
		Mob ram = ram(helper, yard);
		VillageRaids.track(level, yard.hall(), yard.culture(), List.of(ram));
		BlockPos[] gone = {null};
		BlockState[] built = {null};
		Villager[] builder = {null};
		long[] when = {-1, -1}; // broke, put back
		helper.onEachTick(() -> {
			long tick = helper.getTick();
			if (gone[0] == null) {
				for (Map.Entry<BlockPos, BlockState> e : gates.entrySet()) {
					if (!level.getBlockState(e.getKey()).is(e.getValue().getBlock())) {
						gone[0] = e.getKey();
						built[0] = e.getValue();
					}
				}
				if (gone[0] != null) {
					when[0] = tick;
					endSiege(yard);
					builder[0] = builder(helper, yard, new ItemStack(built[0].getBlock().asItem(), 4));
					BuildSite site = Upkeep.look(level, builder[0]);
					helper.assertTrue(site != null && site.isRepair(), "no repair of the gate the ram broke");
				} else if (tick > 600) {
					helper.fail("the ram never broke a gate");
				}
				return;
			}
			if (when[1] < 0) {
				BlockState now = level.getBlockState(gone[0]);
				if (now.is(built[0].getBlock()) && !ModAttachments.BUILDER_JOB.has(builder[0])) {
					when[1] = tick;
					helper.assertTrue(now.getValue(FenceGateBlock.FACING) == built[0].getValue(FenceGateBlock.FACING),
						"the gate went back facing " + now.getValue(FenceGateBlock.FACING) + ", built facing " + built[0].getValue(FenceGateBlock.FACING));
					helper.assertTrue(stillFinished(yard), "the build's record was lost by the repair");
					Upkeep.look(level, builder[0]);
					helper.assertFalse(ThreatData.get(level).damaged().contains(gone[0]), "the gate is back but still on the list");
					// The next siege.
					Mob next = ram(helper, yard);
					VillageRaids.track(level, yard.hall(), yard.culture(), List.of(next));
					Optional<ThreatData.Siege> siege = Sieges.siege(level, yard.hall());
					helper.assertTrue(siege.isPresent() && !siege.get().over, "no second siege");
					helper.assertTrue(siege.get().gates.stream().anyMatch(g -> g.pos.equals(gone[0])), "the repaired gate isn't in the next siege's line: "
						+ siege.get().gates.stream().map(g -> g.pos.toShortString()).toList());
					helper.assertTrue(Sieges.hitPointsLeft(level, yard.hall(), gone[0]) == Sieges.FENCE_GATE_HP, "the repaired gate starts the next siege with "
						+ Sieges.hitPointsLeft(level, yard.hall(), gone[0]) + " hit points");
					helper.succeed();
				} else if (tick > when[0] + 1600) {
					helper.fail("the builder didn't put the gate back: " + now + ", repairing " + ModAttachments.BUILDER_JOB.has(builder[0]));
				}
			}
		});
	}

	/**
	 * The ram broke a gate while the builder had no gate to put back. The siege ends, the world is saved and loaded, and
	 * 600 ticks go by: the gate is still on the builders' list (nothing forgotten), and once gates are in the chest the
	 * builder puts it back.
	 */
	//$ gametest_ticks_batch AREA '3200' '"qaRepairNoMaterials"'
	@GameTest(template = AREA, timeoutTicks = 3200, batch = "qaRepairNoMaterials")
	public void qaAGateBrokenWithoutMaterialsIsNeverForgotten(GameTestHelper helper) {
		Yard yard = yard(helper, StarterBlueprints.PALISADE_GATE);
		ServerLevel level = yard.level();
		Map<BlockPos, BlockState> gates = gates(yard);
		Mob ram = ram(helper, yard);
		VillageRaids.track(level, yard.hall(), yard.culture(), List.of(ram));
		BlockPos[] gone = {null};
		BlockState[] built = {null};
		Villager[] builder = {null};
		long[] when = {-1, -1}; // broke, stocked
		helper.onEachTick(() -> {
			long tick = helper.getTick();
			if (gone[0] == null) {
				for (Map.Entry<BlockPos, BlockState> e : gates.entrySet()) {
					if (!level.getBlockState(e.getKey()).is(e.getValue().getBlock())) {
						gone[0] = e.getKey();
						built[0] = e.getValue();
					}
				}
				if (gone[0] != null) {
					when[0] = tick;
					endSiege(yard);
					ThreatData.get(level).roundTrip(level.registryAccess()); // a save and load once the siege is over
					helper.assertTrue(ThreatData.get(level).damaged().contains(gone[0]), "the gate left the list with the siege's end and a load");
					builder[0] = builder(helper, yard, ItemStack.EMPTY);
					Upkeep.look(level, builder[0]);
				} else if (tick > 600) {
					helper.fail("the ram never broke a gate");
				}
				return;
			}
			if (when[1] < 0) {
				if (tick == when[0] + 300) {
					Upkeep.look(level, builder[0]); // a second look with nothing to build with
				}
				if (tick >= when[0] + 600) {
					helper.assertTrue(level.getBlockState(gone[0]).isAir(), "with no gate in the chest the gap became " + level.getBlockState(gone[0]));
					helper.assertTrue(ThreatData.get(level).damaged().contains(gone[0]), "with no materials the broken gate was dropped from the list: "
						+ ThreatData.get(level).damaged());
					helper.assertTrue(stillFinished(yard), "the build's record was lost");
					Container chest = helper.getBlockEntity(CHEST);
					chest.setItem(0, new ItemStack(built[0].getBlock().asItem(), 4));
					workingMorning(level);
					if (!ModAttachments.BUILDER_JOB.has(builder[0])) {
						BuildSite site = Upkeep.look(level, builder[0]);
						helper.assertTrue(site != null && site.isRepair(), "with the gates in the chest no repair starts");
					}
					when[1] = tick;
				}
				return;
			}
			if (level.getBlockState(gone[0]).is(built[0].getBlock())) {
				helper.succeed();
			} else if (tick > when[1] + 1800) {
				var site = Builders.activeSite(level, builder[0]);
				helper.fail("the builder never put the gate back once the materials came: " + level.getBlockState(gone[0])
					+ (site == null ? ", no site" : ", site " + site.stage() + " " + site.status() + " " + site.detail() + ", skipped " + site.skipped()));
			}
		});
	}

	/**
	 * {@code siegeDamage} stays on by default, also for a config file written before the key existed; with it off a ram
	 * at the gate for 400 ticks takes nothing and the builders have nothing to put back. The sentences a player reads
	 * about it say what the rule is.
	 */
	//$ gametest_ticks_batch AREA '600' '"qaRepairDamageOff"'
	@GameTest(template = AREA, timeoutTicks = 600, batch = "qaRepairDamageOff")
	public void qaSiegeDamageIsOnByDefaultAndOffNothingIsTaken(GameTestHelper helper) {
		helper.assertTrue(new WorkplaceConfig().siegeDamage, "siegeDamage is off by default");
		WorkplaceConfig old = WorkplaceConfig.parse("{\"sieges\": true, \"builderRepairs\": true}");
		helper.assertTrue(old.siegeDamage, "a config file without the key turns siege damage off");
		Language lang = Language.getInstance();
		String tooltip = lang.getOrDefault("aliveworkplace.config.siegeDamage.tooltip");
		helper.assertTrue(tooltip.contains("gate blocks") && tooltip.contains("put back"), "the siegeDamage tooltip: " + tooltip);
		helper.assertTrue(lang.getOrDefault("screen.aliveworkplace.repairs.threats").contains("repaired without asking"),
			"the repairs page doesn't say threat damage is repaired: " + lang.getOrDefault("screen.aliveworkplace.repairs.threats"));
		Yard yard = yard(helper, StarterBlueprints.PALISADE_GATE);
		ServerLevel level = yard.level();
		WorkplaceConfig.parse("{\"siegeDamage\": false}").apply();
		Map<BlockPos, BlockState> all = nonGates(yard);
		all.putAll(gates(yard));
		int listed = ThreatData.get(level).damaged().size();
		Mob ram = ram(helper, yard);
		VillageRaids.track(level, yard.hall(), yard.culture(), List.of(ram));
		helper.runAtTickTime(450, () -> {
			helper.assertTrue(changed(level, all).isEmpty(), "with siegeDamage off the build lost: " + changed(level, all));
			helper.assertTrue(ThreatData.get(level).damaged().size() == listed, "with siegeDamage off something went on the list");
			helper.succeed();
		});
	}
}
