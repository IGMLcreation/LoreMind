package com.loremind.infrastructure.transfer.pdf;

/** Image re-encodee prete a inliner : data-URI + dimensions reelles (pour l'aspect). */
record PdfImage(String uri, int w, int h) {}
