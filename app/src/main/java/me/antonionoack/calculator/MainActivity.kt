package me.antonionoack.calculator

import android.annotation.SuppressLint
import android.app.AlertDialog
import android.content.SharedPreferences
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.ColorDrawable
import android.os.Bundle
import android.os.Looper
import android.view.Gravity
import android.view.ViewGroup
import android.widget.*
import android.widget.SeekBar.OnSeekBarChangeListener
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.children
import me.antonionoack.calculator.BigDec.Companion.E
import me.antonionoack.calculator.BigDec.Companion.NaN
import me.antonionoack.calculator.BigDec.Companion.ONE
import me.antonionoack.calculator.BigDec.Companion.PI
import me.antonionoack.calculator.BigDec.Companion.div100
import me.antonionoack.calculator.BigDec.Companion.negOne2
import me.antonionoack.calculator.BigDec.Companion.negativeInfinity
import me.antonionoack.calculator.BigDec.Companion.positiveInfinity
import java.io.ByteArrayOutputStream
import java.io.PrintStream
import java.math.BigDecimal
import java.math.MathContext
import java.math.RoundingMode
import java.util.*
import kotlin.concurrent.thread
import kotlin.math.max

// todo long press hints about what each function does

class MainActivity : AppCompatActivity() {

    private lateinit var resultView: TextView
    private lateinit var preferences: SharedPreferences

