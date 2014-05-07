package org.aksw.rex.controller;

import java.net.URL;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.aksw.rex.consistency.ConsistencyChecker;
import org.aksw.rex.consistency.ConsistencyCheckerImpl;
import org.aksw.rex.crawler.CrawlIndex;
import org.aksw.rex.domainidentifier.DomainIdentifier;
import org.aksw.rex.domainidentifier.ManualDomainIdentifier;
import org.aksw.rex.examplegenerator.ExampleGenerator;
import org.aksw.rex.examplegenerator.SimpleExampleGenerator;
import org.aksw.rex.results.ExtractionResult;
import org.aksw.rex.uris.URIGenerator;
import org.aksw.rex.uris.URIGeneratorAGDISTIS;
import org.aksw.rex.util.Pair;
import org.aksw.rex.xpath.XPathExtractor;
import org.aksw.rex.xpath.XPathLearner;
import org.aksw.rex.xpath.alfred.ALFREDXPathLearner;
import org.dllearner.kb.sparql.SparqlEndpoint;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import rules.xpath.XPathRule;

import com.google.common.collect.Sets;
import com.hp.hpl.jena.graph.Node;
import com.hp.hpl.jena.graph.Triple;
import com.hp.hpl.jena.rdf.model.Property;
import com.hp.hpl.jena.rdf.model.Resource;
import com.hp.hpl.jena.rdf.model.ResourceFactory;

import edu.stanford.nlp.util.Quadruple;

/**
 * @author ngonga
 */
public class RexController {
	Logger log = LoggerFactory.getLogger(RexController.class);
	ExampleGenerator exampleGenerator;
	DomainIdentifier di;
	Property property;
	XPathLearner xpath;
	URIGenerator uriGenerator;
	ConsistencyChecker consistency;
	SparqlEndpoint endpoint = SparqlEndpoint.getEndpointDBpedia();
	int topNRules = 1;
	private static URL domain;

	public RexController(Property p, ExampleGenerator e, DomainIdentifier d, XPathLearner l, URIGenerator uriGenerator, ConsistencyChecker c, SparqlEndpoint s) {
		property = p;
		exampleGenerator = e;
		di = d;
		xpath = l;
		this.uriGenerator = uriGenerator;
		consistency = c;
		endpoint = s;
	}

	/**
	 * Runs the extraction pipeline
	 * 
	 * @param subjectRule
	 * @param objectRule
	 * 
	 * @return A set of triples
	 * @throws Exception
	 *             If URI generation does not work
	 */
	public Set<Quadruple<Node, Node, Node, String>> run(String subjectRule, String objectRule) throws Exception

	{
		Set<Quadruple<Node, Node, Node, String>> quads = Sets.newHashSet();

		// example generation
		Set<Pair<Resource, Resource>> posExamples = null;
		Set<Pair<Resource, Resource>> negExamples = null;

		// domain identification
		domain = di.getDomain(property, posExamples, negExamples, false);

		// XPath expression generation
		// List<Pair<XPathRule, XPathRule>> extractionRules =
		// xpath.getXPathExpressions(posExamples, negExamples, domain);
		List<Pair<XPathRule, XPathRule>> extractionRules = new ArrayList<Pair<XPathRule, XPathRule>>();

		extractionRules.add(new Pair<XPathRule, XPathRule>(new XPathRule(subjectRule), new XPathRule(objectRule)));

		if (!extractionRules.isEmpty()) {
			// currently, we assume that the best rule is the first one in the
			// list, thus we
			extractionRules = extractionRules.subList(0, 1);
			System.out.println("Top rule:\n" + extractionRules);

			// extract results from the corpus
			Set<ExtractionResult> results = xpath.getExtractionResults(extractionRules, domain);
			log.error("XpathResults extracted: " + results.size());
			// triple generation
			quads = uriGenerator.getTriples(results, property);
//			BufferedWriter bw = new BufferedWriter(new FileWriter("ntFiles/withOutConsistency" + domain.toExternalForm().replaceAll("//", "").replaceAll("/", "") + ".txt"));
//			for (Quadruple<Node, Node, Node, String> q : quads) {
//				bw.write("<" + q.first.getURI() + "> <" + q.second().getURI() + "> <" + q.third().getURI() + "> <" + q.fourth() + ">.\n");
//			}
//			bw.flush();
//			bw.close();
			log.error("Quadrupels generated extracted: " + quads.size());

			// triple filtering
			Set<Triple> triples = quadsToTriples(quads);
			triples = consistency.getConsistentTriples(triples);
			quads = triplesToQuads(triples, quads);

			// triples = consistency.getConsistentTriples(triples,
			// consistency.generateAxioms(endpoint));
			log.error("Consistency checked: " + quads.size());

		}

		return quads;
	}

