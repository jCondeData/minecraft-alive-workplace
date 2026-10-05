package io.github.jcondedata.aliveworkplace.test;

import io.github.jcondedata.aliveworkplace.guard.Cavalry;
import io.github.jcondedata.aliveworkplace.hall.VillageHallBlockEntity;
import io.github.jcondedata.aliveworkplace.hall.VillageHalls;
import io.github.jcondedata.aliveworkplace.hall.VillageNeeds;
import io.github.jcondedata.aliveworkplace.legend.Beastmaster;
import io.github.jcondedata.aliveworkplace.legend.Legend;
import io.github.jcondedata.aliveworkplace.legend.LegendNeeds;
import io.github.jcondedata.aliveworkplace.legend.LegendPowers;
import io.github.jcondedata.aliveworkplace.legend.LegendRecord;
import io.github.jcondedata.aliveworkplace.legend.Legends;
import io.github.jcondedata.aliveworkplace.legend.Rarity;
import io.github.jcondedata.aliveworkplace.people.Moods;
import io.github.jcondedata.aliveworkplace.registry.ModAttachments;
import io.github.jcondedata.aliveworkplace.registry.ModBlocks;
import io.github.jcondedata.aliveworkplace.registry.ModVillagers;
import io.github.jcondedata.aliveworkplace.rules.AnimalsAtJob;
import io.github.jcondedata.aliveworkplace.rules.Condition;
import io.github.jcondedata.aliveworkplace.work.Jobs;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.locale.Language;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Container;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.animal.Cow;
import net.minecraft.world.entity.animal.Wolf;
import net.minecraft.world.entity.animal.horse.AbstractHorse;
import net.minecraft.world.entity.animal.horse.Horse;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.npc.VillagerProfession;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;

/**
 * 29.20, the Beastmaster: the real file (a Rare Rancher who likes clothes, a prisoner in a pillager outpost once the
 * village keeps a ranch of 10 animals by a Rancher's, Butcher's or Shepherd's workstation, or born to a Rancher); war
 * dogs (tamed with bones, armoured with scutes, one per guard, a pair bred when only a pair is left, following the guard
 * and fighting what they fight, replaced 2 days after one falls, kept through a reload); fast horses (a foal at least
 * its best parent and never past vanilla's best, once a day, not past the ranch's cap, saddled when grown; cavalry ride
 * them first); every sentence.
 */
public class BeastmasterGameTests implements net.fabricmc.fabric.api.gametest.v1.FabricGameTest {
	private static final String AREA = "aliveworkplace_test:huge_area";
	private static final BlockPos HALL = new BlockPos(15, 2, 15);
	private static final BlockPos TROUGH = new BlockPos(5, 2, 5);
	private static final BlockPos CHEST = new BlockPos(5, 2, 7);
	private static final BlockPos POST_A = new BlockPos(24, 2, 6);
	private static final BlockPos POST_B = new BlockPos(24, 2, 24);
	private static final long DAY = VillageNeeds.DAY;

	private static VillageHallBlockEntity entity(GameTestHelper helper) {
		return (VillageHallBlockEntity) helper.getBlockEntity(HALL);
	}

