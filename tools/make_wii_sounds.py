#!/usr/bin/env python3
"""Synthesizes the Wii-style menu sounds (original, no Nintendo audio) into res/raw as small mono 22.05 kHz OGGs.

Needs numpy and ffmpeg (libvorbis). Run from the repo root: python3 tools/make_wii_sounds.py
"""
import os
import subprocess
import tempfile
import wave

import numpy as np

SR = 22050
OUT = os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", "launcher", "app", "src", "main", "res", "raw")


def t(sec):
    return np.arange(int(SR * sec)) / SR


def bell(freq, sec, tau):
    x = t(sec)
    partials = [(1.0, 1.0), (2.0, 0.35), (3.01, 0.18), (4.2, 0.08)]
    y = sum(a * np.sin(2 * np.pi * freq * k * x) * np.exp(-x / (tau / k ** 0.5)) for k, a in partials)
    attack = np.minimum(1.0, x / 0.004)
    return y * attack


def startup():
    notes = [(523.25, 0.00), (659.25, 0.13), (783.99, 0.26), (1046.5, 0.39), (1318.5, 0.56)]
    y = np.zeros(int(SR * 1.5))
    for f, at in notes:
        b = bell(f, 1.5 - at, 0.34 if f < 1300 else 0.5)
        i = int(SR * at)
        y[i:i + len(b)] += b * (0.8 if f < 1300 else 0.55)
    y = y[:int(SR * 1.2)]
    y *= np.minimum(1.0, (len(y) - np.arange(len(y))) / (SR * 0.25))
    return y


def tick():
    x = t(0.03)
    y = np.sin(2 * np.pi * 1760 * x) * np.exp(-x / 0.007) + 0.4 * np.sin(2 * np.pi * 880 * x) * np.exp(-x / 0.01)
    return y * np.minimum(1.0, x / 0.002)


def swoosh():
    n = int(SR * 0.2)
    rng = np.random.default_rng(7)
    noise = rng.standard_normal(n)
    x = np.linspace(0, 1, n)
    centre = 500 * (5.0 ** x)
    q = 0.9
    low = band = 0.0
    y = np.zeros(n)
    for i in range(n):
        f = 2 * np.sin(np.pi * centre[i] / SR)
        low += f * band
        high = noise[i] - low - q * band
        band += f * high
        y[i] = band
    return y * np.sin(np.pi * x) ** 2


def confirm():
    parts = []
    for f, sec in ((659.25, 0.07), (987.77, 0.1)):
        x = t(sec)
        parts.append((np.sin(2 * np.pi * f * x) + 0.25 * np.sin(2 * np.pi * 2 * f * x)) * np.exp(-x / (sec * 0.5))
                     * np.minimum(1.0, x / 0.004))
    return np.concatenate(parts)


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
        subprocess.run(["ffmpeg", "-y", "-loglevel", "error", "-i", wav, "-ac", "1", "-ar", str(SR),
                        "-c:a", "libvorbis", "-q:a", "0", dest], check=True)
        print(name, os.path.getsize(dest), "bytes")


if __name__ == "__main__":
    os.makedirs(OUT, exist_ok=True)
    write_ogg("wii_startup", startup(), 0.85)
    write_ogg("wii_tick", tick(), 0.6)
    write_ogg("wii_swoosh", swoosh(), 0.6)
    write_ogg("wii_confirm", confirm(), 0.8)
