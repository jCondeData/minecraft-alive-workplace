package io.github.jcondedata.aliveworkplace.test;

import io.github.jcondedata.aliveworkplace.Expansions;
import io.github.jcondedata.aliveworkplace.blueprint.BlueprintData;
import io.github.jcondedata.aliveworkplace.build.BuildSiteManager;
import io.github.jcondedata.aliveworkplace.hall.Chronicle;
import io.github.jcondedata.aliveworkplace.hall.Festivals;
import io.github.jcondedata.aliveworkplace.hall.VillageGrowth;
import io.github.jcondedata.aliveworkplace.hall.VillageHallBlockEntity;
import io.github.jcondedata.aliveworkplace.hall.VillageHallScreen;
import io.github.jcondedata.aliveworkplace.hall.VillageHalls;
import io.github.jcondedata.aliveworkplace.hall.VillageNeeds;
import io.github.jcondedata.aliveworkplace.people.Sickness;
import io.github.jcondedata.aliveworkplace.registry.ModAttachments;
import io.github.jcondedata.aliveworkplace.registry.ModBlocks;
import io.github.jcondedata.aliveworkplace.registry.ModVillagers;
import io.github.jcondedata.aliveworkplace.story.ArcState;
import io.github.jcondedata.aliveworkplace.story.Arcs;
import io.github.jcondedata.aliveworkplace.story.SicknessStory;
import io.github.jcondedata.aliveworkplace.threat.VillageFire;
import io.github.jcondedata.aliveworkplace.work.ChoiceMenu;
import io.github.jcondedata.aliveworkplace.work.FirewardenWork;
import io.github.jcondedata.aliveworkplace.work.Jobs;
import io.github.jcondedata.aliveworkplace.work.PhysicianWork;
import io.github.jcondedata.aliveworkplace.work.Stations;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Vec3i;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.Container;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.npc.VillagerProfession;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.block.BaseFireBlock;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.properties.AttachFace;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.phys.AABB;

/**
 * QA (lane-c-1101-2153) for B135, B152 and B153, written from the bugs' specs.
 *
 * <p>B135: the Sickness arc's healer is "its Nurse, or 32.18's Physician": a Physician beyond the hall's reach isn't the
 * village's (Doctor Wren is sent for), of two Physicians the one nearest the hall is the healer, the disasters expansion
 * gates the Physician as it gates the rest of their work, and a jobless or Nitwit villager is never the healer.
 *
 * <p>B152: "a firefighter's held item is never lost": a brigade member with an empty hand ends empty-handed (no bucket
 * left over), one holding a stack of bread gets the whole stack back, and a tool kept aside while the hand holds
 * something else by then drops at their feet rather than vanish.
 *
 * <p>B153: "the hall shows the same meal count growth uses": every not-a-meal food in the store (rotten flesh, spider eye,
 * golden food, pufferfish, …) never shows, meals from a kitchen's chest and a Storehouse's chest add up, a chest by
 * neither isn't counted by either, and a store of only rotten flesh reads "Food in store: 0".
 */
public class QaHealerFoodFireGameTests implements FabricGameTest {
	private static final String TALL = "aliveworkplace_test:tall_area";
	private static final String AREA = "aliveworkplace_test:big_area";
	private static final BlockPos SICK_HALL = new BlockPos(15, 2, 15);

	// ---- B135: the Sickness arc's healer ----------------------------------------------------------------------------

	private static void sickness(GameTestHelper helper) {
		int radius = VillageHalls.RADIUS;
		int near = Arcs.NEAR;
		boolean sick = Sickness.ENABLED;
		boolean festivals = Festivals.ENABLED;
		boolean physicians = PhysicianWork.ENABLED;
		boolean open = Expansions.openForTests;
		SicknessStory.Locator locator = SicknessStory.LOCATOR;
		VillageHalls.RADIUS = 12;
		Arcs.NEAR = 0;
		Sickness.ENABLED = true;
		Festivals.ENABLED = true;
		PhysicianWork.ENABLED = true;
		Map<String, Integer> east = Map.of("sea_lavender", 200, "mirewort", 400, "glowcap", 600);
		SicknessStory.LOCATOR = (level, from, herb) -> {
			Integer d = east.get(BuiltInRegistries.BLOCK.getKey(herb).getPath());
			return d == null ? null : from.offset(d, 0, 0);
		};
		Leftovers.after(helper, () -> {
			VillageHalls.RADIUS = radius;
			Arcs.NEAR = near;
			Sickness.ENABLED = sick;
			Festivals.ENABLED = festivals;
			PhysicianWork.ENABLED = physicians;
			Expansions.openForTests = open;
			SicknessStory.LOCATOR = locator;
		});
	}

