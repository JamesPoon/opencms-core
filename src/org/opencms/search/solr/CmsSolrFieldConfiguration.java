/*
 * File   : $Source$
 * Date   : $Date$
 * Version: $Revision$
 *
 * This library is part of OpenCms -
 * the Open Source Content Management System
 *
 * Copyright (C) 2002 - 2009 Alkacon Software (http://www.alkacon.com)
 *
 * This library is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 2.1 of the License, or (at your option) any later version.
 *
 * This library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 * Lesser General Public License for more details.
 *
 * For further information about Alkacon Software, please see the
 * company website: http://www.alkacon.com
 *
 * For further information about OpenCms, please see the
 * project website: http://www.opencms.org
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this library; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place, Suite 330, Boston, MA  02111-1307  USA
 */

package org.opencms.search.solr;

import org.opencms.configuration.I_CmsXmlConfiguration;
import org.opencms.file.CmsFile;
import org.opencms.file.CmsObject;
import org.opencms.file.CmsProperty;
import org.opencms.file.CmsPropertyDefinition;
import org.opencms.file.CmsResource;
import org.opencms.file.types.CmsResourceTypeJsp;
import org.opencms.file.types.CmsResourceTypeXmlContainerPage;
import org.opencms.file.types.CmsResourceTypeXmlContent;
import org.opencms.file.types.CmsResourceTypeXmlPage;
import org.opencms.main.CmsException;
import org.opencms.main.CmsLog;
import org.opencms.main.OpenCms;
import org.opencms.search.CmsSearchIndex;
import org.opencms.search.CmsSearchIndexSource;
import org.opencms.search.I_CmsSearchDocument;
import org.opencms.search.documents.CmsDocumentDependency;
import org.opencms.search.extractors.I_CmsExtractionResult;
import org.opencms.search.fields.CmsLuceneField;
import org.opencms.search.fields.CmsSearchField;
import org.opencms.search.fields.CmsSearchFieldConfiguration;
import org.opencms.search.fields.CmsSearchFieldMapping;
import org.opencms.search.fields.CmsSearchFieldMappingType;
import org.opencms.search.fields.I_CmsSearchFieldMapping;
import org.opencms.util.CmsStringUtil;
import org.opencms.xml.CmsXmlContentDefinition;
import org.opencms.xml.containerpage.CmsContainerElementBean;
import org.opencms.xml.containerpage.CmsContainerPageBean;
import org.opencms.xml.containerpage.CmsXmlContainerPage;
import org.opencms.xml.containerpage.CmsXmlContainerPageFactory;
import org.opencms.xml.content.I_CmsXmlContentHandler;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import org.apache.commons.logging.Log;

/**
 * The search field implementation for Solr.<p>
 *
 * @since 8.5.0
 */
public class CmsSolrFieldConfiguration extends CmsSearchFieldConfiguration {

    /** The log object for this class. */
    private static final Log LOG = CmsLog.getLog(CmsSolrFieldConfiguration.class);

    /** The content locale for the indexed document is stored in order to save performance. */
    private List<Locale> m_contentLocales;

    /** The gallery fields. */
    private Set<CmsSolrField> m_multiLocaleFields = new HashSet<CmsSolrField>();

    /** A list of Solr fields. */
    private Map<String, CmsSolrField> m_solrFields = new HashMap<String, CmsSolrField>();

    /**
     * Default constructor.<p>
     */
    public CmsSolrFieldConfiguration() {

        super();
    }

    /**
     * Adds the additional fields to the configuration, if they are not null.<p>
     *
     * @param additionalFields the additional fields to add
     */
    public void addAdditionalFields(List<CmsSolrField> additionalFields) {

        if (additionalFields != null) {
            for (CmsSolrField solrField : additionalFields) {
                m_solrFields.put(solrField.getName(), solrField);
            }
        }
    }

    /**
     * Returns all configured Solr fields.<p>
     *
     * @return all configured Solr fields
     */
    public Map<String, CmsSolrField> getSolrFields() {

        return Collections.unmodifiableMap(m_solrFields);
    }

