package io.github.jcondedata.aliveworkplace.test;

import io.github.jcondedata.aliveworkplace.AliveWorkplace;
import io.github.jcondedata.aliveworkplace.WorkplaceConfig;
import io.github.jcondedata.aliveworkplace.blueprint.BlueprintData;
import io.github.jcondedata.aliveworkplace.blueprint.StarterBlueprints;
import io.github.jcondedata.aliveworkplace.build.BuildSiteManager;
import io.github.jcondedata.aliveworkplace.guard.VillageRaids;
import io.github.jcondedata.aliveworkplace.hall.VillageHalls;
import io.github.jcondedata.aliveworkplace.people.Sickness;
import io.github.jcondedata.aliveworkplace.registry.ModAttachments;
import io.github.jcondedata.aliveworkplace.threat.Covens;
import io.github.jcondedata.aliveworkplace.threat.Culture;
import io.github.jcondedata.aliveworkplace.threat.Lairs;
import io.github.jcondedata.aliveworkplace.threat.Sieges;
import io.github.jcondedata.aliveworkplace.threat.ThreatData;
import io.github.jcondedata.aliveworkplace.threat.Threats;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.locale.Language;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.Container;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.monster.Witch;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.projectile.ThrownPotion;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.AbstractCauldronBlock;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import net.minecraft.world.phys.Vec3;

/**
 * Witch covens (ROADMAP 32.11). The culture's file as the item writes it (swamps, Village and up, the hex tactic with no
 * rams or climbers, witches 70 and zombie villagers 30, the Coven Mother with 40 more health who blinks, the Coven Hut and
 * its loot) and the switch; a hexed villager is ill and a nurse with honey in her chest cures an ordinary patient but not
 * them, and the hex survives a save and load; the coven broken, once by killing the Coven Mother and once by breaking her
 * cauldron, cures every hexed villager at once; raiding witches keep at least 10 blocks from a finished palisade while
 * they throw potions over it; the Coven Mother blinks 8 to 12 blocks after three hits in a row, not after two; and with
 * the culture switched off no night is hexed and the hexes lift.
 */
public class CovenGameTests implements FabricGameTest {
	private static final String AREA = "aliveworkplace_test:huge_area";
	private static final String BIG = "aliveworkplace_test:big_area";
	private static final ResourceLocation COVEN = AliveWorkplace.id("witch_coven");
	private static final BlockPos HALL = new BlockPos(1, 2, 1);

	private static Culture coven(GameTestHelper helper) {
		Optional<Culture> culture = Threats.get(COVEN);
		helper.assertTrue(culture.isPresent(), "witch_coven.json didn't load");
		return culture.get();
	}

	/** A village round {@code at} of radius 20, with lairs on; everything put back when the test ends. */
	private static BlockPos village(GameTestHelper helper, BlockPos at) {
		Leftovers.clear(helper);
		ServerLevel level = helper.getLevel();
		int radius = VillageHalls.RADIUS;
		VillageHalls.RADIUS = 20;
		boolean lairs = Lairs.ENABLED;
		Lairs.ENABLED = true;
		Set<String> off = Threats.DISABLED;
		long time = level.getDayTime();
		BlockPos hall = helper.absolutePos(at);
		Leftovers.after(helper, () -> {
			level.getEntitiesOfClass(Mob.class, helper.getBounds().inflate(48), m -> !(m instanceof Villager)).forEach(Mob::discard);
			level.getEntitiesOfClass(ThrownPotion.class, helper.getBounds().inflate(48), p -> true).forEach(ThrownPotion::discard);
			VillageRaids.forget(hall);
			ThreatData.get(level).forgetSiege(hall);
			ThreatData.get(level).forgetPast(hall);
			Lairs.forget(level);
			Covens.forget();
			Covens.Data.get(level).forget(hall);
			Lairs.ENABLED = lairs;
			Threats.DISABLED = off;
			VillageHalls.RADIUS = radius;
			level.setDayTime(time);
			new WorkplaceConfig().apply();
		});
		return hall;
	}

	/** The Coven Hut founded by the village round {@code hall}, its middle over {@code ground}. */
	private static Lairs.Lair hut(GameTestHelper helper, BlockPos hall, BlockPos ground) {
		Lairs.Lair lair = Lairs.found(helper.getLevel(), hall, helper.absolutePos(ground), COVEN);
		helper.assertTrue(lair != null && lair.culture().equals(COVEN), "no coven hut: " + lair);
		return lair;
	}

