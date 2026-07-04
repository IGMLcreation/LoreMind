package com.loremind.infrastructure.transfer;

import com.loremind.infrastructure.transfer.dto.ContentExport;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.InputStream;

/**
 * Façade d'IMPORT de contenu en mode FUSION.
 * <p>
 * On NE remplace PAS l'existant : chaque entite importee est reinseree avec un
 * NOUVEL id auto-genere, et toutes les references (FK Long et refs faibles
 * String) sont remappees oldId -> newId. Cela permet d'agreger plusieurs exports
 * dans une meme base sans collision, entre Postgres et H2.
 * <p>
 * Algorithme, chaque phase etant portee par un composant dedie :
 * <ol>
 *   <li>{@link ImportArchiveParser} : {@code data.json} -> {@link ContentExport},
 *       binaires gardes en memoire.</li>
 *   <li>{@link ImageImporter} / {@link StoredFileImporter} : reecrit les binaires
 *       sous LEUR CLE D'ORIGINE (pas de remapping de cle) ; skip si la cle existe deja.</li>
 *   <li>Insertion top-down en construisant les maps de remapping par type
 *       ({@link ImportIdMaps}) : {@link LoreContentInserter} (referentiel lore + systemes),
 *       {@link CampaignContentInserter} (contenu de campagne, quetes legacy comprises),
 *       {@link PlayStateInserter} (etat de jeu).</li>
 *   <li>{@link ImportReferenceRemapper} : 2e passe, remappe les references qui pointent
 *       vers des types inserees plus tard (parentId, defaultNodeId, refs faibles String)
 *       puis re-save.</li>
 * </ol>
 * Les references vers un id absent des maps (ex. relatedPageId hors export) sont
 * CONSERVEES telles quelles (choix : ne pas perdre d'info, ne jamais planter).
 */
@Service
public class ImportService {

    private final ImportArchiveParser archiveParser;
    private final ImageImporter imageImporter;
    private final StoredFileImporter storedFileImporter;
    private final LoreContentInserter loreContentInserter;
    private final CampaignContentInserter campaignContentInserter;
    private final PlayStateInserter playStateInserter;
    private final ImportReferenceRemapper referenceRemapper;

    public ImportService(ImportArchiveParser archiveParser,
                         ImageImporter imageImporter,
                         StoredFileImporter storedFileImporter,
                         LoreContentInserter loreContentInserter,
                         CampaignContentInserter campaignContentInserter,
                         PlayStateInserter playStateInserter,
                         ImportReferenceRemapper referenceRemapper) {
        this.archiveParser = archiveParser;
        this.imageImporter = imageImporter;
        this.storedFileImporter = storedFileImporter;
        this.loreContentInserter = loreContentInserter;
        this.campaignContentInserter = campaignContentInserter;
        this.playStateInserter = playStateInserter;
        this.referenceRemapper = referenceRemapper;
    }

    @Transactional
    public ImportResult importZip(InputStream zipStream) {
        // 1. Parse du zip.
        ImportArchiveParser.ParsedArchive archive = archiveParser.parse(zipStream);
        ContentExport export = archive.export();

        ImportResult.Builder result = new ImportResult.Builder();

        // 2. Reecriture des images + fichiers (cle preservee).
        imageImporter.importImages(export, archive.imageBinaries(), result);
        storedFileImporter.importFiles(export, archive.fileBinaries(), result);

        // 3. Insertion top-down + maps de remapping.
        ImportIdMaps maps = new ImportIdMaps();
        loreContentInserter.insert(export, maps, result);
        campaignContentInserter.insert(export, maps, result);
        playStateInserter.insert(export, maps, result);

        // 4. 2e passe de remapping.
        referenceRemapper.remap(maps);

        return result.build();
    }
}
