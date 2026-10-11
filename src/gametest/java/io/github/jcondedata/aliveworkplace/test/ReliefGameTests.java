package io.github.jcondedata.aliveworkplace.test;

import io.github.jcondedata.aliveworkplace.guard.Relief;
import io.github.jcondedata.aliveworkplace.guard.VillageRaids;
import io.github.jcondedata.aliveworkplace.hall.Chronicle;
import io.github.jcondedata.aliveworkplace.hall.VillageBanners;
import io.github.jcondedata.aliveworkplace.hall.VillageRanks;
import io.github.jcondedata.aliveworkplace.realm.RealmData;
import io.github.jcondedata.aliveworkplace.realm.Realms;
import io.github.jcondedata.aliveworkplace.registry.ModVillagers;
import io.github.jcondedata.aliveworkplace.threat.ThreatData;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponents;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ai.village.poi.PoiTypes;
import net.minecraft.world.entity.animal.horse.AbstractHorse;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.raid.Raid;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BannerPatternLayers;

/**
 * Relief in a raid (ROADMAP 33.17): a raid on a member brings the capital's band and a near member's after the 30-second
 * ride, on horseback, named for their home, with the realm's arms on their shields, paid from the treasury; they kill a
 * raider and ride off after the raid; the sender's hall line and both chronicles. A village in no realm gets no one, the
 * config switch off brings no one, an empty treasury brings only the capital's band, and a vanilla raid calls them too.
 * Each test is alone in its batch (realms are on only inside {@link RealmGameTests#land}).
 */
public class ReliefGameTests implements FabricGameTest {
	private static final String AREA = "aliveworkplace_test:huge_area";

	/** A Town capital (hall 0) and Hamlets 1 and 2 in one realm; relief's tunables put back after. */
	static RealmGameTests.Land realm(GameTestHelper helper, int extra) {
		VillageRanks.Rank[] ranks = new VillageRanks.Rank[3 + extra];
		java.util.Arrays.fill(ranks, VillageRanks.Rank.HAMLET);
		ranks[0] = VillageRanks.Rank.TOWN;
		RealmGameTests.Land land = RealmGameTests.land(helper, ranks);
		RealmGameTests.found(helper, land);
		for (int i = 1; i < 3; i++) {
			helper.assertTrue(Realms.add(land.level(), land.hall(0), land.at(i), land.owner()).ok(), "hall " + i + " didn't join");
		}
		boolean enabled = Relief.ENABLED;
		int edge = Relief.EDGE;
		long linger = Relief.LINGER;
		Relief.ENABLED = true;
		Relief.EDGE = 6;
		Relief.LINGER = 40;
		Relief.forget();
		Leftovers.after(helper, () -> {
			Relief.ENABLED = enabled;
			Relief.EDGE = edge;
			Relief.LINGER = linger;
			Relief.forget();
			ThreatData.get(land.level()).end(land.hall(1));
		});
		return land;
	}

	static RealmData.Realm theRealm(RealmGameTests.Land land) {
		return land.data().realmOf(land.at(0));
	}

	static void treasury(RealmGameTests.Land land, long cents) {
		RealmData.Realm realm = theRealm(land);
		land.data().putRealm(realm.withTreasury(cents, realm.ledger()));
	}

	/** {@code n} guards by hall {@code i}. */
	static List<Villager> guards(RealmGameTests.Land land, int i, int n) {
		List<Villager> guards = new ArrayList<>();
		for (int k = 0; k < n; k++) {
			Villager guard = EntityType.VILLAGER.create(land.level());
			BlockPos at = land.hall(i).offset(k % 3 - 1, 0, 2 + k / 3);
			guard.moveTo(at.getX() + 0.5, at.getY(), at.getZ() + 0.5, 0f, 0f);
			guard.setVillagerData(guard.getVillagerData().setProfession(ModVillagers.GUARD).setLevel(1));
			guard.setVillagerXp(10);
			land.level().addFreshEntity(guard);
			guards.add(guard);
		}
		return guards;
	}

	static boolean chronicleSays(RealmGameTests.Land land, int i, String text) {
		return land.entity(i).chronicle().stream().map(Chronicle.Entry::text).anyMatch(t -> t.getString().equals(text));
	}

