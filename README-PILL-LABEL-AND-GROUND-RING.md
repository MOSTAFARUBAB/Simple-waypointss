# Pill label + ground ring (matches the reference video)

Label (WaypointsMod.drawPill)
- Rounded pill, 14 px high, 1 px border in the waypoint's color, 60% black inside, white text: `name [5m]`.
- Floats about 2.5 blocks above the waypoint. The beam is not touched.
- It grows in whole steps when you get close (whole numbers keep the text sharp):
  1x normally, 2x under 3 blocks, 3x under 1.8 blocks.
  The "Logo" button in the J menu sets the biggest step: Small = 1x, Medium = 2x, Large = 3x.
- It hides only when you are within 1 block of the waypoint.
- The old colored letter logo is no longer drawn (the "logo letter" box in the edit menu has no effect now).

Ground ring (beam/Ring3D.java, new file, only in the 3d-beam build)
- A flat ring, radius 1 block, in the waypoint's color, lying on the ground around the beam.
- Blocks hide it like anything else on the ground. It follows the "Beams" toggle in the J menu.
- It is its own renderer, so if it ever fails it switches itself off and the beam keeps working.

Build with: gradle clean build --no-daemon -PwithBeam=true
