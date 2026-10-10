package io.github.jcondedata.aliveworkplace.test;

import io.github.jcondedata.aliveworkplace.WorkplaceConfig;
import io.github.jcondedata.aliveworkplace.blueprint.BlueprintData;
import io.github.jcondedata.aliveworkplace.blueprint.StarterBlueprints;
import io.github.jcondedata.aliveworkplace.build.BuildSiteManager;
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
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.locale.Language;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.ItemLore;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.FenceGateBlock;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings;

/**
 * Sieges I: rams and gates (ROADMAP 32.4). Each test walls a yard in the middle of the area, with a finished Palisade
 * Gate or Gatehouse in its front wall (the only way in), a villager shut in a stone cell inside, and the raiders of a
 * culture that rams gates outside. The tests ask what the item promises: a ravager ram breaks a Palisade Gate's block
 * within 400 ticks and a pillager is through within 600; a Gatehouse with its portcullis down takes at least three
 * times as long; the portcullis is down while the siege lasts and up after it; a player's own fence gate beside the
 * build is never hit; and the ways it could break: {@code mobGriefing} off, the two config switches off, a save and
 * load half-way through a gate, a player breaking the gate first, and the director's budget of path requests.
 */
public class SiegeGameTests implements FabricGameTest {
	private static final String AREA = "aliveworkplace_test:huge_area";
	private static final ResourceLocation BESIEGERS = ResourceLocation.fromNamespaceAndPath("aliveworkplace_test", "test_besiegers");
	/** The hall's place, inside the yard (most tests need only the position). */
	private static final BlockPos HALL = new BlockPos(14, 2, 8);
	/** The yard's walls: x 5 and 23, z 4 (the back) and 15 (the front, with the gate build in it). */
	private static final int WEST = 5;
	private static final int EAST = 23;
	private static final int BACK = 4;
	private static final int FRONT = 15;
	/** The fence gate "a player" put in the front wall, right beside the Palisade Gate. */
	private static final BlockPos PLAYERS_GATE = new BlockPos(18, 2, FRONT);

	/** A yard and what stands in it. */
	private record Yard(ServerLevel level, BlockPos hall, BlockPos origin, StarterBlueprints.Entry build, Culture culture, Villager villager) {
		/** The world position of the build's blueprint position (x, y, z). */
		BlockPos at(int x, int y, int z) {
			return origin.offset(x, y, z);
		}

		ThreatData.Siege siege(GameTestHelper helper) {
			Optional<ThreatData.Siege> siege = Sieges.siege(level, hall);
			helper.assertTrue(siege.isPresent(), "no siege on the village");
			return siege.get();
		}
	}

	/**
	 * Builds the yard with {@code build} (the Palisade Gate or the Gatehouse) finished in its front wall, its outside to
	 * the south, and a villager in a closed cell inside. What the test changes goes when it ends.
	 */
	private static Yard yard(GameTestHelper helper, StarterBlueprints.Entry build) {
		Leftovers.clear(helper);
		ServerLevel level = helper.getLevel();
		int radius = VillageHalls.RADIUS;
		long time = level.getDayTime();
		boolean griefing = level.getGameRules().getBoolean(GameRules.RULE_MOBGRIEFING);
		VillageHalls.RADIUS = 20;
		BlockPos hall = helper.absolutePos(HALL);
		// A culture that rams gates (none we ship does before the warband, 32.7): a ravager ram and pillagers. It is loaded
		// for the test only, where: nowhere, so no other raid picks it.
		Culture culture = Culture.read(BESIEGERS, com.google.gson.JsonParser.parseString("{\"where\": {\"min_villagers\": 100000}, \"roster\": ["
			+ "{\"entity\": \"minecraft:ravager\", \"share\": 34, \"role\": \"ram\"}, {\"entity\": \"minecraft:pillager\", \"share\": 66, \"role\": \"ranged\"}],"
			+ " \"tactics\": [\"ram_gates\"]}").getAsJsonObject());
		java.util.Map<ResourceLocation, Culture> loaded = new java.util.LinkedHashMap<>();
		Threats.all().forEach(c -> loaded.put(c.id(), c));
		java.util.Map<ResourceLocation, Culture> with = new java.util.LinkedHashMap<>(loaded);
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
		return rebuild(helper, build, culture);
	}

	/** The yard built (again) with {@code build} in its front wall; {@link #yard} has set the village up. */
	private static Yard rebuild(GameTestHelper helper, StarterBlueprints.Entry build, Culture culture) {
		ServerLevel level = helper.getLevel();
		BlockPos hall = helper.absolutePos(HALL);
		BlockPos origin = walls(helper, build);
		BlueprintData.Placement placement = new BlueprintData.Placement(level.dimension().location(), origin, Rotation.NONE, Mirror.NONE);
		BuildSiteManager sites = BuildSiteManager.get(level);
		sites.recordFinished(build.id(), placement, UUID.randomUUID());
		// The villager's cell: stone all round and over it, so no raider sees or reaches it.
		for (int x = 9; x <= 11; x++) {
			for (int z = 6; z <= 8; z++) {
				for (int y = 2; y <= 4; y++) {
					helper.setBlock(new BlockPos(x, y, z), x == 10 && z == 7 && y < 4 ? Blocks.AIR : Blocks.STONE_BRICKS);
				}
			}
		}
		Villager villager = helper.spawn(EntityType.VILLAGER, new BlockPos(10, 2, 7));
		Leftovers.after(helper, () -> sites.forgetFinished(placement));
		return new Yard(level, hall, origin, build, culture, villager);
	}

