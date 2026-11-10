package io.github.jcondedata.aliveworkplace.test;

import io.github.jcondedata.aliveworkplace.hall.Chronicle;
import io.github.jcondedata.aliveworkplace.hall.VillageHalls;
import io.github.jcondedata.aliveworkplace.hall.VillageNeeds;
import io.github.jcondedata.aliveworkplace.legend.Beastmaster;
import io.github.jcondedata.aliveworkplace.legend.Legend;
import io.github.jcondedata.aliveworkplace.legend.LegendData;
import io.github.jcondedata.aliveworkplace.legend.LegendNeeds;
import io.github.jcondedata.aliveworkplace.legend.LegendPowers;
import io.github.jcondedata.aliveworkplace.legend.LegendRecord;
import io.github.jcondedata.aliveworkplace.legend.Legends;
import io.github.jcondedata.aliveworkplace.people.Moods;
import io.github.jcondedata.aliveworkplace.registry.ModAttachments;
import io.github.jcondedata.aliveworkplace.registry.ModBlocks;
import io.github.jcondedata.aliveworkplace.registry.ModVillagers;
import io.github.jcondedata.aliveworkplace.work.Jobs;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Container;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.animal.Wolf;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;

/**
 * QA (lane d, 2026-11-10) for 29.20's war dogs, written from the spec: "with bones in their chest they tame a wolf for
 * each guard without one (the village's wolves, or a pair they breed), fit it with wolf armour when there are armadillo
 * scutes in the chest, and send it to the guard: it follows them ...; one per guard, and a lost dog is replaced after 2
 * days"; and 29.5: a Legend on strike has no powers. Covers two guards each with their own dog and never a second, no
 * wolf at all (no dog, bones kept, nothing bred from nothing), a strike and its end, armour fitted on a later round once
 * the scutes come, and the dog and its guard reloaded together (still bound, still tame, still at heel).
 */
public class QaBeastmaster1110dGameTests implements net.fabricmc.fabric.api.gametest.v1.FabricGameTest {
	private static final String AREA = "aliveworkplace_test:huge_area";
	private static final BlockPos HALL = new BlockPos(15, 2, 15);
	private static final BlockPos TROUGH = new BlockPos(5, 2, 5);
	private static final BlockPos CHEST = new BlockPos(5, 2, 7);
	private static final BlockPos POST_A = new BlockPos(24, 2, 6);
	private static final BlockPos POST_B = new BlockPos(24, 2, 24);
	private static final long DAY = VillageNeeds.DAY;

	private static void setUp(GameTestHelper helper, List<UUID> made) {
		Leftovers.clear(helper);
		Leftovers.halls(helper);
		int radius = VillageHalls.RADIUS;
		VillageHalls.RADIUS = 14;
		helper.setBlock(HALL, ModBlocks.VILLAGE_HALL);
		helper.setBlock(TROUGH, ModBlocks.FEED_TROUGH);
		helper.setBlock(CHEST, Blocks.CHEST);
		Leftovers.after(helper, () -> {
			ServerLevel level = helper.getLevel();
			VillageHalls.RADIUS = radius;
			LegendRecord record = LegendRecord.get(level);
			made.forEach(record::forget); // by id, not by a sweep of the (already emptied) area (B604)
			Leftovers.clear(helper);
			level.setBlockAndUpdate(helper.absolutePos(HALL), Blocks.AIR.defaultBlockState());
			VillageNeeds.forget();
			LegendPowers.forget();
			Moods.forget();
		});
	}

	/** Only the real Beastmaster loaded, moods and needs off (no strike of their own making); the clock and roster put back. */
	private static void staged(GameTestHelper helper, java.util.function.Consumer<Legend> body) {
		ServerLevel level = helper.getLevel();
		long time = level.getDayTime();
		boolean moods = Moods.ENABLED;
		boolean needs = LegendNeeds.ENABLED;
		try {
			Moods.ENABLED = false;
			LegendNeeds.ENABLED = false;
			Legends.reload(level.getServer().getResourceManager());
			Legend beastmaster = Legends.get(Beastmaster.ID).orElseThrow(() -> new AssertionError("beastmaster.json didn't load"));
			Map<ResourceLocation, Legend> map = new LinkedHashMap<>();
			map.put(Beastmaster.ID, beastmaster);
			Legends.setForTest(map);
			LegendPowers.forget();
			body.accept(beastmaster);
		} finally {
			Moods.ENABLED = moods;
			LegendNeeds.ENABLED = needs;
			level.setDayTime(time);
			Moods.forget();
			Legends.reload(level.getServer().getResourceManager());
			LegendPowers.forget();
		}
	}

