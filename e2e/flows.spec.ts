import AxeBuilder from '@axe-core/playwright';
import { expect, test, type Page } from '@playwright/test';

// The main flows, end to end, on a fresh install: a new trip set with the
// app's own date picker, a stay added one question at a time with the clock
// picker, and an expense split between two. The clock is pinned so the
// default dates (a fortnight out) and the calendar's month are always the same.

test.use({ timezoneId: 'UTC' });

test.beforeEach(async ({ page }) => {
  await page.clock.install({ time: new Date('2026-11-14T10:00:00Z') });
  await page.goto('/');
});

async function noViolations(page: Page) {
  const { violations } = await new AxeBuilder({ page }).analyze();
  expect(violations.map((v) => `${v.id}: ${v.nodes.map((n) => n.target.join(' ')).join(', ')}`)).toEqual([]);
}

async function pickDay(page: Page, field: string, day: string, nextMonth = false) {
  await page.locator(field).click();
  const picker = page.getByRole('dialog').last();
  if (nextMonth) await picker.getByRole('button', { name: 'Next month' }).click();
  await picker.getByRole('button', { name: day }).click();
  await picker.getByRole('button', { name: 'Choose' }).click();
}

async function newTrip(page: Page) {
  await page.getByRole('button', { name: 'New itinerary' }).click();
  await page.getByLabel('Name').fill('Lisbon long weekend');
  await page.getByLabel('Destinations').fill('Lisbon');
  await pickDay(page, '#trip-start', 'Fri 11 Dec', true);
  await pickDay(page, '#trip-end', 'Sun 13 Dec');
  await page.getByRole('button', { name: 'Create' }).click();
  await expect(page.locator('#trip-title')).toHaveText('Lisbon long weekend');
}

test('a new trip, its dates from the app calendar, kept after a reload', async ({ page }) => {
  await page.getByRole('button', { name: 'New itinerary' }).click();
  const form = page.getByRole('dialog', { name: 'New itinerary' });
  await expect(form.locator('#trip-start')).toContainText('Sat 28 Nov');
  await expect(page.getByRole('button', { name: 'Create' })).toBeDisabled();
  await page.getByLabel('Name').fill('Lisbon long weekend');

  // Nothing changes until Choose: cancelling the calendar keeps the old day.
  await page.locator('#trip-start').click();
  await page.getByRole('dialog').last().getByRole('button', { name: 'Sun 29 Nov' }).click();
  await page.getByRole('dialog').last().getByRole('button', { name: 'Cancel' }).click();
  await expect(form.locator('#trip-start')).toContainText('Sat 28 Nov');

  // Moving the first day past the last carries the whole week with it.
  await pickDay(page, '#trip-start', 'Fri 11 Dec', true);
  await expect(form.locator('#trip-end')).toContainText('Thu 17 Dec');
  await expect(form).toContainText('7 days');

  // The last day can't go before the first.
  await page.locator('#trip-end').click();
  await expect(page.getByRole('dialog').last().getByRole('button', { name: 'Thu 10 Dec' })).toBeDisabled();
  await page.getByRole('dialog').last().getByRole('button', { name: 'Sun 13 Dec' }).click();
  await page.getByRole('dialog').last().getByRole('button', { name: 'Choose' }).click();
  await expect(form).toContainText('3 days');

  await page.getByRole('button', { name: 'Create' }).click();
  await expect(page.locator('#trip-title')).toHaveText('Lisbon long weekend');
  await expect(page.getByText('11–13 Dec 2026')).toBeVisible();

  await page.reload();
  await expect(page.getByText('Lisbon long weekend').first()).toBeVisible();
});

