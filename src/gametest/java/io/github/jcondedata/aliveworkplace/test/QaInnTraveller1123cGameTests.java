package io.github.jcondedata.aliveworkplace.test;

import io.github.jcondedata.aliveworkplace.inn.Innkeepers;
import io.github.jcondedata.aliveworkplace.inn.Traveller;
import io.github.jcondedata.aliveworkplace.registry.ModBlocks;
import io.github.jcondedata.aliveworkplace.registry.ModVillagers;
import io.github.jcondedata.aliveworkplace.work.Jobs;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.GlobalPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.village.poi.PoiManager;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.npc.VillagerProfession;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.properties.BedPart;

/**
 * QA (lane-c-1123-0353), B1483 and B1470 from the spec: a traveller who comes to stay at an inn is a nitwit guest. He
 * takes no job, so he never claims a job block: a free composter, lectern, Storehouse or Travel Post beside him stays
 * free and the next jobless villager takes it. That holds when the block is put down after he came, after his entity is
 * saved and loaded again, with two travellers at once, and through the inn's own morning round. Hired, he is jobless
 * again and takes a free workstation himself, as the wiki says a hired traveler does (lost-caravan.md, 31.20).
 */
public class QaInnTraveller1123cGameTests implements net.fabricmc.fabric.api.gametest.v1.FabricGameTest {
	private static final String AREA = "aliveworkplace_test:big_area";
	private static final BlockPos COUNTER = new BlockPos(11, 2, 11);
	private static final BlockPos COMPOSTER = new BlockPos(8, 2, 11);
	private static final BlockPos LECTERN = new BlockPos(14, 2, 11);
	private static final BlockPos STOREHOUSE = new BlockPos(11, 2, 8);
	private static final BlockPos TRAVEL_POST = new BlockPos(11, 2, 14);

	// ---- helpers -------------------------------------------------------------------------------------------------

	private static boolean free(GameTestHelper helper, BlockPos block) {
		return helper.getLevel().getPoiManager().getInRange(t -> true, helper.absolutePos(block), 0, PoiManager.Occupancy.HAS_SPACE).findAny().isPresent();
	}

	private static String claims(Villager v) {
		return "job site " + v.getBrain().getMemory(MemoryModuleType.JOB_SITE).map(GlobalPos::pos).map(BlockPos::toShortString).orElse("none")
			+ ", pick " + v.getBrain().getMemory(MemoryModuleType.POTENTIAL_JOB_SITE).map(GlobalPos::pos).map(BlockPos::toShortString).orElse("none")
			+ ", trade " + v.getVillagerData().getProfession();
	}

	private static boolean claimsNothing(Villager v) {
		return v.getBrain().getMemory(MemoryModuleType.JOB_SITE).isEmpty() && v.getBrain().getMemory(MemoryModuleType.POTENTIAL_JOB_SITE).isEmpty();
	}

	private static void bed(GameTestHelper helper, int x, int z) {
		helper.setBlock(new BlockPos(x, 2, z), Blocks.RED_BED.defaultBlockState().setValue(BedBlock.FACING, Direction.SOUTH).setValue(BedBlock.PART, BedPart.FOOT));
		helper.setBlock(new BlockPos(x, 2, z + 1), Blocks.RED_BED.defaultBlockState().setValue(BedBlock.FACING, Direction.SOUTH).setValue(BedBlock.PART, BedPart.HEAD));
	}

	/** An inn: its counter worked by an Innkeeper, with {@code beds} free beds in the area's back row. */
	private static Villager inn(GameTestHelper helper, int beds) {
		helper.setBlock(COUNTER, ModBlocks.INN_COUNTER);
		for (int i = 0; i < beds; i++) {
			bed(helper, 3 + 2 * i, 18);
		}
		Villager innkeeper = helper.spawn(EntityType.VILLAGER, new BlockPos(12, 2, 12));
		Jobs.employ(helper.getLevel(), innkeeper, helper.absolutePos(COUNTER), ModVillagers.INN_COUNTER_POI, ModVillagers.INNKEEPER);
		return innkeeper;
	}

	/** A jobless villager put down on the ground (B1478: one in the air on its first tick skips the nearest blocks a while). */
	private static Villager jobless(GameTestHelper helper, BlockPos at) {
		Villager v = helper.spawn(EntityType.VILLAGER, at);
		v.setOnGround(true);
		return v;
	}

