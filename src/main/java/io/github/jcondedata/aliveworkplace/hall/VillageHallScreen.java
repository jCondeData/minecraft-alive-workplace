package io.github.jcondedata.aliveworkplace.hall;

import io.github.jcondedata.aliveworkplace.build.BuildSite;
import io.github.jcondedata.aliveworkplace.build.BuilderLevels;
import io.github.jcondedata.aliveworkplace.build.BuilderStatusSync;
import io.github.jcondedata.aliveworkplace.build.Builders;
import io.github.jcondedata.aliveworkplace.mc.Chat;
import io.github.jcondedata.aliveworkplace.mc.Players;
import io.github.jcondedata.aliveworkplace.mine.Miners;
import io.github.jcondedata.aliveworkplace.mine.QuarrySite;
import io.github.jcondedata.aliveworkplace.registry.ModBlocks;
import io.github.jcondedata.aliveworkplace.work.ChoiceMenu;
import io.github.jcondedata.aliveworkplace.work.Requests;
import io.github.jcondedata.aliveworkplace.work.WorkerStatus;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.schedule.Activity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.ItemLore;
import net.minecraft.world.phys.Vec3;

/**
 * The Village Hall's screen: a row of the village's numbers (name, villagers, beds, food in store, guards, requests,
 * buildings going up) over everyone who lives there — each worker as their workstation, stacked as high as their level,
 * with what they're doing and waiting for; clicking one makes them glow for a while so they can be found.
 */
public final class VillageHallScreen {
	public static final int NAME = 0;
	static final int PEOPLE = 1;
	static final int BEDS = 2;
	static final int FOOD = 3;
	public static final int GUARDS = 4;
	static final int WELLBEING = 5;
	static final int REQUESTS = 6;
	static final int BUILDS = 7;
	public static final int QUESTS = 8;
	/** The chronicle button, in the middle of the divider. */
	public static final int CHRONICLE = 13;
	/** "Call everyone home", left of the chronicle. */
	public static final int RECALL = 11;
	/** Hire a band of mercenaries till dawn. */
	public static final int MERCENARIES = 12;
	/** Call a festival (with a cake), right of the chronicle. */
	public static final int FESTIVAL = 14;
	/** What the village should do next, at the divider's right end. */
	public static final int ADVICE = 16;
	/** Draw a map of the village (for an empty map), at the divider's left end. */
	public static final int MAP = 10;
	/** Trade routes, right of the chronicle. */
	public static final int ROUTES = 15;
	/** On a jobless villager's page: find them, and where the free workstations start. */
	public static final int FIND = 8;
	/** On the quests page: where the quests are. */
	public static final int[] QUEST_SLOTS = {20, 22, 24};
	static final int PREVIOUS = 9;
	static final int NEXT = 17;
	/** The page row (ROADMAP 22.5): a tab for each page in {@link HallPages}, then light glass for the room left. */
	public static final int PAGE_ROW = 18;
	/** Where a page's own rows start (under its header and divider). */
	public static final int FIRST_ROW = 18;
	/** Where the list of villagers starts, under the page row. */
	public static final int FIRST_PERSON = PAGE_ROW + 9;
	static final int PER_PAGE = ChoiceMenu.SIZE - FIRST_PERSON;
	/** Lines of a list shown in a tooltip before "and N more". */
	private static final int LIST_LINES = 8;
	/** How long a villager clicked on glows. */
	static final int GLOW_TICKS = 200;

	public static void open(ServerPlayer player, BlockPos hall) {
		ServerLevel level = Players.level(player);
		ChoiceMenu.open(player, Component.translatable("screen.aliveworkplace.hall.title", VillageHalls.name(level, hall)),
			p -> p.isAlive() && level.getBlockState(hall).is(ModBlocks.VILLAGE_HALL) && p.position().distanceToSqr(Vec3.atCenterOf(hall)) <= 64,
			menu -> render(menu, level, hall, 0));
	}

	/** The screen opened from afar with a Village Ledger (it stays open while the hall stands). */
	public static void openRemote(ServerPlayer player, BlockPos hall) {
		ServerLevel level = Players.level(player);
		ChoiceMenu.open(player, Component.translatable("screen.aliveworkplace.hall.title", VillageHalls.name(level, hall)),
			p -> p.isAlive() && p.level() == level && level.isLoaded(hall) && level.getBlockState(hall).is(ModBlocks.VILLAGE_HALL),
			menu -> render(menu, level, hall, 0));
	}

	/** The same screen, not shown to anyone (tests). */
	public static ChoiceMenu forTest(ServerPlayer player, BlockPos hall) {
		return ChoiceMenu.detached(player, menu -> render(menu, Players.level(player), hall, 0));
	}

