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
import io.github.jcondedata.aliveworkplace.work.ChoiceMenu;
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
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.Container;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.ItemLore;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BedPart;
import net.minecraft.world.level.levelgen.structure.BoundingBox;

/**
 * B206, B207: the settlers' wagon beds are for the road. Once a Village Hall stands, nobody keeps one as their home and
 * the hall doesn't count them; newcomers (babies, travellers) move in only on a free bed in the village's houses, each
 * taking theirs at once, so a village with N free beds takes in at most N; the hall says when the village is full.
 */
public class WagonBedsGameTests implements net.fabricmc.fabric.api.gametest.v1.FabricGameTest {
	private static final String AREA = "aliveworkplace_test:huge_area";
	private static final BlockPos CAMP = new BlockPos(15, 2, 4);
	private static final BlockPos HALL = new BlockPos(15, 2, 20);

	private static void radius(GameTestHelper helper, int r) {
		int radius = VillageHalls.RADIUS;
		VillageHalls.RADIUS = r;
		Leftovers.after(helper, () -> VillageHalls.RADIUS = radius);
		forgetCamps(helper);
	}

	/** The beds this test lists as wagon beds come off the list afterwards (a later test's bed at the same spot is its own). */
	private static void forgetCamps(GameTestHelper helper) {
		BoundingBox box = BoundingBox.fromCorners(helper.absolutePos(new BlockPos(-2, 0, -2)), helper.absolutePos(new BlockPos(31, 12, 31)));
		Leftovers.after(helper, () -> CampBeds.Data.get(helper.getLevel()).forget(box));
	}

	/** A bed (foot at {@code foot}, head south of it) of {@code bed}'s colour; its head, absolute. */
	private static BlockPos bed(GameTestHelper helper, BlockPos foot, net.minecraft.world.level.block.Block bed) {
		helper.setBlock(foot, bed.defaultBlockState().setValue(BedBlock.FACING, Direction.SOUTH).setValue(BedBlock.PART, BedPart.FOOT));
		helper.setBlock(foot.south(), bed.defaultBlockState().setValue(BedBlock.FACING, Direction.SOUTH).setValue(BedBlock.PART, BedPart.HEAD));
		return helper.absolutePos(foot.south());
	}

	/** The heads of the beds in the test's area, absolute. */
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

	/**
	 * B207: the settlers made camp and took the wagon's two bedrolls as their homes, one asleep in it. The Village Hall goes
	 * up: at its round they get up and forget the wagon, its beds are held for nobody, and the hall counts only the house's
	 * bed, saying the wagon's two aren't counted.
	 */
	//$ gametest_ticks_batch AREA '200' '"settlersLeaveTheWagonOnceTheHallStands"'
	@GameTest(template = AREA, timeoutTicks = 200, batch = "settlersLeaveTheWagonOnceTheHallStands")
	public void settlersLeaveTheWagonOnceTheHallStands(GameTestHelper helper) {
		Leftovers.clear(helper);
		radius(helper, 18);
		ServerLevel level = helper.getLevel();
		ServerPlayer player = helper.makeMockServerPlayerInLevel();
		List<Villager> settlers = SettlersWagonItem.makeCamp(level, player, helper.absolutePos(CAMP));
		helper.assertTrue(settlers.size() == 2, "settlers: " + settlers.size());
		settlers.forEach(v -> v.setNoAi(true));
		List<BlockPos> wagon = bedHeads(helper);
		helper.assertTrue(wagon.size() == 2 && wagon.stream().allMatch(b -> CampBeds.isWagonBed(level, b)), "wagon beds: " + wagon);
		BlockPos house = bed(helper, new BlockPos(5, 2, 25), Blocks.RED_BED);
		helper.runAfterDelay(3, () -> {
			helper.assertTrue(!CampBeds.isWagonBed(level, house), "the house's bed is a wagon bed");
			// as an older camp left them: each settler's home a wagon bed, the first asleep in it
			for (int i = 0; i < 2; i++) {
				helper.assertTrue(CampBeds.claim(level, settlers.get(i), wagon.get(i)), "settler " + i + " couldn't take a wagon bed");
			}
			settlers.get(0).startSleeping(wagon.get(0));
			helper.setBlock(HALL, ModBlocks.VILLAGE_HALL);
			helper.succeedWhen(() -> {
				for (Villager v : settlers) {
					helper.assertTrue(home(v) == null || !wagon.contains(home(v)), v.getName().getString() + "'s home is still the wagon: " + home(v));
				}
				helper.assertTrue(!settlers.get(0).isSleeping(), "still asleep in the wagon");
				for (BlockPos b : wagon) {
					helper.assertTrue(level.getPoiManager().getFreeTickets(b) == 0, "a wagon bed is free to claim: " + b);
				}
				VillageHalls.Census census = VillageHalls.census(level, helper.absolutePos(HALL));
				helper.assertTrue(census.beds() == 1 && census.freeBeds() == 1 && census.wagonBeds() == 2,
					"beds " + census.beds() + ", free " + census.freeBeds() + ", wagon " + census.wagonBeds());
				ChoiceMenu menu = VillageHallScreen.forTest(player, helper.absolutePos(HALL));
				List<String> lines = lore(menu.icon(VillageHallScreen.BEDS));
				helper.assertTrue(lines.contains("Not counted: 2 wagon beds (for the road, not homes)") && lines.contains("Free: 1")
					&& !lines.contains("The village is full: nobody new moves in until a bed is free"), "the Beds lines: " + lines);
			});
		});
	}

