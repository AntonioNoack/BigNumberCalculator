package me.antonionoack.calculator

import android.annotation.SuppressLint
import android.widget.TextView
import androidx.core.math.MathUtils.clamp
import java.math.BigDecimal
import java.math.BigInteger
import java.math.MathContext
import java.util.*
import kotlin.math.*

@Suppress("MemberVisibilityCanBePrivate")
class BigDec(dec: BigDecimal, val state: String? = null) {

    val dec = dec.stripTrailingZeros() // ^^

    constructor(value: String) : this(BigDecimal(value))

    companion object {
        val positiveInfinity = BigDec(BigDecimal.ONE, "+Infinity")
        val negativeInfinity = BigDec(BigDecimal("-1"), "-Infinity")
        val NaN = BigDec(BigDecimal.ZERO, "NaN")
        val ZERO = BigDec(BigDecimal.ZERO)
        val ONE = BigDec(BigDecimal.ONE)
        val div100 = BigDec(BigDecimal(BigInteger.ONE, 2))
        val negOne: BigDecimal = BigDecimal.ONE.negate()
        val negOne2 = BigDec(negOne)
        val threeHalfs = BigDecimal("1.5")
        val half = BigDecimal("0.5")
        val precise = MathContext(500)
        val PI = BigDecimal( // 500 digits of PI
            "3.141592653589793238462643383279502884197169399375105820974944592307816406286208998" +
                    "6280348253421170679821480865132823066470938446095505822317253594081284811174502" +
                    "8410270193852110555964462294895493038196442881097566593344612847564823378678316" +
                    "5271201909145648566923460348610454326648213393607260249141273724587006606315588" +
                    "1748815209209628292540917153643678925903600113305305488204665213841469519415116" +
                    "0943305727036575959195309218611738193261179310511854807446237996274956735188575" +
                    "27248912279381830119491"
        )
        val halfPI: BigDecimal = PI.multiply(BigDecimal("0.5"), precise)
        val negHalfPI: BigDecimal = halfPI.negate()
        val TAU: BigDecimal = PI.multiply(BigDecimal.valueOf(2), precise)
        val E = BigDecimal( // 500 digits of e
            "2.718281828459045235360287471352662497757247093699959574966967627724076630353547594" +
                    "5713821785251664274274663919320030599218174135966290435729003342952605956307381" +
                    "3232862794349076323382988075319525101901157383418793070215408914993488416750924" +
                    "4761460668082264800168477411853742345442437107539077744992069551702761838606261" +
                    "3313845830007520449338265602976067371132007093287091274437470472306969772093101" +
                    "4169283681902551510865746377211125238978442505695369677078544996996794686445490" +
                    "59879316368892300987931"
        )
        val sqrt10 = BigDecimal(sqrt(10.0))
        val v180: BigDecimal = BigDecimal(180)
        val deg2Rad: BigDecimal = PI.divide(v180, precise)
        val rad2Deg: BigDecimal = v180.divide(PI, precise)
        val threeI: BigInteger = BigInteger.valueOf(3)
        val two: BigInteger = BigInteger.valueOf(2)

        // precomputed, because it is expensive (1000 iterations of exp with long numbers)
        // BigDec(10.0).ln(precise).dec
        val ln10 = BigDecimal(
            "2.302585092994045684017991454684364207601101488628772976033327900967572609677352480" +
                    "2359972050895982983419677840422862486334095254650828067566662873690987816894829" +
                    "0720832555468084379989482623319852839350530896537773262884616336622228769821988" +
                    "6746543667474404243274365155048934314939391479619404400222105101714174800368808" +
                    "4012647080685567743216228355220114804663715659121373450747856947683463616792101" +
                    "8064450706480002775026849167465505868569356734206705811364292245544057589257242" +
                    "08241314695689016758940" // the last digit was a 1 instead of a 0, corrected using
            // http://www.plouffe.fr/simon/constants/log10.txt
        )
        val ln10Inv: BigDecimal = BigDecimal.ONE.divide(ln10, precise)
        val maxExpInput = BigDecimal(999999999.4999) // more is not supported by BigDecimal
        val minExpInput: BigDecimal = maxExpInput.negate()
        val log2_10 = log2(10.0)
        val log2_10Inv = 1.0 / log2_10
        val smallValue = BigDecimal(1e-18)
        val largeValue = BigDecimal(1e18)
    }

    override fun toString(): String {
        return state ?: when (dec) {
            PI -> "pi"
            E -> "e"
            else -> {
                val abs = dec.abs()
                if (abs < smallValue || abs > largeValue) dec.toString()
                else dec.toPlainString()
            }
        }
    }

    private fun isNaN() = this == NaN
    private fun sign() = dec.signum()
    private fun isZero() = state == null && dec == BigDecimal.ZERO

