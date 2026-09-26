import { HttpErrorResponse } from "@angular/common/http";
import {
  CreateOccurrenceRequest,
  Occurrence,
  ScheduleFormValue,
} from "./occurrence.model";

/**
 * En deçà, le back refuse de déplacer ou d'annuler une diffusion : l'ordonnanceur
 * peut la prendre en charge à tout instant (`FROZEN_HORIZON` côté back).
 */
export const FROZEN_MINUTES = 5;

/** `2026-10-03` : le jour d'une occurrence, lu tel quel dans son heure serveur. */
export function scheduledDay(occurrence: Occurrence): string {
  return occurrence.scheduledAt.slice(0, 10);
}

/** `18:30` : l'heure d'une occurrence, lue telle quelle dans son heure serveur. */
export function scheduledTime(occurrence: Occurrence): string {
  return occurrence.scheduledAt.slice(11, 16);
}

/**
 * Date du jour au format ISO dans le fuseau du navigateur : `toISOString()`
 * bascule en UTC et renverrait la veille entre minuit et l'aube, heure française.
 */
export function toIsoDate(date: Date): string {
  const month = `${ date.getMonth() + 1 }`.padStart(2, '0');
  const day = `${ date.getDate() }`.padStart(2, '0');

  return `${ date.getFullYear() }-${ month }-${ day }`;
}

/**
 * Une diffusion se déplace ou s'annule tant qu'aucun canal n'a été servi et
 * qu'elle n'est pas imminente — la règle que le back applique, reprise ici
 * pour ne proposer que des gestes qui aboutiront.
 */
export function isModifiable(occurrence: Occurrence, now: Date): boolean {
  if (occurrence.status !== 'SCHEDULED') {
    return false;
  }

  if (!occurrence.deliveries.every((delivery) => delivery.status === 'PENDING')) {
    return false;
  }

  // Sans suffixe de fuseau, la chaîne est lue en heure locale — celle du
  // serveur et du navigateur, tous deux en France.
  const scheduledAt = new Date(occurrence.scheduledAt).getTime();

  return scheduledAt > now.getTime() + FROZEN_MINUTES * 60_000;
}

/** Motif du premier canal en échec, s'il y en a un. */
export function failureReason(occurrence: Occurrence): string | null {
  return occurrence.deliveries.find((delivery) => delivery.status === 'FAILED')?.errorMessage ?? null;
}

/** Heure vide : programmation « pour un jour », heure choisie par le back. */
export function toCreateOccurrenceRequest(publicationId: number, value: ScheduleFormValue): CreateOccurrenceRequest {
  return value.time
    ? { publicationId, channels: value.channels, scheduledAt: `${ value.date }T${ value.time }:00` }
    : { publicationId, channels: value.channels, date: value.date };
}

/**
 * Le back explique ses refus (fenêtre du jour close, diffusion imminente…) dans
 * `message` : c'est l'information utile, préférée au message générique.
 */
export function apiErrorMessage(err: HttpErrorResponse, fallback: string): string {
  const reason = typeof err.error?.message === 'string' ? err.error.message : null;

  return reason ? `${ fallback } ${ reason }` : fallback;
}
