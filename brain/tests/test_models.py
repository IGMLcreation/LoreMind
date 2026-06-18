"""Tests de la logique portée par les modèles de domaine (app.domain.models)."""
from __future__ import annotations

from app.domain.models import (
    ArcProposal,
    CampaignImportResult,
    ChapterProposal,
    ExtractedDocument,
    ExtractedPage,
    RulesImportResult,
    SceneProposal,
)


def test_extracted_document_properties():
    doc = ExtractedDocument(pages=[
        ExtractedPage(index=0, text="page un", used_ocr=False),
        ExtractedPage(index=1, text="page deux", used_ocr=True),
        ExtractedPage(index=2, text="   ", used_ocr=False),  # vide → exclue de full_text
    ])
    assert doc.page_count == 3
    assert doc.ocr_page_count == 1
    assert doc.full_text == "page un\n\npage deux"


def test_rules_import_result_to_markdown():
    result = RulesImportResult(
        sections={"Combat": "règles de combat", "Magie": "règles de magie"},
        page_count=10, ocr_page_count=0,
    )
    md = result.to_markdown()
    assert "## Combat\n\nrègles de combat" in md
    assert "## Magie\n\nrègles de magie" in md
    assert md.endswith("\n")


def test_campaign_import_result_counts():
    arcs = [
        ArcProposal("A1", "", chapters=[
            ChapterProposal("C1", "", scenes=[SceneProposal("S1", ""), SceneProposal("S2", "")]),
            ChapterProposal("C2", "", scenes=[SceneProposal("S3", "")]),
        ]),
        ArcProposal("A2", "", chapters=[]),
    ]
    result = CampaignImportResult(arcs=arcs, page_count=1, ocr_page_count=0)
    assert result.counts() == (2, 2, 3)


def test_arc_proposal_defaults():
    arc = ArcProposal("Acte", "synopsis")
    assert arc.arc_type == "LINEAR"
    assert arc.chapters == []
