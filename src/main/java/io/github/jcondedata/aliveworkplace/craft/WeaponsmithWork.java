package io.github.jcondedata.aliveworkplace.craft;

import io.github.jcondedata.aliveworkplace.build.Builders;
import io.github.jcondedata.aliveworkplace.build.SupplyContainers;
import io.github.jcondedata.aliveworkplace.guard.Guards;
import io.github.jcondedata.aliveworkplace.mend.MendingWork;
import io.github.jcondedata.aliveworkplace.registry.ModAttachments;
import io.github.jcondedata.aliveworkplace.registry.ModVillagers;
import io.github.jcondedata.aliveworkplace.work.Village;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.npc.VillagerProfession;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.AABB;
import org.jetbrains.annotations.Nullable;

/**
 * A Weaponsmith making swords: a guard of the village with no weapon in hand and none in their chests gets one — iron
 * from the storehouse or the smelters, else stone — made at the grindstone and brought to the chests by their Guard Post.
 * Mending worn gear is {@link MendingWork}'s part of the same shift.
 */
public class WeaponsmithWork extends CrafterWork {
	static final List<Item> SWORDS = List.of(Items.IRON_SWORD, Items.STONE_SWORD);

	public WeaponsmithWork() {
		super(Crafting.Kind.CRAFTING, "weaponsmith_crafting", "weaponsmith_crafting", false);
	}

	/** Swords wait while a piece is being mended (the two share the bag). */
	@Override
	protected boolean checkExtraStartConditions(ServerLevel level, Villager villager) {
		return super.checkExtraStartConditions(level, villager) && (isBusy(villager) || !MendingWork.isBusy(villager));
	}

	@Nullable
	@Override
	protected Job choose(ServerLevel level, Villager villager, BlockPos station) {
		if (Village.RADIUS <= 0 || MendingWork.isBusy(villager)) {
			return null;
		}
		List<BlockPos> sources = new ArrayList<>(SupplyContainers.find(level, station, null));
		for (Village.Stash stash : Village.stashes(level, villager, station, null)) {
			VillagerProfession job = stash.job();
			if (job == ModVillagers.PORTER || job == VillagerProfession.ARMORER) {
				sources.addAll(stash.chests());
			}
		}
		Map<Item, Long> stock = null;
		long now = level.getGameTime();
		var boss = villager.getAttached(ModAttachments.BUILDER_EMPLOYER);
		for (Villager guard : level.getEntitiesOfClass(Villager.class, new AABB(station).inflate(Village.RADIUS),
				v -> v.isAlive() && Guards.isGuard(v) && Village.sameSide(level, v.getAttached(ModAttachments.BUILDER_EMPLOYER), boss))) {
			BlockPos post = Builders.benchPos(guard).orElse(null);
			if (post == null || Guards.isWeapon(guard.getItemBySlot(EquipmentSlot.MAINHAND))) {
				continue;
			}
			List<BlockPos> theirs = SupplyContainers.find(level, post, null);
			String claim = guard.getUUID() + "|sword";
			if (theirs.isEmpty() || claimed(claim, now) || SupplyContainers.firstMatching(level, theirs, Guards::isWeapon) != null) {
				continue;
			}
			if (stock == null) {
				stock = SupplyContainers.contents(level, sources);
			}
			for (Item sword : SWORDS) {
				Crafting.Plan plan = Crafting.plan(level, kind, sword, 1, stock);
				if (fits(plan)) {
					claim(claim, now);
					return new Job(post, null, plan, claim, sources);
				}
			}
		}
		return null;
	}
}
