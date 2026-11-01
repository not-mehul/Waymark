<script lang="ts">
  // The trip, counted, and turned once on its axis. Ported from
  // AnalyticsScreen.kt.
  import { bookedShare, busyHours, EMISSION_FACTORS, hasDistance, tripReport } from '../../domain/analytics';
  import { bearingDegrees, distanceKm, formatDistance } from '../../domain/geo';
  import { destinationOf, positionOf, shortLabel } from '../../domain/model';
  import { todayIn, zoneOrUtc } from '../../domain/time';
  import { dayCompact, durationBetween } from '../../domain/time-text';
  import { ui } from '../../state/ui.svelte';
  import BarSeries from '../charts/BarSeries.svelte';
  import DayLoadChart from '../charts/DayLoadChart.svelte';
  import Meter from '../charts/Meter.svelte';
  import RingFigure from '../charts/RingFigure.svelte';
  import SplitBar from '../charts/SplitBar.svelte';
  import { useTrip } from '../trip/context';
  import Icon from '../ui/Icon.svelte';
  import SectionHeader from '../ui/SectionHeader.svelte';

  const { model } = useTrip();
  const report = $derived(model.dossier ? tripReport(model.dossier, model.ideas) : null);
  const today = $derived(todayIn(zoneOrUtc(model.dossier?.trip.homeZoneId), model.now));
  const dayLabel = new Intl.DateTimeFormat('en-GB', { day: 'numeric', month: 'short', timeZone: 'UTC' });
  const legs = $derived(
    (model.dossier?.segments ?? []).flatMap((s) => {
      const to = destinationOf(s);
      const a = positionOf(s.origin);
      const b = positionOf(to);
      return a && b && s.origin.name !== to.name ? [{ segment: s, to, a, b }] : [];
    })
  );
  const round = Math.round;
</script>

<section class="screen" aria-labelledby="numbers-title">
  <header class="screen-head">
    <button
      type="button"
      class="btn-icon"
      aria-label="Back"
      onclick={() => ui.go({ name: 'trip', tripId: model.tripId })}
    >
      <Icon name="arrowLeft" />
    </button>
    <h1 class="t-card-title" id="numbers-title">The numbers</h1>
  </header>

  <div class="tab-body">
    {#if !report || !hasDistance(report)}
      <p class="t-hint">Nothing to count yet. Add a flight and the numbers fill in.</p>
    {:else}
      <div class="numbers-head">
        <RingFigure
          fraction={report.flownKm / report.totalDistanceKm}
          figure={formatDistance(report.totalDistanceKm)}
          caption="Travelled"
        />
        <div class="numbers-stats">
          <div>
            <span class="t-stat accent">{report.countriesVisited.length}</span><span class="t-field-label"
              >Countries</span
            >
          </div>
          <div>
            <span class="t-stat">{report.clockChanges}</span><span class="t-field-label">Clock changes</span>
          </div>
          <div>
            <span class="t-stat">{report.dayLoads.length}</span><span class="t-field-label">Days</span>
          </div>
        </div>
      </div>

      {#if report.longestLegLabel}
        <div class="panel faint">
          <span class="t-field-label">Longest leg</span>
          <p class="t-card-title">{report.longestLegLabel}</p>
          <span class="t-data-small accent">{formatDistance(report.longestLegKm)}</span>
        </div>
      {/if}

      <SectionHeader label="How far, by what" />
      <div class="panel"><BarSeries measures={report.distanceByMode} /></div>

      <SectionHeader label="The shape of the days" />
      <div class="panel stack">
        <DayLoadChart
          days={report.dayLoads.map((load) => ({
            key: load.date.toString(),
            label: dayLabel.format(new Date(`${load.date.toString()}T12:00:00Z`)),
            moving: load.movingHours,
            booked: load.bookedHours,
            today: load.date.equals(today)
          }))}
        />
        <hr class="hairline" />
        {#if report.busiestDay}
          <p class="t-body-small muted">
            Busiest: {dayCompact(report.busiestDay.date)}, {round(busyHours(report.busiestDay))} hours accounted
            for.
          </p>
        {/if}
        {#if report.quietestDay}
          <p class="t-hint">
            Quietest: {dayCompact(report.quietestDay.date)}, {round(busyHours(report.quietestDay))} hours.
          </p>
        {/if}
      </div>

      <SectionHeader label="Where the time goes" />
      <div class="panel stack">
        <SplitBar
          parts={[
            { label: `Moving ${round(report.hoursMoving)}h`, value: report.hoursMoving },
            { label: `Booked ${round(report.hoursBooked)}h`, value: report.hoursBooked },
            { label: `Yours ${round(report.hoursAtRest)}h`, value: report.hoursAtRest }
          ]}
        />
        <Meter
          fraction={bookedShare(report)}
          label="Waking hours with something on them"
          value="{round(bookedShare(report) * 100)}%"
        />
      </div>

      {#if report.nightsByCity.length > 0}
        <SectionHeader label="Nights, by place" />
        <div class="panel"><BarSeries measures={report.nightsByCity} /></div>
      {/if}

      <SectionHeader label="The list" />
      {@const considered = report.ideasDone + report.ideasScheduled + report.ideasOutstanding}
      <div class="panel stack">
        <div class="stats spread">
          <div>
            <span class="t-stat accent">{report.ideasDone}</span><span class="t-field-label">Done</span>
          </div>
          <div>
            <span class="t-stat">{report.ideasScheduled}</span><span class="t-field-label">Scheduled</span>
          </div>
          <div>
            <span class="t-stat">{report.ideasOutstanding}</span><span class="t-field-label">Still on it</span
            >
          </div>
        </div>
        <Meter
          fraction={considered === 0 ? 0 : report.ideasDone / considered}
          label="Ticked off"
          value="{report.ideasDone} of {considered}"
        />
      </div>

      {#if legs.length > 0}
        <SectionHeader label="Every leg" />
        <div class="panel leg-list">
          {#each legs as leg (leg.segment.id)}
            <div class="leg-row">
              <div class="bar-head">
                <span class="t-body-small">{shortLabel(leg.segment.origin)} → {shortLabel(leg.to)}</span>
                <span class="t-data-small dim">{formatDistance(distanceKm(leg.segment.origin, leg.to))}</span>
              </div>
              <span class="t-hint faint"
                >{durationBetween(leg.segment.startEpochMillis, leg.segment.endEpochMillis)} · {Math.trunc(
                  bearingDegrees(leg.a, leg.b)
                )}°</span
              >
            </div>
          {/each}
        </div>
      {/if}

      <SectionHeader label="Carbon" />
      <div class="panel stack">
        <p class="t-section-heading">{round(report.carbonKg)} kg CO₂e</p>
        <p class="t-hint">
          A model, not a measurement: distance times a published factor per mode, {round(
            EMISSION_FACTORS.LONG_HAUL_FLIGHT * 1000
          )} g per passenger-kilometre long-haul, {round(EMISSION_FACTORS.TRAIN * 1000)} g by train. Lodging and
          meals are excluded rather than guessed at.
        </p>
      </div>

      <p class="t-hint faint">Length carries magnitude; labels carry identity.</p>
    {/if}
  </div>
</section>