	private static void sickHall(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		BlockPos abs = helper.absolutePos(SICK_HALL);
		Leftovers.after(helper, () -> Arcs.stopAll(level, abs));
		helper.setBlock(SICK_HALL, ModBlocks.VILLAGE_HALL);
		VillageHallBlockEntity entity = (VillageHallBlockEntity) level.getBlockEntity(abs);
		entity.setLastQuestDay(Long.MAX_VALUE / 2);
		Arcs.stopAll(level, abs);
	}

	private static Villager still(GameTestHelper helper, int x, int z, String name) {
		Villager v = helper.spawn(EntityType.VILLAGER, new BlockPos(x, 2, z));
		v.setNoAi(true);
		v.setCustomName(Component.literal(name));
		return v;
	}

	private static List<Villager> village(GameTestHelper helper) {
		List<Villager> out = new ArrayList<>();
		for (int i = 0; i < 8; i++) {
			out.add(still(helper, 11 + (i % 4) * 3, 12 + (i / 4) * 3, "Villager " + (i + 1)));
		}
		return out;
	}

	/** A Physician at a brewing stand at {@code stand}, standing one block east of it. */
	private static Villager physicianAt(GameTestHelper helper, BlockPos stand, String name) {
		helper.setBlock(stand, Blocks.BREWING_STAND);
		Villager v = still(helper, stand.getX() + 1, stand.getZ(), name);
		Stations.assign(helper.getLevel(), v, helper.absolutePos(stand), ModVillagers.PHYSICIAN);
		helper.assertTrue(PhysicianWork.isPhysician(v), "no Physician at " + stand.toShortString());
		return v;
	}

	private static boolean wrenAbout(ArcState s) {
		return s.mobs.stream().anyMatch(m -> m.key.equals(SicknessStory.WREN)) || SicknessStory.stamp(s, "wren_due") >= 0;
	}

	private static ArcState startSickness(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		level.setDayTime(Chronicle.day(level) * VillageNeeds.DAY + 1000);
		Arcs.Arc arc = Arcs.find("the_sickness").orElse(null);
		helper.assertTrue(arc != null, "the Sickness didn't load");
		ArcState s = Arcs.start(level, helper.absolutePos(SICK_HALL), arc);
		helper.assertTrue(s != null && s.started, "the arc didn't start");
		return s;
	}

	/**
	 * A Physician who works at a brewing stand just beyond the hall's reach isn't this village's healer: with no Nurse
	 * or Physician within the hall's area, Doctor Wren is sent for.
	 */
	//$ gametest_batch TALL '"qaPhysicianBeyondTheHall"'
	@GameTest(template = TALL, batch = "qaPhysicianBeyondTheHall")
	public void aPhysicianBeyondTheHallsReachIsntTheHealer(GameTestHelper helper) {
		Leftovers.clear(helper);
		sickness(helper);
		sickHall(helper);
		village(helper);
		// The hall's area is the hall block grown by 12: x 3..28. The Physician stands at x 29.
		Villager far = physicianAt(helper, new BlockPos(28, 2, 26), "Physician Far");
		helper.assertTrue(!VillageHalls.area(helper.absolutePos(SICK_HALL)).intersects(far.getBoundingBox()),
			"the far Physician is inside the hall's area after all");
		ArcState s = startSickness(helper);
		helper.assertTrue(SicknessStory.healer(helper.getLevel(), s) != far, "a Physician outside the village became its healer");
		helper.assertTrue(wrenAbout(s), "Doctor Wren wasn't sent for though the village has no healer of its own");
		helper.succeed();
	}

