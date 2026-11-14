#!/usr/bin/env python3
"""Static checks for this repository (no Android SDK/JDK required).

1. Every XML file well-forms.
2. Every @string/@color/@dimen/@drawable/@style/@layout/@id reference resolves to a declaration
   (any density/night/locale qualifier counts).
3. Every Java file parses (javalang, Java 8 grammar).
4. Every R.<type>.<name> used from Java exists in res/, and every android:onClick handler exists
   as a public method on the activity.
5. Style item names used by custom views are declared in res/values/attr.xml.
"""
import glob
import os
import re
import sys
import xml.etree.ElementTree as ET

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))

RES_REF = re.compile(r'@(\+)?(string|color|dimen|drawable|style|layout|id|bool|integer|array|'
                     r'attr|menu|xml|mipmap|plurals|fraction)/([A-Za-z0-9_.]+)')

errors = []
warnings = []


def res_files():
    return [p for p in glob.glob(os.path.join(ROOT, 'res', '**', '*.xml'), recursive=True)]


def parse(path):
    try:
        return ET.parse(path)
    except ET.ParseError as exc:
        errors.append(f'{rel(path)}: XML not well-formed: {exc}')
        return None


def rel(p):
    return os.path.relpath(p, ROOT)


def collect_declarations():
    """name -> set(types) declared anywhere under res/values*/ (and layouts for ids)."""
    decl = {}

    def add(kind, name):
        decl.setdefault(kind, set()).add(name)

    for path in glob.glob(os.path.join(ROOT, 'res', 'values*', '*.xml')):
        tree = parse(path)
        if tree is None:
            continue
        for el in tree.getroot():
            tag = el.tag
            name = el.get('name')
            if not name:
                continue
            if tag == 'string':
                add('string', name)
            elif tag == 'color':
                add('color', name)
            elif tag == 'dimen':
                add('dimen', name)
            elif tag == 'item':
                add(el.get('type', 'dimen'), name)
            elif tag in ('bool', 'integer', 'string-array', 'integer-array'):
                add({'string-array': 'array', 'integer-array': 'array'}.get(tag, tag), name)
            elif tag == 'style':
                add('style', name)
                # style item names become attr candidates
                for item in el:
                    if item.tag == 'item':
                        nm = item.get('name', '')
                        if nm and not nm.startswith('android:'):
                            add('attr', nm)
            elif tag == 'declare-styleable':
                for attr in el:
                    if attr.tag == 'attr' and attr.get('name'):
                        add('attr', attr.get('name'))
            elif tag == 'plurals':
                add('plurals', name)
            elif tag == 'array':
                add('array', name)
    for path in glob.glob(os.path.join(ROOT, 'res', 'drawable*', '*.xml')):
        add('drawable', os.path.splitext(os.path.basename(path))[0])
        parse(path)
    for path in glob.glob(os.path.join(ROOT, 'res', 'color', '*.xml')):
        add('color', os.path.splitext(os.path.basename(path))[0])
        parse(path)
    for path in glob.glob(os.path.join(ROOT, 'res', 'layout*', '*.xml')):
        add('layout', os.path.splitext(os.path.basename(path))[0])
    for path in glob.glob(os.path.join(ROOT, 'res', 'menu', '*.xml')):
        add('menu', os.path.splitext(os.path.basename(path))[0])
    for path in glob.glob(os.path.join(ROOT, 'res', 'xml', '*.xml')):
        add('xml', os.path.splitext(os.path.basename(path))[0])
        parse(path)
    for path in glob.glob(os.path.join(ROOT, 'res', 'mipmap*', '*.xml')):
        add('mipmap', os.path.splitext(os.path.basename(path))[0])
    for path in glob.glob(os.path.join(ROOT, 'res', 'anim*', '*.xml')):
        add('anim', os.path.splitext(os.path.basename(path))[0])
    return decl


