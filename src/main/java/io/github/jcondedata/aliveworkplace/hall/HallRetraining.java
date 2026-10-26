package io.github.jcondedata.aliveworkplace.hall;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.github.jcondedata.aliveworkplace.build.Friends;
import io.github.jcondedata.aliveworkplace.mc.Chat;
import io.github.jcondedata.aliveworkplace.registry.ModAttachments;
import io.github.jcondedata.aliveworkplace.registry.ModBlocks;
import io.github.jcondedata.aliveworkplace.registry.ModVillagers;
import io.github.jcondedata.aliveworkplace.work.ChoiceMenu;
import io.github.jcondedata.aliveworkplace.work.Money;
import io.github.jcondedata.aliveworkplace.work.Retraining;
import io.github.jcondedata.aliveworkplace.work.Stations;
import io.github.jcondedata.aliveworkplace.work.Walker;
import io.github.jcondedata.aliveworkplace.work.Words;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.memory.WalkTarget;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.npc.VillagerProfession;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.jetbrains.annotations.Nullable;

/**
 * Changing a villager's job from the Village Hall (ROADMAP 36.18, the owner: "it would be easier if you can just do it at
 * the Village Hall"). Right-clicking a worker in the residents list opens their page, with a Change job button (a
 * plain click still only makes them glow); it lists the village's free job
 * blocks (the Free job blocks page's, 36.9) and the Builder. A first click on one shows the price (the Blueprint Table's
 * retraining price by level, {@link Retraining#price}), a second pays: the old job and its level go (its block stays,
 * free for someone else; a builder hands their builds back), and the new job and its block are theirs on the click, never
 * waiting on them getting there. They then walk over, and one who isn't there within {@link #ARRIVE_TICKS} (walled in,
 * too far, stuck) is moved beside the block. Children, nitwits, Legends and caravan carters can't be retrained; game rule
 * {@code workplaceRetraining} off hides the button.
 */
public final class HallRetraining {
	/** How long a retrained villager has to reach the new block before they're moved beside it (about a minute). */
	public static int ARRIVE_TICKS = 1000;
	/** How often (ticks) a villager on their way is looked at. */
	static final int CHECK_EVERY = 20;
	/** Within this many blocks of the block's middle, they're there. */
	static final double ARRIVED = 3.0;
	/** How fast they walk to the new block. */
	static final float WALK_SPEED = 0.6f;
	/** The Change job button on a worker's page. */
	public static final int CHANGE = VillageHallScreen.FIRST_ROW + 4;

	/** A retrained villager on their way: the new block, and the game time they were retrained. */
	public record Walk(BlockPos station, long since) {
		public static final Codec<Walk> CODEC = RecordCodecBuilder.create(i -> i.group(
			BlockPos.CODEC.fieldOf("station").forGetter(Walk::station),
			Codec.LONG.optionalFieldOf("since", 0L).forGetter(Walk::since)
		).apply(i, Walk::new));
	}

	/** How a paid click went. */
	public enum Outcome { DONE, GONE, NOT_YOURS, REFUSED, TAKEN, SHORT }

	/**
	 * Whether the hall offers to change {@code villager}'s job: the rule is on, and they're a grown villager with a job and
	 * its block (not a nitwit, a Legend or a caravan's carter).
	 */
	public static boolean canRetrain(ServerLevel level, Villager villager) {
		VillagerProfession job = villager.getVillagerData().getProfession();
		return Retraining.on(level) && villager.isAlive() && !villager.isBaby() && job != VillagerProfession.NONE && job != VillagerProfession.NITWIT
			&& villager.getBrain().getMemory(MemoryModuleType.JOB_SITE).isPresent()
			&& io.github.jcondedata.aliveworkplace.legend.Legends.of(villager).isEmpty() && !CaravanSights.isParty(villager);
	}

	/** The free job blocks {@code villager} could change to: every free block in the village of a job other than theirs. */
	public static List<VillageHalls.FreeStation> choices(ServerLevel level, BlockPos hall, Villager villager) {
		VillagerProfession now = villager.getVillagerData().getProfession();
		List<VillageHalls.FreeStation> out = new ArrayList<>();
		for (VillageHalls.FreeStation station : VillageHalls.freeStations(level, hall)) {
			if (station.profession() != now) {
				out.add(station);
			}
		}
		return out;
	}

	/** The price as the hall says it: "20 emeralds" (or CobbleDollars). */
	static Component price(Villager villager) {
		int emeralds = Retraining.price(villager);
		return Money.describe((long) emeralds * Money.DOLLARS_PER_EMERALD, emeralds);
	}

