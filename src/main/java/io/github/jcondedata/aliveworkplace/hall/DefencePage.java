package io.github.jcondedata.aliveworkplace.hall;

import io.github.jcondedata.aliveworkplace.guard.WarParties;
import io.github.jcondedata.aliveworkplace.threat.Culture;
import io.github.jcondedata.aliveworkplace.threat.Lairs;
import io.github.jcondedata.aliveworkplace.threat.ThreatData;
import io.github.jcondedata.aliveworkplace.threat.Threats;
import io.github.jcondedata.aliveworkplace.work.ChoiceMenu;
import io.github.jcondedata.aliveworkplace.work.Words;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/**
 * The Village Hall's Defence page (ROADMAP 32.3), opened from the guards icon: what threatens the village and how the
 * last attacks went.
 * <ul>
 * <li>Second row ({@link #LAIR} to {@link #STOOD}): the lair by the village — whose camp it is, its captain by name,
 * its strength (the icon counts it; the tooltip says how many are at the camp, out raiding and lost in the last raid),
 * roughly where it is (a direction and the distance to the nearest ten blocks) and the days it has stood. With no lair
 * the five slots say "No camp near", and for how long none will come after one was broken up.</li>
 * <li>Bottom row ({@link #HISTORY}): the last three attacks, newest first — the day, who came, how many came and fell,
 * and whether the village fought them off or the rest got away.</li>
 * <li>{@link #SCOUT} (32.12): with a lair standing, "Scout the lair" (a click sends the village's scout, by day), the
 * scout out, or what was found: the lair's exact place, strength and captain. Once it is known, the {@link #WHERE} slot
 * gives the exact place too.</li>
 * <li>{@link #MUSTER} and {@link #SIEGE_BREAKERS} (32.13): muster three in four guards under the player's Rally Banner
 * (without one, the page says to craft one), and hire the Siege-breakers ({@link WarParties}).</li>
 * <li>{@link #AT_PEACE} (32.21): the hall's owner sets the village At peace ({@link AtPeace}), or lets threats come again.</li>
 * <li>Third row, {@link #GATES} (32.4): the village's gates — how many, open or shut, the portcullis up or down, whether
 * a siege holds them shut, and the gate blocks rams broke that wait for a builder.</li>
 * </ul>
 * Everyone who may open the hall may read it. Later items of Milestone 32 add the gates, the scouts, the war party, the
 * next attack and At peace to the rows between. The page is open while lairs are {@link Lairs#live}.
 */
public final class DefencePage {
	public static final int BACK = 0;
	public static final int HEADER = 4;
	/** The lair: whose camp. */
	public static final int LAIR = 10;
	public static final int CAPTAIN = 11;
	public static final int STRENGTH = 12;
	public static final int WHERE = 13;
	public static final int STOOD = 14;
	/** Scouting the lair (32.12): "Scout the lair", a scout out, or what the last one found. */
	public static final int SCOUT = 16;
	/** The village's gates (32.4). */
	public static final int GATES = 19;
	/** Muster a war party under the player's Rally Banner (32.13). */
	public static final int MUSTER = 21;
	/** Hire the Siege-breakers (32.13). */
	public static final int SIEGE_BREAKERS = 23;
	/** The next attack, when the village was warned of it (32.14), or what it takes to see one coming. */
	public static final int NEXT = 25;
	/** At peace (32.21): the hall's owner sets the village at peace; no raids, camps or disasters come. */
	public static final int AT_PEACE = 8;
	/** The last three attacks, newest first. */
	public static final int[] HISTORY = {46, 48, 50};
	/** The raid dens the village knows (38.2), nearest first: "Raid den, tier 3, Fire, active". */
	public static final int[] DENS = {28, 29, 30, 31, 32, 33, 34};
	/** Under each active den (38.1a): hire one of the village's trainers to help in its raid. */
	public static final int[] HELPERS = {37, 38, 39, 40, 41, 42, 43};

