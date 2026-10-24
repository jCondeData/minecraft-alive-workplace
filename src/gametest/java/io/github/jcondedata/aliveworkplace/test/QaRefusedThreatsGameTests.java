package io.github.jcondedata.aliveworkplace.test;

import com.mojang.brigadier.exceptions.CommandSyntaxException;
import io.github.jcondedata.aliveworkplace.AliveWorkplace;
import io.github.jcondedata.aliveworkplace.WorkplaceConfig;
import io.github.jcondedata.aliveworkplace.guard.BanditCamps;
import io.github.jcondedata.aliveworkplace.guard.VillageRaids;
import io.github.jcondedata.aliveworkplace.hall.VillageHallBlockEntity;
import io.github.jcondedata.aliveworkplace.hall.VillageHalls;
import io.github.jcondedata.aliveworkplace.hall.VillageRanks;
import io.github.jcondedata.aliveworkplace.registry.ModBlocks;
import io.github.jcondedata.aliveworkplace.threat.Culture;
import io.github.jcondedata.aliveworkplace.threat.HostileSpawns;
import io.github.jcondedata.aliveworkplace.threat.Lairs;
import io.github.jcondedata.aliveworkplace.threat.ThreatData;
import io.github.jcondedata.aliveworkplace.threat.Threats;
import java.util.ArrayList;
import java.util.List;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.commands.CommandSource;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.monster.ZombifiedPiglin;
import net.minecraft.world.entity.monster.Zoglin;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec2;
import net.minecraft.world.phys.Vec3;

/**
 * QA (lane D, 2026-10-24): the owner's Cobbleverse server runs MobsBeGone, which discards nearly every vanilla mob at
 * {@code ServerLevel.addEntity} (B208). ROADMAP B208 expects every threat's mobs to come anyway ("the camp's pillagers
 * and the raid/night-attack mobs actually spawn and stay"), and the wiki ("Lairs") says every foe goes in through
 * {@code HostileSpawns}. B208GameTests proves it for the bandits and the plain monsters; these prove it for every
 * culture, through the operator's command (32.21) and a lair of each culture on land. The Warlord's band turning
 * zombified while refused is the bug test {@code QaRefusedWarlordGameTests} (tests/refused-warlord); its control is here.
 */
public class QaRefusedThreatsGameTests implements FabricGameTest {
	private static final String BIG = "aliveworkplace_test:big_area";
	private static final String HUGE = "aliveworkplace_test:huge_area";
	private static final BlockPos HALL = new BlockPos(1, 2, 1);
	private static final ResourceLocation PIGLINS = AliveWorkplace.id("piglin_incursion");
	private static final long DAY = 24000;

	/** A hall (village radius 16) with four villagers while the world refuses every hostile mob; all undone at the end. */
	private static BlockPos village(GameTestHelper helper, int radius) {
		Leftovers.clear(helper);
		Leftovers.halls(helper);
		ServerLevel level = helper.getLevel();
		Lairs.forget(level);
		VillageRaids.forget();
		int was = VillageHalls.RADIUS;
		long time = level.getDayTime();
		boolean lairs = Lairs.ENABLED;
		VillageHalls.RADIUS = radius;
		Lairs.ENABLED = true;
		helper.setBlock(HALL, ModBlocks.VILLAGE_HALL);
		BlockPos hall = helper.absolutePos(HALL);
		ThreatData.get(level).forgetPast(hall);
		ThreatData.get(level).forgetClock(hall);
		HostileSpawns.forget();
		HostileSpawns.FORCE = true;
		HostileSpawns.REFUSED_FOR_TESTS = e -> e instanceof Enemy;
		Leftovers.after(helper, () -> {
			HostileSpawns.REFUSED_FOR_TESTS = null;
			HostileSpawns.FORCE = true;
			HostileSpawns.forget();
			level.getEntitiesOfClass(Mob.class, helper.getBounds().inflate(160), m -> !(m instanceof Villager)
				&& (m instanceof Enemy || m.getTags().contains(Lairs.TAG) || m.getTags().contains(VillageRaids.TAG))).forEach(Mob::discard);
			ThreatData.get(level).end(hall);
			Lairs.forget(level);
			VillageRaids.forget();
			BanditCamps.forget(level);
			ThreatData.get(level).forgetPast(hall);
			ThreatData.get(level).forgetClock(hall);
			ThreatData.get(level).forgetSiege(hall);
			ThreatData.get(level).forgetPlunder(hall);
			VillageHalls.RADIUS = was;
			Lairs.ENABLED = lairs;
			level.setDayTime(time);
			new WorkplaceConfig().apply();
		});
		for (int i = 0; i < 4; i++) {
			helper.spawn(EntityType.VILLAGER, new BlockPos(2 + i, 2, 3));
		}
		level.setDayTime(44 * DAY + 15000);
		return hall;
	}