	private static List<Villager> villagers(GameTestHelper helper, int n) {
		List<Villager> out = new ArrayList<>();
		for (int i = 0; i < n; i++) {
			Villager v = helper.spawn(EntityType.VILLAGER, new BlockPos(2 + 2 * i, 2, 3));
			v.setNoAi(true);
			out.add(v);
		}
		return out;
	}

	/** The file: where it comes, its roster, the Coven Mother, the hut and its loot, every line it says; and its switch. */
	//$ gametest_ticks AREA '20'
	@GameTest(template = AREA, timeoutTicks = 20)
	public void theCovenFileIsAsTheItemWritesIt(GameTestHelper helper) {
		Culture culture = coven(helper);
		ServerLevel level = helper.getLevel();
		helper.assertTrue(culture.where().biomes().containsAll(List.of("minecraft:swamp", "minecraft:mangrove_swamp")), "biomes: " + culture.where().biomes());
		helper.assertTrue(culture.where().minRank().map(r -> r.name().equals("VILLAGE")).orElse(false), "rank: " + culture.where().minRank());
		helper.assertTrue(culture.tactics().equals(List.of("hex")) && !Sieges.lays(culture), "tactics: " + culture.tactics());
		helper.assertTrue(culture.roster().stream().noneMatch(m -> m.role() == Culture.Role.RAM || m.role() == Culture.Role.CLIMBER), "rams or ladders");
		int witches = culture.roster().stream().filter(m -> m.entity().equals(EntityType.getKey(EntityType.WITCH))).mapToInt(Culture.Member::share).sum();
		int zombies = culture.roster().stream().filter(m -> m.entity().equals(EntityType.getKey(EntityType.ZOMBIE_VILLAGER))).mapToInt(Culture.Member::share).sum();
		helper.assertTrue(witches == 70 && zombies == 30 && culture.shares() == 100, "roster: witches " + witches + ", zombie villagers " + zombies);
		Culture.Captain mother = culture.captain().orElseThrow();
		helper.assertTrue(mother.entity().equals(EntityType.getKey(EntityType.WITCH)) && mother.health() == 40, "the Coven Mother: " + mother);
		helper.assertTrue(mother.blinks().map(b -> b.after() == 3 && b.near() == 8 && b.far() == 12).orElse(false), "her blink: " + mother.blinks());
		Culture.Lair lair = culture.lair().orElseThrow();
		helper.assertTrue(lair.structure().equals(AliveWorkplace.id("camp/coven_hut")) && lair.wet(), "lair: " + lair);
		helper.assertTrue(culture.loot().map(l -> l.equals(AliveWorkplace.id("chests/coven_hut"))).orElse(false), "loot: " + culture.loot());
		helper.assertTrue(level.getServer().reloadableRegistries().getLootTable(net.minecraft.resources.ResourceKey.create(
			net.minecraft.core.registries.Registries.LOOT_TABLE, AliveWorkplace.id("chests/coven_hut"))) != net.minecraft.world.level.storage.loot.LootTable.EMPTY, "no loot table");
		// The hut: one cauldron (hers), a loot chest, brewing stands.
		StructureTemplate template = level.getStructureManager().get(lair.structure()).orElse(null);
		helper.assertTrue(template != null, "no camp/coven_hut");
		Language lang = Language.getInstance();
		for (String key : new String[] {"threat.aliveworkplace.witch_coven.title", "threat.aliveworkplace.witch_coven.names.20",
			"threat.aliveworkplace.witch_coven.lair", "threat.aliveworkplace.witch_coven.name", "entity.aliveworkplace.coven_raider",
			"message.aliveworkplace.coven.hex", "screen.aliveworkplace.hall.hexed"}) {
			helper.assertTrue(lang.has(key), "no text for " + key);
		}
		culture.messages().own().values().forEach(key -> helper.assertTrue(lang.has(key), "no text for " + key));
		culture.chronicle().own().values().forEach(key -> helper.assertTrue(lang.has(key), "no text for " + key));
		Set<String> off = Threats.DISABLED;
		Threats.DISABLED = Set.of("witch_coven");
		boolean gone = Threats.on(COVEN).isEmpty();
		Threats.DISABLED = off;
		helper.assertTrue(gone, "switched off but on");
		helper.succeed();
	}

