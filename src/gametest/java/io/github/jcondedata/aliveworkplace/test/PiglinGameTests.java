package io.github.jcondedata.aliveworkplace.test;

import io.github.jcondedata.aliveworkplace.AliveWorkplace;
import io.github.jcondedata.aliveworkplace.WorkplaceConfig;
import io.github.jcondedata.aliveworkplace.guard.Guards;
import io.github.jcondedata.aliveworkplace.guard.VillageRaids;
import io.github.jcondedata.aliveworkplace.hall.VillageHallBlockEntity;
import io.github.jcondedata.aliveworkplace.hall.VillageHalls;
import io.github.jcondedata.aliveworkplace.hall.VillageRanks;
import io.github.jcondedata.aliveworkplace.registry.ModBlocks;
import io.github.jcondedata.aliveworkplace.registry.ModVillagers;
import io.github.jcondedata.aliveworkplace.threat.Culture;
import io.github.jcondedata.aliveworkplace.threat.Lairs;
import io.github.jcondedata.aliveworkplace.threat.Piglins;
import io.github.jcondedata.aliveworkplace.threat.Plunder;
import io.github.jcondedata.aliveworkplace.threat.SiegeReport;
import io.github.jcondedata.aliveworkplace.threat.ThreatData;
import io.github.jcondedata.aliveworkplace.threat.Threats;
import io.github.jcondedata.aliveworkplace.work.Jobs;
import java.util.List;
import java.util.Optional;
import java.util.Set;
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
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.monster.ZombifiedPiglin;
import net.minecraft.world.entity.monster.Zoglin;
import net.minecraft.world.entity.monster.hoglin.Hoglin;
import net.minecraft.world.entity.monster.piglin.AbstractPiglin;
import net.minecraft.world.entity.monster.piglin.Piglin;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.NetherPortalBlock;
import net.minecraft.core.Direction;
import net.minecraft.world.phys.AABB;

/**
 * Piglin incursions (ROADMAP 32.10). The culture's file as the item writes it, and where it comes (a Town with a lit
 * Nether portal; not without the portal, below Town, or switched off); a raiding piglin is still a piglin after 600
 * ticks in the Overworld (a plain one beside it isn't) and goes for a villager; a guard fights a raiding hoglin, and
 * leaves a plain one alone; a piglin passing a chest takes one stack of its gold (not from a chest further off, nor
 * anything else), keeps it through a save and load, and drops it when killed; gold carried home at dawn is counted for
 * the siege report, through a save and load; the Blackstone Outpost stands with its portal lit, its raiders step out of
 * that portal, and the Warlord's death puts the portal out and turns the band, at home and raiding, zombified.
 */
public class PiglinGameTests implements FabricGameTest {
	private static final String AREA = "aliveworkplace_test:huge_area";
	private static final ResourceLocation PIGLINS = AliveWorkplace.id("piglin_incursion");
	private static final BlockPos HALL = new BlockPos(15, 2, 15);

	private static Culture piglins(GameTestHelper helper) {
		Optional<Culture> culture = Threats.get(PIGLINS);
		helper.assertTrue(culture.isPresent(), "piglin_incursion.json didn't load");
		return culture.get();
	}

	/** A village round {@code hall} (at {@link #HALL} unless given) of radius 20, put back when the test ends. */
	private static BlockPos village(GameTestHelper helper, BlockPos at) {
		Leftovers.clear(helper);
		ServerLevel level = helper.getLevel();
		int radius = VillageHalls.RADIUS;
		VillageHalls.RADIUS = 20;
		BlockPos hall = helper.absolutePos(at);
		Leftovers.after(helper, () -> {
			level.getEntitiesOfClass(Mob.class, helper.getBounds().inflate(48), m -> !(m instanceof Villager)).forEach(Mob::discard);
			level.getEntitiesOfClass(ItemEntity.class, helper.getBounds().inflate(8), e -> true).forEach(ItemEntity::discard);
			VillageRaids.forget(hall);
			ThreatData.get(level).forgetPlunder(hall);
			ThreatData.get(level).forgetSiege(hall);
			ThreatData.get(level).forgetPast(hall);
			VillageHalls.RADIUS = radius;
			new WorkplaceConfig().apply();
		});
		return hall;
	}

