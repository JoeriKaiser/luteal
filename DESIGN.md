---
name: Luteal
description: A reassuring, discreet, and precise cycle tracker and Duo companion.
colors:
  evergreen-primary: "#235B4E"
  evergreen-primary-dark: "#8FD4BE"
  linen-background: "#F1EDE1"
  porcelain-surface: "#FCFAF5"
  stone-container: "#E6E1D3"
  warm-ink: "#252B27"
  quiet-ink: "#646B66"
  menstrual-rust: "#934545"
  follicular-moss: "#486D55"
  ovulatory-ochre: "#775F1D"
  luteal-indigo: "#5D587E"
typography:
  display:
    fontFamily: "Android system sans-serif"
    fontSize: "32sp"
    fontWeight: 600
    lineHeight: "38sp"
    letterSpacing: "-0.3sp"
  headline:
    fontFamily: "Android system sans-serif"
    fontSize: "28sp"
    fontWeight: 600
    lineHeight: "34sp"
  title:
    fontFamily: "Android system sans-serif"
    fontSize: "20sp"
    fontWeight: 600
    lineHeight: "26sp"
  body:
    fontFamily: "Android system sans-serif"
    fontSize: "16sp"
    fontWeight: 400
    lineHeight: "24sp"
  label:
    fontFamily: "Android system sans-serif"
    fontSize: "14sp"
    fontWeight: 600
    lineHeight: "20sp"
rounded:
  xs: "4dp"
  sm: "8dp"
  md: "12dp"
  lg: "16dp"
  xl: "24dp"
spacing:
  xxs: "4dp"
  xs: "8dp"
  sm: "12dp"
  md: "16dp"
  lg: "24dp"
  xl: "32dp"
  xxl: "48dp"
components:
  button-primary:
    backgroundColor: "{colors.evergreen-primary}"
    textColor: "{colors.porcelain-surface}"
    typography: "{typography.label}"
    rounded: "{rounded.md}"
    height: "52dp"
  card:
    backgroundColor: "{colors.porcelain-surface}"
    textColor: "{colors.warm-ink}"
    rounded: "{rounded.lg}"
    padding: "24dp"
  navigation-active:
    backgroundColor: "{colors.evergreen-primary}"
    textColor: "{colors.evergreen-primary}"
    typography: "{typography.label}"
  status-pill:
    backgroundColor: "{colors.stone-container}"
    textColor: "{colors.warm-ink}"
    typography: "{typography.label}"
    rounded: "{rounded.xl}"
    padding: "6dp 12dp"
---

# Design System: Luteal

## 1. Overview

**Creative North Star: "The Quiet Instrument"**

Luteal behaves like a well-made personal instrument: immediate to read, calm to handle, and precise about what it knows. Warm surfaces prevent the product from feeling clinical while disciplined hierarchy keeps sensitive daily tasks clear. Identity appears through material restraint and subtle cyclical geometry, never through spectacle.

The default scene is a short one-handed check-in in ordinary morning light. Light mode is therefore primary, while dark mode follows the Android system and uses lighter tonal surfaces for depth. Muji planner tactility, Google Pixel interaction clarity, and precise analog instruments anchor the direction.

The system rejects astrology styling, cute or gender-stereotyped wellness branding, gamification, sterile clinical software, permanent star fields, neon accents, decorative glassmorphism, and full-spectrum gradients.

**Key Characteristics:**
- Light-first warm neutral canvas
- One rare evergreen product accent
- Recorded facts visually distinct from estimates
- Familiar Android controls with complete states
- Celestial references reduced to optional cyclical geometry

## 2. Colors

The palette combines linen-tinted neutrals with a controlled evergreen accent. Phase colors are semantic data colors, not decorative page themes.

### Primary
- **Forest Instrument:** The product accent for primary actions, selection, focus, and active navigation. It should carry less than ten percent of a typical screen.
- **Night Evergreen:** The dark-theme product accent, softened to preserve legibility against dark tonal surfaces.

### Secondary
- **Quiet Clay:** Material 3 tertiary roles support estimate labels and secondary emphasis. It never competes with the primary action.

### Tertiary
- **Menstrual Rust, Follicular Moss, Ovulatory Ochre, and Luteal Indigo:** Reserved for phase or observation data. Every use requires a text label or icon because color alone cannot communicate meaning.

### Neutral
- **Linen Ground:** Primary light-theme background.
- **Porcelain Surface:** Actionable and grouped surfaces.
- **Stone Container:** Secondary grouping and inactive controls.
- **Warm Ink:** Primary text.
- **Quiet Ink:** Supporting text and metadata.

**The One Accent Rule.** Evergreen is reserved for actions, focus, and current selection. Decorative accent coverage is forbidden.

**The Recorded Versus Estimated Rule.** Recorded information uses evergreen confirmation. Estimates use the quieter tertiary role and always include the word “estimé” or equivalent explanatory copy. Cycle phases must never be inferred in the presentation layer from fixed day thresholds, and percentage progress must not appear without a domain-backed range.

