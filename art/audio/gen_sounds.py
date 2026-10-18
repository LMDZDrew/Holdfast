"""Original procedural sound effects for Holdfast (no recorded or show audio): walker vocals with the raspy,
strained, breathy character Andrew asked for, and distinct flesh/skull contact sounds per weapon family.

Walker voice model: a low, unsteady glottal pulse train (f0 55-120 Hz with heavy jitter, shimmer and dropouts into
vocal fry), mixed with turbulent breath noise that dominates, shaped by slowly drifting vowel formants (a slack
"ah/uh/oh" jaw), plus a wet rattle (amplitude-modulated noise) in the throat. Contact sounds are layered from
filtered noise bursts, low thumps, crunches (sparse clicks) and wet squelches, with different recipes for blunt,
slash, stab and gunshot so they never sound like one sample.

Writes 44.1 kHz mono Ogg Vorbis into core/src/main/resources/assets/holdfast_core/sounds/ and sounds.json.
Requires numpy, scipy and soundfile (libsndfile with Vorbis). Deterministic: fixed seeds per variant.
"""
import json, os
import numpy as np
from scipy import signal
import soundfile as sf

SR = 44100
HERE = os.path.dirname(__file__)
ASSETS = os.path.join(HERE, "..", "..", "core", "src", "main", "resources", "assets", "holdfast_core")
SOUNDS = os.path.join(ASSETS, "sounds")


def t_axis(sec):
    return np.arange(int(sec * SR)) / SR


def env(n, attack, release, sustain=1.0):
    a, r = max(1, int(attack * SR)), max(1, int(release * SR))
    e = np.full(n, sustain)
    e[:a] = np.linspace(0, sustain, a)
    e[-r:] = np.linspace(sustain, 0, r) * (e[-r:] if r <= n else 1)
    return e


def bandpass(x, lo, hi, order=2):
    sos = signal.butter(order, [lo, hi], btype="band", fs=SR, output="sos")
    return signal.sosfilt(sos, x)


def lowpass(x, f, order=2):
    return signal.sosfilt(signal.butter(order, f, btype="low", fs=SR, output="sos"), x)


def highpass(x, f, order=2):
    return signal.sosfilt(signal.butter(order, f, btype="high", fs=SR, output="sos"), x)


def smooth_noise(rng, n, rate):
    """Slow random drift in [-1, 1] changing about `rate` times a second."""
    k = max(2, int(n / SR * rate) + 2)
    pts = rng.uniform(-1, 1, k)
    return np.interp(np.linspace(0, k - 1, n), np.arange(k), pts)


FORMANTS = {"ah": (750, 1150, 2500), "uh": (600, 1050, 2400), "oh": (500, 850, 2350), "eh": (550, 1700, 2450)}


def formant_filter(x, f1, f2, f3, drift):
    out = np.zeros_like(x)
    for f, gain, q in ((f1, 1.0, 5), (f2, 0.6, 7), (f3, 0.25, 9)):
        lo, hi = f / (1 + 1 / q), f * (1 + 1 / q)
        out += gain * bandpass(x, lo, hi)
    return out


