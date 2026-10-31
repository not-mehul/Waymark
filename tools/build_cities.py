#!/usr/bin/env python3
"""Build core/catalog/src/main/resources/com/waymark/data/catalog/cities.txt, the bundled city gazetteer.

Source: GeoNames "cities15000" (every populated place of 15,000 people or more,
CC BY 4.0, https://www.geonames.org/), as packaged in the geonamescache Python
distribution, which carries each city's IANA time zone alongside it:

    pip download --no-deps geonamescache==3.0.2
    unzip geonamescache-3.0.2-py3-none-any.whl 'geonamescache/data/cities15000.json'
    python3 tools/build_cities.py geonamescache/data/cities15000.json

Output shape, parsed by CityDirectory.kt:

    Europe/Paris                 <- zone table, one per line
    ...
                                 <- blank line
    Paris|48.853|2.349|FR|2138|0|Lutetia;Parigi
    name|lat|lon|country|population in thousands|zone index|alternate names

Alternate names are kept only for cities of 100,000 or more and only in plain
title-case ASCII, which is where the English exonyms a traveler types live
("Bruges", "Gothenburg", "Cologne"). Rows are ordered by population, largest
first, so the first match for an ambiguous name is the one most people mean.
"""

import json
import re
import sys
import unicodedata
from pathlib import Path

ALTERNATES_FROM_POPULATION = 100_000
TITLE_CASE = re.compile(r"^[A-Z][a-z]+(?:[ -][A-Za-z][a-z]+)*$")


def ascii_fold(text: str) -> str:
    return unicodedata.normalize("NFKD", text).encode("ascii", "ignore").decode()


def clean(text: str) -> str:
    return text.replace("|", " ").replace(";", " ").strip()


def main(source: str) -> None:
    cities = list(json.load(open(source, encoding="utf-8")).values())
    cities.sort(key=lambda c: (-c["population"], c["name"]))
    zones = sorted({c["timezone"] for c in cities})
    zone_index = {zone: index for index, zone in enumerate(zones)}

    lines = list(zones) + [""]
    for city in cities:
        name = clean(city["name"])
        alternates = []
        if city["population"] >= ALTERNATES_FROM_POPULATION:
            seen = {ascii_fold(name).lower()}
            for alternate in city["alternatenames"]:
                alternate = clean(alternate)
                key = alternate.lower()
                if len(alternate) <= 25 and TITLE_CASE.match(alternate) and key not in seen:
                    seen.add(key)
                    alternates.append(alternate)
        lines.append(
            "|".join(
                [
                    name,
                    f"{city['latitude']:.3f}",
                    f"{city['longitude']:.3f}",
                    city["countrycode"],
                    str(max(1, round(city["population"] / 1000))),
                    str(zone_index[city["timezone"]]),
                    ";".join(alternates),
                ]
            )
        )

    target = Path(__file__).resolve().parent.parent / "core/catalog/src/main/resources/com/waymark/data/catalog/cities.txt"
    target.write_text("\n".join(lines) + "\n", encoding="utf-8")
    print(f"{len(cities)} cities, {len(zones)} zones -> {target}")


if __name__ == "__main__":
    main(sys.argv[1])
