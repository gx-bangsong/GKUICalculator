"""Re-render the GKUICalculator panels of this comparison sheet.

Usage: python3 docs/ui-comparison/render_ours.py   (writes ours/o*.png, needs Pillow)

Every colour and dimension below is read from this repository's resources, so the panels track
the actual layout contract. Rendering notes:

* The pads are one fixed-height block (@id/pad_stack, 0.47 H) whose rows are weighted 3:5,
  exactly as activity_calculator.xml lays them out, so the 123/fx/sway strip and the display do
  not move when the scientific pad opens -- Google keeps a constant 409 dp key block.
* Key labels follow HapticButton.onSizeChanged(): 0.21 x key width, clamped by 0.42 x key height
  and by key_label_max_textsize (26sp); scientific keys carry their own 0.165 scale.
* Scientific keys use PadButtonStyle.Scientific: radius 10dp, 2dp gutters.

This is a reconstruction from layout source, not a device screenshot.
"""
import os
import re

from PIL import Image, ImageDraw, ImageFont

HERE = os.path.dirname(os.path.abspath(__file__))
REPO = os.path.dirname(os.path.dirname(HERE))


# ---------------------------------------------------------------- resource readers
def _read(*parts):
    with open(os.path.join(REPO, *parts), encoding='utf-8') as fh:
        return fh.read()


def color(name):
    """Resolve a colour from values/color.xml or values-night/color.xml."""
    for folder in ('values', 'values-night'):
        try:
            text = _read('res', folder, 'color.xml')
        except FileNotFoundError:
            continue
        m = re.search(r'<color name="%s">#([0-9A-Fa-f]{6}(?:[0-9A-Fa-f]{2})?)</color>'
                      % re.escape(name), text)
        if m:
            value = m.group(1)
            # Android colours are #RGB, #ARGB, #RRGGBB or #AARRGGBB; drop the alpha here.
            if len(value) == 8:
                value = value[2:]
            elif len(value) == 4:
                value = value[1:]
            return '#' + value
    raise KeyError(name)


def dimen(name):
    text = _read('res', 'values', 'dimens.xml')
    m = re.search(r'<dimen name="%s">([0-9.]+)dp</dimen>' % re.escape(name), text)
    if m:
        return float(m.group(1))
    m = re.search(r'<dimen name="%s">([0-9.]+)sp</dimen>' % re.escape(name), text)
    if m:
        return float(m.group(1))
    raise KeyError(name)


def item(name):
    text = _read('res', 'values', 'dimens.xml')
    m = re.search(r'<item name="%s"[^>]*>([0-9.]+)</item>' % re.escape(name), text)
    if m:
        return float(m.group(1))
    raise KeyError(name)


def ratio(name):
    text = _read('res', 'values', 'ratios.xml')
    m = re.search(r'name="%s">([0-9.]+)</item>' % re.escape(name), text)
    if m:
        return float(m.group(1))
    raise KeyError(name)


def string(name):
    for folder in ('values', 'values-zh-rCN'):
        try:
            text = _read('res', folder, 'strings.xml')
        except FileNotFoundError:
            continue
        m = re.search(r'<string name="%s"[^>]*>(.*?)</string>' % re.escape(name), text)
        if m:
            return m.group(1)
    raise KeyError(name)


# ---------------------------------------------------------------- palette
BG = color('display_background_color')
HIST_BG = color('history_background_color')
INK = color('calculator_key_text')
INK_DISABLED = color('calculator_key_text_disabled')
RESULT_INK = color('display_result_text_color')
KEY_NUM = color('calculator_key_number')
KEY_ACTION = color('calculator_key_action')
KEY_OP = color('calculator_key_operator')
KEY_CLEAR = color('calculator_key_clear')
KEY_EQ = color('calculator_key_equals')
ON_ACCENT = color('calculator_key_text_on_accent')
CHIP_BG = color('calculator_chip_background')
CHIP_INK = color('calculator_chip_text')
CHIP_SEL_BG = color('calculator_chip_selected_background')
CHIP_SEL_INK = color('calculator_chip_selected_text')
SEL_BG = color('calculator_unit_selector_background')
SEL_INK = color('calculator_unit_selector_text')
DIVIDER = color('calculator_display_divider')
PANEL_BG = color('tool_panel_background_color')

