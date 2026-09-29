package io.github.jcondedata.aliveworkplace.test;

import io.github.jcondedata.aliveworkplace.build.BuilderBag;
import io.github.jcondedata.aliveworkplace.nether.Netherworkers;
import io.github.jcondedata.aliveworkplace.registry.ModAttachments;
import io.github.jcondedata.aliveworkplace.registry.ModBlocks;
import io.github.jcondedata.aliveworkplace.registry.ModVillagers;
import io.github.jcondedata.aliveworkplace.work.Jobs;
import io.github.jcondedata.aliveworkplace.work.WorkerStatus;
import java.util.List;
import java.util.Set;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Container;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.portal.PortalShape;
import net.minecraft.world.level.storage.loot.LootTable;

/** Netherworkers: expeditions through the portal and back. */
public class NetherworkerGameTests implements FabricGameTest {
	private static final String AREA = "aliveworkplace_test:big_area";
	private static final BlockPos BRAZIER = new BlockPos(2, 2, 2);
	private static final BlockPos CHEST = new BlockPos(2, 2, 4);
	/** What the fortress table has that the others don't. */
	private static final Set<Item> FORTRESS_ONLY = Set.of(Items.BLAZE_ROD, Items.NETHER_BRICKS, Items.BONE, Items.COAL, Items.GOLD_INGOT,
		Items.WITHER_SKELETON_SKULL);
	private static final Set<Item> WASTES = Set.of(Items.NETHERRACK, Items.SOUL_SAND, Items.BASALT, Items.BLACKSTONE, Items.GLOWSTONE_DUST,
		Items.NETHER_WART, Items.MAGMA_CREAM, Items.GRAVEL, Items.SOUL_SOIL, Items.GOLD_NUGGET, Items.CRYING_OBSIDIAN, Items.GHAST_TEAR);

	private static Villager netherworker(GameTestHelper helper, ItemStack... chest) {
		helper.setDayTime(2000);
		helper.setBlock(BRAZIER, ModBlocks.NETHER_BRAZIER);
		helper.setBlock(CHEST, Blocks.CHEST);
		Container c = helper.getBlockEntity(CHEST);
		for (int i = 0; i < chest.length; i++) {
			c.setItem(i, chest[i]);
		}
		Villager villager = helper.spawn(EntityType.VILLAGER, new BlockPos(3, 2, 3));
		Jobs.employ(helper.getLevel(), villager, helper.absolutePos(BRAZIER), ModVillagers.NETHER_BRAZIER_POI, ModVillagers.NETHERWORKER);
		return villager;
	}

	/** An obsidian frame (four wide, five tall) with the portal lit in it, its inside from (x, 3, z) to (x + 1, 5, z). */
	private static void portal(GameTestHelper helper, int x, int z) {
		for (int dx = -1; dx <= 2; dx++) {
			helper.setBlock(new BlockPos(x + dx, 2, z), Blocks.OBSIDIAN);
			helper.setBlock(new BlockPos(x + dx, 6, z), Blocks.OBSIDIAN);
		}
		for (int y = 3; y <= 5; y++) {
			helper.setBlock(new BlockPos(x - 1, y, z), Blocks.OBSIDIAN);
			helper.setBlock(new BlockPos(x + 2, y, z), Blocks.OBSIDIAN);
		}
		PortalShape.findEmptyPortalShape(helper.getLevel(), helper.absolutePos(new BlockPos(x, 3, z)), Direction.Axis.X)
			.orElseThrow(() -> new net.minecraft.gametest.framework.GameTestAssertException("no portal shape"))
			.createPortalBlocks();
	}

	/** The expedition tables load; a sword and armor bring back a fortress's goods; the kit is read off the bag. */
	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
	public void netherTablesAndKit(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		for (var key : List.of(Netherworkers.WASTES, Netherworkers.MINING, Netherworkers.DEBRIS, Netherworkers.FOREST, Netherworkers.FORTRESS)) {
			helper.assertTrue(level.getServer().reloadableRegistries().getLootTable(key) != LootTable.EMPTY, "missing " + key.location());
		}
		helper.assertTrue(level.getServer().reloadableRegistries().getLootTable(Netherworkers.COBBLEMON) == LootTable.EMPTY,
			"the Cobblemon table loaded without Cobblemon");
		Villager villager = EntityType.VILLAGER.create(level);
		villager.moveTo(helper.absoluteVec(new net.minecraft.world.phys.Vec3(1, 2, 1)));
		List<ItemStack> bare = Netherworkers.finds(level, villager, 0);
		helper.assertTrue(!bare.isEmpty() && bare.stream().allMatch(s -> WASTES.contains(s.getItem())), "a bare trip found " + bare);
		List<ItemStack> armed = Netherworkers.finds(level, villager, Netherworkers.Kit.SWORD | Netherworkers.Kit.ARMOR);
		helper.assertTrue(armed.stream().anyMatch(s -> FORTRESS_ONLY.contains(s.getItem())), "a sword and armor found no fortress goods: " + armed);
		BuilderBag bag = new BuilderBag();
		bag.add(new ItemStack(Items.DIAMOND_PICKAXE));
		bag.add(new ItemStack(Items.IRON_CHESTPLATE));
		bag.add(new ItemStack(Items.BREAD, 3));
		int kit = Netherworkers.kitOf(bag);
		helper.assertTrue(kit == (Netherworkers.Kit.PICKAXE | Netherworkers.Kit.DIAMOND_PICKAXE | Netherworkers.Kit.ARMOR), "kit " + kit);
		helper.succeed();
	}

