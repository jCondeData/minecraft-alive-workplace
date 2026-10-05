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
 * The Golem Smith (ROADMAP 29.15): the legend file, the forge's costs, cap and wait, the choice, the hall's lines and the
 * config switch, the role saved on the
 * golem, the twice-as-fast mending, a Hauler Golem taking 9 stacks to the Storehouse, a Farmhand Golem harvesting and
 * replanting a 9x9 wheat field into its chest, and a Wall Sentry holding its post through a fight.
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

	/** The switches as they were before {@link #staged} (off in tests: putting them back on leaked moods into later tests). */
	private static boolean moods;
	private static boolean needs;

	private static Legend staged(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		moods = Moods.ENABLED;
		needs = LegendNeeds.ENABLED;
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
		Moods.ENABLED = moods;
		LegendNeeds.ENABLED = needs;
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
		int[] mostCarried = {0};
		helper.onEachTick(() -> mostCarried[0] = Math.max(mostCarried[0], HaulerGolems.load(golem).values().stream().mapToInt(Integer::intValue).sum()));
		helper.succeedWhen(() -> {
			Container store = helper.getBlockEntity(storeChest);
			helper.assertTrue(store.countItem(Items.COBBLESTONE) >= 9 * 64, "the store has " + store.countItem(Items.COBBLESTONE) + " cobblestone");
			helper.assertTrue(mostCarried[0] == 9 * 64, "9 stacks a trip, carried at most " + mostCarried[0]);
			helper.assertTrue(box.countItem(Items.COBBLESTONE) + store.countItem(Items.COBBLESTONE) + HaulerGolems.load(golem).values().stream().mapToInt(Integer::intValue).sum()
				== 12 * 64, "nothing lost on the way");
		});
	}

	/** A Farmhand Golem harvests a ripe 9x9 wheat field, plants every spot again and carries the wheat to the field's chest. */
	//$ gametest_ticks_batch AREA '3000' '"golemSmithFarmhand"'
	@GameTest(template = AREA, timeoutTicks = 3000, batch = "golemSmithFarmhand")
	public void farmhandHarvestsAndReplantsANineByNineField(GameTestHelper helper) {
		Leftovers.clear(helper);
		ServerLevel level = helper.getLevel();
		for (int x = 4; x <= 12; x++) {
			for (int z = 4; z <= 12; z++) {
				helper.setBlock(new BlockPos(x, 1, z), Blocks.FARMLAND);
				helper.setBlock(new BlockPos(x, 2, z), Blocks.WHEAT.defaultBlockState().setValue(net.minecraft.world.level.block.CropBlock.AGE, 7));
			}
		}
		BlockPos composter = new BlockPos(16, 2, 8);
		BlockPos chestPos = new BlockPos(17, 2, 8);
		helper.setBlock(composter, Blocks.COMPOSTER);
		helper.setBlock(chestPos, Blocks.CHEST);
		Villager farmer = helper.spawn(EntityType.VILLAGER, new BlockPos(16, 2, 10));
		farmer.setVillagerData(farmer.getVillagerData().setProfession(net.minecraft.world.entity.npc.VillagerProfession.FARMER));
		farmer.setNoAi(true); // the golem's field: the farmer looks on
		farmer.getBrain().setMemory(MemoryModuleType.JOB_SITE, GlobalPos.of(level.dimension(), helper.absolutePos(composter)));
		ModAttachments.FARM_FIELD.set(farmer, new io.github.jcondedata.aliveworkplace.farm.FieldJob(
			net.minecraft.world.level.levelgen.structure.BoundingBox.fromCorners(helper.absolutePos(new BlockPos(4, 1, 4)), helper.absolutePos(new BlockPos(12, 1, 12)))));
		IronGolem golem = GolemSmith.build(level, helper.absolutePos(new BlockPos(16, 1, 14)), GolemSmith.Role.FARMHAND);
		helper.assertTrue(golem != null, "a farmhand");
		Leftovers.after(helper, () -> {
			golem.discard();
			farmer.discard();
		});
		helper.succeedWhen(() -> {
			Container chest = helper.getBlockEntity(chestPos);
			helper.assertTrue(chest.countItem(Items.WHEAT) == 81, "the chest has " + chest.countItem(Items.WHEAT) + " wheat");
			for (int x = 4; x <= 12; x++) {
				for (int z = 4; z <= 12; z++) {
					helper.assertBlockPresent(Blocks.WHEAT, new BlockPos(x, 2, z));
					helper.assertBlockPresent(Blocks.FARMLAND, new BlockPos(x, 1, z));
				}
			}
			helper.assertTrue(HaulerGolems.load(golem).isEmpty(), "the harvest all went in the chest");
		});
	}

	/**
	 * A Wall Sentry given a Patrol Map goes to its first point and holds it through a fight with three zombies: it never
	 * steps more than its leash off the post, has twice a golem's health, and the zombies fall.
	 */
	//$ gametest_ticks_batch AREA '1600' '"golemSmithSentry"'
	@GameTest(template = AREA, timeoutTicks = 1600, batch = "golemSmithSentry")
	public void sentryHoldsItsPointThroughAFight(GameTestHelper helper) {
		Leftovers.clear(helper);
		ServerLevel level = helper.getLevel();
		IronGolem golem = GolemSmith.build(level, helper.absolutePos(new BlockPos(4, 1, 4)), GolemSmith.Role.SENTRY);
		helper.assertTrue(golem != null, "a sentry");
		Leftovers.after(helper, golem::discard);
		helper.assertTrue(golem.getMaxHealth() == 200f && golem.getHealth() == 200f, "twice a golem's health: " + golem.getMaxHealth());
		BlockPos post = helper.absolutePos(new BlockPos(15, 2, 15));
		net.minecraft.server.level.ServerPlayer player = helper.makeMockServerPlayerInLevel();
		ItemStack map = new ItemStack(io.github.jcondedata.aliveworkplace.registry.ModItems.PATROL_MAP);
		map.set(io.github.jcondedata.aliveworkplace.registry.ModComponents.PATROL, new io.github.jcondedata.aliveworkplace.guard.PatrolMapItem.Route(
			java.util.Optional.of(level.dimension().location()), List.of(post, helper.absolutePos(new BlockPos(20, 2, 20)))));
		player.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND, map);
		player.setShiftKeyDown(true);
		net.fabricmc.fabric.api.event.player.UseEntityCallback.EVENT.invoker().interact(player, level, net.minecraft.world.InteractionHand.MAIN_HAND, golem, null);
		level.getServer().getPlayerList().remove(player);
		helper.assertTrue(post.equals(ModAttachments.GOLEM_POST.get(golem)), "the map's first point is its post: " + ModAttachments.GOLEM_POST.get(golem));
		net.minecraft.world.phys.Vec3 at = net.minecraft.world.phys.Vec3.atBottomCenterOf(post);
		List<net.minecraft.world.entity.monster.Zombie> zombies = new java.util.ArrayList<>();
		helper.onEachTick(() -> {
			double d = horizontal(golem.position(), at);
			if (zombies.isEmpty() && d < 1.5) {
				for (BlockPos z : List.of(new BlockPos(19, 2, 15), new BlockPos(15, 2, 19), new BlockPos(11, 2, 15))) {
					net.minecraft.world.entity.monster.Zombie zombie = helper.spawn(EntityType.ZOMBIE, z);
					zombie.setItemSlot(net.minecraft.world.entity.EquipmentSlot.HEAD, new ItemStack(Items.LEATHER_HELMET)); // no burning in the sun
					zombie.setTarget(golem);
					zombies.add(zombie);
				}
			}
			if (!zombies.isEmpty()) {
				helper.assertTrue(d <= io.github.jcondedata.aliveworkplace.guard.WallSentries.LEASH + 0.5, "the sentry left its post: " + d + " blocks off");
			}
		});
		helper.succeedWhen(() -> {
			helper.assertTrue(!zombies.isEmpty(), "the sentry reached its post");
			helper.assertTrue(zombies.stream().noneMatch(net.minecraft.world.entity.Entity::isAlive), "zombies still standing");
			helper.assertTrue(golem.isAlive(), "the sentry fell");
			helper.assertTrue(horizontal(golem.position(), at) <= 2.0, "back at its post");
		});
	}

	private static double horizontal(net.minecraft.world.phys.Vec3 a, net.minecraft.world.phys.Vec3 b) {
		double dx = a.x - b.x;
		double dz = a.z - b.z;
		return Math.sqrt(dx * dx + dz * dz);
	}

	/**
	 * A sneak-right-click goes through the three golems; a farmhand takes an iron hoe and a sentry a shield; the hall
	 * lists each forged golem with what it's doing; with Legends switched off, nothing is forged.
	 */
	//$ gametest_ticks_batch AREA '100' '"golemSmithChoice"'
	@GameTest(template = AREA, timeoutTicks = 100, batch = "golemSmithChoice")
	public void golemSmithChoiceHallAndSwitch(GameTestHelper helper) {
		setUp(helper);
		try {
			Legend legend = staged(helper);
			ServerLevel level = helper.getLevel();
			BlockPos hall = helper.absolutePos(HALL);
			Villager smith = smith(helper, legend);
			for (int i = 0; i < 9; i++) {
				helper.spawn(EntityType.VILLAGER, new BlockPos(14 + i % 5, 2, 4 + 2 * (i / 5)));
			}
			net.minecraft.server.level.ServerPlayer player = helper.makeMockServerPlayerInLevel();
			player.setShiftKeyDown(true);
			player.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND, ItemStack.EMPTY);
			net.fabricmc.fabric.api.event.player.UseEntityCallback.EVENT.invoker().interact(player, level, net.minecraft.world.InteractionHand.MAIN_HAND, smith, null);
			helper.assertTrue(GolemSmith.forge(smith).role() == GolemSmith.Role.FARMHAND, "first choice: " + GolemSmith.forge(smith).next());
			Container chest = helper.getBlockEntity(TABLE.east());
			chest.setItem(0, new ItemStack(Items.IRON_BLOCK, 8));
			chest.setItem(1, new ItemStack(Items.CARVED_PUMPKIN, 2));
			chest.setItem(2, new ItemStack(Items.CHEST, 1));
			helper.assertTrue(GolemSmith.forgeOne(level, hall, smith) == null, "a chest is no hoe: no farmhand");
			chest.setItem(3, new ItemStack(Items.IRON_HOE));
			IronGolem farmhand = GolemSmith.forgeOne(level, hall, smith);
			helper.assertTrue(farmhand != null && GolemSmith.role(farmhand) == GolemSmith.Role.FARMHAND && chest.countItem(Items.IRON_HOE) == 0,
				"a farmhand for the hoe");
			net.fabricmc.fabric.api.event.player.UseEntityCallback.EVENT.invoker().interact(player, level, net.minecraft.world.InteractionHand.MAIN_HAND, smith, null);
			helper.assertTrue(GolemSmith.forge(smith).role() == GolemSmith.Role.SENTRY, "second choice: " + GolemSmith.forge(smith).next());
			ModAttachments.GOLEM_FORGE.set(smith, new GolemSmith.Forge("sentry", Chronicle.day(level) - 2));
			chest.setItem(4, new ItemStack(Items.SHIELD));
			IronGolem sentry = GolemSmith.forgeOne(level, hall, smith);
			helper.assertTrue(sentry != null && GolemSmith.role(sentry) == GolemSmith.Role.SENTRY && chest.countItem(Items.SHIELD) == 0, "a sentry for the shield");
			net.fabricmc.fabric.api.event.player.UseEntityCallback.EVENT.invoker().interact(player, level, net.minecraft.world.InteractionHand.MAIN_HAND, smith, null);
			helper.assertTrue(GolemSmith.forge(smith).role() == GolemSmith.Role.HAULER, "and round to the hauler: " + GolemSmith.forge(smith).next());
			level.getServer().getPlayerList().remove(player);
			List<String> keys = GolemSmith.hallLines(level, hall).stream()
				.map(c -> ((net.minecraft.network.chat.contents.TranslatableContents) c.getContents()).getArgs()[1])
				.map(a -> ((net.minecraft.network.chat.contents.TranslatableContents) ((Component) a).getContents()).getKey()).toList();
			helper.assertTrue(keys.equals(List.of("screen.aliveworkplace.hall.golem.tending", "screen.aliveworkplace.hall.golem.no_post")),
				"the hall's lines: " + keys);
			ModAttachments.GOLEM_FORGE.set(smith, new GolemSmith.Forge("hauler", Chronicle.day(level) - 2));
			for (int i = 0; i < 6; i++) {
				helper.spawn(EntityType.VILLAGER, new BlockPos(14 + i, 2, 10));
			}
			Legends.ENABLED = false;
			try {
				helper.assertTrue(GolemSmith.forgeOne(level, hall, smith) == null, "Legends off: no golem");
			} finally {
				Legends.ENABLED = true;
			}
			helper.succeed();
		} finally {
			restore();
		}
	}
}
