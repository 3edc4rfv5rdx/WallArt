# CHANGELOG
> Newest entries on top.
> N=new feature, E=error fix, F=fine-tune, R=refactor, I=infrastructure

## Unreleased
- F: About opens from an (i) button on the Settings screen, as in the other apps, instead of the ⋮ menu
- F: The About and update dialogs take the accent picked in Settings instead of a fixed colour
- F: The nails and their figures give way while Resize is on, no longer covering the size
## v0.2.38 (2026-11-05)
- N: Hanging in the pencil menu takes how far below the frame's top it rests and one point or two, and shows where each nail goes from the calibration's corner
- N: Holding the button beside Save draft shows the wall without its pictures, for before and after
- N: Straight frames in the ⋮ menu hang the pictures square to the photo at one scale, not in the wall's perspective
## v0.1.29 (2026-11-04)
- I: The icon previews are kept out of git
- I: Build number bumped
- E: Stories left the presets: either way round it was Full HD, which Format took for it
- F: Share sends Full HD's long side, 1920
- F: A new way round in Format keeps the size; only a new shape takes the one nearest its crop
- E: A preset keeps the way round chosen, so A4 and the rest can be landscape too
- E: Frame and artwork photos are drawn through their perspective again, not the canvas's own matrix
- R: Lint's findings fixed: notices read through LocalResources, the KTX createBitmap and withClip
- E: A calibration corner near the screen's edge is dragged instead of taken for the system's back gesture
- N: The wall turns with the phone by its sensor, the bars and dialogs staying upright
- N: While the view is enlarged one finger moves it, off the pictures and the calibration's corners
- F: The frame width steps by 1 cm, as the artwork's sides do
- N: Two fingers enlarge and move the view for a closer look, editing and calibrating alike
- I: The specification and README describe what is done
- F: The photo keeps its size going into and out of Calibrate: the room under it is one height, the calibration's size and buttons in one row there and its hint over the photo
- F: Every button in a dialog, the drop-down fields and the − and + steppers are filled with the accent
- F: The original shape in Format names the photo's own ratio, as 4:3
- N: Add picture asks the new picture's size first, as Size does, then its artwork's photo or none, for an empty mount to plan a gallery with
- N: Resize in the pencil menu puts out handles and the artwork's size in centimetres, a corner sizing it with its shape kept and a side's middle one way only, in whole centimetres, snapping to standard sizes
- N: Duplicate in the pencil menu puts a copy of the picture beside it, at the gap it has to its neighbour or 10 cm
- F: Size in the pencil menu sets the artwork's size and way round and the frame round it, each side typed in large figures or stepped by − and +, with the standard sizes in a drop-down and the size outside the frame large in the title
- N: Save image in the ⋮ menu saves the wall with its pictures into Pictures/WallArt, and Share sends it
- N: Format in the ⋮ menu sets the saved image's way round, shape, size and preset as in PhotoCollage, its crop shown on the wall and dragged off the pictures, its size starting nearest what the crop really is
- N: New wall, Save draft and Open draft in the ⋮ menu, Save draft also under the wall: one draft kept apart from the work, the work asked about before it is lost
- N: Undo and Redo in the top bar step through every change of the wall, a picture's drag as one step
- N: The work is written after every change and comes back as it was at the next start, its photos no longer used deleted
- N: A dragged picture snaps to the others' edges, centres and the gaps already between them, with guide lines and the gap in centimetres
- N: Shadow in the ⋮ menu lets the pictures cast a soft shadow on the wall
- N: The pencil sets the selected picture's artwork, size and frame width in centimetres, frame colour or frame photo, its place in the stack, or removes it
- N: Add picture hangs an artwork in a frame on the calibrated wall, in its perspective, to be tapped and dragged across it
- N: Calibrate puts four corners on a rectangle of known size on the wall, with a grid across it and a loupe over the corner dragged, and takes its size in centimetres
- N: Wall photo in the ⋮ menu picks the wall's photo from the gallery and keeps a copy of it
- N: Settings for theme, colour, language and the update check, and About, under the ⋮ menu
- I: The project starts from the PRJ build scripts, with the in-app updater and the shared About dialog compiled in
