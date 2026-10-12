package io.github.jcondedata.aliveworkplace.wonder;

import io.github.jcondedata.aliveworkplace.blueprint.BlueprintData;
import io.github.jcondedata.aliveworkplace.blueprint.BlueprintStyles;
import io.github.jcondedata.aliveworkplace.build.BuildSite;
import io.github.jcondedata.aliveworkplace.build.BuildSiteManager;
import io.github.jcondedata.aliveworkplace.build.Builders;
import io.github.jcondedata.aliveworkplace.hall.VillageBanners;
import io.github.jcondedata.aliveworkplace.hall.VillageHalls;
import io.github.jcondedata.aliveworkplace.platform.Platform;
import io.github.jcondedata.aliveworkplace.blueprint.BlueprintItem;
import io.github.jcondedata.aliveworkplace.build.Friends;
import io.github.jcondedata.aliveworkplace.build.MaterialFamilies;
import io.github.jcondedata.aliveworkplace.build.SupplyContainers;
import io.github.jcondedata.aliveworkplace.hall.Chronicle;
import io.github.jcondedata.aliveworkplace.mc.Chat;
import io.github.jcondedata.aliveworkplace.mc.Players;
import io.github.jcondedata.aliveworkplace.mc.Rules;
import io.github.jcondedata.aliveworkplace.registry.ModBlocks;
import io.github.jcondedata.aliveworkplace.registry.ModGameRules;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Containers;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import org.jetbrains.annotations.Nullable;

/**
 * A Wonder's stages as build sites (35.4). Each stage's parts become ordinary {@link BuildSite}s, placed with the
 * Wonder's turn and mirror at their offsets, pointing back at the Wonder ({@link BuildSite#wonder}) with the Foundation
 * Stone as their bench (so the chests round the stone are the stockpile). Only the last stage levels the ground round
 * it. A stage starts only when every part of the one before is done: the check reads the saved sites alone, so the
 * order holds through a restart at any tick.
 */
public final class WonderStages {
	/** Ticks between checks for a finished stage. */
	static final int INTERVAL = 20;
	/** Most material lines the stone's status shows. */
	static final int MAX_LINES = 8;
	/** Where the stone may go, tried in turn: just outside the front corner, then along and in front of it (template blocks). */
	private static final List<BlockPos> STONE_SPOTS = List.of(new BlockPos(-1, 0, -1), new BlockPos(-2, 0, -1), new BlockPos(-1, 0, -2),
		new BlockPos(-2, 0, -2), new BlockPos(-1, 1, -1));

	public static void init() {
		Platform.get().onLevelTick(level -> {
			if (level.getGameTime() % INTERVAL == 0) {
				tick(level);
			}
		});
		Platform.get().allowBreakBlock((level, player, pos, state) -> !state.is(ModBlocks.FOUNDATION_STONE) || !(level instanceof ServerLevel server)
			|| breakStone(server, player, pos));
	}

	// ---------------------------------------------------------------- handing the plan over

	/** The Wonder whose Foundation Stone is at {@code pos}, if any. */
	public static Optional<WonderSite> atStone(ServerLevel level, BlockPos pos) {
		return WonderManager.get(level).sites().stream().filter(s -> s.stone().map(pos::equals).orElse(false)).findFirst();
	}

	/**
	 * Where the Foundation Stone goes for a Wonder at {@code at}: just outside its front corner (template block -1, 0, -1,
	 * turned and mirrored with it), else the next free spot round there; empty when all are taken.
	 */
	public static Optional<BlockPos> stoneSpot(ServerLevel level, BlueprintData.Placement at) {
		for (BlockPos rel : STONE_SPOTS) {
			BlockPos pos = at.origin().offset(StructureTemplate.transform(rel, at.mirror(), at.rotation(), BlockPos.ZERO));
			if (level.getBlockState(pos).canBeReplaced() && level.getFluidState(pos).isEmpty()) {
				return Optional.of(pos);
			}
		}
		return Optional.empty();
	}