	private static Villager beastmaster(GameTestHelper helper, Legend legend, List<UUID> made) {
		Villager v = helper.spawn(EntityType.VILLAGER, TROUGH.east());
		v.setNoAi(true);
		v.setVillagerData(v.getVillagerData().setProfession(ModVillagers.RANCHER).setLevel(2));
		Jobs.employ(helper.getLevel(), v, helper.absolutePos(TROUGH), ModVillagers.FEED_TROUGH_POI, ModVillagers.RANCHER);
		v.setVillagerData(v.getVillagerData().setLevel(5));
		Legends.make(helper.getLevel(), v, legend, "test");
		made.add(v.getUUID());
		LegendPowers.forget();
		return v;
	}

	private static Villager guard(GameTestHelper helper, BlockPos post, boolean ai) {
		helper.setBlock(post, ModBlocks.GUARD_POST);
		Villager v = helper.spawn(EntityType.VILLAGER, post.west());
		v.setNoAi(!ai);
		Jobs.employ(helper.getLevel(), v, helper.absolutePos(post), ModVillagers.GUARD_POST_POI, ModVillagers.GUARD);
		return v;
	}

	private static Wolf wolf(GameTestHelper helper, BlockPos at) {
		Wolf w = helper.spawn(EntityType.WOLF, at);
		w.setNoAi(true);
		return w;
	}

	private static Container chest(GameTestHelper helper) {
		return (Container) helper.getBlockEntity(CHEST);
	}

	private static int count(GameTestHelper helper, net.minecraft.world.item.Item item) {
		return chest(helper).countItem(item);
	}

	private static List<Wolf> wolves(GameTestHelper helper) {
		return helper.getLevel().getEntitiesOfClass(Wolf.class, helper.getBounds(), Wolf::isAlive);
	}

	/**
	 * Two guards, four wild wolves and bones for three dogs: one round gives each guard a dog of their own (two different
	 * wolves, 6 bones taken). Then, on that day and the next three, nobody gets a second dog and no more bones go.
	 */
	//$ gametest_ticks_batch AREA '100' '"qaBeast1110dTwo"'
	@GameTest(template = AREA, timeoutTicks = 100, batch = "qaBeast1110dTwo")
	public void twoGuardsGetADogEachAndNeverASecond(GameTestHelper helper) {
		List<UUID> made = new ArrayList<>();
		setUp(helper, made);
		ServerLevel level = helper.getLevel();
		BlockPos hall = helper.absolutePos(HALL);
		staged(helper, legend -> {
			level.setDayTime(40 * DAY + 1000);
			beastmaster(helper, legend, made);
			Villager a = guard(helper, POST_A, false);
			Villager b = guard(helper, POST_B, false);
			for (int i = 0; i < 4; i++) {
				wolf(helper, new BlockPos(8 + i * 2, 2, 10));
			}
			chest(helper).setItem(0, new ItemStack(Items.BONE, 9));
			Beastmaster.round(level, hall);
			Wolf da = Beastmaster.dog(level, a);
			Wolf db = Beastmaster.dog(level, b);
			helper.assertTrue(da != null && db != null, "not every guard got a dog with 4 wolves and 9 bones: " + da + ", " + db);
			helper.assertTrue(da != db, "both guards were sent the same dog");
			helper.assertTrue(a.getUUID().equals(ModAttachments.WAR_DOG_OF.get(da)) && b.getUUID().equals(ModAttachments.WAR_DOG_OF.get(db)),
				"a dog bound to the wrong guard");
			helper.assertTrue(count(helper, Items.BONE) == 3, "bones left " + count(helper, Items.BONE) + " (expected 3)");
			for (int day = 0; day < 4; day++) {
				level.setDayTime((40 + day) * DAY + 2000);
				Beastmaster.round(level, hall);
				long dogs = wolves(helper).stream().filter(Beastmaster::isWarDog).count();
				helper.assertTrue(Beastmaster.dog(level, a) == da && Beastmaster.dog(level, b) == db, "a guard's dog changed on day " + (40 + day));
				helper.assertTrue(dogs == 2, dogs + " war dogs for two guards on day " + (40 + day));
				helper.assertTrue(count(helper, Items.BONE) == 3, "bones taken for a second dog on day " + (40 + day) + ": " + count(helper, Items.BONE) + " left");
			}
			helper.assertTrue(wolves(helper).stream().filter(w -> !Beastmaster.isWarDog(w)).noneMatch(Wolf::isTame), "a spare wolf was tamed for nobody");
		});
		helper.succeed();
	}