    /**
     * @see org.opencms.search.fields.CmsSearchFieldConfiguration#init()
     */
    @Override
    public void init() {

        super.init();
        addAdditionalFields();
    }

    /**
     * @see org.opencms.search.fields.CmsSearchFieldConfiguration#appendAdditionalValuesToDcoument(org.opencms.search.I_CmsSearchDocument, org.opencms.file.CmsObject, org.opencms.file.CmsResource, org.opencms.search.extractors.I_CmsExtractionResult, java.util.List, java.util.List)
     */
    @Override
    protected I_CmsSearchDocument appendAdditionalValuesToDcoument(
        I_CmsSearchDocument document,
        CmsObject cms,
        CmsResource resource,
        I_CmsExtractionResult extractionResult,
        List<CmsProperty> properties,
        List<CmsProperty> propertiesSearched) {

        String mimeType = OpenCms.getResourceManager().getMimeType(resource.getName(), null);
        if (mimeType != null) {
            document.addSearchField(m_solrFields.get(CmsSearchField.FIELD_MIMETYPE), mimeType);
        }

        document.addSearchField(m_solrFields.get(CmsSearchField.FIELD_FILENAME), resource.getName());

        document.addSearchField(m_solrFields.get(CmsSearchField.FIELD_VERSION), "" + resource.getVersion());

        // Add title
        //        System.out.println("-----------------------------------");
        //        System.out.println(resource.getName() + ":   " + extractionResult.getContentItems().keySet());
        //        System.out.println(resource.getName() + ":   " + extractionResult.getContentItems().get("title_en_s"));
        //        System.out.println("-----------------------------------");
        //        if (resource.isInternal()
        //            || resource.isFolder()
        //            || resource.isTemporaryFile()
        //            || (resource.getDateExpired() <= System.currentTimeMillis())) {
        //            // don't index internal resources, folders or temporary files or resources with expire date in the past
        //            return true;
        //        }
        //
        //        try {
        //            // do property lookup with folder search
        //            String propValue = cms.readPropertyObject(resource, CmsPropertyDefinition.PROPERTY_SEARCH_EXCLUDE, true).getValue();
        //            excludeFromIndex = Boolean.valueOf(propValue).booleanValue();
        //            if (!excludeFromIndex && (propValue != null)) {
        //                // property value was neither "true" nor null, must check for "all"
        //                excludeFromIndex = PROPERTY_SEARCH_EXCLUDE_VALUE_ALL.equalsIgnoreCase(propValue.trim());
        //            }
        //        } catch (CmsException e) {
        //            if (LOG.isDebugEnabled()) {
        //                LOG.debug(Messages.get().getBundle().key(Messages.LOG_UNABLE_TO_READ_PROPERTY_1, resource.getRootPath()));
        //            }
        //        }
        //        if (!excludeFromIndex && !USE_ALL_LOCALE.equalsIgnoreCase(getLocale().getLanguage())) {
        //            // check if any resource default locale has a match with the index locale, if not skip resource
        //            List<Locale> locales = OpenCms.getLocaleManager().getDefaultLocales(cms, resource);
        //            Locale match = OpenCms.getLocaleManager().getFirstMatchingLocale(
        //                Collections.singletonList(getLocale()),
        //                locales);
        //            excludeFromIndex = (match == null);
        //        }

        boolean excludeFromIndex = false;
        String propValue = CmsProperty.get(CmsPropertyDefinition.PROPERTY_SEARCH_EXCLUDE, propertiesSearched).getValue();
        excludeFromIndex = Boolean.valueOf(propValue).booleanValue();
        if (!excludeFromIndex && (propValue != null)) {
            // property value was neither "true" nor null, must check for "all"
            excludeFromIndex = CmsSearchIndex.PROPERTY_SEARCH_EXCLUDE_VALUE_ALL.equalsIgnoreCase(propValue.trim())
                || CmsSolrIndex.PROPERTY_SEARCH_EXCLUDE_VALUE_SOLR.equalsIgnoreCase(propValue.trim());
        }

        if (resource.getRootPath().startsWith("/system") || (CmsResourceTypeJsp.getJSPTypeId() == resource.getTypeId())) {
            document.addSearchField(m_solrFields.get(CmsSearchField.FIELD_SEARCH_CHANNEL), "gallery");
        } else {
            document.addSearchField(m_solrFields.get(CmsSearchField.FIELD_SEARCH_CHANNEL), "content");
        }

        return document;
    }

