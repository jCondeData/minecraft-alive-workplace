package io.github.jcondedata.aliveworkplace.inn;

import io.github.jcondedata.aliveworkplace.build.BuilderLevels;
import io.github.jcondedata.aliveworkplace.build.Employer;
import io.github.jcondedata.aliveworkplace.mc.Chat;
import io.github.jcondedata.aliveworkplace.registry.ModAttachments;
import io.github.jcondedata.aliveworkplace.registry.ModVillagers;
import io.github.jcondedata.aliveworkplace.work.ChoiceMenu;
import io.github.jcondedata.aliveworkplace.work.Money;
import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.ai.village.poi.PoiManager;
import net.minecraft.world.entity.ai.village.poi.PoiTypes;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.npc.VillagerProfession;
import net.minecraft.world.entity.npc.VillagerType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.ItemLore;
import net.minecraft.world.phys.AABB;
import org.jetbrains.annotations.Nullable;

/**
 * Inns and travellers: an Innkeeper at an Inn Counter takes in a traveller each morning while there's a free bed nearby
 * (at most {@link #MAX_GUESTS} at a time). A traveller has a trade already — an Apprentice, a Journeyman, now and then an
 * Expert — and can be hired for emeralds (or CobbleDollars): they stay, take the first free job and start at that level.
 * Travellers nobody hires move on after {@link #STAY} ticks. Until they're hired they won't take a job (they're nitwits
 * while they travel).
 */
public final class Innkeepers {
	/** How far from the counter guests stay (and beds count). */
	public static int RADIUS = 32;
	/** Travellers at an inn at once. */
	public static int MAX_GUESTS = 2;
	/** How long an unhired traveller stays (two days). */
	public static long STAY = 48000;
	/** Emeralds to hire a traveller of each level (index = level; CobbleDollars at the usual rate). */
	static final int[] PRICE = {0, 4, 8, 16, 32, 64};

	public static boolean isInnkeeper(Villager villager) {
		return !villager.isBaby() && villager.getVillagerData().getProfession() == ModVillagers.INNKEEPER;
	}

	@Nullable
	public static Traveller traveller(Villager villager) {
		return ModAttachments.TRAVELLER.get(villager);
	}

	public static boolean isTraveller(Villager villager) {
		return ModAttachments.TRAVELLER.has(villager);
	}

	/** The travellers staying round {@code counter}. */
	public static List<Villager> guests(ServerLevel level, BlockPos counter) {
		return level.getEntitiesOfClass(Villager.class, new AABB(counter).inflate(RADIUS, 16, RADIUS), v -> v.isAlive() && isTraveller(v));
	}

	/** A bed nobody has claimed near the counter. */
	public static boolean hasFreeBed(ServerLevel level, BlockPos counter) {
		return level.getPoiManager().findClosest(h -> h.is(PoiTypes.HOME), counter, RADIUS, PoiManager.Occupancy.HAS_SPACE).isPresent();
	}

	/** The level a new traveller has: mostly Apprentices and Journeymen; an Expert more often at a better innkeeper's inn. */
	static int newcomerLevel(ServerLevel level, Villager innkeeper) {
		int skill = BuilderLevels.level(innkeeper);
		int lvl = 2;
		if (level.random.nextFloat() < 0.35f + 0.08f * skill) {
			lvl++;
		}
		if (lvl == 3 && level.random.nextFloat() < 0.05f * skill) {
			lvl++;
		}
		return lvl;
	}

