package io.github.jcondedata.aliveworkplace.hall;

import io.github.jcondedata.aliveworkplace.build.BuildSite;
import io.github.jcondedata.aliveworkplace.build.BuilderLevels;
import io.github.jcondedata.aliveworkplace.build.BuilderStatusSync;
import io.github.jcondedata.aliveworkplace.build.Builders;
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
	static final int NAME = 0;
	static final int PEOPLE = 1;
	static final int BEDS = 2;
	static final int FOOD = 3;
	static final int GUARDS = 4;
	static final int WELLBEING = 5;
	static final int REQUESTS = 6;
	static final int BUILDS = 7;
	static final int PREVIOUS = 9;
	static final int NEXT = 17;
	public static final int FIRST_PERSON = 18;
	static final int PER_PAGE = ChoiceMenu.SIZE - FIRST_PERSON;
	/** Lines of a list shown in a tooltip before "and N more". */
	private static final int LIST_LINES = 8;
	/** How long a villager clicked on glows. */
	static final int GLOW_TICKS = 200;

	public static void open(ServerPlayer player, BlockPos hall) {
		ServerLevel level = player.serverLevel();
		ChoiceMenu.open(player, Component.translatable("screen.aliveworkplace.hall.title", VillageHalls.name(level, hall)),
			p -> p.isAlive() && level.getBlockState(hall).is(ModBlocks.VILLAGE_HALL) && p.position().distanceToSqr(Vec3.atCenterOf(hall)) <= 64,
			menu -> render(menu, level, hall, 0));
	}

	/** The same screen, not shown to anyone (tests). */
	public static ChoiceMenu forTest(ServerPlayer player, BlockPos hall) {
		return ChoiceMenu.detached(player, menu -> render(menu, player.serverLevel(), hall, 0));
	}

	static void render(ChoiceMenu menu, ServerLevel level, BlockPos hall, int page) {
		menu.clearButtons();
		VillageHalls.Census census = VillageHalls.census(level, hall);
		List<Villager> people = new ArrayList<>(census.workers());
		people.addAll(census.jobless());
		int pages = Math.max(1, (people.size() + PER_PAGE - 1) / PER_PAGE);
		int shown = Math.min(page, pages - 1);

		menu.button(NAME, icon(Items.NAME_TAG, VillageHalls.name(level, hall).copy(), ChatFormatting.GOLD,
			line("screen.aliveworkplace.hall.rename", ChatFormatting.DARK_GRAY)), null);
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
				Math.round(VillageGrowth.WELLBEING_NEEDED * 100), VillageGrowth.CAP), growth == VillageGrowth.Blocker.NONE ? ChatFormatting.GREEN : ChatFormatting.YELLOW)), null);
		menu.button(BEDS, icon(Items.RED_BED, Component.translatable("screen.aliveworkplace.hall.beds", census.beds()), ChatFormatting.WHITE,
			line(Component.translatable("screen.aliveworkplace.hall.free_beds", census.freeBeds()),
				census.freeBeds() > 0 ? ChatFormatting.GREEN : ChatFormatting.YELLOW)), null);
		menu.button(FOOD, icon(Items.BREAD, Component.translatable("screen.aliveworkplace.hall.food", census.food()), ChatFormatting.WHITE,
			line("screen.aliveworkplace.hall.food_where", ChatFormatting.GRAY)), null);
		menu.button(GUARDS, icon(Items.IRON_SWORD, Component.translatable("screen.aliveworkplace.hall.guards", census.guards()), ChatFormatting.WHITE,
			line(census.guards() > 0 ? "screen.aliveworkplace.hall.guarded" : "screen.aliveworkplace.hall.unguarded",
				census.guards() > 0 ? ChatFormatting.GRAY : ChatFormatting.YELLOW)), null);
		menu.button(WELLBEING, wellbeingIcon(needs), null);
		menu.button(REQUESTS, requestsIcon(census.requests()), null);
		menu.button(BUILDS, buildsIcon(census.builds()), null);
		menu.divider(1);
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
		int slot = FIRST_PERSON;
		for (Villager villager : people.subList(shown * PER_PAGE, Math.min(people.size(), (shown + 1) * PER_PAGE))) {
			menu.button(slot++, person(level, hall, villager), p -> {
				villager.addEffect(new MobEffectInstance(MobEffects.GLOWING, GLOW_TICKS, 0, false, false));
				p.displayClientMessage(Component.translatable("message.aliveworkplace.hall.glowing", villager.getDisplayName())
					.withStyle(ChatFormatting.GREEN), true);
				level.playSound(null, p.blockPosition(), SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.PLAYERS, 0.6f, 1.2f);
			});
		}
	}

	private static void refresh(ChoiceMenu menu, ServerLevel level, BlockPos hall, int page) {
		render(menu, level, hall, page);
		menu.broadcastChanges();
	}

	/** A villager: their workstation (stacked as high as their level), name, level, what they're doing and waiting for. */
	static ItemStack person(ServerLevel level, BlockPos hall, Villager villager) {
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
		for (Component doing : doing(level, villager)) {
			lore.add(plain(doing, ChatFormatting.WHITE));
		}
		if (io.github.jcondedata.aliveworkplace.school.Schools.isSchooled(villager)) {
			lore.add(line("screen.aliveworkplace.hall.schooled", ChatFormatting.GRAY));
		}
		if (VillageNeeds.isHungry(villager, level.getGameTime())) {
			lore.add(line("screen.aliveworkplace.hall.hungry", ChatFormatting.RED));
		}
		if (VillageNeeds.bed(level, villager) == null) {
			lore.add(line("screen.aliveworkplace.hall.no_bed", ChatFormatting.YELLOW));
		}
		// Builders and miners already say what they need in their status.
		boolean saysWhatItNeeds = Builders.activeSite(level, villager) != null || Miners.activeSite(level, villager) != null;
		List<Requests.Request> waiting = working && !saysWhatItNeeds ? Requests.of(level, villager) : List.of();
		if (!waiting.isEmpty()) {
			lore.add(line(Component.translatable("screen.aliveworkplace.hall.waiting_for", waitingList(waiting)), ChatFormatting.YELLOW));
		}
		lore.add(line(where(hall, villager), ChatFormatting.DARK_GRAY));
		lore.add(line("screen.aliveworkplace.hall.click_to_find", ChatFormatting.DARK_GRAY));
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
		double dx = villager.getX() - (hall.getX() + 0.5);
		double dz = villager.getZ() - (hall.getZ() + 0.5);
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

	private static ItemStack icon(Item item, Component name, ChatFormatting color, Component... lore) {
		ItemStack icon = new ItemStack(item);
		icon.set(DataComponents.CUSTOM_NAME, plain(name, color));
		if (lore.length > 0) {
			icon.set(DataComponents.LORE, new ItemLore(List.of(lore)));
		}
		icon.set(DataComponents.HIDE_ADDITIONAL_TOOLTIP, net.minecraft.util.Unit.INSTANCE);
		return icon;
	}

	private static Component line(String key, ChatFormatting color) {
		return line(Component.translatable(key), color);
	}

	private static Component line(Component text, ChatFormatting color) {
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