	/** Whether the hall's guards icon opens the page. */
	public static boolean open() {
		return Lairs.live();
	}

	/** The guards icon's line for a warned attack (32.14): "Raiders tomorrow night, from the north"; empty when none is known. */
	static Optional<Component> attackLine(ServerLevel level, BlockPos hall) {
		return io.github.jcondedata.aliveworkplace.guard.Warnings.warned(level, hall).map(a -> {
			long ahead = a.day() - Chronicle.day(level);
			Component side = io.github.jcondedata.aliveworkplace.guard.Warnings.side(hall, a);
			return VillageHallScreen.line(ahead >= 2 ? Component.translatable("screen.aliveworkplace.hall.attack_later", ahead, side) // the Seer's (B133)
				: Component.translatable(ahead <= 0 ? "screen.aliveworkplace.hall.attack_tonight" : "screen.aliveworkplace.hall.attack_tomorrow", side), ChatFormatting.RED);
		});
	}

	/** The line on the hall's guards icon: whose lair is camped where, or that none is near. */
	static Component hallLine(ServerLevel level, BlockPos hall) {
		Optional<Lairs.Lair> lair = Lairs.near(level, hall);
		if (lair.isEmpty()) {
			return VillageHallScreen.line("screen.aliveworkplace.defence.no_lair", ChatFormatting.DARK_GRAY);
		}
		Component where = VillageHallScreen.where(hall, lair.get().pos());
		return VillageHallScreen.line(Lairs.named(lair.get())
			? Component.translatable("screen.aliveworkplace.hall.lair", Lairs.captainName(lair.get()), where)
			: Component.translatable("screen.aliveworkplace.hall.lair_unnamed", Lairs.lairName(lair.get().culture()), where), ChatFormatting.RED);
	}

	public static void render(ChoiceMenu menu, ServerLevel level, BlockPos hall) {
		menu.clearButtons();
		menu.button(BACK, VillageHallScreen.icon(Items.ARROW, Component.translatable("screen.aliveworkplace.hall.back"), ChatFormatting.WHITE), p -> {
			VillageHallScreen.render(menu, level, hall, 0);
			menu.broadcastChanges();
		});
		int guards = VillageHalls.census(level, hall).guards();
		menu.button(HEADER, VillageHallScreen.icon(Items.IRON_SWORD, Component.translatable("screen.aliveworkplace.defence.title", VillageHalls.name(level, hall)),
			ChatFormatting.GOLD, VillageHallScreen.line(Component.translatable("screen.aliveworkplace.defence.about"), ChatFormatting.GRAY),
			VillageHallScreen.line(Component.translatable("screen.aliveworkplace.hall.guards", guards), guards > 0 ? ChatFormatting.GRAY : ChatFormatting.YELLOW)), null);
		Optional<Lairs.Lair> standing = Lairs.near(level, hall);
		if (standing.isPresent()) {
			lair(menu, level, hall, standing.get());
		} else {
			int rest = Lairs.resting(level, hall);
			for (int slot = LAIR; slot <= STOOD; slot++) {
				menu.button(slot, VillageHallScreen.icon(Items.LIGHT_GRAY_STAINED_GLASS_PANE, Component.translatable("screen.aliveworkplace.defence.no_lair"),
					ChatFormatting.GRAY, VillageHallScreen.line(rest > 0 ? Words.counted("screen.aliveworkplace.defence.resting", rest, rest)
						: Component.translatable("screen.aliveworkplace.defence.no_lair_hint"), ChatFormatting.DARK_GRAY)), null);
			}
		}
		menu.button(AT_PEACE, AtPeace.icon(level, hall, menu.viewer()), p -> {
			io.github.jcondedata.aliveworkplace.mc.Chat.chat(p, AtPeace.toggle(level, hall, p));
			render(menu, level, hall);
			menu.broadcastChanges();
		});
		menu.button(GATES, gates(level, hall), null);
		menu.button(NEXT, next(level, hall), null);
		menu.button(MUSTER, muster(level, hall), p -> {
			WarParties.Muster result = WarParties.muster(level, hall, p);
			io.github.jcondedata.aliveworkplace.mc.Chat.chat(p, WarParties.said(level, hall, result,
				result == WarParties.Muster.MUSTERED ? WarParties.marchers(level, hall).size() : 0));
			render(menu, level, hall);
			menu.broadcastChanges();
		});
		menu.button(SIEGE_BREAKERS, siegeBreakers(level, hall), p -> {
			io.github.jcondedata.aliveworkplace.mc.Chat.chat(p, WarParties.hire(level, hall, p));
			render(menu, level, hall);
			menu.broadcastChanges();
		});
		List<ThreatData.Past> past = ThreatData.get(level).past(hall);
		for (int i = 0; i < Math.min(HISTORY.length, past.size()); i++) {
			menu.button(HISTORY[i], attack(past.get(i)), null);
		}
		if (past.isEmpty()) {
			menu.button(HISTORY[1], VillageHallScreen.icon(Items.PAPER, Component.translatable("screen.aliveworkplace.defence.no_attacks"), ChatFormatting.GRAY,
				VillageHallScreen.line("screen.aliveworkplace.defence.no_attacks_hint", ChatFormatting.DARK_GRAY)), null);
		}
		if (io.github.jcondedata.aliveworkplace.guard.RaidDens.live()) {
			dens(menu, level, hall);
		}
	}