	/**
	 * {@code player} right-clicked the builder {@code villager} with a Wonder Plan: if the plan is placed, the builder
	 * works for this village and the caps still allow it, the Foundation Stone goes down, the Wonder is begun (announced
	 * to the server and written in the chronicle) and its first stage goes in the builder's queue. Otherwise says why.
	 * The requirements were checked when the plans were drawn up; the caps and the switch are checked again.
	 */
	public static InteractionResult handOver(ServerPlayer player, Villager villager, ItemStack stack) {
		ServerLevel level = Players.level(player);
		Optional<WonderPlanItem.Plan> plan = WonderPlanItem.plan(stack);
		Optional<BlueprintData> data = BlueprintItem.data(stack);
		if (plan.isEmpty() || data.isEmpty()) {
			return InteractionResult.PASS;
		}
		Component name = Wonders.name(plan.get().wonder());
		if (!Friends.mayCommand(player, villager)) {
			return refuse(player, Component.translatable("message.aliveworkplace.wonder.not_your_builder", villager.getDisplayName()));
		}
		if (data.get().placement().isEmpty()) {
			return refuse(player, Component.translatable("message.aliveworkplace.wonder.not_placed", name));
		}
		BlueprintData.Placement at = data.get().placement().get();
		BlockPos hall = plan.get().hall().pos();
		Optional<Component> capped = plan.get().hall().dimension().equals(level.dimension())
			? Wonders.capRefusal(level, hall, plan.get().wonder()) : Optional.of(Component.translatable("message.aliveworkplace.wonder_plan.elsewhere", plan.get().village()));
		if (capped.isPresent()) {
			return refuse(player, capped.get());
		}
		Optional<BlockPos> bench = Builders.benchPos(villager);
		if (bench.isEmpty()) {
			return refuse(player, Component.translatable("message.aliveworkplace.assign.no_bench"));
		}
		if (bench.get().distSqr(hall) > (double) VillageHalls.RADIUS * VillageHalls.RADIUS) {
			return refuse(player, Component.translatable("message.aliveworkplace.wonder.not_of_village", villager.getDisplayName(), plan.get().village()));
		}
		Optional<Component> placeRefusal = data.get().size().isPresent()
			? WonderPlans.placeRefusal(level, plan.get(), at, data.get().size().get()) : Optional.empty();
		if (placeRefusal.isPresent()) {
			return refuse(player, placeRefusal.get());
		}
		Optional<BlockPos> stone = stoneSpot(level, at);
		if (stone.isEmpty()) {
			return refuse(player, Component.translatable("message.aliveworkplace.wonder.no_room", name));
		}
		begin(level, plan.get(), at, stone.get(), villager, player.getUUID(), player.getGameProfile().getName());
		Friends.hire(player, villager);
		if (!player.getAbilities().instabuild) {
			stack.shrink(1);
		}
		level.playSound(null, villager, SoundEvents.VILLAGER_YES, SoundSource.NEUTRAL, 1f, 1f);
		Chat.system(player, Component.translatable("message.aliveworkplace.wonder.begun", villager.getDisplayName(), name,
			Wonders.loaded().get(plan.get().wonder()).stages().get(0).nameText(), SupplyContainers.RADIUS).withStyle(ChatFormatting.GREEN));
		return InteractionResult.SUCCESS;
	}

	private static InteractionResult refuse(Player player, Component why) {
		Chat.system(player, why.copy().withStyle(ChatFormatting.RED));
		return InteractionResult.CONSUME;
	}

