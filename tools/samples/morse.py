#!/usr/bin/env python3
"""Builds Quiz's "Morse code" sample: a sound for each letter on side A, the letter on side B.

    python3 tools/samples/morse.py

Writes apps/quiz/src/main/assets/samples/Morse code.zip (a .qzx plus one WAV
per letter), then regenerate the sample catalog (see SampleCatalogTest). The
sounds are made here, so the sample has no outside source to credit. The same
Python gives the same bytes on every run (fixed zip dates).

Timing is standard Morse: a dot is one unit, a dash three, with one unit of
silence between them. A unit of 70 ms is about 17 words a minute. The tone is
640 Hz, a common practice pitch, at 8000 samples a second: 12.5 samples a
cycle, so the waveform repeats every 25 samples and the zip shrinks it well.
Each tone starts and ends with a 5 ms ramp, which keeps it from clicking.
"""

import io
import math
import struct
import wave
import zipfile
from pathlib import Path

CODE = {
    "A": ".-", "B": "-...", "C": "-.-.", "D": "-..", "E": ".", "F": "..-.", "G": "--.",
    "H": "....", "I": "..", "J": ".---", "K": "-.-", "L": ".-..", "M": "--", "N": "-.",
    "O": "---", "P": ".--.", "Q": "--.-", "R": ".-.", "S": "...", "T": "-", "U": "..-",
    "V": "...-", "W": ".--", "X": "-..-", "Y": "-.--", "Z": "--..",
}

RATE = 8000        # samples a second
TONE = 640.0       # Hz
UNIT = 0.070       # seconds
RAMP = 0.005       # seconds
PAD = 0.150        # silence before and after, so no player clips the start
LOUDNESS = 0.5     # of full scale
ZIP_DATE = (2026, 11, 4, 0, 0, 0)

OUT = Path(__file__).resolve().parents[2] / "apps/quiz/src/main/assets/samples/Morse code.zip"


def silence(seconds):
    return [0] * round(seconds * RATE)


def tone(seconds):
    n = round(seconds * RATE)
    ramp = round(RAMP * RATE)
    samples = []
    for i in range(n):
        edge = min(i, n - 1 - i)
        gain = 0.5 - 0.5 * math.cos(math.pi * edge / ramp) if edge < ramp else 1.0
        samples.append(round(LOUDNESS * 32767 * gain * math.sin(2 * math.pi * TONE * i / RATE)))
    return samples


def letter_wav(code):
    samples = silence(PAD)
    for k, symbol in enumerate(code):
        if k:
            samples += silence(UNIT)
        samples += tone(UNIT if symbol == "." else 3 * UNIT)
    samples += silence(PAD)
    buffer = io.BytesIO()
    with wave.open(buffer, "wb") as out:
        out.setnchannels(1)
        out.setsampwidth(2)
        out.setframerate(RATE)
        out.writeframes(struct.pack("<%dh" % len(samples), *samples))
    return buffer.getvalue()


def main():
    letters = sorted(CODE)
    qzx = "".join(f"!{letter.lower()}.wav\n{letter}\n" for letter in letters)
    OUT.parent.mkdir(parents=True, exist_ok=True)
    with zipfile.ZipFile(OUT, "w") as bundle:
        def add(name, data):
            entry = zipfile.ZipInfo(name, date_time=ZIP_DATE)
            entry.compress_type = zipfile.ZIP_DEFLATED
            entry.external_attr = 0o644 << 16
            bundle.writestr(entry, data)

        add("Morse code.qzx", qzx.encode("utf-8"))
        for letter in letters:
            add(f"{letter.lower()}.wav", letter_wav(CODE[letter]))
    print(f"{OUT}: {OUT.stat().st_size} bytes, {len(letters)} letters")


if __name__ == "__main__":
    main()
