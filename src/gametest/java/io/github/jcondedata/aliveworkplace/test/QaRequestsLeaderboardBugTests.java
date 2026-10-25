package io.github.jcondedata.aliveworkplace.test;

import io.github.jcondedata.aliveworkplace.realm.Leaderboard;
import io.github.jcondedata.aliveworkplace.realm.RealmData;
import io.github.jcondedata.aliveworkplace.realm.Realms;
import io.github.jcondedata.aliveworkplace.story.PersonalRequests;
import io.github.jcondedata.aliveworkplace.story.Quest;
import io.github.jcondedata.aliveworkplace.test.RealmGameTests.Land;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.animal.Cat;
import net.minecraft.world.entity.npc.Villager;

/** QA (lane-c-1025-0053): bug candidates for 31.10 and 33.19, failing on the mod as it is. Not registered on main. */
public class QaRequestsLeaderboardBugTests implements FabricGameTest {
	private static final String AREA = PersonalRequestGameTests.AREA;
	private static final String LAND = "aliveworkplace_test:huge_area";

	/** "A cat within 10 blocks of their bed": a cat sitting on the floor 10 blocks along from the bed counts. */
	//$ gametest_ticks_batch AREA '80' '"qaBugCatTen"'
	@GameTest(template = AREA, timeoutTicks = 80, batch = "qaBugCatTen")
	public void aCatExactlyTenBlocksFromTheBedCounts(GameTestHelper helper) {
		PersonalRequestGameTests.village(helper, () -> {
			ServerLevel level = helper.getLevel();
			BlockPos hall = helper.absolutePos(PersonalRequestGameTests.HALL);
			Villager dara = PersonalRequestGameTests.worker(helper, new BlockPos(3, 2, 3), "Dara", "minecraft:farmer", 2);
			ServerPlayer me = PersonalRequestGameTests.friend(helper, new BlockPos(5, 2, 6), dara, 300);
			BlockPos bed = new BlockPos(2, 2, 2);
			dara.getBrain().setMemory(MemoryModuleType.HOME, GlobalPos.of(level.dimension(), helper.absolutePos(bed)));
			Quest quest = PersonalRequestGameTests.accepted(helper, "cat_by_the_door", dara, me);
			Cat cat = helper.spawn(EntityType.CAT, bed.offset(10, 0, 0));
			cat.setNoAi(true);
			PersonalRequests.look(level, hall, PersonalRequestGameTests.hallEntity(helper), true);
			helper.assertTrue(PersonalRequests.of(dara) == null, "a cat 10 blocks from the bed, on the same floor, didn't count (distance from the bed's centre "
				+ Math.sqrt(cat.distanceToSqr(helper.absolutePos(bed).getCenter())) + ")");
			helper.succeed();
		});
	}

	/** A one-village realm of one villager reads "1 village, 1 villager", not "1 villagers". */
	//$ gametest_ticks_batch LAND '100' '"qaBugRealmOne"'
	@GameTest(template = LAND, timeoutTicks = 100, batch = "qaBugRealmOne")
	public void aRealmOfOneVillagerReadsInTheSingular(GameTestHelper helper) {
		Land land = LeaderboardGameTests.three(helper);
		MinecraftServer server = land.level().getServer();
		Realms.round(land.level(), land.hall(2), land.entity(2), 1);
		land.data().putRealm(new RealmData.Realm(UUID.randomUUID(), Optional.empty(), land.at(2), List.of(new RealmData.Member(land.at(2), 0)), Optional.empty(), "barony", 0));
		String line = Leaderboard.line(Leaderboard.Board.REALM, Leaderboard.standings(server, Leaderboard.Board.REALM)).getString();
		helper.assertTrue(line.endsWith("(Cal) 1 village, 1 villager"), "the realm line: " + line);
		helper.succeed();
	}
}