	/**
	 * No wolf in the village: bones in the chest make no dog, are kept, and no pup comes out of nothing; a lone wild
	 * wolf turns up and the next round tames it (one wolf isn't a pair to keep for breeding).
	 */
	//$ gametest_ticks_batch AREA '100' '"qaBeast1110dNoWolf"'
	@GameTest(template = AREA, timeoutTicks = 100, batch = "qaBeast1110dNoWolf")
	public void noWolfNoDogAndTheBonesWait(GameTestHelper helper) {
		List<UUID> made = new ArrayList<>();
		setUp(helper, made);
		ServerLevel level = helper.getLevel();
		BlockPos hall = helper.absolutePos(HALL);
		staged(helper, legend -> {
			level.setDayTime(40 * DAY + 1000);
			beastmaster(helper, legend, made);
			Villager g = guard(helper, POST_A, false);
			chest(helper).setItem(0, new ItemStack(Items.BONE, 3));
			chest(helper).setItem(1, new ItemStack(Items.ARMADILLO_SCUTE, 6));
			for (int day = 0; day < 3; day++) {
				level.setDayTime((40 + day) * DAY + 1000);
				Beastmaster.round(level, hall);
			}
			helper.assertTrue(Beastmaster.dog(level, g) == null && !ModAttachments.WAR_DOG.has(g), "a dog with no wolf: " + ModAttachments.WAR_DOG.get(g));
			helper.assertTrue(wolves(helper).isEmpty(), "a wolf out of nothing: " + wolves(helper).size());
			helper.assertTrue(count(helper, Items.BONE) == 3 && count(helper, Items.ARMADILLO_SCUTE) == 6,
				"taken with no wolf: bones " + count(helper, Items.BONE) + ", scutes " + count(helper, Items.ARMADILLO_SCUTE));
			Wolf lone = wolf(helper, new BlockPos(12, 2, 20));
			Beastmaster.round(level, hall);
			helper.assertTrue(Beastmaster.dog(level, g) == lone && lone.isTame() && lone.hasArmor(), "the lone wolf wasn't tamed and armoured for the guard");
			helper.assertTrue(count(helper, Items.BONE) == 0 && count(helper, Items.ARMADILLO_SCUTE) == 0, "costs not taken");
		});
		helper.succeed();
	}

	private static void strike(Villager v, boolean on, long today) {
		LegendData data = ModAttachments.LEGEND.get(v);
		ModAttachments.LEGEND.set(v, on ? data.checkedOn(today, Map.of("luxury", 3), today, -1) : data.checkedOn(today, Map.of(), -1, -1));
		LegendPowers.forget();
	}

