package com.gamerz.app

import android.app.Activity
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.android.billingclient.api.*
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest
import java.util.concurrent.atomic.AtomicReference

private val subscriptionIds = listOf("gamerz_wamda", "gamerz_sadeem")
private const val verificationEndpoint =
    "https://gamerz-image-cleanup.netlify.app/.netlify/functions/verify-subscription"

private suspend fun verifySubscription(token: String): Boolean = withContext(Dispatchers.IO) {
    val identity = FirebaseAuth.getInstance().currentUser?.getIdToken(false)?.await()?.token
        ?: error("يجب تسجيل الدخول")
    val c = (URL(verificationEndpoint).openConnection() as HttpURLConnection).apply {
        requestMethod = "POST"
        doOutput = true
        connectTimeout = 10000
        readTimeout = 20000
        setRequestProperty("Content-Type", "application/json")
        setRequestProperty("Authorization", "Bearer " + identity)
    }
    try {
        c.outputStream.use { it.write(JSONObject().put("purchaseToken", token).toString().toByteArray()) }
        if (c.responseCode != 200) error("الخادم لم يقبل التحقق من الشراء (" + c.responseCode + ")")
        JSONObject(c.inputStream.bufferedReader().use { it.readText() }).optBoolean("active", false)
    } finally { c.disconnect() }
}

@Composable
fun GamerzBillingControls(uid: String) {
    val context = LocalContext.current
    val activity = context as? Activity
    val scope = rememberCoroutineScope()
    var status by remember(uid) { mutableStateOf("نتصل بمتجر Google Play…") }
    var products by remember(uid) { mutableStateOf<Map<String, ProductDetails>>(emptyMap()) }
    val holder = remember(uid) { AtomicReference<BillingClient?>(null) }

    val client = remember(uid) {
        BillingClient.newBuilder(context)
            .setListener { result, purchases ->
                if (result.responseCode == BillingClient.BillingResponseCode.OK) {
                    purchases.orEmpty().forEach { purchase ->
                        if (purchase.purchaseState == Purchase.PurchaseState.PURCHASED) {
                            scope.launch {
                                try {
                                    if (verifySubscription(purchase.purchaseToken)) {
                                        status = "تم التحقق من اشتراكك من Google Play."
                                        if (!purchase.isAcknowledged) {
                                            holder.get()?.acknowledgePurchase(
                                                AcknowledgePurchaseParams.newBuilder()
                                                    .setPurchaseToken(purchase.purchaseToken).build()
                                            ) { }
                                        }
                                    } else status = "الاشتراك غير نشط الآن."
                                } catch (e: Exception) {
                                    status = e.localizedMessage ?: "فشل التحقق من الشراء"
                                }
                            }
                        } else status = "الدفع قيد الانتظار ولم تُفعّل العضوية بعد."
                    }
                } else if (result.responseCode != BillingClient.BillingResponseCode.USER_CANCELED) {
                    status = "لم تكتمل عملية الشراء."
                }
            }
            .enablePendingPurchases(
                PendingPurchasesParams.newBuilder().enableOneTimeProducts().build()
            )
            .enableAutoServiceReconnection()
            .build()
            .also { holder.set(it) }
    }
    DisposableEffect(client) {
        client.startConnection(object : BillingClientStateListener {
            override fun onBillingSetupFinished(result: BillingResult) {
                if (result.responseCode != BillingClient.BillingResponseCode.OK) {
                    status = "خدمة Google Play Billing غير متوفرة."
                    return
                }
                client.queryProductDetailsAsync(
                    QueryProductDetailsParams.newBuilder().setProductList(
                        subscriptionIds.map { id ->
                            QueryProductDetailsParams.Product.newBuilder()
                                .setProductId(id)
                                .setProductType(BillingClient.ProductType.SUBS).build()
                        }).build()
                ) { reply, data ->
                    if (reply.responseCode == BillingClient.BillingResponseCode.OK) {
                        products = data.productDetailsList.associateBy { it.productId }
                        status = if (products.isEmpty()) "الخطط لم تُفعّل في Google Play Console بعد."
                            else "اختر خطتك؛ السعر والتجديد يحددهما Google Play."
                    } else status = "تعذّر تحميل الخطط من Google Play."
                }
                client.queryPurchasesAsync(
                    QueryPurchasesParams.newBuilder()
                        .setProductType(BillingClient.ProductType.SUBS).build()
                ) { reply, purchases ->
                    if (reply.responseCode == BillingClient.BillingResponseCode.OK) {
                        purchases.filter { it.purchaseState == Purchase.PurchaseState.PURCHASED }
                            .forEach { purchase ->
                                scope.launch {
                                    try {
                                        if (verifySubscription(purchase.purchaseToken) && !purchase.isAcknowledged)
                                            client.acknowledgePurchase(
                                                AcknowledgePurchaseParams.newBuilder()
                                                    .setPurchaseToken(purchase.purchaseToken).build()
                                            ) { }
                                    } catch (_: Exception) { }
                                }
                            }
                    }
                }
            }
            override fun onBillingServiceDisconnected() {
                status = "انقطع الاتصال بالمتجر. أعد فتح صفحة العضوية."
            }
        })
        onDispose { client.endConnection(); holder.set(null) }
    }
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("Google Play • الاشتراك الآمن", style = MaterialTheme.typography.titleLarge)
        Text(status, style = MaterialTheme.typography.bodySmall)
        subscriptionIds.forEach { id ->
            val details = products[id]
            val offer = details?.subscriptionOfferDetails?.firstOrNull()
            val price = offer?.pricingPhases?.pricingPhaseList?.lastOrNull()?.formattedPrice
            val label = if (id == "gamerz_wamda") "ومضة" else "سديم"
            OutlinedCard {
                Column(Modifier.fillMaxWidth().padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("عضوية " + label, style = MaterialTheme.typography.titleMedium)
                    Text(price ?: "السعر غير متاح حتى تنشر الخطة في Google Play")
                    Button(
                        modifier = Modifier.fillMaxWidth(),
                        enabled = activity != null && details != null &&
                            offer != null && client.isReady,
                        onClick = {
                            if (activity != null && details != null && offer != null) {
                                val params = BillingFlowParams.ProductDetailsParams.newBuilder()
                                    .setProductDetails(details).setOfferToken(offer.offerToken).build()
                                val hash = MessageDigest.getInstance("SHA-256")
                                    .digest(uid.toByteArray()).joinToString("") { "%02x".format(it) }
                                val flow = BillingFlowParams.newBuilder()
                                    .setProductDetailsParamsList(listOf(params))
                                    .setObfuscatedAccountId(hash).build()
                                val result = client.launchBillingFlow(activity, flow)
                                if (result.responseCode != BillingClient.BillingResponseCode.OK)
                                    status = "تعذّر بدء عملية الدفع."
                            }
                        }
                    ) { Text("الاشتراك في " + label) }
                }
            }
        }
        OutlinedButton(onClick = {
            context.startActivity(Intent(Intent.ACTION_VIEW,
                Uri.parse("https://play.google.com/store/account/subscriptions")))
        }, modifier = Modifier.fillMaxWidth()) { Text("إدارة أو إلغاء اشتراكي في Google Play") }
        Text("لا تُمنح أي مزايا مدفوعة إلا بعد تحقق خادم Gamerz من Google Play.",
            style = MaterialTheme.typography.labelSmall)
    }
}
