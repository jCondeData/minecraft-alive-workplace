package io.github.jcondedata.aliveworkplace.test;

import io.github.jcondedata.aliveworkplace.WorkplaceConfig;
import io.github.jcondedata.aliveworkplace.blueprint.BlueprintData;
import io.github.jcondedata.aliveworkplace.blueprint.StarterBlueprints;
import io.github.jcondedata.aliveworkplace.build.BuildSite;
import io.github.jcondedata.aliveworkplace.build.BuildSiteManager;
import io.github.jcondedata.aliveworkplace.build.Builders;
import io.github.jcondedata.aliveworkplace.build.Upkeep;
import io.github.jcondedata.aliveworkplace.guard.VillageRaids;
import io.github.jcondedata.aliveworkplace.hall.Chronicle;
import io.github.jcondedata.aliveworkplace.hall.VillageHallBlockEntity;
import io.github.jcondedata.aliveworkplace.hall.VillageHalls;
import io.github.jcondedata.aliveworkplace.people.Moods;
import io.github.jcondedata.aliveworkplace.registry.ModBlocks;
import io.github.jcondedata.aliveworkplace.registry.ModVillagers;
import io.github.jcondedata.aliveworkplace.research.Research;
import io.github.jcondedata.aliveworkplace.guard.BattleStations;
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
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.locale.Language;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
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
 * Sieges III: battle stations and the morning after (ROADMAP 32.6). Each test stands a finished Wall Tower (and for
 * some a Palisade Gate) by a Village Hall, with guards at a Guard Post and a raider of a besieging culture outside.
 * They ask what the item promises: an archer is on the tower's top within 400 ticks of the siege starting; from the
 * station he shoots a foe 22 blocks off, from the ground he closes to 16 first; knights hold the inside of the breach
 * gate and medics stand behind them; the morning's report names the hero, the players who fought are Heroes of the
 * Village, the villagers are glad; Ramparts doubles a gate's hit points and gives 28 blocks; a builder sees to the
 * broken gate before a house; and the ways it could break: a station's floor broken under the archer, the tower's top
 * built over (no free station), a save and load in mid-siege, a save from before 32.6, and the {@code sieges} switch off.
 */
public class BattleStationGameTests implements FabricGameTest {
	private static final String AREA = "aliveworkplace_test:huge_area";
	private static final ResourceLocation BESIEGERS = ResourceLocation.fromNamespaceAndPath("aliveworkplace_test", "station_besiegers");
	private static final BlockPos HALL = new BlockPos(7, 2, 19);
	/** The Wall Tower's corner: it stands on x 5 to 9, z 13 to 17, its top floor's walk at y 9. */
	private static final BlockPos TOWER = new BlockPos(5, 2, 13);
	/**
	 * The station an archer takes with the raiders to the south or east: the tower's own door (7, 3, 13) is the breach
	 * then, its outside the south, and this is the station nearest that side.
	 */
	private static final BlockPos STATION = new BlockPos(7, 9, 16);
	/** A spot in the village (where a builder's bench could be). */
	private static final BlockPos POST = new BlockPos(11, 2, 22);
	/** The Palisade Gate's corner: its fence gates at x 15 to 17, z 26; the outside is to the south. */
	private static final BlockPos GATE = new BlockPos(13, 2, 25);

	private record Village(ServerLevel level, BlockPos hall, Culture culture, VillageHallBlockEntity entity) {
		ThreatData.Siege siege(GameTestHelper helper) {
			Optional<ThreatData.Siege> siege = Sieges.siege(level, hall);
			helper.assertTrue(siege.isPresent(), "no siege on the village");
			return siege.get();
		}
	}

	private static Village village(GameTestHelper helper, boolean gate) {
		Leftovers.clear(helper);
		ServerLevel level = helper.getLevel();
		int radius = VillageHalls.RADIUS;
		long time = level.getDayTime();
		VillageHalls.RADIUS = 24;
		helper.setDayTime(6000);
		BlockPos hall = helper.absolutePos(HALL);
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
		helper.setBlock(HALL, ModBlocks.VILLAGE_HALL);
		finished(helper, StarterBlueprints.WALL_TOWER, TOWER);
		if (gate) {
			finished(helper, StarterBlueprints.PALISADE_GATE, GATE);
		}
		VillageHallBlockEntity entity = (VillageHallBlockEntity) level.getBlockEntity(hall);
		helper.assertTrue(entity != null, "no hall");
		return new Village(level, hall, culture, entity);
	}

