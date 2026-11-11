package io.github.jcondedata.aliveworkplace.hall;

import io.github.jcondedata.aliveworkplace.blueprint.BlueprintLibrary;
import io.github.jcondedata.aliveworkplace.blueprint.BlueprintOutline;
import io.github.jcondedata.aliveworkplace.blueprint.Blueprints;
import io.github.jcondedata.aliveworkplace.build.BuildSiteManager;
import io.github.jcondedata.aliveworkplace.mc.Chat;
import io.github.jcondedata.aliveworkplace.move.MoveFit;
import io.github.jcondedata.aliveworkplace.move.MovePlan;
import io.github.jcondedata.aliveworkplace.move.Movers;
import io.github.jcondedata.aliveworkplace.move.PendingMoves;
import io.github.jcondedata.aliveworkplace.work.ChoiceMenu;
import io.github.jcondedata.aliveworkplace.work.Words;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Vec3i;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import org.jetbrains.annotations.Nullable;

/**
 * The Village Hall's Move page (ROADMAP 45.2), under Buildings: the village's finished buildings, each a Move button. A
 * click opens the picker: the building, a map of the ground round the new spot (a cell every {@link #STEP} blocks, north
 * up; a click on a cell puts the spot there), a rotate button, the fit check's plain line ({@link MoveFit}), Confirm and
 * Cancel. While choosing, the new spot glows in the world as a ghost outline ({@link BlueprintOutline#glowMove}), green
 * where it fits, red where it's refused, its front edge gold.
 *
 * <p>Confirm needs a Mover working in the village ({@link Movers}); with one, the move is saved as the village's pending
 * move ({@link PendingMoves}) for the Mover (45.3) to take. What the player is picking is kept only while the screen is
 * open: closing it forgets the pick and the ghost.
 */
public final class MovePage {
	public static final String PAGE = "move";
	/** On the picker: back to the list, the building, call off a planned move, rotate, the fit line, confirm, cancel. */
	public static final int BACK = 0;
	/** The picker's guide (B1163): its name is the step's instruction, its lines where it is now, the new spot, the plan and the Mover. */
	public static final int GUIDE = 2;
	public static final int HEADER = 4;
	/** The village map the client draws (B1163): the land, the roads, the buildings (their names its lines), now and the new spot. */
	public static final int VIEW = 6;
	public static final int CALL_OFF = 8;
	public static final int ROTATE = 10;
	public static final int VERDICT = 12;
	public static final int CONFIRM = 14;
	public static final int CANCEL = 16;
	/** The map: {@link #ROWS} rows of {@link #COLUMNS} cells from {@link VillageHallScreen#FIRST_ROW}, north at the top. */
	public static final int COLUMNS = 9;
	public static final int ROWS = 4;
	/** Blocks a map cell stands for, across and down. */
	public static final int STEP = 4;
	/** The map cell of the new spot's middle (fifth column, third row). */
	public static final int CENTRE = VillageHallScreen.FIRST_ROW + 2 * COLUMNS + 4;
	/** The custom data key marking a map cell (the client draws the map as a grid). */
	public static final String MAP_MARK = "aliveworkplace:move_map";
	/** Buildings on the list at most: the four rows under the divider, less the last slot when there are more. */
	public static final int SHOWN = ChoiceMenu.SIZE - VillageHallScreen.FIRST_ROW;
	/** The custom data key of {@link #VIEW}'s map, and of the list's rows (step 1, B1163). */
	public static final String VIEW_MARK = "aliveworkplace:move_view";
	public static final String LIST_MARK = "aliveworkplace:move_list";
	/** The custom data key of {@link #GUIDE}'s step: 2 (pick the new spot) or 3 (confirm). */
	public static final String STEP_MARK = "aliveworkplace:move_step";
	/** The village map's pixels a side; a menu-button click of {@link #MAP_CLICK} plus {@code z * MAP + x} puts the spot there. */
	public static final int MAP = 128;
	public static final int MAP_CLICK = 600_000;
	/** How long the ghost outline stays after the last click (it goes when the screen closes). */
	static final int GHOST_TICKS = 2400;

