package io.github.jcondedata.aliveworkplace.test;

import io.github.jcondedata.aliveworkplace.blueprint.BlueprintItem;
import io.github.jcondedata.aliveworkplace.build.BuildSite;
import io.github.jcondedata.aliveworkplace.build.BuildSiteManager;
import io.github.jcondedata.aliveworkplace.camp.SettlersWagonItem;
import io.github.jcondedata.aliveworkplace.colony.ColonyArrival;
import io.github.jcondedata.aliveworkplace.colony.Colonies;
import io.github.jcondedata.aliveworkplace.guard.VillageRaids;
import io.github.jcondedata.aliveworkplace.hall.Caravans;
import io.github.jcondedata.aliveworkplace.hall.VillageHallBlockEntity;
import io.github.jcondedata.aliveworkplace.hall.VillageHalls;
import io.github.jcondedata.aliveworkplace.hall.VillageNeeds;
import io.github.jcondedata.aliveworkplace.hall.VillageRanks;
import io.github.jcondedata.aliveworkplace.realm.RealmData;
import io.github.jcondedata.aliveworkplace.registry.ModBlocks;
import io.github.jcondedata.aliveworkplace.registry.ModVillagers;
import io.github.jcondedata.aliveworkplace.story.Quest;
import io.github.jcondedata.aliveworkplace.story.Reputation;
import io.github.jcondedata.aliveworkplace.story.Reputation.Title;
import io.github.jcondedata.aliveworkplace.travel.TravelNetwork;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.GlobalPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.animal.IronGolem;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.npc.VillagerProfession;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.phys.AABB;

/**
 * QA (lane-c-1115-0953) of ROADMAP 31.11 "Reputation and titles" and 33.10/33.14 "A colony is born", written from the
 * spec and the wiki (docs/wiki/reputation.md, colony-born.md) for the sides the earlier tests leave out:
 * <ul>
 *   <li>the line above the hotbar for every change ("+10 standing in Thornholm (now 10)", "-10 ... (now -10)", standing
 *   below 0), none with {@code reputation} off;</li>
 *   <li>the chat's "best anywhere": the higher title first, then the more standing (a Lord of a Town beats a Hero
 *   with five times the points);</li>
 *   <li>a negative {@code reputation} reward takes standing away and the lost title is told only to that player;</li>
 *   <li>harm outside any village costs nothing (an iron golem or a villager far from every hall);</li>
 *   <li>raid kills don't carry from one raid to the next, and a second raid pays again at its third raider;</li>
 *   <li>the 48-block search for open ground from both sides (the default is 48; dry ground exactly at the edge is used,
 *   one block short of it the settlers come home, with the owner's sentence naming the distance);</li>
 *   <li>the mother's hall broken while the settlers are on the road: the colony is still founded with everyone, or
 *   with no room everyone comes back to where the hall stood.</li>
 * </ul>
 */
public class QaReputationColony1115c3GameTests implements FabricGameTest {
	private static final String AREA = ReputationGameTests.AREA;
	private static final BlockPos HALL = ReputationGameTests.HALL;
	private static final String COLONY_AREA = "aliveworkplace_test:colony_area";
	private static final int COST = Colonies.COST * 100;

	// ===================================================================================================== reputation

	private record Seen(Component text, boolean overlay) {
	}

	/** A real player in the level whose chat and action-bar lines are kept. */
	private static ServerPlayer listener(GameTestHelper helper, BlockPos at, String name, List<Seen> seen) {
		ServerLevel level = helper.getLevel();
		var cookie = net.minecraft.server.network.CommonListenerCookie.createInitial(new com.mojang.authlib.GameProfile(UUID.randomUUID(), name), false);
		ServerPlayer player = new ServerPlayer(level.getServer(), level, cookie.gameProfile(), cookie.clientInformation()) {
			@Override
			public void displayClientMessage(Component message, boolean overlay) {
				seen.add(new Seen(message, overlay));
				super.displayClientMessage(message, overlay);
			}
		};
		net.minecraft.network.Connection connection = new net.minecraft.network.Connection(net.minecraft.network.protocol.PacketFlow.SERVERBOUND);
		new io.netty.channel.embedded.EmbeddedChannel(connection);
		level.getServer().getPlayerList().placeNewPlayer(connection, player, cookie);
		player.setGameMode(GameType.SURVIVAL);
		BlockPos abs = helper.absolutePos(at);
		player.teleportTo(abs.getX() + 0.5, abs.getY(), abs.getZ() + 0.5);
		Leftovers.after(helper, () -> level.getServer().getPlayerList().remove(player));
		return player;
	}

