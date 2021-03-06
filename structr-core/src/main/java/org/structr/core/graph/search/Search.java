/**
 * Copyright (C) 2010-2014 Morgner UG (haftungsbeschränkt)
 *
 * This file is part of Structr <http://structr.org>.
 *
 * Structr is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as
 * published by the Free Software Foundation, either version 3 of the
 * License, or (at your option) any later version.
 *
 * Structr is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with Structr.  If not, see <http://www.gnu.org/licenses/>.
 */
package org.structr.core.graph.search;

import org.apache.commons.lang.StringUtils;


import org.structr.core.property.PropertyKey;
import org.structr.common.SecurityContext;
import org.structr.common.error.FrameworkException;
import org.structr.core.entity.AbstractNode;
import org.structr.core.entity.AbstractRelationship;

//~--- JDK imports ------------------------------------------------------------

import java.text.Normalizer;

import java.util.LinkedHashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.apache.lucene.search.BooleanClause.Occur;
import org.structr.core.GraphObject;
import org.structr.core.Result;
import org.structr.core.app.StructrApp;
import org.structr.core.entity.Relation;
import org.structr.core.graph.NodeInterface;
import org.structr.core.graph.RelationshipInterface;
import org.structr.schema.ConfigurationProvider;

//~--- classes ----------------------------------------------------------------

/**
 *
 * @author Axel Morgner
 */
public abstract class Search {

	public static final String DISTANCE_SEARCH_KEYWORD    = "distance";
	public static final String LOCATION_SEARCH_KEYWORD    = "location";
	public static final String STREET_SEARCH_KEYWORD      = "street";
	public static final String HOUSE_SEARCH_KEYWORD       = "house";
	public static final String POSTAL_CODE_SEARCH_KEYWORD = "postalCode";
	public static final String CITY_SEARCH_KEYWORD        = "city";
	public static final String STATE_SEARCH_KEYWORD       = "state";
	public static final String COUNTRY_SEARCH_KEYWORD     = "country";
	
	private static final Logger logger                    = Logger.getLogger(Search.class.getName());
	private static final Set<Character> specialCharsExact = new LinkedHashSet<Character>();
	private static final Set<Character> specialChars      = new LinkedHashSet<Character>();

	//~--- static initializers --------------------------------------------

	static {

		specialChars.add('\\');
		specialChars.add('+');
		specialChars.add('-');
		specialChars.add('!');
		specialChars.add('(');
		specialChars.add(')');
		specialChars.add(':');
		specialChars.add('^');
		specialChars.add('[');
		specialChars.add(']');
		specialChars.add('"');
		specialChars.add('{');
		specialChars.add('}');
		specialChars.add('~');
		specialChars.add('*');
		specialChars.add('?');
		specialChars.add('|');
		specialChars.add('&');
		specialChars.add(';');
		specialCharsExact.add('"');
		specialCharsExact.add('\\');

	}

	;

	//~--- methods --------------------------------------------------------

	private static List<SearchAttribute> getTypeAndSubtypesInternal(final Class type, final boolean isExactMatch) {

		final ConfigurationProvider configuration                             = StructrApp.getConfiguration();
		final Map<String, Class<? extends NodeInterface>> nodeEntities        = configuration.getNodeEntities();
		final Map<String, Class<? extends RelationshipInterface>> relEntities = configuration.getRelationshipEntities();
		final List<SearchAttribute> attrs                                     = new LinkedList<>();

		if (type == null) {

			// no entity class for the given type found, examine interface types and subclasses
			Set<Class> classesForInterface = configuration.getClassesForInterface(type.getSimpleName());

			if (classesForInterface != null) {

				for (Class clazz : classesForInterface) {

					attrs.addAll(getTypeAndSubtypesInternal(clazz, isExactMatch));
				}

			}

			return attrs;
		}

		for (Map.Entry<String, Class<? extends NodeInterface>> entity : nodeEntities.entrySet()) {

			Class<? extends NodeInterface> entityClass = entity.getValue();

			if (type.isAssignableFrom(entityClass)) {

				attrs.add(Search.orType(entityClass, isExactMatch));
			}
		}

		for (Map.Entry<String, Class<? extends RelationshipInterface>> entity : relEntities.entrySet()) {

			Class<? extends RelationshipInterface> entityClass = entity.getValue();

			if (type.isAssignableFrom(entityClass)) {

				attrs.add(Search.orType(entityClass, isExactMatch));
			}
		}

		return attrs;

	}

