/*
 * =============================================================================
 *
 *   Copyright (c) 2011-2014, The THYMELEAF team (http://www.thymeleaf.org)
 *
 *   Licensed under the Apache License, Version 2.0 (the "License");
 *   you may not use this file except in compliance with the License.
 *   You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 *   Unless required by applicable law or agreed to in writing, software
 *   distributed under the License is distributed on an "AS IS" BASIS,
 *   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *   See the License for the specific language governing permissions and
 *   limitations under the License.
 *
 * =============================================================================
 */
package org.thymeleaf.engine.markup.handler;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import org.thymeleaf.engine.util.TextUtil;
import org.thymeleaf.util.StringUtils;

/**
 *
 * @author Daniel Fern&aacute;ndez
 * @since 3.0.0
 *
 */
final class MarkupSelectorItem {

    static final String TEXT_SELECTOR = "text()";
    static final String ID_MODIFIER_SEPARATOR = "#";
    static final String CLASS_MODIFIER_SEPARATOR = ".";
    static final String REFERENCE_MODIFIER_SEPARATOR = "%";

    static final String ID_ATTRIBUTE_NAME = "id";
    static final String CLASS_ATTRIBUTE_NAME = "class";

    static final String ODD_SELECTOR = "odd()";
    static final String EVEN_SELECTOR = "even()";



    private final boolean caseSensitive;
    private final boolean anyLevel;
    private final boolean textSelector;
    private final String elementName;
    private final String referenceName;
    private final IndexCondition index;
    private final List<AttributeCondition> attributeConditions;
    private final boolean requiresAttributesInElement;


    // TODO Add to github issues a ticket explaining that the syntax is now extended to:
    //                * support attribute "existence" and "non-existence"
    //                * support indexes "even()" and "odd()"
    //                * support "contains" attribute value with "*="
    //                * support for > and < in indexed selectors

    MarkupSelectorItem(
            final boolean caseSensitive, final boolean anyLevel, final boolean textSelector, final String elementName,
            final String referenceName, final IndexCondition index, final List<AttributeCondition> attributeConditions) {

        super();

        this.caseSensitive = caseSensitive;
        this.anyLevel = anyLevel;
        this.textSelector = textSelector;
        this.elementName = elementName;
        this.referenceName = referenceName;
        this.index = index;
        this.attributeConditions = Collections.unmodifiableList(attributeConditions);

        // This is used in order to perform quick checks when matching: if this selector requires the existence
        // of at least one attribute in the element and the element has none, we know it won't match.
        boolean newRequiresAttributesInElement = false;
        for (final AttributeCondition attributeCondition : this.attributeConditions) {
            if (!attributeCondition.operator.equals(AttributeCondition.Operator.NOT_EQUALS) &&
                !attributeCondition.operator.equals(AttributeCondition.Operator.NOT_EXISTS)) {
                newRequiresAttributesInElement = true;
            }
        }
        this.requiresAttributesInElement = newRequiresAttributesInElement;

    }




    public String toString() {

        final StringBuilder strBuilder = new StringBuilder();

        if (this.anyLevel) {
            strBuilder.append("//");
        } else {
            strBuilder.append("/");
        }

        if (this.elementName != null) {
            strBuilder.append(this.elementName);
        } else if (this.textSelector) {
            strBuilder.append(TEXT_SELECTOR);
        } else {
            strBuilder.append("*");
        }

        if (this.referenceName != null) {
            strBuilder.append("[$ref$=");
            strBuilder.append("'" + this.referenceName + "'");
            strBuilder.append("]");
        }

        if (this.attributeConditions != null && !this.attributeConditions.isEmpty()) {
            strBuilder.append("[");
            strBuilder.append(this.attributeConditions.get(0).name);
            strBuilder.append(this.attributeConditions.get(0).operator.text);
            if (this.attributeConditions.get(0).value != null) {
                strBuilder.append("'" + this.attributeConditions.get(0).value + "'");
            }
            for (int i = 1; i < this.attributeConditions.size(); i++) {
                strBuilder.append(" and ");
                strBuilder.append(this.attributeConditions.get(i).name);
                strBuilder.append(this.attributeConditions.get(i).operator.text);
                if (this.attributeConditions.get(i).value != null) {
                    strBuilder.append("'" + this.attributeConditions.get(i).value + "'");
                }
            }
            strBuilder.append("]");
        }

        if (this.index != null) {
            strBuilder.append("[");
            switch (this.index.type) {
                case VALUE:
                    strBuilder.append(this.index.value);
                    break;
                case LESS_THAN:
                    strBuilder.append("<" + this.index.value);
                    break;
                case MORE_THAN:
                    strBuilder.append(">" + this.index.value);
                    break;
                case EVEN:
                    strBuilder.append(EVEN_SELECTOR);
                    break;
                case ODD:
                    strBuilder.append(ODD_SELECTOR);
                    break;
            }
            strBuilder.append("]");
        }

        return strBuilder.toString();

    }