	private static List<String> overlays(List<Seen> seen) {
		return seen.stream().filter(Seen::overlay).map(s -> s.text().getString()).toList();
	}

	private static int points(GameTestHelper helper, ServerPlayer player) {
		return Reputation.points(helper.getLevel(), helper.absolutePos(HALL), player.getUUID());
	}

	/**
	 * "A line above your hotbar says so each time ("+10 standing in Thornholm (now 60)")": a daily quest finished shows
	 * "+10 standing in Thornholm (now 10)", a hit on a villager "-10 standing in Thornholm (now 0)", and a second hit
	 * "-10 standing in Thornholm (now -10)" (standing can go below 0). With {@code reputation} off a third hit shows
	 * nothing and costs nothing.
	 */
	//$ gametest_ticks_batch AREA '80' '"qaRepCol1115c3Bar"'
	@GameTest(template = AREA, timeoutTicks = 80, batch = "qaRepCol1115c3Bar")
	public void qaTheLineAboveTheHotbarForEveryChange(GameTestHelper helper) {
		ReputationGameTests.village(helper, () -> {
			ServerLevel level = helper.getLevel();
			List<Seen> seen = new ArrayList<>();
			ServerPlayer me = listener(helper, HALL.south(2), "QaRep", seen);
			Quest daily = ReputationGameTests.appleQuest(helper, "hall", 1);
			helper.assertTrue(ReputationGameTests.bring(helper, me, daily, 1) == 1 && points(helper, me) == 10, "the daily quest: " + points(helper, me));
			helper.assertTrue(overlays(seen).contains("+10 standing in Thornholm (now 10)"), "after the quest, above the hotbar: " + overlays(seen));
			Villager dara = HeartEventGameTests.villager(helper, new BlockPos(5, 2, 5), "Dara");
			int mark = overlays(seen).size();
			dara.hurt(level.damageSources().playerAttack(me), 1f);
			helper.assertTrue(points(helper, me) == 0, "after a hit: " + points(helper, me));
			helper.assertTrue(overlays(seen).subList(mark, overlays(seen).size()).contains("-10 standing in Thornholm (now 0)"), "after a hit: " + overlays(seen));
			mark = overlays(seen).size();
			dara.invulnerableTime = 0;
			dara.hurt(level.damageSources().playerAttack(me), 1f);
			helper.assertTrue(dara.isAlive() && points(helper, me) == -10 && ReputationGameTests.title(helper, me) == Title.STRANGER,
				"after a second hit: " + points(helper, me));
			helper.assertTrue(overlays(seen).subList(mark, overlays(seen).size()).contains("-10 standing in Thornholm (now -10)"), "after a second hit: " + overlays(seen));
			for (String line : overlays(seen)) {
				helper.assertTrue(!line.contains("%") && !line.contains("aliveworkplace."), "a raw line above the hotbar: " + line);
			}
			mark = overlays(seen).size();
			Reputation.ENABLED = false;
			dara.invulnerableTime = 0;
			dara.hurt(level.damageSources().playerAttack(me), 1f);
			helper.assertTrue(overlays(seen).subList(mark, overlays(seen).size()).stream().noneMatch(l -> l.contains("standing")),
				"a standing line with reputation off: " + overlays(seen));
			Reputation.ENABLED = true;
			helper.assertTrue(points(helper, me) == -10, "a hit with reputation off cost: " + points(helper, me));
			helper.succeed();
		});
	}