    fun multiply(other: BigDec, ctx: MathContext): BigDec {
        if (isNaN() || other.isNaN()) return NaN
        if (state != null) {
            if (other.isZero()) return NaN // 0 * Infinity
            return if (this == positiveInfinity) if (other.sign() >= 0) this else negativeInfinity
            else if (other.sign() > 0) this else positiveInfinity
        } else if (other.state != null) return other.multiply(this, ctx)
        return try {
            BigDec(dec.multiply(other.dec, ctx))
        } catch (e: ArithmeticException) {
            return when (e.message) {
                "Overflow" -> positiveInfinity
                "Underflow" -> negativeInfinity
                else -> NaN
            }
        }
    }

    fun divide(other: BigDec, ctx: MathContext): BigDec {
        if (isNaN() || other.isNaN()) return NaN
        if (isZero() && other.isZero()) return NaN
        if (state != null) {
            if (other.isZero() || other.state != null) return NaN // Infinity / 0 or Infinity / Infinity
            return if (this == positiveInfinity) if (other.sign() >= 0) this else negativeInfinity
            else if (other.sign() > 0) this else positiveInfinity
        } else if (other.state != null) {
            // this / Infinity
            return ZERO
        }
        return try {
            BigDec(dec.divide(other.dec, ctx))
        } catch (e: ArithmeticException) {
            return when (e.message) {
                "Overflow" -> positiveInfinity
                "Underflow" -> negativeInfinity
                "Division by zero" -> {
                    if (other.dec.signum() >= 0) positiveInfinity
                    else negativeInfinity
                }
                else -> NaN
            }
        }
    }

    fun remainder(other: BigDec, ctx: MathContext): BigDec {
        if (isNaN() || other.isNaN()) return NaN
        if (isZero() && other.isZero()) return NaN
        if (state != null || other.state != null) return NaN
        return try {
            BigDec(dec.remainder(other.dec, ctx))
        } catch (e: ArithmeticException) {
            return when (e.message) {
                "Overflow" -> positiveInfinity
                "Underflow" -> negativeInfinity
                "Division by zero" -> {
                    if (other.dec.signum() >= 0) positiveInfinity
                    else negativeInfinity
                }
                else -> NaN
            }
        }
    }

    fun add(other: BigDec, ctx: MathContext): BigDec {
        if (isNaN() || other.isNaN()) return NaN
        if (this == positiveInfinity && other == negativeInfinity) return NaN
        if (this == negativeInfinity && other == positiveInfinity) return NaN
        if (this == positiveInfinity) return this
        if (this == negativeInfinity) return this
        return try {
            BigDec(dec.add2(other.dec, ctx))
        } catch (e: ArithmeticException) {
            return when (e.message) {
                "Overflow" -> positiveInfinity
                "Underflow" -> negativeInfinity
                else -> NaN
            }
        }
    }

    fun subtract(other: BigDec, ctx: MathContext): BigDec {
        if (isNaN() || other.isNaN()) return NaN
        if (this == other && (this.state != null)) return NaN // infinity with same sign
        if (this == positiveInfinity) return this
        if (this == negativeInfinity) return this
        return try {
            BigDec(dec.subtract(other.dec, ctx))
        } catch (e: ArithmeticException) {
            return when (e.message) {
                "Overflow" -> positiveInfinity
                "Underflow" -> negativeInfinity
                else -> NaN
            }
        }
    }

    fun sqrt(ctx: MathContext): BigDec {
        if (state != null) return this
        if (sign() < 0) return NaN // sqrt of negative numbers not supported
        return try {
            val half = dec.multiply(half, ctx)
            val rawValue = dec.unscaledValue()
            // estimate sqrt
            val length = rawValue.bitLength()
            val shift = max(length - 60, 0).and(1.inv()) // without 1 bit, so length-shift is even
            val approximation = rawValue.shiftRight(shift)
            val scale = dec.scale()
            // rawValue * (10^-scale)
            // approximation * (2^shift) * (10^-scale)
            // sqrt(approx) * 2^(shift>>1) * (10^-(scale/2))
            val sqrtApprox1 = BigDecimal(sqrt(approximation.toDouble()))
            var guess = sqrtApprox1.scaleByPowerOfTen(-ceil(scale / 2f).toInt())
            val halfShift = shift ushr 1
            if (halfShift > 0)
                guess = guess.multiply(BigDecimal(BigInteger.ONE.shiftLeft(halfShift)), ctx)
            if (scale.and(1) != 0)
                guess = guess.multiply(sqrt10, ctx)
            // println("guess comparison ${guess.toDouble() / sqrt(dec.toDouble())}")
            guess = BigDecimal.ONE.divide(guess, ctx)
            for (i in 0 until (log2(dec.precision().toFloat()) * log2(10f)).toInt() + 4) {
                val t0 = guess.multiply(guess, ctx)
                val t1 = half.multiply(t0, ctx)
                val t2 = threeHalfs.subtract(t1, ctx)
                val newGuess = guess.multiply(t2, ctx)
                if (newGuess == guess) {
                    println("sqrt converged after $i iterations")
                    break
                } // converged :)
                guess = newGuess
            }
            BigDec(BigDecimal.ONE.divide(guess, ctx))
        } catch (e: ArithmeticException) {
            return NaN
        }
    }