	/**
	 * What a player is picking: the building ({@code from}, its placement's origin, and its blueprint), its plan and the box
	 * it stands in, the new spot's middle ({@code x}, {@code z}) and the turn.
	 */
	public record Picking(BlockPos hall, BlockPos from, ResourceLocation structure, MovePlan plan, BoundingBox old, int x, int z, Rotation rotation) {
		Picking at(int nx, int nz) {
			return new Picking(hall, from, structure, plan, old, nx, nz, rotation);
		}

		Picking turned(Rotation r) {
			return new Picking(hall, from, structure, plan, old, x, z, r);
		}

		/** The building's turned size. */
		public Vec3i size() {
			return plan.size(rotation);
		}

		/** Whether the spot and turn are the ones it has now. */
		public boolean same() {
			return rotation == Rotation.NONE && x - size().getX() / 2 == old.minX() && z - size().getZ() / 2 == old.minZ();
		}
	}

	private static final Map<UUID, Picking> PICKING = new ConcurrentHashMap<>();

	/**
	 * The village map of a picker (B1163), taken once when it opens: its north-west corner ({@code ox}, {@code oz}), blocks a
	 * pixel ({@code step}), the land's map colours and each pixel's kind (0 ground, 1 road, 2 water).
	 */
	public record MapView(int ox, int oz, int step, byte[] colors, byte[] kinds) {
		/** The block at map pixel ({@code px}, {@code pz}). */
		public BlockPos at(int px, int pz) {
			return new BlockPos(ox + px * step + step / 2, 0, oz + pz * step + step / 2);
		}

		static MapView of(ServerLevel level, BlockPos hall) {
			int step = Math.max(1, (VillageHalls.RADIUS + MAP / 2 - 1) / (MAP / 2));
			int half = MAP / 2 * step;
			byte[] colors = VillageMaps.colors(level, hall, half, step);
			byte[] kinds = new byte[MAP * MAP];
			for (int pz = 0; pz < MAP; pz++) {
				for (int px = 0; px < MAP; px++) {
					int x = hall.getX() - half + px * step;
					int z = hall.getZ() - half + pz * step;
					if (!level.hasChunk(x >> 4, z >> 4)) {
						continue;
					}
					BlockState top = level.getBlockState(new BlockPos(x, MoveFit.groundY(level, x, z), z));
					kinds[pz * MAP + px] = (byte) (top.getFluidState().is(FluidTags.WATER) ? 2
						: io.github.jcondedata.aliveworkplace.city.RoadStyles.isPaving(top) ? 1 : 0);
				}
			}
			return new MapView(hall.getX() - half, hall.getZ() - half, step, colors, kinds);
		}
	}

	private static final Map<UUID, MapView> MAPS = new ConcurrentHashMap<>();

	@Nullable
	private static HallPages.Page page;

	/** Adds the page, as one of the mod's own spare pages, under the Buildings section. */
	public static void init() {
		page = HallPages.registerSpare(PAGE, MovePage::tab, MovePage::header, MovePage::fill);
		HallSections.place(PAGE, HallSections.Section.BUILDINGS);
	}

	/** What {@code player} is picking now, if anything (tests, the screenshot harness). */
	public static Optional<Picking> picking(ServerPlayer player) {
		return Optional.ofNullable(PICKING.get(player.getUUID()));
	}

	/** The fit check of what {@code player} is picking now. */
	public static Optional<MoveFit.Fit> fit(ServerPlayer player) {
		return picking(player).map(p -> fit(player.serverLevel(), p));
	}

	static MoveFit.Fit fit(ServerLevel level, Picking p) {
		return MoveFit.check(level, p.hall(), p.from(), p.old(), p.size(), p.x(), p.z(), p.same());
	}

	static ItemStack tab(ServerLevel level, BlockPos hall) {
		int planned = PendingMoves.get(level).of(hall).size();
		return VillageHallScreen.icon(Items.PISTON, Component.translatable("screen.aliveworkplace.move"), ChatFormatting.GOLD,
			VillageHallScreen.line(Words.counted("screen.aliveworkplace.move.planned", planned, planned), planned > 0 ? ChatFormatting.AQUA : ChatFormatting.GRAY),
			VillageHallScreen.line("screen.aliveworkplace.move.hint", ChatFormatting.DARK_GRAY));
	}

