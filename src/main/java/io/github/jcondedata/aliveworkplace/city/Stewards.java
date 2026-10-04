package io.github.jcondedata.aliveworkplace.city;

import io.github.jcondedata.aliveworkplace.build.BuilderLevels;
import io.github.jcondedata.aliveworkplace.build.Friends;
import io.github.jcondedata.aliveworkplace.hall.VillageHallBlockEntity;
import io.github.jcondedata.aliveworkplace.hall.VillageHalls;
import io.github.jcondedata.aliveworkplace.hall.VillageLedgerItem;
import io.github.jcondedata.aliveworkplace.hall.VillageRanks;
import io.github.jcondedata.aliveworkplace.mc.Chat;
import io.github.jcondedata.aliveworkplace.registry.ModAttachments;
import io.github.jcondedata.aliveworkplace.registry.ModBlocks;
import io.github.jcondedata.aliveworkplace.registry.ModComponents;
import io.github.jcondedata.aliveworkplace.registry.ModVillagers;
import io.github.jcondedata.aliveworkplace.work.Hiring;
import io.github.jcondedata.aliveworkplace.work.Stations;
import java.util.Optional;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.Holder;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.village.poi.PoiManager;
import net.minecraft.world.entity.ai.village.poi.PoiType;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.npc.VillagerProfession;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.AABB;
import org.jetbrains.annotations.Nullable;

/**
 * The Steward (ROADMAP 27.5): one per Village Hall. A player appoints him by sneak-right-clicking a grown villager
 * standing by the hall with that hall's City Plan; the hall isn't an acquirable job site, so nobody takes it by
 * himself. His day is {@link StewardWork}. His level sets how many of his builds may be open at once
 * ({@link #maxOpenBuilds}). Breaking the hall ends the job. Config {@code steward}.
 */
public final class Stewards {
	/** Config switch {@code steward}: off, no new Stewards, and those appointed stand idle. */
	public static boolean ENABLED = true;
	/** Config {@code stewardMaxOpenBuilds}: never more of his builds open at once than this. */
	public static int MAX_OPEN_BUILDS = 4;
	/** How far from the hall the villager may stand to be appointed (as far as a job item reaches: {@link Stations#REACH}). */
	public static final double REACH = Stations.REACH;
	/** Open builds by level, Novice to Master. */
	private static final int[] OPEN_BY_LEVEL = {1, 2, 2, 3, 4};
	/** XP for each of his builds finished, and for each job he gives (27.6-27.9 call {@link #credit}). */
	public static final int XP_BUILD = 10;
	public static final int XP_JOB = 3;

	public static boolean isSteward(Villager villager) {
		return !villager.isBaby() && villager.getVillagerData().getProfession() == ModVillagers.STEWARD;
	}

	/** How many of his builds a Steward may have open at once: by level (1, 2, 2, 3, 4), the rank's cap and the config. */
	public static int maxOpenBuilds(int villagerLevel, VillageRanks.Rank rank) {
		int byLevel = OPEN_BY_LEVEL[Math.max(1, Math.min(5, villagerLevel)) - 1];
		int byRank = rank.ordinal() + 1; // Hamlet 1, Village 2, Town 3, City 4
		return Math.max(0, Math.min(Math.min(byLevel, byRank), MAX_OPEN_BUILDS));
	}

	/** {@link #maxOpenBuilds(int, VillageRanks.Rank)} for {@code steward} at his hall. */
	public static int maxOpenBuilds(ServerLevel level, Villager steward) {
		return hallOf(level, steward).map(hall -> maxOpenBuilds(steward.getVillagerData().getLevel(), VillageRanks.of(level, hall))).orElse(0);
	}

	/** XP for a build of his finished ({@link #XP_BUILD}) or a job he gave ({@link #XP_JOB}): the planner (27.6+) calls this. */
	public static void credit(ServerLevel level, Villager steward, int xp) {
		if (isSteward(steward)) {
			BuilderLevels.addXp(level, steward, xp, null);
		}
	}

	/** The hall {@code villager} is Steward of, if it's still there and loaded. */
	public static Optional<BlockPos> hallOf(ServerLevel level, Villager villager) {
		return villager.getBrain().getMemory(MemoryModuleType.JOB_SITE)
			.filter(g -> g.dimension().equals(level.dimension()))
			.map(GlobalPos::pos)
			.filter(pos -> level.isLoaded(pos) && level.getBlockState(pos).is(ModBlocks.VILLAGE_HALL));
	}

	/** The loaded Steward of the hall at {@code hall}, if it has one. */
	@Nullable
	public static Villager stewardOf(ServerLevel level, BlockPos hall) {
		GlobalPos at = GlobalPos.of(level.dimension(), hall);
		return level.getEntities(EntityType.VILLAGER, new AABB(hall).inflate(VillageHalls.RADIUS * 2),
			v -> isSteward(v) && v.getBrain().getMemory(MemoryModuleType.JOB_SITE).filter(at::equals).isPresent()).stream().findFirst().orElse(null);
	}

