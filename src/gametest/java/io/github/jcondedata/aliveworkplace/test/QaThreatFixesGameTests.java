package io.github.jcondedata.aliveworkplace.test;

import io.github.jcondedata.aliveworkplace.Expansions;
import io.github.jcondedata.aliveworkplace.WorkplaceConfig;
import io.github.jcondedata.aliveworkplace.blueprint.BlueprintData;
import io.github.jcondedata.aliveworkplace.blueprint.StarterBlueprints;
import io.github.jcondedata.aliveworkplace.build.BuildSiteManager;
import io.github.jcondedata.aliveworkplace.guard.BanditCamps;
import io.github.jcondedata.aliveworkplace.guard.Escorts;
import io.github.jcondedata.aliveworkplace.guard.RallyBannerItem;
import io.github.jcondedata.aliveworkplace.guard.VillageRaids;
import io.github.jcondedata.aliveworkplace.guard.WarParties;
import io.github.jcondedata.aliveworkplace.hall.DefencePage;
import io.github.jcondedata.aliveworkplace.hall.VillageHalls;
import io.github.jcondedata.aliveworkplace.registry.ModBlocks;
import io.github.jcondedata.aliveworkplace.registry.ModComponents;
import io.github.jcondedata.aliveworkplace.registry.ModItems;
import io.github.jcondedata.aliveworkplace.threat.Culture;
import io.github.jcondedata.aliveworkplace.threat.Ladders;
import io.github.jcondedata.aliveworkplace.threat.Lairs;
import io.github.jcondedata.aliveworkplace.threat.SandRamps;
import io.github.jcondedata.aliveworkplace.threat.Sieges;
import io.github.jcondedata.aliveworkplace.threat.ThreatData;
import io.github.jcondedata.aliveworkplace.threat.Threats;
import io.github.jcondedata.aliveworkplace.work.ChoiceMenu;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings;
import net.minecraft.world.phys.AABB;

/**
 * QA (lane-d-1025-0408) regression tests for four landed threat fixes, written from the bug text and the spec:
 * <ul>
 * <li>B382 (32.9): the sand ramp still rises to the wall's top over sandstone with sand on it at the wall's foot, and
 * those blocks stay after the siege (red sand: QaThreatFixesBugTests);</li>
 * <li>B383 (32.21): at threat level 3 a raid from every lair culture's lair is at least a quarter bigger than at level 2,
 * for a new lair and for one grown to its most;</li>
 * <li>B387 (32.13): the muster's page line and chat agree in number for every guard count, singular and plural on both
 * sides, the share three in four, at most 12;</li>
 * <li>B418 (32.13): a kill by a guard enlisted on a lowered banner is no war party (5 days); a guard marching under
 * another player's raised banner is one (10 days and the cheer).</li>
 * </ul>
 */
public class QaThreatFixesGameTests implements FabricGameTest {
	private static final String HUGE = "aliveworkplace_test:huge_area";
	private static final String BIG = "aliveworkplace_test:big_area";

	// ---------------------------------------------------------------- B382: the sand ramp over a player's blocks.

	private static final ResourceLocation PILERS = ResourceLocation.fromNamespaceAndPath("aliveworkplace_test", "qa_fix_sand_pilers");
	private static final BlockPos YARD_HALL = new BlockPos(14, 2, 8);
	private static final int WEST = 5;
	private static final int EAST = 23;
	private static final int BACK = 4;
	static final int FRONT = 15;

	private record Yard(ServerLevel level, BlockPos hall, Culture culture, Villager villager) {
	}