	static void render(ChoiceMenu menu, ServerLevel level, BlockPos hall, int page) {
		menu.clearButtons();
		VillageHalls.Census census = VillageHalls.census(level, hall);
		List<Villager> people = new ArrayList<>(census.workers());
		people.addAll(census.jobless());
		int pages = Math.max(1, (people.size() + PER_PAGE - 1) / PER_PAGE);
		int shown = Math.min(page, pages - 1);

		VillageRanks.Score score = VillageRanks.score(level, hall, census.villagers());
		VillageRanks.Rank rank = VillageRanks.rank(score);
		VillageRanks.Rank next = rank.next();
		List<Component> nameLore = new ArrayList<>();
		nameLore.add(line(Component.translatable("screen.aliveworkplace.hall.rank", rank.title()), ChatFormatting.AQUA));
		nameLore.add(line(Component.translatable("screen.aliveworkplace.hall.rank_perks", Math.round((VillageRanks.questRewardFactor(rank) - 1) * 100),
			VillageRanks.caravanRoutes(rank), VillageRanks.marketTraders(rank), VillageRanks.growthCap(rank)), ChatFormatting.GRAY));
		if (next != null) {
			nameLore.add(line(Component.translatable("screen.aliveworkplace.hall.rank_next", next.title(), next.villagers, score.villagers(), next.buildings,
				score.buildings(), next.research, score.research()), ChatFormatting.YELLOW));
		}
		int treasury = Treasury.emeralds(level, hall);
		nameLore.add(line(Component.translatable("screen.aliveworkplace.hall.treasury",
			io.github.jcondedata.aliveworkplace.work.Money.describe((long) treasury * io.github.jcondedata.aliveworkplace.work.Money.DOLLARS_PER_EMERALD, treasury)),
			treasury > 0 ? ChatFormatting.GREEN : ChatFormatting.GRAY));
		if (VillageProtection.ENABLED && level.getBlockEntity(hall) instanceof VillageHallBlockEntity owned) {
			nameLore.add(line(owned.isProtected()
				? Component.translatable("screen.aliveworkplace.hall.protected", owned.ownerName())
				: owned.owner() == null ? Component.translatable("screen.aliveworkplace.hall.unowned")
				: Component.translatable("screen.aliveworkplace.hall.unprotected", owned.ownerName()),
				owned.isProtected() ? ChatFormatting.GREEN : ChatFormatting.GRAY));
			nameLore.add(line(owned.isProtected() ? "screen.aliveworkplace.hall.unprotect_click" : "screen.aliveworkplace.hall.protect_click",
				ChatFormatting.DARK_GRAY));
		}
		nameLore.add(line("screen.aliveworkplace.hall.rename", ChatFormatting.DARK_GRAY));
		menu.button(NAME, icon(Items.NAME_TAG, VillageHalls.name(level, hall).copy(), ChatFormatting.GOLD, nameLore.toArray(Component[]::new)), p -> {
			Chat.chat(p, menu.shiftClicked() ? VillageProtection.toggle(level, hall, p) : Treasury.collect(level, hall, p));
			refresh(menu, level, hall, shown);
		});
		VillageNeeds.Needs needs = VillageNeeds.count(level, hall);
		VillageHallBlockEntity entity = level.getBlockEntity(hall) instanceof VillageHallBlockEntity e ? e : null;
		VillageGrowth.Blocker growth = VillageGrowth.blocker(level, hall, needs, entity == null ? 0 : entity.lastBirth());
		menu.button(PEOPLE, icon(Items.EMERALD, Component.translatable("screen.aliveworkplace.hall.villagers", census.villagers()), ChatFormatting.WHITE,
			line(Component.translatable("screen.aliveworkplace.hall.workers", census.workers().size()), ChatFormatting.GRAY),
			line(Component.translatable("screen.aliveworkplace.hall.jobless", census.jobless().size()), ChatFormatting.GRAY),
			line(Component.translatable("screen.aliveworkplace.hall.children", census.children()), ChatFormatting.GRAY),
			line(Component.translatable("screen.aliveworkplace.hall.born", entity == null ? 0 : entity.births()), ChatFormatting.GRAY),
			line(Component.translatable("screen.aliveworkplace.hall.graves", io.github.jcondedata.aliveworkplace.grave.Graves.near(level, hall, VillageHalls.RADIUS).size()),
				ChatFormatting.GRAY),
			line(Component.translatable("screen.aliveworkplace.hall.growth." + growth.name().toLowerCase(), VillageGrowth.FOOD_NEEDED,
				Math.round(VillageGrowth.WELLBEING_NEEDED * 100), VillageRanks.growthCap(VillageRanks.of(level, hall))), growth == VillageGrowth.Blocker.NONE ? ChatFormatting.GREEN : ChatFormatting.YELLOW)), null);
		menu.button(BEDS, icon(Items.RED_BED, Component.translatable("screen.aliveworkplace.hall.beds", census.beds()), ChatFormatting.WHITE,
			line(Component.translatable("screen.aliveworkplace.hall.free_beds", census.freeBeds()),
				census.freeBeds() > 0 ? ChatFormatting.GREEN : ChatFormatting.YELLOW)), null);
		int kinds = VillageHalls.mealKinds(level, hall);
		menu.button(FOOD, icon(Items.BREAD, Component.translatable("screen.aliveworkplace.hall.food", census.food()), ChatFormatting.WHITE,
			line("screen.aliveworkplace.hall.food_where", ChatFormatting.GRAY),
			line(Component.translatable("screen.aliveworkplace.hall.meal_kinds", kinds, io.github.jcondedata.aliveworkplace.people.Diet.VARIED_KINDS),
				kinds >= io.github.jcondedata.aliveworkplace.people.Diet.VARIED_KINDS ? ChatFormatting.GREEN : ChatFormatting.YELLOW)), null);
		menu.button(GUARDS, icon(Items.IRON_SWORD, Component.translatable("screen.aliveworkplace.hall.guards", census.guards()), ChatFormatting.WHITE,
			line(census.guards() > 0 ? "screen.aliveworkplace.hall.guarded" : "screen.aliveworkplace.hall.unguarded",
				census.guards() > 0 ? ChatFormatting.GRAY : ChatFormatting.YELLOW),
			io.github.jcondedata.aliveworkplace.guard.BanditCamps.near(level, hall)
				.map(camp -> line(Component.translatable("screen.aliveworkplace.hall.bandits", where(hall, camp.pos())), ChatFormatting.RED))
				.orElse(line("screen.aliveworkplace.hall.no_bandits", ChatFormatting.DARK_GRAY))), null);
		menu.button(WELLBEING, wellbeingIcon(needs), null);
		menu.button(REQUESTS, requestsIcon(census.requests()), null);
		menu.button(BUILDS, buildsIcon(census.builds()), null);
		List<VillageQuests.Quest> quests = entity == null ? List.of() : entity.quests();
		menu.button(QUESTS, icon(Items.MAP, Component.translatable("screen.aliveworkplace.hall.quests", quests.size()), ChatFormatting.WHITE,
			line(quests.isEmpty() ? "screen.aliveworkplace.hall.no_quests" : "screen.aliveworkplace.hall.quests_hint",
				quests.isEmpty() ? ChatFormatting.GRAY : ChatFormatting.GREEN)), p -> {
			renderQuests(menu, level, hall, p);
			menu.broadcastChanges();
		});
		menu.divider(1);
		int lines = entity == null ? 0 : entity.chronicle().size();
		menu.button(CHRONICLE, icon(Items.WRITTEN_BOOK, Component.translatable("screen.aliveworkplace.hall.chronicle"), ChatFormatting.WHITE,
			line(Component.translatable("screen.aliveworkplace.hall.chronicle_hint", lines), ChatFormatting.GRAY)), p -> {
			renderChronicle(menu, level, hall);
			menu.broadcastChanges();
		});
		int routes = Caravans.Data.get(level).routesFrom(hall).size();
		menu.button(ROUTES, icon(Items.CHEST_MINECART, Component.translatable("screen.aliveworkplace.hall.routes", routes), ChatFormatting.WHITE,
			line(Component.translatable("screen.aliveworkplace.hall.routes_hint"), ChatFormatting.GRAY)), p -> {
			renderRoutes(menu, level, hall);
			menu.broadcastChanges();
		});
		List<VillageAdvice.Tip> tips = VillageAdvice.tips(level, hall);
		menu.button(ADVICE, icon(Items.COMPASS, Component.translatable("screen.aliveworkplace.hall.advice"), ChatFormatting.WHITE,
			line(tips.isEmpty() ? Component.translatable("screen.aliveworkplace.hall.advice_none")
				: Component.translatable("screen.aliveworkplace.hall.advice_count", tips.size(), tips.get(0).title()), ChatFormatting.GRAY)), p -> {
			renderAdvice(menu, level, hall);
			menu.broadcastChanges();
		});
		menu.button(MAP, icon(Items.FILLED_MAP, Component.translatable("screen.aliveworkplace.hall.map"), ChatFormatting.WHITE,
			line(Component.translatable("screen.aliveworkplace.hall.map_hint"), ChatFormatting.GRAY)), p -> {
			Chat.chat(p, VillageMaps.draw(level, hall, p));
		});
		menu.button(RECALL, icon(Items.BELL, Component.translatable("screen.aliveworkplace.hall.recall"), ChatFormatting.WHITE,
			line(Component.translatable("screen.aliveworkplace.hall.recall_hint"), ChatFormatting.GRAY)), p -> {
			int came = VillageHalls.recall(level, hall);
			Chat.actionBar(p, Component.translatable(came == 0 ? "message.aliveworkplace.hall.recall_none" : "message.aliveworkplace.hall.recalled", came)
				.withStyle(came == 0 ? ChatFormatting.GRAY : ChatFormatting.GREEN));
			if (came > 0) {
				level.playSound(null, hall, SoundEvents.BELL_BLOCK, SoundSource.BLOCKS, 1f, 1f);
			}
			refresh(menu, level, hall, shown);
		});
		int band = io.github.jcondedata.aliveworkplace.guard.Mercenaries.near(level, hall).size();
		menu.button(MERCENARIES, icon(Items.IRON_SWORD, Component.translatable("screen.aliveworkplace.hall.mercenaries"), ChatFormatting.WHITE,
			line(band > 0 ? Component.translatable("screen.aliveworkplace.hall.mercenaries_here", band)
				: Component.translatable("screen.aliveworkplace.hall.mercenaries_hint", io.github.jcondedata.aliveworkplace.guard.Mercenaries.BAND,
					io.github.jcondedata.aliveworkplace.work.Money.describe((long) io.github.jcondedata.aliveworkplace.guard.Mercenaries.price(level, hall)
						* io.github.jcondedata.aliveworkplace.work.Money.DOLLARS_PER_EMERALD, io.github.jcondedata.aliveworkplace.guard.Mercenaries.price(level, hall))),
				band > 0 ? ChatFormatting.GREEN : ChatFormatting.GRAY)), p -> {
			Chat.chat(p, io.github.jcondedata.aliveworkplace.guard.Mercenaries.hire(level, hall, p));
			refresh(menu, level, hall, shown);
		});
		long festival = entity == null ? -1 : Festivals.nextDay(level, hall, entity);
		long inDays = festival - Chronicle.day(level);
		menu.button(FESTIVAL, icon(Items.FIREWORK_ROCKET, Component.translatable("screen.aliveworkplace.hall.festival"), ChatFormatting.WHITE,
			line(Festivals.isOn(level, hall) ? Component.translatable("screen.aliveworkplace.hall.festival_on")
				: inDays <= 0 ? Component.translatable("screen.aliveworkplace.hall.festival_today")
				: io.github.jcondedata.aliveworkplace.work.Words.counted("screen.aliveworkplace.hall.festival_in", inDays, inDays), ChatFormatting.GOLD),
			line("screen.aliveworkplace.hall.festival_hint", ChatFormatting.GRAY)), p -> {
			Chat.chat(p, Festivals.call(level, hall, p));
			refresh(menu, level, hall, shown);
		});
		if (pages > 1) {
			if (shown > 0) {
				menu.button(PREVIOUS, icon(Items.ARROW, Component.translatable("screen.aliveworkplace.hall.previous", shown, pages), ChatFormatting.WHITE),
					p -> refresh(menu, level, hall, shown - 1));
			}
			if (shown < pages - 1) {
				menu.button(NEXT, icon(Items.SPECTRAL_ARROW, Component.translatable("screen.aliveworkplace.hall.next", shown + 2, pages), ChatFormatting.WHITE),
					p -> refresh(menu, level, hall, shown + 1));
			}
		}
		pageRow(menu, level, hall);
		int slot = FIRST_PERSON;
		for (Villager villager : people.subList(shown * PER_PAGE, Math.min(people.size(), (shown + 1) * PER_PAGE))) {
			boolean jobless = census.jobless().contains(villager);
			menu.button(slot++, person(level, hall, villager), p -> {
				if (jobless && villager.getVillagerData().getProfession() != net.minecraft.world.entity.npc.VillagerProfession.NITWIT) {
					renderJobs(menu, level, hall, villager, shown);
					menu.broadcastChanges();
				} else {
					glow(level, villager, p);
				}
			});
		}
	}