	/** {@code trade} null: any job (a composter or a lectern draws one of its jobs, random-jobs.md). */
	private static boolean worksAt(GameTestHelper helper, Villager v, BlockPos block, VillagerProfession trade) {
		return v.getBrain().getMemory(MemoryModuleType.JOB_SITE).map(GlobalPos::pos).filter(helper.absolutePos(block)::equals).isPresent()
			&& (trade == null ? v.getVillagerData().getProfession() != VillagerProfession.NONE && v.getVillagerData().getProfession() != VillagerProfession.NITWIT
				: v.getVillagerData().getProfession() == trade);
	}

	private static String nearby(GameTestHelper helper) {
		StringBuilder b = new StringBuilder();
		for (Villager v : helper.getLevel().getEntitiesOfClass(Villager.class, new net.minecraft.world.phys.AABB(helper.absolutePos(COUNTER)).inflate(40))) {
			b.append(v.getName().getString()).append(" at ").append(v.blockPosition().toShortString()).append(" (").append(claims(v)).append("); ");
		}
		return b.toString();
	}

	/** What a restart does to him: saved, a new villager loaded from it, the old one gone. */
	private static Villager reload(ServerLevel level, Villager old) {
		CompoundTag tag = new CompoundTag();
		old.saveWithoutId(tag);
		Villager copy = EntityType.VILLAGER.create(level);
		copy.load(tag);
		old.discard();
		level.addFreshEntity(copy);
		io.github.jcondedata.aliveworkplace.work.Village.forget(copy);
		return copy;
	}

	/** Runs {@code then} once the inn has {@code count} guests, polling every 10 ticks until {@code deadline}. */
	private static void whenGuests(GameTestHelper helper, int count, int deadline, java.util.function.Consumer<List<Villager>> then) {
		List<Villager> guests = Innkeepers.guests(helper.getLevel(), helper.absolutePos(COUNTER));
		if (guests.size() >= count) {
			then.accept(guests);
		} else if (helper.getTick() >= deadline) {
			helper.fail(guests.size() + " guests by tick " + helper.getTick() + ", wanted " + count + " (the inn's morning round)");
		} else {
			helper.runAfterDelay(10, () -> whenGuests(helper, count, deadline, then));
		}
	}

	// ---- tests ---------------------------------------------------------------------------------------------------

	/**
	 * The inn's own morning brings a traveller to a counter with a free composter, lectern, Storehouse and Travel Post
	 * three blocks away. 300 ticks later every block is still free and he holds none of them; he is still a nitwit guest
	 * with his Traveller record and his bed; then four jobless villagers take all four blocks.
	 */
	//$ gametest_ticks_batch AREA '2000' '"qaInnTraveller1123cMorning"'
	@GameTest(template = AREA, timeoutTicks = 2000, batch = "qaInnTraveller1123cMorning")
	public void theMorningsTravellerLeavesFourFreeJobBlocksToTheJobless(GameTestHelper helper) {
		Leftovers.clear(helper);
		helper.setDayTime(1000);
		inn(helper, 2);
		helper.setBlock(COMPOSTER, Blocks.COMPOSTER);
		helper.setBlock(LECTERN, Blocks.LECTERN);
		helper.setBlock(STOREHOUSE, ModBlocks.STOREHOUSE);
		helper.setBlock(TRAVEL_POST, ModBlocks.TRAVEL_POST);
		whenGuests(helper, 1, 900, guests -> {
			Villager guest = guests.get(0);
			Traveller record = Innkeepers.traveller(guest);
			helper.runAfterDelay(300, () -> {
				for (BlockPos block : List.of(COMPOSTER, LECTERN, STOREHOUSE, TRAVEL_POST)) {
					helper.assertTrue(free(helper, block), "the job block at " + block.toShortString() + " was claimed with no jobless villager about; the traveller: " + claims(guest));
				}
				helper.assertTrue(claimsNothing(guest), "the traveller claims a job block: " + claims(guest));
				helper.assertTrue(guest.isAlive() && Innkeepers.isTraveller(guest) && guest.getVillagerData().getProfession() == VillagerProfession.NITWIT,
					"he is no longer a nitwit traveller: " + claims(guest) + ", traveller " + Innkeepers.isTraveller(guest));
				helper.assertTrue(record != null && record.equals(Innkeepers.traveller(guest)), "his Traveller record changed: " + record + " -> " + Innkeepers.traveller(guest));
				helper.assertTrue(guest.getBrain().getMemory(MemoryModuleType.HOME).isPresent(), "the traveller has no bed (B206: the free bed is his as he arrives)");
				List<Villager> four = new ArrayList<>();
				for (BlockPos at : List.of(new BlockPos(7, 2, 9), new BlockPos(15, 2, 9), new BlockPos(9, 2, 7), new BlockPos(13, 2, 15))) {
					four.add(jobless(helper, at));
				}
				helper.succeedWhen(() -> {
					someoneWorks(helper, four, COMPOSTER, null, guest);
					someoneWorks(helper, four, LECTERN, null, guest);
					someoneWorks(helper, four, STOREHOUSE, ModVillagers.PORTER, guest);
					someoneWorks(helper, four, TRAVEL_POST, ModVillagers.FERRYMAN, guest);
					helper.assertTrue(claimsNothing(guest), "the traveller claims a job block: " + claims(guest));
				});
			});
		});
	}