	private static Culture pilers(GameTestHelper helper) {
		Culture culture = Culture.read(PILERS, com.google.gson.JsonParser.parseString("{\"where\": {\"min_villagers\": 100000}, \"roster\": ["
			+ "{\"entity\": \"minecraft:husk\", \"share\": 60, \"role\": \"climber\"}, {\"entity\": \"minecraft:husk\", \"share\": 40, \"role\": \"melee\"}],"
			+ " \"hours\": \"until_noon\", \"tactics\": [\"sand_ramps\"]}").getAsJsonObject());
		Map<ResourceLocation, Culture> loaded = new LinkedHashMap<>();
		Threats.all().forEach(c -> loaded.put(c.id(), c));
		Map<ResourceLocation, Culture> with = new LinkedHashMap<>(loaded);
		with.put(PILERS, culture);
		Threats.setForTest(with);
		Leftovers.after(helper, () -> Threats.setForTest(loaded));
		return culture;
	}

	/** A walled yard with a Stone Wall (a finished blueprint) in its front side and a villager inside, as the ladder tests build it. */
	private static Yard yard(GameTestHelper helper, Culture culture) {
		Leftovers.clear(helper);
		Leftovers.finished(helper);
		ServerLevel level = helper.getLevel();
		int radius = VillageHalls.RADIUS;
		long time = level.getDayTime();
		VillageHalls.RADIUS = 20;
		BlockPos hall = helper.absolutePos(YARD_HALL);
		Leftovers.after(helper, () -> {
			level.getEntitiesOfClass(Mob.class, helper.getBounds().inflate(48), m -> m.getTags().contains(VillageRaids.TAG)).forEach(Mob::discard);
			Sieges.siege(level, hall).ifPresent(s -> Sieges.lift(level, s));
			VillageRaids.forget(hall);
			ThreatData.get(level).forgetSiege(hall);
			ThreatData.get(level).forgetPast(hall);
			Sieges.forget();
			Ladders.forget();
			SandRamps.forget();
			VillageHalls.RADIUS = radius;
			level.setDayTime(time);
			new WorkplaceConfig().apply();
		});
		for (int y = 2; y <= 5; y++) {
			for (int x = WEST; x <= EAST; x++) {
				helper.setBlock(new BlockPos(x, y, BACK), Blocks.STONE_BRICKS);
				if (x < 11 || x > 17) {
					helper.setBlock(new BlockPos(x, y, FRONT), Blocks.STONE_BRICKS);
				}
			}
			for (int z = BACK; z <= FRONT; z++) {
				helper.setBlock(new BlockPos(WEST, y, z), Blocks.STONE_BRICKS);
				helper.setBlock(new BlockPos(EAST, y, z), Blocks.STONE_BRICKS);
			}
		}
		BlockPos origin = helper.absolutePos(new BlockPos(11, 2, FRONT - 2));
		level.getStructureManager().get(StarterBlueprints.STONE_WALL.id()).orElseThrow().placeInWorld(level, origin, origin,
			new StructurePlaceSettings(), RandomSource.create(1L), 2);
		BlueprintData.Placement placement = new BlueprintData.Placement(level.dimension().location(), origin, Rotation.NONE, Mirror.NONE);
		BuildSiteManager sites = BuildSiteManager.get(level);
		sites.recordFinished(StarterBlueprints.STONE_WALL.id(), placement, UUID.randomUUID());
		Leftovers.after(helper, () -> sites.forgetFinished(placement));
		for (int x = 9; x <= 11; x++) {
			for (int z = 6; z <= 8; z++) {
				for (int y = 2; y <= 4; y++) {
					helper.setBlock(new BlockPos(x, y, z), x == 10 && z == 7 && y < 4 ? Blocks.AIR : Blocks.STONE_BRICKS);
				}
			}
		}
		Villager villager = helper.spawn(EntityType.VILLAGER, new BlockPos(10, 2, 7));
		return new Yard(level, hall, culture, villager);
	}

