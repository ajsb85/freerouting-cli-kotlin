package app.freerouting.management

import app.freerouting.logger.FRLogger
import app.freerouting.settings.GlobalSettings
import java.awt.Font
import java.awt.FontFormatException
import java.awt.GraphicsEnvironment
import java.io.IOException
import java.nio.charset.StandardCharsets
import java.time.Duration
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException
import java.util.Locale
import java.util.ResourceBundle
import java.util.concurrent.ThreadLocalRandom
import java.util.regex.Pattern
import javax.crypto.Cipher
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec
import javax.swing.JButton
import javax.swing.JComponent
import javax.swing.JLabel
import javax.swing.JToggleButton

/**
 * Singleton class to manage the text resources for the application
 */
class TextManager(baseClass: Class<*>, locale: Locale) {

    private val iconMap = mapOf(
        "cog" to 0xF0493,
        "auto-fix" to 0xF0068,
        "cancel" to 0xF073A,
        "delete-sweep" to 0xF05E9,
        "undo" to 0xF054C,
        "redo" to 0xF044E,
        "spider-web" to 0xF0BCA,
        "order-bool-ascending-variant" to 0xF098F,
        "magnify-plus-cursor" to 0xF0A63,
        "magnify-minus" to 0xF034A,
        "alert" to 0xF0026,
        "close-octagon" to 0xF015C,
        "play" to 0xF040A,
        "pause" to 0xF03E4,
        "step-forward" to 0xF04D7,
        "step-backward" to 0xF04D5,
        "fast-forward" to 0xF0211,
        "rewind" to 0xF045F
    )

    private var currentLocale: Locale = locale
    private var currentBaseName: String? = null
    private var defaultMessages: ResourceBundle? = null
    private var classMessages: ResourceBundle? = null
    private var englishClassMessages: ResourceBundle? = null
    private var materialDesignIcons: Font? = null

    init {
        loadResourceBundle(baseClass.name)

        try {
            // Load the font
            materialDesignIcons = Font.createFont(
                Font.TRUETYPE_FONT,
                GlobalSettings::class.java.getResourceAsStream("/materialdesignicons-webfont.ttf")
            )

            // Register the font
            val ge = GraphicsEnvironment.getLocalGraphicsEnvironment()
            ge.registerFont(materialDesignIcons)
        } catch (e: Exception) {
            when (e) {
                is IOException, is FontFormatException -> {
                    FRLogger.error("There was a problem loading the Material Design Icons font", e)
                }
                else -> throw e
            }
        }
    }

    private fun loadResourceBundle(baseName: String) {
        this.currentBaseName = baseName

        // Load the default messages that are common to all classes
        try {
            defaultMessages = ResourceBundle.getBundle("app.freerouting.Common", currentLocale)
        } catch (e: Exception) {
            FRLogger.warn(
                "There was a problem loading the resource bundle 'app.freerouting.Common' of locale '$currentLocale'"
            )
            try {
                defaultMessages = ResourceBundle.getBundle("app.freerouting.Common", Locale.forLanguageTag("en-US"))
            } catch (ex: Exception) {
                defaultMessages = null
                FRLogger.error(
                    "There was a problem loading the resource bundle 'app.freerouting.Common' of locale 'en-US'",
                    null
                )
            }
        }

        // Load the class-specific messages
        try {
            classMessages = ResourceBundle.getBundle(currentBaseName!!, currentLocale)
        } catch (e: Exception) {
            try {
                classMessages = ResourceBundle.getBundle(currentBaseName!!, Locale.forLanguageTag("en-US"))
            } catch (ex: Exception) {
                classMessages = null
            }
        }

        // Load the fallback English messages
        try {
            englishClassMessages = ResourceBundle.getBundle(currentBaseName!!, Locale.forLanguageTag("en"))
        } catch (e: Exception) {
            // FRLogger.warn("There was a problem loading the resource bundle '" + currentBaseName + "' of locale 'en'");
        }
    }