	private static void someoneWorks(GameTestHelper helper, List<Villager> villagers, BlockPos block, VillagerProfession trade, Villager guest) {
		StringBuilder who = new StringBuilder();
		for (Villager v : villagers) {
			if (worksAt(helper, v, block, trade)) {
				return;
			}
			who.append("; ").append(claims(v));
		}
		helper.fail("nobody works at " + block.toShortString() + " as " + (trade == null ? "any job" : trade) + who + "; the traveller: " + claims(guest));
	}

	/**
	 * A job block put down beside a traveller after he came (the bug's player case: it looked free and took nobody) stays
	 * free, and a jobless villager who comes later takes it.
	 */
	//$ gametest_ticks_batch AREA '1200' '"qaInnTraveller1123cPlacedLater"'
	@GameTest(template = AREA, timeoutTicks = 1200, batch = "qaInnTraveller1123cPlacedLater")
	public void aComposterPutDownBesideATravellerStaysFreeForTheJobless(GameTestHelper helper) {
		Leftovers.clear(helper);
		helper.setDayTime(1000);
		inn(helper, 1);
		whenGuests(helper, 1, 900, guests -> {
			Villager guest = guests.get(0);
			helper.runAfterDelay(40, () -> {
				BlockPos rel = COMPOSTER; // three blocks from the counter he stands beside
				helper.setBlock(rel, Blocks.COMPOSTER);
				helper.assertTrue(free(helper, rel), "(setup) the composter isn't a free job block");
				helper.runAfterDelay(300, () -> {
					helper.assertTrue(free(helper, rel), "the composter put down beside the traveller was claimed; the traveller: " + claims(guest)
						+ "; nearby: " + nearby(helper));
					helper.assertTrue(claimsNothing(guest), "the traveller claims a job block: " + claims(guest));
					Villager farmer = jobless(helper, rel.offset(0, 0, 2));
					helper.succeedWhen(() -> helper.assertTrue(worksAt(helper, farmer, rel, null),
						"the jobless villager didn't take the composter: " + claims(farmer) + "; the traveller: " + claims(guest)));
				});
			});
		});
	}

	/**
	 * A traveller's entity saved and loaded again (a restart, or his chunk unloaded and back) is still the same nitwit
	 * guest with the same record and name, and still leaves a lectern beside him to a jobless villager.
	 */
	//$ gametest_ticks_batch AREA '1200' '"qaInnTraveller1123cReload"'
	@GameTest(template = AREA, timeoutTicks = 1200, batch = "qaInnTraveller1123cReload")
	public void aReloadedTravellerStillLeavesTheLecternToTheJobless(GameTestHelper helper) {
		Leftovers.clear(helper);
		helper.setDayTime(1000);
		ServerLevel level = helper.getLevel();
		Villager innkeeper = inn(helper, 1);
		helper.setBlock(LECTERN, Blocks.LECTERN);
		Villager first = Innkeepers.arrive(level, innkeeper, helper.absolutePos(COUNTER));
		helper.assertTrue(first != null, "(setup) no traveller arrived");
		Traveller record = Innkeepers.traveller(first);
		String name = first.getCustomName() == null ? null : first.getCustomName().getString();
		helper.runAfterDelay(20, () -> {
			helper.assertTrue(free(helper, LECTERN), "the lectern was claimed before the reload; the traveller: " + claims(first));
			Villager guest = reload(level, first);
			helper.assertTrue(Innkeepers.isTraveller(guest) && record.equals(Innkeepers.traveller(guest)), "after a reload his record is " + Innkeepers.traveller(guest) + ", was " + record);
			helper.assertTrue(guest.getVillagerData().getProfession() == VillagerProfession.NITWIT, "after a reload: " + claims(guest));
			helper.assertTrue(guest.getCustomName() != null && guest.getCustomName().getString().equals(name), "after a reload his name is " + guest.getCustomName() + ", was " + name);
			helper.assertTrue(Innkeepers.guests(level, helper.absolutePos(COUNTER)).contains(guest), "after a reload he is no guest of the inn");
			helper.runAfterDelay(300, () -> {
				helper.assertTrue(free(helper, LECTERN), "the lectern was claimed after the reload; the traveller: " + claims(guest));
				helper.assertTrue(claimsNothing(guest), "the reloaded traveller claims a job block: " + claims(guest));
				Villager librarian = jobless(helper, LECTERN.offset(1, 0, 1));
				helper.succeedWhen(() -> helper.assertTrue(worksAt(helper, librarian, LECTERN, null),
					"the jobless villager didn't take the lectern: " + claims(librarian) + "; the traveller: " + claims(guest)));
			});
		});
	}