	/** The dens row (38.2): each known den with its tier, type and state, where it is and the day it was found. */
	private static void dens(ChoiceMenu menu, ServerLevel level, BlockPos hall) {
		List<io.github.jcondedata.aliveworkplace.guard.RaidDens.Den> dens = io.github.jcondedata.aliveworkplace.guard.RaidDens.near(level, hall);
		if (dens.isEmpty()) {
			menu.button(DENS[3], VillageHallScreen.icon(Items.LIGHT_GRAY_STAINED_GLASS_PANE, Component.translatable("screen.aliveworkplace.defence.no_raid_den"),
				ChatFormatting.GRAY, VillageHallScreen.line("screen.aliveworkplace.defence.no_raid_den_hint", ChatFormatting.DARK_GRAY)), null);
			return;
		}
		Item crystal = BuiltInRegistries.ITEM.getOptional(io.github.jcondedata.aliveworkplace.guard.RaidDens.DEN).orElse(Items.AMETHYST_SHARD);
		for (int i = 0; i < Math.min(DENS.length, dens.size()); i++) {
			io.github.jcondedata.aliveworkplace.guard.RaidDens.Den den = dens.get(i);
			ItemStack icon = VillageHallScreen.icon(crystal, io.github.jcondedata.aliveworkplace.guard.RaidDens.describe(den).copy(),
				den.active() ? ChatFormatting.LIGHT_PURPLE : ChatFormatting.GRAY,
				VillageHallScreen.line(VillageHallScreen.where(hall, den.pos()), ChatFormatting.WHITE),
				VillageHallScreen.line(Component.translatable(den.active() ? "screen.aliveworkplace.defence.raid_den_active_hint"
					: "screen.aliveworkplace.defence.raid_den_resting_hint"), ChatFormatting.GRAY),
				VillageHallScreen.line(Component.translatable("screen.aliveworkplace.defence.raid_den_found", den.day()), ChatFormatting.DARK_GRAY));
			icon.set(DataComponents.ENCHANTMENT_GLINT_OVERRIDE, den.active());
			menu.button(DENS[i], icon, null);
			if (io.github.jcondedata.aliveworkplace.trainer.RaidHelpers.ENABLED && den.active()) {
				menu.button(HELPERS[i], helpers(level, hall, den, menu.viewer()), p -> {
					io.github.jcondedata.aliveworkplace.mc.Chat.chat(p, io.github.jcondedata.aliveworkplace.trainer.RaidHelpers.hire(level, hall, den.pos(), p));
					render(menu, level, hall);
					menu.broadcastChanges();
				});
			}
		}
	}

