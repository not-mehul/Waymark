import { expect, test } from '@playwright/test';

// style.md rule 6: removing a booking happens at once, with Undo in the
// toast, rather than behind a confirmation.

test('a booking is removed at once, and Undo puts it back', async ({ page }) => {
  await page.goto('/');
  await page.getByRole('button', { name: 'Load the worked example' }).click();
  const timeline = page.getByRole('tabpanel', { name: 'Timeline' });
  const cards = timeline.locator('.tl-card');
  await expect(cards.first()).toBeVisible();
  // The first booking is the outbound flight: one card on the timeline.
  const before = await cards.count();

  await cards.first().click();
  const title = (await page.locator('#segment-title').textContent())!.trim();
  await page.getByRole('button', { name: 'Remove booking' }).click();

  // No confirmation: straight back to the timeline, without that booking.
  await expect(page.getByRole('dialog')).toHaveCount(0);
  await expect(timeline).toBeVisible();
  await expect(cards).toHaveCount(before - 1);
  await expect(timeline.getByRole('button', { name: /BA286/ })).toHaveCount(0);
  await expect(page.getByText(`Removed “${title}”.`)).toBeVisible();

  await page.getByRole('button', { name: 'Undo' }).click();
  await expect(cards).toHaveCount(before);
  await timeline.getByRole('button', { name: /BA286/ }).click();
  await expect(page.locator('#segment-title')).toHaveText(title);
});
