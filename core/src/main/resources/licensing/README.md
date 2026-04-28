# Cle publique JWT du relais OAuth Patreon

Le fichier `jwt-public-key.pem` contient la **cle publique Ed25519** qui sert
a verifier la signature des JWT licence emis par le relais
(`loremind-auth.igmlcreation.fr`).

## Pourquoi ici ?

- C'est une **cle publique** : par nature non-secrete, elle peut etre committee
  dans le repo public et embarquee dans le binaire distribue.
- Cela evite a chaque utilisateur final de devoir renseigner manuellement la
  cle dans son `.env` au moment de l'installation.
- L'env `LICENSING_JWT_PUBLIC_KEY` peut surcharger cette valeur (utile pour
  la rotation de cle sans rebuild ou pour les tests).

## Si le fichier est absent

La feature licensing est **desactivee silencieusement** : `LicenseService.isLicensingEnabled()`
renvoie `false`, et l'UI masque toute la section Patreon.

## Rotation de cle

1. Generer une nouvelle paire dans le relais : `npm run keys:generate`
2. Pousser la nouvelle cle privee : `wrangler secret put JWT_PRIVATE_KEY`
3. Remplacer `jwt-public-key.pem` ici avec la nouvelle cle publique
4. Rebuild + redeployer LoreMind (les anciens JWT seront refuses au prochain
   refresh, l'utilisateur sera invite a reconnecter Patreon)
5. Optionnel : pendant la transition, supporter les deux cles en parallele
   (pas implemente en MVP, peut etre ajoute si besoin operationnel)