	/**
	 * B207, an older save: a camp placed before the wagon beds were listed (a brown bedroll by its campfire, hay bale and
	 * the camp's Blueprint Table), a villager homed in it. The hall's round knows the bedroll by its look, puts it on the list and moves the villager
	 * out; a red bed in a house beside it stays a home. The list survives a save and reload.
	 */
	//$ gametest_ticks_batch AREA '200' '"anOldCampsBedrollIsNoHomeOnceTheHallStands"'
	@GameTest(template = AREA, timeoutTicks = 200, batch = "anOldCampsBedrollIsNoHomeOnceTheHallStands")
	public void anOldCampsBedrollIsNoHomeOnceTheHallStands(GameTestHelper helper) {
		Leftovers.clear(helper);
		radius(helper, 18);
		ServerLevel level = helper.getLevel();
		BlockPos roll = bed(helper, new BlockPos(6, 2, 7), Blocks.BROWN_BED);
		helper.setBlock(new BlockPos(10, 2, 6), Blocks.CAMPFIRE);
		helper.setBlock(new BlockPos(12, 2, 9), Blocks.HAY_BLOCK);
		helper.setBlock(new BlockPos(12, 2, 4), ModBlocks.BLUEPRINT_TABLE); // every camp brings its builder's table (B715)
		BlockPos house = bed(helper, new BlockPos(20, 2, 22), Blocks.RED_BED);
		Villager settler = helper.spawn(EntityType.VILLAGER, new BlockPos(7, 2, 10));
		settler.setNoAi(true);
		helper.assertTrue(!CampBeds.Data.get(level).has(roll), "the old bedroll is listed already");
		helper.runAfterDelay(3, () -> {
			helper.assertTrue(CampBeds.claim(level, settler, roll), "couldn't take the bedroll");
			helper.setBlock(HALL, ModBlocks.VILLAGE_HALL);
			helper.succeedWhen(() -> {
				helper.assertTrue(home(settler) == null, "still homed in the bedroll: " + home(settler));
				helper.assertTrue(level.getPoiManager().getFreeTickets(roll) == 0, "the bedroll is free to claim");
				helper.assertTrue(level.getPoiManager().getFreeTickets(house) == 1 && !CampBeds.isWagonBed(level, house), "the house's bed isn't a home");
				CampBeds.Data data = CampBeds.Data.get(level);
				helper.assertTrue(data.has(roll), "the old bedroll isn't on the list");
				CampBeds.Data reloaded = CampBeds.Data.load(data.save(new CompoundTag(), level.registryAccess()), level.registryAccess());
				helper.assertTrue(reloaded.has(roll) && !reloaded.has(house), "after a reload: bedroll " + reloaded.has(roll) + ", house " + reloaded.has(house));
				VillageHalls.Census census = VillageHalls.census(level, helper.absolutePos(HALL));
				helper.assertTrue(census.beds() == 1 && census.wagonBeds() == 1, "beds " + census.beds() + ", wagon " + census.wagonBeds());
			});
		});
	}