	/** An active den's helpers slot (38.1a): "Hire a raid helper", its price, and who the player has hired for it. */
	static ItemStack helpers(ServerLevel level, BlockPos hall, io.github.jcondedata.aliveworkplace.guard.RaidDens.Den den,
			net.minecraft.server.level.ServerPlayer viewer) {
		List<Component> lore = new ArrayList<>();
		lore.add(VillageHallScreen.line(Component.translatable("screen.aliveworkplace.defence.raid_helpers_price",
			io.github.jcondedata.aliveworkplace.trainer.RaidHelpers.PRICE), ChatFormatting.GREEN));
		lore.add(VillageHallScreen.line("screen.aliveworkplace.defence.raid_helpers_follow", ChatFormatting.GRAY));
		lore.add(VillageHallScreen.line("screen.aliveworkplace.defence.raid_helpers_help", ChatFormatting.LIGHT_PURPLE));
		int hired = 0;
		if (viewer != null) {
			var hire = io.github.jcondedata.aliveworkplace.trainer.RaidHelpers.hireOf(level.getServer(), viewer.getUUID());
			if (hire.isPresent() && hire.get().den.equals(den.pos())) {
				hired = hire.get().helpers.size();
			}
		}
		int free = io.github.jcondedata.aliveworkplace.trainer.RaidHelpers.free(level, hall).size();
		lore.add(VillageHallScreen.line(Component.translatable("screen.aliveworkplace.defence.raid_helpers_hired", hired,
			io.github.jcondedata.aliveworkplace.trainer.RaidHelpers.CAP, free), ChatFormatting.WHITE));
		return VillageHallScreen.icon(counted(Items.EMERALD, Math.max(1, hired)), Component.translatable("screen.aliveworkplace.defence.raid_helpers"),
			ChatFormatting.AQUA, lore.toArray(Component[]::new));
	}

