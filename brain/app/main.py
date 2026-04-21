"""Point d'entrée FastAPI du Brain LoreMind.

Controller volontairement FIN : il valide l'entrée (DTOs Pydantic), délègue
au domaine via injection de dépendance (ports + use cases), et transforme les
erreurs du domaine en réponses HTTP. Aucune connaissance d'Ollama ici.
"""
import json
from typing import Annotated, AsyncIterator, Literal

import hmac
import httpx
from fastapi import Depends, FastAPI, HTTPException, Request
from fastapi.responses import JSONResponse, StreamingResponse
from pydantic import BaseModel, Field

from app.application.chat import ChatUseCase
from app.application.generate_page import GeneratePageUseCase
from app.core.config import Settings, get_settings
from app.core.settings_store import save_overrides
from app.domain.models import (
    ArcSummary,
    CampaignStructuralContext,
    ChapterSummary,
    ChatMessage,
    LoreStructuralContext,
    NarrativeEntityContext,
    PageContext,
    PageGenerationContext,
    PageSummary,
    SceneBranchHint,
    SceneSummary,
)
from app.domain.ports import LLMProvider, LLMProviderError
from app.infrastructure.ollama_adapter import OllamaLLMProvider
from app.infrastructure.onemin_adapter import OneMinAiLLMProvider

app = FastAPI(
    title="LoreMind Brain",
    description="Backend IA pour la génération de contenu narratif.",
    version="0.2.0",
)


# Chemins exemptes d'auth inter-service : healthcheck docker + introspection
# FastAPI (docs uniquement utiles en dev ; en prod docker-compose, le Brain
# n'est pas expose en dehors du reseau interne donc pas un risque).
_PUBLIC_PATHS = frozenset({"/health", "/docs", "/redoc", "/openapi.json"})


@app.middleware("http")
async def require_internal_secret(request: Request, call_next):
    """Refuse toute requete qui ne presente pas le secret partage core<->brain.

    Fail-closed : si `INTERNAL_SHARED_SECRET` n'est pas configure cote Brain,
    TOUTES les requetes non-publiques sont rejetees. Force la configuration
    explicite en prod et empeche un deploiement par defaut non-authentifie.

    Comparaison en temps-constant via `hmac.compare_digest` pour eviter les
    attaques par timing side-channel sur la validation du secret.
    """
    if request.url.path in _PUBLIC_PATHS:
        return await call_next(request)

    expected = get_settings().internal_shared_secret
    provided = request.headers.get("x-internal-secret", "")
    if not expected or not hmac.compare_digest(expected, provided):
        return JSONResponse(
            {"detail": "Unauthorized: invalid or missing X-Internal-Secret"},
            status_code=401,
        )
    return await call_next(request)


# --- DTOs HTTP (frontière, c'est ici et seulement ici qu'on utilise Pydantic) ---


class GenerateRequest(BaseModel):
    prompt: str


class GenerateResponse(BaseModel):
    model: str
    response: str


class GeneratePageRequestDTO(BaseModel):
    """Contexte envoyé par le Core Java pour remplir une page via le LLM."""

    lore_name: str
    folder_name: str
    template_name: str
    template_fields: list[str] = Field(min_length=1)
    page_title: str
    lore_description: str | None = None


class GeneratePageResponseDTO(BaseModel):
    """Retour : une valeur textuelle par champ du template (clé = field name)."""

    values: dict[str, str]


class ChatMessageDTO(BaseModel):
    """Un message de la conversation. Rôles acceptés : user, assistant, system."""

    role: str = Field(pattern="^(user|assistant|system)$")
    content: str


class PageSummaryDTO(BaseModel):
    """Résumé enrichi d'une page : identité + contenu + interconnexions.

    Depuis b9 : values/tags/related_page_titles sont optionnels côté JSON —
    le Core Java ne les sérialise que s'ils sont non-vides (payload léger
    pour un Lore avec beaucoup de pages vierges).
    """

    title: str
    template_name: str
    values: dict[str, str] = Field(default_factory=dict)
    tags: list[str] = Field(default_factory=list)
    related_page_titles: list[str] = Field(default_factory=list)


