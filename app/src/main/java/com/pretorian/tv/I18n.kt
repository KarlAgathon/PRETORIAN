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
.srt fajl|.srt file|.srt-Datei|=|=|.srt файл|fișier .srt|.srt dosyası|file .srt|ملف .srt
Grupa je već prva|The group is already first|Die Gruppe ist schon die erste|=|=|Групата вече е първа|Grupul este deja primul|Grup zaten ilk sırada|Il gruppo è già il primo|المجموعة في الأعلى بالفعل
Grupa je već zadnja|The group is already last|Die Gruppe ist schon die letzte|=|=|Групата вече е последна|Grupul este deja ultimul|Grup zaten son sırada|Il gruppo è già l'ultimo|المجموعة في الأسفل بالفعل
Drži OK na grupi za opcije|Hold OK on a group for options|OK auf einer Gruppe halten für Optionen|=|=|Задръж OK върху група за опции|Ține OK pe un grup pentru opțiuni|Seçenekler için grupta OK'u basılı tut|Tieni OK su un gruppo per le opzioni|اضغط مطولاً على مجموعة للخيارات
Nema aktivne playliste|No active playlist|Keine aktive Playlist|=|Nema aktivne plejliste|Няма активен плейлист|Niciun playlist activ|Etkin liste yok|Nessuna playlist attiva|لا توجد قائمة نشطة
Nema playliste|No playlist|Keine Playlist|=|Nema plejliste|Няма плейлист|Niciun playlist|Liste yok|Nessuna playlist|لا توجد قائمة
Obriši playlistu|Delete playlist|Playlist löschen|=|Obriši plejlistu|Изтрий плейлист|Șterge playlistul|Listeyi sil|Elimina playlist|حذف القائمة
Učitavam opis…|Loading description…|Beschreibung wird geladen…|=|=|Зареждане на описанието…|Se încarcă descrierea…|Açıklama yükleniyor…|Caricamento descrizione…|جارٍ تحميل الوصف…
Učitavam epizode…|Loading episodes…|Folgen werden geladen…|=|=|Зареждане на епизоди…|Se încarcă episoadele…|Bölümler yükleniyor…|Caricamento episodi…|جارٍ تحميل الحلقات…
Učitavam kategorije…|Loading categories…|Kategorien werden geladen…|=|=|Зареждане на категории…|Se încarcă categoriile…|Kategoriler yükleniyor…|Caricamento categorie…|جارٍ تحميل الفئات…
Izaberi kategoriju|Choose a category|Kategorie wählen|Odaberi kategoriju|=|Избери категория|Alege o categorie|Bir kategori seç|Scegli una categoria|اختر فئة
Kategorija je prazna|Category is empty|Kategorie ist leer|=|=|Категорията е празна|Categoria este goală|Kategori boş|La categoria è vuota|الفئة فارغة
Nema epizoda|No episodes|Keine Folgen|=|=|Няма епизоди|Niciun episod|Bölüm yok|Nessun episodio|لا توجد حلقات
Epizode se nisu učitale|Episodes did not load|Folgen nicht geladen|=|=|Епизодите не се заредиха|Episoadele nu s-au încărcat|Bölümler yüklenemedi|Episodi non caricati|لم يتم تحميل الحلقات
Nije se učitalo|Did not load|Nicht geladen|=|=|Не се зареди|Nu s-a încărcat|Yüklenemedi|Non caricato|لم يتم التحميل
Titl|Subtitle|Untertitel|=|=|Субтитри|Subtitrare|Altyazı|Sottotitolo|الترجمة
Titl s linka|Subtitle from a link|Untertitel von Link|=|=|Субтитри от линк|Subtitrare de la link|Bağlantıdan altyazı|Sottotitolo da link|ترجمة من رابط
Titl se nije skinuo|Subtitle did not download|Untertitel nicht geladen|=|=|Субтитрите не се изтеглиха|Subtitrarea nu s-a descărcat|Altyazı indirilemedi|Sottotitolo non scaricato|لم يتم تنزيل الترجمة
Titlovi koji dolaze s filmom|Subtitles that come with the movie|Untertitel aus dem Film|=|=|Субтитри от филма|Subtitrări din film|Filmle gelen altyazılar|Sottotitoli inclusi nel film|ترجمات مرفقة بالفيلم
Nijedan titl, ni ugrađeni|No subtitle at all, not even built-in|Gar keine Untertitel, auch keine eingebetteten|=|=|Никакви субтитри, и вградените|Nicio subtitrare, nici cea inclusă|Hiç altyazı yok, gömülü de|Nessun sottotitolo, nemmeno incorporato|بلا أي ترجمة، ولا المدمجة
Ovaj video nema ugrađenih titlova.|This video has no built-in subtitles.|Dieses Video hat keine eingebetteten Untertitel.|=|=|Видеото няма вградени субтитри.|Videoclipul nu are subtitrări incluse.|Bu videoda gömülü altyazı yok.|Questo video non ha sottotitoli incorporati.|لا يحتوي الفيديو على ترجمة مدمجة.
Zalijepi link do .srt ili .zip|Paste a link to .srt or .zip|Link zu .srt oder .zip einfügen|=|=|Постави линк към .srt или .zip|Lipește un link .srt sau .zip|.srt veya .zip bağlantısı yapıştır|Incolla un link .srt o .zip|الصق رابط .srt أو .zip
Nema titlova za|No subtitles for|Keine Untertitel für|=|=|Няма субтитри за|Nu există subtitrări pentru|Altyazı yok:|Nessun sottotitolo per|لا توجد ترجمة لـ
Preuzimanje nije uspjelo|Download failed|Download fehlgeschlagen|=|=|Изтеглянето не успя|Descărcarea a eșuat|İndirme başarısız|Download non riuscito|فشل التنزيل
Fajl se ne može pročitati|The file cannot be read|Datei kann nicht gelesen werden|Datoteka se ne može pročitati|=|Файлът не може да се прочете|Fișierul nu poate fi citit|Dosya okunamıyor|Impossibile leggere il file|لا يمكن قراءة الملف
Na ovom uređaju nema upravitelja fajlova|This device has no file manager|Auf diesem Gerät gibt es keinen Dateimanager|=|=|Няма файлов мениджър|Dispozitivul nu are manager de fișiere|Bu cihazda dosya yöneticisi yok|Nessun file manager su questo dispositivo|لا يوجد مدير ملفات على الجهاز
Staze se nisu mogle pročitati. Izaberi audio u plejeru: strelica dole › Audio.|Tracks could not be read. Choose audio in the player: Down › Audio.|Spuren nicht lesbar. Audio im Player wählen: Runter › Audio.|=|Staze se nisu mogle pročitati. Izaberi audio u plejeru: strelica dole › Audio.|Пистите не се прочетоха. Избери аудио в плейъра: надолу › Аудио.|Pistele nu au putut fi citite. Alege audio în player: jos › Audio.|Parçalar okunamadı. Sesi oynatıcıda seç: Aşağı › Ses.|Tracce non lette. Scegli l'audio nel player: giù › Audio.|تعذر قراءة المسارات. اختر الصوت في المشغل: لأسفل › الصوت.
Ugrađeni titl na tvom jeziku se sam uključi kad film krene|A built-in subtitle in your language turns on when the movie starts|Eingebettete Untertitel in deiner Sprache starten automatisch|=|=|Вградените субтитри на твоя език се включват сами|Subtitrarea inclusă în limba ta pornește singură|Dilindeki gömülü altyazı kendiliğinden açılır|I sottotitoli incorporati nella tua lingua si attivano da soli|تعمل الترجمة المدمجة بلغتك تلقائيًا
Ako opisa nema na tom jeziku, prikazuje se engleski|If there is no description in that language, English is shown|Gibt es keine Beschreibung in dieser Sprache, wird Englisch gezeigt|=|=|Ако няма описание на този език, се показва английски|Dacă nu există descriere în limba aleasă, se arată engleza|O dilde açıklama yoksa İngilizce gösterilir|Se manca la descrizione in quella lingua, si mostra l'inglese|إذا لم يتوفر وصف بهذه اللغة يُعرض بالإنجليزية
0 % = puni meni, ništa se ne vidi kroz njega. Najviše 40 %.|0 % = solid menu, nothing shows through. At most 40 %.|0 % = volles Menü, nichts scheint durch. Höchstens 40 %.|=|=|0 % = плътно меню. Най-много 40 %.|0 % = meniu opac. Maximum 40 %.|0 % = tam opak menü. En fazla 40 %.|0 % = menu pieno. Al massimo 40 %.|0 % = قائمة معتمة. الحد الأقصى 40 %.
Najviše se vidi u temi Glass.|Most visible in the Glass theme.|Am deutlichsten im Glass-Design.|=|=|Най-видимо в темата Glass.|Cel mai vizibil în tema Glass.|En çok Glass temasında görünür.|Più visibile nel tema Glass.|يظهر أكثر في سمة Glass.
TV program (EPG)|TV guide (EPG)|TV-Programm (EPG)|=|=|ТВ програма (EPG)|Ghid TV (EPG)|TV rehberi (EPG)|Guida TV (EPG)|دليل البرامج (EPG)
Vlastiti EPG izvor (XMLTV link)|Own EPG source (XMLTV link)|Eigene EPG-Quelle (XMLTV-Link)|=|=|Собствен EPG източник (XMLTV)|Sursă EPG proprie (link XMLTV)|Kendi EPG kaynağın (XMLTV)|Fonte EPG propria (link XMLTV)|مصدر EPG خاص (رابط XMLTV)
Vlastiti EPG izvor|Own EPG source|Eigene EPG-Quelle|=|=|Собствен EPG източник|Sursă EPG proprie|Kendi EPG kaynağın|Fonte EPG propria|مصدر EPG خاص
Koristi se kad provider za kanal ne šalje program. Link do .xml ili .xml.gz|Used when the provider sends no guide for a channel. Link to .xml or .xml.gz|Wird genutzt, wenn der Anbieter kein Programm liefert. Link zu .xml oder .xml.gz|=|=|Ползва се, когато доставчикът няма програма. Линк към .xml или .xml.gz|Folosit când furnizorul nu trimite ghid. Link .xml sau .xml.gz|Sağlayıcı rehber göndermezse kullanılır. .xml veya .xml.gz bağlantısı|Usata quando il provider non invia la guida. Link .xml o .xml.gz|يُستخدم عندما لا يرسل المزود الدليل. رابط .xml أو .xml.gz
Dodaj vlastiti EPG: Postavke › Playliste › Vlastiti EPG izvor|Add your own EPG: Settings › Playlists › Own EPG source|Eigenes EPG: Einstellungen › Playlists › Eigene EPG-Quelle|=|Dodaj vlastiti EPG: Podešavanja › Plejliste › Vlastiti EPG izvor|Добави EPG: Настройки › Плейлисти › Собствен EPG|Adaugă EPG: Setări › Playlisturi › Sursă EPG proprie|EPG ekle: Ayarlar › Listeler › Kendi EPG kaynağın|Aggiungi EPG: Impostazioni › Playlist › Fonte EPG propria|أضف EPG: الإعدادات › قوائم التشغيل › مصدر EPG خاص
Ni tvoj EPG izvor nema ovaj kanal.|Your EPG source does not have this channel either.|Auch deine EPG-Quelle hat diesen Sender nicht.|=|=|И твоят EPG няма този канал.|Nici sursa ta EPG nu are canalul.|Kendi EPG kaynağında da bu kanal yok.|Neanche la tua fonte EPG ha questo canale.|مصدر EPG الخاص بك لا يحتوي هذه القناة أيضًا.
Rezultati: ESPN. Prenosi: TV program tvoje playliste.|Scores: ESPN. Broadcasts: your playlist's TV guide.|Ergebnisse: ESPN. Übertragungen: TV-Programm deiner Playlist.|=|Rezultati: ESPN. Prenosi: TV program tvoje plejliste.|Резултати: ESPN. Предавания: програмата на плейлиста.|Scoruri: ESPN. Transmisiuni: ghidul playlistului tău.|Skorlar: ESPN. Yayınlar: listenin TV rehberi.|Risultati: ESPN. Trasmissioni: guida TV della tua playlist.|النتائج: ESPN. البث: دليل قائمتك.
Za postere, opis na tvom jeziku i glumce upiši TMDB ključ: Postavke › Filmovi i titlovi|For posters, descriptions in your language and cast, add a TMDB key: Settings › Movies and subtitles|Für Poster, Beschreibung in deiner Sprache und Besetzung: TMDB-Schlüssel unter Einstellungen › Filme und Untertitel|=|Za postere, opis na tvom jeziku i glumce upiši TMDB ključ: Podešavanja › Filmovi i titlovi|За постери, описание и актьори въведи TMDB ключ: Настройки › Филми и субтитри|Pentru postere, descriere și actori, adaugă cheia TMDB: Setări › Filme și subtitrări|Afiş, açıklama ve oyuncular için TMDB anahtarı gir: Ayarlar › Filmler ve altyazılar|Per locandine, descrizioni e cast aggiungi la chiave TMDB: Impostazioni › Film e sottotitoli|للملصقات والوصف والممثلين أدخل مفتاح TMDB: الإعدادات › الأفلام والترجمة
Aktivirati napredni mod?|Turn on advanced mode?|Erweiterten Modus aktivieren?|=|Aktivirati napredni režim?|Включване на разширения режим?|Activezi modul avansat?|Gelişmiş mod açılsın mı?|Attivare la modalità avanzata?|تشغيل الوضع المتقدم؟
Ostani u jednostavnom modu|Stay in simple mode|Im einfachen Modus bleiben|=|Ostani u jednostavnom režimu|Остани в опростен режим|Rămâi în modul simplu|Basit modda kal|Resta in modalità semplice|البقاء في الوضع البسيط
Nastavi i unesi PIN|Continue and enter PIN|Weiter und PIN eingeben|=|=|Продължи и въведи PIN|Continuă și introdu PIN-ul|Devam et ve PIN gir|Continua e inserisci il PIN|تابع وأدخل PIN
PIN za napredni mod|PIN for advanced mode|PIN für erweiterten Modus|=|PIN za napredni režim|PIN за разширен режим|PIN pentru modul avansat|Gelişmiş mod PIN'i|PIN per la modalità avanzata|PIN للوضع المتقدم
Vratiti sve izmjene?|Undo all changes?|Alle Änderungen zurücksetzen?|=|Vratiti sve izmene?|Отмяна на всички промени?|Anulezi toate modificările?|Tüm değişiklikler geri alınsın mı?|Annullare tutte le modifiche?|التراجع عن كل التغييرات؟
Da, vrati kako je bilo|Yes, restore as it was|Ja, wie vorher|=|=|Да, върни както беше|Da, revino la cum era|Evet, eski haline getir|Sì, ripristina|نعم، أعدها كما كانت
OK na grupi otvara opcije: redoslijed, naziv, sakrivanje, PIN. Kanale uređuješ u TV-u: drži OK na kanalu.|OK on a group opens options: order, name, hiding, PIN. Edit channels in TV: hold OK on a channel.|OK auf einer Gruppe öffnet Optionen: Reihenfolge, Name, Ausblenden, PIN. Sender bearbeitest du im TV: OK auf dem Sender halten.|=|OK na grupi otvara opcije: redosled, naziv, sakrivanje, PIN. Kanale uređuješ u TV-u: drži OK na kanalu.|OK върху група отваря опции. Каналите редактираш в ТВ: задръж OK.|OK pe un grup deschide opțiunile. Canalele se editează în TV: ține OK.|Grupta OK seçenekleri açar. Kanalları TV'de düzenle: kanalda OK'u basılı tut.|OK su un gruppo apre le opzioni. I canali si modificano in TV: tieni OK sul canale.|OK على مجموعة يفتح الخيارات. عدّل القنوات في التلفاز: اضغط مطولاً على القناة.
Grupa|Group|Gruppe|=|=|Група|Grup|Grup|Gruppo|المجموعة
Naziv|Name|Name|=|=|Име|Nume|Ad|Nome|الاسم
Broj|Number|Nummer|=|=|Номер|Număr|Numara|Numero|الرقم
Zaključan|Locked|Gesperrt|=|=|Заключен|Blocat|Kilitli|Bloccato|مقفل
Vrsta strima|Stream type|Stream-Typ|=|=|Вид поток|Tip flux|Yayın türü|Tipo di stream|نوع البث
Kvalitet po nazivu|Quality by name|Qualität laut Name|Kvaliteta po nazivu|=|Качество по име|Calitate după nume|Ada göre kalite|Qualità dal nome|الجودة حسب الاسم
TV kanali|TV channels|TV-Sender|=|=|ТВ канали|Canale TV|TV kanalları|Canali TV|قنوات التلفاز
Provjeri TMDB ključ|Check TMDB key|TMDB-Schlüssel prüfen|=|Proveri TMDB ključ|Провери TMDB ключ|Verifică cheia TMDB|TMDB anahtarını kontrol et|Verifica chiave TMDB|التحقق من مفتاح TMDB
TMDB API ključ|TMDB API key|TMDB-API-Schlüssel|=|=|TMDB API ключ|Cheie API TMDB|TMDB API anahtarı|Chiave API TMDB|مفتاح TMDB API
OMDb API ključ (IMDb ocjena)|OMDb API key (IMDb rating)|OMDb-API-Schlüssel (IMDb-Bewertung)|=|OMDb API ključ (IMDb ocena)|OMDb API ключ (IMDb оценка)|Cheie API OMDb (nota IMDb)|OMDb API anahtarı (IMDb puanı)|Chiave API OMDb (voto IMDb)|مفتاح OMDb (تقييم IMDb)
OpenSubtitles API ključ|OpenSubtitles API key|OpenSubtitles-API-Schlüssel|=|=|OpenSubtitles API ключ|Cheie API OpenSubtitles|OpenSubtitles API anahtarı|Chiave API OpenSubtitles|مفتاح OpenSubtitles API
OpenSubtitles korisnik (opciono)|OpenSubtitles user (optional)|OpenSubtitles-Benutzer (optional)|=|=|Потребител OpenSubtitles (по избор)|Utilizator OpenSubtitles (opțional)|OpenSubtitles kullanıcısı (isteğe bağlı)|Utente OpenSubtitles (facoltativo)|مستخدم OpenSubtitles (اختياري)
OpenSubtitles lozinka|OpenSubtitles password|OpenSubtitles-Passwort|=|=|Парола OpenSubtitles|Parolă OpenSubtitles|OpenSubtitles şifresi|Password OpenSubtitles|كلمة مرور OpenSubtitles
SubDL API ključ|SubDL API key|SubDL-API-Schlüssel|=|=|SubDL API ключ|Cheie API SubDL|SubDL API anahtarı|Chiave API SubDL|مفتاح SubDL API
Prvi put Android može pitati da dozvoliš PRETORIAN-u instaliranje aplikacija. Dozvoli i ponovo klikni Ažuriraj.|The first time, Android may ask you to allow PRETORIAN to install apps. Allow it and click Update again.|Beim ersten Mal fragt Android eventuell, ob PRETORIAN Apps installieren darf. Erlauben und erneut Aktualisieren drücken.|=|=|Първия път Android може да поиска разрешение. Разреши и натисни Обнови отново.|Prima dată Android poate cere permisiunea de instalare. Permite și apasă din nou Actualizează.|Android ilk seferde yükleme izni isteyebilir. İzin ver ve tekrar Güncelle'ye bas.|La prima volta Android può chiedere il permesso di installare. Consenti e premi di nuovo Aggiorna.|قد يطلب Android أول مرة إذن التثبيت. اسمح ثم اضغط تحديث مجددًا.
Postavke › Filmovi i titlovi › OpenSubtitles ili SubDL ključ|Settings › Movies and subtitles › OpenSubtitles or SubDL key|Einstellungen › Filme und Untertitel › OpenSubtitles- oder SubDL-Schlüssel|=|Podešavanja › Filmovi i titlovi › OpenSubtitles ili SubDL ključ|Настройки › Филми и субтитри › ключ|Setări › Filme și subtitrări › cheie|Ayarlar › Filmler ve altyazılar › anahtar|Impostazioni › Film e sottotitoli › chiave|الإعدادات › الأفلام والترجمة › المفتاح
Nova verzija %s|New version %s|Neue Version %s|=|=|Нова версия %s|Versiune nouă %s|Yeni sürüm %s|Nuova versione %s|إصدار جديد %s
Za %s min počinje|Starts in %s min|Beginnt in %s Min.|=|Za %s min počinje|Започва след %s мин|Începe în %s min|%s dk sonra başlıyor|Inizia tra %s min|يبدأ بعد %s د
Počinje sada|Starting now|Beginnt jetzt|=|=|Започва сега|Începe acum|Şimdi başlıyor|Inizia ora|يبدأ الآن
Prebaci|Switch|Umschalten|=|=|Превключи|Comută|Geç|Passa|انتقال
Zatvori|Close|Schließen|=|=|Затвори|Închide|Kapat|Chiudi|إغلاق
Pretraga|Search|Suche|=|=|Търсене|Căutare|Arama|Cerca|بحث
Bez isteka|No expiry|Kein Ablauf|=|=|Без изтичане|Fără expirare|Süresiz|Nessuna scadenza|بلا انتهاء
Istekla %s|Expired %s|Abgelaufen %s|=|=|Изтекъл %s|Expirat %s|Bitti %s|Scaduta %s|انتهت %s
Ističe %s (još %s d)|Expires %s (%s d left)|Läuft ab %s (noch %s T.)|=|=|Изтича %s (още %s д)|Expiră %s (încă %s z)|Bitiş %s (%s gün kaldı)|Scade %s (ancora %s g)|تنتهي %s (متبقي %s يوم)
Ističe %s|Expires %s|Läuft ab %s|=|=|Изтича %s|Expiră %s|Bitiş %s|Scade %s|تنتهي %s
Stigla je nova verzija %s|New version %s is here|Neue Version %s ist da|=|Stigla je nova verzija %s|Нова версия %s е налична|Versiunea %s este disponibilă|Yeni sürüm %s geldi|È arrivata la versione %s|وصل الإصدار %s
Preuzimam %s %%|Downloading %s %%|Lade %s %%|=|=|Изтегляне %s %%|Se descarcă %s %%|İndiriliyor %s %%|Download %s %%|جارٍ التنزيل %s %%
Obavještenja|Notifications|Benachrichtigungen|Obavijesti|Obaveštenja|Известия|Notificări|Bildirimler|Notifiche|الإشعارات
Nema obavještenja.|No notifications.|Keine Benachrichtigungen.|Nema obavijesti.|Nema obaveštenja.|Няма известия.|Nicio notificare.|Bildirim yok.|Nessuna notifica.|لا توجد إشعارات.
Obriši sva obavještenja|Clear all notifications|Alle löschen|Obriši sve obavijesti|Obriši sva obaveštenja|Изчисти всички|Șterge toate|Tümünü temizle|Cancella tutte|مسح كل الإشعارات
Dodaj playlistu u Postavkama|Add a playlist in Settings|Playlist in den Einstellungen hinzufügen|=|Dodaj plejlistu u Podešavanjima|Добави плейлист в Настройки|Adaugă un playlist în Setări|Ayarlardan liste ekle|Aggiungi una playlist nelle Impostazioni|أضف قائمة من الإعدادات
Nastavi gledati|Continue watching|Weiterschauen|=|Nastavi gledanje|Продължи гледането|Continuă vizionarea|İzlemeye devam et|Continua a guardare|متابعة المشاهدة
Sljedeća: S%sE%s|Next: S%sE%s|Nächste: S%sE%s|=|Sledeća: S%sE%s|Следващ: S%sE%s|Următorul: S%sE%s|Sıradaki: S%sE%s|Prossimo: S%sE%s|التالي: S%sE%s
Stao si na %s od %s|Stopped at %s of %s|Gestoppt bei %s von %s|=|Stao si na %s od %s|Спря на %s от %s|Oprit la %s din %s|%s / %s noktasında kaldın|Fermato a %s di %s|توقفت عند %s من %s
Ukloni iz Nastavi gledati|Remove from Continue watching|Aus Weiterschauen entfernen|=|Ukloni iz Nastavi gledanje|Премахни от Продължи|Elimină din Continuă|Devam listesinden çıkar|Rimuovi da Continua|إزالة من المتابعة
Označi kao gledano|Mark as watched|Als gesehen markieren|=|Označi kao gledano|Маркирай като гледано|Marchează ca vizionat|İzlendi olarak işaretle|Segna come visto|تعليم كمشاهد
Pažnja: aktivirate napredni mod i dodatne opcije. Nastavite s pažnjom, jer možete izazvati nepoželjne posljedice i pokvariti podešavanja programa.|Warning: you are turning on advanced mode and extra options. Be careful, you could change things that break the app's settings.|Achtung: Du aktivierst den erweiterten Modus. Vorsicht, Einstellungen können beschädigt werden.|=|Pažnja: aktivirate napredni režim i dodatne opcije. Budite pažljivi, možete pokvariti podešavanja programa.|Внимание: включвате разширения режим. Бъдете внимателни, може да развалите настройките.|Atenție: activezi modul avansat. Ai grijă, poți strica setările aplicației.|Dikkat: gelişmiş modu açıyorsunuz. Dikkatli olun, ayarları bozabilirsiniz.|Attenzione: attivi la modalità avanzata. Fai attenzione, potresti rovinare le impostazioni.|تنبيه: أنت تفعّل الوضع المتقدم. انتبه، قد تفسد الإعدادات.
Nova epizoda: %s|New episode: %s|Neue Folge: %s|=|=|Нов епизод: %s|Episod nou: %s|Yeni bölüm: %s|Nuovo episodio: %s|حلقة جديدة: %s
Drži OK|Hold OK|OK halten|=|=|Задръж OK|Ține OK|OK basılı|Tieni OK|اضغط مطولاً
Nazad|Back|Zurück|=|=|Назад|Înapoi|Geri|Indietro|رجوع
Kretanje|Move|Bewegen|=|=|Движение|Navigare|Gezin|Sposta|تنقل
Izaberi|Select|Auswählen|Odaberi|=|Избери|Alege|Seç|Seleziona|اختيار
Opcije|Options|Optionen|=|=|Опции|Opțiuni|Seçenekler|Opzioni|خيارات
Natrag|Go back|Zurück|=|=|Обратно|Înapoi|Geri dön|Torna indietro|عودة
Fajl nije PRETORIAN backup|The file is not a PRETORIAN backup|Die Datei ist kein PRETORIAN-Backup|Datoteka nije PRETORIAN backup|=|Файлът не е PRETORIAN резервно копие|Fișierul nu e un backup PRETORIAN|Dosya PRETORIAN yedeği değil|Il file non è un backup PRETORIAN|الملف ليس نسخة PRETORIAN
Backup je zaštićen lozinkom|The backup is password protected|Das Backup ist passwortgeschützt|=|=|Копието е защитено с парола|Backup-ul are parolă|Yedek şifre korumalı|Il backup è protetto da password|النسخة محمية بكلمة مرور
Pogrešna lozinka|Wrong password|Falsches Passwort|=|=|Грешна парола|Parolă greșită|Yanlış şifre|Password errata|كلمة مرور خاطئة
Kanali s arhivom|Channels with archive|Sender mit Archiv|=|=|Канали с архив|Canale cu arhivă|Arşivli kanallar|Canali con archivio|قنوات بأرشيف
Traži kanal|Find a channel|Sender suchen|=|=|Търси канал|Caută canal|Kanal ara|Cerca canale|ابحث عن قناة
Nijedan kanal nema arhivu|No channel has an archive|Kein Sender hat ein Archiv|=|=|Няма канал с архив|Niciun canal nu are arhivă|Hiçbir kanalda arşiv yok|Nessun canale ha l'archivio|لا توجد قناة بأرشيف
Provider ne nudi catch-up za tvoje kanale|Your provider offers no catch-up for your channels|Dein Anbieter bietet kein Catch-up|=|=|Доставчикът няма архив за каналите|Furnizorul nu oferă catch-up|Sağlayıcın catch-up sunmuyor|Il provider non offre catch-up|المزود لا يوفر الإعادة
Emisije iz arhive možeš gledati i kad su prošle|Watch shows from the archive after they aired|Sendungen aus dem Archiv nachträglich ansehen|=|=|Гледай предавания от архива|Vezi emisiuni din arhivă|Arşivden yayınları izle|Guarda i programmi dall'archivio|شاهد البرامج من الأرشيف
Zadnjih %s dana|Last %s days|Letzte %s Tage|=|=|Последните %s дни|Ultimele %s zile|Son %s gün|Ultimi %s giorni|آخر %s أيام
Učitavam arhivu…|Loading archive…|Archiv wird geladen…|=|=|Зареждане на архива…|Se încarcă arhiva…|Arşiv yükleniyor…|Caricamento archivio…|جارٍ تحميل الأرشيف…
Arhiva nije dostupna|Archive not available|Archiv nicht verfügbar|=|=|Архивът не е наличен|Arhivă indisponibilă|Arşiv yok|Archivio non disponibile|الأرشيف غير متاح
Nema emisija u arhivi za ovaj dan|No archived shows for this day|Keine Archivsendungen an diesem Tag|=|=|Няма архивни предавания за деня|Nicio emisiune arhivată în ziua asta|Bu gün için arşiv yok|Nessun programma in archivio per questo giorno|لا توجد برامج مؤرشفة لهذا اليوم
OK = gledaj|OK = watch|OK = ansehen|=|=|OK = гледай|OK = vizionează|OK = izle|OK = guarda|OK = مشاهدة
Player se ne može otvoriti|The player cannot be opened|Player lässt sich nicht öffnen|=|Plejer se ne može otvoriti|Плейърът не може да се отвори|Playerul nu se poate deschide|Oynatıcı açılamıyor|Impossibile aprire il player|لا يمكن فتح المشغل
Otvori u playeru|Open in player|Im Player öffnen|=|Otvori u plejeru|Отвори в плейър|Deschide în player|Oynatıcıda aç|Apri nel player|فتح في المشغل
Nema instaliranog eksternog playera. Instaliraj VLC iz Google Play prodavnice.|No external player installed. Install VLC from Google Play.|Kein externer Player installiert. Installiere VLC aus Google Play.|=|Nema instaliranog eksternog plejera. Instaliraj VLC iz Google Play prodavnice.|Няма външен плейър. Инсталирай VLC от Google Play.|Niciun player extern. Instalează VLC din Google Play.|Harici oynatıcı yok. Google Play'den VLC yükle.|Nessun player esterno. Installa VLC da Google Play.|لا يوجد مشغل خارجي. ثبّت VLC من Google Play.
Samo jednom|Just once|Nur einmal|=|=|Само веднъж|Doar o dată|Yalnızca bir kez|Solo una volta|مرة واحدة
Uvijek|Always|Immer|=|Uvek|Винаги|Întotdeauna|Her zaman|Sempre|دائمًا
Uvijek: %s|Always: %s|Immer: %s|=|Uvek: %s|Винаги: %s|Întotdeauna: %s|Her zaman: %s|Sempre: %s|دائمًا: %s
Preuzimanja (Download)|Downloads|Downloads|Preuzimanja (Download)|=|Изтегляния|Descărcări|İndirilenler|Download|التنزيلات
Memorija boxa|Box storage|Gerätespeicher|Memorija uređaja|=|Памет на устройството|Memoria box-ului|Cihaz belleği|Memoria del box|ذاكرة الجهاز
PRETORIAN folder|PRETORIAN folder|PRETORIAN-Ordner|PRETORIAN mapa|=|Папка PRETORIAN|Folder PRETORIAN|PRETORIAN klasörü|Cartella PRETORIAN|مجلد PRETORIAN
PRETORIAN treba dozvolu da vidi fajlove na boxu i USB-u.|PRETORIAN needs permission to see files on the box and USB.|PRETORIAN braucht die Erlaubnis für Dateien auf dem Gerät und USB.|PRETORIAN treba dopuštenje za datoteke na uređaju i USB-u.|=|PRETORIAN се нуждае от разрешение за файловете.|PRETORIAN are nevoie de permisiune pentru fișiere.|PRETORIAN dosyalar için izne ihtiyaç duyuyor.|PRETORIAN ha bisogno del permesso per i file.|يحتاج PRETORIAN إذنًا لرؤية الملفات.
Dozvoli pristup fajlovima|Allow file access|Dateizugriff erlauben|Dopusti pristup datotekama|=|Разреши достъп до файлове|Permite accesul la fișiere|Dosya erişimine izin ver|Consenti accesso ai file|السماح بالوصول للملفات
Ovaj box nema taj ekran. Dozvoli sa laptopa (adb), uputstvo je ispod.|This box has no such screen. Allow it from a laptop (adb), see below.|Dieses Gerät hat diesen Bildschirm nicht. Per adb erlauben, siehe unten.|=|=|Устройството няма този екран. Разреши през adb (виж по-долу).|Box-ul nu are acest ecran. Permite prin adb (mai jos).|Bu cihazda bu ekran yok. adb ile izin ver (aşağıda).|Questo box non ha la schermata. Consenti via adb (sotto).|لا توجد هذه الشاشة. اسمح عبر adb (بالأسفل).
Dozvola se ne može otvoriti|Cannot open the permission screen|Berechtigung lässt sich nicht öffnen|Dopuštenje se ne može otvoriti|=|Разрешението не може да се отвори|Permisiunea nu se poate deschide|İzin ekranı açılamıyor|Impossibile aprire il permesso|تعذر فتح الإذن
Spremi ovdje|Save here|Hier speichern|=|Sačuvaj ovde|Запази тук|Salvează aici|Buraya kaydet|Salva qui|احفظ هنا
Folder je prazan ili nema dozvole.|The folder is empty or not allowed.|Ordner ist leer oder nicht erlaubt.|Mapa je prazna ili nema dopuštenja.|=|Папката е празна или няма достъп.|Folderul e gol sau fără permisiune.|Klasör boş veya izin yok.|Cartella vuota o senza permesso.|المجلد فارغ أو غير مسموح.
Taj file manager se ne može otvoriti, koristim ugrađeni|That file manager cannot be opened, using the built-in one|Dieser Dateimanager geht nicht, nutze den eingebauten|=|=|Файловият мениджър не се отваря, ползвам вградения|Managerul nu se deschide, folosesc cel intern|Dosya yöneticisi açılamadı, dahili kullanılıyor|Il file manager non si apre, uso quello integrato|تعذر فتح مدير الملفات، سأستخدم المدمج
Izaberi file manager|Choose a file manager|Dateimanager wählen|Odaberi upravitelj datoteka|=|Избери файлов мениджър|Alege managerul de fișiere|Dosya yöneticisi seç|Scegli il file manager|اختر مدير الملفات
PRETORIAN (ugrađeni)|PRETORIAN (built-in)|PRETORIAN (eingebaut)|PRETORIAN (ugrađeni)|=|PRETORIAN (вграден)|PRETORIAN (integrat)|PRETORIAN (dahili)|PRETORIAN (integrato)|PRETORIAN (مدمج)
Za daljinski: USB, Download, memorija boxa|For the remote: USB, Downloads, box storage|Für die Fernbedienung: USB, Downloads, Speicher|=|=|За дистанционното: USB, изтегляния, памет|Pentru telecomandă: USB, descărcări, memorie|Kumanda için: USB, indirilenler, bellek|Per il telecomando: USB, download, memoria|للريموت: USB، التنزيلات، الذاكرة
Otvoren je tvoj file manager. Ako se ništa ne desi, pritisni Nazad.|Your file manager is open. If nothing happens, press Back.|Dein Dateimanager ist offen. Falls nichts passiert, drücke Zurück.|=|=|Файловият мениджър е отворен. Ако нищо не стане, натисни Назад.|Managerul tău e deschis. Dacă nu se întâmplă nimic, apasă Înapoi.|Dosya yöneticin açık. Bir şey olmazsa Geri'ye bas.|Il tuo file manager è aperto. Se non succede nulla, premi Indietro.|مدير الملفات مفتوح. إن لم يحدث شيء اضغط رجوع.
Koristi ugrađeni|Use the built-in one|Eingebauten nutzen|=|=|Използвай вградения|Folosește cel intern|Dahiliyi kullan|Usa quello integrato|استخدم المدمج
Lista kanala|Channel list|Senderliste|Popis kanala|=|Списък канали|Listă canale|Kanal listesi|Lista canali|قائمة القنوات
još %s min|%s min left|noch %s Min.|=|=|още %s мин|încă %s min|%s dk kaldı|ancora %s min|متبقي %s د
Nema informacija|No information|Keine Informationen|=|=|Няма информация|Fără informații|Bilgi yok|Nessuna informazione|لا توجد معلومات
Ukloni podsjetnik|Remove reminder|Erinnerung entfernen|=|=|Премахни напомняне|Elimină memento|Hatırlatıcıyı kaldır|Rimuovi promemoria|إزالة التذكير
Podsjeti me|Remind me|Erinnere mich|=|Podseti me|Напомни ми|Amintește-mi|Hatırlat|Ricordamelo|ذكّرني
Podsjetnik postavljen|Reminder set|Erinnerung gesetzt|=|Podsetnik postavljen|Напомнянето е зададено|Memento setat|Hatırlatıcı kuruldu|Promemoria impostato|تم ضبط التذكير
Podsjetnik uklonjen|Reminder removed|Erinnerung entfernt|=|Podsetnik uklonjen|Напомнянето е премахнато|Memento eliminat|Hatırlatıcı kaldırıldı|Promemoria rimosso|تمت إزالة التذكير
Gledaj kanal sada|Watch the channel now|Sender jetzt ansehen|=|=|Гледай канала сега|Vezi canalul acum|Kanalı şimdi izle|Guarda il canale ora|شاهد القناة الآن
Server odbija pristup: nalog istekao ili blokiran|Server denies access: account expired or blocked|Server verweigert Zugriff: Konto abgelaufen oder gesperrt|=|=|Сървърът отказва: акаунтът е изтекъл или блокиран|Serverul refuză: cont expirat sau blocat|Sunucu erişimi reddetti: hesap bitti veya engelli|Il server rifiuta: account scaduto o bloccato|الخادم يرفض: الحساب منتهٍ أو محظور
Previše konekcija: ugasi gledanje na drugom uređaju|Too many connections: stop watching on another device|Zu viele Verbindungen: auf anderem Gerät beenden|=|=|Твърде много връзки: спри на друго устройство|Prea multe conexiuni: oprește pe alt dispozitiv|Çok fazla bağlantı: diğer cihazda kapat|Troppe connessioni: chiudi su un altro dispositivo|اتصالات كثيرة: أوقف المشاهدة على جهاز آخر
Kanal više ne postoji kod providera|The channel no longer exists at the provider|Sender gibt es beim Anbieter nicht mehr|=|=|Каналът вече не съществува|Canalul nu mai există la furnizor|Kanal sağlayıcıda artık yok|Il canale non esiste più|القناة لم تعد موجودة لدى المزود
Server trenutno ne radi|The server is down right now|Server derzeit nicht erreichbar|=|=|Сървърът не работи в момента|Serverul nu funcționează acum|Sunucu şu an çalışmıyor|Il server non funziona ora|الخادم لا يعمل حاليًا
Server je odbio zahtjev|The server refused the request|Server hat die Anfrage abgelehnt|=|Server je odbio zahtev|Сървърът отказа заявката|Serverul a refuzat cererea|Sunucu isteği reddetti|Il server ha rifiutato la richiesta|رفض الخادم الطلب
Nema veze sa serverom|No connection to the server|Keine Verbindung zum Server|=|=|Няма връзка със сървъра|Nicio conexiune la server|Sunucuya bağlantı yok|Nessuna connessione al server|لا يوجد اتصال بالخادم
Kanal trenutno ne radi|The channel is not working right now|Sender funktioniert gerade nicht|=|=|Каналът не работи в момента|Canalul nu merge acum|Kanal şu an çalışmıyor|Il canale non funziona ora|القناة لا تعمل حاليًا
Video se ne može pustiti|The video cannot be played|Video kann nicht abgespielt werden|=|=|Видеото не може да се пусне|Video-ul nu poate fi redat|Video oynatılamıyor|Impossibile riprodurre il video|لا يمكن تشغيل الفيديو
OK pokušaj ponovo · ▲ ▼ drugi kanal · Nazad za izlaz|OK try again · ▲ ▼ other channel · Back to exit|OK erneut · ▲ ▼ anderer Sender · Zurück zum Beenden|=|=|OK опитай пак · ▲ ▼ друг канал · Назад за изход|OK reîncearcă · ▲ ▼ alt canal · Înapoi pentru ieșire|OK tekrar dene · ▲ ▼ başka kanal · Geri ile çık|OK riprova · ▲ ▼ altro canale · Indietro per uscire|OK أعد المحاولة · ▲ ▼ قناة أخرى · رجوع للخروج
OK pokušaj ponovo · Meni › Eksterni player · Nazad za izlaz|OK try again · Menu › External player · Back to exit|OK erneut · Menü › Externer Player · Zurück zum Beenden|=|=|OK опитай пак · Меню › Външен плейър · Назад|OK reîncearcă · Meniu › Player extern · Înapoi|OK tekrar dene · Menü › Harici oynatıcı · Geri|OK riprova · Menu › Player esterno · Indietro|OK أعد المحاولة · القائمة › مشغل خارجي · رجوع
Sada|Now|Jetzt|=|=|Сега|Acum|Şimdi|Ora|الآن
Zatim|Next|Danach|=|=|След това|Apoi|Sonra|Dopo|التالي
TITL|SUBS|UT|=|=|СУБТ.|SUBT.|ALTYAZI|SOTT.|ترجمة
▲ ▼ kanal · ◀ lista kanala · ▶ TV vodič · OK još jednom = dugmad|▲ ▼ channel · ◀ channel list · ▶ TV guide · OK again = buttons|▲ ▼ Sender · ◀ Senderliste · ▶ TV-Guide · OK nochmal = Tasten|=|=|▲ ▼ канал · ◀ списък · ▶ ТВ програма · OK пак = бутони|▲ ▼ canal · ◀ listă · ▶ ghid TV · OK din nou = butoane|▲ ▼ kanal · ◀ liste · ▶ TV rehberi · tekrar OK = düğmeler|▲ ▼ canale · ◀ lista · ▶ guida TV · OK di nuovo = pulsanti|▲ ▼ قناة · ◀ القائمة · ▶ الدليل · OK مرة أخرى = أزرار
OK pauza · ◀ ▶ %s s · ▼ = dugmad (audio, titlovi, format slike)|OK pause · ◀ ▶ %s s · ▼ = buttons (audio, subtitles, picture)|OK Pause · ◀ ▶ %s s · ▼ = Tasten (Audio, Untertitel, Bild)|=|=|OK пауза · ◀ ▶ %s с · ▼ = бутони|OK pauză · ◀ ▶ %s s · ▼ = butoane|OK duraklat · ◀ ▶ %s sn · ▼ = düğmeler|OK pausa · ◀ ▶ %s s · ▼ = pulsanti|OK إيقاف · ◀ ▶ %s ث · ▼ = أزرار
◀ ili Nazad = slika preko cijelog ekrana · OK na emisiji = podsjeti me|◀ or Back = full screen · OK on a show = remind me|◀ oder Zurück = Vollbild · OK auf Sendung = erinnern|=|◀ ili Nazad = slika preko celog ekrana · OK na emisiji = podseti me|◀ или Назад = цял екран · OK = напомни|◀ sau Înapoi = ecran complet · OK = amintește|◀ veya Geri = tam ekran · OK = hatırlat|◀ o Indietro = schermo intero · OK = ricorda|◀ أو رجوع = ملء الشاشة · OK = تذكير
Nastavi od %s|Continue from %s|Fortsetzen ab %s|=|=|Продължи от %s|Continuă de la %s|%s'den devam et|Riprendi da %s|متابعة من %s
od %s|of %s|von %s|=|=|от %s|din %s|/ %s|di %s|من %s
Od početka|From the beginning|Von Anfang an|=|=|От началото|De la început|Baştan|Dall'inizio|من البداية
Liste favorita…|Favourite lists…|Favoritenlisten…|=|Liste omiljenih…|Списъци любими…|Liste favorite…|Favori listeleri…|Liste preferiti…|قوائم المفضلة…
TV vodič|TV guide|TV-Guide|=|=|ТВ програма|Ghid TV|TV rehberi|Guida TV|دليل التلفاز
Učitan|Loaded|Geladen|=|=|Зареден|Încărcat|Yüklendi|Caricato|محمّل
Grupe|Groups|Gruppen|=|=|Групи|Grupuri|Gruplar|Gruppi|المجموعات
◀ grupe · ▶ zatvori|◀ groups · ▶ close|◀ Gruppen · ▶ schließen|=|=|◀ групи · ▶ затвори|◀ grupuri · ▶ închide|◀ gruplar · ▶ kapat|◀ gruppi · ▶ chiudi|◀ المجموعات · ▶ إغلاق
Traži kanal, film, seriju, emisiju ili broj|Search channel, movie, series, show or number|Sender, Film, Serie, Sendung oder Nummer suchen|=|=|Търси канал, филм, сериал, предаване или номер|Caută canal, film, serial, emisiune sau număr|Kanal, film, dizi, program veya numara ara|Cerca canale, film, serie, programma o numero|ابحث عن قناة أو فيلم أو مسلسل أو برنامج
Učitavam listu za pretragu…|Loading the search list…|Suchliste wird geladen…|=|=|Зареждане на списъка…|Se încarcă lista…|Arama listesi yükleniyor…|Caricamento lista…|جارٍ تحميل قائمة البحث…
Upiši bar 2 slova|Type at least 2 letters|Mindestens 2 Buchstaben eingeben|=|=|Въведи поне 2 букви|Scrie cel puțin 2 litere|En az 2 harf yaz|Scrivi almeno 2 lettere|اكتب حرفين على الأقل
Tastatura daljinskog ili Google TV aplikacija na telefonu|Remote keyboard or the Google TV app on your phone|Fernbedienungs-Tastatur oder Google-TV-App am Handy|=|=|Клавиатура или приложението Google TV на телефона|Tastatura sau aplicația Google TV de pe telefon|Klavye veya telefondaki Google TV uygulaması|Tastiera o app Google TV sul telefono|لوحة المفاتيح أو تطبيق Google TV على الهاتف
Ništa nije pronađeno|Nothing found|Nichts gefunden|=|=|Нищо не е намерено|Nimic găsit|Hiçbir şey bulunamadı|Nessun risultato|لم يُعثر على شيء
Kanal|Channel|Sender|=|=|Канал|Canal|Kanal|Canale|قناة
Serija|Series|Serie|=|=|Сериал|Serial|Dizi|Serie|مسلسل
Svi kanali|All channels|Alle Sender|=|=|Всички канали|Toate canalele|Tüm kanallar|Tutti i canali|كل القنوات
Gledaj sada|Watch now|Jetzt ansehen|=|=|Гледай сега|Vezi acum|Şimdi izle|Guarda ora|شاهد الآن
Prikaži u vodiču|Show in guide|Im Guide zeigen|=|=|Покажи в програмата|Arată în ghid|Rehberde göster|Mostra nella guida|عرض في الدليل
Dodaj kanal u favorite|Add channel to favourites|Sender zu Favoriten|=|Dodaj kanal u omiljene|Добави канала в любими|Adaugă canalul la favorite|Kanalı favorilere ekle|Aggiungi canale ai preferiti|أضف القناة للمفضلة
Otvori|Open|Öffnen|=|=|Отвори|Deschide|Aç|Apri|فتح
Emisije|Programmes|Sendungen|=|=|Предавания|Emisiuni|Programlar|Programmi|البرامج
Uključi jednu ili više. Gornja uključena je otvorena, ostale su sklopljene.|Turn on one or more. The top one is open, the others are folded.|Eine oder mehrere einschalten. Die oberste ist offen, die anderen zugeklappt.|=|=|Включи една или повече. Горната е отворена.|Pornește una sau mai multe. Prima e deschisă.|Bir veya daha fazlasını aç. Üstteki açık.|Attiva una o più. La prima è aperta.|شغّل واحدة أو أكثر. العليا مفتوحة.
Aktivna je samo jedna. OK na drugoj je aktivira.|Only one is active. OK on another activates it.|Nur eine ist aktiv. OK auf einer anderen aktiviert sie.|=|=|Активен е само един. OK на друг го активира.|Doar unul e activ. OK pe altul îl activează.|Yalnızca biri aktif. Diğerinde OK onu açar.|Solo una è attiva. OK su un'altra la attiva.|واحدة فقط نشطة. OK على أخرى يفعّلها.
Ažuriram…|Updating…|Aktualisiere…|=|=|Обновяване…|Se actualizează…|Güncelleniyor…|Aggiornamento…|جارٍ التحديث…
Playliste su ažurirane|Playlists updated|Playlists aktualisiert|=|Plejliste su ažurirane|Плейлистите са обновени|Playlisturi actualizate|Listeler güncellendi|Playlist aggiornate|تم تحديث القوائم
Samo jedna aktivna|Only one active|Nur eine aktiv|=|=|Само един активен|Doar unul activ|Yalnızca bir aktif|Solo una attiva|واحدة نشطة فقط
Više aktivnih|Several active|Mehrere aktiv|=|=|Няколко активни|Mai multe active|Birden fazla aktif|Più attive|عدة قوائم نشطة
OTVORENA|OPEN|OFFEN|=|=|ОТВОРЕН|DESCHIS|AÇIK|APERTA|مفتوحة
Drži OK na playlisti: pomjeri, otvori, obriši. Datum isteka osvježava "Ažuriraj sve".|Hold OK on a playlist: move, open, delete. "Update all" refreshes the expiry date.|OK auf Playlist halten: verschieben, öffnen, löschen. "Alle aktualisieren" erneuert das Ablaufdatum.|=|Drži OK na plejlisti: pomeri, otvori, obriši. Datum isteka osvežava "Ažuriraj sve".|Задръж OK: премести, отвори, изтрий. "Обнови всички" опреснява датата.|Ține OK: mută, deschide, șterge. "Actualizează tot" reîmprospătează data.|OK basılı tut: taşı, aç, sil. "Tümünü güncelle" tarihi yeniler.|Tieni OK: sposta, apri, elimina. "Aggiorna tutto" aggiorna la scadenza.|اضغط مطولاً: نقل، فتح، حذف. "تحديث الكل" يحدّث التاريخ.
Isključi|Turn off|Ausschalten|=|=|Изключи|Oprește|Kapat|Disattiva|إيقاف
Uključi|Turn on|Einschalten|=|=|Включи|Pornește|Aç|Attiva|تشغيل
Otvori (gore u TV-u)|Open (on top in TV)|Öffnen (oben im TV)|=|=|Отвори (най-горе в ТВ)|Deschide (sus în TV)|Aç (TV'de en üstte)|Apri (in alto in TV)|فتح (في الأعلى)
Obrisati playlistu?|Delete playlist?|Playlist löschen?|=|Obrisati plejlistu?|Изтриване на плейлиста?|Ștergi playlistul?|Liste silinsin mi?|Eliminare la playlist?|حذف القائمة؟
Da, obriši|Yes, delete|Ja, löschen|=|=|Да, изтрий|Da, șterge|Evet, sil|Sì, elimina|نعم، احذف
M3U playlista|M3U playlist|M3U-Playlist|=|M3U plejlista|M3U плейлист|Playlist M3U|M3U listesi|Playlist M3U|قائمة M3U
Naziv playliste|Playlist name|Playlist-Name|=|Naziv plejliste|Име на плейлиста|Nume playlist|Liste adı|Nome playlist|اسم القائمة
Server adresa (http://server:port)|Server address (http://server:port)|Serveradresse (http://server:port)|=|=|Адрес на сървъра (http://server:port)|Adresă server (http://server:port)|Sunucu adresi (http://server:port)|Indirizzo server (http://server:port)|عنوان الخادم (http://server:port)
M3U link|M3U link|M3U-Link|=|=|M3U линк|Link M3U|M3U bağlantısı|Link M3U|رابط M3U
Korisničko ime|Username|Benutzername|=|=|Потребителско име|Nume utilizator|Kullanıcı adı|Nome utente|اسم المستخدم
Lozinka|Password|Passwort|=|=|Парола|Parolă|Şifre|Password|كلمة المرور
Dugačke linkove i lozinke možeš kucati s telefona: Google TV aplikacija › Daljinski › ikona tastature.|Type long links and passwords on your phone: Google TV app › Remote › keyboard icon.|Lange Links und Passwörter am Handy tippen: Google-TV-App › Fernbedienung › Tastatur.|=|=|Дълги линкове пиши от телефона: Google TV › Дистанционно › клавиатура.|Scrie linkurile lungi din telefon: Google TV › Telecomandă › tastatură.|Uzun bağlantıları telefondan yaz: Google TV › Kumanda › klavye.|Scrivi link lunghi dal telefono: app Google TV › Telecomando › tastiera.|اكتب الروابط الطويلة من الهاتف: تطبيق Google TV › الريموت › لوحة المفاتيح.
Provjeravam…|Checking…|Prüfe…|=|Proveravam…|Проверка…|Se verifică…|Kontrol ediliyor…|Verifica…|جارٍ التحقق…
Spremi i učitaj|Save and load|Speichern und laden|=|Sačuvaj i učitaj|Запази и зареди|Salvează și încarcă|Kaydet ve yükle|Salva e carica|احفظ وحمّل
Raspored|Layout|Layout|=|=|Оформление|Aspect|Düzen|Layout|التخطيط
Klasični|Classic|Klassisch|=|=|Класически|Clasic|Klasik|Classico|كلاسيكي
Klasični: meni lijevo, kategorije u koloni.|Classic: menu on the left, categories in a column.|Klassisch: Menü links, Kategorien in einer Spalte.|=|Klasični: meni levo, kategorije u koloni.|Класически: меню вляво, категории в колона.|Clasic: meniu stânga, categorii în coloană.|Klasik: menü solda, kategoriler sütunda.|Classico: menu a sinistra, categorie in colonna.|كلاسيكي: القائمة يسارًا والفئات في عمود.
New Wave: filmovi i serije preko cijelog ekrana, kategorije gore, pomoć za daljinski na dnu.|New Wave: movies and series across the whole screen, categories on top, remote help at the bottom.|New Wave: Filme und Serien über den ganzen Bildschirm, Kategorien oben, Hilfe unten.|=|New Wave: filmovi i serije preko celog ekrana, kategorije gore, pomoć na dnu.|New Wave: филми на цял екран, категории горе, помощ долу.|New Wave: filme pe tot ecranul, categorii sus, ajutor jos.|New Wave: filmler tam ekran, kategoriler üstte, yardım altta.|New Wave: film a tutto schermo, categorie in alto, aiuto in basso.|New Wave: الأفلام على كامل الشاشة، الفئات بالأعلى، المساعدة بالأسفل.
New Age: meni gore, redovi postera po kategorijama, velike kartice epizoda.|New Age: menu on top, poster rows per category, big episode cards.|New Age: Menü oben, Posterreihen je Kategorie, große Folgenkarten.|=|=|New Age: меню горе, редове постери, големи карти на епизоди.|New Age: meniu sus, rânduri de postere, carduri mari.|New Age: menü üstte, kategori başına afiş sıraları.|New Age: menu in alto, file di locandine per categoria.|New Age: القائمة بالأعلى وصفوف ملصقات لكل فئة.
Oblik ivica|Corner shape|Eckenform|Oblik rubova|=|Форма на ъглите|Forma colțurilor|Köşe şekli|Forma angoli|شكل الزوايا
Zaobljene|Rounded|Abgerundet|=|=|Заоблени|Rotunjite|Yuvarlak|Arrotondati|مستديرة
Blago zaobljene|Slightly rounded|Leicht abgerundet|=|=|Леко заоблени|Ușor rotunjite|Hafif yuvarlak|Leggermente arrotondati|مستديرة قليلًا
Oštre|Sharp|Eckig|=|=|Остри|Ascuțite|Keskin|Squadrati|حادة
Set ikona|Icon set|Symbolsatz|=|=|Комплект икони|Set de pictograme|Simge seti|Set di icone|مجموعة الأيقونات
Preuzeti set|Downloaded set|Heruntergeladener Satz|=|=|Изтеглен комплект|Set descărcat|İndirilen set|Set scaricato|مجموعة منزّلة
Preuzmi još ikona|Download more icons|Weitere Symbole laden|=|=|Изтегли още икони|Descarcă mai multe|Daha fazla simge indir|Scarica altre icone|نزّل المزيد من الأيقونات
Novi setovi s PRETORIAN GitHub stranice|New sets from the PRETORIAN GitHub page|Neue Sätze von der PRETORIAN-GitHub-Seite|=|=|Нови комплекти от GitHub на PRETORIAN|Seturi noi de pe GitHub PRETORIAN|PRETORIAN GitHub sayfasından yeni setler|Nuovi set dalla pagina GitHub di PRETORIAN|مجموعات جديدة من صفحة PRETORIAN على GitHub
Lista setova se nije učitala|The list of sets did not load|Liste nicht geladen|=|=|Списъкът не се зареди|Lista nu s-a încărcat|Liste yüklenemedi|Elenco non caricato|لم تُحمّل القائمة
Prozirnost panela: %s %%|Panel opacity: %s %%|Paneldeckkraft: %s %%|=|=|Непрозрачност на панела: %s %%|Opacitate panou: %s %%|Panel opaklığı: %s %%|Opacità pannello: %s %%|عتامة اللوحة: %s %%
Moderne|Modern|Modern|=|=|Модерни|Moderne|Modern|Moderni|حديثة
Okvir oko grupa|Frame around groups|Rahmen um Gruppen|Okvir oko grupa|=|Рамка около групите|Chenar în jurul grupurilor|Grup çerçevesi|Cornice attorno ai gruppi|إطار حول المجموعات
Tanki okvir u akcentnoj boji, otvorena grupa posvijetli|Thin accent frame, the open group lights up|Dünner Akzentrahmen, offene Gruppe leuchtet|=|=|Тънка рамка, отворената група свети|Chenar subțire, grupul deschis se luminează|İnce çerçeve, açık grup parlar|Cornice sottile, il gruppo aperto si illumina|إطار رفيع، والمجموعة المفتوحة تضيء
Rezolucija u uglu plejera|Resolution in the player corner|Auflösung in der Player-Ecke|Razlučivost u kutu playera|=|Резолюция в ъгъла|Rezoluție în colțul playerului|Oynatıcı köşesinde çözünürlük|Risoluzione nell'angolo|الدقة في زاوية المشغل
Isključeno|Off|Aus|=|=|Изключено|Oprit|Kapalı|Spento|إيقاف
Pri pokretanju|On start|Beim Start|=|=|При старт|La pornire|Başlangıçta|All'avvio|عند البدء
Kad staneš na kanal, desno se pušta mala slika i EPG|When you stop on a channel, a small picture and the guide play on the right|Beim Halten auf einem Sender: kleines Bild und Programm rechts|=|=|При спиране на канал вдясно тръгва малка картина и програма|Când te oprești pe un canal, apare o imagine mică și ghidul|Kanalda durunca sağda küçük görüntü ve rehber|Fermandoti su un canale, a destra appare l'anteprima e la guida|عند الوقوف على قناة تظهر صورة صغيرة والدليل يمينًا
Kanali koje najčešće gledaš sami idu gore (kad nema ručnog redoslijeda)|Channels you watch most move up by themselves (when there is no manual order)|Meistgesehene Sender rutschen nach oben (ohne eigene Reihenfolge)|=|=|Най-гледаните канали се качват нагоре сами|Canalele cele mai vizionate urcă singure|En çok izlediğin kanallar kendiliğinden yukarı çıkar|I canali più visti salgono da soli|القنوات الأكثر مشاهدة تصعد تلقائيًا
Preuzmi ikone|Download icons|Symbole laden|=|=|Изтегли икони|Descarcă pictograme|Simge indir|Scarica icone|تنزيل الأيقونات
Trenutno nema novih setova.|No new sets right now.|Derzeit keine neuen Sätze.|=|=|В момента няма нови комплекти.|Momentan nu sunt seturi noi.|Şu an yeni set yok.|Nessun nuovo set al momento.|لا توجد مجموعات جديدة حاليًا.
Preuzeto|Downloaded|Heruntergeladen|=|=|Изтеглено|Descărcat|İndirildi|Scaricato|تم التنزيل
Preuzimam…|Downloading…|Lade…|=|=|Изтегляне…|Se descarcă…|İndiriliyor…|Download…|جارٍ التنزيل…
Set %s je spreman|Set %s is ready|Satz %s ist bereit|=|=|Комплект %s е готов|Setul %s e gata|%s seti hazır|Set %s pronto|المجموعة %s جاهزة
Posteri, opis na tvom jeziku, glumci, trailer · themoviedb.org › Settings › API|Posters, description in your language, cast, trailer · themoviedb.org › Settings › API|Poster, Beschreibung in deiner Sprache, Besetzung, Trailer · themoviedb.org › Settings › API|=|=|Постери, описание на твоя език, актьори, трейлър · themoviedb.org › Settings › API|Postere, descriere în limba ta, actori, trailer · themoviedb.org › Settings › API|Afişler, kendi dilinde açıklama, oyuncular, fragman · themoviedb.org › Settings › API|Locandine, descrizione nella tua lingua, cast, trailer · themoviedb.org › Settings › API|ملصقات، وصف بلغتك، الممثلون، العرض · themoviedb.org › Settings › API
Više preuzimanja dnevno|More downloads per day|Mehr Downloads pro Tag|=|=|Повече изтегляния на ден|Mai multe descărcări pe zi|Günlük daha fazla indirme|Più download al giorno|تنزيلات أكثر يوميًا
Nije upisana|Not set|Nicht eingetragen|=|=|Не е въведена|Nesetată|Girilmedi|Non impostata|غير مُدخلة
Eksterni player i fajlovi|External player and files|Externer Player und Dateien|Vanjski player i datoteke|Eksterni plejer i fajlovi|Външен плейър и файлове|Player extern și fișiere|Harici oynatıcı ve dosyalar|Player esterno e file|المشغل الخارجي والملفات
Pitaj svaki put|Ask every time|Jedes Mal fragen|=|=|Питай всеки път|Întreabă de fiecare dată|Her seferinde sor|Chiedi ogni volta|اسأل كل مرة
Filmovi i serije uvijek u eksternom playeru|Movies and series always in the external player|Filme und Serien immer im externen Player|=|Filmovi i serije uvek u eksternom plejeru|Филми винаги във външния плейър|Filme mereu în playerul extern|Filmler hep harici oynatıcıda|Film sempre nel player esterno|الأفلام دائمًا في المشغل الخارجي
Pokreni otvara izabrani player umjesto ugrađenog|Play opens the chosen player instead of the built-in one|Abspielen öffnet den gewählten Player statt des eingebauten|=|Pusti otvara izabrani plejer umesto ugrađenog|Пусни отваря избрания плейър|Redă deschide playerul ales|Oynat seçilen oynatıcıyı açar|Riproduci apre il player scelto|تشغيل يفتح المشغل المختار
File manager|File manager|Dateimanager|Upravitelj datoteka|=|Файлов мениджър|Manager de fișiere|Dosya yöneticisi|File manager|مدير الملفات
Podsjetnici|Reminders|Erinnerungen|=|Podsetnici|Напомняния|Mementouri|Hatırlatıcılar|Promemoria|التذكيرات
U TV vodiču ili Sports Centeru: OK na emisiji ili utakmici koja još nije počela › Podsjeti me.|In the TV guide or Sports Center: OK on a show or match that has not started › Remind me.|Im TV-Guide oder Sports Center: OK auf eine kommende Sendung › Erinnere mich.|=|U TV vodiču ili Sports Centeru: OK na emisiji ili utakmici › Podseti me.|В програмата или Sports Center: OK на предаване › Напомни ми.|În ghid sau Sports Center: OK pe o emisiune › Amintește-mi.|Rehberde veya Spor Merkezinde: başlamamış programda OK › Hatırlat.|Nella guida o in Sports Center: OK su un programma › Ricordamelo.|في الدليل أو المركز الرياضي: OK على برنامج قادم › ذكّرني.
Podsjeti prije početka (minuta)|Remind before start (minutes)|Erinnern vor Beginn (Minuten)|=|Podseti pre početka (minuta)|Напомни преди началото (минути)|Amintește înainte (minute)|Başlamadan önce (dakika)|Ricorda prima dell'inizio (minuti)|ذكّر قبل البدء (دقائق)
Koliko dugo poruka stoji (sekundi)|How long the message stays (seconds)|Wie lange die Meldung bleibt (Sekunden)|=|=|Колко дълго стои съобщението (секунди)|Cât stă mesajul (secunde)|Mesaj ne kadar kalsın (saniye)|Durata del messaggio (secondi)|مدة بقاء الرسالة (ثوانٍ)
Kad dođe vrijeme|When the time comes|Wenn es soweit ist|=|Kad dođe vreme|Когато дойде времето|Când vine ora|Zamanı gelince|Quando è ora|عند حلول الوقت
Samo podsjeti|Only remind|Nur erinnern|=|Samo podseti|Само напомни|Doar amintește|Sadece hatırlat|Solo ricorda|تذكير فقط
Prebaci na kanal|Switch to the channel|Zum Sender wechseln|=|=|Превключи на канала|Comută pe canal|Kanala geç|Passa al canale|انتقل إلى القناة
Probudi box iz mirovanja|Wake the box from sleep|Gerät aus Standby wecken|Probudi uređaj iz mirovanja|=|Събуди устройството|Trezește box-ul|Cihazı uykudan uyandır|Risveglia il box|إيقاظ الجهاز
Ne radi na svim uređajima. Ako je box potpuno ugašen, podsjetnik ne može doći.|Does not work on every device. If the box is fully off, the reminder cannot come.|Funktioniert nicht überall. Ist das Gerät ganz aus, kommt keine Erinnerung.|=|=|Не работи навсякъде. Ако е изключено, няма напомняне.|Nu merge peste tot. Dacă e oprit complet, nu vine mementoul.|Her cihazda çalışmaz. Cihaz kapalıysa hatırlatıcı gelmez.|Non funziona ovunque. Se il box è spento, nessun promemoria.|لا يعمل على كل الأجهزة. إذا كان مطفأً لن يصل التذكير.
Tvoji podsjetnici|Your reminders|Deine Erinnerungen|=|Tvoji podsetnici|Твоите напомняния|Mementourile tale|Hatırlatıcıların|I tuoi promemoria|تذكيراتك
Nema podsjetnika.|No reminders.|Keine Erinnerungen.|=|Nema podsetnika.|Няма напомняния.|Niciun memento.|Hatırlatıcı yok.|Nessun promemoria.|لا توجد تذكيرات.
Obriši|Delete|Löschen|=|=|Изтрий|Șterge|Sil|Elimina|حذف
Backup je spremljen|Backup saved|Backup gespeichert|=|Bekap je sačuvan|Копието е запазено|Backup salvat|Yedek kaydedildi|Backup salvato|تم حفظ النسخة
Backup nije spremljen|Backup not saved|Backup nicht gespeichert|=|Bekap nije sačuvan|Копието не е запазено|Backup nesalvat|Yedek kaydedilmedi|Backup non salvato|لم تُحفظ النسخة
Sve je vraćeno. PRETORIAN se ponovo pokreće.|Everything restored. PRETORIAN restarts.|Alles wiederhergestellt. PRETORIAN startet neu.|=|=|Всичко е възстановено. PRETORIAN се рестартира.|Totul a fost restaurat. PRETORIAN repornește.|Her şey geri yüklendi. PRETORIAN yeniden başlıyor.|Tutto ripristinato. PRETORIAN si riavvia.|تمت الاستعادة. يُعاد تشغيل PRETORIAN.
Vraćanje nije uspjelo|Restore failed|Wiederherstellung fehlgeschlagen|=|Vraćanje nije uspelo|Възстановяването не успя|Restaurarea a eșuat|Geri yükleme başarısız|Ripristino non riuscito|فشلت الاستعادة
Backup i vraćanje|Backup and restore|Sichern und wiederherstellen|Sigurnosna kopija i vraćanje|Bekap i vraćanje|Резервно копие|Backup și restaurare|Yedekle ve geri yükle|Backup e ripristino|النسخ والاستعادة
Spašava playliste, grupe, favorite, izgled, ključeve, jezike, gledano i podsjetnike.|Saves playlists, groups, favourites, look, keys, languages, watched and reminders.|Sichert Playlists, Gruppen, Favoriten, Aussehen, Schlüssel, Sprachen, Gesehenes und Erinnerungen.|=|Čuva plejliste, grupe, omiljene, izgled, ključeve, jezike, gledano i podsetnike.|Пази плейлисти, групи, любими, изглед, ключове, езици, гледано, напомняния.|Salvează playlisturi, grupuri, favorite, aspect, chei, limbi, vizionate, mementouri.|Listeleri, grupları, favorileri, görünümü, anahtarları, dilleri, izlenenleri kaydeder.|Salva playlist, gruppi, preferiti, aspetto, chiavi, lingue, visti e promemoria.|يحفظ القوائم والمجموعات والمفضلة والمظهر والمفاتيح واللغات والتذكيرات.
Napravi backup|Make a backup|Backup erstellen|Napravi sigurnosnu kopiju|Napravi bekap|Направи копие|Fă un backup|Yedek oluştur|Crea backup|إنشاء نسخة
Izaberi folder na boxu ili USB-u|Choose a folder on the box or USB|Ordner auf Gerät oder USB wählen|Odaberi mapu na uređaju ili USB-u|=|Избери папка на устройството или USB|Alege un folder pe box sau USB|Cihazda veya USB'de klasör seç|Scegli una cartella sul box o USB|اختر مجلدًا على الجهاز أو USB
Vrati iz backupa|Restore from backup|Aus Backup wiederherstellen|Vrati iz sigurnosne kopije|Vrati iz bekapa|Възстанови от копие|Restaurează din backup|Yedekten geri yükle|Ripristina dal backup|استعادة من نسخة
Izaberi PRETORIAN backup fajl|Choose a PRETORIAN backup file|PRETORIAN-Backup-Datei wählen|Odaberi PRETORIAN datoteku|=|Избери файл на PRETORIAN|Alege un fișier backup PRETORIAN|PRETORIAN yedek dosyası seç|Scegli un file backup PRETORIAN|اختر ملف نسخة PRETORIAN
Automatski backup svake sedmice|Automatic weekly backup|Automatisches wöchentliches Backup|Automatska kopija svaki tjedan|Automatski bekap svake nedelje|Автоматично седмично копие|Backup automat săptămânal|Haftalık otomatik yedek|Backup automatico settimanale|نسخ تلقائي أسبوعي
U memoriji boxa, čuvaju se zadnja 3|In the box storage, the last 3 are kept|Im Gerätespeicher, die letzten 3 bleiben|=|=|В паметта, пазят се последните 3|În memorie, se păstrează ultimele 3|Cihazda, son 3 tutulur|Nella memoria del box, si tengono gli ultimi 3|في الذاكرة، تُحفظ آخر 3
Automatski backup|Automatic backup|Automatisches Backup|Automatska kopija|Automatski bekap|Автоматично копие|Backup automat|Otomatik yedek|Backup automatico|نسخة تلقائية
Vrati|Restore|Wiederherstellen|=|=|Възстанови|Restaurează|Geri yükle|Ripristina|استعادة
Backup sadrži lozinke playlista i API ključeve. Ne dijeli ga s drugima, ili ga zaštiti lozinkom.|The backup holds playlist passwords and API keys. Do not share it, or protect it with a password.|Das Backup enthält Passwörter und API-Schlüssel. Nicht teilen oder mit Passwort schützen.|=|Bekap sadrži lozinke plejlista i API ključeve. Ne deli ga ili ga zaštiti lozinkom.|Копието съдържа пароли и ключове. Не го споделяй или го защити.|Backup-ul conține parole și chei. Nu-l partaja sau protejează-l.|Yedek şifre ve anahtar içerir. Paylaşma veya şifrele.|Il backup contiene password e chiavi. Non condividerlo o proteggilo.|النسخة تحوي كلمات مرور ومفاتيح. لا تشاركها أو احمها بكلمة مرور.
Zaštititi backup lozinkom?|Protect the backup with a password?|Backup mit Passwort schützen?|Zaštititi kopiju lozinkom?|Zaštititi bekap lozinkom?|Защита с парола?|Protejezi backup-ul cu parolă?|Yedek şifrelensin mi?|Proteggere il backup con password?|حماية النسخة بكلمة مرور؟
Bez lozinke|Without password|Ohne Passwort|=|=|Без парола|Fără parolă|Şifresiz|Senza password|بدون كلمة مرور
Sa lozinkom|With password|Mit Passwort|S lozinkom|=|С парола|Cu parolă|Şifreli|Con password|بكلمة مرور
Lozinka za backup|Backup password|Backup-Passwort|Lozinka za kopiju|Lozinka za bekap|Парола за копието|Parolă backup|Yedek şifresi|Password del backup|كلمة مرور النسخة
Gdje spremiti backup?|Where to save the backup?|Wo speichern?|Gdje spremiti kopiju?|Gde sačuvati bekap?|Къде да се запази?|Unde salvezi backup-ul?|Yedek nereye kaydedilsin?|Dove salvare il backup?|أين تُحفظ النسخة؟
USB, Download, memorija boxa|USB, Downloads, box storage|USB, Downloads, Gerätespeicher|USB, Download, memorija uređaja|=|USB, изтегляния, памет|USB, descărcări, memorie|USB, indirilenler, bellek|USB, download, memoria|USB، التنزيلات، الذاكرة
Tvoj file manager|Your file manager|Dein Dateimanager|Tvoj upravitelj datoteka|=|Твоят файлов мениджър|Managerul tău de fișiere|Dosya yöneticin|Il tuo file manager|مدير ملفاتك
Izaberi folder|Choose a folder|Ordner wählen|Odaberi mapu|=|Избери папка|Alege folderul|Klasör seç|Scegli cartella|اختر مجلدًا
Spremljeno: %s|Saved: %s|Gespeichert: %s|=|Sačuvano: %s|Запазено: %s|Salvat: %s|Kaydedildi: %s|Salvato: %s|تم الحفظ: %s
U taj folder se ne može pisati. Izaberi drugi ili PRETORIAN folder.|Cannot write to that folder. Choose another or the PRETORIAN folder.|In diesen Ordner kann nicht geschrieben werden. Anderen oder PRETORIAN-Ordner wählen.|=|=|Не може да се пише там. Избери друга папка.|Nu se poate scrie acolo. Alege alt folder.|O klasöre yazılamıyor. Başka klasör seç.|Impossibile scrivere lì. Scegli un'altra cartella.|لا يمكن الكتابة هناك. اختر مجلدًا آخر.
Izaberi backup|Choose a backup|Backup wählen|Odaberi kopiju|Izaberi bekap|Избери копие|Alege backup-ul|Yedek seç|Scegli backup|اختر نسخة
Vratiti sve iz backupa?|Restore everything from the backup?|Alles aus dem Backup wiederherstellen?|Vratiti sve iz kopije?|Vratiti sve iz bekapa?|Възстановяване на всичко?|Restaurezi totul?|Her şey geri yüklensin mi?|Ripristinare tutto?|استعادة كل شيء؟
Datum: %s · playlista: %s · lista favorita: %s|Date: %s · playlists: %s · favourite lists: %s|Datum: %s · Playlists: %s · Favoritenlisten: %s|=|Datum: %s · plejlista: %s · lista omiljenih: %s|Дата: %s · плейлисти: %s · списъци: %s|Dată: %s · playlisturi: %s · liste: %s|Tarih: %s · liste: %s · favori listesi: %s|Data: %s · playlist: %s · liste preferiti: %s|التاريخ: %s · القوائم: %s · قوائم المفضلة: %s
Trenutne postavke će biti zamijenjene.|Current settings will be replaced.|Aktuelle Einstellungen werden ersetzt.|=|Trenutna podešavanja će biti zamenjena.|Текущите настройки ще бъдат заменени.|Setările actuale vor fi înlocuite.|Mevcut ayarlar değiştirilecek.|Le impostazioni attuali saranno sostituite.|سيتم استبدال الإعدادات الحالية.
Vrati sve|Restore all|Alles wiederherstellen|=|=|Възстанови всичко|Restaurează tot|Hepsini geri yükle|Ripristina tutto|استعادة الكل
Lozinka backupa|Backup password|Backup-Passwort|Lozinka kopije|Lozinka bekapa|Парола на копието|Parola backup-ului|Yedek şifresi|Password del backup|كلمة مرور النسخة
Jednostavni mod ostavlja samo TV, Filmove i Serije. Izlaz iz njega traži PIN.|Simple mode keeps only TV, Movies and Series. Leaving it needs the PIN.|Einfacher Modus zeigt nur TV, Filme und Serien. Verlassen braucht die PIN.|=|Jednostavni režim ostavlja samo TV, Filmove i Serije. Izlaz traži PIN.|Опростеният режим оставя само ТВ, Филми и Сериали. Изход с PIN.|Modul simplu păstrează doar TV, Filme și Seriale. Ieșirea cere PIN.|Basit mod yalnızca TV, Film ve Dizi bırakır. Çıkış PIN ister.|La modalità semplice lascia solo TV, Film e Serie. Per uscire serve il PIN.|الوضع البسيط يبقي التلفاز والأفلام والمسلسلات فقط. الخروج يتطلب PIN.
Za starije i djecu: bez postavki, ništa se ne može pokvariti|For elderly and children: no settings, nothing can break|Für Ältere und Kinder: keine Einstellungen, nichts geht kaputt|=|Za starije i decu: bez podešavanja, ništa se ne može pokvariti|За възрастни и деца: без настройки|Pentru vârstnici și copii: fără setări|Yaşlılar ve çocuklar için: ayar yok|Per anziani e bambini: niente impostazioni|لكبار السن والأطفال: بلا إعدادات
Ne traži stari PIN, samo novi|Does not ask for the old PIN, only a new one|Fragt nicht nach der alten PIN, nur nach einer neuen|=|=|Не иска стария PIN, само нов|Nu cere PIN-ul vechi, doar unul nou|Eski PIN'i sormaz, sadece yenisini|Non chiede il vecchio PIN, solo il nuovo|لا يطلب PIN القديم، فقط الجديد
4 cifre, za napredni mod i zaključane kanale|4 digits, for advanced mode and locked channels|4 Ziffern, für erweiterten Modus und gesperrte Sender|4 znamenke, za napredni mod i zaključane kanale|4 cifre, za napredni režim i zaključane kanale|4 цифри, за разширен режим и заключени канали|4 cifre, pentru modul avansat|4 hane, gelişmiş mod ve kilitli kanallar için|4 cifre, per modalità avanzata e canali bloccati|4 أرقام، للوضع المتقدم والقنوات المقفلة
Zaključavanje grupa i kanala: TV › drži OK na grupi ili kanalu › Zaključaj PIN-om, ili Postavke › Grupe i kanali.|Lock groups and channels: TV › hold OK on a group or channel › Lock with PIN, or Settings › Groups and channels.|Gruppen und Sender sperren: TV › OK halten › Mit PIN sperren, oder Einstellungen › Gruppen und Sender.|=|Zaključavanje: TV › drži OK na grupi ili kanalu › Zaključaj PIN-om, ili Podešavanja › Grupe i kanali.|Заключване: ТВ › задръж OK › Заключи с PIN, или Настройки › Групи и канали.|Blocare: TV › ține OK › Blochează cu PIN, sau Setări › Grupuri și canale.|Kilitleme: TV › OK basılı › PIN ile kilitle, veya Ayarlar › Gruplar ve kanallar.|Blocco: TV › tieni OK › Blocca con PIN, o Impostazioni › Gruppi e canali.|القفل: التلفاز › اضغط مطولاً › قفل بـ PIN، أو الإعدادات › المجموعات والقنوات.
Instalirana verzija: %s. Provjera ide sama pri pokretanju i svakih 6 sati.|Installed version: %s. Checked at start and every 6 hours.|Installierte Version: %s. Prüfung beim Start und alle 6 Stunden.|=|Instalirana verzija: %s. Provera ide sama pri pokretanju i na svakih 6 sati.|Инсталирана версия: %s. Проверка при старт и на 6 часа.|Versiune instalată: %s. Verificare la pornire și la 6 ore.|Yüklü sürüm: %s. Başlangıçta ve 6 saatte bir kontrol.|Versione installata: %s. Controllo all'avvio e ogni 6 ore.|الإصدار المثبت: %s. يُفحص عند البدء وكل 6 ساعات.
Jedan klik: preuzima se i odmah instalira|One click: downloads and installs right away|Ein Klick: lädt und installiert sofort|=|=|Един клик: изтегля и инсталира|Un clic: descarcă și instalează|Tek tık: indirir ve kurar|Un clic: scarica e installa|نقرة واحدة: تنزيل وتثبيت
%s utakmica|%s matches|%s Spiele|=|=|%s мача|%s meciuri|%s maç|%s partite|%s مباراة
%s uživo|%s live|%s live|=|=|%s на живо|%s live|%s canlı|%s in diretta|%s مباشر
ažurirano %s|updated %s|aktualisiert %s|=|=|обновено %s|actualizat %s|güncellendi %s|aggiornato %s|حُدّث %s
Trenutno nema utakmica uživo|No live matches right now|Gerade keine Live-Spiele|=|=|В момента няма мачове на живо|Momentan niciun meci live|Şu an canlı maç yok|Nessuna partita in diretta ora|لا توجد مباريات مباشرة حاليًا
Mogući prenos: %s|Possible broadcast: %s|Mögliche Übertragung: %s|=|=|Възможно предаване: %s|Transmisiune posibilă: %s|Olası yayın: %s|Possibile trasmissione: %s|بث محتمل: %s
Detalji|Details|Details|=|=|Подробности|Detalii|Ayrıntılar|Dettagli|التفاصيل
PRENOSE · |ON AIR · |ÜBERTRAGUNG · |=|=|ПРЕДАВАТ · |TRANSMIT · |YAYINLAYAN · |IN ONDA SU · |تُبث على · 
Mogući prenos (po ligi, nije sigurno)|Possible broadcast (by league, not certain)|Mögliche Übertragung (nach Liga, nicht sicher)|=|=|Възможно предаване (по лига, не е сигурно)|Transmisiune posibilă (după ligă, nesigur)|Olası yayın (ligine göre, kesin değil)|Possibile trasmissione (per campionato, non certa)|بث محتمل (حسب الدوري، غير مؤكد)
Pretraženo %s kanala · program za %s · %s emisija|Searched %s channels · guide for %s · %s shows|%s Sender durchsucht · Programm für %s · %s Sendungen|=|=|Претърсени %s канала · програма за %s · %s предавания|Căutate %s canale · ghid pentru %s · %s emisiuni|%s kanal tarandı · %s için rehber · %s program|Cercati %s canali · guida per %s · %s programmi|بُحث في %s قناة · دليل لـ %s · %s برنامج
Novih filmova: %s|New movies: %s|Neue Filme: %s|=|=|Нови филми: %s|Filme noi: %s|Yeni filmler: %s|Nuovi film: %s|أفلام جديدة: %s
Novih serija: %s|New series: %s|Neue Serien: %s|=|=|Нови сериали: %s|Seriale noi: %s|Yeni diziler: %s|Nuove serie: %s|مسلسلات جديدة: %s
Link do .srt ili .zip titla|Link to a .srt or .zip subtitle|Link zu .srt- oder .zip-Untertitel|=|=|Линк към .srt или .zip|Link spre .srt sau .zip|.srt veya .zip bağlantısı|Link a .srt o .zip|رابط ملف .srt أو .zip
Titl sa diska ili USB-a|Subtitle from disk or USB|Untertitel von Speicher oder USB|=|=|Субтитри от диск или USB|Subtitrare de pe disc sau USB|Diskten veya USB'den altyazı|Sottotitolo da disco o USB|ترجمة من القرص أو USB
Prvo upiši API ključ: Postavke › Filmovi i titlovi|First enter an API key: Settings › Movies and subtitles|Zuerst API-Schlüssel eintragen: Einstellungen › Filme und Untertitel|=|Prvo upiši API ključ: Podešavanja › Filmovi i titlovi|Първо въведи API ключ: Настройки › Филми и субтитри|Mai întâi cheia API: Setări › Filme și subtitrări|Önce API anahtarı gir: Ayarlar › Filmler ve altyazılar|Prima inserisci la chiave API: Impostazioni › Film e sottotitoli|أدخل مفتاح API أولًا: الإعدادات › الأفلام والترجمة
Izaberi 4 cifre. Trebaće za napredni mod i zaključane kanale.|Choose 4 digits. Needed for advanced mode and locked channels.|Wähle 4 Ziffern. Für erweiterten Modus und gesperrte Sender.|Odaberi 4 znamenke. Trebat će za napredni mod i zaključane kanale.|Izaberi 4 cifre. Trebaće za napredni režim i zaključane kanale.|Избери 4 цифри. За разширен режим и заключени канали.|Alege 4 cifre. Pentru modul avansat și canale blocate.|4 hane seç. Gelişmiş mod ve kilitli kanallar için.|Scegli 4 cifre. Per modalità avanzata e canali bloccati.|اختر 4 أرقام. للوضع المتقدم والقنوات المقفلة.
Unesi isti PIN još jednom|Enter the same PIN again|Gleiche PIN noch einmal eingeben|=|=|Въведи същия PIN пак|Introdu același PIN din nou|Aynı PIN'i tekrar gir|Inserisci di nuovo lo stesso PIN|أدخل نفس PIN مرة أخرى
Nova lista favorita|New favourites list|Neue Favoritenliste|Novi popis favorita|Nova lista omiljenih|Нов списък любими|Listă nouă de favorite|Yeni favori listesi|Nuova lista preferiti|قائمة مفضلة جديدة
Naziv (npr. Dokumentarni, Sport, Bosna)|Name (e.g. Documentary, Sport, Bosnia)|Name (z. B. Doku, Sport, Bosnien)|=|=|Име (напр. Документални, Спорт)|Nume (ex. Documentare, Sport)|Ad (ör. Belgesel, Spor)|Nome (es. Documentari, Sport)|الاسم (مثل وثائقي، رياضة)
Naziv (npr. Moji, Supruga, Djeca, Sport)|Name (e.g. Mine, Wife, Kids, Sport)|Name (z. B. Meine, Frau, Kinder, Sport)|=|Naziv (npr. Moji, Supruga, Deca, Sport)|Име (напр. Мои, Съпруга, Деца)|Nume (ex. Ale mele, Soția, Copii)|Ad (ör. Benim, Eşim, Çocuklar)|Nome (es. Miei, Moglie, Bambini)|الاسم (مثل لي، الزوجة، الأطفال)
Preimenuj listu|Rename list|Liste umbenennen|=|=|Преименувай списъка|Redenumește lista|Listeyi yeniden adlandır|Rinomina lista|إعادة تسمية القائمة
Obrisati listu?|Delete list?|Liste löschen?|=|=|Изтриване на списъка?|Ștergi lista?|Liste silinsin mi?|Eliminare la lista?|حذف القائمة؟
Kanali ostaju u playlisti, briše se samo ova lista favorita.|Channels stay in the playlist, only this favourites list is deleted.|Sender bleiben, nur diese Favoritenliste wird gelöscht.|=|Kanali ostaju u plejlisti, briše se samo ova lista omiljenih.|Каналите остават, изтрива се само списъкът.|Canalele rămân, se șterge doar lista.|Kanallar kalır, sadece bu liste silinir.|I canali restano, si elimina solo questa lista.|تبقى القنوات، تُحذف هذه القائمة فقط.
Da, obriši listu|Yes, delete the list|Ja, Liste löschen|=|=|Да, изтрий списъка|Da, șterge lista|Evet, listeyi sil|Sì, elimina la lista|نعم، احذف القائمة
Obriši listu|Delete list|Liste löschen|=|=|Изтрий списъка|Șterge lista|Listeyi sil|Elimina lista|حذف القائمة
OK gledaj · drži OK = opcije · ◀ grupe|OK watch · hold OK = options · ◀ groups|OK ansehen · OK halten = Optionen · ◀ Gruppen|=|=|OK гледай · задръж OK = опции · ◀ групи|OK vezi · ține OK = opțiuni · ◀ grupuri|OK izle · OK basılı = seçenekler · ◀ gruplar|OK guarda · tieni OK = opzioni · ◀ gruppi|OK مشاهدة · مطولاً = خيارات · ◀ المجموعات
Na kanalu drži OK › Dodaj u favorite|Hold OK on a channel › Add to favourites|Auf einem Sender OK halten › Zu Favoriten|=|Na kanalu drži OK › Dodaj u omiljene|Задръж OK на канал › Добави в любими|Ține OK pe canal › Adaugă la favorite|Kanalda OK basılı › Favorilere ekle|Tieni OK su un canale › Aggiungi ai preferiti|اضغط مطولاً على قناة › أضف للمفضلة
Kanale možeš prebaciti ovamo preko opcija kanala|You can move channels here from the channel options|Sender über die Senderoptionen hierher verschieben|=|=|Премести канали тук от опциите|Poți muta canale aici din opțiuni|Kanalları seçeneklerden buraya taşı|Sposta qui i canali dalle opzioni|انقل القنوات هنا من خياراتها
Uklonjeno iz liste %s|Removed from list %s|Aus Liste %s entfernt|=|=|Премахнато от %s|Eliminat din lista %s|%s listesinden çıkarıldı|Rimosso dalla lista %s|أزيل من قائمة %s
Dodano u listu %s|Added to list %s|Zur Liste %s hinzugefügt|=|Dodato u listu %s|Добавено в %s|Adăugat în lista %s|%s listesine eklendi|Aggiunto alla lista %s|أضيف إلى قائمة %s
Playlista|Playlist|Playlist|=|Plejlista|Плейлист|Playlist|Liste|Playlist|القائمة
Nema|None|Keine|=|=|Няма|Niciunul|Yok|Nessuno|لا يوجد
Direktni strim|Direct stream|Direkter Stream|=|=|Директен поток|Flux direct|Doğrudan yayın|Stream diretto|بث مباشر
Nije naveden|Not stated|Nicht angegeben|=|=|Не е посочено|Nespecificat|Belirtilmemiş|Non indicato|غير محدد
Da|Yes|Ja|=|=|Да|Da|Evet|Sì|نعم
Ne|No|Nein|=|=|Не|Nu|Hayır|No|لا
Kanal "%s" nestaje iz liste. Vraćaš ga u Postavke › Grupe i kanali › Vrati sakrivene kanale.|Channel "%s" leaves the list. Bring it back in Settings › Groups and channels › Restore hidden channels.|Sender "%s" verschwindet. Zurück über Einstellungen › Gruppen und Sender › Ausgeblendete zurück.|=|Kanal "%s" nestaje iz liste. Vraćaš ga u Podešavanja › Grupe i kanali › Vrati sakrivene kanale.|Каналът "%s" изчезва. Върни го от Настройки › Групи и канали.|Canalul "%s" dispare. Îl readuci din Setări › Grupuri și canale.|"%s" kanalı listeden çıkar. Ayarlar › Gruplar ve kanallar'dan geri getir.|Il canale "%s" sparisce. Lo ripristini in Impostazioni › Gruppi e canali.|تختفي القناة "%s". أعدها من الإعدادات › المجموعات والقنوات.
Naziv nove grupe|Name of the new group|Name der neuen Gruppe|=|=|Име на новата група|Numele grupului nou|Yeni grubun adı|Nome del nuovo gruppo|اسم المجموعة الجديدة
Premješteno u %s|Moved to %s|Verschoben nach %s|=|Premešteno u %s|Преместено в %s|Mutat în %s|%s grubuna taşındı|Spostato in %s|نُقل إلى %s
Ukloni iz ove liste|Remove from this list|Aus dieser Liste entfernen|=|=|Премахни от този списък|Elimină din această listă|Bu listeden çıkar|Rimuovi da questa lista|إزالة من هذه القائمة
Dodaj Xtream Codes ili M3U playlistu|Add an Xtream Codes or M3U playlist|Xtream-Codes- oder M3U-Playlist hinzufügen|=|Dodaj Xtream Codes ili M3U plejlistu|Добави Xtream Codes или M3U плейлист|Adaugă un playlist Xtream Codes sau M3U|Xtream Codes veya M3U listesi ekle|Aggiungi una playlist Xtream Codes o M3U|أضف قائمة Xtream Codes أو M3U
MOJA|MINE|MEINE|=|=|МОЯ|A MEA|BENİM|MIA|خاصتي
Briše tvoj redoslijed, nazive, sakrivene i zaključane grupe i kanale, i tvoje grupe za ovu playlistu.|Deletes your order, names, hidden and locked groups and channels, and your groups for this playlist.|Löscht Reihenfolge, Namen, ausgeblendete und gesperrte Gruppen und Sender sowie eigene Gruppen.|=|Briše tvoj redosled, nazive, sakrivene i zaključane grupe i kanale, i tvoje grupe.|Изтрива реда, имената, скритите и заключените групи и канали.|Șterge ordinea, numele, grupurile și canalele ascunse și blocate.|Sıralamayı, adları, gizli ve kilitli grupları ve kanalları siler.|Elimina ordine, nomi, gruppi e canali nascosti e bloccati.|يحذف الترتيب والأسماء والمخفي والمقفل.
OK = podsjeti me|OK = remind me|OK = erinnern|=|OK = podseti me|OK = напомни|OK = amintește-mi|OK = hatırlat|OK = ricordamelo|OK = ذكّرني
Sezona %s|Season %s|Staffel %s|=|=|Сезон %s|Sezonul %s|Sezon %s|Stagione %s|الموسم %s
Serije nisu dostupne|Series not available|Serien nicht verfügbar|=|=|Сериалите не са налични|Seriale indisponibile|Diziler yok|Serie non disponibili|المسلسلات غير متاحة
Filmovi nisu dostupni|Movies not available|Filme nicht verfügbar|=|=|Филмите не са налични|Filme indisponibile|Filmler yok|Film non disponibili|الأفلام غير متاحة
Filmovi i serije rade s Xtream Codes playlistom.|Movies and series work with an Xtream Codes playlist.|Filme und Serien gehen mit einer Xtream-Codes-Playlist.|=|Filmovi i serije rade sa Xtream Codes plejlistom.|Филмите работят с Xtream Codes плейлист.|Filmele merg cu un playlist Xtream Codes.|Filmler Xtream Codes listesiyle çalışır.|Film e serie funzionano con playlist Xtream Codes.|الأفلام تعمل مع قائمة Xtream Codes.
Novo dodano|Recently added|Neu hinzugefügt|=|Novo dodato|Наскоро добавени|Adăugate recent|Yeni eklenenler|Aggiunti di recente|أضيف حديثًا
Sortiraj|Sort|Sortieren|=|=|Сортирай|Sortează|Sırala|Ordina|ترتيب
Kao kod providera|Provider order|Wie beim Anbieter|=|=|Както при доставчика|Ca la furnizor|Sağlayıcı sırası|Come il provider|ترتيب المزود
Ocjena|Rating|Bewertung|=|Ocena|Оценка|Notă|Puan|Voto|التقييم
Sve kategorije|All categories|Alle Kategorien|=|=|Всички категории|Toate categoriile|Tüm kategoriler|Tutte le categorie|كل الفئات
Prikaži sve|See all|Alle anzeigen|=|=|Покажи всички|Vezi tot|Tümünü gör|Vedi tutto|عرض الكل
NOVO|NEW|NEU|=|=|НОВО|NOU|YENİ|NUOVO|جديد
%s nova|%s new|%s neu|=|=|%s нови|%s noi|%s yeni|%s nuovi|%s جديد
Drži OK na kategoriji za opcije|Hold OK on a category for options|OK auf Kategorie halten für Optionen|=|=|Задръж OK на категория за опции|Ține OK pe categorie pentru opțiuni|Seçenekler için kategoride OK basılı tut|Tieni OK su una categoria per le opzioni|اضغط مطولاً على فئة للخيارات
Označi sve kao viđeno|Mark all as seen|Alle als gesehen markieren|=|=|Маркирай всички като видени|Marchează toate ca văzute|Hepsini görüldü işaretle|Segna tutto come visto|تعليم الكل كمرئي
Označi kao negledano|Mark as unwatched|Als ungesehen markieren|=|=|Маркирай като негледано|Marchează ca nevizionat|İzlenmedi olarak işaretle|Segna come non visto|تعليم كغير مشاهد
Ukloni oznaku NOVO|Remove NEW badge|NEU-Markierung entfernen|=|=|Премахни НОВО|Elimină eticheta NOU|YENİ etiketini kaldır|Rimuovi etichetta NUOVO|إزالة شارة جديد
Epizoda %s|Episode %s|Folge %s|=|=|Епизод %s|Episodul %s|Bölüm %s|Episodio %s|الحلقة %s
Glumci: %s|Cast: %s|Besetzung: %s|=|=|Актьори: %s|Actori: %s|Oyuncular: %s|Cast: %s|الممثلون: %s
Režija: %s|Director: %s|Regie: %s|=|=|Режисьор: %s|Regia: %s|Yönetmen: %s|Regia: %s|الإخراج: %s
Gledano|Watched|Gesehen|=|=|Гледано|Vizionat|İzlendi|Visto|تمت المشاهدة
Eksterni player se zatvorio|The external player closed|Externer Player wurde geschlossen|Vanjski player se zatvorio|Eksterni plejer se zatvorio|Външният плейър се затвори|Playerul extern s-a închis|Harici oynatıcı kapandı|Il player esterno si è chiuso|أُغلق المشغل الخارجي
Izgleda da player nije mogao pustiti film.|It looks like the player could not play the movie.|Der Player konnte den Film wohl nicht abspielen.|=|Izgleda da plejer nije mogao pustiti film.|Изглежда плейърът не можа да пусне филма.|Se pare că playerul nu a putut reda filmul.|Oynatıcı filmi oynatamamış görünüyor.|Sembra che il player non abbia riprodotto il film.|يبدو أن المشغل لم يستطع تشغيل الفيلم.
Pusti u PRETORIAN plejeru|Play in the PRETORIAN player|Im PRETORIAN-Player abspielen|Pusti u PRETORIAN playeru|=|Пусни в плейъра на PRETORIAN|Redă în playerul PRETORIAN|PRETORIAN oynatıcısında oynat|Riproduci nel player PRETORIAN|شغّل في مشغل PRETORIAN
Izaberi drugi player|Choose another player|Anderen Player wählen|Odaberi drugi player|Izaberi drugi plejer|Избери друг плейър|Alege alt player|Başka oynatıcı seç|Scegli un altro player|اختر مشغلًا آخر
Pratiš|Following|Folgst du|=|=|Следиш|Urmărești|Takip ediliyor|Segui|تتابع
Prati|Follow|Folgen|=|=|Следи|Urmărește|Takip et|Segui|تابِع
Javiću kad izađe nova epizoda|I will tell you when a new episode comes out|Ich melde mich bei einer neuen Folge|Javit ću kad izađe nova epizoda|=|Ще те известя при нов епизод|Te anunț când apare un episod nou|Yeni bölüm çıkınca haber vereceğim|Ti avviso quando esce un nuovo episodio|سأخبرك عند صدور حلقة جديدة
Ne pratiš više ovu seriju|You no longer follow this series|Du folgst dieser Serie nicht mehr|=|=|Вече не следиш сериала|Nu mai urmărești serialul|Bu diziyi artık takip etmiyorsun|Non segui più questa serie|لم تعد تتابع هذا المسلسل
%s od %s|%s of %s|%s von %s|=|=|%s от %s|%s din %s|%s / %s|%s di %s|%s من %s
Pusti|Play|Abspielen|=|=|Пусни|Redă|Oynat|Riproduci|تشغيل
Označi sezonu kao gledanu|Mark season as watched|Staffel als gesehen markieren|=|=|Маркирай сезона като гледан|Marchează sezonul ca vizionat|Sezonu izlendi işaretle|Segna stagione come vista|تعليم الموسم كمشاهد
Emisije, utakmice, obavještenja|Shows, matches, notifications|Sendungen, Spiele, Benachrichtigungen|Emisije, utakmice, obavijesti|Emisije, utakmice, obaveštenja|Предавания, мачове, известия|Emisiuni, meciuri, notificări|Programlar, maçlar, bildirimler|Programmi, partite, notifiche|برامج، مباريات، إشعارات
Spasi sve postavke u fajl|Save all settings to a file|Alle Einstellungen in Datei sichern|Spremi sve postavke u datoteku|Sačuvaj sva podešavanja u fajl|Запази всичко във файл|Salvează totul într-un fișier|Tüm ayarları dosyaya kaydet|Salva tutto in un file|احفظ كل الإعدادات في ملف
Tanke|Thin|Dünn|=|=|Тънки|Subțiri|İnce|Sottili|رفيعة
Normalne|Normal|Normal|=|=|Нормални|Normale|Normal|Normali|عادية
Debele|Bold|Fett|=|=|Дебели|Groase|Kalın|Spesse|عريضة
Popunjene|Filled|Gefüllt|=|=|Запълнени|Pline|Dolu|Piene|ممتلئة
Čiste linije · ISC|Clean lines · ISC|Klare Linien · ISC|=|=|Чисти линии · ISC|Linii curate · ISC|Temiz çizgiler · ISC|Linee pulite · ISC|خطوط نظيفة · ISC
Mekane, moderne · MIT|Soft, modern · MIT|Weich, modern · MIT|=|=|Меки, модерни · MIT|Moi, moderne · MIT|Yumuşak, modern · MIT|Morbide, moderne · MIT|ناعمة وحديثة · MIT
Oštre linije · MIT|Sharp lines · MIT|Scharfe Linien · MIT|=|=|Остри линии · MIT|Linii ascuțite · MIT|Keskin çizgiler · MIT|Linee nette · MIT|خطوط حادة · MIT
Pretorian crvena|Pretorian red|Pretorian-Rot|=|=|Преториан червено|Roșu Pretorian|Pretorian kırmızısı|Rosso Pretorian|أحمر بريتوريان
Plava|Blue|Blau|=|=|Синьо|Albastru|Mavi|Blu|أزرق
Zelena|Green|Grün|=|=|Зелено|Verde|Yeşil|Verde|أخضر
Narandžasta|Orange|Orange|Narančasta|=|Оранжево|Portocaliu|Turuncu|Arancione|برتقالي
Ljubičasta|Purple|Lila|=|=|Лилаво|Mov|Mor|Viola|بنفسجي
Tirkizna|Teal|Türkis|=|=|Тюркоаз|Turcoaz|Turkuaz|Turchese|فيروزي
Roza|Pink|Rosa|Ružičasta|=|Розово|Roz|Pembe|Rosa|وردي
Zlatna|Gold|Gold|=|=|Златно|Auriu|Altın|Oro|ذهبي
UEFA Liga prvaka|UEFA Champions League|UEFA Champions League|UEFA Liga prvaka|UEFA Liga šampiona|Шампионска лига|Liga Campionilor|UEFA Şampiyonlar Ligi|UEFA Champions League|دوري أبطال أوروبا
UEFA Liga Evrope|UEFA Europa League|UEFA Europa League|UEFA Europska liga|UEFA Liga Evrope|Лига Европа|Europa League|UEFA Avrupa Ligi|UEFA Europa League|الدوري الأوروبي
UEFA Konferencijska liga|UEFA Conference League|UEFA Conference League|UEFA Konferencijska liga|=|Лига на конференциите|Conference League|UEFA Konferans Ligi|UEFA Conference League|دوري المؤتمر
Austrijska Bundesliga|Austrian Bundesliga|Österreichische Bundesliga|=|=|Австрийска Бундеслига|Bundesliga Austria|Avusturya Bundesliga|Bundesliga austriaca|الدوري النمساوي
UEFA Liga nacija|UEFA Nations League|UEFA Nations League|=|=|Лига на нациите|Liga Națiunilor|UEFA Uluslar Ligi|UEFA Nations League|دوري الأمم
Kvalifikacije SP (Evropa)|World Cup qualifiers (Europe)|WM-Qualifikation (Europa)|Kvalifikacije SP (Europa)|=|Квалификации за СП (Европа)|Calificări CM (Europa)|Dünya Kupası elemeleri (Avrupa)|Qualificazioni Mondiali (Europa)|تصفيات كأس العالم (أوروبا)
Prijateljske utakmice|Friendlies|Freundschaftsspiele|=|=|Приятелски мачове|Amicale|Hazırlık maçları|Amichevoli|مباريات ودية
Preuzeti|Downloaded|Heruntergeladen|=|=|Изтеглени|Descărcate|İndirilen|Scaricati|منزّلة
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