    fun ln(ctx: MathContext): BigDec {
        if (sign() == 0) return negativeInfinity
        if (sign() < 1 || isNaN()) return NaN
        if (this == positiveInfinity) return this
        return BigDec(ln(dec, ctx))
    }

    // wrong
    /*private fun ln(dec0: BigDecimal, ctx: MathContext): BigDecimal {
        if (dec0 > BigDecimal.ONE) return ln(BigDecimal.ONE.divide(dec, ctx), ctx).negate()
        // first estimate the result...
        // ln(int * 2^shift * 10^-scale) = ln(int) + ln(2)*shift - ln(10)*scale
        // this accelerates convergence, because we avoid random fluctuations of exp()
        val dec = dec0.subtract(BigDecimal.ONE, ctx)
        var sum = dec
        var y = dec
        var i = 2
        var s = -1
        while (true) {
            val newSum = sum.add2(y.divide(BigDecimal(s * i), ctx), ctx)
            if (sum == newSum) {
                println("ln converged after ${i - 1} iterations")
                break
            } else println("$sum -> $newSum")
            sum = newSum
            y = y.multiply(dec, ctx)
            s = -s
            i++
        }
        println("$sum vs ${ln(toDouble())}")
        return sum
    }*/

    private fun ln(dec: BigDecimal, ctx: MathContext) = lnOld(dec, ctx)
    private fun lnOld(dec: BigDecimal, ctx: MathContext): BigDecimal {
        // the convergence is better for larger values, so use it :)
        if (dec < BigDecimal.ONE) return lnOld(BigDecimal.ONE.divide(dec, ctx), ctx).negate()
        // first estimate the result...
        // ln(int * 2^shift * 10^-scale) = ln(int) + ln(2)*shift - ln(10)*scale
        // this accelerates convergence, because we avoid random fluctuations of exp()
        val ctx2 = MathContext(ctx.precision + 1, ctx.roundingMode)
        val scale = dec.scale()
        val rawValue = dec.unscaledValue()
        val shift = max(rawValue.bitLength() - 60, 0)
        val int = rawValue.shiftRight(shift).toDouble()
        // what is the highest possible result? ~ +/- int max -> estimating using doubles is fine :)
        // is literally the same as the result using ln(toDouble()) :3
        var guess = BigDecimal(ln(int) + ln(2.0) * shift - ln(10.0) * scale)
        // improve the result using newton iteration (https://math.stackexchange.com/questions/1382070/iterative-calculation-of-log-x)
        // it converges, so I'd guess it's correct :)
        var i = 0
        while (i < 500) {
            val added = dec.multiply(exp(guess.negate(), ctx2), ctx2).subtract(BigDecimal.ONE, ctx2)
            val newGuess = guess.add2(added, ctx2)
            if (guess == newGuess) {
                println("ln converged after $i iterations")
                break
            } else i++
            guess = newGuess
        }
        return guess
    }

    fun log10(ctx: MathContext): BigDec {
        if (sign() < 1 || isNaN()) return NaN
        if (this == positiveInfinity) return this
        if (dec.unscaledValue() == BigInteger.ONE) { // fast path :D
            return BigDec(BigDecimal(-dec.scale()))
        }
        return BigDec(ln(dec, ctx).multiply(ln10Inv, ctx))
    }

    fun sin(ctx: MathContext): BigDec {
        if (state != null) return NaN
        return BigDec(sin(dec.multiply(deg2Rad, ctx), ctx))
    }

    private fun sin(dec0: BigDecimal, ctx: MathContext): BigDecimal {
        // should be much more accurate than fromDouble(sin(toDouble()))
        var dec = dec0
        if (dec.signum() < 0) return sin(dec.negate(), ctx).negate()
        if (dec > TAU) dec = dec.remainder(TAU, ctx)
        if (dec > PI) return sin(TAU.subtract(dec, ctx), ctx).negate()
        // 0 <= dec <= pi
        if (dec > halfPI) dec = PI.subtract(dec, ctx)
        // 0 <= dec <= pi/2
        var sum = dec
        var signedFactorial = BigDecimal(-6L) // could become very large and cause issues...
        // only after a billion iterations (n²), so it doesn't matter :)
        var i = 4L
        var y = dec
        val x2 = dec.multiply(dec, ctx)
        while (true) {
            y = y.multiply(x2, ctx)
            val nextSum = sum.add2(y.divide(signedFactorial, ctx), ctx)
            if (sum == nextSum) break // converged :)
            sum = nextSum
            signedFactorial = signedFactorial.multiply(BigDecimal.valueOf(-i * (i + 1)))
            i += 2L
        }
        return sum
    }