	/** One of the culture's raiders, as a raid makes it: the first of the roster of kind {@code entity}. */
	private static Mob raider(GameTestHelper helper, Culture culture, EntityType<?> entity, BlockPos at) {
		ServerLevel level = helper.getLevel();
		Culture.Member member = culture.roster().stream().filter(m -> m.entity().equals(EntityType.getKey(entity))).findFirst().orElseThrow();
		Mob mob = Threats.create(level, member);
		helper.assertTrue(mob != null, "no " + member.entity());
		BlockPos p = helper.absolutePos(at);
		mob.moveTo(p.getX() + 0.5, p.getY(), p.getZ() + 0.5, 0f, 0f);
		mob.finalizeSpawn(level, level.getCurrentDifficultyAt(p), MobSpawnType.EVENT, null);
		Threats.outfit(level, mob, culture, member);
		level.addFreshEntityWithPassengers(mob);
		return mob;
	}

	/** A glass pen from {@code a} to {@code b} (corners, inclusive), three high on the floor. */
	private static void pen(GameTestHelper helper, int x0, int z0, int x1, int z1) {
		for (int x = x0; x <= x1; x++) {
			for (int z = z0; z <= z1; z++) {
				if (x == x0 || x == x1 || z == z0 || z == z1) {
					for (int y = 2; y <= 4; y++) {
						helper.setBlock(new BlockPos(x, y, z), Blocks.GLASS);
					}
				}
			}
		}
	}

