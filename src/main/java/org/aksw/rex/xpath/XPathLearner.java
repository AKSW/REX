package org.aksw.rex.xpath;

import java.net.URL;
import java.util.List;
import java.util.Set;

import org.aksw.rex.results.ExtractionResult;
import org.aksw.rex.util.Pair;

import rules.xpath.XPathRule;

import com.hp.hpl.jena.rdf.model.Resource;

public interface XPathLearner {
    List<Pair<XPathRule, XPathRule>> getXPathExpressions(Set<Pair<Resource, Resource>> posExamples, Set<Pair<Resource, Resource>> negExamples, URL Domain);
    Set<ExtractionResult> getExtractionResults(List<Pair<XPathRule, XPathRule>> expressions);

}