	/**
	 * "Anywhere else your best shows: the higher title, then the more standing." Away from both villages: Heroes of
	 * both, 300 in Thornholm and 450 in Brackwater, shows Brackwater; 500 in Thornholm shows Thornholm. Brackwater a Town
	 * and 1050 there (a Lord) beats 5000 in Thornholm, a Hamlet (a Hero).
	 */
	//$ gametest_ticks_batch AREA '60' '"qaRepCol1115c3Best"'
	@GameTest(template = AREA, timeoutTicks = 60, batch = "qaRepCol1115c3Best")
	public void qaTheBestTitleIsTheHigherOneThenTheMoreStanding(GameTestHelper helper) {
		ReputationGameTests.village(helper, () -> {
			ServerLevel level = helper.getLevel();
			BlockPos hall = helper.absolutePos(HALL);
			BlockPos second = new BlockPos(1, 2, 1);
			BlockPos far = helper.absolutePos(second);
			VillageHalls.RADIUS = 4; // (two villages in one test area; the village helper puts the radius back)
			helper.setBlock(second, ModBlocks.VILLAGE_HALL);
			helper.runAfterDelay(2, () -> {
				VillageHallBlockEntity brack = ReputationGameTests.reputation(helper, second, "Brackwater");
				VillageHallBlockEntity thorn = ReputationGameTests.hallEntity(helper);
				thorn.setRank(VillageRanks.Rank.HAMLET);
				brack.setRank(VillageRanks.Rank.HAMLET);
				ServerPlayer me = ReputationGameTests.player(helper, HALL.south(2));
				me.moveTo(hall.getX() + 40.5, hall.getY(), hall.getZ() + 40.5, 0, 0);
				helper.assertTrue(VillageHalls.nearest(level, me.blockPosition()).isEmpty(), "the player is still in a village");
				Component hello = Component.literal("hello");
				Reputation.add(level, hall, me, 300);
				Reputation.add(level, far, me, 450);
				helper.assertTrue(Reputation.decorate(me, hello).getString().equals("[Hero of Brackwater] hello"),
					"Hero 300 and Hero 450, the more standing: " + Reputation.decorate(me, hello).getString());
				Reputation.add(level, hall, me, 200);
				helper.assertTrue(Reputation.decorate(me, hello).getString().equals("[Hero of Thornholm] hello"),
					"Hero 500 and Hero 450, the more standing: " + Reputation.decorate(me, hello).getString());
				brack.setRank(VillageRanks.Rank.TOWN);
				Reputation.add(level, far, me, 600);
				Reputation.add(level, hall, me, 4500);
				helper.assertTrue(Reputation.title(level, far, me.getUUID()) == Title.LORD && Reputation.title(level, hall, me.getUUID()) == Title.HERO,
					"the titles: " + Reputation.title(level, far, me.getUUID()) + ", " + Reputation.title(level, hall, me.getUUID()));
				helper.assertTrue(Reputation.decorate(me, hello).getString().equals("[Lord of Brackwater] hello"),
					"Lord with 1050 and Hero with 5000, the higher title: " + Reputation.decorate(me, hello).getString());
				helper.succeed();
			});
		});
	}

	/**
	 * "Negative points take standing away": a Friend with 60 finishes a daily quest whose file pays
	 * {@code {"type": "reputation", "points": -30}}: 60 + 10 - 30 = 40, a Stranger again, told only to them.
	 */
	//$ gametest_ticks_batch AREA '60' '"qaRepCol1115c3Negative"'
	@GameTest(template = AREA, timeoutTicks = 60, batch = "qaRepCol1115c3Negative")
	public void qaANegativeRewardTakesStandingAway(GameTestHelper helper) {
		ReputationGameTests.village(helper, () -> {
			ServerLevel level = helper.getLevel();
			BlockPos hall = helper.absolutePos(HALL);
			ServerPlayer me = ReputationGameTests.player(helper, HALL.south(2));
			Reputation.add(level, hall, me, 60);
			int told = Reputation.ANNOUNCED.get();
			Quest daily = ReputationGameTests.appleQuest(helper, "hall", 1, "{\"type\": \"reputation\", \"points\": -30}");
			helper.assertTrue(ReputationGameTests.bring(helper, me, daily, 1) == 1, "the quest wasn't finished");
			helper.assertTrue(points(helper, me) == 40 && ReputationGameTests.title(helper, me) == Title.STRANGER,
				"after a -30 reward on a daily quest: " + points(helper, me) + ", " + ReputationGameTests.title(helper, me));
			helper.assertTrue(ReputationGameTests.lastTold().equals("one: You are no longer a Friend of Thornholm. You are a stranger there again."),
				"told: " + ReputationGameTests.lastTold());
			helper.assertTrue(Reputation.ANNOUNCED.get() == told, "the loss was told to the server");
			helper.succeed();
		});
	}

