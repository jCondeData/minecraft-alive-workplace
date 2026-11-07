package io.github.jcondedata.aliveworkplace.test;

import io.github.jcondedata.aliveworkplace.WorkplaceConfig;
import io.github.jcondedata.aliveworkplace.guard.VillageRaids;
import io.github.jcondedata.aliveworkplace.hall.VillageHalls;
import io.github.jcondedata.aliveworkplace.registry.ModBlocks;
import io.github.jcondedata.aliveworkplace.registry.ModVillagers;
import io.github.jcondedata.aliveworkplace.threat.SiegeReport;
import io.github.jcondedata.aliveworkplace.threat.Sieges;
import io.github.jcondedata.aliveworkplace.threat.ThreatData;
import io.github.jcondedata.aliveworkplace.work.Jobs;
import java.util.List;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/**
 * QA bug tests (lane D, 2026-11-06), NOT registered: each fails on main until the mod is fixed.
 * <ul>
 * <li>Siege report hero tie-break. ROADMAP 32.6 / SiegeReport's javadoc / sieges.md: the hero is "the guard with the
 *     most kills (the first to get there when two have as many)". {@code SiegeReport.hero} picks, among guards tied on
 *     the most kills, the one whose FIRST kill came first (the kills map's insertion order), not the one who reached the
 *     top count first. Aldred kills, Brannoc kills, Brannoc kills again (first to 2), Aldred kills again (2 too): the
 *     report names Aldred, the spec says Brannoc.</li>
 * </ul>
 */
public class QaPlunderReport1106dBugTests implements FabricGameTest {
	private static final String AREA = "aliveworkplace_test:huge_area";
	private static final BlockPos HALL = new BlockPos(15, 2, 15);

	private static Villager guard(GameTestHelper helper, int x, int z, String name) {
		BlockPos post = new BlockPos(x, 2, z + 2);
		helper.setBlock(post, ModBlocks.GUARD_POST);
		Villager v = helper.spawn(EntityType.VILLAGER, new BlockPos(x, 2, z));
		Jobs.employ(helper.getLevel(), v, helper.absolutePos(post), ModVillagers.GUARD_POST_POI, ModVillagers.GUARD);
		v.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.IRON_SWORD));
		v.setItemSlot(EquipmentSlot.OFFHAND, new ItemStack(Items.SHIELD));
		v.setNoAi(true);
		v.setCustomName(net.minecraft.network.chat.Component.literal(name));
		return v;
	}

	private static Mob raider(GameTestHelper helper, int x, int z) {
		ServerLevel level = helper.getLevel();
		Mob mob = EntityType.HUSK.create(level);
		BlockPos p = helper.absolutePos(new BlockPos(x, 2, z));
		mob.moveTo(p.getX() + 0.5, p.getY(), p.getZ() + 0.5, 0f, 0f);
		mob.addTag(VillageRaids.TAG);
		mob.setNoAi(true);
		mob.setPersistenceRequired();
		level.addFreshEntity(mob);
		return mob;
	}

	/**
	 * Aldred kills a raider, Brannoc kills one, Brannoc kills a second (the first to 2 kills), then Aldred kills a second
	 * (2 as well). The hero, "the first to get there when two have as many", is Brannoc; the report names him.
	 */
	//$ gametest_ticks_batch AREA '40' '"qaSiegeReportTie1106d"'
	@GameTest(template = AREA, timeoutTicks = 40, batch = "qaSiegeReportTie1106d")
	public void theHeroOnATieIsTheGuardWhoReachedTheMostKillsFirst(GameTestHelper helper) {
		Leftovers.clear(helper);
		ServerLevel level = helper.getLevel();
		int radius = VillageHalls.RADIUS;
		VillageHalls.RADIUS = 20;
		BlockPos hall = helper.absolutePos(HALL);
		ThreatData data = ThreatData.get(level);
		Leftovers.after(helper, () -> {
			level.getEntitiesOfClass(Mob.class, helper.getBounds().inflate(48), m -> !(m instanceof Villager)).forEach(Mob::discard);
			data.forgetSiege(hall);
			data.forgetPlunder(hall);
			data.forgetPast(hall);
			Sieges.forget();
			VillageHalls.RADIUS = radius;
			new WorkplaceConfig().apply();
		});
		data.forgetPlunder(hall);
		data.forgetPast(hall);
		Villager aldred = guard(helper, 18, 20, "Aldred");
		Villager brannoc = guard(helper, 22, 20, "Brannoc");
		List<Mob> raiders = List.of(raider(helper, 10, 8), raider(helper, 11, 8), raider(helper, 12, 8), raider(helper, 13, 8));
		ThreatData.Siege siege = new ThreatData.Siege(hall, level.getGameTime(), level.getDayTime() + 12000);
		siege.came = 4;
		data.beginSiege(siege);
		raiders.get(0).hurt(level.damageSources().mobAttack(aldred), 1000f);
		raiders.get(1).hurt(level.damageSources().mobAttack(brannoc), 1000f);
		raiders.get(2).hurt(level.damageSources().mobAttack(brannoc), 1000f);
		raiders.get(3).hurt(level.damageSources().mobAttack(aldred), 1000f);
		helper.assertTrue(siege.toGuards == 4 && siege.kills.get(aldred.getUUID()) == 2 && siege.kills.get(brannoc.getUUID()) == 2,
			"setup: the kills " + siege.kills + ", to guards " + siege.toGuards);
		String report = SiegeReport.text(level, siege).getString();
		helper.assertTrue(brannoc.getUUID().equals(SiegeReport.hero(siege)) && report.endsWith("Hero of the night: Brannoc, with 2 kills."),
			"Brannoc reached 2 kills first, Aldred only tied him later, yet the report reads: \"" + report + "\"");
		helper.succeed();
	}
}