	private static void setUp(GameTestHelper helper) {
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
			for (Villager v : level.getEntitiesOfClass(Villager.class, helper.getBounds().inflate(16), v -> true)) {
				record.forget(v.getUUID());
			}
			Leftovers.clear(helper);
			level.setBlockAndUpdate(helper.absolutePos(HALL), Blocks.AIR.defaultBlockState());
			VillageNeeds.forget();
			LegendPowers.forget();
			Moods.forget();
		});
	}

	/** Runs {@code body} with only the real Beastmaster loaded, moods and needs off, then puts the clock and roster back. */
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

	private static Villager villager(GameTestHelper helper, BlockPos at, VillagerProfession trade) {
		Villager v = helper.spawn(EntityType.VILLAGER, at);
		v.setNoAi(true);
		v.setVillagerData(v.getVillagerData().setProfession(trade).setLevel(2));
		return v;
	}

	/** The Beastmaster, settled here, at the feed trough with the chest beside it. */
	private static Villager beastmaster(GameTestHelper helper, Legend legend) {
		Villager v = villager(helper, TROUGH.east(), ModVillagers.RANCHER);
		Jobs.employ(helper.getLevel(), v, helper.absolutePos(TROUGH), ModVillagers.FEED_TROUGH_POI, ModVillagers.RANCHER);
		v.setVillagerData(v.getVillagerData().setLevel(5));
		Legends.make(helper.getLevel(), v, legend, "test");
		LegendPowers.forget();
		return v;
	}

	/** A guard at a Guard Post; {@code ai}: free to patrol and fight. */
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

	private static void stock(GameTestHelper helper, ItemStack... items) {
		Container c = chest(helper);
		for (ItemStack s : items) {
			for (int i = 0; i < c.getContainerSize(); i++) {
				if (c.getItem(i).isEmpty()) {
					c.setItem(i, s);
					break;
				}
			}
		}
	}

	private static String key(Component c) {
		return c.getContents() instanceof TranslatableContents t ? t.getKey() : "";
	}

	private static boolean chronicled(GameTestHelper helper, String key) {
		return entity(helper).chronicle().stream().anyMatch(e -> key(e.text()).equals(key));
	}

	/** The file: Rare, a Rancher, likes clothes; a prisoner at an outpost once the ranch holds 10, born to a Rancher; its powers; every sentence. */
	//$ gametest_ticks_batch AREA '100' '"beastFile"'
	@GameTest(template = AREA, timeoutTicks = 100, batch = "beastFile")
	public void theFileAndItsText(GameTestHelper helper) {
		setUp(helper);
		staged(helper, legend -> {
			helper.assertTrue(legend.rarity() == Rarity.RARE, "not Rare");
			helper.assertTrue(legend.job().equals(io.github.jcondedata.aliveworkplace.AliveWorkplace.id("rancher")), "trade " + legend.job());
			helper.assertTrue(legend.luxury().equals(Optional.of("clothes")), "likes " + legend.luxury());
			helper.assertTrue(legend.ways("found").stream().anyMatch(w -> "outpost".equals(w.get("site").getAsString())), "not a prisoner at an outpost");
			helper.assertTrue(legend.ways("born").stream().anyMatch(w -> w.get("trades").toString().contains("aliveworkplace:rancher")), "not born to a Rancher");
			helper.assertTrue(legend.conditions().size() == 1 && legend.conditions().get(0) instanceof AnimalsAtJob ranch && ranch.count() == 10
				&& ranch.jobs().equals(Set.of(ResourceLocation.parse("aliveworkplace:rancher"), ResourceLocation.parse("minecraft:butcher"),
				ResourceLocation.parse("minecraft:shepherd"))), "the ranch condition: " + legend.conditions());
			helper.assertTrue(legend.powers(Beastmaster.WarDogsPower.class).equals(List.of(Beastmaster.WarDogsPower.DEFAULT)),
				"war dogs " + legend.powers(Beastmaster.WarDogsPower.class));
			helper.assertTrue(legend.powers(Beastmaster.HorseBreedingPower.class).equals(List.of(Beastmaster.HorseBreedingPower.DEFAULT)),
				"horse breeding " + legend.powers(Beastmaster.HorseBreedingPower.class));
			String dogs = Beastmaster.WarDogsPower.DEFAULT.describe().getString();
			helper.assertTrue(dogs.equals("Tames a war dog for every guard with 3 bones from their chest, in wolf armour from 6 armadillo scutes; it follows its guard and fights what they fight, and a lost dog is replaced after 2 days"), dogs);
			String horses = Beastmaster.HorseBreedingPower.DEFAULT.describe().getString();
			helper.assertTrue(horses.equals("Breeds the ranch's best horses with golden carrots, up to 8: each foal gets the best speed, jump and health of its parents and a little more, and guards ride them first"), horses);
			String name = Component.translatable("entity.aliveworkplace.war_dog", "Tobin").getString();
			helper.assertTrue(name.equals("Tobin's War Dog"), name);
			Language en = Language.getInstance();
			for (String k : List.of("legend.aliveworkplace.beastmaster.title", "legend.aliveworkplace.beastmaster.lore",
				"legend.aliveworkplace.beastmaster.name.1", "legend.aliveworkplace.beastmaster.name.2", "legend.aliveworkplace.beastmaster.name.3",
				"chronicle.aliveworkplace.war_dog.given", "chronicle.aliveworkplace.war_dog.lost", "chronicle.aliveworkplace.beastmaster.foal")) {
				helper.assertTrue(en.has(k), "no text for " + k);
			}
			helper.assertTrue(Component.translatable("chronicle.aliveworkplace.war_dog.given", "Ulfhild", "Tobin").getString().equals("Ulfhild tamed a war dog for Tobin"), "given line");
			helper.assertTrue(Component.translatable("chronicle.aliveworkplace.war_dog.lost", "Tobin").getString().equals("Tobin's war dog fell"), "lost line");
			helper.assertTrue(Component.translatable("chronicle.aliveworkplace.beastmaster.foal", "Ulfhild").getString().equals("Ulfhild bred a foal"), "foal line");
		});
		helper.succeed();
	}

	/** The ranch: 9 animals by a Rancher's feed trough isn't one, 10 is; a Shepherd's loom counts too, a Farmer's composter not. */
	//$ gametest_ticks_batch AREA '100' '"beastRanch"'
	@GameTest(template = AREA, timeoutTicks = 100, batch = "beastRanch")
	public void tenAnimalsByARanchersTroughMakeARanch(GameTestHelper helper) {
		setUp(helper);
		ServerLevel level = helper.getLevel();
		BlockPos hall = helper.absolutePos(HALL);
		staged(helper, legend -> {
			Condition ranch = legend.conditions().get(0);
			Villager rancher = villager(helper, TROUGH.east(), ModVillagers.RANCHER);
			Jobs.employ(level, rancher, helper.absolutePos(TROUGH), ModVillagers.FEED_TROUGH_POI, ModVillagers.RANCHER);
			helper.setBlock(new BlockPos(26, 2, 26), Blocks.COMPOSTER);
			Villager farmer = villager(helper, new BlockPos(25, 2, 26), VillagerProfession.FARMER);
			Jobs.employ(level, farmer, helper.absolutePos(new BlockPos(26, 2, 26)), net.minecraft.world.entity.ai.village.poi.PoiTypes.FARMER, VillagerProfession.FARMER);
			for (int i = 0; i < 6; i++) {
				helper.spawn(EntityType.COW, new BlockPos(26 - (i % 3), 2, 22 - i / 3)).setNoAi(true); // by the farmer only
			}
			for (int i = 0; i < 9; i++) {
				Cow cow = helper.spawn(EntityType.COW, new BlockPos(2 + (i % 3) * 2, 2, 10 + (i / 3) * 2));
				cow.setNoAi(true);
			}
			helper.assertFalse(ranch.met(level, hall), "9 animals made a ranch: " + ranch.progress(level, hall).line().getString());
			helper.spawn(EntityType.SHEEP, new BlockPos(9, 2, 3)).setNoAi(true);
			helper.assertTrue(ranch.met(level, hall), "10 animals didn't make a ranch: " + ranch.progress(level, hall).line().getString());
			// A Shepherd's loom with the animals instead of the Rancher's trough.
			rancher.discard();
			helper.assertFalse(ranch.met(level, hall), "a ranch without a rancher");
			helper.setBlock(new BlockPos(4, 2, 12), Blocks.LOOM);
			Villager shepherd = villager(helper, new BlockPos(5, 2, 12), VillagerProfession.SHEPHERD);
			Jobs.employ(level, shepherd, helper.absolutePos(new BlockPos(4, 2, 12)), net.minecraft.world.entity.ai.village.poi.PoiTypes.SHEPHERD, VillagerProfession.SHEPHERD);
			helper.assertTrue(ranch.met(level, hall), "a shepherd's ranch didn't count");
		});
		helper.succeed();
	}

	/**
	 * War dogs: with bones in the chest a wild wolf is tamed for a guard (bones taken, armour from the scutes, named for
	 * the guard, a line on the hall); one per guard; no wolf left, none; only a pair left, the pair is bred (a pup, no
	 * dog yet); a third grown wolf goes to the second guard; no bones, no dog; the dog and the guard's record survive a reload.
	 */
	//$ gametest_ticks_batch AREA '100' '"beastDogsTamed"'
	@GameTest(template = AREA, timeoutTicks = 100, batch = "beastDogsTamed")
	public void aWarDogIsTamedAndSentToItsGuard(GameTestHelper helper) {
		setUp(helper);
		ServerLevel level = helper.getLevel();
		BlockPos hall = helper.absolutePos(HALL);
		staged(helper, legend -> {
			level.setDayTime(40 * DAY + 1000);
			Villager bm = beastmaster(helper, legend);
			Villager a = guard(helper, POST_A, false);
			Villager b = guard(helper, POST_B, false);
			Wolf wild = wolf(helper, new BlockPos(10, 2, 10));
			// No bones: nothing.
			Beastmaster.round(level, hall);
			helper.assertTrue(!wild.isTame() && Beastmaster.dog(level, a) == null && Beastmaster.dog(level, b) == null, "a dog without bones");
			stock(helper, new ItemStack(Items.BONE, 6), new ItemStack(Items.ARMADILLO_SCUTE, 6));
			Beastmaster.round(level, hall);
			Villager got = Beastmaster.dog(level, a) == wild ? a : b;
			Villager other = got == a ? b : a;
			helper.assertTrue(Beastmaster.dog(level, got) == wild && Beastmaster.dog(level, other) == null, "the wolf didn't go to one guard");
			helper.assertTrue(wild.isTame() && wild.getOwnerUUID() == null && Beastmaster.isWarDog(wild)
				&& got.getUUID().equals(ModAttachments.WAR_DOG_OF.get(wild)), "not tamed as the guard's war dog");
			helper.assertTrue(wild.hasArmor() && wild.getBodyArmorItem().is(Items.WOLF_ARMOR), "no wolf armour");
			helper.assertTrue(chest(helper).countItem(Items.BONE) == 3 && chest(helper).countItem(Items.ARMADILLO_SCUTE) == 0,
				"bones " + chest(helper).countItem(Items.BONE) + ", scutes " + chest(helper).countItem(Items.ARMADILLO_SCUTE));
			helper.assertTrue(wild.getCustomName() != null && wild.getCustomName().getString().equals(got.getName().getString() + "'s War Dog"),
				"named " + wild.getCustomName());
			helper.assertTrue(chronicled(helper, "chronicle.aliveworkplace.war_dog.given"), "no line on the hall");
			// One per guard: another round changes nothing.
			Beastmaster.round(level, hall);
			helper.assertTrue(Beastmaster.dog(level, got) == wild && Beastmaster.dog(level, other) == null && chest(helper).countItem(Items.BONE) == 3,
				"a second dog, or bones taken for nothing");
			// Only a pair left: they're bred, no dog yet.
			Wolf p1 = wolf(helper, new BlockPos(8, 2, 20));
			Wolf p2 = wolf(helper, new BlockPos(9, 2, 20));
			Beastmaster.round(level, hall);
			List<Wolf> pups = level.getEntitiesOfClass(Wolf.class, helper.getBounds(), Wolf::isBaby);
			helper.assertTrue(pups.size() == 1 && !p1.isTame() && !p2.isTame() && Beastmaster.dog(level, other) == null, "the pair: pups " + pups.size());
			helper.assertTrue(p1.getAge() > 0 && p2.getAge() > 0, "the pair aren't resting after breeding");
			// A third grown wolf: tamed for the other guard.
			Wolf third = wolf(helper, new BlockPos(6, 2, 9));
			Beastmaster.round(level, hall);
			helper.assertTrue(Beastmaster.dog(level, other) == third && chest(helper).countItem(Items.BONE) == 0 && !third.hasArmor(),
				"the third wolf: " + Beastmaster.dog(level, other) + ", bones " + chest(helper).countItem(Items.BONE));
			// A save keeps the dog and the guard's record; a wolf and a guard from an old save have none.
			CompoundTag dogTag = wild.saveWithoutId(new CompoundTag());
			Wolf dogCopy = EntityType.WOLF.create(level);
			dogCopy.load(dogTag);
			Beastmaster.onLoad(dogCopy); // as the level does when it loads the dog
			helper.assertTrue(got.getUUID().equals(ModAttachments.WAR_DOG_OF.get(dogCopy)), "the dog's guard lost in a reload: " + ModAttachments.WAR_DOG_OF.get(dogCopy));
			helper.assertTrue(dogCopy.isTame() && dogCopy.hasArmor() && dogCopy.getMaxHealth() == wild.getMaxHealth(),
				"the dog untamed or unarmoured after a reload: " + dogCopy.isTame() + " " + dogCopy.getBodyArmorItem() + " " + dogCopy.getMaxHealth());
			CompoundTag guardTag = got.saveWithoutId(new CompoundTag());
			Villager guardCopy = EntityType.VILLAGER.create(level);
			guardCopy.load(guardTag);
			helper.assertTrue(ModAttachments.WAR_DOG.get(got).equals(ModAttachments.WAR_DOG.get(guardCopy)), "the guard's dog lost in a reload");
			helper.assertTrue(!ModAttachments.WAR_DOG.has(EntityType.VILLAGER.create(level)) && !ModAttachments.WAR_DOG_OF.has(EntityType.WOLF.create(level)),
				"an old save has a war dog");
			// A player's own wolf is never taken.
			Wolf pet = wolf(helper, new BlockPos(12, 2, 3));
			pet.setTame(true, false);
			pet.setOwnerUUID(java.util.UUID.randomUUID());
			Villager c = guard(helper, new BlockPos(24, 2, 15), false);
			stock(helper, new ItemStack(Items.BONE, 3));
			p1.setAge(0);
			p2.setAge(0);
			for (Wolf pup : pups) {
				pup.discard();
			}
			p2.discard();
			Beastmaster.round(level, hall);
			helper.assertTrue(Beastmaster.dog(level, c) == p1 && !Beastmaster.isWarDog(pet), "the third guard: " + Beastmaster.dog(level, c));
			helper.assertTrue(bm.isAlive(), "the Beastmaster");
		});
		helper.succeed();
	}

	/** A dog lost: the day is kept, a line on the hall; no new dog the next day, one on the second. The guard dead: the dog's let go. */
	//$ gametest_ticks_batch AREA '100' '"beastDogReplaced"'
	@GameTest(template = AREA, timeoutTicks = 100, batch = "beastDogReplaced")
	public void aLostDogIsReplacedTwoDaysLater(GameTestHelper helper) {
		setUp(helper);
		ServerLevel level = helper.getLevel();
		BlockPos hall = helper.absolutePos(HALL);
		staged(helper, legend -> {
			level.setDayTime(40 * DAY + 1000);
			long day = io.github.jcondedata.aliveworkplace.hall.Chronicle.day(level);
			beastmaster(helper, legend);
			Villager guard = guard(helper, POST_A, false);
			Wolf first = wolf(helper, new BlockPos(10, 2, 10));
			stock(helper, new ItemStack(Items.BONE, 9));
			Beastmaster.round(level, hall);
			helper.assertTrue(Beastmaster.dog(level, guard) == first, "no first dog");
			first.kill();
			Beastmaster.Dog d = ModAttachments.WAR_DOG.get(guard);
			helper.assertTrue(d != null && d.dog().isEmpty() && d.lost() == day, "the loss wasn't kept: " + d);
			helper.assertTrue(chronicled(helper, "chronicle.aliveworkplace.war_dog.lost"), "no line for the fallen dog");
			Wolf second = wolf(helper, new BlockPos(11, 2, 10));
			Beastmaster.round(level, hall);
			helper.assertTrue(Beastmaster.dog(level, guard) == null && !second.isTame(), "replaced the same day");
			level.setDayTime(41 * DAY + 1000);
			Beastmaster.round(level, hall);
			helper.assertTrue(Beastmaster.dog(level, guard) == null && !second.isTame(), "replaced after one day");
			level.setDayTime(42 * DAY + 1000);
			Beastmaster.round(level, hall);
			helper.assertTrue(Beastmaster.dog(level, guard) == second && Beastmaster.isWarDog(second), "not replaced after two days");
			helper.assertTrue(ModAttachments.WAR_DOG.get(guard).lost() == -1, "the loss outlived the new dog");
			// A dog gone without a death seen (unloaded, carried off): lost from the round that misses it.
			second.discard();
			Beastmaster.round(level, hall);
			helper.assertTrue(ModAttachments.WAR_DOG.get(guard).lost() == day + 2 && ModAttachments.WAR_DOG.get(guard).dog().isEmpty(), "a missing dog not counted lost");
			// The guard falls: their dog is let go (a tame village wolf for the next guard).
			level.setDayTime(44 * DAY + 1000);
			Wolf third = wolf(helper, new BlockPos(12, 2, 10));
			Beastmaster.round(level, hall);
			helper.assertTrue(Beastmaster.dog(level, guard) == third, "no third dog");
			guard.kill();
			helper.assertTrue(!Beastmaster.isWarDog(third) && third.isTame() && third.getCustomName() == null, "the dog of a fallen guard still theirs");
		});
		helper.succeed();
	}

	/** The dog follows its guard (fetched from 14 blocks off) and fights the zombies the guard fights. */
	//$ gametest_ticks_batch AREA '900' '"beastDogFights"'
	@GameTest(template = AREA, timeoutTicks = 900, batch = "beastDogFights")
	public void theWarDogFollowsAndFights(GameTestHelper helper) {
		setUp(helper);
		ServerLevel level = helper.getLevel();
		BlockPos hall = helper.absolutePos(HALL);
		Legend legend = Legends.get(Beastmaster.ID).orElseThrow(() -> new AssertionError("beastmaster.json didn't load"));
		helper.setDayTime(2000); // guards fight at any hour, but by day they keep to their post
		Villager bm = beastmaster(helper, legend);
		Villager guard = guard(helper, POST_A, true);
		// Bare-handed: the guard's blows are lighter than a bite, so the dog's count (a mob just hit only takes a harder blow).
		Wolf wolf = helper.spawn(EntityType.WOLF, new BlockPos(10, 2, 10));
		stock(helper, new ItemStack(Items.BONE, 3));
		Beastmaster.round(level, hall);
		helper.assertTrue(Beastmaster.dog(level, guard) == wolf, "no war dog for the guard");
		bm.discard();
		LegendPowers.forget();
		helper.setBlock(HALL, Blocks.AIR); // the guard keeps to their post, not the village's rounds
		// Off 14 blocks: it comes to heel.
		wolf.moveTo(guard.getX() - 14, guard.getY(), guard.getZ() + 12, 0f, 0f);
		long began = level.getGameTime();
		boolean[] followed = {false};
		Zombie[] zombies = new Zombie[2];
		boolean[] bit = {false};
		boolean[] fought = {false};
		boolean[] aimed = {false};
		int[] aimedTicks = {0};
		double[] closest = {99};
		boolean[] foe = {false};
		helper.onEachTick(() -> {
			if (!followed[0] && wolf.isAlive() && wolf.distanceTo(guard) < 6) {
				followed[0] = true;
				for (int i = 0; i < zombies.length; i++) {
					BlockPos at = POST_A.offset(-4 + i * 3, 0, 4);
					zombies[i] = helper.spawn(EntityType.ZOMBIE, at);
					zombies[i].setItemSlot(EquipmentSlot.HEAD, new ItemStack(Items.LEATHER_HELMET)); // no burning by day
				}
			}
			if (io.github.jcondedata.aliveworkplace.guard.GuardCombat.isFighting(guard)) {
				fought[0] = true;
			}
			if (wolf.getTarget() instanceof Zombie z) {
				aimed[0] = true;
				aimedTicks[0]++;
				closest[0] = Math.min(closest[0], wolf.distanceTo(z));
			}
			if (Beastmaster.foeOf(guard) != null) {
				foe[0] = true;
			}
			for (Zombie z : zombies) {
				if (z != null && (z.getLastHurtByMob() == wolf || wolf.getLastHurtMob() == z)) {
					bit[0] = true;
				}
			}
		});
		helper.succeedWhen(() -> {
			helper.assertTrue(followed[0], "the dog never came to its guard (" + (int) wolf.distanceTo(guard) + " blocks off after "
				+ (level.getGameTime() - began) + " ticks)");
			helper.assertTrue(bit[0], "the dog never bit the guard's zombies (" + (level.getGameTime() - began) + " ticks; the zombies: "
				+ java.util.Arrays.stream(zombies).map(z -> z == null ? "none" : z.getRemovalReason() + "/" + z.getLastDamageSource() + "/" + (int) z.distanceTo(guard)
					+ "/" + z.getTarget() + "/" + z.getHealth()).toList() + "; guard " + guard.getVillagerData().getProfession() + " fighting " + fought[0]
				+ " dog target " + wolf.getTarget() + " aimed " + aimed[0] + " for " + aimedTicks[0] + " ticks, closest " + closest[0] + " sit " + wolf.isInSittingPose() + " tame " + wolf.isTame() + " foe " + foe[0] + " guard from post " + (int) Math.sqrt(guard.blockPosition().distSqr(helper.absolutePos(POST_A))) + " dog at " + (int) wolf.distanceTo(guard) + ")");
			for (Zombie z : zombies) {
				helper.assertTrue(z != null && !z.isAlive(), "a zombie still up");
				helper.assertTrue(z.getRemovalReason() == net.minecraft.world.entity.Entity.RemovalReason.KILLED, "a zombie gone without a fight: " + z.getRemovalReason());
			}
			helper.assertTrue(guard.isAlive() && Beastmaster.dog(level, guard) == wolf, "the guard or their dog fell");
		});
	}

	/**
	 * Fast horses: a foal takes at least its best parent's speed, jump and health and a little more, never past vanilla's
	 * best (two maxed parents: a maxed foal); tame and marked; two golden carrots taken; once a day; a horse and a
	 * donkey make none; no carrots or a full ranch, none; the mark survives a reload.
	 */
	//$ gametest_ticks_batch AREA '100' '"beastFoals"'
	@GameTest(template = AREA, timeoutTicks = 100, batch = "beastFoals")
	public void aFoalTakesItsBestParentsStatsAndNoMore(GameTestHelper helper) {
		setUp(helper);
		ServerLevel level = helper.getLevel();
		staged(helper, legend -> {
			level.setDayTime(40 * DAY + 1000);
			Villager bm = beastmaster(helper, legend);
			Beastmaster.HorseBreedingPower power = legend.powers(Beastmaster.HorseBreedingPower.class).get(0);
			Horse a = horse(helper, new BlockPos(8, 2, 9), 0.30, 0.5, 20);
			Horse b = horse(helper, new BlockPos(10, 2, 9), 0.20, 0.9, 28);
			helper.spawn(EntityType.DONKEY, new BlockPos(12, 2, 9)).setNoAi(true);
			// No carrots: no foal.
			helper.assertTrue(Beastmaster.breedHorses(level, bm, power) == null, "a foal without golden carrots");
			stock(helper, new ItemStack(Items.GOLDEN_CARROT, 4));
			AbstractHorse foal = Beastmaster.breedHorses(level, bm, power);
			helper.assertTrue(foal instanceof Horse && foal.isBaby() && foal.isTamed() && Beastmaster.bred(foal), "no tame, marked foal: " + foal);
			double speed = foal.getAttributeBaseValue(Attributes.MOVEMENT_SPEED);
			double jump = foal.getAttributeBaseValue(Attributes.JUMP_STRENGTH);
			double health = foal.getAttributeBaseValue(Attributes.MAX_HEALTH);
			helper.assertTrue(speed > 0.30 && speed <= Beastmaster.MAX_SPEED, "speed " + speed);
			helper.assertTrue(jump > 0.9 && jump <= Beastmaster.MAX_JUMP, "jump " + jump);
			helper.assertTrue(health > 28 && health <= Beastmaster.MAX_HEALTH && foal.getHealth() == foal.getMaxHealth(), "health " + health);
			helper.assertTrue(Math.abs(Beastmaster.MAX_SPEED - 0.3375) < 1e-9 && Beastmaster.MAX_JUMP == 1.0 && Beastmaster.MAX_HEALTH == 30, "vanilla's best");
			helper.assertTrue(chest(helper).countItem(Items.GOLDEN_CARROT) == 2, "carrots left " + chest(helper).countItem(Items.GOLDEN_CARROT));
			helper.assertTrue(chronicled(helper, "chronicle.aliveworkplace.beastmaster.foal"), "no line for the foal");
			// Once a day.
			a.setAge(0);
			b.setAge(0);
			helper.assertTrue(Beastmaster.breedHorses(level, bm, power) == null, "two foals in a day");
			// Two maxed parents the next day: a maxed foal, not more.
			level.setDayTime(41 * DAY + 1000);
			stats(a, Beastmaster.MAX_SPEED, Beastmaster.MAX_JUMP, Beastmaster.MAX_HEALTH);
			stats(b, Beastmaster.MAX_SPEED, Beastmaster.MAX_JUMP, Beastmaster.MAX_HEALTH);
			AbstractHorse best = Beastmaster.breedHorses(level, bm, power);
			helper.assertTrue(best != null && best.getAttributeBaseValue(Attributes.MOVEMENT_SPEED) == Beastmaster.MAX_SPEED
				&& best.getAttributeBaseValue(Attributes.JUMP_STRENGTH) == Beastmaster.MAX_JUMP
				&& best.getAttributeBaseValue(Attributes.MAX_HEALTH) == Beastmaster.MAX_HEALTH, "a foal past vanilla's best, or none");
			// A horse alone with a donkey: none.
			level.setDayTime(42 * DAY + 1000);
			b.discard();
			a.setAge(0);
			stock(helper, new ItemStack(Items.GOLDEN_CARROT, 2));
			helper.assertTrue(Beastmaster.breedHorses(level, bm, power) == null, "a horse and a donkey bred");
			// A full ranch: none.
			Horse c = horse(helper, new BlockPos(8, 2, 12), 0.2, 0.6, 20);
			for (int i = 0; i < 3; i++) {
				horse(helper, new BlockPos(3 + i * 2, 2, 14), 0.2, 0.6, 20);
			}
			helper.assertTrue(level.getEntitiesOfClass(AbstractHorse.class, helper.getBounds(), AbstractHorse::isAlive).size() == power.horses(), "not a full ranch");
			helper.assertTrue(Beastmaster.breedHorses(level, bm, power) == null, "bred on a full ranch");
			c.discard();
			helper.assertTrue(Beastmaster.breedHorses(level, bm, power) != null, "no foal once there was room");
			// A save keeps the mark.
			Horse copy = EntityType.HORSE.create(level);
			copy.load(foal.saveWithoutId(new CompoundTag()));
			helper.assertTrue(Beastmaster.bred(copy) && copy.getAttributeBaseValue(Attributes.MOVEMENT_SPEED) == speed, "the foal lost in a reload");
			// The pure rule.
			helper.assertTrue(Beastmaster.foalStat(0.2, 0.3, Beastmaster.MIN_SPEED, Beastmaster.MAX_SPEED, 0.05f) > 0.3
				&& Beastmaster.foalStat(0.336, 0.3, Beastmaster.MIN_SPEED, Beastmaster.MAX_SPEED, 0.05f) == Beastmaster.MAX_SPEED, "foalStat");
		});
		helper.succeed();
	}

	private static Horse horse(GameTestHelper helper, BlockPos at, double speed, double jump, double health) {
		Horse h = helper.spawn(EntityType.HORSE, at);
		h.setNoAi(true);
		h.setAge(0);
		stats(h, speed, jump, health);
		return h;
	}

	private static void stats(AbstractHorse h, double speed, double jump, double health) {
		h.getAttribute(Attributes.MOVEMENT_SPEED).setBaseValue(speed);
		h.getAttribute(Attributes.JUMP_STRENGTH).setBaseValue(jump);
		h.getAttribute(Attributes.MAX_HEALTH).setBaseValue(health);
		h.setHealth(h.getMaxHealth());
	}

	/** Cavalry take a Beastmaster's horse before a nearer ordinary one; their grown foals get saddles from the chest. */
	//$ gametest_ticks_batch AREA '100' '"beastCavalry"'
	@GameTest(template = AREA, timeoutTicks = 100, batch = "beastCavalry")
	public void cavalryPicksTheBredHorses(GameTestHelper helper) {
		setUp(helper);
		ServerLevel level = helper.getLevel();
		staged(helper, legend -> {
			Villager bm = beastmaster(helper, legend);
			Horse grown = horse(helper, new BlockPos(8, 2, 10), 0.3, 0.8, 26);
			ModAttachments.BRED_HORSE.set(grown, 3L);
			grown.setTamed(true);
			Horse foal = horse(helper, new BlockPos(9, 2, 12), 0.3, 0.8, 26);
			foal.setBaby(true);
			ModAttachments.BRED_HORSE.set(foal, 3L);
			// No saddle in the chest: none fitted.
			helper.assertTrue(Beastmaster.saddle(level, bm) == 0 && !grown.isSaddled(), "saddled from nothing");
			stock(helper, new ItemStack(Items.SADDLE), new ItemStack(Items.SADDLE));
			helper.assertTrue(Beastmaster.saddle(level, bm) == 1 && grown.isSaddled() && !foal.isSaddled(), "the grown foal wasn't saddled (or the foal was)");
			helper.assertTrue(chest(helper).countItem(Items.SADDLE) == 1, "saddles left " + chest(helper).countItem(Items.SADDLE));
			// A guard at a post with an ordinary saddled horse right by them, the bred one further off.
			Villager guard = guard(helper, POST_A, false);
			Horse plain = horse(helper, POST_A.west(2), 0.3, 0.8, 26);
			plain.setTamed(true);
			plain.equipSaddle(new ItemStack(Items.SADDLE), null);
			helper.assertTrue(Cavalry.freeHorse(level, guard, helper.absolutePos(POST_A)) == grown, "the guard took the nearer ordinary horse");
			grown.discard();
			helper.assertTrue(Cavalry.freeHorse(level, guard, helper.absolutePos(POST_A)) == plain, "no horse without the bred one");
		});
		helper.succeed();
	}
}