	/** The file says what the item says; it comes to a Town with a lit portal, and not without it, below Town, or switched off. */
	//$ gametest_ticks_batch AREA '40' '"piglinsFile"'
	@GameTest(template = AREA, timeoutTicks = 40, batch = "piglinsFile")
	public void thePiglinFileIsAsTheItemSaysAndComesToATownWithAPortal(GameTestHelper helper) {
		Culture culture = piglins(helper);
		ServerLevel level = helper.getLevel();
		helper.assertTrue(culture.arrival() == Culture.Arrival.PORTAL && culture.hours() == Culture.Hours.NIGHT && culture.where().netherLink(),
			"arrival, hours or where: " + culture);
		helper.assertTrue(culture.tactics().equals(List.of("ram_gates", "plunder")), "tactics: " + culture.tactics());
		int[] shares = culture.roster().stream().mapToInt(Culture.Member::share).toArray();
		helper.assertTrue(shares.length == 4 && shares[0] == 35 && shares[1] == 25 && shares[2] == 25 && shares[3] == 15, "the roster's shares");
		helper.assertTrue(culture.roster().get(0).gear().get(net.minecraft.world.entity.EquipmentSlot.MAINHAND).equals("minecraft:golden_sword")
			&& culture.roster().get(1).gear().get(net.minecraft.world.entity.EquipmentSlot.MAINHAND).equals("minecraft:crossbow")
			&& culture.roster().get(2).entity().equals(EntityType.getKey(EntityType.PIGLIN_BRUTE))
			&& culture.roster().get(3).entity().equals(EntityType.getKey(EntityType.HOGLIN)) && culture.roster().get(3).role() == Culture.Role.RAM, "the roster");
		Culture.Captain warlord = culture.captain().orElseThrow();
		helper.assertTrue(warlord.entity().equals(EntityType.getKey(EntityType.PIGLIN_BRUTE)) && warlord.health() == 50 && warlord.zombifiesBand()
			&& warlord.gear().get(net.minecraft.world.entity.EquipmentSlot.CHEST).equals("minecraft:golden_chestplate"), "the Warlord: " + warlord);
		Culture.Lair lair = culture.lair().orElseThrow();
		helper.assertTrue(lair.structure().equals(AliveWorkplace.id("camp/blackstone_outpost")) && lair.portal() && lair.near() == 56 && lair.far() == 80, "the lair: " + lair);
		helper.assertTrue(level.getStructureManager().get(lair.structure()).isPresent(), "no outpost template");
		helper.assertTrue(culture.loot().equals(Optional.of(AliveWorkplace.id("chests/piglin_hoard"))), "the loot");
		Language lang = Language.getInstance();
		for (String key : new String[] {"threat.aliveworkplace.piglin_incursion.title", "threat.aliveworkplace.piglin_incursion.names.20",
			"threat.aliveworkplace.piglin_incursion.lair", "threat.aliveworkplace.piglin_incursion.name", "entity.aliveworkplace.piglin_raider",
			"chronicle.aliveworkplace.siege_plundered", "chronicle.aliveworkplace.plunder"}) {
			helper.assertTrue(lang.has(key), "no text for " + key);
		}
		culture.messages().own().values().forEach(key -> helper.assertTrue(lang.has(key), "no text for " + key));
		culture.chronicle().own().values().forEach(key -> helper.assertTrue(lang.has(key), "no text for " + key));
		// Where it comes: a Town with a lit Nether portal in its area.
		BlockPos hall = village(helper, new BlockPos(4, 2, 4));
		Set<String> off = Threats.DISABLED;
		Leftovers.after(helper, () -> Threats.DISABLED = off);
		helper.setBlock(new BlockPos(4, 2, 4), ModBlocks.VILLAGE_HALL);
		java.util.function.Consumer<VillageRanks.Rank> rank = r -> ((VillageHallBlockEntity) level.getBlockEntity(hall)).setRank(r);
		helper.runAfterDelay(2, () -> {
			rank.accept(VillageRanks.Rank.TOWN);
			helper.assertFalse(Threats.fits(level, hall, culture, () -> 12), "it came without a portal");
			for (int pass = 0; pass < 2; pass++) { // the frame first, then the portal in it (a portal in half a frame goes out)
				for (int x = 10; x <= 13; x++) {
					for (int y = 2; y <= 6; y++) {
						boolean frame = x == 10 || x == 13 || y == 2 || y == 6;
						if (frame == (pass == 0)) {
							helper.setBlock(new BlockPos(x, y, 10), frame ? Blocks.OBSIDIAN.defaultBlockState()
								: Blocks.NETHER_PORTAL.defaultBlockState().setValue(NetherPortalBlock.AXIS, Direction.Axis.X));
						}
					}
				}
			}
		});
		helper.runAfterDelay(4, () -> {
			rank.accept(VillageRanks.Rank.TOWN);
			helper.assertTrue(Threats.fits(level, hall, culture, () -> 12), "it doesn't come to a Town with a lit portal: rank "
				+ VillageRanks.of(level, hall) + ", link " + io.github.jcondedata.aliveworkplace.threat.Conditions.netherLink(level, hall));
			rank.accept(VillageRanks.Rank.VILLAGE);
			helper.assertFalse(Threats.fits(level, hall, culture, () -> 12), "it came to a Village");
			rank.accept(VillageRanks.Rank.TOWN);
			Threats.DISABLED = Set.of("piglin_incursion");
			helper.assertFalse(Threats.fits(level, hall, culture, () -> 12), "it came switched off");
			helper.assertTrue(Threats.on(PIGLINS).isEmpty(), "switched off but on");
			helper.succeed();
		});
	}

	/**
	 * A raiding piglin is still a piglin after 600 ticks in the Overworld (a plain one in the next pen turned zombified)
	 * and goes for the villager penned in with it.
	 */
	//$ gametest_ticks_batch AREA '760' '"piglinStaysAPiglin"'
	@GameTest(template = AREA, timeoutTicks = 760, batch = "piglinStaysAPiglin")
	public void aRaidingPiglinStaysAPiglinAndAttacksAVillager(GameTestHelper helper) {
		BlockPos hall = village(helper, HALL);
		ServerLevel level = helper.getLevel();
		Culture culture = piglins(helper);
		pen(helper, 2, 2, 12, 12);
		pen(helper, 22, 22, 27, 27);
		Villager villager = helper.spawn(EntityType.VILLAGER, new BlockPos(5, 2, 5));
		villager.setNoAi(true);
		Mob piglin = raider(helper, culture, EntityType.PIGLIN, new BlockPos(10, 2, 10));
		Piglin plain = helper.spawn(EntityType.PIGLIN, new BlockPos(24, 2, 24));
		VillageRaids.track(level, hall, culture, List.of(piglin));
		boolean[] hit = {false};
		helper.onEachTick(() -> {
			if (villager.getLastHurtByMob() == piglin || villager.isAlive() && villager.getHealth() < villager.getMaxHealth()) {
				hit[0] = true;
			}
		});
		helper.runAfterDelay(620, () -> {
			helper.assertTrue(piglin.isAlive() && !piglin.isRemoved() && piglin.getType() == EntityType.PIGLIN && !((AbstractPiglin) piglin).isConverting(),
				"the raiding piglin turned: " + piglin);
			helper.assertTrue(level.getEntitiesOfClass(ZombifiedPiglin.class, helper.getBounds(), z -> true).size() == 1 && plain.isRemoved(),
				"the plain piglin didn't zombify: the test isn't in the Overworld");
			helper.assertTrue(hit[0] || !villager.isAlive(), "the piglin never attacked the villager");
			helper.succeed();
		});
	}