    static final class AttributeCondition {

        static enum Operator {

            EQUALS("="), NOT_EQUALS("!="), STARTS_WITH("^="), ENDS_WITH("$="), EXISTS("*"), NOT_EXISTS("!"), CONTAINS("*=");

            private String text;
            Operator(final String text) {
                this.text = text;
            }

        }

        final String name;
        final Operator operator;
        final String value;

        AttributeCondition(final String name, final Operator operator, final String value) {
            super();
            this.name = name;
            this.operator = operator;
            this.value = value;
        }

    }




    static final class IndexCondition {

        static enum IndexConditionType { VALUE, LESS_THAN, MORE_THAN, EVEN, ODD }
        static IndexCondition INDEX_CONDITION_ODD = new IndexCondition(IndexConditionType.ODD, -1);
        static IndexCondition INDEX_CONDITION_EVEN = new IndexCondition(IndexConditionType.EVEN, -1);

        final IndexConditionType type;
        final int value;

        IndexCondition(final IndexConditionType type, final int value) {
            super();
            this.type = type;
            this.value = value;
        }

    }












    /*
     * -------------------
     * Matching operations
     * -------------------
     */

    boolean anyLevel() {
        return this.anyLevel;
    }


    boolean matchesText() {
        return this.textSelector;
    }


    boolean matches(final int markupBlockIndex, final ElementBuffer elementBuffer,
                    final MarkupSelectorFilter.MarkupBlockMatchingCounter markupBlockMatchingCounter) {

        if (this.textSelector) {
            return false;
        }

        // Quick check on attributes: if selector needs at least one and this element has none (very common case),
        // we know matching will be false.
        if (this.requiresAttributesInElement && elementBuffer.attributeCount == 0) {
            return false;
        }

        // Check the element name. No need to check the "caseSensitive" flag here, because we are checking
        // a normalized element name (which will be already lower cased if the nature of the element requires it,
        // i.e. it comes from HTML parsing), and the element name in a markup selector item, which will have already
        // been created lower-cased if the item was created with the case-sensitive flag set to false.
        if (this.elementName != null &&
                !elementBuffer.normalizedElementName.equals(this.elementName)) {
            return false;
        }

        // Check the attribute values and their operators
        if (this.attributeConditions != null &&
                !this.attributeConditions.isEmpty()) {

            final int attributeConditionsLen = this.attributeConditions.size();
            for (int i = 0; i < attributeConditionsLen; i++) {

                final String attrName = this.attributeConditions.get(i).name;
                final MarkupSelectorItem.AttributeCondition.Operator attrOperator = this.attributeConditions.get(i).operator;
                final String attrValue = this.attributeConditions.get(i).value;

                if (!matchesAttribute(elementBuffer, attrName, attrOperator, attrValue)) {
                    return false;
                }

            }

        }

        // Last thing to test, once we know all other things match, we should check if this selector includes an index
        // and, if it does, check the position of this matching block among all its MATCHING siblings (children of the
        // same parent) by accessing the by-block-index counters. (A block index identifies all the children of the
        // same parent).
        if (this.index != null) {
            return matchesIndex(markupBlockIndex, markupBlockMatchingCounter);
        }

        // Everything has gone right so far, so this has matched
        return true;

    }



