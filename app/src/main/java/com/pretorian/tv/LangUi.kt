package com.pretorian.tv

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun Flag(res: Int, w: Int = 36) {
    Image(painterResource(res), contentDescription = null, modifier = Modifier.size(w.dp, (w * 2 / 3).dp).clip(RoundedCornerShape(4.dp)))
}

/** One language row: flag, own name, name in the app language. */
@Composable
private fun LangRow(l: Lang?, title: String, sub: String?, badge: String?, selected: Boolean, fr: FocusRequester?, onClick: () -> Unit) {
    Focusable(modifier = Modifier.fillMaxWidth().height(58.dp), onClick = onClick, focusRequester = fr,
        normal = if (selected) C.CardFocus else C.Card, zoom = 1.01f) { _ ->
        Row(Modifier.fillMaxSize().padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            if (l != null) Flag(l.flag) else Box(Modifier.size(36.dp, 24.dp).clip(RoundedCornerShape(4.dp)).background(C.CardHi),
                contentAlignment = Alignment.Center) { Ico(R.drawable.lc_globe, 16.dp, C.Soft) }
            Column(Modifier.weight(1f)) {
                T(title, 16.sp, C.Text, FontWeight.SemiBold)
                if (sub != null) T(sub, 12.sp, C.Muted)
            }
            if (badge != null) Box(Modifier.size(28.dp).clip(CircleShape).background(C.Red), contentAlignment = Alignment.Center) {
                T(badge, 13.sp, C.OnRed, FontWeight.Bold)
            }
            else if (selected) Ico(R.drawable.lc_check, 20.dp, C.Red)
        }
    }
}

/** Pick one language by flag and name. [auto] adds a first row "same as app language". */
@Composable
fun LangPicker(title: String, current: String, langs: List<Lang> = Langs.APP, auto: Boolean = false,
               onDismiss: () -> Unit, onPick: (String) -> Unit) {
    val first = remember { FocusRequester() }
    Popup(title, onDismiss, width = 600.dp) {
        if (auto) LangRow(null, "Kao jezik aplikacije", Langs.label(Look.lang), null, current == "auto", first) { onPick("auto") }
        langs.forEachIndexed { i, l ->
            LangRow(l, l.native, Langs.label(l.code), null, current == l.code, if (!auto && i == 0) first else null) { onPick(l.code) }
        }
    }
    LaunchedEffect(Unit) { first.tryFocus() }
}

/** Pick several languages in order: the first one clicked is the most wanted. */
@Composable
fun LangOrderPicker(title: String, current: List<String>, langs: List<Lang> = Langs.MEDIA, onDismiss: () -> Unit, onSave: (List<String>) -> Unit) {
    val chosen = remember { mutableStateListOf<String>().also { it.addAll(current) } }
    val first = remember { FocusRequester() }
    Popup(title, onDismiss, width = 600.dp) {
        T("Klikni jezike redom: prvi je najvažniji.", 13.sp, C.Muted, maxLines = 2)
        Button("Gotovo", R.drawable.lc_check, primary = true, focusRequester = first) { onSave(chosen.toList()) }
        langs.forEach { l ->
            val pos = chosen.indexOf(l.code)
            LangRow(l, l.native, Langs.label(l.code), if (pos >= 0) "${pos + 1}" else null, pos >= 0, null) {
                if (pos >= 0) chosen.remove(l.code) else chosen.add(l.code)
            }
        }
    }
    LaunchedEffect(Unit) { first.tryFocus() }
}