	/** A guard fights a raiding hoglin, which is a foe; a hoglin that isn't raiding isn't. */
	//$ gametest_ticks_batch AREA '600' '"guardFightsHoglin"'
	@GameTest(template = AREA, timeoutTicks = 600, batch = "guardFightsHoglin")
	public void aGuardFightsARaidingHoglin(GameTestHelper helper) {
		BlockPos hall = village(helper, HALL);
		ServerLevel level = helper.getLevel();
		Culture culture = piglins(helper);
		helper.setDayTime(2000);
		helper.setBlock(new BlockPos(2, 2, 2), ModBlocks.GUARD_POST);
		helper.setBlock(new BlockPos(2, 2, 4), Blocks.CHEST);
		Container chest = helper.getBlockEntity(new BlockPos(2, 2, 4));
		chest.setItem(0, new ItemStack(Items.IRON_SWORD));
		chest.setItem(1, new ItemStack(Items.IRON_CHESTPLATE));
		Villager guard = helper.spawn(EntityType.VILLAGER, new BlockPos(4, 2, 4));
		Jobs.employ(level, guard, helper.absolutePos(new BlockPos(2, 2, 2)), ModVillagers.GUARD_POST_POI, ModVillagers.GUARD);
		Mob hoglin = raider(helper, culture, EntityType.HOGLIN, new BlockPos(9, 2, 9));
		hoglin.setNoAi(true);
		Hoglin plain = helper.spawn(EntityType.HOGLIN, new BlockPos(26, 2, 26));
		plain.setNoAi(true);
		helper.assertTrue(Guards.isFoe(hoglin) && !Guards.isFoe(plain), "the raiding hoglin isn't a foe, or the plain one is");
		VillageRaids.track(level, hall, culture, List.of(hoglin));
		helper.succeedWhen(() -> {
			helper.assertTrue(hoglin.getLastHurtByMob() == guard || !hoglin.isAlive(), "the guard hasn't struck the hoglin");
			helper.assertTrue(plain.getLastHurtByMob() == null, "the guard struck the plain hoglin");
		});
	}

