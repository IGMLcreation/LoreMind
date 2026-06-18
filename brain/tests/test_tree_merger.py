"""Tests du _TreeMerger de l'import de campagne (app.application.import_campaign).

Cœur du REDUCE : fusion par nom (insensible à la casse) des sous-arbres
arc→chapitre→scène→pièce produits morceau par morceau, + accumulation des PNJ.
"""
from __future__ import annotations

from app.application.import_campaign import _TreeMerger


def test_single_chunk_builds_full_tree():
    m = _TreeMerger()
    m.add([{
        "name": "Acte I", "description": "intro",
        "chapters": [{
            "name": "Ch1", "description": "d",
            "scenes": [{
                "name": "Sc1", "description": "s",
                "player_narration": "PN", "gm_notes": "GM",
                "rooms": [{"name": "R1", "description": "rd", "enemies": "gob", "loot": "or"}],
            }],
        }],
    }])
    arcs = m.result()
    assert len(arcs) == 1
    arc = arcs[0]
    assert arc.name == "Acte I"
    assert arc.arc_type == "LINEAR"
    sc = arc.chapters[0].scenes[0]
    assert sc.player_narration == "PN"
    assert sc.gm_notes == "GM"
    room = sc.rooms[0]
    assert (room.name, room.enemies, room.loot) == ("R1", "gob", "or")


def test_case_insensitive_arc_and_chapter_merge():
    m = _TreeMerger()
    m.add([{"name": "Acte I", "chapters": [{"name": "Ch1", "scenes": []}]}])
    m.add([{"name": "acte i", "chapters": [{"name": "ch1", "scenes": []},
                                           {"name": "Ch2", "scenes": []}]}])
    arcs = m.result()
    assert len(arcs) == 1
    assert {c.name for c in arcs[0].chapters} == {"Ch1", "Ch2"}


def test_description_first_non_empty_wins():
    m = _TreeMerger()
    m.add([{"name": "A", "description": "", "chapters": []}])
    m.add([{"name": "A", "description": "vraie", "chapters": []}])
    m.add([{"name": "A", "description": "autre", "chapters": []}])
    assert m.result()[0].description == "vraie"


def test_hub_type_wins_if_any_chunk_signals_it():
    m = _TreeMerger()
    m.add([{"name": "A", "type": "LINEAR", "chapters": []}])
    m.add([{"name": "A", "type": "HUB", "chapters": []}])
    assert m.result()[0].arc_type == "HUB"


def _scene(narr=None, gm=None):
    s = {"name": "S"}
    if narr is not None:
        s["player_narration"] = narr
    if gm is not None:
        s["gm_notes"] = gm
    return {"name": "A", "chapters": [{"name": "C", "scenes": [s]}]}


def test_scene_narration_concatenated_across_chunks():
    m = _TreeMerger()
    m.add([_scene(narr="début")])
    m.add([_scene(narr="suite")])
    sc = m.result()[0].chapters[0].scenes[0]
    assert sc.player_narration == "début\n\nsuite"


def test_scene_field_dedups_exact_overlap():
    m = _TreeMerger()
    m.add([_scene(gm="texte identique")])
    m.add([_scene(gm="texte identique")])
    assert m.result()[0].chapters[0].scenes[0].gm_notes == "texte identique"


def test_scene_field_takes_superset_version():
    m = _TreeMerger()
    m.add([_scene(gm="court")])
    m.add([_scene(gm="court et bien plus long")])
    assert m.result()[0].chapters[0].scenes[0].gm_notes == "court et bien plus long"


def test_npcs_longest_description_wins():
    m = _TreeMerger()
    m.add_npcs([{"name": "Thorin", "description": "court"}])
    m.add_npcs([{"name": "thorin", "description": "une description bien plus complète"}])
    npcs = m.npcs()
    assert len(npcs) == 1
    assert npcs[0].name == "Thorin"
    assert npcs[0].description == "une description bien plus complète"


def test_counts():
    m = _TreeMerger()
    m.add([{"name": "A", "chapters": [
        {"name": "C1", "scenes": [{"name": "S1"}, {"name": "S2"}]},
        {"name": "C2", "scenes": []},
    ]}])
    assert m.counts() == (1, 2, 2)


def test_blank_names_are_skipped():
    m = _TreeMerger()
    m.add([{"name": "", "chapters": []},
           {"name": "   ", "chapters": []},
           {"name": "OK", "chapters": [{"name": "", "scenes": []}]}])
    arcs = m.result()
    assert len(arcs) == 1
    assert arcs[0].name == "OK"
    assert arcs[0].chapters == []


def test_merge_chapters_consolidation():
    m = _TreeMerger()
    m.add([{"name": "A", "chapters": [
        {"name": "Intro", "scenes": [{"name": "S1"}]},
        {"name": "Introduction", "scenes": [{"name": "S2"}]},
    ]}])
    assert m.merge_chapters("Intro", ["Introduction"]) is True
    chapters = m.result()[0].chapters
    assert len(chapters) == 1
    assert {s.name for s in chapters[0].scenes} == {"S1", "S2"}


def test_merge_chapters_unknown_target_returns_false():
    m = _TreeMerger()
    m.add([{"name": "A", "chapters": [{"name": "Intro", "scenes": []}]}])
    assert m.merge_chapters("Inexistant", ["Intro"]) is False


def test_merge_scenes_consolidation():
    m = _TreeMerger()
    m.add([{"name": "A", "chapters": [{"name": "C", "scenes": [
        {"name": "Combat", "gm_notes": "x"},
        {"name": "Le combat", "gm_notes": "y"},
    ]}]}])
    assert m.merge_scenes("C", "Combat", ["Le combat"]) is True
    scenes = m.result()[0].chapters[0].scenes
    assert len(scenes) == 1
    assert scenes[0].name == "Combat"
