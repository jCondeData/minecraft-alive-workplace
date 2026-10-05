package io.github.jcondedata.aliveworkplace.hall;

import io.github.jcondedata.aliveworkplace.blueprint.Blueprints;
import io.github.jcondedata.aliveworkplace.build.BuildSite;
import io.github.jcondedata.aliveworkplace.build.BuilderLevels;
import io.github.jcondedata.aliveworkplace.city.CityPlan;
import io.github.jcondedata.aliveworkplace.city.StewardDesk;
import io.github.jcondedata.aliveworkplace.mc.Chat;
import io.github.jcondedata.aliveworkplace.registry.ModItems;
import io.github.jcondedata.aliveworkplace.work.ChoiceMenu;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/**
 * The Steward's desk (ROADMAP 27.8): the hall's "What next?" page when the village has a Steward. The top row holds his
 * modes (Ask me first, Run the village, Rest), himself (the City Plan, as many as his level) and Approve all; then up to
 * 9 proposals, his open builds (shift-click cancels one) and the "What next?" tips as before. A proposal opens its own
 * page: Approve, Decline, Show me, Another spot, Another style. The work is {@link StewardDesk}'s.
 */
public final class StewardDeskPage {
	public static final int ASK = 1;
	public static final int RUN = 2;
	public static final int REST = 3;
	public static final int STEWARD = 4;
	public static final int APPROVE_ALL = 8;
	/** The proposals' row. */
	public static final int FIRST_PROPOSAL = VillageHallScreen.FIRST_ROW;
	/** His open builds' row. */
	public static final int FIRST_OPEN = FIRST_PROPOSAL + 9;
	/** The tips' rows. */
	public static final int FIRST_TIP = FIRST_OPEN + 9;
	/** On a proposal's page. */
	public static final int APPROVE = 20;
	public static final int DECLINE = 21;
	public static final int SHOW = 22;
	public static final int SPOT = 23;
	public static final int STYLE = 24;