	/**
	 * A piglin passing a chest takes one stack of its gold (the cobblestone stays, and the chest six blocks off keeps
	 * its gold), keeps it through a save and load (shown on its head), and drops it when killed. Gold carried home at
	 * dawn is counted, kept through a save and load, and the siege report says it.
	 */
	//$ gametest_ticks_batch AREA '200' '"piglinPlunder"'
	@GameTest(template = AREA, timeoutTicks = 200, batch = "piglinPlunder")
	public void aPiglinPassingAChestTakesItsGoldAndDropsItWhenKilled(GameTestHelper helper) {
		BlockPos hall = village(helper, HALL);
		ServerLevel level = helper.getLevel();
		Culture culture = piglins(helper);
		BlockPos near = new BlockPos(8, 2, 8);
		BlockPos far = new BlockPos(8, 2, 16);
		helper.setBlock(near, Blocks.CHEST);
		helper.setBlock(far, Blocks.CHEST);
		Container chest = helper.getBlockEntity(near);
		chest.setItem(0, new ItemStack(Items.COBBLESTONE, 20));
		chest.setItem(1, new ItemStack(Items.GOLD_INGOT, 64));
		chest.setItem(2, new ItemStack(Items.GOLD_INGOT, 10));
		Container other = helper.getBlockEntity(far);
		other.setItem(0, new ItemStack(Items.GOLD_BLOCK, 5));
		Mob piglin = raider(helper, culture, EntityType.PIGLIN, new BlockPos(10, 2, 10));
		piglin.setNoAi(true);
		Mob hoglin = raider(helper, culture, EntityType.HOGLIN, new BlockPos(6, 2, 8));
		hoglin.setNoAi(true);
		VillageRaids.track(level, hall, culture, List.of(piglin, hoglin));
		helper.assertTrue(Plunder.isGold(new ItemStack(Items.GOLDEN_SWORD)) && Plunder.isGold(new ItemStack(Items.RAW_GOLD))
			&& !Plunder.isGold(new ItemStack(Items.GOLDEN_APPLE)) && !Plunder.isGold(new ItemStack(Items.IRON_INGOT)), "what counts as gold");
		helper.runAfterDelay(45, () -> {
			ItemStack carried = Plunder.carried(piglin);
			helper.assertTrue(carried.is(Items.GOLD_INGOT) && carried.getCount() == 64, "the piglin carries " + carried);
			helper.assertTrue(chest.getItem(0).is(Items.COBBLESTONE) && chest.getItem(0).getCount() == 20 && chest.getItem(1).isEmpty()
				&& chest.getItem(2).getCount() == 10, "the chest: " + chest.getItem(0) + " " + chest.getItem(1) + " " + chest.getItem(2));
			helper.assertTrue(other.getItem(0).getCount() == 5, "the chest 6 blocks off was plundered");
			helper.assertTrue(Plunder.carried(hoglin).isEmpty(), "the hoglin ram took gold");
			helper.assertTrue(piglin.getItemBySlot(net.minecraft.world.entity.EquipmentSlot.HEAD).is(Items.GOLD_INGOT), "the gold isn't shown");
			// A save and load keeps it.
			CompoundTag saved = piglin.saveWithoutId(new CompoundTag());
			Piglin copy = EntityType.PIGLIN.create(level);
			copy.load(saved);
			helper.assertTrue(Plunder.carried(copy).getCount() == 64, "after a save and load it carries " + Plunder.carried(copy));
			copy.discard();
			// Killed, it drops it.
			BlockPos where = piglin.blockPosition();
			piglin.kill();
			int dropped = level.getEntitiesOfClass(ItemEntity.class, new AABB(where).inflate(3), e -> e.getItem().is(Items.GOLD_INGOT))
				.stream().mapToInt(e -> e.getItem().getCount()).sum();
			helper.assertTrue(dropped == 64, "it dropped " + dropped + " gold ingots");
			helper.assertTrue(level.getEntitiesOfClass(ItemEntity.class, new AABB(where).inflate(3), e -> e.getItem().is(Items.GOLDEN_SWORD)).isEmpty(),
				"its own sword dropped");
			// At dawn one carries its gold home: the village lost it, the morning's siege report says so.
			Mob thief = raider(helper, culture, EntityType.PIGLIN, new BlockPos(20, 2, 20));
			Plunder.carry(level, thief, new ItemStack(Items.GOLD_NUGGET, 12));
			Threats.tactics(culture).forEach(t -> t.leave(level, hall, culture, thief));
			helper.assertTrue(Plunder.carried(thief).isEmpty() && ThreatData.get(level).plundered(hall) == 12, "lost: " + ThreatData.get(level).plundered(hall));
			ThreatData.get(level).roundTrip(level.registryAccess());
			helper.assertTrue(ThreatData.get(level).plundered(hall) == 12, "after a save and load: " + ThreatData.get(level).plundered(hall));
			String report = SiegeReport.text(level, new ThreatData.Siege(hall, 0, 0)).getString();
			helper.assertTrue(report.endsWith("12 gold was carried off from the village's chests."), "the report: " + report);
			helper.succeed();
		});
	}

