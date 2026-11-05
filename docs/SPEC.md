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
- The picture's size is set in centimetres as the artwork's size and the
  moulding width; the frame's outer size is shown in the dialog's title.
  Standard artwork sizes (10×15 to 70×100, A4) in a drop-down, the
  orientation, and − / + steppers by each field.
- "Add picture" asks the size first, then a photo of the artwork or none.
- Its position is set by dragging it across the wall.
- "Resize" in the picture's menu shows handles: a corner keeps the artwork's
  proportions, a side's middle changes that side alone; whole centimetres,
  near a standard size it takes it. The size pill shows only then.
- "Duplicate" puts a copy to the right, at the gap the picture already has.
- All pictures live in the wall's flat coordinate system in centimetres and
  are projected onto the photo through the calibration when drawn.
- Alignment for a gallery wall (by edge, by centre, equal gaps).
- A shadow on the wall — a checkbox in the ⋮ menu, for all pictures.
- "Straight frames" — a checkbox in the ⋮ menu, for the whole wall, off at
  first: the pictures are drawn as rectangles square to the photo, at one
  scale, with no perspective. The calibration still gives the scale (its
  corners' area) and the centre.
- Add, remove, replace the frame's or the artwork's photo, change the order.

## Saving

- The finished photo in a Format, as in PhotoCollage: orientation, shape
  (the original one shows its ratio, as 4:3), size and preset. A new shape
  takes the size nearest its crop's real one; applied on Done.
- A shape other than the original shows its crop frame on the wall, which is
  moved by dragging off the pictures; what is outside is cut off.
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

1. + Project skeleton: scripts, Gradle, theme, settings, menu, updater and About.
2. + Wall: picking the photo, calibration by 4 corners with a size in cm.
3. + Pictures: frame and artwork, size in cm, moving, shadow, gallery.
4. + Saving and Share, autosave and the draft, undo/redo.
