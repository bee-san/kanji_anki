<!-- markdownlint-disable MD013 MD033 MD041 -->

<p align="center">
  <img alt="Kani app logo" src="app/src/main/res/mipmap-xxxhdpi/ic_launcher.png" width="160">
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
</p>

Kani cures your Kanji blindness by analysing your Anki, locating your hard kanji, working out why it's hard for you, and creating specialised learning loops to teach you the Kanji in the least amount of time possible.

Kani is an AnkiDroid companion app for Japanese learners who repeatedly miss the same kanji — the painful "kanji blindness" loop where similar-looking characters keep tripping you up.

Kani borrows the parts of Anki that make recall durable: saved evidence from your own cards, FSRS-style review timing, and a repeatable review habit. It intentionally differs from Anki by narrowing the surface area to kanji repair. Kani reads AnkiDroid, builds a small local focus queue, and asks you to practice the characters that are causing real misses instead of managing another general-purpose deck.

Kani helps you:

1. Find kanji that keep causing trouble in your AnkiDroid reviews.
2. Focus on why they are hard, such as unfamiliar characters, visually similar kanji, or unusual readings.
3. Study them through a small, structured queue instead of another full SRS backlog.
4. Compare later AnkiDroid evidence so repaired kanji can retire from the queue.
 append the approved ideas as unchecked queue entries in the requested order.

<img width="472" height="847" alt="image" src="https://github.com/user-attachments/assets/8b62e8a4-b93c-4bff-9348-71dd8c24321c" />
<img width="416" height="845" alt="image" src="https://github.com/user-attachments/assets/ae7fcf8b-e4a6-4c15-93f4-44b5a2897780" />
<img width="481" height="847" alt="image" src="https://github.com/user-attachments/assets/4cf4eef9-dd34-497b-9363-eb1f3224e9f9" />
<img width="647" height="847" alt="image" src="https://github.com/user-attachments/assets/6863e0e8-26d7-42e2-8a97-990668a96fb9" />




- [x] Build the screenshot-driven Cheap Ralph UX improvement loop from `docs/plans/cheap-ralph/2026-06-09-screenshot-driven-ux-improvement-loop.md`: deterministically capture every view, store screenshot manifests, ask a design critic for machine-readable improved-view targets, implement one accepted issue per iteration in a scratch checkout, and gate apply/commit on before/after screenshots, compile/tests, forbidden-path guards, diff limits, and design-critic improvement. Default to review-only and never treat compile-only success as visual validation.
<!-- cheap-ralph-queue:end -->