	/** Of two Physicians in the village (and no Nurse), the one nearest the hall is the healer, and no Doctor Wren comes. */
	//$ gametest_batch TALL '"qaNearestPhysician"'
	@GameTest(template = TALL, batch = "qaNearestPhysician")
	public void ofTwoPhysiciansTheNearestTheHallIsTheHealer(GameTestHelper helper) {
		Leftovers.clear(helper);
		sickness(helper);
		sickHall(helper);
		village(helper);
		Villager further = physicianAt(helper, new BlockPos(22, 2, 22), "Physician Further");
		Villager nearer = physicianAt(helper, new BlockPos(16, 2, 18), "Physician Nearer");
		ArcState s = startSickness(helper);
		Villager healer = SicknessStory.healer(helper.getLevel(), s);
		helper.assertTrue(healer == nearer, "the healer is " + (healer == null ? "nobody" : healer.getName().getString()) + ", not the nearer Physician");
		helper.assertTrue(healer != further && !wrenAbout(s), "Doctor Wren was sent for with two Physicians in the village");
		helper.succeed();
	}

	/**
	 * Who can be the healer by job: a Nurse always; a Physician only while config {@code physicians} is on and the
	 * disasters expansion is open (as for the rest of their work); never a jobless villager standing by a brewing stand,
	 * nor a Nitwit.
	 */
	//$ gametest_batch TALL '"qaHealerJobs"'
	@GameTest(template = TALL, batch = "qaHealerJobs")
	public void whoCanBeTheHealerByJob(GameTestHelper helper) {
		Leftovers.clear(helper);
		sickness(helper);
		ServerLevel level = helper.getLevel();
		Villager physician = physicianAt(helper, new BlockPos(20, 2, 20), "Physician Mara");
		helper.setBlock(new BlockPos(10, 2, 10), Blocks.BREWING_STAND);
		Villager nurse = still(helper, 11, 10, "Nurse Ada");
		Jobs.employ(level, nurse, helper.absolutePos(new BlockPos(10, 2, 10)), ModVillagers.NURSE_STATION_POI, ModVillagers.NURSE);
		helper.setBlock(new BlockPos(5, 2, 5), Blocks.BREWING_STAND);
		Villager jobless = still(helper, 6, 5, "Jobless Ned");
		Villager nitwit = still(helper, 6, 8, "Nitwit Ott");
		nitwit.setVillagerData(nitwit.getVillagerData().setProfession(VillagerProfession.NITWIT));

		helper.assertTrue(SicknessStory.isHealerJob(nurse), "a Nurse isn't a healer");
		helper.assertTrue(SicknessStory.isHealerJob(physician), "a Physician isn't a healer (B135)");
		helper.assertTrue(!SicknessStory.isHealerJob(jobless), "a jobless villager by a brewing stand counts as a healer");
		helper.assertTrue(!SicknessStory.isHealerJob(nitwit), "a Nitwit counts as a healer");
		Expansions.openForTests = false;
		boolean physicianClosed = SicknessStory.isHealerJob(physician);
		boolean nurseClosed = SicknessStory.isHealerJob(nurse);
		Expansions.openForTests = true;
		helper.assertTrue(!physicianClosed, "a Physician counts as a healer with the disasters expansion closed");
		helper.assertTrue(nurseClosed, "the disasters expansion closed stopped a Nurse being a healer");
		PhysicianWork.ENABLED = false;
		helper.assertTrue(!SicknessStory.isHealerJob(physician), "a Physician counts as a healer with config physicians off");
		helper.succeed();
	}

	// ---- B152: the bucket brigade gives back what it held -----------------------------------------------------------

	private static final BlockPos FIRE_HALL = new BlockPos(19, 2, 2);
	private static final BlockPos HUT = new BlockPos(12, 2, 12);
	private static final BlockPos ROOF_TOP = new BlockPos(14, 5, 14);
	private static final BlockPos GRINDSTONE = new BlockPos(3, 2, 3);

