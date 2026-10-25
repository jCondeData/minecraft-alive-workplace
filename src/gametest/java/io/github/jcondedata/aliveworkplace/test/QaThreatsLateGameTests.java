package io.github.jcondedata.aliveworkplace.test;

import io.github.jcondedata.aliveworkplace.AliveWorkplace;
import io.github.jcondedata.aliveworkplace.bounty.Bounties;
import io.github.jcondedata.aliveworkplace.guard.Relief;
import io.github.jcondedata.aliveworkplace.hall.Chronicle;
import io.github.jcondedata.aliveworkplace.hall.VillageHallBlockEntity;
import io.github.jcondedata.aliveworkplace.hall.VillageHalls;
import io.github.jcondedata.aliveworkplace.hall.VillageRanks;
import io.github.jcondedata.aliveworkplace.realm.Realms;
import io.github.jcondedata.aliveworkplace.registry.ModBlocks;
import io.github.jcondedata.aliveworkplace.registry.ModItems;
import io.github.jcondedata.aliveworkplace.wonder.GreatWallPowers;
import java.util.ArrayList;
import java.util.List;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

/**
 * QA, from the spec: the bounty board's names, rewards and hand-in rules (31.13), relief's band arithmetic, thresholds,
 * reach and ride time (33.17), and the Great Wall's barrier margin (35.16).
 */
public class QaThreatsLateGameTests implements FabricGameTest {
	private static final String AREA = "aliveworkplace_test:huge_area";
	private static final BlockPos HALL = new BlockPos(5, 2, 5);
	private static final BlockPos OTHER_HALL = new BlockPos(26, 2, 26);
	private static final BlockPos CAMP = new BlockPos(18, 2, 18);
	private static final BlockPos BOARD = new BlockPos(8, 2, 5);

	private static final List<String> FIRST = List.of("Grimwald", "Varka", "Osric", "Hobb", "Maud", "Teague", "Rook", "Sable",
		"Corvin", "Ysolde", "Brannoc", "Edda", "Fulk", "Ilse", "Jory", "Kestrel", "Lorcan", "Morwen", "Nyle", "Petra", "Quill",
		"Ronan", "Silas", "Wenna");
	/** The spec's 16, "the Grey" spelled "the Gray" by the American-spelling sweep (22.10a). */
	private static final List<String> EPITHETS = List.of("the Red", "Blackhand", "the Fox", "Ironjaw", "the Gray", "Halfmoon",
		"the Cleaver", "Longshot", "the Silent", "Ashcloak", "the Tall", "One-Eye", "the Cruel", "Quickblade", "Stormcrow", "the Mad");

	private static boolean said(List<Component> heard, String text) {
		return heard.stream().anyMatch(c -> c.getString().equals(text));
	}

