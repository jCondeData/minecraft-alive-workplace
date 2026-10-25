package io.github.jcondedata.aliveworkplace.test;

import io.github.jcondedata.aliveworkplace.WorkplaceConfig;
import io.github.jcondedata.aliveworkplace.blueprint.Blueprint;
import io.github.jcondedata.aliveworkplace.blueprint.BlueprintData;
import io.github.jcondedata.aliveworkplace.blueprint.BlueprintItem;
import io.github.jcondedata.aliveworkplace.blueprint.BlueprintLibrary;
import io.github.jcondedata.aliveworkplace.blueprint.StarterBlueprints;
import io.github.jcondedata.aliveworkplace.build.BuildSiteManager;
import io.github.jcondedata.aliveworkplace.guard.BattleStations;
import io.github.jcondedata.aliveworkplace.guard.Gates;
import io.github.jcondedata.aliveworkplace.guard.VillageRaids;
import io.github.jcondedata.aliveworkplace.hall.DefencePage;
import io.github.jcondedata.aliveworkplace.hall.VillageHalls;
import io.github.jcondedata.aliveworkplace.registry.ModBlocks;
import io.github.jcondedata.aliveworkplace.threat.Culture;
import io.github.jcondedata.aliveworkplace.threat.Sieges;
import io.github.jcondedata.aliveworkplace.threat.ThreatData;
import io.github.jcondedata.aliveworkplace.threat.Threats;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponents;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.ItemLore;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.FenceGateBlock;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

/**
 * QA (lane-d-1025-0408): ROADMAP 32.20, the defence upgrades, from the spec. Each upgrade is "lined up over the finished
 * build": clicked onto its finished base it lines up, and not onto the same blocks unrecorded, a shift-click, or another
 * build. Gatehouse II in a siege: "both portcullises drop" and both are drawn up at dawn; "the barbican's gate is the first
 * breach", and the rams break only gate blocks. What the item says each upgrade adds is in its drawing: the dripstone
 * ditch in front of Palisade II and its fighting step a battle station, Stone Wall II's murder holes, Wall Tower II's
 * roofed top over its gallery, Gatehouse II's murder holes over the gateway.
 */
public class QaDefenceUpgradeGameTests implements FabricGameTest {
	private static final org.slf4j.Logger LOG = org.slf4j.LoggerFactory.getLogger("QaDefenceUpgradeGameTests");
	private static final String AREA = "aliveworkplace_test:huge_area";
	private static final ResourceLocation BESIEGERS = ResourceLocation.fromNamespaceAndPath("aliveworkplace_test", "qa_upgrade_besiegers");
	private static final BlockPos HALL = new BlockPos(14, 2, 8);
	private static final int WEST = 5;
	private static final int EAST = 23;
	private static final int BACK = 4;
	private static final int FRONT = 15;

	// --- lined up over the finished build -------------------------------------------------------------------------