	public static void render(ChoiceMenu menu, ServerLevel level, BlockPos hall, Villager steward) {
		StewardDesk.resolve(level, hall); // "Another spot" searches that finished
		menu.clearButtons();
		menu.button(0, VillageHallScreen.icon(Items.ARROW, Component.translatable("screen.aliveworkplace.hall.back"), ChatFormatting.WHITE),
			p -> {
				VillageHallScreen.render(menu, level, hall, 0);
				menu.broadcastChanges();
			});
		CityPlan.Mode mode = StewardDesk.mode(level, hall);
		mode(menu, level, hall, steward, ASK, CityPlan.Mode.ASK, Items.WRITABLE_BOOK, mode);
		mode(menu, level, hall, steward, RUN, CityPlan.Mode.RUN, Items.BELL, mode);
		mode(menu, level, hall, steward, REST, CityPlan.Mode.REST, Items.WHITE_BED, mode);
		int lvl = BuilderLevels.level(steward);
		List<BuildSite> open = StewardDesk.openSites(level, hall);
		ItemStack head = VillageHallScreen.icon(ModItems.CITY_PLAN, Component.translatable("screen.aliveworkplace.desk.title", steward.getDisplayName(),
				VillageHalls.name(level, hall)), ChatFormatting.GOLD,
			VillageHallScreen.line(Component.translatable("screen.aliveworkplace.desk.level", BuilderLevels.levelName(lvl)), ChatFormatting.AQUA),
			VillageHallScreen.line(Component.translatable("screen.aliveworkplace.desk.open", open.size(),
				io.github.jcondedata.aliveworkplace.city.Stewards.maxOpenBuilds(level, steward)), ChatFormatting.GRAY),
			VillageHallScreen.line(Component.translatable("screen.aliveworkplace.desk.mode." + mode.getSerializedName()), ChatFormatting.GRAY));
		roadNotes(head, level, hall);
		head.setCount(Math.max(1, Math.min(5, lvl)));
		menu.button(STEWARD, head, null);
		StewardDesk.State state = StewardDesk.of(level, hall);
		if (state.proposals().size() > 1) {
			menu.button(APPROVE_ALL, VillageHallScreen.icon(Items.EMERALD_BLOCK, Component.translatable("screen.aliveworkplace.desk.approve_all",
				state.proposals().size()), ChatFormatting.GREEN, VillageHallScreen.line("screen.aliveworkplace.desk.approve_all_hint", ChatFormatting.GRAY)), p -> {
				List<Component> started = StewardDesk.approveAll(level, hall, p);
				if (!StewardDesk.mayUse(level, hall, p)) {
					refuse(p);
				} else if (started.isEmpty()) {
					Chat.actionBar(p, Component.translatable("message.aliveworkplace.steward.desk.none_started").withStyle(ChatFormatting.YELLOW));
				} else {
					approved(level, p, Component.translatable("message.aliveworkplace.steward.desk.approved_all", StewardDesk.list(started)));
				}
				rerender(menu, level, hall);
			});
		}
		menu.divider(1);
		int slot = FIRST_PROPOSAL;
		for (StewardDesk.Proposal proposal : state.proposals()) {
			if (slot >= FIRST_OPEN) {
				break;
			}
			List<Component> lore = describe(level, hall, proposal);
			lore.add(VillageHallScreen.line("screen.aliveworkplace.desk.open_hint", ChatFormatting.DARK_GRAY));
			menu.button(slot++, VillageHallScreen.icon(iconOf(proposal), proposal.name(), ChatFormatting.YELLOW, lore.toArray(Component[]::new)), p -> {
				renderProposal(menu, level, hall, proposal.id());
				menu.broadcastChanges();
			});
		}
		if (state.proposals().isEmpty()) {
			menu.button(FIRST_PROPOSAL + 4, VillageHallScreen.icon(Items.PAPER, Component.translatable(mode == CityPlan.Mode.REST
				? "screen.aliveworkplace.desk.resting" : "screen.aliveworkplace.desk.no_proposals"), ChatFormatting.GRAY,
				VillageHallScreen.line(mode == CityPlan.Mode.REST ? "screen.aliveworkplace.desk.resting_hint" : "screen.aliveworkplace.desk.no_proposals_hint",
					ChatFormatting.DARK_GRAY)), null);
		}
		slot = FIRST_OPEN;
		for (BuildSite site : open) {
			if (slot >= FIRST_TIP) {
				break;
			}
			UUID id = site.id();
			Villager builder = site.builder() != null && level.getEntity(site.builder()) instanceof Villager v ? v : null;
			int percent = Math.round(site.progress(site.plan(level)) * 100);
			menu.button(slot++, VillageHallScreen.icon(Items.BRICKS, Blueprints.displayName(site.structure()).copy(), ChatFormatting.WHITE,
				VillageHallScreen.line(builder == null ? Component.translatable("screen.aliveworkplace.desk.site_idle", percent)
					: Component.translatable(site.isQueued() ? "screen.aliveworkplace.desk.site_queued" : "screen.aliveworkplace.desk.site", percent,
					builder.getDisplayName()), ChatFormatting.GRAY),
				VillageHallScreen.line(VillageHallScreen.where(hall, site.placement().origin()), ChatFormatting.GRAY),
				VillageHallScreen.line("screen.aliveworkplace.desk.cancel_hint", ChatFormatting.RED)), p -> {
				if (!menu.shiftClicked()) {
					Chat.actionBar(p, Component.translatable("screen.aliveworkplace.desk.cancel_hint").withStyle(ChatFormatting.YELLOW));
					return;
				}
				if (StewardDesk.cancel(level, hall, p, id)) {
					Chat.actionBar(p, Component.translatable("message.aliveworkplace.steward.desk.cancelled", Blueprints.displayName(site.structure()))
						.withStyle(ChatFormatting.YELLOW));
				} else {
					refuse(p);
				}
				rerender(menu, level, hall);
			});
		}
		slot = FIRST_TIP;
		for (VillageAdvice.Tip tip : VillageAdvice.tips(level, hall)) {
			if (slot >= ChoiceMenu.SIZE) {
				break;
			}
			menu.button(slot++, VillageHallScreen.icon(tip.icon(), tip.title().copy(), ChatFormatting.YELLOW,
				VillageHallScreen.line(tip.how(), ChatFormatting.GRAY)), null);
		}
	}

	/** Roads that stopped at a gap too wide to bridge, on the Steward's card (27.16). */
	private static void roadNotes(ItemStack head, ServerLevel level, BlockPos hall) {
		if (!(level.getBlockEntity(hall) instanceof io.github.jcondedata.aliveworkplace.hall.VillageHallBlockEntity entity)) {
			return;
		}
		List<Component> notes = io.github.jcondedata.aliveworkplace.city.Roads.deskNotes(entity.plan());
		if (notes.isEmpty()) {
			return;
		}
		net.minecraft.world.item.component.ItemLore lore = head.getOrDefault(net.minecraft.core.component.DataComponents.LORE,
			net.minecraft.world.item.component.ItemLore.EMPTY);
		for (Component note : notes) {
			lore = lore.withLineAdded(VillageHallScreen.line(note, ChatFormatting.YELLOW));
		}
		head.set(net.minecraft.core.component.DataComponents.LORE, lore);
	}