	/** 29.5: on strike the Beastmaster's powers stop (no dog, no armour, nothing taken, for days); back at work, a dog that day. */
	//$ gametest_ticks_batch AREA '100' '"qaBeast1110dStrike"'
	@GameTest(template = AREA, timeoutTicks = 100, batch = "qaBeast1110dStrike")
	public void aBeastmasterOnStrikeTamesNoDog(GameTestHelper helper) {
		List<UUID> made = new ArrayList<>();
		setUp(helper, made);
		ServerLevel level = helper.getLevel();
		BlockPos hall = helper.absolutePos(HALL);
		staged(helper, legend -> {
			level.setDayTime(40 * DAY + 1000);
			Villager bm = beastmaster(helper, legend, made);
			LegendData data = ModAttachments.LEGEND.get(bm);
			helper.assertTrue(data != null && data.settled() && !data.onStrike(), "not a settled, working Beastmaster: " + data);
			Villager g = guard(helper, POST_A, false);
			Wolf w = wolf(helper, new BlockPos(10, 2, 10));
			chest(helper).setItem(0, new ItemStack(Items.BONE, 3));
			chest(helper).setItem(1, new ItemStack(Items.ARMADILLO_SCUTE, 6));
			strike(bm, true, Chronicle.day(level));
			for (int day = 0; day < 3; day++) {
				level.setDayTime((40 + day) * DAY + 1000);
				Beastmaster.round(level, hall);
				helper.assertTrue(ModAttachments.LEGEND.get(bm).onStrike(), "the strike ended by itself");
				helper.assertTrue(Beastmaster.dog(level, g) == null && !w.isTame() && !Beastmaster.isWarDog(w), "a war dog from a Beastmaster on strike, day " + (40 + day));
				helper.assertTrue(count(helper, Items.BONE) == 3 && count(helper, Items.ARMADILLO_SCUTE) == 6, "a strike took the bones or scutes");
			}
			strike(bm, false, Chronicle.day(level));
			Beastmaster.round(level, hall);
			helper.assertTrue(Beastmaster.dog(level, g) == w && w.hasArmor(), "no dog the day the strike ended");
			helper.assertTrue(count(helper, Items.BONE) == 0 && count(helper, Items.ARMADILLO_SCUTE) == 0, "costs not taken after the strike");
		});
		helper.succeed();
	}

	/**
	 * Bones but no scutes: a dog without armour. Six scutes later: the next round fits the armour to that same dog and takes
	 * them; six more never go to a dog already in armour.
	 */
	//$ gametest_ticks_batch AREA '100' '"qaBeast1110dArmour"'
	@GameTest(template = AREA, timeoutTicks = 100, batch = "qaBeast1110dArmour")
	public void scutesThatComeLaterArmourTheDogOnce(GameTestHelper helper) {
		List<UUID> made = new ArrayList<>();
		setUp(helper, made);
		ServerLevel level = helper.getLevel();
		BlockPos hall = helper.absolutePos(HALL);
		staged(helper, legend -> {
			level.setDayTime(40 * DAY + 1000);
			beastmaster(helper, legend, made);
			Villager g = guard(helper, POST_A, false);
			Wolf w = wolf(helper, new BlockPos(10, 2, 10));
			chest(helper).setItem(0, new ItemStack(Items.BONE, 3));
			Beastmaster.round(level, hall);
			helper.assertTrue(Beastmaster.dog(level, g) == w && !w.hasArmor(), "the dog: " + Beastmaster.dog(level, g) + ", armour " + w.getBodyArmorItem());
			chest(helper).setItem(1, new ItemStack(Items.ARMADILLO_SCUTE, 6));
			level.setDayTime(41 * DAY + 1000);
			Beastmaster.round(level, hall);
			helper.assertTrue(Beastmaster.dog(level, g) == w, "a new dog instead of armour");
			helper.assertTrue(w.hasArmor() && w.getBodyArmorItem().is(Items.WOLF_ARMOR), "the scutes came but the dog has no armour: " + w.getBodyArmorItem());
			helper.assertTrue(count(helper, Items.ARMADILLO_SCUTE) == 0, "scutes left " + count(helper, Items.ARMADILLO_SCUTE));
			chest(helper).setItem(1, new ItemStack(Items.ARMADILLO_SCUTE, 6));
			Beastmaster.round(level, hall);
			helper.assertTrue(count(helper, Items.ARMADILLO_SCUTE) == 6, "scutes taken for a dog already in armour: " + count(helper, Items.ARMADILLO_SCUTE));
		});
		helper.succeed();
	}

