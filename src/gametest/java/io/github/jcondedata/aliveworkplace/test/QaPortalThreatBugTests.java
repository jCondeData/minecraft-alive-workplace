package io.github.jcondedata.aliveworkplace.test;

import io.github.jcondedata.aliveworkplace.AliveWorkplace;
import io.github.jcondedata.aliveworkplace.WorkplaceConfig;
import io.github.jcondedata.aliveworkplace.guard.BanditCamps;
import io.github.jcondedata.aliveworkplace.guard.VillageRaids;
import io.github.jcondedata.aliveworkplace.hall.VillageHallBlockEntity;
import io.github.jcondedata.aliveworkplace.hall.VillageHalls;
import io.github.jcondedata.aliveworkplace.hall.VillageRanks;
import io.github.jcondedata.aliveworkplace.registry.ModBlocks;
import io.github.jcondedata.aliveworkplace.threat.Lairs;
import io.github.jcondedata.aliveworkplace.threat.ThreatData;
import io.github.jcondedata.aliveworkplace.guard.BanditFort;
import io.github.jcondedata.aliveworkplace.threat.Culture;
import io.github.jcondedata.aliveworkplace.threat.Threats;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.animal.Chicken;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.NetherPortalBlock;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockState;

/**
 * QA (lane D, 2026-10-24), bug tests for B373's portal guard (see {@link QaPortalThreatsGameTests}): the mixin keeps a
 * threat's tagged mob out of a portal, but a raider that rides a mount goes through with its untagged mount, and the
 * Bandit King's fort's men (tagged {@code aliveworkplace_fort}, not as bandits) walk through.
 */
public class QaPortalThreatBugTests implements FabricGameTest {
	private static final String HUGE = "aliveworkplace_test:huge_area";
	private static final String TALL = "aliveworkplace_test:tall_area";
	private static final ResourceLocation DESERT = AliveWorkplace.id("desert_raiders");

	/**
	 * A desert raid's husk that rolled a chicken jockey rides its chicken into a player's portal by the village: the raider
	 * stays in the world (every raider keeps out of portals), its chicken with it. A plain chicken beside it leaves.
	 */
	//$ gametest_ticks_batch HUGE '80' '"qaPortalJockey"'
	@GameTest(template = HUGE, timeoutTicks = 80, batch = "qaPortalJockey")
	public void aRaiderRidingAChickenStaysOutOfAPortal(GameTestHelper helper) {
		Leftovers.clear(helper);
		ServerLevel level = helper.getLevel();
		List<UUID> strays = QaPortalThreatsGameTests.strays(helper);
		Optional<Culture> desert = Threats.get(DESERT);
		helper.assertTrue(desert.isPresent(), "desert_raiders.json didn't load");
		Culture culture = desert.get();
		List<BlockPos> blocks = QaPortalThreatsGameTests.portal(helper, new BlockPos(10, 2, 10));
		BlockPos in = blocks.get(0);
		Culture.Member member = culture.roster().stream().filter(m -> m.entity().equals(EntityType.getKey(EntityType.HUSK))).findFirst().orElseThrow();
		Mob husk = Threats.create(level, member);
		helper.assertTrue(husk != null, "no husk");
		husk.moveTo(in.getX() + 0.5, in.getY(), in.getZ() + 3.5, 0f, 0f);
		husk.finalizeSpawn(level, level.getCurrentDifficultyAt(in), MobSpawnType.EVENT, null);
		Threats.outfit(level, husk, culture, member);
		((Zombie) husk).setBaby(true);
		level.addFreshEntity(husk);
		Leftovers.after(helper, husk::discard);
		strays.add(husk.getUUID());
		Chicken mount = EntityType.CHICKEN.create(level);
		mount.moveTo(husk.getX(), husk.getY(), husk.getZ(), 0f, 0f);
		level.addFreshEntity(mount);
		Leftovers.after(helper, mount::discard);
		strays.add(mount.getUUID());
		helper.assertTrue(husk.startRiding(mount, true), "the husk didn't mount the chicken");
		helper.assertTrue(husk.getTags().contains(Threats.RAIDER_TAG), "the husk isn't a raider: " + husk.getTags());
		Chicken plain = EntityType.CHICKEN.create(level);
		plain.moveTo(in.getX() + 1.5, in.getY(), in.getZ() + 0.5, 0f, 0f);
		level.addFreshEntity(plain);
		strays.add(plain.getUUID());
		// The mount carries the raider: it's the mount that is put in the portal.
		QaPortalThreatsGameTests.throughPortal(helper, in, plain, List.of(husk, mount), "a raider riding a chicken");
	}

	/**
	 * One of the Bandit King's fort's men (a threat's mob, kept to his fort) walks into a player's portal: he stays in
	 * the world. A plain pillager beside him leaves.
	 */
	//$ gametest_ticks_batch TALL '80' '"qaPortalFort"'
	@GameTest(template = TALL, timeoutTicks = 80, batch = "qaPortalFort")
	public void aBanditOfTheFortStaysOutOfAPortal(GameTestHelper helper) {
		Leftovers.clear(helper);
		ServerLevel level = helper.getLevel();
		List<UUID> strays = QaPortalThreatsGameTests.strays(helper);
		for (int x = 0; x < 30; x++) {
			for (int z = 0; z < 30; z++) {
				helper.setBlock(new BlockPos(x, 2, z), Blocks.DIRT);
				helper.setBlock(new BlockPos(x, 3, z), Blocks.GRASS_BLOCK);
			}
		}
		BanditFort.Fort fort = BanditFort.at(helper.absolutePos(new BlockPos(15, 3, 15)), Rotation.NONE);
		helper.assertTrue(BanditFort.place(level, fort, 0, false), "the fort's first stage wasn't placed");
		List<Mob> men = BanditFort.men(level, fort.yard(), 24);
		helper.assertTrue(!men.isEmpty(), "the fort has no men");
		Leftovers.after(helper, () -> BanditFort.men(level, fort.yard(), 64).forEach(Mob::discard));
		Mob man = men.get(0);
		strays.add(man.getUUID());
		List<BlockPos> blocks = QaPortalThreatsGameTests.portal(helper, new BlockPos(10, 22, 10));
		BlockPos in = blocks.get(0);
		Mob plain = EntityType.PILLAGER.create(level);
		plain.moveTo(in.getX() + 1.5, in.getY(), in.getZ() + 0.5, 0f, 0f);
		plain.setPersistenceRequired();
		level.addFreshEntity(plain);
		Leftovers.after(helper, plain::discard);
		strays.add(plain.getUUID());
		QaPortalThreatsGameTests.throughPortal(helper, in, plain, List.of(man), "one of the Bandit King's fort's men");
	}
}