    fun exp(ctx: MathContext): BigDec {
        if (isNaN()) return NaN
        if (this == positiveInfinity) return this
        if (this == negativeInfinity) return ZERO
        if (dec >= maxExpInput) return positiveInfinity
        if (dec <= minExpInput) return ZERO
        return BigDec(exp(dec, ctx)) // this is accurate :3
    }

    private fun exp(dec0: BigDecimal, ctx: MathContext): BigDecimal {
        if (dec0 <= minExpInput) return BigDecimal.ZERO
        if (dec0.signum() < 0) return BigDecimal.ONE.divide(exp(dec0.negate(), ctx), ctx)
        // find the remainder and the fractional part :)
        val half1 = dec0.add2(half, ctx)
        val (int, rem0) = half1.divideAndRemainder(BigDecimal.ONE)
        val int2 = int.intValueExact()
        // ctx.precision must be at least log10(int.intValueExact)
        val ctx2 = if (int2.toDouble() > 10.0.pow(ctx.precision)) {
            MathContext(log10(int2.toDouble()).toInt() + 1, ctx.roundingMode)
        } else ctx
        val intPow = E.pow(int2, ctx2)
        val rem = rem0.subtract(half, ctx)
        // process remainder using polynomial series :)
        // rem is centered around 0.0, so we get much faster convergence :3
        var sum = rem0.add2(half, ctx) // starting at 1+x = 1+rem = rem0+half
        var y = rem
        var factorial = BigDecimal.valueOf(2)
        var i = 3L
        while (true) {
            y = y.multiply(rem, ctx) // starting at x²/2!
            val newSum = sum.add2(y.divide(factorial, ctx), ctx)
            if (sum == newSum) { // converged :)
                println("exp converged after ${i - 3} iterations")
                break
            }
            factorial = factorial.multiply(BigDecimal.valueOf(i), ctx) // starting at i=3 for x³/i³
            sum = newSum
            i++
        }
        return sum.multiply(intPow, ctx)
    }

    fun cos(ctx: MathContext): BigDec {
        if (state != null) return NaN
        return BigDec(sin(dec.multiply(deg2Rad, ctx).add2(halfPI, ctx), ctx))
    }

    fun tan(ctx: MathContext): BigDec {
        if (state != null) return NaN
        val dec = dec.multiply(deg2Rad, ctx)
        return BigDec(tan(dec, ctx))
    }

    private fun tan(dec: BigDecimal, ctx: MathContext): BigDecimal {
        val sine = sin(dec, ctx)
        val cosine = sin(dec.add2(halfPI, ctx), ctx)
        return sine.divide(cosine, ctx)
    }

    fun sinh(ctx: MathContext): BigDec {
        if (state != null) return NaN
        return BigDec(sinh(dec, ctx))
    }

    private fun sinh(dec: BigDecimal, ctx: MathContext): BigDecimal {
        return exp(dec, ctx).subtract(exp(dec.negate(), ctx)).multiply(half, ctx)
    }

    fun cosh(ctx: MathContext): BigDec {
        if (state != null) return NaN
        return BigDec(cosh(dec, ctx))
    }

    private fun cosh(dec: BigDecimal, ctx: MathContext): BigDecimal {
        return exp(dec, ctx).add2(exp(dec.negate(), ctx), ctx).multiply(half, ctx)
    }

    fun tanh(ctx: MathContext): BigDec {
        if (isNaN()) return NaN
        if (this == positiveInfinity) return ONE
        if (this == negativeInfinity) return negOne2
        return BigDec(tanh(dec, ctx))
    }

    private fun tanh(dec: BigDecimal, ctx: MathContext): BigDecimal {
        if (dec == BigDecimal.ZERO) return BigDecimal.ZERO
        if (dec < BigDecimal.ZERO) return tanh(dec.negate(), ctx).negate()
        // (e^x-e^-x) / (e^x+e^-x) can be expanded by e^-x to
        // (1-e^-2x) / (1+e^-2x), which gives no issues for huge values :3
        val value = exp(dec.add(dec).negate(), ctx)
        val sinhExp = BigDecimal.ONE.subtract(value, ctx)
        val coshExp = BigDecimal.ONE.add2(value, ctx)
        return sinhExp.divide(coshExp, ctx)
    }