		private static void mode(ChoiceMenu menu, ServerLevel level, BlockPos hall, Villager steward, int slot, CityPlan.Mode which, Item item,
							 CityPlan.Mode current) {
		boolean on = which == current;
		boolean refused = which == CityPlan.Mode.RUN && !StewardDesk.SELF_RUN;
		String key = "screen.aliveworkplace.desk.mode." + which.getSerializedName();
		ItemStack icon = VillageHallScreen.icon(item, Component.translatable(key + ".name"), on ? ChatFormatting.GREEN : ChatFormatting.WHITE,
			VillageHallScreen.line(Component.translatable(key), ChatFormatting.GRAY),
			VillageHallScreen.line(refused ? "screen.aliveworkplace.desk.mode.run.off" : on ? "screen.aliveworkplace.desk.mode.chosen"
				: "screen.aliveworkplace.desk.mode.choose", refused ? ChatFormatting.RED : on ? ChatFormatting.GREEN : ChatFormatting.DARK_GRAY));
		if (on) {
			icon.set(DataComponents.ENCHANTMENT_GLINT_OVERRIDE, true);
		}
		menu.button(slot, icon, p -> {
			if (refused) {
				Chat.actionBar(p, Component.translatable("screen.aliveworkplace.desk.mode.run.off").withStyle(ChatFormatting.YELLOW));
			} else if (StewardDesk.setMode(level, hall, p, which)) {
				Chat.actionBar(p, Component.translatable("message.aliveworkplace.steward.desk.mode", steward.getDisplayName(),
					Component.translatable(key + ".name")).withStyle(ChatFormatting.GREEN));
			} else {
				refuse(p);
			}
			rerender(menu, level, hall);
		});
	}

	/** A build's blueprint, the jobs' crafting table, the research topic's own icon, the wall's oak fence. */
	static Item iconOf(StewardDesk.Proposal proposal) {
		if (proposal.isJobs()) {
			return Items.CRAFTING_TABLE;
		}
		if (proposal.wall().isPresent()) {
			return Items.OAK_FENCE;
		}
		return proposal.researchTopic().map(t -> t.icon).orElse(ModItems.BLUEPRINT);
	}