	private static void lair(ChoiceMenu menu, ServerLevel level, BlockPos hall, Lairs.Lair lair) {
		Optional<Culture> culture = Threats.get(lair.culture());
		Optional<Culture.Lair> spec = culture.flatMap(Culture::lair);
		Component captain = Lairs.captainName(lair);
		menu.button(LAIR, VillageHallScreen.icon(icon(lair.culture()), Lairs.lairName(lair.culture()).copy(), ChatFormatting.RED,
			VillageHallScreen.line(Component.translatable("screen.aliveworkplace.defence.lair_of", Lairs.cultureName(lair.culture())), ChatFormatting.GRAY),
			VillageHallScreen.line("screen.aliveworkplace.defence.lair_raids", ChatFormatting.YELLOW),
			VillageHallScreen.line("screen.aliveworkplace.defence.lair_break", ChatFormatting.GREEN)), null);
		Item weapon = culture.flatMap(Culture::captain).map(c -> c.gear().get(EquipmentSlot.MAINHAND)).filter(id -> !id.equals(Culture.OMINOUS_BANNER))
			.flatMap(id -> BuiltInRegistries.ITEM.getOptional(ResourceLocation.parse(id))).orElse(Items.IRON_AXE);
		menu.button(CAPTAIN, VillageHallScreen.icon(weapon, captain.copy(), ChatFormatting.GOLD,
			VillageHallScreen.line(Component.translatable("screen.aliveworkplace.defence.captain", Lairs.cultureName(lair.culture())), ChatFormatting.GRAY),
			VillageHallScreen.line("screen.aliveworkplace.defence.captain_home", ChatFormatting.GRAY),
			VillageHallScreen.line("screen.aliveworkplace.defence.captain_falls", ChatFormatting.GREEN)), null);
		List<Component> strength = new ArrayList<>();
		strength.add(VillageHallScreen.line(Component.translatable("screen.aliveworkplace.defence.at_home", lair.home()), ChatFormatting.GRAY));
		strength.add(VillageHallScreen.line(Component.translatable("screen.aliveworkplace.defence.out", lair.out()), lair.out() > 0 ? ChatFormatting.RED : ChatFormatting.GRAY));
		strength.add(VillageHallScreen.line(Component.translatable("screen.aliveworkplace.defence.lost", lair.lost()), lair.lost() > 0 ? ChatFormatting.GREEN : ChatFormatting.GRAY));
		spec.filter(s -> s.growth() > 0).ifPresent(s -> strength.add(VillageHallScreen.line(
			Component.translatable("screen.aliveworkplace.defence.grows", s.growth(), s.max()), ChatFormatting.YELLOW)));
		strength.add(VillageHallScreen.line("screen.aliveworkplace.defence.strength_hint", ChatFormatting.DARK_GRAY));
		menu.button(STRENGTH, VillageHallScreen.icon(counted(Items.CROSSBOW, lair.strength()),
			Component.translatable("screen.aliveworkplace.defence.strength", lair.strength()), ChatFormatting.WHITE, strength.toArray(Component[]::new)), null);
		Optional<io.github.jcondedata.aliveworkplace.guard.Scouts.Known> known = io.github.jcondedata.aliveworkplace.guard.Scouts.known(level, hall);
		menu.button(WHERE, known.isPresent()
			? VillageHallScreen.icon(Items.FILLED_MAP, roughly(hall, lair.pos()).copy(), ChatFormatting.WHITE,
				VillageHallScreen.line(exact(lair.pos()), ChatFormatting.AQUA),
				VillageHallScreen.line(Component.translatable("screen.aliveworkplace.defence.where_scouted", known.get().by(), known.get().day()), ChatFormatting.GRAY))
			: VillageHallScreen.icon(Items.MAP, roughly(hall, lair.pos()).copy(), ChatFormatting.WHITE,
				VillageHallScreen.line("screen.aliveworkplace.defence.where_hint", ChatFormatting.GRAY)), null);
		menu.button(SCOUT, scout(level, hall, lair, known), p -> {
			io.github.jcondedata.aliveworkplace.guard.Scouts.send(p, level, hall);
			render(menu, level, hall);
			menu.broadcastChanges();
		});
		long days = Math.max(0, Chronicle.day(level) - lair.day());
		menu.button(STOOD, VillageHallScreen.icon(counted(Items.CLOCK, days),
			days == 0 ? Component.translatable("screen.aliveworkplace.defence.stood_today") : Words.counted("screen.aliveworkplace.defence.stood", days, days),
			ChatFormatting.WHITE, VillageHallScreen.line(Component.translatable("screen.aliveworkplace.defence.stood_since", lair.day()), ChatFormatting.GRAY)), null);
	}

	/** "At 120, 64, -340": a lair's exact place, once a scout found it. */
	private static Component exact(BlockPos pos) {
		return Component.translatable("screen.aliveworkplace.defence.exact", pos.getX(), pos.getY(), pos.getZ());
	}

