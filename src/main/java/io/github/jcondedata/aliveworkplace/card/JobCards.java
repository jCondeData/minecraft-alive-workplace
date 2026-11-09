package io.github.jcondedata.aliveworkplace.card;

import io.github.jcondedata.aliveworkplace.blueprint.Blueprints;
import io.github.jcondedata.aliveworkplace.build.BuildPlan;
import io.github.jcondedata.aliveworkplace.build.BuildSite;
import io.github.jcondedata.aliveworkplace.build.BuilderLevels;
import io.github.jcondedata.aliveworkplace.build.Builders;
import io.github.jcondedata.aliveworkplace.farm.FieldJob;
import io.github.jcondedata.aliveworkplace.hall.VillageHallScreen;
import io.github.jcondedata.aliveworkplace.hall.VillageHalls;
import io.github.jcondedata.aliveworkplace.hall.VillageProtection;
import io.github.jcondedata.aliveworkplace.mc.Chat;
import io.github.jcondedata.aliveworkplace.mc.Players;
import io.github.jcondedata.aliveworkplace.mine.Miners;
import io.github.jcondedata.aliveworkplace.mine.QuarrySite;
import io.github.jcondedata.aliveworkplace.registry.ModAttachments;
import io.github.jcondedata.aliveworkplace.registry.ModItems;
import io.github.jcondedata.aliveworkplace.work.Requests;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.npc.VillagerData;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import org.jetbrains.annotations.Nullable;

/**
 * The job-status cards (ROADMAP 42.5): a sneak-right-click with an empty hand on a Builder, Miner, Fisher, Farmer,
 * Lumberjack, Orchard keeper or Postman opens a {@link StatusCard} in the Village Hall's look instead of the status lines
 * in chat. It shows the villager's name, job and level, what they're doing (the Hall's own words, {@link
 * VillageHallScreen#doing}), their work in numbers (progress with a bar, counts, experience) and what they need (the
 * Hall's requests, {@link Requests#of}), with three buttons: Hall page (their page in the Village Hall, grayed when no
 * hall is near or the village keeps the player out), In chat (the old status lines, word for word, with their Stop
 * link and the builder's whole material list; first, as a card's first button does what the click did) and Close.
 */
public final class JobCards {
	/** The buttons: their Hall page, the old status lines in chat. */
	public static final int HALL = 0;
	public static final int CHAT = 1;
	/** What-they-need lines before "and N more". */
	static final int NEEDS_SHOWN = 3;

	/** The seven jobs with a status card. */
	public enum Kind { BUILDER, MINER, FISHER, FARMER, LUMBERJACK, ORCHARD, POSTMAN }

	/** Opens {@code villager}'s card, as their {@code kind}, for {@code player}. */
	public static void open(ServerPlayer player, Villager villager, Kind kind) {
		StatusCardMenu.open(player, villager, card(player, villager, kind), (p, v, button) -> {
			if (!v.isAlive()) {
				return; // died or unloaded while the card was open
			}
			if (button == HALL) {
				hallPage(p, v);
			} else if (button == CHAT) {
				chat(p, v, kind);
			}
		});
	}

	/** The status lines in chat, as the sneak-click sent them before the card (and still sends from the card's In chat). */
	public static void chat(ServerPlayer player, Villager villager, Kind kind) {
		switch (kind) {
			case BUILDER -> Builders.sendStatus(player, villager);
			case MINER -> Miners.sendStatus(player, villager);
			case FISHER -> io.github.jcondedata.aliveworkplace.fish.Fishers.sendStatus(player, villager);
			case FARMER -> io.github.jcondedata.aliveworkplace.farm.Fields.sendStatus(player, villager);
			case LUMBERJACK -> io.github.jcondedata.aliveworkplace.wood.TreeFarms.sendStatus(player, villager);
			case ORCHARD -> io.github.jcondedata.aliveworkplace.orchard.Orchards.sendStatus(player, villager);
			case POSTMAN -> io.github.jcondedata.aliveworkplace.mail.Postmen.sendStatus(player, villager);
		}
	}

	/** Opens the Village Hall near {@code villager} on their page, or says why it can't. */
	static void hallPage(ServerPlayer player, Villager villager) {
		ServerLevel level = Players.level(player);
		Component refusal = hallRefusal(player, villager);
		if (refusal != null) {
			Chat.actionBar(player, refusal);
			return;
		}
		VillageHallScreen.openWorker(player, VillageHalls.nearest(level, villager.blockPosition()).orElseThrow(), villager);
	}

	/** Why the Hall page button is grayed: no hall near them, or a protected village that keeps {@code player} out; null if it isn't. */
	@Nullable
	static Component hallRefusal(ServerPlayer player, Villager villager) {
		ServerLevel level = Players.level(player);
		if (villager.level() != level) {
			return Component.translatable("screen.aliveworkplace.card.job.no_hall", villager.getDisplayName());
		}
		Optional<BlockPos> hall = VillageHalls.nearest(level, villager.blockPosition());
		if (hall.isEmpty()) {
			return Component.translatable("screen.aliveworkplace.card.job.no_hall", villager.getDisplayName());
		}
		Optional<BlockPos> keeper = VillageProtection.keeper(level, player, hall.get());
		return keeper.isPresent() ? VillageProtection.denied(level, keeper.get()) : null;
	}