	/** The page row: a tab for each registered page, light glass after them. */
	private static void pageRow(ChoiceMenu menu, ServerLevel level, BlockPos hall) {
		List<HallPages.Page> pages = HallPages.all();
		for (int i = 0; i < 9; i++) {
			if (i < pages.size()) {
				HallPages.Page page = pages.get(i);
				menu.button(PAGE_ROW + i, page.tab().icon(level, hall), p -> {
					renderPage(menu, level, hall, page, p);
					menu.broadcastChanges();
				});
			} else {
				ItemStack pane = new ItemStack(Items.LIGHT_GRAY_STAINED_GLASS_PANE);
				pane.set(DataComponents.HIDE_TOOLTIP, net.minecraft.util.Unit.INSTANCE);
				menu.button(PAGE_ROW + i, pane, null);
			}
		}
	}

	/** A page from {@link HallPages}: back, its header, a divider, then whatever the page shows. */
	public static void renderPage(ChoiceMenu menu, ServerLevel level, BlockPos hall, HallPages.Page page, ServerPlayer viewer) {
		menu.clearButtons();
		menu.button(0, icon(Items.ARROW, Component.translatable("screen.aliveworkplace.hall.back"), ChatFormatting.WHITE), p -> refresh(menu, level, hall, 0));
		menu.button(4, page.header().icon(level, hall), null);
		menu.divider(1);
		page.content().fill(menu, level, hall, viewer);
	}

