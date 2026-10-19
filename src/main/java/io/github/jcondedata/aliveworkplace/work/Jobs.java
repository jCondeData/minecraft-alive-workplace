package io.github.jcondedata.aliveworkplace.work;

import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.village.poi.PoiType;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.npc.VillagerProfession;
import net.minecraft.world.entity.schedule.Activity;
import net.minecraft.world.entity.schedule.Schedule;
import org.jetbrains.annotations.Nullable;

/** Small helpers shared by the jobs. */
public final class Jobs {
	/**
	 * Gives {@code villager} the job of the workstation at {@code station} right away (the block must be
	 * there). Normal play does this through vanilla job-site claiming; tests and admin tools use this.
	 */
	public static void employ(ServerLevel level, Villager villager, BlockPos station, ResourceKey<PoiType> poi, VillagerProfession profession) {
		level.getPoiManager().take(h -> h.is(poi), (h, p) -> p.equals(station), station, 1);
		villager.getBrain().setMemory(MemoryModuleType.JOB_SITE, GlobalPos.of(level.dimension(), station));
		JobSiteTickets.hold(level, villager);
		WorkerLimits.order(villager, profession);
		if (villager.getVillagerXp() == 0) {
			villager.setVillagerXp(1); // keeps the profession even if the workstation is briefly missing
		}
		villager.refreshBrain(level);
	}

	/**
	 * What a villager handed their job's item outside their work hours says (B220): when their work day begins. Null in
	 * their hours, when they start at once. Their hours are kept: a fresh hire at night sleeps first.
	 */
	@Nullable
	public static Component startsLater(Villager villager) {
		Schedule schedule = villager.getBrain().getSchedule();
		int now = (int) (villager.level().getDayTime() % 24000L);
		if (schedule.getActivityAt(now) == Activity.WORK) {
			return null;
		}
		for (int ticks = 10; ticks < 24000; ticks += 10) {
			if (schedule.getActivityAt((now + ticks) % 24000) == Activity.WORK) {
				int minutes = Math.round(ticks / 1200f);
				return Component.translatable(minutes < 1 ? "message.aliveworkplace.job.starts_soon"
					: minutes == 1 ? "message.aliveworkplace.job.starts_minute" : "message.aliveworkplace.job.starts_later",
					villager.getDisplayName(), minutes);
			}
		}
		return null; // a schedule with no work in it
	}

	private Jobs() {
	}
}
