package io.github.jcondedata.aliveworkplace.test;

import io.github.jcondedata.aliveworkplace.WorkplaceConfig;
import io.github.jcondedata.aliveworkplace.blueprint.BlueprintData;
import io.github.jcondedata.aliveworkplace.blueprint.StarterBlueprints;
import io.github.jcondedata.aliveworkplace.build.BuildSiteManager;
import io.github.jcondedata.aliveworkplace.guard.BattleStations;
import io.github.jcondedata.aliveworkplace.guard.VillageRaids;
import io.github.jcondedata.aliveworkplace.hall.Chronicle;
import io.github.jcondedata.aliveworkplace.hall.VillageHallBlockEntity;
import io.github.jcondedata.aliveworkplace.hall.VillageHalls;
import io.github.jcondedata.aliveworkplace.people.Moods;
import io.github.jcondedata.aliveworkplace.registry.ModBlocks;
import io.github.jcondedata.aliveworkplace.registry.ModVillagers;
import io.github.jcondedata.aliveworkplace.research.Research;
import io.github.jcondedata.aliveworkplace.threat.Culture;
import io.github.jcondedata.aliveworkplace.threat.SiegeReport;
import io.github.jcondedata.aliveworkplace.threat.Sieges;
import io.github.jcondedata.aliveworkplace.threat.ThreatData;
import io.github.jcondedata.aliveworkplace.threat.Threats;
import io.github.jcondedata.aliveworkplace.work.Jobs;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.projectile.AbstractArrow;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.alchemy.PotionContents;
import net.minecraft.world.item.alchemy.Potions;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings;

/**
 * QA for Sieges III (ROADMAP 32.6), written from the spec: the edges BattleStationGameTests and QaSiegeGameTests leave
 * open. An archer's post just within / just beyond 24 blocks of a station; from a station a foe just beyond 24 blocks is
 * not shot until Ramparts gives 28; knights and medics hold the inside of a gate that faces east (a rotated build); the
 * morning's hero is the guard with the most kills even when he was overtaken late and fell before dawn, the Hero of the
 * Village lasts a day (not longer), and only the besieged village's villagers are glad.
 */
public class QaBattleStationGameTests implements FabricGameTest {
	private static final String AREA = "aliveworkplace_test:huge_area";
	private static final ResourceLocation BESIEGERS = ResourceLocation.fromNamespaceAndPath("aliveworkplace_test", "qa_station_besiegers");
	private static final BlockPos HALL = new BlockPos(7, 2, 19);
	private static final BlockPos TOWER = new BlockPos(5, 2, 13);
	private static final BlockPos GATE = new BlockPos(13, 2, 25);

	private record Village(ServerLevel level, BlockPos hall, Culture culture, VillageHallBlockEntity entity) {
		ThreatData.Siege siege(GameTestHelper helper) {
			Optional<ThreatData.Siege> siege = Sieges.siege(level, hall);
			helper.assertTrue(siege.isPresent(), "no siege on the village");
			return siege.get();
		}
	}

