#!/usr/bin/env python3
"""Synthesizes the Wii Home background music into res/raw/wii_music.ogg: an ORIGINAL calm loop (no Nintendo audio),
soft electric piano chords, a bell melody and a round bass, 100 BPM, 8 bars (19.2 s), seamless when looped.
Dedicated to the public domain under CC0 (and usable under the repo's MIT licence too).

Needs numpy and ffmpeg (libvorbis). Run from the repo root: python3 tools/make_wii_music.py
"""
import os
import subprocess
import tempfile
import wave

import numpy as np

SR = 22050
BPM = 100
BEAT = 60.0 / BPM
BARS = 8
LOOP = int(round(SR * BARS * 4 * BEAT))
OUT = os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", "launcher", "app", "src", "main", "res", "raw")


def hz(note):
    """MIDI note number to Hz."""
    return 440.0 * 2 ** ((note - 69) / 12.0)


def env(n, attack, tau):
    x = np.arange(n) / SR
    return np.minimum(1.0, x / attack) * np.exp(-x / tau)


def epiano(note, sec, vel):
    """Rhodes-like FM tine: a sine modulated at 1:1 with a fast-decaying index, plus a soft high tine click."""
    n = int(SR * sec)
    x = np.arange(n) / SR
    f = hz(note)
    index = 1.6 * np.exp(-x / 0.18) + 0.15
    y = np.sin(2 * np.pi * f * x + index * np.sin(2 * np.pi * f * x))
    y += 0.12 * np.sin(2 * np.pi * f * 14 * x) * np.exp(-x / 0.02)
    return vel * y * env(n, 0.006, 1.1)


def bell(note, sec, vel):
    """Glassy bell / celesta: a few inharmonic partials, the high ones dying first."""
    n = int(SR * sec)
    x = np.arange(n) / SR
    f = hz(note)
    y = np.zeros(n)
    for ratio, amp, tau in ((1.0, 1.0, 0.9), (2.0, 0.3, 0.5), (3.0, 0.12, 0.25), (4.07, 0.06, 0.15)):
        y += amp * np.sin(2 * np.pi * f * ratio * x) * np.exp(-x / tau)
    return vel * y * np.minimum(1.0, x / 0.003)


def bass(note, sec, vel):
    """Round, plucked sine bass with a touch of second harmonic."""
    n = int(SR * sec)
    x = np.arange(n) / SR
    f = hz(note)
    y = np.sin(2 * np.pi * f * x) + 0.25 * np.sin(2 * np.pi * 2 * f * x) * np.exp(-x / 0.15)
    rel = np.minimum(1.0, (n - np.arange(n)) / (SR * 0.05))
    return vel * y * env(n, 0.01, 0.9) * rel


# One chord per bar (MIDI voicings around middle C), a jazzy, sunny progression in F major.
CHORDS = [
    [57, 60, 64, 67],   # Fmaj7 (A C E G)
    [55, 59, 62, 64],   # G6 / Em7 colour (G B D E)
    [57, 60, 64, 67],   # Am7 (A C E G) over A
    [57, 60, 62, 65],   # Dm7 (A C D F)
    [58, 62, 65, 69],   # Bbmaj7 (Bb D F A)
    [57, 60, 64, 67],   # Am7
    [55, 58, 62, 65],   # Gm7 (G Bb D F)
    [55, 60, 62, 65],   # C9sus (G C D F)
]
BASS_ROOTS = [41, 43, 45, 38, 46, 45, 43, 36]

