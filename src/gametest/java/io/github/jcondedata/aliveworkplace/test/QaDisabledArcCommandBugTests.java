package io.github.jcondedata.aliveworkplace.test;

import io.github.jcondedata.aliveworkplace.WorkplaceConfig;
import io.github.jcondedata.aliveworkplace.hall.Chronicle;
import io.github.jcondedata.aliveworkplace.hall.VillageHallBlockEntity;
import io.github.jcondedata.aliveworkplace.hall.VillageHalls;
import io.github.jcondedata.aliveworkplace.hall.VillageNeeds;
import io.github.jcondedata.aliveworkplace.registry.ModBlocks;
import io.github.jcondedata.aliveworkplace.story.ArcEffects;
import io.github.jcondedata.aliveworkplace.story.ArcState;
import io.github.jcondedata.aliveworkplace.story.Arcs;
import io.github.jcondedata.aliveworkplace.story.LostCaravanStory;
import io.github.jcondedata.aliveworkplace.story.Stories;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.commands.CommandSource;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec2;
import net.minecraft.world.phys.Vec3;

/**
 * QA bug test for {@code disabledArcs} (docs/wiki/config-switches.md: "Story arc ids that never start"): the operator's
 * {@code /workplace story start} of a disabled arc (B574).
 */
public class QaDisabledArcCommandBugTests implements FabricGameTest {
	private static final String AREA = "aliveworkplace_test:huge_area";
	private static final BlockPos HALL = new BlockPos(5, 2, 5);
	private static final ResourceLocation THREE = ResourceLocation.fromNamespaceAndPath("aliveworkplace_test", "test/three_chapters");
	private static final long DAY = VillageNeeds.DAY;

	/**
	 * The operator's {@code /workplace story start} of a disabled arc: it never starts, so the command mustn't say it
	 * "begins", and the village's cooldown (counted from its last arc's end) mustn't be reset by an arc that never ran.
	 */
	//$ gametest_ticks_batch AREA '40' '"qaDisabledArcByCommand"'
	@GameTest(template = AREA, timeoutTicks = 40, batch = "qaDisabledArcByCommand")
	public void qaADisabledArcNeverStartsByCommand(GameTestHelper helper) {
		Leftovers.clear(helper);
		settings(helper);
		ServerLevel level = helper.getLevel();
		VillageHallBlockEntity entity = hall(helper, HALL);
		BlockPos hall = helper.absolutePos(HALL);
		long today = Chronicle.day(level) + 1;
		setDay(level, today);
		Arcs.COOLDOWN_DAYS = 0;
		Arcs.AT_ONCE = 20;
		String village = VillageHalls.name(level, hall).getString();
		Arcs.start(level, hall, arc(helper)); // a village whose last arc ended today...
		Arcs.stop(level, hall);
		helper.assertTrue(Arcs.lastArcDay(level, hall) == today, "the stopped arc didn't start the cooldown: " + Arcs.lastArcDay(level, hall));
		setDay(level, today + 3); // ...three days ago
		int chronicled = chronicle(entity).size();
		Arcs.DISABLED = Set.of(THREE.toString());
		List<String> said = new ArrayList<>();
		List<String> wrong = new ArrayList<>();
		level.getServer().getCommands().performPrefixedCommand(op(level, hall, said), "workplace story start aliveworkplace_test:test/three_chapters");
		if (Arcs.running(level, hall) != null) {
			wrong.add("it is running: " + Arcs.running(level, hall).id);
		}
		if (said.contains("The Test of Three begins in " + village + ".")) {
			wrong.add("the operator was told \"The Test of Three begins in " + village + ".\" though it never ran (all said: " + said + ")");
		}
		if (!said.contains("The Test of Three is switched off on this server (disabledArcs in the config).")) {
			wrong.add("the command didn't say the arc is switched off (all said: " + said + ")");
		}
		long last = Arcs.lastArcDay(level, hall);
		if (last != today) {
			wrong.add("the village's arc cooldown was restarted (last arc day " + today + " -> " + last + ") by an arc that never ran");
		}
		List<String> lines = chronicle(entity);
		if (lines.size() != chronicled) {
			wrong.add("the chronicle got: " + lines.subList(chronicled, lines.size()));
		}
		helper.assertTrue(wrong.isEmpty(), String.join("; ", wrong));
		helper.succeed();
	}

	/** The settings these tests change, put back when the test ends. Once per test. */
	private static void settings(GameTestHelper helper) {
		int radius = VillageHalls.RADIUS;
		int cooldown = Arcs.COOLDOWN_DAYS;
		int atOnce = Arcs.AT_ONCE;
		boolean enabled = Arcs.ENABLED;
		Set<String> disabled = Arcs.DISABLED;
		VillageHalls.RADIUS = 8;
		Leftovers.after(helper, () -> {
			VillageHalls.RADIUS = radius;
			Arcs.COOLDOWN_DAYS = cooldown;
			Arcs.AT_ONCE = atOnce;
			Arcs.ENABLED = enabled;
			Arcs.DISABLED = disabled;
		});
	}

	/** A hall at {@code at} whose own rounds post no quests; its arcs end with the test. */
	private static VillageHallBlockEntity hall(GameTestHelper helper, BlockPos at) {
		ServerLevel level = helper.getLevel();
		BlockPos abs = helper.absolutePos(at);
		Leftovers.after(helper, () -> Arcs.stopAll(level, abs));
		helper.setBlock(at, ModBlocks.VILLAGE_HALL);
		VillageHallBlockEntity entity = (VillageHallBlockEntity) level.getBlockEntity(abs);
		entity.setLastQuestDay(Long.MAX_VALUE / 2);
		Arcs.stopAll(level, abs);
		return entity;
	}

	private static Arcs.Arc arc(GameTestHelper helper) {
		Arcs.Arc arc = Arcs.get(THREE).orElse(null);
		helper.assertTrue(arc != null, "the test pack's arc didn't load");
		return arc;
	}

	private static void setDay(ServerLevel level, long day) {
		level.setDayTime((day - 1) * DAY + 1000);
	}

	private static List<String> chronicle(VillageHallBlockEntity entity) {
		return entity.chronicle().stream().filter(e -> e.kind() == Chronicle.Kind.STORY).map(e -> e.text().getString().toLowerCase()).toList();
	}

	private static List<?> arcQuests(ServerLevel level, BlockPos hall) {
		return Stories.open(level, hall).stream().filter(q -> q.arc != null).toList();
	}

	private static List<Zombie> grubbs(ServerLevel level, BlockPos hall) {
		return level.getEntitiesOfClass(Zombie.class, new AABB(hall).inflate(24), z -> z.isAlive() && z.getTags().contains(ArcEffects.roleTag("grubb")));
	}

	private static CommandSourceStack op(ServerLevel level, BlockPos at, List<String> said) {
		CommandSource out = new CommandSource() {
			@Override
			public void sendSystemMessage(Component message) {
				said.add(message.getString());
			}

			@Override
			public boolean acceptsSuccess() {
				return true;
			}

			@Override
			public boolean acceptsFailure() {
				return true;
			}

			@Override
			public boolean shouldInformAdmins() {
				return false;
			}
		};
		return new CommandSourceStack(out, Vec3.atCenterOf(at.south(2)), Vec2.ZERO, level, 2, "test", Component.literal("test"), level.getServer(), null);
	}
}
