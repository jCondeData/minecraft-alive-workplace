package io.github.jcondedata.aliveworkplace.test;

import io.github.jcondedata.aliveworkplace.camp.CampBeds;
import io.github.jcondedata.aliveworkplace.camp.SettlersWagonItem;
import io.github.jcondedata.aliveworkplace.hall.VillageGrowth;
import io.github.jcondedata.aliveworkplace.hall.VillageHallBlockEntity;
import io.github.jcondedata.aliveworkplace.hall.VillageHallScreen;
import io.github.jcondedata.aliveworkplace.hall.VillageHalls;
import io.github.jcondedata.aliveworkplace.hall.VillageNeeds;
import io.github.jcondedata.aliveworkplace.inn.Innkeepers;
import io.github.jcondedata.aliveworkplace.registry.ModBlocks;
import io.github.jcondedata.aliveworkplace.registry.ModVillagers;
import io.github.jcondedata.aliveworkplace.work.Jobs;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.ItemLore;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.properties.BedPart;
import net.minecraft.world.level.levelgen.structure.BoundingBox;

/**
 * QA (lane-c-1107-0052) of B206 from its spec: "a village with N free beds admits at most N settlers/arrivals, with the
 * settlers' wagon beds not counted", "a traveller who moves on frees it", and "the hall's Beds button says 'The village is
 * full: nobody new moves in until a bed is free'". Boundaries from both sides: N = 0 and N = 3 (the N+1th newcomer is
 * refused), a bed freed by a death, a traveller moving on through the inn's own round (and staying while a player
 * watches), and the hall's full line shown at 0 free beds and not at 1.
 */
public class QaBedCap1107GameTests implements net.fabricmc.fabric.api.gametest.v1.FabricGameTest {
	private static final String AREA = "aliveworkplace_test:huge_area";
	private static final BlockPos CAMP = new BlockPos(15, 2, 4);
	private static final BlockPos HALL = new BlockPos(15, 2, 20);
	private static final BlockPos COUNTER = new BlockPos(10, 2, 18);
	private static final BlockPos STORE_CHEST = new BlockPos(25, 2, 26);
	private static final String FULL = "The village is full: nobody new moves in until a bed is free";
	private static final VillageNeeds.Needs HAPPY = new VillageNeeds.Needs(2, 2, 2, 2, 2, 0, 0, 1f);

	private static void radii(GameTestHelper helper) {
		int hall = VillageHalls.RADIUS;
		int inn = Innkeepers.RADIUS;
		long stay = Innkeepers.STAY;
		int every = VillageNeeds.CHECK_EVERY;
		VillageHalls.RADIUS = 18;
		Innkeepers.RADIUS = 18;
		// Only the rounds a test runs (B997): the hall's own timed round falls on a tick set by where the test stands
		// (its position's hash), and on some runs it came inside a test's few ticks, where a baby of its own took the
		// bed the test had just freed.
		VillageNeeds.CHECK_EVERY = Integer.MAX_VALUE;
		Leftovers.after(helper, () -> {
			VillageHalls.RADIUS = hall;
			Innkeepers.RADIUS = inn;
			Innkeepers.STAY = stay;
			VillageNeeds.CHECK_EVERY = every;
		});
		BoundingBox box = BoundingBox.fromCorners(helper.absolutePos(new BlockPos(-2, 0, -2)), helper.absolutePos(new BlockPos(31, 12, 31)));
		Leftovers.after(helper, () -> CampBeds.Data.get(helper.getLevel()).forget(box));
	}

	private static BlockPos bed(GameTestHelper helper, BlockPos foot) {
		helper.setBlock(foot, Blocks.RED_BED.defaultBlockState().setValue(BedBlock.FACING, Direction.SOUTH).setValue(BedBlock.PART, BedPart.FOOT));
		helper.setBlock(foot.south(), Blocks.RED_BED.defaultBlockState().setValue(BedBlock.FACING, Direction.SOUTH).setValue(BedBlock.PART, BedPart.HEAD));
		return helper.absolutePos(foot.south());
	}

	private static BlockPos home(Villager v) {
		return v.getBrain().getMemory(MemoryModuleType.HOME).map(GlobalPos::pos).orElse(null);
	}

	private static List<String> lore(ItemStack stack) {
		ItemLore lore = stack.get(DataComponents.LORE);
		return lore == null ? List.of() : lore.lines().stream().map(Component::getString).toList();
	}