	private static Mob climber(GameTestHelper helper, Yard yard, double x, double z) {
		ServerLevel level = yard.level();
		Culture.Member member = yard.culture().roster().stream().filter(m -> m.role() == Culture.Role.CLIMBER).findFirst().orElseThrow();
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

	private static Map<BlockPos, BlockState> blocks(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		AABB box = helper.getBounds();
		Map<BlockPos, BlockState> out = new HashMap<>();
		for (BlockPos pos : BlockPos.betweenClosed(BlockPos.containing(box.minX, box.minY, box.minZ), BlockPos.containing(box.maxX - 1, box.maxY - 1, box.maxZ - 1))) {
			out.put(pos.immutable(), level.getBlockState(pos));
		}
		return out;
	}

	private static List<String> changes(GameTestHelper helper, Map<BlockPos, BlockState> before) {
		ServerLevel level = helper.getLevel();
		List<String> out = new ArrayList<>();
		for (Map.Entry<BlockPos, BlockState> e : before.entrySet()) {
			BlockState now = level.getBlockState(e.getKey());
			if (now != e.getValue()) {
				out.add(e.getKey().toShortString() + ": " + e.getValue() + " -> " + now);
			}
		}
		return out;
	}

	/**
	 * The player's blocks ({@code mine}: relative position to block) stand at the Stone Wall's outside foot; a climbing
	 * husk comes from the front. The ramp must still reach the top (the siege says ramped), none of the player's blocks
	 * is ever recorded as the raiders' sand, and after the siege every one of them is still there and nothing else in the
	 * area changed.
	 */
	static void rampOver(GameTestHelper helper, Map<BlockPos, Block> mine) {
		Yard yard = yard(helper, pilers(helper));
		ServerLevel level = yard.level();
		Map<BlockPos, Block> placed = new HashMap<>();
		mine.forEach((rel, block) -> {
			helper.setBlock(rel, block);
			placed.put(helper.absolutePos(rel), block);
		});
		Map<BlockPos, BlockState> before = blocks(helper);
		Mob climber = climber(helper, yard, 14.5, 23.5);
		VillageRaids.track(level, yard.hall(), yard.culture(), List.of(climber));
		boolean[] ramped = {false};
		BlockPos base = helper.absolutePos(BlockPos.ZERO);
		helper.onEachTick(() -> {
			List<BlockPos> sand = SandRamps.sand(level, yard.hall());
			if (!ramped[0] && helper.getTick() > 850) {
				helper.fail("no ramp over the player's blocks in time: piled " + sand.stream().map(p -> p.subtract(base).toShortString()).toList()
					+ ", the husk at " + climber.position().subtract(base.getX(), base.getY(), base.getZ()) + (climber.isAlive() ? "" : " (dead)"));
			}
			for (BlockPos p : sand) {
				helper.assertTrue(!placed.containsKey(p), "the player's " + placed.get(p) + " at " + p.subtract(base).toShortString() + " is recorded as the raiders' sand");
			}
			if (ramped[0]) {
				return;
			}
			Optional<ThreatData.Siege> siege = Sieges.siege(level, yard.hall());
			if (siege.isPresent() && siege.get().ramped) {
				ramped[0] = true;
				climber.discard();
				VillageRaids.forget(yard.hall());
				Sieges.lift(level, siege.get());
				helper.runAfterDelay(5, () -> {
					placed.forEach((p, block) -> helper.assertTrue(level.getBlockState(p).is(block),
						"the player's " + block + " at " + p.subtract(base).toShortString() + " is now " + level.getBlockState(p)));
					helper.assertTrue(SandRamps.sand(level, yard.hall()).isEmpty(), "sand still recorded after the siege");
					helper.assertTrue(changes(helper, before).isEmpty(), "blocks changed after the siege: " + changes(helper, before));
					helper.succeed();
				});
			}
		});
	}

	/**
	 * B382 two high and not all sand: a row of sandstone at the wall's foot with the player's sand on top of it (as a desert
	 * village's own ground often is). The ramp still reaches the top, and the sandstone and the sand both stay.
	 */
	//$ gametest_ticks_batch HUGE '900' '"qaFixSandstoneFoot"'
	@GameTest(template = HUGE, timeoutTicks = 900, batch = "qaFixSandstoneFoot")
	public void qaARampRisesOverSandstoneWithSandOnItAtTheFoot(GameTestHelper helper) {
		Map<BlockPos, Block> mine = new LinkedHashMap<>();
		for (int x = 11; x <= 17; x++) {
			mine.put(new BlockPos(x, 2, FRONT + 1), Blocks.SANDSTONE);
			mine.put(new BlockPos(x, 3, FRONT + 1), Blocks.SAND);
		}
		rampOver(helper, mine);
	}

	// ---------------------------------------------------------------- B383: level 3 for every lair culture.

	private static final BlockPos HALL = new BlockPos(1, 2, 1);
	private static final BlockPos CAMP = new BlockPos(12, 1, 12);
	private static final long DAY = 24000;

	private static void at(ServerLevel level, long day, long time) {
		level.setDayTime((day - 1) * DAY + time);
	}

	private static void clearThreats(ServerLevel level, GameTestHelper helper, BlockPos hall) {
		level.getEntitiesOfClass(Mob.class, helper.getBounds().inflate(64),
			m -> m.getTags().contains(Lairs.TAG) || m.getTags().contains(BanditCamps.TAG) || m.getTags().contains(VillageRaids.TAG)).forEach(Mob::discard);
		VillageRaids.forget();
		ThreatData.get(level).end(hall);
		ThreatData.get(level).forgetPast(hall);
		Lairs.forget(level);
	}

	/**
	 * One raid from a lair of {@code id} on a village of 52 at the threat level now: the lair made fresh (or, with
	 * {@code full}, grown to its most at this level), how many really came.
	 */
	private static int raidFromLair(GameTestHelper helper, BlockPos hall, ResourceLocation id, boolean full, List<String> notes) {
		ServerLevel level = helper.getLevel();
		clearThreats(level, helper, hall);
		Culture culture = Threats.get(id).orElse(null);
		helper.assertTrue(culture != null && culture.lair().isPresent(), "no lair culture " + id);
		at(level, 64, 13000);
		Lairs.Lair lair = Lairs.found(level, hall, helper.absolutePos(CAMP), id);
		helper.assertTrue(lair != null, "no lair of " + id + " at threat level " + Threats.LEVEL);
		if (full) {
			lair = new Lairs.Lair(lair.pos(), lair.hall(), lair.captain(), lair.day(), id, Lairs.most(culture.lair().get()), lair.grown(), 0, 0, lair.name());
			Lairs.putForTest(level, lair);
		}
		at(level, 64, 15000);
		VillageRaids.Raid raid = VillageRaids.start(level, hall, 52, 0, 0.0, culture, RandomSource.create(11L));
		helper.assertTrue(raid != null, "no raid from the lair of " + id + " at threat level " + Threats.LEVEL + (full ? " (full)" : ""));
		int came = VillageRaids.raiders(level, hall).size();
		helper.assertTrue(came == raid.raiders(), id + ": the raid says " + raid.raiders() + " but " + came + " came");
		notes.add(id.getPath() + (full ? " full" : "") + " L" + Threats.LEVEL + "=" + came + " (band " + lair.strength() + ")");
		return came;
	}

	/**
	 * B383 for every lair culture (bar the pirates, whose ship QaPiratesThreatLevelGameTests covers): with threat level 3
	 * set before the lair makes camp, a raid from it on a village of 52 is at least a quarter more raiders than the same
	 * raid at level 2, for a new lair and for one grown to its most. The raiders are really there.
	 */
	//$ gametest_ticks_batch BIG '200' '"qaFixLevel3Lairs"'
	@GameTest(template = BIG, timeoutTicks = 200, batch = "qaFixLevel3Lairs")
	public void qaEveryLairCulturesRaidIsAQuarterBiggerAtThreatLevelThree(GameTestHelper helper) {
		Leftovers.clear(helper);
		ServerLevel level = helper.getLevel();
		int radius = VillageHalls.RADIUS;
		long time = level.getDayTime();
		boolean open = Expansions.openForTests;
		VillageHalls.RADIUS = 16;
		helper.setBlock(HALL, ModBlocks.VILLAGE_HALL);
		BlockPos hall = helper.absolutePos(HALL);
		Leftovers.after(helper, () -> {
			clearThreats(level, helper, hall);
			ThreatData.get(level).forgetClock(hall);
			VillageHalls.RADIUS = radius;
			Expansions.openForTests = open;
			level.setDayTime(time);
			new WorkplaceConfig().apply();
		});
		for (int i = 0; i < 4; i++) {
			helper.spawn(EntityType.VILLAGER, new BlockPos(2 + i, 2, 3));
		}
		helper.runAfterDelay(2, () -> {
			List<String> notes = new ArrayList<>();
			List<ResourceLocation> cultures = List.of(Threats.BANDITS, id("pillager_warband"), id("desert_raiders"), id("witch_coven"), id("piglin_incursion"));
			for (ResourceLocation culture : cultures) {
				for (boolean full : new boolean[] {false, true}) {
					WorkplaceConfig.parse("{\"threatLevel\": 2}").apply();
					int normal = raidFromLair(helper, hall, culture, full, notes);
					WorkplaceConfig.parse("{\"threatLevel\": 3}").apply();
					int hard = raidFromLair(helper, hall, culture, full, notes);
					helper.assertTrue(hard * 4 >= normal * 5 && hard > normal,
						culture + (full ? " (grown to its most)" : " (new)") + ": a raid of " + normal + " at level 2 is " + hard + " at level 3, not a quarter more");
				}
			}
			helper.getLevel().getServer().sendSystemMessage(net.minecraft.network.chat.Component.literal("[qaFixLevel3Lairs] " + notes));
			helper.succeed();
		});
	}

	private static ResourceLocation id(String path) {
		return io.github.jcondedata.aliveworkplace.AliveWorkplace.id(path);
	}

	// ---------------------------------------------------------------- B387: the muster's words for every count.

	private static final Pattern PAGE = Pattern.compile("(\\d+) guards? would march, (\\d+) keeps? the walls");

	/** The words the spec wants: three in four march (at most 12), each side in its own number. */
	private static String pageLine(int march, int stay) {
		return march + (march == 1 ? " guard would march, " : " guards would march, ") + stay + (stay == 1 ? " keeps the walls" : " keep the walls");
	}

	private static String musterLine(int march, int stay) {
		return march + (march == 1 ? " guard gathers" : " guards gather") + " by the hall under your Rally Banner; " + stay + (stay == 1 ? " keeps the walls" : " keep the walls");
	}

	private static void shareIsThreeInFour(GameTestHelper helper, int guards, int march, int stay) {
		helper.assertTrue(march + stay == guards, guards + " guards: " + march + " march and " + stay + " stay");
		int low = Math.min(12, guards * 3 / 4);
		int high = Math.min(12, (guards * 3 + 3) / 4);
		helper.assertTrue(march >= low && march <= high, guards + " guards: " + march + " march, not three in four (at most 12)");
	}

	/**
	 * B387 on the Defence page for 1, 2, 3, 5, 6, 7, 13 and 16 guards (the page's line before any muster): each side of
	 * the line is in its own number ("1 guard would march", "1 keeps the walls", plural otherwise), the two add up to the
	 * guards, and the marchers are three in four, at most 12 (16 guards: 12 march, 4 keep the walls).
	 */
	//$ gametest_batch BIG '"qaFixMusterWords"'
	@GameTest(template = BIG, batch = "qaFixMusterWords")
	public void qaTheMusterLineIsInTheRightNumberForEveryGuardCount(GameTestHelper helper) {
		BlockPos hall = QaWarPartyGameTests.village(helper);
		ServerLevel level = helper.getLevel();
		ServerPlayer p = QaWarPartyGameTests.client(helper).player();
		List<String> wrong = new ArrayList<>();
		List<Integer> counts = List.of(1, 2, 3, 5, 6, 7, 13, 16);
		int spawned = 0;
		for (int want : counts) {
			while (spawned < want) {
				QaWarPartyGameTests.guard(helper, new BlockPos(2 + spawned % 12, 2, 7 + spawned / 12 * 2));
				spawned++;
			}
			ChoiceMenu menu = QaWarPartyGameTests.defencePage(p, level, hall);
			List<String> lore = QaStageSixGameTests.lore(menu.icon(DefencePage.MUSTER));
			String line = lore.stream().filter(l -> l.contains("would march")).findFirst().orElse(null);
			if (line == null) {
				wrong.add(want + " guards: no march line in " + lore);
				continue;
			}
			Matcher m = PAGE.matcher(line);
			if (!m.matches()) {
				wrong.add(want + " guards: '" + line + "'");
				continue;
			}
			int march = Integer.parseInt(m.group(1));
			int stay = Integer.parseInt(m.group(2));
			shareIsThreeInFour(helper, want, march, stay);
			if (!line.equals(pageLine(march, stay))) {
				wrong.add(want + " guards: '" + line + "', not '" + pageLine(march, stay) + "'");
			}
			if (want == 16 && (march != 12 || stay != 4)) {
				wrong.add("16 guards: " + march + " march, " + stay + " stay (at most 12 march)");
			}
		}
		helper.assertTrue(wrong.isEmpty(), "the page's muster line: " + wrong);
		helper.succeed();
	}

	/**
	 * B387 through the muster itself: 5 guards, a Rally Banner, a click on "Muster a war party". The chat names exactly
	 * as many as the banner enlisted, and those left, each in its own number, and the page said the same numbers before.
	 */
	//$ gametest_batch BIG '"qaFixMusterFive"'
	@GameTest(template = BIG, batch = "qaFixMusterFive")
	public void qaMusteringFiveGuardsSaysWhatTheBannerEnlisted(GameTestHelper helper) {
		BlockPos hall = QaWarPartyGameTests.village(helper);
		ServerLevel level = helper.getLevel();
		QaStageFiveGameTests.Client c = QaWarPartyGameTests.client(helper);
		ServerPlayer p = c.player();
		for (int i = 0; i < 5; i++) {
			QaWarPartyGameTests.guard(helper, new BlockPos(3 + i, 2, 7));
		}
		ChoiceMenu menu = QaWarPartyGameTests.defencePage(p, level, hall);
		String line = QaStageSixGameTests.lore(menu.icon(DefencePage.MUSTER)).stream().filter(l -> l.contains("would march")).findFirst().orElse("");
		p.getInventory().add(new ItemStack(ModItems.RALLY_BANNER));
		QaWarPartyGameTests.click(menu, DefencePage.MUSTER, p);
		RallyBannerItem.Rally rally = RallyBannerItem.rally(WarParties.banner(p));
		int march = rally.guards().size();
		int stay = 5 - march;
		shareIsThreeInFour(helper, 5, march, stay);
		helper.assertTrue(rally.raised(), "the banner isn't raised");
		helper.assertTrue(line.equals(pageLine(march, stay)), "the page said '" + line + "' before a muster of " + march);
		String said = QaWarPartyGameTests.last(c.seen());
		helper.assertTrue(said.equals(musterLine(march, stay)), "said '" + said + "', not '" + musterLine(march, stay) + "'");
		helper.succeed();
	}

	// ---------------------------------------------------------------- B418: whose kill makes a war party.

	/**
	 * B418's boundary: a guard IS on the player's Rally Banner, but the banner is lowered (not marching). He kills one of
	 * the band at the lair and the player breaks it alone: no war party was there, so the usual 5 days and no cheer.
	 */
	//$ gametest_batch BIG '"qaFixLoweredBanner"'
	@GameTest(template = BIG, batch = "qaFixLoweredBanner")
	public void qaAKillByAGuardOnALoweredBannerIsNotAWarParty(GameTestHelper helper) {
		BlockPos hall = QaWarPartyGameTests.village(helper);
		ServerLevel level = helper.getLevel();
		ServerPlayer player = QaWarPartyGameTests.client(helper).player();
		player.moveTo(helper.absolutePos(new BlockPos(11, 2, 11)).getCenter());
		Villager guard = QaWarPartyGameTests.guard(helper, new BlockPos(12, 2, 12));
		Villager baker = helper.spawn(EntityType.VILLAGER, new BlockPos(3, 2, 3));
		Lairs.Lair lair = QaWarPartyGameTests.lair(helper, hall, QaWarPartyGameTests.LAIR);
		ItemStack banner = new ItemStack(ModItems.RALLY_BANNER);
		banner.set(ModComponents.RALLY, new RallyBannerItem.Rally(List.of(guard.getUUID()), false));
		player.getInventory().add(banner);
		Escorts.scan(level.getServer());
		helper.assertFalse(WarParties.marching(guard), "setup: a guard on a lowered banner is marching");
		QaWarPartyGameTests.bandit(helper, new BlockPos(14, 2, 12)).hurt(level.damageSources().mobAttack(guard), 1000f);
		Mob captain = (Mob) level.getEntity(lair.captain());
		captain.hurt(level.damageSources().playerAttack(player), 1000f);
		helper.assertTrue(Lairs.near(level, hall).isEmpty(), "the lair stands");
		helper.assertTrue(Lairs.resting(level, hall) == 5, "rest " + Lairs.resting(level, hall) + " days after a lowered banner's guard's kill, not 5");
		helper.assertTrue(WarParties.mood(level, baker) == null, "cheered with no war party: " + WarParties.mood(level, baker));
		helper.succeed();
	}

	/**
	 * B418's other boundary: the guard marches under ANOTHER player's raised Rally Banner, kills one of the band at the
	 * lair, and the first player (with no banner) breaks it. A war party was there, whoever leads it: 10 days and the
	 * cheer.
	 */
	//$ gametest_batch BIG '"qaFixOtherBanner"'
	@GameTest(template = BIG, batch = "qaFixOtherBanner")
	public void qaAGuardUnderAnotherPlayersBannerMakesItAWarParty(GameTestHelper helper) {
		BlockPos hall = QaWarPartyGameTests.village(helper);
		ServerLevel level = helper.getLevel();
		ServerPlayer breaker = QaWarPartyGameTests.client(helper).player();
		breaker.moveTo(helper.absolutePos(new BlockPos(11, 2, 11)).getCenter());
		ServerPlayer leader = QaWarPartyGameTests.client(helper).player();
		leader.moveTo(helper.absolutePos(new BlockPos(12, 2, 10)).getCenter());
		helper.assertTrue(!leader.getUUID().equals(breaker.getUUID()), "setup: one player");
		Villager guard = QaWarPartyGameTests.guard(helper, new BlockPos(12, 2, 12));
		Villager baker = helper.spawn(EntityType.VILLAGER, new BlockPos(3, 2, 3));
		Lairs.Lair lair = QaWarPartyGameTests.lair(helper, hall, QaWarPartyGameTests.LAIR);
		ItemStack banner = new ItemStack(ModItems.RALLY_BANNER);
		banner.set(ModComponents.RALLY, new RallyBannerItem.Rally(List.of(guard.getUUID()), true));
		leader.getInventory().add(banner);
		Escorts.scan(level.getServer());
		helper.assertTrue(WarParties.marching(guard), "setup: the guard isn't marching under the other player's banner");
		QaWarPartyGameTests.bandit(helper, new BlockPos(14, 2, 12)).hurt(level.damageSources().mobAttack(guard), 1000f);
		Mob captain = (Mob) level.getEntity(lair.captain());
		captain.hurt(level.damageSources().playerAttack(breaker), 1000f);
		helper.assertTrue(Lairs.near(level, hall).isEmpty(), "the lair stands");
		helper.assertTrue(Lairs.resting(level, hall) == WarParties.REST_DAYS, "rest " + Lairs.resting(level, hall) + " days with another player's war party there, not " + WarParties.REST_DAYS);
		helper.assertTrue(WarParties.mood(level, baker) != null, "no cheer with another player's war party there");
		helper.succeed();
	}
}
