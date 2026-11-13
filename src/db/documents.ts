// Tickets and documents kept with a trip: a boarding pass picture, a PDF
// e-ticket, a voucher. They live on the phone like everything else, so they
// open at the gate with the radio off.

import type { TripDocument } from '../domain/model';
import type { WaymarkDb } from './database';
import { newId } from './trips';

/** Bigger than any boarding pass or e-ticket; a file this size is something else. */
export const MAX_DOCUMENT_BYTES = 12 * 1024 * 1024;

export class DocumentRepository {
  constructor(private readonly db: WaymarkDb) {}

  async forTrip(tripId: string): Promise<TripDocument[]> {
    return this.db.documents.where('tripId').equals(tripId).sortBy('addedAtMillis');
  }

  async forSegment(segmentId: string): Promise<TripDocument[]> {
    return this.db.documents.where('segmentId').equals(segmentId).sortBy('addedAtMillis');
  }

  async get(id: string): Promise<TripDocument | null> {
    return (await this.db.documents.get(id)) ?? null;
  }

  async add(
    fields: Omit<TripDocument, 'id' | 'addedAtMillis'> & Partial<Pick<TripDocument, 'addedAtMillis'>>
  ): Promise<TripDocument> {
    const document: TripDocument = { id: newId('doc'), addedAtMillis: Date.now(), ...fields };
    await this.db.documents.put(document);
    return document;
  }

  async rename(id: string, name: string): Promise<void> {
    await this.db.documents.update(id, { name });
  }

  async remove(id: string): Promise<void> {
    await this.db.documents.delete(id);
  }

  /** Puts one back exactly as it was, for Undo. */
  async restore(document: TripDocument): Promise<void> {
    await this.db.documents.put(document);
  }
}