class LoreContextDTO(BaseModel):
    """Carte structurelle du Lore avec contenu des pages (b9+)."""

    lore_name: str
    lore_description: str | None = None
    folders: dict[str, list[PageSummaryDTO]] = Field(default_factory=dict)
    tags: list[str] = Field(default_factory=list)


class PageContextDTO(BaseModel):
    """Contexte d'une page spécifique pour focaliser le chat (optionnel)."""

    title: str
    template_name: str
    template_fields: list[str] = Field(default_factory=list)
    values: dict[str, str] = Field(default_factory=dict)


class SceneBranchHintDTO(BaseModel):
    """Indice d'une branche narrative (le Core a deja resolu le nom cible)."""

    label: str
    target_scene_name: str
    condition: str | None = None


class SceneSummaryDTO(BaseModel):
    """Résumé d'une scène : nom + description courte (synopsis)."""

    name: str
    description: str | None = None
    # Optionnel : le Core Java ne serialise illustration_count QUE si > 0
    # (payload plus leger). Defaut 0 = pas d'illustrations ou champ absent.
    illustration_count: int = 0
    # Branches narratives sortantes, omises cote Core si vides.
    branches: list[SceneBranchHintDTO] = Field(default_factory=list)


class ChapterSummaryDTO(BaseModel):
    """Résumé d'un chapitre : nom + description courte + ses scènes."""

    name: str
    description: str | None = None
    scenes: list[SceneSummaryDTO] = Field(default_factory=list)
    illustration_count: int = 0


class ArcSummaryDTO(BaseModel):
    """Résumé d'un arc narratif : nom + description courte + ses chapitres."""

    name: str
    description: str | None = None
    chapters: list[ChapterSummaryDTO] = Field(default_factory=list)
    illustration_count: int = 0


class CampaignContextDTO(BaseModel):
    """Carte narrative enrichie : arcs → chapitres → scènes avec synopsis."""

    campaign_name: str
    campaign_description: str | None = None
    arcs: list[ArcSummaryDTO] = Field(default_factory=list)


class NarrativeEntityDTO(BaseModel):
    """Entité narrative (arc/chapter/scene) en cours d'édition — focus optionnel."""

    entity_type: str = Field(pattern="^(arc|chapter|scene)$")
    title: str
    fields: dict[str, str] = Field(default_factory=dict)


class ChatStreamRequestDTO(BaseModel):
    """Requête de chat streamé : historique + contextes structurels.

    Les 4 contextes (lore, page, campaign, narrative_entity) sont optionnels,
    mais au moins l'un des deux "niveaux haut" (lore_context ou
    campaign_context) doit être fourni. Le validateur `check_scope` applique
    cette règle à la frontière HTTP.
    """

    messages: list[ChatMessageDTO] = Field(min_length=1)
    lore_context: LoreContextDTO | None = None
    page_context: PageContextDTO | None = None
    campaign_context: CampaignContextDTO | None = None
    narrative_entity: NarrativeEntityDTO | None = None

    def has_scope(self) -> bool:
        """Vrai si au moins un contexte racine (Lore ou Campagne) est fourni."""
        return self.lore_context is not None or self.campaign_context is not None


# --- Factories d'injection de dépendance ---


def get_llm_provider(
    settings: Annotated[Settings, Depends(get_settings)],
) -> LLMProvider:
    """Factory d'adapter — point d'inversion de dépendance.

    C'est ici (et uniquement ici) qu'on choisit QUEL adapter concret
    incarne le port, en fonction du champ `llm_provider` des Settings
    (modifiable a chaud depuis l'ecran Parametres de l'UI).
    """
    try:
        if settings.llm_provider == "onemin":
            return OneMinAiLLMProvider(settings)
        return OllamaLLMProvider(settings)
    except LLMProviderError as exc:
        # Ex : cle 1min.ai manquante. On renvoie du 400 plutot que du 500
        # pour que le frontend puisse afficher un message actionnable.
        raise HTTPException(status_code=400, detail=str(exc)) from exc


