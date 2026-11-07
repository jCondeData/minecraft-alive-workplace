package io.github.jcondedata.aliveworkplace.test;

import io.github.jcondedata.aliveworkplace.hall.VillageHallBlockEntity;
import io.github.jcondedata.aliveworkplace.registry.ModVillagers;
import io.github.jcondedata.aliveworkplace.threat.HostileSpawns;
import io.github.jcondedata.aliveworkplace.wonder.GreatWallSentries;
import io.github.jcondedata.aliveworkplace.wonder.WonderSite;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Container;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.animal.IronGolem;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.npc.VillagerType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;

/**
 * Bug test (QA, 2026-11-01; the passing checks are QaThreatSpawnsPeace1101GameTests). B211's spec: with
 * forceVillageSpawns off a refused golem is "left out and logged", and the Golem Smith keeps his iron when his golem is
 * refused. A Great Wall's lost sentry is paid for first: {@code GreatWallSentries.replace} takes the stockpile's four iron
 * blocks and pumpkin, then the golem is refused and never comes, so the village loses the iron every morning for nothing.
 * Not registered in fabric.mod.json until the fix lands with it.
 */
public class QaThreatSpawnsPeaceBugs1101GameTests implements FabricGameTest {
	private static final String HUGE = "aliveworkplace_test:huge_area";

	/**
	 * B211 with forceVillageSpawns off: a refused Wall sentry is left out (logged), and a lost one isn't "made" either:
	 * the stockpile's four iron blocks and pumpkin stay, as the Golem Smith keeps his iron, and the chronicle says nothing.
	 */
	//$ gametest_ticks_batch HUGE '60' '"qa1101WallOff"'
	@GameTest(template = HUGE, timeoutTicks = 60, batch = "qa1101WallOff")
	public void withVillageForcingOffARefusedSentryIsLeftOutAndItsIronKept(GameTestHelper helper) {
		BlockPos hall = QaThreatSpawnsPeace1101GameTests.wallHall(helper);
		ServerLevel level = helper.getLevel();
		long time = level.getDayTime();
		Leftovers.after(helper, () -> level.setDayTime(time));
		BlockPos stoneRel = new BlockPos(12, 2, 24);
		helper.setBlock(stoneRel, Blocks.STONE);
		WonderSite site = QaThreatSpawnsPeace1101GameTests.wall(helper, hall, helper.absolutePos(stoneRel));
		helper.setBlock(new BlockPos(13, 2, 24), Blocks.CHEST);
		Container chest = (Container) level.getBlockEntity(helper.absolutePos(new BlockPos(13, 2, 24)));
		chest.setItem(0, new ItemStack(Items.IRON_BLOCK, 4));
		chest.setItem(1, new ItemStack(Items.CARVED_PUMPKIN, 1));
		Villager tinkerer = helper.spawn(EntityType.VILLAGER, new BlockPos(3, 2, 20));
		tinkerer.setNoAi(true);
		tinkerer.setVillagerData(tinkerer.getVillagerData().setType(VillagerType.PLAINS).setProfession(ModVillagers.TINKERER).setLevel(3));
		tinkerer.setCustomName(Component.literal("Tibbs"));
		QaThreatSpawnsPeace1101GameTests.refusing(helper, e -> e instanceof IronGolem, true, false);
		helper.assertTrue(GreatWallSentries.man(level, site) == 0, "a refused sentry was counted with forceVillageSpawns off");
		helper.assertTrue(level.getEntitiesOfClass(IronGolem.class, helper.getBounds().inflate(8)).isEmpty(), "a golem stands with forceVillageSpawns off");
		helper.assertTrue(HostileSpawns.lastWarning().startsWith("Could not spawn minecraft:iron_golem") && HostileSpawns.lastWarning().contains("forceVillageSpawns is off"),
			"the log line: " + HostileSpawns.lastWarning());
		GreatWallSentries.forget();
		helper.assertTrue(GreatWallSentries.replace(level, hall) == 0, "a refused sentry was replaced");
		helper.assertTrue(chest.getItem(0).is(Items.IRON_BLOCK) && chest.getItem(0).getCount() == 4 && chest.getItem(1).is(Items.CARVED_PUMPKIN),
			"the stockpile paid for a sentry that never came: " + chest.getItem(0) + ", " + chest.getItem(1));
		VillageHallBlockEntity entity = (VillageHallBlockEntity) level.getBlockEntity(hall);
		helper.assertTrue(entity.chronicle().stream().noneMatch(e -> e.text().getString().contains("made a new sentry")), "the chronicle says a sentry was made");
		helper.succeed();
	}

}
