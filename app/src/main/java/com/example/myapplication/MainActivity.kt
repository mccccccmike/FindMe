package com.example.myapplication

import android.R.attr.label
import android.app.ProgressDialog
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.graphics.Color
import android.icu.text.MessageFormat
import android.os.Build
import android.os.Bundle
import android.text.SpannableString
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.TextPaint
import android.text.method.LinkMovementMethod
import android.text.style.ClickableSpan
import android.util.Log
import android.view.View
import android.view.WindowManager
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.collection.SparseArrayCompat
import androidx.core.widget.ContentLoadingProgressBar
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.SortedList
import com.facebook.stetho.okhttp3.StethoInterceptor
import com.google.gson.GsonBuilder
import kotlinx.coroutines.*
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.jetbrains.annotations.TestOnly
import java.io.*
import java.util.*
import kotlin.collections.LinkedHashSet


class MainActivity : AppCompatActivity() {

    val client = OkHttpClient.Builder()
        .addNetworkInterceptor(StethoInterceptor())
        .build()

    val gson = GsonBuilder().create()

    private val textView: TextView by lazy {
        findViewById(R.id.textView)
    }

    private val progressDialog:ProgressDialog by lazy {
        ProgressDialog(this)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        setContentView(R.layout.activity_main)
        progressDialog.show()
        lifecycleScope.launch {
            lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
                val paris = listOf<String>(
                    "0xf040eD78e6880Af04D5040c1C96F038A75eeFa9F",
                    "0xEF15db98153D03a014C93C871524394579e16eC9",
                    "0xe46E6a3C5d4472a04794aF7f7ab3862df35C0229",
                    "0xE875671d5fC032b4636eA0640575a338f3bD4787",
                    "0x1cf77b56db68d287953ec2070954f73203b2682d",
                    "0xc754b7eEd1D31eA8017F581C4C9cd7dd86a969CB"
                )
                val oldestN = 30
                val baseTokenSymbols = LinkedHashSet<String>()

                paris.map {
                    withContext(Dispatchers.IO + CoroutineExceptionHandler { coroutineContext, throwable ->
                        // do nothing
                        Log.e("my", throwable.message ?: "trading-history Unknown Error")
                    }) {
                        var isTradingHistoryNull: Boolean
                        var tb: Long? = null
                        val pair = it
                        val tradingHistory = mutableListOf<TradingHistory>()
                        do {
                            val url = if (tb == null) {
                                "https://io.dexscreener.com/u/trading-history/recent/ethereum/$pair?q=0xC02aaA39b223FE8D0A0e5C4F27eAD9083C756Cc2"
                            } else {
                                "https://io.dexscreener.com/u/trading-history/recent/ethereum/$pair?tb=$tb&q=0xC02aaA39b223FE8D0A0e5C4F27eAD9083C756Cc2"
                            }

                            val request = okhttp3.Request.Builder()
                                .url(url)
                                .build()

                            client.newCall(request).execute().use {
                                if (it.isSuccessful) {
                                    val history =
                                        gson.fromJson(it.body?.string(), History::class.java)
                                    isTradingHistoryNull = history.tradingHistory != null
                                    baseTokenSymbols.add(history.baseTokenSymbol?:"Unknown Token")

                                    history.tradingHistory?.filterNotNull()?.let {
                                        tb = it.lastOrNull()?.blockTimestamp
                                        tradingHistory.addAll(it)
                                    }
                                } else {
                                    isTradingHistoryNull = true
                                }
                            }

                        } while (isTradingHistoryNull)

                        // need to filter out buy transactions
                        tradingHistory
                            .filter { it.type == "buy" }
                            .takeLast(oldestN).reversed()
                    }
                }.flatMap {
                    it
                }.mapNotNull {
                    withContext(Dispatchers.IO + CoroutineExceptionHandler { coroutineContext, throwable ->
                        // do nothing
                        Log.e("my", throwable.message ?: "eth_getTransactionByHash Unknown Error")
                    }) {
                        val mediaType = "application/json".toMediaTypeOrNull()
                        val body =
                            "{\"id\":1,\"jsonrpc\":\"2.0\",\"params\":[\"${it.txnHash}\"],\"method\":\"eth_getTransactionByHash\"}".toRequestBody(
                                mediaType
                            )
                        val request = Request.Builder()
                            .url("https://eth-mainnet.g.alchemy.com/v2/Q5sH2zvE_fu5H1XI4RC09iQXlSHIbJPS")
                            .post(body)
                            .addHeader("accept", "application/json")
                            .addHeader("content-type", "application/json")
                            .build()

                        client.newCall(request).execute().use {
                            if (it.isSuccessful) {
                                val transaction =
                                    gson.fromJson(it.body?.string(), Transaction::class.java)
                                transaction.result?.from
                            } else {
                                null
                            }
                        }
                    }
                }.apply {

                    Log.e("my", "Is the result(${this.size}) equal to ${oldestN * paris.size}?")
                    Log.e("my", baseTokenSymbols.toString())
                    Log.e("my", this.joinToString(",\n"))

                }.mapIndexed { index, s ->
                    s to index
                }.groupBy {
                    it.first
                }.map {
//            it.key to it.value.map {
//                it.second
//            }

                    it.key to it.value.map {
                        it.second
                    }.groupBy {
                        it / oldestN
                    }
                }.sortedByDescending {
                    it.second.size
                }.map {
                    val tokenSymbolsArray = baseTokenSymbols.toArray()
                    val tokens = it.second.map {
                        "bought token [${tokenSymbolsArray[it.key]}] ${it.value.size} times ${it.value.map { "$it -> ${toOrdinal((it + 1) % (oldestN + 1))}" }}"
                    }.toList().joinToString(", \n")
//                    println("Address [${it.first}]:\n${tokens}")
                    val row = "Address [${it.first}]:\n${tokens}"
                    Log.e("my", row)

                    val ss = SpannableString(row)
                    val clickableSpan = object:ClickableSpan() {
                        override fun onClick(p0: View) {
                            Toast.makeText(this@MainActivity, "Text copied to clipboard.", Toast.LENGTH_SHORT).show()
                            val clipboard: ClipboardManager =
                                getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                            val clip = ClipData.newPlainText(null, it.first)
                            clipboard.setPrimaryClip(clip)
                        }

                        override fun updateDrawState(ds: TextPaint) {
                            super.updateDrawState(ds)
                            ds.isUnderlineText = false
                        }
                    }
                    val start = row.indexOf(it.first)
                    val end = start + it.first.length
                    ss.setSpan(clickableSpan,start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)

                    ss
                }.let {
                    progressDialog.hide()
                    val builder = SpannableStringBuilder()
                    it.forEach {
                        builder.appendLine(it)
                    }
                    textView.text = builder
                    textView.movementMethod = LinkMovementMethod.getInstance()
                    textView.highlightColor = Color.TRANSPARENT
                }
            }
        }
    }

    fun toOrdinal(day: Int): String {
        val formatter = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            MessageFormat("{0,ordinal}", Locale.getDefault())
        } else {
            TODO("VERSION.SDK_INT < N")
        }
        return formatter.format(arrayOf(day))
    }
}