	/** Why, where, the five materials it needs most with how many are in store, and who builds it after what. */
	static List<Component> describe(ServerLevel level, BlockPos hall, StewardDesk.Proposal proposal) {
		List<Component> lore = new ArrayList<>();
		if (proposal.isJobs()) { // 27.9: who goes where
			if (!proposal.why().isEmpty()) {
				lore.add(VillageHallScreen.line(Component.translatable("screen.aliveworkplace.desk.why", proposal.reason()), ChatFormatting.AQUA));
			}
			for (io.github.jcondedata.aliveworkplace.city.StewardJobs.Job job : proposal.jobs()) {
				lore.add(VillageHallScreen.line(io.github.jcondedata.aliveworkplace.city.StewardJobs.describe(level, hall, job), ChatFormatting.GRAY));
			}
			return lore;
		}
		if (proposal.wall().isPresent()) { // 27.18: the wall along the plan's line, with what it is made of
			lore.add(VillageHallScreen.line(Component.translatable("screen.aliveworkplace.desk.why", proposal.reason()), ChatFormatting.AQUA));
			java.util.List<io.github.jcondedata.aliveworkplace.city.Walls.Piece> pieces =
				io.github.jcondedata.aliveworkplace.city.Walls.proposedPieces(level, hall, proposal.wall().get());
			int[] counts = io.github.jcondedata.aliveworkplace.city.Walls.counts(pieces);
			lore.add(VillageHallScreen.line(Component.translatable("screen.aliveworkplace.desk.wall_pieces",
				counts[io.github.jcondedata.aliveworkplace.city.Walls.Kind.SEGMENT.ordinal()],
				counts[io.github.jcondedata.aliveworkplace.city.Walls.Kind.TOWER.ordinal()],
				counts[io.github.jcondedata.aliveworkplace.city.Walls.Kind.GATE.ordinal()]), ChatFormatting.GRAY));
			lore.add(VillageHallScreen.line(Component.translatable("screen.aliveworkplace.desk.wall_line",
				io.github.jcondedata.aliveworkplace.city.Walls.lineLength(level, hall)), ChatFormatting.GRAY));
			return lore;
		}
		if (proposal.researchTopic().isPresent()) { // 27.9: the scholars' next topic
			io.github.jcondedata.aliveworkplace.research.Research.Topic topic = proposal.researchTopic().get();
			lore.add(VillageHallScreen.line(Component.translatable("screen.aliveworkplace.desk.why", proposal.reason()), ChatFormatting.AQUA));
			lore.add(VillageHallScreen.line(topic.effect(), ChatFormatting.GRAY));
			int next = io.github.jcondedata.aliveworkplace.research.Research.at(level, hall).level(topic) + 1;
			lore.add(VillageHallScreen.line(Component.translatable("screen.aliveworkplace.desk.research_cost", BuilderLevels.levelName(next),
				io.github.jcondedata.aliveworkplace.research.Research.describe(topic.cost(next))), ChatFormatting.GRAY));
			return lore;
		}
		if (proposal.upgrade()) {
			lore.add(VillageHallScreen.line("screen.aliveworkplace.desk.upgrade", ChatFormatting.AQUA));
		}
		if (!proposal.why().isEmpty()) {
			lore.add(VillageHallScreen.line(Component.translatable("screen.aliveworkplace.desk.why", proposal.reason()), ChatFormatting.AQUA));
		}
		Component where = VillageHallScreen.where(hall, proposal.placement().origin());
		lore.add(VillageHallScreen.line(proposal.searching() ? Component.translatable("screen.aliveworkplace.desk.searching")
			: proposal.zone().isEmpty() ? where : Component.translatable("screen.aliveworkplace.desk.where", where, proposal.zone()), ChatFormatting.GRAY));
		List<StewardDesk.Need> needs = StewardDesk.needs(level, hall, proposal);
		if (!needs.isEmpty()) {
			lore.add(VillageHallScreen.line("screen.aliveworkplace.desk.needs", ChatFormatting.WHITE));
			for (StewardDesk.Need need : needs) {
				boolean all = need.inStore() >= need.count();
				lore.add(VillageHallScreen.line(Component.translatable(all ? "screen.aliveworkplace.desk.need_all" : "screen.aliveworkplace.desk.need",
					need.count(), need.item().getDescription(), need.inStore()), all ? ChatFormatting.GREEN : ChatFormatting.GRAY));
			}
		}
		Optional<StewardDesk.Builder> builder = StewardDesk.builderFor(level, hall, proposal);
		lore.add(VillageHallScreen.line(builder.isEmpty() ? Component.translatable("screen.aliveworkplace.desk.no_builder")
			: builder.get().after() == null ? Component.translatable("screen.aliveworkplace.desk.builder", builder.get().villager().getDisplayName())
			: Component.translatable("screen.aliveworkplace.desk.builder_after", builder.get().villager().getDisplayName(),
			Blueprints.displayName(builder.get().after().structure())), builder.isEmpty() ? ChatFormatting.RED : ChatFormatting.GRAY));
		return lore;
	}