	/**
	 * Runs the extraction pipeline
	 * 
	 * @return A set of triples
	 * @throws Exception
	 *             If URI generation does not work
	 */
	public Set<Quadruple<Node, Node, Node, String>> run() throws Exception

	{
		Set<Quadruple<Node, Node, Node, String>> quads = Sets.newHashSet();

		// example generation
		Set<Pair<Resource, Resource>> posExamples = null;
		Set<Pair<Resource, Resource>> negExamples = null;

		// domain identification
		URL domain = di.getDomain(property, posExamples, negExamples, false);

		// XPath expression generation
		List<Pair<XPathRule, XPathRule>> extractionRules = xpath.getXPathExpressions(posExamples, negExamples, domain);

		if (!extractionRules.isEmpty()) {
			// currently, we assume that the best rule is the first one in the
			// list, thus we
			extractionRules = extractionRules.subList(0, 1);
			System.out.println("Top rule:\n" + extractionRules);

			// extract results from the corpus
			Set<ExtractionResult> results = xpath.getExtractionResults(extractionRules, domain);

			// triple generation
			quads = uriGenerator.getTriples(results, property);

			// triple filtering
			// triples = consistency.getConsistentTriples(triples,
			// consistency.generateAxioms(endpoint));
			Set<Triple> triples = quadsToTriples(quads);
			triples = consistency.getConsistentTriples(triples);
			quads = triplesToQuads(triples, quads);
		}

		return quads;
	}

	private Set<Quadruple<Node, Node, Node, String>> triplesToQuads(Set<Triple> triples, Set<Quadruple<Node, Node, Node, String>> quads) {
		HashSet<Quadruple<Node, Node, Node, String>> set = Sets.newHashSet();
		for (Triple t : triples) {
			for (Quadruple<Node, Node, Node, String> q : quads) {
				if (t.getSubject().getURI().equals(q.first.getURI())) {
					if (t.getPredicate().getURI().equals(q.second.getURI())) {
						if (t.getObject().getURI().equals(q.third.getURI())) {
							set.add(q);
						}
					}
				}
			}
		}
		return set;
	}

	private Set<Triple> quadsToTriples(Set<Quadruple<Node, Node, Node, String>> quads) {
		HashSet<Triple> set = Sets.newHashSet();
		for (Quadruple<Node, Node, Node, String> q : quads) {
			set.add(new Triple(q.first, q.second, q.third));
		}
		return set;
	}

	public static void main(String[] args) throws Exception {
		Property property = ResourceFactory.createProperty("http://dbpedia.org/ontology/director");
		SparqlEndpoint endpoint = SparqlEndpoint.getEndpointDBpedia();

		ExampleGenerator exampleGenerator = new SimpleExampleGenerator();
		exampleGenerator.setMaxNrOfPositiveExamples(100);
		exampleGenerator.setEndpoint(endpoint);
		exampleGenerator.setPredicate(property);

		DomainIdentifier domainIdentifier = new ManualDomainIdentifier(new URL("http://www.imdb.com/title/"));

		CrawlIndex crawlIndex = new CrawlIndex("imdb-title-index/");
		XPathExtractor xPathExtractor = new XPathExtractor(crawlIndex);

		XPathLearner xPathLearner = new ALFREDXPathLearner(crawlIndex);
		// XPathLearner xPathLearner = new XPathLearnerImpl(xPathExtractor,
		// endpoint);
		xPathLearner.setUseExactMatch(false);

		URIGenerator uriGenerator = new URIGeneratorAGDISTIS();

		Set<Quadruple<Node, Node, Node, String>> triples = new RexController(property, exampleGenerator, domainIdentifier, xPathLearner, uriGenerator, new ConsistencyCheckerImpl(endpoint), endpoint).run();

		for (Quadruple<Node, Node, Node, String> quadruple : triples) {
			System.out.println(quadruple);
		}
	}

}