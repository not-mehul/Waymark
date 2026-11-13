import AxeBuilder from '@axe-core/playwright';
import { expect, test, type Page } from '@playwright/test';

// style.md §15: every screen passes axe, and holds at 200% text without
// scrolling sideways. Text sizes are rem, so 200% on the root is what the
// phone's largest font setting does to the page.

async function noViolations(page: Page) {
  const { violations } = await new AxeBuilder({ page }).analyze();
  expect(violations.map((v) => `${v.id}: ${v.nodes.map((n) => n.target.join(' ')).join(', ')}`)).toEqual([]);
}

async function holdsAtDoubleText(page: Page) {
  await page.addStyleTag({ content: 'html { font-size: 200% !important; }' });
  await page.waitForTimeout(100);
  const overflow = await page.evaluate(
    () => document.documentElement.scrollWidth - document.documentElement.clientWidth
  );
  expect(overflow).toBeLessThanOrEqual(1);
}

async function openExample(page: Page) {
  await page.goto('/');
  await page.getByRole('button', { name: 'Load the worked example' }).click();
  await expect(page.locator('#trip-title')).toBeVisible();
}

test('the shelf', async ({ page }) => {
  await page.goto('/');
  await expect(page.locator('#shelf-title')).toBeVisible();
  await noViolations(page);
  await holdsAtDoubleText(page);
});

test('a trip, its menu and a booking', async ({ page }) => {
  await openExample(page);
  await noViolations(page);
  await page.getByRole('button', { name: 'More' }).click();
  await expect(page.getByRole('dialog')).toBeVisible();
  await noViolations(page);
  await page.getByRole('button', { name: 'Close' }).click();
  await expect(page.getByRole('dialog')).toBeHidden();
  await page.locator('.tl-card').first().click();
  await expect(page.locator('#segment-title')).toBeVisible();
  await noViolations(page);
  await expect(page.getByRole('button', { name: 'Add a ticket or document' })).toBeVisible();
  await holdsAtDoubleText(page);
});

test('before you go: the list, and a passport that runs out', async ({ page }) => {
  await openExample(page);
  await page.getByRole('button', { name: 'More' }).click();
  await page.getByRole('button', { name: /Before you go/ }).click();
  await expect(page.locator('#checklist-title')).toBeVisible();
  await page.getByRole('button', { name: 'Add them all' }).click();
  const passport = page.getByRole('checkbox', { name: 'Passport' });
  await passport.click();
  await expect(passport).toHaveAttribute('aria-checked', 'true');
  await noViolations(page);
  await holdsAtDoubleText(page);
});

test('finding a booking across trips', async ({ page }) => {
  await openExample(page);
  await page.getByRole('button', { name: 'Back to itineraries' }).click();
  await page.getByRole('button', { name: 'Find a booking' }).click();
  await page.getByLabel('A place, a hotel, an airline or a reference').fill('bloomsbury');
  const result = page.getByRole('button', { name: /The Bloomsbury Rooms/ });
  await expect(result).toBeVisible();
  await noViolations(page);
  await result.click();
  await expect(page.locator('#segment-title')).toHaveText('The Bloomsbury Rooms');
});
