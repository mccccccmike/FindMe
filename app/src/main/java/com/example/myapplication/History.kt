package com.example.myapplication
import com.google.gson.annotations.SerializedName

data class History(
    @SerializedName("baseTokenSymbol")
    val baseTokenSymbol: String?,
    @SerializedName("quoteTokenSymbol")
    val quoteTokenSymbol: String?,
    @SerializedName("schemaVersion")
    val schemaVersion: String?,
    @SerializedName("tradingHistory")
    val tradingHistory: List<TradingHistory?>?
)

data class TradingHistory(
    @SerializedName("amount0")
    val amount0: String?,
    @SerializedName("amount1")
    val amount1: String?,
    @SerializedName("blockNumber")
    val blockNumber: Int?,
    @SerializedName("blockTimestamp")
    val blockTimestamp: Long?,
    @SerializedName("logIndex")
    val logIndex: Int?,
    @SerializedName("priceUsd")
    val priceUsd: String?,
    @SerializedName("txnHash")
    val txnHash: String?,
    @SerializedName("type")
    val type: String?,
    @SerializedName("volumeUsd")
    val volumeUsd: String?
)