	/**
	 * "Killing an iron golem in the village -100", and a villager's harm counts in the villager's village: a golem
	 * killed and a villager hit 40 blocks from every hall cost nothing; the same next to the hall cost 100 and 10.
	 */
	//$ gametest_ticks_batch AREA '60' '"qaRepCol1115c3Outside"'
	@GameTest(template = AREA, timeoutTicks = 60, batch = "qaRepCol1115c3Outside")
	public void qaHarmOutsideAnyVillageCostsNothing(GameTestHelper helper) {
		ReputationGameTests.village(helper, () -> {
			ServerLevel level = helper.getLevel();
			BlockPos hall = helper.absolutePos(HALL);
			ServerPlayer me = ReputationGameTests.player(helper, HALL.south(2));
			Reputation.add(level, hall, me, 500);
			BlockPos away = new BlockPos(HALL.getX() + 40, 2, HALL.getZ());
			helper.assertTrue(VillageHalls.nearest(level, helper.absolutePos(away)).isEmpty(), "40 blocks away is still in a village");
			IronGolem wild = helper.spawn(EntityType.IRON_GOLEM, away);
			wild.hurt(level.damageSources().playerAttack(me), 1000f);
			Villager stranger = HeartEventGameTests.villager(helper, away.south(2), "Wanderer");
			stranger.hurt(level.damageSources().playerAttack(me), 1f);
			stranger.discard();
			helper.assertTrue(!wild.isAlive() && points(helper, me) == 500, "harm outside any village cost: " + points(helper, me));
			IronGolem golem = helper.spawn(EntityType.IRON_GOLEM, HALL.east(3));
			golem.hurt(level.damageSources().playerAttack(me), 1000f);
			Villager dara = HeartEventGameTests.villager(helper, HALL.west(3), "Dara");
			dara.hurt(level.damageSources().playerAttack(me), 1f);
			helper.assertTrue(!golem.isAlive() && points(helper, me) == 390, "the golem and the hit in the village: " + points(helper, me) + " (expected 390)");
			helper.succeed();
		});
	}

	/**
	 * "The third raider you kill in it: +20, once a raid": two kills in one raid and one in the next pay nothing (the
	 * count starts again with each raid); the third in the second raid pays 20.
	 */
	//$ gametest_ticks_batch AREA '80' '"qaRepCol1115c3Raids"'
	@GameTest(template = AREA, timeoutTicks = 80, batch = "qaRepCol1115c3Raids")
	public void qaRaidKillsDontCarryToTheNextRaid(GameTestHelper helper) {
		ReputationGameTests.village(helper, () -> {
			ServerLevel level = helper.getLevel();
			BlockPos hall = helper.absolutePos(HALL);
			VillageRaids.forget();
			Leftovers.after(helper, VillageRaids::forget);
			ServerPlayer me = ReputationGameTests.player(helper, HALL.south(2));
			helper.assertTrue(VillageRaids.track(level, hall, 6) != null && VillageRaids.active(hall).isPresent(), "no first raid");
			raider(helper, 0).hurt(level.damageSources().playerAttack(me), 1000f);
			raider(helper, 1).hurt(level.damageSources().playerAttack(me), 1000f);
			helper.assertTrue(points(helper, me) == 0, "two kills paid: " + points(helper, me));
			VillageRaids.forget(hall);
			helper.runAfterDelay(3, () -> {
				helper.assertTrue(VillageRaids.track(level, hall, 6) != null && VillageRaids.active(hall).isPresent(), "no second raid");
				raider(helper, 2).hurt(level.damageSources().playerAttack(me), 1000f);
				helper.assertTrue(points(helper, me) == 0, "the first raid's kills carried over: " + points(helper, me));
				raider(helper, 3).hurt(level.damageSources().playerAttack(me), 1000f);
				helper.assertTrue(points(helper, me) == 0, "paid at the second raid's second kill: " + points(helper, me));
				raider(helper, 4).hurt(level.damageSources().playerAttack(me), 1000f);
				helper.assertTrue(points(helper, me) == 20, "the second raid's third raider: " + points(helper, me));
				helper.succeed();
			});
		});
	}

	private static Zombie raider(GameTestHelper helper, int n) {
		Zombie zombie = helper.spawn(EntityType.ZOMBIE, new BlockPos(3 + n, 2, 3));
		zombie.addTag(VillageRaids.TAG);
		return zombie;
	}

