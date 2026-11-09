package io.github.jcondedata.aliveworkplace.build;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;
import com.google.gson.JsonPrimitive;
import io.github.jcondedata.aliveworkplace.mc.Nbt;
import net.minecraft.locale.Language;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Cleans the words a blueprint's block data carries (a sign's lines, a banner's or a chest's name, any mod's text saved
 * as a text component) before a builder copies them (B1044, B1047). A blueprint can be any player's file, so its words
 * keep only what a player could write by hand: the letters, colors and glow. Gone are click events (a sign runs a
 * run_command with permission level 2 for whoever clicks it), the nbt, selector and score parts the game works out with
 * permission level 2 when a sign loads (they show other players' data), and lines longer than the 384 characters a player
 * can write on a sign or whose saved JSON is far bigger than a player's line (huge lines make the chunk's packet too big to
 * send, B1065). Works on the text's saved JSON, read with the same lenient reader the game uses (it skips comments and
 * a {@code )]}'} prefix, B1062), so it covers every mod's text the same way.
 */
final class BlockText {
	/** The most characters a player can write on a sign's line (vanilla trims each edited line to this). */
	static final int MAX_LINE = 384;
	/** A name keeps at most as many characters as a sign's line. */
	static final int MAX_NAME = MAX_LINE;
	/**
	 * A line's (or name's) saved JSON longer than this is cut back to its plain words (B1065): room for a player's longest
	 * line with every letter escaped, plus a little style. A sign then carries at most about 16 KB (16 lines with the back
	 * and the filtered copies), near what a player's sign can, so a chunk full of them stays far under a packet's limit.
	 */
	static final int MAX_JSON = 2 * MAX_LINE + 256;
	private static final Gson GSON = new GsonBuilder().disableHtmlEscaping().create();

	/** Cleans every text in {@code tag}, at any depth, in place. */
	static void cleanAll(CompoundTag tag) {
		for (String key : Nbt.keys(tag).toArray(String[]::new)) {
			Tag child = tag.get(key);
			if (child instanceof StringTag) {
				int max = key.equals("CustomName") ? MAX_NAME : -1;
				String value = Nbt.getString(tag, key);
				String clean = clean(value, max);
				if (!clean.equals(value)) {
					tag.putString(key, clean);
				}
			} else if (child instanceof CompoundTag compound) {
				cleanAll(compound);
			} else if (child instanceof ListTag list) {
				// a sign's lines (SignText's "messages" and "filtered_messages", in vanilla and in mods built on it)
				cleanList(list, key.equals("messages") || key.equals("filtered_messages") ? MAX_LINE : -1);
			}
		}
	}

	private static void cleanList(ListTag list, int max) {
		for (int i = 0; i < list.size(); i++) {
			Tag element = list.get(i);
			if (element instanceof StringTag) {
				String value = Nbt.stringAt(list, i);
				String clean = clean(value, max);
				if (!clean.equals(value)) {
					list.set(i, StringTag.valueOf(clean));
				}
			} else if (element instanceof CompoundTag compound) {
				cleanAll(compound);
			} else if (element instanceof ListTag inner) {
				cleanList(inner, -1);
			}
		}
	}

	/**
	 * {@code text} (a text component's saved JSON) without click events or worked-out parts and, when {@code max} is not
	 * negative, at most {@code max} characters long: a longer one becomes its plain words, cut to {@code max}. Text that is
	 * no JSON, or not a text component, comes back as it was.
	 */
	static String clean(String text, int max) {
		// A text component's object or array needs one of these; a string without them can only be plain words
		if (max < 0 && text.indexOf('{') < 0 && text.indexOf('[') < 0) {
			return text;
		}
		JsonElement json;
		try {
			json = JsonParser.parseString(text);
		} catch (JsonParseException | IllegalStateException e) {
			return text;
		}
		if (json == null || json.isJsonNull()) {
			return text;
		}
		// A line nested deeper than any player's (B1099: 30,000 arrays fit one NBT string) is never walked recursively, here
		// or by the game when it loads the copy: it becomes its plain words, found without recursion. The reader above
		// keeps its own stack, so it reads any depth.
		if (opens(text) > MAX_DEPTH && depth(json) > MAX_DEPTH) {
			String words = words(json);
			int cut = max >= 0 ? max : MAX_LINE;
			return GSON.toJson(new JsonPrimitive(words.substring(0, Math.min(cut, words.length()))));
		}
		// what the game's lenient reader makes of the line decides, not its first character (B1062): a line can start with a
		// comment or a )]}' prefix and still be read as a component
		boolean structured = json.isJsonObject() || json.isJsonArray();
		boolean changed = structured && strip(json);
		if (max >= 0) {
			String plain = plain(json);
			if (plain.length() > max || text.length() > MAX_JSON) {
				return GSON.toJson(new JsonPrimitive(plain.substring(0, Math.min(max, plain.length()))));
			}
		}
		return changed ? GSON.toJson(json) : text;
	}

