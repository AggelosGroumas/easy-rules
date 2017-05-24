/**
 * The MIT License
 *
 *  Copyright (c) 2017, Mahmoud Ben Hassine (mahmoud.benhassine@icloud.com)
 *
 *  Permission is hereby granted, free of charge, to any person obtaining a copy
 *  of this software and associated documentation files (the "Software"), to deal
 *  in the Software without restriction, including without limitation the rights
 *  to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 *  copies of the Software, and to permit persons to whom the Software is
 *  furnished to do so, subject to the following conditions:
 *
 *  The above copyright notice and this permission notice shall be included in
 *  all copies or substantial portions of the Software.
 *
 *  THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 *  IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 *  FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 *  AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 *  LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 *  OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 *  THE SOFTWARE.
 */
package org.easyrules.core;

import org.easyrules.api.Rule;
import org.easyrules.exceptions.RuleLogicalConnectionFormatException;
import org.easyrules.exceptions.RuleNameNotFoundInLogicalConnectionException;
import org.slf4j.Logger;

import javax.script.ScriptEngine;
import javax.script.ScriptEngineManager;
import javax.script.ScriptException;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.Stack;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.easyrules.core.RuleProxy.asRule;
import static org.slf4j.LoggerFactory.getLogger;

/**
 * Class representing a composite rule composed of a set of rules.
 *
 * A composite rule is triggered if <strong>ALL</strong> conditions of its composing rules are satisfied.
 * When a composite rule is applied, actions of <strong>ALL</strong> composing rules are performed.
 *
 * @author Mahmoud Ben Hassine (mahmoud.benhassine@icloud.com)
 */
public class CompositeRule extends BasicRule {

    private static final Logger LOG = getLogger(CompositeRule.class);
    /**
     * Leverage the jdk javascript engine to evaluate a string logical expression
     */
    private static final ScriptEngineManager SCRIPT_ENGINE_MANAGER = new ScriptEngineManager();
    private static final ScriptEngine SCRIPT_ENGINE = SCRIPT_ENGINE_MANAGER.getEngineByName("nashorn");

    /**
     * The set of composing rules.
     */
    protected Set<Rule> rules;

    protected Map<Object, Rule> proxyRules;

    protected String logicalConjunction;

    /**
     * Create a new {@link CompositeRule}.
     */
    public CompositeRule() {
        this(Rule.DEFAULT_NAME, Rule.DEFAULT_DESCRIPTION, Rule.DEFAULT_PRIORITY);
    }

    /**
     * Create a new {@link CompositeRule}.
     *
     * @param name rule name
     */
    public CompositeRule(final String name) {
        this(name, Rule.DEFAULT_DESCRIPTION, Rule.DEFAULT_PRIORITY);
    }

    /**
     * Create a new {@link CompositeRule}.
     *
     * @param name rule name
     * @param description rule description
     */
    public CompositeRule(final String name, final String description) {
        this(name, description, Rule.DEFAULT_PRIORITY);
    }

    /**
     * Create a new {@link CompositeRule}.
     *
     * @param name rule name
     * @param description rule description
     * @param priority rule priority
     */
    public CompositeRule(final String name, final String description, final int priority) {
        super(name, description, priority);
        rules = new TreeSet<>();
        proxyRules = new HashMap<>();
    }

    /**
     * A composite rule is triggered if the object's logical connection expression is evaluated to true.
     * @return true if <strong>ALL</strong> conditions of composing rules are evaluated to true
     */
    @Override
    public boolean evaluate() {
        boolean result = false;
        try {
            validateLogicalConnectionFormat(logicalConjunction);
            if (!rules.isEmpty()) {
                Map<String, Boolean> resultRuleMap = new HashMap<>();
                for (Rule rule : rules) {
                    resultRuleMap.put(rule.getName(), rule.evaluate());
                }
                if (logicalConjunction == null || logicalConjunction.isEmpty()) {
                    result = !resultRuleMap.containsValue(false);
                } else {
                    String evalLogicalConnection = prepareExpression(resultRuleMap, logicalConjunction);
                    result = (Boolean) SCRIPT_ENGINE.eval(evalLogicalConnection);
                }
            }
        } catch (RuleLogicalConnectionFormatException e) {
            LOG.error(e.getMessage());
        } catch (RuleNameNotFoundInLogicalConnectionException e) {
            LOG.error(e.getMessage());
        } catch (ScriptException e) {
            LOG.error("Something happened with the javascript engine. Returning false.");
            e.printStackTrace();
        }
        return result;
    }