# ---------------------------------------------------------------- geometry
SEG_H = dimen('mode_segment_height')
SEG_PAD = dimen('mode_segment_padding')
SEG_MIN_W = dimen('mode_segment_min_width')
KEY_SCALE = item('dimen_key_label_scale')
KEY_MAX = dimen('key_label_max_textsize')
SCI_RADIUS = dimen('scientific_key_corner_radius')
SCI_MARGIN = dimen('scientific_key_horizontal_margin')
SCI_MARGIN_V = dimen('scientific_key_vertical_margin')
SCI_LABEL_MAX = 14.0            # PadButtonStyle.SmallCaps / SmallText cap
PAD_EDGE = dimen('pads_horizontal_edge_padding')
KEY_MARGIN_H = dimen('button_horizontal_margin')
KEY_MARGIN_V = dimen('button_vertical_margin')
KEY_HEIGHT_CLAMP = 0.42
SCI_SCALE = item('dimen_key_label_scale_scientific')

R_DISPLAY = ratio('layout_display_ratio')
R_TOOLBAR = ratio('layout_tool_bar_ratio')
R_PADS = ratio('layout_pads_ratio')
# The block ends above the gesture bar, as in the reference screenshot (34 dp of dead space).
PAD_BOTTOM_INSET = 34.0


def integer(name):
    text = _read('res', 'values', 'integers.xml')
    m = re.search(r'<integer name="%s">([0-9]+)</integer>' % re.escape(name), text)
    if m:
        return int(m.group(1))
    raise KeyError(name)


ICON_SEG_W = dimen('mode_segment_icon_size')
# Icon-only segments (programmer + the life-calculation tools). The renderer draws a stand-in
# glyph; the app uses res/drawable/ic_tool_*.xml vectors.
TOOL_ICONS = [('programmer', '</>'), ('bmi', '\u263a'), ('tax', '%'), ('mortgage', '\u2302'),
              ('date', '\u25a6'), ('relationship', '\u2632')]

SCI_WEIGHT = integer('advanced_pad_weight')
DIGIT_WEIGHT = integer('input_pad_weight')

FONT_REG = '/usr/share/fonts/truetype/dejavu/DejaVuSans.ttf'
FONT_BOLD = '/usr/share/fonts/truetype/dejavu/DejaVuSans-Bold.ttf'

W_DP, H_DP = 400.0, 860.0
PX = 1.5
W, H = int(W_DP * PX), int(H_DP * PX)


def px(v):
    return int(round(v * PX))


def font(size_dp, bold=False):
    return ImageFont.truetype(FONT_BOLD if bold else FONT_REG, max(6, int(size_dp * PX)))


def rrect(d, box, r, fill):
    d.rounded_rectangle([px(box[0]), px(box[1]), px(box[2]), px(box[3])], radius=px(r), fill=fill)


def txt(d, xy, s, size, fill=INK, anchor='la', bold=False):
    d.text((px(xy[0]), px(xy[1])), s, font=font(size, bold), fill=fill, anchor=anchor)


def panel(hist=False):
    im = Image.new('RGB', (W, H), HIST_BG if hist else BG)
    return im, ImageDraw.Draw(im)


def labelled(d, box, label, size, fill_bg, fill_ink=None):
    """A key with its label centred. `size` is already the resolved label size."""
    rrect(d, box, 32, fill_bg)
    if len(label) > 2:
        size = min(size, KEY_MAX * 0.8)
    txt(d, ((box[0] + box[2]) / 2, (box[1] + box[3]) / 2), label, size,
        INK if fill_ink is None else fill_ink, anchor='mm')


# ---------------------------------------------------------------- chrome
def status_bar(d, clock='12:35'):
    txt(d, (14, 7), clock, 11, INK, bold=True)
    for i in range(3):
        d.rectangle([px(342 + i * 5), px(10), px(346 + i * 5), px(17)], fill=INK)


def overflow(d, x=386, y=30):
    for dy in (-5, 0, 5):
        r = px(1.6)
        d.ellipse([px(x) - r, px(y + dy) - r, px(x) + r, px(y + dy) + r], fill=INK)


def history_icon(d):
    cx, cy, r = 20, 30, 8.5
    d.arc([px(cx - r), px(cy - r), px(cx + r), px(cy + r)], 35, 315, fill=INK, width=px(1.5))
    d.polygon([(px(cx - 10), px(cy - 7)), (px(cx - 4), px(cy - 10)), (px(cx - 5), px(cy - 1))],
              fill=INK)