	// ===================================================================================================== colony born

	private record Mother(ServerLevel level, BlockPos hall, VillageHallBlockEntity entity, ServerPlayer owner, List<Seen> heard) {
		GlobalPos at() {
			return GlobalPos.of(level.dimension(), hall);
		}

		RealmData realms() {
			return RealmData.get(level.getServer());
		}

		List<String> chat() {
			return heard.stream().filter(s -> !s.overlay()).map(s -> s.text().getString()).toList();
		}
	}

	/** Thornholm at {@code at}, owned by a listening player, village shrunk to 8 blocks, no hall rounds; cleaned up after. */
	private static Mother mother(GameTestHelper helper, BlockPos at) {
		Leftovers.clear(helper);
		Leftovers.halls(helper);
		Leftovers.finished(helper);
		ServerLevel level = helper.getLevel();
		boolean wasEnabled = Colonies.ENABLED;
		int every = VillageNeeds.CHECK_EVERY;
		int radius = VillageHalls.RADIUS;
		int room = ColonyArrival.ROOM;
		Colonies.ENABLED = true;
		VillageNeeds.CHECK_EVERY = 1_000_000;
		VillageHalls.RADIUS = 8;
		RealmData.get(level.getServer()).clearColonies();
		openSky(helper);
		helper.setBlock(at, ModBlocks.VILLAGE_HALL);
		BlockPos hall = helper.absolutePos(at);
		List<Seen> heard = new ArrayList<>();
		ServerPlayer owner = listener(helper, at.south(2), "QaOwner", heard);
		VillageHallBlockEntity entity = helper.getBlockEntity(at);
		entity.setCustomName(Component.literal("Thornholm"));
		entity.setOwner(owner.getUUID(), "QaOwner");
		AABB area = helper.getBounds();
		Leftovers.after(helper, () -> {
			Colonies.ENABLED = wasEnabled;
			VillageNeeds.CHECK_EVERY = every;
			VillageHalls.RADIUS = radius;
			ColonyArrival.ROOM = room;
			RealmData realms = RealmData.get(level.getServer());
			Caravans.Data caravans = Caravans.Data.get(level);
			for (RealmData.Founded f : realms.founded()) {
				caravans.remove(f.hall().pos());
				TravelNetwork network = TravelNetwork.get(level.getServer());
				for (BlockPos p : BlockPos.betweenClosed(f.hall().pos().offset(-8, -2, -8), f.hall().pos().offset(8, 2, 8))) {
					network.remove(GlobalPos.of(level.dimension(), p.immutable()));
				}
			}
			caravans.remove(hall);
			caravans.clearRoad(hall);
			realms.clearColonies();
			BuildSiteManager sites = BuildSiteManager.get(level);
			for (BuildSite site : List.copyOf(sites.all())) {
				if (area.inflate(16).contains(site.placement().origin().getCenter())) {
					sites.remove(site.id());
				}
			}
			for (Villager v : level.getEntitiesOfClass(Villager.class, area.inflate(16))) {
				v.discard();
			}
		});
		return new Mother(level, hall, entity, owner, heard);
	}

	private static void openSky(GameTestHelper helper) {
		AABB box = helper.getBounds();
		ServerLevel level = helper.getLevel();
		for (BlockPos p : BlockPos.betweenClosed(BlockPos.containing(box.minX, box.maxY - 3, box.minZ), BlockPos.containing(box.maxX, box.maxY + 3, box.maxZ))) {
			if (level.getBlockState(p).is(Blocks.BARRIER)) {
				level.setBlock(p, Blocks.AIR.defaultBlockState(), 2);
			}
		}
	}

	private static CompoundTag saved(GameTestHelper helper, String name, VillagerProfession job, int jobLevel) {
		Villager v = helper.spawn(EntityType.VILLAGER, new BlockPos(40, 2, 40));
		v.setCustomName(Component.literal(name));
		v.setVillagerData(v.getVillagerData().setProfession(job).setLevel(jobLevel));
		CompoundTag tag = new CompoundTag();
		v.saveWithoutId(tag);
		v.discard();
		return tag;
	}

