// Interface TypeScript pour ImageDTO (Backend Java).
// Miroir de com.loremind.infrastructure.web.dto.images.ImageDTO.

export interface Image {
  id: string;
  filename: string;
  contentType: string;
  sizeBytes: number;
  /**
   * URL relative du binaire, ex: "/api/images/42/content".
   * Le front prefixe avec ApiBase pour construire l'URL absolue.
   */
  url: string;
  uploadedAt: string;
}
