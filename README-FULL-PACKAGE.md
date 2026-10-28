# Simple Waypoints — Vanilla Beacon Beam build

This workflow is configured to build **only the 3D-beam variant**. It does not create a separate basic-build artifact.

## Download the correct mod from GitHub Actions

1. Push this project to GitHub.
2. Open the repository's **Actions** tab.
3. Open the latest **Build Simple Waypoints - Vanilla Beacon Beam** run.
4. Confirm the job named `beam3d` is green.
5. In the run's **Artifacts** section, download **`simple-waypoints-3d-beam`**.
6. Extract that artifact and put the normal `.jar` into Minecraft's `mods` folder. Remove older Simple Waypoints JARs first.

If the workflow fails, open the `beam3d` job and send the error log. This workflow now fails visibly if the beam build fails or if the expected JAR is missing.

Note: This package contains source code and a GitHub Actions workflow; it is not a precompiled mod JAR. The rendering still needs in-game verification.