def walker_voice(rng, sec, f0=(60, 95), breath=0.75, strain=0.5, rattle=0.4, vowels=("ah", "uh")):
    n = int(sec * SR)
    t = t_axis(sec)
    f0_curve = np.interp(t, [0, sec * 0.3, sec], [f0[0], f0[1], f0[0] * 0.9]) * (1 + 0.12 * smooth_noise(rng, n, 9))
    f0_curve *= 1 + 0.06 * rng.standard_normal(n).cumsum() / np.sqrt(np.arange(1, n + 1))   # wandering
    phase = np.cumsum(f0_curve / SR)
    # glottal pulses: sharp, irregular, with dropouts (vocal fry and catches in the throat)
    pulse = (np.mod(phase, 1.0) < 0.12).astype(float) * (1 + 0.5 * rng.standard_normal(n))
    gate = (smooth_noise(rng, n, 6) > -0.6 + 0.8 * (1 - strain)).astype(float)
    gate = lowpass(gate, 30)
    voiced = pulse * gate
    noise = rng.standard_normal(n)
    breathy = highpass(noise, 300) * breath
    src = voiced * (1 - 0.5 * breath) + breathy
    v1, v2 = FORMANTS[vowels[0]], FORMANTS[vowels[-1]]
    mix = (np.sin(np.pi * t / sec) ** 2)
    a = formant_filter(src, *v1, drift=0)
    b = formant_filter(src, *v2, drift=0)
    out = a * (1 - mix) + b * mix
    # throat rattle: wet amplitude-modulated low noise
    am = 0.5 + 0.5 * np.sign(np.sin(2 * np.pi * (18 + 8 * smooth_noise(rng, n, 2)) * t))
    out += rattle * bandpass(noise, 120, 700) * lowpass(am, 60) * 0.8
    # rasp: clipped high band
    out += strain * 0.35 * np.tanh(4 * bandpass(noise * (0.5 + voiced), 1800, 5200))
    shape = env(n, 0.08, 0.25) * (0.7 + 0.3 * smooth_noise(rng, n, 3))
    return out * shape


def thump(rng, sec, f=70, decay=0.08):
    t = t_axis(sec)
    body = np.sin(2 * np.pi * (f * (1 + 2 * np.exp(-t / 0.01))) * t) * np.exp(-t / decay)
    return body


def crunch(rng, sec, density=900, decay=0.06, band=(900, 5000)):
    n = int(sec * SR)
    t = t_axis(sec)
    clicks = (rng.random(n) < density / SR).astype(float) * rng.uniform(0.3, 1.0, n)
    return bandpass(clicks + 0.2 * rng.standard_normal(n), *band) * np.exp(-t / decay)


def squelch(rng, sec, f=(260, 1300), decay=0.12):
    n = int(sec * SR)
    t = t_axis(sec)
    sweep = np.interp(t, [0, sec], list(f))
    noise = rng.standard_normal(n)
    wet = bandpass(noise, 200, 2400) * (0.5 + 0.5 * np.sin(2 * np.pi * np.cumsum(sweep / SR) * 3))
    return wet * np.exp(-t / decay)


def whoosh(rng, sec, lo=300, hi=2500, peak=0.45):
    n = int(sec * SR)
    t = t_axis(sec)
    e = np.exp(-((t - sec * peak) / (sec * 0.18)) ** 2)
    centre = lo + (hi - lo) * e
    noise = rng.standard_normal(n)
    out = np.zeros(n)
    for i in range(0, n, 1024):        # time-varying band by blocks
        c = centre[min(n - 1, i + 512)]
        seg = bandpass(noise[max(0, i - 2048):i + 1024], c * 0.6, min(c * 1.6, SR / 2 - 100))[-min(1024, n - i):]
        out[i:i + len(seg)] = seg
    return out * e


def norm(x, peak=0.82):
    x = x - np.mean(x)
    m = np.max(np.abs(x)) or 1.0
    return x / m * peak


def fade(x, ms=6):
    k = int(SR * ms / 1000)
    x[:k] *= np.linspace(0, 1, k)
    x[-k:] *= np.linspace(1, 0, k)
    return x