    /**
     * Takes as input a logical connection string with the rules names, and replaces
     * each rule name with its evaluation outcome.
     * Example: <br>
     * &#09;input: [myRule] && ([yourRule] || [herRule])<br>
     * &#09;output: true && (false || true)<br>
     *
     * @param rulesEvalResultMap The rule_name - rule_evaluation map
     * @param logicalConnection  The logical connection string
     * @return The logicalConjunction string with the replaced boolean values
     * @throws RuleNameNotFoundInLogicalConnectionException If a rule name in the logicalConjunction string is not found in the rulesEvalResultMap.
     */
    private String prepareExpression(Map<String, Boolean> rulesEvalResultMap, String logicalConnection) throws RuleNameNotFoundInLogicalConnectionException {
        Pattern pattern = Pattern.compile("\\[(.+?)\\]");
        Matcher matcher = pattern.matcher(logicalConnection);
        StringBuilder builder = new StringBuilder();
        int i = 0, countMatches = 0;
        while (matcher.find()) {
            countMatches++;
            String name = matcher.group(1);
            Object replacement = rulesEvalResultMap.get(name);
            if (replacement == null) {
                throw new RuleNameNotFoundInLogicalConnectionException(String.format("Rule named {%s} not found in logical connection string. Returning false.", name));
            }
            builder.append(logicalConnection.substring(i, matcher.start()));
            builder.append(replacement.toString());
            i = matcher.end();
        }
        builder.append(logicalConnection.substring(i, logicalConnection.length()));
        return builder.toString();
    }

    /**
     * Validates that the desired logical connection of rules, has a valid format.
     * Sample valid format examples:
     * <ul>
     * <li>[myRule] && ([yourRule] || [herRule])</li>
     * <li>[myRule] || [yourRule] || [herRule]</li>
     * <li>([myRule] || [yourRule]) && [herRule]</li>
     * </ul>
     *
     * @param logicalConnection The desired logical connection of rules.
     * @throws RuleLogicalConnectionFormatException If the format is invalid this exception is thrown
     */
    private void validateLogicalConnectionFormat(String logicalConnection) throws RuleLogicalConnectionFormatException {
        String format = "(\\(?\\[(.+?)\\]\\)?)(\\s(&&|\\|\\|)\\s)?";
        if ( logicalConnection != null && (!logicalConnection.matches(format) || !balancedParenthensies(logicalConnection))) {
            throw new RuleLogicalConnectionFormatException(String.format("Logical connection format {%s} is invalid. Returning false.", logicalConnection));
        }
    }

    private boolean balancedParenthensies(String s) {
        Stack<Character> stack = new Stack<Character>();
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c == '[' || c == '(' || c == '{') {
                stack.push(c);
            } else if (c == ']') {
                if (stack.isEmpty()) {
                    return false;
                }
                if (stack.pop() != '[') {
                    return false;
                }
            } else if (c == ')') {
                if (stack.isEmpty()) {
                    return false;
                }
                if (stack.pop() != '(') {
                    return false;
                }
            } else if (c == '}') {
                if (stack.isEmpty()) {
                    return false;
                }
                if (stack.pop() != '{') {
                    return false;
                }
            }
        }
        return stack.isEmpty();
    }

    /**
     * When a composite rule is applied, <strong>ALL</strong> actions of composing rules are performed
     * in their natural order.
     *
     * @throws Exception thrown if an exception occurs during actions performing
     */
    @Override
    public void execute() throws Exception {
        for (Rule rule : rules) {
            rule.execute();
        }
    }

    /**
     * Add a rule to the composite rule.
     * @param rule the rule to add
     */
    public void addRule(final Object rule) {
        Rule proxy = asRule(rule);
        rules.add(proxy);
        proxyRules.put(rule, proxy);
    }

    /**
     * Remove a rule from the composite rule.
     * @param rule the rule to remove
     */
    public void removeRule(final Object rule) {
        Rule proxy = proxyRules.get(rule);
        if (proxy != null) {
            rules.remove(proxy);
        }
    }

    /**
     * Sets the desired logical connection of rules. Each rule here is represented by it's name, eclosed in square brackets.
     * Sample valid format examples:
     * <ul>
     * <li>[myRuleName] && ([yourRuleName] || [herRuleName])</li>
     * <li>[myRuleName] || [yourRuleName] || [herRuleName]</li>
     * <li>([myRuleName] || [yourRuleName]) && [herRuleName]</li>
     * </ul>
     *
     * @param logicalConjunction The desired logical connection of rules.
     */
    public void setLogicalConjunction(String logicalConjunction) {
        this.logicalConjunction = logicalConjunction;
    }
}