	/**
	 * Begins the Wonder of {@code plan} at {@code at}: lays the Foundation Stone at {@code stone}, records the Wonder and
	 * starts its first stage for {@code builder}; announced to every player and written in the village's chronicle.
	 */
	public static WonderSite begin(ServerLevel level, WonderPlanItem.Plan plan, BlueprintData.Placement at, BlockPos stone, Villager builder,
								   @Nullable UUID owner, String ownerName) {
		BlockPos hall = plan.hall().pos();
		WonderSite site = WonderManager.get(level).begin(plan.wonder(), hall, owner, ownerName, Chronicle.day(level));
		level.setBlockAndUpdate(stone, ModBlocks.FOUNDATION_STONE.defaultBlockState());
		level.playSound(null, stone, SoundEvents.STONE_PLACE, SoundSource.BLOCKS, 1f, 0.6f);
		// 35.5: the Master Architect leads the crew when there is one; else the builder it was handed to.
		UUID lead = WonderCrews.lead(level, site, List.of(builder.getUUID()), builder.getUUID());
		start(level, site.id(), at, stone, lead);
		Component name = Wonders.name(plan.wonder());
		Component village = VillageHalls.name(level, hall);
		Component who = ownerName == null || ownerName.isEmpty() ? builder.getDisplayName() : Component.literal(ownerName);
		level.getServer().getPlayerList().broadcastSystemMessage(Component.translatable("message.aliveworkplace.wonder.announce", who, name, village)
			.withStyle(ChatFormatting.GOLD), false);
		Chronicle.atHall(level, hall, Chronicle.Kind.WONDER, Component.translatable("chronicle.aliveworkplace.wonder.begun", who, name));
		return WonderManager.get(level).site(site.id()).orElseThrow();
	}

	// ---------------------------------------------------------------- the stone

	/** A player breaks a Foundation Stone: the owner or an operator, sneaking, ends its Wonder; anyone else is refused. */
	public static boolean breakStone(ServerLevel level, Player player, BlockPos pos) {
		Optional<WonderSite> found = atStone(level, pos);
		if (found.isEmpty() || found.get().done()) {
			return true; // a stray stone, or a finished Wonder's: just a stone
		}
		WonderSite site = found.get();
		Component name = Wonders.name(site.wonder());
		boolean owner = site.owner().map(player.getUUID()::equals).orElse(false);
		if (!owner && !player.hasPermissions(2)) {
			Chat.actionBar(player, Component.translatable("message.aliveworkplace.wonder.stone_not_yours", name,
				site.ownerName().isEmpty() ? "?" : site.ownerName()).withStyle(ChatFormatting.RED));
			return false;
		}
		if (!player.isShiftKeyDown()) {
			Chat.actionBar(player, Component.translatable("message.aliveworkplace.wonder.stone_sneak", name).withStyle(ChatFormatting.YELLOW));
			return false;
		}
		cancel(level, site.id());
		return true;
	}