    fun asin(ctx: MathContext): BigDec {
        if (state != null) return NaN
        if (dec < negOne || dec > BigDecimal.ONE) return NaN
        return BigDec(asin(dec, ctx).multiply(rad2Deg, ctx))
    }

    private fun asin(dec: BigDecimal, ctx: MathContext): BigDecimal {
        return newtonIteration("asin", dec, ctx, { asin(it) }, { sin(it, ctx) }, {
            val g0 = max(it - 1e-3, -1.0)
            val g1 = min(it + 1e-3, +1.0)
            Pair(g0, g1)
        })
    }

    fun acos(ctx: MathContext): BigDec {
        if (state != null) return NaN
        if (dec < negOne || dec > BigDecimal.ONE) return NaN
        return BigDec(halfPI.subtract(asin(dec, ctx), ctx).multiply(rad2Deg, ctx))
    }

    fun atan(ctx: MathContext): BigDec {
        if (this == negativeInfinity) return BigDec(negHalfPI)
        if (this == positiveInfinity) return BigDec(halfPI)
        if (isNaN()) return NaN
        return BigDec(newtonIteration("atan", dec, ctx,
            { atan(it) }, { tan(it, ctx) }, {
                val delta = max(abs(it), 1e-38) * 1e-3
                Pair(it - delta, it + delta)
            }).multiply(rad2Deg, ctx)
        )
    }

    private fun newtonIteration(
        name: String,
        dec: BigDecimal, ctx: MathContext,
        func: (x: Double) -> Double,
        invFunc: (y: BigDecimal) -> BigDecimal,
        gradientSamplePositions: (x: Double) -> Pair<Double, Double>,
    ): BigDecimal {
        // Newton like iteration :)
        val double = dec.toDouble()
        val guess = BigDecimal(func(double))
        val (g0, g1) = gradientSamplePositions(double)
        val gradient0 = (func(g1) - func(g0)) / (g1 - g0)
        val gradient = BigDecimal(gradient0)
        return newtonIteration(name, dec, ctx, invFunc, guess, gradient)
    }

    private fun newtonIteration(
        name: String,
        dec: BigDecimal, ctx: MathContext,
        invFunc: (y: BigDecimal) -> BigDecimal,
        guess0: BigDecimal,
        gradient: BigDecimal
    ): BigDecimal {
        // Newton like iteration :)
        var guess = guess0
        var i = 0
        var lastError: BigDecimal? = null
        val maxIterations = 100
        while (i++ < maxIterations) {
            // todo if same sign 3 times in a row, scale up gradient?
            val err = invFunc(guess).subtract(dec, ctx)
            val error = err.multiply(gradient, ctx)
            // println("err[$i]: $error by $guess")
            lastError = error
            val newGuess = guess.subtract(error, ctx)
            if (guess == newGuess) {
                println("$name converged after $i iterations")
                return guess
            }
            // todo for high values an tanh <-> atanh, we need to update the gradient!
            println("${newGuess.toDouble()} by ${err.toDouble()} x ${gradient.toDouble()}")
            guess = newGuess
        }
        println("$name didn't converge within $maxIterations iterations!, error: $lastError")
        // convert to degrees
        return guess
    }

    fun asinh(ctx: MathContext): BigDec {
        // all values are covered :)
        if (this == positiveInfinity) return positiveInfinity
        if (this == negativeInfinity) return negativeInfinity
        if (state != null) return NaN
        return BigDec(asinh(dec, ctx))
    }

    private fun asinh(dec: BigDecimal, ctx: MathContext): BigDecimal {
        return newtonIteration("asinh", dec, ctx,
            { asinh(it) }, { sinh(it, ctx) }, {
                val delta = max(abs(it), 1e-38) * 1e-7
                Pair(it - delta, it + delta)
            })
    }

    fun acosh(ctx: MathContext): BigDec {
        if (this == positiveInfinity) return positiveInfinity
        if (isNaN()) return NaN
        if (dec < BigDecimal.ONE) return NaN
        return BigDec(acosh(dec, ctx))
    }

    private fun acosh(dec: BigDecimal, ctx: MathContext): BigDecimal {
        return newtonIteration("acosh", dec, ctx,
            { acosh(it) }, { cosh(it, ctx) }, {
                val g0 = max(it * 0.9999, 1.0)
                val g1 = it * 1.0001
                Pair(g0, g1)
            })
    }

    fun atanh(ctx: MathContext): BigDec {
        if (state != null) return NaN
        if (dec < negOne || dec > BigDecimal.ONE) return NaN
        if (dec == negOne) return negativeInfinity
        if (dec == BigDecimal.ONE) return positiveInfinity
        return BigDec(atanh(dec, ctx))
    }

