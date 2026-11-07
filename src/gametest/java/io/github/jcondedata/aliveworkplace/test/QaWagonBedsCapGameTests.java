package io.github.jcondedata.aliveworkplace.test;

import io.github.jcondedata.aliveworkplace.camp.CampBeds;
import io.github.jcondedata.aliveworkplace.camp.SettlersWagonItem;
import io.github.jcondedata.aliveworkplace.hall.VillageGrowth;
import io.github.jcondedata.aliveworkplace.hall.VillageHallScreen;
import io.github.jcondedata.aliveworkplace.hall.VillageHalls;
import io.github.jcondedata.aliveworkplace.hall.VillageNeeds;
import io.github.jcondedata.aliveworkplace.inn.Innkeepers;
import io.github.jcondedata.aliveworkplace.registry.ModBlocks;
import io.github.jcondedata.aliveworkplace.registry.ModVillagers;
import io.github.jcondedata.aliveworkplace.work.Jobs;
import io.github.jcondedata.aliveworkplace.work.JobPlate;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.Container;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.npc.VillagerProfession;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.ItemLore;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BedPart;
import net.minecraft.world.level.levelgen.structure.BoundingBox;

/**
 * QA (lane-c-1101-2153) for B206, B207 and B221, written from their specs: one free bed is one newcomer whichever way
 * they come (a baby or an inn's traveller), a traveller who moves on gives the bed back to the next newcomer, settlers
 * keep the wagon until a hall stands, the hall's wagon-bed line counts right, and the job plate names every level.
 */
public class QaWagonBedsCapGameTests implements net.fabricmc.fabric.api.gametest.v1.FabricGameTest {
	private static final String AREA = "aliveworkplace_test:huge_area";
	private static final String SMALL = "aliveworkplace_test:build_area";
	private static final BlockPos CAMP = new BlockPos(15, 2, 4);
	private static final BlockPos HALL = new BlockPos(15, 2, 20);
	private static final VillageNeeds.Needs HAPPY = new VillageNeeds.Needs(2, 2, 2, 2, 2, 0, 0, 1f);

	private static void radii(GameTestHelper helper) {
		int hall = VillageHalls.RADIUS;
		int inn = Innkeepers.RADIUS;
		VillageHalls.RADIUS = 18;
		Innkeepers.RADIUS = 18;
		Leftovers.after(helper, () -> {
			VillageHalls.RADIUS = hall;
			Innkeepers.RADIUS = inn;
		});
		BoundingBox box = BoundingBox.fromCorners(helper.absolutePos(new BlockPos(-2, 0, -2)), helper.absolutePos(new BlockPos(31, 12, 31)));
		Leftovers.after(helper, () -> CampBeds.Data.get(helper.getLevel()).forget(box));
	}

	private static BlockPos bed(GameTestHelper helper, BlockPos foot, net.minecraft.world.level.block.Block bed) {
		helper.setBlock(foot, bed.defaultBlockState().setValue(BedBlock.FACING, Direction.SOUTH).setValue(BedBlock.PART, BedPart.FOOT));
		helper.setBlock(foot.south(), bed.defaultBlockState().setValue(BedBlock.FACING, Direction.SOUTH).setValue(BedBlock.PART, BedPart.HEAD));
		return helper.absolutePos(foot.south());
	}

	private static List<BlockPos> bedHeads(GameTestHelper helper) {
		List<BlockPos> out = new ArrayList<>();
		for (BlockPos p : BlockPos.betweenClosed(helper.absolutePos(new BlockPos(0, 1, 0)), helper.absolutePos(new BlockPos(29, 10, 29)))) {
			BlockState state = helper.getLevel().getBlockState(p);
			if (state.is(BlockTags.BEDS) && state.getValue(BedBlock.PART) == BedPart.HEAD) {
				out.add(p.immutable());
			}
		}
		return out;
	}

	private static BlockPos home(Villager v) {
		return v.getBrain().getMemory(MemoryModuleType.HOME).map(GlobalPos::pos).orElse(null);
	}

	private static List<String> lore(ItemStack stack) {
		ItemLore lore = stack.get(DataComponents.LORE);
		return lore == null ? List.of() : lore.lines().stream().map(Component::getString).toList();
	}

