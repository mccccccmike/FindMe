package com.example.myapplication
import com.google.gson.annotations.SerializedName

data class Transaction(
    @SerializedName("id")
    val id: Int?,
    @SerializedName("jsonrpc")
    val jsonrpc: String?,
    @SerializedName("result")
    val result: Result?
)

data class Result(
    @SerializedName("from")
    val from: String?,
    @SerializedName("to")
    val to: String?
)

data class Pair(
    val code: String?,
    val `data`: List<Data?>?
)

data class Data(
    val name: String?,
    val symbol: String?,
    val symbolRef: String?
)