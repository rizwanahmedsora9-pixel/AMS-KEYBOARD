# Third-party notices

AMS Keyboard contains no third-party libraries, SDKs or icon packs. The runtime code depends only
on the Android framework.

## English word list

`app/src/main/res/raw/english_words.txt` is a trimmed and reordered subset of the
[`first20hours/google-10000-english`](https://github.com/first20hours/google-10000-english) lists
(`google-10000-english-usa-no-swears-medium.txt` plus the shorter everyday list).

That project derives its data from the **Google Web Trillion Word Corpus** (Thorsten Brants and
Alex Franz, Linguistic Data Consortium, LDC2006T13) through Peter Norvig's frequency compilation
(<https://norvig.com/ngrams/>), with corpus cleanup by Josh Kaufman. The upstream README states
that educational and personal/research use of the data is permitted under the LDC licence,
Norvig's MIT licence for his contributions, and US fair use doctrine, and that commercial use
should be licensed from the Linguistic Data Consortium.

Only individual English words are included: no sentences, no corpus text and no frequency data
beyond the ordering of the list. The file is used to suggest words and to correct typos while
typing.

## Fonts used to generate the launcher icon

`tools/generate_icons.py` rasterises the legacy launcher icon with a locally installed bold font.
The generated PNGs contain only the shapes of the letters A, M and S, which are not copyrightable
in this form. The adaptive icon (`drawable/ic_launcher_foreground.xml`) ships pre-outlined vector
paths, so no font file is bundled in the APK.
