import { Routes } from '@angular/router';

export const routes: Routes = [
  { path: 'lore', loadComponent: () => import('./lore/lore.component').then(m => m.LoreComponent) },
  { path: 'lore/:id', loadComponent: () => import('./lore/lore-detail/lore-detail.component').then(m => m.LoreDetailComponent) },
  { path: 'lore/:loreId/nodes/create', loadComponent: () => import('./lore/lore-node-create/lore-node-create.component').then(m => m.LoreNodeCreateComponent) },
  { path: 'lore/:loreId/folders/:parentId/create', loadComponent: () => import('./lore/lore-node-create/lore-node-create.component').then(m => m.LoreNodeCreateComponent) },
  { path: 'lore/:loreId/folders/:folderId/edit', loadComponent: () => import('./lore/lore-node-edit/lore-node-edit.component').then(m => m.LoreNodeEditComponent) },
  { path: 'lore/:loreId/templates/create', loadComponent: () => import('./lore/template-create/template-create.component').then(m => m.TemplateCreateComponent) },
  { path: 'lore/:loreId/templates/:templateId', loadComponent: () => import('./lore/template-edit/template-edit.component').then(m => m.TemplateEditComponent) },
  { path: 'lore/:loreId/pages/create', loadComponent: () => import('./lore/page-create/page-create.component').then(m => m.PageCreateComponent) },
  { path: 'lore/:loreId/nodes/:nodeId/pages/create', loadComponent: () => import('./lore/page-create/page-create.component').then(m => m.PageCreateComponent) },
  { path: 'lore/:loreId/pages/:pageId', loadComponent: () => import('./lore/page-view/page-view.component').then(m => m.PageViewComponent) },
  { path: 'lore/:loreId/pages/:pageId/edit', loadComponent: () => import('./lore/page-edit/page-edit.component').then(m => m.PageEditComponent) },
  { path: 'campaigns', loadComponent: () => import('./campaigns/campaigns.component').then(m => m.CampaignsComponent) },
  { path: 'campaigns/:id', loadComponent: () => import('./campaigns/campaign-detail/campaign-detail.component').then(m => m.CampaignDetailComponent) },
  { path: 'campaigns/:campaignId/arcs/create', loadComponent: () => import('./campaigns/arc-create/arc-create.component').then(m => m.ArcCreateComponent) },
  { path: 'campaigns/:campaignId/arcs/:arcId', loadComponent: () => import('./campaigns/arc-view/arc-view.component').then(m => m.ArcViewComponent) },
  { path: 'campaigns/:campaignId/arcs/:arcId/edit', loadComponent: () => import('./campaigns/arc-edit/arc-edit.component').then(m => m.ArcEditComponent) },
  { path: 'campaigns/:campaignId/arcs/:arcId/chapters/create', loadComponent: () => import('./campaigns/chapter-create/chapter-create.component').then(m => m.ChapterCreateComponent) },
  { path: 'campaigns/:campaignId/arcs/:arcId/chapters/:chapterId', loadComponent: () => import('./campaigns/chapter-view/chapter-view.component').then(m => m.ChapterViewComponent) },
  { path: 'campaigns/:campaignId/arcs/:arcId/chapters/:chapterId/graph', loadComponent: () => import('./campaigns/chapter-graph/chapter-graph.component').then(m => m.ChapterGraphComponent) },
  { path: 'campaigns/:campaignId/arcs/:arcId/chapters/:chapterId/edit', loadComponent: () => import('./campaigns/chapter-edit/chapter-edit.component').then(m => m.ChapterEditComponent) },
  { path: 'campaigns/:campaignId/arcs/:arcId/chapters/:chapterId/scenes/create', loadComponent: () => import('./campaigns/scene-create/scene-create.component').then(m => m.SceneCreateComponent) },
  { path: 'campaigns/:campaignId/arcs/:arcId/chapters/:chapterId/scenes/:sceneId', loadComponent: () => import('./campaigns/scene-view/scene-view.component').then(m => m.SceneViewComponent) },
  { path: 'campaigns/:campaignId/arcs/:arcId/chapters/:chapterId/scenes/:sceneId/edit', loadComponent: () => import('./campaigns/scene-edit/scene-edit.component').then(m => m.SceneEditComponent) },
  { path: 'settings', loadComponent: () => import('./settings/settings.component').then(m => m.SettingsComponent) },
  { path: '', redirectTo: '/lore', pathMatch: 'full' }
];
