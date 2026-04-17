/*
 * Copyright 2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package biz.paluch.dap.xml;

import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;

import org.xmlbeam.XBProjector;
import org.xmlbeam.config.DefaultXMLFactoriesConfig;

/**
 * Factory for XMLBeam projector with POM/metadata-friendly configuration.
 */
public class XmlBeamProjectorFactory {

	public static final XBProjector INSTANCE = XmlBeamProjectorFactory.create();

	private XmlBeamProjectorFactory() {}

	private static XBProjector create() {
		SecureXmlFactoriesConfig config = new SecureXmlFactoriesConfig();
		config.setNamespacePhilosophy(DefaultXMLFactoriesConfig.NamespacePhilosophy.AGNOSTIC);
		config.setOmitXMLDeclaration(false);
		config.setPrettyPrinting(false);
		config.setNoEntityResolving(true);
		config.setExpandEntityReferences(false);
		return new XBProjector(config, XBProjector.Flags.TO_STRING_RENDERS_XML);
	}

	private static final class SecureXmlFactoriesConfig extends DefaultXMLFactoriesConfig {

		@Override
		public DocumentBuilderFactory createDocumentBuilderFactory() {

			try {
				DocumentBuilderFactory factory = super.createDocumentBuilderFactory();
				configureSecureXmlProcessing(factory);
				return factory;
			} catch (ParserConfigurationException e) {
				throw new IllegalStateException("Cannot create secure DocumentBuilderFactory", e);
			}
		}

	}

	private static void configureSecureXmlProcessing(DocumentBuilderFactory factory)
			throws ParserConfigurationException {

		factory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
		try {
			factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_DTD, "");
			factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_SCHEMA, "");
		} catch (IllegalArgumentException ignored) {
		}
		try {
			factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
		} catch (ParserConfigurationException ignored) {
		}
		try {
			factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
		} catch (ParserConfigurationException ignored) {
		}
		try {
			factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
		} catch (ParserConfigurationException ignored) {
		}
		try {
			factory.setXIncludeAware(false);
		} catch (IllegalArgumentException ignored) {
		}
		factory.setExpandEntityReferences(false);
	}

}
