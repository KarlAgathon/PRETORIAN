package com.pretorian.tv

/**
 * App language. Texts are written in Bosnian in the code; [tr] swaps a known text for the chosen language.
 * Every T() goes through [tr], so menus switch the moment the language changes.
 * Unknown texts (channel names, film titles…) stay as they are.
 */
data class Lang(val code: String, val name: String, val native: String, val flag: Int)

object Langs {
    /** app languages, in the order shown */
    val APP = listOf(
        Lang("bs", "Bosanski", "Bosanski", R.drawable.flag_bs),
        Lang("hr", "Hrvatski", "Hrvatski", R.drawable.flag_hr),
        Lang("sr", "Srpski", "Srpski", R.drawable.flag_rs),
        Lang("en", "Engleski", "English", R.drawable.flag_en),
        Lang("de", "Njemački", "Deutsch", R.drawable.flag_de),
        Lang("tr", "Turski", "Türkçe", R.drawable.flag_tr),
        Lang("it", "Italijanski", "Italiano", R.drawable.flag_it),
        Lang("bg", "Bugarski", "Български", R.drawable.flag_bg),
        Lang("ro", "Rumunski", "Română", R.drawable.flag_ro),
        Lang("ar", "Arapski", "العربية", R.drawable.flag_ar),
    )
    /** languages for subtitles, audio and descriptions */
    val MEDIA = APP + listOf(
        Lang("fr", "Francuski", "Français", R.drawable.flag_fr),
        Lang("es", "Španski", "Español", R.drawable.flag_es),
    )
    fun of(code: String): Lang? = MEDIA.firstOrNull { it.code == code.lowercase().take(2) }
    /** "Bosanski" in the app language, "English" etc. */
    fun label(code: String): String = of(code)?.let { tr(it.name) } ?: code.uppercase()
}

private const val COLS = "bs|en|de|hr|sr|bg|ro|tr|it|ar"