	/** A traveller walks in and takes a room: spawned beside the counter. */
	@Nullable
	public static Villager arrive(ServerLevel level, Villager innkeeper, BlockPos counter) {
		BlockPos spot = standingSpot(level, counter);
		if (spot == null) {
			return null;
		}
		Villager guest = EntityType.VILLAGER.create(level);
		if (guest == null) {
			return null;
		}
		guest.moveTo(spot.getX() + 0.5, spot.getY(), spot.getZ() + 0.5, level.random.nextFloat() * 360f, 0f);
		guest.finalizeSpawn(level, level.getCurrentDifficultyAt(spot), MobSpawnType.EVENT, null);
		VillagerType[] types = {VillagerType.PLAINS, VillagerType.DESERT, VillagerType.SAVANNA, VillagerType.SNOW, VillagerType.TAIGA,
			VillagerType.JUNGLE, VillagerType.SWAMP};
		guest.setVillagerData(guest.getVillagerData().setType(types[level.random.nextInt(types.length)]).setProfession(VillagerProfession.NITWIT));
		int lvl = newcomerLevel(level, innkeeper);
		ModAttachments.TRAVELLER.set(guest, new Traveller(level.getGameTime(), lvl));
		guest.setCustomName(Component.translatable("entity.aliveworkplace.traveller", BuilderLevels.levelName(lvl)));
		level.addFreshEntityWithPassengers(guest);
		level.sendParticles(ParticleTypes.HAPPY_VILLAGER, guest.getX(), guest.getY() + 1.0, guest.getZ(), 8, 0.3, 0.5, 0.3, 0.0);
		level.playSound(null, counter, SoundEvents.VILLAGER_CELEBRATE, SoundSource.NEUTRAL, 0.8f, 1f);
		for (ServerPlayer player : level.getPlayers(p -> p.distanceToSqr(counter.getCenter()) < 64 * 64)) {
			Chat.chat(player, Component.translatable("message.aliveworkplace.inn.arrived", BuilderLevels.levelName(lvl))
				.withStyle(ChatFormatting.GREEN));
		}
		io.github.jcondedata.aliveworkplace.hall.Chronicle.record(level, counter, io.github.jcondedata.aliveworkplace.hall.Chronicle.Kind.ARRIVED, Component.translatable("chronicle.aliveworkplace.arrived",
			BuilderLevels.levelName(lvl), innkeeper.getDisplayName()));
		return guest;
	}

	@Nullable
	private static BlockPos standingSpot(ServerLevel level, BlockPos counter) {
		for (int r = 1; r <= 6; r++) {
			for (BlockPos p : BlockPos.betweenClosed(counter.offset(-r, -2, -r), counter.offset(r, 2, r))) {
				if (level.getBlockState(p.below()).isFaceSturdy(level, p.below(), net.minecraft.core.Direction.UP)
					&& level.getBlockState(p).getCollisionShape(level, p).isEmpty()
					&& level.getBlockState(p.above()).getCollisionShape(level, p.above()).isEmpty() && level.getFluidState(p).isEmpty()) {
					return p.immutable();
				}
			}
		}
		return null;
	}

	/** Travellers whose stay is over (and nobody's watching) move on. */
	public static void leave(ServerLevel level, Villager guest) {
		if (io.github.jcondedata.aliveworkplace.legend.LegendNeeds.staying(guest)) {
			return;
		}
		level.sendParticles(ParticleTypes.POOF, guest.getX(), guest.getY() + 0.5, guest.getZ(), 10, 0.3, 0.5, 0.3, 0.02);
		guest.discard();
	}

	/** Whether {@code guest}'s stay is over. */
	public static boolean stayOver(ServerLevel level, Villager guest) {
		if (io.github.jcondedata.aliveworkplace.legend.LegendNeeds.staying(guest)) {
			return false; // a settled Legend never moves on (ROADMAP 29.5)
		}
		Traveller t = traveller(guest);
		return t != null && level.getGameTime() - t.arrived() >= STAY;
	}

	static long dollars(int lvl) {
		return (long) emeralds(lvl) * Money.DOLLARS_PER_EMERALD;
	}

	static int emeralds(int lvl) {
		return PRICE[Math.max(1, Math.min(PRICE.length - 1, lvl))];
	}

	/** The traveller's screen: who they are, what they'd start as, and a button to hire them. */
	public static void openHire(ServerPlayer player, Villager guest) {
		Traveller t = traveller(guest);
		if (t == null) {
			return;
		}
		ChoiceMenu.open(player, guest.getDisplayName(), p -> p.isAlive() && guest.isAlive() && isTraveller(guest) && p.distanceToSqr(guest) < 64,
			menu -> render(menu, guest, t));
	}

	/** The same screen, not shown to anyone (tests). */
	public static ChoiceMenu hireMenuForTest(ServerPlayer player, Villager guest) {
		return ChoiceMenu.detached(player, menu -> render(menu, guest, traveller(guest)));
	}

	public static final int INFO_SLOT = 11;
	public static final int HIRE_SLOT = 15;