	/** The yard's walls (four high) and the build in the front one; returns the build's origin. */
	private static BlockPos walls(GameTestHelper helper, StarterBlueprints.Entry build) {
		ServerLevel level = helper.getLevel();
		int width = build.size().getX();
		int depth = build.size().getZ();
		// Its back row (the blueprint's last z but one for the Gatehouse, whose last row is the doorstep) stands in the front wall.
		int x0 = 14 - width / 2;
		int z0 = build == StarterBlueprints.GATEHOUSE ? FRONT - 3 : FRONT - (depth - 1);
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
		return origin;
	}

	/** One of the culture's raiders with {@code role}, as a raid spawns it, at the helper position (x, 2, z), after the villager. */
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

	/** Whether {@code mob} is through the gate: inside the yard's front wall. */
	private static boolean through(GameTestHelper helper, Yard yard, Mob mob) {
		BlockPos base = helper.absolutePos(BlockPos.ZERO);
		double x = mob.getX() - base.getX();
		double z = mob.getZ() - base.getZ();
		int inside = yard.build() == StarterBlueprints.GATEHOUSE ? FRONT - 3 : FRONT - 1;
		return mob.isAlive() && z < inside && z > BACK && x > WEST && x < EAST + 1;
	}

	private static boolean isFenceGate(ServerLevel level, BlockPos pos) {
		return level.getBlockState(pos).getBlock() instanceof FenceGateBlock;
	}