	static ItemStack header(ServerLevel level, BlockPos hall) {
		return VillageHallScreen.icon(Items.PISTON, Component.translatable("screen.aliveworkplace.move.title", VillageHalls.name(level, hall)), ChatFormatting.GOLD,
			VillageHallScreen.line("screen.aliveworkplace.move.about", ChatFormatting.GRAY),
			VillageHallScreen.line(Movers.hired(level, hall) ? "screen.aliveworkplace.move.mover_here" : "screen.aliveworkplace.move.no_mover",
				Movers.hired(level, hall) ? ChatFormatting.GREEN : ChatFormatting.YELLOW));
	}

	/** The list: a Move button per finished building of the village; with more than fit, the last slot says how many more. */
	static void fill(ChoiceMenu menu, ServerLevel level, BlockPos hall, ServerPlayer viewer) {
		stop(viewer);
		List<BuildSiteManager.Finished> buildings = BuildSiteManager.get(level).finishedInVillage(level, hall);
		if (buildings.isEmpty()) {
			menu.button(VillageHallScreen.FIRST_ROW + 4, marked(VillageHallScreen.icon(Items.PAPER, Component.translatable("screen.aliveworkplace.move.none"),
				ChatFormatting.GRAY, VillageHallScreen.line("screen.aliveworkplace.move.none_hint", ChatFormatting.DARK_GRAY)), LIST_MARK, 1), null);
			return;
		}
		int fit = buildings.size() > SHOWN ? SHOWN - 1 : SHOWN;
		int slot = VillageHallScreen.FIRST_ROW;
		PendingMoves pending = PendingMoves.get(level);
		for (BuildSiteManager.Finished f : buildings.subList(0, Math.min(fit, buildings.size()))) {
			BlockPos from = f.placement().origin();
			List<Component> lore = new ArrayList<>();
			BlockPos middle = BlueprintLibrary.get(level, f.structure()).map(b -> BlueprintOutline.bounds(f.placement(), b.size()).getCenter()).orElse(from);
			lore.add(VillageHallScreen.line(Component.translatable("screen.aliveworkplace.move.now", fromHall(hall, middle)), ChatFormatting.GRAY));
			pending.forBuilding(from).ifPresent(m -> lore.add(VillageHallScreen.line(Component.translatable("screen.aliveworkplace.move.planned_to",
				m.to().getX(), m.to().getY(), m.to().getZ()), ChatFormatting.AQUA)));
			lore.add(VillageHallScreen.line("screen.aliveworkplace.move.click", ChatFormatting.DARK_GRAY));
			menu.button(slot++, marked(VillageHallScreen.icon(pending.forBuilding(from).isPresent() ? Items.STICKY_PISTON : Items.BRICKS,
				Blueprints.displayName(f.structure()).copy(), ChatFormatting.WHITE, lore.toArray(Component[]::new)), LIST_MARK, 1), p -> {
				start(menu, level, hall, from, p);
				menu.broadcastChanges();
			});
		}
		if (buildings.size() > fit) {
			menu.button(ChoiceMenu.SIZE - 1, VillageHallScreen.icon(Items.PAPER, Component.translatable("screen.aliveworkplace.move.more", buildings.size() - fit),
				ChatFormatting.GRAY), null);
		}
	}

