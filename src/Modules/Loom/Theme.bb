// =============================================================================
// Loom/Theme.bb -- color palette and 2D drawing primitives
// =============================================================================
//
// The Loom design (see ../../design/loom-prototype/) uses a dark-fantasy
// palette pulled from three places: deep-navy night sky, stone-gray panels
// with brass trim, and parchment cream for body text. This file encodes the
// design tokens as RGB triplets, and provides drawing helpers that wrap
// Blitz3D's primitive Color/Rect/Line/Text calls so the rest of Loom can
// paint surfaces without restating colors at every site.
//
// Why custom-draw at all (vs. F-UI for everything):
//   The Loom aesthetic is gradient-heavy and uses ornamented type. F-UI
//   renders flat stone-gray panels with system fonts; it cannot express the
//   look without a fork. Building one custom-draw layer here lets every
//   surface (atlas, world view, composer, ribbon, palette) share the same
//   visual language. F-UI is reserved for things it is genuinely good at
//   (file dialogs, native text input).
//
// Public API (everything here is called Loom_*):
//
//   Color tokens (use with LoomColor or directly):
//     LOOM_STONE_950, LOOM_STONE_900, ..., LOOM_STONE_100
//     LOOM_ARCANE_900 ... LOOM_ARCANE_300       (primary accent)
//     LOOM_BRASS_800 ... LOOM_BRASS_300         (ornament, ranks)
//     LOOM_PARCHMENT_100, LOOM_PARCHMENT_200    (body text)
//     LOOM_INK_900, LOOM_INK_700                (text on parchment)
//     LOOM_SUCCESS, LOOM_WARNING, LOOM_DANGER   (semantic)
//
//   LoomColor(r, g, b)              -- thin wrapper for Blitz Color
//   LoomFill(x, y, w, h, r, g, b)   -- filled rectangle
//   LoomBorder(x, y, w, h, r, g, b) -- 1px outline rectangle
//   LoomGradientV(x, y, w, h, r1, g1, b1, r2, g2, b2)
//                                   -- vertical gradient, top -> bottom
//   LoomHRule(x, y, w, r, g, b)     -- 1px horizontal divider
//   LoomText(x, y, text$, r, g, b, align=0)
//                                   -- single-line text. align: 0=left, 1=center, 2=right
//   LoomTextCentered(cx, y, text$, r, g, b)
//                                   -- text centered horizontally at cx
//
//   LoomTheme_Init()                -- one-time setup. Loads any fonts we need
//                                     (current alpha: none -- uses default font).
// =============================================================================


// -----------------------------------------------------------------------------
// COLOR TOKENS -- packed RGB triplets exposed as separate r/g/b constants so
// callers can write `LoomFill(..., LOOM_STONE_900_R, LOOM_STONE_900_G,
// LOOM_STONE_900_B)`. Blitz3D's Color() takes three ints; we'd rather have
// the readable token name at every call site than a single packed int that
// has to be unpacked.
// -----------------------------------------------------------------------------

// Stone & shadow (panels, backgrounds)
Const LOOM_STONE_950_R = 8   : Const LOOM_STONE_950_G = 9   : Const LOOM_STONE_950_B = 15    // #08090f deep void
Const LOOM_STONE_900_R = 14  : Const LOOM_STONE_900_G = 16  : Const LOOM_STONE_900_B = 24    // #0e1018 canvas
Const LOOM_STONE_850_R = 20  : Const LOOM_STONE_850_G = 23  : Const LOOM_STONE_850_B = 42    // #14172a card surface
Const LOOM_STONE_800_R = 28  : Const LOOM_STONE_800_G = 34  : Const LOOM_STONE_800_B = 56    // #1c2238 raised
Const LOOM_STONE_700_R = 38  : Const LOOM_STONE_700_G = 42  : Const LOOM_STONE_700_B = 58    // #262a3a sunken
Const LOOM_STONE_500_R = 79  : Const LOOM_STONE_500_G = 75  : Const LOOM_STONE_500_B = 72    // #4f4b48 mortar
Const LOOM_STONE_300_R = 138 : Const LOOM_STONE_300_G = 133 : Const LOOM_STONE_300_B = 128   // #8a8580 dust
Const LOOM_STONE_200_R = 179 : Const LOOM_STONE_200_G = 174 : Const LOOM_STONE_200_B = 164   // #b3aea4 dry stone
Const LOOM_STONE_100_R = 216 : Const LOOM_STONE_100_G = 209 : Const LOOM_STONE_100_B = 194   // #d8d1c2 lit limestone

