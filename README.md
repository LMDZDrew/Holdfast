# The Walking Dead survival project

Pre-implementation foundation for Andrew's AMC-inspired Minecraft survival project, **Holdfast**. The gameplay design is documented; no playable modpack, custom mod, assets, or performance benchmark has been completed.

Start with [docs/decisions.md](docs/decisions.md), then [docs/design.md](docs/design.md). The latter is a working Markdown transcription of the October 16 design document. The decision record takes precedence where later choices supersede it. See [docs/roadmap.md](docs/roadmap.md) for the first executable milestone and [docs/handoff.md](docs/handoff.md) for current state.

The project currently considers Minecraft Java 1.20.1 Forge and TaCZ, but neither the full mod list nor compatibility is verified. Lost Cities is excluded by the project owner. There is deliberately no fabricated Gradle build, dependency lock, CI build, or installation instruction yet.

## Working together

Use separate branches for independent tasks. Keep design decisions, implementation, and observed test results in the same repository. One assistant can implement a change while the other reviews it. Do not describe proposed behavior as already working. Update the handoff after substantial work.

Project repository: https://github.com/LMDZDrew/Holdfast