	private static boolean in(ServerLevel level, Entity e) {
		return e != null && e.isAlive() && !e.isRemoved() && level.getEntity(e.getUUID()) == e;
	}

	private static String describe(List<? extends Entity> mobs) {
		StringBuilder s = new StringBuilder("[");
		for (Entity m : mobs) {
			s.append(EntityType.getKey(m.getType()).getPath()).append(m.isRemoved() ? " removed=" + m.getRemovalReason() : " in").append("; ");
		}
		return s.append(']').toString();
	}

	private static CommandSourceStack op(ServerLevel level, BlockPos at, List<String> said) {
		CommandSource out = new CommandSource() {
			@Override
			public void sendSystemMessage(Component message) {
				said.add(message.getString());
			}

			@Override
			public boolean acceptsSuccess() {
				return true;
			}

			@Override
			public boolean acceptsFailure() {
				return true;
			}

			@Override
			public boolean shouldInformAdmins() {
				return false;
			}
		};
		return new CommandSourceStack(out, Vec3.atCenterOf(at.south(2)), Vec2.ZERO, level, 2, "test", Component.literal("test"), level.getServer(), null);
	}

	private static int run(ServerLevel level, CommandSourceStack source, String command) {
		try {
			return level.getServer().getCommands().getDispatcher().execute(command, source);
		} catch (CommandSyntaxException e) {
			throw new IllegalStateException(command + ": " + e.getMessage());
		}
	}

	/**
	 * {@code /workplace threat start <culture>} for each of our seven cultures, while the world refuses every hostile mob
	 * as MobsBeGone does: the raid the command reports is really there (each raider in the world and persistent), and
	 * still there 100 ticks later; {@code end} takes them away.
	 */
	//$ gametest_ticks_batch BIG '200' '"qaRefusedRaids"'
	@GameTest(template = BIG, timeoutTicks = 200, batch = "qaRefusedRaids")
	public void everyCulturesRaidComesWhenAnotherModRefusesItsMobs(GameTestHelper helper) {
		BlockPos hall = village(helper, 16);
		ServerLevel level = helper.getLevel();
		List<String> said = new ArrayList<>();
		helper.runAfterDelay(2, () -> {
			// The refusal is real.
			Mob plain = EntityType.WITCH.create(level);
			plain.moveTo(helper.absoluteVec(new Vec3(8, 2, 8)));
			helper.assertTrue(!level.addFreshEntity(plain) && plain.isRemoved(), "the world didn't refuse a witch as MobsBeGone does");
			CommandSourceStack op = op(level, hall, said);
			List<ResourceLocation> ours = Threats.all().stream().map(Culture::id).filter(id -> id.getNamespace().equals(AliveWorkplace.MOD_ID)).sorted().toList();
			helper.assertTrue(ours.size() == 7, "expected our 7 cultures: " + ours);
			List<String> failures = new ArrayList<>();
			List<List<Mob>> kept = new ArrayList<>();
			for (ResourceLocation id : ours) {
				said.clear();
				int sent = run(level, op, "workplace threat start " + id);
				List<Mob> raiders = VillageRaids.raiders(level, hall);
				long inWorld = raiders.stream().filter(m -> in(level, m) && m.isPersistenceRequired()).count();
				if (sent <= 0 || raiders.isEmpty() || inWorld != raiders.size() || inWorld < sent) {
					failures.add(id + ": the command said " + sent + " (" + said + "), in the world " + inWorld + " " + describe(raiders)
						+ ", last warning: " + HostileSpawns.lastWarning());
				}
				kept.add(List.copyOf(raiders));
				run(level, op, "workplace threat end");
				if (!VillageRaids.raiders(level, hall).isEmpty() || ThreatData.get(level).raid(hall).isPresent()) {
					failures.add(id + ": end left the raid or its raiders");
				}
			}
			helper.assertTrue(failures.isEmpty(), "raids refused by another mod: " + failures);
			helper.succeed();
		});
	}