	/**
	 * The paid click: retrains {@code villager} as the job at {@code station} if they still can be, the block is still
	 * free and the player can pay. The job and the block are theirs at once; they then walk over. Anything else changes
	 * nothing, and the player is told why.
	 */
	public static Outcome retrain(ServerPlayer player, ServerLevel level, BlockPos hall, Villager villager, VillageHalls.FreeStation station) {
		Component who = villager.getDisplayName(); // before: an unnamed villager is called by their job
		if (!canRetrain(level, villager)) {
			return Outcome.GONE;
		}
		if (!Friends.mayCommand(player, villager)) {
			Chat.actionBar(player, Component.translatable("message.aliveworkplace.hall_retrain.not_yours", who).withStyle(ChatFormatting.YELLOW));
			return Outcome.NOT_YOURS;
		}
		Component refusal = io.github.jcondedata.aliveworkplace.people.ClassJobs.refusal(hall, villager, station.profession());
		if (refusal != null) { // a job above their class (34.8)
			Chat.actionBar(player, refusal.copy().withStyle(ChatFormatting.YELLOW));
			return Outcome.REFUSED;
		}
		// Still free (by where it is: a block several jobs share is offered for each of them, 40.3)
		if (VillageHalls.freeStations(level, hall).stream().noneMatch(s -> s.pos().equals(station.pos()))) {
			Chat.actionBar(player, Component.translatable("message.aliveworkplace.hall.not_assigned").withStyle(ChatFormatting.YELLOW));
			return Outcome.TAKEN;
		}
		int emeralds = Retraining.price(villager);
		long dollars = (long) emeralds * Money.DOLLARS_PER_EMERALD;
		Component price = Money.describe(dollars, emeralds);
		if (!Money.canAfford(player, dollars, emeralds) || !Money.charge(player, dollars, emeralds)) {
			Chat.chat(player, Component.translatable("message.aliveworkplace.retrain.short", who, price, Retraining.shortBy(player, dollars, emeralds))
				.withStyle(ChatFormatting.YELLOW));
			return Outcome.SHORT;
		}
		VillagerProfession old = villager.getVillagerData().getProfession();
		Retraining.retrain(level, player, villager, station.pos(), station.profession());
		// The job and the block are theirs now; they walk over, and are moved beside it if they can't get there (tick).
		ModAttachments.RETRAIN_WALK.set(villager, new Walk(station.pos().immutable(), level.getGameTime()));
		villager.getBrain().setMemory(MemoryModuleType.WALK_TARGET, new WalkTarget(station.pos(), WALK_SPEED, 1));
		level.sendParticles(ParticleTypes.HAPPY_VILLAGER, villager.getX(), villager.getY() + 1.2, villager.getZ(), 8, 0.4, 0.4, 0.4, 0);
		level.playSound(null, villager, SoundEvents.VILLAGER_YES, SoundSource.NEUTRAL, 1f, 1f);
		Chat.chat(player, Words.articled("message.aliveworkplace.retrain.done", 2, who, Stations.name(old), Stations.name(station.profession()),
			level.getBlockState(station.pos()).getBlock().getName(), price).withStyle(ChatFormatting.GREEN));
		return Outcome.DONE;
	}

	/**
	 * Every second, a villager retrained at the hall who is on their way: there (or no longer working at that block), the
	 * walk is over; still on the way, they're kept walking to it; past {@link #ARRIVE_TICKS}, they're moved beside it.
	 * Asleep, they're left in bed and go when they wake (called from mixin/VillagerMixin).
	 */
	public static void tick(Villager villager) {
		if (villager.tickCount % CHECK_EVERY != 7 || !(villager.level() instanceof ServerLevel level)) {
			return;
		}
		Walk walk = ModAttachments.RETRAIN_WALK.get(villager);
		if (walk == null) {
			return;
		}
		BlockPos station = walk.station();
		boolean stillTheirs = villager.getBrain().getMemory(MemoryModuleType.JOB_SITE)
			.filter(g -> g.equals(GlobalPos.of(level.dimension(), station))).isPresent();
		if (!stillTheirs || !level.isLoaded(station) || level.getPoiManager().getType(station).isEmpty()
			|| villager.position().distanceTo(station.getCenter()) <= ARRIVED) {
			ModAttachments.RETRAIN_WALK.remove(villager);
			return;
		}
		if (villager.isSleeping()) {
			return;
		}
		// Vanilla gives up a job site it can't reach for a minute: on the way here, it's this walk that decides.
		villager.getBrain().eraseMemory(MemoryModuleType.CANT_REACH_WALK_TARGET_SINCE);
		if (level.getGameTime() - walk.since() < ARRIVE_TICKS) {
			if (villager.getBrain().getMemory(MemoryModuleType.WALK_TARGET).isEmpty()) {
				villager.getBrain().setMemory(MemoryModuleType.WALK_TARGET, new WalkTarget(station, WALK_SPEED, 1));
			}
			return;
		}
		BlockPos spot = Walker.standingSpot(level, station, station, 2.5);
		if (spot == null) {
			spot = Walker.standingSpot(level, station, station, Stations.REACH);
		}
		if (spot == null) {
			return; // nowhere to stand by it yet (built in): tried again in a second
		}
		level.sendParticles(ParticleTypes.PORTAL, villager.getX(), villager.getY() + 1, villager.getZ(), 20, 0.3, 0.6, 0.3, 0.2);
		villager.stopRiding();
		villager.teleportTo(spot.getX() + 0.5, spot.getY(), spot.getZ() + 0.5);
		villager.getNavigation().stop();
		villager.getBrain().eraseMemory(MemoryModuleType.WALK_TARGET);
		level.sendParticles(ParticleTypes.HAPPY_VILLAGER, villager.getX(), villager.getY() + 1.2, villager.getZ(), 8, 0.4, 0.4, 0.4, 0);
		ModAttachments.RETRAIN_WALK.remove(villager);
	}