RECIPES = {
    # walker vocals
    "walker/groan": (5, lambda r: walker_voice(r, r.uniform(1.4, 2.4), f0=(r.uniform(52, 66), r.uniform(80, 100)), breath=0.8,
                                                 strain=0.45, rattle=0.45, vowels=(r.choice(["ah", "uh", "oh"]), r.choice(["uh", "oh"])))),
    "walker/breath": (4, lambda r: walker_voice(r, r.uniform(0.9, 1.4), f0=(50, 60), breath=0.97, strain=0.2, rattle=0.6, vowels=("uh", "uh")) * 0.7),
    "walker/snarl": (5, lambda r: walker_voice(r, r.uniform(0.6, 1.0), f0=(r.uniform(80, 95), r.uniform(115, 135)), breath=0.65,
                                                 strain=0.95, rattle=0.3, vowels=("eh", "ah"))),
    "walker/lunge": (3, lambda r: walker_voice(r, 0.55, f0=(90, 130), breath=0.6, strain=1.0, rattle=0.2, vowels=("ah", "eh"))
                     + 0.4 * whoosh(r, 0.55, 200, 900)),
    "walker/bite": (4, lambda r: 0.9 * crunch(r, 0.35, density=1400, decay=0.05, band=(1200, 6000)) + 0.8 * squelch(r, 0.35)
                    + 0.5 * walker_voice(r, 0.35, f0=(70, 90), breath=0.8, strain=0.8, rattle=0.5)),
    "walker/death": (3, lambda r: walker_voice(r, 0.9, f0=(70, 45), breath=0.9, strain=0.6, rattle=0.7, vowels=("ah", "uh")) * env(int(0.9 * SR), 0.01, 0.6)),
    # contact sounds (separate from swings and gunfire)
    "impact/blunt": (5, lambda r: thump(r, 0.35, r.uniform(55, 80), 0.07) + 0.35 * squelch(r, 0.35, (200, 600), 0.06)
                     + 0.15 * crunch(r, 0.35, 300, 0.03, (400, 2000))),
    "impact/slash": (5, lambda r: 0.8 * bandpass(r.standard_normal(int(0.32 * SR)), 1500, 7000) * np.exp(-t_axis(0.32) / 0.05)
                     + 0.9 * squelch(r, 0.32, (900, 300), 0.1) + 0.3 * thump(r, 0.32, 90, 0.04)),
    "impact/stab": (5, lambda r: 0.5 * thump(r, 0.3, 110, 0.03) + 1.0 * squelch(r, 0.3, (400, 150), 0.12)
                    + 0.2 * crunch(r, 0.3, 200, 0.02)),
    "impact/gunshot": (5, lambda r: 0.9 * highpass(r.standard_normal(int(0.28 * SR)), 2500) * np.exp(-t_axis(0.28) / 0.008)
                       + 0.8 * squelch(r, 0.28, (700, 200), 0.07) + 0.4 * thump(r, 0.28, 140, 0.02)),
    "impact/skull_blunt": (4, lambda r: 1.0 * thump(r, 0.45, r.uniform(48, 62), 0.09) + 0.9 * crunch(r, 0.45, 2400, 0.05, (500, 4000))
                           + 0.5 * squelch(r, 0.45, (300, 120), 0.14)),
    "impact/skull_blade": (4, lambda r: 0.8 * crunch(r, 0.4, 2000, 0.04, (1500, 7000)) + 0.9 * squelch(r, 0.4, (600, 180), 0.15)
                           + 0.4 * thump(r, 0.4, 85, 0.05)),
    "impact/skull_gunshot": (4, lambda r: 0.8 * highpass(r.standard_normal(int(0.4 * SR)), 2000) * np.exp(-t_axis(0.4) / 0.01)
                             + 0.9 * crunch(r, 0.4, 2600, 0.04) + 1.0 * squelch(r, 0.4, (900, 200), 0.1)),
    "swing/light": (4, lambda r: whoosh(r, r.uniform(0.22, 0.3), 500, 3500)),
    "swing/heavy": (4, lambda r: (lambda d: whoosh(r, d, 180, 1600) + 0.3 * whoosh(r, d, 600, 2600, 0.5))(r.uniform(0.35, 0.45))),
    "body/fall": (4, lambda r: thump(r, 0.6, r.uniform(40, 55), 0.14) + 0.4 * lowpass(r.standard_normal(int(0.6 * SR)), 900) * np.exp(-t_axis(0.6) / 0.12)),
    "bite/blocked": (4, lambda r: 0.7 * thump(r, 0.3, 160, 0.03) + 0.6 * bandpass(r.standard_normal(int(0.3 * SR)), 300, 1800) * np.exp(-t_axis(0.3) / 0.05)
                     + 0.5 * crunch(r, 0.3, 500, 0.03, (2000, 6000))),
}
# event name -> recipe, subtitle
EVENTS = {
    "walker.groan": ("walker/groan", "Walker groans"), "walker.breath": ("walker/breath", "Walker rasps"),
    "walker.snarl": ("walker/snarl", "Walker snarls"), "walker.lunge": ("walker/lunge", "Walker lunges"),
    "walker.bite": ("walker/bite", "Walker bites"), "walker.death": ("walker/death", "Walker goes still"),
    "impact.blunt": ("impact/blunt", "Blunt impact"), "impact.slash": ("impact/slash", "Blade cuts"),
    "impact.stab": ("impact/stab", "Blade stabs"), "impact.gunshot": ("impact/gunshot", "Bullet hits flesh"),
    "impact.skull_blunt": ("impact/skull_blunt", "Skull caves in"), "impact.skull_blade": ("impact/skull_blade", "Blade into skull"),
    "impact.skull_gunshot": ("impact/skull_gunshot", "Headshot"), "swing.light": ("swing/light", "Swing"),
    "swing.heavy": ("swing/heavy", "Heavy swing"), "body.fall": ("body/fall", "Body falls"),
    "bite.blocked": ("bite/blocked", "Bite blocked"),
    "swing.knife": ("swing/light", "Small blade swings"),
    "swing.sword": ("swing/heavy", "Sword swings"),
    "swing.blunt": ("swing/heavy", "Heavy weapon swings"),
}


