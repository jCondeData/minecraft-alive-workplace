package io.github.jcondedata.aliveworkplace.test;

import io.github.jcondedata.aliveworkplace.hall.Caravans;
import io.github.jcondedata.aliveworkplace.hall.Chronicle;
import io.github.jcondedata.aliveworkplace.legend.GolemForgePower;
import io.github.jcondedata.aliveworkplace.legend.GolemSmith;
import io.github.jcondedata.aliveworkplace.legend.Legend;
import io.github.jcondedata.aliveworkplace.legend.LegendNeeds;
import io.github.jcondedata.aliveworkplace.legend.LegendPowers;
import io.github.jcondedata.aliveworkplace.legend.LegendRecord;
import io.github.jcondedata.aliveworkplace.legend.Legends;
import io.github.jcondedata.aliveworkplace.people.Moods;
import io.github.jcondedata.aliveworkplace.registry.ModAttachments;
import io.github.jcondedata.aliveworkplace.registry.ModBlocks;
import io.github.jcondedata.aliveworkplace.store.HaulerGolems;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Container;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.animal.IronGolem;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;

/**
 * The Golem Smith (ROADMAP 29.15), first piece: the legend file, the forge's costs, cap and wait, the role saved on the
 * golem, the twice-as-fast mending and a Hauler Golem taking 9 stacks to the Storehouse.
 */
public class GolemSmithGameTests implements net.fabricmc.fabric.api.gametest.v1.FabricGameTest {
	private static final String AREA = "aliveworkplace_test:huge_area";
	private static final BlockPos HALL = new BlockPos(3, 2, 3);
	private static final BlockPos TABLE = new BlockPos(10, 2, 10);

	private static void setUp(GameTestHelper helper) {
		Leftovers.clear(helper);
		Leftovers.halls(helper);
		ServerLevel level = helper.getLevel();
		helper.setBlock(HALL, ModBlocks.VILLAGE_HALL);
		BlockPos hall = helper.absolutePos(HALL);
		Caravans.Data.get(level).setWants(hall, Component.literal("Test"), List.of());
		Leftovers.after(helper, () -> {
			Caravans.Data.get(level).remove(hall);
			LegendRecord record = LegendRecord.get(level);
			for (Villager v : level.getEntitiesOfClass(Villager.class, helper.getBounds().inflate(96), v -> true)) {
				record.forget(v.getUUID());
				v.discard();
			}
			for (IronGolem g : level.getEntitiesOfClass(IronGolem.class, helper.getBounds().inflate(32), g -> true)) {
				g.discard();
			}
			Legends.reload(level.getServer().getResourceManager());
			LegendPowers.forget();
		});
	}

	private static Legend staged(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		Moods.ENABLED = false;
		LegendNeeds.ENABLED = false;
		Legends.reload(level.getServer().getResourceManager());
		Legend smith = Legends.get(GolemSmith.ID).orElseThrow(() -> new AssertionError("golem_smith.json didn't load"));
		Map<ResourceLocation, Legend> map = new LinkedHashMap<>();
		map.put(GolemSmith.ID, smith);
		Legends.setForTest(map);
		return smith;
	}

	private static void restore() {
		Moods.ENABLED = true;
		LegendNeeds.ENABLED = true;
	}

	private static Villager smith(GameTestHelper helper, Legend legend) {
		helper.setBlock(TABLE, Blocks.SMITHING_TABLE);
		helper.setBlock(TABLE.east(), Blocks.CHEST);
		Villager v = helper.spawn(EntityType.VILLAGER, TABLE.north(2));
		Legends.make(helper.getLevel(), v, legend, "test");
		v.getBrain().setMemory(MemoryModuleType.JOB_SITE, GlobalPos.of(helper.getLevel().dimension(), helper.absolutePos(TABLE)));
		return v;
	}