	/** What {@code villager}'s card shows {@code player} right now. */
	public static StatusCard card(ServerPlayer player, Villager villager, Kind kind) {
		ServerLevel level = (ServerLevel) villager.level();
		List<StatusCard.Line> lines = new ArrayList<>();
		Component subtitle = null;
		switch (kind) {
			case BUILDER -> {
				BuildSite site = Builders.activeSite(level, villager);
				if (site == null) {
					subtitle = idle(villager, "message.aliveworkplace.status.idle", "message.aliveworkplace.status.no_bench");
					MutableComponent idle = subtitle.copy();
					io.github.jcondedata.aliveworkplace.build.BenchMoves.statusLine(villager).ifPresent(l -> idle.append(" ").append(l));
					subtitle = idle;
				} else {
					BuildPlan plan = site.plan(level);
					if (plan == null) {
						subtitle = Component.translatable("message.aliveworkplace.status.blueprint_missing", site.structure().toString());
					} else {
						Builders.refreshMissing(level, site, villager);
						lines.add(new StatusCard.Line(new ItemStack(ModItems.BLUEPRINT), Blueprints.siteName(level, site), percent(site.progress(plan)),
							StatusCard.GOLD, site.progress(plan)));
						if (site.detail() != null) {
							subtitle = joined(VillageHallScreen.doing(level, villager)).append(" ").append(site.detail());
						}
					}
					if (site.skipped() > 0) {
						lines.add(count("skipped", site.skipped(), StatusCard.GRAY));
					}
					List<BuildSite> queue = Builders.queue(level, villager);
					if (!queue.isEmpty()) {
						MutableComponent names = Component.empty();
						for (int i = 0; i < queue.size(); i++) {
							names.append(i == 0 ? Component.empty() : Component.literal(", ")).append(Blueprints.siteName(level, queue.get(i)));
						}
						lines.add(new StatusCard.Line(ItemStack.EMPTY, Component.translatable("screen.aliveworkplace.card.job.next"), names, StatusCard.WHITE,
							StatusCard.NO_BAR));
					}
				}
			}
			case MINER -> {
				QuarrySite site = Miners.activeSite(level, villager);
				if (site == null) {
					subtitle = idle(villager, "message.aliveworkplace.quarry.idle", "message.aliveworkplace.quarry.no_bench_status");
				} else {
					BoundingBox box = site.box();
					lines.add(new StatusCard.Line(new ItemStack(Items.IRON_PICKAXE), Component.translatable("screen.aliveworkplace.card.job.quarry",
						box.getXSpan(), box.getZSpan(), box.getYSpan()), percent(site.progress()), StatusCard.GOLD, site.progress()));
					lines.add(count("mined", site.mined(), StatusCard.WHITE));
					if (site.skipped() > 0) {
						lines.add(count("skipped", site.skipped(), StatusCard.GRAY));
					}
				}
			}
			case FISHER -> lines.add(count(new ItemStack(Items.COD), "fish", ModAttachments.FISH_CAUGHT.getOrElse(villager, 0)));
			case FARMER -> {
				area(lines, ModAttachments.FARM_FIELD.get(villager), new ItemStack(Items.WHEAT), "field");
				lines.add(count("harvested", ModAttachments.FARM_HARVESTED.getOrElse(villager, 0), StatusCard.WHITE));
			}
			case LUMBERJACK -> {
				area(lines, ModAttachments.TREE_FARM.get(villager), new ItemStack(Items.OAK_SAPLING), "tree_farm");
				lines.add(count("felled", ModAttachments.TREES_FELLED.getOrElse(villager, 0), StatusCard.WHITE));
				lines.add(count("planted", ModAttachments.SAPLINGS_PLANTED.getOrElse(villager, 0), StatusCard.WHITE));
			}
			case ORCHARD -> {
				area(lines, ModAttachments.ORCHARD.get(villager), new ItemStack(Items.SWEET_BERRIES), "orchard");
				lines.add(count("picked", ModAttachments.FRUIT_PICKED.getOrElse(villager, 0), StatusCard.WHITE));
				lines.add(count("planted", ModAttachments.SAPLINGS_PLANTED.getOrElse(villager, 0), StatusCard.WHITE));
			}
			case POSTMAN -> {
				lines.add(count(new ItemStack(Items.PAPER), "delivered", ModAttachments.MAIL_DELIVERED.getOrElse(villager, 0)));
				lines.add(count("routes", io.github.jcondedata.aliveworkplace.mail.Postmen.routes(villager).size(), StatusCard.WHITE));
			}
		}
		if (subtitle == null) {
			subtitle = joined(VillageHallScreen.doing(level, villager));
		}
		lines.add(experience(villager));
		needs(lines, level, villager);
		Component tag = Component.translatable("screen.aliveworkplace.card.tag", BuilderLevels.jobName(villager),
			BuilderLevels.levelName(BuilderLevels.level(villager)));
		return new StatusCard(villager.getId(), villager.getDisplayName(), tag, subtitle,
			Component.translatable("screen.aliveworkplace.card.job.heading"), List.copyOf(lines), Component.empty(),
			// In chat first: a card's first button does what the click did before the card (43.3's rule)
			List.of(StatusCard.Button.of(CHAT, Component.translatable("screen.aliveworkplace.card.job.chat"), null),
				StatusCard.Button.of(HALL, Component.translatable("screen.aliveworkplace.card.job.hall"), hallRefusal(player, villager)),
				StatusCard.Button.of(StatusCardMenu.CLOSE, Component.translatable("screen.aliveworkplace.card.close"), null)),
			Component.translatable("screen.aliveworkplace.card.job.hint"));
	}