	private static void glow(ServerLevel level, Villager villager, ServerPlayer p) {
		villager.addEffect(new MobEffectInstance(MobEffects.GLOWING, GLOW_TICKS, 0, false, false));
		Chat.actionBar(p, Component.translatable("message.aliveworkplace.hall.glowing", villager.getDisplayName())
			.withStyle(ChatFormatting.GREEN));
		level.playSound(null, p.blockPosition(), SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.PLAYERS, 0.6f, 1.2f);
	}

	/** A jobless villager's page: the village's free workstations; a click gives them that job. */
	public static void renderJobs(ChoiceMenu menu, ServerLevel level, BlockPos hall, Villager villager, int page) {
		menu.clearButtons();
		menu.button(0, icon(Items.ARROW, Component.translatable("screen.aliveworkplace.hall.back"), ChatFormatting.WHITE), p -> refresh(menu, level, hall, page));
		menu.button(4, person(level, hall, villager), null);
		menu.button(FIND, icon(Items.SPYGLASS, Component.translatable("screen.aliveworkplace.hall.find"), ChatFormatting.WHITE), p -> glow(level, villager, p));
		menu.divider(1);
		List<VillageHalls.FreeStation> stations = VillageHalls.freeStations(level, hall);
		int slot = FIRST_ROW;
		for (VillageHalls.FreeStation station : stations) {
			if (slot >= ChoiceMenu.SIZE) {
				break;
			}
			Component job = Component.translatable("entity.minecraft.villager." + station.profession().name());
			Item item = level.getBlockState(station.pos()).getBlock().asItem();
			menu.button(slot++, icon(item == Items.AIR ? Items.PAPER : item, job.copy(), ChatFormatting.WHITE,
				line(where(hall, station.pos()), ChatFormatting.GRAY),
				line(Component.translatable("screen.aliveworkplace.hall.give_job", villager.getDisplayName(), job), ChatFormatting.GREEN)), p -> {
				if (VillageHalls.assign(level, villager, station)) {
					Chat.actionBar(p, Component.translatable("message.aliveworkplace.hall.assigned", villager.getDisplayName(), job)
						.withStyle(ChatFormatting.GREEN));
					level.playSound(null, p.blockPosition(), SoundEvents.VILLAGER_YES, SoundSource.NEUTRAL, 0.8f, 1f);
					refresh(menu, level, hall, page);
				} else {
					Chat.actionBar(p, Component.translatable("message.aliveworkplace.hall.not_assigned").withStyle(ChatFormatting.YELLOW));
					renderJobs(menu, level, hall, villager, page);
					menu.broadcastChanges();
				}
			});
		}
		if (stations.isEmpty()) {
			menu.button(FIRST_ROW + 4, icon(Items.PAPER, Component.translatable("screen.aliveworkplace.hall.no_free_stations"), ChatFormatting.GRAY,
				line(Component.translatable("screen.aliveworkplace.hall.no_free_stations_hint"), ChatFormatting.DARK_GRAY)), null);
		}
	}

	/** The trade routes page: the villages this one can trade with; a click starts or stops sending them what they need. */
	/** The "What next?" page: what the village lacks, most pressing first (see {@link VillageAdvice}). */
	public static void renderAdvice(ChoiceMenu menu, ServerLevel level, BlockPos hall) {
		menu.clearButtons();
		menu.button(0, icon(Items.ARROW, Component.translatable("screen.aliveworkplace.hall.back"), ChatFormatting.WHITE), p -> refresh(menu, level, hall, 0));
		menu.button(4, icon(Items.COMPASS, Component.translatable("screen.aliveworkplace.hall.advice_title", VillageHalls.name(level, hall)), ChatFormatting.GOLD,
			line(Component.translatable("screen.aliveworkplace.hall.advice_about"), ChatFormatting.GRAY)), null);
		menu.divider(1);
		int slot = FIRST_ROW;
		for (VillageAdvice.Tip tip : VillageAdvice.tips(level, hall)) {
			if (slot >= ChoiceMenu.SIZE) {
				break;
			}
			menu.button(slot++, icon(tip.icon(), tip.title().copy(), ChatFormatting.YELLOW, line(tip.how(), ChatFormatting.GRAY)), null);
		}
		if (slot == FIRST_ROW) {
			menu.button(slot, icon(Items.EMERALD, Component.translatable("screen.aliveworkplace.hall.advice_none"), ChatFormatting.GREEN), null);
		}
	}

