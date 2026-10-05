package io.github.jcondedata.aliveworkplace.hall;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.github.jcondedata.aliveworkplace.blueprint.BlueprintData;
import io.github.jcondedata.aliveworkplace.build.BuildPlan;
import io.github.jcondedata.aliveworkplace.build.BuildSite;
import io.github.jcondedata.aliveworkplace.build.BuildSiteManager;
import io.github.jcondedata.aliveworkplace.build.Builders;
import io.github.jcondedata.aliveworkplace.mc.Chat;
import io.github.jcondedata.aliveworkplace.work.ChoiceMenu;
import io.github.jcondedata.aliveworkplace.world.VillageHouses;
import io.github.jcondedata.aliveworkplace.world.VillagePieces;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.phys.AABB;
import org.jetbrains.annotations.Nullable;

/**
 * House looks (ROADMAP 23.10a): every house of a village style shares that style's outside, and the village's leader (the
 * hall's owner and their friends) may give a house another style's outside on the hall's House looks page (the Builds button). The village's
 * builder then rebuilds the outside over the house like an upgrade ({@link VillagePieces#outsideId}): the room inside,
 * with its job block, bed and chests, stays as it is. The choice is kept with the hall.
 */
public final class PieceLooks {
	/** In a house's view: the five styles' outsides, from this slot. */
	public static final int FIRST_LOOK = VillageHallScreen.FIRST_ROW + 2;

	/** A look chosen for the house standing at {@code origin} (turned by {@code rotation}): its house, built style and new look. */
	public record Choice(BlockPos origin, Rotation rotation, String house, String style, String look) {
		public static final Codec<Choice> CODEC = RecordCodecBuilder.create(i -> i.group(
			BlockPos.CODEC.fieldOf("origin").forGetter(Choice::origin),
			Rotation.CODEC.optionalFieldOf("rotation", Rotation.NONE).forGetter(Choice::rotation),
			Codec.STRING.fieldOf("house").forGetter(Choice::house),
			Codec.STRING.fieldOf("style").forGetter(Choice::style),
			Codec.STRING.fieldOf("look").forGetter(Choice::look)
		).apply(i, Choice::new));
	}

	/** What choosing a look came to. */
	public enum Outcome {
		STARTED, QUEUED, SAME, BUILDING, NOT_ALLOWED, NO_LEADER, NO_BUILDER, GONE;

		public boolean ok() {
			return this == STARTED || this == QUEUED;
		}
	}


	// ---- the houses ------------------------------------------------------------------------------------------------

	/** The village's houses of ours, nearest the hall first, with any whose look was chosen but that can't be seen now. */
	public static List<VillagePieces.Piece> pieces(ServerLevel level, BlockPos hall) {
		List<VillagePieces.Piece> out = new ArrayList<>(VillagePieces.find(level, hall, VillageHalls.RADIUS));
		if (level.getBlockEntity(hall) instanceof VillageHallBlockEntity entity) {
			for (Choice c : entity.pieceLooks()) {
				if (out.stream().noneMatch(p -> p.placement().origin().equals(c.origin()))) {
					// mid-rebuild, half of one outside and half of the other: as chosen
					out.add(new VillagePieces.Piece(c.house(), c.style(), c.look(),
						new BlueprintData.Placement(level.dimension().location(), c.origin(), c.rotation(), Mirror.NONE)));
				}
			}
		}
		out.sort(Comparator.comparingDouble(p -> p.placement().origin().distSqr(hall)));
		return out;
	}

	/** The look chosen for {@code piece}, if one was. */
	public static Optional<Choice> chosen(ServerLevel level, BlockPos hall, VillagePieces.Piece piece) {
		if (!(level.getBlockEntity(hall) instanceof VillageHallBlockEntity entity)) {
			return Optional.empty();
		}
		return entity.pieceLooks().stream().filter(c -> c.origin().equals(piece.placement().origin())).findFirst();
	}

	/** The open outside rebuild of {@code piece}, if any. */
	@Nullable
	public static BuildSite rebuild(ServerLevel level, VillagePieces.Piece piece) {
		for (BuildSite site : BuildSiteManager.get(level).all()) {
			if (VillagePieces.isOutside(site.structure()) && site.placement().origin().equals(piece.placement().origin())
				&& site.placement().dimension().equals(piece.placement().dimension())) {
				return site;
			}
		}
		return null;
	}