	public static SearchAttributeGroup andExactTypeAndSubtypes(final Class type) {
		return andTypeAndSubtypes(type, true);
	}
	
	public static SearchAttributeGroup andTypeAndSubtypes(final Class type, final boolean isExactMatch) {

		SearchAttributeGroup attrs          = new SearchAttributeGroup(Occur.MUST);
		List<SearchAttribute> attrsInternal = getTypeAndSubtypesInternal(type, isExactMatch);

		for (SearchAttribute attr : attrsInternal) {

			attrs.add(attr);
		}

		return attrs;

	}
	
	public static SearchAttributeGroup orExactTypeAndSubtypes(final Class type) {

		SearchAttributeGroup attrs          = new SearchAttributeGroup(Occur.SHOULD);
		List<SearchAttribute> attrsInternal = getTypeAndSubtypesInternal(type, true);

		for (SearchAttribute attr : attrsInternal) {

			attrs.add(attr);
		}

		return attrs;

	}

	public static SearchAttribute orName(final String searchString) {
		return new PropertySearchAttribute(AbstractNode.name, searchString, Occur.SHOULD, false);
	}

	public static SearchAttribute<String> andName(final String searchString) {
		return new PropertySearchAttribute(AbstractNode.name, searchString, Occur.MUST, false);
	}

	public static <T> SearchAttribute<T> andProperty(final SecurityContext securityContext, final PropertyKey<T> key, final T searchValue) {
		return key.getSearchAttribute(securityContext, Occur.MUST, searchValue, false);
	}

	public static SearchAttribute<String> orExactType(final Class type) {
		return orType(type, true);
	}
	
	public static SearchAttribute<String> orType(final Class type, boolean isExactMatch) {
		return new TypeSearchAttribute(type, Occur.SHOULD, isExactMatch);
	}

	public static SearchAttribute<String> andExactType(final Class type) {
		return andType(type, true);
	}

	public static SearchAttribute<String> andType(final Class type, boolean isExactMatch) {
		return new TypeSearchAttribute(type, Occur.MUST, isExactMatch);
	}

	public static SearchAttribute andExactRelType(final Class<? extends Relation> namedRelation) {
		return new PropertySearchAttribute(AbstractRelationship.type, namedRelation.getSimpleName(), Occur.MUST, true);
	}

	public static SearchAttribute orExactRelType(final Class<? extends Relation> namedRelation) {
		return new PropertySearchAttribute(AbstractRelationship.type, namedRelation.getSimpleName(), Occur.SHOULD, true);
	}

	public static SearchAttribute orExactName(final String searchString) {
		return new PropertySearchAttribute(AbstractNode.name, searchString, Occur.SHOULD, true);
	}

	public static SearchAttribute andExactName(final String searchString) {
		return new PropertySearchAttribute(AbstractNode.name, searchString, Occur.MUST, true);
	}

	public static SearchAttribute andExactUuid(final String searchString) {
		return new PropertySearchAttribute(GraphObject.id, searchString, Occur.MUST, true);
	}

	public static <T> SearchAttribute<T> andExactProperty(final SecurityContext securityContext, final PropertyKey<T> propertyKey, final T searchValue) {
		return propertyKey.getSearchAttribute(securityContext, Occur.MUST, searchValue, true);
	}

	public static <T> SearchAttribute<T> orExactProperty(final SecurityContext securityContext, final PropertyKey<T> propertyKey, final T searchValue) {
		return propertyKey.getSearchAttribute(securityContext, Occur.SHOULD, searchValue, true);
	}