	/** Every line of an icon: its name, then its tooltip. */
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
	 * The item's first promise. A ravager ram outside a Palisade Gate with a villager inside: the gates' hit points go
	 * down blow by blow, a gate block is gone within 400 ticks (nothing dropped, and it's on the list for a builder),
	 * and a pillager behind the ram is through within 600. The fence gate a player put beside the build is never hit.
	 */
	//$ gametest_ticks_batch AREA '700' '"siegePalisade"'
	@GameTest(template = AREA, timeoutTicks = 700, batch = "siegePalisade")
	public void aRamBreaksThePalisadeGate(GameTestHelper helper) {
		Yard yard = yard(helper, StarterBlueprints.PALISADE_GATE);
		ServerLevel level = yard.level();
		helper.setBlock(PLAYERS_GATE, Blocks.SPRUCE_FENCE_GATE);
		BlockPos players = helper.absolutePos(PLAYERS_GATE);
		List<BlockPos> gates = List.of(yard.at(2, 0, 1), yard.at(3, 0, 1), yard.at(4, 0, 1));
		gates.forEach(g -> helper.assertTrue(isFenceGate(level, g), "no gate at " + g));
		Mob ram = raider(helper, yard, Culture.Role.RAM, 14.5, 21.5);
		Mob pillager = raider(helper, yard, Culture.Role.RANGED, 14.5, 25.5);
		VillageRaids.track(level, yard.hall(), yard.culture(), List.of(ram, pillager));
		ThreatData.Siege siege = yard.siege(helper);
		helper.assertTrue(siege.breach != null && siege.breach.equals(gates.get(1)), "the breach is the middle gate: " + siege.breach);
		helper.assertTrue(siege.gates.size() == 3 && siege.gates.stream().allMatch(g -> g.hp == Sieges.FENCE_GATE_HP), "three fence gates of 60: " + siege.gates.size());
		helper.assertTrue(Sieges.hitPointsLeft(level, yard.hall(), players) == -1, "the player's gate is in the siege's list");
		long[] seen = {-1, -1, -1}; // first damage, first block gone, the pillager through
		helper.onEachTick(() -> {
			long tick = helper.getTick();
			if (seen[0] < 0 && Sieges.hitPointsLeft(level, yard.hall(), gates.get(1)) < Sieges.FENCE_GATE_HP) {
				seen[0] = tick;
				helper.assertTrue(isFenceGate(level, gates.get(1)), "damaged, the gate still stands");
			}
			if (seen[1] < 0 && gates.stream().anyMatch(g -> !isFenceGate(level, g))) {
				seen[1] = tick;
				helper.assertTrue(seen[0] >= 0 && seen[0] < tick, "the gate went without being damaged first");
				helper.assertTrue(level.getBlockState(gates.get(1)).isAir(), "the middle gate goes first");
				helper.assertTrue(ThreatData.get(level).brokenGates(yard.hall()).equals(List.of(gates.get(1))), "it waits for a builder: "
					+ ThreatData.get(level).brokenGates(yard.hall()));
				helper.assertTrue(level.getEntitiesOfClass(ItemEntity.class, helper.getBounds()).isEmpty(), "the broken gate dropped something");
			}
			if (seen[1] < 0 && tick > 400) {
				helper.fail("no gate block broken within 400 ticks; the middle gate has " + Sieges.hitPointsLeft(level, yard.hall(), gates.get(1))
					+ " hit points, the ram is at " + ram.position());
			}
			if (seen[2] < 0 && through(helper, yard, pillager)) {
				seen[2] = tick;
			}
			if (seen[2] < 0 && tick > 600) {
				helper.fail("the pillager isn't through within 600 ticks: it is at " + pillager.position() + ", the first gate went at " + seen[1]);
			}
			if (seen[1] >= 0 && seen[2] >= 0) {
				helper.assertTrue(isFenceGate(level, players) && Sieges.hitPointsLeft(level, yard.hall(), players) == -1, "the player's gate was hit");
				helper.assertTrue(Sieges.mostPaths(level, yard.hall()) <= Sieges.MAX_PATHS, "paths in a tick: " + Sieges.mostPaths(level, yard.hall()));
				helper.succeed();
			}
		});
	}

	/**
	 * The Gatehouse. Its portcullis drops when the siege begins (iron bars under the drawn-up ones, across the gateway)
	 * and the pillager behind the ram needs at least three times as long to get through as through the Palisade Gate
	 * measured first in the same yard: the bars have 150 hit points each, and the fence gates wait behind them.
	 */
	//$ gametest_ticks_batch AREA '3600' '"siegeGatehouse"'
	@GameTest(template = AREA, timeoutTicks = 3600, batch = "siegeGatehouse")
	public void aGatehouseTakesThreeTimesAsLong(GameTestHelper helper) {
		Yard palisade = yard(helper, StarterBlueprints.PALISADE_GATE);
		ServerLevel level = palisade.level();
		Mob ram = raider(helper, palisade, Culture.Role.RAM, 14.5, 21.5);
		Mob pillager = raider(helper, palisade, Culture.Role.RANGED, 14.5, 25.5);
		VillageRaids.track(level, palisade.hall(), palisade.culture(), List.of(ram, pillager));
		long[] took = {-1, -1}; // through the Palisade Gate; when the Gatehouse's siege began
		Yard[] gatehouse = new Yard[1];
		Mob[] second = new Mob[2];
		helper.onEachTick(() -> {
			long tick = helper.getTick();
			if (took[0] < 0) {
				if (through(helper, palisade, pillager)) {
					took[0] = tick;
					// The same yard again, with the Gatehouse in its wall.
					ram.discard();
					pillager.discard();
					palisade.villager().discard();
					Sieges.lift(level, palisade.siege(helper));
					VillageRaids.forget(palisade.hall());
					BuildSiteManager.get(level).forgetFinished(new BlueprintData.Placement(level.dimension().location(), palisade.origin(), Rotation.NONE, Mirror.NONE));
					for (int x = WEST; x <= EAST; x++) {
						for (int y = 2; y <= 8; y++) {
							for (int z = FRONT - 3; z <= FRONT + 1; z++) {
								helper.setBlock(new BlockPos(x, y, z), Blocks.AIR);
							}
						}
					}
					gatehouse[0] = rebuild(helper, StarterBlueprints.GATEHOUSE, palisade.culture());
					Yard yard = gatehouse[0];
					second[0] = raider(helper, yard, Culture.Role.RAM, 14.5, 21.5);
					second[1] = raider(helper, yard, Culture.Role.RANGED, 14.5, 25.5);
					VillageRaids.track(level, yard.hall(), yard.culture(), List.of(second[0], second[1]));
					took[1] = tick;
					ThreatData.Siege siege = yard.siege(helper);
					for (int x = 4; x <= 6; x++) {
						helper.assertTrue(level.getBlockState(yard.at(x, 3, 3)).is(Blocks.IRON_BARS), "the drawn-up bars are gone at x " + x);
						helper.assertTrue(level.getBlockState(yard.at(x, 2, 3)).is(Blocks.IRON_BARS) && level.getBlockState(yard.at(x, 1, 3)).is(Blocks.IRON_BARS),
							"the portcullis didn't drop at x " + x);
						helper.assertTrue(!level.getBlockState(yard.at(x, 1, 2)).getValue(FenceGateBlock.OPEN), "a gate is open at x " + x);
					}
					helper.assertTrue(siege.portcullis.size() == 6 && Sieges.portcullisDown(level, yard.hall()), "six bars dropped: " + siege.portcullis.size());
					helper.assertTrue(Sieges.hitPointsLeft(level, yard.hall(), yard.at(5, 1, 3)) == Sieges.IRON_BARS_HP, "a bar has 150 hit points");
					helper.assertTrue(siege.breach != null && siege.breach.equals(yard.at(5, 1, 3)), "the breach is the middle of the portcullis: " + siege.breach);
				} else if (tick > 600) {
					helper.fail("the pillager isn't through the Palisade Gate within 600 ticks: it is at " + pillager.position());
				}
				return;
			}
			Yard yard = gatehouse[0];
			long since = tick - took[1];
			if (through(helper, yard, second[1])) {
				helper.assertTrue(since >= 3 * took[0], "through the Gatehouse in " + since + " ticks, through the Palisade Gate in " + took[0]);
				helper.assertTrue(level.getBlockState(yard.at(5, 1, 3)).isAir() && level.getBlockState(yard.at(5, 2, 3)).isAir()
					&& level.getBlockState(yard.at(5, 1, 2)).isAir(), "the pillager is through, but the middle way isn't broken");
				// The dropped bars aren't the blueprint's: only the fence gate waits for a builder.
				helper.assertTrue(ThreatData.get(level).brokenGates(yard.hall()).contains(yard.at(5, 1, 2))
					&& !ThreatData.get(level).brokenGates(yard.hall()).contains(yard.at(5, 1, 3)), "for the builder: " + ThreatData.get(level).brokenGates(yard.hall()));
				helper.succeed();
			} else if (since > 2800) {
				helper.fail("the pillager isn't through the Gatehouse after 2800 ticks: it is at " + second[1].position() + ", the ram at " + second[0].position()
					+ ", bars " + Sieges.hitPointsLeft(level, yard.hall(), yard.at(5, 1, 3)) + "/" + Sieges.hitPointsLeft(level, yard.hall(), yard.at(5, 2, 3))
					+ ", gate " + Sieges.hitPointsLeft(level, yard.hall(), yard.at(5, 1, 2)));
			}
		});
	}

	/**
	 * A siege through the raid's own entry point, on a village with a hall. {@code VillageRaids.start} by a culture with
	 * {@code ram_gates} on a village with a finished Gatehouse: the gates shut in broad daylight and without a guard, the
	 * portcullis drops, and the Defence page says so. When the raid is over the gates stay shut and the portcullis down
	 * (the hall's round doesn't open them, guards or none) until the first dawn after; then the bars are drawn up, the
	 * gates open and the siege is forgotten.
	 */
	//$ gametest_ticks_batch AREA '300' '"siegePortcullis"'
	@GameTest(template = AREA, timeoutTicks = 300, batch = "siegePortcullis")
	public void thePortcullisIsDownWhileTheSiegeLasts(GameTestHelper helper) {
		Yard yard = yard(helper, StarterBlueprints.GATEHOUSE);
		ServerLevel level = yard.level();
		helper.setBlock(HALL, ModBlocks.VILLAGE_HALL);
		helper.setDayTime(6000);
		ServerPlayer player = helper.makeMockServerPlayerInLevel();
		Leftovers.after(helper, () -> level.getServer().getPlayerList().remove(player));
		List<BlockPos> gates = List.of(yard.at(4, 1, 2), yard.at(5, 1, 2), yard.at(6, 1, 2));
		List<BlockPos> dropped = List.of(yard.at(4, 1, 3), yard.at(5, 1, 3), yard.at(6, 1, 3), yard.at(4, 2, 3), yard.at(5, 2, 3), yard.at(6, 2, 3));
		for (BlockPos gate : gates) {
			level.setBlock(gate, level.getBlockState(gate).setValue(FenceGateBlock.OPEN, true), 3);
		}
		List<String> before = lines(DefencePage.gates(level, yard.hall()));
		helper.assertTrue(before.equals(List.of("Gates: open", "3 gates standing", "Portcullis: drawn up",
			"Guards shut the gates at night; a siege shuts them at once and drops the portcullis")), "the page before: " + before);
		helper.assertTrue(Sieges.walled(level, yard.hall()), "a village with a Gatehouse isn't walled");
		// From the south (the +z side), outside the gate.
		VillageRaids.Raid raid = VillageRaids.start(level, yard.hall(), 0, 0, Math.PI / 2, yard.culture(), RandomSource.create(7L));
		helper.assertTrue(raid != null && raid.raiders() == 3, "the raid: " + raid);
		ThreatData.Siege siege = yard.siege(helper);
		helper.assertTrue(!siege.over && siege.dawn == Sieges.nextDawn(level.getDayTime()), "the siege: over " + siege.over + ", dawn " + siege.dawn);
		gates.forEach(g -> helper.assertTrue(!level.getBlockState(g).getValue(FenceGateBlock.OPEN), "the gate at " + g + " is still open"));
		dropped.forEach(b -> helper.assertTrue(level.getBlockState(b).is(Blocks.IRON_BARS), "no bar at " + b));
		helper.assertTrue(level.getBlockState(yard.at(4, 1, 3)).getValue(net.minecraft.world.level.block.IronBarsBlock.EAST), "the bars don't join up");
		List<String> during = lines(DefencePage.gates(level, yard.hall()));
		helper.assertTrue(during.equals(List.of("Gates: shut", "3 gates standing", "Portcullis: down", "Under siege: shut until the first dawn after",
			"Guards shut the gates at night; a siege shuts them at once and drops the portcullis")), "the page in the siege: " + during);
		for (String key : List.of("message.aliveworkplace.siege.begins", "message.aliveworkplace.siege.begins_portcullis", "message.aliveworkplace.siege.breached",
			"screen.aliveworkplace.defence.no_gates", "screen.aliveworkplace.defence.no_gates_hint", "screen.aliveworkplace.defence.gates_broken",
			"screen.aliveworkplace.defence.gates_broken.one", "screen.aliveworkplace.defence.gate_blocks.one", "aliveworkplace.config.sieges",
			"aliveworkplace.config.sieges.tooltip", "aliveworkplace.config.siegeDamage", "aliveworkplace.config.siegeDamage.tooltip")) {
			helper.assertTrue(Language.getInstance().has(key), "no text for " + key);
		}
		helper.runAtTickTime(40, () -> {
			// The raid ends as raids do: its raiders are gone, the hall's round sees it.
			VillageRaids.raiders(level, yard.hall()).forEach(Mob::discard);
			VillageRaids.tick(level, yard.hall(), 0, 0, -100, day -> { });
			helper.assertTrue(VillageRaids.active(yard.hall()).isEmpty(), "the raid isn't over");
			Optional<ThreatData.Siege> after = Sieges.siege(level, yard.hall());
			helper.assertTrue(after.isPresent() && after.get().over, "the siege should last until dawn");
			helper.assertTrue(Gates.round(level, yard.hall(), 1) == 0 && Gates.round(level, yard.hall(), 0) == 0, "the hall's round moved the gates at noon");
			gates.forEach(g -> helper.assertTrue(!level.getBlockState(g).getValue(FenceGateBlock.OPEN), "the gate at " + g + " opened before dawn"));
			helper.assertTrue(Sieges.portcullisDown(level, yard.hall()), "the portcullis went up before dawn");
		});
		helper.runAtTickTime(100, () -> {
			helper.assertTrue(Sieges.portcullisDown(level, yard.hall()), "the portcullis went up before dawn");
			level.setDayTime(siege.dawn + 20); // the first dawn after
		});
		helper.runAtTickTime(150, () -> {
			helper.assertTrue(Sieges.siege(level, yard.hall()).isEmpty(), "the siege wasn't lifted at dawn");
			dropped.forEach(b -> helper.assertTrue(level.getBlockState(b).isAir(), "a bar is still down at " + b));
			for (int x = 4; x <= 6; x++) {
				helper.assertTrue(level.getBlockState(yard.at(x, 3, 3)).is(Blocks.IRON_BARS), "the drawn-up bars went too at x " + x);
			}
			gates.forEach(g -> helper.assertTrue(level.getBlockState(g).getValue(FenceGateBlock.OPEN), "the gate at " + g + " didn't open at dawn"));
			List<String> after = lines(DefencePage.gates(level, yard.hall()));
			helper.assertTrue(after.equals(before), "the page after: " + after);
			helper.succeed();
		});
	}

	/** With {@code mobGriefing} off the ram pounds the Palisade Gate for 2400 ticks and every gate block still stands, whole. */
	//$ gametest_ticks_batch AREA '2500' '"siegeNoGriefing"'
	@GameTest(template = AREA, timeoutTicks = 2500, batch = "siegeNoGriefing")
	public void gatesHoldWithMobGriefingOff(GameTestHelper helper) {
		Yard yard = yard(helper, StarterBlueprints.PALISADE_GATE);
		ServerLevel level = yard.level();
		level.getGameRules().getRule(GameRules.RULE_MOBGRIEFING).set(false, level.getServer());
		List<BlockPos> gates = List.of(yard.at(2, 0, 1), yard.at(3, 0, 1), yard.at(4, 0, 1));
		Mob ram = raider(helper, yard, Culture.Role.RAM, 14.5, 21.5);
		Mob pillager = raider(helper, yard, Culture.Role.RANGED, 14.5, 25.5);
		VillageRaids.track(level, yard.hall(), yard.culture(), List.of(ram, pillager));
		helper.assertTrue(!Sieges.damaging(level), "blows count with mobGriefing off");
		helper.runAtTickTime(300, () -> helper.assertTrue(ram.position().distanceTo(gates.get(1).getCenter()) < 3.5,
			"the ram isn't at the gate: " + ram.position() + ", gate " + gates.get(1)));
		helper.runAtTickTime(2400, () -> {
			for (BlockPos gate : gates) {
				helper.assertTrue(isFenceGate(level, gate), "the gate at " + gate + " is gone");
				helper.assertTrue(Sieges.hitPointsLeft(level, yard.hall(), gate) == Sieges.FENCE_GATE_HP, "the gate at " + gate + " lost hit points");
			}
			helper.assertTrue(ThreatData.get(level).brokenGates(yard.hall()).isEmpty(), "a gate is listed as broken");
			helper.assertTrue(!through(helper, yard, pillager) && !through(helper, yard, ram), "a raider got in");
			helper.assertTrue(yard.villager().isAlive(), "the villager behind the gate died");
			helper.succeed();
		});
	}

	/**
	 * The two switches. {@code siegeDamage} false: it is a siege (the gates shut) but the ram's blows cost the gate
	 * nothing. {@code sieges} false: the same raid is a plain one, no siege, the gates as they were.
	 */
	//$ gametest_ticks_batch AREA '500' '"siegeSwitches"'
	@GameTest(template = AREA, timeoutTicks = 500, batch = "siegeSwitches")
	public void theSwitchesTurnSiegesAndTheirDamageOff(GameTestHelper helper) {
		Yard yard = yard(helper, StarterBlueprints.PALISADE_GATE);
		ServerLevel level = yard.level();
		List<BlockPos> gates = List.of(yard.at(2, 0, 1), yard.at(3, 0, 1), yard.at(4, 0, 1));
		WorkplaceConfig defaults = new WorkplaceConfig();
		helper.assertTrue(defaults.sieges && defaults.siegeDamage && Sieges.ENABLED && Sieges.DAMAGE, "both are on unless the file says otherwise");
		WorkplaceConfig.parse("{\"siegeDamage\": false}").apply();
		helper.assertTrue(Sieges.ENABLED && !Sieges.DAMAGE && !Sieges.damaging(level), "siegeDamage false");
		for (BlockPos gate : gates) {
			level.setBlock(gate, level.getBlockState(gate).setValue(FenceGateBlock.OPEN, true), 3);
		}
		Mob ram = raider(helper, yard, Culture.Role.RAM, 14.5, 21.5);
		VillageRaids.track(level, yard.hall(), yard.culture(), List.of(ram));
		yard.siege(helper);
		gates.forEach(g -> helper.assertTrue(!level.getBlockState(g).getValue(FenceGateBlock.OPEN), "the siege didn't shut the gate at " + g));
		helper.runAtTickTime(400, () -> {
			helper.assertTrue(ram.position().distanceTo(gates.get(1).getCenter()) < 3.5, "the ram isn't at the gate: " + ram.position());
			for (BlockPos gate : gates) {
				helper.assertTrue(isFenceGate(level, gate) && Sieges.hitPointsLeft(level, yard.hall(), gate) == Sieges.FENCE_GATE_HP,
					"with siegeDamage off the gate at " + gate + " has " + Sieges.hitPointsLeft(level, yard.hall(), gate));
			}
			// Now without sieges at all.
			ram.discard();
			Sieges.lift(level, yard.siege(helper));
			VillageRaids.forget(yard.hall());
			WorkplaceConfig.parse("{\"sieges\": false}").apply();
			helper.assertTrue(!Sieges.ENABLED, "sieges false");
			for (BlockPos gate : gates) {
				level.setBlock(gate, level.getBlockState(gate).setValue(FenceGateBlock.OPEN, true), 3);
			}
			Mob other = raider(helper, yard, Culture.Role.RAM, 14.5, 21.5);
			VillageRaids.track(level, yard.hall(), yard.culture(), List.of(other));
			helper.assertTrue(VillageRaids.active(yard.hall()).isPresent(), "the raid itself should go on");
			helper.assertTrue(Sieges.siege(level, yard.hall()).isEmpty(), "a siege with sieges off");
			gates.forEach(g -> helper.assertTrue(level.getBlockState(g).getValue(FenceGateBlock.OPEN), "a plain raid shut the gate at " + g));
			helper.succeed();
		});
	}

	/**
	 * A save and load half-way through a gate: the siege comes back with its breach, its side and the gate's hit points
	 * as they were, the director picks it up again from nothing, and the gate goes after only the blows it still needed.
	 */
	//$ gametest_ticks_batch AREA '700' '"siegeRestart"'
	@GameTest(template = AREA, timeoutTicks = 700, batch = "siegeRestart")
	public void aSiegeCarriesOnAfterARestart(GameTestHelper helper) {
		Yard yard = yard(helper, StarterBlueprints.PALISADE_GATE);
		ServerLevel level = yard.level();
		BlockPos gate = yard.at(3, 0, 1);
		Mob ram = raider(helper, yard, Culture.Role.RAM, 14.5, 21.5);
		VillageRaids.track(level, yard.hall(), yard.culture(), List.of(ram));
		long[] restarted = {-1, 0, 0, 0}; // when, the hit points then, the hit points last seen, blows since
		helper.onEachTick(() -> {
			long tick = helper.getTick();
			int hp = Sieges.hitPointsLeft(level, yard.hall(), gate);
			if (restarted[0] < 0) {
				if (hp <= Sieges.FENCE_GATE_HP - 2 * Sieges.RAM_BLOW) {
					ThreatData.Siege before = yard.siege(helper);
					ThreatData.get(level).roundTrip(level.registryAccess());
					Sieges.forget();
					ThreatData.Siege after = yard.siege(helper);
					helper.assertTrue(after != before, "the save and load gave the same object back");
					helper.assertTrue(after.breach != null && after.breach.equals(before.breach) && after.out == before.out && after.began == before.began
						&& after.dawn == before.dawn && !after.over && after.gates.size() == 3, "the siege after the load: breach " + after.breach + ", out " + after.out);
					helper.assertTrue(Sieges.hitPointsLeft(level, yard.hall(), gate) == hp, "the gate's hit points after the load: "
						+ Sieges.hitPointsLeft(level, yard.hall(), gate) + ", before " + hp);
					helper.assertTrue(after.gates.get(0).pos.equals(gate) && after.gates.get(0).lane == 0 && after.gates.get(1).lane == 1 && after.gates.get(2).lane == 2,
						"the order after the load");
					helper.assertTrue(VillageRaids.active(yard.hall()).isPresent(), "the raid was lost in the load");
					restarted[0] = tick;
					restarted[1] = hp;
					restarted[2] = hp;
				} else if (tick > 400) {
					helper.fail("the ram hasn't struck twice in 400 ticks: the gate has " + hp);
				}
				return;
			}
			if (hp != restarted[2]) {
				helper.assertTrue(hp < restarted[2], "the gate's hit points went up after the load: " + restarted[2] + " to " + hp);
				restarted[2] = hp;
				restarted[3]++;
			}
			if (!isFenceGate(level, gate)) {
				// Only the blows it still needed, not five from the start.
				long needed = (long) Math.ceil(restarted[1] / (double) Sieges.RAM_BLOW);
				helper.assertTrue(restarted[3] == needed, "the gate went after " + restarted[3] + " blows since the load, with " + restarted[1]
					+ " hit points left it needed " + needed);
				helper.assertTrue(ThreatData.get(level).brokenGates(yard.hall()).contains(gate), "the broken gate isn't listed");
				helper.succeed();
			} else if (tick - restarted[0] > 250) {
				helper.fail("the siege didn't carry on after the load: the gate has " + hp);
			}
		});
	}

	/**
	 * A player breaks the gate the ram is at. The siege notes it as gone, not as broken by the rams (so it isn't on the
	 * rams' list for the builder), the way through counts as open, the pillager goes in, and the ram turns on the next
	 * gate block.
	 */
	//$ gametest_ticks_batch AREA '700' '"siegePlayerBreaks"'
	@GameTest(template = AREA, timeoutTicks = 700, batch = "siegePlayerBreaks")
	public void aGateThePlayerBreaksIsNotTheRams(GameTestHelper helper) {
		Yard yard = yard(helper, StarterBlueprints.PALISADE_GATE);
		ServerLevel level = yard.level();
		BlockPos gate = yard.at(3, 0, 1);
		BlockPos next = yard.at(2, 0, 1);
		ServerPlayer player = helper.makeMockServerPlayerInLevel();
		player.gameMode.changeGameModeForPlayer(net.minecraft.world.level.GameType.CREATIVE); // (no raider turns on a creative player)
		Leftovers.after(helper, () -> level.getServer().getPlayerList().remove(player));
		Mob ram = raider(helper, yard, Culture.Role.RAM, 14.5, 21.5);
		Mob pillager = raider(helper, yard, Culture.Role.RANGED, 14.5, 25.5);
		VillageRaids.track(level, yard.hall(), yard.culture(), List.of(ram, pillager));
		long[] broke = {-1};
		helper.onEachTick(() -> {
			long tick = helper.getTick();
			if (broke[0] < 0) {
				if (Sieges.hitPointsLeft(level, yard.hall(), gate) < Sieges.FENCE_GATE_HP) {
					helper.assertTrue(player.gameMode.destroyBlock(gate), "the player couldn't break the gate");
					broke[0] = tick;
				} else if (tick > 300) {
					helper.fail("the ram never struck the gate");
				}
				return;
			}
			if (tick == broke[0] + 2 * Sieges.EVERY) {
				ThreatData.Siege siege = yard.siege(helper);
				helper.assertTrue(siege.gates.get(0).state == ThreatData.GateState.GONE, "the gate the player broke: " + siege.gates.get(0).state);
				helper.assertTrue(Sieges.open(siege), "the way isn't open");
				helper.assertTrue(ThreatData.get(level).brokenGates(yard.hall()).isEmpty(), "the player's doing is on the rams' list");
			}
			if (tick > broke[0] + 2 * Sieges.EVERY && through(helper, yard, pillager)
				&& (Sieges.hitPointsLeft(level, yard.hall(), next) < Sieges.FENCE_GATE_HP || !isFenceGate(level, next))) {
				helper.succeed();
			} else if (tick > broke[0] + 380) {
				helper.fail("after the player broke the gate: the pillager is at " + pillager.position() + ", the next gate has "
					+ Sieges.hitPointsLeft(level, yard.hall(), next) + ", the ram (alive " + ram.isAlive() + ") is at " + ram.position() + ", gates "
					+ yard.siege(helper).gates.stream().map(g -> g.pos.toShortString() + " " + g.state + " " + g.hp).toList());
			}
		});
	}

	/**
	 * The director's budget: with a ram and nine pillagers waiting behind it, it never asks for more than four paths in
	 * one of its ticks, and it does ask (they're sent to the gate). And the crack stages, from whole to nearly gone.
	 */
	//$ gametest_ticks_batch AREA '300' '"siegePaths"'
	@GameTest(template = AREA, timeoutTicks = 300, batch = "siegePaths")
	public void theDirectorAsksForFourPathsATick(GameTestHelper helper) {
		Yard yard = yard(helper, StarterBlueprints.PALISADE_GATE);
		ServerLevel level = yard.level();
		level.getGameRules().getRule(GameRules.RULE_MOBGRIEFING).set(false, level.getServer()); // the gate holds: they keep waiting
		List<Mob> raiders = new ArrayList<>();
		raiders.add(raider(helper, yard, Culture.Role.RAM, 8.5, 27.5));
		for (int i = 0; i < 9; i++) {
			Mob mob = raider(helper, yard, Culture.Role.RANGED, 3.5 + i * 2.5, 28.5);
			mob.setTarget(null);
			raiders.add(mob);
		}
		VillageRaids.track(level, yard.hall(), yard.culture(), raiders);
		helper.assertTrue(Sieges.cracks(60, 60) == 0 && Sieges.cracks(48, 60) == 2 && Sieges.cracks(12, 60) == 8
			&& Sieges.cracks(6, 150) == 9, "the crack stages");
		helper.runAtTickTime(240, () -> {
			int most = Sieges.mostPaths(level, yard.hall());
			long all = Sieges.pathsAsked(level, yard.hall());
			helper.assertTrue(most >= 1 && most <= Sieges.MAX_PATHS, "the most paths in a director's tick: " + most);
			helper.assertTrue(all >= 5 && all <= (240 / Sieges.EVERY + 1) * Sieges.MAX_PATHS, "paths asked in 240 ticks: " + all);
			helper.succeed();
		});
	}

	/** A raid by a culture that rams gates on a village without a wall or gate build is a plain raid; so is another culture's on a walled one. */
	//$ gametest_ticks_batch AREA '100' '"siegeNeedsWalls"'
	@GameTest(template = AREA, timeoutTicks = 100, batch = "siegeNeedsWalls")
	public void onlyAWalledVillageIsBesieged(GameTestHelper helper) {
		Yard yard = yard(helper, StarterBlueprints.PALISADE_GATE);
		ServerLevel level = yard.level();
		helper.assertTrue(Sieges.isDefence(StarterBlueprints.PALISADE.id()) && Sieges.isDefence(StarterBlueprints.GATEHOUSE.id())
			&& Sieges.isDefence(ResourceLocation.fromNamespaceAndPath("aliveworkplace", "gatehouse_2"))
			&& !Sieges.isDefence(StarterBlueprints.STARTER_COTTAGE.id()), "which builds are defences");
		helper.assertTrue(Sieges.hitPoints(Blocks.SPRUCE_FENCE_GATE.defaultBlockState()) == 60 && Sieges.hitPoints(Blocks.OAK_DOOR.defaultBlockState()) == 80
			&& Sieges.hitPoints(Blocks.IRON_BARS.defaultBlockState()) == 150 && Sieges.hitPoints(Blocks.GLASS_PANE.defaultBlockState()) == 0
			&& Sieges.hitPoints(Blocks.SPRUCE_LOG.defaultBlockState()) == 0, "hit points");
		// Another culture (the monsters, no ram_gates) on the walled village.
		Mob zombie = helper.spawn(EntityType.ZOMBIE, new BlockPos(14, 2, 22));
		zombie.addTag(VillageRaids.TAG);
		VillageRaids.track(level, yard.hall(), 1);
		helper.assertTrue(VillageRaids.active(yard.hall()).isPresent() && Sieges.siege(level, yard.hall()).isEmpty(), "the monsters laid a siege");
		zombie.discard();
		VillageRaids.forget(yard.hall());
		// The besiegers on a village whose gate isn't a finished build.
		BuildSiteManager.get(level).forgetFinished(new BlueprintData.Placement(level.dimension().location(), yard.origin(), Rotation.NONE, Mirror.NONE));
		helper.assertTrue(!Sieges.walled(level, yard.hall()), "walled without a finished build");
		Mob ram = raider(helper, yard, Culture.Role.RAM, 14.5, 21.5);
		VillageRaids.track(level, yard.hall(), yard.culture(), List.of(ram));
		helper.assertTrue(Sieges.siege(level, yard.hall()).isEmpty(), "a siege on a village without a finished wall or gate");
		BlockState gate = level.getBlockState(yard.at(3, 0, 1));
		helper.runAtTickTime(80, () -> {
			helper.assertTrue(level.getBlockState(yard.at(3, 0, 1)) == gate, "a gate that isn't a finished build's was touched");
			helper.succeed();
		});
	}
}