	private static Map<Item, Integer> supplies() {
		Map<Item, Integer> out = new LinkedHashMap<>();
		out.put(Items.OAK_PLANKS, 64);
		out.put(Items.BREAD, 16);
		return out;
	}

	private static RealmData.Order order(GameTestHelper helper, Mother m, BlockPos spot) {
		long now = m.level().getGameTime();
		CompoundTag tomas = saved(helper, "Tomas", ModVillagers.BUILDER, 2);
		CompoundTag dara = saved(helper, "Dara", VillagerProfession.FARMER, 1);
		return new RealmData.Order(m.at(), helper.absolutePos(spot), Optional.of(Component.literal("Newbrook")), RealmData.ON_ROAD, now - 100, now, COST,
			now - 200, List.of(), Optional.of(tomas.getUUID("UUID")), supplies(), List.of(tomas, dara));
	}

	private static List<Villager> named(GameTestHelper helper, String name) {
		List<Villager> out = new ArrayList<>();
		for (Villager v : helper.getLevel().getEntitiesOfClass(Villager.class, helper.getBounds().inflate(48))) {
			if (v.isAlive() && v.getDisplayName().getString().equals(name)) {
				out.add(v);
			}
		}
		return out;
	}

	/** Where the settlers' spot is, in the colony area: the middle. */
	private static final BlockPos SPOT = new BlockPos(22, 2, 22);
	/** Where the dry ground's nearest camp front is, east of the spot (a multiple of the search's 3-block step). */
	private static final int EDGE = 12;

	/**
	 * The colony area flooded, but for a dry band on the east: the only places the camp fits have their front's middle
	 * at least {@link #EDGE} blocks east of the spot (the band starts exactly where the camp's west side would be with
	 * its front {@code EDGE} blocks east). A stone rim keeps the water in. Returns the band's first column (relative).
	 */
	private static int lakeWithADryBand(GameTestHelper helper, Mother m) {
		ServerLevel level = m.level();
		// The mother is north of the spot, so the camp faces north (toward her).
		BlockPos front = helper.absolutePos(SPOT.east(EDGE));
		BoundingBox box = SettlersWagonItem.campBox(level, ColonyArrival.COLONY_CAMP, front, BlueprintItem.rotationFacing(Direction.NORTH));
		helper.assertTrue(box != null, "no Colony Camp template");
		int band = SPOT.getX() + EDGE - (front.getX() - box.minX());
		helper.assertTrue(band > SPOT.getX() + 2 && box.maxX() <= helper.absolutePos(new BlockPos(43, 0, 0)).getX(),
			"the camp doesn't fit the colony area's east band: band from x " + band + ", box " + box);
		for (int x = 0; x < band; x++) {
			for (int z = 0; z <= 43; z++) {
				boolean rim = x == 0 || z == 0 || z == 43 || x == band - 1;
				helper.setBlock(new BlockPos(x, 2, z), rim ? Blocks.STONE_BRICKS : Blocks.WATER);
			}
		}
		return band;
	}

	/**
	 * "The nearest open ground within 48 blocks", inside edge: the search reaches 48 blocks by default, and dry ground
	 * whose nearest camp is exactly at the search's distance is used (search shrunk to {@link #EDGE} so it fits the
	 * area). The camp stands wholly on the dry band, not a block of the lake is touched, and the colony is founded.
	 */
	//$ gametest_ticks_batch COLONY_AREA '300' '"qaRepCol1115c3EdgeIn"'
	@GameTest(template = COLONY_AREA, timeoutTicks = 300, batch = "qaRepCol1115c3EdgeIn")
	public void qaDryGroundExactlyAtTheSearchsEdgeIsUsed(GameTestHelper helper) {
		helper.assertTrue(ColonyArrival.ROOM == 48, "the search for open ground isn't 48 blocks: " + ColonyArrival.ROOM);
		Mother m = mother(helper, new BlockPos(36, 2, 3));
		int band = lakeWithADryBand(helper, m);
		ColonyArrival.ROOM = EDGE;
		helper.runAfterDelay(2, () -> m.realms().putOrder(order(helper, m, SPOT)));
		helper.succeedWhen(() -> {
			helper.assertTrue(m.realms().orders().isEmpty() && m.realms().foundedBy(m.at()).size() == 1,
				"no colony with dry ground exactly " + EDGE + " blocks off: orders " + m.realms().orders() + ", founded " + m.realms().founded());
			BlockPos hall = m.realms().foundedBy(m.at()).get(0).hall().pos();
			helper.assertTrue(hall.getX() >= helper.absolutePos(new BlockPos(band, 0, 0)).getX(), "the colony's hall isn't on the dry band: " + hall.toShortString());
			for (int x = 1; x < band - 1; x++) {
				for (int z = 1; z < 43; z++) {
					helper.assertTrue(helper.getLevel().getBlockState(helper.absolutePos(new BlockPos(x, 2, z))).is(Blocks.WATER),
						"the lake was built over at " + x + ", 2, " + z);
				}
			}
			helper.assertTrue(named(helper, "Tomas").size() == 1 && named(helper, "Dara").size() == 1,
				"the settlers at the camp: Tomas " + named(helper, "Tomas").size() + ", Dara " + named(helper, "Dara").size());
		});
	}