    /**
     * @see org.opencms.search.fields.CmsSearchFieldConfiguration#appendDates(org.opencms.search.I_CmsSearchDocument, org.opencms.file.CmsObject, org.opencms.file.CmsResource, org.opencms.search.extractors.I_CmsExtractionResult, java.util.List, java.util.List)
     */
    @Override
    protected I_CmsSearchDocument appendDates(
        I_CmsSearchDocument document,
        CmsObject cms,
        CmsResource resource,
        I_CmsExtractionResult extractionResult,
        List<CmsProperty> properties,
        List<CmsProperty> propertiesSearched) {

        document.addDateField(CmsSearchField.FIELD_DATE_CREATED, resource.getDateCreated(), false);
        document.addDateField(CmsSearchField.FIELD_DATE_LASTMODIFIED, resource.getDateLastModified(), false);
        document.addDateField(CmsSearchField.FIELD_DATE_CONTENT, resource.getDateContent(), false);
        document.addDateField(CmsSearchField.FIELD_DATE_RELEASED, resource.getDateReleased(), false);
        document.addDateField(CmsSearchField.FIELD_DATE_EXPIRED, resource.getDateExpired(), false);

        return document;
    }

    /**
     * @see org.opencms.search.fields.CmsSearchFieldConfiguration#appendFieldMapping(org.opencms.search.I_CmsSearchDocument, org.opencms.search.fields.CmsSearchField, org.opencms.file.CmsObject, org.opencms.file.CmsResource, org.opencms.search.extractors.I_CmsExtractionResult, java.util.List, java.util.List)
     */
    @Override
    protected I_CmsSearchDocument appendFieldMapping(
        I_CmsSearchDocument document,
        CmsSearchField sfield,
        CmsObject cms,
        CmsResource resource,
        I_CmsExtractionResult extractionResult,
        List<CmsProperty> properties,
        List<CmsProperty> propertiesSearched) {

        CmsSolrField field = (CmsSolrField)sfield;
        try {
            StringBuffer text = new StringBuffer();
            for (I_CmsSearchFieldMapping mapping : field.getMappings()) {
                // loop over the mappings of the given field
                if (extractionResult != null) {
                    String mapResult = null;
                    if ((field.getLocale() != null) && mapping.getType().equals(CmsSearchFieldMappingType.CONTENT)) {
                        // this is a localized content field, try to retrieve the localized content extraction
                        String key = CmsSearchFieldConfiguration.getLocaleExtendedName(
                            CmsSearchField.FIELD_CONTENT,
                            field.getLocale());
                        mapResult = extractionResult.getContentItems().get(key);
                        if (mapResult == null) {
                            // no localized content extracted
                            if (!(CmsResourceTypeXmlContent.isXmlContent(resource) || CmsResourceTypeXmlPage.isXmlPage(resource))) {
                                // the resource is no XML content nor an XML page
                                if ((m_contentLocales != null) && m_contentLocales.contains(field.getLocale())) {
                                    // the resource to get the extracted content for has the locale of this field,
                                    // so store the extraction content into this field
                                    mapResult = extractionResult.getContent();
                                }
                            }
                        }
                    } else {
                        // this is not a localized content field, just perform the regular mapping
                        mapResult = mapping.getStringValue(
                            cms,
                            resource,
                            extractionResult,
                            properties,
                            propertiesSearched);
                    }
                    if (mapResult != null) {
                        // append the found mapping result to text content of this field
                        if (text.length() > 0) {
                            text.append('\n');
                        }
                        text.append(mapResult);
                    } else if (mapping.getDefaultValue() != null) {
                        // no mapping result found, but a default is configured
                        text.append("\n" + mapping.getDefaultValue());
                    }
                } else if (mapping.getStringValue(cms, resource, extractionResult, properties, propertiesSearched) != null) {
                    String value = mapping.getStringValue(
                        cms,
                        resource,
                        extractionResult,
                        properties,
                        propertiesSearched);
                    if (value != null) {
                        document.addSearchField(field, value);
                    }
                }
            }
            if ((text.length() <= 0) && (field.getDefaultValue() != null)) {
                text.append(field.getDefaultValue());
            }
            if (text.length() > 0) {
                document.addSearchField(field, text.toString());
            }
        } catch (Exception e) {
            // nothing to do just log
            LOG.error(e);
        }
        return document;
    }

