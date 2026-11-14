"""Re-render the GKUICalculator panels of this comparison sheet.

Usage: python3 docs/ui-comparison/render_ours.py   (writes ours/o*.png, needs Pillow)

Render GKUICalculator screens strictly from res/ values, for comparison with a video of
Google Calculator v10.  Coordinates follow:
  res/values/ratios.xml (portrait): display 0.30, tool_bar 0.08, advanced_pad 0.17, input_pad 0.45
        (the display grows to 1-.08-.45 = 0.47 when the scientific pad is hidden)
  res/values/dimens.xml: pads edge padding 8dp, button margin 4dp, formula/result padding 16dp
  res/values/styles.xml: key corner radius 32dp, key text 24sp, formula 32..48sp, result 32sp
  res/values/color.xml: palette (constants below)
  res/layout/tool_unit_control.xml: pill -> value -> swap row -> pill -> value, no category chips
This is a reconstruction from layout source, not a device screenshot.
"""
from PIL import Image, ImageDraw, ImageFont

BG, HIST_BG = "#FFFBF4", "#FFF2E3"
INK, RESULT_INK = "#29251E", "#566642"
KEY_NUM, KEY_ACTION, KEY_OP = "#F0E8DF", "#FFE2C0", "#F8DEBF"
KEY_CLEAR, KEY_EQ, ON_ACCENT = "#EEE8DF", "#506443", "#FFFBF4"
CHIP_BG, CHIP_INK = "#F2EADD", "#5B4E3B"
CHIP_SEL_BG, CHIP_SEL_INK = "#796849", "#FFFBF4"
SEL_BG, SEL_INK = "#F1E8DC", "#4A4338"
DIVIDER, PANEL_BG, MUTED = "#E4DACB", "#FFF4E9", "#8A8175"

F = "/usr/share/fonts/truetype/dejavu/DejaVuSans.ttf"
FB = "/usr/share/fonts/truetype/dejavu/DejaVuSans-Bold.ttf"
W_DP, H_DP, DP = 400.0, 860.0, 1.5
W, H = int(W_DP * DP), int(H_DP * DP)

def px(v): return int(round(v * DP))
def font(s, bold=False): return ImageFont.truetype(FB if bold else F, max(6, int(s * DP)))
def rrect(d, box, r, fill):
    d.rounded_rectangle([px(box[0]), px(box[1]), px(box[2]), px(box[3])],
                        radius=px(r), fill=fill)
def txt(d, xy, s, size, fill=INK, anchor="la", bold=False):
    d.text((px(xy[0]), px(xy[1])), s, font=font(size, bold), fill=fill, anchor=anchor)
def panel(hist=False):
    im = Image.new("RGB", (W, H), HIST_BG if hist else BG)
    return im, ImageDraw.Draw(im)

def status_bar(d, clock="12:35"):
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
    d.polygon([(px(cx - 10), px(cy - 7)), (px(cx - 4), px(cy - 10)), (px(cx - 5), px(cy - 1))], fill=INK)

def toolbar(d, mode=None, uppercase=False):
    history_icon(d)
    if uppercase:
        rrect(d, (34, 18, 60, 42), 16, CHIP_BG)
        txt(d, (47, 30), "大", 13, CHIP_INK, anchor="mm")
    if mode:
        txt(d, (40, 30), mode, 11.5, CHIP_INK, anchor="lm", bold=True)
    overflow(d)

CHIPS = ["Calc", "Units", "Currency", "Mortgage", "Tax", "BMI", "Date", "Programmer"]

def tool_strip(d, y, sci, active=None):
    h = 0.08 * H_DP
    x = 4
    w = max(44, 20 + 6.6 * len(sci))
    on = sci == "Hide"
    rrect(d, (x, y + 2, x + w, y + h - 2), 16, CHIP_SEL_BG if on else CHIP_BG)
    txt(d, (x + w / 2, y + h / 2), sci, 12, CHIP_SEL_INK if on else CHIP_INK, anchor="mm")
    x += w + 4
    for c in CHIPS:
        cw = 18 + 6.9 * len(c)
        if x + cw > 362:
            txt(d, (x + 3, y + h / 2), "»", 12, CHIP_INK, anchor="mm")
            break
        sel = c == active
        rrect(d, (x, y + 2, x + cw, y + h - 2), 18, CHIP_SEL_BG if sel else CHIP_BG)
        txt(d, (x + cw / 2, y + h / 2), c, 12.5, CHIP_SEL_INK if sel else CHIP_INK, anchor="mm")
        x += cw + 4

