package io.github.jcondedata.aliveworkplace.blueprint;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.github.jcondedata.aliveworkplace.AliveWorkplace;
import io.github.jcondedata.aliveworkplace.blueprint.io.BlueprintFiles;
import io.github.jcondedata.aliveworkplace.blueprint.io.BlueprintImporter;
import io.github.jcondedata.aliveworkplace.mc.Chat;
import io.github.jcondedata.aliveworkplace.mc.Lookup;
import io.github.jcondedata.aliveworkplace.mc.Players;
import io.github.jcondedata.aliveworkplace.registry.ModComponents;
import io.github.jcondedata.aliveworkplace.registry.ModItems;
import io.github.jcondedata.aliveworkplace.work.ChoiceList;
import io.github.jcondedata.aliveworkplace.work.ChoiceMenu;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Vec3i;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.ItemLore;
import net.minecraft.world.level.EmptyBlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.FallingBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplateManager;

/**
 * Shapes for the builders (the Shape Planner): a box (a wall, a floor, a room), a cylinder (a tower, a ring wall), a
 * dome, a sphere, a cone, a pyramid or an arch, any size up to {@link #MAX} a side, solid or hollow, in one of the
 * blocks the player carries. Drawing one turns a Blank Blueprint into a blueprint of it (saved under
 * {@code shapes/<player>/}), which a builder builds like any other. Hollow shapes are cleared inside.
 */
public final class Shapes {
	public static final int MAX = 32;

	public enum Kind {
		BOX(Items.BRICKS), CYLINDER(Items.CAULDRON), DOME(Items.TURTLE_HELMET), SPHERE(Items.SLIME_BALL), CONE(Items.POINTED_DRIPSTONE),
		PYRAMID(Items.SANDSTONE_STAIRS), ARCH(Items.STONE_BRICK_STAIRS);

		final Item icon;

		Kind(Item icon) {
			this.icon = icon;
		}

		public String id() {
			return name().toLowerCase(Locale.ROOT);
		}

		static Kind parse(String id) {
			for (Kind kind : values()) {
				if (kind.id().equals(id)) {
					return kind;
				}
			}
			return BOX;
		}
	}

	/** What a Shape Planner is set to (kept on the item). */
	public record Settings(String kind, int width, int height, int depth, boolean hollow, Optional<ResourceLocation> material) {
		public static final Settings DEFAULT = new Settings("box", 7, 4, 7, true, Optional.empty());
		public static final Codec<Settings> CODEC = RecordCodecBuilder.create(i -> i.group(
			Codec.STRING.fieldOf("kind").forGetter(Settings::kind),
			Codec.INT.fieldOf("width").forGetter(Settings::width),
			Codec.INT.fieldOf("height").forGetter(Settings::height),
			Codec.INT.fieldOf("depth").forGetter(Settings::depth),
			Codec.BOOL.fieldOf("hollow").forGetter(Settings::hollow),
			ResourceLocation.CODEC.optionalFieldOf("material").forGetter(Settings::material)
		).apply(i, Settings::new));
		public static final StreamCodec<RegistryFriendlyByteBuf, Settings> STREAM_CODEC = ByteBufCodecs.fromCodecWithRegistries(CODEC);

		public Kind shape() {
			return Kind.parse(kind);
		}

		Settings with(Kind k) {
			return new Settings(k.id(), width, height, depth, hollow, material);
		}

		Settings size(int w, int h, int d) {
			return new Settings(kind, clamp(w), clamp(h), clamp(d), hollow, material);
		}

		Settings hollow(boolean on) {
			return new Settings(kind, width, height, depth, on, material);
		}

		Settings material(Item item) {
			return new Settings(kind, width, height, depth, hollow, Optional.of(BuiltInRegistries.ITEM.getKey(item)));
		}

		/** The block to build it in (stone bricks until one is picked). */
		public BlockState block() {
			return material.map(BuiltInRegistries.ITEM::get).filter(i -> i instanceof BlockItem).map(i -> ((BlockItem) i).getBlock().defaultBlockState())
				.orElse(Blocks.STONE_BRICKS.defaultBlockState());
		}
	}

	static int clamp(int v) {
		return Math.max(1, Math.min(MAX, v));
	}