def get_generate_page_use_case(
    llm: Annotated[LLMProvider, Depends(get_llm_provider)],
) -> GeneratePageUseCase:
    """Factory du use case — injecte le port LLMProvider sans connaître l'adapter."""
    return GeneratePageUseCase(llm=llm)


def get_chat_use_case(
    llm: Annotated[LLMProvider, Depends(get_llm_provider)],
) -> ChatUseCase:
    """Factory du use case chat.

    L'adapter OllamaLLMProvider satisfait les deux protocoles (LLMProvider
    et LLMChatProvider) par duck typing ; on lui passe la même instance.
    """
    return ChatUseCase(llm=llm)  # type: ignore[arg-type]


# --- Endpoints ---


@app.get("/health")
def health() -> dict[str, str]:
    """Sonde de santé — permet au Core Java de vérifier que le Brain répond."""
    return {"status": "ok", "service": "brain"}


@app.post("/generate", response_model=GenerateResponse)
async def generate(
    body: GenerateRequest,
    settings: Annotated[Settings, Depends(get_settings)],
    llm: Annotated[LLMProvider, Depends(get_llm_provider)],
) -> GenerateResponse:
    """Endpoint libre : prompt → texte brut. Utile pour debug et exploration."""
    try:
        text = await llm.generate(body.prompt)
    except LLMProviderError as exc:
        raise HTTPException(status_code=502, detail=str(exc)) from exc

    return GenerateResponse(model=settings.llm_model, response=text)


@app.post("/generate-page", response_model=GeneratePageResponseDTO)
async def generate_page(
    body: GeneratePageRequestDTO,
    use_case: Annotated[
        GeneratePageUseCase, Depends(get_generate_page_use_case)
    ],
) -> GeneratePageResponseDTO:
    """Endpoint métier : contexte LoreMind → valeurs structurées par champ.

    Branche tout le use case `GeneratePageUseCase`. Ce controller ne fait
    que le mapping DTO ↔ dataclass et la traduction d'erreur domaine → HTTP.
    """
    context = PageGenerationContext(
        lore_name=body.lore_name,
        lore_description=body.lore_description,
        folder_name=body.folder_name,
        template_name=body.template_name,
        template_fields=body.template_fields,
        page_title=body.page_title,
    )

    try:
        result = await use_case.execute(context)
    except LLMProviderError as exc:
        raise HTTPException(status_code=502, detail=str(exc)) from exc

    return GeneratePageResponseDTO(values=result.values)


@app.post("/chat/stream")
async def chat_stream(
    body: ChatStreamRequestDTO,
    use_case: Annotated[ChatUseCase, Depends(get_chat_use_case)],
) -> StreamingResponse:
    """Chat streamé (Server-Sent Events) avec Structural Context.

    Accepte jusqu'à 4 contextes optionnels (Lore, Page focalisée, Campagne,
    entité narrative focalisée). Au moins un contexte racine (Lore ou
    Campagne) est requis pour que la requête ait du sens.

    Format de flux :
      - Chaque token : `data: {"token": "..."}\\n\\n`
      - Fin normale  : `event: done\\ndata: {}\\n\\n`
      - Erreur LLM   : `event: error\\ndata: {"message": "..."}\\n\\n`
    """
    if not body.has_scope():
        raise HTTPException(
            status_code=422,
            detail="Au moins un des deux contextes racines (lore_context ou campaign_context) est requis.",
        )

    messages = [ChatMessage(role=m.role, content=m.content) for m in body.messages]
    lore_context = _to_lore_context(body.lore_context)
    page_context = _to_page_context(body.page_context)
    campaign_context = _to_campaign_context(body.campaign_context)
    narrative_entity = _to_narrative_entity(body.narrative_entity)

    async def event_stream() -> AsyncIterator[str]:
        try:
            async for token in use_case.stream(
                messages,
                lore_context=lore_context,
                page_context=page_context,
                campaign_context=campaign_context,
                narrative_entity=narrative_entity,
            ):
                # json.dumps avec ensure_ascii=False pour préserver les accents
                yield f"data: {json.dumps({'token': token}, ensure_ascii=False)}\n\n"
            yield "event: done\ndata: {}\n\n"
        except LLMProviderError as exc:
            yield f"event: error\ndata: {json.dumps({'message': str(exc)})}\n\n"

    return StreamingResponse(event_stream(), media_type="text/event-stream")