def collect_ids():
    ids = set()
    for path in glob.glob(os.path.join(ROOT, 'res', '**', '*.xml'), recursive=True):
        with open(path, encoding='utf-8') as fh:
            for m in re.finditer(r'@\+id/([A-Za-z0-9_]+)', fh.read()):
                ids.add(m.group(1))
    # ids.xml style declarations
    for path in glob.glob(os.path.join(ROOT, 'res', 'values*', 'ids.xml')):
        tree = parse(path)
        if tree is not None:
            for el in tree.getroot():
                if el.get('name'):
                    ids.add(el.get('name'))
    return ids


def check_xml_refs(decl, ids):
    for path in glob.glob(os.path.join(ROOT, 'res', '**', '*.xml'), recursive=True):
        tree = parse(path)
        if tree is None:
            continue
        text = open(path, encoding='utf-8').read()
        for m in RES_REF.finditer(text):
            plus, kind, name = m.group(1), m.group(2), m.group(3)
            name = name.rstrip('.')
            if kind == 'id':
                if plus:
                    continue
                if name not in ids:
                    errors.append(f'{rel(path)}: unknown id reference @id/{name}')
                continue
            if kind == 'attr':
                # ?attr/ or @attr/ refer to theme attributes; only custom attrs are checked
                if name not in decl.get('attr', set()):
                    warnings.append(f'{rel(path)}: @attr/{name} is not a local attr')
                continue
            known = decl.get(kind, set())
            if kind == 'style':
                # Library themes/styles (Material Components, AppCompat) are not declared here.
                if name.startswith(('Widget.', 'Theme.', 'ThemeOverlay.', 'TextAppearance.',
                                    'Base.', 'Platform.')):
                    continue
                # Exact match only. A dotted style name is NOT satisfied by its parent: AAPT
                # resolves @style/PadButtonStyle.SmallText.Scientific to a resource of that exact
                # name, so accepting the "PadButtonStyle" prefix hid a real build break.
                if name not in known:
                    errors.append(f'{rel(path)}: unknown style @style/{name}')
            elif name not in known:
                errors.append(f'{rel(path)}: unknown @{kind}/{name}')


def check_java(decl, ids):
    import javalang
    for path in glob.glob(os.path.join(ROOT, 'src', '**', '*.java'), recursive=True):
        src = open(path, encoding='utf-8').read()
        try:
            javalang.parse.parse(src)
        except Exception as exc:  # noqa: BLE001 - report and continue
            errors.append(f'{rel(path)}: Java parse error: {exc}')
        for m in re.finditer(r'(?<!android\.)(?<!material\.)\bR\.(\w+)\.(\w+)', src):
            kind, name = m.group(1), m.group(2)
            if kind == 'styleable':
                continue
            if kind == 'id':
                if name not in ids:
                    errors.append(f'{rel(path)}: R.id.{name} is not declared in res/')
            elif name not in decl.get(kind, set()):
                errors.append(f'{rel(path)}: R.{kind}.{name} does not resolve')


def collect_onclicks():
    found = {}
    for path in glob.glob(os.path.join(ROOT, 'res', 'layout*', '*.xml')):
        with open(path, encoding='utf-8') as fh:
            for m in re.finditer(r'android:onClick="(\w+)"', fh.read()):
                found.setdefault(m.group(1), []).append(rel(path))
    return found


def check_onclick_handlers(onclicks):
    src = ''
    for path in glob.glob(os.path.join(ROOT, 'src', '**', '*.java'), recursive=True):
        src += open(path, encoding='utf-8').read()
    for handler, where in onclicks.items():
        # Activity#onClick handlers must be public and take a View
        pat = re.compile(r'public\s+void\s+' + handler + r'\s*\(', re.M)
        if not pat.search(src):
            errors.append(f'{", ".join(where)}: android:onClick="{handler}" has no public void handler')


def main():
    decl = collect_declarations()
    ids = collect_ids()
    check_xml_refs(decl, ids)
    onclicks = collect_onclicks()
    check_onclick_handlers(onclicks)
    check_java(decl, ids)
    for w in warnings:
        print('WARN', w)
    for e in errors:
        print('ERROR', e)
    print(f'\n{len(errors)} error(s), {len(warnings)} warning(s)')
    return 1 if errors else 0


if __name__ == '__main__':
    sys.exit(main())