	/**
	 * Sneak-right-click with a City Plan: makes {@code villager} the Steward of the plan's hall, if he stands by it.
	 * {@link InteractionResult#PASS} when the stack isn't a City Plan.
	 */
	public static InteractionResult appoint(ServerPlayer player, Villager villager, ItemStack stack) {
		if (!stack.is(io.github.jcondedata.aliveworkplace.registry.ModItems.CITY_PLAN)) {
			return InteractionResult.PASS;
		}
		ServerLevel level = (ServerLevel) villager.level();
		if (!ENABLED) {
			return refuse(player, "message.aliveworkplace.steward.off");
		}
		if (villager.isBaby() || villager.getVillagerData().getProfession() == VillagerProfession.NITWIT) {
			return refuse(player, "message.aliveworkplace.steward.cannot");
		}
		VillageLedgerItem.Ledger bound = stack.get(ModComponents.CITY_PLAN_HALL);
		if (bound == null) {
			return refuse(player, "message.aliveworkplace.city_plan.unbound");
		}
		BlockPos hall = bound.hall().pos();
		boolean here = bound.hall().dimension().equals(level.dimension()) && level.isLoaded(hall) && level.getBlockState(hall).is(ModBlocks.VILLAGE_HALL)
			&& hall.distToCenterSqr(villager.position()) <= (REACH + 1) * (REACH + 1);
		if (!here) {
			return refuse(player, Component.translatable("message.aliveworkplace.steward.not_by_hall", bound.name()));
		}
		if (isSteward(villager) && villager.getBrain().getMemory(MemoryModuleType.JOB_SITE).filter(GlobalPos.of(level.dimension(), hall)::equals).isPresent()) {
			return refuse(player, Component.translatable("message.aliveworkplace.steward.already", villager.getDisplayName(), bound.name()));
		}
		if (!Friends.mayCommand(player, villager)) {
			return Hiring.hire(player, villager, Component.empty()); // refuses, and says whose they are
		}
		fixTicket(level, hall);
		if (level.getPoiManager().getFreeTickets(hall) <= 0) {
			Villager other = stewardOf(level, hall);
			return refuse(player, Component.translatable("message.aliveworkplace.steward.taken", bound.name(),
				other != null ? other.getDisplayName() : Component.translatable("message.aliveworkplace.steward.someone")));
		}
		Component who = villager.getDisplayName();
		Stations.assign(level, villager, hall, ModVillagers.STEWARD);
		ModAttachments.STEWARD_ROUND_DAY.remove(villager);
		level.playSound(null, villager, SoundEvents.VILLAGER_YES, SoundSource.NEUTRAL, 1f, 1f);
		Chat.chat(player, Component.translatable("message.aliveworkplace.steward.appointed", who, VillageHalls.name(level, hall))
			.withStyle(ChatFormatting.GREEN));
		return InteractionResult.SUCCESS;
	}

	private static InteractionResult refuse(ServerPlayer player, String key) {
		return refuse(player, Component.translatable(key));
	}

	private static InteractionResult refuse(ServerPlayer player, Component message) {
		Chat.actionBar(player, message.copy().withStyle(ChatFormatting.YELLOW));
		return InteractionResult.CONSUME;
	}

	/**
	 * A hall's record saved when the hall had no place for a Steward still has 0 free places: registers it again (with
	 * the one place it has now), unless a loaded Steward holds it. Called once for each hall when it first ticks after
	 * loading, and before an appointment.
	 */
	public static void fixTicket(ServerLevel level, BlockPos hall) {
		PoiManager poi = level.getPoiManager();
		Optional<Holder<PoiType>> type = poi.getType(hall);
		if (type.isEmpty() || !type.get().is(ModVillagers.VILLAGE_HALL_POI) || poi.getFreeTickets(hall) > 0 || stewardOf(level, hall) != null) {
			return;
		}
		poi.remove(hall);
		poi.add(hall, type.get());
	}

	/** The hall at {@code hall} was broken: its loaded Steward loses the job. */
	public static void hallGone(ServerLevel level, BlockPos hall) {
		GlobalPos at = GlobalPos.of(level.dimension(), hall);
		for (Villager v : level.getEntities(EntityType.VILLAGER, new AABB(hall).inflate(VillageHalls.RADIUS * 2),
				v -> isSteward(v) && v.getBrain().getMemory(MemoryModuleType.JOB_SITE).filter(at::equals).isPresent())) {
			dismiss(level, v);
		}
	}

	/**
	 * Every few seconds (mixin/VillagerMixin): a Steward whose hall is gone (broken while he was away, in an unloaded
	 * chunk) loses the job, as vanilla workers keep theirs only while they have levels.
	 */
	public static void tick(Villager villager) {
		if (villager.tickCount % 100 != 53 || !isSteward(villager) || !(villager.level() instanceof ServerLevel level)) {
			return;
		}
		Optional<GlobalPos> site = villager.getBrain().getMemory(MemoryModuleType.JOB_SITE);
		if (site.isPresent() && (!site.get().dimension().equals(level.dimension()) || !level.isLoaded(site.get().pos()))) {
			return; // can't tell from here
		}
		if (site.isEmpty() || !level.getBlockState(site.get().pos()).is(ModBlocks.VILLAGE_HALL)) {
			dismiss(level, villager);
		}
	}

	/** Takes the Steward's job away: jobless again, his trades and levels gone, the plan out of his hands. */
	static void dismiss(ServerLevel level, Villager villager) {
		villager.getBrain().eraseMemory(MemoryModuleType.JOB_SITE);
		if (StewardWork.holdsPlan(villager)) {
			villager.setItemSlot(EquipmentSlot.MAINHAND, ItemStack.EMPTY);
		}
		villager.setVillagerData(villager.getVillagerData().setProfession(VillagerProfession.NONE).setLevel(1));
		villager.setVillagerXp(0);
		ModAttachments.STEWARD_ROUND_DAY.remove(villager);
		villager.refreshBrain(level);
	}

	private Stewards() {
	}
}