	/** A founded village with one house bed, a store of bread, two grown-ups, an inn and its keeper; the hall's absolute spot. */
	private static BlockPos village(GameTestHelper helper, List<BlockPos> houseBeds, BlockPos counter) {
		ServerLevel level = helper.getLevel();
		ServerPlayer player = helper.makeMockServerPlayerInLevel();
		List<Villager> settlers = SettlersWagonItem.makeCamp(level, player, helper.absolutePos(CAMP));
		helper.assertTrue(settlers.size() == 2, "settlers: " + settlers.size());
		settlers.forEach(v -> v.setNoAi(true));
		houseBeds.add(bed(helper, new BlockPos(4, 2, 24), Blocks.RED_BED));
		helper.setBlock(new BlockPos(25, 2, 24), ModBlocks.STOREHOUSE);
		helper.setBlock(new BlockPos(25, 2, 26), Blocks.CHEST);
		helper.setBlock(counter, ModBlocks.INN_COUNTER);
		Villager innkeeper = helper.spawn(EntityType.VILLAGER, counter.east().south());
		Jobs.employ(level, innkeeper, helper.absolutePos(counter), ModVillagers.INN_COUNTER_POI, ModVillagers.INNKEEPER);
		innkeeper.setNoAi(true);
		helper.setBlock(HALL, ModBlocks.VILLAGE_HALL);
		return helper.absolutePos(HALL);
	}

	private static Villager innkeeper(GameTestHelper helper, BlockPos counter) {
		return helper.getLevel().getEntitiesOfClass(Villager.class, new net.minecraft.world.phys.AABB(helper.absolutePos(counter)).inflate(2),
			v -> v.getVillagerData().getProfession() == ModVillagers.INNKEEPER).get(0);
	}

	/**
	 * B206, two ways in sharing one bed: the village's one free house bed goes to a baby, so the inn takes no traveller
	 * in the morning (the wagon's bedrolls don't count), and the hall says the village is full.
	 */
	//$ gametest_ticks_batch AREA '200' '"qaABabyTakesTheLastBedSoTheInnTakesNoTraveller"'
	@GameTest(template = AREA, timeoutTicks = 200, batch = "qaABabyTakesTheLastBedSoTheInnTakesNoTraveller")
	public void qaABabyTakesTheLastBedSoTheInnTakesNoTraveller(GameTestHelper helper) {
		Leftovers.clear(helper);
		radii(helper);
		helper.setDayTime(1000);
		ServerLevel level = helper.getLevel();
		List<BlockPos> houses = new ArrayList<>();
		BlockPos counter = new BlockPos(10, 2, 18);
		BlockPos hall = village(helper, houses, counter);
		helper.runAfterDelay(5, () -> {
			Container chest = helper.getBlockEntity(new BlockPos(25, 2, 26));
			chest.setItem(0, new ItemStack(Items.BREAD, 64));
			helper.assertTrue(Innkeepers.hasFreeBed(level, helper.absolutePos(counter)), "setup: the inn sees no free bed before the baby");
			Villager baby = VillageGrowth.grow(level, hall, HAPPY, 0);
			helper.assertTrue(baby != null, "no baby with a free house bed and a full store");
			baby.setNoAi(true);
			helper.assertTrue(houses.get(0).equals(home(baby)), "the baby's home: " + home(baby));
			BlockPos at = helper.absolutePos(counter);
			String state = Innkeepers.tend(level, innkeeper(helper, counter), at);
			helper.assertTrue(state.equals("no_bed") && Innkeepers.guests(level, at).isEmpty(),
				"the baby's bed counted free for a traveller too: " + state + ", " + Innkeepers.guests(level, at).size() + " guests");
			helper.assertTrue(VillageGrowth.grow(level, hall, HAPPY, 0) == null, "a second baby for one bed");
			helper.assertTrue(VillageHalls.census(level, hall).full(), "the hall doesn't say the village is full");
			helper.succeed();
		});
	}

