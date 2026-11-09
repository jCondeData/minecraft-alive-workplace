package io.github.jcondedata.aliveworkplace.test;

import io.github.jcondedata.aliveworkplace.WorkplaceConfig;
import io.github.jcondedata.aliveworkplace.blueprint.BlueprintData;
import io.github.jcondedata.aliveworkplace.blueprint.StarterBlueprints;
import io.github.jcondedata.aliveworkplace.build.BuildSiteManager;
import io.github.jcondedata.aliveworkplace.guard.BattleStations;
import io.github.jcondedata.aliveworkplace.guard.VillageRaids;
import io.github.jcondedata.aliveworkplace.hall.VillageHalls;
import io.github.jcondedata.aliveworkplace.registry.ModBlocks;
import io.github.jcondedata.aliveworkplace.threat.Culture;
import io.github.jcondedata.aliveworkplace.threat.Piglins;
import io.github.jcondedata.aliveworkplace.threat.Sieges;
import io.github.jcondedata.aliveworkplace.threat.ThreatData;
import io.github.jcondedata.aliveworkplace.threat.Threats;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.monster.Zoglin;
import net.minecraft.world.level.block.FenceGateBlock;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings;

/**
 * QA (lane D, 2026-11-09) for a hoglin ram whose band zombifies mid-siege (ROADMAP 32.10, Piglins javadoc: "The
 * Warlord's fall ... his band left in the Overworld, at the lair and out raiding, turns zombified at once ... hoglins
 * into zoglins. The raiders among them stay in the raid (they keep its tags) until it ends"; 32.4: "The rams (role ram)
 * walk to the breach and strike"). The piglin incursion's rams are hoglins and its tactics ram gates. A hoglin ram held
 * at a Palisade Gate's middle fence gate (as QaSiegeGameTests holds its ravager) strikes it; Piglins.zombify turns it
 * into a zoglin (convertTo: a new UUID) after the first blow; the zoglin is still the raid's ram and strikes on until
 * the gate goes, nothing dropped, the broken gate listed for a builder. (The director counts its raiders again every
 * 200 ticks, so the zoglin's first blow can wait that long: allowed; then a blow every 40 ticks.)
 */
public class QaZoglinRam1109dGameTests {
	static final String AREA = "aliveworkplace_test:huge_area";
	private static final ResourceLocation HOGLINS = ResourceLocation.fromNamespaceAndPath("aliveworkplace_test", "qa_zoglin_ram_1109d");
	private static final BlockPos HALL = new BlockPos(14, 2, 4);
	private static final BlockPos GATE = new BlockPos(10, 2, 10);

