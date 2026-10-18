"""Rebuild standing bat motion from editable source; preserve other solved keys."""
import player_anims as pa
import player_weapons as pw
import player_more as pm
out, reports = pw.bat_swings()
for build in (pa.bat_quick, pa.bat_charge, pa.bat_charged):
    b, info = build()
    out[b.name] = b.a, info
    reports.extend(b.report)
out.update(pm.bat_free())
pa.export(out, merge_keys=True)
print("\n".join(reports))
print("Source-regenerated:", ", ".join(out))
