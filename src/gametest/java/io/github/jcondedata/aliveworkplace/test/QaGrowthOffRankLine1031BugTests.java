package io.github.jcondedata.aliveworkplace.test;

import io.github.jcondedata.aliveworkplace.hall.VillageGrowth;
import io.github.jcondedata.aliveworkplace.hall.VillageHallScreen;
import io.github.jcondedata.aliveworkplace.hall.VillageHalls;
import io.github.jcondedata.aliveworkplace.hall.VillageNeeds;
import io.github.jcondedata.aliveworkplace.registry.ModBlocks;
import java.util.List;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/**
 * QA (lane-e-1031-1913, config extremes and the sentences a player reads): {@code villageGrowthCap} 0 means villages
 * don't grow (config-switches.md: "0: villages don't grow, and the hall says so"). B576 made the People button's growth
 * line say "Villages don't grow on this server", but the hall's name button, on the same screen, still lists the
 * rank's perks as "... · grows to 0" ({@code screen.aliveworkplace.hall.rank_perks} is given
 * {@code VillageRanks.growthCap(rank)}, which is 0 with the cap off), in a village of two. Expected: the perks line
 * never promises growth "to 0"; it says the village doesn't grow (or leaves growth out) while the cap is off. The control
 * at the default cap (40) reads "grows to 40".
 */
public class QaGrowthOffRankLine1031BugTests implements FabricGameTest {
	private static final String BIG = "aliveworkplace_test:big_area";
	private static final BlockPos HALL = new BlockPos(11, 2, 11);

	//$ gametest_ticks_batch BIG '100' '"qaGrowthOffRankLine1031"'
	@GameTest(template = BIG, timeoutTicks = 100, batch = "qaGrowthOffRankLine1031")
	public void theRankPerksNeverSayGrowsToZero(GameTestHelper helper) {
		Leftovers.clear(helper);
		int radius = VillageHalls.RADIUS;
		int cap = VillageGrowth.CAP;
		VillageHalls.RADIUS = 16;
		ServerLevel level = helper.getLevel();
		ServerPlayer player = helper.makeMockServerPlayerInLevel();
		Leftovers.after(helper, () -> {
			VillageHalls.RADIUS = radius;
			VillageGrowth.CAP = cap;
			VillageNeeds.forget();
			level.getServer().getPlayerList().remove(player);
		});
		helper.spawn(EntityType.VILLAGER, new BlockPos(8, 2, 8));
		helper.spawn(EntityType.VILLAGER, new BlockPos(9, 2, 8));
		helper.setBlock(HALL, ModBlocks.VILLAGE_HALL);
		helper.runAfterDelay(5, () -> {
			VillageGrowth.CAP = 40;
			VillageNeeds.forget();
			String on = perks(player, helper.absolutePos(HALL));
			helper.assertTrue(on.endsWith("grows to 40"), "the control, the default cap: the perks read \"" + on + "\"");

			VillageGrowth.CAP = 0;
			VillageNeeds.forget();
			ItemStack people = VillageHallScreen.forTest(player, helper.absolutePos(HALL)).icon(1);
			List<String> peopleLore = RealmGameTests.lore(people);
			helper.assertTrue(peopleLore.contains("Villages don't grow on this server"), "setup: with the cap off the People button reads " + peopleLore);
			String off = perks(player, helper.absolutePos(HALL));
			helper.assertTrue(!off.contains("grows to 0"), "villageGrowthCap 0, a village of two: the hall's name button reads \"" + off + "\"");
			helper.succeed();
		});
	}

	/** The rank's perks line on the hall's name button. */
	private static String perks(ServerPlayer player, BlockPos hall) {
		ItemStack name = VillageHallScreen.forTest(player, hall).icon(VillageHallScreen.NAME);
		if (!name.is(Items.NAME_TAG)) {
			throw new net.minecraft.gametest.framework.GameTestAssertException("no name button: " + name);
		}
		return RealmGameTests.lore(name).stream().filter(l -> l.startsWith("Quests pay ")).findFirst()
			.orElseThrow(() -> new net.minecraft.gametest.framework.GameTestAssertException("no perks line in " + RealmGameTests.lore(name)));
	}
}