	/** The scouting slot: what a scout found (the exact place, strength and captain), a scout out, or "Scout the lair". */
	private static ItemStack scout(ServerLevel level, BlockPos hall, Lairs.Lair lair,
			Optional<io.github.jcondedata.aliveworkplace.guard.Scouts.Known> known) {
		Optional<io.github.jcondedata.aliveworkplace.guard.Scouts.Mission> out = io.github.jcondedata.aliveworkplace.guard.Scouts.mission(level, hall);
		int count = io.github.jcondedata.aliveworkplace.guard.Scouts.scouts(level, hall).size();
		Component send = count == 0 ? VillageHallScreen.line("screen.aliveworkplace.defence.scout_none", ChatFormatting.YELLOW)
			: VillageHallScreen.line(Words.counted("screen.aliveworkplace.defence.scout_count", count, count), ChatFormatting.GRAY);
		if (out.isPresent()) {
			long minutes = Math.max(0, (level.getGameTime() - out.get().started()) / 20 / 60);
			return VillageHallScreen.icon(Items.SPYGLASS, Component.translatable("screen.aliveworkplace.defence.scout_out", out.get().name()), ChatFormatting.AQUA,
				VillageHallScreen.line(Words.counted("screen.aliveworkplace.defence.scout_out_since", minutes, minutes), ChatFormatting.GRAY),
				VillageHallScreen.line("screen.aliveworkplace.defence.scout_lost_hint", ChatFormatting.DARK_GRAY));
		}
		if (known.isPresent()) {
			return VillageHallScreen.icon(Items.FILLED_MAP, Component.translatable(known.get().trailed() ? "screen.aliveworkplace.defence.scouted_trailed"
					: "screen.aliveworkplace.defence.scouted", known.get().by()), ChatFormatting.GREEN,
				VillageHallScreen.line(exact(lair.pos()), ChatFormatting.AQUA),
				VillageHallScreen.line(Component.translatable("screen.aliveworkplace.defence.scouted_captain", Lairs.captainName(lair)), ChatFormatting.GOLD),
				VillageHallScreen.line(Component.translatable("screen.aliveworkplace.defence.strength", lair.strength()), ChatFormatting.RED),
				VillageHallScreen.line(Component.translatable("screen.aliveworkplace.defence.scouted_day", known.get().day()), ChatFormatting.GRAY),
				send, VillageHallScreen.line("screen.aliveworkplace.defence.scout_again", ChatFormatting.DARK_GRAY));
		}
		return VillageHallScreen.icon(Items.SPYGLASS, Component.translatable("screen.aliveworkplace.defence.scout"), ChatFormatting.WHITE,
			VillageHallScreen.line("screen.aliveworkplace.defence.scout_hint", ChatFormatting.GRAY), send,
			VillageHallScreen.line("screen.aliveworkplace.defence.scout_click", ChatFormatting.YELLOW));
	}

	/** "Muster a war party": how many guards would march and how many keep the walls. */
	private static ItemStack muster(ServerLevel level, BlockPos hall) {
		int marching = WarParties.marchers(level, hall).size();
		int guards = VillageHalls.census(level, hall).guards();
		return VillageHallScreen.icon(Items.WHITE_BANNER, Component.translatable("screen.aliveworkplace.defence.muster"), ChatFormatting.GOLD,
			VillageHallScreen.line("screen.aliveworkplace.defence.muster_hint", ChatFormatting.GRAY),
			VillageHallScreen.line(Words.counted("screen.aliveworkplace.defence.muster_count", marching, marching, Math.max(0, guards - marching)),
				marching > 0 ? ChatFormatting.AQUA : ChatFormatting.YELLOW),
			VillageHallScreen.line("screen.aliveworkplace.defence.muster_banner", ChatFormatting.DARK_GRAY),
			VillageHallScreen.line("screen.aliveworkplace.defence.muster_click", ChatFormatting.YELLOW));
	}

	/** The Siege-breakers: who they are, their price, how long they stay, or that they're here. */
	private static ItemStack siegeBreakers(ServerLevel level, BlockPos hall) {
		long here = io.github.jcondedata.aliveworkplace.guard.Mercenaries.near(level, hall).stream().filter(WarParties::isSiegeBreaker).count();
		int price = WarParties.price(level, hall);
		return VillageHallScreen.icon(Items.CROSSBOW, Component.translatable("screen.aliveworkplace.defence.siege_breakers"), ChatFormatting.GOLD,
			VillageHallScreen.line("screen.aliveworkplace.defence.siege_breakers_who", ChatFormatting.GRAY),
			VillageHallScreen.line(Component.translatable("screen.aliveworkplace.defence.siege_breakers_price",
				io.github.jcondedata.aliveworkplace.work.Money.describe((long) price * io.github.jcondedata.aliveworkplace.work.Money.DOLLARS_PER_EMERALD, price)), ChatFormatting.GREEN),
			VillageHallScreen.line("screen.aliveworkplace.defence.siege_breakers_stay", ChatFormatting.GRAY),
			here > 0 ? VillageHallScreen.line(Words.counted("screen.aliveworkplace.defence.siege_breakers_here", here, here), ChatFormatting.AQUA)
				: VillageHallScreen.line("screen.aliveworkplace.defence.siege_breakers_click", ChatFormatting.YELLOW));
	}

