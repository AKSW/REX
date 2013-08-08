package org.aksw.rex.uris;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.util.HashMap;
import java.util.HashSet;
import java.util.zip.GZIPInputStream;

import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.standard.StandardAnalyzer;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field.Store;
import org.apache.lucene.document.StringField;
import org.apache.lucene.document.TextField;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.queryparser.classic.QueryParser;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.ScoreDoc;
import org.apache.lucene.store.Directory;
import org.apache.lucene.store.MMapDirectory;
import org.apache.lucene.util.Version;
import org.openrdf.model.Statement;
import org.openrdf.rio.RDFHandlerException;
import org.openrdf.rio.RDFParseException;
import org.openrdf.rio.RDFParser;
import org.openrdf.rio.helpers.RDFHandlerBase;
import org.openrdf.rio.ntriples.NTriplesParser;
import org.openrdf.rio.turtle.TurtleParser;
import org.slf4j.LoggerFactory;

import com.hp.hpl.jena.graph.Node;
import com.hp.hpl.jena.graph.NodeFactory;
import com.hp.hpl.jena.graph.Triple;

public class SurfaceFormIndex {
    private org.slf4j.Logger log = LoggerFactory.getLogger(SurfaceFormIndex.class);
    public static final String TSV = "TSV";
    public static final String N_TRIPLES = "NTriples";
    public static final String TTL = "TTL";
    private String FIELD_NAME_URL = "url";
    private String FIELD_NAME_LABEL = "label";
    private Directory directory;
    private Analyzer analyzer;
    private IndexSearcher isearcher;
    private QueryParser parser;
    private DirectoryReader ireader;
    private IndexWriter iwriter;
    private HashMap<String, HashSet<Triple>> cacheSearch;
    private String baseURI;

    public SurfaceFormIndex(String file, String idxDirectory, String type, String baseURI) {
        log.info("Building surface form index!");
        this.baseURI = baseURI;
        try {
            analyzer = new StandardAnalyzer(Version.LUCENE_43);
            File indexDirectory = new File(idxDirectory);

            if (indexDirectory.exists() && indexDirectory.isDirectory() && indexDirectory.listFiles().length > 0) {
                directory = new MMapDirectory(indexDirectory);
            } else {
                indexDirectory.mkdir();
                directory = new MMapDirectory(indexDirectory);
                IndexWriterConfig config = new IndexWriterConfig(Version.LUCENE_43, analyzer);
                iwriter = new IndexWriter(directory, config);
                if (type.equals(TTL))
                    indexTTLFile(file, baseURI);
                if (type.equals(N_TRIPLES))
                    indexNTriplesFile(file, baseURI);
                if (type.equals(TSV))
                    indexTSVFile(file);
                iwriter.close();
            }
            ireader = DirectoryReader.open(directory);
            isearcher = new IndexSearcher(ireader);
            cacheSearch = new HashMap<String, HashSet<Triple>>();
        } catch (IOException e) {
            log.error(e.getLocalizedMessage());
        }
        log.info("Finished building surface form index!");
    }

    private void indexTSVFile(String surfaceFormsTSV) {
        try {
            InputStream fileStream = new FileInputStream(surfaceFormsTSV);
            InputStream gzipStream = new GZIPInputStream(fileStream);
            Reader decoder = new InputStreamReader(gzipStream, "UTF-8");
            BufferedReader br = new BufferedReader(decoder);
            while (br.ready()) {
                String[] line = br.readLine().split("\t");
                String subject = line[0];
                for (int i = 1; i < line.length; ++i) {
                    String object = line[i];
                    Document doc = new Document();
                    log.debug("\t" + subject + " -> " + object);
                    doc.add(new StringField(FIELD_NAME_URL, subject, Store.YES));
                    doc.add(new TextField(FIELD_NAME_LABEL, object, Store.YES));
                    iwriter.addDocument(doc);
                }
            }
            br.close();
        } catch (IOException e) {
            log.error(e.getLocalizedMessage());
        }
    }

    public HashSet<Triple> search(String label) {
        if (cacheSearch.containsKey(label)) {
            return cacheSearch.get(label);
        }
        HashSet<Triple> triples = new HashSet<Triple>();
        try {
            analyzer = new StandardAnalyzer(Version.LUCENE_43);
            parser = new QueryParser(Version.LUCENE_43, FIELD_NAME_LABEL, analyzer);
            parser.setDefaultOperator(QueryParser.Operator.AND);
            Query query = parser.parse(label);
            ScoreDoc[] hits = isearcher.search(query, 10000).scoreDocs;
            for (int i = 0; i < hits.length; i++) {
                Document hitDoc = isearcher.doc(hits[i].doc);
                String subject = java.net.URLDecoder.decode(hitDoc.get(FIELD_NAME_URL), "UTF-8");
                if (subject.replace(baseURI, "").equals(label)) {
                    // TODO replace dummy subject, predicate
                    Node fakeSubject = NodeFactory.createURI("http://dbpedia.org/resource/Leipzig");
                    Node fakePredicate = NodeFactory.createURI("http://www.w3.org/1999/02/22-rdf-syntax-ns#label");
                    triples.add(new Triple(fakeSubject, fakePredicate, NodeFactory.createURI(subject)));
                }
            }
        } catch (Exception e) {
            log.error(e.getLocalizedMessage() + " -> " + label);
        }
        cacheSearch.put(label, triples);
        return triples;
    }

    public void close() {
        try {
            ireader.close();
            directory.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private class OnlineStatementHandler extends RDFHandlerBase {
        @Override
        public void handleStatement(Statement st) {
            String subject = st.getSubject().stringValue();
            String predicate = st.getPredicate().stringValue();
            String object = st.getObject().stringValue();
            addDocumentToIndex(subject, predicate, object);
        }
    }

    private void indexTTLFile(String file, String baseURI) {
        try {
            log.info("Start parsing: " + file);
            RDFParser parser = new TurtleParser();
            OnlineStatementHandler osh = new OnlineStatementHandler();
            parser.setRDFHandler(osh);
            parser.setStopAtFirstError(false);
            parser.parse(new FileReader(file), baseURI);
            log.info("Finished parsing: " + file);
        } catch (IOException e) {
            log.error(e.getLocalizedMessage());
        } catch (RDFParseException e) {
            log.error(e.getLocalizedMessage());
        } catch (RDFHandlerException e) {
            log.error(e.getLocalizedMessage());
        }

    }

    private void indexNTriplesFile(String file, String baseUri) {
        try {
            log.info("Start parsing: " + file);
            RDFParser parser = new NTriplesParser();
            OnlineStatementHandler osh = new OnlineStatementHandler();
            parser.setRDFHandler(osh);
            parser.setStopAtFirstError(false);
            log.info("Finished parsing: " + file);
            parser.parse(new FileReader(file), baseUri);
        } catch (IOException e) {
            log.error(e.getLocalizedMessage());
        } catch (RDFParseException e) {
            log.error(e.getLocalizedMessage());
        } catch (RDFHandlerException e) {
            log.error(e.getLocalizedMessage());
        }
    }

    private void addDocumentToIndex(String subject, String predicate, String object) {
        try {
            if (subject.startsWith("http://yago-knowledge.org/resource/") && predicate.equals("http://www.w3.org/2004/02/skos/core#prefLabel")) {
                Document doc = new Document();
                doc.add(new StringField(FIELD_NAME_URL, subject, Store.YES));
                doc.add(new TextField(FIELD_NAME_LABEL, object, Store.YES));
                iwriter.addDocument(doc);
            }
        } catch (IOException e) {
            log.error(e.getLocalizedMessage());
        }
    }
}
