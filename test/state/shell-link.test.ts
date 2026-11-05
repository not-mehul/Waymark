import { beforeEach, describe, expect, it, vi } from 'vitest';

const go = vi.fn();
const close = vi.fn();
const trip = vi.fn();

vi.mock('../../src/state/app', () => ({ trips: { trip }, settings: {} }));
vi.mock('../../src/state/ui.svelte', () => ({ ui: { go, close } }));

const { openLink } = await import('../../src/state/shell.svelte');

describe('waymark:// links from the sibling apps', () => {
  beforeEach(() => {
    go.mockReset();
    close.mockReset();
    trip.mockReset();
  });

  it('opens the trip a link names when it is on this phone', async () => {
    trip.mockResolvedValue({ id: 't1' });
    await openLink('waymark://open?trip=t1&source=ledger');
    expect(trip).toHaveBeenCalledWith('t1');
    expect(close).toHaveBeenCalled();
    expect(go).toHaveBeenCalledWith({ name: 'trip', tripId: 't1' });
  });

  it('stays on the shelf for a trip that is not here', async () => {
    trip.mockResolvedValue(null);
    await openLink('waymark://open?trip=gone');
    expect(go).not.toHaveBeenCalled();
  });

  it('stays on the shelf for a bare open, unknown actions and other apps', async () => {
    await openLink('waymark://open');
    await openLink('waymark://teleport?trip=t1');
    await openLink('ledger://open?trip=t1');
    expect(trip).not.toHaveBeenCalled();
    expect(go).not.toHaveBeenCalled();
  });
});
