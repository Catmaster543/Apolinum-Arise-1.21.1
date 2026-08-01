package com.fiskerz.apolinum_arise.downed.client;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

import net.minecraft.util.GsonHelper;

/**
 * One downed pose, loaded from {@code assets/apolinumarise/downed_poses/<name>.json} and tunable live
 * (see DownedPoseCommand). All angles are in DEGREES for human-friendly authoring; the render layer
 * converts to radians. A pose is a whole-body "root" transform (lay-down rotation + a block offset to
 * seat the model on the ground) plus per-part rotations for head/body/arms/legs.
 *
 * <p>File format:
 * <pre>
 * {
 *   "root": { "rotation": [xDeg, yDeg, zDeg], "offset": [x, y, z] },
 *   "parts": {
 *     "head": [xDeg, yDeg, zDeg], "body": [...], "rightArm": [...],
 *     "leftArm": [...], "rightLeg": [...], "leftLeg": [...]
 *   }
 * }
 * </pre>
 */
public class DownedPose {
    public static final List<String> PART_NAMES =
            List.of("head", "body", "rightArm", "leftArm", "rightLeg", "leftLeg");

    // Whole-body transform (degrees / blocks).
    public final float[] rootRotation = new float[3];
    public final float[] rootOffset = new float[3];
    // Per-part rotation in degrees: [x, y, z].
    public final Map<String, float[]> parts = new LinkedHashMap<>();

    public DownedPose() {
        for (String name : PART_NAMES) {
            parts.put(name, new float[3]);
        }
    }

    public float[] part(String name) {
        return parts.computeIfAbsent(name, n -> new float[3]);
    }

    public DownedPose copy() {
        DownedPose out = new DownedPose();
        System.arraycopy(rootRotation, 0, out.rootRotation, 0, 3);
        System.arraycopy(rootOffset, 0, out.rootOffset, 0, 3);
        for (String name : PART_NAMES) {
            out.parts.put(name, part(name).clone());
        }
        return out;
    }

    public static DownedPose fromJson(JsonObject json) {
        DownedPose pose = new DownedPose();
        if (json.has("root")) {
            JsonObject root = GsonHelper.getAsJsonObject(json, "root");
            readVec3(root, "rotation", pose.rootRotation);
            readVec3(root, "offset", pose.rootOffset);
        }
        if (json.has("parts")) {
            JsonObject parts = GsonHelper.getAsJsonObject(json, "parts");
            for (String name : PART_NAMES) {
                if (parts.has(name)) {
                    readArray(GsonHelper.getAsJsonArray(parts, name), pose.part(name));
                }
            }
        }
        return pose;
    }

    /** Pretty JSON string, ready to paste into the pose's file. */
    public String toJsonString() {
        StringBuilder sb = new StringBuilder();
        sb.append("{\n");
        sb.append("  \"root\": {\n");
        sb.append("    \"rotation\": ").append(vec(rootRotation)).append(",\n");
        sb.append("    \"offset\": ").append(vec(rootOffset)).append("\n");
        sb.append("  },\n");
        sb.append("  \"parts\": {\n");
        for (int i = 0; i < PART_NAMES.size(); i++) {
            String name = PART_NAMES.get(i);
            sb.append("    \"").append(name).append("\": ").append(vec(part(name)));
            sb.append(i < PART_NAMES.size() - 1 ? ",\n" : "\n");
        }
        sb.append("  }\n");
        sb.append("}");
        return sb.toString();
    }

    private static String vec(float[] v) {
        return "[" + trim(v[0]) + ", " + trim(v[1]) + ", " + trim(v[2]) + "]";
    }

    private static String trim(float value) {
        return value == Math.rint(value) ? Integer.toString((int) value) : Float.toString(value);
    }

    private static void readVec3(JsonObject parent, String key, float[] out) {
        if (parent.has(key)) {
            readArray(GsonHelper.getAsJsonArray(parent, key), out);
        }
    }

    private static void readArray(JsonArray array, float[] out) {
        for (int i = 0; i < 3 && i < array.size(); i++) {
            out[i] = array.get(i).getAsFloat();
        }
    }
}