	/**
	 * B715, a list saved before the fix: the old look had listed a cabin's brown bed (a campfire and a hay bale by it, no
	 * camp's table) along with an old camp's green bedroll (its campfire, hay and Blueprint Table). At the hall's round the
	 * cabin's bed is let go and stays its resident's home and a counted bed; the bedroll stays a wagon bed. The list says
	 * so after a save and reload.
	 */
	//$ gametest_ticks_batch AREA '200' '"anOlderListLetsACabinsBedGoAndKeepsTheCamps"'
	@GameTest(template = AREA, timeoutTicks = 200, batch = "anOlderListLetsACabinsBedGoAndKeepsTheCamps")
	public void anOlderListLetsACabinsBedGoAndKeepsTheCamps(GameTestHelper helper) {
		Leftovers.clear(helper);
		radius(helper, 18);
		ServerLevel level = helper.getLevel();
		CampBeds.Data live = CampBeds.Data.get(level);
		Leftovers.after(helper, () -> level.getDataStorage().set(CampBeds.Data.NAME, live));
		BlockPos cabin = bed(helper, new BlockPos(6, 2, 10), Blocks.BROWN_BED);
		helper.setBlock(new BlockPos(12, 2, 10), Blocks.CAMPFIRE); // the cabin's cooking fire
		helper.setBlock(new BlockPos(0, 2, 10), Blocks.HAY_BLOCK);
		BlockPos roll = bed(helper, new BlockPos(22, 2, 6), Blocks.GREEN_BED);
		helper.setBlock(new BlockPos(24, 2, 3), Blocks.CAMPFIRE);
		helper.setBlock(new BlockPos(26, 2, 8), Blocks.HAY_BLOCK);
		helper.setBlock(new BlockPos(27, 2, 4), ModBlocks.BLUEPRINT_TABLE);
		CompoundTag before = new CompoundTag(); // the list as a save from before B715 has it: no version, both beds on it
		before.putLongArray("beds", new long[] {cabin.asLong(), roll.asLong()});
		CampBeds.Data old = CampBeds.Data.load(before, level.registryAccess());
		helper.assertTrue(old.unverified(cabin) && old.unverified(roll) && !old.has(cabin), "an older list's beds aren't looked at again");
		level.getDataStorage().set(CampBeds.Data.NAME, old);
		Villager resident = helper.spawn(EntityType.VILLAGER, new BlockPos(7, 2, 8));
		resident.setNoAi(true);
		helper.runAfterDelay(3, () -> {
			helper.assertTrue(CampBeds.claim(level, resident, cabin), "setup: the resident couldn't take the cabin's bed");
			helper.setBlock(HALL, ModBlocks.VILLAGE_HALL);
			helper.runAfterDelay(60, () -> {
				CampBeds.Data data = CampBeds.Data.get(level);
				helper.assertTrue(!CampBeds.isWagonBed(level, cabin) && !data.has(cabin) && !data.unverified(cabin), "the cabin's bed is still listed");
				helper.assertTrue(cabin.equals(home(resident)), "the cabin's resident lost his home: " + home(resident));
				helper.assertTrue(CampBeds.isWagonBed(level, roll) && data.has(roll), "the old camp's bedroll was let go");
				helper.assertTrue(level.getPoiManager().getFreeTickets(roll) == 0, "the bedroll is free to claim");
				CampBeds.Data reloaded = CampBeds.Data.load(data.save(new CompoundTag(), level.registryAccess()), level.registryAccess());
				helper.assertTrue(reloaded.has(roll) && !reloaded.unverified(roll) && !reloaded.has(cabin) && !reloaded.unverified(cabin),
					"after a reload: bedroll " + reloaded.has(roll) + ", cabin " + reloaded.has(cabin) + "/" + reloaded.unverified(cabin));
				VillageHalls.Census census = VillageHalls.census(level, helper.absolutePos(HALL));
				helper.assertTrue(census.beds() == 1 && census.wagonBeds() == 1, "beds " + census.beds() + ", wagon " + census.wagonBeds());
				helper.succeed();
			});
		});
	}