// Arcane blue (primary accent)
Const LOOM_ARCANE_900_R = 17  : Const LOOM_ARCANE_900_G = 38  : Const LOOM_ARCANE_900_B = 79    // #11264f
Const LOOM_ARCANE_700_R = 30  : Const LOOM_ARCANE_700_G = 80  : Const LOOM_ARCANE_700_B = 153   // #1e5099
Const LOOM_ARCANE_500_R = 61  : Const LOOM_ARCANE_500_G = 166 : Const LOOM_ARCANE_500_B = 245   // #3da6f5 signature
Const LOOM_ARCANE_300_R = 168 : Const LOOM_ARCANE_300_G = 220 : Const LOOM_ARCANE_300_B = 255   // #a8dcff

// Brass / gold (ornament)
Const LOOM_BRASS_800_R = 85  : Const LOOM_BRASS_800_G = 59  : Const LOOM_BRASS_800_B = 19   // #553b13
Const LOOM_BRASS_700_R = 122 : Const LOOM_BRASS_700_G = 88  : Const LOOM_BRASS_700_B = 33   // #7a5821
Const LOOM_BRASS_500_R = 201 : Const LOOM_BRASS_500_G = 164 : Const LOOM_BRASS_500_B = 74   // #c9a44a signature
Const LOOM_BRASS_300_R = 243 : Const LOOM_BRASS_300_G = 220 : Const LOOM_BRASS_300_B = 160  // #f3dca0

// Parchment & ink
Const LOOM_PARCHMENT_100_R = 246 : Const LOOM_PARCHMENT_100_G = 239 : Const LOOM_PARCHMENT_100_B = 220   // #f6efdc body
Const LOOM_PARCHMENT_200_R = 235 : Const LOOM_PARCHMENT_200_G = 223 : Const LOOM_PARCHMENT_200_B = 185   // #ebdfb9 muted
Const LOOM_INK_900_R = 22  : Const LOOM_INK_900_G = 17  : Const LOOM_INK_900_B = 10        // #16110a
Const LOOM_INK_700_R = 58  : Const LOOM_INK_700_G = 46  : Const LOOM_INK_700_B = 29        // #3a2e1d

// Semantic
Const LOOM_SUCCESS_R = 76  : Const LOOM_SUCCESS_G = 174 : Const LOOM_SUCCESS_B = 79
Const LOOM_WARNING_R = 230 : Const LOOM_WARNING_G = 162 : Const LOOM_WARNING_B = 58
Const LOOM_DANGER_R  = 184 : Const LOOM_DANGER_G  = 48  : Const LOOM_DANGER_B  = 42


// -----------------------------------------------------------------------------
// Cross-surface layout constants. Lives here (not in any single surface's
// module) because multiple surfaces need to share the values to stay aligned:
//   - LOOM_TOP_RIBBON_H: vertical space the Validation Conscience Ribbon
//     (Ribbon.bb) reserves at y=0. Browser top ribbon starts at this y,
//     Composer top starts at this y + its own brand strip, Palette modal
//     centers below it (the ribbon stays visible when the palette is open
//     so the dirty count and broken-ref count remain available).
// -----------------------------------------------------------------------------
Const LOOM_TOP_RIBBON_H = 28


// -----------------------------------------------------------------------------
// State: fonts loaded by LoomTheme_Init. For the alpha skeleton we just use
// the Blitz default font; later phases will load MedievalSharp / Cinzel /
// Cormorant Garamond ttf files from Data/Loom/Fonts/ when those are added.
// -----------------------------------------------------------------------------
Global LoomFont_Body    = 0
Global LoomFont_Display = 0


