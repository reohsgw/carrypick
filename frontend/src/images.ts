import hero from './assets/hero.jpg';
import keychain from './assets/keychain.jpg';
import skincare from './assets/skincare.jpg';
import snacks from './assets/snacks.jpg';
import { assetUrl } from './api';

/* Photos taken from the team's Figma design. */
export const IMAGES = { hero, keychain, skincare, snacks };

/**
 * The photo to show for a product: the traveler's own upload if there is one,
 * otherwise a category photo so lists don't look empty in the demo. Categories without a
 * matching photo return null and get an icon tile instead.
 */
export function productPhoto(imageUrl: string | null | undefined, category: string | null | undefined): string | null {
  const own = assetUrl(imageUrl);
  if (own) return own;
  switch ((category ?? '').toLowerCase()) {
    case 'snacks':
    case 'food':
      return snacks;
    case 'beauty':
      return skincare;
    case 'other':
      return keychain;
    default:
      return null;
  }
}
