"""Scoped source rebuild: ground katana only; standing assets stay frozen."""
import player_anims as pa
import player_more as pm
b, info = pm.overhead_ground("katana")
pa.export({b.name:(b.a,info)},merge_keys=True)
print("\n".join(b.report))
