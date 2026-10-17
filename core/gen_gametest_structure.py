"""Writes the empty 7x4x7 GameTest arena (stone floor, air above) as a gzipped structure NBT for 1.20.1.
Hand-written NBT encoder so no extra dependency is needed."""
import gzip, os, struct

TAG_END, TAG_INT, TAG_STRING, TAG_LIST, TAG_COMPOUND = 0, 3, 8, 9, 10


def s(v):
    b = v.encode("utf-8")
    return struct.pack(">H", len(b)) + b


def payload(tag, v):
    if tag == TAG_INT:
        return struct.pack(">i", v)
    if tag == TAG_STRING:
        return s(v)
    if tag == TAG_LIST:
        et, items = v
        return struct.pack(">bi", et if items else TAG_END, len(items)) + b"".join(payload(et, x) for x in items)
    if tag == TAG_COMPOUND:
        return b"".join(struct.pack(">b", t) + s(k) + payload(t, x) for k, (t, x) in v.items()) + b"\x00"
    raise ValueError(tag)


SX, SY, SZ = 7, 4, 7
blocks = [{"pos": (TAG_LIST, (TAG_INT, [x, 0, z])), "state": (TAG_INT, 0)} for x in range(SX) for z in range(SZ)]
root = {
    "DataVersion": (TAG_INT, 3465),
    "size": (TAG_LIST, (TAG_INT, [SX, SY, SZ])),
    "palette": (TAG_LIST, (TAG_COMPOUND, [{"Name": (TAG_STRING, "minecraft:stone")}])),
    "blocks": (TAG_LIST, (TAG_COMPOUND, blocks)),
    "entities": (TAG_LIST, (TAG_COMPOUND, [])),
}
data = struct.pack(">b", TAG_COMPOUND) + s("") + payload(TAG_COMPOUND, root)
out = os.path.join(os.path.dirname(__file__), "src", "main", "resources", "data", "holdfast_core", "structures", "arena.nbt")
with gzip.open(out, "wb") as f:
    f.write(data)
print("wrote", out, len(data), "bytes")
