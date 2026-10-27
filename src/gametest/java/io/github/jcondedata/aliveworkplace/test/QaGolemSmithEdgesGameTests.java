package io.github.jcondedata.aliveworkplace.test;

import io.github.jcondedata.aliveworkplace.farm.FieldJob;
import io.github.jcondedata.aliveworkplace.guard.PatrolMapItem;
import io.github.jcondedata.aliveworkplace.guard.WallSentries;
import io.github.jcondedata.aliveworkplace.hall.Caravans;
import io.github.jcondedata.aliveworkplace.hall.Chronicle;
import io.github.jcondedata.aliveworkplace.legend.GolemSmith;
import io.github.jcondedata.aliveworkplace.legend.Legend;
import io.github.jcondedata.aliveworkplace.legend.LegendNeeds;
import io.github.jcondedata.aliveworkplace.legend.LegendPowers;
import io.github.jcondedata.aliveworkplace.legend.LegendRecord;
import io.github.jcondedata.aliveworkplace.legend.Legends;
import io.github.jcondedata.aliveworkplace.people.Moods;
import io.github.jcondedata.aliveworkplace.registry.ModAttachments;
import io.github.jcondedata.aliveworkplace.registry.ModBlocks;
import io.github.jcondedata.aliveworkplace.registry.ModComponents;
import io.github.jcondedata.aliveworkplace.registry.ModItems;
import io.github.jcondedata.aliveworkplace.store.HaulerGolems;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import net.fabricmc.fabric.api.event.player.UseEntityCallback;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.animal.IronGolem;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.npc.VillagerProfession;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CropBlock;
import net.minecraft.world.level.block.NetherWartBlock;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.phys.Vec3;

/**
 * QA for 29.15, the Golem Smith, written from the spec (qa-lane-d-1027-1612), aimed at what the earlier golem tests
 * leave open: one missing piece of a recipe (the shield, the 4th iron block, a plain pumpkin for a carved one) builds
 * nothing and takes nothing; the 2-day wait turns on the day's first tick and not one tick before; a smith working
 * in a roofed room gets their golem on the floor beside them, not on the roof; the hauler's and farmhand's names over
 * their heads; a plain iron golem doesn't haul; a farmhand picks only ripe crops and nether wart, replants them and
 * leaves unripe wheat, melons and flowers alone; a Wall Sentry fights a foe 4 blocks off its post without stepping
 * past its leash, lets one 7 blocks off be, and without a Patrol Map goes after a foe like any golem.
 */
public class QaGolemSmithEdgesGameTests implements net.fabricmc.fabric.api.gametest.v1.FabricGameTest {
	private static final String AREA = "aliveworkplace_test:huge_area";
	private static final BlockPos HALL = new BlockPos(3, 2, 3);
	private static final BlockPos TABLE = new BlockPos(10, 2, 10);

	private static boolean moods;
	private static boolean needs;