    private var text = ""
    private val chain = ArrayList<Any>()
    private var mathContext = MathContext(64, RoundingMode.HALF_UP)
    private var lastOp: ((BigDec) -> Any)? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        init(findViewById(R.id.all))
        resultView = findViewById(R.id.console)
        resultView.typeface = Typeface.MONOSPACE
        preferences = getPreferences(MODE_PRIVATE)
        loadState()
        showNumber()
    }

    private val separator1 = 1.toChar().toString()
    private val separator2 = 1.toChar().toString()

    private fun loadState() {
        text = preferences.getString("text", "") ?: ""
        chain.clear()
        var chain0 = preferences.getString("chain", "") ?: ""
        if (chain0.length > 10000) chain0 = "Too long"
        chain.addAll(chain0
            .split(separator1)
            .filter { it.isNotEmpty() }
            .map { parseSymbol(it) })
        val precision = preferences.getInt("precision", -1)
        if (precision > 0) {
            mathContext = MathContext(precision, mathContext.roundingMode)
        }
    }

    private fun storeState() {
        var chain0 = chain.joinToString(separator1)
        if (chain0.length > 10000) chain0 = "Too long"
        preferences.edit()
            .putString("text", text)
            .putString("chain", chain0)
            .putInt("precision", mathContext.precision)
            .apply()
    }

    private fun parseSymbol(it: String): Any {
        return when (it) {
            "pi" -> BigDec(PI)
            "e" -> BigDec(E)
            NaN.state -> NaN
            positiveInfinity.state -> positiveInfinity
            negativeInfinity.state -> negativeInfinity
            else -> try {
                BigDec(it)
            } catch (e: NumberFormatException) {
                it
            }
        }
    }

    private fun handle(e: Throwable): String {
        e.printStackTrace()
        val str = ByteArrayOutputStream(512)
        e.printStackTrace(PrintStream(str))
        val title = "Error: ${e.message}"
        msg(title, str.toString())
        return title
    }

    private fun setEnabled(enabled: Boolean) {
        runOnUiThread {
            setEnabled(enabled, findViewById(R.id.all))
        }
    }

    private fun setEnabled(enabled: Boolean, viewGroup: ViewGroup) {
        for (child in viewGroup.children) {
            when (child) {
                is ViewGroup -> setEnabled(enabled, child)
                is TextView -> child.isEnabled = enabled
            }
        }
    }

    private fun pushText() {
        if (text.isNotEmpty()) {
            synchronized(chain) {
                if (text == "-") {
                    chain.add("(")
                    chain.add(negOne2)
                    chain.add("*")
                } else chain.add(parse())
            }
            text = ""
        }
    }

    private fun op(symbol: String, replace: Boolean = true) {
        synchronized(chain) {
            pushText()
            if (replace && chain.isEmpty()) chain.add(BigDec.ZERO)
            // replace last symbol
            if (replace && chain.last() is String && chain.last() != "(" && chain.last() != ")" && chain.last() != "rand")
                chain[chain.lastIndex] = symbol
            else chain.add(symbol)
        }
    }

    private fun parse(): BigDec {
        if (text.endsWith(".") || text.endsWith("e") || text.endsWith("e-")) text += "0"
        return try {
            BigDec(text)
        } catch (e: NumberFormatException) {
            val v = text.toDouble()
            when {
                v.isNaN() -> NaN
                v == Double.POSITIVE_INFINITY -> positiveInfinity
                v == Double.NEGATIVE_INFINITY -> negativeInfinity
                else -> BigDec(BigDecimal(v))
            }
        }
    }

    private fun showNumber() {
        // if not on UI thread, restart :)
        if (Looper.getMainLooper().thread != Thread.currentThread()) {
            runOnUiThread { showNumber() }
            return
        }
        val newText = synchronized(chain) {
            if (text.isEmpty()) {
                if (chain.isEmpty()) "..."
                else chain.joinToString(" ") { formatNumber(it) }
            } else if (chain.isEmpty()) formatNumber(text)
            else chain.joinToString(" ") { formatNumber(it) } + " " + formatNumber(text)
        }
        if (resultView.text != newText) {
            storeState()
            resultView.text = newText
        }
    }

    private fun formatNumber(b: Any): String {
        return if (b !is BigDec) b.toString()
        else if (b.state != null || b.dec == E || b.dec == PI) b.toString()
        else return formatNumber(b.toString())
    }

    private fun formatNumber(s: String): String {
        var i0 = s.indexOf('.')
        var i1 = s.indexOf('E', 0, true)
        if (i1 < 0) i1 = s.length
        if (i0 < 0) i0 = i1
        val i2 = s.length
        val i3 = (i0 + 2) % 3
        val x = StringBuilder(s.length * 4 / 3 + 1)
        for (i in 0 until i0) { // before dot
            x.append(s[i])
            if ((i > 0 || s[0] != '-') &&
                i % 3 == i3 && i + 1 < i0
            ) x.append(',')
        }
        if (i0 < s.length) { // after dot
            if (i0 + 1 < i1) {
                x.append(s[i0++]) // dot
                val i4 = (i0 + 2) % 3
                for (i in i0 until i1) {
                    x.append(s[i])
                    if (i % 3 == i4 && i + 1 < i1) x.append(',')
                }
            }
            if (i1 + 1 < s.length) { // after exponent
                x.append('e');i1++
                if (s[i1] == '+' || s[i1] == '-') x.append(s[i1++])
                else x.append('+')
                val i5 = (i2 + 2) % 3
                for (i in i1 until i2) {
                    x.append(s[i])
                    if (i % 3 == i5 && i + 1 < i2) x.append(',')
                }
            } else x.append(s, i1, i2)
        } else x.append(s, i0, i2)
        return x.toString()
    }

    @SuppressLint("SetTextI18n")
    private fun init(v: ViewGroup) {
        for (child in v.children) {
            when (child) {
                is ViewGroup -> init(child)
                is TextView -> {
                    child.setOnClickListener {
                        val cmd = child.text.toString()
                        if (cmd.length == 1 && cmd[0] in '0'..'9') {
                            if (chain.lastOrNull() is BigDec) chain.clear()
                            text += cmd
                        } else when (cmd) {
                            "*", "/", "%", "+" -> op(cmd)
                            "x^y" -> op("^")
                            "^1/", "y√x" -> op("^1/")
                            "-" -> {
                                if (text.endsWith("e") || (text.isEmpty() &&
                                            (chain.isEmpty() ||
                                                    chain.last() in listOf("*", "/", "^", "(")))
                                ) {
                                    if (text.endsWith("-")) text = text.substring(0, text.lastIndex)
                                    else text += "-"
                                } else op("-")
                            }
                            ".", "," -> synchronized(chain) {
                                if (chain.lastOrNull() is BigDec) chain.clear()
                                if ('.' !in text) text += "."
                            }
                            "*10^" -> if ('e' !in text) {
                                synchronized(chain) {
                                    if (chain.lastOrNull() is BigDec) chain.clear()
                                    if (text.endsWith(".")) text += "0"
                                    if (text.isEmpty()) text = "1"
                                    text += "e"
                                }
                            }
                            "=" -> thread {
                                setEnabled(false)
                                if (chain.size == 1 && text.isEmpty() && chain[0] != "rand") {
                                    val lc = chain[0]
                                    val lastOp = lastOp
                                    if (lastOp != null && lc is BigDec) {
                                        val r = try {
                                            lastOp(lc)
                                        } catch (e: Throwable) {
                                            handle(e)
                                        }
                                        synchronized(chain) {
                                            chain[0] = r
                                        }
                                        showNumber()
                                    }
                                } else {
                                    eval()
                                }
                                setEnabled(true)
                            }
                            "<-" -> if (text.isNotEmpty()) {
                                text = text.substring(0, text.length - 1)
                            } else synchronized(chain) {
                                if (chain.isNotEmpty())
                                    chain.removeAt(chain.lastIndex)
                            }
                            "(" -> op("(", false)
                            ")" -> synchronized(chain) {    // only allow ), if there is enough (
                                if (chain.count { it == "(" } > chain.count { it == ")" }) {
                                    op(")", false)
                                }
                            }
                            "e" -> synchronized(chain) {
                                text = ""
                                chain.add(E)
                            }
                            "pi" -> synchronized(chain) {
                                text = ""
                                chain.add(PI)
                            }
                            "sqrt",
                            "sin", "asin", "sinh", "asinh",
                            "cos", "acos", "cosh", "acosh",
                            "tan", "atan", "tanh", "atanh",
                            "3n+1", "prime",
                            "log10", "exp", "ln", "n!", "1/x",
                            "rand" -> {
                                op(cmd, false)
                            }
                            "load" -> {
                                var dialog: AlertDialog? = null
                                val alert = AlertDialog.Builder(this)
                                val stored = preferences.getString("storage", "") ?: ""
                                val values = stored
                                    .split(separator2)
                                    .filter { it.isNotEmpty() }
                                    .map { parseSymbol(it) }
                                    .toMutableList()
                                val scrollView = ScrollView(this)
                                layoutInflater.inflate(R.layout.dialog_list, scrollView)
                                val bar = scrollView.children.last() as LinearLayout
                                bar.orientation = LinearLayout.VERTICAL
                                if (values.isEmpty()) {
                                    toast("Store something first", false)
                                } else {
                                    for (value in values) {
                                        layoutInflater.inflate(R.layout.button, bar)
                                        val text = bar.children.last() as TextView
                                        text.text = value.toString()
                                        text.background
                                        text.setPadding(5, 5, 5, 5)
                                        text.gravity = Gravity.CENTER_HORIZONTAL
                                        text.setOnLongClickListener {
                                            values.remove(value)
                                            bar.removeView(text)
                                            val newValues = values.joinToString(separator2)
                                            preferences.edit()
                                                .putString("storage", newValues)
                                                .apply()
                                            true
                                        }
                                        text.setOnClickListener {
                                            pushText()
                                            if (value is BigDecimal) {
                                                this.text = value.toString()
                                            } else synchronized(chain) {
                                                chain.add(value)
                                            }
                                            showNumber()
                                            dialog?.dismiss()
                                        }
                                    }
                                }
                                alert.setView(scrollView)
                                dialog = alert.show()
                                dialog.window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
                            }
                            "store" -> {
                                eval()
                                if (chain.isNotEmpty()) {
                                    val stored = preferences.getString("storage", "") ?: ""
                                    val values = stored
                                        .split(separator2)
                                        .filter { it.isNotEmpty() }
                                        .map { parseSymbol(it) }
                                        .toMutableList()
                                    val value = chain.first()
                                    values.add(value)
                                    preferences.edit()
                                        .putString("storage", values.joinToString(separator2))
                                        .apply()
                                    toast("Stored value", false)
                                } else toast("Cannot store nothing", false)
                            }
                            "prec" -> {

                                var dialog: AlertDialog? = null

                                val alert = AlertDialog.Builder(this)

                                val list = layoutInflater.inflate(
                                    R.layout.dialog_precision,
                                    null
                                ) as LinearLayout

                                val bar = list.findViewById<SeekBar>(R.id.seekBar)
                                val minValue = 1
                                val maxValue = 500

                                val tv = list.findViewById<TextView>(R.id.precisionText)
                                tv.text = if (mathContext.precision == 1) "1 digit"
                                else "${mathContext.precision} digits"

                                bar.max = maxValue - minValue
                                bar.progress = mathContext.precision - minValue
                                bar.setOnSeekBarChangeListener(object : OnSeekBarChangeListener {

                                    override fun onProgressChanged(
                                        seekBar: SeekBar?,
                                        progress: Int,
                                        fromUser: Boolean
                                    ) {
                                        val precision = minValue + bar.progress
                                        tv.text = if (precision == 1) "1 digit"
                                        else "$precision digits"
                                    }

                                    override fun onStartTrackingTouch(seekBar: SeekBar?) {}
                                    override fun onStopTrackingTouch(seekBar: SeekBar?) {}

                                })

                                alert.setView(list)

                                list.findViewById<TextView>(R.id.cancel).setOnClickListener {
                                    dialog?.dismiss()
                                }
                                list.findViewById<TextView>(R.id.ok).setOnClickListener {
                                    val precision = minValue + bar.progress
                                    mathContext =
                                        MathContext(precision, mathContext.roundingMode)
                                    storeState()
                                    dialog?.dismiss()
                                }

                                dialog = alert.show()
                                dialog.window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))

                            }
                            else -> {
                                println("todo: implement $cmd")
                            }
                        }
                        showNumber()
                    }
                }
            }
        }
    }

    private fun applyFunc(name: String, value: BigDec): BigDec {
        return when (name) {
            "sqrt" -> value.sqrt(mathContext)
            "sin" -> value.sin(mathContext)
            "cos" -> value.cos(mathContext)
            "tan" -> value.tan(mathContext)
            "sinh" -> value.sinh(mathContext)
            "cosh" -> value.cosh(mathContext)
            "tanh" -> value.tanh(mathContext)
            "asin" -> value.asin(mathContext)
            "acos" -> value.acos(mathContext)
            "atan" -> value.atan(mathContext)
            "asinh" -> value.asinh(mathContext)
            "acosh" -> value.acosh(mathContext)
            "atanh" -> value.atanh(mathContext)
            "prime" -> value.isPrime(this, mathContext)
            "ln" -> value.ln(mathContext)
            "log10" -> value.log10(mathContext)
            "exp" -> value.exp(mathContext)
            "3n+1" -> value.powerOf2For3nPlus1(this)
            "n!" -> value.faculty(this, mathContext)
            "1/x" -> ONE.divide(value, mathContext)
            else -> NaN
        }
    }

    private fun eval() {

        synchronized(chain) {

            pushText()

            var open = chain.count { it == "(" }
            var close = chain.count { it == ")" }

            while (open < close) {
                open++
                chain.add(0, "(")
            }

            while (close < open) {
                close++
                chain.add(")")
            }
        }

        // solve from inner to outer
        // find first index of )
        do {
            val lastSize = chain.size
            val idx = chain.indexOf(")")
            if (idx > 0) {
                val idx1 = synchronized(chain) {
                    chain.subList(0, idx).lastIndexOf("(")
                }
                if (idx1 >= 0) {
                    if (idx - idx1 == 2) {
                        synchronized(chain) {
                            // remove brackets
                            chain.removeAt(idx)
                            chain.removeAt(idx1)
                        }
                    } else {
                        // solve within brackets :)
                        solve(idx1 + 1, idx)
                    }
                } // else malformed brackets
            } else {
                // no brackets found -> solve all :)
                solve(0, chain.size)
            }
        } while (lastSize > chain.size)

        text = ""
        if (chain.size > 1) {
            println(chain)
        }
        showNumber()

    }

    private val digits = "0123456789".map { it.toString() }
    private fun solve(i0: Int, i1: Int) {

        // process rand
        for (i in i0 until i1) {
            if (chain[i] == "rand") {
                val rand = Random(System.nanoTime())
                val str = (0 until max(1, mathContext.precision))
                    .joinToString("", "0.") { digits[rand.nextInt(10)] }
                chain[i] = BigDec(str)
            }
        }

        // process functions
        var i = i0
        while (i + 1 < i1) {
            val p = chain[i]
            val a = chain[i + 1]
            if (isFunc(p) && a is BigDec) {
                val result = applyFunc(p as String, a)
                lastOp = { applyFunc(p, it) }
                synchronized(chain) {
                    chain[i] = result
                    chain.removeAt(i + 1)
                }
                return // changed something
            } else i++
        }

        if (i1 - i0 == 2) {
            val p = chain[i0 + 1]
            val a = chain[i0]
            if (isFunc(p) && a is BigDec) {
                p as String
                val result = try {
                    applyFunc(p, a)
                } catch (e: ArithmeticException) {
                    handle(e)
                }
                lastOp = { applyFunc(p, it) }
                synchronized(chain) {
                    chain[i0] = result
                    chain.removeAt(i0 + 1)
                }
                return // changed something
            }
        }

        if (processBinary2(i0, i1) { a, b, o ->
                if (a == "(" && b == ")" && o is BigDec) o
                else null
            }) return

        if (processBinary(i0, i1) { a, b, o ->
                when (o) {
                    "^" -> a.pow(b, mathContext)
                    "^1/" -> a.pow(ONE.divide(b, mathContext), mathContext)
                    else -> null
                }
            }) return

        if (processBinary(i0, i1) { a, b, o ->
                when (o) {
                    "*" -> a.multiply(b, mathContext)
                    "/" -> a.divide(b, mathContext)
                    "%" -> a.remainder(b, mathContext)
                    else -> null
                }
            }) return

        // search for +/- num % (non-number)
        for (j in i0 until i1 - 2) {
            val sign = chain[j]
            val number = chain[j + 1]
            val percent = chain[j + 2]
            val notANumber = chain.getOrNull(j + 3)
            if ((sign == "+" || sign == "-") &&
                number is BigDec && percent == "%" && notANumber !is BigDec
            ) {
                chain[j] = "*"
                val value = number.multiply(div100, mathContext)
                chain[j + 1] =
                    if (sign == "+") ONE.add(value, mathContext)
                    else ONE.subtract(value, mathContext)
                chain.removeAt(j + 2)
                return
            }
        }

        if (processBinary(i0, i1) { a, b, o ->
                when (o) {
                    "+" -> a.add(b, mathContext)
                    "-" -> a.subtract(b, mathContext)
                    else -> null
                }
            }) return
    }

    private fun isFunc(name: Any?): Boolean {
        return name is String && name.length > 1 && name != "^1/"
    }

    private fun processBinary(
        i0: Int, i1: Int,
        process: (a: BigDec, b: BigDec, o: String) -> Any?
    ): Boolean {
        var i = i0
        var i1x = i1
        while (i + 2 < i1x) {
            val p = chain.getOrNull(i - 1)
            if (!isFunc(p)) {
                val a = chain[i]
                val b = chain[i + 2]
                val o = chain[i + 1]
                if (a is BigDec && b is BigDec && o is String) {
                    val r = try {
                        process(a, b, o)
                    } catch (e: Throwable) {
                        handle(e)
                    }
                    if (r != null) {
                        lastOp = { process(it, b, o) ?: "Invalid Operation" }
                        synchronized(chain) {
                            chain.removeAt(i + 2)
                            chain.removeAt(i + 1)
                            chain[i] = r
                        }
                        i1x -= 2
                    } else i++
                } else i++
            } else i++
        }
        return i1 > i1x
    }

    private fun processBinary2(
        i0: Int,
        i1: Int,
        process: (a: Any, b: Any, o: Any) -> BigDec?
    ): Boolean {
        var i = i0
        var i1x = i1
        while (i + 2 < i1x) {
            val p = chain.getOrNull(i - 1)
            if (!isFunc(p)) {
                val a = chain[i]
                val b = chain[i + 2]
                val o = chain[i + 1]
                val r = try {
                    process(a, b, o)
                } catch (e: Throwable) {
                    handle(e)
                }
                if (r != null) {
                    synchronized(chain) {
                        chain.removeAt(i + 2)
                        chain.removeAt(i + 1)
                        chain[i] = r
                    }
                    i1x -= 2
                } else i++
            } else i++
        }
        return i1 > i1x
    }

    fun ask(
        question: String,
        description: String,
        yes: () -> Unit,
        no: () -> Unit,
        cancel: () -> Unit
    ): TextView {
        val tv = TextView(this)
        tv.gravity = Gravity.CENTER_HORIZONTAL
        runOnUiThread {
            // todo nicer colors
            val alert = AlertDialog.Builder(this)
            alert.setTitle(question)
            alert.setMessage(description)
            alert.setView(tv)
            alert.setPositiveButton("Yes") { _, _ -> yes() }
            alert.setNegativeButton("No") { _, _ -> no() }
            alert.setOnCancelListener { cancel() }
            /*val dialog =*/ alert.show()
            // dialog.window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
        }
        return tv
    }

    private fun msg(
        question: String,
        description: String
    ) {
        runOnUiThread {
            // todo nicer colors
            val alert = AlertDialog.Builder(this)
            alert.setTitle(question)
            alert.setMessage(description)
            /*val dialog =*/ alert.show()
            // dialog.window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
        }
    }

    private fun toast(message: String, long: Boolean) {
        runOnUiThread {
            val length = if (long) Toast.LENGTH_LONG else Toast.LENGTH_SHORT
            Toast.makeText(this, message, length)
                .show()
        }
    }

}