	// --- The pages -------------------------------------------------------------------------------------------------

	/** A worker's page: back to the list, the worker, Find, and the Change job button. */
	public static void renderWorker(ChoiceMenu menu, ServerLevel level, BlockPos hall, Villager villager, int page) {
		menu.clearButtons();
		menu.button(0, VillageHallScreen.icon(Items.ARROW, Component.translatable("screen.aliveworkplace.hall.back"), ChatFormatting.WHITE),
			p -> VillageHallScreen.showList(menu, level, hall, page));
		menu.button(4, VillageHallScreen.person(level, hall, villager, menu.viewer()), null);
		menu.button(VillageHallScreen.FIND, VillageHallScreen.icon(Items.SPYGLASS, Component.translatable("screen.aliveworkplace.hall.find"), ChatFormatting.WHITE),
			p -> VillageHallScreen.glow(level, villager, p));
		menu.divider(1);
		if (!canRetrain(level, villager)) {
			return; // the rule turned off, or they lost their job, while the page was open
		}
		int count = choices(level, hall, villager).size();
		menu.button(CHANGE, VillageHallScreen.icon(Items.EMERALD, Component.translatable("screen.aliveworkplace.hall_retrain.change"), ChatFormatting.GREEN,
			VillageHallScreen.line(Component.translatable("screen.aliveworkplace.hall_retrain.change_about"), ChatFormatting.GRAY),
			VillageHallScreen.line(Component.translatable("screen.aliveworkplace.hall_retrain.price", price(villager), Retraining.levelName(villager)), ChatFormatting.GOLD),
			VillageHallScreen.line("screen.aliveworkplace.hall_retrain.lost", ChatFormatting.GRAY),
			VillageHallScreen.line(io.github.jcondedata.aliveworkplace.work.Words.counted("screen.aliveworkplace.hall_retrain.free", count, count),
				count == 0 ? ChatFormatting.DARK_GRAY : ChatFormatting.WHITE),
			VillageHallScreen.line("screen.aliveworkplace.hall_retrain.change_click", ChatFormatting.DARK_GRAY)), p -> {
			renderChoices(menu, level, hall, villager, page, null);
			menu.broadcastChanges();
		});
	}