	/** The Move button of the building at {@code from}: its plan is taken and the picker opens on it. */
	static void start(ChoiceMenu menu, ServerLevel level, BlockPos hall, BlockPos from, ServerPlayer player) {
		Component refused = io.github.jcondedata.aliveworkplace.move.MoveRules.refusal(level, hall, from, player); // 45.6
		if (refused != null) {
			Chat.actionBar(player, refused.copy().withStyle(ChatFormatting.RED));
			level.playSound(null, player.blockPosition(), SoundEvents.VILLAGER_NO, SoundSource.NEUTRAL, 0.8f, 1f);
			backToList(menu, level, hall, player);
			return;
		}
		Optional<BuildSiteManager.Finished> building = BuildSiteManager.get(level).finishedInVillage(level, hall).stream()
			.filter(f -> f.placement().origin().equals(from)).findFirst();
		Optional<MovePlan> plan = building.flatMap(f -> MovePlan.capture(level, f));
		if (plan.isEmpty()) {
			Chat.actionBar(player, Component.translatable("message.aliveworkplace.move.cannot").withStyle(ChatFormatting.RED));
			level.playSound(null, player.blockPosition(), SoundEvents.VILLAGER_NO, SoundSource.NEUTRAL, 0.8f, 1f);
			backToList(menu, level, hall, player);
			return;
		}
		BuildSiteManager.Finished f = building.get();
		MovePlan p = plan.get();
		BoundingBox old = BlueprintLibrary.get(level, f.structure()).map(b -> BlueprintOutline.bounds(f.placement(), b.size()))
			.orElse(BoundingBox.fromCorners(from, from.offset(p.size().getX() - 1, p.size().getY() - 1, p.size().getZ() - 1)));
		Picking picking = new Picking(hall, from, f.structure(), p, old, old.minX() + p.size().getX() / 2, old.minZ() + p.size().getZ() / 2, Rotation.NONE);
		Optional<PendingMoves.Move> planned = PendingMoves.get(level).forBuilding(from);
		if (planned.isPresent()) { // open on the spot already planned
			Vec3i size = p.size(planned.get().rotation());
			picking = picking.turned(planned.get().rotation()).at(planned.get().to().getX() + size.getX() / 2, planned.get().to().getZ() + size.getZ() / 2);
		}
		PICKING.put(player.getUUID(), picking);
		MAPS.put(player.getUUID(), MapView.of(level, hall));
		menu.onClose(() -> stop(player));
		render(menu, level, player);
	}

	/** The picker for what {@code player} is picking: the building, the controls, the fit line and the map. */
	static void render(ChoiceMenu menu, ServerLevel level, ServerPlayer player) {
		Picking p = PICKING.get(player.getUUID());
		if (p == null) {
			return;
		}
		BlockPos hall = p.hall();
		MoveFit.Fit fit = fit(level, p);
		Direction front = p.plan().front(p.rotation());
		BlueprintOutline.glowMove(player, fit.box(), front, fit.fits(), GHOST_TICKS);
		menu.clearButtons();
		menu.button(BACK, VillageHallScreen.icon(Items.ARROW, Component.translatable("screen.aliveworkplace.move.back"), ChatFormatting.WHITE),
			pl -> cancel(menu, level, hall, pl));
		Component name = Blueprints.displayName(p.structure());
		menu.button(HEADER, VillageHallScreen.icon(Items.BRICKS, name.copy(), ChatFormatting.GOLD,
			VillageHallScreen.line(VillageHallScreen.where(hall, p.from()), ChatFormatting.GRAY),
			VillageHallScreen.line(Component.translatable("screen.aliveworkplace.move.size", p.size().getX(), p.size().getZ(), p.size().getY()), ChatFormatting.GRAY),
			VillageHallScreen.line(Component.translatable("screen.aliveworkplace.move.faces", direction(front)), ChatFormatting.GRAY)), null);
		Optional<PendingMoves.Move> planned = PendingMoves.get(level).forBuilding(p.from());
		if (planned.isPresent()) {
			menu.button(CALL_OFF, VillageHallScreen.icon(Items.COBWEB, Component.translatable("screen.aliveworkplace.move.call_off"), ChatFormatting.RED,
				VillageHallScreen.line(Component.translatable("screen.aliveworkplace.move.planned_to", planned.get().to().getX(), planned.get().to().getY(),
					planned.get().to().getZ()), ChatFormatting.GRAY)), pl -> {
				PendingMoves.get(level).remove(p.from());
				Chat.actionBar(pl, Component.translatable("message.aliveworkplace.move.called_off", name).withStyle(ChatFormatting.YELLOW));
				backToList(menu, level, hall, pl);
			});
		}
		menu.button(ROTATE, VillageHallScreen.icon(Items.COMPASS, Component.translatable("screen.aliveworkplace.move.rotate"), ChatFormatting.WHITE,
			VillageHallScreen.line(Component.translatable("screen.aliveworkplace.move.rotate_hint", direction(p.plan().front(p.rotation().getRotated(Rotation.CLOCKWISE_90)))),
				ChatFormatting.GRAY)), pl -> {
			Picking now = PICKING.get(pl.getUUID());
			if (now != null) {
				PICKING.put(pl.getUUID(), now.turned(now.rotation().getRotated(Rotation.CLOCKWISE_90)));
			}
			render(menu, level, pl);
			menu.broadcastChanges();
		});
		menu.button(VERDICT, verdict(fit), null);
		boolean mover = Movers.hired(level, hall);
		List<Component> confirmLore = new ArrayList<>();
		confirmLore.add(VillageHallScreen.line("screen.aliveworkplace.move.confirm_hint", ChatFormatting.GRAY));
		if (!fit.fits()) {
			confirmLore.add(VillageHallScreen.line(fit.line(), ChatFormatting.RED));
		}
		if (!mover) {
			confirmLore.add(VillageHallScreen.line("screen.aliveworkplace.move.needs_mover", ChatFormatting.RED));
		}
		menu.button(CONFIRM, VillageHallScreen.icon(fit.fits() && mover ? Items.PISTON : Items.GRAY_DYE, Component.translatable("screen.aliveworkplace.move.confirm"),
			fit.fits() && mover ? ChatFormatting.GREEN : ChatFormatting.GRAY, confirmLore.toArray(Component[]::new)), pl -> confirm(menu, level, pl));
		menu.button(CANCEL, VillageHallScreen.icon(Items.BARRIER, Component.translatable("screen.aliveworkplace.move.cancel"), ChatFormatting.RED,
			VillageHallScreen.line("screen.aliveworkplace.move.cancel_hint", ChatFormatting.GRAY)), pl -> cancel(menu, level, hall, pl));
		map(menu, level, p, fit);
		menu.button(GUIDE, guide(level, p, fit, mover), null);
		MapView view = MAPS.get(player.getUUID());
		if (view != null) {
			menu.button(VIEW, view(level, p, fit, view), null);
		}
	}

