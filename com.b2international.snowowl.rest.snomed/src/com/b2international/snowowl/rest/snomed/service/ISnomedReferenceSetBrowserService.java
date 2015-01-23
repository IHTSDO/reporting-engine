/**
 * Copyright (c) 2012 B2i Healthcare. All rights reserved.
 */
package com.b2international.snowowl.rest.snomed.service;

import java.io.Serializable;
import java.util.List;

import com.b2international.snowowl.rest.domain.IReferenceSet;
import com.b2international.snowowl.rest.domain.IReferenceSetMember;
import com.b2international.snowowl.rest.service.IReferenceSetBrowserService;
import com.b2international.snowowl.rest.snomed.domain.ISnomedReferenceSet;
import com.b2international.snowowl.rest.snomed.domain.ISnomedReferenceSetMember;

/**
 * TODO: review javadoc
 * 
 * SNOMED CT specific interface of the RESTful Reference Set Browser Service.
 * 
 * <p>
 * The following features are supported:
 * <ul>
 *   <li>{@link ISnomedReferenceSetBrowserService#getAllReferenceSets() <em>Retrieve all reference sets</em>}</li>
 *   <li>{@link ISnomedReferenceSetBrowserService#getMembers(Serializable) <em>Retrieve all reference set members</em>}</li>
 *   <li>{@link ISnomedReferenceSetBrowserService#findReferringMembers(Serializable) <em>Find referred members</em>}</li>
 * </ul>
 * </p>
 * 
 * @param <T> type of the SNOMED CT specific reference set. 
 * The &lt;<b>T</b>&gt; type should implement the {@link IReferenceSet} interface.
 * @param <K> type of the SNOMED CT concept's unique identifier.
 * The &lt;<b>K</b>&gt; type should implement the {@link Serializable} interface.
 * @param <M> type of the SNOMED CT terminology dependent reference set member.
 * The &lt;<b>M</b>&gt; type should implement the {@link IReferenceSetMember} interface.
 * @see IReferenceSetBrowserService
 *
 * @author Akos Kitta
 */
public interface ISnomedReferenceSetBrowserService extends IReferenceSetBrowserService<ISnomedReferenceSet, ISnomedReferenceSetMember> {

	/**
	 * An HTTP <b>GET</b> <i>indempotent</i> and <i>safe</i> operation (request) for retrieving all
	 * available reference sets from the SNOMED CT terminology.
	 * <br/><br/>This HTTP request produces <i>XML</i> and <i>JSON</i> media types. The returning media type always depends on the 
	 * request's <i>HTTP Header</i>. The <i>XML</i> representation can be requested by using {@code Accept: application/xml} in the header,
	 * while the <i>JSON</i> representation is available by using {@code Accept: application/json} instead.
	 * <br/><br/>
	 * <b>Resource URI:</b>&nbsp;&nbsp;&nbsp;&nbsp;<tt>GET /snomed/refsetbrowserservice/getall</tt>
	 * <br/><br/>
	 * <b>Example:</b>&nbsp;&nbsp;&nbsp;&nbsp;<tt>GET /snomed/refsetbrowserservice/getall</tt>
	 * <pre>
	 * {
	 *   "list" : [ {
	 *     "SnomedReferenceSet" : [ {
	 *       "id" : 32570271000036106,
	 *       "label" : "Australian English language reference set",
	 *       "identifier" : {
	 *         "@class" : "SnomedDetailedConcept",
	 *         "id" : 32570271000036106,
	 *         "label" : "Australian English language reference set",
	 *         "childCount" : 0,
	 *         "parents" : {
	 *           "@class" : "SnomedConcept",
	 *           "id" : 900000000000507009,
	 *           "label" : "English",
	 *           "childCount" : 3
	 *         },
	 *         "effectiveTime" : "16-10-2009",
	 *         "module" : "SNOMED Clinical Terms Australian extension",
	 *         "definitionStatus" : "Primitive",
	 *         "active" : true
	 *       },
	 *       "referencedComponentType" : "com.b2international.snowowl.terminology.snomed.description"
	 *     }, {
	 *       "id" : 32570511000036102,
	 *       "label" : "Emergency department reason for presenting reference set",
	 *       "identifier" : {
	 *         "@class" : "SnomedDetailedConcept",
	 *         "id" : 32570511000036102,
	 *         "label" : "Emergency department reason for presenting reference set",
	 *         "childCount" : 0,
	 *         "parents" : [ {
	 *           "@class" : "SnomedConcept",
	 *           "id" : 900000000000480006,
	 *           "label" : "Attribute value type",
	 *           "childCount" : 52
	 *         }, {
	 *           "@class" : "SnomedConcept",
	 *           "id" : 32570561000036100,
	 *           "label" : "Emergency department clinical group",
	 *           "childCount" : 4
	 *         } ],
	 *         "effectiveTime" : "31-07-2010",
	 *         "module" : "Emergency department extension",
	 *         "definitionStatus" : "Primitive",
	 *         "active" : true
	 *       },
	 *       "referencedComponentType" : "com.b2international.snowowl.terminology.snomed.concept"
	 *    //... end of example snippet
	 * </pre>
	 * @return all reference sets from the SNOMED CT terminology.
	 * @see <a href="http://json.org/">JSON</a><br/>
	 * @see <a href="http://www.w3.org/TR/xml/">Extensible Markup Language &#40;XML&#41; 1.0 &#40;Fifth Edition&#41;</a><br/>
	 * @see IReferenceSetBrowserService
	 * @see ISnomedReferenceSetBrowserService
	 */
	List<ISnomedReferenceSet> getAllReferenceSets();
}
