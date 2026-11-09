#!/usr/bin/env python3
"""Cloches des alertes du chrono (docs/spec/temps-reel-chrono.md § Cloches, issue #234).

Synthèse additive, bibliothèque standard seulement : sortie reproductible à l'octet, aucune licence de son à suivre.
Écrit les deux fichiers dans les ressources communes (bureau, web) et dans celles d'Android (canaux de notification).
Usage : python3 kmp/tools/make_bells.py
"""
import math
import struct
import sys
import wave
from pathlib import Path

RATE = 22050
PRIME = 392.0  # le nominal (2 × prime) est à 784 Hz, sol 5 : la note entendue, claire sans être aiguë
# Partiels d'une cloche (rapport à la prime, amplitude, constante de temps en s) : hum, prime, tierce mineure (le timbre
# « cloche »), quinte, nominal, puis les partiels supérieurs inharmoniques (le métal). Les graves durent le plus : la traîne.
PARTIALS = [
    (0.5, 0.30, 1.6), (1.0, 0.45, 1.2), (1.183, 0.45, 1.0), (1.506, 0.18, 0.6), (2.0, 1.0, 0.9),
    (2.514, 0.35, 0.5), (2.662, 0.25, 0.4), (3.011, 0.2, 0.3), (4.166, 0.12, 0.2),
]
# Doublets désaccordés de quelques hertz : leur battement donne le scintillement du métal.
BEATS = [(1.183, 0.2, 1.0, 1.7), (2.0, 0.4, 0.9, 2.3)]
# Frappe : partiels très aigus éteints en quelques dizaines de millisecondes (le « tic » du battant).
CLICK = [(6.24, 0.25, 0.03), (8.93, 0.18, 0.02), (11.4, 0.12, 0.015)]
ATTACK = 0.002
PEAK = 0.35  # « léger » : crête à 35 % de la pleine échelle


def strike(t: float) -> float:
    if t < 0:
        return 0.0
    attack = min(1.0, t / ATTACK)
    tone = sum(a * math.exp(-t / tau) * math.sin(2 * math.pi * PRIME * r * t) for r, a, tau in PARTIALS + CLICK)
    shimmer = sum(a * math.exp(-t / tau) * math.sin(2 * math.pi * (PRIME * r + hz) * t) for r, a, tau, hz in BEATS)
    return attack * (tone + shimmer)


def render(strikes: list[tuple[float, float]], seconds: float) -> bytes:
    """[strikes] : (instant en s, volume relatif)."""
    samples = [sum(g * strike(i / RATE - at) for at, g in strikes) for i in range(int(seconds * RATE))]
    scale = PEAK / max(abs(x) for x in samples)
    return b"".join(struct.pack("<h", round(x * scale * 32767)) for x in samples)


def write(path: Path, frames: bytes) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    with wave.open(str(path), "wb") as out:
        out.setnchannels(1)
        out.setsampwidth(2)
        out.setframerate(RATE)
        out.writeframes(frames)


BELLS = {
    "bell_single.wav": render([(0.0, 1.0)], 2.5),
    # La seconde frappe, plus douce, garde sa traîne : on distingue l'oubli de la pause à l'oreille.
    "bell_double.wav": render([(0.0, 1.0), (0.75, 0.55)], 3.2),
}


def main() -> None:
    kmp = Path(sys.argv[1]) if len(sys.argv) > 1 else Path(__file__).resolve().parent.parent
    for name, frames in BELLS.items():
        write(kmp / "ui/src/commonMain/composeResources/files" / name, frames)
        write(kmp / "androidApp/src/main/res/raw" / name, frames)
        print(name, len(frames) // 2 / RATE, "s")


if __name__ == "__main__":
    main()