	/** The step the picker is on: 3 (confirm) once the spot fits, else 2 (pick the new spot). */
	public static int step(MoveFit.Fit fit) {
		return fit.fits() ? 3 : 2;
	}

	/**
	 * The guide (B1163): its name says what to do now ("Step 2 of 3: click the map where ..."), its lines where the building
	 * stands now and where it would go (from the Hall), what the Mover will do, about how long, and whether a Mover works here.
	 */
	static ItemStack guide(ServerLevel level, Picking p, MoveFit.Fit fit, boolean mover) {
		Component name = Blueprints.displayName(p.structure());
		Component instruction = fit.fits()
			? Component.translatable(mover ? "screen.aliveworkplace.move.step3" : "screen.aliveworkplace.move.step3_no_mover", name)
			: Component.translatable(fit.reason() == MoveFit.Reason.SAME_SPOT ? "screen.aliveworkplace.move.step2" : "screen.aliveworkplace.move.step2_refused", name);
		BlockPos hall = p.hall();
		BlockPos now = p.old().getCenter();
		Component spot = fit.reason() == MoveFit.Reason.SAME_SPOT ? Component.translatable("screen.aliveworkplace.move.spot_none")
			: Component.translatable("screen.aliveworkplace.move.spot", fromHall(hall, fit.box().getCenter()));
		int minutes = minutes(p.plan());
		ItemStack icon = VillageHallScreen.icon(Items.FILLED_MAP, instruction, fit.fits() ? ChatFormatting.GREEN : ChatFormatting.GOLD,
			VillageHallScreen.line(Component.translatable("screen.aliveworkplace.move.now", fromHall(hall, now)), ChatFormatting.YELLOW),
			VillageHallScreen.line(spot, fit.fits() ? ChatFormatting.GREEN : fit.reason() == MoveFit.Reason.SAME_SPOT ? ChatFormatting.GRAY : ChatFormatting.RED),
			VillageHallScreen.line("screen.aliveworkplace.move.plan", ChatFormatting.GRAY),
			VillageHallScreen.line(Words.counted("screen.aliveworkplace.move.time", minutes, minutes), ChatFormatting.GRAY),
			VillageHallScreen.line(mover ? "screen.aliveworkplace.move.mover_here" : "screen.aliveworkplace.move.no_mover_how",
				mover ? ChatFormatting.GREEN : ChatFormatting.YELLOW));
		return marked(icon, STEP_MARK, step(fit));
	}

	/** About how many minutes one Mover works on {@code plan}: each block taken down and put up again, a step each, and walking. */
	public static int minutes(MovePlan plan) {
		long ticks = (long) plan.blocks().size() * 2 * io.github.jcondedata.aliveworkplace.move.MoverWork.STEP_TICKS * 5 / 4 + 600;
		return (int) Math.max(1, (ticks + 1199) / 1200);
	}

