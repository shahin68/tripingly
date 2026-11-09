---
name: layout-symmetry
description: Use for any composable or canvas design that places content in rows, cards, sheets, pills or bars: Tripinly's symmetry rules for paddings, insets, equal heights and alignment, with a checklist to run before calling UI done.
---

# Layout symmetry

Shahin checks every screen for symmetry. These are all the rules he has given (2026-10-27 to 2026-11-09), gathered from chats so they are never re-asked. `docs/knowledge/07-architecture.md` ("Symmetry", "Plan sheet rows") holds the short form; this skill is the full set with how to apply it. Applies to Compose code **and** to canvas mock-ups.

## 1. Both ends equal

- The left and right ends of every row, card, sheet and bar sit the **same distance in**. Check start padding = end padding on every container, not just the outer one.
- **Icon button at an end:** its 48 dp touch area is not what the eye sees. Pad the end less so the visible circle or glyph lines up with the content at the start (`SheetHeader`, plan sheet title row, the day tabs' round +). A plain `padding(end = …)` is enough; no `touchInset` helpers or extra measuring ("we don't need to be this meticulous").
- **Nested containers:** each level is symmetric on its own. A card sits the same distance in from both sheet edges, and its own content sits the same distance in on every side (e.g. plan sheet card 16 dp in, Route card content 10 dp in on all four sides).
- **Sideways-scrolling rows** (`LazyRow`) don't pad the row; they use that inset as `contentPadding`, so items line up with the rows above at rest and scroll out to the container's edge. Add `Modifier.horizontalFadingEdge(state)`.
- **Bottom vs. sides:** a floating bar's bottom gap matches its side gap so it looks centred in its corner (Home bottom bar, day tabs: `floatingBottom` + 8 dp). Window insets come only from Compose `WindowInsets` (`safeSides`, `bottomSystemBar`); in landscape only the side with the cutout pads, which is accepted.

## 2. Image at the start of a card

- A cover, thumbnail or avatar at a card's start is **equidistant from the card's top, bottom and start**.
- Nothing beside it is taller, so cards in one list are **the same height whatever they show** (with or without ⋮, photo button, heart). If a row with ⋮ would be taller, pad it less vertically (person rows: 4 dp less) instead of letting heights differ.
- Wrapping content height is fine only if every card in the list ends up the same height.

## 3. Same heights in one row

- Controls that sit in one row are **one height**: header pills, back button, search and menu (56 dp on the map header); Route pill, ⋯ and stop pills in the plan sheet (44 dp). Circles stay circles at that height.
- Corner radii match between equivalent cards (Explore cards use the trip cards' radius).
- A loading button keeps its size (spinner replaces the label; never a fixed width to fake it).

## 4. Aligned columns of actions

- **Like/hot buttons line up** down a list: same size everywhere (only *My profile* trips use the small read-only count). On cards with ⋮, the heart sits in the end column **centred under ⋮**, count before it (`countFirst`), so changing count widths never shift the heart.
- Inline heart/flame has **no start padding**: the icon lines up with the text above it (comments: heart under the text, no ripple).
- **Top alignment beats centring** when an avatar meets a title: the title's top lines up with the avatar's top; the top row needn't be vertically centred.
- Avatar inset = close-button inset on the same card (trip card: 10 dp both; 16 dp was too much).

## 5. Small marks

- Red "new" dots: one component (`NewIndicator`), one size (8 dp) and the same stroke on every pin; on a round photo it sits on the circle's edge at 45°, never over a number or icon; same colour selected or not, light and dark.
- Drop-down menus open anchored to the element that opened them.

## Checklist (run on every UI change and canvas board)

1. For each container: start = end? top/bottom matched where it's a card with a start image?
2. End icon buttons: does the visible glyph line up with the start content (not the touch area)?
3. Sideways lists: inset as `contentPadding`, scrolling to the edge, fading edge on?
4. Everything in one row the same height; every card in one list the same height?
5. Hearts/counters aligned in a column across the list, independent of count width?
6. Avatar/title tops aligned; avatar inset = close inset?
7. Portrait and landscape both still symmetric (except the system cutout side)?

Use `spacing.*` tokens; when a rule needs a number not in the tokens, take it from the existing component that already follows the rule rather than inventing one.