	/**
	 * The captains' names are the spec's 24 and 16; a Town's bounty pays 12 x 1.5; with no insignia, or at another
	 * village's hall, nothing is paid and the player is told why (the insignia kept); a captain who dies to no player
	 * still drops his insignia; right-clicking the board with it pays, once, and says so.
	 */
	//$ gametest_ticks_batch AREA '60' '"qaBountyClaim"'
	@GameTest(template = AREA, timeoutTicks = 60, batch = "qaBountyClaim")
	public void bountyNamesRewardsAndHandInRules(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		Leftovers.clear(helper);
		Leftovers.halls(helper);
		int radius = VillageHalls.RADIUS;
		boolean enabled = Bounties.ENABLED;
		VillageHalls.RADIUS = 16;
		Bounties.ENABLED = true;
		Bounties.forget(level);
		Leftovers.after(helper, () -> {
			VillageHalls.RADIUS = radius;
			Bounties.ENABLED = enabled;
			Bounties.forget(level);
		});
		helper.assertTrue(Bounties.firstNames().equals(FIRST), "first names: " + Bounties.firstNames());
		helper.assertTrue(Bounties.epithets().equals(EPITHETS), "epithets: " + Bounties.epithets());
		helper.assertTrue(Bounties.Bounty.name(1, 1).getString().equals("Varka Blackhand"), "name(1,1): " + Bounties.Bounty.name(1, 1).getString());
		helper.assertTrue(Bounties.Bounty.name(23, 15).getString().equals("Wenna the Mad"), "name(23,15): " + Bounties.Bounty.name(23, 15).getString());

		helper.setBlock(HALL, ModBlocks.VILLAGE_HALL);
		helper.setBlock(OTHER_HALL, ModBlocks.VILLAGE_HALL);
		BlockPos hall = helper.absolutePos(HALL);
		BlockPos other = helper.absolutePos(OTHER_HALL);
		VillageHallBlockEntity entity = (VillageHallBlockEntity) level.getBlockEntity(hall);
		entity.setLastQuestDay(Long.MAX_VALUE / 2);
		entity.setRank(VillageRanks.Rank.TOWN);
		((VillageHallBlockEntity) level.getBlockEntity(other)).setLastQuestDay(Long.MAX_VALUE / 2);
		helper.setBlock(BOARD, ModBlocks.BOUNTY_BOARD);

		Bounties.Bounty b = Bounties.post(level, hall, AliveWorkplace.id("fleetfoot"), 0, 0, hall.offset(160, 0, 0));
		helper.assertTrue(b != null && b.reward == 18, "12 emeralds times the Town factor: " + (b == null ? "none" : b.reward));

		List<Component> heard = new ArrayList<>();
		ServerPlayer me = RealmGameTests.listener(helper, heard, "qa-hunter");
		helper.assertTrue(Bounties.claimAny(me, level, hall) == Bounties.Claim.NONE, "claimed with no insignia");
		helper.assertTrue(said(heard, "You carry no Captain's Insignia."), "no-insignia line: " + heard.stream().map(Component::getString).toList());

		helper.assertTrue(Bounties.raise(level, b, helper.absolutePos(CAMP), helper.absolutePos(CAMP.north(4))), "he didn't stand");
		Mob captain = level.getEntity(b.captain) instanceof Mob m ? m : null;
		helper.assertTrue(captain != null, "no captain");
		captain.hurt(level.damageSources().generic(), 1000f); // no player's blow
		helper.runAfterDelay(3, () -> {
			ItemEntity drop = level.getEntitiesOfClass(ItemEntity.class, new AABB(helper.absolutePos(CAMP)).inflate(6),
				e -> e.getItem().is(ModItems.CAPTAINS_INSIGNIA)).stream().findFirst().orElse(null);
			helper.assertTrue(drop != null && b.fallen, "no insignia when he died to no player");
			helper.assertTrue(entity.chronicle().stream().anyMatch(e -> e.kind() == Chronicle.Kind.BOUNTY && e.text().getString().equals("Grimwald the Red fell.")),
				"chronicle: " + entity.chronicle().stream().map(e -> e.text().getString()).toList());
			ItemStack insignia = drop.getItem().copy();
			drop.discard();
			me.getInventory().add(insignia);
			// the wrong village: refused, told where, insignia kept
			heard.clear();
			helper.assertTrue(Bounties.claimAny(me, level, other) == Bounties.Claim.NOT_HERE, "paid at another village's hall");
			String name = VillageHalls.name(level, hall).getString();
			helper.assertTrue(said(heard, "This bounty was posted by " + name + "; hand the insignia in there."), "not-here line: "
				+ heard.stream().map(Component::getString).toList());
			helper.assertTrue(me.getInventory().countItem(ModItems.CAPTAINS_INSIGNIA) == 1 && me.getInventory().countItem(Items.EMERALD) == 0,
				"the wrong hall took the insignia or paid");
			// the board, right-clicked with it in hand
			int slot = 0;
			while (!me.getInventory().getItem(slot).is(ModItems.CAPTAINS_INSIGNIA)) {
				slot++;
			}
			ItemStack held = me.getInventory().removeItemNoUpdate(slot);
			me.setItemInHand(InteractionHand.MAIN_HAND, held);
			heard.clear();
			BlockPos board = helper.absolutePos(BOARD);
			level.getBlockState(board).useItemOn(me.getMainHandItem(), level, me, InteractionHand.MAIN_HAND,
				new BlockHitResult(Vec3.atCenterOf(board), Direction.NORTH, board, false));
			helper.assertTrue(me.getInventory().countItem(Items.EMERALD) == 18 && me.getInventory().countItem(ModItems.CAPTAINS_INSIGNIA) == 0,
				"the board paid " + me.getInventory().countItem(Items.EMERALD));
			helper.assertTrue(said(heard, "Paid 18 emeralds for the insignia of Grimwald the Red."), "paid line: " + heard.stream().map(Component::getString).toList());
			helper.assertTrue(entity.chronicle().stream().anyMatch(e -> e.text().getString().equals("qa-hunter handed in the insignia of Grimwald the Red and was paid 18 emeralds.")),
				"claimed chronicle: " + entity.chronicle().stream().map(e -> e.text().getString()).toList());
			helper.assertTrue(Bounties.open(level, hall).isEmpty(), "still open after the claim");
			helper.succeed();
		});
	}