	/**
	 * All four upgrades, each over its own base standing in the area. Before the base is recorded finished, clicking the
	 * upgrade onto it doesn't line it up over the base; once finished, a shift-click still doesn't (it places freely) and a
	 * plain click does, exactly. Gatehouse II clicked onto a finished Wall Tower doesn't line up over the tower.
	 */
	//$ gametest_ticks_batch AREA '200' '"qaDefenceLineUp"'
	@GameTest(template = AREA, timeoutTicks = 200, batch = "qaDefenceLineUp")
	public void anUpgradeLinesUpOnlyOverItsOwnFinishedBase(GameTestHelper helper) {
		Leftovers.clear(helper);
		Leftovers.finished(helper);
		ServerLevel level = helper.getLevel();
		BuildSiteManager sites = BuildSiteManager.get(level);
		Map<StarterBlueprints.Entry, StarterBlueprints.Entry> pairs = new java.util.LinkedHashMap<>();
		pairs.put(StarterBlueprints.PALISADE, StarterBlueprints.PALISADE_2);
		pairs.put(StarterBlueprints.STONE_WALL, StarterBlueprints.STONE_WALL_2);
		pairs.put(StarterBlueprints.WALL_TOWER, StarterBlueprints.WALL_TOWER_2);
		pairs.put(StarterBlueprints.GATEHOUSE, StarterBlueprints.GATEHOUSE_2);
		List<BlockPos> origins = List.of(new BlockPos(2, 2, 3), new BlockPos(11, 2, 3), new BlockPos(20, 2, 3), new BlockPos(4, 2, 14));
		ServerPlayer player = helper.makeMockServerPlayerInLevel();
		Leftovers.after(helper, () -> level.getServer().getPlayerList().remove(player));
		Map<StarterBlueprints.Entry, BlueprintData.Placement> placements = new HashMap<>();
		Map<StarterBlueprints.Entry, BlockPos> clickAt = new HashMap<>();
		int i = 0;
		for (StarterBlueprints.Entry base : pairs.keySet()) {
			BlockPos origin = helper.absolutePos(origins.get(i++));
			level.getStructureManager().get(base.id()).orElseThrow().placeInWorld(level, origin, origin, new StructurePlaceSettings(), RandomSource.create(1L), 2);
			BlueprintData.Placement placement = new BlueprintData.Placement(level.dimension().location(), origin, Rotation.NONE, Mirror.NONE);
			placements.put(base, placement);
			Leftovers.after(helper, () -> sites.forgetFinished(placement));
			// a solid block of the base, well inside it
			Blueprint b = BlueprintLibrary.get(level, base.id()).orElseThrow();
			BlockPos block = b.blocks().stream().filter(e -> e.pos().getY() >= 1 && !e.state().isAir() && e.state().isSolid())
				.map(Blueprint.Entry::pos).findFirst().orElseThrow();
			clickAt.put(base, origin.offset(block));
		}
		for (Map.Entry<StarterBlueprints.Entry, StarterBlueprints.Entry> e : pairs.entrySet()) {
			StarterBlueprints.Entry base = e.getKey();
			StarterBlueprints.Entry up = e.getValue();
			Optional<BlueprintData.Placement> unrecorded = click(player, up, clickAt.get(base), false);
			helper.assertFalse(unrecorded.equals(Optional.of(placements.get(base))), up.id() + " lined up over a " + base.id() + " no builder finished");
		}
		for (StarterBlueprints.Entry base : pairs.keySet()) {
			sites.recordFinished(base.id(), placements.get(base), UUID.randomUUID());
		}
		for (Map.Entry<StarterBlueprints.Entry, StarterBlueprints.Entry> e : pairs.entrySet()) {
			StarterBlueprints.Entry base = e.getKey();
			StarterBlueprints.Entry up = e.getValue();
			Optional<BlueprintData.Placement> shifted = click(player, up, clickAt.get(base), true);
			helper.assertFalse(shifted.equals(Optional.of(placements.get(base))), up.id() + " lined up on a shift-click");
			Optional<BlueprintData.Placement> lined = click(player, up, clickAt.get(base), false);
			helper.assertTrue(lined.equals(Optional.of(placements.get(base))), up.id() + " didn't line up over its finished " + base.id() + ": " + lined);
		}
		Optional<BlueprintData.Placement> wrong = click(player, StarterBlueprints.GATEHOUSE_2, clickAt.get(StarterBlueprints.WALL_TOWER), false);
		helper.assertFalse(wrong.equals(Optional.of(placements.get(StarterBlueprints.WALL_TOWER))), "Gatehouse II lined up over a finished Wall Tower");
		helper.succeed();
	}