	public static void renderRoutes(ChoiceMenu menu, ServerLevel level, BlockPos hall) {
		menu.clearButtons();
		menu.button(0, icon(Items.ARROW, Component.translatable("screen.aliveworkplace.hall.back"), ChatFormatting.WHITE), p -> refresh(menu, level, hall, 0));
		Caravans.Data data = Caravans.Data.get(level);
		long onTheRoad = data.onTheRoad().stream().filter(s -> s.from().equals(hall) || s.to().equals(hall)).count();
		menu.button(4, icon(Items.CHEST_MINECART, Component.translatable("screen.aliveworkplace.hall.routes_title", VillageHalls.name(level, hall)),
			ChatFormatting.GOLD, line(Component.translatable("screen.aliveworkplace.hall.routes_about", Caravans.KEEP, Caravans.CARGO_STACKS), ChatFormatting.GRAY),
			line(Component.translatable("screen.aliveworkplace.hall.routes_road", onTheRoad), ChatFormatting.GRAY)), null);
		menu.divider(1);
		List<Caravans.Village> neighbours = Caravans.neighbours(level, hall);
		int slot = FIRST_ROW;
		for (Caravans.Village other : neighbours) {
			if (slot >= ChoiceMenu.SIZE) {
				break;
			}
			boolean sending = data.routesFrom(hall).contains(other.hall());
			boolean receiving = data.routesFrom(other.hall()).contains(hall);
			List<Component> lore = new ArrayList<>();
			lore.add(line(where(hall, other.hall()), ChatFormatting.GRAY));
			if (!other.wants().isEmpty()) {
				net.minecraft.network.chat.MutableComponent wants = Component.empty();
				for (int i = 0; i < Math.min(3, other.wants().size()); i++) {
					Caravans.Want w = other.wants().get(i);
					wants.append(i == 0 ? Component.empty() : Component.literal(", "))
						.append(Component.translatable("chronicle.aliveworkplace.goods", w.count(), w.item().getDescription()));
				}
				lore.add(line(Component.translatable("screen.aliveworkplace.hall.route_wants", wants), ChatFormatting.YELLOW));
			} else {
				lore.add(line(Component.translatable("screen.aliveworkplace.hall.route_wants_nothing"), ChatFormatting.DARK_GRAY));
			}
			if (receiving) {
				lore.add(line(Component.translatable("screen.aliveworkplace.hall.route_receiving"), ChatFormatting.AQUA));
			}
			lore.add(line(Component.translatable(sending ? "screen.aliveworkplace.hall.route_on" : "screen.aliveworkplace.hall.route_off"),
				sending ? ChatFormatting.GREEN : ChatFormatting.GRAY));
			ItemStack icon = icon(sending ? Items.CHEST_MINECART : Items.MINECART, other.name().copy(), sending ? ChatFormatting.GREEN : ChatFormatting.WHITE,
				lore.toArray(Component[]::new));
			menu.button(slot++, icon, p -> {
				int max = VillageRanks.caravanRoutes(VillageRanks.of(level, hall));
				boolean on = data.toggleRoute(hall, other.hall(), max);
				boolean wasOn = sending;
				Chat.actionBar(p, Component.translatable(on ? "message.aliveworkplace.hall.route_started"
					: wasOn ? "message.aliveworkplace.hall.route_stopped" : "message.aliveworkplace.hall.route_full", other.name(), max)
					.withStyle(on ? ChatFormatting.GREEN : ChatFormatting.YELLOW));
				renderRoutes(menu, level, hall);
				menu.broadcastChanges();
			});
		}
		if (neighbours.isEmpty()) {
			menu.button(FIRST_ROW + 4, icon(Items.PAPER, Component.translatable("screen.aliveworkplace.hall.no_neighbours"), ChatFormatting.GRAY,
				line(Component.translatable("screen.aliveworkplace.hall.no_neighbours_hint", Caravans.RANGE), ChatFormatting.DARK_GRAY)), null);
		}
	}

	/** The chronicle page: what happened in the village, newest first. */
	public static void renderChronicle(ChoiceMenu menu, ServerLevel level, BlockPos hall) {
		menu.clearButtons();
		menu.button(0, icon(Items.ARROW, Component.translatable("screen.aliveworkplace.hall.back"), ChatFormatting.WHITE), p -> refresh(menu, level, hall, 0));
		VillageHallBlockEntity entity = level.getBlockEntity(hall) instanceof VillageHallBlockEntity e ? e : null;
		List<Chronicle.Entry> entries = entity == null ? List.of() : entity.chronicle();
		menu.button(4, icon(Items.WRITTEN_BOOK, Component.translatable("screen.aliveworkplace.hall.chronicle_title", VillageHalls.name(level, hall)),
			ChatFormatting.GOLD, line(Component.translatable("screen.aliveworkplace.hall.chronicle_about", Chronicle.day(level)), ChatFormatting.GRAY)), null);
		menu.divider(1);
		int slot = FIRST_ROW;
		for (int i = entries.size() - 1; i >= 0 && slot < ChoiceMenu.SIZE; i--) {
			Chronicle.Entry entry = entries.get(i);
			menu.button(slot++, icon(entry.kind().icon, entry.text().copy(), ChatFormatting.WHITE,
				line(Component.translatable("screen.aliveworkplace.hall.chronicle_day", entry.day()), ChatFormatting.GRAY)), null);
		}
		if (entries.isEmpty()) {
			menu.button(FIRST_ROW + 4, icon(Items.PAPER, Component.translatable("screen.aliveworkplace.hall.chronicle_empty"), ChatFormatting.GRAY), null);
		}
	}

	/** The quests page: each open quest with what it takes and pays; a click hands in what a quest asks for. */
	public static void renderQuests(ChoiceMenu menu, ServerLevel level, BlockPos hall, ServerPlayer viewer) {
		menu.clearButtons();
		menu.button(0, icon(Items.ARROW, Component.translatable("screen.aliveworkplace.hall.back"), ChatFormatting.WHITE), p -> refresh(menu, level, hall, 0));
		VillageHallBlockEntity entity = level.getBlockEntity(hall) instanceof VillageHallBlockEntity e ? e : null;
		List<VillageQuests.Quest> quests = entity == null ? List.of() : entity.quests();
		menu.button(4, icon(Items.WRITABLE_BOOK, Component.translatable("screen.aliveworkplace.hall.quests_title", VillageHalls.name(level, hall)),
			ChatFormatting.GOLD, line(Component.translatable("screen.aliveworkplace.hall.quests_about", VillageQuests.MAX_OPEN), ChatFormatting.GRAY),
			line(Component.translatable("screen.aliveworkplace.hall.quests_done", entity == null ? 0 : entity.questsDone()), ChatFormatting.GRAY)), null);
		menu.divider(1);
		for (int i = 0; i < Math.min(quests.size(), QUEST_SLOTS.length); i++) {
			VillageQuests.Quest quest = quests.get(i);
			menu.button(QUEST_SLOTS[i], questIcon(level, quest, viewer), quest.kind() != VillageQuests.Kind.BRING ? null : p -> {
				int given = VillageQuests.handIn(p, hall, quest.id());
				if (given == 0) {
					Chat.actionBar(p, Component.translatable("message.aliveworkplace.quest.nothing", quest.itemType().getDescription())
						.withStyle(ChatFormatting.YELLOW));
				} else {
					level.playSound(null, p.blockPosition(), SoundEvents.BUNDLE_INSERT, SoundSource.PLAYERS, 0.8f, 1f);
				}
				renderQuests(menu, level, hall, p);
				menu.broadcastChanges();
			});
		}
	}