	/**
	 * A founded village: a real settlers' camp (two grown-ups, their two wagon bedrolls), {@code houseBeds} red house
	 * beds in a row, a Storehouse and chest, an inn counter with its keeper and the hall. Returns the mock player.
	 */
	private static ServerPlayer village(GameTestHelper helper, int houseBeds, List<BlockPos> beds) {
		ServerLevel level = helper.getLevel();
		ServerPlayer player = helper.makeMockServerPlayerInLevel();
		List<Villager> settlers = SettlersWagonItem.makeCamp(level, player, helper.absolutePos(CAMP));
		helper.assertTrue(settlers.size() == 2, "setup: settlers " + settlers.size());
		settlers.forEach(v -> v.setNoAi(true));
		for (int i = 0; i < houseBeds; i++) {
			beds.add(bed(helper, new BlockPos(3 + 2 * i, 2, 24)));
		}
		helper.setBlock(new BlockPos(25, 2, 24), ModBlocks.STOREHOUSE);
		helper.setBlock(STORE_CHEST, Blocks.CHEST);
		helper.setBlock(COUNTER, ModBlocks.INN_COUNTER);
		Villager innkeeper = helper.spawn(EntityType.VILLAGER, COUNTER.east().south());
		Jobs.employ(level, innkeeper, helper.absolutePos(COUNTER), ModVillagers.INN_COUNTER_POI, ModVillagers.INNKEEPER);
		innkeeper.setNoAi(true);
		helper.setBlock(HALL, ModBlocks.VILLAGE_HALL);
		return player;
	}

	private static Villager innkeeper(GameTestHelper helper) {
		return helper.getLevel().getEntitiesOfClass(Villager.class, new net.minecraft.world.phys.AABB(helper.absolutePos(COUNTER)).inflate(2),
			v -> v.getVillagerData().getProfession() == ModVillagers.INNKEEPER).get(0);
	}

	private static void stock(GameTestHelper helper) {
		Container chest = helper.getBlockEntity(STORE_CHEST);
		chest.setItem(0, new ItemStack(Items.BREAD, 64));
	}

	/** Every player out of the inn's sight (more than 24 blocks), so a traveller whose stay is over may move on. */
	private static void playersAway(GameTestHelper helper) {
		for (ServerPlayer p : helper.getLevel().players()) {
			if (p.distanceToSqr(helper.absolutePos(COUNTER).getCenter()) < 80 * 80) {
				p.teleportTo(p.getX(), p.getY() + 200, p.getZ());
			}
		}
	}

	/**
	 * N = 3 from both sides: three free house beds take exactly three babies, each in a bed of its own; the fourth baby
	 * isn't born, the inn takes no traveller, and the hall says the village is full. The wagon's two bedrolls never count.
	 */
	//$ gametest_ticks_batch AREA '200' '"qaBedCap1107Three"'
	@GameTest(template = AREA, timeoutTicks = 200, batch = "qaBedCap1107Three")
	public void qaThreeFreeBedsTakeThreeBabiesAndNoFourth(GameTestHelper helper) {
		Leftovers.clear(helper);
		radii(helper);
		helper.setDayTime(1000);
		ServerLevel level = helper.getLevel();
		List<BlockPos> beds = new ArrayList<>();
		village(helper, 3, beds);
		BlockPos hall = helper.absolutePos(HALL);
		helper.runAfterDelay(5, () -> {
			stock(helper);
			helper.assertTrue(VillageHalls.census(level, hall).freeBeds() == 3,
				"setup: the census's free beds with 3 house beds and 2 wagon bedrolls: " + VillageHalls.census(level, hall).freeBeds());
			Set<BlockPos> homes = new HashSet<>();
			for (int i = 0; i < 3; i++) {
				Villager baby = VillageGrowth.grow(level, hall, HAPPY, 0);
				helper.assertTrue(baby != null, "baby " + (i + 1) + " of 3 wasn't born with " + (3 - i) + " free beds");
				baby.setNoAi(true);
				helper.assertTrue(beds.contains(home(baby)), "baby " + (i + 1) + "'s home isn't a house bed: " + home(baby));
				homes.add(home(baby));
			}
			helper.assertTrue(homes.size() == 3, "two babies share a bed: " + homes);
			helper.assertTrue(VillageGrowth.grow(level, hall, HAPPY, 0) == null, "a fourth baby for three beds");
			String state = Innkeepers.tend(level, innkeeper(helper), helper.absolutePos(COUNTER));
			helper.assertTrue(state.equals("no_bed") && Innkeepers.guests(level, helper.absolutePos(COUNTER)).isEmpty(),
				"the inn took a traveller with every house bed taken: " + state);
			helper.assertTrue(VillageHalls.census(level, hall).full(), "the hall doesn't say full with every house bed taken");
			helper.succeed();
		});
	}