	/**
	 * A builder upgrades a finished Gatehouse to Gatehouse II over it. While the work goes on the Gatehouse is still the
	 * village's (its three gates open and shut with the round); once it's done the spot holds one finished build, Gatehouse
	 * II, and not the Gatehouse beside it: the round opens and shuts six fence gates, not nine, and the barbican is the
	 * siege's breach.
	 */
	//$ gametest_ticks_batch AREA '12000' '"qaGatehouseUpgradeRecord"'
	@GameTest(template = AREA, timeoutTicks = 12000, batch = "qaGatehouseUpgradeRecord")
	public void anUpgradedGatehouseIsOneGatehouseII(GameTestHelper helper) {
		Leftovers.clear(helper);
		Leftovers.halls(helper);
		Leftovers.finished(helper);
		ServerLevel level = helper.getLevel();
		int delay = level.getGameRules().getInt(io.github.jcondedata.aliveworkplace.registry.ModGameRules.BUILD_DELAY);
		boolean help = level.getGameRules().getBoolean(io.github.jcondedata.aliveworkplace.registry.ModGameRules.BUILDERS_HELP);
		long time = level.getDayTime();
		Leftovers.after(helper, () -> {
			level.getGameRules().getRule(io.github.jcondedata.aliveworkplace.registry.ModGameRules.BUILD_DELAY).set(delay, level.getServer());
			level.getGameRules().getRule(io.github.jcondedata.aliveworkplace.registry.ModGameRules.BUILDERS_HELP).set(help, level.getServer());
			level.setDayTime(time);
		});
		helper.setDayTime(2000);
		level.getGameRules().getRule(io.github.jcondedata.aliveworkplace.registry.ModGameRules.BUILD_DELAY).set(2, level.getServer());
		level.getGameRules().getRule(io.github.jcondedata.aliveworkplace.registry.ModGameRules.BUILDERS_HELP).set(false, level.getServer());
		BlockPos originRel = new BlockPos(10, 2, 8);
		BlockPos origin = helper.absolutePos(originRel);
		BlockPos hall = helper.absolutePos(new BlockPos(15, 2, 4));
		BlueprintData.Placement placement = new BlueprintData.Placement(level.dimension().location(), origin, Rotation.NONE, Mirror.NONE);
		level.getStructureManager().get(StarterBlueprints.GATEHOUSE.id()).orElseThrow().placeInWorld(level, origin, origin, new StructurePlaceSettings(), RandomSource.create(1L), 2);
		BuildSiteManager sites = BuildSiteManager.get(level);
		sites.recordFinished(StarterBlueprints.GATEHOUSE.id(), placement, UUID.randomUUID());
		Leftovers.after(helper, () -> sites.forgetFinished(placement));
		BlockPos bench = new BlockPos(3, 2, 3);
		BlockPos[] chests = {new BlockPos(3, 2, 6), new BlockPos(3, 2, 8)};
		helper.setBlock(bench, ModBlocks.BUILDERS_BENCH);
		for (BlockPos c : chests) {
			helper.setBlock(c, Blocks.CHEST);
		}
		var report = io.github.jcondedata.aliveworkplace.build.BlueprintSupplies.check(level,
			new BlueprintData(StarterBlueprints.GATEHOUSE_2.id(), Optional.of(StarterBlueprints.GATEHOUSE_2.size()), Optional.of(placement))).orElseThrow();
		List<ItemStack> stacks = new ArrayList<>();
		for (var m : report.missing()) {
			for (int left = m.count(); left > 0; left -= m.item().getDefaultMaxStackSize()) {
				stacks.add(new ItemStack(m.item(), Math.min(left, m.item().getDefaultMaxStackSize())));
			}
		}
		helper.assertTrue(stacks.size() <= 27 * chests.length, "the upgrade needs " + stacks.size() + " stacks");
		for (int i = 0; i < stacks.size(); i++) {
			((net.minecraft.world.Container) helper.getBlockEntity(chests[i / 27])).setItem(i % 27, stacks.get(i));
		}
		Villager builder = helper.spawn(EntityType.VILLAGER, new BlockPos(5, 2, 3));
		io.github.jcondedata.aliveworkplace.build.Builders.employ(level, builder, helper.absolutePos(bench));
		var site = io.github.jcondedata.aliveworkplace.build.Builders.start(level, builder, null, StarterBlueprints.GATEHOUSE_2.id(), placement);
		helper.assertTrue(site != null, "the builder didn't take the upgrade");
		boolean[] checkedMidway = {false};
		helper.onEachTick(() -> {
			if (sites.get(site.id()) != null) {
				if (!checkedMidway[0] && site.placed() > 0) {
					checkedMidway[0] = true;
					helper.assertTrue(sites.finishedNear(level, hall, 20).stream().anyMatch(f -> f.placement().equals(placement)),
						"the Gatehouse stopped being a finished build as soon as its upgrade began");
				}
				return;
			}
			List<BuildSiteManager.Finished> here = sites.finishedNear(level, hall, 20).stream().filter(f -> f.placement().equals(placement)).toList();
			helper.assertTrue(here.size() == 1 && here.get(0).structure().equals(StarterBlueprints.GATEHOUSE_2.id()), "finished on the spot: " + here);
			helper.assertTrue(level.getBlockState(origin.offset(5, 1, 8)).getBlock() instanceof FenceGateBlock, "the barbican's gate wasn't built");
			int opened = Gates.shut(level, hall, false);
			int shut = Gates.shut(level, hall, true);
			helper.assertTrue(opened == 6 && shut == 6, "the round opened " + opened + " and shut " + shut + " fence gates, not six");
			helper.assertTrue(checkedMidway[0], "never saw the work under way");
			LOG.info("QA: Gatehouse II built over the Gatehouse by tick {}, placed {}, round {} gates", helper.getTick(), site.placed(), opened);
			helper.succeed();
		});
	}

