#!/usr/bin/env python3
"""One-off: turns the Kotlin catalog's hand-written tables into TypeScript.

Run from the repo root while the Kotlin modules still exist:
  python3 scripts/port-catalog-tables.py
It regenerates src/catalog/*-data.ts, so a correction made to the Kotlin
tables before the switch-over can be carried across. Deleted in phase 6.
"""
import json, re, pathlib

KT = pathlib.Path('core/catalog/src/main/java/com/waymark/data/catalog')
OUT = pathlib.Path('src/catalog')
HEADER = '// Generated from the Kotlin catalog by scripts/port-catalog-tables.py. Do not edit.\n'

def kotlin_strings(text):
    return [json.loads('"' + s + '"') for s in re.findall(r'"((?:[^"\\]|\\.)*)"', text)]

def airlines():
    src = (KT / 'Airlines.kt').read_text()
    body = src[src.index('mapOf(') : src.index('\n    )\n')]
    pairs = re.findall(r'"([^"]+)" to "((?:[^"\\]|\\.)*)"', body)
    rows = ',\n'.join(f'  [{json.dumps(c)}, {json.dumps(json.loads(chr(34) + n + chr(34)), ensure_ascii=False)}]' for c, n in pairs)
    (OUT / 'airlines-data.ts').write_text(
        HEADER + '\n/** [IATA designator, name], in the Kotlin table\'s order (a later duplicate wins). */\n'
        f'export const AIRLINE_ROWS: readonly (readonly [string, string])[] = [\n{rows}\n];\n')
    return len(pairs)

def airports():
    src = (KT / 'Airports.kt').read_text()
    rows = []
    for line in src.splitlines():
        m = re.match(r'\s*Airport\((.*)\),\s*$', line)
        if not m:
            continue
        py = m.group(1).replace('listOf(', '[').replace(')', ']')
        code, name, city, country, lat, lon, zone, terminals = eval('(' + py + ')')
        rows.append([code, name, city, country, lat, lon, zone, terminals])
    body = ',\n'.join('  ' + json.dumps(r, ensure_ascii=False) for r in rows)
    (OUT / 'airports-data.ts').write_text(
        HEADER + '\n/** The hand-checked core stations: [code, name, city, country, lat, lon, zone, terminals]. */\n'
        'export const CORE_AIRPORT_ROWS: readonly (readonly [string, string, string, string, number, number, string, readonly string[]])[] = [\n'
        f'{body}\n];\n')
    return len(rows)

def countries():
    src = (KT / 'Countries.kt').read_text()
    rows = []
    for line in src.splitlines():
        m = re.match(r'\s*c\((.*)\),\s*$', line)
        if not m:
            continue
        rows.append(list(eval('(' + m.group(1).replace('false', 'False').replace('true', 'True') + ')')))
    body = ',\n'.join('  ' + json.dumps(r, ensure_ascii=False) for r in rows)
    (OUT / 'countries-data.ts').write_text(
        HEADER + '\n/** [code, name, currency code, currency name, plugs, volts, hertz, emergency, drives on left]. */\n'
        'export const COUNTRY_ROWS: readonly (readonly [string, string, string, string, string, number, number, string, boolean])[] = [\n'
        f'{body}\n];\n')
    return len(rows)

def coastline():
    src = (KT / 'Coastline.kt').read_text()
    body = src[src.index('private val raw: List<String> = listOf(') :]
    body = body[body.index('(') + 1 : body.rindex(')\n}')]
    rings = eval('[' + body + ']')
    lines = ',\n'.join('  ' + json.dumps(r) for r in rings)
    (OUT / 'coastline-data.ts').write_text(
        HEADER + '\n/** Closed rings, largest landmass first, as "lon lat" pairs separated by commas. */\n'
        f'export const COASTLINE_RINGS: readonly string[] = [\n{lines}\n];\n')
    return len(rings), sum(len(r.split(',')) for r in rings)

print('airlines', airlines())
print('airports', airports())
print('countries', countries())
print('coastline', coastline())
