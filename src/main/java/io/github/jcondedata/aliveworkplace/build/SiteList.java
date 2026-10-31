package io.github.jcondedata.aliveworkplace.build;

import io.github.jcondedata.aliveworkplace.blueprint.Blueprints;
import io.github.jcondedata.aliveworkplace.mc.Chat;
import io.github.jcondedata.aliveworkplace.mc.Rules;
import io.github.jcondedata.aliveworkplace.registry.ModAttachments;
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
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.AABB;
import org.jetbrains.annotations.Nullable;

/**
 * B279: the material list of a build under way. Once a blueprint is handed over the player could only see the 3-4 lines
 * over the builder's head; now a Book and Quill used on the builder (or his Builder's Bench) writes the whole rest of
 * the build as a checklist, and a sneak-right-click with an empty hand puts it all in chat. "The rest" is every step
 * of the site's plan, from the stage it is in to the end, not yet standing ({@link MaterialRules#matches}), furniture
 * and item frames included; what his chests, his and his crew's bags and the village (a storehouse, other workers'
 * spare stock) already hold is taken off, as the builder counts it when he waits for materials.
 */
public final class SiteList {
	/** How far from a bench its builder is looked for when the bench's own build isn't on record. */
	private static final int BENCH_REACH = 48;

	/** The checklist for {@code site}; empty lines when nothing is left to place. */
	public static BlueprintSupplies.Checklist checklist(ServerLevel level, BuildSite site, @Nullable Villager builder) {
		BuildPlan plan = site.plan(level);
		if (plan == null || site.isDeconstruction() || site.isDone()) {
			return new BlueprintSupplies.Checklist(Optional.ofNullable(site.bench()), List.of());
		}
		Map<Item, Integer> need = new LinkedHashMap<>();
		Builders.remainingNeed(level, site, plan).forEach((item, n) -> need.merge(BlueprintSupplies.key(item), n, Integer::sum));
		for (BuildPlan.EntityStep entity : plan.entities()) { // item frames, paintings, armour stands go up last
			if (!BuildEntities.isPresent(level, entity)) {
				need.merge(BlueprintSupplies.key(entity.cost()), 1, Integer::sum);
			}
		}
		Optional<BlockPos> bench = builder != null ? Builders.siteBench(level, builder, site) : Optional.ofNullable(site.bench());
		List<BlockPos> supplies = bench.map(b -> SupplyContainers.find(level, b, plan.supplyExclude())).orElse(List.of());
		List<BuilderBag> bags = bags(level, site, builder);
		Map<Item, Long> elsewhere = builder != null && bench.isPresent() ? BuilderWork.spareElsewhere(level, builder, site, plan, bench.get()) : Map.of();
		boolean free = Rules.on(level, ModGameRules.FREE_MATERIALS);
		List<BlueprintSupplies.Line> lines = new ArrayList<>();
		for (Map.Entry<Item, Integer> e : need.entrySet()) {
			long have = BlueprintSupplies.have(level, supplies, e.getKey()) + elsewhere.getOrDefault(MaterialFamilies.key(e.getKey()), 0L);
			for (Item member : MaterialFamilies.accepted(e.getKey())) {
				for (BuilderBag bag : bags) {
					have += bag.count(member);
				}
			}
			lines.add(new BlueprintSupplies.Line(e.getKey(), e.getValue(), free ? 0 : (int) Math.max(0, e.getValue() - have)));
		}
		BlueprintSupplies.sort(lines);
		return new BlueprintSupplies.Checklist(bench, List.copyOf(lines));
	}

	/** The lead's bag and the crew's: what they carry goes into this build. */
	private static List<BuilderBag> bags(ServerLevel level, BuildSite site, @Nullable Villager builder) {
		List<BuilderBag> bags = new ArrayList<>();
		List<UUID> crew = new ArrayList<>(site.helpers(level.getGameTime()));
		if (site.builder() != null) {
			crew.add(site.builder());
		}
		if (builder != null && !crew.contains(builder.getUUID())) {
			crew.add(builder.getUUID());
		}
		for (UUID id : crew) {
			if (level.getEntity(id) instanceof Villager mate) {
				BuilderBag bag = ModAttachments.BUILDER_BAG.get(mate);
				if (bag != null && !bags.contains(bag)) {
					bags.add(bag);
				}
			}
		}
		return bags;
	}

	/** What the book says under its heading: how far along the build is and what is counted. */
	static Component intro(ServerLevel level, BuildSite site) {
		BuildPlan plan = site.plan(level);
		int percent = plan == null ? 0 : Math.round(site.progress(plan) * 100);
		return Component.translatable("book.aliveworkplace.material_list.site", percent,
			Component.translatable("message.aliveworkplace.status.stage." + site.stage().name().toLowerCase()));
	}

	/**
	 * Why there is no list to write for {@code site} (null: there is one). A finished or idle builder says so plainly,
	 * and so does one taking a building down or one whose every block is already in place.
	 */
	@Nullable
	static Component nothingToList(ServerLevel level, @Nullable BuildSite site, Component who, BlueprintSupplies.Checklist list) {
		if (site == null) {
			return Component.translatable("message.aliveworkplace.site_list.idle", who);
		}
		if (site.plan(level) == null) {
			return Component.translatable("message.aliveworkplace.status.blueprint_missing", site.structure().toString());
		}
		if (site.isDeconstruction()) {
			return Component.translatable("message.aliveworkplace.site_list.taking_down", who);
		}
		if (list.lines().isEmpty()) {
			return Component.translatable("message.aliveworkplace.site_list.all_built", Blueprints.siteName(level, site));
		}
		return null;
	}