	/** Stands {@code build} at {@code at} and records it finished. */
	private static BlueprintData.Placement finished(GameTestHelper helper, StarterBlueprints.Entry build, BlockPos at) {
		ServerLevel level = helper.getLevel();
		BlockPos origin = helper.absolutePos(at);
		level.getStructureManager().get(build.id()).orElseThrow().placeInWorld(level, origin, origin, new StructurePlaceSettings(), RandomSource.create(1L), 2);
		BlueprintData.Placement placement = new BlueprintData.Placement(level.dimension().location(), origin, Rotation.NONE, Mirror.NONE);
		BuildSiteManager sites = BuildSiteManager.get(level);
		sites.recordFinished(build.id(), placement, UUID.randomUUID());
		Leftovers.after(helper, () -> sites.forgetFinished(placement));
		return placement;
	}

	/** A guard with a Guard Post of their own two blocks south of them, with {@code off} in the off hand: a bow makes an archer, a shield a knight, a healing potion a medic. */
	private static Villager guard(GameTestHelper helper, ItemStack off, int x, int z) {
		BlockPos post = new BlockPos(x, 2, z + 2); // (a Guard Post takes one guard)
		helper.setBlock(post, ModBlocks.GUARD_POST);
		Villager v = helper.spawn(EntityType.VILLAGER, new BlockPos(x, 2, z));
		Jobs.employ(helper.getLevel(), v, helper.absolutePos(post), ModVillagers.GUARD_POST_POI, ModVillagers.GUARD);
		v.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.IRON_SWORD));
		v.setItemSlot(EquipmentSlot.OFFHAND, off);
		return v;
	}

	/** A raider that stands still at (x, 2, z): a husk carrying the raid's tag. */
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

	/**
	 * The item's first promise, and the second half of it. An archer whose post is within 24 blocks of a finished Wall
	 * Tower is on its top, on the station nearest the breach's outside, within 400 ticks of the siege starting; from there he shoots the
	 * raider standing 22 blocks off and hits it. The tower's blueprint is read once. With Ramparts he reaches 28 blocks.
	 */
	//$ gametest_ticks_batch AREA '900' '"stationArcher"'
	@GameTest(template = AREA, timeoutTicks = 900, batch = "stationArcher")
	public void anArcherTakesTheTowersTopAndShootsFromIt(GameTestHelper helper) {
		Village village = village(helper, false);
		ServerLevel level = village.level();
		int reads = BattleStations.reads;
		Villager archer = guard(helper, new ItemStack(Items.BOW), 12, 21);
		Mob foe = raider(helper, 29.5, 16.5);
		BlockPos station = helper.absolutePos(STATION);
		List<BlockPos> stations = BattleStations.stations(level, village.hall());
		helper.assertTrue(stations.size() == 7 && stations.contains(station) && stations.stream().allMatch(s -> s.getY() == station.getY()),
			"the Wall Tower's stations are the seven free planks of its top floor: " + stations);
		VillageRaids.track(level, village.hall(), village.culture(), List.of(foe));
		village.siege(helper);
		long[] seen = {-1, -1}; // on the station, the first arrow
		helper.onEachTick(() -> {
			long tick = helper.getTick();
			if (seen[0] < 0) {
				helper.assertTrue(arrows(helper, archer) == 0, "he shot at a foe " + archer.distanceTo(foe) + " blocks off from the ground");
				if (BattleStations.onStation(archer)) {
					seen[0] = tick;
					helper.assertTrue(BattleStations.post(archer).equals(Optional.of(station)), "his station is " + BattleStations.post(archer) + ", not the one nearest the breach's outside, " + station);
					helper.assertTrue(archer.getY() >= station.getY() - 0.1, "on the station at y " + archer.getY());
					double off = archer.distanceTo(foe);
					helper.assertTrue(off >= 22 && off < BattleStations.RANGE, "the foe is " + off + " blocks off");
					helper.assertTrue(BattleStations.range(archer, 16) == BattleStations.RANGE && BattleStations.heightBonus(archer, foe) == 1.25f,
						"range " + BattleStations.range(archer, 16) + ", bonus " + BattleStations.heightBonus(archer, foe));
				} else if (tick > 400) {
					helper.fail("the archer isn't on the tower's top within 400 ticks: he is at " + archer.position() + ", his place " + BattleStations.post(archer));
				}
				return;
			}
			if (seen[1] < 0 && arrows(helper, archer) > 0) {
				seen[1] = tick;
			}
			if (seen[1] >= 0 && foe.getHealth() < foe.getMaxHealth()) {
				helper.assertTrue(BattleStations.reads == reads + 1, "the tower's blueprint was read " + (BattleStations.reads - reads) + " times");
				helper.assertTrue(BattleStations.mostPaths(level, village.hall()) <= BattleStations.MAX_PATHS, "paths a tick: " + BattleStations.mostPaths(level, village.hall()));
				village.entity().setResearch(new Research.State(Map.of("drill", 1, "fortification", 1, "ramparts", 1), Optional.empty(), 0, false));
				helper.runAfterDelay(41, () -> {
					helper.assertTrue(BattleStations.range(archer, 16) == BattleStations.RAMPARTS_RANGE, "with Ramparts he reaches " + BattleStations.range(archer, 16));
					helper.succeed();
				});
				seen[1] = Long.MAX_VALUE - 1000;
				seen[0] = Long.MAX_VALUE - 1000;
			} else if (tick > seen[0] + 400 && seen[0] < Long.MAX_VALUE - 1000) {
				helper.fail("from the station he " + (seen[1] < 0 ? "never shot at" : "shot at but never hit") + " the foe " + archer.distanceTo(foe) + " blocks off");
			}
		});
	}

	/**
	 * No free station: the tower's top is built over, so the archer has no place, answers as before, and shoots the
	 * raider 22 blocks off only once he has closed to 16.
	 */
	//$ gametest_ticks_batch AREA '700' '"stationGround"'
	@GameTest(template = AREA, timeoutTicks = 700, batch = "stationGround")
	public void onTheGroundAnArcherClosesInFirst(GameTestHelper helper) {
		Village village = village(helper, false);
		ServerLevel level = village.level();
		for (int x = 6; x <= 8; x++) {
			for (int z = 14; z <= 16; z++) {
				helper.setBlock(new BlockPos(x, 9, z), Blocks.STONE);
			}
		}
		Villager archer = guard(helper, new ItemStack(Items.BOW), 8, 22);
		Mob foe = raider(helper, 29.5, 21.5);
		helper.assertTrue(archer.distanceTo(foe) >= 21, "the foe starts " + archer.distanceTo(foe) + " blocks off");
		VillageRaids.track(level, village.hall(), village.culture(), List.of(foe));
		village.siege(helper);
		helper.onEachTick(() -> {
			helper.assertTrue(!BattleStations.stationed(archer), "he has a place though every station is built over: " + BattleStations.post(archer));
			if (arrows(helper, archer) > 0) {
				helper.assertTrue(archer.distanceTo(foe) <= 16.6, "he shot from the ground at a foe " + archer.distanceTo(foe) + " blocks off");
				helper.succeed();
			}
		});
	}

	/** A station's floor is broken under the archer in mid-siege: he gives it up, takes another and is on it again. */
	//$ gametest_ticks_batch AREA '900' '"stationBroken"'
	@GameTest(template = AREA, timeoutTicks = 900, batch = "stationBroken")
	public void aBrokenStationIsGivenUpForAnother(GameTestHelper helper) {
		Village village = village(helper, false);
		ServerLevel level = village.level();
		Villager archer = guard(helper, new ItemStack(Items.BOW), 12, 21);
		Mob foe = raider(helper, 14.5, 29.5);
		BlockPos station = helper.absolutePos(STATION);
		VillageRaids.track(level, village.hall(), village.culture(), List.of(foe));
		boolean[] broken = {false};
		helper.onEachTick(() -> {
			if (!broken[0]) {
				if (BattleStations.onStation(archer)) {
					helper.assertTrue(BattleStations.post(archer).equals(Optional.of(station)), "his station: " + BattleStations.post(archer));
					level.setBlock(station.below(), Blocks.AIR.defaultBlockState(), 3);
					broken[0] = true;
				} else if (helper.getTick() > 400) {
					helper.fail("the archer never took the station: he is at " + archer.position());
				}
				return;
			}
			Optional<BlockPos> now = BattleStations.post(archer);
			if (now.isPresent() && !now.get().equals(station) && BattleStations.onStation(archer)) {
				helper.assertTrue(BattleStations.standable(level, now.get()), "his new station can't be stood on: " + now);
				helper.succeed();
			}
		});
	}

	/**
	 * Knights and medics. With a Palisade Gate as the breach, the knight's place is 3 blocks inside the gate and the
	 * medic's 6, behind him; the plain guard has none (he rallies as before); both walk there. With the {@code sieges}
	 * switch off the same raid is a plain one and nobody has a place.
	 */
	//$ gametest_ticks_batch AREA '700' '"stationKnights"'
	@GameTest(template = AREA, timeoutTicks = 700, batch = "stationKnights")
	public void knightsHoldTheGateAndMedicsStandBehind(GameTestHelper helper) {
		Village village = village(helper, true);
		ServerLevel level = village.level();
		Villager knight = guard(helper, new ItemStack(Items.SHIELD), 12, 21);
		Villager medic = guard(helper, PotionContents.createItemStack(Items.POTION, Potions.HEALING), 14, 21);
		Villager plain = guard(helper, ItemStack.EMPTY, 10, 21);
		Mob foe = raider(helper, 16.5, 29.5);
		Sieges.ENABLED = false;
		VillageRaids.track(level, village.hall(), village.culture(), List.of(foe));
		helper.assertTrue(Sieges.siege(level, village.hall()).isEmpty(), "a siege with the switch off");
		helper.runAtTickTime(45, () -> {
			helper.assertTrue(!BattleStations.stationed(knight) && !BattleStations.stationed(medic), "places taken with the sieges switch off");
			VillageRaids.forget(village.hall());
			new WorkplaceConfig().apply();
			helper.assertTrue(Sieges.ENABLED, "sieges should be on in tests");
			VillageRaids.track(level, village.hall(), village.culture(), List.of(foe));
			ThreatData.Siege siege = village.siege(helper);
			BlockPos breach = siege.breach;
			helper.assertTrue(breach != null && breach.equals(helper.absolutePos(new BlockPos(16, 2, 26))), "the breach is the middle gate: " + breach);
			helper.assertTrue(siege.came == 1 && siege.hpFactor == 1 && siege.gates.stream().allMatch(g -> g.hp == Sieges.FENCE_GATE_HP), "came " + siege.came + ", factor " + siege.hpFactor);
		});
		helper.onEachTick(() -> {
			if (helper.getTick() < 90) {
				return;
			}
			BlockPos breach = village.siege(helper).breach;
			Optional<BlockPos> k = BattleStations.post(knight);
			Optional<BlockPos> m = BattleStations.post(medic);
			helper.assertTrue(k.isPresent() && m.isPresent(), "the knight's place " + k + ", the medic's " + m);
			helper.assertTrue(k.get().getX() == breach.getX() && k.get().getZ() == breach.getZ() - BattleStations.KNIGHT_BACK, "the knight's place " + k + ", the breach " + breach);
			helper.assertTrue(m.get().getX() == breach.getX() && m.get().getZ() == breach.getZ() - BattleStations.MEDIC_BACK, "the medic's place " + m + ", the breach " + breach);
			helper.assertTrue(!BattleStations.stationed(plain) && !BattleStations.onStation(knight), "the plain guard has a place, or the knight's counts as a station");
			if (k.get().closerToCenterThan(knight.position(), 1.6) && m.get().closerToCenterThan(medic.position(), 1.6)) {
				helper.succeed();
			}
		});
	}

	/**
	 * The morning after. Two guards and a player kill raiders of a siege (one guard twice); the game is saved and loaded
	 * in mid-siege; the raid is fought off and dawn comes. The chronicle has a SIEGE entry that says how many came and
	 * fell and to whom, that the gate held, and names the guard with two kills as the hero; the player is a Hero of the
	 * Village for a day; a villager is 10 happier with "we held"; the builders see to the defences first for two days.
	 */
	//$ gametest_ticks_batch AREA '300' '"stationReport"'
	@GameTest(template = AREA, timeoutTicks = 300, batch = "stationReport")
	public void theMorningsReportNamesTheHero(GameTestHelper helper) {
		Village village = village(helper, true);
		ServerLevel level = village.level();
		Villager hero = guard(helper, new ItemStack(Items.SHIELD), 12, 21);
		Villager other = guard(helper, new ItemStack(Items.SHIELD), 14, 21);
		hero.setNoAi(true);
		other.setNoAi(true);
		hero.setCustomName(net.minecraft.network.chat.Component.literal("Brannoc"));
		other.setCustomName(net.minecraft.network.chat.Component.literal("Wilmot"));
		Villager farmer = helper.spawn(EntityType.VILLAGER, new BlockPos(9, 2, 21));
		farmer.setNoAi(true);
		ServerPlayer player = helper.makeMockServerPlayerInLevel();
		Leftovers.after(helper, () -> level.getServer().getPlayerList().remove(player));
		List<Mob> raiders = List.of(raider(helper, 14.5, 29.5), raider(helper, 15.5, 29.5), raider(helper, 16.5, 29.5), raider(helper, 17.5, 29.5), raider(helper, 18.5, 29.5));
		helper.assertTrue(!SiegeReport.defencesFirst(level, helper.absolutePos(POST)), "defences first before any siege");
		VillageRaids.track(level, village.hall(), village.culture(), raiders);
		helper.assertTrue(village.siege(helper).came == 5, "came " + village.siege(helper).came);
		helper.assertTrue(SiegeReport.defencesFirst(level, helper.absolutePos(POST)), "defences aren't first during the siege");
		raiders.get(0).hurt(level.damageSources().mobAttack(other), 1000f);
		raiders.get(1).hurt(level.damageSources().mobAttack(hero), 1000f);
		raiders.get(2).hurt(level.damageSources().mobAttack(hero), 1000f);
		raiders.get(3).hurt(level.damageSources().playerAttack(player), 1000f);
		helper.runAtTickTime(30, () -> {
			ThreatData.Siege siege = village.siege(helper);
			helper.assertTrue(siege.toGuards == 3 && siege.toPlayers == 1 && siege.toOthers == 0, "fell to guards " + siege.toGuards + ", players " + siege.toPlayers + ", others " + siege.toOthers);
			ThreatData.get(level).roundTrip(level.registryAccess());
			Sieges.forget();
			BattleStations.forget();
			ThreatData.Siege loaded = village.siege(helper);
			helper.assertTrue(loaded != siege && loaded.came == 5 && loaded.toGuards == 3 && loaded.toPlayers == 1 && loaded.kills.equals(siege.kills)
				&& loaded.names.equals(siege.names) && loaded.players.equals(siege.players) && loaded.hpFactor == 1, "the siege's count after a load: " + loaded.kills);
			helper.assertTrue(hero.getUUID().equals(SiegeReport.hero(loaded)), "the hero after the load: " + SiegeReport.hero(loaded));
			// The last raider leaves (not a kill); the hall's round sees the raid is over; dawn comes.
			raiders.get(4).discard();
			VillageRaids.tick(level, village.hall(), 0, 0, -100, day -> { });
			helper.assertTrue(VillageRaids.active(village.hall()).isEmpty() && loaded.over && !loaded.fled, "the raid isn't over and won");
			helper.assertTrue(village.entity().chronicle().stream().noneMatch(e -> e.kind() == Chronicle.Kind.SIEGE), "the report came before the morning");
			level.setDayTime(loaded.dawn + 20);
		});
		helper.runAtTickTime(80, () -> {
			helper.assertTrue(Sieges.siege(level, village.hall()).isEmpty(), "the siege wasn't lifted at dawn");
			List<String> reports = village.entity().chronicle().stream().filter(e -> e.kind() == Chronicle.Kind.SIEGE).map(e -> e.text().getString()).toList();
			helper.assertTrue(reports.size() == 1, "siege reports in the chronicle: " + reports);
			String report = reports.get(0);
			helper.assertTrue(report.equals("The siege: 5 raiders came and 4 fell, 3 to the guards and 1 to players. The gate held. Hero of the night: Brannoc, with 2 kills."),
				"the report: " + report);
			MobEffectInstance effect = player.getEffect(MobEffects.HERO_OF_THE_VILLAGE);
			helper.assertTrue(effect != null && effect.getDuration() > SiegeReport.HERO_TICKS - 200, "the player who fought isn't a Hero of the Village for a day: " + effect);
			Moods.Mood mood = Moods.work(level, farmer);
			helper.assertTrue(mood.good().stream().anyMatch(c -> c.getString().equals("we held against the siege")), "no \"we held\" among " + mood.good());
			ThreatData data = ThreatData.get(level);
			long now = level.getGameTime();
			helper.assertTrue(data.heldUntil(village.hall()) > now + 23000 && data.heldUntil(village.hall()) <= now + SiegeReport.HELD_TICKS, "glad until " + (data.heldUntil(village.hall()) - now));
			helper.assertTrue(data.mendingUntil(village.hall()) > now + 47000 && SiegeReport.defencesFirst(level, helper.absolutePos(POST)), "defences first until " + (data.mendingUntil(village.hall()) - now));
			data.roundTrip(level.registryAccess());
			helper.assertTrue(data.heldUntil(village.hall()) > now + 23000 && data.mendingUntil(village.hall()) > now + 47000, "the morning after was lost in a load");
			// The same villager at the same moment without the day of gladness: 10 less, and the reason gone.
			data.after(village.hall(), 0, data.mendingUntil(village.hall()));
			Moods.Mood without = Moods.work(level, farmer);
			helper.assertTrue(mood.score() < 100 && mood.score() - without.score() == SiegeReport.HELD_MOOD, "mood " + mood.score() + " with \"we held\", " + without.score() + " without");
			helper.assertTrue(without.good().stream().noneMatch(c -> c.getString().equals("we held against the siege")), "\"we held\" after its day");
			helper.succeed();
		});
	}

	/**
	 * Ramparts, and a save from before 32.6. Without the research a Palisade Gate's fence gates have 60 hit points; with
	 * it (it needs Fortification I, has one level) 120, and the factor is in the save. A siege saved without the new
	 * fields loads with one for the factor, nobody's kills and no morning after.
	 */
	//$ gametest_ticks_batch AREA '200' '"stationRamparts"'
	@GameTest(template = AREA, timeoutTicks = 200, batch = "stationRamparts")
	public void rampartsDoublesAGatesHitPoints(GameTestHelper helper) {
		Village village = village(helper, true);
		ServerLevel level = village.level();
		Mob foe = raider(helper, 16.5, 29.5);
		helper.assertTrue(Research.Topic.RAMPARTS.maxLevel == 1 && Research.Topic.RAMPARTS.needs().equals(Map.of(Research.Topic.FORTIFICATION, 1)),
			"Ramparts: " + Research.Topic.RAMPARTS.maxLevel + " level, needs " + Research.Topic.RAMPARTS.needs());
		helper.assertTrue(!village.entity().research().available(Research.Topic.RAMPARTS), "Ramparts on offer without Fortification");
		VillageRaids.track(level, village.hall(), village.culture(), List.of(foe));
		ThreatData.Siege plain = village.siege(helper);
		helper.assertTrue(plain.gates.size() == 3 && plain.gates.stream().allMatch(g -> g.hp == Sieges.FENCE_GATE_HP), "without Ramparts: " + plain.gates.get(0).hp);
		Sieges.lift(level, plain);
		VillageRaids.forget(village.hall());
		village.entity().setResearch(new Research.State(Map.of("drill", 1, "fortification", 1), Optional.empty(), 0, false));
		helper.assertTrue(village.entity().research().available(Research.Topic.RAMPARTS), "Ramparts isn't on offer after Fortification I");
		village.entity().setResearch(new Research.State(Map.of("drill", 1, "fortification", 1, "ramparts", 1), Optional.empty(), 0, false));
		VillageRaids.track(level, village.hall(), village.culture(), List.of(foe));
		ThreatData.Siege strong = village.siege(helper);
		BlockPos gate = strong.breach;
		helper.assertTrue(strong.hpFactor == 2 && strong.gates.size() == 3 && strong.gates.stream().allMatch(g -> g.hp == 2 * Sieges.FENCE_GATE_HP),
			"with Ramparts: factor " + strong.hpFactor + ", hit points " + strong.gates.get(0).hp);
		helper.assertTrue(Sieges.hitPointsLeft(level, village.hall(), gate) == 120, "the breach gate has " + Sieges.hitPointsLeft(level, village.hall(), gate));
		ThreatData data = ThreatData.get(level);
		CompoundTag saved = data.save(new CompoundTag(), level.registryAccess());
		data.roundTrip(level.registryAccess());
		helper.assertTrue(village.siege(helper).hpFactor == 2 && Sieges.hitPointsLeft(level, village.hall(), gate) == 120, "the factor after a load: " + village.siege(helper).hpFactor);
		// A save from before 32.6: the same, without the fields it added.
		CompoundTag old = saved.copy();
		old.remove("after");
		ListTag sieges = old.getList("sieges", Tag.TAG_COMPOUND);
		for (int i = 0; i < sieges.size(); i++) {
			for (String key : List.of("hp_factor", "came", "fled", "to_guards", "to_players", "to_others", "kills", "players")) {
				sieges.getCompound(i).remove(key);
			}
		}
		data.read(old);
		ThreatData.Siege from = village.siege(helper);
		helper.assertTrue(from.hpFactor == 1 && from.came == 0 && !from.fled && from.kills.isEmpty() && from.names.isEmpty() && from.players.isEmpty()
			&& from.toGuards == 0 && from.toPlayers == 0 && from.toOthers == 0 && from.gates.size() == 3, "an old save's siege: factor " + from.hpFactor);
		helper.assertTrue(data.heldUntil(village.hall()) == 0 && data.mendingUntil(village.hall()) == 0 && SiegeReport.hero(from) == null, "an old save has a morning after");
		helper.assertTrue(SiegeReport.text(level, from).getString().contains("No guard made a kill."), "an old save's report: " + SiegeReport.text(level, from).getString());
		data.read(saved);
		helper.succeed();
	}

	/**
	 * Defences first. A house wall (recorded first, so the first the builder would find) and the Palisade Gate both miss
	 * a block after a siege that broke the gate. The morning after, the builder with the materials in the chest takes
	 * the gate first: the fence gate is back while the house's plank is still missing, and then the house is mended too.
	 */
	//$ gametest_ticks_batch AREA '3600' '"stationMending"'
	@GameTest(template = AREA, timeoutTicks = 3600, batch = "stationMending")
	public void buildersMendTheGateBeforeAHouse(GameTestHelper helper) {
		Village village = village(helper, false);
		ServerLevel level = village.level();
		boolean was = Upkeep.ENABLED;
		Upkeep.ENABLED = true;
		Leftovers.after(helper, () -> Upkeep.ENABLED = was);
		BlockPos bench = new BlockPos(20, 2, 20);
		BlockPos chestAt = new BlockPos(20, 2, 22);
		helper.setBlock(bench, ModBlocks.BUILDERS_BENCH);
		helper.setBlock(chestAt, Blocks.CHEST);
		net.minecraft.world.Container chest = helper.getBlockEntity(chestAt);
		chest.setItem(0, new ItemStack(Items.OAK_PLANKS, 4));
		chest.setItem(1, new ItemStack(Items.SPRUCE_FENCE_GATE, 4));
		Villager builder = helper.spawn(EntityType.VILLAGER, new BlockPos(21, 2, 21));
		builder.setNoAi(true); // (he looks only when the test lets him)
		Builders.employ(level, builder, helper.absolutePos(bench));
		BuildSiteManager sites = BuildSiteManager.get(level);
		// The house: a wall of planks, recorded before the gate.
		BlockPos src = new BlockPos(22, 2, 16);
		for (int x = 0; x < 4; x++) {
			helper.setBlock(src.offset(x, 0, 0), Blocks.OAK_PLANKS);
		}
		ResourceLocation house = ResourceLocation.fromNamespaceAndPath("aliveworkplace_test", "station_house_" + Long.toHexString(level.getGameTime()));
		level.getStructureManager().getOrCreate(house).fillFromWorld(level, helper.absolutePos(src), new net.minecraft.core.Vec3i(4, 1, 1), false, Blocks.STRUCTURE_VOID);
		BlueprintData.Placement housePlace = new BlueprintData.Placement(level.dimension().location(), helper.absolutePos(src), Rotation.NONE, Mirror.NONE);
		sites.recordFinished(house, housePlace, builder.getUUID());
		Leftovers.after(helper, () -> sites.forgetFinished(housePlace));
		BlockPos gateOrigin = helper.absolutePos(GATE);
		level.getStructureManager().get(StarterBlueprints.PALISADE_GATE.id()).orElseThrow().placeInWorld(level, gateOrigin, gateOrigin, new StructurePlaceSettings(), RandomSource.create(1L), 2);
		BlueprintData.Placement gatePlace = new BlueprintData.Placement(level.dimension().location(), gateOrigin, Rotation.NONE, Mirror.NONE);
		sites.recordFinished(StarterBlueprints.PALISADE_GATE.id(), gatePlace, builder.getUUID());
		Leftovers.after(helper, () -> sites.forgetFinished(gatePlace));
		List<BuildSiteManager.Finished> near = sites.finishedNear(level, helper.absolutePos(bench), Builders.MAX_SITE_DISTANCE).stream()
			.filter(f -> f.structure().equals(house) || f.structure().equals(StarterBlueprints.PALISADE_GATE.id())).toList();
		helper.assertTrue(near.size() == 2 && near.get(0).structure().equals(house), "the house should be the first the builder finds: " + near);
		// The siege: the gate's middle block goes (as a ram leaves it), a plank of the house too; the raid ends; dawn.
		Mob foe = raider(helper, 16.5, 29.5);
		VillageRaids.track(level, village.hall(), village.culture(), List.of(foe));
		ThreatData.Siege siege = village.siege(helper);
		BlockPos gate = siege.breach;
		helper.assertTrue(gate != null && level.getBlockState(gate).is(Blocks.SPRUCE_FENCE_GATE), "the breach: " + gate);
		BlockPos plank = helper.absolutePos(src.offset(1, 0, 0));
		level.setBlock(gate, Blocks.AIR.defaultBlockState(), 3);
		ThreatData.get(level).gateBroken(village.hall(), gate);
		level.setBlock(plank, Blocks.AIR.defaultBlockState(), 3);
		foe.discard();
		VillageRaids.tick(level, village.hall(), 0, 0, -100, day -> { });
		level.setDayTime(siege.dawn + 20);
		boolean[] looked = {false, false};
		helper.onEachTick(() -> {
			if (helper.getTick() < 45) {
				return;
			}
			if (!looked[0]) {
				looked[0] = true;
				helper.assertTrue(Sieges.siege(level, village.hall()).isEmpty() && ThreatData.get(level).mendingUntil(village.hall()) > level.getGameTime(), "no morning after");
				level.setDayTime(level.getDayTime() - level.getDayTime() % 24000 + 24000 + 2500); // the working day
				BuildSite site = Upkeep.look(level, builder);
				helper.assertTrue(site != null && site.structure().equals(StarterBlueprints.PALISADE_GATE.id()), "the builder's first repair is " + (site == null ? "nothing" : site.structure()));
				builder.setNoAi(false);
				return;
			}
			if (!looked[1]) {
				if (level.getBlockState(gate).is(Blocks.SPRUCE_FENCE_GATE)) {
					looked[1] = true;
					helper.assertTrue(level.getBlockState(plank).isAir(), "the house was mended before the gate");
				}
				return;
			}
			if (level.getBlockState(plank).is(Blocks.OAK_PLANKS)) {
				helper.succeed();
			}
		});
	}

	/** Every sentence 32.6 added has its text. */
	//$ gametest_batch 'FabricGameTest.EMPTY_STRUCTURE' '"stationText"'
	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE, batch = "stationText")
	public void everyNewSentenceHasItsText(GameTestHelper helper) {
		for (String key : List.of("message.aliveworkplace.guard.state.station", "message.aliveworkplace.guard.state.to_station", "chronicle.aliveworkplace.siege",
			"chronicle.aliveworkplace.siege_gate_held", "chronicle.aliveworkplace.siege_gate_broken", "chronicle.aliveworkplace.siege_hero",
			"chronicle.aliveworkplace.siege_hero_one", "chronicle.aliveworkplace.siege_no_hero", "message.aliveworkplace.siege.heroes",
			"research.aliveworkplace.ramparts", "research.aliveworkplace.ramparts.effect", "mood.aliveworkplace.reason.we_held")) {
			helper.assertTrue(Language.getInstance().has(key), "no text for " + key);
		}
		helper.assertTrue(Research.Topic.RAMPARTS.title().getString().equals("Ramparts"), "the research's name: " + Research.Topic.RAMPARTS.title().getString());
		helper.assertTrue(Chronicle.Kind.SIEGE.icon == Items.IRON_BARS, "the SIEGE kind's icon");
		// Every research topic has its slot on the scholars' screen (Ramparts is the fifteenth, the first of a third row).
		helper.assertTrue(io.github.jcondedata.aliveworkplace.research.ResearchScreen.TOPIC_SLOTS.length >= Research.Topic.values().length
			&& io.github.jcondedata.aliveworkplace.research.ResearchScreen.TOPIC_SLOTS[Research.Topic.RAMPARTS.ordinal()] == 37, "no slot for Ramparts on the research screen");
		helper.succeed();
	}
}
