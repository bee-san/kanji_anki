<!-- markdownlint-disable MD013 MD033 MD041 -->

<p align="center">
  <img alt="Kani app logo" src="docs/assets/kani-seal.svg" width="160">
</p>

<h1 align="center">Kani</h1>

<p align="center">
  <a href="https://github.com/bee-san/kanji_anki/releases">
    <img alt="GitHub downloads" src="https://img.shields.io/github/downloads/bee-san/kanji_anki/total?style=for-the-badge&logo=github&label=downloads">
  </a>
  <a href="https://github.com/bee-san/kanji_anki/actions/workflows/sonarqube.yml">
    <img alt="SonarCloud quality gate" src="https://sonarcloud.io/api/project_badges/measure?project=bee-san_kanji_anki&metric=alert_status">
  </a>
  <a href="https://sonarcloud.io/summary/overall?id=bee-san_kanji_anki">
    <img alt="SonarCloud code smells" src="https://sonarcloud.io/api/project_badges/measure?project=bee-san_kanji_anki&metric=code_smells">
  </a>
  <a href="https://discord.skerritt.blog"><img alt="Discord" src="https://img.shields.io/discord/754001738184392704?logo=discord&label=Discord"></a>
</p>

Kani does one thing: it stops you mixing up the kanji you already study. It reads your AnkiDroid history, finds the kanji you keep missing, and trains only those, in sessions of a few minutes.

## How it works

1. **Setup picks everything for you.** Kani finds the note types, decks and word/reading/meaning fields in AnkiDroid and asks you to confirm. It then looks for leeches, cards failed twice or more, and suspended cards.
2. **Only the kanji you miss.** A failing word only flags the kanji you don't read fine elsewhere: `待ち合わせ` flags 待, not 合, if you read 合 in plenty of healthy words.
3. **Every mistake shows you why.** Type the meaning of a look-alike and Kani shows the pair side by side, with the strokes that tell them apart in colour (worked out from KanjiVG). Miss a reading and it shows the kanji's sound family. Miss a meaning twice and you write your own hook.
4. **Look-alike sprints.** A kanji flashes for 0.7 s, a mask wipes it, and you pick it from its look-alikes. Face buttons (X Y A B) work on handhelds and controllers.
5. **Write the part that decides.** A pair that keeps coming back moves to handwriting: the shared strokes stay faint and you draw the rest from memory.
6. **Solid after three days.** A kanji is solid when it's right on three separate days and fast. If you turn it on, Kani then tags its notes `kani_solid` in Anki. Kani never changes your scheduling.

Also: it meets new look-alikes before you do (a new kanji in Anki that looks like one you know is shown once, first), speaks the word on reveal with Android's text-to-speech, has a home-screen widget with your top pair, a daily reminder, and a light theme by day with true-black AMOLED by night.

## License

Kani is licensed under the [European Union Public Licence v1.2](LICENSE) (EUPL-1.2).

The RWKV memory model in [`rwkv/`](rwkv/) is ported from Anki and remains under the [GNU AGPL v3 or later](rwkv/LICENSE). The EUPL lists the AGPL v3 as a compatible licence, so the app as a whole is distributed under AGPL-3.0-or-later. See [`THIRD_PARTY_NOTICES.md`](THIRD_PARTY_NOTICES.md) for third-party code, models, and data.
