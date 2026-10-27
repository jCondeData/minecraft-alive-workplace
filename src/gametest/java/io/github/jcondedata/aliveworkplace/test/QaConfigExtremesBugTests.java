package io.github.jcondedata.aliveworkplace.test;

import io.github.jcondedata.aliveworkplace.hall.VillageGrowth;
import io.github.jcondedata.aliveworkplace.hall.VillageHallScreen;
import io.github.jcondedata.aliveworkplace.hall.VillageHalls;
import io.github.jcondedata.aliveworkplace.hall.VillageNeeds;
import io.github.jcondedata.aliveworkplace.hall.VillageRanks;
import io.github.jcondedata.aliveworkplace.realm.Realms;
import io.github.jcondedata.aliveworkplace.registry.ModBlocks;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/**
 * QA bugs found testing config extremes (B576, B577). villageGrowthCap 0 means "villages
 * don't grow" (docs/wiki/config-switches.md), but the hall's growth line then reads "The village is full (0
 * villagers)" in a village of two: a count that contradicts the census right above it. Expected: a line that says the
 * village doesn't grow on this server (or no growth line), never a cap of 0 villagers.
 * <p>
 * And a realm at its realmMaxMembers (most visible at the least, 2) refuses one more with a chat sentence that starts
 * in lower case: "the Barony of X already holds 2 villages, as many as a realm may." (message.aliveworkplace.realm.full
 * puts the realm's title, "the Barony of ...", first). Expected: "The Barony of X already holds ...".
 */
public class QaConfigExtremesBugTests {
	private static final String BIG = "aliveworkplace_test:big_area";
	private static final BlockPos HALL = new BlockPos(11, 2, 11);
	private static final String HUGE = "aliveworkplace_test:huge_area";

	//$ gametest_ticks_batch HUGE '100' '"qaExtBugRealmFull"'
	@GameTest(template = HUGE, timeoutTicks = 100, batch = "qaExtBugRealmFull")
	public void realmFullSentenceStartsWithACapital(GameTestHelper helper) {
		RealmGameTests.Land land = RealmGameTests.land(helper, VillageRanks.Rank.TOWN, VillageRanks.Rank.HAMLET, VillageRanks.Rank.HAMLET);
		Realms.MAX_MEMBERS = 2; // land() puts the old value back after
		RealmGameTests.found(helper, land);
		helper.assertTrue(Realms.add(land.level(), land.hall(0), land.at(1), land.owner()).ok(), "the second village didn't join");
		String said = Realms.add(land.level(), land.hall(0), land.at(2), land.owner()).message().getString();
		helper.assertTrue(!said.isEmpty() && Character.isUpperCase(said.charAt(0)), "the full realm's refusal starts in lower case: \"" + said + "\"");
		helper.assertTrue(said.startsWith("The Barony of ") && said.endsWith(" already holds 2 villages, as many as a realm may."), "the full realm's refusal: \"" + said + "\"");
		helper.succeed();
	}

	//$ gametest_ticks_batch BIG '100' '"qaExtBugGrowthLine"'
	@GameTest(template = BIG, timeoutTicks = 100, batch = "qaExtBugGrowthLine")
	public void growthCapZeroLineDoesNotSayFullAtZero(GameTestHelper helper) {
		Leftovers.clear(helper);
		int radius = VillageHalls.RADIUS;
		int cap = VillageGrowth.CAP;
		VillageHalls.RADIUS = 16;
		VillageGrowth.CAP = 0;
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
			VillageNeeds.forget();
			ItemStack people = VillageHallScreen.forTest(player, helper.absolutePos(HALL)).icon(1);
			helper.assertTrue(people.is(Items.EMERALD), "no People icon: " + people);
			List<String> lore = RealmGameTests.lore(people);
			String line = lore.get(lore.size() - 1);
			helper.assertTrue(!line.contains("(0 villagers)"), "villageGrowthCap 0, a village of two: the hall says \"" + line + "\"");
			helper.assertTrue(line.equals("Villages don't grow on this server"), "villageGrowthCap 0: the growth line is \"" + line + "\"");
			helper.succeed();
		});
	}
}
