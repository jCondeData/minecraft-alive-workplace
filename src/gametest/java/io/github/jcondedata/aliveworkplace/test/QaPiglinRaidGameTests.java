package io.github.jcondedata.aliveworkplace.test;

import io.github.jcondedata.aliveworkplace.AliveWorkplace;
import io.github.jcondedata.aliveworkplace.WorkplaceConfig;
import io.github.jcondedata.aliveworkplace.guard.VillageRaids;
import io.github.jcondedata.aliveworkplace.hall.VillageHalls;
import io.github.jcondedata.aliveworkplace.threat.Culture;
import io.github.jcondedata.aliveworkplace.threat.Plunder;
import io.github.jcondedata.aliveworkplace.threat.ThreatData;
import io.github.jcondedata.aliveworkplace.threat.Threats;
import java.util.List;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;

/**
 * QA (lane D, 2026-10-24), ROADMAP 32.10 from the spec: a Piglin Incursion's raiders "go for villagers and guards (their
 * anger set on them)". PiglinGameTests proves it for a piglin with a golden sword (any harm to the villager counted);
 * the nightly sweep found that hoglins and brutes going for villagers had no test (Piglins.java:139 and :111 mutants
 * survived). Here each kind on the roster that fights hand to hand, penned with a villager, strikes that villager.
 */
public class QaPiglinRaidGameTests implements FabricGameTest {
	private static final String AREA = "aliveworkplace_test:huge_area";
	private static final ResourceLocation PIGLINS = AliveWorkplace.id("piglin_incursion");
	private static final BlockPos HALL = new BlockPos(15, 2, 15);

	private static BlockPos village(GameTestHelper helper) {
		Leftovers.clear(helper);
		ServerLevel level = helper.getLevel();
		int radius = VillageHalls.RADIUS;
		VillageHalls.RADIUS = 20;
		BlockPos hall = helper.absolutePos(HALL);
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

	/** A glass pen, three high on the floor, from (x0, z0) to (x1, z1), corners inclusive. */
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

	/** One of the culture's raiders of kind {@code entity}, as a raid makes it (its outfit and the first roster line of that kind). */
	private static Mob raider(GameTestHelper helper, Culture culture, EntityType<?> entity, BlockPos at) {
		ServerLevel level = helper.getLevel();
		Culture.Member member = culture.roster().stream().filter(m -> m.entity().equals(EntityType.getKey(entity))).findFirst().orElse(null);
		helper.assertTrue(member != null, "the roster has no " + EntityType.getKey(entity));
		Mob mob = Threats.create(level, member);
		helper.assertTrue(mob != null, "no " + member.entity());
		BlockPos p = helper.absolutePos(at);
		mob.moveTo(p.getX() + 0.5, p.getY(), p.getZ() + 0.5, 0f, 0f);
		mob.finalizeSpawn(level, level.getCurrentDifficultyAt(p), MobSpawnType.EVENT, null);
		Threats.outfit(level, mob, culture, member);
		level.addFreshEntityWithPassengers(mob);
		return mob;
	}

	/** {@code kind}, raiding and penned 7 blocks from a villager, strikes that villager itself within the time. */
	private static void strikes(GameTestHelper helper, EntityType<?> kind) {
		BlockPos hall = village(helper);
		ServerLevel level = helper.getLevel();
		Culture culture = Threats.get(PIGLINS).orElse(null);
		helper.assertTrue(culture != null, "piglin_incursion.json didn't load");
		pen(helper, 2, 2, 12, 12);
		Villager villager = helper.spawn(EntityType.VILLAGER, new BlockPos(5, 2, 5));
		villager.setNoAi(true);
		Mob raider = raider(helper, culture, kind, new BlockPos(10, 2, 10));
		VillageRaids.track(level, hall, culture, List.of(raider));
		helper.succeedWhen(() -> helper.assertTrue(villager.getLastHurtByMob() == raider || villager.isDeadOrDying() && villager.getKillCredit() == raider,
			"the raiding " + EntityType.getKey(kind).getPath() + " hasn't struck the villager (last hurt by " + villager.getLastHurtByMob() + ")"));
	}

	//$ gametest_ticks_batch AREA '400' '"qaHoglinStrikes"'
	@GameTest(template = AREA, timeoutTicks = 400, batch = "qaHoglinStrikes")
	public void aRaidingHoglinGoesForAVillager(GameTestHelper helper) {
		strikes(helper, EntityType.HOGLIN);
	}

	//$ gametest_ticks_batch AREA '400' '"qaBruteStrikes"'
	@GameTest(template = AREA, timeoutTicks = 400, batch = "qaBruteStrikes")
	public void aRaidingPiglinBruteGoesForAVillager(GameTestHelper helper) {
		strikes(helper, EntityType.PIGLIN_BRUTE);
	}

	/**
	 * 32.10: a plunderer "drops it if killed". Forty piglins each carrying 10 gold ingots (shown on their heads), killed by
	 * a player as a player kills them: exactly the 400 ingots they carried fall, never one more (the ingot shown on the
	 * head mustn't drop as a piece of armour too).
	 */
	//$ gametest_ticks_batch AREA '60' '"qaPlunderNoMore"'
	@GameTest(template = AREA, timeoutTicks = 60, batch = "qaPlunderNoMore")
	public void plunderersKilledByAPlayerDropExactlyTheGoldTheyCarried(GameTestHelper helper) {
		BlockPos hall = village(helper);
		ServerLevel level = helper.getLevel();
		Culture culture = Threats.get(PIGLINS).orElse(null);
		helper.assertTrue(culture != null, "piglin_incursion.json didn't load");
		ServerPlayer player = helper.makeMockServerPlayerInLevel();
		Leftovers.after(helper, () -> level.getServer().getPlayerList().remove(player));
		List<Mob> piglins = new java.util.ArrayList<>();
		for (int i = 0; i < 40; i++) {
			Mob piglin = raider(helper, culture, EntityType.PIGLIN, new BlockPos(3 + (i % 8) * 3, 2, 3 + (i / 8) * 3));
			piglin.setNoAi(true);
			piglin.setItemSlot(net.minecraft.world.entity.EquipmentSlot.HEAD, ItemStack.EMPTY); // (a bare head, where the gold is shown)
			Plunder.carry(level, piglin, new ItemStack(Items.GOLD_INGOT, 10));
			piglins.add(piglin);
		}
		VillageRaids.track(level, hall, culture, piglins);
		helper.runAfterDelay(2, () -> {
			helper.assertTrue(piglins.stream().allMatch(m -> m.getItemBySlot(net.minecraft.world.entity.EquipmentSlot.HEAD).is(Items.GOLD_INGOT)),
				"the gold isn't shown on their heads");
			for (Mob piglin : piglins) {
				piglin.hurt(level.damageSources().playerAttack(player), 1000f);
			}
			helper.runAfterDelay(3, () -> {
				helper.assertTrue(piglins.stream().noneMatch(Mob::isAlive), "a piglin lived");
				int ingots = level.getEntitiesOfClass(ItemEntity.class, helper.getBounds().inflate(4), e -> e.getItem().is(Items.GOLD_INGOT))
					.stream().mapToInt(e -> e.getItem().getCount()).sum();
				helper.assertTrue(ingots == 400, "40 plunderers carrying 10 ingots each dropped " + ingots + " ingots");
				helper.succeed();
			});
		});
	}
}