    fun getText(key: String, vararg args: String): String {
        var text: String
        if (classMessages != null && classMessages!!.containsKey(key)) {
            text = classMessages!!.getString(key)
        } else if (defaultMessages != null && defaultMessages!!.containsKey(key)) {
            text = defaultMessages!!.getString(key)
        } else if (englishClassMessages != null && englishClassMessages!!.containsKey(key)) {
            text = englishClassMessages!!.getString(key)
        } else {
            return key
        }

        // Pattern to match {{variable_name}} placeholders
        val pattern = Pattern.compile("\\{\\{(.+?)\\}\\}")
        val matcher = pattern.matcher(text)

        // Find and replace all matches
        var argIndex = 0
        while (matcher.find()) {
            // Entire match including {{ and }}
            val placeholder = matcher.group(0)

            if (!placeholder.startsWith("{{icon:") && argIndex < args.size) {
                // replace the placeholder with the value
                text = text.replace(placeholder, args[argIndex])
                argIndex++
            }
        }

        return text
    }

    private fun insertIcons(component: JComponent, text: String): String {
        var resultText = text
        // Pattern to match {{variable_name}} placeholders
        val pattern = Pattern.compile("\\{\\{icon:(.+?)\\}\\}")
        val matcher = pattern.matcher(resultText)

        // Find all matches
        while (matcher.find()) {
            // Entire match including {{ and }}
            val placeholder = matcher.group(0)

            // Get the icon name
            val iconName = matcher.group(1)

            try {
                // Get the unicode code point for the icon
                val codePoint = iconMap[iconName] ?: continue

                // Convert the code point to a String
                resultText = resultText.replace(placeholder, String(Character.toChars(codePoint)))

                val originalFont = component.font
                component.font = materialDesignIcons!!.deriveFont(Font.PLAIN, originalFont.size * 1.5f)
            } catch (e: Exception) {
                FRLogger.error("There was a problem setting the icon for the component", e)
            }
        }

        return resultText
    }

    // Add methods to set text for different GUI components
    fun setText(component: JComponent, key: String, vararg args: String) {
        var text = getText(key, *args)
        var tooltip: String? = getText(key + "_tooltip", *args)

        if (tooltip == null || tooltip.isEmpty() || tooltip == key + "_tooltip") {
            tooltip = null
        }

        text = insertIcons(component, text)

        // Set the text for the component
        when (component) {
            is JButton -> {
                component.text = text
                if (!tooltip.isNullOrEmpty()) {
                    component.toolTipText = tooltip
                }
            }
            is JToggleButton -> {
                component.text = text
                if (!tooltip.isNullOrEmpty()) {
                    component.toolTipText = tooltip
                }
            }
            is JLabel -> {
                component.text = text
                if (!tooltip.isNullOrEmpty()) {
                    component.toolTipText = tooltip
                }
            }
            else -> {
                val componentType = component.javaClass.name
                FRLogger.warn("The component type '$componentType' is not supported")
            }
        }
    }

    fun getLocale(): Locale {
        return currentLocale
    }

    fun setLocale(locale: Locale) {
        this.currentLocale = locale
        loadResourceBundle(currentBaseName!!)
    }