	/**
	 * {@code player} chooses {@code look} for {@code piece}: if they lead the village, the nearest free builder (or the one
	 * with the shortest queue) rebuilds its outside, and the choice is kept with the hall. A rebuild to another look that
	 * is still going is called off first.
	 */
	public static Outcome choose(ServerLevel level, BlockPos hall, ServerPlayer player, VillagePieces.Piece piece, String look) {
		if (!(level.getBlockEntity(hall) instanceof VillageHallBlockEntity entity) || !VillageHouses.STYLES.contains(look)) {
			return Outcome.GONE;
		}
		if (entity.owner() == null && !player.hasPermissions(2)) {
			return Outcome.NO_LEADER;
		}
		if (!VillageProtection.mayBuild(level, entity, player)) {
			return Outcome.NOT_ALLOWED;
		}
		BuildSite open = rebuild(level, piece);
		if (open != null) {
			if (VillagePieces.outsideParts(open.structure()).map(p -> p[0].equals(look)).orElse(false)) {
				return Outcome.BUILDING;
			}
		} else {
			Optional<VillagePieces.Piece> standing = VillagePieces.at(level, piece.placement());
			if (standing.isEmpty() && chosen(level, hall, piece).isEmpty()) {
				return Outcome.GONE; // (a house with a chosen look may stand half rebuilt: it's still there)
			}
			if (standing.isPresent() && standing.get().look().equals(look)) {
				keep(entity, piece, look);
				return Outcome.SAME;
			}
		}
		Optional<Villager> builder = builderFor(level, hall, piece);
		if (builder.isEmpty()) {
			return Outcome.NO_BUILDER;
		}
		if (open != null) {
			Builders.cancel(level, open);
		}
		Villager villager = builder.get();
		UUID owner = entity.owner() != null ? entity.owner() : player.getUUID();
		String ownerName = entity.owner() != null ? entity.ownerName() : player.getGameProfile().getName();
		boolean busy = Builders.activeSite(level, villager) != null;
		BuildSite site = busy
			? Builders.enqueue(level, villager, owner, ownerName, VillagePieces.outsideId(look, piece.house()), piece.placement())
			: Builders.start(level, villager, owner, ownerName, VillagePieces.outsideId(look, piece.house()), piece.placement());
		site.setLevelGround(false); // the street and gardens round a village house stay as they are
		keep(entity, piece, look);
		Chronicle.record(level, hall, Chronicle.Kind.PLANS, Component.translatable("chronicle.aliveworkplace.piece_look",
			player.getDisplayName(), VillagePieces.houseName(piece.house()), VillagePieces.styleName(look)));
		return busy ? Outcome.QUEUED : Outcome.STARTED;
	}

	private static void keep(VillageHallBlockEntity entity, VillagePieces.Piece piece, String look) {
		List<Choice> choices = new ArrayList<>(entity.pieceLooks().stream().filter(c -> !c.origin().equals(piece.placement().origin())).toList());
		choices.add(new Choice(piece.placement().origin(), piece.placement().rotation(), piece.house(), piece.style(), look));
		entity.setPieceLooks(choices);
	}

	/** The village's builder for {@code piece}: the nearest idle one whose bench reaches it, else the one with the shortest queue. */
	static Optional<Villager> builderFor(ServerLevel level, BlockPos hall, VillagePieces.Piece piece) {
		BlockPos centre = piece.placement().origin().offset(net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate.transform(
			new BlockPos(4, 0, 5), Mirror.NONE, piece.placement().rotation(), BlockPos.ZERO));
		Villager best = null;
		int bestQueue = Integer.MAX_VALUE;
		List<Villager> builders = level.getEntities(EntityType.VILLAGER, new AABB(hall).inflate(VillageHalls.RADIUS),
				v -> v.isAlive() && Builders.isBuilder(v) && Builders.benchPos(v).isPresent()).stream()
			.sorted(Comparator.comparingDouble(v -> v.distanceToSqr(centre.getX(), centre.getY(), centre.getZ()))).toList();
		for (Villager v : builders) {
			BlockPos bench = Builders.benchPos(v).orElseThrow();
			if (Math.sqrt(centre.distSqr(bench)) > Builders.MAX_SITE_DISTANCE) {
				continue;
			}
			int queue = Builders.activeSite(level, v) == null ? 0 : 1 + Builders.queue(level, v).size();
			if (queue <= Builders.MAX_QUEUE && queue < bestQueue) {
				best = v;
				bestQueue = queue;
			}
		}
		return Optional.ofNullable(best);
	}