	/** Clicks a fresh blueprint of {@code entry} onto {@code pos} (from the west), as a player; returns where it lies. */
	private static Optional<BlueprintData.Placement> click(ServerPlayer player, StarterBlueprints.Entry entry, BlockPos pos, boolean shift) {
		ItemStack stack = BlueprintItem.create(entry.id(), entry.size());
		player.setItemInHand(InteractionHand.MAIN_HAND, stack);
		player.setShiftKeyDown(shift);
		try {
			stack.useOn(new UseOnContext(player, InteractionHand.MAIN_HAND, new BlockHitResult(Vec3.atCenterOf(pos), Direction.WEST, pos, false)));
		} finally {
			player.setShiftKeyDown(false);
		}
		return BlueprintItem.data(player.getMainHandItem()).flatMap(BlueprintData::placement);
	}

	// --- what each drawing adds ----------------------------------------------------------------------------------

	/**
	 * Palisade II: a ditch of pointed dripstone stakes pointing up in front (outside, past the base's own depth: the side
	 * the Gatehouse II's barbican goes out to), and a fighting step along its inside. Stone Wall II: its hoardings'
	 * gallery floor has murder holes (openings) between solid boards. Wall Tower II: a roof over every station of its gallery.
	 * Gatehouse II: murder holes in the passage roof between the two gates, over the gateway.
	 */
	//$ gametest EMPTY_STRUCTURE
	@GameTest(template = EMPTY_STRUCTURE)
	public void theUpgradesAddWhatTheItemPromises(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		Blueprint palisade = BlueprintLibrary.get(level, StarterBlueprints.PALISADE_2.id()).orElseThrow();
		List<BlockPos> stakes = palisade.blocks().stream().filter(e -> e.state().is(Blocks.POINTED_DRIPSTONE)).map(Blueprint.Entry::pos).toList();
		helper.assertTrue(stakes.size() >= 6, "Palisade II's ditch has " + stakes.size() + " stakes");
		int baseDepth = StarterBlueprints.PALISADE.size().getZ();
		helper.assertTrue(stakes.stream().allMatch(p -> p.getZ() >= baseDepth), "a stake isn't out in front of the palisade: " + stakes);
		helper.assertTrue(stakes.stream().allMatch(p -> palisade.blocks().stream().anyMatch(e -> e.state().is(Blocks.POINTED_DRIPSTONE) && e.pos().equals(p)
			&& e.state().getValue(net.minecraft.world.level.block.PointedDripstoneBlock.TIP_DIRECTION) == Direction.UP)), "a stake doesn't point up");
		// The fighting step as a battle station: QaDefenceUpgradeBugTests (Palisade II has none, B on tests/defence-upgrades-1025).
		Map<BlockPos, BlockState> palisadeAt = states(palisade);
		helper.assertTrue(java.util.stream.IntStream.range(0, 7).filter(x -> x != 3).allMatch(x -> palisadeAt.containsKey(new BlockPos(x, 2, 0))
			|| palisadeAt.containsKey(new BlockPos(x, 3, 0))), "Palisade II has no fighting step along its inside");

		Blueprint wall = BlueprintLibrary.get(level, StarterBlueprints.STONE_WALL_2.id()).orElseThrow();
		Map<BlockPos, BlockState> at = states(wall);
		int holes = 0;
		int boards = 0;
		for (int x = 0; x < wall.size().getX(); x++) {
			BlockState floor = at.getOrDefault(new BlockPos(x, 4, 3), Blocks.AIR.defaultBlockState());
			if (floor.isAir()) {
				holes++;
			} else {
				boards++;
			}
		}
		helper.assertTrue(holes >= 2 && boards >= 2, "Stone Wall II's gallery floor has " + holes + " murder holes and " + boards + " boards");
		helper.assertTrue(wall.blocks().stream().anyMatch(e -> e.pos().getY() >= 6 && e.state().is(net.minecraft.tags.BlockTags.STAIRS)),
			"Stone Wall II's walkway has no roof");

		Blueprint tower = BlueprintLibrary.get(level, StarterBlueprints.WALL_TOWER_2.id()).orElseThrow();
		Map<BlockPos, BlockState> towerAt = states(tower);
		List<BlockPos> gallery = BattleStations.spots(tower).stream().filter(p -> p.getY() == 7).toList();
		long sides = gallery.stream().filter(p -> p.getX() == 0 || p.getX() == 4 || p.getZ() == 0 || p.getZ() == 4).count();
		helper.assertTrue(sides >= 8, "Wall Tower II's gallery has " + sides + " stations on its sides: " + gallery);
		for (BlockPos p : gallery) {
			boolean roofed = false;
			for (int y = p.getY() + 3; y < tower.size().getY(); y++) {
				roofed |= towerAt.containsKey(new BlockPos(p.getX(), y, p.getZ()));
			}
			helper.assertTrue(roofed, "no roof over Wall Tower II's station at " + p);
		}

		Blueprint gate = BlueprintLibrary.get(level, StarterBlueprints.GATEHOUSE_2.id()).orElseThrow();
		Map<BlockPos, BlockState> gateAt = states(gate);
		// the passage between the gatehouse's portcullis (z 3) and the barbican's (z 7), its roof at y 4
		int roof = 0;
		int murder = 0;
		for (int z = 4; z <= 6; z++) {
			for (int x = 4; x <= 6; x++) {
				BlockPos p = new BlockPos(x, 4, z);
				if (gateAt.containsKey(p)) {
					roof++;
				} else if (!gateAt.containsKey(p.above())) {
					murder++; // open up through the roof
				}
			}
			for (int x = 4; x <= 6; x++) {
				for (int y = 1; y <= 3; y++) {
					helper.assertFalse(gateAt.containsKey(new BlockPos(x, y, z)), "the barbican's passage is blocked at " + x + ", " + y + ", " + z);
				}
			}
		}
		helper.assertTrue(murder >= 1 && roof >= 4, "Gatehouse II's passage roof has " + murder + " murder holes and " + roof + " roof blocks");
		helper.succeed();
	}

