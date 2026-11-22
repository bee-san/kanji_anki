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

<p align="center">
  <b>Stop mixing up the kanji you already study.</b>
</p>

<p align="center">
  <picture><source media="(prefers-color-scheme: dark)" srcset="docs/screenshots/hero-dark.png"><img alt="Three Kani screens: a mix-up card comparing 末 and 未, the Today screen with 7 kanji to train, and handwriting the part that makes 待" src="docs/screenshots/hero.png" width="760"></picture>
</p>

<p align="center">
  <a href="https://github.com/bee-san/kanji_anki/releases"><b>Download the APK</b></a> · needs <a href="https://github.com/ankidroid/Anki-Android">AnkiDroid</a>
</p>

You know 待. You know 持. You still mix them up. Kani finds the kanji you keep getting wrong in Anki and trains just those, a few minutes a day.

## It finds them for you

<p align="center">
  <picture><source media="(prefers-color-scheme: dark)" srcset="docs/screenshots/welcome-dark.png"><img alt="Welcome screen: Stop mixing up the kanji you already study" src="docs/screenshots/welcome.png" width="250"></picture>
  <picture><source media="(prefers-color-scheme: dark)" srcset="docs/screenshots/scan-dark.png"><img alt="Scan result: 47 kanji keep slipping out of 1,846, with leech, failed and suspended counts" src="docs/screenshots/scan.png" width="250"></picture>
  <picture><source media="(prefers-color-scheme: dark)" srcset="docs/screenshots/words-dark.png"><img alt="Only the kanji you miss: 待ち合わせ flags 待 and clears 合" src="docs/screenshots/words.png" width="250"></picture>
</p>

Kani reads your AnkiDroid history and picks out your leeches, repeat failures and suspended cards. A failing word only flags the kanji that's actually the problem: `待ち合わせ` flags 待, not 合.

## A few minutes a day

<p align="center">
  <picture><source media="(prefers-color-scheme: dark)" srcset="docs/screenshots/check-dark.png"><img alt="Meaning check for 末 with the answer typed in" src="docs/screenshots/check.png" width="250"></picture>
  <picture><source media="(prefers-color-scheme: dark)" srcset="docs/screenshots/check-reading-dark.png"><img alt="Reading check for 期待: romaji typed as kita shows as きた" src="docs/screenshots/check-reading.png" width="250"></picture>
  <picture><source media="(prefers-color-scheme: dark)" srcset="docs/screenshots/reveal-dark.png"><img alt="Reveal card: right, きたい, with the word, its meaning and a speaker button" src="docs/screenshots/reveal.png" width="250"></picture>
</p>

Type the meaning or the reading. Romaji turns into kana as you go.

## See why you got it wrong

<p align="center">
  <picture><source media="(prefers-color-scheme: dark)" srcset="docs/screenshots/contrast-dark.png"><img alt="Mix-up card: you read 末 as 未, with the differing stroke in colour" src="docs/screenshots/contrast.png" width="250"></picture>
  <picture><source media="(prefers-color-scheme: dark)" srcset="docs/screenshots/hook-dark.png"><img alt="Build your own hook for 微 after missing its meaning twice" src="docs/screenshots/hook.png" width="250"></picture>
  <picture><source media="(prefers-color-scheme: dark)" srcset="docs/screenshots/early-dark.png"><img alt="New in your Anki: 侍 looks like 待, shown side by side" src="docs/screenshots/early.png" width="250"></picture>
</p>

Mix up two look-alikes and Kani puts them side by side with the difference in colour. Keep missing a meaning and you write your own hook. New look-alikes get shown to you before they can trip you up.

## Sprint your look-alikes

<p align="center">
  <picture><source media="(prefers-color-scheme: dark)" srcset="docs/screenshots/sprint-flash-dark.png"><img alt="Sprint: 待 flashes on screen" src="docs/screenshots/sprint-flash.png" width="250"></picture>
  <picture><source media="(prefers-color-scheme: dark)" srcset="docs/screenshots/sprint-pick-dark.png"><img alt="Sprint: pick the kanji you saw from 持, 待, 侍 and 特" src="docs/screenshots/sprint-pick.png" width="250"></picture>
  <picture><source media="(prefers-color-scheme: dark)" srcset="docs/screenshots/sprint-feedback-dark.png"><img alt="Sprint feedback: the wrong pick 持 in red, the right answer 待 in blue" src="docs/screenshots/sprint-feedback.png" width="250"></picture>
</p>

A kanji flashes, then you pick it out from its look-alikes. Works with a controller too.

## Write the part that decides

<p align="center">
  <picture><source media="(prefers-color-scheme: dark)" srcset="docs/screenshots/write-empty-dark.png"><img alt="Handwriting: the shared strokes of 待 are faint and a dashed box marks the missing part" src="docs/screenshots/write-empty.png" width="300"></picture>
  <picture><source media="(prefers-color-scheme: dark)" srcset="docs/screenshots/write-dark.png"><img alt="Handwriting: the left part of 待 drawn and accepted" src="docs/screenshots/write.png" width="300"></picture>
</p>

If a pair keeps coming back, you draw the strokes that set them apart.

## Solid after three days

<p align="center">
  <picture><source media="(prefers-color-scheme: dark)" srcset="docs/screenshots/done-dark.png"><img alt="Session done: 2 kanji now solid, with each kanji on day 1, 2 or 3 of 3" src="docs/screenshots/done.png" width="250"></picture>
  <picture><source media="(prefers-color-scheme: dark)" srcset="docs/screenshots/blind-spots-dark.png"><img alt="Blind spots: look-alike pairs with mix-up counts and Training, Watching or Solid" src="docs/screenshots/blind-spots.png" width="250"></picture>
  <picture><source media="(prefers-color-scheme: dark)" srcset="docs/screenshots/today-dark.png"><img alt="Today: 7 kanji you keep missing, and your top blind spots" src="docs/screenshots/today.png" width="250"></picture>
</p>

Get a kanji right, and fast, on three separate days and it's solid. Kani keeps an eye on it after that and brings it back if it starts to slip.

## Light or true black

<p align="center">
  <img alt="Today screen in the light theme" src="docs/screenshots/today.png" width="250">
  <img alt="Today screen in the AMOLED theme" src="docs/screenshots/today-dark.png" width="250">
  <img alt="Settings in the AMOLED theme" src="docs/screenshots/settings-dark.png" width="250">
</p>

## It leaves your Anki alone

Kani never touches your scheduling and nothing leaves your phone. The only thing it can write is a `kani_solid` tag, and only if you turn that on.

## License

Kani is licensed under the [European Union Public Licence v1.2](LICENSE) (EUPL-1.2).

The RWKV memory model in [`rwkv/`](rwkv/) is ported from Anki and remains under the [GNU AGPL v3 or later](rwkv/LICENSE). The EUPL lists the AGPL v3 as a compatible licence, so the app as a whole is distributed under AGPL-3.0-or-later. See [`THIRD_PARTY_NOTICES.md`](THIRD_PARTY_NOTICES.md) for third-party code, models, and data.