	/** The cells of a shape: true where there's a block, false where it's cleared (inside a hollow shape), absent elsewhere. */
	public static Boolean[][][] cells(Kind kind, int w, int h, int d, boolean hollow) {
		boolean[][][] solid = new boolean[w][h][d];
		for (int x = 0; x < w; x++) {
			for (int y = 0; y < h; y++) {
				for (int z = 0; z < d; z++) {
					solid[x][y][z] = inside(kind, x, y, z, w, h, d);
				}
			}
		}
		Boolean[][][] out = new Boolean[w][h][d];
		boolean openTop = kind == Kind.CYLINDER;
		boolean openBottom = kind == Kind.DOME || kind == Kind.CONE || kind == Kind.PYRAMID;
		for (int x = 0; x < w; x++) {
			for (int y = 0; y < h; y++) {
				for (int z = 0; z < d; z++) {
					if (kind == Kind.ARCH) {
						out[x][y][z] = solid[x][y][z] ? Boolean.TRUE : Boolean.FALSE; // the opening is cleared
					} else if (solid[x][y][z]) {
						boolean shell = !hollow
							|| x == 0 || x == w - 1 || z == 0 || z == d - 1
							|| y == 0 && !openBottom || y == h - 1 && !openTop
							|| !solid[x - 1][y][z] || !solid[x + 1][y][z] || !solid[x][y][z - 1] || !solid[x][y][z + 1]
							|| y > 0 && !solid[x][y - 1][z] || y < h - 1 && !solid[x][y + 1][z];
						out[x][y][z] = shell;
					}
				}
			}
		}
		return out;
	}

	private static boolean inside(Kind kind, int x, int y, int z, int w, int h, int d) {
		double u = (x + 0.5 - w / 2.0) / (w / 2.0);
		double v = (z + 0.5 - d / 2.0) / (d / 2.0);
		return switch (kind) {
			case BOX -> true;
			case CYLINDER -> u * u + v * v <= 1.0;
			case DOME -> {
				double t = (y + 0.5) / h;
				yield u * u + v * v + t * t <= 1.0;
			}
			case SPHERE -> {
				double t = (y + 0.5 - h / 2.0) / (h / 2.0);
				yield u * u + v * v + t * t <= 1.0;
			}
			case CONE -> {
				double r = 1.0 - (double) y / h;
				yield u * u + v * v <= r * r;
			}
			case PYRAMID -> x >= y && x < w - y && z >= y && z < d - y;
			case ARCH -> {
				// A wall with a round-topped opening, one block of wall either side and at least one over it.
				double rx = (w - 2) / 2.0;
				double ry = Math.max(1, h - 1);
				double du = (x + 0.5 - w / 2.0) / Math.max(0.5, rx);
				double dv = (y + 0.5) / ry;
				yield w < 3 || du * du + dv * dv > 1.0;
			}
		};
	}

	/** The blueprint of {@code settings} (called {@code id}). */
	public static Blueprint blueprint(ResourceLocation id, Settings settings) {
		Boolean[][][] cells = cells(settings.shape(), settings.width(), settings.height(), settings.depth(), settings.hollow());
		BlockState block = settings.block();
		BlockState air = Blocks.AIR.defaultBlockState();
		List<Blueprint.Entry> entries = new ArrayList<>();
		for (int x = 0; x < settings.width(); x++) {
			for (int y = 0; y < settings.height(); y++) {
				for (int z = 0; z < settings.depth(); z++) {
					Boolean cell = cells[x][y][z];
					if (cell != null) {
						entries.add(new Blueprint.Entry(new BlockPos(x, y, z), cell ? block : air, null));
					}
				}
			}
		}
		return new Blueprint(id, new Vec3i(settings.width(), settings.height(), settings.depth()), entries);
	}

	/** How many blocks {@code settings} takes. */
	public static int count(Settings settings) {
		Boolean[][][] cells = cells(settings.shape(), settings.width(), settings.height(), settings.depth(), settings.hollow());
		int n = 0;
		for (Boolean[][] plane : cells) {
			for (Boolean[] row : plane) {
				for (Boolean cell : row) {
					if (Boolean.TRUE.equals(cell)) {
						n++;
					}
				}
			}
		}
		return n;
	}