	/** The next attack (32.14): when, who and from which side, if the village was warned; else who keeps watch, or that nobody does. */
	public static ItemStack next(ServerLevel level, BlockPos hall) {
		Optional<ThreatData.Attack> warned = io.github.jcondedata.aliveworkplace.guard.Warnings.warned(level, hall);
		if (warned.isPresent()) {
			ThreatData.Attack attack = warned.get();
			long ahead = attack.day() - Chronicle.day(level);
			return VillageHallScreen.icon(Items.BELL, ahead >= 2 ? Component.translatable("screen.aliveworkplace.defence.next_later", ahead) // the Seer's (B133)
					: Component.translatable(ahead <= 0 ? "screen.aliveworkplace.defence.next_tonight" : "screen.aliveworkplace.defence.next_tomorrow"),
				ChatFormatting.RED,
				VillageHallScreen.line(Component.translatable("screen.aliveworkplace.defence.next_who",
					io.github.jcondedata.aliveworkplace.guard.Warnings.who(level, hall, attack), io.github.jcondedata.aliveworkplace.guard.Warnings.side(hall, attack)), ChatFormatting.GOLD),
				VillageHallScreen.line("screen.aliveworkplace.defence.next_ready", ChatFormatting.GRAY));
		}
		Optional<io.github.jcondedata.aliveworkplace.guard.Warnings.Source> source = io.github.jcondedata.aliveworkplace.guard.Warnings.source(level, hall);
		if (source.isPresent()) {
			return VillageHallScreen.icon(Items.SPYGLASS, Component.translatable("screen.aliveworkplace.defence.next_none"), ChatFormatting.GREEN,
				VillageHallScreen.line(Component.translatable("screen.aliveworkplace.defence.next_watch", source.get().who()), ChatFormatting.GRAY));
		}
		return VillageHallScreen.icon(Items.GRAY_STAINED_GLASS_PANE, Component.translatable("screen.aliveworkplace.defence.next_blind"), ChatFormatting.YELLOW,
			VillageHallScreen.line("screen.aliveworkplace.defence.next_blind_hint", ChatFormatting.DARK_GRAY));
	}

	/** The gates: open or shut, the portcullis up or down, a siege holding them, and what rams broke. */
	public static ItemStack gates(ServerLevel level, BlockPos hall) {
		io.github.jcondedata.aliveworkplace.threat.Sieges.Status status = io.github.jcondedata.aliveworkplace.threat.Sieges.status(level, hall);
		if (status.gates() == 0 && !status.portcullis() && status.broken() == 0) {
			return VillageHallScreen.icon(Items.OAK_FENCE_GATE, Component.translatable("screen.aliveworkplace.defence.no_gates"), ChatFormatting.GRAY,
				VillageHallScreen.line("screen.aliveworkplace.defence.no_gates_hint", ChatFormatting.DARK_GRAY));
		}
		boolean shut = status.open() == 0;
		List<Component> lines = new ArrayList<>();
		lines.add(VillageHallScreen.line(Words.counted("screen.aliveworkplace.defence.gate_blocks", status.gates(), status.gates()), ChatFormatting.GRAY));
		if (status.portcullis()) {
			lines.add(VillageHallScreen.line(status.down() ? "screen.aliveworkplace.defence.portcullis_down" : "screen.aliveworkplace.defence.portcullis_up",
				status.down() ? ChatFormatting.YELLOW : ChatFormatting.GRAY));
		}
		if (status.siege()) {
			lines.add(VillageHallScreen.line("screen.aliveworkplace.defence.gates_siege", ChatFormatting.RED));
		}
		if (status.broken() > 0) {
			lines.add(VillageHallScreen.line(Words.counted("screen.aliveworkplace.defence.gates_broken", status.broken(), status.broken()), ChatFormatting.RED));
		}
		lines.add(VillageHallScreen.line("screen.aliveworkplace.defence.gates_hint", ChatFormatting.DARK_GRAY));
		return VillageHallScreen.icon(status.down() ? Items.IRON_BARS : Items.SPRUCE_FENCE_GATE,
			Component.translatable(shut ? "screen.aliveworkplace.defence.gates_shut" : "screen.aliveworkplace.defence.gates_open"),
			status.broken() > 0 ? ChatFormatting.RED : ChatFormatting.WHITE, lines.toArray(Component[]::new));
	}

