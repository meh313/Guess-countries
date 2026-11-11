package com.example.monetization

import android.app.Activity
import android.content.Context
import com.android.billingclient.api.AcknowledgePurchaseParams
import com.android.billingclient.api.BillingClient
import com.android.billingclient.api.BillingClientStateListener
import com.android.billingclient.api.BillingFlowParams
import com.android.billingclient.api.BillingResult
import com.android.billingclient.api.PendingPurchasesParams
import com.android.billingclient.api.ProductDetails
import com.android.billingclient.api.Purchase
import com.android.billingclient.api.PurchasesUpdatedListener
import com.android.billingclient.api.QueryProductDetailsParams
import com.android.billingclient.api.QueryPurchasesParams
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * Google Play Billing for the one-time Remove ads purchase ([Store.REMOVE_ADS], a non-consumable in-app product the
 * Play Console must list, see docs/monetization.md). On connecting it reads the price and the purchases made, so a
 * player on a new phone gets their purchase back by signing in; a purchase is acknowledged as soon as it is seen,
 * which Play asks for within three days or it is refunded. The callbacks come on whatever thread the library
 * chooses; the flows carry the results to the screens.
 */
class PlayStore(private val productId: String = Store.REMOVE_ADS) : Store, PurchasesUpdatedListener {
    private val _adsRemoved = MutableStateFlow<Boolean?>(null)
    override val adsRemoved: StateFlow<Boolean?> = _adsRemoved

    private val _removeAdsPrice = MutableStateFlow<String?>(null)
    override val removeAdsPrice: StateFlow<String?> = _removeAdsPrice

    private val _purchase = MutableStateFlow(PurchaseState.IDLE)
    override val purchase: StateFlow<PurchaseState> = _purchase

    private val _available = MutableStateFlow(false)
    override val available: StateFlow<Boolean> = _available

    private var client: BillingClient? = null
    private var product: ProductDetails? = null

    /** Set by [restore] so the next purchase lookup reports what it found; a lookup on connecting says nothing. */
    private var reportLookup = false

    override fun connect(context: Context) {
        if (client != null) return
        val built = BillingClient.newBuilder(context.applicationContext)
            .setListener(this)
            .enablePendingPurchases(PendingPurchasesParams.newBuilder().enableOneTimeProducts().build())
            .enableAutoServiceReconnection()
            .build()
        client = built
        built.startConnection(object : BillingClientStateListener {
            override fun onBillingSetupFinished(result: BillingResult) {
                if (result.responseCode == BillingClient.BillingResponseCode.OK) {
                    queryProduct()
                    queryPurchases()
                } else {
                    _available.value = false
                }
            }

            override fun onBillingServiceDisconnected() {
                _available.value = false
            }
        })
    }

    private fun queryProduct() {
        val params = QueryProductDetailsParams.newBuilder()
            .setProductList(
                listOf(
                    QueryProductDetailsParams.Product.newBuilder()
                        .setProductId(productId)
                        .setProductType(BillingClient.ProductType.INAPP)
                        .build()
                )
            )
            .build()
        client?.queryProductDetailsAsync(params) { result, details ->
            val found = if (result.responseCode == BillingClient.BillingResponseCode.OK) {
                details.productDetailsList.firstOrNull { it.productId == productId }
            } else {
                null
            }
            product = found
            _removeAdsPrice.value = found?.oneTimePurchaseOfferDetailsList?.firstOrNull()?.formattedPrice
                ?: found?.oneTimePurchaseOfferDetails?.formattedPrice
            _available.value = found != null
        }
    }

    private fun queryPurchases() {
        val params = QueryPurchasesParams.newBuilder().setProductType(BillingClient.ProductType.INAPP).build()
        client?.queryPurchasesAsync(params) { result, purchases ->
            val report = reportLookup
            reportLookup = false
            if (result.responseCode != BillingClient.BillingResponseCode.OK) {
                if (report) _purchase.value = PurchaseState.UNAVAILABLE
                return@queryPurchasesAsync
            }
            val owned = handle(purchases)
            if (report) {
                _purchase.value = when {
                    owned -> PurchaseState.OWNED
                    _purchase.value == PurchaseState.PENDING -> PurchaseState.PENDING
                    else -> PurchaseState.NOTHING_TO_RESTORE
                }
            }
        }
    }

    /**
     * Reads the Remove ads purchase out of [purchases] (Play's complete list of the account's one-time purchases):
     * owned when one is purchased, acknowledging it if need be; pending when one waits for its payment; else not owned.
     * True when owned.
     */
    private fun handle(purchases: List<Purchase>): Boolean {
        val ours = purchases.filter { productId in it.products }
        val purchased = ours.firstOrNull { it.purchaseState == Purchase.PurchaseState.PURCHASED }
        if (purchased != null) {
            if (!purchased.isAcknowledged) {
                client?.acknowledgePurchase(AcknowledgePurchaseParams.newBuilder().setPurchaseToken(purchased.purchaseToken).build()) {}
            }
            _adsRemoved.value = true
            return true
        }
        _adsRemoved.value = false
        if (ours.any { it.purchaseState == Purchase.PurchaseState.PENDING }) _purchase.value = PurchaseState.PENDING
        return false
    }

    override fun onPurchasesUpdated(result: BillingResult, purchases: List<Purchase>?) {
        when (result.responseCode) {
            BillingClient.BillingResponseCode.OK -> {
                val owned = handle(purchases.orEmpty())
                if (owned) _purchase.value = PurchaseState.OWNED else if (_purchase.value != PurchaseState.PENDING) _purchase.value = PurchaseState.IDLE
            }
            BillingClient.BillingResponseCode.USER_CANCELED -> _purchase.value = PurchaseState.IDLE
            // Bought already (on another phone, say): the lookup turns the ads off.
            BillingClient.BillingResponseCode.ITEM_ALREADY_OWNED -> {
                reportLookup = true
                queryPurchases()
            }
            else -> _purchase.value = PurchaseState.FAILED
        }
    }

    override fun buyRemoveAds(activity: Activity) {
        val ready = client?.takeIf { it.isReady }
        val details = product
        if (ready == null || details == null) {
            _purchase.value = PurchaseState.UNAVAILABLE
            return
        }
        _purchase.value = PurchaseState.BUSY
        val productParams = BillingFlowParams.ProductDetailsParams.newBuilder().setProductDetails(details)
        // A one-time product with several purchase options names the one to buy; a plain one has none to name.
        details.oneTimePurchaseOfferDetailsList?.firstOrNull()?.offerToken?.takeIf { it.isNotBlank() }?.let(productParams::setOfferToken)
        val params = BillingFlowParams.newBuilder().setProductDetailsParamsList(listOf(productParams.build())).build()
        val launched = ready.launchBillingFlow(activity, params)
        if (launched.responseCode != BillingClient.BillingResponseCode.OK) _purchase.value = PurchaseState.FAILED
    }

    override fun restore() {
        val ready = client?.takeIf { it.isReady }
        if (ready == null) {
            _purchase.value = PurchaseState.UNAVAILABLE
            return
        }
        _purchase.value = PurchaseState.BUSY
        reportLookup = true
        queryPurchases()
    }

    override fun clearPurchaseState() {
        if (_purchase.value != PurchaseState.BUSY) _purchase.value = PurchaseState.IDLE
    }
}
