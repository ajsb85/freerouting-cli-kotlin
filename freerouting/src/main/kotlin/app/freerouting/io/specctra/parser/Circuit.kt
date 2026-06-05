package app.freerouting.io.specctra.parser

import app.freerouting.logger.FRLogger
import java.io.IOException
import java.util.Arrays
import java.util.LinkedList

class Circuit private constructor() {

    /**
     * A max_length of -1 indicates, that no maximum length is defined.
     */
    class ReadScopeResult(
        @JvmField val max_length: Double,
        @JvmField val min_length: Double,
        @JvmField val use_via: Collection<String>,
        @JvmField val use_layer: Collection<String>
    )

    /**
     * A max_length of -1 indicates, that no maximum length is defined.
     */
    internal class LengthMatchingRule(
        val max_length: Double,
        val min_length: Double
    )

    companion object {
        /**
         * Currently only the length matching rule is read from a circuit scope. If the scope does not contain a length matching rule, null is returned.
         */
        @JvmStatic
        fun read_scope(p_scanner: IJFlexScanner): ReadScopeResult? {
            var next_token: Any? = null
            var min_trace_length = 0.0
            var max_trace_length = 0.0
            val use_via = LinkedList<String>()
            val use_layer = LinkedList<String>()
            while (true) {
                val prev_token = next_token
                try {
                    next_token = p_scanner.next_token()
                } catch (e: IOException) {
                    FRLogger.error("Circuit.read_scope: IO error scanning file", e)
                    return null
                }
                if (next_token == null) {
                    FRLogger.warn("Circuit.read_scope: unexpected end of file at '" + p_scanner.get_scope_identifier() + "'")
                    return null
                }
                if (next_token === Keyword.CLOSED_BRACKET) {
                    // end of scope
                    break
                }
                if (prev_token === Keyword.OPEN_BRACKET) {
                    if (next_token === Keyword.LENGTH) {
                        val length_rule = read_length_scope(p_scanner)
                        if (length_rule != null) {
                            min_trace_length = length_rule.min_length
                            max_trace_length = length_rule.max_length
                        }
                    } else if (next_token === Keyword.USE_VIA) {
                        val vias = Structure.read_via_padstacks(p_scanner)
                        if (vias != null) {
                            use_via.addAll(vias)
                        }
                    } else if (next_token === Keyword.USE_LAYER) {
                        use_layer.addAll(Arrays.stream(DsnFile.read_string_list_scope(p_scanner)).toList())
                    } else {
                        ScopeKeyword.skip_scope(p_scanner)
                    }
                }
            }
            return ReadScopeResult(max_trace_length, min_trace_length, use_via, use_layer)
        }

        @JvmStatic
        internal fun read_length_scope(p_scanner: IJFlexScanner): LengthMatchingRule? {
            val result: LengthMatchingRule
            val length_arr = DoubleArray(2)
            var next_token: Any? = null
            for (i in 0..1) {
                try {
                    next_token = p_scanner.next_token()
                } catch (e: IOException) {
                    FRLogger.error("Circuit.read_length_scope: IO error scanning file", e)
                    return null
                }
                if (next_token is Double) {
                    length_arr[i] = next_token
                } else if (next_token is Int) {
                    length_arr[i] = next_token.toDouble()
                } else {
                    FRLogger.warn("Circuit.read_length_scope: number expected at '" + p_scanner.get_scope_identifier() + "'")
                    return null
                }
            }
            result = LengthMatchingRule(length_arr[0], length_arr[1])
            while (true) {
                val prev_token = next_token
                try {
                    next_token = p_scanner.next_token()
                } catch (e: IOException) {
                    FRLogger.error("Circuit.read_length_scope: IO error scanning file", e)
                    return null
                }
                if (next_token == null) {
                    FRLogger.warn("Circuit.read_length_scope: unexpected end of file at '" + p_scanner.get_scope_identifier() + "'")
                    return null
                }
                if (next_token === Keyword.CLOSED_BRACKET) {
                    // end of scope
                    break
                }
                if (prev_token === Keyword.OPEN_BRACKET) {
                    ScopeKeyword.skip_scope(p_scanner)
                }
            }
            return result
        }
    }
}