def key_grid(d, y0, y1, rows, size, radius, style):
    """pads_horizontal_edge_padding 8dp + button_horizontal_margin 4dp; no vertical margin."""
    rh = (y1 - y0) / len(rows)
    cell = (W_DP - 16) / 4.0
    kw = cell - 8
    for r_i, row in enumerate(rows):
        for c_i, lab in enumerate(row):
            x = 8 + c_i * cell + 4
            y = y0 + r_i * rh
            fill, ink = style(lab, r_i, c_i)
            rrect(d, (x, y, x + kw, y + rh), radius, fill)
            txt(d, (x + kw / 2, y + rh / 2), lab, size if len(lab) <= 2 else size * 0.72, ink, anchor="mm")

SCI_ROWS = [["√", "π", "^", "!"], ["Rad", "sin", "cos", "tan"], ["Inv", "e", "ln", "log"]]
MAIN_ROWS = [["AC", "( )", "%", "÷"], ["7", "8", "9", "×"], ["4", "5", "6", "−"],
             ["1", "2", "3", "+"], ["0", ".", "⌫", "="]]

def sci_style(lab, r, c):
    return KEY_ACTION, INK

def main_style(lab, r, c):
    if lab == "=": return KEY_EQ, ON_ACCENT
    if lab == "AC": return KEY_CLEAR, INK
    if c == 3: return KEY_OP, INK
    if r == 0: return KEY_ACTION, INK
    return KEY_NUM, INK

def draw_pads(d, sci_visible):
    """Pads are anchored to the bottom: input_pad 0.45H, advanced_pad 0.17H above it."""
    input_h = 0.45 * H_DP
    adv_h = 0.17 * H_DP
    bottom = H_DP - 4
    key_grid(d, bottom - input_h, bottom, MAIN_ROWS, 24, 32, main_style)
    if sci_visible:
        key_grid(d, bottom - input_h - adv_h, bottom - input_h, SCI_ROWS, 16, 16, sci_style)

def display_height(sci_visible):
    return H_DP * (1 - 0.08 - 0.45 - (0.17 if sci_visible else 0))

# ------------------------------------------------------------------ screens
def screen_calculator(sci=False):
    im, d = panel()
    status_bar(d); toolbar(d)
    dh = display_height(sci)
    # display_two_line.xml: formula above result, result_padding_bottom 32dp
    txt(d, (W_DP - 16, dh - 50 - 44), "9,764+65", 30, INK, anchor="rs")
    txt(d, (W_DP - 16, dh - 50), "9,829", 40, RESULT_INK, anchor="rs")
    rrect(d, (W_DP / 2 - 12, dh - 10, W_DP / 2 + 12, dh - 6), 2, "#DCD2C4")
    tool_strip(d, dh, "Hide" if sci else "Sci", active="Calc")
    draw_pads(d, sci)
    return im

def screen_units(sci=False, picker=False):
    im, d = panel()
    status_bar(d); toolbar(d)
    # res/layout/tool_unit_control.xml : category ChipGroup first, then the from-row
    y = 56
    x = 20
    for i, c in enumerate(["Length", "Area", "Volume", "Weight", "Temperature", "Time"]):
        cw = 20 + 6.6 * len(c)
        if x + cw > 382:
            break
        on = i == 4
        rrect(d, (x, y, x + cw, y + 32), 18, CHIP_SEL_BG if on else CHIP_BG)
        txt(d, (x + cw / 2, y + 16), c, 11.5, CHIP_SEL_INK if on else CHIP_INK, anchor="mm")
        x += cw + 6
    y += 42
    pw = 22 + 7.4 * 2
    rrect(d, (W_DP - 20 - pw, y, W_DP - 20, y + 30), 15, SEL_BG)
    txt(d, (W_DP - 20 - pw / 2, y + 15), "°C", 13.5, SEL_INK, anchor="mm")
    txt(d, (W_DP - 20, y + 60), "38", 34, INK, anchor="rs")
    y += 74
    d.line([px(58), px(y + 20), px(W_DP - 20), px(y + 20)], fill=DIVIDER, width=px(1))
    d.line([px(26), px(y + 12), px(34), px(y + 20)], fill=CHIP_INK, width=px(1.5))
    d.line([px(34), px(y + 20), px(26), px(y + 28)], fill=CHIP_INK, width=px(1.5))
    d.line([px(30), px(y + 8), px(30), px(y + 32)], fill=CHIP_INK, width=px(1.5))
    y += 40
    rrect(d, (W_DP - 20 - pw, y, W_DP - 20, y + 30), 15, SEL_BG)
    txt(d, (W_DP - 20 - pw / 2, y + 15), "°F", 13.5, SEL_INK, anchor="mm")
    txt(d, (W_DP - 20, y + 60), "100.4", 34, RESULT_INK, anchor="rs")
    dh = display_height(sci)
    tool_strip(d, dh, "Hide" if sci else "Sci", active="Units")
    draw_pads(d, sci)
    if picker:
        ov = Image.new("RGBA", (W, H), (0, 0, 0, 0))
        ImageDraw.Draw(ov).rectangle([0, 0, W, H], fill=(0, 0, 0, 55))
        im = Image.alpha_composite(im.convert("RGBA"), ov).convert("RGB")
        d = ImageDraw.Draw(im)
        rows = ["°C", "°F", "K", "Add custom unit"]
        ph = 8 + 44 * len(rows) + 8
        x0, y0 = 176, 96
        rrect(d, (x0 + 2, y0 + 3, x0 + 212, y0 + ph + 3), 8, "#E7DBCB")
        rrect(d, (x0, y0, x0 + 212, y0 + ph), 8, "#FFFBF4")
        yy = y0 + 8
        for i, r in enumerate(rows):
            if i == 3:
                d.line([px(x0 + 8), px(yy), px(x0 + 204), px(yy)], fill=DIVIDER, width=px(1))
            txt(d, (x0 + 14, yy + 22), r, 13, INK, anchor="lm")
            yy += 44
        rrect(d, (W_DP - 20 - pw, 120, W_DP - 20, 150), 15, CHIP_SEL_BG)
        txt(d, (W_DP - 20 - pw / 2, 135), "°C", 13.5, CHIP_SEL_INK, anchor="mm")
    return im

