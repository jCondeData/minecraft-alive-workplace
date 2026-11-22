package io.github.jcondedata.aliveworkplace.test;

import io.github.jcondedata.aliveworkplace.WorkplaceConfig;
import io.github.jcondedata.aliveworkplace.guard.BattleStations;
import io.github.jcondedata.aliveworkplace.guard.GuardCombat;
import io.github.jcondedata.aliveworkplace.guard.Guards;
import io.github.jcondedata.aliveworkplace.guard.VillageRaids;
import io.github.jcondedata.aliveworkplace.hall.VillageHalls;
import io.github.jcondedata.aliveworkplace.registry.ModBlocks;
import io.github.jcondedata.aliveworkplace.registry.ModVillagers;
import io.github.jcondedata.aliveworkplace.threat.Culture;
import io.github.jcondedata.aliveworkplace.threat.Sieges;
import io.github.jcondedata.aliveworkplace.threat.ThreatData;
import io.github.jcondedata.aliveworkplace.threat.Threats;
import io.github.jcondedata.aliveworkplace.work.Jobs;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;

/**
 * B1475: in a raid a guard goes for a raider within guardRadius of his post even when walls hide it, but not for one
 * farther out, and outside a raid not for a monster he can't see. The guard stands shut in a stone room by his post.
 */
public class GuardRaidSightB1475GameTests implements FabricGameTest {
	private static final String AREA = "aliveworkplace_test:huge_area";
	private static final ResourceLocation RAIDERS = ResourceLocation.fromNamespaceAndPath("aliveworkplace_test", "guard_raid_sight_b1475");
	private static final BlockPos HALL = new BlockPos(15, 2, 15);
	private static final BlockPos POST = new BlockPos(3, 2, 3);
	private static final int WATCH = 200;