    /**
     * @see org.opencms.search.fields.CmsSearchFieldConfiguration#appendFieldMappings(org.opencms.search.I_CmsSearchDocument, org.opencms.file.CmsObject, org.opencms.file.CmsResource, org.opencms.search.extractors.I_CmsExtractionResult, java.util.List, java.util.List)
     */
    @Override
    protected I_CmsSearchDocument appendFieldMappings(
        I_CmsSearchDocument document,
        CmsObject cms,
        CmsResource resource,
        I_CmsExtractionResult extractionResult,
        List<CmsProperty> properties,
        List<CmsProperty> propertiesSearched) {

        Set<CmsSearchField> mappedFields = getXSDMappings(cms, resource);
        if (mappedFields != null) {
            for (CmsSearchField field : mappedFields) {
                document = appendFieldMapping(
                    document,
                    field,
                    cms,
                    resource,
                    extractionResult,
                    properties,
                    propertiesSearched);
            }
        }

        // add field mappings from elements of a container page
        if (CmsResourceTypeXmlContainerPage.isContainerPage(resource)) {
            document = appendFieldMappingsFromElementsOnThePage(document, cms, resource);

        }

        for (CmsSolrField field : m_solrFields.values()) {
            document = appendFieldMapping(
                document,
                field,
                cms,
                resource,
                extractionResult,
                properties,
                propertiesSearched);
        }
        return document;
    }

    /**
     * Adds search fields from elements on a container page to a container page's document.
     * @param document The document for the container page
     * @param cms The current CmsObject
     * @param resource The resource of the container page
     * @return the manipulated document
     */
    protected I_CmsSearchDocument appendFieldMappingsFromElementsOnThePage(
        I_CmsSearchDocument document,
        CmsObject cms,
        CmsResource resource) {

        try {
            CmsFile file = cms.readFile(resource);
            CmsXmlContainerPage containerPage = CmsXmlContainerPageFactory.unmarshal(cms, file);
            CmsContainerPageBean containerBean = containerPage.getContainerPage(cms);
            if (containerBean != null) {
                for (CmsContainerElementBean element : containerBean.getElements()) {
                    element.initResource(cms);
                    CmsResource elemResource = element.getResource();
                    Set<CmsSearchField> mappedFields = getXSDMappingsForPage(cms, elemResource);
                    if (mappedFields != null) {

                        for (CmsSearchField field : mappedFields) {
                            document = appendFieldMapping(
                                document,
                                field,
                                cms,
                                elemResource,
                                CmsSolrDocumentXmlContent.extractXmlContent(cms, elemResource, getIndex()),
                                cms.readPropertyObjects(resource, false),
                                cms.readPropertyObjects(resource, true));
                        }
                    }
                }
            }
        } catch (CmsException e) {
            LOG.error(e.getLocalizedMessage(), e);
        }
        return document;
    }