def screen_history():
    im, d = panel(hist=True)
    status_bar(d)
    d.line([px(12), px(36), px(24), px(26)], fill=INK, width=px(1.8))
    d.line([px(12), px(36), px(24), px(46)], fill=INK, width=px(1.8))
    txt(d, (32, 30), "History", 18, INK, anchor="lm")
    txt(d, (W_DP - 16, 30), "Clear", 13, INK, anchor="rm")
    yy = 74
    for date, items in [("4 days ago", [("9,764+65", "9,829"), ("12,345 × 3", "37,035")]),
                        ("12 Nov 2026", [("38 °C → °F", "100.4")])]:
        txt(d, (16, yy), date, 11.5, MUTED)
        yy += 22
        for f_, r_ in items:
            txt(d, (W_DP - 16, yy), f_, 24, INK, anchor="rs")
            txt(d, (W_DP - 16, yy + 34), r_, 24, RESULT_INK, anchor="rs")
            yy += 78
            d.line([px(16), px(yy - 18), px(W_DP - 16), px(yy - 18)], fill="#E7DBC9", width=px(1))
    return im

def screen_overlay():
    im, d = panel()
    status_bar(d); toolbar(d)
    dh = display_height(False)
    # display_two_line.xml: formula above result, result_padding_bottom 32dp
    txt(d, (W_DP - 16, dh - 50 - 44), "9,764+65", 30, INK, anchor="rs")
    txt(d, (W_DP - 16, dh - 50), "9,829", 40, RESULT_INK, anchor="rs")
    rrect(d, (W_DP / 2 - 12, dh - 10, W_DP / 2 + 12, dh - 6), 2, "#DCD2C4")
    # tool_panel_overlay.xml: guideline at 40%, card below it, grid of items
    ov = Image.new("RGBA", (W, H), (0, 0, 0, 0))
    ImageDraw.Draw(ov).rectangle([0, px(0.40 * H_DP), W, H], fill=(0, 0, 0, 70))
    im = Image.alpha_composite(im.convert("RGBA"), ov).convert("RGB")
    d = ImageDraw.Draw(im)
    y0 = 0.40 * H_DP
    rrect(d, (0, y0, W_DP, H_DP), 20, PANEL_BG)
    txt(d, (20, y0 + 18), "More tools", 16, INK, bold=True)
    tools = ["Units", "Currency", "Mortgage", "Income tax", "BMI", "Date", "Programmer", "Relationship"]
    gw = (W_DP - 16) / 4
    for i, t in enumerate(tools):
        cx = 8 + (i % 4) * gw + gw / 2
        cy = y0 + 96 + (i // 4) * 96
        txt(d, (cx, cy), t, 10.5, INK, anchor="mm")
    return im

if __name__ == "__main__":
    import os
    here = os.path.dirname(os.path.abspath(__file__))
    out_dir = os.path.join(here, "ours")
    os.makedirs(out_dir, exist_ok=True)
    out = {
        "calc": screen_calculator(False),
        "calc_sci": screen_calculator(True),
        "units": screen_units(False),
        "units_picker": screen_units(False, picker=True),
        "history": screen_history(),
        "overlay": screen_overlay(),
    }
    names = {"calc": "o1-calculator", "calc_sci": "o2-scientific",
             "units": "o3-unit-conversion", "units_picker": "o4-unit-picker",
             "history": "o5-history", "overlay": "o6-more-menu"}
    for k, v in out.items():
        v.save(os.path.join(out_dir, names[k] + ".png"))
    print("wrote", out_dir)