	/** What to tell the player after {@code outcome}. */
	public static Component message(ServerLevel level, BlockPos hall, VillagePieces.Piece piece, String look, Outcome outcome) {
		Component house = VillagePieces.houseName(piece.house());
		Component style = VillagePieces.styleName(look);
		String owner = level.getBlockEntity(hall) instanceof VillageHallBlockEntity entity ? entity.ownerName() : "";
		BuildSite site = rebuild(level, piece);
		Component builder = site != null && site.builder() != null && level.getEntity(site.builder()) instanceof Villager v
			? v.getDisplayName() : Component.translatable("screen.aliveworkplace.piece_looks.a_builder");
		return switch (outcome) {
			case STARTED -> Component.translatable("message.aliveworkplace.piece_look.started", builder, house, style).withStyle(ChatFormatting.GREEN);
			case QUEUED -> Component.translatable("message.aliveworkplace.piece_look.queued", builder, house, style).withStyle(ChatFormatting.GREEN);
			case SAME -> Component.translatable("message.aliveworkplace.piece_look.same", house, style).withStyle(ChatFormatting.YELLOW);
			case BUILDING -> Component.translatable("message.aliveworkplace.piece_look.building", house, style).withStyle(ChatFormatting.YELLOW);
			case NOT_ALLOWED -> Component.translatable("message.aliveworkplace.piece_look.not_allowed", owner).withStyle(ChatFormatting.RED);
			case NO_LEADER -> Component.translatable("message.aliveworkplace.piece_look.no_leader").withStyle(ChatFormatting.RED);
			case NO_BUILDER -> Component.translatable("message.aliveworkplace.piece_look.no_builder", house, Builders.MAX_SITE_DISTANCE)
				.withStyle(ChatFormatting.RED);
			case GONE -> Component.translatable("message.aliveworkplace.piece_look.gone").withStyle(ChatFormatting.RED);
		};
	}

	// ---- the page ------------------------------------------------------------------------------------------------

	/** Each style's icon: the block its outside shows most. */
	public static Item icon(String style) {
		return switch (style) {
			case "desert" -> Items.CUT_SANDSTONE;
			case "savanna" -> Items.ACACIA_LOG;
			case "snowy" -> Items.STRIPPED_SPRUCE_LOG;
			case "taiga" -> Items.SPRUCE_PLANKS;
			default -> Items.OAK_LOG;
		};
	}

	static ItemStack header(ServerLevel level, BlockPos hall) {
		String owner = level.getBlockEntity(hall) instanceof VillageHallBlockEntity entity && entity.owner() != null ? entity.ownerName() : null;
		return VillageHallScreen.icon(Items.OAK_DOOR, Component.translatable("screen.aliveworkplace.piece_looks.title", VillageHalls.name(level, hall)),
			ChatFormatting.GOLD,
			VillageHallScreen.line("screen.aliveworkplace.piece_looks.about", ChatFormatting.GRAY),
			VillageHallScreen.line("screen.aliveworkplace.piece_looks.inside", ChatFormatting.GRAY),
			owner == null ? VillageHallScreen.line("screen.aliveworkplace.piece_looks.no_leader", ChatFormatting.YELLOW)
				: VillageHallScreen.line(Component.translatable("screen.aliveworkplace.piece_looks.leader", owner), ChatFormatting.YELLOW));
	}

	/** The House looks page (from the hall's Builds button): back, its header, then a button per house; a click opens its looks. */
	public static void render(ChoiceMenu menu, ServerLevel level, BlockPos hall) {
		menu.clearButtons();
		menu.button(0, VillageHallScreen.icon(Items.ARROW, Component.translatable("screen.aliveworkplace.hall.back"), ChatFormatting.WHITE), p -> {
			VillageHallScreen.render(menu, level, hall, 0);
			menu.broadcastChanges();
		});
		menu.button(4, header(level, hall), null);
		menu.divider(1);
		List<VillagePieces.Piece> pieces = pieces(level, hall);
		if (pieces.isEmpty()) {
			menu.button(VillageHallScreen.FIRST_ROW + 4, VillageHallScreen.icon(Items.PAPER,
				Component.translatable("screen.aliveworkplace.piece_looks.none"), ChatFormatting.GRAY,
				VillageHallScreen.line("screen.aliveworkplace.piece_looks.none_hint", ChatFormatting.DARK_GRAY)), null);
			return;
		}
		int slot = VillageHallScreen.FIRST_ROW;
		for (VillagePieces.Piece piece : pieces) {
			if (slot >= ChoiceMenu.SIZE) {
				break;
			}
			menu.button(slot++, pieceIcon(level, hall, piece, true), p -> {
				renderPiece(menu, level, hall, piece);
				menu.broadcastChanges();
			});
		}
	}

