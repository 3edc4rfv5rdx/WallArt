# WallArt

**Try pictures on your wall: a photo of the wall, pictures in frames on it,
true to size and in the wall's perspective.**

![Android 11+](https://img.shields.io/badge/Android-11%2B-3DDC84?logo=android&logoColor=white)
![Kotlin](https://img.shields.io/badge/Kotlin-Compose-7F52FF?logo=kotlin&logoColor=white)
[![License: GPL v3](https://img.shields.io/badge/License-GPLv3-blue.svg)](LICENSE)

Work in progress: what it is to do is in [docs/SPEC.md](docs/SPEC.md).

## Features

- **Wall**: a photo of the wall from the gallery, copied into the app.
- **Calibration**: four corners dragged onto a rectangle of known size on the
  wall — a door, a cabinet, a sheet of A4 — and its width and height typed in
  centimetres. That sets the wall's perspective and scale at once. A grid
  across the rectangle shows whether the perspective is right, and a loupe
  shows the corner under the finger.
- **Pictures**: an artwork in a frame, as many as wanted, drawn in the
  wall's perspective and true to size. Tap one to select it, drag it across
  the wall; near another one's edge, centre or a gap already between two,
  it snaps to it, with a guide line and the gap in centimetres. The pencil sets its artwork, its outer size and frame width in
  centimetres, a frame colour or a photo of a frame, and its place in the
  stack. Shadow in the ⋮ menu lets them cast a soft shadow.
- **Format**: the saved image's way round, shape (the photo's own, 1:1,
  4:5, 3:4, 2:3, 9:16, A4), size and preset (Instagram, Full HD, 10×15,
  A4 300 dpi and more), as in PhotoCollage. Any shape but the photo's own
  shows its crop on the wall, the rest dimmed; a drag off the pictures moves
  it. The size starts at the one nearest what the crop really is.
- **Saving**: Save image in the ⋮ menu draws the crop with its pictures at
  the format's size into Pictures/WallArt; Share sends it to another app.
- **Undo and redo** step through every change of the wall; a drag is one
  step.
- **Kept**: the work is written after every change and is there as it was
  at the next start. One draft is kept apart from it, by hand: Save draft,
  under the wall and in the ⋮ menu, and Open draft; New wall starts over and leaves the draft
  as it is.

## Build

The numbered scripts are the PRJ build set; `AGENTS.md` describes them.