	/** A hall and a finished log hut (a build of the village), fire ticking, no rain, other Firewardens far away. */
	private static ServerLevel hut(GameTestHelper helper, String name) {
		Leftovers.clear(helper);
		Leftovers.halls(helper);
		Leftovers.finished(helper);
		ServerLevel level = helper.getLevel();
		int radius = VillageHalls.RADIUS;
		boolean fireTick = level.getGameRules().getBoolean(GameRules.RULE_DOFIRETICK);
		VillageHalls.RADIUS = 20;
		level.getGameRules().getRule(GameRules.RULE_DOFIRETICK).set(true, level.getServer());
		VillageFire.reset();
		level.setWeatherParameters(6000, 0, false, false);
		level.setRainLevel(0f);
		level.setThunderLevel(0f);
		net.minecraft.world.phys.Vec3 middle = helper.getBounds().getCenter();
		for (Villager other : level.getEntities(net.minecraft.world.level.entity.EntityTypeTest.forClass(Villager.class), FirewardenWork::isFirewarden)) {
			if (other.getBrain().getMemory(net.minecraft.world.entity.ai.memory.MemoryModuleType.JOB_SITE)
				.filter(g -> g.dimension() == level.dimension()
					&& net.minecraft.world.phys.Vec3.atCenterOf(g.pos()).distanceTo(middle) <= VillageFire.ANSWER + 32).isPresent()) {
				other.discard();
			}
		}
		Leftovers.after(helper, () -> {
			VillageHalls.RADIUS = radius;
			level.getGameRules().getRule(GameRules.RULE_DOFIRETICK).set(fireTick, level.getServer());
			for (BlockPos p : BlockPos.betweenClosed(BlockPos.containing(helper.getBounds().getMinPosition()),
				BlockPos.containing(helper.getBounds().getMaxPosition()))) {
				if (level.getBlockState(p).is(BlockTags.FIRE)) {
					level.removeBlock(p, false);
				}
			}
			VillageFire.reset();
		});
		helper.setBlock(FIRE_HALL, ModBlocks.VILLAGE_HALL);
		for (int x = 0; x < 5; x++) {
			for (int z = 0; z < 5; z++) {
				if ((x == 0 || x == 4) && (z == 0 || z == 4)) {
					helper.setBlock(HUT.offset(x, 0, z), Blocks.OAK_LOG);
					helper.setBlock(HUT.offset(x, 1, z), Blocks.OAK_LOG);
				}
				helper.setBlock(HUT.offset(x, 2, z), Blocks.OAK_LOG);
			}
		}
		ResourceLocation id = ResourceLocation.fromNamespaceAndPath("aliveworkplace_test", "qafire_" + name + "_" + Long.toHexString(level.getGameTime()));
		level.getStructureManager().getOrCreate(id).fillFromWorld(level, helper.absolutePos(HUT), new Vec3i(5, 3, 5), false, Blocks.STRUCTURE_VOID);
		BlueprintData.Placement placement = new BlueprintData.Placement(level.dimension().location(), helper.absolutePos(HUT), Rotation.NONE, Mirror.NONE);
		BuildSiteManager.get(level).recordFinished(id, placement, UUID.randomUUID());
		Leftovers.after(helper, () -> BuildSiteManager.get(level).forgetFinished(placement));
		VillageFire.forgetBuilds();
		return level;
	}

	private static long fires(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		return BlockPos.betweenClosedStream(helper.getBounds()).filter(p -> level.getBlockState(p).is(BlockTags.FIRE)).count();
	}