def main():
    sounds_json, lang = {}, {}
    total = 0
    for recipe, (count, fn) in RECIPES.items():
        os.makedirs(os.path.join(SOUNDS, os.path.dirname(recipe)), exist_ok=True)
        for i in range(count):
            rng = np.random.default_rng(sum(map(ord, recipe)) * 7919 + i * 104729)   # deterministic per variant
            x = norm(np.asarray(fn(rng), dtype=float))
            if "gunshot" in recipe:
                x = norm(np.tanh(3.0 * x))       # keep a sharp transient loud enough after Vorbis
            x = fade(x)
            sf.write(os.path.join(SOUNDS, f"{recipe}_{i + 1}.ogg"), x.astype(np.float32), SR, format="OGG", subtype="VORBIS")
            total += 1
    for event, (recipe, subtitle) in EVENTS.items():
        count = RECIPES[recipe][0]
        sounds_json[event] = {"subtitle": f"subtitles.holdfast_core.{event}",
                              "sounds": [{"name": f"holdfast_core:{recipe}_{i + 1}"} for i in range(count)]}
        lang[f"subtitles.holdfast_core.{event}"] = subtitle
    with open(os.path.join(ASSETS, "sounds.json"), "w") as f:
        json.dump(sounds_json, f, indent=1)
    with open(os.path.join(HERE, "subtitles.json"), "w") as f:
        json.dump(lang, f, indent=1)
    print(f"wrote {total} ogg files for {len(EVENTS)} events")


def refresh_swing_aliases():
    """Add event fallbacks without re-encoding any already accepted audio samples."""
    path = os.path.join(ASSETS, "sounds.json")
    sounds = json.load(open(path))
    lang_path = os.path.join(HERE, "subtitles.json")
    lang = json.load(open(lang_path))
    for event in ("swing.knife", "swing.sword", "swing.blunt"):
        recipe, subtitle = EVENTS[event]
        sounds[event] = {"subtitle": f"subtitles.holdfast_core.{event}",
                         "sounds": [{"name": f"holdfast_core:{recipe}_{i + 1}"} for i in range(RECIPES[recipe][0])]}
        lang[f"subtitles.holdfast_core.{event}"] = subtitle
    json.dump(sounds, open(path, "w"), indent=1)
    json.dump(lang, open(lang_path, "w"), indent=1)


if __name__ == "__main__":
    import sys
    refresh_swing_aliases() if "--refresh-swing-aliases" in sys.argv else main()