    /**
     * @see org.opencms.search.fields.CmsSearchFieldConfiguration#appendLocales(org.opencms.search.I_CmsSearchDocument, org.opencms.file.CmsObject, org.opencms.file.CmsResource, org.opencms.search.extractors.I_CmsExtractionResult, java.util.List, java.util.List)
     */
    @Override
    protected I_CmsSearchDocument appendLocales(
        I_CmsSearchDocument document,
        CmsObject cms,
        CmsResource resource,
        I_CmsExtractionResult extraction,
        List<CmsProperty> properties,
        List<CmsProperty> propertiesSearched) {

        // append the resource locales
        List<String> itemLocales = null;
        List<Locale> resourceLocales = new ArrayList<Locale>();
        if ((extraction != null)
            && (extraction.getContentItems() != null)
            && (extraction.getContentItems().get(CmsSearchField.FIELD_RESOURCE_LOCALES) != null)) {
            // XMl content or page
            String localesAsString = extraction.getContentItems().get(CmsSearchField.FIELD_RESOURCE_LOCALES);
            itemLocales = CmsStringUtil.splitAsList(localesAsString, ' ');
            for (String locale : itemLocales) {
                resourceLocales.add(new Locale(locale));
                // Write the value for each multilingual field into locale-aware Solr fields (e.g.
                // "title_<locale>_s").
                for (CmsSolrField sf : m_multiLocaleFields) {
                    String effFieldName = CmsSearchFieldConfiguration.getLocaleExtendedName(sf.getName(), locale)
                        + "_s";
                    String value = extraction.getContentItems().get(effFieldName);
                    CmsSolrField f = new CmsSolrField(effFieldName, null, null, null, 0);
                    document.addSearchField(f, value);

                    effFieldName = CmsSearchFieldConfiguration.getLocaleExtendedName(sf.getName(), locale) + "_txt";
                    value = extraction.getContentItems().get(effFieldName);
                    f = new CmsSolrField(effFieldName, null, null, null, 0);
                    document.addSearchField(f, value);
                }
            }
        } else {
            // For all other resources add all default locales
            resourceLocales = OpenCms.getLocaleManager().getDefaultLocales(cms, resource);

            /*
             * A problem is likely to arise when dealing with multilingual fields:
             * Only values extracted from XML resources are written into the Solr locale-aware fields (e.g.
             * "title_<locale>_s"), therefore sorting by them will not work as non-XML (unilingual) resources extract
             * the information by the resource property facility and will not write to an Solr locale-aware field.
             *
             * The following code is used to fix this behavior, at least for "Title".
             */

            // Check all passed properties for "Title"...
            for (final CmsProperty prop : propertiesSearched) {
                if (prop.getName().equals(CmsPropertyDefinition.PROPERTY_TITLE)) {
                    final String value = prop.getValue();

                    // Write a Solr locale-aware field for every locale the system supports...
                    final List<Locale> availableLocales = OpenCms.getLocaleManager().getAvailableLocales();
                    for (final Locale locale : availableLocales) {
                        final String lang = locale.getLanguage();
                        // Don't proceed if a field has already written for this locale.
                        if (!resourceLocales.contains(lang)) {
                            final String effFieldName = CmsSearchFieldConfiguration.getLocaleExtendedName(
                                CmsSearchField.FIELD_TITLE_UNSTORED,
                                locale) + "_s";

                            final CmsSolrField f = new CmsSolrField(effFieldName, null, null, null, 0);
                            document.addSearchField(f, value);
                        }
                    }
                }
            }

        }

        document.addResourceLocales(resourceLocales);

        // append the content locales
        m_contentLocales = new ArrayList<Locale>();
        if (itemLocales != null) {
            // XML content or page
            m_contentLocales = resourceLocales;
        } else {
            // for all other try to determine the locales
            m_contentLocales = getContentLocales(cms, resource, extraction);
        }
        document.addContentLocales(m_contentLocales);

        // append document dependencies if configured
        if (hasLocaleDependencies()) {
            CmsDocumentDependency dep = CmsDocumentDependency.load(cms, resource);
            ((CmsSolrDocument)document).addDocumentDependency(cms, dep);
        }
        return document;
    }