	/** Removes click events and nbt, selector and score parts from {@code json}, at any depth. True if it changed. */
	private static boolean strip(JsonElement json) {
		boolean changed = false;
		if (json instanceof JsonObject object) {
			for (String click : new String[] {"clickEvent", "click_event"}) {
				if (object.remove(click) != null) {
					changed = true;
				}
			}
			boolean computed = false;
			if (isString(object.get("nbt")) && (object.has("entity") || object.has("block") || object.has("storage"))) {
				for (String key : new String[] {"nbt", "entity", "block", "storage", "source", "interpret", "separator"}) {
					object.remove(key);
				}
				computed = true;
			}
			if (isString(object.get("selector"))) {
				object.remove("selector");
				object.remove("separator");
				computed = true;
			}
			if (object.get("score") instanceof JsonObject) {
				object.remove("score");
				computed = true;
			}
			if (computed) {
				// the part shows nothing; its style and the words after it stay
				object.remove("type");
				if (!object.has("text") && !object.has("translate") && !object.has("keybind")) {
					object.addProperty("text", "");
				}
				changed = true;
			}
			for (Map.Entry<String, JsonElement> entry : new ArrayList<>(object.entrySet())) {
				changed |= strip(entry.getValue());
			}
		} else if (json instanceof JsonArray array) {
			for (JsonElement element : array) {
				changed |= strip(element);
			}
		}
		return changed;
	}

	private static boolean isString(JsonElement json) {
		return json instanceof JsonPrimitive primitive && primitive.isString();
	}

	/**
	 * The deepest nesting a line keeps as it is (B1099). Far past any text a player or a mod writes (a translation nested ten
	 * deep is about 30), and shallow enough that the walks below and the game's own reading of the copy stay small.
	 */
	static final int MAX_DEPTH = 256;

	/** How many objects and arrays {@code text} opens, counted on its characters: never less than its nesting. */
	private static int opens(String text) {
		int count = 0;
		for (int i = 0; i < text.length(); i++) {
			char c = text.charAt(i);
			if (c == '[' || c == '{') {
				count++;
			}
		}
		return count;
	}

	/** How deep {@code json} nests, measured without recursion. */
	private static int depth(JsonElement json) {
		ArrayDeque<JsonElement> elements = new ArrayDeque<>();
		ArrayDeque<Integer> depths = new ArrayDeque<>();
		elements.push(json);
		depths.push(0);
		int deepest = 0;
		while (!elements.isEmpty()) {
			JsonElement element = elements.pop();
			int depth = depths.pop();
			deepest = Math.max(deepest, depth);
			if (deepest > MAX_DEPTH) {
				return deepest;
			}
			Iterable<JsonElement> children = element instanceof JsonArray array ? array
				: element instanceof JsonObject object ? object.asMap().values() : java.util.List.of();
			for (JsonElement child : children) {
				elements.push(child);
				depths.push(depth + 1);
			}
		}
		return deepest;
	}

	/**
	 * The plain words of an over-deep {@code json} (B1099), in order and without recursion: its text, the language's text of
	 * its keybinds and translations (without their arguments) and the parts after it. Click events and worked-out parts show
	 * nothing. Stops a little past {@link #LIMIT}.
	 */
	private static String words(JsonElement json) {
		StringBuilder out = new StringBuilder();
		ArrayDeque<JsonElement> todo = new ArrayDeque<>();
		todo.push(json);
		while (!todo.isEmpty() && out.length() <= LIMIT) {
			JsonElement element = todo.pop();
			if (element instanceof JsonPrimitive primitive) {
				out.append(primitive.getAsString());
			} else if (element instanceof JsonArray array) {
				for (int i = array.size() - 1; i >= 0; i--) {
					todo.push(array.get(i));
				}
			} else if (element instanceof JsonObject object) {
				if (isString(object.get("text"))) {
					out.append(object.get("text").getAsString());
				}
				if (isString(object.get("keybind"))) {
					String name = object.get("keybind").getAsString();
					out.append(Language.getInstance().getOrDefault(name, name));
				}
				if (isString(object.get("translate"))) {
					String key = object.get("translate").getAsString();
					String fallback = isString(object.get("fallback")) ? object.get("fallback").getAsString() : key;
					out.append(Language.getInstance().getOrDefault(key, fallback));
				}
				if (object.get("extra") instanceof JsonArray extra) {
					todo.push(extra);
				}
			}
		}
		return out.length() > LIMIT ? out.substring(0, LIMIT) : out.toString();
	}