	static ItemStack questIcon(ServerLevel level, VillageQuests.Quest quest, ServerPlayer viewer) {
		Item item = switch (quest.kind()) {
			case BRING -> quest.itemType();
			case SLAY -> Items.IRON_SWORD;
			case BATTLE -> net.minecraft.core.registries.BuiltInRegistries.ITEM.getOptional(
				net.minecraft.resources.ResourceLocation.fromNamespaceAndPath("cobblemon", "poke_ball")).orElse(Items.TARGET);
		};
		ItemStack icon = new ItemStack(item, Math.max(1, Math.min(64, quest.left())));
		List<Component> lore = new ArrayList<>();
		if (!quest.poster().isEmpty()) {
			lore.add(line(Component.translatable("screen.aliveworkplace.hall.quest_by", quest.poster()), ChatFormatting.GRAY));
		}
		lore.add(line(Component.translatable("screen.aliveworkplace.hall.quest_progress", quest.progress(), quest.count()), ChatFormatting.GRAY));
		lore.add(line(Component.translatable("screen.aliveworkplace.hall.quest_reward",
			io.github.jcondedata.aliveworkplace.work.Money.describe((long) quest.reward() * io.github.jcondedata.aliveworkplace.work.Money.DOLLARS_PER_EMERALD,
				quest.reward())), ChatFormatting.GREEN));
		long days = Math.max(1, (VillageQuests.LASTS - (level.getGameTime() - quest.posted()) + 23999) / 24000);
		lore.add(line(Component.translatable("screen.aliveworkplace.hall.quest_days", days), ChatFormatting.DARK_GRAY));
		switch (quest.kind()) {
			case BRING -> {
				int have = viewer.getInventory().countItem(quest.itemType());
				lore.add(line(have > 0 ? Component.translatable("screen.aliveworkplace.hall.quest_hand_in", Math.min(have, quest.left()))
					: Component.translatable("screen.aliveworkplace.hall.quest_none_on_you"), have > 0 ? ChatFormatting.YELLOW : ChatFormatting.DARK_GRAY));
			}
			case SLAY -> lore.add(line(Component.translatable("screen.aliveworkplace.hall.quest_slay_hint", VillageHalls.RADIUS), ChatFormatting.YELLOW));
			case BATTLE -> lore.add(line("screen.aliveworkplace.hall.quest_battle_hint", ChatFormatting.YELLOW));
		}
		return icon(item, VillageQuests.describe(quest).copy(), ChatFormatting.GOLD, lore.toArray(Component[]::new));
	}

	private static void refresh(ChoiceMenu menu, ServerLevel level, BlockPos hall, int page) {
		render(menu, level, hall, page);
		menu.broadcastChanges();
	}

	/** A villager: their workstation (stacked as high as their level), name, level, what they're doing and waiting for. */
	public static ItemStack person(ServerLevel level, BlockPos hall, Villager villager) {
		boolean working = villager.getBrain().getMemory(MemoryModuleType.JOB_SITE).isPresent()
			&& villager.getVillagerData().getProfession() != net.minecraft.world.entity.npc.VillagerProfession.NONE;
		Item station = working ? workstation(level, villager) : Items.PAPER;
		ItemStack icon = new ItemStack(station, working ? BuilderLevels.level(villager) : 1);
		Component name = villager.hasCustomName() ? villager.getDisplayName()
			: working ? Component.translatable("entity.minecraft.villager." + villager.getVillagerData().getProfession().name())
			: Component.translatable("screen.aliveworkplace.hall.no_job");
		icon.set(DataComponents.CUSTOM_NAME, plain(name, working ? ChatFormatting.WHITE : ChatFormatting.GRAY));
		List<Component> lore = new ArrayList<>();
		if (villager.hasCustomName() && working) {
			lore.add(line(Component.translatable("entity.minecraft.villager." + villager.getVillagerData().getProfession().name()), ChatFormatting.GRAY));
		}
		if (working) {
			lore.add(line(levelLine(villager), ChatFormatting.GRAY));
		}
		List<io.github.jcondedata.aliveworkplace.people.Traits.Trait> traits = io.github.jcondedata.aliveworkplace.people.Traits.of(villager);
		if (!traits.isEmpty()) {
			net.minecraft.network.chat.MutableComponent list = Component.empty();
			for (int i = 0; i < traits.size(); i++) {
				list.append(i == 0 ? Component.empty() : Component.literal(", "))
					.append(Component.translatable("screen.aliveworkplace.hall.trait", traits.get(i).title(), traits.get(i).description()));
			}
			lore.add(line(Component.translatable("screen.aliveworkplace.hall.traits", list), ChatFormatting.AQUA));
		}
		for (Component doing : doing(level, villager)) {
			lore.add(plain(doing, ChatFormatting.WHITE));
		}
		Component pace = working ? io.github.jcondedata.aliveworkplace.work.Pace.describe(villager) : null;
		if (pace != null) {
			// "Works 62% faster (Machop from the pasture, a happy mood)", "Works 100% faster: at the cap (...)" (ROADMAP 30.2)
			lore.add(line(Component.translatable("screen.aliveworkplace.hall.pace", pace).withStyle(pace.getStyle()), ChatFormatting.GRAY));
		}
		io.github.jcondedata.aliveworkplace.people.Moods.Mood mood = io.github.jcondedata.aliveworkplace.people.Moods.of(villager);
		if (mood != null) {
			net.minecraft.network.chat.MutableComponent why = Component.empty();
			List<Component> reasons = new ArrayList<>(mood.bad());
			reasons.addAll(mood.good());
			for (int i = 0; i < Math.min(4, reasons.size()); i++) {
				why.append(i == 0 ? Component.empty() : Component.literal(", ")).append(reasons.get(i));
			}
			lore.add(line(Component.translatable("screen.aliveworkplace.hall.mood", mood.title(), why),
				mood.score() >= io.github.jcondedata.aliveworkplace.people.Moods.HAPPY ? ChatFormatting.GREEN
					: mood.score() < io.github.jcondedata.aliveworkplace.people.Moods.UNHAPPY ? ChatFormatting.RED : ChatFormatting.GRAY));
		}
		io.github.jcondedata.aliveworkplace.people.Families.Parents parents = io.github.jcondedata.aliveworkplace.people.Families.parents(villager);
		if (parents != null) {
			lore.add(line(Component.translatable("screen.aliveworkplace.hall.child_of", parents.mother(), parents.father()), ChatFormatting.GRAY));
		}
		io.github.jcondedata.aliveworkplace.people.Couples.Partner partner = io.github.jcondedata.aliveworkplace.people.Couples.partner(villager);
		if (partner != null) {
			lore.add(line(Component.translatable(partner.married() ? "screen.aliveworkplace.hall.married_to" : "screen.aliveworkplace.hall.courting",
				partner.name()), ChatFormatting.LIGHT_PURPLE));
		}
		if (io.github.jcondedata.aliveworkplace.school.Schools.isSchooled(villager)) {
			lore.add(line("screen.aliveworkplace.hall.schooled", ChatFormatting.GRAY));
		}
		if (io.github.jcondedata.aliveworkplace.people.Sickness.isIll(villager)) {
			lore.add(line("screen.aliveworkplace.hall.ill", ChatFormatting.RED));
		}
		if (VillageNeeds.isHungry(villager, level.getGameTime())) {
			lore.add(line("screen.aliveworkplace.hall.hungry", ChatFormatting.RED));
		}
		BlockPos bed = VillageNeeds.bed(level, villager);
		if (bed == null) {
			lore.add(line("screen.aliveworkplace.hall.no_bed", ChatFormatting.YELLOW));
		} else {
			io.github.jcondedata.aliveworkplace.people.Homes.at(level, bed).ifPresentOrElse(
				home -> lore.add(line(Component.translatable("screen.aliveworkplace.hall.home", home.name()), ChatFormatting.GRAY)),
				() -> lore.add(line("screen.aliveworkplace.hall.home_unbuilt", ChatFormatting.DARK_GRAY)));
		}
		// Builders and miners already say what they need in their status.
		boolean saysWhatItNeeds = Builders.activeSite(level, villager) != null || Miners.activeSite(level, villager) != null;
		List<Requests.Request> waiting = working && !saysWhatItNeeds ? Requests.of(level, villager) : List.of();
		if (!waiting.isEmpty()) {
			lore.add(line(Component.translatable("screen.aliveworkplace.hall.waiting_for", waitingList(waiting)), ChatFormatting.YELLOW));
		}
		lore.add(line(where(hall, villager), ChatFormatting.DARK_GRAY));
		boolean canBeGivenAJob = !working && !villager.isBaby() && villager.getVillagerData().getProfession() != net.minecraft.world.entity.npc.VillagerProfession.NITWIT;
		lore.add(line(canBeGivenAJob ? "screen.aliveworkplace.hall.jobless_click" : "screen.aliveworkplace.hall.click_to_find",
			canBeGivenAJob ? ChatFormatting.GREEN : ChatFormatting.DARK_GRAY));
		icon.set(DataComponents.LORE, new ItemLore(lore));
		return icon;
	}