    /**
     * @see org.opencms.search.fields.CmsSearchFieldConfiguration#appendProperties(org.opencms.search.I_CmsSearchDocument, org.opencms.file.CmsObject, org.opencms.file.CmsResource, org.opencms.search.extractors.I_CmsExtractionResult, java.util.List, java.util.List)
     */
    @Override
    protected I_CmsSearchDocument appendProperties(
        I_CmsSearchDocument document,
        CmsObject cms,
        CmsResource resource,
        I_CmsExtractionResult extraction,
        List<CmsProperty> properties,
        List<CmsProperty> propertiesSearched) {

        for (CmsProperty prop : propertiesSearched) {
            if (CmsStringUtil.isNotEmptyOrWhitespaceOnly(prop.getValue())) {
                document.addSearchField(new CmsSolrField(
                    prop.getName() + CmsSearchField.FIELD_DYNAMIC_PROPERTIES,
                    null,
                    null,
                    null,
                    CmsSearchField.BOOST_DEFAULT), prop.getValue());

                // Also write the property using the dynamic field '_s' in order to prevent tokenization
                // of the property. The resulting field is named '<property>_prop_s'.
                document.addSearchField(new CmsSolrField(prop.getName()
                    + CmsSearchField.FIELD_DYNAMIC_PROPERTIES
                    + "_s", null, null, null, CmsSearchField.BOOST_DEFAULT), prop.getValue());
            }
        }

        for (CmsProperty prop : properties) {
            if (CmsStringUtil.isNotEmptyOrWhitespaceOnly(prop.getValue())) {
                document.addSearchField(
                    new CmsSolrField(
                        prop.getName() + CmsSearchField.FIELD_DYNAMIC_PROPERTIES_DIRECT,
                        null,
                        null,
                        null,
                        CmsSearchField.BOOST_DEFAULT),
                    prop.getValue());

                // Also write the property using the dynamic field '_s' in order to prevent tokenization
                // of the property. The resulting field is named '<property>_prop_nosearch_s'.
                document.addSearchField(new CmsSolrField(prop.getName()
                    + CmsSearchField.FIELD_DYNAMIC_PROPERTIES_DIRECT
                    + "_s", null, null, null, CmsSearchField.BOOST_DEFAULT), prop.getValue());
            }
        }
        return document;
    }

    /**
     * Retrieves the locales for an content, that is whether an XML content nor an XML page.<p>
     *
     * Uses following strategy:
     * <ul>
     * <li>first by file name</li>
     * <li>then by detection and</li>
     * <li>otherwise take the first configured default locale for this resource</li>
     * </ul>
     *
     * @param cms the current CmsObject
     * @param resource the resource to get the content locales for
     * @param extraction the extraction result
     *
     * @return the determined locales for the given resource
     */
    protected List<Locale> getContentLocales(CmsObject cms, CmsResource resource, I_CmsExtractionResult extraction) {

        // try to detect locale by filename
        Locale detectedLocale = CmsStringUtil.getLocaleForName(resource.getRootPath());
        // try to detect locale by language detector
        if (getIndex().isLanguageDetection()
            && (detectedLocale == null)
            && (extraction != null)
            && (extraction.getContent() != null)) {
            detectedLocale = CmsStringUtil.getLocaleForText(extraction.getContent());
        }
        // take the detected locale or use the first configured default locale for this resource
        List<Locale> result = new ArrayList<Locale>();
        if (detectedLocale != null) {
            // take the found locale
            result.add(detectedLocale);
        } else {
            // take the first configured OpenCms default locale for this resource as fall-back
            result.add(OpenCms.getLocaleManager().getDefaultLocales(cms, resource).get(0));
            LOG.debug(Messages.get().getBundle().key(Messages.LOG_LANGUAGE_DETECTION_FAILED_1, resource));
        }
        return result;
    }

