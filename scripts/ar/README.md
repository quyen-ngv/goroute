# AR object asset spec (§3.15)

An **AR object** is a 3D model a player finds in AR and taps to clear a checkpoint: a turtle, a
sealed scroll, a lantern. The console keeps a library of them; creators only pick from it. This
page is what a model must be to go into that library.

## Files per object

| File | Used by | Rules |
|---|---|---|
| `<name>.glb` | Android, and the **source** of everything else | glTF 2.0 binary. All textures embedded. **No** Draco, meshopt or KTX2/Basis compression. |
| `<name>.usdz` | iOS (RealityKit does not read GLB) | Made from the same model. Blender 4.x (File → Export → Universal Scene Description, `.usdz`) or Apple's Reality Converter. `scripts/ar/glb_to_usdz.py` does it headless from the GLB. |
| `<name>.png` | Library thumbnail | 512×512, transparent background. PNG, JPEG or WEBP up to 1 MB. |

The console refuses a GLB that breaks a hard rule below and says why; it warns about the soft ones.

## The model

- **Units and scale:** metres, real-world size. A scroll is ~0.4 m tall, a turtle ~0.6 m long. The
  app shows it at this size (a creator can scale it 0.25×–4× per checkpoint).
- **Axes:** +Y up, the object's **front faces +Z** (the glTF convention; Blender converts on export).
- **Origin:** at the **centre of the base**, where the object touches the ground. A floating object
  still has its origin on the ground, with the body raised above it.
- **Geometry:** aim for **≤ 20,000 triangles**; **30,000 is the hard limit**.
- **Materials:** 1–2 PBR metallic-roughness materials. Bake ambient occlusion into the base colour
  if you want contact shading; the app adds a soft ground shadow.
- **Textures:** PNG or JPEG, power-of-two sides, **≤ 1024 px** (2048 at most).
- **Size:** each file **≤ 5 MB** (warning above); **10 MB is the hard limit**. Players download it
  with the quest, often on mobile data.
- **Skinned models:** at most **50 bones** in a skin.

## Animation (all optional)

Name the clips exactly:

| Clip | When it plays | If missing |
|---|---|---|
| `idle` | While the object waits to be found (loops) | The app bobs and slowly turns the object, with a soft glow |
| `walk` | While a **wandering** object moves (loops) | The object **cannot be used as a wandering object** |
| `collect` | Once, when the player taps it | The app plays a generic burst-and-shrink effect |

A **static model with no animation at all is fine** (a scroll, a stele, a lantern).

Other clip names are kept but never played; the console warns about them.

**USDZ holds a single animation timeline.** Put the clips on it back to back in this order:
`idle`, then `walk`, then `collect`, with no gap. The console reads each clip's name and length from
the GLB and the iOS app cuts the USDZ timeline at those lengths, so both files must have the same
clips with the same lengths. `glb_to_usdz.py` lays them out this way.

## Checklist before uploading

1. Opens in Blender; the object stands on the ground plane, facing +Z (front view in glTF).
2. Real size (measure it).
3. `.glb` exported with "Apply modifiers", no compression, textures embedded.
4. Clips named `idle` / `walk` / `collect` (any you have).
5. `.usdz` made from that same `.glb` (`python3 scripts/ar/glb_to_usdz.py model.glb`).
6. Thumbnail rendered, 512×512.

## Where this is enforced

- `ArModelInspector` (backend) reads the GLB's JSON chunk on upload and on save: compression
  extensions, external files, triangles, bones, clip names and lengths.
- The library row stores the clips, triangle count and whether the object can wander, always
  re-read from the GLB it points to.