	private static Map<BlockPos, BlockState> states(Blueprint b) {
		Map<BlockPos, BlockState> at = new HashMap<>();
		b.blocks().forEach(e -> {
			if (!e.state().isAir()) {
				at.put(e.pos(), e.state());
			}
		});
		return at;
	}

	// --- Gatehouse II in a siege ----------------------------------------------------------------------------------

	private record Yard(ServerLevel level, BlockPos hall, BlockPos origin, Culture culture, Villager villager) {
		BlockPos at(int x, int y, int z) {
			return origin.offset(x, y, z);
		}
	}

	/** A walled yard with a finished Gatehouse II in its front wall, the outside to the south, a villager in a stone cell. */
	private static Yard yard(GameTestHelper helper) {
		Leftovers.clear(helper);
		Leftovers.finished(helper);
		ServerLevel level = helper.getLevel();
		int radius = VillageHalls.RADIUS;
		long time = level.getDayTime();
		boolean griefing = level.getGameRules().getBoolean(GameRules.RULE_MOBGRIEFING);
		VillageHalls.RADIUS = 20;
		BlockPos hall = helper.absolutePos(HALL);
		Culture culture = Culture.read(BESIEGERS, com.google.gson.JsonParser.parseString("{\"where\": {\"min_villagers\": 100000}, \"roster\": ["
			+ "{\"entity\": \"minecraft:ravager\", \"share\": 34, \"role\": \"ram\"}, {\"entity\": \"minecraft:pillager\", \"share\": 66, \"role\": \"ranged\"}],"
			+ " \"tactics\": [\"ram_gates\"]}").getAsJsonObject());
		Map<ResourceLocation, Culture> loaded = new java.util.LinkedHashMap<>();
		Threats.all().forEach(c -> loaded.put(c.id(), c));
		Map<ResourceLocation, Culture> with = new java.util.LinkedHashMap<>(loaded);
		with.put(BESIEGERS, culture);
		Threats.setForTest(with);
		Leftovers.after(helper, () -> {
			Threats.setForTest(loaded);
			level.getEntitiesOfClass(Mob.class, helper.getBounds().inflate(48), m -> m.getTags().contains(VillageRaids.TAG)).forEach(Mob::discard);
			Sieges.siege(level, hall).ifPresent(s -> Sieges.lift(level, s));
			VillageRaids.forget(hall);
			ThreatData.get(level).forgetSiege(hall);
			ThreatData.get(level).forgetPast(hall);
			Sieges.forget();
			VillageHalls.RADIUS = radius;
			level.getGameRules().getRule(GameRules.RULE_MOBGRIEFING).set(griefing, level.getServer());
			level.setDayTime(time);
			new WorkplaceConfig().apply();
		});
		StarterBlueprints.Entry build = StarterBlueprints.GATEHOUSE_2;
		int width = build.size().getX();
		int x0 = 14 - width / 2;
		int z0 = FRONT - 3;
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
		BuildSiteManager sites = BuildSiteManager.get(level);
		sites.recordFinished(build.id(), placement, UUID.randomUUID());
		Leftovers.after(helper, () -> sites.forgetFinished(placement));
		for (int x = 9; x <= 11; x++) {
			for (int z = 6; z <= 8; z++) {
				for (int y = 2; y <= 4; y++) {
					helper.setBlock(new BlockPos(x, y, z), x == 10 && z == 7 && y < 4 ? Blocks.AIR : Blocks.STONE_BRICKS);
				}
			}
		}
		Villager villager = helper.spawn(EntityType.VILLAGER, new BlockPos(10, 2, 7));
		return new Yard(level, hall, origin, culture, villager);
	}