    /**
     * Returns the search field mappings declared within the XSD.<p>
     *
     * @param cms the CmsObject
     * @param resource the resource
     *
     * @return the fields to map
     */
    protected Set<CmsSearchField> getXSDMappings(CmsObject cms, CmsResource resource) {

        try {
            if (CmsResourceTypeXmlContent.isXmlContent(resource)) {
                I_CmsXmlContentHandler handler = CmsXmlContentDefinition.getContentHandlerForResource(cms, resource);
                if ((handler != null) && !handler.getSearchFields().isEmpty()) {
                    return handler.getSearchFields();
                }
            }
        } catch (CmsException e) {
            LOG.error(e.getMessage(), e);
        }
        return null;
    }

    /**
     * Returns the search field mappings declared within the XSD that should be applied to the container page.<p>
     *
     * @param cms the CmsObject
     * @param resource the resource
     *
     * @return the fields to map
     */
    protected Set<CmsSearchField> getXSDMappingsForPage(CmsObject cms, CmsResource resource) {

        try {
            if (CmsResourceTypeXmlContent.isXmlContent(resource)) {
                I_CmsXmlContentHandler handler = CmsXmlContentDefinition.getContentHandlerForResource(cms, resource);
                if ((handler != null) && !handler.getSearchFieldsForPage().isEmpty()) {
                    return handler.getSearchFieldsForPage();
                }
            }
        } catch (CmsException e) {
            LOG.error(e.getMessage(), e);
        }
        return null;
    }

