package io.github.jcondedata.aliveworkplace.wonder;

import io.github.jcondedata.aliveworkplace.AliveWorkplace;
import io.github.jcondedata.aliveworkplace.blueprint.BlueprintData;
import io.github.jcondedata.aliveworkplace.blueprint.BlueprintLibrary;
import io.github.jcondedata.aliveworkplace.blueprint.BlueprintOutline;
import io.github.jcondedata.aliveworkplace.blueprint.Blueprints;
import io.github.jcondedata.aliveworkplace.build.BuildSite;
import io.github.jcondedata.aliveworkplace.build.BuildSiteManager;
import io.github.jcondedata.aliveworkplace.hall.VillageHallBlockEntity;
import io.github.jcondedata.aliveworkplace.hall.VillageHalls;
import io.github.jcondedata.aliveworkplace.hall.VillageProtection;
import io.github.jcondedata.aliveworkplace.mc.Ids;
import io.github.jcondedata.aliveworkplace.realm.RealmData;
import io.github.jcondedata.aliveworkplace.realm.RealmTreasury;
import io.github.jcondedata.aliveworkplace.realm.Realms;
import io.github.jcondedata.aliveworkplace.work.Money;
import java.util.List;
import java.util.Optional;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Vec3i;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import org.jetbrains.annotations.Nullable;

/**
 * Drawing up a Wonder's plans and where its plan may go down (35.3). Drawing up costs {@link #COST} emeralds, paid by
 * the realm's treasury when the village is in a realm with realms on, else by the hall's treasury, else by the player;
 * only the hall's owner, their friends and operators may, and only with the caps clear and every requirement met. A
 * placed plan's centre must be within the hall's reach ({@link VillageHalls#RADIUS}), all of it within {@link #REACH}
 * blocks of the hall, and it may not cover a finished building or another Wonder.
 */
public final class WonderPlans {
	/** Emeralds the plans cost. */
	public static final int COST = 32;
	/** Blocks from the hall every corner of a Wonder must be within. */
	public static final int REACH = 96;

	/** Who paid for the plans. */
	public enum Payer { REALM, HALL, PLAYER, FREE }

	/** What came of drawing up the plans: the refusal (null: drawn), the message, the plan given and who paid. */
	public record Drawn(@Nullable Component refusal, Component message, ItemStack plan, @Nullable Payer payer) {
		public boolean drawn() {
			return refusal == null;
		}
	}

	/**
	 * Why {@code player} may not draw up the plans of {@code id} for the village at {@code hall} (not the money: that's
	 * {@link #draw}'s); empty when they may.
	 */
	public static Optional<Component> refusal(ServerLevel level, BlockPos hall, ServerPlayer player, ResourceLocation id) {
		Optional<Component> start = Wonders.startRefusal(level, hall, id);
		if (start.isPresent()) {
			return start;
		}
		VillageHallBlockEntity entity = (VillageHallBlockEntity) level.getBlockEntity(hall); // startRefusal checked it
		if (entity.owner() == null) {
			return Optional.of(Component.translatable("message.aliveworkplace.wonder_plan.no_owner", VillageHalls.name(level, hall)));
		}
		if (!VillageProtection.mayRule(level, entity, player)) {
			return Optional.of(Component.translatable("message.aliveworkplace.wonder_plan.not_yours", VillageHalls.name(level, hall), entity.ownerName()));
		}
		Wonder wonder = Wonders.loaded().get(id);
		List<ResourceLocation> missing = WonderBlueprints.missingParts(level.getServer(), wonder);
		if (!missing.isEmpty()) {
			AliveWorkplace.LOG.warn("Wonder {}: its plans can't be drawn up, parts missing: {}", id, missing);
			return Optional.of(Component.translatable("message.aliveworkplace.wonder_plan.missing_part", wonder.nameText(), missing.get(0).toString()));
		}
		return Optional.empty();
	}

	/** {@code player} draws up the plans of {@code id} for the village at {@code hall}: paid, the plan in their hands. */
	public static Drawn draw(ServerLevel level, BlockPos hall, ServerPlayer player, ResourceLocation id) {
		Optional<Component> refused = refusal(level, hall, player, id);
		if (refused.isPresent()) {
			return new Drawn(refused.get(), refused.get().copy().withStyle(ChatFormatting.RED), ItemStack.EMPTY, null);
		}
		VillageHallBlockEntity entity = (VillageHallBlockEntity) level.getBlockEntity(hall);
		Wonder wonder = Wonders.loaded().get(id);
		Component village = VillageHalls.name(level, hall);
		long cents = COST * 100L;
		Payer payer = null;
		RealmData.Realm realm = Realms.ENABLED ? Realms.realmOf(level, hall) : null;
		if (realm != null && RealmTreasury.spend(level.getServer(), realm,
			cents, Component.translatable("realm.aliveworkplace.movement.wonder_plans", wonder.nameText(), village))) {
			payer = Payer.REALM;
		} else if (entity.treasury() >= cents) {
			entity.setTreasury((int) (entity.treasury() - cents));
			payer = Payer.HALL;
		} else if (player.getAbilities().instabuild) {
			payer = Payer.FREE;
		} else if (Money.canAfford(player, (long) COST * Money.DOLLARS_PER_EMERALD, COST)
			&& Money.charge(player, (long) COST * Money.DOLLARS_PER_EMERALD, COST)) {
			payer = Payer.PLAYER;
		}
		if (payer == null) {
			Component why = Component.translatable("message.aliveworkplace.wonder_plan.money", money(COST), money(entity.treasury() / 100));
			return new Drawn(why, why.copy().withStyle(ChatFormatting.RED), ItemStack.EMPTY, null);
		}
		ItemStack plan = WonderPlanItem.of(level, hall, wonder, village);
		ItemStack given = plan.copy();
		if (!player.getInventory().add(given)) {
			player.drop(given, false);
		}
		level.playSound(null, player.blockPosition(), SoundEvents.UI_CARTOGRAPHY_TABLE_TAKE_RESULT, SoundSource.PLAYERS, 1f, 0.8f);
		Component message = Component.translatable("message.aliveworkplace.wonder_plan.drawn." + payer.name().toLowerCase(java.util.Locale.ROOT),
			wonder.nameText(), village, money(COST));
		return new Drawn(null, message.copy().withStyle(ChatFormatting.GREEN), plan, payer);
	}