    private boolean matchesAttribute(
            final ElementBuffer elementBuffer,
            final String attrName, final MarkupSelectorItem.AttributeCondition.Operator attrOperator, final String attrValue) {

        boolean found = false;
        for (int i = 0; i < elementBuffer.attributeCount; i++) {

            if (!TextUtil.equals(this.caseSensitive,
                    attrName, 0, attrName.length(),
                    elementBuffer.attributeBuffers[i], 0, elementBuffer.attributeNameLens[i])) {
                continue;
            }

            // Even if both HTML and XML forbid duplicated attributes, we are going to anyway going to allow
            // them and not consider an attribute "not-matched" just because it doesn't match in one of its
            // instances.
            found = true;

            if ("class".equals(attrName)) {

                // The attribute we are comparing is actually the "class" attribute, which requires an special treatment
                if (matchesClassAttributeValue(
                        attrOperator, attrValue,
                        elementBuffer.attributeBuffers[i], elementBuffer.attributeValueContentOffsets[i], elementBuffer.attributeValueContentLens[i])) {
                    return true;
                }

            } else {

                if (matchesAttributeValue(
                        attrOperator, attrValue,
                        elementBuffer.attributeBuffers[i], elementBuffer.attributeValueContentOffsets[i], elementBuffer.attributeValueContentLens[i])) {
                    return true;
                }

            }


        }

        if (found) {
            // The attribute existed, but it didn't match - we just checked until the end in case there were duplicates
            return false;
        }

        // Attribute was not found in element, so we will consider it a match if the operator is NOT_EXISTS
        return MarkupSelectorItem.AttributeCondition.Operator.NOT_EXISTS.equals(attrOperator);

    }




    private static boolean matchesAttributeValue(
            final MarkupSelectorItem.AttributeCondition.Operator attrOperator,
            final String attrValue,
            final char[] elementAttrValueBuffer, final int elementAttrValueOffset, final int elementAttrValueLen) {

        switch (attrOperator) {

            case EQUALS:
                // Test equality: we are testing values, so we always use case-sensitivity = true
                return TextUtil.equals(true,
                        attrValue,              0,                      attrValue.length(),
                        elementAttrValueBuffer, elementAttrValueOffset, elementAttrValueLen);

            case NOT_EQUALS:
                // Test inequality: we are testing values, so we always use case-sensitivity = true
                return !TextUtil.equals(true,
                        attrValue,              0,                      attrValue.length(),
                        elementAttrValueBuffer, elementAttrValueOffset, elementAttrValueLen);

            case STARTS_WITH:
                return TextUtil.startsWith(true,
                        elementAttrValueBuffer, elementAttrValueOffset, elementAttrValueLen,
                        attrValue,              0,                      attrValue.length());

            case ENDS_WITH:
                return TextUtil.endsWith(true,
                        elementAttrValueBuffer, elementAttrValueOffset, elementAttrValueLen,
                        attrValue,              0,                      attrValue.length());

            case CONTAINS:
                return TextUtil.contains(true,
                        elementAttrValueBuffer, elementAttrValueOffset, elementAttrValueLen,
                        attrValue,              0,                      attrValue.length());

            case EXISTS:
                // The fact that this attribute exists is enough to return true
                return true;

            case NOT_EXISTS:
                // This attribute should not exist in order to match
                return false;

            default:
                throw new IllegalArgumentException("Unknown operator: " + attrOperator);

        }

    }


    private static boolean matchesClassAttributeValue(
            final MarkupSelectorItem.AttributeCondition.Operator attrOperator,
            final String attrValue,
            final char[] elementAttrValueBuffer, final int elementAttrValueOffset, final int elementAttrValueLen) {

        if (elementAttrValueLen == 0) {
            return StringUtils.isEmptyOrWhitespace(attrValue);
        }

        int i = 0;

        while (i < elementAttrValueLen && Character.isWhitespace(elementAttrValueBuffer[elementAttrValueOffset + i])) { i++; }

        if (i == elementAttrValueLen) {
            return StringUtils.isEmptyOrWhitespace(attrValue);
        }

        while (i < elementAttrValueLen) {

            final int lastOffset = elementAttrValueOffset + i;

            while (i < elementAttrValueLen && !Character.isWhitespace(elementAttrValueBuffer[elementAttrValueOffset + i])) { i++; }

            if (matchesAttributeValue(attrOperator, attrValue, elementAttrValueBuffer, lastOffset, (elementAttrValueOffset + i) - lastOffset)) {
                return true;
            }

            while (i < elementAttrValueLen && Character.isWhitespace(elementAttrValueBuffer[elementAttrValueOffset + i])) { i++; }

        }

        return false;

    }