def toolbar(d):
    """res/layout/toolbar.xml: history nav icon + overflow; the mode label is tool-specific."""
    history_icon(d)
    overflow(d)


def mode_strip(d, y, scientific=False, active=None):
    """res/layout/tool_bar.xml: 123 / fx / (two-way arrow) track, then the tool icon track."""
    band = R_TOOLBAR * H_DP
    strip_top = y + (band - SEG_H) / 2
    radius = SEG_H / 2
    labels = [string('mode_segment_basic'), string('mode_segment_scientific'),
              string('mode_segment_convert')]
    widths = [max(SEG_MIN_W, 2 * SEG_PAD + 7.4 * len(t)) for t in labels]
    group_w = sum(widths)
    rrect(d, (4, strip_top, 4 + group_w, strip_top + SEG_H), radius, CHIP_BG)
    x = 4
    for i, (label, w) in enumerate(zip(labels, widths)):
        selected = (i == 1 and scientific) or (i == 2 and active in ('unit', 'currency'))
        if selected:
            rrect(d, (x, strip_top, x + w, strip_top + SEG_H), radius, CHIP_SEL_BG)
        txt(d, (x + w / 2, strip_top + SEG_H / 2), label, 15,
            CHIP_SEL_INK if selected else CHIP_INK, anchor='mm')
        x += w

    # Tool icons: programmer, then the life-calculation tools, in their own scrolling track.
    x += 6
    tool_slot = ICON_SEG_W
    icons = TOOL_ICONS
    visible = int((W_DP - x - 44 - 4) // tool_slot)
    shown = icons[:max(visible, 1)]
    track_w = len(shown) * tool_slot
    rrect(d, (x, strip_top, x + track_w, strip_top + SEG_H), radius, CHIP_BG)
    for name, glyph in shown:
        cx = x + tool_slot / 2
        if name == active:
            rrect(d, (x, strip_top, x + tool_slot, strip_top + SEG_H), radius, CHIP_SEL_BG)
        txt(d, (cx, strip_top + SEG_H / 2), glyph, 15,
            CHIP_SEL_INK if name == active else CHIP_INK, anchor='mm')
        x += tool_slot
    overflow(d)


# ---------------------------------------------------------------- pads
MAIN_ROWS = [["AC", "( )", "%", "÷"], ["7", "8", "9", "×"], ["4", "5", "6", "−"],
             ["1", "2", "3", "+"], ["0", ".", "⌫", "="]]
SCI_ROWS = [["√", "π", "^", "!"], ["Rad", "sin", "cos", "tan"], ["Inv", "e", "ln", "log"]]


def main_style(label, row, col):
    if label == "=":
        return KEY_EQ, ON_ACCENT
    if label == "AC":
        return KEY_CLEAR, INK
    if col == 3:
        return KEY_OP, INK
    if row == 0:
        return KEY_ACTION, INK
    return KEY_NUM, INK


def key_label_size(width, height, scale):
    """HapticButton.onSizeChanged(): width x scale, clamped by height and the sp cap."""
    return min(width * scale, height * KEY_HEIGHT_CLAMP, KEY_MAX)


def draw_input_pad(d, y0, y1):
    row_h = (y1 - y0) / len(MAIN_ROWS)
    cell = (W_DP - 2 * PAD_EDGE) / 4.0
    kw = cell - 2 * KEY_MARGIN_H
    for r, row in enumerate(MAIN_ROWS):
        for c, label in enumerate(row):
            x = PAD_EDGE + c * cell + KEY_MARGIN_H
            y = y0 + r * row_h
            fill, ink = main_style(label, r, c)
            size = key_label_size(kw, row_h - 2 * KEY_MARGIN_V, KEY_SCALE)
            labelled(d, (x, y + KEY_MARGIN_V, x + kw, y + row_h - KEY_MARGIN_V),
                     label, size, fill, ink)


def draw_advanced_pad(d, y0, y1):
    """Flat, low pills in the same 4-column grid, as in Google Calculator v10."""
    row_h = (y1 - y0) / len(SCI_ROWS)
    cell = (W_DP - 2 * PAD_EDGE) / 4.0
    kw = cell - 2 * MAIN_KEY_MARGIN_H
    for r, row in enumerate(SCI_ROWS):
        for c, label in enumerate(row):
            x = PAD_EDGE + c * cell + MAIN_KEY_MARGIN_H + SCI_MARGIN - KEY_MARGIN_H
            y = y0 + r * row_h + SCI_MARGIN_V
            pill_h = row_h - 2 * SCI_MARGIN_V
            rrect(d, (x, y, x + kw, y + pill_h), SCI_RADIUS, KEY_ACTION)
            txt(d, (x + kw / 2, y + pill_h / 2), label,
                key_label_size(kw, pill_h, SCI_SCALE), INK, anchor='mm')


MAIN_KEY_MARGIN_H = KEY_MARGIN_H


def display_height(scientific):
    """The pad block is constant; only the rows inside it are re-divided."""
    return H_DP - R_TOOLBAR * H_DP - R_PADS * H_DP


def draw_display_values(d, dh):
    txt(d, (W_DP - 16, dh - 50 - 40), "9,764+65", 30, INK, anchor='rs')
    txt(d, (W_DP - 16, dh - 50), "9,829", 40, RESULT_INK, anchor='rs')
    rrect(d, (W_DP / 2 - 12, dh - 10, W_DP / 2 + 12, dh - 6), 2, DIVIDER)


# ---------------------------------------------------------------- screens
def screen_calculator(scientific=False):
    im, d = panel()
    status_bar(d)
    toolbar(d)
    dh = display_height(scientific)
    draw_display_values(d, dh)
    mode_strip(d, dh, scientific=scientific, active=None)
    block_top = dh + R_TOOLBAR * H_DP
    block_h = R_PADS * H_DP
    total = SCI_WEIGHT + DIGIT_WEIGHT
    sci_h = block_h * SCI_WEIGHT / total if scientific else 0.0
    if scientific:
        draw_advanced_pad(d, block_top, block_top + sci_h)
    draw_input_pad(d, block_top + sci_h, block_top + block_h)
    return im


def screen_units(scientific=False, picker=False, currency=False):
    im, d = panel()
    status_bar(d)
    toolbar(d)
    dh = display_height(scientific)
    y = 56
    x = 20
    if not currency:
        for i, c in enumerate(["Length", "Area", "Volume", "Weight", "Temperature", "Time"]):
            cw = 20 + 6.6 * len(c)
            if x + cw > 382:
                break
            on = i == 4
            rrect(d, (x, y, x + cw, y + 32), 18, CHIP_SEL_BG if on else CHIP_BG)
            txt(d, (x + cw / 2, y + 16), c, 11.5, CHIP_SEL_INK if on else CHIP_INK, anchor='mm')
            x += cw + 6
        y += 42
    pw = 22 + 7.4 * 2
    rrect(d, (W_DP - 20 - pw, y, W_DP - 20, y + 30), 15, SEL_BG)
    txt(d, (W_DP - 20 - pw / 2, y + 15), '€ EUR' if currency else '°C', 13.5, SEL_INK, anchor='mm')
    txt(d, (W_DP - 20, y + 60), "38", 34, INK, anchor='rs')
    y += 74
    d.line([px(58), px(y + 20), px(W_DP - 20), px(y + 20)], fill=DIVIDER, width=px(1))
    d.line([px(26), px(y + 12), px(34), px(y + 20)], fill=CHIP_INK, width=px(1.5))
    d.line([px(34), px(y + 20), px(26), px(y + 28)], fill=CHIP_INK, width=px(1.5))
    d.line([px(30), px(y + 8), px(30), px(y + 32)], fill=CHIP_INK, width=px(1.5))
    y += 40
    rrect(d, (W_DP - 20 - pw, y, W_DP - 20, y + 30), 15, SEL_BG)
    txt(d, (W_DP - 20 - pw / 2, y + 15), '¥ JPY' if currency else '°F', 13.5, SEL_INK, anchor='mm')
    txt(d, (W_DP - 20, y + 60), "4,970" if currency else "100.4", 34, RESULT_INK, anchor='rs')
    mode_strip(d, dh, scientific=scientific, active='currency' if currency else 'unit')
    block_top = dh + R_TOOLBAR * H_DP
    block_h = R_PADS * H_DP
    total = SCI_WEIGHT + DIGIT_WEIGHT
    sci_h = block_h * SCI_WEIGHT / total if scientific else 0.0
    if scientific:
        draw_advanced_pad(d, block_top, block_top + sci_h)
    draw_input_pad(d, block_top + sci_h, block_top + block_h)
    if picker:
        ov = Image.new('RGBA', (W, H), (0, 0, 0, 0))
        ImageDraw.Draw(ov).rectangle([0, 0, W, H], fill=(0, 0, 0, 55))
        im = Image.alpha_composite(im.convert('RGBA'), ov).convert('RGB')
        d = ImageDraw.Draw(im)
        rows = ["°C", "°F", "K", "Add custom unit"]
        ph = 8 + 44 * len(rows) + 8
        x0, y0 = 176, 96
        rrect(d, (x0 + 2, y0 + 3, x0 + 212, y0 + ph + 3), 8, DIVIDER)
        rrect(d, (x0, y0, x0 + 212, y0 + ph), 8, BG)
        yy = y0 + 8
        for i, r in enumerate(rows):
            if i == 3:
                d.line([px(x0 + 8), px(yy), px(x0 + 204), px(yy)], fill=DIVIDER, width=px(1))
            txt(d, (x0 + 14, yy + 22), r, 13, INK, anchor='lm')
            yy += 44
        rrect(d, (W_DP - 20 - pw, 120, W_DP - 20, 150), 15, CHIP_SEL_BG)
        txt(d, (W_DP - 20 - pw / 2, 135), '°C', 13.5, CHIP_SEL_INK, anchor='mm')
    return im


def screen_history():
    im, d = panel(hist=True)
    status_bar(d)
    d.line([px(12), px(36), px(24), px(26)], fill=INK, width=px(1.8))
    d.line([px(12), px(36), px(24), px(46)], fill=INK, width=px(1.8))
    txt(d, (32, 30), "History", 18, INK, anchor='lm')
    txt(d, (W_DP - 16, 30), "Clear", 13, INK, anchor='rm')
    yy = 74
    for date, items in [("4 days ago", [("9,764+65", "9,829"), ("12,345 × 3", "37,035")]),
                        ("12 Nov 2026", [("38 °C → °F", "100.4")])]:
        txt(d, (16, yy), date, 11.5, INK_DISABLED)
        yy += 22
        for f_, r_ in items:
            txt(d, (W_DP - 16, yy), f_, 24, INK, anchor='rs')
            txt(d, (W_DP - 16, yy + 34), r_, 24, RESULT_INK, anchor='rs')
            yy += 78
            d.line([px(16), px(yy - 18), px(W_DP - 16), px(yy - 18)], fill=DIVIDER, width=px(1))
    return im


def screen_overlay():
    im, d = panel()
    status_bar(d)
    toolbar(d)
    dh = display_height(False)
    draw_display_values(d, dh)
    mode_strip(d, dh, active=None)
    ov = Image.new('RGBA', (W, H), (0, 0, 0, 0))
    ImageDraw.Draw(ov).rectangle([0, px(0.40 * H_DP), W, H], fill=(0, 0, 0, 70))
    im = Image.alpha_composite(im.convert('RGBA'), ov).convert('RGB')
    d = ImageDraw.Draw(im)
    y0 = 0.40 * H_DP
    rrect(d, (0, y0, W_DP, H_DP), 20, PANEL_BG)
    txt(d, (20, y0 + 18), "More tools", 16, INK, bold=True)
    tools = ["Units", "Currency", "Mortgage", "Income tax", "BMI", "Date", "Programmer",
             "Relationship"]
    gw = (W_DP - 16) / 4
    for i, t in enumerate(tools):
        cx = 8 + (i % 4) * gw + gw / 2
        cy = y0 + 96 + (i // 4) * 96
        txt(d, (cx, cy), t, 10.5, INK, anchor='mm')
    return im


if __name__ == "__main__":
    out_dir = os.path.join(HERE, 'ours')
    os.makedirs(out_dir, exist_ok=True)
    names = {
        'calc': 'o1-calculator',
        'calc_sci': 'o2-scientific',
        'units': 'o3-unit-conversion',
        'units_picker': 'o4-unit-picker',
        'history': 'o5-history',
        'overlay': 'o6-more-menu',
        'currency': 'o7-currency',
    }
    out = {
        'calc': screen_calculator(False),
        'calc_sci': screen_calculator(True),
        'units': screen_units(False),
        'units_picker': screen_units(True, picker=True),
        'history': screen_history(),
        'overlay': screen_overlay(),
        'currency': screen_units(False, currency=True),
    }
    for k, v in out.items():
        v.save(os.path.join(out_dir, names[k] + '.png'))
    print('wrote', out_dir)
