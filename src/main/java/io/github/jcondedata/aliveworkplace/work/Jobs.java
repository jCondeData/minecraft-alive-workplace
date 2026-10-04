package io.github.jcondedata.aliveworkplace.work;

import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.village.poi.PoiType;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.npc.VillagerProfession;

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

	private Jobs() {
	}
}
