package io.github.jcondedata.aliveworkplace.test;

import io.github.jcondedata.aliveworkplace.city.CityPlanItem;
import io.github.jcondedata.aliveworkplace.city.Stewards;
import io.github.jcondedata.aliveworkplace.hall.VillageHallBlockEntity;
import io.github.jcondedata.aliveworkplace.registry.ModBlocks;
import io.github.jcondedata.aliveworkplace.registry.ModItems;
import io.github.jcondedata.aliveworkplace.registry.ModVillagers;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.village.poi.PoiManager;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;

/**
 * QA (lane E, saves, 2026-10-31) for B687 (a Steward away where nothing is loaded keeps his hall: the check that gives
 * an old hall its Steward's place runs once in the hall's life, "saved, so a hall whose Steward holds the place isn't
 * checked again"), across a save and reload, which the fix's own test doesn't do:
 * <ul>
 * <li>a hall saved with its Steward, read back as a new block entity (as the next server start does) while he is away:
 * its first tick and a second appointment leave him the hall;</li>
 * <li>a hall saved before the check existed (no {@code stewardPlaceChecked}) with its Steward by it: checked once, he
 * keeps it, and the next save says it was checked, so he can then go away and still keep it.</li>
 * </ul>
 */
public class QaStewardPlaceSaves1031GameTests implements FabricGameTest {
	private static final String AREA = "aliveworkplace_test:huge_area";
	private static final BlockPos HALL = new BlockPos(5, 2, 5);

	private static void otherHalls(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		for (BlockPos old : level.getPoiManager().findAll(h -> h.is(ModVillagers.VILLAGE_HALL_POI), q -> true,
			helper.absolutePos(HALL), 180, PoiManager.Occupancy.ANY).map(BlockPos::immutable).toList()) {
			level.removeBlock(old, false);
		}
	}

	private static ItemStack planFor(GameTestHelper helper, ServerPlayer player) {
		ItemStack stack = new ItemStack(ModItems.CITY_PLAN);
		CityPlanItem.bind(helper.getLevel(), player, stack, helper.absolutePos(HALL));
		return stack;
	}

	private static boolean stewardOfHall(GameTestHelper helper, Villager villager) {
		return villager.getVillagerData().getProfession() == ModVillagers.STEWARD && villager.getBrain().getMemory(MemoryModuleType.JOB_SITE)
			.filter(GlobalPos.of(helper.getLevel().dimension(), helper.absolutePos(HALL))::equals).isPresent();
	}

	/** Somewhere within the hall's reach whose chunk isn't loaded (the GameTest server loads only the test areas). */
	private static BlockPos away(GameTestHelper helper) {
		BlockPos hall = helper.absolutePos(HALL);
		for (int[] d : new int[][] {{100, 100}, {-100, 100}, {100, -100}, {-100, -100}, {110, 0}, {-110, 0}, {0, 110}, {0, -110}}) {
			BlockPos p = hall.offset(d[0], 0, d[1]);
			if (!helper.getLevel().isLoaded(p)) {
				return p;
			}
		}
		throw new net.minecraft.gametest.framework.GameTestAssertException("no unloaded chunk near the test area");
	}

	/** The hall as the next server start reads it: a new block entity from the saved tag, in place of the old one. */
	private static VillageHallBlockEntity reload(GameTestHelper helper, CompoundTag saved) {
		ServerLevel level = helper.getLevel();
		BlockPos hall = helper.absolutePos(HALL);
		BlockEntity fresh = BlockEntity.loadStatic(hall, level.getBlockState(hall), saved, level.registryAccess());
		helper.assertTrue(fresh instanceof VillageHallBlockEntity, "the saved hall read back as " + fresh);
		level.setBlockEntity(fresh);
		return (VillageHallBlockEntity) level.getBlockEntity(hall);
	}

	private static Villager appointed(GameTestHelper helper, ServerPlayer player, ItemStack plan) {
		Villager steward = StewardGameTests.seasoned(helper.spawn(EntityType.VILLAGER, HALL.east(2)));
		steward.setNoAi(true);
		helper.assertTrue(Stewards.appoint(player, steward, plan) == InteractionResult.SUCCESS, "setup: the Steward wasn't appointed");
		return steward;
	}

