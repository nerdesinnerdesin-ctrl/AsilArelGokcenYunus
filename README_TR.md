# Hapis Tavla (Android)

İki ayrı tavla oyunu: **Hapis Tavla** (Mahbus / Plakoto) ve **Klasik Tavla** (Backgammon). Açılışta oyunu seçersin; kurallar, çevrimiçi masalar ve oda kodları birbirinden tamamen ayrıdır. Bilgisayara karşı ya da çevrimiçi oynanır. Arayüz Türkçedir. Oyun yatay (landscape) çalışır, en altta AdMob banner reklamı bulunur.

## APK'yı almanın en kolay yolu (GitHub, bilgisayara hiçbir şey kurmadan)

1. github.com'da ücretsiz hesap aç, **New repository** ile boş bir depo oluştur.
2. Zip'i bilgisayarda **Klasöre çıkar**. Klasörün **içindeki her şeyi** (`.github`, `app`, `gradle` klasörleri ve dosyalar) depoya yükle: **Add file > Upload files**, sürükle-bırak, **Commit changes**.
   - Klasörler yüklenmezse: Windows'ta `.github` ve `app` klasörlerini tek tek sürükle ya da zip'teki dosyaları GitHub'da **Create new file** ile yol yazarak ekle.
   - Depoda daha önce boş bir `.github/workflows/main.yml` varsa: dosyayı aç, sağ üstteki **kalem (Edit)** simgesine bas, içindekileri sil, zip'teki `.github/workflows/main.yml` içeriğini yapıştır ve **Commit changes** de.
3. **Actions** sekmesine gir. "APK derle" çalışır (3-6 dakika). Başlamazsa soldan "APK derle" > **Run workflow**.
4. Yeşil tikten sonra çalışmaya tıkla, en alttaki **Artifacts** bölümünden `hapis-tavla-apk` dosyasını indir. İçinden `app-debug.apk` çıkar.
5. APK'yı telefona aktar ve aç (bilinmeyen kaynaklardan yükleme iznini ver).

## Çevrimiçi oynama

Ana ekranda oyunu seç > **Çevrimiçi oyna** altında: **Masa aç** (rakip bekle; masan herkesin gördüğü **Açık masalar** listesinde görünür), **Açık masalar** (birinin masasına katıl), **Hızlı eşleş** vardır; arkadaşını Profil > Arkadaşlar'dan davet edebilirsin. Sunucu kurmak gerekmez: oyun, herkese açık ücretsiz MQTT sunucularının (HiveMQ, EMQX, Mosquitto) hepsine birden bağlanıp mesajları bunlar üzerinden iletir (şifreli bağlantı denenir). Mesajlar yalnızca oyun hamlelerini içerir; ancak bu ortak sunucular güvence altında değildir, ciddi bir yayın için kendi sunucunuza geçmeniz önerilir.

## Profil, arkadaşlar ve diğer özellikler

- **Açılış menüsü:** uygulama menü ekranıyla açılır; oyun modunu seçince tahta gelir. Oyundaki MENÜ düğmesi aynı ekrana döner.
- **Profil:** puan/seviye (Çırak → Efsane), istatistikler, 10 başarım, günlük bonus (üst üste girdikçe artar). Yalnızca bilgisayara ve çevrimiçi oyunlar sayılır. Veriler telefonda saklanır.
- **Arkadaşlar:** Sağ üstteki profil simgesi > Arkadaşlar. Herkesin 8 haneli bir oyuncu kodu vardır; kodu WhatsApp, Instagram vb. ile paylaşabilirsin. Arkadaşını oyuna davet edebilirsin (davet, uygulaması açık olan arkadaşa ulaşır). Çevrimiçi maçtan sonra rakibi tek dokunuşla ekleyebilirsin.
- **Hızlı mesajlar** (çevrimiçi oyunda), **ses efektleri** (menüden kapatılabilir) ve **3 tahta teması** (Ceviz, Abanoz: seviye 5, Zümrüt: seviye 10).
- **Google/Facebook ile giriş ve bulutta kayıt:** `KURULUM_GIRIS.md` dosyasındaki tek seferlik kurulumdan sonra çalışır. Kurulum yapılmazsa oyun misafir olarak çalışır. Instagram giriş sunmaz; onun yerine oyuncu kodunu paylaşma düğmesi vardır.