    companion object {
        @JvmStatic
        fun convertInstantToString(instant: Instant): String {
            return convertInstantToString(instant, "yyyyMMdd_HHmmss")
        }

        @JvmStatic
        fun convertInstantToString(instant: Instant, format: String): String {
            val formatter = DateTimeFormatter.ofPattern(format)
            val localDateTime = LocalDateTime.ofInstant(instant, ZoneId.systemDefault())
            return localDateTime.format(formatter)
        }

        @JvmStatic
        fun generateRandomAlphanumericString(length: Int): String {
            val characters = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789"
            val randomString = StringBuilder()
            for (i in 0 until length) {
                val index = (characters.length * ThreadLocalRandom.current().nextDouble()).toInt()
                randomString.append(characters[index])
            }
            return randomString.toString()
        }

        @JvmStatic
        fun parseTimespanString(timespanString: String): Long? {
            try {
                // convert the string from "HH:mm:ss" or "mm:ss" or "ss" format to "PnDTnHnMn.nS" format
                val durationString = convertFromTimespanToDurationFormat(timespanString)
                // parse the duration
                val duration = Duration.parse(durationString)
                return duration.seconds
            } catch (e: DateTimeParseException) {
                return null
            }
        }

        @JvmStatic
        fun convertFromTimespanToDurationFormat(timespanString: String): String {
            val parts = timespanString.split(":".toRegex()).dropLastWhile { it.isEmpty() }.toTypedArray()
            val durationString = StringBuilder("PT")

            if (parts.size == 3) {
                durationString
                    .append(parts[0])
                    .append("H")
                    .append(parts[1])
                    .append("M")
                    .append(parts[2])
                    .append("S")
            } else if (parts.size == 2) {
                durationString
                    .append(parts[0])
                    .append("M")
                    .append(parts[1])
                    .append("S")
            } else if (parts.size == 1) {
                durationString
                    .append(parts[0])
                    .append("S")
            }

            return durationString.toString()
        }

        /**
         * Shortens a string to a specified number of characters by replacing the middle part with dots
         */
        @JvmStatic
        fun shortenString(text: String, peakCharacterCount: Int): String {
            var shortenedText = text
            if (text.length > peakCharacterCount * 2) {
                shortenedText = shortenedText.substring(0, peakCharacterCount) + "..." +
                        text.substring(text.length - peakCharacterCount)
            }
            return shortenedText
        }

        /**
         * Removes quotes from the beginning and end of a string
         */
        @JvmStatic
        fun removeQuotes(text: String?): String? {
            if (text == null || text.length < 2) {
                return text
            }

            var result = text
            if (result.startsWith("\"") && result.endsWith("\"")) {
                result = result.substring(1, result.length - 1)
            }

            return result
        }

        /**
         * Decrypts a string using AES-256-CBC with a passphrase
         */
        @JvmStatic
        fun decryptAes256Cbc(encodedText: ByteArray, passphrase: String): ByteArray? {
            try {
                val iv = IvParameterSpec("freeroutingivpar".toByteArray(StandardCharsets.UTF_8))
                val skeySpec = SecretKeySpec(passphrase.toByteArray(StandardCharsets.UTF_8), "AES")

                val cipher = Cipher.getInstance("AES/CBC/PKCS5PADDING")
                cipher.init(Cipher.DECRYPT_MODE, skeySpec, iv)
                return cipher.doFinal(encodedText)
            } catch (ex: Exception) {
                FRLogger.error("There was a problem decrypting the text", ex)
            }

            return null
        }

        /**
         * Unescapes unicode characters in a string
         */
        @JvmStatic
        fun unescapeUnicode(text: String): String {
            val pattern = Pattern.compile("\\\\u(\\p{XDigit}{4})")
            val matcher = pattern.matcher(text)
            val result = StringBuffer()

            while (matcher.find()) {
                val hexCode = matcher.group(1)
                val unicode = Integer.parseInt(hexCode, 16).toChar()
                matcher.appendReplacement(result, unicode.toString())
            }
            matcher.appendTail(result)

            return result.toString()
        }

        @JvmStatic
        fun longToHexadecimalString(longValue: Long): String {
            return "0x%016X".format(Locale.US, longValue)
        }

        @JvmStatic
        fun hexadecimalStringToLong(hexString: String): Long {
            return if (hexString.startsWith("0x") || hexString.startsWith("0X")) {
                val sub = hexString.substring(2)
                java.lang.Long.parseUnsignedLong(sub, 16)
            } else {
                java.lang.Long.parseUnsignedLong(hexString, 10)
            }
        }
    }
}
