#!/usr/bin/env python3
"""Locale matrix for the shared Compose resources both hosts render.

Goal 203 asks that every user-visible desktop string be migrated through shared
resources and tested in English and Japanese plus long-string
pseudo-localization. The first two are checkable here without a window, and this
is the only place they are checkable *across* modules: a Kotlin render test
resolves one module's `Res.string`, so a locale that went missing in
`feature-stats` cannot fail a `feature-study` test. The rendering side — that a
resolved string actually fits, at a large font and a long translation — is in
`StudyLocaleAssertions.kt`, which needs a composition and therefore runs per
module.

What is asserted here is deliberately structural, because these are the failures
that ship silently:

- A key present in one locale and not the other. Compose falls back to the
  default locale, so the app keeps working and shows English to a Japanese user
  — the one failure mode no test that renders a single locale will catch.
- A format placeholder that differs between locales. `%1$d` dropped in
  translation is a number that never appears; `%2$s` where the English has
  `%1$s` silently swaps two substitutions. Neither is a compile error and both
  survive every type check in the build.
- A plural whose quantity items differ. Japanese has no plural distinction, so
  its `plurals` carries one `other` item by design; what must match is the
  *name* set, not the item set.
- A Japanese value byte-identical to its English one, which is an untranslated
  string unless it is deliberately locale-invariant. The exceptions are listed
  by name below with the reason, so adding one is a decision rather than an
  accident.
"""

from __future__ import annotations

import re
import unittest
import xml.etree.ElementTree as ElementTree
from pathlib import Path


ROOT = Path(__file__).resolve().parents[1]

# The locale every module must also carry. Kani ships English and Japanese; a
# third locale would be a new entry here and a new `values-xx` directory, and
# every assertion below is written over the set rather than over the pair.
TRANSLATED_LOCALES = ("ja",)

# Values that are the same in both locales on purpose, with the reason. Anything
# not on this list that matches its English source is an untranslated string.
LOCALE_INVARIANT_VALUES = {
    # A format shell — "%1$s (%2$s)" — with no words of its own to translate.
    ("feature-home", "note_type_label"),
    # An Anki search query, typed into AnkiDroid's own search box. Anki's search
    # syntax is not localized, so translating `tag:` or `is:suspended` would
    # produce a query that matches nothing.
    ("feature-home", "repaired_handoff_query"),
}

PLACEHOLDER = re.compile(r"%(?:\d+\$)?[sd]")


def resource_modules() -> tuple[str, ...]:
    """Every module carrying shared Compose string resources, sorted."""
    roots = ROOT.glob("*/src/commonMain/composeResources/values/strings.xml")
    return tuple(sorted(path.relative_to(ROOT).parts[0] for path in roots))


def _strings_file(module: str, locale: str | None) -> Path:
    values = "values" if locale is None else f"values-{locale}"
    return ROOT / module / "src/commonMain/composeResources" / values / "strings.xml"


def read_strings(module: str, locale: str | None = None) -> dict[str, str]:
    """The `<string>` entries of one module and locale, by name."""
    root = ElementTree.parse(_strings_file(module, locale)).getroot()
    return {entry.get("name", ""): (entry.text or "") for entry in root.findall("string")}


def read_plurals(module: str, locale: str | None = None) -> dict[str, dict[str, str]]:
    """The `<plurals>` entries of one module and locale, name to quantity map."""
    root = ElementTree.parse(_strings_file(module, locale)).getroot()
    return {
        entry.get("name", ""): {
            item.get("quantity", ""): (item.text or "") for item in entry.findall("item")
        }
        for entry in root.findall("plurals")
    }


class SharedStringModuleCoverageTest(unittest.TestCase):
    def test_every_module_with_resources_is_known_and_carries_every_locale(self) -> None:
        modules = resource_modules()
        # Named rather than discovered-only: a feature that loses its resource
        # directory entirely would otherwise make this whole file pass vacuously.
        self.assertEqual(
            (
                "feature-games",
                "feature-home",
                "feature-missing-kanji",
                "feature-settings",
                "feature-shell",
                "feature-stats",
                "feature-study",
            ),
            modules,
        )
        for module in modules:
            for locale in TRANSLATED_LOCALES:
                with self.subTest(module=module, locale=locale):
                    self.assertTrue(_strings_file(module, locale).is_file())

    def test_no_module_ships_an_empty_resource_set(self) -> None:
        for module in resource_modules():
            with self.subTest(module=module):
                names = set(read_strings(module)) | set(read_plurals(module))
                self.assertTrue(names, f"{module} declares no shared strings")