	/**
	 * The Blackstone Outpost stands by a Town with its portal lit and its band immune to the Overworld; a raid from it
	 * steps out of that portal. The Warlord falls: the portal goes out, the lair is broken, and every piglin and hoglin of
	 * the band, at the outpost and raiding, is zombified (the raiders still raiders).
	 */
	//$ gametest_ticks_batch AREA '60' '"warlordFalls"'
	@GameTest(template = AREA, timeoutTicks = 60, batch = "warlordFalls")
	public void theWarlordsDeathPutsOutThePortalAndZombifiesTheBand(GameTestHelper helper) {
		BlockPos hall = village(helper, new BlockPos(1, 2, 1));
		ServerLevel level = helper.getLevel();
		Culture culture = piglins(helper);
		boolean lairs = Lairs.ENABLED;
		Lairs.ENABLED = true;
		Leftovers.after(helper, () -> {
			Lairs.forget(level);
			Lairs.ENABLED = lairs;
		});
		helper.setBlock(new BlockPos(1, 2, 1), ModBlocks.VILLAGE_HALL);
		((VillageHallBlockEntity) level.getBlockEntity(hall)).setRank(VillageRanks.Rank.TOWN);
		helper.runAfterDelay(2, () -> {
			Lairs.Lair lair = Lairs.found(level, hall, helper.absolutePos(new BlockPos(15, 1, 15)), PIGLINS);
			helper.assertTrue(lair != null && lair.culture().equals(PIGLINS), "no outpost: " + lair);
			List<BlockPos> portal = Piglins.portalBlocks(level, lair);
			helper.assertTrue(portal.size() == 12, "portal blocks lit: " + portal.size());
			Mob warlord = (Mob) level.getEntity(lair.captain());
			helper.assertTrue(warlord != null && warlord.getType() == EntityType.PIGLIN_BRUTE && warlord.getMaxHealth() == 50 + 50
				&& warlord.getItemBySlot(net.minecraft.world.entity.EquipmentSlot.HEAD).is(Items.GOLDEN_HELMET), "the Warlord: " + warlord);
			List<Mob> band = Lairs.band(level, lair);
			helper.assertTrue(band.size() == 9 && band.stream().allMatch(m -> m instanceof AbstractPiglin p ? !p.isConverting() : !(m instanceof Hoglin h) || !h.isConverting()),
				"the band: " + band.size());
			// A raid: out of the outpost's portal.
			VillageRaids.Raid raid = VillageRaids.start(level, hall, 8, 0, Double.NaN, culture, RandomSource.create(7L));
			helper.assertTrue(raid != null, "no raid");
			List<Mob> raiders = level.getEntitiesOfClass(Mob.class, helper.getBounds().inflate(32), m -> m.getTags().contains(VillageRaids.TAG));
			helper.assertTrue(raiders.size() == raid.raiders() && raiders.size() == 5, "raiders: " + raiders.size() + " of " + raid.raiders());
			for (Mob m : raiders) {
				helper.assertTrue(portal.stream().anyMatch(p -> p.closerThan(m.blockPosition(), 2.5)), "a raider didn't step out of the portal: " + m.blockPosition());
			}
			// The Warlord falls.
			warlord.hurt(level.damageSources().fellOutOfWorld(), 1000f);
			helper.assertTrue(Lairs.near(level, hall).isEmpty(), "his death didn't break the outpost");
			int lit = 0;
			for (BlockPos p : portal) {
				if (level.getBlockState(p).is(Blocks.NETHER_PORTAL)) {
					lit++;
				}
			}
			helper.assertTrue(lit == 0, "portal blocks still lit: " + lit);
			AABB around = helper.getBounds().inflate(32);
			List<LivingEntity> left = level.getEntitiesOfClass(LivingEntity.class, around, e -> e.isAlive() && Piglins.piglinKind(e));
			helper.assertTrue(left.isEmpty(), "piglins still unturned: " + left);
			List<Mob> turned = level.getEntitiesOfClass(Mob.class, around, m -> m.isAlive() && (m instanceof ZombifiedPiglin || m instanceof Zoglin));
			long raiding = turned.stream().filter(m -> m.getTags().contains(VillageRaids.TAG)).count();
			helper.assertTrue(raiding == 5 && turned.size() >= 9, "zombified: " + turned.size() + ", of them raiding " + raiding);
			helper.succeed();
		});
	}
}