    private fun atanh(dec: BigDecimal, ctx: MathContext): BigDecimal {
        val asDouble = dec.toDouble()
        // tanh(575.45 - 575.992846) is the maximum (0.999...999 with 500 digits), then it's just one 😄
        return if (abs(asDouble) > 0.999) { // the gradient becomes to unreliable -> use an estimate for it
            val oneMinus = BigDecimal.ONE.subtract(dec.abs())
            val estimate = ln(oneMinus.multiply(half), ctx)
                .multiply(half).negate()
            val gradient = half.divide(oneMinus, ctx)
            val absATanH = newtonIteration(
                "atanh", dec, ctx,
                { tanh(it, ctx) },
                estimate, gradient
            )
            if (asDouble < 0.0) absATanH.negate() else absATanH
        } else {
            newtonIteration("atanh", dec, ctx,
                {
                    // Math.atanh(0.9999999999999999)
                    val max = 18.714973875118524
                    clamp(atanh(it), -max, max)
                }, { tanh(it, ctx) }, {
                    val max = 0.9999999999999999 // else is infinity
                    val delta = 1e-15
                    val g0 = max(it - delta, -max)
                    val g1 = min(it + delta, +max)
                    Pair(g0, g1)
                })
        }
    }

    private fun toDouble(): Double {
        return when (this) {
            positiveInfinity -> Double.POSITIVE_INFINITY
            negativeInfinity -> Double.NEGATIVE_INFINITY
            NaN -> Double.NaN
            else -> dec.toDouble()
        }
    }

    fun pow(other: BigDec, ctx: MathContext): BigDec {
        if (isNaN() || other.isNaN()) return NaN
        if (other == ZERO) return ONE
        // handle infinity
        if (other == positiveInfinity) {
            return if (this == positiveInfinity) return positiveInfinity
            else if (this == negativeInfinity) return NaN // unknown sign
            else if (dec > BigDecimal.ONE) positiveInfinity
            else if (dec == BigDecimal.ONE) ONE
            else if (dec >= BigDecimal.ZERO) ZERO
            else if (dec > negOne) ZERO
            else NaN // switching signs -> unknown sign of 1 or infinity
        } else if (other == negativeInfinity) {
            return if (this == positiveInfinity || this == negativeInfinity) return ZERO
            else if (dec > BigDecimal.ONE || dec < negOne) ZERO
            else if (dec == BigDecimal.ONE) ONE
            else if (dec >= BigDecimal.ZERO) positiveInfinity
            else NaN // switching signs -> unknown sign of  1 or infinity
        }
        if (this == positiveInfinity) {
            return if (other.dec < BigDecimal.ZERO) ZERO
            else if (other.dec == BigDecimal.ZERO) ONE
            else positiveInfinity
        } else if (this == negativeInfinity) {
            // switches signs: if even +inf, if odd -inf, NaN else
            val (int, rem) = other.dec.divideAndRemainder(BigDecimal.ONE, ctx)
            if (rem != BigDecimal.ZERO) return NaN
            val isOdd = int.toBigInteger().lowestSetBit == 0
            return if (isOdd) negativeInfinity else positiveInfinity
        }
        val expD = other.toDouble()
        // only use fast path, if value is exact
        if (BigDecimal(expD) == other.dec) {
            if (expD == 0.25) return sqrt(ctx).sqrt(ctx)
            if (expD == 0.5) return sqrt(ctx)
            val expI = expD.toInt()
            // fast path for integer exponents
            val limit = 10.0.pow(ctx.precision)
            if (expI.toDouble() == expD && abs(expI) < 999999999 && abs(expI) < limit)
                return BigDec(dec.pow(expI, ctx))
        }
        return ln(ctx).multiply(other, ctx).exp(ctx)
    }

    fun powerOf2For3nPlus1(ctx: MainActivity): BigDec {
        if (state != null) return NaN
        if (sign() < 1) return NaN
        var int = dec.toBigInteger()
        return slowLoop("3n+1", ctx, {
            int = if (int.and(BigInteger.ONE) == BigInteger.ZERO) int.shiftRight(1)
            else threeI * int + BigInteger.ONE
            if (int.bitCount() > 1) null else BigDec(BigDecimal(int))
        }, { "Remaining: ${int.bitLength()} bits (#$it)" })
    }

