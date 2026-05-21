---
name: SnipShot Design System
colors:
  surface: '#f9f9fb'
  surface-dim: '#d9dadc'
  surface-bright: '#f9f9fb'
  surface-container-lowest: '#ffffff'
  surface-container-low: '#f3f3f5'
  surface-container: '#edeef0'
  surface-container-high: '#e8e8ea'
  surface-container-highest: '#e2e2e4'
  on-surface: '#1a1c1d'
  on-surface-variant: '#4a4551'
  inverse-surface: '#2f3132'
  inverse-on-surface: '#f0f0f2'
  outline: '#7b7582'
  outline-variant: '#ccc3d2'
  surface-tint: '#6c4da7'
  primary: '#63439c'
  on-primary: '#ffffff'
  primary-container: '#7c5cb7'
  on-primary-container: '#f7edff'
  inverse-primary: '#d4bbff'
  secondary: '#615c6f'
  on-secondary: '#ffffff'
  secondary-container: '#e7dff6'
  on-secondary-container: '#676275'
  tertiary: '#5c4d80'
  on-tertiary: '#ffffff'
  tertiary-container: '#75659a'
  on-tertiary-container: '#f6eeff'
  error: '#ba1a1a'
  on-error: '#ffffff'
  error-container: '#ffdad6'
  on-error-container: '#93000a'
  primary-fixed: '#ebdcff'
  primary-fixed-dim: '#d4bbff'
  on-primary-fixed: '#260059'
  on-primary-fixed-variant: '#54348d'
  secondary-fixed: '#e7dff6'
  secondary-fixed-dim: '#cac3d9'
  on-secondary-fixed: '#1d1929'
  on-secondary-fixed-variant: '#494456'
  tertiary-fixed: '#eaddff'
  tertiary-fixed-dim: '#d0bdf9'
  on-tertiary-fixed: '#211142'
  on-tertiary-fixed-variant: '#4d3e70'
  background: '#f9f9fb'
  on-background: '#1a1c1d'
  surface-variant: '#e2e2e4'
typography:
  display-sm:
    fontFamily: Manrope
    fontSize: 24px
    fontWeight: '700'
    lineHeight: 32px
    letterSpacing: -0.02em
  headline-md:
    fontFamily: Manrope
    fontSize: 18px
    fontWeight: '600'
    lineHeight: 24px
  body-md:
    fontFamily: Hanken Grotesk
    fontSize: 16px
    fontWeight: '400'
    lineHeight: 24px
  label-lg:
    fontFamily: Hanken Grotesk
    fontSize: 14px
    fontWeight: '500'
    lineHeight: 20px
  label-sm:
    fontFamily: Hanken Grotesk
    fontSize: 12px
    fontWeight: '500'
    lineHeight: 16px
    letterSpacing: 0.01em
  metadata-code:
    fontFamily: Hanken Grotesk
    fontSize: 11px
    fontWeight: '400'
    lineHeight: 14px
    letterSpacing: 0.02em
rounded:
  sm: 0.25rem
  DEFAULT: 0.5rem
  md: 0.75rem
  lg: 1rem
  xl: 1.5rem
  full: 9999px
spacing:
  margin-mobile: 1.25rem
  gutter-grid: 1rem
  stack-sm: 0.5rem
  stack-md: 1rem
  inset-card: 1rem
---

## Brand & Style

The design system is centered on efficiency, clarity, and the high-density information needs of scanlation workflows. It targets creators and enthusiasts who require a tool that feels professional yet accessible. 

The visual style is **Corporate Modern with a Minimalist focus**. It utilizes a clean, high-contrast interface to ensure that the user's content—complex manga and manhwa panels—remains the focal point. By using generous white space, structured grids, and subtle tonal layering, the system transforms a potentially cluttered file management task into an organized, streamlined experience. The primary purple accent provides a sense of technological sophistication and creative energy.

## Colors

The palette is anchored by a vibrant **Amethyst Purple** (#7C5CB7) derived from the brand identity. This primary color is used for interactive elements and key brand moments. 

A soft **Lavender Tint** (#E9E1F8) serves as the secondary color, primarily for container backgrounds (like folders) and active states to provide a clear visual distinction without the harshness of high-contrast borders. The neutral palette is deliberately cool and light to ensure that grayscale manga content pops against the interface.

## Typography

This design system uses **Manrope** for headlines to convey a modern, technical, yet balanced personality. **Hanken Grotesk** is used for all functional body and label text, chosen for its exceptional legibility in high-density data scenarios like file names and timestamps.

To maintain hierarchy in an image-heavy dashboard:
- Use `headline-md` for folder titles.
- Use `label-sm` for individual file names.
- Use `metadata-code` for technical details (e.g., resolution, file size, or timestamp).

## Layout & Spacing

The system employs a **Fluid Grid** model optimized for mobile consumption. A 2-column layout is the standard for the dashboard to balance preview visibility with information density.

- **Margins:** 20px (1.25rem) global side margins.
- **Gutters:** 16px (1rem) between cards to prevent visual crowding.
- **Rhythm:** An 8px base unit (0.5rem) governs all internal card padding and element stacking.
- **Reflow:** On tablets, the grid expands to 4 or 6 columns while maintaining the 16px gutter.

## Elevation & Depth

To keep the UI clean and "un-heavy," hierarchy is achieved through **Tonal Layers** and **Low-contrast Outlines** rather than aggressive shadows.

1.  **Base Layer:** The application background (#FFFFFF or #F9F9FB).
2.  **Folder Level:** Uses the secondary Lavender color (#E9E1F8) as a solid fill with no shadow to indicate a container that holds other items.
3.  **Image Card Level:** Uses a white background with a subtle 1px border (#E5E7EB).
4.  **Interactive Floating Action Buttons (FAB):** Utilize a medium ambient shadow (8px blur, 10% opacity, tinted with primary purple) to sit above the scrollable content.

## Shapes

The system uses a **Rounded (Level 2)** shape language. This provides a friendly, contemporary feel that softens the "technical" nature of file management.

- **Standard Cards:** 0.5rem (8px) corner radius.
- **Large Containers/Folders:** 1rem (16px) corner radius to differentiate them from individual items.
- **Floating Buttons/Chips:** Full pill-shape (3rem) for maximum tactile appeal.

## Components

### Folder Containers
Folders should be represented as large, 1:1 or 4:3 aspect ratio cards with a `surface-folder` background. The icon should be centered and utilize the primary brand color. Titles are placed below the icon using `headline-md`.

### Image Thumbnails
Image cards must have a fixed aspect ratio (usually 3:4 for portrait manga pages). The image occupies the top portion, with a white metadata strip at the bottom.
- **Metadata Label:** Use `label-sm` for the filename. Truncate in the middle if necessary (e.g., "trans...842.png").
- **Overlay:** A subtle semi-transparent gradient may be used at the bottom of the image if metadata is overlaid directly on the photo.

### Buttons & FABs
- **Primary FAB:** A pill-shaped button containing a "+" icon, using the primary purple background and white icon.
- **Navigation Bar:** Use active states with a subtle background pill around the icon (using the secondary lavender tint).

### Chips & Badges
Use for status (e.g., "Translated", "Original"). Small, pill-shaped elements with `label-sm` text and 12px horizontal padding.