	//$ gametest_ticks_batch AREA '600' '"qaZoglinRam1109d"'
	@GameTest(template = AREA, timeoutTicks = 600, batch = "qaZoglinRam1109d")
	public void aHoglinRamThatZombifiesMidSiegeBreaksTheGateAsAZoglin(GameTestHelper helper) {
		Leftovers.clear(helper);
		Leftovers.finished(helper);
		ServerLevel level = helper.getLevel();
		int radius = VillageHalls.RADIUS;
		long time = level.getDayTime();
		VillageHalls.RADIUS = 24;
		level.setDayTime(18000);
		BlockPos hall = helper.absolutePos(HALL);
		Culture culture = Culture.read(HOGLINS, com.google.gson.JsonParser.parseString("{\"where\": {\"min_villagers\": 100000}, \"roster\": ["
			+ "{\"entity\": \"minecraft:hoglin\", \"share\": 100, \"role\": \"ram\"}], \"tactics\": [\"ram_gates\"]}").getAsJsonObject());
		Map<ResourceLocation, Culture> loaded = new LinkedHashMap<>();
		Threats.all().forEach(c -> loaded.put(c.id(), c));
		Map<ResourceLocation, Culture> with = new LinkedHashMap<>(loaded);
		with.put(HOGLINS, culture);
		Threats.setForTest(with);
		Leftovers.after(helper, () -> {
			Threats.setForTest(loaded);
			level.getEntitiesOfClass(Mob.class, helper.getBounds().inflate(48), m -> m.getTags().contains(VillageRaids.TAG)
				|| m.getTags().contains(Threats.RAIDER_TAG)).forEach(Mob::discard);
			Sieges.siege(level, hall).ifPresent(s -> Sieges.lift(level, s));
			VillageRaids.forget(hall);
			ThreatData.get(level).forgetSiege(hall);
			ThreatData.get(level).forgetPast(hall);
			Sieges.forget();
			BattleStations.forget();
			VillageHalls.RADIUS = radius;
			level.setDayTime(time);
			new WorkplaceConfig().apply();
		});
		helper.setBlock(HALL, ModBlocks.VILLAGE_HALL);
		BlockPos origin = helper.absolutePos(GATE);
		level.getStructureManager().get(StarterBlueprints.PALISADE_GATE.id()).orElseThrow().placeInWorld(level, origin, origin,
			new StructurePlaceSettings(), RandomSource.create(1L), 2);
		BlueprintData.Placement placement = new BlueprintData.Placement(level.dimension().location(), origin, Rotation.NONE, Mirror.NONE);
		BuildSiteManager sites = BuildSiteManager.get(level);
		sites.recordFinished(StarterBlueprints.PALISADE_GATE.id(), placement, UUID.randomUUID());
		Leftovers.after(helper, () -> sites.forgetFinished(placement));

		BlockPos gate = origin.offset(3, 0, 1);
		helper.assertTrue(level.getBlockState(gate).getBlock() instanceof FenceGateBlock, "no fence gate at " + gate);
		Culture.Member member = culture.roster().get(0);
		Mob hoglin = Threats.create(level, member);
		helper.assertTrue(hoglin != null, "no hoglin");
		hoglin.moveTo(gate.getX() + 0.5, gate.getY(), gate.getZ() + 2.5, 180f, 0f); // (a hoglin is narrower than a ravager: in reach)
		hoglin.finalizeSpawn(level, level.getCurrentDifficultyAt(hoglin.blockPosition()), MobSpawnType.EVENT, null);
		if (hoglin instanceof net.minecraft.world.entity.monster.hoglin.Hoglin h) {
			h.setBaby(false);
		}
		Threats.outfit(level, hoglin, culture, member);
		hoglin.addTag(VillageRaids.TAG);
		hoglin.setPersistenceRequired();
		hoglin.setNoAi(true);
		level.addFreshEntity(hoglin);
		VillageRaids.track(level, hall, culture, List.of(hoglin));
		Optional<ThreatData.Siege> laid = Sieges.siege(level, hall);
		helper.assertTrue(laid.isPresent(), "no siege on the village");
		ThreatData.Siege siege = laid.get();
		helper.assertTrue(gate.equals(siege.breach), "the breach " + siege.breach + " isn't the middle fence gate " + gate);
		int full = Sieges.FENCE_GATE_HP;
		helper.assertTrue(Sieges.hitPointsLeft(level, hall, gate) == full, "the gate doesn't start at " + full);

		Mob[] ram = {hoglin};
		long[] turned = {-1};
		int[] hpAtTurn = {full};
		int[] last = {full};
		List<String> blows = new java.util.ArrayList<>();
		helper.onEachTick(() -> {
			long tick = helper.getTick();
			int hp = Sieges.hitPointsLeft(level, hall, gate);
			if (turned[0] < 0) {
				if (hp < full) {
					Mob zoglin = Piglins.zombify(level, ram[0]);
					helper.assertTrue(zoglin instanceof Zoglin, "the hoglin didn't turn into a zoglin: " + zoglin);
					helper.assertTrue(!zoglin.getUUID().equals(hoglin.getUUID()), "the zoglin kept the hoglin's UUID: the test would prove nothing");
					helper.assertTrue(Threats.role(zoglin) == Culture.Role.RAM, "the zoglin isn't a ram: " + zoglin.getTags());
					ram[0] = zoglin;
					turned[0] = tick;
					hpAtTurn[0] = hp;
					last[0] = hp;
				} else if (tick > 200) {
					helper.fail("the hoglin ram never struck the gate (the test would prove nothing): " + Sieges.directorState(level, hall, hoglin));
				}
				return;
			}
			helper.assertTrue(ram[0].isAlive(), "the zoglin ram died");
			if (hp != last[0]) {
				blows.add((tick - turned[0]) + ":" + hp);
				last[0] = hp;
				if (blows.size() > 1) {
					String[] a = blows.get(blows.size() - 2).split(":");
					helper.assertTrue(tick - turned[0] - Long.parseLong(a[0]) == Sieges.RAM_EVERY, "the zoglin's blows aren't 40 ticks apart: " + blows);
				}
			}
			if (hp == 0) {
				helper.assertTrue(level.getBlockState(gate).isAir(), "the gate's hit points are 0 but it stands: " + level.getBlockState(gate));
				helper.assertTrue(level.getEntitiesOfClass(net.minecraft.world.entity.item.ItemEntity.class, helper.getBounds().inflate(4)).isEmpty(),
					"the broken gate dropped something");
				helper.assertTrue(ThreatData.get(level).brokenGates(hall).contains(gate), "the broken gate isn't listed for a builder");
				helper.assertTrue(VillageRaids.raiders(level, hall).contains(ram[0]), "the zoglin isn't one of the raid's raiders");
				helper.succeed();
			} else if (tick - turned[0] > 200 + 5 * Sieges.RAM_EVERY + 20) {
				helper.fail("the zoglin, a ram, didn't break the gate: " + hp + " of " + hpAtTurn[0] + " hit points left " + (tick - turned[0])
					+ " ticks after it turned; blows " + blows + "; zoglin at " + ram[0].position().subtract(net.minecraft.world.phys.Vec3.atBottomCenterOf(gate))
					+ "; " + Sieges.directorState(level, hall, ram[0]));
			}
		});
	}
}