	/**
	 * A hexed villager is ill (witch particles, slowness, the hall calls them hexed) and a nurse with honey in her chest
	 * cures the ordinarily ill patient beside them but not them; the hex survives a save and load, and a villager from before 32.11 has an ordinary illness.
	 */
	//$ gametest_ticks_batch BIG '400' '"covenNurse"'
	@GameTest(template = BIG, timeoutTicks = 400, batch = "covenNurse")
	public void aNursesHoneyDoesNotCureAHex(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		village(helper, HALL);
		Villager nurse = NurseGameTests.nurse(helper);
		helper.setBlock(new BlockPos(2, 2, 4), Blocks.CHEST);
		Container chest = helper.getBlockEntity(new BlockPos(2, 2, 4));
		chest.setItem(0, new ItemStack(Items.HONEY_BOTTLE, 3));
		Villager hexed = helper.spawn(EntityType.VILLAGER, new BlockPos(10, 2, 10));
		Villager ill = helper.spawn(EntityType.VILLAGER, new BlockPos(12, 2, 12));
		Sickness.hex(level, hexed);
		Sickness.fallIll(level, ill);
		helper.assertTrue(Sickness.isIll(hexed) && Sickness.isHexed(hexed) && !Sickness.isHexed(ill), "not hexed");
		helper.assertTrue(hexed.hasEffect(net.minecraft.world.effect.MobEffects.MOVEMENT_SLOWDOWN), "the hexed aren't slowed");
		Sickness.fallIll(level, hexed);
		helper.assertTrue(Sickness.isHexed(hexed), "an ordinary illness took the hex's place");
		// Save and load.
		CompoundTag saved = hexed.saveWithoutId(new CompoundTag());
		Villager copy = EntityType.VILLAGER.create(level);
		copy.load(saved);
		helper.assertTrue(Sickness.isHexed(copy), "the hex didn't survive a save and load");
		CompoundTag plain = ill.saveWithoutId(new CompoundTag());
		Villager old = EntityType.VILLAGER.create(level);
		old.load(plain);
		helper.assertTrue(Sickness.isIll(old) && !Sickness.isHexed(old), "an ordinary illness read back as a hex");
		helper.runAfterDelay(160, () -> helper.succeedWhen(() -> {
			helper.assertFalse(Sickness.isIll(ill), "the nurse didn't cure the ordinary patient");
			helper.assertTrue(Sickness.isHexed(hexed), "the nurse's honey cured a hex");
			helper.assertTrue(chest.countItem(Items.HONEY_BOTTLE) == 2, "honey used: " + (3 - chest.countItem(Items.HONEY_BOTTLE)) + " (one patient)");
		}));
	}

	/**
	 * A night's hex lays one to three of the village's villagers low; the Coven Mother killed, the coven is broken and every
	 * hexed villager is cured at once; the night is remembered across a save and load.
	 */
	//$ gametest_ticks_batch AREA '60' '"covenMother"'
	@GameTest(template = AREA, timeoutTicks = 60, batch = "covenMother")
	public void killingTheCovenMotherCuresEveryHex(GameTestHelper helper) {
		BlockPos hall = village(helper, HALL);
		ServerLevel level = helper.getLevel();
		coven(helper);
		helper.runAfterDelay(2, () -> {
			Lairs.Lair lair = hut(helper, hall, new BlockPos(15, 1, 18));
			List<Villager> people = villagers(helper, 4);
			List<Villager> hexed = Covens.hexNight(level, lair, RandomSource.create(5L));
			helper.assertTrue(hexed.size() >= 1 && hexed.size() <= 3, "hexed: " + hexed.size());
			helper.assertTrue(people.stream().filter(Sickness::isHexed).count() == hexed.size(), "hexed among the villagers");
			long day = io.github.jcondedata.aliveworkplace.hall.Chronicle.day(level);
			helper.assertTrue(Covens.Data.get(level).lastHexed(hall) == day, "the night isn't remembered");
			CompoundTag saved = Covens.Data.get(level).save(new CompoundTag(), level.registryAccess());
			Covens.Data.get(level).forget(hall);
			Covens.Data loaded = new Covens.Data();
			helper.assertTrue(loaded.lastHexed(hall) == -1, "a new world has hexed");
			Mob mother = (Mob) level.getEntity(lair.captain());
			helper.assertTrue(mother instanceof Witch && mother.getMaxHealth() == 26 + 40, "the Coven Mother: " + mother);
			mother.hurt(level.damageSources().fellOutOfWorld(), 1000f);
			helper.assertTrue(Lairs.near(level, hall).isEmpty(), "her death didn't break the coven");
			helper.assertTrue(people.stream().noneMatch(Sickness::isIll), "still hexed after the coven broke: "
				+ people.stream().filter(Sickness::isIll).count());
			helper.assertTrue(saved.contains("hexed"), "the night isn't saved");
			helper.succeed();
		});
	}

