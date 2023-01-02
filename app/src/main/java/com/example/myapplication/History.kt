package com.example.myapplication
import com.google.gson.annotations.SerializedName

data class History(
    val baseTokenSymbol: String?,
    val logs: List<TradingHistory?>?,
    val quoteTokenSymbol: String?,
    val schemaVersion: String?
)

data class TradingHistory(
    val amount0: String?,
    val amount1: String?,
    val blockNumber: Int?,
    val blockTimestamp: Long?,
    val logIndex: Int?,
    val logType: String?,
    val maker: String?,
    val priceUsd: String?,
    val txnHash: String?,
    val txnType: String?,
    val volumeUsd: String?
)