	/** A proposal's page. */
	public static void renderProposal(ChoiceMenu menu, ServerLevel level, BlockPos hall, int id) {
		Villager steward = io.github.jcondedata.aliveworkplace.city.Stewards.stewardOf(level, hall);
		Optional<StewardDesk.Proposal> found = StewardDesk.of(level, hall).get(id);
		if (steward == null || found.isEmpty()) {
			VillageHallScreen.renderAdvice(menu, level, hall);
			return;
		}
		StewardDesk.Proposal proposal = found.get();
		menu.clearButtons();
		menu.button(0, VillageHallScreen.icon(Items.ARROW, Component.translatable("screen.aliveworkplace.hall.back"), ChatFormatting.WHITE),
			p -> rerender(menu, level, hall));
		menu.button(4, VillageHallScreen.icon(iconOf(proposal), proposal.name(), ChatFormatting.GOLD,
			describe(level, hall, proposal).toArray(Component[]::new)), null);
		menu.divider(1);
		menu.button(APPROVE, VillageHallScreen.icon(Items.EMERALD, Component.translatable("screen.aliveworkplace.desk.approve"), ChatFormatting.GREEN,
			VillageHallScreen.line("screen.aliveworkplace.desk.approve_hint", ChatFormatting.GRAY)), p -> {
			StewardDesk.Outcome outcome = StewardDesk.approve(level, hall, p, id);
			if (outcome.ok()) {
				approved(level, p, proposal.isJobs() ? Component.translatable("message.aliveworkplace.steward.desk.approved_jobs")
					: proposal.researchTopic().isPresent() ? Component.translatable("message.aliveworkplace.steward.desk.approved_research",
					proposal.researchTopic().get().title())
					: Component.translatable(outcome == StewardDesk.Outcome.STARTED
					? "message.aliveworkplace.steward.desk.approved" : "message.aliveworkplace.steward.desk.queued", proposal.name()));
				rerender(menu, level, hall);
			} else {
				Chat.actionBar(p, Component.translatable("message.aliveworkplace.steward.desk.outcome." + outcome.name().toLowerCase(java.util.Locale.ROOT),
					proposal.name(), steward.getDisplayName()).withStyle(ChatFormatting.YELLOW));
				renderProposal(menu, level, hall, id);
				menu.broadcastChanges();
			}
		});
		menu.button(DECLINE, VillageHallScreen.icon(Items.BARRIER, Component.translatable("screen.aliveworkplace.desk.decline"), ChatFormatting.RED,
			VillageHallScreen.line(Component.translatable("screen.aliveworkplace.desk.decline_hint", StewardDesk.DECLINE_DAYS), ChatFormatting.GRAY)), p -> {
			if (StewardDesk.decline(level, hall, p, id)) {
				Chat.actionBar(p, Component.translatable("message.aliveworkplace.steward.desk.declined", proposal.name(), StewardDesk.DECLINE_DAYS)
					.withStyle(ChatFormatting.YELLOW));
			} else {
				refuse(p);
			}
			rerender(menu, level, hall);
		});
		if (!proposal.isBuild()) {
			return; // jobs and research: Approve or Decline
		}
		menu.button(SHOW, VillageHallScreen.icon(Items.SPYGLASS, Component.translatable("screen.aliveworkplace.desk.show"), ChatFormatting.WHITE,
			VillageHallScreen.line(Component.translatable("screen.aliveworkplace.desk.show_hint", StewardDesk.SHOW_TICKS / 20), ChatFormatting.GRAY)), p -> {
			if (StewardDesk.show(level, hall, p, id)) {
				Chat.actionBar(p, Component.translatable("message.aliveworkplace.steward.desk.shown", proposal.name(),
					VillageHallScreen.where(hall, proposal.placement().origin())).withStyle(ChatFormatting.AQUA));
				p.closeContainer();
			}
		});
		if (!proposal.upgrade()) {
			menu.button(SPOT, VillageHallScreen.icon(Items.COMPASS, Component.translatable("screen.aliveworkplace.desk.spot"), ChatFormatting.WHITE,
				VillageHallScreen.line("screen.aliveworkplace.desk.spot_hint", ChatFormatting.GRAY)), p -> {
				if (StewardDesk.anotherSpot(level, hall, p, id)) {
					Chat.actionBar(p, Component.translatable("message.aliveworkplace.steward.desk.spot", steward.getDisplayName()).withStyle(ChatFormatting.AQUA));
				} else {
					refuse(p);
				}
				renderProposal(menu, level, hall, id);
				menu.broadcastChanges();
			});
		}
		menu.button(STYLE, VillageHallScreen.icon(Items.PAINTING, Component.translatable("screen.aliveworkplace.desk.style"), ChatFormatting.WHITE,
			VillageHallScreen.line("screen.aliveworkplace.desk.style_hint", ChatFormatting.GRAY)), p -> {
			if (StewardDesk.anotherStyle(level, hall, p, id)) {
				Chat.actionBar(p, Component.translatable("message.aliveworkplace.steward.desk.style",
					StewardDesk.of(level, hall).get(id).map(StewardDesk.Proposal::name).orElse(proposal.name())).withStyle(ChatFormatting.AQUA));
			} else if (!StewardDesk.mayUse(level, hall, p)) {
				refuse(p);
			} else {
				Chat.actionBar(p, Component.translatable("message.aliveworkplace.steward.desk.no_style").withStyle(ChatFormatting.YELLOW));
			}
			renderProposal(menu, level, hall, id);
			menu.broadcastChanges();
		});
	}

	private static void approved(ServerLevel level, ServerPlayer player, Component message) {
		Chat.actionBar(player, message.copy().withStyle(ChatFormatting.GREEN));
		level.playSound(null, player.blockPosition(), SoundEvents.VILLAGER_YES, SoundSource.NEUTRAL, 0.8f, 1f);
	}

	private static void refuse(ServerPlayer player) {
		Chat.actionBar(player, Component.translatable("message.aliveworkplace.steward.desk.not_allowed").withStyle(ChatFormatting.RED));
	}

	private static void rerender(ChoiceMenu menu, ServerLevel level, BlockPos hall) {
		VillageHallScreen.renderAdvice(menu, level, hall);
		menu.broadcastChanges();
	}

	private StewardDeskPage() {
	}
}