## Oyun kuralları (özet)

- Her oyuncunun 15 taşı vardır; taş vurulmaz, tek taşın üstüne basınca o taş **hapsolur**.
- İlk elde her oyuncu en az 2 farklı taş oynar.
- Sonraki ellerde bir taşla iki zarın toplamı tek dokunuşla oynanabilir (ör. 11).

## Android Studio ile derlemek

1. Android Studio'yu kur, **Open** ile bu klasörü aç, Gradle eşitlemesinin bitmesini bekle.
2. Telefonu USB hata ayıklama açıkken bağlayıp **Run** de ya da **Build > Build APK(s)** ile APK üret
   (`app/build/outputs/apk/debug/app-debug.apk`).

## AdMob reklam kimlikleri

Proje şu an Google'ın **test kimlikleriyle** çalışır; test reklamları görünür, gelir olmaz.
Gerçek kimliklere geçmek için `app/src/main/res/values/strings.xml` dosyasında iki değeri değiştir:

- `admob_app_id`: AdMob'daki **uygulama** kimliği (`ca-app-pub-XXXX~YYYY`)
- `admob_interstitial_unit_id`: AdMob'daki **geçiş (tam sayfa) reklam birimi** kimliği (`ca-app-pub-XXXX/ZZZZ`)

Geliştirirken gerçek kimlikle kendi reklamına tıklama; hesabın askıya alınabilir. Yayına çıkmadan önce AB/Birleşik Krallık kullanıcıları için onay (UMP/consent) ekranı ve Play Console'daki reklam kimliği beyanı gerekebilir.

## Dosyalar