// =============================================================================
// LoomTheme_Init -- one-time setup. Safe to call multiple times.
//
// Loads body + display fonts. Blitz3D's LoadFont takes a Windows font
// name; Segoe UI is the Windows Vista+ default UI font and looks
// noticeably more polished than the built-in bitmap default. If the
// font isn't installed (older Windows or unusual locale), LoadFont
// returns 0 and the rest of Loom paints with the default font as before
// -- no visible failure mode.
// =============================================================================
Function LoomTheme_Init()
    LoomFont_Body    = LoadFont("Segoe UI", 14, False, False, False)
    LoomFont_Display = LoadFont("Segoe UI", 18, True,  False, False)
    If LoomFont_Body <> 0 Then SetFont LoomFont_Body
End Function


; Swap to the body font for subsequent Text() calls. Surfaces that want
; bigger / bold text (LOOM brand mark, modal titles) call UseDisplay;
; everything else stays on the body font set at boot.
Function LoomTheme_UseBody()
    If LoomFont_Body <> 0 Then SetFont LoomFont_Body
End Function

Function LoomTheme_UseDisplay()
    If LoomFont_Display <> 0 Then SetFont LoomFont_Display
End Function


// =============================================================================
// Drawing primitives
// =============================================================================

Function LoomColor(r, g, b)
    Color r, g, b
End Function


; Drop shadow under a card/modal rect. Two-layer fake -- Blitz3D has no
; alpha so we approximate softness by painting a mid-stone rect at +2px
; and a deep-stone rect at +4px. Cheap (two filled rects) and gives the
; surface a clear visual lift from the background.
;
; Call BEFORE painting the surface fill so the shadow lands underneath.
Function LoomShadowCard(x, y, w, h)
    Color LOOM_STONE_900_R, LOOM_STONE_900_G, LOOM_STONE_900_B
    Rect x + 2, y + 2, w, h, True
    Color LOOM_STONE_950_R, LOOM_STONE_950_G, LOOM_STONE_950_B
    Rect x + 4, y + 4, w, h, True
End Function

Function LoomFill(x, y, w, h, r, g, b)
    Color r, g, b
    Rect x, y, w, h, True
End Function

Function LoomBorder(x, y, w, h, r, g, b)
    Color r, g, b
    Rect x, y, w, h, False
End Function

// Vertical gradient by drawing N horizontal stripes with interpolated color.
// Step size is 1 pixel for smoothness; if h is large and perf matters, callers
// can fake bigger steps. This is fine for splashes and panels.
Function LoomGradientV(x, y, w, h, r1, g1, b1, r2, g2, b2)
    If h <= 0 Then Return
    Local i = 0
    Local rr, gg, bb
    For i = 0 To h - 1
        // Interpolate between (r1,g1,b1) at i=0 and (r2,g2,b2) at i=h-1.
        // Multiply-then-divide to keep ints in range.
        rr = r1 + ((r2 - r1) * i) / (h - 1)
        gg = g1 + ((g2 - g1) * i) / (h - 1)
        bb = b1 + ((b2 - b1) * i) / (h - 1)
        Color rr, gg, bb
        Line x, y + i, x + w - 1, y + i
    Next
End Function

Function LoomHRule(x, y, w, r, g, b)
    Color r, g, b
    Line x, y, x + w - 1, y
End Function

// Arbitrary straight line between two points (LoomHRule is horizontal-only).
// Used by the thread-web overlay to draw threads between entity nodes.
Function LoomLine(x1, y1, x2, y2, r, g, b)
    Color r, g, b
    Line x1, y1, x2, y2
End Function

// align: 0 = left, 1 = center, 2 = right (relative to x).
Function LoomText(x, y, txt$, r, g, b, align = 0)
    Color r, g, b
    Text x, y, txt$, align, 0
End Function

// Convenience: text centered horizontally at cx. Vertically aligned by y top.
Function LoomTextCentered(cx, y, txt$, r, g, b)
    Color r, g, b
    Text cx, y, txt$, 1, 0
End Function