	/**
	 * What right-clicking the stone at {@code pos} says: the Wonder and its village, the stage and how far along it is,
	 * and the stage's material list with what the chests round the stone are short of.
	 */
	public static Component statusText(ServerLevel level, BlockPos pos) {
		Optional<WonderSite> found = atStone(level, pos);
		if (found.isEmpty()) {
			return Component.translatable("message.aliveworkplace.wonder.status.none").withStyle(ChatFormatting.GRAY);
		}
		WonderSite site = found.get();
		Wonder wonder = Wonders.loaded().get(site.wonder());
		MutableComponent text = Component.empty();
		text.append(Component.translatable("message.aliveworkplace.wonder.status.header", Wonders.name(site.wonder()), VillageHalls.name(level, site.hall()))
			.withStyle(ChatFormatting.GOLD));
		if (site.done()) {
			return text.append("\n  ").append(Component.translatable("command.aliveworkplace.wonders.finished", site.finished().get()).withStyle(ChatFormatting.GREEN));
		}
		if (wonder == null || site.stage() < 1 || site.stage() > wonder.stages().size()) {
			return text.append("\n  ").append(Component.translatable("command.aliveworkplace.wonders.stage_unknown", site.stage()).withStyle(ChatFormatting.GRAY));
		}
		int percent = Math.round(stageProgress(level, site) * 100);
		text.append("\n  ").append(Component.translatable("message.aliveworkplace.wonder.status.stage", site.stage(), wonder.stages().size(),
			wonder.stages().get(site.stage() - 1).nameText(), percent).withStyle(ChatFormatting.YELLOW));
		// 35.5: the crew and its lead
		text.append("\n  ").append(Component.translatable("message.aliveworkplace.wonder.status.crew", WonderCrews.leadName(level, site),
			WonderCrews.crew(level, site).size(), WonderCrews.limit(site)).withStyle(ChatFormatting.AQUA));
		List<Line> lines = materials(level, site, pos);
		if (lines.isEmpty()) {
			text.append("\n  ").append(Component.translatable("message.aliveworkplace.wonder.status.nothing_left").withStyle(ChatFormatting.GREEN));
		} else {
			int shortOf = (int) lines.stream().filter(l -> l.toBring() > 0).count();
			text.append("\n  ").append((shortOf == 0
				? Component.translatable("message.aliveworkplace.wonder.status.all_there", SupplyContainers.RADIUS)
				: Component.translatable("message.aliveworkplace.wonder.status.materials", SupplyContainers.RADIUS))
				.withStyle(shortOf == 0 ? ChatFormatting.GREEN : ChatFormatting.GRAY));
			for (int i = 0; i < Math.min(MAX_LINES, lines.size()); i++) {
				Line l = lines.get(i);
				text.append("\n    ").append(l.toBring() > 0
					? Component.translatable("message.aliveworkplace.wonder.status.short", l.need(), l.item().getDescription(), l.toBring()).withStyle(ChatFormatting.YELLOW)
					: Component.translatable("message.aliveworkplace.wonder.status.there", l.need(), l.item().getDescription()).withStyle(ChatFormatting.DARK_GREEN));
			}
			if (lines.size() > MAX_LINES) {
				text.append("\n    ").append(Component.translatable("tooltip.aliveworkplace.blueprint.more", lines.size() - MAX_LINES).withStyle(ChatFormatting.DARK_GRAY));
			}
		}
		text.append("\n  ").append(site.called()
			? Component.translatable("message.aliveworkplace.wonder.status.called").withStyle(ChatFormatting.DARK_AQUA)
			: Component.translatable("message.aliveworkplace.wonder.status.call").withStyle(s -> s.withColor(ChatFormatting.AQUA).withUnderlined(true)
				.withClickEvent(new net.minecraft.network.chat.ClickEvent(net.minecraft.network.chat.ClickEvent.Action.RUN_COMMAND, "/workplace wonders call"))
				.withHoverEvent(new net.minecraft.network.chat.HoverEvent(net.minecraft.network.chat.HoverEvent.Action.SHOW_TEXT,
					Component.translatable("message.aliveworkplace.wonder.status.call_hover")))));
		text.append("\n  ").append(Component.translatable("message.aliveworkplace.wonder.status.abandon").withStyle(ChatFormatting.DARK_GRAY));
		return text;
	}

	/** One line of a stage's material list: the material, how many the stage still needs, how many the chests lack. */
	public record Line(Item item, int need, int toBring) {
	}

	/**
	 * What {@code site}'s current stage still needs (blocks not standing yet, by material family), and how much of each
	 * the chests within {@link SupplyContainers#RADIUS} of {@code stone} lack; still-to-bring first, biggest first.
	 */
	public static List<Line> materials(ServerLevel level, WonderSite site, BlockPos stone) {
		BuildSiteManager sites = BuildSiteManager.get(level);
		Map<Item, Integer> need = new LinkedHashMap<>();
		for (UUID part : site.parts()) {
			BuildSite build = sites.get(part);
			if (build == null || build.isDone()) {
				continue;
			}
			io.github.jcondedata.aliveworkplace.build.BuildPlan plan = build.plan(level);
			if (plan != null) {
				Builders.remainingNeed(level, build, plan).forEach((item, n) -> need.merge(item, n, Integer::sum));
			}
		}
		List<BlockPos> chests = SupplyContainers.find(level, stone, null);
		boolean free = Rules.on(level, ModGameRules.FREE_MATERIALS);
		List<Line> lines = new ArrayList<>();
		need.forEach((item, n) -> {
			long have = 0;
			for (Item member : MaterialFamilies.accepted(item)) {
				have += SupplyContainers.count(level, chests, member);
			}
			lines.add(new Line(item, n, free ? 0 : (int) Math.max(0, n - have)));
		});
		lines.sort(java.util.Comparator.comparing((Line l) -> l.toBring() == 0).thenComparing(Line::need, java.util.Comparator.reverseOrder())
			.thenComparing(l -> net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(l.item())));
		return lines;
	}