	/** "Apprentice · 35/70 XP" (or "Master · top level"), with any Pokémon partners helping. */
	static Component levelLine(Villager villager) {
		int lvl = BuilderLevels.level(villager);
		net.minecraft.network.chat.MutableComponent out = net.minecraft.world.entity.npc.VillagerData.canLevelUp(lvl)
			? Component.translatable("screen.aliveworkplace.hall.level", BuilderLevels.levelName(lvl), villager.getVillagerXp(),
				net.minecraft.world.entity.npc.VillagerData.getMaxXpPerLevel(lvl))
			: Component.translatable("screen.aliveworkplace.hall.level_max", BuilderLevels.levelName(lvl));
		List<Component> partners = io.github.jcondedata.aliveworkplace.work.Partners.helpers(villager);
		if (!partners.isEmpty()) {
			out.append(Component.translatable("screen.aliveworkplace.hall.partners", io.github.jcondedata.aliveworkplace.work.Partners.names(partners))
				.withStyle(ChatFormatting.GREEN));
		}
		return out;
	}

	/** The block a worker works at, as an item (a sheet of paper if it isn't one). */
	static Item workstation(ServerLevel level, Villager villager) {
		GlobalPos site = villager.getBrain().getMemory(MemoryModuleType.JOB_SITE).orElse(null);
		if (site == null || !site.dimension().equals(level.dimension())) {
			return Items.PAPER;
		}
		Item item = level.getBlockState(site.pos()).getBlock().asItem();
		return item == Items.AIR ? Items.PAPER : item;
	}

	/** What a villager is doing: their build or quarry, their job's status, or just where they are in the day. */
	static List<Component> doing(ServerLevel level, Villager villager) {
		if (villager.isSleeping()) {
			return List.of(line("screen.aliveworkplace.hall.sleeping", ChatFormatting.GRAY));
		}
		BuildSite site = Builders.activeSite(level, villager);
		if (site != null) {
			BuilderStatusSync.Status status = BuilderStatusSync.status(level, site, villager);
			if (status != null) {
				return List.of(status.title(), status.line());
			}
		}
		QuarrySite quarry = Miners.activeSite(level, villager);
		if (quarry != null) {
			return List.of(Component.translatable("message.aliveworkplace.quarry.state." + quarry.status().name().toLowerCase())
				.withStyle(quarry.status() == QuarrySite.Status.NEEDS_PICKAXE ? ChatFormatting.YELLOW : ChatFormatting.GRAY));
		}
		WorkerStatus.Entry entry = WorkerStatus.get(villager, level.getGameTime());
		if (entry != null) {
			return List.of(entry.title(), entry.line());
		}
		Activity activity = villager.getBrain().getActiveNonCoreActivity().orElse(Activity.IDLE);
		String key = activity == Activity.WORK ? "at_work" : activity == Activity.REST ? "resting" : activity == Activity.MEET ? "meeting"
			: activity == Activity.PANIC ? "panicking" : activity == Activity.PLAY ? "playing" : "idle";
		return List.of(line("screen.aliveworkplace.hall." + key, activity == Activity.PANIC ? ChatFormatting.RED : ChatFormatting.GRAY));
	}

	/** "12 Oak Planks, a pickaxe" (the first three). */
	private static Component waitingList(List<Requests.Request> requests) {
		net.minecraft.network.chat.MutableComponent out = Component.empty();
		for (int i = 0; i < Math.min(3, requests.size()); i++) {
			Requests.Request r = requests.get(i);
			if (i > 0) {
				out.append(", ");
			}
			out.append(r.count() > 1 ? Component.translatable("screen.aliveworkplace.hall.count", r.count(), r.what()) : r.what());
		}
		if (requests.size() > 3) {
			out.append(Component.translatable("screen.aliveworkplace.hall.and_more", requests.size() - 3));
		}
		return out;
	}