	/** One past attack: its culture's icon counting those who came, the day and who, how many fell and how it ended. */
	private static ItemStack attack(ThreatData.Past past) {
		return VillageHallScreen.icon(counted(icon(past.culture()), past.came()),
			Component.translatable("screen.aliveworkplace.defence.attack", past.day(), Lairs.cultureName(past.culture())), ChatFormatting.WHITE,
			VillageHallScreen.line(Component.translatable("screen.aliveworkplace.defence.attack_count", past.came(), past.fell()), ChatFormatting.GRAY),
			past.fled() ? VillageHallScreen.line(Words.counted("screen.aliveworkplace.defence.attack_fled", past.came() - past.fell(), past.came() - past.fell()),
					ChatFormatting.YELLOW)
				: VillageHallScreen.line("screen.aliveworkplace.defence.attack_won", ChatFormatting.GREEN));
	}

	/**
	 * An icon that counts {@code count} (1 to 64) whatever its item: a crossbow or a clock stacks to 1, and the menu's
	 * container cuts a stack down to its item's size, so these icons are allowed 64 (and, as nothing that wears out may
	 * stack, lose their durability).
	 */
	private static ItemStack counted(Item item, long count) {
		ItemStack icon = new ItemStack(item);
		if (icon.getMaxStackSize() < 64) {
			icon.remove(DataComponents.MAX_DAMAGE);
			icon.remove(DataComponents.DAMAGE);
			icon.set(DataComponents.MAX_STACK_SIZE, 64);
		}
		icon.setCount((int) Math.max(1, Math.min(64, count)));
		return icon;
	}

	/** The item that stands for a culture: its lair's {@code icon}, or a zombie's head for raiders from nowhere. */
	private static Item icon(ResourceLocation culture) {
		return Threats.get(culture).flatMap(Culture::lair).flatMap(lair -> BuiltInRegistries.ITEM.getOptional(lair.icon())).orElse(Items.ZOMBIE_HEAD);
	}

	/** "north-east, about 90 blocks": the side of the hall and the distance to the nearest ten. */
	public static Component roughly(BlockPos hall, BlockPos pos) {
		double dx = pos.getX() - hall.getX();
		double dz = pos.getZ() - hall.getZ();
		long distance = Math.max(10, Math.round(Math.sqrt(dx * dx + dz * dz) / 10) * 10);
		// 0 = south, counting clockwise in eighths (Minecraft's +z is south), as VillageHallScreen.where.
		int eighth = Math.floorMod((int) Math.round(Math.atan2(-dx, dz) / (Math.PI / 4)), 8);
		String[] directions = {"s", "sw", "w", "nw", "n", "ne", "e", "se"};
		return Component.translatable("screen.aliveworkplace.defence.where", Component.translatable("screen.aliveworkplace.hall.dir." + directions[eighth]), distance);
	}

	private DefencePage() {
	}
}