test('a stay, added one question at a time, lands under its days', async ({ page }) => {
  await newTrip(page);
  await page.getByRole('button', { name: 'Add plan' }).click();
  await page.getByRole('button', { name: /Somewhere to stay/ }).click();

  // Step two needs a name before Next.
  await expect(page.getByRole('button', { name: 'Next' })).toBeDisabled();
  await page.getByLabel('Name').fill('Casa do Alfama');
  await page.getByLabel('Where').fill('Alfama, Lisbon');
  await page.getByRole('button', { name: 'Next' }).click();

  await expect(page.locator('#plan-start')).toContainText('Fri 11 Dec');
  await pickDay(page, '#plan-end', 'Sun 13 Dec');

  // Escape (Android's Back on the web) closes the clock, not the whole sheet.
  await page.locator('#plan-from-time').click();
  await expect(page.getByRole('dialog')).toHaveCount(2);
  await page.keyboard.press('Escape');
  await expect(page.getByRole('dialog')).toHaveCount(1);
  await expect(page.getByRole('dialog', { name: 'Add a plan' })).toBeVisible();

  await page.locator('#plan-from-time').click();
  const clock = page.getByRole('dialog').last();
  await clock.getByRole('group', { name: 'Hour' }).getByRole('button', { name: '16' }).click();
  await clock.getByRole('button', { name: '30 minutes' }).click();
  await expect(clock.getByRole('heading', { name: '16:30' })).toBeVisible();
  await clock.getByRole('button', { name: 'Choose' }).click();
  await expect(page.locator('#plan-from-time')).toContainText('16:30');
  await noViolations(page);

  await page.getByRole('button', { name: 'Next' }).click();
  await page.getByLabel('Confirmation code').fill('ALF-2291');
  await page.getByRole('button', { name: 'Add', exact: true }).click();
  await expect(page.getByRole('dialog')).toHaveCount(0);

  const timeline = page.getByRole('tabpanel', { name: 'Timeline' });
  await expect(timeline.getByRole('heading', { name: 'Friday 11 December' })).toBeVisible();
  await expect(timeline.getByRole('button', { name: /^Casa do Alfama .*2 nights/ })).toBeVisible();
  await expect(timeline.getByRole('heading', { name: 'Sunday 13 December' })).toBeVisible();
  await expect(timeline.getByRole('button', { name: /^Check out · Casa do Alfama/ })).toBeVisible();

  await timeline.getByRole('button', { name: /^Casa do Alfama/ }).click();
  await expect(page.locator('#segment-title')).toHaveText('Casa do Alfama');
  await expect(page.getByText(/Check-in 16:30 Fri 11 Dec/)).toBeVisible();
  await expect(page.getByRole('button', { name: 'Copy Confirmation ALF-2291' })).toBeVisible();
});

test('an expense split between two, and the payment that squares it', async ({ page }) => {
  await newTrip(page);
  await page.getByRole('tab', { name: 'Costs' }).click();
  await expect(page.getByRole('button', { name: 'Add an expense' })).toBeDisabled();

  await page.getByRole('tab', { name: 'Party' }).click();
  for (const name of ['Mehul Sen', 'Asha Rao']) {
    await page.getByRole('button', { name: 'Add traveler' }).click();
    await page.getByLabel('Full name').fill(name);
    await page.getByRole('button', { name: 'Add', exact: true }).click();
    await expect(page.getByRole('button', { name: `Edit ${name}` })).toBeVisible();
  }

  await page.getByRole('tab', { name: 'Costs' }).click();
  await page.getByRole('button', { name: 'Add an expense' }).click();
  const sheet = page.getByRole('dialog', { name: 'Add an expense' });
  await expect(sheet.getByRole('button', { name: 'Save' })).toBeDisabled();
  await sheet.getByLabel('What it was').fill('Dinner at the taberna');
  await sheet.getByLabel('How much, in USD').fill('84.50');
  await sheet.getByRole('button', { name: 'Save' }).click();

  const costs = page.getByRole('tabpanel', { name: 'Costs' });
  await expect(costs).toContainText(/\$84\.50\s*spent together/);
  await expect(costs).toContainText('$42.25 a head');
  await expect(costs).toContainText('Asha Rao pays Mehul Sen $42.25');
  await expect(costs.getByRole('button', { name: /^Dinner at the taberna Mehul Sen paid/ })).toBeVisible();
  await noViolations(page);

  // Editing who it was for moves the whole bill onto one person.
  await costs.getByRole('button', { name: /^Dinner at the taberna/ }).click();
  const edit = page.getByRole('dialog', { name: 'Edit the expense' });
  const forGroup = edit.getByRole('button', { name: 'Mehul Sen' }).last();
  await forGroup.click();
  await expect(forGroup).toHaveAttribute('aria-pressed', 'false');
  await edit.getByRole('button', { name: 'Save' }).click();
  await expect(costs).toContainText('Asha Rao pays Mehul Sen $84.50');
});