	/**
	 * A brigade member who had nothing in hand carries a bucket on the call and ends empty-handed (the bucket isn't left
	 * to them); one holding a stack of 12 bread gets all 12 back, nothing dropped.
	 */
	//$ gametest_ticks_batch AREA '1200' '"qaBrigadeHands"'
	@GameTest(template = AREA, timeoutTicks = 1200, batch = "qaBrigadeHands")
	public void theBrigadeGivesBackExactlyWhatWasHeld(GameTestHelper helper) {
		ServerLevel level = hut(helper, "hands");
		helper.setBlock(GRINDSTONE, Blocks.GRINDSTONE.defaultBlockState().setValue(BlockStateProperties.ATTACH_FACE, AttachFace.FLOOR)
			.setValue(BlockStateProperties.HORIZONTAL_FACING, Direction.SOUTH));
		Villager warden = helper.spawn(EntityType.VILLAGER, new BlockPos(3, 2, 4));
		Stations.assign(level, warden, helper.absolutePos(GRINDSTONE), ModVillagers.FIREWARDEN);
		warden.setNoAi(true);
		warden.setVillagerData(warden.getVillagerData().setLevel(3));
		Villager empty = helper.spawn(EntityType.VILLAGER, new BlockPos(8, 2, 9));
		Villager baker = helper.spawn(EntityType.VILLAGER, new BlockPos(9, 2, 8));
		baker.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.BREAD, 12));
		boolean[] bucket = {false, false};
		helper.runAfterDelay(2, () -> {
			BlockPos at = helper.absolutePos(ROOF_TOP);
			level.setBlockAndUpdate(at, BaseFireBlock.getState(level, at));
			List<Villager> called = VillageFire.ring(level, warden, at);
			helper.assertTrue(called.contains(empty) && called.contains(baker), "the brigade isn't the two villagers: " + called);
		});
		helper.onEachTick(() -> {
			bucket[0] |= empty.getMainHandItem().is(Items.WATER_BUCKET);
			bucket[1] |= baker.getMainHandItem().is(Items.WATER_BUCKET);
		});
		helper.succeedWhen(() -> {
			helper.assertTrue(bucket[0] && bucket[1], "the brigade never carried buckets");
			helper.assertTrue(fires(helper) == 0, "still burning");
			helper.assertTrue(VillageFire.brigadeOf(empty).isEmpty() && VillageFire.brigadeOf(baker).isEmpty(), "the brigade wasn't sent home");
			helper.assertTrue(empty.getMainHandItem().isEmpty(), "the empty-handed villager kept " + empty.getMainHandItem());
			ItemStack bread = baker.getMainHandItem();
			int dropped = level.getEntitiesOfClass(ItemEntity.class, new AABB(baker.blockPosition()).inflate(16), e -> e.getItem().is(Items.BREAD))
				.stream().mapToInt(e -> e.getItem().getCount()).sum();
			int kept = baker.getInventory().countItem(Items.BREAD);
			helper.assertTrue(bread.is(Items.BREAD) && bread.getCount() + dropped + kept == 12 && bread.getCount() == 12,
				"the bread: " + bread + " in hand, " + dropped + " dropped, " + kept + " in the pack");
			helper.assertTrue(!ModAttachments.BRIGADE_HELD.has(empty) && !ModAttachments.BRIGADE_HELD.has(baker), "something is still kept aside");
		});
	}

	/**
	 * A villager whose tool was kept aside for a call, now home (no call) with something else in hand (an emerald): the
	 * tool isn't lost, it drops at their feet with its wear, and the emerald stays in hand.
	 */
	//$ gametest_ticks_batch AREA '200' '"qaBrigadeHandFull"'
	@GameTest(template = AREA, timeoutTicks = 200, batch = "qaBrigadeHandFull")
	public void aKeptToolDropsWhenTheHandIsFull(GameTestHelper helper) {
		Leftovers.clear(helper);
		VillageFire.reset();
		ServerLevel level = helper.getLevel();
		Villager v = helper.spawn(EntityType.VILLAGER, new BlockPos(5, 2, 5));
		v.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.EMERALD, 3));
		ItemStack axe = new ItemStack(Items.IRON_AXE);
		axe.setDamageValue(9);
		ModAttachments.BRIGADE_HELD.set(v, axe);
		helper.succeedWhen(() -> {
			helper.assertTrue(!ModAttachments.BRIGADE_HELD.has(v), "the axe is still kept aside");
			List<ItemEntity> axes = level.getEntitiesOfClass(ItemEntity.class, new AABB(v.blockPosition()).inflate(8), e -> e.getItem().is(Items.IRON_AXE));
			boolean inHand = v.getMainHandItem().is(Items.IRON_AXE);
			helper.assertTrue(inHand || axes.size() == 1, "the axe was lost: hand " + v.getMainHandItem() + ", on the ground " + axes.size());
			ItemStack found = inHand ? v.getMainHandItem() : axes.get(0).getItem();
			helper.assertTrue(found.getDamageValue() == 9, "the axe lost its wear: " + found.getDamageValue());
			int emeralds = v.getMainHandItem().is(Items.EMERALD) ? v.getMainHandItem().getCount() : 0;
			emeralds += level.getEntitiesOfClass(ItemEntity.class, new AABB(v.blockPosition()).inflate(8), e -> e.getItem().is(Items.EMERALD))
				.stream().mapToInt(e -> e.getItem().getCount()).sum();
			helper.assertTrue(emeralds == 3, "the emeralds: " + emeralds);
			axes.forEach(ItemEntity::discard);
			v.discard();
		});
	}

	// ---- B153: the hall's Food in store is growth's meal count ------------------------------------------------------

	private static final BlockPos FOOD_HALL = new BlockPos(11, 2, 11);

	/**
	 * Every not-a-meal food (rotten flesh, spider eye, poisonous potato, pufferfish, golden carrot and apple, chorus
	 * fruit, suspicious stew, honey, cake) in the store never shows; bread by the Storehouse and cooked beef by a
	 * kitchen's smoker add up; bread in a chest by neither isn't counted; hall, census and growth all read 7, and with
	 * only rotten flesh left the hall reads "Food in store: 0".
	 */
	//$ gametest_ticks_batch AREA '100' '"qaHallFoodIsMeals"'
	@GameTest(template = AREA, timeoutTicks = 100, batch = "qaHallFoodIsMeals")
	public void theHallsFoodIsGrowthsMealCount(GameTestHelper helper) {
		Leftovers.clear(helper);
		int radius = VillageHalls.RADIUS;
		VillageHalls.RADIUS = 16;
		Leftovers.after(helper, () -> VillageHalls.RADIUS = radius);
		ServerLevel level = helper.getLevel();
		helper.setBlock(FOOD_HALL, ModBlocks.VILLAGE_HALL);
		helper.setBlock(new BlockPos(17, 2, 17), ModBlocks.STOREHOUSE);
		helper.setBlock(new BlockPos(17, 2, 19), Blocks.CHEST);
		helper.setBlock(new BlockPos(3, 2, 18), Blocks.SMOKER);
		helper.setBlock(new BlockPos(3, 2, 19), Blocks.CHEST);
		helper.setBlock(new BlockPos(2, 2, 2), Blocks.CHEST);
		Container store = helper.getBlockEntity(new BlockPos(17, 2, 19));
		Container kitchen = helper.getBlockEntity(new BlockPos(3, 2, 19));
		Container elsewhere = helper.getBlockEntity(new BlockPos(2, 2, 2));
		ItemStack[] junk = {new ItemStack(Items.ROTTEN_FLESH, 64), new ItemStack(Items.SPIDER_EYE, 64), new ItemStack(Items.POISONOUS_POTATO, 64),
			new ItemStack(Items.PUFFERFISH, 64), new ItemStack(Items.GOLDEN_CARROT, 64), new ItemStack(Items.GOLDEN_APPLE, 64),
			new ItemStack(Items.ENCHANTED_GOLDEN_APPLE, 64), new ItemStack(Items.CHORUS_FRUIT, 64), new ItemStack(Items.SUSPICIOUS_STEW, 1),
			new ItemStack(Items.HONEY_BOTTLE, 16), new ItemStack(Items.CAKE, 1)};
		for (int i = 0; i < junk.length; i++) {
			store.setItem(i, junk[i].copy());
		}
		store.setItem(junk.length, new ItemStack(Items.BREAD, 2));
		kitchen.setItem(0, new ItemStack(Items.COOKED_BEEF, 5));
		kitchen.setItem(1, new ItemStack(Items.ROTTEN_FLESH, 30));
		elsewhere.setItem(0, new ItemStack(Items.BREAD, 10));
		ServerPlayer player = helper.makeMockServerPlayerInLevel();
		helper.runAfterDelay(5, () -> {
			BlockPos hall = helper.absolutePos(FOOD_HALL);
			List<BlockPos> chests = VillageNeeds.store(level, hall);
			helper.assertTrue(chests.contains(helper.absolutePos(new BlockPos(17, 2, 19))) && chests.contains(helper.absolutePos(new BlockPos(3, 2, 19))),
				"the store isn't the Storehouse's and the kitchen's chests: " + chests);
			helper.assertTrue(!chests.contains(helper.absolutePos(new BlockPos(2, 2, 2))), "a chest by neither is in the store");
			long meals = VillageGrowth.meals(level, chests);
			long food = VillageHalls.food(level, hall);
			long census = VillageHalls.census(level, hall).food();
			helper.assertTrue(meals == 7 && food == 7 && census == 7, "growth " + meals + ", hall " + food + ", census " + census + " (expected 7)");
			ChoiceMenu menu = VillageHallScreen.forTest(player, hall);
			String label = menu.icon(VillageHallScreen.FOOD).getHoverName().getString();
			helper.assertTrue(label.equals("Food in store: 7"), "the hall reads: " + label);

			// Only rotten flesh left: nothing to show.
			store.setItem(junk.length, ItemStack.EMPTY);
			kitchen.setItem(0, ItemStack.EMPTY);
			long after = VillageHalls.food(level, hall);
			helper.assertTrue(after == 0 && VillageGrowth.meals(level, VillageNeeds.store(level, hall)) == 0, "with only junk the hall counts " + after);
			String empty = VillageHallScreen.forTest(player, hall).icon(VillageHallScreen.FOOD).getHoverName().getString();
			helper.assertTrue(empty.equals("Food in store: 0"), "with only junk the hall reads: " + empty);
			helper.succeed();
		});
	}
}