	/**
	 * Outside edge: the same lake with the search one block short of the dry ground ({@link #EDGE} - 1): no room, so
	 * nothing is built on the band, the settlers are back home, the 32 emeralds in the treasury, and the owner reads "No
	 * room for Newbrook: only water or cliffs within 11 blocks of the spot. ..."
	 */
	//$ gametest_ticks_batch COLONY_AREA '300' '"qaRepCol1115c3EdgeOut"'
	@GameTest(template = COLONY_AREA, timeoutTicks = 300, batch = "qaRepCol1115c3EdgeOut")
	public void qaOneBlockShortOfDryGroundTheSettlersComeHome(GameTestHelper helper) {
		Mother m = mother(helper, new BlockPos(36, 2, 3));
		m.entity().setTreasury(500);
		int band = lakeWithADryBand(helper, m);
		ColonyArrival.ROOM = EDGE - 1;
		boolean[] given = new boolean[1];
		helper.runAfterDelay(2, () -> {
			m.realms().putOrder(order(helper, m, SPOT));
			given[0] = true;
		});
		helper.succeedWhen(() -> {
			helper.assertTrue(given[0] && m.realms().orders().isEmpty(), "the order is still out: " + m.realms().orders());
			helper.assertTrue(m.realms().founded().isEmpty(), "a colony was founded beyond the search: " + m.realms().founded());
			for (BlockPos p : BlockPos.betweenClosed(helper.absolutePos(new BlockPos(band, 2, 6)), helper.absolutePos(new BlockPos(43, 4, 43)))) {
				helper.assertTrue(!helper.getLevel().getBlockState(p).is(ModBlocks.VILLAGE_HALL), "a hall was put on the dry band at " + p.toShortString());
			}
			helper.assertTrue(m.entity().treasury() == 500 + COST, "the treasury: " + m.entity().treasury() + " (expected " + (500 + COST) + ")");
			helper.assertTrue(named(helper, "Tomas").size() == 1 && named(helper, "Dara").size() == 1,
				"settlers home: Tomas " + named(helper, "Tomas").size() + ", Dara " + named(helper, "Dara").size());
			String expected = "No room for Newbrook: only water or cliffs within " + (EDGE - 1) + " blocks of the spot. Tomas and Dara came home to Thornholm "
				+ "with the supplies, and 32 emeralds went back into the treasury.";
			helper.assertTrue(m.chat().contains(expected), "what the owner read: " + m.chat());
		});
	}