    /**
     * Adds additional fields to this field configuration.<p>
     */
    private void addAdditionalFields() {

        /*
         * Add fields from opencms-search.xml (Lucene fields)
         */
        for (CmsSearchField field : getFields()) {
            if (field instanceof CmsLuceneField) {
                CmsSolrField newSolrField = new CmsSolrField((CmsLuceneField)field);
                m_solrFields.put(newSolrField.getName(), newSolrField);
            }
        }

        /*
         * Add the content fields (multiple for contents with more than one locale)
         */
        // add the content_<locale> fields to this configuration
        CmsSolrField solrField = new CmsSolrField(
            CmsSearchField.FIELD_CONTENT,
            null,
            null,
            null,
            CmsSearchField.BOOST_DEFAULT);
        solrField.addMapping(new CmsSearchFieldMapping(CmsSearchFieldMappingType.CONTENT, CmsSearchField.FIELD_CONTENT));
        m_solrFields.put(solrField.getName(), solrField);
        for (Locale locale : OpenCms.getLocaleManager().getAvailableLocales()) {
            solrField = new CmsSolrField(
                CmsSearchFieldConfiguration.getLocaleExtendedName(CmsSearchField.FIELD_CONTENT, locale),
                Collections.singletonList(locale.toString() + CmsSearchField.FIELD_EXCERPT),
                locale,
                null,
                CmsSearchField.BOOST_DEFAULT);
            solrField.addMapping(new CmsSearchFieldMapping(
                CmsSearchFieldMappingType.CONTENT,
                CmsSearchField.FIELD_CONTENT));
            m_solrFields.put(solrField.getName(), solrField);
        }

        /*
         * Fields filled within appendFields
         */
        CmsSolrField sfield = new CmsSolrField(CmsSearchField.FIELD_MIMETYPE, null, null, null, 0);
        m_solrFields.put(sfield.getName(), sfield);

        sfield = new CmsSolrField(CmsSearchField.FIELD_FILENAME, null, null, null, 0);
        m_solrFields.put(sfield.getName(), sfield);

        sfield = new CmsSolrField(CmsSearchField.FIELD_VERSION, null, null, null, 0);
        m_solrFields.put(sfield.getName(), sfield);

        sfield = new CmsSolrField(CmsSearchField.FIELD_SEARCH_CHANNEL, null, null, null, 0);
        m_solrFields.put(sfield.getName(), sfield);

        /*
         * Add multi-locale fields without mapping, filled in appendLocales
         */
        sfield = new CmsSolrField(CmsSearchField.FIELD_TITLE_UNSTORED, null, null, null, 0);
        m_multiLocaleFields.add(sfield);
        m_solrFields.put(sfield.getName(), sfield);

        sfield = new CmsSolrField(CmsSearchField.FIELD_DESCRIPTION, null, null, null, 0);
        m_solrFields.put(sfield.getName(), sfield);
        m_multiLocaleFields.add(sfield);

        /*
         * Fields with mapping
         */
        sfield = new CmsSolrField(CmsSearchField.FIELD_STATE, null, null, null, 0);
        CmsSearchFieldMapping map = new CmsSearchFieldMapping(
            CmsSearchFieldMappingType.ATTRIBUTE,
            CmsSearchField.FIELD_STATE);
        sfield.addMapping(map);
        m_solrFields.put(sfield.getName(), sfield);

        sfield = new CmsSolrField(CmsSearchField.FIELD_USER_LAST_MODIFIED, null, null, null, 0);
        map = new CmsSearchFieldMapping(CmsSearchFieldMappingType.ATTRIBUTE, CmsSearchField.FIELD_USER_LAST_MODIFIED);
        sfield.addMapping(map);
        m_solrFields.put(sfield.getName(), sfield);

        sfield = new CmsSolrField(CmsSearchField.FIELD_USER_CREATED, null, null, null, 0);
        map = new CmsSearchFieldMapping(CmsSearchFieldMappingType.ATTRIBUTE, CmsSearchField.FIELD_USER_CREATED);
        sfield.addMapping(map);
        m_solrFields.put(sfield.getName(), sfield);

        sfield = new CmsSolrField(CmsSearchField.FIELD_META, null, null, null, 0);
        map = new CmsSearchFieldMapping(CmsSearchFieldMappingType.PROPERTY, CmsPropertyDefinition.PROPERTY_TITLE);
        sfield.addMapping(map);
        map = new CmsSearchFieldMapping(CmsSearchFieldMappingType.PROPERTY, CmsPropertyDefinition.PROPERTY_DESCRIPTION);
        sfield.addMapping(map);
        map = new CmsSearchFieldMapping(CmsSearchFieldMappingType.ATTRIBUTE, I_CmsXmlConfiguration.A_NAME);
        sfield.addMapping(map);
        m_solrFields.put(sfield.getName(), sfield);

        sfield = new CmsSolrField(CmsSearchField.FIELD_SEARCH_EXCLUDE, null, null, null, 0);
        map = new CmsSearchFieldMapping(
            CmsSearchFieldMappingType.PROPERTY_SEARCH,
            CmsPropertyDefinition.PROPERTY_SEARCH_EXCLUDE);
        sfield.addMapping(map);
        m_solrFields.put(sfield.getName(), sfield);

        sfield = new CmsSolrField(CmsSearchField.FIELD_CONTAINER_TYPES, null, null, null, 0);
        map = new CmsSearchFieldMapping(
            CmsSearchFieldMappingType.DYNAMIC,
            "org.opencms.search.galleries.CmsGallerySearchFieldMapping");
        map.setDefaultValue("container_types");
        sfield.addMapping(map);
        m_solrFields.put(sfield.getName(), sfield);

        sfield = new CmsSolrField(CmsSearchField.FIELD_ADDITIONAL_INFO, null, null, null, 0);
        map = new CmsSearchFieldMapping(
            CmsSearchFieldMappingType.DYNAMIC,
            "org.opencms.search.galleries.CmsGallerySearchFieldMapping");
        map.setDefaultValue("additional_info");
        sfield.addMapping(map);
        m_solrFields.put(sfield.getName(), sfield);

        getFields().clear();
        getFields().addAll(m_solrFields.values());
    }

    /**
     * Returns <code>true</code> if at least one of the index sources uses a VFS indexer that is able
     * to index locale dependent resources.<p>
     *
     * TODO This should be improved somehow
     *
     * @return <code>true</code> if this field configuration should resolve locale dependencies
     */
    private boolean hasLocaleDependencies() {

        for (CmsSearchIndexSource source : getIndex().getSources()) {
            if (source.getIndexer().isLocaleDependenciesEnable()) {
                return true;
            }
        }
        return false;
    }
}
