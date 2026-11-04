package xx.wallart

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class WallJsonTest {

    private val wall = Wall(
        photo = "wall.jpg",
        calibration = Calibration(listOf(Pt(0.3, 0.2), Pt(0.6, 0.28), Pt(0.6, 0.72), Pt(0.3, 0.8)), 80.0, 200.5),
        pictures = listOf(
            Picture(1, CmRect(-10.5, 20.0, 50.0, 70.0), artPhoto = "a.jpg"),
            Picture(2, CmRect(60.0, 20.0, 30.0, 40.0), mouldingCm = 0.0, frameColor = 0xFFC9A23F.toInt(), framePhoto = "f.jpg"),
        ),
        shadow = false,
    )

    @Test
    fun `a wall comes back as it was written`() {
        assertEquals(wall, WallJson.read(WallJson.write(wall)))
        assertEquals(Wall(), WallJson.read(WallJson.write(Wall())))
    }

    @Test
    fun `its photos are every one it names, once`() {
        assertEquals(setOf("wall.jpg", "a.jpg", "f.jpg"), wall.photoNames)
        assertEquals(emptySet<String>(), Wall().photoNames)
    }

    @Test
    fun `a file of another version, or no wall at all, is not read`() {
        assertNull(WallJson.read(WallJson.write(wall).replace("\"version\":1", "\"version\":2")))
        assertNull(WallJson.read("not json"))
        assertNull(WallJson.read("{}"))
    }
}
