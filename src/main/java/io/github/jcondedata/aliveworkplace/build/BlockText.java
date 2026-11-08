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
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;

import java.util.ArrayList;
import java.util.Map;

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

	/** About the words {@code json} shows: its text, translation keys and their arguments, and the parts after it. */
	private static String plain(JsonElement json) {
		StringBuilder out = new StringBuilder();
		plain(json, out);
		return out.toString();
	}

	private static void plain(JsonElement json, StringBuilder out) {
		if (json instanceof JsonPrimitive primitive) {
			out.append(primitive.getAsString());
		} else if (json instanceof JsonArray array) {
			for (JsonElement element : array) {
				plain(element, out);
			}
		} else if (json instanceof JsonObject object) {
			for (String key : new String[] {"text", "translate", "keybind"}) {
				if (isString(object.get(key))) {
					out.append(object.get(key).getAsString());
				}
			}
			for (String key : new String[] {"with", "extra"}) {
				if (object.get(key) instanceof JsonArray parts) {
					plain(parts, out);
				}
			}
		}
	}

	private BlockText() {
	}
}
