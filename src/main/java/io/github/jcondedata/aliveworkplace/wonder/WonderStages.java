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
 * Stone as their bench (so the chests round the stone are the stockpile). Only the last stage with a part on the
 * Wonder's floor levels the ground round it, and only with those parts (35.8a). A stage starts only when every part of the one before is done and its own goods are dedicated (35.7,
 * {@link WonderGoods}): the checks read the saved sites alone, so the order holds through a restart at any tick.
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
				Dedications.tick(level);
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
	 * Begins the Wonder of {@code plan} at {@code at}: lays the Foundation Stone at {@code stone}, records the Wonder with
	 * {@code builder}'s crew lead, and its first stage starts as soon as its goods are dedicated (35.7: straight away if
	 * the stockpile holds them already, by day); announced to every player and written in the village's chronicle.
	 */
	public static WonderSite begin(ServerLevel level, WonderPlanItem.Plan plan, BlueprintData.Placement at, BlockPos stone, Villager builder,
								   @Nullable UUID owner, String ownerName) {
		BlockPos hall = plan.hall().pos();
		WonderSite site = WonderManager.get(level).begin(plan.wonder(), hall, owner, ownerName, Chronicle.day(level));
		level.setBlockAndUpdate(stone, ModBlocks.FOUNDATION_STONE.defaultBlockState());
		level.playSound(null, stone, SoundEvents.STONE_PLACE, SoundSource.BLOCKS, 1f, 0.6f);
		// 35.5: the Master Architect leads the crew when there is one; else the builder it was handed to.
		UUID lead = WonderCrews.lead(level, site, List.of(builder.getUUID()), builder.getUUID());
		WonderManager manager = WonderManager.get(level);
		manager.place(site.id(), at, stone);
		if (lead != null) {
			manager.setBuilder(site.id(), lead);
		}
		Component name = Wonders.name(plan.wonder());
		Component village = VillageHalls.name(level, hall);
		Component who = ownerName == null || ownerName.isEmpty() ? builder.getDisplayName() : Component.literal(ownerName);
		level.getServer().getPlayerList().broadcastSystemMessage(Component.translatable("message.aliveworkplace.wonder.announce", who, name, village)
			.withStyle(ChatFormatting.GOLD), false);
		Chronicle.atHall(level, hall, Chronicle.Kind.WONDER, Component.translatable("chronicle.aliveworkplace.wonder.begun", who, name));
		WonderGoods.check(level, manager.site(site.id()).orElseThrow()); // stage I's goods already in: dedicated now
		return manager.site(site.id()).orElseThrow();
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
			text.append("\n  ").append(Component.translatable("command.aliveworkplace.wonders.finished", site.finished().get()).withStyle(ChatFormatting.GREEN));
			return text.append("\n  ").append(standingLine(site)); // 35.8: how much of it stands, and whether its powers are off
		}
		if (wonder == null || site.stage() < 1 || site.stage() > wonder.stages().size()) {
			return text.append("\n  ").append(Component.translatable("command.aliveworkplace.wonders.stage_unknown", site.stage()).withStyle(ChatFormatting.GRAY));
		}
		int percent = Math.round(stageProgress(level, site) * 100);
		text.append("\n  ").append(Component.translatable("message.aliveworkplace.wonder.status.stage", site.stage(), wonder.stages().size(),
			wonder.stages().get(site.stage() - 1).nameText(), percent).withStyle(ChatFormatting.YELLOW));
		// 35.5: the crew and its lead
		text.append("\n  ").append(Component.translatable("message.aliveworkplace.wonder.status.crew", WonderCrews.leadName(level, site),
			WonderCrews.crew(level, site).size(), WonderCrews.limit(level, site)).withStyle(ChatFormatting.AQUA));
		goodsText(level, site, text);
		List<Line> lines = site.started() ? materials(level, site, pos) : List.of();
		if (!site.started()) {
			// waiting for its goods: no blocks to bring yet
		} else if (lines.isEmpty()) {
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
		// B462: the Wonder's id for /workplace cancel, which works at any stage (also while it waits for its goods)
		text.append("\n  ").append(Component.translatable("message.aliveworkplace.wonder.status.cancel").withStyle(s -> s.withColor(ChatFormatting.RED).withUnderlined(true)
			.withClickEvent(new net.minecraft.network.chat.ClickEvent(net.minecraft.network.chat.ClickEvent.Action.SUGGEST_COMMAND, "/workplace cancel " + site.id()))
			.withHoverEvent(new net.minecraft.network.chat.HoverEvent(net.minecraft.network.chat.HoverEvent.Action.SHOW_TEXT,
				Component.translatable("message.aliveworkplace.wonder.status.cancel_hover")))));
		return text;
	}

	/**
	 * A finished Wonder's upkeep line (35.8), for the stone and the hall: "Standing: 100% (counted on day 12). Its powers are
	 * on." or, under {@code wonderStanding}, "Damaged: 82% stands (90% needed). Its powers have stopped until the builders
	 * repair it."
	 */
	public static Component standingLine(WonderSite site) {
		WonderSite.Kept kept = site.kept();
		if (kept.damaged()) {
			return Component.translatable("message.aliveworkplace.wonder.status.damaged", kept.standing(), WonderUpkeep.STANDING).withStyle(ChatFormatting.RED);
		}
		return (kept.counted() > 0
			? Component.translatable("message.aliveworkplace.wonder.status.standing", kept.standing(), kept.counted())
			: Component.translatable("message.aliveworkplace.wonder.status.standing_new")).withStyle(ChatFormatting.DARK_GREEN);
	}

	/**
	 * The stone's goods lines (35.7): while the stage waits, every line ("5 of 8 Bread · Chef", or "no Chef in the
	 * village"), what's left to do and how to hand goods in; once dedicated, that it is.
	 */
	static void goodsText(ServerLevel level, WonderSite site, MutableComponent text) {
		List<WonderGoods.Line> lines = WonderGoods.lines(level, site);
		if (site.started()) {
			if (!WonderGoods.goods(site).isEmpty()) {
				text.append("\n  ").append(Component.translatable("message.aliveworkplace.wonder.goods.dedicated").withStyle(ChatFormatting.DARK_GREEN));
			}
			return;
		}
		boolean all = lines.stream().allMatch(WonderGoods.Line::full);
		text.append("\n  ").append((all
			? Component.translatable(WonderGoods.isDay(level) ? "message.aliveworkplace.wonder.goods.all_in" : "message.aliveworkplace.wonder.goods.morning")
			: Component.translatable("message.aliveworkplace.wonder.goods.header", SupplyContainers.RADIUS)).withStyle(all ? ChatFormatting.GREEN : ChatFormatting.GOLD));
		for (WonderGoods.Line l : lines) {
			Wonder.Good good = l.good();
			MutableComponent line;
			if (good.found()) {
				// 35.7a/b: where it's found, and the rare worker who'd find it: wanted, searching, or brought it
				line = Component.translatable("message.aliveworkplace.wonder.goods.found_line", l.in(), l.need(), WonderGoods.what(good), WonderGoods.hint(good));
				if (good.helper().isPresent()) {
					Component who = WonderGoods.helperName(level, site.hall(), good.helper().get());
					if (l.brought()) {
						line.append(Component.translatable("message.aliveworkplace.wonder.goods.helper_brought", who).withStyle(ChatFormatting.GOLD));
					} else if (!l.full() && good.helper().get().condition().met(level, site.hall())) {
						line.append(Component.translatable("message.aliveworkplace.wonder.goods.helper_searching", who,
							Math.min(good.helper().get().days(), WonderGoods.searchedDays(level, site, l) + 1), good.helper().get().days()).withStyle(ChatFormatting.AQUA));
					} else if (!l.full()) {
						line.append(Component.translatable("message.aliveworkplace.wonder.goods.helper_wanted", who, good.helper().get().days()).withStyle(ChatFormatting.GRAY));
					}
				}
			} else {
				line = Component.translatable("message.aliveworkplace.wonder.goods.line", l.in(), l.need(), WonderGoods.what(good), WonderGoods.job(good));
				if (!l.full() && !WonderGoods.hasMaker(level, site.hall(), good.job())) {
					line.append(Component.translatable("message.aliveworkplace.wonder.goods.no_maker", WonderGoods.job(good)).withStyle(ChatFormatting.RED));
				}
			}
			text.append("\n    ").append(line.withStyle(l.full() ? ChatFormatting.DARK_GREEN : ChatFormatting.YELLOW));
		}
		if (!all) {
			text.append("\n  ").append(Component.translatable("message.aliveworkplace.wonder.goods.how").withStyle(ChatFormatting.GRAY));
		}
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
	 * now for {@code builder} (may be null: the village's builders pick it up), its goods or not (commands and tests; a
	 * player's plan goes through {@link #begin}, whose stage waits for its goods). Returns the stage's build sites.
	 */
	public static List<BuildSite> start(ServerLevel level, UUID id, BlueprintData.Placement placement, BlockPos stone, @Nullable UUID builder) {
		WonderManager manager = WonderManager.get(level);
		manager.place(id, placement, stone);
		manager.setDedicated(id, true);
		return startStage(level, manager.site(id).orElseThrow(), builder);
	}

	/** Whether {@code part} stands on the Wonder's floor (its bottom at the Wonder's y = 0). */
	static boolean onFloor(Wonder.Part part) {
		return part.offset().getY() <= 0;
	}

	/**
	 * The stage whose parts level the ground round the Wonder (35.8a): the last one with a part on the Wonder's floor,
	 * so the ground is tidied once, after the last building done at ground level (0: none has one).
	 */
	static int levellingStage(Wonder wonder) {
		for (int stage = wonder.stages().size(); stage >= 1; stage--) {
			if (wonder.stages().get(stage - 1).parts().stream().anyMatch(WonderStages::onFloor)) {
				return stage;
			}
		}
		return 0;
	}

	/** Creates the build sites of {@code site}'s current stage, and records them on the Wonder. */
	static List<BuildSite> startStage(ServerLevel level, WonderSite site, @Nullable UUID builder) {
		Optional<Wonder> wonder = Optional.ofNullable(Wonders.loaded().get(site.wonder()));
		if (wonder.isEmpty() || site.placement().isEmpty() || site.stage() < 1 || site.stage() > wonder.get().stages().size()) {
			return List.of();
		}
		BlueprintData.Placement at = site.placement().get();
		int levelling = levellingStage(wonder.get());
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
			// 35.8a: only a part on the Wonder's floor levels the ground round it. A part higher up (the folly's dome)
			// once "levelled" round its own bottom, filling the air beside the tower with a band of dirt.
			build.setLevelGround(site.stage() == levelling && onFloor(part));
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

	/**
	 * What the owner reads when {@code who} takes up a Wonder part from their queue (B678): the Wonder and its stage,
	 * "Ann is starting on a part of stage 1 of the Test Folly (Foundations) next.", never the part's raw structure name.
	 * Empty when {@code part} isn't a Wonder's or its Wonder can't be found.
	 */
	public static Optional<Component> partNextLine(ServerLevel level, BuildSite part, Component who) {
		if (part.wonder() == null) {
			return Optional.empty();
		}
		Optional<WonderSite> site = WonderManager.get(level).site(part.wonder());
		Wonder wonder = site.map(s -> Wonders.loaded().get(s.wonder())).orElse(null);
		if (wonder == null || site.get().stage() < 1 || site.get().stage() > wonder.stages().size()) {
			return Optional.empty();
		}
		int stage = site.get().stage();
		return Optional.of(Component.translatable("message.aliveworkplace.wonder.part_next", who, stage, wonder.nameText(),
			wonder.stages().get(stage - 1).nameText()));
	}

	/**
	 * What the owner is told when {@code who} finishes {@code part}, a part of a Wonder still going up (B138): with other
	 * parts of its stage still open, how many of the stage's parts stand; the stage's last part, that the stage is
	 * finished (the stone then says which stage is next). Empty for the last part of the last stage, whose finish is
	 * announced to everyone (WonderCelebration), and for a part of a Wonder that's gone or no longer loaded.
	 */
	public static Optional<Component> partFinishedLine(ServerLevel level, BuildSite part, Component who) {
		if (part.wonder() == null) {
			return Optional.empty();
		}
		Optional<WonderSite> site = WonderManager.get(level).site(part.wonder());
		Wonder wonder = site.map(s -> Wonders.loaded().get(s.wonder())).orElse(null);
		if (wonder == null || site.get().stage() < 1 || site.get().stage() > wonder.stages().size()) {
			return Optional.empty();
		}
		WonderSite s = site.get();
		BuildSiteManager sites = BuildSiteManager.get(level);
		int total = Math.max(1, s.parts().size());
		int standing = 0;
		for (UUID id : s.parts()) {
			BuildSite build = sites.get(id);
			if (id.equals(part.id()) || build == null || build.isDone()) {
				standing++; // (a part whose site is gone counts as built, as in stageDone)
			}
		}
		Component stageName = wonder.stages().get(s.stage() - 1).nameText();
		if (standing < total) {
			return Optional.of(Component.translatable("message.aliveworkplace.wonder.part_built", who, wonder.nameText(), s.stage(),
				stageName, standing, total));
		}
		if (s.stage() == wonder.stages().size()) {
			return Optional.empty(); // the Wonder's finished: everyone hears it from WonderCelebration
		}
		return Optional.of(Component.translatable("message.aliveworkplace.wonder.stage_built", who, s.stage(), wonder.stages().size(),
			wonder.nameText(), stageName));
	}

	/**
	 * Moves every Wonder of {@code level} whose stage is done on to its next stage, or finishes it; a stage waiting for its
	 * goods posts them and is dedicated once they're in, by day (35.7).
	 */
	public static void tick(ServerLevel level) {
		if (!Wonders.ENABLED) {
			return; // switched off: the Wonders wait, saved, for the switch to come back on
		}
		WonderManager manager = WonderManager.get(level);
		for (WonderSite site : manager.sites()) {
			if (!site.goingUp() || site.placement().isEmpty()) {
				continue;
			}
			if (!site.started()) {
				WonderGoods.search(level, site, INTERVAL); // 35.7b: the rare workers search for the found goods
				WonderGoods.check(level, manager.site(site.id()).orElse(site));
			} else if (!site.parts().isEmpty() && stageDone(level, site)) {
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
			long day = io.github.jcondedata.aliveworkplace.hall.Chronicle.day(level);
			manager.finish(site.id(), day);
			WonderCelebration.finished(level, manager.site(site.id()).orElseThrow(), day); // 35.8: told, the festival, the chronicles, its powers on
			return; // (its crew is remembered while the server runs: a member's leftovers still go back to the stone)
		}
		// 35.5: the next stage's lead is the Master Architect, else the highest-level builder of the crew.
		builder = WonderCrews.lead(level, site, WonderCrews.candidates(site, builder), builder);
		if (builder != null) {
			manager.setBuilder(site.id(), builder);
		}
		// 35.7: the next stage waits for its goods (dedicated at once if they're in already, by day)
		manager.setStage(site.id(), next);
		manager.setParts(site.id(), List.of());
		manager.setDedicated(site.id(), false);
		manager.setDelivered(site.id(), List.of());
		ServerPlayer owner = site.owner().map(o -> level.getServer().getPlayerList().getPlayer(o)).orElse(null);
		if (owner != null) {
			Chat.system(owner, Component.translatable("message.aliveworkplace.wonder.stage_begun", next, wonder.stages().size(), wonder.nameText(),
				wonder.stages().get(next - 1).nameText()).withStyle(ChatFormatting.GREEN));
		}
		WonderGoods.check(level, manager.site(site.id()).orElseThrow());
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
		WonderGoods.handBack(level, site); // 35.7: the goods handed in for a stage not yet dedicated
		site.stone().ifPresent(stone -> io.github.jcondedata.aliveworkplace.work.Requests.clearAt(level, stone));
		Dedications.end(id);
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