# --- Mapping DTO → domaine (frontière HTTP) ---------------------------------


def _to_lore_context(dto: LoreContextDTO | None) -> LoreStructuralContext | None:
    if dto is None:
        return None
    return LoreStructuralContext(
        lore_name=dto.lore_name,
        lore_description=dto.lore_description,
        folders={
            folder: [_to_page_summary(p) for p in pages]
            for folder, pages in dto.folders.items()
        },
        tags=dto.tags,
    )


def _to_page_summary(dto: PageSummaryDTO) -> PageSummary:
    return PageSummary(
        title=dto.title,
        template_name=dto.template_name,
        values=dict(dto.values),
        tags=list(dto.tags),
        related_page_titles=list(dto.related_page_titles),
    )


def _to_page_context(dto: PageContextDTO | None) -> PageContext | None:
    if dto is None:
        return None
    return PageContext(
        title=dto.title,
        template_name=dto.template_name,
        template_fields=dto.template_fields,
        values=dto.values,
    )


def _to_campaign_context(dto: CampaignContextDTO | None) -> CampaignStructuralContext | None:
    if dto is None:
        return None
    arcs = [
        ArcSummary(
            name=arc.name,
            description=arc.description,
            illustration_count=arc.illustration_count,
            chapters=[
                ChapterSummary(
                    name=ch.name,
                    description=ch.description,
                    illustration_count=ch.illustration_count,
                    scenes=[
                        SceneSummary(
                            name=sc.name,
                            description=sc.description,
                            illustration_count=sc.illustration_count,
                            branches=[
                                SceneBranchHint(
                                    label=br.label,
                                    target_scene_name=br.target_scene_name,
                                    condition=br.condition,
                                )
                                for br in sc.branches
                            ],
                        )
                        for sc in ch.scenes
                    ],
                )
                for ch in arc.chapters
            ],
        )
        for arc in dto.arcs
    ]
    return CampaignStructuralContext(
        campaign_name=dto.campaign_name,
        campaign_description=dto.campaign_description,
        arcs=arcs,
    )


# --- Settings (parametrage runtime depuis l'UI) ------------------------------


class SettingsDTO(BaseModel):
    """Vue serialisable des settings modifiables depuis l'UI.

    Expose uniquement les champs que l'utilisateur peut changer a chaud.
    Les secrets (onemin_api_key) sont masques en lecture.
    """

    llm_provider: Literal["ollama", "onemin"]
    ollama_base_url: str
    llm_model: str
    onemin_model: str
    # True si une cle 1min.ai est deja configuree — pas de leak de la cle elle-meme.
    onemin_api_key_set: bool


class SettingsUpdateDTO(BaseModel):
    """Patch partiel des settings. Tous les champs sont optionnels."""

    llm_provider: Literal["ollama", "onemin"] | None = None
    ollama_base_url: str | None = None
    llm_model: str | None = None
    onemin_model: str | None = None
    # Chaine vide => on efface la cle. None => pas de changement.
    onemin_api_key: str | None = None


def _to_settings_dto(s: Settings) -> SettingsDTO:
    return SettingsDTO(
        llm_provider=s.llm_provider,
        ollama_base_url=s.ollama_base_url,
        llm_model=s.llm_model,
        onemin_model=s.onemin_model,
        onemin_api_key_set=bool(s.onemin_api_key),
    )