	/**
	 * B206: a founded village (settlers' wagon, two beds in its houses, food and a happy mood) has babies only while a bed
	 * in its houses is free: two babies, each in their own house bed at once, never a wagon bed; the third waits for a bed,
	 * and the hall says the village is full.
	 */
	//$ gametest_ticks_batch AREA '200' '"twoFreeBedsTakeInTwoNewcomers"'
	@GameTest(template = AREA, timeoutTicks = 200, batch = "twoFreeBedsTakeInTwoNewcomers")
	public void twoFreeBedsTakeInTwoNewcomers(GameTestHelper helper) {
		Leftovers.clear(helper);
		radius(helper, 18);
		ServerLevel level = helper.getLevel();
		ServerPlayer player = helper.makeMockServerPlayerInLevel();
		List<Villager> settlers = SettlersWagonItem.makeCamp(level, player, helper.absolutePos(CAMP));
		helper.assertTrue(settlers.size() == 2, "settlers: " + settlers.size());
		settlers.forEach(v -> v.setNoAi(true));
		List<BlockPos> wagon = bedHeads(helper);
		Set<BlockPos> houses = new HashSet<>(List.of(bed(helper, new BlockPos(4, 2, 24), Blocks.RED_BED), bed(helper, new BlockPos(7, 2, 24), Blocks.RED_BED)));
		helper.setBlock(new BlockPos(25, 2, 24), ModBlocks.STOREHOUSE);
		helper.setBlock(new BlockPos(25, 2, 26), Blocks.CHEST);
		Container chest = helper.getBlockEntity(new BlockPos(25, 2, 26));
		helper.setBlock(HALL, ModBlocks.VILLAGE_HALL);
		BlockPos hall = helper.absolutePos(HALL);
		VillageNeeds.Needs happy = new VillageNeeds.Needs(2, 2, 2, 2, 2, 0, 0, 1f);
		helper.runAfterDelay(5, () -> {
			chest.setItem(0, new ItemStack(Items.BREAD, 64)); // (after the hall's first round, which would grow the village itself)
			List<Villager> babies = new ArrayList<>();
			for (int i = 0; i < 3; i++) {
				Villager baby = VillageGrowth.grow(level, hall, happy, 0);
				if (baby != null) {
					baby.setNoAi(true);
					babies.add(baby);
				}
			}
			helper.assertTrue(babies.size() == 2, babies.size() + " babies with two free beds (the wagon's two aren't homes)");
			Set<BlockPos> homes = new HashSet<>();
			for (Villager baby : babies) {
				helper.assertTrue(home(baby) != null && houses.contains(home(baby)), "a baby's home: " + home(baby) + ", not a house bed " + houses);
				homes.add(home(baby));
			}
			helper.assertTrue(homes.size() == 2, "two babies in one bed: " + homes);
			helper.assertTrue(wagon.stream().noneMatch(homes::contains), "a baby in the wagon");
			helper.assertTrue(VillageGrowth.blocker(level, hall, happy, 0) == VillageGrowth.Blocker.NO_BED, "the third: " + VillageGrowth.blocker(level, hall, happy, 0));
			VillageHalls.Census census = VillageHalls.census(level, hall);
			helper.assertTrue(census.full() && census.beds() == 2 && census.freeBeds() == 0, "beds " + census.beds() + ", free " + census.freeBeds());
			List<String> lines = lore(VillageHallScreen.forTest(player, hall).icon(VillageHallScreen.BEDS));
			helper.assertTrue(lines.contains("The village is full: nobody new moves in until a bed is free") && lines.contains("Free: 0"),
				"the Beds lines: " + lines);
			helper.succeed();
		});
	}

