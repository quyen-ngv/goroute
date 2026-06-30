package com.ds.goroute.service.impl;

import com.ds.goroute.constant.ErrorConstant;
import com.ds.goroute.entity.Place;
import com.ds.goroute.exception.BusinessException;
import com.ds.goroute.repository.PlaceRepository;
import com.ds.goroute.service.PlaceSearchIndexService;
import com.ds.goroute.utils.LuceneTitleQueryBuilder;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.lucene.analysis.standard.StandardAnalyzer;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.StringField;
import org.apache.lucene.document.TextField;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.index.Term;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.ScoreDoc;
import org.apache.lucene.search.SearcherFactory;
import org.apache.lucene.search.SearcherManager;
import org.apache.lucene.search.TopDocs;
import org.apache.lucene.store.Directory;
import org.apache.lucene.store.FSDirectory;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class PlaceSearchIndexServiceImpl implements PlaceSearchIndexService {

    private static final int REINDEX_BATCH_SIZE = 200;

    private final PlaceRepository placeRepository;

    private Directory directory;
    private IndexWriter writer;
    private SearcherManager searcherManager;
    private StandardAnalyzer analyzer;

    @PostConstruct
    public void init() throws IOException {
        analyzer = new StandardAnalyzer();
        Path indexDir = Paths.get("data", "lucene-index", "places");
        Files.createDirectories(indexDir);
        directory = FSDirectory.open(indexDir);
        IndexWriterConfig config = new IndexWriterConfig(analyzer);
        writer = new IndexWriter(directory, config);
        searcherManager = new SearcherManager(writer, new SearcherFactory());
        log.info("Place Lucene index initialized at: {}", indexDir.toAbsolutePath());
        ensureSearchIndexPopulated();
    }

    private void ensureSearchIndexPopulated() {
        try {
            IndexSearcher searcher = searcherManager.acquire();
            try {
                if (searcher.getIndexReader().numDocs() == 0 && placeRepository.countAll() > 0) {
                    log.warn("Place Lucene index is empty but DB has data — reindexing");
                    triggerReindex();
                }
            } finally {
                searcherManager.release(searcher);
            }
        } catch (IOException e) {
            log.warn("Could not verify place Lucene index: {}", e.getMessage());
        }
    }

    @PreDestroy
    public void cleanup() throws IOException {
        if (searcherManager != null) {
            searcherManager.close();
        }
        if (writer != null) {
            writer.close();
        }
        if (directory != null) {
            directory.close();
        }
        if (analyzer != null) {
            analyzer.close();
        }
    }

    @Override
    public void indexPlace(Place place) {
        if (place == null || place.getId() == null) {
            return;
        }
        try {
            writer.updateDocument(new Term("id", place.getId().toString()), toDocument(place));
            writer.commit();
            refreshSearchIndex();
        } catch (IOException e) {
            log.error("Failed to index place {}", place.getId(), e);
        }
    }

    @Override
    public void deletePlace(UUID id) {
        if (id == null) {
            return;
        }
        try {
            writer.deleteDocuments(new Term("id", id.toString()));
            writer.commit();
            refreshSearchIndex();
        } catch (IOException e) {
            log.error("Failed to delete place {} from index", id, e);
        }
    }

    @Override
    public void triggerReindex() {
        try {
            writer.deleteAll();

            int offset = 0;
            int total = 0;
            while (true) {
                List<Place> batch = placeRepository.findPage(REINDEX_BATCH_SIZE, offset);
                if (batch.isEmpty()) {
                    break;
                }
                for (Place place : batch) {
                    writer.addDocument(toDocument(place));
                }
                total += batch.size();
                offset += batch.size();
            }

            writer.commit();
            refreshSearchIndex();
            log.info("Reindexed {} places", total);
        } catch (IOException e) {
            log.error("Place reindex failed", e);
            throw new BusinessException(ErrorConstant.INTERNAL_SERVER_ERROR, "Place reindex failed");
        }
    }

    @Override
    public List<UUID> searchTitleIds(String query, int maxResults) throws IOException {
        if (query == null || query.isBlank()) {
            return List.of();
        }

        Query textQuery = LuceneTitleQueryBuilder.build(query.trim(), analyzer);
        IndexSearcher searcher = searcherManager.acquire();
        try {
            TopDocs topDocs = searcher.search(textQuery, maxResults);
            List<UUID> ids = new ArrayList<>(topDocs.scoreDocs.length);
            for (ScoreDoc scoreDoc : topDocs.scoreDocs) {
                Document doc = searcher.doc(scoreDoc.doc);
                ids.add(UUID.fromString(doc.get("id")));
            }
            return ids;
        } finally {
            searcherManager.release(searcher);
        }
    }

    private Document toDocument(Place place) {
        Document doc = new Document();
        doc.add(new StringField("id", place.getId().toString(), Field.Store.YES));
        doc.add(new TextField("title", place.getTitle() != null ? place.getTitle() : "", Field.Store.YES));
        return doc;
    }

    private void refreshSearchIndex() {
        try {
            searcherManager.maybeRefresh();
        } catch (IOException e) {
            log.warn("Failed to refresh place Lucene searcher: {}", e.getMessage());
        }
    }
}
