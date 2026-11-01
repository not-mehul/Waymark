import type { IdeaKind } from '../../domain/model';
import type { IconName } from '../../util/icons';

/** The glyph for each kind, shared by the picker and the board. */
export const IDEA_ICON: Record<IdeaKind, IconName> = {
  SEE: 'eye',
  EAT: 'utensils',
  DO: 'activity',
  SHOP: 'bag'
};