	/**
	 * The mother's hall broken while the settlers are on the road (the road can't be called off then): when they arrive
	 * the colony is still founded, named Newbrook, and Tomas and Dara step out there, once each.
	 */
	//$ gametest_ticks_batch COLONY_AREA '300' '"qaRepCol1115c3MotherGone"'
	@GameTest(template = COLONY_AREA, timeoutTicks = 300, batch = "qaRepCol1115c3MotherGone")
	public void qaTheMothersHallBrokenOnTheRoadTheColonyIsStillFounded(GameTestHelper helper) {
		Mother m = mother(helper, new BlockPos(3, 2, 3));
		long[] arrives = new long[1];
		helper.runAfterDelay(2, () -> {
			RealmData.Order order = order(helper, m, new BlockPos(24, 2, 26));
			long now = m.level().getGameTime();
			// still on the road for 20 ticks
			m.realms().putOrder(order.withState(RealmData.ON_ROAD, order.leaves(), now + 20));
			arrives[0] = now + 20;
			m.level().destroyBlock(m.hall(), false);
		});
		helper.succeedWhen(() -> {
			helper.assertTrue(arrives[0] > 0 && !helper.getLevel().getBlockState(m.hall()).is(ModBlocks.VILLAGE_HALL), "the mother's hall still stands");
			helper.assertTrue(m.realms().orders().isEmpty() && m.realms().foundedBy(m.at()).size() == 1,
				"not founded: orders " + m.realms().orders() + ", founded " + m.realms().founded());
			BlockPos hall = m.realms().foundedBy(m.at()).get(0).hall().pos();
			helper.assertTrue(helper.getLevel().getBlockEntity(hall) instanceof VillageHallBlockEntity, "no hall at the colony");
			helper.assertTrue(VillageHalls.name(helper.getLevel(), hall).getString().equals("Newbrook"),
				"the colony's name: " + VillageHalls.name(helper.getLevel(), hall).getString());
			helper.assertTrue(named(helper, "Tomas").size() == 1 && named(helper, "Dara").size() == 1,
				"the settlers at the colony: Tomas " + named(helper, "Tomas").size() + ", Dara " + named(helper, "Dara").size());
		});
	}

	/**
	 * The mother's hall broken while the settlers are on the road, and no room at the spot (search shrunk, a pond): the
	 * settlers come back to where the hall stood, once each, and the supplies are there (on the ground or in their
	 * pockets): nobody and nothing is lost.
	 */
	//$ gametest_ticks_batch COLONY_AREA '300' '"qaRepCol1115c3MotherGoneNoRoom"'
	@GameTest(template = COLONY_AREA, timeoutTicks = 300, batch = "qaRepCol1115c3MotherGoneNoRoom")
	public void qaNoRoomWithTheMothersHallGoneNobodyIsLost(GameTestHelper helper) {
		Mother m = mother(helper, new BlockPos(3, 2, 3));
		ColonyArrival.ROOM = 3;
		for (int x = 14; x <= 36; x++) {
			for (int z = 14; z <= 36; z++) {
				boolean rim = x == 14 || x == 36 || z == 14 || z == 36;
				helper.setBlock(new BlockPos(x, 2, z), rim ? Blocks.STONE_BRICKS : Blocks.WATER);
			}
		}
		boolean[] given = new boolean[1];
		helper.runAfterDelay(2, () -> {
			RealmData.Order order = order(helper, m, new BlockPos(25, 2, 25));
			m.realms().putOrder(order.withState(RealmData.ON_ROAD, order.leaves(), m.level().getGameTime() + 20));
			m.level().destroyBlock(m.hall(), false);
			given[0] = true;
		});
		helper.succeedWhen(() -> {
			helper.assertTrue(given[0] && m.realms().orders().isEmpty(), "the order is still out: " + m.realms().orders());
			helper.assertTrue(m.realms().founded().isEmpty(), "a colony was founded: " + m.realms().founded());
			helper.assertTrue(named(helper, "Tomas").size() == 1 && named(helper, "Dara").size() == 1,
				"settlers back: Tomas " + named(helper, "Tomas").size() + ", Dara " + named(helper, "Dara").size());
			int bread = 0;
			int planks = 0;
			for (net.minecraft.world.entity.item.ItemEntity item : helper.getLevel().getEntitiesOfClass(net.minecraft.world.entity.item.ItemEntity.class,
				helper.getBounds().inflate(4))) {
				bread += item.getItem().is(Items.BREAD) ? item.getItem().getCount() : 0;
				planks += item.getItem().is(Items.OAK_PLANKS) ? item.getItem().getCount() : 0;
			}
			for (Villager v : helper.getLevel().getEntitiesOfClass(Villager.class, helper.getBounds().inflate(4))) {
				for (int i = 0; i < v.getInventory().getContainerSize(); i++) {
					bread += v.getInventory().getItem(i).is(Items.BREAD) ? v.getInventory().getItem(i).getCount() : 0;
					planks += v.getInventory().getItem(i).is(Items.OAK_PLANKS) ? v.getInventory().getItem(i).getCount() : 0;
				}
			}
			helper.assertTrue(bread == 16 && planks == 64, "the supplies back: bread " + bread + " (16), planks " + planks + " (64)");
		});
	}
}
