#!/usr/bin/env python3
"""Fails the build when a text shown in the app has no translation in I18n.kt (all 10 languages).
Texts go through T(), OptionRow(), Button(), Popup(), tr(), trf() and similar; a text with a variable inside
must use trf("... %s ...", value) so it can be translated."""
import os, re, sys
SRC = os.path.join(os.path.dirname(__file__), '..', 'app', 'src', 'main', 'java', 'com', 'pretorian', 'tv')
CALLS = r'\b(T|OptionRow|Button|Popup|Message|Loading|Chip|ToggleRow|Label|Page|TextDialog|PinDialog|tr|trf|Field|HomeTile|RailItem|Segmented|LangPicker|LangOrderPicker|FileBrowser|FilePicker|Badge)\('
LETTER = re.compile(r'[A-Za-zČĆŠĐŽčćšđž]')
keep = set(l.strip() for l in open(os.path.join(os.path.dirname(__file__), 'i18n_keep.txt'), encoding='utf-8') if l.strip() and not l.startswith('#'))
i18n = open(os.path.join(SRC, 'I18n.kt'), encoding='utf-8').read()
table = i18n.split('private val TABLE = """')[1].split('""".trimIndent()')[0]
known = {}
bad_rows = []
for line in table.strip().split('\n'):
    parts = line.split('|')
    if len(parts) != 10: bad_rows.append(line[:60]); continue
    known[parts[0]] = parts
lit = re.compile(r'"((?:[^"\\]|\\.)*)"')

def args(src, start):
    depth, i = 0, start
    while i < len(src):
        c = src[i]
        if c == '"':
            j = i + 1
            while j < len(src) and src[j] != '"':
                j += 2 if src[j] == '\\' else 1
            i = j
        elif c == '(':
            depth += 1
        elif c == ')':
            depth -= 1
            if depth == 0: return src[start:i + 1]
        i += 1
    return ''

missing, templated = {}, {}
for fn in sorted(os.listdir(SRC)):
    if not fn.endswith('.kt') or fn == 'I18n.kt': continue
    src = open(os.path.join(SRC, fn), encoding='utf-8').read()
    texts = []
    for m in re.finditer(CALLS, src):
        a = args(src, m.end() - 1)
        texts += lit.findall(a)
    for m in re.finditer(r'\b(sub|text|label|title)\s*=\s*"((?:[^"\\]|\\.)*)"', src): texts.append(m.group(2))
    for m in re.finditer(r'\bto\s+"((?:[^"\\]|\\.)*)"', src): texts.append(m.group(1))
    for t in texts:
        plain = re.sub(r'\$\{[^}]*\}|\$[A-Za-z_]\w*', '', t).replace('\\"', '"')
        if not LETTER.search(plain): continue
        if t in keep or t.strip() in keep or plain.strip() in keep: continue
        if '$' in t and t not in known:
            templated.setdefault(t, fn); continue
        if t.replace('\\"', '"') not in known and t not in known:
            missing.setdefault(t, fn)
if bad_rows: print('Broken table rows:', *bad_rows, sep='\n  ')
if templated: print('Texts with a variable inside (use trf):', *[f'{k}  ({v})' for k, v in templated.items()], sep='\n  ')
if missing: print('Texts without translation:', *[f'{k}  ({v})' for k, v in missing.items()], sep='\n  ')
if bad_rows or missing or templated:
    print(f'\n{len(missing)} missing, {len(templated)} templated, {len(bad_rows)} broken rows'); sys.exit(1)
print(f'Translations OK: {len(known)} texts in 10 languages')