enum class CHAINS(
    val wallets: String,
    val groupBy: Int
) {
    ETH("C:\\Users\\iwicfattis\\Desktop\\wallets_eth.txt", 30),
    BSC("C:\\Users\\iwicfattis\\Desktop\\wallets_bsc.txt", 40)
}

@TestOnly
fun main() {
    val chain = CHAINS.ETH
    val file = File(chain.wallets)
    getFileContent(FileInputStream(file), "utf-8")
        .replace("\n", "")
        .split(",")
        .mapIndexed { index, s ->
            s to index
        }.groupBy {
            it.first
        }.map {
//            it.key to it.value.map {
//                it.second
//            }

            it.key to it.value.map {
                it.second
            }.groupBy {
                it / chain.groupBy
            }
        }.sortedByDescending {
            it.second.size
        }.forEach {
            val tokens = it.second.map {
                "bought token [${it.key}] ${it.value.size}(${it.value}) times"
            }.toList().joinToString(", \n")
            println("Address [${it.first}]:\n${tokens}")
        }
}

@Throws(IOException::class)
fun getFileContent(
    fis: FileInputStream?,
    encoding: String?
): String {
    BufferedReader(InputStreamReader(fis, encoding)).use { br ->
        val sb = StringBuilder()
        var line: String?
        while (br.readLine().also { line = it } != null) {
            sb.append(line)
//            sb.append('\n')
        }
        return sb.toString()
    }
}