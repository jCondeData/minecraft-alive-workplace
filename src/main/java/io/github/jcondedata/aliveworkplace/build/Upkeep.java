package io.github.jcondedata.aliveworkplace.build;

import com.google.common.collect.ImmutableMap;
import io.github.jcondedata.aliveworkplace.blueprint.Blueprint;
import io.github.jcondedata.aliveworkplace.blueprint.BlueprintLibrary;
import io.github.jcondedata.aliveworkplace.blueprint.Blueprints;
import io.github.jcondedata.aliveworkplace.registry.ModAttachments;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.WeakHashMap;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.ai.behavior.Behavior;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.memory.MemoryStatus;
import net.minecraft.world.entity.npc.Villager;
import org.jetbrains.annotations.Nullable;

/**
 * Upkeep: an idle builder looks over the buildings they (or whoever they work for) finished nearby every so often, and
 * when blocks are missing — a creeper's hole, a raid, a window someone broke — sets about putting them back
 * ({@link BuildPlan#repair}: only the holes are filled, with the builder's usual materials; nothing players put there is
 * touched). {@code builderRepairs} in the config turns it off.
 */
public final class Upkeep {
	public static boolean ENABLED = true;
	/** How often an idle builder looks (ticks). */
	public static int EVERY = 1200;

	private static final Map<Villager, Long> NEXT = new WeakHashMap<>();

	/** The WORK behaviour that does the looking. */
	public static final class Look extends Behavior<Villager> {
		public Look() {
			super(ImmutableMap.of(MemoryModuleType.JOB_SITE, MemoryStatus.VALUE_PRESENT), 1);
		}

		@Override
		protected boolean checkExtraStartConditions(ServerLevel level, Villager villager) {
			if (!ENABLED || !Builders.isBuilder(villager) || villager.hasAttached(ModAttachments.BUILDER_JOB) || Builders.isHelping(villager)) {
				return false;
			}
			long now = level.getGameTime();
			Long next = NEXT.get(villager);
			if (next != null && now < next) {
				return false;
			}
			NEXT.put(villager, now + EVERY);
			return true;
		}

		@Override
		protected void start(ServerLevel level, Villager villager, long gameTime) {
			look(level, villager);
		}
	}

	/** Whose buildings {@code builder} looks after: their employer's (and friends'), or their own if nobody hired them. */
	static boolean looksAfter(ServerLevel level, Villager builder, UUID owner) {
		Employer boss = builder.getAttached(ModAttachments.BUILDER_EMPLOYER);
		if (boss == null) {
			return owner.equals(builder.getUUID());
		}
		return owner.equals(boss.id()) || Friends.get(level.getServer()).mayDirect(owner, boss.id());
	}

	/** Starts a repair of the first damaged building {@code builder} looks after near their bench; returns it (null if none). */
	@Nullable
	public static BuildSite look(ServerLevel level, Villager builder) {
		BlockPos bench = Builders.benchPos(builder).orElse(null);
		if (bench == null) {
			return null;
		}
		BuildSiteManager manager = BuildSiteManager.get(level);
		for (BuildSiteManager.Finished f : manager.finishedNear(level, bench, Builders.MAX_SITE_DISTANCE)) {
			if (!looksAfter(level, builder, f.owner()) || manager.all().stream().anyMatch(s -> s.placement().equals(f.placement()))) {
				continue;
			}
			Optional<Blueprint> blueprint = BlueprintLibrary.get(level, f.structure());
			if (blueprint.isEmpty()) {
				continue;
			}
			int missing = BuildPlan.repair(blueprint.get(), f.placement(), level).size();
			if (missing == 0) {
				continue;
			}
			ServerPlayer owner = level.getServer().getPlayerList().getPlayer(f.owner());
			BuildSite site = manager.create(f.owner(), owner != null ? owner.getGameProfile().getName() : "", f.structure(), f.placement());
			site.setRepair();
			site.setBuilder(builder.getUUID());
			site.setBench(bench);
			builder.setAttached(ModAttachments.BUILDER_JOB, new BuilderJob(site.id()));
			if (owner != null) {
				Builders.tell(owner, Component.translatable("message.aliveworkplace.repairing", builder.getDisplayName(), Blueprints.displayName(f.structure()),
					missing), ChatFormatting.AQUA);
			}
			return site;
		}
		return null;
	}

	private Upkeep() {
	}
}