	/**
	 * Six riders at most in all: the capital (9 guards: 3) and hamlet 2 (9 guards: 3) fill it, so hamlet 3 (9 guards)
	 * sends none; the treasury pays exactly 6 emeralds.
	 */
	//$ gametest_ticks_batch AREA '20' '"qaReliefCap"'
	@GameTest(template = AREA, timeoutTicks = 20, batch = "qaReliefCap")
	public void reliefStopsAtSixRiders(GameTestHelper helper) {
		RealmGameTests.Land land = ReliefGameTests.realm(helper, 1);
		ServerLevel level = land.level();
		helper.assertTrue(Realms.add(level, land.hall(0), land.at(3), land.owner()).ok(), "hall 3 didn't join");
		ReliefGameTests.treasury(land, 1000);
		ReliefGameTests.guards(land, 0, 9);
		ReliefGameTests.guards(land, 2, 9);
		ReliefGameTests.guards(land, 3, 9);
		int riders = Relief.call(level, land.hall(1));
		List<Relief.Band> bands = Relief.onTheRoad(level, land.hall(1));
		int sum = bands.stream().mapToInt(Relief.Band::riders).sum();
		helper.assertTrue(riders == 6 && sum == 6, "riders " + riders + ", bands " + bands);
		helper.assertTrue(bands.stream().allMatch(band -> band.riders() <= Relief.MAX_BAND), "a band over 3: " + bands);
		helper.assertTrue(bands.stream().anyMatch(band -> band.capital() && band.riders() == 3), "the capital's band of 3: " + bands);
		helper.assertTrue(ReliefGameTests.theRealm(land).treasury() == 400, "6 riders cost " + (1000 - ReliefGameTests.theRealm(land).treasury()) + " cents");
		// realms switched off: no realm, so no one rides (put back by the land's cleanup)
		Relief.forget();
		Realms.ENABLED = false;
		helper.assertTrue(Relief.call(level, land.hall(1)) == 0 && Relief.onTheRoad(level, land.hall(1)).isEmpty(), "riders came with realms off");
		helper.assertTrue(ReliefGameTests.theRealm(land).treasury() == 400, "paid with realms off");
		helper.succeed();
	}

	/**
	 * Who comes: a member with 1 guard sends no one and one with 2 sends one; the ride is a tick per 4 blocks once the
	 * 30-second floor is lowered; out of range a member stays home but the capital comes wherever it is; with one
	 * emerald left only the capital's band is paid for.
	 */
	//$ gametest_ticks_batch AREA '20' '"qaReliefRange"'
	@GameTest(template = AREA, timeoutTicks = 20, batch = "qaReliefRange")
	public void reliefThresholdsReachAndRide(GameTestHelper helper) {
		RealmGameTests.Land land = ReliefGameTests.realm(helper, 1);
		ServerLevel level = land.level();
		long minRide = Relief.MIN_RIDE;
		int range = Relief.RANGE;
		Leftovers.after(helper, () -> {
			Relief.MIN_RIDE = minRide;
			Relief.RANGE = range;
		});
		helper.assertTrue(Realms.add(level, land.hall(0), land.at(3), land.owner()).ok(), "hall 3 didn't join");
		ReliefGameTests.treasury(land, 1000);
		ReliefGameTests.guards(land, 2, 1);
		ReliefGameTests.guards(land, 3, 2);
		Relief.MIN_RIDE = 1;
		Relief.call(level, land.hall(1));
		List<Relief.Band> bands = Relief.onTheRoad(level, land.hall(1));
		helper.assertTrue(bands.stream().noneMatch(band -> band.home().equals(land.at(2))), "a member with 1 guard sent help: " + bands);
		Relief.Band three = bands.stream().filter(band -> band.home().equals(land.at(3))).findFirst().orElse(null);
		helper.assertTrue(three != null && three.riders() == 1, "a member with 2 guards: " + bands);
		long expected = (long) Math.ceil(Math.sqrt(land.hall(3).distSqr(land.hall(1))) / 4);
		helper.assertTrue(three.arrive() - three.sent() == expected, "ride " + (three.arrive() - three.sent()) + ", a tick per 4 blocks is " + expected);
		Relief.Band capital = bands.stream().filter(Relief.Band::capital).findFirst().orElse(null);
		long capitalRide = (long) Math.ceil(Math.sqrt(land.hall(0).distSqr(land.hall(1))) / 4);
		helper.assertTrue(capital != null && capital.riders() == 1 && capital.arrive() - capital.sent() == capitalRide, "the capital's band: " + capital);
		helper.assertTrue(ReliefGameTests.theRealm(land).treasury() == 800, "treasury " + ReliefGameTests.theRealm(land).treasury());

		// out of range: hamlet 3 stays home, the capital still comes; and one emerald left pays only the capital's rider
		Relief.forget();
		ReliefGameTests.guards(land, 2, 1); // hamlet 2 now has 2 guards, in range of nothing either
		Relief.RANGE = 5;
		ReliefGameTests.treasury(land, 100);
		Relief.call(level, land.hall(1));
		bands = Relief.onTheRoad(level, land.hall(1));
		helper.assertTrue(bands.size() == 1 && bands.get(0).capital(), "out of range: " + bands);
		helper.assertTrue(ReliefGameTests.theRealm(land).treasury() == 0, "treasury " + ReliefGameTests.theRealm(land).treasury());
		Relief.forget();
		Relief.RANGE = range;
		ReliefGameTests.treasury(land, 100);
		Relief.call(level, land.hall(1));
		bands = Relief.onTheRoad(level, land.hall(1));
		helper.assertTrue(bands.size() == 1 && bands.get(0).capital(), "one emerald paid for more than the capital's rider: " + bands);
		helper.succeed();
	}