	private static Mob raider(GameTestHelper helper, Yard yard, Culture.Role role, double x, double z) {
		ServerLevel level = yard.level();
		Culture.Member member = yard.culture().roster().stream().filter(m -> m.role() == role).findFirst().orElseThrow();
		Mob mob = Threats.create(level, member);
		helper.assertTrue(mob != null, "no " + member.entity());
		BlockPos base = helper.absolutePos(BlockPos.ZERO);
		mob.moveTo(base.getX() + x, base.getY() + 2, base.getZ() + z, 180f, 0f);
		mob.finalizeSpawn(level, level.getCurrentDifficultyAt(mob.blockPosition()), MobSpawnType.EVENT, null);
		Threats.outfit(level, mob, yard.culture(), member);
		mob.setTarget(yard.villager());
		level.addFreshEntityWithPassengers(mob);
		return mob;
	}

	private static List<String> lines(ItemStack icon) {
		List<String> lines = new ArrayList<>();
		lines.add(icon.getHoverName().getString());
		ItemLore lore = icon.get(DataComponents.LORE);
		if (lore != null) {
			lore.lines().forEach(line -> lines.add(line.getString()));
		}
		return lines;
	}

	/**
	 * A raid through its own entry point on a village with Gatehouse II: both gates shut and both portcullises drop (the
	 * Defence page says down); after the raid they stay down until the first dawn after, and then both are drawn up again
	 * (the bars back at the top, the way clear), all six gates open and the page reads as before.
	 */
	//$ gametest_ticks_batch AREA '300' '"qaGatehouseIIDawn"'
	@GameTest(template = AREA, timeoutTicks = 300, batch = "qaGatehouseIIDawn")
	public void bothPortcullisesOfGatehouseIIAreDrawnUpAtDawn(GameTestHelper helper) {
		Yard yard = yard(helper);
		ServerLevel level = yard.level();
		helper.setBlock(HALL, ModBlocks.VILLAGE_HALL);
		helper.setDayTime(6000);
		ServerPlayer player = helper.makeMockServerPlayerInLevel();
		Leftovers.after(helper, () -> level.getServer().getPlayerList().remove(player));
		List<BlockPos> gates = new ArrayList<>();
		List<BlockPos> dropped = new ArrayList<>();
		List<BlockPos> drawnUp = new ArrayList<>();
		for (int x = 4; x <= 6; x++) {
			for (int z : new int[] {2, 8}) {
				gates.add(yard.at(x, 1, z));
			}
			for (int z : new int[] {3, 7}) {
				dropped.add(yard.at(x, 1, z));
				dropped.add(yard.at(x, 2, z));
				drawnUp.add(yard.at(x, 3, z));
			}
		}
		Gates.shut(level, yard.hall(), false); // morning: the hall's round has opened them
		gates.forEach(g -> helper.assertTrue(level.getBlockState(g).getValue(FenceGateBlock.OPEN), "the gate at " + g + " isn't open in the morning"));
		List<String> before = lines(DefencePage.gates(level, yard.hall()));
		helper.assertTrue(before.contains("Portcullis: drawn up"), "the page before: " + before);
		VillageRaids.Raid raid = VillageRaids.start(level, yard.hall(), 0, 0, Math.PI / 2, yard.culture(), RandomSource.create(7L));
		helper.assertTrue(raid != null, "no raid");
		Optional<ThreatData.Siege> siege = Sieges.siege(level, yard.hall());
		helper.assertTrue(siege.isPresent(), "a raid by rams on a village with Gatehouse II isn't a siege");
		gates.forEach(g -> helper.assertTrue(!level.getBlockState(g).getValue(FenceGateBlock.OPEN), "the gate at " + g + " is still open in the siege"));
		dropped.forEach(b -> helper.assertTrue(level.getBlockState(b).is(Blocks.IRON_BARS), "no bar dropped at " + b));
		List<String> during = lines(DefencePage.gates(level, yard.hall()));
		helper.assertTrue(during.contains("Portcullis: down"), "the page in the siege: " + during);
		long dawn = siege.get().dawn;
		helper.runAtTickTime(40, () -> {
			VillageRaids.raiders(level, yard.hall()).forEach(Mob::discard);
			VillageRaids.tick(level, yard.hall(), 0, 0, -100, day -> { });
			helper.assertTrue(VillageRaids.active(yard.hall()).isEmpty(), "the raid isn't over");
			dropped.forEach(b -> helper.assertTrue(level.getBlockState(b).is(Blocks.IRON_BARS), "a portcullis went up before dawn at " + b));
			level.setDayTime(dawn + 20);
		});
		helper.runAtTickTime(120, () -> {
			helper.assertTrue(Sieges.siege(level, yard.hall()).isEmpty(), "the siege wasn't lifted at dawn");
			dropped.forEach(b -> helper.assertTrue(level.getBlockState(b).isAir(), "a bar is still down at " + b + ": " + level.getBlockState(b)));
			drawnUp.forEach(b -> helper.assertTrue(level.getBlockState(b).is(Blocks.IRON_BARS), "the drawn-up bars are gone at " + b));
			gates.forEach(g -> helper.assertTrue(level.getBlockState(g).getValue(FenceGateBlock.OPEN), "the gate at " + g + " didn't open at dawn"));
			List<String> after = lines(DefencePage.gates(level, yard.hall()));
			helper.assertTrue(after.equals(before), "the page after: " + after + ", before: " + before);
			helper.succeed();
		});
	}