	/**
	 * B206 the other way round: a traveller takes the one free house bed, so no baby comes for it; when the traveller
	 * moves on, the bed is free again and a baby can have it.
	 */
	//$ gametest_ticks_batch AREA '200' '"qaATravellerTakesTheLastBedThenGivesItBack"'
	@GameTest(template = AREA, timeoutTicks = 200, batch = "qaATravellerTakesTheLastBedThenGivesItBack")
	public void qaATravellerTakesTheLastBedThenGivesItBack(GameTestHelper helper) {
		Leftovers.clear(helper);
		radii(helper);
		helper.setDayTime(1000);
		ServerLevel level = helper.getLevel();
		List<BlockPos> houses = new ArrayList<>();
		BlockPos counter = new BlockPos(10, 2, 18);
		BlockPos hall = village(helper, houses, counter);
		helper.runAfterDelay(5, () -> {
			Container chest = helper.getBlockEntity(new BlockPos(25, 2, 26));
			chest.setItem(0, new ItemStack(Items.BREAD, 64));
			BlockPos at = helper.absolutePos(counter);
			String state = Innkeepers.tend(level, innkeeper(helper, counter), at);
			List<Villager> guests = Innkeepers.guests(level, at);
			helper.assertTrue(state.equals("hosting") && guests.size() == 1, "with one free house bed: " + state + ", " + guests.size() + " guests");
			Villager guest = guests.get(0);
			guest.setNoAi(true);
			helper.assertTrue(houses.get(0).equals(home(guest)), "the traveller's home: " + home(guest));
			helper.assertTrue(VillageGrowth.blocker(level, hall, HAPPY, 0) == VillageGrowth.Blocker.NO_BED,
				"the traveller's bed counted free for a baby: " + VillageGrowth.blocker(level, hall, HAPPY, 0));
			Innkeepers.leave(level, guest);
			helper.assertTrue(VillageGrowth.blocker(level, hall, HAPPY, 0) != VillageGrowth.Blocker.NO_BED,
				"the bed stayed taken after the traveller moved on");
			helper.assertTrue(!VillageHalls.census(level, hall).full(), "the hall still says full after the traveller left");
			helper.succeed();
		});
	}

	/** B207: before a Village Hall stands, the settlers keep the wagon's bedrolls as their homes (they sleep on the road). */
	//$ gametest_ticks_batch AREA '200' '"qaBeforeAHallStandsSettlersKeepTheWagon"'
	@GameTest(template = AREA, timeoutTicks = 200, batch = "qaBeforeAHallStandsSettlersKeepTheWagon")
	public void qaBeforeAHallStandsSettlersKeepTheWagon(GameTestHelper helper) {
		Leftovers.clear(helper);
		radii(helper);
		ServerLevel level = helper.getLevel();
		ServerPlayer player = helper.makeMockServerPlayerInLevel();
		List<Villager> settlers = SettlersWagonItem.makeCamp(level, player, helper.absolutePos(CAMP));
		helper.assertTrue(settlers.size() == 2, "settlers: " + settlers.size());
		settlers.forEach(v -> v.setNoAi(true));
		List<BlockPos> wagon = bedHeads(helper);
		helper.runAfterDelay(3, () -> {
			for (int i = 0; i < 2; i++) {
				helper.assertTrue(CampBeds.claim(level, settlers.get(i), wagon.get(i)), "settler " + i + " couldn't take a wagon bed");
			}
			helper.runAfterDelay(150, () -> {
				for (int i = 0; i < 2; i++) {
					helper.assertTrue(wagon.get(i).equals(home(settlers.get(i))), "a settler lost the wagon before any hall stood: " + home(settlers.get(i)));
				}
				helper.succeed();
			});
		});
	}

	/** B221: the plate names each of the five levels, and follows a level-up at once. */
	//$ gametest SMALL
	@GameTest(template = SMALL)
	public void qaThePlateNamesEveryLevelAndFollowsALevelUp(GameTestHelper helper) {
		Villager v = helper.spawn(EntityType.VILLAGER, 2, 2, 2);
		v.setNoAi(true);
		String[] words = {"Novice", "Apprentice", "Journeyman", "Expert", "Master"};
		for (int lvl = 1; lvl <= 5; lvl++) {
			v.setVillagerData(v.getVillagerData().setProfession(VillagerProfession.LIBRARIAN).setLevel(lvl));
			String plate = JobPlate.plate(v, v.getDisplayName()).getString();
			helper.assertTrue(plate.equals("Librarian, " + words[lvl - 1]), "level " + lvl + ": " + plate);
		}
		v.setVillagerData(v.getVillagerData().setProfession(VillagerProfession.NONE).setLevel(1));
		helper.assertTrue(!JobPlate.shows(v) && JobPlate.plate(v, v.getDisplayName()).getString().equals("Villager"),
			"a villager who lost the job keeps a job plate: " + JobPlate.plate(v, v.getDisplayName()).getString());
		helper.succeed();
	}
}