	/** "34 blocks north-east". */
	static Component where(BlockPos hall, Villager villager) {
		return where(hall, villager.getX(), villager.getZ());
	}

	public static Component where(BlockPos hall, BlockPos pos) {
		return where(hall, pos.getX() + 0.5, pos.getZ() + 0.5);
	}

	static Component where(BlockPos hall, double x, double z) {
		double dx = x - (hall.getX() + 0.5);
		double dz = z - (hall.getZ() + 0.5);
		int distance = (int) Math.round(Math.sqrt(dx * dx + dz * dz));
		if (distance < 4) {
			return Component.translatable("screen.aliveworkplace.hall.here");
		}
		// 0 = south, counting clockwise in eighths (Minecraft's +z is south).
		int eighth = Math.floorMod((int) Math.round(Math.atan2(-dx, dz) / (Math.PI / 4)), 8);
		String[] directions = {"s", "sw", "w", "nw", "n", "ne", "e", "se"};
		return Component.translatable("screen.aliveworkplace.hall.away", distance,
			Component.translatable("screen.aliveworkplace.hall.dir." + directions[eighth]));
	}

	/** How well the village is kept: fed, in a bed, safe and lit, and the pace of work it makes. */
	static ItemStack wellbeingIcon(VillageNeeds.Needs needs) {
		int pace = needs.pacePercent();
		Component paceLine = pace > 0 ? line(Component.translatable("screen.aliveworkplace.hall.faster", pace), ChatFormatting.GREEN)
			: pace < 0 ? line(Component.translatable("screen.aliveworkplace.hall.slower", -pace), ChatFormatting.RED)
			: line("screen.aliveworkplace.hall.usual_pace", ChatFormatting.GRAY);
		return icon(Items.CAKE, Component.translatable("screen.aliveworkplace.hall.wellbeing", Math.round(needs.wellbeing() * 100)), ChatFormatting.WHITE,
			line(Component.translatable("screen.aliveworkplace.hall.fed", needs.fed(), needs.adults()),
				needs.fed() < needs.adults() ? ChatFormatting.YELLOW : ChatFormatting.GRAY),
			line(Component.translatable("screen.aliveworkplace.hall.housed", needs.housed(), needs.villagers()),
				needs.housed() < needs.villagers() ? ChatFormatting.YELLOW : ChatFormatting.GRAY),
			line(Component.translatable("screen.aliveworkplace.hall.safety", needs.guards(), needs.lit(), needs.villagers()), ChatFormatting.GRAY),
			line(Component.translatable("screen.aliveworkplace.hall.beauty", needs.beauty(), Math.round(Decorations.bonus(needs.beauty()) * 100),
				Math.round(Decorations.MAX_BONUS * 100)), needs.beauty() > 0 ? ChatFormatting.GRAY : ChatFormatting.DARK_GRAY),
			paceLine,
			line("screen.aliveworkplace.hall.wellbeing_hint", ChatFormatting.DARK_GRAY));
	}

	private static ItemStack requestsIcon(List<Requests.Request> requests) {
		List<Component> lore = new ArrayList<>();
		if (requests.isEmpty()) {
			lore.add(line("screen.aliveworkplace.hall.no_requests", ChatFormatting.GRAY));
		}
		for (int i = 0; i < Math.min(LIST_LINES, requests.size()); i++) {
			Requests.Request r = requests.get(i);
			lore.add(line(Component.translatable("screen.aliveworkplace.hall.request",
				Component.translatable("entity.minecraft.villager." + r.worker().getVillagerData().getProfession().name()),
				r.count() > 1 ? Component.translatable("screen.aliveworkplace.hall.count", r.count(), r.what()) : r.what()), ChatFormatting.YELLOW));
		}
		if (requests.size() > LIST_LINES) {
			lore.add(line(Component.translatable("screen.aliveworkplace.hall.more", requests.size() - LIST_LINES), ChatFormatting.GRAY));
		}
		if (!requests.isEmpty()) {
			lore.add(line("screen.aliveworkplace.hall.storehouse_hint", ChatFormatting.DARK_GRAY));
		}
		return icon(Items.WRITABLE_BOOK, Component.translatable("screen.aliveworkplace.hall.requests", requests.size()), ChatFormatting.WHITE,
			lore.toArray(Component[]::new));
	}

	private static ItemStack buildsIcon(List<VillageHalls.Build> builds) {
		List<Component> lore = new ArrayList<>();
		if (builds.isEmpty()) {
			lore.add(line("screen.aliveworkplace.hall.no_builds", ChatFormatting.GRAY));
		}
		for (int i = 0; i < Math.min(LIST_LINES, builds.size()); i++) {
			VillageHalls.Build b = builds.get(i);
			lore.add(line(b.builder() == null
				? Component.translatable("screen.aliveworkplace.hall.build_idle", b.name(), Math.round(b.progress() * 100))
				: Component.translatable("screen.aliveworkplace.hall.build", b.name(), Math.round(b.progress() * 100), b.builder().getDisplayName()),
				ChatFormatting.GRAY));
		}
		if (builds.size() > LIST_LINES) {
			lore.add(line(Component.translatable("screen.aliveworkplace.hall.more", builds.size() - LIST_LINES), ChatFormatting.GRAY));
		}
		return icon(Items.BRICKS, Component.translatable("screen.aliveworkplace.hall.builds", builds.size()), ChatFormatting.WHITE,
			lore.toArray(Component[]::new));
	}

	static ItemStack icon(Item item, Component name, ChatFormatting color, Component... lore) {
		ItemStack icon = new ItemStack(item);
		icon.set(DataComponents.CUSTOM_NAME, plain(name, color));
		if (lore.length > 0) {
			icon.set(DataComponents.LORE, new ItemLore(List.of(lore)));
		}
		return io.github.jcondedata.aliveworkplace.mc.Tooltips.nameAndLoreOnly(icon);
	}

	static Component line(String key, ChatFormatting color) {
		return line(Component.translatable(key), color);
	}

	static Component line(Component text, ChatFormatting color) {
		return plain(text, color);
	}

	/** Not italic (lore is by default), in {@code color} unless the text has its own. */
	private static Component plain(Component text, ChatFormatting color) {
		return text.copy().withStyle(style -> {
			style = style.withItalic(false);
			return style.getColor() != null ? style : style.withColor(color);
		});
	}

	private VillageHallScreen() {
	}
}