    private boolean matchesIndex(
            final int markupBlockIndex, final MarkupSelectorFilter.MarkupBlockMatchingCounter markupBlockMatchingCounter) {

        // Didn't previously exist: initialize. Given few selectors use indexes, this allows us to avoid creating
        // these array structures if not needed.
        if (markupBlockMatchingCounter.counters == null) {
            markupBlockMatchingCounter.indexes = new int[MarkupSelectorFilter.MarkupBlockMatchingCounter.DEFAULT_COUNTER_SIZE];
            markupBlockMatchingCounter.counters = new int[MarkupSelectorFilter.MarkupBlockMatchingCounter.DEFAULT_COUNTER_SIZE];
            Arrays.fill(markupBlockMatchingCounter.indexes, -1);
            Arrays.fill(markupBlockMatchingCounter.counters, -1);
        }

        // Check whether we already had a counter for this current markup block index
        int i = 0;
        while (i < markupBlockMatchingCounter.indexes.length
                && markupBlockMatchingCounter.indexes[i] >= 0 // Will stop at the first -1
                && markupBlockMatchingCounter.indexes[i] != markupBlockIndex) { i++; }

        // If no counter found and the array is already full, grow structures
        if (i == markupBlockMatchingCounter.indexes.length) {
            final int[] newMarkupBlockMatchingIndexes = new int[markupBlockMatchingCounter.indexes.length + MarkupSelectorFilter.MarkupBlockMatchingCounter.DEFAULT_COUNTER_SIZE];
            final int[] newMarkupBlockMatchingCounters = new int[markupBlockMatchingCounter.counters.length + MarkupSelectorFilter.MarkupBlockMatchingCounter.DEFAULT_COUNTER_SIZE];
            Arrays.fill(newMarkupBlockMatchingIndexes, -1);
            Arrays.fill(newMarkupBlockMatchingCounters, -1);
            System.arraycopy(markupBlockMatchingCounter.indexes, 0, newMarkupBlockMatchingIndexes, 0, markupBlockMatchingCounter.indexes.length);
            System.arraycopy(markupBlockMatchingCounter.counters, 0, newMarkupBlockMatchingCounters, 0, markupBlockMatchingCounter.counters.length);
            markupBlockMatchingCounter.indexes = newMarkupBlockMatchingIndexes;
            markupBlockMatchingCounter.counters = newMarkupBlockMatchingCounters;
        }

        // If the counter is new, initialize it. If not, increase it
        if (markupBlockMatchingCounter.indexes[i] == -1) {
            markupBlockMatchingCounter.indexes[i] = markupBlockIndex;
            markupBlockMatchingCounter.counters[i] = 0;
        } else {
            markupBlockMatchingCounter.counters[i]++;
        }

        switch (this.index.type) {
            case VALUE:
                if (this.index.value != markupBlockMatchingCounter.counters[i]) {
                    return false;
                }
                break;
            case LESS_THAN:
                if (this.index.value <= markupBlockMatchingCounter.counters[i]) {
                    return false;
                }
                break;
            case MORE_THAN:
                if (this.index.value >= markupBlockMatchingCounter.counters[i]) {
                    return false;
                }
                break;
            case EVEN:
                if (markupBlockMatchingCounter.counters[i] % 2 != 0) {
                    return false;
                }
                break;
            case ODD:
                if (markupBlockMatchingCounter.counters[i] % 2 == 0) {
                    return false;
                }
                break;
        }

        return true;

    }




}