- `app/src/main/java/com/hapistavla/oyun/Board.java`: oyunun kuralları (Android'den bağımsız)
- `Ai.java`: bilgisayar rakibi
- `Game.java`: oyun akışı; `Online.java`/`Mqtt.java`: çevrimiçi oyun
- `Cloud.java`/`Json.java`: hesap girişi ve bulut eşitleme
- `Profile.java`: profil/arkadaşlar; `Sfx.java`: ses efektleri
- `Gfx.java`: ahşap tahta ve taş çizimi; `TahtaView.java`: ekran ve dokunma
- `MainActivity.java`: ekran, AdMob banner'ı


## Klasik Tavla kuralları (Hapis Tavla'dan ayrıdır)
Standart dizilişle başlar; rakibin tek taşı vurulur ve bara gider, barda taş varken önce girmek gerekir; 15 taş eve girince toplanır; ilk toplayan kazanır (1 puan, rakip hiç toplayamadıysa mars 2 puan). Katlama küpü ve backgammon (3 puan) yoktur. "Ev tahtasında vur-kaç yasağı" gibi yerel varyantlar uygulanmamıştır.

## Ana ekran
İki oyun kartı + sağ üstte **profil** (arkadaşlar, giriş yap / hesap oluştur, ad, istatistikler) ve **ayarlar** (tema, ses) simgeleri. "Aynı telefonda iki kişi" modu kaldırıldı.

## Oyun ekranı
- Kenar paneller daraltıldı, tahta genişledi. Solda sadece taş simgesi ve puan; sağda Menü, Geri al, İpucu, Kurallar, Oto topla, Çıkış düğmeleri.
- **Çevrimiçi oyunda** sağ altta hazır emoji (6 yüz ifadesi) ve kısa mesajlar bulunur; rakibe dokunuşla gider, rakipte balon olarak 3 saniye görünür.
- **Oto topla:** Açıkken, taş toplamak tek seçenekse tek dokunuşla toplanır; oynanabilecek tek hamle taş toplamaksa oyun kendisi oynar. Ayarlardan da açılıp kapanır.
- **Maç hedefi:** Oyun seçildikten sonra "MAÇ: 1 / 3 / 5 PUAN" düğmesi. Bir taraf hedef puana ulaşınca maç biter (mars 2 puandır). Çevrimiçi oyunda masayı kuranın seçimi geçerlidir.
- **Çıkış:** Onay sorar; çevrimiçi oyunda rakibe haber gider.
- **Açık masalar:** Her masa bir kart: oturan oyuncunun adı, tahta ve boş sandalye. Karta dokununca masaya oturursun.

## Çevrimiçi: süre ve bağlantı kopması
- **Hamle süresi:** Her el 30 saniyedir (zar atma dahil). Süre dolunca oyun senin yerine zarı atar ve en iyi görünen hamleleri oynar. Üst üste 3 kez süre dolarsa oyunu kaybedersin. Sayaç, sıradaki oyuncunun kartında ince bir çubuk olarak görünür (son 10 saniyede kırmızı).
- **Bağlantı kopması:** Bağlantısı kopan oyuncuya 1 dakika tanınır; karşı tarafta geri sayım görünür ve sayaç durur. Dönerse oyun kaldığı yerden devam eder (hamle sayacıyla durum eşitlenir). Dönmezse oyunu kaybeder, diğer taraf tüm maçı kazanır.
- **Bilerek çıkış:** Oyun sürerken "Çıkış" oyunu kaybettirir (önce onay sorulur).
- Bilgisayara karşı oyunda süre sayacı yoktur.

## Son eklemeler
- 101 Okey kural seçenekleri (kurulum ekranında açık/kapalı anahtarlar, sırasıyla EŞLİ, KATLAMA, CEZA; hepsi varsayılan kapalı): eşli (karşılıklı oturanlar takım, takım toplamı), katlama (okeyle bitirme ×2, elden bitirme ×2, ikisi ×4), ceza (okey atmak / işlenebilir taş atmak +101). Seçenekler çevrimiçi masa listesinde "Eşli · Katlamalı · Cezalı" olarak görünür. Oyun içinde sağ üstteki EL kutusuna dokununca PUAN TABLOSU açılır (her el, C = ceza, K = katlama, takım toplamı). Kurallar metinleri de güncellendi.
- Alt banner reklam kaldırıldı; oyun alanı ekranın tamamını kullanıyor. Oyun (maç) bitince tam sayfa reklam gösterilir (en sık 2 dakikada bir; reklam hazır değilse atlanır).
- Okey / 101 Okey: Istakada her gerçek perin (geçerli seri/takım) üstünde altın bir tutamaç çubuğu çıkar. Tutamaçtan sürüklersen per bütün olarak taşınır; taşı tutarsan tek taş taşınır. Per olmayan yan yana taşlar bütün taşınmaz. Takas yok: taşlar araya girer, yer yoksa taşan taşlar öbür satırın boş gözüne geçer.

- "KODU PAYLAŞ" düğmeleri (bekleme ve masa ekranlarında) kaldırıldı; kodla katılma menüsü olmadığı için işe yaramıyordu. Arkadaş eklemek için "KODUMU PAYLAŞ" (profil) duruyor.

- Hata düzeltmesi: Okey > Çevrimiçi oyna'dan "‹ GERİ" düğmesiyle dönünce Hapis/Klasik tavla menüsüne düşülüyordu ve oradan "Bilgisayara karşı" tavla açıyordu. İki ayrı geri-gitme kodu birleştirildi; geri artık her oyunda doğru ekrana döner.

- Genel denetim: kullanılmayan kodlar temizlendi (eski zorluk ekranı, eski ifade menüsü, kodla katıl/özel oda işleyicileri, kullanılmayan yardımcı fonksiyonlar). Uygulama arka plana alınınca çevrimdışı oyunlarda sıra süresi artık işlemez.

- Okey / 101 Okey: ıstakada taşlar hiçbir durumda yer değiştirmez; hem sürüklemede hem dokunarak taşımada taş araya girer, diğerleri kayar. Satır tamamen doluysa sondaki taş öbür satırın ilk boş yerine geçer.

- 101 Okey: 12-13-1 ve 13-1-2 serisi artık geçersiz (1 yalnız seride başta kullanılır: 1-2-3). Klasik Okey'de 12-13-1 serbest kalmaya devam eder.

- Okey / 101 Okey: okey (joker) taşı ıstakanda sayı tarafı görünmeden arka yüzüyle durur; gösterge yazısı tek satırda (GÖSTERGE · OKEY: renk sayı).

- Zorluk seçimi (kolay/orta/zor) kaldırıldı; tavlada bilgisayar her zaman en güçlü seviyede oynar. Diğer oyunların botlarında zaten seviye yoktu.

- Okey / 101 Okey: taşlar desteden veya soldaki oyuncunun attığı taştan SÜRÜKLENEREK çekilir ve ıstakada istediğin yere bırakılır; ıstaka içinde taşı bir başkasının yanına bırakınca yer değiştirmez, ARAYA girer (diğer taşlar kayar). Destenin üstünde kalan taş sayısı yazar, gösterge destenin hemen yanındadır. Dokunarak oynama da çalışır.
- Düzeltme: Okey açılırken arkadaki tavla tahtası görünebiliyordu; salon oyunu açıkken artık gizleniyor. Kurallar menüsü numarası emoji menüsüyle çakışmayacak şekilde ayrıldı.

- **Sıra süresi (7 oyun):** Sıra kimdeyse profil kutusu yanıp söner. İnsan oyuncuda kutunun çevresinde 60 saniyelik halka azalır (yeşil → son 20 sn sarı → son 10 sn kırmızı ve hızlı yanıp söner) ve kalan süre yazılır. Süre dolarsa oyun o oyuncu yerine bot gibi en uygun hamleyi oynar (Okey/101'de çek+at, King/Batak'ta kart/ihale/koz seçimi, tavlada zar+hamle). Çevrimdışı menü açıkken süre durur. Tavlada çevrimiçi 3 kez üst üste süre dolarsa kayıp kuralı aynen duruyor.
- **Kâğıt oyunları:** Kendi profil kutun elinin tam üstünde ortalı.
- **Derleme güvenliği:** Taşınan sınıflar açık import ile çağrılıyor; depoda eski düz klasördeki kopyalar kalsa bile "ambiguous" hatası vermez (yine de eski kopyaları silmeni öneririm: Game, Board, Ai, Okey, OkeyView, Okey101, Okey101View, King, KingView, Batak, BatakView, Cards, CardArt, SalonBase, SalonNet).

- **Kod düzeni (oyunlar ayrı paketlerde):** `app/src/main/java/com/hapistavla/oyun/` altında her oyun kendi klasöründe: `tavla/` (Hapis/Klasik Tavla: Board, Game, Ai), `okey/`, `okey101/`, `king/`, `batak/` (Batak + Açmalı Batak) ve ortak salon kodu `salon/` (SalonBase, SalonNet, kart çizimi). Ana klasörde sadece uygulama kabuğu (MainActivity, menüler, ağ, profil). Bir oyundaki değişiklik diğerinin dosyasına dokunmaz.
- **Düzeltmeler:** King'de botun seçebileceği tek koz rengi elinde hiç yoksa oyunun kilitlenmesi giderildi. 101 Okey'de bir seri 14 taşa uzatılabiliyordu (en çok 13).

- **Masa ekranı:** Okey/101/Batak/King açık masaları 4 koltuklu masa kartı olarak gösterilir (oturanlar avatarla, boş koltuk "+"); masa açınca büyük 4 koltuklu bekleme masası çıkar. ÖZEL ODA ve KODLA KATIL menüleri kaldırıldı.
- **Kurallar:** Ayarlar > Kurallar artık her oyun için ayrı menü (Hapis Tavla, Klasik Tavla, Okey, 101 Okey, King, Batak, Açmalı Batak).
- **Uyarılar:** Çıkış uyarısı, "Sunucuya bağlanıyor" ve mesaj kutuları yazı boyutunda küçük kutu olarak ekranın ortasında.
- **Oyun Salonu:** Ana ekranda Zar / Taş / Kağıt oyunları kategorileri. **Batak** hazır (3 bilgisayar rakip, ihale 4-13, kimse girmezse 3 ile başlama, atılan renge uyma / büyük atma / koz zorunluluğu, koz seçimi, 81/101 hedef). **Okey** hazır (puandan düşme: 11/9/7/5/3/1, çift ve okey atarak bitirme 2 katı). **101 Okey** hazır (7/9/11 el, 101 puanla açma, taş işleme, ceza puanı). **King (Rıfkı)** hazır (20 el, ceza ve koz oyunları, puan tablosu). Oyun Salonu tamamlandı.
- **Masa aç → açık masalar ekranı (tavla):** Masa açınca bekleme ekranı yerine açık masalar listesi gelir; kendi masan en üstte "SENİN MASAN – rakip bekleniyor" olarak durur. Başkasının masasına dokunursan kendi masan kapanır ve oraya oturursun. Özel oda ve arkadaş davetinde kod ekranı aynen kalır.
- **Açmalı Batak (yeni):** Kağıt oyunlarında. Eşli oynanır (karşındaki eşin). İhale en az 8 (kimse girmezse 7). İhaleci kozu seçince eşinin kartları herkese açılır, ihaleci iki eli de oynar. İhaleci ve eşi aldıkları elleri toplar; yetişirse ihale kadar puan, yatarsa o kadar eksi, rakipler aldıkları el kadar puan yazar. 13 ihale veren takım 13 eli de alırsa maçı hemen kazanır, alamazsa hemen kaybeder. Çevrimiçi de oynanır.
- **Salon oyunları çevrimiçi (Okey, 101 Okey, King, Batak, Açmalı Batak):** Oyunu seçince "Çevrimiçi oyna / Bilgisayara karşı". Çevrimiçi: Masa aç (kendi masan üstte), Açık masalar, Hızlı eşleş, arkadaşı davet et. 4 kişiye kadar gerçek oyuncu; boş koltuklara bot oturur, masa sahibi OYUNU BAŞLAT der. Sohbet, emoji, rövanş, rakibi arkadaş ekle çalışır. Oyundan çıkan ya da kopan oyuncunun yerine bot geçer.
- **King kuralları sıkılaştırıldı:** Her oyun türü masada tam 2 kez seçilir (6 ceza x 2 + 4 koz x 2 = 20 el); seçim ekranı bunu ve herkesin kalan haklarını hesaba katar. Koz oyunlarında koz düşmeden koz ile el açılamaz; koz çekilmişse elindeki daha büyük kozu atmak zorundasın (kesmek serbest).
- **King kupa kuralı:** Kupa almaz ve Rıfkı oyunlarında ilk kupa oynanmadan kupa ile el açılamaz (elinde yalnız kupa varsa serbest). Kupalar soluk görünür, dokununca uyarı çıkar; botlar da aynı kurala uyar. Kurallar penceresine eklendi.
- **Okey ve 101 Okey perleri:** Istakanın yanında SERİ DİZ (seri/per) ve ÇİFT DİZ (aynı taşları ikişer ikişer eşler, okey tek kalanla eş olur) düğmeleri. Desteden ya da atılandan çekilen taş ıstakanın sağ boş yerine konur ve seçili (havada) gelir: istediğin boş yere dokunarak taşıyabilir, başka taşa dokunarak yer değiştirebilir ya da AT kutusuna atabilirsin.
- **Açık masalar sayfalama (salon oyunları):** 6'dan fazla masa varsa SONRAKİ › düğmesiyle sayfalar arasında gezilir.
- **Batak botları (ayarlandı):** Botlar yüksek ihalelerde daha temkinli; ihaleyi tutma oranı normal Batak'ta ~%65-70, Açmalı Batak'ta ~%65. Açmalı Batak'ta kimse 8 vermezse 7'ye kalma oranı ~%20.
- **Batak el iptal (Batak ve Açmalı Batak):** Bir oyuncunun elinde 10'dan büyük kart (J, Q, K, A) yoksa dağıtım iptal edilir, yeniden dağıtılır ve ekranda kimin elinde olmadığı yazar.
- **Per taşıma (Okey, 101):** Bir perin en sağındaki taşa dokununca per tamamen seçilir, boş bir yere dokununca tüm per oraya taşınır.
- **Oyun yönü:** Dört salon oyununda da oyun saat yönünün tersine dönüyor; sıra senden sonra sağındaki oyuncuya geçer, atılan taşı solundaki oyuncudan alırsın.
- **101 Okey ekranı:** Istakanın yanında "DİZİLİ GRUPLAR 109 / 101" göstergesi (açmak için yeterliyse yeşil), oyuncu kutularında "Toplam" puanı. Taş çekme netleşti: sıran gelince DESTE "ÇEK ▲", yandaki atılan taş "AL ▲" diye ışıklanır (elini açamayacaksan "AÇAMAZSIN" ve sönük); taş atarken AT kutusu "AT ▲" diye yanar; altta ne yapacağını yazan bir satır durur.
- **Hata düzeltmesi:** Masa aç / Özel oda / Açık masalar'daki puan seçimi artık doğru çalışır.
- **Kare menü düğmeleri:** Oyun menüsü, çevrimiçi menü, ayarlar, puan seçimi simgeli kare düğmelere döndü (simgeler kodla çizilir). Oyun menüsünde iki düğme var: 1) Çevrimiçi oyna, 2) Bilgisayara karşı. Bilgisayara karşı: kaç puana kadar oynanacağı seçilir ve oyun başlar (zorluk seçimi yok, bilgisayar hep en güçlü). **Kurallar** Ayarlar menüsüne taşındı.
- Çevrimiçi bir işe (masa aç, katıl, hızlı eşleş, davet) başlarken yarım kalan eski oyun artık otomatik biter.
- Ana ekranda **ÇIKIŞ** düğmesi (onay sorar) uygulamayı kapatır.
- **Masa aç / Özel oda / Arkadaşa davet** seçilince önce "Kaç puana kadar?" (1 / 3 / 5) sorulur; seçim iki oyunda da (Hapis ve Klasik) geçerlidir ve rakibe de aktarılır.
- **Sohbet (çevrimiçi):** Sağ panelde SOHBET düğmesi. Serbest yazı + hazır mesajlar; rakipten gelen mesaj oyun ekranında kısa balon olur, okunmamışlar düğmede sayı olarak görünür. Küfürlü sözcükler otomatik `***` yapılır. Sohbet **açık/kapalı**: sohbet penceresinden ya da Ayarlar'dan kapatılır; kapalıyken rakipten mesaj gelmez, sen de yazamazsın. Mesajlar sadece o maç boyunca tutulur.
- **Taşlar kayarak hareket eder** (zar toplamı hamlesi peş peşe, vurulan taş bara kayar).
- **Zar atışı:** zarlar sağdan sekerek gelir, dönerek yerine oturur; zar sesi çarpma/sekme şeklinde.
- **Kutlama:** kazanınca konfeti (maçı kazanınca daha bol ve uzun).
- **Rövanş:** maç/oyun bitince "RÖVANŞ" düğmesi (arada kalan oyunlarda "Sonraki Oyun").
- **Açık masa kartında** "N puana kadar / tek oyun" yazar.
- **Stiller:** Ayarlar > TAŞ STİLİ (Klasik, Mermer, Ahşap, Altın) ve ZAR STİLİ (Fildişi, Kırmızı, Altın, Mavi).