    @SuppressLint("SetTextI18n")
    fun slowLoop(
        title: String,
        ctx: MainActivity,
        compute: (i: Int) -> BigDec?,
        info: (i: Int) -> String
    ): BigDec {
        var asked = false
        var mayStop = false
        var lastAsked = System.nanoTime()
        var lastShown = System.nanoTime()
        var timeout = 250_000_000L
        var tv: TextView? = null
        var i = 0
        var result: BigDec?
        while (true) {
            if (mayStop) return NaN
            result = compute(i)
            if (result != null) break
            // run on ui thread?
            val time = System.nanoTime()
            if (!asked) {
                if (time - lastAsked > timeout) {
                    asked = true

                    fun stopFunc() {
                        mayStop = true
                    }

                    fun continueFunc() {
                        lastAsked = System.nanoTime()
                        timeout += timeout / 2
                        asked = false
                    }

                    tv = ctx.ask(
                        title,
                        "The calculation is taking a long time.\n" +
                                "Do you want to exit the calculation?",
                        ::stopFunc,
                        ::continueFunc,
                        ::continueFunc
                    )
                }
            } else {
                // wait at least 10ms
                if (time - lastShown > 10_000_000) {
                    lastShown = time
                    ctx.runOnUiThread {
                        tv?.text = info(i)
                    }
                }
            }
            i++
        }
        if (tv != null) {
            ctx.runOnUiThread {
                tv.text = "Done in $i iterations, $result"
            }
        }
        return result!!
    }

    fun faculty(ctx: MainActivity, ctx1: MathContext): BigDec {

        // todo use Stirling formula for large numbers?
        //  -> add extra function for it? :)
        // https://de.m.wikipedia.org/wiki/Stirling-Formel
        // https://de.m.wikipedia.org/wiki/Formel_von_Burnside ?

        if (this == positiveInfinity) return positiveInfinity
        if (state != null) return NaN
        if (sign() < 1) return NaN
        if (dec > BigDecimal(268609168))
            return positiveInfinity // largest representable number

        val n0 = dec.toLong()
        if (n0 <= 2) return this // self :)
        if (n0 < 22) { // fits into a long -> fast route :)
            var f = 2L
            for (i in 3..n0) {
                f *= i
            }
            return BigDec(BigDecimal(f))
        }

        // else a lot to calculate 😅
        // var i = n0
        /*var f = BigInteger.valueOf(i)
        val sliceSize = 256L // quickly consumable slices ^^
        val edgeSize = 1 shl 21*/

        val small = ctx1.precision <= 16 && n0 <= 59184980

        val log2 = 64 - n0.countLeadingZeroBits()
        val log2x = max(log2 - (if (small) 6 else 3), 0)
        val pieces = 1 shl log2x
        val bitLimit = 1 + (log2_10 * ctx1.precision).toInt()

        if (small) {
            // limitation of 999,999,999 in exp()
            val memory = DoubleArray(log2x + 1)
            var memorySize = 0
            return slowLoop("$n0!", ctx, { i ->
                if (i < pieces) {
                    var start = (2 + i * (n0 - 1) / pieces).toDouble()
                    val end = (2 + (i + 1) * (n0 - 1) / pieces).toDouble()
                    var partialSum = 0.0
                    while (start < end) {
                        partialSum += ln(start)
                        start++
                    }
                    memory[memorySize++] = partialSum
                    // calculate how often we need to multiply
                    var mask = 1
                    while (i.and(mask) == mask) {
                        memory[memorySize - 2] += memory[memorySize - 1]
                        memorySize--
                        mask += mask + 1
                    }
                    null
                } else {
                    BigDec(BigDecimal(memory[0])).exp(ctx1)
                }
            }, { "$it/$pieces" })
        }

        val memory = ArrayList<Number>()
        return slowLoop("$n0!", ctx, { i ->
            if (i < pieces) {
                val start = 2 + i * (n0 - 1) / pieces
                val end = 2 + (i + 1) * (n0 - 1) / pieces
                var partialFactorial = BigInteger.valueOf(start)
                for (j in start + 1 until end) {
                    partialFactorial *= BigInteger.valueOf(j)
                }
                memory.add(partialFactorial)
                // calculate how often we need to multiply
                var mask = 1
                while (i.and(mask) == mask) {
                    val a = memory[memory.size - 2]
                    val b = memory.removeAt(memory.lastIndex)
                    memory[memory.lastIndex] = when {
                        a is BigInteger && b is BigInteger -> {
                            if (a.bitCount() + b.bitCount() >= bitLimit) {
                                BigDecimal(a).multiply(BigDecimal(b), ctx1)
                            } else a.multiply(b)
                        }
                        a is BigDecimal && b is BigDecimal -> {
                            a.multiply(b, ctx1)
                        }
                        a is BigDecimal -> {
                            b as BigInteger
                            a.multiply(BigDecimal(b), ctx1)
                        }
                        else -> {
                            a as BigInteger
                            b as BigDecimal
                            b.multiply(BigDecimal(a), ctx1)
                        }
                    }
                    mask += mask + 1
                }
                null
            } else {
                val first = memory[0]
                val dec = if (first is BigDecimal) first
                else BigDecimal(first as BigInteger)
                BigDec(dec.round(ctx1))
            }

            /*if (i >= 4) {
                // to do make small subgroups for better performance :)
                val t0 = System.nanoTime()
                var subFactor = BigInteger.valueOf(i--)
                if (i < edgeSize) {
                    for (j in 0 until min(sliceSize, (i - 2) / 3)) {
                        subFactor *= BigInteger.valueOf(i * (i - 1) * (i - 2))
                        i -= 3
                    }
                } else {
                    for (j in 0 until min(sliceSize, (i - 1) shr 1)) {
                        subFactor *= BigInteger.valueOf(i * (i - 1)) // i² < 2^64, so it's fine :)
                        i -= 2
                    }
                }
                val t1 = System.nanoTime()
                println("${(t1 - t0)} [-> $i]")
                f *= subFactor
                null
            } else if (i >= 2) {
                var subFactor = BigInteger.valueOf(i--)
                for (j in 0 until i - 1) {
                    subFactor *= BigInteger.valueOf(i--)
                }
                f *= subFactor
                println("--> $i")
                null
            } else {
                println("done at $i")
                BigDec(BigDecimal(f))
            }*/
        }, { "$it/$pieces" })
    }