# The melody: (bar, beat, MIDI note, length in beats). Gentle and stepwise, answering itself in the second half.
MELODY = [
    (0, 0.0, 76, 1.5), (0, 1.5, 79, 0.5), (0, 2.0, 77, 1.0), (0, 3.0, 76, 1.0),
    (1, 0.0, 74, 1.5), (1, 1.5, 71, 0.5), (1, 2.0, 74, 2.0),
    (2, 0.0, 72, 1.0), (2, 1.0, 76, 1.0), (2, 2.0, 79, 1.5), (2, 3.5, 81, 0.5),
    (3, 0.0, 77, 3.0), (3, 3.0, 74, 1.0),
    (4, 0.0, 77, 1.5), (4, 1.5, 81, 0.5), (4, 2.0, 79, 1.0), (4, 3.0, 77, 1.0),
    (5, 0.0, 76, 1.5), (5, 1.5, 72, 0.5), (5, 2.0, 76, 2.0),
    (6, 0.0, 74, 1.0), (6, 1.0, 77, 1.0), (6, 2.0, 79, 1.0), (6, 3.0, 82, 1.0),
    (7, 0.0, 81, 2.0), (7, 2.0, 79, 1.0), (7, 3.0, 77, 0.5), (7, 3.5, 79, 0.5),
]


def add(buf, at_sec, sound):
    """Mixes a sound into the loop, wrapping its tail around to the start so the loop is seamless."""
    i = int(round(at_sec * SR)) % LOOP
    for k in range(0, len(sound), LOOP):
        part = sound[k:k + LOOP]
        j = i
        first = min(len(part), LOOP - j)
        buf[j:j + first] += part[:first]
        if first < len(part):
            buf[:len(part) - first] += part[first:]


def music():
    buf = np.zeros(LOOP)
    bar = 4 * BEAT
    for b, chord in enumerate(CHORDS):
        start = b * bar
        # Electric piano: the chord on beat 1 and a softer push on the "and" of 2, gently spread.
        for hit, vel in ((0.0, 0.32), (1.5, 0.2), (3.0, 0.14)):
            for k, note in enumerate(chord):
                add(buf, start + hit * BEAT + k * 0.012, epiano(note, 2.6, vel))
        root = BASS_ROOTS[b]
        for beat, note, vel in ((0.0, root, 0.55), (2.0, root + 7, 0.4), (3.5, root + 12, 0.25)):
            add(buf, start + beat * BEAT, bass(note, 1.4 * BEAT if beat < 3 else 0.5 * BEAT, vel))
    for b, beat, note, length in MELODY:
        add(buf, b * bar + beat * BEAT, bell(note, max(1.2, length * BEAT + 0.8), 0.36))
        # A quiet octave sparkle on the long notes.
        if length >= 1.5:
            add(buf, b * bar + beat * BEAT + 0.01, bell(note + 12, 1.0, 0.07))
    # A short circular echo (3/8 note) to soften it; circular so the loop stays seamless.
    d = int(round(SR * BEAT * 0.75))
    buf = buf + 0.22 * np.roll(buf, d) + 0.08 * np.roll(buf, 2 * d)
    # Gentle low-pass (one-pole, run twice around the loop so it settles to the circular steady state).
    a = np.exp(-2 * np.pi * 4500 / SR)
    y = np.zeros(LOOP)
    z = 0.0
    for _ in range(2):
        for i in range(LOOP):
            z = (1 - a) * buf[i] + a * z
            y[i] = z
    return y


def write_ogg(name, y, peak):
    y = y / np.max(np.abs(y)) * peak
    pcm = (y * 32767).astype("<i2")
    with tempfile.TemporaryDirectory() as d:
        wav = os.path.join(d, name + ".wav")
        with wave.open(wav, "wb") as w:
            w.setnchannels(1)
            w.setsampwidth(2)
            w.setframerate(SR)
            w.writeframes(pcm.tobytes())
        dest = os.path.join(OUT, name + ".ogg")
        # ANDROID_LOOP makes Android's Ogg extractor loop the stream itself, sample-exact (no gap at the seam).
        subprocess.run(["ffmpeg", "-y", "-loglevel", "error", "-i", wav, "-ac", "1", "-ar", str(SR),
                        "-c:a", "libvorbis", "-b:a", "40k",
                        "-metadata", "ANDROID_LOOP=true", dest], check=True)
        print(name, os.path.getsize(dest), "bytes,", round(len(y) / SR, 2), "s")


if __name__ == "__main__":
    os.makedirs(OUT, exist_ok=True)
    write_ogg("wii_music", music(), 0.8)