	/** A guard by his post, walled in on every side and roofed over (he sees nothing outside). */
	private static Villager walledGuard(GameTestHelper helper) {
		for (int x = 1; x <= 5; x++) {
			for (int z = 1; z <= 5; z++) {
				for (int y = 2; y <= 5; y++) {
					boolean wall = x == 1 || x == 5 || z == 1 || z == 5 || y == 5;
					helper.setBlock(new BlockPos(x, y, z), wall ? Blocks.STONE.defaultBlockState() : Blocks.AIR.defaultBlockState());
				}
			}
		}
		helper.setBlock(POST, ModBlocks.GUARD_POST);
		Villager v = helper.spawn(EntityType.VILLAGER, new BlockPos(2, 2, 2));
		Jobs.employ(helper.getLevel(), v, helper.absolutePos(POST), ModVillagers.GUARD_POST_POI, ModVillagers.GUARD);
		v.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.IRON_SWORD));
		return v;
	}

	private static Mob husk(GameTestHelper helper, BlockPos at, boolean raider) {
		ServerLevel level = helper.getLevel();
		BlockPos p = helper.absolutePos(at);
		Mob m = EntityType.HUSK.create(level);
		m.moveTo(p.getX() + 0.5, p.getY(), p.getZ() + 0.5, 0f, 0f);
		if (raider) {
			m.addTag(VillageRaids.TAG);
		}
		m.setNoAi(true);
		m.setPersistenceRequired();
		level.addFreshEntity(m);
		return m;
	}

	/** A village round the hall with a raid of {@code raiders} on it, put back as it was afterwards. */
	private static void raid(GameTestHelper helper, List<Mob> raiders) {
		ServerLevel level = helper.getLevel();
		int radius = VillageHalls.RADIUS;
		VillageHalls.RADIUS = 40;
		BlockPos hall = helper.absolutePos(HALL);
		Culture culture = Culture.read(RAIDERS, com.google.gson.JsonParser.parseString("{\"where\": {\"min_villagers\": 100000}, \"roster\": ["
			+ "{\"entity\": \"minecraft:husk\", \"share\": 100, \"role\": \"melee\"}], \"tactics\": []}").getAsJsonObject());
		Map<ResourceLocation, Culture> loaded = new LinkedHashMap<>();
		Threats.all().forEach(c -> loaded.put(c.id(), c));
		Map<ResourceLocation, Culture> with = new LinkedHashMap<>(loaded);
		with.put(RAIDERS, culture);
		Threats.setForTest(with);
		Leftovers.after(helper, () -> {
			Threats.setForTest(loaded);
			Sieges.siege(level, hall).ifPresent(s -> Sieges.lift(level, s));
			VillageRaids.forget(hall);
			ThreatData.get(level).forgetSiege(hall);
			ThreatData.get(level).forgetPast(hall);
			Sieges.forget();
			BattleStations.forget();
			VillageHalls.RADIUS = radius;
			new WorkplaceConfig().apply();
		});
		helper.setBlock(HALL, ModBlocks.VILLAGE_HALL);
		VillageRaids.track(level, hall, culture, raiders);
		helper.assertTrue(VillageRaids.active(hall).isPresent(), "(setup) no raid on the village");
	}

	private static void setUp(GameTestHelper helper) {
		Leftovers.clear(helper);
		Leftovers.halls(helper);
		long time = helper.getLevel().getDayTime();
		helper.setDayTime(6000);
		Leftovers.after(helper, () -> helper.getLevel().setDayTime(time));
	}

	/** In a raid, a raider 12 blocks out that the walls hide is still the guard's foe. */
	//$ gametest_ticks_batch AREA '400' '"guardRaidSightB1475Near"'
	@GameTest(template = AREA, timeoutTicks = 400, batch = "guardRaidSightB1475Near")
	public void aGuardGoesForAHiddenRaiderWithinGuardRadius(GameTestHelper helper) {
		setUp(helper);
		Villager guard = walledGuard(helper);
		Mob raider = husk(helper, new BlockPos(15, 2, 3), true);
		raid(helper, List.of(raider));
		helper.succeedWhen(() -> helper.assertTrue(GuardCombat.foe(guard) == raider, "the guard never went for the hidden raider 12 blocks from his post"));
	}

	/** In a raid, a hidden raider farther from the post than guardRadius is left alone. */
	//$ gametest_ticks_batch AREA '400' '"guardRaidSightB1475Far"'
	@GameTest(template = AREA, timeoutTicks = 400, batch = "guardRaidSightB1475Far")
	public void aGuardLeavesAHiddenRaiderBeyondGuardRadius(GameTestHelper helper) {
		setUp(helper);
		Villager guard = walledGuard(helper);
		helper.assertTrue(Guards.RADIUS == 24, "(setup) guardRadius isn't the default 24 but " + Guards.RADIUS);
		Mob raider = husk(helper, new BlockPos(27, 2, 27), true); // about 34 blocks from the post, inside the raid area
		raid(helper, List.of(raider));
		helper.onEachTick(() -> {
			helper.assertTrue(GuardCombat.foe(guard) == null, "the guard went for a hidden raider beyond guardRadius at tick " + helper.getTick());
			if (helper.getTick() > WATCH) {
				helper.succeed();
			}
		});
	}

	/** With no raid on, a husk 12 blocks out that the walls hide is no foe of his (he must see a monster to go for it). */
	//$ gametest_ticks_batch AREA '400' '"guardRaidSightB1475NoRaid"'
	@GameTest(template = AREA, timeoutTicks = 400, batch = "guardRaidSightB1475NoRaid")
	public void aGuardLeavesAHiddenMonsterAloneOutsideARaid(GameTestHelper helper) {
		setUp(helper);
		Villager guard = walledGuard(helper);
		husk(helper, new BlockPos(15, 2, 3), true); // even with a raider's tag, with no raid on
		husk(helper, new BlockPos(3, 2, 15), false);
		helper.onEachTick(() -> {
			helper.assertTrue(GuardCombat.foe(guard) == null, "the guard went for a hidden monster with no raid on at tick " + helper.getTick());
			if (helper.getTick() > WATCH) {
				helper.succeed();
			}
		});
	}
}