	/** Her cauldron broken, the coven is broken and every hexed villager is cured at once. */
	//$ gametest_ticks_batch AREA '200' '"covenCauldron"'
	@GameTest(template = AREA, timeoutTicks = 200, batch = "covenCauldron")
	public void breakingHerCauldronCuresEveryHex(GameTestHelper helper) {
		BlockPos hall = village(helper, HALL);
		ServerLevel level = helper.getLevel();
		coven(helper);
		Lairs.Lair[] lair = new Lairs.Lair[1];
		List<Villager> people = new ArrayList<>();
		helper.runAfterDelay(2, () -> {
			lair[0] = hut(helper, hall, new BlockPos(15, 1, 18));
			people.addAll(villagers(helper, 4));
			for (Villager v : people.subList(0, 3)) {
				Sickness.hex(level, v);
			}
			BlockPos cauldron = Covens.cauldron(level, lair[0]);
			helper.assertTrue(cauldron != null && level.getBlockState(cauldron).is(Blocks.WATER_CAULDRON), "no cauldron at the hut: " + cauldron);
			helper.assertTrue(level.getBlockState(cauldron.below()).is(Blocks.CAMPFIRE), "her cauldron isn't over the fire");
			level.destroyBlock(cauldron, false);
			helper.assertTrue(!(level.getBlockState(cauldron).getBlock() instanceof AbstractCauldronBlock), "the cauldron still stands");
		});
		helper.runAfterDelay(4, () -> helper.succeedWhen(() -> {
			helper.assertTrue(Lairs.near(level, hall).isEmpty(), "breaking her cauldron didn't break the coven");
			helper.assertTrue(people.stream().noneMatch(Sickness::isIll), "still hexed: " + people.stream().filter(Sickness::isIll).count());
			helper.assertTrue(level.getEntity(lair[0].captain()) == null, "the Coven Mother is still at her hut");
		}));
	}

	/**
	 * A raid of witches on a village behind a finished palisade: they keep at least 10 blocks from it, never climb or ram
	 * it, and throw their potions over it.
	 */
	//$ gametest_ticks_batch AREA '420' '"covenWall"'
	@GameTest(template = AREA, timeoutTicks = 420, batch = "covenWall")
	public void raidingWitchesKeepTheirDistanceFromAWall(GameTestHelper helper) {
		BlockPos hall = village(helper, new BlockPos(15, 2, 0));
		Leftovers.finished(helper);
		ServerLevel level = helper.getLevel();
		Culture culture = coven(helper);
		BlockPos origin = helper.absolutePos(new BlockPos(12, 2, 6));
		level.getStructureManager().get(StarterBlueprints.PALISADE.id()).orElseThrow().placeInWorld(level, origin, origin,
			new StructurePlaceSettings(), RandomSource.create(1L), 2);
		BlueprintData.Placement placement = new BlueprintData.Placement(level.dimension().location(), origin, Rotation.NONE, Mirror.NONE);
		BuildSiteManager sites = BuildSiteManager.get(level);
		sites.recordFinished(StarterBlueprints.PALISADE.id(), placement, UUID.randomUUID());
		Leftovers.after(helper, () -> sites.forgetFinished(placement));
		Villager villager = helper.spawn(EntityType.VILLAGER, new BlockPos(15, 2, 3));
		villager.setNoAi(true);
		Culture.Member witch = culture.roster().stream().filter(m -> m.entity().equals(EntityType.getKey(EntityType.WITCH))).findFirst().orElseThrow();
		List<Mob> raiders = new ArrayList<>();
		for (int x : new int[] {13, 15, 17}) {
			Mob mob = Threats.create(level, witch);
			BlockPos p = helper.absolutePos(new BlockPos(x, 2, 10));
			mob.moveTo(p.getX() + 0.5, p.getY(), p.getZ() + 0.5, 180f, 0f);
			mob.finalizeSpawn(level, level.getCurrentDifficultyAt(p), MobSpawnType.EVENT, null);
			Threats.outfit(level, mob, culture, witch);
			mob.setTarget(villager);
			level.addFreshEntityWithPassengers(mob);
			raiders.add(mob);
		}
		VillageRaids.track(level, hall, culture, raiders);
		List<BlockPos> walls = Sieges.wallBlocks(level, hall);
		helper.assertTrue(walls.size() > 10, "the palisade isn't a finished wall: " + walls.size());
		Vec3 stand = Covens.standing(hall, raiders.get(1).position(), walls);
		helper.assertTrue(stand != null && Covens.wallDistance(stand.x, stand.z, walls) >= Covens.NEAREST, "where they stand: " + stand);
		boolean[] thrown = {false};
		helper.onEachTick(() -> {
			if (!level.getEntitiesOfClass(ThrownPotion.class, helper.getBounds().inflate(16), p -> p.getOwner() instanceof Witch).isEmpty()) {
				thrown[0] = true;
			}
		});
		helper.runAfterDelay(400, () -> {
			for (Mob mob : raiders) {
				double from = Covens.wallDistance(mob.getX(), mob.getZ(), walls);
				helper.assertTrue(mob.isAlive() && from >= 10, "a witch stands " + String.format("%.1f", from) + " from the wall at " + mob.blockPosition());
				helper.assertTrue(mob.getZ() > origin.getZ() + 1, "a witch got over the wall: " + mob.blockPosition());
			}
			helper.assertTrue(thrown[0], "no potion thrown over the wall");
			helper.succeed();
		});
	}

