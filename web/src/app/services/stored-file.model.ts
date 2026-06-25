// Interface TypeScript pour StoredFileDTO (Backend Java).
// Miroir de com.loremind.infrastructure.web.dto.files.StoredFileDTO.
// Fichier generique (battlemap : media video/image + sidecar JSON Universal VTT).

export interface StoredFile {
  id: string;
  filename: string;
  contentType: string;
  sizeBytes: number;
  /** URL relative du binaire, ex: "/api/files/42/content". */
  url: string;
  uploadedAt: string;
}