	/** How far along {@code site}'s current stage is, 0 to 1: the mean of its parts' progress (a part whose site is gone is done). */
	public static float stageProgress(ServerLevel level, WonderSite site) {
		if (site.parts().isEmpty()) {
			return 0f;
		}
		BuildSiteManager sites = BuildSiteManager.get(level);
		float sum = 0;
		for (UUID part : site.parts()) {
			BuildSite build = sites.get(part);
			io.github.jcondedata.aliveworkplace.build.BuildPlan plan = build == null || build.isDone() ? null : build.plan(level);
			sum += build == null || build.isDone() ? 1f : plan == null ? 0f : build.progress(plan);
		}
		return sum / site.parts().size();
	}

	// ---------------------------------------------------------------- stages

	/**
	 * Puts the Wonder {@code id} down at {@code placement} with its stone at {@code stone} and starts its current stage
	 * for {@code builder} (may be null: the village's builders pick it up). Returns the stage's build sites.
	 */
	public static List<BuildSite> start(ServerLevel level, UUID id, BlueprintData.Placement placement, BlockPos stone, @Nullable UUID builder) {
		WonderManager manager = WonderManager.get(level);
		manager.place(id, placement, stone);
		return startStage(level, manager.site(id).orElseThrow(), builder);
	}

	/** Creates the build sites of {@code site}'s current stage, and records them on the Wonder. */
	static List<BuildSite> startStage(ServerLevel level, WonderSite site, @Nullable UUID builder) {
		Optional<Wonder> wonder = Optional.ofNullable(Wonders.loaded().get(site.wonder()));
		if (wonder.isEmpty() || site.placement().isEmpty() || site.stage() < 1 || site.stage() > wonder.get().stages().size()) {
			return List.of();
		}
		BlueprintData.Placement at = site.placement().get();
		boolean last = site.stage() == wonder.get().stages().size();
		BuildSiteManager sites = BuildSiteManager.get(level);
		List<BuildSite> made = new ArrayList<>();
		UUID owner = site.owner().orElse(new UUID(0L, 0L));
		String colours = wonder.get().villageColours() ? VillageBanners.wonderStyle(VillageBanners.of(level, site.hall())) : null;
		for (Wonder.Part part : wonder.get().stages().get(site.stage() - 1).parts()) {
			BlockPos origin = at.origin().offset(StructureTemplate.transform(part.offset(), at.mirror(), at.rotation(), BlockPos.ZERO));
			BuildSite build = sites.create(owner, site.ownerName(), BlueprintStyles.styled(part.part(), colours),
				new BlueprintData.Placement(at.dimension(), origin, at.rotation(), at.mirror()));
			build.setWonder(site.id());
			site.stone().ifPresent(build::setBench);
			build.setLevelGround(last);
			build.setFoundation(site.stage() == 1); // later stages stand on the stage below
			if (builder != null) {
				build.setBuilder(builder);
				build.setQueued(true); // in the builder's queue: picked up when they finish what they're on (Builders.activeSite)
			}
			made.add(build);
		}
		WonderManager.get(level).setParts(site.id(), made.stream().map(BuildSite::id).toList());
		if (builder != null) {
			WonderManager.get(level).setBuilder(site.id(), builder);
		}
		return made;
	}

	/** Whether every part of {@code site}'s current stage is done (a part whose site is gone counts as done). */
	public static boolean stageDone(ServerLevel level, WonderSite site) {
		BuildSiteManager sites = BuildSiteManager.get(level);
		for (UUID part : site.parts()) {
			BuildSite build = sites.get(part);
			if (build != null && !build.isDone()) {
				return false;
			}
		}
		return true;
	}

