package com.example.data

import com.example.model.Book
import com.example.model.BookLevel

object GlobalBook {
    fun bucketSizeFor(price: Double): Double = when {
        price >= 1000 -> 0.5
        price >= 100 -> 0.1
        price >= 10 -> 0.05
        price >= 1 -> 0.001
        else -> 0.00001
    }

    fun build(binance: Book, bybit: Book, okx: Book, multiExchange: Boolean): Book {
        val bids = sortedMapOf<Double, Double>(Comparator.reverseOrder())
        val asks = sortedMapOf<Double, Double>()

        fun addLevels(levels: List<BookLevel>, isBid: Boolean) {
            val bucket = bucketSizeFor(levels.firstOrNull()?.price ?: return)
            for (lvl in levels.take(50)) {
                val key = (lvl.price / bucket).toLong() * bucket
                if (isBid) bids[key] = (bids[key] ?: 0.0) + lvl.qty
                else asks[key] = (asks[key] ?: 0.0) + lvl.qty
            }
        }

        addLevels(binance.bids, true); addLevels(binance.asks, false)
        if (multiExchange) {
            addLevels(bybit.bids, true); addLevels(bybit.asks, false)
            addLevels(okx.bids, true); addLevels(okx.asks, false)
        }

        return Book(
            bids = bids.entries.take(20).map { BookLevel(it.key, it.value) },
            asks = asks.entries.take(20).map { BookLevel(it.key, it.value) },
            ts = maxOf(binance.ts, bybit.ts, okx.ts),
            label = if (multiExchange) "Global" else "Binance"
        )
    }
}