	/**
	 * About the words {@code json} shows: its text, keybinds and translations with their arguments, and the parts after
	 * it. A translation shows an argument once for each {@code %s} that uses it, as the game does (B1074: ten {@code %1$s}
	 * nested five deep show 100,000 letters from a short line), so arguments are counted the same way, through the
	 * language's text for a key it knows (B1088: a vanilla key that uses its argument three times, nested ten deep, showed
	 * over a million letters while its key measured short). The count stops a
	 * little past {@link #LIMIT}, so a line built to show billions of letters costs no more to measure than a long one.
	 */
	private static String plain(JsonElement json) {
		StringBuilder out = new StringBuilder();
		plain(json, out);
		return out.length() > LIMIT ? out.substring(0, LIMIT) : out.toString();
	}

	/** More letters than any line or name keeps, so a cut-off count still tells a too-long line from one that fits. */
	private static final int LIMIT = 4 * MAX_LINE;
	/** A translation's placeholders, as the game's TranslatableContents reads them. */
	private static final Pattern FORMAT = Pattern.compile("%(?:(\\d+)\\$)?([A-Za-z%]|$)");

	private static void plain(JsonElement json, StringBuilder out) {
		if (out.length() > LIMIT) {
			return;
		}
		if (json instanceof JsonPrimitive primitive) {
			out.append(primitive.getAsString());
		} else if (json instanceof JsonArray array) {
			for (JsonElement element : array) {
				plain(element, out);
			}
		} else if (json instanceof JsonObject object) {
			if (isString(object.get("text"))) {
				out.append(object.get("text").getAsString());
			}
			if (isString(object.get("keybind"))) {
				// a client shows the binding's key, or the language's text for the name when no binding has it (B1100:
				// KeyMapping.createNameSupplier), so the longer of the two counts
				String name = object.get("keybind").getAsString();
				out.append(Language.getInstance().getOrDefault(name, name));
			}
			if (isString(object.get("translate"))) {
				String key = object.get("translate").getAsString();
				String fallback = isString(object.get("fallback")) ? object.get("fallback").getAsString() : key;
				// the game shows the language's text for a key it knows, not the key (B1088)
				translate(Language.getInstance().getOrDefault(key, fallback),
					object.get("with") instanceof JsonArray with ? with : new JsonArray(), out);
			} else if (object.get("with") instanceof JsonArray with) {
				plain(with, out);
			}
			if (object.get("extra") instanceof JsonArray extra) {
				plain(extra, out);
			}
		}
	}

	/**
	 * The words a translation with the text {@code format} (the language's text for its key, or its fallback or key when the
	 * language has none, as in the game) shows. A format the game can't read shows as it is, as in the game.
	 */
	private static void translate(String format, JsonArray with, StringBuilder out) {
		// each argument's words are worked out once and repeated, so nesting costs no more than the letters shown
		String[] args = new String[with.size()];
		Matcher matcher = FORMAT.matcher(format);
		int next = 0;
		int start = 0;
		StringBuilder shown = new StringBuilder();
		while (matcher.find(start) && shown.length() + out.length() <= LIMIT) {
			shown.append(format, start, matcher.start());
			start = matcher.end();
			String type = matcher.group(2);
			if ("%".equals(type) && matcher.group(0).equals("%%")) {
				shown.append('%');
				continue;
			}
			if (!"s".equals(type)) {
				out.append(format, 0, Math.min(format.length(), LIMIT + 1));
				return;
			}
			String number = matcher.group(1);
			// a number too big for an int is no argument (and must not throw here)
			int index = number == null ? next++ : number.length() > 9 ? -1 : Integer.parseInt(number) - 1;
			if (index < 0 || index >= args.length) {
				out.append(format, 0, Math.min(format.length(), LIMIT + 1));
				return;
			}
			if (args[index] == null) {
				StringBuilder arg = new StringBuilder();
				plain(with.get(index), arg);
				args[index] = arg.length() > LIMIT ? arg.substring(0, LIMIT + 1) : arg.toString();
			}
			shown.append(args[index]);
		}
		if (shown.length() + out.length() <= LIMIT) {
			shown.append(format, start, format.length());
		}
		out.append(shown);
	}

	private BlockText() {
	}
}