/* One line per text: bs | en | de | hr | sr | bg | ro | tr | it | ar.  "=" means the same as Bosnian. */
private val TABLE = """
Početna|Home|Startseite|=|=|Начало|Acasă|Ana sayfa|Home|الرئيسية
TV|TV|TV|=|=|ТВ|TV|TV|TV|التلفاز
Filmovi|Movies|Filme|=|=|Филми|Filme|Filmler|Film|الأفلام
Serije|Series|Serien|=|=|Сериали|Seriale|Diziler|Serie|المسلسلات
Sport|Sports|Sport|=|=|Спорт|Sport|Spor|Sport|الرياضة
Postavke|Settings|Einstellungen|=|Podešavanja|Настройки|Setări|Ayarlar|Impostazioni|الإعدادات
Playliste|Playlists|Playlists|Playliste|Plejliste|Плейлисти|Playlisturi|Oynatma listeleri|Playlist|قوائم التشغيل
Dodaj, aktiviraj, ažuriraj|Add, activate, update|Hinzufügen, aktivieren, aktualisieren|=|Dodaj, aktiviraj, ažuriraj|Добави, активирай, обнови|Adaugă, activează, actualizează|Ekle, etkinleştir, güncelle|Aggiungi, attiva, aggiorna|إضافة، تفعيل، تحديث
Grupe i kanali|Groups and channels|Gruppen und Sender|=|=|Групи и канали|Grupuri și canale|Gruplar ve kanallar|Gruppi e canali|المجموعات والقنوات
Redoslijed, nazivi, sakrivanje, PIN|Order, names, hiding, PIN|Reihenfolge, Namen, Ausblenden, PIN|=|Redosled, nazivi, sakrivanje, PIN|Ред, имена, скриване, PIN|Ordine, nume, ascundere, PIN|Sıra, adlar, gizleme, PIN|Ordine, nomi, nascondi, PIN|الترتيب، الأسماء، الإخفاء، PIN
Izgled|Appearance|Aussehen|=|=|Изглед|Aspect|Görünüm|Aspetto|المظهر
Tema, boja, prozirnost, veličine|Theme, colour, transparency, sizes|Design, Farbe, Transparenz, Größen|=|Tema, boja, providnost, veličine|Тема, цвят, прозрачност, размери|Temă, culoare, transparență, mărimi|Tema, renk, saydamlık, boyutlar|Tema, colore, trasparenza, dimensioni|السمة، اللون، الشفافية، الأحجام
Filmovi i titlovi|Movies and subtitles|Filme und Untertitel|=|Filmovi i titlovi|Филми и субтитри|Filme și subtitrări|Filmler ve altyazılar|Film e sottotitoli|الأفلام والترجمة
TMDB, IMDb, titlovi, jezici|TMDB, IMDb, subtitles, languages|TMDB, IMDb, Untertitel, Sprachen|=|=|TMDB, IMDb, субтитри, езици|TMDB, IMDb, subtitrări, limbi|TMDB, IMDb, altyazılar, diller|TMDB, IMDb, sottotitoli, lingue|TMDB، IMDb، الترجمة، اللغات
Roditelji i jednostavni mod|Parents and simple mode|Eltern und einfacher Modus|=|Roditelji i jednostavni režim|Родители и опростен режим|Părinți și mod simplu|Ebeveyn ve basit mod|Genitori e modalità semplice|الأهل والوضع البسيط
PIN, jednostavni mod|PIN, simple mode|PIN, einfacher Modus|=|PIN, jednostavni režim|PIN, опростен режим|PIN, mod simplu|PIN, basit mod|PIN, modalità semplice|PIN، الوضع البسيط
Ažuriranje|Updates|Updates|=|Ažuriranje|Обновяване|Actualizări|Güncelleme|Aggiornamenti|التحديثات
Nova verzija aplikacije|New app version|Neue App-Version|=|=|Нова версия|Versiune nouă|Yeni uygulama sürümü|Nuova versione|إصدار جديد من التطبيق
O aplikaciji|About|Über die App|=|=|За приложението|Despre aplicație|Hakkında|Informazioni|حول التطبيق
Verzija i licence|Version and licences|Version und Lizenzen|=|=|Версия и лицензи|Versiune și licențe|Sürüm ve lisanslar|Versione e licenze|الإصدار والتراخيص
Jezik|Language|Sprache|=|=|Език|Limbă|Dil|Lingua|اللغة
Jezik aplikacije|App language|App-Sprache|=|=|Език на приложението|Limba aplicației|Uygulama dili|Lingua dell'app|لغة التطبيق
Meniji, dugmad i poruke|Menus, buttons and messages|Menüs, Tasten und Meldungen|=|=|Менюта, бутони и съобщения|Meniuri, butoane și mesaje|Menüler, düğmeler ve mesajlar|Menu, pulsanti e messaggi|القوائم والأزرار والرسائل
Jezik promijenjen|Language changed|Sprache geändert|=|Jezik promenjen|Езикът е сменен|Limba a fost schimbată|Dil değiştirildi|Lingua cambiata|تم تغيير اللغة
Spremljeno|Saved|Gespeichert|=|Sačuvano|Запазено|Salvat|Kaydedildi|Salvato|تم الحفظ
Gotovo|Done|Fertig|=|=|Готово|Gata|Tamam|Fatto|تم
Odustani|Cancel|Abbrechen|=|Otkaži|Отказ|Anulează|İptal|Annulla|إلغاء
Spremi|Save|Speichern|=|Sačuvaj|Запази|Salvează|Kaydet|Salva|حفظ
Učitavam…|Loading…|Lädt…|=|Učitavam…|Зареждане…|Se încarcă…|Yükleniyor…|Caricamento…|جارٍ التحميل…
Greška|Error|Fehler|Pogreška|=|Грешка|Eroare|Hata|Errore|خطأ
Pokreni|Play|Abspielen|=|Pusti|Пусни|Redă|Oynat|Riproduci|تشغيل
Titlovi|Subtitles|Untertitel|=|=|Субтитри|Subtitrări|Altyazılar|Sottotitoli|الترجمة
Audio|Audio|Audio|=|=|Аудио|Audio|Ses|Audio|الصوت
Trailer|Trailer|Trailer|=|=|Трейлър|Trailer|Fragman|Trailer|العرض الدعائي
Eksterni player|External player|Externer Player|Vanjski player|Eksterni plejer|Външен плейър|Player extern|Harici oynatıcı|Player esterno|مشغل خارجي
FILM|MOVIE|FILM|=|=|ФИЛМ|FILM|FİLM|FILM|فيلم
SERIJA|SERIES|SERIE|=|=|СЕРИАЛ|SERIAL|DİZİ|SERIE|مسلسل
Favoriti|Favourites|Favoriten|=|Omiljeni|Любими|Favorite|Favoriler|Preferiti|المفضلة
★ Favoriti|★ Favourites|★ Favoriten|=|★ Omiljeni|★ Любими|★ Favorite|★ Favoriler|★ Preferiti|★ المفضلة
Nova grupa|New group|Neue Gruppe|=|=|Нова група|Grup nou|Yeni grup|Nuovo gruppo|مجموعة جديدة
Nova grupa…|New group…|Neue Gruppe…|=|=|Нова група…|Grup nou…|Yeni grup…|Nuovo gruppo…|مجموعة جديدة…
Izaberi grupu|Choose a group|Gruppe wählen|Odaberi grupu|=|Избери група|Alege un grup|Bir grup seç|Scegli un gruppo|اختر مجموعة
Učitavam grupe…|Loading groups…|Gruppen werden geladen…|=|=|Зареждане на групи…|Se încarcă grupurile…|Gruplar yükleniyor…|Caricamento gruppi…|جارٍ تحميل المجموعات…
Učitavam kanale…|Loading channels…|Sender werden geladen…|=|=|Зареждане на канали…|Se încarcă canalele…|Kanallar yükleniyor…|Caricamento canali…|جارٍ تحميل القنوات…
Kanali se nisu učitali|Channels did not load|Sender nicht geladen|=|=|Каналите не се заредиха|Canalele nu s-au încărcat|Kanallar yüklenemedi|Canali non caricati|لم يتم تحميل القنوات
Grupa je prazna|Group is empty|Gruppe ist leer|=|=|Групата е празна|Grupul este gol|Grup boş|Il gruppo è vuoto|المجموعة فارغة
Nema favorita|No favourites|Keine Favoriten|=|Nema omiljenih|Няма любими|Nicio favorită|Favori yok|Nessun preferito|لا توجد مفضلة
OK gledaj · drži OK = opcije|OK watch · hold OK = options|OK ansehen · OK halten = Optionen|=|OK gledaj · drži OK = opcije|OK гледай · задръж OK = опции|OK vizionează · ține OK = opțiuni|OK izle · OK basılı = seçenekler|OK guarda · tieni OK = opzioni|OK للمشاهدة · اضغط مطولاً = خيارات
Pomjeri gore|Move up|Nach oben|Pomakni gore|Pomeri gore|Премести нагоре|Mută sus|Yukarı taşı|Sposta su|نقل لأعلى
Pomjeri dolje|Move down|Nach unten|Pomakni dolje|Pomeri dole|Премести надолу|Mută jos|Aşağı taşı|Sposta giù|نقل لأسفل
Preimenuj|Rename|Umbenennen|=|=|Преименувай|Redenumește|Yeniden adlandır|Rinomina|إعادة تسمية
Preimenuj kanal|Rename channel|Sender umbenennen|=|=|Преименувай канал|Redenumește canalul|Kanalı yeniden adlandır|Rinomina canale|إعادة تسمية القناة
Preimenuj grupu|Rename group|Gruppe umbenennen|=|=|Преименувай група|Redenumește grupul|Grubu yeniden adlandır|Rinomina gruppo|إعادة تسمية المجموعة
Novi naziv|New name|Neuer Name|=|=|Ново име|Nume nou|Yeni ad|Nuovo nome|اسم جديد
Sakrij / izbriši grupu|Hide / delete group|Gruppe ausblenden / löschen|=|=|Скрий / изтрий група|Ascunde / șterge grupul|Grubu gizle / sil|Nascondi / elimina gruppo|إخفاء / حذف المجموعة
Sakrij grupu|Hide group|Gruppe ausblenden|=|=|Скрий група|Ascunde grupul|Grubu gizle|Nascondi gruppo|إخفاء المجموعة
Prikaži grupu|Show group|Gruppe anzeigen|=|=|Покажи група|Arată grupul|Grubu göster|Mostra gruppo|إظهار المجموعة
Obriši grupu|Delete group|Gruppe löschen|=|=|Изтрий група|Șterge grupul|Grubu sil|Elimina gruppo|حذف المجموعة
Zaključaj PIN-om (4 broja)|Lock with PIN (4 digits)|Mit PIN sperren (4 Ziffern)|=|=|Заключи с PIN (4 цифри)|Blochează cu PIN (4 cifre)|PIN ile kilitle (4 hane)|Blocca con PIN (4 cifre)|قفل برمز PIN (4 أرقام)
Otključaj (ukloni PIN)|Unlock (remove PIN)|Entsperren (PIN entfernen)|=|=|Отключи (премахни PIN)|Deblochează (elimină PIN)|Kilidi aç (PIN kaldır)|Sblocca (rimuovi PIN)|إلغاء القفل
Za kanale koje djeca ne smiju gledati|For channels children must not watch|Für Sender, die Kinder nicht sehen dürfen|=|Za kanale koje deca ne smeju da gledaju|За канали, които децата не бива да гледат|Pentru canale interzise copiilor|Çocukların izlememesi gereken kanallar için|Per canali vietati ai bambini|للقنوات الممنوعة على الأطفال
Dodaj u favorite|Add to favourites|Zu Favoriten|=|Dodaj u omiljene|Добави в любими|Adaugă la favorite|Favorilere ekle|Aggiungi ai preferiti|إضافة إلى المفضلة
Ukloni iz favorita|Remove from favourites|Aus Favoriten entfernen|=|Ukloni iz omiljenih|Премахни от любими|Elimină din favorite|Favorilerden çıkar|Rimuovi dai preferiti|إزالة من المفضلة
Dodano u favorite|Added to favourites|Zu Favoriten hinzugefügt|=|Dodato u omiljene|Добавено в любими|Adăugat la favorite|Favorilere eklendi|Aggiunto ai preferiti|أضيف إلى المفضلة
Uklonjeno iz favorita|Removed from favourites|Aus Favoriten entfernt|=|Uklonjeno iz omiljenih|Премахнато от любими|Eliminat din favorite|Favorilerden çıkarıldı|Rimosso dai preferiti|أزيل من المفضلة
Premjesti u drugu grupu|Move to another group|In andere Gruppe verschieben|Premjesti u drugu grupu|Premesti u drugu grupu|Премести в друга група|Mută în alt grup|Başka gruba taşı|Sposta in un altro gruppo|نقل إلى مجموعة أخرى
Premjesti u grupu|Move to group|In Gruppe verschieben|=|Premesti u grupu|Премести в група|Mută în grup|Gruba taşı|Sposta nel gruppo|نقل إلى مجموعة
Sakrij kanal|Hide channel|Sender ausblenden|=|=|Скрий канал|Ascunde canalul|Kanalı gizle|Nascondi canale|إخفاء القناة
Prikaži kanal|Show channel|Sender anzeigen|=|=|Покажи канал|Arată canalul|Kanalı göster|Mostra canale|إظهار القناة
Otključaj kanal|Unlock channel|Sender entsperren|=|=|Отключи канал|Deblochează canalul|Kanal kilidini aç|Sblocca canale|إلغاء قفل القناة
Izbriši kanal|Delete channel|Sender löschen|=|=|Изтрий канал|Șterge canalul|Kanalı sil|Elimina canale|حذف القناة
Izbrisati kanal?|Delete channel?|Sender löschen?|=|=|Да се изтрие ли каналът?|Ștergi canalul?|Kanal silinsin mi?|Eliminare il canale?|حذف القناة؟
Da, izbriši iz liste|Yes, remove from list|Ja, aus Liste entfernen|=|=|Да, премахни от списъка|Da, elimină din listă|Evet, listeden sil|Sì, rimuovi dalla lista|نعم، احذف من القائمة
Informacije o kanalu|Channel info|Senderinfo|=|=|Информация за канала|Informații canal|Kanal bilgisi|Info canale|معلومات القناة
Nema EPG podataka za ovaj kanal.|No EPG data for this channel.|Keine EPG-Daten für diesen Sender.|=|=|Няма EPG данни за канала.|Nu există date EPG.|Bu kanal için EPG yok.|Nessun dato EPG per questo canale.|لا توجد بيانات دليل لهذه القناة.
Učitavam program…|Loading guide…|Programm wird geladen…|=|=|Зареждане на програмата…|Se încarcă ghidul…|Rehber yükleniyor…|Caricamento guida…|جارٍ تحميل الدليل…
Program nije dostupan|Guide not available|Programm nicht verfügbar|=|=|Програмата не е налична|Ghid indisponibil|Rehber yok|Guida non disponibile|الدليل غير متاح
Stani na kanal da vidiš program|Stop on a channel to see its guide|Auf einem Sender stehen für das Programm|=|=|Спри на канал за програмата|Oprește-te pe un canal pentru ghid|Rehber için kanalda dur|Fermati su un canale per la guida|قف على قناة لرؤية الدليل
SADA|NOW|JETZT|=|=|СЕГА|ACUM|ŞİMDİ|ORA|الآن
UŽIVO|LIVE|LIVE|=|=|НА ЖИВО|LIVE|CANLI|LIVE|مباشر
Audio staze|Audio tracks|Tonspuren|=|=|Аудио писти|Piste audio|Ses parçaları|Tracce audio|المسارات الصوتية
Film ima samo jednu audio stazu|This video has only one audio track|Dieses Video hat nur eine Tonspur|=|=|Видеото има само една аудио писта|Video are o singură pistă audio|Bu videoda tek ses parçası var|Il video ha una sola traccia audio|يحتوي الفيديو على مسار صوتي واحد
Provjeravam audio staze…|Checking audio tracks…|Tonspuren werden geprüft…|=|Proveravam audio staze…|Проверка на аудио пистите…|Se verifică pistele audio…|Ses parçaları kontrol ediliyor…|Verifica tracce audio…|جارٍ فحص المسارات الصوتية…
Bez titla|No subtitles|Keine Untertitel|=|=|Без субтитри|Fără subtitrare|Altyazı yok|Nessun sottotitolo|بدون ترجمة
Isključeni|Off|Aus|=|=|Изключени|Oprite|Kapalı|Disattivati|متوقفة
Ugrađen u video|Built into the video|Im Video enthalten|=|=|Вграден във видеото|Inclus în video|Videoya gömülü|Incorporato nel video|مدمج في الفيديو
Ugrađeni titlovi|Built-in subtitles|Eingebettete Untertitel|=|=|Вградени субтитри|Subtitrări incluse|Gömülü altyazılar|Sottotitoli incorporati|ترجمة مدمجة
Traži online|Search online|Online suchen|=|=|Търси онлайн|Caută online|Çevrimiçi ara|Cerca online|البحث على الإنترنت
S linka (internet)|From a link (internet)|Von Link (Internet)|=|=|От линк (интернет)|De la link (internet)|Bağlantıdan (internet)|Da link (internet)|من رابط (إنترنت)
Sa diska ili USB-a|From disk or USB|Von Speicher oder USB|=|=|От диск или USB|De pe disc sau USB|Diskten veya USB'den|Da disco o USB|من القرص أو USB
Jezik titla|Subtitle language|Untertitelsprache|=|=|Език на субтитрите|Limba subtitrării|Altyazı dili|Lingua sottotitoli|لغة الترجمة
Jezici titla|Subtitle languages|Untertitelsprachen|=|=|Езици на субтитрите|Limbi subtitrare|Altyazı dilleri|Lingue sottotitoli|لغات الترجمة
Jezici zvuka|Audio languages|Audiosprachen|=|=|Езици на звука|Limbi audio|Ses dilleri|Lingue audio|لغات الصوت
Titlovi online|Online subtitles|Online-Untertitel|=|=|Онлайн субтитри|Subtitrări online|Çevrimiçi altyazılar|Sottotitoli online|ترجمات عبر الإنترنت
Titl učitan|Subtitle loaded|Untertitel geladen|=|=|Субтитрите са заредени|Subtitrare încărcată|Altyazı yüklendi|Sottotitolo caricato|تم تحميل الترجمة
Tražim…|Searching…|Suche…|=|=|Търсене…|Se caută…|Aranıyor…|Ricerca…|جارٍ البحث…
Automatski|Automatic|Automatisch|=|=|Автоматично|Automat|Otomatik|Automatico|تلقائي
Format slike|Picture format|Bildformat|=|=|Формат на картината|Format imagine|Görüntü formatı|Formato immagine|تنسيق الصورة
Prilagodi|Fit|Anpassen|=|=|Побери|Potrivește|Sığdır|Adatta|ملاءمة
Zumiraj|Zoom|Zoomen|=|=|Увеличи|Mărește|Yakınlaştır|Zoom|تكبير
Rastegni|Stretch|Strecken|=|=|Разтегни|Întinde|Uzat|Allunga|تمديد
Kvalitet i informacije|Quality and info|Qualität und Info|Kvaliteta i informacije|=|Качество и инфо|Calitate și informații|Kalite ve bilgi|Qualità e info|الجودة والمعلومات
Slika|Picture|Bild|=|=|Картина|Imagine|Görüntü|Immagine|الصورة
Zvuk|Sound|Ton|=|=|Звук|Sunet|Ses|Audio|الصوت
Video kodek|Video codec|Videocodec|=|=|Видео кодек|Codec video|Video kodeği|Codec video|ترميز الفيديو
Plejer|Player|Player|Player|=|Плейър|Player|Oynatıcı|Player|المشغل
Premotavanje lijevo/desno|Skip left/right|Spulen links/rechts|Premotavanje lijevo/desno|Premotavanje levo/desno|Превъртане ляво/дясно|Derulare stânga/dreapta|Sol/sağ atlama|Salto sinistra/destra|التخطي يسارًا/يمينًا
Automatski uključi titl|Turn subtitles on automatically|Untertitel automatisch einschalten|=|=|Автоматично включвай субтитри|Pornește subtitrarea automat|Altyazıyı otomatik aç|Attiva sottotitoli automaticamente|تشغيل الترجمة تلقائيًا
Podaci o filmovima|Movie data|Filmdaten|=|=|Данни за филми|Date despre filme|Film verileri|Dati dei film|بيانات الأفلام
Jezik opisa filmova|Movie description language|Sprache der Filmbeschreibung|=|=|Език на описанията|Limba descrierilor|Film açıklaması dili|Lingua delle descrizioni|لغة وصف الأفلام
Kao jezik aplikacije|Same as app language|Wie App-Sprache|=|=|Като езика на приложението|Ca limba aplicației|Uygulama diliyle aynı|Come la lingua dell'app|مثل لغة التطبيق
Ključevi su besplatni i čuvaju se samo na ovom uređaju.|Keys are free and stored only on this device.|Schlüssel sind kostenlos und nur auf diesem Gerät gespeichert.|=|Ključevi su besplatni i čuvaju se samo na ovom uređaju.|Ключовете са безплатни и се пазят само на това устройство.|Cheile sunt gratuite și stocate doar pe acest dispozitiv.|Anahtarlar ücretsizdir ve yalnızca bu cihazda saklanır.|Le chiavi sono gratuite e restano su questo dispositivo.|المفاتيح مجانية وتُحفظ على هذا الجهاز فقط.
Nije upisan|Not set|Nicht eingetragen|=|Nije upisan|Не е въведен|Nesetat|Girilmedi|Non impostato|غير مُدخل
Tema|Theme|Design|=|=|Тема|Temă|Tema|Tema|السمة
Tamna|Dark|Dunkel|=|=|Тъмна|Întunecată|Koyu|Scuro|داكن
Svijetla|Light|Hell|=|Svetla|Светла|Luminoasă|Açık|Chiaro|فاتح
Crno-bijela|Black & white|Schwarz-weiß|=|Crno-bela|Черно-бяла|Alb-negru|Siyah-beyaz|Bianco e nero|أبيض وأسود
Akcentna boja|Accent colour|Akzentfarbe|Naglasna boja|=|Акцентен цвят|Culoare de accent|Vurgu rengi|Colore d'accento|لون التمييز
Nijansa boje|Colour shade|Farbton|=|=|Нюанс|Nuanță|Renk tonu|Tonalità|درجة اللون
Tamnija|Darker|Dunkler|=|=|По-тъмен|Mai închis|Daha koyu|Più scuro|أغمق
Malo tamnija|Slightly darker|Etwas dunkler|=|=|Малко по-тъмен|Puțin mai închis|Biraz koyu|Un po' più scuro|أغمق قليلًا
Normalna|Normal|Normal|=|=|Нормален|Normal|Normal|Normale|عادي
Malo svjetlija|Slightly lighter|Etwas heller|=|Malo svetlija|Малко по-светъл|Puțin mai deschis|Biraz açık|Un po' più chiaro|أفتح قليلًا
Svjetlija|Lighter|Heller|=|Svetlija|По-светъл|Mai deschis|Daha açık|Più chiaro|أفتح
Providnost menija|Menu transparency|Menü-Transparenz|Prozirnost izbornika|Providnost menija|Прозрачност на менютата|Transparența meniului|Menü saydamlığı|Trasparenza menu|شفافية القوائم
Veličina redova (kanali i grupe)|Row size (channels and groups)|Zeilengröße (Sender und Gruppen)|=|=|Размер на редовете|Mărimea rândurilor|Satır boyutu|Dimensione righe|حجم الصفوف
Mala|Small|Klein|=|=|Малък|Mic|Küçük|Piccola|صغير
Srednja|Medium|Mittel|=|=|Среден|Mediu|Orta|Media|متوسط
Velika|Large|Groß|=|=|Голям|Mare|Büyük|Grande|كبير
Male|Small|Klein|=|=|Малки|Mici|Küçük|Piccole|صغيرة
Srednje|Medium|Mittel|=|=|Средни|Medii|Orta|Medie|متوسطة
Velike|Large|Groß|=|=|Големи|Mari|Büyük|Grandi|كبيرة
Mali|Small|Klein|=|=|Малки|Mici|Küçük|Piccoli|صغيرة
Srednji|Medium|Mittel|=|=|Средни|Medii|Orta|Medi|متوسطة
Veliki|Large|Groß|=|=|Големи|Mari|Büyük|Grandi|كبيرة
Pločice na Početnoj|Home tiles|Kacheln auf der Startseite|=|=|Плочки на началото|Plăci pe ecranul principal|Ana sayfa kutuları|Riquadri della Home|مربعات الرئيسية
Posteri filmova i serija|Movie and series posters|Film- und Serienposter|=|=|Постери|Postere|Afişler|Locandine|ملصقات الأفلام والمسلسلات
Meniji i info traka preko slike|Menus and info bar over the picture|Menüs und Infoleiste über dem Bild|=|=|Менюта и инфо лента|Meniuri și bara de info|Menüler ve bilgi çubuğu|Menu e barra info|القوائم وشريط المعلومات
Stakleno (prozirno)|Glass (see-through)|Glas (durchsichtig)|=|=|Стъкло (прозрачно)|Sticlă (transparent)|Cam (saydam)|Vetro (trasparente)|زجاجي (شفاف)
Crno|Black|Schwarz|=|=|Черно|Negru|Siyah|Nero|أسود
Puna boja|Solid colour|Vollfarbe|=|=|Плътен цвят|Culoare plină|Düz renk|Colore pieno|لون كامل
Obris|Outline|Umriss|=|=|Контур|Contur|Çerçeve|Contorno|إطار
Kanali|Channels|Sender|=|=|Канали|Canale|Kanallar|Canali|القنوات
Pregled kanala uživo|Live channel preview|Live-Vorschau|=|=|Преглед на живо|Previzualizare live|Canlı önizleme|Anteprima dal vivo|معاينة مباشرة
Najgledaniji na vrhu grupe|Most watched on top|Meistgesehene oben|=|=|Най-гледаните най-горе|Cele mai vizionate sus|En çok izlenenler üstte|Più visti in alto|الأكثر مشاهدة في الأعلى
Promjene se vide odmah.|Changes show right away.|Änderungen sind sofort sichtbar.|=|Promene se vide odmah.|Промените се виждат веднага.|Modificările apar imediat.|Değişiklikler hemen görünür.|Le modifiche sono immediate.|تظهر التغييرات فورًا.
Dodaj playlistu|Add playlist|Playlist hinzufügen|=|Dodaj plejlistu|Добави плейлист|Adaugă playlist|Liste ekle|Aggiungi playlist|إضافة قائمة
Ažuriraj sve|Update all|Alle aktualisieren|=|=|Обнови всички|Actualizează tot|Tümünü güncelle|Aggiorna tutto|تحديث الكل
AKTIVNA|ACTIVE|AKTIV|=|=|АКТИВЕН|ACTIV|AKTİF|ATTIVA|نشطة
Ažuriraj|Update|Aktualisieren|=|=|Обнови|Actualizează|Güncelle|Aggiorna|تحديث
Ažuriraj sada|Update now|Jetzt aktualisieren|=|=|Обнови сега|Actualizează acum|Şimdi güncelle|Aggiorna ora|حدّث الآن
Provjeri ima li nova verzija|Check for a new version|Nach neuer Version suchen|=|Proveri da li ima nove verzije|Провери за нова версия|Caută versiune nouă|Yeni sürümü kontrol et|Cerca nuova versione|التحقق من وجود إصدار جديد
Instaliram…|Installing…|Installiere…|=|=|Инсталиране…|Se instalează…|Yükleniyor…|Installazione…|جارٍ التثبيت…
Napredni mod|Advanced mode|Erweiterter Modus|=|Napredni režim|Разширен режим|Mod avansat|Gelişmiş mod|Modalità avanzata|الوضع المتقدم
Uključi jednostavni mod|Turn on simple mode|Einfachen Modus einschalten|=|Uključi jednostavni režim|Включи опростен режим|Pornește modul simplu|Basit modu aç|Attiva modalità semplice|تشغيل الوضع البسيط
Postavi PIN|Set PIN|PIN festlegen|=|=|Задай PIN|Setează PIN|PIN belirle|Imposta PIN|تعيين PIN
Resetuj PIN|Reset PIN|PIN zurücksetzen|=|=|Нулирай PIN|Resetează PIN|PIN sıfırla|Reimposta PIN|إعادة تعيين PIN
Novi PIN|New PIN|Neue PIN|=|=|Нов PIN|PIN nou|Yeni PIN|Nuovo PIN|PIN جديد
Ponovi PIN|Repeat PIN|PIN wiederholen|=|=|Повтори PIN|Repetă PIN|PIN'i tekrarla|Ripeti PIN|أعد إدخال PIN
Zaključano|Locked|Gesperrt|=|=|Заключено|Blocat|Kilitli|Bloccato|مقفل
Unesi roditeljski PIN|Enter the parental PIN|Eltern-PIN eingeben|=|=|Въведи родителски PIN|Introdu PIN-ul parental|Ebeveyn PIN'ini gir|Inserisci il PIN genitori|أدخل رمز PIN
Vrati sakrivene kanale|Restore hidden channels|Ausgeblendete Sender zurück|=|=|Върни скритите канали|Readu canalele ascunse|Gizli kanalları geri getir|Ripristina canali nascosti|استعادة القنوات المخفية
Vrati sve izmjene|Undo all changes|Alle Änderungen zurücksetzen|=|Vrati sve izmene|Отмени всички промени|Anulează toate modificările|Tüm değişiklikleri geri al|Annulla tutte le modifiche|التراجع عن كل التغييرات
Izvorni kod|Source code|Quellcode|=|=|Изходен код|Cod sursă|Kaynak kod|Codice sorgente|الشيفرة المصدرية
Ikone i font|Icons and font|Symbole und Schrift|=|=|Икони и шрифт|Pictograme și font|Simgeler ve yazı tipi|Icone e font|الأيقونات والخط
IPTV player za Android TV|IPTV player for Android TV|IPTV-Player für Android TV|=|IPTV plejer za Android TV|IPTV плейър за Android TV|Player IPTV pentru Android TV|Android TV için IPTV oynatıcı|Player IPTV per Android TV|مشغل IPTV لأجهزة Android TV
Sports Center|Sports Center|Sports Center|=|=|Спортен център|Sports Center|Spor Merkezi|Sports Center|المركز الرياضي
Uživo sada|Live now|Jetzt live|=|=|На живо сега|Live acum|Şimdi canlı|In diretta ora|مباشر الآن
Kasnije|Later|Später|=|=|По-късно|Mai târziu|Daha sonra|Più tardi|لاحقًا
Završeno|Finished|Beendet|=|=|Завършили|Încheiate|Bitti|Terminate|انتهت
Lige|Leagues|Ligen|=|=|Лиги|Ligi|Ligler|Campionati|الدوريات
PRENOSE|ON AIR|ÜBERTRAGUNG|=|=|ПРЕДАВАТ|TRANSMIT|YAYINLAYAN|IN ONDA SU|تُبث على
Samo moji kanali|Only my channels|Nur meine Sender|=|=|Само моите канали|Doar canalele mele|Sadece kanallarım|Solo i miei canali|قنواتي فقط
Sve|All|Alle|=|=|Всички|Toate|Tümü|Tutti|الكل
Fudbal|Football|Fußball|Nogomet|=|Футбол|Fotbal|Futbol|Calcio|كرة القدم
Košarka|Basketball|Basketball|=|=|Баскетбол|Baschet|Basketbol|Basket|كرة السلة
Odbojka|Volleyball|Volleyball|=|=|Волейбол|Volei|Voleybol|Pallavolo|الكرة الطائرة
Formula 1|Formula 1|Formel 1|=|=|Формула 1|Formula 1|Formula 1|Formula 1|الفورمولا 1
Tenis|Tennis|Tennis|=|=|Тенис|Tenis|Tenis|Tennis|التنس
Rukomet|Handball|Handball|=|=|Хандбал|Handbal|Hentbol|Pallamano|كرة اليد
Ostalo|Other|Sonstiges|=|=|Други|Altele|Diğer|Altro|أخرى
Jučer|Yesterday|Gestern|=|Juče|Вчера|Ieri|Dün|Ieri|أمس
Danas|Today|Heute|=|=|Днес|Azi|Bugün|Oggi|اليوم
Sutra|Tomorrow|Morgen|=|=|Утре|Mâine|Yarın|Domani|غدًا
u tvojoj listi|in your list|in deiner Liste|=|=|в твоя списък|în lista ta|listende var|nella tua lista|في قائمتك
nije u tvojoj listi|not in your list|nicht in deiner Liste|=|=|не е в списъка|nu e în listă|listende yok|non nella tua lista|ليست في قائمتك
Nema utakmica za ovaj dan|No matches on this day|Keine Spiele an diesem Tag|=|=|Няма мачове за този ден|Niciun meci în această zi|Bu gün maç yok|Nessuna partita in questo giorno|لا توجد مباريات في هذا اليوم
Učitavam utakmice…|Loading matches…|Spiele werden geladen…|=|=|Зареждане на мачове…|Se încarcă meciurile…|Maçlar yükleniyor…|Caricamento partite…|جارٍ تحميل المباريات…
Tražim prenose u tvom TV programu…|Looking for broadcasts in your TV guide…|Suche Übertragungen in deinem TV-Programm…|=|=|Търся предавания в програмата…|Caut transmisiuni în ghidul tău…|TV rehberinde yayın aranıyor…|Cerco trasmissioni nella tua guida…|جارٍ البحث عن البث في دليلك…
Prenos nije pronađen u tvojoj listi|No broadcast found in your list|Keine Übertragung in deiner Liste|=|=|Няма предаване в списъка ти|Nicio transmisiune în lista ta|Listende yayın bulunamadı|Nessuna trasmissione nella tua lista|لم يُعثر على بث في قائمتك
Izaberi kanal|Choose a channel|Sender wählen|Odaberi kanal|=|Избери канал|Alege canalul|Kanal seç|Scegli il canale|اختر قناة
Na tvojim kanalima|On your channels|Auf deinen Sendern|=|=|По твоите канали|Pe canalele tale|Kanallarında|Sui tuoi canali|على قنواتك
Poluvrijeme|Half-time|Halbzeit|=|Poluvreme|Полувреме|Pauză|Devre arası|Intervallo|الاستراحة
Bosanski|Bosnian|Bosnisch|Bosanski|Bosanski|Босненски|Bosniacă|Boşnakça|Bosniaco|البوسنية
Hrvatski|Croatian|Kroatisch|=|=|Хърватски|Croată|Hırvatça|Croato|الكرواتية
Srpski|Serbian|Serbisch|=|=|Сръбски|Sârbă|Sırpça|Serbo|الصربية
Engleski|English|Englisch|=|=|Английски|Engleză|İngilizce|Inglese|الإنجليزية
Njemački|German|Deutsch|=|Nemački|Немски|Germană|Almanca|Tedesco|الألمانية
Turski|Turkish|Türkisch|=|=|Турски|Turcă|Türkçe|Turco|التركية
Italijanski|Italian|Italienisch|Talijanski|=|Италиански|Italiană|İtalyanca|Italiano|الإيطالية
Bugarski|Bulgarian|Bulgarisch|=|=|Български|Bulgară|Bulgarca|Bulgaro|البلغارية
Rumunski|Romanian|Rumänisch|=|=|Румънски|Română|Rumence|Rumeno|الرومانية
Arapski|Arabic|Arabisch|=|=|Арабски|Arabă|Arapça|Arabo|العربية
Francuski|French|Französisch|=|=|Френски|Franceză|Fransızca|Francese|الفرنسية
Španski|Spanish|Spanisch|Španjolski|=|Испански|Spaniolă|İspanyolca|Spagnolo|الإسبانية
Klikni jezike redom: prvi je najvažniji.|Click languages in order: the first is the most wanted.|Sprachen der Reihe nach anklicken: die erste ist die wichtigste.|=|=|Избери езиците подред: първият е най-важен.|Alege limbile în ordine: prima e cea mai dorită.|Dilleri sırayla seç: ilki en önemlisidir.|Scegli le lingue in ordine: la prima è la preferita.|اختر اللغات بالترتيب: الأولى هي الأهم.
Nepoznat jezik|Unknown language|Unbekannte Sprache|=|=|Неизвестен език|Limbă necunoscută|Bilinmeyen dil|Lingua sconosciuta|لغة غير معروفة
""".trimIndent()

private val DICT: Map<String, Map<String, String>> by lazy {
    val cols = COLS.split('|')
    val out = HashMap<String, HashMap<String, String>>()
    TABLE.lineSequence().filter { it.isNotBlank() }.forEach { line ->
        val parts = line.split('|')
        if (parts.size != cols.size) return@forEach
        val bs = parts[0]
        for (i in 1 until cols.size) {
            val v = parts[i].trim()
            out.getOrPut(cols[i]) { HashMap() }[bs] = if (v == "=" || v.isEmpty()) bs else v
        }
    }
    out
}

/** [s] in the app language (or as is, when unknown). */
fun tr(s: String): String {
    val l = Look.lang
    if (l == "bs" || s.isEmpty()) return s
    return DICT[l]?.get(s) ?: s
}

/** Description language for TMDB: the chosen one, or the app language. */
fun metaLanguage(): String = Prefs.metaLang.ifBlank { "auto" }.let { if (it == "auto") Look.lang else it }
