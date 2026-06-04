package app.freerouting.io.specctra.parser

import app.freerouting.logger.FRLogger
import java.io.IOException
import java.util.LinkedList

/**
 * Contains the information of a Specctra Class scope.
 */
class NetClass(
    @JvmField val name: String,
    @JvmField val trace_clearance_class: String?,
    @JvmField val net_list: Collection<String>,
    @JvmField val rules: Collection<Rule>,
    @JvmField val layer_rules: Collection<Rule.LayerRule>,
    @JvmField val use_via: Collection<String>,
    @JvmField val use_layer: Collection<String>,
    @JvmField val via_rule: String?,
    @JvmField val shove_fixed: Boolean,
    @JvmField val pull_tight: Boolean,
    @JvmField val min_trace_length: Double,
    @JvmField val max_trace_length: Double
) {

    class ClassClass(
        @JvmField val class_names: Collection<String>,
        @JvmField val rules: Collection<Rule>,
        @JvmField val layer_rules: Collection<Rule.LayerRule>
    )

    companion object {
        @JvmStatic
        fun read_scope(p_scanner: IJFlexScanner): NetClass? {
            try {
                // read the class name
                p_scanner.yybegin(SpecctraDsnStreamReader.NAME)
                val class_name = p_scanner.next_string() ?: ""

                val net_list = LinkedList<String>()
                val rules_missing = false
                // read the nets belonging to the class
                val netsInTheClass = p_scanner.next_string_list()
                if (netsInTheClass != null) {
                    net_list.addAll(netsInTheClass)
                }

                val rules = LinkedList<Rule>()
                val layer_rules = LinkedList<Rule.LayerRule>()
                val use_via = LinkedList<String>()
                val use_layer = LinkedList<String>()
                var via_rule: String? = null
                var trace_clearance_class: String? = null
                var pull_tight = true
                var shove_fixed = false
                var min_trace_length = 0.0
                var max_trace_length = 0.0

                var next_token = p_scanner.next_token()
                if (!rules_missing) {
                    var prev_token = next_token
                    while (true) {
                        next_token = p_scanner.next_token()
                        if (next_token == null) {
                            FRLogger.warn("NetClass.read_scope: unexpected end of file at '" + p_scanner.get_scope_identifier() + "'")
                            return null
                        }
                        if (next_token === Keyword.CLOSED_BRACKET) {
                            // end of scope
                            break
                        }
                        if (prev_token === Keyword.OPEN_BRACKET) {
                            if (next_token === Keyword.RULE) {
                                val parsedRules = Rule.read_scope(p_scanner) ?: return null
                                rules.addAll(parsedRules)
                            } else if (next_token === Keyword.LAYER_RULE) {
                                val parsedLayerRule = Rule.read_layer_rule_scope(p_scanner) ?: return null
                                layer_rules.add(parsedLayerRule)
                            } else if (next_token === Keyword.VIA_RULE) {
                                via_rule = DsnFile.read_string_scope(p_scanner)
                            } else if (next_token === Keyword.CIRCUIT) {
                                val curr_rule = Circuit.read_scope(p_scanner)
                                if (curr_rule != null) {
                                    max_trace_length = curr_rule.max_length
                                    min_trace_length = curr_rule.min_length
                                    use_via.addAll(curr_rule.use_via)
                                    use_layer.addAll(curr_rule.use_layer)
                                }
                            } else if (next_token === Keyword.CLEARANCE_CLASS) {
                                trace_clearance_class = DsnFile.read_string_scope(p_scanner)
                                if (trace_clearance_class == null) {
                                    return null
                                }
                            } else if (next_token === Keyword.SHOVE_FIXED) {
                                shove_fixed = DsnFile.read_on_off_scope(p_scanner)
                            } else if (next_token === Keyword.PULL_TIGHT) {
                                pull_tight = DsnFile.read_on_off_scope(p_scanner)
                            } else {
                                ScopeKeyword.skip_scope(p_scanner)
                            }
                        }
                        prev_token = next_token
                    }
                }
                return NetClass(
                    class_name,
                    trace_clearance_class,
                    net_list,
                    rules,
                    layer_rules,
                    use_via,
                    use_layer,
                    via_rule,
                    shove_fixed,
                    pull_tight,
                    min_trace_length,
                    max_trace_length
                )
            } catch (e: IOException) {
                FRLogger.error("NetClass.read_scope: IO error while scanning file", e)
                return null
            }
        }

        @JvmStatic
        fun read_class_class_scope(p_scanner: IJFlexScanner): ClassClass? {
            try {
                val classes = LinkedList<String>()
                val rules = LinkedList<Rule>()
                val layer_rules = LinkedList<Rule.LayerRule>()
                var prev_token: Any? = null
                while (true) {
                    val next_token = p_scanner.next_token()
                    if (next_token == null) {
                        FRLogger.warn("ClassClass.read_scope: unexpected end of file at '" + p_scanner.get_scope_identifier() + "'")
                        return null
                    }
                    if (next_token === Keyword.CLOSED_BRACKET) {
                        // end of scope
                        break
                    }
                    if (prev_token === Keyword.OPEN_BRACKET) {
                        if (next_token === Keyword.CLASSES) {
                            val classNames = DsnFile.read_string_list_scope(p_scanner)
                            if (classNames != null) {
                                classes.addAll(classNames)
                            }
                        } else if (next_token === Keyword.RULE) {
                            val parsedRules = Rule.read_scope(p_scanner) ?: return null
                            rules.addAll(parsedRules)
                        } else if (next_token === Keyword.LAYER_RULE) {
                            val parsedLayerRule = Rule.read_layer_rule_scope(p_scanner) ?: return null
                            layer_rules.add(parsedLayerRule)
                        }
                    }
                    prev_token = next_token
                }
                return ClassClass(classes, rules, layer_rules)
            } catch (e: IOException) {
                FRLogger.error("NetClass.read_scope: IO error while scanning file", e)
                return null
            }
        }
    }
}
