# Stage 1 controls

All bindings are editable under **Options -> Controls -> Key Binds**.

| Action | Gate 0 binding | Settings entry |
|---|---|---|
| Escape upright/ankle grab, request execution, cancel before commit | **F** | **Holdfast -> Escape / Execute / Cancel** |
| TaCZ gun melee/shove | **F** | **TaCZ -> Melee** |
| Ordinary Holdfast melee swing | Left mouse | Vanilla **Attack/Destroy** |
| Hold/release blunt charge | Right mouse | Vanilla **Use Item/Place Block** |
| Swap offhand | **Y** in this preset | Vanilla **Swap Item With Offhand** |

Holdfast has one dedicated context mapping; all its current keyboard-only actions use it. Attack/charge inherit the user's existing vanilla mappings. The escape prompt shows the actual context binding.

With both context and TaCZ melee on F, escape/cancel takes priority. Looking at a live walker with a pistol sends an execution request; with a rifle, F remains native TaCZ melee. With no execution context, gun melee remains TaCZ's. The server can still refuse an execution for range, obstruction, ammunition, posture, etc. An input does not guarantee an outcome. Separately remapping the two controls makes them independent. Native TaCZ melee is blocked while the server considers the player grabbed/executing.

The Holdfast context mapping's code default is F. TaCZ's jar still ships V as its reset default; the **Gate 0 pack preset** sets its saved mapping to F. We do not patch that jar or rewrite user remaps at every login. To apply the preset with the client closed:

```powershell
.\core\tools\set-gate0-controls.ps1 -OptionsPath "$env:APPDATA\PrismLauncher\instances\Holdfast Gate0\minecraft\options.txt"
```

The script backs up options.txt and moves vanilla offhand swap from F to Y only when it conflicts. Restore its timestamped `.bak` with the client closed to undo. Both saved F bindings can be changed afterward in Controls; their intentional shared key can be shown as a conflict by Minecraft. Actual combined-key TaCZ behavior is pending the client review.