	/**
	 * Turns one Book and Quill in {@code bookHand} into the list of {@code builder}'s build (or {@code site}'s, from its
	 * bench, when {@code builder} isn't known). False, the book untouched and the player told why, when there's no list.
	 */
	public static boolean write(ServerLevel level, Player player, @Nullable BuildSite site, @Nullable Villager builder, InteractionHand bookHand) {
		Component who = builder != null ? builder.getDisplayName() : Component.translatable("message.aliveworkplace.status.builder");
		BlueprintSupplies.Checklist list = site == null ? null : checklist(level, site, builder);
		Component nothing = nothingToList(level, site, who, list);
		if (nothing != null) {
			Chat.actionBar(player, nothing.copy().withStyle(ChatFormatting.YELLOW));
			return false;
		}
		Component name = Blueprints.siteName(level, site);
		ItemStack book = MaterialList.book(name, list, intro(level, site), player.getName().getString());
		ItemStack quill = player.getItemInHand(bookHand);
		if (quill.getCount() == 1) {
			player.setItemInHand(bookHand, book);
		} else {
			quill.shrink(1);
			if (!player.getInventory().add(book)) {
				player.drop(book, false);
			}
		}
		level.playSound(null, player.blockPosition(), net.minecraft.sounds.SoundEvents.BOOK_PAGE_TURN, net.minecraft.sounds.SoundSource.PLAYERS, 1f, 1f);
		Chat.actionBar(player, Component.translatable("message.aliveworkplace.blueprint.list_written", name).withStyle(ChatFormatting.GREEN));
		return true;
	}

	/** The whole list as chat text: a heading, a line for each thing still to bring, then one line of what's all there. */
	public static Component chat(ServerLevel level, BuildSite site, @Nullable Villager builder) {
		Component who = builder != null ? builder.getDisplayName() : Component.translatable("message.aliveworkplace.status.builder");
		BlueprintSupplies.Checklist list = checklist(level, site, builder);
		Component nothing = nothingToList(level, site, who, list);
		if (nothing != null) {
			return nothing.copy().withStyle(ChatFormatting.GREEN);
		}
		Component name = Blueprints.siteName(level, site);
		MutableComponent text = Component.empty();
		List<BlueprintSupplies.Line> toBring = list.lines().stream().filter(l -> l.toBring() > 0).toList();
		text.append(toBring.isEmpty()
			? Component.translatable("message.aliveworkplace.site_list.chat_all_there", name).withStyle(ChatFormatting.GREEN)
			: Component.translatable("message.aliveworkplace.site_list.chat_heading", name, // B402: "1 item of 1 kind"
				io.github.jcondedata.aliveworkplace.work.Words.counted("message.aliveworkplace.site_list.items", list.toBring(), list.toBring()),
				io.github.jcondedata.aliveworkplace.work.Words.counted("message.aliveworkplace.site_list.kinds", toBring.size(), toBring.size())).withStyle(ChatFormatting.GOLD));
		for (BlueprintSupplies.Line line : toBring) {
			text.append(Component.literal("\n  "));
			text.append(MaterialList.line(line).copy().withStyle(ChatFormatting.YELLOW));
		}
		List<BlueprintSupplies.Line> there = list.lines().stream().filter(l -> l.toBring() == 0).toList();
		if (!there.isEmpty()) {
			MutableComponent names = Component.empty();
			for (int i = 0; i < there.size(); i++) {
				if (i > 0) {
					names.append(", ");
				}
				names.append(io.github.jcondedata.aliveworkplace.furnish.FurnitureEquivalents.name(there.get(i).item()));
			}
			text.append(Component.literal("\n  "));
			text.append(Component.translatable("message.aliveworkplace.site_list.in_chests", names).withStyle(ChatFormatting.DARK_GREEN));
		}
		return text;
	}

	/**
	 * A Book and Quill used on a builder's job block at {@code bench} (the Builder's Bench, or the Blueprint Table a
	 * builder works at, B337): the list of the build under way from it, or the "no build at this bench" line.
	 */
	public static void answerAtBench(ServerLevel level, Player player, BlockPos bench, InteractionHand hand) {
		BuildSite site = atBench(level, bench);
		if (site == null) {
			Chat.actionBar(player, Component.translatable("message.aliveworkplace.site_list.no_build_at_bench").withStyle(ChatFormatting.YELLOW));
		} else {
			write(level, player, site, builderAt(level, bench), hand);
		}
	}

	/** Whether {@code pos} is a builder's job block: a build under way is run from it, or a builder about works there. */
	public static boolean isBuildersBench(ServerLevel level, BlockPos pos) {
		return builderAt(level, pos) != null || atBench(level, pos) != null;
	}

	/** The build under way from the Builder's Bench at {@code bench}, and its builder when he's about; null when none. */
	@Nullable
	public static BuildSite atBench(ServerLevel level, BlockPos bench) {
		for (BuildSite site : BuildSiteManager.get(level).all()) {
			if (!site.isQueued() && !site.isDone() && bench.equals(site.bench())) {
				return site;
			}
		}
		Villager builder = builderAt(level, bench);
		return builder == null ? null : Builders.activeSite(level, builder);
	}

	/** The builder whose bench is {@code bench}, if he is about. */
	@Nullable
	public static Villager builderAt(ServerLevel level, BlockPos bench) {
		for (BuildSite site : BuildSiteManager.get(level).all()) {
			if (!site.isQueued() && !site.isDone() && bench.equals(site.bench()) && site.builder() != null
				&& level.getEntity(site.builder()) instanceof Villager v) {
				return v;
			}
		}
		for (Villager v : level.getEntitiesOfClass(Villager.class, new AABB(bench).inflate(BENCH_REACH), Builders::isBuilder)) {
			if (Builders.benchPos(v).filter(bench::equals).isPresent()) {
				return v;
			}
		}
		return null;
	}

	private SiteList() {
	}
}