	/** A builder's or miner's line with no site: "has nothing to build" or "has no bench", as the chat said. */
	private static Component idle(Villager villager, String idleKey, String noBenchKey) {
		return Component.translatable(Builders.benchPos(villager).isPresent() ? idleKey : noBenchKey, villager.getDisplayName());
	}

	/** The Hall's "what they're doing" lines in one sentence. */
	private static MutableComponent joined(List<Component> doing) {
		MutableComponent out = Component.empty();
		for (int i = 0; i < doing.size(); i++) {
			if (doing.get(i).getString().isEmpty()) {
				continue;
			}
			out.append(out.getSiblings().isEmpty() ? Component.empty() : Component.literal(" · ")).append(doing.get(i));
		}
		return out;
	}

	private static Component percent(float progress) {
		return Component.translatable("screen.aliveworkplace.card.job.percent", Math.round(progress * 100));
	}

	private static StatusCard.Line count(String key, int n, int color) {
		return new StatusCard.Line(ItemStack.EMPTY, Component.translatable("screen.aliveworkplace.card.job." + key), Component.literal(String.valueOf(n)),
			color, StatusCard.NO_BAR);
	}

	private static StatusCard.Line count(ItemStack icon, String key, int n) {
		return new StatusCard.Line(icon, Component.translatable("screen.aliveworkplace.card.job." + key), Component.literal(String.valueOf(n)),
			StatusCard.WHITE, StatusCard.NO_BAR);
	}

	/** "Field: 9 × 9" for a worker's marked area. */
	private static void area(List<StatusCard.Line> lines, @Nullable FieldJob job, ItemStack icon, String key) {
		if (job != null) {
			BoundingBox box = job.box();
			lines.add(new StatusCard.Line(icon, Component.translatable("screen.aliveworkplace.card.job." + key),
				Component.translatable("screen.aliveworkplace.card.job.size", box.getXSpan(), box.getZSpan()), StatusCard.WHITE, StatusCard.NO_BAR));
		}
	}

	/** "Experience: 35 / 70 XP" with a bar to the next level, or "Top level". */
	static StatusCard.Line experience(Villager villager) {
		int lvl = BuilderLevels.level(villager);
		Component label = Component.translatable("screen.aliveworkplace.card.job.experience");
		if (!VillagerData.canLevelUp(lvl)) {
			return new StatusCard.Line(ItemStack.EMPTY, label, Component.translatable("screen.aliveworkplace.card.job.top_level"), StatusCard.GREEN,
				StatusCard.NO_BAR);
		}
		int xp = villager.getVillagerXp();
		int min = VillagerData.getMinXpPerLevel(lvl);
		int max = VillagerData.getMaxXpPerLevel(lvl);
		float bar = max <= min ? 0f : Math.max(0f, Math.min(1f, (xp - min) / (float) (max - min)));
		return new StatusCard.Line(ItemStack.EMPTY, label, Component.translatable("screen.aliveworkplace.card.job.xp", xp, max), StatusCard.GREEN, bar);
	}

	/** What they need, as the Hall's requests list it (the builder's short materials, a miner's pickaxe or ladders, ...). */
	private static void needs(List<StatusCard.Line> lines, ServerLevel level, Villager villager) {
		List<Requests.Request> requests = Requests.of(level, villager);
		if (requests.isEmpty()) {
			lines.add(new StatusCard.Line(ItemStack.EMPTY, Component.translatable("screen.aliveworkplace.card.job.needs"),
				Component.translatable("screen.aliveworkplace.card.job.needs_nothing"), StatusCard.GREEN, StatusCard.NO_BAR));
			return;
		}
		for (Requests.Request r : requests.subList(0, Math.min(NEEDS_SHOWN, requests.size()))) {
			lines.add(new StatusCard.Line(r.icon().copyWithCount(1), Component.translatable("screen.aliveworkplace.card.job.need", Requests.inSentence(r.what())),
				Component.translatable("screen.aliveworkplace.card.job.need_count", r.count()), StatusCard.ORANGE, StatusCard.NO_BAR));
		}
		if (requests.size() > NEEDS_SHOWN) {
			lines.add(new StatusCard.Line(ItemStack.EMPTY, Component.translatable("screen.aliveworkplace.card.job.need_more", requests.size() - NEEDS_SHOWN),
				Component.empty(), StatusCard.GRAY, StatusCard.NO_BAR));
		}
	}

	private JobCards() {
	}
}