	/**
	 * A lair made by each culture with one on land, while the world refuses every hostile mob: its captain and band are
	 * in the world, persistent, still there 100 ticks later, and it isn't broken up at the next round.
	 */
	private static void lairStands(GameTestHelper helper, ResourceLocation culture) {
		BlockPos hall = village(helper, 20);
		ServerLevel level = helper.getLevel();
		((VillageHallBlockEntity) level.getBlockEntity(hall)).setRank(VillageRanks.Rank.TOWN);
		helper.runAfterDelay(2, () -> {
			level.setDayTime(44 * DAY + 2000);
			Lairs.Lair lair = Lairs.found(level, hall, helper.absolutePos(new BlockPos(15, 1, 15)), culture);
			helper.assertTrue(lair != null && lair.culture().equals(culture), culture + ": no lair while refused; last warning: " + HostileSpawns.lastWarning());
			Entity captain = level.getEntity(lair.captain());
			helper.assertTrue(captain instanceof Mob c && in(level, c) && c.isPersistenceRequired(), culture + ": the captain isn't in the world: " + captain);
			List<Mob> band = new ArrayList<>(Lairs.band(level, lair));
			band.removeIf(m -> m.getUUID().equals(lair.captain()));
			helper.assertTrue(!band.isEmpty() && band.stream().allMatch(m -> in(level, m) && m.isPersistenceRequired()), culture + ": the band " + describe(band));
			helper.runAfterDelay(100, () -> {
				Lairs.round(level, hall, false);
				helper.assertTrue(Lairs.near(level, hall).isPresent(), culture + ": the lair was broken up with its captain alive");
				helper.assertTrue(in(level, level.getEntity(lair.captain())), culture + ": the captain is gone after 100 ticks");
				helper.assertTrue(band.stream().allMatch(m -> in(level, m)), culture + ": the band is gone after 100 ticks: " + describe(band));
				helper.succeed();
			});
		});
	}

	//$ gametest_ticks_batch HUGE '200' '"qaRefusedWarCamp"'
	@GameTest(template = HUGE, timeoutTicks = 200, batch = "qaRefusedWarCamp")
	public void aWarbandsWarCampStandsWhenAnotherModRefusesItsMobs(GameTestHelper helper) {
		lairStands(helper, AliveWorkplace.id("pillager_warband"));
	}

	//$ gametest_ticks_batch HUGE '200' '"qaRefusedTombCamp"'
	@GameTest(template = HUGE, timeoutTicks = 200, batch = "qaRefusedTombCamp")
	public void aDesertTombCampStandsWhenAnotherModRefusesItsMobs(GameTestHelper helper) {
		lairStands(helper, AliveWorkplace.id("desert_raiders"));
	}

	//$ gametest_ticks_batch HUGE '200' '"qaRefusedOutpost"'
	@GameTest(template = HUGE, timeoutTicks = 200, batch = "qaRefusedOutpost")
	public void aPiglinOutpostStandsWhenAnotherModRefusesItsMobs(GameTestHelper helper) {
		lairStands(helper, PIGLINS);
	}

