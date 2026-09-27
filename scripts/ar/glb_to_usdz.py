"""Makes the iOS USDZ for an AR object from its GLB, headless, with Blender (4.x).

    blender --background --python scripts/ar/glb_to_usdz.py -- model.glb [out.usdz]

USDZ holds a single animation timeline, so the GLB's clips are laid on it back to back in the
order the app expects (idle, walk, collect), each starting where the previous one ended. The app
cuts the timeline at the clip lengths the console read from the GLB, so this order and these
lengths must match. Other clips are left off the timeline.

See docs/AR_OBJECT_ASSET_SPEC.md.
"""

import math
import sys

import bpy  # type: ignore  # provided by Blender

ORDER = ("idle", "walk", "collect")


def arguments():
    argv = sys.argv[sys.argv.index("--") + 1:] if "--" in sys.argv else []
    if not argv:
        sys.exit("usage: blender --background --python glb_to_usdz.py -- model.glb [out.usdz]")
    source = argv[0]
    target = argv[1] if len(argv) > 1 else source.rsplit(".", 1)[0] + ".usdz"
    return source, target


def main():
    source, target = arguments()
    bpy.ops.wm.read_factory_settings(use_empty=True)
    bpy.ops.import_scene.gltf(filepath=source)

    scene = bpy.context.scene
    fps = scene.render.fps / scene.render.fps_base
    actions = {action.name.lower(): action for action in bpy.data.actions}
    # The glTF importer names an action "<clip>" or "<clip>_<object>"; match on the clip part.
    by_clip = {}
    for name, action in actions.items():
        clip = name.split("_")[0]
        if clip in ORDER and clip not in by_clip:
            by_clip[clip] = action

    armatures = [obj for obj in scene.objects if obj.animation_data or obj.type == "ARMATURE"]
    frame = 0
    if by_clip and armatures:
        for obj in armatures:
            obj.animation_data_create()
            obj.animation_data.action = None
            track = obj.animation_data.nla_tracks.new()
            track.name = "timeline"
            start = 0
            for clip in ORDER:
                action = by_clip.get(clip)
                if action is None:
                    continue
                first, last = action.frame_range
                strip = track.strips.new(clip, int(start), action)
                strip.action_frame_start = first
                strip.action_frame_end = last
                start += last - first
            frame = max(frame, start)
        scene.frame_start = 0
        scene.frame_end = max(1, math.ceil(frame))
        for clip in ORDER:
            if clip in by_clip:
                first, last = by_clip[clip].frame_range
                print(f"{clip}: {(last - first) / fps:.3f} s")

    bpy.ops.wm.usd_export(
        filepath=target,
        export_animation=bool(by_clip),
        export_materials=True,
        export_textures=True,
        generate_preview_surface=True,
    )
    print(f"wrote {target}")


main()
