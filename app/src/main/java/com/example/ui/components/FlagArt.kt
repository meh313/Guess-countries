package com.example.ui.components

import androidx.annotation.DrawableRes
import com.example.R

/**
 * The bundled artwork for a country's flag (res/drawable-nodpi/flag_xx.webp), or null when a country has
 * none. Spelled out instead of looked up by name so a missing file is a compile error and the resource
 * shrinker keeps exactly these files.
 */
@DrawableRes
fun flagArtFor(code: String): Int? = when (code) {
    "FR" -> R.drawable.flag_fr
    "DE" -> R.drawable.flag_de
    "IT" -> R.drawable.flag_it
    "GB" -> R.drawable.flag_gb
    "ES" -> R.drawable.flag_es
    "GR" -> R.drawable.flag_gr
    "SE" -> R.drawable.flag_se
    "NO" -> R.drawable.flag_no
    "UA" -> R.drawable.flag_ua
    "US" -> R.drawable.flag_us
    "CA" -> R.drawable.flag_ca
    "MX" -> R.drawable.flag_mx
    "BR" -> R.drawable.flag_br
    "AR" -> R.drawable.flag_ar
    "CO" -> R.drawable.flag_co
    "CL" -> R.drawable.flag_cl
    "JP" -> R.drawable.flag_jp
    "CN" -> R.drawable.flag_cn
    "IN" -> R.drawable.flag_in
    "KR" -> R.drawable.flag_kr
    "VN" -> R.drawable.flag_vn
    "TH" -> R.drawable.flag_th
    "SA" -> R.drawable.flag_sa
    "EG" -> R.drawable.flag_eg
    "KE" -> R.drawable.flag_ke
    "ZA" -> R.drawable.flag_za
    "NG" -> R.drawable.flag_ng
    "MA" -> R.drawable.flag_ma
    "TZ" -> R.drawable.flag_tz
    "AU" -> R.drawable.flag_au
    "NZ" -> R.drawable.flag_nz
    "FJ" -> R.drawable.flag_fj
    "AQ" -> R.drawable.flag_aq
    else -> null
}