	//$ gametest_ticks_batch HUGE '200' '"qaRefusedCovenHut"'
	@GameTest(template = HUGE, timeoutTicks = 200, batch = "qaRefusedCovenHut")
	public void aCovenHutStandsWhenAnotherModRefusesItsMobs(GameTestHelper helper) {
		lairStands(helper, AliveWorkplace.id("witch_coven"));
	}

	/**
	 * 32.10: when the Warlord falls, his band (at the outpost and out raiding) turns zombified and stays. With nothing
	 * refused, every piglin and hoglin of the band and the raid counted is zombified and still there. (The same while the
	 * world refuses zombified piglins and zoglins, as Cobbleverse's MobsBeGone does, is the bug test QaRefusedWarlordGameTests.)
	 */
	//$ gametest_ticks_batch HUGE '60' '"qaWarlordControl"'
	@GameTest(template = HUGE, timeoutTicks = 60, batch = "qaWarlordControl")
	public void aWarlordsWholeBandAndRaidTurnZombifiedWhenHeFalls(GameTestHelper helper) {
		warlordFalls(helper, false);
	}

	static void warlordFalls(GameTestHelper helper, boolean refuse) {
		BlockPos hall = village(helper, 20);
		if (!refuse) {
			HostileSpawns.REFUSED_FOR_TESTS = null;
		}
		ServerLevel level = helper.getLevel();
		((VillageHallBlockEntity) level.getBlockEntity(hall)).setRank(VillageRanks.Rank.TOWN);
		helper.runAfterDelay(2, () -> {
			level.setDayTime(44 * DAY + 15000);
			Lairs.Lair lair = Lairs.found(level, hall, helper.absolutePos(new BlockPos(15, 1, 15)), PIGLINS);
			helper.assertTrue(lair != null && lair.culture().equals(PIGLINS), "no outpost: " + lair);
			Mob warlord = (Mob) level.getEntity(lair.captain());
			helper.assertTrue(warlord != null && in(level, warlord), "the Warlord isn't in the world: " + warlord);
			List<Mob> band = new ArrayList<>(Lairs.band(level, lair));
			band.removeIf(m -> m.getUUID().equals(lair.captain()));
			helper.assertTrue(band.size() >= 5 && band.stream().allMatch(m -> in(level, m)), "the band: " + describe(band));
			VillageRaids.Raid raid = VillageRaids.start(level, hall, 8, 0, Double.NaN, Threats.get(PIGLINS).orElseThrow(), RandomSource.create(7L));
			helper.assertTrue(raid != null && raid.raiders() > 0, "no raid");
			AABB around = helper.getBounds().inflate(32);
			int piglins = level.getEntitiesOfClass(Mob.class, around, m -> m.isAlive() && !m.getUUID().equals(lair.captain())
				&& (m.getTags().contains(Lairs.TAG) || m.getTags().contains(VillageRaids.TAG))).size();
			helper.assertTrue(piglins >= band.size(), "the band and raiders before: " + piglins);
			// The Warlord falls.
			warlord.hurt(level.damageSources().fellOutOfWorld(), 1000f);
			helper.assertTrue(Lairs.near(level, hall).isEmpty(), "his death didn't break the outpost");
			List<LivingEntity> unturned = level.getEntitiesOfClass(LivingEntity.class, around, e -> e.isAlive() && io.github.jcondedata.aliveworkplace.threat.Piglins.piglinKind(e));
			List<Mob> turned = level.getEntitiesOfClass(Mob.class, around, m -> m.isAlive() && (m instanceof ZombifiedPiglin || m instanceof Zoglin));
			helper.assertTrue(unturned.isEmpty() && turned.size() >= piglins,
				"of " + piglins + " piglins and hoglins, zombified and in the world: " + turned.size() + ", unturned: " + unturned.size()
					+ " (the rest vanished); last warning: " + HostileSpawns.lastWarning());
			helper.succeed();
		});
	}
}
