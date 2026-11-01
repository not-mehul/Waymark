<script lang="ts">
  // What to know on arrival, for the places this trip actually stops: the
  // place with the most hours in it first, and anywhere else with a day or
  // more after it. A connection is not a destination. Ported from
  // InsightsScreen.kt.
  import { onDestroy } from 'svelte';
  import { countryCurrency, countryDriving, countryOf, countryPower } from '../../catalog/countries';
  import { destinations } from '../../domain/time-at-place';
  import { dayCompact, duration } from '../../domain/time-text';
  import { describeWeather, isWet, stopLabel, temperature } from '../../domain/weather';
  import { settings } from '../../state/app';
  import { ui } from '../../state/ui.svelte';
  import { WeatherModel } from '../../state/weather.svelte';
  import { useTrip } from '../trip/context';
  import Icon from '../ui/Icon.svelte';
  import SectionHeader from '../ui/SectionHeader.svelte';

  const { model, open } = useTrip();
  const forecast = new WeatherModel();
  onDestroy(() => forecast.destroy());

  const places = $derived(model.dossier ? destinations(model.dossier, model.travelerFilter) : []);

  $effect(() => {
    forecast.track(model.dossier);
  });
  // Fetched when the screen opens, when the stops change, and when online extras are turned on.
  $effect(() => {
    void settings.onlineExtras;
    void forecast.stops.length;
    void forecast.refresh();
  });

  const fetchedNothing = $derived(forecast.stops.every((s) => s.days.length === 0));
  const basis = $derived(new Set(forecast.stops.flatMap((s) => s.days.map((d) => d.basis))));
  const footnote = $derived(
    [
      basis.has('FORECAST') ? 'Forecasts from Open-Meteo.' : '',
      basis.has('LAST_YEAR')
        ? 'Beyond two weeks there is no forecast, so those days show the same dates last year.'
        : '',
      forecast.note ?? ''
    ]
      .filter((s) => s !== '')
      .join(' ') || 'From Open-Meteo, kept on the device for when there is no signal.'
  );
</script>

<section class="screen" aria-labelledby="notes-title">
  <header class="screen-head">
    <button
      type="button"
      class="btn-icon"
      aria-label="Back"
      onclick={() => ui.go({ name: 'trip', tripId: model.tripId })}
    >
      <Icon name="arrowLeft" />
    </button>
    <h1 class="t-card-title" id="notes-title">On arrival</h1>
  </header>

  <div class="tab-body">
    {#if places.length === 0}
      <div class="panel faint">
        <p class="t-hint">
          Add two bookings and Waymark can work out where this trip actually spends its time.
        </p>
      </div>
    {:else}
      <SectionHeader label="Weather" />
      {#if !settings.onlineExtras && fetchedNothing}
        <div class="panel faint">
          <p class="t-hint">
            The forecast where the trip will be, fetched only if you let Waymark go online.
          </p>
        </div>
        <button type="button" class="btn btn-secondary btn-block" onclick={() => open({ name: 'online' })}>
          Show the weather
        </button>
      {:else}
        {#if forecast.stops.length === 0}
          <p class="t-hint">No days still to come with a place to ask about.</p>
        {/if}
        {#each forecast.stops as stop, index (index)}
          <div class="panel weather">
            <span class="t-field-label">{stopLabel(stop.stop)}</span>
            {#if stop.days.length === 0}
              <p class="t-hint">{forecast.updating ? 'Fetching…' : 'Not fetched yet.'}</p>
            {/if}
            {#each stop.days as day (day.date.toString())}
              <div class="weather-row">
                <span class="t-data-small dim">{dayCompact(day.date)}</span>
                <span class="t-body-small" class:accent={isWet(day.code)}>{describeWeather(day.code)}</span>
                <span class="t-data heading">{temperature(day.highC)} / {temperature(day.lowC)}</span>
                <span class="t-data-small dim"
                  >{day.rainChance !== null
                    ? `${day.rainChance}%`
                    : day.rainMm !== null
                      ? day.rainMm < 0.5
                        ? 'dry'
                        : `${Math.round(day.rainMm)} mm`
                      : ''}</span
                >
              </div>
              {#if day.basis === 'LAST_YEAR'}<span class="t-hint faint">last year</span>{/if}
            {/each}
          </div>
        {/each}
        <p class="t-hint">{footnote}</p>
        {#if settings.onlineExtras}
          <button
            type="button"
            class="btn btn-secondary btn-block"
            disabled={forecast.updating}
            onclick={() => forecast.refresh()}
          >
            {forecast.updating ? 'Updating…' : 'Update the weather'}
          </button>
        {/if}
      {/if}

      {#each places as stay, index (index)}
        {@const country = countryOf(stay.place)}
        <SectionHeader
          label={stay.place.city.trim() || stay.place.name}
          trailing={duration(stay.minutes)}
          emphasis={index === 0}
        />
        {#if !country}
          <div class="panel faint">
            <p class="t-hint">No country notes bundled for {stay.place.country.trim() || 'here'}.</p>
          </div>
        {:else}
          <div class="panel facts">
            <div>
              <span class="t-field-label">Money</span><span class="t-body-small"
                >{countryCurrency(country)}</span
              >
            </div>
            <div>
              <span class="t-field-label">Power</span><span class="t-body-small">{countryPower(country)}</span
              >
            </div>
            <div>
              <span class="t-field-label">Emergency</span><span class="t-body-small"
                >{country.emergencyNumber}</span
              >
            </div>
            <div>
              <span class="t-field-label">Roads</span><span class="t-body-small"
                >{countryDriving(country)}</span
              >
            </div>
          </div>
        {/if}
      {/each}

      <p class="t-hint faint">
        Country facts, bundled and offline. Several countries run separate police, fire and ambulance numbers;
        where one number reaches all three from a foreign handset, that is the one shown.
      </p>
    {/if}
  </div>
</section>