	/** N = 0: a village whose only beds are the wagon's bedrolls takes no baby and no traveller, and says it is full. */
	//$ gametest_ticks_batch AREA '200' '"qaBedCap1107None"'
	@GameTest(template = AREA, timeoutTicks = 200, batch = "qaBedCap1107None")
	public void qaOnlyWagonBedrollsTakeNobodyNew(GameTestHelper helper) {
		Leftovers.clear(helper);
		radii(helper);
		helper.setDayTime(1000);
		ServerLevel level = helper.getLevel();
		ServerPlayer player = village(helper, 0, new ArrayList<>());
		BlockPos hall = helper.absolutePos(HALL);
		helper.runAfterDelay(5, () -> {
			stock(helper);
			helper.assertTrue(VillageGrowth.blocker(level, hall, HAPPY, 0) == VillageGrowth.Blocker.NO_BED,
				"the wagon's bedrolls counted for a baby: " + VillageGrowth.blocker(level, hall, HAPPY, 0));
			helper.assertTrue(VillageGrowth.grow(level, hall, HAPPY, 0) == null, "a baby born into a wagon bedroll");
			String state = Innkeepers.tend(level, innkeeper(helper), helper.absolutePos(COUNTER));
			helper.assertTrue(state.equals("no_bed"), "the inn took a traveller into a wagon bedroll: " + state);
			List<String> lines = lore(VillageHallScreen.forTest(player, hall).icon(VillageHallScreen.BEDS));
			helper.assertTrue(lines.contains("Free: 0"), "the Beds button's free line with only bedrolls: " + lines);
			helper.assertTrue(lines.stream().filter(FULL::equals).count() == 1, "the Beds button doesn't say the village is full once: " + lines);
			helper.succeed();
		});
	}

	/** The hall's full line at the boundary: one free bed shows "Free: 1" and no full line; once a baby takes it, both change. */
	//$ gametest_ticks_batch AREA '200' '"qaBedCap1107Line"'
	@GameTest(template = AREA, timeoutTicks = 200, batch = "qaBedCap1107Line")
	public void qaTheFullLineComesWithTheLastBedAndNotBefore(GameTestHelper helper) {
		Leftovers.clear(helper);
		radii(helper);
		helper.setDayTime(1000);
		ServerLevel level = helper.getLevel();
		ServerPlayer player = village(helper, 1, new ArrayList<>());
		BlockPos hall = helper.absolutePos(HALL);
		helper.runAfterDelay(5, () -> {
			stock(helper);
			List<String> before = lore(VillageHallScreen.forTest(player, hall).icon(VillageHallScreen.BEDS));
			helper.assertTrue(before.contains("Free: 1") && !before.contains(FULL), "one free bed: " + before);
			Villager baby = VillageGrowth.grow(level, hall, HAPPY, 0);
			helper.assertTrue(baby != null, "no baby for the one free bed");
			baby.setNoAi(true);
			List<String> after = lore(VillageHallScreen.forTest(player, hall).icon(VillageHallScreen.BEDS));
			helper.assertTrue(after.contains("Free: 0") && after.contains(FULL), "after the baby took the last bed: " + after);
			helper.succeed();
		});
	}

	/** A bed whose newcomer died is free again: the next baby or traveller may have it, and the hall stops saying full. */
	//$ gametest_ticks_batch AREA '200' '"qaBedCap1107Death"'
	@GameTest(template = AREA, timeoutTicks = 200, batch = "qaBedCap1107Death")
	public void qaADeadBabysBedIsFreeAgain(GameTestHelper helper) {
		Leftovers.clear(helper);
		radii(helper);
		helper.setDayTime(1000);
		ServerLevel level = helper.getLevel();
		List<BlockPos> beds = new ArrayList<>();
		village(helper, 1, beds);
		BlockPos hall = helper.absolutePos(HALL);
		helper.runAfterDelay(5, () -> {
			stock(helper);
			Villager baby = VillageGrowth.grow(level, hall, HAPPY, 0);
			helper.assertTrue(baby != null && beds.get(0).equals(home(baby)), "setup: the baby didn't take the house bed");
			baby.setNoAi(true);
			helper.assertTrue(VillageHalls.census(level, hall).full(), "setup: not full with the bed taken");
			baby.kill();
			helper.runAfterDelay(5, () -> {
				helper.assertTrue(!VillageHalls.census(level, hall).full(), "the hall still says full after the bed's baby died");
				helper.assertTrue(Innkeepers.hasFreeBed(level, helper.absolutePos(COUNTER)), "the inn doesn't see the dead baby's bed as free");
				Villager next = VillageGrowth.grow(level, hall, HAPPY, 0);
				helper.assertTrue(next != null && beds.get(0).equals(home(next)), "the next baby didn't get the freed bed: " + (next == null ? "no baby" : home(next)));
				next.setNoAi(true);
				helper.succeed();
			});
		});
	}

