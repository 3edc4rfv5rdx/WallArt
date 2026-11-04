package xx.wallart

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class FormatTest {

    private val photo = 4.0 / 3 // 4000 × 3000, landscape

    private fun assertCrop(expected: Crop, actual: Crop) {
        assertEquals(expected.left, actual.left, 1e-9)
        assertEquals(expected.top, actual.top, 1e-9)
        assertEquals(expected.width, actual.width, 1e-9)
        assertEquals(expected.height, actual.height, 1e-9)
    }

    @Test
    fun `the original shape crops nothing`() {
        assertCrop(Crop(0.0, 0.0, 1.0, 1.0), Format().crop(photo))
        assertEquals(2048 to 1536, Format().savedSize(photo))
    }

    @Test
    fun `a narrower shape takes the whole height, centred`() {
        val square = Format(Aspect.SQUARE)
        assertCrop(Crop(0.125, 0.0, 0.75, 1.0), square.crop(photo))
    }

    @Test
    fun `a wider shape takes the whole width`() {
        val wide = Format(Aspect.NINE_SIXTEEN, landscape = true)
        assertCrop(Crop(0.0, 0.125, 1.0, 0.75), wide.crop(photo))
        assertEquals(1920 to 1080, wide.savedSize(photo))
    }

    @Test
    fun `the crop moves within the photo and stops at its edges`() {
        val square = Format(Aspect.SQUARE)
        assertCrop(Crop(0.0, 0.0, 0.75, 1.0), square.cropMoved(photo, -0.5, 0.0).crop(photo))
        assertCrop(Crop(0.25, 0.0, 0.75, 1.0), square.cropMoved(photo, 0.5, 0.0).crop(photo))
        // The way the crop fills does not move.
        assertEquals(0.5, square.cropMoved(photo, 0.0, 0.3).cropY, 0.0)
    }

    @Test
    fun `the size is the shape's nearest to what the crop really is`() {
        // The whole 4000 × 3000 photo: 4096 is nearest 4000.
        assertEquals(4096, Format().fitted(4000, 3000).longSide)
        // A square of it is 3000 long: 2048 and 4096 are 952 and 1096 off.
        assertEquals(2048, Format(Aspect.SQUARE).fitted(4000, 3000).longSide)
        // A tie goes to the larger.
        assertEquals(4096, Format().fitted(3072, 2000).longSide)
    }

    @Test
    fun `a preset sets shape, way round and size, and is known again`() {
        val format = Format().withPreset(Preset.FULL_HD)
        assertEquals(Format(Aspect.NINE_SIXTEEN, landscape = true, longSide = 1920), format)
        assertEquals(Preset.FULL_HD, format.preset())
        assertNull(Format().preset())
    }

    @Test
    fun `a photo's shape is named by the ratio of small sides nearest it`() {
        assertEquals("4:3", ratioLabel(4000, 3000))
        assertEquals("3:4", ratioLabel(3024, 4032))
        // A few pixels short of 16:9 is still 16:9.
        assertEquals("16:9", ratioLabel(4000, 2252))
        assertEquals("1:1", ratioLabel(2000, 2000))
        // A panorama no small ratio comes near.
        assertEquals("4.27:1", ratioLabel(8000, 1875))
    }
}