	/** "17 blocks north-west of the Hall", or "next to the Hall". */
	static Component fromHall(BlockPos hall, BlockPos pos) {
		double dx = pos.getX() - hall.getX();
		double dz = pos.getZ() - hall.getZ();
		return dx * dx + dz * dz < 16 ? Component.translatable("screen.aliveworkplace.move.by_hall")
			: Component.translatable("screen.aliveworkplace.move.from_hall", VillageHallScreen.where(hall, pos));
	}

	/**
	 * The village map for the client to draw (B1163): the land and its roads, the Hall, the village's edge, every other
	 * building (its box, its name a line of the icon), where the building stands now and the new spot with its front.
	 */
	static ItemStack view(ServerLevel level, Picking p, MoveFit.Fit fit, MapView map) {
		List<MoveFit.Other> others = MoveFit.others(level, p.hall(), p.from());
		List<Component> names = new ArrayList<>();
		int[] boxes = new int[others.size() * 4];
		for (int i = 0; i < others.size(); i++) {
			BoundingBox b = others.get(i).box();
			boxes[i * 4] = b.minX();
			boxes[i * 4 + 1] = b.minZ();
			boxes[i * 4 + 2] = b.maxX();
			boxes[i * 4 + 3] = b.maxZ();
			names.add(VillageHallScreen.line(others.get(i).name(), ChatFormatting.WHITE));
		}
		CompoundTag tag = new CompoundTag();
		tag.putInt("ox", map.ox());
		tag.putInt("oz", map.oz());
		tag.putInt("step", map.step());
		tag.putByteArray("colors", map.colors());
		tag.putByteArray("kinds", map.kinds());
		tag.putInt("hallX", p.hall().getX());
		tag.putInt("hallZ", p.hall().getZ());
		tag.putInt("radius", VillageHalls.RADIUS);
		tag.putIntArray("others", boxes);
		tag.putIntArray("old", new int[]{p.old().minX(), p.old().minZ(), p.old().maxX(), p.old().maxZ()});
		tag.putIntArray("spot", new int[]{fit.box().minX(), fit.box().minZ(), fit.box().maxX(), fit.box().maxZ()});
		tag.putInt("front", p.plan().front(p.rotation()).get2DDataValue());
		tag.putInt("fit", fit.fits() ? 0 : fit.reason() == MoveFit.Reason.SAME_SPOT ? 1 : 2);
		ItemStack icon = VillageHallScreen.icon(Items.MAP, VillageHalls.name(level, p.hall()).copy(), ChatFormatting.WHITE, names.toArray(Component[]::new));
		CompoundTag data = icon.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag();
		data.put(VIEW_MARK, tag);
		icon.set(DataComponents.CUSTOM_DATA, CustomData.of(data));
		return icon;
	}