	/**
	 * B997: the hall's own round (as every {@code VillageNeeds.CHECK_EVERY} ticks in play) gives a dead baby's freed bed
	 * to the next baby: the bed counts free to the village's own growth too, and once taken the hall says full again.
	 */
	//$ gametest_ticks_batch AREA '200' '"qaBedCap1107Round"'
	@GameTest(template = AREA, timeoutTicks = 200, batch = "qaBedCap1107Round")
	public void qaTheHallsOwnRoundGivesADeadBabysBedToTheNext(GameTestHelper helper) {
		Leftovers.clear(helper);
		radii(helper);
		helper.setDayTime(1000);
		ServerLevel level = helper.getLevel();
		List<BlockPos> beds = new ArrayList<>();
		village(helper, 1, beds);
		BlockPos hall = helper.absolutePos(HALL);
		helper.runAfterDelay(5, () -> {
			stock(helper);
			Villager baby = VillageGrowth.grow(level, hall, HAPPY, 0);
			helper.assertTrue(baby != null && beds.get(0).equals(home(baby)), "setup: the baby didn't take the house bed");
			baby.setNoAi(true);
			baby.kill();
			helper.assertTrue(!VillageHalls.census(level, hall).full(), "the hall still says full the tick the bed's baby died");
			((VillageHallBlockEntity) level.getBlockEntity(hall)).setNeeds(null); // the hall's round on its next tick
			helper.runAfterDelay(2, () -> {
				List<Villager> babies = level.getEntitiesOfClass(Villager.class, VillageHalls.area(hall), v -> v.isAlive() && v.isBaby());
				helper.assertTrue(babies.size() == 1 && beds.get(0).equals(home(babies.get(0))),
					"the hall's own round didn't give the freed bed to a new baby: " + babies.stream().map(QaBedCap1107GameTests::home).toList());
				babies.get(0).setNoAi(true);
				helper.assertTrue(VillageHalls.census(level, hall).full(), "the hall doesn't say full once its own baby took the freed bed");
				helper.succeed();
			});
		});
	}

	/**
	 * A traveller moves on through the inn's own round once the stay is over and nobody watches, and the bed is free for
	 * the next newcomer; while a player stands by the traveller stays and keeps the bed.
	 */
	//$ gametest_ticks_batch AREA '200' '"qaBedCap1107Leave"'
	@GameTest(template = AREA, timeoutTicks = 200, batch = "qaBedCap1107Leave")
	public void qaATravellerMovesOnInTheInnsRoundAndFreesTheBed(GameTestHelper helper) {
		Leftovers.clear(helper);
		radii(helper);
		helper.setDayTime(1000);
		ServerLevel level = helper.getLevel();
		List<BlockPos> beds = new ArrayList<>();
		ServerPlayer player = village(helper, 1, beds);
		BlockPos hall = helper.absolutePos(HALL);
		BlockPos at = helper.absolutePos(COUNTER);
		helper.runAfterDelay(5, () -> {
			stock(helper);
			String state = Innkeepers.tend(level, innkeeper(helper), at);
			List<Villager> guests = Innkeepers.guests(level, at);
			helper.assertTrue(state.equals("hosting") && guests.size() == 1, "setup: " + state + ", " + guests.size() + " guests");
			Villager guest = guests.get(0);
			guest.setNoAi(true);
			helper.assertTrue(beds.get(0).equals(home(guest)), "the traveller's home: " + home(guest));
			Innkeepers.STAY = 1;
			helper.setDayTime(7000); // past the morning: no new traveller comes for the freed bed in this test
			helper.runAfterDelay(3, () -> {
				// a player beside the traveller: the stay is over, but nobody leaves in sight of a player
				player.teleportTo(guest.getX(), guest.getY(), guest.getZ() + 2);
				Innkeepers.tend(level, innkeeper(helper), at);
				helper.assertTrue(guest.isAlive() && beds.get(0).equals(home(guest)), "the traveller left (or lost the bed) in sight of a player");
				helper.assertTrue(VillageGrowth.blocker(level, hall, HAPPY, 0) == VillageGrowth.Blocker.NO_BED, "the staying traveller's bed counted free");
				playersAway(helper);
				Innkeepers.tend(level, innkeeper(helper), at);
				helper.assertTrue(!guest.isAlive() || guest.isRemoved(), "the traveller stayed after the stay was over, nobody watching");
				helper.assertTrue(VillageGrowth.blocker(level, hall, HAPPY, 0) != VillageGrowth.Blocker.NO_BED,
					"the bed stayed taken after the traveller moved on: " + VillageGrowth.blocker(level, hall, HAPPY, 0));
				helper.assertTrue(!VillageHalls.census(level, hall).full(), "the hall still says full after the traveller moved on");
				helper.succeed();
			});
		});
	}
}