	/** Blocks a player can build a shape in: full, plain blocks from their inventory (no chests, no sand), up to nine. */
	public static List<Item> materials(ServerPlayer player) {
		Set<Item> found = new LinkedHashSet<>();
		List<ItemStack> stacks = new ArrayList<>();
		stacks.add(player.getOffhandItem());
		for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
			stacks.add(player.getInventory().getItem(i));
		}
		for (ItemStack stack : stacks) {
			if (found.size() < 9 && stack.getItem() instanceof BlockItem item && buildable(item.getBlock())) {
				found.add(item);
			}
		}
		return new ArrayList<>(found);
	}

	static boolean buildable(Block block) {
		BlockState state = block.defaultBlockState();
		return !(block instanceof EntityBlock) && !(block instanceof FallingBlock) && !state.isAir()
			&& state.isCollisionShapeFullBlock(EmptyBlockGetter.INSTANCE, BlockPos.ZERO);
	}

	// --- the Shape Planner's screen -------------------------------------------------------------------------------

	public static final int FIRST_KIND = 1;
	public static final int FIRST_MATERIAL = 9;
	public static final int WIDTH = 28;
	public static final int HEIGHT = 31;
	public static final int DEPTH = 34;
	public static final int HOLLOW = 38;
	public static final int INFO = 40;
	public static final int DRAW = 49;
	/**
	 * The list screen's own rows (ROADMAP 42.4): the shape row (the shapes as icon cells), the block row (the blocks as
	 * icon cells), the Draw row (its Draw button a cell) and the screen's own line (the count at the top, the hint).
	 */
	public static final int SHAPE_ROW = 0;
	public static final int BLOCK_ROW = 8;
	public static final int DRAW_ROW = 48;
	public static final int LIST_INFO = 53;

	/** Opens the planner's screen for {@code player}, holding the planner in {@code hand}: the list screen (42.4). */
	public static void open(ServerPlayer player, InteractionHand hand) {
		ChoiceMenu.openList(player, Component.translatable("screen.aliveworkplace.shapes"),
			p -> p.getItemInHand(hand).is(ModItems.SHAPE_PLANNER), menu -> fill(menu, player, hand));
	}

	/** Lays out the screen (tests call it on a detached menu). */
	public static void fill(ChoiceMenu menu, ServerPlayer player, InteractionHand hand) {
		menu.clearButtons();
		ItemStack planner = player.getItemInHand(hand);
		Settings s = planner.getOrDefault(ModComponents.SHAPE, Settings.DEFAULT);
		Runnable redraw = () -> fill(menu, player, hand);
		// The list (42.4): a row for the shape with the shapes as icon cells (the chosen one gold), a row for the block with
		// the blocks carried as icon cells, a row per size with - and +, solid or hollow, the size and Draw. Every button
		// keeps its slot, its name (the tooltip) and its click.
		int[] kindSlots = new int[Kind.values().length];
		for (Kind kind : Kind.values()) {
			kindSlots[kind.ordinal()] = FIRST_KIND + kind.ordinal();
		}
		menu.button(SHAPE_ROW, ChoiceList.cells(ChoiceList.row(label(new ItemStack(s.shape().icon), Component.translatable("screen.aliveworkplace.shapes.shape_row",
			Component.translatable("shape.aliveworkplace." + s.shape().id())), false), null, null, null, false, false), kindSlots), null);
		for (Kind kind : Kind.values()) {
			ItemStack icon = label(new ItemStack(kind.icon), Component.translatable("shape.aliveworkplace." + kind.id()), kind == s.shape());
			ChoiceList.cell(icon, null, kind == s.shape());
			menu.button(FIRST_KIND + kind.ordinal(), icon, p -> {
				set(player, hand, current(player, hand).with(kind));
				redraw.run();
			});
		}
		List<Item> materials = materials(player);
		int[] materialSlots = new int[Math.max(1, materials.size())];
		materialSlots[0] = FIRST_MATERIAL + 4; // (the barrier, when there are none)
		for (int i = 0; i < materials.size(); i++) {
			materialSlots[i] = FIRST_MATERIAL + i;
		}
		ItemStack blockRow = label(s.material().isPresent() ? new ItemStack(s.block().getBlock()) : new ItemStack(Items.BARRIER), s.material().isPresent()
			? Component.translatable("screen.aliveworkplace.shapes.block_row", s.block().getBlock().getName())
			: Component.translatable("screen.aliveworkplace.shapes.block_none"), false);
		menu.button(BLOCK_ROW, ChoiceList.cells(ChoiceList.row(blockRow, materials.isEmpty() ? Component.translatable("screen.aliveworkplace.shapes.no_blocks") : null,
			null, null, false, false), materialSlots), null);
		for (int i = 0; i < materials.size(); i++) {
			Item item = materials.get(i);
			boolean chosen = s.material().map(m -> m.equals(BuiltInRegistries.ITEM.getKey(item))).orElse(false);
			menu.button(FIRST_MATERIAL + i, ChoiceList.cell(label(new ItemStack(item), item.getDescription().copy(), chosen), null, chosen), p -> {
				set(player, hand, current(player, hand).material(item));
				redraw.run();
			});
		}
		if (materials.isEmpty()) {
			menu.button(FIRST_MATERIAL + 4, ChoiceList.cell(label(new ItemStack(Items.BARRIER), Component.translatable("screen.aliveworkplace.shapes.no_blocks"), false),
				null, false), null);
		}
		menu.divider(2);
		dimension(menu, player, hand, WIDTH, "width", s.width(), (t, v) -> t.size(v, t.height(), t.depth()), Settings::width, redraw);
		dimension(menu, player, hand, HEIGHT, "height", s.height(), (t, v) -> t.size(t.width(), v, t.depth()), Settings::height, redraw);
		dimension(menu, player, hand, DEPTH, "depth", s.depth(), (t, v) -> t.size(t.width(), t.height(), v), Settings::depth, redraw);
		if (s.shape() != Kind.ARCH) {
			// (the whole row is the switch, as the chest's icon was)
			menu.button(HOLLOW, ChoiceList.row(label(new ItemStack(s.hollow() ? Items.GLASS : Items.STONE),
				Component.translatable(s.hollow() ? "screen.aliveworkplace.shapes.hollow" : "screen.aliveworkplace.shapes.solid"), false), null, null, null, false, false), p -> {
				Settings t = current(player, hand);
				set(player, hand, t.hollow(!t.hollow()));
				redraw.run();
			});
		}
		ItemStack info = label(new ItemStack(Items.BOOK), Component.translatable("screen.aliveworkplace.shapes.info",
			s.width(), s.height(), s.depth()), false);
		info.set(DataComponents.LORE, new ItemLore(List.of(io.github.jcondedata.aliveworkplace.work.Words.counted("screen.aliveworkplace.shapes.count", count(s), count(s),
			s.block().getBlock().getName()).withStyle(ChatFormatting.GRAY))));
		menu.button(INFO, ChoiceList.row(info, null, null, null, false, false), null);
		// The screen's own line: how many blocks it takes at the top right (the book's tooltip on it), the hint at the foot.
		ItemStack own = info.copy();
		ChoiceList.info(own, io.github.jcondedata.aliveworkplace.work.Words.counted("screen.aliveworkplace.shapes.count", count(s), count(s),
			s.block().getBlock().getName()), Component.translatable("screen.aliveworkplace.shapes.list_hint"), List.of());
		menu.button(LIST_INFO, own, null);
		menu.button(DRAW_ROW, ChoiceList.cells(ChoiceList.row(label(new ItemStack(ModItems.BLANK_BLUEPRINT), Component.translatable("screen.aliveworkplace.shapes.draw"), false),
			null, null, null, false, false), DRAW), null);
		menu.button(DRAW, ChoiceList.cell(label(new ItemStack(ModItems.BLANK_BLUEPRINT), Component.translatable("screen.aliveworkplace.shapes.draw"), true),
			Component.translatable("screen.aliveworkplace.shapes.button_draw"), true), p -> {
			Component result = draw(Players.level(player), player, current(player, hand));
			Chat.chat(player, result);
			player.closeContainer();
		});
	}

	private interface Resize {
		Settings apply(Settings s, int value);
	}

	private static void dimension(ChoiceMenu menu, ServerPlayer player, InteractionHand hand, int slot, String name, int value, Resize resize,
								  java.util.function.ToIntFunction<Settings> get, Runnable redraw) {
		ItemStack shown = label(new ItemStack(Items.PAPER, Math.max(1, Math.min(64, value))),
			Component.translatable("screen.aliveworkplace.shapes." + name, value), false);
		// The list (42.4): the size's row with - and + in it (their names, the tooltips, say shift-click for 5).
		menu.button(slot, ChoiceList.cells(ChoiceList.row(shown, null, null, null, false, false), slot - 1, slot + 1), null);
		menu.button(slot - 1, ChoiceList.cell(label(new ItemStack(Items.RED_STAINED_GLASS_PANE), Component.translatable("screen.aliveworkplace.shapes.less"), false),
			Component.literal("-"), false), p -> {
			Settings t = current(player, hand);
			set(player, hand, resize.apply(t, get.applyAsInt(t) - (menu.shiftClicked() ? 5 : 1)));
			redraw.run();
		});
		menu.button(slot + 1, ChoiceList.cell(label(new ItemStack(Items.LIME_STAINED_GLASS_PANE), Component.translatable("screen.aliveworkplace.shapes.more"), false),
			Component.literal("+"), false), p -> {
			Settings t = current(player, hand);
			set(player, hand, resize.apply(t, get.applyAsInt(t) + (menu.shiftClicked() ? 5 : 1)));
			redraw.run();
		});
	}

	private static Settings current(ServerPlayer player, InteractionHand hand) {
		return player.getItemInHand(hand).getOrDefault(ModComponents.SHAPE, Settings.DEFAULT);
	}

	private static void set(ServerPlayer player, InteractionHand hand, Settings settings) {
		ItemStack stack = player.getItemInHand(hand);
		if (stack.is(ModItems.SHAPE_PLANNER)) {
			stack.set(ModComponents.SHAPE, settings);
		}
	}

	private static ItemStack label(ItemStack icon, Component name, boolean glint) {
		icon.set(DataComponents.ITEM_NAME, name);
		if (glint) {
			icon.set(DataComponents.ENCHANTMENT_GLINT_OVERRIDE, true);
		}
		return icon;
	}

	/**
	 * Draws {@code settings} up as a blueprint for a Blank Blueprint from {@code player}'s inventory (none needed in
	 * creative) and gives it to them. The same shape in the same block is drawn once and handed out again after.
	 */
	public static Component draw(ServerLevel level, ServerPlayer player, Settings settings) {
		if (settings.material().isEmpty()) {
			return Component.translatable("message.aliveworkplace.shapes.no_block").withStyle(ChatFormatting.YELLOW);
		}
		int blank = -1;
		for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
			if (player.getInventory().getItem(i).is(ModItems.BLANK_BLUEPRINT)) {
				blank = i;
				break;
			}
		}
		if (blank < 0 && !player.getAbilities().instabuild) {
			return Component.translatable("message.aliveworkplace.shapes.no_blank").withStyle(ChatFormatting.YELLOW);
		}
		ResourceLocation material = settings.material().get();
		String name = settings.shape().id() + "_" + settings.width() + "x" + settings.height() + "x" + settings.depth()
			+ (settings.hollow() && settings.shape() != Kind.ARCH ? "_hollow_" : "_")
			+ (material.getNamespace().equals("minecraft") ? "" : material.getNamespace() + "_") + material.getPath().replace('/', '_');
		ResourceLocation id = AliveWorkplace.id("shapes/" + BlueprintImporter.sanitizeFolder(player.getGameProfile().getName()) + "/" + name);
		StructureTemplateManager manager = level.getServer().getStructureManager();
		if (manager.get(id).isEmpty()) {
			StructureTemplate template = manager.getOrCreate(id);
			template.load(Lookup.lookup(BuiltInRegistries.BLOCK), BlueprintFiles.toStructureNbt(blueprint(id, settings)));
			template.setAuthor(player.getGameProfile().getName());
			if (!manager.save(id)) {
				manager.remove(id);
				return Component.translatable("message.aliveworkplace.shapes.failed").withStyle(ChatFormatting.RED);
			}
		}
		if (blank >= 0 && !player.getAbilities().instabuild) {
			player.getInventory().getItem(blank).shrink(1);
		}
		Vec3i size = new Vec3i(settings.width(), settings.height(), settings.depth());
		ItemStack blueprint = BlueprintItem.create(id, size);
		if (!player.getInventory().add(blueprint)) {
			player.drop(blueprint, false);
		}
		level.playSound(null, player.blockPosition(), SoundEvents.BOOK_PAGE_TURN, SoundSource.PLAYERS, 1f, 1f);
		return io.github.jcondedata.aliveworkplace.work.Words.counted("message.aliveworkplace.shapes.drawn", count(settings), Blueprints.displayName(id), count(settings),
			settings.block().getBlock().getName()).withStyle(ChatFormatting.GREEN);
	}

	private Shapes() {
	}
}