	public static String unquoteExactMatch(final String searchString) {

		String result = searchString;

		if (searchString.startsWith("\"")) {

			result = result.substring(1);
		}

		if (searchString.endsWith("\"")) {

			result = result.substring(0, result.length() - 1);
		}

		return result;

	}

	/**
	 * Normalize special characters to ASCII
	 *
	 * @param input
	 * @return
	 */
	public static String normalize(final String input) {

		String normalized = Normalizer.normalize(input, Normalizer.Form.NFD);

		return normalized.replaceAll("[^\\p{ASCII}]", "");

	}

	/**
	 * Remove dangerous characters from a search string
	 *
	 * @param input
	 * @return
	 */
	public static String clean(final String input) {

//              String output = Normalizer.clean(input, Form.NFD);
		String output = StringUtils.trim(input);

//              String output = input;
		// Remove all kinds of quotation marks
		output = StringUtils.replace(output, "´", "");
		output = StringUtils.replace(output, "`", "");
		output = StringUtils.replace(output, "'", "");

		// output = StringUtils.replace(output, ".", "");
		output = StringUtils.replace(output, ",", "");
		output = StringUtils.replace(output, " - ", "");
		output = StringUtils.replace(output, "- ", "");
		output = StringUtils.replace(output, " -", "");
		output = StringUtils.replace(output, "=", "");
		output = StringUtils.replace(output, "<", "");
		output = StringUtils.replace(output, ">", "");

		// Remove Lucene special characters
		//
		// + - && || ! ( ) { } [ ] ^ " ~ * ? : \
		output = StringUtils.replace(output, "+", "");

		// output = StringUtils.replace(output, "-", "");
		output = StringUtils.replace(output, "&&", "");
		output = StringUtils.replace(output, "||", "");
		output = StringUtils.replace(output, "!", "");
		output = StringUtils.replace(output, "(", "");
		output = StringUtils.replace(output, ")", "");
		output = StringUtils.replace(output, "{", "");
		output = StringUtils.replace(output, "}", "");
		output = StringUtils.replace(output, "[", "");
		output = StringUtils.replace(output, "]", "");
		output = StringUtils.replace(output, "^", "");
		output = StringUtils.replace(output, "\"", "");
		output = StringUtils.replace(output, "~", "");
		output = StringUtils.replace(output, "*", "");
		output = StringUtils.replace(output, "?", "");
		output = StringUtils.replace(output, ":", "");
		output = StringUtils.replace(output, "\\", "");

		return output;
	}

	public static String escapeForLucene(String input) {

		StringBuilder output = new StringBuilder();

		for (int i = 0; i < input.length(); i++) {

			char c = input.charAt(i);

			if (specialChars.contains(c) || Character.isWhitespace(c)) {

				output.append('\\');
			}

			output.append(c);

		}

		return output.toString();

	}
	
	/*
	public static String escapeForLuceneExact(String input) {

		if (input == null) {

			return null;
		}

		StringBuilder output = new StringBuilder();

		for (int i = 0; i < input.length(); i++) {

			char c = input.charAt(i);

			if (specialCharsExact.contains(c) || Character.isWhitespace(c)) {

				output.append('\\');
			}

			output.append(c);

		}

		return output.toString();

	}
	*/
	
	//~--- get methods ----------------------------------------------------

	/**
	 * Return a list with all nodes matching the given string
	 *
	 * Internally, the wildcard character '*' will be appended to the string.
	 *
	 * @param securityContext
	 * @param string
	 * @return
	 */
	public static List<String> getNodeNamesLike(SecurityContext securityContext, final String string) {

		List<String> names                = new LinkedList<>();

		try {

			Result<NodeInterface> result = StructrApp.getInstance(securityContext).nodeQuery(NodeInterface.class).andName(string + SearchAttribute.WILDCARD).getResult();

			if (result != null) {

				for (NodeInterface node : result.getResults()) {

					names.add(node.getName());
				}

			}

		} catch (FrameworkException fex) {

			logger.log(Level.WARNING, "Unable to execute SearchNodeCommand", fex);

		}

		return names;

	}
	
}
