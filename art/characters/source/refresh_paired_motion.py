"""Scoped paired knife/bat/katana correction; follow with make_all.sh --finish."""
import runpy
import sys

runpy.run_module("walker_anims", run_name="__main__")
runpy.run_module("placement", run_name="__main__")

import player_anims as pa

out = {}
for name, build in (("exec_knife", pa.knife), ("exec_bat_charged", pa.bat_charged),
                    ("exec_katana", pa.katana)):
    if len(sys.argv) > 1 and name not in sys.argv[1:]:
        continue
    builder, info = build()
    out[name] = builder.a, info
    print("\n".join(builder.report))
pa.export(out, merge_keys=True)
print("Source-regenerated:", ", ".join(out))