    fun isPrime(ctx0: MainActivity, ctx: MathContext): BigDec {
        if (state != null) return NaN
        if (sign() < 1) return NaN
        val number = dec.toBigInteger()
        if (number < two) return ZERO
        if (number == two) return ONE
        if (!number.testBit(0)) return BigDec(BigDecimal(two))
        // faster algorithm? already much faster than the sieve :)
        // 15x faster at size of ~1B
        val runs = 200_000L
        if (number.bitLength() < 50) {
            val value = number.toLong()
            val root = sqrt(value.toDouble()).toInt()
            for (i in 3..root step 2) {
                if (value % i == 0L) return BigDec(BigDecimal(BigInteger.valueOf(i.toLong())))
            }
            return ONE
        } else if (number.bitLength() < 64) {
            val value = number.toLong()
            val root = sqrt(value.toDouble()).toLong()
            val runs1 = root / runs
            return slowLoop("Is $number a prime?", ctx0, {
                val start = 3L + it * runs
                val end = min(root, 3L + (it + 1) * runs)
                if (start < root) {
                    var result: BigDec? = null
                    for (i in start until end step 2) {
                        if (value % i == 0L) {
                            result = BigDec(BigDecimal(i))
                            break
                        }
                    }
                    result
                } else ONE
            }, { "$it/$runs1" })
        } else {
            val root = sqrt(ctx).dec.toBigInteger().toLong()
            val zero = BigInteger.ZERO
            return slowLoop("Is $number a prime?", ctx0, {
                val start = 3L + it * runs
                val end = min(root, 3L + (it + 1) * runs)
                if (start < root) {
                    var result: BigDec? = null
                    for (i in start until end step 2) {
                        val bi = BigInteger.valueOf(i)
                        if (number % bi == zero) {
                            result = BigDec(BigDecimal(bi))
                            break
                        }
                    }
                    result
                } else ONE
            }, { "$it/$root" })
            /*for (i in 3L until root step 2) {
                val bi = BigInteger.valueOf(i)
                if (number % bi == zero)
                    return BigDec(BigDecimal(bi))
            }*/
        }
    }

    private fun isPrime1(value: Long): BigDec {
        val root = sqrt(value.toDouble()).toInt()
        for (i in 3..root step 2) {
            if (value % i == 0L) return BigDec(BigDecimal(BigInteger.valueOf(i.toLong())))
        }
        return ONE
    }

    /** add, but avoid unnecessary additions of too small terms */
    private fun BigDecimal.add2(other: BigDecimal, ctx: MathContext): BigDecimal {
        if (this == other) {
            // just multiply by two :)
            return BigDecimal(unscaledValue().shiftLeft(1), scale())
        }
        val s0 = guessSizeExp10(this)
        val s1 = guessSizeExp10(other)
        return if (abs(s0 - s1) > 1 + ctx.precision) {
            // difference is larger than packable into result
            if (s0 > s1) this else other
        } else {
            add(other, ctx)
        }
    }

    override fun hashCode(): Int {
        return dec.hashCode()
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        return (other is BigDec && other.state == state && other.dec == dec) ||
                (other is BigDecimal && state == null && dec == other)
    }

    private fun guessSizeExp10(d: BigDecimal): Double {
        return -d.scale() + d.unscaledValue().bitLength() * log2_10Inv
    }

}