	/**
	 * The barrier's arc is the wall's own, seen from the hall, plus 10 degrees either side, also where it wraps past
	 * due west; a hall inside the footprint gets no arc; a gathering aimed behind the wall lands just outside it.
	 */
	//$ gametest 'FabricGameTest.EMPTY_STRUCTURE'
	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
	public void greatWallArcHasTenDegreesEitherSide(GameTestHelper helper) {
		BlockPos hall = BlockPos.ZERO;
		double margin = Math.toRadians(10);
		// north of the hall: its near corners at -135 and -45 degrees
		List<GreatWallPowers.Arc> north = List.of(GreatWallPowers.arc(hall, new BoundingBox(-10, 0, -20, 10, 5, -11), margin).orElseThrow());
		for (double d : new double[] {-144, -90, -36}) {
			helper.assertTrue(GreatWallPowers.walled(north, Math.toRadians(d)), "not walled at " + d);
		}
		for (double d : new double[] {-146, -34, 90, 0, 180}) {
			helper.assertFalse(GreatWallPowers.walled(north, Math.toRadians(d)), "walled at " + d);
		}
		// west of the hall: 135..225 degrees, plus the margin, across +-180
		List<GreatWallPowers.Arc> west = List.of(GreatWallPowers.arc(hall, new BoundingBox(-20, 0, -10, -11, 5, 10), margin).orElseThrow());
		for (double d : new double[] {180, -180, 126, -126, 170, -170}) {
			helper.assertTrue(GreatWallPowers.walled(west, Math.toRadians(d)), "west wall not walled at " + d);
		}
		for (double d : new double[] {124, -124, 0, 90}) {
			helper.assertFalse(GreatWallPowers.walled(west, Math.toRadians(d)), "west wall walled at " + d);
		}
		helper.assertTrue(GreatWallPowers.arc(hall, new BoundingBox(-5, 0, -5, 5, 5, 5), margin).isEmpty(), "an arc for a hall inside the wall");
		// aimed straight behind it with a narrow spread: just outside, at an edge
		RandomSource random = RandomSource.create(3516L);
		for (int i = 0; i < 20; i++) {
			double b = GreatWallPowers.bearing(north, Math.toRadians(-90), Math.toRadians(5), random);
			double deg = Math.toDegrees(b);
			deg = ((deg + 540) % 360) - 180;
			helper.assertFalse(GreatWallPowers.walled(north, b), "gathered behind it at " + deg);
			helper.assertTrue(Math.abs(deg + 145) < 3 || Math.abs(deg + 35) < 3, "not at the arc's edge: " + deg);
		}
		helper.succeed();
	}
}
