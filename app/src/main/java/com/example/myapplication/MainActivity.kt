package com.example.myapplication

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
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.isVisible
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
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

class MainActivity : AppCompatActivity() {

    val client = OkHttpClient.Builder()
        .addNetworkInterceptor(StethoInterceptor())
        .build()

    val gson = GsonBuilder().create()

    private val textViewResults: TextView by lazy {
        findViewById(R.id.textViewResults)
    }

    private val textViewParis: TextView by lazy {
        findViewById(R.id.textViewParis)
    }

    private val buttonStart: Button by lazy {
        findViewById(R.id.buttonStart)
    }

    private val progressDialog:ProgressDialog by lazy {
        ProgressDialog(this).apply {
            this.setCanceledOnTouchOutside(false)
        }
    }

    /*
        "0xe00ed75bf786c4bc5a2bc700f82d58a13efbe993",
        "0xe2773ac103bc59b6abdc77722c516ad4a70961f8",
        "0xd16dc7b67afd1bc2e6eb1ca679f7ff25a23dd62b",
        "0x94308d1ec11d21c19dfa109cb2d52b43164108e7",
        "0x5b7f957086a694270d2ff6c92359ccc403215453",
        "0x9866930060bc5f532dc1b9db721069720564fd12",
        "0x05c4ca5dca0d347da5d174a7b6987dee658c53ed",
        "0x19630d70c9d2d5d4f18b779d7cb79fc98342a9c4",
        "0x0d74ad9986e08a1d02279a755e6bd7a04c4d7053",
        "0x6b9ce687f36de450a729256a168522d9c093e9c1"
     */
    private val paris = mutableListOf<String>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        setContentView(R.layout.activity_main)

        buttonStart.setOnClickListener {
            lifecycleScope.launch {
//                lifecycle.repeatOnLifecycle(Lifecycle.State.CREATED) {
//
//                }
                progressDialog.show()

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
                    textViewResults.text = builder
                    textViewResults.movementMethod = LinkMovementMethod.getInstance()
                    textViewResults.highlightColor = Color.TRANSPARENT
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

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) {
            val clipboard = getSystemService(CLIPBOARD_SERVICE) as ClipboardManager
            if (clipboard.hasPrimaryClip() && clipboard.primaryClip != null) {
                val clip: String =
                    clipboard.primaryClip?.getItemAt(0)?.coerceToText(this)
                        .toString()

                if (paris.contains(clip).not()) {
                    Toast.makeText(this, "Detected: $clip", Toast.LENGTH_SHORT).show()
                    paris.add(clip)
                }

                textViewParis.text = paris.joinToString(",\n")
                buttonStart.isVisible = paris.isNotEmpty()
            }
        }
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