	/** A house's button: its name, its outside now, where it is and how its rebuild is going. */
	public static ItemStack pieceIcon(ServerLevel level, BlockPos hall, VillagePieces.Piece piece, boolean hint) {
		List<Component> lore = new ArrayList<>();
		lore.add(VillageHallScreen.line(Component.translatable("screen.aliveworkplace.piece_looks.outside", VillagePieces.styleName(piece.look())),
			ChatFormatting.GRAY));
		if (!piece.style().equals(piece.look())) {
			lore.add(VillageHallScreen.line(Component.translatable("screen.aliveworkplace.piece_looks.built_as", VillagePieces.styleName(piece.style())),
				ChatFormatting.DARK_GRAY));
		}
		BlockPos at = piece.placement().origin();
		lore.add(VillageHallScreen.line(Component.translatable("screen.aliveworkplace.piece_looks.at", at.getX(), at.getY(), at.getZ()),
			ChatFormatting.DARK_GRAY));
		BuildSite site = rebuild(level, piece);
		if (site != null) {
			String look = VillagePieces.outsideParts(site.structure()).map(p -> p[0]).orElse(piece.look());
			BuildPlan plan = site.plan(level);
			int percent = plan == null ? 0 : Math.round(site.progress(plan) * 100);
			lore.add(VillageHallScreen.line(Component.translatable("screen.aliveworkplace.piece_looks.rebuilding", VillagePieces.styleName(look), percent),
				ChatFormatting.AQUA));
			if (!site.missing().isEmpty()) {
				lore.add(VillageHallScreen.line(Component.translatable("screen.aliveworkplace.piece_looks.waiting", Builders.formatMissing(site, 3)),
					ChatFormatting.RED));
			}
		}
		if (hint) {
			lore.add(VillageHallScreen.line("screen.aliveworkplace.piece_looks.click", ChatFormatting.DARK_GRAY));
		}
		return VillageHallScreen.icon(icon(piece.look()), VillagePieces.houseName(piece.house()), ChatFormatting.WHITE, lore.toArray(Component[]::new));
	}

	/** A house's view: back, the house, then the five styles' outsides; a click on one chooses it. */
	public static void renderPiece(ChoiceMenu menu, ServerLevel level, BlockPos hall, VillagePieces.Piece piece) {
		menu.clearButtons();
		menu.button(0, VillageHallScreen.icon(Items.ARROW, Component.translatable("screen.aliveworkplace.hall.back"), ChatFormatting.WHITE), p -> {
			render(menu, level, hall);
			menu.broadcastChanges();
		});
		menu.button(4, pieceIcon(level, hall, piece, false), null);
		menu.divider(1);
		for (int i = 0; i < VillageHouses.STYLES.size(); i++) {
			String look = VillageHouses.STYLES.get(i);
			List<Component> lore = new ArrayList<>();
			if (look.equals(piece.look())) {
				lore.add(VillageHallScreen.line("screen.aliveworkplace.piece_looks.now", ChatFormatting.GREEN));
			}
			if (look.equals(piece.style())) {
				lore.add(VillageHallScreen.line("screen.aliveworkplace.piece_looks.original", ChatFormatting.GRAY));
			}
			lore.add(VillageHallScreen.line("screen.aliveworkplace.piece_looks.choose", ChatFormatting.DARK_GRAY));
			menu.button(FIRST_LOOK + i, VillageHallScreen.icon(icon(look), Component.translatable("screen.aliveworkplace.piece_looks.look",
				VillagePieces.styleName(look)), ChatFormatting.GOLD, lore.toArray(Component[]::new)), p -> {
				Outcome outcome = choose(level, hall, p, piece, look);
				Chat.actionBar(p, message(level, hall, piece, look, outcome));
				renderPiece(menu, level, hall, piece);
				menu.broadcastChanges();
			});
		}
	}

	private PieceLooks() {
	}
}