## 3. Typography

**Display Font:** Android system sans-serif
**Body Font:** Android system sans-serif

**Character:** A single native humanist sans family keeps the app immediate, readable, and familiar. Hierarchy comes from size, weight, color, and spacing rather than a decorative display face.

### Hierarchy
- **Display** (600, 32sp, 38sp): Rare high-emphasis values and first-level moments.
- **Headline** (600, 28sp, 34sp): Screen titles.
- **Title** (600, 20sp, 26sp): Major sections and grouped surfaces.
- **Body** (400, 16sp, 24sp): Primary reading copy. Longer text stays within a comfortable measure on large screens.
- **Label** (600, 14sp, 20sp): Buttons, navigation, status, and compact controls.

**The Native Clarity Rule.** Never use a display font for labels, buttons, health observations, or numerical data.

**The Scalable Text Rule.** Layouts must survive Android font scaling through 200 percent without clipped labels, hidden actions, or overlapping controls. Action pairs stack when their French labels no longer fit, status rows become vertical, and navigation may use a shorter visible label while preserving the full accessible name.

## 4. Elevation

Luteal is flat by default. Light-mode depth comes from explicit surface colors and a quiet one-pixel outline. Dark-mode depth comes from progressively lighter surfaces, not shadows. Temporary overlays may use Material 3 platform elevation.

**The Flat by Default Rule.** Resting cards use zero elevation. Shadows appear only where platform behavior requires spatial separation, such as sheets or transient menus.

## 5. Components

Components use familiar Material 3 behavior with restrained Luteal tokens. Every interactive component needs default, focused, pressed, disabled, loading, error, and success treatment where the state applies.

### Buttons
- **Shape:** Gently structured corners (12dp), never pill-shaped by default.
- **Primary:** Forest Instrument background, light tinted text, 52dp minimum height.
- **Pressed / Focus:** Material state layer plus a visible accessible focus treatment.
- **Secondary:** Outlined, transparent, and equal in height to the primary action.

### Chips
- **Style:** Compact 8dp corners with explicit selected and unselected states.
- **State:** Selected state uses a tonal container, check icon, and selected semantics. It never relies on a color shift alone.
- **Reflow:** Option groups wrap into an adaptive grid. Horizontally clipped choices without a visible scroll affordance are forbidden.

### Cards / Containers
- **Corner Style:** Calm 16dp corners.
- **Background:** Porcelain Surface in light mode or the appropriate tonal surface in dark mode.
- **Shadow Strategy:** Zero resting elevation.
- **Border:** One-pixel outline variant around truly grouped content.
- **Internal Padding:** 24dp, reduced only for compact lists.
- **Use:** Cards are reserved for actionable or strongly related content. Section headings, spacing, and dividers group ordinary lists and settings.

### Inputs / Fields
- **Style:** Material outlined fields, persistent labels, 12dp corners, and a minimum 48dp target.
- **Focus:** Evergreen focus treatment with screen-reader-compatible labeling.
- **Error / Disabled:** Explicit message and icon treatment, never color alone.
- **Draft Safety:** Observation drafts remain visible until persistence succeeds. Failure preserves every field and exposes a specific retry action.

### Navigation
- Bottom navigation uses four stable destinations: Aujourd’hui, Journal, Duo, and Réglages. Labels remain visible. At extreme text scaling the visible Aujourd’hui label may become Jour while its full accessible name remains unchanged. Active selection uses the primary tonal role; inactive items remain high enough contrast to read.

### Selection Rows
- Switch, radio, and checkbox rows expose exactly one focusable semantic control. Title, supporting text, role, value, and state are announced together; nested duplicate actions are forbidden.

### Status Pill
- A compact read-only label distinguishes recorded, estimated, private, and local-only states. Semantic variants own their color role and icon, so screens never assemble arbitrary status treatments. It is not an action and must not look tappable.

## 6. Do's and Don'ts

### Do:
- **Do** use the 4dp spacing foundation and vary spacing to create rhythm.
- **Do** keep controls at least 48dp high and preserve visible labels.
- **Do** separate recorded facts from estimates in color, icon, and copy.
- **Do** follow the system dark-mode preference and reduced-motion setting.
- **Do** use celestial identity only as restrained cyclical geometry or optional illustration.

### Don't:
- **Don't** resemble astrology or horoscope products or imply a biological relationship with celestial events.
- **Don't** use cute, infantilizing, or gender-stereotyped wellness branding.
- **Don't** gamify tracking with streaks, scores, rewards, or guilt-producing completion states.
- **Don't** imitate sterile clinical software or present Luteal as a medical instrument.
- **Don't** use permanent star fields, neon accents, decorative glassmorphism, or full-spectrum gradients.
- **Don't** use gradient text, side-stripe accents, identical card grids, nested cards, or modal dialogs as the first solution.
- **Don't** communicate phase, intensity, selection, privacy, or error through color alone.