	/**
	 * With bread and gear by the brazier and a portal nearby: the netherworker packs, steps through (out of sight, taking
	 * no harm while away), comes back, and puts the Nether's goods and the worn gear in the chest.
	 */
	@GameTest(template = AREA, timeoutTicks = 1800, batch = "netherworker")
	public void aNetherExpedition(GameTestHelper helper) {
		Leftovers.clear(helper);
		int trip = Netherworkers.TRIP_TICKS;
		Netherworkers.TRIP_TICKS = 120;
		Leftovers.after(helper, () -> Netherworkers.TRIP_TICKS = trip);
		portal(helper, 9, 9);
		Villager villager = netherworker(helper, new ItemStack(Items.BREAD, 3), new ItemStack(Items.IRON_PICKAXE), new ItemStack(Items.IRON_SWORD),
			new ItemStack(Items.IRON_CHESTPLATE));
		Container chest = helper.getBlockEntity(CHEST);
		boolean[] sawAway = {false};
		helper.onEachTick(() -> {
			if (Netherworkers.isAway(villager) && !sawAway[0]) {
				sawAway[0] = true;
				helper.assertTrue(villager.isInvisible(), "an away netherworker should be out of sight");
				float health = villager.getHealth();
				villager.hurt(helper.getLevel().damageSources().generic(), 4f);
				helper.assertTrue(villager.getHealth() == health, "an away netherworker took damage");
			}
		});
		helper.succeedWhen(() -> {
			helper.assertTrue(sawAway[0], "never went through the portal");
			helper.assertFalse(Netherworkers.isAway(villager), "still away");
			helper.assertTrue(!villager.isInvisible(), "still invisible");
			helper.assertTrue(chest.countItem(Items.BREAD) == 0, "the bread should have been eaten, " + chest.countItem(Items.BREAD) + " left");
			ItemStack pick = ItemStack.EMPTY;
			boolean nether = false;
			for (int i = 0; i < chest.getContainerSize(); i++) {
				ItemStack s = chest.getItem(i);
				if (s.is(Items.IRON_PICKAXE)) {
					pick = s;
				}
				nether |= WASTES.contains(s.getItem());
			}
			helper.assertTrue(nether, "nothing from the Nether in the chest");
			helper.assertTrue(!pick.isEmpty() && pick.getDamageValue() > 0, "the pickaxe should be back, worn");
			helper.assertTrue(chest.countItem(Items.IRON_SWORD) == 1 && chest.countItem(Items.IRON_CHESTPLATE) == 1, "the sword and armor should be back");
			helper.assertTrue(ModAttachments.NETHER_TRIPS.getOrElse(villager, 0) == 1, "trips: " + ModAttachments.NETHER_TRIPS.getOrElse(villager, 0));
		});
	}

	/** No portal near the brazier: the netherworker says so and stays home. */
	@GameTest(template = AREA, timeoutTicks = 400, batch = "netherworker_no_portal")
	public void noPortalNoExpedition(GameTestHelper helper) {
		Leftovers.clear(helper);
		Villager villager = netherworker(helper, new ItemStack(Items.BREAD, 3));
		Container chest = helper.getBlockEntity(CHEST);
		helper.succeedWhen(() -> {
			WorkerStatus.Entry status = WorkerStatus.get(villager, helper.getLevel().getGameTime());
			helper.assertTrue(status != null && status.line().getContents() instanceof TranslatableContents t
				&& t.getKey().equals("message.aliveworkplace.netherworker.state.no_portal"), "status: " + (status == null ? null : status.line().getString()));
			helper.assertFalse(Netherworkers.isAway(villager), "went away without a portal");
			helper.assertTrue(chest.countItem(Items.BREAD) == 3, "the bread was taken");
		});
	}
}