@app.get("/settings", response_model=SettingsDTO)
def read_settings(settings: Annotated[Settings, Depends(get_settings)]) -> SettingsDTO:
    """Retourne la config courante (secrets masques)."""
    return _to_settings_dto(settings)


@app.put("/settings", response_model=SettingsDTO)
def update_settings(patch: SettingsUpdateDTO) -> SettingsDTO:
    """Applique un patch partiel aux settings et persiste les overrides.

    Toute requete HTTP suivante verra les nouvelles valeurs (pas de cache).
    """
    overrides = {k: v for k, v in patch.model_dump().items() if v is not None}
    if overrides:
        save_overrides(overrides)
    # Relit .env + overrides fusionnes pour confirmation.
    return _to_settings_dto(get_settings())


@app.get("/models/ollama")
async def list_ollama_models(
    settings: Annotated[Settings, Depends(get_settings)],
) -> dict[str, list[str]]:
    """Liste les modeles disponibles sur le serveur Ollama configure.

    Retourne une liste vide si Ollama est injoignable — l'UI affichera un
    message plutot qu'une 500.
    """
    url = f"{settings.ollama_base_url}/api/tags"
    try:
        async with httpx.AsyncClient(timeout=5) as client:
            response = await client.get(url)
            response.raise_for_status()
            data = response.json()
    except httpx.HTTPError:
        return {"models": []}
    models = [m.get("name", "") for m in data.get("models", []) if m.get("name")]
    return {"models": sorted(models)}


@app.get("/models/onemin")
def list_onemin_models() -> dict[str, list[dict[str, object]]]:
    """Catalogue statique des modeles 1min.ai, groupes par fournisseur.

    Liste construite par probing direct de l'endpoint chat-with-ai avec
    une vraie cle API (avril 2026) : chaque ID renvoie 200, les IDs
    absents renvoient 400 UNSUPPORTED_MODEL.

    Nota : les IDs Anthropic utilisent la nomenclature propre a 1min.ai
    (`claude-<family>-<version>`), pas la convention officielle Anthropic.
    """
    return {
        "groups": [
            {
                "provider": "Anthropic",
                "models": ["claude-opus-4-6", "claude-sonnet-4-6"],
            },
            {
                "provider": "OpenAI",
                "models": [
                    "gpt-5",
                    "gpt-5-mini",
                    "gpt-5-nano",
                    "gpt-4.1",
                    "gpt-4.1-mini",
                    "gpt-4.1-nano",
                    "gpt-4o",
                    "gpt-4o-mini",
                    "gpt-4-turbo",
                    "gpt-3.5-turbo",
                    "o3",
                    "o3-pro",
                    "o3-mini",
                    "o4-mini",
                ],
            },
            {
                "provider": "Google",
                "models": ["gemini-2.5-pro", "gemini-2.5-flash"],
            },
            {
                "provider": "Mistral",
                "models": [
                    "mistral-large-latest",
                    "mistral-medium-latest",
                    "mistral-small-latest",
                    "open-mistral-nemo",
                ],
            },
            {
                "provider": "DeepSeek",
                "models": ["deepseek-chat", "deepseek-reasoner"],
            },
            {
                "provider": "xAI",
                "models": ["grok-3", "grok-3-mini"],
            },
            {
                "provider": "Meta",
                "models": [
                    "meta/meta-llama-3.1-405b-instruct",
                    "meta/meta-llama-3-70b-instruct",
                ],
            },
            {
                "provider": "Alibaba",
                "models": ["qwen-plus", "qwen3-max"],
            },
            {
                "provider": "Perplexity",
                "models": ["sonar", "sonar-pro"],
            },
        ]
    }


def _to_narrative_entity(dto: NarrativeEntityDTO | None) -> NarrativeEntityContext | None:
    if dto is None:
        return None
    return NarrativeEntityContext(
        entity_type=dto.entity_type,
        title=dto.title,
        fields=dict(dto.fields),
    )