	/** The file: Legendary, Tinkerer, likes wine, inspired in Tinkerers, a heavy core from the seven-item pool, golem_forge. */
	//$ gametest_ticks_batch EMPTY_STRUCTURE '20' '"golemSmithFile"'
	@GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 20, batch = "golemSmithFile")
	public void golemSmithFile(GameTestHelper helper) {
		Legends.reload(helper.getLevel().getServer().getResourceManager());
		Legend legend = Legends.get(GolemSmith.ID).orElseThrow(() -> new AssertionError("golem_smith.json didn't load"));
		helper.assertTrue(legend.job().toString().equals("aliveworkplace:tinkerer"), "job: " + legend.job());
		helper.assertTrue(legend.luxury().orElse("").equals("wine"), "luxury: " + legend.luxury());
		helper.assertTrue(legend.ways("inspired").size() == 1, "an inspired way");
		helper.assertTrue(io.github.jcondedata.aliveworkplace.legend.StrangeMoods.masterworkItem(legend) == Items.HEAVY_CORE, "the masterwork is a heavy core");
		helper.assertTrue(io.github.jcondedata.aliveworkplace.legend.StrangeMoods.pool(legend).size() == 7, "seven materials");
		GolemForgePower power = legend.powers(GolemForgePower.class).get(0);
		helper.assertTrue(power.days() == 2 && power.villagersPerGolem() == 5 && power.mendFactor() == 2f, "the forge: " + power);
		helper.succeed();
	}

	/**
	 * The costs are taken from the chest by the smithing table; a second golem waits 2 days; the cap (one per 5
	 * villagers) holds until the village grows; the role survives a save and load; a Smith mends twice as fast.
	 */
	//$ gametest_ticks_batch AREA '100' '"golemSmithForge"'
	@GameTest(template = AREA, timeoutTicks = 100, batch = "golemSmithForge")
	public void golemSmithForgeCostsCapAndWait(GameTestHelper helper) {
		setUp(helper);
		try {
			Legend legend = staged(helper);
			ServerLevel level = helper.getLevel();
			BlockPos hall = helper.absolutePos(HALL);
			Villager smith = smith(helper, legend);
			Container chest = helper.getBlockEntity(TABLE.east());
			chest.setItem(0, new ItemStack(Items.IRON_BLOCK, 12));
			chest.setItem(1, new ItemStack(Items.CARVED_PUMPKIN, 3));
			chest.setItem(2, new ItemStack(Items.CHEST, 3));
			helper.assertTrue(GolemSmith.forgeOne(level, hall, smith) == null, "1 villager: no room under the cap");
			for (int i = 0; i < 4; i++) {
				helper.spawn(EntityType.VILLAGER, new BlockPos(14 + i, 2, 4));
			}
			IronGolem golem = GolemSmith.forgeOne(level, hall, smith);
			helper.assertTrue(golem != null, "5 villagers and the costs in the chest: a golem");
			helper.assertTrue(chest.countItem(Items.IRON_BLOCK) == 8 && chest.countItem(Items.CARVED_PUMPKIN) == 2 && chest.countItem(Items.CHEST) == 2,
				"the costs taken: " + chest.countItem(Items.IRON_BLOCK) + " iron blocks left");
			helper.assertTrue(GolemSmith.role(golem) == GolemSmith.Role.HAULER && golem.hasCustomName() && golem.isCustomNameVisible(), "a named hauler");
			CompoundTag saved = golem.saveWithoutId(new CompoundTag());
			IronGolem loaded = EntityType.IRON_GOLEM.create(level);
			loaded.load(saved);
			helper.assertTrue(GolemSmith.role(loaded) == GolemSmith.Role.HAULER, "the role survives a save and load");
			for (int i = 0; i < 5; i++) {
				helper.spawn(EntityType.VILLAGER, new BlockPos(14 + i, 2, 6));
			}
			helper.assertTrue(GolemSmith.forgeOne(level, hall, smith) == null, "the same day: wait");
			long today = Chronicle.day(level);
			ModAttachments.GOLEM_FORGE.set(smith, new GolemSmith.Forge("hauler", today - 1));
			helper.assertTrue(GolemSmith.forgeOne(level, hall, smith) == null, "a day later: still waiting");
			ModAttachments.GOLEM_FORGE.set(smith, new GolemSmith.Forge("hauler", today - 2));
			helper.assertTrue(GolemSmith.forgeOne(level, hall, smith) != null, "two days later and 10 villagers: a second golem");
			ModAttachments.GOLEM_FORGE.set(smith, new GolemSmith.Forge("hauler", today - 2));
			helper.assertTrue(GolemSmith.forgeOne(level, hall, smith) == null, "2 golems for 10 villagers: the cap");
			helper.assertTrue(GolemSmith.mendFactor(smith) == 2f, "a Smith mends twice as fast");
			Villager plain = helper.spawn(EntityType.VILLAGER, new BlockPos(20, 2, 20));
			helper.assertTrue(GolemSmith.mendFactor(plain) == 1f, "anyone else at the usual pace");
			helper.succeed();
		} finally {
			restore();
		}
	}

	/** No costs in the chest: no golem, nothing taken. */
	//$ gametest_ticks_batch AREA '100' '"golemSmithShort"'
	@GameTest(template = AREA, timeoutTicks = 100, batch = "golemSmithShort")
	public void golemSmithWantsTheWholeCost(GameTestHelper helper) {
		setUp(helper);
		try {
			Legend legend = staged(helper);
			Villager smith = smith(helper, legend);
			for (int i = 0; i < 5; i++) {
				helper.spawn(EntityType.VILLAGER, new BlockPos(14 + i, 2, 4));
			}
			Container chest = helper.getBlockEntity(TABLE.east());
			chest.setItem(0, new ItemStack(Items.IRON_BLOCK, 4));
			chest.setItem(1, new ItemStack(Items.CARVED_PUMPKIN, 1));
			helper.assertTrue(GolemSmith.forgeOne(helper.getLevel(), helper.absolutePos(HALL), smith) == null, "no chest in the chest: no golem");
			helper.assertTrue(chest.countItem(Items.IRON_BLOCK) == 4 && chest.countItem(Items.CARVED_PUMPKIN) == 1, "nothing taken");
			helper.succeed();
		} finally {
			restore();
		}
	}

	/** A Hauler Golem takes 9 stacks from a Drop Box to the chest by the Storehouse in one trip. */
	//$ gametest_ticks_batch AREA '1200' '"golemSmithHauler"'
	@GameTest(template = AREA, timeoutTicks = 1200, batch = "golemSmithHauler")
	public void haulerTakesNineStacksToTheStorehouse(GameTestHelper helper) {
		Leftovers.clear(helper);
		BlockPos storehouse = new BlockPos(20, 2, 20);
		BlockPos storeChest = new BlockPos(21, 2, 20);
		helper.setBlock(storehouse, ModBlocks.STOREHOUSE);
		helper.setBlock(storeChest, Blocks.CHEST);
		BlockPos boxPos = new BlockPos(6, 2, 6);
		helper.setBlock(boxPos, ModBlocks.DROP_BOX);
		Container box = helper.getBlockEntity(boxPos);
		for (int i = 0; i < 12; i++) {
			box.setItem(i, new ItemStack(Items.COBBLESTONE, 64));
		}
		IronGolem golem = GolemSmith.build(helper.getLevel(), helper.absolutePos(new BlockPos(12, 1, 12)), GolemSmith.Role.HAULER);
		helper.assertTrue(golem != null, "a hauler");
		Leftovers.after(helper, golem::discard);
		helper.succeedWhen(() -> {
			Container store = helper.getBlockEntity(storeChest);
			helper.assertTrue(store.countItem(Items.COBBLESTONE) >= 9 * 64, "the store has " + store.countItem(Items.COBBLESTONE) + " cobblestone");
			helper.assertTrue(store.countItem(Items.COBBLESTONE) % (9 * 64) == 0, "whole trips of 9 stacks: " + store.countItem(Items.COBBLESTONE));
			helper.assertTrue(HaulerGolems.load(golem).isEmpty() || store.countItem(Items.COBBLESTONE) == 9 * 64, "unloaded");
		});
	}
}