	/** "32 emeralds", or their worth in CobbleDollars with the pack. */
	static Component money(int emeralds) {
		return Money.describe((long) emeralds * Money.DOLLARS_PER_EMERALD, emeralds);
	}

	/**
	 * Why {@code plan}'s Wonder, {@code size} big, may not go down at {@code placement}; empty when it may: its centre
	 * within the hall's reach, every corner within {@link #REACH} blocks of the hall, over no finished building and no
	 * other Wonder.
	 */
	public static Optional<Component> placeRefusal(ServerLevel level, WonderPlanItem.Plan plan, BlueprintData.Placement placement, Vec3i size) {
		Component village = plan.village();
		if (!plan.hall().dimension().equals(level.dimension()) || !placement.dimension().equals(Ids.of(level.dimension()))) {
			return Optional.of(Component.translatable("message.aliveworkplace.wonder_plan.elsewhere", village));
		}
		BlockPos hall = plan.hall().pos();
		if (!(level.getBlockEntity(hall) instanceof VillageHallBlockEntity)) {
			return Optional.of(Component.translatable("message.aliveworkplace.wonder_plan.no_hall", village));
		}
		BoundingBox box = BlueprintOutline.bounds(placement, size);
		double cx = (box.minX() + box.maxX() + 1) / 2.0 - (hall.getX() + 0.5);
		double cz = (box.minZ() + box.maxZ() + 1) / 2.0 - (hall.getZ() + 0.5);
		int centre = (int) Math.round(Math.sqrt(cx * cx + cz * cz));
		if (centre > VillageHalls.RADIUS) {
			return Optional.of(Component.translatable("message.aliveworkplace.wonder_plan.outside", village, VillageHalls.RADIUS, centre));
		}
		int far = 0;
		for (int x : new int[] {box.minX(), box.maxX()}) {
			for (int z : new int[] {box.minZ(), box.maxZ()}) {
				double dx = x - hall.getX();
				double dz = z - hall.getZ();
				far = Math.max(far, (int) Math.ceil(Math.sqrt(dx * dx + dz * dz)));
			}
		}
		if (far > REACH) {
			return Optional.of(Component.translatable("message.aliveworkplace.wonder_plan.too_far", REACH, village, far));
		}
		BuildSiteManager sites = BuildSiteManager.get(level);
		for (BuildSiteManager.Finished f : sites.finishedNear(level, hall, REACH + 2 * VillageHalls.RADIUS)) {
			Optional<BoundingBox> other = BlueprintLibrary.get(level, f.structure()).map(b -> BlueprintOutline.bounds(f.placement(), b.size()));
			if (other.isPresent() && overlaps(box, other.get())) {
				return Optional.of(isWonder(f.structure()) ? Component.translatable("message.aliveworkplace.wonder_plan.over_wonder")
					: Component.translatable("message.aliveworkplace.wonder_plan.over_building", Blueprints.displayName(f.structure())));
			}
		}
		for (BuildSite site : sites.all()) {
			if (isWonder(site.structure()) && site.placement().dimension().equals(placement.dimension())) {
				Optional<BoundingBox> other = BlueprintLibrary.get(level, site.structure()).map(b -> BlueprintOutline.bounds(site.placement(), b.size()));
				if (other.isPresent() && overlaps(box, other.get())) {
					return Optional.of(Component.translatable("message.aliveworkplace.wonder_plan.over_wonder"));
				}
			}
		}
		// A finished Wonder's parts aren't among the village's buildings (B143): its own record says where it stands
		for (WonderSite done : WonderManager.get(level).sites()) {
			if (!done.done() || done.placement().isEmpty() || !done.placement().get().dimension().equals(placement.dimension())) {
				continue;
			}
			for (WonderUpkeep.Part part : WonderUpkeep.parts(level, done)) {
				Optional<BoundingBox> other = BlueprintLibrary.get(level, part.plain()).map(b -> BlueprintOutline.bounds(part.placement(), b.size()));
				if (other.isPresent() && overlaps(box, other.get())) {
					return Optional.of(Component.translatable("message.aliveworkplace.wonder_plan.over_wonder"));
				}
			}
		}
		return Optional.empty();
	}

	/** Whether a structure is a Wonder's part or whole blueprint. */
	static boolean isWonder(ResourceLocation structure) {
		return structure.getPath().startsWith(WonderBlueprints.FOLDER);
	}

	/** Whether two footprints share a column (a Wonder isn't built over a building, whatever their heights). */
	static boolean overlaps(BoundingBox a, BoundingBox b) {
		return a.minX() <= b.maxX() && a.maxX() >= b.minX() && a.minZ() <= b.maxZ() && a.maxZ() >= b.minZ();
	}

	private WonderPlans() {
	}
}
