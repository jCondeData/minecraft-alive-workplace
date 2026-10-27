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
 * when blocks are missing — a creeper's hole, a raid, a window someone broke, what a village fire burned (32.16, once
 * it's out) — sets about putting them back ({@link BuildPlan#repair}: only the holes are filled, with the builder's
 * usual materials; nothing players put there is touched). {@code builderRepairs} in the config turns it off, except
 * for what threats took (ROADMAP 32.16a: a gate a ram broke, a block a village fire burned; {@code ThreatData#damaged}):
 * those buildings are always repaired, so no threat ever destroys the builders' work for good. A damaged Wonder (35.8,
 * {@code WonderUpkeep}) comes first and is always repaired too: its powers wait on it.
 *
 * <p>36.15: with {@code repairAsk} on ({@link #ASK}, the default) the builder asks first: a damaged building nothing
 * threatened is put on the list with "Repair? Yes / Not now" for its owner ({@link RepairAsks}), and only one they said
 * yes to is repaired. What threats took and a Wonder's parts are still repaired without asking.
 */
public final class Upkeep {
	public static boolean ENABLED = true;
	/** Whether builders put back what threats took even with {@link #ENABLED} off (off in GameTests, as {@link #ENABLED} is). */
	public static boolean THREATS = true;
	/** Whether builders ask before repairing a building nothing threatened (36.15, {@code repairAsk}); off: they repair by themselves. */
	public static boolean ASK = true;
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
			if (!ENABLED && !(THREATS && !io.github.jcondedata.aliveworkplace.threat.ThreatData.get(level).damaged().isEmpty())
				&& !io.github.jcondedata.aliveworkplace.wonder.WonderUpkeep.anyDamaged(level) // a damaged Wonder is always repaired (35.8)
				&& !BuildSiteManager.get(level).anyRefreshAsked() // a refresh the owner asked for (36.21), whatever builderRepairs says
				|| !Builders.isBuilder(villager) || ModAttachments.BUILDER_JOB.has(villager) || Builders.isHelping(villager)) {
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
		Employer boss = ModAttachments.BUILDER_EMPLOYER.get(builder);
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
		// 35.8: the village's damaged Wonder before anything else, a repair site per part with holes (whatever builderRepairs says:
		// its powers wait on it)
		java.util.List<BuildSite> wonder = io.github.jcondedata.aliveworkplace.wonder.WonderUpkeep.repair(level, builder, bench);
		if (!wonder.isEmpty()) {
			return wonder.get(0);
		}
		// 36.21: a refresh to the latest look its owner asked for, waiting for a free builder
		BuildSite refresh = Refreshes.takeUp(level, builder, bench);
		if (refresh != null) {
			return refresh;
		}
		if (!ENABLED && !THREATS) {
			return null;
		}
		BuildSiteManager manager = BuildSiteManager.get(level);
		java.util.List<BlockPos> taken = threatDamage(level);
		// 36.20: every building of the village the bench stands in, and outside it those within the builder's reach
		java.util.List<BuildSiteManager.Finished> finished = new java.util.ArrayList<>(manager.finishedIn(level).stream()
			.filter(f -> Builders.inReach(level, bench, f.structure(), f.placement())).toList()); // B615: placing's own rule
		if (io.github.jcondedata.aliveworkplace.threat.SiegeReport.defencesFirst(level, bench)) {
			// In a siege and for two days after it (32.6): the walls, gates and towers before anything else (the sort keeps the rest in order).
			finished.sort(java.util.Comparator.comparingInt(f -> io.github.jcondedata.aliveworkplace.threat.Sieges.isDefence(f.structure()) ? 0 : 1));
		}
		for (BuildSiteManager.Finished f : finished) {
			if (!looksAfter(level, builder, f.owner()) || manager.all().stream().anyMatch(s -> s.placement().equals(f.placement()))) {
				continue;
			}
			if (io.github.jcondedata.aliveworkplace.threat.VillageFire.nearestFire(level, f.placement().origin(), 16).isPresent()) {
				continue; // still burning (32.16): what burned goes back once the fire is out
			}
			io.github.jcondedata.aliveworkplace.furnish.FurnitureChoice furniture = manager.furniture(f.placement());
			// 37.3: what it was built with; a piece whose mod is gone was dropped, so its vanilla piece goes back
			Optional<Blueprint> blueprint = BlueprintLibrary.get(level, f.structure()).map(b -> furniture.apply(b, f.structure()));
			if (blueprint.isEmpty()) {
				continue;
			}
			net.minecraft.world.level.levelgen.structure.BoundingBox box = io.github.jcondedata.aliveworkplace.blueprint.BlueprintOutline.bounds(f.placement(),
				blueprint.get().size());
			boolean threatened = taken.stream().anyMatch(box::isInside);
			if (!ENABLED && !threatened) {
				continue; // builderRepairs off: only what threats took (32.16a).
			}
			// (what an earlier repair couldn't put back stays out: it would only be fetched, skipped and taken back again)
			int missing = BuildPlan.repair(blueprint.get(), f.placement(), level, manager.gaveUp(f.placement())).size();
			if (missing == 0) {
				manager.setRepairAsk(f.placement(), null); // (put right some other way: off the Repairs page)
				continue;
			}
			if (ASK && !threatened && !RepairAsks.mayStart(level, manager, f, missing, builder)) {
				continue; // 36.15: asked, or waiting for an answer
			}
			if (threatened) {
				manager.setRepairAsk(f.placement(), null); // what threats took is repaired without asking: a question from before is moot
			}
			ServerPlayer owner = level.getServer().getPlayerList().getPlayer(f.owner());
			BuildSite site = manager.create(f.owner(), owner != null ? owner.getGameProfile().getName() : "", f.structure(), f.placement());
			site.setRepair();
			site.setFurniture(furniture);
			site.setBuilder(builder.getUUID());
			site.setBench(bench);
			ModAttachments.BUILDER_JOB.set(builder, new BuilderJob(site.id()));
			if (owner != null) {
				Builders.tell(owner, io.github.jcondedata.aliveworkplace.work.Words.counted("message.aliveworkplace.repairing", missing, builder.getDisplayName(), Blueprints.displayName(f.structure()),
					missing), ChatFormatting.AQUA);
			}
			return site;
		}
		return null;
	}

	/**
	 * The builders' blocks threats took that are still missing (32.16a); those back in place, or no longer in any
	 * finished building, come off the saved list.
	 */
	static java.util.List<BlockPos> threatDamage(ServerLevel level) {
		io.github.jcondedata.aliveworkplace.threat.ThreatData data = io.github.jcondedata.aliveworkplace.threat.ThreatData.get(level);
		java.util.List<BlockPos> out = new java.util.ArrayList<>();
		for (BlockPos pos : data.damaged()) {
			if (!level.isLoaded(pos)) {
				out.add(pos); // (can't tell yet)
				continue;
			}
			boolean inBuild = io.github.jcondedata.aliveworkplace.threat.VillageFire.buildAt(level, pos).isPresent();
			if (!inBuild || !level.getBlockState(pos).canBeReplaced()) {
				data.repaired(pos);
			} else {
				out.add(pos);
			}
		}
		return out;
	}

	private Upkeep() {
	}
}
