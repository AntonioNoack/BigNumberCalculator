package me.antonionoack.calculator

import java.util.*
import kotlin.math.sqrt

fun isPrime2(n: Int): BigDec {
    // support more than 2B?
    val end = sqrt(n.toDouble()).toInt() + 1
    val list = BitSet(end)
    for (i in 2..end) {
        if (!list[i]) {// we found a prime :)
            if (n % i == 0) return BigDec.ZERO
            // mark all multiples
            var j = i + i
            while (j < end) {
                list.set(j)
                j += i
            }
        }
    }
    return BigDec.ONE
}

fun isPrime2(n: Long): BigDec {
    // support more than 2B?
    val end = sqrt(n.toDouble()).toInt() + 1
    if (end.toLong() * end.toLong() <= n) return BigDec.NaN
    val list = BitSet(end)
    for (i in 2..end) {
        if (!list[i]) {// we found a prime :)
            if (n % i == 0L) return BigDec.ZERO
            // mark all multiples
            var j = i + i
            while (j < end) {
                list.set(j)
                j += i
            }
        }
    }
    return BigDec.ONE
}