	/**
	 * A save and reload of the guard and the dog together, through the level (the load event, not a helper): the copies
	 * are still bound to each other, the dog tame, armoured and named; the next round gives no second dog; and the
	 * reloaded dog, left 15 blocks off, comes back to its reloaded guard.
	 */
	//$ gametest_ticks_batch AREA '600' '"qaBeast1110dReload"'
	@GameTest(template = AREA, timeoutTicks = 600, batch = "qaBeast1110dReload")
	public void aWarDogAndItsGuardSurviveAReloadTogether(GameTestHelper helper) {
		List<UUID> made = new ArrayList<>();
		setUp(helper, made);
		ServerLevel level = helper.getLevel();
		BlockPos hall = helper.absolutePos(HALL);
		helper.setDayTime(2000);
		Legend legend = Legends.get(Beastmaster.ID).orElseThrow(() -> new AssertionError("beastmaster.json didn't load"));
		Villager bm = beastmaster(helper, legend, made);
		Villager guard = guard(helper, POST_A, true);
		Wolf wolf = helper.spawn(EntityType.WOLF, new BlockPos(10, 2, 10));
		chest(helper).setItem(0, new ItemStack(Items.BONE, 6));
		chest(helper).setItem(1, new ItemStack(Items.ARMADILLO_SCUTE, 6));
		Beastmaster.round(level, hall);
		helper.assertTrue(Beastmaster.dog(level, guard) == wolf && wolf.hasArmor(), "no armoured war dog before the save");
		String name = wolf.getCustomName() == null ? "" : wolf.getCustomName().getString();
		CompoundTag guardTag = guard.saveWithoutId(new CompoundTag());
		CompoundTag dogTag = wolf.saveWithoutId(new CompoundTag());
		UUID guardId = guard.getUUID();
		UUID dogId = wolf.getUUID();
		guard.discard();
		wolf.discard();
		Villager[] g2 = {null};
		Wolf[] d2 = {null};
		boolean[] checked = {false};
		helper.runAfterDelay(2, () -> {
			Villager gc = EntityType.VILLAGER.create(level);
			gc.load(guardTag);
			Wolf dc = EntityType.WOLF.create(level);
			dc.load(dogTag);
			BlockPos post = helper.absolutePos(POST_A);
			dc.moveTo(post.getX() - 15, post.getY(), post.getZ() + 8, 0f, 0f); // left behind, out of reach
			helper.assertTrue(level.addFreshEntity(gc) && level.addFreshEntity(dc), "the reloaded guard or dog wasn't added");
			g2[0] = gc;
			d2[0] = dc;
			helper.assertTrue(gc.getUUID().equals(guardId) && dc.getUUID().equals(dogId), "new ids after a reload");
			helper.assertTrue(dc.isTame() && dc.getOwnerUUID() == null, "the reloaded dog isn't tame (or has a player owner): tame " + dc.isTame());
			helper.assertTrue(dc.hasArmor() && dc.getCustomName() != null && dc.getCustomName().getString().equals(name),
				"armour or name lost: " + dc.getBodyArmorItem() + " / " + dc.getCustomName());
			helper.assertTrue(guardId.equals(ModAttachments.WAR_DOG_OF.get(dc)), "the dog forgot its guard");
			helper.assertTrue(Beastmaster.dog(level, gc) == dc, "the guard forgot their dog: " + ModAttachments.WAR_DOG.get(gc));
			Beastmaster.round(level, hall);
			helper.assertTrue(Beastmaster.dog(level, gc) == dc && count(helper, Items.BONE) == 3, "a second dog after the reload, bones " + count(helper, Items.BONE));
			bm.discard();
			LegendPowers.forget();
			helper.setBlock(HALL, Blocks.AIR); // the guard keeps to their post
			checked[0] = true;
		});
		helper.succeedWhen(() -> {
			helper.assertTrue(checked[0], "not reloaded yet");
			Villager gc = g2[0];
			Wolf dc = d2[0];
			helper.assertTrue(dc.isAlive() && gc.isAlive(), "the guard or the dog is gone");
			helper.assertTrue(dc.distanceTo(gc) < 6, "the reloaded dog didn't come to its guard: " + (int) dc.distanceTo(gc) + " blocks off");
			helper.assertTrue(dc.isTame() && Beastmaster.dog(level, gc) == dc, "the bond broke while following");
		});
	}
}