	static void setUp(GameTestHelper helper) {
		Leftovers.clear(helper);
		Leftovers.halls(helper);
		ServerLevel level = helper.getLevel();
		helper.setBlock(HALL, ModBlocks.VILLAGE_HALL);
		BlockPos hall = helper.absolutePos(HALL);
		Caravans.Data.get(level).setWants(hall, Component.literal("Anvilby"), List.of());
		moods = Moods.ENABLED;
		needs = LegendNeeds.ENABLED;
		Moods.ENABLED = false;
		LegendNeeds.ENABLED = false;
		Leftovers.after(helper, () -> {
			Moods.ENABLED = moods;
			LegendNeeds.ENABLED = needs;
			Legends.ENABLED = true;
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

	static Legend staged(GameTestHelper helper) {
		Legends.reload(helper.getLevel().getServer().getResourceManager());
		Legend smith = Legends.get(GolemSmith.ID).orElseThrow(() -> new AssertionError("golem_smith.json didn't load"));
		Map<ResourceLocation, Legend> map = new LinkedHashMap<>();
		map.put(GolemSmith.ID, smith);
		Legends.setForTest(map);
		return smith;
	}

	static Villager smith(GameTestHelper helper, Legend legend) {
		helper.setBlock(TABLE, Blocks.SMITHING_TABLE);
		helper.setBlock(TABLE.east(), Blocks.CHEST);
		Villager v = helper.spawn(EntityType.VILLAGER, TABLE.north(2));
		Legends.make(helper.getLevel(), v, legend, "test");
		v.getBrain().setMemory(MemoryModuleType.JOB_SITE, GlobalPos.of(helper.getLevel().dimension(), helper.absolutePos(TABLE)));
		return v;
	}

	static void villagers(GameTestHelper helper, int n, int z) {
		for (int i = 0; i < n; i++) {
			helper.spawn(EntityType.VILLAGER, new BlockPos(20 + i % 6, 2, z + 2 * (i / 6)));
		}
	}

	private static String contents(Container chest) {
		List<String> out = new ArrayList<>();
		for (int i = 0; i < chest.getContainerSize(); i++) {
			if (!chest.getItem(i).isEmpty()) {
				out.add(chest.getItem(i).getCount() + " " + chest.getItem(i).getItem());
			}
		}
		return out.toString();
	}

	/**
	 * Each recipe one piece short: a sentry's cost without the shield, a hauler's with 3 iron blocks, a farmhand's with
	 * a plain pumpkin for the carved one. Nothing is built, nothing is taken and the wait doesn't start; with the
	 * piece added, exactly the cost goes.
	 */
	//$ gametest_ticks_batch AREA '100' '"qaSmithEdgeShort"'
	@GameTest(template = AREA, timeoutTicks = 100, batch = "qaSmithEdgeShort")
	public void onePieceShortBuildsNothingAndTakesNothing(GameTestHelper helper) {
		setUp(helper);
		Legend legend = staged(helper);
		ServerLevel level = helper.getLevel();
		BlockPos hall = helper.absolutePos(HALL);
		Villager smith = smith(helper, legend);
		villagers(helper, 14, 4);
		Container chest = helper.getBlockEntity(TABLE.east());

		ModAttachments.GOLEM_FORGE.set(smith, new GolemSmith.Forge("sentry", -1L));
		chest.setItem(0, new ItemStack(Items.IRON_BLOCK, 4));
		chest.setItem(1, new ItemStack(Items.CARVED_PUMPKIN, 1));
		chest.setItem(2, new ItemStack(Items.IRON_HOE));
		chest.setItem(3, new ItemStack(Items.CHEST));
		String before = contents(chest);
		helper.assertTrue(GolemSmith.forgeOne(level, hall, smith) == null, "a sentry without a shield");
		helper.assertTrue(contents(chest).equals(before), "a refused sentry took: " + before + " -> " + contents(chest));
		helper.assertTrue(GolemSmith.forge(smith).lastDay() == -1L, "a refused sentry started the wait: " + GolemSmith.forge(smith));
		helper.assertTrue(GolemSmith.forged(level, hall) == 0, "a golem stands: " + GolemSmith.forged(level, hall));

		ModAttachments.GOLEM_FORGE.set(smith, new GolemSmith.Forge("hauler", -1L));
		chest.setItem(0, new ItemStack(Items.IRON_BLOCK, 3));
		before = contents(chest);
		helper.assertTrue(GolemSmith.forgeOne(level, hall, smith) == null, "a hauler from 3 iron blocks");
		helper.assertTrue(contents(chest).equals(before), "a refused hauler took: " + before + " -> " + contents(chest));

		ModAttachments.GOLEM_FORGE.set(smith, new GolemSmith.Forge("farmhand", -1L));
		chest.setItem(0, new ItemStack(Items.IRON_BLOCK, 4));
		chest.setItem(1, new ItemStack(Items.PUMPKIN, 1));
		before = contents(chest);
		helper.assertTrue(GolemSmith.forgeOne(level, hall, smith) == null, "a farmhand from a plain pumpkin");
		helper.assertTrue(contents(chest).equals(before), "a refused farmhand took: " + before + " -> " + contents(chest));
		helper.assertTrue(GolemSmith.forged(level, hall) == 0, "a golem stands after three refusals");

		chest.setItem(4, new ItemStack(Items.CARVED_PUMPKIN, 1));
		IronGolem farmhand = GolemSmith.forgeOne(level, hall, smith);
		helper.assertTrue(farmhand != null, "the whole farmhand cost built nothing: " + contents(chest));
		helper.assertTrue(chest.countItem(Items.IRON_BLOCK) == 0 && chest.countItem(Items.CARVED_PUMPKIN) == 0 && chest.countItem(Items.IRON_HOE) == 0,
			"the farmhand's cost wasn't taken: " + contents(chest));
		helper.assertTrue(chest.countItem(Items.PUMPKIN) == 1 && chest.countItem(Items.CHEST) == 1, "more than the cost was taken: " + contents(chest));
		String farmhandTitle = Component.translatable("entity.aliveworkplace.golem.farmhand").getString();
		helper.assertTrue(farmhandTitle.equals("Farmhand Golem") && farmhand.isCustomNameVisible() && farmhand.getCustomName() != null
			&& farmhand.getCustomName().getString().equals(farmhandTitle), "the farmhand's name: " + farmhand.getCustomName());
		helper.assertTrue(GolemSmith.role(farmhand) == GolemSmith.Role.FARMHAND && farmhand.getMaxHealth() == 100f,
			"a farmhand with a golem's usual health: " + GolemSmith.role(farmhand) + " " + farmhand.getMaxHealth());
		helper.assertTrue(GolemSmith.forge(smith).role() == GolemSmith.Role.FARMHAND && GolemSmith.forge(smith).lastDay() == Chronicle.day(level),
			"the forge after the farmhand: " + GolemSmith.forge(smith));
		helper.succeed();
	}

	/**
	 * The 2-day wait at its edge: a golem built on day D; at the last tick of day D+1 the smith still waits, at the first
	 * tick of day D+2 the next golem comes. The village's hauler gets its name over its head.
	 */
	//$ gametest_ticks_batch AREA '100' '"qaSmithEdgeWait"'
	@GameTest(template = AREA, timeoutTicks = 100, batch = "qaSmithEdgeWait")
	public void theWaitEndsOnTheSecondDaysFirstTick(GameTestHelper helper) {
		setUp(helper);
		ServerLevel level = helper.getLevel();
		long time = level.getDayTime();
		Leftovers.after(helper, () -> level.setDayTime(time));
		Legend legend = staged(helper);
		BlockPos hall = helper.absolutePos(HALL);
		Villager smith = smith(helper, legend);
		villagers(helper, 14, 4);
		Container chest = helper.getBlockEntity(TABLE.east());
		chest.setItem(0, new ItemStack(Items.IRON_BLOCK, 8));
		chest.setItem(1, new ItemStack(Items.CARVED_PUMPKIN, 2));
		chest.setItem(2, new ItemStack(Items.CHEST, 2));
		long day = 24000L; // a Minecraft day
		level.setDayTime(10 * day + 6000);
		long d = Chronicle.day(level);
		IronGolem first = GolemSmith.forgeOne(level, hall, smith);
		helper.assertTrue(first != null, "no first golem");
		String haulerTitle = Component.translatable("entity.aliveworkplace.golem.hauler").getString();
		helper.assertTrue(haulerTitle.equals("Hauler Golem") && first.isCustomNameVisible() && first.getCustomName() != null
			&& first.getCustomName().getString().equals(haulerTitle), "the hauler's name: " + first.getCustomName());
		helper.assertTrue(GolemSmith.forge(smith).lastDay() == d, "the forge's day: " + GolemSmith.forge(smith) + ", today " + d);
		level.setDayTime(11 * day + day - 1); // the last tick of the next day
		helper.assertTrue(Chronicle.day(level) == d + 1, "the clock: " + Chronicle.day(level));
		helper.assertTrue(GolemSmith.forgeOne(level, hall, smith) == null, "a second golem one tick before the 2 days were up");
		helper.assertTrue(chest.countItem(Items.IRON_BLOCK) == 4, "the early refusal took iron: " + chest.countItem(Items.IRON_BLOCK));
		level.setDayTime(12 * day); // the first tick of day D+2
		helper.assertTrue(Chronicle.day(level) == d + 2, "the clock: " + Chronicle.day(level));
		helper.assertTrue(GolemSmith.forgeOne(level, hall, smith) != null, "no second golem on the second day");
		helper.assertTrue(chest.countItem(Items.IRON_BLOCK) == 0 && GolemSmith.forged(level, hall) == 2, "after the second: " + contents(chest));
		helper.succeed();
	}

	/**
	 * A smith working in a roofed house (3 blocks of room under the roof, walls all round): the golem stands on the
	 * house's floor beside the smith, never on the roof or outside the walls.
	 */
	//$ gametest_ticks_batch AREA '100' '"qaSmithEdgeIndoors"'
	@GameTest(template = AREA, timeoutTicks = 100, batch = "qaSmithEdgeIndoors")
	public void aGolemBuiltIndoorsStandsOnTheFloorBesideTheSmith(GameTestHelper helper) {
		setUp(helper);
		Legend legend = staged(helper);
		ServerLevel level = helper.getLevel();
		BlockPos hall = helper.absolutePos(HALL);
		// a house: walls at x 6 and 14, z 4 and 14, a roof at y 5 (room at y 2-4)
		for (int x = 6; x <= 14; x++) {
			for (int z = 4; z <= 14; z++) {
				helper.setBlock(new BlockPos(x, 5, z), Blocks.OAK_PLANKS);
				if (x == 6 || x == 14 || z == 4 || z == 14) {
					for (int y = 2; y <= 4; y++) {
						helper.setBlock(new BlockPos(x, y, z), Blocks.COBBLESTONE);
					}
				}
			}
		}
		Villager smith = smith(helper, legend);
		villagers(helper, 4, 18);
		Container chest = helper.getBlockEntity(TABLE.east());
		chest.setItem(0, new ItemStack(Items.IRON_BLOCK, 4));
		chest.setItem(1, new ItemStack(Items.CARVED_PUMPKIN, 1));
		chest.setItem(2, new ItemStack(Items.CHEST, 1));
		IronGolem golem = GolemSmith.forgeOne(level, hall, smith);
		helper.assertTrue(golem != null, "no golem indoors");
		BlockPos at = golem.blockPosition().subtract(helper.absolutePos(BlockPos.ZERO));
		helper.assertTrue(at.getY() == 2, "the golem stands at y " + at.getY() + " (the floor is 2, the roof 5): " + at);
		helper.assertTrue(at.getX() > 6 && at.getX() < 14 && at.getZ() > 4 && at.getZ() < 14, "the golem stands outside the house: " + at);
		helper.assertTrue(golem.position().distanceTo(smith.position()) <= 4.5, "not beside the smith: " + golem.position().distanceTo(smith.position()));
		helper.assertTrue(level.noCollision(golem), "the golem stands in a block at " + at);
		helper.succeed();
	}

	/** A plain iron golem by a full Drop Box and a Storehouse stays a plain golem: it carries nothing for 300 ticks. */
	//$ gametest_ticks_batch AREA '400' '"qaSmithEdgePlain"'
	@GameTest(template = AREA, timeoutTicks = 400, batch = "qaSmithEdgePlain")
	public void aPlainGolemHaulsNothing(GameTestHelper helper) {
		Leftovers.clear(helper);
		helper.setBlock(new BlockPos(20, 2, 20), ModBlocks.STOREHOUSE);
		helper.setBlock(new BlockPos(21, 2, 20), Blocks.CHEST);
		BlockPos boxPos = new BlockPos(6, 2, 6);
		helper.setBlock(boxPos, ModBlocks.DROP_BOX);
		Container box = helper.getBlockEntity(boxPos);
		for (int i = 0; i < 4; i++) {
			box.setItem(i, new ItemStack(Items.COBBLESTONE, 64));
		}
		IronGolem golem = helper.spawn(EntityType.IRON_GOLEM, new BlockPos(12, 2, 12));
		Leftovers.after(helper, golem::discard);
		helper.assertTrue(GolemSmith.role(golem) == null, "a plain golem has a role: " + GolemSmith.role(golem));
		helper.onEachTick(() -> {
			helper.assertTrue(HaulerGolems.load(golem).isEmpty(), "a plain golem picked up " + HaulerGolems.load(golem));
			if (helper.getTick() >= 300) {
				helper.assertTrue(box.countItem(Items.COBBLESTONE) == 4 * 64, "the drop box lost " + (4 * 64 - box.countItem(Items.COBBLESTONE)));
				Container store = helper.getBlockEntity(new BlockPos(21, 2, 20));
				helper.assertTrue(store.isEmpty(), "the store got goods from a plain golem");
				helper.succeed();
			}
		});
	}

	/**
	 * A farmhand on a farmer's field: ripe wheat, carrots and nether wart are picked and replanted; unripe wheat, a melon
	 * and a poppy on the field are left as they are; the wheat goes into the chest by the farmer's composter, one each.
	 * Random ticks are off so nothing ripens on its own.
	 */
	//$ gametest_ticks_batch AREA '2400' '"qaSmithEdgeFarm"'
	@GameTest(template = AREA, timeoutTicks = 2400, batch = "qaSmithEdgeFarm")
	public void aFarmhandPicksOnlyRipeCropsAndLeavesTheRest(GameTestHelper helper) {
		Leftovers.clear(helper);
		ServerLevel level = helper.getLevel();
		int ticking = level.getGameRules().getInt(GameRules.RULE_RANDOMTICKING);
		level.getGameRules().getRule(GameRules.RULE_RANDOMTICKING).set(0, level.getServer());
		Leftovers.after(helper, () -> level.getGameRules().getRule(GameRules.RULE_RANDOMTICKING).set(ticking, level.getServer()));
		List<BlockPos> wheat = new ArrayList<>();
		List<BlockPos> carrots = new ArrayList<>();
		BlockPos unripe1 = new BlockPos(6, 2, 6);
		BlockPos unripe2 = new BlockPos(4, 2, 8);
		BlockPos wart = new BlockPos(8, 2, 8);
		BlockPos melon = new BlockPos(8, 2, 4);
		BlockPos poppy = new BlockPos(4, 2, 6);
		for (int x = 4; x <= 8; x++) {
			for (int z = 4; z <= 8; z++) {
				BlockPos top = new BlockPos(x, 2, z);
				if (top.equals(wart)) {
					helper.setBlock(top.below(), Blocks.SOUL_SAND);
					helper.setBlock(top, Blocks.NETHER_WART.defaultBlockState().setValue(NetherWartBlock.AGE, NetherWartBlock.MAX_AGE));
				} else if (top.equals(melon) || top.equals(poppy)) {
					helper.setBlock(top.below(), Blocks.DIRT);
					helper.setBlock(top, top.equals(melon) ? Blocks.MELON : Blocks.POPPY);
				} else {
					helper.setBlock(top.below(), Blocks.FARMLAND);
					if (top.equals(unripe1) || top.equals(unripe2)) {
						helper.setBlock(top, Blocks.WHEAT.defaultBlockState().setValue(CropBlock.AGE, 3));
					} else if (z == 4) {
						helper.setBlock(top, Blocks.CARROTS.defaultBlockState().setValue(CropBlock.AGE, 7));
						carrots.add(top);
					} else {
						helper.setBlock(top, Blocks.WHEAT.defaultBlockState().setValue(CropBlock.AGE, 7));
						wheat.add(top);
					}
				}
			}
		}
		BlockPos composter = new BlockPos(12, 2, 6);
		BlockPos chestPos = new BlockPos(13, 2, 6);
		helper.setBlock(composter, Blocks.COMPOSTER);
		helper.setBlock(chestPos, Blocks.CHEST);
		Villager farmer = helper.spawn(EntityType.VILLAGER, new BlockPos(12, 2, 8));
		farmer.setVillagerData(farmer.getVillagerData().setProfession(VillagerProfession.FARMER));
		farmer.refreshBrain(level);
		farmer.setNoAi(true);
		farmer.getBrain().setMemory(MemoryModuleType.JOB_SITE, GlobalPos.of(level.dimension(), helper.absolutePos(composter)));
		ModAttachments.FARM_FIELD.set(farmer, new FieldJob(BoundingBox.fromCorners(helper.absolutePos(new BlockPos(4, 1, 4)), helper.absolutePos(new BlockPos(8, 1, 8)))));
		IronGolem golem = GolemSmith.build(level, helper.absolutePos(new BlockPos(12, 1, 12)), GolemSmith.Role.FARMHAND);
		helper.assertTrue(golem != null, "a farmhand");
		Leftovers.after(helper, () -> {
			golem.discard();
			farmer.discard();
		});
		helper.onEachTick(() -> {
			for (BlockPos p : List.of(unripe1, unripe2)) {
				helper.assertTrue(helper.getBlockState(p).is(Blocks.WHEAT) && helper.getBlockState(p).getValue(CropBlock.AGE) == 3,
					"unripe wheat at " + p + " was touched: " + helper.getBlockState(p));
			}
			helper.assertBlockPresent(Blocks.MELON, melon);
			helper.assertBlockPresent(Blocks.POPPY, poppy);
			helper.assertBlockPresent(Blocks.SOUL_SAND, wart.below());
		});
		helper.succeedWhen(() -> {
			Container chest = helper.getBlockEntity(chestPos);
			for (BlockPos p : wheat) {
				helper.assertTrue(helper.getBlockState(p).is(Blocks.WHEAT) && helper.getBlockState(p).getValue(CropBlock.AGE) == 0, "wheat at " + p + ": " + helper.getBlockState(p));
				helper.assertBlockPresent(Blocks.FARMLAND, p.below());
			}
			for (BlockPos p : carrots) {
				helper.assertTrue(helper.getBlockState(p).is(Blocks.CARROTS) && helper.getBlockState(p).getValue(CropBlock.AGE) == 0, "carrots at " + p + ": " + helper.getBlockState(p));
			}
			helper.assertTrue(helper.getBlockState(wart).is(Blocks.NETHER_WART) && helper.getBlockState(wart).getValue(NetherWartBlock.AGE) == 0,
				"the nether wart: " + helper.getBlockState(wart));
			helper.assertTrue(HaulerGolems.load(golem).isEmpty(), "still carrying " + HaulerGolems.load(golem));
			helper.assertTrue(chest.countItem(Items.WHEAT) == wheat.size(), "the chest has " + chest.countItem(Items.WHEAT) + " wheat of " + wheat.size());
			helper.assertTrue(chest.countItem(Items.NETHER_WART) >= 1, "no nether wart in the chest: " + contents(chest));
			helper.assertTrue(chest.countItem(Items.MELON_SLICE) == 0 && chest.countItem(Items.POPPY) == 0, "the chest has melon or poppy: " + contents(chest));
		});
	}

	private static double horizontal(Vec3 a, Vec3 b) {
		double dx = a.x - b.x;
		double dz = a.z - b.z;
		return Math.sqrt(dx * dx + dz * dz);
	}

	private static IronGolem postedSentry(GameTestHelper helper, BlockPos post, String name) {
		ServerLevel level = helper.getLevel();
		IronGolem golem = GolemSmith.build(level, helper.absolutePos(new BlockPos(post.getX() - 1, 1, post.getZ())), GolemSmith.Role.SENTRY);
		helper.assertTrue(golem != null, "no sentry");
		Leftovers.after(helper, golem::discard);
		ServerPlayer player = RealmGameTests.listener(helper, new ArrayList<>(), name);
		ItemStack map = new ItemStack(ModItems.PATROL_MAP);
		map.set(ModComponents.PATROL, new PatrolMapItem.Route(Optional.of(level.dimension().location()), List.of(helper.absolutePos(post))));
		player.setItemInHand(InteractionHand.MAIN_HAND, map);
		player.setShiftKeyDown(true);
		UseEntityCallback.EVENT.invoker().interact(player, level, InteractionHand.MAIN_HAND, golem, null);
		level.getServer().getPlayerList().remove(player);
		helper.assertTrue(helper.absolutePos(post).equals(WallSentries.post(golem)), "not posted: " + WallSentries.post(golem));
		return golem;
	}

	private static Zombie standingFoe(GameTestHelper helper, BlockPos at) {
		Zombie zombie = helper.spawn(EntityType.ZOMBIE, at);
		zombie.setNoAi(true);
		zombie.setItemSlot(EquipmentSlot.HEAD, new ItemStack(Items.LEATHER_HELMET)); // (no burning in the sun)
		return zombie;
	}

	/**
	 * A sentry at its post with a zombie standing 4 blocks off (inside the 6 it holds): it fights and kills it, and never
	 * steps more than its leash (3 blocks) from the post.
	 */
	//$ gametest_ticks_batch AREA '800' '"qaSmithEdgeNear"'
	@GameTest(template = AREA, timeoutTicks = 800, batch = "qaSmithEdgeNear")
	public void aSentryFightsAFoeFourBlocksOffWithinItsLeash(GameTestHelper helper) {
		Leftovers.clear(helper);
		BlockPos post = new BlockPos(15, 2, 15);
		IronGolem golem = postedSentry(helper, post, "qa-edge-near");
		Vec3 at = Vec3.atBottomCenterOf(helper.absolutePos(post));
		Zombie[] foe = {null};
		double[] farthest = {0};
		helper.onEachTick(() -> {
			double d = horizontal(golem.position(), at);
			if (foe[0] == null && d < 1.5) {
				foe[0] = standingFoe(helper, new BlockPos(19, 2, 15));
			}
			if (foe[0] != null) {
				farthest[0] = Math.max(farthest[0], d);
				helper.assertTrue(d <= WallSentries.LEASH + 0.5, "the sentry stepped " + d + " blocks off its post");
			}
		});
		helper.succeedWhen(() -> {
			helper.assertTrue(foe[0] != null, "the sentry never reached its post");
			helper.assertTrue(!foe[0].isAlive(), "the zombie 4 blocks off is still standing (" + foe[0].getHealth() + " health); the sentry went " + farthest[0] + " off");
		});
	}

	/** A zombie standing 7 blocks off the post (one past the 6 it holds) is left alone for 200 ticks. */
	//$ gametest_ticks_batch AREA '600' '"qaSmithEdgeFar"'
	@GameTest(template = AREA, timeoutTicks = 600, batch = "qaSmithEdgeFar")
	public void aSentryLeavesAFoeSevenBlocksOffAlone(GameTestHelper helper) {
		Leftovers.clear(helper);
		BlockPos post = new BlockPos(12, 2, 15);
		IronGolem golem = postedSentry(helper, post, "qa-edge-far");
		Vec3 at = Vec3.atBottomCenterOf(helper.absolutePos(post));
		Zombie[] foe = {null};
		long[] start = {-1};
		helper.onEachTick(() -> {
			double d = horizontal(golem.position(), at);
			if (foe[0] == null && d < 1.5) {
				foe[0] = standingFoe(helper, new BlockPos(19, 2, 15));
				start[0] = helper.getTick();
			}
			if (foe[0] != null) {
				helper.assertTrue(d <= WallSentries.LEASH + 0.5, "the sentry stepped " + d + " blocks off its post");
				if (helper.getTick() - start[0] >= 200) {
					helper.assertTrue(foe[0].isAlive() && foe[0].getHealth() == foe[0].getMaxHealth(), "the foe 7 blocks off was hurt: " + foe[0].getHealth());
					helper.succeed();
				}
			}
		});
	}

	/** A Wall Sentry never given a Patrol Map is an ordinary golem: it goes after a zombie 10 blocks away and kills it. */
	//$ gametest_ticks_batch AREA '800' '"qaSmithEdgeNoMap"'
	@GameTest(template = AREA, timeoutTicks = 800, batch = "qaSmithEdgeNoMap")
	public void aSentryWithoutAMapFightsLikeAnyGolem(GameTestHelper helper) {
		Leftovers.clear(helper);
		IronGolem golem = GolemSmith.build(helper.getLevel(), helper.absolutePos(new BlockPos(5, 1, 15)), GolemSmith.Role.SENTRY);
		helper.assertTrue(golem != null, "no sentry");
		Leftovers.after(helper, golem::discard);
		helper.assertTrue(WallSentries.post(golem) == null, "a post without a map: " + WallSentries.post(golem));
		Vec3 start = golem.position();
		Zombie foe = standingFoe(helper, new BlockPos(16, 2, 15));
		helper.succeedWhen(() -> {
			helper.assertTrue(!foe.isAlive(), "the zombie 10 blocks off is still standing; the golem is " + horizontal(golem.position(), start) + " from where it was built");
			helper.assertTrue(golem.isAlive() && GolemSmith.role(golem) == GolemSmith.Role.SENTRY, "the sentry");
		});
	}
}