	/** Three hits in a row and the Coven Mother blinks 8 to 12 blocks away; two do nothing, nor hits far apart. */
	//$ gametest_ticks_batch AREA '120' '"covenBlink"'
	@GameTest(template = AREA, timeoutTicks = 120, batch = "covenBlink")
	public void theCovenMotherBlinksAfterThreeHits(GameTestHelper helper) {
		BlockPos hall = village(helper, HALL);
		ServerLevel level = helper.getLevel();
		coven(helper);
		Mob[] mother = new Mob[1];
		Vec3[] at = new Vec3[1];
		helper.runAfterDelay(2, () -> {
			Lairs.Lair lair = hut(helper, hall, new BlockPos(15, 1, 15));
			mother[0] = (Mob) level.getEntity(lair.captain());
			helper.assertTrue(mother[0] != null, "no Coven Mother");
			mother[0].setNoAi(true);
			at[0] = mother[0].position();
			mother[0].hurt(level.damageSources().generic(), 1f);
		});
		helper.runAfterDelay(20, () -> mother[0].hurt(level.damageSources().generic(), 1f));
		helper.runAfterDelay(38, () -> {
			helper.assertTrue(mother[0].position().distanceTo(at[0]) < 1, "she blinked after two hits");
			mother[0].hurt(level.damageSources().generic(), 1f);
			double moved = Math.hypot(mother[0].getX() - at[0].x, mother[0].getZ() - at[0].z);
			helper.assertTrue(mother[0].isAlive() && moved >= 7.5 && moved <= 12.5, "after three hits she moved " + String.format("%.1f", moved));
		});
		helper.runAfterDelay(40, helper::succeed);
	}

	/**
	 * A hex doesn't pass by itself, long past an illness's time, while the coven stands; switched off, a coven hexes no one
	 * at night, and a hex laid before lifts on the hall's next round.
	 */
	//$ gametest_ticks_batch AREA '120' '"covenOff"'
	@GameTest(template = AREA, timeoutTicks = 120, batch = "covenOff")
	public void switchedOffACovenHexesNoOne(GameTestHelper helper) {
		BlockPos hall = village(helper, HALL);
		ServerLevel level = helper.getLevel();
		coven(helper);
		List<Villager> people = new ArrayList<>();
		helper.runAfterDelay(2, () -> {
			hut(helper, hall, new BlockPos(15, 1, 18));
			people.addAll(villagers(helper, 3));
			Sickness.hex(level, people.get(0));
			ModAttachments.ILL_SINCE.set(people.get(0), level.getGameTime() - Sickness.RECOVERY * 3); // long past an illness's time
			Sickness.round(level, people.get(0), 40);
			helper.assertTrue(Sickness.isHexed(people.get(0)), "the hex passed by itself while the coven stands");
			Threats.DISABLED = Set.of("witch_coven");
			level.setDayTime(level.getDayTime() / 24000 * 24000 + 15000);
			Sickness.round(level, people.get(0), 40);
			helper.assertFalse(Sickness.isIll(people.get(0)), "the hex didn't lift with the coven switched off");
		});
		helper.runAfterDelay(100, () -> {
			helper.assertTrue(people.stream().noneMatch(Sickness::isIll), "hexed while switched off: " + people.stream().filter(Sickness::isIll).count());
			helper.assertTrue(Covens.Data.get(level).lastHexed(hall) == -1, "a night was hexed while switched off");
			helper.succeed();
		});
	}
}
