package io.github.jcondedata.aliveworkplace.school;

import com.google.common.collect.ImmutableMap;
import io.github.jcondedata.aliveworkplace.build.BuilderLevels;
import io.github.jcondedata.aliveworkplace.build.Builders;
import io.github.jcondedata.aliveworkplace.registry.ModAttachments;
import io.github.jcondedata.aliveworkplace.work.Pace;
import io.github.jcondedata.aliveworkplace.work.Walker;
import io.github.jcondedata.aliveworkplace.work.WorkerStatus;
import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.ai.behavior.Behavior;
import net.minecraft.world.entity.ai.behavior.EntityTracker;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.memory.MemoryStatus;
import net.minecraft.world.entity.ai.memory.WalkTarget;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * A Teacher's shift at their Teacher's Desk: the children within {@link #RADIUS} blocks who haven't been to school yet
 * are called over, and every one within {@link #CLASS} blocks of the desk has lessons while the teacher stands by it
 * (pastured Psychic and Normal Pokémon make lessons go quicker). A child with enough lessons has been to school
 * ({@link Schools}).
 */
public class TeacherWork extends Behavior<Villager> {
	/** How far from the desk children are called in. */
	public static int RADIUS = 32;
	/** How close to the desk counts as being in class. */
	static final double CLASS = 5.0;
	/** How often the class is checked (and lessons counted). */
	static final int EVERY = 20;

	/** Lesson progress a round of class makes for each child: {@link #EVERY} at the teacher's pace (ROADMAP 30.2). */
	public static int lesson(Villager villager) {
		return Pace.progress(EVERY, villager);
	}

	private final Walker walker = new Walker(0.5f);
	private int timer;
	private int inClass;
	private int called;

	public TeacherWork() {
		super(ImmutableMap.of(
			MemoryModuleType.JOB_SITE, MemoryStatus.VALUE_PRESENT,
			MemoryModuleType.WALK_TARGET, MemoryStatus.REGISTERED,
			MemoryModuleType.LOOK_TARGET, MemoryStatus.REGISTERED
		), 1200);
	}

	@Override
	protected boolean checkExtraStartConditions(ServerLevel level, Villager villager) {
		return !villager.isSleeping() && Schools.isTeacher(villager) && Builders.benchPos(villager).isPresent();
	}

	@Override
	protected boolean canStillUse(ServerLevel level, Villager villager, long gameTime) {
		return checkExtraStartConditions(level, villager);
	}

	@Override
	protected void start(ServerLevel level, Villager villager, long gameTime) {
		walker.reset();
		timer = 0;
	}

	@Override
	protected void tick(ServerLevel level, Villager villager, long gameTime) {
		BlockPos desk = Builders.benchPos(villager).orElse(null);
		if (desk == null) {
			return;
		}
		walker.walkTo(level, villager, desk, 2.5);
		if (--timer > 0) {
			return;
		}
		timer = EVERY;
		Vec3 middle = Vec3.atCenterOf(desk);
		List<Villager> children = level.getEntitiesOfClass(Villager.class, new AABB(desk).inflate(RADIUS, 8, RADIUS),
			v -> v.isAlive() && v.isBaby() && !Schools.isSchooled(v));
		inClass = 0;
		called = 0;
		int lesson = lesson(villager);
		for (Villager child : children) {
			if (child.distanceToSqr(middle) > CLASS * CLASS) {
				// Come to class
				child.getBrain().setMemory(MemoryModuleType.WALK_TARGET, new WalkTarget(desk, 0.6f, 2));
				called++;
				continue;
			}
			inClass++;
			child.getBrain().setMemory(MemoryModuleType.LOOK_TARGET, new EntityTracker(villager, true));
			if (Schools.teach(level, child, lesson)) {
				ModAttachments.PUPILS_TAUGHT.set(villager, ModAttachments.PUPILS_TAUGHT.getOrElse(villager, 0) + 1);
				BuilderLevels.addXp(level, villager, 3, null);
			}
		}
		if (inClass > 0) {
			villager.swing(InteractionHand.MAIN_HAND);
			villager.getBrain().setMemory(MemoryModuleType.LOOK_TARGET, new EntityTracker(children.get(level.random.nextInt(children.size())), true));
			level.sendParticles(ParticleTypes.ENCHANT, desk.getX() + 0.5, desk.getY() + 1.4, desk.getZ() + 0.5, 6, 0.3, 0.2, 0.3, 0.3);
			// A Psychic or Normal partner floats a book by the desk during the lesson (ROADMAP 28.6).
			io.github.jcondedata.aliveworkplace.work.PartnerShows.cue(villager, "lesson", desk);
		}
		status(villager);
	}

	private void status(Villager villager) {
		Component title = Component.translatable("message.aliveworkplace.teacher.title", ModAttachments.PUPILS_TAUGHT.getOrElse(villager, 0));
		Component line = inClass > 0 ? Component.translatable("message.aliveworkplace.teacher.state.teaching", inClass)
			: called > 0 ? Component.translatable("message.aliveworkplace.teacher.state.calling", called)
			: Component.translatable("message.aliveworkplace.teacher.state.none");
		WorkerStatus.set(villager, title, -1f, line.copy().withStyle(ChatFormatting.GRAY));
	}

	@Override
	protected void stop(ServerLevel level, Villager villager, long gameTime) {
		villager.getBrain().eraseMemory(MemoryModuleType.WALK_TARGET);
	}
}
