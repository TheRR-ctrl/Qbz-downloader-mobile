# Third-party work this app builds on

## iPod nano 6G/7G support (`app/src/main/java/com/qbdlx/mobile/ipod/`)

- **hashAB** - the anti-tamper signature the iPod nano 6G/7G checks on its library. A public-domain algorithm,
  reverse engineered and published at https://github.com/DigiDNA/hashab (and https://github.com/dstaley/hashab).
- **ipodsync** - https://github.com/buldiei/ipodsync, MIT License, Copyright (c) 2026 Alex Buldiei. It ported hashAB to
  Python and works out how to add and remove tracks in the nano's SQLite library. `HashAbGen*.kt` were produced by
  `tools/hashab/py2kt.py` from that port's syntax tree, `hashab_tables.bin` is its packed copy of the algorithm's tables,
  and `HashAb.kt`, `Cbk.kt` and the library logic follow its code.
- **TunesReloaded** - https://github.com/rish1p/tunesreloaded. The idea of managing an iPod from a program that only
  has access to the iPod's mounted folder (no iTunes), and the layout of the `.cbk` file.

```
MIT License

Copyright (c) 2026 Alex Buldiei

Permission is hereby granted, free of charge, to any person obtaining a copy of this software and associated
documentation files (the "Software"), to deal in the Software without restriction, including without limitation the
rights to use, copy, modify, merge, publish, distribute, sublicense, and/or sell copies of the Software, and to permit
persons to whom the Software is furnished to do so, subject to the following conditions:

The above copyright notice and this permission notice shall be included in all copies or substantial portions of the
Software.

THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE
WARRANTIES OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR
COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR
OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
```

Not affiliated with Apple. iPod and iTunes are trademarks of Apple Inc.

## Chromaprint (QBDLX Scanner)

`scanner/src/main/java/com/qbdlx/scanner/Chromaprint.kt` is a Kotlin port of the audio fingerprint of
[acoustid/chromaprint](https://github.com/acoustid/chromaprint) (LGPL-2.1-or-later): its frame/chroma pipeline,
classifier table and fingerprint compression. A unit test checks the output bit for bit against libchromaprint.
Songs are looked up on [AcoustID](https://acoustid.org), which needs its own free application key.

`usbdac/` is a copy of [ostrichbat2k/libusbdac](https://github.com/ostrichbat2k/libusbdac) (GPL-3.0-or-later, commit 3cc831d):
direct USB audio output (UAC1/2/3) that skips Android's mixer. Only its Gradle build file and one Room call
(`fallbackToDestructiveMigration()`) were changed. Its own LICENSE is in that folder.