	/**
	 * Hamlet 1 is raided: the watch calls; the capital (no guards: one rider) and hamlet 2 (6 guards: two) send bands that
	 * arrive exactly 600 ticks later, paid 3 emeralds from the treasury; they arrive mounted at the edge, named and with the
	 * realm's arms; they kill a zombie; when the raid is over they ride off with their horses; the halls and chronicles say so.
	 */
	//$ gametest_ticks_batch AREA '1400' '"reliefRides"'
	@GameTest(template = AREA, timeoutTicks = 1400, batch = "reliefRides")
	public void aRaidBringsTheRealmsRiders(GameTestHelper helper) {
		RealmGameTests.Land land = realm(helper, 0);
		ServerLevel level = land.level();
		helper.setDayTime(18000);
		treasury(land, 500);
		RealmData.Realm armed = theRealm(land).withArms(Optional.of(new VillageBanners.Colours(DyeColor.RED, BannerPatternLayers.EMPTY)));
		land.data().putRealm(armed);
		List<Villager> homeGuards = guards(land, 2, 6);
		homeGuards.forEach(g -> g.setNoAi(true)); // they stay home; only the riders fight
		// the raider: a zombie standing by hamlet 1 (the raid lasts while it lives)
		Zombie raider = EntityType.ZOMBIE.create(level);
		BlockPos at = land.hall(1).relative(Direction.EAST, 3);
		raider.moveTo(at.getX() + 0.5, at.getY(), at.getZ() + 0.5, 0f, 0f);
		raider.addTag(VillageRaids.TAG);
		raider.setNoAi(true);
		raider.setPersistenceRequired();
		level.addFreshEntity(raider);
		VillageRaids.track(level, land.hall(1), 1);
		long[] arrive = {-1};
		long[] sent = {-1};
		int[] phase = {0};
		Zombie[] zombie = {raider};
		helper.runAfterDelay(1390, () -> {
			if (phase[0] < 4) {
				helper.fail("stuck at step " + phase[0] + ": arrive " + arrive[0] + ", now " + level.getGameTime() + ", riders "
					+ Relief.riders(level, land.hall(1)).stream().map(r -> r.blockPosition() + " " + r.getVehicle()).toList()
					+ ", zombie " + (zombie[0] == null ? "none" : zombie[0].isAlive() + " " + zombie[0].getHealth() + " at " + zombie[0].blockPosition())
					+ ", raided " + VillageRaids.raided(level, land.hall(1), land.hall(1)));
			}
		});
		helper.onEachTick(() -> {
			long now = level.getGameTime();
			List<Villager> riders = Relief.riders(level, land.hall(1));
			switch (phase[0]) {
				case 0 -> {
					List<Relief.Band> bands = Relief.onTheRoad(level, land.hall(1));
					if (bands.isEmpty()) {
						return;
					}
					helper.assertTrue(bands.size() == 2, "bands on the road: " + bands);
					Relief.Band capital = bands.get(0);
					Relief.Band near = bands.get(1);
					helper.assertTrue(capital.capital() && capital.riders() == 1 && capital.home().equals(land.at(0)), "the capital's band: " + capital);
					helper.assertTrue(!near.capital() && near.riders() == 2 && near.home().equals(land.at(2)), "hamlet 2's band: " + near);
					helper.assertTrue(capital.arrive() - capital.sent() == Relief.MIN_RIDE && near.arrive() - near.sent() == Relief.MIN_RIDE,
						"not a 30-second ride: " + bands);
					helper.assertTrue(theRealm(land).treasury() == 200, "3 riders didn't cost 3 emeralds: " + theRealm(land).treasury());
					String line = Relief.hallLine(level, land.hall(2)) == null ? "" : Relief.hallLine(level, land.hall(2)).getString();
					helper.assertTrue(line.equals("2 guards away helping " + land.name(1)), "hamlet 2's hall line: " + line);
					helper.assertTrue(chronicleSays(land, 2, "2 guards rode to help " + land.name(1) + " in a raid"), "hamlet 2's chronicle: " + land.entity(2).chronicle());
					arrive[0] = near.arrive();
					sent[0] = near.sent();
					phase[0] = 1;
				}
				case 1 -> {
					if (riders.isEmpty()) {
						helper.assertTrue(now < arrive[0] + 1, "no riders by their arrival at " + arrive[0] + " (now " + now + ")");
						return;
					}
					helper.assertTrue(now >= arrive[0], "riders came early: " + now + " < " + arrive[0]);
					helper.assertTrue(riders.size() == 3, "riders: " + riders.size());
					long named = riders.stream().filter(r -> r.getCustomName() != null && r.getCustomName().getString().equals(land.name(2) + "'s guard")).count();
					helper.assertTrue(named == 2, "riders named for hamlet 2: " + named + " of " + riders.stream().map(r -> String.valueOf(r.getCustomName())).toList());
					for (Villager r : riders) {
						helper.assertTrue(r.getVehicle() instanceof AbstractHorse horse && horse.isSaddled(), "a rider not on horseback: " + r);
						helper.assertTrue(r.getItemBySlot(EquipmentSlot.OFFHAND).get(DataComponents.BASE_COLOR) == DyeColor.RED, "no arms on the shield: " + r.getItemBySlot(EquipmentSlot.OFFHAND));
						helper.assertTrue(r.distanceToSqr(net.minecraft.world.phys.Vec3.atCenterOf(land.hall(1))) < 12 * 12, "a rider far from the village: " + r.blockPosition());
					}
					// saved and loaded (a restart): still a rider for hamlet 1, from home, with their stay
					Villager copy = EntityType.VILLAGER.create(level);
					copy.load(riders.get(0).saveWithoutId(new net.minecraft.nbt.CompoundTag()));
					helper.assertTrue(Relief.isRider(copy) && io.github.jcondedata.aliveworkplace.registry.ModAttachments.RELIEF_RIDER.get(copy).target().equals(land.hall(1))
						&& io.github.jcondedata.aliveworkplace.registry.ModAttachments.MERCENARY_UNTIL.get(copy) != null, "a rider's errand isn't saved");
					copy.discard();
					// hamlet 2's riders came from the east, the capital's from the west
					helper.assertTrue(riders.stream().filter(r -> r.getX() > land.hall(1).getX()).count() == 2, "hamlet 2's riders not on its side");
					phase[0] = 2;
				}
				case 2 -> {
					if (zombie[0].isAlive()) {
						return;
					}
					helper.assertTrue(zombie[0].getLastHurtByMob() instanceof Villager v && Relief.isRider(v), "the zombie didn't die to a rider: " + zombie[0].getLastHurtByMob());
					ThreatData.get(level).end(land.hall(1)); // the raid is over
					phase[0] = 3;
				}
				case 3 -> {
					if (!riders.isEmpty()) {
						return;
					}
					helper.assertTrue(level.getEntitiesOfClass(AbstractHorse.class, new net.minecraft.world.phys.AABB(land.hall(1)).inflate(32),
						h -> h.getTags().contains(Relief.HORSE_TAG)).isEmpty(), "their horses stayed");
					helper.assertTrue(chronicleSays(land, 1, "2 guards from " + land.name(2) + " rode in to help against the raid"), "hamlet 1's chronicle: " + land.entity(1).chronicle());
					helper.assertTrue(Relief.hallLine(level, land.hall(2)) == null, "still away: " + Relief.hallLine(level, land.hall(2)));
					phase[0] = 4;
					helper.succeed();
				}
				default -> {
				}
			}
		});
	}

