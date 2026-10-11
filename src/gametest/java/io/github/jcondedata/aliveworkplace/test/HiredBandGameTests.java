package io.github.jcondedata.aliveworkplace.test;

import io.github.jcondedata.aliveworkplace.Expansions;
import io.github.jcondedata.aliveworkplace.guard.Escorts;
import io.github.jcondedata.aliveworkplace.guard.Guards;
import io.github.jcondedata.aliveworkplace.guard.Mercenaries;
import io.github.jcondedata.aliveworkplace.guard.WarParties;
import io.github.jcondedata.aliveworkplace.hall.VillageHalls;
import io.github.jcondedata.aliveworkplace.registry.ModBlocks;
import io.github.jcondedata.aliveworkplace.story.BanditKingStory;
import java.util.List;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;

/**
 * B117: a villager made a guard at runtime (a hired band of Mercenaries, the Siege-breakers, the Bandit King story's
 * allies) gets a guard's brain at once, so they fight a zombie beside them without a save and reload.
 */
public class HiredBandGameTests implements FabricGameTest {
	private static final String AREA = "aliveworkplace_test:big_area";
	private static final BlockPos HALL = new BlockPos(5, 2, 5);
	private static final BlockPos ZOMBIE = new BlockPos(5, 2, 10);

	/** A night (no sunburn) and the hall; everything put back afterwards. */
	private static BlockPos village(GameTestHelper helper) {
		Leftovers.clear(helper);
		ServerLevel level = helper.getLevel();
		long time = level.getDayTime();
		boolean open = Expansions.openForTests;
		int radius = VillageHalls.RADIUS;
		VillageHalls.RADIUS = 16;
		Expansions.openForTests = true;
		helper.setBlock(HALL, ModBlocks.VILLAGE_HALL);
		Leftovers.after(helper, () -> {
			level.getEntitiesOfClass(Villager.class, helper.getBounds().inflate(16), Mercenaries::isMercenary).forEach(Villager::discard);
			VillageHalls.RADIUS = radius;
			Expansions.openForTests = open;
			level.setDayTime(time);
		});
		level.setDayTime(18000);
		return helper.absolutePos(HALL);
	}

	private static ServerPlayer player(GameTestHelper helper, BlockPos at) {
		ServerPlayer player = helper.makeMockServerPlayerInLevel();
		player.setGameMode(GameType.SURVIVAL);
		player.moveTo(at.getX() + 0.5, at.getY(), at.getZ() + 0.5);
		Leftovers.after(helper, () -> {
			player.getInventory().clearContent();
			Escorts.scan(helper.getLevel().getServer());
			helper.getLevel().getServer().getPlayerList().remove(player);
		});
		return player;
	}

	/** A zombie standing still a few blocks from the hall. */
	private static Zombie zombie(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		Zombie zombie = EntityType.ZOMBIE.create(level);
		BlockPos at = helper.absolutePos(ZOMBIE);
		zombie.moveTo(at.getX() + 0.5, at.getY(), at.getZ() + 0.5, 0f, 0f);
		zombie.setNoAi(true);
		zombie.setPersistenceRequired();
		level.addFreshEntity(zombie);
		return zombie;
	}

	private static String key(Component said) {
		return said.getContents() instanceof TranslatableContents t ? t.getKey() : said.getString();
	}

	/** A band hired at the hall beside a zombie: they kill it straight away, no reload. */
	//$ gametest_ticks_batch AREA '400' '"hiredBandFights"'
	@GameTest(template = AREA, timeoutTicks = 400, batch = "hiredBandFights")
	public void aHiredBandFightsAtOnce(GameTestHelper helper) {
		BlockPos hall = village(helper);
		ServerLevel level = helper.getLevel();
		ServerPlayer player = player(helper, hall.above());
		player.getInventory().add(new ItemStack(Items.EMERALD, Mercenaries.PRICE_EMERALDS));
		Zombie zombie = zombie(helper);
		Component said = Mercenaries.hire(level, hall, player);
		helper.assertTrue(key(said).equals("message.aliveworkplace.mercenaries.hired"), "not hired: " + said.getString());
		List<Villager> band = Mercenaries.near(level, hall);
		helper.assertTrue(band.size() == Mercenaries.BAND && band.stream().allMatch(Guards::isGuard), "band of " + band.size());
		helper.succeedWhen(() -> helper.assertTrue(!zombie.isAlive(), "the zombie lives (" + zombie.getHealth() + "): the band stands idle"));
	}

	/** The Siege-breakers, hired beside a zombie: they kill it straight away, no reload. */
	//$ gametest_ticks_batch AREA '400' '"siegeBreakersFight"'
	@GameTest(template = AREA, timeoutTicks = 400, batch = "siegeBreakersFight")
	public void siegeBreakersFightAtOnce(GameTestHelper helper) {
		BlockPos hall = village(helper);
		ServerLevel level = helper.getLevel();
		ServerPlayer player = player(helper, hall.above().south(3));
		player.getInventory().add(new ItemStack(Items.EMERALD, WarParties.PRICE_EMERALDS));
		Zombie zombie = zombie(helper);
		Component said = WarParties.hire(level, hall, player);
		helper.assertTrue(key(said).equals("message.aliveworkplace.siege_breakers.hired"), "not hired: " + said.getString());
		List<Villager> band = Mercenaries.near(level, hall).stream().filter(WarParties::isSiegeBreaker).toList();
		helper.assertTrue(band.size() == WarParties.BAND, "band of " + band.size());
		helper.succeedWhen(() -> helper.assertTrue(!zombie.isAlive(), "the zombie lives (" + zombie.getHealth() + "): the band stands idle"));
	}

	/** The Bandit King story's allies, called to the hall beside a zombie: they kill it straight away, no reload. */
	//$ gametest_ticks_batch AREA '400' '"stormAlliesFight"'
	@GameTest(template = AREA, timeoutTicks = 400, batch = "stormAlliesFight")
	public void stormAlliesFightAtOnce(GameTestHelper helper) {
		BlockPos hall = village(helper);
		ServerLevel level = helper.getLevel();
		Zombie zombie = zombie(helper);
		List<Villager> allies = new java.util.ArrayList<>();
		for (int i = 0; i < 2; i++) {
			Villager ally = BanditKingStory.ally(level, hall, Component.literal("Ally"), i);
			helper.assertTrue(ally != null && Guards.isGuard(ally), "no ally");
			// they come to the top of the ground, which here is the test area's roof: down to the floor beside the hall
			BlockPos floor = hall.offset(i - 1, 0, 2);
			ally.teleportTo(floor.getX() + 0.5, floor.getY(), floor.getZ() + 0.5);
			allies.add(ally);
		}
		helper.succeedWhen(() -> helper.assertTrue(!zombie.isAlive(), "the zombie lives (" + zombie.getHealth() + "): the allies stand idle "
			+ allies.stream().map(a -> a.blockPosition() + " " + a.getBrain().getActiveActivities()).toList()));
	}
}
