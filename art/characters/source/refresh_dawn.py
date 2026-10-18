"""Regenerate only the dawn feedback's source clips; run make_all.sh --finish afterward."""
import runpy
import player_anims as pa
import player_more as pm
import player_weapons as pw

runpy.run_module('walker_anims', run_name='__main__')
out = pa.grab_anims()
out.update(pw.unarmed())
for name, build in [('exec_katana', pa.katana), ('exec_katana_ground', lambda: pm.overhead_ground('katana'))]:
    builder, info = build()
    out[name] = (builder.a, info)
    print('\n'.join(builder.report))
pa.export(out, merge_keys=True)
print('Source-regenerated:', ', '.join(out))