class SharedStringLocaleParityTest(unittest.TestCase):
    def test_every_key_exists_in_every_locale(self) -> None:
        # The silent one: Compose falls back to the default locale, so a missing
        # Japanese key shows English to a Japanese user with no error anywhere.
        for module in resource_modules():
            for locale in TRANSLATED_LOCALES:
                with self.subTest(module=module, locale=locale):
                    default = set(read_strings(module))
                    translated = set(read_strings(module, locale))
                    self.assertEqual(
                        set(),
                        default - translated,
                        f"{module} is missing {locale} strings",
                    )
                    self.assertEqual(
                        set(),
                        translated - default,
                        f"{module} has {locale} strings with no default",
                    )

    def test_every_plural_exists_in_every_locale(self) -> None:
        for module in resource_modules():
            for locale in TRANSLATED_LOCALES:
                with self.subTest(module=module, locale=locale):
                    self.assertEqual(
                        set(read_plurals(module)),
                        set(read_plurals(module, locale)),
                        f"{module} plural names differ in {locale}",
                    )

    def test_no_value_is_blank_in_any_locale(self) -> None:
        for module in resource_modules():
            for locale in (None, *TRANSLATED_LOCALES):
                strings = read_strings(module, locale)
                for name, value in sorted(strings.items()):
                    with self.subTest(module=module, locale=locale, name=name):
                        self.assertTrue(value.strip(), f"{module}/{name} is blank")

    def test_no_value_carries_an_android_backslash_escape(self) -> None:
        """No shared string may use Android's XML backslash escapes.

        `\\'` and `\\"` are Android resource conventions. Compose Multiplatform's
        resource reader does not interpret them, so it renders the backslash to the
        user: desktop Home showed "This collection doesn\\'t report FSRS memory
        state". XML needs no escape for an apostrophe at all, and a double quote
        inside an element's text is equally fine, so the correct fix is always to
        delete the backslash rather than to escape it differently.

        Found by screenshotting the running desktop app — no test covered it, because
        every existing check compares locales against each other and both locales
        carried the same wrong bytes.
        """
        for module in resource_modules():
            for locale in (None, *TRANSLATED_LOCALES):
                strings = read_strings(module, locale)
                for name, value in sorted(strings.items()):
                    for escape in ("\\'", '\\"'):
                        with self.subTest(module=module, locale=locale, name=name):
                            self.assertNotIn(
                                escape,
                                value,
                                f"{module}/{name} uses the Android escape {escape!r}; "
                                "Compose Multiplatform renders the backslash literally",
                            )

    def test_every_translation_differs_unless_deliberately_invariant(self) -> None:
        for module in resource_modules():
            default = read_strings(module)
            for locale in TRANSLATED_LOCALES:
                translated = read_strings(module, locale)
                for name, value in sorted(default.items()):
                    if (module, name) in LOCALE_INVARIANT_VALUES:
                        continue
                    with self.subTest(module=module, locale=locale, name=name):
                        self.assertNotEqual(
                            value,
                            translated[name],
                            f"{module}/{name} is untranslated in {locale}",
                        )

    def test_every_declared_invariant_is_real_and_still_needed(self) -> None:
        # A stale entry here would suppress a genuine untranslated string, so the
        # exception list is itself checked against the resources.
        for module, name in sorted(LOCALE_INVARIANT_VALUES):
            with self.subTest(module=module, name=name):
                default = read_strings(module)
                self.assertIn(name, default, f"{module}/{name} no longer exists")
                for locale in TRANSLATED_LOCALES:
                    self.assertEqual(
                        default[name],
                        read_strings(module, locale)[name],
                        f"{module}/{name} is translated now; drop the exception",
                    )


class SharedStringPlaceholderParityTest(unittest.TestCase):
    def test_every_locale_uses_the_same_placeholders(self) -> None:
        # Compared as a multiset: a translation may reorder `%1$s` and `%2$s`,
        # which is the point of positional placeholders, but it may not drop one
        # or invent one. Dropping `%1$d` produces a sentence with no number in it
        # and no error at all.
        for module in resource_modules():
            default = read_strings(module)
            for locale in TRANSLATED_LOCALES:
                translated = read_strings(module, locale)
                for name, value in sorted(default.items()):
                    with self.subTest(module=module, locale=locale, name=name):
                        self.assertEqual(
                            sorted(PLACEHOLDER.findall(value)),
                            sorted(PLACEHOLDER.findall(translated[name])),
                            f"{module}/{name} placeholders differ in {locale}",
                        )

    def test_every_plural_item_uses_the_same_placeholders_as_its_default(self) -> None:
        # Not compared item-for-item: Japanese has no plural distinction, so its
        # plurals carry one `other` item by design. What must hold is that every
        # item, in either locale, substitutes the same values.
        for module in resource_modules():
            default = read_plurals(module)
            for locale in TRANSLATED_LOCALES:
                translated = read_plurals(module, locale)
                for name, items in sorted(default.items()):
                    expected = sorted(PLACEHOLDER.findall(next(iter(items.values()))))
                    for quantity, value in sorted((*items.items(), *translated[name].items())):
                        with self.subTest(module=module, name=name, quantity=quantity):
                            self.assertEqual(
                                expected,
                                sorted(PLACEHOLDER.findall(value)),
                                f"{module}/{name}[{quantity}] placeholders differ",
                            )

    def test_no_string_mixes_positional_and_bare_placeholders(self) -> None:
        # `"%1$s of %s"` throws at format time on one host and silently reuses an
        # argument on another. Kani's own resolver reads positional forms, so a
        # bare `%s` that slipped in is a defect wherever it renders.
        bare = re.compile(r"%[sd]")
        positional = re.compile(r"%\d+\$[sd]")
        for module in resource_modules():
            for locale in (None, *TRANSLATED_LOCALES):
                values = list(read_strings(module, locale).items())
                for name, items in read_plurals(module, locale).items():
                    values.extend((f"{name}[{q}]", v) for q, v in items.items())
                for name, value in sorted(values):
                    with self.subTest(module=module, locale=locale, name=name):
                        self.assertFalse(
                            bool(bare.search(positional.sub("", value))),
                            f"{module}/{name} mixes bare and positional placeholders",
                        )


if __name__ == "__main__":
    unittest.main()