	private static ItemStack marked(ItemStack icon, String key, int value) {
		CompoundTag tag = icon.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag();
		tag.putInt(key, value);
		icon.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));
		return icon;
	}

	/** Whether menu-button click {@code id} is a click on the picker's village map. */
	public static boolean handles(int id) {
		return id >= MAP_CLICK && id < MAP_CLICK + MAP * MAP;
	}

	/** A click on the village map (B1163): the new spot's middle goes on the block clicked. */
	public static boolean click(ChoiceMenu menu, ServerLevel level, ServerPlayer player, int id) {
		Picking now = PICKING.get(player.getUUID());
		MapView view = MAPS.get(player.getUUID());
		if (now == null || view == null || !handles(id)) {
			return false;
		}
		int index = id - MAP_CLICK;
		BlockPos at = view.at(index % MAP, index / MAP);
		PICKING.put(player.getUUID(), now.at(at.getX(), at.getZ()));
		render(menu, level, player);
		menu.broadcastChanges();
		return true;
	}

	/** The menu-button number of a click on the village map at block ({@code x}, {@code z}), or -1 off the map (tests, the harness). */
	public static int mapClick(ServerPlayer player, int x, int z) {
		MapView view = MAPS.get(player.getUUID());
		if (view == null) {
			return -1;
		}
		int px = Math.floorDiv(x - view.ox(), view.step());
		int pz = Math.floorDiv(z - view.oz(), view.step());
		return px < 0 || pz < 0 || px >= MAP || pz >= MAP ? -1 : MAP_CLICK + pz * MAP + px;
	}

	/** The fit line's icon: green when the spot fits, red with the reason when it's refused, yellow before a spot is picked. */
	static ItemStack verdict(MoveFit.Fit fit) {
		Item item = fit.fits() ? Items.LIME_DYE : fit.reason() == MoveFit.Reason.SAME_SPOT ? Items.YELLOW_DYE : Items.RED_DYE;
		ChatFormatting color = fit.fits() ? ChatFormatting.GREEN : fit.reason() == MoveFit.Reason.SAME_SPOT ? ChatFormatting.YELLOW : ChatFormatting.RED;
		return VillageHallScreen.icon(item, fit.line().copy(), color,
			VillageHallScreen.line(Component.translatable("screen.aliveworkplace.move.new_corner", fit.min().getX(), fit.min().getY(), fit.min().getZ()),
				ChatFormatting.GRAY));
	}

	/** The map: a cell every {@link #STEP} blocks round the new spot's middle, north at the top; a click puts the spot there. */
	private static void map(ChoiceMenu menu, ServerLevel level, Picking p, MoveFit.Fit fit) {
		List<MoveFit.Other> others = MoveFit.others(level, p.hall(), p.from());
		for (int row = 0; row < ROWS; row++) {
			for (int col = 0; col < COLUMNS; col++) {
				int x = p.x() + (col - 4) * STEP;
				int z = p.z() + (row - 2) * STEP;
				int slot = VillageHallScreen.FIRST_ROW + row * COLUMNS + col;
				ItemStack icon = cell(level, p, fit, others, x, z);
				CompoundTag tag = icon.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag();
				tag.putInt(MAP_MARK, slot == CENTRE ? 2 : 1);
				icon.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));
				menu.button(slot, icon, pl -> {
					Picking now = PICKING.get(pl.getUUID());
					if (now != null) {
						PICKING.put(pl.getUUID(), now.at(x, z));
					}
					render(menu, level, pl);
					menu.broadcastChanges();
				});
			}
		}
	}

	/** One map cell: what is at ({@code x}, {@code z}), with where and the click. */
	private static ItemStack cell(ServerLevel level, Picking p, MoveFit.Fit fit, List<MoveFit.Other> others, int x, int z) {
		BlockPos hall = p.hall();
		BlockPos ground = new BlockPos(x, MoveFit.groundY(level, x, z), z);
		Item item;
		Component what;
		ChatFormatting color = ChatFormatting.WHITE;
		if (fit.box().minX() <= x && fit.box().maxX() >= x && fit.box().minZ() <= z && fit.box().maxZ() >= z) {
			boolean same = fit.reason() == MoveFit.Reason.SAME_SPOT;
			item = fit.fits() ? Items.LIME_STAINED_GLASS : same ? Items.YELLOW_STAINED_GLASS : Items.RED_STAINED_GLASS;
			what = Component.translatable("screen.aliveworkplace.move.map.spot", Blueprints.displayName(p.structure()));
			color = fit.fits() ? ChatFormatting.GREEN : same ? ChatFormatting.YELLOW : ChatFormatting.RED;
		} else if (hall.getX() >= x - STEP / 2 && hall.getX() < x + STEP / 2 && hall.getZ() >= z - STEP / 2 && hall.getZ() < z + STEP / 2) {
			item = Items.BELL;
			what = Component.translatable("screen.aliveworkplace.move.the_hall");
		} else if (p.old().minX() <= x && p.old().maxX() >= x && p.old().minZ() <= z && p.old().maxZ() >= z) {
			item = Items.YELLOW_STAINED_GLASS;
			what = Component.translatable("screen.aliveworkplace.move.map.now");
			color = ChatFormatting.YELLOW;
		} else if (!VillageHalls.contains(hall, new BlockPos(x, hall.getY(), z))) {
			item = Items.GRAY_STAINED_GLASS;
			what = Component.translatable("screen.aliveworkplace.move.map.outside");
			color = ChatFormatting.GRAY;
		} else {
			MoveFit.Other other = null;
			for (MoveFit.Other o : others) {
				if (o.box().minX() <= x && o.box().maxX() >= x && o.box().minZ() <= z && o.box().maxZ() >= z) {
					other = o;
				}
			}
			BlockState top = level.getBlockState(ground);
			if (other != null) {
				item = Items.BRICKS;
				what = other.name().copy();
			} else if (top.getFluidState().is(FluidTags.WATER)) {
				item = Items.BLUE_STAINED_GLASS;
				what = Component.translatable("screen.aliveworkplace.move.map.water");
				color = ChatFormatting.AQUA;
			} else if (io.github.jcondedata.aliveworkplace.city.RoadStyles.isPaving(top)) {
				item = Items.DIRT_PATH;
				what = Component.translatable("screen.aliveworkplace.move.map.road");
			} else {
				Item block = top.getBlock().asItem();
				item = block == Items.AIR ? Items.GRASS_BLOCK : block;
				what = Component.translatable("screen.aliveworkplace.move.map.ground");
				color = ChatFormatting.GRAY;
			}
		}
		return VillageHallScreen.icon(item, what, color,
			VillageHallScreen.line(VillageHallScreen.where(hall, ground), ChatFormatting.GRAY),
			VillageHallScreen.line("screen.aliveworkplace.move.map.click", ChatFormatting.DARK_GRAY));
	}

	/** Confirm: the spot must fit and a Mover work in the village; then the move is saved for the Mover to take. */
	static void confirm(ChoiceMenu menu, ServerLevel level, ServerPlayer player) {
		Picking p = PICKING.get(player.getUUID());
		if (p == null) {
			return;
		}
		MoveFit.Fit fit = fit(level, p);
		Component refusal = io.github.jcondedata.aliveworkplace.move.MoveRules.refusal(level, p.hall(), p.from(), player); // 45.6: asked again
		if (refusal == null) {
			refusal = !fit.fits() ? fit.line() : !Movers.hired(level, p.hall()) ? Component.translatable("message.aliveworkplace.move.needs_mover") : null;
		}
		if (refusal != null) {
			Chat.actionBar(player, refusal.copy().withStyle(ChatFormatting.RED));
			level.playSound(null, player.blockPosition(), SoundEvents.VILLAGER_NO, SoundSource.NEUTRAL, 0.8f, 1f);
			render(menu, level, player);
			menu.broadcastChanges();
			return;
		}
		PendingMoves.get(level).put(new PendingMoves.Move(p.hall(), p.from(), p.structure(), fit.min(), p.rotation(), player.getUUID()));
		Chat.actionBar(player, Component.translatable("message.aliveworkplace.move.planned", Blueprints.displayName(p.structure()),
			fit.min().getX(), fit.min().getY(), fit.min().getZ()).withStyle(ChatFormatting.GREEN));
		level.playSound(null, player.blockPosition(), SoundEvents.VILLAGER_YES, SoundSource.NEUTRAL, 0.8f, 1f);
		backToList(menu, level, p.hall(), player);
	}

	/** Cancel (or Back): the pick is forgotten, the ghost goes, and the list shows again; nothing is saved. */
	static void cancel(ChoiceMenu menu, ServerLevel level, BlockPos hall, ServerPlayer player) {
		stop(player);
		backToList(menu, level, hall, player);
	}

	/** Forgets what {@code player} was picking and takes the ghost away. */
	public static void stop(ServerPlayer player) {
		PICKING.remove(player.getUUID());
		MAPS.remove(player.getUUID());
		BlueprintOutline.stopMove(player);
	}

	/** "north", "east", ... as the player reads it. */
	static Component direction(Direction d) {
		return Component.translatable("screen.aliveworkplace.move.direction." + d.getName());
	}

	private static void backToList(ChoiceMenu menu, ServerLevel level, BlockPos hall, ServerPlayer p) {
		HallPages.Page shown = page;
		if (shown == null) {
			for (HallPages.Page each : HallPages.all()) {
				if (each.id().equals(PAGE)) {
					shown = each;
				}
			}
		}
		if (shown != null) {
			VillageHallScreen.renderPage(menu, level, hall, shown, p);
		} else {
			VillageHallScreen.showList(menu, level, hall, 0);
		}
		menu.broadcastChanges();
	}

	private MovePage() {
	}
}