	/**
	 * Two travellers at once (the inn takes two) with a composter and a Storehouse between them: neither claims a block,
	 * and two jobless villagers take one each.
	 */
	//$ gametest_ticks_batch AREA '1200' '"qaInnTraveller1123cTwo"'
	@GameTest(template = AREA, timeoutTicks = 1200, batch = "qaInnTraveller1123cTwo")
	public void twoTravellersAtOnceLeaveBothBlocksToTheJobless(GameTestHelper helper) {
		Leftovers.clear(helper);
		helper.setDayTime(1000);
		ServerLevel level = helper.getLevel();
		Villager innkeeper = inn(helper, 2);
		helper.setBlock(COMPOSTER, Blocks.COMPOSTER);
		helper.setBlock(STOREHOUSE, ModBlocks.STOREHOUSE);
		Villager a = Innkeepers.arrive(level, innkeeper, helper.absolutePos(COUNTER));
		Villager b = Innkeepers.arrive(level, innkeeper, helper.absolutePos(COUNTER));
		helper.assertTrue(a != null && b != null && a != b, "(setup) two travellers didn't arrive");
		helper.assertTrue(Innkeepers.guests(level, helper.absolutePos(COUNTER)).size() == 2, "(setup) the inn has " + Innkeepers.guests(level, helper.absolutePos(COUNTER)).size() + " guests");
		helper.runAfterDelay(300, () -> {
			helper.assertTrue(free(helper, COMPOSTER) && free(helper, STOREHOUSE), "a block was claimed with no jobless villager about; the travellers: "
				+ claims(a) + " / " + claims(b));
			helper.assertTrue(claimsNothing(a) && claimsNothing(b), "a traveller claims a job block: " + claims(a) + " / " + claims(b));
			List<Villager> two = List.of(jobless(helper, new BlockPos(7, 2, 9)), jobless(helper, new BlockPos(9, 2, 7)));
			helper.succeedWhen(() -> {
				someoneWorks(helper, two, COMPOSTER, null, a);
				someoneWorks(helper, two, STOREHOUSE, ModVillagers.PORTER, b);
				helper.assertTrue(claimsNothing(a) && claimsNothing(b), "a traveller claims a job block: " + claims(a) + " / " + claims(b));
			});
		});
	}

	/**
	 * Hired, a traveller is jobless again and takes the free composter beside him by himself (lost-caravan.md: "he takes
	 * whatever free workstation the village has, as a hired traveler does"): his nitwit brain must not outlive the hire.
	 */
	//$ gametest_ticks_batch AREA '1200' '"qaInnTraveller1123cHired"'
	@GameTest(template = AREA, timeoutTicks = 1200, batch = "qaInnTraveller1123cHired")
	public void aHiredTravellerTakesTheFreeComposterHimself(GameTestHelper helper) {
		Leftovers.clear(helper);
		helper.setDayTime(1000);
		ServerLevel level = helper.getLevel();
		Villager innkeeper = inn(helper, 1);
		helper.setBlock(COMPOSTER, Blocks.COMPOSTER);
		Villager guest = Innkeepers.arrive(level, innkeeper, helper.absolutePos(COUNTER));
		helper.assertTrue(guest != null, "(setup) no traveller arrived");
		helper.runAfterDelay(200, () -> {
			helper.assertTrue(free(helper, COMPOSTER) && claimsNothing(guest), "the traveller claimed the composter before he was hired: " + claims(guest));
			ServerPlayer player = helper.makeMockServerPlayerInLevel();
			player.setGameMode(net.minecraft.world.level.GameType.SURVIVAL);
			player.getInventory().add(new ItemStack(Items.EMERALD, 64));
			player.getInventory().add(new ItemStack(Items.EMERALD, 64));
			helper.assertTrue(Innkeepers.hire(player, guest), "not hired with 128 emeralds");
			helper.assertTrue(!Innkeepers.isTraveller(guest) && guest.getVillagerData().getProfession() == VillagerProfession.NONE, "hired but still: " + claims(guest));
			player.discard();
			helper.succeedWhen(() -> helper.assertTrue(worksAt(helper, guest, COMPOSTER, null),
				"the hired traveller didn't take the free composter: " + claims(guest)));
		});
	}
}