	/**
	 * The jobs a worker can change to: each free job block (its job, where it is, the price), then the Builder if no
	 * Blueprint Table is free. {@code picked}: the block clicked once, waiting for the click that pays.
	 */
	public static void renderChoices(ChoiceMenu menu, ServerLevel level, BlockPos hall, Villager villager, int page, @Nullable BlockPos picked) {
		menu.clearButtons();
		menu.button(0, VillageHallScreen.icon(Items.ARROW, Component.translatable("screen.aliveworkplace.hall.back"), ChatFormatting.WHITE), p -> {
			renderWorker(menu, level, hall, villager, page);
			menu.broadcastChanges();
		});
		menu.button(4, VillageHallScreen.person(level, hall, villager, menu.viewer()), null);
		menu.divider(1);
		if (!canRetrain(level, villager)) {
			return;
		}
		Component price = price(villager);
		Component who = villager.getDisplayName();
		List<VillageHalls.FreeStation> choices = choices(level, hall, villager);
		boolean builder = villager.getVillagerData().getProfession() == ModVillagers.BUILDER;
		int slot = VillageHallScreen.FIRST_ROW;
		int last = builder ? ChoiceMenu.SIZE : ChoiceMenu.SIZE - 1; // the last slot kept for the Builder
		for (VillageHalls.FreeStation station : choices) {
			if (slot >= last) {
				break;
			}
			builder |= station.profession() == ModVillagers.BUILDER;
			Component job = Stations.name(station.profession());
			Item item = level.getBlockState(station.pos()).getBlock().asItem();
			Component refusal = io.github.jcondedata.aliveworkplace.people.ClassJobs.refusal(hall, villager, station.profession());
			if (refusal != null) { // a job above their class (34.8): greyed, with the class it needs; a click says why
				io.github.jcondedata.aliveworkplace.people.SocialClasses.SocialClass need = io.github.jcondedata.aliveworkplace.people.ClassJobs.needed(station.profession());
				menu.button(slot++, VillageHallScreen.icon(item == Items.AIR ? Items.PAPER : item, job.copy(), ChatFormatting.DARK_GRAY,
					VillageHallScreen.line(VillageHallScreen.where(hall, station.pos()), ChatFormatting.GRAY),
					VillageHallScreen.line(Component.translatable("screen.aliveworkplace.hall.job_needs_class", need.name()), ChatFormatting.RED)), p -> {
					Chat.actionBar(p, refusal.copy().withStyle(ChatFormatting.YELLOW));
					level.playSound(null, p.blockPosition(), SoundEvents.VILLAGER_NO, SoundSource.NEUTRAL, 0.8f, 1f);
				});
				continue;
			}
			boolean chosen = station.pos().equals(picked);
			ItemStack icon = VillageHallScreen.icon(item == Items.AIR ? Items.PAPER : item, job.copy(), chosen ? ChatFormatting.GOLD : ChatFormatting.WHITE,
				VillageHallScreen.line(VillageHallScreen.where(hall, station.pos()), ChatFormatting.GRAY),
				VillageHallScreen.line(Component.translatable("screen.aliveworkplace.hall_retrain.price", price, Retraining.levelName(villager)), ChatFormatting.GOLD),
				chosen ? VillageHallScreen.line(Words.articled("screen.aliveworkplace.hall_retrain.pay", 2, price, who, job), ChatFormatting.GREEN)
					: VillageHallScreen.line(Words.articled("screen.aliveworkplace.hall_retrain.pick", 1, who, job), ChatFormatting.GREEN));
			if (chosen) {
				icon.set(net.minecraft.core.component.DataComponents.ENCHANTMENT_GLINT_OVERRIDE, true);
			}
			menu.button(slot++, icon, p -> pick(menu, level, hall, villager, page, station, chosen, p));
		}
		if (!builder) {
			// The Builder works at a Blueprint Table: with none free, the way to one.
			menu.button(slot, VillageHallScreen.icon(ModBlocks.BLUEPRINT_TABLE.asItem(), Stations.name(ModVillagers.BUILDER).copy(), ChatFormatting.DARK_GRAY,
				VillageHallScreen.line(Component.translatable("screen.aliveworkplace.hall_retrain.no_table", ModBlocks.BLUEPRINT_TABLE.getName()), ChatFormatting.GRAY)), p -> {
				Chat.actionBar(p, Component.translatable("message.aliveworkplace.job.needs_station", Stations.name(ModVillagers.BUILDER),
					ModBlocks.BLUEPRINT_TABLE.getName()).withStyle(ChatFormatting.YELLOW));
				level.playSound(null, p.blockPosition(), SoundEvents.VILLAGER_NO, SoundSource.NEUTRAL, 0.8f, 1f);
			});
		}
	}

	/** A job block clicked: the first click shows the price, the second (on the same block) pays and retrains them. */
	static void pick(ChoiceMenu menu, ServerLevel level, BlockPos hall, Villager villager, int page, VillageHalls.FreeStation station, boolean chosen,
		ServerPlayer p) {
		if (!chosen) {
			Chat.chat(p, Words.articled("message.aliveworkplace.retrain.offer", 3, villager.getDisplayName(),
				Stations.name(villager.getVillagerData().getProfession()), Retraining.levelName(villager), Stations.name(station.profession()), price(villager))
				.withStyle(ChatFormatting.GOLD));
			Chat.actionBar(p, Component.translatable("message.aliveworkplace.hall_retrain.confirm").withStyle(ChatFormatting.GRAY));
			level.playSound(null, p.blockPosition(), SoundEvents.UI_BUTTON_CLICK.value(), SoundSource.PLAYERS, 0.5f, 1f);
			renderChoices(menu, level, hall, villager, page, station.pos());
			menu.broadcastChanges();
			return;
		}
		Outcome outcome = retrain(p, level, hall, villager, station);
		if (outcome == Outcome.DONE) {
			VillageHallScreen.showList(menu, level, hall, page); // the hall shows the new job straight away
			return;
		}
		level.playSound(null, p.blockPosition(), SoundEvents.VILLAGER_NO, SoundSource.NEUTRAL, 0.8f, 1f);
		if (outcome == Outcome.GONE) {
			VillageHallScreen.showList(menu, level, hall, page);
		} else {
			renderChoices(menu, level, hall, villager, page, null);
			menu.broadcastChanges();
		}
	}

	private HallRetraining() {
	}
}