	/** Moves every Wonder of {@code level} whose stage is done on to its next stage, or finishes it. */
	public static void tick(ServerLevel level) {
		if (!Wonders.ENABLED) {
			return; // switched off: the Wonders wait, saved, for the switch to come back on
		}
		WonderManager manager = WonderManager.get(level);
		for (WonderSite site : manager.sites()) {
			if (site.goingUp() && site.placement().isPresent() && !site.parts().isEmpty() && stageDone(level, site)) {
				advance(level, site);
			}
		}
	}

	/** {@code site}'s stage is done: the next one starts (its builders kept), or the Wonder is finished. */
	static void advance(ServerLevel level, WonderSite site) {
		WonderManager manager = WonderManager.get(level);
		Wonder wonder = Wonders.loaded().get(site.wonder());
		BuildSiteManager sites = BuildSiteManager.get(level);
		UUID builder = site.builder().orElse(null); // the one it was handed to (a finished part's site is gone)
		for (UUID part : site.parts()) {
			BuildSite build = sites.get(part);
			if (build != null && build.builder() != null) {
				builder = build.builder();
			}
		}
		int next = site.stage() + 1;
		if (wonder == null || next > wonder.stages().size()) {
			manager.finish(site.id(), io.github.jcondedata.aliveworkplace.hall.Chronicle.day(level));
			return; // (its crew is remembered while the server runs: a member's leftovers still go back to the stone)
		}
		// 35.5: the next stage's lead is the Master Architect, else the highest-level builder of the crew.
		builder = WonderCrews.lead(level, site, WonderCrews.candidates(site, builder), builder);
		manager.setStage(site.id(), next);
		startStage(level, manager.site(site.id()).orElseThrow(), builder);
		ServerPlayer owner = site.owner().map(o -> level.getServer().getPlayerList().getPlayer(o)).orElse(null);
		if (owner != null) {
			Chat.system(owner, Component.translatable("message.aliveworkplace.wonder.stage_begun", next, wonder.stages().size(), wonder.nameText(),
				wonder.stages().get(next - 1).nameText()).withStyle(ChatFormatting.GREEN));
		}
	}

	/**
	 * Ends the Wonder {@code id}: what's built stays, its open build sites end (their builders let go), it's forgotten,
	 * and a fresh Wonder Plan goes to its owner if online, else drops by the stone. Returns the plan given (empty if
	 * there was no such Wonder or it isn't loaded any more).
	 */
	public static ItemStack cancel(ServerLevel level, UUID id) {
		WonderManager manager = WonderManager.get(level);
		BuildSiteManager sites = BuildSiteManager.get(level);
		Optional<WonderSite> found = manager.site(id);
		if (found.isEmpty()) {
			return ItemStack.EMPTY;
		}
		WonderSite site = found.get();
		for (UUID part : site.parts()) {
			BuildSite build = sites.get(part);
			if (build != null && !build.isDone()) {
				Builders.cancel(level, build);
				sites.remove(part);
			}
		}
		manager.remove(id);
		WonderCrews.forget(id);
		Wonder wonder = Wonders.loaded().get(site.wonder());
		if (wonder == null || site.done()) {
			return ItemStack.EMPTY;
		}
		Chronicle.atHall(level, site.hall(), Chronicle.Kind.WONDER, Component.translatable("chronicle.aliveworkplace.wonder.abandoned", wonder.nameText(), site.stage()));
		ItemStack plan = WonderPlanItem.of(level, site.hall(), wonder, VillageHalls.name(level, site.hall()));
		ItemStack given = plan.copy();
		ServerPlayer owner = site.owner().map(o -> level.getServer().getPlayerList().getPlayer(o)).orElse(null);
		if (owner != null) {
			Chat.system(owner, Component.translatable("message.aliveworkplace.wonder.cancelled", wonder.nameText()).withStyle(ChatFormatting.YELLOW));
			if (!owner.getInventory().add(given)) {
				owner.drop(given, false);
			}
		} else {
			BlockPos at = site.stone().orElse(site.placement().map(BlueprintData.Placement::origin).orElse(site.hall()));
			Containers.dropItemStack(level, at.getX() + 0.5, at.getY() + 1, at.getZ() + 0.5, given);
		}
		return plan;
	}

	private WonderStages() {
	}
}