	private static void render(ChoiceMenu menu, Villager guest, Traveller t) {
		menu.clearButtons();
		ServerLevel level = (ServerLevel) guest.level();
		long daysLeft = Math.max(1, (STAY - (level.getGameTime() - t.arrived()) + 23999) / 24000);
		ItemStack info = new ItemStack(Items.WRITABLE_BOOK);
		info.set(DataComponents.CUSTOM_NAME, plain(Component.translatable("screen.aliveworkplace.inn.traveller", BuilderLevels.levelName(t.level())),
			ChatFormatting.GOLD));
		info.set(DataComponents.LORE, new ItemLore(List.of(
			plain(Component.translatable("screen.aliveworkplace.inn.starts_as", BuilderLevels.levelName(t.level())), ChatFormatting.GRAY),
			plain(Component.translatable("screen.aliveworkplace.inn.staying", daysLeft), ChatFormatting.GRAY))));
		menu.button(INFO_SLOT, info, null);
		ItemStack hire = new ItemStack(Items.EMERALD, Math.min(64, emeralds(t.level())));
		hire.set(DataComponents.CUSTOM_NAME, plain(Component.translatable("screen.aliveworkplace.inn.hire",
			Money.describe(dollars(t.level()), emeralds(t.level()))), ChatFormatting.GREEN));
		hire.set(DataComponents.LORE, new ItemLore(List.of(
			plain(Component.translatable("screen.aliveworkplace.inn.hire_hint"), ChatFormatting.GRAY))));
		menu.button(HIRE_SLOT, hire, p -> {
			if (hire(p, guest)) {
				p.closeContainer();
			}
		});
	}

	/** {@code player} hires the traveller: paid for, they join the village and start their first job at their level. */
	public static boolean hire(ServerPlayer player, Villager guest) {
		Traveller t = traveller(guest);
		if (t == null || !guest.isAlive()) {
			return false;
		}
		if (!Money.charge(player, dollars(t.level()), emeralds(t.level()))) {
			Chat.actionBar(player, Component.translatable("message.aliveworkplace.inn.cant_afford",
				Money.describe(dollars(t.level()), emeralds(t.level())), Money.balance(player)).withStyle(ChatFormatting.YELLOW));
			return false;
		}
		ModAttachments.TRAVELLER.remove(guest);
		guest.setCustomName(null);
		ModAttachments.HEAD_START.set(guest, t.level());
		ModAttachments.BUILDER_EMPLOYER.set(guest, new Employer(player.getUUID(), player.getGameProfile().getName()));
		guest.setVillagerData(guest.getVillagerData().setProfession(VillagerProfession.NONE));
		ServerLevel level = (ServerLevel) guest.level();
		guest.refreshBrain(level);
		level.sendParticles(ParticleTypes.HAPPY_VILLAGER, guest.getX(), guest.getY() + 1.0, guest.getZ(), 12, 0.4, 0.5, 0.4, 0.0);
		level.playSound(null, guest.blockPosition(), SoundEvents.VILLAGER_YES, SoundSource.NEUTRAL, 1f, 1f);
		Chat.chat(player, Component.translatable("message.aliveworkplace.inn.hired", BuilderLevels.levelName(t.level()))
			.withStyle(ChatFormatting.GREEN));
		io.github.jcondedata.aliveworkplace.hall.Chronicle.record(level, guest.blockPosition(), io.github.jcondedata.aliveworkplace.hall.Chronicle.Kind.JOINED, Component.translatable("chronicle.aliveworkplace.joined",
			BuilderLevels.levelName(t.level()), player.getDisplayName()));
		io.github.jcondedata.aliveworkplace.legend.Gifted.Gift gift = io.github.jcondedata.aliveworkplace.legend.Gifted.of(guest);
		if (gift != null) {
			io.github.jcondedata.aliveworkplace.hall.Chronicle.record(level, guest.blockPosition(), io.github.jcondedata.aliveworkplace.hall.Chronicle.Kind.JOINED,
				Component.translatable("chronicle.aliveworkplace.joined_gifted", guest.getDisplayName(), gift.title()));
		}
		return true;
	}

	private static Component plain(Component text, ChatFormatting color) {
		return text.copy().withStyle(style -> style.withItalic(false).withColor(color));
	}

	private Innkeepers() {
	}
}
