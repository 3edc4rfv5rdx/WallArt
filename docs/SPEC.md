# WallArt — specification

Russian version: [SPEC.ru.md](SPEC.ru.md).

Trying pictures on a wall: a photo of one's own wall with one or more framed
pictures on it, true to size and in the wall's perspective. The result is
saved as a photo.

A standalone app with no code shared with PhotoCollage (apart from the shared
`shared/updater` and `shared/about` modules). Minimal surroundings.

## Wall

- The background is a photo of the wall from the gallery (the system photo
  picker).
- **Calibration, once for the whole wall.** The user places 4 corners on some
  rectangle on the wall (a door, a cabinet, a tile, an A4 sheet) and enters its
  width and height in centimetres. That sets the perspective and the scale at
  once.
- For a photo taken square-on the corners simply form a rectangle, and
  calibration comes down to the size in centimetres.
- Calibration can be redone at any time. The pictures keep their places in
  the wall's centimetres.

## Pictures

- One or more pictures (a gallery wall).
- A picture is a pair of overlays:
  - **frame:** a photo of a frame or a coloured rectangle; the moulding width
    is set in cm;
  - **artwork:** a photo of the picture itself, lying over the frame, inset
    by the moulding width.
- The picture's size is set in centimetres (the frame's outer size), its
  position by dragging it across the wall.
- All pictures live in the wall's flat coordinate system in centimetres and
  are projected onto the photo through the calibration when drawn.
- Alignment for a gallery wall (by edge, by centre, equal gaps).
- A shadow on the wall — a checkbox.
- Add, remove, replace the frame's or the artwork's photo, change the order.

## Saving

- The finished photo with a choice of size (long side), as in PhotoCollage.
- Saved to `Pictures/WallArt` through MediaStore, with no permissions.
- Share.

## Draft

- The current work is written automatically after every change and restored
  at the next launch.
- There is one draft, saved separately and by hand ("Save draft"); it is
  opened from the ⋮ menu.
- No archives, export, import or list of drafts.
- "New wall" starts over (with a confirmation).
- Undo and redo.

## Surroundings (as in every PRJ app)

- The build script set, the updater (`23-ToUpdate.sh`) and About.
- Settings: theme (system, light, dark), accent colour, language (en, ru, uk),
  update check at launch.
- The ⋮ menu in the top right corner.
- minSdk 30, Kotlin + Compose, portrait orientation.

## Order of work

1. Project skeleton: scripts, Gradle, theme, settings, menu, updater and About.
2. Wall: picking the photo, calibration by 4 corners with a size in cm.
3. Pictures: frame and artwork, size in cm, moving, shadow, gallery.
4. Saving and Share, autosave and the draft, undo/redo.