	/**
	 * Rams outside Gatehouse II: the first block they break is the barbican's gate (z 8), while the gatehouse's own gate
	 * and both portcullises still stand, and no block of the build that isn't a gate or a bar is touched.
	 */
	//$ gametest_ticks_batch AREA '2400' '"qaGatehouseIIFirstBreach"'
	@GameTest(template = AREA, timeoutTicks = 2400, batch = "qaGatehouseIIFirstBreach")
	public void theBarbicansGateIsTheFirstBreach(GameTestHelper helper) {
		Yard yard = yard(helper);
		ServerLevel level = yard.level();
		StarterBlueprints.Entry build = StarterBlueprints.GATEHOUSE_2;
		Map<BlockPos, BlockState> rest = new HashMap<>();
		for (BlockPos p : BlockPos.betweenClosed(yard.origin(), yard.origin().offset(build.size()).offset(-1, -1, -1))) {
			BlockState state = level.getBlockState(p);
			if (!state.isAir() && !Sieges.isGate(state)) {
				rest.put(p.immutable(), state);
			}
		}
		Mob ram = raider(helper, yard, Culture.Role.RAM, 14.5, 24.5);
		Mob pillager = raider(helper, yard, Culture.Role.RANGED, 14.5, 27.5);
		VillageRaids.track(level, yard.hall(), yard.culture(), List.of(ram, pillager));
		List<BlockPos> outer = List.of(yard.at(4, 1, 8), yard.at(5, 1, 8), yard.at(6, 1, 8));
		List<BlockPos> inner = new ArrayList<>(List.of(yard.at(4, 1, 2), yard.at(5, 1, 2), yard.at(6, 1, 2)));
		for (int x = 4; x <= 6; x++) {
			for (int z : new int[] {3, 7}) {
				inner.add(yard.at(x, 1, z));
				inner.add(yard.at(x, 2, z));
			}
		}
		outer.forEach(p -> helper.assertTrue(level.getBlockState(p).getBlock() instanceof FenceGateBlock, "no barbican gate at " + p.subtract(yard.origin()).toShortString()));
		inner.forEach(p -> helper.assertTrue(Sieges.isGate(level.getBlockState(p)), "no gate or dropped bar at " + p.subtract(yard.origin()).toShortString()));
		helper.onEachTick(() -> {
			boolean outerBroken = outer.stream().anyMatch(p -> !(level.getBlockState(p).getBlock() instanceof FenceGateBlock));
			for (BlockPos p : inner) {
				if (!Sieges.isGate(level.getBlockState(p))) {
					helper.fail("a block behind the barbican's gate went first: " + p.subtract(yard.origin()).toShortString() + " is " + level.getBlockState(p)
						+ (outerBroken ? "" : " while the barbican's gate stands"));
				}
			}
			if (outerBroken) {
				for (Map.Entry<BlockPos, BlockState> e : rest.entrySet()) {
					helper.assertTrue(level.getBlockState(e.getKey()).is(e.getValue().getBlock()), "the rams broke a block that isn't a gate: "
						+ e.getKey().subtract(yard.origin()).toShortString() + " is " + level.getBlockState(e.getKey()));
				}
				List<BlockPos> broken = ThreatData.get(level).brokenGates(yard.hall());
				helper.assertTrue(broken.stream().anyMatch(outer::contains), "the broken barbican gate isn't on the list for the builder: " + broken);
				helper.assertTrue(helper.getTick() > 20, "the barbican's gate went at tick " + helper.getTick() + ": before any ram reached it");
				LOG.info("QA: the barbican's gate broke first, at tick {}, broken {}", helper.getTick(), broken.size());
				helper.succeed();
			} else if (helper.getTick() > 2200) {
				helper.fail("the barbican's gate wasn't broken within 2200 ticks: ram at " + ram.position() + ", gate hit points "
					+ Sieges.hitPointsLeft(level, yard.hall(), outer.get(1)));
			}
		});
	}
}
