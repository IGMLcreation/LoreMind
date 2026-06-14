package com.loremind.domain.campaigncontext.ports;

/**
 * Port de sortie : indexation RAG d'une source de notebook (déléguée au Brain).
 * Les vecteurs vivent côté Brain, keyés par {@code sourceId}.
 */
public interface NotebookIndexer {

    /** Récapitulatif d'indexation renvoyé par le Brain. */
    record IndexResult(int chunks, int pageCount, int ocrPageCount) {}

    /** Indexe une source (extraction + embeddings + stockage vectoriel). */
    IndexResult index(String sourceId, byte[] pdfBytes, String filename);

    /** Supprime les vecteurs d'une source (au DELETE d'une source/notebook). */
    void delete(String sourceId);
}
