/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package org.aksw.rex.domainidentifier;

import com.hp.hpl.jena.rdf.model.Resource;
import com.hp.hpl.jena.rdf.model.ResourceFactory;
import java.net.URL;
import java.util.HashSet;
import java.util.Set;
import org.aksw.rex.util.Pair;
import org.junit.Test;
import static org.junit.Assert.assertTrue;

/**
 * Test for domain identifier
 * @author ngonga
 */
public class DomainIdentifierTest {

    @Test
    public void testDomainIdentifier() throws Exception {
        Set<Pair<Resource, Resource>> posExamples = new HashSet<Pair<Resource, Resource>>();
        Resource r1 = ResourceFactory.createResource("http://dbpedia.org/resource/Tom_Cruise");
        Resource r2 = ResourceFactory.createResource("http://dbpedia.org/resource/Minority_Report");
        Set<Pair<Resource, Resource>> negExamples = new HashSet<Pair<Resource, Resource>>();
        posExamples.add(new Pair(r1, r2));
        Resource r3 = ResourceFactory.createResource("http://dbpedia.org/resource/Don_Johnson");
        Resource r4 = ResourceFactory.createResource("http://dbpedia.org/resource/Miami_Vice");
        posExamples.add(new Pair(r3, r4));
        GoogleDomainIdentifier gdi = new GoogleDomainIdentifier();
        URL domain = gdi.getDomain(ResourceFactory.createProperty("http://dbpedia.org/ontology/starring"), posExamples, negExamples, true);
        boolean correct = false;
        System.out.println(domain);
        if(domain.equals(new URL("http://www.imdb.com"))) correct = true;
        assertTrue("Should be imdb", correct);
    }
}