	/**
	 * A village in no realm gets no one; with {@code realmRelief} off no one comes; with the treasury empty only the
	 * capital's band comes (unpaid); a second call in the same raid sends nobody more.
	 */
	//$ gametest_ticks_batch AREA '100' '"reliefNoOne"'
	@GameTest(template = AREA, timeoutTicks = 100, batch = "reliefNoOne")
	public void noRealmNoSwitchNoMoney(GameTestHelper helper) {
		RealmGameTests.Land land = realm(helper, 1);
		ServerLevel level = land.level();
		guards(land, 2, 6);
		helper.assertTrue(land.data().realmOf(land.at(3)) == null, "hall 3 is in the realm");
		helper.assertTrue(Relief.call(level, land.hall(3)) == 0 && Relief.onTheRoad(level, land.hall(3)).isEmpty(), "a village in no realm got help");
		Relief.ENABLED = false;
		helper.assertTrue(Relief.call(level, land.hall(1)) == 0 && Relief.onTheRoad(level, land.hall(1)).isEmpty(), "help came with realmRelief off");
		Relief.ENABLED = true;
		treasury(land, 0);
		int riders = Relief.call(level, land.hall(1));
		List<Relief.Band> bands = Relief.onTheRoad(level, land.hall(1));
		helper.assertTrue(riders == 1 && bands.size() == 1 && bands.get(0).capital(), "with the treasury empty: " + riders + " " + bands);
		helper.assertTrue(theRealm(land).treasury() == 0, "the treasury: " + theRealm(land).treasury());
		helper.assertTrue(Relief.call(level, land.hall(1)) == 0, "called twice in one raid");
		// the band arithmetic: 1 per 3 guards, 1..3, plus Levies
		helper.assertTrue(Relief.band(0, 0) == 1 && Relief.band(6, 0) == 2 && Relief.band(30, 0) == 3 && Relief.band(30, 1) == 4, "band sizes");
		helper.succeed();
	}

	/** A vanilla pillager raid at hamlet 1 calls the realm's riders too. */
	//$ gametest_ticks_batch AREA '200' '"reliefVanilla"'
	@GameTest(template = AREA, timeoutTicks = 200, batch = "reliefVanilla")
	public void aVanillaRaidCallsThemToo(GameTestHelper helper) {
		RealmGameTests.Land land = realm(helper, 0);
		ServerLevel level = land.level();
		helper.setDayTime(6000);
		treasury(land, 1000);
		BlockPos bell = land.hall(1).offset(2, 0, 2);
		level.setBlockAndUpdate(bell, Blocks.BELL.defaultBlockState());
		Raid[] raid = new Raid[1];
		Leftovers.after(helper, () -> {
			if (raid[0] != null) {
				raid[0].stop();
			}
		});
		helper.runAfterDelay(5, () -> level.getPoiManager().take(h -> h.is(PoiTypes.MEETING), (h, p) -> true, bell, 2));
		helper.runAfterDelay(30, () -> {
			ServerPlayer player = helper.makeMockServerPlayerInLevel();
			raid[0] = level.getRaids().createOrExtendRaid(player, bell);
			helper.assertTrue(raid[0] != null, "no vanilla raid");
			raid[0].setRaidOmenLevel(1);
		});
		helper.runAfterDelay(35, () -> helper.succeedWhen(() -> {
			helper.assertTrue(!Relief.onTheRoad(level, land.hall(1)).isEmpty(), "no band rode to the vanilla raid");
			raid[0].stop();
		}));
	}
}