	/**
	 * B206: an inn whose only free bed is a camp's bedroll takes no traveller in the morning; with one free bed in a house,
	 * one comes, that bed theirs at once, and the inn takes no second for it. When the traveller moves on, the bed is free
	 * again.
	 */
	//$ gametest_ticks_batch AREA '100' '"theInnTakesATravellerOnlyIntoAFreeHouseBed"'
	@GameTest(template = AREA, timeoutTicks = 100, batch = "theInnTakesATravellerOnlyIntoAFreeHouseBed")
	public void theInnTakesATravellerOnlyIntoAFreeHouseBed(GameTestHelper helper) {
		Leftovers.clear(helper);
		int innRadius = Innkeepers.RADIUS;
		Innkeepers.RADIUS = 14;
		Leftovers.after(helper, () -> Innkeepers.RADIUS = innRadius);
		forgetCamps(helper);
		helper.setDayTime(1000);
		ServerLevel level = helper.getLevel();
		BlockPos counter = new BlockPos(15, 2, 15);
		helper.setBlock(counter, ModBlocks.INN_COUNTER);
		Villager innkeeper = helper.spawn(EntityType.VILLAGER, new BlockPos(16, 2, 16));
		Jobs.employ(level, innkeeper, helper.absolutePos(counter), ModVillagers.INN_COUNTER_POI, ModVillagers.INNKEEPER);
		innkeeper.setNoAi(true);
		BlockPos roll = bed(helper, new BlockPos(8, 2, 8), Blocks.GREEN_BED);
		helper.setBlock(new BlockPos(11, 2, 8), Blocks.CAMPFIRE);
		helper.setBlock(new BlockPos(12, 2, 11), Blocks.HAY_BLOCK);
		helper.setBlock(new BlockPos(4, 2, 4), ModBlocks.BLUEPRINT_TABLE); // the old camp's builder's table (B715)
		helper.runAfterDelay(3, () -> {
			BlockPos at = helper.absolutePos(counter);
			helper.assertTrue(CampBeds.isWagonBed(level, roll), "the bedroll isn't known for one");
			String state = Innkeepers.tend(level, innkeeper, at);
			helper.assertTrue(state.equals("no_bed") && Innkeepers.guests(level, at).isEmpty(), "with only a bedroll free: " + state);
			BlockPos house = bed(helper, new BlockPos(20, 2, 20), Blocks.RED_BED);
			helper.runAfterDelay(3, () -> {
				String hosting = Innkeepers.tend(level, innkeeper, at);
				List<Villager> guests = Innkeepers.guests(level, at);
				helper.assertTrue(hosting.equals("hosting") && guests.size() == 1, "with a free house bed: " + hosting + ", " + guests.size() + " guests");
				Villager guest = guests.get(0);
				guest.setNoAi(true);
				helper.assertTrue(house.equals(home(guest)), "the traveller's home: " + home(guest));
				String full = Innkeepers.tend(level, innkeeper, at);
				helper.assertTrue(full.equals("no_bed") && Innkeepers.guests(level, at).size() == 1, "the bed counted free twice: " + full);
				Innkeepers.leave(level, guest);
				helper.assertTrue(level.getPoiManager().getFreeTickets(house) == 1, "the bed stayed taken after the traveller left");
				helper.succeed();
			});
		});
	}

	/**
	 * The list of wagon beds holds a placed camp's bedrolls (their heads) and nothing else, and survives a save and reload;
	 * another bed put where a bedroll stood is no wagon bed.
	 */
	//$ gametest_ticks_batch AREA '40' '"campBedsAreListedWhereTheyArePlaced"'
	@GameTest(template = AREA, timeoutTicks = 40, batch = "campBedsAreListedWhereTheyArePlaced")
	public void campBedsAreListedWhereTheyArePlaced(GameTestHelper helper) {
		Leftovers.clear(helper);
		forgetCamps(helper);
		ServerLevel level = helper.getLevel();
		BlockPos head = bed(helper, new BlockPos(5, 2, 5), Blocks.BROWN_BED);
		BlockPos red = bed(helper, new BlockPos(7, 2, 5), Blocks.RED_BED);
		BlockPos outside = bed(helper, new BlockPos(20, 2, 20), Blocks.BROWN_BED);
		BlockPos a = helper.absolutePos(new BlockPos(3, 2, 3));
		BlockPos b = helper.absolutePos(new BlockPos(8, 3, 8));
		helper.assertTrue(CampBeds.record(level, BoundingBox.fromCorners(a, b)) == 1, "beds listed: not just the bedroll's head");
		CampBeds.Data data = CampBeds.Data.get(level);
		helper.assertTrue(data.has(head) && !data.has(head.north()) && !data.has(red) && !data.has(outside), "listed: head " + data.has(head) + ", foot "
			+ data.has(head.north()) + ", a red bed " + data.has(red) + ", outside " + data.has(outside));
		helper.assertTrue(CampBeds.isWagonBed(level, head) && !CampBeds.isWagonBed(level, red), "a wagon bed: bedroll " + CampBeds.isWagonBed(level, head)
			+ ", red bed " + CampBeds.isWagonBed(level, red));
		// a red bed put where the bedroll stood is a bed like any other
		helper.setBlock(new BlockPos(5, 2, 5), Blocks.AIR);
		bed(helper, new BlockPos(5, 2, 5), Blocks.RED_BED);
		helper.assertTrue(!CampBeds.isWagonBed(level, head), "a red bed where the bedroll was is a wagon bed");
		CampBeds.Data reloaded = CampBeds.Data.load(data.save(new CompoundTag(), level.registryAccess()), level.registryAccess());
		helper.assertTrue(reloaded.has(head) && !reloaded.has(outside), "lost in a reload");
		helper.succeed();
	}
}