	/** Saved with its Steward, reloaded while he's away: neither the first tick nor a second appointment takes his hall. */
	//$ gametest_ticks_batch AREA '100' '"qaStewardAwayReload"'
	@GameTest(template = AREA, timeoutTicks = 100, batch = "qaStewardAwayReload")
	public void aReloadedHallKeepsItsAwaySteward(GameTestHelper helper) {
		Leftovers.clear(helper);
		otherHalls(helper);
		ServerLevel level = helper.getLevel();
		helper.setBlock(HALL, ModBlocks.VILLAGE_HALL);
		BlockPos hall = helper.absolutePos(HALL);
		ServerPlayer player = helper.makeMockServerPlayerInLevel();
		ItemStack plan = planFor(helper, player);
		Villager steward = appointed(helper, player, plan);
		helper.runAfterDelay(3, () -> {
			VillageHallBlockEntity entity = (VillageHallBlockEntity) level.getBlockEntity(hall);
			CompoundTag saved = entity.saveWithFullMetadata(level.registryAccess());
			helper.assertTrue(saved.getBoolean("stewardPlaceChecked"), "a hall that has ticked with its Steward saved stewardPlaceChecked as false: " + saved.get("stewardPlaceChecked"));
			BlockPos away = away(helper);
			steward.teleportTo(away.getX() + 0.5, away.getY(), away.getZ() + 0.5);
			helper.assertTrue(Stewards.stewardOf(level, hall) == null, "the Steward away at " + away + " is still seen: the test proves nothing");
			reload(helper, saved);
			helper.runAfterDelay(5, () -> {
				helper.assertTrue(level.getPoiManager().getFreeTickets(hall) == 0, "the reloaded hall's place came free while its Steward was away");
				Villager second = StewardGameTests.seasoned(helper.spawn(EntityType.VILLAGER, HALL.south(2)));
				second.setNoAi(true);
				InteractionResult result = Stewards.appoint(player, second, plan);
				helper.assertTrue(result != InteractionResult.SUCCESS && !stewardOfHall(helper, second) && level.getPoiManager().getFreeTickets(hall) == 0,
					"after a reload a second Steward took the hall of one who is away: " + result);
				steward.teleportTo(hall.getX() + 2.5, hall.getY(), hall.getZ() + 0.5);
				helper.assertTrue(Stewards.stewardOf(level, hall) == steward && stewardOfHall(helper, steward), "the Steward back from away isn't the reloaded hall's");
				steward.discard();
				helper.succeed();
			});
		});
	}

	/**
	 * A hall saved before the check (no stewardPlaceChecked), its Steward by it: after the reload he keeps the hall, the
	 * hall saves that it was checked, and a second reload with him away still leaves him the hall.
	 */
	//$ gametest_ticks_batch AREA '120' '"qaStewardOldHallReload"'
	@GameTest(template = AREA, timeoutTicks = 120, batch = "qaStewardOldHallReload")
	public void anOldHallIsCheckedOnceAndRemembersIt(GameTestHelper helper) {
		Leftovers.clear(helper);
		otherHalls(helper);
		ServerLevel level = helper.getLevel();
		helper.setBlock(HALL, ModBlocks.VILLAGE_HALL);
		BlockPos hall = helper.absolutePos(HALL);
		ServerPlayer player = helper.makeMockServerPlayerInLevel();
		ItemStack plan = planFor(helper, player);
		Villager steward = appointed(helper, player, plan);
		helper.runAfterDelay(3, () -> {
			CompoundTag old = level.getBlockEntity(hall).saveWithFullMetadata(level.registryAccess());
			old.remove("stewardPlaceChecked");
			reload(helper, old);
			helper.runAfterDelay(5, () -> {
				helper.assertTrue(Stewards.stewardOf(level, hall) == steward && stewardOfHall(helper, steward) && level.getPoiManager().getFreeTickets(hall) == 0,
					"the old hall's Steward, by his hall, lost it at its first check (free places " + level.getPoiManager().getFreeTickets(hall) + ")");
				CompoundTag now = level.getBlockEntity(hall).saveWithFullMetadata(level.registryAccess());
				helper.assertTrue(now.getBoolean("stewardPlaceChecked"), "the old hall, checked, saves stewardPlaceChecked as " + now.get("stewardPlaceChecked"));
				BlockPos away = away(helper);
				steward.teleportTo(away.getX() + 0.5, away.getY(), away.getZ() + 0.5);
				reload(helper, now);
				helper.runAfterDelay(5, () -> {
					helper.assertTrue(level.getPoiManager().getFreeTickets(hall) == 0, "the checked old hall's place came free at the next reload, its Steward away");
					steward.discard();
					helper.succeed();
				});
			});
		});
	}
}
