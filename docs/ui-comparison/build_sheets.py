"""Compose the side-by-side comparison sheets from google/ and ours/.

Usage: python3 docs/ui-comparison/build_sheets.py   (writes 01/02/03-*.png, needs Pillow)

Left of each pair is a Google Calculator v10 frame lifted from the video, right is the
reconstruction written by render_ours.py from this repository's resources. The panels are
scaled to a common height so the two sides can be read against each other.
"""
import os

from PIL import Image, ImageDraw, ImageFont

HERE = os.path.dirname(os.path.abspath(__file__))
GOOGLE = os.path.join(HERE, 'google')
OURS = os.path.join(HERE, 'ours')

FONT_REG = '/usr/share/fonts/truetype/dejavu/DejaVuSans.ttf'
FONT_BOLD = '/usr/share/fonts/truetype/dejavu/DejaVuSans-Bold.ttf'

PANEL_H = 1120          # phone height on the sheet
GAP = 26                # between panels
MARGIN = 22
HEADER_H = 96
CAPTION_H = 34

BG = '#FFFFFF'
INK = '#1F1B16'
MUTED = '#6B6157'
ACCENT = '#715B3E'


def font(size, bold=False):
    return ImageFont.truetype(FONT_BOLD if bold else FONT_REG, size)


def load(path):
    im = Image.open(path).convert('RGB')
    w = int(round(im.width * PANEL_H / im.height))
    return im.resize((w, PANEL_H), Image.LANCZOS)


def build(out_name, title, subtitle, pairs):
    panels = []
    for g, o, cap_l, cap_r in pairs:
        panels.append((load(os.path.join(GOOGLE, g)), cap_l))
        panels.append((load(os.path.join(OURS, o)), cap_r))

    width = MARGIN * 2 + sum(p.width for p, _ in panels) + GAP * (len(panels) - 1)
    height = HEADER_H + CAPTION_H + PANEL_H + MARGIN
    sheet = Image.new('RGB', (width, height), BG)
    d = ImageDraw.Draw(sheet)

    d.text((MARGIN, 18), title, font=font(24, True), fill=INK)
    d.text((MARGIN, 52), subtitle, font=font(14), fill=MUTED)

    x = MARGIN
    for i, (im, caption) in enumerate(panels):
        google = i % 2 == 0
        d.text((x, HEADER_H), caption, font=font(14, True),
               fill=MUTED if google else ACCENT)
        sheet.paste(im, (x, HEADER_H + CAPTION_H))
        if i % 2 == 1:
            d.rectangle([x - GAP // 2, HEADER_H, x - GAP // 2 + 1, height - MARGIN],
                        fill='#E3D8CB')
        x += im.width + GAP
    sheet.save(os.path.join(HERE, out_name))
    print('wrote', out_name, sheet.size)


def main():
    build('01-calculator.png',
          'Calculator — keypad, mode row and scientific pad',
          'Left of each pair: Google Calculator v10 (frame from the video). '
          'Right: GKUICalculator, rendered from res/ values.',
          [('g1-calculator.png', 'o1-calculator.png',
            'GOOGLE · keypad + mode strip', 'GKUI · keypad + mode strip'),
           ('g2-scientific.png', 'o2-scientific.png',
            'GOOGLE · scientific pad', 'GKUI · scientific pad (fx)')])

    build('02-unit-conversion.png',
          'Unit conversion — rows, categories and the unit picker',
          'The display shrinks in both apps; the mode strip and key block stay where they are.',
          [('g3-unit-conversion.png', 'o3-unit-conversion.png',
            'GOOGLE · Temperature conversion', 'GKUI · Temperature conversion'),
           ('g4-unit-picker.png', 'o4-unit-picker.png',
            'GOOGLE · unit list', 'GKUI · unit picker')])

    build('03-history-and-more.png',
          'History and the overflow surface',
          'Google keeps history on the calculator surface and in the overflow menu; GKUI has a '
          'dedicated history screen plus the tool overlay.',
          [('g5-history.png', 'o5-history.png',
            'GOOGLE · history', 'GKUI · history screen'),
           ('g5-more-menu.png', 'o6-more-menu.png',
            'GOOGLE · overflow menu', 'GKUI · tool overlay')])


if __name__ == '__main__':
    main()