	/** A village with its hall at {@code hallAt} and nothing else; the raiders are husks that ram gates. */
	private static Village village(GameTestHelper helper, BlockPos hallAt) {
		Leftovers.clear(helper);
		ServerLevel level = helper.getLevel();
		int radius = VillageHalls.RADIUS;
		long time = level.getDayTime();
		VillageHalls.RADIUS = 24;
		helper.setDayTime(6000);
		BlockPos hall = helper.absolutePos(hallAt);
		Culture culture = Culture.read(BESIEGERS, com.google.gson.JsonParser.parseString("{\"where\": {\"min_villagers\": 100000}, \"roster\": ["
			+ "{\"entity\": \"minecraft:husk\", \"share\": 100, \"role\": \"melee\"}], \"tactics\": [\"ram_gates\"]}").getAsJsonObject());
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
			BattleStations.forget();
			Moods.forget();
			VillageHalls.RADIUS = radius;
			level.setDayTime(time);
			new WorkplaceConfig().apply();
		});
		helper.setBlock(hallAt, ModBlocks.VILLAGE_HALL);
		VillageHallBlockEntity entity = (VillageHallBlockEntity) level.getBlockEntity(hall);
		helper.assertTrue(entity != null, "no hall");
		return new Village(level, hall, culture, entity);
	}

	/** Stands {@code build} at {@code at}, turned by {@code rotation}, and records it finished. */
	private static BlueprintData.Placement finished(GameTestHelper helper, StarterBlueprints.Entry build, BlockPos at, Rotation rotation) {
		ServerLevel level = helper.getLevel();
		BlockPos origin = helper.absolutePos(at);
		level.getStructureManager().get(build.id()).orElseThrow().placeInWorld(level, origin, origin,
			new StructurePlaceSettings().setRotation(rotation), RandomSource.create(1L), 2);
		BlueprintData.Placement placement = new BlueprintData.Placement(level.dimension().location(), origin, rotation, Mirror.NONE);
		BuildSiteManager sites = BuildSiteManager.get(level);
		sites.recordFinished(build.id(), placement, UUID.randomUUID());
		Leftovers.after(helper, () -> sites.forgetFinished(placement));
		return placement;
	}

	/** A guard whose Guard Post is at (x, 2, z + 2), standing at (x, 2, z); {@code off}: a bow makes an archer, a shield a knight, a healing potion a medic. */
	private static Villager guard(GameTestHelper helper, ItemStack off, int x, int z) {
		BlockPos post = new BlockPos(x, 2, z + 2);
		helper.setBlock(post, ModBlocks.GUARD_POST);
		Villager v = helper.spawn(EntityType.VILLAGER, new BlockPos(x, 2, z));
		Jobs.employ(helper.getLevel(), v, helper.absolutePos(post), ModVillagers.GUARD_POST_POI, ModVillagers.GUARD);
		v.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.IRON_SWORD));
		v.setItemSlot(EquipmentSlot.OFFHAND, off);
		return v;
	}

	/** A raider that stands still at (x, 2, z). */
	private static Mob raider(GameTestHelper helper, double x, double z) {
		ServerLevel level = helper.getLevel();
		Mob mob = EntityType.HUSK.create(level);
		BlockPos base = helper.absolutePos(BlockPos.ZERO);
		mob.moveTo(base.getX() + x, base.getY() + 2, base.getZ() + z, 0f, 0f);
		mob.addTag(VillageRaids.TAG);
		mob.setNoAi(true);
		mob.setPersistenceRequired();
		level.addFreshEntity(mob);
		return mob;
	}

	private static long arrows(GameTestHelper helper, Villager archer) {
		return helper.getLevel().getEntitiesOfClass(AbstractArrow.class, helper.getBounds().inflate(16), a -> a.getOwner() == archer).size();
	}

	private static double nearest(List<BlockPos> stations, BlockPos post) {
		return stations.stream().mapToDouble(s -> Math.sqrt(Math.pow(s.getX() - post.getX(), 2) + Math.pow(s.getZ() - post.getZ(), 2))).min().orElse(1e9);
	}

	/**
	 * "An archer whose post is within 24 blocks of a finished Wall Tower": the Wall Tower stands in a corner; one archer's
	 * post is 23-24 blocks (across the ground) from its nearest station, another's 24.05-24.9. Both are guards of the
	 * village. The first takes a station on the tower; the second has none (he rallies as before).
	 */
	//$ gametest_ticks_batch AREA '400' '"qaStationReach"'
	@GameTest(template = AREA, timeoutTicks = 400, batch = "qaStationReach")
	public void anArcherWhosePostIsJustBeyond24GetsNoStation(GameTestHelper helper) {
		Village village = village(helper, new BlockPos(14, 2, 14));
		ServerLevel level = village.level();
		finished(helper, StarterBlueprints.WALL_TOWER, new BlockPos(1, 2, 1), Rotation.NONE);
		List<BlockPos> stations = BattleStations.stations(level, village.hall());
		helper.assertTrue(!stations.isEmpty(), "the Wall Tower has no stations");
		BlockPos within = null;
		BlockPos beyond = null;
		for (int x = 29; x >= 10 && (within == null || beyond == null); x--) {
			for (int z = 29; z >= 12; z--) {
				BlockPos post = helper.absolutePos(new BlockPos(x, 2, z));
				double d = nearest(stations, post);
				if (beyond == null && d > 24.05 && d < 24.9 && x >= 12) {
					beyond = new BlockPos(x, 2, z);
				} else if (within == null && d > 23 && d <= 23.8 && x <= 27 && (beyond == null || Math.abs(beyond.getX() - x) + Math.abs(beyond.getZ() - z) > 3)) {
					within = new BlockPos(x, 2, z);
				}
			}
		}
		helper.assertTrue(within != null && beyond != null, "no posts at the edge: " + within + " / " + beyond);
		BlockPos w = within;
		BlockPos b = beyond;
		helper.assertTrue(VillageHalls.area(village.hall()).contains(helper.absolutePos(b).getCenter())
			&& VillageHalls.area(village.hall()).contains(helper.absolutePos(w).getCenter()), "a post outside the village");
		Villager near = guard(helper, new ItemStack(Items.BOW), w.getX(), w.getZ() - 2);
		Villager far = guard(helper, new ItemStack(Items.BOW), b.getX(), b.getZ() - 2);
		Mob foe = raider(helper, 3.5, 28.5);
		foe.setInvulnerable(true);
		VillageRaids.track(level, village.hall(), village.culture(), List.of(foe));
		village.siege(helper);
		helper.runAtTickTime(120, () -> {
			Optional<BlockPos> n = BattleStations.post(near);
			helper.assertTrue(n.isPresent() && stations.contains(n.get()), "the archer whose post is " + nearest(stations, helper.absolutePos(w))
				+ " blocks from the tower has no station: " + n);
			helper.assertTrue(!BattleStations.stationed(far), "the archer whose post is " + nearest(stations, helper.absolutePos(b))
				+ " blocks from the tower's nearest station has one: " + BattleStations.post(far));
			helper.succeed();
		});
	}

	/**
	 * From the station the archer reaches 24 blocks: a raider 25.3 blocks off (in plain sight below) is not shot at, and
	 * he doesn't climb down after it; once the village has Ramparts (28 blocks) he shoots it from the station.
	 */
	//$ gametest_ticks_batch AREA '1200' '"qaStationOutOfReach"'
	@GameTest(template = AREA, timeoutTicks = 1200, batch = "qaStationOutOfReach")
	public void fromAStationAFoeJustBeyond24IsShotOnlyWithRamparts(GameTestHelper helper) {
		Village village = village(helper, new BlockPos(14, 2, 16));
		ServerLevel level = village.level();
		finished(helper, StarterBlueprints.WALL_TOWER, new BlockPos(1, 2, 1), Rotation.NONE); // in a corner: the whole area lies before it
		Villager archer = guard(helper, new ItemStack(Items.BOW), 10, 10);
		Mob foe = raider(helper, 28.5, 28.5);
		foe.setInvulnerable(true); // (a hit would end the siege; the arrows count)
		VillageRaids.track(level, village.hall(), village.culture(), List.of(foe));
		village.siege(helper);
		long[] on = {-1, -1}; // on the station, Ramparts given
		helper.onEachTick(() -> {
			long tick = helper.getTick();
			if (on[0] < 0) {
				helper.assertTrue(arrows(helper, archer) == 0, "he shot from the ground at a foe " + archer.distanceTo(foe) + " blocks off");
				if (BattleStations.onStation(archer)) {
					on[0] = tick;
					if (!archer.hasLineOfSight(foe) || archer.distanceTo(foe) < 24.8 || archer.distanceTo(foe) > 25.8) {
						// A spot 25-25.8 blocks off in his sight from the station
						net.minecraft.world.phys.Vec3 best = null;
						for (double z = 29.5; z >= 0.5 && best == null; z -= 0.5) {
							for (double x = 29.5; x >= 0.5 && best == null; x -= 0.5) {
								net.minecraft.world.phys.Vec3 at = helper.absoluteVec(new net.minecraft.world.phys.Vec3(x, 2, z));
								foe.teleportTo(at.x, at.y, at.z);
								double d = archer.distanceTo(foe);
								if (d > 25 && d < 25.8 && archer.hasLineOfSight(foe)) {
									best = at;
								}
							}
						}
						helper.assertTrue(best != null, "no spot 25-25.8 blocks off in the archer's sight from " + BattleStations.post(archer));
						foe.teleportTo(best.x, best.y, best.z);
					}
					double off = archer.distanceTo(foe);
					helper.assertTrue(off > 24.8 && off < 26 && archer.hasLineOfSight(foe), "the foe is " + off + " blocks off, in sight " + archer.hasLineOfSight(foe));
				} else if (tick > 450) {
					helper.fail("the archer isn't on the tower's top: " + archer.position());
				}
				return;
			}
			if (on[1] < 0) {
				helper.assertTrue(BattleStations.onStation(archer), "he left his station for a foe out of reach: at " + archer.position());
				helper.assertTrue(arrows(helper, archer) == 0, "from the station he shot at a foe " + archer.distanceTo(foe) + " blocks off (beyond 24)");
				if (tick >= on[0] + 200) {
					village.entity().setResearch(new Research.State(Map.of("drill", 1, "fortification", 1, "ramparts", 1), Optional.empty(), 0, false));
					on[1] = tick;
				}
				return;
			}
			if (arrows(helper, archer) > 0) {
				helper.assertTrue(BattleStations.onStation(archer), "he came down to shoot: at " + archer.position());
				helper.assertTrue(BattleStations.range(archer, 16) == 28, "range " + BattleStations.range(archer, 16));
				helper.succeed();
			} else if (tick > on[1] + 300) {
				helper.fail("with Ramparts (28 blocks) he never shot at the foe " + archer.distanceTo(foe) + " blocks off; range "
					+ BattleStations.range(archer, 16) + ", in sight " + archer.hasLineOfSight(foe));
			}
		});
	}

	/**
	 * The breach gate faces east (a Palisade Gate turned a quarter, the village to the east, the raiders to the west): the
	 * knight's place is 3 blocks inside it (east), the medic's 6, behind him, both in line with the breach, and both get there.
	 */
	//$ gametest_ticks_batch AREA '700' '"qaStationEastGate"'
	@GameTest(template = AREA, timeoutTicks = 700, batch = "qaStationEastGate")
	public void knightsHoldTheInsideOfAGateThatFacesAnotherWay(GameTestHelper helper) {
		Village village = village(helper, new BlockPos(24, 2, 13));
		ServerLevel level = village.level();
		finished(helper, StarterBlueprints.PALISADE_GATE, new BlockPos(8, 2, 10), Rotation.CLOCKWISE_90);
		Villager knight = guard(helper, new ItemStack(Items.SHIELD), 18, 10);
		Villager medic = guard(helper, PotionContents.createItemStack(Items.POTION, Potions.HEALING), 20, 10);
		Mob foe = raider(helper, 0.5, 13.5);
		foe.setInvulnerable(true);
		VillageRaids.track(level, village.hall(), village.culture(), List.of(foe));
		ThreatData.Siege siege = village.siege(helper);
		BlockPos breach = siege.breach;
		helper.assertTrue(breach != null && level.getBlockState(breach).getBlock() instanceof net.minecraft.world.level.block.FenceGateBlock,
			"the breach isn't the turned gate's fence gate: " + (breach == null ? null : helper.relativePos(breach)));
		helper.assertTrue(siege.out == Direction.WEST, "the outside of the east-facing gate is " + siege.out + ", the raider is to the west");
		helper.onEachTick(() -> {
			if (helper.getTick() < 45) {
				return;
			}
			Optional<BlockPos> k = BattleStations.post(knight);
			Optional<BlockPos> m = BattleStations.post(medic);
			helper.assertTrue(k.isPresent() && m.isPresent(), "the knight's place " + k + ", the medic's " + m);
			helper.assertTrue(k.get().getZ() == breach.getZ() && k.get().getX() == breach.getX() + BattleStations.KNIGHT_BACK,
				"the knight's place " + helper.relativePos(k.get()) + " isn't 3 inside (east of) the breach " + helper.relativePos(breach));
			helper.assertTrue(m.get().getZ() == breach.getZ() && m.get().getX() == breach.getX() + BattleStations.MEDIC_BACK,
				"the medic's place " + helper.relativePos(m.get()) + " isn't 6 inside (east of) the breach " + helper.relativePos(breach));
			if (k.get().closerToCenterThan(knight.position(), 1.6) && m.get().closerToCenterThan(medic.position(), 1.6)) {
				helper.succeed();
			}
		});
	}

	/**
	 * The morning after, at its edges. Guard Aldred kills first, then Brannoc kills twice (he overtakes) and falls
	 * himself before dawn: the report still names Brannoc, with 2 kills. The player who fought is a Hero of the Village
	 * for a day and not more; a villager of the village is glad ("we held", +10), one outside the village isn't; a
	 * builder's bench outside the village doesn't put defences first.
	 */
	//$ gametest_ticks_batch AREA '300' '"qaStationMorning"'
	@GameTest(template = AREA, timeoutTicks = 300, batch = "qaStationMorning")
	public void theHeroIsTheGuardWithMostKillsEvenIfHeFell(GameTestHelper helper) {
		Village village = village(helper, HALL);
		ServerLevel level = village.level();
		finished(helper, StarterBlueprints.PALISADE_GATE, GATE, Rotation.NONE);
		Villager first = guard(helper, new ItemStack(Items.SHIELD), 12, 21);
		Villager hero = guard(helper, new ItemStack(Items.SHIELD), 14, 21);
		first.setNoAi(true);
		hero.setNoAi(true);
		first.setCustomName(net.minecraft.network.chat.Component.literal("Aldred"));
		hero.setCustomName(net.minecraft.network.chat.Component.literal("Brannoc"));
		Villager farmer = helper.spawn(EntityType.VILLAGER, new BlockPos(9, 2, 21));
		farmer.setNoAi(true);
		Villager stranger = EntityType.VILLAGER.create(level);
		BlockPos away = village.hall().offset(VillageHalls.RADIUS + 8, 0, 0);
		stranger.moveTo(away.getX() + 0.5, away.getY(), away.getZ() + 0.5, 0f, 0f);
		stranger.setNoAi(true);
		stranger.setNoGravity(true);
		level.addFreshEntity(stranger);
		Leftovers.after(helper, stranger::discard);
		ServerPlayer player = helper.makeMockServerPlayerInLevel();
		Leftovers.after(helper, () -> level.getServer().getPlayerList().remove(player));
		List<Mob> raiders = List.of(raider(helper, 14.5, 29.5), raider(helper, 15.5, 29.5), raider(helper, 16.5, 29.5), raider(helper, 17.5, 29.5),
			raider(helper, 18.5, 29.5));
		VillageRaids.track(level, village.hall(), village.culture(), raiders);
		ThreatData.Siege siege = village.siege(helper);
		raiders.get(0).hurt(level.damageSources().mobAttack(first), 1000f);
		raiders.get(1).hurt(level.damageSources().mobAttack(hero), 1000f);
		raiders.get(2).hurt(level.damageSources().mobAttack(hero), 1000f);
		raiders.get(3).hurt(level.damageSources().playerAttack(player), 1000f);
		helper.runAtTickTime(20, () -> {
			helper.assertTrue(hero.getUUID().equals(SiegeReport.hero(siege)), "the hero is " + SiegeReport.hero(siege) + ", not the guard with 2 kills");
			hero.discard(); // he fell before dawn
			raiders.get(4).discard();
			VillageRaids.tick(level, village.hall(), 0, 0, -100, day -> { });
			helper.assertTrue(VillageRaids.active(village.hall()).isEmpty() && siege.over && !siege.fled, "the raid isn't over and won");
			level.setDayTime(siege.dawn + 20);
		});
		helper.runAtTickTime(80, () -> {
			helper.assertTrue(Sieges.siege(level, village.hall()).isEmpty(), "the siege wasn't lifted at dawn");
			List<String> reports = village.entity().chronicle().stream().filter(e -> e.kind() == Chronicle.Kind.SIEGE).map(e -> e.text().getString()).toList();
			helper.assertTrue(reports.size() == 1 && reports.get(0).contains("Hero of the night: Brannoc, with 2 kills."), "the report: " + reports);
			MobEffectInstance effect = player.getEffect(MobEffects.HERO_OF_THE_VILLAGE);
			helper.assertTrue(effect != null && effect.getDuration() > SiegeReport.HERO_TICKS - 200 && effect.getDuration() <= 24000,
				"the fighter's Hero of the Village isn't a day: " + effect);
			helper.assertTrue(Moods.work(level, farmer).good().stream().anyMatch(c -> c.getString().equals("we held against the siege")), "the villager isn't glad");
			helper.assertTrue(Moods.work(level, stranger).good().stream().noneMatch(c -> c.getString().equals("we held against the siege")),
				"a villager outside the village is glad it held");
			helper.assertTrue(SiegeReport.defencesFirst(level, village.hall().offset(3, 0, 3)), "the village's builders don't put defences first");
			helper.assertTrue(!SiegeReport.defencesFirst(level, away), "a bench outside the village puts defences first");
			helper.succeed();
		});
	}
}
