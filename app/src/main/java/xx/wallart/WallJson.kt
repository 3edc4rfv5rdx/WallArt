package xx.wallart

import org.json.JSONArray
import org.json.JSONObject

/**
 * The wall as JSON, for the work kept between starts and for the draft.
 * [VERSION] is written with it; a file of another version is not read, so a
 * later change of shape never misreads an old file.
 */
object WallJson {
    private const val VERSION = 1

    fun write(wall: Wall): String = JSONObject().apply {
        put("version", VERSION)
        wall.photo?.let { put("photo", it) }
        wall.calibration?.let { c ->
            put(
                "calibration",
                JSONObject()
                    .put("corners", JSONArray().apply { c.corners.forEach { put(JSONArray().put(it.x).put(it.y)) } })
                    .put("width", c.widthCm)
                    .put("height", c.heightCm),
            )
        }
        put("shadow", wall.shadow)
        put("straight", wall.straight)
        put(
            "format",
            JSONObject()
                .put("aspect", wall.format.aspect.name)
                .put("landscape", wall.format.landscape)
                .put("longSide", wall.format.longSide)
                .put("cropX", wall.format.cropX)
                .put("cropY", wall.format.cropY),
        )
        put("pictures", JSONArray().apply { wall.pictures.forEach { put(picture(it)) } })
    }.toString()

    /** The wall in [text]; null when it is not one this version wrote. */
    fun read(text: String): Wall? = runCatching {
        val json = JSONObject(text)
        if (json.getInt("version") != VERSION) return null
        Wall(
            photo = json.optStringOrNull("photo"),
            calibration = json.optJSONObject("calibration")?.let { c ->
                val corners = c.getJSONArray("corners")
                Calibration(
                    corners = (0 until corners.length()).map { i ->
                        val p = corners.getJSONArray(i)
                        Pt(p.getDouble(0), p.getDouble(1))
                    },
                    widthCm = c.getDouble("width"),
                    heightCm = c.getDouble("height"),
                )
            },
            shadow = json.optBoolean("shadow", true),
            straight = json.optBoolean("straight", false),
            // Walls kept before there was a format have the default one.
            format = json.optJSONObject("format")?.let { format(it) } ?: Format(),
            pictures = json.getJSONArray("pictures").let { list -> (0 until list.length()).map { picture(list.getJSONObject(it)) } },
        )
    }.getOrNull()

    /** A format; a shape this version does not know, or a size its shape does not offer, is its default. */
    private fun format(json: JSONObject): Format {
        val aspect = Aspect.entries.firstOrNull { it.name == json.optString("aspect") } ?: Aspect.ORIGINAL
        val longSide = json.optInt("longSide").takeIf { it in aspect.sizes } ?: aspect.defaultLong
        return Format(
            aspect = aspect,
            landscape = json.optBoolean("landscape"),
            longSide = longSide,
            cropX = json.optDouble("cropX", 0.5).coerceIn(0.0, 1.0),
            cropY = json.optDouble("cropY", 0.5).coerceIn(0.0, 1.0),
        )
    }

    private fun picture(p: Picture): JSONObject = JSONObject()
        .put("id", p.id)
        .put("left", p.bounds.left)
        .put("top", p.bounds.top)
        .put("width", p.bounds.width)
        .put("height", p.bounds.height)
        .put("moulding", p.mouldingCm)
        .put("frameColor", p.frameColor)
        .apply {
            p.framePhoto?.let { put("framePhoto", it) }
            p.artPhoto?.let { put("artPhoto", it) }
            p.hanging?.let { hanging ->
                put("hangingDrop", hanging.dropCm)
                hanging.spacingCm?.let { put("hangingSpacing", it) }
            }
        }

    private fun picture(json: JSONObject) = Picture(
        id = json.getLong("id"),
        bounds = CmRect(json.getDouble("left"), json.getDouble("top"), json.getDouble("width"), json.getDouble("height")),
        mouldingCm = json.getDouble("moulding"),
        frameColor = json.getInt("frameColor"),
        framePhoto = json.optStringOrNull("framePhoto"),
        artPhoto = json.optStringOrNull("artPhoto"),
        hanging = if (json.has("hangingDrop")) {
            Hanging(json.getDouble("hangingDrop"), if (json.has("hangingSpacing")) json.getDouble("hangingSpacing") else null)
        } else {
            null
        },
    )

    /** The string under [key]; null when there is none — optString would make it "". */
    private fun JSONObject.optStringOrNull(key: String): String? = if (has(key)) getString(key) else null
}
