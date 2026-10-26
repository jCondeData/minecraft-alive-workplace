package io.github.jcondedata.aliveworkplace.test;

import io.github.jcondedata.aliveworkplace.hall.Chronicle;
import io.github.jcondedata.aliveworkplace.hall.VillageHallBlockEntity;
import io.github.jcondedata.aliveworkplace.hall.VillageHalls;
import io.github.jcondedata.aliveworkplace.people.ClassMigration;
import io.github.jcondedata.aliveworkplace.registry.ModAttachments;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.phys.Vec3;

/**
 * QA findings for ROADMAP 34.22 that fail on the mod as it is (kept off the main suite until fixed). The village is the
 * circle {@link VillageHalls#contains} draws (the census, the wiki); seeding goes by the square box round it.
 */
public class QaClassMigrationBugTests implements net.fabricmc.fabric.api.gametest.v1.FabricGameTest {
	private static final String AREA = "aliveworkplace_test:big_area";

	/**
	 * A player standing in the square's corner, 15 blocks out on both axes (21 blocks from the hall: outside the 16-block
	 * village) hears nothing, and a villager standing there is neither seeded by this hall nor counted in its chronicle.
	 */
	//$ gametest_ticks_batch AREA '200' '"qaClassCorner"'
	@GameTest(template = AREA, timeoutTicks = 200, batch = "qaClassCorner")
	public void theSquaresCornerIsOutsideTheVillage(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		VillageHallBlockEntity hall = QaClassMigrationGameTests.oldHall(helper, false);
		QaClassMigrationGameTests.singles(helper, 3);
		BlockPos at = hall.getBlockPos();
		BlockPos corner = at.offset(VillageHalls.RADIUS - 1, 0, VillageHalls.RADIUS - 1);
		helper.assertTrue(!VillageHalls.contains(at, corner), "the corner is in the village");
		List<Component> heard = new ArrayList<>();
		QaClassMigrationGameTests.listener(helper, heard, Vec3.atBottomCenterOf(corner), "qa-corner");
		Villager stranger = EntityType.VILLAGER.create(level);
		stranger.moveTo(corner.getX() + 0.5, corner.getY(), corner.getZ() + 0.5, 0, 0);
		stranger.setNoAi(true);
		level.addFreshEntity(stranger);
		QaClassMigrationGameTests.seedAll(level, hall, Chronicle.day(level));
		String name = VillageHalls.name(level, at).getString();
		List<String> lines = hall.chronicle().stream().filter(e -> e.kind() == Chronicle.Kind.CLASS).map(e -> e.text().getString()).toList();
		List<String> wrong = new ArrayList<>();
		if (!lines.equals(List.of("The people of " + name + " found their places: 3 peasants"))) {
			wrong.add("the chronicle counts the stranger: " + lines);
		}
		if (ModAttachments.SOCIAL_CLASS.has(stranger)) {
			wrong.add("a villager outside the village was seeded by its hall");
		}
		if (QaClassMigrationGameTests.heardSeeding(heard) != 0) {
			wrong.add("a player outside the village heard the chat line");
		}
		helper.assertTrue(wrong.isEmpty(), String.join("; ", wrong));
		helper.succeed();
	}
}
