#!/usr/bin/env python3
"""Put back the accented letters missing from data/catalog/airports.txt.

The table Waymark started from arrived with encoding damage: where a name had
an accented letter there was a replacement character, and those runs were
stripped when the file was built, so "Montréal" shipped as "Montral". This
restores them from OurAirports (public domain, https://ourairports.com/data/):

    curl -O https://raw.githubusercontent.com/davidmegginson/ourairports-data/main/airports.csv
    python3 tools/repair_airport_names.py airports.csv

A name or city is replaced only when OurAirports has the same station (same
IATA code, within half a degree) and its value, with the non-ASCII letters
taken out, is exactly what the file holds. That is the signature of the
damage. When OurAirports has reworded a name since, the same test is made
word by word, and last, a word still damaged is matched against every
station's spelling in the same country when only one fits. Nothing else in the file changes: no renames, no new rows, and the
codes, coordinates and time zones stay as they are.
"""

import csv
import re
import sys
import unicodedata
from pathlib import Path

TABLE = Path(__file__).resolve().parent.parent / "data" / "catalog" / "airports.txt"
NON_ASCII = re.compile(r"[^\x00-\x7f]+")
WORD = re.compile(r"[^\s/()\-–,.]+")


def damaged_form(text: str) -> str:
    return NON_ASCII.sub("", text)


def lost_letters_index(path: str) -> dict[tuple[str, str], str]:
    """Every accented word in OurAirports, keyed by country and damaged form.

    Kept only where the damaged form is at least four letters, points at one
    spelling in that country, and is not a word OurAirports uses plainly:
    "Chiinu" in Moldova can only be "Chişinău", but "Kon" could be anything,
    and a Mexican "Paredo" must not borrow a Brazilian "Paredão".
    """
    seen: dict[tuple[str, str], set[str]] = {}
    plain: set[str] = set()
    with open(path, newline="", encoding="utf-8") as f:
        for row in csv.DictReader(f):
            for field in (row["name"], row["municipality"] or ""):
                for word in WORD.findall(unicodedata.normalize("NFC", field)):
                    if NON_ASCII.search(word):
                        seen.setdefault((row["iso_country"], damaged_form(word)), set()).add(word)
                    else:
                        plain.add(word)
    return {
        key: next(iter(words))
        for key, words in seen.items()
        if len(key[1]) >= 4 and len(words) == 1 and key[1] not in plain
    }


def load_reference(path: str) -> dict[str, list[dict[str, str]]]:
    by_code: dict[str, list[dict[str, str]]] = {}
    with open(path, newline="", encoding="utf-8") as f:
        for row in csv.DictReader(f):
            code = (row.get("iata_code") or "").strip().upper()
            if len(code) == 3:
                by_code.setdefault(code, []).append(row)
    return by_code


def restore(ours: str, theirs: str, also: str = "") -> str | None:
    """Ours with its lost letters put back from theirs, or None if nothing to do.

    The whole value first: ours is exactly theirs with the non-ASCII letters
    lost. Failing that, word by word, for names OurAirports has since reworded
    ("Mlaga Airport" against "Málaga-Costa del Sol Airport"): a word of ours
    is replaced only by a word of theirs whose damaged form it exactly is.
    `also` lends its words too: a city spelled out in the station's name.
    """
    theirs = unicodedata.normalize("NFC", theirs.strip())
    also = unicodedata.normalize("NFC", also.strip())
    if theirs != ours and NON_ASCII.search(theirs) and damaged_form(theirs) == ours:
        return theirs
    words = WORD.findall(theirs) + WORD.findall(also)
    accented = {damaged_form(w): w for w in words if NON_ASCII.search(w)}
    accented = {k: w for k, w in accented.items() if len(k) >= 2}
    repaired = WORD.sub(lambda m: accented.get(m.group(0), m.group(0)), ours)
    return repaired if repaired != ours else None


def main() -> None:
    if len(sys.argv) != 2:
        sys.exit(__doc__)
    reference = load_reference(sys.argv[1])
    lost = lost_letters_index(sys.argv[1])
    lines = TABLE.read_text(encoding="utf-8").split("\n")
    body = lines.index("") + 1  # the zone table ends at the first blank line
    fixed = 0
    for i in range(body, len(lines)):
        parts = lines[i].split("|")
        if len(parts) != 7:
            continue
        code, name, city, country, lat, lon, _zone = parts
        near = [
            r
            for r in reference.get(code, [])
            if abs(float(r["latitude_deg"]) - float(lat)) < 0.5
            and abs(float(r["longitude_deg"]) - float(lon)) < 0.5
        ]
        new_name, new_city = name, city
        if len(near) == 1:
            their_name, their_city = near[0]["name"], near[0]["municipality"] or ""
            new_name = restore(name, their_name, their_city) or name
            new_city = restore(city, their_city, their_name) or city
        # Then any word still damaged, against every station's spelling.
        def heal(m: re.Match[str]) -> str:
            return lost.get((country, m.group(0)), m.group(0))

        new_name = WORD.sub(heal, new_name)
        new_city = WORD.sub(heal, new_city)
        if (new_name, new_city) != (name, city):
            parts[1], parts[2] = new_name, new_city
            lines[i] = "|".join(parts)
            fixed += 1
    TABLE.write_text("\n".join(lines), encoding="utf-8")
    print(f"restored {fixed} stations")


if __name__ == "__main__":
    main()
