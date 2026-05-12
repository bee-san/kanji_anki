# Embedded Fonts

Kani randomizes one font variant each time a `font_meaning` study card is
shown. Font cards now use exactly three bundled Japanese display variants:

- CineCaption 2.26, bundled from the user's local copy with explicit rights
  holder permission.
- DotGothic16 Regular from Fontworks
  (`https://github.com/fontworks-fonts/DotGothic16`). It recreates the feel of
  old 16x16 Japanese bitmap fonts while retaining modern vector Japanese
  coverage.
- Reggae One Regular from Fontworks
  (`https://github.com/fontworks-fonts/Reggae`). It is a high-energy Japanese
  display font with sharpened stroke ends, useful for kanji shape variation.

DotGothic16 and Reggae One are distributed under the SIL Open Font License 1.1.
CineCaption is not OFL; it is bundled under explicit permission from its rights
holder. The in-app attribution text is packaged at
`app/src/main/res/raw/font_attribution.txt`.
