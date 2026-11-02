# Vanilla beacon beam only + ground ring

- The beam is drawn only by Minecraft's own BeaconRenderer.submitBeaconBeam (beam/Beam3D.java).
  The custom on-screen (HUD) beam code was removed from WaypointsMod.java.
- Ring3D.java was rewritten to use Minecraft's own SubmitNodeCollector + RenderTypes.debugQuads()
  (the same collector the beacon beam uses). No custom render pipeline any more.
- Two jars are built (Actions > run > Artifacts):
    simple-waypoints-3d-beam        : beam only. Same as before, always builds.
    simple-waypoints-3d-beam-ring   : beam + ring. If it is missing, the ring code did not compile.
  Use only ONE of them in the mods folder.
- Not compiled or tested in Minecraft by the author of this patch.
